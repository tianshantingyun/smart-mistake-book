package com.tingyun.smartmistakebook.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

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
 *
 * **匀速流动（批次 52）**：上游的增量是成串到达的（网关每 8 帧合并一次、轮询 120ms 一次），
 * 直接把新文本整体贴上去会"一段一段闪出来"。这里把目标文本当作水位，按帧把可见长度往前推：
 * 积压越多推进越快（每帧约 5%，最少 1 字、最多 24 字），积压清零后就是逐字匀速。
 * 因此上游停顿/突进不会改变学生看到的节奏。
 */
@Composable
fun TutorStreamingReply(
    markdown: String,
    modifier: Modifier = Modifier,
) {
    var revealedChars by remember { mutableIntStateOf(0) }
    LaunchedEffect(markdown) {
        if (revealedChars > markdown.length) revealedChars = markdown.length
        while (revealedChars < markdown.length) {
            val remaining = markdown.length - revealedChars
            val step = (remaining / 20).coerceIn(1, 24)
            revealedChars = (revealedChars + step).coerceAtMost(markdown.length)
            delay(16L)
        }
    }
    val shown = remember(markdown, revealedChars) { markdown.take(revealedChars) }
    val blocks = remember(shown) { splitStreamingBlocks(shown) }
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
