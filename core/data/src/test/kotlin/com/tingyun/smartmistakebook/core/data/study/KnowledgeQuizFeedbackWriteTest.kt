package com.tingyun.smartmistakebook.core.data.study

import com.tingyun.smartmistakebook.core.database.KnowledgeNodeSeedRecord
import com.tingyun.smartmistakebook.core.domain.MasteryWriteGate
import com.tingyun.smartmistakebook.core.model.TutorAssessmentItem
import com.tingyun.smartmistakebook.core.model.TutorChoice
import com.tingyun.smartmistakebook.core.model.TutorEvidenceDirection
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 知识点复习作答回写（spec dual-review-entry §3.4）：判答（客观对错）→ 走本地
 * [com.tingyun.smartmistakebook.core.domain.MasteryWriteGate] 门控 → Accepted 才把
 * 掌握度证据写入 chat-evidence（进而经 ledger/投影更新知识点掌握态，影响后续错题排程）。
 * 消灭的失败：回写链没有测试——判定与门控接错、方向写反、或未锚定节点也落库都无人发现。
 */
class KnowledgeQuizFeedbackWriteTest {

    private val learnerId = RoomBackedStudyExperienceRepository.DEFAULT_LEARNER_ID
    private val sessionId = 5_000L
    private val conversationId = "knowledge-quiz-review:$sessionId"

    private fun repository(database: FakeStudyDatabasePort): RoomBackedStudyExperienceRepository {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        return RoomBackedStudyExperienceRepository(
            database = database,
            applicationScope = scope,
            clock = Clock.fixed(Instant.parse("2026-01-02T08:00:00Z"), ZoneId.of("Asia/Shanghai")),
            studyZoneId = ZoneId.of("Asia/Shanghai"),
        )
    }

    /**
     * PRD-02：把本次会话/知识点的题面键标记为**独立核验**（`VERIFIED`）——本文件的重点是
     * 既有门控与权重；未核验键的保守行为由 `AnswerKeyCorrectionInstrumentedTest` 覆盖。
     */
    private suspend fun FakeStudyDatabasePort.trustQuizKey(
        sessionId: Long,
        nodeId: String,
        correctChoiceId: String = "A",
    ) {
        val item = TutorAssessmentItem(
            id = "knowledge-quiz:item:$nodeId",
            stemMarkdown = "题干",
            choices = listOf(
                TutorChoice(id = "A", markdown = "选项 A"),
                TutorChoice(id = "B", markdown = "选项 B"),
            ),
            correctChoiceId = correctChoiceId,
            knowledgeNodeIds = setOf(nodeId),
        )
        KnowledgeQuizItemStore(this, learnerId).save(sessionId, nodeId, item, 1_000)
        val snapshotId = knowledgeQuizItemSnapshotId(learnerId, sessionId, nodeId)
        val record = requireNotNull(findAssessmentItemSnapshot(snapshotId))
        overwriteAssessmentItemSnapshot(record.copy(verificationStatus = "VERIFIED"))
    }

    private suspend fun anchoredDatabase(nodeId: String = "kc-monotonicity"): FakeStudyDatabasePort {
        val database = FakeStudyDatabasePort()
        database.knowledgeNodes += KnowledgeNodeSeedRecord(
            knowledgeNodeId = nodeId,
            stableCode = "math.function.monotonicity",
            subject = "MATH",
            displayName = "函数单调性",
            parentKnowledgeNodeId = null,
            taxonomyVersion = "cn-highschool-m1-v1",
            createdAtEpochMillis = 1_000,
            canonicalName = "函数单调性",
        )
        database.trustQuizKey(sessionId, nodeId)
        return database
    }

    @Test
    fun correctAnswerWritesPositiveAnchoredEvidence() = runBlocking {
        val database = anchoredDatabase()
        val repository = repository(database)

        try {
            val result = repository.submitKnowledgeQuizFeedback(
                requestId = "req-correct",
                knowledgeNodeId = "kc-monotonicity",
                correctChoiceId = "A",
                selectedChoiceId = "A",
                occurredAtEpochMillis = 5_000,
                conversationId = conversationId,
            )

            assertTrue(result.isCorrect)
            assertTrue(result.evidenceRecorded)
            val evidence = database.recordedChatEvidence.single()
            assertEquals(learnerId, evidence.learner_id)
            assertEquals("kc-monotonicity", evidence.knowledge_node_id)
            assertEquals(TutorEvidenceDirection.POSITIVE.name, evidence.direction)
            assertEquals("KNOWLEDGE_QUIZ", evidence.source_kind)
            assertEquals(conversationId, evidence.conversation_id)
            assertEquals("knowledge-quiz:req-correct:kc-monotonicity", evidence.evidence_id)
            assertTrue(evidence.weight > 0.0)
            assertEquals(5_000L, evidence.created_at_epoch_millis)
            // D-M M4：测验通道补合法 anchor——本次测验直接选定的知识点，CONFIRMED 全权重
            //（D9 半权只对非 CONFIRMED 生效；权重与 M4 前逐位一致）。
            assertEquals("CONFIRMED", evidence.anchor_class)
            // D-M M4：置信不再写裸字面量，取具名常量（本地机械判定对错）。
            assertEquals(OBJECTIVE_ANSWER_CONFIDENCE, evidence.confidence, 1e-9)
            // 正常写入不带拒绝标记（拒绝行只作审计、不进投影）。
            assertEquals(null, evidence.rejected_reason)
        } finally {
            repository.close()
        }
    }

    @Test
    fun wrongAnswerWritesNegativeEvidence() = runBlocking {
        val database = anchoredDatabase()
        val repository = repository(database)

        try {
            val result = repository.submitKnowledgeQuizFeedback(
                requestId = "req-wrong",
                knowledgeNodeId = "kc-monotonicity",
                correctChoiceId = "A",
                selectedChoiceId = "B",
                occurredAtEpochMillis = 6_000,
                conversationId = conversationId,
            )

            assertFalse(result.isCorrect)
            assertTrue(result.evidenceRecorded)
            val evidence = database.recordedChatEvidence.single()
            assertEquals(TutorEvidenceDirection.NEGATIVE.name, evidence.direction)
            assertEquals("knowledge-quiz:req-wrong:kc-monotonicity", evidence.evidence_id)
        } finally {
            repository.close()
        }
    }

    @Test
    fun unanchoredKnowledgeNodeIsRejectedWithAnObservationRow() = runBlocking {
        // 知识库里不存在该节点 → 门控 KNOWLEDGE_NODE_NOT_ANCHORED 拒绝：不进投影，
        // 防止模型/调用方凭臆造节点污染掌握度。D-M M4：被拒 ≠ 什么都不落——与讲题
        // 通道一致，落一条 rejected 观察行（审计/校准读得到"哪一档来路被拒得最多"）。
        val database = FakeStudyDatabasePort()
        // 题面键独立核验（否则先被键信任门挡成 ANSWER_KEY_UNVERIFIED，测不到“未锚定”这一支）。
        database.trustQuizKey(sessionId, "kc-not-in-library")
        val repository = repository(database)

        try {
            val result = repository.submitKnowledgeQuizFeedback(
                requestId = "req-unanchored",
                knowledgeNodeId = "kc-not-in-library",
                correctChoiceId = "A",
                selectedChoiceId = "A",
                occurredAtEpochMillis = 7_000,
                conversationId = conversationId,
            )

            assertTrue(result.isCorrect)
            assertFalse(result.evidenceRecorded)
            assertNotNull(result.rejectedReason)
            val rejected = database.recordedChatEvidence.single()
            assertEquals("kc-not-in-library", rejected.knowledge_node_id)
            assertEquals("KNOWLEDGE_NODE_NOT_ANCHORED", rejected.rejected_reason)
            assertEquals(7_000L, rejected.rejected_at_epoch_millis)
            assertEquals(0.0, rejected.weight, 1e-9)
            assertEquals("CONFIRMED", rejected.anchor_class)
        } finally {
            repository.close()
        }
    }

    @Test
    fun evidenceIdIsDeterministicSoRetriesDoNotDuplicate() = runBlocking {
        // 同一 requestId + 节点 → 同一 evidence_id（DB 侧幂等键），重试不会写重复证据。
        val database = anchoredDatabase()
        val repository = repository(database)

        try {
            repeat(2) { attempt ->
                repository.submitKnowledgeQuizFeedback(
                    requestId = "req-retry",
                    knowledgeNodeId = "kc-monotonicity",
                    correctChoiceId = "A",
                    selectedChoiceId = "A",
                    occurredAtEpochMillis = 8_000L + attempt,
                    conversationId = conversationId,
                )
            }
            val ids = database.recordedChatEvidence.map { it.evidence_id }.distinct()
            assertEquals(listOf("knowledge-quiz:req-retry:kc-monotonicity"), ids)
        } finally {
            repository.close()
        }
    }

    /**
     * KD-43（UNKNOWN 键 → REPLACE）：模型生成的键未核验时，作答只落 rejected 观察行；
     * 学生用“改正答案键”断言新键后，观察行保留，这次作答按新键重判成一条**新身份**的
     * 有效证据入账（可被投影/重放消费），同一身份重试不再补第二笔。
     */
    @Test
    fun anUnverifiedKeyCorrectedByTheUserRejudgesTheRejectedAnswerExactlyOnce() = runBlocking {
        val nodeId = "kc-unverified"
        val database = FakeStudyDatabasePort()
        database.knowledgeNodes += KnowledgeNodeSeedRecord(
            knowledgeNodeId = nodeId,
            stableCode = "math.function.monotonicity",
            subject = "MATH",
            displayName = "函数单调性",
            parentKnowledgeNodeId = null,
            taxonomyVersion = "cn-highschool-m1-v1",
            createdAtEpochMillis = 1_000,
            canonicalName = "函数单调性",
        )
        val item = TutorAssessmentItem(
            id = "knowledge-quiz:item:$nodeId",
            stemMarkdown = "题干",
            choices = listOf(
                TutorChoice(id = "A", markdown = "选项 A"),
                TutorChoice(id = "B", markdown = "选项 B"),
            ),
            correctChoiceId = "A",
            knowledgeNodeIds = setOf(nodeId),
        )
        // 不升级 verificationStatus：模型生成的题面默认 UNKNOWN。
        KnowledgeQuizItemStore(database, learnerId).save(sessionId, nodeId, item, 1_000)
        val repository = repository(database)
        // 生产提交身份（KnowledgeReviewSessionViewModel.knowledgeQuizSubmissionId）含
        // `:题面:选项`；改正键入口按这个形状定位被拒观察行，测试不能用短 id。
        val firstRequestId = "knowledge-quiz:review:$sessionId:${item.id}:B"

        try {
            val first = repository.submitKnowledgeQuizFeedback(
                requestId = firstRequestId,
                knowledgeNodeId = nodeId,
                correctChoiceId = "A",
                selectedChoiceId = "B",
                occurredAtEpochMillis = 5_000,
                conversationId = conversationId,
            )
            assertFalse("UNKNOWN 键不得新增数值证据", first.evidenceRecorded)
            assertEquals("ANSWER_KEY_UNVERIFIED", first.rejectedReason)
            val observation = database.recordedChatEvidence.single {
                it.source_kind == "KNOWLEDGE_QUIZ"
            }
            assertTrue("真实作答保留成观察行", observation.isRejected)

            val receipt = AnswerKeyCorrectionWriter(database, learnerId).submit(
                AnswerKeyCorrectionCommand(
                    requestId = "fix-unverified-key",
                    knowledgeNodeId = nodeId,
                    conversationId = conversationId,
                    itemId = item.id,
                    selectedChoiceId = "B",
                    newCorrectChoiceId = "B",
                    expectedKeyVersion = 0,
                    reasonMarkdown = "答案键应为 B",
                    occurredAtEpochMillis = 6_000,
                ),
            )
            assertEquals(AnswerKeyCorrectionStatus.RECORDED, receipt.status)
            assertEquals(setOf(nodeId), receipt.affectedKnowledgeNodeIds)

            val keyFixId = knowledgeQuizKeyFixEvidenceId(
                rejectedAnswerEvidenceId = observation.evidence_id,
                knowledgeNodeId = nodeId,
                newCorrectChoiceId = "B",
            )
            val accepted = database.recordedChatEvidence.single { it.evidence_id == keyFixId }
            assertEquals("新身份落的是正常证据", null, accepted.rejected_reason)
            assertEquals("KNOWLEDGE_QUIZ", accepted.source_kind)
            assertTrue("按新键重判为答对 → 正向权重", accepted.weight > 0)
            assertTrue(
                "替代键写进机器可读前缀，后续作答据此校验",
                accepted.reason_markdown.startsWith(
                    knowledgeQuizKeyCorrectionReasonPrefix(item.id, "B"),
                ),
            )
            assertTrue(
                "原被拒观察行一字不改",
                database.recordedChatEvidence.first {
                    it.evidence_id == observation.evidence_id
                }.isRejected,
            )

            // 同一身份重试：已由改正后的作答覆盖，不得再补一笔。
            val resubmit = repository.submitKnowledgeQuizFeedback(
                requestId = firstRequestId,
                knowledgeNodeId = nodeId,
                correctChoiceId = "B",
                selectedChoiceId = "B",
                occurredAtEpochMillis = 7_000,
                conversationId = conversationId,
            )
            assertTrue("重试如实回报已记录", resubmit.evidenceRecorded)
            assertEquals(
                "不得因重试再补一笔“换新身份”的证据",
                1,
                database.recordedChatEvidence.count { it.evidence_id == keyFixId },
            )

            // 同一改正命令重试按身份幂等（CAS 用 null：与既有定向纠正一致，
            // 带旧 expectedKeyVersion 的重试按设计显式 CONFLICT）。
            val replay = AnswerKeyCorrectionWriter(database, learnerId).submit(
                AnswerKeyCorrectionCommand(
                    requestId = "fix-unverified-key",
                    knowledgeNodeId = nodeId,
                    conversationId = conversationId,
                    itemId = item.id,
                    selectedChoiceId = "B",
                    newCorrectChoiceId = "B",
                    expectedKeyVersion = null,
                    reasonMarkdown = "答案键应为 B",
                    occurredAtEpochMillis = 6_000,
                ),
            )
            assertEquals(AnswerKeyCorrectionStatus.REPLAYED, replay.status)

            // 下一场复习：题面按会话重新生成，上一场的键改正**不得**跨会话生效。
            // 旧实现按常量的 itemId 命中上一场的观察行 → 命中上一场的 key-fix 证据 id →
            // 新会话的合法作答被静默当成"已记录"。这里钉住新会话仍按自己的快照键走
            // UNKNOWN 观察行，且观察行是**新写的一笔**。
            val nextSessionId = 6_000L
            KnowledgeQuizItemStore(database, learnerId).save(
                sessionId = nextSessionId,
                knowledgeNodeId = nodeId,
                item = item,
                occurredAtEpochMillis = 9_000,
            )
            val nextSession = repository.submitKnowledgeQuizFeedback(
                requestId = "knowledge-quiz:review:$nextSessionId:${item.id}:A",
                knowledgeNodeId = nodeId,
                correctChoiceId = "A",
                selectedChoiceId = "A",
                occurredAtEpochMillis = 10_000,
                conversationId = "knowledge-quiz-review:$nextSessionId",
            )
            assertFalse(
                "新会话不得复用上一场的 key-fix 证据",
                nextSession.evidenceRecorded,
            )
            assertEquals("ANSWER_KEY_UNVERIFIED", nextSession.rejectedReason)
            assertEquals(
                "新会话必须留下自己的观察行",
                2,
                database.recordedChatEvidence.count {
                    it.isRejected && it.source_kind == "KNOWLEDGE_QUIZ"
                },
            )
        } finally {
            repository.close()
        }
    }

    /**
     * Regression for the P1 where the conversation id was a global constant, so
     * the per-session write quota became a lifetime quota: once 50 evidence rows
     * were accepted, every later answer was rejected forever. A new session must
     * start with a fresh quota.
     */
    @Test
    fun aNewReviewSessionIsNotBlockedByAnExhaustedEarlierSession() = runBlocking {
        val newSessionId = 6_000L
        val database = anchoredDatabase()
        database.trustQuizKey(newSessionId, "kc-monotonicity")
        database.acceptedChatEvidenceByConversation[
            "knowledge-quiz-review:exhausted-session"
        ] = MasteryWriteGate.MAX_WRITES_PER_CONVERSATION
        val repository = repository(database)

        try {
            val result = repository.submitKnowledgeQuizFeedback(
                requestId = "req-new-session",
                knowledgeNodeId = "kc-monotonicity",
                correctChoiceId = "A",
                selectedChoiceId = "A",
                occurredAtEpochMillis = 9_000,
                conversationId = "knowledge-quiz-review:$newSessionId",
            )

            assertTrue("New session must not inherit an exhausted quota", result.evidenceRecorded)
        } finally {
            repository.close()
        }
    }
}
