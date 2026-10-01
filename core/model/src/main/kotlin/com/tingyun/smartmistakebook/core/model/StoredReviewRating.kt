package com.tingyun.smartmistakebook.core.model

/**
 * `review_log.rating` 的唯一 1-based 基址（DB 契约锚点；审计 P9，2026-09-30）。
 *
 * 为什么在 `core:model`：SQL/存储层（`core:database`）与算法层（`core:domain`）都要引用同一处基址，
 * 而前两者的依赖方向只到 `core:model`——枚举放这里，SQL 模板（`KernelWave2Migration_54_55`）与
 * 记录校验（`StudyDatabaseRecords`）才能与算法侧（`FsrsRating.storedRating`）同源。
 *
 * 消灭的失败（审计 P9 原始证据）：`1` / `2` 曾在迁移 SQL、`ReviewLogSink`、`AttentionSignal`
 * 各写一遍——改枚举序或改语义时没有任何机械联系把它们一起变红，静默漂移。
 *
 * 与算法侧的口径配对由 `core:domain` 的契约测试钉死：
 * `FsrsRating.entries.map { it.storedRating } == StoredReviewRating.entries.map { it.value }`。
 */
enum class StoredReviewRating(val value: Int) {
    AGAIN(1),
    HARD(2),
    GOOD(3),
    EASY(4),
    ;

    companion object {
        /** 合法落库值下界（AGAIN = 1）。 */
        val MIN: Int = AGAIN.value

        /** 合法落库值上界（EASY = 4）。 */
        val MAX: Int = EASY.value
    }
}
