package com.tingyun.smartmistakebook.core.data.study

import com.tingyun.smartmistakebook.core.database.StudyDatabasePort
import com.tingyun.smartmistakebook.core.database.StudyDbValue
import com.tingyun.smartmistakebook.core.database.entity.LearnerChatEvidenceEntity
import com.tingyun.smartmistakebook.core.domain.KnowledgeQuizFeedbackResult
import com.tingyun.smartmistakebook.core.domain.MasteryWriteGate
import com.tingyun.smartmistakebook.core.domain.knowledgeQuizMasteryVerdict
import com.tingyun.smartmistakebook.core.model.EvidenceCorrection
import com.tingyun.smartmistakebook.core.model.KnowledgeAnchorClass
import com.tingyun.smartmistakebook.core.model.LearningEvidenceDirection
import com.tingyun.smartmistakebook.core.model.answerKeyTrustDecision
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 客观作答通道写入的 `evidenceConfidence`。
 *
 * 为什么是 1.0（而不是模型通道的 `call.confidence`）：这条证据的方向**不由任何判断者给出**
 * ——对错由本地 `selectedChoiceId == correctChoiceId` 机械比较得出
 *（[knowledgeQuizMasteryVerdict]），不存在模型语义不确定性，也不存在"判断得准不准"的档位；
 * 1.0 表示"方向是本地事实"。D-M M4 前它是本文件里的裸字面量（无出处，与模型通道的
 * 判断置信混在同一语义位）；现在具名并可引：引用链 = 本地机械判定 → 本常量。
 *
 * **PRD-02 起它只声明"本地比较确定"，不再声明"答案键已被独立核验"**：键信任由题面快照的
 * `verification_status` 单独判定（[answerKeyTrustDecision]）。
 */
internal const val OBJECTIVE_ANSWER_CONFIDENCE = 1.0

/** 键未独立核验：只保留会话反馈与真实选择，不新增数值掌握证据。 */
internal const val REJECT_ANSWER_KEY_UNVERIFIED = "ANSWER_KEY_UNVERIFIED"

/** 键已被更正/质疑：旧键判分（迟到）或该键暂停计分。 */
internal const val REJECT_ANSWER_KEY_VERSION_STALE = "ANSWER_KEY_VERSION_STALE"
internal const val REJECT_ANSWER_KEY_DISPUTED = "ANSWER_KEY_DISPUTED"

/**
 * Knowledge-quiz feedback writes: the objective verdict is gated by
 * [com.tingyun.smartmistakebook.core.domain.MasteryWriteGate] (node anchoring,
 * per-conversation and per-learner quotas) before it becomes chat evidence.
 *
 * D-M M4（2026-10-02）：本通道不再自写 `learner_chat_evidence`——它与讲题通道共用唯一
 * 写入口 [KnowledgeEvidenceWriter]（锚定/配额/D9/幂等 id/被拒观察行都在那里）。
 * 本类只提供客观作答通道的语义输入：
 * - 方向/理解档来自 [knowledgeQuizMasteryVerdict]（本地机械判定）；
 * - `anchorClass = CONFIRMED`——测验题目**直接选定**该知识点（不是检索候选/披露），
 *   D9 半权因此不适用（权重与 M4 前逐位一致）；
 * - 置信 = [OBJECTIVE_ANSWER_CONFIDENCE]（具名、可引）。
 *
 * **PRD-02（2026-10-19）键信任门**：本地机械比较成功 ≠ 键已验证。写入口先按
 * `KnowledgeQuizItemStore.readSnapshot(session, node)` 的 `verification_status` 判定两轴：
 * - `VERIFIED` / `USER_ASSERTED` → 走上面的既有规则；
 * - `UNKNOWN`/缺行/读不回/键与本题面不符 → 只落一条 **rejected 观察行**（保留真实选择），
 *   不新增数值掌握证据；迟到旧键判分（该证据已被更正）直接拒收。
 *
 * 不自动晋升旧反馈：历史行一字不改；本门只约束**新写**。
 */
internal class KnowledgeQuizFeedbackWriter(
    private val database: StudyDatabasePort,
    private val learnerId: String,
) {
    private val evidenceWriter = KnowledgeEvidenceWriter(database)
    private val itemStore = KnowledgeQuizItemStore(database, learnerId)

    suspend fun submit(
        requestId: String,
        knowledgeNodeId: String,
        correctChoiceId: String,
        selectedChoiceId: String,
        occurredAtEpochMillis: Long,
        conversationId: String,
    ): KnowledgeQuizFeedbackResult {
        val isCorrect = selectedChoiceId == correctChoiceId
        val verdict = knowledgeQuizMasteryVerdict(isCorrect)
        require(conversationId.isNotBlank()) { "Knowledge quiz conversation id must not be blank" }

        val evidenceId = knowledgeEvidenceId(
            namespace = requestId,
            channel = KnowledgeEvidenceChannel.KNOWLEDGE_QUIZ,
            knowledgeNodeId = knowledgeNodeId,
        )
        val existingRows = database.readChatEvidenceByLearner(learnerId)

        // 迟到旧键判分：这条证据已经被一条定向更正/撤销指向——旧键不再有计分权威。
        // 不写新行、不推进账本（被拒/未曾接受的旧记录不凭纠错生成新事实）。
        if (existingRows.any { it.corrects_evidence_id == evidenceId }) {
            return KnowledgeQuizFeedbackResult(
                isCorrect = isCorrect,
                evidenceRecorded = false,
                rejectedReason = REJECT_ANSWER_KEY_VERSION_STALE,
            )
        }

        // 中性预报告（未看答案时"答案有问题"）：无 target、无已选选项 → 只观察；
        // 该会话/知识点/题面的键暂停计分，但仍把学生的真实选择留成观察行。
        // 争议按题面 id 匹配；题面 id 可能含冒号，必须用**完整前缀**判断（下面拿到题面后做）。

        // 两轴判定：题面读得回、键与本题面一致、且键有独立支持才允许数值证据。
        val sessionId = knowledgeQuizSessionIdOf(conversationId)
        val snapshot = sessionId?.let { itemStore.readSnapshot(it, knowledgeNodeId) }
        val snapshotItem = snapshot?.item
        val itemId = snapshotItem?.id
        if (itemId != null && existingRows.any {
                it.isDisputeObservationFor(
                    knowledgeNodeId = knowledgeNodeId,
                    itemId = itemId,
                )
            }
        ) {
            recordRejectedObservation(
                evidenceId = evidenceId,
                knowledgeNodeId = knowledgeNodeId,
                conversationId = conversationId,
                direction = verdict.direction.name,
                reason = "知识点复习作答（${if (isCorrect) "答对" else "答错"}）" +
                    "；学生选择 $selectedChoiceId；该键已被质疑，暂停计分",
                occurredAtEpochMillis = occurredAtEpochMillis,
                rejectedReason = REJECT_ANSWER_KEY_DISPUTED,
            )
            return KnowledgeQuizFeedbackResult(
                isCorrect = isCorrect,
                evidenceRecorded = false,
                rejectedReason = REJECT_ANSWER_KEY_DISPUTED,
            )
        }
        // 后续作答按**最新有效替代键**校验：用户更正过的键来源是 USER_ASSERTED（不是机器
        // 金标），也不改写题面快照。没有更正时仍以快照键为准。
        val correctedKey = snapshotItem?.let {
            latestCorrectedKey(existingRows, knowledgeNodeId, it.id)
        }
        val expectedKey = correctedKey ?: snapshotItem?.correctChoiceId
        val keyMatches = expectedKey != null && expectedKey == correctChoiceId
        val trust = answerKeyTrustDecision(
            verificationStatus = if (correctedKey != null) {
                "USER_ASSERTED"
            } else {
                snapshot?.verificationStatus
            },
            itemReadable = snapshotItem != null,
            keyVersion = snapshot?.answerSpecSchema,
        )
        if (!keyMatches) {
            val stale = snapshotItem != null
            recordRejectedObservation(
                evidenceId = evidenceId,
                knowledgeNodeId = knowledgeNodeId,
                conversationId = conversationId,
                direction = verdict.direction.name,
                reason = "知识点复习作答（${if (isCorrect) "答对" else "答错"}）" +
                    "；学生选择 $selectedChoiceId；" +
                    if (stale) "本题面已换成另一版答案键" else "这道题的题面/答案键读不回",
                occurredAtEpochMillis = occurredAtEpochMillis,
                rejectedReason = if (stale) {
                    REJECT_ANSWER_KEY_VERSION_STALE
                } else {
                    REJECT_ANSWER_KEY_UNVERIFIED
                },
            )
            return KnowledgeQuizFeedbackResult(
                isCorrect = isCorrect,
                evidenceRecorded = false,
                rejectedReason = if (stale) {
                    REJECT_ANSWER_KEY_VERSION_STALE
                } else {
                    REJECT_ANSWER_KEY_UNVERIFIED
                },
            )
        }
        if (!trust.mayWriteNumericEvidence) {
            recordRejectedObservation(
                evidenceId = evidenceId,
                knowledgeNodeId = knowledgeNodeId,
                conversationId = conversationId,
                direction = verdict.direction.name,
                reason = "知识点复习作答（${if (isCorrect) "答对" else "答错"}）" +
                    "；学生选择 $selectedChoiceId；答案键尚未独立核验，只记录选择",
                occurredAtEpochMillis = occurredAtEpochMillis,
                rejectedReason = REJECT_ANSWER_KEY_UNVERIFIED,
            )
            return KnowledgeQuizFeedbackResult(
                isCorrect = isCorrect,
                evidenceRecorded = false,
                rejectedReason = REJECT_ANSWER_KEY_UNVERIFIED,
            )
        }

        val writeOutcome = evidenceWriter.write(
            KnowledgeEvidenceWriteRequest(
                learnerId = learnerId,
                channel = KnowledgeEvidenceChannel.KNOWLEDGE_QUIZ,
                namespace = requestId,
                conversationId = conversationId,
                knowledgeNodeId = knowledgeNodeId,
                direction = verdict.direction,
                understanding = verdict.understanding,
                evidenceConfidence = OBJECTIVE_ANSWER_CONFIDENCE,
                // 键来自用户更正时在理由里如实留痕（不把用户来源伪装成机器金标）。
                reasonMarkdown = "知识点复习作答（${if (isCorrect) "答对" else "答错"}）" +
                    (if (correctedKey != null) "；按用户更正的答案键判定" else ""),
                anchorClass = KnowledgeAnchorClass.CONFIRMED.name,
                hasObjectiveSupport = verdict.hasBehavioralSupport,
                // 客观作答通道本来就有一份本地可核查的证据（答对），无需模型引用锚。
                evidenceAnchorCount = 0,
                // 本通道的语义**就是**学生的客观作答，不存在"作答否定判断"这一冲突：
                // 答错时 direction 已经是 NEGATIVE，与作答方向一致。
                objectiveAnswersContradictPositive = false,
                // 知识点复习当前没有 UI 注意力采集通道；1.0 = 不因注意力降门（与 M4 前一致）。
                attentionFactor = 1.0,
                occurredAtEpochMillis = occurredAtEpochMillis,
            ),
        )
        return when (writeOutcome) {
            is KnowledgeEvidenceWriteOutcome.Accepted -> if (
                writeOutcome.ledgerStatus == KnowledgeEvidenceLedgerStatus.BLOCKED_BY_REJECTED_ROW
            ) {
                // 同身份已有一条 rejected 观察行（如早期未核验键的尝试）：accepted 写进不了账本。
                // 不改写历史行、不凭重试伪造事实——如实回报"没有记录"。
                KnowledgeQuizFeedbackResult(
                    isCorrect = isCorrect,
                    evidenceRecorded = false,
                    rejectedReason = "EVIDENCE_IDENTITY_STICKY",
                )
            } else {
                KnowledgeQuizFeedbackResult(
                    isCorrect = isCorrect,
                    evidenceRecorded = true,
                )
            }
            is KnowledgeEvidenceWriteOutcome.Rejected -> KnowledgeQuizFeedbackResult(
                isCorrect = isCorrect,
                evidenceRecorded = false,
                rejectedReason = writeOutcome.reason.name,
            )
        }
    }

    private suspend fun recordRejectedObservation(
        evidenceId: String,
        knowledgeNodeId: String,
        conversationId: String,
        direction: String,
        reason: String,
        occurredAtEpochMillis: Long,
        rejectedReason: String,
    ) {
        database.recordChatEvidence(
            listOf(
                LearnerChatEvidenceEntity(
                    evidence_id = evidenceId,
                    learner_id = learnerId,
                    conversation_id = conversationId,
                    knowledge_node_id = knowledgeNodeId,
                    direction = direction,
                    weight = 0.0,
                    reason_markdown = reason,
                    confidence = OBJECTIVE_ANSWER_CONFIDENCE,
                    source_kind = StudyDbValue.ChatEvidenceSourceKind.KNOWLEDGE_QUIZ,
                    created_at_epoch_millis = occurredAtEpochMillis,
                    rejected_reason = rejectedReason,
                    rejected_at_epoch_millis = occurredAtEpochMillis,
                    anchor_class = MasteryWriteGate.ANCHOR_CLASS_CONFIRMED,
                ),
            ),
        )
    }
}

/** 中性"答案有问题"预报告的机器可读前缀（理由正文跟在后面；题面 id 可含冒号）。 */
internal fun knowledgeQuizKeyDisputeReasonPrefix(itemId: String): String =
    "answer-key-dispute:$itemId:"

/**
 * 键**替代**的机器可读前缀：`answer-key-correction:<itemId>:<newCorrectChoiceId>:<理由>`。
 *
 * 为什么把新键写进理由：schema 只加了一列（明确目标），没有第二个自由列可以承载“新键”。
 * 前缀让**后续作答**能按最新有效替代校验 `correctChoiceId`（旧键迟到被拒、新键可正常计分），
 * 同时不把新键伪装成题面快照（快照历史仍一字不改）。
 */
internal fun knowledgeQuizKeyCorrectionReasonPrefix(
    itemId: String,
    newCorrectChoiceId: String,
): String = "answer-key-correction:$itemId:$newCorrectChoiceId:"

internal fun knowledgeQuizKeyCorrectionNewCorrectChoice(
    reasonMarkdown: String,
    itemId: String,
): String? {
    val prefix = "answer-key-correction:$itemId:"
    if (!reasonMarkdown.startsWith(prefix)) return null
    val rest = reasonMarkdown.removePrefix(prefix)
    val separator = rest.indexOf(':')
    return if (separator > 0) rest.substring(0, separator) else null
}

/** 同一题面最近一条**未被撤销**的替代键；没有 = null。 */
internal fun latestCorrectedKey(
    rows: List<LearnerChatEvidenceEntity>,
    knowledgeNodeId: String,
    itemId: String,
): String? {
    val voidedCorrections = rows.asSequence()
        .filter {
            it.source_kind == LearnerChatEvidenceEntity.SOURCE_KIND_KEY_VOID && !it.isRejected
        }
        .mapNotNull(LearnerChatEvidenceEntity::corrects_evidence_id)
        .toSet()
    return rows.asReversed()
        .filter {
            it.source_kind == LearnerChatEvidenceEntity.SOURCE_KIND_KEY_CORRECTION &&
                !it.isRejected &&
                it.knowledge_node_id == knowledgeNodeId &&
                it.evidence_id !in voidedCorrections
        }
        .firstNotNullOfOrNull {
            knowledgeQuizKeyCorrectionNewCorrectChoice(it.reason_markdown, itemId)
        }
}

private fun LearnerChatEvidenceEntity.isDisputeObservationFor(
    knowledgeNodeId: String,
    itemId: String,
): Boolean {
    if (!isRejected || rejected_reason != REJECT_ANSWER_KEY_DISPUTED) return false
    // 争议按“题面”生效（跨会话）：同一道题在任何会话里被报告为有问题，后续作答都不计分。
    // 题面 id 可能含冒号，所以只做完整前缀匹配（调用方拿得到题面 id）。
    return knowledge_node_id == knowledgeNodeId &&
        reason_markdown.startsWith(knowledgeQuizKeyDisputeReasonPrefix(itemId))
}

// ---- 定向纠正 / 撤销 ----

/** 一次定向纠正/撤销命令（PRD-02）。 */
data class AnswerKeyCorrectionCommand(
    val requestId: String,
    val knowledgeNodeId: String,
    val conversationId: String,
    /** 被纠正的证据 id；null = 按 `itemId + selectedChoiceId` 在**原始作答**账本行里定位。 */
    val targetEvidenceId: String? = null,
    val itemId: String? = null,
    val selectedChoiceId: String? = null,
    /** 更正后的答案（与 [selectedChoiceId] 一起可直接算出替代判分）。 */
    val newCorrectChoiceId: String? = null,
    /** 替代判分的显式真值（无 `newCorrectChoiceId` 时必填）。 */
    val replacementCorrect: Boolean? = null,
    /** true = 撤销目标；false = 用替代判分重算目标。 */
    val voidTarget: Boolean = false,
    /** 可选的键版本 CAS（= 上一次更正事件的账本序列；0 = 尚无更正）。 */
    val expectedKeyVersion: Long? = null,
    val reasonMarkdown: String,
    val occurredAtEpochMillis: Long,
)

enum class AnswerKeyCorrectionStatus {
    /** 已落库并写入账本；下一次 drain 会触发全量重放。 */
    RECORDED,

    /** 同身份重复请求：既有行/既有账本事件仍是唯一事实。 */
    REPLAYED,

    /** 目标不存在、不是已接受证据、或请求自身不完整；未写入任何事实。 */
    REJECTED,

    /** `expectedKeyVersion` 与当前账本不符；未写入任何事实。 */
    CONFLICT,
}

/**
 * 一次纠正/撤销的回执。`replayPending = true` 表示账本已追加、投影尚未追平；
 * 投影 checkpoint 追平后由 drainer 的消费回执收口（本回执不把“记下了”说成“已重放完”）。
 */
data class AnswerKeyCorrectionReceipt(
    val correctionId: String,
    val status: AnswerKeyCorrectionStatus,
    val targetEvidenceId: String?,
    val affectedKnowledgeNodeIds: Set<String>,
    val replayPending: Boolean,
    /** 会话语义上的键版本 = 该更正事件在账本里的序列；未落账本 = null。 */
    val keyVersion: Long?,
    val reason: String? = null,
)

/**
 * 定向纠正/撤销的唯一写入口（PRD-02 / KD-43）。
 *
 * 约束：
 * - 只追加**有明确 target** 的证据事件（`learner_chat_evidence.corrects_evidence_id`），
 *   不改写/删除原行；
 * - 目标必须是该 learner 的**已接受账本证据**（rejected 观察行、不存在/未曾接受的记录都拒绝）；
 *   按 `itemId + selectedChoiceId` 隐式定位时只匹配**原始作答**（`KNOWLEDGE_QUIZ`）行，
 *   不会把此前写下的 `KEY_CORRECTION`/`KEY_VOID` 自己当成目标（否则第二次报告会撤销
 *   第一条纠正、把原错误键重新放回掌握度）；显式 `targetEvidenceId` 仍可用于“撤销更正”；
 * - 目标必须更早（账本序列），从结构上禁止循环引用；
 * - 纠正路径绕过新增证据的冷却/配额（设计 §3.3），但仍校验目标所有权/版本/来源；
 *   纠正事件本身不作为一条新的数值证据计入掌握度（只在原作答位置替换/排除）；
 * - `expectedKeyVersion` 做一次显式 CAS，迟到请求显式 CONFLICT，不用新时间戳绕过幂等；
 *   `submit` 在本进程内串行执行“读当前键版本 → 落账本”（companion 互斥锁），因此
 *   并发更正不会同时通过同一版本检查；
 * - **REPLACE 必须带 `itemId + newCorrectChoiceId`**：新键写进理由的机器可读前缀
 *   （[knowledgeQuizKeyCorrectionReasonPrefix]），后续作答据此校验；新键还必须是该题面真实
 *   存在的选项。VOID 不需要新键。
 * - 命令身份 = `(requestId, target, kind)` → 确定性 correction id，重复请求 REPLAYED。
 *
 * 中性"答案有问题"预报告（`voidTarget = true` 且没有 target/item/choice）只落 rejected
 * 观察行：它暂停该键计分，但不冒充一笔账本更正，也不生成任何学习事实。**已作答但作答只
 * 落成 rejected 观察行**（例如题面键未核验）时同样不能假装写账本更正：按
 * `itemId + selectedChoiceId` 找不到已接受目标，就回落到同一条争议观察行，让学生的报告
 * 真的被记下、该键暂停计分，而不是回 `ANSWER_KEY_TARGET_NOT_FOUND` 丢反馈。
 */
class AnswerKeyCorrectionWriter(
    private val database: StudyDatabasePort,
    private val learnerId: String,
) {

    /**
     * 串行化 CAS 检查与落账本（见类 KDoc）；纠正写入本身很少，不构成吞吐热点。
     * 放在 companion 而不是实例字段：同一进程内即使出现第二个 writer，也不能让两个
     * 并发更正同时通过同一 `expectedKeyVersion`。
     */
    private companion object {
        val correctionMutex = Mutex()
    }

    suspend fun submit(command: AnswerKeyCorrectionCommand): AnswerKeyCorrectionReceipt =
        correctionMutex.withLock { submitLocked(command) }

    private suspend fun submitLocked(
        command: AnswerKeyCorrectionCommand,
    ): AnswerKeyCorrectionReceipt {
        if (command.requestId.isBlank() || command.reasonMarkdown.isBlank()) {
            return rejected("ANSWER_KEY_CORRECTION_REQUEST_INCOMPLETE")
        }

        val rows = database.readChatEvidenceByLearner(learnerId)

        // 中性预报告（未看答案时"答案有问题"）：无 target、无已选选项 → 只观察；
        // 该会话/知识点/题面的键暂停计分，但仍把学生的真实选择留成观察行。
        if (command.voidTarget && command.targetEvidenceId == null &&
            command.selectedChoiceId == null
        ) {
            return recordDisputeObservation(command)
        }

        val target = resolveTarget(command, rows)
        if (target == null) {
            // 已作答但该作答只落成 rejected 观察行（最常见：题面键 UNKNOWN）时，账本里
            // 没有可撤销的已接受证据。UI 的“答案有问题”仍然是真实反馈：退回中性争议
            // 观察行（暂停该键计分、保留真实选择），而不是把反馈丢成 TARGET_NOT_FOUND。
            if (command.voidTarget && hasRejectedAnswerObservation(command, rows)) {
                return recordDisputeObservation(command)
            }
            return rejected("ANSWER_KEY_TARGET_NOT_FOUND")
        }
        if (target.learner_id != learnerId || target.isRejected || !target.isLedgerEvidence()) {
            return rejected("ANSWER_KEY_TARGET_NOT_LEDGERED")
        }

        val expected = command.expectedKeyVersion
        if (expected != null) {
            val current = currentKeyVersion(target.evidence_id)
            if (current != expected) {
                return AnswerKeyCorrectionReceipt(
                    correctionId = correctionId(command, target),
                    status = AnswerKeyCorrectionStatus.CONFLICT,
                    targetEvidenceId = target.evidence_id,
                    affectedKnowledgeNodeIds = setOf(target.knowledge_node_id),
                    replayPending = false,
                    keyVersion = current,
                    reason = "ANSWER_KEY_VERSION_CONFLICT",
                )
            }
        }

        val replacement = if (command.voidTarget) {
            null
        } else {
            replacementVerdict(command) ?: return rejected("ANSWER_KEY_REPLACEMENT_VERDICT_REQUIRED")
        }
        val replacementKeyPrefix = if (command.voidTarget) {
            ""
        } else {
            val itemId = command.itemId ?: return rejected("ANSWER_KEY_CORRECTION_ITEM_REQUIRED")
            if (itemId.isBlank()) return rejected("ANSWER_KEY_CORRECTION_ITEM_REQUIRED")
            // 原始证据的 id 里嵌着题面 id；对不上 = 调用方拿错了目标，别把新键写到另一道题上。
            if (target.source_kind != LearnerChatEvidenceEntity.SOURCE_KIND_KEY_CORRECTION &&
                target.source_kind != LearnerChatEvidenceEntity.SOURCE_KIND_KEY_VOID &&
                !target.evidence_id.contains(":$itemId:")
            ) {
                return rejected("ANSWER_KEY_TARGET_ITEM_MISMATCH")
            }
            val newCorrectChoiceId = command.newCorrectChoiceId
                ?: return rejected("ANSWER_KEY_REPLACEMENT_KEY_REQUIRED")
            if (newCorrectChoiceId.isBlank()) {
                return rejected("ANSWER_KEY_REPLACEMENT_KEY_REQUIRED")
            }
            // 新键必须是该题面真实存在的选项；否则 UI 的本地判题永远判不对。
            // 优先用**目标证据行自己的会话**（原始作答会话），不依赖调用方传对会话 id。
            val sessionId = knowledgeQuizSessionIdOf(target.conversation_id)
                ?: knowledgeQuizSessionIdOf(command.conversationId)
            val storedItem = sessionId?.let {
                KnowledgeQuizItemStore(database, learnerId)
                    .readSnapshot(it, target.knowledge_node_id)
                    ?.item
            }
            if (storedItem != null && storedItem.choices.none { it.id == newCorrectChoiceId }) {
                return rejected("ANSWER_KEY_CHOICE_NOT_IN_ITEM")
            }
            knowledgeQuizKeyCorrectionReasonPrefix(itemId, newCorrectChoiceId)
        }
        val direction = when {
            replacement == null -> LearningEvidenceDirection.NONE
            replacement -> LearningEvidenceDirection.POSITIVE
            else -> LearningEvidenceDirection.NEGATIVE
        }
        val weight = when {
            replacement == null -> 0.0
            replacement -> MasteryWriteGate.positiveWeightFor(
                knowledgeQuizMasteryVerdict(true).understanding,
            )
            else -> MasteryWriteGate.negativeWeight()
        }
        val correctionId = correctionId(command, target)
        val entry = LearnerChatEvidenceEntity(
            evidence_id = correctionId,
            learner_id = learnerId,
            conversation_id = command.conversationId,
            knowledge_node_id = target.knowledge_node_id,
            direction = direction.name,
            weight = weight,
            reason_markdown = replacementKeyPrefix + command.reasonMarkdown,
            confidence = 1.0,
            source_kind = if (command.voidTarget) {
                LearnerChatEvidenceEntity.SOURCE_KIND_KEY_VOID
            } else {
                LearnerChatEvidenceEntity.SOURCE_KIND_KEY_CORRECTION
            },
            created_at_epoch_millis = command.occurredAtEpochMillis,
            // 纠错不借用证据通道的锚定等级；这一列只对投影证据有审计意义。
            anchor_class = null,
            corrects_evidence_id = target.evidence_id,
        )
        val entries = buildList {
            add(entry)
            // 撤销一条**原始证据** = 该键被报告有问题：同时落一条 rejected 争议观察行
            // （与账本更正同一事务），让后续作答暂停计分。撤销一条“更正”是回退操作，
            // 不是键投诉，不写争议行。
            if (command.voidTarget && command.itemId != null &&
                target.source_kind != LearnerChatEvidenceEntity.SOURCE_KIND_KEY_CORRECTION &&
                target.source_kind != LearnerChatEvidenceEntity.SOURCE_KIND_KEY_VOID
            ) {
                add(disputeRow(command, command.itemId, target.knowledge_node_id))
            }
        }
        val receipt = database.recordChatEvidence(entries)
        val recorded = correctionId in receipt.insertedAcceptedEvidenceIds
        val replayed = correctionId in receipt.replayedAcceptedEvidenceIds
        if (!recorded && !replayed) {
            return rejected("ANSWER_KEY_CORRECTION_NOT_LEDGERED").copy(
                correctionId = correctionId,
                targetEvidenceId = target.evidence_id,
            )
        }
        return AnswerKeyCorrectionReceipt(
            correctionId = correctionId,
            status = if (recorded) {
                AnswerKeyCorrectionStatus.RECORDED
            } else {
                AnswerKeyCorrectionStatus.REPLAYED
            },
            targetEvidenceId = target.evidence_id,
            affectedKnowledgeNodeIds = setOf(target.knowledge_node_id),
            replayPending = recorded,
            keyVersion = ledgerSequenceOf(correctionId),
        )
    }

    /**
     * 中性"答案有问题"入口（未看答案时）：写一条 rejected 观察行，前缀里带题面 id。
     * 之后同题面的提交在 [KnowledgeQuizFeedbackWriter] 里被判为"暂停计分"。
     */
    private suspend fun recordDisputeObservation(
        command: AnswerKeyCorrectionCommand,
    ): AnswerKeyCorrectionReceipt {
        val itemId = command.itemId ?: return rejected("ANSWER_KEY_DISPUTE_ITEM_REQUIRED")
        if (itemId.isBlank()) return rejected("ANSWER_KEY_DISPUTE_ITEM_REQUIRED")
        val id = "key-dispute:${command.requestId}"
        val row = disputeRow(command, itemId, command.knowledgeNodeId)
        // rejected 行不进 ledger，`insertedAcceptedEvidenceIds` 恒空：是否首次只能看写入**前**。
        val existedBefore = database.readChatEvidenceByLearner(learnerId)
            .any { it.evidence_id == id }
        database.recordChatEvidence(listOf(row))
        return AnswerKeyCorrectionReceipt(
            correctionId = id,
            status = if (existedBefore) {
                AnswerKeyCorrectionStatus.REPLAYED
            } else {
                AnswerKeyCorrectionStatus.RECORDED
            },
            targetEvidenceId = null,
            affectedKnowledgeNodeIds = setOf(command.knowledgeNodeId),
            replayPending = false,
            keyVersion = null,
            reason = REJECT_ANSWER_KEY_DISPUTED,
        )
    }

    private fun disputeRow(
        command: AnswerKeyCorrectionCommand,
        itemId: String,
        knowledgeNodeId: String,
    ): LearnerChatEvidenceEntity = LearnerChatEvidenceEntity(
        evidence_id = "key-dispute:${command.requestId}",
        learner_id = learnerId,
        conversation_id = command.conversationId,
        knowledge_node_id = knowledgeNodeId,
        direction = LearningEvidenceDirection.NONE.name,
        weight = 0.0,
        reason_markdown = knowledgeQuizKeyDisputeReasonPrefix(itemId) + command.reasonMarkdown,
        confidence = 1.0,
        source_kind = LearnerChatEvidenceEntity.SOURCE_KIND_KEY_VOID,
        created_at_epoch_millis = command.occurredAtEpochMillis,
        rejected_reason = REJECT_ANSWER_KEY_DISPUTED,
        rejected_at_epoch_millis = command.occurredAtEpochMillis,
        corrects_evidence_id = null,
    )

    private suspend fun resolveTarget(
        command: AnswerKeyCorrectionCommand,
        rows: List<LearnerChatEvidenceEntity>,
    ): LearnerChatEvidenceEntity? {
        command.targetEvidenceId?.let { id ->
            return rows.firstOrNull { it.evidence_id == id }
        }
        val itemId = command.itemId ?: return null
        val selectedChoiceId = command.selectedChoiceId ?: return null
        return rows.asReversed().firstOrNull { row ->
            row.knowledge_node_id == command.knowledgeNodeId &&
                row.isLedgerEvidence() &&
                // 隐式定位只认**原始作答**：纠正/撤销行的 id 以目标 id 为后缀，也包含
                // `:itemId:choiceId`；若不排除，第二次报告会命中第一条 KEY_VOID 自己，
                // 撤销该更正、让原错误键重新计入掌握度（且不再写新的争议行）。
                row.source_kind == StudyDbValue.ChatEvidenceSourceKind.KNOWLEDGE_QUIZ &&
                row.corrects_evidence_id == null &&
                row.evidence_id.contains(":$itemId:$selectedChoiceId")
        }
    }

    /**
     * 这道题的真实作答是否已经以 rejected 观察行存在（例如键未核验）。
     * 只用于把“已作答后报告答案有问题”降级为争议观察，不把任意缺失 target 都当成功。
     */
    private fun hasRejectedAnswerObservation(
        command: AnswerKeyCorrectionCommand,
        rows: List<LearnerChatEvidenceEntity>,
    ): Boolean {
        val itemId = command.itemId ?: return false
        val selectedChoiceId = command.selectedChoiceId ?: return false
        return rows.any { row ->
            row.isRejected &&
                row.knowledge_node_id == command.knowledgeNodeId &&
                row.source_kind == StudyDbValue.ChatEvidenceSourceKind.KNOWLEDGE_QUIZ &&
                row.evidence_id.contains(":$itemId:$selectedChoiceId")
        }
    }

    private fun replacementVerdict(command: AnswerKeyCorrectionCommand): Boolean? {
        val newCorrect = command.newCorrectChoiceId
        val selected = command.selectedChoiceId
        if (newCorrect != null && selected != null) return selected == newCorrect
        return command.replacementCorrect
    }

    private fun LearnerChatEvidenceEntity.isLedgerEvidence(): Boolean =
        !isRejected && (
            source_kind == StudyDbValue.ChatEvidenceSourceKind.MODEL_CHAT ||
                source_kind == StudyDbValue.ChatEvidenceSourceKind.KNOWLEDGE_QUIZ ||
                LearnerChatEvidenceEntity.isCorrectionSourceKind(source_kind)
            )

    private fun correctionId(
        command: AnswerKeyCorrectionCommand,
        target: LearnerChatEvidenceEntity,
    ): String = if (command.voidTarget) {
        "key-void:${command.requestId}:${target.evidence_id}"
    } else {
        "key-correction:${command.requestId}:${target.evidence_id}"
    }

    /** 当前键版本 = 指向该目标的最后一笔更正事件的账本序列；尚无可更正的更正 = 0。 */
    private suspend fun currentKeyVersion(targetEvidenceId: String): Long =
        database.loadLearningLedger(learnerId).validPrefix
            .mapNotNull { it.event as? EvidenceCorrection }
            .filter { it.targetEvidenceId == targetEvidenceId }
            .maxOfOrNull(EvidenceCorrection::eventSequence)
            ?: 0L

    private suspend fun ledgerSequenceOf(correctionId: String): Long? =
        database.loadLearningLedger(learnerId).validPrefix
            .firstOrNull { it.event.ledgerEventId == correctionId }
            ?.event
            ?.eventSequence

    private fun rejected(reason: String) = AnswerKeyCorrectionReceipt(
        correctionId = "",
        status = AnswerKeyCorrectionStatus.REJECTED,
        targetEvidenceId = null,
        affectedKnowledgeNodeIds = emptySet(),
        replayPending = false,
        keyVersion = null,
        reason = reason,
    )
}
