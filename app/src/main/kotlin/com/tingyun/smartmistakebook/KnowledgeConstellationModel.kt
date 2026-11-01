package com.tingyun.smartmistakebook

import com.tingyun.smartmistakebook.core.domain.StudyKnowledgeSummary
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

internal const val STARS_PER_PAGE = 18

internal data class KnowledgeStar(val knowledge: StudyKnowledgeSummary, val x: Float, val y: Float)
internal data class KnowledgeStarLink(val prerequisiteId: String, val dependentId: String)

internal data class ConstellationDestination(val subjectName: String, val page: Int)

/** One confirmed hop around the anchor, including recorded neighbors beyond the overview page. */
internal fun constellationNeighborhood(
    knowledge: List<StudyKnowledgeSummary>, prerequisites: Map<String, Set<String>>, anchorId: String,
): List<StudyKnowledgeSummary> {
    val records = knowledge.distinctBy { it.knowledgeNodeId }.associateBy { it.knowledgeNodeId }
    val anchor = records[anchorId] ?: return emptyList()
    val related = prerequisites[anchorId].orEmpty() + prerequisites.filterValues { anchorId in it }.keys
    return listOf(anchor) + (related - anchorId).sorted().mapNotNull(records::get)
}

/** A relation can lead beyond the current page or subject; reveal the actual destination. */
internal fun constellationDestination(
    knowledge: List<StudyKnowledgeSummary>, subjectName: String, id: String,
): ConstellationDestination? {
    val sorted = knowledge.distinctBy { it.knowledgeNodeId }.sortedBy { it.knowledgeNodeId }
    val target = sorted.firstOrNull { it.knowledgeNodeId == id } ?: return null
    val nextSubject = if (subjectName.isEmpty()) "" else target.subject.name
    val index = sorted.filter { nextSubject.isEmpty() || it.subject.name == nextSubject }
        .indexOfFirst { it.knowledgeNodeId == id }
    return ConstellationDestination(nextSubject, index / STARS_PER_PAGE)
}

internal fun constellationLinks(
    knowledgeIds: Set<String>,
    prerequisites: Map<String, Set<String>>,
): List<KnowledgeStarLink> = prerequisites.flatMap { (dependent, earlier) ->
    earlier.mapNotNull { prerequisite ->
        if (dependent != prerequisite && dependent in knowledgeIds && prerequisite in knowledgeIds)
            KnowledgeStarLink(prerequisite, dependent) else null
    }
}.distinct().sortedWith(compareBy({ it.prerequisiteId }, { it.dependentId }))

/** Bounded, deterministic layout. Distances carry no learning or relationship meaning. */
internal fun constellationStars(
    knowledge: List<StudyKnowledgeSummary>,
    links: List<KnowledgeStarLink>,
    anchorId: String? = null,
): List<KnowledgeStar> {
    val sorted = knowledge.distinctBy { it.knowledgeNodeId }.sortedBy { it.knowledgeNodeId }
    if (sorted.isEmpty()) return emptyList()
    if (sorted.size == 1) return listOf(KnowledgeStar(sorted.first(), .5f, .5f))
    val ids = sorted.mapIndexed { index, item -> item.knowledgeNodeId to index }.toMap()
    val positions = sorted.indices.map { i ->
        val angle = i * 2.3999632297
        val radius = .12 + .29 * sqrt((i + .5) / sorted.size)
        floatArrayOf((.5 + cos(angle) * radius).toFloat(), (.5 + sin(angle) * radius).toFloat())
    }
    val anchorIndex = anchorId?.let(ids::get)
    if (anchorIndex != null) { positions[anchorIndex][0] = .5f; positions[anchorIndex][1] = .5f }
    repeat(72) { iteration ->
        val forces = Array(sorted.size) { FloatArray(2) }
        for (a in positions.indices) for (b in 0 until a) {
            val dx = positions[a][0] - positions[b][0]
            val dy = positions[a][1] - positions[b][1]
            val distance = sqrt(dx * dx + dy * dy).coerceAtLeast(.01f)
            val force = .00018f / (distance * distance)
            forces[a][0] += dx / distance * force; forces[a][1] += dy / distance * force
            forces[b][0] -= dx / distance * force; forces[b][1] -= dy / distance * force
        }
        links.forEach { link ->
            val a = ids[link.prerequisiteId] ?: return@forEach
            val b = ids[link.dependentId] ?: return@forEach
            val dx = positions[b][0] - positions[a][0]
            val dy = positions[b][1] - positions[a][1]
            val distance = sqrt(dx * dx + dy * dy).coerceAtLeast(.01f)
            // A local map needs breathing room around its fixed center, including with only two neighbors.
            val spring = if (anchorIndex != null) (distance - .32f) / distance else 1f
            forces[a][0] += dx * spring * .035f; forces[a][1] += dy * spring * .035f
            forces[b][0] -= dx * spring * .035f; forces[b][1] -= dy * spring * .035f
        }
        val cooling = 1f - iteration / 90f
        positions.forEachIndexed { i, position ->
            if (i == anchorIndex) return@forEachIndexed
            for (axis in 0..1) {
                val towardCenter = (.5f - position[axis]) * (if (anchorIndex == null) .012f else .005f)
                position[axis] = (position[axis] + (forces[i][axis] + towardCenter)
                    .coerceIn(-.018f, .018f) * cooling).coerceIn(.12f, .88f)
            }
        }
    }
    return sorted.mapIndexed { i, item -> KnowledgeStar(item, positions[i][0], positions[i][1]) }
}
