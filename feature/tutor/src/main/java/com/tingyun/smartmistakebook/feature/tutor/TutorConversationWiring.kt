package com.tingyun.smartmistakebook.feature.tutor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import com.tingyun.smartmistakebook.core.domain.AgentPendingRequestRepository
import com.tingyun.smartmistakebook.core.domain.AppendTutorAssistantMessageCommand
import com.tingyun.smartmistakebook.core.domain.AppendTutorStudentMessageCommand
import com.tingyun.smartmistakebook.core.domain.ArchiveTutorConversationCommand
import com.tingyun.smartmistakebook.core.domain.ClearTutorConversationDraftCommand
import com.tingyun.smartmistakebook.core.domain.CreateTutorConversationCommand
import com.tingyun.smartmistakebook.core.domain.DeleteTutorConversationCommand
import com.tingyun.smartmistakebook.core.domain.LobbyMessageImageIntake
import com.tingyun.smartmistakebook.core.domain.ModelTaskRepository
import com.tingyun.smartmistakebook.core.domain.PauseTutorConversationCommand
import com.tingyun.smartmistakebook.core.domain.SaveTutorConversationDraftCommand
import com.tingyun.smartmistakebook.core.domain.StudyCatalogEntry
import com.tingyun.smartmistakebook.core.domain.TutorAttachedQuestionReader
import com.tingyun.smartmistakebook.core.domain.TutorConversation
import com.tingyun.smartmistakebook.core.domain.TutorConversationRepository
import com.tingyun.smartmistakebook.core.domain.TutorConversationSnapshot
import com.tingyun.smartmistakebook.core.domain.TutorMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * 讲题入口这条交互面的**装配**（C2 的落地点）：交互面配置 → 唯一的状态持有者。
 *
 * 消灭的具体失败：确认卡执行路径 ①（拍照草稿 → 错题本）的 `captureSessionId` 在生产里恒为
 * null——它唯一的来源 `anchoredCaptureSessionId` 没有任何生产调用方传值，于是
 * `TutorPendingRequestCommands.execute` 的 `SAVE_CAPTURE_DRAFT` 分支只有单测直接构造 context
 * 才触发（A4 的落点在生产不可达）。这条链上"配置 → ViewModel"曾经根本不存在（讲题入口自己
 * 在组合里持有状态），所以 id 也就没有地方可传。现在它只有这一条路可走：
 *
 * ```
 * CapturedTutorSessionRoute → TutorSurfaceConfig.captureSessionId
 *   → tutorSurfaceConversationViewModel(...) → TutorConversationViewModel
 *   → TutorPendingRequestCoordinator → TutorPendingRequestCommands → 真实草稿保存
 * ```
 *
 * 同一条链的第二处锚（A4 执行路径 ②）走同一手法：
 * `SavedMistakeTutorRoute → TutorSurfaceConfig.libraryProblemId → tutorSurfaceConversationViewModel`
 * → ViewModel → 协调器 → 命令 → "打开错题本"这个真实落点。没有它，错题讲题页上模型申请的
 * "存 / 打开这一轮这道题"连卡都挂不出来（本地没有目标）——那正是"模型提出请求却被静默丢掉"。
 *
 * 路由与测试调用的是同一个函数——装配只此一处，不会在某个入口被漏掉。
 */
internal fun tutorSurfaceConversationViewModel(
    savedStateHandle: SavedStateHandle,
    conversations: TutorConversationRepository,
    modelTasks: ModelTaskRepository,
    imageIntake: LobbyMessageImageIntake?,
    /** 确认卡的落库端口（A4）：null = 这条交互面不接确认卡。 */
    pendingRequests: AgentPendingRequestRepository?,
    /** 确认卡三条执行路径的落点；由入口装配（见 [tutorLocalActionLandings]）。 */
    landings: TutorLocalActionLandings,
    /** 这条交互面的配置（会话区 + 本轮附件的锚）。 */
    surface: TutorSurfaceConfig,
    /** 加号菜单「从错题库选择」选中后的题面读取器（讲题侧）；null = 这个入口不提供。 */
    attachedQuestionReader: TutorAttachedQuestionReader? = null,
    /** 选题弹层的目录条目（与读取器配对）。 */
    catalogEntries: List<StudyCatalogEntry> = emptyList(),
    /**
     * 这条交互面的**会话键**：讲题侧是 `TutorConversationIds.captured(question.sessionId)`
     * （会话行按需创建，键在行还不存在时就已经确定）；智能体栏是空（第一条消息自己建行）。
     */
    surfaceConversationId: String,
): TutorConversationViewModel = TutorConversationViewModel(
    savedStateHandle = savedStateHandle,
    conversations = conversations,
    modelTasks = modelTasks,
    imageIntake = imageIntake,
    initialConversationId = surfaceConversationId,
    conversationArea = surface.area,
    pendingRequests = pendingRequests,
    localActionLandings = landings,
    anchoredCaptureSessionId = surface.captureSessionId,
    anchoredLibraryProblemId = surface.libraryProblemId,
    attachedQuestionReader = attachedQuestionReader,
    catalogEntries = catalogEntries,
)

/** [tutorSurfaceConversationViewModel] 的 ViewModel 工厂（与 `TutorConversationViewModelFactory` 同风格）。 */
internal class TutorSurfaceConversationViewModelFactory(
    private val conversations: TutorConversationRepository,
    private val modelTasks: ModelTaskRepository,
    private val imageIntake: LobbyMessageImageIntake?,
    private val pendingRequests: AgentPendingRequestRepository?,
    private val landings: TutorLocalActionLandings,
    private val surface: TutorSurfaceConfig,
    private val surfaceConversationId: String,
    private val attachedQuestionReader: TutorAttachedQuestionReader? = null,
    private val catalogEntries: List<StudyCatalogEntry> = emptyList(),
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(
        modelClass: Class<T>,
        extras: CreationExtras,
    ): T {
        require(modelClass.isAssignableFrom(TutorConversationViewModel::class.java)) {
            "Unknown TutorConversationViewModel class: ${modelClass.name}"
        }
        return tutorSurfaceConversationViewModel(
            savedStateHandle = extras.createSavedStateHandle(),
            conversations = conversations,
            modelTasks = modelTasks,
            imageIntake = imageIntake,
            pendingRequests = pendingRequests,
            landings = landings,
            surface = surface,
            surfaceConversationId = surfaceConversationId,
            attachedQuestionReader = attachedQuestionReader,
            catalogEntries = catalogEntries,
        ) as T
    }
}

/**
 * 没有会话仓库的交互面（无库降级 / 测试替身）用的空仓库：**读回空、写入即错**。
 *
 * 为什么需要它：对话状态只能有一个持有者（C2），而持有者要一个仓库；"这个界面不落库"
 * （`conversations == null`）此前是合法配置（测试替身与无库界面），不补这一份就会在建状态
 * 持有者那一步崩掉、退回"没有持有者"的老样子。写入**抛错而不是静默丢弃**：这一形态本就不该
 * 写入，静默 no-op 会让下一个人以为它落了库。
 */
internal object TutorConversationWithoutLibrary : TutorConversationRepository {
    override fun observeRecent(limit: Int, area: String): Flow<List<TutorConversation>> =
        flowOf(emptyList())

    override fun observeConversation(conversationId: String): Flow<TutorConversationSnapshot?> =
        flowOf(null)

    override suspend fun createConversation(command: CreateTutorConversationCommand): TutorConversation =
        noLibraryWrite()

    override suspend fun appendStudentMessage(command: AppendTutorStudentMessageCommand): TutorMessage =
        noLibraryWrite()

    override suspend fun appendAssistantMessage(command: AppendTutorAssistantMessageCommand): TutorMessage =
        noLibraryWrite()

    override suspend fun updateMessageStatus(
        command: com.tingyun.smartmistakebook.core.domain.UpdateTutorMessageStatusCommand,
    ): TutorMessage = noLibraryWrite()

    override suspend fun pauseConversation(command: PauseTutorConversationCommand): TutorConversation =
        noLibraryWrite()

    override suspend fun archiveConversation(command: ArchiveTutorConversationCommand): TutorConversation =
        noLibraryWrite()

    override suspend fun deleteConversation(command: DeleteTutorConversationCommand) = Unit

    override suspend fun saveDraft(command: SaveTutorConversationDraftCommand) = noLibraryWrite()

    override suspend fun clearDraft(command: ClearTutorConversationDraftCommand) = Unit

    private fun noLibraryWrite(): Nothing = error(
        "这个交互面没有接上会话仓库（conversations == null），不该写入对话",
    )
}
