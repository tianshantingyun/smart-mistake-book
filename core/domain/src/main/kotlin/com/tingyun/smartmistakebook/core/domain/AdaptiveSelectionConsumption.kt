package com.tingyun.smartmistakebook.core.domain

/**
 * G5-A 选择决策的**消费出口**（KD-67 产品面半边）。
 *
 * 选择器已经能确定性地说"这些目标里还有未核验的题面，需要澄清/确认"
 * （`AdaptiveDecisionKind.CLARIFY_OR_CONFIRM`），但此前 feature/app 没有任何实现方：
 * 决策被算出来就丢掉了——这正是 KD-67 的 fail-closed 死端之一。本接口是该决策在
 * domain 层的消费端口；确认卡/补题入口在 feature 侧实现它（本批不建 UI，见 KD-67）。
 *
 * 实现方约束：
 * - 只会在选择器返回 `CLARIFY_OR_CONFIRM` 且确实存在未核验目标候选时收到调用；
 *   其余决策（ASK/SKIP/NO_SAFE）不会产生空回调。
 * - [AdaptiveClarificationRequest] 是**只读快照**：候选列表是决策时刻的投影，
 *   实现方不得把"确认了旧候选"当成"新题面已核验"——核验升级必须走
 *   `decideAnswerKeyVerificationUpgrade` + 写方 CAS，不能由 UI 直接改状态。
 * - 实现方自己负责 UI 线程/生命周期；选择器与装配器不持有 Activity/Fragment。
 */
interface AdaptiveClarificationConsumer {
    /** 选择器要求澄清/确认时调用（同一决策重放可能重复调用，实现方需幂等）。 */
    suspend fun onClarificationRequired(request: AdaptiveClarificationRequest)
}

/**
 * 一道等待确认的题面的最小投影：消费者据此定位题面并展示"这是不是正确答案"的确认卡，
 * 不需要重新跑一遍选择器的未核验过滤（避免两处判据漂移）。
 */
data class AdaptiveClarificationCandidate(
    val assessmentItemId: String,
    val itemFamilyId: String,
    val knowledgeNodeIds: Set<String>,
) {
    init {
        require(assessmentItemId.isNotBlank()) { "Clarification candidate id must not be blank" }
        require(itemFamilyId.isNotBlank()) { "Clarification candidate family id must not be blank" }
        require(knowledgeNodeIds.isNotEmpty() && knowledgeNodeIds.none(String::isBlank)) {
            "Clarification candidate must target at least one knowledge node"
        }
    }
}

/**
 * 一次澄清/确认请求：选择器决策的可消费载荷。
 *
 * 只携带"需要确认什么"与"为什么"，不携带可写的核验判定——确认结果必须回到
 * [AnswerKeyVerificationUpgradeRequest] 的显式来源字段（PRD-02），本对象不能直接改状态。
 */
data class AdaptiveClarificationRequest(
    val learnerId: String,
    val targetKnowledgeNodeIds: Set<String>,
    val requestedTargetKind: AdaptiveTargetKind,
    val candidates: List<AdaptiveClarificationCandidate>,
    val reasonCodes: Set<String>,
    val selectorVersion: String,
    val decisionAtEpochMillis: Long,
) {
    init {
        require(learnerId.isNotBlank()) { "Clarification learner id must not be blank" }
        require(targetKnowledgeNodeIds.isNotEmpty() && targetKnowledgeNodeIds.none(String::isBlank)) {
            "Clarification must target at least one knowledge node"
        }
        require(candidates.isNotEmpty()) {
            "A clarification request must carry at least one unverified candidate"
        }
        require(candidates.map(AdaptiveClarificationCandidate::assessmentItemId).distinct().size == candidates.size) {
            "Clarification candidates must have unique assessment-item ids"
        }
        require(reasonCodes.isNotEmpty()) { "Clarification must be explainable" }
        require(selectorVersion.isNotBlank()) { "Clarification selector version must not be blank" }
        require(decisionAtEpochMillis >= 0) { "Clarification decision time must not be negative" }
    }
}

/**
 * 从选择器决策生成可消费的澄清载荷；非 `CLARIFY_OR_CONFIRM` 或没有未核验目标时返回 null。
 *
 * 未核验候选的过滤与选择器 `noCandidateDecision` 同源（[unverifiedTargetCandidates]）：
 * 若选择器因其他原因（证据过期/预测越界）返回 CLARIFY，载荷仍然如实列出"目标上尚未核验
 * 的题面"，消费者可以据此补题/确认，而不是拿到一个空列表。
 */
fun AdaptiveDecision.clarificationRequest(
    request: AdaptiveSelectionRequest,
): AdaptiveClarificationRequest? {
    if (kind != AdaptiveDecisionKind.CLARIFY_OR_CONFIRM) return null
    val unverified = unverifiedTargetCandidates(
        candidates = request.candidates,
        targetKnowledgeNodeIds = request.targetKnowledgeNodeIds,
    )
    // 防御：CLARIFY 由 noCandidateDecision 只在"存在未核验目标"时产生；走到这里为空
    // 说明决策与候选已经不一致（例如调用方自己构造了 AdaptiveDecision），不编造载荷。
    if (unverified.isEmpty()) return null
    return AdaptiveClarificationRequest(
        learnerId = request.learnerSnapshot.learnerId,
        targetKnowledgeNodeIds = request.targetKnowledgeNodeIds,
        requestedTargetKind = request.requestedTargetKind,
        candidates = unverified.map { candidate ->
            AdaptiveClarificationCandidate(
                assessmentItemId = candidate.assessmentItemId,
                itemFamilyId = candidate.itemFamilyId,
                knowledgeNodeIds = candidate.knowledgeNodeIds,
            )
        },
        reasonCodes = reasonCodes,
        selectorVersion = selectorVersion,
        decisionAtEpochMillis = request.decisionAtEpochMillis,
    )
}
