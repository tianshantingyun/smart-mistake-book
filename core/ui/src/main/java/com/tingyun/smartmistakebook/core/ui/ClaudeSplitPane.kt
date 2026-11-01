package com.tingyun.smartmistakebook.core.ui

// ArtifactsSplitPaneResizer.tsx, Caho1/open-claude-ui c1f923ae; MIT, Copyright 2026 Caho1.
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

internal fun claudeSplitRatio(request: Float, availableWidth: Float, minimumPaneWidth: Float): Float {
    if (!availableWidth.isFinite() || availableWidth <= 0 || !minimumPaneWidth.isFinite() || minimumPaneWidth < 0) return .5f
    val minimum = (minimumPaneWidth / availableWidth).coerceIn(0f, .5f)
    return (if (request.isFinite()) request else .5f).coerceIn(minimum, 1f - minimum)
}

/** Both panes remain reachable; resizing changes presentation only, never the editor instance. */
@Composable
fun ClaudeSplitPane(
    ratio: Float,
    onRatioChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    left: @Composable () -> Unit,
    right: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val available = with(density) { (maxWidth - 48.dp).toPx() }
        val minimumPane = with(density) { 320.dp.toPx() }
        val minimum = if (available > 0) (minimumPane / available).coerceIn(0f, .5f) else .5f
        val bounded = claudeSplitRatio(ratio, available, minimumPane)
        val currentRatio by rememberUpdatedState(bounded)
        val changeRatio by rememberUpdatedState(onRatioChange)
        val direction = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1 else 1
        var dragging by remember { mutableStateOf(false) }
        var cancelled by remember { mutableStateOf(false) }
        var dragStart by remember { mutableFloatStateOf(.5f) }
        fun update(value: Float) = changeRatio(claudeSplitRatio(value, available, minimumPane))
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(bounded)) { left() }
            Box(Modifier.width(48.dp).fillMaxHeight().testTag("editor_split_handle")
                .semantics {
                    contentDescription = "调整原图与编辑区宽度"
                    progressBarRangeInfo = ProgressBarRangeInfo(bounded, minimum..(1f - minimum))
                    setProgress { update(it); true }
                    customActions = listOf(CustomAccessibilityAction("恢复等宽") { update(.5f); true })
                }.onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) false else when (event.key) {
                        Key.DirectionLeft -> { update(currentRatio - .05f * direction); true }
                        Key.DirectionRight -> { update(currentRatio + .05f * direction); true }
                        Key.MoveHome -> { update(.5f); true }
                        Key.Escape -> { if (dragging) { cancelled = true; update(dragStart); dragging = false; true } else false }
                        else -> false
                    }
                }.focusable()
                .pointerInput(available, direction) {
                    detectDragGestures(onDragStart = {
                        dragStart = currentRatio; cancelled = false; dragging = true
                    }, onDragCancel = { if (!cancelled) update(dragStart); dragging = false },
                        onDragEnd = { dragging = false }) { change, amount ->
                        change.consume()
                        if (!cancelled && available > 0) update(currentRatio + amount.x * direction / available)
                    }
                }.pointerInput(available) { detectTapGestures(onDoubleTap = { update(.5f) }) },
                contentAlignment = Alignment.Center) {
                Box(Modifier.width(1.dp).fillMaxHeight().background(if (dragging) JadeActive else Ink.copy(alpha = .1f)))
                Box(Modifier.size(16.dp, 28.dp).background(ContentSurface, RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center) {
                    Box(Modifier.size(3.dp, 12.dp).background(if (dragging) JadeActive else InkSecondary, RoundedCornerShape(2.dp)))
                }
            }
            Box(Modifier.weight(1f - bounded)) { right() }
        }
    }
}
