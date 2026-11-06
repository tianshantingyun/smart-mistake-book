package com.tingyun.smartmistakebook.core.data.model

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.data.readyKnowledgeBaseAvailability
import com.tingyun.smartmistakebook.core.database.StudyDatabaseFactory
import com.tingyun.smartmistakebook.core.database.StudyDatabasePort
import com.tingyun.smartmistakebook.core.domain.ModelGateway
import com.tingyun.smartmistakebook.core.model.ModelExecutionLocation
import com.tingyun.smartmistakebook.core.model.ModelFailureCode
import com.tingyun.smartmistakebook.core.model.ModelGatewayEvent
import com.tingyun.smartmistakebook.core.model.ModelGatewayExecution
import com.tingyun.smartmistakebook.core.model.ModelTaskKind
import com.tingyun.smartmistakebook.core.model.ModelTaskLogicalOperationFingerprint
import com.tingyun.smartmistakebook.core.model.ModelTaskRemoteDispatchPolicy
import com.tingyun.smartmistakebook.core.model.ModelTaskRequest
import com.tingyun.smartmistakebook.core.model.ModelTaskStatus
import com.tingyun.smartmistakebook.core.model.PaperEditBlockKind
import com.tingyun.smartmistakebook.core.model.PaperEditBlockSnapshot
import com.tingyun.smartmistakebook.core.model.PaperEditInput
import com.tingyun.smartmistakebook.core.model.PaperEditQuestionSnapshot
import com.tingyun.smartmistakebook.core.model.ProviderCapabilitySnapshot
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 批次 41 / WS7 项 44（账本半边）：改卷走**同一本**持久派发账本。
 *
 * 消灭的具体失败（契约 C-5 的验收句"新任务走同一 ledger 的负向（预算耗尽拒绝）"）：
 * 新 kind 若绕开 `reserveModelTaskRemoteDispatch`（例如在 feature 侧直接调网关、或另开一套
 * 内存计数），同一份卷面可以被无限重发而不留痕；反过来，若它和讲题轮共用同一个逻辑操作，
 * 一次改卷失败会吃掉讲题的派发额度。这里用真 Room 账本 + 一个在 `Started` 之前就失败的
 * gateway 替身（fake gateway）钉住两件事：
 *
 * 1. 同一逻辑操作（同输入、同 paperId/instruction）第 1..5 次失败可重试、第 6 次落
 *    `PERMANENT_FAILURE`，第 7 次重放不再调用 gateway；
 * 2. 学生改了要求就是另一次逻辑操作（指纹不同），从第 1 次重新计数。
 */
@RunWith(AndroidJUnit4::class)
class PaperEditLedgerInstrumentedTest {
    private lateinit var context: Context
    private lateinit var database: StudyDatabasePort
    private lateinit var databaseName: String

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        databaseName = "paper-edit-ledger-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        database = StudyDatabaseFactory.open(context, databaseName)
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun paperEditFailureConsumesItsOwnSixDispatchBudgetAndFreshEditsStartOver() = runBlocking {
        var now = 5_000L
        val gatewayExecutions = AtomicInteger()
        val capabilities = paperEditCapabilities()
        val repository = RoomModelTaskRepository(
            database = database,
            gateway = object : ModelGateway {
                override suspend fun capabilities(): ProviderCapabilitySnapshot = capabilities

                override fun execute(execution: ModelGatewayExecution): Flow<ModelGatewayEvent> =
                    flow {
                        gatewayExecutions.incrementAndGet()
                        error("Connection failed before the provider emitted Started")
                    }
            },
            clock = { ++now },
            knowledgeBaseAvailability = readyKnowledgeBaseAvailability(),
        )
        val request = paperEditRequest()
        val budget = ModelTaskRemoteDispatchPolicy.MAX_DISPATCHES

        val attempts = (1..budget).map { repository.execute(request).toList().last() }
        attempts.dropLast(1).forEachIndexed { index, snapshot ->
            assertEquals(ModelTaskStatus.RETRYABLE_FAILURE, snapshot.status)
            assertEquals(index + 1, snapshot.attemptCount)
        }
        val exhausted = attempts.last()

        assertEquals(ModelTaskStatus.PERMANENT_FAILURE, exhausted.status)
        assertEquals(budget, exhausted.attemptCount)
        assertEquals(ModelFailureCode.UNKNOWN, exhausted.failure?.code)
        assertEquals(false, exhausted.failure?.retryable)
        assertEquals(budget, gatewayExecutions.get())

        // 账本已耗尽：重放同一个逻辑操作不再产生一次新派遣。
        val replayed = repository.execute(request).toList().last()
        assertEquals(exhausted, replayed)
        assertEquals(budget, gatewayExecutions.get())

        // 改一句话要求 = 新的逻辑操作：自己的额度从第 1 次开始，不被上一操作耗尽的预算牵连。
        val otherInput = paperEditInput(instruction = "把第 1 题删掉")
        val otherRequest = paperEditRequest(
            input = otherInput,
            requestId = "paper-edit-request-fresh",
        )
        assertNotEquals(
            ModelTaskLogicalOperationFingerprint.of(request.input),
            ModelTaskLogicalOperationFingerprint.of(otherInput),
        )
        val fresh = repository.execute(otherRequest).toList().last()

        assertEquals(ModelTaskStatus.RETRYABLE_FAILURE, fresh.status)
        assertEquals(1, fresh.attemptCount)
        assertEquals(budget + 1, gatewayExecutions.get())
    }

    private fun paperEditRequest(
        input: PaperEditInput = paperEditInput(),
        requestId: String = "paper-edit-request",
    ) = ModelTaskRequest(
        requestId = requestId,
        input = input,
        occurredAtEpochMillis = 1_000,
        // 6B-3 走全局同意通道（与生产 `isAgentConsentEligible=true` 同形），不建逐次清单。
        agentConsentGranted = true,
        egressManifest = null,
    )

    private fun paperEditInput(instruction: String = "把第 1 题标题改短") = PaperEditInput(
        paperId = "paper-1",
        revision = 3,
        instruction = instruction,
        questions = listOf(
            PaperEditQuestionSnapshot(
                entryId = "entry-a",
                title = "二次函数综合题",
                blocks = listOf(
                    PaperEditBlockSnapshot(
                        blockId = "stem-entry-a",
                        kind = PaperEditBlockKind.PARAGRAPH,
                        text = "求函数的单调区间",
                    ),
                ),
            ),
        ),
    )

    private fun paperEditCapabilities() = ProviderCapabilitySnapshot(
        providerId = "paper-edit-provider",
        providerDisplayName = "改卷测试提供方",
        modelId = "paper-edit-model",
        supportedTasks = setOf(ModelTaskKind.PAPER_EDIT),
        supportsImageInput = false,
        supportsStructuredOutput = true,
        supportsStreaming = false,
        isDemo = false,
        executionLocation = ModelExecutionLocation.EXTERNAL_PROVIDER,
        providerConfigurationVersion = "instrumented-paper-edit-v1",
    )
}
