package com.tingyun.smartmistakebook.core.data.study

import com.tingyun.smartmistakebook.core.domain.ReviewSample
import com.tingyun.smartmistakebook.core.domain.StudyDayMath
import com.tingyun.smartmistakebook.core.model.LearningEvidence
import com.tingyun.smartmistakebook.core.model.LearningEvidenceDirection
import com.tingyun.smartmistakebook.core.model.LearningEvidenceReason
import com.tingyun.smartmistakebook.core.model.StudyDayContext
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * W2-4/KF-23 的**写侧** `review_log.state` 派生：与迁移回填同一口径
 * （无前条=New、前条 AGAIN=Relearning、同一学习日=Learning、跨学习日=Review）。
 *
 * 迁移回填的真库结果由仪器化用例覆盖；这里钉住写入路径的判定表，防止两条口径漂开。
 */
class ReviewLogStateTest {

    private val learnerId = "learner:state"
    private val zone = ZoneId.of("Asia/Shanghai")
    private val clock = Clock.fixed(Instant.parse("2026-01-20T08:00:00Z"), zone)
    private val t0 = Instant.parse("2026-01-10T00:00:00Z").toEpochMilli()

    @Test
    fun `state follows the previous row rating and the study day gap`() = runBlocking {
        val database = FakeStudyDatabasePort()
        val sink = ReviewLogSink(database, learnerId, clock, zone)

        record(sink, at = t0, correct = true)
        assertEquals("首条 = New", ReviewSample.STATE_NEW, lastState(database))

        record(sink, at = t0 + 3_600_000, correct = true)
        assertEquals("同一学习日重复 = Learning", ReviewSample.STATE_LEARNING, lastState(database))

        record(sink, at = t0 + DAY, correct = true)
        assertEquals("跨学习日 = Review", ReviewSample.STATE_REVIEW, lastState(database))

        record(sink, at = t0 + 2 * DAY, correct = false)
        assertEquals("前条为答对、跨日 → 仍是 Review", ReviewSample.STATE_REVIEW, lastState(database))

        record(sink, at = t0 + 2 * DAY + 60_000, correct = true)
        assertEquals(
            "前条 AGAIN → Relearning（优先级高于同日判定，与迁移回填一致）",
            ReviewSample.STATE_RELEARNING,
            lastState(database),
        )
    }

    private suspend fun record(
        sink: ReviewLogSink,
        at: Long,
        correct: Boolean,
    ) {
        sink.record(
            practiceUnitId = "unit-state",
            evidence = if (correct) {
                LearningEvidence(
                    direction = LearningEvidenceDirection.POSITIVE,
                    weight = 1.0,
                    reason = LearningEvidenceReason.INDEPENDENT_CORRECT,
                )
            } else {
                LearningEvidence(
                    direction = LearningEvidenceDirection.NEGATIVE,
                    weight = 1.0,
                    reason = LearningEvidenceReason.INDEPENDENT_INCORRECT,
                )
            },
            occurredAtEpochMillis = at,
            durationSeconds = 10,
            // 与生产写路径同源：学习日由时间戳 + 偏移现算（04:00 日界）。
            studyDay = StudyDayContext(
                epochDay = StudyDayMath.localEpochDayOf(at, utcOffsetMinutes = 480),
                timeZoneId = zone.id,
                utcOffsetMinutes = 480,
            ),
            sourceKind = ReviewLogSink.SOURCE_KIND_ATTEMPT,
            sourceId = "attempt-$at",
            priorMemory = null,
        )
    }

    private fun lastState(database: FakeStudyDatabasePort): Int =
        database.reviewLogEntries.last().state

    private companion object {
        const val DAY = 86_400_000L
    }
}
