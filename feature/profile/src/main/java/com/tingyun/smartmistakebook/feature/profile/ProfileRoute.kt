package com.tingyun.smartmistakebook.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import com.tingyun.smartmistakebook.core.ui.MasteryEstimateBar
import com.tingyun.smartmistakebook.core.ui.masteryEstimateColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tingyun.smartmistakebook.core.domain.StudyKnowledgeSummary
import com.tingyun.smartmistakebook.core.domain.StudyProfileOverview
import com.tingyun.smartmistakebook.core.domain.StudyReviewOverview
import com.tingyun.smartmistakebook.core.model.MasteryStatus
import com.tingyun.smartmistakebook.core.model.AppCapabilitySnapshot
import com.tingyun.smartmistakebook.core.model.NetworkMode
import com.tingyun.smartmistakebook.core.ui.Divider
import com.tingyun.smartmistakebook.core.ui.Ink
import com.tingyun.smartmistakebook.core.ui.InkSecondary
import com.tingyun.smartmistakebook.core.ui.JadeActive
import com.tingyun.smartmistakebook.core.ui.JadeSoft
import com.tingyun.smartmistakebook.core.ui.PaperDivider
import com.tingyun.smartmistakebook.core.ui.RootPageColumn
import com.tingyun.smartmistakebook.core.ui.SectionHeader
import com.tingyun.smartmistakebook.core.ui.SubjectIcon
import com.tingyun.smartmistakebook.core.ui.Track
import com.tingyun.smartmistakebook.core.ui.ClaudeSettingsRow
import com.tingyun.smartmistakebook.core.ui.WorkspacePageHeader
import com.tingyun.smartmistakebook.core.ui.WorkspacePanel
import com.tingyun.smartmistakebook.core.ui.SmartColors

@Composable
fun ProfileRoute(
    overview: StudyProfileOverview,
    review: StudyReviewOverview,
    capabilities: AppCapabilitySnapshot,
    onOpenCapability: () -> Unit,
    onOpenLearningMastery: () -> Unit,
    onOpenDataPrivacy: () -> Unit,
    onOpenReminder: () -> Unit,
    onOpenScheduling: () -> Unit,
    onOpenStorage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    RootPageColumn(
        modifier = modifier.testTag("profile_screen"),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = 8.dp,
            end = 16.dp,
            bottom = 12.dp,
        ),
    ) {
        WorkspacePageHeader("我的", subtitle = "学习记录与应用设置")
        if (overview.hasLearningEvidence) {
            WeeklyLearningSection(overview, review, Modifier.padding(top = 16.dp))
        }
        if (overview.weaknesses.isNotEmpty()) {
            WeaknessSection(overview.weaknesses, Modifier.padding(top = 16.dp))
        }
        LearningMasteryEntry(
            overview = overview,
            onClick = onOpenLearningMastery,
            modifier = Modifier.padding(top = 16.dp),
        )
        SettingsSection(
            capabilities = capabilities,
            onOpenCapability = onOpenCapability,
            onOpenDataPrivacy = onOpenDataPrivacy,
            onOpenReminder = onOpenReminder,
            onOpenScheduling = onOpenScheduling,
            onOpenStorage = onOpenStorage,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

@Composable
private fun LearningMasteryEntry(
    overview: StudyProfileOverview,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(title = "学习掌握")
        Spacer(Modifier.height(8.dp))
        WorkspacePanel(contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
            SettingsRow(
                title = "查看全部知识点",
                subtitle = learningMasterySubtitle(overview),
                icon = Icons.Outlined.AccountTree,
                onClick = onClick,
                modifier = Modifier
                    .testTag("profile_learning_mastery"),
            )
        }
    }
}

@Composable
private fun WeeklyLearningSection(
    overview: StudyProfileOverview,
    review: StudyReviewOverview,
    modifier: Modifier = Modifier,
) {
    WorkspacePanel(modifier = modifier.testTag("profile_weekly_learning")) {
        Column {
            SectionHeader(title = "当前学习情况")
            BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                if (maxWidth < 260.dp || LocalDensity.current.fontScale > 1.3f) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        WeeklyStat("累计作答", overview.recordedAttemptCount.toString(), "次", Modifier.fillMaxWidth())
                        PaperDivider()
                        WeeklyStat("已掌握", overview.newlyMasteredCount.toString(), "个知识点", Modifier.fillMaxWidth())
                        PaperDivider()
                        WeeklyStat("连续复习", review.completionStreakDays.toString(), "天", Modifier.fillMaxWidth())
                    }
                } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    WeeklyStat("累计作答", overview.recordedAttemptCount.toString(), "次", Modifier.weight(1f))
                    StatDivider()
                    WeeklyStat("已掌握", overview.newlyMasteredCount.toString(), "个知识点", Modifier.weight(1f))
                    StatDivider()
                    WeeklyStat("连续复习", review.completionStreakDays.toString(), "天", Modifier.weight(1f))
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .background(JadeSoft, RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ShowChart,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = JadeActive,
                )
                Column(Modifier.padding(start = 10.dp)) {
                    Text(
                        text = profileLearningStatus(overview, review),
                        color = Ink,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

internal fun profileLearningStatus(
    overview: StudyProfileOverview,
    review: StudyReviewOverview,
): String = when {
    !overview.hasLearningEvidence -> "暂无学习记录 · 完成一次复习后开始记录"
    !overview.projectionIsCurrent -> "学习记录已保存 · 正在整理"
    review.completedToday -> "今天的复习已完成 · 已自动记录"
    else -> "已根据最近 ${overview.recordedAttemptCount} 次作答更新学习情况"
}

@Composable
private fun WeeklyStat(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = "$label，$value$unit"
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            color = InkSecondary,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            color = JadeActive,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = unit,
            color = InkSecondary,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun StatDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(48.dp)
            .background(Divider),
    )
}

@Composable
private fun WeaknessSection(
    weaknesses: List<StudyKnowledgeSummary>,
    modifier: Modifier = Modifier,
) {
    WorkspacePanel(modifier = modifier.testTag("profile_weaknesses")) {
        Column {
            SectionHeader(title = "当前薄弱点")
            weaknesses.forEachIndexed { index, weakness ->
                if (index > 0) PaperDivider(Modifier.padding(start = 64.dp))
                WeaknessRow(
                    title = weakness.displayName,
                    // P8（规格 §1.2）：detail 追加粗粒度区间文案（与掌握档同源的唯一文案函数）。
                    detail = "${weakness.status.displayLabel()} · 以独立作答为参考 · " +
                        com.tingyun.smartmistakebook.core.ui.masteryIntervalLabel(
                            weakness.conservativeMasteryScore,
                            weakness.masteryIntervalUpper,
                        ),
                    mastery = weakness.conservativeMasteryScore.toFloat(),
                    subject = weakness.subject,
                    hasEvidence = weakness.masteryIntervalUpper != null,
                    modifier = if (index == 0) Modifier.padding(top = 10.dp) else Modifier,
                )
            }
        }
    }
}

private fun MasteryStatus.displayLabel(): String = when (this) {
    MasteryStatus.UNKNOWN -> "待确认"
    MasteryStatus.LEARNING -> "正在学习"
    MasteryStatus.MASTERED -> "已掌握"
    MasteryStatus.CONFLICTED -> "表现不稳定"
    MasteryStatus.STALE -> "建议复习"
}

@Composable
private fun WeaknessRow(
    title: String,
    detail: String,
    mastery: Float,
    subject: com.tingyun.smartmistakebook.core.model.SubjectKind,
    hasEvidence: Boolean,
    modifier: Modifier = Modifier,
) {
    // Qualitative bands only: precise probabilities require a
    // calibrated model (audit section 6.4), aligned with ReviewRoute.
    val masteryLabel = if (hasEvidence)
        com.tingyun.smartmistakebook.core.ui.masteryBandLabel(mastery.toDouble()) else "证据还少"
    val estimateText = if (hasEvidence) "估计$masteryLabel" else masteryLabel
    val estimateColor = if (hasEvidence) masteryEstimateColor(mastery.toDouble()) else SmartColors.InkMuted
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .padding(vertical = 4.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "$title，$detail，掌握情况估计为$masteryLabel"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SubjectIcon(
            subject = subject,
            contentDescription = "$title 所属学科",
            modifier = Modifier.size(44.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (maxWidth < 200.dp || LocalDensity.current.fontScale > 1.3f) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(title, color = Ink, style = MaterialTheme.typography.titleMedium)
                        Text(estimateText, color = estimateColor, style = MaterialTheme.typography.labelMedium)
                    }
                } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(title, Modifier.weight(1f), color = Ink, style = MaterialTheme.typography.titleMedium)
                    Text(estimateText, color = estimateColor, style = MaterialTheme.typography.labelMedium)
                }
            }
            Text(
                text = detail,
                color = InkSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
            MasteryEstimateBar(value = mastery, color = estimateColor,
                modifier = Modifier.padding(top = 5.dp), hasEvidence = hasEvidence)
        }
    }
}

@Composable
private fun SettingsSection(
    capabilities: AppCapabilitySnapshot,
    onOpenCapability: () -> Unit,
    onOpenDataPrivacy: () -> Unit,
    onOpenReminder: () -> Unit,
    onOpenScheduling: () -> Unit,
    onOpenStorage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val capabilitySubtitle = tutorCapabilitySubtitle(capabilities)
    val privacySubtitle = when (capabilities.networkMode) {
        NetworkMode.STRICT_OFFLINE -> "当前版本未启用联网智能服务"
        NetworkMode.LOCAL_FIRST -> "学习记录保存在本机 · 使用 AI 时才联网"
    }

    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(title = "设置")
        Spacer(Modifier.height(8.dp))
        WorkspacePanel(contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
            Column {
                SettingsRow(
                    title = "模型设置",
                    subtitle = capabilitySubtitle,
                    icon = Icons.Outlined.Memory,
                    iconContainerColor = SmartColors.LearningSurface,
                    iconTint = SmartColors.LearningAccent,
                    onClick = onOpenCapability,
                    modifier = Modifier
                        .testTag("profile_capability_setting"),
                )
                PaperDivider(Modifier.padding(start = 48.dp, end = 8.dp))
                SettingsRow(
                    title = "数据与隐私",
                    subtitle = privacySubtitle,
                    icon = Icons.Outlined.Shield,
                    onClick = onOpenDataPrivacy,
                    modifier = Modifier.testTag("profile_privacy_setting"),
                )
                PaperDivider(Modifier.padding(start = 48.dp, end = 8.dp))
                SettingsRow(
                    title = "提醒与复习偏好",
                    subtitle = "每日提醒时间与通知权限",
                    icon = Icons.Outlined.NotificationsNone,
                    iconContainerColor = SmartColors.PendingSurface,
                    iconTint = SmartColors.PendingAccent,
                    onClick = onOpenReminder,
                    modifier = Modifier.testTag("profile_reminder_setting"),
                )
                PaperDivider(Modifier.padding(start = 48.dp, end = 8.dp))
                SettingsRow(
                    title = "复习安排",
                    subtitle = "记忆目标、复习算法与考试日期",
                    icon = Icons.Outlined.Tune,
                    iconContainerColor = SmartColors.LearningSurface,
                    iconTint = SmartColors.LearningAccent,
                    onClick = onOpenScheduling,
                    modifier = Modifier.testTag("profile_scheduling_setting"),
                )
                PaperDivider(Modifier.padding(start = 48.dp, end = 8.dp))
                SettingsRow(
                    title = "存储与导出",
                    subtitle = "管理题图、备份与导出文件",
                    icon = Icons.Outlined.Backup,
                    iconContainerColor = SmartColors.ReadingSurface,
                    iconTint = SmartColors.ReadingAccent,
                    onClick = onOpenStorage,
                    modifier = Modifier.testTag("profile_storage_setting"),
                )
            }
        }
    }
}

internal fun learningMasterySubtitle(overview: StudyProfileOverview): String {
    val summaries = overview.weaknesses + overview.strengths
    if (summaries.isEmpty()) return "完成作答后，可在这里查看学习情况"
    val subjectCount = summaries.map(StudyKnowledgeSummary::subject)
        .filterNot { it == com.tingyun.smartmistakebook.core.model.SubjectKind.GENERAL }
        .distinct()
        .size
    val subjectText = if (subjectCount > 0) "$subjectCount 科 · " else ""
    return "$subjectText${summaries.size} 个知识点有学习记录"
}

internal fun tutorCapabilitySubtitle(capabilities: AppCapabilitySnapshot): String =
    when {
        !capabilities.tutorTeachingEnabled ||
            capabilities.networkMode == NetworkMode.STRICT_OFFLINE ->
            "讲题暂不可用 · 查看设置"
        capabilities.remoteModelAvailable ->
            if (capabilities.remoteModelImageInputVerified) {
                "图片识别与讲题可用"
            } else {
                "文字讲题可用 · 图片识别暂不可用"
            }
        capabilities.remoteModelAuthenticationFailed ->
            "API Key 可能已失效 · 查看测试结果"
        capabilities.remoteModelCapabilitiesTested ->
            "当前模型暂不兼容 · 查看测试结果"
        capabilities.remoteModelConfigured ->
            "配置已保存 · 请测试模型"
        else ->
            "设置模型服务 · 仅发送本次操作所需的内容"
    }

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconContainerColor: Color = JadeSoft,
    iconTint: Color = SmartColors.JadeDark,
) {
    ClaudeSettingsRow(title, subtitle, icon, onClick, modifier, iconContainerColor, iconTint)
}
