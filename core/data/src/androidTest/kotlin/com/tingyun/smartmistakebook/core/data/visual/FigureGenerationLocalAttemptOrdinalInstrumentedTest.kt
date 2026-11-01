package com.tingyun.smartmistakebook.core.data.visual

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.database.StudyDatabaseFactory
import com.tingyun.smartmistakebook.core.database.port.BeginFigureGenerationAttemptCommand
import com.tingyun.smartmistakebook.core.database.port.BeginFigureGenerationOperationCommand
import com.tingyun.smartmistakebook.core.database.port.BeginLocalFigureGenerationAttemptCommand
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 本地方法（JS）attempt 的负序号分配（真库 / 真 DAO）。
 *
 * **它消灭的具体失败**：`insertLocalAttempt` 的序号由持久层在同一个 `@Transaction` 里
 * `MIN(attempt_ordinal)-1` 分配；如果这条路径只有 fake（硬编码 -1），那么"本地先占正数
 * 序号、远程账本下一次拿到同一序号、`insertAttempt` ABORT、recorder 吞异常丢审计行"
 * 的缺陷可以在全绿测试下回来。本用例直接经 [StudyDatabaseFactory] 打开真库、经
 * `FigureGenerationRecorderPort` 建行，钉住：
 * - 同一 operation 的两次本地尝试得到 **-1、-2**（不同 attempt_id，`reservation_id` NULL）；
 * - 远程尝试先占 `1` 后，本地仍从 `-1` 起——两条序列不相交；
 * - 远程 `1` 的重复写入仍被唯一索引拒绝（本地没有"吃掉"正数区间）；
 * - 远程 `2` 仍可写入（对账/重试路径不被本地尝试挤掉）。
 */
@RunWith(AndroidJUnit4::class)
class FigureGenerationLocalAttemptOrdinalInstrumentedTest {

    @Test
    fun localAttemptsUseNegativeOrdinalsDisjointFromTheRemoteLedgerSequence() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "figure-local-ordinal-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        try {
            val database = StudyDatabaseFactory.open(context, databaseName)
            val operationId = "op-local-ordinal"
            assertTrue(
                "操作行必须先落库（attempt 有 RESTRICT 外键）",
                database.beginFigureGenerationOperation(
                    BeginFigureGenerationOperationCommand(
                        operationId = operationId,
                        learnerId = "learner:test",
                        sourceKind = "TUTOR_MESSAGE",
                        sourceId = "message-$operationId",
                        basisFingerprint = "a".repeat(64),
                        purpose = "EXPLANATION",
                        policyVersion = "figure-generation-policy-v1",
                        status = "RUNNING",
                        createdAtEpochMillis = 1_000,
                    ),
                ),
            )
            // 账本先给一次远程尝试发号 1（模拟 dispatchCount=1）。
            assertTrue(
                database.beginFigureGenerationAttempt(
                    remoteAttempt(
                        attemptId = "attempt-remote-1",
                        operationId = operationId,
                        ordinal = 1,
                    ),
                ),
            )

            val first = database.beginLocalFigureGenerationAttempt(
                localAttempt(operationId, "c".repeat(64), startedAtEpochMillis = 2_000),
            )
            val second = database.beginLocalFigureGenerationAttempt(
                localAttempt(operationId, "d".repeat(64), startedAtEpochMillis = 3_000),
            )
            requireNotNull(first) { "本地 attempt 必须落库" }
            requireNotNull(second) { "第二次本地 attempt 必须落库" }

            assertEquals("本地序号从 -1 起", -1, first.attemptOrdinal)
            assertEquals("同一操作的下一次本地尝试必须是 -2", -2, second.attemptOrdinal)
            assertNotEquals("两次本地尝试必须是不同身份", first.attemptId, second.attemptId)
            assertEquals(
                "attempt_id 与序号同源（含 method）",
                "figure-attempt:$operationId:-1:JS",
                first.attemptId,
            )

            // 远程下一个序号 2 仍可落：本地尝试没有占用正数区间。
            assertTrue(
                database.beginFigureGenerationAttempt(
                    remoteAttempt(
                        attemptId = "attempt-remote-2",
                        operationId = operationId,
                        ordinal = 2,
                    ),
                ),
            )

            // 远程序号 1 的重复写入必须 ABORT：本地 -1 不是"同一个 1"。
            var duplicateRejected = false
            try {
                database.beginFigureGenerationAttempt(
                    remoteAttempt(
                        attemptId = "attempt-remote-1-retry",
                        operationId = operationId,
                        ordinal = 1,
                    ),
                )
            } catch (_: Exception) {
                duplicateRejected = true
            }
            assertTrue(
                "远程序号 1 的重复写入必须撞唯一索引（本地没有吃掉正数区间）",
                duplicateRejected,
            )
            database.close()

            openForRead(context, databaseName).use { db ->
                assertEquals(
                    "四次成功写入（远程 1/2 + 本地 -1/-2）",
                    4,
                    db.count("SELECT COUNT(*) FROM figure_generation_attempt"),
                )
                db.rawQuery(
                    """
                    SELECT attempt_ordinal, method, reservation_id
                    FROM figure_generation_attempt
                    WHERE operation_id = ?
                    ORDER BY attempt_ordinal ASC
                    """.trimIndent(),
                    arrayOf(operationId),
                ).use { cursor ->
                    val rows = buildList {
                        while (cursor.moveToNext()) {
                            add(
                                Triple(
                                    cursor.getInt(0),
                                    cursor.getString(1),
                                    if (cursor.isNull(2)) null else cursor.getString(2),
                                ),
                            )
                        }
                    }
                    assertEquals(
                        "顺序必须是 -2、-1、1、2",
                        listOf(-2, -1, 1, 2),
                        rows.map { it.first },
                    )
                    rows.filter { it.first < 0 }.forEach { row ->
                        assertEquals("本地尝试 method=JS", "JS", row.second)
                        assertNull("本地尝试没有远程预留", row.third)
                    }
                    rows.filter { it.first > 0 }.forEach { row ->
                        assertEquals("远程尝试 method=IMAGE_MODEL", "IMAGE_MODEL", row.second)
                    }
                }
            }
        } finally {
            context.deleteDatabase(databaseName)
        }
    }

    private fun remoteAttempt(
        attemptId: String,
        operationId: String,
        ordinal: Int,
    ) = BeginFigureGenerationAttemptCommand(
        attemptId = attemptId,
        operationId = operationId,
        attemptOrdinal = ordinal,
        method = "IMAGE_MODEL",
        status = "RUNNING",
        sourceSha256 = null,
        inputFingerprint = "b".repeat(64),
        reservationId = "reservation-$ordinal",
        startedAtEpochMillis = 1_000,
    )

    private fun localAttempt(
        operationId: String,
        inputFingerprint: String,
        startedAtEpochMillis: Long,
    ) = BeginLocalFigureGenerationAttemptCommand(
        operationId = operationId,
        method = "JS",
        status = "RUNNING",
        sourceSha256 = null,
        inputFingerprint = inputFingerprint,
        startedAtEpochMillis = startedAtEpochMillis,
    )

    private fun openForRead(context: Context, databaseName: String): SQLiteDatabase =
        SQLiteDatabase.openDatabase(
            context.getDatabasePath(databaseName).absolutePath,
            null,
            SQLiteDatabase.OPEN_READWRITE,
        )

    private fun SQLiteDatabase.count(sql: String): Int =
        rawQuery(sql, null).use { cursor ->
            check(cursor.moveToFirst()) { "count query returned no row: $sql" }
            cursor.getInt(0)
        }
}
