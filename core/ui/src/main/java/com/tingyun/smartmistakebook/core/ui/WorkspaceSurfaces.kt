package com.tingyun.smartmistakebook.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion

enum class WorkspaceNoticeTone { INFO, SUCCESS, WARNING, ERROR }

/** Feedback keeps its semantic meaning in every workflow, including errors shown inside a modal. */
@Composable
fun WorkspaceNotice(
    message: String,
    modifier: Modifier = Modifier,
    tone: WorkspaceNoticeTone = WorkspaceNoticeTone.INFO,
    title: String? = null,
) {
    val (surface, accent, icon) = when (tone) {
        WorkspaceNoticeTone.INFO -> Triple(SmartColors.LearningSurface, SmartColors.LearningAccent, Icons.Outlined.Info)
        WorkspaceNoticeTone.SUCCESS -> Triple(SmartColors.JadeSoft, SmartColors.JadeDark, Icons.Outlined.CheckCircle)
        WorkspaceNoticeTone.WARNING -> Triple(SmartColors.PendingSurface, SmartColors.PendingAccent, Icons.Outlined.WarningAmber)
        WorkspaceNoticeTone.ERROR -> Triple(SmartColors.ErrorSurface, SmartColors.ErrorText, Icons.Outlined.ErrorOutline)
    }
    val emphasized = tone == WorkspaceNoticeTone.WARNING || tone == WorkspaceNoticeTone.ERROR
    Surface(modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        shape = RoundedCornerShape(10.dp), color = if (emphasized) surface else Color.Transparent,
        border = if (emphasized) BorderStroke(1.dp, accent.copy(alpha = .15f)) else null) {
        Row(Modifier.padding(horizontal = if (emphasized) 12.dp else 0.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
            Icon(icon, null, Modifier.padding(top = 2.dp).size(18.dp), tint = accent)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                title?.let { Text(it, color = accent, style = MaterialTheme.typography.titleSmall) }
                Text(message, color = if (tone == WorkspaceNoticeTone.ERROR) accent else SmartColors.InkSecondary,
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** Compact progress for existing multi-step flows; it never navigates or marks work complete. */
@Composable
fun WorkspaceJourney(steps: List<String>, currentStep: Int, modifier: Modifier = Modifier,
    onStepClick: ((Int) -> Unit)? = null, enabled: Boolean = true) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        steps.forEachIndexed { index, label ->
            val active = index == currentStep
            val complete = index < currentStep
            val ink = if (active || complete) SmartColors.JadeDark else SmartColors.InkSecondary
            Column(Modifier.weight(1f).then(if (onStepClick != null) Modifier.clickable(enabled = enabled,
                role = Role.Tab, onClickLabel = "查看$label") { onStepClick(index) } else Modifier)
                .defaultMinSize(minHeight = SmartDimens.MinimumTouchTarget), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(shape = RoundedCornerShape(9.dp), color = Color.Transparent) {
                        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                            if (complete) Icon(Icons.Outlined.CheckCircle, null, Modifier.size(16.dp), tint = ink)
                            else Text("${index + 1}", color = ink, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    Text(label, Modifier.weight(1f), color = ink, style = if (active) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelMedium)
                }
                Surface(Modifier.fillMaxWidth().height(2.dp), color = if (active || complete) SmartColors.Jade else SmartColors.Divider) { }
            }
        }
    }
}

/** A shared landing-page identity, with actions that move below large text. */
@Composable
fun WorkspacePageHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    Surface(modifier.fillMaxWidth(), color = Color.Transparent, contentColor = SmartColors.Ink) {
        BoxWithConstraints(Modifier.padding(top = 36.dp, bottom = 20.dp)) {
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
                    actions?.invoke(this)
                }
            }
        }
    }
}

/** Flat sections by default; explicit colored surfaces retain an object boundary. */
@Composable
fun WorkspacePanel(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues? = null,
    containerColor: Color = Color.Transparent,
    content: @Composable ColumnScope.() -> Unit,
) {
    val surfaced = containerColor != Color.Transparent
    val resolvedPadding = contentPadding ?: if (surfaced) PaddingValues(16.dp)
        else PaddingValues(horizontal = 0.dp, vertical = 16.dp)
    val shape = RoundedCornerShape(if (surfaced) SmartDimens.SurfaceRadius else 0.dp)
    Surface(modifier.fillMaxWidth().then(if (surfaced) Modifier.claudeSurfaceShadow(shape) else Modifier), shape = shape,
        color = containerColor, contentColor = Ink,
        border = if (surfaced) BorderStroke(1.dp, SmartColors.Divider) else null, shadowElevation = 0.dp) {
        Column {
            if (!surfaced) PaperDivider()
            Column(Modifier.fillMaxWidth().padding(resolvedPadding),
                verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
    }
}
