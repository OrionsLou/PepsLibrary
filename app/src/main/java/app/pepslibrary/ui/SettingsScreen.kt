package app.pepslibrary.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.pepslibrary.BuildConfig
import app.pepslibrary.R
import app.pepslibrary.settings.KeepScreenOn
import app.pepslibrary.settings.ReadingTheme
import app.pepslibrary.settings.ThemeMode

/** Kept word for word, as asked; the grammar is part of the charm. */
internal const val MEOW_NOTE =
    "Made this for me wife so that I may smother her with physical affection while reading, of which she is allergic to."

/** The saved library view in plain words, for the Settings screen: the sort on one line, the filters on another. */
internal fun libraryViewSummary(order: LibraryOrder, filter: LibraryFilter): List<String> {
    val filters = buildList {
        if (filter.pinnedOnly) add("Pinned only")
        CompletionStatus.entries.filter { it in filter.statuses }.forEach { add(it.label) }
        filter.fandoms.sortedBy(::titleSortKey).forEach { add(if (it == NONE_LISTED) "No fandom listed" else it) }
        filter.authors.sortedBy(::titleSortKey).forEach { add(if (it == NONE_LISTED) "No author listed" else it) }
    }
    return listOf(
        "Sort: ${order.label}",
        if (filters.isEmpty()) "No filters" else "Filters: ${filters.joinToString(", ")}",
    )
}

/**
 * The licence file wraps its lines at about 80 characters, which wraps again, raggedly, on a phone. This joins the
 * lines of each paragraph for display while keeping paragraph breaks, headings (all capitals), numbered clauses and
 * rule lines on lines of their own. The words are unchanged.
 */
internal fun reflowLicence(text: String): String =
    text.replace("\r\n", "\n").split(Regex("\n\\s*\n")).joinToString("\n\n") { paragraph ->
        val lines = paragraph.lines().map { it.trim() }.filter { it.isNotEmpty() }
        buildString {
            lines.forEachIndexed { index, line ->
                when {
                    index == 0 -> append(line)
                    standsAlone(line) || line.first().isDigit() || standsAlone(lines[index - 1]) ->
                        append('\n').append(line)
                    else -> append(' ').append(line)
                }
            }
        }
    }

/** A rule line of dashes, or a heading in capitals. */
private fun standsAlone(line: String): Boolean =
    line.all { it == '-' } || (line.any { it.isLetter() } && line == line.uppercase())

/** Whether there's anything for "Reset sort and filters" to undo. */
internal fun isDefaultLibraryView(order: LibraryOrder, filter: LibraryFilter): Boolean =
    order == LibraryOrder() && !filter.isActive

/**
 * Everything that's saved, in one place: how the app and the reader look, whether the reader keeps the screen on,
 * the library's sort and filters, the
 * version, and a note from me behind the Meow button. Drawn over the library, which stays underneath.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    onThemeMode: (ThemeMode) -> Unit,
    /** Null means the reader matches the app. */
    readingTheme: ReadingTheme?,
    onReadingTheme: (ReadingTheme?) -> Unit,
    keepScreenOn: KeepScreenOn,
    onKeepScreenOn: (KeepScreenOn) -> Unit,
    libraryOrder: LibraryOrder,
    libraryFilter: LibraryFilter,
    onResetLibraryView: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showMeow by rememberSaveable { mutableStateOf(false) }
    var showLicence by remember { mutableStateOf(false) }

    if (showLicence) LicenceDialog(onDismiss = { showLicence = false })

    // The empty pointerInput swallows touches so they don't fall through to the screens underneath.
    Surface(modifier.fillMaxSize().pointerInput(Unit) {}) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to library")
                }
                Text("Settings", style = MaterialTheme.typography.titleLarge)
            }

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Section("Appearance") {
                    Text("App", style = MaterialTheme.typography.bodyMedium)
                    Choices(
                        options = ThemeMode.entries,
                        selected = themeMode,
                        label = { mode ->
                            when (mode) {
                                ThemeMode.SYSTEM -> "Match phone"
                                ThemeMode.LIGHT -> "Light"
                                ThemeMode.DARK -> "Dark"
                            }
                        },
                        onSelect = onThemeMode,
                    )
                    Text("Reading", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                    Choices(
                        options = listOf(null) + ReadingTheme.entries,
                        selected = readingTheme,
                        label = { it?.label ?: "Match app" },
                        onSelect = onReadingTheme,
                    )
                    Text(
                        "The reading theme also changes from the button in the reader's bottom bar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                HorizontalDivider()
                Section("Reader") {
                    KeepScreenOnSetting(keepScreenOn, onKeepScreenOn)
                }

                HorizontalDivider()
                Section("Library") {
                    libraryViewSummary(libraryOrder, libraryFilter).forEach {
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                    }
                    OutlinedButton(
                        onClick = onResetLibraryView,
                        enabled = !isDefaultLibraryView(libraryOrder, libraryFilter),
                    ) { Text("Reset sort and filters") }
                }

                HorizontalDivider()
                Section("About") {
                    Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Titles are set in Literata, under the SIL Open Font License.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { showLicence = true }) { Text("View licence") }
                    }
                }

                HorizontalDivider()
                Button(onClick = { showMeow = !showMeow }) { Text("Meow") }
                AnimatedVisibility(visible = showMeow) { MeowCard() }
            }
        }
    }
}

/** The three kinds of [KeepScreenOn], for the segmented choice. */
private enum class KeepScreenOnKind(val label: String) { ALWAYS("Always"), TIMED("Timed"), OFF("Off") }

/**
 * "Keep the screen on" while reading: Always, Timed (for a number of whole minutes after the last touch) or Off. The
 * minutes are typed or stepped with − and +; a value that isn't a whole number in range isn't saved, and the field
 * says why.
 */
@Composable
private fun KeepScreenOnSetting(setting: KeepScreenOn, onChange: (KeepScreenOn) -> Unit) {
    // Remembered across switching kinds, so going Timed → Always → Timed keeps the chosen minutes.
    var minutes by rememberSaveable { mutableIntStateOf((setting as? KeepScreenOn.Timed)?.minutes ?: KeepScreenOn.DEFAULT_MINUTES) }
    var typed by rememberSaveable { mutableStateOf(minutes.toString()) }
    fun setMinutes(value: Int) {
        minutes = KeepScreenOn.clampMinutes(value)
        typed = minutes.toString()
        onChange(KeepScreenOn.Timed(minutes))
    }

    Text("Keep the screen on", style = MaterialTheme.typography.bodyMedium)
    Choices(
        options = KeepScreenOnKind.entries,
        selected = when (setting) {
            KeepScreenOn.Always -> KeepScreenOnKind.ALWAYS
            is KeepScreenOn.Timed -> KeepScreenOnKind.TIMED
            KeepScreenOn.Off -> KeepScreenOnKind.OFF
        },
        label = { it.label },
        onSelect = { kind ->
            onChange(
                when (kind) {
                    KeepScreenOnKind.ALWAYS -> KeepScreenOn.Always
                    KeepScreenOnKind.TIMED -> KeepScreenOn.Timed(minutes)
                    KeepScreenOnKind.OFF -> KeepScreenOn.Off
                },
            )
        },
    )
    if (setting is KeepScreenOn.Timed) {
        val valid = KeepScreenOn.parseMinutes(typed) != null
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(onClick = { setMinutes(minutes - 1) }, enabled = minutes > KeepScreenOn.MIN_MINUTES) {
                Text("−", style = MaterialTheme.typography.titleLarge)
            }
            OutlinedTextField(
                value = typed,
                onValueChange = { text ->
                    typed = text.filter(Char::isDigit).take(3)
                    KeepScreenOn.parseMinutes(typed)?.let {
                        minutes = it
                        onChange(KeepScreenOn.Timed(it))
                    }
                },
                singleLine = true,
                isError = !valid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                suffix = { Text("min") },
                modifier = Modifier.width(112.dp),
            )
            IconButton(onClick = { setMinutes(minutes + 1) }, enabled = minutes < KeepScreenOn.MAX_MINUTES) {
                Text("+", style = MaterialTheme.typography.titleLarge)
            }
        }
    }
    Text(
        when (setting) {
            KeepScreenOn.Always -> "The screen stays on for as long as the reader is open."
            is KeepScreenOn.Timed -> if (KeepScreenOn.parseMinutes(typed) != null) {
                "The screen stays on for ${setting.minutes} ${if (setting.minutes == 1) "minute" else "minutes"} after " +
                    "your last page turn or tap, then sleeps as usual."
            } else {
                "Enter whole minutes from ${KeepScreenOn.MIN_MINUTES} to ${KeepScreenOn.MAX_MINUTES}."
            }
            KeepScreenOn.Off -> "The screen sleeps on the phone's usual timer."
        },
        style = MaterialTheme.typography.bodySmall,
        color = if (setting is KeepScreenOn.Timed && KeepScreenOn.parseMinutes(typed) == null) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> Choices(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                label = { Text(label(option), maxLines = 1) },
            )
        }
    }
}

/** Pep, in nose pink, beside the note. */
@Composable
private fun MeowCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The drawable carries a launcher icon's padding around the cat, so it's drawn larger than its slot.
            Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                Icon(
                    painterResource(R.drawable.ic_launcher_monochrome),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.requiredSize(108.dp),
                )
            }
            Text(MEOW_NOTE, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/** The Open Font License asks for its text to travel with the font; it's bundled as res/raw/literata_ofl.txt. */
@Composable
private fun LicenceDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val text = remember {
        reflowLicence(context.resources.openRawResource(R.raw.literata_ofl).bufferedReader().use { it.readText() })
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Literata licence") },
        text = {
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
