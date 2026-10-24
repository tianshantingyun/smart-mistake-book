package com.tingyun.smartmistakebook.core.data.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.data.study.seedStudyFacts
import com.tingyun.smartmistakebook.core.database.ErrorBookEntrySeedRecord
import com.tingyun.smartmistakebook.core.database.PracticeUnitSeedRecord
import com.tingyun.smartmistakebook.core.database.ProblemRevisionSeedRecord
import com.tingyun.smartmistakebook.core.database.ProblemSeedRecord
import com.tingyun.smartmistakebook.core.database.StudyDatabaseFactory
import com.tingyun.smartmistakebook.core.database.StudyDatabasePort
import com.tingyun.smartmistakebook.core.database.StudySeedBundle
import com.tingyun.smartmistakebook.core.domain.BackupValidation
import com.tingyun.smartmistakebook.core.domain.BackupWriteGate
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Collections
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * KD-61（批次 19 T3）：关库前**硬性 quiesce** 钩子的窗口位置与失败语义。
 *
 * 1. **窗口内**：钩子必须运行在「排空接门写者（beginCommit）之后、连接池 close() 之前」
 *    ——太早（beginCommit 前）它挡不住交换段里新到的 collector 写；太晚（close 后）
 *    它自己就撞已关闭的连接池。可观测判据：notify(正在完成) → quiesce → port:close，
 *    且 quiesce 执行瞬间写门尚未终态。
 * 2. **安全失败点**：钩子抛异常必须中止恢复且**不关库**——交换尚未开始，catch 走
 *    「尚未 swap，清临时件」分支，原数据不动、写门回 Idle、端口仍可读。若这条不成立，
 *    "quiesce 失败"就会变成"半关库"这种最坏状态。
 */
@RunWith(AndroidJUnit4::class)
class RestoreBackgroundQuiesceInstrumentedTest {

    @Test
    fun backgroundQuiesceRunsInsideTheCloseWindowBeforeThePoolCloses() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = StudyDatabaseFactory.DEFAULT_DATABASE_NAME
        context.deleteDatabase(databaseName)
        val events = Collections.synchronizedList(mutableListOf<String>())
        val gate = BackupWriteGate(
            maxWriteQueueWaitMillis = 5_000L,
            drainTimeoutMillis = 5_000L,
        )
        val database = StudyDatabaseFactory.open(context, databaseName)
        val recordingPort = RecordingPort(database, events)
        val repository = AndroidBackupRepository(
            context = context,
            database = recordingPort,
            writeGate = gate,
            restartNotifier = { message -> events += "notify:$message" },
            observerQuiesceMillis = 0L,
            backgroundQuiesce = {
                assertFalse(
                    "quiesce 必须在写门终态之前执行（否则它自己已撞关闭的连接池）",
                    gate.databaseClosedForRestart.value,
                )
                events += "quiesce"
            },
        )
        try {
            seedStudyFacts(context, database, databaseName, seed())
            val archiveBytes = ByteArrayOutputStream().also { output ->
                repository.create(output)
            }.toByteArray()

            val validation = repository.validate(ByteArrayInputStream(archiveBytes))
            assertTrue("夹具必须产出可通过校验的归档", validation is BackupValidation.Valid)

            val receipt = repository.restore(ByteArrayInputStream(archiveBytes))
            assertTrue("恢复必须要求重启（连接池已关闭）", receipt.restartRequired)

            val notifyIndex = events.indexOfFirst { it.startsWith("notify:正在完成") }
            val quiesceIndex = events.indexOf("quiesce")
            val closeIndex = events.indexOf("port:close")
            assertTrue("必须发生 quiesce，实际：$events", quiesceIndex >= 0)
            assertTrue("必须在关库前通知界面，实际：$events", notifyIndex >= 0)
            assertTrue("必须关闭注入的数据库端口，实际：$events", closeIndex >= 0)
            assertTrue(
                "quiesce 必须在「通知重启之后、关库之前」（KD-61 窗口内），实际：$events",
                notifyIndex < quiesceIndex && quiesceIndex < closeIndex,
            )
            assertTrue("关库后写门必须进入终态", gate.databaseClosedForRestart.value)
        } finally {
            runCatching { database.close() }
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun aFailingQuiesceAbortsTheRestoreBeforeThePoolCloses() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = StudyDatabaseFactory.DEFAULT_DATABASE_NAME
        context.deleteDatabase(databaseName)
        val events = Collections.synchronizedList(mutableListOf<String>())
        val gate = BackupWriteGate(drainTimeoutMillis = 5_000L)
        val database = StudyDatabaseFactory.open(context, databaseName)
        val recordingPort = RecordingPort(database, events)
        val repository = AndroidBackupRepository(
            context = context,
            database = recordingPort,
            writeGate = gate,
            restartNotifier = { message -> events += "notify:$message" },
            observerQuiesceMillis = 0L,
            backgroundQuiesce = { error("quiesce 失败（用例注入）") },
        )
        try {
            seedStudyFacts(context, database, databaseName, seed())
            val archiveBytes = ByteArrayOutputStream().also { output ->
                repository.create(output)
            }.toByteArray()

            val failure = runCatching {
                repository.restore(ByteArrayInputStream(archiveBytes))
            }.exceptionOrNull()

            assertNotNull("钩子抛异常必须中止恢复", failure)
            assertEquals(
                "中止必须原样向上抛钩子的失败（不吞不改），实际：$failure",
                "quiesce 失败（用例注入）",
                failure?.message,
            )
            assertFalse(
                "交换未开始：不得关闭连接池，实际：$events",
                events.contains("port:close"),
            )
            assertFalse(
                "交换未开始：写门不得进入终态（要重新放行写者）",
                gate.databaseClosedForRestart.value,
            )
            assertEquals(
                "活库必须仍可读（quiesce 失败是安全的交换前失败点）",
                1,
                database.countMistakes(),
            )
        } finally {
            runCatching { database.close() }
            context.deleteDatabase(databaseName)
        }
    }

    private class RecordingPort(
        private val delegate: StudyDatabasePort,
        private val events: MutableList<String>,
    ) : StudyDatabasePort by delegate {
        override fun close() {
            events += "port:close"
            delegate.close()
        }

        override suspend fun countMistakes(): Int {
            events += "port:countMistakes"
            return delegate.countMistakes()
        }
    }

    private fun seed() = StudySeedBundle(
        problems = listOf(
            ProblemSeedRecord(
                problemId = "problem-quiesce",
                canonicalFingerprint = "c".repeat(64),
                subject = "MATH",
                createdAtEpochMillis = 1_000,
            ),
        ),
        revisions = listOf(
            ProblemRevisionSeedRecord(
                revisionId = "revision-quiesce",
                problemId = "problem-quiesce",
                revisionNumber = 1,
                title = "关库窗口题",
                problemMarkdown = "求函数最值。",
                questionDocumentSnapshot = null,
                answerSpecId = null,
                answerSpecSnapshot = null,
                answerVerificationStatus = "UNKNOWN",
                sourceType = "TEST",
                sourceReference = null,
                contentFingerprint = "d".repeat(64),
                createdAtEpochMillis = 1_000,
            ),
        ),
        practiceUnits = listOf(
            PracticeUnitSeedRecord(
                practiceUnitId = "practice-quiesce",
                problemId = "problem-quiesce",
                problemRevisionId = "revision-quiesce",
                unitKey = "unit:quiesce",
                unitKind = "PROBLEM",
                title = "关库窗口题",
                promptMarkdown = "求函数最值。",
                estimatedSeconds = 180,
                createdAtEpochMillis = 1_000,
            ),
        ),
        errorBookEntries = listOf(
            ErrorBookEntrySeedRecord(
                entryId = "entry-quiesce",
                practiceUnitId = "practice-quiesce",
                problemId = "problem-quiesce",
                currentRevisionId = "revision-quiesce",
                sourceKey = null,
                acceptedAtEpochMillis = 1_000,
                updatedAtEpochMillis = 1_000,
            ),
        ),
    )
}
