package app.pepslibrary.ui

import androidx.compose.runtime.saveable.Saver
import app.pepslibrary.data.WorkEntity

/** Stands in for "no author listed" among [LibraryFilter.authors]; AO3 never has an empty author name. */
const val NO_AUTHOR = ""

/**
 * Which works the library shows. Within one kind, any selected value matches (these authors OR those); different
 * kinds combine, so every active kind must match. An empty selection means that kind doesn't filter.
 */
data class LibraryFilter(val authors: Set<String> = emptySet()) {
    val isActive: Boolean get() = authors.isNotEmpty()

    /** Selected values across every kind, for the "Filter (n)" button. */
    val selectedCount: Int get() = authors.size

    fun matches(work: WorkEntity): Boolean =
        authors.isEmpty() || authorKeys(work).any { it in authors }

    fun toggleAuthor(author: String): LibraryFilter =
        copy(authors = if (author in authors) authors - author else authors + author)
}

/** Lets the filter survive the library closing and the activity being recreated, like the sort does. */
val LibraryFilterSaver: Saver<LibraryFilter, ArrayList<String>> = Saver(
    save = { ArrayList(it.authors) },
    restore = { LibraryFilter(authors = it.toSet()) },
)

/** One checkbox in the filter sheet: [value] is what's stored in the filter, [count] how many works have it. */
data class FilterOption(val value: String, val label: String, val count: Int)

/**
 * The authors in the library, alphabetical (compared like titles), each with how many works list them. A selected
 * author who no longer has any works (e.g. after a delete) is still listed with 0, so it can be unchecked.
 */
fun authorOptions(works: List<WorkEntity>, filter: LibraryFilter): List<FilterOption> {
    val counts = works.flatMap { authorKeys(it) }.groupingBy { it }.eachCount()
    val keys = counts.keys + filter.authors
    return keys
        .sortedWith(compareBy<String> { it == NO_AUTHOR }.thenBy { titleSortKey(it) }.thenBy { it })
        .map { FilterOption(it, if (it == NO_AUTHOR) "No author listed" else it, counts[it] ?: 0) }
}

/** A co-authored work counts once under each of its authors. */
private fun authorKeys(work: WorkEntity): Set<String> = work.authors.toSet().ifEmpty { setOf(NO_AUTHOR) }
