package com.tingyun.smartmistakebook.core.domain

import com.tingyun.smartmistakebook.core.model.MistakePdfLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        kind: AgentPendingRequestKind = AgentPendingRequestKind.SAVE_TO_NOTEBOOK,
        // 形状必须是这个 kind 声明过的（按 kind 逐字核对是落库口的一部分）。
        payloadJson: String = """{"captureSessionId":"session-1"}""",
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
        requestId = "agent-req:logical-1:SAVE_TO_NOTEBOOK:0000000000000000",
        conversationArea = "AGENT",
        conversationId = "conversation-1",
        logicalOperationId = "logical-1",
        messageId = "message-1",
        kind = AgentPendingRequestKind.SAVE_TO_NOTEBOOK,
        payloadJson = """{"captureSessionId":"session-1"}""",
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
            agentPendingRequestId(
                "logical-1",
                AgentPendingRequestKind.SAVE_TO_NOTEBOOK,
                """{"captureSessionId":"session-1"}""",
            ),
            draft(
                logicalOperationId = "logical-1",
                payloadJson = """{"captureSessionId":"session-1"}""",
            ).requestId(),
        )
    }

    @Test
    fun aDifferentTurnKindOrPayloadIsADifferentRequest() {
        assertNotEquals(draft().requestId(), draft(logicalOperationId = "logical-2").requestId())
        assertNotEquals(
            draft().requestId(),
            draft(
                kind = AgentPendingRequestKind.OPEN_PROBLEM,
                payloadJson = """{"libraryProblemId":"p1"}""",
            ).requestId(),
        )
        assertNotEquals(
            draft().requestId(),
            draft(payloadJson = """{"captureSessionId":"session-2"}""").requestId(),
        )
    }

    /** 幂等键要能长期复算：算法换了，旧行就再也对不上同一请求——所以钉住已知向量。 */
    @Test
    fun thePayloadDigestIsTheFrozenFnv1a64() {
        assertEquals("cbf29ce484222325", fnv1a64Hex(""))
        assertEquals("af63dc4c8601ec8c", fnv1a64Hex("a"))
        // 4B B3：START_EXPORT 的 payload 不再是空形状——用**真实的**导出提议 payload 钉向量
        // （空串向量是改前的形状，留着会让下一个人以为空 payload 仍然合法）。
        val exportPayload = exportProposalPayload(
            MistakePdfLayout(
                templateId = MistakePdfLayout.TEMPLATE_PRACTICE_SHEET,
                columnCount = 2,
            ),
        )
        assertEquals(
            "agent-req:logical-1:START_EXPORT:d5c84a25c896f0b4",
            agentPendingRequestId("logical-1", AgentPendingRequestKind.START_EXPORT, exportPayload),
        )
    }

    // --- payload 是固定参数，不是模型正文的容器 ---

    @Test
    fun aPayloadMustBeABoundedJsonObject() {
        requireAgentPendingRequestPayload(
            AgentPendingRequestKind.OPEN_PROBLEM,
            """{"libraryProblemId":"p1"}""",
        )

        val rejected = listOf(
            "problemId=p1",
            "[1,2,3]",
            "{unterminated",
            "",
            """{"libraryProblemId":"line
break"}""",
            """{"libraryProblemId":"""" + "x".repeat(MAX_AGENT_PENDING_REQUEST_PAYLOAD_CHARS) + """}""",
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

    // --- START_EXPORT 的固定形状（4B B3-1）：模板枚举 + 完整版式，缺字段/越界一律拒 ---

    @Test
    fun anExportProposalPayloadIsAcceptedAndReadBackAsTheSameLayout() {
        val layout = MistakePdfLayout(
            templateId = MistakePdfLayout.TEMPLATE_PRACTICE_SHEET,
            marginPt = 60,
            fontScale = 3,
            columnCount = 2,
            blockOrder = listOf("paragraph", "choice_group"),
            imageScale = 0.8f,
            includeAnswer = true,
            includeNote = true,
        )
        val payload = exportProposalPayload(layout)

        requireAgentPendingRequestPayload(AgentPendingRequestKind.START_EXPORT, payload)

        assertEquals(layout, tutorLocalActionExportProposal(payload))
    }

    @Test
    fun anExportProposalPayloadMissingEitherFieldIsRefused() {
        val payload = exportProposalPayload(MistakePdfLayout(templateId = "practice_sheet"))

        assertRejected {
            requireAgentPendingRequestPayload(
                AgentPendingRequestKind.START_EXPORT,
                withoutExportPayloadKey(payload, "templateId"),
            )
        }
        assertRejected {
            requireAgentPendingRequestPayload(
                AgentPendingRequestKind.START_EXPORT,
                withoutExportPayloadKey(payload, "layout"),
            )
        }
    }

    @Test
    fun anExportProposalWithAnUnknownTemplateOrExtraFieldIsRefused() {
        val payload = exportProposalPayload(MistakePdfLayout(templateId = "practice_sheet"))

        assertRejected {
            requireAgentPendingRequestPayload(
                AgentPendingRequestKind.START_EXPORT,
                payload.replace("\"practice_sheet\"", "\"poster\""),
            )
        }
        assertRejected {
            requireAgentPendingRequestPayload(
                AgentPendingRequestKind.START_EXPORT,
                payload.dropLast(1) + ""","problemIds":["p1"]}""",
            )
        }
    }

    @Test
    fun anExportProposalWithAnOutOfRangeOrIncompleteLayoutIsRefused() {
        val payload = exportProposalPayload(MistakePdfLayout(templateId = "practice_sheet"))

        // 越界（marginPt=100）与缺字段（去掉 includeNote）都必须在落库口被拒。
        assertRejected {
            requireAgentPendingRequestPayload(
                AgentPendingRequestKind.START_EXPORT,
                payload.replace("\"marginPt\":48", "\"marginPt\":100"),
            )
        }
        assertRejected {
            requireAgentPendingRequestPayload(
                AgentPendingRequestKind.START_EXPORT,
                payload.replace(",\"includeNote\":false", ""),
            )
        }
        // 类型不符（marginPt 写成字符串）同样拒。
        assertRejected {
            requireAgentPendingRequestPayload(
                AgentPendingRequestKind.START_EXPORT,
                payload.replace("\"marginPt\":48", "\"marginPt\":\"48\""),
            )
        }
        // 顶层 templateId 与 layout 内的不一致：拒（两处必须同一份事实）。
        assertRejected {
            requireAgentPendingRequestPayload(
                AgentPendingRequestKind.START_EXPORT,
                payload.replaceFirst(
                    "\"templateId\":\"practice_sheet\"",
                    "\"templateId\":\"compact\"",
                ),
            )
        }
    }

    private fun withoutExportPayloadKey(payload: String, key: String): String {
        val json = kotlinx.serialization.json.Json
        val root = json.parseToJsonElement(payload) as kotlinx.serialization.json.JsonObject
        return json.encodeToString(
            kotlinx.serialization.json.JsonObject.serializer(),
            kotlinx.serialization.json.JsonObject(root.filterKeys { it != key }),
        )
    }

    // --- 升级兼容（P1-a）：写口严格、读回宽容，旧行不把读路径抛死 ---

    /**
     * 旧版本（`d2317f5d`）写出的 START_EXPORT 形状是**空对象 `{}`**：读回必须放行（否则
     * `toDomain()` 抛 → 挂卡区/回喂区整片消失），写口必须仍然严格（新行按新形状说话）。
     */
    @Test
    fun aLegacyEmptyExportPayloadIsReadableWhileTheWritePortStaysStrict() {
        val legacy = legacyExportRow("{}")

        // 读回：不抛；语义解析读不出提议，交给 UI 走"旧版本"降级路径。
        assertNull(tutorLocalActionExportProposal(legacy.payloadJson))
        assertTrue(agentPendingRequestExportPayloadIsLegacy(legacy.payloadJson))

        // 写口：不许再写出旧形状（新行必须恰好 {templateId, layout}）。
        assertRejected {
            AgentPendingRequestDraft(
                conversationArea = "AGENT",
                conversationId = "conversation-1",
                logicalOperationId = "op-legacy",
                messageId = "message-1",
                kind = AgentPendingRequestKind.START_EXPORT,
                payloadJson = "{}",
            )
        }

        // 读回宽容**只对 START_EXPORT**：其余 kind 的坏形状照旧抛。
        assertRejected {
            legacyExportRow("""{"problemId":"p1"}""").copy(kind = AgentPendingRequestKind.OPEN_PROBLEM)
        }
        // 结构性规则在读回路径上仍然生效（不是"什么都收"）：非 JSON 对象照旧抛。
        assertRejected { legacyExportRow("not json") }
        assertRejected { legacyExportRow("[1,2]") }

        // 新版形状的行读回照旧通过。
        legacyExportRow(
            exportProposalPayload(MistakePdfLayout(templateId = "practice_sheet")),
        )
    }

    private fun legacyExportRow(payloadJson: String): AgentPendingRequest = AgentPendingRequest(
        requestId = "agent-req:op-legacy:START_EXPORT:0000000000000000",
        conversationArea = "AGENT",
        conversationId = "conversation-1",
        logicalOperationId = "op-legacy",
        messageId = "message-1",
        kind = AgentPendingRequestKind.START_EXPORT,
        payloadJson = payloadJson,
        status = AgentPendingRequestStatus.PENDING,
        createdAtEpochMillis = 100L,
    )

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
