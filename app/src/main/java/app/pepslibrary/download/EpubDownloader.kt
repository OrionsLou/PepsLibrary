package app.pepslibrary.download

import app.pepslibrary.ao3.Ao3
import app.pepslibrary.ao3.WorkMetadata
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
    ) : DownloadResult

    data class Failure(
        val kind: FailureKind,
        val message: String,
        /** From a 429's Retry-After header, when AO3 gave one in seconds. */
        val retryAfterSeconds: Long? = null,
    ) : DownloadResult
}

/**
 * Downloads one work as an EPUB: load the work page, find the EPUB link, fetch it. The EPUB bundles every
 * chapter, so a work costs one download however long it is. Blocking; call it off the main thread. It never
 * retries: queueing, delays and Retry-After handling belong to the download queue.
 */
class EpubDownloader(private val client: OkHttpClient, private val worksDir: File) {

    fun download(workId: Long): DownloadResult = try {
        fetchAndSave(workId)
    } catch (e: IOException) {
        DownloadResult.Failure(FailureKind.NETWORK, e.message ?: e.javaClass.simpleName)
    }

    private fun fetchAndSave(workId: Long): DownloadResult {
        val page = client.newCall(Request.Builder().url(Ao3.workUrl(workId)).build()).execute().use { response ->
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
        return client.newCall(epubRequest).execute().use { response ->
            failureFor(response, "EPUB file")?.let { return it }
            save(workId, epubUrl, Ao3.parseWorkMetadata(html), response)
        }
    }

    private fun save(workId: Long, epubUrl: String, metadata: WorkMetadata, response: Response): DownloadResult {
        worksDir.mkdirs()
        val target = File(worksDir, "$workId.epub")
        val part = File(worksDir, "$workId.epub.part")
        try {
            response.requireBody().byteStream().use { input -> part.outputStream().use { input.copyTo(it) } }
            if (!looksLikeZip(part)) {
                return DownloadResult.Failure(
                    FailureKind.NOT_AN_EPUB,
                    "AO3 returned something that isn't an EPUB (content-type ${response.header("Content-Type")}).",
                )
            }
            Files.move(part.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
            return DownloadResult.Success(
                file = target,
                bytes = target.length(),
                epubUrl = epubUrl,
                metadata = metadata,
                sourceUpdatedAt = Ao3.updatedAtFromDownloadUrl(epubUrl),
            )
        } finally {
            part.delete() // gone already after a successful move; cleans up any partial or rejected download
        }
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
