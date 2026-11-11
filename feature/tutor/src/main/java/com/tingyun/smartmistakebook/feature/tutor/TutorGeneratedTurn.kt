package com.tingyun.smartmistakebook.feature.tutor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import com.tingyun.smartmistakebook.core.ui.ErrorText
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.tingyun.smartmistakebook.core.ui.ClaudeTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tingyun.smartmistakebook.core.model.TutorPlanOutput
import com.tingyun.smartmistakebook.core.model.TutorMoveType
import com.tingyun.smartmistakebook.core.model.TutorPlanInput
import com.tingyun.smartmistakebook.core.model.TutorSuggestedMove
import com.tingyun.smartmistakebook.core.domain.TutorTurnResponse
import com.tingyun.smartmistakebook.core.ui.GeneratedFiguresSection
import com.tingyun.smartmistakebook.core.ui.InkSecondary
import com.tingyun.smartmistakebook.core.ui.JadeActive
import com.tingyun.smartmistakebook.core.ui.OutlineActionChip
import com.tingyun.smartmistakebook.core.ui.PrimaryActionButton
import com.tingyun.smartmistakebook.core.ui.TutorReplyMarkdown
import com.tingyun.smartmistakebook.core.ui.TutorToolTraceLine
import com.tingyun.smartmistakebook.core.ui.rememberTutorToolTraceDisplay

@Composable
internal fun TutorTurnContent(
    output: TutorPlanOutput,
    modifier: Modifier = Modifier,
    /**
     * 本轮讲解的正文（K1a：消息行是唯一文本权威）。null = 这一轮没有消息行，**不回落账本**：
     * 回落会让同一条文本有两个去处（旧行读账本、新行读消息行），两处一漂就是"学生看到的"
     * 与"库里记下的"不是同一句话。结构化载荷（完整讲解 / 另一种方法 / 选择题）不受影响。
     */
    openingMarkdown: String?,
    /**
     * 这一轮的工具痕迹（B1，同样来自消息行）：加粗灰色小字内联、点开可看详情（含被拒理由）。
     * null = 这一轮没有发起工具调用（或旧行没有痕迹），整行不渲染。
     */
    toolTraceJson: String? = null,
    /**
     * A2（4B）：本轮讲解消息上的**持久配图引用**（`tutor_message_source_asset` 的资产 id）。
     * 非空且给了 [figureAssetResolver] 时，在讲解正文下方渲染可展开的生成图卡。
     */
    figureAssetIds: List<String> = emptyList(),
    /** 生成图资产 id → 本地 URI（本地读回，绝不出网）；null = 不渲染配图卡。 */
    figureAssetResolver: (suspend (String) -> String?)? = null,
    response: TutorTurnResponse? = null,
    solutionRevealPreviewed: Boolean = false,
    interactionEnabled: Boolean = true,
    splitChoiceFeedback: Boolean = false,
    interactionBusy: Boolean = false,
    interactionError: String? = null,
    onSubmitChoice: (String) -> Unit = {},
    onRequestHint: (() -> Unit)? = null,
    onContinue: (TutorMoveType) -> Unit = {},
    onRevealSolution: () -> Unit = {},
    onRestartCycle: () -> Unit = {},
    solutionBottomModifier: Modifier = Modifier,
    /** 讲解正文那条动作条（A2）的标签。 */
    planActionTestTag: String = "captured_tutor_plan_actions",
    showCopyAction: Boolean = true,
    savedDraftChoiceId: String? = null,
    onDraftChoiceChanged: ((String?) -> Unit)? = null,
) {
    val plan = output.plan
    val item = plan.diagnosticItem
    val laterSections = listOf(plan.solutionMarkdown, plan.alternateMethodMarkdown) +
        item?.choices.orEmpty().mapNotNull { it.feedbackMarkdown }
    val openingAssets = com.tingyun.smartmistakebook.core.ui.tutorOpeningFigureAssets(
        openingMarkdown.orEmpty(), figureAssetIds, laterSections)
    // A pending click is process-local UI state. Persisting it without the in-flight coroutine can
    // restore a permanently disabled choice after activity recreation.
    var pendingChoiceId by remember(
        output.sessionId,
        output.questionDocumentId,
        output.draftRevisionNumber,
        output.cycleOrdinal,
        output.turnOrdinal,
    ) {
        mutableStateOf<String?>(null)
    }
    var draftChoiceId by rememberSaveable(
        output.sessionId, output.questionDocumentId, output.draftRevisionNumber,
        output.cycleOrdinal, output.turnOrdinal,
    ) { mutableStateOf(savedDraftChoiceId) }
    val choiceResponse = response?.takeIf(TutorTurnResponse::hasChoicePayload)
    val submitted = choiceResponse != null
    val selectedId = choiceResponse?.selectedChoiceId ?: pendingChoiceId ?: draftChoiceId
    val showSolution = response?.solutionRevealed == true || solutionRevealPreviewed
    val explanationOnlyAlternateVisible =
        response?.requestedMove == TutorMoveType.CHANGE_REPRESENTATION

    LaunchedEffect(interactionError, submitted) {
        if (pendingChoiceId != null && (submitted || interactionError != null)) {
            pendingChoiceId = null
        }
    }

    val questionContent: @Composable () -> Unit = {
        item?.let { interaction ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = com.tingyun.smartmistakebook.core.ui.SmartColors.QuestionSurface,
                border = BorderStroke(.5.dp, com.tingyun.smartmistakebook.core.ui.SmartColors.Divider),
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("想一想", style = MaterialTheme.typography.labelLarge,
                        color = com.tingyun.smartmistakebook.core.ui.SmartColors.ReadingAccent)
                    TutorIllustratedSection(interaction.stemMarkdown, figureAssetIds, figureAssetResolver, toolTraceJson)
                    interaction.promptMarkdown?.let { TutorIllustratedSection(it, figureAssetIds, figureAssetResolver, toolTraceJson) }
                    if (!submitted) Text("选择后点击提交", color = InkSecondary,
                        style = MaterialTheme.typography.bodySmall)
                    interaction.choices.forEach { choice ->
                        val isSelected = selectedId == choice.id
                        val choicesEnabled = interactionEnabled && !submitted && !interactionBusy && pendingChoiceId == null
                        val state = when {
                            submitted && isSelected -> "已提交"
                            pendingChoiceId == choice.id -> "正在提交"
                            submitted -> "未选择"
                            !choicesEnabled -> "暂不可操作"
                            else -> "选择答案"
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) com.tingyun.smartmistakebook.core.ui.JadeSoft
                                else com.tingyun.smartmistakebook.core.ui.SmartColors.ContentSurface.copy(alpha = .7f),
                        ) {
                            Row(
                                Modifier.fillMaxWidth().defaultMinSize(minHeight = 52.dp)
                                    .selectable(selected = isSelected, enabled = choicesEnabled, role = Role.RadioButton,
                                        onClick = {
                                            if (pendingChoiceId == null && !submitted && !interactionBusy) {
                                                draftChoiceId = choice.id
                                                onDraftChoiceChanged?.invoke(choice.id)
                                            }
                                        })
                                    .semantics {
                                        stateDescription = state
                                        contentDescription = "选择答案：${choice.markdown}"
                                    }
                                    .testTag("captured_tutor_choice_${choice.id}")
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                RadioButton(selected = isSelected, onClick = null,
                                    colors = RadioButtonDefaults.colors(selectedColor = JadeActive,
                                        unselectedColor = InkSecondary), modifier = Modifier.size(24.dp))
                                TutorReplyMarkdown(choice.markdown, Modifier.weight(1f))
                            }
                        }
                    }
                    PrimaryActionButton(
                        text = when {
                            submitted -> "已提交"
                            pendingChoiceId != null || interactionBusy -> "正在提交…"
                            else -> "提交答案"
                        },
                        enabled = interactionEnabled && !submitted && !interactionBusy &&
                            pendingChoiceId == null && draftChoiceId in interaction.choices.map { it.id },
                        onClick = {
                            val choiceId = draftChoiceId
                            if (interactionEnabled && !submitted && !interactionBusy && pendingChoiceId == null &&
                                interaction.choices.any { it.id == choiceId }) {
                                pendingChoiceId = choiceId
                                onSubmitChoice(requireNotNull(choiceId))
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("captured_tutor_submit_choice"),
                    )
                    if (!submitted && onRequestHint != null) {
                        TextButton(onClick = onRequestHint,
                            enabled = interactionEnabled && !interactionBusy && pendingChoiceId == null,
                            modifier = Modifier.testTag("captured_tutor_request_hint")) { Text("给我一点提示") }
                    }
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("captured_tutor_model_ready"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        openingMarkdown?.let { markdown ->
            com.tingyun.smartmistakebook.core.ui.TutorMessageDocument(markdown,
                figureAssetIds = openingAssets, resolveFigure = figureAssetResolver,
                figureCaptions = com.tingyun.smartmistakebook.core.model.tutorFigureCaptions(toolTraceJson),
                inlineQuestion = questionContent.takeIf { item != null })
            // 动作条（A2）：讲解这一轮的正文与讲题侧的回复共用同一条——「复制」复制的是消息行
            // 里的原文 markdown（渲染后的文本会丢格式）。
            if (showCopyAction) TutorReplyActionBar(
                shape = tutorReplyActionShape(bodyMarkdown = markdown),
                testTagPrefix = planActionTestTag,
            )
        }
        // B1：这一轮查阅了什么，单独一行小字（不混进思考卡），点开看明细。
        TutorToolTraceLine(display = rememberTutorToolTraceDisplay(toolTraceJson))
        // A2：本轮生成的配图（消息行上的持久引用 → 本地读回）。
        if (openingMarkdown == null) figureAssetResolver?.let { resolver ->
            openingAssets.takeIf { it.isNotEmpty() }?.let { assetIds ->
                GeneratedFiguresSection(assetIds = assetIds, resolveAsset = resolver)
            }
        }
        if (openingMarkdown == null) questionContent()
        if (item == null) {
            TutorMoveButtons(
                moves = plan.suggestedMoves.filter { move ->
                    move.type == TutorMoveType.CHANGE_REPRESENTATION ||
                        move.type == TutorMoveType.REVEAL_SOLUTION
                },
                requestedMove = response?.requestedMove,
                showSolution = showSolution,
                alternateVisible = explanationOnlyAlternateVisible,
                interactionBusy = interactionBusy,
                interactionEnabled = interactionEnabled,
                canContinue = true,
                explanationOnly = true,
                onContinue = onContinue,
                onRevealSolution = onRevealSolution,
            )
            if (explanationOnlyAlternateVisible) {
                Text(
                    text = "另一种方法",
                    color = InkSecondary,
                    style = MaterialTheme.typography.labelLarge,
                )
                TutorIllustratedSection(plan.alternateMethodMarkdown, figureAssetIds, figureAssetResolver, toolTraceJson,
                    modifier = Modifier.testTag("captured_tutor_alternate_method"))
            }
            if (showSolution) {
                Text(
                    text = "完整讲解",
                    color = InkSecondary,
                    style = MaterialTheme.typography.labelLarge,
                )
                TutorIllustratedSection(plan.solutionMarkdown, figureAssetIds, figureAssetResolver, toolTraceJson,
                    modifier = Modifier.testTag("captured_tutor_solution"))
                Box(
                    modifier = Modifier
                        .size(1.dp)
                        .then(solutionBottomModifier),
                )
            }
        } else if (submitted && !splitChoiceFeedback) {
            val submittedResponse = requireNotNull(choiceResponse)
            Text(
                text = if (submittedResponse.selectionWasCorrect == true) {
                    "判断正确"
                } else {
                    "再看一下这一步"
                },
                color = if (submittedResponse.selectionWasCorrect == true) JadeActive else ErrorText,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            TutorIllustratedSection(requireNotNull(submittedResponse.feedbackMarkdown),
                figureAssetIds, figureAssetResolver, toolTraceJson)
            TutorMoveButtons(
                moves = plan.suggestedMoves,
                requestedMove = submittedResponse.requestedMove,
                showSolution = showSolution,
                alternateVisible = false,
                interactionBusy = interactionBusy,
                interactionEnabled = interactionEnabled,
                canContinue = output.turnOrdinal < TutorPlanInput.MAX_TURNS,
                explanationOnly = false,
                onContinue = onContinue,
                onRevealSolution = onRevealSolution,
            )
            if (showSolution) {
                Text(
                    text = "规范讲解",
                    color = InkSecondary,
                    style = MaterialTheme.typography.labelLarge,
                )
                TutorIllustratedSection(plan.solutionMarkdown, figureAssetIds, figureAssetResolver, toolTraceJson)
                Text(
                    text = "另一种方法",
                    color = InkSecondary,
                    style = MaterialTheme.typography.labelLarge,
                )
                TutorIllustratedSection(plan.alternateMethodMarkdown, figureAssetIds, figureAssetResolver, toolTraceJson)
                Box(
                    modifier = Modifier
                        .size(1.dp)
                        .then(solutionBottomModifier),
                )
            }
            if (output.turnOrdinal == TutorPlanInput.MAX_TURNS) {
                PrimaryActionButton(
                    text = "继续讲这道题",
                    onClick = onRestartCycle,
                    enabled = interactionEnabled && !interactionBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("captured_tutor_restart_cycle"),
                    contentDescription = "保留本轮学习记忆并开始新的讲题轮次",
                )
            }
        }
        interactionError?.let { message ->
            Text(
                text = message,
                color = ErrorText,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("captured_tutor_interaction_error"),
            )
        }
    }
}

@Composable
internal fun TutorChoiceFeedbackContent(
    output: TutorPlanOutput,
    response: TutorTurnResponse,
    solutionRevealPreviewed: Boolean = false,
    interactionEnabled: Boolean,
    interactionBusy: Boolean = false,
    interactionError: String? = null,
    onContinue: (TutorMoveType) -> Unit = {},
    onRevealSolution: () -> Unit = {},
    onRestartCycle: () -> Unit = {},
    solutionBottomModifier: Modifier = Modifier,
    modifier: Modifier = Modifier,
    figureAssetIds: List<String> = emptyList(),
    figureAssetResolver: (suspend (String) -> String?)? = null,
    toolTraceJson: String? = null,
) {
    require(response.hasChoicePayload)
    val plan = output.plan
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("captured_tutor_choice_feedback_${response.cycleOrdinal}_${response.turnOrdinal}"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = if (response.selectionWasCorrect == true) {
                "判断正确"
            } else {
                "再看一下这一步"
            },
            color = if (response.selectionWasCorrect == true) JadeActive else ErrorText,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        TutorIllustratedSection(requireNotNull(response.feedbackMarkdown),
            figureAssetIds, figureAssetResolver, toolTraceJson)
        TutorMoveButtons(
            moves = plan.suggestedMoves,
            requestedMove = response.requestedMove,
            showSolution = response.solutionRevealed || solutionRevealPreviewed,
            alternateVisible = false,
            interactionBusy = interactionBusy,
            interactionEnabled = interactionEnabled,
            canContinue = output.turnOrdinal < TutorPlanInput.MAX_TURNS,
            explanationOnly = false,
            onContinue = onContinue,
            onRevealSolution = onRevealSolution,
        )
        if (response.solutionRevealed || solutionRevealPreviewed) {
            Text(
                text = "规范讲解",
                color = InkSecondary,
                style = MaterialTheme.typography.labelLarge,
            )
            TutorIllustratedSection(plan.solutionMarkdown, figureAssetIds, figureAssetResolver, toolTraceJson)
            Text(
                text = "另一种方法",
                color = InkSecondary,
                style = MaterialTheme.typography.labelLarge,
            )
            TutorIllustratedSection(plan.alternateMethodMarkdown, figureAssetIds, figureAssetResolver, toolTraceJson)
            Box(
                modifier = Modifier
                    .size(1.dp)
                    .then(solutionBottomModifier),
            )
        }
        if (output.turnOrdinal == TutorPlanInput.MAX_TURNS) {
            PrimaryActionButton(
                text = "继续讲这道题",
                onClick = onRestartCycle,
                enabled = interactionEnabled && !interactionBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("captured_tutor_restart_cycle"),
                contentDescription = "保留本轮学习记忆并开始新的讲题轮次",
            )
        }
        interactionError?.let { message ->
            Text(
                text = message,
                color = ErrorText,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("captured_tutor_interaction_error"),
            )
        }
    }
}

@Composable
private fun TutorMoveButtons(
    moves: List<TutorSuggestedMove>,
    requestedMove: TutorMoveType?,
    showSolution: Boolean,
    alternateVisible: Boolean,
    interactionBusy: Boolean,
    interactionEnabled: Boolean,
    canContinue: Boolean,
    explanationOnly: Boolean,
    onContinue: (TutorMoveType) -> Unit,
    onRevealSolution: () -> Unit,
) {
    val hasRevealMove = moves.any { it.type == TutorMoveType.REVEAL_SOLUTION }
    val visibleMoves = if (hasRevealMove) moves else moves.take(2)
    if (visibleMoves.isNotEmpty()) {
        Text(
            text = "接下来想怎么看？",
            color = InkSecondary,
            style = MaterialTheme.typography.bodySmall,
        )
    }
    visibleMoves.forEach { move ->
        val isReveal = move.type == TutorMoveType.REVEAL_SOLUTION
        val enabled = interactionEnabled && !interactionBusy && if (isReveal) {
            !showSolution && (explanationOnly || requestedMove == null)
        } else {
            canContinue && requestedMove == null && !showSolution
        }
        val tag = when {
            explanationOnly && move.type == TutorMoveType.CHANGE_REPRESENTATION ->
                "captured_tutor_show_alternate"
            explanationOnly && isReveal -> "captured_tutor_reveal_without_choice"
            else -> "captured_tutor_move_${move.id}"
        }
        OutlineActionChip(
            text = move.label,
            onClick = {
                if (isReveal) onRevealSolution() else onContinue(move.type)
            },
            enabled = enabled,
            selected = (isReveal && showSolution) ||
                (move.type == TutorMoveType.CHANGE_REPRESENTATION && alternateVisible),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(tag),
        )
    }
    if (!hasRevealMove && !showSolution) {
        TextButton(
            onClick = onRevealSolution,
            enabled = interactionEnabled &&
                !interactionBusy && (explanationOnly || requestedMove == null),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(
                    if (explanationOnly) {
                        "captured_tutor_reveal_without_choice"
                    } else {
                        "captured_tutor_reveal_fallback"
                    },
                ),
        ) {
            Text("查看完整讲解")
        }
    }
}

@Composable
private fun TutorIllustratedSection(
    markdown: String,
    figureAssetIds: List<String>,
    resolver: (suspend (String) -> String?)?,
    traceJson: String?,
    modifier: Modifier = Modifier,
) {
    val referenced = com.tingyun.smartmistakebook.core.ui.tutorMessageFigureReferences(markdown).toSet()
    com.tingyun.smartmistakebook.core.ui.TutorMessageDocument(
        markdown, figureAssetIds.filter { it in referenced }, resolver, modifier,
        figureCaptions = com.tingyun.smartmistakebook.core.model.tutorFigureCaptions(traceJson))
}
