package app.pepslibrary.epub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EpubChaptersTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val book = listOf(
        Triple("Work_split_000.xhtml", "Preface", "Words: 1,000"),
        Triple("Work_split_001.xhtml", "Chapter 1: The Maze", "It began in a maze."),
        Triple("Work_split_002.xhtml", "Chapter 2", "It went on."),
    )

    @Test
    fun readsTheReadingOrderWithPathsInsideTheZip() {
        val chapters = EpubChapters.read(TestEpub.write(tmp.newFile("a.epub"), book))!!

        assertEquals(
            listOf("OEBPS/Work_split_000.xhtml", "OEBPS/Work_split_001.xhtml", "OEBPS/Work_split_002.xhtml"),
            chapters.map { it.path },
        )
        assertEquals(listOf("Preface", "Chapter 1: The Maze", "Chapter 2"), chapters.map { it.heading })
    }

    @Test
    fun aPackageFileAtTheZipRootResolvesToo() {
        val chapters = EpubChapters.read(TestEpub.write(tmp.newFile("a.epub"), book, opfDir = ""))!!
        assertEquals("Work_split_001.xhtml", chapters[1].path)
    }

    @Test
    fun identicalFilesHashTheSame_andAnyEditChangesTheHash() {
        val first = EpubChapters.read(TestEpub.write(tmp.newFile("a.epub"), book))!!
        val again = EpubChapters.read(TestEpub.write(tmp.newFile("b.epub"), book))!!
        val edited = EpubChapters.read(
            TestEpub.write(tmp.newFile("c.epub"), book.toMutableList().also { it[1] = it[1].copy(third = "It began in a maze!") }),
        )!!

        assertEquals(first, again)
        assertNotEquals(first[1].sha256, edited[1].sha256)
        assertEquals(first[2].sha256, edited[2].sha256)
    }

    @Test
    fun aFileWithoutAHeadingHasNone() {
        val chapters = EpubChapters.read(TestEpub.write(tmp.newFile("a.epub"), listOf(Triple("x.xhtml", null, "text"))))!!
        assertNull(chapters.single().heading)
    }

    @Test
    fun encodedFileNamesAreStoredDecoded() {
        val chapters = EpubChapters.read(TestEpub.write(tmp.newFile("a.epub"), listOf(Triple("ch 1.xhtml", "One", "text"))))!!
        assertEquals("OEBPS/ch 1.xhtml", chapters.single().path)
    }

    @Test
    fun somethingThatIsNotAnEpubGivesNull() {
        assertNull(EpubChapters.read(tmp.newFile("empty.epub")))
        assertNull(EpubChapters.read(tmp.newFile("junk.epub").apply { writeBytes(byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 3, 4, 1, 2)) }))
    }

    @Test
    fun locatorHrefsConvertToZipPathsAndBack() {
        assertEquals("OEBPS/ch 1.xhtml", EpubChapters.pathOf("OEBPS/ch%201.xhtml#p3"))
        assertEquals("OEBPS/ch1.xhtml", EpubChapters.pathOf("OEBPS/Text/../ch1.xhtml"))
        assertEquals("OEBPS/ch%201.xhtml", EpubChapters.hrefOf("OEBPS/ch 1.xhtml"))
        assertEquals("Work_split_002.xhtml", EpubChapters.hrefOf("Work_split_002.xhtml"))
    }
}
