package app.pepslibrary.data

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
