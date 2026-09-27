package app.pepslibrary.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkDao {
    /** Inserts, or updates in place if the work is already there (a re-download), so related rows survive. */
    @Upsert
    suspend fun upsert(work: WorkEntity)

    @Query("SELECT * FROM works ORDER BY downloadedAt DESC")
    fun observeAll(): Flow<List<WorkEntity>>

    @Query("SELECT * FROM works WHERE workId = :workId")
    suspend fun get(workId: Long): WorkEntity?

    @Query("UPDATE works SET lastOpenedAt = :at WHERE workId = :workId")
    suspend fun markOpened(workId: Long, at: Long)

    /** Its reading position goes with it (foreign key cascade). */
    @Query("DELETE FROM works WHERE workId = :workId")
    suspend fun delete(workId: Long)

    /** Emits again whenever the row for [workId] appears, changes, or is removed. */
    @Query("SELECT * FROM works WHERE workId = :workId")
    fun observe(workId: Long): Flow<WorkEntity?>
}
