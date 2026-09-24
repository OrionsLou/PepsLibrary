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
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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

/**
 * Everything currently queued or stuck at FAILED — the multi-item view; a work's own page only ever shows its
 * own entry. Drawn over the browser like the Library screen, so the WebView (and the page you were on) stays
 * alive underneath.
 */
@Composable
fun QueueScreen(
    entries: List<DownloadQueueEntity>,
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
                Text(
                    "${entries.size} ${if (entries.size == 1) "item" else "items"}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(end = 16.dp),
                )
            }

            if (entries.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "Nothing queued. Open a work in the browser and tap Download EPUB.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(entries, key = { it.workId }) { entry ->
                        QueueCard(entry, onRetry = { onRetry(entry.workId) }, onRemove = { onRemove(entry.workId) })
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueCard(entry: DownloadQueueEntity, onRetry: () -> Unit, onRemove: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // No title yet: it's only known once the work page is actually fetched, which hasn't happened (or
            // didn't succeed) for anything shown here. See HANDOFF's phase 3 UI-polish note if this feels too bare.
            Text("Work ${entry.workId}", style = MaterialTheme.typography.titleMedium)
            queueStatusLabel(entry)?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }

            // A running download resolves itself; only a paused (PENDING) or stuck (FAILED) row needs a manual
            // way out, so nothing is offered while IN_PROGRESS.
            if (entry.status != QueueStatus.IN_PROGRESS) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (entry.status == QueueStatus.FAILED) {
                        Button(onClick = onRetry) { Text("Retry now") }
                    }
                    OutlinedButton(onClick = onRemove) { Text("Remove") }
                }
            }
        }
    }
}
