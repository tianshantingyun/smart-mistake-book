package com.tingyun.smartmistakebook.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageViewportTransformTest {
    private val viewport = ImageViewport(200f, 100f, 200f, 100f)

    @Test fun zoomKeepsTheTouchedImagePointInPlace() {
        val result = zoomImageAt(viewport, ImageViewportTransform(), 2f, 150f, 50f)
        assertEquals(2f, result.scale, 0.001f)
        assertEquals(-50f, result.offsetX, 0.001f)
        assertEquals(0f, result.offsetY, 0.001f)
    }

    @Test fun shrinkingClampsTranslationBackToTheImageBoundary() {
        val result = clampImageTransform(ImageViewportTransform(1f, 100f, -100f), viewport)
        assertEquals(0f, result.offsetX, 0.001f)
        assertEquals(0f, result.offsetY, 0.001f)
    }

    @Test fun contentSmallerThanTheViewportStaysCentered() {
        val result = clampImageTransform(ImageViewportTransform(1f, 30f, 30f), ImageViewport(100f, 50f, 200f, 100f))
        assertEquals(0f, result.offsetX, 0.001f)
        assertEquals(0f, result.offsetY, 0.001f)
    }

    @Test fun invalidDimensionsCannotProduceANonFiniteTransform() {
        val result = zoomImageAt(ImageViewport(0f, Float.NaN, 200f, 100f), ImageViewportTransform(), 4f, 0f, 0f)
        assertTrue(result.scale.isFinite() && result.offsetX.isFinite() && result.offsetY.isFinite())
    }

    @Test fun excessiveZoomAndPanAreBounded() {
        val result = clampImageTransform(ImageViewportTransform(100f, 99999f, -99999f), viewport)
        assertEquals(4f, result.scale, 0.001f)
        assertEquals(300f, result.offsetX, 0.001f)
        assertEquals(-150f, result.offsetY, 0.001f)
    }
}
