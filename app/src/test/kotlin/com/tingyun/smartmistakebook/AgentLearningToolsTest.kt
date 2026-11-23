package com.tingyun.smartmistakebook

import com.tingyun.smartmistakebook.core.domain.ReviewDayPreference
import com.tingyun.smartmistakebook.core.domain.StudyDataStatus
import com.tingyun.smartmistakebook.core.domain.StudyExperienceSnapshot
import com.tingyun.smartmistakebook.core.domain.StudyReviewOverview
import com.tingyun.smartmistakebook.feature.tutor.TutorLearningToolDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AgentLearningToolsTest {
    @Test
    fun rejectsInventedAndRepeatedPaperCodes() {
        val tool = TutorLearningToolDraft("【智能体组卷】", "选两道题", "[012345abcdef] 数学 单调性")
        val source = tool.messageFor("请改成一道题\n[deadbeef0000] 学生举例")
        assertEquals("请改成一道题\n[deadbeef0000] 学生举例", TutorLearningToolDraft.fromMessage(source)?.request)
        assertEquals(
            AgentToolProposal.Paper(listOf("012345abcdef")),
            decodeAgentToolProposal("""建议一题
                |【组卷确认数据】{"ids":["012345abcdef"]}""".trimMargin(), source),
        )
        assertNull(decodeAgentToolProposal("""【组卷确认数据】{"ids":["deadbeef0000"]}""", source))
        assertNull(decodeAgentToolProposal("""【组卷确认数据】{"ids":["012345abcdef","012345abcdef"]}""", source))
    }

    @Test
    fun datesOutsideTheWindowAndStartedSessionsAreProtected() {
        val active = StudyExperienceSnapshot(
            status = StudyDataStatus.READY, review = StudyReviewOverview(activeSessionId = "doing"),
        )
        val day = ReviewDayPreference(99, emptyList(), 7, 50)
        assertNotNull(reviewAdviceIssue(AgentToolProposal.Review(listOf(day)), active, 100))
        assertNotNull(reviewAdviceIssue(AgentToolProposal.Review(listOf(day.copy(epochDay = 100))), active, 100))
        assertNull(reviewAdviceIssue(AgentToolProposal.Review(listOf(day.copy(epochDay = 101))), active, 100))
    }
}
