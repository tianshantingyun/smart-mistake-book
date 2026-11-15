package com.tingyun.smartmistakebook.core.data.model

import com.tingyun.smartmistakebook.core.model.TutorLobbyLocalActionOutcome
import com.tingyun.smartmistakebook.core.model.TutorToolRoundResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TutorActionProgressTextTest {
    @Test fun actualActionReceiptsRemainVisibleWithTheToolHeadline() {
        val outcomes = listOf(
            TutorLobbyLocalActionOutcome("SAVE_TO_NOTEBOOK", "SUCCEEDED", "已经加入错题本。", ok = true),
            TutorLobbyLocalActionOutcome("OPEN_PROBLEM", "FAILED", "当前导航窗口不可用。", ok = false),
        )
        assertEquals("已查阅错题本\n已经加入错题本。\n当前导航窗口不可用。",
            tutorActionProgressText("已查阅错题本", outcomes))
        assertTrue(tutorActionProgressText(null, outcomes).contains("当前导航窗口不可用。"))
    }

    @Test fun progressTextIsBounded() {
        val outcomes = List(4) { TutorLobbyLocalActionOutcome("START_EXPORT", "QUEUED", "字".repeat(2_000)) }
        assertTrue(tutorActionProgressText("字".repeat(4_000), outcomes).length <=
            TutorToolRoundResult.MAX_TOOL_ROUND_RESULT_CHARS)
    }
}
