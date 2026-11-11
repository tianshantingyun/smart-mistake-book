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
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/**
 * V1/V5/V11 的 DataStore 持久化证据：首次安装默认 UNKNOWN；grant 落政策与审计字段；
 * 撤回落 withdrawn_at；政策变更回 UNKNOWN；<14 无监护人拒绝 grant。
 */
@RunWith(AndroidJUnit4::class)
class TelemetryConsentStoreInstrumentedTest {
    private val policy = TelemetryPolicy(version = "2026-11-01", hash = "sha256:instrumented-policy")

    @Test
    fun freshInstall_defaultsToUnknownAndGateClosed() = runBlocking {
        val context = isolatedContext("consent-fresh")
        val job = SupervisorJob()
        val store = TelemetryConsentStore(context, CoroutineScope(job + Dispatchers.IO))
        try {
            val consent = store.current()

            assertEquals(TelemetryConsentState.UNKNOWN, consent.state)
            assertFalse(consent.isEnabled)
            assertEquals(null, consent.policyVersion)
            assertEquals(null, consent.policyHash)
            assertEquals(null, consent.grantedAtEpochMillis)
            assertEquals(null, consent.withdrawnAtEpochMillis)
            assertFalse(TelemetryGate.isCollectionAllowed(consent, TelemetryBuildConfig(telemetryBuildEnabled = true), policy))
        } finally {
            job.cancelAndJoin()
        }
    }

    @Test
    fun grant_persistsPolicyAndAuditFieldsAcrossStoreRecreation() = runBlocking {
        val context = isolatedContext("consent-grant")
        val firstJob = SupervisorJob()
        val first = TelemetryConsentStore(context, CoroutineScope(firstJob + Dispatchers.IO))
        first.grant(policy, TelemetryAgeBand.AGE_18_PLUS, guardianConfirmed = false, grantedAtEpochMillis = 1_234L)
        firstJob.cancelAndJoin()

        val secondJob = SupervisorJob()
        val second = TelemetryConsentStore(context, CoroutineScope(secondJob + Dispatchers.IO))
        try {
            val restored = second.current()
            assertEquals(TelemetryConsentState.GRANTED, restored.state)
            assertTrue(restored.isEnabled)
            assertEquals(policy.version, restored.policyVersion)
            assertEquals(policy.hash, restored.policyHash)
            assertEquals(1_234L, restored.grantedAtEpochMillis)
            assertEquals(TelemetryAgeBand.AGE_18_PLUS, restored.ageBand)
            assertFalse(restored.guardianConfirmed)
            assertTrue(TelemetryGate.isCollectionAllowed(restored, TelemetryBuildConfig(telemetryBuildEnabled = true), policy))
        } finally {
            secondJob.cancelAndJoin()
        }
    }

    @Test
    fun withdraw_persistsWithdrawnStateAndTimestamp() = runBlocking {
        val context = isolatedContext("consent-withdraw")
        val job = SupervisorJob()
        val store = TelemetryConsentStore(context, CoroutineScope(job + Dispatchers.IO))
        try {
            store.grant(policy, TelemetryAgeBand.AGE_18_PLUS, guardianConfirmed = false, grantedAtEpochMillis = 1_000L)
            val withdrawn = store.withdraw(withdrawnAtEpochMillis = 2_000L)

            assertEquals(TelemetryConsentState.WITHDRAWN, withdrawn.state)
            assertFalse(withdrawn.isEnabled)
            assertEquals(1_000L, withdrawn.grantedAtEpochMillis)
            assertEquals(2_000L, withdrawn.withdrawnAtEpochMillis)
            assertFalse(TelemetryGate.isCollectionAllowed(withdrawn, TelemetryBuildConfig(telemetryBuildEnabled = true), policy))
        } finally {
            job.cancelAndJoin()
        }
    }

    @Test
    fun policyChange_invalidatesOldConsent() = runBlocking {
        val context = isolatedContext("consent-policy-change")
        val job = SupervisorJob()
        val store = TelemetryConsentStore(context, CoroutineScope(job + Dispatchers.IO))
        try {
            store.grant(policy, TelemetryAgeBand.AGE_18_PLUS, guardianConfirmed = false)
            assertTrue(store.current().isEnabled)

            val invalidated = store.invalidateForPolicyChange()

            assertEquals(TelemetryConsentState.UNKNOWN, invalidated.state)
            assertFalse(invalidated.isEnabled)
            assertFalse(TelemetryGate.isCollectionAllowed(invalidated, TelemetryBuildConfig(telemetryBuildEnabled = true), policy))
        } finally {
            job.cancelAndJoin()
        }
    }

    @Test
    fun under14WithoutGuardian_grantIsRejectedAndStateStaysUnknown() = runBlocking {
        val context = isolatedContext("consent-under14")
        val job = SupervisorJob()
        val store = TelemetryConsentStore(context, CoroutineScope(job + Dispatchers.IO))
        try {
            try {
                store.grant(policy, TelemetryAgeBand.UNDER_14, guardianConfirmed = false)
                fail("grant must be rejected for under-14 without guardian confirmation")
            } catch (_: IllegalArgumentException) {
                // expected
            }

            assertEquals(TelemetryConsentState.UNKNOWN, store.current().state)
        } finally {
            job.cancelAndJoin()
        }
    }

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
