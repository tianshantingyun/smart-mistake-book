package com.tingyun.smartmistakebook

import com.tingyun.smartmistakebook.core.domain.ExamCalendarEntry
import com.tingyun.smartmistakebook.core.domain.KnowledgeBaseAvailability
import com.tingyun.smartmistakebook.core.domain.ReviewDayPreference
import com.tingyun.smartmistakebook.core.domain.SchedulingOptions
import com.tingyun.smartmistakebook.core.domain.StudyCatalogEntry
import com.tingyun.smartmistakebook.core.domain.StudyDataStatus
import com.tingyun.smartmistakebook.core.domain.StudyExperienceSnapshot
import com.tingyun.smartmistakebook.core.ui.studentSubjectLabel
import com.tingyun.smartmistakebook.core.ui.studentLabel
import com.tingyun.smartmistakebook.feature.tutor.TutorLearningTool
import com.tingyun.smartmistakebook.feature.tutor.TutorLearningToolAction
import com.tingyun.smartmistakebook.feature.tutor.TutorLearningToolDraft
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import java.security.MessageDigest
import java.time.LocalDate

internal const val AgentToolPromptKey = "agent_tool_prompt"
private const val PaperMarker = "【智能体组卷】"
private const val MasteryMarker = "【智能体薄弱点分析】"
private const val ReviewMarker = "【智能体复习规划】"
private const val PaperResult = "【组卷确认数据】"
private const val ReviewResult = "【复习确认数据】"
private val paperLine = Regex("^\\[([a-f0-9]{12})] ")

internal fun agentLearningTools(
    experience: StudyExperienceSnapshot,
    knowledgeBase: KnowledgeBaseAvailability,
    schedulingReady: Boolean,
    open: (TutorLearningTool) -> Unit,
): List<TutorLearningToolAction> {
    val unavailable = when (experience.status) {
        StudyDataStatus.LOADING -> "正在读取本机学习记录…"
        StudyDataStatus.ERROR -> "本机学习记录暂不可用"
        StudyDataStatus.READY -> null
    }
    val profile = experience.profile
    val hasMastery = (profile.weaknesses + profile.strengths).isNotEmpty()
    return listOf(
        toolAction(
            TutorLearningTool.PAPER_ASSEMBLY,
            "智能体从错题本选题，确认后生成 PDF",
            unavailable ?: "错题本还没有可选题目".takeIf { experience.catalog.isEmpty() },
            open,
        ),
        toolAction(
            TutorLearningTool.LEARNING_MASTERY,
            "根据掌握库分析薄弱点",
            unavailable ?: when {
                hasMastery -> null
                knowledgeBase == KnowledgeBaseAvailability.Preparing && profile.hasLearningEvidence -> "掌握分析正在准备"
                knowledgeBase is KnowledgeBaseAvailability.Unavailable && profile.hasLearningEvidence -> "掌握分析暂不可用"
                else -> "完成练习后可分析薄弱点"
            },
            open,
        ),
        toolAction(
            TutorLearningTool.REVIEW_PLANNING,
            "按天调整重点、题量和主观题比例",
            unavailable ?: "正在读取复习安排…".takeUnless { schedulingReady },
            open,
        ),
    )
}

private fun toolAction(
    tool: TutorLearningTool,
    detail: String,
    unavailable: String?,
    open: (TutorLearningTool) -> Unit,
) = TutorLearningToolAction(
    tool = tool,
    detail = unavailable ?: detail,
    onOpen = if (unavailable == null) ({ open(tool) }) else null,
)

internal fun paperAgentPrompt(experience: StudyExperienceSnapshot): String = learningToolPrompt(
    PaperMarker,
    "请从错题本选 6 道值得巩固的题，题量不足时少选。",
) {
    appendLine("从以下错题选题并简述理由。末行输出 $PaperResult{\"ids\":[\"候选编号\"]}，按出题顺序，只用下列编号且不重复。")
    experience.catalog.groupBy { it.subject }.values
        .flatMap { entries -> entries.take(8).mapIndexed { rank, entry -> rank to entry } }
        .sortedBy { it.first }.take(14).forEach { (_, entry) ->
            val title = entry.title.lineSequence().first().take(25)
            val topic = entry.knowledgeLabels.firstOrNull()?.lineSequence()?.first()?.take(12).orEmpty()
            appendLine("[${entry.paperCode()}] ${entry.subject.studentSubjectLabel()} $title $topic".trimEnd())
        }
}.trim()

internal fun masteryAgentPrompt(experience: StudyExperienceSnapshot): String = learningToolPrompt(
    MasteryMarker,
    "请根据掌握记录，分析目前最需要巩固的知识点。",
) {
    appendLine("依据掌握库的估计与样本列出优先巩固项、证据和练习建议，区分薄弱和样本不足。")
    val profile = experience.profile
    if (!profile.projectionIsCurrent) appendLine("以下是上次投影，结果待更新。")
    appendLine("已记录作答：${profile.recordedAttemptCount} 次。")
    (profile.weaknesses.take(8).map { "薄弱" to it } + profile.strengths.take(4).map { "较强" to it })
        .forEach { (label, point) ->
            val low = (point.conservativeMasteryScore * 100).toInt()
            val upper = point.masteryIntervalUpper?.let { "–${(it * 100).toInt()}" }.orEmpty()
            appendLine("$label｜${point.subject.studentLabel()} ${point.displayName.take(34)}｜掌握估计 $low$upper%｜独立正确 ${point.independentCorrectObservationCount} 次")
        }
}.trim()

internal fun reviewAgentPrompt(
    experience: StudyExperienceSnapshot,
    options: SchedulingOptions,
    exams: List<ExamCalendarEntry>,
    preferences: List<ReviewDayPreference> = emptyList(),
    today: LocalDate = LocalDate.now(),
): String = learningToolPrompt(ReviewMarker, "请安排未来三天的复习，给出重点、题量和主观题比例。") {
    appendLine("本地日期 $today，每天 ${options.dailyReviewMinutes} 分钟。今日计划 ${experience.review.scheduledCount} 道。")
    if (experience.review.activeSessionId != null || experience.review.completedToday) {
        appendLine("今日已开始或完成，请从明天安排。")
    }
    val focus = reviewFocusLabels(experience).take(12)
    appendLine("可用方向：${focus.joinToString("、")}")
    val weaknesses = experience.profile.weaknesses.take(4).joinToString("、") { point ->
        "${point.displayName.take(30)}（保守掌握估计 ${(point.conservativeMasteryScore * 100).toInt()}%）"
    }
    if (weaknesses.isNotEmpty()) appendLine("待巩固：$weaknesses")
    preferences.filter { it.epochDay in today.toEpochDay()..today.plusDays(6).toEpochDay() }
        .sortedBy { it.epochDay }.take(7).forEach { day ->
            appendLine(
                "${LocalDate.ofEpochDay(day.epochDay)} 已设：${day.focus.joinToString("、").take(64)} " +
                    "最多 ${day.questionLimit} 题，主观题目标 ${day.subjectivePercent}%。",
            )
        }
    exams.filter { it.examEpochDay >= today.toEpochDay() }
        .sortedBy { it.examEpochDay }.take(3).forEach { exam ->
            appendLine("${exam.subject.take(24)}考试：${LocalDate.ofEpochDay(exam.examEpochDay)}")
        }
    appendLine("末行输出 $ReviewResult{\"days\":[{\"date\":\"$today\",\"focus\":[],\"questionLimit\":10,\"subjectivePercent\":50}]}。只用上述方向（最多4个），日期为今天至未来6天，题量1至40，比例0至100。")
}

private fun learningToolPrompt(marker: String, request: String, context: StringBuilder.() -> Unit): String =
    TutorLearningToolDraft(marker, request, buildString(context).trim()).messageFor(request)

internal fun reviewFocusLabels(experience: StudyExperienceSnapshot): List<String> =
    (experience.profile.weaknesses.map { it.displayName } + experience.catalog.map { it.subject } +
        experience.catalog.flatMap { it.knowledgeLabels + it.chapterLabels })
        .filter { it.isNotBlank() && it.length <= 48 && it.none(Char::isISOControl) }.distinct()

internal fun StudyCatalogEntry.paperCode(): String = agentEffectId("$entryId\n$problemRevisionId").take(12)

internal fun agentEffectId(value: String): String =
    MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 0xff) }

internal sealed interface AgentToolProposal {
    data class Paper(val codes: List<String>) : AgentToolProposal
    data class Review(val days: List<ReviewDayPreference>) : AgentToolProposal
}

internal fun toolReplyKind(studentMessage: String?): String? = when {
    studentMessage?.startsWith("$PaperMarker\n") == true -> PaperResult
    studentMessage?.startsWith("$ReviewMarker\n") == true -> ReviewResult
    else -> null
}

internal fun agentToolReplyText(body: String, studentMessage: String?): String =
    toolReplyKind(studentMessage)?.let { marker -> body.substringBefore(marker).trimEnd() }
        ?.ifBlank { "智能体建议" } ?: body

internal fun decodeAgentToolProposal(body: String, studentMessage: String): AgentToolProposal? {
    val marker = toolReplyKind(studentMessage) ?: return null
    val payload = body.substringAfter(marker, missingDelimiterValue = "").trim()
    if (payload.isBlank() || payload.length > 6_000) return null
    return runCatching {
        val root = Json.parseToJsonElement(payload) as JsonObject
        if (marker == PaperResult) {
            require(root.keys == setOf("ids"))
            val context = TutorLearningToolDraft.fromMessage(studentMessage)?.context ?: studentMessage
            val allowed = context.lineSequence().mapNotNull { paperLine.find(it)?.groupValues?.get(1) }.toSet()
            val codes = (root.getValue("ids") as JsonArray).map { (it as JsonPrimitive).also { v -> require(v.isString) }.content }
            require(codes.size in 1..18 && codes.distinct().size == codes.size && codes.all { it in allowed })
            AgentToolProposal.Paper(codes)
        } else {
            require(root.keys == setOf("days"))
            val days = (root.getValue("days") as JsonArray).map { value ->
                val day = value as JsonObject
                require(day.keys == setOf("date", "focus", "questionLimit", "subjectivePercent"))
                ReviewDayPreference(
                    epochDay = LocalDate.parse((day.getValue("date") as JsonPrimitive).content).toEpochDay(),
                    focus = (day.getValue("focus") as JsonArray).map { (it as JsonPrimitive).also { v -> require(v.isString) }.content },
                    questionLimit = requireNotNull((day.getValue("questionLimit") as JsonPrimitive).takeUnless { it.isString }?.intOrNull),
                    subjectivePercent = requireNotNull((day.getValue("subjectivePercent") as JsonPrimitive).takeUnless { it.isString }?.intOrNull),
                )
            }
            require(days.size in 1..7 && days.map { it.epochDay }.distinct().size == days.size)
            AgentToolProposal.Review(days.sortedBy { it.epochDay })
        }
    }.getOrNull()
}
