package com.tingyun.smartmistakebook.core.data.mistake

import com.tingyun.smartmistakebook.core.database.KnowledgeNodeSeedRecord
import com.tingyun.smartmistakebook.core.model.KnowledgeCatalogPosition
import com.tingyun.smartmistakebook.core.database.port.KnowledgeReadPort
import com.tingyun.smartmistakebook.core.domain.KnowledgeCatalogRepository
import com.tingyun.smartmistakebook.core.domain.StaleCatalogCursorException
import com.tingyun.smartmistakebook.core.domain.extractCatalogKeywords
import com.tingyun.smartmistakebook.core.domain.normalizeCatalogText
import com.tingyun.smartmistakebook.core.model.CatalogCursor
import com.tingyun.smartmistakebook.core.model.CatalogHit
import com.tingyun.smartmistakebook.core.model.CatalogPage
import com.tingyun.smartmistakebook.core.model.CatalogQuery
import com.tingyun.smartmistakebook.core.model.KnowledgeNodeSelection
import com.tingyun.smartmistakebook.core.model.figureSha256Hex
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Application-scoped catalog. Only small phrase metadata is cached, never full teaching prose. */
class RoomKnowledgeCatalogRepository(
    private val database: KnowledgeReadPort,
    scope: CoroutineScope,
) : KnowledgeCatalogRepository {
    private val session = UUID.randomUUID().toString()
    private val generation = MutableStateFlow("$session:0")
    private val ready = CompletableDeferred<Unit>()
    private val phraseLock = Mutex()
    private var phraseCache: Pair<String, List<String>>? = null
    @Volatile private var observationFailure: Exception? = null

    init {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                var version = 0L
                database.observeKnowledgeCatalogChanges().collect {
                    generation.value = "$session:${++version}"
                    ready.complete(Unit)
                }
                ready.complete(Unit)
            } catch (cancelled: CancellationException) {
                ready.completeExceptionally(cancelled)
                throw cancelled
            } catch (failure: Exception) {
                observationFailure = failure
                ready.completeExceptionally(failure)
            }
        }
    }

    override fun observeChanges(): Flow<Unit> = generation.drop(1).map { Unit }

    override suspend fun search(query: CatalogQuery): CatalogPage = withContext(Dispatchers.IO) {
        ready.await()
        observationFailure?.let { throw it }
        val revision = generation.value
        val text = normalizeCatalogText(query.text)
        val querySha = figureSha256Hex(text.toByteArray(Charsets.UTF_8))
        val after = query.cursor?.let { cursor ->
            if (cursor.catalogRevision != revision || cursor.querySha256 != querySha) throw StaleCatalogCursorException()
            try {
                Json.decodeFromString<KnowledgeCatalogPosition>(cursor.lastSortKey)
            } catch (_: IllegalArgumentException) {
                throw StaleCatalogCursorException()
            }
        }
        val dictionary = phraseLock.withLock {
            phraseCache?.takeIf { it.first == revision }?.second ?: database.readKnowledgeCatalogPhrases()
                .also { phraseCache = revision to it }
        }
        val terms = extractCatalogKeywords(text, dictionary).map(::normalizeCatalogText)
            .ifEmpty { if (text.isEmpty()) emptyList() else listOf(text) }
        val rows = database.readKnowledgeCatalogPage(terms, after, query.pageSize + 1)
        val page = rows.take(query.pageSize)
        val ancestors = page.associate { it.node.knowledgeNodeId to it.node }.toMutableMap()
        for (depth in 0 until 64) {
            val missing = ancestors.values.mapNotNull { it.parentKnowledgeNodeId }.toSet() - ancestors.keys
            if (missing.isEmpty()) break
            val parents = database.readKnowledgeNodesByIds(missing)
            if (parents.isEmpty()) break
            val previousSize = ancestors.size
            ancestors.putAll(parents.associateBy { it.knowledgeNodeId })
            if (ancestors.size == previousSize) break
        }
        if (generation.value != revision) throw StaleCatalogCursorException()
        CatalogPage(
            hits = page.map { row ->
                CatalogHit(
                    node = KnowledgeNodeSelection(row.node.knowledgeNodeId, row.node.taxonomyVersion),
                    subject = row.node.subject,
                    chapterPath = parentPath(row.node, ancestors),
                    name = row.node.canonicalName.ifBlank { row.node.displayName },
                    snippet = snippet(row.snippet, terms),
                    keywords = extractCatalogKeywords(snippet(row.snippet, terms), dictionary).take(8),
                )
            },
            next = if (rows.size > query.pageSize) page.lastOrNull()?.let {
                CatalogCursor(querySha, revision, Json.encodeToString(it.position))
            } else null,
            catalogRevision = revision,
        )
    }

    private fun parentPath(node: KnowledgeNodeSeedRecord, nodes: Map<String, KnowledgeNodeSeedRecord>): List<String> {
        val seen = mutableSetOf(node.knowledgeNodeId)
        val path = mutableListOf<String>()
        var parent = node.parentKnowledgeNodeId
        while (parent != null && seen.add(parent) && path.size < 64) {
            val record = nodes[parent] ?: break
            path += record.canonicalName.ifBlank { record.displayName }
            parent = record.parentKnowledgeNodeId
        }
        return path.asReversed()
    }

    private fun snippet(raw: String, terms: List<String>): String {
        val text = raw.replace('\u001F', '、')
        val normalized = normalizeCatalogText(text)
        val match = terms.map { normalized.indexOf(it) }.filter { it >= 0 }.minOrNull() ?: 0
        val start = (match - 48).coerceIn(0, text.length)
        val end = (start + 240).coerceAtMost(text.length)
        return (if (start > 0) "…" else "") + text.substring(start, end) + if (end < text.length) "…" else ""
    }
}
