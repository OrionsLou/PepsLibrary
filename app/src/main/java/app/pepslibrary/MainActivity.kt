package app.pepslibrary

import android.content.Context
import android.content.Intent
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
import app.pepslibrary.ao3.Ao3
import app.pepslibrary.ui.LinkFromBook
import app.pepslibrary.ui.OpeningScreen
import app.pepslibrary.ui.OpeningStyle
import app.pepslibrary.ui.PepsLibraryApp
import app.pepslibrary.ui.openingStyle
import app.pepslibrary.ui.theme.PepsTheme

class MainActivity : ComponentActivity() {
    /** An AO3 page to open in the browser, handed over by the reader; cleared once the browser has loaded it. */
    private var pendingLink by mutableStateOf<LinkFromBook?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Only on a fresh start: after a restore, the intent is the one already handled.
        if (savedInstanceState == null) pendingLink = linkToOpen(intent)
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
                        PepsLibraryApp(openLink = pendingLink, onLinkOpened = { pendingLink = null })
                    }
                    // Outside the insets padding, so it's centred on the whole screen like the system splash.
                    if (showOpening) OpeningScreen(opening) { showOpening = false }
                }
            }
        }
    }

    /** The reader finishing with a link to open: this activity is already below it, so the link arrives here. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        linkToOpen(intent)?.let { pendingLink = it }
    }

    companion object {
        private const val EXTRA_OPEN_URL = "openUrl"
        private const val EXTRA_FROM_WORK_ID = "fromWorkId"

        /** Once per process: backing out and reopening while the app is still in memory doesn't replay it. */
        private var openingShown = false

        /**
         * Brings the browser back to the front with [url] open, closing whatever was started on top of it (the
         * reader). Clear-top plus single-top reuses the running browser, so its history and sign-in are kept.
         * [fromWorkId] is the book the link was tapped in, which Back on that page reopens.
         */
        fun openUrlIntent(context: Context, url: String, fromWorkId: Long): Intent =
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_OPEN_URL, url)
                .putExtra(EXTRA_FROM_WORK_ID, fromWorkId)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        /**
         * This activity is exported (it's the launcher's), so any app could send it a link: only AO3 pages are ever
         * loaded, the same rule the browser applies to its own navigation.
         */
        private fun linkToOpen(intent: Intent?): LinkFromBook? {
            val url = intent?.getStringExtra(EXTRA_OPEN_URL)?.takeIf(Ao3::isAo3Url) ?: return null
            val workId = intent.getLongExtra(EXTRA_FROM_WORK_ID, -1).takeIf { it > 0 } ?: return null
            return LinkFromBook(url, workId)
        }
    }

    override fun onStop() {
        super.onStop()
        // Persist session cookies to disk so a sign-in survives the process being killed.
        CookieManager.getInstance().flush()
    }
}
