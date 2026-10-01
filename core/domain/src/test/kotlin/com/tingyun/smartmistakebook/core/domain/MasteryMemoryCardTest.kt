package com.tingyun.smartmistakebook.core.domain

import com.tingyun.smartmistakebook.core.model.AnswerRevealOutcome
import com.tingyun.smartmistakebook.core.model.AssessmentEvidenceSnapshot
import com.tingyun.smartmistakebook.core.model.AssessmentSnapshotVerification
import com.tingyun.smartmistakebook.core.model.Attempt
import com.tingyun.smartmistakebook.core.model.CalibrationSnapshot
import com.tingyun.smartmistakebook.core.model.CalibrationSupport
import com.tingyun.smartmistakebook.core.model.ChatEvidenceSubmitted
import com.tingyun.smartmistakebook.core.model.EvidenceAttributionCertainty
import com.tingyun.smartmistakebook.core.model.EvidenceAttributionRole
import com.tingyun.smartmistakebook.core.model.KnowledgeEvidenceAttribution
import com.tingyun.smartmistakebook.core.model.LearnerSnapshot
import com.tingyun.smartmistakebook.core.model.LearningEvidence
import com.tingyun.smartmistakebook.core.model.LearningEvidenceDirection
import com.tingyun.smartmistakebook.core.model.LearningEvidenceReason
import com.tingyun.smartmistakebook.core.model.MasteryStatus
import com.tingyun.smartmistakebook.core.model.ProblemMemoryOutcome
import com.tingyun.smartmistakebook.core.model.StudyDayContext
import com.tingyun.smartmistakebook.core.model.StudyDayMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * W3-1/W3-2（roadmap W3-1；台账「裁决 13 · 修订」，2026-09-30）的**知识点记忆卡**行为锁定：
 * 卡与逐题共用 `FsrsMemoryUpdateModel.nextMemoryState`，吃该知识点的**作答流**（本文件里即
 * Attempt 通道）；判据 = 稳定度 ≥ 21 天（Anki mature 口径）∧ 当前召回概率 ≥ 0.9。
 *
 * 期望值由公式转写脚本算出（与研究文档 `docs/research/2026-09-30-mastery-criterion-evidence.md`
 * §4 同族脚本逐字转写 `nextMemoryState` 三分支 + `nextDifficulty`；容差 1e-4，
 * 数值差异超出该容差即说明分支/口径漂移）。
 */
class MasteryMemoryCardTest {

    private val projector = LearningProjector()

    @Test
    fun `a single binding card follows the fsrs trajectory and masters on day 26`() {
        var snapshot = LearnerSnapshot.empty("learner-1")
        val plan = listOf(
            0L to wrongEvidence(), // 初次错题：卡从 AGAIN 的初始稳定度起步
            1L to easyEvidence(),
            3L to easyEvidence(),
            9L to easyEvidence(),
            26L to easyEvidence(),
        )
        val expectedStability = listOf(0.212, 1.886788, 6.269161, 17.379189, 43.842078)

        plan.forEachIndexed { index, (day, evidence) ->
            snapshot = projector.project(
                snapshot,
                listOf(
                    attempt(
                        id = "a-${index + 1}",
                        sequence = (index + 1).toLong(),
                        evidence = evidence,
                        occurredAt = BASE_AT_MILLIS + day * DAY_MILLIS,
                    ),
                ),
                (index + 1).toLong(),
            ).snapshot
            val card = snapshot.knowledgeMasteryStates.getValue("kc-a")

            assertEquals(
                "第 ${index + 1} 次作答后的卡稳定度",
                expectedStability[index],
                card.memoryStabilityDays!!,
                1e-4,
            )
            // 单题单绑定：卡与逐题 FSRS 数值等价（同一函数、同一时间基）。
            val problem = snapshot.problemMemoryStates.getValue("unit-1")
            assertEquals(problem.stabilityDays, card.memoryStabilityDays!!, 1e-9)
            // 判据（作答瞬间 R = 1）：第 5 次（R ≥ 0.9 且 S = 43.84 ≥ 21）才掌握。
            assertEquals(
                "第 ${index + 1} 次作答后的掌握状态",
                index == 4,
                card.status == MasteryStatus.MASTERED,
            )
        }
    }

    @Test
    fun `two same day mistake bindings slow the card and it masters on day 70`() {
        // 同日两道错题：卡连吃两次遗忘（第二次走同日分支）→ 起步更低、难度更高（D 被抬到 ~8.8），
        // 第 11 次作答（第 70 天）才越 21 天耐久门。期望值见类注释脚本。
        var snapshot = LearnerSnapshot.empty("learner-1")
        val days = listOf(0L, 0L, 1L, 1L, 3L, 3L, 9L, 9L, 26L, 26L, 70L, 70L)
        val expectedStability = listOf(
            0.212, 0.083357, 0.613945, 0.666128, 2.353331, 2.353331,
            6.829314, 6.829314, 17.691316, 17.691316, 41.975969, 41.975969,
        )

        days.forEachIndexed { index, day ->
            val unit = if (index % 2 == 0) "unit-1" else "unit-2"
            val sequence = (index + 1).toLong()
            snapshot = projector.project(
                snapshot,
                listOf(
                    attempt(
                        id = "$unit-$sequence",
                        sequence = sequence,
                        evidence = if (day == 0L) wrongEvidence() else easyEvidence(),
                        occurredAt = BASE_AT_MILLIS + day * DAY_MILLIS,
                        unit = unit,
                        family = "family-$unit",
                    ),
                ),
                sequence,
            ).snapshot
            val card = snapshot.knowledgeMasteryStates.getValue("kc-a")

            assertEquals(
                "第 $sequence 次作答后的卡稳定度",
                expectedStability[index],
                card.memoryStabilityDays!!,
                1e-4,
            )
            assertEquals(
                "第 $sequence 次作答后的掌握状态",
                index >= 10,
                card.status == MasteryStatus.MASTERED,
            )
        }
    }

    @Test
    fun `the beta binomial point estimate and wilson bound follow the kf-10 table`() {
        var snapshot = LearnerSnapshot.empty("learner-1")
        repeat(8) { index ->
            snapshot = projector.project(
                snapshot,
                listOf(
                    attempt(
                        id = "a-${index + 1}",
                        sequence = (index + 1).toLong(),
                        evidence = easyEvidence(),
                        occurredAt = BASE_AT_MILLIS + index * DAY_MILLIS,
                    ),
                ),
                (index + 1).toLong(),
            ).snapshot
        }
        val eightCorrect = snapshot.knowledgeMasteryStates.getValue("kc-a")
        assertEquals(8.0, eightCorrect.successWeight, 1e-9)
        assertEquals(0.0, eightCorrect.failureWeight, 1e-9)
        // p̂=(8+0.5)/(9)=0.944444；Wilson 下界 0.605809（KF-10 公式，脚本手算）。
        assertEquals(0.944444, eightCorrect.masteryScore, 1e-6)
        assertEquals(0.605809, eightCorrect.conservativeMasteryScore, 1e-6)

        val oneAndOne = projector.project(
            snapshot,
            listOf(
                attempt(
                    id = "a-9",
                    sequence = 9,
                    evidence = wrongEvidence(),
                    occurredAt = BASE_AT_MILLIS + 9 * DAY_MILLIS,
                ),
            ),
            9,
        ).snapshot.knowledgeMasteryStates.getValue("kc-a")
        // s=8, f=1 → p̂=8.5/10=0.85；显示层的保守下界随之下降（与旧 EMA 的差异方向用例）。
        assertEquals(0.85, oneAndOne.masteryScore, 1e-6)
        assertTrue(
            "一次失败即拉低下界（旧固定增益 EMA 无此性质）",
            oneAndOne.conservativeMasteryScore < eightCorrect.conservativeMasteryScore,
        )
    }

    @Test
    fun `the memory criterion enforces the durability bar and current retention`() {
        val decay = -FsrsScheduleMath.DEFAULT_PARAMETERS[20]
        fun days(count: Long): Long = count * DAY_MILLIS

        // 稳定度低于 21 天：恒不达标。
        assertFalse(ClearlyMasteredForSkipPolicy.meetsMemoryCriterion(20.9, 0L, 0L, decay))
        // 稳定度 21 天：作答瞬间达标；恰好 21 天后 R = 0.9（边界，容差内通过）；第 22 天跌破。
        assertTrue(ClearlyMasteredForSkipPolicy.meetsMemoryCriterion(21.0, 0L, 0L, decay))
        assertTrue(ClearlyMasteredForSkipPolicy.meetsMemoryCriterion(21.0, 0L, days(21), decay))
        assertFalse(ClearlyMasteredForSkipPolicy.meetsMemoryCriterion(21.0, 0L, days(22), decay))
        // 稳定度 44 天：44 天内 R ≥ 0.9，第 45 天跌破。
        assertTrue(ClearlyMasteredForSkipPolicy.meetsMemoryCriterion(44.0, 0L, days(44), decay))
        assertFalse(ClearlyMasteredForSkipPolicy.meetsMemoryCriterion(44.0, 0L, days(45), decay))
        // 从未作答（无卡）：不达标。
        assertFalse(ClearlyMasteredForSkipPolicy.meetsMemoryCriterion(null, null, days(1), decay))
        assertFalse(ClearlyMasteredForSkipPolicy.meetsMemoryCriterion(30.0, null, days(1), decay))
    }

    @Test
    fun `reveals lapse the problem card while the knowledge card follows the post reveal answer`() {
        // 架构边界（2026-09-30 核实）：独立"看答案"事件（`AnswerRevealOutcome`）只走题卡
        // （weight=0、ANSWER_REVEALED，裁决 1 的落点）；KC 层唯一能吃到的"看答案"是**看答案
        // 之后的作答**（INCORRECT_AFTER_REVEAL → AGAIN）。本用例锁定两半的分工。
        var snapshot = LearnerSnapshot.empty("learner-1")
        listOf(0L to 1, 1L to 2).forEach { (day, sequence) ->
            snapshot = projector.project(
                snapshot,
                listOf(
                    attempt(
                        id = "a-$sequence",
                        sequence = sequence.toLong(),
                        evidence = easyEvidence(),
                        occurredAt = BASE_AT_MILLIS + day * DAY_MILLIS,
                    ),
                ),
                sequence.toLong(),
            ).snapshot
        }
        val beforeKc = snapshot.knowledgeMasteryStates.getValue("kc-a")
        val beforeProblem = snapshot.problemMemoryStates.getValue("unit-1")
        // 两次独立正确（GOOD 首条 2.3065 → 第 1 天跨日更新 7.315301，脚本手算）。
        assertEquals(7.315301, beforeKc.memoryStabilityDays!!, 1e-4)

        // 独立看答案：题卡 lapse（裁决 1），KC 卡不变（reveal 事件不带 KC 归属路径）。
        val revealAt = BASE_AT_MILLIS + 2 * DAY_MILLIS
        snapshot = projector.project(
            snapshot,
            listOf(
                AnswerRevealOutcome(
                    outcomeId = "reveal-1",
                    presentationId = "presentation-reveal-1",
                    assessmentSnapshot = assessmentSnapshot("reveal-1"),
                    occurredAtEpochMillis = revealAt,
                    studyDay = StudyDayContext(
                        epochDay = StudyDayMath.localEpochDayOf(revealAt, utcOffsetMinutes = 0),
                        timeZoneId = "UTC",
                        utcOffsetMinutes = 0,
                    ),
                    eventSequence = 3,
                ),
            ),
            3,
        ).snapshot
        val afterRevealKc = snapshot.knowledgeMasteryStates.getValue("kc-a")
        val afterRevealProblem = snapshot.problemMemoryStates.getValue("unit-1")
        assertEquals(
            "KC 卡不因独立看答案而变（架构边界；看答案后的作答才是 KC 证据）",
            beforeKc.memoryStabilityDays!!,
            afterRevealKc.memoryStabilityDays!!,
            0.0,
        )
        assertTrue(
            "题卡必须 lapse（裁决 1：看答案计一次遗忘失败）",
            afterRevealProblem.stabilityDays < beforeProblem.stabilityDays,
        )

        // 看答案之后的作答（INCORRECT_AFTER_REVEAL）：KC 卡按 AGAIN lapse、f 增加。
        snapshot = projector.project(
            snapshot,
            listOf(
                attempt(
                    id = "a-reveal-answer",
                    sequence = 4,
                    evidence = LearningEvidence(
                        LearningEvidenceDirection.NEGATIVE,
                        1.0,
                        LearningEvidenceReason.INCORRECT_AFTER_REVEAL,
                    ),
                    occurredAt = revealAt + 60_000,
                ),
            ),
            4,
        ).snapshot
        val afterAnswer = snapshot.knowledgeMasteryStates.getValue("kc-a")
        assertTrue(
            "看答案后的作答把 KC 卡 lapse",
            afterAnswer.memoryStabilityDays!! < afterRevealKc.memoryStabilityDays!!,
        )
        assertEquals(1.0, afterAnswer.failureWeight, 1e-9)

        // 聊天自述证据（CHAT_EVIDENCE 通道）：只动 s/f 与展示数值，不碰卡（E 判据白名单）。
        snapshot = projector.project(
            snapshot,
            listOf(
                ChatEvidenceSubmitted(
                    evidenceId = "chat-1",
                    conversationId = "conversation-1",
                    knowledgeNodeId = "kc-a",
                    direction = LearningEvidenceDirection.POSITIVE,
                    weight = 0.35,
                    reasonMarkdown = "讲通了",
                    confidence = 0.9,
                    occurredAtEpochMillis = BASE_AT_MILLIS + 3 * DAY_MILLIS,
                    eventSequence = 5,
                ),
            ),
            5,
        ).snapshot
        val afterChat = snapshot.knowledgeMasteryStates.getValue("kc-a")
        assertEquals(
            "聊天通道不得改卡",
            afterAnswer.memoryStabilityDays!!,
            afterChat.memoryStabilityDays!!,
            0.0,
        )
        assertEquals(
            "聊天通道只累加 s/f",
            afterAnswer.successWeight + 0.35,
            afterChat.successWeight,
            1e-9,
        )
    }

    // ---- fixtures（与 FsrsProjectionBehaviorTest 同族）----

    private fun attempt(
        id: String,
        sequence: Long,
        evidence: LearningEvidence,
        occurredAt: Long,
        unit: String = "unit-1",
        family: String = "family-$id",
        knowledgeNodeId: String = "kc-a",
    ): Attempt {
        val outcome = when {
            evidence.direction == LearningEvidenceDirection.NONE -> ProblemMemoryOutcome.ANSWER_REVEALED
            evidence.signedWeight > 0 -> ProblemMemoryOutcome.INDEPENDENT_RECALL
            else -> ProblemMemoryOutcome.RETRIEVAL_FAILURE
        }
        return Attempt(
            attemptId = id,
            presentationId = "presentation-$id",
            responseOrdinal = 1,
            assessmentSnapshot = assessmentSnapshot(id, unit, family, knowledgeNodeId),
            evidence = evidence,
            problemMemoryOutcome = outcome,
            occurredAtEpochMillis = occurredAt,
            durationSeconds = 60,
            studyDay = StudyDayContext(
                epochDay = StudyDayMath.localEpochDayOf(occurredAt, utcOffsetMinutes = 0),
                timeZoneId = "UTC",
                utcOffsetMinutes = 0,
            ),
            eventSequence = sequence,
        )
    }

    private fun assessmentSnapshot(
        id: String,
        unit: String = "unit-1",
        family: String = "family-$id",
        knowledgeNodeId: String = "kc-a",
    ) = AssessmentEvidenceSnapshot(
        snapshotId = "snapshot-$id",
        assessmentItemId = "assessment-$id",
        practiceUnitId = unit,
        problemRevisionId = "revision-1",
        answerSpecId = "answer-1",
        itemFamilyId = family,
        sourceBundleId = "source-$id",
        taxonomyVersion = "taxonomy-v1",
        verification = AssessmentSnapshotVerification.VERIFIED,
        calibration = CalibrationSnapshot(
            CalibrationSupport.SUPPORTED,
            "calibration-source",
            "calibration-v1",
            0,
            400 * DAY_MILLIS,
        ),
        attributions = listOf(
            KnowledgeEvidenceAttribution(
                bindingId = "binding-$unit",
                knowledgeNodeId = knowledgeNodeId,
                weight = 1.0,
                basisRevisionId = "revision-1",
                taxonomyVersion = "taxonomy-v1",
                role = EvidenceAttributionRole.PRIMARY,
                certainty = EvidenceAttributionCertainty.DIRECT,
            ),
        ),
        capturedAtEpochMillis = 0,
    )

    private fun easyEvidence(weight: Double = 1.0) = LearningEvidence(
        LearningEvidenceDirection.POSITIVE,
        weight,
        LearningEvidenceReason.INDEPENDENT_CORRECT,
    )

    private fun wrongEvidence() = LearningEvidence(
        LearningEvidenceDirection.NEGATIVE,
        1.0,
        LearningEvidenceReason.INDEPENDENT_INCORRECT,
    )

    private companion object {
        const val DAY_MILLIS = AlgorithmConstants.DAY_MILLIS

        /** W2-4/KF-25 夹具纪律：时间基取 2026-01-05 09:00 UTC（远离纪元，04:00 日界下日序为正）。 */
        const val BASE_AT_MILLIS = 1_767_603_600_000L
    }
}
