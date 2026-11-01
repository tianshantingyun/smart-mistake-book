package com.tingyun.smartmistakebook.feature.tutor

import android.view.inputmethod.EditorInfo
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.tingyun.smartmistakebook.core.domain.ModelTaskRepository
import com.tingyun.smartmistakebook.core.domain.StudyProfileOverview
import com.tingyun.smartmistakebook.core.model.*
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TutorImeCommitIntegrationInstrumentedTest : CapturedTutorSessionTestBase() {
    @Test fun theSessionDispatchesTheJustCommittedBodyWithoutWaitingForAFrame() {
        val tasks = CommitTasks()
        val conversations = FakeTutorConversations()
        val question = session()
        val clock = AtomicLong(1000)
        composeRule.setContent {
            MaterialTheme {
                ReadyCapturedSession(session = question, clock = clock::getAndIncrement,
                    saveInProgress = false, saveError = null, onSave = {},
                    modelTasks = tasks, interactions = emptyInteractions(), conversations = conversations,
                    profile = StudyProfileOverview(), onOpenModelSettings = {})
            }
        }
        composeRule.waitUntil(10000) { tasks.snapshots.value.any { it.status == ModelTaskStatus.SUCCEEDED } }
        composeRule.onNodeWithTag("tutor_chat_composer").performClick()
        composeRule.runOnIdle {
            val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).single()
            val connection = checkNotNull(activity.window.decorView.findFocus()?.onCreateInputConnection(EditorInfo()))
            connection.setComposingText("你", 1)
            connection.finishComposingText()
            connection.performEditorAction(EditorInfo.IME_ACTION_SEND)
        }
        composeRule.waitUntil(5000) { tasks.requests.any { it.input is TutorRespondInput } }
        assertEquals("你", (tasks.requests.last().input as TutorRespondInput).studentMessage)
    }

    private inner class CommitTasks : ModelTaskRepository {
        val snapshots = MutableStateFlow<List<ModelTaskSnapshot>>(emptyList())
        val requests = mutableListOf<ModelTaskRequest>()
        private val provider = ProviderCapabilitySnapshot("ime-test", "输入回归", "test",
            setOf(ModelTaskKind.TUTOR_PLAN, ModelTaskKind.TUTOR_RESPOND), false, true, false,
            executionLocation = ModelExecutionLocation.LOCAL_NO_EGRESS)
        override suspend fun capabilities() = provider
        override fun observe(requestId: String) = snapshots.map { it.lastOrNull { row -> row.request.requestId == requestId } }
        override fun observeBySubject(subjectId: String, kind: ModelTaskKind) =
            snapshots.map { it.filter { row -> row.request.input.kind == kind } }
        override fun execute(request: ModelTaskRequest) = flow {
            requests += request
            val plan = request.input.kind == ModelTaskKind.TUTOR_PLAN
            val result = ModelTaskSnapshot("ime-task-${requests.size}", request, ModelTaskFingerprint.of(request),
                if (plan) ModelTaskStatus.SUCCEEDED else ModelTaskStatus.RUNNING, 1,
                if (plan) ModelTaskStage.COMPLETE else ModelTaskStage.PREPARING, "回归状态", 1, provider,
                output = if (plan) tutorOutput() else null,
                createdAtEpochMillis = request.occurredAtEpochMillis, updatedAtEpochMillis = request.occurredAtEpochMillis)
            snapshots.value += result
            emit(result)
        }
    }
}
