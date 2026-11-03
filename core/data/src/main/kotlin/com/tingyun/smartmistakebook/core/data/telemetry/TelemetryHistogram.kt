package com.tingyun.smartmistakebook.core.data.telemetry

/**
 * 定长直方图（R5 §1.3.6）：固定桶数组 + count/sum/min/max，不保存原始事件。
 *
 * 分位数是**桶内线性插值**的近似值（Prometheus classic histogram 的常规做法）：
 * 找到第 rank 个样本所在桶后，在 [lower, upper] 之间按桶内位置插值。最后一只桶
 * （大于最大边界）以观测到的 `max` 为上界。因此 p50/p95 只能作为分布估计，不能当作
 * 精确样本值；`count/sum/min/max` 是精确的。
 *
 * 桶语义：`counts[0]` 表示 `<= bounds[0]`，`counts[i]` 表示 `(bounds[i-1], bounds[i]]`，
 * 最后一只表示 `> bounds.last()`。
 */
class TelemetryHistogram(val bounds: List<Long>) {
    init {
        require(bounds.isNotEmpty()) { "histogram needs at least one boundary" }
        require(bounds.size <= TelemetryBucketBounds.MAX_BUCKET_BOUNDARIES) {
            "histogram declares ${bounds.size} boundaries; max is " +
                TelemetryBucketBounds.MAX_BUCKET_BOUNDARIES
        }
        require(bounds.zipWithNext().all { (left, right) -> left < right }) {
            "histogram boundaries must be strictly increasing: $bounds"
        }
    }

    private val counts = LongArray(bounds.size + 1)
    private var observedCount: Long = 0L
    private var observedSum: Long = 0L
    private var observedMin: Long = Long.MAX_VALUE
    private var observedMax: Long = Long.MIN_VALUE

    val count: Long get() = observedCount
    val sum: Long get() = observedSum

    /** 没有样本时为 null（而不是 0，避免把“没观测”说成“0 ms”）。 */
    val min: Long? get() = if (observedCount == 0L) null else observedMin
    val max: Long? get() = if (observedCount == 0L) null else observedMax

    fun record(value: Long) {
        // 时钟回拨/负值不制造负桶：夹到 0，保留“发生过一次”的事实。
        val normalized = value.coerceAtLeast(0L)
        counts[bucketIndexOf(normalized)] += 1L
        observedCount += 1L
        observedSum += normalized
        if (normalized < observedMin) observedMin = normalized
        if (normalized > observedMax) observedMax = normalized
    }

    fun mergeFrom(other: TelemetryHistogram) {
        require(bounds == other.bounds) { "cannot merge histograms with different boundaries" }
        if (other.observedCount == 0L) return
        for (index in counts.indices) {
            counts[index] += other.counts[index]
        }
        observedCount += other.observedCount
        observedSum += other.observedSum
        if (other.observedMin < observedMin) observedMin = other.observedMin
        if (other.observedMax > observedMax) observedMax = other.observedMax
    }

    fun countsSnapshot(): List<Long> = counts.toList()

    /**
     * @param quantile 0.0–1.0。空直方图返回 null。
     * @return 近似分位数（毫秒或计数，取决于指标单位）。
     */
    fun quantile(quantile: Double): Long? {
        require(quantile in 0.0..1.0) { "quantile must be within [0, 1]: $quantile" }
        if (observedCount == 0L) return null
        val rank = kotlin.math.ceil(quantile * observedCount.toDouble()).toLong().coerceIn(1L, observedCount)
        var cumulative = 0L
        for (index in counts.indices) {
            val bucketCount = counts[index]
            if (bucketCount == 0L) continue
            val previousCumulative = cumulative
            cumulative += bucketCount
            if (cumulative < rank) continue

            val lower = if (index == 0) 0L else bounds[index - 1]
            val upper = if (index < bounds.size) bounds[index] else observedMax
            if (upper <= lower) return upper
            val rankInBucket = rank - previousCumulative
            val interpolated = lower + ((upper - lower) * rankInBucket) / bucketCount
            return interpolated.coerceIn(lower, upper)
        }
        return observedMax
    }

    private fun bucketIndexOf(value: Long): Int {
        for (index in bounds.indices) {
            if (value <= bounds[index]) return index
        }
        return bounds.size
    }
}

/** 查询级分位数的只读结果。 */
data class TelemetryPercentiles(
    val p50: Long,
    val p95: Long,
    val count: Long,
    val min: Long,
    val max: Long,
    val sum: Long,
)
