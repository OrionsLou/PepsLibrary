package app.pepslibrary.ui

import androidx.compose.runtime.saveable.Saver
import app.pepslibrary.data.WorkEntity

/**
 * Stands in for "none listed" among [LibraryFilter.authors] and [LibraryFilter.fandoms]; AO3 never has an empty
 * author or fandom name.
 */
const val NONE_LISTED = ""

/**
 * Which works the library shows. Within one kind, any selected value matches (these authors OR those); different
 * kinds combine, so every active kind must match. An empty selection means that kind doesn't filter.
 */
data class LibraryFilter(
    val authors: Set<String> = emptySet(),
    val statuses: Set<CompletionStatus> = emptySet(),
    val fandoms: Set<String> = emptySet(),
    val pinnedOnly: Boolean = false,
) {
    val isActive: Boolean get() = selectedCount > 0

    /** Selected values across every kind, for the "Filter (n)" button. */
    val selectedCount: Int get() = authors.size + statuses.size + fandoms.size + (if (pinnedOnly) 1 else 0)

    fun matches(work: WorkEntity): Boolean =
        (!pinnedOnly || work.pinned) &&
            (authors.isEmpty() || authorKeys(work).any { it in authors }) &&
            (statuses.isEmpty() || completionStatus(work) in statuses) &&
            (fandoms.isEmpty() || fandomKeys(work).any { it in fandoms })

    fun togglePinnedOnly(): LibraryFilter = copy(pinnedOnly = !pinnedOnly)

    fun toggleAuthor(author: String): LibraryFilter = copy(authors = authors.toggle(author))

    fun toggleStatus(status: CompletionStatus): LibraryFilter = copy(statuses = statuses.toggle(status))

    fun toggleFandom(fandom: String): LibraryFilter = copy(fandoms = fandoms.toggle(fandom))
}

private fun <T> Set<T>.toggle(value: T): Set<T> = if (value in this) this - value else this + value

/** Lets the filter survive the library closing and the activity being recreated, like the sort does. */
val LibraryFilterSaver: Saver<LibraryFilter, ArrayList<ArrayList<String>>> = Saver(
    save = {
        arrayListOf(
            ArrayList(it.authors),
            ArrayList(it.statuses.map { s -> s.name }),
            ArrayList(it.fandoms),
            arrayListOf(it.pinnedOnly.toString()),
        )
    },
    restore = {
        LibraryFilter(
            authors = it[0].toSet(),
            statuses = it[1].map(CompletionStatus::valueOf).toSet(),
            fandoms = it[2].toSet(),
            pinnedOnly = it[3].single().toBoolean(),
        )
    },
)

/** One checkbox in the filter sheet: [value] is what's stored in the filter, [count] how many works have it. */
data class FilterOption(val value: String, val label: String, val count: Int)

/** The single "Pinned only" choice, with how many works are pinned. */
fun pinnedOption(works: List<WorkEntity>): FilterOption =
    FilterOption("pinned", "Pinned only", works.count { it.pinned })

/** The authors in the library; see [namedOptions]. */
fun authorOptions(works: List<WorkEntity>, filter: LibraryFilter): List<FilterOption> =
    namedOptions(works.map(::authorKeys), filter.authors, noneLabel = "No author listed")

/** The fandoms in the library; see [namedOptions]. */
fun fandomOptions(works: List<WorkEntity>, filter: LibraryFilter): List<FilterOption> =
    namedOptions(works.map(::fandomKeys), filter.fandoms, noneLabel = "No fandom listed")

/** All three statuses, always in the same order and always listed (even at 0), so the section never shifts. */
fun statusOptions(works: List<WorkEntity>): List<FilterOption> {
    val counts = works.groupingBy(::completionStatus).eachCount()
    return CompletionStatus.entries.map { FilterOption(it.name, it.label, counts[it] ?: 0) }
}

/**
 * Every name across [keysPerWork], alphabetical (compared like titles) with "none listed" last, each with how many
 * works have it. A selected name no longer in any work (e.g. after a delete) is still listed with 0, so it can be
 * unchecked.
 */
private fun namedOptions(keysPerWork: List<Set<String>>, selected: Set<String>, noneLabel: String): List<FilterOption> {
    val counts = keysPerWork.flatten().groupingBy { it }.eachCount()
    return (counts.keys + selected)
        .sortedWith(compareBy<String> { it == NONE_LISTED }.thenBy { titleSortKey(it) }.thenBy { it })
        .map { FilterOption(it, if (it == NONE_LISTED) noneLabel else it, counts[it] ?: 0) }
}

/** A co-authored work counts once under each of its authors. */
private fun authorKeys(work: WorkEntity): Set<String> = work.authors.toSet().ifEmpty { setOf(NONE_LISTED) }

/** A crossover counts once under each of its fandoms. */
private fun fandomKeys(work: WorkEntity): Set<String> = work.fandoms.toSet().ifEmpty { setOf(NONE_LISTED) }
