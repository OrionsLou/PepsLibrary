package app.pepslibrary.ui

import app.pepslibrary.data.WorkEntity
import java.util.Locale
import kotlin.math.roundToInt

/**
 * How far into a work the reader is, from the saved fraction (0.0 to 1.0, or null if never opened or unknown):
 * "Not started", "42% read" or "Finished".
 */
internal fun readingProgressLabel(fraction: Double?): String = when {
    fraction == null || fraction < 0.005 -> "Not started"
    fraction >= 0.995 -> "Finished"
    else -> "${(fraction * 100).roundToInt()}% read"
}

enum class CompletionStatus(val label: String) {
    COMPLETED("Completed"),
    WIP("Work in progress"),
    OTHER("Other (chapter count unknown)"),
}

/**
 * A work is finished once every planned chapter is up; with no planned total ("3/?") it is still going. OTHER is a
 * work whose chapter count couldn't be read from AO3's page at download time.
 */
internal fun completionStatus(work: WorkEntity): CompletionStatus {
    val published = work.chaptersPublished ?: return CompletionStatus.OTHER
    val total = work.chaptersTotal
    return if (total != null && published >= total) CompletionStatus.COMPLETED else CompletionStatus.WIP
}

/** "by A, B", or null when the work has no known author. */
internal fun workByline(work: WorkEntity): String? =
    work.authors.takeIf { it.isNotEmpty() }?.joinToString(", ")?.let { "by $it" }

/** e.g. "8,994 words · 3/3 chapters · Complete". Parts AO3 didn't give us are left out. */
internal fun workStatsLine(work: WorkEntity): String {
    val words = work.words?.let { String.format(Locale.US, "%,d %s", it, if (it == 1) "word" else "words") }

    val published = work.chaptersPublished
    val total = work.chaptersTotal
    val chapters = published?.let {
        if (it == 1 && total == 1) "1 chapter" else "$it/${total ?: "?"} chapters"
    }
    val status = when (completionStatus(work)) {
        CompletionStatus.COMPLETED -> "Complete"
        CompletionStatus.WIP -> "In progress"
        CompletionStatus.OTHER -> null
    }

    return listOfNotNull(words, chapters, status).joinToString(" · ")
}
