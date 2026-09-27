package com.tingyun.smartmistakebook.feature.tutor

import com.tingyun.smartmistakebook.core.domain.ModelTaskRepository
import com.tingyun.smartmistakebook.core.domain.StudyProfileOverview
import com.tingyun.smartmistakebook.core.domain.TutorConversationRepository
import com.tingyun.smartmistakebook.core.model.ModelTaskSnapshot
import com.tingyun.smartmistakebook.core.model.ModelTaskKind
import com.tingyun.smartmistakebook.core.model.ModelTaskStatus
import com.tingyun.smartmistakebook.core.model.ProviderCapabilitySnapshot
import com.tingyun.smartmistakebook.core.model.TutorConversationMemory
import com.tingyun.smartmistakebook.core.model.TutorPlanInput
import com.tingyun.smartmistakebook.core.model.TutorPlanOutput
import com.tingyun.smartmistakebook.core.model.TutorTurnHistoryEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * A plan turn may start when the single agent gate allows dispatch to the provider right now.
 * That gate is [tutorAgentChatEnabled] (location + consent + capability); it owns the decision
 * so the panel and the command layer cannot drift. Local providers never egress, so they need
 * no consent.
 */
internal class TutorPlanCommands(
    private val scope: CoroutineScope,
    private val sink: TutorPlanSink,
) {
    fun executeTurn(
        cycleOrdinal: Int,
        priorConversationMemory: TutorConversationMemory?,
        priorCycleStudentMessages: List<String>,
        priorTurns: List<TutorTurnHistoryEntry>,
    ) {
        val provider = sink.provider() ?: return
        if (!tutorAgentChatEnabled(provider, ModelTaskKind.TUTOR_PLAN)) return
        val question = sink.question()
        val attempt = tutorPlanAttemptCount(
            sink.planTasks().count { task ->
                val input = task.request.input as? TutorPlanInput
                input?.cycleOrdinal == cycleOrdinal &&
                    input.priorConversationMemory == priorConversationMemory &&
                    input.priorCycleStudentMessages == priorCycleStudentMessages &&
                    input.priorTurns == priorTurns
            },
        )
        val requestId = tutorPlanRequestId(
            question = question,
            provider = provider,
            attempt = attempt,
            cycleOrdinal = cycleOrdinal,
            priorConversationMemory = priorConversationMemory,
            priorCycleStudentMessages = priorCycleStudentMessages,
            priorTurns = priorTurns,
        )
        val occurredAt = sink.clock()
        val request = buildTutorPlanRequest(
            question = question,
            profile = sink.profile(),
            provider = provider,
            requestId = requestId,
            occurredAtEpochMillis = occurredAt,
            cycleOrdinal = cycleOrdinal,
            priorConversationMemory = priorConversationMemory,
            priorCycleStudentMessages = priorCycleStudentMessages,
            priorTurns = priorTurns,
        )
        scope.launch {
            var recorded = false
            sink.modelTasks.execute(request).collect { snapshot ->
                // 成功的讲题轮把正文落进消息行（K1a）：这是"这一轮说了什么"的耐久事实，
                // 会话身份也由它按需建立（K1b，空会话不落库）。只记一次（终态只到一次，
                // 但状态流可能有多个快照）。簿记失败不阻断对话，写入器内部已 runCatching + 留日志。
                if (recorded || snapshot.status != ModelTaskStatus.SUCCEEDED) return@collect
                val output = snapshot.output as? TutorPlanOutput ?: return@collect
                recorded = true
                recordTutorAssistantTurn(
                    conversations = sink.conversations,
                    sessionId = question.sessionId,
                    questionDocumentId = question.questionDocument.document.id,
                    revisionNumber = question.revisionNumber,
                    questionTitle = question.title,
                    requestId = request.requestId,
                    replyToMessageId = null,
                    bodyMarkdown = output.plan.openingMarkdown,
                    thinkingMarkdown = output.plan.thinkingMarkdown,
                    occurredAtEpochMillis = request.occurredAtEpochMillis,
                    completedAtEpochMillis = snapshot.updatedAtEpochMillis,
                )
            }
        }
    }
}

internal class TutorPlanSink(
    val provider: () -> ProviderCapabilitySnapshot?,
    val question: () -> TutorQuestionContext,
    val profile: () -> StudyProfileOverview,
    val clock: () -> Long,
    val planTasks: () -> List<ModelTaskSnapshot>,
    val modelTasks: ModelTaskRepository,
    /**
     * 会话仓库：讲题轮的正文写进 `tutor_message`（K1a）。null 表示该界面/替身不落库——
     * 此时不写消息行，界面上仍按账本渲染（旧行同一条回落路径）。
     */
    val conversations: TutorConversationRepository? = null,
)
