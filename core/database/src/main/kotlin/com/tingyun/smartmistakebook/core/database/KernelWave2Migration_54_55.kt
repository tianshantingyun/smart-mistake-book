package com.tingyun.smartmistakebook.core.database

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.tingyun.smartmistakebook.core.model.StoredReviewRating

/**
 * v54→55：**内核 Wave 2** 的 `review_log.state` 列（roadmap W2-4 / KF-23）。
 *
 * 一个版本只允许一个 `Migration` 对象，所以 v54→55 只有这一处迁移体（与 v53→54 同一约定）。
 *
 * **为什么要回填**（与 v53→54"两件都不回填"的口径不同）：`state` 是有信息量的列——拟合侧
 * 按它选"同日重复 vs 长程复习"的分支，留 DEFAULT 0 会让全部存量行自称 New、把算法状态
 * 全判错。回填口径与**写入侧派生**（`ReviewLogSink.record`）逐条同构：
 * 无前条=New(0)、前条 AGAIN=Relearning(3)、同日（`delta_t_days = 0`）=Learning(1)、
 * 跨日=Review(2)。
 *
 * **推导的已知边界**（fix-plan KF-23 标注为 UNVERIFIED，落地时以本注释与仪器化用例为准）：
 * 存量行的"同日"判定只能借 `delta_t_days`（当时的日界是本地 00:00，W2-4 起改为 04:00），
 * 因此 00:00–04:00 之间的历史复习在新口径下可能偏一天——这是口径变更本身的一部分，
 * 已由本波 `PROJECTOR` bump 触发的全量重放把投影侧一并重算，`review_log` 只作训练数据、
 * 不参与投影。
 */
internal val KERNEL_WAVE2_MIGRATION_54_55 = object : Migration(54, 55) {
    override suspend fun migrate(connection: SQLiteConnection) {
        addReviewLogState(connection)
    }
}

/** 前条 (learner, card) 的 rating；无前条返回 NULL。 */
private const val PREVIOUS_RATING_SUBQUERY = """
    (SELECT prev.`rating` FROM `review_log` AS prev
     WHERE prev.`learner_id` = `review_log`.`learner_id`
       AND prev.`card_id` = `review_log`.`card_id`
       AND (
           prev.`reviewed_at_utc` < `review_log`.`reviewed_at_utc`
           OR (
               prev.`reviewed_at_utc` = `review_log`.`reviewed_at_utc`
               AND prev.`review_log_id` < `review_log`.`review_log_id`
           )
       )
     ORDER BY prev.`reviewed_at_utc` DESC, prev.`review_log_id` DESC
     LIMIT 1)
"""

/**
 * `review_log.rating` 的 AGAIN 落库值（P9）：值取自 `core:model` 的唯一基址枚举。
 *
 * **必须是 `val` 不是 `const val`**——取枚举实例属性不是编译期常量（Kotlin 直接编译错，
 * 探针实测 2026-10-01）；插值在类初始化时完成，值仍为 1，
 * `KernelWave2SchemaContractTest` 的 `"= 1 THEN 3"` 字符串断言原样通过。
 */
private val AGAIN_STORED = StoredReviewRating.AGAIN.value

internal val REVIEW_LOG_STATE_DDL_54_55: List<String> = listOf(
    "ALTER TABLE `review_log` ADD COLUMN `state` INTEGER NOT NULL DEFAULT 0",
    """
    UPDATE `review_log` SET `state` = CASE
        WHEN $PREVIOUS_RATING_SUBQUERY = $AGAIN_STORED THEN 3
        WHEN $PREVIOUS_RATING_SUBQUERY IS NULL THEN 0
        WHEN `review_log`.`delta_t_days` = 0 THEN 1
        ELSE 2
    END
    """.trimIndent(),
)

private fun addReviewLogState(connection: SQLiteConnection) {
    REVIEW_LOG_STATE_DDL_54_55.forEach(connection::execSQL)
}
