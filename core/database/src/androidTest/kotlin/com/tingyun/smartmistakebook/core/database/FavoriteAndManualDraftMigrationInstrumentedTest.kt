package com.tingyun.smartmistakebook.core.database

import android.content.Context
import androidx.paging.PagingSource
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * **C11 收藏 + A8 schema（v66→67）的真库证据**，一个文件两截：
 *
 * 一、**迁移带数据**（`FullMigrationMatrixInstrumentedTest` 用的是空库，看不出"旧行还在不在、
 *    新列对旧行是什么值"）：
 *    1. v66 库里两行条目（一行本来就有备注）升到 v67 后**一行不丢**，`is_favorite` 全是 0
 *       （未收藏——迁移不回填编造的收藏），备注原样；
 *    2. `library_catalog` 视图读得到 `is_favorite`；
 *    3. `problem_draft.source_asset_id` 变成可空，**旧草稿行原样保留**，同一张表里能写进一行
 *       空源草稿（A8 手动建题的形态：NULL = 从创建起就没有照片）；
 *    4. 再开一次（第二次经过迁移出口）形态与行不变——迁移幂等。
 *
 * 二、**收藏筛选的两条查询链**（目录 `instr(lower())` 链 + FTS 链，加上 facet 与行级写入）：
 *    同一个真库上，`favoriteOnly` 在分页/计数/facet 三处同口径；`setErrorBookEntryFavorite`
 *    写进去以后两条链都立刻改结果，写不存在的行如实返回 false。
 */
@RunWith(AndroidJUnit4::class)
class FavoriteAndManualDraftMigrationInstrumentedTest {

    @Test
    fun favoriteColumnAndNullableDraftSourceMigrateWithRowsIntactAndIdempotent() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "favorite-manual-v66-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        try {
            createDatabaseFromExportedSchema(context, databaseName, version = 66)
            seedV66Rows(context, databaseName)

            val migrated = StudyDatabaseFactory.open(context, databaseName)
            assertEquals(STUDY_DATABASE_VERSION, migrated.readDatabaseVersion())
            migrated.close()

            inspectV67Shape(context, databaseName)
            // A8 的形态证明：重建后的表**真的**能装下一行没有照片的草稿（NULL = 从创建起就没有源图）。
            insertManualDraftRow(context, databaseName)

            // 幂等：同一库再开一次（迁移已是 no-op）不得改变形态或行（含上面这行空源草稿）。
            val reopened = StudyDatabaseFactory.open(context, databaseName)
            assertEquals(STUDY_DATABASE_VERSION, reopened.readDatabaseVersion())
            reopened.close()
            inspectV67Shape(context, databaseName)
        } finally {
            context.deleteDatabase(databaseName)
        }
    }

    /** 在 v67 库里写一行空源草稿并读回：这是 A8 schema 半边的直接证据。 */
    private fun insertManualDraftRow(context: Context, databaseName: String) {
        val connection = AndroidSQLiteDriver().open(context.getDatabasePath(databaseName).absolutePath)
        try {
            connection.execSQL(
                """
                INSERT INTO `problem_draft` (
                    `draft_id`, `source_asset_id`, `origin`, `status`, `current_revision_number`,
                    `created_at_epoch_millis`, `updated_at_epoch_millis`, `request_fingerprint`
                ) VALUES (
                    '$MANUAL_DRAFT_ID', NULL, 'LIBRARY', 'EDITING', 1,
                    $NOW, $NOW, 'fp-manual-draft'
                )
                """.trimIndent(),
            )
            val statement = connection.prepare(
                "SELECT `source_asset_id` FROM `problem_draft` WHERE `draft_id` = '$MANUAL_DRAFT_ID'",
            )
            try {
                assertTrue("空源草稿必须写得进读得出", statement.step())
                assertTrue("NULL 就是 NULL（不是空串、不是占位资产 id）", statement.isNull(0))
            } finally {
                statement.close()
            }
        } finally {
            connection.close()
        }
    }

    @Test
    fun favoriteFilterAndRowWriteAgreeOnBothQueryChains() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "favorite-filter-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        try {
            createDatabaseFromExportedSchema(context, databaseName, version = 66)
            seedV66Rows(context, databaseName)
            val store = StudyDatabaseFactory.open(context, databaseName)
            store.refreshLibrarySearchProjection()
            // v66 里没有收藏列：收藏是升级后由**行级写入**建立的（不是迁移编造的值）。
            assertTrue(store.setErrorBookEntryFavorite(FAVORITE_ENTRY_ID, true, NOW))

            // 目录链（空搜索词：instr(lower()) 回退路径）
            assertEquals(2, store.libraryCatalogCount("", null, null, null, null, null))
            assertEquals(
                "只看收藏：两行里只有一行被收藏",
                1,
                store.libraryCatalogCount("", null, null, null, null, null, favoriteOnly = true),
            )
            assertEquals(
                listOf(FAVORITE_ENTRY_ID),
                store.libraryCatalogPage(
                    searchText = "",
                    subjectId = null,
                    sectionId = null,
                    masteryId = null,
                    createdFromEpochMillis = null,
                    createdToEpochMillis = null,
                    sort = "RECENTLY_UPDATED",
                    offset = 0,
                    limit = 10,
                    favoriteOnly = true,
                ).map { row -> row.entryId },
            )
            assertTrue(
                "行级读值必须来自同一个视图列（收藏行 true、未收藏行 false）",
                store.libraryCatalogPage(
                    searchText = "",
                    subjectId = null,
                    sectionId = null,
                    masteryId = null,
                    createdFromEpochMillis = null,
                    createdToEpochMillis = null,
                    sort = "RECENTLY_UPDATED",
                    offset = 0,
                    limit = 10,
                ).associate { row -> row.entryId to row.isFavorite } ==
                    mapOf(FAVORITE_ENTRY_ID to true, PLAIN_ENTRY_ID to false),
            )

            // FTS 链（非空搜索词：MATCH + 加权排序 + 同一套过滤）
            val favoriteMatch = CjkTextTokenizer.matchExpression(FAVORITE_TITLE)
            val favoriteTokens = CjkTextTokenizer.tokens(FAVORITE_TITLE)
            assertEquals(
                1,
                store.librarySearchCount(
                    matchQuery = favoriteMatch,
                    subjectId = null,
                    sectionId = null,
                    masteryId = null,
                    createdFromEpochMillis = null,
                    createdToEpochMillis = null,
                    favoriteOnly = true,
                ),
            )
            assertEquals(
                "筛选必须真的咬住：另一道题在收藏筛选下搜不到",
                0,
                store.librarySearchCount(
                    matchQuery = CjkTextTokenizer.matchExpression(PLAIN_TITLE),
                    subjectId = null,
                    sectionId = null,
                    masteryId = null,
                    createdFromEpochMillis = null,
                    createdToEpochMillis = null,
                    favoriteOnly = true,
                ),
            )
            assertEquals(
                "同一搜索词在不过滤时照常命中（收藏筛选不是全局开关）",
                1,
                store.librarySearchCount(
                    matchQuery = CjkTextTokenizer.matchExpression(PLAIN_TITLE),
                    subjectId = null,
                    sectionId = null,
                    masteryId = null,
                    createdFromEpochMillis = null,
                    createdToEpochMillis = null,
                ),
            )
            val page = store.librarySearchPagingSource(
                matchQuery = favoriteMatch,
                subjectId = null,
                sectionId = null,
                masteryId = null,
                createdFromEpochMillis = null,
                createdToEpochMillis = null,
                sort = "RECENTLY_UPDATED",
                tokens = favoriteTokens,
                favoriteOnly = true,
            ).load(PagingSource.LoadParams.Refresh(null, 10, false))
            val loaded = page as PagingSource.LoadResult.Page
            assertEquals(listOf(FAVORITE_ENTRY_ID), loaded.data.map { row -> row.entryId })
            assertTrue("FTS 行同样必须带收藏读值", loaded.data.single().isFavorite)

            // facet：收藏这一维的计数不看自己的过滤（其余筛选照常生效），两条链各一条。
            fun facetCount(rows: List<LibraryFacetCountRecord>): Int =
                rows.single { it.id == FAVORITE_FACET_ID }.count
            assertEquals(
                1,
                facetCount(store.libraryCatalogFacets("", null, null, null, null, null, "FAVORITE")),
            )
            assertEquals(
                1,
                facetCount(
                    store.librarySearchFacets(
                        matchQuery = CjkTextTokenizer.matchExpression(FAVORITE_TITLE),
                        subjectId = null,
                        sectionId = null,
                        masteryId = null,
                        createdFromEpochMillis = null,
                        createdToEpochMillis = null,
                        facet = "FAVORITE",
                    ),
                ),
            )

            // 行级写入：改收藏后两条链立刻改结果；写不存在的行如实 false。
            assertTrue(
                store.setErrorBookEntryFavorite(PLAIN_ENTRY_ID, true, NOW),
            )
            assertEquals(
                2,
                store.libraryCatalogCount("", null, null, null, null, null, favoriteOnly = true),
            )
            assertTrue(store.setErrorBookEntryFavorite(FAVORITE_ENTRY_ID, false, NOW))
            assertEquals(
                1,
                store.libraryCatalogCount("", null, null, null, null, null, favoriteOnly = true),
            )
            assertFalse(
                "写没写进去必须如实报，不能假成功",
                store.setErrorBookEntryFavorite("entry-missing", true, NOW),
            )
            store.close()
        } finally {
            context.deleteDatabase(databaseName)
        }
    }

    /** 往 66.json 建出来的库里写两行真实条目（一行已收藏）与一行照片草稿。 */
    private fun seedV66Rows(context: Context, databaseName: String) {
        val connection = AndroidSQLiteDriver().open(context.getDatabasePath(databaseName).absolutePath)
        try {
            connection.execSQL("PRAGMA foreign_keys = OFF")
            seedEntry(
                connection = connection,
                entryId = FAVORITE_ENTRY_ID,
                problemId = "problem-favorite",
                revisionId = "revision-favorite",
                unitId = "unit-favorite",
                title = FAVORITE_TITLE,
                markdown = "收藏这一行也要能被全文搜到。",
                userNote = "本来就有备注",
            )
            seedEntry(
                connection = connection,
                entryId = PLAIN_ENTRY_ID,
                problemId = "problem-plain",
                revisionId = "revision-plain",
                unitId = "unit-plain",
                title = PLAIN_TITLE,
                markdown = "这一行没被收藏。",
                userNote = null,
            )
            connection.execSQL(
                """
                INSERT INTO `canonical_source_asset` (
                    `source_asset_id`, `content_sha256`, `relative_path`, `mime_type`,
                    `byte_size`, `width`, `height`, `source_type`, `created_at_epoch_millis`
                ) VALUES (
                    '$DRAFT_ASSET_ID', '$DRAFT_SHA', 'assets/$DRAFT_ASSET_ID.jpg', 'image/jpeg',
                    1024, 10, 10, 'CAMERA', $NOW
                )
                """.trimIndent(),
            )
            connection.execSQL(
                """
                INSERT INTO `problem_draft` (
                    `draft_id`, `source_asset_id`, `origin`, `status`, `current_revision_number`,
                    `created_at_epoch_millis`, `updated_at_epoch_millis`, `request_fingerprint`
                ) VALUES (
                    '$DRAFT_ID', '$DRAFT_ASSET_ID', 'LIBRARY', 'EDITING', 1,
                    $NOW, $NOW, 'fp-favorite-draft'
                )
                """.trimIndent(),
            )
            connection.execSQL(
                """
                INSERT INTO `problem_draft_revision` (
                    `draft_id`, `revision_number`, `basis_revision_number`, `subject`, `title`,
                    `question_document_snapshot`, `document_fingerprint`, `author`,
                    `created_at_epoch_millis`
                ) VALUES (
                    '$DRAFT_ID', 1, NULL, NULL, '新拍题目',
                    '$DRAFT_DOCUMENT_JSON', '$DRAFT_DOCUMENT_FINGERPRINT', 'CAPTURE_IMPORT',
                    $NOW
                )
                """.trimIndent(),
            )
            connection.execSQL("PRAGMA foreign_keys = ON")
        } finally {
            connection.close()
        }
    }

    private fun seedEntry(
        connection: SQLiteConnection,
        entryId: String,
        problemId: String,
        revisionId: String,
        unitId: String,
        title: String,
        markdown: String,
        userNote: String?,
    ) {
        connection.execSQL(
            """
            INSERT INTO `problem` (
                `problem_id`, `canonical_fingerprint`, `subject`, `created_at_epoch_millis`
            ) VALUES ('$problemId', 'fp-$problemId', 'MATH', $NOW)
            """.trimIndent(),
        )
        connection.execSQL(
            """
            INSERT INTO `problem_revision` (
                `revision_id`, `problem_id`, `revision_number`, `title`, `problem_markdown`,
                `answer_spec_id`, `answer_spec_snapshot`, `answer_verification_status`,
                `source_type`, `source_reference`, `content_fingerprint`,
                `created_at_epoch_millis`
            ) VALUES (
                '$revisionId', '$problemId', 1, '$title', '$markdown',
                NULL, NULL, 'UNKNOWN', 'CAPTURE_CONFIRMED', NULL, 'fp-$revisionId',
                $NOW
            )
            """.trimIndent(),
        )
        connection.execSQL(
            """
            INSERT INTO `practice_unit` (
                `practice_unit_id`, `problem_id`, `problem_revision_id`, `unit_key`,
                `unit_kind`, `title`, `prompt_markdown`, `estimated_seconds`,
                `created_at_epoch_millis`
            ) VALUES (
                '$unitId', '$problemId', '$revisionId', 'whole-problem',
                'WHOLE_PROBLEM', '$title', '$markdown', 120, $NOW
            )
            """.trimIndent(),
        )
        connection.execSQL(
            """
            INSERT INTO `error_book_entry` (
                `entry_id`, `practice_unit_id`, `problem_id`, `current_revision_id`,
                `source_key`, `status`, `accepted_at_epoch_millis`, `updated_at_epoch_millis`,
                `user_note`
            ) VALUES (
                '$entryId', '$unitId', '$problemId', '$revisionId',
                NULL, 'ACTIVE', $NOW, $NOW, ${userNote?.let { "'$it'" } ?: "NULL"}
            )
            """.trimIndent(),
        )
    }

    /** 迁移后的真库形状：收藏列在（旧行 0）、视图读得到、草稿表可空且旧行原样。 */
    private fun inspectV67Shape(context: Context, databaseName: String) {
        val connection = AndroidSQLiteDriver().open(context.getDatabasePath(databaseName).absolutePath)
        try {
            val columns = columnNames(connection, "error_book_entry")
            assertEquals("收藏列追加在行尾", "is_favorite", columns.last())

            val rows = connection.prepare(
                "SELECT `entry_id`, `is_favorite`, `user_note` FROM `error_book_entry` " +
                    "ORDER BY `entry_id` ASC",
            )
            try {
                val seen = mutableListOf<Triple<String, Long, String?>>()
                while (rows.step()) {
                    // 驱动对 NULL 调 getText 会抛 NPE：先问 isNull 再取值。
                    val note = if (rows.isNull(2)) null else rows.getText(2)
                    seen += Triple(rows.getText(0)!!, rows.getLong(1), note)
                }
                assertEquals(
                    "旧行一行不丢；收藏一律 0（未收藏，不回填编造值）；备注原样",
                    listOf(
                        Triple(FAVORITE_ENTRY_ID, 0L, "本来就有备注"),
                        Triple(PLAIN_ENTRY_ID, 0L, null),
                    ),
                    seen,
                )
            } finally {
                rows.close()
            }

            val view = connection.prepare(
                "SELECT `is_favorite` FROM `library_catalog` WHERE `entry_id` = '$FAVORITE_ENTRY_ID'",
            )
            try {
                assertTrue("视图必须读得到收藏列", view.step())
                assertEquals(0L, view.getLong(0))
            } finally {
                view.close()
            }

            val info = connection.prepare("PRAGMA table_info(`problem_draft`)")
            try {
                var sourceAssetNullable = false
                while (info.step()) {
                    if (info.getText(1) == "source_asset_id") {
                        assertEquals("A8 手动草稿没有照片：这一列必须可空", 0L, info.getLong(3))
                        sourceAssetNullable = true
                    }
                }
                assertTrue("problem_draft.source_asset_id 必须还在", sourceAssetNullable)
            } finally {
                info.close()
            }
            val draft = connection.prepare(
                "SELECT `source_asset_id` FROM `problem_draft` WHERE `draft_id` = '$DRAFT_ID'",
            )
            try {
                assertTrue("照片草稿必须一行不丢", draft.step())
                assertEquals(DRAFT_ASSET_ID, draft.getText(0))
            } finally {
                draft.close()
            }
        } finally {
            connection.close()
        }
    }

    private fun columnNames(connection: SQLiteConnection, table: String): List<String> {
        val statement = connection.prepare("PRAGMA table_info(`$table`)")
        try {
            val names = mutableListOf<String>()
            while (statement.step()) names += statement.getText(1)!!
            return names
        } finally {
            statement.close()
        }
    }

    private companion object {
        const val NOW = 1_700_000_000_000L
        const val FAVORITE_ENTRY_ID = "entry-favorite"
        const val PLAIN_ENTRY_ID = "entry-plain"
        const val FAVORITE_TITLE = "收藏直线的垂直平分线"
        const val PLAIN_TITLE = "普通三角形面积"
        const val DRAFT_ID = "draft-photo"
        const val MANUAL_DRAFT_ID = "draft-manual"
        const val DRAFT_ASSET_ID = "asset-photo"
        const val DRAFT_SHA =
            "1111111111111111111111111111111111111111111111111111111111111111"
        const val FAVORITE_FACET_ID = "favorite"

        /** v66 形态的草稿修订快照（这一列的内容不属于本用例的断言面）。 */
        const val DRAFT_DOCUMENT_JSON =
            "{\"schemaVersion\":1,\"document\":{\"id\":\"document-draft-photo\",\"blocks\":[" +
                "{\"type\":\"paragraph\",\"id\":\"stem\",\"markdown\":\"题面\"}]}," +
                "\"blockEvidence\":[{\"blockId\":\"stem\",\"sourceAssetId\":\"asset-photo\"," +
                "\"sourceRegion\":{\"left\":0.0,\"top\":0.0,\"right\":1.0,\"bottom\":1.0}," +
                "\"writingLayer\":\"UNKNOWN\",\"provenance\":\"IMPORTED_STRUCTURE\"," +
                "\"confidence\":null,\"reviewStatus\":\"NEEDS_REVIEW\"," +
                "\"producerVersion\":\"capture-import-v1\"}]}"
        const val DRAFT_DOCUMENT_FINGERPRINT =
            "2222222222222222222222222222222222222222222222222222222222222222"
    }
}
