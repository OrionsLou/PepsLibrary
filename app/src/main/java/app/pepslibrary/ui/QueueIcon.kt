package app.pepslibrary.ui

import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * Material's "download" icon, an arrow into a tray (path data from Google's Material Icons, Apache 2.0), split into its
 * two parts so the arrow can move on its own. Defined here because it's only in material-icons-extended, a large
 * dependency to add for one icon; see [PinIcons].
 */
private object DownloadIcons {
    val Arrow: ImageVector by lazy { icon("DownloadArrow", "M19,9h-4V3H9v6H5l7,7L19,9z") }
    val Tray: ImageVector by lazy { icon("DownloadTray", "M5,20h14v-2H5V20z") }

    private fun icon(name: String, path: String): ImageVector =
        ImageVector.Builder(name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .addPath(pathData = addPathNodes(path), fill = SolidColor(Color.Black))
            .build()
}

/** The badge's number: the count, capped so it stays a small dot of text. Null (no badge) for an empty queue. */
internal fun queueBadgeText(count: Int): String? = when {
    count <= 0 -> null
    count > 99 -> "99+"
    else -> count.toString()
}

/** What TalkBack reads for the footer button: its name, plus how many works are in the queue. */
internal fun queueButtonDescription(count: Int): String = when {
    count <= 0 -> "Download queue"
    count == 1 -> "Download queue, 1 work"
    else -> "Download queue, $count works"
}

/**
 * The footer's download-queue button: an arrow into a tray, with an accent badge counting the queued and failed works
 * whenever there are any. While a download is running the arrow drops gently towards the tray and back, on a loop;
 * with Android's animations turned off it stays still.
 */
@Composable
internal fun QueueButton(count: Int, downloading: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val badge = queueBadgeText(count)
    Box(modifier) {
        IconButton(onClick = onClick) {
            Box {
                Icon(DownloadIcons.Tray, contentDescription = queueButtonDescription(count))
                val context = LocalContext.current
                val animationsOff = remember {
                    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
                }
                if (downloading && !animationsOff) {
                    val drop by rememberInfiniteTransition(label = "queueArrow").animateFloat(
                        initialValue = -3f,
                        targetValue = 1.5f,
                        animationSpec = infiniteRepeatable(tween(650, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                        label = "queueArrowDrop",
                    )
                    val px = with(LocalDensity.current) { 1.dp.toPx() }
                    Icon(DownloadIcons.Arrow, contentDescription = null, modifier = Modifier.graphicsLayer { translationY = drop * px })
                } else {
                    Icon(DownloadIcons.Arrow, contentDescription = null)
                }
            }
        }
        // In the button's corner rather than Material's usual spot on the icon's corner, where it hid most of the
        // arrowhead. Drawn outside the IconButton, whose round clip would cut it off out here; it takes no touches,
        // so a tap on it still reaches the button.
        if (badge != null) {
            Badge(
                modifier = Modifier.align(Alignment.TopEnd).offset(x = (-4).dp, y = 4.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) { Text(badge) }
        }
    }
}
