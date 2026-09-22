package app.pepslibrary.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReadingProgressRepositoryTest {
    /** In-memory stand-in for the Room DAO, with the same replace-by-key upsert. */
    private class FakeDao : ReadingProgressDao {
        val rows = MutableStateFlow<Map<Long, ReadingProgressEntity>>(emptyMap())
        override suspend fun upsert(progress: ReadingProgressEntity) { rows.value = rows.value + (progress.workId to progress) }
        override suspend fun get(workId: Long): ReadingProgressEntity? = rows.value[workId]
        override fun observeAll(): Flow<List<ReadingProgressEntity>> = rows.map { it.values.toList() }
    }

    private val dao = FakeDao()
    private var clock = 1_000L
    private val repository = ReadingProgressRepository(dao) { clock }

    private val locator = """{"href":"chapter2.xhtml","type":"application/xhtml+xml","locations":{"progression":0.5,"totalProgression":0.4}}"""

    @Test
    fun nothingIsSavedForAWorkNeverOpened() = runBlocking {
        assertNull(repository.get(42))
        assertEquals(emptyMap<Long, Double?>(), repository.fractions.first())
    }

    @Test
    fun aSavedPositionComesBackExactly() = runBlocking {
        clock = 5_000L
        repository.save(42, locator, 0.4)

        val row = repository.get(42)!!
        assertEquals(42L, row.workId)
        assertEquals(locator, row.locatorJson)
        assertEquals(0.4, row.totalProgression!!, 0.0)
        assertEquals(5_000L, row.updatedAt)
    }

    @Test
    fun savingAgainReplacesThePositionInsteadOfAddingARow() = runBlocking {
        repository.save(42, "first", 0.1)
        clock = 9_000L
        repository.save(42, "second", 0.7)

        assertEquals(1, dao.rows.value.size)
        val row = repository.get(42)!!
        assertEquals("second", row.locatorJson)
        assertEquals(0.7, row.totalProgression!!, 0.0)
        assertEquals(9_000L, row.updatedAt)
    }

    @Test
    fun eachWorkKeepsItsOwnPosition() = runBlocking {
        repository.save(1, "one", 0.25)
        repository.save(2, "two", 0.75)
        assertEquals("one", repository.get(1)!!.locatorJson)
        assertEquals("two", repository.get(2)!!.locatorJson)
    }

    @Test
    fun fractionsMapsEachWorkToHowFarItIs() = runBlocking {
        repository.save(1, "a", 0.25)
        repository.save(2, "b", null)
        assertEquals(mapOf(1L to 0.25, 2L to null), repository.fractions.first())
    }

    @Test
    fun anOvershootingOrBrokenFractionIsClampedOrDropped() = runBlocking {
        repository.save(1, "a", 1.0000001)
        repository.save(2, "b", -0.2)
        repository.save(3, "c", Double.NaN)
        assertEquals(1.0, repository.get(1)!!.totalProgression!!, 0.0)
        assertEquals(0.0, repository.get(2)!!.totalProgression!!, 0.0)
        assertNull(repository.get(3)!!.totalProgression)
    }
}
