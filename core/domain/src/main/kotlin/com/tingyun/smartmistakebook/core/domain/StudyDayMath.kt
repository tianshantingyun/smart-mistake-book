package com.tingyun.smartmistakebook.core.domain

/**
 * 本地日序（epoch day）的**单一定义处**（内核修复路线图 Wave 1 / W1-6 · P1）。
 *
 * 审计 `docs/research/2026-09-28-kernel-scale-precision-audit.md` §2 的 P1 证据：同一个"学习者本地日"
 * 在三条路上各推一遍——写路径（`StudyWriteContext`）用 zone 规则算日历日；投影（`LearningProjector`）
 * 用"时间戳 + 记录下来的 UTC 偏移"做整数除法；`ReviewLogSink` 又用**当前** zone 重算上一条复习的日。
 * 三份推导在时区变更或分钟对齐的边界上会给出不同答案，而 delta_t（FSRS 的 `delta_t`、日序去重、
 * 跨日连击）正是从它们里出来的——分叉是静默的：改一处、另一处照旧跑。
 *
 * 统一口径（roadmap W1-6 的原文）：**账本存原始时间戳 + 偏移，日序一律由"时间戳 + 偏移"派生**。
 * 于是：
 * - 写路径把事件发生时刻的偏移一起盖进账本（`attempt_event.study_day_utc_offset_minutes` 等），
 *   日序用本表算——它落库的值与投影重放时算出来的值**由构造保证相同**（同一函数、同一输入）；
 * - 投影重放读账本里的偏移，用本表算（`LearningProjector` 过去自己写了一份同样的算术，现已指向这里）；
 * - `ReviewLogSink` 的 delta_t 也用本表算，且两个日序取**同一个偏移**（本次事件的偏移）。
 *
 * **如实登记与参照实现的差异**（2026-10-01 审查订正）：py-fsrs 的 `delta_t` 并不是日历日差，
 * 而是 `review - last_review` 两个 aware datetime 相减后对**绝对时长**取整天数——"两端同偏移"
 * 不是 py-fsrs 的口径，本表的真实依据是**与投影器既有算术单源化 + 账本可复算**（重放只读
 * 时间戳与偏移，十年后结果逐位相同）。已知的边界行为：跨 DST 切换的复习对，新口径与
 * 此前按 zone 规则落库的值可在本地午夜 ±1 小时的角落差 1 天——这正是三源统一要消除的分叉。
 *
 * 为什么是整数除法而不是 `LocalDate.toEpochDay()`：日序在重放期必须**可复算**——账本只存
 * 时间戳与偏移，不存 zone 数据库版本、也不存当年的 DST 规则；"时间戳 + 偏移 → 日序"是纯算术，
 * 十年后重放同一条账本得到同一个日序。zone 规则只用在**写入那一刻**（把当时的偏移取出来）。
 */
object StudyDayMath {

    /** 分钟 → 毫秒。 */
    private const val MILLIS_PER_MINUTE = 60_000L

    /**
     * 本地日序：`floorDiv(时间戳 + 偏移, 一天)`。
     *
     * 用 `floorDiv` 而不是 `/`：纪元前的负时间戳也要向下取整到"那一天"，否则 1970 年前的时间戳
     * 会在日界上偏一天（`Long` 除法向零取整）。
     *
     * 日历日**差**（delta_t 的分子）不在这里：`ReviewLogSink` 用"盖章日 − 本函数(上一时间戳)"
     * 现算并 `coerceAtLeast(0)`——两个端点同用本次事件的偏移（理由见文件头），生产只有这一处
     * 消费方，不设第二个入口。
     */
    fun localEpochDayOf(epochMillis: Long, utcOffsetMinutes: Int): Long =
        Math.floorDiv(epochMillis + utcOffsetMinutes.toLong() * MILLIS_PER_MINUTE, AlgorithmConstants.DAY_MILLIS)
}
