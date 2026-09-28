package app.pepslibrary.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeepScreenOnTest {
    @Test
    fun theDefaultIsAlwaysOn() {
        assertEquals(KeepScreenOn.Always, KeepScreenOn.DEFAULT)
        assertEquals(KeepScreenOn.Always, KeepScreenOn.fromSaved(null))
    }

    @Test
    fun everyChoiceSurvivesSavingAndLoading() {
        listOf(KeepScreenOn.Always, KeepScreenOn.Off, KeepScreenOn.Timed(1), KeepScreenOn.Timed(25), KeepScreenOn.Timed(240))
            .forEach { assertEquals(it, KeepScreenOn.fromSaved(it.toSaved())) }
    }

    @Test
    fun anUnreadableSavedValueFallsBackToTheDefault() {
        listOf("", "sometimes", "timed:", "timed:abc", "TIMED:5").forEach {
            assertEquals(it, KeepScreenOn.DEFAULT, KeepScreenOn.fromSaved(it))
        }
    }

    @Test
    fun savedMinutesOutOfRangeAreBroughtBackInRange() {
        assertEquals(KeepScreenOn.Timed(1), KeepScreenOn.fromSaved("timed:0"))
        assertEquals(KeepScreenOn.Timed(240), KeepScreenOn.fromSaved("timed:9999"))
    }

    @Test
    fun typedMinutesMustBeAWholeNumberInRange() {
        assertEquals(1, KeepScreenOn.parseMinutes("1"))
        assertEquals(15, KeepScreenOn.parseMinutes(" 15 "))
        assertEquals(240, KeepScreenOn.parseMinutes("240"))
        listOf("", "0", "241", "1.5", "-3", "abc", "10 min").forEach {
            assertNull(it, KeepScreenOn.parseMinutes(it))
        }
    }
}
