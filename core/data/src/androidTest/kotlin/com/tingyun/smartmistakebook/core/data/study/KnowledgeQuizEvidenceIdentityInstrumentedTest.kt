package com.tingyun.smartmistakebook.core.data.study

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.database.KnowledgeNodeSeedRecord
import com.tingyun.smartmistakebook.core.database.LearningLedgerReadStatus
import com.tingyun.smartmistakebook.core.database.ProjectionBatchStopReason
import com.tingyun.smartmistakebook.core.database.StudyDatabaseFactory
import com.tingyun.smartmistakebook.core.database.StudyDatabasePort
import com.tingyun.smartmistakebook.core.database.StudySeedBundle
import com.tingyun.smartmistakebook.core.model.ChatEvidenceSubmitted
import com.tingyun.smartmistakebook.core.model.KnowledgeNodeGranularity
import com.tingyun.smartmistakebook.core.model.KnowledgeNodeKind
import com.tingyun.smartmistakebook.core.model.KnowledgeNodeVerificationStatus
import com.tingyun.smartmistakebook.core.model.TutorAssessmentItem
import com.tingyun.smartmistakebook.core.model.TutorChoice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * AUD-01 验收（**真 Room**，不是 Fake）：知识点复习的证据身份 = `(会话, 题面, 选项)`。
 *
 * 三条验收各自钉一面：
 * 1. 同一次会话里的重试 = **一笔**（身份相同 → 幂等），且已落库那行不被重放改写；
 * 2. 隔日另一场会话同选项 = **第二笔**（身份带会话分量 → 各自成笔，冷却 12h 已过故接受）；
 * 3. 重放不撞 `projection_outbox` 的 `event_kind+event_id` 唯一键、不推进学习序列：
 *    账本无空洞（改前第二笔 accepted 写会撞键回滚，学生看到"回写失败"而库里什么都没有）。
 */
@RunWith(AndroidJUnit4::class)
class KnowledgeQuizEvidenceIdentityInstrumentedTest {

    @Test
    fun sameSessionRetryLeavesExactlyOneEvidenceRow() = runBlocking {
        withQuizFixture { database, repository ->
            val first = repository.submitKnowledgeQuizFeedback(
                requestId = SESSION_ONE_REQUEST_ID,
                knowledgeNodeId = NODE_ID,
                correctChoiceId = "A",
                selectedChoiceId = "A",
                occurredAtEpochMillis = DAY_ONE_AT,
                conversationId = SESSION_ONE_CONVERSATION_ID,
            )
            assertTrue(first.evidenceRecorded)

            // 同一次会话里的重试：同一身份、只晚 1 秒。
            // AUD-11 ①（2026-10-16）起小测通道不取同 KC 冷却，这一笔在**门控上是接受**——
            // 于是真正被考的是身份层：按身份重放不得留下第二笔、也不得把已接受那行改写成
            // 拒绝行（改前这一笔在门控被判冷却，验的是"被拒 + 身份去重"那条路，结论同一）。
            repository.submitKnowledgeQuizFeedback(
                requestId = SESSION_ONE_REQUEST_ID,
                knowledgeNodeId = NODE_ID,
                correctChoiceId = "A",
                selectedChoiceId = "A",
                occurredAtEpochMillis = DAY_ONE_AT + 1_000,
                conversationId = SESSION_ONE_CONVERSATION_ID,
            )

            val rows = database.readChatEvidenceByLearner(LEARNER_ID)
            assertEquals("同一次会话的重试只该留一笔证据", 1, rows.size)
            val row = rows.single()
            // 重放按身份是 no-op：已接受的那行不被拒绝标记覆盖，时间戳仍是第一笔的。
            assertNull("重放不得改写已落库那一行", row.rejected_reason)
            assertEquals(DAY_ONE_AT, row.created_at_epoch_millis)
            assertEquals(
                "证据身份由提交身份派生（knowledge-quiz:<requestId>:<节点>）",
                "knowledge-quiz:$SESSION_ONE_REQUEST_ID:$NODE_ID",
                row.evidence_id,
            )
        }
    }

    @Test
    fun theSameChoiceInALaterSessionIsASecondEvidenceRow() = runBlocking {
        withQuizFixture { database, repository ->
            val dayOne = repository.submitKnowledgeQuizFeedback(
                requestId = SESSION_ONE_REQUEST_ID,
                knowledgeNodeId = NODE_ID,
                correctChoiceId = "A",
                selectedChoiceId = "A",
                occurredAtEpochMillis = DAY_ONE_AT,
                conversationId = SESSION_ONE_CONVERSATION_ID,
            )
            // 隔日再来：另一场会话（身份带各自会话分量），同知识点、同选项。
            val nextDay = repository.submitKnowledgeQuizFeedback(
                requestId = SESSION_TWO_REQUEST_ID,
                knowledgeNodeId = NODE_ID,
                correctChoiceId = "A",
                selectedChoiceId = "A",
                occurredAtEpochMillis = DAY_ONE_AT + ONE_DAY_MILLIS,
                conversationId = SESSION_TWO_CONVERSATION_ID,
            )

            assertTrue(dayOne.evidenceRecorded)
            assertTrue("隔日第二笔应被接受（同 KC 冷却 12h 已过）", nextDay.evidenceRecorded)
            val rows = database.readChatEvidenceByLearner(LEARNER_ID)
            assertEquals("隔日同选项是第二笔，不是被幂等键吞掉的一笔", 2, rows.size)
            assertEquals(2, rows.map { it.evidence_id }.distinct().size)
            assertTrue(rows.all { it.rejected_reason == null })
            // 两笔各自进账本：序列 1、2（不同身份 → 各自一行 outbox）。
            val ledger = database.loadLearningLedger(LEARNER_ID)
            assertEquals(LearningLedgerReadStatus.COMPLETE, ledger.status)
            assertEquals(listOf(1L, 2L), ledger.validPrefix.map { it.event.eventSequence })
        }
    }

    @Test
    fun anAcceptedReplayOfOneIdentityNeverCollidesOnTheOutboxUniqueKeyOrLeavesASequenceGap() = runBlocking {
        withQuizFixture { database, repository ->
            // 同一身份、隔了 13h 再写一次：这一次门控会**接受**，于是真的走到 outbox 写入。
            // 改前：撞 event_kind+event_id 唯一键（ABORT）→ 整个事务回滚 + 抛异常，学生看到
            // "回写失败"而库里什么都没有。现在：按身份静默 no-op——不写第二行、不推进学习序列。
            // （AUD-11 ① 起小测通道不再有 12h 冷却，13h 这个间隔对放行已无影响；用例保留原形状，
            // 它钉的是 accepted 重放，与间隔无关。）
            repository.submitKnowledgeQuizFeedback(
                requestId = SESSION_ONE_REQUEST_ID,
                knowledgeNodeId = NODE_ID,
                correctChoiceId = "A",
                selectedChoiceId = "A",
                occurredAtEpochMillis = DAY_ONE_AT,
                conversationId = SESSION_ONE_CONVERSATION_ID,
            )
            val replay = repository.submitKnowledgeQuizFeedback(
                requestId = SESSION_ONE_REQUEST_ID,
                knowledgeNodeId = NODE_ID,
                correctChoiceId = "A",
                selectedChoiceId = "A",
                occurredAtEpochMillis = DAY_ONE_AT + THIRTEEN_HOURS_MILLIS,
                conversationId = SESSION_ONE_CONVERSATION_ID,
            )

            assertTrue("小测通道的这笔写在门控上是接受（才走得到 outbox 写入）", replay.evidenceRecorded)
            val rows = database.readChatEvidenceByLearner(LEARNER_ID)
            assertEquals("按身份重放：账本里仍是一笔", 1, rows.size)
            assertEquals(DAY_ONE_AT, rows.single().created_at_epoch_millis)

            val batch = database.loadProjectionBatch(
                projectionName = "study-experience-v1",
                learnerId = LEARNER_ID,
                limit = 100,
            )
            assertEquals(ProjectionBatchStopReason.END_OF_LEDGER, batch.stopReason)
            val chatEvents = batch.events.map { it.event }.filterIsInstance<ChatEvidenceSubmitted>()
            assertEquals("同身份重放不该多出一条 outbox 行", 1, chatEvents.size)
            assertEquals(listOf(1L), chatEvents.map { it.eventSequence })

            val ledger = database.loadLearningLedger(LEARNER_ID)
            assertEquals(LearningLedgerReadStatus.COMPLETE, ledger.status)
            assertEquals("重放不得推进学习序列（账本无空洞）", 1, ledger.validPrefix.size)
        }
    }

    /**
     * 真库 + 真仓库（生产写路径：repository → KnowledgeQuizFeedbackWriter → 统一写入口 → DAO）。
     * 知识点行必须真实存在，否则门控按"未锚定"拒绝，用例就测不到"接受后身份如何落库"。
     */
    private suspend fun withQuizFixture(
        block: suspend (StudyDatabasePort, RoomBackedStudyExperienceRepository) -> Unit,
    ) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "knowledge-quiz-evidence-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        val database: StudyDatabasePort = StudyDatabaseFactory.open(context, databaseName)
        val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val repository = RoomBackedStudyExperienceRepository(
            database = database,
            applicationScope = applicationScope,
        )
        try {
            seedStudyFacts(context, database, databaseName, quizSeed())
            // PRD-02：本用例测的是证据身份/幂等，不是键信任门；把两场会话的题面键
            // 显式标成独立核验（模型生成默认 UNKNOWN，只会落观察行）。
            trustQuizKey(database, SESSION_ONE_START, NODE_ID)
            trustQuizKey(database, SESSION_TWO_START, NODE_ID)
            block(database, repository)
        } finally {
            repository.close()
            applicationScope.cancel()
            database.close()
            context.deleteDatabase(databaseName)
        }
    }

    private suspend fun trustQuizKey(
        database: StudyDatabasePort,
        sessionId: Long,
        nodeId: String,
    ) {
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
        KnowledgeQuizItemStore(database, LEARNER_ID).save(sessionId, nodeId, item, 1_000)
        val snapshotId = knowledgeQuizItemSnapshotId(LEARNER_ID, sessionId, nodeId)
        val record = requireNotNull(database.findAssessmentItemSnapshot(snapshotId))
        database.overwriteAssessmentItemSnapshot(record.copy(verificationStatus = "VERIFIED"))
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
        const val DAY_ONE_AT = 1_700_000_000_000L
        const val SESSION_ONE_START = 1_700_000_000_000L
        const val SESSION_TWO_START = 1_700_086_400_000L
        const val ONE_DAY_MILLIS = 24L * 60 * 60 * 1_000
        const val THIRTEEN_HOURS_MILLIS = 13L * 60 * 60 * 1_000

        /**
         * 提交身份就是 feature 层派生的形态（`knowledge-quiz:review:<会话>:<题面>:<选项>`）：
         * 两条只差会话分量——这正是"同次重试一笔、隔日同选项两笔"的分水岭。
         */
        const val SESSION_ONE_REQUEST_ID =
            "knowledge-quiz:review:1700000000000:knowledge-quiz:item:kc-monotonicity:A"
        const val SESSION_TWO_REQUEST_ID =
            "knowledge-quiz:review:1700086400000:knowledge-quiz:item:kc-monotonicity:A"
        const val SESSION_ONE_CONVERSATION_ID = "knowledge-quiz-review:1700000000000"
        const val SESSION_TWO_CONVERSATION_ID = "knowledge-quiz-review:1700086400000"
    }
}
