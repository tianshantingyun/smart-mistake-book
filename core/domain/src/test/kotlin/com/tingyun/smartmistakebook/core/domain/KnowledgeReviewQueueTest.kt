package com.tingyun.smartmistakebook.core.domain

import com.tingyun.smartmistakebook.core.model.CalibrationSnapshot
import com.tingyun.smartmistakebook.core.model.CalibrationSupport
import com.tingyun.smartmistakebook.core.model.IndependentCorrectObservation
import com.tingyun.smartmistakebook.core.model.KnowledgeMasteryState
import com.tingyun.smartmistakebook.core.model.MasteryStatus
import com.tingyun.smartmistakebook.core.model.ProblemMemoryState
import com.tingyun.smartmistakebook.core.model.ReviewReason
import com.tingyun.smartmistakebook.core.model.ReviewDifficultyBand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 知识点复习队列编排（spec dual-review-entry §3.2）：今天复习的知识点队列由错题排程同构的
 * 打分（scoreKnowledgeNode，T2）决定——打分、按时间预算取队，并在分数接近时用"同讲解材料/
 * 同科目降权"和"难度档轮换"（与错题排程同一套机制）让会话交错。纯函数。
 *
 * 3E（2026-10-16 裁定）：范围扩为**两档**——今日队列 + 全库扫描（有绑定题 ∧ 材料可出题 ∧
 * 不在今日队列，每日 ≤ `FULL_LIBRARY_SCAN_DAILY_CAP` 个）。档内范围条件由
 * `fullLibraryScanCandidate` 落地（无绑定题/无材料 = 不在范围），名额与时间预算两道封顶
 * 由 `selectKnowledgeReviewQueue` 落地；本文件同时钉这两条。
 */
class KnowledgeReviewQueueTest {

    private val scorer = KnowledgeNodeScorer()
    private val now = 1_000_000_000_000L

    private fun mastery(id: String, status: MasteryStatus): KnowledgeMasteryState = KnowledgeMasteryState(
        knowledgeNodeId = id,
        masteryScore = 0.95,
        conservativeMasteryScore = 0.9,
        evidenceMass = 0.0,
        status = status,
        calibrationSupport = CalibrationSupport.SUPPORTED,
        projectorVersion = "test",
        checkpointSequence = 1,
        lastEvidenceAtEpochMillis = now,
    )

    /** 一条被校准为 SUPPORTED 的独立答对观察——构造非 HARD 难度档所必需。 */
    private fun supportedObservation(familyId: String) = IndependentCorrectObservation(
        itemFamilyId = familyId,
        studyDayEpochDay = 1,
        occurredAtEpochMillis = now,
        evidenceWeight = 1.0,
        calibration = CalibrationSnapshot(
            support = CalibrationSupport.SUPPORTED,
            sourceId = "test-calibration",
            version = "test-v1",
            validFromEpochMillis = 0,
            validUntilEpochMillis = now + 1,
        ),
    )

    private fun candidate(
        id: String,
        subject: String = "MATH",
        material: String? = "material-$id",
        state: KnowledgeMasteryState? = null,
        seconds: Int = 60,
        recallRisk: Double? = null,
    ) = KnowledgeReviewCandidate(
        knowledgeNodeId = id,
        subjectId = subject,
        materialGroupId = material,
        state = state,
        estimatedDurationSeconds = seconds,
        recallRisk = recallRisk,
    )

    @Test
    fun knowledgeRecallRiskAggregatesTheWeakestBoundItemAndSkipsUnknownOnes() {
        val memory = ProblemMemoryState(
            practiceUnitId = "unit-1",
            stabilityDays = 10.0,
            difficulty = 5.0,
            lastReviewedAtEpochMillis = now - 86_400_000L,
            nextReviewAtEpochMillis = now,
            projectorVersion = "test",
            checkpointSequence = 1,
        )
        val risks = knowledgeRecallRiskByNode(
            boundPracticeUnitIdsByNode = mapOf(
                "kc-strong" to listOf("unit-1"),
                "kc-no-memory" to listOf("unit-missing"),
            ),
            memoryStates = mapOf("unit-1" to memory),
            nowEpochMillis = now,
            decay = -FsrsScheduleMath.DEFAULT_PARAMETERS[20],
        )
        // 一道稳定题的 R 很高（近 1），但仍是"已知记忆"。
        assertTrue(risks.getValue("kc-strong") > 0.9)
        // 没有已知记忆的绑定题不进结果，由调用方回退到掌握度估计。
        assertTrue("kc-no-memory" !in risks)
    }

    @Test
    fun knowledgeRecallRiskUsesTheMinimumOverBoundItems() {
        val fresh = ProblemMemoryState(
            practiceUnitId = "unit-fresh",
            stabilityDays = 100.0,
            difficulty = 5.0,
            lastReviewedAtEpochMillis = now - 86_400_000L,
            nextReviewAtEpochMillis = now,
            projectorVersion = "test",
            checkpointSequence = 1,
        )
        val decayed = fresh.copy(practiceUnitId = "unit-decayed", stabilityDays = 0.2)
        val risks = knowledgeRecallRiskByNode(
            boundPracticeUnitIdsByNode = mapOf("kc-1" to listOf("unit-fresh", "unit-decayed")),
            memoryStates = mapOf("unit-fresh" to fresh, "unit-decayed" to decayed),
            nowEpochMillis = now,
            decay = -FsrsScheduleMath.DEFAULT_PARAMETERS[20],
        )
        assertTrue(risks.getValue("kc-1") < 0.8)
    }

    @Test
    fun scoreKnowledgeNodeUsesRecallRiskWhenProvided() {
        val state = mastery("kc1", MasteryStatus.LEARNING)
        val decayed = requireNotNull(
            scorer.scoreKnowledgeNode("kc1", state, now, recallRisk = 0.2),
        )
        val fresh = requireNotNull(
            scorer.scoreKnowledgeNode("kc1", state, now, recallRisk = 0.95),
        )
        assertTrue(ReviewReason.DUE_RECALL_RISK in decayed.reasons)
        assertTrue(
            "decayed score ${decayed.score} must exceed fresh score ${fresh.score}",
            decayed.score > fresh.score,
        )
    }

    @Test
    fun skipsMasteredNodesAndKeepsRiskyOnes() {
        val candidates = listOf(
            candidate("kc1", state = mastery("kc1", MasteryStatus.CONFLICTED)),
            candidate("kc2", state = mastery("kc2", MasteryStatus.UNKNOWN)),
            candidate("kc3", state = mastery("kc3", MasteryStatus.MASTERED)),
        )
        val queue = selectKnowledgeReviewQueue(
            scorer = scorer,
            candidates = candidates,
            now = now,
            timeBudgetSeconds = 120,
        )
        assertTrue(queue.isNotEmpty())
        assertTrue(queue.none { it.knowledgeNodeId == "kc3" })
        assertTrue(queue.all { it.score >= 1.0 })
    }

    @Test
    fun respectsTheTimeBudget() {
        val candidates = listOf(
            candidate("kc1", state = mastery("kc1", MasteryStatus.UNKNOWN), seconds = 120),
            candidate("kc2", state = mastery("kc2", MasteryStatus.CONFLICTED), seconds = 120),
        )
        val queue = selectKnowledgeReviewQueue(
            scorer = scorer,
            candidates = candidates,
            now = now,
            timeBudgetSeconds = 120,
        )
        assertTrue(queue.size == 1)
    }

    @Test
    fun emptyOrAllMasteredCandidatesYieldsEmptyQueue() {
        assertTrue(
            selectKnowledgeReviewQueue(
                scorer = scorer,
                candidates = emptyList(),
                now = now,
                timeBudgetSeconds = 120,
            ).isEmpty(),
        )
    }

    @Test
    fun ordersRiskyNodesByDescendingScore() {
        // 一个高风险的冲突点 + 一个新鲜的学习中点。两者都高级别风险，值按降序排队。
        val weak = mastery("kc-w", MasteryStatus.LEARNING).copy(
            masteryScore = 0.4,
            conservativeMasteryScore = 0.3,
        )
        val candidates = listOf(
            candidate("kc1", state = mastery("kc1", MasteryStatus.CONFLICTED)),
            candidate("kc-w", state = weak),
        )
        val queue = selectKnowledgeReviewQueue(
            scorer = scorer,
            candidates = candidates,
            now = now,
            timeBudgetSeconds = 240,
        )
        assertTrue(queue.size == 2)
        // 分数仍是主导：多样性/难度只做小幅调整，不足以翻转明显分差。
        assertTrue(queue.zipWithNext().all { (a, b) -> a.score >= b.score })
    }

    @Test
    fun interleavesTeachingMaterialsInsteadOfQuizzingTheSameMaterialBackToBack() {
        // 两个节点共用 m1、两个共用 m2，分数完全平局（都无证据）——降权让材料交错。
        val candidates = listOf(
            candidate("kc-a", material = "m1"),
            candidate("kc-b", material = "m1"),
            candidate("kc-c", material = "m2"),
            candidate("kc-d", material = "m2"),
        )
        val queue = selectKnowledgeReviewQueue(
            scorer = scorer,
            candidates = candidates,
            now = now,
            timeBudgetSeconds = 240,
        )
        assertEquals(4, queue.size)
        val materials = queue.map { scored ->
            candidates.single { it.knowledgeNodeId == scored.knowledgeNodeId }.materialGroupId
        }
        assertTrue(materials.zipWithNext().all { (a, b) -> a != b })
    }

    @Test
    fun interleavesSubjectsWhenMaterialsAlreadyDiffer() {
        // 材料各不相同，只有科目重复——同科降权仍应让两个科目交错。
        val candidates = listOf(
            candidate("kc-a", subject = "MATH", material = "m1"),
            candidate("kc-b", subject = "MATH", material = "m2"),
            candidate("kc-c", subject = "PHYSICS", material = "m3"),
            candidate("kc-d", subject = "PHYSICS", material = "m4"),
        )
        val queue = selectKnowledgeReviewQueue(
            scorer = scorer,
            candidates = candidates,
            now = now,
            timeBudgetSeconds = 240,
        )
        assertEquals(4, queue.size)
        val subjects = queue.map { scored ->
            candidates.single { it.knowledgeNodeId == scored.knowledgeNodeId }.subjectId
        }
        assertTrue(subjects.zipWithNext().all { (a, b) -> a != b })
    }

    @Test
    fun neverDropsNodesWhenEveryCandidateSharesOneMaterial() {
        // 回归护栏：多样性必须是软降权。若做成"硬约束"，全部共用一份材料时会截断到 1 个，
        // 学生会永远只复习到一个知识点。
        val candidates = (1..3).map { index -> candidate("kc-$index", material = "m-only") }
        val queue = selectKnowledgeReviewQueue(
            scorer = scorer,
            candidates = candidates,
            now = now,
            timeBudgetSeconds = 240,
        )
        assertEquals(3, queue.size)
    }

    @Test
    fun scoresKnowledgeNodesWithAMasteryDerivedDifficultyBand() {
        // 无证据 → 最难（HARD）；有一份被支撑的答对观察、平滑掌握 0.5 → MEDIUM。
        val medium = mastery("kc-mid", MasteryStatus.LEARNING).copy(
            masteryScore = 0.5,
            conservativeMasteryScore = 0.0,
            evidenceMass = 1.0,
            independentCorrectObservations = listOf(supportedObservation("family-mid")),
        )
        assertEquals(
            ReviewDifficultyBand.HARD,
            scorer.scoreKnowledgeNode("kc-none", null, now)!!.difficultyBand,
        )
        assertEquals(
            ReviewDifficultyBand.MEDIUM,
            scorer.scoreKnowledgeNode("kc-mid", medium, now)!!.difficultyBand,
        )
    }

    @Test
    fun cyclesDifficultyBandsWhenScoresTie() {
        // 无证据节点与"平滑掌握 0.5"的节点同为 4.0 分（5×0.2+3×1.0 = 5×0.5+3×0.5）：
        // 平局时按难度循环（首档 MEDIUM）先取中等难度的点，而不是永远按 id 取。
        val medium = mastery("kc-mid", MasteryStatus.LEARNING).copy(
            masteryScore = 0.5,
            conservativeMasteryScore = 0.0,
            evidenceMass = 1.0,
            independentCorrectObservations = listOf(supportedObservation("family-mid")),
        )
        val candidates = listOf(
            candidate("kc-none", material = "m-none"),
            candidate("kc-mid", material = "m-mid", state = medium),
        )
        val queue = selectKnowledgeReviewQueue(
            scorer = scorer,
            candidates = candidates,
            now = now,
            timeBudgetSeconds = 240,
        )
        assertEquals(listOf("kc-mid", "kc-none"), queue.map { it.knowledgeNodeId })
    }

    @Test
    fun sessionPlanRejectsDuplicateNodes() {
        assertThrows(IllegalArgumentException::class.java) {
            KnowledgeReviewSessionPlan(
                queue = listOf(entry("kc1"), entry("kc1")),
            )
        }
    }

    @Test
    fun sessionPlanRejectsBlankEntryFields() {
        assertThrows(IllegalArgumentException::class.java) {
            KnowledgeReviewQueueEntry(
                knowledgeNodeId = "",
                subject = "MATH",
                displayName = "一元二次方程",
                masteryScore = 0.5,
                lastEvidenceAtEpochMillis = now,
            )
        }
    }

    @Test
    fun sessionPlanAcceptsNoEvidenceNodeAsUnknown() {
        // 无掌握态的知识点（从未有过证据）也必须是合法候选——planner 把它当 NEWLY_ADDED，
        // 不能因为它没有掌握态条目就被计划拒绝。
        val plan = KnowledgeReviewSessionPlan(
            queue = listOf(
                entry("kc-fresh").copy(
                    masteryScore = null,
                    lastEvidenceAtEpochMillis = null,
                ),
            ),
        )
        assertTrue(plan.queue.single().masteryScore == null)
    }

    private fun entry(knowledgeNodeId: String) = KnowledgeReviewQueueEntry(
        knowledgeNodeId = knowledgeNodeId,
        subject = "MATH",
        displayName = "知识点$knowledgeNodeId",
        masteryScore = 0.5,
        lastEvidenceAtEpochMillis = now,
    )

    // ---- 3E 全库薄弱点扫描（第二档，2026-10-16 裁定「每日 10 个知识点」）----

    @Test
    fun fullLibraryScanCandidateStaysOutOfScopeWithoutBoundQuestionsOrMaterial() {
        val state = mastery("kc-scan", MasteryStatus.CONFLICTED)
        // 无绑定题 → 不在扫描范围：没有承载题就没有时间信号（需求定稿 R5 的残留边界）。
        assertNull(
            "无绑定题的节点不得成为扫描候选",
            fullLibraryScanCandidate(
                knowledgeNodeId = "kc-scan",
                subjectId = "MATH",
                materialGroupId = "material-kc-scan",
                boundPracticeUnitIds = emptyList(),
                state = state,
                recallRisk = null,
            ),
        )
        // 无可出题材料 → 不在扫描范围：排进会话也取不到题（spec §3.3 防臆造锚）。
        assertNull(
            "无材料的节点不得成为扫描候选",
            fullLibraryScanCandidate(
                knowledgeNodeId = "kc-scan",
                subjectId = "MATH",
                materialGroupId = null,
                boundPracticeUnitIds = listOf("unit-1"),
                state = state,
                recallRisk = null,
            ),
        )
        assertNull(
            "空白材料组与 null 同义",
            fullLibraryScanCandidate(
                knowledgeNodeId = "kc-scan",
                subjectId = "MATH",
                materialGroupId = "  ",
                boundPracticeUnitIds = listOf("unit-1"),
                state = state,
                recallRisk = null,
            ),
        )
        // 有绑定题 + 有材料 → 候选；估时与今日档同一个中档基线，科目与材料组原样带上。
        val candidate = requireNotNull(
            fullLibraryScanCandidate(
                knowledgeNodeId = "kc-scan",
                subjectId = "MATH",
                materialGroupId = "material-kc-scan",
                boundPracticeUnitIds = listOf("unit-1"),
                state = state,
                recallRisk = 0.4,
            ),
        )
        assertEquals("kc-scan", candidate.knowledgeNodeId)
        assertEquals("MATH", candidate.subjectId)
        assertEquals("material-kc-scan", candidate.materialGroupId)
        assertEquals(state, candidate.state)
        assertEquals(0.4, candidate.recallRisk!!, 1e-9)
        assertEquals(
            LogDurationModel.TIER_BASELINE_MEDIUM_SECONDS,
            candidate.estimatedDurationSeconds,
        )
    }

    @Test
    fun recoversWeakNodesThatAreNotInTodaysQueue() {
        // 第二档的意义：今日错题没碰到的薄弱点照样进队列（3E 的"不受今日队列限制"）。
        val today = listOf(candidate("kc-today", state = mastery("kc-today", MasteryStatus.UNKNOWN)))
        val scanned = listOf(
            candidate("kc-outside", state = mastery("kc-outside", MasteryStatus.CONFLICTED)),
        )
        val queue = selectKnowledgeReviewQueue(
            scorer = scorer,
            candidates = today,
            now = now,
            timeBudgetSeconds = 240,
            fullLibraryScanCandidates = scanned,
        )
        assertEquals(
            setOf("kc-today", "kc-outside"),
            queue.mapTo(linkedSetOf()) { it.knowledgeNodeId },
        )
    }

    @Test
    fun capsTheFullLibraryScanAtTheDailyCap() {
        val scanned = (1..FULL_LIBRARY_SCAN_DAILY_CAP + 5).map { index ->
            candidate(
                id = "kc-scan-$index",
                material = "material-$index",
                state = mastery("kc-scan-$index", MasteryStatus.CONFLICTED),
            )
        }
        val queue = selectKnowledgeReviewQueue(
            scorer = scorer,
            candidates = emptyList(),
            now = now,
            timeBudgetSeconds = 3_600,
            fullLibraryScanCandidates = scanned,
        )
        assertEquals(
            "第二档每日名额封顶必须生效（F17 配额）",
            FULL_LIBRARY_SCAN_DAILY_CAP,
            queue.size,
        )
        assertTrue(queue.all { it.knowledgeNodeId.startsWith("kc-scan-") })
    }

    @Test
    fun aZeroDailyCapDisablesTheSecondTierEntirely() {
        val scanned = listOf(
            candidate("kc-outside", state = mastery("kc-outside", MasteryStatus.CONFLICTED)),
        )
        val queue = selectKnowledgeReviewQueue(
            scorer = scorer,
            candidates = emptyList(),
            now = now,
            timeBudgetSeconds = 240,
            fullLibraryScanCandidates = scanned,
            fullLibraryScanDailyCap = 0,
        )
        assertTrue(queue.isEmpty())
    }

    @Test
    fun timeBudgetDropsMergedTierEntriesAlike() {
        // 名额之外还有时间预算这道封顶：1 个今日点 + 3 个扫描点，预算 120s / 每点 60s → 只排 2 个。
        val today = listOf(
            candidate("kc-today", state = mastery("kc-today", MasteryStatus.CONFLICTED), seconds = 60),
        )
        val scanned = (1..3).map { index ->
            candidate(
                id = "kc-scan-$index",
                material = "material-$index",
                state = mastery("kc-scan-$index", MasteryStatus.UNKNOWN),
                seconds = 60,
            )
        }
        val queue = selectKnowledgeReviewQueue(
            scorer = scorer,
            candidates = today,
            now = now,
            timeBudgetSeconds = 120,
            fullLibraryScanCandidates = scanned,
        )
        assertEquals(2, queue.size)
    }

    @Test
    fun todayQueueEntryWinsWhenTheSameNodeIsInBothTiers() {
        val todayState = mastery("kc-shared", MasteryStatus.CONFLICTED)
        val queue = selectKnowledgeReviewQueue(
            scorer = scorer,
            candidates = listOf(
                candidate("kc-shared", material = "material-today", state = todayState),
            ),
            now = now,
            timeBudgetSeconds = 240,
            fullLibraryScanCandidates = listOf(
                candidate("kc-shared", material = "material-scan", recallRisk = 0.2),
            ),
        )
        val expected = requireNotNull(scorer.scoreKnowledgeNode("kc-shared", todayState, now)).score
        assertEquals(1, queue.size)
        assertEquals("并集同 id 必须以今日档那一条为准", expected, queue.single().score, 1e-9)
    }

    /**
     * 规模门（S9/S17 纪律的 JVM 侧形态，同 `ReviewPlannerScaleBenchmarkTest` 的口径）：
     * 全库 5000 个扫描候选，只按分取前 N、且整体在宽门内跑完——它钉的是"扫描不因库规模变成
     * 逐节点/二次方开销"，不是微基准标定值。预热 2 次取 best-of-3，门给 1500ms（实际约几十毫秒）。
     */
    @Test
    fun fiveThousandFullLibraryScanCandidatesStayWithinTheDailyCapAndScaleGate() {
        val scanned = (1..5_000).map { index ->
            candidate(
                id = "kc-scan-$index",
                material = "material-$index",
                state = mastery("kc-scan-$index", MasteryStatus.LEARNING).copy(
                    masteryScore = 0.2,
                    conservativeMasteryScore = 0.1,
                ),
                seconds = 60,
            )
        }
        fun runQueue() = selectKnowledgeReviewQueue(
            scorer = scorer,
            candidates = emptyList(),
            now = now,
            timeBudgetSeconds = 1_200,
            fullLibraryScanCandidates = scanned,
        )

        repeat(2) { runQueue() }
        var bestMillis = Long.MAX_VALUE
        var queue: List<KnowledgeNodeScorer.ScoredKnowledgeNode> = emptyList()
        repeat(3) {
            bestMillis = minOf(
                bestMillis,
                kotlin.system.measureTimeMillis { queue = runQueue() },
            )
        }
        assertEquals(FULL_LIBRARY_SCAN_DAILY_CAP, queue.size)
        assertTrue(
            "5000 扫描候选取队 best-of-3 实测 ${bestMillis}ms，超过 1500ms 宽门",
            bestMillis < 1_500,
        )
    }
}
