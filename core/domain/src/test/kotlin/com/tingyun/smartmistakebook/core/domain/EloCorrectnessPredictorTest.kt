package com.tingyun.smartmistakebook.core.domain

import com.tingyun.smartmistakebook.core.model.CalibrationSnapshot
import com.tingyun.smartmistakebook.core.model.CalibrationSupport
import com.tingyun.smartmistakebook.core.model.IndependentCorrectObservation
import com.tingyun.smartmistakebook.core.model.KnowledgeMasteryState
import com.tingyun.smartmistakebook.core.model.LearnerSnapshot
import com.tingyun.smartmistakebook.core.model.MasteryStatus
import com.tingyun.smartmistakebook.core.model.ProjectionCheckpoint
import kotlin.math.ln
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * G5-A Elo 预测器（批次 21 T2）：重放确定性、R4 公式金样、降级矩阵、走廊边界与
 * O(n) 量化。
 *
 * 金样值的来源（如实）：按 R4 `docs/research/2026-10-25-elo-predictor-spec.md` §1.4/§1.5
 * 的公式在 JVM（本机 Temurin 21 `jshell`，`java.lang.Math.exp`）上算出后硬编码；断言用
 * 1e-9 容差（同一公式族跨引擎的 exp 末位差 ≤1 ulp 远小于此），**精确相等只用于与 exp
 * 无关的闭式值**（如 0.125/1.0）和同引擎重现的逐位断言。
 *
 * 消灭的失败：预测器没有可复现的数值口径时，走廊判定与"同台账前缀逐位不变"都无从证明。
 */
class EloCorrectnessPredictorTest {

    private val predictor = EloCorrectnessPredictor()
    private val selector = AdaptiveQuestionSelector()

    // ---- R4 §1.4/§1.5 公式金样（首答先验 / 重答 seconds_ago / α(n) 首答门 / pfae 非对称 / guess） ----

    @Test
    fun `proso fold formulas match hand-computed goldens`() {
        val first = EloAnswerRecord("knowledge-quiz:r1:kc-a", "kc-a", true, 4, 1_000_000L, 1L)
        val second = EloAnswerRecord("knowledge-quiz:r2:kc-a", "kc-a", false, 4, 1_080_000L, 2L)
        val third = EloAnswerRecord("knowledge-quiz:r3:kc-b", "kc-b", true, null, 2_000_000L, 3L)

        // 首答先验分支：skill = prior(0) − difficulty(0) = 0 → p = 0.25 + 0.75·0.5 = 0.625。
        // 答对局部技能 + pfae_good·(1−p) = 3.4·0.375 = 1.275；
        // 首答门 α(0) = 0.8 → prior += 0.8·0.375，difficulty −= 0.8·0.375。
        // （首答前的点预测用 INITIAL：作答后同一时刻再预测会走重答分支的秒距下限。）
        assertEquals(0.625, predictor.predictCorrectness(EloLearnerState.INITIAL, "kc-a", 4, 1_000_000L), 1e-12)
        val afterFirst = predictor.replay(listOf(first))
        assertEquals(1.275, afterFirst.currentSkillByNode.getValue("kc-a"), 1e-12)
        assertEquals(0.30000000000000004, afterFirst.priorSkill, 1e-12)
        assertEquals(-0.30000000000000004, afterFirst.difficultyByNode.getValue("kc-a"), 1e-12)
        assertEquals(1, afterFirst.firstAnswerCountByUser)
        assertEquals(1, afterFirst.firstAnswerCountByNode.getValue("kc-a"))
        assertEquals(1, afterFirst.answeredCountByNode.getValue("kc-a"))

        // 重答分支：seconds_ago = 80s → skill = 1.275 + 80/80 = 2.275，
        // p = 0.25 + 0.75·sigmoid(2.275)。
        assertEquals(0.9300889576886322, predictor.predictCorrectness(afterFirst, "kc-a", 4, 1_080_000L), 1e-9)

        // 答错非对称步长 pfae_bad = 0.3：局部技能 = 1.275 + 0.3·(0 − p2)；
        // 非首答 → 全局 prior/difficulty 不再更新。
        val afterSecond = predictor.replay(listOf(first, second))
        assertEquals(0.9959733126934103, afterSecond.currentSkillByNode.getValue("kc-a"), 1e-9)
        assertEquals(0.30000000000000004, afterSecond.priorSkill, 1e-12)
        assertEquals(1, afterSecond.firstAnswerCountByUser)
        assertEquals(2, afterSecond.answeredCountByNode.getValue("kc-a"))

        // guess=0 降级（选项数缺失）：新节点先验分支 skill = prior − 0 = 0.3 → p = sigmoid(0.3)。
        assertEquals(0.574442516811659, predictor.predictCorrectness(afterSecond, "kc-b", null, 2_000_000L), 1e-9)

        // user 侧 α 在第二次首答时衰减为 0.8/(1+0.05·1) = 0.8/1.05（item 侧计数单学习者恒 0）。
        val afterThird = predictor.replay(listOf(first, second, third))
        // 首答局部技能基线 = prior − difficulty（proso update_phase：current_skill is None
        // → current_skill = prior_skill − difficulty，再叠 pfae 步长）——kc-b 此时 prior 已非 0。
        assertEquals(1.7468954428403594, afterThird.currentSkillByNode.getValue("kc-b"), 1e-9)
        assertEquals(0.6242342729054027, afterThird.priorSkill, 1e-9)
        assertEquals(-0.3404459865506728, afterThird.difficultyByNode.getValue("kc-b"), 1e-9)
        assertEquals(2, afterThird.firstAnswerCountByUser)
        assertEquals(2, afterThird.answeredCountByNode.getValue("kc-a"))
    }

    @Test
    fun `first answer alpha decays with the user first-answer count only`() {
        // 三个不同节点各一次正确首答（4 选项）：α_user 依次 0.8、0.8/1.05、0.8/1.10；
        // α_item 每次都按该节点首答计数（单学习者恒 0）→ 恒 0.8。
        val events = listOf(
            EloAnswerRecord("knowledge-quiz:c1:kc-a", "kc-a", true, 4, 1_000, 1L),
            EloAnswerRecord("knowledge-quiz:c2:kc-b", "kc-b", true, 4, 2_000, 2L),
            EloAnswerRecord("knowledge-quiz:c3:kc-c", "kc-c", true, 4, 3_000, 3L),
        )
        val afterSecond = predictor.replay(events.take(2))
        // 第三个节点的首答预测：前两笔 4 选项首答后 prior = 0.3 + α_user(1)·(1−p₂) = 0.5431757046790521，
        // skill = prior − 0（该节点无历史）。
        assertEquals(
            0.7244131433893694,
            predictor.predictCorrectness(afterSecond, "kc-c", 4, 3_000L),
            1e-9,
        )
        val state = predictor.replay(events)
        // 首答基线 = prior（此刻 0.5431757046790521）− 该节点难度（缺键 0），再叠 3.4·(1−p)。
        assertEquals(1.4801710171551963, state.currentSkillByNode.getValue("kc-c"), 1e-9)
        assertEquals(0.7436025094867835, state.priorSkill, 1e-9)
        assertEquals(-0.22046948528850452, state.difficultyByNode.getValue("kc-c"), 1e-9)
        assertEquals(3, state.firstAnswerCountByUser)
        assertEquals(mapOf("kc-a" to 1, "kc-b" to 1, "kc-c" to 1), state.firstAnswerCountByNode)
    }

    @Test
    fun `guess correction anchors predictions at the chance level`() {
        // 4 选项 → 下锚 0.25：冷启动 p = 0.25 + 0.75·0.5 = 0.625；2 选项 → 0.75；
        // 选项数缺失/为 0 → 关闭修正（guess=0 → 0.5）。
        assertEquals(0.625, predictor.predictCorrectness(EloLearnerState.INITIAL, "kc-a", 4, 1_000), 1e-12)
        assertEquals(0.75, predictor.predictCorrectness(EloLearnerState.INITIAL, "kc-a", 2, 1_000), 1e-12)
        assertEquals(0.5, predictor.predictCorrectness(EloLearnerState.INITIAL, "kc-a", null, 1_000), 1e-12)
        assertEquals(0.5, predictor.predictCorrectness(EloLearnerState.INITIAL, "kc-a", 0, 1_000), 1e-12)
    }

    @Test
    fun `re-answer time boost follows time shift over seconds ago`() {
        val state = EloLearnerState.INITIAL.copy(
            currentSkillByNode = mapOf("kc-a" to 0.5),
            lastAnswerAtByNode = mapOf("kc-a" to 1_000_000L),
        )
        // 80 秒前答过：skill = 0.5 + 80/80 = 1.5 → p = 0.25 + 0.75·sigmoid(1.5)。
        assertEquals(0.8631808571452327, predictor.predictCorrectness(state, "kc-a", 4, 1_080_000L), 1e-9)
        // 秒距下限 max(seconds_ago, 0.001)（proso :199）：同一时刻 → 回升项 80000 → p → 1.0。
        assertEquals(1.0, predictor.predictCorrectness(state, "kc-a", 4, 1_000_000L), 1e-12)
    }

    @Test
    fun `invalid or inverted timestamps degrade to the prior branch`() {
        // R4 §2.2.4/§4.5：上次时间缺失、≤0、或时间倒挂 → 先验分支（不猜插值）。
        // 先验分支 skill = 0.2 − (−0.1) = 0.3 → p = 0.25 + 0.75·sigmoid(0.3)。
        val base = EloLearnerState.INITIAL.copy(
            priorSkill = 0.2,
            difficultyByNode = mapOf("kc-a" to -0.1),
            currentSkillByNode = mapOf("kc-a" to 0.5),
        )
        assertEquals(
            0.6808318876087442,
            predictor.predictCorrectness(base.copy(lastAnswerAtByNode = mapOf("kc-a" to 0L)), "kc-a", 4, 9_000L),
            1e-9,
        )
        assertEquals(
            0.6808318876087442,
            predictor.predictCorrectness(
                base.copy(lastAnswerAtByNode = mapOf("kc-a" to 2_000_000L)),
                "kc-a",
                4,
                1_000_000L,
            ),
            1e-9,
        )
    }

    @Test
    fun `a zero timestamp mid-stream replays through the prior branch and poisons the next last-time`() {
        val first = EloAnswerRecord("knowledge-quiz:z1:kc-a", "kc-a", true, 4, 1_000L, 1L)
        val zeroTime = EloAnswerRecord("knowledge-quiz:z2:kc-a", "kc-a", false, 4, 0L, 2L)
        val state = predictor.replay(listOf(first, zeroTime))
        // 第二笔时间戳 ≤0 → 预测走先验分支（skill = prior − difficulty = 0.6…）：
        // 局部技能 = 1.275 + 0.3·(0 − p)，p = 0.25 + 0.75·sigmoid(0.6000000000000001)。
        assertEquals(1.054727331099196, state.currentSkillByNode.getValue("kc-a"), 1e-9)
        // 非首答（局部技能已存在）→ 不触发 α 首答门。
        assertEquals(0.30000000000000004, state.priorSkill, 1e-12)
        assertEquals(0L, state.lastAnswerAtByNode.getValue("kc-a"))
        assertEquals(2, state.answeredCountByNode.getValue("kc-a"))
    }

    // ---- 纯函数性 / 确定性 ----

    @Test
    fun `replay is a pure deterministic function of the prefix`() {
        val events = listOf(
            EloAnswerRecord("knowledge-quiz:d1:kc-a", "kc-a", true, 4, 1_000L, 1L),
            EloAnswerRecord("knowledge-quiz:d2:kc-b", "kc-b", false, 2, 2_000L, 2L),
            EloAnswerRecord("knowledge-quiz:d3:kc-a", "kc-a", true, 4, 3_000L, 3L),
        )
        val first = predictor.replay(events)
        val second = predictor.replay(events)
        assertEquals(first, second)
        // 逐位：核心标量与映射值都以位模式相等（同引擎 + 同前缀）。
        assertEquals(first.priorSkill.toRawBits(), second.priorSkill.toRawBits())
        assertEquals(
            first.currentSkillByNode.mapValues { it.value.toRawBits() },
            second.currentSkillByNode.mapValues { it.value.toRawBits() },
        )
        // 前缀性：同一前缀两次折叠必须给出同一状态（缓存/重折叠透明的前提）。
        assertEquals(predictor.replay(events.take(2)), predictor.replay(events.take(2)))
        assertEquals(EloLearnerState.INITIAL, predictor.replay(emptyList()))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `replay refuses out-of-order records instead of guessing an order`() {
        predictor.replay(
            listOf(
                EloAnswerRecord("knowledge-quiz:o2:kc-a", "kc-a", true, 4, 1_000L, 2L),
                EloAnswerRecord("knowledge-quiz:o1:kc-a", "kc-a", true, 4, 2_000L, 1L),
            ),
        )
    }

    // ---- 区间与走廊边界（R4 §4.4；任务项五的 0.55/0.85） ----

    @Test
    fun `cold start interval is full width and can never pass a corridor`() {
        val interval = predictor.predictInterval(EloLearnerState.INITIAL, "kc-a", 4, 800)
        // p = 0.625，h(0) = 0.5 → [0.125, 1.0]（1.125 夹到 1.0）：宽 0.875，任何走廊必拒。
        assertEquals(0.125, interval.lowerBound, 1e-12)
        assertEquals(1.0, interval.upperBound, 1e-12)
        assertEquals(LearningCoreVersions.PREDICTOR_ELO, interval.modelVersion)
        assertEquals(800L + 86_400_000L, interval.validUntilEpochMillis)

        val decision = selector.select(
            request(
                candidates = listOf(candidate(interval)),
                decisionAtEpochMillis = 800,
            ),
        )
        assertEquals(AdaptiveDecisionKind.NO_SAFE_CANDIDATE, decision.kind)
        assertTrue("PREDICTION_UNKNOWN_STALE_OR_OUTSIDE_CORRIDOR" in decision.reasonCodes)
    }

    @Test
    fun `foundation corridor accepts exactly the closed interval fifty-five to eighty-five`() {
        // 走廊判据（AdaptiveQuestionSelector :244-252）是闭区间：下界 ≥ 0.55 ∧ 上界 ≤ 0.85。
        assertEquals(AdaptiveDecisionKind.ASK, decisionFor(listOf(candidate(prediction(0.55, 0.85)))).kind)
        assertEquals(
            AdaptiveDecisionKind.NO_SAFE_CANDIDATE,
            decisionFor(listOf(candidate(prediction(0.549999, 0.85)))).kind,
        )
        assertEquals(
            AdaptiveDecisionKind.NO_SAFE_CANDIDATE,
            decisionFor(listOf(candidate(prediction(0.55, 0.850001)))).kind,
        )
        // 迁移类走廊 0.35–0.75 同样闭区间。
        assertEquals(
            AdaptiveDecisionKind.ASK,
            decisionFor(
                listOf(
                    candidate(prediction(0.35, 0.75), targetKind = AdaptiveTargetKind.TRANSFER),
                ),
                requestedKind = AdaptiveTargetKind.TRANSFER,
            ).kind,
        )
    }

    @Test
    fun `corridor opens only after the interval narrows past the boundary`() {
        // p ≈ 0.7（skill = ln 1.5 − 1.0，重答回升 1.0 → ln 1.5）：n=10 时 h = 0.5/√11 ≈ 0.15076，
        // 下界 0.549244 < 0.55 → 拒绝；n=11 时 h = 0.5/√12 ≈ 0.14434，[0.555662, 0.844338]
        // ⊆ [0.55, 0.85] → 通过。这是"约 n≥11 打开 FOUNDATION 走廊"（R4 §4.4）的量化钉。
        fun stateWith(answeredCount: Int) = EloLearnerState.INITIAL.copy(
            priorSkill = 0.2,
            currentSkillByNode = mapOf("kc-a" to (ln(1.5) - 1.0)),
            lastAnswerAtByNode = mapOf("kc-a" to 1_000_000L),
            answeredCountByNode = mapOf("kc-a" to answeredCount),
            firstAnswerCountByUser = answeredCount,
            firstAnswerCountByNode = mapOf("kc-a" to 1),
        )

        val rejected = predictor.predictInterval(stateWith(10), "kc-a", 4, 1_080_000L)
        assertEquals(0.549244, rejected.lowerBound, 1e-9)
        assertEquals(0.850756, rejected.upperBound, 1e-9)
        assertEquals(
            AdaptiveDecisionKind.NO_SAFE_CANDIDATE,
            decisionFor(listOf(candidate(rejected)), decisionAt = 1_080_000L).kind,
        )

        val admitted = predictor.predictInterval(stateWith(11), "kc-a", 4, 1_080_000L)
        assertEquals(0.555662, admitted.lowerBound, 1e-9)
        assertEquals(0.844338, admitted.upperBound, 1e-9)
        assertEquals(
            AdaptiveDecisionKind.ASK,
            decisionFor(listOf(candidate(admitted)), decisionAt = 1_080_000L).kind,
        )
    }

    @Test
    fun `half width clamps to the lower floor for well answered nodes`() {
        val state = EloLearnerState.INITIAL.copy(
            currentSkillByNode = mapOf("kc-a" to 0.5),
            lastAnswerAtByNode = mapOf("kc-a" to 1_000_000L),
            answeredCountByNode = mapOf("kc-a" to 500),
            firstAnswerCountByUser = 500,
            firstAnswerCountByNode = mapOf("kc-a" to 1),
        )
        // 0.5/√501 ≈ 0.02234 → 夹到下限 0.05；p = 0.8631808571452327 → [0.813181, 0.913181]。
        val interval = predictor.predictInterval(state, "kc-a", 4, 1_080_000L)
        assertEquals(0.813181, interval.lowerBound, 1e-9)
        assertEquals(0.913181, interval.upperBound, 1e-9)
    }

    // ---- 超参：PENDING_CALIBRATION 的防误用钉 ----

    @Test
    fun `default hyperparameters are the uncalibrated proso production snapshot`() {
        val defaults = EloHyperparameters.PROSO_PRODUCTION_DEFAULTS
        // 每个值都是 proso 源码字面量（prediction.py:166 + 生产配置零覆盖，MIT，实拉核验）。
        // 借用值 ≠ 本项目校准值（Wave X 线）：校准要改任何一个，必须有意改这里并 bump
        // LearningCoreVersions.PREDICTOR_ELO。
        assertEquals(0.8, defaults.eloAlpha, 0.0)
        assertEquals(0.05, defaults.eloDynamicAlpha, 0.0)
        assertEquals(3.4, defaults.pfaeGood, 0.0)
        assertEquals(0.3, defaults.pfaeBad, 0.0)
        assertEquals(80.0, defaults.timeShiftSeconds, 0.0)
        assertEquals(0.5, defaults.intervalHalfWidthInitial, 0.0)
        assertEquals(86_400_000L, defaults.predictionValidityMillis)
        // 版本身份：v1 即"未校准"标记（区间携带同一串）。
        assertEquals("predictor-elo-v1", LearningCoreVersions.PREDICTOR_ELO)
        assertEquals(LearningCoreVersions.PREDICTOR_ELO, predictor.modelVersion)
        assertEquals(
            LearningCoreVersions.PREDICTOR_ELO,
            predictor.predictInterval(EloLearnerState.INITIAL, "kc-a", 4, 1_000).modelVersion,
        )
    }

    // ---- 复杂度：O(E) 单遍折叠的量化证据（R4 §3.2；任务项五） ----

    @Test
    fun `replay scales linearly with the event count`() {
        fun syntheticEvents(count: Int): List<EloAnswerRecord> = List(count) { index ->
            EloAnswerRecord(
                evidenceId = "knowledge-quiz:bench:$index",
                knowledgeNodeId = "kc-${index % 500}",
                isCorrect = index % 3 != 0,
                optionsCount = 4,
                occurredAtEpochMillis = 1_000_000L + index * 1_000L,
                eventSequence = index + 1L,
            )
        }

        fun minReplayNanos(events: List<EloAnswerRecord>): Long = (1..3).minOf {
            val started = System.nanoTime()
            predictor.replay(events)
            System.nanoTime() - started
        }

        // JIT 预热（两次同规模），再把"10 倍事件量"的时间比钉在远低于二次方的位置。
        repeat(2) { predictor.replay(syntheticEvents(2_000)) }
        val smallEvents = syntheticEvents(2_000)
        val largeEvents = syntheticEvents(20_000)
        val smallNanos = minReplayNanos(smallEvents)
        val largeNanos = minReplayNanos(largeEvents)
        println(
            "Elo replay O(n) probe: 2k=${smallNanos / 1_000}us 20k=${largeNanos / 1_000}us " +
                "ratio=${"%.1f".format(largeNanos.toDouble() / smallNanos)}",
        )
        // O(n) 判据：10× 事件量对应 ~10× 时间；二次方会是 ~100×。20× 留足噪音余量。
        assertTrue(
            "Elo replay must not scale quadratically (2k=${smallNanos}ns, 20k=${largeNanos}ns)",
            largeNanos < smallNanos * 20,
        )
        // 量化证据的一部分：大规模折叠的结果自洽（每个节点都记满）。
        val state = predictor.replay(largeEvents)
        assertEquals(20_000, state.answeredCountByNode.values.sum())
        assertEquals(500, state.answeredCountByNode.size)
    }

    // ---- 夹具（与 AdaptiveQuestionSelectorTest 的夹具同源，本文件只取走廊路径所需的最小面） ----

    private fun candidate(
        interval: PredictedCorrectnessInterval,
        assessmentItemId: String = "assessment-elo",
        targetKind: AdaptiveTargetKind = AdaptiveTargetKind.FOUNDATION,
    ) = AdaptiveQuestionCandidate(
        assessmentItemId = assessmentItemId,
        knowledgeNodeIds = setOf("kc-a"),
        itemFamilyId = "elo-family-$assessmentItemId",
        targetKind = targetKind,
        contentVerified = true,
        answerWouldBeRevealed = false,
        informationValue = 0.8,
        predictedCorrectness = interval,
    )

    private fun prediction(lower: Double, upper: Double) = PredictedCorrectnessInterval(
        lowerBound = lower,
        upperBound = upper,
        modelVersion = LearningCoreVersions.PREDICTOR_ELO,
        generatedAtEpochMillis = 0,
        validUntilEpochMillis = 100_000_000,
    )

    private fun decisionFor(
        candidates: List<AdaptiveQuestionCandidate>,
        requestedKind: AdaptiveTargetKind = AdaptiveTargetKind.FOUNDATION,
        decisionAt: Long = 800,
    ): AdaptiveDecision = selector.select(
        request(
            candidates = candidates,
            requestedKind = requestedKind,
            decisionAtEpochMillis = decisionAt,
        ),
    )

    private fun request(
        candidates: List<AdaptiveQuestionCandidate>,
        requestedKind: AdaptiveTargetKind = AdaptiveTargetKind.FOUNDATION,
        decisionAtEpochMillis: Long,
    ) = AdaptiveSelectionRequest(
        learnerSnapshot = LearnerSnapshot(
            learnerId = "learner-1",
            knowledgeMasteryStates = mapOf("kc-a" to learningState()),
            checkpoint = ProjectionCheckpoint(8, LearningProjector.VERSION, 800),
            generatedAtEpochMillis = 800,
        ),
        targetKnowledgeNodeIds = setOf("kc-a"),
        requestedTargetKind = requestedKind,
        userRequestedQuestion = true,
        candidates = candidates,
        decisionAtEpochMillis = decisionAtEpochMillis,
    )

    /** 学习态 + 当前校准支持：请求可通过校准门，走廊判据是唯一裁决点。 */
    private fun learningState() = KnowledgeMasteryState(
        knowledgeNodeId = "kc-a",
        masteryScore = 0.6,
        conservativeMasteryScore = 0.4,
        evidenceMass = 2.0,
        successWeight = 2.0,
        failureWeight = 1.0,
        memoryStabilityDays = 5.0,
        memoryDifficulty = 6.0,
        lastAttemptAtEpochMillis = 200,
        lastAttemptStudyDayEpochDay = 2,
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
        status = MasteryStatus.LEARNING,
        calibrationSupport = CalibrationSupport.SUPPORTED,
        projectorVersion = LearningProjector.VERSION,
        checkpointSequence = 8,
        lastEvidenceAtEpochMillis = 200,
    )
}
