package com.tingyun.smartmistakebook.core.ui

// Native port of Caho1/open-claude-ui ConfirmationDialog.tsx, c1f923ae; MIT, Copyright 2026 Caho1.
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Modal chrome only: callers retain their validation, confirmation and dismissal commands. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun ClaudeAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    shape: Shape = RoundedCornerShape(24.dp),
    containerColor: Color = ContentSurface,
    iconContentColor: Color = JadeActive,
    titleContentColor: Color = Ink,
    textContentColor: Color = InkSecondary,
    tonalElevation: Dp = 0.dp,
    properties: DialogProperties = DialogProperties(),
    dangerConfirm: Boolean = false,
    emphasizeConfirm: Boolean = true,
) {
    Dialog(onDismissRequest, properties = properties.copyForClaudeWidth()) {
        BoxWithConstraints(Modifier.fillMaxSize().imePadding().padding(24.dp)
            .claudeDismissOnEscape(enabled = properties.dismissOnBackPress, onDismiss = onDismissRequest),
            contentAlignment = Alignment.Center) {
            Surface(modifier.widthIn(max = 520.dp).fillMaxWidth().heightIn(max = maxHeight)
                .claudeModalEntrance(),
                shape = shape, color = containerColor, tonalElevation = tonalElevation,
                shadowElevation = 12.dp) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (icon != null) CompositionLocalProvider(LocalContentColor provides iconContentColor, content = icon)
                        Column(Modifier.weight(1f).padding(top = 8.dp)) {
                            if (title != null) CompositionLocalProvider(LocalContentColor provides titleContentColor) {
                                ProvideTextStyle(MaterialTheme.typography.titleLarge, title)
                            }
                        }
                        if (properties.dismissOnBackPress || properties.dismissOnClickOutside) {
                            ClaudeIconButton(onClick = onDismissRequest) {
                                Icon(Icons.Outlined.Close, contentDescription = "关闭对话框")
                            }
                        }
                    }
                    if (text != null) {
                        Column(Modifier.weight(1f, fill = false).fillMaxWidth()
                            .verticalScroll(rememberScrollState())) {
                            CompositionLocalProvider(LocalContentColor provides textContentColor) {
                                ProvideTextStyle(MaterialTheme.typography.bodyMedium, text)
                            }
                        }
                    }
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        CompositionLocalProvider(LocalClaudeDialogButtonKind provides ClaudeDialogButtonKind.CANCEL) {
                            dismissButton?.invoke()
                        }
                        CompositionLocalProvider(LocalClaudeDialogButtonKind provides if (emphasizeConfirm) ClaudeDialogButtonKind.CONFIRM else ClaudeDialogButtonKind.CANCEL,
                            LocalClaudeDangerConfirm provides dangerConfirm) { confirmButton() }
                    }
                }
            }
        }
    }
}

private fun DialogProperties.copyForClaudeWidth() = DialogProperties(
    dismissOnBackPress = dismissOnBackPress,
    dismissOnClickOutside = dismissOnClickOutside,
    securePolicy = securePolicy,
    usePlatformDefaultWidth = false,
    decorFitsSystemWindows = decorFitsSystemWindows,
)
