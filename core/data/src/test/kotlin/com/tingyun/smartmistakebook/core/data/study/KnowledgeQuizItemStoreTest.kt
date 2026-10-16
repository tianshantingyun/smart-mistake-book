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
 *
 * KD-41 追加：损坏行**不可复用**——读口照旧读不回，写口把这一行**覆盖重写**，让"重试出题"
 * 可恢复（改前第二笔也撞同一行、也被丢弃：每点一次重试白烧一次模型调用、题面永远显示不出来）。
 * 修复**不改变**正常行的"首见即真"：解得开的既有行一律回读，绝不覆盖。
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

    /** 一行形状合法的呈现行；改参数即可造出"解不开"的坏行（KD-41 用例共用）。 */
    private fun presentationRow(
        snapshotId: String,
        optionsSnapshot: String = """[{"id":"A","markdown":"正确项"},{"id":"B","markdown":"干扰项"}]""",
        answerSpecSnapshot: String =
            """{"schema":"knowledge-quiz-item.v1","itemId":"i","correctChoiceId":"A","knowledgeNodeIds":["kc-confl"]}""",
    ) = AssessmentItemSnapshotSeedRecord(
        assessmentItemSnapshotId = snapshotId,
        itemRevision = 1,
        practiceUnitId = null,
        problemRevisionId = null,
        tutorContentSnapshotId = snapshotId,
        promptMarkdown = "题面",
        optionsSnapshot = optionsSnapshot,
        answerSpecSnapshot = answerSpecSnapshot,
        verificationStatus = "VERIFIED",
        assessmentEligibility = "SESSION_ONLY",
        scoringMode = "AUTO_VERIFIED",
        learnerSnapshotVersion = "knowledge-quiz-review-v1",
        projectionCheckpoint = 0L,
        hintLevelAtPresentation = 0,
        answerRevealState = "HIDDEN",
        createdAtEpochMillis = 11_000L,
    )

    /**
     * 四种「坏行」：选项解不开、schema 版本不认识、答案解不开、解得出但形状不合法
     * （标准答案不在选项集里——最后一关由题面模型自己的 init 把守）。
     */
    private fun corruptedRows(snapshotId: String): List<AssessmentItemSnapshotSeedRecord> = listOf(
        presentationRow(snapshotId, optionsSnapshot = """[{"id":"A","markdown":"选项"},"""),
        presentationRow(
            snapshotId,
            answerSpecSnapshot =
                """{"schema":"knowledge-quiz-item.v99","itemId":"i","correctChoiceId":"A","knowledgeNodeIds":["kc-confl"]}""",
        ),
        presentationRow(
            snapshotId,
            answerSpecSnapshot = """{"schema":"knowledge-quiz-item.v1","itemId":"i",""",
        ),
        presentationRow(
            snapshotId,
            answerSpecSnapshot =
                """{"schema":"knowledge-quiz-item.v1","itemId":"i","correctChoiceId":"C","knowledgeNodeIds":["kc-confl"]}""",
        ),
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

        // 两种"坏行"：选项解不开、schema 版本不认识（其余两种坏法见 corruptedRows）。
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
            // KD-41：坏行不许当成「已有可用题面」处理。改前这里断言的是 null（= 每次重试都
            // 撞回同一行、白烧一次模型调用、题面永远显示不出来）；规则改后同一处断言的是
            // 覆盖重写——读侧语义（上一行的 null）不变，写侧从"死路"变成"可恢复"。
            val retried = store.save(
                sessionId = 5_000L,
                knowledgeNodeId = "kc-confl",
                item = item("kc-confl"),
                occurredAtEpochMillis = 12_000L,
            )
            assertEquals(
                "坏行不可复用：本次题面覆盖重写这一行",
                item("kc-confl"),
                retried,
            )
            assertEquals(
                "覆盖重写后必须能读回（重试可恢复）",
                item("kc-confl"),
                store.read(5_000L, "kc-confl"),
            )
        }
    }

    /**
     * KD-41 的正面用例：损坏行存在 →「重试出题」生成第二笔 → **第二笔成功且可显示**
     * （app 层拿非 null 的返回值走 `KnowledgeQuizLoadResult.Ready`，而不是 `Unavailable`），
     * 且此后读回的就是这一笔。
     */
    @Test
    fun aCorruptedRowIsRewrittenSoRetryCanRecover() = runBlocking {
        val database = FakeStudyDatabasePort()
        val store = store(database)
        val snapshotId = knowledgeQuizItemSnapshotId(learnerId, 5_000L, "kc-confl")
        // 旁证：另一个知识点的行必须完好——修复只针对自己那个身份，不许顺手清掉别人的题面。
        val neighbour = store.save(
            sessionId = 5_000L,
            knowledgeNodeId = "kc-other",
            item = item("kc-other", stem = "另一个知识点的题"),
            occurredAtEpochMillis = 11_000L,
        )

        corruptedRows(snapshotId).forEach { corrupted ->
            database.assessmentItemSnapshots[snapshotId] = corrupted

            val retried = store.save(
                sessionId = 5_000L,
                knowledgeNodeId = "kc-confl",
                item = item("kc-confl", stem = "重试生成的题"),
                occurredAtEpochMillis = 12_000L,
            )

            assertEquals(
                "重试出的题面必须可显示（app 层拿它走 Ready）",
                "重试生成的题",
                retried?.stemMarkdown,
            )
            assertEquals("重试后的题面必须能读回", retried, store.read(5_000L, "kc-confl"))
            assertEquals("另一身份的行不受影响", neighbour, store.read(5_000L, "kc-other"))
            assertEquals(
                "同一身份只该有一行：覆盖不是新增",
                2L,
                database.assessmentItemSnapshots.size.toLong(),
            )
        }
    }

    /**
     * KD-41 边界：修复不是"打开了覆盖闸"。被覆盖重写之后，那一行重新是**正常行**——
     * 同身份再写第三笔仍走"首见即真"（回读第二笔），不因它曾被修复过而可以反复覆写。
     */
    @Test
    fun aRepairedRowIsImmutableAgain() = runBlocking {
        val database = FakeStudyDatabasePort()
        val store = store(database)
        val snapshotId = knowledgeQuizItemSnapshotId(learnerId, 5_000L, "kc-confl")
        database.assessmentItemSnapshots[snapshotId] = corruptedRows(snapshotId).first()

        val repaired = store.save(
            sessionId = 5_000L,
            knowledgeNodeId = "kc-confl",
            item = item("kc-confl", stem = "重试生成的题"),
            occurredAtEpochMillis = 12_000L,
        )
        val third = store.save(
            sessionId = 5_000L,
            knowledgeNodeId = "kc-confl",
            item = item("kc-confl", stem = "第三道题"),
            occurredAtEpochMillis = 13_000L,
        )

        assertEquals("重试生成的题", repaired?.stemMarkdown)
        assertEquals("修复后该行恢复不可变：第三笔不得覆盖", "重试生成的题", third?.stemMarkdown)
        assertEquals("重试生成的题", store.read(5_000L, "kc-confl")?.stemMarkdown)
    }
}
