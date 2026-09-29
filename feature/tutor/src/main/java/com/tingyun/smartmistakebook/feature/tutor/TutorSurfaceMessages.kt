package com.tingyun.smartmistakebook.feature.tutor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.tingyun.smartmistakebook.core.domain.LobbyMessageImageIntake
import com.tingyun.smartmistakebook.core.domain.TutorMessage
import com.tingyun.smartmistakebook.core.domain.TutorMessageRole
import com.tingyun.smartmistakebook.core.domain.TutorMessageStatus
import com.tingyun.smartmistakebook.core.model.ModelFailureCode
import com.tingyun.smartmistakebook.core.model.requiresModelSettings
import com.tingyun.smartmistakebook.core.ui.Ink
import com.tingyun.smartmistakebook.core.ui.InkSecondary
import com.tingyun.smartmistakebook.core.ui.JadeActive
import com.tingyun.smartmistakebook.core.ui.JadeSoft
import com.tingyun.smartmistakebook.core.ui.OutlineActionChip
import com.tingyun.smartmistakebook.core.ui.ThinkingCollapsibleCard
import com.tingyun.smartmistakebook.core.ui.TutorToolTraceLine
import com.tingyun.smartmistakebook.core.ui.rememberTutorToolTraceDisplay
import kotlinx.coroutines.delay

/**
 * 交互面唯一的失败卡出口文案（三套重试文案 ——「重新发送」「重试」「重新生成」—— 统一成这一套）。
 *
 * 消灭的失败：同一件"再试一次"的事在三个入口有三种叫法，学生换一个入口就要重新猜哪个按钮是
 * 同一个意思；文案也不可能三处同时改对。
 */
internal const val TUTOR_SURFACE_RETRY_LABEL = "重试"
internal const val TUTOR_SURFACE_OPEN_SETTINGS_LABEL = "检查模型设置"

/**
 * 消息动作条（A2）的三套文案。停止与"已停止"是学生主动做出的结果，不是失败——它写在灰色的
 * 一行字上，而不是失败卡的红字里。
 */
internal const val TUTOR_SURFACE_STOP_LABEL = "停止"
internal const val TUTOR_SURFACE_STOPPED_LABEL = "已停止"
internal const val TUTOR_SURFACE_COPY_LABEL = "复制"
internal const val TUTOR_SURFACE_COPIED_LABEL = "已复制"

/**
 * 助手消息的动作条（A2：停止 / 重试 / 复制），三个入口共用这一条。
 *
 * 消灭的失败：这三个动作此前散在三处、各有各的文案与出口（失败卡上的"重新发送"、讲题侧的
 * "重试"、生成中根本没有任何出口），学生得先猜哪个按钮是同一个意思。现在它们是同一条动作条，
 * 出现条件只由"这一轮有没有那个动作"决定。
 *
 * 复制走 Compose 剪贴板（[LocalClipboardManager]），复制的是消息行里的原始 markdown
 * （不是渲染后的文本）——学生粘到别处时格式不丢。
 */
@Composable
internal fun TutorMessageActionBar(
    modifier: Modifier = Modifier,
    /** 可复制的内容（助手正文原文）；null = 这条消息没有可复制的正文。 */
    copyText: String? = null,
    /** 这一轮还能不能重试（重试 = 重发同类请求）；null = 不给重试出口。 */
    retryLabel: String? = null,
    onRetry: () -> Unit = {},
    /** 正在生成时才给的停止出口；null = 这一条不是"正在生成"。 */
    stopLabel: String? = null,
    onStop: () -> Unit = {},
    /** 学生已经停止过这一轮：灰色的「已停止」，不是失败卡。 */
    stoppedLabel: String? = null,
    testTagPrefix: String = "tutor_message_actions",
) {
    if (copyText == null && retryLabel == null && stopLabel == null && stoppedLabel == null) return
    val clipboard = LocalClipboardManager.current
    var copied by remember(copyText) { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(TUTOR_COPIED_FEEDBACK_MILLIS)
            copied = false
        }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTagPrefix),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        stoppedLabel?.let { label ->
            Text(
                text = label,
                modifier = Modifier.testTag("${testTagPrefix}_stopped"),
                color = InkSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        copyText?.let { text ->
            OutlineActionChip(
                text = if (copied) TUTOR_SURFACE_COPIED_LABEL else TUTOR_SURFACE_COPY_LABEL,
                onClick = {
                    clipboard.setText(AnnotatedString(text))
                    copied = true
                },
                modifier = Modifier.testTag("${testTagPrefix}_copy"),
            )
        }
        stopLabel?.let { label ->
            OutlineActionChip(
                text = label,
                onClick = onStop,
                modifier = Modifier.testTag("${testTagPrefix}_stop"),
            )
        }
        retryLabel?.let { label ->
            OutlineActionChip(
                text = label,
                onClick = onRetry,
                modifier = Modifier.testTag("${testTagPrefix}_retry"),
            )
        }
    }
}

/** 「已复制」这一行小字停留多久：够看见，不干扰下一次点击。 */
private const val TUTOR_COPIED_FEEDBACK_MILLIS = 1_600L

/**
 * 这条助手消息的「重试」目标：**任何状态**都能重试**最后一轮**（成功 / 已停止 / 失败共用
 * 同一套），重试 = 重发它回复的那条学生消息（同一逻辑操作的下一次尝试）。
 *
 * 只在它是会话最后一条消息时给出口：更早的轮次重发会把更晚的轮次塞进这次请求的上下文，
 * 对话顺序会错乱——那时按钮不该出现（出现就是假按钮）。
 */
internal fun TutorMessage.retryTargetOrNull(
    conversationMessages: List<TutorMessage>,
): TutorMessage? {
    if (role != TutorMessageRole.ASSISTANT) return null
    if (conversationMessages.lastOrNull()?.messageId != messageId) return null
    val repliedTo = replyToMessageId ?: return null
    return conversationMessages.firstOrNull { candidate ->
        candidate.messageId == repliedTo && candidate.role == TutorMessageRole.STUDENT
    }
}

/**
 * 一条助手消息的动作条**形状**（A2）：三个入口共用同一个判据。
 *
 * 消灭的失败：动作条此前只有智能体栏那一个渲染器有，讲题侧走的是另一条分支——同一条助手回复
 * 在讲题入口里没有「复制」，生成中那一条也没有任何出口（A2 只在智能体栏成立）。把"该给哪些
 * 动作"提成一个纯函数之后，两个渲染器读同一份判据，第三条渲染器不会再长出第三套动作规则。
 *
 * @param copyText 可复制的**原文**（消息行的 markdown，不是渲染后的文本）；null = 这一条没有
 *   可复制的正文（失败卡、已停止：正文本来就不是模型答的那段话）。
 * @param stopped 学生主动停止过这一轮：一行灰字「已停止」，不是失败卡的红字。
 * @param retryable 这一轮还能重试（重试 = 重发它回复的那条学生消息）。
 */
internal data class TutorReplyActionShape(
    val copyText: String? = null,
    val stoppedLabel: String? = null,
    val retryLabel: String? = null,
) {
    /** 一个动作都没有时整条不渲染。 */
    val isEmpty: Boolean
        get() = copyText == null && stoppedLabel == null && retryLabel == null
}

internal fun tutorReplyActionShape(
    bodyMarkdown: String?,
    stopped: Boolean = false,
    retryable: Boolean = false,
): TutorReplyActionShape = TutorReplyActionShape(
    copyText = bodyMarkdown?.takeIf(String::isNotBlank),
    stoppedLabel = TUTOR_SURFACE_STOPPED_LABEL.takeIf { stopped },
    retryLabel = TUTOR_SURFACE_RETRY_LABEL.takeIf { retryable },
)

/** 形状 → 动作条（唯一的渲染点，三个入口都走它）。 */
@Composable
internal fun TutorReplyActionBar(
    shape: TutorReplyActionShape,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {},
    testTagPrefix: String = "tutor_message_actions",
) {
    TutorMessageActionBar(
        modifier = modifier,
        copyText = shape.copyText,
        retryLabel = shape.retryLabel,
        onRetry = onRetry,
        stoppedLabel = shape.stoppedLabel,
        testTagPrefix = testTagPrefix,
    )
}

/** 助手正文就是「复制」复制的原文（消息体的非空由 TutorMessage 的不变量保证）。 */

/**
 * 助手回复的一行状态（生成中 / 已暂停 / 旧配置未完成）：正文之外只有这一行。
 *
 * @param textTestTag 文字行的标签（旧行为里被测试按住的那些）；null = 不挂标签。
 * @param spinnerTestTag 转圈本身的标签（`tutor_chat_reply_progress` 就是它）。
 * @param spinning 这一行表示"正在动"还是"停在这里"——暂停与旧配置未完成不该转圈。
 */
internal data class TutorSurfaceProgress(
    val text: String,
    val textTestTag: String? = null,
    val spinnerTestTag: String? = null,
    val spinning: Boolean = true,
)

/**
 * 助手回复的失败态（唯一形状）：一句正文 + 可选原因 + 至多两个出口。
 *
 * [actions] 的文案来自 [TUTOR_SURFACE_RETRY_LABEL] / [TUTOR_SURFACE_OPEN_SETTINGS_LABEL]；
 * 三个入口各自把被测试按住的 testTag 传进来，出口的形状与文案只剩一套。
 */
internal data class TutorSurfaceFailure(
    val detail: String,
    val reason: String? = null,
    val reasonTestTag: String? = null,
    val primaryActionLabel: String? = null,
    val primaryActionTestTag: String = "tutor_surface_failure_action",
    val onPrimaryAction: () -> Unit = {},
    val secondaryActionLabel: String? = null,
    val secondaryActionTestTag: String = "tutor_surface_failure_secondary_action",
    val onSecondaryAction: () -> Unit = {},
)

/**
 * 学生气泡（唯一实现）。
 *
 * 消灭的失败：大厅与讲题各画一遍学生气泡，同一个"我说的话"在两处是两种圆角与两种底色；
 * 附图入口也只有一处支持。三个入口现在共用这一个。
 */
@Composable
internal fun TutorSurfaceStudentBubble(
    body: String,
    imageAssetIds: List<String> = emptyList(),
    imageIntake: LobbyMessageImageIntake? = null,
    attachmentTestTagPrefix: String = "session",
    modifier: Modifier = Modifier,
    testTag: String = "tutor_surface_student_message",
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .testTag(testTag),
            color = JadeSoft.copy(alpha = 0.72f),
            shape = RoundedCornerShape(14.dp, 14.dp, 4.dp, 14.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
                if (imageAssetIds.isNotEmpty() && imageIntake != null) {
                    MessageImagesRow(
                        assetIds = imageAssetIds,
                        imageIntake = imageIntake,
                        testTagPrefix = attachmentTestTagPrefix,
                    )
                }
                Text(
                    text = body,
                    modifier = Modifier.padding(
                        top = if (imageAssetIds.isEmpty()) 0.dp else 8.dp,
                    ),
                    color = Ink,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/**
 * 助手回复（唯一实现）：思考块 + 正文 + 一行状态或失败卡 + 结构化载荷插槽。
 *
 * 正文走 [TutorPrompt] 这一种视觉（本来只有大厅是它）；讲题侧此前是另一种气泡。
 * 结构化载荷（选择题 / 可点动作 / 意图卡 / 配图）由调用方从 [extras] 注入——它们不是"说过的话"，
 * 但仍然渲染在同一个回复件里，不另起一套气泡。
 *
 * @param bodyMarkdown 正文（K1a：来自消息行，唯一文本权威）；失败态只画卡时传 null。
 * @param progress 生成中 / 暂停 / 旧配置未完成时的那一行状态。
 * @param failure 失败态；与 [progress] 互斥（调用方保证）。
 */
@Composable
internal fun TutorSurfaceAssistantReply(
    bodyMarkdown: String?,
    modifier: Modifier = Modifier,
    thinkingMarkdown: String? = null,
    thinkingCollapsed: Boolean = true,
    /**
     * 这一轮的工具痕迹（B1）：加粗灰色小字内联、点开可看详情（含被拒理由）。null = 没有痕迹，
     * 整行不渲染。它与思考卡是**两件东西**，各自一行——思考是模型在推理，查阅是它对外做的事。
     */
    toolTraceJson: String? = null,
    progress: TutorSurfaceProgress? = null,
    failure: TutorSurfaceFailure? = null,
    /** 非 null 时在回复末尾放一个 1dp 的定位点（答案曝光的"底部进视口"判据挂在它上面）。 */
    bottomAnchor: Modifier? = null,
    extras: @Composable ColumnScope.() -> Unit = {},
    testTag: String = "tutor_surface_assistant_reply",
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(testTag),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (bodyMarkdown != null) {
                ThinkingCollapsibleCard(
                    thinkingMarkdown = thinkingMarkdown,
                    thinking = thinkingCollapsed,
                )
                TutorToolTraceLine(display = rememberTutorToolTraceDisplay(toolTraceJson))
                TutorPrompt(text = bodyMarkdown, modifier = Modifier.fillMaxWidth())
            }
            progress?.let { line ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (line.spinning) {
                        val spinnerModifier = Modifier.size(20.dp)
                        CircularProgressIndicator(
                            modifier = line.spinnerTestTag
                                ?.let { tag -> spinnerModifier.testTag(tag) }
                                ?: spinnerModifier,
                            color = JadeActive,
                            strokeWidth = 2.dp,
                        )
                    }
                    Text(
                        text = line.text,
                        modifier = line.textTestTag
                            ?.let { tag -> Modifier.testTag(tag) }
                            ?: Modifier,
                        color = InkSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            failure?.let { failed ->
                TutorTurnFailureCard(
                    detail = failed.detail,
                    reason = failed.reason,
                    reasonTestTag = failed.reasonTestTag,
                    primaryActionLabel = failed.primaryActionLabel,
                    primaryActionTestTag = failed.primaryActionTestTag,
                    onPrimaryAction = failed.onPrimaryAction,
                    secondaryActionLabel = failed.secondaryActionLabel,
                    secondaryActionTestTag = failed.secondaryActionTestTag,
                    onSecondaryAction = failed.onSecondaryAction,
                )
            }
            extras()
            bottomAnchor?.let { anchor ->
                Box(modifier = Modifier.size(1.dp).then(anchor))
            }
        }
    }
}

/**
 * 一条会话消息的渲染（大厅这种"一行一条消息"的入口用它；讲题侧按轮次渲染，用的是上面同一批
 * 组件）：学生行 = [TutorSurfaceStudentBubble]，助手行 = [TutorSurfaceAssistantReply]。
 */
@Composable
internal fun TutorSurfaceMessageItem(
    message: TutorMessage,
    modifier: Modifier = Modifier,
    imageIntake: LobbyMessageImageIntake? = null,
    attachmentTestTagPrefix: String = "lobby",
    onOpenCapabilitySettings: () -> Unit = {},
    onOpenModelSettings: () -> Unit = onOpenCapabilitySettings,
    /** 可原样重发时，这里是要重发的那条学生消息；否则为 null（不渲染重发按钮）。 */
    resendTarget: TutorMessage? = null,
    onResend: (TutorMessage) -> Unit = {},
    /**
     * 助手消息动作条（A2）：任何状态下的**最后一轮**都可以重试（成功也可以——重试 = 重发同类
     * 请求）；null = 这一条没有可重试的目标。已停止的那一轮同样用它，而不是走失败卡。
     */
    retryTarget: TutorMessage? = null,
    onRetry: (TutorMessage) -> Unit = {},
    studentTestTag: String = "tutor_lobby_student_message",
    assistantTestTag: String = "tutor_lobby_assistant_message",
    failureTestTag: String = "tutor_lobby_task_failure",
    failureReasonTestTag: String = "tutor_lobby_failure_reason",
    progressTestTag: String = "tutor_lobby_task_progress",
    resendTestTag: String = "tutor_lobby_resend",
    actionBarTestTag: String = "tutor_lobby_message_actions",
) {
    if (message.role == TutorMessageRole.STUDENT) {
        TutorSurfaceStudentBubble(
            body = message.bodyMarkdown,
            imageAssetIds = message.sourceImageAssetIds,
            imageIntake = imageIntake,
            attachmentTestTagPrefix = attachmentTestTagPrefix,
            modifier = modifier,
            testTag = studentTestTag,
        )
        return
    }

    when (message.status) {
        TutorMessageStatus.SUCCEEDED -> TutorSurfaceAssistantReply(
            bodyMarkdown = message.bodyMarkdown,
            modifier = modifier,
            thinkingMarkdown = message.thinkingMarkdown,
            // B1：这一轮查阅了什么，随对话流一起出现、重开会话照样在（痕迹存在消息行上）。
            toolTraceJson = message.toolTraceJson,
            testTag = assistantTestTag,
            extras = {
                TutorReplyActionBar(
                    shape = tutorReplyActionShape(
                        bodyMarkdown = message.bodyMarkdown,
                        retryable = retryTarget != null,
                    ),
                    onRetry = { retryTarget?.let(onRetry) },
                    testTagPrefix = actionBarTestTag,
                )
            },
        )

        // 学生按了停止（A2）：一行灰字「已停止」+ 可重试，**不是**失败卡的红字——这一轮不是
        // 模型失败，也不是学生做错了什么。正文是那句停止提示，没有"原文"可复制。
        TutorMessageStatus.CANCELLED -> TutorSurfaceAssistantReply(
            bodyMarkdown = null,
            modifier = modifier,
            testTag = assistantTestTag,
            extras = {
                TutorReplyActionBar(
                    shape = tutorReplyActionShape(
                        bodyMarkdown = null,
                        stopped = true,
                        retryable = retryTarget != null,
                    ),
                    onRetry = { retryTarget?.let(onRetry) },
                    testTagPrefix = actionBarTestTag,
                )
            },
        )

        TutorMessageStatus.FAILED -> {
            val failureCode = message.errorCode
                ?.let { raw -> runCatching { ModelFailureCode.valueOf(raw) }.getOrNull() }
            val resendTo = resendTarget
            val retryTo = retryTarget
            TutorSurfaceAssistantReply(
                // 正文就是失败卡里那一句：不再在正文与卡片上各写一遍（同一句话出现两次）。
                bodyMarkdown = null,
                modifier = modifier,
                failure = TutorSurfaceFailure(
                    detail = message.bodyMarkdown,
                    reason = lobbyFailureReasonText(failureCode),
                    reasonTestTag = failureReasonTestTag,
                    primaryActionLabel = if (failureCode?.requiresModelSettings() == true) {
                        TUTOR_SURFACE_OPEN_SETTINGS_LABEL
                    } else {
                        null
                    },
                    primaryActionTestTag = "tutor_lobby_failure_open_model_settings",
                    onPrimaryAction = onOpenModelSettings,
                    // 原样再发一次：同一句原文、同一批附图，以新的 attempt 重新签发授权。
                    secondaryActionLabel = if (resendTo != null) {
                        TUTOR_SURFACE_RETRY_LABEL
                    } else {
                        null
                    },
                    secondaryActionTestTag = resendTestTag,
                    onSecondaryAction = { resendTo?.let(onResend) },
                ),
                testTag = failureTestTag,
                extras = {
                    // 失败码不适合原样重发时（例如格式问题），动作条仍然给一个统一的「重试」，
                    // 而不是让这一条消息一个出口都没有。**失败卡没有「复制」**：那一段是失败
                    // 说明，不是模型答的话（复制它没有意义）。
                    if (resendTo == null && retryTo != null) {
                        TutorReplyActionBar(
                            shape = tutorReplyActionShape(
                                bodyMarkdown = null,
                                retryable = true,
                            ),
                            onRetry = { onRetry(retryTo) },
                            testTagPrefix = actionBarTestTag,
                        )
                    }
                },
            )
        }

        else -> TutorSurfaceAssistantReply(
            bodyMarkdown = message.bodyMarkdown,
            modifier = modifier,
            testTag = progressTestTag,
        )
    }
}
