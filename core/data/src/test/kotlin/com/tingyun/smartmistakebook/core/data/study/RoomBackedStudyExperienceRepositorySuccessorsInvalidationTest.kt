package com.tingyun.smartmistakebook.core.data.study

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * S20：仓库侧接线——`knowledge_node` 表级失效信号 → 后继映射缓存失效 → 下一次 drain 重读。
 *
 * 这是"失效条件与 `knowledge_node` 变化/合并退役写路径对齐"的仓库侧证据：内容调和是唯一改
 * `superseded_by` 的生产写路径，它经 Room DAO 落库，Room 的表级失效让
 * `observeKnowledgeNodeChanges()` 发射；假库在这里扮演 Room 的角色（真库实现见
 * `RoomStudyDatabase.observeKnowledgeNodeChanges`）。
 */
class RoomBackedStudyExperienceRepositorySuccessorsInvalidationTest {

    @Test
    fun `a knowledge-node change signal invalidates the successors cache for the next drain`() =
        runBlocking {
            val database = SignallingFakeDatabase()
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            val repository = RoomBackedStudyExperienceRepository(
                database = database,
                applicationScope = scope,
                clock = Clock.fixed(
                    Instant.parse("2026-01-02T08:00:00Z"),
                    ZoneId.of("Asia/Shanghai"),
                ),
                studyZoneId = ZoneId.of("Asia/Shanghai"),
            )
            try {
                repository.initialize()
                assertEquals("首次重建读一次后继映射", 1, database.successorReads)

                repository.refresh()
                assertEquals(
                    "没有 knowledge_node 变化时命中缓存（不重读）",
                    1,
                    database.successorReads,
                )

                // 内容调和的合并退役：写库 → Room 表级失效。
                database.successors = mapOf("kc-old" to "kc-final")
                database.publishKnowledgeNodeChange()
                repository.refresh()

                assertEquals("knowledge_node 变化后必须重读", 2, database.successorReads)
                assertEquals(
                    "重读带上新映射",
                    mapOf("kc-old" to "kc-final"),
                    database.successorsRead.last(),
                )
            } finally {
                repository.close()
                scope.cancel()
            }
        }
}

/**
 * 在既有 [FakeStudyDatabasePort] 上加两处：可变更的后继映射（计数）与失效信号。
 */
private class SignallingFakeDatabase : FakeStudyDatabasePort() {
    var successors: Map<String, String> = emptyMap()
    var successorReads: Int = 0
        private set

    /** 每次读返回的映射，供"重读带上新值"断言。 */
    val successorsRead = mutableListOf<Map<String, String>>()

    private val knowledgeNodeChanges = MutableSharedFlow<Unit>(replay = 1, extraBufferCapacity = 8)

    override suspend fun readKnowledgeNodeSuccessors(): Map<String, String> {
        successorReads++
        successorsRead += successors
        return successors
    }

    override fun observeKnowledgeNodeChanges(): Flow<Unit> = knowledgeNodeChanges

    fun publishKnowledgeNodeChange() {
        check(knowledgeNodeChanges.tryEmit(Unit)) { "invalidation signal buffer overflowed" }
    }
}
