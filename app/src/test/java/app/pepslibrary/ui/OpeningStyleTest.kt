package app.pepslibrary.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class OpeningStyleTest {
    @Test
    fun aColdStartAnimates() {
        assertEquals(OpeningStyle.ANIMATED, openingStyle(coldStart = true, animationsOff = false))
    }

    @Test
    fun withAndroidAnimationsOffItHoldsStillInstead() {
        assertEquals(OpeningStyle.STILL, openingStyle(coldStart = true, animationsOff = true))
    }

    @Test
    fun returningToTheAppNeverReplaysIt() {
        assertEquals(OpeningStyle.NONE, openingStyle(coldStart = false, animationsOff = false))
        assertEquals(OpeningStyle.NONE, openingStyle(coldStart = false, animationsOff = true))
    }
}
