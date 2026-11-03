package com.tingyun.smartmistakebook.core.data.telemetry

/**
 * 记录点产出的唯一事件类型集合。调用方在**已通过门**之后构造事件；事件本身只带枚举维度，
 * 不携带题面、节点 ID、文件路径或任何自由文本（R5 §2.1/§2.7）。
 */
sealed class TelemetryEvent {
    abstract val metric: TelemetryMetric
    abstract val attributes: TelemetryAttributes

    open val counterDelta: Long get() = 1
    open val histogramValue: Long? get() = null
    open val gaugeValue: Double? get() = null
}

/** 单个检索阶段耗时。 */
data class StageDuration(
    val stage: TelemetryStage,
    val outcome: TelemetryOutcome,
    val coldStart: Boolean,
    val subject: TelemetrySubject?,
    val trigger: TelemetryTrigger,
    val durationMs: Long,
) : TelemetryEvent() {
    override val metric: TelemetryMetric get() = TelemetryMetric.STAGE_DURATION_MS
    override val attributes: TelemetryAttributes
        get() = TelemetryAttributes(
            stage = stage,
            outcome = outcome,
            coldStart = coldStart,
            subject = subject,
            trigger = trigger,
        )
    override val histogramValue: Long get() = durationMs.coerceAtLeast(0L)
}

/** 用户动作被接受 → 首个结果渲染完成；每个进程只记首次真实检索。 */
data class FirstQueryDuration(
    val coldStart: Boolean,
    val kbReady: Boolean,
    val trigger: TelemetryTrigger,
    val durationMs: Long,
) : TelemetryEvent() {
    override val metric: TelemetryMetric get() = TelemetryMetric.FIRST_QUERY_DURATION_MS
    override val attributes: TelemetryAttributes
        get() = TelemetryAttributes(coldStart = coldStart, kbReady = kbReady, trigger = trigger)
    override val histogramValue: Long get() = durationMs.coerceAtLeast(0L)
}

/** 查询级端到端耗时：p50/p95 由 [TelemetryAggregator.percentiles] 从桶分布估计。 */
data class QueryDuration(
    val trigger: TelemetryTrigger,
    val outcome: TelemetryOutcome,
    val durationMs: Long,
) : TelemetryEvent() {
    override val metric: TelemetryMetric get() = TelemetryMetric.QUERY_DURATION_MS
    override val attributes: TelemetryAttributes
        get() = TelemetryAttributes(trigger = trigger, outcome = outcome)
    override val histogramValue: Long get() = durationMs.coerceAtLeast(0L)
}

/** 进入真实检索的查询数（分母；排除跳过）。 */
data class QueryStarted(
    val trigger: TelemetryTrigger,
    val subject: TelemetrySubject,
    val kbReady: Boolean,
) : TelemetryEvent() {
    override val metric: TelemetryMetric get() = TelemetryMetric.QUERY_TOTAL
    override val attributes: TelemetryAttributes
        get() = TelemetryAttributes(trigger = trigger, subject = subject, kbReady = kbReady)
}

/** 未进入检索的查询（空题面/未就绪/加载失败/取消）。 */
data class QuerySkipped(
    val reason: TelemetryReason,
    val trigger: TelemetryTrigger,
) : TelemetryEvent() {
    override val metric: TelemetryMetric get() = TelemetryMetric.QUERY_SKIPPED_TOTAL
    override val attributes: TelemetryAttributes
        get() = TelemetryAttributes(reason = reason, trigger = trigger)
}

/** 召回候选非空、但精排 `rankedAtoms` 为空（回退主题之前）。 */
data class NoRelevantHit(
    val subject: TelemetrySubject,
    val trigger: TelemetryTrigger,
) : TelemetryEvent() {
    override val metric: TelemetryMetric get() = TelemetryMetric.QUERY_NO_RELEVANT_HIT_TOTAL
    override val attributes: TelemetryAttributes
        get() = TelemetryAttributes(subject = subject, trigger = trigger)
}

/** 精排零命中后回退到主题节点。 */
data class FallbackToTopic(
    val trigger: TelemetryTrigger,
) : TelemetryEvent() {
    override val metric: TelemetryMetric get() = TelemetryMetric.QUERY_FALLBACK_TOTAL
    override val attributes: TelemetryAttributes
        get() = TelemetryAttributes(fallback = TelemetryFallback.TOPIC, trigger = trigger)
}

/** 最终上下文为空。 */
data class EmptyContext(
    val reason: TelemetryReason,
    val trigger: TelemetryTrigger,
) : TelemetryEvent() {
    override val metric: TelemetryMetric get() = TelemetryMetric.QUERY_EMPTY_CONTEXT_TOTAL
    override val attributes: TelemetryAttributes
        get() = TelemetryAttributes(reason = reason, trigger = trigger)
}

/** 检索阶段异常；`error_class` 是有界枚举。 */
data class QueryError(
    val stage: TelemetryStage,
    val errorClass: TelemetryErrorClass,
    val retryable: Boolean,
) : TelemetryEvent() {
    override val metric: TelemetryMetric get() = TelemetryMetric.QUERY_ERROR_TOTAL
    override val attributes: TelemetryAttributes
        get() = TelemetryAttributes(stage = stage, errorClass = errorClass, retryable = retryable)
}

/** 512 召回后的候选数（饱和度代理）。 */
data class RecallCandidates(
    val subject: TelemetrySubject,
    val trigger: TelemetryTrigger,
    val candidateCount: Int,
) : TelemetryEvent() {
    override val metric: TelemetryMetric get() = TelemetryMetric.RECALL_CANDIDATE_COUNT
    override val attributes: TelemetryAttributes
        get() = TelemetryAttributes(subject = subject, trigger = trigger)
    override val histogramValue: Long get() = candidateCount.coerceAtLeast(0).toLong()
}

/**
 * 饱和度仪表：只允许目录中的 gauge 指标，值是原始字节数/条目数（不在这里换算档位）。
 */
data class SaturationGauge(
    override val metric: TelemetryMetric,
    val value: Double,
    override val attributes: TelemetryAttributes = TelemetryAttributes(),
) : TelemetryEvent() {
    init {
        require(metric.type == TelemetryMetricType.GAUGE) {
            "$metric is ${metric.type.wireName}; SaturationGauge only accepts gauges"
        }
        require(value.isFinite() && value >= 0.0) { "gauge value must be finite and non-negative: $value" }
    }

    override val gaugeValue: Double get() = value
}
