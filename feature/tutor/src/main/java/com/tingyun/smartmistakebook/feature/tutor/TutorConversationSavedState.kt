package com.tingyun.smartmistakebook.feature.tutor

import androidx.lifecycle.SavedStateHandle
import com.tingyun.smartmistakebook.core.domain.ModelTaskRepository
import com.tingyun.smartmistakebook.core.model.ModelTaskSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 交互面的**跨进程保留**（B3）：草稿、待发附图、相机目标、两个开关、读题失败、重试原文。
 *
 * 为什么单独一处（从 `TutorConversationViewModel` 里搬出来）：这八件东西是**一组**——
 * 它们的读写是同一件事的两半（进页面读回来、每次状态变化写回去），而 ViewModel 的其余部分
 * 是发送/裁决/流式的流程。抽出来之后，那个类里"状态变化"只有一个写入点，这一组键与它们的
 * 恢复也只有一个出处；顺便让那个文件回到规模门以内。
 *
 * 读侧：[restoreTutorConversationUiState] 给初始状态；写侧：[persistTutorConversationUiState]
 * 每次状态变化调用一次。**没有任何一件走 `remember`**：跨进程会丢的东西不许活在组合里。
 */
internal fun SavedStateHandle.restoredTutorConversationState(): TutorConversationPreservedState =
    TutorConversationPreservedState(
        conversationId = get<String>(KEY_CONVERSATION_ID)?.takeIf(String::isNotBlank).orEmpty(),
        draft = get<String>(KEY_DRAFT).orEmpty(),
        pendingImageUris = get<ArrayList<String>>(KEY_PENDING_IMAGES).orEmpty(),
        pendingCameraImageUri = get<String>(KEY_PENDING_CAMERA_URI),
        attachMenuOpen = get<Boolean>(KEY_ATTACH_MENU_OPEN) == true,
        mistakePickerOpen = get<Boolean>(KEY_MISTAKE_PICKER_OPEN) == true,
        attachReadFailed = get<String>(KEY_ATTACH_READ_FAILED),
        lastFailedMessage = get<String>(KEY_LAST_FAILED_MESSAGE),
        pendingAttachedQuestionEntryId = get<String>(KEY_ATTACHED_QUESTION_ENTRY_ID),
    )

/** 从进程恢复时读回来的那一组（[SavedStateHandle.restoredTutorConversationState] 的产物）。 */
internal data class TutorConversationPreservedState(
    val conversationId: String,
    val draft: String,
    val pendingImageUris: List<String>,
    val pendingCameraImageUri: String?,
    val attachMenuOpen: Boolean,
    val mistakePickerOpen: Boolean,
    val attachReadFailed: String?,
    val lastFailedMessage: String?,
    /**
     * 本轮附加题在目录里的键（不是题面本身）：附加题是重对象，耐久保留的是**身份**，
     * 恢复时按它重新读回（读不回来就如实说读不出来，而不是带一条没有题面的"添加"进去）。
     */
    val pendingAttachedQuestionEntryId: String?,
)

/** 状态 → SavedStateHandle（每次状态变化唯一的那次写入）。 */
internal fun persistTutorConversationState(
    savedStateHandle: SavedStateHandle,
    state: TutorConversationUiState,
) {
    savedStateHandle[KEY_CONVERSATION_ID] = state.conversationId
    savedStateHandle[KEY_DRAFT] = state.draft
    savedStateHandle[KEY_PENDING_IMAGES] = ArrayList(state.pendingImages.map { it.localUri })
    savedStateHandle[KEY_PENDING_CAMERA_URI] = state.pendingCameraImageUri
    savedStateHandle[KEY_ATTACH_MENU_OPEN] = state.attachMenuOpen
    savedStateHandle[KEY_MISTAKE_PICKER_OPEN] = state.mistakePickerOpen
    savedStateHandle[KEY_ATTACH_READ_FAILED] = state.attachReadFailed
    savedStateHandle[KEY_LAST_FAILED_MESSAGE] = state.lastFailedMessage
    savedStateHandle[KEY_ATTACHED_QUESTION_ENTRY_ID] = state.pendingAttachedQuestionEntryId
}

private const val KEY_CONVERSATION_ID = "tutorConversationId"
private const val KEY_DRAFT = "tutorConversationDraft"
private const val KEY_PENDING_IMAGES = "tutorConversationPendingImages"
private const val KEY_PENDING_CAMERA_URI = "tutorConversationPendingCameraUri"
private const val KEY_ATTACH_MENU_OPEN = "tutorConversationAttachMenuOpen"
private const val KEY_MISTAKE_PICKER_OPEN = "tutorConversationMistakePickerOpen"
private const val KEY_ATTACH_READ_FAILED = "tutorConversationAttachReadFailed"
private const val KEY_LAST_FAILED_MESSAGE = "tutorConversationLastFailedMessage"
private const val KEY_ATTACHED_QUESTION_ENTRY_ID = "tutorConversationAttachedQuestionEntryId"

/**
 * 「本会话当前在途那一轮」的**实时文本订阅**（A3）：会话 id + 在途请求 id 一起作键，
 * **不按任务类型分桶**——分桶正是"只有回应轮有流"的成因。
 *
 * 抽出来（而不是留在 ViewModel 里）是因为它自带两个可变字段（订阅键与那个 Job）：它们是
 * "当前订阅"这一件事实的两半，与对话状态无关，放在一起才不会与状态机的手臂互相干扰。
 */
internal class TutorLiveTurnSubscription(
    private val scope: CoroutineScope,
    private val modelTasks: ModelTaskRepository,
    /** 一帧实时文本 → 状态；由调用方决定写进哪一份状态。 */
    private val onTurn: (TutorLiveTurn) -> Unit,
) {
    private var job: Job? = null
    private var subscriptionKey: String? = null

    fun update(conversationKey: String, tasks: List<ModelTaskSnapshot>) {
        val active = activeTutorLiveTurn(conversationKey, tasks)
        if (job?.isActive == true && subscriptionKey == active?.subscriptionKey) return
        subscriptionKey = active?.subscriptionKey
        job?.cancel()
        if (active == null) {
            onTurn(TutorLiveTurn.EMPTY)
            return
        }
        job = scope.launch {
            modelTasks.observeTutorLiveTurn(active.requestId, onTurn)
        }
    }
}
