package app.pepslibrary.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * Where the reader last was in a work. Kept apart from [WorkEntity] so re-downloading a work (which upserts its
 * row) never touches it, and deleting a work takes its progress with it.
 */
@Entity(
    tableName = "reading_progress",
    foreignKeys = [
        ForeignKey(
            entity = WorkEntity::class,
            parentColumns = ["workId"],
            childColumns = ["workId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ReadingProgressEntity(
    @PrimaryKey val workId: Long,
    /** Readium's Locator as JSON: the chapter plus the position inside it. Opaque to us; only Readium reads it. */
    val locatorJson: String,
    /** Fraction of the whole work read, 0.0 to 1.0, for display. Null if Readium couldn't say. */
    val totalProgression: Double?,
    /** Epoch millis of the last save. */
    val updatedAt: Long,
)
