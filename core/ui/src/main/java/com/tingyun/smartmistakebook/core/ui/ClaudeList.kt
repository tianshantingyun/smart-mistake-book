package com.tingyun.smartmistakebook.core.ui

// Native adaptations of ClaudeDesktopHeader / DraggableSessionList (Caho1, c1f923ae, MIT).
import androidx.compose.foundation.background
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Mobile arrangement of the upstream sidebar item paint; destination identity stays with the host. */
@Composable
fun ClaudeNavigationItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.defaultMinSize(minHeight = 64.dp).clip(RoundedCornerShape(8.dp))
        .claudeInteractiveRow(onClick, selected, role = Role.Tab, selectedFill = Color.Transparent)
        .semantics { this.selected = selected }.padding(horizontal = 6.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally) {
        val motion = LocalClaudeMotionEnabled.current
        val target = if (selected) JadeSoft else Color.Transparent
        val animated by animateColorAsState(target, ClaudeMotion.spec(motion), label = "navigation-capsule")
        Box(Modifier.width(52.dp).height(28.dp).clip(RoundedCornerShape(14.dp))
            .background(if (motion) animated else target), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(20.dp), tint = if (selected) JadeActive else InkSecondary)
        }
        Text(label, color = if (selected) JadeDark else InkSecondary, style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
fun ClaudePageHeader(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    backModifier: Modifier = Modifier,
    backLabel: String = "返回",
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(ContentSurface)
        .padding(horizontal = 6.dp, vertical = 6.dp).defaultMinSize(minHeight = 48.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (onBack != null) ClaudeIconButton(onBack, backModifier) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, backLabel, Modifier.size(20.dp), tint = Ink)
        }
        Text(title, Modifier.weight(1f), color = Ink, style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold)
        actions()
    }
}

@Composable
fun ClaudeSettingsRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconContainerColor: Color = JadeSoft,
    iconTint: Color = JadeDark,
) {
    Row(modifier.fillMaxWidth().defaultMinSize(minHeight = 72.dp).clip(RoundedCornerShape(16.dp))
        .claudeInteractiveRow(onClick).semantics(mergeDescendants = true) {}
        .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(iconContainerColor),
            contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(20.dp), tint = iconTint)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = Ink, style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium)
            Text(subtitle, color = InkSecondary, style = MaterialTheme.typography.bodySmall)
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(16.dp), tint = InkSecondary)
    }
}
