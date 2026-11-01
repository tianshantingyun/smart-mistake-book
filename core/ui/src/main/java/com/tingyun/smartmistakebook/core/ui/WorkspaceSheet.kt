package com.tingyun.smartmistakebook.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Shared sheet chrome; callers own selection, validation, confirmation and dismissal. */
@Composable
fun WorkspaceSheet(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    titleModifier: Modifier = Modifier,
    subtitleModifier: Modifier = Modifier,
    actions: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.fillMaxSize().imePadding().padding(top = 24.dp)
            .claudeDismissOnEscape(onDismiss = onDismiss), contentAlignment = Alignment.BottomCenter) {
            Surface(modifier.widthIn(max = 600.dp).fillMaxWidth().heightIn(max = maxHeight)
                .claudeModalEntrance(), color = SmartColors.ContentSurface,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                border = BorderStroke(1.dp, SmartColors.Divider), shadowElevation = 12.dp) {
                Column {
                    Surface(Modifier.align(Alignment.CenterHorizontally).padding(top = 10.dp).width(32.dp).height(4.dp),
                        color = SmartColors.Track, shape = RoundedCornerShape(2.dp)) {}
                    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(title, titleModifier.weight(1f).semantics { heading() }, color = SmartColors.Ink,
                            style = MaterialTheme.typography.titleLarge)
                        ClaudeIconButton(onDismiss) { Icon(Icons.Outlined.Close, "关闭对话框") }
                    }
                    if (!subtitle.isNullOrBlank()) Text(subtitle, subtitleModifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        color = SmartColors.InkSecondary, style = MaterialTheme.typography.bodySmall)
                    PaperDivider()
                    Column(Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
                    PaperDivider()
                    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp), content = actions)
                }
            }
        }
    }
}

/** Single-choice row with one touch target and an explicit accessible radio role. */
@Composable
fun WorkspaceChoiceRow(
    label: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier.fillMaxWidth().selectable(selected, role = Role.RadioButton, onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) SmartColors.JadeSoft else SmartColors.ContentSurface,
        border = BorderStroke(1.dp, if (selected) SmartColors.Jade else SmartColors.Divider)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected, onClick = null)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(label, color = SmartColors.Ink, style = MaterialTheme.typography.titleMedium)
                Text(description, color = SmartColors.InkSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
