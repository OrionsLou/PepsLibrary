package app.pepslibrary.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReadingThemeTest {
    @Test
    fun eachTapMovesToTheNextThemeAndWrapsRound() {
        assertEquals(ReadingTheme.SEPIA, ReadingTheme.LIGHT.next())
        assertEquals(ReadingTheme.DARK, ReadingTheme.SEPIA.next())
        assertEquals(ReadingTheme.LIGHT, ReadingTheme.DARK.next())
    }

    @Test
    fun untilOneIsChosenReadingMatchesTheApp() {
        assertEquals(ReadingTheme.DARK, ReadingTheme.resolve(saved = null, appDark = true))
        assertEquals(ReadingTheme.LIGHT, ReadingTheme.resolve(saved = null, appDark = false))
    }

    @Test
    fun aChosenThemeWinsWhateverTheApp() {
        listOf(true, false).forEach { appDark ->
            ReadingTheme.entries.forEach { assertEquals(it, ReadingTheme.resolve(saved = it, appDark = appDark)) }
        }
    }

    @Test
    fun savedNamesRoundTrip_andAnythingElseCountsAsNotChosen() {
        ReadingTheme.entries.forEach { assertEquals(it, ReadingTheme.fromName(it.name)) }
        assertNull(ReadingTheme.fromName(null))
        assertNull(ReadingTheme.fromName("NIGHT"))
    }
}
