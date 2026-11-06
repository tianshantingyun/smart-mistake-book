package com.tingyun.smartmistakebook.core.data.telemetry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetryAggregatorTest {

    @Test
    fun `four golden signals each have at least one catalog metric`() {
        for (signal in TelemetrySignal.entries) {
            assertTrue(
                "no metric mapped to ${signal.wireName}",
                TelemetryMetric.entries.any { it.signal == signal },
            )
        }
    }

    @Test
    fun `every catalog metric maps to exactly one golden signal`() {
        val counts = TelemetryMetric.entries.groupingBy { it.signal }.eachCount()
        assertEquals(TelemetrySignal.entries.toSet(), counts.keys)
    }

    @Test
    fun `empty result rate uses pre-fallback numerator and excludes skipped queries from the denominator`() {
        val aggregator = TelemetryAggregator()
        repeat(4) { aggregator.record(QueryStarted(TelemetryTrigger.TUTOR, TelemetrySubject.MATH, kbReady = true)) }
        aggregator.record(NoRelevantHit(TelemetrySubject.MATH, TelemetryTrigger.TUTOR))
        aggregator.record(FallbackToTopic(TelemetryTrigger.TUTOR))
        aggregator.record(EmptyContext(TelemetryReason.NO_CANDIDATES, TelemetryTrigger.TUTOR))
        repeat(2) { aggregator.record(QuerySkipped(TelemetryReason.NO_FEATURES, TelemetryTrigger.TUTOR)) }

        val rates = aggregator.queryRates()

        assertEquals(4L, rates.queryTotal)
        assertEquals(1L, rates.noRelevantHit)
        assertEquals(1L, rates.fallback)
        assertEquals(1L, rates.emptyContext)
        assertEquals(2L, rates.skipped)
        assertEquals(0.25, rates.emptyResultRate, 0.0001)
        assertEquals(0.25, rates.fallbackRate, 0.0001)
        assertEquals(0.25, rates.finalEmptyContextRate, 0.0001)
    }

    @Test
    fun `query percentiles are computed from the merged query histogram`() {
        val aggregator = TelemetryAggregator()
        listOf(10L, 20L, 30L, 40L).forEach { value ->
            aggregator.record(QueryDuration(TelemetryTrigger.TUTOR, TelemetryOutcome.OK, value))
        }

        val percentiles = aggregator.percentiles()

        assertNotNull(percentiles)
        assertEquals(4L, percentiles!!.count)
        assertEquals(10L, percentiles.min)
        assertEquals(40L, percentiles.max)
        assertEquals(100L, percentiles.sum)
        assertTrue("p50=${percentiles.p50}", percentiles.p50 <= percentiles.p95)
    }

    @Test
    fun `empty aggregator has no percentiles instead of zero values`() {
        assertEquals(null, TelemetryAggregator().percentiles())
    }

    @Test
    fun `percentiles can isolate successful queries from slow errors`() {
        val aggregator = TelemetryAggregator()
        aggregator.record(QueryDuration(TelemetryTrigger.TUTOR, TelemetryOutcome.OK, 10))
        aggregator.record(QueryDuration(TelemetryTrigger.TUTOR, TelemetryOutcome.OK, 20))
        aggregator.record(QueryDuration(TelemetryTrigger.TUTOR, TelemetryOutcome.ERROR, 5_000))

        val ok = aggregator.percentiles(outcome = TelemetryOutcome.OK)
        val errors = aggregator.percentiles(outcome = TelemetryOutcome.ERROR)

        assertNotNull(ok)
        assertNotNull(errors)
        assertEquals(2L, ok!!.count)
        assertEquals(20L, ok.max)
        assertEquals(1L, errors!!.count)
        assertEquals(5_000L, errors.max)
    }

    @Test
    fun `cardinality cap drops new cells and reports the drop as a metric`() {
        val aggregator = TelemetryAggregator(maxCells = 2)

        aggregator.record(QueryStarted(TelemetryTrigger.TUTOR, TelemetrySubject.MATH, kbReady = true))
        aggregator.record(QueryStarted(TelemetryTrigger.REVIEW, TelemetrySubject.MATH, kbReady = true))
        val accepted = aggregator.record(QueryStarted(TelemetryTrigger.PREDISCLOSURE, TelemetrySubject.MATH, kbReady = true))

        assertFalse(accepted)
        assertEquals(2L, aggregator.totalRecords)
        assertEquals(1L, aggregator.dropped)
        val dropped = aggregator.snapshot().single { it.name == "telemetry_dropped_total" }
        assertEquals(1.0, dropped.value!!, 0.0)
        assertEquals("queue_full", dropped.attrs["reason"])
    }

    @Test
    fun `gauge cell keeps the latest value`() {
        val aggregator = TelemetryAggregator()
        aggregator.record(SaturationGauge(TelemetryMetric.DB_SIZE_BYTES, 100.0))
        aggregator.record(SaturationGauge(TelemetryMetric.DB_SIZE_BYTES, 200.0))

        val snapshot = aggregator.snapshot()
        val gauge = snapshot.single { it.name == "kb_db_size_bytes" }
        assertEquals(200.0, gauge.value!!, 0.0)
        assertEquals(2L, aggregator.totalRecords)
    }

    @Test
    fun `stage histogram snapshot carries bounds counts and exact aggregates`() {
        val aggregator = TelemetryAggregator()
        aggregator.record(StageDuration(TelemetryStage.SQL_RECALL, TelemetryOutcome.OK, true, TelemetrySubject.MATH, TelemetryTrigger.TUTOR, 12))
        aggregator.record(StageDuration(TelemetryStage.SQL_RECALL, TelemetryOutcome.OK, true, TelemetrySubject.MATH, TelemetryTrigger.TUTOR, 30))

        val stage = aggregator.snapshot().single { it.name == "kb_stage_duration_ms" }

        assertEquals(TelemetryBucketBounds.STAGE_MS, stage.bounds)
        assertEquals((TelemetryBucketBounds.STAGE_MS.size + 1).toLong(), stage.counts!!.size.toLong())
        assertEquals(2L, stage.count)
        assertEquals(42L, stage.sum)
        assertEquals(12L, stage.min)
        assertEquals(30L, stage.max)
        assertEquals("sql_recall", stage.attrs["stage"])
        assertEquals("tutor", stage.attrs["trigger"])
    }
}
