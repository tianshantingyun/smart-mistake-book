package com.tingyun.smartmistakebook.core.data.mistake

import com.tingyun.smartmistakebook.core.data.study.FakeStudyDatabasePort
import com.tingyun.smartmistakebook.core.database.KnowledgeNodeSeedRecord
import com.tingyun.smartmistakebook.core.database.port.KnowledgeCatalogRecord
import com.tingyun.smartmistakebook.core.database.port.KnowledgeReadPort
import com.tingyun.smartmistakebook.core.domain.StaleCatalogCursorException
import com.tingyun.smartmistakebook.core.model.CatalogQuery
import com.tingyun.smartmistakebook.core.model.KnowledgeCatalogPosition
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomKnowledgeCatalogRepositoryTest {
    @Test fun cursorIsBoundToQueryAndPagePosition() = runBlocking {
        val lifetime = SupervisorJob()
        try {
            val port = CatalogPort()
            val repository = RoomKnowledgeCatalogRepository(port, CoroutineScope(coroutineContext + lifetime))
            val first = repository.search(CatalogQuery("函数", pageSize = 2))
            assertNotNull(first.next)
            val second = repository.search(CatalogQuery("函数", first.next, 2))
            assertEquals(listOf("3"), second.hits.map { it.node.nodeId })
            assertEquals(first.hits.last().node.nodeId, port.lastPosition?.nodeId)
            val before = port.reads
            assertTrue(runCatching { repository.search(CatalogQuery("最值", first.next)) }
                .exceptionOrNull() is StaleCatalogCursorException)
            assertEquals(before, port.reads)
            assertEquals(1, port.phraseReads)
        } finally { lifetime.cancel() }
    }

    @Test fun invalidationRejectsOldCursorAndRefreshesPhrases() = runBlocking {
        val lifetime = SupervisorJob()
        try {
            val port = CatalogPort()
            val repository = RoomKnowledgeCatalogRepository(port, CoroutineScope(coroutineContext + lifetime))
            val first = repository.search(CatalogQuery("函数", pageSize = 2))
            val changed = async(start = CoroutineStart.UNDISPATCHED) { repository.observeChanges().first() }
            port.changes.emit(Unit)
            changed.await()
            assertTrue(runCatching { repository.search(CatalogQuery("函数", first.next)) }
                .exceptionOrNull() is StaleCatalogCursorException)
            val fresh = repository.search(CatalogQuery("函数"))
            assertFalse(first.catalogRevision == fresh.catalogRevision)
            assertEquals(2, port.phraseReads)
        } finally { lifetime.cancel() }
    }

    @Test fun damagedSavedCursorIsReportedAsStale() = runBlocking {
        val lifetime = SupervisorJob()
        try {
            val port = CatalogPort()
            val repository = RoomKnowledgeCatalogRepository(port, CoroutineScope(coroutineContext + lifetime))
            val first = repository.search(CatalogQuery("函数", pageSize = 2))
            val cursor = first.next!!.copy(lastSortKey = "not-json")
            assertTrue(runCatching { repository.search(CatalogQuery("函数", cursor)) }
                .exceptionOrNull() is StaleCatalogCursorException)
            val invalidPosition = first.next!!.copy(lastSortKey =
                """{"score":-1,"subject":"MATH","name":"name","nodeId":"1"}""")
            assertTrue(runCatching { repository.search(CatalogQuery("函数", invalidPosition)) }
                .exceptionOrNull() is StaleCatalogCursorException)
        } finally { lifetime.cancel() }
    }

    @Test fun cancellationDoesNotPoisonAnotherSearch() = runBlocking {
        val lifetime = SupervisorJob()
        try {
            val port = CatalogPort()
            port.block = CompletableDeferred()
            val repository = RoomKnowledgeCatalogRepository(port, CoroutineScope(coroutineContext + lifetime))
            val search = async { repository.search(CatalogQuery("函数")) }
            port.entered.await()
            search.cancelAndJoin()
            assertTrue(search.isCancelled)
            port.block = null
            assertEquals(3, repository.search(CatalogQuery("函数")).hits.size)
        } finally { lifetime.cancel() }
    }

    @Test fun invalidationWhileReadingDoesNotReturnMixedGenerationPage() = runBlocking {
        val lifetime = SupervisorJob()
        try {
            val port = CatalogPort()
            port.block = CompletableDeferred()
            val repository = RoomKnowledgeCatalogRepository(port, CoroutineScope(coroutineContext + lifetime))
            val search = async { runCatching { repository.search(CatalogQuery("函数")) } }
            port.entered.await()
            val changed = async(start = CoroutineStart.UNDISPATCHED) { repository.observeChanges().first() }
            port.changes.emit(Unit)
            changed.await()
            port.block!!.complete(Unit)
            assertTrue(search.await().exceptionOrNull() is StaleCatalogCursorException)
        } finally { lifetime.cancel() }
    }

    private class CatalogPort : KnowledgeReadPort by FakeStudyDatabasePort() {
        val changes = MutableSharedFlow<Unit>(replay = 1).also { it.tryEmit(Unit) }
        val entered = CompletableDeferred<Unit>()
        var block: CompletableDeferred<Unit>? = null
        var reads = 0
        var phraseReads = 0
        var lastPosition: KnowledgeCatalogPosition? = null
        override fun observeKnowledgeCatalogChanges() = changes
        override suspend fun readKnowledgeCatalogPhrases(): List<String> {
            phraseReads++
            return listOf("函数")
        }
        override suspend fun readKnowledgeCatalogPage(
            terms: List<String>, after: KnowledgeCatalogPosition?, limit: Int,
        ): List<KnowledgeCatalogRecord> {
            reads++
            entered.complete(Unit)
            block?.await()
            lastPosition = after
            return (1..3).map { index ->
                val node = KnowledgeNodeSeedRecord(
                    knowledgeNodeId = "$index", stableCode = "$index", subject = "MATH",
                    displayName = "函数$index", parentKnowledgeNodeId = null,
                    taxonomyVersion = "v1", createdAtEpochMillis = 1,
                )
                KnowledgeCatalogRecord(node, "函数的完整描述", KnowledgeCatalogPosition(1, "MATH", node.displayName, "$index"))
            }.filter { after == null || it.node.knowledgeNodeId > after.nodeId }.take(limit)
        }
    }
}
