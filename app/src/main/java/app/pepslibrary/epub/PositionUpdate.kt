package app.pepslibrary.epub

/** What a re-download means for a saved reading position. Paths are zip paths, as in [EpubChapter.path]. */
sealed interface PositionUpdate {
    /** The chapter is byte-for-byte unchanged at the same path: the saved position is still exact. */
    data object Keep : PositionUpdate

    /** The chapter is unchanged but its file was renumbered (a chapter before it was added or removed). */
    data class Moved(val path: String) : PositionUpdate

    /** The chapter itself changed, so the exact spot can't be trusted: go to its start, found by heading. */
    data class ChapterStart(val path: String) : PositionUpdate

    /** The chapter can't be found at all any more: go to the start of the work. */
    data class WorkStart(val path: String) : PositionUpdate
}

/**
 * Decides where a position saved in [savedPath] of the [before] EPUB belongs in the [after] one. Calibre names
 * AO3's chapter files by their place in the book (`..._split_002.xhtml`), so a file name alone can silently point
 * at a different chapter after an earlier one is removed; matching by content hash is what makes this safe.
 */
fun reconcilePosition(savedPath: String, before: List<EpubChapter>, after: List<EpubChapter>): PositionUpdate {
    if (after.isEmpty()) return PositionUpdate.Keep
    val saved = before.find { it.path == savedPath }
        ?: return if (after.any { it.path == savedPath }) PositionUpdate.Keep else PositionUpdate.WorkStart(after.first().path)

    if (after.any { it.path == savedPath && it.sha256 == saved.sha256 }) return PositionUpdate.Keep
    after.find { it.sha256 == saved.sha256 }?.let { return PositionUpdate.Moved(it.path) }
    saved.heading?.let { heading -> after.find { it.heading == heading } }?.let { return PositionUpdate.ChapterStart(it.path) }
    return PositionUpdate.WorkStart(after.first().path)
}
