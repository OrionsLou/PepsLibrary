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

/**
 * 2 -> 3: adds the download queue. Existing rows in `works` and `reading_progress` are untouched. The SQL is
 * copied from the schema Room exported for version 3 (app/schemas/.../3.json), same as MIGRATION_1_2.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `download_queue` (`workId` INTEGER NOT NULL, `status` TEXT NOT NULL, " +
                "`attempts` INTEGER NOT NULL, `notBeforeMillis` INTEGER, `lastFailureKind` TEXT, " +
                "`lastFailureMessage` TEXT, `enqueuedAt` INTEGER NOT NULL, PRIMARY KEY(`workId`))",
        )
    }
}
