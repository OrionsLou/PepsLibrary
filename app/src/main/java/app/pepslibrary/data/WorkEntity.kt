package app.pepslibrary.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One downloaded work: what AO3 said about it when we downloaded it, plus where the EPUB is. */
@Entity(tableName = "works")
data class WorkEntity(
    @PrimaryKey val workId: Long,
    val title: String,
    val authors: List<String>,
    val summary: String?,
    val rating: String?,
    val warnings: List<String>,
    val categories: List<String>,
    val fandoms: List<String>,
    val relationships: List<String>,
    val characters: List<String>,
    val tags: List<String>,
    val language: String?,
    val words: Int?,
    val chaptersPublished: Int?,
    /** Null while the author hasn't fixed a total ("3/?"). */
    val chaptersTotal: Int?,
    /** ISO dates (yyyy-MM-dd), so they sort as text. */
    val publishedDate: String?,
    val updatedDate: String?,
    /** AO3's `updated_at` for the downloaded version; changes when the work does. */
    val sourceUpdatedAt: Long?,
    /** File name only, inside the app's works folder, so it survives the app's data directory moving. */
    val epubFileName: String,
    val fileSizeBytes: Long,
    /** Epoch millis of the latest download of this work. */
    val downloadedAt: Long,
)
