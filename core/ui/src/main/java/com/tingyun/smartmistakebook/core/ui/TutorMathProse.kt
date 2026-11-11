package com.tingyun.smartmistakebook.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tingyun.smartmistakebook.core.model.ReadableMathText

/**
 * 讲题正文的一段：普通 Markdown，或一段独立公式（`$$…$$`）。
 * 独立公式交给既有的数学引擎渲染；行内公式不切段（否则会打断 Markdown 列表/段落），
 * 由 [splitTutorProseSegments] 先转成可读数学文本再原样留在 Markdown 里。
 */
internal sealed interface TutorProseSegment {
    data class Markdown(val text: String) : TutorProseSegment
    data class DisplayMath(val latex: String) : TutorProseSegment
}

/**
 * 按独立公式 `$$…$$` 切段，并把行内公式 `$…$` 转成可读数学（[ReadableMathText.formula]，
 * 解析失败原样透传，与题干渲染同一套约定）。
 *
 * 行内公式只认同一行内闭合的 `$…$`（不跨行、非空），`\$` 视为转义；这样普通文本里的
 * 单个 `$` 不会吞掉后面整段讲解。
 */
internal fun splitTutorProseSegments(markdown: String): List<TutorProseSegment> {
    if (markdown.isEmpty()) return emptyList()
    val literal = BooleanArray(markdown.length)
    val offsets = buildList {
        add(0)
        markdown.forEachIndexed { index, character -> if (character == '\n') add(index + 1) }
    }
    val document = org.commonmark.parser.Parser.builder()
        .includeSourceSpans(org.commonmark.parser.IncludeSourceSpans.BLOCKS_AND_INLINES).build().parse(markdown)
    document.accept(object : org.commonmark.node.AbstractVisitor() {
        private fun protect(node: org.commonmark.node.Node) {
            node.sourceSpans.forEach { span ->
                val start = offsets[span.lineIndex] + span.columnIndex
                for (position in start until (start + span.length).coerceAtMost(markdown.length)) literal[position] = true
            }
        }
        override fun visit(node: org.commonmark.node.Code) = protect(node)
        override fun visit(node: org.commonmark.node.FencedCodeBlock) = protect(node)
        override fun visit(node: org.commonmark.node.IndentedCodeBlock) = protect(node)
    })
    fun escaped(position: Int): Boolean {
        var index = position - 1
        while (index >= 0 && markdown[index] == '\\') index--
        return (position - index - 1) % 2 == 1
    }
    fun closingDelimiter(start: Int, delimiter: String, multiline: Boolean): Int {
        var end = markdown.indexOf(delimiter, start)
        while (end >= 0) {
            if (!multiline && '\n' in markdown.substring(start, end)) return -1
            if (!literal[end] && !escaped(end)) return end
            end = markdown.indexOf(delimiter, end + delimiter.length)
        }
        return -1
    }
    val segments = ArrayList<TutorProseSegment>()
    val text = StringBuilder()
    var index = 0
    fun flushText() {
        if (text.isNotEmpty()) {
            segments.add(TutorProseSegment.Markdown(text.toString()))
            text.setLength(0)
        }
    }
    while (index < markdown.length) {
        if (literal[index] || escaped(index)) {
            text.append(markdown[index++])
            continue
        }
        val displayOpen = when {
            markdown.startsWith("$$", index) -> "$$"
            markdown.startsWith("\\[", index) -> "\\["
            else -> null
        }
        if (displayOpen != null) {
            val close = if (displayOpen == "$$") "$$" else "\\]"
            val end = closingDelimiter(index + 2, close, multiline = true)
            if (end > index + 2) {
                flushText()
                segments.add(TutorProseSegment.DisplayMath(markdown.substring(index + 2, end)))
                index = end + 2
            } else {
                text.append(displayOpen)
                index += 2
            }
            continue
        }
        val inlineOpen = when {
            markdown[index] == '$' -> "$"
            markdown.startsWith("\\(", index) -> "\\("
            else -> null
        }
        if (inlineOpen != null) {
            val close = if (inlineOpen == "$") "$" else "\\)"
            val start = index + inlineOpen.length
            val end = closingDelimiter(start, close, multiline = false)
            if (end > start && markdown.substring(start, end).isNotBlank()) {
                // Entities preserve mathematical comparisons without making them Markdown HTML.
                val readable = ReadableMathText.formula(markdown.substring(start, end))
                    .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                    .replace("*", "\\*").replace("_", "\\_")
                text.append(readable)
                index = end + close.length
                continue
            }
        }
        text.append(markdown[index++])
    }
    flushText()
    return segments
}

/**
 * 讲题正文渲染：Markdown 走既有安全渲染（含 fail-closed 回退），独立公式走数学引擎
 * （解析失败回退为可读文本），两段之间的排版由本组件统一。
 */
@Composable
fun TutorReplyMarkdown(
    markdown: String,
    modifier: Modifier = Modifier,
) {
    val segments = splitTutorProseSegments(markdown)
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        segments.forEach { segment ->
            when (segment) {
                is TutorProseSegment.Markdown -> AiReplyRichMarkdown(
                    markdown = segment.text,
                    modifier = Modifier.fillMaxWidth(),
                )
                is TutorProseSegment.DisplayMath -> MathFormulaBox(
                    formula = segment.latex,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tutor_reply_formula")
                        .padding(vertical = 10.dp),
                    color = Ink,
                    fallbackText = ReadableMathText.formula(segment.latex),
                    fontSize = 20.sp,
                )
            }
        }
    }
}
