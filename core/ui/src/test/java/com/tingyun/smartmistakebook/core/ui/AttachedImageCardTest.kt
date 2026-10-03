package com.tingyun.smartmistakebook.core.ui

import com.tingyun.smartmistakebook.core.model.AttachedImage
import com.tingyun.smartmistakebook.core.model.AttachedImageKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AttachedImageCardTest {
    @Test
    fun collapsedTitleLabelsARedrawByKind() {
        val title = attachedImageCollapsedTitle(
            AttachedImage(
                imageId = "redraw-1",
                kind = AttachedImageKind.REDRAW_PROBLEM,
                description = "重绘题面去除手写笔迹保留印刷内容",
                accessibilityText = "干净的题目图",
            ),
        )
        assertTrue(title.startsWith("重绘图"))
        assertTrue(title.contains("重绘题面"))
    }

    @Test
    fun collapsedTitleLabelsAProcessFigureByKind() {
        val title = attachedImageCollapsedTitle(
            AttachedImage(
                imageId = "process-1",
                kind = AttachedImageKind.GENERATE_PROCESS,
                description = "画出导数为正与为负的区间标注图",
            ),
        )
        assertTrue(title.startsWith("过程图"))
        assertTrue(title.contains("标注图"))
    }

    @Test
    fun aGeneratedFigureResolvesOnceToReady() = runBlocking {
        // A2：持久引用（资产 id）→ 解析一次 → Ready；同一 id 二次组合不重复解析（缓存）。
        var resolveCalls = 0
        val cache = GeneratedFigureAssetCache { assetId ->
            resolveCalls += 1
            "file://${assetId}"
        }

        assertEquals(null, cache.cached("figure-a"))
        val first = cache.stateOf("figure-a")
        val second = cache.stateOf("figure-a")

        assertEquals(AttachedImageRenderState.Ready("file://figure-a"), first)
        assertEquals(first, second)
        assertEquals("同一 id 只解析一次", 1, resolveCalls)
        assertEquals(AttachedImageRenderState.Ready("file://figure-a"), cache.cached("figure-a"))
    }

    @Test
    fun aFailedGeneratedFigureIsRememberedAsFailedNotRetriedByTheUi() = runBlocking {
        // 解析失败是这张资产当前的真实状态；缓存它，不让界面在每次重组时反复重试。
        var resolveCalls = 0
        val cache = GeneratedFigureAssetCache {
            resolveCalls += 1
            null
        }

        assertEquals(AttachedImageRenderState.Failed, cache.stateOf("figure-b"))
        assertEquals(AttachedImageRenderState.Failed, cache.stateOf("figure-b"))
        assertEquals(1, resolveCalls)
    }

    /** A4：解析结果 → 三态（拿到 URI 是 READY，拿不到是 FAILED；GENERATING 是解析前的初始态）。 */
    @Test
    fun resolutionMapsToTheThreeRenderStates() {
        assertEquals(
            AttachedImageRenderState.Ready("file://a"),
            attachedImageRenderState("file://a"),
        )
        assertEquals(AttachedImageRenderState.Failed, attachedImageRenderState(null))
    }
}
