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

/**
 * 4 -> 5: adds when each work was last opened. Works already read get the time of their last saved reading position,
 * which is the closest record there is, so the new "Last read" sort is useful straight away.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `works` ADD COLUMN `lastOpenedAt` INTEGER")
        db.execSQL(
            "UPDATE `works` SET `lastOpenedAt` = " +
                "(SELECT `updatedAt` FROM `reading_progress` WHERE `reading_progress`.`workId` = `works`.`workId`)",
        )
    }
}

/** 3 -> 4: adds the one-time notice shown when a re-download moved a reading position back. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `reading_progress` ADD COLUMN `notice` TEXT")
    }
}
