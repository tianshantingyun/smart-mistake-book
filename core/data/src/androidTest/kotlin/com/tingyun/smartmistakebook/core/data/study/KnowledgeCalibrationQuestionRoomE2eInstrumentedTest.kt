package com.tingyun.smartmistakebook.core.data.study

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.database.KnowledgeNodeSeedRecord
import com.tingyun.smartmistakebook.core.database.StudyDatabaseFactory
import com.tingyun.smartmistakebook.core.database.StudyDatabasePort
import com.tingyun.smartmistakebook.core.database.StudyDbValue
import com.tingyun.smartmistakebook.core.database.StudySeedBundle
import com.tingyun.smartmistakebook.core.domain.AdaptiveClarificationConsumer
import com.tingyun.smartmistakebook.core.domain.AdaptiveClarificationRequest
import com.tingyun.smartmistakebook.core.domain.AdaptiveDecisionKind
import com.tingyun.smartmistakebook.core.domain.AdaptiveTargetKind
import com.tingyun.smartmistakebook.core.domain.AnswerKeyVerificationSource
import com.tingyun.smartmistakebook.core.domain.EloCorrectnessPredictor
import com.tingyun.smartmistakebook.core.model.AnswerKeyVerification
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * KD-67 第二道校准题 · **真 Room 端到端**（批次 50）。
 *
 * 消灭的失败（批次 49 终裁 §B49-6 与 `repair/unit-gate-and-kd67-blockers.md` §3 逐字登记）：
 * 1. 「真 Room e2e 未跑」——确认链只在 JVM 假库与设备 UI 注入态上成立，投影/账本/单列 CAS
 *    从未在同一条真库链路里连起来；
 * 2. 「第二道校准题未实现」——候选池只有本会话已落库的 FOUNDATION 题，`(会话, 知识点)`
 *    单槽不允许第二道题。
 *
 * 本用例只走**生产写路径**（不手改 `verification_status` 造绿）：
 * - 落库：`persistKnowledgeQuizItem` / `persistKnowledgeCalibrationQuizItem`（真 Room 呈现快照表）；
 * - 作答：`submitKnowledgeQuizFeedback` / `submitKnowledgeCalibrationQuizFeedback`（键信任门）；
 * - 账本写：`AnswerKeyCorrectionWriter`（UNKNOWN 键的用户断言路径，真实账本事件）；
 * - CAS：`KnowledgeQuizItemVerificationUpgrader` + `LedgerAnswerKeyVerificationSourceVerifier`
 *   （单列 UPDATE + 来源回执校验）；
 * - 下次装配：`AdaptiveSelectionAssembler` / `selectKnowledgeReviewQuestion`（真读面）。
 *
 * 覆盖两条完整链：
 * - 会话题面：作答 → 观察行 → 用户断言 → CAS → **下次装配 contentVerified=true**；
 * - 第二道校准题：同一 `(会话, 知识点)` 的独立槽位，确认后同样 contentVerified=true，且
 *   校准走廊（`AdaptiveTargetKind.CALIBRATION`）能选中它；确认前必须停在澄清出口。
 *
 * 两个槽位的核验互不影响：升级一道题不得把另一道题标成已核验（断言逐槽）。
 */
@RunWith(AndroidJUnit4::class)
class KnowledgeCalibrationQuestionRoomE2eInstrumentedTest {

    @Test
    fun answerThenLedgerAssertionThenCasMakesTheNextAssemblySeeTheVerifiedQuestion() =
        runBlocking {
            withRealDatabase { database, repository ->
                repository.persistKnowledgeQuizItem(
                    sessionId = SESSION_ID,
                    knowledgeNodeId = NODE,
                    item = reviewItem(),
                )
                repository.persistKnowledgeCalibrationQuizItem(
                    sessionId = SESSION_ID,
                    knowledgeNodeId = NODE,
                    item = calibrationItem(),
                )

                // 1) 作答会话题面：键未核验 → 只落观察行，不写数值证据。
                val answer = repository.submitKnowledgeQuizFeedback(
                    requestId = submissionRequestId(REVIEW_ITEM_ID, "A"),
                    knowledgeNodeId = NODE,
                    correctChoiceId = "A",
                    selectedChoiceId = "A",
                    occurredAtEpochMillis = T0,
                    conversationId = CONVERSATION_ID,
                )
                assertFalse(answer.evidenceRecorded)
                assertEquals("ANSWER_KEY_UNVERIFIED", answer.rejectedReason)

                // 2) 账本写：学生确认"这个答案对" → 真实用户断言事件。
                val receipt = AnswerKeyCorrectionWriter(database, LEARNER_ID).submit(
                    AnswerKeyCorrectionCommand(
                        requestId = "confirm-review-question",
                        knowledgeNodeId = NODE,
                        conversationId = CONVERSATION_ID,
                        itemId = REVIEW_ITEM_ID,
                        selectedChoiceId = "A",
                        newCorrectChoiceId = "A",
                        reasonMarkdown = "学生确认题面答案：A",
                        occurredAtEpochMillis = T0 + 1_000L,
                    ),
                )
                assertEquals(AnswerKeyCorrectionStatus.RECORDED, receipt.status)

                // 3) 单列 CAS：来源校验读真账本回执。
                val upgrade = upgrader(database).upgrade(
                    sessionId = SESSION_ID,
                    knowledgeNodeId = NODE,
                    targetVerification = AnswerKeyVerification.USER_ASSERTED,
                    source = userAssertion(receipt.correctionId),
                )
                assertTrue("$upgrade", upgrade is KnowledgeQuizItemVerificationUpgradeOutcome.Upgraded)

                // 4) 下次装配：同一行读回已核验；另一槽仍 UNKNOWN。
                val candidates = assemble(database)
                val review = candidates.single { it.assessmentItemId == REVIEW_ITEM_ID }
                val calibration = candidates.single { it.assessmentItemId == CALIBRATION_ITEM_ID }
                assertTrue("确认后的题面必须在下次装配里 contentVerified=true", review.contentVerified)
                assertFalse("确认会话题面不得顺手核验第二道题", calibration.contentVerified)
                assertEquals(AdaptiveTargetKind.FOUNDATION, review.targetKind)
                assertEquals(AdaptiveTargetKind.CALIBRATION, calibration.targetKind)
            }
        }

    @Test
    fun confirmingTheSecondQuestionMakesTheCalibrationCorridorSelectIt() = runBlocking {
        withRealDatabase { database, repository ->
            repository.persistKnowledgeQuizItem(
                sessionId = SESSION_ID,
                knowledgeNodeId = NODE,
                item = reviewItem(),
            )
            repository.persistKnowledgeCalibrationQuizItem(
                sessionId = SESSION_ID,
                knowledgeNodeId = NODE,
                item = calibrationItem(),
            )
            // 两槽各自可读、互不覆盖：第二道题有独立行身份。
            assertEquals(REVIEW_ITEM_ID, repository.readPersistedKnowledgeQuizItem(SESSION_ID, NODE)?.id)
            assertEquals(
                CALIBRATION_ITEM_ID,
                repository.readPersistedKnowledgeCalibrationQuizItem(SESSION_ID, NODE)?.id,
            )

            val consumer = RecordingClarificationConsumer()
            val before = repository.selectKnowledgeReviewQuestion(
                sessionId = SESSION_ID,
                knowledgeNodeId = NODE,
                clarificationConsumer = consumer,
                requestedTargetKind = AdaptiveTargetKind.CALIBRATION,
                userRequestedQuestion = true,
            )
            assertEquals(
                "未核验的第二道题不得因'学生点了先做校准题'进入 ASK",
                AdaptiveDecisionKind.CLARIFY_OR_CONFIRM,
                before?.kind,
            )
            assertEquals(1, consumer.requests.size)

            // 1) 作答第二道题：必须按**第二道题自己的键**（B）判分（不是会话题的 A）。
            val answer = repository.submitKnowledgeCalibrationQuizFeedback(
                requestId = submissionRequestId(CALIBRATION_ITEM_ID, "B"),
                knowledgeNodeId = NODE,
                itemId = CALIBRATION_ITEM_ID,
                correctChoiceId = "B",
                selectedChoiceId = "B",
                occurredAtEpochMillis = T0,
                conversationId = CONVERSATION_ID,
            )
            assertFalse(answer.evidenceRecorded)
            assertEquals("ANSWER_KEY_UNVERIFIED", answer.rejectedReason)

            // 2) 账本写：确认第二道题的答案键。
            val receipt = AnswerKeyCorrectionWriter(database, LEARNER_ID).submit(
                AnswerKeyCorrectionCommand(
                    requestId = "confirm-calibration-question",
                    knowledgeNodeId = NODE,
                    conversationId = CONVERSATION_ID,
                    itemId = CALIBRATION_ITEM_ID,
                    selectedChoiceId = "B",
                    newCorrectChoiceId = "B",
                    reasonMarkdown = "学生确认题面答案：B",
                    occurredAtEpochMillis = T0 + 1_000L,
                ),
            )
            assertEquals(AnswerKeyCorrectionStatus.RECORDED, receipt.status)

            // 3) CAS 只升级校准槽。
            val upgrade = upgrader(database).upgrade(
                sessionId = SESSION_ID,
                knowledgeNodeId = NODE,
                targetVerification = AnswerKeyVerification.USER_ASSERTED,
                source = userAssertion(receipt.correctionId),
                slot = KnowledgeQuizQuestionSlot.CALIBRATION,
            )
            assertTrue("$upgrade", upgrade is KnowledgeQuizItemVerificationUpgradeOutcome.Upgraded)
            assertEquals(
                StudyDbValue.VerificationStatus.USER_ASSERTED,
                database.findAssessmentItemSnapshot(calibrationSnapshotId())?.verificationStatus,
            )
            assertEquals(
                "会话题面的核验状态不得被第二道题的确认改掉",
                StudyDbValue.VerificationStatus.UNKNOWN,
                database.findAssessmentItemSnapshot(reviewSnapshotId())?.verificationStatus,
            )

            // 4) 下次装配：第二道题 contentVerified=true，且标成 CALIBRATION 候选。
            val assembled = assemble(database)
            val calibrationCandidate =
                assembled.single { it.assessmentItemId == CALIBRATION_ITEM_ID }
            assertTrue(calibrationCandidate.contentVerified)
            assertEquals(AdaptiveTargetKind.CALIBRATION, calibrationCandidate.targetKind)
            assertFalse(
                assembled.single { it.assessmentItemId == REVIEW_ITEM_ID }.contentVerified,
            )

            // 5) 校准走廊选中第二道题（真快照 + 真账本 + 真投影）。
            val after = repository.selectKnowledgeReviewQuestion(
                sessionId = SESSION_ID,
                knowledgeNodeId = NODE,
                clarificationConsumer = RecordingClarificationConsumer(),
                requestedTargetKind = AdaptiveTargetKind.CALIBRATION,
                userRequestedQuestion = true,
            )
            assertEquals(AdaptiveDecisionKind.ASK_CALIBRATION, after?.kind)
            assertEquals(CALIBRATION_ITEM_ID, after?.selectedAssessmentItemId)
            // 控制面必须来自真投影（账本已非空），不是 drain 失败后的空快照——
            // 否则"真 Room e2e"只是把空快照喂给选择器。
            assertTrue(
                "选择决策的投影位点必须 ≥ 账本里的用户断言事件",
                (after?.projectionCheckpointSequence ?: 0L) >= 1L,
            )
        }
    }

    @Test
    fun answeringTheSecondQuestionWithoutItsIdentityIsJudgedAgainstTheSessionQuestion() = runBlocking {
        withRealDatabase { _, repository ->
            repository.persistKnowledgeQuizItem(
                sessionId = SESSION_ID,
                knowledgeNodeId = NODE,
                item = reviewItem(),
            )
            repository.persistKnowledgeCalibrationQuizItem(
                sessionId = SESSION_ID,
                knowledgeNodeId = NODE,
                item = calibrationItem(),
            )

            // 旧读口（按 `(会话, 知识点)` 读会话题面）：第二道题的键 B 会被拿题面的键 A 判，
            // 误报成"旧键迟到"——这正是必须按题面 id 定位槽位的那条失败。
            val misjudged = repository.submitKnowledgeQuizFeedback(
                requestId = "${submissionRequestId(CALIBRATION_ITEM_ID, "B")}:legacy",
                knowledgeNodeId = NODE,
                correctChoiceId = "B",
                selectedChoiceId = "B",
                occurredAtEpochMillis = T0,
                conversationId = CONVERSATION_ID,
            )
            assertFalse(misjudged.evidenceRecorded)
            assertEquals("ANSWER_KEY_VERSION_STALE", misjudged.rejectedReason)

            // 新读口（带题面 id）：按第二道题自己的键判 → 只是"键未核验"的观察行。
            val judged = repository.submitKnowledgeCalibrationQuizFeedback(
                requestId = submissionRequestId(CALIBRATION_ITEM_ID, "B"),
                knowledgeNodeId = NODE,
                itemId = CALIBRATION_ITEM_ID,
                correctChoiceId = "B",
                selectedChoiceId = "B",
                occurredAtEpochMillis = T0 + 500L,
                conversationId = CONVERSATION_ID,
            )
            assertFalse(judged.evidenceRecorded)
            assertEquals("ANSWER_KEY_UNVERIFIED", judged.rejectedReason)
        }
    }

    // ---- 夹具 ----

    private class RecordingClarificationConsumer : AdaptiveClarificationConsumer {
        val requests = mutableListOf<AdaptiveClarificationRequest>()

        override suspend fun onClarificationRequired(request: AdaptiveClarificationRequest) {
            requests += request
        }
    }

    private suspend fun withRealDatabase(
        block: suspend (StudyDatabasePort, RoomBackedStudyExperienceRepository) -> Unit,
    ) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "knowledge-calibration-e2e-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        val database = StudyDatabaseFactory.open(context, databaseName)
        val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val repository = RoomBackedStudyExperienceRepository(
            database = database,
            applicationScope = applicationScope,
        )
        try {
            seed(context, database, databaseName)
            block(database, repository)
        } finally {
            repository.close()
            applicationScope.cancel()
            database.close()
            context.deleteDatabase(databaseName)
        }
    }

    private suspend fun seed(
        context: Context,
        database: StudyDatabasePort,
        databaseName: String,
    ) {
        seedStudyFacts(
            context = context,
            database = database,
            databaseName = databaseName,
            bundle = StudySeedBundle(
                problems = emptyList(),
                revisions = emptyList(),
                practiceUnits = emptyList(),
                errorBookEntries = emptyList(),
                knowledgeNodes = listOf(
                    KnowledgeNodeSeedRecord(
                        knowledgeNodeId = NODE,
                        stableCode = "$TAXONOMY_VERSION:math:atomic:$NODE",
                        subject = "MATH",
                        displayName = NODE,
                        parentKnowledgeNodeId = null,
                        taxonomyVersion = TAXONOMY_VERSION,
                        createdAtEpochMillis = 1_000L,
                        canonicalName = NODE,
                        nodeKind = KnowledgeNodeKind.REASONING.name,
                        granularity = KnowledgeNodeGranularity.ATOMIC.name,
                        verificationStatus = KnowledgeNodeVerificationStatus.SOURCE_GROUNDED.name,
                    ),
                ),
            ),
        )
    }

    private fun upgrader(database: StudyDatabasePort) = KnowledgeQuizItemVerificationUpgrader(
        database = database,
        learnerId = LEARNER_ID,
        sourceVerifier = LedgerAnswerKeyVerificationSourceVerifier(
            database = database,
            learnerId = LEARNER_ID,
        ),
    )

    private fun userAssertion(confirmationId: String) =
        AnswerKeyVerificationSource.UserAssertion(
            confirmationId = confirmationId,
            confirmedByLearnerId = LEARNER_ID,
            confirmedAtEpochMillis = T0 + 2_000L,
        )

    /** 直接装配（不经选择器）：断言两槽候选的核验轴与目标类型。 */
    private suspend fun assemble(database: StudyDatabasePort) =
        AdaptiveSelectionAssembler(
            database = database,
            learnerId = LEARNER_ID,
            predictor = EloCorrectnessPredictor(),
        ).assembleCandidates(
            sessionId = SESSION_ID,
            targetKnowledgeNodeIds = setOf(NODE),
            decisionAtEpochMillis = System.currentTimeMillis(),
        )

    private fun reviewSnapshotId() = knowledgeQuizItemSnapshotId(LEARNER_ID, SESSION_ID, NODE)

    private fun calibrationSnapshotId() =
        knowledgeQuizCalibrationItemSnapshotId(LEARNER_ID, SESSION_ID, NODE)

    /** 生产提交身份形状（与 `KnowledgeReviewSessionViewModel.knowledgeQuizSubmissionId` 同形）。 */
    private fun submissionRequestId(itemId: String, choiceId: String) =
        "knowledge-quiz:review:$SESSION_ID:$itemId:$choiceId"

    private fun reviewItem() = TutorAssessmentItem(
        id = REVIEW_ITEM_ID,
        stemMarkdown = "题干（会话题面）",
        choices = listOf(
            TutorChoice(id = "A", markdown = "选项 A"),
            TutorChoice(id = "B", markdown = "选项 B"),
        ),
        correctChoiceId = "A",
        knowledgeNodeIds = setOf(NODE),
    )

    /** 第二道题：**不同的题面 id 与不同的答案键**（键相同的夹具会掩盖按错题面判分的缺陷）。 */
    private fun calibrationItem() = TutorAssessmentItem(
        id = CALIBRATION_ITEM_ID,
        stemMarkdown = "题干（第二道校准题）",
        choices = listOf(
            TutorChoice(id = "A", markdown = "选项 A"),
            TutorChoice(id = "B", markdown = "选项 B"),
        ),
        correctChoiceId = "B",
        knowledgeNodeIds = setOf(NODE),
    )

    private companion object {
        const val LEARNER_ID = RoomBackedStudyExperienceRepository.DEFAULT_LEARNER_ID
        const val NODE = "kc-calibration-e2e"
        const val SESSION_ID = 1_700_000_000_000L
        const val T0 = 1_700_000_001_000L
        const val TAXONOMY_VERSION = "cn-highschool-m1-v1"
        const val REVIEW_ITEM_ID = "knowledge-quiz:item:$NODE"
        const val CALIBRATION_ITEM_ID = "knowledge-quiz:calibration:$NODE"
        const val CONVERSATION_ID = "knowledge-quiz-review:$SESSION_ID"
    }
}
