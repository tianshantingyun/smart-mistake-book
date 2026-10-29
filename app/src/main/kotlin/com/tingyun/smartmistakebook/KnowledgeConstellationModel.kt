package com.tingyun.smartmistakebook

import com.tingyun.smartmistakebook.core.domain.StudyKnowledgeSummary
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

internal data class KnowledgeStar(val knowledge: StudyKnowledgeSummary, val x: Float, val y: Float)
internal data class KnowledgeStarLink(val prerequisiteId: String, val dependentId: String)

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
): List<KnowledgeStar> {
    val sorted = knowledge.distinctBy { it.knowledgeNodeId }.sortedBy { it.knowledgeNodeId }
    if (sorted.isEmpty()) return emptyList()
    val ids = sorted.mapIndexed { index, item -> item.knowledgeNodeId to index }.toMap()
    val positions = sorted.indices.map { i ->
        val angle = i * 2.3999632297
        val radius = .12 + .29 * sqrt((i + .5) / sorted.size)
        floatArrayOf((.5 + cos(angle) * radius).toFloat(), (.5 + sin(angle) * radius).toFloat())
    }
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
            forces[a][0] += dx * .035f; forces[a][1] += dy * .035f
            forces[b][0] -= dx * .035f; forces[b][1] -= dy * .035f
        }
        val cooling = 1f - iteration / 90f
        positions.forEachIndexed { i, position ->
            for (axis in 0..1) {
                val towardCenter = (.5f - position[axis]) * .012f
                position[axis] = (position[axis] + (forces[i][axis] + towardCenter)
                    .coerceIn(-.018f, .018f) * cooling).coerceIn(.12f, .88f)
            }
        }
    }
    return sorted.mapIndexed { i, item -> KnowledgeStar(item, positions[i][0], positions[i][1]) }
}
