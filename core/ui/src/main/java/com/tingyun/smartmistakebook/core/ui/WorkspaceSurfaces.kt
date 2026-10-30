package com.tingyun.smartmistakebook.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.dp

/** A shared landing-page identity, with actions that move below large text. */
@Composable
fun WorkspacePageHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    Surface(modifier.fillMaxWidth(), color = Color.Transparent, contentColor = SmartColors.Ink) {
        BoxWithConstraints(Modifier.padding(horizontal = 4.dp, vertical = 14.dp)) {
            val showNotebook = actions == null && maxWidth >= 300.dp &&
                LocalDensity.current.fontScale <= 1.3f && subtitle.orEmpty().length <= 16
            @Composable fun Identity(modifier: Modifier = Modifier) {
                Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, color = SmartColors.Ink, style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
                    if (!subtitle.isNullOrBlank()) Text(subtitle, color = SmartColors.InkSecondary,
                        style = MaterialTheme.typography.bodySmall.copy(lineBreak = LineBreak.Paragraph))
                }
            }
            if (actions != null && (maxWidth < 280.dp || LocalDensity.current.fontScale > 1.3f)) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Identity()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically, content = actions)
                }
            } else {
                Row(Modifier.fillMaxWidth().defaultMinSize(minHeight = 40.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Identity(Modifier.weight(1f))
                    if (showNotebook) StudioNotebookMark()
                    actions?.invoke(this)
                }
            }
        }
    }
}

/** The same content surface for forms, summaries, result cards and settings groups. */
@Composable
fun WorkspacePanel(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    containerColor: Color = ContentSurface,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = containerColor,
        contentColor = Ink, border = BorderStroke(1.dp, SmartColors.Divider), shadowElevation = 0.dp) {
        Column(Modifier.fillMaxWidth().padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}
