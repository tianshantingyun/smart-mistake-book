package com.tingyun.smartmistakebook.core.database

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/** Adds library editing and paired answer export after the published visual snapshot migrations. */
internal val LIBRARY_INTERIOR_MIGRATION_71_72 = object : Migration(71, 72) {
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
        connection.execSQL("""CREATE TABLE IF NOT EXISTS `detailed_solution_manifest` (
            `manifest_id` TEXT NOT NULL, `paper_draft_id` TEXT NOT NULL, `manifest_snapshot` TEXT NOT NULL,
            `manifest_sha256` TEXT NOT NULL, `input_sha256` TEXT NOT NULL, `status` TEXT NOT NULL,
            `state_version` INTEGER NOT NULL, `created_at_epoch_millis` INTEGER NOT NULL,
            `updated_at_epoch_millis` INTEGER NOT NULL, PRIMARY KEY(`manifest_id`))""")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_detailed_solution_manifest_paper_draft_id` ON `detailed_solution_manifest` (`paper_draft_id`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_detailed_solution_manifest_status` ON `detailed_solution_manifest` (`status`)")
        connection.execSQL("""CREATE TABLE IF NOT EXISTS `detailed_solution` (
            `solution_id` TEXT NOT NULL, `revision_id` TEXT NOT NULL, `question_sha256` TEXT NOT NULL,
            `protocol_version` TEXT NOT NULL, `request_id` TEXT NOT NULL, `request_snapshot` TEXT NOT NULL,
            `request_fingerprint` TEXT NOT NULL, `output_snapshot` TEXT, `document_snapshot` TEXT,
            `document_sha256` TEXT, `figures_snapshot` TEXT NOT NULL, `origin` TEXT, `verification` TEXT,
            `generation` INTEGER NOT NULL, `status` TEXT NOT NULL, `state_version` INTEGER NOT NULL,
            `message` TEXT, `created_at_epoch_millis` INTEGER NOT NULL, `updated_at_epoch_millis` INTEGER NOT NULL,
            PRIMARY KEY(`solution_id`), FOREIGN KEY(`revision_id`) REFERENCES `problem_revision`(`revision_id`)
            ON UPDATE NO ACTION ON DELETE RESTRICT)""")
        connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_detailed_solution_revision_id_question_sha256_protocol_version` ON `detailed_solution` (`revision_id`, `question_sha256`, `protocol_version`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_detailed_solution_status` ON `detailed_solution` (`status`)")
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
