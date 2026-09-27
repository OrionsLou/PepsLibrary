package app.pepslibrary.epub

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Writes a minimal EPUB with its package file in [opfDir], in the same shape Calibre gives AO3's downloads. */
object TestEpub {
    /** [files] are (file name, heading, body text) in reading order. */
    fun write(target: File, files: List<Triple<String, String?, String>>, opfDir: String = "OEBPS"): File {
        val prefix = if (opfDir.isEmpty()) "" else "$opfDir/"
        val manifest = files.mapIndexed { i, (name, _, _) ->
            """<item id="html$i" href="${name.replace(" ", "%20")}" media-type="application/xhtml+xml"/>"""
        }.joinToString("\n")
        val spine = files.indices.joinToString("\n") { """<itemref idref="html$it"/>""" }
        val opf = """<?xml version="1.0" encoding="utf-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="2.0">
              <manifest>$manifest</manifest>
              <spine toc="ncx">$spine</spine>
            </package>"""
        val container = """<?xml version="1.0"?>
            <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
              <rootfiles><rootfile full-path="${prefix}content.opf" media-type="application/oebps-package+xml"/></rootfiles>
            </container>"""

        ZipOutputStream(target.outputStream()).use { zip ->
            fun put(path: String, text: String) {
                zip.putNextEntry(ZipEntry(path))
                zip.write(text.toByteArray())
                zip.closeEntry()
            }
            put("mimetype", "application/epub+zip")
            put("META-INF/container.xml", container)
            put("${prefix}content.opf", opf)
            files.forEach { (name, heading, body) ->
                val h = heading?.let { "<h2 class=\"heading\">$it</h2>" } ?: ""
                put("$prefix$name", "<html><body>$h<p>$body</p></body></html>")
            }
        }
        return target
    }
}
