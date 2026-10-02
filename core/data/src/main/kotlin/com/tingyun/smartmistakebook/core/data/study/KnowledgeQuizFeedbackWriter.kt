package com.tingyun.smartmistakebook.core.data.study

import com.tingyun.smartmistakebook.core.database.StudyDatabasePort
import com.tingyun.smartmistakebook.core.domain.KnowledgeQuizFeedbackResult
import com.tingyun.smartmistakebook.core.domain.knowledgeQuizMasteryVerdict
import com.tingyun.smartmistakebook.core.model.KnowledgeAnchorClass

/**
 * 客观作答通道写入的 `evidenceConfidence`。
 *
 * 为什么是 1.0（而不是模型通道的 `call.confidence`）：这条证据的方向**不由任何判断者给出**
 * ——对错由本地 `selectedChoiceId == correctChoiceId` 机械比较得出
 *（[knowledgeQuizMasteryVerdict]），不存在模型语义不确定性，也不存在"判断得准不准"的档位；
 * 1.0 表示"方向是本地事实"。D-M M4 前它是本文件里的裸字面量（无出处，与模型通道的
 * 判断置信混在同一语义位）；现在具名并可引：引用链 = 本地机械判定 → 本常量。
 */
internal const val OBJECTIVE_ANSWER_CONFIDENCE = 1.0

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
 */
internal class KnowledgeQuizFeedbackWriter(
    private val database: StudyDatabasePort,
    private val learnerId: String,
) {
    private val evidenceWriter = KnowledgeEvidenceWriter(database)

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
        // 知识点复习按"每次复习会话"计数防刷：调用方传入该次会话的 id（见
        // KnowledgeReviewSessionViewModel），否则固定 id 会把每会话配额变成终身配额，
        // 累计写满后永久拒写（审计 2026-09-09 P1）。
        require(conversationId.isNotBlank()) { "Knowledge quiz conversation id must not be blank" }
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
                reasonMarkdown = "知识点复习作答（${if (isCorrect) "答对" else "答错"}）",
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
            is KnowledgeEvidenceWriteOutcome.Accepted -> KnowledgeQuizFeedbackResult(
                isCorrect = isCorrect,
                evidenceRecorded = true,
            )
            is KnowledgeEvidenceWriteOutcome.Rejected -> KnowledgeQuizFeedbackResult(
                isCorrect = isCorrect,
                evidenceRecorded = false,
                rejectedReason = writeOutcome.reason.name,
            )
        }
    }
}
