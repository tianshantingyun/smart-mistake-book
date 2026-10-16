package com.tingyun.smartmistakebook.core.data.study

import com.tingyun.smartmistakebook.core.database.KnowledgeNodeSeedRecord
import com.tingyun.smartmistakebook.core.domain.MasteryWriteGate
import com.tingyun.smartmistakebook.core.model.TutorEvidenceDirection
import com.tingyun.smartmistakebook.core.model.TutorUnderstandingTier
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * AUD-11 ①②：写入口的两件"可观察/可分辨"纪律（JVM 层）。
 *
 * 1. **冷却只对模型通道取**（`KnowledgeEvidenceWriter.write`）：假库记下"谁问过冷却、用的哪个
 *    通道"，据此钉住客观作答通道不再取模型通道的冷却年龄——真 SQL 的分层由
 *    `KnowledgeQuizCooldownChannelScopeInstrumentedTest` 在真库上验证；
 * 2. **落库去向可读**（[KnowledgeEvidenceLedgerStatus]）：新落行 / 正常重放 / 身份粘性
 *    （既有行是被拒观察行）三档不再混成一个 `Accepted`。真库上的同一语义由
 *    `KnowledgeQuizEvidenceLedgerReceiptInstrumentedTest` 覆盖。
 */
class KnowledgeEvidenceLedgerStatusTest {

    private val learnerId = "learner:ledger-status"
    private val nodeId = "kc-monotonicity"

    @Test
    fun `a fresh accepted write reports ledgered`() = runBlocking {
        val database = anchoredFake()
        val writer = KnowledgeEvidenceWriter(database)

        val outcome = writer.write(modelRequest(requestId = "req-fresh"))

        assertEquals(KnowledgeEvidenceLedgerStatus.LEDGERED, accepted(outcome).ledgerStatus)
    }

    @Test
    fun `a retried accepted write reports replayed`() = runBlocking {
        val database = anchoredFake()
        val writer = KnowledgeEvidenceWriter(database)

        writer.write(modelRequest(requestId = "req-retry"))
        val replay = writer.write(modelRequest(requestId = "req-retry", occurredAtEpochMillis = 11_000))

        assertEquals(
            "同身份重放 = 重试（AUD-01 既有语义），不是丢写：如实报 REPLAYED",
            KnowledgeEvidenceLedgerStatus.REPLAYED,
            accepted(replay).ledgerStatus,
        )
    }

    @Test
    fun `an accepted write blocked by an earlier rejected row reports blocked`() = runBlocking {
        // 身份粘性（AUD-11 ③ 附带测到、登记 KD-43）：先一笔被拒的写占住 identity，条件变化后
        // 同一身份的合法写通过门、却在落库被 IGNORE 吞掉——账本永远不会有这笔事件。
        val database = FakeStudyDatabasePort()
        val writer = KnowledgeEvidenceWriter(database)
        val rejected = writer.write(modelRequest(requestId = "req-sticky"))
        assertEquals(
            "节点不在库里 → 未锚定拒绝（第一笔占住 identity）",
            KnowledgeEvidenceWriteOutcome.Rejected(MasteryWriteGate.RejectReason.KNOWLEDGE_NODE_NOT_ANCHORED),
            rejected,
        )

        // 条件变化（节点被导入 / 冷却过期都算）：同一身份这一次通过门。
        database.knowledgeNodes += nodeSeed()
        val second = writer.write(modelRequest(requestId = "req-sticky", occurredAtEpochMillis = 13_000))

        assertEquals(
            "门放行了（不是拒绝），但这一笔进不了账本——这一档必须单独可读",
            KnowledgeEvidenceLedgerStatus.BLOCKED_BY_REJECTED_ROW,
            accepted(second).ledgerStatus,
        )
        // "既有被拒行不被改写 / 库里仍只有一行"是**真库**上的断言（本假件是记录器，
        // 逐条追加、不模拟 IGNORE 的存储后果）：见
        // `KnowledgeQuizCooldownChannelScopeInstrumentedTest.anAcceptedWriteBlockedByARejectedRowIsReportedAndStaysOutOfTheLedger`。
    }

    @Test
    fun `only the model channel asks for the same-kc cooldown`() = runBlocking {
        val database = anchoredFake()
        val writer = KnowledgeEvidenceWriter(database)

        writer.write(modelRequest(requestId = "req-model"))
        val quiz = writer.write(quizRequest(requestId = "req-quiz"))

        assertEquals(
            "冷却只该按模型通道问一次（客观作答通道按自身幂等身份防重）",
            listOf(nodeId to "MODEL_CHAT"),
            database.kcCooldownQueries,
        )
        assertEquals(KnowledgeEvidenceLedgerStatus.LEDGERED, accepted(quiz).ledgerStatus)
    }

    private fun accepted(outcome: KnowledgeEvidenceWriteOutcome): KnowledgeEvidenceWriteOutcome.Accepted =
        outcome as? KnowledgeEvidenceWriteOutcome.Accepted
            ?: error("expected an accepted write, got $outcome")

    private fun anchoredFake() = FakeStudyDatabasePort().apply { knowledgeNodes += nodeSeed() }

    private fun nodeSeed() = KnowledgeNodeSeedRecord(
        knowledgeNodeId = nodeId,
        stableCode = "math.function.monotonicity",
        subject = "MATH",
        displayName = "函数单调性",
        parentKnowledgeNodeId = null,
        taxonomyVersion = "cn-highschool-m1-v1",
        createdAtEpochMillis = 1_000,
        canonicalName = "函数单调性",
    )

    private fun modelRequest(
        requestId: String,
        occurredAtEpochMillis: Long = 10_000,
    ) = KnowledgeEvidenceWriteRequest(
        learnerId = learnerId,
        channel = KnowledgeEvidenceChannel.MODEL_CHAT,
        namespace = requestId,
        discriminator = "mastery_update",
        conversationId = "tutor-session:1",
        knowledgeNodeId = nodeId,
        direction = TutorEvidenceDirection.NEGATIVE,
        understanding = TutorUnderstandingTier.CONFIDENT,
        evidenceConfidence = 0.9,
        reasonMarkdown = "学生这一步没做出来",
        anchorClass = MasteryWriteGate.ANCHOR_CLASS_CONFIRMED,
        occurredAtEpochMillis = occurredAtEpochMillis,
    )

    private fun quizRequest(
        requestId: String,
        occurredAtEpochMillis: Long = 21_000,
    ) = KnowledgeEvidenceWriteRequest(
        learnerId = learnerId,
        channel = KnowledgeEvidenceChannel.KNOWLEDGE_QUIZ,
        namespace = requestId,
        conversationId = "knowledge-quiz-review:1700000000000",
        knowledgeNodeId = nodeId,
        direction = TutorEvidenceDirection.POSITIVE,
        understanding = TutorUnderstandingTier.CONFIDENT,
        evidenceConfidence = OBJECTIVE_ANSWER_CONFIDENCE,
        reasonMarkdown = "知识点复习作答（答对）",
        anchorClass = MasteryWriteGate.ANCHOR_CLASS_CONFIRMED,
        hasObjectiveSupport = true,
        occurredAtEpochMillis = occurredAtEpochMillis,
    )
}
