package com.tingyun.smartmistakebook

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.tingyun.smartmistakebook.core.domain.MistakeExportRecord
import com.tingyun.smartmistakebook.core.domain.MistakeExportStatus
import com.tingyun.smartmistakebook.core.domain.StudyDataStatus
import com.tingyun.smartmistakebook.core.domain.StudyExperienceSnapshot
import com.tingyun.smartmistakebook.core.domain.TutorMessage
import com.tingyun.smartmistakebook.core.export.MistakePdfDeliveryIntents
import com.tingyun.smartmistakebook.core.export.MistakePdfDocumentSaver
import com.tingyun.smartmistakebook.core.export.MistakePdfExporter
import com.tingyun.smartmistakebook.core.export.MistakePdfSaveResult
import com.tingyun.smartmistakebook.core.export.PreparedMistakePdfToken
import com.tingyun.smartmistakebook.core.model.MistakeExportFormat
import com.tingyun.smartmistakebook.core.model.MistakePdfLayout
import com.tingyun.smartmistakebook.core.ui.InkSecondary
import com.tingyun.smartmistakebook.core.ui.OutlineActionChip
import com.tingyun.smartmistakebook.core.ui.PrimaryActionButton
import com.tingyun.smartmistakebook.core.ui.SmartColors
import com.tingyun.smartmistakebook.core.ui.WorkspacePanel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate

@Composable
internal fun AgentLearningToolCard(
    message: TutorMessage,
    sourceText: String,
    application: SmartMistakeBookApplication,
    experience: StudyExperienceSnapshot,
    exports: List<MistakeExportRecord>,
    appliedAdvice: Set<String>,
) {
    if (toolReplyKind(sourceText) == null) return
    val effectId = remember(message.messageId) { agentEffectId("${message.conversationId}\n${message.messageId}") }
    val proposal = remember(message.bodyMarkdown, sourceText) { decodeAgentToolProposal(message.bodyMarkdown, sourceText) }
    val scope = rememberCoroutineScope()
    var busy by remember(message.messageId) { mutableStateOf(false) }
    var notice by rememberSaveable(message.messageId) { mutableStateOf<String?>(null) }

    fun runAction(action: suspend () -> String?) {
        if (busy) return
        busy = true
        notice = null
        scope.launch {
            try {
                notice = action()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                notice = "暂时无法完成这项操作，请重试。"
            } finally {
                busy = false
            }
        }
    }

    WorkspacePanel(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("agent_tool_reply_card"),
        containerColor = SmartColors.ContentSurface,
    ) {
        when (proposal) {
            is AgentToolProposal.Paper -> {
                val exportId = "agent-paper-$effectId"
                val observed = exports.firstOrNull { it.exportId == exportId }
                val record by produceState(observed, exportId, observed) {
                    value = observed ?: application.mistakeExportRepository.readRecord(exportId)
                }
                val currentRecord = record
                when (currentRecord?.status) {
                    MistakeExportStatus.SUCCEEDED -> AgentPaperFile(currentRecord)
                    MistakeExportStatus.RUNNING -> Text("正在生成试卷 PDF…")
                    MistakeExportStatus.FAILED -> Text(currentRecord.failureMessage ?: "试卷生成失败，请重新请求组卷。")
                    null -> {
                        val byCode = remember(experience.catalog) { experience.catalog.associateBy { it.paperCode() } }
                        val selected = proposal.codes.mapNotNull(byCode::get)
                        Text("确认智能体选题", style = MaterialTheme.typography.titleMedium)
                        selected.forEachIndexed { index, item ->
                            Text("${index + 1}. ${item.title}", style = MaterialTheme.typography.bodyMedium)
                        }
                        val ready = selected.size == proposal.codes.size && experience.status == StudyDataStatus.READY
                        if (!ready) Text("候选题已更新或暂不可读，请重新组卷。", color = InkSecondary)
                        PrimaryActionButton(
                            text = if (busy) "正在检查题面…" else "确认并生成 PDF",
                            enabled = ready && !busy,
                            onClick = {
                                runAction {
                                    val snapshot = application.studyRepository.snapshot.value
                                    val confirmed = snapshot.catalog.associateBy { it.paperCode() }
                                    if (snapshot.status != StudyDataStatus.READY || proposal.codes.any { it !in confirmed }) {
                                        return@runAction "候选题已更新，请重新组卷。"
                                    }
                                    val entries = proposal.codes.map { confirmed.getValue(it) }
                                    if (application.mistakeExportRepository.readRecord(exportId) != null) return@runAction null
                                    val paper = application.buildMistakePaperEdit(
                                        entries.map { it.entryId }, MistakePdfLayout.DEFAULT,
                                    )
                                    val draft = paper?.toDraft()
                                    if (draft == null || draft.frozenQuestions.map { it.entryId to it.basis.revisionId } !=
                                        entries.map { it.entryId to it.problemRevisionId }
                                    ) {
                                        return@runAction "所选题缺少可导出的题面，或版本已变化，请重新组卷。"
                                    }
                                    application.startMistakeExportEdited(draft, MistakeExportFormat.PDF, exportId)
                                    val queued = withTimeoutOrNull(12_000) {
                                        application.mistakeExportRepository.observeRecords().first { rows ->
                                            rows.any { it.exportId == exportId }
                                        }
                                    }
                                    if (queued == null) "导出暂未入队，请稍后重试。" else null
                                }
                            },
                            modifier = Modifier.testTag("agent_confirm_paper"),
                        )
                    }
                }
            }
            is AgentToolProposal.Review -> {
                val today = LocalDate.now().toEpochDay()
                val applied = effectId in appliedAdvice
                val validation = reviewAdviceIssue(proposal, experience, today)
                Text("复习调整预览", style = MaterialTheme.typography.titleMedium)
                proposal.days.forEach { day ->
                    Text(
                        "${LocalDate.ofEpochDay(day.epochDay)} · 最多 ${day.questionLimit} 题 · 主观题目标 ${day.subjectivePercent}%",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (day.focus.isNotEmpty()) Text(day.focus.joinToString("、"), color = InkSecondary)
                }
                Text("实际题量和类型比例取决于可用题目与复习时长。", color = InkSecondary,
                    style = MaterialTheme.typography.bodySmall)
                if (applied) {
                    Text("已保存复习调整", modifier = Modifier.testTag("agent_review_applied"))
                } else {
                    validation?.let { Text(it, color = InkSecondary) }
                    PrimaryActionButton(
                        text = if (busy) "正在保存…" else "确认调整复习",
                        enabled = validation == null && !busy,
                        onClick = {
                            runAction {
                                val currentDay = LocalDate.now().toEpochDay()
                                reviewAdviceIssue(proposal, application.studyRepository.snapshot.value, currentDay)
                                    ?.let { return@runAction it }
                                val written = application.schedulingSettingsStore.applyReviewDayPreferences(
                                    effectId, currentDay, proposal.days,
                                )
                                if (written && proposal.days.any { it.epochDay == currentDay }) {
                                    try {
                                        application.studyRepository.refresh()
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (_: Exception) {
                                        return@runAction "设置已保存，今日队列暂未刷新。"
                                    }
                                }
                                null
                            }
                        },
                        modifier = Modifier.testTag("agent_confirm_review"),
                    )
                }
            }
            null -> Text("没有可确认的完整建议，可重新请智能体生成。", color = InkSecondary)
        }
        notice?.let { Text(it, color = InkSecondary) }
    }
}

internal fun reviewAdviceIssue(
    proposal: AgentToolProposal.Review,
    experience: StudyExperienceSnapshot,
    today: Long,
): String? = when {
    experience.status != StudyDataStatus.READY -> "学习记录暂未就绪。"
    proposal.days.any { it.epochDay !in today..(today + 6) } -> "日期已过期或超过未来 6 天，请重新规划。"
    proposal.days.any { it.epochDay == today } &&
        (experience.review.completedToday || experience.review.activeSessionId != null) ->
        "今日复习已开始或完成，请从明天重新规划。"
    proposal.days.any { day -> day.focus.any { it !in reviewFocusLabels(experience) } } ->
        "复习方向已变化，请根据当前学习记录重新规划。"
    else -> null
}

@Composable
private fun AgentPaperFile(record: MistakeExportRecord) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val exporter = remember(context) { MistakePdfExporter(context) }
    val name = record.displayName ?: "错题试卷.pdf"
    var busy by remember { mutableStateOf(false) }
    var notice by rememberSaveable(record.exportId) { mutableStateOf<String?>(null) }
    suspend fun prepare() = withContext(Dispatchers.IO) {
        record.preparedPdfValueOrNull()?.let(PreparedMistakePdfToken::fromPersistedValue)?.let(exporter::reopenVerified)
    }
    val download = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri == null) {
            busy = false
        } else {
            scope.launch {
                try {
                    val prepared = prepare()
                    val saved = prepared != null && withContext(Dispatchers.IO) {
                        MistakePdfDocumentSaver.save(context, prepared, uri) == MistakePdfSaveResult.SAVED
                    }
                    notice = if (saved) "已保存到选定位置。" else "文件已清理或保存失败。"
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    notice = "暂时无法保存试卷。"
                } finally {
                    busy = false
                }
            }
        }
    }
    Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("agent_paper_file"))
    Text("PDF · ${record.pageCount} 页", color = InkSecondary, style = MaterialTheme.typography.bodySmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlineActionChip("下载", enabled = !busy, onClick = {
            busy = true
            notice = null
            scope.launch {
                try {
                    if (prepare() == null) {
                        notice = "文件已清理，请重新组卷。"
                        busy = false
                    } else {
                        download.launch(name)
                    }
                } catch (cancelled: CancellationException) {
                    busy = false
                    throw cancelled
                } catch (_: Exception) {
                    notice = "暂时无法打开下载位置。"
                    busy = false
                }
            }
        })
        OutlineActionChip("分享", enabled = !busy, onClick = {
            busy = true
            notice = null
            scope.launch {
                try {
                    val prepared = prepare()
                    if (prepared == null) {
                        notice = "文件已清理，请重新组卷。"
                    } else {
                        context.startActivity(Intent.createChooser(
                            MistakePdfDeliveryIntents.share(context, prepared, name), "分享试卷",
                        ))
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    notice = "暂时无法分享试卷。"
                } finally {
                    busy = false
                }
            }
        })
    }
    notice?.let { Text(it, color = InkSecondary) }
}
