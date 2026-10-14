package com.tingyun.smartmistakebook.feature.tutor

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.domain.ModelTaskRepository
import com.tingyun.smartmistakebook.core.domain.StudyProfileOverview
import com.tingyun.smartmistakebook.core.model.ImmutablePayloadConflict
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicLong

/**
 * 计划失败卡上的「重试」（KD-35）：**不许再把进程带走**。
 *
 * 现场：首轮讲解以 `PERMANENT_FAILURE / INVALID_RESPONSE` 收场，学生点失败卡上唯一的出口
 * 「重试」→ 进程消失（`ImmutablePayloadConflictException: model_task tutor-plan:… already
 * exists with a different payload`）。两条各自独立的路都要钉住：
 *
 * 1. **重试换到了新身份**（尾缀 attempt 递增）⇒ 写入路径正常落**新的一行**，这一轮照常继续
 *    （[aRetriedPlanFailureGetsANewIdentityAndANewTaskRow]）。
 * 2. 万一还是撞上"同标识、不同载荷"（例如并发重试）⇒ 冲突被接住、变成输入区上方那句可见的
 *    交代，**进程照旧活着**，出口还在（[aRefusedRetryShowsAVisibleFailureAndKeepsTheRetryOutlet]）。
 *
 * 这里的替身仓库带一个最小账本：同一标识第二次带**不同载荷**进来就按真实写入路径拒绝
 * （`ModelTaskTransactionDao.kt:233`），所以第 1 条用例会因"尝试次数被冻住"而失败——它同时是
 * 身份的钉子与崩溃的钉子。
 */
@RunWith(AndroidJUnit4::class)
class TutorPlanReplayRetryInstrumentedTest : CapturedTutorSessionTestBase() {
    @Test
    fun aRetriedPlanFailureGetsANewIdentityAndANewTaskRow() {
        val modelTasks = PlanLedgerModelTasks()
        showSession(modelTasks)
        composeRule.waitUntil(10_000) {
            modelTasks.tasks.value.lastOrNull()?.status == ModelTaskStatus.PERMANENT_FAILURE
        }

        composeRule.onNodeWithTag("tutor_plan_retry").performScrollTo().performClick()
        composeRule.waitUntil(10_000) { modelTasks.dispatches.size == 2 }

        composeRule.runOnIdle {
            assertNotEquals(
                "重试必须换到新标识（否则写入路径按同标识不同载荷拒绝）",
                modelTasks.dispatches[0].requestId,
                modelTasks.dispatches[1].requestId,
            )
            assertEquals("重试要真的产生新的一行", 2, modelTasks.tasks.value.size)
        }
    }

    @Test
    fun aRefusedRetryShowsAVisibleFailureAndKeepsTheRetryOutlet() {
        val modelTasks = PlanLedgerModelTasks(rejectEveryDispatchAfterTheFirst = true)
        showSession(modelTasks)
        composeRule.waitUntil(10_000) { modelTasks.tasks.value.isNotEmpty() }

        composeRule.onNodeWithTag("tutor_plan_retry").performScrollTo().performClick()
        composeRule.waitUntil(10_000) { modelTasks.refusals == 1 }
        composeRule.waitForIdle()

        // 冲突变成了学生看得见的一句话（输入区上方），而不是一次进程消失。
        composeRule.onNodeWithTag("tutor_plan_start_error")
            .assertTextEquals(TUTOR_PLAN_REPLAY_CONFLICT_MESSAGE)
        // 出口还在：再试一次就是这座界面给出的唯一动作。
        composeRule.onNodeWithTag("tutor_plan_retry").assertExists()
    }

    private fun showSession(modelTasks: ModelTaskRepository) {
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
                        interactions = emptyInteractions(),
                        conversations = conversations,
                        profile = StudyProfileOverview(),
                        onOpenModelSettings = {},
                    )
                }
            }
        }
    }

    /**
     * 带最小账本的计划任务替身：**同标识、不同载荷 = 拒绝**（与真实写入路径同一条纪律）。
     *
     * [rejectEveryDispatchAfterTheFirst] 用来把"残余冲突"这条路单独走一遍：第二次派发无论
     * 标识如何都被拒，界面必须把它变成可见的失败。
     */
    private inner class PlanLedgerModelTasks(
        private val rejectEveryDispatchAfterTheFirst: Boolean = false,
    ) : ModelTaskRepository {
        val tasks = MutableStateFlow<List<ModelTaskSnapshot>>(emptyList())
        val dispatches = mutableListOf<ModelTaskRequest>()
        var refusals: Int = 0

        private val fingerprintByRequestId = mutableMapOf<String, String>()
        private val provider = ProviderCapabilitySnapshot(
            providerId = "replay-retry-test",
            providerDisplayName = "重试回归模型",
            modelId = "tutor-model-v1",
            supportedTasks = setOf(ModelTaskKind.TUTOR_PLAN, ModelTaskKind.TUTOR_RESPOND),
            supportsImageInput = false,
            supportsStructuredOutput = true,
            supportsStreaming = false,
            executionLocation = ModelExecutionLocation.LOCAL_NO_EGRESS,
            providerConfigurationVersion = "replay-retry-v1",
        )

        override suspend fun capabilities(): ProviderCapabilitySnapshot = provider

        override fun observe(requestId: String): Flow<ModelTaskSnapshot?> = tasks.map { snapshots ->
            snapshots.lastOrNull { snapshot -> snapshot.request.requestId == requestId }
        }

        override fun observeBySubject(
            subjectId: String,
            kind: ModelTaskKind,
        ): Flow<List<ModelTaskSnapshot>> = tasks.map { snapshots ->
            snapshots.filter { snapshot -> snapshot.request.input.kind == kind }
        }

        override fun execute(request: ModelTaskRequest): Flow<ModelTaskSnapshot> = flow {
            dispatches += request
            val fingerprint = ModelTaskFingerprint.of(request)
            val stored = fingerprintByRequestId[request.requestId]
            val refused = (stored != null && stored != fingerprint) ||
                (rejectEveryDispatchAfterTheFirst && dispatches.size > 1)
            if (refused) {
                refusals += 1
                throw PlanReplayConflict(request.requestId)
            }
            fingerprintByRequestId[request.requestId] = fingerprint

            val running = ModelTaskSnapshot(
                taskId = "replay-retry-task-${dispatches.size}",
                request = request,
                requestFingerprint = fingerprint,
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

            val failed = running.copy(
                status = ModelTaskStatus.PERMANENT_FAILURE,
                stateVersion = 2,
                stage = ModelTaskStage.VALIDATING_OUTPUT,
                userMessage = "这次没有准备好题面，请重新处理",
                failure = ModelTaskFailure(
                    code = ModelFailureCode.INVALID_RESPONSE,
                    message = "这次没有准备好题面，请重新处理",
                    retryable = false,
                ),
                updatedAtEpochMillis = request.occurredAtEpochMillis + 1,
            )
            tasks.value = tasks.value.map { snapshot ->
                if (snapshot.taskId == failed.taskId) failed else snapshot
            }
            emit(failed)
        }
    }

    /** 与 `core:database` 抛出的那一个冲突实现同一个跨层标记。 */
    private class PlanReplayConflict(requestId: String) : IllegalStateException(
        "model_task $requestId already exists with a different payload",
    ), ImmutablePayloadConflict
}
