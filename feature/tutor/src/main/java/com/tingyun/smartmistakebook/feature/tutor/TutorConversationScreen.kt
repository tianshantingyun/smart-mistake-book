package com.tingyun.smartmistakebook.feature.tutor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.tingyun.smartmistakebook.core.domain.TutorConversationAreas
import com.tingyun.smartmistakebook.core.ui.Paper

/**
 * 交互面自己的标签：**三个入口都渲染同一个组件**，测试按它断言"这是同一条交互面"。
 * 入口各自的标签（`tutor_screen` / `captured_tutor_session_screen` / `saved_mistake_tutor_screen`）
 * 继续由入口传进来的 `modifier` 承担，两边互不覆盖。
 */
internal const val TUTOR_SURFACE_TAG = "tutor_conversation_screen"

/** 空态（能力目录）的标签。 */
internal const val TUTOR_CAPABILITY_DIRECTORY_TAG = "tutor_capability_directory"

/**
 * 一条交互面的差异（C1）：三个入口渲染同一个 [TutorConversationScreen]，差别只在**所在栏、
 * 本轮附件、壳自己的按钮与出口、空态**。
 *
 * 消灭的失败：同一份对话数据此前有两个渲染器（大厅一套、讲题一套：两种气泡、两套失败卡、
 * 三种重试文案），学生的同一个动作在不同入口长得不一样，修一处忘一处。
 *
 * @param area 会话区（K1 的判别列）：这条交互面长在哪一栏。本阶段三个入口都是智能体栏，
 *   复习栏两个入口在阶段 5 各给一个区。
 * @param header 壳自己的标题栏（返回 / 历史 / 能力设置）。
 * @param leadingContent 常驻在列表顶部的"这一轮在讲什么"（题面卡、来源行、学习记忆卡）。
 * @param trailingContent 壳自己的任务按钮（拍照入口的「存入错题本」「结束且不保存」）。
 * @param emptyState 空态（A1）：进入即新会话时给学生看的"它能干什么"。null = 这个入口没有空态。
 * @param autoStartFirstTurn 进入即自动开首轮（讲题入口有题可讲；智能体栏等学生先说一句）。
 * @param onOpenAttachedQuestionDetail 出口回调：本轮附件的题可以点开看（错题讲题入口有，
 *   它接的是"打开错题本"）。null = 这个入口不提供这个出口，界面上也不出现那个按钮。
 * @param captureSessionId 这条交互面锚着的**拍照会话 id**（A4 执行路径 ① 的唯一来源）：
 *   有它，模型申请"加入错题本"时那张卡执行的就是"把这次拍照的草稿存进错题本"。拍照入口给
 *   本次 sessionId；其余入口为 null（没有草稿可存，卡会落到题 / 附图那两条路径上）。
 * @param libraryProblemId 这条交互面锚着的**那一道已在错题本里的题**（A4 执行路径 ② 的唯一
 *   来源）：错题讲题入口给它（这一页讲的就是这一条错题）。有它，模型申请"打开 / 存这一轮这道题"
 *   时那张卡的目标就是它（执行 = 打开错题本，回喂如实说"已经在错题本里"）；为 null 时这一页
 *   没有可指的那道题，这类申请连卡都挂不出来（本地没有目标）。
 */
internal data class TutorSurfaceConfig(
    val area: String = TutorConversationAreas.AGENT,
    val header: @Composable () -> Unit,
    val composerPlaceholder: String = "问这道题，或说出你卡住的步骤",
    val liveAnswerTestTag: String = "tutor_streaming_reply",
    val leadingContent: (@Composable ColumnScope.() -> Unit)? = null,
    val trailingContent: (@Composable ColumnScope.() -> Unit)? = null,
    val emptyState: (@Composable () -> Unit)? = null,
    val autoStartFirstTurn: Boolean = false,
    val onOpenAttachedQuestionDetail: (() -> Unit)? = null,
    val captureSessionId: String? = null,
    val libraryProblemId: String? = null,
)

/**
 * 唯一的一条交互面（C1）：标题栏 + 列表（常驻块 / 空态 / 各入口的对话内容 / 壳的尾部）+
 * 在途一轮的实时文本 + 附件区 + 输入区。
 *
 * 消灭的失败（A5）：输入区此前是**可选参数**——四个入口状态（模型未就绪、计划尚未出结果、
 * 计划失败、会话已结束）各自 `return` 一个不带输入框的帧，学生看到的是"这里没有输入框"。
 * 现在输入区是**必填参数**：没有哪一个分支能不给它，输入框也就不会消失。
 *
 * @param composer 输入区（A5）。必填：不给它就没有交互面。
 * @param attachments 附件区（本轮附件 + 待发附图 + 读题失败），渲染在输入区正上方。
 * @param emptyStateVisible 空态是否在场（有内容后让位）。
 * @param content 各入口自己的对话内容（智能体栏 = 消息行；讲题 = 轮次时间线）。
 */
@Composable
internal fun TutorConversationScreen(
    config: TutorSurfaceConfig,
    composer: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    attachments: (@Composable () -> Unit)? = null,
    liveTurn: TutorLiveTurn? = null,
    liveStatusText: String? = null,
    livePlaceholder: String = TUTOR_LIVE_PLACEHOLDER,
    /** 在途一轮的「停止」（A2）：null = 这个入口这一轮不提供停止。 */
    liveTurnStopAction: (() -> Unit)? = null,
    liveTurnStopTestTag: String = "tutor_live_stop",
    emptyStateVisible: Boolean = false,
    autoScrollVersion: Any? = null,
    forceFollowToken: Any? = null,
    blockAutoFollowToken: Any? = null,
    listState: LazyListState = rememberLazyListState(),
    listViewportModifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Paper),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .testTag(TUTOR_SURFACE_TAG),
        ) {
            TutorConversationFrame(
                header = config.header,
                autoScrollVersion = autoScrollVersion,
                forceFollowToken = forceFollowToken,
                blockAutoFollowToken = blockAutoFollowToken,
                modifier = Modifier.fillMaxSize(),
                listState = listState,
                listViewportModifier = listViewportModifier,
                liveTurn = liveTurn,
                liveStatusText = liveStatusText,
                livePlaceholder = livePlaceholder,
                liveAnswerTestTag = config.liveAnswerTestTag,
                liveTurnStopAction = liveTurnStopAction,
                liveTurnStopTestTag = liveTurnStopTestTag,
                composer = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        attachments?.invoke()
                        composer()
                    }
                },
            ) {
                config.leadingContent?.let { leading ->
                    item("tutor_surface_leading") {
                        Column(content = leading)
                    }
                }
                if (emptyStateVisible) {
                    config.emptyState?.let { directory ->
                        item("tutor_surface_empty_state") { directory() }
                    }
                }
                content()
                config.trailingContent?.let { trailing ->
                    item("tutor_surface_trailing") {
                        Column(content = trailing)
                    }
                }
            }
        }
    }
}
