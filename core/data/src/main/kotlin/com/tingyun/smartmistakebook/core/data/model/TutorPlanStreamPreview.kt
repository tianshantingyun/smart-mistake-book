package com.tingyun.smartmistakebook.core.data.model

import com.tingyun.smartmistakebook.core.model.SafeInlineMarkdown
import com.tingyun.smartmistakebook.core.model.TutorTurnPlan
import kotlinx.serialization.json.Json

internal fun String.startsTutorPlanEnvelope(): Boolean =
    when (trimStart().firstOrNull()) {
        '{', '`' -> true
        else -> false
    }

/** Read only the student-visible, top-level opening from an unfinished plan object. */
internal fun String.tutorPlanOpeningPreview(): String? {
    val source = take(MAX_PLAN_PREVIEW_SOURCE_CHARS).trimStart()
        .removePrefix("```json").removePrefix("```").trimStart()
    if (!source.startsWith('{')) return null

    val closers = ArrayDeque<Char>()
    var opening: String? = null
    var index = 0
    while (index < source.length) {
        when (val char = source[index]) {
            '{' -> closers.addLast('}')
            '[' -> closers.addLast(']')
            '}', ']' -> {
                if (closers.removeLastOrNull() != char) return null
                if (closers.isEmpty()) break
            }
            '"' -> {
                val end = source.scanJsonString(index)
                if (closers.size == 1 && end.closed) {
                    val colon = source.skipJsonSpace(end.offset)
                    if (source.getOrNull(colon) == ':') {
                        when (source.decodeJsonString(index, end)) {
                            "toolRequests", "localActions" -> return null
                            "openingMarkdown" -> {
                                if (opening != null) return null
                                val valueStart = source.skipJsonSpace(colon + 1)
                                if (source.getOrNull(valueStart) != '"') return null
                                val valueEnd = source.scanJsonString(valueStart)
                                opening = source.decodeJsonString(valueStart, valueEnd) ?: return null
                                if (!valueEnd.closed) break
                                index = valueEnd.offset
                                continue
                            }
                        }
                    }
                }
                if (!end.closed) break
                index = end.offset
                continue
            }
        }
        index++
    }
    return opening?.takeIf {
        it.isNotBlank() &&
            !SafeInlineMarkdown.requiresPlainTextFallback(it) &&
            !SafeInlineMarkdown.BARE_URL.containsMatchIn(it) &&
            !it.contains("```") &&
            it.none { char -> (char.isISOControl() && char !in "\t\r\n") ||
                char in '\u202A'..'\u202E' || char in '\u2066'..'\u2069' ||
                char in "\u061C\u200E\u200F" }
    }?.take(TutorTurnPlan.MAX_OPENING_CHARS)?.let { text ->
        if (text.last().isHighSurrogate()) text.dropLast(1) else text
    }
}

private fun String.skipJsonSpace(start: Int): Int {
    var index = start
    while (index < length && this[index].isWhitespace()) index++
    return index
}

private data class JsonStringEnd(val offset: Int, val closed: Boolean)

private fun String.scanJsonString(start: Int): JsonStringEnd {
    var index = start + 1
    while (index < length) {
        when (this[index]) {
            '"' -> return JsonStringEnd(index + 1, closed = true)
            '\\' -> {
                val next = index + if (getOrNull(index + 1) == 'u') 6 else 2
                if (next > length) return JsonStringEnd(index, closed = false)
                index = next
            }
            else -> index++
        }
    }
    return JsonStringEnd(index, closed = false)
}

private fun String.decodeJsonString(start: Int, end: JsonStringEnd): String? {
    val token = substring(start, end.offset)
    return runCatching { Json.decodeFromString<String>(if (end.closed) token else token + '"') }
        .getOrNull()
}

private const val MAX_PLAN_PREVIEW_SOURCE_CHARS = 64 * 1024
