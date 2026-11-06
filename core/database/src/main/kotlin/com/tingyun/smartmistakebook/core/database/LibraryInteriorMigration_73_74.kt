package com.tingyun.smartmistakebook.core.database

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection

/** Reconciles the two shipped v73 layouts without deleting learner data. */
internal val LIBRARY_INTERIOR_MIGRATION_73_74 = object : Migration(73, 74) {
    override suspend fun migrate(connection: SQLiteConnection) {
        val tables = buildSet {
            connection.prepare("SELECT name FROM sqlite_master WHERE type = 'table'").use { query ->
                while (query.step()) add(query.getText(0))
            }
        }
        val exportColumns = buildSet {
            connection.prepare("PRAGMA table_info(`mistake_export_record`)").use { query ->
                while (query.step()) add(query.getText(1))
            }
        }
        val interiorTables = setOf(
            "mistake_edit_job", "detailed_solution_manifest", "detailed_solution",
            "mistake_export_group", "paper_review", "paper_review_question", "paper_review_photo",
        )
        val pairedExportColumns = setOf("group_id", "artifact_role")
        // Earlier-version migration chains still carry their original identity hash here.
        // A complete layout needs no repair; Room validates its full structure afterwards.
        if (tables.containsAll(interiorTables) && exportColumns.containsAll(pairedExportColumns)) return

        val identity = connection.prepare("SELECT identity_hash FROM room_master_table WHERE id = 42").use {
            if (it.step()) it.getText(0) else null
        }
        // Main's exported v72 snapshot predates library editing. Its 72→73 step
        // adds only paper tables before this migration runs, with the v72 hash intact.
        val paperTables = setOf("paper_review", "paper_review_question", "paper_review_photo")
        if (identity == "aa5e8e8e6536e0c1f3adcda6867814cb" &&
            tables.intersect(interiorTables) == paperTables &&
            exportColumns.intersect(pairedExportColumns).isEmpty()
        ) {
            LIBRARY_INTERIOR_MIGRATION_71_72.migrate(connection)
            return
        }

        check(
            identity == "1d086050204a0da1ed6b6e3299dd9733" &&
                tables.intersect(interiorTables).isEmpty() &&
                exportColumns.intersect(pairedExportColumns).isEmpty(),
        ) { "Unsupported partial v73 library schema; existing data has been preserved" }

        LIBRARY_INTERIOR_MIGRATION_71_72.migrate(connection)
        PAPER_REVIEW_MIGRATION_72_73.migrate(connection)
    }
}

/** Adds main-branch error-cause records to existing UI databases without rewriting user rows. */
internal val UI_INTEGRATION_MIGRATION_74_75 = object : Migration(74, 75) {
    override suspend fun migrate(connection: SQLiteConnection) {
        ERROR_CAUSE_MIGRATION_72_73.migrate(connection)
    }
}
