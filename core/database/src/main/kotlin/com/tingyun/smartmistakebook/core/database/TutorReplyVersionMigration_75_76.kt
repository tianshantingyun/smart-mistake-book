package com.tingyun.smartmistakebook.core.database

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

internal val TUTOR_REPLY_VERSION_MIGRATION_75_76 = object : Migration(75, 76) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("""CREATE TABLE IF NOT EXISTS `tutor_reply_group` (
            `group_id` TEXT NOT NULL, `conversation_id` TEXT NOT NULL,
            `original_request_id` TEXT NOT NULL, `selected_version_id` TEXT NOT NULL,
            PRIMARY KEY(`group_id`), FOREIGN KEY(`group_id`) REFERENCES `tutor_message`(`message_id`) ON UPDATE NO ACTION ON DELETE CASCADE)""")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_tutor_reply_group_conversation_id` ON `tutor_reply_group` (`conversation_id`)")
        connection.execSQL("""CREATE TABLE IF NOT EXISTS `tutor_reply_version` (
            `version_id` TEXT NOT NULL, `group_id` TEXT NOT NULL, `request_id` TEXT NOT NULL,
            `ordinal` INTEGER NOT NULL, `body_markdown` TEXT NOT NULL, `thinking_markdown` TEXT,
            `tool_trace_json` TEXT, `source_asset_ids_json` TEXT NOT NULL,
            `request_json` TEXT, `output_json` TEXT, `provider_json` TEXT, `created_at_epoch_millis` INTEGER NOT NULL,
            PRIMARY KEY(`version_id`), FOREIGN KEY(`group_id`) REFERENCES `tutor_reply_group`(`group_id`) ON UPDATE NO ACTION ON DELETE CASCADE)""")
        connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tutor_reply_version_group_id_ordinal` ON `tutor_reply_version` (`group_id`, `ordinal`)")
        connection.execSQL("""CREATE TABLE IF NOT EXISTS `tutor_reply_version_answer` (
            `version_id` TEXT NOT NULL, `response_json` TEXT NOT NULL, `updated_at_epoch_millis` INTEGER NOT NULL,
            PRIMARY KEY(`version_id`), FOREIGN KEY(`version_id`) REFERENCES `tutor_reply_version`(`version_id`) ON UPDATE NO ACTION ON DELETE CASCADE)""")
    }
}
