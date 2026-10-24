package com.tingyun.smartmistakebook.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T5/5B-G8：答卷身份（AUD-01/02 身份同型）。
 *
 * 消灭的失败（spec 2026-10-11-new-stages §5B"答案键和回收事实身份"）：
 * 答卷没有身份分量时，同一张纸重传会记两笔分（重复计分）；两张不同的卷、或同一张卷不同
 * 天的作答会撞到同一个键上互相覆盖。身份必须由**内容指纹 + 学习日**派生，重传幂等、
 * 不同卷/不同日不撞键。
 */
class PaperAnswerSheetIdentityTest {

    @Test
    fun samePaperSameQuestionSameStudyDayIsIdempotent() {
        val first = PaperAnswerSheetIdentity(
            exportId = "export-1",
            paperFingerprint = FINGERPRINT_A,
            recycledAtEpochMillis = FIVE_AM,
            utcOffsetMinutes = 0,
        )
        val retry = PaperAnswerSheetIdentity(
            exportId = "export-1",
            paperFingerprint = FINGERPRINT_A,
            recycledAtEpochMillis = EIGHT_PM, // 同一学习日内的另一次重传
            utcOffsetMinutes = 0,
        )

        assertEquals(
            "同卷同题同学习日重传必须派生同一个作答尝试键（不重复计分）",
            first.attemptKeyFor("question-0"),
            retry.attemptKeyFor("question-0"),
        )
        assertEquals(first.sheetKey, retry.sheetKey)
    }

    @Test
    fun reExportedIdenticalContentSameDaySharesTheAttemptKey() {
        val first = PaperAnswerSheetIdentity(
            exportId = "export-1",
            paperFingerprint = FINGERPRINT_A,
            recycledAtEpochMillis = FIVE_AM,
            utcOffsetMinutes = 0,
        )
        val reExported = PaperAnswerSheetIdentity(
            exportId = "export-2", // 同内容重新导出：新导出记录、同一份卷面指纹
            paperFingerprint = FINGERPRINT_A,
            recycledAtEpochMillis = SIX_AM,
            utcOffsetMinutes = 0,
        )

        assertEquals(
            "指纹定'哪张卷'；exportId 只是溯源。同内容重导出不得给同一张答卷开第二条计分道",
            first.attemptKeyFor("question-0"),
            reExported.attemptKeyFor("question-0"),
        )
    }

    @Test
    fun differentPaperContentDoesNotCollide() {
        val paperA = sheetIdentity(fingerprint = FINGERPRINT_A)
        val paperB = sheetIdentity(fingerprint = FINGERPRINT_B)

        assertNotEquals(
            "不同卷（不同卷面指纹）不得撞键",
            paperA.attemptKeyFor("question-0"),
            paperB.attemptKeyFor("question-0"),
        )
        assertNotEquals(paperA.sheetKey, paperB.sheetKey)
    }

    @Test
    fun differentStudyDayDoesNotCollide() {
        val dayOne = sheetIdentity(recycledAtEpochMillis = FIVE_AM)
        val dayTwo = sheetIdentity(recycledAtEpochMillis = NEXT_DAY_FIVE_AM)

        assertNotEquals(
            "同一张卷、不同学习日（学生隔天又做了一遍）是两次合法作答，不得撞键",
            dayOne.attemptKeyFor("question-0"),
            dayTwo.attemptKeyFor("question-0"),
        )
        assertNotEquals(dayOne.sheetKey, dayTwo.sheetKey)
    }

    @Test
    fun studyDayFollowsTheFourAmDayStartRule() {
        val beforeFourAm = sheetIdentity(recycledAtEpochMillis = THREE_AM)
        val afterFourAm = sheetIdentity(recycledAtEpochMillis = FIVE_AM)

        assertEquals(-1L, beforeFourAm.recycleStudyDay)
        assertEquals(0L, afterFourAm.recycleStudyDay)
        assertNotEquals(
            "本地 04:00 日界（StudyDayMath）：凌晨三点与五点分属两个学习日",
            beforeFourAm.attemptKeyFor("question-0"),
            afterFourAm.attemptKeyFor("question-0"),
        )
    }

    @Test
    fun attemptKeysAreNamespacedByQuestion() {
        val identity = sheetIdentity()

        assertNotEquals(
            "同一张卷上不同题是不同作答尝试",
            identity.attemptKeyFor("question-0"),
            identity.attemptKeyFor("question-1"),
        )
    }

    @Test
    fun attemptKeysCarryTheirNamespacePrefix() {
        val identity = sheetIdentity()

        assertTrue(identity.attemptKeyFor("question-0").startsWith("paper-answer-attempt-"))
        assertTrue(identity.sheetKey.startsWith("paper-answer-sheet-"))
    }

    @Test
    fun blankIdentityComponentsAreRejected() {
        assertRejected(exportId = " ", fingerprint = FINGERPRINT_A)
        assertRejected(exportId = "export-1", fingerprint = "")
    }

    private fun sheetIdentity(
        exportId: String = "export-1",
        fingerprint: String = FINGERPRINT_A,
        recycledAtEpochMillis: Long = FIVE_AM,
        utcOffsetMinutes: Int = 0,
    ) = PaperAnswerSheetIdentity(
        exportId = exportId,
        paperFingerprint = fingerprint,
        recycledAtEpochMillis = recycledAtEpochMillis,
        utcOffsetMinutes = utcOffsetMinutes,
    )

    private fun assertRejected(
        exportId: String,
        fingerprint: String,
    ) {
        var rejected = false
        try {
            sheetIdentity(exportId = exportId, fingerprint = fingerprint)
        } catch (_: IllegalArgumentException) {
            rejected = true
        }
        assertTrue("空白身份分量必须被拒绝：exportId='$exportId'", rejected)
    }

    private companion object {
        const val FIVE_AM = 5L * 3_600_000L
        const val SIX_AM = 6L * 3_600_000L
        const val EIGHT_PM = 20L * 3_600_000L
        const val THREE_AM = 3L * 3_600_000L
        const val NEXT_DAY_FIVE_AM = 29L * 3_600_000L
        val FINGERPRINT_A = "a".repeat(64)
        val FINGERPRINT_B = "b".repeat(64)
    }
}
