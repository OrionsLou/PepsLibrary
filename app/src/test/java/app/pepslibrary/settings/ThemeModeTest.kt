package app.pepslibrary.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeModeTest {
    @Test
    fun systemFollowsThePhone() {
        assertTrue(ThemeMode.SYSTEM.isDark(systemDark = true))
        assertFalse(ThemeMode.SYSTEM.isDark(systemDark = false))
    }

    @Test
    fun anOverrideIgnoresThePhone() {
        listOf(true, false).forEach { phone ->
            assertFalse(ThemeMode.LIGHT.isDark(systemDark = phone))
            assertTrue(ThemeMode.DARK.isDark(systemDark = phone))
        }
    }

    @Test
    fun savedNamesRoundTrip() {
        ThemeMode.entries.forEach { assertEquals(it, ThemeMode.fromName(it.name)) }
    }

    @Test
    fun nothingSavedOrAnUnknownNameMeansFollowThePhone() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromName(null))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromName("SEPIA"))
    }
}
