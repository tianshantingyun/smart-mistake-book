package com.tingyun.smartmistakebook.core.data.tutor

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.data.capture.CaptureWorkflowTestBase
import com.tingyun.smartmistakebook.core.data.capture.BatchImportRepositoryFactory
import com.tingyun.smartmistakebook.core.data.model.RoomModelTaskRepository
import com.tingyun.smartmistakebook.core.data.readyKnowledgeBaseAvailability
import com.tingyun.smartmistakebook.core.domain.AgentPendingRequestDecision
import com.tingyun.smartmistakebook.core.domain.AgentPendingRequestKind
import com.tingyun.smartmistakebook.core.domain.AgentPendingRequestRepository
import com.tingyun.smartmistakebook.core.domain.AgentPendingRequestStatus
import com.tingyun.smartmistakebook.core.domain.DecidePendingRequestCommand
import com.tingyun.smartmistakebook.core.domain.LobbyMessageImageIntake
import com.tingyun.smartmistakebook.core.domain.ModelGateway
import com.tingyun.smartmistakebook.core.domain.SuspendTurnForConsentCommand
import com.tingyun.smartmistakebook.core.domain.TutorAttachedImageIntake
import com.tingyun.smartmistakebook.core.domain.TutorConsentRequests
import com.tingyun.smartmistakebook.core.domain.TutorLocalActionContext
import com.tingyun.smartmistakebook.core.domain.TutorPermissionSubject
import com.tingyun.smartmistakebook.core.domain.TutorSendAction
import com.tingyun.smartmistakebook.core.domain.TutorSendState
import com.tingyun.smartmistakebook.core.domain.TutorTurnSendStateMachine
import com.tingyun.smartmistakebook.core.domain.toAgentPendingRequestPayload
import com.tingyun.smartmistakebook.core.model.CaptureAssessment
import com.tingyun.smartmistakebook.core.model.CaptureAssessmentDecision
import com.tingyun.smartmistakebook.core.model.CaptureAssessmentInput
import com.tingyun.smartmistakebook.core.model.CaptureAssessmentOrigin
import com.tingyun.smartmistakebook.core.model.CaptureAssessmentOutput
import com.tingyun.smartmistakebook.core.model.ModelExecutionLocation
import com.tingyun.smartmistakebook.core.model.ModelGatewayEvent
import com.tingyun.smartmistakebook.core.model.ModelGatewayExecution
import com.tingyun.smartmistakebook.core.model.ModelLiveKind
import com.tingyun.smartmistakebook.core.model.ModelTaskKind
import com.tingyun.smartmistakebook.core.model.ModelTaskRemoteDispatchPolicy
import com.tingyun.smartmistakebook.core.model.ModelTaskRequest
import com.tingyun.smartmistakebook.core.model.ModelTaskStatus
import com.tingyun.smartmistakebook.core.model.ProviderCapabilitySnapshot
import com.tingyun.smartmistakebook.core.model.TutorLocalAction
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 三条真实落地（A4）与取消（A2）在**真库**上的断言：库里真的出现了条目 / 状态真的落了终态。
 *
 * 本机可跑的用例（`TutorPendingRequestCommandsTest`）证明接线与路由；这里证明落库那一半：
 * 附图上库真的落成草稿/导入任务、确认卡裁决真的落成终态行、停止真的落成 `CANCELLED` 且不动
 * 派发账本。
 */
@RunWith(AndroidJUnit4::class)
class TutorLocalActionLandingInstrumentedTest : CaptureWorkflowTestBase() {

    private fun images(): LobbyMessageImageIntake =
        LobbyMessageImageIntakeFactory.create(context, database)

    private fun intake(processingScope: CoroutineScope): TutorAttachedImageIntake =
        TutorAttachedImageIntakeFactory.create(
            images = images(),
            capture = repository,
            batchImports = BatchImportRepositoryFactory.create(
                context = context,
                database = database,
                capture = repository,
                // 流水线推进交给一个已停止的 scope：这里断言的是"任务行落库"，不是它跑完。
                processingScope = processingScope,
            ),
        )

    @Test
    fun twoAttachedImagesLandAsABatchImportJobInTheDatabase() = runBlocking {
        val batchImports = BatchImportRepositoryFactory.create(
            context = context,
            database = database,
            capture = repository,
            processingScope = stoppedScope(),
        )
        val intake = TutorAttachedImageIntakeFactory.create(
            images = images(),
            capture = repository,
            batchImports = batchImports,
        )
        val assets = listOf(
            images().registerImage(privateUri(createJpeg(120, 90)).toString(), 100),
            images().registerImage(privateUri(createJpeg(64, 64)).toString(), 200),
        )

        val result = intake.intake(assets.map { it.assetId }, occurredAtEpochMillis = 1_000)

        assertTrue(result.detail, result.landed)
        val job = batchImports.observeBatchImports().first().singleOrNull()
        assertNotNull("批量导入任务必须真的落库（${result.detail}）", job)
        assertEquals(2, job!!.pages.size)
    }

    @Test
    fun aSingleAttachedImageLandsAsOnePendingDraftInTheDatabase() = runBlocking {
        val intake = intake(stoppedScope())
        val asset = images().registerImage(privateUri(createJpeg(80, 60)).toString(), 100)

        val result = intake.intake(listOf(asset.assetId), occurredAtEpochMillis = 1_000)

        assertTrue(result.detail, result.landed)
        assertEquals(
            "单张图必须落成一份待处理草稿",
            1,
            repository.observePendingCaptures().first().size,
        )
    }

    @Test
    fun intakeWithoutAnyResolvableImageReportsThatItDidNotLand() = runBlocking {
        val intake = intake(stoppedScope())

        val result = intake.intake(listOf("asset-does-not-exist"), occurredAtEpochMillis = 1_000)

        assertTrue("没落成必须如实说没落成：${result.detail}", !result.landed)
        assertTrue(repository.observePendingCaptures().first().isEmpty())
    }

    @Test
    fun aDecidedCardLeavesATerminalRowAndKeepsItsNote() = runBlocking {
        val requests: AgentPendingRequestRepository =
            AgentPendingRequestRepositoryFactory.create(database)
        val consent = TutorConsentRequests(requests)
        val state = TutorTurnSendStateMachine.reduce(
            TutorSendState(),
            TutorSendAction.StudentMessagePersisted("logical-1", "message-1"),
        )
        val context = TutorLocalActionContext(attachedImageAssetIds = listOf("asset-1"))

        val suspension = consent.suspend(
            state = state,
            command = SuspendTurnForConsentCommand(
                subject = TutorPermissionSubject.LocalAction(TutorLocalAction.SAVE_TO_NOTEBOOK),
                conversationArea = "AGENT",
                conversationId = "conversation-1",
                payloadJson = context.toAgentPendingRequestPayload(),
                occurredAtEpochMillis = 10,
            ),
        )
        assertNotNull(suspension)
        assertEquals(
            AgentPendingRequestStatus.PENDING,
            requests.readPendingRequests("AGENT").single().status,
        )

        consent.decide(
            state = suspension!!.state,
            command = DecidePendingRequestCommand(
                requestId = suspension.request.requestId,
                decision = AgentPendingRequestDecision.ACCEPT,
                resolutionNote = "已经加入录入队列（2 页）。",
                occurredAtEpochMillis = 20,
            ),
        )

        val resolved = requests.readResolvedRequests("AGENT").single()
        assertEquals(AgentPendingRequestKind.SAVE_TO_NOTEBOOK, resolved.kind)
        assertEquals(AgentPendingRequestStatus.ACCEPTED, resolved.status)
        assertEquals("已经加入录入队列（2 页）。", resolved.resolutionNote)
        // 终态的行不再出现在待确认列表里（界面因此不会再画一张已经点过的卡）。
        assertTrue(requests.readPendingRequests("AGENT").isEmpty())
    }

    @Test
    fun stoppingATaskLandsTheCancelledTerminalWithoutSpendingDispatchBudget() = runBlocking {
        val gateway = HangingModelGateway()
        val repository = RoomModelTaskRepository(
            database = database,
            gateway = gateway,
            knowledgeBaseAvailability = readyKnowledgeBaseAvailability(),
        )
        val request = request()
        val running = CompletableDeferred<Unit>()

        val execution = launch(Dispatchers.IO) {
            repository.execute(request).collect { snapshot ->
                if (snapshot.status == ModelTaskStatus.RUNNING) running.complete(Unit)
            }
        }
        assertNotNull(
            "这一轮必须真的跑起来（Started 已发）",
            withTimeoutOrNull(10_000) { running.await() },
        )
        val beforeStop = database.readModelTask(request.requestId)

        repository.cancel(request.requestId)

        val afterStop = database.readModelTask(request.requestId)
        assertEquals(ModelTaskStatus.CANCELLED, afterStop?.status)
        assertEquals(
            "取消不是一次派遣：账本里的派遣计数不得变化",
            beforeStop?.attemptCount,
            afterStop?.attemptCount,
        )
        assertTrue(
            "取消后计数仍在上限内（还可以再发）",
            ModelTaskRemoteDispatchPolicy.canSchedule(afterStop?.attemptCount ?: 0),
        )
        execution.cancel()
        gateway.release()
    }

    @Test
    fun theSameOperationCanBeDispatchedAgainImmediatelyAfterAStop() = runBlocking {
        val hanging = RoomModelTaskRepository(
            database = database,
            gateway = HangingModelGateway(),
            knowledgeBaseAvailability = readyKnowledgeBaseAvailability(),
        )
        val first = request()
        val running = CompletableDeferred<Unit>()
        val execution = launch(Dispatchers.IO) {
            hanging.execute(first).collect { snapshot ->
                if (snapshot.status == ModelTaskStatus.RUNNING) running.complete(Unit)
            }
        }
        withTimeoutOrNull(10_000) { running.await() }
        hanging.cancel(first.requestId)
        execution.cancel()

        // 立刻再发：同一条逻辑操作的下一次尝试必须马上能跑（不被上一次的进程内锁挡住）。
        val immediate = RoomModelTaskRepository(
            database = database,
            gateway = ImmediateModelGateway(),
            knowledgeBaseAvailability = readyKnowledgeBaseAvailability(),
        )
        val statuses = immediate.execute(first.copy(requestId = "${first.requestId}:retry"))
            .toList()

        assertEquals(ModelTaskStatus.SUCCEEDED, statuses.last().status)
    }

    private fun stoppedScope(): CoroutineScope =
        CoroutineScope(SupervisorJob().also { it.cancel() } + Dispatchers.Default)

    private fun request() = ModelTaskRequest(
        requestId = "capture-assess:cancellation",
        input = CaptureAssessmentInput(
            draftId = "draft-cancellation",
            sourceAssetId = "asset-cancellation",
            origin = CaptureAssessmentOrigin.TUTOR,
            imageWidth = 1080,
            imageHeight = 1440,
        ),
        occurredAtEpochMillis = 1_000,
    )
}

private val TEST_GATEWAY_CAPABILITIES = ProviderCapabilitySnapshot(
    providerId = "instrumented-gateway",
    providerDisplayName = "仪器化测试网关",
    modelId = "instrumented",
    supportedTasks = ModelTaskKind.entries.toSet(),
    supportsImageInput = true,
    supportsStructuredOutput = true,
    supportsStreaming = true,
    executionLocation = ModelExecutionLocation.EXTERNAL_PROVIDER,
    providerConfigurationVersion = "instrumented-v1",
)

/** 一直在流式、不会自己结束的网关：给"取消"制造一个真的在跑的窗口。 */
private class HangingModelGateway : ModelGateway {
    private val released = CompletableDeferred<Unit>()

    override suspend fun capabilities(): ProviderCapabilitySnapshot = TEST_GATEWAY_CAPABILITIES

    override fun execute(execution: ModelGatewayExecution) = flow<ModelGatewayEvent> {
        emit(ModelGatewayEvent.Started(TEST_GATEWAY_CAPABILITIES))
        emit(ModelGatewayEvent.Progress.of("正在读题"))
        var index = 0
        while (!released.isCompleted) {
            emit(
                ModelGatewayEvent.LiveProgress(
                    kind = ModelLiveKind.THINKING,
                    text = "思考片段${index++}",
                ),
            )
            delay(50)
        }
    }

    fun release() {
        released.complete(Unit)
    }
}

/** 立刻完成的网关：证明"停止之后同一条逻辑操作可以马上再发"。 */
private class ImmediateModelGateway : ModelGateway {
    override suspend fun capabilities(): ProviderCapabilitySnapshot = TEST_GATEWAY_CAPABILITIES

    override fun execute(execution: ModelGatewayExecution) = flow<ModelGatewayEvent> {
        emit(ModelGatewayEvent.Started(TEST_GATEWAY_CAPABILITIES))
        emit(
            ModelGatewayEvent.Completed(
                CaptureAssessmentOutput(
                    assessment = CaptureAssessment(
                        decision = CaptureAssessmentDecision.PASS,
                        issues = emptyList(),
                        suggestedActions = emptyList(),
                        modelVersion = "test/immediate-v1",
                    ),
                ),
            ),
        )
    }
}
