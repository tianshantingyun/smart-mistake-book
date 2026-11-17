package com.tingyun.smartmistakebook.core.model

import kotlinx.serialization.Serializable

/** Host-resolved app context shared by conversation, teaching and planning requests. */
@Serializable
data class TutorAgentContext(
    val subject: String? = null,
    val subjects: List<String> = emptyList(),
    val selectedQuestion: TutorAgentQuestion? = null,
    val knownEntryIds: List<String> = emptyList(),
    val knownFigureIds: List<String> = emptyList(),
    val actionOutcomes: List<TutorLobbyLocalActionOutcome> = emptyList(),
    val backupReferences: List<TutorBackupReference> = emptyList(),
    val javascriptStateJson: String? = null,
    val feedbackImages: List<ModelEgressAssetGrant> = emptyList(),
    /**
     * 本轮由应用注入的复习计划/容量/排程/考试只读事实（JSON，≤16 KiB）。它不直接进提示词；
     * `REVIEW_PLAN_READ` 工具按需回读，避免每轮提示词都背一份计划快照。
     */
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER)
    val reviewContextJson: String? = null,
    /**
     * 本轮真实可执行的应用动作集合（应用层执行器按仓储就绪位裁剪）。
     * null = 旧输入/测试没有提供快照：`forRound` 回退到静态实现集合。
     */
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER)
    val availableLocalActions: Set<TutorLocalAction>? = null,
    val roundsUsed: Int = 0,
) {
    init {
        subject?.requireSafeModelText("Agent subject", 80, false)
        require(subjects.size <= 32 && subjects.distinct().size == subjects.size)
        require(knownEntryIds.size <= 180 && knownEntryIds.distinct().size == knownEntryIds.size)
        require(knownFigureIds.size <= 32 && actionOutcomes.size <= 12)
        require(backupReferences.size <= 20 && feedbackImages.size <= 4)
        require(availableLocalActions.orEmpty().all { it in TutorCapabilitySnapshot.IMPLEMENTED_LOCAL_ACTIONS }) {
            "Agent context cannot advertise an unimplemented local action"
        }
        require(roundsUsed in 0..TutorToolRoundResult.MAX_TOOL_ROUNDS)
        javascriptStateJson?.let {
            require(it.toByteArray(Charsets.UTF_8).size <= 16 * 1024)
            kotlinx.serialization.json.Json.parseToJsonElement(it)
        }
        reviewContextJson?.let {
            require(it.toByteArray(Charsets.UTF_8).size <= 16 * 1024)
            require(kotlinx.serialization.json.Json.parseToJsonElement(it) is
                kotlinx.serialization.json.JsonObject)
        }
    }
}

@Serializable
data class TutorAgentQuestion(
    val entryId: String,
    val problemId: String,
    val problemRevisionId: String,
    val subject: String,
    val title: String,
    val document: QuestionDocument,
    val tutorSessionId: String? = null,
    val sourceImages: List<ModelEgressAssetGrant> = emptyList(),
) {
    init {
        listOf(entryId, problemId, problemRevisionId).forEach {
            it.requireSafeModelText("Agent question id", ModelTaskRequest.MAX_ID_CHARS, false)
        }
        require(sourceImages.size <= 8)
    }
}

fun ModelTaskInput.agentContextSnapshot(): TutorAgentContext? = when (this) {
    is TutorLobbyInput -> agentContext
    is TutorPlanInput -> agentContext
    is TutorRespondInput -> agentContext
    else -> null
}

fun ModelTaskInput.withAgentContext(context: TutorAgentContext): ModelTaskInput = when (this) {
    is TutorLobbyInput -> copy(agentContext = context)
    is TutorPlanInput -> copy(agentContext = context)
    is TutorRespondInput -> copy(agentContext = context)
    else -> this
}
