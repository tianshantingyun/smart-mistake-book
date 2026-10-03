package com.tingyun.smartmistakebook.feature.tutor

import com.tingyun.smartmistakebook.core.domain.AgentPendingRequest
import com.tingyun.smartmistakebook.core.domain.AgentPendingRequestDecision
import com.tingyun.smartmistakebook.core.domain.AgentPendingRequestDraft
import com.tingyun.smartmistakebook.core.domain.AgentPendingRequestKind
import com.tingyun.smartmistakebook.core.domain.AgentPendingRequestRepository
import com.tingyun.smartmistakebook.core.domain.AgentPendingRequestStatus
import com.tingyun.smartmistakebook.core.domain.TutorAttachedImageIntakeResult
import com.tingyun.smartmistakebook.core.domain.TutorConsentSuspension
import com.tingyun.smartmistakebook.core.domain.TutorLocalActionContext
import com.tingyun.smartmistakebook.core.domain.TutorPermissionSubject
import com.tingyun.smartmistakebook.core.domain.TutorSendAction
import com.tingyun.smartmistakebook.core.domain.TutorSendPhase
import com.tingyun.smartmistakebook.core.domain.TutorSendState
import com.tingyun.smartmistakebook.core.domain.TutorTurnSendStateMachine
import com.tingyun.smartmistakebook.core.domain.tutorLocalActionAdmission
import com.tingyun.smartmistakebook.core.domain.tutorLocalActionContext
import com.tingyun.smartmistakebook.core.domain.tutorLocalActionExportProposal
import com.tingyun.smartmistakebook.core.model.MistakePdfLayout
import com.tingyun.smartmistakebook.core.model.TutorLocalAction
import com.tingyun.smartmistakebook.core.model.TutorLocalActionRequest
import com.tingyun.smartmistakebook.core.model.TutorToolName
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 本地动作通道的**五 kind 全链**（A4 / D-K2e）：建卡 → 裁决 → 执行 → 回喂，每种拼写各一条。
 *
 * 判别格是**每一 kind 都真的走到它的落点**（工具拼写与动作拼写落到同一张卡、导出与复习计划如实
 * 说"还不能自动做"）、**没有目标不挂卡**、**裁决后行落终态且留痕与回喂是同一句话**、
 * **进程死亡后相位回到等确认**。SQL 语义（幂等键、唯一槽）由仪器化用例证明，这里用内存替身。
 */
class TutorPendingRequestCommandsTest {
    private val repository = FakeAgentPendingRequestRepository()
    private var savedCaptureSessions = mutableListOf<String>()
    private var openedProblems = mutableListOf<String?>()
    private var intakeCalls = mutableListOf<List<String>>()
    private var intakeResult: TutorAttachedImageIntakeResult? =
        TutorAttachedImageIntakeResult(landed = true, detail = "已经加入录入队列（2 页）。")

    private fun commands() = TutorPendingRequestCommands(
        conversationArea = "AGENT",
        requests = repository,
        landings = TutorLocalActionLandings(
            saveCaptureDraft = { sessionId ->
                savedCaptureSessions += sessionId
                "已经加入错题本。"
            },
            openLibraryProblem = { problemId -> openedProblems += problemId },
            intakeAttachedImages = { assetIds, _ ->
                intakeCalls += assetIds
                intakeResult
            },
        ),
        clock = { 1_000L },
    )

    private fun turn() = TutorTurnSendStateMachine.reduce(
        TutorSendState(),
        TutorSendAction.StudentMessagePersisted("logical-1", "message-1"),
    )

    /** 一条动作请求 → 准入 → 挂卡；准入不过时返回 null（与生产路径同一个入口）。 */
    private suspend fun suspendAction(
        commands: TutorPendingRequestCommands,
        action: TutorLocalAction,
        context: TutorLocalActionContext,
        parameters: Map<String, String> = emptyMap(),
    ): TutorConsentSuspension? {
        val admission = tutorLocalActionAdmission(
            request = TutorLocalActionRequest(action = action, parameters = parameters),
            context = context,
        ) ?: return null
        return commands.suspendFor(
            state = turn(),
            conversationId = "conversation-1",
            admission = admission,
            occurredAtEpochMillis = 10,
        )
    }

    /** 工具拼写（`NOTEBOOK_WRITE` 被工具环放进 ask 档）走同一个挂卡入口。 */
    private suspend fun suspendToolSpelling(
        commands: TutorPendingRequestCommands,
        context: TutorLocalActionContext,
    ): TutorConsentSuspension? {
        val admission = tutorLocalActionAdmission(
            subject = TutorPermissionSubject.Tool(TutorToolName.NOTEBOOK_WRITE),
            context = context,
        ) ?: return null
        return commands.suspendFor(
            state = turn(),
            conversationId = "conversation-1",
            admission = admission,
            occurredAtEpochMillis = 10,
        )
    }

    // ---- 五 kind 各一条：建卡 → 裁决 → 执行 → 回喂 ----

    @Test
    fun theNotebookWriteSpellingHangsTheSameCardAndRunsTheIntake() = runBlocking {
        val commands = commands()
        val suspension = suspendToolSpelling(
            commands,
            TutorLocalActionContext(attachedImageAssetIds = listOf("asset-1")),
        )!!

        assertEquals(TutorSendPhase.AWAITING_CONSENT, suspension.state.phase)
        assertEquals(AgentPendingRequestKind.NOTEBOOK_WRITE, suspension.request.kind)
        assertEquals(
            listOf("asset-1"),
            tutorLocalActionContext(suspension.request.payloadJson).attachedImageAssetIds,
        )

        val decision = commands.decide(
            state = suspension.state,
            requestId = suspension.request.requestId,
            decision = AgentPendingRequestDecision.ACCEPT,
        )!!
        assertEquals(listOf(listOf("asset-1")), intakeCalls)
        assertEquals("已经加入录入队列（2 页）。", decision.detail)
        assertFeedBack(kind = "NOTEBOOK_WRITE", detail = "已经加入录入队列（2 页）。")
    }

    @Test
    fun theSaveActionSpellingSavesTheCaptureDraft() = runBlocking {
        val commands = commands()
        val suspension = suspendAction(
            commands,
            TutorLocalAction.SAVE_TO_NOTEBOOK,
            TutorLocalActionContext(captureSessionId = "tutor-session-1"),
        )!!

        assertEquals(AgentPendingRequestKind.SAVE_TO_NOTEBOOK, suspension.request.kind)
        val decision = commands.decide(
            state = suspension.state,
            requestId = suspension.request.requestId,
            decision = AgentPendingRequestDecision.ACCEPT,
        )!!

        assertEquals(listOf("tutor-session-1"), savedCaptureSessions)
        assertEquals("已经加入错题本。", decision.detail)
        assertFeedBack(kind = "SAVE_TO_NOTEBOOK", detail = "已经加入错题本。")
    }

    @Test
    fun openProblemOpensTheNamedProblem() = runBlocking {
        val commands = commands()
        val suspension = suspendAction(
            commands,
            TutorLocalAction.OPEN_PROBLEM,
            TutorLocalActionContext(libraryProblemId = "problem-7"),
        )!!

        assertEquals(AgentPendingRequestKind.OPEN_PROBLEM, suspension.request.kind)
        // 形状：这个 kind 的 payload 只允许"是哪道题"。
        assertEquals("problem-7", tutorLocalActionContext(suspension.request.payloadJson).libraryProblemId)

        commands.decide(
            state = suspension.state,
            requestId = suspension.request.requestId,
            decision = AgentPendingRequestDecision.ACCEPT,
        )

        assertEquals(listOf("problem-7"), openedProblems)
        assertFeedBack(kind = "OPEN_PROBLEM", detail = "已经在错题本里打开了。")
    }

    @Test
    fun startExportHangsACardAndOpensTheSheetWithTheProposedLayout() = runBlocking {
        val commands = commands()
        val suspension = suspendAction(
            commands,
            TutorLocalAction.START_EXPORT,
            TutorLocalActionContext(),
            parameters = mapOf("templateId" to "practice_sheet", "columnCount" to "2"),
        )!!

        assertEquals(AgentPendingRequestKind.START_EXPORT, suspension.request.kind)
        // 形状：模板 + 完整版式（缺省字段已在本地填成默认值）。
        val layout = tutorLocalActionExportProposal(suspension.request.payloadJson)
        assertNotNull(layout)
        assertEquals(MistakePdfLayout.TEMPLATE_PRACTICE_SHEET, layout!!.templateId)
        assertEquals(2, layout.columnCount)
        assertEquals(MistakePdfLayout.DEFAULT_FONT_SCALE, layout.fontScale)

        val decision = commands.decide(
            state = suspension.state,
            requestId = suspension.request.requestId,
            decision = AgentPendingRequestDecision.ACCEPT,
        )!!

        // 真接线（4B B3-3）：确认后打开导出设置——版式提议随裁决结果交给交互面（sheet 预填），
        // 这里**不生成文件**（文件在 sheet 上点「开始导出」才入队），也不走任何别的落点。
        assertEquals(layout, decision.exportProposal)
        assertTrue(decision.detail.contains("导出设置"))
        assertTrue(savedCaptureSessions.isEmpty())
        assertTrue(openedProblems.isEmpty())
        assertTrue(intakeCalls.isEmpty())
        assertFeedBack(kind = "START_EXPORT", detail = decision.detail)
    }

    @Test
    fun anExportRequestWithoutAProposalNeverHangsACard() = runBlocking {
        // templateId 是声明里的必填项：缺了它这条请求不成立——本地**不替模型挑默认模板**，
        // 也不挂一张"点了才知道没提议"的卡（fail-closed）。
        val suspension = suspendAction(
            commands(),
            TutorLocalAction.START_EXPORT,
            TutorLocalActionContext(),
        )

        assertNull(suspension)
        assertTrue(repository.readPendingRequests("AGENT").isEmpty())
    }

    @Test
    fun aLegacyExportCardStillOpensTheSheetWithTheDefaultLayout() = runBlocking {
        // 升级兼容（P1-a）：旧版本写下的行是空形状 `{}`。读回不抛（读回宽容），卡可渲染
        // （文案如实说"来自旧版本"），decide 用默认版式打开 sheet——不许死路、不许崩。
        val legacy = AgentPendingRequest(
            requestId = "agent-req:legacy:START_EXPORT:0000000000000000",
            conversationArea = "AGENT",
            conversationId = "conversation-1",
            logicalOperationId = "logical-1",
            messageId = "message-1",
            kind = AgentPendingRequestKind.START_EXPORT,
            payloadJson = "{}",
            status = AgentPendingRequestStatus.PENDING,
            createdAtEpochMillis = 10,
        )
        repository.seed(listOf(legacy))
        val commands = commands()

        assertTrue(
            pendingRequestCopy(AgentPendingRequestKind.START_EXPORT, legacy.payloadJson)
                .detail.contains("旧版本"),
        )

        val decision = commands.decide(
            state = turn(),
            requestId = legacy.requestId,
            decision = AgentPendingRequestDecision.ACCEPT,
        )!!

        assertEquals(MistakePdfLayout.DEFAULT, decision.exportProposal)
        assertTrue(decision.detail.contains("导出设置"))
        assertEquals(
            AgentPendingRequestStatus.ACCEPTED,
            repository.readResolvedRequests("AGENT").single().status,
        )
    }

    @Test
    fun addToReviewPlanHangsACardAndSaysHonestlyThatItIsNotWiredYet() = runBlocking {
        val commands = commands()
        val suspension = suspendAction(
            commands,
            TutorLocalAction.ADD_TO_REVIEW_PLAN,
            TutorLocalActionContext(libraryProblemId = "problem-9"),
        )!!

        assertEquals(AgentPendingRequestKind.ADD_TO_REVIEW_PLAN, suspension.request.kind)
        val decision = commands.decide(
            state = suspension.state,
            requestId = suspension.request.requestId,
            decision = AgentPendingRequestDecision.ACCEPT,
        )!!

        assertEquals(notWiredYetDetail(AgentPendingRequestKind.ADD_TO_REVIEW_PLAN), decision.detail)
        assertFeedBack(
            kind = "ADD_TO_REVIEW_PLAN",
            detail = notWiredYetDetail(AgentPendingRequestKind.ADD_TO_REVIEW_PLAN),
        )
    }

    // ---- 准入的边界 ----

    @Test
    fun aSaveCardWithoutAnyExecutableTargetIsNeverHung() = runBlocking {
        val commands = commands()
        val suspension = suspendAction(
            commands,
            TutorLocalAction.SAVE_TO_NOTEBOOK,
            TutorLocalActionContext(),
        )

        assertNull(suspension)
        assertTrue(repository.readPendingRequests("AGENT").isEmpty())
        assertEquals(TutorSendPhase.PERSISTING, turn().phase)
    }

    @Test
    fun anActionWithAnUndeclaredParameterIsRefusedBeforeAnyCard() = runBlocking {
        // 模型只能选不能造：除 START_EXPORT 的模板/版式参数外，参数形状都是空集，塞一个参数
        // 就是无效请求（不挂卡）。
        val admission = tutorLocalActionAdmission(
            request = TutorLocalActionRequest(
                action = TutorLocalAction.OPEN_PROBLEM,
                parameters = mapOf("problemId" to "problem-7"),
            ),
            context = TutorLocalActionContext(libraryProblemId = "problem-7"),
        )

        assertNull(admission)
    }

    @Test
    fun aRefusedActionWithoutAdmissionNeverHangsACard() = runBlocking {
        // 权限档不放行（本轮不在白名单里）：连卡都不出现——"调用被拒"和"学生还没点"是两件事。
        val admission = tutorLocalActionAdmission(
            request = TutorLocalActionRequest(action = TutorLocalAction.OPEN_PROBLEM),
            context = TutorLocalActionContext(libraryProblemId = "problem-7"),
            permissionContext = com.tingyun.smartmistakebook.core.domain.TutorRoundPermissionContext(
                allowedActions = emptySet(),
            ),
        )

        assertNull(admission)
    }

    // ---- 裁决与留痕 ----

    @Test
    fun decliningExecutesNothingButStillLeavesTheTrace() = runBlocking {
        val commands = commands()
        val suspension = suspendAction(
            commands,
            TutorLocalAction.SAVE_TO_NOTEBOOK,
            TutorLocalActionContext(attachedImageAssetIds = listOf("asset-1")),
        )!!

        val decision = commands.decide(
            state = suspension.state,
            requestId = suspension.request.requestId,
            decision = AgentPendingRequestDecision.DECLINE,
        )!!

        assertTrue(intakeCalls.isEmpty())
        assertEquals("学生选择先不保存。", decision.detail)
        assertEquals(
            AgentPendingRequestStatus.DECLINED,
            repository.readResolvedRequests("AGENT").single().status,
        )
        assertFeedBack(kind = "SAVE_TO_NOTEBOOK", detail = "学生选择先不保存。")
    }

    @Test
    fun decliningAnOpenProblemSaysItWasNotOpened() = runBlocking {
        val commands = commands()
        val suspension = suspendAction(
            commands,
            TutorLocalAction.OPEN_PROBLEM,
            TutorLocalActionContext(libraryProblemId = "problem-7"),
        )!!

        val decision = commands.decide(
            state = suspension.state,
            requestId = suspension.request.requestId,
            decision = AgentPendingRequestDecision.DECLINE,
        )!!

        assertEquals(declineDetail(AgentPendingRequestKind.OPEN_PROBLEM), decision.detail)
    }

    @Test
    fun aFailedLandingSaysSoInsteadOfPretendingItWorked() = runBlocking {
        intakeResult = TutorAttachedImageIntakeResult(landed = false, detail = "这些图片已经不在本机了。")
        val commands = commands()
        val suspension = suspendAction(
            commands,
            TutorLocalAction.SAVE_TO_NOTEBOOK,
            TutorLocalActionContext(attachedImageAssetIds = listOf("asset-1")),
        )!!

        val decision = commands.decide(
            state = suspension.state,
            requestId = suspension.request.requestId,
            decision = AgentPendingRequestDecision.ACCEPT,
        )!!

        assertEquals("没能执行：这些图片已经不在本机了。", decision.detail)
    }

    @Test
    fun thePhaseComesBackToAwaitingConsentAfterTheProcessRestarted() = runBlocking {
        suspendAction(
            commands(),
            TutorLocalAction.SAVE_TO_NOTEBOOK,
            TutorLocalActionContext(attachedImageAssetIds = listOf("asset-1")),
        )!!

        // 进程死亡后：内存里的相位没了（IDLE），行还在库里——重建同一张卡，相位回到等确认。
        val restored = commands().resumeAfterRestart(
            state = TutorSendState(),
            pending = repository.readPendingRequests("AGENT").single(),
        )

        assertNotNull(restored)
        assertEquals(TutorSendPhase.AWAITING_CONSENT, restored!!.phase)
        assertEquals("logical-1", restored.logicalOperationId)
    }

    @Test
    fun resolvedRowsAreReadBackPerConversationForTheNextRound() = runBlocking {
        val commands = commands()
        val suspension = suspendAction(
            commands,
            TutorLocalAction.SAVE_TO_NOTEBOOK,
            TutorLocalActionContext(attachedImageAssetIds = listOf("asset-1")),
        )!!
        commands.decide(
            state = suspension.state,
            requestId = suspension.request.requestId,
            decision = AgentPendingRequestDecision.ACCEPT,
        )

        val mine = commands.resolvedOutcomeRecords("conversation-1")
        val other = commands.resolvedOutcomeRecords("conversation-2")

        assertEquals(1, mine.size)
        assertEquals("SAVE_TO_NOTEBOOK", mine.single().kind)
        assertTrue(other.isEmpty())
        assertFalse(mine.single().detail.isNullOrBlank())
    }

    private fun assertFeedBack(kind: String, detail: String) = runBlocking {
        val outcome = commands().resolvedOutcomeRecords("conversation-1").single()
        assertEquals(kind, outcome.kind)
        assertEquals(detail, outcome.detail)
    }
}

/** 内存版待确认行：语义（幂等/终态）够本测试用，SQL 语义由仪器化用例证明。 */
internal class FakeAgentPendingRequestRepository : AgentPendingRequestRepository {
    private val rows = MutableStateFlow<List<AgentPendingRequest>>(emptyList())

    /** 播种（升级兼容用例要预置"旧版本写下的行"）。 */
    fun seed(seedRows: List<AgentPendingRequest>) {
        rows.value = seedRows
    }

    override suspend fun createRequest(
        draft: AgentPendingRequestDraft,
        createdAtEpochMillis: Long,
    ): AgentPendingRequest? {
        val requestId = draft.requestId()
        rows.value.firstOrNull { it.requestId == requestId }?.let { return it }
        val created = AgentPendingRequest(
            requestId = requestId,
            conversationArea = draft.conversationArea,
            conversationId = draft.conversationId,
            logicalOperationId = draft.logicalOperationId,
            messageId = draft.messageId,
            kind = draft.kind,
            payloadJson = draft.payloadJson,
            status = AgentPendingRequestStatus.PENDING,
            createdAtEpochMillis = createdAtEpochMillis,
        )
        rows.value = rows.value + created
        return created
    }

    override suspend fun resolveRequest(
        requestId: String,
        decision: AgentPendingRequestDecision,
        resolutionNote: String?,
        resolvedAtEpochMillis: Long,
    ): AgentPendingRequest? {
        val existing = rows.value.firstOrNull { it.requestId == requestId } ?: return null
        check(existing.status == AgentPendingRequestStatus.PENDING) { "already resolved" }
        val resolved = existing.copy(
            status = decision.terminalStatus,
            resolvedAtEpochMillis = resolvedAtEpochMillis,
            resolutionNote = resolutionNote,
        )
        rows.value = rows.value.map { row -> if (row.requestId == requestId) resolved else row }
        return resolved
    }

    override fun observePendingRequests(conversationArea: String?): Flow<List<AgentPendingRequest>> =
        rows.map { all ->
            all.filter { row ->
                row.status == AgentPendingRequestStatus.PENDING &&
                    (conversationArea == null || row.conversationArea == conversationArea)
            }
        }

    override suspend fun readPendingRequests(conversationArea: String?): List<AgentPendingRequest> =
        observePendingRequests(conversationArea).first()

    override suspend fun readResolvedRequests(
        conversationArea: String,
        limit: Int,
    ): List<AgentPendingRequest> = rows.value
        .filter { row -> row.conversationArea == conversationArea && row.status.isTerminal }
        .sortedByDescending { row -> row.resolvedAtEpochMillis }
        .take(limit)
}
