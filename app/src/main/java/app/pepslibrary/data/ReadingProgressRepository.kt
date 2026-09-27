package app.pepslibrary.data

import app.pepslibrary.epub.EpubChapter
import app.pepslibrary.epub.LocatorJson
import app.pepslibrary.epub.PositionUpdate
import app.pepslibrary.epub.reconcilePosition
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Saved reading positions. Depends on [ReadingProgressDao] only, so it can be tested with a fake. */
class ReadingProgressRepository(
    private val dao: ReadingProgressDao,
    private val now: () -> Long = System::currentTimeMillis,
) {
    /** Fraction read (0.0 to 1.0, or null if unknown) for each work that has a saved position. */
    val fractions: Flow<Map<Long, Double?>> = dao.observeAll().map { rows ->
        rows.associate { it.workId to it.totalProgression }
    }

    suspend fun get(workId: Long): ReadingProgressEntity? = dao.get(workId)

    /** After a re-download replaced [before] with [after], moves the saved position if it no longer holds. */
    suspend fun reconcileAfterUpdate(workId: Long, before: List<EpubChapter>, after: List<EpubChapter>) {
        val row = dao.get(workId) ?: return
        val savedPath = LocatorJson.path(row.locatorJson) ?: return
        val updated = when (val update = reconcilePosition(savedPath, before, after)) {
            PositionUpdate.Keep -> return
            is PositionUpdate.Moved -> row.copy(locatorJson = LocatorJson.moveTo(row.locatorJson, update.path))
            is PositionUpdate.ChapterStart ->
                row.copy(locatorJson = LocatorJson.startOf(update.path), notice = PositionNotice.CHAPTER_CHANGED)
            is PositionUpdate.WorkStart -> row.copy(
                locatorJson = LocatorJson.startOf(update.path),
                totalProgression = 0.0,
                notice = PositionNotice.CHAPTER_REMOVED,
            )
        }
        dao.upsert(updated.copy(updatedAt = now()))
    }

    suspend fun save(workId: Long, locatorJson: String, totalProgression: Double?) {
        dao.upsert(
            ReadingProgressEntity(
                workId = workId,
                locatorJson = locatorJson,
                // Readium can overshoot slightly at the very end; the display code expects 0..1.
                totalProgression = totalProgression?.takeUnless { it.isNaN() }?.coerceIn(0.0, 1.0),
                updatedAt = now(),
            ),
        )
    }
}
