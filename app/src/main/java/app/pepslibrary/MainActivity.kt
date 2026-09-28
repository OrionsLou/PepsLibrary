package app.pepslibrary

import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.webkit.CookieManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.pepslibrary.ui.OpeningScreen
import app.pepslibrary.ui.OpeningStyle
import app.pepslibrary.ui.PepsLibraryApp
import app.pepslibrary.ui.openingStyle
import app.pepslibrary.ui.theme.PepsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val opening = openingStyle(
            coldStart = savedInstanceState == null && !openingShown,
            animationsOff = Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f,
        )
        openingShown = true
        if (opening != OpeningStyle.NONE && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // The opening screen's first frame matches the splash exactly, so drop the splash at once instead of
            // letting the system fade it out, which briefly dimmed the cat in the hand-off.
            splashScreen.setOnExitAnimationListener { it.remove() }
        }
        setContent {
            PepsTheme {
                var showOpening by remember { mutableStateOf(opening != OpeningStyle.NONE) }
                Surface(modifier = Modifier.fillMaxSize()) {
                    Box(Modifier.safeDrawingPadding()) {
                        PepsLibraryApp()
                    }
                    // Outside the insets padding, so it's centred on the whole screen like the system splash.
                    if (showOpening) OpeningScreen(opening) { showOpening = false }
                }
            }
        }
    }

    private companion object {
        /** Once per process: backing out and reopening while the app is still in memory doesn't replay it. */
        var openingShown = false
    }

    override fun onStop() {
        super.onStop()
        // Persist session cookies to disk so a sign-in survives the process being killed.
        CookieManager.getInstance().flush()
    }
}
