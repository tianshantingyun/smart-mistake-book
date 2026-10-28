package com.tingyun.smartmistakebook.core.ui

// Caho1/open-claude-ui SegmentedControl.tsx / cds-theme.css, c1f923ae; MIT, Copyright 2026 Caho1.
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip as MaterialFilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.SelectableChipElevation
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ClaudeFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    shape: Shape = RoundedCornerShape(12.dp),
    colors: SelectableChipColors = FilterChipDefaults.filterChipColors(
        containerColor = Ink.copy(alpha = .04f), labelColor = InkSecondary,
        selectedContainerColor = JadeSoft, selectedLabelColor = JadeActive,
    ),
    elevation: SelectableChipElevation? = null,
    border: BorderStroke? = null,
    interactionSource: MutableInteractionSource? = null,
) = MaterialFilterChip(selected = selected, onClick = onClick, label = label,
    modifier = modifier.defaultMinSize(minHeight = 48.dp), enabled = enabled,
    leadingIcon = leadingIcon, trailingIcon = trailingIcon, shape = shape,
    colors = colors, elevation = elevation, border = border, interactionSource = interactionSource)

data class ClaudeSegment(val id: String, val label: String, val testTag: String = "")

/** Controlled selection; overflow scrolls rather than hiding the only names of native options. */
@Composable
fun ClaudeSegmentedControl(
    segments: List<ClaudeSegment>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    if (segments.isEmpty()) return
    val focusRequests = remember(segments.map { it.id }) { List(segments.size) { FocusRequester() } }
    var requestedFocusId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(requestedFocusId, selectedId) {
        if (requestedFocusId != null && requestedFocusId == selectedId) {
            val index = segments.indexOfFirst { it.id == selectedId }
            if (index >= 0) focusRequests[index].requestFocus()
            requestedFocusId = null
        }
    }
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1 else 1
    val motion = LocalClaudeMotionEnabled.current
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val minCell = 76.dp * LocalDensity.current.fontScale
        val cellWidth = maxOf((maxWidth - 2.dp) / segments.size, minCell)
        val selectedIndex = segments.indexOfFirst { it.id == selectedId }
        val thumbOffset by animateDpAsState(cellWidth * selectedIndex.coerceAtLeast(0),
            animationSpec = if (motion) tween(ClaudeMotion.SnapMillis, easing = ClaudeMotion.Snap) else androidx.compose.animation.core.snap(),
            label = "cds-segment-thumb")
        Box(Modifier.horizontalScroll(rememberScrollState())) {
            Box(Modifier.width(cellWidth * segments.size + 2.dp).height(IntrinsicSize.Min)
                .background(Ink.copy(alpha = .06f), CircleShape).padding(1.dp)) {
                val boundedOffset = thumbOffset.coerceIn(0.dp, cellWidth * (segments.size - 1))
                if (selectedIndex >= 0) Box(Modifier.matchParentSize().padding(start = boundedOffset,
                    end = cellWidth * (segments.size - 1) - boundedOffset)
                    .shadow(1.dp, CircleShape).background(ContentSurface, CircleShape))
                Row(Modifier.selectableGroup()) {
                    segments.forEachIndexed { index, segment ->
                        val selected = segment.id == selectedId
                        var focused by remember(segment.id) { mutableStateOf(false) }
                        Box(Modifier.width(cellWidth).fillMaxHeight().defaultMinSize(minHeight = 48.dp)
                            .focusRequester(focusRequests[index])
                            .onFocusChanged { focused = it.isFocused }
                            .focusProperties { canFocus = enabled && index == selectedIndex.coerceAtLeast(0) }
                            .onKeyEvent { event ->
                                if (!enabled || event.type != KeyEventType.KeyDown) false else {
                                    val next = when (event.key) {
                                        Key.DirectionLeft -> (index - direction + segments.size) % segments.size
                                        Key.DirectionRight -> (index + direction + segments.size) % segments.size
                                        Key.MoveHome -> 0
                                        Key.MoveEnd -> segments.lastIndex
                                        else -> -1
                                    }
                                    if (next < 0) false else {
                                        if (segments[next].id != selectedId) {
                                            requestedFocusId = segments[next].id
                                            onSelect(segments[next].id)
                                        }
                                        true
                                    }
                                }
                            }
                            .clip(CircleShape)
                            .then(if (focused && enabled) Modifier.border(2.dp,
                                claudeFocusIndicatorColor(ContentSurface), CircleShape) else Modifier)
                            .selectable(selected, enabled, Role.Tab) {
                                if (!selected) onSelect(segment.id)
                            }.testTag(segment.testTag).padding(horizontal = 12.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center) {
                            Text(segment.label, color = if (selected) JadeDark else InkSecondary,
                                style = ClaudeLayout.ControlText,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                        }
                    }
                }
            }
        }
    }
}
