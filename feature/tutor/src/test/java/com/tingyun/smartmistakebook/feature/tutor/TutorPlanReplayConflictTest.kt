package com.tingyun.smartmistakebook.feature.tutor

import com.tingyun.smartmistakebook.core.domain.ModelTaskRepository
import com.tingyun.smartmistakebook.core.domain.StudyProfileOverview
import com.tingyun.smartmistakebook.core.model.AppFailure
import com.tingyun.smartmistakebook.core.model.AppFailureCode
import com.tingyun.smartmistakebook.core.model.CapturedQuestionDocument
import com.tingyun.smartmistakebook.core.model.ContentBlock
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
import com.tingyun.smartmistakebook.core.model.QuestionBlockEvidence
import com.tingyun.smartmistakebook.core.model.QuestionBlockProvenance
import com.tingyun.smartmistakebook.core.model.QuestionBlockReviewStatus
import com.tingyun.smartmistakebook.core.model.QuestionDocument
import com.tingyun.smartmistakebook.core.model.TutorConversationMemory
import com.tingyun.smartmistakebook.core.model.TutorMoveType
import com.tingyun.smartmistakebook.core.model.TutorTurnHistoryEntry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 讲题计划派发的「同标识、不同载荷」冲突与重试身份（KD-35）。
 *
 * 现场：首轮讲解失败 → 失败卡上的「重试」→ 进程直接消失
 * （`ImmutablePayloadConflictException: model_task tutor-plan:… already exists with a different
 * payload`，`ModelTaskTransactionDao.kt:233`）。两个各自独立的缺口叠在一起：
 *
 * ①**重试身份**：请求标识的尾缀是"这一轮的第几次尝试"，而尝试次数取自"这一轮已经落过几行"。
 * 读一份被冻在首次组合（那时通常一行都没有）的任务列表，每次重试都会被算成第 0 次、复用同一个
 * 标识；而重试的时间戳必然不同 ⇒ 写入路径按"同标识、不同载荷"拒绝。
 * ②**兜底**：那个异常从**组合作用域**的协程里抛出来，没有人接住 ⇒ 直达主线程。
 *
 * 这里钉的就是两件事：重试必须换到新标识；即使真的撞上冲突，也只把**这一种**变成可见的、
 * 可重试的失败——别的 `IllegalStateException`（正直性缺陷、协程取消）照旧抛出去，绝不静默。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TutorPlanReplayConflictTest {
    @Test
    fun aRetriedPlanTurnCountsThePersistedRowAndGetsANewIdentity() {
        val provider = planProvider()
        val firstRequest = planRequest(provider, attempt = 0, occurredAtEpochMillis = 100)
        val persisted = listOf(planTaskSnapshot(firstRequest))

        // 首次派发：这一轮一行都没有 ⇒ 第 0 次尝试。
        assertEquals(
            0,
            tutorPlanAttemptCount(
                planTasks = emptyList(),
                cycleOrdinal = 1,
                priorConversationMemory = null,
                priorCycleStudentMessages = emptyList(),
                priorTurns = emptyList(),
            ),
        )
        // 失败之后重试：那一行还在 ⇒ 第 1 次尝试。
        val retryAttempt = tutorPlanAttemptCount(
            planTasks = persisted,
            cycleOrdinal = 1,
            priorConversationMemory = null,
            priorCycleStudentMessages = emptyList(),
            priorTurns = emptyList(),
        )
        assertEquals(1, retryAttempt)

        // 新尝试 = 新标识：复用旧标识而载荷不同（重试的时钟必然不同）就是 KD-35 的那次拒绝。
        val retryRequestId = tutorPlanRequestId(QUESTION, provider, attempt = retryAttempt)
        assertNotEquals(firstRequest.requestId, retryRequestId)
        assertEquals("1", retryRequestId.substringAfterLast(':'))
    }

    @Test
    fun anotherTurnOrAnotherCyclesContextIsNotCountedAsThisTurnsAttempt() {
        val provider = planProvider()
        // 同一轮的下一个回合（已有一轮历史）：不是"这一轮的第几次尝试"。
        val nextTurn = planTaskSnapshot(
            planRequest(
                provider,
                attempt = 0,
                occurredAtEpochMillis = 100,
                priorTurns = listOf(turn()),
            ),
        )
        // 第二个循环（`TutorPlanInput` 要求它必须带会话记忆）：同样不算。
        val laterCycle = planTaskSnapshot(
            planRequest(
                provider,
                attempt = 0,
                occurredAtEpochMillis = 200,
                cycleOrdinal = 2,
                priorConversationMemory = conversationMemory(),
            ),
        )

        assertEquals(
            0,
            tutorPlanAttemptCount(
                planTasks = listOf(nextTurn, laterCycle),
                cycleOrdinal = 1,
                priorConversationMemory = null,
                priorCycleStudentMessages = emptyList(),
                priorTurns = emptyList(),
            ),
        )
    }

    @Test
    fun aReplayConflictOnThePlanDispatchBecomesAVisibleRetryableFailure() = runTest {
        val modelTasks = PlanModelTasks(failure = PlanReplayConflict())
        val harness = planSession(modelTasks, scope = this)

        harness.commands.executeTurn(1, null, emptyList(), emptyList())
        advanceUntilIdle()

        assertEquals("冲突必须真的走到仓库", 1, modelTasks.executions)
        val failure = requireNotNull(harness.planStartError) {
            "冲突被接住之后必须留下可见的失败，而不是静默"
        }
        assertEquals(AppFailureCode.TUTOR_OPERATION_REPLAY_MISMATCH, failure.code)
        assertEquals(TUTOR_PLAN_REPLAY_CONFLICT_MESSAGE, failure.message)
        assertTrue("数据都在（题目与草稿没被动过）", failure.dataPreserved)
        assertTrue("重试会带新身份重来，所以它是可重试的", failure.retryable)
    }

    @Test
    fun aPlanDispatchFailureThatIsNotAReplayConflictIsNotSwallowed() {
        // 不是冲突的失败（例如正直性缺陷）必须照旧抛出去：把它伪装成"可重试的失败"只会把真正的
        // 数据问题藏起来。用真调度器 + 异常处理器把它接在测试这一侧，别让它打崩 JVM。
        val captured = mutableListOf<Throwable>()
        val handled = CompletableDeferred<Unit>()
        val scope = CoroutineScope(
            SupervisorJob() + CoroutineExceptionHandler { _, failure ->
                captured += failure
                handled.complete(Unit)
            },
        )
        val harness = planSession(PlanModelTasks(failure = PlanIntegrityFailure()), scope = scope)

        harness.commands.executeTurn(1, null, emptyList(), emptyList())
        runBlocking { withTimeout(5_000) { handled.await() } }

        assertTrue(captured.single() is PlanIntegrityFailure)
        assertNull("不是冲突就不该给一个可重试的交代", harness.planStartError)
    }

    // --- helpers -------------------------------------------------------------

    /** 一条独立的讲题计划会话：命令 + 它读写的两处状态（任务列表 / 计划失败文案）。 */
    private inner class PlanSession(
        modelTasks: ModelTaskRepository,
        scope: CoroutineScope,
    ) {
        var planTasks: List<ModelTaskSnapshot> = emptyList()
        var planStartError: AppFailure? = null

        val commands: TutorPlanCommands = TutorPlanCommands(
            scope = scope,
            sink = TutorPlanSink(
                provider = { planProvider() },
                question = { QUESTION },
                profile = { StudyProfileOverview() },
                clock = { 10_000L },
                planTasks = { planTasks },
                modelTasks = modelTasks,
                setPlanStartError = { failure -> planStartError = failure },
            ),
        )
    }

    private fun planSession(modelTasks: ModelTaskRepository, scope: CoroutineScope): PlanSession =
        PlanSession(modelTasks = modelTasks, scope = scope)

    /**
     * 计划任务仓库替身：`execute` 只做一件事——按用例抛错。它必须抛在**流内部**：真实仓库就是
     * 在 `execute(request).collect { }` 的那一层抛的（写入发生在第一个快照发出来之前）。
     */
    private inner class PlanModelTasks(
        private val failure: Throwable,
    ) : ModelTaskRepository {
        var executions: Int = 0

        override suspend fun capabilities(): ProviderCapabilitySnapshot = planProvider()

        override fun observe(requestId: String): Flow<ModelTaskSnapshot?> = flowOf(null)

        override fun execute(request: ModelTaskRequest): Flow<ModelTaskSnapshot> = flow {
            executions += 1
            throw failure
        }
    }

    /** 真正的冲突：与 `core:database` 抛出的那一个实现同一个跨层标记。 */
    private class PlanReplayConflict : IllegalStateException(
        "model_task tutor-plan:abcdef:2:1:1:policy:0 already exists with a different payload",
    ), ImmutablePayloadConflict

    /** 冲突之外的族成员（正直性缺陷）：不许被当成可重试失败。 */
    private class PlanIntegrityFailure : IllegalStateException("model task ledger is inconsistent")

    private companion object {
        // 声明顺序即初始化顺序：题面先有，上下文再读它。
        val QUESTION: TutorQuestionContext = TutorQuestionContext(
            sessionId = "session-1",
            revisionNumber = 2,
            subject = "MATH",
            title = "求函数的单调区间",
            questionDocument = CapturedQuestionDocument(
                document = QuestionDocument(
                    id = "document-1",
                    blocks = listOf(
                        ContentBlock.Paragraph(
                            id = "stem",
                            markdown = "已知 f(x)=x³-3x，求它的单调区间。",
                        ),
                    ),
                ),
                blockEvidence = listOf(
                    QuestionBlockEvidence(
                        blockId = "stem",
                        sourceAssetId = "asset-1",
                        provenance = QuestionBlockProvenance.USER_TRANSCRIPTION,
                        reviewStatus = QuestionBlockReviewStatus.USER_CONFIRMED,
                    ),
                ),
            ),
        )

        fun planProvider(): ProviderCapabilitySnapshot = ProviderCapabilitySnapshot(
            providerId = "provider",
            providerDisplayName = "本机模型",
            modelId = "model",
            supportedTasks = setOf(ModelTaskKind.TUTOR_PLAN),
            supportsImageInput = false,
            supportsStructuredOutput = true,
            supportsStreaming = false,
            executionLocation = ModelExecutionLocation.LOCAL_NO_EGRESS,
            providerConfigurationVersion = "provider-config-v1",
        )

        fun planRequest(
            provider: ProviderCapabilitySnapshot,
            attempt: Int,
            occurredAtEpochMillis: Long,
            cycleOrdinal: Int = 1,
            priorConversationMemory: TutorConversationMemory? = null,
            priorCycleStudentMessages: List<String> = emptyList(),
            priorTurns: List<TutorTurnHistoryEntry> = emptyList(),
        ): ModelTaskRequest = buildTutorPlanRequest(
            question = QUESTION,
            profile = StudyProfileOverview(),
            provider = provider,
            requestId = tutorPlanRequestId(
                question = QUESTION,
                provider = provider,
                attempt = attempt,
                cycleOrdinal = cycleOrdinal,
                priorConversationMemory = priorConversationMemory,
                priorCycleStudentMessages = priorCycleStudentMessages,
                priorTurns = priorTurns,
            ),
            occurredAtEpochMillis = occurredAtEpochMillis,
            cycleOrdinal = cycleOrdinal,
            priorConversationMemory = priorConversationMemory,
            priorCycleStudentMessages = priorCycleStudentMessages,
            priorTurns = priorTurns,
        )

        /** 一轮已经讲过的历史（`TutorPlanInput` 要求历史序号从 1 连续）。 */
        fun turn(): TutorTurnHistoryEntry = TutorTurnHistoryEntry(
            turnOrdinal = 1,
            diagnosticStemMarkdown = "导数先正后负时，原函数怎样变化？",
            selectedChoiceMarkdown = "先增后减",
            selectionWasCorrect = false,
            feedbackMarkdown = "继续分析当前题。",
            requestedMove = TutorMoveType.DEEPEN_REASONING,
        )

        /** 会话题的记忆：`TutorPlanInput` 要求第二个循环起必须带它。 */
        fun conversationMemory(): TutorConversationMemory = TutorConversationMemory(
            completedCycleCount = 1,
            answeredTurnCount = 4,
            correctChoiceCount = 2,
            lastFeedbackMarkdown = "你已经能识别定义域，但符号变化仍不稳定。",
            lastRequestedMove = TutorMoveType.CHANGE_REPRESENTATION,
        )

        fun planTaskSnapshot(request: ModelTaskRequest): ModelTaskSnapshot = ModelTaskSnapshot(
            taskId = "task-${request.requestId}",
            request = request,
            requestFingerprint = ModelTaskFingerprint.of(request),
            status = ModelTaskStatus.RETRYABLE_FAILURE,
            stateVersion = 2,
            stage = ModelTaskStage.VALIDATING_OUTPUT,
            userMessage = "这次讲解没有完成",
            attemptCount = 1,
            failure = ModelTaskFailure(
                code = ModelFailureCode.INVALID_RESPONSE,
                message = "这次讲解没有完成",
                retryable = true,
            ),
            createdAtEpochMillis = request.occurredAtEpochMillis,
            updatedAtEpochMillis = request.occurredAtEpochMillis,
        )
    }
}
