package com.tingyun.smartmistakebook.feature.review

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tingyun.smartmistakebook.core.domain.KnowledgeQuizFeedbackResult
import com.tingyun.smartmistakebook.core.domain.KnowledgeReviewQueueEntry
import com.tingyun.smartmistakebook.core.domain.KnowledgeReviewSessionPlan
import com.tingyun.smartmistakebook.core.model.TutorAssessmentItem
import com.tingyun.smartmistakebook.core.ui.PaperDivider
import com.tingyun.smartmistakebook.core.ui.PrimaryActionButton
import com.tingyun.smartmistakebook.core.ui.RootPageColumn
import com.tingyun.smartmistakebook.core.ui.SafeMarkdownText
import com.tingyun.smartmistakebook.core.ui.SectionHeader
import com.tingyun.smartmistakebook.core.ui.SmartColors

/**
 * 知识点复习会话（spec dual-review-entry §3.3/§3.4）：一次"今日知识点复习"从首页进入后，
 * 沿 [KnowledgeReviewSessionPlan] 逐知识点：向模型要一道选择题 → 学生作答 → 判答 →
 * 回写掌握度 → 下一知识点，直到队列完成。dispatch（取题、恢复、回写）都作为 suspend 回调
 * 从 app 层注入——本屏不持有 repository/gateway，保持 feature/review 薄。
 *
 * 取题有两条路（AUD-02）：[loadQuiz] = 现场出题并落库（首次进入 / 学生明确重试）；
 * [restoreQuiz] = 进程重建后把**原题面**读回来（只读，不出网）。由 ViewModel 依持久化的
 * 加载状态决定走哪条：有题面可读回就绝不重出题。
 */
/**
 * "答案有问题"的一次预报告（PRD-02）。
 *
 * [selectedChoiceId] 为 null = 学生**还没提交**（中性入口：不提前泄露答案）；非 null =
 * 已作答后的定向反馈，目标证据可由 `(知识点, 题面, 选择)` 定位。
 */
data class KnowledgeQuizAnswerIssue(
    val knowledgeNodeId: String,
    val itemId: String,
    val selectedChoiceId: String?,
    val reason: String,
)

@Composable
fun KnowledgeReviewSessionScreen(
    plan: KnowledgeReviewSessionPlan,
    onBack: () -> Unit,
    restoreQuiz: suspend (KnowledgeReviewQueueEntry, Long) -> KnowledgeQuizLoadResult,
    loadQuiz: suspend (KnowledgeReviewQueueEntry, Long) -> KnowledgeQuizLoadResult,
    submitAnswer: suspend (
        requestId: String,
        knowledgeNodeId: String,
        correctChoiceId: String,
        selectedChoiceId: String,
        occurredAtEpochMillis: Long,
        conversationId: String,
    ) -> KnowledgeQuizFeedbackResult,
    onFinished: () -> Unit,
    /** PRD-02：答案有问题入口；默认 no-op，由集成轨（I1）接纠正/暂停计分的写入口。 */
    onReportAnswerIssue: (KnowledgeQuizAnswerIssue) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val viewModel: KnowledgeReviewSessionViewModel = viewModel(
        factory = KnowledgeReviewSessionViewModelFactory(plan),
    )
    if (viewModel.sessionRestoreFailed) {
        RootPageColumn(modifier = modifier.testTag("knowledge_review_restore_failed")) {
            KnowledgeSessionHeader(onBack = onBack)
            Spacer(Modifier.height(18.dp))
            Text(
                text = "这次复习的知识点顺序无法恢复，请返回后重新开始。已记录的学习结果不会丢失。",
                style = MaterialTheme.typography.bodyLarge,
                color = SmartColors.InkSecondary,
            )
            Spacer(Modifier.height(16.dp))
            PrimaryActionButton(
                text = "返回复习首页",
                onClick = onBack,
                modifier = Modifier.fillMaxWidth().testTag("knowledge_review_finish"),
            )
        }
        return
    }
    if (viewModel.queueSize == 0) {
        EmptyKnowledgeReviewScreen(onBack = onBack, modifier = modifier)
        return
    }
    // 进入节点且未取题时自动取题；continueToNext 清空后这里会为新节点再次取题。
    // 恢复态（LOADED 但内存无题）走读回，不出网（AUD-02）——分支规则在
    // [knowledgeQuizLoadPath]，它与"恢复不自动新出题"同一条，单独可测。
    LaunchedEffect(viewModel.currentIndex, viewModel.loadStatus, viewModel.submittedChoice) {
        if (viewModel.currentEntry == null) return@LaunchedEffect
        when (
            knowledgeQuizLoadPath(
                loadStatus = viewModel.loadStatus,
                hasItem = viewModel.currentItem != null,
                alreadySubmitted = viewModel.submittedChoice != null,
            )
        ) {
            KnowledgeQuizLoadPath.RESTORE -> viewModel.restoreCurrentQuiz(restoreQuiz)
            KnowledgeQuizLoadPath.GENERATE -> viewModel.loadCurrentQuiz(loadQuiz)
            KnowledgeQuizLoadPath.NONE -> Unit
        }
    }

    val entry = viewModel.currentEntry
    val item = viewModel.currentItem
    val progressIndex = viewModel.currentIndex.coerceIn(0, viewModel.queueSize - 1)
    RootPageColumn(modifier = modifier.testTag("knowledge_review_session_root")) {
        KnowledgeSessionHeader(onBack = onBack)
        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = {
                (progressIndex + 1).toFloat() / viewModel.queueSize.toFloat()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp),
            color = SmartColors.Jade,
            trackColor = SmartColors.Track,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "第 ${progressIndex + 1} / ${viewModel.queueSize} 个知识点",
            color = SmartColors.InkSecondary,
            style = MaterialTheme.typography.labelMedium,
        )
        PaperDivider(Modifier.padding(vertical = 18.dp))
        if (entry == null && viewModel.completed) {
            SectionHeader(title = "本次知识点复习已结束")
            Spacer(Modifier.height(10.dp))
            Text(
                text = "跳过的知识点没有记录本次结果，之后仍可复习。",
                style = MaterialTheme.typography.bodyMedium,
                color = SmartColors.InkSecondary,
            )
            Spacer(Modifier.height(18.dp))
            PrimaryActionButton(
                text = "完成今日知识点复习",
                onClick = onFinished,
                modifier = Modifier.fillMaxWidth().testTag("knowledge_review_finish"),
            )
            return@RootPageColumn
        }
        if (entry != null) {
            SectionHeader(title = entry.displayName)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "知识点复习 · 掌握度巩固",
                style = MaterialTheme.typography.bodySmall,
                color = SmartColors.InkSecondary,
            )
            Spacer(Modifier.height(16.dp))
        }
        when (viewModel.loadStatus) {
            KnowledgeQuizLoadStatus.LOADING -> Text(
                text = "正在为这个知识点出题…",
                modifier = Modifier.testTag("knowledge_review_loading"),
                color = SmartColors.InkSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
            KnowledgeQuizLoadStatus.FAILED -> KnowledgeQuizLoadFailure(
                message = "这道知识点暂时没有生成出来。",
                onRetry = {
                    viewModel.loadCurrentQuiz(loadQuiz)
                },
                onSkip = viewModel::skipCurrentNode,
                onBack = onBack,
            )
            // D-Q3：知识内容还在后台就位。与失败同一张卡片，但话说的是实话，
            // 且进程重建后会回到 IDLE 自动重取（那时内容多半已就位）。
            KnowledgeQuizLoadStatus.KNOWLEDGE_PREPARING -> KnowledgeQuizLoadFailure(
                message = "这个知识点的题目还在准备中，稍后再试一次。",
                onRetry = {
                    viewModel.loadCurrentQuiz(loadQuiz)
                },
                onSkip = viewModel::skipCurrentNode,
                onBack = onBack,
            )
            // 没有讲解材料 ≠ 失败：重试不会有不同结果，所以不给"重试出题"，
            // 只留"跳过这个知识点"和返回。
            KnowledgeQuizLoadStatus.NO_MATERIAL -> KnowledgeQuizLoadFailure(
                message = "这个知识点暂时没有可用的讲解材料，先跳过它吧。",
                onRetry = null,
                onSkip = viewModel::skipCurrentNode,
                onBack = onBack,
            )
            KnowledgeQuizLoadStatus.IDLE -> Unit
            KnowledgeQuizLoadStatus.LOADED -> if (item != null) {
                KnowledgeQuizContent(
                    viewModel = viewModel,
                    item = item,
                    knowledgeNodeId = entry?.knowledgeNodeId.orEmpty(),
                    submitAnswer = submitAnswer,
                    onReportAnswerIssue = onReportAnswerIssue,
                )
            }
        }
        // 已回写的节点（含进程重建后仅剩判决、题目待重观察的情形）渲染判决与前进按钮。
        viewModel.submittedChoice?.let { submittedChoice ->
            Spacer(Modifier.height(12.dp))
            viewModel.feedbackResult?.let { result ->
                KnowledgeQuizVerdict(result = result)
                Spacer(Modifier.height(10.dp))
            }
            if (viewModel.completed) {
                PrimaryActionButton(
                    text = "完成今日知识点复习",
                    onClick = onFinished,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("knowledge_review_finish"),
                )
            } else {
                OutlinedButton(
                    onClick = { viewModel.continueToNext() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("knowledge_review_next"),
                ) {
                    Text("下一知识点")
                }
            }
        }
    }
}

@Composable
private fun KnowledgeSessionHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.testTag("knowledge_review_back"),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "返回复习首页",
                tint = SmartColors.Ink,
            )
        }
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = "知识点复习",
                color = SmartColors.Ink,
                fontSize = 26.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun KnowledgeQuizContent(
    viewModel: KnowledgeReviewSessionViewModel,
    item: TutorAssessmentItem,
    knowledgeNodeId: String,
    submitAnswer: suspend (
        requestId: String,
        knowledgeNodeId: String,
        correctChoiceId: String,
        selectedChoiceId: String,
        occurredAtEpochMillis: Long,
        conversationId: String,
    ) -> KnowledgeQuizFeedbackResult,
    onReportAnswerIssue: (KnowledgeQuizAnswerIssue) -> Unit,
) {
    var issueDialogVisible by rememberSaveable { mutableStateOf(false) }
    var issueReason by rememberSaveable { mutableStateOf("") }
    SafeMarkdownText(
        markdown = item.stemMarkdown,
        color = SmartColors.Ink,
        style = MaterialTheme.typography.bodyLarge.copy(
            fontSize = 18.sp,
            lineHeight = 29.sp,
        ),
    )
    Spacer(Modifier.height(20.dp))
    item.choices.forEach { choice ->
        ReviewChoiceRow(
            choice = choice,
            isCorrect = item.evaluateChoice(choice.id).isCorrect,
            selectedChoice = viewModel.selectedChoice,
            submittedChoice = viewModel.submittedChoice,
            enabled = viewModel.submitStatus != KnowledgeQuizSubmitStatus.RECORDING,
            onSelect = viewModel::select,
            testTag = "knowledge_review_choice_${choice.id}",
        )
        Spacer(Modifier.height(10.dp))
    }
    if (viewModel.submittedChoice == null) {
        Spacer(Modifier.height(6.dp))
        PrimaryActionButton(
            text = when (viewModel.submitStatus) {
                KnowledgeQuizSubmitStatus.RECORDING -> "正在回写掌握度…"
                KnowledgeQuizSubmitStatus.FAILED -> "重新提交答案"
                else -> "提交答案"
            },
            onClick = {
                viewModel.submitAnswer(
                    occurredAtEpochMillis = System.currentTimeMillis(),
                    submit = submitAnswer,
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("knowledge_review_submit"),
            enabled = viewModel.selectedChoice != null &&
                viewModel.submitStatus != KnowledgeQuizSubmitStatus.RECORDING,
            contentDescription = if (viewModel.selectedChoice == null) {
                "请先选择一个答案"
            } else {
                "提交当前选择并回写掌握度"
            },
        )
    }
    // PRD-02："答案有问题"入口。未作答时用中性文案（不提前泄露答案）；已作答时带上
    // 真实选择，供纠错写入口定位目标证据。
    TextButton(
        onClick = { issueDialogVisible = true },
        modifier = Modifier
            .fillMaxWidth()
            .testTag(
                if (viewModel.submittedChoice == null) {
                    "knowledge_review_report_issue_neutral"
                } else {
                    "knowledge_review_report_issue"
                },
            ),
    ) {
        Text(
            text = if (viewModel.submittedChoice == null) {
                "这道题的答案有问题？"
            } else {
                "这道题的答案有问题，反馈一下"
            },
            color = SmartColors.InkSecondary,
            style = MaterialTheme.typography.labelMedium,
        )
    }
    if (issueDialogVisible) {
        AlertDialog(
            onDismissRequest = { issueDialogVisible = false },
            title = { Text("答案有问题") },
            text = {
                Column {
                    Text(
                        text = if (viewModel.submittedChoice == null) {
                            "先记下这道题的题面问题，本题不按对错计分；你的答案仍然会保留。"
                        } else {
                            "说明这道题的答案/选项哪里有问题；已记录的作答不会被改写。"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = SmartColors.InkSecondary,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = issueReason,
                        onValueChange = { issueReason = it },
                        label = { Text("问题说明（可留空）") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("knowledge_review_issue_reason"),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onReportAnswerIssue(
                            KnowledgeQuizAnswerIssue(
                                knowledgeNodeId = knowledgeNodeId,
                                itemId = item.id,
                                selectedChoiceId = viewModel.submittedChoice,
                                reason = issueReason.ifBlank { "答案/选项有问题" },
                            ),
                        )
                        issueDialogVisible = false
                        issueReason = ""
                    },
                    modifier = Modifier.testTag("knowledge_review_issue_confirm"),
                ) {
                    Text(if (viewModel.submittedChoice == null) "记下，先不计分" else "提交反馈")
                }
            },
            dismissButton = {
                TextButton(onClick = { issueDialogVisible = false }) {
                    Text("取消")
                }
            },
        )
    }
}

/**
 * 掌握度回写被门控拒收时的学生可读说明（纯函数，便于单测）：
 * 拒收原因是内部枚举名，面向学生必须翻译成人话。
 *
 * [SAME_KC_IN_COOLDOWN] 一句（AUD-11 ① 修正）：冷却自 2026-10-16 起**只约束模型通道**
 * （`KnowledgeEvidenceWriter` 对客观作答通道不取冷却年龄），所以本通道的新写不会再以这个
 * 理由被拒；这句只剩一种到达路径——**旧版本落进 SavedStateHandle 的拒因**
 *（`KnowledgeReviewSessionViewModel` 会把 `KnowledgeQuizFeedbackResult` 序列化保存）。
 * 改前文案是"这个知识点刚刚复习过"：当挡路的是**模型**的写、学生根本没复习过时它是错的
 *（报告 §2.2 实测）。现文案只说本地能确证的事实——该知识点上刚有一笔更新的掌握记录。
 */
internal fun knowledgeQuizRejectedReasonText(reason: String): String = when (reason) {
    "INTENT_BELOW_ROUTE_CONFIDENCE" -> "这次的作答意图还不够明确，先只作为观察"
    "EVIDENCE_BELOW_CONFIDENCE" -> "这次的把握还不够，先只作为观察"
    "KNOWLEDGE_NODE_NOT_ANCHORED" -> "这道题还没有绑定到具体知识点，先只作为观察"
    "MASTERED_WITHOUT_EVIDENCE_ANCHOR",
    "POSITIVE_WITHOUT_EVIDENCE_ANCHOR",
    -> "这次没有找到可核对的依据，先只作为观察"
    "SAME_KC_IN_COOLDOWN" -> "这个知识点上刚有一笔更新的掌握记录，这次先只作为观察"
    "CONVERSATION_QUOTA_EXHAUSTED" -> "本次复习的观察记录已经够了，这次先不计入"
    "LEARNER_WINDOW_QUOTA_EXHAUSTED" -> "短时间内记录得比较多，这次先只作为观察"
    "ATTENTION_BELOW_FLOOR" -> "这次作答投入得还不够，先只作为观察"
    "CONTRADICTORY_SEMANTICS" -> "这次的判断前后不太一致，先只作为观察"
    // PRD-02：键信任/纠错门的新拒因——都**不**声称已计入掌握度。
    "ANSWER_KEY_UNVERIFIED" -> "这道题的答案还没有经过独立核对，这次只记录你的选择，不计入掌握度"
    "ANSWER_KEY_VERSION_STALE" -> "这道题的答案刚刚更正过；旧键下的这次判分不再计入"
    "ANSWER_KEY_DISPUTED" -> "这道题的答案已标记为有争议，暂停计分"
    "EVIDENCE_IDENTITY_STICKY" -> "这次作答没有写入掌握度记录（同一次作答已有更早的观察记录）"
    else -> "这次先只作为观察记录"
}

@Composable
private fun KnowledgeQuizVerdict(result: KnowledgeQuizFeedbackResult) {
    val correct = result.isCorrect
    androidx.compose.material3.Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("knowledge_review_verdict"),
        color = if (correct) SmartColors.JadeSoft else SmartColors.Paper,
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (correct) SmartColors.Jade else SmartColors.ErrorWarm,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = if (correct) SmartColors.JadeDark else SmartColors.ErrorWarm,
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = if (correct) "回答正确" else "这次选择还差一步",
                    color = if (correct) SmartColors.JadeDark else SmartColors.ErrorWarm,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                )
                result.rejectedReason?.let { reason ->
                    Text(
                        text = knowledgeQuizRejectedReasonText(reason),
                        style = MaterialTheme.typography.bodySmall,
                        color = SmartColors.InkSecondary,
                    )
                }
            }
        }
    }
}

/**
 * 取不到题时的卡片：话说的是**这一种**原因（[message]），出路随之不同——
 * [onRetry] 为 null 表示"重试不会有不同结果"（比如没有讲解材料），此时只给跳过与返回。
 */
@Composable
private fun KnowledgeQuizLoadFailure(
    message: String,
    onRetry: (() -> Unit)?,
    onSkip: () -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = message,
            modifier = Modifier.testTag("knowledge_review_load_failed"),
            color = SmartColors.InkSecondary,
            style = MaterialTheme.typography.bodyLarge,
        )
        if (onRetry != null) {
            Spacer(Modifier.height(16.dp))
            PrimaryActionButton(
                text = "重试出题",
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth().testTag("knowledge_review_retry"),
            )
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onSkip,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("knowledge_review_skip"),
        ) {
            Text("跳过这个知识点")
        }
        Text(
            text = "跳过不会记录本次结果，这个知识点保持到期，之后还会再遇到。",
            modifier = Modifier.padding(top = 6.dp),
            color = SmartColors.InkSecondary,
            style = MaterialTheme.typography.labelSmall,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("返回复习首页")
        }
    }
}

@Composable
private fun EmptyKnowledgeReviewScreen(
    onBack: () -> Unit,
    modifier: Modifier,
) {
    RootPageColumn(modifier = modifier.testTag("knowledge_review_empty")) {
        KnowledgeSessionHeader(onBack = onBack)
        Spacer(Modifier.height(12.dp))
        PaperDivider(Modifier.padding(vertical = 18.dp))
        Text(
            text = "今天没有需要复习的知识点——错题里涉及的知识点都已掌握或尚未到期。",
            color = SmartColors.InkSecondary,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
