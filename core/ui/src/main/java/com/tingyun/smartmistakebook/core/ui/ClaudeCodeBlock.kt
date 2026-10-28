package com.tingyun.smartmistakebook.core.ui

// Native CodeBlock.tsx port, Caho1/open-claude-ui c1f923ae, MIT (Copyright 2026 Caho1).
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Receives the parser's original literal; presentation never trims or rewrites copied code. */
@Composable
fun ClaudeCodeBlock(literal: String, language: String, modifier: Modifier = Modifier) {
    val foreground = SmartColors.OnCode
    val shape = RoundedCornerShape(16.dp)
    Column(modifier.fillMaxWidth().testTag("claude_code_block").clip(shape)
        .background(SmartColors.CodeSurface)) {
        Row(Modifier.fillMaxWidth().background(SmartColors.CodeHeader).padding(start = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("</>", color = foreground, style = ClaudeLayout.ControlText)
            Text(language.trim().substringBefore(' ').ifBlank { "代码" }, Modifier.weight(1f),
                color = foreground, style = ClaudeLayout.ControlText, maxLines = 1, overflow = TextOverflow.Ellipsis)
            ClaudeCopyButton(literal, label = "复制代码", contentColor = foreground,
                errorColor = Color(0xFFFFB4AB), successColor = Color(0xFF3DD68C))
        }
        SelectionContainer {
            Text(literal, Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(14.dp),
                color = foreground, style = TextStyle(fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp, lineHeight = 20.sp), softWrap = false)
        }
    }
}
