package com.tingyun.smartmistakebook.core.domain

/** Single source of truth for persisted learning-algorithm version identities. */
object LearningCoreVersions {
    const val EVIDENCE = "evidence-v4"

    /**
     * v5 → v6（2026-09-11）：`projectChatEvidence` 开始写 `lastEvidenceAt` /
     * `lastEvidenceDirection`（审计 AUDIT-ALGORITHM-2026-09-09 §3.5）。改的是
     * **同一条账本事件的投影结果**，增量投影不会重算已消费的事件，因此必须靠
     * 版本不匹配触发全量重放（`StudyProjectionDrainer` 的
     * `requiresReplay → commitFullReplay`），否则已有库里的 KC 仍旧带着旧的
     * null 时钟，修复对它们静默无效。
     *
     * v6 → v7（2026-09-11）：跨日判定的 delta_t 改由
     * `lastReviewedAtEpochMillis` + 事件的 UTC 偏移现算，不再读
     * `ProblemMemoryState.lastReviewedEpochDay`（审计 §3.7——`projectTutorAnswerExposure`
     * 曾让该字段落入 UTC 日序默认值，使同一本地日的复习被误判为跨日）。这是一次
     * **数值口径变更**：受影响的卡（曝光态之后的复习、以及 v42 迁移按 UTC 回填过
     * 日序的存量行）必须重算，否则新旧混用。
     *
     * v7 → v8（2026-10-01，Wave 2）：两处**数值口径变更**合并为一次 bump——
     * ①W2-1/KF-01 在线投影的 R 与间隔反函数接入个性化 w20（此前隐式吃默认 decay，
     * 有优化参数的学习者其在线口径与拟合口径不一致）；②W2-4/KF-25 学习日界从本地
     * 00:00 移到 04:00（`StudyDayMath.DAY_START_HOUR`，官方 day_start）。两者都改变
     * 投影输出：旧快照必须全量重放（W0 的 `projection_archive` 会先归档旧投影），
     * 否则新旧口径混用。`REVIEW_COMPOSITE` 同步变化 → 当日计划按新口径重排。
     */
    const val PROJECTOR = "projector-v8"
    const val FORGETTING_CURVE = "curve-v3"
    const val SKIP_POLICY = "skip-v3"
    const val ATTRIBUTION = "attribution-v2"
    const val LEDGER = "ledger-v2"
    const val PREDICTION_INTERVAL = "prediction-interval-v1"
    const val REVIEW_PLANNER = "review-planner-v6"
    const val SELECTOR = "selector-v6"

    const val PROJECTION_COMPOSITE =
        "learning-core-v8($PROJECTOR,$EVIDENCE,$FORGETTING_CURVE,$SKIP_POLICY,$ATTRIBUTION,$LEDGER)"
    const val REVIEW_COMPOSITE =
        "learning-core-v8($REVIEW_PLANNER,$PROJECTOR,$FORGETTING_CURVE,$SKIP_POLICY,$LEDGER)"
    const val SELECTOR_COMPOSITE =
        "learning-core-v8($SELECTOR,$PROJECTOR,$SKIP_POLICY,$PREDICTION_INTERVAL,$LEDGER)"
}
