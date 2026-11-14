package com.tingyun.smartmistakebook.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halilibo.richtext.markdown.Markdown
import com.halilibo.richtext.markdown.MarkdownParseOptions
import com.halilibo.richtext.ui.CodeBlockStyle
import com.halilibo.richtext.ui.RichText
import com.halilibo.richtext.ui.RichTextStyle
import com.halilibo.richtext.ui.material3.SetupMaterial3RichText
import com.halilibo.richtext.ui.string.RichTextStringStyle
import com.tingyun.smartmistakebook.core.model.SafeInlineMarkdown
import com.tingyun.smartmistakebook.core.model.StructuredContentLimits

/**
 * 终稿块级渲染的 fail-closed 判定：只有不含任何 HTML/active scheme/远程图片/裸 URL 的标记，
 * 才值得交给 richtext 完整 commonmark；否则回退到受限 SafeMarkdownText。
 * 裸 URL 判定复用数据层 `SafeInlineMarkdown.BARE_URL`——单一真源，随数据层扩展自动生效，避免两处正则漂移。
 * 纯函数，便于单元测试锁定。
 */
internal fun shouldUseRichTextMarkdown(markdown: String): Boolean {
    if (markdown.isBlank()) return false
    if (markdown.length > StructuredContentLimits.MAX_TEXT_CHARS) return false
    if (SafeInlineMarkdown.BARE_URL.containsMatchIn(markdown)) return false
    return !SafeInlineMarkdown.requiresPlainTextFallback(markdown)
}

/**
 * 终稿块级渲染器：以 richtext 完整 commonmark 渲染模型讲解正文。终端渲染，只用于已
 * SUCCEEDED 的完整讲解；流式仍走 SafeMarkdownText。
 *
 * 安全（fail-closed）：数据层先经 requireTutorSceneText 白名单（禁 HTML/链接/URL/图片）。
 * 渲染前再兜底：检测到 HTML/active scheme/远程图片即回退到受限 SafeMarkdownText；并关掉
 * 自动链接、把链接点击设为 no-op，避免任何导航/外发。
 *
 * 行内样式与流式/回退共用 [TutorMarkdownTokens]——bold/italic/code span 经 [RichTextStringStyle]
 * 注入，使"中途→完成"不跳变。
 */
@Composable
fun AiReplyRichMarkdown(
    markdown: String,
    modifier: Modifier = Modifier,
) {
    // 模型常以单个换行表示分段；CommonMark 会把它并进同一段，学生看到的是一整块。
    // 渲染前把"非列表/非代码"的连续行各自提升为段落，加粗与分段才真的可见。
    val normalized = remember(markdown) { normalizeTutorParagraphs(markdown) }
    if (!shouldUseRichTextMarkdown(normalized)) {
        SafeMarkdownText(
            markdown = normalized,
            style = TutorMarkdownTokens.body,
            modifier = modifier,
        )
        return
    }

    ProvideTextStyle(TutorMarkdownTokens.body.copy(color = Ink)) {
        SetupMaterial3RichText {
            RichText(
                modifier = modifier,
                style = RichTextStyle(
                    paragraphSpacing = 16.sp,
                    headingStyle = { level, _ -> TutorMarkdownTokens.heading(level) },
                    stringStyle = RichTextStringStyle(
                        boldStyle = TutorMarkdownTokens.strong,
                        italicStyle = TutorMarkdownTokens.emphasis,
                        codeStyle = TutorMarkdownTokens.code,
                    ),
                    codeBlockStyle = CodeBlockStyle(
                        textStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Normal,
                            fontSize = 13.sp,
                            color = Color(0xFFE5E5E5),
                            lineHeight = 20.sp,
                        ),
                        wordWrap = true,
                        modifier = Modifier
                            .background(
                                color = Color(0xFF0D0D0C),
                                shape = RoundedCornerShape(12.dp),
                            ).border(1.dp, Color(0xFF262626), RoundedCornerShape(12.dp)),
                    ),
                ),
            ) {
                Markdown(
                    content = normalized,
                    markdownParseOptions = MarkdownParseOptions(autolink = false),
                    onLinkClicked = { /* fail-closed：no-op，链接惰性 */ },
                    codeBlockContent = { literal, language -> ClaudeCodeBlock(literal, language) },
                )
            }
        }
    }
}

/**
 * 把模型回复里的**单个换行**提升为段落分隔（列表、标题、引用、表格、代码围栏保持原样）。
 * 纯函数，锁在 JVM 测试里：输入不变则输出逐字稳定。
 */
internal fun normalizeTutorParagraphs(markdown: String): String {
    if (markdown.isBlank()) return markdown
    val lines = markdown.replace("\r\n", "\n").split('\n')
    val out = ArrayList<String>(lines.size + 8)
    var inFence = false
    var block = ArrayList<String>()

    fun flushBlock() {
        if (block.isEmpty()) return
        val plain = block.none { line ->
            val t = line.trimStart()
            t.startsWith("-") || t.startsWith("*") || t.startsWith("+") ||
                t.matches(Regex("\\d+[.)]\\s+.*")) || t.startsWith("#") ||
                t.startsWith(">") || t.startsWith("|")
        }
        if (block.size > 1 && plain) {
            block.forEachIndexed { index, line ->
                if (index > 0) out.add("")
                out.add(line)
            }
        } else {
            out.addAll(block)
        }
        block = ArrayList()
    }

    for (line in lines) {
        val trimmed = line.trimStart()
        if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
            flushBlock()
            inFence = !inFence
            out.add(line)
            continue
        }
        if (inFence) {
            out.add(line)
            continue
        }
        if (line.isBlank()) {
            flushBlock()
            out.add("")
        } else {
            block.add(line)
        }
    }
    flushBlock()
    return out.joinToString("\n")
}
