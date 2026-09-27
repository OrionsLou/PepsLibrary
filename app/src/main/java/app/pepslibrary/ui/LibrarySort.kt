package app.pepslibrary.ui

import app.pepslibrary.data.WorkEntity
import java.text.Normalizer
import java.util.Locale

enum class LibrarySort(val label: String) {
    DOWNLOADED("Date downloaded"),
    TITLE("Title"),
    AUTHOR("Author"),
}

/**
 * Orders the library. Ties fall back to newest download first, so the order is stable. Titles compare by
 * [titleSortKey]; a plain key rather than java.text.Collator, whose handling of spaces differs between the JVM the
 * tests run on and Android.
 */
fun sortWorks(works: List<WorkEntity>, sort: LibrarySort): List<WorkEntity> {
    val newestFirst = compareByDescending<WorkEntity> { it.downloadedAt }
    return when (sort) {
        LibrarySort.DOWNLOADED -> works.sortedWith(newestFirst)
        LibrarySort.TITLE -> works.sortedWith(compareBy<WorkEntity> { titleSortKey(it.title) }.then(newestFirst))
        // By the first listed author, AO3's byline order; one author's works by title. No known author goes last.
        LibrarySort.AUTHOR -> works.sortedWith(
            compareBy<WorkEntity> { it.authors.isEmpty() }
                .thenBy { it.authors.firstOrNull()?.let(::titleSortKey) }
                .thenBy { titleSortKey(it.title) }
                .then(newestFirst),
        )
    }
}

/**
 * Used for author names too. Case and accents ignored ("Élan" sorts with "elan"), a leading "The"/"A" kept as AO3 does, and word by word: a
 * space sorts before any letter, so "A Quiet Place" comes before "Album".
 */
internal fun titleSortKey(title: String): String =
    Normalizer.normalize(title.trim(), Normalizer.Form.NFD)
        .replace(COMBINING_MARKS, "")
        .lowercase(Locale.ROOT)

private val COMBINING_MARKS = Regex("\\p{Mn}+")
