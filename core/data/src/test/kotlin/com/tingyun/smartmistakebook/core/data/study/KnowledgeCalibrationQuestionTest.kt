package com.tingyun.smartmistakebook.core.data.study

import com.tingyun.smartmistakebook.core.database.AssessmentItemSnapshotSeedRecord
import com.tingyun.smartmistakebook.core.database.StudyDbValue
import com.tingyun.smartmistakebook.core.domain.AdaptiveDecisionKind
import com.tingyun.smartmistakebook.core.domain.AdaptiveTargetKind
import com.tingyun.smartmistakebook.core.domain.AnswerKeyVerificationSource
import com.tingyun.smartmistakebook.core.domain.AnswerKeyVerificationSourceVerifier
import com.tingyun.smartmistakebook.core.domain.EloCorrectnessPredictor
import com.tingyun.smartmistakebook.core.domain.LearningProjector
import com.tingyun.smartmistakebook.core.model.AnswerKeyVerification
import com.tingyun.smartmistakebook.core.model.LearnerSnapshot
import com.tingyun.smartmistakebook.core.model.ProjectionCheckpoint
import com.tingyun.smartmistakebook.core.model.TutorAssessmentItem
import com.tingyun.smartmistakebook.core.model.TutorChoice
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * KD-67 第二道校准题（批次 50）：**候选池**与**单槽身份**。
 *
 * 消灭的失败（批次 49 终裁 §B49-6 逐字登记）：「第二道校准题未实现——候选池只有本会话
 * 已落库的 FOUNDATION 题，`(会话, 知识点)` 单槽不允许第二道」。两处缺一不可：
 * 1. 持久化只能存一道题（第二个槽位不存在），第二道题落库时要么覆盖第一道、要么无处可写；
 * 2. 装配器只读会话题面槽、且把候选一律标成 FOUNDATION，CALIBRATION 走廊
 *    （`AdaptiveQuestionSelector.selectCalibration`）永远没有可选题面 → 学生点了
 *    「先做校准题」也只会看到 `NO_SAFE_CALIBRATION_CANDIDATE`。
 *
 * 本组用例钉住的语义（不得放宽）：
 * - 第二道题是**独立身份**：题面 id 与会话题面不同（选择器要求候选 id 唯一；答案键
 *   更正前缀按题面 id 分流）；
 * - 它**仍受核验门约束**：未核验的第二道题 `contentVerified=false`，CALIBRATION 走廊
 *   不会因为"学生点了按钮"就放行未核验题面；
 * - 核验是**逐槽**的：升级第二道题不得把第一道题的状态一起改掉。
 */
class KnowledgeCalibrationQuestionTest {

    private val learnerId = RoomBackedStudyExperienceRepository.DEFAULT_LEARNER_ID
    private val sessionId = 5_000L
    private val nodeId = "kc-calib"

    @Test
    fun `assembled candidates expose the persisted second question as a calibration candidate`() = runBlocking {
        val database = FakeStudyDatabasePort()
        database.assessmentItemSnapshots[reviewSnapshotId()] = snapshotRow(
            snapshotId = reviewSnapshotId(),
            itemId = reviewItemId(),
        )
        // 第二道题的行身份**写成字面量**：身份格式被改动时这条用例应当当场报红，
        // 而不是跟着实现悄悄改口径（与 `KnowledgeQuizItemSnapshotInstrumentedTest:67` 同纪律）。
        val calibrationSnapshotId =
            "knowledge-quiz-item-calibration:$learnerId:$sessionId:$nodeId"
        database.assessmentItemSnapshots[calibrationSnapshotId] = snapshotRow(
            snapshotId = calibrationSnapshotId,
            itemId = knowledgeQuizCalibrationItemId(nodeId),
        )

        val candidates = assembler(database).assembleCandidates(
            sessionId = sessionId,
            targetKnowledgeNodeIds = setOf(nodeId),
            decisionAtEpochMillis = 2_000L,
        )

        assertEquals(
            "同一知识点必须装配出两道题（会话题面 + 第二道校准题）",
            setOf(reviewItemId(), knowledgeQuizCalibrationItemId(nodeId)),
            candidates.map { it.assessmentItemId }.toSet(),
        )
        val review = candidates.single { it.assessmentItemId == reviewItemId() }
        val calibration = candidates.single { it.assessmentItemId == knowledgeQuizCalibrationItemId(nodeId) }
        assertEquals(AdaptiveTargetKind.FOUNDATION, review.targetKind)
        assertEquals(
            "第二道题必须标成 CALIBRATION，否则选择器的校准走廊永远选不到它",
            AdaptiveTargetKind.CALIBRATION,
            calibration.targetKind,
        )
        assertEquals(setOf(nodeId), calibration.knowledgeNodeIds)
        assertEquals(
            "未核验的第二道题不得因'学生点了先做校准题'而被放行",
            false,
            calibration.contentVerified,
        )
    }

    @Test
    fun `the calibration corridor selects the second question only after its own row is verified`() = runBlocking {
        val database = FakeStudyDatabasePort()
        database.assessmentItemSnapshots[reviewSnapshotId()] = snapshotRow(
            snapshotId = reviewSnapshotId(),
            itemId = reviewItemId(),
        )
        val calibrationSnapshotId =
            "knowledge-quiz-item-calibration:$learnerId:$sessionId:$nodeId"
        database.assessmentItemSnapshots[calibrationSnapshotId] = snapshotRow(
            snapshotId = calibrationSnapshotId,
            itemId = knowledgeQuizCalibrationItemId(nodeId),
        )

        val before = assembler(database).select(
            sessionId = sessionId,
            learnerSnapshot = selectionSnapshot(),
            targetKnowledgeNodeIds = setOf(nodeId),
            requestedTargetKind = AdaptiveTargetKind.CALIBRATION,
            decisionAtEpochMillis = 2_000L,
            userRequestedQuestion = true,
        )
        assertEquals(
            "两道题都未核验时，校准走廊必须停在澄清出口（fail-closed）",
            AdaptiveDecisionKind.CLARIFY_OR_CONFIRM,
            before.decision.kind,
        )
        assertNull(before.decision.selectedAssessmentItemId)

        // 只核验第二道题（第一道仍是 UNKNOWN）：校准走廊应当选它。
        database.assessmentItemSnapshots[calibrationSnapshotId] =
            database.assessmentItemSnapshots.getValue(calibrationSnapshotId).copy(
                verificationStatus = StudyDbValue.VerificationStatus.USER_ASSERTED,
            )

        val after = assembler(database).select(
            sessionId = sessionId,
            learnerSnapshot = selectionSnapshot(),
            targetKnowledgeNodeIds = setOf(nodeId),
            requestedTargetKind = AdaptiveTargetKind.CALIBRATION,
            decisionAtEpochMillis = 2_000L,
            userRequestedQuestion = true,
        )
        assertEquals(
            "确认后的第二道校准题必须能被校准走廊选中",
            AdaptiveDecisionKind.ASK_CALIBRATION,
            after.decision.kind,
        )
        assertEquals(knowledgeQuizCalibrationItemId(nodeId), after.decision.selectedAssessmentItemId)
        assertNull("ASK_CALIBRATION 不得携带澄清载荷", after.clarification)
    }

    @Test
    fun `the second question persists under its own slot without touching the session question`() = runBlocking {
        val database = FakeStudyDatabasePort()
        val store = KnowledgeQuizItemStore(database, learnerId)
        val review = item(itemId = reviewItemId(), correctChoiceId = "A")
        val calibration = item(
            itemId = knowledgeQuizCalibrationItemId(nodeId),
            correctChoiceId = "B",
        )

        assertEquals(review, store.save(sessionId, nodeId, review, 1_000L))
        assertEquals(
            calibration,
            store.save(
                sessionId = sessionId,
                knowledgeNodeId = nodeId,
                item = calibration,
                occurredAtEpochMillis = 2_000L,
                slot = KnowledgeQuizQuestionSlot.CALIBRATION,
            ),
        )

        // 两槽各自可读、互不覆盖（旧实现只有一行，第二笔会撞不可变闸/覆盖第一道题）。
        assertEquals(review, store.read(sessionId, nodeId))
        assertEquals(calibration, store.read(sessionId, nodeId, KnowledgeQuizQuestionSlot.CALIBRATION))
        assertEquals(2, database.assessmentItemSnapshots.size)

        // 已知题面 id 时必须定位到**自己的**槽位；id 对不上 = null（不拿另一道题顶替）。
        assertEquals(
            calibration,
            store.readSnapshotForItem(sessionId, nodeId, knowledgeQuizCalibrationItemId(nodeId))?.item,
        )
        assertNull(store.readSnapshotForItem(sessionId, nodeId, "knowledge-quiz:item:other"))
    }

    @Test
    fun `confirming the second question upgrades only its own slot`() = runBlocking {
        val database = FakeStudyDatabasePort()
        val store = KnowledgeQuizItemStore(database, learnerId)
        store.save(sessionId, nodeId, item(itemId = reviewItemId(), correctChoiceId = "A"), 1_000L)
        store.save(
            sessionId = sessionId,
            knowledgeNodeId = nodeId,
            item = item(itemId = knowledgeQuizCalibrationItemId(nodeId), correctChoiceId = "B"),
            occurredAtEpochMillis = 2_000L,
            slot = KnowledgeQuizQuestionSlot.CALIBRATION,
        )
        val upgrader = KnowledgeQuizItemVerificationUpgrader(
            database = database,
            learnerId = learnerId,
            statusWriter = SingleColumnVerificationWriter(database),
            sourceVerifier = AnswerKeyVerificationSourceVerifier { source, _, _ ->
                source is AnswerKeyVerificationSource.UserAssertion &&
                    source.confirmationId == "correction:second-question"
            },
        )

        val outcome = upgrader.upgrade(
            sessionId = sessionId,
            knowledgeNodeId = nodeId,
            targetVerification = AnswerKeyVerification.USER_ASSERTED,
            source = AnswerKeyVerificationSource.UserAssertion(
                confirmationId = "correction:second-question",
                confirmedByLearnerId = learnerId,
                confirmedAtEpochMillis = 9_000L,
            ),
            slot = KnowledgeQuizQuestionSlot.CALIBRATION,
        )

        assertTrue("$outcome", outcome is KnowledgeQuizItemVerificationUpgradeOutcome.Upgraded)
        assertEquals(
            "确认第二道题只能改第二槽",
            StudyDbValue.VerificationStatus.USER_ASSERTED,
            database.assessmentItemSnapshots.getValue(calibrationSnapshotId()).verificationStatus,
        )
        assertEquals(
            "会话题面的核验状态不得被第二道题的确认顺手改掉",
            StudyDbValue.VerificationStatus.UNKNOWN,
            database.assessmentItemSnapshots.getValue(reviewSnapshotId()).verificationStatus,
        )
    }

    @Test
    fun `answering the second question is judged against its own key not the session question`() = runBlocking {
        val database = FakeStudyDatabasePort()
        val store = KnowledgeQuizItemStore(database, learnerId)
        store.save(sessionId, nodeId, item(itemId = reviewItemId(), correctChoiceId = "A"), 1_000L)
        store.save(
            sessionId = sessionId,
            knowledgeNodeId = nodeId,
            item = item(itemId = knowledgeQuizCalibrationItemId(nodeId), correctChoiceId = "B"),
            occurredAtEpochMillis = 2_000L,
            slot = KnowledgeQuizQuestionSlot.CALIBRATION,
        )
        val writer = KnowledgeQuizFeedbackWriter(database, learnerId)

        val result = writer.submit(
            requestId = "knowledge-quiz:review:$sessionId:${knowledgeQuizCalibrationItemId(nodeId)}:B",
            knowledgeNodeId = nodeId,
            correctChoiceId = "B",
            selectedChoiceId = "B",
            occurredAtEpochMillis = 3_000L,
            conversationId = "knowledge-quiz-review:$sessionId",
            itemId = knowledgeQuizCalibrationItemId(nodeId),
        )

        // 按第二道题自己的键（B）判分：这是一次"键未核验"的观察行，**不是**旧键拒收。
        // 旧实现按 (会话, 知识点) 读会话题面（键 A），会把这次作答误判成 VERSION_STALE。
        assertEquals(false, result.evidenceRecorded)
        assertEquals("ANSWER_KEY_UNVERIFIED", result.rejectedReason)
        assertEquals(1, database.recordedChatEvidence.size)
        assertTrue(
            "观察行的证据身份必须带着第二道题自己的题面 id（更正前缀按它分流）",
            database.recordedChatEvidence.single().evidence_id
                .contains(":${knowledgeQuizCalibrationItemId(nodeId)}:B"),
        )
    }

    // ---- 夹具 ----

    private fun assembler(database: FakeStudyDatabasePort) =
        AdaptiveSelectionAssembler(database, learnerId, EloCorrectnessPredictor())

    private fun reviewSnapshotId() = knowledgeQuizItemSnapshotId(learnerId, sessionId, nodeId)

    private fun calibrationSnapshotId() =
        knowledgeQuizCalibrationItemSnapshotId(learnerId, sessionId, nodeId)

    private fun reviewItemId() = "knowledge-quiz:item:$nodeId"

    private fun item(itemId: String, correctChoiceId: String) = TutorAssessmentItem(
        id = itemId,
        stemMarkdown = "考察 $nodeId 的题面",
        choices = listOf(
            TutorChoice(id = "A", markdown = "选项 A"),
            TutorChoice(id = "B", markdown = "选项 B"),
        ),
        correctChoiceId = correctChoiceId,
        knowledgeNodeIds = setOf(nodeId),
    )

    /** 生产 adapter 的契约镜像：只改 verification_status 的单列 CAS。 */
    private class SingleColumnVerificationWriter(
        private val database: FakeStudyDatabasePort,
    ) : AssessmentItemVerificationStatusWriter {
        override suspend fun compareAndSetVerificationStatus(
            assessmentItemSnapshotId: String,
            expectedCurrentStatus: String,
            newVerificationStatus: String,
        ): Boolean {
            val current = database.assessmentItemSnapshots[assessmentItemSnapshotId] ?: return false
            if (current.verificationStatus != expectedCurrentStatus) return false
            database.assessmentItemSnapshots[assessmentItemSnapshotId] =
                current.copy(verificationStatus = newVerificationStatus)
            return true
        }
    }

    /**
     * 选择器只要求"新鲜 + 投影 CURRENT"就能进 CALIBRATION 走廊；掌握度状态不参与
     * 校准走廊判据（`AdaptiveQuestionSelector.selectCalibration`）。空状态表因此足够，
     * 且不会让本组用例意外依赖掌握度算法。
     */
    private fun selectionSnapshot() = LearnerSnapshot(
        learnerId = learnerId,
        knowledgeMasteryStates = emptyMap(),
        checkpoint = ProjectionCheckpoint(0L, LearningProjector.VERSION, 500L),
        generatedAtEpochMillis = 500L,
    )

    private fun snapshotRow(
        snapshotId: String,
        itemId: String,
        verificationStatus: String = StudyDbValue.VerificationStatus.UNKNOWN,
    ) = AssessmentItemSnapshotSeedRecord(
        assessmentItemSnapshotId = snapshotId,
        itemRevision = 1,
        practiceUnitId = null,
        problemRevisionId = null,
        tutorContentSnapshotId = snapshotId,
        promptMarkdown = "考察 $nodeId 的题面",
        optionsSnapshot = """[{"id":"A","markdown":"选项 A"},{"id":"B","markdown":"选项 B"}]""",
        answerSpecSnapshot =
            """{"schema":"knowledge-quiz-item.v1","itemId":"$itemId",""" +
                """"correctChoiceId":"A","knowledgeNodeIds":["$nodeId"]}""",
        verificationStatus = verificationStatus,
        assessmentEligibility = "SESSION_ONLY",
        scoringMode = "AUTO_VERIFIED",
        learnerSnapshotVersion = "knowledge-quiz-review-v1",
        projectionCheckpoint = 0L,
        hintLevelAtPresentation = 0,
        answerRevealState = "HIDDEN",
        createdAtEpochMillis = 11_000L,
    )
}
