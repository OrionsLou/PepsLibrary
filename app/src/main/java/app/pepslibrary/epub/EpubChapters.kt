package app.pepslibrary.epub

import app.pepslibrary.ao3.Ao3
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.File
import java.net.URI
import java.security.MessageDigest
import java.util.zip.ZipFile

/** One file in an EPUB's reading order. [path] is its location inside the zip, decoded (no %20). */
data class EpubChapter(val path: String, val sha256: String, val heading: String?)

object EpubChapters {
    /**
     * The files in [file]'s reading order, or null if it can't be read as an EPUB. Best effort: whatever goes
     * wrong here must never fail a download, it only means a saved position is left as it was.
     */
    fun read(file: File): List<EpubChapter>? = try {
        ZipFile(file).use(::readZip)?.takeIf { it.isNotEmpty() }
    } catch (_: Exception) {
        null
    }

    /** A Readium Locator href ("OEBPS/ch%201.xhtml#p3") as a decoded zip path ("OEBPS/ch 1.xhtml"). */
    fun pathOf(href: String): String? = runCatching { ROOT.resolve(href).path?.removePrefix("/") }.getOrNull()

    /** The reverse of [pathOf]: a zip path encoded as the relative href Readium expects. */
    fun hrefOf(path: String): String = URI(null, null, path, null).rawPath

    private val ROOT = URI("file", null, "/", null)

    private fun readZip(zip: ZipFile): List<EpubChapter>? {
        val container = Jsoup.parse(zip.text("META-INF/container.xml") ?: return null, "", Parser.xmlParser())
        val opfPath = container.selectFirst("rootfile[full-path]")?.attr("full-path") ?: return null
        val opf = Jsoup.parse(zip.text(opfPath) ?: return null, "", Parser.xmlParser())

        val manifest = opf.select("manifest > item[id][href]").associate { it.attr("id") to it.attr("href") }
        val opfUri = URI("file", null, "/$opfPath", null)
        return opf.select("spine > itemref[idref]").map { itemref ->
            val href = manifest[itemref.attr("idref")] ?: return null
            val path = opfUri.resolve(href).path.removePrefix("/")
            val bytes = zip.bytes(path) ?: return null
            EpubChapter(path, sha256(bytes), Ao3.epubChapterHeading(bytes.toString(Charsets.UTF_8)))
        }
    }

    private fun ZipFile.bytes(path: String): ByteArray? = getEntry(path)?.let { entry -> getInputStream(entry).use { it.readBytes() } }

    private fun ZipFile.text(path: String): String? = bytes(path)?.toString(Charsets.UTF_8)

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
