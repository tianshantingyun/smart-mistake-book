package com.tingyun.smartmistakebook.core.data.study

import com.tingyun.smartmistakebook.core.database.AssessmentItemSnapshotSeedRecord
import com.tingyun.smartmistakebook.core.model.TutorAssessmentItem
import com.tingyun.smartmistakebook.core.model.TutorChoice
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * AUD-02：知识点复习题面的持久化（落库 / 读回 / 首见即真 / 损坏与缺失都读不回）。
 *
 * 消灭的失败：题面只存内存时，进程重建后只能重新出题——又出一次网，且学生看到的是另一道题。
 * 这里钉住"同一 `(会话, 知识点)` 恒同一道题"，以及"读不回时返回 null 而不是编一个题面"。
 */
class KnowledgeQuizItemStoreTest {

    private val learnerId = RoomBackedStudyExperienceRepository.DEFAULT_LEARNER_ID

    private fun item(nodeId: String, stem: String = "考察 $nodeId 的选择题") = TutorAssessmentItem(
        id = "knowledge-quiz:item:$nodeId",
        stemMarkdown = stem,
        choices = listOf(
            TutorChoice(id = "A", markdown = "正确项"),
            TutorChoice(id = "B", markdown = "干扰项"),
        ),
        correctChoiceId = "A",
        knowledgeNodeIds = setOf(nodeId),
    )

    private fun store(database: FakeStudyDatabasePort) = KnowledgeQuizItemStore(
        database = database,
        learnerId = learnerId,
    )

    @Test
    fun aPersistedItemReadsBackAsTheSameQuestion() = runBlocking {
        val database = FakeStudyDatabasePort()
        val store = store(database)
        val saved = item("kc-confl")

        val canonical = store.save(
            sessionId = 5_000L,
            knowledgeNodeId = "kc-confl",
            item = saved,
            occurredAtEpochMillis = 11_000L,
        )
        val readBack = store.read(sessionId = 5_000L, knowledgeNodeId = "kc-confl")

        assertEquals(saved, canonical)
        assertEquals("题面（题干/选项/标准答案/归属）必须逐字段读回", saved, readBack)
        assertEquals(1, database.assessmentItemSnapshots.size)
    }

    @Test
    fun aMissingRowReadsAsUnreadableInsteadOfGuessing() = runBlocking {
        val database = FakeStudyDatabasePort()

        // 缺失：这个身份没有题面 —— 调用方走失败出口，而不是编一道题出来。
        assertNull(store(database).read(sessionId = 5_000L, knowledgeNodeId = "kc-confl"))
    }

    @Test
    fun aSecondGenerationInTheSameSessionKeepsTheFirstQuestion() = runBlocking {
        val database = FakeStudyDatabasePort()
        val store = store(database)

        val first = store.save(
            sessionId = 5_000L,
            knowledgeNodeId = "kc-confl",
            item = item("kc-confl", stem = "第一道题"),
            occurredAtEpochMillis = 11_000L,
        )
        // 同身份的第二笔（例如重试后模型给了另一道题）：库里那一行不可变，回读仍是第一道题——
        // 屏幕上的题面与之后恢复读回的题面因此不可能分叉。
        val second = store.save(
            sessionId = 5_000L,
            knowledgeNodeId = "kc-confl",
            item = item("kc-confl", stem = "第二道题"),
            occurredAtEpochMillis = 12_000L,
        )

        assertEquals("第一道题", first?.stemMarkdown)
        assertEquals("首见即真：第二笔不得覆盖", "第一道题", second?.stemMarkdown)
        assertEquals("第一道题", store.read(5_000L, "kc-confl")?.stemMarkdown)
    }

    @Test
    fun differentSessionsDoNotShareAQuestion() = runBlocking {
        val database = FakeStudyDatabasePort()
        val store = store(database)

        store.save(
            sessionId = 5_000L,
            knowledgeNodeId = "kc-confl",
            item = item("kc-confl", stem = "今天的题"),
            occurredAtEpochMillis = 11_000L,
        )
        // 隔日另一场会话：不同身份 —— 既读不到昨天的题，也不会互相顶掉。
        assertNull(store.read(sessionId = 5_000L + 24L * 60 * 60 * 1_000, "kc-confl"))
        assertNotEquals(
            knowledgeQuizItemSnapshotId(learnerId, 5_000L, "kc-confl"),
            knowledgeQuizItemSnapshotId(learnerId, 5_000L + 24L * 60 * 60 * 1_000, "kc-confl"),
        )
    }

    @Test
    fun aCorruptedRowIsReportedAsUnreadable() = runBlocking {
        val database = FakeStudyDatabasePort()
        val store = store(database)
        val snapshotId = knowledgeQuizItemSnapshotId(learnerId, 5_000L, "kc-confl")

        // 三种"坏行"：schema 版本不认识、选项解不开、答案解不开。
        listOf(
            AssessmentItemSnapshotSeedRecord(
                assessmentItemSnapshotId = snapshotId,
                itemRevision = 1,
                practiceUnitId = null,
                problemRevisionId = null,
                tutorContentSnapshotId = snapshotId,
                promptMarkdown = "题面",
                optionsSnapshot = """[{"id":"A","markdown":"选项"},""",
                answerSpecSnapshot = """{"schema":"knowledge-quiz-item.v1","itemId":"i","correctChoiceId":"A","knowledgeNodeIds":["kc-confl"]}""",
                verificationStatus = "VERIFIED",
                assessmentEligibility = "SESSION_ONLY",
                scoringMode = "AUTO_VERIFIED",
                learnerSnapshotVersion = "knowledge-quiz-review-v1",
                projectionCheckpoint = 0L,
                hintLevelAtPresentation = 0,
                answerRevealState = "HIDDEN",
                createdAtEpochMillis = 11_000L,
            ),
            AssessmentItemSnapshotSeedRecord(
                assessmentItemSnapshotId = snapshotId,
                itemRevision = 1,
                practiceUnitId = null,
                problemRevisionId = null,
                tutorContentSnapshotId = snapshotId,
                promptMarkdown = "题面",
                optionsSnapshot = """[{"id":"A","markdown":"正确项"},{"id":"B","markdown":"干扰项"}]""",
                answerSpecSnapshot = """{"schema":"knowledge-quiz-item.v99","itemId":"i","correctChoiceId":"A","knowledgeNodeIds":["kc-confl"]}""",
                verificationStatus = "VERIFIED",
                assessmentEligibility = "SESSION_ONLY",
                scoringMode = "AUTO_VERIFIED",
                learnerSnapshotVersion = "knowledge-quiz-review-v1",
                projectionCheckpoint = 0L,
                hintLevelAtPresentation = 0,
                answerRevealState = "HIDDEN",
                createdAtEpochMillis = 11_000L,
            ),
        ).forEach { corrupted ->
            database.assessmentItemSnapshots[snapshotId] = corrupted

            assertNull("坏行必须读不回（不许把坏题面端上屏幕）", store.read(5_000L, "kc-confl"))
            assertNull(
                "坏行上再落一次也拿不到可用题面：按失败出口处置",
                store.save(
                    sessionId = 5_000L,
                    knowledgeNodeId = "kc-confl",
                    item = item("kc-confl"),
                    occurredAtEpochMillis = 12_000L,
                ),
            )
        }
    }
}
