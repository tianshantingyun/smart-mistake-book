package com.tingyun.smartmistakebook.core.data.settings

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.domain.FsrsParameterFit
import com.tingyun.smartmistakebook.core.domain.FsrsScheduleMath
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 校准可见页的持久化底座（批次 49 P2-12）：拟合记录（时间/样本/验证损失与默认基线）
 * 必须跨进程重启可读，否则页面的"最近拟合"只能显示"—"。
 *
 * 另一个被钉住的语义：`setOptimizedParameters` 的任何写入都会清掉旧拟合记录——
 * 重置参数、备份恢复都会走它。旧时点贴到一组新参数上，比没有记录更误导。
 */
@RunWith(AndroidJUnit4::class)
class SchedulingSettingsStoreInstrumentedTest {

    @Test
    fun fitRecordRoundTripsThroughTheDataStore() = runBlocking {
        val (context, scope) = isolatedContext()
        val store = DataStoreSchedulingSettingsStore(context, scope)
        val fit = FsrsParameterFit(
            adoptedAtEpochMillis = 1_760_000_000_000L,
            sampleCount = 1_234,
            validationLogLoss = 0.312,
            baselineValidationLogLoss = 0.355,
        )

        store.recordParameterFit(fit)

        assertEquals(fit, store.parameterFit.first())
    }

    @Test
    fun aFitRecordWithoutABaselineLossIsReadBackWithANullBaseline() = runBlocking {
        val (context, scope) = isolatedContext()
        val store = DataStoreSchedulingSettingsStore(context, scope)
        val fit = FsrsParameterFit(
            adoptedAtEpochMillis = 5L,
            sampleCount = 400,
            validationLogLoss = 0.4,
            baselineValidationLogLoss = null,
        )

        store.recordParameterFit(fit)

        val readBack = store.parameterFit.first()
        assertEquals(fit, readBack)
        assertNull(readBack?.baselineValidationLogLoss)
    }

    @Test
    fun writingParametersClearsTheStaleFitRecord() = runBlocking {
        val (context, scope) = isolatedContext()
        val store = DataStoreSchedulingSettingsStore(context, scope)
        store.recordParameterFit(
            FsrsParameterFit(
                adoptedAtEpochMillis = 1L,
                sampleCount = 400,
                validationLogLoss = 0.4,
                baselineValidationLogLoss = 0.5,
            ),
        )

        val restored = FsrsScheduleMath.DEFAULT_PARAMETERS.copyOf().also { it[4] = it[4] + 0.5 }
        store.setOptimizedParameters(restored)

        assertEquals(restored.toList(), store.optimizedParameters.first()?.toList())
        assertNull("导入/恢复的参数没有本地拟合记录", store.parameterFit.first())
    }

    @Test
    fun resettingParametersClearsBothTheParametersAndTheFitRecord() = runBlocking {
        val (context, scope) = isolatedContext()
        val store = DataStoreSchedulingSettingsStore(context, scope)
        store.setOptimizedParameters(FsrsScheduleMath.DEFAULT_PARAMETERS.copyOf().also { it[0] = 9.9 })
        store.recordParameterFit(
            FsrsParameterFit(
                adoptedAtEpochMillis = 2L,
                sampleCount = 500,
                validationLogLoss = 0.3,
                baselineValidationLogLoss = 0.4,
            ),
        )

        store.setOptimizedParameters(null)

        assertNull(store.optimizedParameters.first())
        assertNull(store.parameterFit.first())
    }

    private fun isolatedContext(): Pair<Context, CoroutineScope> {
        val base = ApplicationProvider.getApplicationContext<Context>()
        // DataStore 单文件单实例：每个用例独立 filesDir，避免跨用例/跨进程实例冲突。
        val root = File(base.cacheDir, "scheduling-settings-${UUID.randomUUID()}")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        return IsolatedStorageContext(base, root) to scope
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
