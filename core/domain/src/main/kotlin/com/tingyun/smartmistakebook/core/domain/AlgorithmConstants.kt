package com.tingyun.smartmistakebook.core.domain

/**
 * 内核算法常数的**单一定义处**（内核修复路线图 Wave 0 / W0-4，证据审计 Q5：82 文件散落 `const val`、
 * 两份排程权重表已分叉、`DAY_MILLIS` 至少 4 处重复）。
 *
 * 收进来的判据是"**同一个数写了两遍**"，不是"这个数看起来像个常数"：
 * - 同值不同处：两份权重表（`ReviewPlanner` 与 `ReviewPlannerV2` 曾各写一份，值相同）与
 *   `DAY_MILLIS`（六个文件各写一份）；
 * - 同值不同名：掌握度阈值在 `ClearlyMasteredForSkipPolicy` 的具名常量与
 *   `MasteryDecisionPolicy.DEFAULT` 里各写一份。
 *
 * 这些副本分叉过一次（"权重表两份"就是 Q5 的原始证据），分叉的后果是**静默**的：改一处、
 * 另一处照旧跑，两条路算出的顺序不同而没有任何报错。收敛之后，删掉这里的定义就是编译错
 * （消费方都是引用或别名，不再有第二份字面量）。
 *
 * 边界：**只收"重复"**。单处使用的常数留在原文件（它们没有分叉面）；本表不追求"所有常数都在这里"，
 * 那会把领域语义从它被读懂的上下文里搬走——审计 Q5 的"随 KF-13 扩容"因此是后续波次的事，
 * 不是 Wave 0 的判据（Wave 0 的判据只有：权重表单源 + `DAY_MILLIS` 单源）。
 *
 * **已知未收进来的地方**（本波如实记下，不假装收干净了）：
 * - `core/model` 里的两处（`TutorTasks.MILLIS_PER_DAY`、`LearningState.lastReviewedEpochDay` 的
 *   默认值 `86_400_000L`）：`core:model` 在依赖方向上**早于** `core:domain`，引用不到本表。
 *   要收只能把日长常量下沉到 `core:model`，那是一次独立的分层决定，不在 Wave 0。
 * - `core/database` androidTest 的 `MasteryOverviewInstrumentedTest.DAY_MILLIS`：同理
 *   （`core:database` 只看得到 `core:model`）。测试夹具的字面量不是算法常数的定义处，
 *   不影响"改这里、那边不同步"的失败面；生产侧与其余测试的引用都已指向本表。
 */
object AlgorithmConstants {

    /**
     * 一天的毫秒数（**恒定 24 小时**，不是"本地日"）。
     *
     * 用它算出来的量一律是"物理时长"：FSRS 的 `delta_t`、稳定性间隔、证据时效窗口。
     * 与"本地日"（`LocalDate`/epoch day、`StudyDayContext`）是两套口径，不要互换——
     * 换日边界由日历与 UTC 偏移决定，不由 86_400_000 决定。
     *
     * 需要 `Double` 的消费方请写 `AlgorithmConstants.DAY_MILLIS.toDouble()`（或文件内别名），
     * 不要再写字面量。
     */
    const val DAY_MILLIS: Long = 86_400_000L

    /**
     * 掌握度判定阈值（θ 与证据门槛）。
     *
     * θ=0.85（[LOWER_BOUND]）是台账「3A 前裁决门 · 收口」裁决 13 的已裁值（"θ=0.85 保持"），
     * 本文件只是把它从两处同值声明收敛到一处，**没有改值**——改这里的数就是改掌握度判定，
     * 属于"动算法"，要走版本清单（`docs/research/algorithm-version-ledger.md`）与 `SKIP_POLICY` / `PROJECTOR` 版本纪律。
     */
    object Mastery {
        /** 掌握度**下界**门槛（θ）：点估计不是判据，保守下界才是。 */
        const val LOWER_BOUND: Double = 0.85

        /** 直接证据的**权重总量**门槛（沿用 `ClearlyMasteredForSkipPolicy.EVIDENCE_MASS` 的名字）。 */
        const val MIN_EVIDENCE_MASS: Double = 2.0

        /** 独立正确观察的**题目族**广度门槛。 */
        const val MIN_ITEM_FAMILIES: Int = 2

        /** 独立正确观察的**学习日**广度门槛。 */
        const val MIN_STUDY_DAYS: Int = 2

        /** 证据时效上限（天）：过期证据不再支撑 MASTERED。 */
        const val MAX_EVIDENCE_AGE_DAYS: Int = 45
    }

    /**
     * 排程打分权重（`ReviewPlanner` 与 `ReviewPlannerV2` 共用的一份表）。
     *
     * 这些值目前是**手调值**，其科学化已由台账裁决 17 排在 Wave X（离线模拟对比权重方案后定案）。
     * 本次收敛只消除"同值两份"，**不改任何一个数**，因此计划指纹（含队列与权重派生结果）
     * 逐位不变、不需要 bump `REVIEW_PLANNER`。
     */
    object ReviewScoring {
        /** 难度档边界（FSRS 1..10 域，spec §3.2）。 */
        const val EASY_DIFFICULTY_CEILING: Double = 4.0
        const val MEDIUM_DIFFICULTY_CEILING: Double = 7.0

        /** 弱点判定阈值（掌握度点估计低于它算"弱"）。 */
        const val WEAKNESS_THRESHOLD: Double = 0.35

        /**
         * 考试只能把卡提前到"预测回忆起概率低于它"为止（spec 2.17 的 `R < r*_exam`；
         * 研究 2026-09-09 §5：FSRS 稳定度增益为 `e^{w10(1−R)}−1`）。
         */
        const val EARLY_REVIEW_MAX_RETRIEVABILITY: Double = 0.8

        /** 打分项权重：到期 / 弱点 / 遗忘 / 重复错题 / 回避 / 考试 / 等待。 */
        const val DUE_WEIGHT: Double = 5.0
        const val WEAKNESS_WEIGHT: Double = 3.0
        const val LAPSE_WEIGHT: Double = 1.0
        const val REPEAT_MISTAKE_WEIGHT: Double = 2.0
        const val AVOIDANCE_WEIGHT: Double = 1.0
        const val EXAM_WEIGHT: Double = 2.0
        const val WAITING_WEIGHT: Double = 1.5

        /** 多样性罚：同题族 / 同来源，以及罚分总上限。 */
        const val FAMILY_PENALTY_WEIGHT: Double = 0.3
        const val SOURCE_PENALTY_WEIGHT: Double = 0.2
        const val MAX_DIVERSITY_PENALTY: Double = 1.5

        /** 近期遗忘窗口（毫秒）：`LAPSE_WEIGHT` 只在这个窗口内的遗忘上计分。 */
        const val RECENT_LAPSE_WINDOW_MILLIS: Long = 30L * DAY_MILLIS

        /** 等待压力：宽限天数与饱和坡度（天）。 */
        const val WAITING_GRACE_DAYS: Double = 7.0
        const val WAITING_BONUS_RAMP_DAYS: Double = 83.0
    }
}
