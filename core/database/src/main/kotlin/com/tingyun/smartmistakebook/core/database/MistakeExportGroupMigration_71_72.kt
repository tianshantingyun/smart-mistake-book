package com.tingyun.smartmistakebook.core.database

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

internal val MISTAKE_EXPORT_GROUP_MIGRATION_71_72 = object : Migration(71, 72) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("""CREATE TABLE IF NOT EXISTS `mistake_export_group` (
            `group_id` TEXT NOT NULL, `manifest_id` TEXT NOT NULL, `manifest_sha256` TEXT NOT NULL,
            `format_token` TEXT NOT NULL, `requested_answers` INTEGER NOT NULL, `questions_export_id` TEXT,
            `answers_export_id` TEXT, `preview_sha256` TEXT, `solutions_sha256` TEXT,
            `state_version` INTEGER NOT NULL, `created_at_epoch_millis` INTEGER NOT NULL, PRIMARY KEY(`group_id`),
            FOREIGN KEY(`manifest_id`) REFERENCES `detailed_solution_manifest`(`manifest_id`) ON UPDATE NO ACTION ON DELETE RESTRICT)""")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_mistake_export_group_manifest_id` ON `mistake_export_group` (`manifest_id`)")
        connection.execSQL("ALTER TABLE `mistake_export_record` ADD COLUMN `group_id` TEXT REFERENCES `mistake_export_group`(`group_id`) ON UPDATE NO ACTION ON DELETE RESTRICT")
        connection.execSQL("ALTER TABLE `mistake_export_record` ADD COLUMN `artifact_role` TEXT NOT NULL DEFAULT 'QUESTIONS'")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_mistake_export_record_group_id` ON `mistake_export_record` (`group_id`)")
    }
}
