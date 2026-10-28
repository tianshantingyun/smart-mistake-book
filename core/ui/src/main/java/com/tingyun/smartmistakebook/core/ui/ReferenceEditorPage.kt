package com.tingyun.smartmistakebook.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** Moves the same editor between layouts, keeping field and scroll state when the window changes. */
@Composable
fun ReferenceEditorPage(
    modifier: Modifier = Modifier,
    imageUris: List<String> = emptyList(),
    content: @Composable ColumnScope.() -> Unit,
) {
    val currentContent by rememberUpdatedState(content)
    val editor = remember { movableContentOf { RootPageColumn(content = currentContent) } }
    var splitRatio by rememberSaveable { mutableFloatStateOf(.5f) }
    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        if (imageUris.isNotEmpty() && maxWidth >= 840.dp && maxHeight >= 480.dp) {
            ClaudeSplitPane(splitRatio, { splitRatio = it }, Modifier.widthIn(max = 1240.dp), left = {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
                    .testTag("editor_source_reference"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader("原图参考")
                    imageUris.forEachIndexed { index, uri ->
                        EditorReferenceImage(uri, index)
                    }
                }
            }, right = { editor() })
        } else {
            editor()
        }
    }
}

@Composable
private fun EditorReferenceImage(uri: String, index: Int) {
    var viewing by remember(uri) { mutableStateOf(false) }
    var state by remember(uri) { mutableStateOf(LocalImageLoadState.LOADING) }
    WorkspacePanel(contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
        BoundedLocalImage(uri, "参考图 ${index + 1}", expanded = true,
            modifier = Modifier.fillMaxWidth(), onLoadStateChange = { state = it })
        if (state == LocalImageLoadState.UNAVAILABLE) {
            Text("这张参考图暂时无法读取，编辑内容仍保留。", color = InkSecondary,
                style = MaterialTheme.typography.bodySmall)
        } else if (state == LocalImageLoadState.AVAILABLE) {
            InlineActionButton("查看大图", { viewing = true })
        }
    }
    if (viewing) LocalImageViewer(uri, "参考图 ${index + 1}", { viewing = false })
}
