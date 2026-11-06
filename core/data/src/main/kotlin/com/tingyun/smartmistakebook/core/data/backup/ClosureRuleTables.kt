package com.tingyun.smartmistakebook.core.data.backup

import java.security.MessageDigest

/**
 * 读侧按**声明版本**选择的不可变闭包规则表（WS1 item 5 / R2 §3）。
 *
 * **消灭的具体失败**：`closureVersion` 被当成"必须等于本机常量"的硬等值比较，一份
 * closure 4（schema 72）的合法旧包在第一道门就被拒，即使它的 91 表行数、摘要与裁剪数
 * 都自洽——旧包永久不可恢复。规则表把"用哪张表解释 rowCounts/digest"变成数据，
 * 读侧按包自称的版本校验，绝不按当前 v6 规则重算。
 *
 * 兼容历史包：`closure ∈ {4, 5, 6}`；4、5 按冻结键集合区分主分支与旧 UI 分支。`≤3` 没有不可变规则表（无法按当时算法重算摘要），
 * `≥7` 规则未知——两者都明确拒绝并提示升级/重导出（R2 §3.4）。
 *
 * **不复制上游代码**：只冻结校验所需的三件事（键集合、裁剪上限、摘要前缀）。v4 数值
 * 逐字取自 `git show 70509247:core/data/.../SmbkSelectionPlan.kt`（closure 4 时点，
 * R2 §1.2 实读），不是估算。
 */
internal data class SmbkClosureRule(
    val closureVersion: Int,
    val digestPrefix: String,
    val rowCountKeys: Set<String>,
    val trimOrderSize: Int,
    /** 相对上一版**新增**的表：允许在恢复时按 0 行补齐（Room 迁移只建空表）。 */
    val addedTables: Set<String>,
    /** 相对上一版**删除**的表：声明与实测都必须为 0，非 0 即拒绝整包（不静默丢行）。 */
    val droppedTables: Set<String>,
) {
    init {
        require(closureVersion >= 1) { "Closure version must be positive" }
        require(digestPrefix.isNotBlank()) { "Digest prefix must not be blank" }
        require(trimOrderSize >= 0) { "Trim order size must not be negative" }
        require(addedTables.intersect(rowCountKeys).isEmpty()) {
            "Added tables must not already be part of the declared key space"
        }
        require(droppedTables.intersect(addedTables).isEmpty()) {
            "A table cannot be both added and dropped in one projection"
        }
        require(rowCountKeys.containsAll(droppedTables)) {
            "Dropped tables must be declared by the older rule table"
        }
    }
}

internal object ClosureRuleTables {

    private val errorCauseTables: Set<String> = linkedSetOf(
        "error_cause_observation",
        "error_cause_binding",
    )

    /** closure 4 的恒裁派生表；closure 5 随 KB 32B 回滚删除。 */
    private val materialFeatureTable: Set<String> = setOf("knowledge_search_material_feature")

    /** 现行规则（closure 5 / schema 73）：唯一可写版本。 */
    val v6 = SmbkClosureRule(
        closureVersion = SmbkSelectionPlan.CLOSURE_VERSION,
        digestPrefix = SmbkSelectionPlan.CLOSURE_DIGEST_PREFIX,
        rowCountKeys = SmbkSelectionPlan.rowCountKeys,
        trimOrderSize = SmbkSelectionPlan.trimOrder.size,
        addedTables = emptySet(),
        droppedTables = emptySet(),
    )

    private val interiorTables = setOf("mistake_edit_job", "detailed_solution_manifest", "detailed_solution", "mistake_export_group", "paper_review", "paper_review_question", "paper_review_photo")
    val v5 = SmbkClosureRule(5, "smbk-selection-v5", SmbkSelectionPlan.rowCountKeys - interiorTables,
        84, interiorTables, emptySet())
    private val v5Ui = SmbkClosureRule(5, "smbk-selection-v5", SmbkSelectionPlan.rowCountKeys - errorCauseTables,
        89, errorCauseTables, emptySet())

    /**
     * closure 4（schema 72）的冻结快照：91 键 / 83 裁剪项 / `smbk-selection-v4`。
     * 相对 v4，v5 增 `error_cause_observation`、`error_cause_binding`，删
     * `knowledge_search_material_feature`（R2 §1.2）。
     */
    val v4 = SmbkClosureRule(
        closureVersion = 4,
        digestPrefix = "smbk-selection-v4",
        rowCountKeys = SmbkSelectionPlan.rowCountKeys - errorCauseTables - interiorTables + materialFeatureTable,
        trimOrderSize = 83,
        addedTables = errorCauseTables + interiorTables,
        droppedTables = materialFeatureTable,
    )

    private val paperTables = setOf("paper_review", "paper_review_question", "paper_review_photo")
    private val v4Ui = SmbkClosureRule(4, "smbk-selection-v4", SmbkSelectionPlan.rowCountKeys - errorCauseTables - paperTables,
        86, errorCauseTables + paperTables, emptySet())

    init {
        check(v4.rowCountKeys.size == 91) { "closure 4 must declare 91 row-count keys" }
        check(v6.rowCountKeys.size == SmbkSelectionPlan.allTables.size) {
            "closure 6 row-count keys must match the current table list"
        }
        check(v4.droppedTables.all { it !in v6.rowCountKeys }) {
            "Dropped tables must be absent from the current rule table"
        }
        check(v4.addedTables.all { it in v6.rowCountKeys }) {
            "Added tables must be present in the current rule table"
        }
    }

    /** 声明版本 → 规则表；不认识返回 null（调用方给出可读拒绝）。 */
    fun of(closureVersion: Int, keys: Set<String>? = null): SmbkClosureRule? = when (closureVersion) {
        v4.closureVersion -> if (keys == v4Ui.rowCountKeys) v4Ui else v4
        5 -> if (keys == v5Ui.rowCountKeys) v5Ui else v5
        v6.closureVersion -> v6
        else -> null
    }

    /**
     * 按**声明规则**重算摘要（§9.7 算法的参数化版本；prefix 与 closureVersion 来自规则）。
     * `rowCounts` 必须恰好覆盖该规则的键集合，漏键/多键抛出（避免摘要不可比）。
     */
    fun digest(
        rule: SmbkClosureRule,
        categories: Collection<SmbkSelectionCategory>,
        assetPolicy: String,
        rowCounts: Map<String, Long>,
    ): String {
        require(rowCounts.keys == rule.rowCountKeys) {
            val missing = (rule.rowCountKeys - rowCounts.keys).sorted()
            val extra = (rowCounts.keys - rule.rowCountKeys).sorted()
            "rowCounts 必须覆盖 ${rule.rowCountKeys.size} 张表（closure ${rule.closureVersion}）：" +
                "missing=$missing extra=$extra"
        }
        val categoryNames = categories.map { it.name }.distinct().sorted()
        val payload = buildString {
            append(rule.digestPrefix).append('\n')
            append("closureVersion=").append(rule.closureVersion).append('\n')
            append("categories=").append(categoryNames.joinToString(",")).append('\n')
            append("assetPolicy=").append(assetPolicy).append('\n')
            rowCounts.keys.sorted().forEach { table ->
                append(table).append('=').append(rowCounts.getValue(table)).append('\n')
            }
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(payload.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }
}

/**
 * 一份旧规则包在读侧得到的**投影**（R2 §4.3）：声明版本、增删表集合、原始声明行数，
 * 以及恢复侧对账用的归一化行数（删掉的表移除、新增的表补 0）。
 *
 * 只用于恢复侧核对，不回写 manifest、不替换 `closureDigest`。
 */
internal data class SmbkSelectionProjection(
    val declaredClosureVersion: Int,
    val addedTables: Set<String>,
    val droppedTables: Set<String>,
    val declaredRowCounts: Map<String, Long>,
    val normalizedRowCounts: Map<String, Long>,
) {
    init {
        require(declaredClosureVersion >= 1) { "Declared closure version must be positive" }
        require(declaredRowCounts.keys.containsAll(droppedTables)) {
            "Declared row counts must contain every dropped table"
        }
        require(normalizedRowCounts.keys.containsAll(addedTables)) {
            "Normalized row counts must fill every added table"
        }
        require(addedTables.none { it in normalizedRowCounts && normalizedRowCounts.getValue(it) != 0L }) {
            "Added tables must be normalized to zero rows"
        }
    }

    companion object {
        /** 现行版本：声明即当前，无投影。 */
        fun identity(rowCounts: Map<String, Long>, closureVersion: Int): SmbkSelectionProjection =
            SmbkSelectionProjection(
                declaredClosureVersion = closureVersion,
                addedTables = emptySet(),
                droppedTables = emptySet(),
                declaredRowCounts = rowCounts,
                normalizedRowCounts = rowCounts,
            )
    }
}
