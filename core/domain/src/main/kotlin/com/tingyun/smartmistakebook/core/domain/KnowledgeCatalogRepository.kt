package com.tingyun.smartmistakebook.core.domain

import com.tingyun.smartmistakebook.core.model.CatalogPage
import com.tingyun.smartmistakebook.core.model.CatalogQuery
import java.text.Normalizer
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** User-facing complete catalog search, separate from bounded tutor recall. */
interface KnowledgeCatalogRepository {
    suspend fun search(query: CatalogQuery): CatalogPage
    fun observeChanges(): Flow<Unit> = emptyFlow()
}

/** Signals that the UI must restart pagination rather than merge incompatible pages. */
class StaleCatalogCursorException : IllegalArgumentException("知识目录已更新，请重新搜索")

fun normalizeCatalogText(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFKC).lowercase(Locale.ROOT).trim()

/** Greedy longest phrase matching keeps user-facing concepts, never index character grams. */
fun extractCatalogKeywords(sentence: String, approvedPhrases: List<String>): List<String> {
    val normalized = normalizeCatalogText(sentence)
    if (normalized.isEmpty()) return emptyList()
    val phrases = approvedPhrases.asSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinctBy(::normalizeCatalogText)
        .map { it to normalizeCatalogText(it) }
        .filter { it.second.isNotEmpty() }
        .sortedWith(compareByDescending<Pair<String, String>> { it.second.length }.thenBy { it.second })
        .toList()
    val result = linkedSetOf<String>()
    var position = 0
    while (position < normalized.length) {
        val match = phrases.firstOrNull { (_, key) -> normalized.startsWith(key, position) }
        if (match == null) position++ else {
            result += match.first
            position += match.second.length
        }
    }
    return result.toList()
}
