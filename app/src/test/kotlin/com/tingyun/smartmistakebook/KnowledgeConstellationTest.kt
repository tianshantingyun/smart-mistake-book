package com.tingyun.smartmistakebook

import com.tingyun.smartmistakebook.core.domain.StudyKnowledgeSummary
import com.tingyun.smartmistakebook.core.model.MasteryStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KnowledgeConstellationTest {
    private fun knowledge(id: String) = StudyKnowledgeSummary(id, id, MasteryStatus.UNKNOWN, 0.0)

    @Test fun onlyConfirmedVisiblePrerequisiteRelationsBecomeLinks() {
        val links = constellationLinks(setOf("a", "b", "c"), mapOf(
            "b" to setOf("a", "b", "outside"), "outside" to setOf("a")))
        assertEquals(listOf(KnowledgeStarLink("a", "b")), links)
    }

    @Test fun nearbyKnowledgeNeverCreatesAnInventedRelation() {
        assertTrue(constellationLinks(setOf("a", "b"), emptyMap()).isEmpty())
    }

    @Test fun layoutIsStableUnderReorderingAndFitsTheViewport() {
        val records = (1..18).map { knowledge("point-$it") }
        val links = listOf(KnowledgeStarLink("point-1", "point-2"))
        val stars = constellationStars(records, links)
        assertEquals(stars, constellationStars(records.reversed(), links))
        assertTrue(stars.all { it.x.isFinite() && it.y.isFinite() && it.x in 0.12f..0.88f && it.y in 0.12f..0.88f })
        assertEquals(18, stars.map { it.x to it.y }.distinct().size)
    }

    @Test fun masteryUpdatesDoNotMoveTheStars() {
        val records = listOf(knowledge("a"), knowledge("b"))
        val before = constellationStars(records, emptyList()).map { it.x to it.y }
        val after = constellationStars(records.map { it.copy(status = MasteryStatus.MASTERED,
            conservativeMasteryScore = .9) }, emptyList()).map { it.x to it.y }
        assertEquals(before, after)
    }

    @Test fun missingOrDuplicateKnowledgeDoesNotCreateExtraStars() {
        assertTrue(constellationStars(emptyList(), emptyList()).isEmpty())
        val star = knowledge("a")
        assertEquals(1, constellationStars(listOf(star, star), emptyList()).size)
    }
}
