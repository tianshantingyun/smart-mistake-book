package com.tingyun.smartmistakebook.feature.tutor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import com.tingyun.smartmistakebook.core.ui.ClaudeLayout
import com.tingyun.smartmistakebook.core.ui.claudeInteractiveRow
import com.tingyun.smartmistakebook.core.ui.ContentSurface
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.tingyun.smartmistakebook.core.ui.Ink
import com.tingyun.smartmistakebook.core.ui.InkSecondary
import com.tingyun.smartmistakebook.core.ui.InlineActionButton
import com.tingyun.smartmistakebook.core.ui.SubtleSurface

/**
 * 空态里的一条能力（它能干什么）。
 *
 * @param examples 「试一句」的示例（S5）：点了就**作为首条消息发出去**。每条能力都有一句以上，
 *   且每一句都是学生真的会说的第一句话——写了不自然的话，点了之后模型答得也别扭。
 * @param onAction 这个能力真实存在的动作；null = 这一条只做说明（没有死按钮）。
 */
internal data class TutorCapability(
    val id: String,
    val title: String,
    val detail: String,
    val examples: List<TutorCapabilityExample> = emptyList(),
    val actionLabel: String? = null,
    val actionTestTag: String? = null,
    val onAction: (() -> Unit)? = null,
)

internal data class TutorCapabilityExample(
    val text: String,
    val testTag: String,
)

/**
 * 智能体栏空态的能力目录（A1：新会话空白时展示"它能干什么"，有内容后让位）。
 *
 * 只列**真的点了有反应**的能力：录入并讲解、从错题本挑一道、打开错题本。选择导出（第二项能力）
 * 要等导出链路落地（阶段 4B），这里不出现——一个点了没反应的入口比没有入口更糟。
 */
internal fun agentCapabilityDirectory(
    onCapture: () -> Unit,
    onPickMistake: () -> Unit,
    onOpenMistakeNotebook: () -> Unit,
): List<TutorCapability> = listOf(
    TutorCapability(
        id = "explain",
        title = "讲一道题",
        detail = "发送题目、截图或推导过程，告诉我哪一步没懂。",
        examples = listOf(
            TutorCapabilityExample("函数单调性怎么判断？", "tutor_example_monotonicity"),
            TutorCapabilityExample("帮我看看错题本里最近常错什么", "tutor_example_notebook"),
        ),
    ),
    TutorCapability(
        id = "capture",
        title = "录入并讲解",
        detail = "拍照或选图，整理题面后开始讲解。",
        examples = listOf(
            TutorCapabilityExample(
                "我想拍一道题，你先说说这类题一般从哪下手",
                "tutor_example_capture",
            ),
        ),
        actionLabel = "开始录入",
        actionTestTag = "tutor_capture_shortcut",
        onAction = onCapture,
    ),
    TutorCapability(
        id = "pick-from-notebook",
        title = "从错题本挑一道",
        detail = "选择一道已保存的题，继续讲解。",
        examples = listOf(
            TutorCapabilityExample(
                "从我错题本里挑一道最该复习的，讲讲它的思路",
                "tutor_example_pick_mistake",
            ),
        ),
        actionLabel = "从错题本选择",
        actionTestTag = "tutor_choose_existing_button",
        onAction = onPickMistake,
    ),
    TutorCapability(
        id = "open-notebook",
        title = "去错题本看看",
        detail = "查看已保存的题和学习记录。",
        examples = listOf(
            TutorCapabilityExample(
                "我的错题本里现在大概有些什么？",
                "tutor_example_open_notebook",
            ),
        ),
        actionLabel = "打开错题本",
        actionTestTag = "tutor_directory_open_notebook",
        onAction = onOpenMistakeNotebook,
    ),
)

/**
 * 能力目录（空态）：标题 + 一句引导 + 每条能力一行说明和一个真实出口。
 *
 * `tutor_empty_state` 这个标签留在根上（既有用例按它断言"空态在"）——目录是空态的内容，
 * 不是它的替代品。
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun TutorCapabilityDirectory(
    capabilities: List<TutorCapability>,
    modifier: Modifier = Modifier,
    /** 点「试一句」：把那句话**当作首条消息发出去**（S5；不是填进输入框等学生再点发送）。 */
    onExample: (String) -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .testTag(TUTOR_CAPABILITY_DIRECTORY_TAG),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "也可以从这里开始。",
            modifier = Modifier.testTag("tutor_empty_state"),
            color = InkSecondary,
            style = ClaudeLayout.ControlText,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            capabilities.forEach { capability ->
                val label = capability.actionLabel
                val action = capability.onAction
                if (label != null && action != null) {
                    TutorSuggestionChip(label, action, capability.actionTestTag ?: "tutor_capability_action_${capability.id}")
                }
            }
        }
        capabilities.forEach { capability ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("tutor_capability_${capability.id}"),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (capability.examples.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        itemVerticalAlignment = Alignment.CenterVertically,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        capability.examples.forEach { example ->
                            TutorSuggestionChip(example.text, { onExample(example.text) }, example.testTag)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TutorSuggestionChip(text: String, onClick: () -> Unit, testTag: String) {
    val shape = RoundedCornerShape(12.dp)
    Box(Modifier.heightIn(min = 48.dp).background(ContentSurface, shape)
        .border(1.dp, Ink.copy(alpha = .1f), shape)
        .claudeInteractiveRow(onClick, shape = shape).padding(horizontal = 12.dp, vertical = 10.dp).testTag(testTag),
        contentAlignment = Alignment.CenterStart) {
        Text(text, color = InkSecondary, style = ClaudeLayout.ControlText, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
