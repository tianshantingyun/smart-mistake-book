package com.tingyun.smartmistakebook.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 待确认请求的**纯语义**：幂等键、payload 形状、PENDING → 终态的状态机。
 *
 * 落库层（`AgentPendingRequestDao`）把同样三条不变量写进事务；这里测的是那份契约的
 * 可执行定义——两边不一致时，测试先炸的是这一份。
 */
class AgentPendingRequestTest {
    private fun draft(
        logicalOperationId: String = "logical-1",
        kind: AgentPendingRequestKind = AgentPendingRequestKind.START_EXPORT,
        payloadJson: String = """{"scope":"subjects"}""",
        conversationArea: String = "AGENT",
        conversationId: String = "conversation-1",
        messageId: String = "message-1",
    ) = AgentPendingRequestDraft(
        conversationArea = conversationArea,
        conversationId = conversationId,
        logicalOperationId = logicalOperationId,
        messageId = messageId,
        kind = kind,
        payloadJson = payloadJson,
    )

    private fun row(
        status: AgentPendingRequestStatus,
        resolvedAtEpochMillis: Long? = if (status.isTerminal) 200L else null,
        resolutionNote: String? = if (status == AgentPendingRequestStatus.PENDING) null else "note",
    ) = AgentPendingRequest(
        requestId = "agent-req:logical-1:START_EXPORT:0000000000000000",
        conversationArea = "AGENT",
        conversationId = "conversation-1",
        logicalOperationId = "logical-1",
        messageId = "message-1",
        kind = AgentPendingRequestKind.START_EXPORT,
        payloadJson = """{"scope":"subjects"}""",
        status = status,
        createdAtEpochMillis = 100L,
        resolvedAtEpochMillis = resolvedAtEpochMillis,
        resolutionNote = resolutionNote,
    )

    // --- 同一请求只挂一次：幂等键 ---

    @Test
    fun theSameRequestAlwaysDerivesTheSameId() {
        assertEquals(draft().requestId(), draft().requestId())
        assertEquals(
            agentPendingRequestId("logical-1", AgentPendingRequestKind.START_EXPORT, """{"a":1}"""),
            draft(logicalOperationId = "logical-1", payloadJson = """{"a":1}""").requestId(),
        )
    }

    @Test
    fun aDifferentTurnKindOrPayloadIsADifferentRequest() {
        assertNotEquals(draft().requestId(), draft(logicalOperationId = "logical-2").requestId())
        assertNotEquals(
            draft().requestId(),
            draft(kind = AgentPendingRequestKind.OPEN_PROBLEM).requestId(),
        )
        assertNotEquals(
            draft().requestId(),
            draft(payloadJson = """{"scope":"sections"}""").requestId(),
        )
    }

    /** 幂等键要能长期复算：算法换了，旧行就再也对不上同一请求——所以钉住已知向量。 */
    @Test
    fun thePayloadDigestIsTheFrozenFnv1a64() {
        assertEquals("cbf29ce484222325", fnv1a64Hex(""))
        assertEquals("af63dc4c8601ec8c", fnv1a64Hex("a"))
        assertEquals(
            "agent-req:logical-1:START_EXPORT:cbf29ce484222325",
            agentPendingRequestId("logical-1", AgentPendingRequestKind.START_EXPORT, ""),
        )
    }

    // --- payload 是固定参数，不是模型正文的容器 ---

    @Test
    fun aPayloadMustBeABoundedJsonObject() {
        requireAgentPendingRequestPayload(
            AgentPendingRequestKind.OPEN_PROBLEM,
            """{"problemId":"p1"}""",
        )

        val rejected = listOf(
            "problemId=p1",
            "[1,2,3]",
            "{unterminated",
            "",
            """{"note":"line
break"}""",
            """{"note":"""" + "x".repeat(MAX_AGENT_PENDING_REQUEST_PAYLOAD_CHARS) + """}""",
        )
        rejected.forEach { payload ->
            val failure = runCatching {
                requireAgentPendingRequestPayload(AgentPendingRequestKind.OPEN_PROBLEM, payload)
            }.exceptionOrNull()
            assertEquals(
                "Payload must be rejected: ${payload.take(24)}",
                IllegalArgumentException::class.java,
                failure?.javaClass,
            )
        }
    }

    // --- PENDING → 终态，不可回头 ---

    @Test
    fun everyDecisionSettlesAPendingRequestIntoItsOwnTerminalStatus() {
        AgentPendingRequestDecision.entries.forEach { decision ->
            assertEquals(
                decision.terminalStatus,
                agentPendingRequestResolution(AgentPendingRequestStatus.PENDING, decision),
            )
        }
        assertEquals(
            AgentPendingRequestStatus.ACCEPTED,
            agentPendingRequestResolution(
                AgentPendingRequestStatus.PENDING,
                AgentPendingRequestDecision.ACCEPT,
            ),
        )
    }

    @Test
    fun repeatingTheSameDecisionIsIdempotentAndContradictingItIsNot() {
        AgentPendingRequestStatus.entries.filter { it.isTerminal }.forEach { terminal ->
            val sameDecision = AgentPendingRequestDecision.entries.single {
                it.terminalStatus == terminal
            }
            assertEquals(terminal, agentPendingRequestResolution(terminal, sameDecision))

            AgentPendingRequestDecision.entries
                .filter { it != sameDecision }
                .forEach { other ->
                    assertNull(
                        "A settled request must not be re-decided as $other",
                        agentPendingRequestResolution(terminal, other),
                    )
                }
        }
    }

    @Test
    fun noDecisionTurnsASettledRequestBackToPending() {
        AgentPendingRequestStatus.entries.forEach { current ->
            AgentPendingRequestDecision.entries.forEach { decision ->
                assertNotEquals(
                    AgentPendingRequestStatus.PENDING,
                    agentPendingRequestResolution(current, decision),
                )
            }
        }
    }

    @Test
    fun aRowRequiresATerminalStatusAndItsTraceToAgree() {
        row(AgentPendingRequestStatus.ACCEPTED)

        assertRejected { row(AgentPendingRequestStatus.PENDING, resolvedAtEpochMillis = 200L) }
        assertRejected { row(AgentPendingRequestStatus.DECLINED, resolvedAtEpochMillis = null) }
        assertRejected { row(AgentPendingRequestStatus.ACCEPTED, resolutionNote = " ") }
    }

    /** 终态行的裁决时间与理由一起给；PENDING 行两列都不该有。 */
    @Test
    fun anUnsettledRowCarriesNoTrace() {
        val pending = row(AgentPendingRequestStatus.PENDING)
        assertNull(pending.resolvedAtEpochMillis)
        assertNull(pending.resolutionNote)
        assertRejected { pending.copy(resolutionNote = "理由") }
    }

    private fun assertRejected(block: () -> Unit) {
        val failure = runCatching(block).exceptionOrNull()
        assertEquals(
            "Expected an invariant failure",
            IllegalArgumentException::class.java,
            failure?.javaClass,
        )
    }
}
