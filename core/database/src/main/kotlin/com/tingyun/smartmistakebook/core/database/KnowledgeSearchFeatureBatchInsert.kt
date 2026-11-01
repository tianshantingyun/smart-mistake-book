package com.tingyun.smartmistakebook.core.database

import androidx.room3.PooledConnection
import com.tingyun.smartmistakebook.core.database.entity.KnowledgeSearchFeatureEntity

/**
 * 特征行**批量 VALUES** 写入（F6 性能剖析 2026-10-31）。
 *
 * 改前：`ProblemOrganizationDao.insertKnowledgeSearchFeatures(List)` 走 room3 的
 * `EntityInsertAdapter.insert`——一条预编译的 `INSERT OR IGNORE ... VALUES (?,?,?)`
 * 逐行 bind → step → reset。四科首次重建 547k 行就是 547k 次 step（含每次事务/语句
 * 状态机开销）。本文件把同一个事务内的行按 [SEARCH_FEATURE_INSERT_BATCH_ROWS] 组合成
 * 一条多行 `VALUES (...),(...),...` 语句，**一次 step 写入多行**。
 *
 * 等价性（逐条对应改动前语义）：
 * 1. **列与列序完全相同**：`subject, search_feature, knowledge_node_id`（与 Room 生成
 *    的 INSERT 列序一致）；
 * 2. **冲突策略相同**：`INSERT OR IGNORE`——主键 `(subject, search_feature,
 *    knowledge_node_id)` 冲突时忽略该行，与逐行路径逐行忽略等价；
 * 3. **行序相同**：按传入 `features` 的原顺序绑定，批次内与批次间的相对顺序都不变；
 * 4. **事务边界相同**：本函数不自己开事务，由调用方原有的
 *    `database.withWriteTransaction { ... }` 包住（改前 Room 的 `@Insert` 也只是在
 *    这个外层事务里再跑一个同连接事务；外层提交点不变）；
 * 5. **行集相同**：同样的实体 → 同样的三列值，不增不减不改。
 *
 * 为什么批量上限是 300 行：[SEARCH_FEATURE_INSERT_BATCH_ROWS] × 3 = 900 个绑定参数，
 * 低于 SQLite 旧平台的 999 上限（与 `MAX_IN_PARAMETERS = 900` 同一口径）。
 */
internal const val SEARCH_FEATURE_INSERT_BATCH_ROWS = 300

/** 单行三列；批量语句的参数数 = 行数 × 本常量。 */
internal const val SEARCH_FEATURE_INSERT_PARAMETERS_PER_ROW = 3

/**
 * 生成 [rowCount] 行的 `INSERT OR IGNORE` 语句。
 *
 * 只生成 SQL 文本；绑定与执行见 [insertKnowledgeSearchFeaturesBatched]。`rowCount` 的
 * 上界由 [SEARCH_FEATURE_INSERT_BATCH_ROWS] 保证（参数数 < 999）。
 */
internal fun knowledgeSearchFeatureInsertSql(rowCount: Int): String {
    require(rowCount in 1..SEARCH_FEATURE_INSERT_BATCH_ROWS) {
        "rowCount must be in 1..$SEARCH_FEATURE_INSERT_BATCH_ROWS, was $rowCount"
    }
    val values = buildString(rowCount * 8) {
        repeat(rowCount) { index ->
            if (index > 0) append(',')
            append("(?,?,?)")
        }
    }
    return "INSERT OR IGNORE INTO `knowledge_search_feature` " +
        "(`subject`, `search_feature`, `knowledge_node_id`) VALUES $values"
}

/**
 * 在**调用方已有的写事务**里批量写入 [features]。
 *
 * [PooledConnection] 是 room3 事务作用域（`TransactionScope` 实现它），所以
 * `database.withWriteTransaction { insertKnowledgeSearchFeaturesBatched(rows) }`
 * 复用的就是外层事务的写连接，不会另开嵌套事务。空列表是 no-op。
 */
internal suspend fun PooledConnection.insertKnowledgeSearchFeaturesBatched(
    features: List<KnowledgeSearchFeatureEntity>,
) {
    var offset = 0
    while (offset < features.size) {
        val end = minOf(offset + SEARCH_FEATURE_INSERT_BATCH_ROWS, features.size)
        val statementSql = knowledgeSearchFeatureInsertSql(end - offset)
        usePrepared(statementSql) { statement ->
            var parameter = 1
            for (index in offset until end) {
                val feature = features[index]
                statement.bindText(parameter++, feature.subject)
                statement.bindText(parameter++, feature.searchFeature)
                statement.bindText(parameter++, feature.knowledgeNodeId)
            }
            statement.step()
        }
        offset = end
    }
}
