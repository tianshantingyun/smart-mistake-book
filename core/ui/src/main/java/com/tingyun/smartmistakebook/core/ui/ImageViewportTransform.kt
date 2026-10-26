package com.tingyun.smartmistakebook.core.ui

import kotlin.math.max

internal data class ImageViewport(
    val imageWidth: Float,
    val imageHeight: Float,
    val viewportWidth: Float,
    val viewportHeight: Float,
) {
    val valid: Boolean get() = listOf(imageWidth, imageHeight, viewportWidth, viewportHeight)
        .all { it.isFinite() && it > 0f }
}

internal data class ImageViewportTransform(
    val scale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
)

internal fun clampImageTransform(
    transform: ImageViewportTransform,
    viewport: ImageViewport,
): ImageViewportTransform {
    if (!viewport.valid) return ImageViewportTransform()
    val scale = transform.scale.takeIf { it.isFinite() }?.coerceIn(1f, 4f) ?: 1f
    val horizontal = max(0f, (viewport.imageWidth * scale - viewport.viewportWidth) / 2f)
    val vertical = max(0f, (viewport.imageHeight * scale - viewport.viewportHeight) / 2f)
    return ImageViewportTransform(
        scale,
        (transform.offsetX.takeIf { it.isFinite() } ?: 0f).coerceIn(-horizontal, horizontal),
        (transform.offsetY.takeIf { it.isFinite() } ?: 0f).coerceIn(-vertical, vertical),
    )
}

internal fun zoomImageAt(
    viewport: ImageViewport,
    current: ImageViewportTransform,
    targetScale: Float,
    anchorX: Float,
    anchorY: Float,
): ImageViewportTransform {
    val previous = clampImageTransform(current, viewport)
    val scale = targetScale.takeIf { it.isFinite() }?.coerceIn(1f, 4f) ?: previous.scale
    val ratio = scale / previous.scale
    val x = (anchorX - viewport.viewportWidth / 2f).takeIf { it.isFinite() } ?: 0f
    val y = (anchorY - viewport.viewportHeight / 2f).takeIf { it.isFinite() } ?: 0f
    return clampImageTransform(
        ImageViewportTransform(scale, x - (x - previous.offsetX) * ratio, y - (y - previous.offsetY) * ratio),
        viewport,
    )
}
