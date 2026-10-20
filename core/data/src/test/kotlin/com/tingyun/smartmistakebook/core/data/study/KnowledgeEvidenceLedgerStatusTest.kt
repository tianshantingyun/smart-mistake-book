package com.tingyun.smartmistakebook.core.data.study

import com.tingyun.smartmistakebook.core.database.KnowledgeNodeSeedRecord
import com.tingyun.smartmistakebook.core.database.entity.LearnerChatEvidenceEntity
import com.tingyun.smartmistakebook.core.domain.MasteryWriteGate
import com.tingyun.smartmistakebook.core.model.TutorEvidenceDirection
import com.tingyun.smartmistakebook.core.model.TutorUnderstandingTier
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    fun `an accepted write blocked by an earlier rejected row is compensated under a fresh identity`() =
        runBlocking {
            // 身份粘性（AUD-11 ③ 附带测到、登记 KD-43(b)）：先一笔被拒的写占住 identity，
            // 条件变化后同一身份的合法写通过门。裁定：观察行保留，合法写换新身份入账，
            // 回执标记粘性（零用户参与）。
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
                "合法写换新身份真正入了账本，且回执明确标记“经历过身份粘性”",
                KnowledgeEvidenceLedgerStatus.LEDGERED_AFTER_REJECTED_IDENTITY,
                accepted(second).ledgerStatus,
            )
            val primaryId = "chat-ev:req-sticky:mastery_update:$nodeId"
            val compensatedId = knowledgeEvidenceIdAfterRejectedIdentity(primaryId, nodeId)
            assertTrue(
                "被拒观察行保留（未被覆盖/删除）",
                database.recordedChatEvidence.first { it.evidence_id == primaryId }.isRejected,
            )
            val compensated = database.recordedChatEvidence.single { it.evidence_id == compensatedId }
            assertEquals("新身份落的是正常证据", null, compensated.rejected_reason)
            assertEquals(13_000L, compensated.created_at_epoch_millis)
        }

    @Test
    fun `a compensated identity that is itself blocked stays loud instead of silently succeeding`() =
        runBlocking {
            // 反例：补偿身份也被一条被拒观察行占住（数据损坏/另一写入者）。只补一层，
            // 不无限换身份，不把“没进账本”伪装成成功。
            val database = FakeStudyDatabasePort().apply { knowledgeNodes += nodeSeed() }
            val primaryId = "chat-ev:req-double:mastery_update:$nodeId"
            database.recordedChatEvidence += rejectedRow(primaryId)
            database.recordedChatEvidence += rejectedRow(
                knowledgeEvidenceIdAfterRejectedIdentity(primaryId, nodeId),
            )
            val writer = KnowledgeEvidenceWriter(database)

            val outcome = writer.write(modelRequest(requestId = "req-double"))

            assertEquals(
                "补偿也失败时如实报 BLOCKED（不假装已入账）",
                KnowledgeEvidenceLedgerStatus.BLOCKED_BY_REJECTED_ROW,
                accepted(outcome).ledgerStatus,
            )
        }

    private fun rejectedRow(evidenceId: String) = LearnerChatEvidenceEntity(
        evidence_id = evidenceId,
        learner_id = learnerId,
        conversation_id = "tutor-session:1",
        knowledge_node_id = nodeId,
        direction = TutorEvidenceDirection.NEGATIVE.name,
        weight = 0.0,
        reason_markdown = "先前的被拒观察",
        confidence = 0.9,
        source_kind = "MODEL_CHAT",
        created_at_epoch_millis = 1_000,
        rejected_reason = MasteryWriteGate.RejectReason.KNOWLEDGE_NODE_NOT_ANCHORED.name,
        rejected_at_epoch_millis = 1_000,
    )

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
