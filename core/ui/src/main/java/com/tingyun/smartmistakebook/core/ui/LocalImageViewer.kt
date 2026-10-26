package com.tingyun.smartmistakebook.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tingyun.smartmistakebook.core.model.NormalizedSourceRegion
import kotlin.math.min
import kotlin.math.roundToInt

/** Read-only, bounded local preview. Opening it never requests or regenerates an image. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LocalImageViewer(
    imageUri: String,
    contentDescription: String,
    onDismiss: () -> Unit,
    sourceRegion: NormalizedSourceRegion? = null,
) {
    var transform by remember(imageUri, sourceRegion) { mutableStateOf(ImageViewportTransform()) }
    var imageSize by remember(imageUri, sourceRegion) { mutableStateOf(IntSize.Zero) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var loadState by remember(imageUri, sourceRegion) { mutableStateOf(LocalImageLoadState.LOADING) }
    val fit = if (imageSize.width > 0 && imageSize.height > 0) {
        min(viewportSize.width.toFloat() / imageSize.width, viewportSize.height.toFloat() / imageSize.height)
    } else 0f
    val viewport = ImageViewport(imageSize.width * fit, imageSize.height * fit,
        viewportSize.width.toFloat(), viewportSize.height.toFloat())
    fun zoom(scale: Float) {
        transform = zoomImageAt(viewport, transform, scale,
            viewport.viewportWidth / 2f, viewport.viewportHeight / 2f)
    }
    fun pan(x: Float, y: Float) {
        transform = clampImageTransform(transform.copy(
            offsetX = transform.offsetX + x * viewport.viewportWidth / 4f,
            offsetY = transform.offsetY + y * viewport.viewportHeight / 4f), viewport)
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Paper).safeDrawingPadding().claudeModalEntrance()
            .claudeDismissOnEscape(onDismiss = onDismiss)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(contentDescription, Modifier.weight(1f), color = Ink,
                    style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                InlineActionButton("关闭", onDismiss, contentDescription = "关闭图片查看")
            }
            Box(
                Modifier.weight(1f).fillMaxWidth().background(SubtleSurface).clipToBounds()
                    .testTag("image_viewer_viewport")
                    .onSizeChanged {
                        if (viewportSize != it) transform = ImageViewportTransform()
                        viewportSize = it
                    }
                    .pointerInput(imageUri, sourceRegion, viewport) {
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            val zoomed = zoomImageAt(viewport, transform, transform.scale * zoom,
                                centroid.x, centroid.y)
                            transform = clampImageTransform(zoomed.copy(
                                offsetX = zoomed.offsetX + pan.x, offsetY = zoomed.offsetY + pan.y), viewport)
                        }
                    }
                    .pointerInput(imageUri, sourceRegion, viewport) {
                        detectTapGestures(onDoubleTap = { point ->
                            transform = if (transform.scale > 1f) ImageViewportTransform() else
                                zoomImageAt(viewport, transform, 2f, point.x, point.y)
                        })
                    },
                contentAlignment = Alignment.Center,
            ) {
                BoundedLocalImage(
                    imageUri, contentDescription, expanded = true,
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        scaleX = transform.scale; scaleY = transform.scale
                        translationX = transform.offsetX; translationY = transform.offsetY
                    },
                    expandedMaxHeight = androidx.compose.ui.unit.Dp.Infinity,
                    sourceRegion = sourceRegion,
                    onLoadStateChange = { loadState = it },
                    onImageSizeChanged = { imageSize = it },
                )
                if (loadState == LocalImageLoadState.UNAVAILABLE) {
                    Text("图片暂时打不开，请返回查看来源或重新选择。",
                        Modifier.padding(24.dp), color = InkSecondary, style = MaterialTheme.typography.bodyMedium)
                }
            }
            FlowRow(Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.Center, itemVerticalAlignment = Alignment.CenterVertically) {
                InlineActionButton("−", { zoom(transform.scale / 1.25f) },
                    enabled = imageSize != IntSize.Zero && transform.scale > 1f, contentDescription = "缩小图片")
                Text("${(transform.scale * 100).roundToInt()}%", Modifier.testTag("image_viewer_scale"),
                    color = Ink, style = MaterialTheme.typography.labelMedium)
                InlineActionButton("＋", { zoom(transform.scale * 1.25f) },
                    enabled = imageSize != IntSize.Zero && transform.scale < 4f, contentDescription = "放大图片")
                InlineActionButton("复位", { transform = ImageViewportTransform() }, contentDescription = "复位图片")
            }
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                val canPanHorizontally = imageSize.width * fit * transform.scale > viewportSize.width
                val canPanVertically = imageSize.height * fit * transform.scale > viewportSize.height
                InlineActionButton("←", { pan(1f, 0f) }, enabled = canPanHorizontally, contentDescription = "查看左侧")
                InlineActionButton("↑", { pan(0f, 1f) }, enabled = canPanVertically, contentDescription = "查看上方")
                InlineActionButton("↓", { pan(0f, -1f) }, enabled = canPanVertically, contentDescription = "查看下方")
                InlineActionButton("→", { pan(-1f, 0f) }, enabled = canPanHorizontally, contentDescription = "查看右侧")
            }
            if (imageSize != IntSize.Zero) {
                Text("预览 ${imageSize.width} × ${imageSize.height} 像素 · 放大不增加细节",
                    Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp).testTag("image_viewer_resolution"),
                    color = InkSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
