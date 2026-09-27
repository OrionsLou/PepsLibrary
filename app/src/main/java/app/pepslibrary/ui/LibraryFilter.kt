package app.pepslibrary.ui

import androidx.compose.runtime.saveable.Saver
import app.pepslibrary.data.WorkEntity

/** Stands in for "no author listed" among [LibraryFilter.authors]; AO3 never has an empty author name. */
const val NO_AUTHOR = ""

/**
 * Which works the library shows. Within one kind, any selected value matches (these authors OR those); different
 * kinds combine, so every active kind must match. An empty selection means that kind doesn't filter.
 */
data class LibraryFilter(
    val authors: Set<String> = emptySet(),
    val statuses: Set<CompletionStatus> = emptySet(),
) {
    val isActive: Boolean get() = selectedCount > 0

    /** Selected values across every kind, for the "Filter (n)" button. */
    val selectedCount: Int get() = authors.size + statuses.size

    fun matches(work: WorkEntity): Boolean =
        (authors.isEmpty() || authorKeys(work).any { it in authors }) &&
            (statuses.isEmpty() || completionStatus(work) in statuses)

    fun toggleAuthor(author: String): LibraryFilter = copy(authors = authors.toggle(author))

    fun toggleStatus(status: CompletionStatus): LibraryFilter = copy(statuses = statuses.toggle(status))
}

private fun <T> Set<T>.toggle(value: T): Set<T> = if (value in this) this - value else this + value

/** Lets the filter survive the library closing and the activity being recreated, like the sort does. */
val LibraryFilterSaver: Saver<LibraryFilter, ArrayList<ArrayList<String>>> = Saver(
    save = { arrayListOf(ArrayList(it.authors), ArrayList(it.statuses.map { s -> s.name })) },
    restore = { LibraryFilter(authors = it[0].toSet(), statuses = it[1].map(CompletionStatus::valueOf).toSet()) },
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

/** All three statuses, always in the same order and always listed (even at 0), so the section never shifts. */
fun statusOptions(works: List<WorkEntity>): List<FilterOption> {
    val counts = works.groupingBy(::completionStatus).eachCount()
    return CompletionStatus.entries.map { FilterOption(it.name, it.label, counts[it] ?: 0) }
}

/** A co-authored work counts once under each of its authors. */
private fun authorKeys(work: WorkEntity): Set<String> = work.authors.toSet().ifEmpty { setOf(NO_AUTHOR) }
