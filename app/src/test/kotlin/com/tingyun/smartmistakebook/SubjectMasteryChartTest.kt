package com.tingyun.smartmistakebook

import com.tingyun.smartmistakebook.core.domain.StudyKnowledgeSummary
import com.tingyun.smartmistakebook.core.model.MasteryStatus
import com.tingyun.smartmistakebook.core.model.SubjectKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubjectMasteryChartTest {
    private fun point(id: String, value: Double, upper: Double? = .9) = StudyKnowledgeSummary(
        knowledgeNodeId = id, displayName = id, subject = SubjectKind.MATH,
        status = MasteryStatus.LEARNING, conservativeMasteryScore = value, masteryIntervalUpper = upper)

    @Test fun chartUsesTheSameSubjectMeanAsTheDetailRows() {
        val points = subjectMasteryPoints(listOf(point("a", .4), point("b", .8)))
        assertEquals(.6f, points.single().estimate!!, .0001f)
    }

    @Test fun absentEvidenceIsNotTurnedIntoAZeroScore() {
        assertNull(subjectMasteryPoints(listOf(point("a", 0.0, null))).single().estimate)
    }

    @Test fun repeatedKnowledgeDoesNotChangeTheMean() {
        assertEquals(.6f, subjectMasteryPoints(listOf(point("a", .4), point("a", .4), point("b", .8))).single().estimate!!, .0001f)
    }
}
