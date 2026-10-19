package com.tingyun.smartmistakebook.core.data.study

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.database.AssessmentItemSnapshotSeedRecord
import com.tingyun.smartmistakebook.core.database.KnowledgeNodeSeedRecord
import com.tingyun.smartmistakebook.core.database.StudyDatabaseFactory
import com.tingyun.smartmistakebook.core.database.StudyDatabasePort
import com.tingyun.smartmistakebook.core.database.StudySeedBundle
import com.tingyun.smartmistakebook.core.domain.MasteryWriteGate
import com.tingyun.smartmistakebook.core.model.KnowledgeNodeGranularity
import com.tingyun.smartmistakebook.core.model.KnowledgeNodeKind
import com.tingyun.smartmistakebook.core.model.KnowledgeNodeVerificationStatus
import com.tingyun.smartmistakebook.core.model.TutorAssessmentItem
import com.tingyun.smartmistakebook.core.model.TutorChoice
import com.tingyun.smartmistakebook.core.model.TutorEvidenceDirection
import com.tingyun.smartmistakebook.core.model.TutorUnderstandingTier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * AUD-11 ③（真 Room + 生产写路径）：审计口把**证据行对回题面快照**。
 *
 * 消灭的失败：错键落库后审计面无法定位"这笔证据读的是哪一道题、哪一版键"——证据行里没有
 * 题面 id（报告 §3.3(c)）。本用例走完整生产链：`persistKnowledgeQuizItem`（题面落呈现快照）→
 * `submitKnowledgeQuizFeedback`（作答落证据行 + 账本事件）→ `KnowledgeQuizEvidenceAudit.scan()`
 * 对上；并钉住两条边界：坏行只报键版本不猜题面、讲题通道的行不进本口。
 */
@RunWith(AndroidJUnit4::class)
class KnowledgeQuizEvidenceAuditInstrumentedTest {

    @Test
    fun anEvidenceRowIsPairedWithTheItemAndKeyVersionTheStudentActuallyAnswered() = runBlocking {
        withRealDatabase { database, repository ->
            val item = quizItem()
            val canonical = repository.persistKnowledgeQuizItem(
                sessionId = SESSION_ID,
                knowledgeNodeId = NODE_ID,
                item = item,
            )
            assertEquals("落库并回读的是同一道题", item, canonical)

            // PRD-02：模型生成题面默认 UNKNOWN（本地可判 ≠ 键已核验）。本用例测的是
            // 证据行↔题面快照的配对，不是键信任门；把这一行显式标成独立核验，
            // 让作答走既有 accepted 路径。
            val snapshotId = knowledgeQuizItemSnapshotId(LEARNER_ID, SESSION_ID, NODE_ID)
            val storedSnapshot = requireNotNull(database.findAssessmentItemSnapshot(snapshotId))
            database.overwriteAssessmentItemSnapshot(
                storedSnapshot.copy(verificationStatus = "VERIFIED"),
            )

            // 提交身份与写入标识都照 feature 层形态（KnowledgeReviewSessionViewModel）：
            // 这两条字符串是审计口解析的输入，形态变了这条用例当场红。
            val requestId = "knowledge-quiz:review:$SESSION_ID:${item.id}:A"
            val result = repository.submitKnowledgeQuizFeedback(
                requestId = requestId,
                knowledgeNodeId = NODE_ID,
                correctChoiceId = item.correctChoiceId,
                selectedChoiceId = "A",
                occurredAtEpochMillis = T0,
                conversationId = "knowledge-quiz-review:$SESSION_ID",
            )
            assertTrue("证据要真的落库（否则审计对上的不是生产行）", result.evidenceRecorded)

            val link = KnowledgeQuizEvidenceAudit(database, LEARNER_ID).scan().single()

            assertEquals("knowledge-quiz:$requestId:$NODE_ID", link.evidenceId)
            assertEquals(requestId, link.requestId)
            assertEquals(NODE_ID, link.knowledgeNodeId)
            assertEquals(SESSION_ID, link.sessionId)
            val reference = link.item
            assertNotNull("对上题面行", reference)
            assertEquals(
                knowledgeQuizItemSnapshotId(LEARNER_ID, SESSION_ID, NODE_ID),
                reference!!.assessmentItemSnapshotId,
            )
            assertEquals("键版本引用：题面版本", 1, reference.itemRevision)
            assertEquals(
                "键版本引用：answer_spec 的 schema",
                "knowledge-quiz-item.v1",
                reference.answerSpecSchema,
            )
            assertEquals("对上的是学生作答的那道题", item, reference.item)
            assertEquals(
                "对上的键就是判答用的键（错键审计要能看见这一点）",
                item.correctChoiceId,
                reference.item?.correctChoiceId,
            )
        }
    }

    @Test
    fun anUnreadableItemRowReportsItsKeyVersionInsteadOfGuessingAQuestion() = runBlocking {
        withRealDatabase { database, repository ->
            // 手写一行"解不开"的呈现行（schema 不认识）：审计面必须说"题面读不回"，
            // 但题面行 id / revision / 键版本照旧报得出——KD-41/答案键审计看的就是这个。
            val snapshotId = knowledgeQuizItemSnapshotId(LEARNER_ID, SESSION_ID, NODE_ID)
            database.saveAssessmentItemSnapshot(
                AssessmentItemSnapshotSeedRecord(
                    assessmentItemSnapshotId = snapshotId,
                    itemRevision = 2,
                    practiceUnitId = null,
                    problemRevisionId = null,
                    tutorContentSnapshotId = snapshotId,
                    promptMarkdown = "题面",
                    optionsSnapshot = """[{"id":"A","markdown":"正确项"},{"id":"B","markdown":"干扰项"}]""",
                    answerSpecSnapshot = """{"schema":"knowledge-quiz-item.v99","itemId":"i",""" +
                        """"correctChoiceId":"A","knowledgeNodeIds":["$NODE_ID"]}""",
                    verificationStatus = "VERIFIED",
                    assessmentEligibility = "SESSION_ONLY",
                    scoringMode = "AUTO_VERIFIED",
                    learnerSnapshotVersion = "knowledge-quiz-review-v1",
                    projectionCheckpoint = 0L,
                    hintLevelAtPresentation = 0,
                    answerRevealState = "HIDDEN",
                    createdAtEpochMillis = T0,
                ),
            )
            repository.submitKnowledgeQuizFeedback(
                requestId = "knowledge-quiz:review:$SESSION_ID:broken:0",
                knowledgeNodeId = NODE_ID,
                correctChoiceId = "A",
                selectedChoiceId = "A",
                occurredAtEpochMillis = T0,
                conversationId = "knowledge-quiz-review:$SESSION_ID",
            )

            val reference = KnowledgeQuizEvidenceAudit(database, LEARNER_ID).scan().single().item
            assertNotNull(reference)
            assertEquals(2, reference!!.itemRevision)
            assertEquals("knowledge-quiz-item.v99", reference.answerSpecSchema)
            assertNull("坏行不猜题面（调用方走既有失败出口）", reference.item)
        }
    }

    @Test
    fun aModelChatRowStaysOutOfTheQuizAudit() = runBlocking {
        withRealDatabase { database, _ ->
            KnowledgeEvidenceWriter(database).write(
                KnowledgeEvidenceWriteRequest(
                    learnerId = LEARNER_ID,
                    channel = KnowledgeEvidenceChannel.MODEL_CHAT,
                    namespace = "model-task-1",
                    discriminator = "mastery_update",
                    conversationId = "tutor-session:1",
                    knowledgeNodeId = NODE_ID,
                    direction = TutorEvidenceDirection.NEGATIVE,
                    understanding = TutorUnderstandingTier.CONFIDENT,
                    evidenceConfidence = 0.9,
                    reasonMarkdown = "学生这一步没做出来",
                    anchorClass = MasteryWriteGate.ANCHOR_CLASS_CONFIRMED,
                    occurredAtEpochMillis = T0,
                ),
            )

            assertTrue(
                "讲题通道没有题面快照，不进本口（否则给一堆永远为 null 的引用）",
                KnowledgeQuizEvidenceAudit(database, LEARNER_ID).scan().isEmpty(),
            )
        }
    }

    private fun quizItem() = TutorAssessmentItem(
        id = "knowledge-quiz:item:$NODE_ID",
        stemMarkdown = "函数单调性的判定？",
        choices = listOf(
            TutorChoice(id = "A", markdown = "干扰项"),
            TutorChoice(id = "B", markdown = "正确项"),
        ),
        correctChoiceId = "B",
        knowledgeNodeIds = setOf(NODE_ID),
    )

    private suspend fun withRealDatabase(
        block: suspend (StudyDatabasePort, RoomBackedStudyExperienceRepository) -> Unit,
    ) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "quiz-evidence-audit-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        val database: StudyDatabasePort = StudyDatabaseFactory.open(context, databaseName)
        val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val repository = RoomBackedStudyExperienceRepository(
            database = database,
            applicationScope = applicationScope,
        )
        try {
            seedStudyFacts(context, database, databaseName, quizSeed())
            block(database, repository)
        } finally {
            repository.close()
            applicationScope.cancel()
            database.close()
            context.deleteDatabase(databaseName)
        }
    }

    private fun quizSeed() = StudySeedBundle(
        problems = emptyList(),
        revisions = emptyList(),
        practiceUnits = emptyList(),
        errorBookEntries = emptyList(),
        knowledgeNodes = listOf(
            KnowledgeNodeSeedRecord(
                knowledgeNodeId = NODE_ID,
                stableCode = "$TAXONOMY_VERSION:math:atomic:monotonicity",
                subject = "MATH",
                displayName = "判断函数单调性",
                parentKnowledgeNodeId = null,
                taxonomyVersion = TAXONOMY_VERSION,
                createdAtEpochMillis = 1_000,
                canonicalName = "判断函数单调性",
                nodeKind = KnowledgeNodeKind.REASONING.name,
                granularity = KnowledgeNodeGranularity.ATOMIC.name,
                verificationStatus = KnowledgeNodeVerificationStatus.SOURCE_GROUNDED.name,
            ),
        ),
    )

    private companion object {
        const val LEARNER_ID = "learner:local"
        const val NODE_ID = "kc-monotonicity"
        const val TAXONOMY_VERSION = "cn-highschool-m1-v1"
        const val SESSION_ID = 1_700_000_000_000L
        const val T0 = 1_700_000_000_000L
    }
}
