package com.tingyun.smartmistakebook.feature.tutor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.tingyun.smartmistakebook.core.ui.ErrorWarm
import com.tingyun.smartmistakebook.core.ui.InkSecondary
import com.tingyun.smartmistakebook.core.ui.OutlineActionChip

/**
 * 输入区不能发送时的原因与出口（A5）。
 *
 * 消灭的失败：不能发送时输入框整块消失（大厅在"计划还没出结果"、模型未配置、会话已结束时直接
 * 不渲染输入区，共 4 处早退帧），学生看到的是"这里没有输入框"——既不知道发生了什么，也没有
 * 任何可以点的东西。现在输入框永远在，旁边一句人话说明为什么暂时发不出去，以及能做什么。
 */
internal data class TutorComposerBlock(
    val reason: String,
    val action: TutorComposerAction? = null,
    /** 这一轮已经终结（会话已结束）：输入框仍然在，但确实没有可以发过去的对象。 */
    val inputDisabled: Boolean = false,
)

internal data class TutorComposerAction(
    val kind: TutorComposerActionKind,
    val label: String,
)

internal enum class TutorComposerActionKind {
    /** 原样再发一次 / 重新生成这一轮。 */
    RETRY,

    /** 模型配置是问题所在。 */
    OPEN_SETTINGS,

    /** 有一轮停在半路（残留任务）：接上它，这一轮才收尾。 */
    RESUME,
}

/**
 * 输入区此刻的可用性：全部来自外部事实，纯函数，便于把"不能发送"的四种情况逐个钉住。
 *
 * @param providerReady 已配置模型且支持本轮任务（讲题轮 = TUTOR_PLAN/TUTOR_RESPOND，智能体栏 = TUTOR_LOBBY）。
 * @param planReady 讲题轮的首轮讲解已经出结果（智能体栏无此概念，恒 true）。
 * @param planFailed 首轮讲解以失败收场。
 * @param conversationEnded 会话已结束且未保存（拍照入口的"结束且不保存"）。
 * @param questionNotReady 这道题还没有读出来（加载中 / 读不到 / 题面不完整）：输入框仍在，
 *   但确实还没有可以发过去的对象——**不是**"这里没有输入框"，而是"它还在打开"。
 * @param retryableTurnExists 有一轮失败且可以原样重试。
 * @param stalledTurnReason 有一轮停在半路（残留任务）：这是那句人话原因。非 null 时输入区
 *   照常可用、发送键变灰，出口是「继续回复」——**发送被挡住时说的话与挡它的判据同源**，
 *   所以不会出现"按了发送什么也没发生"（A5）。
 */
internal data class TutorComposerAvailability(
    val providerReady: Boolean,
    val providerLoadFailed: Boolean = false,
    val planReady: Boolean = true,
    val planFailed: Boolean = false,
    val conversationEnded: Boolean = false,
    val questionNotReady: Boolean = false,
    val retryableTurnExists: Boolean = false,
    val stalledTurnReason: String? = null,
)

/** 不能发送时的原因（可以发送时返回 null，按正常态渲染）。顺序 = 谁先拦住学生就先说谁。 */
internal fun TutorComposerAvailability.block(): TutorComposerBlock? = when {
    questionNotReady -> TutorComposerBlock(
        reason = "这道题还没有打开，读出来之后就能接着问。",
        inputDisabled = true,
    )

    conversationEnded -> TutorComposerBlock(
        reason = "这次讲题已经结束，未存入错题本。",
        inputDisabled = true,
    )

    providerLoadFailed -> TutorComposerBlock(
        reason = "暂时读不到模型配置，检查一下再继续。",
        action = OPEN_SETTINGS_ACTION,
    )

    !providerReady -> TutorComposerBlock(
        reason = "当前模型还不能处理对话，先完成模型配置和能力测试。",
        action = OPEN_SETTINGS_ACTION,
    )

    planFailed -> TutorComposerBlock(
        reason = "这道题的讲解没有准备好。",
        action = RETRY_ACTION,
    )

    !planReady -> TutorComposerBlock(
        reason = "正在准备这道题的讲解，马上就好。",
    )

    // 模型没问题、讲解也在，但上一轮停在半路：接上它，新的这一条才发得出去。
    stalledTurnReason != null -> TutorComposerBlock(
        reason = stalledTurnReason,
        action = RESUME_ACTION,
    )

    else -> null
}

private val RETRY_ACTION = TutorComposerAction(TutorComposerActionKind.RETRY, TUTOR_SURFACE_RETRY_LABEL)
private val OPEN_SETTINGS_ACTION =
    TutorComposerAction(TutorComposerActionKind.OPEN_SETTINGS, TUTOR_SURFACE_OPEN_SETTINGS_LABEL)
private val RESUME_ACTION = TutorComposerAction(TutorComposerActionKind.RESUME, TUTOR_RESUME_TURN_LABEL)

/** 「继续回复」的文案：与列表里那个出口同一个词（同一件事只有一种叫法）。 */
internal const val TUTOR_RESUME_TURN_LABEL = "继续回复"

/**
 * 输入区（唯一实现，A5）：原因行 + 附件区 + 输入框 + 发送键。
 *
 * 三个入口共用；不能发送的原因与出口由 [block] 给，输入框本身不消失。
 */
@Composable
internal fun TutorSurfaceComposer(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    sending: Boolean = false,
    block: TutorComposerBlock? = null,
    onAction: (TutorComposerAction) -> Unit = {},
    actionTestTags: (TutorComposerActionKind) -> String = { "tutor_composer_action" },
    placeholder: String = "问这道题，或说出你卡住的步骤",
    onOpenAttachMenu: (() -> Unit)? = null,
    attachmentPreview: (@Composable () -> Unit)? = null,
    attachmentCount: Int = 0,
    reasonTestTag: String = "tutor_composer_block_reason",
) {
    Column(modifier = modifier.fillMaxWidth()) {
        block?.let { blocked ->
            Text(
                text = blocked.reason,
                modifier = Modifier
                    .padding(bottom = 6.dp)
                    .testTag(reasonTestTag),
                color = InkSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
            blocked.action?.let { action ->
                OutlineActionChip(
                    text = action.label,
                    onClick = { onAction(action) },
                    modifier = Modifier
                        .padding(bottom = 6.dp)
                        .testTag(actionTestTags(action.kind)),
                )
            }
        }
        TutorChatComposer(
            value = value,
            // 输入框只在"这一轮已经没有对象可发"时才不可输入（会话已结束）；其余阻断都只是
            // 发不出去，学生仍然可以先打字（发不出去时原因就写在上面，A5）。
            enabled = block?.inputDisabled != true,
            sending = sending,
            onValueChange = onValueChange,
            onSend = onSend,
            placeholder = placeholder,
            onOpenAttachMenu = onOpenAttachMenu,
            attachmentPreview = attachmentPreview,
            attachmentCount = attachmentCount,
            // 有阻断就发不出去：发送键变灰，原因与出口在上面那块里。
            sendBlocked = block != null,
        )
    }
}

/** 一句失败说明（发送路径上的错误）：输入区上方，与输入框同在一个常驻块里。 */
@Composable
internal fun TutorSurfaceStartError(
    message: String,
    modifier: Modifier = Modifier,
    testTag: String = "tutor_chat_start_error",
) {
    Text(
        text = message,
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag),
        color = ErrorWarm,
        style = MaterialTheme.typography.bodySmall,
    )
}
