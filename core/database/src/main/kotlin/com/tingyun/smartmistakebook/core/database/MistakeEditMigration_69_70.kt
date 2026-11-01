package com.tingyun.smartmistakebook.core.database

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

internal val MISTAKE_EDIT_MIGRATION_69_70 = object : Migration(69, 70) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("""CREATE TABLE IF NOT EXISTS `mistake_edit_job` (
            `operation_id` TEXT NOT NULL, `entry_id` TEXT NOT NULL, `basis_revision_id` TEXT NOT NULL,
            `draft_sha256` TEXT NOT NULL, `semantic_fingerprint` TEXT NOT NULL,
            `request_id` TEXT NOT NULL, `request_snapshot` TEXT NOT NULL, `request_fingerprint` TEXT NOT NULL,
            `generation` INTEGER NOT NULL, `status` TEXT NOT NULL, `state_version` INTEGER NOT NULL,
            `output_snapshot` TEXT, `figures_snapshot` TEXT NOT NULL, `message` TEXT,
            `created_at_epoch_millis` INTEGER NOT NULL, `updated_at_epoch_millis` INTEGER NOT NULL,
            `committed_revision_id` TEXT, PRIMARY KEY(`operation_id`),
            FOREIGN KEY(`entry_id`) REFERENCES `error_book_entry`(`entry_id`) ON UPDATE NO ACTION ON DELETE CASCADE,
            FOREIGN KEY(`basis_revision_id`) REFERENCES `problem_revision`(`revision_id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
            FOREIGN KEY(`committed_revision_id`) REFERENCES `problem_revision`(`revision_id`) ON UPDATE NO ACTION ON DELETE RESTRICT)""")
        listOf("entry_id", "basis_revision_id", "committed_revision_id").forEach { column ->
            connection.execSQL("CREATE INDEX IF NOT EXISTS `index_mistake_edit_job_$column` ON `mistake_edit_job` (`$column`)")
        }
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_mistake_edit_job_status_operation_id` ON `mistake_edit_job` (`status`, `operation_id`)")
    }
}
