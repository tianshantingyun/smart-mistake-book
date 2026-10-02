package com.tingyun.smartmistakebook.core.data.study

import com.tingyun.smartmistakebook.core.database.StudyDatabasePort
import com.tingyun.smartmistakebook.core.database.entity.LearnerChatEvidenceEntity
import com.tingyun.smartmistakebook.core.domain.MasteryWriteGate
import com.tingyun.smartmistakebook.core.model.TutorEvidenceDirection
import com.tingyun.smartmistakebook.core.model.TutorUnderstandingTier

/**
 * 证据写通道的**唯一写入口**（D-M M4，2026-10-02）。
 *
 * 退场背景：此前两条通道各写各的——`RoomTutorToolRunner.masteryUpdate`（模型讲题）
 * 与 `KnowledgeQuizFeedbackWriter.submit`（知识点测验）。同一个门（[MasteryWriteGate]）
 * 与同一条表（`learner_chat_evidence`）却有两套落库口径：
 *
 * - **被拒**：讲题通道落 rejected 观察行，测验通道什么都不落（拒了等于没发生，
 *   校准面读不到"测验通道被拒得最多的是哪一档"）；
 * - **anchor_class**：讲题通道按代号角色落三档，测验通道恒 NULL；
 * - **配额/D9 半权**：讲题通道在 runner 里现算冷却/会话/滚动窗并施加半权，测验通道
 *   另抄一遍读取、且不参与 D9 降权；
 * - **幂等 id**：两条通道各写一条拼串规则；
 * - **置信**：测验通道写死裸字面量 `1.0`（无出处）。
 *
 * 本类把这些收成入口规则：锚定判定、三路配额读取、门评估、D9 降权、幂等 id、
 * **被拒落观察行**全部只在本类发生。通道差异只剩"语义输入"（方向/理解档/理由/
 * 置信/锚定等级），由调用方按各自语义提供：
 *
 * - 讲题通道：`anchorClass` 按代号角色机械确立（非 CONFIRMED 减半，见
 *   [MasteryWriteGate.effectiveEvidenceWeight]）；置信 = 模型自报的 `call.confidence`；
 * - 测验通道：`anchorClass = CONFIRMED`（本次测验**直接选定**的知识点，不是检索候选/
 *   披露），置信 = [OBJECTIVE_ANSWER_CONFIDENCE]（本地机械判定对错，理由可引）。
 *
 * 零 bump 理由：讲题通道逐字段与重构前一致；测验通道新增的是 rejected 观察行（不进投影）
 * 与 `anchor_class` 审计列（投影只读 `weight`），accepted 行的 weight/direction/时间戳
 * 一个字节没变（CONFIRMED 全权重）。
 */
internal class KnowledgeEvidenceWriter(
    private val database: StudyDatabasePort,
) {

    suspend fun write(request: KnowledgeEvidenceWriteRequest): KnowledgeEvidenceWriteOutcome {
        val now = request.occurredAtEpochMillis
        // 锚定：节点必须真实存在；讲题通道在科目上下文内还要求节点属于该科目
        //（防模型在一个科目会话里把证据写进无关科目）。测验通道 requiredSubject = null。
        val knowledgeNode = request.knowledgeNodeId
            .takeIf(String::isNotBlank)
            ?.let { database.readKnowledgeNodesByIds(setOf(it)).firstOrNull() }
        val anchored = knowledgeNode != null &&
            (request.requiredSubject == null || knowledgeNode.subject == request.requiredSubject)

        // 门控数据源：三个索引支撑的精确查询（O(log n)，不做全表拉取）。
        // - 同 KC 冷却按 learner 粒度（跨会话）：防"我懂了"开新会话绕过。
        // - 会话配额按本会话 accepted 数（null 会话 = 无会话通道，计 0——与讲题直调一致）。
        // - learner 滚动窗配额按 learner 最近窗口内 accepted 总数（防多会话 farm）。
        val lastSameKcWrite = database.lastAcceptedChatEvidenceAtForKc(
            learnerId = request.learnerId,
            knowledgeNodeId = request.knowledgeNodeId,
        )
        val sameKcLastWriteAgoMillis = lastSameKcWrite?.let { (now - it).coerceAtLeast(0) }
        val acceptedInWindow = database.countAcceptedChatEvidenceSince(
            learnerId = request.learnerId,
            sinceEpochMillis = now - MasteryWriteGate.LEARNER_WINDOW_MILLIS,
        )
        val acceptedInConversation = request.conversationId
            ?.let { database.countAcceptedChatEvidenceInConversation(it) }
            ?: 0
        val input = MasteryWriteGate.GateInput(
            evidenceConfidence = request.evidenceConfidence,
            direction = request.direction,
            understanding = request.understanding,
            knowledgeNodeIsAnchored = anchored,
            hasObjectiveSupport = request.hasObjectiveSupport,
            evidenceAnchorCount = request.evidenceAnchorCount,
            objectiveAnswersContradictPositive = request.objectiveAnswersContradictPositive,
            sameKcLastWriteAgoMillis = sameKcLastWriteAgoMillis,
            writesThisConversation = acceptedInConversation,
            writesThisLearnerInWindow = acceptedInWindow,
            attentionFactor = request.attentionFactor,
        )
        val evidenceId = knowledgeEvidenceId(
            namespace = request.namespace,
            channel = request.channel,
            knowledgeNodeId = request.knowledgeNodeId,
            discriminator = request.discriminator,
        )
        return when (val result = MasteryWriteGate.evaluate(input)) {
            is MasteryWriteGate.GateResult.Accepted -> {
                // D9 降权安全垫（写口唯一数值分支）：anchor_class 非 CONFIRMED 时权重减半，
                // **写入时**施加——存库 weight 即生效权重，投影/重放按存库值逐位进行。
                val effectiveWeight = MasteryWriteGate.effectiveEvidenceWeight(
                    baseWeight = result.weight,
                    anchorClass = request.anchorClass,
                )
                database.recordChatEvidence(
                    listOf(
                        entry(
                            request = request,
                            evidenceId = evidenceId,
                            weight = effectiveWeight,
                            rejectedReason = null,
                        ),
                    ),
                )
                KnowledgeEvidenceWriteOutcome.Accepted(weight = effectiveWeight)
            }
            is MasteryWriteGate.GateResult.Rejected -> {
                // 被拒 ≠ 删除：落 rejected 审计行（不进投影），拒因交回调用方。
                // anchor_class 一并落上——校准要能区分"哪一档来路的证据被拒得最多"。
                database.recordChatEvidence(
                    listOf(
                        entry(
                            request = request,
                            evidenceId = evidenceId,
                            weight = 0.0,
                            rejectedReason = result.reason,
                        ),
                    ),
                )
                KnowledgeEvidenceWriteOutcome.Rejected(reason = result.reason)
            }
        }
    }

    private fun entry(
        request: KnowledgeEvidenceWriteRequest,
        evidenceId: String,
        weight: Double,
        rejectedReason: MasteryWriteGate.RejectReason?,
    ): LearnerChatEvidenceEntity = LearnerChatEvidenceEntity(
        evidence_id = evidenceId,
        learner_id = request.learnerId,
        conversation_id = request.conversationId.orEmpty(),
        knowledge_node_id = request.knowledgeNodeId,
        direction = request.direction.name,
        weight = weight,
        reason_markdown = request.reasonMarkdown,
        confidence = request.evidenceConfidence,
        source_kind = request.channel.sourceKind,
        created_at_epoch_millis = request.occurredAtEpochMillis,
        rejected_reason = rejectedReason?.name,
        rejected_at_epoch_millis = rejectedReason?.let { request.occurredAtEpochMillis },
        anchor_class = request.anchorClass,
    )
}

/** 两条证据写通道的语义标签：`source_kind` 落库值与幂等 id 前缀由它单源决定。 */
internal enum class KnowledgeEvidenceChannel(
    val sourceKind: String,
    internal val idPrefix: String,
) {
    MODEL_CHAT(sourceKind = "MODEL_CHAT", idPrefix = "chat-ev"),
    KNOWLEDGE_QUIZ(sourceKind = "KNOWLEDGE_QUIZ", idPrefix = "knowledge-quiz"),
}

/**
 * 幂等证据 id 的**唯一规则**（D-M M4）：同一 `(namespace, channel, discriminator, 节点)`
 * 恒映射同一 id，重试不重复落库（Room IGNORE 兜底）；`namespace` 缺失时退化为
 * 唯一但**非幂等**的 nanoTime id（直调/测试调用者没有 requestId 命名空间）。
 *
 * 形态按通道保留（讲题 `chat-ev:<requestId>:<工具名>:<节点>`、测验
 * `knowledge-quiz:<requestId>:<节点>`）——id 是既有行主键，改造不动存量行的形状。
 */
internal fun knowledgeEvidenceId(
    namespace: String?,
    channel: KnowledgeEvidenceChannel,
    knowledgeNodeId: String,
    discriminator: String? = null,
): String {
    if (namespace.isNullOrBlank()) return "${channel.idPrefix}-${System.nanoTime()}"
    return when (channel) {
        KnowledgeEvidenceChannel.MODEL_CHAT ->
            "${channel.idPrefix}:$namespace:${discriminator.orEmpty()}:$knowledgeNodeId"
        KnowledgeEvidenceChannel.KNOWLEDGE_QUIZ ->
            "${channel.idPrefix}:$namespace:$knowledgeNodeId"
    }
}

/** 一次证据写的语义输入（数值与门控由 [KnowledgeEvidenceWriter] 本地决定）。 */
internal data class KnowledgeEvidenceWriteRequest(
    val learnerId: String,
    val channel: KnowledgeEvidenceChannel,
    /** 幂等命名空间（模型任务 requestId）；null = 直调，退回唯一 id。 */
    val namespace: String?,
    /** 讲题通道 = 工具名（进入幂等 id 区分同 request 的不同工具）。 */
    val discriminator: String? = null,
    /** 会话 id；null = 无会话通道（讲题大厅直调），会话配额计 0。 */
    val conversationId: String?,
    val knowledgeNodeId: String,
    val direction: TutorEvidenceDirection,
    val understanding: TutorUnderstandingTier,
    val evidenceConfidence: Double,
    val reasonMarkdown: String,
    /** 写入时锚定等级（D9）：CONFIRMED 全权重，其余减半。 */
    val anchorClass: String,
    /** 讲题通道的科目上下文：节点须属于该科目才锚定；null = 不强加科目匹配。 */
    val requiredSubject: String? = null,
    val hasObjectiveSupport: Boolean = false,
    val evidenceAnchorCount: Int = 0,
    val objectiveAnswersContradictPositive: Boolean = false,
    val attentionFactor: Double = 1.0,
    val occurredAtEpochMillis: Long,
)

/** 写入口的判定结果：accepted 带回生效权重（D9 施加后），rejected 带回拒因。 */
internal sealed interface KnowledgeEvidenceWriteOutcome {
    data class Accepted(val weight: Double) : KnowledgeEvidenceWriteOutcome
    data class Rejected(val reason: MasteryWriteGate.RejectReason) : KnowledgeEvidenceWriteOutcome
}
