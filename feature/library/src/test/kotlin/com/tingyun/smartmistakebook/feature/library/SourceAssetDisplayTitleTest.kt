package com.tingyun.smartmistakebook.feature.library

import org.junit.Assert.assertEquals
import org.junit.Test
import com.tingyun.smartmistakebook.core.domain.MistakeSourceAsset
import com.tingyun.smartmistakebook.core.domain.MistakeSourceLocation
import com.tingyun.smartmistakebook.core.domain.MistakeSourceSet
import com.tingyun.smartmistakebook.core.domain.questionImageAssets

class SourceAssetDisplayTitleTest {
    @Test fun outerListsShareQuestionImagesWithoutExposingAnswerPictures() {
        fun asset(id: String, role: String, at: Long) = MistakeSourceAsset(
            role, id, "a".repeat(64), "image/png", 100, 100, 80, "CAMERA", at,
            MistakeSourceLocation.Available("file:///test/$id.png"),
        )
        val source = MistakeSourceSet.Present(listOf(
            asset("original", "QUESTION_SOURCE", 1), asset("answer", "TUTOR_EXPLANATION", 4),
            asset("old", "CLEAN_IMAGE", 2), asset("current", "CLEAN_IMAGE", 3),
        ))
        assertEquals(listOf("current", "old", "original"), source.questionImageAssets().map { it.sourceAssetId })
    }

    @Test fun generatedAndHistoricalCopiesKeepTheirIdentity() {
        assertEquals("重绘", sourceAssetDisplayTitle(CLEAN_IMAGE_SOURCE_ROLE, 1, 1, false, false))
        assertEquals("旧版重绘（仅历史题面）", sourceAssetDisplayTitle(CLEAN_IMAGE_SOURCE_ROLE, 1, 1, true, false))
        assertEquals("原图 2 / 3", sourceAssetDisplayTitle("QUESTION_SOURCE", 2, 3, false, false))
        assertEquals("讲解图", sourceAssetDisplayTitle("TUTOR_EXPLANATION", 1, 1, false, true))
    }
}
