package com.tingyun.smartmistakebook.core.data.backup

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/**
 * T3 快照裁剪器的设备侧验收（schema 69）。
 *
 * 夹具直接按导出的 `69.json` 建库（含 room_master_table、视图与 FTS4 外部内容表），
 * 不经过 Room，也不碰生产库。断言覆盖：
 * - 9/13/15 三个合法组合；
 * - 0/8 在**打开文件之前**被拒绝；
 * - 被删类别的哨兵文本在 VACUUM 后不再出现在文件字节里；
 * - FTS 与 `library_search_content` 对齐；
 * - schema / room_master_table / sqlite_sequence 不变；
 * - 同一 mask 第二次裁剪幂等（无删除且无空闲页 → 不 VACUUM）；
 * - 真子集即使本次无删除，只要仍有空闲页也 VACUUM；
 * - 全选允许未登记表原样随包（无"未包含类别"），真子集仍 fail-closed；
 * - 空间预检失败时不改快照。
 */
@RunWith(AndroidJUnit4::class)
class BackupSnapshotTrimmerInstrumentedTest {

    private val sessionSentinel = "SENTINEL-SESSION-DO-NOT-LEAK"
    private val masterySentinel = "SENTINEL-MASTERY-DO-NOT-LEAK"
    private val issueSentinel = "SENTINEL-ISSUE-KEEP"

    @Test
    fun mask9TrimsSessionsAndMasteryButKeepsSharedFigureOperation() {
        withSeededSnapshot { context, name, path, ids ->
            val report = BackupSnapshotTrimmer().trim(File(path), 9)

            assertEquals(9, report.selectionMask)
            assertEquals(SmbkSelectionPlan.rowCountKeys, report.postRowCounts.keys)
            assertEquals(SmbkSelectionPlan.SCHEMA_VERSION, report.schemaVersion)
            assertTrue("mask=9 删除了行，必须 VACUUM", report.vacuumed)
            assertTrue(report.ftsRebuilt)
            assertEquals(0L, report.ftsOrphanDocIds)

            SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                assertCount(db, "problem", 1)
                assertCount(db, "problem_revision", 1)
                assertCount(db, "problem_revision_source_asset", 1)
                assertCount(db, "canonical_source_asset", 4)
                assertCount(db, "tutor_conversation", 0)
                assertCount(db, "tutor_message", 0)
                assertCount(db, "tutor_message_source_asset", 0)
                assertCount(db, "review_log", 0)
                assertCount(db, "learner_chat_evidence", 0)
                assertCount(db, "knowledge_search_feature", 0)
                assertCount(db, "knowledge_search_index_state", 0)
                assertCount(db, "model_task", 0)
                assertCount(db, "model_task_event", 0)
                assertCount(db, "model_task_operation", 0)
                // O_SESSION_ONLY 没有存活图引用 → 删；O_TUTOR 被题面 revision 的共享图拉回 → 留；
                // O_ISSUE 是 ISSUE_BOOK 种子；O_CLEAN 被存活 clean_redraw_task.operation_id 保住。
                assertCount(db, "figure_generation_operation", 3)
                assertCount(db, "figure_generation_attempt", 3)
                assertCount(db, "library_search_content", 1)
                assertCount(db, "library_search_fts", 1)
                assertEquals(0L, ftsOrphanCount(db))
            }

            assertTrue(ids.sharedAssetId in report.referencedAssetIds)
            assertTrue(ids.issueAssetId in report.referencedAssetIds)
            assertTrue(ids.cleanAssetId in report.referencedAssetIds)
            assertFalse(
                "只被已删会话引用的图不能进打包资产集合",
                ids.sessionOnlyAssetId in report.referencedAssetIds,
            )
            assertEquals(1L, report.deletedRowCounts["tutor_message"])
            assertEquals(1L, report.deletedRowCounts["review_log"])
            assertEquals(1L, report.deletedRowCounts["figure_generation_attempt"])
            assertEquals(1L, report.deletedRowCounts["figure_generation_operation"])

            val bytes = File(path).readBytes().toString(Charsets.ISO_8859_1)
            assertFalse("会话哨兵在 VACUUM 后不得留在快照字节里", bytes.contains(sessionSentinel))
            assertFalse("掌握库哨兵在 VACUUM 后不得留在快照字节里", bytes.contains(masterySentinel))
            assertTrue("选中类别的哨兵必须保留", bytes.contains(issueSentinel))
        }
    }

    @Test
    fun mask13KeepsSessionsAndDeletesOnlyMasteryAndDerivedTables() {
        withSeededSnapshot { _, _, path, _ ->
            val report = BackupSnapshotTrimmer().trim(File(path), 13)

            assertEquals(13, report.selectionMask)
            assertTrue(report.vacuumed)
            SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                assertCount(db, "tutor_conversation", 1)
                assertCount(db, "tutor_message", 1)
                assertCount(db, "tutor_message_source_asset", 1)
                assertCount(db, "review_log", 0)
                assertCount(db, "learner_chat_evidence", 0)
                assertCount(db, "model_task", 0)
                assertCount(db, "knowledge_search_feature", 0)
                assertCount(db, "knowledge_search_index_state", 0)
                // SESSIONS 被选中：TUTOR_MESSAGE 源的 operation 全部是 O_seed。
                assertCount(db, "figure_generation_operation", 4)
                assertCount(db, "figure_generation_attempt", 4)
                assertCount(db, "library_search_content", 1)
                assertCount(db, "library_search_fts", 1)
            }
            assertEquals(0L, report.deletedRowCounts["tutor_message"])
            assertEquals(1L, report.deletedRowCounts["review_log"])
        }
    }

    @Test
    fun mask15KeepsAllUserCategoriesButAlwaysTrimsDerivedIndexes() {
        withSeededSnapshot { _, _, path, ids ->
            val report = BackupSnapshotTrimmer().trim(File(path), 15)

            assertEquals(15, report.selectionMask)
            assertTrue("派生索引有行，必须 VACUUM", report.vacuumed)
            assertEquals(2, report.trimmedTableCount)
            assertEquals(2L, report.trimmedRowCount)
            SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                assertCount(db, "tutor_conversation", 1)
                assertCount(db, "tutor_message", 1)
                assertCount(db, "review_log", 1)
                assertCount(db, "learner_chat_evidence", 1)
                assertCount(db, "model_task", 1)
                assertCount(db, "model_task_event", 1)
                assertCount(db, "model_task_operation", 1)
                assertCount(db, "figure_generation_operation", 4)
                assertCount(db, "figure_generation_attempt", 4)
                assertCount(db, "canonical_source_asset", 4)
                assertCount(db, "knowledge_search_feature", 0)
                assertCount(db, "knowledge_search_index_state", 0)
                assertCount(db, "library_search_content", 1)
                assertCount(db, "library_search_fts", 1)
            }
            assertEquals(ids.allAssetIds, report.referencedAssetIds)
        }
    }

    @Test
    fun schemaRoomMasterTableAndSqliteSequenceAreUntouched() {
        withSeededSnapshot { _, _, path, _ ->
            val file = File(path)
            val before = captureCatalog(file.absolutePath)
            assertEquals(SmbkSelectionPlan.SCHEMA_VERSION, before.userVersion)
            assertEquals(SmbkSelectionPlan.IDENTITY_HASH, before.roomMasterIdentityHash)

            BackupSnapshotTrimmer().trim(file, 9)

            val after = captureCatalog(file.absolutePath)
            assertEquals("裁剪不得改 schema/索引/视图定义", before.schemaSql, after.schemaSql)
            assertEquals(
                "room_master_table 的 Room 身份哈希必须原样保留",
                before.roomMasterIdentityHash,
                after.roomMasterIdentityHash,
            )
            assertEquals(
                "sqlite_sequence 的高水位不得被裁剪清零",
                before.sqliteSequence,
                after.sqliteSequence,
            )
            assertEquals(SmbkSelectionPlan.SCHEMA_VERSION, after.userVersion)
        }
    }

    @Test
    fun invalidMasksAreRejectedBeforeTheSnapshotIsTouched() {
        withSeededSnapshot { _, _, path, _ ->
            val file = File(path)
            val beforeBytes = file.readBytes()

            listOf(0, 1, 7, 8, 10, 11, 12, 14, 16, -1).forEach { mask ->
                val failure = assertThrows(IllegalArgumentException::class.java) {
                    BackupSnapshotTrimmer().trim(file, mask)
                }
                assertTrue(failure.message.orEmpty().contains("mask=$mask"))
            }

            assertTrue("非法 mask 不得改写快照", beforeBytes.contentEquals(file.readBytes()))
        }
    }

    @Test
    fun trimmingWithTheSameMaskTwiceIsIdempotentAndDoesNotVacuumAgain() {
        withSeededSnapshot { _, _, path, _ ->
            val file = File(path)
            val first = BackupSnapshotTrimmer().trim(file, 9)
            val second = BackupSnapshotTrimmer().trim(file, 9)

            assertTrue(first.vacuumed)
            assertFalse("第二次没有删除行，不得再 VACUUM", second.vacuumed)
            assertEquals(0L, second.trimmedRowCount)
            assertEquals(0, second.trimmedTableCount)
            assertEquals(first.postRowCounts, second.preRowCounts)
            assertEquals(first.postRowCounts, second.postRowCounts)
            assertTrue(second.deletedRowCounts.values.all { it == 0L })
        }
    }

    /**
     * 真子集即使本次一行没删，只要快照里还有空闲页（旧回退路径的逐字节拷贝会保留历史
     * 会话/掌握正文），也必须 VACUUM；否则清单声明"不含这些类别"，字节里却留着。
     *
     * 用 mask=13（题+会话+图，未选掌握库）：掌握库表先手工删空（模拟用户早先删除、库还
     * 没 VACUUM），本次裁剪不再删任何行，但空闲页仍在。
     */
    @Test
    fun subsetTrimVacuumsWhenFreePagesRemainEvenWithNoRowsDeleted() {
        withSeededSnapshot { _, _, path, _ ->
            val file = File(path)
            SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
                // Android SQLite 的 auto_vacuum 可能是 FULL：删行会就地截断，freelist 恒 0。
                // 本用例要模拟"旧回退快照里仍留着历史空闲页"，先显式改成手动 vacuum 并重建。
                db.execSQL("PRAGMA auto_vacuum=0")
                db.execSQL("VACUUM")
                // 让 review_log 长出多张叶子页，正文里带掌握库哨兵。
                repeat(300) { index ->
                    db.insertOrThrow("review_log", null, ContentValues().apply {
                        put("learner_id", "learner-1")
                        put("card_id", "pseudo:MATH")
                        put("rating", 3)
                        put("delta_t_days", 1.0)
                        put("duration_ms", 1_000)
                        put("reviewed_at_utc", 2_000L + index)
                        put("source_kind", "MANUAL")
                        put("source_id", "free-page-source-$index")
                        put("evidence_weight", 1.0)
                        put("time_bucket", "MORNING")
                        put("planned_reason", masterySentinel.repeat(20))
                        put("recorded_at", 2_000L + index)
                    })
                }
                // 设备 SQLite 在只删小行时可能复用页而不留 freelist（本用例曾在真机红）：
                // 再在 ALWAYS 表 knowledge_node 里写一批大正文再删掉，稳定制造空闲页；
                // 这些行不属于任何未选类别，trim 本身仍然一行不删。
                repeat(120) { index ->
                    db.insertOrThrow("knowledge_node", null, ContentValues().apply {
                        put("knowledge_node_id", "free-page-node-$index")
                        put("stable_code", "FP-$index")
                        put("subject", "MATH")
                        put("display_name", "free-page-$index")
                        put("taxonomy_version", "v1")
                        put("created_at_epoch_millis", 2_000L + index)
                        put("boundary_markdown", masterySentinel.repeat(4_000))
                    })
                }
                db.execSQL(
                    "DELETE FROM knowledge_node WHERE knowledge_node_id LIKE 'free-page-node-%'",
                )
                // 模拟"用户早就删过掌握记录、库还没 VACUUM"：未选类别与派生表/按行筛的
                // 台账先清空，让本次裁剪没有任何行可删。选中类别（题/会话/图）一行不动。
                SmbkSelectionPlan.categoryTables.forEach { (category, tables) ->
                    if (category != SmbkSelectionCategory.MASTERY) return@forEach
                    tables.forEach { table -> db.execSQL("DELETE FROM \"$table\"") }
                }
                SmbkSelectionPlan.splitTables.forEach { table ->
                    db.execSQL("DELETE FROM \"$table\"")
                }
                SmbkSelectionPlan.derivedTables.forEach { table ->
                    db.execSQL("DELETE FROM \"$table\"")
                }
                db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }
            }
            val freePagesBefore = freePageCount(path)
            assertTrue("夹具必须留下空闲页，实际 freelist=$freePagesBefore", freePagesBefore > 0L)

            val report = BackupSnapshotTrimmer().trim(file, 13)

            assertEquals("本次没有任何行可删", 0L, report.trimmedRowCount)
            assertEquals(0, report.trimmedTableCount)
            assertTrue("仍有空闲页时真子集必须 VACUUM", report.vacuumed)
            assertEquals("VACUUM 后不得再有空闲页", 0L, freePageCount(path))
            val after = file.readBytes().toString(Charsets.ISO_8859_1)
            assertFalse("VACUUM 后掌握库哨兵不得留在快照字节里", after.contains(masterySentinel))
        }
    }

    private fun freePageCount(path: String): Long =
        SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("PRAGMA freelist_count", null).use { cursor ->
                if (!cursor.moveToFirst()) error("无法读取 freelist_count")
                cursor.getLong(0)
            }
        }

    /**
     * 全选没有"未包含的类别"，未登记表属于"整库随包"的语义（设备大库回归夹具用额外的
     * bloat 表撑库）；真子集仍必须对未知表 fail-closed，防止未登记数据随包泄露。
     */
    @Test
    fun fullSelectionAllowsUnknownTablesWhileSubsetStillRejectsThem() {
        withSeededSnapshot { _, _, path, _ ->
            SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
                db.execSQL("CREATE TABLE unregistered_extra(payload TEXT)")
                db.execSQL("INSERT INTO unregistered_extra(payload) VALUES('kept-in-full')")
            }
            val report = BackupSnapshotTrimmer().trim(File(path), 15)
            assertEquals(15, report.selectionMask)
            SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                assertCount(db, "unregistered_extra", 1)
            }
        }
        withSeededSnapshot { _, _, path, _ ->
            SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
                db.execSQL("CREATE TABLE unregistered_extra(payload TEXT)")
            }
            val failure = assertThrows(BackupSnapshotTrimException::class.java) {
                BackupSnapshotTrimmer().trim(File(path), 9)
            }
            assertTrue(
                "真子集必须拒绝未登记表：${failure.message}",
                failure.message.orEmpty().contains("未登记"),
            )
        }
    }

    @Test
    fun spacePreflightFailureLeavesTheSnapshotUntouched() {
        withSeededSnapshot { _, _, path, _ ->
            val file = File(path)
            val before = file.readBytes()
            val trimmer = BackupSnapshotTrimmer(freeSpaceProvider = { 0L })

            val failure = assertThrows(BackupSnapshotTrimException::class.java) {
                trimmer.trim(file, 9)
            }
            assertTrue(failure.message.orEmpty().contains("磁盘空间不足"))
            assertTrue(before.contentEquals(file.readBytes()))
        }
    }

    private fun withSeededSnapshot(
        block: (Context, String, String, SeedIds) -> Unit,
    ) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "trimmer-${UUID.randomUUID()}.db"
        context.deleteDatabase(databaseName)
        try {
            createDatabaseFromExportedSchema(
                context = context,
                databaseName = databaseName,
                version = SmbkSelectionPlan.SCHEMA_VERSION,
            )
            val path = context.getDatabasePath(databaseName).absolutePath
            val ids = seed(context, databaseName)
            block(context, databaseName, path, ids)
        } finally {
            context.deleteDatabase(databaseName)
            File(context.getDatabasePath(databaseName).absolutePath + "-wal").delete()
            File(context.getDatabasePath(databaseName).absolutePath + "-shm").delete()
        }
    }

    private fun seed(context: Context, databaseName: String): SeedIds {
        val db = SQLiteDatabase.openDatabase(
            context.getDatabasePath(databaseName).absolutePath,
            null,
            SQLiteDatabase.OPEN_READWRITE,
        )
        try {
            val now = 1_000L
            val knowledgeNodeId = "node-1"
            db.insertOrThrow(
                "knowledge_node",
                null,
                ContentValues().apply {
                    put("knowledge_node_id", knowledgeNodeId)
                    put("stable_code", "KN-1")
                    put("subject", "MATH")
                    put("display_name", "一次函数")
                    put("taxonomy_version", "v1")
                    put("created_at_epoch_millis", now)
                },
            )

            val sharedAsset = canonicalAsset(db, "shared", now)
            val issueAsset = canonicalAsset(db, "issue", now)
            val sessionOnlyAsset = canonicalAsset(db, "session-only", now)
            val cleanAsset = canonicalAsset(db, "clean", now)

            db.insertOrThrow("problem", null, ContentValues().apply {
                put("problem_id", "problem-1")
                put("canonical_fingerprint", "fingerprint-1")
                put("subject", "MATH")
                put("created_at_epoch_millis", now)
            })
            db.insertOrThrow("problem_revision", null, ContentValues().apply {
                put("revision_id", "revision-1")
                put("problem_id", "problem-1")
                put("revision_number", 1)
                put("title", issueSentinel)
                put("problem_markdown", "题干")
                put("answer_verification_status", "VERIFIED")
                put("source_type", "CAPTURE")
                put("content_fingerprint", "revision-fingerprint-1")
                put("created_at_epoch_millis", now)
            })
            db.insertOrThrow("problem_revision_source_asset", null, ContentValues().apply {
                put("problem_revision_id", "revision-1")
                put("source_asset_id", sharedAsset)
                put("role", "STEM")
                put("ordinal", 0)
            })

            db.insertOrThrow("tutor_conversation", null, ContentValues().apply {
                put("conversation_id", "conversation-1")
                put("anchor_kind", "TEXT_ONLY")
                putNull("anchor_id")
                putNull("anchor_revision_id")
                put("status", "ACTIVE")
                put("title", sessionSentinel)
                put("created_at_epoch_millis", now)
                put("updated_at_epoch_millis", now)
                put("last_turn_ordinal", 1)
            })
            db.insertOrThrow("tutor_message", null, ContentValues().apply {
                put("message_id", "message-1")
                put("conversation_id", "conversation-1")
                put("ordinal", 1)
                put("role", "STUDENT")
                put("body_markdown", sessionSentinel)
                put("status", "PERSISTED")
                put("created_at_epoch_millis", now)
            })
            db.insertOrThrow("tutor_message_source_asset", null, ContentValues().apply {
                put("message_id", "message-1")
                put("source_asset_id", sharedAsset)
                put("ordinal", 0)
                put("role", "STEM")
                put("basis_revision_id", "revision-1")
            })

            db.insertOrThrow("review_log", null, ContentValues().apply {
                put("learner_id", "learner-1")
                put("card_id", "pseudo:MATH")
                put("rating", 3)
                put("delta_t_days", 1.0)
                put("duration_ms", 1_000)
                put("reviewed_at_utc", now)
                put("source_kind", "MANUAL")
                put("source_id", "source-1")
                put("evidence_weight", 1.0)
                put("time_bucket", "MORNING")
                put("planned_reason", masterySentinel)
                put("recorded_at", now)
            })
            db.insertOrThrow("learner_chat_evidence", null, ContentValues().apply {
                put("evidence_id", "evidence-1")
                put("learner_id", "learner-1")
                put("conversation_id", "conversation-1")
                put("knowledge_node_id", knowledgeNodeId)
                put("direction", "POSITIVE")
                put("weight", 1.0)
                put("reason_markdown", masterySentinel)
                put("confidence", 0.9)
                put("source_kind", "MODEL_CHAT")
                put("created_at_epoch_millis", now)
            })

            insertModelTask(db, knowledgeNodeId, now)

            val sharedOperation = figureOperation(db, "op-tutor", "TUTOR_MESSAGE", now)
            val issueOperation = figureOperation(db, "op-issue", "PROBLEM_REVISION", now)
            val sessionOnlyOperation = figureOperation(db, "op-session-only", "TUTOR_MESSAGE", now)
            val cleanOperation = figureOperation(db, "op-clean", "TUTOR_MESSAGE", now)
            figureAttempt(db, "attempt-tutor", sharedOperation, sharedAsset, now)
            figureAttempt(db, "attempt-issue", issueOperation, issueAsset, now)
            figureAttempt(db, "attempt-session-only", sessionOnlyOperation, sessionOnlyAsset, now)
            figureAttempt(db, "attempt-clean", cleanOperation, cleanAsset, now)
            db.insertOrThrow("clean_redraw_task", null, ContentValues().apply {
                put("task_id", "clean-task-1")
                put("revision_id", "revision-1")
                put("source_asset_id", issueAsset)
                put("source_image_sha256", "sha-issue")
                put("request_fingerprint", "clean-request-1")
                // 故意指向一个不存在的草稿：no-new-dangling 只禁止“裁剪新增悬空”，
                // 这条历史悬空在裁剪前就存在，必须原样允许。
                put("subject_id", "draft-does-not-exist")
                put("status", "SUCCEEDED")
                put("attempt_count", 1)
                put("created_at_epoch_millis", now)
                put("updated_at_epoch_millis", now)
                put("operation_id", cleanOperation)
            })

            db.insertOrThrow("library_search_content", null, ContentValues().apply {
                put("problem_revision_id", "revision-1")
                put("stem_text", issueSentinel)
                put("options_text", "")
                put("solution_text", "")
                put("subject", "MATH")
                put("chapter", "")
                put("knowledge_points", "")
                put("tags", "")
                put("error_reason", "")
                put("formula_tokens", "")
            })
            db.execSQL("INSERT INTO library_search_fts(library_search_fts) VALUES('rebuild')")

            db.insertOrThrow("knowledge_search_feature", null, ContentValues().apply {
                put("subject", "MATH")
                put("search_feature", "feature-1")
                put("knowledge_node_id", knowledgeNodeId)
            })
            db.insertOrThrow("knowledge_search_index_state", null, ContentValues().apply {
                put("subject", "MATH")
                put("index_version", 1)
            })

            return SeedIds(
                sharedAssetId = sharedAsset,
                issueAssetId = issueAsset,
                sessionOnlyAssetId = sessionOnlyAsset,
                cleanAssetId = cleanAsset,
                allAssetIds = setOf(sharedAsset, issueAsset, sessionOnlyAsset, cleanAsset),
            )
        } finally {
            db.close()
        }
    }

    private fun canonicalAsset(db: SQLiteDatabase, seed: String, now: Long): String {
        val id = "asset-$seed"
        db.insertOrThrow("canonical_source_asset", null, ContentValues().apply {
            put("source_asset_id", id)
            put("content_sha256", "sha-$seed")
            put("relative_path", "source-assets/$seed.png")
            put("mime_type", "image/png")
            put("byte_size", 10L)
            put("width", 1)
            put("height", 1)
            put("source_type", "CAPTURE")
            put("created_at_epoch_millis", now)
        })
        return id
    }

    private fun insertModelTask(db: SQLiteDatabase, knowledgeNodeId: String, now: Long) {
        val operation = "operation-model-task-1"
        db.insertOrThrow("model_task_operation", null, ContentValues().apply {
            put("operation_fingerprint", operation)
            put("subject_id", knowledgeNodeId)
            put("task_kind", "REVIEW_RERANK")
            put("dispatch_count", 1)
            put("created_at_epoch_millis", now)
            put("updated_at_epoch_millis", now)
        })
        db.insertOrThrow("model_task", null, ContentValues().apply {
            put("task_id", "task-1")
            put("request_id", "request-1")
            put("request_fingerprint", "request-fingerprint-1")
            put("operation_fingerprint", operation)
            put("request_snapshot", "{}")
            put("task_kind", "REVIEW_RERANK")
            put("subject_id", knowledgeNodeId)
            put("status", "SUCCEEDED")
            put("state_version", 1)
            put("stage", "DONE")
            put("user_message", "rerank")
            put("attempt_count", 1)
            put("created_at_epoch_millis", now)
            put("updated_at_epoch_millis", now)
        })
        db.insertOrThrow("model_task_event", null, ContentValues().apply {
            put("task_id", "task-1")
            put("state_version", 1)
            putNull("previous_status")
            put("next_status", "SUCCEEDED")
            put("stage", "DONE")
            put("user_message", "rerank")
            put("attempt_count", 1)
            put("created_at_epoch_millis", now)
        })
    }

    private fun figureOperation(
        db: SQLiteDatabase,
        id: String,
        sourceKind: String,
        now: Long,
    ): String {
        db.insertOrThrow("figure_generation_operation", null, ContentValues().apply {
            put("operation_id", id)
            put("learner_id", "learner-1")
            put("source_kind", sourceKind)
            put("source_id", "source-$id")
            put("basis_fingerprint", "basis-$id")
            put("purpose", "FIGURE")
            put("policy_version", "v1")
            put("status", "SUCCEEDED")
            put("reserved_remote_dispatches", 1)
            put("used_remote_dispatches", 1)
            put("final_answer_reserved", 0)
            put("created_at_epoch_millis", now)
            put("updated_at_epoch_millis", now)
        })
        return id
    }

    private fun figureAttempt(
        db: SQLiteDatabase,
        id: String,
        operationId: String,
        outputAssetId: String,
        now: Long,
    ) {
        db.insertOrThrow("figure_generation_attempt", null, ContentValues().apply {
            put("attempt_id", id)
            put("operation_id", operationId)
            put("attempt_ordinal", 1)
            put("method", "IMAGE_MODEL")
            put("status", "SUCCEEDED")
            put("input_fingerprint", "input-$id")
            put("output_asset_id", outputAssetId)
            put("started_at_epoch_millis", now)
            put("ended_at_epoch_millis", now)
        })
    }

    private fun assertCount(db: SQLiteDatabase, table: String, expected: Int) {
        db.rawQuery("SELECT COUNT(*) FROM \"$table\"", null).use { cursor ->
            cursor.moveToFirst()
            assertEquals("$table 行数", expected.toLong(), cursor.getLong(0))
        }
    }

    private fun ftsOrphanCount(db: SQLiteDatabase): Long =
        db.rawQuery(
            """
            SELECT (SELECT COUNT(*) FROM library_search_fts
                    WHERE docid NOT IN (SELECT rowid FROM library_search_content))
                 + (SELECT COUNT(*) FROM library_search_content
                    WHERE rowid NOT IN (SELECT docid FROM library_search_fts))
            """.trimIndent(),
            null,
        ).use { cursor ->
            cursor.moveToFirst()
            cursor.getLong(0)
        }

    private fun captureCatalog(path: String): CatalogSnapshot =
        SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            val schema = mutableListOf<String>()
            db.rawQuery(
                """
                SELECT type, name, tbl_name, COALESCE(sql, '')
                FROM sqlite_master
                WHERE name NOT LIKE 'sqlite_autoindex_%'
                ORDER BY type, name
                """.trimIndent(),
                null,
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    schema += listOf(
                        cursor.getString(0),
                        cursor.getString(1),
                        cursor.getString(2),
                        cursor.getString(3),
                    ).joinToString("|")
                }
            }
            val sequence = mutableListOf<String>()
            db.rawQuery(
                "SELECT name, seq FROM sqlite_sequence ORDER BY name",
                null,
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    sequence += "${cursor.getString(0)}=${cursor.getLong(1)}"
                }
            }
            val identityHash = db.rawQuery(
                "SELECT identity_hash FROM room_master_table WHERE id = 42",
                null,
            ).use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
            CatalogSnapshot(
                schemaSql = schema,
                roomMasterIdentityHash = identityHash,
                sqliteSequence = sequence,
                userVersion = db.version,
            )
        }

    private data class CatalogSnapshot(
        val schemaSql: List<String>,
        val roomMasterIdentityHash: String?,
        val sqliteSequence: List<String>,
        val userVersion: Int,
    )

    private data class SeedIds(
        val sharedAssetId: String,
        val issueAssetId: String,
        val sessionOnlyAssetId: String,
        val cleanAssetId: String,
        val allAssetIds: Set<String>,
    )
}
