package com.tingyun.smartmistakebook.feature.review

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.tingyun.smartmistakebook.core.ui.ClaudeTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tingyun.smartmistakebook.core.domain.ReviewStoredAnswer
import com.tingyun.smartmistakebook.core.domain.StudyAnswerRevealRequest
import com.tingyun.smartmistakebook.core.domain.StudyAnswerRevealResult
import com.tingyun.smartmistakebook.core.domain.StudyCatalogEntry
import com.tingyun.smartmistakebook.core.domain.StudyReviewSessionStatus
import com.tingyun.smartmistakebook.core.ui.PrimaryActionButton
import com.tingyun.smartmistakebook.core.ui.RootPageColumn
import com.tingyun.smartmistakebook.core.ui.ClaudePageHeader
import com.tingyun.smartmistakebook.core.ui.SectionHeader
import com.tingyun.smartmistakebook.core.ui.SmartColors
import com.tingyun.smartmistakebook.core.ui.studentSubjectLabel

/**
 * 讲题判定结算的可见状态（KD-36）。
 *
 * 结算没有形成判定（NO_VERDICT）或没有写成功（异常）时，复习项上要**看得见**，并且有一个
 * 显式的再次尝试入口——沉默的 NO_VERDICT 等于学生停在第 1 题却不知道发生了什么，
 * 页面上也没有任何可操作的东西。
 *
 * 文案由调用方（App 目的地）给：它才知道这次是"没有判定"还是"写入失败"。
 */
data class ReviewTutorSettlementNotice(
    val message: String,
    val retryLabel: String,
)

/**
 * 无工件错题的复习入口（第2条：复习作答与评级不再由学生自评）。
 *
 * 这道题没有机判选项，学生在这里唯一的动作是**去讲题判定**：先把题按自己的思路做一遍，
 * 讲题里模型出检查题（选择题或开放式追问），对错由本地核对、模型给语义判词，判定完成后
 * 自动落成这次复习的 attempt 并进入下一题。
 *
 * 没有模型时不给假通道：如实说明"需要配置模型才能判定"，学生可以返回——该复习项保持
 * 到期，不会被伪造的记忆更新或跳过记录带过。
 *
 * **这是 2026-09-14 知情裁定的取舍，不是遗漏**：另两个候选（零权重"本次先跳过"、计划期排除）
 * 的代价见 `docs/scenario-registry.md` 的 ReviewSession 行。已知后果——生产线上的题都是
 * 无工件题，所以没有可用模型时**整条复习队列都不推进**（卡在第 1 题），等于该构建没有复习功能。
 * 改这条行为前先读那份登记：跳过必须零权重、不动记忆、该题保持到期，否则就是伪造证据。
 */
@Composable
fun CapturedReviewSessionScreen(
    onBack: () -> Unit,
    entry: StudyCatalogEntry,
    queuePosition: Int,
    queueSize: Int,
    tutorJudgedAvailable: Boolean,
    onOpenTutorJudge: () -> Unit,
    /**
     * 上一次讲题判定的结算结果（KD-36）：null = 不需要说（已结算 / 还没形成可说的状态）。
     */
    settlementNotice: ReviewTutorSettlementNotice? = null,
    /** 「再试一次结算」的出口；null = 这个界面不给重试（结算还没有可重试的对象）。 */
    onRetrySettlement: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    questionState: com.tingyun.smartmistakebook.core.domain.MistakeDetailState? = null,
    /**
     * PRD-05：三键自评通道。提供时本题不再依赖模型判定——作答后按对/卡顿/错自评、
     * 看已存答案、下一题；模型只作可选讲解路径。null = 未接线（保留旧行为）。
     *
     * I1 接线：`onSubmitSelfReport = { repository.submitReviewSelfReport(it) }`、
     * `onUndoReview = { repository.undoReviewSelfReport(it) }`、
     * `onContinue = continueReview`、`storedAnswer = repository.storedReviewAnswer(entry.practiceUnitId)`。
     */
    reviewSessionId: String? = null,
    reviewStateVersion: Long? = null,
    /**
     * attempt 的呈现身份。默认并入题版本与会话身份：题版本变更或跨日新会话时，
     * 旧的 attempt/回执不会串到新呈现上。I1 接线时可显式传入。
     */
    presentationId: String = capturedReviewPresentationId(entry, reviewSessionId),
    storedAnswer: com.tingyun.smartmistakebook.core.domain.ReviewStoredAnswer? = null,
    onSubmitSelfReport:
        (suspend (com.tingyun.smartmistakebook.core.domain.StudySelfReportedReviewSubmission) ->
            com.tingyun.smartmistakebook.core.domain.StudySelfReportedReviewResult)? = null,
    onUndoReview:
        (suspend (com.tingyun.smartmistakebook.core.domain.StudyReviewUndoRequest) ->
            com.tingyun.smartmistakebook.core.domain.StudyReviewUndoResult)? = null,
    /**
     * 展示已存答案即真实曝光：必须落 reveal outcome，撤销后重评才不会被当成独立作答。
     * null = 宿主未接线（不写曝光记录，自评仍可用）。
     */
    onRevealAnswer:
        (suspend (StudyAnswerRevealRequest) -> StudyAnswerRevealResult)? = null,
    onContinue: ((com.tingyun.smartmistakebook.core.domain.StudyReviewAdvanceResult) -> Unit)? = null,
    onSessionRewound:
        ((com.tingyun.smartmistakebook.core.domain.StudyReviewSessionProgress) -> Unit)? = null,
    /** 无可信答案时的"跳过"出口；不写证据、不伪称判对。 */
    onSkipQuestion: (() -> Unit)? = null,
) {
    val safeQueueSize = queueSize.coerceAtLeast(1)
    val safeQueuePosition = queuePosition.coerceIn(1, safeQueueSize)
    val problemScrollState = rememberScrollState()
    val selfReportAvailable = selfReportChannelReady(
        hasSubmit = onSubmitSelfReport != null,
        hasUndo = onUndoReview != null,
        sessionId = reviewSessionId,
        stateVersion = reviewStateVersion,
    )
    val selfReportViewModel: ReviewSelfReportViewModel? = if (selfReportAvailable) {
        viewModel(key = "captured-self-report:$presentationId")
    } else {
        null
    }
    val startedAtEpochMillis = remember(presentationId) { System.currentTimeMillis() }
    if (selfReportViewModel != null) {
        LaunchedEffect(selfReportViewModel.undoStatus, selfReportViewModel.latestProgress) {
            if (selfReportViewModel.undoStatus == SelfReportUndoStatus.UNDONE) {
                selfReportViewModel.latestProgress?.let { onSessionRewound?.invoke(it) }
            }
        }
    }
    // 自评记录后 UI 会展示已存答案/解析——只有可信答案才是真实曝光。没有这笔 outcome，
    // 撤销后的重评会被当成"没看过答案"的独立作答。无可信答案时面板只是如实说明，
    // 不能冒充看过答案（reveal 写入也会因无机判工件而失败）。
    val hasTrustedStoredAnswer = storedAnswer?.isTrusted == true
    var revealWriteFailed by remember(presentationId) { mutableStateOf(false) }
    val revealWriter = onRevealAnswer
    val selfReportState = selfReportViewModel
    if (selfReportState != null && revealWriter != null) {
        LaunchedEffect(
            selfReportState.recorded?.attemptId,
            presentationId,
            hasTrustedStoredAnswer,
        ) {
            if (selfReportState.recorded == null || !hasTrustedStoredAnswer) {
                return@LaunchedEffect
            }
            revealWriteFailed = runCatching {
                revealWriter(
                    StudyAnswerRevealRequest(
                        requestId = "request:$presentationId:reveal:1",
                        presentationId = presentationId,
                        practiceUnitId = entry.practiceUnitId,
                        occurredAtEpochMillis = System.currentTimeMillis(),
                    ),
                )
            }.isFailure
        }
    }
    RootPageColumn(modifier = modifier.testTag("captured_review_session_root")) {
        ClaudePageHeader(
            title = "今日复习",
            onBack = onBack,
            backModifier = Modifier.testTag("captured_review_back"),
            backLabel = "返回今日复习",
            actions = {
                Text(entry.subject.studentSubjectLabel(), style = MaterialTheme.typography.labelLarge,
                    color = SmartColors.InkSecondary)
            },
        )
        Spacer(Modifier.height(10.dp))
        ReviewSessionProgress(
            label = "第 $safeQueuePosition / $safeQueueSize 题 · 重做已保存的原题",
            progress = safeQueuePosition.toFloat() / safeQueueSize.toFloat(),
        )
        Spacer(Modifier.height(16.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .clip(RoundedCornerShape(20.dp))
                .background(SmartColors.ContentSurface)
                .verticalScroll(problemScrollState)
                .padding(16.dp),
        ) {
            SectionHeader(title = entry.title)
            Spacer(Modifier.height(12.dp))
            CapturedReviewQuestionContent(entry, questionState)
        }
        Spacer(Modifier.height(24.dp))
        if (selfReportViewModel != null) {
            // PRD-05 无模型闭环：三键自评 + 已存答案 + 下一题 + undo。
            val recorded = selfReportViewModel.recorded
            ReviewSelfReportKeys(
                selected = selfReportViewModel.selectedKey,
                enabled = recorded == null &&
                    selfReportViewModel.status != SelfReportStatus.RECORDING,
                pendingSubmit = selfReportViewModel.hasPendingSubmit,
                onSelect = { key ->
                    if (selfReportViewModel.status == SelfReportStatus.RECORDING) {
                        return@ReviewSelfReportKeys
                    }
                    if (recorded != null) return@ReviewSelfReportKeys
                    selfReportViewModel.acknowledgeUndo()
                    selfReportViewModel.select(key)
                    selfReportViewModel.requestSubmit(
                        presentationId = presentationId,
                        practiceUnitId = entry.practiceUnitId,
                        fallbackSessionId = requireNotNull(reviewSessionId),
                        fallbackStateVersion = requireNotNull(reviewStateVersion),
                        selectedChoiceId = null,
                        durationSeconds = ((System.currentTimeMillis() - startedAtEpochMillis)
                            .coerceAtLeast(0L) / 1_000L).toInt(),
                        occurredAtEpochMillis = System.currentTimeMillis(),
                        submit = requireNotNull(onSubmitSelfReport),
                    )
                },
            )
            ReviewSelfReportStatusLine(
                status = selfReportViewModel.status,
                undoStatus = selfReportViewModel.undoStatus,
                undoReceipt = selfReportViewModel.undoReceipt,
            )
            if (recorded != null) {
                Spacer(Modifier.height(12.dp))
                ReviewSelfReportReceipt(recorded = recorded)
                Spacer(Modifier.height(10.dp))
                ReviewStoredAnswerPanel(
                    storedAnswer = storedAnswer ?: ReviewStoredAnswer(
                        answerMarkdown = null,
                        explanationMarkdown = null,
                    ),
                    visible = true,
                )
                if (revealWriteFailed) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "未能确认答案查看记录；撤销后重答，可能被误记为未看过答案。",
                        style = MaterialTheme.typography.bodySmall,
                        color = SmartColors.ErrorText,
                        modifier = Modifier.testTag("review_reveal_write_failed"),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            selfReportViewModel.requestUndo(
                                presentationId = presentationId,
                                practiceUnitId = entry.practiceUnitId,
                                occurredAtEpochMillis = System.currentTimeMillis(),
                                undo = requireNotNull(onUndoReview),
                            )
                        },
                        enabled = selfReportViewModel.undoStatus !=
                            SelfReportUndoStatus.RECORDING,
                        modifier = Modifier.testTag("review_undo_answer"),
                    ) {
                        Text(
                            if (selfReportViewModel.undoStatus == SelfReportUndoStatus.FAILED) {
                                "重试撤销"
                            } else {
                                "撤销这次作答"
                            },
                        )
                    }
                    if (onContinue != null) {
                        TextButton(
                            onClick = {
                                selfReportViewModel.recordedResultOrNull()?.let(onContinue)
                            },
                            modifier = Modifier.testTag("review_next_item"),
                        ) {
                            Text(
                                if (recorded.progress().status ==
                                    StudyReviewSessionStatus.COMPLETED
                                ) {
                                    "完成今日复习"
                                } else {
                                    "下一题"
                                },
                            )
                        }
                    }
                }
            } else {
                Spacer(Modifier.height(10.dp))
                ReviewStoredAnswerPanel(
                    storedAnswer = storedAnswer,
                    visible = false,
                )
                if (storedAnswerAvailability(storedAnswer) ==
                    StoredAnswerAvailability.UNTRUSTED &&
                    onSkipQuestion != null
                ) {
                    TextButton(
                        onClick = onSkipQuestion,
                        modifier = Modifier.testTag("review_skip_question"),
                    ) {
                        Text("先跳过这道题（不记录对错）")
                    }
                }
                if (selfReportViewModel.undoStatus == SelfReportUndoStatus.UNDONE) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "已回到本题，看过答案的记录仍保留。重新作答计为第 " +
                            "${selfReportViewModel.currentAttemptOrdinal()} 次作答。",
                        style = MaterialTheme.typography.bodySmall,
                        color = SmartColors.InkSecondary,
                        modifier = Modifier.testTag("review_undo_reattempt_notice"),
                    )
                }
            }
            if (tutorJudgedAvailable) {
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = onOpenTutorJudge,
                    modifier = Modifier.testTag("captured_review_tutor_judged_button"),
                ) {
                    Text("请求讲题（可选）")
                }
            }
        } else if (tutorJudgedAvailable) {
            Text(
                text = "先按自己的思路把它做一遍，然后去讲题：模型会出检查题，对错由本机核对，" +
                    "判定完成后自动记入复习并进入下一题。",
                modifier = Modifier.testTag("captured_review_tutor_judged_hint"),
                style = MaterialTheme.typography.bodyMedium,
                color = SmartColors.InkSecondary,
            )
            Spacer(Modifier.height(12.dp))
            PrimaryActionButton(
                text = "去讲题判定",
                onClick = onOpenTutorJudge,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .testTag("captured_review_tutor_judged_button"),
                contentDescription = "去讲题里完成这道题的检查",
            )
        } else {
            Text(
                text = "这道题需要模型出检查题才能判定对错，当前还没有可用的模型服务。" +
                    "先配置好模型再回来复习；这道题会一直留在今日复习里。",
                modifier = Modifier.testTag("captured_review_tutor_unavailable_notice"),
                style = MaterialTheme.typography.bodyMedium,
                color = SmartColors.InkSecondary,
            )
        }
        // KD-36：判定结算不再是无声的。没有形成判定 / 没有写成功时，这里如实说，
        // 并且给一个"重新判定"的显式出口（或上面那个"再讲一次"的入口）。
        settlementNotice?.let { notice ->
            Spacer(Modifier.height(12.dp))
            Text(
                text = notice.message,
                modifier = Modifier.testTag("captured_review_settlement_notice"),
                style = MaterialTheme.typography.bodyMedium,
                color = SmartColors.ErrorText,
            )
            onRetrySettlement?.let { retry ->
                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = retry,
                    modifier = Modifier.testTag("captured_review_settlement_retry"),
                ) {
                    Text(notice.retryLabel, color = SmartColors.JadeActive)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

/**
 * 无工件错题的 attempt 呈现身份：题版本 + 练习单元，并在有会话身份时并入会话。
 *
 * 消灭的失败：同一道题换了 problemRevision（题被改正/重新保存）或进入跨日的新复习会话时，
 * 若 continue 用旧 presentation，界面会恢复上一次的"已记录/可撤销"状态，甚至把旧 attempt
 * 的回执展示在新题上。会话身份为空时保持旧格式（未接线不编造）。
 */
internal fun capturedReviewPresentationId(
    entry: StudyCatalogEntry,
    sessionId: String?,
): String = buildString {
    append("presentation:captured-review:")
    append(entry.problemRevisionId)
    append(':')
    append(entry.practiceUnitId)
    sessionId?.takeIf(String::isNotBlank)?.let { session ->
        append(":session:")
        append(session)
    }
}
