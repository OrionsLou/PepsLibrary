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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pepslibrary.data.WorkEntity
import java.text.DateFormat
import java.util.Date

/**
 * The downloaded works. Drawn over the browser rather than replacing it, so the WebView (and the page you were
 * on) stays alive underneath.
 */
@Composable
fun LibraryScreen(
    works: List<WorkEntity>,
    /** Fraction read (0.0 to 1.0) for works that have a saved reading position. */
    progress: Map<Long, Double?>,
    /** Works being downloaded right now; they can't be deleted until that finishes. */
    downloading: Set<Long>,
    sort: LibrarySort,
    onSortChange: (LibrarySort) -> Unit,
    onOpenWork: (workId: Long) -> Unit,
    onDeleteWork: (workId: Long) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    var confirmDelete by remember { mutableStateOf<WorkEntity?>(null) }
    val listState = rememberLazyListState()
    // A new order starts from the top. Otherwise the list keeps whichever card was first in view, now mid-list.
    LaunchedEffect(sort) { listState.scrollToItem(0) }

    confirmDelete?.let { work ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete this work?") },
            text = {
                Text(
                    "\"${work.title}\" and your reading position will be removed from this phone. " +
                        "You can download it again from AO3 later.",
                )
            },
            confirmButton = {
                TextButton(onClick = { onDeleteWork(work.workId); confirmDelete = null }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }

    // The empty pointerInput swallows touches so they don't fall through to the WebView underneath.
    Surface(modifier.fillMaxSize().pointerInput(Unit) {}) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to browser")
                }
                Text("Library", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Text(
                    "${works.size} ${if (works.size == 1) "work" else "works"}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(end = 16.dp),
                )
            }

            if (works.isNotEmpty()) {
                SortMenu(sort, onSortChange, Modifier.padding(horizontal = 12.dp))
            }

            if (works.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "No downloads yet. Open a work in the browser and tap Download EPUB.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(sortWorks(works, sort), key = { it.workId }) { work ->
                        WorkCard(
                            work = work,
                            progressLabel = readingProgressLabel(progress[work.workId]),
                            canDelete = work.workId !in downloading,
                            onClick = { onOpenWork(work.workId) },
                            onDelete = { confirmDelete = work },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SortMenu(sort: LibrarySort, onSortChange: (LibrarySort) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        TextButton(onClick = { expanded = true }) {
            Text("Sort: ${sort.label}")
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            LibrarySort.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = { onSortChange(option); expanded = false },
                    trailingIcon = { if (option == sort) Icon(Icons.Filled.Check, contentDescription = "Selected") },
                )
            }
        }
    }
}

@Composable
private fun WorkCard(
    work: WorkEntity,
    progressLabel: String,
    canDelete: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 4.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    work.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).padding(top = 16.dp),
                )
                IconButton(onClick = onDelete, enabled = canDelete) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = if (canDelete) "Delete ${work.title}" else "Downloading, can't delete yet",
                    )
                }
            }
            Column(Modifier.padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                WorkDetails(work, progressLabel)
            }
        }
    }
}

@Composable
private fun WorkDetails(work: WorkEntity, progressLabel: String) {
    workByline(work)?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    if (work.fandoms.isNotEmpty()) {
        Text(
            work.fandoms.joinToString(", "),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    workStatsLine(work).takeIf { it.isNotEmpty() }?.let {
        Text(it, style = MaterialTheme.typography.bodySmall)
    }
    Text(progressLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    work.summary?.let {
        Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
    Text(
        "Downloaded ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(work.downloadedAt))}",
        style = MaterialTheme.typography.labelSmall,
    )
}
