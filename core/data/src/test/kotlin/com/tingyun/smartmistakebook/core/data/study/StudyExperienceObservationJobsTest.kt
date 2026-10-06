package com.tingyun.smartmistakebook.core.data.study

import com.tingyun.smartmistakebook.core.database.MistakeRecord
import com.tingyun.smartmistakebook.core.domain.StudyKnowledgeCoverageOverview
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * S20：观察侧合并（`conflate` + `distinctUntilChanged`）——三条重建型路径的计数用例。
 *
 * 本测试直接驱动 [StudyExperienceObservationJobs]：仓库对三条路径的处理器各是**一次快照
 * 重建**（`onMistakes`/`onPendingDraftCount`/`onLedgerChanged` → `publishReadySnapshot`），
 * 因此处理器调用次数就是重建次数。用例在"重建在飞"时灌入突发事件：合并语义下应只多出
 * **一次**重建（合并后的最新值），而不是每个事件一次。
 *
 * 用 `Dispatchers.Unconfined` 让发射与处理器进入在同一线程上确定性交错；`tryEmit` 的
 * `MutableSharedFlow` 模拟 Room 的表级失效（一次写入可能连发多次）。
 */
class StudyExperienceObservationJobsTest {

    @Test
    fun `a burst of mistake emissions collapses to one rebuild while a rebuild is in flight`() =
        runBlocking {
            val database = BurstingFakeDatabase()
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            val rebuilds = GatedRebuilds<List<MistakeRecord>>()
            val jobs = jobs(database, scope, onMistakes = rebuilds::onRebuild)
            try {
                database.mistakeEmissions.tryEmit(listOf(mistake("e0")))
                rebuilds.awaitFirstEntered()

                // 重建在飞：10 次突发事件都在处理器忙碌期间到达。
                for (index in 1..10) {
                    database.mistakeEmissions.tryEmit(listOf(mistake("e$index")))
                }

                rebuilds.releaseFirst()
                rebuilds.awaitCalls(2)
                delay(200)

                assertEquals(
                    "10 次突发事件只产生 1 次合并后的重建（在飞 1 + 合并 1），不是 11 次",
                    2,
                    rebuilds.calls.get(),
                )
                assertEquals(
                    "最后一个值必须送达（conflate 只丢中间值）",
                    "e10",
                    rebuilds.lastSeen.get()?.single()?.entryId,
                )
            } finally {
                jobs.cancel()
                scope.cancel()
            }
        }

    @Test
    fun `a burst of draft-count emissions collapses to one rebuild while a rebuild is in flight`() =
        runBlocking {
            val database = BurstingFakeDatabase()
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            val rebuilds = GatedRebuilds<Int>()
            val jobs = jobs(database, scope, onPendingDraftCount = rebuilds::onRebuild)
            try {
                database.draftCountEmissions.tryEmit(1)
                rebuilds.awaitFirstEntered()

                for (count in 2..10) {
                    database.draftCountEmissions.tryEmit(count)
                }

                rebuilds.releaseFirst()
                rebuilds.awaitCalls(2)
                delay(200)

                assertEquals(
                    "10 次待批改计数突发只产生 1 次合并后的重建",
                    2,
                    rebuilds.calls.get(),
                )
                assertEquals("最后一个值必须送达", 10, rebuilds.lastSeen.get())
            } finally {
                jobs.cancel()
                scope.cancel()
            }
        }

    @Test
    fun `a burst of ledger-head emissions collapses to one rebuild while a rebuild is in flight`() =
        runBlocking {
            val database = BurstingFakeDatabase()
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            val rebuilds = GatedRebuilds<Unit>()
            val jobs = jobs(
                database,
                scope,
                onLedgerChanged = { rebuilds.onRebuild(Unit) },
            )
            try {
                database.ledgerHeadEmissions.tryEmit(1L)
                rebuilds.awaitFirstEntered()

                for (sequence in 2L..10L) {
                    database.ledgerHeadEmissions.tryEmit(sequence)
                }

                rebuilds.releaseFirst()
                rebuilds.awaitCalls(2)
                delay(200)

                assertEquals(
                    "10 次账本头突发只产生 1 次合并后的重建",
                    2,
                    rebuilds.calls.get(),
                )
            } finally {
                jobs.cancel()
                scope.cancel()
            }
        }

    @Test
    fun `consecutive identical mistake emissions do not trigger a second rebuild`() = runBlocking {
        val database = BurstingFakeDatabase()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val rebuilds = GatedRebuilds<List<MistakeRecord>>()
        val jobs = jobs(database, scope, onMistakes = rebuilds::onRebuild)
        try {
            val same = listOf(mistake("same"))
            database.mistakeEmissions.tryEmit(same)
            rebuilds.awaitCalls(1)

            // Room 的表级失效会让同一条观察流重复发射内容相同的值。
            database.mistakeEmissions.tryEmit(same)
            delay(200)

            assertEquals(
                "内容相同的重复发射不得再触发重建（distinctUntilChanged）",
                1,
                rebuilds.calls.get(),
            )
        } finally {
            jobs.cancel()
            scope.cancel()
        }
    }

    private fun jobs(
        database: BurstingFakeDatabase,
        scope: CoroutineScope,
        onMistakes: suspend (List<MistakeRecord>) -> Unit = {},
        onPendingDraftCount: suspend (Int) -> Unit = {},
        onLedgerChanged: suspend () -> Unit = {},
    ) = StudyExperienceObservationJobs(
        database = database,
        learnerId = "learner:local",
        mutex = Mutex(),
        scope = scope,
        coverageFlow = flowOf(StudyKnowledgeCoverageOverview()),
        onMistakes = onMistakes,
        onPendingDraftCount = onPendingDraftCount,
        onLedgerChanged = onLedgerChanged,
        onCoverage = {},
        onFailure = { error("观察循环不得失败：$it") },
    )

    private fun mistake(entryId: String) = MistakeRecord(
        entryId = entryId,
        problemId = "problem:$entryId",
        problemRevisionId = "revision:$entryId",
        practiceUnitId = "unit:$entryId",
        sourceKey = null,
        subject = "MATH",
        title = "title $entryId",
        problemMarkdown = "stem $entryId",
        status = "ACTIVE",
        createdAtEpochMillis = 1_000,
        nextReviewAtEpochMillis = null,
        retrievability = null,
    )
}

private const val OBSERVATION_TIMEOUT_MILLIS = 5_000L

/**
 * 带闸门的重建计数器：第一次调用会停住（模拟"重建在飞"），其余照常计数。
 */
private class GatedRebuilds<T> {
    val calls = AtomicInteger()
    val lastSeen = AtomicReference<T?>()
    private val entered = CompletableDeferred<Unit>()
    private val release = CompletableDeferred<Unit>()

    suspend fun onRebuild(value: T) {
        lastSeen.set(value)
        if (calls.incrementAndGet() == 1) {
            entered.complete(Unit)
            release.await()
        }
    }

    suspend fun awaitFirstEntered() {
        withTimeout(OBSERVATION_TIMEOUT_MILLIS) { entered.await() }
    }

    fun releaseFirst() {
        release.complete(Unit)
    }

    suspend fun awaitCalls(expected: Int) {
        withTimeout(OBSERVATION_TIMEOUT_MILLIS) {
            while (calls.get() < expected) delay(10)
        }
    }
}

/**
 * Room 表级失效的替身：三条观察流各是一支可连发的 `MutableSharedFlow`（`replay = 1`
 * 保证订阅建立前发出的值也能被观察到）。
 */
private class BurstingFakeDatabase : FakeStudyDatabasePort() {
    val mistakeEmissions =
        MutableSharedFlow<List<MistakeRecord>>(replay = 1, extraBufferCapacity = 16)
    val draftCountEmissions = MutableSharedFlow<Int>(replay = 1, extraBufferCapacity = 16)
    val ledgerHeadEmissions = MutableSharedFlow<Long>(replay = 1, extraBufferCapacity = 16)

    override fun observeMistakes(): Flow<List<MistakeRecord>> = mistakeEmissions

    override fun observePendingProblemDraftCount(): Flow<Int> = draftCountEmissions

    override fun observeLearningLedgerHead(learnerId: String): Flow<Long> = ledgerHeadEmissions
}
