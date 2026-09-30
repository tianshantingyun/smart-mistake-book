package com.tingyun.smartmistakebook.core.database

import android.content.Context
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * **W2-4/KF-23 的回填实测**（v54→55）：在 v54 的真实库上播种旧形态的 `review_log`
 * （无 state 列），迁移后逐行核对回填口径——无前条=New(0)、前条 AGAIN=Relearning(3)、
 * 同日（delta_t_days=0）=Learning(1)、跨日=Review(2)，且与写入侧派生（`ReviewLogSink`）
 * 逐条同构。JVM 契约测试管 SQL 形状，这里管真库结果。
 */
@RunWith(AndroidJUnit4::class)
class KernelWave2MigrationInstrumentedTest {

    @Test
    fun reviewLogStateIsBackfilledFromPreviousRatingAndDayGap() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "wave2-state-backfill-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        try {
            createDatabaseFromExportedSchema(context, databaseName, version = 54)
            seedLegacyReviewLog(context, databaseName)

            val migrated = StudyDatabaseFactory.open(context, databaseName)
            val states = migrated.readReviewLogSamples(LEARNER, limit = 10).map { it.state }

            assertEquals(
                "回填四态：首条 New / 同日 Learning / 跨日 Review / 同日 Learning / 前条 AGAIN Relearning",
                listOf(0, 1, 2, 1, 3),
                states,
            )
            migrated.close()
        } finally {
            context.deleteDatabase(databaseName)
        }
    }

    /**
     * 播种五条同卡复习（v54 形态：INSERT 不含 state）：
     * 1) 首条 GOOD 2) 同日 GOOD 3) 跨日 GOOD 4) 同日 AGAIN 5) 跨日 GOOD。
     * `delta_t_days` 按当时写入口径给出（与时间戳一致），回填只读既有列。
     */
    private fun seedLegacyReviewLog(context: Context, databaseName: String) {
        val connection = AndroidSQLiteDriver().open(context.getDatabasePath(databaseName).absolutePath)
        try {
            val rows = listOf(
                LegacyRow(at = 0L, rating = 3, deltaDays = 0.0),
                LegacyRow(at = 3_600_000L, rating = 3, deltaDays = 0.0),
                LegacyRow(at = 3 * DAY_MILLIS, rating = 3, deltaDays = 3.0),
                LegacyRow(at = 3 * DAY_MILLIS + 3_600_000L, rating = 1, deltaDays = 0.0),
                LegacyRow(at = 5 * DAY_MILLIS, rating = 3, deltaDays = 2.0),
            )
            rows.forEachIndexed { index, row ->
                connection.execSQL(
                    """
                    INSERT INTO `review_log` (
                        `learner_id`, `card_id`, `rating`, `delta_t_days`, `duration_ms`,
                        `reviewed_at_utc`, `source_kind`, `source_id`, `evidence_weight`,
                        `scheduling_eligible`, `time_bucket`, `scroll_up_count`, `edit_count`,
                        `interruption_count`, `away_millis`, `planned_reason`, `recorded_at`
                    ) VALUES (
                        '$LEARNER', 'unit-legacy', ${row.rating}, ${row.deltaDays}, 1000,
                        ${row.at}, 'ATTEMPT', 'legacy-$index', 1.0,
                        1, 'MORNING', 0, 0,
                        0, 0, NULL, ${row.at}
                    )
                    """.trimIndent(),
                )
            }
        } finally {
            connection.close()
        }
    }

    private data class LegacyRow(val at: Long, val rating: Int, val deltaDays: Double)

    private companion object {
        const val LEARNER = "learner:wave2-backfill"
        const val DAY_MILLIS = 86_400_000L
    }
}
