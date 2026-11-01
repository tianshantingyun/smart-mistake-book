package com.tingyun.smartmistakebook.core.ui

// ModelSelectorEffort.tsx / cds-theme.css, Caho1/open-claude-ui c1f923ae; MIT, Copyright 2026 Caho1.
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu as MaterialDropdownMenu
import androidx.compose.material3.DropdownMenuItem as MaterialDropdownMenuItem
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.MenuItemColors
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties

@Composable
fun ClaudeDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset.Zero,
    scrollState: ScrollState = rememberScrollState(),
    properties: PopupProperties = PopupProperties(focusable = true),
    shape: Shape = RoundedCornerShape(20.dp),
    containerColor: Color = ContentSurface,
    tonalElevation: Dp = 0.dp,
    shadowElevation: Dp = 8.dp,
    border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit,
) = MaterialDropdownMenu(expanded, onDismissRequest, modifier, offset, scrollState, properties,
    shape, containerColor, tonalElevation, shadowElevation, border, content)

@Composable
fun ClaudeDropdownMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    colors: MenuItemColors = MenuDefaults.itemColors(textColor = Ink,
        leadingIconColor = InkSecondary, trailingIconColor = InkSecondary),
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
    interactionSource: MutableInteractionSource? = null,
) {
    ProvideTextStyle(ClaudeLayout.ControlText) {
        MaterialDropdownMenuItem(text, onClick,
            modifier.defaultMinSize(minHeight = 48.dp).clip(RoundedCornerShape(8.dp)),
            leadingIcon, trailingIcon, enabled, colors, contentPadding, interactionSource)
    }
}
