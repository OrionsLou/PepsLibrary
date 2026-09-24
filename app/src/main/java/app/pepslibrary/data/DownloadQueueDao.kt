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
}
