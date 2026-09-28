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

/**
 * App-wide settings, in their own SharedPreferences file. One instance per process, exposing each setting as a
 * StateFlow so every screen (and both activities) updates the moment it changes.
 */
class AppSettings private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    private val themeModeState = MutableStateFlow(ThemeMode.fromName(prefs.getString(THEME_MODE, null)))

    val themeMode: StateFlow<ThemeMode> = themeModeState.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(THEME_MODE, mode.name).apply()
        themeModeState.value = mode
    }

    companion object {
        private const val THEME_MODE = "themeMode"

        @Volatile private var instance: AppSettings? = null

        fun get(context: Context): AppSettings = instance ?: synchronized(this) {
            instance ?: AppSettings(context.applicationContext).also { instance = it }
        }
    }
}
