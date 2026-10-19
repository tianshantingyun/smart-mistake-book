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
import com.tingyun.smartmistakebook.core.domain.BackupWriteDeferredException
import com.tingyun.smartmistakebook.core.domain.BackupWriteGate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Collections

/**
 * KD-56 的设备级回归（小库部分）：
 *
 * 1. **复现根因**：room3 的 `PassthroughConnectionPool.close()` 是终态；关库后在
 *    **main 线程**做任何 DAO 查询都抛 `Connection pool is closed`（设备实录 code 21，
 *    进程死亡）。这条用例把"谁在关库后碰 Room"变成可执行的失败面。
 * 2. **新顺序**：恢复在关库**之前**把"请重启"交给界面（[restartNotifier]），关库后
 *    不再调用注入端口；写门进入终态，接入的写入口立即失败而不是落进已关闭连接池。
 * 3. **数据可读**：恢复返回后，用**新的** Room 实例打开恢复出的库，题数可读。
 * 4. **坏包**：交换前失败不关库、不通知重启，活库仍可读。
 *
 * ≥365MB 的大库往返见 `BackupLargeDatabaseScaleInstrumentedTest`（设备轨执行）。
 */
@RunWith(AndroidJUnit4::class)
class BackupRestoreLifecycleInstrumentedTest {

    @Test
    fun closedRoomPoolFailsMainThreadQueriesWithConnectionPoolIsClosed() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(StudyDatabaseFactory.DEFAULT_DATABASE_NAME)
        val database = StudyDatabaseFactory.open(context)
        try {
            database.close()

            val failure = withContext(Dispatchers.Main) {
                runCatching { database.countMistakes() }.exceptionOrNull()
            }
            assertNotNull("关库后的 DAO 查询必须抛出异常（不能静默读到旧数据）", failure)
            val messages = generateSequence(failure) { it.cause }
                .mapNotNull { it.message }
                .joinToString(" | ")
            assertTrue(
                "必须复现 KD-56 的 Connection pool is closed，实际：$messages",
                messages.contains("Connection pool is closed"),
            )
        } finally {
            context.deleteDatabase(StudyDatabaseFactory.DEFAULT_DATABASE_NAME)
        }
    }

    @Test
    fun restoreNotifiesRestartBeforeClosingThePoolAndRestoredDataStaysReadable() = runBlocking {
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
            restartNotifier = { message ->
                assertFalse(
                    "恢复交换整链必须在后台线程：不允许在 main looper 上关库/换文件",
                    android.os.Looper.myLooper() == android.os.Looper.getMainLooper(),
                )
                events += "notify:$message"
            },
            observerQuiesceMillis = 0L,
        )
        try {
            seedStudyFacts(context, database, databaseName, seed())

            val archiveBytes = ByteArrayOutputStream().also { output ->
                repository.create(output)
            }.toByteArray()
            events.clear()

            val validation = repository.validate(ByteArrayInputStream(archiveBytes))
            assertTrue("夹具必须产出可通过校验的归档", validation is BackupValidation.Valid)

            val receipt = repository.restore(ByteArrayInputStream(archiveBytes))
            assertEquals(1, receipt.problemCount)
            assertTrue("恢复必须要求重启（连接池已关闭）", receipt.restartRequired)

            // 关库之前已经通知界面；关库之后端口上不能再有调用。
            val closeIndex = events.indexOf("port:close")
            assertTrue("恢复必须关闭注入的数据库端口，实际：$events", closeIndex >= 0)
            val firstNotifyIndex = events.indexOfFirst { it.startsWith("notify:正在完成") }
            assertTrue(
                "必须在关库之前通知界面进入重启态，实际：$events",
                firstNotifyIndex in 0 until closeIndex,
            )
            assertTrue(
                "关库后本进程不得再碰 Room 端口，实际：$events",
                events.subList(closeIndex + 1, events.size).none { it.startsWith("port:") },
            )
            assertTrue(
                "恢复成功后必须再通知一次最终结果，实际：$events",
                events.indexOfLast { it.startsWith("notify:恢复完成") } > closeIndex,
            )
            assertTrue(
                "关库后写门必须进入终态",
                gate.databaseClosedForRestart.value,
            )
            val writeFailure = runCatching {
                gate.withWriteAdmission("采集保存") { error("must not run") }
            }.exceptionOrNull()
            assertTrue(
                "关库后接入写门的入口必须立即失败：$writeFailure",
                writeFailure is BackupWriteDeferredException,
            )

            // 恢复出的库必须能被一个新 Room 实例读出（模拟重启后的打开路径）。
            val reopened = StudyDatabaseFactory.open(context, databaseName)
            try {
                assertEquals(1, reopened.countMistakes())
            } finally {
                reopened.close()
            }
        } finally {
            runCatching { database.close() }
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun corruptArchiveFailsBeforeTheSwapWithoutClosingTheLiveDatabase() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = StudyDatabaseFactory.DEFAULT_DATABASE_NAME
        context.deleteDatabase(databaseName)
        val events = Collections.synchronizedList(mutableListOf<String>())
        val gate = BackupWriteGate(drainTimeoutMillis = 5_000L)
        val database = StudyDatabaseFactory.open(context, databaseName)
        val repository = AndroidBackupRepository(
            context = context,
            database = database,
            writeGate = gate,
            restartNotifier = { message -> events += message },
            observerQuiesceMillis = 0L,
        )
        try {
            seedStudyFacts(context, database, databaseName, seed())
            val truncated = ByteArray(32) { 0x7f }

            val failure = runCatching {
                repository.restore(ByteArrayInputStream(truncated))
            }
            assertTrue("坏包必须如实失败：${failure.exceptionOrNull()}", failure.isFailure)
            assertTrue(
                "交换前失败不得通知重启，实际：$events",
                events.isEmpty(),
            )
            assertFalse(
                "交换前失败不关库，写门不得进入终态",
                gate.databaseClosedForRestart.value,
            )
            assertEquals(
                "活库必须仍可读（坏包不破坏原数据）",
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

        override suspend fun checkpointForBackup() {
            events += "port:checkpointForBackup"
            delegate.checkpointForBackup()
        }

        override suspend fun snapshotForBackup(
            sourceDatabaseFile: File,
            snapshotTarget: File,
        ) {
            events += "port:snapshotForBackup"
            delegate.snapshotForBackup(sourceDatabaseFile, snapshotTarget)
        }
    }

    private fun seed() = StudySeedBundle(
        problems = listOf(
            ProblemSeedRecord(
                problemId = "problem-lifecycle",
                canonicalFingerprint = "c".repeat(64),
                subject = "MATH",
                createdAtEpochMillis = 1_000,
            ),
        ),
        revisions = listOf(
            ProblemRevisionSeedRecord(
                revisionId = "revision-lifecycle",
                problemId = "problem-lifecycle",
                revisionNumber = 1,
                title = "恢复生命周期题",
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
                practiceUnitId = "practice-lifecycle",
                problemId = "problem-lifecycle",
                problemRevisionId = "revision-lifecycle",
                unitKey = "unit:lifecycle",
                unitKind = "PROBLEM",
                title = "恢复生命周期题",
                promptMarkdown = "求函数最值。",
                estimatedSeconds = 180,
                createdAtEpochMillis = 1_000,
            ),
        ),
        errorBookEntries = listOf(
            ErrorBookEntrySeedRecord(
                entryId = "entry-lifecycle",
                practiceUnitId = "practice-lifecycle",
                problemId = "problem-lifecycle",
                currentRevisionId = "revision-lifecycle",
                sourceKey = null,
                acceptedAtEpochMillis = 1_000,
                updatedAtEpochMillis = 1_000,
            ),
        ),
    )
}
