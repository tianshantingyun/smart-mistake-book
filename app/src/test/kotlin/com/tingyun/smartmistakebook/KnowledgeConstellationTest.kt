package com.tingyun.smartmistakebook

import com.tingyun.smartmistakebook.core.domain.StudyKnowledgeSummary
import com.tingyun.smartmistakebook.core.model.MasteryStatus
import com.tingyun.smartmistakebook.core.model.SubjectKind
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

    @Test fun relationNavigationRevealsTheTargetPageAndPreservesAllSubjectScope() {
        val records = (0..39).map { knowledge("point-${it.toString().padStart(2, '0')}") }
        assertEquals(ConstellationDestination("", 2), constellationDestination(records.reversed(), "", "point-38"))
        assertEquals(null, constellationDestination(records, "", "missing"))
    }

    @Test fun relationNavigationCanCrossSubjectsWithoutLandingOnAnEmptyGroup() {
        val records = (0..19).map { knowledge("math-${it.toString().padStart(2, '0')}").copy(subject = SubjectKind.MATH) } +
            knowledge("physics").copy(subject = SubjectKind.PHYSICS)
        assertEquals(ConstellationDestination("PHYSICS", 0), constellationDestination(records, "MATH", "physics"))
        assertEquals(ConstellationDestination("MATH", 1), constellationDestination(records, "PHYSICS", "math-19"))
    }

    @Test fun denseLabelsRemainInsideTheViewportWithoutCoveringEachOtherOrStars() {
        val candidates = (0..17).map { i -> StarLabelCandidate("$i", 70f + (i % 4) * 60f,
            40f + (i / 4) * 55f, 90f, 28f) }
        val labels = constellationLabels(candidates, 335f, 320f, 4f, 15f)
        assertTrue(labels.isNotEmpty())
        assertEquals("0", labels.first().id)
        assertTrue(labels.size < candidates.size)
        labels.forEachIndexed { index, label ->
            assertTrue(label.left >= 4f && label.top >= 4f && label.right <= 331f && label.bottom <= 316f)
            labels.take(index).forEach { other ->
                assertTrue(label.right + 4f <= other.left || label.left >= other.right + 4f ||
                    label.bottom + 4f <= other.top || label.top >= other.bottom + 4f)
            }
            assertTrue(candidates.none { star -> star.x > label.left - 4f && star.x < label.right + 4f &&
                star.y > label.top - 4f && star.y < label.bottom + 4f })
        }
    }

    @Test fun offscreenStarsNeverLeaveDetachedLabelsAtTheViewportEdge() {
        val labels = constellationLabels(listOf(StarLabelCandidate("outside", -20f, 40f, 90f, 28f),
            StarLabelCandidate("inside", 170f, 160f, 90f, 28f)), 335f, 320f, 4f, 15f)
        assertEquals(listOf("inside"), labels.map { it.id })
    }

    @Test fun focusedLabelNearTheEdgeCanStayVisibleWithoutMovingItsStar() {
        val labels = constellationLabels(listOf(StarLabelCandidate("focus", 298f, 170f, 148f, 32f)),
            335f, 320f, 4f, 15f)
        assertEquals(1, labels.size)
        assertEquals(331f, labels.single().right, .01f)
        assertTrue(labels.single().top > 170f)
    }

    @Test fun localGraphIncludesOnlyRecordedDirectNeighborsRegardlessOfOverviewPage() {
        val records = (0..39).map { knowledge("point-${it.toString().padStart(2, '0')}") }
        val graph = mapOf("point-38" to setOf("point-00", "missing"),
            "point-39" to setOf("point-38"), "point-00" to setOf("point-00", "point-21"))
        val local = constellationNeighborhood(records, graph, "point-00")
        assertEquals(listOf("point-00", "point-21", "point-38"), local.map { it.knowledgeNodeId })
        assertTrue(local.none { it.knowledgeNodeId == "point-39" })
    }

    @Test fun localGraphKeepsItsAnchorCenteredWhileOtherStarsRemainBounded() {
        val records = (0..17).map { knowledge("point-$it") }
        val stars = constellationStars(records, emptyList(), "point-17")
        val anchor = stars.single { it.knowledge.knowledgeNodeId == "point-17" }
        assertEquals(.5f, anchor.x, .001f)
        assertEquals(.5f, anchor.y, .001f)
        assertTrue(stars.all { it.x in 0.12f..0.88f && it.y in 0.12f..0.88f })
    }

    @Test fun missingRelationsDoNotTurnLocalGraphIntoInventedConnections() {
        val records = listOf(knowledge("a"), knowledge("b"))
        assertEquals(listOf("a"), constellationNeighborhood(records, emptyMap(), "a").map { it.knowledgeNodeId })
        assertTrue(constellationNeighborhood(records, emptyMap(), "missing").isEmpty())
    }

    @Test fun oneRecordedKnowledgeIsCenteredEvenWithoutRelations() {
        val star = constellationStars(listOf(knowledge("a")), emptyList()).single()
        assertEquals(.5f, star.x, .001f)
        assertEquals(.5f, star.y, .001f)
    }

    @Test fun sparseLocalNeighborsDoNotCollapseIntoTheCenter() {
        val stars = constellationStars(listOf(knowledge("a"), knowledge("b"), knowledge("c")),
            listOf(KnowledgeStarLink("a", "b"), KnowledgeStarLink("b", "c")), "b")
        stars.filter { it.knowledge.knowledgeNodeId != "b" }.forEach { star ->
            val dx = star.x - .5f; val dy = star.y - .5f
            assertTrue(kotlin.math.sqrt(dx * dx + dy * dy) > .23f)
        }
    }
}
