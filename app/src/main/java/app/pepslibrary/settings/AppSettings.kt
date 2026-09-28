package app.pepslibrary.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Whether the app is light or dark. SYSTEM follows the phone's own setting. */
enum class ThemeMode {
    SYSTEM, LIGHT, DARK;

    fun isDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }

    companion object {
        /** A name saved by this or an earlier version; anything unknown falls back to SYSTEM rather than crashing. */
        fun fromName(name: String?): ThemeMode = entries.find { it.name == name } ?: SYSTEM
    }
}

/** The reader's page colours. The reader's own bars follow them too. */
enum class ReadingTheme(val label: String) {
    LIGHT("Light"), SEPIA("Sepia"), DARK("Dark");

    /** The button in the seeking bar steps through them in this order, wrapping round. */
    fun next(): ReadingTheme = entries[(ordinal + 1) % entries.size]

    companion object {
        /** Null for nothing saved yet, or a name a later version no longer knows. */
        fun fromName(name: String?): ReadingTheme? = entries.find { it.name == name }

        /** Until one is chosen, reading matches the app: dark pages in a dark app, light ones otherwise. */
        fun resolve(saved: ReadingTheme?, appDark: Boolean): ReadingTheme = saved ?: if (appDark) DARK else LIGHT
    }
}

/**
 * App-wide settings, in their own SharedPreferences file. One instance per process, exposing each setting as a
 * StateFlow so every screen (and both activities) updates the moment it changes.
 */
class AppSettings private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    private val themeModeState = MutableStateFlow(ThemeMode.fromName(prefs.getString(THEME_MODE, null)))

    val themeMode: StateFlow<ThemeMode> = themeModeState.asStateFlow()

    private val readingThemeState = MutableStateFlow(ReadingTheme.fromName(prefs.getString(READING_THEME, null)))

    /** Null until a reading theme has been chosen; see [ReadingTheme.resolve]. */
    val readingTheme: StateFlow<ReadingTheme?> = readingThemeState.asStateFlow()

    /** Null goes back to matching the app, as before anything was chosen. */
    fun setReadingTheme(theme: ReadingTheme?) {
        prefs.edit().putString(READING_THEME, theme?.name).apply()
        readingThemeState.value = theme
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(THEME_MODE, mode.name).apply()
        themeModeState.value = mode
    }

    companion object {
        private const val THEME_MODE = "themeMode"
        private const val READING_THEME = "readingTheme"

        @Volatile private var instance: AppSettings? = null

        fun get(context: Context): AppSettings = instance ?: synchronized(this) {
            instance ?: AppSettings(context.applicationContext).also { instance = it }
        }
    }
}
