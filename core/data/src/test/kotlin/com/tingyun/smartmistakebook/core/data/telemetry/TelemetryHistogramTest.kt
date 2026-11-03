package com.tingyun.smartmistakebook.core.data.telemetry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetryHistogramTest {

    @Test
    fun `every catalog histogram stays at or below fifteen boundaries`() {
        val histograms = TelemetryMetric.entries.filter { it.type == TelemetryMetricType.HISTOGRAM }
        assertTrue("catalog must expose at least one histogram", histograms.isNotEmpty())
        for (metric in histograms) {
            assertTrue(
                "${metric.wireName} declares ${metric.bounds.size} boundaries",
                metric.bounds.size <= TelemetryBucketBounds.MAX_BUCKET_BOUNDARIES,
            )
            val histogram = TelemetryHistogram(metric.bounds)
            assertEquals(metric.bounds.size + 1, histogram.countsSnapshot().size)
        }
    }

    @Test
    fun `count sum min max and bucket counts are exact`() {
        val histogram = TelemetryHistogram(listOf(0L, 10L, 20L))

        histogram.record(5)
        histogram.record(15)
        histogram.record(25)

        assertEquals(3L, histogram.count)
        assertEquals(45L, histogram.sum)
        assertEquals(5L, histogram.min)
        assertEquals(25L, histogram.max)
        assertEquals(listOf(0L, 1L, 1L, 1L), histogram.countsSnapshot())
    }

    @Test
    fun `boundary values land in the inclusive bucket`() {
        val histogram = TelemetryHistogram(listOf(0L, 10L, 20L))

        histogram.record(0)
        histogram.record(10)
        histogram.record(20)

        assertEquals(listOf(1L, 1L, 1L, 0L), histogram.countsSnapshot())
    }

    @Test
    fun `p50 and p95 are monotonic approximate bucket values`() {
        val histogram = TelemetryHistogram(listOf(0L, 10L, 20L))
        listOf(5L, 15L, 25L).forEach(histogram::record)

        val p50 = histogram.quantile(0.50)
        val p95 = histogram.quantile(0.95)

        assertNotNull(p50)
        assertNotNull(p95)
        assertEquals(20L, p50)
        assertEquals(25L, p95)
        assertTrue(p50!! <= p95!!)
    }

    @Test
    fun `empty histogram returns null quantiles and null min max`() {
        val histogram = TelemetryHistogram(listOf(0L, 10L))

        assertNull(histogram.quantile(0.50))
        assertNull(histogram.quantile(0.95))
        assertNull(histogram.min)
        assertNull(histogram.max)
        assertEquals(0L, histogram.count)
    }

    @Test
    fun `negative observations are clamped to zero instead of creating negative buckets`() {
        val histogram = TelemetryHistogram(listOf(0L, 10L))

        histogram.record(-5)

        assertEquals(listOf(1L, 0L, 0L), histogram.countsSnapshot())
        assertEquals(0L, histogram.min)
        assertEquals(0L, histogram.sum)
    }

    @Test
    fun `merge adds counts and observed extrema`() {
        val left = TelemetryHistogram(listOf(0L, 10L, 20L))
        val right = TelemetryHistogram(listOf(0L, 10L, 20L))
        left.record(5)
        right.record(15)
        right.record(25)

        left.mergeFrom(right)

        assertEquals(3L, left.count)
        assertEquals(45L, left.sum)
        assertEquals(5L, left.min)
        assertEquals(25L, left.max)
        assertEquals(listOf(0L, 1L, 1L, 1L), left.countsSnapshot())
    }
}
