package app.pepslibrary.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 1 -> 2: adds reading positions. Existing rows in `works` are untouched. The SQL is copied from the schema Room
 * exported for version 2 (app/schemas/.../2.json); Room checks the result against that schema when opening.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `reading_progress` (`workId` INTEGER NOT NULL, " +
                "`locatorJson` TEXT NOT NULL, `totalProgression` REAL, `updatedAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`workId`), " +
                "FOREIGN KEY(`workId`) REFERENCES `works`(`workId`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
    }
}
