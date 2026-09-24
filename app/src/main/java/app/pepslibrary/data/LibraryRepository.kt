package app.pepslibrary.data

import app.pepslibrary.download.DownloadResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The list of downloaded works. Depends on [WorkDao] only, so it can be tested with a fake. */
class LibraryRepository(
    private val dao: WorkDao,
    private val now: () -> Long = System::currentTimeMillis,
) {
    /** Most recently downloaded first. Emits again whenever the table changes. */
    val works: Flow<List<WorkEntity>> = dao.observeAll()

    /** Null if [workId] isn't in the library. Emits again if that changes (e.g. a fresh download lands). */
    fun isDownloaded(workId: Long): Flow<Boolean> = dao.observe(workId).map { it != null }

    /** Records a finished download. Downloading a work again replaces its row (new metadata, new timestamp). */
    suspend fun saveDownload(workId: Long, result: DownloadResult.Success) {
        val m = result.metadata
        dao.upsert(
            WorkEntity(
                workId = workId,
                // Never store a blank title, even if AO3's markup changed and nothing could be parsed.
                title = m.title ?: "Work $workId",
                authors = m.authors,
                summary = m.summary,
                rating = m.rating,
                warnings = m.warnings,
                categories = m.categories,
                fandoms = m.fandoms,
                relationships = m.relationships,
                characters = m.characters,
                tags = m.tags,
                language = m.language,
                words = m.words,
                chaptersPublished = m.chaptersPublished,
                chaptersTotal = m.chaptersTotal,
                publishedDate = m.publishedDate,
                updatedDate = m.updatedDate,
                sourceUpdatedAt = result.sourceUpdatedAt,
                epubFileName = result.file.name,
                fileSizeBytes = result.bytes,
                downloadedAt = now(),
            ),
        )
    }
}
