package com.tingyun.smartmistakebook.feature.tutor

import com.tingyun.smartmistakebook.core.domain.AppendTutorAssistantMessageCommand
import com.tingyun.smartmistakebook.core.domain.ModelTaskRepository
import com.tingyun.smartmistakebook.core.domain.TutorConversationRepository
import com.tingyun.smartmistakebook.core.domain.TutorMessageStatus
import java.util.UUID

/**
 * 「停止」这一条路（A2）：在途回合登记 → 取消 → 补一条「已停止」的助手行。
 *
 * 拆出来的理由只有一个：交互面的 ViewModel 是 C2 定的"状态唯一持有者"，它已经装着发送、附件、
 * 实时流、确认卡四件事；这条流程只通过回调读写那份状态，不另持一份。
 *
 * 消灭的具体失败：`CANCELLED` 这个终态在状态机里存在、在界面文案里存在，但**没有任何用户触发
 * 的路径**会把它推出来——学生想让一段正在生成的回复停下来时，唯一的做法是离开页面。
 */
internal class TutorRoundStopCommands(
    private val modelTasks: ModelTaskRepository,
    private val conversations: TutorConversationRepository,
    private val currentState: () -> TutorConversationUiState,
    private val updateState: ((TutorConversationUiState) -> TutorConversationUiState) -> Unit,
    /** 停止完成后的收尾（放开输入区、把这一轮从"当前那一轮"复位）。 */
    private val onRoundStopped: () -> Unit,
    /** 停不下来时如实说（终态没落成，学生仍可重试）。 */
    private val onStopFailed: () -> Unit,
) {
    /**
     * 这一轮还在跑：把它**整轮**记下来。只有"当前这一条派发"知道自己的请求标识与回合身份，
     * 界面层不该从任务台账反推（反推出来的东西在重试后会指向旧那一轮）。
     */
    private data class InFlightRound(
        val requestId: String,
        val conversationId: String,
        val logicalOperationId: String,
        val replyToMessageId: String?,
    )

    private var inFlightRound: InFlightRound? = null

    /**
     * 学生主动停掉的回合。停止之后，这一轮迟到的结果（取消从收集协程抛出来）**不再**被当成
     * "中断"去补一条失败消息——收尾由 [stop] 自己做（灰字「已停止」+ 可重试）。
     */
    private val studentStoppedRounds = mutableSetOf<String>()

    /** 一条派发开始了：登记它，`stop` 才找得到要停的那一条。 */
    fun beginRound(
        requestId: String,
        conversationId: String,
        replyToMessageId: String?,
        logicalOperationId: String,
    ) {
        inFlightRound = InFlightRound(
            requestId = requestId,
            conversationId = conversationId,
            logicalOperationId = logicalOperationId,
            replyToMessageId = replyToMessageId,
        )
    }

    /** 这条派发结束了（无论成败）：只有它自己还挂在登记表上时才摘除。 */
    fun endRound(logicalOperationId: String) {
        if (inFlightRound?.logicalOperationId == logicalOperationId) inFlightRound = null
    }

    /** 这一轮是不是被学生停掉的（取消从收集协程抛出来时，收尾方式是另一条）。 */
    fun isStudentStopped(logicalOperationId: String): Boolean =
        studentStoppedRounds.contains(logicalOperationId)

    /**
     * 学生按下「停止」：取消这一轮正在跑的派发，落 `CANCELLED` 终态，并在会话流里补上一条
     * 灰字「已停止」的助手行（可重试）。
     *
     * 顺序是有讲究的：先记下"这一轮是学生停的"（取消会从收集协程里抛出来，那一路必须先知道
     * 这不是故障），再让仓库取消（它会等终态真的落库回来）并补消息行，最后才放开输入区——
     * 这样"立刻再发"不会与这一条补写抢同一个会话序号。
     *
     * 取消**不消耗派发预算**（仓库不 reserve）：学生停掉一次不该让这一轮少一次机会——重试仍然
     * 是同一逻辑操作的下一次尝试。
     */
    suspend fun stop() {
        val round = inFlightRound ?: return
        if (!studentStoppedRounds.add(round.logicalOperationId)) return
        try {
            modelTasks.cancel(round.requestId)
            appendStoppedRound(round)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            onStopFailed()
        } finally {
            onRoundStopped()
        }
    }

    /** 补一条「已停止」的助手行：序号交给会话计数器分配（不与人抢同一个号）。 */
    private suspend fun appendStoppedRound(round: InFlightRound) {
        val now = System.currentTimeMillis()
        runCatching {
            conversations.appendAssistantMessage(
                AppendTutorAssistantMessageCommand(
                    conversationId = round.conversationId,
                    messageId = "tutor-message:${UUID.randomUUID()}",
                    ordinal = null,
                    replyToMessageId = round.replyToMessageId,
                    bodyMarkdown = TUTOR_CONVERSATION_STOPPED_REPLY_BODY,
                    logicalOperationId = round.logicalOperationId,
                    status = TutorMessageStatus.CANCELLED,
                    createdAtEpochMillis = now,
                    completedAtEpochMillis = now,
                    errorCode = null,
                ),
            )
        }
    }
}
