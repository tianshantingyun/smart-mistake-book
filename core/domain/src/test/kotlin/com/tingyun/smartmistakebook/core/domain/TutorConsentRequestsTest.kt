package com.tingyun.smartmistakebook.core.domain

import com.tingyun.smartmistakebook.core.model.TutorLocalAction
import com.tingyun.smartmistakebook.core.model.TutorToolName
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 确认卡接线（插眼 5）：ask 档对象 → 行 + `AWAITING_CONSENT`；裁决 → 终态 + 回合继续。
 *
 * 这里用一份内存替身扮演落库层：替身只做"存/取/冲突"三种动作，**判定用的是域里的那份
 * 纯状态机**（落库层把同一份不变量写进 SQL，SQL 那一侧由 `AgentPendingRequestDaoInstrumentedTest`
 * 对着真库跑）。替身的落库可以关掉——用来证明"没落成就不许推进相位"。
 */
class TutorConsentRequestsTest {
    private val requests = FakePendingRequests()
    private val consent = TutorConsentRequests(requests)

    private fun turn(
        logicalOperationId: String = "logical-1",
        messageId: String = "message-1",
    ) = TutorTurnSendStateMachine.reduce(
        TutorSendState(),
        TutorSendAction.StudentMessagePersisted(logicalOperationId, messageId),
    )

    private fun suspendCommand(
        subject: TutorPermissionSubject = TutorPermissionSubject.LocalAction(
            TutorLocalAction.OPEN_PROBLEM,
        ),
        payloadJson: String = """{"problemRevisionId":"rev-1"}""",
        context: TutorRoundPermissionContext = TutorRoundPermissionContext(),
        occurredAtEpochMillis: Long = 100L,
    ) = SuspendTurnForConsentCommand(
        subject = subject,
        conversationArea = "AGENT",
        conversationId = "conversation-1",
        payloadJson = payloadJson,
        occurredAtEpochMillis = occurredAtEpochMillis,
        context = context,
    )

    @Test
    fun anAdmittedAskRequestHangsTheTurnAndLeavesTheCardInTheDatabase() =
        runBlocking {
            val suspended = consent.suspend(state = turn(), command = suspendCommand())

            assertNotNull(suspended)
            assertEquals(TutorSendPhase.AWAITING_CONSENT, suspended!!.state.phase)
            assertEquals(AgentPendingRequestStatus.PENDING, suspended.request.status)
            assertEquals(AgentPendingRequestKind.OPEN_PROBLEM, suspended.request.kind)
            assertEquals("conversation-1", suspended.request.conversationId)
            assertEquals("logical-1", suspended.request.logicalOperationId)
            assertEquals("message-1", suspended.request.messageId)
            // 行是权威：库里那一条就是返回的这一条。
            assertEquals(listOf(suspended.request), requests.rows.values.toList())
        }

    @Test
    fun onlyTheAskTierHangs(): Unit = runBlocking {
        val refusedSubjects = listOf(
            TutorPermissionSubject.Tool(TutorToolName.MASTERY_UPDATE),
            TutorPermissionSubject.Tool(TutorToolName.KNOWLEDGE_READ),
            TutorPermissionSubject.Tool(TutorToolName.NOTEBOOK_READ),
            TutorPermissionSubject.Tool(TutorToolName.MASTERY_READ),
        )
        refusedSubjects.forEach { subject ->
            val failure = runCatching {
                consent.suspend(state = turn(), command = suspendCommand(subject = subject))
            }.exceptionOrNull()
            assertEquals(
                "$subject must not hang a consent card",
                IllegalArgumentException::class.java,
                failure?.javaClass,
            )
        }
        // 没准入的 ask 对象也不挂：连卡都不该出现。
        val notAdmitted = runCatching {
            consent.suspend(
                state = turn(),
                command = suspendCommand(
                    context = TutorRoundPermissionContext(allowedActions = emptySet()),
                ),
            )
        }.exceptionOrNull()
        assertEquals(IllegalArgumentException::class.java, notAdmitted?.javaClass)
        assertTrue(requests.rows.isEmpty())
    }

    /** 没落成行就不许推进相位：一张挂起却没有行的卡，进程一死就悬死。 */
    @Test
    fun withoutAPersistedRowTheTurnIsNotSuspended(): Unit = runBlocking {
        requests.persistEnabled = false

        val suspended = consent.suspend(state = turn(), command = suspendCommand())

        assertNull(suspended)
        assertTrue(requests.rows.isEmpty())
    }

    /** 陈旧回合（已经走过去了）不在库里留下一张没有主人的卡。 */
    @Test
    fun aFinishedTurnNeverLeavesACardBehind(): Unit = runBlocking {
        val completed = TutorTurnSendStateMachine.reduce(
            turn().let { TutorTurnSendStateMachine.reduce(it, TutorSendAction.ConsentGranted("logical-1", "message-1")) },
            TutorSendAction.DispatchStarted("logical-1", "message-1"),
        ).let {
            TutorTurnSendStateMachine.reduce(it, TutorSendAction.DispatchSucceeded("logical-1", "message-1"))
        }

        assertNull(consent.suspend(state = completed, command = suspendCommand()))
        assertTrue(requests.rows.isEmpty())
    }

    @Test
    fun theSameRequestHangsExactlyOnce(): Unit = runBlocking {
        val first = consent.suspend(state = turn(), command = suspendCommand())
        val second = consent.suspend(state = turn(), command = suspendCommand())

        assertEquals(1, requests.rows.size)
        assertEquals(first!!.request.requestId, second!!.request.requestId)
        assertEquals(TutorSendPhase.AWAITING_CONSENT, second.state.phase)
    }

    /**
     * 幂等键含 payload：同一回合里对**另一个目标**的同类请求是**另一张卡**（"打开第 1 题、
     * 再打开第 2 题"在同一回合里都可能发生），只有"同一目标被重放"才落回同一张卡。
     */
    @Test
    fun anotherTargetInTheSameTurnIsASeparateCard(): Unit = runBlocking {
        val first = consent.suspend(state = turn(), command = suspendCommand())!!
        val second = consent.suspend(
            state = turn(),
            command = suspendCommand(payloadJson = """{"problemRevisionId":"rev-2"}"""),
        )!!

        assertEquals(2, requests.rows.size)
        assertNotEquals(first.request.requestId, second.request.requestId)
        assertEquals(TutorSendPhase.AWAITING_CONSENT, second.state.phase)
    }

    @Test
    fun anAcceptedCardSettlesAndDispatchesTheWaitingTurn(): Unit = runBlocking {
        val suspended = consent.suspend(state = turn(), command = suspendCommand())!!
        val resolved = consent.decide(
            state = suspended.state,
            command = DecidePendingRequestCommand(
                requestId = suspended.request.requestId,
                decision = AgentPendingRequestDecision.ACCEPT,
                resolutionNote = "学生点了确认",
                occurredAtEpochMillis = 200L,
            ),
        )!!

        assertEquals(AgentPendingRequestStatus.ACCEPTED, resolved.request.status)
        assertEquals(200L, resolved.request.resolvedAtEpochMillis)
        assertEquals("学生点了确认", resolved.request.resolutionNote)
        assertEquals(TutorSendPhase.DISPATCHING, resolved.state.phase)
        assertEquals(1, resolved.state.dispatchAttemptCount)
        // 回喂通道的行与落库的行同源。
        assertEquals(resolved.request.status, resolved.outcome.status)
        assertEquals(suspended.request.requestId, resolved.outcome.requestId)
    }

    /** 拒绝也继续回合：模型要知道"没执行"，而不是被悬在那里（D-K2e 的裁决回喂）。 */
    @Test
    fun aDeclinedCardStillContinuesTheTurnWithTheTrace(): Unit = runBlocking {
        val suspended = consent.suspend(state = turn(), command = suspendCommand())!!
        val resolved = consent.decide(
            state = suspended.state,
            command = DecidePendingRequestCommand(
                requestId = suspended.request.requestId,
                decision = AgentPendingRequestDecision.DECLINE,
                resolutionNote = "学生这次不导出",
                occurredAtEpochMillis = 200L,
            ),
        )!!

        assertEquals(AgentPendingRequestStatus.DECLINED, resolved.request.status)
        assertEquals("学生这次不导出", resolved.outcome.resolutionNote)
        assertEquals(TutorSendPhase.DISPATCHING, resolved.state.phase)
        assertEquals(AgentPendingRequestStatus.DECLINED, requests.rows.values.single().status)
    }

    @Test
    fun aDoubleTapDoesNotDispatchTwice(): Unit = runBlocking {
        val suspended = consent.suspend(state = turn(), command = suspendCommand())!!
        val command = DecidePendingRequestCommand(
            requestId = suspended.request.requestId,
            decision = AgentPendingRequestDecision.ACCEPT,
            occurredAtEpochMillis = 200L,
        )

        val first = consent.decide(state = suspended.state, command = command)!!
        val second = consent.decide(state = first.state, command = command)!!

        assertEquals(1, first.state.dispatchAttemptCount)
        assertEquals(1, second.state.dispatchAttemptCount)
        assertEquals(TutorSendPhase.DISPATCHING, second.state.phase)
        assertEquals(200L, second.request.resolvedAtEpochMillis)
    }

    /** 裁决一定落库；回合已经走过去了就不推进它（相位不被过期结果改写）。 */
    @Test
    fun aDecisionOnAMovedOnTurnLeavesTheTraceWithoutTouchingThePhase() =
        runBlocking {
            val suspended = consent.suspend(state = turn(), command = suspendCommand())!!
            val idle = TutorTurnSendStateMachine.reduce(TutorSendState(), TutorSendAction.Reset())

            val resolved = consent.decide(
                state = idle,
                command = DecidePendingRequestCommand(
                    requestId = suspended.request.requestId,
                    decision = AgentPendingRequestDecision.IGNORE,
                    occurredAtEpochMillis = 200L,
                ),
            )!!

            assertEquals(AgentPendingRequestStatus.IGNORED, resolved.request.status)
            assertEquals(TutorSendPhase.IDLE, resolved.state.phase)
        }

    @Test
    fun contradictingADecisionIsRefusedInsteadOfRewritingHistory(): Unit =
        runBlocking {
            val suspended = consent.suspend(state = turn(), command = suspendCommand())!!
            consent.decide(
                state = suspended.state,
                command = DecidePendingRequestCommand(
                    requestId = suspended.request.requestId,
                    decision = AgentPendingRequestDecision.ACCEPT,
                    occurredAtEpochMillis = 200L,
                ),
            )

            val failure = runCatching {
                consent.decide(
                    state = suspended.state,
                    command = DecidePendingRequestCommand(
                        requestId = suspended.request.requestId,
                        decision = AgentPendingRequestDecision.DECLINE,
                        occurredAtEpochMillis = 300L,
                    ),
                )
            }.exceptionOrNull()

            assertEquals(IllegalStateException::class.java, failure?.javaClass)
            assertEquals(AgentPendingRequestStatus.ACCEPTED, requests.rows.values.single().status)
        }

    @Test
    fun aRestartRebuildsTheSameCardFromThePendingRow(): Unit = runBlocking {
        val suspended = consent.suspend(state = turn(), command = suspendCommand())!!
        // 进程死亡：内存里的相位没了，行还在。重启后回合从"学生消息已落库"重新推进。
        val restarted = turn()

        val rebuilt = consent.resumeAfterRestart(restarted, suspended.request)!!

        assertEquals(TutorSendPhase.AWAITING_CONSENT, rebuilt.state.phase)
        assertEquals(suspended.request.requestId, rebuilt.request.requestId)
        assertEquals(0, rebuilt.state.dispatchAttemptCount)
    }

    @Test
    fun aCardOfAnotherTurnIsNotRebuiltOntoThisTurn(): Unit = runBlocking {
        val suspended = consent.suspend(state = turn(), command = suspendCommand())!!
        val otherTurn = turn(logicalOperationId = "logical-2", messageId = "message-2")

        assertNull(consent.resumeAfterRestart(otherTurn, suspended.request))

        val settled = suspended.request.copy(
            status = AgentPendingRequestStatus.ACCEPTED,
            resolvedAtEpochMillis = 300L,
        )
        assertNull(consent.resumeAfterRestart(turn(), settled))
    }

    private class FakePendingRequests : AgentPendingRequestRepository {
        val rows = linkedMapOf<String, AgentPendingRequest>()
        var persistEnabled = true

        override suspend fun createRequest(
            draft: AgentPendingRequestDraft,
            createdAtEpochMillis: Long,
        ): AgentPendingRequest? {
            if (!persistEnabled) return null
            val requestId = draft.requestId()
            rows[requestId]?.let { existing ->
                if (
                    existing.kind != draft.kind ||
                    existing.payloadJson != draft.payloadJson ||
                    existing.conversationId != draft.conversationId ||
                    existing.conversationArea != draft.conversationArea
                ) {
                    throw IllegalStateException("agent_pending_request $requestId conflicts")
                }
                return existing
            }
            return AgentPendingRequest(
                requestId = requestId,
                conversationArea = draft.conversationArea,
                conversationId = draft.conversationId,
                logicalOperationId = draft.logicalOperationId,
                messageId = draft.messageId,
                kind = draft.kind,
                payloadJson = draft.payloadJson,
                status = AgentPendingRequestStatus.PENDING,
                createdAtEpochMillis = createdAtEpochMillis,
            ).also { rows[requestId] = it }
        }

        override suspend fun resolveRequest(
            requestId: String,
            decision: AgentPendingRequestDecision,
            resolutionNote: String?,
            resolvedAtEpochMillis: Long,
        ): AgentPendingRequest? {
            if (!persistEnabled) return null
            val existing = rows[requestId]
                ?: throw IllegalStateException("agent_pending_request $requestId is missing")
            val next = agentPendingRequestResolution(existing.status, decision)
                ?: throw IllegalStateException("agent_pending_request $requestId is already settled")
            return existing.copy(
                status = next,
                resolvedAtEpochMillis = resolvedAtEpochMillis,
                resolutionNote = resolutionNote,
            ).also { rows[requestId] = it }
        }
    }
}
