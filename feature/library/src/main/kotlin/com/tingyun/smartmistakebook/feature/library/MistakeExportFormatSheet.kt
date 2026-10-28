package com.tingyun.smartmistakebook.feature.library

import com.tingyun.smartmistakebook.core.ui.claudeDismissOnEscape
import com.tingyun.smartmistakebook.core.ui.claudeModalEntrance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tingyun.smartmistakebook.core.model.MistakeExportFormat
import com.tingyun.smartmistakebook.core.ui.Ink
import com.tingyun.smartmistakebook.core.ui.InkSecondary
import com.tingyun.smartmistakebook.core.ui.Outline
import com.tingyun.smartmistakebook.core.ui.OutlineActionChip
import com.tingyun.smartmistakebook.core.ui.PrimaryActionButton

/**
 * 原生导出（错题详情的单题、错题本的「导出当前筛选」）在**入队前**选择交付格式
 * （PRD-04 / T4）。
 *
 * 它消灭的失败：原生入口改前只有 PDF，学生想要 Word 只能绕到讲题页的导出 sheet；而若把格式
 * 只做成界面上的一个标签、入队时仍按默认 PDF，学生「看到选了 Word、拿到 PDF」——所以确认
 * 回调交出的 [MistakeExportFormat] 必须原样进 `MistakeExportJobRequest`（`format` 是
 * WorkManager 输入、记录文件名与成果入口交付动作的共同身份）。
 *
 * **默认 PDF**：原生入口的既有行为不变；[rememberSaveable] 存的是稳定 [MistakeExportFormat.token]，
 * 缺失/损坏/未知代号都回落到 PDF（[mistakeExportFormatSelection]）。装配处应在打开时**条件
 * 组合**本 sheet（`pendingTarget?.let { ... }`），离开组合即丢弃上次的选择——每次打开都从
 * PDF 开始，不会把上一次选的 Word 悄悄带到下一次导出。
 *
 * **可取消**：[onDismiss] 同时挂在背景/返回手势与「取消」上；取消不触发 [onConfirm]，
 * 装配处不得导航到「导出成果」或入队任何格式。
 *
 * **不改版式**：这里只选格式，请求继续用 `MistakePdfLayout.DEFAULT`（默认不含答案区）；
 * 版式与内容能力仍由 core:export 的既有渲染器决定，本 sheet 不重写、不预览。
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun MistakeExportFormatSheet(
    onDismiss: () -> Unit,
    onConfirm: (MistakeExportFormat) -> Unit,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
) {
    var selectedToken by rememberSaveable { mutableStateOf(MistakeExportFormat.PDF.token) }
    val selected = mistakeExportFormatSelection(selectedToken)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().imePadding().padding(top = 24.dp)
                .claudeDismissOnEscape(onDismiss = onDismiss),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Surface(
                modifier = modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth()
                    .heightIn(max = maxHeight)
                    .claudeModalEntrance()
                    .testTag("mistake_export_format_sheet"),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                shadowElevation = 12.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "选择导出格式",
                        modifier = Modifier.testTag("mistake_export_format_title"),
                        color = Ink,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    subtitle?.let { text ->
                        Text(
                            text = text,
                            modifier = Modifier.testTag("mistake_export_format_subtitle"),
                            color = InkSecondary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    Text(
                        text = "文件格式",
                        color = Ink,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        MistakeExportFormat.entries.forEach { format ->
                            val isSelected = format == selected
                            OutlineActionChip(
                                text = format.studentLabel,
                                onClick = { selectedToken = format.token },
                                selected = isSelected,
                                contentDescription = mistakeExportFormatOptionDescription(
                                    format = format,
                                    selected = isSelected,
                                ),
                                modifier = Modifier.testTag(
                                    "mistake_export_format_option_${format.token}",
                                ),
                            )
                        }
                    }

                    if (selected == MistakeExportFormat.DOCX) {
                        Text(
                            text = mistakeExportFormatDocxNotice,
                            modifier = Modifier.testTag("mistake_export_format_docx_notice"),
                            color = InkSecondary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    // 默认不含答案区：格式选择不改变导出内容与版式（与既有 DEFAULT 一致）。
                    Text(
                        text = "导出内容默认不含答案区；这里只选择文件格式，不改动版式。",
                        modifier = Modifier.testTag("mistake_export_format_answer_note"),
                        color = InkSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )

                    Spacer(Modifier.height(2.dp))
                    PrimaryActionButton(
                        text = "开始导出",
                        onClick = { onConfirm(selected) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("mistake_export_format_confirm"),
                    )
                    OutlineActionChip(
                        text = "取消",
                        onClick = onDismiss,
                        modifier = Modifier.testTag("mistake_export_format_cancel"),
                    )
                }
            }
        }
    }
}

/**
 * 保存状态里的格式代号 → 格式。
 *
 * 缺省/空/未知一律 PDF：旧版本保存状态里没有这个键，损坏状态里可能是别的字符串——两者都
 * 不能 crash，也不能把默认悄悄变成 Word（与 worker 侧「缺格式键 = PDF」同一兼容口径）。
 */
internal fun mistakeExportFormatSelection(rawToken: String?): MistakeExportFormat =
    MistakeExportFormat.fromToken(rawToken) ?: MistakeExportFormat.PDF

/** 格式 chip 的可访问描述：选中的那个明确报"已选"，测试与读屏都靠它分辨选中态。 */
internal fun mistakeExportFormatOptionDescription(
    format: MistakeExportFormat,
    selected: Boolean,
): String = if (selected) "${format.studentLabel}（已选）" else format.studentLabel

/**
 * Word 交付的如实说明（与讲题页导出 sheet 同一句话）：只说当前实现真的做到的事，
 * 不承诺 Word 与 PDF 逐项一致。
 */
internal const val mistakeExportFormatDocxNotice: String =
    "Word 版式：图形以文字说明呈现，暂不含作答空白区；打印请用 Word 打开后进行。"
