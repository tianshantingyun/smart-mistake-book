package com.tingyun.smartmistakebook.feature.tutor

import androidx.lifecycle.SavedStateHandle
import com.tingyun.smartmistakebook.core.domain.AgentPendingRequest
import com.tingyun.smartmistakebook.core.domain.LobbyMessageImage
import com.tingyun.smartmistakebook.core.domain.LobbyMessageImageIntake
import com.tingyun.smartmistakebook.core.domain.TutorConversation
import com.tingyun.smartmistakebook.core.domain.TutorMessage
import com.tingyun.smartmistakebook.core.domain.TutorMessageRole
import com.tingyun.smartmistakebook.core.domain.TutorSendState
import com.tingyun.smartmistakebook.core.model.ActionType
import com.tingyun.smartmistakebook.core.model.AppFailure
import com.tingyun.smartmistakebook.core.model.AppFailureCode
import com.tingyun.smartmistakebook.core.model.AttachedRoundQuestion
import com.tingyun.smartmistakebook.core.model.ModelTaskRequest
import com.tingyun.smartmistakebook.core.model.ModelTaskSnapshot
import com.tingyun.smartmistakebook.core.model.TutorInteractionMode
import com.tingyun.smartmistakebook.core.model.ModelTaskStatus
import com.tingyun.smartmistakebook.core.model.ProviderCapabilitySnapshot
import com.tingyun.smartmistakebook.core.model.Retryability
import com.tingyun.smartmistakebook.core.model.TutorLobbyInput
import com.tingyun.smartmistakebook.core.model.appFailure

/** 智能体交互面的对话状态（[TutorConversationViewModel] 的唯一对外形状）。 */
internal data class TutorConversationUiState(
    /** 当前会话 id；空白 = 本轮还没落库（K1b：发出第一条消息才建行）。 */
    val conversationId: String = "",
    val conversation: TutorConversation? = null,
    val messages: List<TutorMessage> = emptyList(),
    /** 大厅任务台账（K1a 之后只提供状态、失败卡与在途判定；正文一律从消息行来）。 */
    val tasks: List<ModelTaskSnapshot> = emptyList(),
    val draft: String = "",
    val pendingImages: List<PendingMessageImage> = emptyList(),
    val pendingCameraImageUri: String? = null,
    val attachMenuOpen: Boolean = false,
    val mistakePickerOpen: Boolean = false,
    val attachReadFailed: String? = null,
    /**
     * 学生本轮**显式附加的题**（讲题侧加号 → 从错题库选择）：这一轮要讲的那一道。
     * 发送后清空（与附图同一生命周期）。
     */
    val pendingAttachedQuestion: AttachedRoundQuestion? = null,
    /**
     * 附加题在目录里的键：题面是重对象，跨进程保留的是这个键，恢复时按它重新读回
     * （见 `TutorConversationViewModel.restorePendingAttachedQuestion`）。
     */
    val pendingAttachedQuestionEntryId: String? = null,
    /** 结构化交互（选择 / 继续讲 / 重新开始）正在落库：按钮在此期间不可再点。 */
    val interactionBusy: Boolean = false,
    /** 结构化交互失败的那一句（学生看得见的交代）。 */
    val interactionError: String? = null,
    /** 讲题侧这一轮是不是本机刚派发的（用它排除"自动恢复"重复收集同一条任务）。 */
    val locallyStartedRequestId: String? = null,
    val provider: ProviderCapabilitySnapshot? = null,
    val providerLoadFailed: Boolean = false,
    val sendState: TutorSendState = TutorSendState(),
    val sending: Boolean = false,
    val resumingRequestId: String? = null,
    val error: AppFailure? = null,
    val lastFailedMessage: String? = null,
    val notebookLookupRequested: Boolean = false,
    /**
     * 这条会话未裁决的确认卡（A4）：行来自 `agent_pending_request`，界面只是它的视图——
     * 进程死亡后重建的是同一张卡。
     */
    val pendingRequestCards: List<AgentPendingRequest> = emptyList(),
    /** 最近一次裁决的结果一句话（学生看得见的交代；也是回喂模型的那句话）。 */
    val pendingRequestDetail: String? = null,
    val liveTurn: TutorLiveTurn = TutorLiveTurn.EMPTY,
    /**
     * 本轮的交互模式（D-Q9）：会话行上是权威值，会话还没落库时按**会话区默认**给
     * （智能体栏正常、复习栏引导）。学生在会话里随时可换（换完立刻生效，不依赖模型）。
     */
    val interactionMode: TutorInteractionMode = TutorInteractionMode.NORMAL,
) {
    /**
     * 这一轮还会不会再动：任务停在非终态（协程已死、DB 无终态）与"正在发送"都算。
     * 有它才敢把输入框关掉、把"继续回复"露出来。
     */
    val stalledTask: ModelTaskSnapshot?
        get() = tasks.lastOrNull(ModelTaskSnapshot::isTutorLiveStatus)

    val hasActiveTask: Boolean
        get() = stalledTask != null || sending || resumingRequestId != null

    /**
     * 停在非终态、**且此刻没有任何东西在动它**的那一条（进程死亡或离开页面留下的残留）。
     *
     * 这才是「继续回复」唯一该出现的时刻：[hasActiveTask] 里的另外两项（[sending] /
     * [resumingRequestId]）说明"已经有东西在动它"——那时出口是「停止」或等待，再给一个
     * 「继续回复」就是第二条并发派发。
     *
     * 判别格（回归的成因就是它）：渲染条件曾经写成 `stalledTask != null && !hasActiveTask`，
     * 而 `hasActiveTask` 的第一项就是 `stalledTask != null`，于是这个条件恒假、出口与它唯一的
     * 调用点（[TutorConversationViewModel.onResumeStalledTask]）一起不可达。现在的条件是
     * `A && !B`，A 与 B 不同源，可以成立。
     */
    val resumableStalledTask: ModelTaskSnapshot?
        get() = stalledTask?.takeIf { !sending && resumingRequestId == null }

    /** 「继续回复」这一刻在不在场（渲染条件就是它，测试按它钉住可达性）。 */
    val resumeStalledTurnVisible: Boolean
        get() = resumableStalledTask != null

    /**
     * 这一轮还没收尾时的**人话原因**（A5）：发送不许静默吞掉点击。
     *
     * 残留态（可续）与在途态（正在发送 / 正在续上）共用一个判据 [hasActiveTask]，但只有
     * 前者需要给出口：在途态的"正在发送"由发送键上的转圈本身说明，此时再多一行字是噪声。
     */
    val stalledTurnBlockReason: String?
        get() = TUTOR_TURN_STALLED_BLOCK_REASON.takeIf { resumableStalledTask != null }

    /** 在途状态只在真的有一轮在跑时渲染（否则会把上一条的残影画回去）。 */
    val visibleLiveTurn: TutorLiveTurn?
        get() = liveTurn.takeIf { sending || resumingRequestId != null }
}

/** 终态失败：这三种状态下这一轮不会再有输出，必须落一条学生看得见的交代。 */
internal val TERMINAL_FAILURE_STATUSES = setOf(
    ModelTaskStatus.RETRYABLE_FAILURE,
    ModelTaskStatus.PERMANENT_FAILURE,
    ModelTaskStatus.CANCELLED,
)

internal const val MAX_PERSISTED_TASKS = 64

internal fun ModelTaskSnapshot.isTutorLiveStatus(): Boolean = status in TUTOR_LIVE_TASK_STATUSES

/**
 * 大厅任务台账 → 每个学生轮次只留最近一次派发（按轮次号排序）。
 *
 * 与 `latestTutorRespondTasks` 同一手法：重试/重放会在同一个轮次号下攒出多个快照，
 * 界面只认最新那一次。**不做 takeLast 截断**（B6）：长会话里前面的轮次不能被静默丢掉
 * ——轮次的状态与失败卡都从这份台账来。
 */
internal fun tutorLobbyConversationTasks(
    persistedTasks: List<ModelTaskSnapshot>,
): List<ModelTaskSnapshot> = persistedTasks
    .mapNotNull { task ->
        (task.request.input as? TutorLobbyInput)?.let { input -> input.messageOrdinal to task }
    }
    .groupBy({ it.first }, { it.second })
    .mapNotNull { (_, attempts) -> attempts.maxByOrNull(ModelTaskSnapshot::updatedAtEpochMillis) }
    .sortedBy { task -> (task.request.input as TutorLobbyInput).messageOrdinal }

internal suspend fun List<TutorMessage>.toContextImages(
    intake: LobbyMessageImageIntake?,
    beforeOrdinal: Int?,
): List<LobbyMessageImage> {
    if (intake == null) return emptyList()
    val latest = sortedBy { it.ordinal }
        .filter { message -> beforeOrdinal == null || message.ordinal < beforeOrdinal }
        .lastOrNull { message ->
            message.role == TutorMessageRole.STUDENT && message.sourceImageAssetIds.isNotEmpty()
        }
        ?: return emptyList()
    return latest.sourceImageAssetIds.mapNotNull { assetId -> intake.describeImage(assetId) }
}

/** 纯图消息的兜底正文（消息体不能为空）。 */
internal const val TUTOR_CONVERSATION_IMAGE_ONLY_MESSAGE = "请帮我看看这些图片。"

/**
 * 有残留任务（停在非终态、又没有协程在动它）时输入区里的一句话：先接上它，再发新消息。
 *
 * 它是**发送被挡住时的唯一原因来源**（`TutorComposerAvailability.stalledTurnReason` 与
 * `TutorConversationViewModel` 里那三处守卫读的是同一个判据），所以"按了发送什么也没发生"
 * 这件事在结构上不可能再发生：点击要么真的发出去，要么这句话就在输入框上方。
 */
internal const val TUTOR_TURN_STALLED_BLOCK_REASON =
    "上一条回复还没有完成，先点「继续回复」把它接上，再发新消息。"

/**
 * 本轮附件题读不出来时的那一句（讲题侧「从错题库选择」）。
 *
 * 它此前散在界面里（`TutorSessionPanel`），现在跟着读取那一步一起在 ViewModel 里——读盘在哪，
 * 读失败的话就在哪，界面只负责把它渲染到附件区那一行。
 */
internal const val TUTOR_ATTACH_READ_FAILED_MESSAGE = "这道题的题面现在读不出来，换一道试试。"

internal const val TUTOR_CONVERSATION_FAILED_REPLY_BODY = "这次回复没有准备好，你的消息已经保留。"
internal const val TUTOR_CONVERSATION_INTERRUPTED_REPLY_BODY = "这条消息已经保留，暂时没有收到讲解。"

/**
 * 学生按下停止之后那一条助手行的正文（A2）。
 *
 * 界面按 `CANCELLED` 状态画的是灰色的「已停止」+ 可重试；这一句是那条行的**事实记录**
 * （它不进模型上下文：装配器只收 `SUCCEEDED` 的助手行，所以不会让模型以为它答过）。
 */
internal const val TUTOR_CONVERSATION_STOPPED_REPLY_BODY = "已停止生成。"

internal fun tutorSendRecoveryFailedError(): AppFailure = appFailure(
    code = AppFailureCode.NETWORK_UNAVAILABLE,
    title = "暂时没有恢复成功",
    message = "这条回复还没有完成，可以再试一次。",
    dataPreserved = true,
    retryability = Retryability.RETRYABLE,
    primaryAction = ActionType.RETRY,
)
