package com.tingyun.smartmistakebook

internal data class StarLabelCandidate(val id: String, val x: Float, val y: Float, val width: Float, val height: Float)
internal data class PlacedStarLabel(val id: String, val left: Float, val top: Float, val width: Float, val height: Float) {
    val right get() = left + width
    val bottom get() = top + height
}

/** Input is ordered by focus priority. Hide colliding labels, never their knowledge stars. */
internal fun constellationLabels(
    candidates: List<StarLabelCandidate>, width: Float, height: Float, gap: Float, clearance: Float,
): List<PlacedStarLabel> {
    val placed = mutableListOf<PlacedStarLabel>()
    candidates.forEach { item ->
        if (item.x !in 0f..width || item.y !in 0f..height) return@forEach
        if (item.width > width - 2 * gap || item.height > height - 2 * gap) return@forEach
        val centeredX = (item.x - item.width / 2).coerceIn(gap, width - gap - item.width)
        val options = listOf(
            centeredX to item.y + clearance,
            centeredX to item.y - clearance - item.height,
            item.x + clearance to item.y - item.height / 2,
            item.x - clearance - item.width to item.y - item.height / 2,
        )
        options.map { (x, y) -> PlacedStarLabel(item.id, x, y, item.width, item.height) }
            .firstOrNull { next ->
                next.left >= gap && next.top >= gap && next.right <= width - gap && next.bottom <= height - gap &&
                    placed.none { other -> next.left < other.right + gap && next.right + gap > other.left &&
                        next.top < other.bottom + gap && next.bottom + gap > other.top } &&
                    candidates.none { star -> star.x > next.left - gap && star.x < next.right + gap &&
                        star.y > next.top - gap && star.y < next.bottom + gap }
            }?.let(placed::add)
    }
    return placed
}
