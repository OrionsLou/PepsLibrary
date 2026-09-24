package app.pepslibrary.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadQueueDao {
    /** Adding a work already in the queue (any status) upserts it, rather than creating a second row. */
    @Upsert
    suspend fun upsert(entry: DownloadQueueEntity)

    @Query("SELECT * FROM download_queue WHERE workId = :workId")
    suspend fun get(workId: Long): DownloadQueueEntity?

    @Query("SELECT * FROM download_queue ORDER BY enqueuedAt ASC")
    fun observeAll(): Flow<List<DownloadQueueEntity>>

    @Query("DELETE FROM download_queue WHERE workId = :workId")
    suspend fun delete(workId: Long)

    /** Also clears any pending retry-wait, since a status change makes it stale either way. */
    @Query("UPDATE download_queue SET status = :status, notBeforeMillis = NULL WHERE workId = :workId")
    suspend fun setStatus(workId: Long, status: QueueStatus)

    /** Bulk version of [setStatus], for every row currently in a given status (e.g. recovering after a crash). */
    @Query("UPDATE download_queue SET status = :to, notBeforeMillis = NULL WHERE status = :from")
    suspend fun resetStatus(from: QueueStatus, to: QueueStatus)
}
