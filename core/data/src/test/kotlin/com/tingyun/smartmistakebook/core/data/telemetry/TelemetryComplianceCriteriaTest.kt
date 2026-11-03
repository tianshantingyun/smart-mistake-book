package com.tingyun.smartmistakebook.core.data.telemetry

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R5 §4.2 的 V1/V2/V4–V8/V11/V12 在**纯逻辑层**的落点；V3/V9/V10 与 V1/V2/V11 的源码扫描部分
 * 由 `tools/ci/check_telemetry_compliance.py` 提供（固定白名单路径的静态审计）。真实 DataStore
 * 持久化与文件路径由 `TelemetryConsentStoreInstrumentedTest` / `TelemetryRecorderInstrumentedTest`
 * 覆盖。证据边界见本轮 detail：本类不冒充设备抓包或 `aapt2` 产物检查。
 */
class TelemetryComplianceCriteriaTest {
    private val policy = TelemetryPolicy(version = "2026-11-01", hash = "sha256:test-policy")
    private val temporaryDirectories = mutableListOf<File>()

    @After
    fun tearDown() {
        temporaryDirectories.forEach { it.deleteRecursively() }
    }

    @Test
    fun `V1 fresh install defaults to UNKNOWN and the collection gate is closed`() {
        val consent = TelemetryConsentSnapshot()
        assertEquals(TelemetryConsentState.UNKNOWN, consent.state)
        assertFalse(consent.isEnabled)

        assertFalse(TelemetryGate.isCollectionAllowed(consent, TelemetryBuildConfig(), policy))
        assertFalse(
            TelemetryGate.isCollectionAllowed(
                consent,
                TelemetryBuildConfig(telemetryBuildEnabled = true),
                policy,
            ),
        )
    }

    @Test
    fun `V2 no consent means zero records and no telemetry directory`() {
        val directory = newDirectory("v2")
        val buffer = TelemetryBuffer(directory)
        val recorder = TelemetryRecorder(
            buffer = buffer,
            currentPolicy = policy,
            buildConfig = TelemetryBuildConfig(telemetryBuildEnabled = true),
        )
        var constructed = 0

        val accepted = recorder.record {
            constructed += 1
            StageDuration(TelemetryStage.SQL_RECALL, TelemetryOutcome.OK, true, TelemetrySubject.MATH, TelemetryTrigger.TUTOR, 12)
        }

        assertFalse(accepted)
        assertEquals("the event lambda must not run while the gate is closed", 0, constructed)
        assertEquals(0L, recorder.recordedSamples)
        assertFalse(directory.exists())
        assertFalse(buffer.hasFiles())
    }

    @Test
    fun `V4 grant enables collection and the first batch is written only after consent`() {
        val directory = newDirectory("v4")
        val recorder = recorder(directory)
        recorder.onConsentChanged(grantedConsent())

        assertTrue(recorder.record(QueryStarted(TelemetryTrigger.TUTOR, TelemetrySubject.MATH, kbReady = true)))
        assertEquals(1L, recorder.recordedSamples)
        assertTrue(recorder.flush(metadata("b_v4")))
        assertTrue(TelemetryBuffer(directory).hasFiles())
        assertEquals(1, TelemetryBuffer(directory).readAll().size)
    }

    @Test
    fun `V5 withdraw stops collection immediately and purges local buffer`() {
        val directory = newDirectory("v5")
        val recorder = recorder(directory)
        recorder.onConsentChanged(grantedConsent())
        recorder.record(QueryStarted(TelemetryTrigger.TUTOR, TelemetrySubject.MATH, kbReady = true))
        assertTrue(recorder.flush(metadata("b_v5")))
        assertTrue(TelemetryBuffer(directory).hasFiles())

        recorder.onConsentChanged(
            grantedConsent().copy(
                state = TelemetryConsentState.WITHDRAWN,
                withdrawnAtEpochMillis = 2_000L,
            ),
        )

        assertFalse(recorder.isCollectionAllowed())
        assertEquals(0L, recorder.recordedSamples)
        assertFalse(TelemetryBuffer(directory).hasFiles())
        assertFalse(directory.exists())
        assertFalse(recorder.record(QueryStarted(TelemetryTrigger.TUTOR, TelemetrySubject.MATH, kbReady = true)))
        assertEquals(0L, recorder.recordedSamples)
    }

    @Test
    fun `V6 under14 without guardian is blocked and undisclosed age fails closed`() {
        val under14 = grantedConsent().copy(ageBand = TelemetryAgeBand.UNDER_14, guardianConfirmed = false)
        val under14WithGuardian = under14.copy(guardianConfirmed = true)
        val teenWithoutGuardian = grantedConsent().copy(ageBand = TelemetryAgeBand.AGE_14_17, guardianConfirmed = false)

        assertFalse(TelemetryGate.isCollectionAllowed(under14, enabledBuild(), policy))
        assertTrue(TelemetryGate.isCollectionAllowed(under14WithGuardian, enabledBuild(), policy))
        assertFalse(TelemetryGate.isCollectionAllowed(teenWithoutGuardian, enabledBuild(), policy))
        assertTrue(
            TelemetryGate.isCollectionAllowed(
                teenWithoutGuardian.copy(guardianConfirmed = true),
                enabledBuild(),
                policy,
            ),
        )
        assertFalse(
            TelemetryGate.isCollectionAllowed(
                grantedConsent().copy(ageBand = TelemetryAgeBand.UNDISCLOSED),
                enabledBuild(),
                policy,
            ),
        )
        assertFalse(TelemetryGate.isCollectionAllowed(grantedConsent().copy(ageBand = null), enabledBuild(), policy))
    }

    @Test
    fun `V7 payload contains no free text and only whitelisted attribute keys`() {
        val directory = newDirectory("v7")
        val recorder = recorder(directory)
        recorder.onConsentChanged(grantedConsent())
        recorder.record(StageDuration(TelemetryStage.SQL_RECALL, TelemetryOutcome.OK, true, TelemetrySubject.MATH, TelemetryTrigger.TUTOR, 12))
        recorder.record(QueryStarted(TelemetryTrigger.TUTOR, TelemetrySubject.MATH, kbReady = true))
        recorder.record(NoRelevantHit(TelemetrySubject.MATH, TelemetryTrigger.TUTOR))
        recorder.record(FallbackToTopic(TelemetryTrigger.TUTOR))
        recorder.record(EmptyContext(TelemetryReason.NO_CANDIDATES, TelemetryTrigger.TUTOR))
        recorder.record(QueryError(TelemetryStage.RERANK, TelemetryErrorClass.SQLITE, retryable = false))
        recorder.record(RecallCandidates(TelemetrySubject.MATH, TelemetryTrigger.TUTOR, 37))
        assertTrue(recorder.flush(metadata("b_v7")))

        val raw = TelemetryBuffer(directory).readAll().single()
        val batch = TelemetryJson.decode(raw)
        for (metric in batch.metrics) {
            assertTrue(
                "unknown attrs on ${metric.name}: ${metric.attrs.keys}",
                metric.attrs.keys.all { it in TelemetryAttributes.ALLOWED_KEYS },
            )
        }
        for (fragment in FORBIDDEN_PAYLOAD_FRAGMENTS) {
            assertFalse("payload leaked '$fragment'", raw.contains(fragment))
        }
    }

    @Test
    fun `V8 buffer is bounded and drops the oldest entries`() {
        val directory = newDirectory("v8")
        val buffer = TelemetryBuffer(
            directory = directory,
            maxFiles = 2,
            maxFileBytes = 64,
            maxEntries = 3,
        )

        listOf("first", "second", "third", "fourth").forEach { assertTrue(buffer.append(it)) }

        assertEquals(listOf("second", "third", "fourth"), buffer.readAll())
        assertEquals(3, buffer.entryCount())
        assertEquals(6, TelemetryBuffer.DEFAULT_MAX_FILES)
        assertEquals(64L * 1024L, TelemetryBuffer.DEFAULT_MAX_FILE_BYTES)
        assertEquals(300, TelemetryBuffer.DEFAULT_MAX_ENTRIES)
    }

    @Test
    fun `V11 consent snapshot carries grant withdraw policy and age audit fields`() {
        val consent = grantedConsent().copy(withdrawnAtEpochMillis = 2_000L)

        assertEquals(TelemetryConsentState.GRANTED, consent.state)
        assertEquals("2026-11-01", consent.policyVersion)
        assertEquals("sha256:test-policy", consent.policyHash)
        assertEquals(1_000L, consent.grantedAtEpochMillis)
        assertEquals(2_000L, consent.withdrawnAtEpochMillis)
        assertEquals(TelemetryAgeBand.AGE_18_PLUS, consent.ageBand)
        assertFalse(consent.guardianConfirmed)
    }

    @Test
    fun `V12 v1 upload gate stays closed without an endpoint`() {
        assertFalse(
            TelemetryGate.isUploadAllowed(grantedConsent(), enabledBuild(), policy, hasInternetPermission = true),
        )
        assertFalse(
            TelemetryGate.isUploadAllowed(
                grantedConsent(),
                TelemetryBuildConfig(telemetryBuildEnabled = true, endpoint = "https://telemetry.example.invalid"),
                policy,
                hasInternetPermission = false,
            ),
        )
        assertTrue(
            TelemetryGate.isUploadAllowed(
                grantedConsent(),
                TelemetryBuildConfig(telemetryBuildEnabled = true, endpoint = "https://telemetry.example.invalid"),
                policy,
                hasInternetPermission = true,
            ),
        )
    }

    private fun recorder(directory: File): TelemetryRecorder = TelemetryRecorder(
        buffer = TelemetryBuffer(directory),
        currentPolicy = policy,
        buildConfig = enabledBuild(),
    )

    private fun grantedConsent(): TelemetryConsentSnapshot = TelemetryConsentSnapshot(
        state = TelemetryConsentState.GRANTED,
        policyVersion = policy.version,
        policyHash = policy.hash,
        grantedAtEpochMillis = 1_000L,
        ageBand = TelemetryAgeBand.AGE_18_PLUS,
    )

    private fun enabledBuild(): TelemetryBuildConfig = TelemetryBuildConfig(telemetryBuildEnabled = true)

    private fun metadata(batchId: String): TelemetryBatchMetadata = TelemetryBatchMetadata(
        batchId = batchId,
        windowStartUtc = "2026-11-02T10:00:00Z",
        windowEndUtc = "2026-11-02T11:00:00Z",
        appVersionName = "0.2.0",
        appVersionCode = 2L,
        flavor = TelemetryFlavor.LOCAL_FIRST,
        apiLevel = 34,
        ramClass = TelemetryRamClass.MID,
        samplingRate = 0.1,
    )

    private fun newDirectory(name: String): File {
        val directory = File(Files.createTempDirectory("smbk-telemetry-$name").toFile(), "telemetry")
        temporaryDirectories += directory.parentFile
        return directory
    }

    private companion object {
        val FORBIDDEN_PAYLOAD_FRAGMENTS = listOf(
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
