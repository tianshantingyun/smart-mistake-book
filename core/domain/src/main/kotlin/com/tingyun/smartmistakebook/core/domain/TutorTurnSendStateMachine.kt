package com.tingyun.smartmistakebook.core.domain

enum class TutorSendPhase {
    IDLE,
    PERSISTING,
    AWAITING_CONSENT,
    DISPATCHING,
    STREAMING,
    RETRYABLE_FAILURE,
    PERMANENT_FAILURE,
    COMPLETED,
}

/**
 * 一轮学生消息的发送状态。**没有 UI 派遣预算**（B5）：派遣上限只留在内核账本
 * （`ModelTasks.MAX_DISPATCHES`），界面这一层只回答"当前这一轮走到哪一步"。
 *
 * 消灭的失败：这里此前记着一个 `dispatchAttemptCount`，并以它为闸门拒绝发送——于是
 * "这条消息还在处理中 / 发送次数已到上限"成了学生在界面上永远打不开的死结（界面自己的
 * 计数与内核账本各算一套，前者先到顶就把人拦住）。预算只有一个主人，就是内核账本。
 */
data class TutorSendState(
    val phase: TutorSendPhase = TutorSendPhase.IDLE,
    val logicalOperationId: String? = null,
    val messageId: String? = null,
    val errorCode: String? = null,
) {
    init {
        require(logicalOperationId == null || logicalOperationId.isNotBlank()) {
            "Tutor logical operation id must be null or non-blank"
        }
        require(messageId == null || messageId.isNotBlank()) {
            "Tutor message id must be null or non-blank"
        }
        require(
            phase != TutorSendPhase.DISPATCHING ||
                (logicalOperationId != null && messageId != null),
        ) { "A dispatching tutor turn requires stable logical and message ids" }
        require(
            phase != TutorSendPhase.RETRYABLE_FAILURE &&
                phase != TutorSendPhase.PERMANENT_FAILURE ||
                errorCode != null,
        ) { "A failed tutor turn requires an error code" }
    }

    /** 这一轮是不是"当前那一轮"；未开始发送（或已停止）时为 false。 */
    val isCurrentRound: Boolean
        get() = logicalOperationId != null && messageId != null
}

sealed interface TutorSendAction {
    val logicalOperationId: String?
    val messageId: String?

    data class StudentMessagePersisted(
        override val logicalOperationId: String,
        override val messageId: String,
    ) : TutorSendAction

    data class ConsentRequired(
        override val logicalOperationId: String,
        override val messageId: String,
    ) : TutorSendAction

    data class ConsentGranted(
        override val logicalOperationId: String,
        override val messageId: String,
    ) : TutorSendAction

    data class DispatchStarted(
        override val logicalOperationId: String,
        override val messageId: String,
    ) : TutorSendAction

    data class DispatchSucceeded(
        override val logicalOperationId: String,
        override val messageId: String,
    ) : TutorSendAction

    data class DispatchFailed(
        override val logicalOperationId: String,
        override val messageId: String,
        val errorCode: String,
        val retryable: Boolean,
    ) : TutorSendAction

    data class ResumeAfterRestart(
        override val logicalOperationId: String,
        override val messageId: String,
    ) : TutorSendAction

    /**
     * 回到空闲：新回合开始、学生停止这一轮、或整条会话换新时调用。
     *
     * 与"新学生消息"不同，它**不代表任何一轮的存在**——停止之后这一轮就不再是"当前那一轮"，
     * 它迟到的结果因此会被下面那条"被取代即忽略"的规则挡掉。
     */
    data object Reset : TutorSendAction {
        override val logicalOperationId: String? = null
        override val messageId: String? = null
    }
}

/**
 * 一轮发送的状态机（B5 重定语义）。
 *
 * 只有两条规则：
 *
 * 1. **新学生消息一律开新回合并重置标识**——上一轮成功、失败还是被取消，都不影响学生接着说
 *    下一句。此前"完成/失败"的标识会挡住新回合，学生必须靠"重试/继续回复"这类出口才能往下走。
 * 2. **被取代回合的迟到结果一律忽略**（原样返回当前状态，不写 `PERMANENT_FAILURE`，也不再
 *    制造 `STALE_TUTOR_TURN_RESULT` 这个专有错误码）——上一轮的超时/失败/成功回到客户端时，
 *    学生已经在说下一句了，它不能再改写界面状态。
 *
 * 结果类动作（[TutorSendAction.DispatchStarted] / [TutorSendAction.DispatchSucceeded] /
 * [TutorSendAction.DispatchFailed]）只作用于标识相同的当前回合；其余动作由学生动作或恢复
 * 路径驱动，带上谁的标识就归谁。
 */
object TutorTurnSendStateMachine {
    fun reduce(
        state: TutorSendState,
        action: TutorSendAction,
    ): TutorSendState = when (action) {
        is TutorSendAction.Reset -> TutorSendState()
        is TutorSendAction.StudentMessagePersisted -> state.copy(
            phase = TutorSendPhase.PERSISTING,
            logicalOperationId = action.logicalOperationId,
            messageId = action.messageId,
            errorCode = null,
        )
        // 已经完成的那一轮**到此为止**：它的结果迟到就忽略，也不能再被挂起一张没有主人的
        // 确认卡、或被重新派发。要接着说下一句，就走上面那条"新回合"。
        else -> if (state.phase == TutorSendPhase.COMPLETED) {
            state
        } else {
            advance(state, action)
        }
    }

    private fun advance(
        state: TutorSendState,
        action: TutorSendAction,
    ): TutorSendState = when (action) {
        is TutorSendAction.ConsentRequired -> state.copy(
            phase = TutorSendPhase.AWAITING_CONSENT,
            logicalOperationId = action.logicalOperationId,
            messageId = action.messageId,
            errorCode = null,
        )
        is TutorSendAction.ConsentGranted -> state.copy(
            phase = TutorSendPhase.DISPATCHING,
            logicalOperationId = action.logicalOperationId,
            messageId = action.messageId,
            errorCode = null,
        )
        is TutorSendAction.ResumeAfterRestart -> state.copy(
            phase = TutorSendPhase.AWAITING_CONSENT,
            logicalOperationId = action.logicalOperationId,
            messageId = action.messageId,
            errorCode = null,
        )
        is TutorSendAction.DispatchStarted,
        is TutorSendAction.DispatchSucceeded,
        is TutorSendAction.DispatchFailed,
        -> if (action.isLateResultOfASupersededRound(state)) state else applyResult(state, action)
        is TutorSendAction.Reset,
        is TutorSendAction.StudentMessagePersisted,
        -> state
    }

    private fun applyResult(
        state: TutorSendState,
        action: TutorSendAction,
    ): TutorSendState = when (action) {
        is TutorSendAction.DispatchStarted -> state.copy(
            phase = TutorSendPhase.STREAMING,
            logicalOperationId = action.logicalOperationId,
            messageId = action.messageId,
            errorCode = null,
        )
        is TutorSendAction.DispatchSucceeded -> state.copy(
            phase = TutorSendPhase.COMPLETED,
            logicalOperationId = action.logicalOperationId,
            messageId = action.messageId,
            errorCode = null,
        )
        is TutorSendAction.DispatchFailed -> state.copy(
            phase = if (action.retryable) {
                TutorSendPhase.RETRYABLE_FAILURE
            } else {
                TutorSendPhase.PERMANENT_FAILURE
            },
            logicalOperationId = action.logicalOperationId,
            messageId = action.messageId,
            errorCode = action.errorCode,
        )
        // 上面 when 已经把落在这里的动作列全；其余动作在 reduce 的第一层处理。
        else -> state
    }

    /**
     * 这条结果是不是**被取代的那一轮**的迟到回报。
     *
     * 判据只有一条：它带的标识与当前回合不同（包括"当前没有回合"——停止之后就没有回合了）。
     * 这样"上一轮超时"落在学生已经发出的下一句上时，界面状态原样不动。
     */
    private fun TutorSendAction.isLateResultOfASupersededRound(state: TutorSendState): Boolean =
        !state.isCurrentRound ||
            logicalOperationId != state.logicalOperationId ||
            messageId != state.messageId
}
