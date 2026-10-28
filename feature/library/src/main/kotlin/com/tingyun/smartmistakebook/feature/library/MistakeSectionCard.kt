package com.tingyun.smartmistakebook.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tingyun.smartmistakebook.core.ui.ClaudeDisclosure
import com.tingyun.smartmistakebook.core.ui.SmartColors

@Composable
internal fun MistakeModuleHeader(
    showDivider: Boolean = true,
    background: Color = SmartColors.ContentSurface,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth().background(background)) {
        content()
        if (showDivider) HorizontalDivider(color = SmartColors.Divider)
    }
}

@Composable
internal fun MistakeSectionCard(
    title: String,
    modifier: Modifier = Modifier,
    expanded: Boolean = true,
    action: (@Composable () -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    headerColor: Color = SmartColors.SubtleSurface,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = SmartColors.ContentSurface,
        shadowElevation = 1.dp,
    ) {
        Column {
            MistakeModuleHeader(showDivider = expanded, background = headerColor) {
                BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    @Composable
                    fun Title(modifier: Modifier = Modifier) {
                        Text(
                            text = title,
                            modifier = modifier.semantics { heading() },
                            color = SmartColors.Ink,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    if (action != null && (maxWidth < 240.dp || LocalDensity.current.fontScale > 1.3f)) {
                        Column(
                            Modifier.defaultMinSize(minHeight = 40.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Title()
                            action()
                        }
                    } else {
                        Row(
                            Modifier.fillMaxWidth().defaultMinSize(minHeight = 40.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Title(Modifier.weight(1f))
                            action?.invoke()
                        }
                    }
                }
            }
            ClaudeDisclosure(visible = expanded) {
                Column(
                    Modifier.fillMaxWidth().padding(contentPadding),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    content = content,
                )
            }
        }
    }
}
