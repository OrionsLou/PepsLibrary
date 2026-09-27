package app.pepslibrary.ui

import android.content.Context

/**
 * The library's sort and filter as plain values SharedPreferences can hold (names, not enums), so a choice
 * survives an app restart. Kept separate from [LibraryViewStore] so the conversion can be unit tested on the JVM.
 */
data class SavedLibraryView(
    val sort: String? = null,
    val reversed: Boolean = false,
    val authors: Set<String> = emptySet(),
    val statuses: Set<String> = emptySet(),
    val fandoms: Set<String> = emptySet(),
    val pinnedOnly: Boolean = false,
) {
    /**
     * Anything a later version no longer knows, such as a renamed sort or status, is dropped rather than crashing
     * on start: an unknown sort falls back to the default order, an unknown status is simply not selected.
     */
    fun toOrder(): LibraryOrder =
        LibrarySort.entries.find { it.name == sort }?.let { LibraryOrder(it, reversed) } ?: LibraryOrder()

    fun toFilter(): LibraryFilter = LibraryFilter(
        authors = authors,
        statuses = statuses.mapNotNull { name -> CompletionStatus.entries.find { it.name == name } }.toSet(),
        fandoms = fandoms,
        pinnedOnly = pinnedOnly,
    )

    companion object {
        fun of(order: LibraryOrder, filter: LibraryFilter) = SavedLibraryView(
            sort = order.sort.name,
            reversed = order.reversed,
            authors = filter.authors,
            statuses = filter.statuses.map { it.name }.toSet(),
            fandoms = filter.fandoms,
            pinnedOnly = filter.pinnedOnly,
        )
    }
}

/** Where [SavedLibraryView] lives: its own small SharedPreferences file, read once at start and written on change. */
class LibraryViewStore(context: Context) {
    private val prefs = context.getSharedPreferences("library_view", Context.MODE_PRIVATE)

    fun load(): SavedLibraryView = SavedLibraryView(
        sort = prefs.getString(SORT, null),
        reversed = prefs.getBoolean(REVERSED, false),
        // Copied: SharedPreferences doesn't allow the returned sets to be kept or modified.
        authors = prefs.getStringSet(AUTHORS, null).orEmpty().toSet(),
        statuses = prefs.getStringSet(STATUSES, null).orEmpty().toSet(),
        fandoms = prefs.getStringSet(FANDOMS, null).orEmpty().toSet(),
        pinnedOnly = prefs.getBoolean(PINNED_ONLY, false),
    )

    fun save(view: SavedLibraryView) {
        prefs.edit()
            .putString(SORT, view.sort)
            .putBoolean(REVERSED, view.reversed)
            .putStringSet(AUTHORS, view.authors)
            .putStringSet(STATUSES, view.statuses)
            .putStringSet(FANDOMS, view.fandoms)
            .putBoolean(PINNED_ONLY, view.pinnedOnly)
            .apply()
    }

    private companion object {
        const val SORT = "sort"
        const val REVERSED = "reversed"
        const val AUTHORS = "authors"
        const val STATUSES = "statuses"
        const val FANDOMS = "fandoms"
        const val PINNED_ONLY = "pinnedOnly"
    }
}
