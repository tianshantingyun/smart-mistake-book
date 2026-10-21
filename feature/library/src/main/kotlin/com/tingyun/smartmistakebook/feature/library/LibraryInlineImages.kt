package com.tingyun.smartmistakebook.feature.library

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.tingyun.smartmistakebook.core.domain.MistakeDetailRepository
import com.tingyun.smartmistakebook.core.domain.MistakeDetailState
import com.tingyun.smartmistakebook.core.domain.MistakeSourceAsset
import com.tingyun.smartmistakebook.core.domain.MistakeSourceLocation
import com.tingyun.smartmistakebook.core.ui.BoundedLocalImage
import com.tingyun.smartmistakebook.core.ui.LocalImageLoadState
import com.tingyun.smartmistakebook.core.ui.SmartColors
import kotlinx.coroutines.CancellationException

/** Only composed list items read their saved local pictures; there is no generation or write port. */
@Composable
internal fun LibraryInlineImages(
    mistake: LibraryMistake,
    repository: MistakeDetailRepository?,
    modifier: Modifier = Modifier,
) {
    if (repository == null) return
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
    if (current is InlineImagesState.Images && current.assets.isEmpty()) return
    Column(modifier) {
        when (current) {
            InlineImagesState.Loading -> PreviewMessage("正在加载题图…")
            InlineImagesState.Unavailable -> PreviewMessage("题图暂时无法显示")
            is InlineImagesState.Images -> current.assets.forEachIndexed { index, asset ->
                key(asset.sourceAssetId) {
                    if (current.assets.size > 1) PreviewMessage("题图 · ${index + 1}/${current.assets.size}")
                    InlineImage(asset, "${mistake.title}，第${index + 1}张题图")
                }
            }
        }
    }
}

@Composable
private fun InlineImage(asset: MistakeSourceAsset, description: String) {
    val uri = (asset.location as? MistakeSourceLocation.Available)?.localUri
    if (uri == null) {
        PreviewMessage("题图暂时无法显示")
        return
    }
    var loadState by remember(asset.sourceAssetId, uri) { mutableStateOf(LocalImageLoadState.LOADING) }
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        val imageHeight = (maxWidth * (asset.height.toFloat() / asset.width)).coerceIn(80.dp, 480.dp)
        BoundedLocalImage(
            imageUri = uri,
            contentDescription = description,
            expanded = true,
            expandedMaxHeight = 480.dp,
            modifier = Modifier.fillMaxWidth().height(imageHeight).clip(MaterialTheme.shapes.small),
            onLoadStateChange = { loadState = it },
        )
    }
    if (loadState == LocalImageLoadState.UNAVAILABLE) PreviewMessage("题图暂时无法显示")
}

@Composable
private fun PreviewMessage(message: String) {
    Text(message, color = SmartColors.Ink, style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(top = 6.dp, bottom = 6.dp))
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
    val images = detail.source.displayAssets()
        .filter { it.mimeType.startsWith("image/", ignoreCase = true) }
        .distinctBy { it.sourceAssetId }
        .sortedWith(compareBy<MistakeSourceAsset> { if (it.role == CLEAN_IMAGE_SOURCE_ROLE) 0 else 1 }
            .thenBy { sourceOrder[it.sourceAssetId] ?: Int.MAX_VALUE }
            .thenBy { it.createdAtEpochMillis })
    return InlineImagesState.Images(images)
}
