package com.tingyun.smartmistakebook.core.database

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 待确认请求表（插眼 5）对着**真库**的三条不变量。单测里那份纯状态机证明不了这些：
 * 它们在 SQL 里（主键 + `INSERT IGNORE`、`WHERE status = 'PENDING'` 的比较交换、
 * 外键 CASCADE），只有真库能证。
 *
 * 1. **同一请求只挂一次**：同 id 同载荷重放 → 一行；同 id 不同载荷 → 冲突，不覆盖已挂的卡。
 * 2. **PENDING → 终态，不可回头**：终态行再裁决同一结果 → 幂等；不同结果 → 冲突。
 * 3. **会话删了卡也走**：外键 CASCADE（卡不可能挂在一个已经不存在的会话上）。
 */
@RunWith(AndroidJUnit4::class)
class AgentPendingRequestInstrumentedTest {
    private lateinit var store: StudyDatabasePort

    @Before
    fun setUp() {
        store = StudyDatabaseFactory.openInMemory(ApplicationProvider.getApplicationContext<Context>())
    }

    @After
    fun tearDown() {
        store.close()
    }

    @Test
    fun theSameRequestHangsExactlyOnce() = runBlocking {
        seedConversation()

        val first = store.createAgentPendingRequest(command())!!
        val replay = store.createAgentPendingRequest(command(createdAtEpochMillis = 999L))!!

        assertEquals(first.requestId, replay.requestId)
        assertEquals(1, store.readPendingAgentRequests().size)
        assertEquals("PENDING", replay.status)
        // 重放不刷新时间：行记的是"什么时候挂的"，不是"最后一次被提起"。
        assertEquals(100L, replay.createdAtEpochMillis)
    }

    @Test
    fun aSecondPayloadOnTheSameRequestIdNeverOverwritesTheHangingCard() = runBlocking {
        seedConversation()
        store.createAgentPendingRequest(command())

        val failure = runCatching {
            store.createAgentPendingRequest(command(payloadJson = """{"libraryProblemId":"rev-2"}"""))
        }.exceptionOrNull()

        assertEquals(ImmutablePayloadConflictException::class.java, failure?.javaClass)
        assertEquals(
            listOf("""{"libraryProblemId":"rev-1"}"""),
            store.readPendingAgentRequests().map { it.payloadJson },
        )
    }

    @Test
    fun decidingSettlesTheCardWithItsTraceAndRepeatingTheSameDecisionIsIdempotent() =
        runBlocking {
            seedConversation()
            val request = store.createAgentPendingRequest(command())!!

            val decided = store.resolveAgentPendingRequest(
                ResolveAgentPendingRequestDatabaseCommand(
                    requestId = request.requestId,
                    status = "DECLINED",
                    resolutionNote = "这次不导出",
                    resolvedAtEpochMillis = 200L,
                ),
            )!!

            assertEquals("DECLINED", decided.status)
            assertEquals(200L, decided.resolvedAtEpochMillis)
            assertEquals("这次不导出", decided.resolutionNote)
            assertTrue(store.readPendingAgentRequests().isEmpty())

            // 双击/两处 UI 先后点了同一个决定：幂等，不报错、不改历史。
            val repeated = store.resolveAgentPendingRequest(
                ResolveAgentPendingRequestDatabaseCommand(
                    requestId = request.requestId,
                    status = "DECLINED",
                    resolutionNote = "换了一句理由",
                    resolvedAtEpochMillis = 300L,
                ),
            )!!
            assertEquals(200L, repeated.resolvedAtEpochMillis)
            assertEquals("这次不导出", repeated.resolutionNote)
        }

    @Test
    fun aContradictingDecisionIsRefusedInsteadOfRewritingHistory() = runBlocking {
        seedConversation()
        val request = store.createAgentPendingRequest(command())!!
        store.resolveAgentPendingRequest(
            ResolveAgentPendingRequestDatabaseCommand(
                requestId = request.requestId,
                status = "ACCEPTED",
                resolutionNote = null,
                resolvedAtEpochMillis = 200L,
            ),
        )

        val failure = runCatching {
            store.resolveAgentPendingRequest(
                ResolveAgentPendingRequestDatabaseCommand(
                    requestId = request.requestId,
                    status = "IGNORED",
                    resolutionNote = "学生划走了",
                    resolvedAtEpochMillis = 300L,
                ),
            )
        }.exceptionOrNull()

        assertEquals(ImmutablePayloadConflictException::class.java, failure?.javaClass)
        assertEquals(
            listOf("ACCEPTED"),
            store.readResolvedAgentPendingRequests("AGENT", limit = 10).map { it.status },
        )
    }

    @Test
    fun aPendingRequestMustBeSettledIntoATerminalStatus() = runBlocking {
        seedConversation()
        val request = store.createAgentPendingRequest(command())!!

        val failure = runCatching {
            store.resolveAgentPendingRequest(
                ResolveAgentPendingRequestDatabaseCommand(
                    requestId = request.requestId,
                    status = "PENDING",
                    resolutionNote = null,
                    resolvedAtEpochMillis = 200L,
                ),
            )
        }.exceptionOrNull()

        assertEquals(IllegalArgumentException::class.java, failure?.javaClass)
        assertEquals(1, store.readPendingAgentRequests().size)
    }

    @Test
    fun aRequestForAConversationThatNeverHangedIsRefused() = runBlocking {
        seedConversation()

        val failure = runCatching {
            store.resolveAgentPendingRequest(
                ResolveAgentPendingRequestDatabaseCommand(
                    requestId = "agent-req:never-hung",
                    status = "ACCEPTED",
                    resolutionNote = null,
                    resolvedAtEpochMillis = 200L,
                ),
            )
        }.exceptionOrNull()

        assertEquals(ImmutablePayloadConflictException::class.java, failure?.javaClass)
    }

    /** 悬浮球待确认层按会话区读未裁决的卡；裁决过的卡不再出现在待确认清单里。 */
    @Test
    fun pendingCardsAreObservablePerArea() = runBlocking {
        seedConversation(conversationId = "conversation-1")
        seedConversation(conversationId = "conversation-2")
        val first = store.createAgentPendingRequest(command(conversationId = "conversation-1"))!!
        store.createAgentPendingRequest(
            command(
                conversationId = "conversation-2",
                conversationArea = "REVIEW_MISTAKE",
                logicalOperationId = "logical-2",
            ),
        )

        assertEquals(
            setOf("conversation-1", "conversation-2"),
            store.observePendingAgentRequests(conversationArea = null).first()
                .map { it.conversationId }.toSet(),
        )
        assertEquals(
            listOf("conversation-1"),
            store.observePendingAgentRequests(conversationArea = "AGENT").first()
                .map { it.conversationId },
        )
        assertEquals(
            listOf("conversation-2"),
            store.readPendingAgentRequests(conversationArea = "REVIEW_MISTAKE")
                .map { it.conversationId },
        )

        store.resolveAgentPendingRequest(
            ResolveAgentPendingRequestDatabaseCommand(
                requestId = first.requestId,
                status = "ACCEPTED",
                resolutionNote = null,
                resolvedAtEpochMillis = 200L,
            ),
        )
        assertEquals(
            listOf("conversation-2"),
            store.observePendingAgentRequests(conversationArea = null).first()
                .map { it.conversationId },
        )
    }

    /** 回喂通道：已裁决的行按裁决时间倒序读回，带理由与时刻（同一份文本两处用，不写两遍）。 */
    @Test
    fun resolvedCardsReadBackNewestFirstWithTheirTrace() = runBlocking {
        seedConversation()
        val first = store.createAgentPendingRequest(command())!!
        val second = store.createAgentPendingRequest(
            command(logicalOperationId = "logical-2", payloadJson = """{"libraryProblemId":"rev-2"}"""),
        )!!
        store.resolveAgentPendingRequest(
            ResolveAgentPendingRequestDatabaseCommand(
                requestId = first.requestId,
                status = "DECLINED",
                resolutionNote = "不导出",
                resolvedAtEpochMillis = 200L,
            ),
        )
        store.resolveAgentPendingRequest(
            ResolveAgentPendingRequestDatabaseCommand(
                requestId = second.requestId,
                status = "ACCEPTED",
                resolutionNote = null,
                resolvedAtEpochMillis = 300L,
            ),
        )

        val resolved = store.readResolvedAgentPendingRequests("AGENT", limit = 10)

        assertEquals(listOf(second.requestId, first.requestId), resolved.map { it.requestId })
        assertEquals(listOf("ACCEPTED", "DECLINED"), resolved.map { it.status })
        assertEquals("不导出", resolved[1].resolutionNote)
        assertNull(resolved[0].resolutionNote)
    }

    /** 会话删了，它的待确认卡跟着走：卡不可能挂在一个已经不存在的会话上。 */
    @Test
    fun deletingTheConversationTakesItsCardsWithIt() = runBlocking {
        seedConversation()
        store.createAgentPendingRequest(command())!!

        store.deleteTutorConversation("conversation-1")

        assertTrue(store.readPendingAgentRequests().isEmpty())
    }

    private suspend fun seedConversation(conversationId: String = "conversation-1") {
        store.createTutorConversation(
            CreateTutorConversationDatabaseCommand(
                conversationId = conversationId,
                anchorKind = "TEXT_ONLY",
                anchorId = null,
                anchorRevisionId = null,
                title = null,
                createdAtEpochMillis = 1L,
            ),
        )
    }

    private fun command(
        conversationId: String = "conversation-1",
        conversationArea: String = "AGENT",
        logicalOperationId: String = "logical-1",
        payloadJson: String = """{"libraryProblemId":"rev-1"}""",
        createdAtEpochMillis: Long = 100L,
    ) = CreateAgentPendingRequestDatabaseCommand(
        requestId = "agent-req:$logicalOperationId:OPEN_PROBLEM:${logicalOperationId.length}",
        conversationArea = conversationArea,
        conversationId = conversationId,
        logicalOperationId = logicalOperationId,
        messageId = "message-1",
        kind = "OPEN_PROBLEM",
        payloadJson = payloadJson,
        createdAtEpochMillis = createdAtEpochMillis,
    )
}
