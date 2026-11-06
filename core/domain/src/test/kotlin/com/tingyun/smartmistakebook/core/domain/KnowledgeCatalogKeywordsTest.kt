package com.tingyun.smartmistakebook.core.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class KnowledgeCatalogKeywordsTest {
    @Test fun sentenceKeepsMeaningfulPhrasesInReadingOrder() {
        assertEquals(listOf("二次函数", "最小值"), extractCatalogKeywords(
            "我想查找二次函数的最小值", listOf("最小值", "函数", "二次函数"),
        ))
    }

    @Test fun repeatedPhrasesDoNotCreateDuplicateModules() {
        assertEquals(listOf("数量积", "做功"), extractCatalogKeywords(
            "数量积与做功，再用数量积解释", listOf("数量积", "做功", "数量积"),
        ))
    }

    @Test fun fullWidthAndCaseDifferencesMatchWithoutLosingDisplayName() {
        assertEquals(listOf("ABC", "最小值"), extractCatalogKeywords(
            "ａｂｃ 的最小值", listOf("ABC", "最小值"),
        ))
    }

    @Test fun neverInventsKeywordsFromCharacterGrams() {
        assertEquals(emptyList<String>(), extractCatalogKeywords("陌生描述", listOf("函数", "做功")))
        assertEquals(emptyList<String>(), extractCatalogKeywords("函数", listOf("", " ")))
    }

    @Test fun narrowerPhraseCanStillMatchElsewhereInSentence() {
        assertEquals(listOf("二次函数", "函数"), extractCatalogKeywords(
            "二次函数与其他函数", listOf("函数", "二次函数"),
        ))
    }
}
