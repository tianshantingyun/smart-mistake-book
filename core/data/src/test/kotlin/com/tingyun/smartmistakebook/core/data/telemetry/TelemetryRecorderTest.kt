package com.tingyun.smartmistakebook.core.data.telemetry

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TelemetryRecorderTest {
    private lateinit var root: File
    private val policy = TelemetryPolicy(version = "2026-11-01", hash = "sha256:test-policy")

    @Before
    fun setUp() {
        root = Files.createTempDirectory("smbk-telemetry-recorder").toFile()
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun `unknown cached consent at startup does not purge an existing buffer`() {
        val directory = File(root, "telemetry")
        val recorder = recorder(directory)
        recorder.onConsentChanged(granted())
        recorder.record(QueryStarted(TelemetryTrigger.TUTOR, TelemetrySubject.MATH, kbReady = true))
        assertTrue(recorder.flush(metadata()))

        // 新进程里 DataStore 还没 refresh 时，缓存是 UNKNOWN；这不是“用户撤回”。
        recorder.onConsentChanged(TelemetryConsentSnapshot())

        assertFalse(recorder.isCollectionAllowed())
        assertTrue("startup UNKNOWN must not delete a consenting user's buffered batch", TelemetryBuffer(directory).hasFiles())
    }

    @Test
    fun `policy change while granted purges stale local data`() {
        val directory = File(root, "telemetry")
        val recorder = recorder(directory)
        recorder.onConsentChanged(granted())
        recorder.record(QueryStarted(TelemetryTrigger.TUTOR, TelemetrySubject.MATH, kbReady = true))
        assertTrue(recorder.flush(metadata()))

        recorder.onConsentChanged(granted().copy(policyHash = "sha256:new-policy"))

        assertFalse(recorder.isCollectionAllowed())
        assertFalse(TelemetryBuffer(directory).hasFiles())
    }

    @Test
    fun `withdraw purges and blocks later records`() {
        val directory = File(root, "telemetry")
        val recorder = recorder(directory)
        recorder.onConsentChanged(granted())
        recorder.record(QueryStarted(TelemetryTrigger.TUTOR, TelemetrySubject.MATH, kbReady = true))
        assertTrue(recorder.flush(metadata()))

        recorder.onConsentChanged(granted().copy(state = TelemetryConsentState.WITHDRAWN))

        assertFalse(TelemetryBuffer(directory).hasFiles())
        assertFalse(recorder.record(QueryStarted(TelemetryTrigger.TUTOR, TelemetrySubject.MATH, kbReady = true)))
    }

    private fun recorder(directory: File): TelemetryRecorder = TelemetryRecorder(
        buffer = TelemetryBuffer(directory),
        currentPolicy = policy,
        buildConfig = TelemetryBuildConfig(telemetryBuildEnabled = true),
    )

    private fun granted(): TelemetryConsentSnapshot = TelemetryConsentSnapshot(
        state = TelemetryConsentState.GRANTED,
        policyVersion = policy.version,
        policyHash = policy.hash,
        grantedAtEpochMillis = 1_000L,
        ageBand = TelemetryAgeBand.AGE_18_PLUS,
    )

    private fun metadata(): TelemetryBatchMetadata = TelemetryBatchMetadata(
        batchId = "b_recorder",
        windowStartUtc = "2026-11-02T10:00:00Z",
        windowEndUtc = "2026-11-02T11:00:00Z",
        appVersionName = "0.2.0",
        appVersionCode = 2L,
        flavor = TelemetryFlavor.LOCAL_FIRST,
        apiLevel = 34,
        ramClass = TelemetryRamClass.MID,
        samplingRate = 0.1,
    )
}
