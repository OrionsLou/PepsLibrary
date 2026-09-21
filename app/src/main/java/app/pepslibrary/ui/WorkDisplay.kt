package app.pepslibrary.ui

import app.pepslibrary.data.WorkEntity
import java.util.Locale

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
    // A work is finished once every planned chapter is up. With no planned total ("3/?") it is still going.
    val status = published?.let { if (total != null && it >= total) "Complete" else "In progress" }

    return listOfNotNull(words, chapters, status).joinToString(" · ")
}
