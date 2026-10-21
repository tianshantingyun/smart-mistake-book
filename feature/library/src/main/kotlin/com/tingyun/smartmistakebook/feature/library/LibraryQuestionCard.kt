package com.tingyun.smartmistakebook.feature.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tingyun.smartmistakebook.core.domain.MistakeDetailRepository
import com.tingyun.smartmistakebook.core.ui.SmartColors

@Composable
internal fun LibraryQuestionCard(
    mistake: LibraryMistake,
    sourceRepository: MistakeDetailRepository?,
    onClick: () -> Unit,
    onToggleFavorite: (Boolean) -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).testTag("library_item_${mistake.id}"),
        color = SmartColors.OnJade,
        shape = MaterialTheme.shapes.extraLarge,
        border = BorderStroke(0.5.dp, SmartColors.Divider.copy(alpha = 0.6f)),
    ) {
        Column {
            LibraryInlineImages(
                mistake = mistake,
                repository = sourceRepository,
                modifier = Modifier.fillMaxWidth(),
            )
            Column(Modifier.padding(start = 16.dp, end = 12.dp, top = 4.dp, bottom = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = mistake.title,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = SmartColors.Ink,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    IconButton(
                        onClick = { onToggleFavorite(!mistake.isFavorite) },
                        modifier = Modifier.testTag("library_favorite_${mistake.id}"),
                    ) {
                        Icon(
                            imageVector = if (mistake.isFavorite) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                            contentDescription = if (mistake.isFavorite) "取消收藏 ${mistake.title}" else "收藏 ${mistake.title}",
                            tint = if (mistake.isFavorite) SmartColors.Jade else SmartColors.InkSecondary,
                        )
                    }
                }
                Text(
                    text = mistake.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = SmartColors.Ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = mistake.contentPath,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelMedium,
                        color = SmartColors.Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Surface(
                        color = if (mistake.mastery == MasteryState.MASTERED) SmartColors.JadeSoft else SmartColors.Paper,
                        shape = MaterialTheme.shapes.extraSmall,
                    ) {
                        Text(
                            text = mistake.mastery.label,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = SmartColors.Ink,
                        )
                    }
                }
            }
        }
    }
}
