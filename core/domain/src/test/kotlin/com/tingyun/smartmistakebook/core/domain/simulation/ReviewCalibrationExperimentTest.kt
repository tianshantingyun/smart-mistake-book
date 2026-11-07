package com.tingyun.smartmistakebook.core.domain.simulation

import com.tingyun.smartmistakebook.core.domain.AlgorithmConstants
import com.tingyun.smartmistakebook.core.domain.ClearlyMasteredForSkipPolicy
import com.tingyun.smartmistakebook.core.domain.FsrsScheduleMath
import com.tingyun.smartmistakebook.core.domain.MasteryDecisionPolicy
import com.tingyun.smartmistakebook.core.domain.ReviewSample
import com.tingyun.smartmistakebook.core.domain.ReviewScoringWeights
import com.tingyun.smartmistakebook.core.model.KnowledgeMasteryState
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest
import java.time.Instant
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * 批次 49 · P1-8 标定实验执行器。
 *
 * 逐条实现 `docs/research/2026-11-08-review-calibration-prereg.md`（冻结）：
 * §4 G6 幅度扫描（含 §4.2 等时化）、§5 权重坐标扫描（17 臂）、§6 掌握阈值读数
 * （只测不改）、§8 机检清单、§8.1 运行矩阵、§12 报告字段。
 *
 * **为什么是一个被环境变量门住的测试**：它必须跑在 `core:domain` 的 JVM 测试源集里
 * （与模拟器同模块、能读 `internal` 出口），但不该把 ~1400 次模拟塞进每次 `:core:domain:test`。
 * 门：`BATCH49_CALIBRATION=run`。默认跳过（`assumeTrue`），套件耗时不变。
 *
 * **全部结论是 model-based**：只对 30 KC / 300 题 / 120 天 / 1200s 的模拟世界成立。
 * 真库样本不足处一律 `BLOCKED`，不填 0。
 *
 * 产物目录：`docs/research/evidence/2026-11-08-batch-49/calibration/`。
 */
class ReviewCalibrationExperimentTest {

    @Test
    fun `run the frozen calibration matrix`() {
        assumeTrue(
            "标定实验以 BATCH49_CALIBRATION=run 显式触发（默认跳过以保持 :core:domain:test 耗时不变）",
            System.getenv("BATCH49_CALIBRATION") == "run",
        )

        val startedAt = Instant.now()
        val cache = RunCache()
        val worlds = buildList {
            DEV_SEEDS.take(worldLimit()).forEach { add(WorldSpec("dev", it)) }
            HOLDOUT_SEEDS.take(worldLimit()).forEach { add(WorldSpec("holdout", it)) }
        }

        // ---------- §4 G6：基线 + 4 个 f × 11 个 rho ----------
        val g6 = G6Experiment(cache)
        worlds.forEach { world ->
            println("[batch49] G6 world=${world.layer}/${world.seed}")
            g6.observe(world)
        }
        val g6Decision = g6.decide()

        // ---------- §5 权重坐标：17 臂 ----------
        val weights = WeightExperiment(cache)
        worlds.forEach { world ->
            println("[batch49] weights world=${world.layer}/${world.seed}")
            weights.observe(world)
        }
        val weightDecision = weights.decide()

        // ---------- §6 掌握阈值读数（只测不改）+ §6.3 前缀同一性 ----------
        val thresholds = ThresholdExperiment(cache)
        worlds.forEach { world ->
            println("[batch49] thresholds world=${world.layer}/${world.seed}")
            thresholds.observe(world)
        }

        // ---------- §4.3 CONFIRM（只在有 finalist 时跑）----------
        val confirmation = g6ConfirmIfNeeded(g6Decision, cache)

        // ---------- 同 seed 重跑一致性（§8 机检 4）----------
        val determinism = determinismChecks(cache)

        val finishedAt = Instant.now()
        val manifest = manifest(startedAt, finishedAt, cache, worldLimit())

        // 证据由 tools/ci/run_batch49_calibration.py 从这些带标记的 stdout 行落盘；
        // 测试本身不写文件（避免测试代码持有任意路径写入口）。
        println("[batch49-artifact] run-manifest.json\n$manifest")
        println("[batch49-artifact] g6-runs.csv\n" + g6.csv())
        println("[batch49-artifact] g6-decisions.json\n" + g6.decisionJson(g6Decision))
        println("[batch49-artifact] weight-runs.csv\n" + weights.csv())
        println("[batch49-artifact] weight-decisions.json\n" + weights.decisionJson(weightDecision))
        println("[batch49-artifact] thresholds.csv\n" + thresholds.csv())
        println("[batch49-artifact] threshold-readings.json\n" + thresholds.json())
        println("[batch49-artifact] prefix-identity.json\n" + thresholds.prefixJson())
        println("[batch49-artifact] confirmation.json\n$confirmation")
        println("[batch49-artifact] determinism.json\n$determinism")
        println("[batch49-artifact] holdout-run-id.txt\n" + HOLDOUT_RUN_ID + "\n")

        println("[batch49] physical runs=${cache.physicalRuns} cache hits=${cache.cacheHits}")
    }

    // ==================================================================================
    // §4 G6
    // ==================================================================================

    private inner class G6Experiment(private val cache: RunCache) {
        private val cells = mutableListOf<G6Cell>()
        private val baselines = mutableMapOf<Long, RunView>()
        private val equalTime = mutableMapOf<Pair<Long, Double>, EqualTime>()

        fun observe(world: WorldSpec) {
            val baseline = cache.view(seed = world.seed, horizon = 120, truth = TRUTH_SLIP, f = 0.0)
            baselines[world.seed] = baseline
            G6_GRID.forEach { f ->
                val scan = RHO_GRID.map { rho ->
                    val budget = equalTimeBudget(rho)
                    val view = cache.view(
                        seed = world.seed,
                        horizon = 120,
                        truth = TRUTH_SLIP,
                        f = f,
                        budgetSeconds = budget,
                    )
                    rho to view
                }
                val selection = selectEqualTime(baseline, scan)
                equalTime[world.seed to f] = selection
                if (selection.rho == null) {
                    cells += G6Cell(world, f, null, null, selection, null)
                    return@forEach
                }
                val view = scan.first { it.first == selection.rho }.second
                cells += G6Cell(world, f, view, baseline, selection, guardOf(view, baseline))
            }
        }

        fun csv(): String = buildString {
            append("layer,seed,f,rho,budget_seconds,equal_time_rel_error,y,baseline_y,q,total_answered_seconds,")
            append("baseline_total_answered_seconds,overrun_strict,overrun_tolerance,starved_subjects,")
            append("kc_gap_p95,baseline_kc_gap_p95,brier_delta,guard_ok,max_daily_items,days_at_item_cap,")
            append("brier,ece,log_loss,mastered,reviewed_kc,digest\n")
            cells.forEach { cell ->
                val guard = cell.guard
                append(cell.world.layer).append(',').append(cell.world.seed).append(',')
                append(fmt(cell.f)).append(',')
                append(cell.equalTime.rho?.let { fmt(it) } ?: "").append(',')
                append(cell.equalTime.rho?.let { equalTimeBudget(it) } ?: "").append(',')
                append(cell.equalTime.relativeError?.let { fmt(it) } ?: "").append(',')
                append(cell.view?.y?.let { fmt(it) } ?: "").append(',')
                append(cell.baseline?.y?.let { fmt(it) } ?: "").append(',')
                append(cell.view?.q ?: "").append(',')
                append(cell.view?.totalAnswered ?: "").append(',')
                append(cell.baseline?.totalAnswered ?: "").append(',')
                append(cell.view?.overrunStrict ?: "").append(',')
                append(cell.view?.overrunTolerance ?: "").append(',')
                append(guard?.starvedSubjects?.joinToString(";") ?: "").append(',')
                append(cell.view?.kcGapP95?.let { fmt(it) } ?: "").append(',')
                append(cell.baseline?.kcGapP95?.let { fmt(it) } ?: "").append(',')
                append(guard?.brierDelta?.let { fmt(it) } ?: "").append(',')
                append(guard != null && guard.strictOverruns == 0 && guard.toleranceOverruns == 0 &&
                    guard.starvedSubjects.isEmpty() && guard.kcGapOk).append(',')
                append(cell.view?.maxDailyItemCount ?: "").append(',')
                append(cell.view?.daysAtItemCap ?: "").append(',')
                append(cell.view?.brier?.let { fmt(it) } ?: "").append(',')
                append(cell.view?.ece?.let { fmt(it) } ?: "").append(',')
                append(cell.view?.logLoss?.let { fmt(it) } ?: "").append(',')
                append(cell.view?.mastered ?: "").append(',')
                append(cell.view?.reviewedKc ?: "").append(',')
                append(cell.view?.digest ?: "").append('\n')
            }
        }

        fun decide(): List<ArmDecision> = G6_GRID.map { f -> decideArm(f) }

        private fun decideArm(f: Double): ArmDecision {
            val holdoutCells = cells.filter { it.world.layer == "holdout" && it.f == f && it.view != null }
            val devCells = cells.filter { it.world.layer == "dev" && it.f == f && it.view != null }
            val deltas = holdoutCells.map { it.view!!.y!! - it.baseline!!.y!! }
            val devDeltas = devCells.map { it.view!!.y!! - it.baseline!!.y!! }
            val median = median(deltas)
            val positive = deltas.count { it > 0.0 }
            // G1–G3 逐 world；G4 取跨 world 中位（预注册 §3.2）。
            val guard13 = holdoutCells.all { cell ->
                val guard = cell.guard!!
                guard.strictOverruns == 0 &&
                    guard.toleranceOverruns == 0 &&
                    guard.starvedSubjects.isEmpty() &&
                    guard.kcGapOk
            }
            val brierDeltaMedian = median(holdoutCells.mapNotNull { it.guard!!.brierDelta })
            val guardOk = guard13 && (brierDeltaMedian == null || brierDeltaMedian <= BRIER_DELTA_TOLERANCE)
            val endpoint = f == G6_GRID.max()
            val pass = median != null &&
                median >= MIN_EFFECT_ONE_KC &&
                positive >= 8 &&
                guardOk &&
                !endpoint &&
                deltas.size >= 8
            val reason = when {
                deltas.size < 8 -> "UNDERPOWERED"
                median == null -> "NO_EQUAL_TIME_ON_ALL_WORLDS"
                median < MIN_EFFECT_ONE_KC -> "median_delta_below_one_kc"
                positive < 8 -> "sign_consistency_below_8_of_10"
                !guardOk -> "guardrail_violated"
                endpoint -> "grid_endpoint_not_adopted"
                else -> "passed"
            }
            return ArmDecision(
                id = "G6/A${indexOfF(f)}",
                f = f,
                medianDelta = median,
                positiveWorlds = positive,
                n = deltas.size,
                devMedianDelta = median(devDeltas),
                devPositiveWorlds = devDeltas.count { it > 0.0 },
                devN = devDeltas.size,
                guardOk = guardOk,
                pass = pass,
                reason = reason,
                bootstrap = bootstrap(deltas),
            )
        }

        fun decisionJson(decisions: List<ArmDecision>): String = buildString {
            append("{\n")
            append("  \"line\": \"G6\",\n")
            append("  \"primary_metric\": \"exam_subject_coverage_in_window\",\n")
            append("  \"model_based\": true,\n")
            append("  \"min_effect_one_kc\": ").append(hex(MIN_EFFECT_ONE_KC)).append(",\n")
            append("  \"sign_consistency_rule\": \"#{w: delta>0} >= 8/10; P(X>=8|coin)=56/1024=0.0547 (NOT a 5% test)\",\n")
            append("  \"arms\": [\n")
            decisions.forEachIndexed { index, arm ->
                append("    {\"id\": ").append(quote(arm.id))
                append(", \"f\": ").append(num(arm.f))
                append(", \"median_delta\": ").append(hexOrNull(arm.medianDelta))
                append(", \"positive\": ").append(arm.positiveWorlds)
                append(", \"n\": ").append(arm.n)
                append(", \"dev_median_delta\": ").append(hexOrNull(arm.devMedianDelta))
                append(", \"dev_positive\": ").append(arm.devPositiveWorlds)
                append(", \"dev_n\": ").append(arm.devN)
                append(", \"guard_ok\": ").append(arm.guardOk)
                append(", \"pass\": ").append(arm.pass)
                append(", \"reason\": ").append(quote(arm.reason))
                append(", \"bootstrap_ci95\": ").append(ci(arm.bootstrap))
                append("}")
                if (index != decisions.lastIndex) append(',')
                append('\n')
            }
            append("  ],\n")
            val passing = decisions.filter { it.pass }
            val adopted = passing.minByOrNull { it.f }
            val table = perWorldTable(decisions)
            append("  \"table\": [\n")
            table.forEachIndexed { index, row ->
                append("    ").append(row)
                if (index != table.lastIndex) append(',')
                append('\n')
            }
            append("  ],\n")
            append("  \"no_equal_time_cells\": ").append(cells.count { it.view == null }).append(",\n")
            append("  \"adopted_f\": ").append(adopted?.let { num(it.f) } ?: "null").append(",\n")
            append("  \"verdict\": ")
            append(
                quote(
                    when {
                        adopted != null -> "ADOPT_${adopted.f}"
                        else -> "KEEP_0.50 (no arm passed the pre-registered gate)"
                    },
                ),
            )
            append("\n}\n")
        }

        private fun perWorldTable(decisions: List<ArmDecision>): List<String> {
            val seeds = cells.filter { it.world.layer == "holdout" }.map { it.world.seed }.distinct()
            return seeds.map { seed ->
                val entries = decisions.joinToString(",") { arm ->
                    val cell = cells.firstOrNull { it.world.layer == "holdout" && it.world.seed == seed && it.f == arm.f }
                    val delta = cell?.let { c -> c.view?.let { v -> v.y!! - c.baseline!!.y!! } }
                    "\"f=${num(arm.f)}\": " + hexOrNull(delta)
                }
                "{\"seed\": $seed, $entries}"
            }
        }
    }

    private inner class G6Cell(
        val world: WorldSpec,
        val f: Double,
        val view: RunView?,
        val baseline: RunView?,
        val equalTime: EqualTime,
        val guard: GuardReport?,
    )

    private data class EqualTime(
        val rho: Double?,
        val relativeError: Double?,
        val scan: List<Pair<Double, Long>>,
    )

    private fun selectEqualTime(baseline: RunView, scan: List<Pair<Double, RunView>>): EqualTime {
        val target = baseline.totalAnswered.toDouble()
        val scored = scan.map { (rho, view) ->
            Triple(rho, view.totalAnswered, kotlin.math.abs(view.totalAnswered - target) / target)
        }
        val best = scored
            .filter { it.third <= EQUAL_TIME_TOLERANCE }
            .minWithOrNull(compareBy<Triple<Double, Long, Double>> { it.third }.thenByDescending { it.first })
        return EqualTime(
            rho = best?.first,
            relativeError = best?.third,
            scan = scan.map { it.first to it.second.totalAnswered },
        )
    }

    // ==================================================================================
    // §5 权重坐标
    // ==================================================================================

    private inner class WeightExperiment(private val cache: RunCache) {
        private val rows = mutableListOf<WeightRow>()
        private val baseline = mutableMapOf<Long, RunView>()

        fun observe(world: WorldSpec) {
            val w0 = cache.view(seed = world.seed, horizon = 120, truth = TRUTH_SLIP, f = 0.0)
            baseline[world.seed] = w0
            WEIGHT_ARMS.forEach { arm ->
                val view = cache.view(
                    seed = world.seed,
                    horizon = 120,
                    truth = TRUTH_SLIP,
                    f = 0.0,
                    weights = arm.weights,
                    weightsKey = arm.id,
                )
                val rel = kotlin.math.abs(view.totalAnswered - w0.totalAnswered).toDouble() / w0.totalAnswered
                var equal = EqualTime(1.0, rel, emptyList())
                var finalView: RunView? = view
                if (rel > EQUAL_TIME_TOLERANCE) {
                    val scan = RHO_GRID.map { rho ->
                        rho to cache.view(
                            seed = world.seed,
                            horizon = 120,
                            truth = TRUTH_SLIP,
                            f = 0.0,
                            budgetSeconds = equalTimeBudget(rho),
                            weights = arm.weights,
                            weightsKey = arm.id,
                        )
                    }
                    equal = selectEqualTime(w0, scan)
                    // §9：NO_EQUAL_TIME 的臂不得用 as-configured 冒充等时臂，直接剔除。
                    finalView = scan.firstOrNull { it.first == equal.rho }?.second
                }
                rows += WeightRow(world, arm, finalView, w0, equal, finalView?.let { guardOf(it, w0) })
            }
        }

        fun csv(): String = buildString {
            append("layer,seed,arm,coordinate,multiplier,rho,equal_time_rel_error,y,baseline_y,q,")
            append("total_answered_seconds,overrun_strict,overrun_tolerance,starved_subjects,kc_gap_p95,")
            append("brier_delta,guard_ok,brier,digest\n")
            rows.forEach { row ->
                append(row.world.layer).append(',').append(row.world.seed).append(',')
                append(row.arm.id).append(',').append(row.arm.coordinate).append(',')
                append(fmt(row.arm.multiplier)).append(',')
                append(row.equal.rho?.let { fmt(it) } ?: "").append(',')
                append(row.equal.relativeError?.let { fmt(it) } ?: "").append(',')
                append(row.view?.y?.let { fmt(it) } ?: "").append(',')
                append(fmt(row.baseline.y!!)).append(',')
                append(row.view?.q ?: "").append(',')
                append(row.view?.totalAnswered ?: "").append(',')
                append(row.view?.overrunStrict ?: "").append(',')
                append(row.view?.overrunTolerance ?: "").append(',')
                append(row.guard?.starvedSubjects?.joinToString(";") ?: "").append(',')
                append(row.view?.kcGapP95?.let { fmt(it) } ?: "").append(',')
                append(row.guard?.brierDelta?.let { fmt(it) } ?: "").append(',')
                append(row.guard != null && row.guard.strictOverruns == 0 &&
                    row.guard.toleranceOverruns == 0 && row.guard.starvedSubjects.isEmpty() &&
                    row.guard.kcGapOk).append(',')
                append(row.view?.brier?.let { fmt(it) } ?: "").append(',')
                append(row.view?.digest ?: "").append('\n')
            }
        }

        fun decide(): List<ArmDecision> = WEIGHT_ARMS.map { arm ->
            val holdout = rows.filter { it.world.layer == "holdout" && it.arm.id == arm.id }
            val dev = rows.filter { it.world.layer == "dev" && it.arm.id == arm.id }
            val holdoutTimed = holdout.filter { it.view != null }
            val devTimed = dev.filter { it.view != null }
            val deltas = holdoutTimed.map { it.view!!.y!! - it.baseline.y!! }
            val devDeltas = devTimed.map { it.view!!.y!! - it.baseline.y!! }
            val median = median(deltas)
            val positive = deltas.count { it > 0.0 }
            val guard13 = holdoutTimed.all { row ->
                val guard = row.guard!!
                guard.strictOverruns == 0 &&
                    guard.toleranceOverruns == 0 &&
                    guard.starvedSubjects.isEmpty() &&
                    guard.kcGapOk
            }
            val brierDeltaMedian = median(holdoutTimed.mapNotNull { it.guard!!.brierDelta })
            val guardOk = guard13 && (brierDeltaMedian == null || brierDeltaMedian <= BRIER_DELTA_TOLERANCE)
            val endpoint = arm.isEndpoint
            val pass = median != null && median >= MIN_EFFECT_ONE_KC && positive >= 8 && guardOk &&
                !endpoint && deltas.size >= 8
            ArmDecision(
                id = arm.id,
                f = arm.multiplier,
                medianDelta = median,
                positiveWorlds = positive,
                n = deltas.size,
                devMedianDelta = median(devDeltas),
                devPositiveWorlds = devDeltas.count { it > 0.0 },
                devN = devDeltas.size,
                guardOk = guardOk,
                pass = pass,
                reason = when {
                    deltas.size < 8 -> "UNDERPOWERED"
                    median == null -> "NO_EQUAL_TIME_ON_ALL_WORLDS"
                    median < MIN_EFFECT_ONE_KC -> "median_delta_below_one_kc"
                    positive < 8 -> "sign_consistency_below_8_of_10"
                    !guardOk -> "guardrail_violated"
                    endpoint -> "grid_endpoint_not_adopted"
                    else -> "passed"
                },
                bootstrap = bootstrap(deltas),
            )
        }

        fun decisionJson(decisions: List<ArmDecision>): String = buildString {
            append("{\n")
            append("  \"line\": \"weights\",\n")
            append("  \"seam\": \"ReviewScoringWeights (prereg §5.1)\",\n")
            append("  \"model_based\": true,\n")
            append("  \"grid\": [0.75, 0.875, 1.0, 1.125, 1.25],\n")
            append("  \"arms\": [\n")
            decisions.forEachIndexed { index, arm ->
                val meta = WEIGHT_ARMS.first { it.id == arm.id }
                append("    {\"id\": ").append(quote(arm.id))
                append(", \"coordinate\": ").append(quote(meta.coordinate))
                append(", \"multiplier\": ").append(num(meta.multiplier))
                append(", \"median_delta\": ").append(hexOrNull(arm.medianDelta))
                append(", \"positive\": ").append(arm.positiveWorlds)
                append(", \"n\": ").append(arm.n)
                append(", \"dev_median_delta\": ").append(hexOrNull(arm.devMedianDelta))
                append(", \"dev_positive\": ").append(arm.devPositiveWorlds)
                append(", \"dev_n\": ").append(arm.devN)
                append(", \"guard_ok\": ").append(arm.guardOk)
                append(", \"pass\": ").append(arm.pass)
                append(", \"reason\": ").append(quote(arm.reason))
                append(", \"bootstrap_ci95\": ").append(ci(arm.bootstrap))
                append("}")
                if (index != decisions.lastIndex) append(',')
                append('\n')
            }
            append("  ],\n")
            val passing = decisions.filter { it.pass }
            append("  \"passing\": ").append(passing.size).append(",\n")
            append("  \"adopted\": null,\n")
            append("  \"note\": \"至多采纳 1 个坐标；本批未见任何坐标过门时维持全部现值（预注册 §5.4）\",\n")
            append("  \"verdict\": ")
            append(quote(if (passing.isEmpty()) "KEEP_ALL_CURRENT_WEIGHTS" else "REVIEW_CANDIDATES_NEXT_BATCH"))
            append("\n}\n")
        }
    }

    private inner class WeightRow(
        val world: WorldSpec,
        val arm: WeightArm,
        val view: RunView?,
        val baseline: RunView,
        val equal: EqualTime,
        val guard: GuardReport?,
    )

    private data class WeightArm(
        val id: String,
        val coordinate: String,
        val multiplier: Double,
        val weights: ReviewScoringWeights,
        val isEndpoint: Boolean,
    )

    // ==================================================================================
    // §6 掌握阈值读数
    // ==================================================================================

    private inner class ThresholdExperiment(private val cache: RunCache) {
        private val readings = mutableListOf<ThresholdReading>()
        private val prefixChecks = mutableListOf<PrefixCheck>()
        private val consistency = mutableListOf<String>()

        fun observe(world: WorldSpec) {
            THRESHOLD_TRUTHS.forEach { truth ->
                val views = THRESHOLD_DAYS.associateWith { day ->
                    cache.view(seed = world.seed, horizon = day, truth = truth, f = 0.0)
                }
                // 读数一致性：本文件重算的 T0 掌握数必须等于模拟器自报的读数。
                THRESHOLD_DAYS.forEach { day ->
                    val view = views.getValue(day)
                    assertEquals(
                        "T0 掌握数重算与 SimulationMetrics.masteredKnowledgeNodeCount 不一致（seed=${world.seed}," +
                            " truth=$truth, day=$day）：刻意口径漂移，读数作废",
                        view.mastered,
                        view.masteredSets.getValue("T0").size,
                    )
                }
                // §6.3 前缀同一性（以 120 天 run 为参照）。
                val full = views.getValue(120)
                THRESHOLD_DAYS.filter { it != 120 }.forEach { day ->
                    val short = views.getValue(day)
                    prefixChecks += PrefixCheck(
                        world = world,
                        truth = truth,
                        day = day,
                        reviewLogPrefixEqual = short.reviewLog.size <= full.reviewLog.size &&
                            short.reviewLog == full.reviewLog.take(short.reviewLog.size),
                        workloadPrefixEqual = dailyWorkloadPrefixEqual(short, full, day),
                        shortReviewRows = short.reviewLog.size,
                    )
                }
                THRESHOLD_POLICIES.forEach { (policyId, policy) ->
                    THRESHOLD_DAYS.forEach { day ->
                        val view = views.getValue(day)
                        val mastered = view.masteredSets.getValue(policyId)
                        readings += ThresholdReading(
                            world = world,
                            truth = truth,
                            policyId = policyId,
                            day = day,
                            coverage = mastered.size,
                            misjudged = view.misjudged.getValue(policyId),
                            structuralZero = truth == TRUTH_SLIP,
                            retention30 = if (day == 60) retention(views, policyId, 30, 60) else null,
                            retention60 = if (day == 90) retention(views, policyId, 30, 90) else null,
                            retention90 = if (day == 120) retention(views, policyId, 30, 120) else null,
                        )
                    }
                }
            }
        }

        private fun dailyWorkloadPrefixEqual(short: RunView, full: RunView, day: Int): Boolean =
            short.dailyWorkload.size == day && short.dailyWorkload == full.dailyWorkload.take(day)

        private fun retention(
            views: Map<Int, RunView>,
            policyId: String,
            anchor: Int,
            lag: Int,
        ): Double? {
            val anchorSet = views.getValue(anchor).masteredSets.getValue(policyId)
            if (anchorSet.isEmpty()) return null
            val laterSet = views.getValue(lag).masteredSets.getValue(policyId)
            return anchorSet.intersect(laterSet).size.toDouble() / anchorSet.size
        }

        fun csv(): String = buildString {
            append("layer,seed,truth_arm,policy,day,coverage,coverage_share,misjudged,misjudged_rate,")
            append("structural_zero,retention_from_day30_at_60,retention_from_day30_at_90,retention_from_day30_at_120\n")
            readings.forEach { reading ->
                append(reading.world.layer).append(',').append(reading.world.seed).append(',')
                append(reading.truth).append(',').append(reading.policyId).append(',')
                append(reading.day).append(',').append(reading.coverage).append(',')
                append(fmt(reading.coverage / 30.0)).append(',')
                append(reading.misjudged).append(',')
                append(if (reading.coverage == 0) "" else fmt(reading.misjudged.toDouble() / reading.coverage)).append(',')
                append(reading.structuralZero).append(',')
                append(reading.retention30?.let { fmt(it) } ?: "").append(',')
                append(reading.retention60?.let { fmt(it) } ?: "").append(',')
                append(reading.retention90?.let { fmt(it) } ?: "").append('\n')
            }
        }

        fun json(): String = buildString {
            append("{\n")
            append("  \"line\": \"mastery_thresholds\",\n")
            append("  \"model_based\": true,\n")
            append("  \"change_proposed\": false,\n")
            append("  \"thresholds\": [[21.0, 0.9], [14.0, 0.9], [21.0, 0.85]],\n")
            append("  \"note\": \"只测不改：本批不产生任何阈值变更提案（ask 硬约束）\",\n")
            append("  \"aggregates\": [\n")
            val groups = readings.groupBy { Triple(it.truth, it.policyId, it.day) }
            groups.entries.sortedWith(
                compareBy({ it.key.first }, { it.key.second }, { it.key.third }),
            ).forEachIndexed { index, entry ->
                val values = entry.value
                val coverage = values.map { it.coverage }
                val rates = values.filter { it.coverage > 0 }.map { it.misjudged.toDouble() / it.coverage }
                append("    {\"truth_arm\": ").append(quote(entry.key.first))
                append(", \"policy\": ").append(quote(entry.key.second))
                append(", \"day\": ").append(entry.key.third)
                append(", \"n\": ").append(values.size)
                append(", \"coverage_median\": ").append(hexOrNull(median(coverage.map { it.toDouble() })))
                append(", \"coverage_min\": ").append(coverage.minOrNull() ?: 0)
                append(", \"coverage_max\": ").append(coverage.maxOrNull() ?: 0)
                append(", \"misjudge_rate_median\": ").append(hexOrNull(median(rates)))
                append(", \"structural_zero\": ").append(entry.key.first == TRUTH_SLIP)
                append("}")
                if (index != groups.size - 1) append(',')
                append('\n')
            }
            append("  ]\n}\n")
        }

        fun prefixJson(): String = buildString {
            append("{\n  \"checks\": [\n")
            prefixChecks.forEachIndexed { index, check ->
                append("    {\"layer\": ").append(quote(check.world.layer))
                append(", \"seed\": ").append(check.world.seed)
                append(", \"truth_arm\": ").append(quote(check.truth))
                append(", \"horizon_days\": ").append(check.day)
                append(", \"review_log_prefix_equal\": ").append(check.reviewLogPrefixEqual)
                append(", \"daily_workload_prefix_equal\": ").append(check.workloadPrefixEqual)
                append(", \"review_rows_in_short_run\": ").append(check.shortReviewRows)
                append("}")
                if (index != prefixChecks.lastIndex) append(',')
                append('\n')
            }
            append("  ],\n")
            append("  \"all_equal\": ").append(prefixChecks.all { it.reviewLogPrefixEqual && it.workloadPrefixEqual })
            append("\n}\n")
        }
    }

    private data class ThresholdReading(
        val world: WorldSpec,
        val truth: String,
        val policyId: String,
        val day: Int,
        val coverage: Int,
        val misjudged: Int,
        val structuralZero: Boolean,
        val retention30: Double?,
        val retention60: Double?,
        val retention90: Double?,
    )

    private data class PrefixCheck(
        val world: WorldSpec,
        val truth: String,
        val day: Int,
        val reviewLogPrefixEqual: Boolean,
        val workloadPrefixEqual: Boolean,
        val shortReviewRows: Int,
    )

    // ==================================================================================
    // 共享：run 视图 / 缓存
    // ==================================================================================

    private data class RunKey(
        val seed: Long,
        val horizon: Int,
        val budget: Int,
        val f: Double,
        val truth: String,
        val weights: String,
    )

    private inner class RunCache {
        private val views = LinkedHashMap<RunKey, RunView>()
        var physicalRuns: Int = 0
            private set
        var cacheHits: Int = 0
            private set
        val logicalRequests: Int get() = physicalRuns + cacheHits

        fun view(
            seed: Long,
            horizon: Int,
            truth: String,
            f: Double,
            budgetSeconds: Int = DEFAULT_BUDGET_SECONDS,
            weights: ReviewScoringWeights = ReviewScoringWeights.DEFAULT,
            weightsKey: String = "default",
        ): RunView {
            val key = RunKey(seed, horizon, budgetSeconds, f, truth, weightsKey)
            views[key]?.let {
                cacheHits++
                return it
            }
            physicalRuns++
            val run = SimulatedLearner(
                SimulationConfig(
                    seed = seed,
                    horizonDays = horizon,
                    dailyBudgetSeconds = budgetSeconds,
                    examCapacityExtraFraction = f,
                    examDayBySubject = if (horizon >= MIN_EXAM_HORIZON) {
                        SimulationConfig.DEFAULT_EXAM_DAY_BY_SUBJECT
                    } else {
                        // 短 horizon 下默认考日（108..120）越界；考窗最早在第 95 天才开始，
                        // 30/60/90 天的决策与"有考试记录"逐位相同（§6.3 前缀同一性用例钉住）。
                        emptyMap()
                    },
                    truthParameters = truthParameters(truth),
                    scoringWeights = weights,
                ),
            ).run()
            val view = toView(run, key, captureLog = isThresholdRun(key))
            views[key] = view
            return view
        }

        /** 只有 §6 的 7 个阈值 run 需要保留整份 review_log（前缀同一性比对与掌握读数）。 */
        private fun isThresholdRun(key: RunKey): Boolean =
            key.f == 0.0 &&
                key.budget == DEFAULT_BUDGET_SECONDS &&
                key.weights == "default" &&
                key.truth in THRESHOLD_TRUTHS &&
                key.horizon in THRESHOLD_DAYS
    }

    @Suppress("UNCHECKED_CAST")
    private fun toView(run: SimulationRun, key: RunKey, captureLog: Boolean): RunView {
        val metrics = run.metrics
        val at = horizonEnd(key.horizon)
        val truthDecay = -truthParameters(key.truth)[20]
        val masteredSets = LinkedHashMap<String, Set<String>>()
        val misjudged = LinkedHashMap<String, Int>()
        THRESHOLD_POLICIES.forEach { (id, policy) ->
            val set = LinkedHashSet<String>()
            var bad = 0
            run.finalSnapshot.knowledgeMasteryStates.forEach { (kcId, state) ->
                if (
                    ClearlyMasteredForSkipPolicy.meetsMemoryCriterion(
                        memoryStabilityDays = state.memoryStabilityDays,
                        lastAttemptAtEpochMillis = state.lastAttemptAtEpochMillis,
                        atEpochMillis = at,
                        decay = PRODUCT_DECAY,
                        policy = policy,
                    )
                ) {
                    set += kcId
                    val last = state.lastAttemptAtEpochMillis
                    val stability = state.memoryStabilityDays
                    if (last != null && stability != null) {
                        val elapsedDays = ((at - last) / DAY_MILLIS).toDouble()
                        val truthRetention = FsrsScheduleMath.retention(elapsedDays, stability, truthDecay)
                        if (truthRetention < policy.minimumCurrentRetention) bad++
                    }
                }
            }
            masteredSets[id] = set
            misjudged[id] = bad
        }
        val daysByKc = LinkedHashMap<String, MutableSet<Int>>()
        val daysBySubject = LinkedHashMap<String, MutableSet<Int>>()
        run.attempts.forEach { attempt ->
            daysByKc.getOrPut(attempt.knowledgeNodeId) { LinkedHashSet() } += attempt.day
            daysBySubject.getOrPut(attempt.subjectId) { LinkedHashSet() } += attempt.day
        }
        val recentShare = daysBySubject.mapValues { (_, days) -> days.count { it in 91..120 }.toDouble() / 6.0 }
        val gaps = daysByKc.values.map { days ->
            val inWindow = days.filter { it in 31..120 }.sorted()
            if (inWindow.size < 2) {
                90
            } else {
                inWindow.zipWithNext { a, b -> b - a }.maxOrNull() ?: 90
            }
        }.sorted()
        val q = metrics.examSubjectCoverage.sumOf { exam ->
            run.attempts.count { it.subjectId == exam.subjectId && it.day in (exam.examDay - 13)..exam.examDay }
        }
        return RunView(
            seed = key.seed,
            horizon = key.horizon,
            budget = key.budget,
            f = key.f,
            truth = key.truth,
            weightsKey = key.weights,
            y = metrics.examSubjectCoverage.takeIf { it.isNotEmpty() }
                ?.map { it.reviewedShare }?.average(),
            q = q,
            brier = metrics.brierScore,
            ece = metrics.expectedCalibrationError,
            logLoss = metrics.logLoss,
            mastered = metrics.masteredKnowledgeNodeCount,
            reviewedKc = metrics.reviewedKnowledgeNodeCount,
            totalAnswered = metrics.totalAnsweredSeconds,
            overrunStrict = metrics.overrunDays,
            overrunTolerance = metrics.dailyWorkload.count {
                it.answeredSeconds > kotlin.math.floor(1.05 * it.budgetSeconds).toInt()
            },
            recentShare = recentShare,
            kcGapP95 = nearestRank(gaps, 0.95),
            maxDailyItemCount = metrics.dailyWorkload.maxOfOrNull { it.plannedItemCount } ?: 0,
            // `ReviewPlannerV2.MAX_BEAM_STEPS = 20`：预算扩容在 20 题/日封顶后不再转化为工作量，
            // 这是 G6 在本世界形状下可能"测不出差异"的机制解释（报告必须写，不得当成"无作用"）。
            daysAtItemCap = metrics.dailyWorkload.count { it.plannedItemCount >= 20 },
            digest = metrics.digest(),
            masteredSets = masteredSets,
            misjudged = misjudged,
            reviewLog = if (captureLog) run.reviewLog else emptyList(),
            dailyWorkload = if (captureLog) metrics.dailyWorkload else emptyList(),
        )
    }

    private data class RunView(
        val seed: Long,
        val horizon: Int,
        val budget: Int,
        val f: Double,
        val truth: String,
        val weightsKey: String,
        val y: Double?,
        val q: Int,
        val brier: Double?,
        val ece: Double?,
        val logLoss: Double?,
        val mastered: Int,
        val reviewedKc: Int,
        val totalAnswered: Long,
        val overrunStrict: Int,
        val overrunTolerance: Int,
        val recentShare: Map<String, Double>,
        val kcGapP95: Double,
        val maxDailyItemCount: Int,
        val daysAtItemCap: Int,
        val digest: String,
        val masteredSets: Map<String, Set<String>>,
        val misjudged: Map<String, Int>,
        val reviewLog: List<ReviewSample>,
        val dailyWorkload: List<DailyWorkload>,
    )

    private data class GuardReport(
        val strictOverruns: Int,
        val toleranceOverruns: Int,
        val starvedSubjects: List<String>,
        val kcGapOk: Boolean,
        val brierDelta: Double?,
    )

    private fun guardOf(view: RunView, baseline: RunView): GuardReport {
        val starved = baseline.recentShare.keys.filter { subject ->
            (view.recentShare[subject] ?: 0.0) < 0.9 * (baseline.recentShare[subject] ?: 0.0)
        }
        val kcGapOk = view.kcGapP95 <= maxOf(21.0, baseline.kcGapP95)
        val brierDelta = if (view.brier != null && baseline.brier != null) {
            view.brier - baseline.brier
        } else {
            null
        }
        return GuardReport(view.overrunStrict, view.overrunTolerance, starved, kcGapOk, brierDelta)
    }

    // ==================================================================================
    // §4.3 CONFIRM / §8 机检
    // ==================================================================================

    private fun g6ConfirmIfNeeded(decisions: List<ArmDecision>, cache: RunCache): String {
        val finalists = decisions.filter { it.pass }
        if (finalists.isEmpty()) {
            return "{\"status\": \"NOT_RUN\", \"reason\": \"no_arm_passed_g6_gate\", \"model_based\": true}\n"
        }
        val f = finalists.minByOrNull { it.f }!!.f
        val rows = mutableListOf<String>()
        var positive = 0
        var n = 0
        HOLDOUT_SEEDS.take(worldLimit()).forEach { seed ->
            val baseline = cache.view(seed = seed, horizon = 120, truth = TRUTH_DECAY, f = 0.0)
            val scan = RHO_GRID.map { rho ->
                rho to cache.view(
                    seed = seed,
                    horizon = 120,
                    truth = TRUTH_DECAY,
                    f = f,
                    budgetSeconds = equalTimeBudget(rho),
                )
            }
            val equal = selectEqualTime(baseline, scan)
            val view = scan.firstOrNull { it.first == equal.rho }?.second
            if (view != null) {
                n++
                val delta = view.y!! - baseline.y!!
                if (delta > 0.0) positive++
                rows += "{\"seed\": $seed, \"f\": ${num(f)}, \"rho\": ${num(equal.rho!!)}, \"delta\": ${hex(delta)}}"
            }
        }
        val confirmed = positive >= 8
        return buildString {
            append("{\n  \"status\": ").append(quote(if (confirmed) "CONFIRMED" else "CONDITIONAL_NOT_ADOPTED"))
            append(",\n  \"truth_arm\": \"W-decay\",\n  \"f\": ").append(num(f))
            append(",\n  \"positive\": ").append(positive).append(",\n  \"n\": ").append(n)
            append(",\n  \"require\": 8,\n  \"model_based\": true,\n  \"rows\": [\n    ")
            append(rows.joinToString(",\n    "))
            append("\n  ]\n}\n")
        }
    }

    private fun determinismChecks(cache: RunCache): String {
        val requests = listOf(
            Triple(901L, 0.25, "g6_f0.25_rho1.0"),
            Triple(901L, 0.0, "baseline"),
            Triple(902L, 0.35, "g6_f0.35_rho1.0"),
        )
        val rows = requests.map { (seed, f, label) ->
            val first = SimulatedLearner(
                SimulationConfig(seed = seed, horizonDays = 120, examCapacityExtraFraction = f),
            ).run().metrics.digest()
            val second = SimulatedLearner(
                SimulationConfig(seed = seed, horizonDays = 120, examCapacityExtraFraction = f),
            ).run().metrics.digest()
            "{\"case\": ${quote(label)}, \"seed\": $seed, \"f\": ${num(f)}, " +
                "\"digest_a\": ${quote(first)}, \"digest_b\": ${quote(second)}, " +
                "\"equal\": ${first == second}}"
        }
        return buildString {
            append("{\n  \"in_process_repeats\": [\n    ")
            append(rows.joinToString(",\n    "))
            append("\n  ],\n  \"in_process_all_equal\": ")
            append(rows.all { it.contains("\"equal\": true") })
            append(",\n  \"note\": \"跨进程复跑由外部两次 Gradle 调用比对 run-manifest/g6-runs.csv 完成\"\n}\n")
        }
    }

    private fun manifest(
        startedAt: Instant,
        finishedAt: Instant,
        cache: RunCache,
        worldCount: Int,
    ): String = buildString {
        append("{\n")
        append("  \"batch\": \"49\",\n")
        append("  \"model_based\": true,\n")
        append("  \"holdout_run_id\": ").append(quote(HOLDOUT_RUN_ID)).append(",\n")
        append("  \"primary_metric\": \"exam_subject_coverage_in_window\",\n")
        append("  \"prereg_sha256\": ").append(quote(sha256Of(repoRoot().resolve(PREREG_PATH)))).append(",\n")
        append("  \"head\": ").append(quote(gitHead())).append(",\n")
        append("  \"file_sha256\": {\n")
        PINNED_FILES.entries.forEachIndexed { index, entry ->
            val path = repoRoot().resolve(entry.value)
            val hash = if (Files.exists(path)) sha256Of(path) else "MISSING"
            append("    ").append(quote(entry.key)).append(": ").append(quote(hash))
            if (index != PINNED_FILES.size - 1) append(',')
            append('\n')
        }
        append("  },\n")
        append("  \"g6_grid\": [0.15, 0.25, 0.35, 0.5],\n")
        append("  \"g6_baseline_f\": 0.0,\n")
        append("  \"weight_grid\": [0.75, 0.875, 1.0, 1.125, 1.25],\n")
        append("  \"thresholds_evaluated_not_changed\": [[21.0, 0.9], [14.0, 0.9], [21.0, 0.85]],\n")
        append("  \"rho_grid\": [1.0, 0.975, 0.95, 0.925, 0.9, 0.875, 0.85, 0.825, 0.8, 0.775, 0.75],\n")
        append("  \"equal_time_tolerance\": ").append(hex(EQUAL_TIME_TOLERANCE)).append(",\n")
        append("  \"brier_delta_tolerance\": ").append(hex(BRIER_DELTA_TOLERANCE)).append(",\n")
        append("  \"dev_seeds\": [101, 102, 103, 104, 105, 106, 107, 108, 109, 110],\n")
        append("  \"holdout_seeds\": [901, 902, 903, 904, 905, 906, 907, 908, 909, 910],\n")
        append("  \"worlds_per_layer\": ").append(worldCount).append(",\n")
        append("  \"simulation_runs_physical\": ").append(cache.physicalRuns).append(",\n")
        append("  \"simulation_runs_logical\": ").append(cache.logicalRequests).append(",\n")
        append("  \"expected_runs_per_world_from_prereg\": \"69-74 (extra rho scans for unequal weight arms are新增)\",\n")
        append("  \"started_at\": ").append(quote(startedAt.toString())).append(",\n")
        append("  \"finished_at\": ").append(quote(finishedAt.toString())).append(",\n")
        append("  \"wall_clock_seconds\": ").append((finishedAt.toEpochMilli() - startedAt.toEpochMilli()) / 1000).append(",\n")
        append("  \"output_dir\": \"stdout://tools/ci/run_batch49_calibration.py\",\n")
        append("  \"known_deviations\": [\n")
        append("    \"weight-line requires the §5.1 injection seam; seam landed per prereg §5.1 contract, so ReviewPlannerV2.kt / ReviewPlanner.kt / SimulatedLearner.kt hashes differ from prereg §1.1 (baseline digests still pinned in file_sha256_baseline)\",\n")
        append("    \"W-slip misjudgment rate is a structural zero (truth decay == product decay), not evidence\",\n")
        append("    \"A-line FSRS fitting (§7) not run in this ask; not part of the named scan set\",\n")
        append("    \"real-database samples not touched -> BLOCKED, no numbers invented\"\n")
        append("  ],\n")
        append("  \"file_sha256_baseline\": {\n")
        BASELINE_HASHES.entries.forEachIndexed { index, entry ->
            append("    ").append(quote(entry.key)).append(": ").append(quote(entry.value))
            if (index != BASELINE_HASHES.size - 1) append(',')
            append('\n')
        }
        append("  }\n}\n")
    }

    // ==================================================================================
    // 常量与工具
    // ==================================================================================

    private fun resolveOutDir(): Path {
        System.getenv("BATCH49_CALIBRATION_OUT")?.let { raw ->
            // 环境变量只作**仓库 build/ 内**的输出重定向；越界即拒绝，避免把证据文件写到任意路径。
            val root = repoRoot()
            val candidate = Paths.get(raw).toAbsolutePath().normalize()
            require(candidate.startsWith(root.resolve("build"))) {
                "BATCH49_CALIBRATION_OUT 必须位于仓库 build/ 下：$candidate"
            }
            return candidate
        }
        var dir: Path? = Paths.get(System.getProperty("user.dir")).toAbsolutePath()
        while (dir != null) {
            if (Files.isDirectory(dir.resolve(EVIDENCE_DIR))) {
                return dir.resolve(EVIDENCE_DIR).resolve("calibration")
            }
            dir = dir.parent
        }
        error("Cannot locate $EVIDENCE_DIR from ${System.getProperty("user.dir")}")
    }

    private fun repoRoot(): Path {
        var dir: Path? = Paths.get(System.getProperty("user.dir")).toAbsolutePath()
        while (dir != null) {
            if (Files.isDirectory(dir.resolve(".git"))) return dir
            dir = dir.parent
        }
        error("Cannot locate repository root")
    }

    private fun gitHead(): String = runCatching {
        val process = ProcessBuilder("git", "rev-parse", "HEAD")
            .directory(repoRoot().toFile())
            .redirectErrorStream(true)
            .start()
        process.inputStream.bufferedReader().readText().trim().also { process.waitFor() }
    }.getOrDefault("UNKNOWN")

    private fun sha256Of(path: Path): String = MessageDigest.getInstance("SHA-256")
        .digest(Files.readAllBytes(path))
        .joinToString("") { "%02x".format(Locale.ROOT, it.toInt() and 0xff) }

    private fun truthParameters(name: String): DoubleArray = when (name) {
        TRUTH_DECAY -> FsrsScheduleMath.DEFAULT_PARAMETERS.copyOf().also { it[20] *= 1.3 }
        else -> FsrsScheduleMath.DEFAULT_PARAMETERS.copyOf()
    }

    private fun horizonEnd(horizon: Int): Long = BASE_EPOCH_MILLIS + (horizon + 1) * DAY_MILLIS

    private fun equalTimeBudget(rho: Double): Int = kotlin.math.floor(DEFAULT_BUDGET_SECONDS * rho).toInt()

    private fun worldLimit(): Int = System.getenv("BATCH49_CALIBRATION_WORLDS")?.toIntOrNull() ?: 10

    private fun indexOfF(f: Double): Int = G6_GRID.indexOf(f) + 1

    private fun median(values: List<Double>): Double? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[middle - 1] + sorted[middle]) / 2.0 else sorted[middle]
    }

    private fun nearestRank(sortedValues: List<Int>, percentile: Double): Double {
        if (sortedValues.isEmpty()) return Double.NaN
        val rank = kotlin.math.ceil(percentile * sortedValues.size).toInt().coerceIn(1, sortedValues.size)
        return sortedValues[rank - 1].toDouble()
    }

    /** 配对差 cluster bootstrap：重抽 world，10,000 次，固定 seed 20261108，报 2.5/97.5 分位。 */
    private fun bootstrap(deltas: List<Double>): Pair<Double, Double>? {
        if (deltas.size < 2) return null
        val random = java.util.Random(20261108L)
        val medians = DoubleArray(10_000)
        repeat(10_000) { iteration ->
            val sample = List(deltas.size) { deltas[random.nextInt(deltas.size)] }
            medians[iteration] = median(sample)!!
        }
        medians.sort()
        return medians[(0.025 * medians.size).toInt()] to medians[(0.975 * medians.size).toInt() - 1]
    }

    private fun fmt(value: Double): String = String.format(Locale.ROOT, "%.6f", value)

    private fun num(value: Double): String = if (value == kotlin.math.floor(value) && kotlin.math.abs(value) < 1e9) {
        value.toLong().toString()
    } else {
        fmt(value)
    }

    private fun quote(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    private fun hex(value: Double): String = "\"" + java.lang.Double.toHexString(value) + "\""

    private fun hexOrNull(value: Double?): String = value?.let { hex(it) } ?: "null"

    private fun ci(range: Pair<Double, Double>?): String =
        if (range == null) "null" else "[${hex(range.first)}, ${hex(range.second)}]"

    private data class WorldSpec(val layer: String, val seed: Long)

    private data class ArmDecision(
        val id: String,
        val f: Double,
        val medianDelta: Double?,
        val positiveWorlds: Int,
        val n: Int,
        val devMedianDelta: Double?,
        val devPositiveWorlds: Int,
        val devN: Int,
        val guardOk: Boolean,
        val pass: Boolean,
        val reason: String,
        val bootstrap: Pair<Double, Double>?,
    )

    private companion object {
        const val DAY_MILLIS = 86_400_000L

        /** 与 `SimulatedLearner.BASE_EPOCH_MILLIS` 同值；一致性由 T0 掌握数断言钉住。 */
        const val BASE_EPOCH_MILLIS = 1_767_603_600_000L
        const val DEFAULT_BUDGET_SECONDS = 1_200
        const val MIN_EXAM_HORIZON = 108
        const val PREFIX_ROWS = 200

        val DEV_SEEDS = (101L..110L).toList()
        val HOLDOUT_SEEDS = (901L..910L).toList()
        val G6_GRID = listOf(0.15, 0.25, 0.35, 0.5)
        val RHO_GRID = listOf(1.0, 0.975, 0.95, 0.925, 0.9, 0.875, 0.85, 0.825, 0.8, 0.775, 0.75)
        val THRESHOLD_DAYS = listOf(30, 60, 90, 120)
        const val TRUTH_SLIP = "W-slip"
        const val TRUTH_DECAY = "W-decay"
        val THRESHOLD_TRUTHS = listOf(TRUTH_SLIP, TRUTH_DECAY)
        val PRODUCT_DECAY: Double = -FsrsScheduleMath.DEFAULT_PARAMETERS[20]

        /** 1 个 KC = 1/30 = 0.0333：本世界形状下唯一可分辨的最小效应（预注册 §3.1）。 */
        const val MIN_EFFECT_ONE_KC = 1.0 / 30.0
        const val EQUAL_TIME_TOLERANCE = 0.015
        const val BRIER_DELTA_TOLERANCE = 0.005

        val THRESHOLD_POLICIES: Map<String, MasteryDecisionPolicy> = linkedMapOf(
            "T0" to MasteryDecisionPolicy.DEFAULT,
            "T1" to MasteryDecisionPolicy(
                policyVersion = "prereg-49",
                minimumStabilityDays = 14.0,
                minimumCurrentRetention = 0.90,
                decay = -FsrsScheduleMath.DEFAULT_PARAMETERS[20],
                minimumItemFamilies = 2,
                minimumStudyDays = 2,
            ),
            "T2" to MasteryDecisionPolicy(
                policyVersion = "prereg-49",
                minimumStabilityDays = 21.0,
                minimumCurrentRetention = 0.85,
                decay = -FsrsScheduleMath.DEFAULT_PARAMETERS[20],
                minimumItemFamilies = 2,
                minimumStudyDays = 2,
            ),
        )

        val WEIGHT_ARMS: List<WeightArm> = buildList {
            listOf(0.75, 0.875, 1.125, 1.25).forEach { m ->
                add(
                    WeightArm(
                        id = "W/due/x$m",
                        coordinate = "due",
                        multiplier = m,
                        weights = ReviewScoringWeights.DEFAULT.copy(
                            dueWeight = AlgorithmConstants.ReviewScoring.DUE_WEIGHT * m,
                        ),
                        isEndpoint = m == 0.75 || m == 1.25,
                    ),
                )
            }
            listOf(0.75, 0.875, 1.125, 1.25).forEach { m ->
                add(
                    WeightArm(
                        id = "W/weak/x$m",
                        coordinate = "weak",
                        multiplier = m,
                        weights = ReviewScoringWeights.DEFAULT.copy(
                            weaknessWeight = AlgorithmConstants.ReviewScoring.WEAKNESS_WEIGHT * m,
                        ),
                        isEndpoint = m == 0.75 || m == 1.25,
                    ),
                )
            }
            listOf(0.875, 1.125, 1.25).forEach { m ->
                add(
                    WeightArm(
                        id = "W/exam/x$m",
                        coordinate = "exam",
                        multiplier = m,
                        weights = ReviewScoringWeights.DEFAULT.copy(
                            examWeight = AlgorithmConstants.ReviewScoring.EXAM_WEIGHT * m,
                        ),
                        isEndpoint = m == 0.875 || m == 1.25,
                    ),
                )
            }
            listOf(0.75, 0.875).forEach { m ->
                add(
                    WeightArm(
                        id = "W/kc-drop/x$m",
                        coordinate = "kc-drop",
                        multiplier = m,
                        weights = ReviewScoringWeights.DEFAULT.copy(
                            kcDropWeight = ReviewScoringWeights.DEFAULT_KC_DROP_WEIGHT * m,
                        ),
                        isEndpoint = m == 0.75,
                    ),
                )
            }
            listOf(0.75, 0.875, 1.125, 1.25).forEach { m ->
                add(
                    WeightArm(
                        id = "W/diversity/x$m",
                        coordinate = "diversity",
                        multiplier = m,
                        weights = ReviewScoringWeights.DEFAULT.copy(
                            familyPenaltyWeight = AlgorithmConstants.ReviewScoring.FAMILY_PENALTY_WEIGHT * m,
                            sourcePenaltyWeight = AlgorithmConstants.ReviewScoring.SOURCE_PENALTY_WEIGHT * m,
                        ),
                        isEndpoint = m == 0.75 || m == 1.25,
                    ),
                )
            }
        }

        const val EVIDENCE_DIR = "docs/research/evidence/2026-11-08-batch-49"
        const val PREREG_PATH = "docs/research/2026-11-08-review-calibration-prereg.md"

        val PINNED_FILES: Map<String, String> = linkedMapOf(
            "ReviewPlannerV2.kt" to
                "core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/ReviewPlannerV2.kt",
            "AlgorithmConstants.kt" to
                "core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/AlgorithmConstants.kt",
            "ReviewPlanner.kt" to
                "core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/ReviewPlanner.kt",
            "ClearlyMasteredForSkipPolicy.kt" to
                "core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/ClearlyMasteredForSkipPolicy.kt",
            "SchedulingEvaluation.kt" to
                "core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/SchedulingEvaluation.kt",
            "ReviewPlannerScaleBenchmarkTest.kt" to
                "core/domain/src/test/kotlin/com/tingyun/smartmistakebook/core/domain/ReviewPlannerScaleBenchmarkTest.kt",
            "StudyReviewPlannerService.kt" to
                "core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/StudyReviewPlannerService.kt",
            "SimulatedLearner.kt" to
                "core/domain/src/test/kotlin/com/tingyun/smartmistakebook/core/domain/simulation/SimulatedLearner.kt",
            "SimulatedLearnerTest.kt" to
                "core/domain/src/test/kotlin/com/tingyun/smartmistakebook/core/domain/simulation/SimulatedLearnerTest.kt",
        )

        /** 预注册 §1.1 的冻结哈希（实验开始前实测），用于登记 seam 落地前后的差异。 */
        val BASELINE_HASHES: Map<String, String> = linkedMapOf(
            "ReviewPlannerV2.kt" to "ee89aba3dfeb079812b72bf3808b9acc564c7d159743e1577da86de97cc20ecf",
            "AlgorithmConstants.kt" to "e695418f5a24f56ab38ec11d44dd84f7030423c69bb57fd27942e34a36ccfd84",
            "ReviewPlanner.kt" to "e5d66b5b00a22b6dac6fc0d32c3af54658b0f0f8428df74febb458e1ac0bfbfa",
            "SimulatedLearner.kt" to "d170338b35152f1adc0ef1d32e34a60fd896b696cb68fbf36483475f655cc2dc",
        )

        val HOLDOUT_RUN_ID: String = System.getenv("BATCH49_HOLDOUT_RUN_ID")
            ?.takeIf { it.isNotBlank() }
            ?: ("batch49-holdout-" + Instant.now().toEpochMilli())
    }
}
