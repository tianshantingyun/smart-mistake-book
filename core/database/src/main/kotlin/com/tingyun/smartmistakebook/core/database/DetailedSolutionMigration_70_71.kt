package com.tingyun.smartmistakebook.core.database

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

internal val DETAILED_SOLUTION_MIGRATION_70_71 = object : Migration(70, 71) {
    override suspend fun migrate(connection: SQLiteConnection) {
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
    }
}
