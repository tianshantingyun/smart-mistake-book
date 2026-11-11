package com.tingyun.smartmistakebook.core.data.telemetry

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * V2/V4/V5 的设备侧证据：同意前 `filesDir/telemetry` 不存在；grant 后 record+flush 才出现
 * 一个批次文件；withdraw 后文件被删除且后续 record 不再进入聚合器。
 */
@RunWith(AndroidJUnit4::class)
class TelemetryRecorderInstrumentedTest {
    private val policy = TelemetryPolicy(version = "2026-11-01", hash = "sha256:instrumented-policy")

    @Test
    fun noConsent_doesNotCreateTelemetryDirectoryOrRecord() = runBlocking {
        val context = isolatedContext("recorder-fresh")
        val job = SupervisorJob()
        val store = TelemetryConsentStore(context, CoroutineScope(job + Dispatchers.IO))
        try {
            val directory = File(context.filesDir, "telemetry")
            val recorder = recorder(directory)
            recorder.onConsentChanged(store.current())
            var constructed = 0

            val accepted = recorder.record {
                constructed += 1
                StageDuration(TelemetryStage.SQL_RECALL, TelemetryOutcome.OK, true, TelemetrySubject.MATH, TelemetryTrigger.TUTOR, 9)
            }

            assertFalse(accepted)
            assertEquals(0, constructed)
            assertEquals(0L, recorder.recordedSamples)
            assertFalse(directory.exists())
        } finally {
            job.cancelAndJoin()
        }
    }

    @Test
    fun grant_thenRecordAndFlush_writesExactlyOneDecodableBatch() = runBlocking {
        val context = isolatedContext("recorder-grant")
        val job = SupervisorJob()
        val store = TelemetryConsentStore(context, CoroutineScope(job + Dispatchers.IO))
        try {
            store.grant(policy, TelemetryAgeBand.AGE_18_PLUS, guardianConfirmed = false, grantedAtEpochMillis = 1L)
            val directory = File(context.filesDir, "telemetry")
            val recorder = recorder(directory)
            recorder.onConsentChanged(store.current())

            assertTrue(recorder.record(QueryDuration(TelemetryTrigger.TUTOR, TelemetryOutcome.OK, 18)))
            assertTrue(recorder.record(NoRelevantHit(TelemetrySubject.MATH, TelemetryTrigger.TUTOR)))
            assertTrue(recorder.flush(metadata("b_instrumented_grant")))

            val buffer = TelemetryBuffer(directory)
            val lines = buffer.readAll()
            assertEquals(1, lines.size)
            val batch = TelemetryJson.decode(lines.single())
            assertEquals("b_instrumented_grant", batch.batchId)
            assertEquals(policy.version, batch.consent.policyVersion)
            assertEquals(policy.hash, batch.consent.policyHash)
            assertTrue(batch.metrics.any { it.name == "kb_query_duration_ms" })
            assertTrue(batch.metrics.any { it.name == "kb_query_no_relevant_hit_total" })
        } finally {
            job.cancelAndJoin()
        }
    }

    @Test
    fun withdraw_purgesBufferAndStopsFurtherRecording() = runBlocking {
        val context = isolatedContext("recorder-withdraw")
        val job = SupervisorJob()
        val store = TelemetryConsentStore(context, CoroutineScope(job + Dispatchers.IO))
        try {
            store.grant(policy, TelemetryAgeBand.AGE_18_PLUS, guardianConfirmed = false)
            val directory = File(context.filesDir, "telemetry")
            val recorder = recorder(directory)
            recorder.onConsentChanged(store.current())
            recorder.record(QueryStarted(TelemetryTrigger.TUTOR, TelemetrySubject.MATH, kbReady = true))
            assertTrue(recorder.flush(metadata("b_instrumented_withdraw")))
            assertTrue(TelemetryBuffer(directory).hasFiles())

            store.withdraw(withdrawnAtEpochMillis = 9_999L)
            recorder.onConsentChanged(store.current())

            assertFalse(recorder.isCollectionAllowed())
            assertFalse(TelemetryBuffer(directory).hasFiles())
            assertFalse(directory.exists())
            assertFalse(recorder.record(QueryStarted(TelemetryTrigger.TUTOR, TelemetrySubject.MATH, kbReady = true)))
            assertEquals(0L, recorder.recordedSamples)
        } finally {
            job.cancelAndJoin()
        }
    }

    private fun recorder(directory: File): TelemetryRecorder = TelemetryRecorder(
        buffer = TelemetryBuffer(directory),
        currentPolicy = policy,
        buildConfig = TelemetryBuildConfig(telemetryBuildEnabled = true),
    )

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

    private fun isolatedContext(name: String): Context {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val root = File(base.cacheDir, "telemetry-tests/$name-${UUID.randomUUID()}")
        return IsolatedStorageContext(base, root)
    }

    private class IsolatedStorageContext(
        base: Context,
        private val root: File,
    ) : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this

        override fun getFilesDir(): File = File(root, "files").also { directory ->
            check(directory.isDirectory || directory.mkdirs())
        }

        override fun getNoBackupFilesDir(): File = File(root, "no-backup").also { directory ->
            check(directory.isDirectory || directory.mkdirs())
        }
    }
}
