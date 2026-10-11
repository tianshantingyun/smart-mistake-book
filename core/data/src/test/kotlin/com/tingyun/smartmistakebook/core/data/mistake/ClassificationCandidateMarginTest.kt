package com.tingyun.smartmistakebook.core.data.mistake

import com.tingyun.smartmistakebook.core.database.ProblemClassificationCandidateRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * K5-B：`normalized_margin` 与「存疑」派生（cleanlab 语义）的纯函数契约。
 *
 * 消灭的失败：候选竞争关系此前**不可考**（只存被接受的标签），无法回答"这个被接受的标签
 * 是不是在几乎并列/被同轮更高分候选压着的情况下被接受的"。本测试钉住语义边界：
 * 同维度取最大对手分、无对手 = 不可判（不是 0、不是"安全"）、并列/极小 margin = 存疑、
 * 被拒候选同样进对手集、多接受标签各算各的（可负 margin）、派生**零副作用**（只读）。
 */
class ClassificationCandidateMarginTest {

    @Test
    fun marginIsTheSelectedScoreMinusTheStrongestSameDimensionCompetitor() {
        assertEquals(
            "cleanlab 语义：p(选中) − max(p(其它候选同维度))",
            0.05,
            normalizedMargin(0.90, listOf(0.85, 0.40))!!,
            1e-9,
        )
        assertEquals(
            "对手更强 ⇒ 负 margin（该标签被接受了，但同轮有分更高的候选）",
            -0.10,
            normalizedMargin(0.80, listOf(0.90))!!,
            1e-9,
        )
    }

    @Test
    fun aSingleCandidateHasNoMarginAndIsNeverMarkedSuspect() {
        assertNull(
            "无对手 ⇒ margin 缺失 = 不可判（不是 0）",
            normalizedMargin(0.90, emptyList()),
        )
        val signals = acceptedLabelMarginSignals(
            listOf(candidate("only", accepted = true, confidence = 0.90)),
        )
        val only = signals.single()
        assertNull("仅一个候选：margin 不可判", only.margin)
        assertFalse("不可判 ≠ 存疑（也绝不是「已验证正确」——调用方按 margin == null 区分）", only.suspect)
    }

    @Test
    fun tiesAndHairlineMarginsAreMarkedSuspectWhileClearWinnersAreNot() {
        val signals = acceptedLabelMarginSignals(
            listOf(
                // 同轮同维度：被接受 0.90，对手 0.90（并列）⇒ 存疑
                candidate("tie", accepted = true, confidence = 0.90),
                candidate("tie-rival", accepted = false, confidence = 0.90),
                // 同轮另一维度：被接受 0.86，对手 0.85（margin 0.01 ≤ 0.02）⇒ 存疑
                candidate("hairline", accepted = true, confidence = 0.86, dimension = "CHAPTER"),
                candidate("hairline-rival", accepted = false, confidence = 0.85, dimension = "CHAPTER"),
            ),
        )

        val tie = signals.single { it.labelId == "label:tie" }
        assertEquals(0.0, tie.margin!!, 0.0)
        assertTrue("并列必须存疑", tie.suspect)

        val hairline = signals.single { it.labelId == "label:hairline" }
        assertEquals(0.01, hairline.margin!!, 1e-9)
        assertTrue("margin 0.01 ≤ 0.02 必须存疑", hairline.suspect)

        val clear = acceptedLabelMarginSignals(
            listOf(
                candidate("clear", accepted = true, confidence = 0.95),
                candidate("clear-rival", accepted = false, confidence = 0.90),
            ),
        ).single()
        assertEquals(0.05, clear.margin!!, 1e-9)
        assertFalse("0.05 > 0.02 不标存疑（阈值是可调策略，不是判决）", clear.suspect)
    }

    @Test
    fun onlyAcceptedCandidatesAreSignalledAndRejectedOnesStillCompete() {
        val signals = acceptedLabelMarginSignals(
            listOf(
                candidate("accepted", accepted = true, confidence = 0.80),
                candidate("rejected", accepted = false, confidence = 0.88),
            ),
        )

        assertEquals("只给已接受标签出信号（被拒候选的分数由审计的选择偏差段呈现）", 1, signals.size)
        val accepted = signals.single()
        assertEquals(
            "被拒候选也是对手：0.80 − 0.88 = −0.08",
            -0.08,
            accepted.margin!!,
            1e-9,
        )
        assertTrue("被同轮更高分的被拒候选压着 ⇒ 存疑", accepted.suspect)
    }

    @Test
    fun differentRoundsOrDimensionsNeverCompeteWithEachOther() {
        val signals = acceptedLabelMarginSignals(
            listOf(
                candidate("round-1", accepted = true, confidence = 0.90, acceptedAt = 100),
                candidate("round-2", accepted = true, confidence = 0.90, acceptedAt = 200),
                candidate("other-revision", accepted = true, confidence = 0.90, revisionId = "revision-2"),
            ),
        )

        assertTrue(
            "不同轮/不同修订的候选不是对手：三个都已接受且各自无对手 ⇒ margin 全为不可判",
            signals.all { it.margin == null && !it.suspect },
        )
    }

    @Test
    fun multipleAcceptedLabelsInOneDimensionEachGetTheirOwnMargin() {
        val signals = acceptedLabelMarginSignals(
            listOf(
                candidate("top", accepted = true, confidence = 0.92),
                candidate("second", accepted = true, confidence = 0.90),
            ),
        )

        val top = signals.single { it.labelId == "label:top" }
        val second = signals.single { it.labelId == "label:second" }
        assertEquals(0.02, top.margin!!, 1e-9)
        assertTrue("预算允许接受多个，但 top 与第二只差 0.02 ⇒ 两者都存疑", top.suspect)
        assertEquals(-0.02, second.margin!!, 1e-9)
        assertTrue("排名低于同轮已接受候选 ⇒ 负 margin ⇒ 存疑", second.suspect)
    }

    @Test
    fun theDerivationIsPureAndDoesNotTouchTheInputRows() {
        val rows = listOf(
            candidate("kept", accepted = true, confidence = 0.90),
            candidate("rival", accepted = false, confidence = 0.89),
        )
        val snapshot = rows.toList()

        acceptedLabelMarginSignals(rows)

        assertEquals("派生不得改写任何行（只呈现，不自动纠正）", snapshot, rows)
    }

    private fun candidate(
        name: String,
        accepted: Boolean,
        confidence: Double,
        dimension: String = "KNOWLEDGE",
        acceptedAt: Long = 2_000,
        revisionId: String = "revision-1",
    ) = ProblemClassificationCandidateRecord(
        candidateId = "classification-candidate:$name",
        problemId = "problem-1",
        basisRevisionId = revisionId,
        dimension = dimension,
        labelId = "label:$name",
        displayName = name,
        confidence = confidence,
        accepted = accepted,
        acceptanceSource = "LOCAL_POLICY_ACCEPTED",
        acceptedAtEpochMillis = acceptedAt,
    )
}
