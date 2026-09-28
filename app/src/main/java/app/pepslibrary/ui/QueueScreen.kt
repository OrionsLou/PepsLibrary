package app.pepslibrary.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import app.pepslibrary.data.DownloadQueueEntity
import app.pepslibrary.data.QueueStatus
import app.pepslibrary.download.DownloadProgress
import app.pepslibrary.download.RunningDownload

/**
 * Everything currently queued or stuck at FAILED — the multi-item view; a work's own page only ever shows its
 * own entry. Drawn over the browser like the Library screen, so the WebView (and the page you were on) stays
 * alive underneath.
 */
@Composable
fun QueueScreen(
    entries: List<DownloadQueueEntity>,
    online: Boolean,
    /** The download in progress, if any, and how far it has got. */
    running: RunningDownload?,
    onRetry: (workId: Long) -> Unit,
    onRemove: (workId: Long) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)

    // The empty pointerInput swallows touches so they don't fall through to the WebView underneath.
    Surface(modifier.fillMaxSize().pointerInput(Unit) {}) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to browser")
                }
                Text("Downloads", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            }
            Shelf {
                if (entries.isNotEmpty()) {
                    Text(
                        "${entries.size} ${if (entries.size == 1) "work" else "works"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }

            if (!online) {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "You're offline. Downloads are paused and resume by themselves once you're back online.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            }


            if (entries.isEmpty()) {
                SleepingCatMessage("Nothing downloading. Open a work in the browser and tap Download EPUB.")
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(entries, key = { it.workId }) { entry ->
                        Column(Modifier.animateItem()) {
                            QueueRow(
                                entry,
                                online,
                                progress = running?.takeIf { it.workId == entry.workId }?.progress,
                                onRetry = { onRetry(entry.workId) },
                                onRemove = { onRemove(entry.workId) },
                            )
                            HorizontalDivider(
                                Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueRow(
    entry: DownloadQueueEntity,
    online: Boolean,
    progress: DownloadProgress?,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(queueRowTitle(entry), style = MaterialTheme.typography.titleMedium)
        queueStatusLabel(entry, online, progress)?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (entry.status == QueueStatus.IN_PROGRESS) DownloadProgressIndicator(progress)

        // Remove and Cancel are the same action (stop it if running, then take it off the queue); the label
        // just says which one it is for this row.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (entry.status == QueueStatus.FAILED) {
                Button(onClick = onRetry) { Text("Retry now") }
            }
            OutlinedButton(onClick = onRemove) { Text(if (queueShowsCancel(entry)) "Cancel" else "Remove") }
        }
    }
}

/**
 * A thin accent bar for a download in progress: filling up once the size is known, moving back and forth while AO3
 * is still preparing the file or didn't say how big it is.
 */
@Composable
internal fun DownloadProgressIndicator(progress: DownloadProgress?, modifier: Modifier = Modifier) {
    val fraction = downloadProgressFraction(progress)
    val barModifier = modifier.fillMaxWidth().height(3.dp)
    if (fraction != null) {
        LinearProgressIndicator(
            progress = { fraction },
            modifier = barModifier,
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.outlineVariant,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
    } else {
        LinearProgressIndicator(
            modifier = barModifier,
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.outlineVariant,
            gapSize = 0.dp,
        )
    }
}
