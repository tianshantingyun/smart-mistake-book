package com.tingyun.smartmistakebook.core.data.study

import com.tingyun.smartmistakebook.core.database.MistakeRecord
import com.tingyun.smartmistakebook.core.database.TutorSessionProblemAnchorRecord
import com.tingyun.smartmistakebook.core.database.TutorTurnResponseRecord
import com.tingyun.smartmistakebook.core.database.entity.LearnerChatEvidenceEntity
import com.tingyun.smartmistakebook.core.database.port.PracticeUnitKnowledgeBindingRecord
import com.tingyun.smartmistakebook.core.domain.StudyReviewSessionStatus
import com.tingyun.smartmistakebook.core.domain.TutorJudgedReviewSettlement
import com.tingyun.smartmistakebook.core.domain.TutorJudgedReviewSettlementStatus
import com.tingyun.smartmistakebook.core.model.EvidenceAttributionCertainty
import com.tingyun.smartmistakebook.core.model.EvidenceAttributionRole
import com.tingyun.smartmistakebook.core.model.LearningEvidenceReason
import com.tingyun.smartmistakebook.core.model.LocalModelJudgedContract
import com.tingyun.smartmistakebook.core.model.ProblemMemoryOutcome
import com.tingyun.smartmistakebook.core.model.TutorConversationIds
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 讲题判定结算的决策表（第2条：复习作答与评级改由模型判定）。
 *
 * 判定合成顺序：本轮有本地核对的检查题作答 → 本地判定即判定（一条答错即判错，模型
 * 口头说对不作数）；没有检查题 → 取最近一条通过门控、且**锚定在本题 KC 集**上的模型判词
 * （AUD-11 ①）；两样都没有 → 不写不推进。
 * 定价非独立、题目级只落 HARD/AGAIN，依据 `docs/research/model-judged-verdict-pricing.md`。
 */
class TutorJudgedReviewSettlerTest {
    @Test
    fun localCheckAnswersDecideTheVerdictAndModelClaimCannotOverrideAnIncorrectOne() = runBlocking {
        // 学生在本轮检查题里答错了一条：行为证据胜出，即便会话里也有一条（门控已接受的）
        // 模型 POSITIVE 判词，结算必须判错。
        val database = seededDatabase()
        database.tutorTurnResponses += choiceResponse(correct = false)
        database.recordedChatEvidence += modelVerdict(direction = "POSITIVE")

        val result = settle(database)

        assertEquals(TutorJudgedReviewSettlementStatus.RECORDED, result.status)
        assertFalse(requireNotNull(result.isCorrect))
        assertEquals(
            LearningEvidenceReason.MODEL_JUDGED_INCORRECT,
            database.lastAttemptCommand?.evidence?.reason,
        )
        assertEquals(0.5, database.lastAttemptCommand?.evidence?.weight ?: -1.0, 0.0)
        assertEquals(
            ProblemMemoryOutcome.RETRIEVAL_FAILURE,
            database.lastAttemptCommand?.problemMemoryOutcome,
        )
        assertEquals(
            // 台账裁决 18：这条判定的来源是**本地核对**（检查题作答），不是模型判词——
            // source_kind 因此是 LOCAL_CHECKED（参与拟合），MODEL_JUDGED 留给开放作答的模型判词。
            ReviewLogSink.SOURCE_KIND_LOCAL_CHECKED,
            database.reviewLogEntries.single().sourceKind,
        )
        assertTrue(result.created)
        // 本地核对分支的驱动是检查题作答（没有节点可挂）：归属保持为空，与既有仪器化用例
        // 「这条通道不动知识点掌握度」同一语义（AUD-11 ①只给模型判词分支落账）。
        assertTrue(database.lastEvidenceSnapshot?.attributions.isNullOrEmpty())
        assertEquals(1, database.attemptCount)
    }

    @Test
    fun allCheckAnswersCorrectSettlesAsCorrect() = runBlocking {
        val database = seededDatabase()
        database.tutorTurnResponses += choiceResponse(correct = true)
        database.tutorTurnResponses += choiceResponse(correct = true, turnOrdinal = 2)

        val result = settle(database)

        assertTrue(requireNotNull(result.isCorrect))
        assertEquals(
            LearningEvidenceReason.MODEL_JUDGED_CORRECT,
            database.lastAttemptCommand?.evidence?.reason,
        )
        assertEquals(0.5, database.lastAttemptCommand?.evidence?.weight ?: -1.0, 0.0)
        assertEquals(
            ProblemMemoryOutcome.ASSISTED_RECALL,
            database.lastAttemptCommand?.problemMemoryOutcome,
        )
    }

    @Test
    fun earlierCycleMistakesDoNotStickAfterReteach() = runBlocking {
        // 上一轮答错 → 重教 → 本轮全对：只算最新一轮（restartCycle 的理由不该永久记账）。
        val database = seededDatabase()
        database.tutorTurnResponses += choiceResponse(correct = false, cycleOrdinal = 1)
        database.tutorTurnResponses += choiceResponse(correct = true, cycleOrdinal = 2)

        val result = settle(database)

        assertTrue(requireNotNull(result.isCorrect))
    }

    @Test
    fun openEndedSessionFallsBackToTheGateAcceptedModelVerdict() = runBlocking {
        // 开放性提问没有本地机判的检查题：只能用模型判词（通过门控的那条）。
        val database = seededDatabase()
        database.recordedChatEvidence += modelVerdict(direction = "POSITIVE")

        val result = settle(database)

        assertTrue(requireNotNull(result.isCorrect))
        assertEquals(
            LearningEvidenceReason.MODEL_JUDGED_CORRECT,
            database.lastAttemptCommand?.evidence?.reason,
        )
        assertEquals(
            // 审查补的半边：模型判词必须仍落 MODEL_JUDGED 档（拟合排除）。若 fromLocalCheck
            // 被写反/恒 true，κ≈0.70 噪声的开放作答会**静默进拟合集**，这条断言就是唯一会红的门。
            ReviewLogSink.SOURCE_KIND_MODEL_JUDGED,
            database.reviewLogEntries.single().sourceKind,
        )
    }

    @Test
    fun aVerdictFromBeforeThisReviewItemDoesNotSettleIt() = runBlocking {
        // 上一次推进（或本次复习开始）之前留下的判词，早该被上一项消费掉；若拿它结算，
        // 学生一进复习页就会被旧判词直接判定通过。检查题作答时间早于复习会话开始。
        val database = seededDatabase()
        database.tutorTurnResponses += choiceResponse(
            correct = true,
            submittedAtEpochMillis = 1_500,
        )

        val result = settle(database)

        assertEquals(TutorJudgedReviewSettlementStatus.NO_VERDICT, result.status)
        assertEquals(0, database.attemptCount)
    }

    @Test
    fun aPrerequisiteKnowledgeVerdictNoLongerFlipsTheWholeQuestion() = runBlocking {
        // AUD-11 ① 的反例本体（报告 §1.2）：本题要考 kc-target（NEGATIVE，学生没做出来），
        // 前置 kc-prereq 上有一条**更新**的半权 POSITIVE（0.15×0.5=0.075，"这一步会了"）。
        // 修前：只按时间取最新 → 整题判成做对并按 0.5 记账（6.67× 放大）；修后：不锚定在本题
        // KC 集上的证据无权驱动整题，判定由锚定的那条 NEGATIVE 做出。
        val database = seededDatabase()
        database.recordedChatEvidence += modelVerdict(
            direction = "NEGATIVE",
            nodeId = QUESTION_KNOWLEDGE_NODE_ID,
            evidenceId = "evidence-target",
            createdAtEpochMillis = 2_600,
            weight = 0.35,
        )
        database.recordedChatEvidence += modelVerdict(
            direction = "POSITIVE",
            nodeId = PREREQUISITE_KNOWLEDGE_NODE_ID,
            evidenceId = "evidence-prereq",
            createdAtEpochMillis = 2_700,
            weight = 0.075,
        )

        val result = settle(database)

        assertEquals(TutorJudgedReviewSettlementStatus.RECORDED, result.status)
        assertFalse(requireNotNull(result.isCorrect))
        assertEquals(
            LearningEvidenceReason.MODEL_JUDGED_INCORRECT,
            database.lastAttemptCommand?.evidence?.reason,
        )
        // 归属记的是驱动证据（target 节点），不是那条更"新"的前置证据。
        val attribution = requireNotNull(database.lastEvidenceSnapshot?.attributions?.single())
        assertEquals(QUESTION_KNOWLEDGE_NODE_ID, attribution.knowledgeNodeId)
        assertEquals(0.35, attribution.weight, 0.0)
    }

    @Test
    fun evidenceOnAnUnrelatedKnowledgeNodeIsNotAVerdict() = runBlocking {
        // 只有外部（另一科）节点的证据：没有任何锚定在本题 KC 集上的 accepted 证据 →
        // 保持阻塞（NO_VERDICT），不写 attempt、不落复习日志、不给假的记忆更新。
        val database = seededDatabase()
        database.recordedChatEvidence += modelVerdict(
            direction = "POSITIVE",
            nodeId = UNRELATED_KNOWLEDGE_NODE_ID,
        )

        val result = settle(database)

        assertEquals(TutorJudgedReviewSettlementStatus.NO_VERDICT, result.status)
        assertNull(result.isCorrect)
        assertEquals(0, database.attemptCount)
        assertTrue(database.reviewLogEntries.isEmpty())
        assertNull(database.lastEvidenceSnapshot)
    }

    @Test
    fun onlyExternalNodeEvidenceLeavesTheItemBlocked() = runBlocking {
        // 外部节点证据即使**更新**（2_900 > 会话锚定 2_500）也不构成判词：不锚定 = 不是判词。
        val database = seededDatabase()
        database.recordedChatEvidence += modelVerdict(
            direction = "NEGATIVE",
            nodeId = PREREQUISITE_KNOWLEDGE_NODE_ID,
            createdAtEpochMillis = 2_900,
        )

        val result = settle(database)

        assertEquals(TutorJudgedReviewSettlementStatus.NO_VERDICT, result.status)
        assertEquals(0, database.attemptCount)
        assertEquals(PRACTICE_UNIT_ID, result.nextPracticeUnitId)
    }

    @Test
    fun aQuestionWithoutKnowledgeNodesCanNeverAnchorAVerdict() = runBlocking {
        // 诚实未分类的题（KC 集为空）：每条证据都在题面 KC 集之外 → 恒 NO_VERDICT。
        // 这正是"锚定"的代价：宁可不判，也不把跨粒度的证据记成这次做对。
        val database = seededDatabase(knowledgeNodeIds = emptySet())
        database.recordedChatEvidence += modelVerdict(direction = "POSITIVE")

        val result = settle(database)

        assertEquals(TutorJudgedReviewSettlementStatus.NO_VERDICT, result.status)
        assertEquals(0, database.attemptCount)
    }

    @Test
    fun anAnchoredVerdictWithoutAMatchingBindingStaysBlocked() = runBlocking {
        // 锚定由题面 KC 集与库内现役绑定共同核实：KC 集里有节点、但库里没有该 revision 的绑定行
        // （异常态）→ 不伪造归属、不推进（与"没有锚定证据"同一条出口）。
        val database = seededDatabase(bindingRevisionId = "some-other-revision")
        database.recordedChatEvidence += modelVerdict(direction = "POSITIVE")

        val result = settle(database)

        assertEquals(TutorJudgedReviewSettlementStatus.NO_VERDICT, result.status)
        assertEquals(0, database.attemptCount)
        assertNull(database.lastEvidenceSnapshot)
    }

    @Test
    fun theSettledSnapshotRecordsTheDrivingEvidenceWithItsBinding() = runBlocking {
        // 归属落账的形状：绑定 = 该题该 revision 的现役绑定行（写入口会按五元组核对它），
        // 节点 = 驱动证据的节点，权重 = 驱动证据的生效权重，快照 taxonomy 随该绑定。
        val database = seededDatabase()
        database.recordedChatEvidence += modelVerdict(direction = "POSITIVE")

        val result = settle(database)

        assertEquals(TutorJudgedReviewSettlementStatus.RECORDED, result.status)
        val snapshot = requireNotNull(database.lastEvidenceSnapshot)
        val attribution = snapshot.attributions.single()
        assertEquals(QUESTION_BINDING_ID, attribution.bindingId)
        assertEquals(QUESTION_KNOWLEDGE_NODE_ID, attribution.knowledgeNodeId)
        assertEquals("captured-revision", attribution.basisRevisionId)
        assertEquals(BINDING_TAXONOMY_VERSION, attribution.taxonomyVersion)
        assertEquals(0.15, attribution.weight, 0.0)
        assertEquals(EvidenceAttributionRole.PRIMARY, attribution.role)
        assertEquals(EvidenceAttributionCertainty.DIRECT, attribution.certainty)
        // 归属的 basisRevision/taxonomy 必须与快照逐字一致（DatabaseContractValidator），
        // 且 taxonomy 必须是那条绑定行的 taxonomy（AttemptTransactionDao 的五元组核对）。
        assertEquals(attribution.taxonomyVersion, snapshot.taxonomyVersion)
        assertEquals("captured-revision", snapshot.problemRevisionId)
        assertNotEquals(LocalModelJudgedContract.TAXONOMY_VERSION, snapshot.taxonomyVersion)
    }

    @Test
    fun aNewerSessionWithOnlyExternalEvidenceDoesNotHideTheAnchoredOne() = runBlocking {
        // KD-36 的多会话回看与 AUD-11 ① 的锚定门共存：最新锚定的会话只有外部节点证据
        // （不构成判词）→ 继续往旧会话找，旧会话里锚定的那条仍然是判词。
        val database = seededDatabase()
        database.recordedChatEvidence += modelVerdict(
            direction = "POSITIVE",
            evidenceId = "evidence-anchored",
            createdAtEpochMillis = 2_700,
        )
        database.recordedChatEvidence += modelVerdict(
            direction = "NEGATIVE",
            sessionId = "tutor-session-2",
            nodeId = PREREQUISITE_KNOWLEDGE_NODE_ID,
            evidenceId = "evidence-external",
            createdAtEpochMillis = 3_500,
        )
        database.tutorSessionAnchors += anchor(sessionId = TUTOR_SESSION_ID, anchoredAt = 2_500)
        database.tutorSessionAnchors += anchor(sessionId = "tutor-session-2", anchoredAt = 3_400)

        val result = settle(database)

        assertEquals(TutorJudgedReviewSettlementStatus.RECORDED, result.status)
        assertTrue(requireNotNull(result.isCorrect))
        // 归属仍指向**驱动**那条（旧会话的 anchored 证据）的节点。
        assertEquals(
            QUESTION_KNOWLEDGE_NODE_ID,
            database.lastEvidenceSnapshot?.attributions?.single()?.knowledgeNodeId,
        )
    }

    @Test
    fun rejectedModelVerdictIsNotEvidence() = runBlocking {
        // 被门控拒写的行（rejected_reason 非空，weight 0）只作观察，不能当判定依据。
        val database = seededDatabase()
        database.recordedChatEvidence += modelVerdict(
            direction = "POSITIVE",
            rejectedReason = "EVIDENCE_ANCHOR_MISSING",
        )

        val result = settle(database)

        assertEquals(TutorJudgedReviewSettlementStatus.NO_VERDICT, result.status)
        assertNull(result.isCorrect)
        assertEquals(0, database.attemptCount)
        assertTrue(database.reviewLogEntries.isEmpty())
    }

    @Test
    fun noVerdictAtAllWritesNothingAndLeavesTheQueueWhereItWas() = runBlocking {
        val database = seededDatabase()

        val result = settle(database)

        assertEquals(TutorJudgedReviewSettlementStatus.NO_VERDICT, result.status)
        assertEquals(0, database.attemptCount)
        assertEquals(0, result.progress.currentOrdinal)
        assertEquals(StudyReviewSessionStatus.ACTIVE, result.progress.status)
        assertEquals(PRACTICE_UNIT_ID, result.nextPracticeUnitId)
    }

    @Test
    fun aNewerTutorSessionWithoutAnAnswerDoesNotHideTheAnsweredOne() = runBlocking {
        // KD-36：学生在一个会话里作答，随后又进了一次讲题页（A1：每次进入新开会话）但没作答，
        // 之后退出。此前结算只读"最新锚定"的那个空会话 → 静默 NO_VERDICT：既不结算，也不说
        // 为什么，复习项永远停在第 1 题。现在从最近锚定的会话里找"最近一次形成判定的会话"。
        val database = seededDatabase()
        database.tutorTurnResponses += choiceResponse(correct = true)
        database.tutorSessionAnchors += anchor(sessionId = TUTOR_SESSION_ID, anchoredAt = 2_500)
        database.tutorSessionAnchors += anchor(sessionId = "tutor-session-2", anchoredAt = 3_400)

        val result = settle(database)

        assertEquals(TutorJudgedReviewSettlementStatus.RECORDED, result.status)
        assertTrue(requireNotNull(result.isCorrect))
        assertEquals(1, database.attemptCount)
    }

    @Test
    fun theMostRecentlyAnsweredTutorSessionDecides() = runBlocking {
        // 同一道题答了两次（旧会话答对、新会话答错）：判定取**最近一次作答**，
        // 不能被更早那条判词盖过去——否则重讲之后仍拿第一次的对错记账。
        val database = seededDatabase()
        database.tutorTurnResponses += choiceResponse(correct = true)
        database.tutorTurnResponses += choiceResponse(
            sessionId = "tutor-session-2",
            correct = false,
            submittedAtEpochMillis = 3_500,
        )
        database.tutorSessionAnchors += anchor(sessionId = TUTOR_SESSION_ID, anchoredAt = 2_500)
        database.tutorSessionAnchors += anchor(sessionId = "tutor-session-2", anchoredAt = 3_400)

        val result = settle(database)

        assertEquals(TutorJudgedReviewSettlementStatus.RECORDED, result.status)
        assertFalse(requireNotNull(result.isCorrect))
        assertEquals(
            LearningEvidenceReason.MODEL_JUDGED_INCORRECT,
            database.lastAttemptCommand?.evidence?.reason,
        )
    }

    @Test
    fun anAlreadyConsumedAnswerInTheNewestSessionIsNotReplacedByAnOlderOne() = runBlocking {
        // 最新锚定会话里那条作答发生在**本次复习开始之前**（旧复习留下的）：仍判无判词，
        // 不许因为"再往前还有一个更早的会话"就把更旧的那条翻出来当判定。
        // 这条钉的是回看的边界：新鲜度门槛按"最近一次作答"判，不从历史里挑一条能过的。
        val database = seededDatabase()
        database.tutorTurnResponses += choiceResponse(
            sessionId = "tutor-session-2",
            correct = true,
            submittedAtEpochMillis = 1_500,
        )
        database.tutorTurnResponses += choiceResponse(correct = true, submittedAtEpochMillis = 2_600)
        database.tutorSessionAnchors += anchor(sessionId = TUTOR_SESSION_ID, anchoredAt = 2_500)
        database.tutorSessionAnchors += anchor(sessionId = "tutor-session-2", anchoredAt = 3_400)

        val result = settle(database)

        assertEquals(TutorJudgedReviewSettlementStatus.NO_VERDICT, result.status)
        assertEquals(0, database.attemptCount)
    }

    @Test
    fun replayingTheSameSettlementDoesNotDoubleWrite() = runBlocking {
        val database = seededDatabase()
        database.tutorTurnResponses += choiceResponse(correct = true)

        val settlement = startedSettlement(database)
        val first = settle(database, settlement)
        val replay = settle(database, settlement)

        assertTrue(first.created)
        assertFalse(replay.created)
        assertEquals(first.attemptId, replay.attemptId)
        assertEquals(1, database.attemptCount)
        assertEquals(1, database.reviewLogEntries.size)
    }

    @Test
    fun replayingTheSameModelVerdictSettlementKeepsOneSnapshotId() = runBlocking {
        // 模型判词分支的快照 id 是**内容寻址**（结算身份 + 归属内容）：同一条驱动证据重放 →
        // 同一个 id → 真库走"复用旧行"（KD-37 的时间戳容忍），不会因为重跑/新时间戳另写一行。
        val database = seededDatabase()
        database.recordedChatEvidence += modelVerdict(direction = "POSITIVE")

        val settlement = startedSettlement(database)
        val first = settle(database, settlement)
        val firstSnapshotId = requireNotNull(database.lastEvidenceSnapshot).snapshotId
        val replay = settle(database, settlement)

        assertTrue(first.created)
        assertFalse(replay.created)
        assertEquals(firstSnapshotId, requireNotNull(database.lastEvidenceSnapshot).snapshotId)
        assertEquals(1, database.attemptCount)
    }

    @Test
    fun aChangedVerdictContentYieldsANewSnapshotId() = runBlocking {
        // 内容寻址的另一半：判词内容真变（这里：检查题作答接手，归属从"驱动证据"变成空）→
        // id 不同 → 不会撞"同 id 不同载荷"的身份冲突（KD-37 的同类死路）。本用例钉的是
        // **快照身份**这一道门；已落 attempt 的重放冲突是另一条既有语义（真库 DAO 的
        // `readAttemptReplay`），不在本用例范围。
        val database = seededDatabase()
        database.recordedChatEvidence += modelVerdict(direction = "POSITIVE")
        val settlement = startedSettlement(database)
        settle(database, settlement)
        val modelVerdictSnapshotId = requireNotNull(database.lastEvidenceSnapshot).snapshotId

        database.tutorTurnResponses += choiceResponse(correct = true)
        val second = settle(database, settlement)

        assertEquals(TutorJudgedReviewSettlementStatus.RECORDED, second.status)
        val localCheckSnapshot = requireNotNull(database.lastEvidenceSnapshot)
        assertNotEquals(modelVerdictSnapshotId, localCheckSnapshot.snapshotId)
        assertTrue(localCheckSnapshot.attributions.isEmpty())
    }

    // --- helpers -------------------------------------------------------------

    /**
     * 这道保存题：KC 集默认带一个知识点（AUD-11 ① 的锚定门按 `MistakeRecord.knowledgeNodeIds`
     * 判定），并给该节点播一条 `captured-revision` 上的现役绑定——归属落账必须是这条真实绑定行
     * （写入口按五元组核对）。`knowledgeNodeIds = emptySet()` 用来构造"诚实未分类"的题。
     */
    private fun seededDatabase(
        knowledgeNodeIds: Set<String> = setOf(QUESTION_KNOWLEDGE_NODE_ID),
        bindingRevisionId: String = "captured-revision",
    ): FakeStudyDatabasePort {
        val database = FakeStudyDatabasePort()
        database.addMistake(
            MistakeRecord(
                entryId = "captured-entry",
                problemId = "captured-problem",
                problemRevisionId = "captured-revision",
                practiceUnitId = PRACTICE_UNIT_ID,
                sourceKey = "capture:photo-1",
                subject = "MATH",
                title = "函数原题",
                problemMarkdown = "求函数的单调区间。",
                status = "ACTIVE",
                createdAtEpochMillis = 1_000,
                nextReviewAtEpochMillis = null,
                retrievability = null,
                knowledgeNodeIds = knowledgeNodeIds,
            ),
        )
        database.addPracticeUnitKnowledgeBinding(
            PracticeUnitKnowledgeBindingRecord(
                bindingId = QUESTION_BINDING_ID,
                practiceUnitId = PRACTICE_UNIT_ID,
                knowledgeNodeId = QUESTION_KNOWLEDGE_NODE_ID,
                basisRevisionId = bindingRevisionId,
                taxonomyVersion = BINDING_TAXONOMY_VERSION,
                acceptedAtEpochMillis = 2_400,
            ),
        )
        database.tutorSessionAnchor = TutorSessionProblemAnchorRecord(
            learnerId = LEARNER_ID,
            sessionId = TUTOR_SESSION_ID,
            problemRevisionId = "captured-revision",
            practiceUnitId = PRACTICE_UNIT_ID,
            source = "SAVED_MISTAKE",
            anchoredAtEpochMillis = 2_500,
        )
        return database
    }

    /**
     * 先开一个真实的计划+会话（sessionId/version 由仓库自己产），把结算请求固定下来，
     * 再用它结算。重放测试必须复用同一个请求——会话完成后再开一次会撞计划冲突，
     * 那是另一个失败路径，不是这里要测的幂等。
     */
    private suspend fun startedSettlement(database: FakeStudyDatabasePort): TutorJudgedReviewSettlement =
        withRepository(database) { repository ->
            val started = requireNotNull(
                repository.startOrResumeReviewSession("settle-start", 2_000),
            )
            TutorJudgedReviewSettlement(
                requestId = "settle-1",
                sessionId = started.sessionId,
                expectedStateVersion = started.stateVersion,
                practiceUnitId = PRACTICE_UNIT_ID,
                presentationId = "presentation:tutor-judged:$PRACTICE_UNIT_ID",
                occurredAtEpochMillis = 3_000,
                durationSeconds = 90,
            )
        }

    private suspend fun settle(database: FakeStudyDatabasePort) =
        settle(database, startedSettlement(database))

    private suspend fun settle(
        database: FakeStudyDatabasePort,
        settlement: TutorJudgedReviewSettlement,
    ) = withRepository(database) { repository ->
        repository.settleTutorJudgedReview(settlement)
    }

    private suspend fun <T> withRepository(
        database: FakeStudyDatabasePort,
        block: suspend (RoomBackedStudyExperienceRepository) -> T,
    ): T {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val repository = RoomBackedStudyExperienceRepository(
                database = database,
                applicationScope = scope,
                clock = Clock.fixed(
                    Instant.parse("2026-01-02T08:00:00Z"),
                    ZoneId.of("Asia/Shanghai"),
                ),
                studyZoneId = ZoneId.of("Asia/Shanghai"),
            )
            repository.initialize()
            return block(repository)
        } finally {
            scope.cancel()
        }
    }

    /** 这道题上的一个讲题会话锚（KD-36 的多会话用例按锚定时刻新旧播种）。 */
    private fun anchor(sessionId: String, anchoredAt: Long) = TutorSessionProblemAnchorRecord(
        learnerId = LEARNER_ID,
        sessionId = sessionId,
        problemRevisionId = "captured-revision",
        practiceUnitId = PRACTICE_UNIT_ID,
        source = "SAVED_MISTAKE",
        anchoredAtEpochMillis = anchoredAt,
    )

    private fun choiceResponse(
        correct: Boolean,
        cycleOrdinal: Int = 1,
        turnOrdinal: Int = 1,
        submittedAtEpochMillis: Long = 2_600,
        sessionId: String = TUTOR_SESSION_ID,
    ) = TutorTurnResponseRecord(
        sessionId = sessionId,
        questionDocumentId = "document-1",
        revisionNumber = 1,
        cycleOrdinal = cycleOrdinal,
        turnOrdinal = turnOrdinal,
        diagnosticStemMarkdown = "这一步的依据是什么？",
        selectedChoiceId = "choice-a",
        selectedChoiceMarkdown = "因为单调",
        selectionWasCorrect = correct,
        feedbackMarkdown = "已显示反馈",
        requestedMove = null,
        solutionRevealed = false,
        choiceSubmittedAtEpochMillis = submittedAtEpochMillis,
        submittedAtEpochMillis = submittedAtEpochMillis,
        updatedAtEpochMillis = submittedAtEpochMillis,
    )

    /**
     * 一条讲题会话里落下的 chat-evidence。节点默认 = 本题 KC 集里的那个（锚定），
     * 需要构造"前置/无关节点""另一个会话"的证据时显式换 [nodeId] / [sessionId]。
     */
    private fun modelVerdict(
        direction: String,
        rejectedReason: String? = null,
        nodeId: String = QUESTION_KNOWLEDGE_NODE_ID,
        evidenceId: String = "evidence-1",
        createdAtEpochMillis: Long = 2_700,
        weight: Double = 0.15,
        sessionId: String = TUTOR_SESSION_ID,
    ) = LearnerChatEvidenceEntity(
        evidence_id = evidenceId,
        learner_id = LEARNER_ID,
        conversation_id = TutorConversationIds.captured(sessionId),
        knowledge_node_id = nodeId,
        direction = direction,
        weight = if (rejectedReason == null) weight else 0.0,
        reason_markdown = "模型判词",
        confidence = 0.9,
        source_kind = "MODEL_CHAT",
        created_at_epoch_millis = createdAtEpochMillis,
        rejected_reason = rejectedReason,
        rejected_at_epoch_millis = rejectedReason?.let { createdAtEpochMillis },
    )

    private companion object {
        const val LEARNER_ID = "learner-1"
        const val PRACTICE_UNIT_ID = "captured-practice-unit"
        const val TUTOR_SESSION_ID = "tutor-session-1"
        const val QUESTION_KNOWLEDGE_NODE_ID = "knowledge:function-monotonicity"
        const val PREREQUISITE_KNOWLEDGE_NODE_ID = "knowledge:function-domain"
        const val UNRELATED_KNOWLEDGE_NODE_ID = "knowledge:optics-refraction"
        const val QUESTION_BINDING_ID = "binding:captured:function-monotonicity"

        /** 分类确认写进绑定行的 taxonomy（`RoomMistakeOrganizationRepository` 的 `local-policy-v1`）。 */
        const val BINDING_TAXONOMY_VERSION = "local-policy-v1"
    }
}
