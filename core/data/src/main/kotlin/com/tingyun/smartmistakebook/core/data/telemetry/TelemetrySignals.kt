package com.tingyun.smartmistakebook.core.data.telemetry

/**
 * SRE 四信号在端侧的对应物（R5 §1.1）。每个指标只能映射到一个信号，避免同一份数据
 * 在两个信号下被重复解释。
 */
enum class TelemetrySignal(val wireName: String) {
    LATENCY("latency"),
    TRAFFIC("traffic"),
    ERRORS("errors"),
    SATURATION("saturation"),
}

enum class TelemetryMetricType(val wireName: String) {
    COUNTER("counter"),
    HISTOGRAM("histogram"),
    GAUGE("gauge"),
}

enum class TelemetryUnit(val wireName: String) {
    MILLISECONDS("ms"),
    COUNT("count"),
    BYTES("bytes"),
}

/**
 * 直方图边界（R5 §2.4）。单位随指标：`ms` 或 `count`。
 *
 * 上限 [MAX_BUCKET_BOUNDARIES] = 15：本地存储与聚合基数有界，避免桶爆炸。
 */
object TelemetryBucketBounds {
    const val MAX_BUCKET_BOUNDARIES = 15

    /** 阶段耗时：亚毫秒到 10 s；含 100/500/1000/5000 等常用观察点。 */
    val STAGE_MS = listOf(0L, 1L, 2L, 5L, 10L, 20L, 50L, 100L, 200L, 500L, 1_000L, 2_000L, 5_000L, 10_000L)

    /** 首查：覆盖 TTID/TTFD 常见区间，上限 30 s 用于发现卡死。 */
    val FIRST_QUERY_MS =
        listOf(0L, 50L, 100L, 250L, 500L, 1_000L, 2_000L, 3_000L, 5_000L, 8_000L, 10_000L, 15_000L, 30_000L)

    /** 查询级端到端：与阶段耗时同域，额外把 300 ms 这一常用 SLO 边界放进去。 */
    val QUERY_MS =
        listOf(0L, 10L, 25L, 50L, 100L, 200L, 300L, 500L, 750L, 1_000L, 2_000L, 3_000L, 5_000L, 10_000L)

    /** 512 是当前召回上限；观察候选规模异常膨胀/过窄。 */
    val RECALL_COUNT = listOf(0L, 1L, 5L, 10L, 20L, 50L, 100L, 200L, 512L)
}

/**
 * 端侧指标目录（R5 §2.3 的最小实现面）。每个条目都声明：
 * - 四信号归属（[signal]）；
 * - 类型/单位；
 * - 直方图边界（仅 histogram；≤15）。
 *
 * 这里**没有** `telemetry_upload_*`：v1 只做本机指标面，不建立上传通道；
 * 没有生产者却存在的指标只会制造“看起来在上报”的假象。
 */
enum class TelemetryMetric(
    val wireName: String,
    val type: TelemetryMetricType,
    val unit: TelemetryUnit,
    val signal: TelemetrySignal,
    val bounds: List<Long> = emptyList(),
) {
    // Latency
    STAGE_DURATION_MS(
        wireName = "kb_stage_duration_ms",
        type = TelemetryMetricType.HISTOGRAM,
        unit = TelemetryUnit.MILLISECONDS,
        signal = TelemetrySignal.LATENCY,
        bounds = TelemetryBucketBounds.STAGE_MS,
    ),
    FIRST_QUERY_DURATION_MS(
        wireName = "kb_first_query_duration_ms",
        type = TelemetryMetricType.HISTOGRAM,
        unit = TelemetryUnit.MILLISECONDS,
        signal = TelemetrySignal.LATENCY,
        bounds = TelemetryBucketBounds.FIRST_QUERY_MS,
    ),
    QUERY_DURATION_MS(
        wireName = "kb_query_duration_ms",
        type = TelemetryMetricType.HISTOGRAM,
        unit = TelemetryUnit.MILLISECONDS,
        signal = TelemetrySignal.LATENCY,
        bounds = TelemetryBucketBounds.QUERY_MS,
    ),

    // Traffic
    QUERY_TOTAL(
        wireName = "kb_query_total",
        type = TelemetryMetricType.COUNTER,
        unit = TelemetryUnit.COUNT,
        signal = TelemetrySignal.TRAFFIC,
    ),
    QUERY_SKIPPED_TOTAL(
        wireName = "kb_query_skipped_total",
        type = TelemetryMetricType.COUNTER,
        unit = TelemetryUnit.COUNT,
        signal = TelemetrySignal.TRAFFIC,
    ),

    // Errors（含“精排零命中”和“回退主题”这类隐式错误）
    QUERY_NO_RELEVANT_HIT_TOTAL(
        wireName = "kb_query_no_relevant_hit_total",
        type = TelemetryMetricType.COUNTER,
        unit = TelemetryUnit.COUNT,
        signal = TelemetrySignal.ERRORS,
    ),
    QUERY_FALLBACK_TOTAL(
        wireName = "kb_query_fallback_total",
        type = TelemetryMetricType.COUNTER,
        unit = TelemetryUnit.COUNT,
        signal = TelemetrySignal.ERRORS,
    ),
    QUERY_EMPTY_CONTEXT_TOTAL(
        wireName = "kb_query_empty_context_total",
        type = TelemetryMetricType.COUNTER,
        unit = TelemetryUnit.COUNT,
        signal = TelemetrySignal.ERRORS,
    ),
    QUERY_ERROR_TOTAL(
        wireName = "kb_query_error_total",
        type = TelemetryMetricType.COUNTER,
        unit = TelemetryUnit.COUNT,
        signal = TelemetrySignal.ERRORS,
    ),

    // Saturation
    RECALL_CANDIDATE_COUNT(
        wireName = "kb_recall_candidate_count",
        type = TelemetryMetricType.HISTOGRAM,
        unit = TelemetryUnit.COUNT,
        signal = TelemetrySignal.SATURATION,
        bounds = TelemetryBucketBounds.RECALL_COUNT,
    ),
    DB_SIZE_BYTES(
        wireName = "kb_db_size_bytes",
        type = TelemetryMetricType.GAUGE,
        unit = TelemetryUnit.BYTES,
        signal = TelemetrySignal.SATURATION,
    ),
    DEVICE_FREE_STORAGE_BYTES(
        wireName = "device_free_storage_bytes",
        type = TelemetryMetricType.GAUGE,
        unit = TelemetryUnit.BYTES,
        signal = TelemetrySignal.SATURATION,
    ),
    PROCESS_ANON_RSS_BYTES(
        wireName = "process_anon_rss_bytes",
        type = TelemetryMetricType.GAUGE,
        unit = TelemetryUnit.BYTES,
        signal = TelemetrySignal.SATURATION,
    ),
    TELEMETRY_BUFFER_BYTES(
        wireName = "telemetry_buffer_bytes",
        type = TelemetryMetricType.GAUGE,
        unit = TelemetryUnit.BYTES,
        signal = TelemetrySignal.SATURATION,
    ),
    TELEMETRY_BUFFER_ENTRIES(
        wireName = "telemetry_buffer_entries",
        type = TelemetryMetricType.GAUGE,
        unit = TelemetryUnit.COUNT,
        signal = TelemetrySignal.SATURATION,
    ),
    TELEMETRY_DROPPED_TOTAL(
        wireName = "telemetry_dropped_total",
        type = TelemetryMetricType.COUNTER,
        unit = TelemetryUnit.COUNT,
        signal = TelemetrySignal.SATURATION,
    ),
    ;

    init {
        require(bounds.size <= TelemetryBucketBounds.MAX_BUCKET_BOUNDARIES) {
            "$wireName declares ${bounds.size} bucket boundaries; max is " +
                TelemetryBucketBounds.MAX_BUCKET_BOUNDARIES
        }
        require(bounds.zipWithNext().all { (left, right) -> left < right }) {
            "$wireName bucket boundaries must be strictly increasing: $bounds"
        }
        if (type == TelemetryMetricType.HISTOGRAM) {
            require(bounds.isNotEmpty()) { "$wireName is a histogram and needs bucket boundaries" }
        } else {
            require(bounds.isEmpty()) { "$wireName is ${type.wireName} and must not declare buckets" }
        }
        if (unit == TelemetryUnit.MILLISECONDS) {
            require(type == TelemetryMetricType.HISTOGRAM) { "$wireName: ms is emitted only as a histogram" }
        }
    }
}
