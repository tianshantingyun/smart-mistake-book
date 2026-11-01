package com.tingyun.smartmistakebook.core.data.backup

import android.database.sqlite.SQLiteDatabase
import java.io.File

/**
 * 恢复侧 S3 软引用重查（6C-2 设计 §3.2-S3，口径按 §9.4.3 的收窄；批次 19 T2-4）。
 *
 * **为什么恢复侧只重查这些**（§9.4.3）：恢复侧拿不到 `D_pre`（成包前那份悬空集合），
 * 附录 C 里 `check=no-new-dangling` 的 15 条软引用无法判定"新增"——历史悬空（如删过
 * 会话后 `model_task.subject_id` 的既存悬空）是合法数据，按 0 行拒绝会把本机裁剪器自己
 * 产出的合法包也拒掉（那正是成包侧 I4 从 v1 的 0 行口径改成 no-new-dangling 的原因）。
 *
 * 因此恢复侧只重跑**结构上可完全重算**的部分（§9.4.3 明列：闭包、硬 FK、§9.5 figure
 * 行规则；硬 FK 已在 Phase 2b 的 `foreign_key_check`、类别闭包已在 Phase 2c.1）：
 * 1. `model_task` / `model_task_operation` 的 `task_kind` 必须都在
 *    [SmbkSelectionPlan.modelTaskKindCategory] 内（fail-closed，与裁剪器同口径）；
 * 2. 未选类别的 `task_kind` 不得仍留行（split 表按行筛选，Phase 2c.1 的"未选类别 0 行"
 *    只盖类别表、盖不到 split 表——「split 表 kind 级谎报」此前只在成包侧被发现）；
 * 3. `figure_generation_operation.source_kind` 必须都在
 *    [SmbkSelectionPlan.figureOperationSourceKindCategory] 内（fail-closed）；
 * 4. §9.5 的 O_keep/A_keep 行级规则按包内库重算：`source_kind` 类别未选中的 operation
 *    只有两种合法存活方式——产物仍被存活根引用（O_ref）或被存活 `clean_redraw_task`
 *    钉住（O_seed 第三支）；不满足的 operation/attempt 行就是第三方重裁/拼接包漏删的行。
 *
 * **方向性**：只断言"该删的行不在"（重裁包多带数据/谎报），不断言"悬空目标必须存在"——
 * 后者在历史悬空上是误拒（见上）。"该留的行被第三方删掉"的残余风险由 §9.10 登记，
 * 不在这里假装解决。
 *
 * mask=null（v1/v2 旧包）与全选（15）不重查：旧包没有选择语义，全选的裁剪器只裁派生
 * 索引、split 表一行不动（与裁剪器 `assertNoUnknownTaskKinds` 对全选的早退同口径）。
 */
internal object RestoreSoftReferenceRecheck {

    /**
     * staged 库 split 家族的观测值。抽取（[recheck]）与判定（[violationsFor]）分开：
     * 判定是纯函数，JVM 单测不需要 SQLite 就能钉住每条规则的语义。
     */
    internal data class StagedSplitObservations(
        /** `model_task.task_kind` → 行主键样例（task_id）。 */
        val modelTaskKinds: Map<String, List<String>>,
        /** `model_task_operation.task_kind` → 行主键样例（operation_fingerprint）。 */
        val modelTaskOperationKinds: Map<String, List<String>>,
        /** `figure_generation_operation.source_kind` → operation_id 样例。 */
        val figureOperationSourceKinds: Map<String, List<String>>,
        /** R：按选择存活下来的根引用（§9.5-1，四张根表按类别）。 */
        val keptAssetIds: Set<String>,
        /** 存活 `clean_redraw_task.operation_id` 钉住的 operation（O_seed 第三支）。 */
        val cleanRedrawPinnedOperationIds: Set<String>,
        /** `figure_generation_attempt` 行（operation 归属 + 产物）。 */
        val attempts: List<StagedAttempt>,
    ) {
        internal data class StagedAttempt(
            val attemptId: String,
            val operationId: String,
            val outputAssetId: String?,
        )
    }

    /** 违规清空 = 通过；每条违规都是给人读的（进入 [BackupRestoreException] 的拒因）。 */
    fun violationsFor(mask: Int?, observed: StagedSplitObservations): List<String> {
        if (mask == null || mask == SmbkSelectionPlan.FULL_MASK) return emptyList()
        val selected = SmbkSelectionPlan.categoriesOf(mask)
        val problems = mutableListOf<String>()

        listOf(
            "model_task" to observed.modelTaskKinds,
            "model_task_operation" to observed.modelTaskOperationKinds,
        ).forEach { (table, kinds) ->
            val unknown = kinds.keys
                .filterNot { it in SmbkSelectionPlan.modelTaskKindCategory }
                .sorted()
            if (unknown.isNotEmpty()) {
                problems += "$table 含映射外 task_kind=${unknown.joinToString("/")}，" +
                    "拒绝猜测其类别（样例 ${sample(kinds, unknown.first())}）"
            }
            val deletedKinds = SmbkSelectionPlan.deletedModelTaskKinds(mask)
            val leftover = kinds.filterKeys { it in deletedKinds }
            if (leftover.isNotEmpty()) {
                val kind = leftover.keys.sorted().first()
                problems += "$table 留有未选类别的 task_kind" +
                    "（${leftover.keys.sorted().joinToString("/")}，样例 ${leftover.getValue(kind).first()}）"
            }
        }

        val unknownSourceKinds = observed.figureOperationSourceKinds.keys
            .filterNot { it in SmbkSelectionPlan.figureOperationSourceKindCategory }
            .sorted()
        if (unknownSourceKinds.isNotEmpty()) {
            problems += "figure_generation_operation 含映射外 source_kind=" +
                "${unknownSourceKinds.joinToString("/")}，拒绝猜测其类别" +
                "（样例 ${sample(observed.figureOperationSourceKinds, unknownSourceKinds.first())}）"
        }

        // §9.5 O_keep = O_seed（source_kind 类别选中的全部）∪ clean_redraw 钉住 ∪ O_ref
        // （产物 ∈ R 的 attempt 所属 operation）。A_keep = O_keep 的 attempt ∪ 产物 ∈ R 的 attempt。
        val keptOperations = buildSet {
            observed.figureOperationSourceKinds.forEach { (kind, operationIds) ->
                val category = SmbkSelectionPlan.figureOperationSourceKindCategory[kind]
                if (category != null && category in selected) addAll(operationIds)
            }
            addAll(observed.cleanRedrawPinnedOperationIds)
            observed.attempts.forEach { attempt ->
                if (attempt.outputAssetId != null && attempt.outputAssetId in observed.keptAssetIds) {
                    add(attempt.operationId)
                }
            }
        }
        val leftoverOperations = observed.figureOperationSourceKinds.values.flatten()
            .filterNot { it in keptOperations }
            .sorted()
        if (leftoverOperations.isNotEmpty()) {
            problems += "figure_generation_operation 留有未选类别且无存活产物/钉住的行" +
                "（样例 ${leftoverOperations.take(3).joinToString("、")}）"
        }
        val leftoverAttempts = observed.attempts
            .filter { attempt ->
                attempt.operationId !in keptOperations &&
                    (attempt.outputAssetId == null || attempt.outputAssetId !in observed.keptAssetIds)
            }
            .map { it.attemptId }
            .sorted()
        if (leftoverAttempts.isNotEmpty()) {
            problems += "figure_generation_attempt 留有未保留 operation 且产物不在根引用内的行" +
                "（样例 ${leftoverAttempts.take(3).joinToString("、")}）"
        }
        return problems
    }

    /**
     * 从 staged 库抽取观测值并判定。表不存在按无行处理（旧 schema 的包走不到这里：
     * v3 selection 要求 closureVersion=2/schema 69，但 fail-closed 不依赖这一假设）。
     * 查询失败（库打不开/读不出）抛 [BackupRestoreException]——恢复被拒绝而不是放行。
     */
    fun recheck(stagedDatabaseFile: File, mask: Int?): List<String> {
        if (mask == null || mask == SmbkSelectionPlan.FULL_MASK) return emptyList()
        val path = stagedDatabaseFile.absolutePath
        val observed = try {
            SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY).use { database ->
                val selected = SmbkSelectionPlan.categoriesOf(mask)
                StagedSplitObservations(
                    modelTaskKinds = if (tableExists(database, "model_task")) {
                        queryPairs(database, "SELECT task_kind, task_id FROM model_task")
                    } else {
                        emptyMap()
                    },
                    modelTaskOperationKinds = if (tableExists(database, "model_task_operation")) {
                        queryPairs(
                            database,
                            "SELECT task_kind, operation_fingerprint FROM model_task_operation",
                        )
                    } else {
                        emptyMap()
                    },
                    figureOperationSourceKinds = if (tableExists(database, "figure_generation_operation")) {
                        queryPairs(
                            database,
                            "SELECT source_kind, operation_id FROM figure_generation_operation",
                        )
                    } else {
                        emptyMap()
                    },
                    keptAssetIds = SmbkSelectionPlan.assetRoots
                        .asSequence()
                        .filter { it.category in selected }
                        .flatMap { root -> queryRootAssets(database, root).asSequence() }
                        .toSet(),
                    cleanRedrawPinnedOperationIds = if (tableExists(database, "clean_redraw_task")) {
                        queryColumn(
                            database,
                            "SELECT DISTINCT operation_id FROM clean_redraw_task " +
                                "WHERE operation_id IS NOT NULL",
                        ).toSet()
                    } else {
                        emptySet()
                    },
                    attempts = if (tableExists(database, "figure_generation_attempt")) {
                        queryAttempts(database)
                    } else {
                        emptyList()
                    },
                )
            }
        } catch (failure: BackupRestoreException) {
            throw failure
        } catch (failure: Exception) {
            throw BackupRestoreException(
                "备份内容选择的软引用重查无法读取包内库，恢复被拒绝：${failure.message.orEmpty()}",
                failure,
            )
        } finally {
            listOf(File("$path-wal"), File("$path-shm")).forEach { sidecar -> sidecar.delete() }
        }
        return violationsFor(mask, observed)
    }

    private fun sample(kinds: Map<String, List<String>>, kind: String): String =
        kinds.getValue(kind).firstOrNull() ?: kind

    private fun tableExists(database: SQLiteDatabase, table: String): Boolean =
        database.rawQuery(
            "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ? LIMIT 1",
            arrayOf(table),
        ).use { cursor -> cursor.moveToFirst() }

    /** 两列字符串查询 → 第一列分组、第二列为样例列表（只取前 5 条，够进拒因）。 */
    private fun queryPairs(
        database: SQLiteDatabase,
        sql: String,
    ): Map<String, List<String>> {
        database.rawQuery(sql, null).use { cursor ->
            val grouped = LinkedHashMap<String, MutableList<String>>()
            while (cursor.moveToNext()) {
                val key = cursor.getString(0) ?: continue
                val value = cursor.getString(1) ?: continue
                grouped.getOrPut(key) { mutableListOf() }.add(value)
            }
            return grouped.mapValues { (_, values) -> values.take(5) }
        }
    }

    private fun queryColumn(database: SQLiteDatabase, sql: String): List<String> =
        database.rawQuery(sql, null).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    cursor.getString(0)?.let { add(it) }
                }
            }
        }

    /** R：一张根引用表指向的资产（表不存在按空——旧 schema 的包）。 */
    private fun queryRootAssets(
        database: SQLiteDatabase,
        root: SmbkAssetRoot,
    ): List<String> {
        if (!tableExists(database, root.table)) return emptyList()
        return queryColumn(
            database,
            "SELECT ${root.column} FROM ${root.table} WHERE ${root.column} IS NOT NULL",
        )
    }

    private fun queryAttempts(
        database: SQLiteDatabase,
    ): List<StagedSplitObservations.StagedAttempt> =
        database.rawQuery(
            "SELECT attempt_id, operation_id, output_asset_id FROM figure_generation_attempt",
            null,
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val attemptId = cursor.getString(0) ?: continue
                    val operationId = cursor.getString(1) ?: continue
                    add(
                        StagedSplitObservations.StagedAttempt(
                            attemptId = attemptId,
                            operationId = operationId,
                            outputAssetId = cursor.getString(2),
                        ),
                    )
                }
            }
        }
}
