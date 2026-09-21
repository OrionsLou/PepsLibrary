package app.pepslibrary.data

import org.junit.Assert.assertEquals
import org.junit.Test

class StringListConverterTest {
    private fun roundTrip(list: List<String>) = StringListConverter.toList(StringListConverter.fromList(list))

    @Test
    fun anEmptyListStaysEmpty() {
        assertEquals("", StringListConverter.fromList(emptyList()))
        assertEquals(emptyList<String>(), StringListConverter.toList(""))
    }

    @Test
    fun singleAndMultipleItemsRoundTrip() {
        assertEquals(listOf("Fluff"), roundTrip(listOf("Fluff")))
        assertEquals(listOf("a", "b", "c"), roundTrip(listOf("a", "b", "c")))
    }

    @Test
    fun tagNamesWithCommasQuotesNewlinesAndUnicodeSurvive() {
        val tags = listOf("Angst, but make it fluffy", "\"quoted\" 'tag'", "line\nbreak", "日本語 – ünïcödé 🙂", "a|b;c\\d")
        assertEquals(tags, roundTrip(tags))
    }

    @Test
    fun orderIsPreserved() {
        val list = listOf("z", "a", "m")
        assertEquals(list, roundTrip(list))
    }

    @Test
    fun theStoredFormIsTheValuesJoinedByAControlCharacter() {
        assertEquals("ab", StringListConverter.fromList(listOf("a", "b")))
    }
}
