package com.tingyun.smartmistakebook.core.data.tutor

import com.tingyun.smartmistakebook.core.database.AgentPendingRequestRecord
import com.tingyun.smartmistakebook.core.database.CreateAgentPendingRequestDatabaseCommand
import com.tingyun.smartmistakebook.core.database.ResolveAgentPendingRequestDatabaseCommand
import com.tingyun.smartmistakebook.core.database.port.AgentPendingRequestPort
import com.tingyun.smartmistakebook.core.domain.AgentPendingRequestDecision
import com.tingyun.smartmistakebook.core.domain.AgentPendingRequestDraft
import com.tingyun.smartmistakebook.core.domain.AgentPendingRequestKind
import com.tingyun.smartmistakebook.core.domain.AgentPendingRequestRepository
import com.tingyun.smartmistakebook.core.domain.AgentPendingRequestStatus
import com.tingyun.smartmistakebook.core.domain.DecidePendingRequestCommand
import com.tingyun.smartmistakebook.core.domain.SuspendTurnForConsentCommand
import com.tingyun.smartmistakebook.core.domain.TutorConsentRequests
import com.tingyun.smartmistakebook.core.domain.TutorPermissionSubject
import com.tingyun.smartmistakebook.core.domain.TutorRoundPermissionContext
import com.tingyun.smartmistakebook.core.domain.TutorSendAction
import com.tingyun.smartmistakebook.core.domain.TutorSendPhase
import com.tingyun.smartmistakebook.core.domain.TutorSendState
import com.tingyun.smartmistakebook.core.domain.TutorTurnSendStateMachine
import com.tingyun.smartmistakebook.core.model.TutorLocalAction
import com.tingyun.smartmistakebook.core.model.TutorToolName
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 落库实现 + 确认卡接线的**组合**跑通：域侧请求 → 落库命令 → 读回域记录 → 裁决 → 相位继续。
 *
 * 这里刻意**不重测** SQL 语义（幂等、终态不可回头的比较交换）：那一份在真库上由
 * `AgentPendingRequestInstrumentedTest` 证明，重写一遍只会得到一份会和 SQL 漂开的第二实现。
 * 本文件盯的是"两层之间没有丢字段/换名字"：域 → 命令的每个字段、记录 → 域的每个枚举，
 * 以及接线的进出（挂起 → AWAITING_CONSENT、裁决 → DISPATCHING + 回喂行）。
 */
class RoomAgentPendingRequestRepositoryTest {
    private val port = FakeAgentPendingRequestPort()
    private val repository: AgentPendingRequestRepository =
        RoomAgentPendingRequestRepository(port)
    private val consent = TutorConsentRequests(repository)

    @Test
    fun aSuspensionWritesEveryFieldOfTheRequestItWasAskedFor() = runBlocking {
        val suspended = consent.suspend(
            state = turn(),
            command = SuspendTurnForConsentCommand(
                subject = TutorPermissionSubject.LocalAction(TutorLocalAction.START_EXPORT),
                conversationArea = "REVIEW_MISTAKE",
                conversationId = "conversation-1",
                payloadJson = """{"scope":"subjects"}""",
                occurredAtEpochMillis = 100L,
                context = TutorRoundPermissionContext(),
            ),
        )

        assertNotNull(suspended)
        val command = port.createdCommands.single()
        assertEquals(
            AgentPendingRequestDraft(
                conversationArea = "REVIEW_MISTAKE",
                conversationId = "conversation-1",
                logicalOperationId = "logical-1",
                messageId = "message-1",
                kind = AgentPendingRequestKind.START_EXPORT,
                payloadJson = """{"scope":"subjects"}""",
            ).requestId(),
            command.requestId,
        )
        assertEquals("START_EXPORT", command.kind)
        assertEquals("REVIEW_MISTAKE", command.conversationArea)
        assertEquals("logical-1", command.logicalOperationId)
        assertEquals("message-1", command.messageId)
        assertEquals(100L, command.createdAtEpochMillis)
        assertEquals(TutorSendPhase.AWAITING_CONSENT, suspended!!.state.phase)
    }

    @Test
    fun aDecisionSettlesTheRowAndResumesTheTurn() = runBlocking {
        val suspended = consent.suspend(
            state = turn(),
            command = SuspendTurnForConsentCommand(
                subject = TutorPermissionSubject.LocalAction(TutorLocalAction.ADD_TO_REVIEW_PLAN),
                conversationArea = "AGENT",
                conversationId = "conversation-1",
                payloadJson = """{"problemRevisionId":"rev-1"}""",
                occurredAtEpochMillis = 100L,
            ),
        )!!

        val resolved = consent.decide(
            state = suspended.state,
            command = DecidePendingRequestCommand(
                requestId = suspended.request.requestId,
                decision = AgentPendingRequestDecision.ACCEPT,
                resolutionNote = "纳入计划",
                occurredAtEpochMillis = 200L,
            ),
        )!!

        assertEquals(
            ResolveAgentPendingRequestDatabaseCommand(
                requestId = suspended.request.requestId,
                status = "ACCEPTED",
                resolutionNote = "纳入计划",
                resolvedAtEpochMillis = 200L,
            ),
            port.resolvedCommands.single(),
        )
        assertEquals(AgentPendingRequestStatus.ACCEPTED, resolved.request.status)
        assertEquals(AgentPendingRequestStatus.ACCEPTED, resolved.outcome.status)
        assertEquals(TutorSendPhase.DISPATCHING, resolved.state.phase)
        assertTrue(port.pendingRows.value.isEmpty())
    }

    @Test
    fun readsMapEveryColumnBackToItsDomainShape() = runBlocking {
        port.pendingRows.value = listOf(record(status = "PENDING"))
        port.resolvedRows = listOf(record(status = "IGNORED", resolutionNote = "学生划走了"))

        val pending = repository.observePendingRequests(conversationArea = "AGENT").first()
        val resolved = repository.readResolvedRequests("AGENT", limit = 5)

        assertEquals(AgentPendingRequestKind.OPEN_PROBLEM, pending.single().kind)
        assertEquals(AgentPendingRequestStatus.PENDING, pending.single().status)
        assertNull(pending.single().resolvedAtEpochMillis)
        assertEquals(AgentPendingRequestStatus.IGNORED, resolved.single().status)
        assertEquals("学生划走了", resolved.single().resolutionNote)
        assertEquals(200L, resolved.single().resolvedAtEpochMillis)
        assertEquals(listOf("AGENT" to 5), port.readResolvedCalls)
    }

    @Test
    fun readResolvedUsesTheFeedBackCapByDefault() = runBlocking {
        repository.readResolvedRequests(conversationArea = "AGENT")

        assertEquals(
            listOf("AGENT" to AgentPendingRequestRepository.MAX_RESOLVED_PENDING_REQUESTS),
            port.readResolvedCalls,
        )
    }

    /** 没落库能力的实现（端口默认值）不能被当成"挂上了"：行是权威，没有行就没有挂起。 */
    @Test
    fun anImplementationThatDoesNotPersistNeverSuspendsTheTurn() = runBlocking {
        val noStore = TutorConsentRequests(object : AgentPendingRequestRepository {})

        assertNull(
            noStore.suspend(
                state = turn(),
                command = SuspendTurnForConsentCommand(
                    subject = TutorPermissionSubject.Tool(TutorToolName.NOTEBOOK_WRITE),
                    conversationArea = "AGENT",
                    conversationId = "conversation-1",
                    payloadJson = """{"problemRevisionId":"rev-1"}""",
                    occurredAtEpochMillis = 100L,
                ),
            ),
        )
    }

    private fun turn() = TutorTurnSendStateMachine.reduce(
        TutorSendState(),
        TutorSendAction.StudentMessagePersisted("logical-1", "message-1"),
    )

    private fun record(
        status: String,
        resolutionNote: String? = null,
    ) = AgentPendingRequestRecord(
        requestId = "agent-req:logical-1:OPEN_PROBLEM:0000000000000000",
        conversationArea = "AGENT",
        conversationId = "conversation-1",
        logicalOperationId = "logical-1",
        messageId = "message-1",
        kind = "OPEN_PROBLEM",
        payloadJson = """{"problemRevisionId":"rev-1"}""",
        status = status,
        createdAtEpochMillis = 100L,
        resolvedAtEpochMillis = if (status == "PENDING") null else 200L,
        resolutionNote = resolutionNote,
    )

    private class FakeAgentPendingRequestPort : AgentPendingRequestPort {
        val createdCommands = mutableListOf<CreateAgentPendingRequestDatabaseCommand>()
        val resolvedCommands = mutableListOf<ResolveAgentPendingRequestDatabaseCommand>()
        val pendingRows = MutableStateFlow<List<AgentPendingRequestRecord>>(emptyList())
        var resolvedRows: List<AgentPendingRequestRecord> = emptyList()
        val readResolvedCalls = mutableListOf<Pair<String, Int>>()

        override suspend fun createAgentPendingRequest(
            command: CreateAgentPendingRequestDatabaseCommand,
        ): AgentPendingRequestRecord {
            createdCommands += command
            val row = AgentPendingRequestRecord(
                requestId = command.requestId,
                conversationArea = command.conversationArea,
                conversationId = command.conversationId,
                logicalOperationId = command.logicalOperationId,
                messageId = command.messageId,
                kind = command.kind,
                payloadJson = command.payloadJson,
                status = "PENDING",
                createdAtEpochMillis = command.createdAtEpochMillis,
                resolvedAtEpochMillis = null,
                resolutionNote = null,
            )
            pendingRows.value = pendingRows.value + row
            return row
        }

        override suspend fun resolveAgentPendingRequest(
            command: ResolveAgentPendingRequestDatabaseCommand,
        ): AgentPendingRequestRecord {
            resolvedCommands += command
            val existing = pendingRows.value.single { it.requestId == command.requestId }
            pendingRows.value = pendingRows.value - existing
            return existing.copy(
                status = command.status,
                resolvedAtEpochMillis = command.resolvedAtEpochMillis,
                resolutionNote = command.resolutionNote,
            )
        }

        override fun observePendingAgentRequests(
            conversationArea: String?,
        ): Flow<List<AgentPendingRequestRecord>> = pendingRows

        override suspend fun readResolvedAgentPendingRequests(
            conversationArea: String,
            limit: Int,
        ): List<AgentPendingRequestRecord> {
            readResolvedCalls += conversationArea to limit
            return resolvedRows
        }
    }
}
