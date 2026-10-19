package com.tingyun.smartmistakebook.feature.review

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tingyun.smartmistakebook.core.domain.NewIntroductionPolicy
import com.tingyun.smartmistakebook.core.domain.StudyProfileOverview
import com.tingyun.smartmistakebook.core.domain.StudyReviewOverview
import com.tingyun.smartmistakebook.core.ui.OutlineActionChip
import com.tingyun.smartmistakebook.core.ui.PaperDivider
import com.tingyun.smartmistakebook.core.ui.PrimaryActionButton
import com.tingyun.smartmistakebook.core.ui.RootPageColumn
import com.tingyun.smartmistakebook.core.ui.SectionHeader
import com.tingyun.smartmistakebook.core.ui.SmartColors
import com.tingyun.smartmistakebook.core.ui.SubjectIcon
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun ReviewRoute(
    overview: StudyReviewOverview,
    profile: StudyProfileOverview,
    onStartReview: () -> Unit,
    onStartKnowledgeReview: (() -> Unit)? = null,
    /** 今日可复习知识点数（spec §3.1）；null = 未加载/不可用。>0 才显示知识点入口。 */
    knowledgeReviewCount: Int? = null,
    /**
     * 错题本里已保存的题数（PRD-06 空态半边）。null = 未接线/未知——此时绝不断言"还没有题"。
     * I1 接线建议：`experience.catalog.size.takeIf { experience.status == StudyDataStatus.READY }`。
     */
    savedQuestionCount: Int? = null,
    /**
     * 待处理录入任务中需要学生继续的数量（只读）。
     * 来源建议：`CaptureWorkflowRepository.observePendingCaptures()` 里
     * `SOURCE_UNAVAILABLE / RECAPTURE_REQUIRED / RETRY_OR_MANUAL` 的条数——只读现有状态，
     * 不新增模型调用、不重新拍照。
     */
    pendingFailedTaskCount: Int = 0,
    /**
     * 空库的录入入口（PRD-06）。I1 接线建议：
     * `{ navController.navigate(Routes.capture(CaptureEntryOrigin.LIBRARY)) }`——与错题本栏同一入口，
     * 落到含"手动建题"的方式选择，不误入要求照片的讲题入口。null = 未接线，不渲染 CTA。
     */
    onCapture: (() -> Unit)? = null,
    /**
     * 继续处理待处理任务。I1 接线建议同上（录入页同页展示待处理列表）；null = 不渲染入口。
     */
    onContinuePendingTasks: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val surfaceState = resolveReviewSurfaceState(
        scheduledCount = overview.scheduledCount,
        activeSessionId = overview.activeSessionId,
        completedToday = overview.completedToday,
        hasLearningEvidence = profile.hasLearningEvidence,
        savedQuestionCount = savedQuestionCount,
        pendingFailedTaskCount = pendingFailedTaskCount,
        intakeBacklogCount = overview.intakeBacklogCount,
    )
    RootPageColumn(
        modifier = modifier.testTag("review_root"),
    ) {
        ReviewGreeting()
        Spacer(Modifier.height(24.dp))
        SectionHeader(title = "今日复习")
        Spacer(Modifier.height(24.dp))
        if (surfaceState == ReviewSurfaceState.NORMAL) {
            ReviewSummary(overview)
        } else {
            reviewStateCopy(surfaceState, pendingFailedTaskCount)?.let { copy ->
                ReviewStateNotice(
                    copy = copy,
                    action = when (surfaceState) {
                        ReviewSurfaceState.EMPTY_LIBRARY -> onCapture
                        ReviewSurfaceState.PENDING_FAILED_TASKS -> onContinuePendingTasks
                        else -> null
                    },
                    actionTestTag = when (surfaceState) {
                        ReviewSurfaceState.EMPTY_LIBRARY -> "review_capture_button"
                        ReviewSurfaceState.PENDING_FAILED_TASKS -> "review_continue_pending_tasks"
                        else -> "review_state_action"
                    },
                )
            }
        }
        Spacer(Modifier.height(30.dp))
        // 双复习入口（spec dual-review-entry §3.1）：知识点复习与错题复习并列、学生自由选，
        // 建议先巩固概念再做错题。入口只在今天真有可复习知识点（count>0）时出现——count 由
        // app 层经 currentKnowledgeReviewPlan 实算（含"有讲解材料可出题"过滤），首页不猜测；
        // 错题复习是否完成不影响该入口（两者独立，自由选）。
        if (knowledgeReviewEntryVisible(knowledgeReviewCount, onStartKnowledgeReview != null)) {
            KnowledgeReviewEntryCard(
                knowledgeCount = requireNotNull(knowledgeReviewCount),
                onClick = requireNotNull(onStartKnowledgeReview),
                modifier = Modifier.testTag("review_start_knowledge_review"),
            )
            Spacer(Modifier.height(12.dp))
        }
        PrimaryActionButton(
            text = when {
                overview.completedToday -> "今日复习已完成"
                overview.activeSessionId != null -> "继续今日复习"
                overview.scheduledCount > 0 -> "开始今日复习"
                else -> "暂无待复习题"
            },
            onClick = onStartReview,
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .testTag("review_start_button"),
            icon = Icons.AutoMirrored.Outlined.MenuBook,
            enabled = overview.scheduledCount > 0 && !overview.completedToday,
        )
        // 失败待处理任务在正常计划态下也要保留入口：它属于录入任务，不是复习队列的一部分。
        if (surfaceState != ReviewSurfaceState.PENDING_FAILED_TASKS &&
            reviewPendingTasksEntryVisible(
                count = pendingFailedTaskCount,
                hasContinue = onContinuePendingTasks != null,
            )
        ) {
            Spacer(Modifier.height(12.dp))
            OutlineActionChip(
                text = "继续处理 $pendingFailedTaskCount 道待处理题目",
                onClick = requireNotNull(onContinuePendingTasks),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("review_continue_pending_tasks"),
            )
        }
        // Intake backlog coverage promise (spec batch-intake §6 P3): questions
        // recorded but not yet introduced carry no learning pressure; the
        // surface shows them as a plan ("每天约 X 题新学，约 M 天覆盖") so a
        // large import reads as a schedule, not a wall.
        if (overview.intakeBacklogCount > 0) {
            Spacer(Modifier.height(12.dp))
            IntakeBacklogPreview(
                backlogCount = overview.intakeBacklogCount,
                medianEstimateSeconds = overview.intakeMedianEstimateSeconds,
                modifier = Modifier.testTag("review_intake_backlog_preview"),
            )
        }
        reviewContinuityText(overview)?.let { continuity ->
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = SmartColors.JadeSoft.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(12.dp),
                    )
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .semantics(mergeDescendants = true) {}
                    .testTag("review_continuity"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = SmartColors.JadeDark,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = continuity,
                    color = SmartColors.Ink,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        if (profile.hasLearningEvidence && profile.weaknesses.isNotEmpty()) {
            PaperDivider(Modifier.padding(vertical = 26.dp))
            SectionHeader(title = "薄弱知识点")
            Spacer(Modifier.height(10.dp))
            profile.weaknesses.take(2).forEachIndexed { index, weakness ->
                WeakPointRow(
                    title = weakness.displayName,
                    // P8（规格 §1.2）：detail 追加粗粒度区间文案（与掌握档同一文案函数）。
                    detail = "独立作答把握：" +
                        com.tingyun.smartmistakebook.core.ui.masteryBandLabel(
                            weakness.conservativeMasteryScore,
                        ) + "（" +
                        com.tingyun.smartmistakebook.core.ui.masteryIntervalLabel(
                            weakness.conservativeMasteryScore,
                            weakness.masteryIntervalUpper,
                        ) + "）",
                    icon = if (index == 0) {
                        Icons.AutoMirrored.Outlined.ShowChart
                    } else {
                        Icons.Outlined.Bolt
                    },
                    testTag = "review_weakness_$index",
                )
            }
        }
    }
}

/**
 * 复习首页四态（PRD-06 空态半边）：
 * - [NORMAL]：有今日计划 / 进行中会话 / 今日已完成 → 统计摘要；
 * - [EMPTY_LIBRARY]：快照明确为空且没有任何学习证据 → 录入 CTA；
 * - [NO_DUE_QUESTIONS]：有题（或已有历史证据）但今天没有到期 → 与"没有题"分开；
 * - [PENDING_FAILED_TASKS]：有需要学生继续的录入任务（且无今日计划）→ 保留继续入口；
 * - [UNKNOWN]：题数未接线 / 快照未就绪 → 保留既有"暂无学习记录"，不谎称空库。
 */
internal enum class ReviewSurfaceState {
    NORMAL,
    EMPTY_LIBRARY,
    NO_DUE_QUESTIONS,
    PENDING_FAILED_TASKS,
    UNKNOWN,
}

internal fun resolveReviewSurfaceState(
    scheduledCount: Int,
    activeSessionId: String?,
    completedToday: Boolean,
    hasLearningEvidence: Boolean,
    savedQuestionCount: Int?,
    pendingFailedTaskCount: Int,
    intakeBacklogCount: Int = 0,
): ReviewSurfaceState = when {
    scheduledCount > 0 || activeSessionId != null || completedToday -> ReviewSurfaceState.NORMAL
    pendingFailedTaskCount > 0 -> ReviewSurfaceState.PENDING_FAILED_TASKS
    savedQuestionCount != null && savedQuestionCount > 0 -> ReviewSurfaceState.NO_DUE_QUESTIONS
    // 待引入的新题（intake backlog）同样是"已保存的题"：只是今天还没到期。
    intakeBacklogCount > 0 -> ReviewSurfaceState.NO_DUE_QUESTIONS
    // 有学习证据说明历史上存在过题（可能已归档、不在当前目录）：不能谎称从没有题。
    hasLearningEvidence -> ReviewSurfaceState.NO_DUE_QUESTIONS
    savedQuestionCount == 0 -> ReviewSurfaceState.EMPTY_LIBRARY
    else -> ReviewSurfaceState.UNKNOWN
}

/** 空态/待处理态的固定文案；只描述真实存在的数据与本地动作，不承诺模型能力。 */
internal data class ReviewStateCopy(
    val title: String,
    val supportingText: String,
    val actionLabel: String? = null,
)

internal fun reviewStateCopy(
    state: ReviewSurfaceState,
    pendingFailedTaskCount: Int = 0,
): ReviewStateCopy? = when (state) {
    ReviewSurfaceState.NORMAL -> null
    ReviewSurfaceState.EMPTY_LIBRARY -> ReviewStateCopy(
        // 只说"没有可复习的题"：已移出的题不在复习池里，不能断言整个错题本为空。
        title = "还没有可复习的题",
        supportingText = "先录入第一道题。没有照片也可以手动输入题面，之后这里会安排复习。",
        actionLabel = "录入第一道题",
    )
    ReviewSurfaceState.NO_DUE_QUESTIONS -> ReviewStateCopy(
        title = "今天没有到期的题",
        supportingText = "到期安排会出现在这里；已保存的内容可以到错题本查看。",
    )
    ReviewSurfaceState.PENDING_FAILED_TASKS -> ReviewStateCopy(
        title = "还有题目没有处理完",
        supportingText = "还有 $pendingFailedTaskCount 道录入没有处理完，记录保存在本机；" +
            "可从待处理列表继续。",
        actionLabel = "继续处理",
    )
    ReviewSurfaceState.UNKNOWN -> ReviewStateCopy(
        title = "暂无学习记录",
        supportingText = "学习记录生成后会显示在这里。",
    )
}

internal fun reviewPendingTasksEntryVisible(count: Int, hasContinue: Boolean): Boolean =
    count > 0 && hasContinue

@Composable
private fun ReviewStateNotice(
    copy: ReviewStateCopy,
    action: (() -> Unit)?,
    actionTestTag: String,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = copy.title,
            color = SmartColors.Ink,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.testTag("review_state_title"),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = copy.supportingText,
            color = SmartColors.InkSecondary,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag("review_state_supporting"),
        )
        if (copy.actionLabel != null && action != null) {
            Spacer(Modifier.height(14.dp))
            PrimaryActionButton(
                text = copy.actionLabel,
                onClick = action,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag(actionTestTag),
            )
        }
    }
}

/**
 * 知识点复习入口的显隐规则（spec dual-review-entry §3.1）：今天真有可复习知识点
 * （app 层实算的 [count] > 0，含"有讲解材料可出题"过滤）且已接线启动回调时才显示。
 * 与错题复习是否完成无关——两者独立、学生自由选。
 */
internal fun knowledgeReviewEntryVisible(
    count: Int?,
    hasStartCallback: Boolean,
): Boolean = hasStartCallback && count != null && count > 0

/**
 * 知识点复习入口卡：与错题复习并列的第二个入口，建议先巩固概念再练题。
 * 显示今日可复习知识点数（spec §3.1"建议先复习知识点：XX 个"）。
 */
@Composable
private fun KnowledgeReviewEntryCard(
    knowledgeCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = SmartColors.JadeSoft.copy(alpha = 0.45f),
        border = androidx.compose.foundation.BorderStroke(1.dp, SmartColors.Jade.copy(alpha = 0.5f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SubjectIcon(
                icon = Icons.AutoMirrored.Outlined.ShowChart,
                contentDescription = "知识点复习",
                modifier = Modifier.size(44.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "先复习知识点",
                    style = MaterialTheme.typography.titleMedium,
                    color = SmartColors.Ink,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "建议先复习知识点：$knowledgeCount 个",
                    style = MaterialTheme.typography.bodySmall,
                    color = SmartColors.InkSecondary,
                )
            }
            Text(
                text = "开始",
                color = SmartColors.JadeDark,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

internal fun reviewContinuityText(overview: StudyReviewOverview): String? = when {
    overview.completionStreakDays <= 0 -> null
    overview.completedToday ->
        "今天已完成 · 连续复习 ${overview.completionStreakDays} 天"
    else ->
        "连续复习 ${overview.completionStreakDays} 天 · 完成今天的安排后自动记录"
}

@Composable
private fun ReviewGreeting() {
    val now = remember { Date() }
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greeting = when (hour) {
        in 5..10 -> "早上好，同学"
        in 11..13 -> "中午好，同学"
        in 14..17 -> "下午好，同学"
        else -> "晚上好，同学"
    }
    val dateLabel = remember(now) {
        SimpleDateFormat("M月d日 · EEEE", Locale.SIMPLIFIED_CHINESE).format(now)
    }
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = greeting,
            color = SmartColors.Ink,
            fontSize = 29.sp,
            lineHeight = 38.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = dateLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = SmartColors.InkSecondary,
        )
    }
}

@Composable
private fun ReviewSummary(overview: StudyReviewOverview) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SummaryValue(
            value = overview.scheduledCount.toString(),
            label = if (overview.completedToday) "道今日已完成" else "道计划复习",
        )
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(48.dp)
                .background(SmartColors.Divider),
        )
        SummaryValue(
            value = ((overview.estimatedSeconds + 59) / 60).toString(),
            label = "预计用时（分钟）",
        )
    }
}

@Composable
private fun SummaryValue(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            color = SmartColors.Ink,
            fontSize = 42.sp,
            lineHeight = 46.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = SmartColors.InkSecondary,
        )
    }
}

@Composable
private fun WeakPointRow(
    title: String,
    detail: String,
    icon: ImageVector,
    testTag: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SubjectIcon(
            icon = icon,
            contentDescription = "$title 知识点",
            modifier = Modifier.size(44.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = SmartColors.Ink,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = SmartColors.InkSecondary,
            )
        }
    }
}


/**
 * Intake-backlog coverage preview (spec batch-intake §6 P3): renders the
 * backlog as a schedule — "每天约 X 题新学，约 M 天覆盖" — computed with the
 * same NewIntroductionPolicy.preview the scheduler uses, so the promise
 * matches what the daily introduction actually does. A median reference
 * budget of 15 minutes of new material per day keeps the estimate stable
 * without threading the full time-budget preference into this surface.
 */
@Composable
private fun IntakeBacklogPreview(
    backlogCount: Int,
    medianEstimateSeconds: Int,
    modifier: Modifier = Modifier,
) {
    val estimateSeconds = if (medianEstimateSeconds > 0) medianEstimateSeconds else 60
    val preview = remember(backlogCount, estimateSeconds) {
        NewIntroductionPolicy.preview(
            backlogCount = backlogCount,
            typicalItemSeconds = estimateSeconds,
            timeBudgetSeconds = DEFAULT_INTAKE_DAILY_BUDGET_SECONDS,
        )
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = SmartColors.JadeSoft.copy(alpha = 0.35f),
                shape = RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (preview.daysToCover <= 0) {
                "还有 $backlogCount 道新题待引入"
            } else {
                "还有 $backlogCount 道新题待学：每天约 ${preview.perDayItems} 题，约 ${preview.daysToCover} 天覆盖"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = SmartColors.Ink,
        )
    }
}

/** Reference daily budget (seconds) the intake preview is computed from. */
private const val DEFAULT_INTAKE_DAILY_BUDGET_SECONDS = 900
