package com.tingyun.smartmistakebook.feature.tutor

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.domain.ModelTaskRepository
import com.tingyun.smartmistakebook.core.domain.RecordTutorChoiceCommand
import com.tingyun.smartmistakebook.core.domain.StudyProfileOverview
import com.tingyun.smartmistakebook.core.domain.TutorInteractionRepository
import com.tingyun.smartmistakebook.core.domain.TutorTurnResponse
import com.tingyun.smartmistakebook.core.model.ModelExecutionLocation
import com.tingyun.smartmistakebook.core.model.ModelFailureCode
import com.tingyun.smartmistakebook.core.model.ModelTaskFailure
import com.tingyun.smartmistakebook.core.model.ModelTaskFingerprint
import com.tingyun.smartmistakebook.core.model.ModelTaskKind
import com.tingyun.smartmistakebook.core.model.ModelTaskRequest
import com.tingyun.smartmistakebook.core.model.ModelTaskSnapshot
import com.tingyun.smartmistakebook.core.model.ModelTaskStage
import com.tingyun.smartmistakebook.core.model.ModelTaskStatus
import com.tingyun.smartmistakebook.core.model.ProviderCapabilitySnapshot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicLong

@RunWith(AndroidJUnit4::class)
class TutorCommandRecompositionInstrumentedTest : CapturedTutorSessionTestBase() {
    @Test
    fun retryUsesTheObservedAttemptInsteadOfTheFirstComposition() {
        val modelTasks = RecompositionModelTasks(ModelTaskStatus.PERMANENT_FAILURE)
        showSession(modelTasks)
        composeRule.waitUntil(10_000) {
            modelTasks.tasks.value.lastOrNull()?.status == ModelTaskStatus.PERMANENT_FAILURE
        }
        composeRule.onNodeWithTag("tutor_plan_retry").performScrollTo().performClick()
        composeRule.waitUntil(10_000) { modelTasks.requests.size == 2 }

        composeRule.runOnIdle {
            assertNotEquals(modelTasks.requests[0].requestId, modelTasks.requests[1].requestId)
            assertEquals("重试必须读取已观察到的首个任务", "1", modelTasks.requests[1].requestId.substringAfterLast(':'))
        }
    }

    @Test
    fun aPlanThatFinishesAfterCompositionCanPersistAChoice() {
        val modelTasks = RecompositionModelTasks(ModelTaskStatus.SUCCEEDED, holdPlan = true)
        val choices = mutableListOf<RecordTutorChoiceCommand>()
        val responses = MutableStateFlow<List<TutorTurnResponse>>(emptyList())
        val interactions = object : TutorInteractionRepository by emptyInteractions() {
            override fun observe(sessionId: String): Flow<List<TutorTurnResponse>> = responses

            override suspend fun recordChoice(command: RecordTutorChoiceCommand): TutorTurnResponse {
                choices += command
                return tutorResponse(command.selectedChoiceId).also { responses.value = listOf(it) }
            }
        }
        showSession(modelTasks, interactions)
        composeRule.waitUntil(10_000) {
            modelTasks.tasks.value.lastOrNull()?.status == ModelTaskStatus.RUNNING
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle { modelTasks.finishPlan.complete(Unit) }
        composeRule.waitUntil(10_000) {
            modelTasks.tasks.value.lastOrNull()?.status == ModelTaskStatus.SUCCEEDED
        }
        composeRule.onNodeWithTag("captured_tutor_choice_choice-2").performScrollTo().performClick()

        composeRule.runOnIdle {
            assertEquals("完成后的计划必须真正进入选择写入通道", 1, choices.size)
            assertEquals("choice-2", choices.single().selectedChoiceId)
            assertEquals("document-1", choices.single().questionDocumentId)
            assertEquals("choice-2", responses.value.single().selectedChoiceId)
        }
    }

    private fun showSession(
        modelTasks: ModelTaskRepository,
        interactions: TutorInteractionRepository = emptyInteractions(),
    ) {
        val session = session()
        val conversations = FakeTutorConversations()
        val clock = AtomicLong(1_000L)
        composeRule.setContent {
            MaterialTheme {
                Column {
                    ReadyCapturedSession(
                        session = session,
                        clock = clock::getAndIncrement,
                        saveInProgress = false,
                        saveError = null,
                        onSave = {},
                        modelTasks = modelTasks,
                        interactions = interactions,
                        conversations = conversations,
                        profile = StudyProfileOverview(),
                        onOpenModelSettings = {},
                    )
                }
            }
        }
    }

    private inner class RecompositionModelTasks(
        private val finalStatus: ModelTaskStatus,
        private val holdPlan: Boolean = false,
    ) : ModelTaskRepository {
        val tasks = MutableStateFlow<List<ModelTaskSnapshot>>(emptyList())
        val requests = mutableListOf<ModelTaskRequest>()
        val finishPlan = CompletableDeferred<Unit>()
        private val provider = ProviderCapabilitySnapshot(
            providerId = "recomposition-test",
            providerDisplayName = "重组回归模型",
            modelId = "tutor-model-v1",
            supportedTasks = setOf(ModelTaskKind.TUTOR_PLAN, ModelTaskKind.TUTOR_RESPOND),
            supportsImageInput = false,
            supportsStructuredOutput = true,
            supportsStreaming = false,
            executionLocation = ModelExecutionLocation.LOCAL_NO_EGRESS,
        )

        override suspend fun capabilities() = provider
        override fun observe(requestId: String) = tasks.map { snapshots ->
            snapshots.lastOrNull { it.request.requestId == requestId }
        }
        override fun observeBySubject(subjectId: String, kind: ModelTaskKind) = tasks.map { snapshots ->
            snapshots.filter { it.request.input.kind == kind }
        }

        override fun execute(request: ModelTaskRequest) = flow {
            requests += request
            val running = ModelTaskSnapshot(
                taskId = "recomposition-task-${requests.size}",
                request = request,
                requestFingerprint = ModelTaskFingerprint.of(request),
                status = ModelTaskStatus.RUNNING,
                stateVersion = 1,
                stage = ModelTaskStage.PREPARING,
                userMessage = "正在准备讲解",
                attemptCount = 1,
                provider = provider,
                createdAtEpochMillis = request.occurredAtEpochMillis,
                updatedAtEpochMillis = request.occurredAtEpochMillis,
            )
            tasks.value = tasks.value + running
            emit(running)
            if (holdPlan) finishPlan.await()
            val completed = running.copy(
                status = finalStatus,
                stateVersion = 2,
                stage = if (finalStatus == ModelTaskStatus.SUCCEEDED) ModelTaskStage.COMPLETE
                    else ModelTaskStage.VALIDATING_OUTPUT,
                output = tutorOutput().takeIf { finalStatus == ModelTaskStatus.SUCCEEDED },
                failure = ModelTaskFailure(
                    ModelFailureCode.INVALID_RESPONSE,
                    "测试模型返回无效内容",
                    retryable = false,
                ).takeUnless { finalStatus == ModelTaskStatus.SUCCEEDED },
            )
            tasks.value = tasks.value.map { if (it.taskId == completed.taskId) completed else it }
            emit(completed)
        }
    }
}
