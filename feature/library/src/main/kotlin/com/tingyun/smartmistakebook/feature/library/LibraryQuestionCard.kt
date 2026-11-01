package com.tingyun.smartmistakebook.feature.library

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import com.tingyun.smartmistakebook.core.ui.ClaudeIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tingyun.smartmistakebook.core.domain.MistakeDetailRepository
import com.tingyun.smartmistakebook.core.ui.SmartColors
import com.tingyun.smartmistakebook.core.ui.ClaudeCard
import com.tingyun.smartmistakebook.core.ui.ClaudeMotion
import com.tingyun.smartmistakebook.core.ui.LocalClaudeMotionEnabled

@Composable
internal fun LibraryQuestionCard(
    mistake: LibraryMistake,
    sourceRepository: MistakeDetailRepository?,
    onClick: () -> Unit,
    onToggleFavorite: (Boolean) -> Unit,
) {
    ClaudeCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).testTag("library_item_${mistake.id}"),
        shape = RoundedCornerShape(18.dp),
        bordered = true,
        elevation = 0.dp,
    ) {
        Column {
            LibraryInlineImages(
                mistake = mistake,
                repository = sourceRepository,
                modifier = Modifier.fillMaxWidth(),
                captionContent = { source, previewAction ->
                    Box(Modifier.padding(start = if (source.isBlank()) 16.dp else 0.dp,
                        end = if (source.isBlank()) 12.dp else 0.dp)) {
                        LibraryCardHeading(mistake, source, onToggleFavorite, previewAction)
                    }
                },
                fallbackContent = {
                    if (mistake.summary.isNotBlank() && mistake.summary.trim() != mistake.title.trim()) Text(
                        text = mistake.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = SmartColors.Ink,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun LibraryCardHeading(
    mistake: LibraryMistake,
    source: String,
    onToggleFavorite: (Boolean) -> Unit,
    previewAction: (@Composable () -> Unit)?,
) {
    val favoriteTint = libraryStateColor(
        if (mistake.isFavorite) SmartColors.Jade else SmartColors.InkSecondary,
        label = "library-favorite-color", durationMillis = ClaudeMotion.PressMillis,
    )
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(mistake.title,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, lineHeight = 24.sp),
                fontWeight = FontWeight.SemiBold,
                color = SmartColors.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.weight(1f)) { LibraryCardMetadata(mistake, source) }
                previewAction?.invoke()
            }
        }
        IconButton(onClick = { onToggleFavorite(!mistake.isFavorite) },
            modifier = Modifier.align(Alignment.Top).testTag("library_favorite_${mistake.id}").semantics {
                stateDescription = if (mistake.isFavorite) "已收藏" else "未收藏"
            }) {
            Icon(if (mistake.isFavorite) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                contentDescription = if (mistake.isFavorite) "取消收藏 ${mistake.title}" else "收藏 ${mistake.title}",
                tint = favoriteTint, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun LibraryCardMetadata(mistake: LibraryMistake, source: String) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val stacked = maxWidth < 180.dp || LocalDensity.current.fontScale > 1.3f
        @Composable fun ContentPath(modifier: Modifier = Modifier) {
            Text(if (source.isBlank()) mistake.contentPath else "$source · ${mistake.contentPath}", modifier = modifier,
                style = MaterialTheme.typography.labelMedium, color = SmartColors.InkSecondary,
                maxLines = if (stacked) 2 else 1, overflow = TextOverflow.Ellipsis)
        }
        if (stacked) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ContentPath()
            LibraryMasteryBadge(mistake.mastery)
        } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ContentPath(Modifier.weight(1f))
            LibraryMasteryBadge(mistake.mastery)
        }
    }
}

@Composable
internal fun LibraryMasteryBadge(mastery: MasteryState) {
    val background = libraryStateColor(when (mastery) {
        MasteryState.LEARNING, MasteryState.MASTERED -> SmartColors.JadeSoft
        MasteryState.CONFLICTED, MasteryState.STALE -> SmartColors.ErrorWarm.copy(alpha = .12f)
        MasteryState.UNKNOWN -> Color.Transparent
    }, label = "library-mastery-surface")
    val tint = libraryStateColor(when (mastery) {
        MasteryState.LEARNING, MasteryState.MASTERED -> SmartColors.JadeDark
        MasteryState.CONFLICTED, MasteryState.STALE -> SmartColors.ErrorText
        MasteryState.UNKNOWN -> SmartColors.InkSecondary
    }, label = "library-mastery-icon")
    val icon = when (mastery) {
        MasteryState.UNKNOWN -> Icons.Outlined.HelpOutline
        MasteryState.LEARNING -> Icons.Outlined.AutoStories
        MasteryState.MASTERED -> Icons.Outlined.CheckCircle
        MasteryState.CONFLICTED -> Icons.Outlined.ErrorOutline
        MasteryState.STALE -> Icons.Outlined.Schedule
    }
    Surface(color = background, shape = MaterialTheme.shapes.extraSmall) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, null, modifier = Modifier.size(12.dp), tint = tint)
            Text(mastery.label, style = MaterialTheme.typography.labelMedium, color = SmartColors.Ink)
        }
    }
}

@Composable
private fun libraryStateColor(target: Color, label: String, durationMillis: Int = ClaudeMotion.SnapMillis): Color {
    // A snap spec still updates animated state on a frame; reduced motion must render the target now.
    if (!LocalClaudeMotionEnabled.current) return target
    val color by animateColorAsState(target, animationSpec = ClaudeMotion.spec(true, durationMillis), label = label)
    return color
}
