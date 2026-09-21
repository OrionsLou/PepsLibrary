package app.pepslibrary.download

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
    fun aFailedRedownloadKeepsTheExistingCopy() {
        worksDir.mkdirs()
        val existing = File(worksDir, "$workId.epub").apply { writeBytes(epubBytes) }

        downloader { it.reply(code = 429) }.download(workId)

        assertArrayEquals(epubBytes, existing.readBytes())
    }
}
