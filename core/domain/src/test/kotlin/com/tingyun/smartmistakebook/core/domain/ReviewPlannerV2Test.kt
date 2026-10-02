package com.tingyun.smartmistakebook.core.domain

import com.tingyun.smartmistakebook.core.model.CalibrationSnapshot
import com.tingyun.smartmistakebook.core.model.CalibrationSupport
import com.tingyun.smartmistakebook.core.model.IndependentCorrectObservation
import com.tingyun.smartmistakebook.core.model.KnowledgeMasteryState
import com.tingyun.smartmistakebook.core.model.LearnerSnapshot
import com.tingyun.smartmistakebook.core.model.LearnerSnapshotFreshness
import com.tingyun.smartmistakebook.core.model.MasteryStatus
import com.tingyun.smartmistakebook.core.model.ProblemMemoryState
import com.tingyun.smartmistakebook.core.model.ProjectionCheckpoint
import com.tingyun.smartmistakebook.core.model.ReviewReason
import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Property-style tests for [ReviewPlannerV2] (audit §3.4 / §7.2). Uses
 * fixed-seed random generators so the runs are deterministic and replayable.
 *
 * With an empty [LogDurationModel] every candidate's modeled duration is the
 * global prior ([LogDurationModel.GLOBAL_PRIOR_SECONDS] = 60s), which keeps
 * the budget arithmetic in these tests exact.
 */
class ReviewPlannerV2Test {
    private val planner = ReviewPlannerV2()

    @Test
    fun `randomized plans never repeat practice units and never exceed the budget`() {
        val random = Random(42)
        repeat(25) { trial ->
            val count = 20 + random.nextInt(41)
            val candidates = (0 until count).map { index ->
                candidate(
                    unitId = "unit-$trial-$index",
                    familyId = "family-${random.nextInt(8)}",
                    sourceId = "source-${random.nextInt(6)}",
                    difficulty = random.nextDouble(1.0, 10.0),
                    seconds = 60,
                    subjectId = "subject-${random.nextInt(3)}",
                )
            }

            val plan = planner.plan(request(candidates, 900))

            val ids = plan.queueItems.map { it.practiceUnitId }
            assertEquals(ids.size, ids.distinct().size)
            assertTrue(plan.totalEstimatedDurationSeconds <= 900)
        }
    }

    @Test
    fun `same input and same model state replay to the same fingerprint`() {
        val candidates = (0 until 12).map { index ->
            candidate(
                unitId = "unit-$index",
                familyId = "family-$index",
                sourceId = "source-$index",
                difficulty = 5.5,
                seconds = 60,
                subjectId = "subject-${index % 3}",
            )
        }
        val reviewRequest = request(candidates, 600)

        val first = planner.plan(reviewRequest)
        val second = planner.plan(reviewRequest)

        assertEquals(first, second)
        assertEquals(first.planFingerprint, second.planFingerprint)
        assertEquals(first.planId, second.planId)

        val shifted = planner.plan(reviewRequest.copy(planningAtEpochMillis = now + 1))
        assertTrue(first.planFingerprint != shifted.planFingerprint)
    }

    @Test
    fun `budget fill median reaches at least 85 percent on synthetic data`() {
        val random = Random(7)
        val budget = 900
        val fills = (0 until 21).map { trial ->
            val count = 25 + random.nextInt(36)
            val candidates = (0 until count).map { index ->
                candidate(
                    unitId = "unit-$trial-$index",
                    familyId = "family-${index % 10}",
                    sourceId = "source-${index % 7}",
                    difficulty = random.nextDouble(1.0, 10.0),
                    seconds = 60,
                    subjectId = "subject-${random.nextInt(3)}",
                )
            }
            val plan = planner.plan(request(candidates, budget))
            plan.totalEstimatedDurationSeconds / budget.toDouble()
        }

        val median = fills.sorted()[fills.size / 2]
        assertTrue("median budget fill was $median", median >= 0.85)
        assertTrue(fills.all { it <= 1.0 })
    }

    @Test
    fun `every selected item explains why it was scheduled`() {
        val candidates = (0 until 8).map { index ->
            candidate(
                unitId = "unit-$index",
                familyId = "family-$index",
                sourceId = null,
                difficulty = 5.5,
                seconds = 60,
                subjectId = "subject-${index % 2}",
            )
        }

        val plan = planner.plan(request(candidates, 480))

        assertTrue(plan.queueItems.isNotEmpty())
        plan.queueItems.forEach { item ->
            assertTrue(item.reasons.isNotEmpty())
            assertTrue(ReviewReason.NEWLY_ADDED in item.reasons)
        }
    }

    @Test
    fun `same item family never appears in consecutive positions`() {
        val candidates = (0 until 12).map { index ->
            candidate(
                unitId = "unit-$index",
                familyId = "family-${index % 3}",
                sourceId = "source-$index",
                difficulty = 5.5,
                seconds = 60,
                subjectId = "subject-${index % 2}",
            )
        }

        val plan = planner.plan(request(candidates, 720))

        assertTrue(plan.queueItems.isNotEmpty())
        plan.queueItems.zipWithNext().forEach { (left, right) ->
            assertTrue(
                "adjacent items share family ${left.itemFamilyId}",
                left.itemFamilyId != right.itemFamilyId,
            )
        }
    }

    @Test
    fun `same subject never runs longer than three consecutive items`() {
        val singleSubject = (0 until 8).map { index ->
            candidate(
                unitId = "single-$index",
                familyId = "family-s$index",
                sourceId = null,
                difficulty = 5.5,
                seconds = 60,
                subjectId = "subject-single",
            )
        }
        val mixer = candidate(
            unitId = "mixer",
            familyId = "family-mixer",
            sourceId = null,
            difficulty = 5.5,
            seconds = 60,
            subjectId = "subject-other",
        )
        val candidates = singleSubject + mixer

        val plan = planner.plan(request(candidates, 900))

        val candidateById = candidates.associateBy(ReviewCandidate::practiceUnitId)
        val subjects = plan.queueItems.map {
            candidateById.getValue(it.practiceUnitId).subjectId
        }
        var run = 1
        var maxRun = 1
        subjects.zipWithNext().forEach { (left, right) ->
            run = if (left == right) run + 1 else 1
            maxRun = maxOf(maxRun, run)
        }
        assertTrue("subject run reached $maxRun", maxRun <= 3)
        // Eight same-subject candidates plus one mixer can schedule at most
        // 3 + 1 + 3 = 7 items under the run constraint.
        assertTrue(plan.queueItems.size <= 7)
        assertTrue(plan.queueItems.size >= 4)
    }

    @Test
    fun `degenerate single candidate scenarios still fill the budget`() {
        val single = planner.plan(
            request(listOf(candidate("unit-a", "family-a", null, 5.5, 60)), 600),
        )
        assertEquals(listOf("unit-a"), single.queueItems.map { it.practiceUnitId })
        assertEquals(60, single.totalEstimatedDurationSeconds)

        val pair = planner.plan(
            request(
                listOf(
                    candidate("unit-a", "family-a", null, 5.5, 60),
                    candidate("unit-b", "family-b", null, 5.5, 60),
                ),
                600,
            ),
        )
        assertEquals(2, pair.queueItems.size)
        assertEquals(120, pair.totalEstimatedDurationSeconds)

        val empty = planner.plan(request(emptyList(), 600))
        assertTrue(empty.queueItems.isEmpty())
        assertEquals(0, empty.totalEstimatedDurationSeconds)
    }

    @Test
    fun `beam collapse still returns the best partial plan instead of an empty queue`() {
        // All candidates share one item family, so the hard "no consecutive
        // family" constraint allows at most one selection. Beam pruning can
        // then drop every expandable state; the planner must still return the
        // best partial plan instead of collapsing to an empty queue (the old
        // beam early-stop regression).
        val candidates = (0 until 5).map { index ->
            candidate(
                unitId = "unit-$index",
                familyId = "family-shared",
                sourceId = "source-$index",
                difficulty = 5.5,
                seconds = 60,
                subjectId = "subject-$index",
            )
        }

        val plan = planner.plan(request(candidates, 600))

        assertEquals(1, plan.queueItems.size)
        assertEquals("unit-0", plan.queueItems.single().practiceUnitId)
        assertEquals(60, plan.totalEstimatedDurationSeconds)
    }

    @Test
    fun `v2 is not worse than a first-fit greedy baseline on adversarial data`() {
        // Adversarial ordering: low-value candidates first, high-value ones
        // (exam priority) at the back. A first-fit baseline scanning in input
        // order fills the entire budget with low-value items.
        val lowValue = (0 until 10).map { index ->
            candidate(
                unitId = "low-$index",
                familyId = "family-low-$index",
                sourceId = null,
                difficulty = 5.5,
                seconds = 60,
                subjectId = "subject-${index % 3}",
            )
        }
        val highValue = (0 until 10).map { index ->
            candidate(
                unitId = "high-$index",
                familyId = "family-high-$index",
                sourceId = null,
                difficulty = 5.5,
                seconds = 60,
                subjectId = "subject-${index % 3}",
            ).copy(examPriority = 1.0)
        }
        val candidates = lowValue + highValue
        val budget = 600

        val plan = planner.plan(request(candidates, budget))

        // First-fit baseline: take candidates in input order while they fit.
        val baseline = mutableListOf<ReviewCandidate>()
        var remainingSeconds = budget
        for (item in candidates) {
            if (item.estimatedDurationSeconds <= remainingSeconds) {
                baseline += item
                remainingSeconds -= item.estimatedDurationSeconds
            }
        }
        val baselineHighCount = baseline.count { it.practiceUnitId.startsWith("high-") }
        val v2HighCount = plan.queueItems.count { it.practiceUnitId.startsWith("high-") }

        assertTrue(
            "V2 selected $v2HighCount high-value items, baseline $baselineHighCount",
            v2HighCount > baselineHighCount,
        )
        // V2 fills at least as much of the budget as the baseline.
        assertTrue(plan.totalEstimatedDurationSeconds >= budget - remainingSeconds)
        assertTrue(plan.totalEstimatedDurationSeconds <= budget)
    }

    @Test
    fun `swap keeps the high value item over a weak but more diverse one`() {
        // Behavior-contract test (not a bug-reproduction): with a large score
        // gap the swap phase must keep the high-value pair even when the
        // alternative adds diversity. The old absolute total-utility bonus
        // only mis-fired in a narrow band (score gap < diversity gain), which
        // this scenario does not reproduce — it pins the contract that a
        // weak-but-diverse item must not crowd out a high-value one.
        // A and B are high-value: their KC is CONFLICTED (a mastery
        // emergency), so each scores well above C, whose KC has recent
        // supported evidence and near-certain mastery (low mastery risk).
        // A and B share a source; C brings a different source and family, so
        // swapping B for C would add diversity — but C's static value is far
        // too low to justify displacing B. The swap phase must keep A and B.
        val highValueSnapshot = masterySnapshot(
            kcId = "kc-conflicted",
            status = MasteryStatus.CONFLICTED,
            mastery = 0.6,
            conservative = 0.55,
        )
        val lowRiskState = KnowledgeMasteryState(
            knowledgeNodeId = "kc-ok",
            masteryScore = 0.95,
            conservativeMasteryScore = 0.92,
            evidenceMass = 4.0,
            status = MasteryStatus.LEARNING,
            calibrationSupport = CalibrationSupport.SUPPORTED,
            projectorVersion = LearningProjector.VERSION,
            checkpointSequence = 4,
            lastEvidenceAtEpochMillis = now,
            independentCorrectObservations = listOf(
                IndependentCorrectObservation(
                    itemFamilyId = "family-ok",
                    studyDayEpochDay = now / DAY_MILLIS,
                    occurredAtEpochMillis = now - 86_400_000L,
                    eventSequence = 1,
                    bindingId = "binding-ok",
                    evidenceWeight = 1.0,
                    calibration = CalibrationSnapshot(
                        CalibrationSupport.SUPPORTED,
                        "source",
                        "v1",
                        now - 86_400_000L,
                        now + 86_400_000L,
                    ),
                ),
            ),
        )
        val combined = highValueSnapshot.copy(
            knowledgeMasteryStates = mapOf(
                "kc-conflicted" to highValueSnapshot.knowledgeMasteryStates.getValue("kc-conflicted"),
                "kc-ok" to lowRiskState,
            ),
        )
        val a = candidate(
            "a", "family-a", "source-shared", 5.5, 60, "subject-1", kc = "kc-conflicted",
        )
        val b = candidate(
            "b", "family-b", "source-shared", 5.5, 60, "subject-2", kc = "kc-conflicted",
        )
        val c = candidate(
            "c", "family-c", "source-other", 5.5, 60, "subject-3", kc = "kc-ok",
        )

        val plan = planner.plan(request(listOf(a, b, c), 120, combined))

        val selected = plan.queueItems.map { it.practiceUnitId }.toSet()
        assertEquals(setOf("a", "b"), selected)
    }

    @Test
    fun `leeched card stays reachable but ranks below a healthy card`() {
        val leeched = memoryState(
            unitId = "unit-leech",
            stabilityDays = 2.0,
            difficulty = 9.0,
            nextReviewAtEpochMillis = now - DAY_MILLIS,
            lapseCount = 7,
            consecutiveCrossDayAgain = 2,
        )
        val healthy = memoryState(
            unitId = "unit-healthy",
            stabilityDays = 2.0,
            difficulty = 5.0,
            nextReviewAtEpochMillis = now - DAY_MILLIS,
        )
        val snapshot = supportedSnapshot().copy(
            problemMemoryStates = mapOf(
                "unit-leech" to leeched,
                "unit-healthy" to healthy,
            ),
        )
        val leechCandidate = candidate("unit-leech", "family-leech", null, 9.0, 60)
            .copy(leech = true)
        val healthyCandidate = candidate("unit-healthy", "family-healthy", null, 5.0, 60)

        // Spec 2.16 pause: with a healthy alternative the leeched card loses.
        val paired = planner.plan(request(listOf(leechCandidate, healthyCandidate), 60, snapshot))
        assertEquals(listOf("unit-healthy"), paired.queueItems.map { it.practiceUnitId })

        // ...but it is never permanently unschedulable: the Again streak only
        // clears through a cross-day success, so it must stay reachable when
        // nothing else is due.
        val solo = planner.plan(request(listOf(leechCandidate), 60, snapshot))
        assertEquals(listOf("unit-leech"), solo.queueItems.map { it.practiceUnitId })
    }

    @Test
    fun `an exam pulls a card forward only below the retrieval ceiling`() {
        val known = memoryState(
            unitId = "unit-known",
            stabilityDays = 100.0,
            difficulty = 5.0,
            nextReviewAtEpochMillis = now + 30 * DAY_MILLIS,
        )
        val decayed = memoryState(
            unitId = "unit-decayed",
            stabilityDays = 0.2,
            difficulty = 5.0,
            nextReviewAtEpochMillis = now + 30 * DAY_MILLIS,
        )
        val snapshot = supportedSnapshot().copy(
            problemMemoryStates = mapOf("unit-known" to known, "unit-decayed" to decayed),
        )
        val knownExam = candidate("unit-known", "family-known", null, 5.0, 60)
            .copy(examPriority = 1.0)
        val decayedExam = candidate("unit-decayed", "family-decayed", null, 5.0, 60)
            .copy(examPriority = 1.0)

        // R ≈ 1: an exam may not pull forward a card the student clearly still
        // remembers (spec 2.17: the exam queue is `R < r*_exam`; 研究 2026-09-09
        // §5 — FSRS gain is e^{w10(1−R)}−1).
        val blocked = planner.plan(request(listOf(knownExam), 60, snapshot))
        assertTrue(blocked.queueItems.isEmpty())

        // R ≈ 0.6: the same exam reason does open the early review.
        val allowed = planner.plan(request(listOf(decayedExam), 60, snapshot))
        assertEquals(listOf("unit-decayed"), allowed.queueItems.map { it.practiceUnitId })
    }

    @Test
    fun `same-KC quota is waived while the knowledge node is not yet learned`() {
        val candidates = (0 until 3).map { index ->
            candidate("unit-$index", "family-$index", null, 5.5, 60, kc = "kc-a")
        } + candidate("unit-other", "family-other", null, 5.5, 60, kc = "kc-b")
        val readyB = masteryState("kc-b", mastery = 0.95, conservative = 0.9)

        fun planWith(a: KnowledgeMasteryState) = planner.plan(
            request(
                candidates,
                240,
                LearnerSnapshot(
                    learnerId = "learner-1",
                    problemMemoryStates = emptyMap(),
                    knowledgeMasteryStates = mapOf("kc-a" to a, "kc-b" to readyB),
                    checkpoint = ProjectionCheckpoint(4, LearningProjector.VERSION, now),
                    generatedAtEpochMillis = now,
                ),
            ),
        )

        // 研究 2026-09-09 §2: below the ready-to-learn threshold the KC is
        // blocked first, so all three same-KC items may be scheduled.
        val blockedFirst = planWith(masteryState("kc-a", mastery = 0.2, conservative = 0.1))
            .queueItems.count { "kc-a" in it.knowledgeNodeIds }
        // Once the KC is ready to learn, the interleaving quota binds again.
        val interleaved = planWith(masteryState("kc-a", mastery = 0.95, conservative = 0.9))
            .queueItems.count { "kc-a" in it.knowledgeNodeIds }

        assertEquals(3, blockedFirst)
        assertTrue("ready KC should be capped below the blocked-first run", interleaved < 3)
    }

    @Test
    fun `a candidate whose prerequisite is missing is hard gated`() {
        // spec §2.9 / §6-L4 + KF-08（2026-10-01）：前置未就绪 ⇒ **排除**（此前是降权）。
        // 这条断言的存在理由是它曾经**从来不可能通过**：生产调用点从不填
        // `knowledgePrerequisites`，于是 prereqGap 恒为 0、闸门从不生效。
        // 因此本测试同时锁两件事——喂入图之后闸门确实起作用，以及缺前置的题被排除。
        val snapshot = snapshotWith(
            masteryState("kc-needs-prereq", mastery = 0.9, conservative = 0.9),
            masteryState("kc-ready", mastery = 0.9, conservative = 0.9),
            masteryState("kc-prereq-weak", mastery = 0.2, conservative = 0.15),
            masteryState("kc-prereq-ready", mastery = 0.95, conservative = 0.9),
        )
        val prerequisites = mapOf(
            "kc-needs-prereq" to setOf("kc-prereq-weak"),
            "kc-ready" to setOf("kc-prereq-ready"),
        )
        val blocked = candidate("unit-blocked", "family-blocked", null, 5.5, 60, kc = "kc-needs-prereq")
        val ready = candidate("unit-ready", "family-ready", null, 5.5, 60, kc = "kc-ready")

        // 预算只够一题：缺前置的那题被排除，只剩 ready。
        val contested = planner.plan(
            request(listOf(blocked, ready), 60, snapshot, prerequisites),
        )
        assertEquals(listOf("unit-ready"), contested.queueItems.map { it.practiceUnitId })

        // 池子够大时它**仍然**不进计划——KF-08 的语义就是硬过滤，不是"排后面"。
        val both = planner.plan(request(listOf(blocked, ready), 120, snapshot, prerequisites))
        assertEquals(listOf("unit-ready"), both.queueItems.map { it.practiceUnitId })
    }

    @Test
    fun `a candidate returns to the plan once its prerequisite recovers`() {
        // KF-08 的"解除"半句：先修恢复（gap 回 0）后同一候选自动回池——闸门是纯函数
        // of（当前图 × 当前掌握），不需要任何额外状态。
        val snapshot = snapshotWith(
            masteryState("kc-needs-prereq", mastery = 0.9, conservative = 0.9),
            masteryState("kc-prereq-becoming-ready", mastery = 0.8, conservative = 0.65),
        )
        val prerequisites = mapOf("kc-needs-prereq" to setOf("kc-prereq-becoming-ready"))
        val dependent = candidate("unit-blocked", "family-blocked", null, 5.5, 60, kc = "kc-needs-prereq")

        // conservative 0.65 ≥ τ_ready 0.6：先修已具备 → 候选在池。
        val plan = planner.plan(request(listOf(dependent), 120, snapshot, prerequisites))
        assertEquals(listOf("unit-blocked"), plan.queueItems.map { it.practiceUnitId })
    }

    @Test
    fun `without a prerequisite graph no candidate is gated`() {
        // 反例，也是这次接线前的生产实况：图是空的（或没被喂进来）时，任何题都不该被
        // 判成缺前置——不知道前置不等于没有前置。
        val snapshot = snapshotWith(
            masteryState("kc-needs-prereq", mastery = 0.9, conservative = 0.9),
            masteryState("kc-ready", mastery = 0.9, conservative = 0.9),
        )
        val blocked = candidate("unit-blocked", "family-blocked", null, 5.5, 60, kc = "kc-needs-prereq")
        val ready = candidate("unit-ready", "family-ready", null, 5.5, 60, kc = "kc-ready")

        val plan = planner.plan(request(listOf(blocked, ready), 120, snapshot))

        assertEquals(2, plan.queueItems.size)
        assertTrue(
            plan.queueItems.none { ReviewReason.PREREQ_GAP in it.reasons },
        )
    }

    @Test
    fun `a chat-fresh but card-overdue knowledge node still raises the stale review risk`() {
        // 裁决 28 双钟回归（V2 候选路径）：知识点的 lastEvidenceAt 刚被聊天证据刷新，但记忆卡
        // 的最后作答在 60 天前——风险分支必须按 STALE_KNOWLEDGE 计。旧代码看 lastEvidenceAt
        // 的新鲜度（45 天窗），一次讲题对话就能把已忘的知识点从复习压力里抹掉。
        val overdueKc = KnowledgeMasteryState(
            knowledgeNodeId = "kc-a",
            masteryScore = 0.95,
            conservativeMasteryScore = 0.9,
            evidenceMass = 2.0,
            memoryStabilityDays = 30.0,
            memoryDifficulty = 6.0,
            lastAttemptAtEpochMillis = now - 60 * DAY_MILLIS,
            lastAttemptStudyDayEpochDay = (now - 60 * DAY_MILLIS) / DAY_MILLIS,
            status = MasteryStatus.MASTERED,
            calibrationSupport = CalibrationSupport.SUPPORTED,
            projectorVersion = LearningProjector.VERSION,
            checkpointSequence = 4,
            lastEvidenceAtEpochMillis = now,
        )
        val chatFreshSnapshot = LearnerSnapshot(
            learnerId = "learner-1",
            problemMemoryStates = emptyMap(),
            knowledgeMasteryStates = mapOf("kc-a" to overdueKc),
            checkpoint = ProjectionCheckpoint(4, LearningProjector.VERSION, now),
            generatedAtEpochMillis = now,
        )

        val plan = planner.plan(
            request(listOf(candidate("unit-a", "family-a", null, 5.5, 60)), 120, chatFreshSnapshot),
        )

        val reasons = plan.queueItems.single().reasons
        assertTrue("记忆卡过期必须带 STALE_KNOWLEDGE，实际：$reasons", ReviewReason.STALE_KNOWLEDGE in reasons)
    }

    // ---- D-M M6：V1 `ReviewPlannerTest` 独有覆盖的转移（13 例逐条核对，不静默丢断言）----
    // 仍适用的行为断言搬到这里；V1 独有的"同族/同源硬排除"与"错题难度循环"随 V1 退场
    //（生产不可达；难度循环现由知识点队列的 KnowledgeNodeScorer 承载，见
    // KnowledgeReviewQueueTest.cyclesDifficultyBandsWhenScoresTie），记录在版本台账 §3.12。

    @Test
    fun `overdue risk and weak knowledge are explained`() {
        val snapshot = snapshot().copy(
            problemMemoryStates = mapOf(
                "unit-a" to memoryState(
                    unitId = "unit-a",
                    stabilityDays = 1.0,
                    difficulty = 5.5,
                    nextReviewAtEpochMillis = now + 2 * DAY_MILLIS,
                ).copy(
                    lastReviewedAtEpochMillis = now - 5 * DAY_MILLIS,
                    nextReviewAtEpochMillis = now - 2 * DAY_MILLIS,
                ),
            ),
        )

        val plan = planner.plan(
            request(listOf(candidate("unit-a", "family-a", null, 5.5, 60)), 60, snapshot),
        )

        val reasons = plan.queueItems.single().reasons
        assertTrue(ReviewReason.DUE_RECALL_RISK in reasons)
        assertTrue(ReviewReason.WEAK_KNOWLEDGE in reasons)
    }

    @Test
    fun `a repeatedly captured mistake is prioritized and changes plan identity`() {
        val snapshot = snapshot().copy(
            problemMemoryStates = mapOf(
                "unit-a" to memoryState("unit-a", 1.0, 5.5, now - DAY_MILLIS),
                "unit-z" to memoryState("unit-z", 1.0, 5.5, now - DAY_MILLIS),
            ),
        )
        val baseCandidates = listOf(
            candidate("unit-a", "family-a", "source-a", 5.5, 60),
            candidate("unit-z", "family-z", "source-z", 5.5, 60),
        )
        val repeatedCandidates = baseCandidates.map { item ->
            if (item.practiceUnitId == "unit-z") {
                item.copy(repeatMistakePriority = 0.5)
            } else {
                item
            }
        }

        val base = planner.plan(request(baseCandidates, 120, snapshot))
        val repeated = planner.plan(request(repeatedCandidates, 120, snapshot))

        assertEquals("unit-a", base.queueItems.first().practiceUnitId)
        assertEquals("unit-z", repeated.queueItems.first().practiceUnitId)
        assertTrue(ReviewReason.REPEATED_MISTAKE in repeated.queueItems.first().reasons)
        assertTrue(base.planFingerprint != repeated.planFingerprint)
    }

    @Test
    fun `an older unscheduled mistake outranks a newly arrived equal candidate`() {
        val old = candidate("unit-z", "family-z", "source-z", 5.5, 60)
            .copy(eligibleSinceEpochMillis = 0)
        val recent = candidate("unit-a", "family-a", "source-a", 5.5, 60)
            .copy(eligibleSinceEpochMillis = now - DAY_MILLIS)

        val plan = planner.plan(request(listOf(recent, old), 60))

        assertEquals("unit-z", plan.queueItems.single().practiceUnitId)
        assertTrue(ReviewReason.LONG_WAITING in plan.queueItems.single().reasons)
    }

    @Test
    fun `missing and conflicted knowledge are scored as conservative calibration risk`() {
        val conflicted = snapshot().knowledgeMasteryStates.getValue("kc-a").copy(
            status = MasteryStatus.CONFLICTED,
            conflictSinceSequence = 3,
        )
        val snapshot = snapshot().copy(
            knowledgeMasteryStates = mapOf("kc-a" to conflicted),
        )
        val candidate = candidate("unit-a", "family-a", null, 5.5, 60)
            .copy(knowledgeNodeIds = setOf("kc-a", "kc-missing"))

        val plan = planner.plan(request(listOf(candidate), 60, snapshot))

        val reasons = plan.queueItems.single().reasons
        assertTrue(ReviewReason.MISSING_KNOWLEDGE_EVIDENCE in reasons)
        assertTrue(ReviewReason.CONFLICTED_KNOWLEDGE in reasons)
        assertTrue(ReviewReason.CALIBRATION_CHECK in reasons)
    }

    @Test
    fun `clock rollback is exposed as conservative review risk`() {
        val futureMemory = memoryState(
            unitId = "unit-a",
            stabilityDays = 1.0,
            difficulty = 5.5,
            nextReviewAtEpochMillis = now + 2 * DAY_MILLIS,
        ).copy(lastReviewedAtEpochMillis = now + DAY_MILLIS)

        val plan = planner.plan(
            request(
                listOf(candidate("unit-a", "family-a", null, 5.5, 60)),
                60,
                snapshot().copy(problemMemoryStates = mapOf("unit-a" to futureMemory)),
            ),
        )

        assertTrue(ReviewReason.CLOCK_ANOMALY in plan.queueItems.single().reasons)
    }

    @Test
    fun `expired calibration is conservative even when the mastery estimate is high`() {
        val expiredCalibration = CalibrationSnapshot(
            CalibrationSupport.SUPPORTED,
            "calibration-source",
            "calibration-v1",
            0,
            now - 1,
        )
        val highButExpired = snapshot().knowledgeMasteryStates.getValue("kc-a").copy(
            masteryScore = 0.99,
            conservativeMasteryScore = 0.98,
            lastEvidenceAtEpochMillis = now - 1,
            independentCorrectObservations = listOf(
                IndependentCorrectObservation(
                    "family-a",
                    9,
                    now - 1,
                    evidenceWeight = 1.0,
                    calibration = expiredCalibration,
                ),
            ),
        )
        val snapshot = snapshot().copy(
            knowledgeMasteryStates = mapOf("kc-a" to highButExpired),
        )

        val plan = planner.plan(request(listOf(candidate("unit-a", "family-a", null, 5.5, 60)), 60, snapshot))

        assertTrue(ReviewReason.CALIBRATION_CHECK in plan.queueItems.single().reasons)
        assertTrue(ReviewReason.WEAK_KNOWLEDGE in plan.queueItems.single().reasons)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a stale snapshot is refused instead of mixing checkpoints`() {
        planner.plan(
            request(
                candidates = listOf(candidate("unit-a", "family-a", null, 5.5, 60)),
                budget = 60,
                learnerSnapshot = snapshot().copy(freshness = LearnerSnapshotFreshness.STALE),
            ),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a planning time before the projected snapshot is refused`() {
        ReviewPlanningRequest(
            learnerSnapshot = snapshot(),
            candidates = listOf(candidate("unit-a", "family-a", null, 5.5, 60)),
            localDayEpochDay = 10,
            timeZoneId = "Asia/Shanghai",
            timeBudgetSeconds = 60,
            planningAtEpochMillis = now - 1,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a planning time before the correction watermark is refused`() {
        ReviewPlanningRequest(
            learnerSnapshot = snapshot().copy(correctionWatermarkEpochMillis = now + 1),
            candidates = listOf(candidate("unit-a", "family-a", null, 5.5, 60)),
            localDayEpochDay = 10,
            timeZoneId = "Asia/Shanghai",
            timeBudgetSeconds = 60,
            planningAtEpochMillis = now,
        )
    }

    @Test
    fun `five thousand item backlog rotates without exceeding the daily budget`() {
        val simulationStart = 6_000 * DAY_MILLIS
        val calibration = CalibrationSnapshot(
            CalibrationSupport.SUPPORTED,
            "backlog-simulation",
            "calibration-v1",
            0,
            simulationStart + 30 * DAY_MILLIS,
        )
        val currentWeakMastery = snapshot().knowledgeMasteryStates.getValue("kc-a").copy(
            lastEvidenceAtEpochMillis = simulationStart,
            independentCorrectObservations = listOf(
                IndependentCorrectObservation(
                    "backlog-family",
                    1,
                    simulationStart,
                    evidenceWeight = 1.0,
                    calibration = calibration,
                ),
            ),
        )
        val candidates = (0 until 5_000).map { index ->
            candidate(
                unitId = "unit-${index.toString().padStart(4, '0')}",
                familyId = "family-$index",
                sourceId = "source-$index",
                difficulty = 5.5,
                seconds = 60,
            ).copy(
                eligibleSinceEpochMillis =
                    simulationStart - (5_000L - index) * DAY_MILLIS,
            )
        }
        val memories = linkedMapOf<String, ProblemMemoryState>()
        val seen = linkedSetOf<String>()

        repeat(10) { day ->
            val planningAt = simulationStart + day * DAY_MILLIS
            val baseSnapshot = snapshot().copy(
                problemMemoryStates = memories.toMap(),
                knowledgeMasteryStates = mapOf("kc-a" to currentWeakMastery),
            )
            val plan = planner.plan(
                ReviewPlanningRequest(
                    learnerSnapshot = baseSnapshot,
                    candidates = candidates,
                    localDayEpochDay = 6_000L + day,
                    timeZoneId = "Asia/Shanghai",
                    timeBudgetSeconds = 900,
                    planningAtEpochMillis = planningAt,
                ),
            )
            val selectedIds = plan.queueItems.map { it.practiceUnitId }

            assertEquals(900, plan.totalEstimatedDurationSeconds)
            assertEquals(15, selectedIds.size)
            assertTrue(selectedIds.none(seen::contains))
            seen += selectedIds
            selectedIds.forEach { practiceUnitId ->
                memories[practiceUnitId] = memoryState(
                    unitId = practiceUnitId,
                    stabilityDays = 30.0,
                    difficulty = 5.5,
                    nextReviewAtEpochMillis = planningAt + 30 * DAY_MILLIS,
                ).copy(lastReviewedAtEpochMillis = planningAt)
            }
        }

        assertEquals(150, seen.size)
    }

    private fun snapshotWith(vararg states: KnowledgeMasteryState) = LearnerSnapshot(
        learnerId = "learner-1",
        problemMemoryStates = emptyMap(),
        knowledgeMasteryStates = states.associateBy(KnowledgeMasteryState::knowledgeNodeId),
        checkpoint = ProjectionCheckpoint(4, LearningProjector.VERSION, now),
        generatedAtEpochMillis = now,
    )

    private fun masteryState(
        id: String,
        mastery: Double,
        conservative: Double,
    ) = KnowledgeMasteryState(
        knowledgeNodeId = id,
        masteryScore = mastery,
        conservativeMasteryScore = conservative,
        evidenceMass = 0.0,
        status = MasteryStatus.LEARNING,
        calibrationSupport = CalibrationSupport.SUPPORTED,
        projectorVersion = LearningProjector.VERSION,
        checkpointSequence = 4,
    )

    /**
     * A snapshot whose KC carries recent, calibration-supported evidence, so no
     * integrity reason (CALIBRATION_CHECK / STALE_KNOWLEDGE) bypasses the early
     * review gate. [snapshot] leaves those reasons active, which is fine for the
     * other tests but not for early-review assertions.
     */
    private fun supportedSnapshot(): LearnerSnapshot {
        val state = KnowledgeMasteryState(
            knowledgeNodeId = "kc-a",
            masteryScore = 0.95,
            conservativeMasteryScore = 0.92,
            evidenceMass = 4.0,
            status = MasteryStatus.LEARNING,
            calibrationSupport = CalibrationSupport.SUPPORTED,
            projectorVersion = LearningProjector.VERSION,
            checkpointSequence = 4,
            lastEvidenceAtEpochMillis = now,
            independentCorrectObservations = listOf(
                IndependentCorrectObservation(
                    itemFamilyId = "family-ok",
                    studyDayEpochDay = now / DAY_MILLIS,
                    occurredAtEpochMillis = now - DAY_MILLIS,
                    eventSequence = 1,
                    bindingId = "binding-ok",
                    evidenceWeight = 1.0,
                    calibration = CalibrationSnapshot(
                        CalibrationSupport.SUPPORTED,
                        "source",
                        "v1",
                        now - DAY_MILLIS,
                        now + DAY_MILLIS,
                    ),
                ),
            ),
        )
        return LearnerSnapshot(
            learnerId = "learner-1",
            problemMemoryStates = emptyMap(),
            knowledgeMasteryStates = mapOf("kc-a" to state),
            checkpoint = ProjectionCheckpoint(4, LearningProjector.VERSION, now),
            generatedAtEpochMillis = now,
        )
    }

    private fun memoryState(
        unitId: String,
        stabilityDays: Double,
        difficulty: Double,
        nextReviewAtEpochMillis: Long,
        lapseCount: Int = 0,
        consecutiveCrossDayAgain: Int = 0,
    ) = ProblemMemoryState(
        practiceUnitId = unitId,
        stabilityDays = stabilityDays,
        difficulty = difficulty,
        lastReviewedAtEpochMillis = now - DAY_MILLIS,
        nextReviewAtEpochMillis = nextReviewAtEpochMillis,
        lapseCount = lapseCount,
        consecutiveCrossDayAgain = consecutiveCrossDayAgain,
        projectorVersion = LearningProjector.VERSION,
        checkpointSequence = 4,
    )

    private fun masterySnapshot(
        kcId: String,
        status: MasteryStatus,
        mastery: Double,
        conservative: Double,
    ): LearnerSnapshot {
        val masteryState = KnowledgeMasteryState(
            knowledgeNodeId = kcId,
            masteryScore = mastery,
            conservativeMasteryScore = conservative,
            evidenceMass = 2.0,
            status = status,
            calibrationSupport = CalibrationSupport.SUPPORTED,
            projectorVersion = LearningProjector.VERSION,
            checkpointSequence = 4,
        )
        return LearnerSnapshot(
            learnerId = "learner-1",
            problemMemoryStates = emptyMap(),
            knowledgeMasteryStates = mapOf(kcId to masteryState),
            checkpoint = ProjectionCheckpoint(4, LearningProjector.VERSION, now),
            generatedAtEpochMillis = now,
        )
    }

    private fun request(
        candidates: List<ReviewCandidate>,
        budget: Int,
        learnerSnapshot: LearnerSnapshot = snapshot(),
        knowledgePrerequisites: Map<String, Set<String>> = emptyMap(),
    ) = ReviewPlanningRequest(
        learnerSnapshot = learnerSnapshot,
        candidates = candidates,
        localDayEpochDay = 10,
        timeZoneId = "Asia/Shanghai",
        timeBudgetSeconds = budget,
        planningAtEpochMillis = now,
        knowledgePrerequisites = knowledgePrerequisites,
    )

    private fun snapshot(): LearnerSnapshot {
        val mastery = KnowledgeMasteryState(
            knowledgeNodeId = "kc-a",
            masteryScore = 0.4,
            conservativeMasteryScore = 0.2,
            evidenceMass = 2.0,
            status = MasteryStatus.LEARNING,
            calibrationSupport = CalibrationSupport.SUPPORTED,
            projectorVersion = LearningProjector.VERSION,
            checkpointSequence = 4,
        )
        return LearnerSnapshot(
            learnerId = "learner-1",
            problemMemoryStates = emptyMap(),
            knowledgeMasteryStates = mapOf("kc-a" to mastery),
            checkpoint = ProjectionCheckpoint(4, LearningProjector.VERSION, now),
            generatedAtEpochMillis = now,
        )
    }

    private fun candidate(
        unitId: String,
        familyId: String,
        sourceId: String?,
        difficulty: Double,
        seconds: Int,
        subjectId: String? = null,
        kc: String = "kc-a",
    ) = ReviewCandidate(
        practiceUnitId = unitId,
        knowledgeNodeIds = setOf(kc),
        itemFamilyId = familyId,
        sourceBundleId = sourceId,
        subjectId = subjectId,
        difficulty = difficulty,
        estimatedDurationSeconds = seconds,
    )

    private companion object {
        const val DAY_MILLIS = AlgorithmConstants.DAY_MILLIS
        val now = 10 * DAY_MILLIS
    }
}
