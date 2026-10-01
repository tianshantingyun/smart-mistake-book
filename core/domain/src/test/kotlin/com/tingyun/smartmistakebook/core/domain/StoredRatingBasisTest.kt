package com.tingyun.smartmistakebook.core.domain

import com.tingyun.smartmistakebook.core.model.StoredReviewRating
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P9（审计 P9 行，2026-09-30）：rating 基址的唯一换算面。
 *
 * 配对契约：算法侧 `FsrsRating` 的声明序与落库侧 `StoredReviewRating` 的值必须一一对应
 * ——改名/重排任一侧即红（这是两条表被机械绑在一起的唯一保证）。
 */
class StoredRatingBasisTest {

    @Test
    fun `stored ratings pair one to one with the algorithm enum`() {
        assertEquals(
            StoredReviewRating.entries.map { it.value },
            FsrsRating.entries.map { it.storedRating },
        )
        assertEquals(1, StoredReviewRating.MIN)
        assertEquals(4, StoredReviewRating.MAX)
    }

    @Test
    fun `fromStored clamps out of range values to the endpoints`() {
        assertEquals(FsrsRating.AGAIN, FsrsRating.fromStored(1))
        assertEquals(FsrsRating.HARD, FsrsRating.fromStored(2))
        assertEquals(FsrsRating.GOOD, FsrsRating.fromStored(3))
        assertEquals(FsrsRating.EASY, FsrsRating.fromStored(4))
        assertEquals(FsrsRating.AGAIN, FsrsRating.fromStored(0))
        assertEquals(FsrsRating.AGAIN, FsrsRating.fromStored(-5))
        assertEquals(FsrsRating.EASY, FsrsRating.fromStored(5))
        assertEquals(FsrsRating.EASY, FsrsRating.fromStored(99))
    }

    @Test
    fun `storedIsAgain is true only for the stored again value`() {
        assertTrue(FsrsRating.storedIsAgain(1))
        assertFalse(FsrsRating.storedIsAgain(2))
        assertFalse(FsrsRating.storedIsAgain(3))
        assertFalse(FsrsRating.storedIsAgain(4))
    }

    @Test
    fun `poor grade covers again and hard only`() {
        assertTrue(FsrsRating.AGAIN.isPoorGrade)
        assertTrue(FsrsRating.HARD.isPoorGrade)
        assertFalse(FsrsRating.GOOD.isPoorGrade)
        assertFalse(FsrsRating.EASY.isPoorGrade)
    }

    @Test
    fun `parameter index and grade minus three keep the py fsrs basis`() {
        assertEquals(listOf(0, 1, 2, 3), FsrsRating.entries.map { it.parameterIndex })
        assertEquals(listOf(-2, -1, 0, 1), FsrsRating.entries.map { it.gradeMinusThree })
    }
}
