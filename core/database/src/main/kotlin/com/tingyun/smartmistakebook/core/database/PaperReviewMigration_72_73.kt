package com.tingyun.smartmistakebook.core.database

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/** Additive paper-review history, frozen questions and unmodified answer photographs. */
internal val PAPER_REVIEW_MIGRATION_72_73 = object : Migration(72, 73) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("""CREATE TABLE IF NOT EXISTS `paper_review` (`paper_id` TEXT NOT NULL, `session_id` TEXT NOT NULL, `plan_id` TEXT NOT NULL, `fingerprint` TEXT NOT NULL, `draft_snapshot` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `export_id` TEXT, PRIMARY KEY(`paper_id`), FOREIGN KEY(`session_id`) REFERENCES `review_session`(`review_session_id`) ON UPDATE NO ACTION ON DELETE RESTRICT)""")
        connection.execSQL("""CREATE TABLE IF NOT EXISTS `paper_review_question` (`paper_id` TEXT NOT NULL, `ordinal` INTEGER NOT NULL, `entry_id` TEXT NOT NULL, `practice_unit_id` TEXT NOT NULL, `problem_id` TEXT NOT NULL, `revision_id` TEXT NOT NULL, `title` TEXT NOT NULL, `markdown` TEXT NOT NULL, `result` TEXT, `corrected` INTEGER NOT NULL, `original_visible` INTEGER NOT NULL, `answered_at` INTEGER, `note` TEXT NOT NULL, `applied` INTEGER NOT NULL, PRIMARY KEY(`paper_id`, `ordinal`), FOREIGN KEY(`paper_id`) REFERENCES `paper_review`(`paper_id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`entry_id`) REFERENCES `error_book_entry`(`entry_id`) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(`revision_id`) REFERENCES `problem_revision`(`revision_id`) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(`practice_unit_id`) REFERENCES `practice_unit`(`practice_unit_id`) ON UPDATE NO ACTION ON DELETE RESTRICT)""")
        connection.execSQL("""CREATE TABLE IF NOT EXISTS `paper_review_photo` (`photo_id` TEXT NOT NULL, `paper_id` TEXT NOT NULL, `source_asset_id` TEXT NOT NULL, `corrected` INTEGER NOT NULL, `question_ordinal` INTEGER, `created_at` INTEGER NOT NULL, PRIMARY KEY(`photo_id`), FOREIGN KEY(`paper_id`) REFERENCES `paper_review`(`paper_id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`source_asset_id`) REFERENCES `canonical_source_asset`(`source_asset_id`) ON UPDATE NO ACTION ON DELETE RESTRICT)""")
        connection.execSQL("""CREATE INDEX IF NOT EXISTS `index_paper_review_session_id` ON `paper_review` (`session_id`)""")
        connection.execSQL("""CREATE INDEX IF NOT EXISTS `index_paper_review_question_entry_id` ON `paper_review_question` (`entry_id`)""")
        connection.execSQL("""CREATE INDEX IF NOT EXISTS `index_paper_review_question_revision_id` ON `paper_review_question` (`revision_id`)""")
        connection.execSQL("""CREATE INDEX IF NOT EXISTS `index_paper_review_question_practice_unit_id` ON `paper_review_question` (`practice_unit_id`)""")
        connection.execSQL("""CREATE INDEX IF NOT EXISTS `index_paper_review_photo_paper_id` ON `paper_review_photo` (`paper_id`)""")
        connection.execSQL("""CREATE INDEX IF NOT EXISTS `index_paper_review_photo_source_asset_id` ON `paper_review_photo` (`source_asset_id`)""")
        connection.execSQL("""CREATE UNIQUE INDEX IF NOT EXISTS `index_paper_review_photo_paper_id_source_asset_id` ON `paper_review_photo` (`paper_id`, `source_asset_id`)""")
    }
}
