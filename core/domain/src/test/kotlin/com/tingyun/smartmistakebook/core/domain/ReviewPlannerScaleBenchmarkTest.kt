package com.tingyun.smartmistakebook.core.domain

import com.tingyun.smartmistakebook.core.model.CalibrationSnapshot
import com.tingyun.smartmistakebook.core.model.CalibrationSupport
import com.tingyun.smartmistakebook.core.model.IndependentCorrectObservation
import com.tingyun.smartmistakebook.core.model.KnowledgeMasteryState
import com.tingyun.smartmistakebook.core.model.LearnerSnapshot
import com.tingyun.smartmistakebook.core.model.MasteryStatus
import com.tingyun.smartmistakebook.core.model.ProblemMemoryState
import com.tingyun.smartmistakebook.core.model.ProjectionCheckpoint
import com.tingyun.smartmistakebook.core.model.ReviewReason
import java.util.Random
import kotlin.system.measureTimeMillis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * W4-1 排程放大（S12–S16）的**规模基准**与**等价性金样**。
 *
 * 1. `five thousand candidate plan completes under one second`：确定性种子生成的
 *    5000 候选夹具，预热 3 次后 best-of-5 计时，断言 <1000ms（捕捉 O(N²) 级爆炸，
 *    不是微基准标定值；roadmap W4-1 的终门口径）。
 * 2. `golden selection sequences stay bit identical`：三组固定夹具（约 60/500/5000
 *    候选）的选卡序列（practiceUnitId 顺序 + 每项 reasons 集合 + 优先级分值 hex）。
 *    金样在 S12–S16 重构**之前**捕获并硬编码；重构后必须逐位一致——这是本批
 *    "零 bump"（逐位等价）主张的载体。分值以 hex 形式一并钉住：它进入计划指纹，
 *    "选序不变"不足以支撑"指纹逐位不变"。
 *
 * 金样**不含任何版本串**：`VERSION` / 指纹 schema 的 bump 不会破坏它；能破坏它的
 * 只有选卡语义本身的变化。
 *
 * 实测（W4-1 开发机，单类隔离运行）：改前 best-of-5 = 1054ms（单次 1085ms）；
 * S12–S16 后 best-of-5 = 168ms。注意 `org.gradle.parallel=true`：在完整套件里与
 * 其他模块的任务并发时，同一用例实测 631ms（仍 <1000ms 门）——门是数量级的宽松门，
 * 不是微基准标定值。
 *
 * 夹具形状沿用 [ReviewPlannerV2Test] 的 `candidate()` / `request()` 口径：
 * 空 [LogDurationModel] 下每道题的建模时长都是全局先验 60s，预算算术可精确复算。
 */
class ReviewPlannerScaleBenchmarkTest {
    private val planner = ReviewPlannerV2()

    @Test
    fun `five thousand candidate plan completes under one second`() {
        val request = fixtureRequest(candidateCount = 5_000, seed = FIXTURE_SEED_LARGE)

        // 预热 3 次（JIT/类加载不计入门内）。
        repeat(3) { planner.plan(request) }

        var bestMillis = Long.MAX_VALUE
        repeat(5) {
            bestMillis = minOf(bestMillis, measureTimeMillis { planner.plan(request) })
        }

        assertTrue(
            "5000 候选排程 best-of-5 实测 ${bestMillis}ms，超过 1000ms 门（W4-1 终门）",
            bestMillis < 1_000,
        )
    }

    @Test
    fun `golden selection sequences stay bit identical`() {
        assertEquals(
            "60 候选夹具的选卡序列与 S12–S16 重构前金样不一致",
            GOLDEN_SELECTION_SMALL,
            captureSelection(fixtureRequest(candidateCount = 60, seed = FIXTURE_SEED_SMALL)),
        )
        assertEquals(
            "500 候选夹具的选卡序列与 S12–S16 重构前金样不一致",
            GOLDEN_SELECTION_MEDIUM,
            captureSelection(fixtureRequest(candidateCount = 500, seed = FIXTURE_SEED_MEDIUM)),
        )
        assertEquals(
            "5000 候选夹具的选卡序列与 S12–S16 重构前金样不一致",
            GOLDEN_SELECTION_LARGE,
            captureSelection(fixtureRequest(candidateCount = 5_000, seed = FIXTURE_SEED_LARGE)),
        )
    }

    /** 选卡序列：`practiceUnitId[排序后的 reasons]分值hex`，以 `;` 连接。 */
    private fun captureSelection(request: ReviewPlanningRequest): String =
        planner.plan(request).queueItems.joinToString(";") { item ->
            val reasons = item.reasons.map(ReviewReason::name).sorted().joinToString(",")
            val score = java.lang.Double.toHexString(item.priorityScore)
            "${item.practiceUnitId}[$reasons]$score"
        }

    /**
     * 确定性种子夹具（固定 seed + 固定遍历顺序 ⇒ 每次运行逐位相同）。
     *
     * 覆盖 S12–S16 的每条路径：
     * - 先修图（kc-2k 依赖 kc-2k-1）：共享先修对（[ReviewPlannerV2.computeConfusablePartners]）
     *   与 KF-08 硬过滤（少数先修保守分低于 τ_ready）；
     * - 观察列表 0..4 条、校准窗口部分已过期：`CalibrationSupport` 现算与
     *   `MasterySmoothing` 平滑路径；
     * - 到期/未到期/新题三态 + examPriority/repeatMistake/leech/avoidance 信号：
     *   早复习闸门、anti-oscillation 配额、beam/greedy/swap 各分支；
     * - 全部候选建模后为 60s（空时长模型 → 全局先验），预算 1800s ⇒ 选卡 ≤ 20（beam 步数上限）。
     */
    private fun fixtureRequest(
        candidateCount: Int,
        seed: Long,
        budgetSeconds: Int = 1_800,
    ): ReviewPlanningRequest {
        val random = Random(seed)
        val dayMillis = AlgorithmConstants.DAY_MILLIS
        val kcCount = maxOf(8, candidateCount / 12)
        val familyCount = maxOf(6, candidateCount / 25)
        val sourceCount = maxOf(4, candidateCount / 40)
        val subjectCount = maxOf(3, candidateCount / 100)

        // 先修图：一半 KC 是另一半的依赖方 —— 共享先修对（S14）与 KF-08 门都由此产生。
        val prerequisites = (1..kcCount / 2).associate { half ->
            "kc-${2 * half}" to setOf("kc-${2 * half - 1}")
        }

        val masteryStates = linkedMapOf<String, KnowledgeMasteryState>()
        for (kcIndex in 0 until kcCount) {
            val kcId = "kc-$kcIndex"
            // 少数 KC 的保守分低于 τ_ready(0.6)：让缺前置的候选真实出现（硬过滤路径）。
            val conservative = if (kcIndex % 7 == 3) {
                random.nextDouble(0.05, 0.55)
            } else {
                random.nextDouble(0.6, 0.95)
            }
            val mastery = (conservative + random.nextDouble(0.0, 0.05)).coerceAtMost(1.0)
            val observations = (0 until random.nextInt(5)).map { obsIndex ->
                val occurredAt = NOW - random.nextLong(0L, 60L * dayMillis)
                val windowStillOpen = random.nextBoolean()
                IndependentCorrectObservation(
                    itemFamilyId = "obs-family-$kcIndex-$obsIndex",
                    studyDayEpochDay = occurredAt / dayMillis,
                    occurredAtEpochMillis = occurredAt,
                    eventSequence = (obsIndex + 1).toLong(),
                    bindingId = "binding-$kcIndex-$obsIndex",
                    evidenceWeight = 0.5 + random.nextDouble(0.0, 0.5),
                    calibration = CalibrationSnapshot(
                        support = CalibrationSupport.SUPPORTED,
                        sourceId = "calibration-source",
                        version = "calibration-v1",
                        validFromEpochMillis = 0L,
                        // 窗口已过期时 supportAt(now) 返回 UNKNOWN：未支持观察路径。
                        validUntilEpochMillis = if (windowStillOpen) {
                            NOW + dayMillis
                        } else {
                            NOW - dayMillis
                        },
                    ),
                )
            }
            val lastAttempt = NOW - random.nextLong(0L, 70L * dayMillis)
            masteryStates[kcId] = KnowledgeMasteryState(
                knowledgeNodeId = kcId,
                masteryScore = mastery,
                conservativeMasteryScore = conservative,
                evidenceMass = random.nextDouble(0.0, 5.0),
                memoryStabilityDays = if (random.nextInt(4) == 0) null else random.nextDouble(2.0, 40.0),
                memoryDifficulty = if (random.nextInt(4) == 0) null else random.nextDouble(1.0, 10.0),
                lastAttemptAtEpochMillis = lastAttempt,
                lastAttemptStudyDayEpochDay = lastAttempt / dayMillis,
                independentCorrectObservations = observations,
                status = if (kcIndex % 11 == 5) MasteryStatus.CONFLICTED else MasteryStatus.LEARNING,
                calibrationSupport = CalibrationSupport.SUPPORTED,
                projectorVersion = LearningProjector.VERSION,
                checkpointSequence = 4,
            )
        }

        val candidates = (0 until candidateCount).map { index ->
            val unitId = "unit-$index"
            val primaryKc = random.nextInt(kcCount)
            val kcs = linkedSetOf("kc-$primaryKc")
            // 1/5 的题绑定第二个 KC：制造更多"一题跨两 KC"的共享先修对。
            if (random.nextInt(5) == 0) {
                kcs += "kc-${(primaryKc + kcCount / 2) % kcCount}"
            }
            ReviewCandidate(
                practiceUnitId = unitId,
                knowledgeNodeIds = kcs,
                itemFamilyId = "family-${random.nextInt(familyCount)}",
                sourceBundleId = if (random.nextInt(6) == 0) {
                    null
                } else {
                    "source-${random.nextInt(sourceCount)}"
                },
                subjectId = "subject-${random.nextInt(subjectCount)}",
                difficulty = 1.0 + random.nextDouble(0.0, 9.0),
                // 空时长模型下会被替换为全局先验 60s；此处值只为形状一致。
                estimatedDurationSeconds = 60,
                examPriority = if (random.nextInt(5) == 0) 1.0 else 0.0,
                repeatMistakePriority = if (random.nextInt(4) == 0) {
                    random.nextDouble(0.25, 1.0)
                } else {
                    0.0
                },
                eligibleSinceEpochMillis = NOW - random.nextLong(0L, 40L * dayMillis),
                leech = random.nextInt(25) == 0,
                avoidance = random.nextInt(12) == 0,
            )
        }

        val memoryStates = linkedMapOf<String, ProblemMemoryState>()
        candidates.forEach { candidate ->
            val roll = random.nextInt(100)
            if (roll < 15) {
                // 新题：无记忆状态（NEWLY_ADDED）。
                return@forEach
            }
            val stability = random.nextDouble(1.0, 30.0)
            val gap = random.nextLong(dayMillis, 30L * dayMillis)
            val nextReview = if (roll < 80) {
                // 到期：nextReview 在过去 0..20 天之间（到期/逾期风险），
                // 上次复习再往前 1..30 天（nextReview ≥ lastReviewed 恒成立）。
                NOW - random.nextLong(0L, 20L * dayMillis)
            } else {
                // 未到期：早复习闸门只在 CLOCK_ANOMALY/CALIBRATION_CHECK/
                // REPEATED_MISTAKE/KC_MASTERY_DROP 或考试窗口下放行。
                NOW + random.nextLong(dayMillis, 30L * dayMillis)
            }
            val lastReviewed = if (roll < 80) nextReview - gap else NOW - gap
            val leeched = candidate.leech
            memoryStates[candidate.practiceUnitId] = ProblemMemoryState(
                practiceUnitId = candidate.practiceUnitId,
                stabilityDays = stability,
                difficulty = candidate.difficulty,
                lastReviewedAtEpochMillis = lastReviewed,
                nextReviewAtEpochMillis = nextReview,
                lapseCount = if (leeched) 7 else random.nextInt(4),
                consecutiveCrossDayAgain = if (leeched) 2 else 0,
                projectorVersion = LearningProjector.VERSION,
                checkpointSequence = 4,
            )
        }

        val snapshot = LearnerSnapshot(
            learnerId = "learner-scale",
            problemMemoryStates = memoryStates,
            knowledgeMasteryStates = masteryStates,
            checkpoint = ProjectionCheckpoint(4, LearningProjector.VERSION, NOW),
            generatedAtEpochMillis = NOW,
        )
        return ReviewPlanningRequest(
            learnerSnapshot = snapshot,
            candidates = candidates,
            localDayEpochDay = 10,
            timeZoneId = "Asia/Shanghai",
            timeBudgetSeconds = budgetSeconds,
            planningAtEpochMillis = NOW,
            knowledgePrerequisites = prerequisites,
        )
    }

    private companion object {
        /**
         * 时间原点：第 120 个学习日（偏移取大，60 天前的观察/30 天前的复习都还是正时间戳）。
         */
        const val NOW = 120L * AlgorithmConstants.DAY_MILLIS

        const val FIXTURE_SEED_SMALL = 11L
        const val FIXTURE_SEED_MEDIUM = 29L
        const val FIXTURE_SEED_LARGE = 20261001L

        /**
         * S12–S16 重构前捕获的金样（夹具：60 候选）。格式：
         * `unitId[REASON1,REASON2,...]<scoreHex>`，`;` 连接，顺序即选卡顺序。
         */
        const val GOLDEN_SELECTION_SMALL =
            "unit-5[AVOIDANCE_SIGNAL,CALIBRATION_CHECK,CONFUSABLE_PAIR,DUE_RECALL_RISK,LONG_WAITING,REPEATED_MISTAKE,STALE_KNOWLEDGE,WEAK_KNOWLEDGE]0x1.0a0cd434cf84p3;" +
                "unit-48[CALIBRATION_CHECK,CONFLICTED_KNOWLEDGE,CONFUSABLE_PAIR,DUE_RECALL_RISK,LONG_WAITING,WEAK_KNOWLEDGE]0x1.27cc0c599589cp3;" +
                "unit-34[CALIBRATION_CHECK,CONFUSABLE_PAIR,DUE_RECALL_RISK,STALE_KNOWLEDGE,WEAK_KNOWLEDGE]0x1.279b71cdb92b2p3;" +
                "unit-42[CALIBRATION_CHECK,CONFLICTED_KNOWLEDGE,CONFUSABLE_PAIR,DUE_RECALL_RISK,LONG_WAITING,WEAK_KNOWLEDGE]0x1.10bfe68c8b44ap3;" +
                "unit-2[CALIBRATION_CHECK,CONFUSABLE_PAIR,DUE_RECALL_RISK,LONG_WAITING,STALE_KNOWLEDGE,WEAK_KNOWLEDGE]0x1.00628c95c390cp3;" +
                "unit-43[CALIBRATION_CHECK,CONFUSABLE_PAIR,DUE_RECALL_RISK,EXAM_PRIORITY,STALE_KNOWLEDGE,WEAK_KNOWLEDGE]0x1.003cf34c8dd57p3;" +
                "unit-23[CALIBRATION_CHECK,CONFUSABLE_PAIR,DUE_RECALL_RISK,LONG_WAITING,STALE_KNOWLEDGE,WEAK_KNOWLEDGE]0x1.ed94a407919cep2;" +
                "unit-44[CALIBRATION_CHECK,CONFUSABLE_PAIR,DUE_RECALL_RISK,EXAM_PRIORITY,LONG_WAITING,STALE_KNOWLEDGE,WEAK_KNOWLEDGE]0x1.e7bf6ee1011d6p2;" +
                "unit-1[CALIBRATION_CHECK,CONFUSABLE_PAIR,DUE_RECALL_RISK,LONG_WAITING,STALE_KNOWLEDGE,WEAK_KNOWLEDGE]0x1.d665c6b0a7c3ap2;" +
                "unit-25[DUE_RECALL_RISK,EXAM_PRIORITY,LONG_WAITING,WEAK_KNOWLEDGE]0x1.c56e71e49e275p2;" +
                "unit-4[CALIBRATION_CHECK,CONFUSABLE_PAIR,DUE_RECALL_RISK,LONG_WAITING,STALE_KNOWLEDGE,WEAK_KNOWLEDGE]0x1.c37e570f1a18bp2;" +
                "unit-21[CALIBRATION_CHECK,CONFUSABLE_PAIR,DUE_RECALL_RISK,LONG_WAITING,STALE_KNOWLEDGE,WEAK_KNOWLEDGE]0x1.bc1651a08a4d9p2;" +
                "unit-18[DUE_RECALL_RISK,LONG_WAITING,REPEATED_MISTAKE,WEAK_KNOWLEDGE]0x1.9511b8c06ae94p2;" +
                "unit-27[CALIBRATION_CHECK,CONFLICTED_KNOWLEDGE,CONFUSABLE_PAIR,DUE_RECALL_RISK,LONG_WAITING,WEAK_KNOWLEDGE]0x1.4590471db30e4p2;" +
                "unit-33[CALIBRATION_CHECK,CONFUSABLE_PAIR,DUE_RECALL_RISK,LONG_WAITING,STALE_KNOWLEDGE,WEAK_KNOWLEDGE]0x1.527f03b05f8f6p2;" +
                "unit-13[CALIBRATION_CHECK,CONFUSABLE_PAIR,LONG_WAITING,NEWLY_ADDED,STALE_KNOWLEDGE,WEAK_KNOWLEDGE]0x1.09db63617c5c9p2;" +
                "unit-30[CALIBRATION_CHECK,CONFUSABLE_PAIR,DUE_RECALL_RISK,LONG_WAITING,REPEATED_MISTAKE,STALE_KNOWLEDGE,WEAK_KNOWLEDGE]0x1.40952195a06fep2;" +
                "unit-8[DUE_RECALL_RISK,LONG_WAITING,WEAK_KNOWLEDGE]0x1.21bb641fc0597p2;" +
                "unit-7[CALIBRATION_CHECK,CONFUSABLE_PAIR,LONG_WAITING,STALE_KNOWLEDGE,WEAK_KNOWLEDGE]0x1.cad032055f0c8p1;" +
                "unit-16[CALIBRATION_CHECK,DUE_RECALL_RISK,LONG_WAITING,WEAK_KNOWLEDGE]0x1.9892423ac4601p2"

        /** S12–S16 重构前捕获的金样（夹具：500 候选）。 */
        const val GOLDEN_SELECTION_MEDIUM =
            "unit-16[CALIBRATION_CHECK,CONFUSABLE_PAIR,DUE_RECALL_RISK,EXAM_PRIORITY,LONG_WAITING,REPEATED_MISTAKE,STALE_KNOWLEDGE,WEAK_KNOWLEDGE]0x1.62cd04073189dp3;" +
                "unit-331[CALIBRATION_CHECK,CONFUSABLE_PAIR,DUE_RECALL_RISK,EXAM_PRIORITY,REPEATED_MISTAKE,STALE_KNOWLEDGE,WEAK_KNOWLEDGE]0x1.7a19cd9829c09p3;" +
                "unit-294[AVOIDANCE_SIGNAL,CALIBRATION_CHECK,CONFUSABLE_PAIR,DUE_RECALL_RISK,EXAM_PRIORITY,LONG_WAITING,STALE_KNOWLEDGE,WEAK_KNOWLEDGE]0x1.6290b5e34635cp3;" +
                "unit-78[CALIBRATION_CHECK,CONFUSABLE_PAIR,DUE_RECALL_RISK,EXAM_PRIORITY,LONG_WAITING,REPEATED_MISTAKE,STALE_KNOWLEDGE,WEAK_KNOWLEDGE]0x1.55ef778e6bb87p3;" +
                "unit-475[AVOIDANCE_SIGNAL,CALIBRATION_CHECK,CONFUSABLE_PAIR,DUE_RECALL_RISK,LONG_WAITING,STALE_KNOWLEDGE,WEAK_KNOWLEDGE]0x1.47a5f33e4ffd9p3;" +
                "unit-460[AVOIDANCE_SIGNAL,CALIBRATION_CHECK,DUE_RECALL_RISK,LONG_WAITING,REPEATED_MISTAKE,STALE_KNOWLEDGE,WEAK_KNOWLEDGE]0x1.477965905cdb2p3;" +
                "unit-464[CALIBRATION_CHECK,CONFUSABLE_PAIR,DUE_RECALL_RISK,EXAM_PRIORITY,LONG_WAITING,STALE_KNOWLEDGE,WEAK_KNOWLEDGE]0x1.4019227b996b9p3;" +
                "unit-282[CALIBRATION_CHECK,CONFUSABLE_PAIR,DUE_RECALL_RISK,EXAM_PRIORITY,LONG_WAITING,WEAK_KNOWLEDGE]0x1.3760e1499bc4ep3;" +
                "unit-353[AVOIDANCE_SIGNAL,CALIBRATION_CHECK,CONFUSABLE_PAIR,DUE_RECALL_RISK,LONG_WAITING,STALE_KNOWLEDGE,WEAK_KNOWLEDGE]0x1.2dd15c80c591dp3;" +
                "unit-155[DUE_RECALL_RISK,EXAM_PRIORITY,LONG_WAITING,REPEATED_MISTAKE,WEAK_KNOWLEDGE]0x1.2d65c1d5326e5p3;" +
                "unit-103[AVOIDANCE_SIGNAL,DUE_RECALL_RISK,LONG_WAITING,REPEATED_MISTAKE,WEAK_KNOWLEDGE]0x1.10e716b8b6948p3;" +
                "unit-236[AVOIDANCE_SIGNAL,CALIBRATION_CHECK,CONFUSABLE_PAIR,DUE_RECALL_RISK,LONG_WAITING,REPEATED_MISTAKE,STALE_KNOWLEDGE,WEAK_KNOWLEDGE]0x1.2338487259528p3;" +
                "unit-17[CONFUSABLE_PAIR,DUE_RECALL_RISK,EXAM_PRIORITY,LONG_WAITING,REPEATED_MISTAKE,WEAK_KNOWLEDGE]0x1.34feb63e95e54p3;" +
                "unit-360[CALIBRATION_CHECK,CONFUSABLE_PAIR,DUE_RECALL_RISK,LONG_WAITING,STALE_KNOWLEDGE,WEAK_KNOWLEDGE]0x1.224107ba7f124p3;" +
                "unit-238[CALIBRATION_CHECK,CONFUSABLE_PAIR,DUE_RECALL_RISK,LONG_WAITING,REPEATED_MISTAKE,STALE_KNOWLEDGE,WEAK_KNOWLEDGE]0x1.49e07ed19e981p3;" +
                "unit-184[CALIBRATION_CHECK,DUE_RECALL_RISK,LONG_WAITING,REPEATED_MISTAKE,STALE_KNOWLEDGE,WEAK_KNOWLEDGE]0x1.1dd994e08d1ffp3;" +
                "unit-126[AVOIDANCE_SIGNAL,CALIBRATION_CHECK,CONFUSABLE_PAIR,DUE_RECALL_RISK,LONG_WAITING,STALE_KNOWLEDGE,WEAK_KNOWLEDGE]0x1.faee0be541b52p2;" +
                "unit-412[CALIBRATION_CHECK,CONFUSABLE_PAIR,DUE_RECALL_RISK,REPEATED_MISTAKE,STALE_KNOWLEDGE,WEAK_KNOWLEDGE]0x1.1a66fb49dcaaep3;" +
                "unit-163[CALIBRATION_CHECK,DUE_RECALL_RISK,EXAM_PRIORITY,REPEATED_MISTAKE,WEAK_KNOWLEDGE]0x1.27537eeafaeap3;" +
                "unit-242[CALIBRATION_CHECK,CONFUSABLE_PAIR,DUE_RECALL_RISK,EXAM_PRIORITY,LONG_WAITING,STALE_KNOWLEDGE,WEAK_KNOWLEDGE]0x1.1edf584f6a678p3"

        /** S12–S16 重构前捕获的金样（夹具：5000 候选）。 */
        const val GOLDEN_SELECTION_LARGE =
            "unit-1554[AVOIDANCE_SIGNAL,CALIBRATION_CHECK,CONFUSABLE_PAIR,DUE_RECALL_RISK,EXAM_PRIORITY,LONG_WAITING,REPEATED_MISTAKE,WEAK_KNOWLEDGE]0x1.79407a6cd86bap3;" +
                "unit-2196[CALIBRATION_CHECK,CONFUSABLE_PAIR,DUE_RECALL_RISK,EXAM_PRIORITY,LONG_WAITING,REPEATED_MISTAKE,STALE_KNOWLEDGE,WEAK_KNOWLEDGE]0x1.6c2c2f49a01d1p3;" +
                "unit-2753[AVOIDANCE_SIGNAL,CALIBRATION_CHECK,DUE_RECALL_RISK,EXAM_PRIORITY,LONG_WAITING,REPEATED_MISTAKE,WEAK_KNOWLEDGE]0x1.65633cc4ad9e7p3;" +
                "unit-234[CALIBRATION_CHECK,CONFUSABLE_PAIR,DUE_RECALL_RISK,EXAM_PRIORITY,LONG_WAITING,REPEATED_MISTAKE,WEAK_KNOWLEDGE]0x1.6550e7c3823b5p3;" +
                "unit-4345[CALIBRATION_CHECK,CONFUSABLE_PAIR,DUE_RECALL_RISK,EXAM_PRIORITY,LONG_WAITING,REPEATED_MISTAKE,WEAK_KNOWLEDGE]0x1.651e9e906af4ep3;" +
                "unit-1384[CALIBRATION_CHECK,CONFLICTED_KNOWLEDGE,CONFUSABLE_PAIR,DUE_RECALL_RISK,EXAM_PRIORITY,LONG_WAITING,WEAK_KNOWLEDGE]0x1.669f4135be936p3;" +
                "unit-1410[CALIBRATION_CHECK,CONFUSABLE_PAIR,DUE_RECALL_RISK,EXAM_PRIORITY,LONG_WAITING,REPEATED_MISTAKE,STALE_KNOWLEDGE,WEAK_KNOWLEDGE]0x1.64ff6ba4bb282p3;" +
                "unit-2977[CALIBRATION_CHECK,CONFUSABLE_PAIR,DUE_RECALL_RISK,EXAM_PRIORITY,LONG_WAITING,REPEATED_MISTAKE,STALE_KNOWLEDGE,WEAK_KNOWLEDGE]0x1.604d26d158846p3;" +
                "unit-1259[CONFUSABLE_PAIR,DUE_RECALL_RISK,EXAM_PRIORITY,LONG_WAITING,REPEATED_MISTAKE,WEAK_KNOWLEDGE]0x1.5fe15ea270684p3;" +
                "unit-2364[CALIBRATION_CHECK,CONFUSABLE_PAIR,DUE_RECALL_RISK,EXAM_PRIORITY,LONG_WAITING,REPEATED_MISTAKE,STALE_KNOWLEDGE,WEAK_KNOWLEDGE]0x1.5bb678a061191p3;" +
                "unit-1418[AVOIDANCE_SIGNAL,CALIBRATION_CHECK,CONFUSABLE_PAIR,DUE_RECALL_RISK,EXAM_PRIORITY,LONG_WAITING,WEAK_KNOWLEDGE]0x1.596c56ccf0d2p3;" +
                "unit-4446[CALIBRATION_CHECK,CONFUSABLE_PAIR,DUE_RECALL_RISK,EXAM_PRIORITY,LONG_WAITING,REPEATED_MISTAKE,STALE_KNOWLEDGE,WEAK_KNOWLEDGE]0x1.60fb62a3523eep3;" +
                "unit-1493[CALIBRATION_CHECK,CONFUSABLE_PAIR,DUE_RECALL_RISK,EXAM_PRIORITY,LONG_WAITING,REPEATED_MISTAKE,STALE_KNOWLEDGE,WEAK_KNOWLEDGE]0x1.5402c8e5e4ebcp3;" +
                "unit-4507[CALIBRATION_CHECK,DUE_RECALL_RISK,EXAM_PRIORITY,LONG_WAITING,REPEATED_MISTAKE,STALE_KNOWLEDGE,WEAK_KNOWLEDGE]0x1.535fa366681c9p3;" +
                "unit-886[CALIBRATION_CHECK,CONFLICTED_KNOWLEDGE,CONFUSABLE_PAIR,DUE_RECALL_RISK,EXAM_PRIORITY,LONG_WAITING,REPEATED_MISTAKE,WEAK_KNOWLEDGE]0x1.4f4032212ac32p3;" +
                "unit-2667[CALIBRATION_CHECK,CONFUSABLE_PAIR,DUE_RECALL_RISK,EXAM_PRIORITY,LONG_WAITING,REPEATED_MISTAKE,WEAK_KNOWLEDGE]0x1.4cf96d32b1102p3;" +
                "unit-2842[CALIBRATION_CHECK,CONFUSABLE_PAIR,DUE_RECALL_RISK,EXAM_PRIORITY,LONG_WAITING,WEAK_KNOWLEDGE]0x1.4b1128834b5aap3;" +
                "unit-67[CALIBRATION_CHECK,CONFUSABLE_PAIR,DUE_RECALL_RISK,EXAM_PRIORITY,LONG_WAITING,REPEATED_MISTAKE,WEAK_KNOWLEDGE]0x1.6b6d0b3701449p3;" +
                "unit-2352[CALIBRATION_CHECK,CONFUSABLE_PAIR,DUE_RECALL_RISK,EXAM_PRIORITY,LONG_WAITING,REPEATED_MISTAKE,WEAK_KNOWLEDGE]0x1.6656f8358b4p3;" +
                "unit-3699[AVOIDANCE_SIGNAL,CALIBRATION_CHECK,DUE_RECALL_RISK,EXAM_PRIORITY,LONG_WAITING,STALE_KNOWLEDGE,WEAK_KNOWLEDGE]0x1.47e9afed8e9b3p3"
    }
}
