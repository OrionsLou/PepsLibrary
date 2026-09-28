package app.pepslibrary.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.pepslibrary.ao3.Ao3

/**
 * Sticky footer under the WebView. Back and forward walk the WebView's history and are disabled at either end.
 * Refresh is the way out when AO3 or Cloudflare serves an error or a bot-check page; while a page is loading it
 * becomes Stop. The download queue opens everything currently queued or failed, and its icon shows how many and
 * whether one is downloading; Library opens the list of works already downloaded.
 */
@Composable
fun BrowserToolbar(
    canGoBack: Boolean,
    canGoForward: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    loading: Boolean,
    onRefresh: () -> Unit,
    onStop: () -> Unit,
    /** Works queued or failed, for the queue button's badge. */
    queueCount: Int,
    /** A download is running right now, which moves the queue button's arrow. */
    downloading: Boolean,
    onOpenQueue: () -> Unit,
    onOpenLibrary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxWidth(), tonalElevation = 3.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().height(48.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, enabled = canGoBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            IconButton(onClick = onForward, enabled = canGoForward) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Forward")
            }
            if (loading) {
                IconButton(onClick = onStop) {
                    Icon(Icons.Default.Close, contentDescription = "Stop loading")
                }
            } else {
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh page")
                }
            }
            QueueButton(count = queueCount, downloading = downloading, onClick = onOpenQueue)
            IconButton(onClick = onOpenLibrary) {
                Icon(LibraryIcon, contentDescription = "Library")
            }
        }
    }
}

/**
 * Where Refresh should send the WebView: null means "reload the page it is on". If the very first load failed there
 * is no current page to reload, so start over from AO3's home page instead.
 */
internal fun refreshTarget(currentUrl: String?): String? =
    if (currentUrl.isNullOrBlank()) Ao3.HOME_URL else null
