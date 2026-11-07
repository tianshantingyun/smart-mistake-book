package com.tingyun.smartmistakebook.core.domain

/**
 * 校准可见页（批次 49 P2-12）的读模型：一屏说清"现在生效的 21 维 FSRS 参数是哪来的、
 * 最近一次拟合是什么时候、在同一批验证对上比默认参数好还是差、各计划理由与各来源的
 * 实现回忆率是多少、考前扩容（G6）幅度与排程器版本"。
 *
 * 为什么不是一个"参数快照 + 若干独立读口"：页面上的每个数字都必须在**同一时刻、同一批
 * review_log 样本**上算出。分开读会让"当前参数"与"参数对应的验证损失"来自两个时点，
 * 学生看到的比较就不是同一次拟合的结论。因此所有字段由
 * [SchedulingCalibrationViewBuilder.build] 一次装配。
 *
 * 这些都是**只读展示**：参数的写入仍只发生在
 * `StudyExperienceRepository.optimizeSchedulingParameters`（采纳门通过才写）与
 * `SchedulingSettingsStore.setOptimizedParameters`（重置/恢复备份）。
 */
data class SchedulingCalibrationView(
    /** 当前生效参数（21 维，顺序与 `FsrsScheduleMath` 的 w0..w20 一致）。 */
    val parameters: List<Double>,
    /** 参数来源：决定界面上"这是本地拟合的还是导入/默认的"。 */
    val parameterSource: ParameterSource,
    /** 最近一次**本地采纳**的拟合记录；null = 本机没有拟合记录。 */
    val lastFit: FsrsParameterFit?,
    /** 当前 review_log 里可预测（长程）样本数——拟合 400 门用的就是它。 */
    val predictableSampleCount: Int,
    /** 当前参数在同一 80/20 时间切分验证桶上的 logLoss；无验证对时 null。 */
    val currentValidationLogLoss: Double?,
    /** 默认参数（FSRS-6 出厂值）在同一验证桶上的 logLoss；无验证对时 null。 */
    val baselineValidationLogLoss: Double?,
    /** 按计划理由的实现回忆率（与拟合同一口径：MODEL_JUDGED / REVEAL 不进）。 */
    val perReason: List<PlannedReasonCalibration>,
    /** G6 考前扩容上限（单源：`AlgorithmConstants.ReviewScoring`）。 */
    val examCapacityRampMaxExtraFraction: Double,
    /** 排程器版本（单源：`ReviewPlannerV2.VERSION`）。 */
    val plannerVersion: String,
) {
    /** 参数维数单独给出，界面不必再写一个 21 的字面量。 */
    val parameterCount: Int get() = parameters.size

    /** 拟合器 400 可预测样本门（单源引用，界面文案用）。 */
    val fitSampleFloor: Int get() = FsrsParameterOptimizer.MIN_SAMPLES_FOR_FITTING

    enum class ParameterSource {
        /** 本机拟合过并采纳（存在拟合记录）。 */
        LOCAL_FIT,

        /** 仍是 FSRS-6 默认参数（本机没有拟合记录，也没有导入参数）。 */
        DEFAULT,

        /** 参数存在但没有本地拟合记录：备份恢复/更早版本写入——不能冒充本地拟合。 */
        IMPORTED,
    }
}

/**
 * 最近一次成功采纳的拟合记录（时间 / 样本 / 验证损失与默认基线）。
 *
 * 由 `StudySchedulingCalibration.optimizeSchedulingParameters` 在**通过采纳门之后**
 * 与参数一起写入；`setOptimizedParameters` 的任何写入都会清掉它（参数换了，旧记录
 * 就不再描述当前参数）。这两个损失都是**采纳当时**的快照，页面上的"当前验证损失"另行
 * 现算，二者不混用。
 */
data class FsrsParameterFit(
    val adoptedAtEpochMillis: Long,
    /** 拟合时的可预测样本数（`FsrsParameterOptimizer.Result.sampleCount`）。 */
    val sampleCount: Int,
    /** 采纳时候选参数的验证 logLoss（纯 BCE）。 */
    val validationLogLoss: Double,
    /** 采纳时默认参数在同一验证桶上的 logLoss；无验证对时 null。 */
    val baselineValidationLogLoss: Double?,
) {
    init {
        require(adoptedAtEpochMillis >= 0) { "Fit time must not be negative" }
        require(sampleCount >= 0) { "Fit sample count must not be negative" }
    }
}

/**
 * 一次装配（纯函数）：review_log 样本 + 当前存储参数 + 拟合记录 → 页面读模型。
 *
 * 口径全部复用既有单源：
 * - 可预测样本数 / 验证损失 → [FsrsParameterOptimizer]；
 * - 按理由校准 → [SchedulingEvaluationHarness.calibratePlannedReasons]；
 * - G6 幅度 → [AlgorithmConstants.ReviewScoring]；
 * - 版本 → [ReviewPlannerV2.VERSION]。
 */
object SchedulingCalibrationViewBuilder {

    fun build(
        samples: List<ReviewSample>,
        storedParameters: DoubleArray?,
        lastFit: FsrsParameterFit?,
    ): SchedulingCalibrationView {
        val current = storedParameters ?: FsrsScheduleMath.DEFAULT_PARAMETERS
        // 来源判定只看"参数是怎么来的"，不看值像不像默认参数：备份恢复回来的参数可能
        // 恰好等于默认值，但它仍不是本地拟合的产物；反过来，本地拟合也可能收敛到接近默认。
        val source = when {
            lastFit != null -> SchedulingCalibrationView.ParameterSource.LOCAL_FIT
            storedParameters == null -> SchedulingCalibrationView.ParameterSource.DEFAULT
            else -> SchedulingCalibrationView.ParameterSource.IMPORTED
        }
        return SchedulingCalibrationView(
            parameters = current.toList(),
            parameterSource = source,
            lastFit = lastFit,
            predictableSampleCount = FsrsParameterOptimizer.predictableSampleCount(samples),
            currentValidationLogLoss = FsrsParameterOptimizer.validationLogLoss(samples, current),
            // 同一函数、同一批 fittable 样本、同一 80/20 分位切分——两个损失才可相减。
            baselineValidationLogLoss = FsrsParameterOptimizer.validationLogLoss(
                samples,
                FsrsScheduleMath.DEFAULT_PARAMETERS,
            ),
            perReason = SchedulingEvaluationHarness.calibratePlannedReasons(samples),
            examCapacityRampMaxExtraFraction =
                AlgorithmConstants.ReviewScoring.EXAM_CAPACITY_RAMP_MAX_EXTRA_FRACTION,
            plannerVersion = ReviewPlannerV2.VERSION,
        )
    }
}
