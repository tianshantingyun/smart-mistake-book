package com.tingyun.smartmistakebook.core.data.study

import com.tingyun.smartmistakebook.core.database.AssessmentItemSnapshotSeedRecord
import com.tingyun.smartmistakebook.core.database.entity.LearnerChatEvidenceEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AUD-11 ③ 审计读口（`KnowledgeQuizEvidenceAudit`）的**解析规则**：证据行 → 题面快照引用。
 *
 * 这一层测的是"对不上时各自留各自的 null"这条纪律（合成一条看着像对上的记录比报 null 更坏），
 * 以及三条身份格式（证据 id / 会话写入标识 / 题面行 id）的解析边界；真库上的端到端对上
 * 由 `KnowledgeQuizEvidenceAuditInstrumentedTest` 覆盖（那边走生产写路径落真行）。
 */
class KnowledgeQuizEvidenceAuditTest {

    private val learnerId = "learner:audit"
    private val nodeId = "kc-monotonicity"

    @Test
    fun `a quiz evidence row is paired with its item snapshot by session`() = runBlocking {
        val database = FakeStudyDatabasePort()
        val sessionId = 1_700_000_000_000L
        database.assessmentItemSnapshots[snapshotId(sessionId)] = snapshot(sessionId)
        val requestId = "knowledge-quiz:review:$sessionId:knowledge-quiz:item:$nodeId:A"
        database.recordedChatEvidence += quizRow(
            evidenceId = "knowledge-quiz:$requestId:$nodeId",
            conversationId = "knowledge-quiz-review:$sessionId",
        )

        val link = audit(database).scan().single()

        assertEquals("knowledge-quiz:$requestId:$nodeId", link.evidenceId)
        assertEquals(requestId, link.requestId)
        assertEquals(nodeId, link.knowledgeNodeId)
        assertEquals(sessionId, link.sessionId)
        val item = link.item
        assertNotNull("行在就必须给得出引用", item)
        assertEquals(snapshotId(sessionId), item!!.assessmentItemSnapshotId)
        assertEquals(1, item.itemRevision)
        assertEquals("knowledge-quiz-item.v1", item.answerSpecSchema)
        assertEquals("B", item.item?.correctChoiceId)
        assertEquals(listOf("A", "B"), item.item?.choices?.map { it.id })
    }

    @Test
    fun `a model chat row never enters the quiz audit`() = runBlocking {
        val database = FakeStudyDatabasePort()
        database.recordedChatEvidence += quizRow(
            evidenceId = "chat-ev:req-1:mastery_update:$nodeId",
            conversationId = "tutor-session:1",
            sourceKind = "MODEL_CHAT",
        )

        assertTrue(
            "讲题通道没有题面快照，混进来只会给一堆永远为 null 的引用",
            audit(database).scan().isEmpty(),
        )
    }

    @Test
    fun `a conversation id that is not a session shape links no session and no item`() = runBlocking {
        val database = FakeStudyDatabasePort()
        val sessionId = 1_700_000_000_000L
        database.assessmentItemSnapshots[snapshotId(sessionId)] = snapshot(sessionId)
        // 旧口径的固定 conversation id（无会话分量）：解析不出会话 → 也对不上题面。
        database.recordedChatEvidence += quizRow(
            evidenceId = "knowledge-quiz:req-legacy:$nodeId",
            conversationId = "knowledge-quiz-review",
        )

        val link = audit(database).scan().single()

        assertNull("不是本通道会话形状 = null，不截串不猜", link.sessionId)
        assertNull(link.item)
    }

    @Test
    fun `a missing item row still reports the session reference but no question`() = runBlocking {
        val database = FakeStudyDatabasePort()
        val sessionId = 5_000L
        database.recordedChatEvidence += quizRow(
            evidenceId = "knowledge-quiz:req-missing:$nodeId",
            conversationId = "knowledge-quiz-review:$sessionId",
        )

        val link = audit(database).scan().single()

        assertEquals(sessionId, link.sessionId)
        assertNull("没有那一行就没有引用——不拿内存/邻近行顶替", link.item)
    }

    @Test
    fun `an unreadable item row reports its key version and no question`() = runBlocking {
        val database = FakeStudyDatabasePort()
        val sessionId = 7_000L
        database.assessmentItemSnapshots[snapshotId(sessionId)] = snapshot(
            sessionId = sessionId,
            itemRevision = 3,
            answerSpecSchema = "knowledge-quiz-item.v99",
        )
        database.recordedChatEvidence += quizRow(
            evidenceId = "knowledge-quiz:req-broken:$nodeId",
            conversationId = "knowledge-quiz-review:$sessionId",
        )

        val item = audit(database).scan().single().item
        assertNotNull("行在 = 引用就在（只是题面读不回）", item)
        assertEquals(3, item!!.itemRevision)
        assertEquals(
            "键版本引用在题面读不回时也要报得出来（审计要能说清这行坏在哪一版）",
            "knowledge-quiz-item.v99",
            item.answerSpecSchema,
        )
        assertNull("坏行不猜题面", item.item)
    }

    @Test
    fun `request id parsing rejects the fallback and model id shapes`() {
        val sessionId = 1_700_000_000_000L
        assertEquals(
            "knowledge-quiz:review:$sessionId:item-1:A",
            knowledgeQuizEvidenceRequestIdOf(
                evidenceId = "knowledge-quiz:knowledge-quiz:review:$sessionId:item-1:A:$nodeId",
                knowledgeNodeId = nodeId,
            ),
        )
        assertNull(
            "空命名空间的回退形态（knowledge-quiz-<nanoTime>）没有身份段",
            knowledgeQuizEvidenceRequestIdOf("knowledge-quiz-123456789", knowledgeNodeId = nodeId),
        )
        assertNull(
            "讲题通道的 id 不进本口",
            knowledgeQuizEvidenceRequestIdOf(
                evidenceId = "chat-ev:req-1:mastery_update:$nodeId",
                knowledgeNodeId = nodeId,
            ),
        )
        assertNull(
            "尾部节点段对不上 = 不是这一行的 id",
            knowledgeQuizEvidenceRequestIdOf("knowledge-quiz:req-1:other-node", knowledgeNodeId = nodeId),
        )
    }

    private fun audit(database: FakeStudyDatabasePort) = KnowledgeQuizEvidenceAudit(database, learnerId)

    private fun snapshotId(sessionId: Long) =
        knowledgeQuizItemSnapshotId(learnerId, sessionId, nodeId)

    private fun quizRow(
        evidenceId: String,
        conversationId: String,
        sourceKind: String = "KNOWLEDGE_QUIZ",
    ) = LearnerChatEvidenceEntity(
        evidence_id = evidenceId,
        learner_id = learnerId,
        conversation_id = conversationId,
        knowledge_node_id = nodeId,
        direction = "POSITIVE",
        weight = 0.15,
        reason_markdown = "知识点复习作答（答对）",
        confidence = 1.0,
        source_kind = sourceKind,
        created_at_epoch_millis = 9_000,
        anchor_class = "CONFIRMED",
    )

    /** 一行呈现快照（题面 + 键载荷）；[answerSpecSchema] 可改坏来测"读不回"。 */
    private fun snapshot(
        sessionId: Long,
        itemRevision: Int = 1,
        answerSpecSchema: String = "knowledge-quiz-item.v1",
    ) = AssessmentItemSnapshotSeedRecord(
        assessmentItemSnapshotId = snapshotId(sessionId),
        itemRevision = itemRevision,
        practiceUnitId = null,
        problemRevisionId = null,
        tutorContentSnapshotId = snapshotId(sessionId),
        promptMarkdown = "函数单调性的判定？",
        optionsSnapshot = """[{"id":"A","markdown":"正确项"},{"id":"B","markdown":"干扰项"}]""",
        answerSpecSnapshot = """{"schema":"$answerSpecSchema","itemId":"knowledge-quiz:item:$nodeId",""" +
            """"correctChoiceId":"B","knowledgeNodeIds":["$nodeId"]}""",
        verificationStatus = "VERIFIED",
        assessmentEligibility = "SESSION_ONLY",
        scoringMode = "AUTO_VERIFIED",
        learnerSnapshotVersion = "knowledge-quiz-review-v1",
        projectionCheckpoint = 0L,
        hintLevelAtPresentation = 0,
        answerRevealState = "HIDDEN",
        createdAtEpochMillis = 9_000,
    )
}
