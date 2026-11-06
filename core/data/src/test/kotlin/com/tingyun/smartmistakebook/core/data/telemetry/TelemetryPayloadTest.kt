package com.tingyun.smartmistakebook.core.data.telemetry

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class TelemetryPayloadTest {
    private lateinit var root: File
    private lateinit var buffer: TelemetryBuffer
    private val policy = TelemetryPolicy(version = "2026-11-01", hash = "sha256:test-policy")

    @Before
    fun setUp() {
        root = Files.createTempDirectory("smbk-telemetry-payload").toFile()
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun `batch serializes only whitelisted fields and enum attributes`() {
        val recorder = recorder()
        recorder.onConsentChanged(grantedConsent())
        recorder.record(StageDuration(TelemetryStage.SQL_RECALL, TelemetryOutcome.OK, true, TelemetrySubject.MATH, TelemetryTrigger.TUTOR, 12))
        recorder.record(QueryStarted(TelemetryTrigger.TUTOR, TelemetrySubject.MATH, kbReady = true))
        recorder.record(QueryDuration(TelemetryTrigger.TUTOR, TelemetryOutcome.OK, 18))
        recorder.record(NoRelevantHit(TelemetrySubject.MATH, TelemetryTrigger.TUTOR))
        recorder.record(RecallCandidates(TelemetrySubject.MATH, TelemetryTrigger.TUTOR, 37))
        recorder.record(SaturationGauge(TelemetryMetric.DB_SIZE_BYTES, 4_096.0))

        assertTrue(recorder.flush(metadata()))
        val raw = buffer.readAll().single()
        val batch = TelemetryJson.decode(raw)

        assertEquals(1, batch.schema)
        assertEquals("b_test", batch.batchId)
        assertEquals("product_metrics", batch.consent.scope)
        assertTrue(batch.metrics.any { it.name == "kb_stage_duration_ms" })
        assertTrue(batch.metrics.any { it.name == "kb_query_total" })
        assertTrue(batch.metrics.any { it.name == "kb_db_size_bytes" })

        for (metric in batch.metrics) {
            assertTrue(
                "unknown attrs on ${metric.name}: ${metric.attrs.keys}",
                metric.attrs.keys.all { it in TelemetryAttributes.ALLOWED_KEYS },
            )
        }

        for (forbidden in FORBIDDEN_FRAGMENTS) {
            assertFalse("payload leaked '$forbidden': $raw", raw.contains(forbidden))
        }
        assertEquals(batch, TelemetryJson.decode(raw))
    }

    @Test
    fun `unknown attribute keys are rejected before they can be serialized`() {
        try {
            TelemetryMetricSnapshot(
                name = "kb_query_total",
                type = "counter",
                unit = "count",
                value = 1.0,
                attrs = mapOf("question_text" to "a student wrote this"),
            )
            fail("free-text attribute key must be rejected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("unknown telemetry attribute keys"))
        }
    }

    @Test
    fun `histogram snapshot count must equal the sum of its buckets`() {
        try {
            TelemetryMetricSnapshot(
                name = "kb_stage_duration_ms",
                type = "histogram",
                unit = "ms",
                bounds = listOf(0L, 10L),
                counts = listOf(1L, 0L, 0L),
                count = 2L,
                sum = 5L,
                min = 0L,
                max = 5L,
            )
            fail("inconsistent histogram must be rejected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("count must equal the sum"))
        }
    }

    @Test
    fun `metadata rejects out-of-range sampling and blank identity fields`() {
        assertThrowsIllegalArgument {
            metadata().copy(samplingRate = 1.1)
        }
        assertThrowsIllegalArgument {
            metadata().copy(batchId = " ")
        }
        assertThrowsIllegalArgument {
            metadata().copy(apiLevel = 0)
        }
    }

    private fun recorder(): TelemetryRecorder {
        buffer = TelemetryBuffer(File(root, "telemetry"))
        return TelemetryRecorder(
            buffer = buffer,
            currentPolicy = policy,
            buildConfig = TelemetryBuildConfig(telemetryBuildEnabled = true),
        )
    }

    private fun grantedConsent(): TelemetryConsentSnapshot = TelemetryConsentSnapshot(
        state = TelemetryConsentState.GRANTED,
        policyVersion = policy.version,
        policyHash = policy.hash,
        grantedAtEpochMillis = 1_000L,
        ageBand = TelemetryAgeBand.AGE_18_PLUS,
    )

    private fun metadata(): TelemetryBatchMetadata = TelemetryBatchMetadata(
        batchId = "b_test",
        windowStartUtc = "2026-11-02T10:00:00Z",
        windowEndUtc = "2026-11-02T11:00:00Z",
        appVersionName = "0.2.0",
        appVersionCode = 2L,
        flavor = TelemetryFlavor.LOCAL_FIRST,
        apiLevel = 34,
        ramClass = TelemetryRamClass.MID,
        samplingRate = 0.1,
    )

    private fun assertThrowsIllegalArgument(block: () -> Unit) {
        try {
            block()
            fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    private companion object {
        val FORBIDDEN_FRAGMENTS = listOf(
            "question",
            "node_id",
            "nodeId",
            "android_id",
            "androidId",
            "file_path",
            "filePath",
            "phone",
            "stack_trace",
            "stackTrace",
        )
    }
}
