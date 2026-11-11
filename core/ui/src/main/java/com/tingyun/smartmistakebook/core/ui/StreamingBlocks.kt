package com.tingyun.smartmistakebook.core.ui

/**
 * A streamed tutor reply, split for rendering: [completed] blocks are stable and render with the
 * full pipeline (markdown + math), while [tail] is still growing and renders as tolerant plain
 * text so an unterminated `$$`, code fence or `**` cannot flicker.
 *
 * This mirrors how open-source streaming-markdown renderers work (e.g. Vercel's streamdown:
 * "when you tokenize and stream it, new challenges arise" — solved by treating the unfinished
 * tail separately), and it lets the completed blocks be memoized: their strings never change
 * again, so Compose can skip re-composing them.
 */
internal data class StreamingBlocks(
    val completed: List<String>,
    val tail: String,
)

/**
 * Splits streamed markdown at top-level block boundaries (blank lines) while never cutting inside
 * an open code fence or an open `$$…$$` display-math block — those regions belong to the tail
 * until they close.
 */
internal fun splitStreamingBlocks(markdown: String): StreamingBlocks {
    if (markdown.isBlank()) return StreamingBlocks(emptyList(), "")
    val completed = ArrayList<String>()
    val current = StringBuilder()
    var fenceMarker: Char? = null
    var fenceLength = 0
    var displayMathOpen = false

    fun flushBlock() {
        if (current.isNotBlank()) completed.add(current.toString().trimEnd())
        current.setLength(0)
    }

    for (line in markdown.split('\n')) {
        val trimmed = line.trim()
        val marker = trimmed.firstOrNull()?.takeIf { it == '`' || it == '~' }
        val markerLength = if (marker == null) 0 else trimmed.takeWhile { it == marker }.length
        val fenceOpen = fenceMarker != null
        if (fenceOpen) {
            if (marker == fenceMarker && markerLength >= fenceLength && trimmed.drop(markerLength).isBlank()) {
                fenceMarker = null
            }
        } else if (!displayMathOpen && markerLength >= 3 && line.takeWhile { it == ' ' }.length <= 3) {
            fenceMarker = marker
            fenceLength = markerLength
        } else if (trimmed.startsWith("$$")) {
            val closedOnSameLine = trimmed.length > 4 && trimmed.endsWith("$$")
            if (!closedOnSameLine) displayMathOpen = !displayMathOpen
        }
        if (trimmed.isEmpty() && fenceMarker == null && !displayMathOpen) {
            flushBlock()
            continue
        }
        current.append(line).append('\n')
    }
    return StreamingBlocks(completed = completed, tail = current.toString().trim())
}
