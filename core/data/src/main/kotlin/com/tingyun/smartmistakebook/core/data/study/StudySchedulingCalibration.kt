package com.tingyun.smartmistakebook.core.data.study

import com.tingyun.smartmistakebook.core.database.StudyDatabasePort
import com.tingyun.smartmistakebook.core.domain.CalibrationInput
import com.tingyun.smartmistakebook.core.domain.CalibrationReportBuilder
import com.tingyun.smartmistakebook.core.domain.ChatEvidenceGateCalibration
import com.tingyun.smartmistakebook.core.domain.FsrsParameterFit
import com.tingyun.smartmistakebook.core.domain.FsrsParameterOptimizer
import com.tingyun.smartmistakebook.core.domain.FsrsScheduleMath
import com.tingyun.smartmistakebook.core.domain.HLRPredictionAuditService
import com.tingyun.smartmistakebook.core.domain.OptimalRetention
import com.tingyun.smartmistakebook.core.domain.PlannedReasonCalibration
import com.tingyun.smartmistakebook.core.domain.ReviewSample
import com.tingyun.smartmistakebook.core.domain.SchedulingCalibrationView
import com.tingyun.smartmistakebook.core.domain.SchedulingCalibrationViewBuilder
import com.tingyun.smartmistakebook.core.domain.SchedulingEvaluationHarness
import com.tingyun.smartmistakebook.core.domain.fittableReviewSamples
import com.tingyun.smartmistakebook.core.domain.SchedulingEvaluationReport
import com.tingyun.smartmistakebook.core.domain.SchedulingSettingsStore
import com.tingyun.smartmistakebook.core.domain.SourceCalibration
import com.tingyun.smartmistakebook.core.model.CalibrationReport
import com.tingyun.smartmistakebook.core.model.LearnerSnapshot
import com.tingyun.smartmistakebook.core.model.LearningModelVersion
import java.time.Clock
import kotlinx.coroutines.flow.first

/**
 * Calibration and scheduling-parameter surfaces of the study repository:
 * evaluation reports, per-source and per-reason calibration, reminder timing,
 * FSRS parameter optimisation and the desired-retention recommendation. Kept
 * apart from the repository's write orchestration; the learner projection is
 * injected as a provider so this service never owns projection state.
 */
internal class StudySchedulingCalibration(
    private val database: StudyDatabasePort,
    private val learnerId: String,
    private val reviewLogSink: ReviewLogSink,
    private val predictionAuditService: HLRPredictionAuditService,
    private val schedulingSettingsStore: SchedulingSettingsStore?,
    private val clock: Clock,
    private val learnerSnapshot: suspend () -> LearnerSnapshot,
) {

    suspend fun evaluateSchedulingModels(): SchedulingEvaluationReport? {
        // 与 harness 同一口径：讲题判定行不计入可评估样本，因此只做过讲题判定复习的
        // 学习者走"没有数据"的返回，而不是撞进 evaluate 的空集要求。
        val samples = fittableReviewSamples(reviewLogSink.reviewSamples())
        if (samples.isEmpty()) return null
        val eligible = samples.groupBy(ReviewSample::practiceUnitId).values.any { it.size >= 2 }
        if (!eligible) return null
        return SchedulingEvaluationHarness.evaluate(samples)
    }


    suspend fun sourceCalibrations(): List<SourceCalibration> =
        reviewLogSink.sourceCalibrations()


    suspend fun plannedReasonCalibrations(): List<PlannedReasonCalibration> =
        SchedulingEvaluationHarness.calibratePlannedReasons(reviewLogSink.reviewSamples())

    /**
     * 校准可见页的一次装配（批次 49 P2-12）。
     *
     * 读的都是既有读面：review_log（唯一属主 [ReviewLogSink]）+ 设置存储里的当前参数与
     * 拟合记录；不含任何新 DAO 方法、不写任何东西。装配口径在
     * [SchedulingCalibrationViewBuilder]（纯函数，有 JVM 用例）。
     */
    suspend fun schedulingCalibrationView(): SchedulingCalibrationView =
        SchedulingCalibrationViewBuilder.build(
            samples = reviewLogSink.reviewSamples(),
            storedParameters = schedulingSettingsStore?.optimizedParameters?.first(),
            lastFit = schedulingSettingsStore?.parameterFit?.first(),
        )


    suspend fun chatEvidenceGateCalibration(): ChatEvidenceGateCalibration.GateCalibrationReport? {
        val acceptedTotal = database.countAcceptedChatEvidenceSince(
            learnerId = learnerId,
            sinceEpochMillis = 0,
        )
        val rejected = database.countRejectedChatEvidenceByReason(learnerId)
        // 30 天观察窗的每小时分布（校准看近期行为，不看全部历史）。
        // P2 修正（审计 2026-09-28）：全用注入 Clock，不再直接读系统时钟。
        val hourWindowStart = clock.millis() - 30L * 24 * 60 * 60 * 1000
        val perHour = database.countAcceptedChatEvidencePerHour(learnerId, hourWindowStart)
        // AUD-11 ①：DAO 的分桶行现在带 source_kind。同一批行切两刀——按拒因/小时求总数
        //（原口径：滚动窗压力与占比都用它）与按通道摊开（新维：读得出"被挡掉的是哪一路"）。
        val observation = ChatEvidenceGateCalibration.GateObservation(
            acceptedCount = acceptedTotal,
            rejectedByReason = rejected
                .groupBy { it.reason }
                .mapValues { (_, rows) -> rows.sumOf { it.count } },
            acceptedPerHour = perHour
                .groupBy { it.hourBucket }
                .mapValues { (_, rows) -> rows.sumOf { it.count } },
            rejectedBySourceKindAndReason = rejected
                .groupBy { it.sourceKind }
                .mapValues { (_, rows) -> rows.associate { it.reason to it.count } },
            acceptedPerSourceKindPerHour = perHour
                .groupBy { it.sourceKind }
                .mapValues { (_, rows) -> rows.associate { it.hourBucket to it.count } },
        )
        if (observation.totalObservations == 0) return null
        return ChatEvidenceGateCalibration.calibrate(observation)
    }


    suspend fun suggestedReminderMinute(): Int? = reviewLogSink.suggestedReminderMinute()


    suspend fun optimizeSchedulingParameters(): FsrsParameterOptimizer.Result? {
        val store = requireNotNull(schedulingSettingsStore) {
            "Parameter optimization requires a scheduling settings store"
        }
        // Phone-safe bound: numeric-gradient fitting replays the history many
        // times, so optimization runs on the most recent window only.
        // W2-2 修正：窗口截断在**过滤之后**——否则 MODEL_JUDGED/REVEAL 行占比高时，
        // 真实作答行会被挤出窗口，20k 行里可能凑不出 400 个可拟合样本。
        val fittingSamples = fittableReviewSamples(reviewLogSink.reviewSamples())
            .takeLast(MAX_OPTIMIZE_SAMPLES)
        val result = FsrsParameterOptimizer.optimize(fittingSamples)
        if (result.mode == FsrsParameterOptimizer.Mode.INSUFFICIENT_DATA) return null

        // W2-2/KF-04 采纳门：候选参数在同一批数据、同一时间切分协议下**不劣于**存量参数
        // 才写入（官方采纳门）；存量更优或无法同协议比较时保守保留存量。
        // 拒绝路径**不得调用** setOptimizedParameters(null)——那会把学习者已有的参数清空。
        val existing = store.optimizedParameters.first()
        val candidateLoss = FsrsParameterOptimizer.validationLogLoss(fittingSamples, result.parameters)
        val existingLoss = existing?.let {
            FsrsParameterOptimizer.validationLogLoss(fittingSamples, it)
        }
        if (!shouldAdopt(candidateLoss, existing, existingLoss)) {
            android.util.Log.i(
                LOG_TAG,
                "adoption gate kept stored parameters: candidate=$candidateLoss existing=$existingLoss",
            )
            return null
        }
        val adoptedLoss = candidateLoss ?: return null
        store.setOptimizedParameters(result.parameters)
        // 批次 49 P2-12：参数写入后立刻记下"这次拟合的时点/样本/验证损失与基线"。
        // 只写参数的话，页面的"最近拟合"在重启后只能显示"—"；顺序是 先参数、后记录，
        // 因为 setOptimizedParameters 会清掉旧记录（旧时点不得贴到新参数上）。
        store.recordParameterFit(
            FsrsParameterFit(
                adoptedAtEpochMillis = clock.millis(),
                sampleCount = result.sampleCount,
                validationLogLoss = adoptedLoss,
                baselineValidationLogLoss = FsrsParameterOptimizer.validationLogLoss(
                    fittingSamples,
                    FsrsScheduleMath.DEFAULT_PARAMETERS,
                ),
            ),
        )
        return result
    }

    /**
     * W2-2/KF-04 的采纳判定（纯函数，分支各有用例）：
     * - 候选不可测（无验证对）→ 拒绝：拿不到同协议证据就不许覆盖存量；
     * - 存量为空 → 首次写入；
     * - 存量在但不可测（历史参数在其时点无验证对）→ 保守保留；
     * - 都可测 → 不劣于（≤）才写。
     */
    internal fun shouldAdopt(
        candidateLoss: Double?,
        existingParameters: DoubleArray?,
        existingLoss: Double?,
    ): Boolean = when {
        candidateLoss == null -> false
        existingParameters == null -> true
        existingLoss == null -> false
        else -> candidateLoss <= existingLoss
    }


    suspend fun recommendedDesiredRetention(): OptimalRetention.Recommendation? {
        val snapshot = learnerSnapshot()
        val cards = snapshot.problemMemoryStates.values.map { memory ->
            OptimalRetention.Card(
                stabilityDays = memory.stabilityDays,
                difficulty = memory.difficulty,
            )
        }
        val parameters = schedulingSettingsStore
            ?.optimizedParameters
            ?.first()
            ?: FsrsScheduleMath.DEFAULT_PARAMETERS
        return OptimalRetention.recommend(cards, parameters)
    }


    /**
     * Calibration report for one model generation, computed over resolved
     * prediction/outcome pairs persisted by the audit loop (audit §6.3).
     */
    suspend fun calibrationReport(modelVersion: LearningModelVersion): CalibrationReport {
        val resolved = database.readResolvedStudentModelPredictions(
            modelId = modelVersion.modelId,
            modelVersion = modelVersion.version,
        )
        return CalibrationReportBuilder.build(
            modelVersion = modelVersion,
            resolved = resolved.map { row ->
                CalibrationInput(
                    predictedScore = row.predictedScore,
                    conservativeScore = row.conservativeScore,
                    wasIndependentCorrect = row.wasIndependentCorrect,
                )
            },
            totalPredictions = resolved.size,
            generatedAtEpochMillis = clock.millis(),
        )
    }


    suspend fun calibrationReport(): CalibrationReport =
        calibrationReport(predictionAuditService.modelVersion)


    private companion object {
        const val MAX_OPTIMIZE_SAMPLES = 20_000
        const val LOG_TAG = "StudySchedulingCalibration"
    }
}
