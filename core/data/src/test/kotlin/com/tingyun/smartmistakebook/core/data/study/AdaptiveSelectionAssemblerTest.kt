package com.tingyun.smartmistakebook.core.data.study

import com.tingyun.smartmistakebook.core.database.AssessmentItemSnapshotSeedRecord
import com.tingyun.smartmistakebook.core.database.KnowledgeNodeRelationRecord
import com.tingyun.smartmistakebook.core.database.KnowledgeNodeSeedRecord
import com.tingyun.smartmistakebook.core.database.LearningLedgerRead
import com.tingyun.smartmistakebook.core.database.LearningLedgerReadStatus
import com.tingyun.smartmistakebook.core.database.MistakeRecord
import com.tingyun.smartmistakebook.core.database.PersistedLearningLedgerEvent
import com.tingyun.smartmistakebook.core.database.StudyDbValue
import com.tingyun.smartmistakebook.core.domain.AdaptiveClarificationConsumer
import com.tingyun.smartmistakebook.core.domain.AdaptiveClarificationRequest
import com.tingyun.smartmistakebook.core.domain.AdaptiveCorrectnessPredictor
import com.tingyun.smartmistakebook.core.domain.AdaptiveDecisionKind
import com.tingyun.smartmistakebook.core.domain.AdaptiveQuestionCandidate
import com.tingyun.smartmistakebook.core.domain.AdaptiveQuestionSelector
import com.tingyun.smartmistakebook.core.domain.AdaptiveSelectionRequest
import com.tingyun.smartmistakebook.core.domain.AdaptiveTargetKind
import com.tingyun.smartmistakebook.core.domain.AnswerKeyVerificationSource
import com.tingyun.smartmistakebook.core.domain.AnswerKeyVerificationSourceVerifier
import com.tingyun.smartmistakebook.core.domain.EloAnswerRecord
import com.tingyun.smartmistakebook.core.domain.EloCorrectnessPredictor
import com.tingyun.smartmistakebook.core.domain.EloLearnerState
import com.tingyun.smartmistakebook.core.domain.LearningCoreVersions
import com.tingyun.smartmistakebook.core.domain.LearningProjector
import com.tingyun.smartmistakebook.core.domain.PredictedCorrectnessInterval
import com.tingyun.smartmistakebook.core.model.AnswerKeyVerification
import com.tingyun.smartmistakebook.core.model.CalibrationSnapshot
import com.tingyun.smartmistakebook.core.model.CalibrationSupport
import com.tingyun.smartmistakebook.core.model.ChatEvidenceSubmitted
import com.tingyun.smartmistakebook.core.model.EvidenceCorrection
import com.tingyun.smartmistakebook.core.model.EvidenceCorrectionKind
import com.tingyun.smartmistakebook.core.model.IndependentCorrectObservation
import com.tingyun.smartmistakebook.core.model.KnowledgeMasteryState
import com.tingyun.smartmistakebook.core.model.LearnerSnapshot
import com.tingyun.smartmistakebook.core.model.LearningEvidenceDirection
import com.tingyun.smartmistakebook.core.model.LearningLedgerEvent
import com.tingyun.smartmistakebook.core.model.MasteryStatus
import com.tingyun.smartmistakebook.core.model.ProjectionCheckpoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * G5-A 读侧装配（批次 21 T2）：台账有效流解析（通道过滤 / VOID/REPLACE 链 /
 * 选项数逐事件降级）、GAP 与坏前缀的 fail-closed、候选装配（当次会话题面目快照 +
 * 既有 itemFamily 映射的错题）、水位缓存的透明性。
 *
 * 消灭的失败：候选与预测没有生产装配者时，选择器永远拿不到 `candidates`；
 * 而装配一旦"猜"（读不到就编选项数、修正语义与投影不一致、GAP 时给半套状态），
 * 走廊判据就会在不可信输入上放行。本用例把每个降级出口钉在 fail-closed 一侧。
 *
 * 假库复用 `FakeStudyDatabasePort`（同包、internal；账本夹具 = `projectionLedger`，
 * 题面行 = `assessmentItemSnapshots`，错题 = `addMistake`）。
 */
class AdaptiveSelectionAssemblerTest {

    private val learnerId = RoomBackedStudyExperienceRepository.DEFAULT_LEARNER_ID
    private val sessionId = 5_000L

    // ---- 台账 → 有效作答流 ----

    @Test
    fun `quiz evidence stream filters the channel and resolves option counts per event`() = runBlocking {
        val database = FakeStudyDatabasePort()
        database.assessmentItemSnapshots[
            knowledgeQuizItemSnapshotId(learnerId, sessionId, "kc-a"),
        ] = snapshotRow(sessionId = sessionId, nodeId = "kc-a", options = 4)
        database.projectionLedger = listOf(
            ledgerEvent(quizEvidence("knowledge-quiz:req-1:kc-a", "kc-a", sessionId, POSITIVE, 1_000L, 1L)),
            // 讲题通道（chat-ev:）不是机械判定的对错流，必须排除（R4 §2.4）。
            ledgerEvent(
                ChatEvidenceSubmitted(
                    evidenceId = "chat-ev:req-2:mastery:update:kc-a",
                    conversationId = "tutor-chat:1",
                    knowledgeNodeId = "kc-a",
                    direction = POSITIVE,
                    weight = ChatEvidenceSubmitted.POSITIVE_WEIGHT,
                    reasonMarkdown = "模型判词",
                    confidence = 0.8,
                    occurredAtEpochMillis = 1_500L,
                    eventSequence = 2L,
                ),
            ),
            // 无题面快照 → 该事件 optionsCount = null（guess 修正关闭），不影响其它事件。
            ledgerEvent(quizEvidence("knowledge-quiz:req-3:kc-b", "kc-b", 6_000L, NEGATIVE, 2_000L, 3L)),
            // 会话标识不是本通道形状 → 同样逐事件降级。
            ledgerEvent(
                quizEvidence("knowledge-quiz:req-4:kc-c", "kc-c", sessionId, POSITIVE, 3_000L, 4L)
                    .copy(conversationId = "foreign-session:9"),
            ),
        )

        val stream = requireNotNull(assembler(database).loadEloAnswerStream())

        assertEquals(listOf(1L, 3L, 4L), stream.map(EloAnswerRecord::eventSequence))
        assertEquals(listOf(true, false, true), stream.map(EloAnswerRecord::isCorrect))
        assertNull("缺少题面快照的事件必须逐事件降级（guess=0），不全局失效", stream[1].optionsCount)
        assertNull(stream[2].optionsCount)
        assertEquals(4, stream[0].optionsCount)
        assertEquals(1_000L, stream[0].occurredAtEpochMillis)
    }

    @Test
    fun `void and replace corrections resolve with mastery-projection semantics`() = runBlocking {
        fun replace(target: String, direction: LearningEvidenceDirection, sequence: Long) = EvidenceCorrection(
            correctionId = "correction-$sequence",
            targetEvidenceId = target,
            kind = EvidenceCorrectionKind.REPLACE,
            replacementDirection = direction,
            replacementWeight = ChatEvidenceSubmitted.NEGATIVE_WEIGHT,
            reasonMarkdown = "改判",
            occurredAtEpochMillis = 9_000L + sequence,
            eventSequence = sequence,
        )
        fun void(target: String, sequence: Long) = EvidenceCorrection(
            correctionId = "correction-$sequence",
            targetEvidenceId = target,
            kind = EvidenceCorrectionKind.VOID,
            replacementDirection = LearningEvidenceDirection.NONE,
            replacementWeight = 0.0,
            reasonMarkdown = "撤销",
            occurredAtEpochMillis = 9_000L + sequence,
            eventSequence = sequence,
        )

        val evidence = quizEvidence("knowledge-quiz:req-1:kc-a", "kc-a", sessionId, NEGATIVE, 1_000L, 1L)
        val later = quizEvidence("knowledge-quiz:req-2:kc-a", "kc-a", sessionId, POSITIVE, 2_000L, 2L)

        // REPLACE：目标保持**原位置与原时间**，按新对错生效；后续事件位置不变。
        run {
            val database = FakeStudyDatabasePort()
            database.projectionLedger = listOf(
                ledgerEvent(evidence),
                ledgerEvent(later),
                ledgerEvent(replace(evidence.evidenceId, POSITIVE, sequence = 3L)),
            )
            val stream = requireNotNull(assembler(database).loadEloAnswerStream())
            assertEquals(listOf(1L, 2L), stream.map(EloAnswerRecord::eventSequence))
            assertEquals(true, stream[0].isCorrect)
            assertEquals(1_000L, stream[0].occurredAtEpochMillis)
        }

        // VOID：整条排除，不贡献任何折叠步骤。
        run {
            val database = FakeStudyDatabasePort()
            database.projectionLedger = listOf(
                ledgerEvent(evidence),
                ledgerEvent(later),
                ledgerEvent(void(evidence.evidenceId, sequence = 3L)),
            )
            val stream = requireNotNull(assembler(database).loadEloAnswerStream())
            assertEquals(listOf(2L), stream.map(EloAnswerRecord::eventSequence))
        }

        // 更正的更正（链式，与 LearningProjector.resolveEvidenceEffect 同语义）：
        // 撤销更正 → 原改判失效、证据回到原方向；改判更正 → 以最后一层载荷为准。
        run {
            val database = FakeStudyDatabasePort()
            database.projectionLedger = listOf(
                ledgerEvent(evidence),
                ledgerEvent(replace(evidence.evidenceId, POSITIVE, sequence = 2L)),
                ledgerEvent(void("correction-2", sequence = 3L)),
            )
            val stream = requireNotNull(assembler(database).loadEloAnswerStream())
            assertEquals(false, stream.single().isCorrect)
        }
        run {
            val database = FakeStudyDatabasePort()
            database.projectionLedger = listOf(
                ledgerEvent(evidence),
                ledgerEvent(replace(evidence.evidenceId, POSITIVE, sequence = 2L)),
                ledgerEvent(replace("correction-2", NEGATIVE, sequence = 3L)),
            )
            val stream = requireNotNull(assembler(database).loadEloAnswerStream())
            assertEquals(false, stream.single().isCorrect)
        }
        run {
            val database = FakeStudyDatabasePort()
            database.projectionLedger = listOf(
                ledgerEvent(evidence),
                ledgerEvent(replace(evidence.evidenceId, POSITIVE, sequence = 2L)),
                ledgerEvent(void("correction-2", sequence = 3L)),
                ledgerEvent(void("correction-3", sequence = 4L)),
            )
            val stream = requireNotNull(assembler(database).loadEloAnswerStream())
            // 撤销被再次撤销 → 改判恢复生效。
            assertEquals(true, stream.single().isCorrect)
        }
    }

    @Test
    fun `gap ledger and broken prefixes fail closed`() = runBlocking {
        val gapped = GappedDatabasePort(LearningLedgerReadStatus.GAP)
        gapped.assessmentItemSnapshots[
            knowledgeQuizItemSnapshotId(learnerId, sessionId, "kc-a"),
        ] = snapshotRow(sessionId = sessionId, nodeId = "kc-a")
        gapped.projectionLedger = listOf(
            ledgerEvent(quizEvidence("knowledge-quiz:req-1:kc-a", "kc-a", sessionId, POSITIVE, 1_000L, 1L)),
        )
        val result = assembler(gapped)
        assertNull(result.loadEloAnswerStream())
        assertNull(result.currentEloState())
        val candidates = result.assembleCandidates(sessionId, setOf("kc-a"), 2_000L)
        assertEquals(1, candidates.size)
        assertNull("GAP 台账 → 全部候选无预测（选择器既有 fail-closed 出口）", candidates.single().predictedCorrectness)

        // 悬空更正（目标不在完整前缀里）：解析异常按读失败处置，不返回半套流。
        val dangling = FakeStudyDatabasePort()
        dangling.projectionLedger = listOf(
            ledgerEvent(
                EvidenceCorrection(
                    correctionId = "correction-1",
                    targetEvidenceId = "knowledge-quiz:missing:kc-a",
                    kind = EvidenceCorrectionKind.VOID,
                    replacementDirection = LearningEvidenceDirection.NONE,
                    replacementWeight = 0.0,
                    reasonMarkdown = "悬空",
                    occurredAtEpochMillis = 9_000L,
                    eventSequence = 1L,
                ),
            ),
        )
        assertNull(assembler(dangling).loadEloAnswerStream())
    }

    // ---- 候选装配 ----

    @Test
    fun `candidates assemble from the current session pool and saved mistake families`() = runBlocking {
        val database = FakeStudyDatabasePort()
        database.assessmentItemSnapshots[
            knowledgeQuizItemSnapshotId(learnerId, sessionId, "kc-a"),
        ] = snapshotRow(sessionId = sessionId, nodeId = "kc-a", options = 4)
        database.assessmentItemSnapshots[
            knowledgeQuizItemSnapshotId(learnerId, sessionId, "kc-b"),
        ] = snapshotRow(
            sessionId = sessionId,
            nodeId = "kc-b",
            options = 2,
            verificationStatus = StudyDbValue.VerificationStatus.USER_ASSERTED,
        )
        database.assessmentItemSnapshots[
            knowledgeQuizItemSnapshotId(learnerId, sessionId, "kc-c"),
        ] = snapshotRow(
            sessionId = sessionId,
            nodeId = "kc-c",
            options = 4,
            answerRevealState = "REVEALED",
        )
        database.assessmentItemSnapshots[
            knowledgeQuizItemSnapshotId(learnerId, sessionId, "kc-d"),
        ] = snapshotRow(sessionId = sessionId, nodeId = "kc-d", optionsSnapshot = "[not-json")
        database.addMistake(mistake("pu-1", setOf("kc-a")))
        database.addMistake(mistake("pu-2", emptySet()))

        val candidates = assembler(database).assembleCandidates(
            sessionId = sessionId,
            targetKnowledgeNodeIds = setOf("kc-a", "kc-b", "kc-c", "kc-d"),
            decisionAtEpochMillis = 2_000L,
        )

        // 节点字典序 → 错题 practiceUnitId 序；损坏行不产候选。
        assertEquals(
            listOf("knowledge-quiz:item:kc-a", "knowledge-quiz:item:kc-b", "knowledge-quiz:item:kc-c", "pu-1", "pu-2"),
            candidates.map(AdaptiveQuestionCandidate::assessmentItemId),
        )
        val (snapshotA, snapshotB, snapshotC, mistakeOne, mistakeTwo) = candidates

        // 模型生成行生于 UNKNOWN → 未核验（绝不静默放行）；USER_ASSERTED → 核验为真。
        assertEquals(false, snapshotA.contentVerified)
        assertEquals(true, snapshotB.contentVerified)
        assertEquals(false, snapshotC.contentVerified)
        // 揭示态按行现读，不假设为 HIDDEN。
        assertEquals(false, snapshotA.answerWouldBeRevealed)
        assertEquals(true, snapshotC.answerWouldBeRevealed)
        // 题面族的 itemFamily 就是题面自身（同节点跨会话的题面 id 是常量）。
        assertEquals("knowledge-quiz:item:kc-a", snapshotA.itemFamilyId)
        assertEquals(AdaptiveTargetKind.FOUNDATION, snapshotA.targetKind)
        assertEquals(0.0, snapshotA.informationValue, 0.0)
        assertEquals(setOf("kc-a"), snapshotA.knowledgeNodeIds)

        // 空台账 = 无历史（INITIAL，非 null）：冷启动预测照出，但区间全宽 → 走廊必拒。
        assertEquals(FLAT_INTERVAL, snapshotA.predictedCorrectness)
        // 2 选项 → 机会锚 0.5，p=0.75 → [0.25, 1.0]。
        assertEquals(FLAT_INTERVAL_TWO_OPTIONS, snapshotB.predictedCorrectness)

        // 错题：itemFamily 与 StudyReviewPlannerService :373 同源；核验面不可判定 → 恒假。
        assertEquals("saved-question:pu-1", mistakeOne.itemFamilyId)
        assertEquals(false, mistakeOne.contentVerified)
        assertEquals(setOf("kc-a"), mistakeOne.knowledgeNodeIds)
        // 错题不锚定选项数（guess 修正关闭）→ guess=0 的点预测，区间全宽。
        assertEquals(FLAT_INTERVAL_ZERO_GUESS, mistakeOne.predictedCorrectness)
        // 空绑定 → pseudo 兜底形态同源（只读，不物化）。
        assertEquals(setOf("pseudo:MATH"), mistakeTwo.knowledgeNodeIds)
        assertTrue(mistakeTwo.predictedCorrectness != null)
    }

    @Test
    fun `no snapshot and no mistake yields no candidates without failing`() = runBlocking {
        val database = FakeStudyDatabasePort()

        val candidates = assembler(database).assembleCandidates(sessionId, setOf("kc-a"), 2_000L)

        assertEquals(emptyList<AdaptiveQuestionCandidate>(), candidates)
    }

    @Test
    fun `predictions follow the replayed state of the ledger prefix`() = runBlocking {
        val database = FakeStudyDatabasePort()
        database.assessmentItemSnapshots[
            knowledgeQuizItemSnapshotId(learnerId, sessionId, "kc-a"),
        ] = snapshotRow(sessionId = sessionId, nodeId = "kc-a", options = 4)
        database.projectionLedger = listOf(
            ledgerEvent(quizEvidence("knowledge-quiz:req-1:kc-a", "kc-a", sessionId, POSITIVE, 100_000L, 1L)),
        )

        val candidate = assembler(database)
            .assembleCandidates(sessionId, setOf("kc-a"), decisionAtEpochMillis = 100_000L)
            .single()

        // 一次正确作答后 n=1：h = 0.5/√2 ≈ 0.35355，p = '积极但未收敛'的点预测；
        // 区间仍远宽于走廊 → 选择器必拒（本用例只钉装配把状态接进预测）。
        val interval = requireNotNull(candidate.predictedCorrectness)
        assertTrue(interval.lowerBound < 0.55 || interval.upperBound > 0.85)
    }

    // ---- 并发/取消安全（批次 21 复核） ----

    @Test
    fun `coroutine cancellation propagates instead of becoming a fail-closed null`() {
        val database = object : FakeStudyDatabasePort() {
            override suspend fun loadLearningLedger(learnerId: String): LearningLedgerRead =
                throw CancellationException("cancelled by host")
        }
        val assembler = assembler(database)

        val calls: List<suspend () -> Any?> = listOf(
            { assembler.loadEloAnswerStream() },
            { assembler.currentEloState() },
            { assembler.assembleCandidates(sessionId, setOf("kc-a"), 2_000L) },
        )
        for ((index, call) in calls.withIndex()) {
            val thrown = try {
                runBlocking { call() }
                null
            } catch (error: Throwable) {
                error
            }
            assertTrue(
                "call #$index must rethrow CancellationException, not swallow it as null/empty: $thrown",
                thrown is CancellationException,
            )
        }
    }

    // ---- 生产候选的 fail-closed 出口 ----

    @Test
    fun `assembled production candidates stay fail-closed to clarify until verified`() = runBlocking {
        val database = FakeStudyDatabasePort()
        // 唯一生产写入方（KnowledgeQuizItemStore.record）固定 UNKNOWN；错题候选恒 false。
        database.assessmentItemSnapshots[
            knowledgeQuizItemSnapshotId(learnerId, sessionId, "kc-a"),
        ] = snapshotRow(sessionId = sessionId, nodeId = "kc-a")
        database.addMistake(mistake("pu-1", setOf("kc-a")))
        val candidates = assembler(database).assembleCandidates(sessionId, setOf("kc-a"), 2_000L)
        assertEquals(2, candidates.size)
        assertTrue("两个候选源都必须未核验（fail-closed）", candidates.none { it.contentVerified })

        val decision = AdaptiveQuestionSelector().select(
            AdaptiveSelectionRequest(
                learnerSnapshot = LearnerSnapshot(
                    learnerId = learnerId,
                    knowledgeMasteryStates = mapOf("kc-a" to knowledgeState("kc-a", stabilityDays = 5.0)),
                    checkpoint = ProjectionCheckpoint(1, LearningProjector.VERSION, 1_000),
                    generatedAtEpochMillis = 1_000,
                ),
                targetKnowledgeNodeIds = setOf("kc-a"),
                requestedTargetKind = AdaptiveTargetKind.FOUNDATION,
                userRequestedQuestion = true,
                candidates = candidates,
                decisionAtEpochMillis = 2_000L,
            ),
        )

        // 在"没有核验写方、feature/app 也没有 CLARIFY_OR_CONFIRM 消费者"之前，G5-A
        // 到不了 ASK。这条断言是死端的回归钉：若未来把 contentVerified 放宽为 true，
        // 本用例会红，迫使改动者同时落地核验写方与消费者。
        assertEquals(AdaptiveDecisionKind.CLARIFY_OR_CONFIRM, decision.kind)
        assertNull(decision.selectedAssessmentItemId)
        assertNull("未选中候选时不得编造预测器版本", decision.predictionModelVersion)
    }

    // ---- KD-67 生产半边：CLARIFY 可消费出口 + 升级后可进 ASK ----

    @Test
    fun `unverified production candidates expose a consumable clarification request`() = runBlocking {
        val database = FakeStudyDatabasePort()
        database.assessmentItemSnapshots[
            knowledgeQuizItemSnapshotId(learnerId, sessionId, "kc-a"),
        ] = snapshotRow(sessionId = sessionId, nodeId = "kc-a")
        val consumer = RecordingClarificationConsumer()

        val outcome = assembler(database).selectAndConsume(
            sessionId = sessionId,
            learnerSnapshot = calibratedSnapshot(),
            targetKnowledgeNodeIds = setOf("kc-a"),
            requestedTargetKind = AdaptiveTargetKind.FOUNDATION,
            decisionAtEpochMillis = 2_000L,
            clarificationConsumer = consumer,
            userRequestedQuestion = true,
        )

        // 校准门通过（否则 CLARIFY 会因证据过期产生）；UNKNOWN 候选进不了走廊 → CLARIFY。
        assertEquals(AdaptiveDecisionKind.CLARIFY_OR_CONFIRM, outcome.decision.kind)
        assertNull(outcome.decision.selectedAssessmentItemId)
        val clarification = requireNotNull(outcome.clarification)
        assertEquals(
            listOf("knowledge-quiz:item:kc-a"),
            clarification.candidates.map { it.assessmentItemId },
        )
        assertEquals(setOf("kc-a"), clarification.targetKnowledgeNodeIds)
        assertTrue("TARGET_CONTENT_NOT_VERIFIED" in clarification.reasonCodes)
        assertEquals(
            "CLARIFY 决策必须到达消费者，而不是算完就丢（KD-67 第二个死端）",
            listOf(clarification),
            consumer.requests,
        )
    }

    @Test
    fun `upgraded user asserted candidate enters the ask corridor with the existing elo predictor`() = runBlocking {
        val database = FakeStudyDatabasePort()
        val snapshotId = knowledgeQuizItemSnapshotId(learnerId, sessionId, "kc-a")
        database.assessmentItemSnapshots[snapshotId] = snapshotRow(sessionId = sessionId, nodeId = "kc-a")
        database.projectionLedger = corridorLedger(sessionId)
        val upgrader = KnowledgeQuizItemVerificationUpgrader(
            database = database,
            learnerId = learnerId,
            statusWriter = SingleColumnVerificationWriter(database),
            sourceVerifier = AnswerKeyVerificationSourceVerifier { source, _, _ ->
                source is AnswerKeyVerificationSource.UserAssertion &&
                    source.confirmationId == "key-correction:req-1:evidence-1"
            },
        )

        val upgrade = upgrader.upgrade(
            sessionId = sessionId,
            knowledgeNodeId = "kc-a",
            targetVerification = AnswerKeyVerification.USER_ASSERTED,
            source = AnswerKeyVerificationSource.UserAssertion(
                confirmationId = "key-correction:req-1:evidence-1",
                confirmedByLearnerId = learnerId,
                confirmedAtEpochMillis = 950_000L,
            ),
        )
        assertTrue("$upgrade", upgrade is KnowledgeQuizItemVerificationUpgradeOutcome.Upgraded)
        val consumer = RecordingClarificationConsumer()

        val outcome = assembler(database).selectAndConsume(
            sessionId = sessionId,
            learnerSnapshot = calibratedSnapshot(ledgerHeadSequence = 12L),
            targetKnowledgeNodeIds = setOf("kc-a"),
            requestedTargetKind = AdaptiveTargetKind.FOUNDATION,
            decisionAtEpochMillis = 961_000L,
            clarificationConsumer = consumer,
            userRequestedQuestion = true,
        )

        // 既有 Elo 预测器从 12 笔作答前缀重放：p≈0.7036、h≈0.1387 → [0.56496, 0.84231]，
        // 落在 FOUNDATION 走廊 [0.55, 0.85] 内；升级把 contentVerified 置真后即可选中。
        // 区间值也直接断言（不只依赖"最终 ASK"反推），把走廊入口的证据钉死。
        val interval = EloCorrectnessPredictor().predictInterval(
            state = requireNotNull(assembler(database).currentEloState()),
            knowledgeNodeId = "kc-a",
            optionsCount = 4,
            decisionAtEpochMillis = 961_000L,
        )
        assertEquals(0.56496, interval.lowerBound, 1e-9)
        assertEquals(0.84231, interval.upperBound, 1e-9)
        assertEquals(AdaptiveDecisionKind.ASK, outcome.decision.kind)
        assertEquals("knowledge-quiz:item:kc-a", outcome.decision.selectedAssessmentItemId)
        assertEquals(LearningCoreVersions.PREDICTOR_ELO, outcome.decision.predictionModelVersion)
        assertNull(outcome.clarification)
        assertTrue("ASK 决策不得触发澄清消费者", consumer.requests.isEmpty())
    }

    // ---- 水位缓存的透明性 ----

    @Test
    fun `watermark cache is transparent and re-folds when the ledger grows`() = runBlocking {
        val database = FakeStudyDatabasePort()
        database.assessmentItemSnapshots[
            knowledgeQuizItemSnapshotId(learnerId, sessionId, "kc-a"),
        ] = snapshotRow(sessionId = sessionId, nodeId = "kc-a", options = 4)
        val counting = CountingPredictor()
        val cached = AdaptiveSelectionAssembler(database, learnerId, counting)

        assertEquals(EloLearnerState.INITIAL, cached.currentEloState())
        assertEquals(1, counting.replayCalls)
        // 水位不变 → 命中缓存，不再折叠。
        assertEquals(EloLearnerState.INITIAL, cached.currentEloState())
        assertEquals(1, counting.replayCalls)

        database.projectionLedger = listOf(
            ledgerEvent(quizEvidence("knowledge-quiz:req-1:kc-a", "kc-a", sessionId, POSITIVE, 100_000L, 1L)),
        )
        val advanced = requireNotNull(cached.currentEloState())
        assertEquals(2, counting.replayCalls)
        assertEquals(1, advanced.answeredCountByNode.getValue("kc-a"))

        // 缓存命中与全程重折叠逐位一致（透明：缓存不影响任何输出）。
        val fresh = AdaptiveSelectionAssembler(database, learnerId, CountingPredictor())
        val refolded = requireNotNull(fresh.currentEloState())
        assertEquals(refolded, advanced)
        assertEquals(refolded.priorSkill.toRawBits(), advanced.priorSkill.toRawBits())
        assertEquals(
            refolded.currentSkillByNode.mapValues { it.value.toRawBits() },
            advanced.currentSkillByNode.mapValues { it.value.toRawBits() },
        )
    }

    // ---- 先修稳定度（KF-16 读侧接线） ----

    @Test
    fun `prerequisite stability joins the existing graph with the learner snapshot`() = runBlocking {
        val database = FakeStudyDatabasePort()
        database.knowledgeNodes += knowledgeNode("kc-a")
        database.knowledgeNodes += knowledgeNode("kc-pre")
        database.knowledgeNodeRelations += knowledgeRelation("rel-1", prerequisite = "kc-pre", dependent = "kc-a")
        database.knowledgeNodeRelations += knowledgeRelation("rel-2", prerequisite = "kc-unknown", dependent = "kc-a")
        val snapshot = LearnerSnapshot(
            learnerId = learnerId,
            knowledgeMasteryStates = mapOf("kc-pre" to knowledgeState("kc-pre", stabilityDays = 5.0)),
            checkpoint = ProjectionCheckpoint(1, LearningProjector.VERSION, 800),
            generatedAtEpochMillis = 800,
        )

        val stability = assembler(database).assemblePrerequisiteStability(setOf("kc-a", "kc-b"), snapshot)

        // kc-a 的先修按 id 序：kc-pre（记忆卡 5.0）、kc-unknown（无记忆卡 → null，消费方忽略）；
        // kc-b 无已知先修 → 无条目（"不知道前置 ≠ 没有前置"）。
        assertEquals(mapOf("kc-a" to listOf(5.0, null)), stability)
    }

    // ---- 夹具 ----

    private fun assembler(
        database: FakeStudyDatabasePort,
        predictor: AdaptiveCorrectnessPredictor = EloCorrectnessPredictor(),
    ) = AdaptiveSelectionAssembler(database, learnerId, predictor)

    private fun ledgerEvent(event: LearningLedgerEvent) =
        PersistedLearningLedgerEvent(event = event, canonicalFingerprint = "fingerprint:${event.ledgerEventId}")

    private fun quizEvidence(
        evidenceId: String,
        nodeId: String,
        sessionId: Long,
        direction: LearningEvidenceDirection,
        occurredAtEpochMillis: Long,
        eventSequence: Long,
    ) = ChatEvidenceSubmitted(
        evidenceId = evidenceId,
        conversationId = "knowledge-quiz-review:$sessionId",
        knowledgeNodeId = nodeId,
        direction = direction,
        weight = if (direction == LearningEvidenceDirection.POSITIVE) {
            ChatEvidenceSubmitted.POSITIVE_WEIGHT
        } else {
            ChatEvidenceSubmitted.NEGATIVE_WEIGHT
        },
        reasonMarkdown = "本地机械判分",
        confidence = 1.0,
        occurredAtEpochMillis = occurredAtEpochMillis,
        eventSequence = eventSequence,
    )

    private fun snapshotRow(
        sessionId: Long,
        nodeId: String,
        options: Int = 4,
        verificationStatus: String = StudyDbValue.VerificationStatus.UNKNOWN,
        answerRevealState: String = "HIDDEN",
        optionsSnapshot: String? = null,
    ): AssessmentItemSnapshotSeedRecord {
        val snapshotId = knowledgeQuizItemSnapshotId(learnerId, sessionId, nodeId)
        return AssessmentItemSnapshotSeedRecord(
            assessmentItemSnapshotId = snapshotId,
            itemRevision = 1,
            practiceUnitId = null,
            problemRevisionId = null,
            tutorContentSnapshotId = snapshotId,
            promptMarkdown = "考察 $nodeId 的题面",
            optionsSnapshot = optionsSnapshot ?: (0 until options).joinToString(",", "[", "]") { index ->
                """{"id":"${'A' + index}","markdown":"选项$index"}"""
            },
            answerSpecSnapshot =
                """{"schema":"knowledge-quiz-item.v1","itemId":"knowledge-quiz:item:$nodeId",""" +
                """"correctChoiceId":"A","knowledgeNodeIds":["$nodeId"]}""",
            verificationStatus = verificationStatus,
            assessmentEligibility = "SESSION_ONLY",
            scoringMode = "AUTO_VERIFIED",
            learnerSnapshotVersion = "knowledge-quiz-review-v1",
            projectionCheckpoint = 0L,
            hintLevelAtPresentation = 0,
            answerRevealState = answerRevealState,
            createdAtEpochMillis = 11_000L,
        )
    }

    private fun mistake(unitId: String, nodeIds: Set<String>) = MistakeRecord(
        entryId = "entry-$unitId",
        problemId = "problem-$unitId",
        problemRevisionId = "revision-$unitId",
        practiceUnitId = unitId,
        sourceKey = null,
        subject = "math",
        title = "错题 $unitId",
        problemMarkdown = "题干 $unitId",
        status = "ACTIVE",
        createdAtEpochMillis = 1_000L,
        nextReviewAtEpochMillis = null,
        retrievability = null,
        knowledgeNodeIds = nodeIds,
    )

    private fun knowledgeNode(nodeId: String) = KnowledgeNodeSeedRecord(
        knowledgeNodeId = nodeId,
        stableCode = nodeId,
        subject = "math",
        displayName = nodeId,
        parentKnowledgeNodeId = null,
        taxonomyVersion = "test-taxonomy",
        createdAtEpochMillis = 1_000L,
    )

    private fun knowledgeRelation(relationId: String, prerequisite: String, dependent: String) =
        KnowledgeNodeRelationRecord(
            relationId = relationId,
            subject = "math",
            prerequisiteKnowledgeNodeId = prerequisite,
            dependentKnowledgeNodeId = dependent,
            relationType = "PREREQUISITE_OF",
            sourceId = "test-source",
            sourceLocator = "test-locator",
            reviewedAtEpochMillis = 1_000L,
        )

    /**
     * 校准门可通过的学习快照（选择器 `hasCurrentCalibration` 要求当前 SUPPORTED）。
     * 没有这份校准，CLARIFY 会由"证据不新鲜"触发，而不是由"候选未核验"触发——
     * 本组用例要钉的是后者。
     */
    private fun calibratedSnapshot(ledgerHeadSequence: Long = 1L) = LearnerSnapshot(
        learnerId = learnerId,
        knowledgeMasteryStates = mapOf(
            "kc-a" to knowledgeState("kc-a", stabilityDays = 5.0).copy(
                independentCorrectObservations = listOf(
                    IndependentCorrectObservation(
                        itemFamilyId = "family-a",
                        studyDayEpochDay = 1,
                        occurredAtEpochMillis = 100,
                        evidenceWeight = 1.0,
                        calibration = CalibrationSnapshot(
                            CalibrationSupport.SUPPORTED,
                            "calibration-source",
                            "calibration-v1",
                            0,
                            100_000_000,
                        ),
                    ),
                ),
                calibrationSupport = CalibrationSupport.SUPPORTED,
            ),
        ),
        checkpoint = ProjectionCheckpoint(ledgerHeadSequence, LearningProjector.VERSION, 500),
        generatedAtEpochMillis = 500,
    )

    /**
     * 12 笔、每笔间隔 80 秒的作答前缀（1/2/4 答对，其余答错）。按既有 Elo 预测器重放：
     * 末笔技能 -0.5743，决策时刻 +80s 回升 1.0 → p=0.7036；n=12 → h=0.1387，
     * 区间 [0.56496, 0.84231] 落在 FOUNDATION 走廊 [0.55, 0.85] 内。
     * 用真实预测器（不是 stub 区间）证明升级后的 ASK 路径可达。
     */
    private fun corridorLedger(sessionId: Long): List<PersistedLearningLedgerEvent> {
        val answers = listOf(true, true, false, true) + List(8) { false }
        return answers.mapIndexed { index, correct ->
            ledgerEvent(
                quizEvidence(
                    evidenceId = "knowledge-quiz:req-${index + 1}:kc-a",
                    nodeId = "kc-a",
                    sessionId = sessionId,
                    direction = if (correct) POSITIVE else NEGATIVE,
                    occurredAtEpochMillis = 1_000L + index * 80_000L,
                    eventSequence = index + 1L,
                ),
            )
        }
    }

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

    private class RecordingClarificationConsumer : AdaptiveClarificationConsumer {
        val requests = mutableListOf<AdaptiveClarificationRequest>()

        override suspend fun onClarificationRequired(request: AdaptiveClarificationRequest) {
            requests += request
        }
    }

    private fun knowledgeState(nodeId: String, stabilityDays: Double) = KnowledgeMasteryState(
        knowledgeNodeId = nodeId,
        masteryScore = 0.6,
        conservativeMasteryScore = 0.4,
        evidenceMass = 1.0,
        successWeight = 1.0,
        failureWeight = 0.0,
        memoryStabilityDays = stabilityDays,
        memoryDifficulty = 6.0,
        lastAttemptAtEpochMillis = 200L,
        lastAttemptStudyDayEpochDay = 1L,
        status = MasteryStatus.LEARNING,
        calibrationSupport = CalibrationSupport.UNKNOWN,
        projectorVersion = LearningProjector.VERSION,
        checkpointSequence = 1L,
    )

    private class GappedDatabasePort(
        private val status: LearningLedgerReadStatus,
    ) : FakeStudyDatabasePort() {
        override suspend fun loadLearningLedger(learnerId: String): LearningLedgerRead =
            LearningLedgerRead(
                learnerId = learnerId,
                validPrefix = projectionLedger,
                status = status,
            )
    }

    /** 计折叠次数：证明水位缓存命中的时候确实没有再折叠。 */
    private class CountingPredictor(
        private val delegate: EloCorrectnessPredictor = EloCorrectnessPredictor(),
    ) : AdaptiveCorrectnessPredictor {
        var replayCalls: Int = 0
            private set

        override fun replay(events: List<EloAnswerRecord>): EloLearnerState {
            replayCalls += 1
            return delegate.replay(events)
        }

        override fun predictInterval(
            state: EloLearnerState,
            knowledgeNodeId: String,
            optionsCount: Int?,
            decisionAtEpochMillis: Long,
        ): PredictedCorrectnessInterval =
            delegate.predictInterval(state, knowledgeNodeId, optionsCount, decisionAtEpochMillis)
    }

    private companion object {
        val POSITIVE = LearningEvidenceDirection.POSITIVE
        val NEGATIVE = LearningEvidenceDirection.NEGATIVE

        /** 冷启动 + 4 选项：p=0.625，h=0.5 → [0.125, 1.0]。 */
        val FLAT_INTERVAL = PredictedCorrectnessInterval(
            lowerBound = 0.125,
            upperBound = 1.0,
            modelVersion = "predictor-elo-v1",
            generatedAtEpochMillis = 2_000L,
            validUntilEpochMillis = 86_402_000L,
        )

        /** 冷启动 + 猜测修正关闭：p=0.5，h=0.5 → [0.0, 1.0]。 */
        val FLAT_INTERVAL_ZERO_GUESS = PredictedCorrectnessInterval(
            lowerBound = 0.0,
            upperBound = 1.0,
            modelVersion = "predictor-elo-v1",
            generatedAtEpochMillis = 2_000L,
            validUntilEpochMillis = 86_402_000L,
        )

        /** 冷启动 + 2 选项：p=0.75，h=0.5 → [0.25, 1.0]。 */
        val FLAT_INTERVAL_TWO_OPTIONS = PredictedCorrectnessInterval(
            lowerBound = 0.25,
            upperBound = 1.0,
            modelVersion = "predictor-elo-v1",
            generatedAtEpochMillis = 2_000L,
            validUntilEpochMillis = 86_402_000L,
        )
    }
}
