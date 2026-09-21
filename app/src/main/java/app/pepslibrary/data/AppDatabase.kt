package app.pepslibrary.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Bump [version] and add a Migration whenever the schema changes; never fall back to destructive migration, since
 * rows point at EPUB files on disk that would be orphaned. Room writes each version's schema to app/schemas.
 */
@Database(entities = [WorkEntity::class], version = 1, exportSchema = true)
@TypeConverters(StringListConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun workDao(): WorkDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "pepslibrary.db")
                .build()
                .also { instance = it }
        }
    }
}
