/*
 * Layout port of Caho1/open-claude-ui (c1f923ae), MIT, Copyright (c) 2026 Caho1.
 * Sources: ChatComposer.tsx, ChatMessageBubbles.tsx, ClaudeDesktopHeader.tsx.
 * License: app/src/main/assets/open-source/open-claude-ui-LICENSE.txt.
 */
package com.tingyun.smartmistakebook.core.ui

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object ClaudeLayout {
    val ComposerRadius = 20.dp
    val ComposerPadding = 14.dp
    val ComposerGap = 8.dp
    val ComposerText = TextStyle(fontSize = 15.sp, lineHeight = 21.sp)
    val HeaderText = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
    val ControlText = TextStyle(fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)
    val MessageRadius = 18.dp
    val MessagePaddingX = 16.dp
    val MessagePaddingY = 12.dp
    const val MessageWidthFraction = .85f
    val AssistantInset = 4.dp
}
