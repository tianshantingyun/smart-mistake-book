package com.tingyun.smartmistakebook.core.data.study

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.database.AssessmentItemSnapshotSeedRecord
import com.tingyun.smartmistakebook.core.database.StudyDatabaseFactory
import com.tingyun.smartmistakebook.core.database.StudyDatabasePort
import com.tingyun.smartmistakebook.core.model.TutorAssessmentItem
import com.tingyun.smartmistakebook.core.model.TutorChoice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * AUD-02 验收（**真 Room**）：题面落进既有呈现快照表后按 `(会话, 知识点)` 读回同一道题；
 * 没写过/写坏了的身份读不回（null = 走失败出口），而不是编一道题出来。
 *
 * 真库这一层专门覆盖 Fake 看不到的两件事：`DatabaseContractValidator` 对呈现行的契约
 * （归属、枚举值、长度）与 `assessment_item_snapshot` 的唯一索引/外键形状。
 */
@RunWith(AndroidJUnit4::class)
class KnowledgeQuizItemSnapshotInstrumentedTest {

    private val learnerId = "learner:local"

    private fun item(nodeId: String, stem: String) = TutorAssessmentItem(
        id = "knowledge-quiz:item:$nodeId",
        stemMarkdown = stem,
        choices = listOf(
            TutorChoice(id = "A", markdown = "正确项"),
            TutorChoice(id = "B", markdown = "干扰项"),
        ),
        correctChoiceId = "B",
        knowledgeNodeIds = setOf(nodeId),
    )

    @Test
    fun aPersistedQuizItemReadsBackAsTheSameQuestion() = runBlocking {
        withRealDatabase { _, repository ->
            val saved = item("kc-monotonicity", "函数单调性的判定？")

            val canonical = repository.persistKnowledgeQuizItem(
                sessionId = 5_000L,
                knowledgeNodeId = "kc-monotonicity",
                item = saved,
            )
            assertEquals(saved, canonical)
            assertEquals(
                saved,
                repository.readPersistedKnowledgeQuizItem(5_000L, "kc-monotonicity"),
            )
            // 另一场会话（隔日）是另一个身份：读不到今天的题。
            assertNull(repository.readPersistedKnowledgeQuizItem(5_000L + 86_400_000L, "kc-monotonicity"))
        }
    }

    @Test
    fun aCorruptedPresentationRowReadsBackAsUnreadable() = runBlocking {
        withRealDatabase { database, repository ->
            // 行身份 = `knowledge-quiz-item:<learner>:<会话>:<知识点>`。写成字面量：身份格式被
            // 改动时这条用例应当当场报红（而不是跟着实现悄悄改口径）。
            val snapshotId = "knowledge-quiz-item:$learnerId:5000:kc-monotonicity"
            // 直接落一行"解不开"的呈现行（schema 版本不认识）：恢复必须说"读不回"。
            database.saveAssessmentItemSnapshot(
                AssessmentItemSnapshotSeedRecord(
                    assessmentItemSnapshotId = snapshotId,
                    itemRevision = 1,
                    practiceUnitId = null,
                    problemRevisionId = null,
                    tutorContentSnapshotId = snapshotId,
                    promptMarkdown = "题面",
                    optionsSnapshot = """[{"id":"A","markdown":"正确项"},{"id":"B","markdown":"干扰项"}]""",
                    answerSpecSnapshot = """{"schema":"knowledge-quiz-item.v99","itemId":"i","correctChoiceId":"A","knowledgeNodeIds":["kc-monotonicity"]}""",
                    verificationStatus = "VERIFIED",
                    assessmentEligibility = "SESSION_ONLY",
                    scoringMode = "AUTO_VERIFIED",
                    learnerSnapshotVersion = "knowledge-quiz-review-v1",
                    projectionCheckpoint = 0L,
                    hintLevelAtPresentation = 0,
                    answerRevealState = "HIDDEN",
                    createdAtEpochMillis = 11_000L,
                ),
            )

            assertNull(
                "坏行读不回：调用方据此走既有失败出口，不重出题",
                repository.readPersistedKnowledgeQuizItem(5_000L, "kc-monotonicity"),
            )
        }
    }

    private suspend fun withRealDatabase(
        block: suspend (StudyDatabasePort, RoomBackedStudyExperienceRepository) -> Unit,
    ) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "knowledge-quiz-item-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        val database: StudyDatabasePort = StudyDatabaseFactory.open(context, databaseName)
        val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val repository = RoomBackedStudyExperienceRepository(
            database = database,
            applicationScope = applicationScope,
        )
        try {
            block(database, repository)
        } finally {
            repository.close()
            applicationScope.cancel()
            database.close()
            context.deleteDatabase(databaseName)
        }
    }
}
