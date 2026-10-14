package id.web.agentvaedah.strideway.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Every schema change bumps [StridewayDatabase] version and adds a step here. Installed phones already
 * hold real rides, so there is deliberately no destructive fallback: a missing step must fail loudly in
 * testing instead of wiping a user's history. Room checks the result against the entities, so column
 * types, NOT NULL, defaults and index names below must match the entity declarations exactly.
 */
object Migrations {
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `activities` ADD COLUMN `description` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `activities` ADD COLUMN `hideStartM` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `activities` ADD COLUMN `hideEndM` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `activities` ADD COLUMN `hideMap` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `activities` ADD COLUMN `effortsVersion` INTEGER NOT NULL DEFAULT 0")

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `best_efforts` (`activityId` INTEGER NOT NULL, `effortKey` TEXT NOT NULL, " +
                    "`seconds` REAL NOT NULL, `startIdx` INTEGER NOT NULL, `endIdx` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`activityId`, `effortKey`), FOREIGN KEY(`activityId`) REFERENCES `activities`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `activity_media` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`activityId` INTEGER NOT NULL, `path` TEXT NOT NULL, `addedMs` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`activityId`) REFERENCES `activities`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_activity_media_activityId` ON `activity_media` (`activityId`)")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `saved_places` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`name` TEXT NOT NULL, `lat` REAL NOT NULL, `lon` REAL NOT NULL, `createdMs` INTEGER NOT NULL)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `bike_components` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`type` TEXT NOT NULL, `label` TEXT NOT NULL, `installMs` INTEGER NOT NULL, `kmAtInstall` REAL NOT NULL, " +
                    "`serviceIntervalKm` REAL NOT NULL, `active` INTEGER NOT NULL)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `component_events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`componentId` INTEGER NOT NULL, `kind` TEXT NOT NULL, `atMs` INTEGER NOT NULL, `note` TEXT, " +
                    "FOREIGN KEY(`componentId`) REFERENCES `bike_components`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_component_events_componentId` ON `component_events` (`componentId`)")
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `route_maneuvers` (`routeId` INTEGER NOT NULL, `seq` INTEGER NOT NULL, " +
                    "`pointIndex` INTEGER NOT NULL, `type` TEXT NOT NULL, `exitNumber` INTEGER NOT NULL, `street` TEXT, " +
                    "PRIMARY KEY(`routeId`, `seq`), FOREIGN KEY(`routeId`) REFERENCES `routes`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `training_plans` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`goal` TEXT NOT NULL, `startEpochDay` INTEGER NOT NULL, `eventEpochDay` INTEGER NOT NULL, " +
                    "`level` TEXT NOT NULL, `createdMs` INTEGER NOT NULL, `weeksJson` TEXT NOT NULL)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `planned_sessions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`planId` INTEGER NOT NULL, `epochDay` INTEGER NOT NULL, `workoutId` TEXT NOT NULL, `doneActivityId` INTEGER, " +
                    "FOREIGN KEY(`planId`) REFERENCES `training_plans`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_planned_sessions_planId` ON `planned_sessions` (`planId`)")
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
}
