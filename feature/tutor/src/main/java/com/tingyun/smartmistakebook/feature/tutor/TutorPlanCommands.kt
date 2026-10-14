package com.tingyun.smartmistakebook.feature.tutor

import com.tingyun.smartmistakebook.core.domain.ModelTaskRepository
import com.tingyun.smartmistakebook.core.domain.StudyProfileOverview
import com.tingyun.smartmistakebook.core.domain.TutorConversationRepository
import com.tingyun.smartmistakebook.core.model.ActionType
import com.tingyun.smartmistakebook.core.model.AppFailure
import com.tingyun.smartmistakebook.core.model.AppFailureCode
import com.tingyun.smartmistakebook.core.model.ImmutablePayloadConflict
import com.tingyun.smartmistakebook.core.model.ModelTaskSnapshot
import com.tingyun.smartmistakebook.core.model.ModelTaskKind
import com.tingyun.smartmistakebook.core.model.ModelTaskStatus
import com.tingyun.smartmistakebook.core.model.ProviderCapabilitySnapshot
import com.tingyun.smartmistakebook.core.model.Retryability
import com.tingyun.smartmistakebook.core.model.TutorConversationMemory
import com.tingyun.smartmistakebook.core.model.TutorPlanOutput
import com.tingyun.smartmistakebook.core.model.TutorTurnHistoryEntry
import com.tingyun.smartmistakebook.core.model.appFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

internal const val TUTOR_PLAN_REPLAY_CONFLICT_TITLE = "这一轮讲解没有派出去"
internal const val TUTOR_PLAN_REPLAY_CONFLICT_MESSAGE =
    "题目已经保存，再试一次就能重新开始这一轮的讲解。"

/**
 * 「同标识、不同载荷」的冲突在讲题计划路径上的学生可见处置（KD-35）。
 *
 * 可重试而不是不可重试：重试会带着**新身份**（尾缀 attempt 递增）重来，下一次不会撞同一行；
 * 数据全在（题目、讲解草稿都没被动过）。学生能做的动作就是再点一次，所以它是唯一的主出口。
 */
internal fun tutorPlanReplayConflictError(): AppFailure = appFailure(
    code = AppFailureCode.TUTOR_OPERATION_REPLAY_MISMATCH,
    title = TUTOR_PLAN_REPLAY_CONFLICT_TITLE,
    message = TUTOR_PLAN_REPLAY_CONFLICT_MESSAGE,
    dataPreserved = true,
    retryability = Retryability.RETRYABLE,
    primaryAction = ActionType.RETRY,
)

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
            planTasks = sink.planTasks(),
            cycleOrdinal = cycleOrdinal,
            priorConversationMemory = priorConversationMemory,
            priorCycleStudentMessages = priorCycleStudentMessages,
            priorTurns = priorTurns,
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
        // 一次新派发从"没有失败"开始：上一次的冲突文案不许留在屏幕上（与回应轮同一纪律：
        // `TutorRespondCommands.collect` 进门前先清 `setChatStartError(null)`）。
        sink.setPlanStartError(null)
        scope.launch {
            // KD-35：写入路径在"同标识、不同载荷"上抛 `ImmutablePayloadConflictException`
            // （`ModelTaskTransactionDao.kt:233`），而这里抛出的位置是**组合作用域**的协程——
            // 没人接住时异常直达主线程，进程直接消失（crash buffer 里唯一的 FATAL EXCEPTION）。
            //
            // 只接这一种，是因为"接住"和"吞掉"只差一个判据：
            // - `CancellationException` 也在 `IllegalStateException` 这族里（下面单独重抛），
            //   放宽到整族会把协程取消吞掉；
            // - `LearningLedgerIntegrityException` 一类是**正直性缺陷**，伪装成"可重试的失败"
            //   只会把真正的数据问题藏起来——它们照旧抛出去。
            // 冲突本身按"这一次没写成、请再试一次"如实报，重试会带新身份重来（见 attempt）。
            try {
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
                        requestId = request.requestId,
                        replyToMessageId = null,
                        bodyMarkdown = output.plan.openingMarkdown,
                        thinkingMarkdown = output.plan.thinkingMarkdown,
                        occurredAtEpochMillis = request.occurredAtEpochMillis,
                        completedAtEpochMillis = snapshot.updatedAtEpochMillis,
                        // B1：这一轮查阅了什么的痕迹，与正文同一次写入。
                        toolTraceJson = toolTraceJsonFor(sink.modelTasks, request.requestId),
                        // S2：本轮披露的课本材料标题（来源标签的核对基准）。
                        disclosedMaterialTitles = question.reviewedTeachingReferences
                            .map { reference -> reference.title },
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: IllegalStateException) {
                if (failure !is ImmutablePayloadConflict) throw failure
                android.util.Log.w(
                    "TutorPlan",
                    "Plan dispatch was rejected as an immutable payload replay conflict",
                    failure,
                )
                sink.setPlanStartError(tutorPlanReplayConflictError())
            }
        }
    }
}

internal class TutorPlanSink(
    val provider: () -> ProviderCapabilitySnapshot?,
    val question: () -> TutorQuestionContext,
    val profile: () -> StudyProfileOverview,
    val clock: () -> Long,
    /**
     * 本会话**当前**的计划任务（不按题过滤，判据自己按轮次/前情筛）。
     *
     * 必须读**最新的**那一份：这一次调用的结果直接决定"重试算第几次尝试"，从而决定 requestId。
     * 读一份被冻住（例如首次组合时还是空列表）的旧列表，会把每一次重试都算成第 0 次、复用同一
     * 个标识，而重试的时间戳必然不同——写入路径随即以"同标识、不同载荷"拒绝（KD-35）。
     */
    val planTasks: () -> List<ModelTaskSnapshot>,
    val modelTasks: ModelTaskRepository,
    /**
     * 计划派发**没能开始**时的如实告知（KD-35）：拿到失败文案的界面要给出可重试的出口。
     * 传 `null` 表示"这一轮重新开始派发了"，上一次的文案随之下线。
     */
    val setPlanStartError: (AppFailure?) -> Unit,
    /**
     * 会话仓库：讲题轮的正文写进 `tutor_message`（K1a）。null 表示该界面/替身不落库——
     * 此时不写消息行，界面上仍按账本渲染（旧行同一条回落路径）。
     */
    val conversations: TutorConversationRepository? = null,
)
