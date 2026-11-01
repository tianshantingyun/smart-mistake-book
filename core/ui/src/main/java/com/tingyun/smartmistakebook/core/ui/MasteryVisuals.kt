package com.tingyun.smartmistakebook.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.tingyun.smartmistakebook.core.model.MasteryStatus

fun masteryStatusColor(status: MasteryStatus): Color = when (status) {
    MasteryStatus.MASTERED -> SmartColors.JadeActive
    MasteryStatus.LEARNING -> SmartColors.LearningAccent
    MasteryStatus.CONFLICTED -> SmartColors.PendingAccent
    MasteryStatus.STALE -> SmartColors.ReadingAccent
    MasteryStatus.UNKNOWN -> SmartColors.InkMuted
}

fun masteryEstimateColor(value: Double): Color = when {
    !value.isFinite() -> SmartColors.InkMuted
    value >= MASTERY_STRONG_THRESHOLD -> SmartColors.JadeActive
    value >= MASTERY_FAIR_THRESHOLD -> SmartColors.LearningAccent
    else -> SmartColors.PendingAccent
}

internal fun normalizedMasteryProgress(value: Float): Float =
    if (value.isFinite()) value.coerceIn(0f, 1f) else 0f

/** The enclosing row provides the estimate and evidence as readable semantics. */
@Composable
fun MasteryEstimateBar(
    value: Float,
    color: Color,
    modifier: Modifier = Modifier,
    hasEvidence: Boolean = true,
) {
    val motion = LocalClaudeMotionEnabled.current
    val target = if (hasEvidence) normalizedMasteryProgress(value) else 0f
    val progress by animateFloatAsState(target, ClaudeMotion.spec(motion, ClaudeMotion.RevealMillis),
        label = "mastery-estimate-update")
    val tint by animateColorAsState(color, ClaudeMotion.spec(motion), label = "mastery-status-update")
    Box(modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp))
        .background(SmartColors.Track).clearAndSetSemantics {}) {
        val displayed = if (motion) progress else target
        if (displayed > 0f) Box(Modifier.fillMaxWidth(displayed).height(7.dp)
            .clip(RoundedCornerShape(4.dp)).background(if (motion) tint else color))
    }
}
