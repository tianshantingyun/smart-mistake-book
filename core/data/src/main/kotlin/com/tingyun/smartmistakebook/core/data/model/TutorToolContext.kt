package com.tingyun.smartmistakebook.core.data.model

import com.tingyun.smartmistakebook.core.data.study.RoomTutorToolRunner
import com.tingyun.smartmistakebook.core.data.study.TutorKnowledgeCodeRegistry
import com.tingyun.smartmistakebook.core.model.ModelTaskInput
import com.tingyun.smartmistakebook.core.model.TutorConversationIds
import com.tingyun.smartmistakebook.core.model.TutorPlanInput
import com.tingyun.smartmistakebook.core.model.TutorRespondInput
import com.tingyun.smartmistakebook.core.model.disclosesQuestionCandidates

/**
 * 一个任务输入 → 这一轮工具执行要的上下文（`RoomTutorToolRunner.Context`）。
 *
 * 抽成顶层函数是为了两件事：**会话锚、科目、代号注册表按同一规则取**这条规则只写一次；
 * 以及让"大厅没有会话上下文时这些字段都留空"这条边界与 runner 的空范围分支躺在同一屏上
 * （runner 对空范围返回 ok=true，不再把它们报成失败）。
 *
 * 工具环对 Plan 与 Respond 是同一个（D8）：上下文按同一规则取，不按任务类型分叉。
 */
internal fun tutorToolContext(
    input: ModelTaskInput,
    requestId: String,
    allowsExtendedResult: Boolean,
    sessionKnowledgeCodeRegistry: TutorKnowledgeCodeRegistry?,
): RoomTutorToolRunner.Context {
    val respond = input as? TutorRespondInput
    val plan = input as? TutorPlanInput
    val sessionId = respond?.sessionId ?: plan?.sessionId
    return RoomTutorToolRunner.Context(
        subject = respond?.subject ?: plan?.subject,
        // 扩展结果预算的轮内裁决：见仓库里每轮只放一次的守卫（那是唯一能看到同轮兄弟调用的地方）。
        allowsExtendedResult = allowsExtendedResult,
        // 会话锚：讲题会话的 conversationId 由 sessionId 确定性推导
        // （CapturedTutorSessionRoute 的 CreateTutorConversationCommand 同规则），
        // 供 MASTERY_UPDATE 的冷却/配额/审计按会话粒度工作。
        conversationId = sessionId?.let(TutorConversationIds::captured),
        // 裸 sessionId：NOTEBOOK_WRITE 用它 resolve 对应的 capture draft。
        // sessionId（"tutor-session-..."）≠ draftId（"draft-..."），写路径需
        // readTutorSession(sessionId) 拿 draftId 再 readProblemDraft(draftId)。
        // MASTERY_UPDATE 的客观交叉核对也用它回读本轮检查题作答。
        tutorSessionId = sessionId,
        // 客观交叉核对只数当前轮：新一轮重教时上一轮的答错不该永久作废正向判断。
        cycleOrdinal = respond?.cycleOrdinal ?: plan?.cycleOrdinal ?: 1,
        // 幂等命名空间：同一 model-task request 的重试/多轮共享同一 evidenceId 命名空间，
        // 让 MASTERY_UPDATE 的 evidence_id 确定性派生（重试不重复落库）。
        evidenceIdNamespace = requestId,
        // 本轮披露集合是否覆盖候选菜单：NOTEBOOK_READ 的产出形态由它决定——覆盖了才允许
        // 逐条点名别的题（那属于已披露的 RELATED_QUESTION_CANDIDATES），否则只给条数与检索词。
        // 判据取自请求本身（与清单侧核对 includesQuestionCandidates 用的是同一条），
        // 不看解析路由、不看执行位置。
        roundDisclosesQuestionCandidates = input.disclosesQuestionCandidates(),
        // 会话代号注册表：KNOWLEDGE_READ 的追加披露与 MASTERY_UPDATE 的代号解析都走它。
        knowledgeCodeRegistry = sessionKnowledgeCodeRegistry,
    )
}

/**
 * 该输入的会话代号注册表；大厅没有科目上下文与预披露节点，不建（其写工具白名单为空 →
 * 空范围"本轮无可写目标"，见 `tutorToolRoundOutcomes`）。
 */
internal fun sessionKnowledgeCodeRegistry(
    input: ModelTaskInput,
    registries: Map<String, TutorKnowledgeCodeRegistry>,
): TutorKnowledgeCodeRegistry? = when (input) {
    is TutorPlanInput -> registries[input.sessionId]
    is TutorRespondInput -> registries[input.sessionId]
    else -> null
}
