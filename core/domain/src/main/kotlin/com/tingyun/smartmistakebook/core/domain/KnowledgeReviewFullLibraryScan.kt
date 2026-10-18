package com.tingyun.smartmistakebook.core.domain

import com.tingyun.smartmistakebook.core.model.KnowledgeMasteryState

/**
 * 3E 全库薄弱点扫描（第二档）的**每次排计划名额上限**：一次排计划最多从第二档取 10 个知识点。
 *
 * **口径（KD-46）**：这是「每次排计划 ≤10」，**不是严格的每日累计总量**——知识点队列是
 * 会话内现排、不落库（见下），同日多次进入（或答题后重算）会重新选候选，当日累计可以超过 10。
 * 2026-10-16 裁定的「每日最多补捞 10 个」按这个近似口径落地。若要严格每日总量，需要新增
 * 「当日已扫描集合」的持久面；本批按裁定接受近似，只把口径写实（low，登记性）。
 *
 * 出处 = **本轮裁定**（`docs/research/2026-10-11-new-stages-spec.md` §3E 的「关键决策」
 * 是待裁占位值；最终值由需求方 2026-10-16 裁定为「第二档 = 每日 10 个知识点、时间预算沿用
 * 20 分钟」，见 `.zcode/plans/plan-2026-10-16-batch-3e-6c-aud09-t9.md` §0 ④ 与 T5 修法）。
 * 需求侧对应 F17 维护配额（"同时承担 F7 扫描量的封顶"，`docs/requirements-fused-final-2026-10-11.md`）
 * 与主线 3E 行「每日捞回量由 F8 预算 + F17 配额封顶」。
 *
 * **双封顶**：名额（本常量）与时间（`StudyReviewPlannerService.reviewTimeBudgetSeconds`，
 * 默认 20 分钟）各封一道。名额只封**第二档的增量**，今日错题队列那一档不受它限制——
 * "全库扫描，不受今日队列限制"是范围定稿的原话，反过来也成立：扫描也不该把今日队列挤掉。
 *
 * 值本身是起步占位量级（Wave X 校准的对象），不是标定值：改它会改变"每次排计划最多捞回几个
 * 全库薄弱点"，因此属于产品裁定面，不许在实现里顺手调整。
 *
 * **版本**：本档不落库、不进任何计划指纹（知识点队列是会话内现排、直接返回 UI 的对象，
 * `KnowledgeReviewSessionPlan` 全仓无持久化落点）——沿用 `KnowledgeNodeScorer` 的同一口径
 * （台账 `docs/research/algorithm-version-ledger.md` §3.12：知识点队列非持久化 ⇒ 零 bump）。
 */
const val FULL_LIBRARY_SCAN_DAILY_CAP: Int = 10

/**
 * 3E 全库扫描的**第二档候选**：把一个「全库候选事实」装成可打分候选，或者判定它
 * **不在扫描范围**（返回 null）。
 *
 * 两道范围条件是范围定稿的逐条落地（spec dual-review-entry §3.2「今日队列 + 全库第二档」）：
 * - [boundPracticeUnitIds] 为空 → 无绑定题的知识点没有时间信号，其"是否薄弱"无从判断
 *   （需求定稿 R5 已注明的残留边界：无绑定题的知识点不在本阶段范围）。把它排进队列等于让
 *   "未知"冒充"薄弱"，还会让掌握度 EMA 单独决定排序。
 * - [materialGroupId] 为空/空白 → 该点无法出题：KNOWLEDGE_QUIZ 以讲解材料 `boundaryMarkdown`
 *   为防臆造锚（spec §3.3），排进会话只会卡在"这道题没有材料"的取题失败上。
 *
 * 消灭的失败：扫描范围若只按"活跃知识点"取，会把上述两类节点排进会话——一个没有时间信号、
 * 一个出不了题；两者都在学生会话里表现为死点，而"排了却出不了题"正是 §3.3 当初加
 * quiz-able 过滤要关掉的那个失败。
 *
 * 打分口径与今日档同源：调用方传入同一份 [state] 与 [recallRisk]（绑定题最小检索概率，
 * 见 [knowledgeRecallRiskByNode]），估计耗时取同一个中档基线
 * （`LogDurationModel.TIER_BASELINE_MEDIUM_SECONDS`，与今日档逐字一致）。
 */
fun fullLibraryScanCandidate(
    knowledgeNodeId: String,
    /** 科目：扫描档用 `knowledge_node.subject` 原值（派发侧就按它取材料）。 */
    subjectId: String,
    /** 该节点的可出题材料组（多份材料取 material_id 字典序最小者，同今日档口径）。 */
    materialGroupId: String?,
    /** 该节点的绑定题（practice unit id）**全量**。 */
    boundPracticeUnitIds: Collection<String>,
    state: KnowledgeMasteryState?,
    recallRisk: Double?,
): KnowledgeReviewCandidate? {
    if (knowledgeNodeId.isBlank() || subjectId.isBlank()) return null
    if (materialGroupId.isNullOrBlank()) return null
    if (boundPracticeUnitIds.isEmpty()) return null
    return KnowledgeReviewCandidate(
        knowledgeNodeId = knowledgeNodeId,
        subjectId = subjectId,
        materialGroupId = materialGroupId,
        state = state,
        estimatedDurationSeconds = LogDurationModel.TIER_BASELINE_MEDIUM_SECONDS,
        recallRisk = recallRisk,
    )
}
