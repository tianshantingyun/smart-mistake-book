package com.tingyun.smartmistakebook.feature.library

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HideImage
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tingyun.smartmistakebook.core.domain.MistakeDetailRepository
import com.tingyun.smartmistakebook.core.domain.MistakeDetailState
import com.tingyun.smartmistakebook.core.domain.MistakeSourceAsset
import com.tingyun.smartmistakebook.core.domain.MistakeSourceLocation
import com.tingyun.smartmistakebook.core.ui.BoundedLocalImage
import com.tingyun.smartmistakebook.core.ui.LocalImageLoadState
import com.tingyun.smartmistakebook.core.ui.LocalImageViewer
import com.tingyun.smartmistakebook.core.ui.InlineActionButton
import com.tingyun.smartmistakebook.core.ui.SmartColors
import com.tingyun.smartmistakebook.core.ui.ClaudeQuietDots
import kotlinx.coroutines.CancellationException

/** Visible pages read their saved local pictures; there is no generation or write port. */
@Composable
internal fun LibraryInlineImages(
    mistake: LibraryMistake,
    repository: MistakeDetailRepository?,
    modifier: Modifier = Modifier,
    captionContent: @Composable (String, (@Composable () -> Unit)?) -> Unit = { text, action ->
        SourceCaption(text, action)
    },
    fallbackContent: @Composable () -> Unit = {},
) {
    if (repository == null) {
        Column(modifier) {
            MistakeModuleHeader { captionContent("", null) }
            fallbackContent()
        }
        return
    }
    val preview by key(repository, mistake.id, mistake.imageVersionKey) {
        produceState<InlineImagesState>(InlineImagesState.Loading) {
            try {
                repository.observe(mistake.id).collect { value = it.inlineImages() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                value = InlineImagesState.Unavailable
            }
        }
    }
    val current = preview
    Column(modifier) {
        when (current) {
            InlineImagesState.Loading -> {
                MistakeModuleHeader { captionContent("", null) }
                fallbackContent()
                PreviewMessage("正在加载题图…", loading = true)
            }
            InlineImagesState.Unavailable -> {
                MistakeModuleHeader { captionContent("", null) }
                PreviewMessage("题图暂时无法显示")
                fallbackContent()
            }
            is InlineImagesState.Images -> {
                if (current.assets.isEmpty()) {
                    MistakeModuleHeader { captionContent("", null) }
                    fallbackContent()
                }
                current.assets.forEachIndexed { index, asset ->
                    key(asset.sourceAssetId) {
                        InlineImage(asset, "${mistake.title}，第${index + 1}张题图", index + 1, current.assets.size,
                            captionContent = { source, action ->
                                if (index == 0) captionContent(source, action) else SourceCaption(source, action)
                            },
                            fallbackContent = if (index == 0) fallbackContent else ({}))
                    }
                }
            }
        }
    }
}

@Composable
private fun InlineImage(asset: MistakeSourceAsset, description: String, position: Int, total: Int,
    captionContent: @Composable (String, (@Composable () -> Unit)?) -> Unit,
    fallbackContent: @Composable () -> Unit) {
    val sourceTitle = sourceAssetDisplayTitle(asset.role, 1, 1, isHistorical = false, isExplanation = false)
    val caption = if (total > 1) "$sourceTitle · $position/$total" else sourceTitle
    val uri = (asset.location as? MistakeSourceLocation.Available)?.localUri
    if (uri == null) {
        MistakeModuleHeader {
            Box(Modifier.padding(start = 16.dp, end = 12.dp)) { captionContent(caption, null) }
        }
        PreviewMessage("题图暂时无法显示")
        fallbackContent()
        return
    }
    var loadState by remember(asset.sourceAssetId, uri) { mutableStateOf(LocalImageLoadState.LOADING) }
    var inViewport by remember(asset.sourceAssetId, uri) { mutableStateOf(false) }
    var viewing by rememberSaveable(asset.sourceAssetId, uri) { mutableStateOf(false) }
    // A single lazy item may contain many pages. Composition alone does not bound its decodes.
    // Include the caption so its preview action remains usable at the viewport edge.
    Column(Modifier.fillMaxWidth().onGloballyPositioned { coordinates ->
        val visible = coordinates.boundsInWindow()
        inViewport = visible.width > 0f && visible.height > 0f
    }) {
        MistakeModuleHeader {
            Box(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp)) {
                captionContent(caption) {
                    InlineActionButton("放大", { viewing = true }, enabled = inViewport && loadState == LocalImageLoadState.AVAILABLE,
                        contentDescription = "放大$description")
                }
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val imageHeight = (maxWidth * (asset.height.toFloat() / asset.width)).coerceIn(80.dp, 480.dp)
            val imageModifier = Modifier.fillMaxWidth().height(imageHeight).clip(MaterialTheme.shapes.small)
            val frameModifier = if (loadState == LocalImageLoadState.UNAVAILABLE)
                Modifier.fillMaxWidth().heightIn(min = imageHeight).clip(MaterialTheme.shapes.small) else imageModifier
            Box(frameModifier.background(SmartColors.SubtleSurface), contentAlignment = Alignment.Center) {
                if (loadState == LocalImageLoadState.UNAVAILABLE) {
                    PreviewMessage("题图暂时无法显示")
                }
                if (inViewport) {
                    BoundedLocalImage(
                        imageUri = uri,
                        contentDescription = description,
                        expanded = true,
                        expandedMaxHeight = 480.dp,
                        modifier = imageModifier,
                        onLoadStateChange = { loadState = it },
                    )
                } else {
                    // Retain geometry and the scroll target without retaining a bitmap offscreen.
                    Box(imageModifier.semantics { contentDescription = description })
                }
            }
        }
        if (loadState == LocalImageLoadState.UNAVAILABLE) fallbackContent()
        if (viewing) LocalImageViewer(uri, "$sourceTitle · $description", onDismiss = { viewing = false })
    }
}

@Composable
private fun SourceCaption(text: String, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (text.isNotBlank()) Text(text, modifier = Modifier.weight(1f),
            color = SmartColors.InkSecondary, style = MaterialTheme.typography.labelMedium)
        action?.invoke()
    }
}

@Composable
private fun PreviewMessage(message: String, loading: Boolean = false) {
    Surface(color = SmartColors.SubtleSurface, shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.defaultMinSize(minHeight = if (loading) 40.dp else 64.dp)
            .padding(horizontal = 16.dp, vertical = if (loading) 8.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (loading) ClaudeQuietDots() else Icon(Icons.Outlined.HideImage, null,
                modifier = Modifier.size(20.dp), tint = SmartColors.InkSecondary)
            Text(message, color = SmartColors.Ink, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private sealed interface InlineImagesState {
    data object Loading : InlineImagesState
    data object Unavailable : InlineImagesState
    data class Images(val assets: List<MistakeSourceAsset>) : InlineImagesState
}

private fun MistakeDetailState.inlineImages(): InlineImagesState {
    val detail = when (this) {
        MistakeDetailState.Loading -> return InlineImagesState.Loading
        is MistakeDetailState.Ready -> detail
        is MistakeDetailState.Legacy -> detail
        else -> return InlineImagesState.Unavailable
    }
    if (detail.archived) return InlineImagesState.Images(emptyList())
    // Revision evidence retains reading order even when source links are returned in asset-ID order.
    val sourceOrder = (this as? MistakeDetailState.Ready)?.questionDocument?.blockEvidence
        ?.map { it.sourceAssetId }?.distinct()?.withIndex()?.associate { it.value to it.index }.orEmpty()
    // displayAssets() 已排除讲解图，并把"当前干净题面"（最新一张）放在最前。这里只在
    // 原图组内按题面证据的阅读顺序重排；干净题面组保持 displayAssets 的顺序（index），
    // 否则再按 createdAt 升序会把多张历史重绘倒回"旧图在前"。
    val images = detail.source.displayAssets()
        .filter { it.mimeType.startsWith("image/", ignoreCase = true) }
        .distinctBy { it.sourceAssetId }
        .withIndex()
        .sortedWith(
            compareBy<IndexedValue<MistakeSourceAsset>> {
                if (it.value.role == CLEAN_IMAGE_SOURCE_ROLE) 0 else 1
            }
                .thenBy {
                    if (it.value.role == CLEAN_IMAGE_SOURCE_ROLE) {
                        it.index
                    } else {
                        sourceOrder[it.value.sourceAssetId] ?: Int.MAX_VALUE
                    }
                }
                .thenBy { it.value.createdAtEpochMillis },
        )
        .map { it.value }
    return InlineImagesState.Images(images)
}
