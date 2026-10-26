package com.tingyun.smartmistakebook.core.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ClaudeSplitRatioTest {
    @Test fun resizingKeepsBothPanesAtTheirMinimumWidth() {
        assertEquals(.4f, claudeSplitRatio(-1f, 800f, 320f), .0001f)
        assertEquals(.6f, claudeSplitRatio(2f, 800f, 320f), .0001f)
        assertEquals(.55f, claudeSplitRatio(.55f, 800f, 320f), .0001f)
    }
    @Test fun smallerWindowAndInvalidInputNeverCreateNegativeWeights() {
        assertEquals(.5f, claudeSplitRatio(.9f, 400f, 320f), .0001f)
        assertEquals(.5f, claudeSplitRatio(Float.NaN, 800f, 320f), .0001f)
        assertEquals(.5f, claudeSplitRatio(.2f, 0f, 320f), .0001f)
    }
}
