package com.tingyun.smartmistakebook.core.data.study

import com.tingyun.smartmistakebook.core.database.ReviewLogEntry
import com.tingyun.smartmistakebook.core.database.StudyDatabasePort
import com.tingyun.smartmistakebook.core.domain.ExamCalendarEntry
import com.tingyun.smartmistakebook.core.domain.FsrsParameterOptimizer
import com.tingyun.smartmistakebook.core.domain.FsrsScheduleMath
import com.tingyun.smartmistakebook.core.domain.HLRPredictionAuditService
import com.tingyun.smartmistakebook.core.domain.LearningProjector
import com.tingyun.smartmistakebook.core.domain.SchedulingOptions
import com.tingyun.smartmistakebook.core.domain.SchedulingSettingsStore
import com.tingyun.smartmistakebook.core.model.LearnerSnapshot
import com.tingyun.smartmistakebook.core.model.ProjectionCheckpoint
import java.time.Clock
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * W2-2/KF-04+05 的采纳门：FSRS 参数拟合的 400 硬门与"不劣于存量才写入"的写入门。
 *
 * 存量参数是学习者的资产——门误开会让每次启动随机游走，门误关会让拟合永远不生效，
 * 拒绝路径误传 null 会把存量直接清空（DataStore set(null) 的语义就是清除）。
 */
class StudySchedulingCalibrationTest {

    private val learnerId = "learner:gate"
    private val zone = ZoneId.of("Asia/Shanghai")
    private val clock = Clock.fixed(java.time.Instant.parse("2026-01-20T08:00:00Z"), zone)

    @Test
    fun `adoption gate refuses to fit below the 400 predictable sample floor`() = runBlocking {
        val database = FakeStudyDatabasePort()
        val store = FakeSchedulingSettingsStore()
        val calibration = calibration(database, store)
        seedGateFixture(database, cardCount = 399)

        assertNull(calibration.optimizeSchedulingParameters())
        assertNull("门下的拟合不许写库", store.optimized)
    }

    @Test
    fun `empty store adopts the candidate once the gate passes`() = runBlocking {
        val database = FakeStudyDatabasePort()
        val store = FakeSchedulingSettingsStore()
        val calibration = calibration(database, store)
        seedGateFixture(database, cardCount = 450)

        val result = calibration.optimizeSchedulingParameters()

        assertNotNull(result)
        assertEquals(FsrsParameterOptimizer.Mode.FULL_FIT, result!!.mode)
        assertEquals(450, result.sampleCount)
        assertSame("候选参数必须原样写入存量", result.parameters, store.optimized)
    }

    @Test
    fun `three consecutive optimizations are stable instead of random walking`() = runBlocking {
        val database = FakeStudyDatabasePort()
        val store = FakeSchedulingSettingsStore()
        val calibration = calibration(database, store)
        seedGateFixture(database, cardCount = 450)

        val first = calibration.optimizeSchedulingParameters()
        val second = calibration.optimizeSchedulingParameters()
        val third = calibration.optimizeSchedulingParameters()

        assertNotNull(first)
        // 优化器无随机源、数据不变 ⇒ 三次结果的参数逐位一致（KF-04 的"三次启动稳定"）。
        assertEquals(first!!.parameters.toList(), second!!.parameters.toList())
        assertEquals(second.parameters.toList(), third!!.parameters.toList())
        assertEquals(first.parameters.toList(), store.optimized!!.toList())
    }

    @Test
    fun `adoption decision covers every loss combination`() {
        val calibration = calibration(FakeStudyDatabasePort(), FakeSchedulingSettingsStore())
        val stored = doubleArrayOf(0.1542)

        // 候选不可测（无验证对）→ 拒绝：没有同协议证据不许覆盖存量。
        assertFalse(calibration.shouldAdopt(null, stored, 0.6))
        // 存量为空 → 首次写入。
        assertTrue(calibration.shouldAdopt(0.6, null, null))
        // 存量在但不可测 → 保守保留。
        assertFalse(calibration.shouldAdopt(0.6, stored, null))
        // 都可测：不劣于（≤）才写。
        assertTrue(calibration.shouldAdopt(0.5, stored, 0.6))
        assertFalse(calibration.shouldAdopt(0.6, stored, 0.5))
        assertTrue(calibration.shouldAdopt(0.6, stored, 0.6))
    }

    private fun calibration(
        database: StudyDatabasePort,
        store: FakeSchedulingSettingsStore,
    ): StudySchedulingCalibration = StudySchedulingCalibration(
        database = database,
        learnerId = learnerId,
        reviewLogSink = ReviewLogSink(database, learnerId, clock, zone),
        predictionAuditService = HLRPredictionAuditService(),
        schedulingSettingsStore = store,
        clock = clock,
        learnerSnapshot = {
            LearnerSnapshot(
                learnerId = learnerId,
                checkpoint = ProjectionCheckpoint(
                    lastSequence = 0,
                    projectorVersion = LearningProjector.VERSION,
                    projectedAtEpochMillis = 0,
                ),
                generatedAtEpochMillis = 0,
            )
        },
    )

    /** 每卡两行、间隔 2 天：可预测样本数 = 卡数（W2-2 门的计数口径）。 */
    private suspend fun seedGateFixture(database: StudyDatabasePort, cardCount: Int) {
        val entries = (0 until cardCount).flatMap { card ->
            listOf(
                entry("unit-$card", at = DAY * card, rating = 3, delta = 0.0, index = 0),
                entry("unit-$card", at = DAY * (card + 2), rating = 3, delta = 2.0, index = 1),
            )
        }
        database.recordReviewLogEntries(entries)
    }

    private fun entry(
        unitId: String,
        at: Long,
        rating: Int,
        delta: Double,
        index: Int,
    ) = ReviewLogEntry(
        learnerId = learnerId,
        practiceUnitId = unitId,
        rating = rating,
        deltaTDays = delta,
        durationMs = 0,
        reviewedAtEpochMillis = at,
        sourceKind = ReviewLogSink.SOURCE_KIND_ATTEMPT,
        sourceId = "$unitId-attempt-$index",
        evidenceWeight = 1.0,
        timeBucket = "MORNING",
        state = com.tingyun.smartmistakebook.core.domain.ReviewSample.STATE_REVIEW,
        recordedAtEpochMillis = at,
    )

    private class FakeSchedulingSettingsStore : SchedulingSettingsStore {
        var optimized: DoubleArray? = null

        override val options: Flow<SchedulingOptions> = flowOf(SchedulingOptions())
        override suspend fun setOptions(options: SchedulingOptions) = Unit
        override val exams: Flow<List<ExamCalendarEntry>> = flowOf(emptyList())
        override suspend fun addExam(entry: ExamCalendarEntry) = Unit
        override suspend fun removeExam(entryId: String) = Unit
        override val optimizedParameters: Flow<DoubleArray?>
            get() = flowOf(optimized)
        override suspend fun setOptimizedParameters(parameters: DoubleArray?) {
            optimized = parameters
        }
    }

    private companion object {
        const val DAY = 86_400_000L
    }
}
