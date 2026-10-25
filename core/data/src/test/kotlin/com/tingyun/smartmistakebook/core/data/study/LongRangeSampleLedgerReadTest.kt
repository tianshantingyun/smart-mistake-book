package com.tingyun.smartmistakebook.core.data.study

import com.tingyun.smartmistakebook.core.database.ReviewLogEntry
import com.tingyun.smartmistakebook.core.domain.FsrsParameterOptimizer
import com.tingyun.smartmistakebook.core.domain.LongRangeSampleClassification
import com.tingyun.smartmistakebook.core.domain.ReviewSample
import com.tingyun.smartmistakebook.core.model.LearningEvidence
import com.tingyun.smartmistakebook.core.model.LearningEvidenceDirection
import com.tingyun.smartmistakebook.core.model.LearningEvidenceReason
import com.tingyun.smartmistakebook.core.model.StudyDayContext
import com.tingyun.smartmistakebook.core.model.StudyDayMath
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * AUD-17 台账的**读面**（批次 20 / T9）：`ReviewLogSink.longRangeSampleLedger` 读的是
 * review_log 的**原始面**（含撤销/观察行）。分类判据在域层（`LongRangeSampleLedgerTest`
 * 已钉），这里只证明仓储读口的三件事：
 * 1. 原始面 ≠ 有效证据面——`reviewSamples()` 排除撤销行，台账原始数含它（AUD-17 要求
 *    原始/排除分列，撤销不能在读口就被吞掉）；
 * 2. 写路径盖章的 `state`（`ReviewLogSink.record` 派生）驱动的分类与优化器口径一致；
 * 3. 仓储出口（`StudyExperienceRepository.longRangeSampleLedger`）接通到域层报告。
 */
class LongRangeSampleLedgerReadTest {

    private val learnerId = "learner:ledger"
    private val zone = ZoneId.of("Asia/Shanghai")
    private val clock = Clock.fixed(Instant.parse("2026-01-20T08:00:00Z"), zone)
    private val t0 = Instant.parse("2026-01-10T00:00:00Z").toEpochMilli()

    @Test
    fun `ledger reads the raw face including revoked rows and matches the optimizer criteria`() = runBlocking {
        val database = FakeStudyDatabasePort()
        val sink = ReviewLogSink(database, learnerId, clock, zone)

        // 卡 unit-x 六行：首条 / 同日（写路径盖 state=Learning）/ 长程（state=Review）/
        // 撤销 / 模型判词 / 看答案。
        record(sink, at = t0)
        record(sink, at = t0 + 2 * HOUR)
        record(sink, at = t0 + 3 * DAY)
        record(sink, at = t0 + 4 * DAY, schedulingEligible = false)
        record(sink, at = t0 + 4 * DAY + HOUR, sourceKind = ReviewLogSink.SOURCE_KIND_MODEL_JUDGED)
        record(sink, at = t0 + 4 * DAY + 2 * HOUR, sourceKind = ReviewLogSink.SOURCE_KIND_REVEAL)

        val ledger = sink.longRangeSampleLedger()

        // 原始面：六行全在（撤销行不吞）。
        assertEquals(6, ledger.rawRowCount)
        assertEquals(1, ledger.countOf(LongRangeSampleClassification.FIRST_OF_CARD))
        assertEquals(1, ledger.countOf(LongRangeSampleClassification.SAME_DAY_REPEAT))
        assertEquals(1, ledger.countOf(LongRangeSampleClassification.PREDICTABLE_LONG_RANGE))
        assertEquals(1, ledger.countOf(LongRangeSampleClassification.REVOKED_OR_OBSERVED))
        assertEquals(1, ledger.countOf(LongRangeSampleClassification.MODEL_JUDGED_EXCLUDED))
        assertEquals(1, ledger.countOf(LongRangeSampleClassification.REVEAL_EXCLUDED))

        // 有效证据面：reviewSamples() 只排除撤销行（来源档的排除发生在拟合口径，不在读口）。
        assertEquals(5, sink.reviewSamples().size)
        // 同库同数据下，台账最终列与优化器可预测样本数逐位一致。
        assertEquals(
            FsrsParameterOptimizer.predictableSampleCount(sink.reviewSamples()),
            ledger.predictableLongRangeCount,
        )
        // 台账是只读派生：读它不改变有效证据面。
        assertEquals(5, sink.reviewSamples().size)
    }

    @Test
    fun `repository exposes the read-only ledger outlet`() = runBlocking {
        val database = FakeStudyDatabasePort()
        val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val repository = RoomBackedStudyExperienceRepository(
            database = database,
            applicationScope = applicationScope,
            learnerId = learnerId,
            clock = clock,
            studyZoneId = zone,
        )
        database.recordReviewLogEntries(
            listOf(
                entry(at = t0, state = ReviewSample.STATE_NEW, deltaTDays = 0.0),
                entry(at = t0 + 3 * DAY, state = ReviewSample.STATE_REVIEW, deltaTDays = 3.0),
            ),
        )

        val ledger = repository.longRangeSampleLedger()

        // 出口接通：默认实现返回 null，RoomBacked 覆写返回域层报告（本批不接 UI，
        // Wave X 消费方经此只读出口取数）。
        assertNotNull(ledger)
        val report = ledger!!
        assertEquals(2, report.rawRowCount)
        assertEquals(1, report.countOf(LongRangeSampleClassification.FIRST_OF_CARD))
        assertEquals(1, report.countOf(LongRangeSampleClassification.PREDICTABLE_LONG_RANGE))
        assertEquals(FsrsParameterOptimizer.MIN_SAMPLES_FOR_FITTING, report.gateFloor)
    }

    private suspend fun record(
        sink: ReviewLogSink,
        at: Long,
        schedulingEligible: Boolean = true,
        sourceKind: String = ReviewLogSink.SOURCE_KIND_ATTEMPT,
    ) {
        sink.record(
            practiceUnitId = "unit-x",
            evidence = LearningEvidence(
                direction = LearningEvidenceDirection.POSITIVE,
                weight = 1.0,
                reason = LearningEvidenceReason.INDEPENDENT_CORRECT,
            ),
            occurredAtEpochMillis = at,
            durationSeconds = 10,
            // 与生产写路径同源：学习日由时间戳 + 偏移现算（04:00 日界）。
            studyDay = StudyDayContext(
                epochDay = StudyDayMath.localEpochDayOf(at, utcOffsetMinutes = 480),
                timeZoneId = zone.id,
                utcOffsetMinutes = 480,
            ),
            sourceKind = sourceKind,
            sourceId = "ledger-$at-$sourceKind",
            priorMemory = null,
            schedulingEligible = schedulingEligible,
        )
    }

    private fun entry(at: Long, state: Int, deltaTDays: Double): ReviewLogEntry = ReviewLogEntry(
        learnerId = learnerId,
        practiceUnitId = "unit-x",
        rating = 3,
        deltaTDays = deltaTDays,
        durationMs = 10_000,
        reviewedAtEpochMillis = at,
        sourceKind = ReviewLogSink.SOURCE_KIND_ATTEMPT,
        sourceId = "seed-$at",
        evidenceWeight = 1.0,
        timeBucket = "MORNING",
        state = state,
        recordedAtEpochMillis = at,
    )

    private companion object {
        const val DAY = 86_400_000L
        const val HOUR = 3_600_000L
    }
}
