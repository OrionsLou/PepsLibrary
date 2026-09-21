package app.pepslibrary.ao3

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Ao3MetadataTest {
    private fun fixture(name: String): String =
        checkNotNull(javaClass.getResource("/ao3/$name")) { "missing fixture $name" }.readText()

    private fun meta(html: String) = Ao3.parseWorkMetadata(html)

    /** Wraps stats in the real nesting (dl.work.meta > dd.stats > dl.stats). */
    private fun withStats(vararg pairs: Pair<String, String>) = """<dl class="work meta group"><dd class="stats"><dl class="stats">""" +
        pairs.joinToString("") { (k, v) -> """<dt class="$k">x:</dt><dd class="$k">$v</dd>""" } + "</dl></dd></dl>"

    // --- the full, real-structure page ---

    @Test
    fun parsesEverythingFromARealStructurePage() {
        val m = meta(fixture("work_page_sample.html"))

        assertEquals("Sample Work Title", m.title)
        assertEquals(listOf("SampleAuthor", "Second Author"), m.authors)
        assertEquals("General Audiences", m.rating)
        assertEquals(listOf("Creator Chose Not To Use Archive Warnings"), m.warnings)
        assertEquals(listOf("Gen", "F/M"), m.categories)
        assertEquals(listOf("Sample Fandom", "Other Fandom"), m.fandoms)
        assertEquals(listOf("Alice/Bob"), m.relationships)
        assertEquals(listOf("Alice", "Bob"), m.characters)
        assertEquals(listOf("Fluff", "Angst, but \"make it\" fluffy"), m.tags)
        assertEquals("English", m.language)
        assertEquals(12345, m.words)
        assertEquals(3, m.chaptersPublished)
        assertEquals(10, m.chaptersTotal)
        assertEquals("2020-01-02", m.publishedDate)
        assertEquals("2020-03-04", m.updatedDate)
    }

    @Test
    fun theWorkSummaryIsUsed_notTheChapterSummaryThatComesFirstInThePage() {
        val m = meta(fixture("work_page_sample.html"))
        // Paragraphs are kept apart and odd whitespace is collapsed.
        assertEquals("First paragraph of the summary.\n\nSecond paragraph with odd whitespace.", m.summary)
    }

    // --- authors ---

    @Test
    fun anAnonymousWorkHasItsBylineAsTheAuthor() {
        val m = meta("""<div class="preface group"><h3 class="byline heading">Anonymous</h3></div>""")
        assertEquals(listOf("Anonymous"), m.authors)
    }

    @Test
    fun noBylineMeansNoAuthors() {
        assertEquals(emptyList<String>(), meta("<html></html>").authors)
    }

    // --- stats ---

    @Test
    fun anUnfinishedWorkWithNoPlannedTotalHasNullTotal() {
        val m = meta(withStats("chapters" to "1/?"))
        assertEquals(1, m.chaptersPublished)
        assertNull(m.chaptersTotal)
    }

    @Test
    fun aFinishedWorkReadsCompletedAsTheUpdatedDate() {
        val html = """<dl class="work meta group"><dd class="stats"><dl class="stats">
            <dt class="published">Published:</dt><dd class="published">2026-09-21</dd>
            <dt class="status">Completed:</dt><dd class="status">2026-09-22</dd></dl></dd></dl>"""
        val m = meta(html)
        assertEquals("2026-09-21", m.publishedDate)
        assertEquals("2026-09-22", m.updatedDate)
    }

    @Test
    fun wordCountsIgnoreThousandsSeparators() {
        assertEquals(34676, meta(withStats("words" to "34,676")).words)
        assertEquals(1234567, meta(withStats("words" to "1,234,567")).words)
        assertEquals(34676, meta(withStats("words" to "34.676")).words)
        assertEquals(1, meta(withStats("words" to "1")).words)
    }

    @Test
    fun unreadableStatsAreNullNotErrors() {
        val m = meta(withStats("words" to "many", "chapters" to "three", "published" to "last Tuesday", "status" to "soon"))
        assertNull(m.words)
        assertNull(m.chaptersPublished)
        assertNull(m.chaptersTotal)
        assertNull(m.publishedDate)
        assertNull(m.updatedDate)
    }

    // --- resilience: a markup change must never break a download ---

    @Test
    fun emptyOrGarbageInputGivesEmptyMetadata() {
        listOf("", "   ", "<<<>>>", "not html at all", "<html><body><p>Error 404</p></body></html>").forEach {
            assertEquals("input: $it", WorkMetadata(), meta(it))
        }
    }

    @Test
    fun missingSectionsAreEmptyNotFailures() {
        val m = meta("""<h2 class="title heading">Only A Title</h2>""".let { """<div class="preface group">$it</div>""" })
        assertEquals("Only A Title", m.title)
        assertTrue(m.tags.isEmpty() && m.fandoms.isEmpty() && m.characters.isEmpty())
        assertNull(m.summary)
        assertNull(m.rating)
    }

    @Test
    fun emptyTagsAndBlankTitlesAreDropped() {
        val html = """<div class="preface group"><h2 class="title heading">   </h2></div>
            <dl class="work meta group"><dd class="freeform tags"><ul><li><a class="tag"> </a></li><li><a class="tag"> Kept </a></li></ul></dd></dl>"""
        val m = meta(html)
        assertNull(m.title)
        assertEquals(listOf("Kept"), m.tags)
    }

    @Test
    fun aSummaryWithoutParagraphsFallsBackToItsText() {
        val html = """<div class="preface group"><div class="summary module"><blockquote class="userstuff">Just text.</blockquote></div></div>"""
        assertEquals("Just text.", meta(html).summary)
    }

    // --- updated_at ---

    @Test
    fun updatedAtIsReadFromDownloadUrls() {
        assertEquals(1789956295L, Ao3.updatedAtFromDownloadUrl("https://archiveofourown.org/downloads/1/T.epub?updated_at=1789956295"))
        assertEquals(5L, Ao3.updatedAtFromDownloadUrl("https://archiveofourown.org/downloads/1/T.epub?x=1&updated_at=5"))
    }

    @Test
    fun updatedAtIsNullWhenMissingOrUnusable() {
        assertNull(Ao3.updatedAtFromDownloadUrl(null))
        assertNull(Ao3.updatedAtFromDownloadUrl("https://archiveofourown.org/downloads/1/T.epub"))
        assertNull(Ao3.updatedAtFromDownloadUrl("https://archiveofourown.org/downloads/1/T.epub?updated_at="))
        assertNull(Ao3.updatedAtFromDownloadUrl("https://archiveofourown.org/downloads/1/T.epub?updated_at=abc"))
        assertNull(Ao3.updatedAtFromDownloadUrl("https://archiveofourown.org/downloads/1/T.epub?updated_at=99999999999999999999999"))
        assertNull(Ao3.updatedAtFromDownloadUrl("https://archiveofourown.org/x?not_updated_at=5"))
    }
}
