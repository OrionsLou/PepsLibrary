package app.pepslibrary.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.draw.rotate
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
    order: LibraryOrder,
    onOrderChange: (LibraryOrder) -> Unit,
    filter: LibraryFilter,
    onFilterChange: (LibraryFilter) -> Unit,
    onOpenWork: (workId: Long) -> Unit,
    onSetPinned: (workId: Long, pinned: Boolean) -> Unit,
    onDeleteWork: (workId: Long) -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    var confirmDelete by remember { mutableStateOf<WorkEntity?>(null) }
    var showFilters by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val shown = sortWorks(works.filter(filter::matches), order)
    // Start from the top when the order, the filter, or the work at the top changes (e.g. the one just read moving
    // up under "Last read"). Otherwise the list keeps whichever card was first in view and hides the new top one.
    LaunchedEffect(order, filter, shown.firstOrNull()?.workId) { listState.scrollToItem(0) }

    if (showFilters) {
        FilterSheet(works, filter, onFilterChange, onDismiss = { showFilters = false })
    }

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
                    listOfNotNull(
                        workCountLabel(shown = shown.size, total = works.size, filtered = filter.isActive),
                        // The size of what's listed, so it always matches the list, filtered or not.
                        shown.takeIf { it.isNotEmpty() }?.let { list -> formatFileSize(list.sumOf { it.fileSizeBytes }) },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                )
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings")
                }
            }

            if (works.isNotEmpty()) {
                Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    SortMenu(order, onOrderChange, Modifier.weight(1f, fill = false))
                    TextButton(onClick = { showFilters = true }) {
                        Text(if (filter.isActive) "Filter (${filter.selectedCount})" else "Filter")
                    }
                }
            }

            if (works.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "No downloads yet. Open a work in the browser and tap Download EPUB.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            } else if (shown.isEmpty()) {
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("No works match these filters.", style = MaterialTheme.typography.bodyLarge)
                    TextButton(onClick = { onFilterChange(LibraryFilter()) }) { Text("Clear filters") }
                }
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(shown, key = { it.workId }) { work ->
                        WorkCard(
                            work = work,
                            progressLabel = readingProgressLabel(progress[work.workId]),
                            canDelete = work.workId !in downloading,
                            onClick = { onOpenWork(work.workId) },
                            onTogglePin = { onSetPinned(work.workId, !work.pinned) },
                            onDelete = { confirmDelete = work },
                        )
                    }
                }
            }
        }
    }
}

internal fun workCountLabel(shown: Int, total: Int, filtered: Boolean): String {
    val noun = if (total == 1) "work" else "works"
    return if (filtered) "$shown of $total $noun" else "$total $noun"
}

/** One section per kind of filter; the sheet itself scrolls, so a long author list stays usable. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSheet(
    works: List<WorkEntity>,
    filter: LibraryFilter,
    onFilterChange: (LibraryFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Filters", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { onFilterChange(LibraryFilter()) }, enabled = filter.isActive) { Text("Clear all") }
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            item { FilterSectionHeader("Pinned") }
            item(key = "pinned") {
                FilterRow(pinnedOption(works), checked = filter.pinnedOnly) { onFilterChange(filter.togglePinnedOnly()) }
            }
            item { FilterSectionHeader("Status") }
            items(statusOptions(works), key = { "status:${it.value}" }) { option ->
                val status = CompletionStatus.valueOf(option.value)
                FilterRow(option, checked = status in filter.statuses) {
                    onFilterChange(filter.toggleStatus(status))
                }
            }
            item { FilterSectionHeader("Fandom") }
            items(fandomOptions(works, filter), key = { "fandom:${it.value}" }) { option ->
                FilterRow(option, checked = option.value in filter.fandoms) {
                    onFilterChange(filter.toggleFandom(option.value))
                }
            }
            item { FilterSectionHeader("Author") }
            items(authorOptions(works, filter), key = { "author:${it.value}" }) { option ->
                FilterRow(option, checked = option.value in filter.authors) {
                    onFilterChange(filter.toggleAuthor(option.value))
                }
            }
        }
    }
}

@Composable
private fun FilterSectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun FilterRow(option: FilterOption, checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(12.dp))
        Text(option.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            "${option.count}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 12.dp),
        )
    }
}

@Composable
private fun SortMenu(order: LibraryOrder, onOrderChange: (LibraryOrder) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        TextButton(onClick = { expanded = true }) {
            Text("Sort: ${order.label}")
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            LibrarySort.entries.forEach { option ->
                val current = option == order.sort
                DropdownMenuItem(
                    // The selected option shows the direction tapping it again will switch to.
                    text = {
                        Column {
                            Text(option.label)
                            if (current) {
                                val next = if (order.reversed) option.naturalDirection else option.reversedDirection
                                Text("Tap to sort $next", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    },
                    onClick = { onOrderChange(order.select(option)); expanded = false },
                    trailingIcon = { if (current) Icon(Icons.Filled.Check, contentDescription = "Selected") },
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
    onTogglePin: () -> Unit,
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
                IconButton(onClick = onTogglePin) {
                    // A pinned work's pin tilts, like one pushed into a board.
                    val tilt by animateFloatAsState(if (work.pinned) 45f else 0f, label = "pinTilt")
                    Icon(
                        if (work.pinned) PinIcons.Filled else PinIcons.Outlined,
                        contentDescription = if (work.pinned) "Unpin ${work.title}" else "Pin ${work.title} to the top",
                        tint = if (work.pinned) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                        modifier = Modifier.rotate(tilt),
                    )
                }
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
    val formatDate = { millis: Long -> DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis)) }
    lastReadLabel(work.lastOpenedAt, formatDate)?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
    Text(
        "Downloaded ${formatDate(work.downloadedAt)} · ${formatFileSize(work.fileSizeBytes)}",
        style = MaterialTheme.typography.labelSmall,
    )
}
