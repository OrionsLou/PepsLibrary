package app.pepslibrary.download

import app.pepslibrary.ao3.Ao3
import app.pepslibrary.ao3.WorkMetadata
import app.pepslibrary.epub.EpubChapter
import app.pepslibrary.epub.EpubChapters
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * BOT_CHECK, RATE_LIMITED, SERVER_ERROR and NETWORK are worth retrying later; the others won't fix themselves.
 * SERVER_ERROR covers AO3 and Cloudflare 5xx responses, e.g. Cloudflare's 525 "SSL handshake failed" to AO3's servers.
 */
enum class FailureKind { BOT_CHECK, RATE_LIMITED, SERVER_ERROR, NO_EPUB_LINK, NOT_AN_EPUB, HTTP_ERROR, NETWORK }

sealed interface DownloadResult {
    data class Success(
        val file: File,
        val bytes: Long,
        val epubUrl: String,
        /** Read from the work page we already fetched to find the link; empty fields if AO3's markup changed. */
        val metadata: WorkMetadata,
        /** AO3's `updated_at` for this version of the work, from the download link. */
        val sourceUpdatedAt: Long?,
        /**
         * Only for a re-download that replaced a readable copy: the old and new reading order, so a saved position
         * can be checked against what changed. Null for a first download or if either file couldn't be read.
         */
        val previousChapters: List<EpubChapter>? = null,
        val chapters: List<EpubChapter>? = null,
    ) : DownloadResult

    /** Stopped by [DownloadHandle.cancel] before the new copy was saved; any existing copy is untouched. */
    data object Cancelled : DownloadResult

    data class Failure(
        val kind: FailureKind,
        val message: String,
        /** From a 429's Retry-After header, when AO3 gave one in seconds. */
        val retryAfterSeconds: Long? = null,
    ) : DownloadResult
}

/** What a running download is doing, for the progress shown on screen. */
sealed interface DownloadProgress {
    /** Fetching the work page, to find the EPUB link. */
    data object LoadingPage : DownloadProgress

    /** The EPUB has been asked for; AO3 can take a while to build one for a long work before the first byte. */
    data object WaitingForAo3 : DownloadProgress

    /** [total] is from AO3's Content-Length, or null when it didn't send one. */
    data class Receiving(val bytes: Long, val total: Long?) : DownloadProgress
}

/**
 * One per download. Another thread can stop it: cancelling cancels the OkHttp call in flight, which closes its
 * connection, so even a stalled read ends at once instead of waiting out the read timeout. And the download reports
 * how far it has got through it, to [onProgress].
 */
class DownloadHandle(private val onProgress: (DownloadProgress) -> Unit = {}) {
    private var cancelled = false
    private var call: Call? = null

    val isCancelled: Boolean
        @Synchronized get() = cancelled

    @Synchronized
    fun cancel() {
        cancelled = true
        call?.cancel()
    }

    internal fun report(progress: DownloadProgress) = onProgress(progress)

    /** Tracks [call] as the request in flight, cancelling it straight away if [cancel] already happened. */
    @Synchronized
    internal fun track(call: Call): Call {
        this.call = call
        if (cancelled) call.cancel()
        return call
    }
}

/** Often enough for a smooth bar, rarely enough not to flood the UI with updates. */
private const val PROGRESS_STEP_BYTES = 16 * 1024L

/**
 * Downloads one work as an EPUB: load the work page, find the EPUB link, fetch it. The EPUB bundles every
 * chapter, so a work costs one download however long it is. Blocking; call it off the main thread. It never
 * retries: queueing, delays and Retry-After handling belong to the download queue.
 */
class EpubDownloader(private val client: OkHttpClient, private val worksDir: File) {

    /**
     * Once the new file has replaced the old one the download counts as done, so a [handle] cancel that arrives
     * after that point still returns Success: the caller must save it, or the library would miss a file on disk.
     */
    fun download(workId: Long, handle: DownloadHandle = DownloadHandle()): DownloadResult {
        if (handle.isCancelled) return DownloadResult.Cancelled
        return try {
            fetchAndSave(workId, handle)
        } catch (e: IOException) {
            // A cancelled call surfaces as an IOException ("Canceled", or a closed socket mid-read).
            if (handle.isCancelled) DownloadResult.Cancelled
            else DownloadResult.Failure(FailureKind.NETWORK, e.message ?: e.javaClass.simpleName)
        }
    }

    private fun fetchAndSave(workId: Long, handle: DownloadHandle): DownloadResult {
        handle.report(DownloadProgress.LoadingPage)
        val pageRequest = Request.Builder().url(Ao3.workUrl(workId)).build()
        val page = handle.track(client.newCall(pageRequest)).execute().use { response ->
            failureFor(response, "work page")?.let { return it }
            response.request.url.toString() to response.requireBody().string()
        }
        val (pageUrl, html) = page

        val epubUrl = Ao3.findEpubUrl(html, pageUrl)
            ?: return DownloadResult.Failure(
                FailureKind.NO_EPUB_LINK,
                "No EPUB link on the work page. Is it restricted (sign in) or hidden behind a prompt?",
            )

        val epubRequest = Request.Builder().url(epubUrl).header("Referer", pageUrl).build()
        handle.report(DownloadProgress.WaitingForAo3)
        return handle.track(client.newCall(epubRequest)).execute().use { response ->
            failureFor(response, "EPUB file")?.let { return it }
            save(workId, epubUrl, Ao3.parseWorkMetadata(html), response, handle)
        }
    }

    private fun save(
        workId: Long,
        epubUrl: String,
        metadata: WorkMetadata,
        response: Response,
        handle: DownloadHandle,
    ): DownloadResult {
        worksDir.mkdirs()
        val target = File(worksDir, "$workId.epub")
        val part = File(worksDir, "$workId.epub.part")
        try {
            copyReporting(response, part, handle)
            if (!looksLikeZip(part)) {
                return DownloadResult.Failure(
                    FailureKind.NOT_AN_EPUB,
                    "AO3 returned something that isn't an EPUB (content-type ${response.header("Content-Type")}).",
                )
            }
            val previous = if (target.isFile) EpubChapters.read(target) else null
            Files.move(part.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
            return DownloadResult.Success(
                file = target,
                bytes = target.length(),
                epubUrl = epubUrl,
                metadata = metadata,
                sourceUpdatedAt = Ao3.updatedAtFromDownloadUrl(epubUrl),
                previousChapters = previous,
                chapters = previous?.let { EpubChapters.read(target) },
            )
        } finally {
            part.delete() // gone already after a successful move; cleans up any partial or rejected download
        }
    }

    /** Writes the body to [target], reporting the bytes so far about every [PROGRESS_STEP_BYTES] and once at the end. */
    private fun copyReporting(response: Response, target: File, handle: DownloadHandle) {
        val body = response.requireBody()
        val total = body.contentLength().takeIf { it > 0 }
        var bytes = 0L
        var reported = 0L
        handle.report(DownloadProgress.Receiving(0, total))
        body.byteStream().use { input ->
            target.outputStream().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    bytes += read
                    if (bytes - reported >= PROGRESS_STEP_BYTES) {
                        reported = bytes
                        handle.report(DownloadProgress.Receiving(bytes, total))
                    }
                }
            }
        }
        if (bytes != reported) handle.report(DownloadProgress.Receiving(bytes, total))
    }

    /** Maps AO3's and Cloudflare's "no" answers to a Failure; null means the response is good to read. */
    private fun failureFor(response: Response, stage: String): DownloadResult.Failure? = when {
        response.header("cf-mitigated") == "challenge" || response.code == 403 -> DownloadResult.Failure(
            FailureKind.BOT_CHECK,
            "Blocked by AO3's bot check (HTTP ${response.code}). Open the site in the browser tab and pass it first.",
        )
        response.code == 429 -> {
            val retryAfter = response.header("Retry-After")?.trim()?.toLongOrNull()
            DownloadResult.Failure(
                FailureKind.RATE_LIMITED,
                "AO3 is rate limiting requests" + (retryAfter?.let { ", retry after ${it}s" } ?: "") + ".",
                retryAfter,
            )
        }
        response.code in 500..599 -> DownloadResult.Failure(
            FailureKind.SERVER_ERROR,
            "AO3 or Cloudflare had a server error (HTTP ${response.code}) while fetching the $stage. " +
                "This is usually temporary; try again shortly.",
        )
        !response.isSuccessful -> DownloadResult.Failure(
            FailureKind.HTTP_ERROR,
            "HTTP ${response.code} from ${response.request.url.host} while fetching the $stage.",
        )
        else -> null
    }

    private fun looksLikeZip(file: File): Boolean {
        val magic = ByteArray(4)
        val read = file.inputStream().use { it.read(magic) }
        return read == 4 && magic[0] == 'P'.code.toByte() && magic[1] == 'K'.code.toByte() &&
            magic[2] == 3.toByte() && magic[3] == 4.toByte()
    }

    private fun Response.requireBody() = checkNotNull(body) { "response has no body" }
}
