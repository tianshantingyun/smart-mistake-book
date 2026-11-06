package com.tingyun.smartmistakebook.core.data.telemetry

/**
 * 有界内存聚合器（R5 §1.3.6 / §2.6）：
 * - 计数器、直方图、仪表都按 `(指标, 枚举属性)` 定长聚合，不保存原始事件；
 * - 维度组合总数封顶 [maxCells]，满了丢弃新单元格并累计 `telemetry_dropped_total`；
 * - 不写文件、不联网；落盘由 [TelemetryRecorder.flush] 在同意门通过后触发。
 */
class TelemetryAggregator(private val maxCells: Int = DEFAULT_MAX_CELLS) {
    init {
        require(maxCells >= 1) { "maxCells must be >= 1" }
    }

    private data class CellKey(
        val metric: TelemetryMetric,
        val attributes: Map<String, String>,
    )

    private val counters = LinkedHashMap<CellKey, Long>()
    private val histograms = LinkedHashMap<CellKey, TelemetryHistogram>()
    private val gauges = LinkedHashMap<CellKey, Double>()

    private var droppedSamples: Long = 0L

    /** 已接受的样本数（被丢弃的不算）。 */
    var totalRecords: Long = 0L
        private set

    val dropped: Long get() = droppedSamples

    fun record(event: TelemetryEvent): Boolean {
        val key = CellKey(event.metric, event.attributes.toWireMap())
        return when (event.metric.type) {
            TelemetryMetricType.COUNTER -> {
                if (!ensureCapacity(key, counters)) return false
                counters[key] = (counters[key] ?: 0L) + event.counterDelta
                totalRecords += 1L
                true
            }

            TelemetryMetricType.HISTOGRAM -> {
                val value = event.histogramValue ?: return false
                if (!ensureCapacity(key, histograms)) return false
                histograms.getOrPut(key) { TelemetryHistogram(event.metric.bounds) }.record(value)
                totalRecords += 1L
                true
            }

            TelemetryMetricType.GAUGE -> {
                val value = event.gaugeValue ?: return false
                if (!ensureCapacity(key, gauges)) return false
                gauges[key] = value
                totalRecords += 1L
                true
            }
        }
    }

    fun snapshot(): List<TelemetryMetricSnapshot> {
        val metrics = ArrayList<TelemetryMetricSnapshot>(counters.size + histograms.size + gauges.size + 1)
        counters.forEach { (key, value) ->
            metrics += TelemetryMetricSnapshot(
                name = key.metric.wireName,
                type = key.metric.type.wireName,
                unit = key.metric.unit.wireName,
                value = value.toDouble(),
                attrs = key.attributes,
            )
        }
        histograms.forEach { (key, histogram) ->
            metrics += TelemetryMetricSnapshot(
                name = key.metric.wireName,
                type = key.metric.type.wireName,
                unit = key.metric.unit.wireName,
                bounds = key.metric.bounds,
                counts = histogram.countsSnapshot(),
                count = histogram.count,
                sum = histogram.sum,
                min = histogram.min,
                max = histogram.max,
                attrs = key.attributes,
            )
        }
        gauges.forEach { (key, value) ->
            metrics += TelemetryMetricSnapshot(
                name = key.metric.wireName,
                type = key.metric.type.wireName,
                unit = key.metric.unit.wireName,
                value = value,
                attrs = key.attributes,
            )
        }
        if (droppedSamples > 0L) {
            // 自观测：丢弃本身也是指标；它不占单元格，避免“满了以后连丢弃计数都放不下”。
            metrics += TelemetryMetricSnapshot(
                name = TelemetryMetric.TELEMETRY_DROPPED_TOTAL.wireName,
                type = TelemetryMetricType.COUNTER.wireName,
                unit = TelemetryUnit.COUNT.wireName,
                value = droppedSamples.toDouble(),
                attrs = mapOf("reason" to TelemetryDropReason.QUEUE_FULL.wireName),
            )
        }
        return metrics
    }

    /**
     * 查询级 p50/p95。把同一指标（可选同一 outcome）的所有属性单元格合并成一只直方图再估
     * 分位数；空分布返回 null，不把“没数据”报成 0。
     *
     * `outcome` 非空时只合并该结果的样本：SRE 延迟要求区分成功/失败，慢错误不能被整体
     * p95 掩盖（R5 §1.1）。
     */
    fun percentiles(
        metric: TelemetryMetric = TelemetryMetric.QUERY_DURATION_MS,
        outcome: TelemetryOutcome? = null,
    ): TelemetryPercentiles? {
        require(metric.type == TelemetryMetricType.HISTOGRAM) { "$metric is not a histogram" }
        val merged = TelemetryHistogram(metric.bounds)
        histograms.forEach { (key, histogram) ->
            val matchesOutcome = outcome == null || key.attributes["outcome"] == outcome.wireName
            if (key.metric == metric && matchesOutcome) merged.mergeFrom(histogram)
        }
        if (merged.count == 0L) return null
        return TelemetryPercentiles(
            p50 = merged.quantile(0.50) ?: return null,
            p95 = merged.quantile(0.95) ?: return null,
            count = merged.count,
            min = merged.min ?: return null,
            max = merged.max ?: return null,
            sum = merged.sum,
        )
    }

    /**
     * 空结果率口径（R5 §2.3）：分子用回退前的 `no_relevant_hit`，分母只含真正进入检索的
     * `kb_query_total`；`kb_query_skipped_total` 另行返回，不稀释比率。
     */
    fun queryRates(): TelemetryQueryRates = TelemetryQueryRates(
        queryTotal = counterTotal(TelemetryMetric.QUERY_TOTAL),
        noRelevantHit = counterTotal(TelemetryMetric.QUERY_NO_RELEVANT_HIT_TOTAL),
        fallback = counterTotal(TelemetryMetric.QUERY_FALLBACK_TOTAL),
        emptyContext = counterTotal(TelemetryMetric.QUERY_EMPTY_CONTEXT_TOTAL),
        skipped = counterTotal(TelemetryMetric.QUERY_SKIPPED_TOTAL),
        errors = counterTotal(TelemetryMetric.QUERY_ERROR_TOTAL),
    )

    fun clear() {
        counters.clear()
        histograms.clear()
        gauges.clear()
        droppedSamples = 0L
        totalRecords = 0L
    }

    private fun counterTotal(metric: TelemetryMetric): Long =
        counters.entries.filter { it.key.metric == metric }.sumOf { it.value }

    private fun ensureCapacity(key: CellKey, cells: Map<CellKey, *>): Boolean {
        if (cells.containsKey(key)) return true
        if (cells.size >= maxCells) {
            droppedSamples += 1L
            return false
        }
        return true
    }

    companion object {
        /** subject 9 × stage 8 × outcome 4 × cold 2 × trigger 3 ≈ 1728，取整到 2000。 */
        const val DEFAULT_MAX_CELLS = 2_000
    }
}

/** 查询质量比率；分母为 0 时比率返回 0.0（并由 `queryTotal` 让调用方知道没有分母）。 */
data class TelemetryQueryRates(
    val queryTotal: Long,
    val noRelevantHit: Long,
    val fallback: Long,
    val emptyContext: Long,
    val skipped: Long,
    val errors: Long,
) {
    val emptyResultRate: Double get() = ratio(noRelevantHit, queryTotal)
    val fallbackRate: Double get() = ratio(fallback, queryTotal)
    val finalEmptyContextRate: Double get() = ratio(emptyContext, queryTotal)

    private fun ratio(numerator: Long, denominator: Long): Double =
        if (denominator <= 0L) 0.0 else numerator.toDouble() / denominator.toDouble()
}
