package app.pepslibrary.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards readability if a colour is ever tweaked: WCAG AA wants 4.5:1 for text against its background. */
class PepsPaletteTest {
    private fun contrast(a: Color, b: Color): Double {
        val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun assertReadable(name: String, text: Color, background: Color) {
        val ratio = contrast(text, background)
        assertTrue("$name is only %.2f:1".format(ratio), ratio >= 4.5)
    }

    @Test
    fun lightTextRolesAreReadable() = with(PepsPalette.Light) {
        listOf(coat, undercoat).forEach { bg ->
            assertReadable("stripe", stripe, bg)
            assertReadable("muted", muted, bg)
            assertReadable("eye", eye, bg)
        }
        assertReadable("white on eye (buttons)", Color.White, eye)
    }

    @Test
    fun darkTextRolesAreReadable() = with(PepsPalette.Dark) {
        listOf(coat, undercoat).forEach { bg ->
            assertReadable("stripe", stripe, bg)
            assertReadable("muted", muted, bg)
            assertReadable("eye", eye, bg)
            assertReadable("nose", nose, bg)
        }
        assertReadable("coat on eye (buttons)", coat, eye)
    }

    @Test
    fun lightNosePinkIsVisibleAsAnIcon() {
        // Icons need 3:1 (WCAG non-text contrast); light nose pink is deliberately not used for text.
        assertTrue(contrast(PepsPalette.Light.nose, PepsPalette.Light.coat) >= 3.0)
    }
}
