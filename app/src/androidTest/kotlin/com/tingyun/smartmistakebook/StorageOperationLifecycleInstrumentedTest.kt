package com.tingyun.smartmistakebook

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class StorageOperationLifecycleInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun anInterruptedProcessDoesNotRestoreAPhantomBusyOperation() {
        compose.runOnUiThread {
            val state = SavedStateHandle(mapOf(StorageOperationsViewModel.RUNNING to true))
            val restored = StorageOperationsViewModel(state)
            assertFalse(restored.busy)
            assertTrue(restored.message.orEmpty().contains("中断"))
            assertEquals(false, state.get<Boolean>(StorageOperationsViewModel.RUNNING))
        }
    }

    @Test fun aRunningOperationSurvivesActivityRecreationExactlyOnce() {
        lateinit var original: StorageOperationsViewModel
        val finish = CompletableDeferred<Unit>()
        var executions = 0
        compose.runOnUiThread {
            original = ViewModelProvider(compose.activity)[StorageOperationsViewModel::class.java]
            original.busy = true
            original.operationScope.launch {
                executions++
                finish.await()
                original.message = "操作完成"
                original.busy = false
            }
        }
        compose.activityRule.scenario.recreate()
        compose.runOnUiThread {
            assertSame(original, ViewModelProvider(compose.activity)[StorageOperationsViewModel::class.java])
            assertTrue(original.busy)
            assertEquals(1, executions)
            finish.complete(Unit)
        }
        compose.waitUntil { !original.busy }
        assertEquals("操作完成", original.message)
    }
}
