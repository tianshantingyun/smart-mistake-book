package com.tingyun.smartmistakebook.core.data.study

import com.tingyun.smartmistakebook.core.domain.FsrsEvidenceRatingMapper
import com.tingyun.smartmistakebook.core.domain.FsrsRating
import com.tingyun.smartmistakebook.core.domain.ReviewSample
import com.tingyun.smartmistakebook.core.domain.ReviewSelfReportKey
import com.tingyun.smartmistakebook.core.model.LearningEvidenceDirection
import com.tingyun.smartmistakebook.core.model.LearningEvidenceReason
import com.tingyun.smartmistakebook.core.model.ProblemMemoryOutcome
import com.tingyun.smartmistakebook.core.model.StudyDayContext
import com.tingyun.smartmistakebook.core.model.StudyDayMath
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PRD-05 三键自评的**数据合同**（裁定 32/36；设计 §3.4）。
 *
 * 钉住四件事：
 * 1. 三键只有 GOOD/HARD/AGAIN，没有 Easy；
 * 2. 证据 reason 只能是 `SELF_REPORTED_RECALL` / `SELF_REPORTED_STUCK`，不冒充
 *    LOCAL_CHECKED/MODEL_JUDGED/INDEPENDENT；
 * 3. HARD 是"费力但回忆成功"（POSITIVE + scheduling Hard），不是失败；AGAIN 才是失败；
 * 4. 原档与调度档分列：0.8/0.7/1.0 逐位落 review_log.evidence_weight，映射为 Good/Hard/Again。
 */
class ReviewSelfReportContractTest {

    @Test
    fun theThreeKeysAreGoodHardAndAgainWithoutEasy() {
        assertEquals(
            listOf(ReviewSelfReportKey.GOOD, ReviewSelfReportKey.HARD, ReviewSelfReportKey.AGAIN),
            ReviewSelfReportKey.entries,
        )
    }

    @Test
    fun goodIsPositiveSelfReportedRecallCappedAtGood() {
        val evidence = SelfReportedReviewEvidence.evidenceFor(ReviewSelfReportKey.GOOD)

        assertEquals(LearningEvidenceDirection.POSITIVE, evidence.direction)
        assertEquals(0.8, evidence.weight, 0.0)
        assertEquals(LearningEvidenceReason.SELF_REPORTED_RECALL, evidence.reason)
        assertFalse("自评不能成为独立正确", evidence.isIndependent)
        assertEquals(
            ProblemMemoryOutcome.ASSISTED_RECALL,
            SelfReportedReviewEvidence.memoryOutcomeFor(ReviewSelfReportKey.GOOD),
        )
        assertEquals(FsrsRating.GOOD, schedulingRating(evidence.reason, evidence.weight))
        assertNotEquals(FsrsRating.EASY, schedulingRating(evidence.reason, evidence.weight))
    }

    @Test
    fun hardStaysAStruggleButStillASuccessAndIsNotLiftedToGood() {
        val evidence = SelfReportedReviewEvidence.evidenceFor(ReviewSelfReportKey.HARD)

        assertEquals("卡顿 = 费力但想起来了：方向仍是正性", LearningEvidenceDirection.POSITIVE, evidence.direction)
        assertEquals("原档 0.7 逐位保留，不接受折扣", 0.7, evidence.weight, 0.0)
        assertEquals(LearningEvidenceReason.SELF_REPORTED_RECALL, evidence.reason)
        assertEquals(
            ProblemMemoryOutcome.ASSISTED_RECALL,
            SelfReportedReviewEvidence.memoryOutcomeFor(ReviewSelfReportKey.HARD),
        )
        assertEquals(
            "硬→Hard：若被折扣到 0.665 会掉成 Good，这是要被消灭的静默抬档",
            FsrsRating.HARD,
            schedulingRating(evidence.reason, evidence.weight),
        )
    }

    @Test
    fun againIsTheOnlyFailureAndMustNotBeAbsorbedByHard() {
        val evidence = SelfReportedReviewEvidence.evidenceFor(ReviewSelfReportKey.AGAIN)

        assertEquals(LearningEvidenceDirection.NEGATIVE, evidence.direction)
        assertEquals(1.0, evidence.weight, 0.0)
        assertEquals(LearningEvidenceReason.SELF_REPORTED_STUCK, evidence.reason)
        assertFalse(evidence.isIndependent)
        assertEquals(
            ProblemMemoryOutcome.RETRIEVAL_FAILURE,
            SelfReportedReviewEvidence.memoryOutcomeFor(ReviewSelfReportKey.AGAIN),
        )
        assertEquals(FsrsRating.AGAIN, schedulingRating(evidence.reason, evidence.weight))
        assertNotEquals(
            "错不能映射成 Hard（Anki 的 Hard 误用会把失败高估为成功回忆）",
            FsrsRating.HARD,
            schedulingRating(evidence.reason, evidence.weight),
        )
    }

    @Test
    fun selfReportReasonsNeverMasqueradeAsMachineCheckedEvidence() {
        ReviewSelfReportKey.entries.forEach { key ->
            val evidence = SelfReportedReviewEvidence.evidenceFor(key)
            assertNotEquals(LearningEvidenceReason.INDEPENDENT_CORRECT, evidence.reason)
            assertNotEquals(LearningEvidenceReason.INDEPENDENT_INCORRECT, evidence.reason)
            assertNotEquals(LearningEvidenceReason.MODEL_JUDGED_CORRECT, evidence.reason)
            assertNotEquals(LearningEvidenceReason.MODEL_JUDGED_INCORRECT, evidence.reason)
            assertFalse(evidence.isIndependent)
        }
    }

    /**
     * 原档与调度档分列：三键不参与注意力/时长折扣，`review_log.evidence_weight` 逐位保留
     * 0.8/0.7/1.0，`review_log.rating` 单独写实际调度档 Good/Hard/Again。
     *
     * 这一条消灭的失败：0.7 若被折扣成 0.665，`reportedRatingFor` 会落进 else→Good，
     * 把"卡顿"静默抬成"对"（Anki 官方警告的 Hard 误用/高估记忆）。
     */
    @Test
    fun reviewLogWritesTheReportedKeyVerbatimAndTheSchedulingRatingSeparately() = runBlocking {
        val database = FakeStudyDatabasePort()
        val zone = ZoneId.of("Asia/Shanghai")
        val clock = Clock.fixed(Instant.parse("2026-01-20T08:00:00Z"), zone)
        val sink = ReviewLogSink(database, LEARNER_ID, clock, zone)
        val start = Instant.parse("2026-01-20T01:00:00Z").toEpochMilli()
        val studyDay = StudyDayContext(
            epochDay = StudyDayMath.localEpochDayOf(start, utcOffsetMinutes = 480),
            timeZoneId = zone.id,
            utcOffsetMinutes = 480,
        )
        val expected = listOf(
            ReviewSelfReportKey.GOOD to FsrsRating.GOOD,
            ReviewSelfReportKey.HARD to FsrsRating.HARD,
            ReviewSelfReportKey.AGAIN to FsrsRating.AGAIN,
        )

        expected.forEachIndexed { index, (key, rating) ->
            val evidence = SelfReportedReviewEvidence.evidenceFor(key)
            sink.record(
                practiceUnitId = UNIT_ID,
                evidence = evidence,
                occurredAtEpochMillis = start + index * 1_000L,
                durationSeconds = 30,
                studyDay = studyDay,
                sourceKind = ReviewLogSink.SOURCE_KIND_SELF_REPORT,
                sourceId = "self-report-attempt-$index",
                priorMemory = null,
            )
            val row = database.reviewLogEntries.last()
            assertEquals("原始档逐位落 evidence_weight", evidence.weight, row.evidenceWeight, 0.0)
            assertEquals("调度档单独落 review_log.rating", rating.storedRating, row.rating)
            assertTrue(row.schedulingEligible)
            assertEquals(ReviewLogSink.SOURCE_KIND_SELF_REPORT, row.sourceKind)
        }
    }

    /**
     * 撤销后的"有效证据视图"：VOID 行保留审计但退出日程/统计/训练样本；它也不再是
     * 重评那行的"前条"（否则撤销后重评会被当成同一天第二次回忆）。
     */
    @Test
    fun voidedReviewLogRowLeavesTheEffectiveViewAndStopsBeingThePreviousRow() = runBlocking {
        val database = FakeStudyDatabasePort()
        val zone = ZoneId.of("Asia/Shanghai")
        val clock = Clock.fixed(Instant.parse("2026-01-20T08:00:00Z"), zone)
        val sink = ReviewLogSink(database, LEARNER_ID, clock, zone)
        val start = Instant.parse("2026-01-20T01:00:00Z").toEpochMilli()
        val studyDay = StudyDayContext(
            epochDay = StudyDayMath.localEpochDayOf(start, utcOffsetMinutes = 480),
            timeZoneId = zone.id,
            utcOffsetMinutes = 480,
        )

        sink.record(
            practiceUnitId = UNIT_ID,
            evidence = SelfReportedReviewEvidence.evidenceFor(ReviewSelfReportKey.GOOD),
            occurredAtEpochMillis = start,
            durationSeconds = 30,
            studyDay = studyDay,
            sourceKind = ReviewLogSink.SOURCE_KIND_SELF_REPORT,
            sourceId = "voided-attempt",
            priorMemory = null,
        )
        assertEquals(1, sink.reviewSamples().size)

        // 模拟 Room 撤销事务里的 markReviewLogSchedulingIneligible。
        database.reviewLogEntries[0] = database.reviewLogEntries[0].copy(schedulingEligible = false)
        sink.invalidateCachedProfile()
        assertTrue("被撤销的行退出有效证据视图", sink.reviewSamples().isEmpty())

        sink.record(
            practiceUnitId = UNIT_ID,
            evidence = SelfReportedReviewEvidence.evidenceFor(ReviewSelfReportKey.HARD),
            occurredAtEpochMillis = start + 60_000L,
            durationSeconds = 30,
            studyDay = studyDay,
            sourceKind = ReviewLogSink.SOURCE_KIND_SELF_REPORT,
            sourceId = "reanswer-attempt",
            priorMemory = null,
        )
        assertEquals(
            "被撤销的作答不再是前条：重评按 New 起算",
            ReviewSample.STATE_NEW,
            database.reviewLogEntries.last().state,
        )
        assertEquals(1, sink.reviewSamples().size)
        assertEquals(
            "重评的调度档仍是 Hard",
            FsrsRating.HARD,
            sink.reviewSamples().single().rating,
        )
    }

    private fun schedulingRating(
        reason: LearningEvidenceReason,
        weight: Double,
    ): FsrsRating = FsrsEvidenceRatingMapper.schedulingRatingFor(reason, weight)

    private companion object {
        const val LEARNER_ID = "learner:self-report"
        const val UNIT_ID = "unit:self-report"
    }
}
