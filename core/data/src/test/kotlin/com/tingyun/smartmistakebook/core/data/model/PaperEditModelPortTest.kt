package com.tingyun.smartmistakebook.core.data.model

import com.tingyun.smartmistakebook.core.domain.ModelTaskRepository
import com.tingyun.smartmistakebook.core.model.ModelFailureCode
import com.tingyun.smartmistakebook.core.model.ModelTaskFailure
import com.tingyun.smartmistakebook.core.model.ModelTaskFingerprint
import com.tingyun.smartmistakebook.core.model.ModelTaskKind
import com.tingyun.smartmistakebook.core.model.ModelTaskLogicalOperationFingerprint
import com.tingyun.smartmistakebook.core.model.ModelTaskOutput
import com.tingyun.smartmistakebook.core.model.ModelTaskRequest
import com.tingyun.smartmistakebook.core.model.ModelTaskSnapshot
import com.tingyun.smartmistakebook.core.model.ModelTaskStage
import com.tingyun.smartmistakebook.core.model.ModelTaskStatus
import com.tingyun.smartmistakebook.core.model.PaperEditBlockKind
import com.tingyun.smartmistakebook.core.model.PaperEditBlockSnapshot
import com.tingyun.smartmistakebook.core.model.PaperEditInput
import com.tingyun.smartmistakebook.core.model.PaperEditModelException
import com.tingyun.smartmistakebook.core.model.PaperEditOutput
import com.tingyun.smartmistakebook.core.model.PaperEditQuestionSnapshot
import com.tingyun.smartmistakebook.core.model.ProviderCapabilitySnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 批次 41 / WS7 项 46：装配接缝 [RepositoryPaperEditModelPort] 的判据。
 *
 * 这些用例钉住的是"接口怎么用"而不是网关细节：请求形状（kind/schema/不带清单/同意位透传）、
 * 终态分类（成功 / 可重试失败 / 不可重试失败 / 取消 / 没落终态）、以及终态必须属于本次提交。
 * 消灭的具体失败都在接口 KDoc 里：feature 侧旁路有界快照或白名单解析器、把失败当"没有修改"、
 * 把上一请求的结果当作本次改卷。
 *
 * 预算/账本的持久半边在 `core:data/src/androidTest` 的 `PaperEditLedgerInstrumentedTest`
 * （真实 Room 账本）；这里是 JVM 侧的同一身份断言：requestId 变化不产生新的逻辑操作。
 */
class PaperEditModelPortTest {

    @Test
    fun thePortSubmitsOneBoundedPaperEditRequestAndReturnsTheValidatedOutput() = runBlocking {
        val output = commandsOutput()
        val repository = FakeTaskRepository { request ->
            listOf(
                snapshot(request, ModelTaskStatus.RUNNING),
                snapshot(request, ModelTaskStatus.SUCCEEDED, output = output),
            )
        }
        val port = port(repository)

        val returned = port.commandsFor(input(), agentConsentGranted = true)

        assertEquals(output, returned)
        val request = repository.executed.single()
        assertEquals("paper-edit:port-test", request.requestId)
        assertEquals(input(), request.input)
        assertEquals(ModelTaskKind.PAPER_EDIT, request.input.kind)
        assertEquals(ModelTaskRequest.PAPER_EDIT_SCHEMA_VERSION, request.schemaVersion)
        assertTrue(request.agentConsentGranted)
        // 改卷走全局同意通道；端口不得自己造一份逐次出网清单。
        assertNull(request.egressManifest)
    }

    @Test
    fun theCallerConsentValueIsForwardedAndNeverHardcoded() = runBlocking {
        val repository = FakeTaskRepository { request ->
            listOf(snapshot(request, ModelTaskStatus.SUCCEEDED, output = commandsOutput()))
        }
        val port = port(repository)

        port.commandsFor(input(), agentConsentGranted = true)
        port.commandsFor(input(), agentConsentGranted = false)

        assertEquals(listOf(true, false), repository.executed.map { it.agentConsentGranted })
    }

    @Test
    fun aRetryableFailureSurfacesItsCodeAndRetryabilityInsteadOfEmptyCommands() {
        val failure = ModelTaskFailure(
            code = ModelFailureCode.INCOMPLETE_OUTPUT,
            message = "模型这次没写完，可以重试",
            retryable = true,
        )
        val repository = FakeTaskRepository { request ->
            listOf(
                snapshot(
                    request,
                    ModelTaskStatus.RETRYABLE_FAILURE,
                    failure = failure,
                    userMessage = failure.message,
                ),
            )
        }

        val thrown = assertThrows(PaperEditModelException::class.java) {
            runBlocking { port(repository).commandsFor(input(), agentConsentGranted = true) }
        }

        assertEquals(ModelTaskStatus.RETRYABLE_FAILURE, thrown.status)
        assertEquals(ModelFailureCode.INCOMPLETE_OUTPUT, thrown.failureCode)
        assertTrue(thrown.retryable)
        assertEquals(failure.message, thrown.message)
    }

    @Test
    fun aPermanentFailureIsNotFlattenedIntoAnEmptyCommandList() {
        val failure = ModelTaskFailure(
            code = ModelFailureCode.INVALID_RESPONSE,
            message = "模型返回的编辑命令不可用",
            retryable = false,
        )
        val repository = FakeTaskRepository { request ->
            listOf(
                snapshot(
                    request,
                    ModelTaskStatus.PERMANENT_FAILURE,
                    failure = failure,
                    userMessage = failure.message,
                ),
            )
        }

        val thrown = assertThrows(PaperEditModelException::class.java) {
            runBlocking { port(repository).commandsFor(input(), agentConsentGranted = true) }
        }

        assertEquals(ModelTaskStatus.PERMANENT_FAILURE, thrown.status)
        assertEquals(ModelFailureCode.INVALID_RESPONSE, thrown.failureCode)
        assertFalse(thrown.retryable)
    }

    @Test
    fun aCancelledTaskIsReportedAsCancellationNotAsSuccess() {
        val repository = FakeTaskRepository { request ->
            listOf(
                snapshot(request, ModelTaskStatus.CANCELLED, userMessage = "已停止这次改卷"),
            )
        }

        val thrown = assertThrows(PaperEditModelException::class.java) {
            runBlocking { port(repository).commandsFor(input(), agentConsentGranted = true) }
        }

        assertEquals(ModelTaskStatus.CANCELLED, thrown.status)
        assertNull("取消不是失败码：不得伪造一个 INVALID_RESPONSE", thrown.failureCode)
        assertEquals("已停止这次改卷", thrown.message)
    }

    @Test
    fun aFlowThatEndsWithoutATerminalStateFailsClosed() {
        val repository = FakeTaskRepository { request ->
            listOf(snapshot(request, ModelTaskStatus.RUNNING))
        }

        val thrown = assertThrows(PaperEditModelException::class.java) {
            runBlocking { port(repository).commandsFor(input(), agentConsentGranted = true) }
        }

        assertEquals(ModelTaskStatus.RUNNING, thrown.status)
        assertTrue("没有终态是可重试的悬置，不是形状错误", thrown.retryable)
    }

    @Test
    fun anEmptyFlowFailsClosedRatherThanReportingNoChanges() {
        val repository = FakeTaskRepository { emptyList() }

        val thrown = assertThrows(PaperEditModelException::class.java) {
            runBlocking { port(repository).commandsFor(input(), agentConsentGranted = true) }
        }

        assertNull(thrown.status)
        assertFalse(thrown.retryable)
    }

    @Test
    fun aTerminalStateFromAnotherRequestCannotBeHandedBack() {
        val repository = FakeTaskRepository { request ->
            val other = request.copy(requestId = "paper-edit:someone-else")
            listOf(snapshot(other, ModelTaskStatus.SUCCEEDED, output = commandsOutput()))
        }

        val thrown = assertThrows(PaperEditModelException::class.java) {
            runBlocking { port(repository).commandsFor(input(), agentConsentGranted = true) }
        }

        assertEquals("paper-edit:port-test", repository.executed.single().requestId)
        assertEquals(ModelTaskStatus.SUCCEEDED, thrown.status)
        assertTrue("${thrown.message}", thrown.message!!.contains("不属于本次请求"))
    }

    @Test
    fun retriesOfTheSameEditShareOneLogicalOperationWhileNewRequestsDoNot() = runBlocking {
        // 账本按 `kind.name + 输入 JSON` 扣预算：换 requestId 的重发仍是同一次逻辑操作，
        // 学生改了要求才是新操作（新额度）。这一条与设备侧 6 次耗尽用例同口径。
        val repository = FakeTaskRepository { request ->
            listOf(snapshot(request, ModelTaskStatus.SUCCEEDED, output = commandsOutput()))
        }
        val requestIds = ArrayDeque(listOf("paper-edit:first", "paper-edit:second", "paper-edit:third"))
        val port = RepositoryPaperEditModelPort(
            repository = repository,
            clock = { 1_700_000_000_000 },
            requestIds = { requestIds.removeFirst() },
        )

        port.commandsFor(input(), agentConsentGranted = true)
        port.commandsFor(input(), agentConsentGranted = true)
        port.commandsFor(input(instruction = "把第 1 题删掉"), agentConsentGranted = true)

        val sent = repository.executed
        assertNotEquals(sent[0].requestId, sent[1].requestId)
        assertEquals(
            ModelTaskLogicalOperationFingerprint.of(sent[0].input),
            ModelTaskLogicalOperationFingerprint.of(sent[1].input),
        )
        assertNotEquals(
            "换一句话要求就是另一次逻辑操作，必须有自己的派发预算",
            ModelTaskLogicalOperationFingerprint.of(sent[1].input),
            ModelTaskLogicalOperationFingerprint.of(sent[2].input),
        )
    }

    private fun port(repository: ModelTaskRepository) = RepositoryPaperEditModelPort(
        repository = repository,
        clock = { 1_700_000_000_000 },
        requestIds = { "paper-edit:port-test" },
    )

    private fun input(instruction: String = "把第 1 题标题改短") = PaperEditInput(
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

    private fun commandsOutput() = PaperEditOutput(
        commandsJson = """{"commands":[{"type":"set_question_title","entryId":"entry-a","title":"短一点的标题"}]}""",
        modelVersion = "fake/paper-v1",
    )

    private fun snapshot(
        request: ModelTaskRequest,
        status: ModelTaskStatus,
        output: ModelTaskOutput? = null,
        failure: ModelTaskFailure? = null,
        userMessage: String = "改卷中",
    ) = ModelTaskSnapshot(
        taskId = "task:${request.requestId}",
        request = request,
        requestFingerprint = ModelTaskFingerprint.of(request),
        status = status,
        stateVersion = 1,
        stage = ModelTaskStage.VALIDATING_OUTPUT,
        userMessage = userMessage,
        attemptCount = 0,
        output = output,
        failure = failure,
        createdAtEpochMillis = 1_700_000_000_000,
        updatedAtEpochMillis = 1_700_000_000_000,
    )

    private class FakeTaskRepository(
        private val snapshots: (ModelTaskRequest) -> List<ModelTaskSnapshot>,
    ) : ModelTaskRepository {
        val executed = mutableListOf<ModelTaskRequest>()

        override suspend fun capabilities(): ProviderCapabilitySnapshot = PROVIDER

        override fun observe(requestId: String): Flow<ModelTaskSnapshot?> = flowOf(null)

        override fun execute(request: ModelTaskRequest): Flow<ModelTaskSnapshot> {
            executed += request
            return flow { snapshots(request).forEach { emit(it) } }
        }

        private companion object {
            val PROVIDER = ProviderCapabilitySnapshot(
                providerId = "paper-edit-test-provider",
                providerDisplayName = "改卷测试提供方",
                modelId = "paper-edit-test-model",
                supportedTasks = setOf(ModelTaskKind.PAPER_EDIT),
                supportsImageInput = false,
                supportsStructuredOutput = true,
                supportsStreaming = false,
                providerConfigurationVersion = "port-test-v1",
            )
        }
    }
}
