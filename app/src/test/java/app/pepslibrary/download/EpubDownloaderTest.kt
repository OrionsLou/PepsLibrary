package app.pepslibrary.download

import app.pepslibrary.epub.TestEpub
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class EpubDownloaderTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val workId = 92982131L
    private val pageUrl = "https://archiveofourown.org/works/92982131/chapters/248031186"
    private val epubPath = "/downloads/92982131/Bruces_Business.epub"
    private val epubBytes = byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 3, 4) + ByteArray(2048) { it.toByte() }
    private val workPageHtml = """<html><body><li class="download"><ul>
        <li><a href="$epubPath?updated_at=1">EPUB</a></li></ul></li></body></html>"""

    private val requests = mutableListOf<Request>()
    private val worksDir get() = File(tmp.root, "works")

    /** A client that never touches the network: [handler] answers every request. */
    private fun downloader(handler: (Request) -> Response): EpubDownloader {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                requests += chain.request()
                handler(chain.request())
            }
            .build()
        return EpubDownloader(client, worksDir)
    }

    private fun Request.reply(
        code: Int = 200,
        body: ByteArray = ByteArray(0),
        type: String = "text/html",
        headers: Map<String, String> = emptyMap(),
    ): Response = Response.Builder()
        .request(this)
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message("test")
        .apply { headers.forEach { (k, v) -> header(k, v) } }
        .body(body.toResponseBody(type.toMediaType()))
        .build()

    /** Happy-path routing: the work page, then the EPUB. */
    private fun normalSite(request: Request): Response = when {
        request.url.encodedPath.startsWith("/works/") ->
            request.reply(body = workPageHtml.toByteArray(), type = "text/html")
        request.url.encodedPath == epubPath ->
            request.reply(body = epubBytes, type = "application/epub+zip")
        else -> request.reply(code = 404)
    }

    private fun failure(result: DownloadResult) = result as DownloadResult.Failure

    private fun assertNothingLeftBehind() {
        val leftovers = worksDir.listFiles().orEmpty().map { it.name }
        assertTrue("unexpected files: $leftovers", leftovers.isEmpty())
    }

    @Test
    fun downloadsTheEpubAndSavesItByWorkId() {
        val result = downloader(::normalSite).download(workId) as DownloadResult.Success

        assertEquals(File(worksDir, "$workId.epub"), result.file)
        assertEquals(epubBytes.size.toLong(), result.bytes)
        assertArrayEquals(epubBytes, result.file.readBytes())
        assertEquals("https://archiveofourown.org$epubPath?updated_at=1", result.epubUrl)
        assertEquals(listOf("$workId.epub"), worksDir.list()!!.toList())
    }

    @Test
    fun successCarriesTheUpdatedAtFromTheDownloadLink() {
        val result = downloader(::normalSite).download(workId) as DownloadResult.Success
        assertEquals(1L, result.sourceUpdatedAt) // the page's link ends in ?updated_at=1
    }

    @Test
    fun successCarriesTheMetadataParsedFromTheWorkPageWeAlreadyFetched() {
        val samplePage = checkNotNull(javaClass.getResource("/ao3/work_page_sample.html")).readText()
        val result = downloader { request ->
            when {
                request.url.encodedPath.startsWith("/works/") -> request.reply(body = samplePage.toByteArray())
                request.url.encodedPath.endsWith(".epub") -> request.reply(body = epubBytes, type = "application/epub+zip")
                else -> request.reply(code = 404)
            }
        }.download(workId) as DownloadResult.Success

        assertEquals("Sample Work Title", result.metadata.title)
        assertEquals(listOf("SampleAuthor", "Second Author"), result.metadata.authors)
        assertEquals(12345, result.metadata.words)
        assertEquals(1700000000L, result.sourceUpdatedAt)
        assertEquals("no extra request just for metadata", 2, requests.size)
    }

    @Test
    fun aPageWeCannotReadMetadataFromStillDownloads() {
        // The default test page has an EPUB link but none of AO3's metadata markup.
        val result = downloader(::normalSite).download(workId) as DownloadResult.Success
        assertNull(result.metadata.title)
        assertTrue(result.file.exists())
    }

    @Test
    fun makesExactlyTwoRequestsInOrder_workPageThenEpub_withReferer() {
        downloader(::normalSite).download(workId)

        assertEquals(2, requests.size)
        assertEquals("https://archiveofourown.org/works/$workId?view_adult=true", requests[0].url.toString())
        assertEquals("https://archiveofourown.org$epubPath?updated_at=1", requests[1].url.toString())
        // Redirected work pages (e.g. to /chapters/...) must be used as the base for relative links and the Referer.
        assertNull(requests[0].header("Referer"))
        assertEquals("https://archiveofourown.org/works/$workId?view_adult=true", requests[1].header("Referer"))
    }

    @Test
    fun overwritesAnExistingCopy() {
        worksDir.mkdirs()
        File(worksDir, "$workId.epub").writeBytes(byteArrayOf(9, 9, 9))

        val result = downloader(::normalSite).download(workId) as DownloadResult.Success

        assertArrayEquals(epubBytes, result.file.readBytes())
    }

    @Test
    fun botCheckOnThePageIsReported_byHeader() {
        val result = failure(downloader { it.reply(code = 200, headers = mapOf("cf-mitigated" to "challenge")) }.download(workId))
        assertEquals(FailureKind.BOT_CHECK, result.kind)
        assertEquals(1, requests.size)
    }

    @Test
    fun botCheckIsReported_on403() {
        val result = failure(downloader { it.reply(code = 403) }.download(workId))
        assertEquals(FailureKind.BOT_CHECK, result.kind)
    }

    @Test
    fun rateLimitOnThePageReportsRetryAfter_andStops() {
        val result = failure(downloader { it.reply(code = 429, headers = mapOf("Retry-After" to "45")) }.download(workId))
        assertEquals(FailureKind.RATE_LIMITED, result.kind)
        assertEquals(45L, result.retryAfterSeconds)
        assertEquals("must not retry on its own", 1, requests.size)
    }

    @Test
    fun rateLimitOnTheEpubReportsRetryAfter_andLeavesNoFile() {
        val result = failure(
            downloader { request ->
                if (request.url.encodedPath == epubPath) {
                    request.reply(code = 429, headers = mapOf("Retry-After" to "120"))
                } else {
                    normalSite(request)
                }
            }.download(workId),
        )
        assertEquals(FailureKind.RATE_LIMITED, result.kind)
        assertEquals(120L, result.retryAfterSeconds)
        assertEquals(2, requests.size)
        assertNothingLeftBehind()
    }

    @Test
    fun rateLimitWithoutAUsableRetryAfterHasNoDelay() {
        assertNull(failure(downloader { it.reply(code = 429) }.download(workId)).retryAfterSeconds)
        assertNull(
            failure(downloader { it.reply(code = 429, headers = mapOf("Retry-After" to "Wed, 21 Oct 2026 07:28:00 GMT")) }.download(workId))
                .retryAfterSeconds,
        )
    }

    @Test
    fun otherHttpErrorsAreReportedWithTheStatus() {
        val result = failure(downloader { it.reply(code = 404) }.download(workId))
        assertEquals(FailureKind.HTTP_ERROR, result.kind)
        assertTrue(result.message, result.message.contains("404"))
    }

    @Test
    fun cloudflareAndServerErrorsOnThePageAreRetryableServerErrors_notGenericHttpErrors() {
        listOf(500, 502, 503, 520, 522, 525, 526, 599).forEach { code ->
            requests.clear()
            val result = failure(downloader { it.reply(code = code) }.download(workId))
            assertEquals("HTTP $code", FailureKind.SERVER_ERROR, result.kind)
            assertTrue(result.message, result.message.contains("$code") && result.message.contains("work page"))
            assertEquals("must not retry on its own", 1, requests.size)
        }
    }

    @Test
    fun aServerErrorOnTheEpubNamesThatStage_andLeavesNoFile() {
        val result = failure(
            downloader { request ->
                if (request.url.encodedPath == epubPath) request.reply(code = 525) else normalSite(request)
            }.download(workId),
        )
        assertEquals(FailureKind.SERVER_ERROR, result.kind)
        assertTrue(result.message, result.message.contains("525") && result.message.contains("EPUB file"))
        assertEquals(2, requests.size)
        assertNothingLeftBehind()
    }

    @Test
    fun clientErrorsStayGenericHttpErrors() {
        listOf(400, 404, 410).forEach { code ->
            assertEquals("HTTP $code", FailureKind.HTTP_ERROR, failure(downloader { it.reply(code = code) }.download(workId)).kind)
        }
    }

    @Test
    fun aWorkPageWithoutAnEpubLinkIsReported_andNoSecondRequestIsMade() {
        val result = failure(
            downloader { it.reply(body = "<html><body>Sign in to view this work</body></html>".toByteArray()) }.download(workId),
        )
        assertEquals(FailureKind.NO_EPUB_LINK, result.kind)
        assertEquals(1, requests.size)
    }

    @Test
    fun anHtmlResponseInPlaceOfTheEpubIsRejected_andNotSaved() {
        val result = failure(
            downloader { request ->
                if (request.url.encodedPath == epubPath) {
                    request.reply(body = "<html>Shields are up!</html>".toByteArray(), type = "text/html")
                } else {
                    normalSite(request)
                }
            }.download(workId),
        )
        assertEquals(FailureKind.NOT_AN_EPUB, result.kind)
        assertNothingLeftBehind()
    }

    @Test
    fun anEmptyEpubResponseIsRejected() {
        val result = failure(
            downloader { request ->
                if (request.url.encodedPath == epubPath) request.reply(body = ByteArray(0)) else normalSite(request)
            }.download(workId),
        )
        assertEquals(FailureKind.NOT_AN_EPUB, result.kind)
        assertNothingLeftBehind()
    }

    @Test
    fun aDroppedConnectionIsReportedAsANetworkFailure_andLeavesNoFile() {
        val result = failure(
            downloader { request ->
                if (request.url.encodedPath == epubPath) throw IOException("connection reset") else normalSite(request)
            }.download(workId),
        )
        assertEquals(FailureKind.NETWORK, result.kind)
        assertTrue(result.message, result.message.contains("connection reset"))
        assertFalse(File(worksDir, "$workId.epub").exists())
    }

    @Test
    fun aFirstDownloadHasNoChapterComparison() {
        val result = downloader(::normalSite).download(workId) as DownloadResult.Success
        assertNull(result.previousChapters)
        assertNull(result.chapters)
    }

    @Test
    fun aRedownloadOverARealEpubReportsTheOldAndNewReadingOrder() {
        val oldBook = TestEpub.write(tmp.newFile("old.epub"), listOf(Triple("c1.xhtml", "Chapter 1", "one")))
        val newBook = TestEpub.write(
            tmp.newFile("new.epub"),
            listOf(Triple("c1.xhtml", "Chapter 1", "one"), Triple("c2.xhtml", "Chapter 2", "two")),
        ).readBytes()
        worksDir.mkdirs()
        oldBook.copyTo(File(worksDir, "$workId.epub"))

        val result = downloader { request ->
            if (request.url.encodedPath == epubPath) request.reply(body = newBook, type = "application/epub+zip") else normalSite(request)
        }.download(workId) as DownloadResult.Success

        assertEquals(listOf("OEBPS/c1.xhtml"), result.previousChapters!!.map { it.path })
        assertEquals(listOf("OEBPS/c1.xhtml", "OEBPS/c2.xhtml"), result.chapters!!.map { it.path })
        assertEquals(result.previousChapters!![0], result.chapters!![0])
    }

    @Test
    fun aRedownloadOverAnUnreadableCopyStillSucceeds_withoutAComparison() {
        worksDir.mkdirs()
        File(worksDir, "$workId.epub").writeBytes(byteArrayOf(9, 9, 9))

        val result = downloader(::normalSite).download(workId) as DownloadResult.Success

        assertNull(result.previousChapters)
        assertArrayEquals(epubBytes, result.file.readBytes())
    }

    @Test
    fun aFailedRedownloadKeepsTheExistingCopy() {
        worksDir.mkdirs()
        val existing = File(worksDir, "$workId.epub").apply { writeBytes(epubBytes) }

        downloader { it.reply(code = 429) }.download(workId)

        assertArrayEquals(epubBytes, existing.readBytes())
    }

    // --- cancelling ---

    @Test
    fun aDownloadCancelledBeforeItStartsMakesNoRequest() {
        val canceller = DownloadCanceller().apply { cancel() }

        val result = downloader(::normalSite).download(workId, canceller)

        assertEquals(DownloadResult.Cancelled, result)
        assertTrue(requests.isEmpty())
    }

    @Test
    fun aCancelBetweenTheWorkPageAndTheEpubStopsBeforeTheFile_andKeepsTheExistingCopy() {
        worksDir.mkdirs()
        val existing = File(worksDir, "$workId.epub").apply { writeBytes(byteArrayOf(9, 9, 9)) }
        val canceller = DownloadCanceller()

        val result = downloader { request ->
            normalSite(request).also { if (request.url.encodedPath.startsWith("/works/")) canceller.cancel() }
        }.download(workId, canceller)

        assertEquals(DownloadResult.Cancelled, result)
        assertArrayEquals(byteArrayOf(9, 9, 9), existing.readBytes())
        assertEquals(listOf("$workId.epub"), worksDir.list()!!.toList()) // no .part left behind
    }

    /**
     * Against a real socket, since that's what Cancel has to beat: the EPUB response sends a few bytes and then
     * stalls, as a dropped network does. The read timeout is far longer than the test allows, so only an actual
     * cancel of the connection can end it in time.
     */
    @Test
    fun cancellingAStalledTransferEndsItAtOnce_leavingNoPartFile_andTheExistingCopyIntact() {
        worksDir.mkdirs()
        val existing = File(worksDir, "$workId.epub").apply { writeBytes(byteArrayOf(9, 9, 9)) }
        val server = ServerSocket(0)
        val stalled = CountDownLatch(1)
        thread(isDaemon = true) {
            // One connection per request (Connection: close), so each accept() is the next request.
            repeat(2) {
                val socket = server.accept()
                val reader = socket.getInputStream().bufferedReader()
                val path = reader.readLine().split(" ")[1]
                while (reader.readLine().isNotEmpty()) Unit // skip headers
                val out = socket.getOutputStream()
                if (path.startsWith("/works/")) {
                    val body = workPageHtml.toByteArray()
                    out.write("HTTP/1.1 200 OK\r\nContent-Type: text/html\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
                    out.write(body)
                    out.flush()
                    socket.close()
                } else {
                    out.write("HTTP/1.1 200 OK\r\nContent-Type: application/epub+zip\r\nContent-Length: 1000000\r\nConnection: close\r\n\r\n".toByteArray())
                    out.write(epubBytes)
                    out.flush()
                    stalled.countDown() // and never send the rest
                }
            }
        }
        val client = OkHttpClient.Builder()
            .readTimeout(60, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val url = chain.request().url.newBuilder().scheme("http").host("127.0.0.1").port(server.localPort).build()
                // Only the socket is local: the response still names the AO3 URL, as the downloader expects.
                chain.proceed(chain.request().newBuilder().url(url).build()).newBuilder().request(chain.request()).build()
            }
            .build()
        val canceller = DownloadCanceller()
        var result: DownloadResult? = null
        val download = thread { result = EpubDownloader(client, worksDir).download(workId, canceller) }

        assertTrue("server never reached the stall", stalled.await(10, TimeUnit.SECONDS))
        Thread.sleep(200) // let the client get into its blocking read
        canceller.cancel()
        download.join(5_000)

        assertFalse("download still blocked after cancel", download.isAlive)
        assertEquals(DownloadResult.Cancelled, result)
        assertArrayEquals(byteArrayOf(9, 9, 9), existing.readBytes())
        assertEquals(listOf("$workId.epub"), worksDir.list()!!.toList())
        server.close()
    }
}
