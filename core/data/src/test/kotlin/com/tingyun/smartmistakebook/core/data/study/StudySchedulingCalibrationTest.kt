package com.tingyun.smartmistakebook.core.data.study

import com.tingyun.smartmistakebook.core.database.ReviewLogEntry
import com.tingyun.smartmistakebook.core.database.StudyDatabasePort
import com.tingyun.smartmistakebook.core.domain.ExamCalendarEntry
import com.tingyun.smartmistakebook.core.domain.FsrsParameterFit
import com.tingyun.smartmistakebook.core.domain.FsrsParameterOptimizer
import com.tingyun.smartmistakebook.core.domain.FsrsScheduleMath
import com.tingyun.smartmistakebook.core.domain.HLRPredictionAuditService
import com.tingyun.smartmistakebook.core.domain.LearningProjector
import com.tingyun.smartmistakebook.core.domain.SchedulingCalibrationView
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
    fun `adoption records when how many samples and the validation loss against the default baseline`() = runBlocking {
        val database = FakeStudyDatabasePort()
        val store = FakeSchedulingSettingsStore()
        val calibration = calibration(database, store)
        seedGateFixture(database, cardCount = 450)

        val result = requireNotNull(calibration.optimizeSchedulingParameters())
        val fit = requireNotNull(store.fit)

        assertEquals("拟合时点用注入 Clock，不读系统时钟", clock.millis(), fit.adoptedAtEpochMillis)
        assertEquals(450, fit.sampleCount)
        assertEquals(result.validationLogLoss, fit.validationLogLoss, 0.0)
        assertNotNull("默认参数基线必须能在同一协议下测出", fit.baselineValidationLogLoss)
        assertTrue(fit.baselineValidationLogLoss!!.isFinite())
    }

    @Test
    fun `a rejected fit keeps the previous fit record intact`() = runBlocking {
        val database = FakeStudyDatabasePort()
        val store = FakeSchedulingSettingsStore()
        val calibration = calibration(database, store)
        val previous = FsrsParameterFit(
            adoptedAtEpochMillis = 123L,
            sampleCount = 400,
            validationLogLoss = 0.5,
            baselineValidationLogLoss = 0.6,
        )
        store.fit = previous
        seedGateFixture(database, cardCount = 399)

        assertNull(calibration.optimizeSchedulingParameters())
        assertSame("拒绝路径不写参数，也不改拟合记录", previous, store.fit)
    }

    @Test
    fun `the calibration view reports stored parameters the fit record and predictable samples`() = runBlocking {
        val database = FakeStudyDatabasePort()
        val store = FakeSchedulingSettingsStore()
        val calibration = calibration(database, store)
        seedGateFixture(database, cardCount = 450)

        val result = requireNotNull(calibration.optimizeSchedulingParameters())
        val view = calibration.schedulingCalibrationView()

        assertEquals(SchedulingCalibrationView.ParameterSource.LOCAL_FIT, view.parameterSource)
        assertEquals("页面展示的必须是实际生效的那组参数", result.parameters.toList(), view.parameters)
        assertEquals(store.fit, view.lastFit)
        assertEquals(450, view.predictableSampleCount)
        assertEquals(21, view.parameterCount)
        assertEquals(FsrsParameterOptimizer.MIN_SAMPLES_FOR_FITTING, view.fitSampleFloor)
        assertNotNull("有验证对时页面要给出当前参数损失", view.currentValidationLogLoss)
        assertNotNull("有验证对时页面要给出默认基线损失", view.baselineValidationLogLoss)
        assertTrue("夹具没有计划理由 → 按理由表为空", view.perReason.isEmpty())
    }

    @Test
    fun `the calibration view reports imported parameters when a fit record is absent`() = runBlocking {
        val database = FakeStudyDatabasePort()
        val store = FakeSchedulingSettingsStore()
        val calibration = calibration(database, store)
        seedGateFixture(database, cardCount = 3)
        store.optimized = FsrsScheduleMath.DEFAULT_PARAMETERS.copyOf().also { it[4] = it[4] + 0.5 }

        val view = calibration.schedulingCalibrationView()

        assertEquals(SchedulingCalibrationView.ParameterSource.IMPORTED, view.parameterSource)
        assertNull(view.lastFit)
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
        var fit: FsrsParameterFit? = null

        override val options: Flow<SchedulingOptions> = flowOf(SchedulingOptions())
        override suspend fun setOptions(options: SchedulingOptions) = Unit
        override val exams: Flow<List<ExamCalendarEntry>> = flowOf(emptyList())
        override suspend fun addExam(entry: ExamCalendarEntry) = Unit
        override suspend fun removeExam(entryId: String) = Unit
        override val optimizedParameters: Flow<DoubleArray?>
            get() = flowOf(optimized)
        override val parameterFit: Flow<FsrsParameterFit?>
            get() = flowOf(fit)
        override suspend fun recordParameterFit(fit: FsrsParameterFit?) {
            this.fit = fit
        }
        override suspend fun setOptimizedParameters(parameters: DoubleArray?) {
            optimized = parameters
            // 与 DataStore 实现同语义：参数换了，旧拟合记录不再描述当前参数。
            fit = null
        }
    }

    private companion object {
        const val DAY = 86_400_000L
    }
}
