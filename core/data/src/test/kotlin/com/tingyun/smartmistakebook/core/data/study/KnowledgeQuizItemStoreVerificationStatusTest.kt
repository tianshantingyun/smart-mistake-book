package com.tingyun.smartmistakebook.core.data.study

import com.tingyun.smartmistakebook.core.database.AssessmentItemSnapshotSeedRecord
import com.tingyun.smartmistakebook.core.database.StudyDbValue
import com.tingyun.smartmistakebook.core.model.TutorAssessmentItem
import com.tingyun.smartmistakebook.core.model.TutorChoice
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * KD-67 死端之一：唯一的生产题面写入方 `KnowledgeQuizItemStore.record` 固定
 * `verificationStatus = UNKNOWN`——调用方即使已经知道"这一行的答案被独立核验/用户确认过"，
 * 也没有任何办法把它写进库；而 KD-41 的损坏行重写口还会把从旧行带过来的状态整行覆盖成
 * `UNKNOWN`，让一次**已发生**的用户确认在下次重试后静默消失（候选重新落回
 * `CLARIFY_OR_CONFIRM`，学生看到的是"我刚确认过，怎么又问一遍"）。
 *
 * 两个用例分别钉住：
 * 1. **写口能写核验状态**：给定的状态必须落库（不是永远 UNKNOWN）；
 * 2. **修复口不降级**：覆盖一条坏行的内容时，已核验状态原样保留。
 */
class KnowledgeQuizItemStoreVerificationStatusTest {

    private val learnerId = RoomBackedStudyExperienceRepository.DEFAULT_LEARNER_ID

    private fun item(nodeId: String) = TutorAssessmentItem(
        id = "knowledge-quiz:item:$nodeId",
        stemMarkdown = "考察 $nodeId 的选择题",
        choices = listOf(
            TutorChoice(id = "A", markdown = "正确项"),
            TutorChoice(id = "B", markdown = "干扰项"),
        ),
        correctChoiceId = "A",
        knowledgeNodeIds = setOf(nodeId),
    )

    private fun corruptRow(snapshotId: String, verificationStatus: String) =
        AssessmentItemSnapshotSeedRecord(
            assessmentItemSnapshotId = snapshotId,
            itemRevision = 1,
            practiceUnitId = null,
            problemRevisionId = null,
            tutorContentSnapshotId = snapshotId,
            promptMarkdown = "旧题面",
            // 选项解不开 = 这行不可复用（读口返回 null）。
            optionsSnapshot = "[not-json",
            answerSpecSnapshot =
                """{"schema":"knowledge-quiz-item.v1","itemId":"i","correctChoiceId":"A","knowledgeNodeIds":["kc-a"]}""",
            verificationStatus = verificationStatus,
            assessmentEligibility = StudyDbValue.AssessmentEligibility.SESSION_ONLY,
            scoringMode = StudyDbValue.ScoringMode.AUTO_VERIFIED,
            learnerSnapshotVersion = "knowledge-quiz-review-v1",
            projectionCheckpoint = 0L,
            hintLevelAtPresentation = 0,
            answerRevealState = "HIDDEN",
            createdAtEpochMillis = 11_000L,
        )

    @Test
    fun `save persists the verification status it is given`() = runBlocking {
        val database = FakeStudyDatabasePort()
        val store = KnowledgeQuizItemStore(database, learnerId)
        val snapshotId = knowledgeQuizItemSnapshotId(learnerId, 5_000L, "kc-a")

        store.save(
            sessionId = 5_000L,
            knowledgeNodeId = "kc-a",
            item = item("kc-a"),
            occurredAtEpochMillis = 11_000L,
            verificationStatus = StudyDbValue.VerificationStatus.USER_ASSERTED,
        )

        assertEquals(
            "调用方知道的核验状态必须写进库，而不是被固定成 UNKNOWN",
            StudyDbValue.VerificationStatus.USER_ASSERTED,
            database.assessmentItemSnapshots.getValue(snapshotId).verificationStatus,
        )
    }

    @Test
    fun `a corrupt row rewrite keeps an already verified status instead of downgrading it`() =
        runBlocking {
            val database = FakeStudyDatabasePort()
            val snapshotId = knowledgeQuizItemSnapshotId(learnerId, 5_000L, "kc-a")
            database.assessmentItemSnapshots[snapshotId] =
                corruptRow(snapshotId, StudyDbValue.VerificationStatus.USER_ASSERTED)
            val store = KnowledgeQuizItemStore(database, learnerId)

            store.save(
                sessionId = 5_000L,
                knowledgeNodeId = "kc-a",
                item = item("kc-a"),
                occurredAtEpochMillis = 12_000L,
            )

            assertEquals(
                "KD-41 修复口只修内容，不得把已确认的核验状态降回 UNKNOWN",
                StudyDbValue.VerificationStatus.USER_ASSERTED,
                database.assessmentItemSnapshots.getValue(snapshotId).verificationStatus,
            )
        }
}
