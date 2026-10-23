package com.tingyun.smartmistakebook.core.data.backup

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile

/**
 * 快照裁剪失败。**只要抛出，调用方就不能成包**：I1–I7 任意一条不过、VACUUM 失败、
 * 预检失败或快照不可写都在这里显式失败，不返回"看起来完成"的报告。
 */
class BackupSnapshotTrimException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

/** 快照库仍引用（应随包）的一条资产行。 */
data class TrimAssetReference(
    val sourceAssetId: String,
    val relativePath: String,
    val byteSize: Long,
    val contentSha256: String,
)

/**
 * 裁剪报告。所有表计数都按 [SmbkSelectionPlan.allTables] 的 86 个键给出（0 也写），
 * 可直接作为 v3 manifest 的 `rowCounts` 来源；`trimmedTableCount` 只数**实际删除了行**的表
 * （不含 FTS rebuild），与 §9.7 的 digest 口径一致。
 */
data class TrimReport(
    val selectionMask: Int,
    val schemaVersion: Int,
    val preRowCounts: Map<String, Long>,
    val postRowCounts: Map<String, Long>,
    val deletedRowCounts: Map<String, Long>,
    val trimmedTableCount: Int,
    val trimmedRowCount: Long,
    val vacuumed: Boolean,
    val ftsRebuilt: Boolean,
    val ftsOrphanDocIds: Long,
    val referencedAssets: List<TrimAssetReference>,
    val referencedAssetIds: Set<String>,
    val databaseBytesBefore: Long,
    val databaseBytesAfter: Long,
)

/**
 * 6C-2 的快照裁剪器：只按**路径**打开快照文件，绝不触碰实时库或 Room 连接。
 *
 * 顺序（对应设计 §2.2/§2.4）：
 * 1. 在 `BEGIN` **之前**打开 `PRAGMA foreign_keys=ON` 并复核确实生效；
 * 2. 单事务内先读 pre 计数与软引用悬空集合，再按 [SmbkSelectionPlan.trimOrder] 删除；
 * 3. 事务内重建 `library_search_fts`，随后跑 I1–I7；
 * 4. 提交后**只有确实删过行、或真子集仍有空闲页**才 `VACUUM`（空闲页可能留着历史会话/
 *    掌握正文，而清单已声明不含这些类别；主路径 `VACUUM INTO` 的快照没有空闲页，回退的
 *    字节拷贝则可能有），再 checkpoint、关库、清理 `-wal/-shm`、fsync。
 *
 * 合法性闸门直接用 [SmbkSelectionPlan.requireCreatableMask]：本批只接受 9/13/15，
 * 0/8 与不自洽组合在打开文件之前就抛 `IllegalArgumentException`，不做静默补齐。
 * 全选（15）仍会裁掉两张派生索引并重建 FTS（本批裁定 ③ 的 a+c），用户类别一行不动。
 *
 * I5 的范围说明：本类能证明的是**库内资产闭合**（五站点引用集合 = 四根 + attempt 产物钉住
 * 的集合，且每一条都能在 `canonical_source_asset` 解析到行）。包内文件是否存在、字节数/SHA
 * 是否一致由 T4 的 `resolveReferencedAssetFiles` 与 `SmbkArchiveCodec.create` 用同一份
 * 引用集合核对；裁剪器没有资产目录，不能假装已经检查过文件。
 */
class BackupSnapshotTrimmer(
    /**
     * 可用空间读口。默认取快照父目录的 `usableSpace`；测试注入 0 可覆盖预检失败路径。
     */
    private val freeSpaceProvider: (File) -> Long = { file ->
        file.parentFile?.usableSpace ?: 0L
    },
    /** VACUUM 会再写一份完整文件，预检预留的工作余量（默认 10 MiB）。 */
    private val vacuumWorkingSpaceBytes: Long = 10L * 1024L * 1024L,
) {
    /**
     * I4 的 15 条 `no-new-dangling` 检查。每条返回**悬空源行主键**；pre/post 两次取集合，
     * 断言 `D_post ⊆ D_pre`（只允许减少，不允许裁剪制造新悬空）。
     *
     * 两处口径说明：
     * - `tutor_conversation.anchor_id` 只有 `PROBLEM_REVISION` 指向 `problem.problem_id`；
     *   `EPHEMERAL_DRAFT` 的 anchor_id 是旧 `tutor_session` 令牌，真正的草稿引用在
     *   `capture_draft_id`（已单独检查），不能把会话令牌当 draft 主键反查。
     * - `model_task_operation.subject_id` 按机器块 `checkDetail` 只要求 kind 已知，不要求宿主
     *   仍存在（预算台账先于宿主）；因此该检查是"映射外 kind 行"，且全选时通过绑定 0 关闭。
     */
    private val softReferenceChecks: List<SoftReferenceCheck> = buildSoftReferenceChecks()

    init {
        require(vacuumWorkingSpaceBytes >= 0L) { "VACUUM 工作余量不能为负" }
        val expected = SmbkSelectionPlan.softReferences
            .filter { it.check == CHECK_NO_NEW_DANGLING }
            .map { "${it.table}.${it.column}" }
            .toSet()
        val actual = softReferenceChecks.map { it.id }.toSet()
        check(actual == expected) {
            "快照裁剪器的软引用检查与 SmbkSelectionPlan 漂开：" +
                "missing=${(expected - actual).sorted()} extra=${(actual - expected).sorted()}"
        }
    }

    /**
     * 就地裁剪 [snapshotFile]，返回报告。快照必须已经是**独立单文件**（成包侧
     * `VACUUM INTO`/字节拷贝的产物），本方法不负责创建快照。
     */
    fun trim(snapshotFile: File, mask: Int): TrimReport {
        // 产品裁定：0/8 与不自洽组合在打开文件之前拒绝，且消息里带上原始 mask。
        SmbkSelectionPlan.requireCreatableMask(mask)
        val categories = SmbkSelectionPlan.categoriesOf(mask)
        requireSnapshotFile(snapshotFile)
        preflightSpace(snapshotFile)

        val bytesBefore = snapshotFile.length()
        val database = try {
            SQLiteDatabase.openDatabase(
                snapshotFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READWRITE,
            )
        } catch (failure: SQLiteException) {
            throw BackupSnapshotTrimException(
                "无法按路径打开快照数据库：${snapshotFile.name}（${failure.message.orEmpty()}）",
                failure,
            )
        }

        val outcome = try {
            configureForeignKeys(database)
            validateSchemaIdentity(database, mask)

            val trimmed = trimInTransaction(database, mask, categories)

            val deletedRows = deletedRowCounts(trimmed.preRowCounts, trimmed.postRowCounts).values.sum()
            // 空闲页检查（事项 #8）：只在本次删了行时 VACUUM 不够——旧回退快照是逐字节
            // 拷贝，库里可能早就有"上次删除会话/掌握记录"留下的空闲页；本次这些类别已经
            // 0 行，deletedRows=0，但空闲页字节仍会被打进一份声明"不含 SESSIONS/MASTERY"
            // 的子集包。真子集只要 freelist_count>0 就压实（全选没有"未包含类别"，
            // 不做这次额外开销）。主路径 VACUUM INTO 的快照 freelist 为 0，不会多付代价。
            val freelistPages = if (mask != SmbkSelectionPlan.FULL_MASK) {
                queryScalarLong(database, "PRAGMA freelist_count", emptyList())
            } else {
                0L
            }
            val vacuumed = deletedRows > 0L || freelistPages > 0L
            if (vacuumed) {
                // VACUUM 不能在显式事务里执行；走到这里事务已经提交，I1–I7 全部通过。
                database.execSQL("VACUUM")
            }
            checkpointAndRequireNotBusy(database)
            database.close()

            TrimOutcome(
                preRowCounts = trimmed.preRowCounts,
                postRowCounts = trimmed.postRowCounts,
                changes = trimmed.changes,
                assets = trimmed.assets,
                ftsOrphanDocIds = trimmed.ftsOrphanDocIds,
                schemaVersion = trimmed.schemaVersion,
                vacuumed = vacuumed,
            )
        } catch (failure: BackupSnapshotTrimException) {
            throw failure
        } catch (failure: Exception) {
            throw BackupSnapshotTrimException(
                "快照裁剪失败：${failure.message.orEmpty()}",
                failure,
            )
        } finally {
            if (database.isOpen) {
                runCatching { database.close() }
            }
        }

        cleanSnapshotSidecars(snapshotFile)
        fsyncSnapshot(snapshotFile)
        // 目录项的持久化是 best-effort：Android 上目录可读打开并 fsync；个别文件系统拒绝时
        // 不应把已经通过 I1–I7 的快照误报成失败（文件本身的 fsync 已在上一步强制）。
        fsyncDirectory(snapshotFile.parentFile)

        val deleted = deletedRowCounts(outcome.preRowCounts, outcome.postRowCounts)
        return TrimReport(
            selectionMask = mask,
            schemaVersion = outcome.schemaVersion,
            preRowCounts = outcome.preRowCounts,
            postRowCounts = outcome.postRowCounts,
            deletedRowCounts = deleted,
            trimmedTableCount = deleted.values.count { it > 0L },
            trimmedRowCount = deleted.values.sum(),
            vacuumed = outcome.vacuumed,
            ftsRebuilt = true,
            ftsOrphanDocIds = outcome.ftsOrphanDocIds,
            referencedAssets = outcome.assets,
            referencedAssetIds = outcome.assets.mapTo(linkedSetOf()) { it.sourceAssetId },
            databaseBytesBefore = bytesBefore,
            databaseBytesAfter = snapshotFile.length(),
        )
    }

    // ---------------------------------------------------------------------
    // 事务内：pre 快照、删除、FTS rebuild、I1–I7
    // ---------------------------------------------------------------------

    private fun trimInTransaction(
        database: SQLiteDatabase,
        mask: Int,
        categories: Set<SmbkSelectionCategory>,
    ): TrimOutcome {
        database.beginTransaction()
        try {
            val preRowCounts = countAllTables(database)
            val preDangling = readDanglingKeys(database, mask)

            assertNoUnknownTaskKinds(database, mask)
            if (mask != SmbkSelectionPlan.FULL_MASK) {
                prepareFigureFilter(database, mask, categories)
            }

            val changes = linkedMapOf<String, Long>()
            deleteBySelection(database, mask, categories, changes)

            rebuildFts(database)
            val postRowCounts = countAllTables(database)
            val postDangling = readDanglingKeys(database, mask)

            assertNoNewDangling(preDangling, postDangling)
            assertForeignKeysClosed(database)
            assertTrimmedCategoriesEmpty(database, mask, categories, postRowCounts)
            assertSelectedRowsUnchanged(preRowCounts, postRowCounts, categories)
            assertLedgerConservation(preRowCounts, postRowCounts, changes)

            val assets = readReferencedAssets(database, mask)
            assertAssetClosure(database, mask, assets)
            val ftsOrphanDocIds = countFtsOrphans(database)
            if (ftsOrphanDocIds != 0L) {
                fail("I5/FTS：rebuild 后仍有 $ftsOrphanDocIds 个 docid 与内容表不一致")
            }
            assertIntegrityCheckOk(database)

            database.setTransactionSuccessful()
            return TrimOutcome(
                preRowCounts = preRowCounts,
                postRowCounts = postRowCounts,
                changes = changes,
                assets = assets,
                ftsOrphanDocIds = ftsOrphanDocIds,
                schemaVersion = database.version,
                vacuumed = false,
            )
        } finally {
            database.endTransaction()
        }
    }

    private fun configureForeignKeys(database: SQLiteDatabase) {
        // 必须在 BEGIN 之前：SQLite 每个连接默认 OFF，忘了这一条 RESTRICT 全部失效。
        database.execSQL("PRAGMA foreign_keys = ON")
        val enabled = database.rawQuery("PRAGMA foreign_keys", null).use { cursor ->
            cursor.moveToFirst() && cursor.getInt(0) == 1
        }
        if (!enabled) {
            fail("PRAGMA foreign_keys=ON 未生效，拒绝裁剪（避免静默产出悬空快照）")
        }
    }

    private fun validateSchemaIdentity(database: SQLiteDatabase, mask: Int) {
        if (database.version != SmbkSelectionPlan.SCHEMA_VERSION) {
            fail(
                "快照 schema=${database.version}，裁剪器只支持 schema=" +
                    SmbkSelectionPlan.SCHEMA_VERSION,
            )
        }
        val identityHash = querySingleString(
            database,
            "SELECT identity_hash FROM room_master_table WHERE id = 42",
            emptyList(),
        )
        if (identityHash != SmbkSelectionPlan.IDENTITY_HASH) {
            fail(
                "room_master_table 身份哈希与 schema 69 不符：" +
                    "expected=${SmbkSelectionPlan.IDENTITY_HASH} actual=$identityHash",
            )
        }

        val tables = queryStrings(
            database,
            "SELECT name FROM sqlite_master WHERE type = 'table'",
            emptyList(),
        )
        val missingTables = SmbkSelectionPlan.allTables.filterNot { it in tables }
        if (missingTables.isNotEmpty()) {
            fail("快照缺少裁剪清单中的表：${missingTables.joinToString("、")}")
        }
        val allowedTables = buildSet {
            addAll(SmbkSelectionPlan.allTables)
            add("room_master_table")
            add("android_metadata")
            addAll(FTS_SHADOW_TABLES)
        }
        val unknownTables = tables
            .filterNot { it.startsWith("sqlite_") }
            .filterNot { it in allowedTables }
        if (unknownTables.isNotEmpty() && mask != SmbkSelectionPlan.FULL_MASK) {
            // 未知表不会被裁剪 → 数据会原样随包泄露；schema 漂移必须 fail-closed。
            // 全选例外：没有"未包含的类别"，未知表本来就属于"整库随包"的语义（生产 schema
            // 不会有未知表；设备大库回归夹具用一张额外的 bloat 表把库撑大）。
            fail("快照包含未登记的普通表，拒绝裁剪：${unknownTables.sorted().joinToString("、")}")
        }

        val views = queryStrings(
            database,
            "SELECT name FROM sqlite_master WHERE type = 'view'",
            emptyList(),
        )
        val missingViews = SmbkSelectionPlan.views.filterNot { it in views }
        if (missingViews.isNotEmpty()) {
            fail("快照缺少清单中的视图：${missingViews.joinToString("、")}")
        }
    }

    private fun countAllTables(database: SQLiteDatabase): LinkedHashMap<String, Long> =
        linkedMapOf<String, Long>().apply {
            SmbkSelectionPlan.allTables.forEach { table ->
                put(table, countRows(database, table))
            }
        }

    private fun countRows(database: SQLiteDatabase, table: String): Long =
        database.rawQuery("SELECT COUNT(*) FROM ${quoteIdentifier(table)}", null).use { cursor ->
            if (!cursor.moveToFirst()) fail("无法读取 $table 的行数")
            cursor.getLong(0)
        }

    private fun readDanglingKeys(
        database: SQLiteDatabase,
        mask: Int,
    ): Map<String, Set<String>> {
        val subsetSelected = mask != SmbkSelectionPlan.FULL_MASK
        return softReferenceChecks.associate { check ->
            val args = if (check.appendSubsetFlag) {
                check.args + if (subsetSelected) "1" else "0"
            } else {
                check.args
            }
            check.id to queryStrings(database, check.sql, args).toSet()
        }
    }

    private fun assertNoNewDangling(
        pre: Map<String, Set<String>>,
        post: Map<String, Set<String>>,
    ) {
        for ((id, preKeys) in pre) {
            val postKeys = post[id] ?: emptySet()
            val newDangling = postKeys - preKeys
            if (newDangling.isNotEmpty()) {
                fail(
                    "I4 软引用新增悬空：$id；样例=" +
                        newDangling.sorted().take(5).joinToString("、"),
                )
            }
        }
    }

    private fun assertForeignKeysClosed(database: SQLiteDatabase) {
        val violations = mutableListOf<String>()
        database.rawQuery("PRAGMA foreign_key_check", null).use { cursor ->
            while (cursor.moveToNext() && violations.size < 5) {
                violations += "table=${cursor.getString(0)}, rowid=${cursor.getString(1)}"
            }
        }
        if (violations.isNotEmpty()) {
            fail("I1 foreign_key_check 非空：${violations.joinToString("；")}")
        }
    }

    private fun assertTrimmedCategoriesEmpty(
        database: SQLiteDatabase,
        mask: Int,
        selected: Set<SmbkSelectionCategory>,
        postRowCounts: Map<String, Long>,
    ) {
        for ((category, tables) in SmbkSelectionPlan.categoryTables) {
            if (category in selected) continue
            tables.forEach { table ->
                val remaining = postRowCounts[table] ?: fail("报告缺少 $table 的 post 计数")
                if (remaining != 0L) {
                    fail("I2 未选类别 ${category.name} 的表 $table 仍有 $remaining 行")
                }
            }
        }
        SmbkSelectionPlan.derivedTables.forEach { table ->
            val remaining = postRowCounts[table] ?: fail("报告缺少 $table 的 post 计数")
            if (remaining != 0L) {
                fail("派生索引 $table 必须恒裁，仍剩 $remaining 行")
            }
        }

        val deletedKinds = SmbkSelectionPlan.deletedModelTaskKinds(mask).toList()
        if (deletedKinds.isNotEmpty()) {
            val placeholders = inClause(deletedKinds)
            listOf("model_task", "model_task_operation").forEach { table ->
                val remaining = queryScalarLong(
                    database,
                    "SELECT COUNT(*) FROM ${quoteIdentifier(table)} " +
                        "WHERE task_kind IN ($placeholders)",
                    deletedKinds,
                )
                if (remaining != 0L) {
                    fail("I2 未选 task_kind 在 $table 仍有 $remaining 行")
                }
            }
        }
    }

    private fun assertSelectedRowsUnchanged(
        pre: Map<String, Long>,
        post: Map<String, Long>,
        selected: Set<SmbkSelectionCategory>,
    ) {
        for (category in selected) {
            for (table in SmbkSelectionPlan.categoryTables.getValue(category)) {
                val before = pre[table] ?: fail("报告缺少 $table 的 pre 计数")
                val after = post[table] ?: fail("报告缺少 $table 的 post 计数")
                if (before != after) {
                    fail("I3 选中类别 ${category.name} 的表 $table 被误删：pre=$before post=$after")
                }
            }
        }
        // ALWAYS 表不参与 I2/I3 的分类计数，但同样一行都不能少。
        SmbkSelectionPlan.alwaysTables.forEach { table ->
            val before = pre[table] ?: fail("报告缺少 $table 的 pre 计数")
            val after = post[table] ?: fail("报告缺少 $table 的 post 计数")
            if (before != after) {
                fail("ALWAYS 表 $table 被误删：pre=$before post=$after")
            }
        }
    }

    private fun assertLedgerConservation(
        pre: Map<String, Long>,
        post: Map<String, Long>,
        changes: Map<String, Long>,
    ) {
        for (table in SmbkSelectionPlan.allTables) {
            val before = pre[table] ?: fail("报告缺少 $table 的 pre 计数")
            val after = post[table] ?: fail("报告缺少 $table 的 post 计数")
            val actualDeleted = before - after
            if (actualDeleted < 0L) {
                fail("I6 表 $table 行数增加：pre=$before post=$after")
            }
            val changed = changes[table] ?: 0L
            if (actualDeleted != changed) {
                fail(
                    "I6 台账不守恒：$table pre=$before post=$after " +
                        "actualDeleted=$actualDeleted changes()=$changed",
                )
            }
        }
    }

    private fun assertIntegrityCheckOk(database: SQLiteDatabase) {
        val problems = mutableListOf<String>()
        var sawResult = false
        database.rawQuery("PRAGMA integrity_check", null).use { cursor ->
            while (cursor.moveToNext()) {
                sawResult = true
                val result = cursor.getString(0) ?: ""
                if (result != "ok" && problems.size < 5) {
                    problems += result
                }
            }
        }
        if (!sawResult) fail("I7 integrity_check 没有返回结果")
        if (problems.isNotEmpty()) {
            fail("I7 integrity_check 失败：${problems.joinToString("；")}")
        }
    }

    // ---------------------------------------------------------------------
    // 删除：类别整表 + split 行级
    // ---------------------------------------------------------------------

    private fun deleteBySelection(
        database: SQLiteDatabase,
        mask: Int,
        selected: Set<SmbkSelectionCategory>,
        changes: MutableMap<String, Long>,
    ) {
        val deletedKinds = SmbkSelectionPlan.deletedModelTaskKinds(mask).toList()
        for (table in SmbkSelectionPlan.trimOrder) {
            when {
                table in SmbkSelectionPlan.derivedTables -> deleteAllRows(database, table, changes)
                table in SmbkSelectionPlan.splitTables -> deleteSplitRows(
                    database = database,
                    table = table,
                    mask = mask,
                    deletedKinds = deletedKinds,
                    changes = changes,
                )
                else -> {
                    val category = SmbkSelectionPlan.categoryOf(table)
                    if (category != null && category !in selected) {
                        deleteAllRows(database, table, changes)
                    }
                }
            }
        }
    }

    private fun deleteSplitRows(
        database: SQLiteDatabase,
        table: String,
        mask: Int,
        deletedKinds: List<String>,
        changes: MutableMap<String, Long>,
    ) {
        when (table) {
            "model_task_event" -> if (deletedKinds.isNotEmpty()) {
                deleteWhere(
                    database = database,
                    table = table,
                    sql = "DELETE FROM ${quoteIdentifier(table)} WHERE task_id IN (" +
                        "SELECT task_id FROM ${quoteIdentifier("model_task")} " +
                        "WHERE task_kind IN (${inClause(deletedKinds)}))",
                    args = deletedKinds,
                    changes = changes,
                )
            }
            "model_task" -> if (deletedKinds.isNotEmpty()) {
                deleteWhere(
                    database = database,
                    table = table,
                    sql = "DELETE FROM ${quoteIdentifier(table)} " +
                        "WHERE task_kind IN (${inClause(deletedKinds)})",
                    args = deletedKinds,
                    changes = changes,
                )
            }
            "model_task_operation" -> if (deletedKinds.isNotEmpty()) {
                deleteWhere(
                    database = database,
                    table = table,
                    sql = "DELETE FROM ${quoteIdentifier(table)} " +
                        "WHERE task_kind IN (${inClause(deletedKinds)})",
                    args = deletedKinds,
                    changes = changes,
                )
            }
            "figure_generation_attempt" -> if (mask != SmbkSelectionPlan.FULL_MASK) {
                deleteWhere(
                    database = database,
                    table = table,
                    sql = "DELETE FROM ${quoteIdentifier(table)} " +
                        "WHERE attempt_id NOT IN " +
                        "(SELECT attempt_id FROM smbk_kept_attempt)",
                    args = emptyList(),
                    changes = changes,
                )
            }
            "figure_generation_operation" -> if (mask != SmbkSelectionPlan.FULL_MASK) {
                deleteWhere(
                    database = database,
                    table = table,
                    sql = "DELETE FROM ${quoteIdentifier(table)} " +
                        "WHERE operation_id NOT IN " +
                        "(SELECT operation_id FROM smbk_kept_operation)",
                    args = emptyList(),
                    changes = changes,
                )
            }
            else -> fail("SmbkSelectionPlan 中出现未实现的 split 表：$table")
        }
    }

    private fun deleteAllRows(
        database: SQLiteDatabase,
        table: String,
        changes: MutableMap<String, Long>,
    ) {
        deleteWhere(
            database = database,
            table = table,
            sql = "DELETE FROM ${quoteIdentifier(table)}",
            args = emptyList(),
            changes = changes,
        )
    }

    private fun deleteWhere(
        database: SQLiteDatabase,
        table: String,
        sql: String,
        args: List<String>,
        changes: MutableMap<String, Long>,
    ) {
        if (args.isEmpty()) {
            database.execSQL(sql)
        } else {
            database.execSQL(sql, Array<Any?>(args.size) { index -> args[index] })
        }
        // 需求明确要求 changes() 计数：不用 executeUpdateDelete() 的返回值代替。
        val changed = database.rawQuery("SELECT changes()", null).use { cursor ->
            if (!cursor.moveToFirst()) fail("无法读取 $table 的 changes()")
            cursor.getLong(0)
        }
        changes[table] = (changes[table] ?: 0L) + changed
    }

    // ---------------------------------------------------------------------
    // split 行级预扫（§9.5）
    // ---------------------------------------------------------------------

    private fun prepareFigureFilter(
        database: SQLiteDatabase,
        mask: Int,
        selected: Set<SmbkSelectionCategory>,
    ) {
        database.execSQL("CREATE TEMP TABLE smbk_kept_asset(asset_id TEXT PRIMARY KEY)")
        database.execSQL("CREATE TEMP TABLE smbk_kept_operation(operation_id TEXT PRIMARY KEY)")
        database.execSQL("CREATE TEMP TABLE smbk_kept_attempt(attempt_id TEXT PRIMARY KEY)")

        val issueBookSelected = SmbkSelectionCategory.ISSUE_BOOK in selected
        val sessionsSelected = SmbkSelectionCategory.SESSIONS in selected
        val issueFlag = if (issueBookSelected) "1" else "0"
        val sessionsFlag = if (sessionsSelected) "1" else "0"

        // R：存活根引用表在**任何 DELETE 之前**指向的资产。
        database.execSQL(
            """
            INSERT OR IGNORE INTO smbk_kept_asset(asset_id)
            SELECT source_asset_id FROM problem_revision_source_asset WHERE CAST(? AS INTEGER) = 1
            UNION SELECT source_asset_id FROM problem_draft_source_asset WHERE CAST(? AS INTEGER) = 1
            UNION SELECT source_asset_id FROM problem_draft
                  WHERE CAST(? AS INTEGER) = 1 AND source_asset_id IS NOT NULL
            UNION SELECT source_asset_id FROM tutor_message_source_asset WHERE CAST(? AS INTEGER) = 1
            """.trimIndent(),
            arrayOf<Any?>(issueFlag, issueFlag, issueFlag, sessionsFlag),
        )

        // O_seed：按 source_kind 的类别选；不按 source_id 反查宿主行。
        val operationArms = mutableListOf<String>()
        val operationArgs = mutableListOf<String>()
        val tutorKinds = SmbkSelectionPlan.figureOperationSourceKindCategory
            .filterValues { it == SmbkSelectionCategory.SESSIONS }
            .keys.toList()
        val issueKinds = SmbkSelectionPlan.figureOperationSourceKindCategory
            .filterValues { it == SmbkSelectionCategory.ISSUE_BOOK }
            .keys.toList()
        if (tutorKinds.isNotEmpty()) {
            operationArms += "(CAST(? AS INTEGER) = 1 AND source_kind IN (${inClause(tutorKinds)}))"
            operationArgs += sessionsFlag
            operationArgs += tutorKinds
        }
        if (issueKinds.isNotEmpty()) {
            operationArms += "(CAST(? AS INTEGER) = 1 AND source_kind IN (${inClause(issueKinds)}))"
            operationArgs += issueFlag
            operationArgs += issueKinds
        }
        if (operationArms.isEmpty()) {
            fail("figureOperationSourceKindCategory 为空，无法执行 split 行级规则")
        }
        database.execSQL(
            "INSERT OR IGNORE INTO smbk_kept_operation(operation_id) " +
                "SELECT operation_id FROM figure_generation_operation " +
                "WHERE ${operationArms.joinToString(" OR ")}",
            Array<Any?>(operationArgs.size) { index -> operationArgs[index] },
        )
        database.execSQL(
            """
            INSERT OR IGNORE INTO smbk_kept_operation(operation_id)
            SELECT operation_id FROM clean_redraw_task
            WHERE CAST(? AS INTEGER) = 1 AND operation_id IS NOT NULL
            """.trimIndent(),
            arrayOf<Any?>(issueFlag),
        )

        // O_ref：产出资产仍被 R 引用的 operation（可能来自未选中的宿主类别）。
        database.execSQL(
            """
            INSERT OR IGNORE INTO smbk_kept_operation(operation_id)
            SELECT operation.operation_id
            FROM figure_generation_operation AS operation
            WHERE EXISTS (
                SELECT 1
                FROM figure_generation_attempt AS attempt
                JOIN smbk_kept_asset AS kept
                  ON kept.asset_id = attempt.output_asset_id
                WHERE attempt.operation_id = operation.operation_id
            )
            """.trimIndent(),
        )

        // A_keep：O_keep 的全部 attempt ∪ 产出 R 中资产的 attempt。
        database.execSQL(
            """
            INSERT OR IGNORE INTO smbk_kept_attempt(attempt_id)
            SELECT attempt_id FROM figure_generation_attempt
            WHERE operation_id IN (SELECT operation_id FROM smbk_kept_operation)
               OR output_asset_id IN (SELECT asset_id FROM smbk_kept_asset)
            """.trimIndent(),
        )

        val knownSourceKinds = SmbkSelectionPlan.figureOperationSourceKindCategory.keys.toList()
        val unknownOperations = queryStrings(
            database,
            "SELECT operation_id FROM figure_generation_operation " +
                "WHERE source_kind NOT IN (${inClause(knownSourceKinds)}) " +
                "AND operation_id NOT IN " +
                "(SELECT operation_id FROM smbk_kept_operation) " +
                "ORDER BY operation_id LIMIT 20",
            knownSourceKinds,
        )
        if (unknownOperations.isNotEmpty()) {
            fail(
                "真子集遇到映射外的 figure source_kind（未被共享图/clean_redraw 拉回）：" +
                    unknownOperations.take(5).joinToString("、"),
            )
        }
    }

    private fun assertNoUnknownTaskKinds(database: SQLiteDatabase, mask: Int) {
        if (mask == SmbkSelectionPlan.FULL_MASK) return
        val knownKinds = SmbkSelectionPlan.modelTaskKindCategory.keys.toList()
        listOf(
            "model_task" to "task_id",
            "model_task_operation" to "operation_fingerprint",
        ).forEach { (table, key) ->
            val unknown = queryStrings(
                database,
                "SELECT $key || ' / ' || task_kind FROM ${quoteIdentifier(table)} " +
                    "WHERE task_kind NOT IN (${inClause(knownKinds)}) " +
                    "ORDER BY $key LIMIT 20",
                knownKinds,
            )
            if (unknown.isNotEmpty()) {
                fail(
                    "真子集遇到映射外的 task_kind，拒绝猜测（$table）：" +
                        unknown.take(5).joinToString("、"),
                )
            }
        }
    }

    // ---------------------------------------------------------------------
    // FTS 与资产闭合
    // ---------------------------------------------------------------------

    private fun rebuildFts(database: SQLiteDatabase) {
        database.execSQL(
            "INSERT INTO ${quoteIdentifier("library_search_fts")}" +
                "(library_search_fts) VALUES('rebuild')",
        )
    }

    private fun countFtsOrphans(database: SQLiteDatabase): Long =
        queryScalarLong(
            database,
            """
            SELECT
                (SELECT COUNT(*) FROM library_search_fts
                 WHERE docid NOT IN (SELECT rowid FROM library_search_content))
              + (SELECT COUNT(*) FROM library_search_content
                 WHERE rowid NOT IN (SELECT docid FROM library_search_fts))
            """.trimIndent(),
            emptyList(),
        )

    private fun readReferencedAssets(
        database: SQLiteDatabase,
        mask: Int,
    ): List<TrimAssetReference> {
        val pinSql = if (mask == SmbkSelectionPlan.FULL_MASK) {
            FULL_ASSET_PINS_SQL
        } else {
            TRIMMED_ASSET_PINS_SQL
        }
        return queryAssets(
            database,
            """
            SELECT asset.source_asset_id,
                   asset.relative_path,
                   asset.byte_size,
                   asset.content_sha256
            FROM canonical_source_asset AS asset
            WHERE asset.source_asset_id IN ($pinSql)
            ORDER BY asset.source_asset_id
            """.trimIndent(),
            emptyList(),
        )
    }

    private fun assertAssetClosure(
        database: SQLiteDatabase,
        mask: Int,
        assets: List<TrimAssetReference>,
    ) {
        val pinned = readPinnedAssetIds(database, mask)
        val fiveSiteReferenced = queryStrings(
            database,
            FIVE_SITE_ASSET_IDS_SQL,
            emptyList(),
        ).toSet()
        if (pinned != fiveSiteReferenced) {
            fail(
                "I5 资产闭合不一致：pin-only=" +
                    (pinned - fiveSiteReferenced).sorted().take(5).joinToString("、") +
                    "；five-site-only=" +
                    (fiveSiteReferenced - pinned).sorted().take(5).joinToString("、"),
            )
        }

        val resolved = assets.mapTo(linkedSetOf()) { it.sourceAssetId }
        if (resolved != pinned) {
            fail(
                "I5 引用的资产行在 canonical_source_asset 缺失：" +
                    (pinned - resolved).sorted().take(5).joinToString("、"),
            )
        }
        assets.forEach { asset ->
            if (asset.relativePath.isBlank() || asset.contentSha256.isBlank() || asset.byteSize < 0L) {
                fail("I5 资产行列非法：${asset.sourceAssetId}")
            }
        }
    }

    private fun readPinnedAssetIds(
        database: SQLiteDatabase,
        mask: Int,
    ): Set<String> = queryStrings(
        database,
        if (mask == SmbkSelectionPlan.FULL_MASK) {
            FULL_ASSET_PINS_SQL
        } else {
            TRIMMED_ASSET_PINS_SQL
        },
        emptyList(),
    ).toSet()

    private fun queryAssets(
        database: SQLiteDatabase,
        sql: String,
        args: List<String>,
    ): List<TrimAssetReference> = database.rawQuery(sql, args.toNullableArray()).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(
                    TrimAssetReference(
                        sourceAssetId = cursor.getString(0) ?: "",
                        relativePath = cursor.getString(1) ?: "",
                        byteSize = cursor.getLong(2),
                        contentSha256 = cursor.getString(3) ?: "",
                    ),
                )
            }
        }
    }

    // ---------------------------------------------------------------------
    // 事务后：VACUUM / 单文件交付
    // ---------------------------------------------------------------------

    private fun checkpointAndRequireNotBusy(database: SQLiteDatabase) {
        // PRAGMA 返回一行（busy, log, checkpointed）；execSQL 会把它当"返回结果"拒绝。
        val busy = database.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
        if (busy != 0) {
            fail("wal_checkpoint(TRUNCATE) 返回 busy=$busy，拒绝交付带未落盘帧的快照")
        }
    }

    private fun cleanSnapshotSidecars(snapshotFile: File) {
        listOf(
            File("${snapshotFile.absolutePath}-wal"),
            File("${snapshotFile.absolutePath}-shm"),
        ).forEach { sidecar ->
            if (sidecar.exists() && !sidecar.delete()) {
                fail("无法删除快照附属文件：${sidecar.name}")
            }
        }
    }

    private fun fsyncSnapshot(snapshotFile: File) {
        try {
            RandomAccessFile(snapshotFile, "r").use { file -> file.fd.sync() }
        } catch (failure: Exception) {
            throw BackupSnapshotTrimException(
                "快照 fsync 失败：${snapshotFile.name}（${failure.message.orEmpty()}）",
                failure,
            )
        }
    }

    private fun fsyncDirectory(directory: File?) {
        if (directory == null || !directory.isDirectory) return
        runCatching {
            FileInputStream(directory).use { stream -> stream.fd.sync() }
        }
    }

    // ---------------------------------------------------------------------
    // 纯查询与小工具
    // ---------------------------------------------------------------------

    private fun requireSnapshotFile(snapshotFile: File) {
        if (!snapshotFile.isFile || snapshotFile.length() <= 0L) {
            fail("快照文件不存在或为空：${snapshotFile.absolutePath}")
        }
        if (snapshotFile.parentFile?.isDirectory != true) {
            fail("快照文件所在目录不可用：${snapshotFile.parentFile?.absolutePath}")
        }
    }

    private fun preflightSpace(snapshotFile: File) {
        val snapshotBytes = snapshotFile.length()
        val required = try {
            Math.addExact(Math.addExact(snapshotBytes, snapshotBytes), vacuumWorkingSpaceBytes)
        } catch (_: ArithmeticException) {
            Long.MAX_VALUE
        }
        val available = freeSpaceProvider(snapshotFile)
        if (available < required) {
            fail(
                "磁盘空间不足：快照裁剪需要 ${toMegabytes(required)}MB，" +
                    "可用 ${toMegabytes(available)}MB（快照 ${toMegabytes(snapshotBytes)}MB，" +
                    "VACUUM 工作余量 ${toMegabytes(vacuumWorkingSpaceBytes)}MB）",
            )
        }
    }

    private fun deletedRowCounts(
        pre: Map<String, Long>,
        post: Map<String, Long>,
    ): LinkedHashMap<String, Long> = linkedMapOf<String, Long>().apply {
        SmbkSelectionPlan.allTables.forEach { table ->
            val before = pre[table] ?: fail("报告缺少 $table 的 pre 计数")
            val after = post[table] ?: fail("报告缺少 $table 的 post 计数")
            put(table, before - after)
        }
    }

    private fun querySingleString(
        database: SQLiteDatabase,
        sql: String,
        args: List<String>,
    ): String? = database.rawQuery(sql, args.toNullableArray()).use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }

    private fun queryStrings(
        database: SQLiteDatabase,
        sql: String,
        args: List<String>,
    ): List<String> = database.rawQuery(sql, args.toNullableArray()).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(cursor.getString(0) ?: "")
            }
        }
    }

    private fun queryScalarLong(
        database: SQLiteDatabase,
        sql: String,
        args: List<String>,
    ): Long = database.rawQuery(sql, args.toNullableArray()).use { cursor ->
        if (!cursor.moveToFirst()) fail("查询没有返回计数：${sql.take(80)}")
        cursor.getLong(0)
    }

    private fun List<String>.toNullableArray(): Array<String>? =
        takeIf { it.isNotEmpty() }?.toTypedArray()

    private fun fail(message: String): Nothing = throw BackupSnapshotTrimException(message)

    private fun quoteIdentifier(identifier: String): String =
        "\"" + identifier.replace("\"", "\"\"") + "\""

    private fun toMegabytes(bytes: Long): Long = bytes / 1024L / 1024L

    private fun inClause(values: Collection<String>): String =
        values.joinToString(",") { "?" }

    private data class SoftReferenceCheck(
        val id: String,
        val sql: String,
        val args: List<String>,
        val appendSubsetFlag: Boolean = false,
    )

    private data class TrimOutcome(
        val preRowCounts: LinkedHashMap<String, Long>,
        val postRowCounts: LinkedHashMap<String, Long>,
        val changes: Map<String, Long>,
        val assets: List<TrimAssetReference>,
        val ftsOrphanDocIds: Long,
        val schemaVersion: Int,
        val vacuumed: Boolean,
    )

    private fun buildSoftReferenceChecks(): List<SoftReferenceCheck> {
        val terminalStatuses = listOf("SUCCEEDED", "PERMANENT_FAILURE", "CANCELLED")
        val captureKinds = listOf("CAPTURE_ASSESS", "CAPTURE_PARSE")
        val classifyKinds = listOf("PROBLEM_CLASSIFY", "PROBLEM_RELATE")
        val tutorKinds = listOf(
            "TUTOR_PLAN",
            "TUTOR_RESPOND",
            "TUTOR_LOBBY",
            "TUTOR_EVALUATE",
            "LEARNING_SUMMARIZE",
        )
        val knowledgeKinds = listOf("KNOWLEDGE_QUIZ", "REVIEW_RERANK")
        val knownKinds = SmbkSelectionPlan.modelTaskKindCategory.keys.toList()

        val modelTaskSql = """
            SELECT task_id FROM model_task
            WHERE status NOT IN (${inClause(terminalStatuses)})
              AND (
                (task_kind IN (${inClause(captureKinds)})
                 AND NOT EXISTS (
                    SELECT 1 FROM problem_draft AS draft
                    WHERE draft.draft_id = model_task.subject_id))
                OR
                (task_kind IN (${inClause(classifyKinds)})
                 AND NOT EXISTS (
                    SELECT 1 FROM problem_revision AS revision
                    WHERE revision.revision_id = model_task.subject_id))
                OR
                (task_kind = 'IMAGE_PIPELINE_CLASSIFY'
                 AND NOT EXISTS (
                    SELECT 1 FROM canonical_source_asset AS asset
                    WHERE asset.source_asset_id = model_task.subject_id)
                 AND NOT EXISTS (
                    SELECT 1 FROM problem_draft AS draft
                    WHERE draft.draft_id = model_task.subject_id))
                OR
                (task_kind IN (${inClause(tutorKinds)})
                 AND NOT EXISTS (
                    SELECT 1 FROM tutor_conversation AS conversation
                    WHERE conversation.conversation_id = model_task.subject_id
                       OR conversation.conversation_id =
                          substr(model_task.subject_id,
                                 length('tutor-conv:captured:') + 1)))
                OR
                (task_kind IN (${inClause(knowledgeKinds)})
                 AND NOT EXISTS (
                    SELECT 1 FROM knowledge_node AS node
                    WHERE node.knowledge_node_id = model_task.subject_id))
              )
        """.trimIndent()
        val modelTaskArgs =
            terminalStatuses + captureKinds + classifyKinds + tutorKinds + knowledgeKinds

        return listOf(
            SoftReferenceCheck("model_task.subject_id", modelTaskSql, modelTaskArgs),
            SoftReferenceCheck(
                id = "model_task_operation.subject_id",
                sql = "SELECT operation_fingerprint FROM model_task_operation " +
                    "WHERE task_kind NOT IN (${inClause(knownKinds)}) AND CAST(? AS INTEGER) = 1",
                args = knownKinds,
                appendSubsetFlag = true,
            ),
            SoftReferenceCheck(
                "tutor_conversation.capture_draft_id",
                """
                SELECT conversation_id FROM tutor_conversation
                WHERE capture_draft_id IS NOT NULL
                  AND NOT EXISTS (
                    SELECT 1 FROM problem_draft AS draft
                    WHERE draft.draft_id = tutor_conversation.capture_draft_id)
                """.trimIndent(),
                emptyList(),
            ),
            SoftReferenceCheck(
                "tutor_conversation.anchor_practice_unit_id",
                """
                SELECT conversation_id FROM tutor_conversation
                WHERE anchor_practice_unit_id IS NOT NULL
                  AND NOT EXISTS (
                    SELECT 1 FROM practice_unit AS unit
                    WHERE unit.practice_unit_id = tutor_conversation.anchor_practice_unit_id)
                """.trimIndent(),
                emptyList(),
            ),
            SoftReferenceCheck(
                "tutor_conversation.anchor_problem_revision_id",
                """
                SELECT conversation_id FROM tutor_conversation
                WHERE anchor_problem_revision_id IS NOT NULL
                  AND NOT EXISTS (
                    SELECT 1 FROM problem_revision AS revision
                    WHERE revision.revision_id =
                          tutor_conversation.anchor_problem_revision_id)
                """.trimIndent(),
                emptyList(),
            ),
            SoftReferenceCheck(
                "tutor_conversation.anchor_id",
                """
                SELECT conversation_id FROM tutor_conversation
                WHERE anchor_kind = 'PROBLEM_REVISION'
                  AND anchor_id IS NOT NULL
                  AND NOT EXISTS (
                    SELECT 1 FROM problem
                    WHERE problem.problem_id = tutor_conversation.anchor_id)
                """.trimIndent(),
                emptyList(),
            ),
            SoftReferenceCheck(
                "tutor_message.bound_problem_id",
                """
                SELECT message_id FROM tutor_message
                WHERE bound_problem_id IS NOT NULL
                  AND NOT EXISTS (
                    SELECT 1 FROM problem
                    WHERE problem.problem_id = tutor_message.bound_problem_id)
                """.trimIndent(),
                emptyList(),
            ),
            SoftReferenceCheck(
                "tutor_message.bound_problem_revision_id",
                """
                SELECT message_id FROM tutor_message
                WHERE bound_problem_revision_id IS NOT NULL
                  AND NOT EXISTS (
                    SELECT 1 FROM problem_revision AS revision
                    WHERE revision.revision_id = tutor_message.bound_problem_revision_id)
                """.trimIndent(),
                emptyList(),
            ),
            SoftReferenceCheck(
                "tutor_message_source_asset.basis_revision_id",
                """
                SELECT message_id || char(31) || ordinal
                FROM tutor_message_source_asset
                WHERE basis_revision_id IS NOT NULL
                  AND NOT EXISTS (
                    SELECT 1 FROM problem_revision AS revision
                    WHERE revision.revision_id =
                          tutor_message_source_asset.basis_revision_id)
                """.trimIndent(),
                emptyList(),
            ),
            SoftReferenceCheck(
                "learner_chat_evidence.conversation_id",
                """
                SELECT evidence_id FROM learner_chat_evidence
                WHERE NOT EXISTS (
                    SELECT 1 FROM tutor_conversation AS conversation
                    WHERE conversation.conversation_id =
                          learner_chat_evidence.conversation_id)
                """.trimIndent(),
                emptyList(),
            ),
            SoftReferenceCheck(
                "binding_change_event.practice_unit_id",
                """
                SELECT binding_change_id FROM binding_change_event
                WHERE NOT EXISTS (
                    SELECT 1 FROM practice_unit AS unit
                    WHERE unit.practice_unit_id = binding_change_event.practice_unit_id)
                """.trimIndent(),
                emptyList(),
            ),
            SoftReferenceCheck(
                "student_model_prediction.practice_unit_id",
                """
                SELECT prediction_id FROM student_model_prediction
                WHERE NOT EXISTS (
                    SELECT 1 FROM practice_unit AS unit
                    WHERE unit.practice_unit_id =
                          student_model_prediction.practice_unit_id)
                """.trimIndent(),
                emptyList(),
            ),
            SoftReferenceCheck(
                "review_log.card_id",
                """
                SELECT review_log_id FROM review_log
                WHERE card_id NOT LIKE 'pseudo:%'
                  AND NOT EXISTS (
                    SELECT 1 FROM practice_unit AS unit
                    WHERE unit.practice_unit_id = review_log.card_id)
                """.trimIndent(),
                emptyList(),
            ),
            SoftReferenceCheck(
                "clean_redraw_task.subject_id",
                """
                SELECT task_id FROM clean_redraw_task
                WHERE NOT EXISTS (
                    SELECT 1 FROM problem_draft AS draft
                    WHERE draft.draft_id = clean_redraw_task.subject_id)
                """.trimIndent(),
                emptyList(),
            ),
            SoftReferenceCheck(
                "clean_redraw_task.operation_id",
                """
                SELECT task_id FROM clean_redraw_task
                WHERE operation_id IS NOT NULL
                  AND NOT EXISTS (
                    SELECT 1 FROM figure_generation_operation AS operation
                    WHERE operation.operation_id = clean_redraw_task.operation_id)
                """.trimIndent(),
                emptyList(),
            ),
        )
    }

    private companion object {
        const val CHECK_NO_NEW_DANGLING = "no-new-dangling"

        /**
         * FTS4 外部内容表的影子表。未知普通表一律拒绝，避免"新表没进裁剪清单 → 原样泄露"。
         * `sqlite_%` 内部表与 `android_metadata` 在预检里另行放行。
         */
        val FTS_SHADOW_TABLES = setOf(
            "library_search_fts_content",
            "library_search_fts_segments",
            "library_search_fts_segdir",
            "library_search_fts_docsize",
            "library_search_fts_stat",
        )

        val FULL_ASSET_PINS_SQL = """
            SELECT source_asset_id FROM problem_revision_source_asset
            UNION SELECT source_asset_id FROM problem_draft_source_asset
            UNION SELECT source_asset_id FROM problem_draft
                  WHERE source_asset_id IS NOT NULL
            UNION SELECT source_asset_id FROM tutor_message_source_asset
            UNION SELECT output_asset_id FROM figure_generation_attempt
                  WHERE output_asset_id IS NOT NULL
        """.trimIndent()

        val TRIMMED_ASSET_PINS_SQL = """
            SELECT asset_id FROM smbk_kept_asset
            UNION SELECT output_asset_id FROM figure_generation_attempt
                  WHERE output_asset_id IS NOT NULL
        """.trimIndent()

        /**
         * 生产 `findUnreferencedCanonicalAssets` 的五站点补集（与
         * `PendingCaptureDao.findUnreferencedCanonicalAssets` 同一口径）。裁剪器用它反查
         * "库自己认为被引用的资产"，再与 §9.5 的 pin 集合对拍。
         */
        val FIVE_SITE_ASSET_IDS_SQL = """
            SELECT asset.source_asset_id
            FROM canonical_source_asset AS asset
            WHERE EXISTS (
                    SELECT 1 FROM problem_revision_source_asset AS revision_link
                    WHERE revision_link.source_asset_id = asset.source_asset_id)
               OR EXISTS (
                    SELECT 1 FROM problem_draft_source_asset AS draft_link
                    WHERE draft_link.source_asset_id = asset.source_asset_id)
               OR EXISTS (
                    SELECT 1 FROM problem_draft AS draft
                    WHERE draft.source_asset_id = asset.source_asset_id)
               OR EXISTS (
                    SELECT 1 FROM tutor_message_source_asset AS message_link
                    WHERE message_link.source_asset_id = asset.source_asset_id)
               OR EXISTS (
                    SELECT 1 FROM figure_generation_attempt AS attempt_output
                    WHERE attempt_output.output_asset_id = asset.source_asset_id)
        """.trimIndent()
    }
}
