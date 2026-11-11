package com.tingyun.smartmistakebook.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.commonmark.parser.IncludeSourceSpans
import org.commonmark.node.Paragraph
import org.commonmark.parser.Parser

internal sealed interface TutorMessageBlock {
    data class Markdown(val text: String) : TutorMessageBlock
    data class Figure(val assetId: String) : TutorMessageBlock
    data object Question : TutorMessageBlock
}

/** Only a complete top-level paragraph may position a host-owned figure. */
internal fun parseTutorMessageDocument(markdown: String): List<TutorMessageBlock> {
    val parser = Parser.builder().includeSourceSpans(IncludeSourceSpans.BLOCKS).build()
    val document = parser.parse(markdown)
    val lineOffsets = buildList {
        add(0)
        markdown.forEachIndexed { index, character -> if (character == '\n') add(index + 1) }
    }
    val figurePattern = Regex("\\[\\[figure:([^\\s\\[\\]]{1,256})]]")
    val blocks = mutableListOf<TutorMessageBlock>()
    var cursor = 0
    var displayMathDelimiter: String? = null
    var node = document.firstChild
    while (node != null) {
        if (node is Paragraph && node.sourceSpans.isNotEmpty()) {
            val first = node.sourceSpans.first()
            val last = node.sourceSpans.last()
            val start = lineOffsets[first.lineIndex] + first.columnIndex
            val end = lineOffsets[last.lineIndex] + last.columnIndex + last.length
            val paragraph = markdown.substring(start, end)
            val inMath = displayMathDelimiter != null
            displayMathDelimiter = com.tingyun.smartmistakebook.core.model.SafeInlineMarkdown
                .displayMathDelimiterAfter(paragraph, displayMathDelimiter)
            val match = if (inMath) null else figurePattern.matchEntire(paragraph.trim())
            val question = !inMath && paragraph.trim() == "[[question]]"
            if (match != null || question) {
                markdown.substring(cursor, start).trim().takeIf(String::isNotEmpty)?.let {
                    blocks += TutorMessageBlock.Markdown(it)
                }
                blocks += if (question) TutorMessageBlock.Question else TutorMessageBlock.Figure(requireNotNull(match).groupValues[1])
                cursor = end
            }
        }
        node = node.next
    }
    markdown.substring(cursor).trim().takeIf(String::isNotEmpty)?.let {
        blocks += TutorMessageBlock.Markdown(it)
    }
    return blocks
}

fun tutorMessageCopyText(
    markdown: String,
    figureCaptions: Map<String, String> = emptyMap(),
    questionMarkdown: String? = null,
): String {
    val figures = mutableListOf<String>()
    var questionCopied = false
    return parseTutorMessageDocument(markdown).joinToString("\n\n") { block ->
        when (block) {
            is TutorMessageBlock.Markdown -> block.text
            TutorMessageBlock.Question -> if (questionCopied) "" else {
                questionCopied = true
                questionMarkdown ?: "〔思考题〕"
            }
            is TutorMessageBlock.Figure -> {
                if (block.assetId !in figures) figures += block.assetId
                val caption = figureCaptions[block.assetId]?.takeIf(String::isNotBlank)
                "〔图 ${figures.indexOf(block.assetId) + 1}${caption?.let { "：$it" }.orEmpty()}〕"
            }
        }
    }
}

fun tutorMessageFigureReferences(markdown: String): List<String> =
    parseTutorMessageDocument(markdown).filterIsInstance<TutorMessageBlock.Figure>().map { it.assetId }.distinct()

/** Figures reserved for a later answer stay hidden until that section is opened. */
fun tutorOpeningFigureAssets(markdown: String, assetIds: List<String>, laterSections: List<String>): List<String> {
    val visible = tutorMessageFigureReferences(markdown).toSet()
    val later = laterSections.flatMap(::tutorMessageFigureReferences).toSet()
    return assetIds.filter { it !in later || it in visible }
}

/** Assets are resolved locally and only from this message's persisted allowlist. */
@Composable
fun TutorMessageDocument(
    markdown: String,
    figureAssetIds: List<String> = emptyList(),
    resolveFigure: (suspend (String) -> String?)? = null,
    modifier: Modifier = Modifier,
    figureCaptions: Map<String, String> = emptyMap(),
    inlineQuestion: (@Composable () -> Unit)? = null,
) {
    val blocks = remember(markdown) { parseTutorMessageDocument(markdown) }
    val allowed = figureAssetIds.toSet()
    val positioned = blocks.filterIsInstance<TutorMessageBlock.Figure>()
        .map { it.assetId }.filter { it in allowed }.distinct()
    val order = positioned + figureAssetIds.distinct().filterNot { it in positioned }
    val anchors = remember(order) { order.associateWith { BringIntoViewRequester() } }
    val scope = rememberCoroutineScope()
    val shown = mutableSetOf<String>()
    var questionShown = false
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        blocks.forEach { block ->
            when (block) {
                is TutorMessageBlock.Markdown -> TutorReplyMarkdown(block.text)
                TutorMessageBlock.Question -> if (!questionShown) {
                    questionShown = true
                    inlineQuestion?.invoke()
                }
                is TutorMessageBlock.Figure -> {
                    if (block.assetId !in allowed || resolveFigure == null) {
                        Text("配图暂不可用", style = ClaudeLayout.ControlText, color = InkSecondary)
                    } else if (shown.add(block.assetId)) {
                        GeneratedFiguresSection(listOf(block.assetId), resolveFigure,
                            modifier = Modifier.bringIntoViewRequester(anchors.getValue(block.assetId)),
                            firstFigureNumber = order.indexOf(block.assetId) + 1, captions = figureCaptions)
                    } else {
                        ClaudeTextButton(onClick = {
                            scope.launch { anchors.getValue(block.assetId).bringIntoView() }
                        }) { Text("查看图 ${order.indexOf(block.assetId) + 1}") }
                    }
                }
            }
        }
        val remaining = order.filterNot { it in shown }
        if (remaining.isNotEmpty() && resolveFigure != null) {
            GeneratedFiguresSection(remaining, resolveFigure, firstFigureNumber = positioned.size + 1,
                captions = figureCaptions)
        }
        if (!questionShown) inlineQuestion?.invoke()
    }
}
