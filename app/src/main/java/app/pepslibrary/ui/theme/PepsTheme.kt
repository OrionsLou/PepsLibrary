package app.pepslibrary.ui.theme

import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import app.pepslibrary.R
import app.pepslibrary.settings.AppSettings
import app.pepslibrary.settings.ReadingTheme

/**
 * Pep's colours: a grey tabby's coat, undercoat and stripes, with eye green as the accent and nose pink for small
 * icons only. Text roles are checked for contrast in PepsPaletteTest. Keep in step with colors.xml in res/values
 * and res/values-night, which colour the window before Compose draws.
 */
object PepsPalette {
    object Light {
        val coat = Color(0xFFF2F2F0)
        val undercoat = Color(0xFFE6E5E2)
        val stripe = Color(0xFF2E2C2A)
        val muted = Color(0xFF6B6660)
        val whisker = Color(0xFFCFCCC7)
        val eye = Color(0xFF566420)
        val nose = Color(0xFFB8656B)
    }

    /** Only for reading: warm paper, for the reader's pages and its bars while the Sepia theme is on. */
    object Sepia {
        val paper = Color(0xFFF4ECD8)
        val undercoat = Color(0xFFEADFC6)
        val ink = Color(0xFF3B3025)
        val muted = Color(0xFF6E5F4E)
        val eye = Color(0xFF566420)
        val nose = Color(0xFFA85A60)
    }

    object Dark {
        val coat = Color(0xFF252423)
        val undercoat = Color(0xFF302F2D)
        val stripe = Color(0xFFECEAE6)
        val muted = Color(0xFFA8A39C)
        val whisker = Color(0xFF45423F)
        val eye = Color(0xFFB7C46E)
        val nose = Color(0xFFE09A9F)
    }
}

private val LightColors = with(PepsPalette.Light) {
    lightColorScheme(
        primary = eye,
        onPrimary = Color.White,
        primaryContainer = Color(0xFFDCE3B8),
        onPrimaryContainer = Color(0xFF2A3208),
        secondary = muted,
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFE0DDD8),
        onSecondaryContainer = stripe,
        tertiary = nose,
        onTertiary = Color.White,
        background = coat,
        onBackground = stripe,
        surface = coat,
        onSurface = stripe,
        surfaceVariant = undercoat,
        onSurfaceVariant = muted,
        surfaceTint = eye,
        outline = Color(0xFF9A958E),
        outlineVariant = whisker,
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFEDEDEA),
        surfaceContainer = undercoat,
        surfaceContainerHigh = Color(0xFFE0DFDB),
        surfaceContainerHighest = Color(0xFFDAD8D4),
    )
}

private val DarkColors = with(PepsPalette.Dark) {
    darkColorScheme(
        primary = eye,
        onPrimary = coat,
        primaryContainer = Color(0xFF3F4A14),
        onPrimaryContainer = Color(0xFFDCE3B8),
        secondary = muted,
        onSecondary = coat,
        secondaryContainer = whisker,
        onSecondaryContainer = stripe,
        tertiary = nose,
        onTertiary = Color(0xFF3A1E20),
        background = coat,
        onBackground = stripe,
        surface = coat,
        onSurface = stripe,
        surfaceVariant = undercoat,
        onSurfaceVariant = muted,
        surfaceTint = eye,
        outline = Color(0xFF7A756F),
        outlineVariant = whisker,
        surfaceContainerLowest = Color(0xFF1E1D1C),
        surfaceContainerLow = Color(0xFF2A2928),
        surfaceContainer = undercoat,
        surfaceContainerHigh = Color(0xFF3A3937),
        surfaceContainerHighest = whisker,
    )
}

private val SepiaColors = with(PepsPalette.Sepia) {
    lightColorScheme(
        primary = eye,
        onPrimary = Color.White,
        primaryContainer = Color(0xFFDCE3B8),
        onPrimaryContainer = Color(0xFF2A3208),
        secondary = muted,
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFE3D5B5),
        onSecondaryContainer = ink,
        tertiary = nose,
        onTertiary = Color.White,
        background = paper,
        onBackground = ink,
        surface = paper,
        onSurface = ink,
        surfaceVariant = undercoat,
        onSurfaceVariant = muted,
        surfaceTint = eye,
        outline = Color(0xFF9C8C74),
        outlineVariant = Color(0xFFD9CBAA),
        surfaceContainerLowest = Color(0xFFFAF5E9),
        surfaceContainerLow = Color(0xFFF0E6CF),
        surfaceContainer = undercoat,
        surfaceContainerHigh = Color(0xFFE4D8BC),
        surfaceContainerHighest = Color(0xFFDDD0B2),
    )
}

/**
 * The page itself in each reading theme, handed to Readium. Dark pages use a slightly softer text than the app's
 * own, which is easier on the eyes over a long read at night.
 */
fun pageColors(theme: ReadingTheme): Pair<Color, Color> = when (theme) {
    ReadingTheme.LIGHT -> PepsPalette.Light.coat to PepsPalette.Light.stripe
    ReadingTheme.SEPIA -> PepsPalette.Sepia.paper to PepsPalette.Sepia.ink
    ReadingTheme.DARK -> PepsPalette.Dark.coat to Color(0xFFDDD9D2)
}

/** Literata is a variable font: one file, with each weight picked through its wght axis. */
@OptIn(ExperimentalTextApi::class)
private fun literata(weight: FontWeight) =
    Font(R.font.literata, weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))

/** Book-like serif for work titles, screen titles and the "Pep's Library" wordmark. */
val Literata = FontFamily(
    literata(FontWeight.Normal),
    literata(FontWeight.Medium),
    literata(FontWeight.SemiBold),
    literata(FontWeight.Bold),
)

/** Titles and headlines in Literata; body text and labels stay in the system font, so controls feel native. */
private val PepsTypography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontFamily = Literata),
        displayMedium = displayMedium.copy(fontFamily = Literata),
        displaySmall = displaySmall.copy(fontFamily = Literata),
        headlineLarge = headlineLarge.copy(fontFamily = Literata),
        headlineMedium = headlineMedium.copy(fontFamily = Literata),
        headlineSmall = headlineSmall.copy(fontFamily = Literata),
        titleLarge = titleLarge.copy(fontFamily = Literata),
        titleMedium = titleMedium.copy(fontFamily = Literata),
        titleSmall = titleSmall.copy(fontFamily = Literata),
    )
}

/** The same scrims enableEdgeToEdge uses by default, for three-button navigation where a bar is still drawn. */
private const val LIGHT_SCRIM = 0xE6FFFFFF.toInt()
private const val DARK_SCRIM = 0x801B1B1B.toInt()

/**
 * The app's theme: light or dark per the in-app setting (following the phone by default). Also sets the status- and
 * navigation-bar icons to match, since the platform otherwise picks them from the phone's setting alone, which left
 * white icons on a white app when the phone was dark.
 *
 * The reader passes its [reading] theme instead, so its bars match the page: Light, Sepia or Dark.
 */
@Composable
fun PepsTheme(reading: ReadingTheme? = null, content: @Composable () -> Unit) {
    val mode by AppSettings.get(LocalContext.current).themeMode.collectAsState()
    val dark = reading?.let { it == ReadingTheme.DARK } ?: mode.isDark(systemDark = isSystemInDarkTheme())
    val colors = when {
        reading == ReadingTheme.SEPIA -> SepiaColors
        dark -> DarkColors
        else -> LightColors
    }

    val activity = LocalContext.current as? ComponentActivity
    DisposableEffect(activity, dark) {
        activity?.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ) { dark },
            navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { dark },
        )
        onDispose {}
    }

    MaterialTheme(colorScheme = colors, typography = PepsTypography, content = content)
}
