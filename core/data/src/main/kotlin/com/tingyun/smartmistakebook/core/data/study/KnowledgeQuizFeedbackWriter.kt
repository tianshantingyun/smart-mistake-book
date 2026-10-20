package com.tingyun.smartmistakebook.core.data.study

import com.tingyun.smartmistakebook.core.database.LearningLedgerReadStatus
import com.tingyun.smartmistakebook.core.database.StudyDatabasePort
import com.tingyun.smartmistakebook.core.database.StudyDbValue
import com.tingyun.smartmistakebook.core.database.entity.LearnerChatEvidenceEntity
import com.tingyun.smartmistakebook.core.domain.KnowledgeQuizFeedbackResult
import com.tingyun.smartmistakebook.core.domain.MasteryWriteGate
import com.tingyun.smartmistakebook.core.domain.knowledgeQuizMasteryVerdict
import com.tingyun.smartmistakebook.core.model.ChatEvidenceSubmitted
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
        // KD-43：读侧排序与账本序一致（见 [latestCorrectedKey]）；账本读不出完整前缀时
        // 显式退化为行时间序（该降级在函数 KDoc 里写明，不再默默分叉）。
        val correctedKey = snapshotItem?.let {
            latestCorrectedKey(
                rows = existingRows,
                knowledgeNodeId = knowledgeNodeId,
                itemId = it.id,
                // 没有键替代行时不读整条账本（绝大多数作答都走这条）；有替代行才需要
                // 账本序，读不完整时函数内部显式降级为时间序。
                ledgerOrder = if (existingRows.hasKeyCorrectionEvidence(it.id)) {
                    database.correctionLedgerOrder(learnerId)
                } else {
                    null
                },
                // 只认本会话的替代键：`itemId` 是常量，跨会话套用旧题键会给新题误判。
                sessionId = sessionId,
            )
        }
        // UNKNOWN 键被用户改正后，这次作答已经以**新身份**按新键重判并入了账本
        // （见 AnswerKeyCorrectionWriter.recordUserAssertedKeyCorrection）。同一身份的
        // 重试不得再补一笔：直接按已有事实回报 recorded。
        val keyFixEvidenceId = if (correctedKey != null && correctChoiceId == correctedKey) {
            snapshotItem?.let { item ->
                rejectedAnswerObservationId(
                    rows = existingRows,
                    knowledgeNodeId = knowledgeNodeId,
                    itemId = item.id,
                    selectedChoiceId = selectedChoiceId,
                    conversationId = conversationId,
                )?.let { observationId ->
                    knowledgeQuizKeyFixEvidenceId(
                        rejectedAnswerEvidenceId = observationId,
                        knowledgeNodeId = knowledgeNodeId,
                        newCorrectChoiceId = correctedKey,
                    )
                }
            }
        } else {
            null
        }
        if (keyFixEvidenceId != null && existingRows.any { !it.isRejected && it.evidence_id == keyFixEvidenceId }) {
            return KnowledgeQuizFeedbackResult(
                isCorrect = isCorrect,
                evidenceRecorded = true,
            )
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
            is KnowledgeEvidenceWriteOutcome.Accepted -> when (writeOutcome.ledgerStatus) {
                KnowledgeEvidenceLedgerStatus.BLOCKED_BY_REJECTED_ROW ->
                    // 写入口已自动补偿一次；补偿身份也被占住才会到这里。不改写历史行、
                    // 不凭重试伪造事实——如实回报"没有记录"。
                    KnowledgeQuizFeedbackResult(
                        isCorrect = isCorrect,
                        evidenceRecorded = false,
                        rejectedReason = "EVIDENCE_IDENTITY_STICKY",
                    )

                KnowledgeEvidenceLedgerStatus.LEDGERED,
                KnowledgeEvidenceLedgerStatus.REPLAYED,
                // KD-43(b)：合法写已换新身份入账；观察行保留、回执标记粘性。
                KnowledgeEvidenceLedgerStatus.LEDGERED_AFTER_REJECTED_IDENTITY,
                KnowledgeEvidenceLedgerStatus.REPLAYED_AFTER_REJECTED_IDENTITY,
                -> KnowledgeQuizFeedbackResult(
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

/**
 * 用户改正答案键后，这次作答以**新身份**重判入账时使用的证据 id（KD-43 / UNKNOWN 键 REPLACE）。
 *
 * 为什么挂在被拒观察行的 id 上：被拒观察行保留原样（它记录的是"当时键未核验"的真实状态），
 * 合法事实必须换一个新身份才能进账本；用 `(观察行 id, 新键)` 派生让同一命令重试恒落同一 id
 * （幂等），换一个键则是另一笔事实。
 */
internal fun knowledgeQuizKeyFixEvidenceId(
    rejectedAnswerEvidenceId: String,
    knowledgeNodeId: String,
    newCorrectChoiceId: String,
): String = insertBeforeTrailingNodeSegment(
    evidenceId = rejectedAnswerEvidenceId,
    knowledgeNodeId = knowledgeNodeId,
    insertedSegment = "key-fix:$newCorrectChoiceId",
)

/**
 * 同一题面最近一条**未被撤销**的有效替代键；没有 = null。
 *
 * 来源有两类，都按**账本序**（[ledgerOrder]）取最后一条：
 * 1. `KEY_CORRECTION` 更正行（针对已接受证据的定向 REPLACE）；
 * 2. UNKNOWN 键的"改正答案键"写入的**已接受作答行**（理由带同一机器前缀，KD-43）。
 *
 * [ledgerOrder] = 证据 id → 账本事件序列（只含已接受账本事件）。**降级规则（显式）**：
 * 为 null（账本读不完整/不可用）或任一候选行不在映射里时，退回
 * `created_at_epoch_millis`、再以行序作决定性 tie-break——这与旧行为一致，只在
 * 同毫秒/时钟回拨时才可能与账本序分叉；调用方应如实知道这是降级路径，不要把它当成
 * 账本序保证。
 *
 * [sessionId] 非 null 时只取**该会话**的键替代。题面快照按 `(learner, 会话, 知识点)` 存，
 * 但 `itemId` 是常量：若不按会话隔离，隔日新生成的题面会继承旧题的键（新题不含该选项时
 * 直接抛异常；含该选项时用旧题的键判分）。会话无法判定的替代行在过滤模式下**不生效**
 * （fail-closed），宁可退回快照键，也不跨会话套用一个来历不明的键。
 */
internal fun latestCorrectedKey(
    rows: List<LearnerChatEvidenceEntity>,
    knowledgeNodeId: String,
    itemId: String,
    ledgerOrder: Map<String, Long>? = null,
    sessionId: Long? = null,
): String? {
    val rowsById = rows.associateBy { it.evidence_id }
    fun sessionOf(row: LearnerChatEvidenceEntity): Long? = when (row.source_kind) {
        // 定向纠正/撤销的 `conversation_id` 可能是命令侧的文本标识；它纠正的**目标证据**
        // 才属于某场复习会话，沿 corrects_evidence_id 取目标的会话。
        LearnerChatEvidenceEntity.SOURCE_KIND_KEY_CORRECTION,
        LearnerChatEvidenceEntity.SOURCE_KIND_KEY_VOID ->
            row.corrects_evidence_id
                ?.let(rowsById::get)
                ?.let { knowledgeQuizSessionIdOf(it.conversation_id) }

        else -> knowledgeQuizSessionIdOf(row.conversation_id)
    }
    val voidedCorrections = rows.asSequence()
        .filter {
            it.source_kind == LearnerChatEvidenceEntity.SOURCE_KIND_KEY_VOID && !it.isRejected
        }
        .mapNotNull(LearnerChatEvidenceEntity::corrects_evidence_id)
        .toSet()
    val correctionPrefix = "answer-key-correction:$itemId:"
    val candidates = rows.mapIndexedNotNull { index, row ->
        if (row.isRejected) return@mapIndexedNotNull null
        if (row.knowledge_node_id != knowledgeNodeId) return@mapIndexedNotNull null
        if (row.evidence_id in voidedCorrections) return@mapIndexedNotNull null
        if (sessionId != null && sessionOf(row) != sessionId) return@mapIndexedNotNull null
        val isDirectedCorrection =
            row.source_kind == LearnerChatEvidenceEntity.SOURCE_KIND_KEY_CORRECTION
        val isUserAssertedAnswer =
            row.source_kind == StudyDbValue.ChatEvidenceSourceKind.KNOWLEDGE_QUIZ &&
                row.reason_markdown.startsWith(correctionPrefix)
        if (!isDirectedCorrection && !isUserAssertedAnswer) return@mapIndexedNotNull null
        val correctedKey = knowledgeQuizKeyCorrectionNewCorrectChoice(row.reason_markdown, itemId)
            ?: return@mapIndexedNotNull null
        CorrectedKeyCandidate(row = row, rowIndex = index, correctedKey = correctedKey)
    }
    if (candidates.isEmpty()) return null
    val ledgerOrdered = ledgerOrder?.takeIf { order ->
        candidates.all { it.row.evidence_id in order }
    }
    val latest = if (ledgerOrdered != null) {
        candidates.maxByOrNull { ledgerOrdered.getValue(it.row.evidence_id) }
    } else {
        candidates.maxWithOrNull(
            compareBy<CorrectedKeyCandidate> { it.row.created_at_epoch_millis }
                .thenBy { it.rowIndex },
        )
    }
    return latest?.correctedKey
}

private data class CorrectedKeyCandidate(
    val row: LearnerChatEvidenceEntity,
    val rowIndex: Int,
    val correctedKey: String,
)

/**
 * 证据 id → 账本序列。只接受**完整**的账本前缀；GAP/CONFLICT 或读取失败返回 null，
 * 由 [latestCorrectedKey] 走显式降级（时间序）。
 */
internal suspend fun StudyDatabasePort.correctionLedgerOrder(
    learnerId: String,
): Map<String, Long>? {
    val ledger = runCatching { loadLearningLedger(learnerId) }.getOrNull() ?: return null
    if (ledger.status != LearningLedgerReadStatus.COMPLETE) return null
    return ledger.validPrefix.mapNotNull { persisted ->
        when (val event = persisted.event) {
            is EvidenceCorrection -> event.ledgerEventId to event.eventSequence
            is ChatEvidenceSubmitted -> event.ledgerEventId to event.eventSequence
            else -> null
        }
    }.toMap()
}

internal fun List<LearnerChatEvidenceEntity>.hasKeyCorrectionEvidence(
    itemId: String,
): Boolean = any { row ->
    if (row.isRejected) return@any false
    val directedCorrection =
        row.source_kind == LearnerChatEvidenceEntity.SOURCE_KIND_KEY_CORRECTION
    val userAssertedAnswer =
        row.source_kind == StudyDbValue.ChatEvidenceSourceKind.KNOWLEDGE_QUIZ &&
            row.reason_markdown.startsWith("answer-key-correction:$itemId:")
    directedCorrection || userAssertedAnswer
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

/**
 * 这道题这次选择的 **UNKNOWN 键**被拒作答观察行 id（KD-43）。只认原始作答通道
 * （`KNOWLEDGE_QUIZ`）且拒因为 `ANSWER_KEY_UNVERIFIED`；没有 = null。
 *
 * [conversationId] 非 null 时只认**本次会话**的观察行。`itemId` 是常量而题面/选项按会话
 * 重新生成：不加会话分量时，下一场复习里同一个 `selectedChoiceId` 会命中上一场的观察行，
 * 进而命中上一场已入账的 key-fix 证据 id，把这次的合法作答静默当成"已记录"。
 */
internal fun rejectedAnswerObservationId(
    rows: List<LearnerChatEvidenceEntity>,
    knowledgeNodeId: String,
    itemId: String,
    selectedChoiceId: String,
    conversationId: String? = null,
): String? = rows.asReversed().firstOrNull { row ->
    row.isRejected &&
        row.rejected_reason == REJECT_ANSWER_KEY_UNVERIFIED &&
        row.knowledge_node_id == knowledgeNodeId &&
        row.source_kind == StudyDbValue.ChatEvidenceSourceKind.KNOWLEDGE_QUIZ &&
        (conversationId == null || row.conversation_id == conversationId) &&
        row.evidence_id.contains(":$itemId:$selectedChoiceId")
}?.evidence_id

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
    /**
     * 可选的键版本 CAS：
     * - 有账本目标（定向纠正）时 = 上一次指向该目标的更正事件账本序列（0 = 尚无更正）；
     * - UNKNOWN 键的 REPLACE（无账本目标）时 = **题面级**键版本，即该题所有键替代事件
     *   （更正行/用户断言作答行）账本序列的最大值（0 = 尚无替代）。
     */
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
    /**
     * 被纠正的已接受证据 id；UNKNOWN 键的 REPLACE 没有已接受目标，
     * 这里指向**被保留的被拒作答观察行**（说明重判的是哪一笔作答）。
     */
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
 *
 * **UNKNOWN 键的 REPLACE（KD-43）**：键未核验时账本里没有可纠正的已接受作答，只有一条
 * `ANSWER_KEY_UNVERIFIED` 观察行。此时 `voidTarget = false` 且带
 * `itemId/newCorrectChoiceId/selectedChoiceId` 会走 [recordUserAssertedKeyCorrection]：
 * 观察行保留，学生这次作答按用户断言的新键重判成一条**新身份**的普通证据入账（理由带
 * `answer-key-correction:<itemId>:<新键>:` 前缀，后续作答据此校验），投影/重放因此看到
 * 真实结果。这是 UI“改正答案键”对话框的落点。
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

        // UNKNOWN 键的“改正答案键”（REPLACE）：账本里可能没有可纠正的已接受作答——
        // 当前这次作答只落成 rejected 观察行（同题面更早会话可能另有已接受行）。
        val unverifiedObservation = if (!command.voidTarget) {
            findUnverifiedAnswerObservation(command, rows)
        } else {
            null
        }
        val target = resolveTarget(command, rows)
        if (target == null) {
            // 保留观察行，按用户断言的新键把这次作答以新身份重判入账（KD-43 / PRD-02）。
            if (unverifiedObservation != null) {
                return recordUserAssertedKeyCorrection(command, unverifiedObservation)
            }
            // 已作答但该作答只落成 rejected 观察行（最常见：题面键 UNKNOWN）时，账本里
            // 没有可撤销的已接受证据。UI 的“答案有问题”仍然是真实反馈：退回中性争议
            // 观察行（暂停该键计分、保留真实选择），而不是把反馈丢成 TARGET_NOT_FOUND。
            if (command.voidTarget && hasRejectedAnswerObservation(command, rows)) {
                return recordDisputeObservation(command)
            }
            return rejected("ANSWER_KEY_TARGET_NOT_FOUND")
        }
        // 隐式定位到了更早会话的已接受作答，而**当前**这次作答是更新的 rejected 观察行：
        // 改正的是学生眼前这一笔，不是旧会话那一笔。
        if (command.targetEvidenceId == null && unverifiedObservation != null) {
            val targetIndex = rows.indexOfFirst { it.evidence_id == target.evidence_id }
            val observationIndex = rows.indexOfFirst {
                it.evidence_id == unverifiedObservation.evidence_id
            }
            if (observationIndex > targetIndex) {
                return recordUserAssertedKeyCorrection(command, unverifiedObservation)
            }
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
     * UNKNOWN 键的“改正答案键”（KD-43）：账本里没有可 REPLACE 的已接受证据，因此不改写
     * 任何历史行，而是**保留**那条 `ANSWER_KEY_UNVERIFIED` 观察行，按用户断言的新键把
     * 学生这次真实作答**重判**为一笔新身份的有效证据（`source_kind = KNOWLEDGE_QUIZ`，
     * 理由带 `answer-key-correction:<itemId>:<新键>:` 机器前缀）。
     *
     * 为什么不是一条 `KEY_CORRECTION` 事件：账本更正要求目标必须是更早的**已接受**事件，
     * 而被拒观察行按设计永不进账本；伪造一个目标会让重放拒绝整条账本。把“新键 + 重判后的
     * 这笔作答”作为普通证据入账，既让投影/重放看到真实结果，也让后续读取
     * （[latestCorrectedKey]）按账本序拿到新键。
     *
     * 幂等：新身份 = `(观察行 id, 新键)`；同一命令重试 REPLAYED，换一个键是另一笔事实。
     * `expectedKeyVersion` 走**题面级**键版本 CAS（该题所有键替代事件的账本序列最大值；
     * 0 = 尚无替代），迟到请求显式 CONFLICT。
     */
    private suspend fun recordUserAssertedKeyCorrection(
        command: AnswerKeyCorrectionCommand,
        observation: LearnerChatEvidenceEntity,
    ): AnswerKeyCorrectionReceipt {
        val itemId = command.itemId ?: return rejected("ANSWER_KEY_CORRECTION_ITEM_REQUIRED")
        if (itemId.isBlank()) return rejected("ANSWER_KEY_CORRECTION_ITEM_REQUIRED")
        val newCorrectChoiceId = command.newCorrectChoiceId
            ?: return rejected("ANSWER_KEY_REPLACEMENT_KEY_REQUIRED")
        if (newCorrectChoiceId.isBlank()) return rejected("ANSWER_KEY_REPLACEMENT_KEY_REQUIRED")
        val selectedChoiceId = command.selectedChoiceId
            ?: return rejected("ANSWER_KEY_REPLACEMENT_ANSWER_REQUIRED")

        val evidenceId = knowledgeQuizKeyFixEvidenceId(
            rejectedAnswerEvidenceId = observation.evidence_id,
            knowledgeNodeId = observation.knowledge_node_id,
            newCorrectChoiceId = newCorrectChoiceId,
        )
        val expected = command.expectedKeyVersion
        if (expected != null) {
            val current = currentKeyVersionForItem(itemId)
            if (current != expected) {
                return AnswerKeyCorrectionReceipt(
                    correctionId = evidenceId,
                    status = AnswerKeyCorrectionStatus.CONFLICT,
                    targetEvidenceId = observation.evidence_id,
                    affectedKnowledgeNodeIds = setOf(observation.knowledge_node_id),
                    replayPending = false,
                    keyVersion = current,
                    reason = "ANSWER_KEY_VERSION_CONFLICT",
                )
            }
        }

        // 新键必须是该题面真实存在的选项（与定向 REPLACE 同一判据）；题面读不回时不猜。
        // 这条写入绕过了证据门控（纠正路径的既有设计），所以必须自己守住两个前提：
        // 题面读得回、知识点真实锚定——否则就会借“改键”把证据写进不存在的节点。
        val sessionId = knowledgeQuizSessionIdOf(observation.conversation_id)
            ?: knowledgeQuizSessionIdOf(command.conversationId)
        val storedItem = sessionId?.let {
            KnowledgeQuizItemStore(database, learnerId)
                .readSnapshot(it, observation.knowledge_node_id)
                ?.item
        } ?: return rejected("ANSWER_KEY_ITEM_UNREADABLE")
        if (storedItem.choices.none { it.id == newCorrectChoiceId }) {
            return rejected("ANSWER_KEY_CHOICE_NOT_IN_ITEM")
        }
        if (database.readKnowledgeNodesByIds(setOf(observation.knowledge_node_id)).isEmpty()) {
            return rejected("ANSWER_KEY_NODE_NOT_ANCHORED")
        }

        val rejudgedCorrect = selectedChoiceId == newCorrectChoiceId
        val verdict = knowledgeQuizMasteryVerdict(rejudgedCorrect)
        val entry = LearnerChatEvidenceEntity(
            evidence_id = evidenceId,
            learner_id = learnerId,
            conversation_id = observation.conversation_id,
            knowledge_node_id = observation.knowledge_node_id,
            direction = verdict.direction.name,
            weight = if (rejudgedCorrect) {
                MasteryWriteGate.positiveWeightFor(verdict.understanding)
            } else {
                MasteryWriteGate.negativeWeight()
            },
            reason_markdown = knowledgeQuizKeyCorrectionReasonPrefix(itemId, newCorrectChoiceId) +
                command.reasonMarkdown,
            confidence = OBJECTIVE_ANSWER_CONFIDENCE,
            source_kind = StudyDbValue.ChatEvidenceSourceKind.KNOWLEDGE_QUIZ,
            created_at_epoch_millis = command.occurredAtEpochMillis,
            anchor_class = MasteryWriteGate.ANCHOR_CLASS_CONFIRMED,
        )
        val receipt = database.recordChatEvidence(listOf(entry))
        val recorded = evidenceId in receipt.insertedAcceptedEvidenceIds
        val replayed = evidenceId in receipt.replayedAcceptedEvidenceIds
        if (!recorded && !replayed) {
            return rejected("ANSWER_KEY_CORRECTION_NOT_LEDGERED").copy(
                correctionId = evidenceId,
                targetEvidenceId = observation.evidence_id,
            )
        }
        return AnswerKeyCorrectionReceipt(
            correctionId = evidenceId,
            status = if (recorded) {
                AnswerKeyCorrectionStatus.RECORDED
            } else {
                AnswerKeyCorrectionStatus.REPLAYED
            },
            // 指向被保留的观察行：它记录了“当时键未核验”的真实状态，永不被改写。
            targetEvidenceId = observation.evidence_id,
            affectedKnowledgeNodeIds = setOf(observation.knowledge_node_id),
            replayPending = recorded,
            keyVersion = ledgerSequenceOf(evidenceId),
        )
    }

    /**
     * 题面级键版本 = 该题所有键替代事件（更正行/用户断言作答行）的账本序列最大值。
     *
     * 公开给 UI 作为“改正答案键”的 CAS 期望值（T7）：Root 在提交前取当前值并传入
     * `expectedKeyVersion`，迟到请求因此显式 CONFLICT，而不是静默覆盖更新的改正。
     */
    suspend fun currentKeyVersionForItem(itemId: String): Long {
        val prefix = "answer-key-correction:$itemId:"
        return database.loadLearningLedger(learnerId).validPrefix
            .mapNotNull { persisted ->
                when (val event = persisted.event) {
                    is EvidenceCorrection ->
                        event.eventSequence.takeIf { event.reasonMarkdown.startsWith(prefix) }

                    is ChatEvidenceSubmitted ->
                        event.eventSequence.takeIf { event.reasonMarkdown.startsWith(prefix) }

                    else -> null
                }
            }
            .maxOrNull()
            ?: 0L
    }

    /**
     * UNKNOWN 键留下的被拒作答观察行（键信任门把作答保留成观察、没有账本目标）。
     * 只认 `ANSWER_KEY_UNVERIFIED`：其它拒因（未锚定/配额/争议）不允许借“改键”绕过门控。
     */
    private fun findUnverifiedAnswerObservation(
        command: AnswerKeyCorrectionCommand,
        rows: List<LearnerChatEvidenceEntity>,
    ): LearnerChatEvidenceEntity? {
        val itemId = command.itemId ?: return null
        val selectedChoiceId = command.selectedChoiceId ?: return null
        // 生产入口把当前复习会话带进 command.conversationId；题面/选项按会话重新生成，
        // 因此只认本会话的观察行（旧会话的同一选项不能当作"这次作答"被重判）。
        // 无法解析会话的旧调用（文本命令 id）退回不按会话过滤的既有行为。
        val targetSession = knowledgeQuizSessionIdOf(command.conversationId)
        return rows.asReversed().firstOrNull { row ->
            row.isRejected &&
                row.rejected_reason == REJECT_ANSWER_KEY_UNVERIFIED &&
                row.knowledge_node_id == command.knowledgeNodeId &&
                row.source_kind == StudyDbValue.ChatEvidenceSourceKind.KNOWLEDGE_QUIZ &&
                (targetSession == null ||
                    knowledgeQuizSessionIdOf(row.conversation_id) == targetSession) &&
                row.evidence_id.contains(":$itemId:$selectedChoiceId")
        }
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
        val keyFixPrefix = "answer-key-correction:$itemId:"
        return rows.asReversed().firstOrNull { row ->
            row.knowledge_node_id == command.knowledgeNodeId &&
                row.isLedgerEvidence() &&
                // 隐式定位只认**原始作答**：纠正/撤销行的 id 以目标 id 为后缀，也包含
                // `:itemId:choiceId`；若不排除，第二次报告会命中第一条 KEY_VOID 自己，
                // 撤销该更正、让原错误键重新计入掌握度（且不再写新的争议行）。
                row.source_kind == StudyDbValue.ChatEvidenceSourceKind.KNOWLEDGE_QUIZ &&
                row.corrects_evidence_id == null &&
                // UNKNOWN 键改正写入的 key-fix 行也是 KNOWLEDGE_QUIZ，且 id 里带
                // `:itemId:choiceId`；它不是原始作答（是上一次改正的结果），不能当成本次
                // 改正的目标，否则同一改正命令重试会再写一条 KEY_CORRECTION 而不是 REPLAY。
                !row.reason_markdown.startsWith(keyFixPrefix) &&
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
