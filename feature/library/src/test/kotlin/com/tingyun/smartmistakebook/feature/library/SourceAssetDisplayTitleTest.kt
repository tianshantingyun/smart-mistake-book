package com.tingyun.smartmistakebook.feature.library

import org.junit.Assert.assertEquals
import org.junit.Test

class SourceAssetDisplayTitleTest {
    @Test fun generatedAndHistoricalCopiesKeepTheirIdentity() {
        assertEquals("重绘", sourceAssetDisplayTitle(CLEAN_IMAGE_SOURCE_ROLE, 1, 1, false, false))
        assertEquals("旧版重绘（仅历史题面）", sourceAssetDisplayTitle(CLEAN_IMAGE_SOURCE_ROLE, 1, 1, true, false))
        assertEquals("原图 2 / 3", sourceAssetDisplayTitle("QUESTION_SOURCE", 2, 3, false, false))
        assertEquals("讲解图", sourceAssetDisplayTitle("TUTOR_EXPLANATION", 1, 1, false, true))
    }
}
