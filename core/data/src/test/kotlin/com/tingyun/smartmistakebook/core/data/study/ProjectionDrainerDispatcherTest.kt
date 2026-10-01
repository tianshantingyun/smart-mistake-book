package com.tingyun.smartmistakebook.core.data.study

import com.tingyun.smartmistakebook.core.domain.LearningProjector
import com.tingyun.smartmistakebook.core.model.LearnerSnapshot
import com.tingyun.smartmistakebook.core.model.ProblemMemoryState
import com.tingyun.smartmistakebook.core.model.ProjectionCheckpoint
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * W4-2 投影热路径的 drainer 侧两条：S4（批内快照复用）与 S10（显式调度器）。
 *
 * S10：数据库段必须走注入的 IO 调度器、投影计算段必须走注入的计算调度器——drain 的调用方
 * 可能是任意协程上下文，工作不该留在那里。
 * S4：一次 drain 调用只在开头读一次当前快照；后续批次复用上一笔提交返回的快照，
 * 不再每步重读全部投影表。
 */
class ProjectionDrainerDispatcherTest {

    @Test
    fun `drain routes database work to the io dispatcher and projection to the compute dispatcher`() =
        runBlocking {
            val database = FakeStudyDatabasePort()
            // 版本不匹配 → 强制走 归档 + 全量重放（compute 段必然会执行）。
            database.publishDisplacedProjection(previousVersionSnapshot())
            val log = mutableListOf<String>()
            val drainer = StudyProjectionDrainer(
                database = database,
                learnerId = LEARNER_ID,
                learningProjector = LearningProjector(),
                clock = fixedClock,
                computeDispatcher = RecordingDispatcher("compute", log),
                databaseDispatcher = RecordingDispatcher("database", log),
            )

            drainer.drain()

            assertTrue("数据库访问必须显式走注入的 IO 调度器：$log", log.contains("database"))
            assertTrue("投影重放必须显式走注入的计算调度器：$log", log.contains("compute"))
            assertTrue(
                "顺序应为先读库、再计算、最后提交（都在显式调度器上）：$log",
                log.indexOf("database") < log.indexOf("compute") &&
                    log.indexOf("compute") < log.lastIndexOf("database"),
            )
        }

    @Test
    fun `a drain call reads the current snapshot once and reuses it across batches`() = runBlocking {
        val database = FakeStudyDatabasePort()
        database.publishDisplacedProjection(previousVersionSnapshot())
        val drainer = StudyProjectionDrainer(
            database = database,
            learnerId = LEARNER_ID,
            learningProjector = LearningProjector(),
            clock = fixedClock,
            computeDispatcher = Dispatchers.Unconfined,
            databaseDispatcher = Dispatchers.Unconfined,
        )

        val drained = requireNotNull(drainer.drain())

        assertEquals(
            "S4：一次 drain 调用内只读一次当前快照（提交结果复用，不再每步重读）",
            1,
            database.projectionSnapshotReads,
        )
        assertEquals(LearningProjector.VERSION, drained.snapshot.checkpoint.projectorVersion)
    }

    private fun previousVersionSnapshot() = LearnerSnapshot(
        learnerId = LEARNER_ID,
        problemMemoryStates = mapOf(
            "unit-previous-projector-version" to ProblemMemoryState(
                practiceUnitId = "unit-previous-projector-version",
                stabilityDays = 2.0,
                difficulty = 9.0,
                lastReviewedAtEpochMillis = 10L * 86_400_000L,
                nextReviewAtEpochMillis = 12L * 86_400_000L,
                lastAttemptId = "attempt:previous-version",
                projectorVersion = PREVIOUS_VERSION,
                checkpointSequence = 1,
            ),
        ),
        checkpoint = ProjectionCheckpoint(
            lastSequence = 1,
            projectorVersion = PREVIOUS_VERSION,
            projectedAtEpochMillis = 10L * 86_400_000L,
        ),
        knownLedgerHeadSequence = 1,
        generatedAtEpochMillis = 10L * 86_400_000L,
    )

    private class RecordingDispatcher(
        private val name: String,
        private val log: MutableList<String>,
    ) : CoroutineDispatcher() {
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            log += name
            // 记录后交给共享调度器执行（Default 是守护线程池，测试结束不需要额外清理）。
            Dispatchers.Default.dispatch(context, block)
        }
    }

    private companion object {
        const val LEARNER_ID = "learner:local"
        const val PREVIOUS_VERSION = "learning-core-v6(projector-v6,evidence-v4)"
        val fixedClock: Clock =
            Clock.fixed(Instant.parse("2026-01-02T08:00:00Z"), ZoneId.of("Asia/Shanghai"))
    }
}
