package com.tingyun.smartmistakebook.feature.capture

import com.tingyun.smartmistakebook.core.ui.SmartColors
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.tingyun.smartmistakebook.core.ui.WorkspacePanel

/** Keep the existing form spacing while sharing the app's panel surface. */
@Composable
internal fun CapturePanel(
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    WorkspacePanel(modifier = modifier, containerColor = SmartColors.ContentSurface) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = verticalArrangement, content = content)
    }
}
