package com.tingyun.smartmistakebook.feature.tutor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tingyun.smartmistakebook.core.model.MistakePdfBlockKind
import com.tingyun.smartmistakebook.core.model.MistakePdfLayout
import com.tingyun.smartmistakebook.core.model.decodeTutorTurnToolTrace
import com.tingyun.smartmistakebook.core.domain.StudyCatalogEntry
import com.tingyun.smartmistakebook.core.domain.TutorMessage
import com.tingyun.smartmistakebook.core.domain.TutorMessageRole
import com.tingyun.smartmistakebook.core.model.TutorToolName
import com.tingyun.smartmistakebook.core.ui.ErrorWarm
import com.tingyun.smartmistakebook.core.ui.Ink
import com.tingyun.smartmistakebook.core.ui.InkSecondary
import com.tingyun.smartmistakebook.core.ui.Outline
import com.tingyun.smartmistakebook.core.ui.OutlineActionChip
import com.tingyun.smartmistakebook.core.ui.PrimaryActionButton
import kotlin.math.roundToInt

/**
 * 导出 sheet 的状态（4B B3-3）：确认卡点了"去导出"之后，学生在这里改版式、勾题。
 *
 * 它放在 ViewModel 的 UiState 里（而不是 sheet 自己的 `remember`）：旋转屏幕不能把学生改到
 * 一半的版式和勾选丢掉。**有意不做进程死亡持久化**——要自动恢复一张已 ACCEPTED 卡的 sheet，
 * 需要额外的"应打开"持久标记与配套清理，不在本批范围；进程死亡后学生重新发起导出即可
 * （卡的终态与留痕都在库里，不是悬死卡）。
 */
internal data class TutorExportSheetState(
    /** 模型提议的版式（已在准入时过 validate()）；学生在此之上改。 */
    val layout: MistakePdfLayout,
    /** 本轮本地留痕（NOTEBOOK_READ）检索到的条目 id，按检索顺序。 */
    val candidateEntryIds: List<String>,
    /** 已勾选要导出的条目 id。 */
    val selectedEntryIds: Set<String>,
)

/**
 * 本轮留痕里 `NOTEBOOK_READ` 检索到的条目 id —— 导出 sheet 的**唯一**候选来源。
 *
 * 它消灭的失败：若候选来自模型给的 id，就是把"选哪道题"退化成"编哪道题"。模型"选出"的过程
 * 本来就是它调用 `NOTEBOOK_READ` 检索并展示的过程；这里读的是那一轮助手消息行上的
 * `tool_trace_json`（本地留痕，不进模型可见文本），只认本地事实。
 */
internal fun exportCandidateEntryIds(
    messages: List<TutorMessage>,
    logicalOperationId: String,
): List<String> = messages.asSequence()
    .filter { message ->
        message.role == TutorMessageRole.ASSISTANT &&
            message.logicalOperationId == logicalOperationId
    }
    .mapNotNull { message -> decodeTutorTurnToolTrace(message.toolTraceJson) }
    .flatMap { trace ->
        trace.entries.asSequence().filter { entry -> entry.tool == TutorToolName.NOTEBOOK_READ }
    }
    .flatMap { entry -> entry.problemEntryIds.asSequence() }
    .distinct()
    .toList()

/** 候选条目在目录里的可读形状（列表里展示的题面摘要）。 */
internal data class TutorExportCandidate(
    val entryId: String,
    val title: String,
    val subject: String,
)

/** 候选解析结果：可展示的条目 + 已不在错题本里的条数（如实说明，不静默吞掉）。 */
internal data class TutorExportCandidateResolution(
    val candidates: List<TutorExportCandidate>,
    val missingCount: Int,
)

/** 留痕 id → 目录条目（顺序按留痕；查不到的记 missingCount）。 */
internal fun resolveExportCandidates(
    candidateEntryIds: List<String>,
    entries: List<StudyCatalogEntry>,
): TutorExportCandidateResolution {
    val byId = entries.associateBy(StudyCatalogEntry::entryId)
    val candidates = candidateEntryIds.mapNotNull { entryId ->
        byId[entryId]?.let { entry ->
            TutorExportCandidate(
                entryId = entry.entryId,
                title = entry.title,
                subject = entry.subject,
            )
        }
    }
    return TutorExportCandidateResolution(
        candidates = candidates,
        missingCount = candidateEntryIds.size - candidates.size,
    )
}

/**
 * 版式的一句话摘要（确认卡与 sheet 共用；如"练习卷 · 双栏 · 字号大 · 含答案 · 边距 60pt ·
 * 图 80% · 块序 段落→选择题"）。
 *
 * 非默认的 marginPt / imageScale / blockOrder 也进摘要：模型提议了"图小一点""边距宽一点"
 * 这类要求时，学生在确认卡上必须看得到——只报模板/栏数/字号会让提议悄悄消失。
 */
internal fun exportLayoutSummary(layout: MistakePdfLayout): String {
    val template = when (layout.templateId) {
        MistakePdfLayout.TEMPLATE_PRACTICE_SHEET -> "练习卷"
        MistakePdfLayout.TEMPLATE_WITH_ANSWERS -> "答案卷"
        else -> "紧凑版"
    }
    val parts = buildList {
        add(template)
        add(if (layout.columnCount == 2) "双栏" else "单栏")
        add(
            when (layout.fontScale) {
                1 -> "字号小"
                3 -> "字号大"
                else -> "字号标准"
            },
        )
        add(
            if (layout.includeAnswer || layout.templateId == MistakePdfLayout.TEMPLATE_WITH_ANSWERS) {
                "含答案"
            } else {
                "不含答案"
            },
        )
        if (layout.includeSolution) add("含解析（暂不可用）")
        if (layout.includeNote) add("含备注")
        if (layout.marginPt != MistakePdfLayout.DEFAULT_MARGIN_PT) {
            add("边距 ${layout.marginPt}pt")
        }
        if (layout.imageScale != MistakePdfLayout.DEFAULT_IMAGE_SCALE) {
            add("图 ${(layout.imageScale * 100).roundToInt()}%")
        }
        if (layout.blockOrder.isNotEmpty()) {
            add(
                "块序 " + layout.blockOrder.joinToString(separator = "→") { token ->
                    exportBlockKindLabel(token)
                },
            )
        }
    }
    return parts.joinToString(separator = " · ")
}

/** 块顺序 token → 学生看得懂的短名（未知 token 原样显示，不猜）。 */
private fun exportBlockKindLabel(token: String): String = when (MistakePdfBlockKind.fromToken(token)) {
    MistakePdfBlockKind.PARAGRAPH -> "段落"
    MistakePdfBlockKind.SECTION_HEADING -> "小标题"
    MistakePdfBlockKind.FORMULA -> "公式"
    MistakePdfBlockKind.CHOICE_GROUP -> "选择题"
    MistakePdfBlockKind.FIGURE -> "图形"
    null -> token
}

/** 请求了但会被 fail-closed 跳过的版式功能 → sheet 上的如实提示；没有时 null。 */
internal fun exportSkipNotice(layout: MistakePdfLayout): String? {
    val features = layout.unsupportedRequestedFeatures()
    if (features.isEmpty()) return null
    return features.joinToString(separator = "、") { it.studentLabel } +
        "暂不可用：本次导出会跳过它，不会留空占位。"
}

/**
 * 勾选/取消一道题时的状态推进（纯函数，VM 只转发）。
 *
 * **未知 id（「从错题本再选」的条目）在勾选时并入候选集合**：picker 只能给目录内的条目，
 * `resolveExportCandidates` 解析得出标题——不并入的话，选中的 id 不在候选里，渲染与提交都
 * 按"已勾选 ∩ 候选"过滤，学生点完看不到行、计数不变、确认按钮永远禁用（验收不达的静默死路）。
 * 取消勾选只移出选中集合，候选行保留（可以再勾回来）。
 */
internal fun tutorExportSheetWithCandidateToggled(
    sheet: TutorExportSheetState,
    entryId: String,
    selected: Boolean,
): TutorExportSheetState {
    if (entryId.isBlank()) return sheet
    val nextCandidates = if (selected && entryId !in sheet.candidateEntryIds) {
        sheet.candidateEntryIds + entryId
    } else {
        sheet.candidateEntryIds
    }
    val nextSelected = if (selected) {
        sheet.selectedEntryIds + entryId
    } else {
        sheet.selectedEntryIds - entryId
    }
    return sheet.copy(candidateEntryIds = nextCandidates, selectedEntryIds = nextSelected)
}

/**
 * 当前**可提交**的条目：已勾选 ∩ 目录里解析得出的候选，顺序按候选列表。
 *
 * 界面渲染的勾选态与「开始导出」提交的集合读的是同一个函数——"看到勾上的"和"提交的"不会漂。
 */
internal fun TutorExportSheetState.submittableEntryIds(
    resolution: TutorExportCandidateResolution,
): List<String> = resolution.candidates
    .map { candidate -> candidate.entryId }
    .filter { entryId -> entryId in selectedEntryIds }

/**
 * 导出 sheet（4B B3-3）：确认卡之后学生真正定稿的地方。
 *
 * 三件事同屏：① 版式参数表单（预填模型提议值，学生可改）；② 候选题（来自本轮本地留痕）
 * 默认列出、学生勾选，也可以从错题本再选（复用 `TutorMistakePickerDialog`，不另造一套检索）；
 * ③ 确认后交给后台导出管线（装配处入队 + 导航到「导出成果」）。
 */
@Composable
internal fun TutorExportSheet(
    state: TutorExportSheetState,
    catalogEntries: List<StudyCatalogEntry>,
    onLayoutChange: (MistakePdfLayout) -> Unit,
    onToggleCandidate: (entryId: String, selected: Boolean) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (MistakePdfLayout, List<String>) -> Unit,
) {
    var pickerOpen by remember { mutableStateOf(false) }
    val resolution = remember(state.candidateEntryIds, catalogEntries) {
        resolveExportCandidates(state.candidateEntryIds, catalogEntries)
    }
    // 渲染的勾选态与提交集合同源：已勾选 ∩ 可解析候选（顺序按候选）。
    val selectedIds = state.submittableEntryIds(resolution)
    val skipNotice = exportSkipNotice(state.layout)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 640.dp)
                    .testTag("tutor_export_sheet"),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
                border = BorderStroke(1.dp, Outline),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "导出练习卷",
                        color = Ink,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "版式：${exportLayoutSummary(state.layout)}",
                        modifier = Modifier.testTag("tutor_export_sheet_summary"),
                        color = InkSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )

                    // ---- ① 版式参数表单（预填模型提议值） ----
                    SheetSectionLabel("模板")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            MistakePdfLayout.TEMPLATE_PRACTICE_SHEET to "练习卷",
                            MistakePdfLayout.TEMPLATE_COMPACT to "紧凑版",
                            MistakePdfLayout.TEMPLATE_WITH_ANSWERS to "答案卷",
                        ).forEach { (templateId, label) ->
                            OutlineActionChip(
                                text = label,
                                onClick = {
                                    onLayoutChange(state.layout.copy(templateId = templateId))
                                },
                                selected = state.layout.templateId == templateId,
                                modifier = Modifier.testTag("tutor_export_sheet_template_$templateId"),
                            )
                        }
                    }

                    SheetSectionLabel("栏数与字号")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1 to "单栏", 2 to "双栏").forEach { (count, label) ->
                            OutlineActionChip(
                                text = label,
                                onClick = { onLayoutChange(state.layout.copy(columnCount = count)) },
                                selected = state.layout.columnCount == count,
                                modifier = Modifier.testTag("tutor_export_sheet_columns_$count"),
                            )
                        }
                        listOf(1 to "字号小", 2 to "字号标准", 3 to "字号大").forEach { (scale, label) ->
                            OutlineActionChip(
                                text = label,
                                onClick = { onLayoutChange(state.layout.copy(fontScale = scale)) },
                                selected = state.layout.fontScale == scale,
                                modifier = Modifier.testTag("tutor_export_sheet_font_$scale"),
                            )
                        }
                    }

                    SheetSectionLabel("页边距与图形缩放")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StepperChip(
                            label = "页边距 ${state.layout.marginPt}pt",
                            canDecrease = state.layout.marginPt > MistakePdfLayout.MIN_MARGIN_PT,
                            canIncrease = state.layout.marginPt < MistakePdfLayout.MAX_MARGIN_PT,
                            onDecrease = {
                                onLayoutChange(
                                    state.layout.copy(
                                        marginPt = (state.layout.marginPt - 12)
                                            .coerceAtLeast(MistakePdfLayout.MIN_MARGIN_PT),
                                    ),
                                )
                            },
                            onIncrease = {
                                onLayoutChange(
                                    state.layout.copy(
                                        marginPt = (state.layout.marginPt + 12)
                                            .coerceAtMost(MistakePdfLayout.MAX_MARGIN_PT),
                                    ),
                                )
                            },
                            testTagPrefix = "tutor_export_sheet_margin",
                        )
                        StepperChip(
                            label = "图形 ${(state.layout.imageScale * 100).toInt()}%",
                            canDecrease = state.layout.imageScale > MistakePdfLayout.MIN_IMAGE_SCALE,
                            canIncrease = state.layout.imageScale < MistakePdfLayout.MAX_IMAGE_SCALE,
                            onDecrease = {
                                onLayoutChange(
                                    state.layout.copy(
                                        imageScale = (state.layout.imageScale - 0.1f)
                                            .coerceAtLeast(MistakePdfLayout.MIN_IMAGE_SCALE),
                                    ),
                                )
                            },
                            onIncrease = {
                                onLayoutChange(
                                    state.layout.copy(
                                        imageScale = (state.layout.imageScale + 0.1f)
                                            .coerceAtMost(MistakePdfLayout.MAX_IMAGE_SCALE),
                                    ),
                                )
                            },
                            testTagPrefix = "tutor_export_sheet_image_scale",
                        )
                    }

                    SheetSectionLabel("附加内容")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlineActionChip(
                            text = if (state.layout.includeAnswer) "含答案区" else "不含答案区",
                            onClick = {
                                onLayoutChange(
                                    state.layout.copy(includeAnswer = !state.layout.includeAnswer),
                                )
                            },
                            selected = state.layout.includeAnswer,
                            modifier = Modifier.testTag("tutor_export_sheet_include_answer"),
                        )
                        OutlineActionChip(
                            text = if (state.layout.includeSolution) "含解析区" else "不含解析区",
                            onClick = {
                                onLayoutChange(
                                    state.layout.copy(includeSolution = !state.layout.includeSolution),
                                )
                            },
                            selected = state.layout.includeSolution,
                            modifier = Modifier.testTag("tutor_export_sheet_include_solution"),
                        )
                        OutlineActionChip(
                            text = if (state.layout.includeNote) "含备注区" else "不含备注区",
                            onClick = {
                                onLayoutChange(
                                    state.layout.copy(includeNote = !state.layout.includeNote),
                                )
                            },
                            selected = state.layout.includeNote,
                            modifier = Modifier.testTag("tutor_export_sheet_include_note"),
                        )
                    }
                    skipNotice?.let { notice ->
                        Text(
                            text = notice,
                            modifier = Modifier.testTag("tutor_export_sheet_skip_notice"),
                            color = ErrorWarm,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    // ---- ② 候选题目（本轮本地留痕） ----
                    SheetSectionLabel("本轮查到的题（勾选要导出的）")
                    if (resolution.candidates.isEmpty()) {
                        Text(
                            text = "这一轮没有从错题本里查到题。可以从错题本再选一道。",
                            modifier = Modifier.testTag("tutor_export_sheet_candidates_empty"),
                            color = InkSecondary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else {
                        resolution.candidates.forEach { candidate ->
                            CandidateRow(
                                candidate = candidate,
                                checked = candidate.entryId in selectedIds,
                                onCheckedChange = { checked ->
                                    onToggleCandidate(candidate.entryId, checked)
                                },
                            )
                        }
                    }
                    if (resolution.missingCount > 0) {
                        Text(
                            text = "另有 ${resolution.missingCount} 道已不在错题本里，不会导出。",
                            modifier = Modifier.testTag("tutor_export_sheet_missing_note"),
                            color = InkSecondary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    OutlineActionChip(
                        text = "从错题本再选",
                        onClick = { pickerOpen = true },
                        modifier = Modifier.testTag("tutor_export_sheet_pick_more"),
                    )

                    // ---- ③ 确认 ----
                    Spacer(Modifier.height(2.dp))
                    PrimaryActionButton(
                        text = "开始导出（已勾 ${selectedIds.size} 道）",
                        onClick = { onConfirm(state.layout, selectedIds) },
                        enabled = selectedIds.isNotEmpty(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("tutor_export_sheet_confirm"),
                    )
                    if (selectedIds.isEmpty()) {
                        Text(
                            text = "至少勾一道题才能导出。",
                            modifier = Modifier.testTag("tutor_export_sheet_confirm_hint"),
                            color = InkSecondary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    OutlineActionChip(
                        text = "先不用",
                        onClick = onDismiss,
                        modifier = Modifier.testTag("tutor_export_sheet_dismiss"),
                    )
                }
            }
        }
    }

    if (pickerOpen) {
        TutorMistakePickerDialog(
            entries = catalogEntries,
            onPick = { entry ->
                pickerOpen = false
                onToggleCandidate(entry.entryId, true)
            },
            onDismiss = { pickerOpen = false },
            testTagPrefix = "tutor_export_sheet_picker",
        )
    }
}

@Composable
private fun SheetSectionLabel(text: String) {
    Text(
        text = text,
        color = Ink,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
private fun StepperChip(
    label: String,
    canDecrease: Boolean,
    canIncrease: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    testTagPrefix: String,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlineActionChip(
            text = "−",
            onClick = onDecrease,
            enabled = canDecrease,
            modifier = Modifier.testTag("${testTagPrefix}_decrease"),
        )
        Text(
            text = label,
            modifier = Modifier.testTag("${testTagPrefix}_label"),
            color = Ink,
            style = MaterialTheme.typography.bodySmall,
        )
        OutlineActionChip(
            text = "+",
            onClick = onIncrease,
            enabled = canIncrease,
            modifier = Modifier.testTag("${testTagPrefix}_increase"),
        )
    }
}

@Composable
private fun CandidateRow(
    candidate: TutorExportCandidate,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("tutor_export_sheet_candidate_${candidate.entryId}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag("tutor_export_sheet_candidate_check_${candidate.entryId}"),
        )
        Column(modifier = Modifier.padding(start = 4.dp)) {
            Text(
                text = candidate.title,
                color = Ink,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = candidate.subject,
                color = InkSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
