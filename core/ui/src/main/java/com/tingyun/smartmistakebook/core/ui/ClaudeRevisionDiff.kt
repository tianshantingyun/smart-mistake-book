package com.tingyun.smartmistakebook.core.ui

// Presentation port of StreamingDiffViewer.tsx (Caho1/open-claude-ui c1f923ae, MIT).
// Receives the existing field diff; never parses or commits a domain revision.
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ClaudeRevisionDiff(label: String, before: String?, after: String?, modifier: Modifier = Modifier) {
    val previous = before?.takeIf(String::isNotBlank) ?: "（无）"
    val next = after?.takeIf(String::isNotBlank) ?: "（无）"
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().background(SubtleSurface).padding(start = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, Modifier.weight(1f), color = Ink, style = ClaudeLayout.ControlText, fontWeight = FontWeight.SemiBold)
            ClaudeCopyButton("$label\n修改前：$previous\n修改后：$next")
        }
        SelectionContainer {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (maxWidth >= 480.dp && LocalDensity.current.fontScale <= 1.3f) {
                    Row {
                        DiffValue("−", "修改前：$previous", ErrorText.copy(alpha = .07f), Modifier.weight(1f))
                        DiffValue("+", "修改后：$next", Jade.copy(alpha = .07f), Modifier.weight(1f))
                    }
                } else Column {
                    DiffValue("−", "修改前：$previous", ErrorText.copy(alpha = .07f))
                    DiffValue("+", "修改后：$next", Jade.copy(alpha = .07f))
                }
            }
        }
    }
}

@Composable
private fun DiffValue(marker: String, text: String, background: Color, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().background(background).padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(marker, color = InkSecondary, style = MaterialTheme.typography.bodySmall)
        Text(text, Modifier.weight(1f), color = Ink, style = MaterialTheme.typography.bodySmall)
    }
}
