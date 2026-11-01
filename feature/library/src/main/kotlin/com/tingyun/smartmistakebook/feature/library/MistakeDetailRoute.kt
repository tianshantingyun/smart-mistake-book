package com.tingyun.smartmistakebook.feature.library

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.PictureAsPdf
import com.tingyun.smartmistakebook.core.ui.ClaudeAlertDialog as AlertDialog
import com.tingyun.smartmistakebook.core.ui.ClaudeButton as Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import com.tingyun.smartmistakebook.core.ui.ClaudeIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import com.tingyun.smartmistakebook.core.ui.ClaudeSecondaryButton as OutlinedButton
import com.tingyun.smartmistakebook.core.ui.ClaudeTextField as OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.tingyun.smartmistakebook.core.ui.ClaudeTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tingyun.smartmistakebook.core.domain.ExplicitRedrawCommand
import com.tingyun.smartmistakebook.core.domain.ExplicitRedrawFailureReason
import com.tingyun.smartmistakebook.core.domain.ExplicitRedrawPhase
import com.tingyun.smartmistakebook.core.domain.ExplicitRedrawRequestResult
import com.tingyun.smartmistakebook.core.domain.ExplicitRedrawState
import com.tingyun.smartmistakebook.core.domain.MistakeDetail
import com.tingyun.smartmistakebook.core.domain.MistakeDetailIdentity
import com.tingyun.smartmistakebook.core.domain.MistakeDetailRepository
import com.tingyun.smartmistakebook.core.domain.MistakeDetailState
import com.tingyun.smartmistakebook.core.domain.MistakeOrganizationRepository
import com.tingyun.smartmistakebook.core.domain.MistakeRevisionKey
import com.tingyun.smartmistakebook.core.domain.MistakeRevisionSummary
import com.tingyun.smartmistakebook.core.domain.ModelTaskRepository
import com.tingyun.smartmistakebook.core.domain.KnowledgeBaseAvailability
import com.tingyun.smartmistakebook.core.domain.MistakeSourceAsset
import com.tingyun.smartmistakebook.core.domain.MistakeSourceLocation
import com.tingyun.smartmistakebook.core.domain.MistakeSourceSet
import com.tingyun.smartmistakebook.core.domain.StudyProfileOverview
import com.tingyun.smartmistakebook.core.domain.StudyCatalogEntry
import com.tingyun.smartmistakebook.core.model.VisualRole
import com.tingyun.smartmistakebook.core.ui.BoundedLocalImage
import com.tingyun.smartmistakebook.core.ui.LocalImageViewer
import com.tingyun.smartmistakebook.core.ui.InlineActionButton
import com.tingyun.smartmistakebook.core.ui.Ink
import com.tingyun.smartmistakebook.core.ui.InkSecondary
import com.tingyun.smartmistakebook.core.ui.JadeActive
import com.tingyun.smartmistakebook.core.ui.JadeSoft
import com.tingyun.smartmistakebook.core.ui.LocalImageLoadState
import com.tingyun.smartmistakebook.core.ui.Outline
import com.tingyun.smartmistakebook.core.ui.OutlineActionChip
import com.tingyun.smartmistakebook.core.ui.PaperDivider
import com.tingyun.smartmistakebook.core.ui.PrimaryActionButton
import com.tingyun.smartmistakebook.core.ui.RootPageColumn
import com.tingyun.smartmistakebook.core.ui.SafeMarkdownText
import kotlinx.coroutines.launch
import com.tingyun.smartmistakebook.core.ui.SectionHeader
import com.tingyun.smartmistakebook.core.ui.StructuredContentRenderer
import com.tingyun.smartmistakebook.core.ui.studentSubjectLabel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MistakeDetailRoute(
    errorBookEntryId: String,
    repository: MistakeDetailRepository,
    organizationRepository: MistakeOrganizationRepository,
    modelTasks: ModelTaskRepository,
    profile: StudyProfileOverview,
    catalogEntries: List<StudyCatalogEntry>,
    onBack: () -> Unit,
    onExport: (MistakeRevisionKey) -> Unit,
    onTutor: (MistakeRevisionKey) -> Unit,
    onOpenRelatedMistake: (String) -> Unit,
    onOpenModelSettings: () -> Unit,
    /**
     * 6B-2 原生编辑入口：当前版本进入卷面编辑（只改导出文档副本，不改这一版题面）。
     * 历史版本只读，不显示该入口。null = 装配处未接线，页面不摆一个点了没反应的按钮。
     */
    onEditPaper: ((MistakeRevisionKey) -> Unit)? = null,
    /** 知识能力就绪位（D-Q3）：未就绪时智能整理如实说"准备中"，不发起、也不误报模型未配置。 */
    knowledgeBaseAvailability: KnowledgeBaseAvailability = KnowledgeBaseAvailability.Ready,
    modifier: Modifier = Modifier,
) {
    var selectedRevisionId by rememberSaveable(errorBookEntryId) {
        mutableStateOf<String?>(null)
    }
    var editing by rememberSaveable(errorBookEntryId) { mutableStateOf(false) }
    var savedRevisionNotice by rememberSaveable(errorBookEntryId) {
        mutableStateOf<String?>(null)
    }
    var refreshToken by rememberSaveable(errorBookEntryId) { mutableIntStateOf(0) }
    if (editing) {
        MistakeRevisionEditRoute(
            entryId = errorBookEntryId,
            repository = repository,
            onBack = {
                editing = false
                // A save may have succeeded even if the learner used the back arrow instead of
                // the completion button; always re-read the head when leaving the editor.
                refreshToken += 1
            },
            onDone = {
                editing = false
                savedRevisionNotice = "修改已保存，之后的复习和导出将使用当前题面。"
                refreshToken += 1
            },
            modifier = modifier,
        )
        return
    }
    val revisionHistoryFlow = remember(errorBookEntryId, repository, refreshToken) {
        if (errorBookEntryId.isBlank()) {
            flowOf(emptyList())
        } else {
            repository.observeRevisionHistory(errorBookEntryId)
        }
    }
    val revisionHistory by revisionHistoryFlow.collectAsStateWithLifecycle(
        initialValue = emptyList(),
    )
    val selectedRevision = revisionHistory.firstOrNull { summary ->
        summary.problemRevisionId == selectedRevisionId && !summary.isCurrent
    }
    val stateFlow: Flow<MistakeDetailState> = remember(
        errorBookEntryId,
        repository,
        selectedRevision,
        refreshToken,
    ) {
        if (errorBookEntryId.isBlank()) {
            flowOf(MistakeDetailState.NotFound)
        } else if (selectedRevision != null) {
            repository.observeExact(selectedRevision.toKey())
        } else {
            repository.observe(errorBookEntryId)
        }
    }
    val state by stateFlow.collectAsStateWithLifecycle(
        initialValue = MistakeDetailState.Loading,
    )
    MistakeDetailContent(
        state = state,
        onBack = onBack,
        onExport = onExport,
        onTutor = onTutor,
        repository = repository,
        organizationRepository = organizationRepository,
        modelTasks = modelTasks,
        profile = profile,
        catalogEntries = catalogEntries,
        revisionHistory = revisionHistory,
        selectedRevisionId = selectedRevision?.problemRevisionId,
        onSelectRevision = { summary ->
            selectedRevisionId = summary.problemRevisionId.takeUnless { summary.isCurrent }
        },
        onOpenRelatedMistake = onOpenRelatedMistake,
        onOpenModelSettings = onOpenModelSettings,
        onEditRequested = { editing = true },
        onEditPaper = onEditPaper,
        revisionNotice = savedRevisionNotice,
        modifier = modifier,
        knowledgeBaseAvailability = knowledgeBaseAvailability,
    )
}

@Composable
internal fun MistakeDetailContent(
    state: MistakeDetailState,
    onBack: () -> Unit,
    onExport: (MistakeRevisionKey) -> Unit,
    onTutor: (MistakeRevisionKey) -> Unit = {},
    repository: MistakeDetailRepository? = null,
    organizationRepository: MistakeOrganizationRepository? = null,
    modelTasks: ModelTaskRepository? = null,
    profile: StudyProfileOverview = StudyProfileOverview(),
    catalogEntries: List<StudyCatalogEntry> = emptyList(),
    revisionHistory: List<MistakeRevisionSummary> = emptyList(),
    selectedRevisionId: String? = null,
    onSelectRevision: (MistakeRevisionSummary) -> Unit = {},
    onOpenRelatedMistake: (String) -> Unit = {},
    onOpenModelSettings: () -> Unit = {},
    onEditRequested: (() -> Unit)? = null,
    onEditPaper: ((MistakeRevisionKey) -> Unit)? = null,
    revisionNotice: String? = null,
    knowledgeBaseAvailability: KnowledgeBaseAvailability = KnowledgeBaseAvailability.Ready,
    modifier: Modifier = Modifier,
) {
    RootPageColumn(
        modifier = modifier.testTag("mistake_detail_screen"),
    ) {
        MistakeDetailHeader(onBack)
        revisionNotice?.let { notice ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("mistake_detail_revision_notice"),
                shape = RoundedCornerShape(10.dp),
                color = JadeSoft.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, Outline),
            ) {
                Text(
                    text = notice,
                    color = InkSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(12.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(12.dp))
        if (
            selectedRevisionId != null &&
            state !is MistakeDetailState.Ready &&
            state != MistakeDetailState.Loading
        ) {
            HistoricalRevisionRecovery(
                current = revisionHistory.firstOrNull(MistakeRevisionSummary::isCurrent),
                onSelectRevision = onSelectRevision,
            )
            Spacer(Modifier.height(12.dp))
        }
        when (state) {
            MistakeDetailState.Loading -> LoadingDetail()
            is MistakeDetailState.Ready -> ReadyDetail(
                state = state,
                onExport = { onExport(checkNotNull(state.exportRevisionKeyOrNull())) },
                onTutor = { onTutor(checkNotNull(state.exportRevisionKeyOrNull())) },
                onBack = onBack,
                repository = repository,
                organizationRepository = organizationRepository,
                modelTasks = modelTasks,
                profile = profile,
                catalogEntries = catalogEntries,
                revisionHistory = revisionHistory,
                isViewingHistoricalRevision = selectedRevisionId != null,
                onSelectRevision = onSelectRevision,
                onOpenRelatedMistake = onOpenRelatedMistake,
                onOpenModelSettings = onOpenModelSettings,
                onEdit = onEditRequested,
                onEditPaper = onEditPaper?.let { callback ->
                    { callback(checkNotNull(state.exportRevisionKeyOrNull())) }
                },
                knowledgeBaseAvailability = knowledgeBaseAvailability,
            )
            is MistakeDetailState.Legacy -> LegacyDetail(state.detail)
            is MistakeDetailState.CorruptSnapshot -> CorruptDetail(state.identity)
            MistakeDetailState.NotFound -> NotFoundDetail()
        }
    }
}

@Composable
private fun HistoricalRevisionRecovery(
    current: MistakeRevisionSummary?,
    onSelectRevision: (MistakeRevisionSummary) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("mistake_detail_historical_recovery"),
        shape = RoundedCornerShape(10.dp),
        color = JadeSoft.copy(alpha = 0.5f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Outline),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "这个历史题面无法完整读取，当前版本没有受到影响。",
                color = InkSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (current != null) {
                PrimaryActionButton(
                    text = "回到当前第 ${current.revisionNumber} 版",
                    onClick = { onSelectRevision(current) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("mistake_detail_recover_current_revision"),
                    contentDescription = "退出无法读取的历史题面并回到当前版本",
                )
            }
        }
    }
}

@Composable
private fun MistakeDetailHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .size(48.dp)
                .testTag("mistake_detail_back"),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "返回",
                tint = Ink,
            )
        }
        Spacer(Modifier.size(4.dp))
        Text(
            text = "错题详情",
            color = Ink,
            style = MaterialTheme.typography.headlineSmall,
        )
    }
}

@Composable
private fun LoadingDetail() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 64.dp)
            .testTag("mistake_detail_loading"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(32.dp),
            color = JadeActive,
        )
        Text(
            text = "正在读取本机题目…",
            color = InkSecondary,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReadyDetail(
    state: MistakeDetailState.Ready,
    onExport: () -> Unit,
    onTutor: () -> Unit,
    onBack: () -> Unit,
    repository: MistakeDetailRepository?,
    organizationRepository: MistakeOrganizationRepository?,
    modelTasks: ModelTaskRepository?,
    profile: StudyProfileOverview,
    catalogEntries: List<StudyCatalogEntry>,
    revisionHistory: List<MistakeRevisionSummary>,
    isViewingHistoricalRevision: Boolean,
    onSelectRevision: (MistakeRevisionSummary) -> Unit,
    onOpenRelatedMistake: (String) -> Unit,
    onOpenModelSettings: () -> Unit,
    onEdit: (() -> Unit)?,
    onEditPaper: (() -> Unit)?,
    knowledgeBaseAvailability: KnowledgeBaseAvailability,
) {
    Column(
        modifier = Modifier.testTag("mistake_detail_ready"),
    ) {
        IdentityBlock(
            identity = state.detail.identity,
            status = if (isViewingHistoricalRevision) "历史题面 · 只读" else "当前题面",
        )
        RevisionHistorySection(
            history = revisionHistory,
            viewedRevisionId = state.detail.identity.problemRevisionId,
            onSelectRevision = onSelectRevision,
        )
        Spacer(Modifier.height(12.dp))
        if (isViewingHistoricalRevision) {
            val current = revisionHistory.firstOrNull(MistakeRevisionSummary::isCurrent)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("mistake_detail_historical_notice"),
                shape = RoundedCornerShape(10.dp),
                color = JadeSoft.copy(alpha = 0.5f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Outline),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        "这是旧版题面，仅供查看。讲题、复习和整理使用当前版本。",
                        color = InkSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            if (current != null) {
                Spacer(Modifier.height(10.dp))
                PrimaryActionButton(
                    text = "回到当前第 ${current.revisionNumber} 版",
                    onClick = { onSelectRevision(current) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("mistake_detail_return_current_revision"),
                    contentDescription = "回到当前题面",
                )
            }
        } else {
            PrimaryActionButton(
                text = "讲解这道题",
                onClick = onTutor,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("mistake_detail_start_tutor"),
                icon = Icons.AutoMirrored.Outlined.Chat,
                contentDescription = "继续或开始讲解这道错题",
            )
        }
        Spacer(Modifier.height(16.dp))
        MistakeSectionCard("题面", modifier = Modifier.testTag("mistake_detail_question_module")) {
            StructuredContentRenderer(
                document = state.questionDocument.document.let { document ->
                    if (document.title?.trim() == state.detail.identity.title.trim()) document.copy(title = null)
                    else document
                },
                choicesEnabled = false,
            )
            PaperDivider()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!isViewingHistoricalRevision && onEdit != null) {
                    InlineActionButton("修改这道题", onEdit, Modifier.testTag("mistake_detail_edit"))
                }
                InlineActionButton(
                    text = if (isViewingHistoricalRevision) "导出这一版 A4" else "导出 A4",
                    onClick = onExport,
                    modifier = Modifier.testTag("mistake_detail_export_a4"),
                )
                // 6B-2：当前版本可进入卷面编辑（只改导出文档副本）；历史版本只读，不提供入口。
                if (!isViewingHistoricalRevision && onEditPaper != null) {
                    InlineActionButton(
                        text = "编辑卷面",
                        onClick = onEditPaper,
                        modifier = Modifier.testTag("mistake_detail_edit_paper"),
                    )
                }
            }
            // 4E WG-06 T3：用户显式重绘只对当前版本开放；历史版本只读（旧修订的结果已 SUPERSEDED）。
            if (!isViewingHistoricalRevision && repository != null && repository.supportsExplicitRedraw()) {
                Spacer(Modifier.height(8.dp))
                ExplicitRedrawSection(
                    revisionId = state.detail.identity.problemRevisionId,
                    repository = repository,
                )
            }
        }
        SourceSection(
            source = state.detail.source,
            isHistorical = isViewingHistoricalRevision,
        )
        if (!isViewingHistoricalRevision && repository != null) {
            Spacer(Modifier.height(16.dp))
            MistakeUserNoteCard(
                entryId = state.detail.identity.errorBookEntryId,
                note = state.detail.userNote,
                repository = repository,
            )
            Spacer(Modifier.height(10.dp))
            if (state.detail.archived) {
                MistakeRestoreAction(
                    entryId = state.detail.identity.errorBookEntryId,
                    repository = repository,
                )
            } else {
                MistakeArchiveAction(
                    entryId = state.detail.identity.errorBookEntryId,
                    repository = repository,
                    onArchived = onBack,
                )
            }
        }
        // 讲解图在独立模块直接呈现，不混进题目图片区。
        ExplanationSourceSection(
            source = state.detail.source,
        )
        if (!isViewingHistoricalRevision && organizationRepository != null && modelTasks != null) {
            MistakeOrganizationSection(
                key = checkNotNull(state.exportRevisionKeyOrNull()),
                organizationRepository = organizationRepository,
                modelTasks = modelTasks,
                profile = profile,
                catalogEntries = catalogEntries,
                onOpenRelatedMistake = onOpenRelatedMistake,
                onOpenModelSettings = onOpenModelSettings,
                knowledgeBaseAvailability = knowledgeBaseAvailability,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

/**
 * 4E WG-06 T3：详情页的显式重绘入口与状态。
 *
 * 状态来自持久台账（`clean_redraw_task`），观察本身不派发；只有点击按钮才调用
 * `requestExplicitRedraw`。失败时展示原因，额度未耗尽给"重试重绘"。
 */
@Composable
private fun ExplicitRedrawSection(
    revisionId: String,
    repository: MistakeDetailRepository,
) {
    val state by repository.observeExplicitRedraw(revisionId)
        .collectAsState(initial = ExplicitRedrawState.Idle)
    val scope = rememberCoroutineScope()
    var submitting by remember(revisionId) { mutableStateOf(false) }
    var message by rememberSaveable(revisionId) { mutableStateOf<String?>(null) }
    val busy = submitting || state.phase == ExplicitRedrawPhase.RUNNING
    val buttonLabel = when {
        busy -> "重绘中…"
        state.phase == ExplicitRedrawPhase.FAILED && state.canRetry -> "重试重绘"
        state.phase == ExplicitRedrawPhase.IDLE -> "重绘干净题面"
        else -> null
    }

    Column(Modifier.fillMaxWidth()) {
        buttonLabel?.let { label ->
            OutlinedButton(
                onClick = {
                    message = null
                    submitting = true
                    scope.launch {
                        val result = try {
                            repository.requestExplicitRedraw(
                                ExplicitRedrawCommand(
                                    problemRevisionId = revisionId,
                                    expectedRevisionId = revisionId,
                                    reason = EXPLICIT_REDRAW_UI_REASON,
                                ),
                            )
                        } catch (failure: Exception) {
                            ExplicitRedrawRequestResult.Rejected(
                                failure.message ?: "REQUEST_FAILED",
                            )
                        }
                        submitting = false
                        message = explicitRedrawResultMessage(result)
                    }
                },
                enabled = !busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("mistake_detail_redraw"),
            ) { Text(label) }
        }
        if (busy) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .testTag("mistake_detail_redraw_progress"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = JadeActive,
                )
                Text(
                    text = explicitRedrawStatusText(state) ?: "正在重绘题面…",
                    color = InkSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        } else {
            explicitRedrawStatusText(state)?.let { status ->
                Spacer(Modifier.height(6.dp))
                Text(
                    text = status,
                    color = InkSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("mistake_detail_redraw_status"),
                )
            }
        }
        message?.let { notice ->
            Spacer(Modifier.height(6.dp))
            Text(
                text = notice,
                color = InkSecondary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("mistake_detail_redraw_message"),
            )
        }
    }
}

/** 详情页发起重绘的固定理由（用户动作；写入命令的审计字段，不参与作业身份）。 */
internal const val EXPLICIT_REDRAW_UI_REASON: String = "用户在详情页请求重新生成干净题面"

/** 状态文案（JVM 可测）：null = 不显示状态行。 */
internal fun explicitRedrawStatusText(state: ExplicitRedrawState): String? = when (state.phase) {
    ExplicitRedrawPhase.IDLE -> null
    ExplicitRedrawPhase.RUNNING -> if (state.attemptCount == 0) {
        "已提交重绘，等待开始…"
    } else {
        "正在重绘题面（第 ${state.attemptCount} 次尝试）…"
    }
    ExplicitRedrawPhase.FAILED -> buildString {
        append("上次重绘失败：")
        append(explicitRedrawFailureText(state.failureReason))
        if (!state.canRetry) append("；重试次数已用完")
    }
    ExplicitRedrawPhase.SUCCEEDED -> "已生成新的干净题面。"
    ExplicitRedrawPhase.SUPERSEDED -> "题面已更新，这次重绘没有执行。"
}

/** 失败原因文案（JVM 可测）。 */
internal fun explicitRedrawFailureText(reason: ExplicitRedrawFailureReason?): String =
    when (reason) {
        ExplicitRedrawFailureReason.SOURCE_UNREADABLE -> "原图读不出来"
        ExplicitRedrawFailureReason.REDRAW_FAILED -> "生成没有成功"
        ExplicitRedrawFailureReason.ATTACH_FAILED -> "新图没能保存到这道题"
        ExplicitRedrawFailureReason.UNKNOWN, null -> "未知原因"
    }

/** 命令结果的人员可读提示（JVM 可测）；null = 不需要额外提示。 */
internal fun explicitRedrawResultMessage(result: ExplicitRedrawRequestResult): String? =
    when (result) {
        is ExplicitRedrawRequestResult.Accepted,
        is ExplicitRedrawRequestResult.AlreadySettled,
        ExplicitRedrawRequestResult.NotSupported,
        -> null

        ExplicitRedrawRequestResult.NotCurrentRevision ->
            "题面已更新，请回到当前版本后再重绘。"

        ExplicitRedrawRequestResult.NoSource ->
            "这道题没有可用的原图，暂时不能重绘。"

        ExplicitRedrawRequestResult.Unavailable ->
            "请在模型设置中完成配置后重绘。"

        is ExplicitRedrawRequestResult.Rejected ->
            "重绘请求未被接受（${result.reason}）。"
    }

@Composable
private fun MistakeUserNoteCard(
    entryId: String,
    note: String?,
    repository: MistakeDetailRepository,
) {
    val scope = rememberCoroutineScope()
    var editing by rememberSaveable(entryId) { mutableStateOf(false) }
    var savedNote by rememberSaveable(entryId, note) { mutableStateOf(note) }
    var draft by rememberSaveable(entryId) { mutableStateOf(note.orEmpty()) }
    var saving by remember { mutableStateOf(false) }
    var message by rememberSaveable(entryId) { mutableStateOf<String?>(null) }

    MistakeSectionCard(
        title = "错因 / 备注",
        modifier = Modifier
            .fillMaxWidth()
            .testTag("mistake_detail_note"),
        action = {
            TextButton(enabled = !saving, onClick = {
                editing = !editing
                draft = savedNote.orEmpty()
                message = null
            }) {
                Text(if (editing) "取消" else if (savedNote.isNullOrBlank()) "添加" else "编辑")
            }
        },
    ) {
        Column {
            if (editing) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.take(2000); message = null },
                    placeholder = { Text("写下这道题错在哪、下次怎么避免…") },
                    minLines = 2,
                    maxLines = 6,
                    enabled = !saving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("mistake_detail_note_input"),
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        saving = true
                        scope.launch {
                            try {
                                val saved = repository.updateUserNote(
                                    entryId = entryId,
                                    note = draft,
                                    updatedAtEpochMillis = System.currentTimeMillis(),
                                )
                                saving = false
                                if (saved) {
                                    savedNote = draft.trim().takeIf(String::isNotEmpty)
                                    editing = false
                                    message = "已保存备注"
                                } else {
                                    message = "保存失败，这道题可能已不存在"
                                }
                            } catch (failure: Exception) {
                                saving = false
                                message = failure.message ?: "保存失败，请重试"
                            }
                        }
                    },
                    enabled = !saving,
                    colors = ButtonDefaults.buttonColors(containerColor = JadeActive),
                    modifier = Modifier.testTag("mistake_detail_note_save"),
                ) { Text(if (saving) "保存中…" else "保存备注") }
            } else if (!savedNote.isNullOrBlank()) {
                Text(
                    text = savedNote.orEmpty(),
                    color = Ink,
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Text(
                    text = "暂无备注，可记录错因或解题要点。",
                    color = InkSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            message?.let {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = it,
                    color = InkSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("mistake_detail_note_message"),
                )
            }
        }
    }
}

@Composable
private fun MistakeRestoreAction(
    entryId: String,
    repository: MistakeDetailRepository,
) {
    val scope = rememberCoroutineScope()
    var restoring by remember { mutableStateOf(false) }
    var error by rememberSaveable(entryId) { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("mistake_detail_archived_banner"),
            shape = RoundedCornerShape(10.dp),
            color = JadeSoft.copy(alpha = 0.5f),
            border = BorderStroke(1.dp, Outline),
        ) {
            Column(Modifier.padding(14.dp)) {
                Text(
                    "这道题已移出错题本",
                    color = Ink,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "学习记录已保留，恢复后会重新出现在错题本和复习中。",
                    color = InkSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        restoring = true
                        scope.launch {
                            try {
                                val ok = repository.restoreEntry(
                                    entryId = entryId,
                                    at = System.currentTimeMillis(),
                                )
                                restoring = false
                                if (!ok) error = "恢复失败，这道题可能已不存在"
                            } catch (failure: Exception) {
                                restoring = false
                                error = failure.message ?: "恢复失败，请重试"
                            }
                        }
                    },
                    enabled = !restoring,
                    modifier = Modifier.testTag("mistake_detail_restore"),
                ) { Text(if (restoring) "恢复中…" else "恢复到错题本") }
                error?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = it,
                        color = InkSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun MistakeArchiveAction(
    entryId: String,
    repository: MistakeDetailRepository,
    onArchived: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var showConfirm by rememberSaveable(entryId) { mutableStateOf(false) }
    var archiving by remember { mutableStateOf(false) }
    var error by rememberSaveable(entryId) { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { showConfirm = true },
            enabled = !archiving,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("mistake_detail_archive"),
        ) { Text("移出错题本") }
        if (showConfirm) {
            AlertDialog(
                onDismissRequest = { showConfirm = false },
                title = { Text("移出错题本？") },
                text = {
                    Text("题目会从错题本、搜索和复习中消失，但学习记录会保留，之后可以恢复。")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            archiving = true
                            scope.launch {
                                try {
                                    val ok = repository.archiveEntry(
                                        entryId = entryId,
                                        at = System.currentTimeMillis(),
                                    )
                                    archiving = false
                                    if (ok) {
                                        showConfirm = false
                                        onArchived()
                                    } else {
                                        error = "这道题不存在或已移出"
                                    }
                                } catch (failure: Exception) {
                                    archiving = false
                                    error = failure.message ?: "操作失败，请重试"
                                }
                            }
                        },
                    ) { Text("移出") }
                },
                dismissButton = {
                    TextButton(onClick = { showConfirm = false }) { Text("取消") }
                },
            )
        }
        error?.let {
            Spacer(Modifier.height(6.dp))
            Text(
                text = it,
                color = InkSecondary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("mistake_detail_archive_error"),
            )
        }
    }
}

internal fun MistakeDetailState.exportRevisionKeyOrNull(): MistakeRevisionKey? =
    (this as? MistakeDetailState.Ready)?.detail?.identity?.let { identity ->
        MistakeRevisionKey(
            entryId = identity.errorBookEntryId,
            problemId = identity.problemId,
            problemRevisionId = identity.problemRevisionId,
        )
    }

@Composable
private fun LegacyDetail(detail: MistakeDetail) {
    Column(
        modifier = Modifier.testTag("mistake_detail_legacy"),
    ) {
        IdentityBlock(detail.identity, status = "早期保存的题目")
        SourceSection(
            source = detail.source,
        )
        PaperDivider(Modifier.padding(vertical = 18.dp))
        SectionHeader("题面文本")
        Spacer(Modifier.height(12.dp))
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("mistake_detail_legacy_notice"),
            color = JadeSoft.copy(alpha = 0.45f),
            shape = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Outline),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "较早保存的题面",
                    color = Ink,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "这道题没有可用原图，因此暂不支持导出；已保存的题面仍可查看。",
                    color = InkSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        SafeMarkdownText(
            markdown = detail.fallbackMarkdown,
            color = Ink,
            style = MaterialTheme.typography.bodyLarge,
        )
        ExplanationSourceSection(
            source = detail.source,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun CorruptDetail(identity: MistakeDetailIdentity) {
    Column(
        modifier = Modifier.testTag("mistake_detail_corrupt"),
    ) {
        IdentityBlock(identity, status = "题面暂时无法读取")
        PaperDivider(Modifier.padding(vertical = 18.dp))
        SectionHeader("题面状态")
        Spacer(Modifier.height(12.dp))
        Text(
            text = "题面暂时无法显示",
            color = Ink,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "这份题面内容不完整，暂时不能显示；本机原图和记录仍保留。",
            color = InkSecondary,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun NotFoundDetail() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp)
            .testTag("mistake_detail_not_found"),
    ) {
        Text(
            text = "未找到这道题",
            color = Ink,
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "这道题不在错题本中。若已移出，可到「已移出的题」中恢复。",
            color = InkSecondary,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun IdentityBlock(
    identity: MistakeDetailIdentity,
    status: String,
) {
    Text(
        text = identity.title,
        color = Ink,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = "${identity.subject.studentSubjectLabel()} · 第 ${identity.revisionNumber} 版 · $status",
        color = InkSecondary,
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun RevisionHistorySection(
    history: List<MistakeRevisionSummary>,
    viewedRevisionId: String,
    onSelectRevision: (MistakeRevisionSummary) -> Unit,
) {
    if (history.isEmpty()) return
    val ordered = remember(history) { history.sortedByDescending(MistakeRevisionSummary::revisionNumber) }
    val viewed = ordered.firstOrNull { it.problemRevisionId == viewedRevisionId } ?: return
    var expanded by rememberSaveable(viewedRevisionId, ordered.size) { mutableStateOf(false) }

    Spacer(Modifier.height(18.dp))
    SectionHeader(
        title = "题面版本",
        action = if (ordered.size > 1) {
            {
                OutlineActionChip(
                    text = if (expanded) "收起" else "查看 ${ordered.size} 个版本",
                    onClick = { expanded = !expanded },
                    modifier = Modifier.testTag("mistake_revision_history_toggle"),
                    selected = expanded,
                )
            }
        } else {
            null
        },
    )
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .testTag("mistake_revision_summary"),
        shape = RoundedCornerShape(10.dp),
        color = JadeSoft.copy(alpha = 0.4f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Outline),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = "正在查看第 ${viewed.revisionNumber} 版${if (viewed.isCurrent) " · 当前" else " · 历史只读"}",
                color = Ink,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = if (ordered.size == 1) {
                    "当前只有 1 个已保存题面版本"
                } else {
                    "共保留 ${ordered.size} 个已保存版本，旧版不会被后续修改覆盖"
                },
                color = InkSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
    if (expanded) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .testTag("mistake_revision_history_list"),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ordered.forEach { revision ->
                val selected = revision.problemRevisionId == viewedRevisionId
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("mistake_revision_${revision.revisionNumber}")
                        .then(
                            if (selected) Modifier else Modifier.clickable {
                                onSelectRevision(revision)
                            },
                        ),
                    shape = RoundedCornerShape(8.dp),
                    color = if (selected) JadeSoft else MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Outline),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            text = "第 ${revision.revisionNumber} 版${if (revision.isCurrent) " · 当前" else ""}",
                            color = if (revision.isCurrent) JadeActive else Ink,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = revision.title,
                            color = Ink,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            text = formatRevisionTime(revision.createdAtEpochMillis),
                            color = InkSecondary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

private fun formatRevisionTime(epochMillis: Long): String {
    if (epochMillis <= 0L) return "保存时间未记录"
    return SimpleDateFormat(REVISION_TIME_PATTERN, Locale.getDefault())
        .format(Date(epochMillis))
}

private const val REVISION_TIME_PATTERN = "yyyy年M月d日 HH:mm"

@Composable
private fun SourceSection(source: MistakeSourceSet, isHistorical: Boolean = false) {
    SourceAssetSection(source.detailAssets().question, isHistorical = isHistorical)
}

@Composable
private fun ExplanationSourceSection(source: MistakeSourceSet) {
    SourceAssetSection(source.detailAssets().explanation, isExplanation = true)
}

/** Pictures stay visible in their own role section; only decoding follows the viewport. */
@Composable
private fun SourceAssetSection(
    assets: List<MistakeSourceAsset>,
    isHistorical: Boolean = false,
    isExplanation: Boolean = false,
) {
    if (assets.isEmpty()) return
    val title = if (isExplanation) "讲解图" else "原图"
    val tag = if (isExplanation) "mistake_detail_explanation" else "mistake_detail_source"
    Spacer(Modifier.height(16.dp))
    MistakeSectionCard(
        title = if (assets.size > 1) "$title · ${assets.size} 张" else title,
        modifier = Modifier.testTag("${tag}_section"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().testTag("${tag}_content"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            assets.forEachIndexed { index, asset ->
                SourceAssetPreview(asset, index + 1, assets.size, isHistorical, isExplanation)
            }
        }
    }
}

internal fun sourceAssetDisplayTitle(
    role: String,
    position: Int,
    total: Int,
    isHistorical: Boolean,
    isExplanation: Boolean,
): String = when {
    isExplanation -> if (total == 1) "讲解图" else "讲解图 $position / $total"
    role == CLEAN_IMAGE_SOURCE_ROLE && isHistorical -> "旧版重绘（仅历史题面）"
    role == CLEAN_IMAGE_SOURCE_ROLE -> "重绘"
    total == 1 -> "原图"
    else -> "原图 $position / $total"
}

@Composable
private fun SourceAssetPreview(
    asset: MistakeSourceAsset,
    position: Int,
    total: Int,
    isHistorical: Boolean = false,
    isExplanation: Boolean = false,
) {
    val imageUri = (asset.location as? MistakeSourceLocation.Available)?.localUri
    val title = sourceAssetDisplayTitle(asset.role, position, total, isHistorical, isExplanation)
    val tag = "${if (isExplanation) "mistake_detail_explanation_asset" else "mistake_detail_source_asset"}_$position"
    var viewing by rememberSaveable(asset.sourceAssetId, imageUri) { mutableStateOf(false) }
    var inViewport by remember(asset.sourceAssetId, imageUri) { mutableStateOf(false) }
    var loadState by remember(asset.sourceAssetId, imageUri) {
        mutableStateOf(if (imageUri == null) LocalImageLoadState.UNAVAILABLE else LocalImageLoadState.LOADING)
    }
    val showAssetHeading = total > 1 || asset.role == CLEAN_IMAGE_SOURCE_ROLE
    @Composable
    fun ViewAction(modifier: Modifier = Modifier) {
        if (imageUri != null) {
            InlineActionButton("查看大图", { viewing = true }, modifier.testTag("${tag}_view"),
                enabled = loadState == LocalImageLoadState.AVAILABLE)
        }
    }
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp).testTag(tag)) {
        if (showAssetHeading) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(title, Modifier.weight(1f), color = Ink,
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                ViewAction()
            }
        }
        if (imageUri != null && loadState != LocalImageLoadState.UNAVAILABLE) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val imageHeight = (maxWidth * (asset.height.toFloat() / asset.width.coerceAtLeast(1))).coerceAtLeast(80.dp)
                // Keep natural full-width geometry while releasing offscreen sampled bitmaps.
                Box(Modifier.fillMaxWidth().height(imageHeight).testTag("${tag}_image")
                    .onGloballyPositioned { coordinates ->
                        val visible = coordinates.boundsInWindow()
                        inViewport = visible.width > 0f && visible.height > 0f
                    }) {
                    if (inViewport) {
                        BoundedLocalImage(
                            imageUri = imageUri,
                            contentDescription = title,
                            expanded = true,
                            expandedMaxHeight = imageHeight,
                            modifier = Modifier.fillMaxSize().clickable(
                                enabled = loadState == LocalImageLoadState.AVAILABLE,
                            ) { viewing = true },
                            onLoadStateChange = { loadState = it },
                        )
                    }
                }
            }
        }
        if (!showAssetHeading && imageUri != null) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End) {
                ViewAction()
            }
        }
        sourceStatusText(imageUri != null, loadState)?.let { status ->
            Text(status, Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = InkSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
    if (viewing && imageUri != null) LocalImageViewer(imageUri, title, { viewing = false })
}

/** 持久角色值来自 [VisualRole] 单源；保留别名给列表缩略图与既有测试。 */
internal val CLEAN_IMAGE_SOURCE_ROLE: String = VisualRole.CLEAN_IMAGE.name

/** 详情页角色分区：题目区与讲解区各拿各的资产，互不混排。 */
internal data class MistakeDetailAssets(
    val question: List<MistakeSourceAsset>,
    val explanation: List<MistakeSourceAsset>,
)

internal fun MistakeSourceSet.detailAssets(): MistakeDetailAssets = when (this) {
    MistakeSourceSet.Missing -> MistakeDetailAssets(emptyList(), emptyList())
    is MistakeSourceSet.Present -> {
        val explanation = assets.filter { it.role == VisualRole.TUTOR_EXPLANATION.name }
        val question = assets.filterNot { it.role == VisualRole.TUTOR_EXPLANATION.name }
        // 同一修订可能留有历史上多次重绘的 CLEAN_IMAGE：最新的那张才是"当前干净题面"
        // （attach 侧现在也会替换旧链接；这里对旧库数据按创建时间兜底选新）。
        val clean = question.filter { it.role == CLEAN_IMAGE_SOURCE_ROLE }
            .sortedWith(compareByDescending<MistakeSourceAsset> { it.createdAtEpochMillis }
                .thenBy { it.sourceAssetId })
        // 旧数据（只有 CLEAN_IMAGE/QUESTION_SOURCE）保持"重绘在前、其余原顺序"的逐位结果。
        MistakeDetailAssets(
            question = clean + question.filterNot { it.role == CLEAN_IMAGE_SOURCE_ROLE },
            explanation = explanation,
        )
    }
}

/**
 * 列表缩略图/题图预览用的题目区资产。
 *
 * **TUTOR_EXPLANATION 永远不在这里**：它是解析区图，不是题目本身。此前只把 CLEAN_IMAGE
 * 提前、其余全保留，导致书库卡片把讲解图逐张标成"题图"（4E WG-06 新增角色数据的读侧
 * 影响面；详情页/导出早已按角色过滤，这里是遗漏的读口）。
 */
internal fun MistakeSourceSet.displayAssets(): List<MistakeSourceAsset> = when (this) {
    MistakeSourceSet.Missing -> emptyList()
    is MistakeSourceSet.Present -> {
        val clean = assets.filter { it.role == CLEAN_IMAGE_SOURCE_ROLE }
            .sortedWith(compareByDescending<MistakeSourceAsset> { it.createdAtEpochMillis }
                .thenBy { it.sourceAssetId })
        val original = assets.filterNot {
            it.role == CLEAN_IMAGE_SOURCE_ROLE || it.role == VisualRole.TUTOR_EXPLANATION.name
        }
        // Clean redraws (if attached at commit time) come first; originals stay available.
        clean + original
    }
}

internal fun sourceStatusText(
    hasLocalLocation: Boolean,
    loadState: LocalImageLoadState,
): String? = when {
    !hasLocalLocation -> "图片文件暂时无法读取。"
    loadState == LocalImageLoadState.LOADING -> "正在加载图片…"
    loadState == LocalImageLoadState.UNAVAILABLE -> "图片暂时无法显示。"
    else -> null
}
