package com.tingyun.smartmistakebook

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import com.tingyun.smartmistakebook.core.ui.ClaudeSecondaryButton as OutlinedButton
import com.tingyun.smartmistakebook.core.ui.ClaudeTextField as OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import com.tingyun.smartmistakebook.core.ui.ClaudeTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.tingyun.smartmistakebook.core.domain.ExamCalendarEntry
import com.tingyun.smartmistakebook.core.domain.OptimalRetention
import com.tingyun.smartmistakebook.core.domain.SchedulingOptions
import com.tingyun.smartmistakebook.core.ui.InkSecondary
import com.tingyun.smartmistakebook.core.ui.PaperDivider
import com.tingyun.smartmistakebook.core.ui.RootPageColumn
import com.tingyun.smartmistakebook.core.ui.WorkspacePanel
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * `SchedulingOptions.init` 支持的记忆目标 band（`StudyExperienceRepository.kt` 同值）。
 */
private const val RETENTION_MIN = 0.7
private const val RETENTION_MAX = 0.97

/**
 * 把滑杆的 Float 读数落成 `SchedulingOptions` 可接受的 Double。
 *
 * 消灭的失败：滑杆端点经 Float→Double 后**越出** 0.7..0.97（`0.97f.toDouble()` =
 * 0.9700000286102295、`0.7f.toDouble()` = 0.6999999880790710），`SchedulingOptions` 的
 * `init { require(...) }` 直接抛 IllegalArgumentException。批次 44 soak 在复习安排页点滑杆
 * 端点（证据帧 `docs/research/evidence/2026-11-05-batch-44/f-domain/soak-crash-logcat.txt`）
 * 因此 FATAL EXCEPTION 并重启进程。落库前夹回 band，端点与越界读数都不再崩溃。
 *
 * 批次 49 P2-12 复核：本函数在 `ea4348f9`（界面收口重写）后从文件中消失，但
 * `SchedulingRetentionSliderValueTest` 仍在引用它——修复被静默回退。这里按 587098f1 原样恢复。
 */
internal fun retentionFromSlider(value: Float): Double =
    value.toDouble().coerceIn(RETENTION_MIN, RETENTION_MAX)

/**
 * 复习安排设置（spec mastery-scheduling §2.17/§8-B4）：记忆目标、考试日历。
 * 审计 2026-09-09 发现这三个写入路径此前全仓无调用方——`setOptions` /
 * `declareExam` / `removeExam` 只有接口与实现，学生改不了任何排程参数。
 * KF-11（2026-10-01）：FSRS 开关已随 kill-switch 退场（FSRS 唯一化）。
 *
 * 排程参数在仓库构造时读取，改动在下次启动生效（spec §2.20：单次会话内投影模型稳定），
 * 因此本页只负责落库，不假装立即生效。
 */
@Composable
internal fun SchedulingSettingsScreen(
    options: SchedulingOptions,
    exams: List<ExamCalendarEntry>,
    onSetOptions: (SchedulingOptions) -> Unit,
    onAddExam: (ExamCalendarEntry) -> Unit,
    onRemoveExam: (String) -> Unit,
    onBack: () -> Unit,
    retentionHint: OptimalRetention.Recommendation? = null,
    settingsMessage: String? = null,
) {
    RootPageColumn(modifier = Modifier.testTag("root_scheduling_settings")) {
        SecondaryHeader(title = "复习安排", onBack = onBack)
        Text(
            text = "每日时长用于新计划，不影响已开始或已导出的计划。记忆目标下次启动时生效。",
            color = InkSecondary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp),
        )
        Spacer(Modifier.height(12.dp))

        WorkspacePanel {
            var minutes by rememberSaveable(options.dailyReviewMinutes) { mutableStateOf(options.dailyReviewMinutes.toString()) }
            SettingsSectionTitle("每日预计复习时长")
            Text("${options.dailyReviewMinutes} 分钟 / 天", style = MaterialTheme.typography.headlineSmall,
                color = com.tingyun.smartmistakebook.core.ui.SmartColors.JadeDark)
            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
            @Composable fun Presets() {
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
                    listOf(10, 20, 30, 45).forEach { value ->
                        com.tingyun.smartmistakebook.core.ui.OutlineActionChip("$value 分钟", { minutes = value.toString() },
                            selected = minutes.toIntOrNull() == value)
                    }
                }
            }
            Presets()
            OutlinedTextField(value = minutes, onValueChange = { value -> minutes = value.filter(Char::isDigit).take(3) },
                label = { Text("自定义分钟数（5–120）") }, singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().testTag("scheduling_daily_minutes"))
            com.tingyun.smartmistakebook.core.ui.PrimaryActionButton("保存每日时长",
                onClick = { minutes.toIntOrNull()?.takeIf { it in 5..120 }?.let { onSetOptions(options.copy(dailyReviewMinutes = it)) } },
                enabled = minutes.toIntOrNull()?.let { it in 5..120 && it != options.dailyReviewMinutes } == true,
                modifier = Modifier.fillMaxWidth().testTag("scheduling_save_daily_minutes"))
            Text("系统按题目难度估算题量，实际用时可能不同；考试期会适当增加复习安排。",
                color = InkSecondary, style = MaterialTheme.typography.bodySmall)
            settingsMessage?.let { Text(it, color = InkSecondary, style = MaterialTheme.typography.bodySmall) }
        }
        Spacer(Modifier.height(16.dp))
        WorkspacePanel {
            Column {
                SettingsSectionTitle(title = "记忆目标")
                Text(
                    text = "${(options.desiredRetention * 100).roundToInt()}%",
                    color = com.tingyun.smartmistakebook.core.ui.SmartColors.LearningAccent,
                    style = MaterialTheme.typography.displaySmall,
                    modifier = Modifier.padding(top = 12.dp).testTag("scheduling_retention_label"),
                )
                Text("目标回忆成功率", style = MaterialTheme.typography.bodySmall, color = InkSecondary)
                Slider(
                    value = options.desiredRetention.toFloat(),
                    onValueChange = { value ->
                        onSetOptions(options.copy(desiredRetention = retentionFromSlider(value)))
                    },
                    valueRange = 0.7f..0.97f,
                    steps = 26,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("scheduling_retention_slider"),
                )
                // CMRR-style recommendation (研究 2026-09-09 §5): a reference value from
                // the learner's own memory states, never applied automatically.
                retentionHint?.let { hint ->
                    Text(
                        text = "参考值 ${(hint.desiredRetention * 100).roundToInt()}%（实验性，" +
                            "由你自己的记忆状态模拟得出，仅供参考）",
                        color = InkSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag("scheduling_retention_hint"),
                    )
                }

            }
        }
        Spacer(Modifier.height(16.dp))

        WorkspacePanel {
            Column {
                SettingsSectionTitle(title = "考试日历")
                Text(
                    text = "添加考试日期后，考前两周会优先安排相关科目的复习。",
                    color = InkSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                ExamCalendarEditor(
                    exams = exams,
                    onAddExam = onAddExam,
                    onRemoveExam = onRemoveExam,
                )
            }
        }
    }
}

@Composable
private fun ExamCalendarEditor(
    exams: List<ExamCalendarEntry>,
    onAddExam: (ExamCalendarEntry) -> Unit,
    onRemoveExam: (String) -> Unit,
) {
    var subject by rememberSaveable { mutableStateOf("") }
    var title by rememberSaveable { mutableStateOf("") }
    var daysFromNow by rememberSaveable { mutableStateOf("30") }

    exams.forEach { exam ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(exam.title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "${exam.subject} · ${LocalDate.ofEpochDay(exam.examEpochDay)}",
                    color = InkSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(
                onClick = { onRemoveExam(exam.entryId) },
                modifier = Modifier.testTag("exam_remove_${exam.entryId}"),
            ) {
                Text("删除")
            }
        }
    }

    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = subject,
        onValueChange = { subject = it },
        label = { Text("科目（如 数学）") },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("exam_subject_field"),
    )
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = title,
        onValueChange = { title = it },
        label = { Text("考试名称（如 期中考试）") },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("exam_title_field"),
    )
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = daysFromNow,
        onValueChange = { daysFromNow = it.filter(Char::isDigit).take(4) },
        label = { Text("还有几天（如 30）") },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("exam_days_field"),
    )
    Spacer(Modifier.height(8.dp))
    val parsedDays = daysFromNow.toLongOrNull()
    val canAdd = subject.isNotBlank() && title.isNotBlank() && parsedDays != null && parsedDays >= 0
    OutlinedButton(
        onClick = {
            val days = parsedDays ?: return@OutlinedButton
            onAddExam(
                ExamCalendarEntry(
                    entryId = "exam-${subject.trim()}-${System.currentTimeMillis()}",
                    subject = subject.trim(),
                    examEpochDay = LocalDate.now(ZoneId.systemDefault()).toEpochDay() + days,
                    title = title.trim(),
                ),
            )
            subject = ""
            title = ""
            daysFromNow = "30"
        },
        enabled = canAdd,
        modifier = Modifier.testTag("exam_add_button"),
    ) {
        Text("添加考试")
    }
}
