package com.tingyun.smartmistakebook.core.data.study

import com.tingyun.smartmistakebook.core.domain.PaperAnswerKeyLinkDescriptor
import com.tingyun.smartmistakebook.core.domain.PaperAnswerSheet
import com.tingyun.smartmistakebook.core.domain.PaperAnswerSheetIdentity
import com.tingyun.smartmistakebook.core.domain.PaperQuestionAnswer
import com.tingyun.smartmistakebook.core.domain.buildPaperAnswerKeyChain
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T5/5B-G8：键链/答卷内存仓储（零 schema 先行的契约参照实现）。
 *
 * 消灭的失败：域层接口零实现 → 回收契约不可执行、导出侧接线（遗留）没有可对齐的参照；
 * 用例钉住"同 sheetKey 重传不产生第二条、不覆盖首次记录"与"键链按 (export, 指纹) 读写"。
 */
class InMemoryPaperAnswerRecycleStoreTest {

    private val chainStore = InMemoryPaperAnswerKeyChainStore()
    private val sheetStore = InMemoryPaperAnswerSheetStore()

    @Test
    fun chainSaveAndReadRoundTrip() = runBlocking {
        val chain = buildPaperAnswerKeyChain(
            exportId = "export-1",
            paperFingerprint = FINGERPRINT_A,
            createdAtEpochMillis = 42L,
            descriptors = listOf(
                PaperAnswerKeyLinkDescriptor(
                    paperQuestionId = "question-0",
                    practiceUnitId = "pu-1",
                    problemRevisionId = "rev-1",
                    choiceIds = listOf("opt-a", "opt-b"),
                    correctChoiceId = "opt-a",
                    answerVerificationStatus = "VERIFIED",
                    answerSpecId = "spec-v1",
                ),
            ),
        )

        chainStore.saveChain(chain)

        assertEquals(chain, chainStore.readChain("export-1", FINGERPRINT_A))
    }

    @Test
    fun chainReadReturnsNullForUnknownPaper() = runBlocking {
        assertNull(chainStore.readChain("export-missing", FINGERPRINT_A))
        assertNull(chainStore.readChain("export-1", FINGERPRINT_B))
    }

    @Test
    fun chainResaveForTheSamePaperReplacesTheMapping() = runBlocking {
        val first = chainOf(links = 1)
        val second = chainOf(links = 2)

        chainStore.saveChain(first)
        chainStore.saveChain(second)

        assertEquals(
            "同 (export, 指纹) 重复保存以最新为准（导出重试同卷面）",
            second,
            chainStore.readChain("export-1", FINGERPRINT_A),
        )
    }

    @Test
    fun recordingTheSameSheetTwiceCreatesOnlyOnce() = runBlocking {
        val sheet = sheetOf(fingerprint = FINGERPRINT_A, day = 0L)

        val first = sheetStore.recordAnswerSheet(sheet)
        val retry = sheetStore.recordAnswerSheet(sheet.copy(recycledAtEpochMillis = sheet.recycledAtEpochMillis + 1))

        assertTrue("首次记录 created=true", first.created)
        assertFalse("重传不产生第二条（幂等）", retry.created)
        assertEquals(first.sheetKey, retry.sheetKey)
    }

    @Test
    fun replayDoesNotOverwriteTheFirstRecord() = runBlocking {
        val original = sheetOf(fingerprint = FINGERPRINT_A, day = 0L)

        sheetStore.recordAnswerSheet(original)
        sheetStore.recordAnswerSheet(original.copy(recycledAtEpochMillis = original.recycledAtEpochMillis + 1))

        assertSame("首次记录是权威（重传不覆盖）", original, sheetStore.readAnswerSheet(original.identity.sheetKey))
    }

    @Test
    fun differentPaperOrDifferentDayAreDistinctRecords() = runBlocking {
        val paperA = sheetStore.recordAnswerSheet(sheetOf(fingerprint = FINGERPRINT_A, day = 0L))
        val paperB = sheetStore.recordAnswerSheet(sheetOf(fingerprint = FINGERPRINT_B, day = 0L))
        val nextDay = sheetStore.recordAnswerSheet(sheetOf(fingerprint = FINGERPRINT_A, day = 1L))

        assertTrue(paperA.created && paperB.created && nextDay.created)
        assertTrue("不同卷/不同日互不撞键", paperA.sheetKey != paperB.sheetKey && paperA.sheetKey != nextDay.sheetKey)
    }

    @Test
    fun readUnknownSheetReturnsNull() = runBlocking {
        assertNull(sheetStore.readAnswerSheet("paper-answer-sheet-missing"))
    }

    // ------------------------------------------------------------------

    private fun chainOf(links: Int) = buildPaperAnswerKeyChain(
        exportId = "export-1",
        paperFingerprint = FINGERPRINT_A,
        createdAtEpochMillis = 0L,
        descriptors = (0 until links).map { index ->
            PaperAnswerKeyLinkDescriptor(
                paperQuestionId = "question-$index",
                practiceUnitId = "pu-$index",
                problemRevisionId = "rev-$index",
                choiceIds = listOf("opt-a", "opt-b"),
                correctChoiceId = "opt-a",
                answerVerificationStatus = "VERIFIED",
                answerSpecId = "spec-v1",
            )
        },
    )

    private fun sheetOf(fingerprint: String, day: Long): PaperAnswerSheet {
        // 学习日 day 的 05:00（04:00 日界之后），偏移 0：day*24h + 5h。
        val recycledAt = day * 86_400_000L + 5L * 3_600_000L
        val identity = PaperAnswerSheetIdentity(
            exportId = "export-1",
            paperFingerprint = fingerprint,
            recycledAtEpochMillis = recycledAt,
            utcOffsetMinutes = 0,
        )
        return PaperAnswerSheet(
            identity = identity,
            recycledAtEpochMillis = recycledAt,
            answers = listOf(PaperQuestionAnswer(paperQuestionId = "question-0", selectedChoiceId = "opt-a")),
        )
    }

    private companion object {
        val FINGERPRINT_A = "a".repeat(64)
        val FINGERPRINT_B = "b".repeat(64)
    }
}
