package com.tingyun.smartmistakebook.feature.capture

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tingyun.smartmistakebook.core.ui.WorkspacePanel

/** Keep the existing form spacing while sharing the app's panel surface. */
@Composable
internal fun CapturePanel(
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    WorkspacePanel(modifier = modifier, contentPadding = PaddingValues(0.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = verticalArrangement, content = content)
    }
}
