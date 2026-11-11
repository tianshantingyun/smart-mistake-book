package com.tingyun.smartmistakebook.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

internal fun tutorStreamingDisplayText(markdown: String): String {
    val blocks = parseTutorMessageDocument(markdown)
    if (blocks.none { it is TutorMessageBlock.Figure || it == TutorMessageBlock.Question }) return markdown
    return blocks.joinToString("\n\n") { block ->
        when (block) {
            is TutorMessageBlock.Markdown -> block.text
            is TutorMessageBlock.Figure -> "正在准备配图…"
            TutorMessageBlock.Question -> "正在准备思考题…"
        }
    }
}

/**
 * 生成中的回答正文：逐 token 增长时，已完成的块走完整渲染（markdown + 公式），尾块走宽容
 * 纯文本。
 *
 * 尾块宽容是必须的：流式过程中随时可能停在未闭合的 `**`、`$$` 或代码围栏中间，直接交给完整
 * 渲染管线会在每一帧闪出一次解析噪声；而等它闭合再整体重排又会让学生看到"跳变"。已完成块用
 * [key] 固定，字符串不再变化，Compose 可以直接跳过它们的重组。
 */
@Composable
fun TutorStreamingReply(
    markdown: String,
    modifier: Modifier = Modifier,
) {
    val blocks = remember(markdown) { splitStreamingBlocks(markdown) }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        blocks.completed.forEachIndexed { index, block ->
            key(index) { TutorReplyMarkdown(tutorStreamingDisplayText(block)) }
        }
        if (blocks.tail.isNotBlank()) {
            Text(
                text = tutorStreamingDisplayText(blocks.tail),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("tutor_streaming_tail"),
                color = Ink,
                style = TutorMarkdownTokens.body,
            )
        }
    }
}
