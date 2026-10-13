package com.tingyun.smartmistakebook.core.domain

import com.tingyun.smartmistakebook.core.model.NormalizedSourceRegion
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 切分区域判据的消费侧归属（分题阻塞修复 A2/A3②③）。
 *
 * 这些规则原在 `core:model` 的 `CaptureAssessment` 契约里：模型层一旦拒绝，整轮任务就落
 * INVALID_RESPONSE，连多页关系请求的 followingPageRelations 都一起丢。规则下移到切分消费侧后，
 * 由这一份判据（与 [SplitCaptureDraftRequest] 同一套阈值和几何）在两个入口把关：
 * 单页题图的 `captureSplitDecision` 与批量页的 `BatchSplitRecognizer`。
 *
 * 迁移自 `core:model` 的 ModelTasksTest：`multipleIndependentQuestionsRequireBoundedSplitRegions`
 * 的正例（合法区域集可用）与 `splitAssessmentCannotOmitQuestionRegions` 的反例（SPLIT 无 region
 * 即拒）断言都在这里，不删只搬。
 */
class CaptureSplitRegionSetTest {

    @Test
    fun `two to twelve distinct question sized regions are usable`() {
        // 迁移自 ModelTasksTest.multipleIndependentQuestionsRequireBoundedSplitRegions。
        assertTrue(
            listOf(
                NormalizedSourceRegion(left = 0.05, top = 0.05, right = 0.95, bottom = 0.45),
                NormalizedSourceRegion(left = 0.05, top = 0.52, right = 0.95, bottom = 0.95),
            ).isUsableSplitRegionSet(),
        )
        assertTrue(rows(12).isUsableSplitRegionSet())
    }

    @Test
    fun `no regions or a single region is not a split`() {
        // 迁移自 ModelTasksTest.splitAssessmentCannotOmitQuestionRegions（A3③）：
        // 「SPLIT 无 region 即拒」在消费侧同样成立。
        assertFalse(emptyList<NormalizedSourceRegion>().isUsableSplitRegionSet())
        assertFalse(rows(1).isUsableSplitRegionSet())
    }

    @Test
    fun `more than twelve regions is not usable`() {
        assertFalse(rows(13).isUsableSplitRegionSet())
    }

    @Test
    fun `duplicate regions are not usable`() {
        val region = NormalizedSourceRegion(left = 0.05, top = 0.05, right = 0.95, bottom = 0.45)
        assertFalse(listOf(region, region, region.copy()).isUsableSplitRegionSet())
    }

    @Test
    fun `regions below the minimum size are not usable`() {
        val normal = NormalizedSourceRegion(left = 0.05, top = 0.05, right = 0.95, bottom = 0.45)
        // 高度不足 0.04。
        assertFalse(
            listOf(
                normal,
                NormalizedSourceRegion(left = 0.05, top = 0.55, right = 0.95, bottom = 0.58),
            ).isUsableSplitRegionSet(),
        )
        // 宽度 0.08、高度 0.07 都达标，但面积 0.0056 不足 0.006。
        assertFalse(
            listOf(
                normal,
                NormalizedSourceRegion(left = 0.05, top = 0.55, right = 0.13, bottom = 0.62),
            ).isUsableSplitRegionSet(),
        )
    }

    @Test
    fun `regions overlapping past the limit are not usable`() {
        assertFalse(
            listOf(
                NormalizedSourceRegion(left = 0.05, top = 0.05, right = 0.5, bottom = 0.5),
                NormalizedSourceRegion(left = 0.06, top = 0.06, right = 0.45, bottom = 0.45),
            ).isUsableSplitRegionSet(),
        )
    }

    @Test
    fun `coordinates outside the page are not usable`() {
        assertFalse(
            listOf(
                NormalizedSourceRegion(left = 0.05, top = 0.05, right = 0.95, bottom = 0.45),
                NormalizedSourceRegion(left = -0.1, top = 0.55, right = 0.9, bottom = 0.95),
            ).isUsableSplitRegionSet(),
        )
    }

    /** 阅读顺序排列的等宽条带：每页一题。 */
    private fun rows(count: Int): List<NormalizedSourceRegion> = List(count) { index ->
        NormalizedSourceRegion(
            left = 0.05,
            top = index * 0.07,
            right = 0.95,
            bottom = index * 0.07 + 0.06,
        )
    }
}
