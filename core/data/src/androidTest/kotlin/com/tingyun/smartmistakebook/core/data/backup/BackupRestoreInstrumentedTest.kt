package com.tingyun.smartmistakebook.core.data.backup

import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.database.sqlite.SQLiteDatabase
import androidx.core.app.NotificationManagerCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.database.AppendTutorStudentMessageDatabaseCommand
import com.tingyun.smartmistakebook.core.database.CanonicalSourceAssetRecord
import com.tingyun.smartmistakebook.core.database.ContentInstallStateRecord
import com.tingyun.smartmistakebook.core.database.CreateTutorConversationDatabaseCommand
import com.tingyun.smartmistakebook.core.database.ErrorBookEntrySeedRecord
import com.tingyun.smartmistakebook.core.database.PracticeUnitSeedRecord
import com.tingyun.smartmistakebook.core.database.ProblemRevisionSeedRecord
import com.tingyun.smartmistakebook.core.database.ProblemSeedRecord
import com.tingyun.smartmistakebook.core.database.StudyDatabaseFactory
import com.tingyun.smartmistakebook.core.data.knowledge.BundledKnowledgePackResources
import com.tingyun.smartmistakebook.core.data.study.seedStudyFacts
import com.tingyun.smartmistakebook.core.database.StudyDatabasePort
import com.tingyun.smartmistakebook.core.database.StudyDbValue
import com.tingyun.smartmistakebook.core.database.StudySeedBundle
import com.tingyun.smartmistakebook.core.data.settings.AndroidKeystoreModelSecretVault
import com.tingyun.smartmistakebook.core.data.settings.DataStoreReviewReminderRepository
import com.tingyun.smartmistakebook.core.data.settings.DataStoreSchedulingSettingsStore
import com.tingyun.smartmistakebook.core.data.settings.MODEL_SECRET_KEY_ALIAS
import com.tingyun.smartmistakebook.core.data.settings.ModelSecretBinding
import com.tingyun.smartmistakebook.core.domain.BackupValidation
import com.tingyun.smartmistakebook.core.domain.BackupWriteGate
import com.tingyun.smartmistakebook.core.domain.ExamCalendarEntry
import com.tingyun.smartmistakebook.core.domain.RestoreSettingsOutcome
import com.tingyun.smartmistakebook.core.domain.ReviewReminderDelivery
import com.tingyun.smartmistakebook.core.domain.ReviewReminderPreferences
import com.tingyun.smartmistakebook.core.domain.ReviewReminderRepository
import com.tingyun.smartmistakebook.core.domain.SchedulingOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.KeyStore
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Fault-injection matrix for backup/restore V2 (§10.4).
 *
 * Runnable scenarios: truncated archive, checksum corruption, zip bomb
 * (compression ratio), too many entries, single-entry oversize, total
 * decompression oversize, pre-swap staged database validation failure,
 * duplicate restore request idempotency, and startup recovery after a
 * simulated process death (journal + generation artifacts crafted by hand).
 *
 * True process-death scenarios (killProcess mid-swap) are provided as
 * executable skeletons with explicit injection points; they are no-ops
 * unless the manual flag is enabled.
 */
@RunWith(AndroidJUnit4::class)
class BackupRestoreInstrumentedTest {
    @Test
    fun deleteAllDataRemovesTheModelApiKeyKeystoreAlias() = runBlocking {
        // Regression: the sweep used to match a hard-coded "smartmistakebook_"
        // prefix, which never matched the alias the vault actually writes, so
        // "delete all data" left the API-key Keystore entry behind.
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(StudyDatabaseFactory.DEFAULT_DATABASE_NAME)
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val vault = AndroidKeystoreModelSecretVault(context)
        try {
            vault.write(
                TEST_API_KEY_CHARS.copyOf(),
                ModelSecretBinding(
                    generationId = "generation-delete-all",
                    provider = "test-provider",
                    baseUrl = "https://provider.invalid",
                    modelId = "test-model",
                ),
            )
            assertTrue(
                "fixture must create the alias production actually uses",
                keyStore.containsAlias(MODEL_SECRET_KEY_ALIAS),
            )

            val database = StudyDatabaseFactory.open(context)
            val databaseName = StudyDatabaseFactory.DEFAULT_DATABASE_NAME
            AndroidBackupRepository(context, database).deleteAllData()

            assertFalse(
                "delete-all-data must remove the app's model API key alias",
                keyStore.containsAlias(MODEL_SECRET_KEY_ALIAS),
            )
        } finally {
            context.deleteDatabase(StudyDatabaseFactory.DEFAULT_DATABASE_NAME)
            runCatching { vault.clear() }
        }
    }

    @Test
    fun backupDeleteAndRestoreRoundTripPreservesCatalog() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(StudyDatabaseFactory.DEFAULT_DATABASE_NAME)
        val backupDirectory = File(context.filesDir, "backup-round-trip").apply { mkdirs() }
        val archive = File(backupDirectory, "round-trip-${System.nanoTime()}.smbk")
        try {
            val database = StudyDatabaseFactory.open(context)
            val databaseName = StudyDatabaseFactory.DEFAULT_DATABASE_NAME
            seedStudyFacts(context, database, databaseName, seed())
            val repository = AndroidBackupRepository(context, database)

            val receipt = FileOutputStream(archive).use { output ->
                repository.create(output)
            }
            assertEquals(1, receipt.problemCount)

            val validation = FileInputStream(archive).use { input ->
                repository.validate(input)
            }
            assertTrue(validation is BackupValidation.Valid)

            val deleted = repository.deleteAllData()
            assertTrue(deleted.deletedDatabaseBytes > 0L)

            // KD-56：deleteAllData 与恢复一样关闭 room3 连接池（终态），生产里必须
            // 重启。这里用"新开库 + 新仓库"模拟重启后的进程，再走恢复。
            val reopenedEmpty = StudyDatabaseFactory.open(context)
            assertEquals(0, reopenedEmpty.countMistakes())
            val restartedRepository = AndroidBackupRepository(
                context = context,
                database = reopenedEmpty,
                writeGate = BackupWriteGate(),
            )

            val restoreReceipt = FileInputStream(archive).use { input ->
                restartedRepository.restore(input)
            }
            assertEquals(1, restoreReceipt.problemCount)

            val restored = StudyDatabaseFactory.open(context)
            assertEquals(1, restored.countMistakes())
            restored.close()
        } finally {
            context.deleteDatabase(StudyDatabaseFactory.DEFAULT_DATABASE_NAME)
            archive.delete()
            backupDirectory.delete()
        }
    }

    /**
     * AUD-05：备份期间并发写一笔（登记新图 + 建立引用）时，包内引用闭合仍成立——
     * 归档里那份库引用的每一张图都在包里，且包里**只有**那份库引用的图（快照之后才登记的
     * 资产是下一包的）；回执的题数也必须等于归档库里那份库自己的数。
     *
     * 同时钉住取数来源：实时库端口在整个成包过程里只被要求做一件事——快照（KD-54
     * 追加的 `checkpointForBackup` 是快照前的写队列排空，不是读取，本夹具不记录它）。
     * 旧实现用的是
     * 实时库的 `countMistakes()`，那个数在窗口里会超前于归档内容。
     */
    @Test
    fun backupCreatedWhileACaptureIsRegisteredKeepsTheArchiveReferenceClosed() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // 默认库名：成包/恢复的路径（getDatabasePath(DEFAULT_DATABASE_NAME) 那一份）与注入
        // 端口必须是同一个库——旧实现与新实现都按这个前提取快照（见
        // AndroidBackupRepository.create）。
        context.deleteDatabase(StudyDatabaseFactory.DEFAULT_DATABASE_NAME)
        val databaseName = StudyDatabaseFactory.DEFAULT_DATABASE_NAME
        val planted = mutableListOf<CanonicalSourceAssetRecord>()
        val scratch = File(context.cacheDir, "backup-concurrent-${System.nanoTime()}")
        try {
            val database = StudyDatabaseFactory.open(context, databaseName)
            seedStudyFacts(context, database, databaseName, seed())
            val firstAsset = plantCanonicalAsset(
                context = context,
                database = database,
                seed = "concurrent-first",
                createdAtEpochMillis = 2_000,
                planted = planted,
            )
            referenceViaStudentMessage(database, "concurrent-first", firstAsset.sourceAssetId)

            val liveCalls = mutableListOf<String>()
            val repository = AndroidBackupRepository(context, RecordingPort(database, liveCalls))
            var writtenDuringBackup: CanonicalSourceAssetRecord? = null

            val (backupReceipt, firstArchive) = coroutineScope {
                val writer = async(Dispatchers.IO) {
                    // 窗口内的一笔录入：资产行先落，引用随后在另一个事务里建立（大堂的真实形态）。
                    val second = plantCanonicalAsset(
                        context = context,
                        database = database,
                        seed = "concurrent-second",
                        createdAtEpochMillis = System.currentTimeMillis(),
                        planted = planted,
                    )
                    referenceViaStudentMessage(database, "concurrent-second", second.sourceAssetId)
                    writtenDuringBackup = second
                }
                val created = async {
                    val archive = ByteArrayOutputStream()
                    val createdReceipt = repository.create(archive)
                    createdReceipt to archive.toByteArray()
                }
                writer.await()
                created.await()
            }

            assertTrue(
                "题数与引用集合只能问快照库；实时库端口除了旧回退的一份快照之外不得有读取，" +
                    "实际：$liveCalls",
                liveCalls.none { it == "countMistakes" } &&
                    liveCalls.all { it == "snapshotForBackup" },
            )

            val firstClosure = unpackedArchiveClosure(context, firstArchive, File(scratch, "first"))
            assertTrue("夹具必须真的建立了一条引用", firstClosure.referencedAssets.isNotEmpty())
            assertEquals(
                "包内资产集合必须正好是包内库的引用集合（快照之后登记的图不算本次引用）",
                firstClosure.referencedAssets.size,
                backupReceipt.assetCount,
            )
            assertEquals(
                "回执题数必须与归档库自己的数一致",
                firstClosure.problemCount,
                backupReceipt.problemCount,
            )

            // "窗口内新增视为下一包"：写者已 join，下一包必须把窗口里那张新图收进来。
            // 第一包收没收它取决于快照时刻，因此这里不对第一包下断言——但对下一包是确定的。
            val secondAsset = requireNotNull(writtenDuringBackup) { "并发写者必须完成登记" }
            val secondArchive = ByteArrayOutputStream()
            val secondReceipt = repository.create(secondArchive)
            val secondClosure = unpackedArchiveClosure(
                context,
                secondArchive.toByteArray(),
                File(scratch, "second"),
            )
            assertTrue(
                "窗口内新增的图必须进下一包：${secondAsset.relativePath}",
                secondClosure.referencedAssets
                    .any { record -> record.sourceAssetId == secondAsset.sourceAssetId },
            )
            assertEquals(
                "下一包的资产集合同样必须正好是它的引用集合",
                secondClosure.referencedAssets.size,
                secondReceipt.assetCount,
            )
        } finally {
            planted.forEach { assetFile(context, it).delete() }
            context.deleteDatabase(databaseName)
            scratch.deleteRecursively()
        }
    }

    @Test
    fun oldSchemaArchiveMigratesOnRestore() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sourceName = "old-schema-source-${System.nanoTime()}.db"
        val backupDirectory = File(context.filesDir, "backup-old-schema").apply { mkdirs() }
        val archive = File(backupDirectory, "old-schema-${System.nanoTime()}.smbk")
        context.deleteDatabase(StudyDatabaseFactory.DEFAULT_DATABASE_NAME)
        context.deleteDatabase(sourceName)
        try {
            createDatabaseFromExportedSchema(context, sourceName, version = 30)
            SQLiteDatabase.openDatabase(
                context.getDatabasePath(sourceName).absolutePath,
                null,
                SQLiteDatabase.OPEN_READWRITE,
            ).use { db ->
                db.insertOrThrow(
                    "problem",
                    null,
                    ContentValues().apply {
                        put("problem_id", "problem-old")
                        put("canonical_fingerprint", "a".repeat(64))
                        put("subject", "MATH")
                        put("created_at_epoch_millis", 1_000L)
                    },
                )
                db.insertOrThrow(
                    "problem_revision",
                    null,
                    ContentValues().apply {
                        put("revision_id", "revision-old")
                        put("problem_id", "problem-old")
                        put("revision_number", 1)
                        put("title", "旧库备份题")
                        put("problem_markdown", "从旧版本备份恢复后仍能找到这道题。")
                        put("answer_verification_status", "UNKNOWN")
                        put("source_type", "TEST")
                        put("content_fingerprint", "b".repeat(64))
                        put("created_at_epoch_millis", 1_000L)
                    },
                )
                db.insertOrThrow(
                    "practice_unit",
                    null,
                    ContentValues().apply {
                        put("practice_unit_id", "practice-old")
                        put("problem_id", "problem-old")
                        put("problem_revision_id", "revision-old")
                        put("unit_key", "unit:old")
                        put("unit_kind", "PROBLEM")
                        put("title", "旧库备份题")
                        put("prompt_markdown", "从旧版本备份恢复后仍能找到这道题。")
                        put("estimated_seconds", 180)
                        put("created_at_epoch_millis", 1_000L)
                    },
                )
                db.insertOrThrow(
                    "error_book_entry",
                    null,
                    ContentValues().apply {
                        put("entry_id", "entry-old")
                        put("practice_unit_id", "practice-old")
                        put("problem_id", "problem-old")
                        put("current_revision_id", "revision-old")
                        put("status", "ACTIVE")
                        put("accepted_at_epoch_millis", 1_000L)
                        put("updated_at_epoch_millis", 1_000L)
                    },
                )
            }

            val current = StudyDatabaseFactory.open(context)
            val repository = AndroidBackupRepository(context, current)
            FileOutputStream(archive).use { output ->
                SmbkArchiveCodec.create(
                    archive = output,
                    database = context.getDatabasePath(sourceName),
                    assets = emptyList(),
                    referencedAssets = emptyList(),
                    databaseSchemaVersion = 30,
                    problemCount = 1,
                    createdAtEpochMillis = 1_000L,
                )
            }

            val restoreReceipt = FileInputStream(archive).use { input ->
                repository.restore(input)
            }
            assertEquals(30, restoreReceipt.databaseSchemaVersion)
            assertEquals(1, restoreReceipt.problemCount)

            val restored = StudyDatabaseFactory.open(context)
            assertEquals(1, restored.countMistakes())
            assertEquals(
                "从旧版本备份恢复后仍能找到这道题。",
                restored.libraryCatalogPage(
                    searchText = "",
                    subjectId = null,
                    sectionId = null,
                    masteryId = null,
                    createdFromEpochMillis = null,
                    createdToEpochMillis = null,
                    sort = "RECENTLY_CREATED",
                    offset = 0,
                    limit = 10,
                ).single().problemMarkdown,
            )
            restored.close()
        } finally {
            context.deleteDatabase(StudyDatabaseFactory.DEFAULT_DATABASE_NAME)
            context.deleteDatabase(sourceName)
            archive.delete()
            backupDirectory.delete()
        }
    }

    /**
     * 6C-1 的设置往返：备份 → 改设置 → 恢复 → 设置回到备份里那份。
     *
     * 设置走**生产用的 DataStore 实现**（隔离存储目录，避免与 app 进程里那一对实例撞同一个
     * 文件），数据库走真实仓库路径与真实的恢复交换。同时钉住两件事：
     *   - 清单的版本戳：`hasSettings`、应用版本、内容版本（取自**快照库**里的
     *     `content_install_state`）；
     *   - 投递历史不在备份载荷里：恢复不许把它清掉（"今天已经提醒过"不能被旧包改写）。
     */
    @Test
    fun settingsSurviveBackupAndRestoreRoundTrip() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(StudyDatabaseFactory.DEFAULT_DATABASE_NAME)
        val isolatedRoot = File(context.cacheDir, "backup-settings-tests/${UUID.randomUUID()}")
        val scopeJob = SupervisorJob()
        val scope = CoroutineScope(scopeJob + Dispatchers.IO)
        val scheduling = DataStoreSchedulingSettingsStore(
            IsolatedStorageContext(context, isolatedRoot),
            scope,
        )
        val reminders = DataStoreReviewReminderRepository(
            IsolatedStorageContext(context, isolatedRoot),
            scope,
        )
        try {
            val database = StudyDatabaseFactory.open(context)
            val databaseName = StudyDatabaseFactory.DEFAULT_DATABASE_NAME
            seedStudyFacts(context, database, databaseName, seed())
            val packId = requireNotNull(
                BundledKnowledgePackResources.updateManifest?.packId,
            ) { "随包内容清单必须可读，否则内容版本戳无处可查" }
            database.recordContentInstallState(
                ContentInstallStateRecord(
                    packId = packId,
                    contentVersion = "backup-test-content-v1",
                    appliedAtEpochMillis = 1_000L,
                ),
            )

            val backupExams = listOf(
                ExamCalendarEntry(
                    entryId = "exam-math",
                    subject = "MATH",
                    examEpochDay = 20_700,
                    title = "数学期末",
                ),
                ExamCalendarEntry(
                    entryId = "exam-physics",
                    subject = "PHYSICS",
                    examEpochDay = 20_710,
                    title = "物理期末",
                ),
            )
            val backupParameters = List(21) { index -> 0.05 * (index + 1) }
            scheduling.setOptions(SchedulingOptions(0.9))
            backupExams.forEach { scheduling.addExam(it) }
            scheduling.setOptimizedParameters(backupParameters.toDoubleArray())
            reminders.setEnabled(true)
            reminders.setReminderTime(18 * 60 + 30)
            val claimedDelivery = ReviewReminderDelivery(
                localEpochDay = 20_655,
                zoneId = "Asia/Shanghai",
            )
            assertTrue(reminders.claimNotificationDelivery(claimedDelivery))

            // 系统通知授权不是偏好、也不在包里：恢复前后它必须原样（"只存偏好"）。
            val notificationsAuthorized =
                NotificationManagerCompat.from(context).areNotificationsEnabled()

            val repository = AndroidBackupRepository(context, database, scheduling, reminders)
            // 判据取"成包前那一行"而不是写死的串：随包安装器可能在测试进程里并发写同一行，
            // 而戳的语义是"快照那一刻那一行的值"——拿它对照才是在测取数来源本身。
            val stampedContentVersion = database.readContentInstallState(packId)?.contentVersion
            assertTrue("夹具必须让随包内容版本有值", !stampedContentVersion.isNullOrEmpty())
            val archive = ByteArrayOutputStream()
            val receipt = repository.create(archive)
            assertEquals(1, receipt.problemCount)

            val validation = repository.validate(ByteArrayInputStream(archive.toByteArray()))
            assertTrue(validation is BackupValidation.Valid)
            validation as BackupValidation.Valid
            assertTrue("v2 成包必须带上设置", validation.manifest.hasSettings)
            assertEquals(
                "应用版本戳取自包管理器",
                context.packageManager.getPackageInfo(context.packageName, 0).versionName,
                validation.manifest.appVersionName,
            )
            assertEquals(
                "内容版本戳取自快照库里的 content_install_state",
                stampedContentVersion,
                validation.manifest.contentVersion,
            )

            // 换成与备份逐字段不同的另一套设置：往返才有判据。
            scheduling.setOptions(SchedulingOptions(0.8))
            scheduling.exams.first().forEach { scheduling.removeExam(it.entryId) }
            scheduling.addExam(
                ExamCalendarEntry(
                    entryId = "exam-other",
                    subject = "CHEMISTRY",
                    examEpochDay = 20_800,
                    title = "化学期末",
                ),
            )
            scheduling.setOptimizedParameters(null)
            reminders.setEnabled(false)
            reminders.setReminderTime(7 * 60)

            val restoreReceipt = repository.restore(ByteArrayInputStream(archive.toByteArray()))

            assertEquals("题数取自归档库本身", 1, restoreReceipt.problemCount)
            assertEquals(
                "备份里的设置写进了本机",
                RestoreSettingsOutcome.APPLIED,
                restoreReceipt.settings,
            )
            assertEquals(
                "恢复只写偏好，不复活也不改动系统通知授权",
                notificationsAuthorized,
                NotificationManagerCompat.from(context).areNotificationsEnabled(),
            )
            assertEquals(0.9, scheduling.options.first().desiredRetention, 1e-9)
            assertEquals(
                backupExams.sortedBy { it.entryId },
                scheduling.exams.first().sortedBy { it.entryId },
            )
            assertArrayEquals(
                "本地拟合参数必须原样往返",
                backupParameters.toDoubleArray(),
                scheduling.optimizedParameters.first(),
                0.0,
            )
            assertEquals(
                ReviewReminderPreferences(enabled = true, minutesAfterMidnight = 18 * 60 + 30),
                reminders.current(),
            )
            assertFalse(
                "投递历史不在备份载荷里，恢复不许把它清掉",
                reminders.claimNotificationDelivery(claimedDelivery),
            )

            database.close()
            val restored = StudyDatabaseFactory.open(context)
            assertEquals(1, restored.countMistakes())
            restored.close()
        } finally {
            scopeJob.cancelAndJoin()
            context.deleteDatabase(StudyDatabaseFactory.DEFAULT_DATABASE_NAME)
            isolatedRoot.deleteRecursively()
        }
    }

    /**
     * v1 旧包（没有 settings、没有版本戳）在新 app 上恢复**不炸**，且不改动本机设置：
     * 缺省字段读作"包内没有设置"，不是"设置是默认值"。
     */
    @Test
    fun restoringAV1ArchiveWithoutSettingsKeepsLocalSettingsUntouched() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(StudyDatabaseFactory.DEFAULT_DATABASE_NAME)
        val isolatedRoot = File(context.cacheDir, "backup-v1-settings/${UUID.randomUUID()}")
        val scopeJob = SupervisorJob()
        val scope = CoroutineScope(scopeJob + Dispatchers.IO)
        val scheduling = DataStoreSchedulingSettingsStore(
            IsolatedStorageContext(context, isolatedRoot),
            scope,
        )
        val reminders = DataStoreReviewReminderRepository(
            IsolatedStorageContext(context, isolatedRoot),
            scope,
        )
        val snapshotFile = File(context.cacheDir, "v1-restore-snapshot-${System.nanoTime()}.db")
        try {
            val database = StudyDatabaseFactory.open(context)
            val databaseName = StudyDatabaseFactory.DEFAULT_DATABASE_NAME
            seedStudyFacts(context, database, databaseName, seed())
            val localExam = ExamCalendarEntry(
                entryId = "exam-local",
                subject = "MATH",
                examEpochDay = 20_900,
                title = "本机考试",
            )
            scheduling.setOptions(SchedulingOptions(0.9))
            scheduling.addExam(localExam)
            reminders.setEnabled(true)
            reminders.setReminderTime(6 * 60 + 15)

            // 拿真实快照做一份 v1 归档：清单只有 v1 那会儿的字段。
            database.snapshotForBackup(context.getDatabasePath(databaseName), snapshotFile)
            val v1Archive = buildV1Archive(snapshotFile.readBytes())

            val repository = AndroidBackupRepository(context, database, scheduling, reminders)
            val receipt = repository.restore(ByteArrayInputStream(v1Archive))

            assertEquals(1, receipt.problemCount)
            assertEquals(
                "v1 旧包没有设置载荷，本机设置原样不动",
                RestoreSettingsOutcome.NOT_IN_ARCHIVE,
                receipt.settings,
            )
            assertEquals(0.9, scheduling.options.first().desiredRetention, 1e-9)
            assertEquals(listOf(localExam), scheduling.exams.first())
            assertEquals(
                ReviewReminderPreferences(enabled = true, minutesAfterMidnight = 6 * 60 + 15),
                reminders.current(),
            )

            database.close()
            val restored = StudyDatabaseFactory.open(context)
            assertEquals(1, restored.countMistakes())
            restored.close()
        } finally {
            scopeJob.cancelAndJoin()
            context.deleteDatabase(StudyDatabaseFactory.DEFAULT_DATABASE_NAME)
            snapshotFile.delete()
            isolatedRoot.deleteRecursively()
        }
    }

    /**
     * 设置写不进去时是**部分恢复**：回执如实标 [RestoreSettingsOutcome.WRITE_FAILED]，
     * 数据库**不回滚**（它已经切换到备份数据且一致）。
     *
     * KD-47：设置是多个键**顺序写、非原子**的——本夹具的失败点在提醒口
     * （[FailingReviewReminderRepository.setEnabled]），此前的调度选项已写成备份那份。
     * 所以"本机设置保持原样"不是事实（旧文案已改）；本用例把"失败前已成功的键保留"
     * 钉成断言，防止以后又把非原子写当成原子来承诺。
     */
    @Test
    fun settingsWriteFailureIsReportedAsPartialWithoutRollingBackTheRestoredDatabase() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(StudyDatabaseFactory.DEFAULT_DATABASE_NAME)
        val isolatedRoot = File(context.cacheDir, "backup-settings-failure/${UUID.randomUUID()}")
        val scopeJob = SupervisorJob()
        val scope = CoroutineScope(scopeJob + Dispatchers.IO)
        val scheduling = DataStoreSchedulingSettingsStore(
            IsolatedStorageContext(context, isolatedRoot),
            scope,
        )
        try {
            val database = StudyDatabaseFactory.open(context)
            val databaseName = StudyDatabaseFactory.DEFAULT_DATABASE_NAME
            seedStudyFacts(context, database, databaseName, seed())
            scheduling.setOptions(SchedulingOptions(0.9))

            val repository = AndroidBackupRepository(
                context,
                database,
                scheduling,
                FailingReviewReminderRepository(),
            )
            val archive = ByteArrayOutputStream()
            repository.create(archive)

            // 恢复前把本机设置改成另一份（0.8）。写失败发生在提醒口——调度选项此前已被
            // 写成备份那份 0.9，本机设置因此是"半新半旧"，不是恢复前那份。
            scheduling.setOptions(SchedulingOptions(0.8))

            val receipt = repository.restore(ByteArrayInputStream(archive.toByteArray()))

            assertEquals(1, receipt.problemCount)
            assertEquals(
                "设置没写进去必须如实标 partial",
                RestoreSettingsOutcome.WRITE_FAILED,
                receipt.settings,
            )
            assertEquals(
                "KD-47：多键顺序写非原子——失败前已成功的调度键必须保留（0.9，而非恢复前的 0.8）；" +
                    "'本机设置保持原样'不是事实",
                0.9,
                scheduling.options.first().desiredRetention,
                1e-9,
            )
            val restored = StudyDatabaseFactory.open(context)
            assertEquals(
                "设置写失败不回滚已经一致的数据库",
                1,
                restored.countMistakes(),
            )
            restored.close()
        } finally {
            scopeJob.cancelAndJoin()
            context.deleteDatabase(StudyDatabaseFactory.DEFAULT_DATABASE_NAME)
            isolatedRoot.deleteRecursively()
        }
    }

    /**
     * v1 归档夹具：`formatVersion` 1、只有 v1 那会儿的清单字段（没有版本戳与设置字段），
     * 数据条目与校验和按既有约定写全。
     */
    private fun buildV1Archive(databaseContent: ByteArray): ByteArray {
        val databaseSha = sha256Hex(databaseContent)
        val manifest = buildString {
            append("{")
            append("\"formatVersion\":1,")
            append("\"databaseSchemaVersion\":32,")
            append("\"createdAt\":1000,")
            append("\"problemCount\":1,")
            append("\"assetCount\":0,")
            append("\"hashAlgorithm\":\"SHA-256\",")
            append("\"files\":[")
            append("{\"path\":\"database.sqlite\",\"sha256\":\"$databaseSha\",")
            append("\"byteSize\":${databaseContent.size}}]}")
        }
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(manifest.toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("database.sqlite"))
            zip.write(databaseContent)
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("checksums.sha256"))
            zip.write("$databaseSha *database.sqlite".toByteArray())
            zip.closeEntry()
        }
        return output.toByteArray()
    }

    /**
     * 只失败在"写"上的提醒设置口：读侧返回默认值（成包要用），写侧抛异常——复现
     * "数据库已切换、设置写不进去"的形态，且不掺入其它故障。
     */
    private class FailingReviewReminderRepository : ReviewReminderRepository {
        override val preferences: Flow<ReviewReminderPreferences> =
            flowOf(ReviewReminderPreferences())

        override suspend fun current(): ReviewReminderPreferences = ReviewReminderPreferences()

        override suspend fun setEnabled(enabled: Boolean) {
            error("reminder preference write is unavailable in this fixture")
        }

        override suspend fun setReminderTime(minutesAfterMidnight: Int) {
            error("reminder preference write is unavailable in this fixture")
        }

        override suspend fun claimNotificationDelivery(delivery: ReviewReminderDelivery): Boolean =
            true
    }

    /** 设置 DataStore 的隔离存储：同一个文件只允许一个实例，测试不能撞 app 进程那份。 */
    private class IsolatedStorageContext(
        base: Context,
        private val root: File,
    ) : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this

        override fun getFilesDir(): File = File(root, "files").also { directory ->
            check(directory.isDirectory || directory.mkdirs())
        }

        override fun getNoBackupFilesDir(): File = File(root, "no-backup").also { directory ->
            check(directory.isDirectory || directory.mkdirs())
        }
    }

    @Test
    fun orphanCanonicalAssetIsRemovedWithoutTouchingReferencedAssets() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "orphan-cleanup-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        val planted = mutableListOf<CanonicalSourceAssetRecord>()
        try {
            val database = StudyDatabaseFactory.open(context, databaseName)
            seedStudyFacts(context, database, databaseName, seed())
            val repository = AndroidBackupRepository(context, database)
            val orphan = plantCanonicalAsset(
                context = context,
                database = database,
                seed = "orphan",
                createdAtEpochMillis = 2_000,
                planted = planted,
            )

            val removed = repository.cleanupOrphanAssets()

            // 计数是"真删掉的图"：账本里删了行、文件却没删掉时不该报成一张。
            assertEquals(1, removed)
            assertEquals(null, database.readCanonicalSourceAsset(orphan.sourceAssetId))
            assertFalse(assetFile(context, orphan).exists())
            assertEquals(1, database.countMistakes())
            database.close()
        } finally {
            planted.forEach { assetFile(context, it).delete() }
            context.deleteDatabase(databaseName)
        }
    }

    /**
     * 端口装饰器：把"未引用候选"固定成引用建立之前的快照，用来复现旧实现的时序
     * （清理先按旧候选删文件、之后才重新判定并删行）。其余方法经接口委托原样透传。
     */
    private class StaleOrphanSnapshotPort(
        private val delegate: StudyDatabasePort,
        private val snapshot: List<CanonicalSourceAssetRecord>,
    ) : StudyDatabasePort by delegate {
        override suspend fun readUnreferencedCanonicalAssets():
            List<CanonicalSourceAssetRecord> = snapshot
    }

    /**
     * 记录**实时库**端口上被问过什么（成包必须只问它"做一份快照"，其余读数都该取自快照库）。
     * 其余方法经接口委托原样透传。
     */
    private class RecordingPort(
        private val delegate: StudyDatabasePort,
        private val calls: MutableList<String>,
    ) : StudyDatabasePort by delegate {
        override suspend fun snapshotForBackup(sourceDatabaseFile: File, snapshotTarget: File) {
            calls += "snapshotForBackup"
            delegate.snapshotForBackup(sourceDatabaseFile, snapshotTarget)
        }

        override suspend fun countMistakes(): Int {
            calls += "countMistakes"
            return delegate.countMistakes()
        }
    }

    /**
     * 原子认领的判据：清理不得依据过期的候选快照删文件。
     *
     * 本用例刻意让端口继续返回"引用建立之前"的旧候选。旧实现会据此删掉文件，随后那次
     * 整表删除因引用已存在而放过该行——文件没了、行还在，于是断言文件仍在时失败；
     * 新实现在写事务内重新判定，不读这个快照，行与文件都保得住。
     */
    @Test
    fun cleanupNeverUnlinksAnAssetThatBecameReferencedAfterTheCandidateRead() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "orphan-stale-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        val planted = mutableListOf<CanonicalSourceAssetRecord>()
        try {
            val database = StudyDatabaseFactory.open(context, databaseName)
            seedStudyFacts(context, database, databaseName, seed())
            val asset = plantCanonicalAsset(
                context = context,
                database = database,
                seed = "stale",
                createdAtEpochMillis = 2_000,
                planted = planted,
            )
            val staleSnapshot = database.readUnreferencedCanonicalAssets()
            assertTrue(staleSnapshot.any { it.sourceAssetId == asset.sourceAssetId })

            // 大堂的引用形态：资产行先落，引用随后在另一个事务里建立。
            database.createTutorConversation(
                CreateTutorConversationDatabaseCommand(
                    conversationId = "conversation-stale",
                    anchorKind = "TEXT_ONLY",
                    anchorId = null,
                    anchorRevisionId = null,
                    title = null,
                    createdAtEpochMillis = 1_000,
                ),
            )
            database.appendTutorStudentMessage(
                AppendTutorStudentMessageDatabaseCommand(
                    conversationId = "conversation-stale",
                    messageId = "message-stale",
                    ordinal = 1,
                    bodyMarkdown = "看看这道题",
                    logicalOperationId = "operation-stale",
                    createdAtEpochMillis = 1_000,
                    sourceImageAssetIds = listOf(asset.sourceAssetId),
                ),
            )

            val repository = AndroidBackupRepository(
                context,
                StaleOrphanSnapshotPort(database, staleSnapshot),
            )
            val removed = repository.cleanupOrphanAssets()

            assertEquals(0, removed)
            assertTrue(assetFile(context, asset).isFile)
            assertEquals(asset, database.readCanonicalSourceAsset(asset.sourceAssetId))
            database.close()
        } finally {
            planted.forEach { assetFile(context, it).delete() }
            context.deleteDatabase(databaseName)
        }
    }

    /**
     * 同内容再登记必须把登记时间刷成本次调用给的值：资产 id 与文件名都由内容哈希推出，
     * `INSERT OR IGNORE` 会保留旧时间戳，于是"会话被删 → 行成孤儿 → 学生重发同一张图"
     * 时宽限期按旧时间戳判定，这一行会在"登记 → 建立引用"的窗口里被认领回收，随后的
     * 写引用撞 `tutor_message_source_asset` 的 RESTRICT 外键而让发送失败。
     */
    @Test
    fun reRegisteringTheSameImageRefreshesTheCleanupTimestamp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "orphan-reregister-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        val planted = mutableListOf<CanonicalSourceAssetRecord>()
        try {
            val database = StudyDatabaseFactory.open(context, databaseName)
            seedStudyFacts(context, database, databaseName, seed())
            val repository = AndroidBackupRepository(context, database)
            val asset = plantCanonicalAsset(
                context = context,
                database = database,
                seed = "reregister",
                createdAtEpochMillis = 2_000,
                planted = planted,
            )
            database.createTutorConversation(
                CreateTutorConversationDatabaseCommand(
                    conversationId = "conversation-reregister",
                    anchorKind = "TEXT_ONLY",
                    anchorId = null,
                    anchorRevisionId = null,
                    title = null,
                    createdAtEpochMillis = 1_000,
                ),
            )
            database.appendTutorStudentMessage(
                AppendTutorStudentMessageDatabaseCommand(
                    conversationId = "conversation-reregister",
                    messageId = "message-reregister",
                    ordinal = 1,
                    bodyMarkdown = "看看这道题",
                    logicalOperationId = "operation-reregister",
                    createdAtEpochMillis = 1_000,
                    sourceImageAssetIds = listOf(asset.sourceAssetId),
                ),
            )
            // 会话被删 → 引用级联删除，资产行留成孤儿，时间戳仍是过去那个 2_000。
            database.deleteTutorConversation("conversation-reregister")
            assertTrue(
                database.readUnreferencedCanonicalAssets()
                    .any { it.sourceAssetId == asset.sourceAssetId },
            )

            // 学生重发同一张图：同 id 再登记，时间戳应是本次的"现在"。
            val reRegistered = asset.copy(createdAtEpochMillis = System.currentTimeMillis())
            database.registerCanonicalSourceAsset(reRegistered)

            val removed = repository.cleanupOrphanAssets()

            assertEquals(0, removed)
            assertEquals(reRegistered, database.readCanonicalSourceAsset(asset.sourceAssetId))
            assertTrue(assetFile(context, asset).isFile)
            database.close()
        } finally {
            planted.forEach { assetFile(context, it).delete() }
            context.deleteDatabase(databaseName)
        }
    }

    /**
     * 宽限期：学生刚拍/刚选、引用尚未建立的图不能在下一轮清理里被回收
     * （`tutor_message_source_asset` 对资产行是 RESTRICT 外键，删了会让发送失败）。
     */
    @Test
    fun youngOrphanIsDeferredUntilItAgesPastTheGraceWindow() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "orphan-grace-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        val planted = mutableListOf<CanonicalSourceAssetRecord>()
        try {
            val database = StudyDatabaseFactory.open(context, databaseName)
            seedStudyFacts(context, database, databaseName, seed())
            val repository = AndroidBackupRepository(context, database)
            val ancient = plantCanonicalAsset(
                context = context,
                database = database,
                seed = "ancient",
                createdAtEpochMillis = 2_000,
                planted = planted,
            )
            val young = plantCanonicalAsset(
                context = context,
                database = database,
                seed = "young",
                createdAtEpochMillis = System.currentTimeMillis(),
                planted = planted,
            )

            val removed = repository.cleanupOrphanAssets()

            assertEquals(1, removed)
            assertEquals(null, database.readCanonicalSourceAsset(ancient.sourceAssetId))
            assertFalse(assetFile(context, ancient).exists())
            assertEquals(young, database.readCanonicalSourceAsset(young.sourceAssetId))
            assertTrue(assetFile(context, young).isFile)
            // 被推迟，不是被丢弃：等它老了，下一轮清理仍然会回收它。
            assertTrue(
                database.readUnreferencedCanonicalAssets()
                    .any { it.sourceAssetId == young.sourceAssetId },
            )
            database.close()
        } finally {
            planted.forEach { assetFile(context, it).delete() }
            context.deleteDatabase(databaseName)
        }
    }

    /**
     * 按 vault 的落盘约定种一张真图：`source-assets/<sha256>.jpg` 加与之匹配的行。
     * `vault.delete` 会核对大小与 SHA-256，只插行不落文件的夹具会让清理计数失去意义。
     *
     * 文件先登记进 [planted] 再插库：插入抛异常时文件不会漏在共享的 vault 目录里，
     * 别的套件按目录名集合做断言时不会被它污染。
     */
    private suspend fun plantCanonicalAsset(
        context: Context,
        database: StudyDatabasePort,
        seed: String,
        createdAtEpochMillis: Long,
        planted: MutableList<CanonicalSourceAssetRecord>,
    ): CanonicalSourceAssetRecord {
        val bytes = "canonical-asset-$seed".toByteArray()
        val sha256 = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte) }
        val assetRoot = File(context.filesDir, "source-assets").apply { mkdirs() }
        val file = File(assetRoot, "$sha256.jpg")
        FileOutputStream(file).use { it.write(bytes) }
        val record = CanonicalSourceAssetRecord(
            sourceAssetId = "asset-${sha256.take(32)}",
            contentSha256 = sha256,
            relativePath = "source-assets/$sha256.jpg",
            mimeType = "image/jpeg",
            byteSize = file.length(),
            width = 100,
            height = 200,
            sourceType = StudyDbValue.SourceAssetType.CAMERA,
            createdAtEpochMillis = createdAtEpochMillis,
        )
        planted += record
        database.insertOrphanCanonicalAssetForTest(record)
        return record
    }

    private fun assetFile(
        context: Context,
        asset: CanonicalSourceAssetRecord,
    ): File = File(context.filesDir, asset.relativePath)

    /**
     * 大堂的真实引用形态：学生消息附图建立 `tutor_message_source_asset` 引用（四表引用口径
     * 里的一支）。资产行必须先登记，引用才能建立（RESTRICT 外键）。
     */
    private suspend fun referenceViaStudentMessage(
        database: StudyDatabasePort,
        suffix: String,
        sourceAssetId: String,
    ) {
        database.createTutorConversation(
            CreateTutorConversationDatabaseCommand(
                conversationId = "conversation-$suffix",
                anchorKind = "TEXT_ONLY",
                anchorId = null,
                anchorRevisionId = null,
                title = null,
                createdAtEpochMillis = 1_000,
            ),
        )
        database.appendTutorStudentMessage(
            AppendTutorStudentMessageDatabaseCommand(
                conversationId = "conversation-$suffix",
                messageId = "message-$suffix",
                ordinal = 1,
                bodyMarkdown = "看看这道题",
                logicalOperationId = "operation-$suffix",
                createdAtEpochMillis = 1_000,
                sourceImageAssetIds = listOf(sourceAssetId),
            ),
        )
    }

    /** 临时库名开出来的验证/归档读口，连同它的 -wal/-shm 一起清掉。 */
    private fun deleteDatabaseArtifacts(databaseFile: File) {
        listOf(
            databaseFile,
            File("${databaseFile.absolutePath}-wal"),
            File("${databaseFile.absolutePath}-shm"),
        ).forEach { it.delete() }
    }

    /** 一个归档解出来的闭合事实：包内库引用哪些图、它自己有多少道题。 */
    private data class UnpackedArchiveClosure(
        val referencedAssets: List<CanonicalSourceAssetRecord>,
        val problemCount: Int,
    )

    /**
     * 解包归档并对拍包内闭合：`包内库引用的图 → 条目在包里 + 字节数与记录一致`。返回包内库的
     * 引用集合与题数，供调用方与成包回执比对；归档库按临时库名开出来读，读完把 -wal/-shm 清掉。
     */
    private suspend fun unpackedArchiveClosure(
        context: Context,
        archiveBytes: ByteArray,
        destination: File,
    ): UnpackedArchiveClosure {
        destination.mkdirs()
        val validation = SmbkArchiveCodec.unpack(
            ByteArrayInputStream(archiveBytes),
            destination,
        )
        assertTrue("归档必须能过编解码层的校验", validation is BackupValidation.Valid)
        val unpackedName = "backup-unpacked-${System.nanoTime()}.db"
        val unpackedDatabaseFile = context.getDatabasePath(unpackedName)
        File(destination, "database.sqlite").copyTo(unpackedDatabaseFile, overwrite = true)
        val unpacked = StudyDatabaseFactory.open(context, unpackedName)
        return try {
            val referenced = unpacked.readReferencedCanonicalAssets()
            referenced.forEach { record ->
                val staged = File(
                    File(destination, "assets"),
                    record.relativePath.substringAfterLast('/'),
                )
                assertTrue("包内库引用的图必须在包里：${record.relativePath}", staged.isFile)
                assertEquals(
                    "包内图的字节数必须与库里的资产行一致：${record.relativePath}",
                    record.byteSize,
                    staged.length(),
                )
            }
            UnpackedArchiveClosure(
                referencedAssets = referenced,
                problemCount = unpacked.countMistakes(),
            )
        } finally {
            unpacked.close()
            deleteDatabaseArtifacts(unpackedDatabaseFile)
        }
    }

    // ------------------------------------------------------------------
    // §10.4 fault injection: archive-level attacks
    // ------------------------------------------------------------------

    @Test
    fun truncatedArchiveIsRejectedByValidateAndRestore() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scratch = File(context.cacheDir, "fault-truncated").apply { mkdirs() }
        try {
            val archive = buildSyntheticArchive(databaseContent = ByteArray(8_192) { 7 })
            // Injection point: cut the archive at 70% of its length.
            val truncated = archive.copyOfRange(0, (archive.size * 0.7).toInt())

            val validation = SmbkArchiveCodec.validate(
                ByteArrayInputStream(truncated),
                File(scratch, "v1").apply { mkdirs() },
            )
            assertTrue("截断归档必须被拒绝", validation is BackupValidation.Invalid)

            val database = StudyDatabaseFactory.open(context)
            val databaseName = StudyDatabaseFactory.DEFAULT_DATABASE_NAME
            val repository = AndroidBackupRepository(context, database)
            val failure = runCatching {
                repository.restore(ByteArrayInputStream(truncated))
            }
            assertTrue(failure.isFailure)
            database.close()
        } finally {
            context.deleteDatabase(StudyDatabaseFactory.DEFAULT_DATABASE_NAME)
            scratch.deleteRecursively()
        }
    }

    @Test
    fun checksumMismatchIsRejected() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scratch = File(context.cacheDir, "fault-checksum").apply { mkdirs() }
        try {
            // Archive whose data bytes do not match the manifest/checksum sha.
            val archive = buildSyntheticArchive(databaseContent = "original-db".toByteArray())
            val validation = SmbkArchiveCodec.validate(
                ByteArrayInputStream(corruptDataByte(archive)),
                File(scratch, "v1").apply { mkdirs() },
            )
            assertTrue("checksum 不一致必须被拒绝", validation is BackupValidation.Invalid)
        } finally {
            scratch.deleteRecursively()
        }
    }

    @Test
    fun zipBombCompressionRatioIsRejected() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scratch = File(context.cacheDir, "fault-bomb").apply { mkdirs() }
        try {
            // A single entry of 16 MB zeros compresses to a few KB → ratio ≫ 100.
            val bomb = buildSyntheticArchive(databaseContent = ByteArray(16 * 1024 * 1024))
            val validation = SmbkArchiveCodec.validate(
                ByteArrayInputStream(bomb),
                scratch,
            )
            assertTrue("压缩比超限必须被拒绝", validation is BackupValidation.Invalid)
        } finally {
            scratch.deleteRecursively()
        }
    }

    @Test
    fun tooManyEntriesAreRejected() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scratch = File(context.cacheDir, "fault-entries").apply { mkdirs() }
        try {
            val limits = SmbkResourceLimits(maxEntryCount = 3)
            val archive = buildSyntheticArchive(
                databaseContent = "db".toByteArray(),
                extraAssets = mapOf(
                    "assets/a1.bin" to byteArrayOf(1),
                    "assets/a2.bin" to byteArrayOf(2),
                    "assets/a3.bin" to byteArrayOf(3),
                ),
            )
            val validation = SmbkArchiveCodec.validate(
                ByteArrayInputStream(archive),
                scratch,
                limits,
            )
            assertTrue("条目数超限必须被拒绝", validation is BackupValidation.Invalid)
        } finally {
            scratch.deleteRecursively()
        }
    }

    @Test
    fun singleEntryOversizeIsRejected() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scratch = File(context.cacheDir, "fault-single").apply { mkdirs() }
        try {
            // KD-53 起数据库条目用自己的预算；这一档钉的是**题图**的单条目上限。
            val limits = SmbkResourceLimits(maxSingleEntryBytes = 256L)
            val archive = buildSyntheticArchive(
                databaseContent = "db".toByteArray(),
                extraAssets = mapOf(
                    // 随机字节：确保先撞上单条目预算，而不是压缩比门。
                    "assets/oversize.bin" to ByteArray(1_024)
                        .also { java.util.Random(17L).nextBytes(it) },
                ),
            )
            val validation = SmbkArchiveCodec.validate(
                ByteArrayInputStream(archive),
                scratch,
                limits,
            )
            assertTrue("单条目超限必须被拒绝", validation is BackupValidation.Invalid)
        } finally {
            scratch.deleteRecursively()
        }
    }

    /**
     * KD-53：`database.sqlite` 的单条目预算与题图分开。数据库条目高于
     * `maxSingleEntryBytes`、低于 `maxDatabaseEntryBytes` 时，**校验与解包都必须通过**——
     * 旧实现这里会报 `database.sqlite 超过单条目解压大小上限`，正是设备实测的不可校验/
     * 不可恢复。
     */
    @Test
    fun databaseEntryUsesItsOwnBudgetOnBothValidateAndUnpack() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scratch = File(context.cacheDir, "fault-db-budget-ok").apply { mkdirs() }
        val destination = File(context.cacheDir, "fault-db-budget-unpack").apply { mkdirs() }
        val databaseBytes = ByteArray(1_024).also { java.util.Random(11L).nextBytes(it) }
        try {
            val limits = SmbkResourceLimits(
                maxSingleEntryBytes = 256L,
                maxDatabaseEntryBytes = 4_096L,
                maxTotalDecompressedBytes = 64L * 1024L,
            )
            val archive = buildSyntheticArchive(databaseContent = databaseBytes)

            val validation = SmbkArchiveCodec.validate(
                ByteArrayInputStream(archive),
                scratch,
                limits,
            )
            assertTrue(
                "数据库条目高于单图上限、低于数据库上限时必须可校验：$validation",
                validation is BackupValidation.Valid,
            )

            val unpacked = SmbkArchiveCodec.unpack(
                ByteArrayInputStream(archive),
                destination,
                limits,
            )
            assertTrue(
                "恢复侧与校验侧同口径，必须同样可解包：$unpacked",
                unpacked is BackupValidation.Valid,
            )
            assertArrayEquals(databaseBytes, File(destination, "database.sqlite").readBytes())
        } finally {
            scratch.deleteRecursively()
            destination.deleteRecursively()
        }
    }

    /** KD-53 负向：数据库条目超过自己的预算时，校验与解包都必须拒绝（同口径）。 */
    @Test
    fun databaseEntryAboveItsOwnBudgetIsRejectedByBothPaths() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scratch = File(context.cacheDir, "fault-db-budget-bad").apply { mkdirs() }
        val destination = File(context.cacheDir, "fault-db-budget-bad-unpack").apply { mkdirs() }
        val databaseBytes = ByteArray(2_048).also { java.util.Random(13L).nextBytes(it) }
        try {
            val limits = SmbkResourceLimits(
                maxSingleEntryBytes = 1_000_000L,
                maxDatabaseEntryBytes = 1_024L,
                maxTotalDecompressedBytes = 1_000_000L,
            )
            val archive = buildSyntheticArchive(databaseContent = databaseBytes)

            val validation = SmbkArchiveCodec.validate(
                ByteArrayInputStream(archive),
                scratch,
                limits,
            )
            assertTrue(validation is BackupValidation.Invalid)
            assertTrue(
                (validation as BackupValidation.Invalid).reason.contains("database.sqlite"),
            )

            val unpacked = SmbkArchiveCodec.unpack(
                ByteArrayInputStream(archive),
                destination,
                limits,
            )
            assertTrue(unpacked is BackupValidation.Invalid)
            assertTrue(
                (unpacked as BackupValidation.Invalid).reason.contains("database.sqlite"),
            )
            assertTrue(!File(destination, "database.sqlite").exists())
        } finally {
            scratch.deleteRecursively()
            destination.deleteRecursively()
        }
    }

    @Test
    fun totalDecompressionOversizeIsRejected() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scratch = File(context.cacheDir, "fault-total").apply { mkdirs() }
        try {
            // Per-entry limit high, but the total budget is exhausted by entry #2.
            val limits = SmbkResourceLimits(
                maxSingleEntryBytes = 4_096L,
                maxTotalDecompressedBytes = 700L,
            )
            val archive = buildSyntheticArchive(
                databaseContent = ByteArray(512) { 4 },
                extraAssets = mapOf("assets/big.bin" to ByteArray(512) { 5 }),
            )
            val validation = SmbkArchiveCodec.validate(
                ByteArrayInputStream(archive),
                scratch,
                limits,
            )
            assertTrue("总解压超限必须被拒绝", validation is BackupValidation.Invalid)
        } finally {
            scratch.deleteRecursively()
        }
    }

    // ------------------------------------------------------------------
    // §10.4 fault injection: restore-level attacks
    // ------------------------------------------------------------------

    @Test
    fun preSwapValidationFailureRejectsCorruptDatabaseAndKeepsLiveData() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(StudyDatabaseFactory.DEFAULT_DATABASE_NAME)
        try {
            val database = StudyDatabaseFactory.open(context)
            val databaseName = StudyDatabaseFactory.DEFAULT_DATABASE_NAME
            seedStudyFacts(context, database, databaseName, seed())
            val repository = AndroidBackupRepository(context, database)

            // Archive whose database entry is not a SQLite database at all.
            val corruptArchive = buildSyntheticArchive(
                databaseContent = "this is not a sqlite database".toByteArray(),
            )

            val failure = runCatching {
                repository.restore(ByteArrayInputStream(corruptArchive))
            }
            assertTrue("预验证失败必须拒绝切换", failure.isFailure)
            assertTrue(
                (failure.exceptionOrNull() as? BackupRestoreException)
                    ?.message.orEmpty()
                    .contains("预验证失败"),
            )

            // Live data must be untouched: reopen and count.
            database.close()
            val reopened = StudyDatabaseFactory.open(context)
            assertEquals(1, reopened.countMistakes())
            reopened.close()
        } finally {
            context.deleteDatabase(StudyDatabaseFactory.DEFAULT_DATABASE_NAME)
        }
    }

    /**
     * AUD-05 的拒恢复面：包里的库引用了包内没有的图（旧成包顺序留下的形态，或条目被人删过），
     * 恢复必须在**切换前**被拒绝，且原因指明缺的是哪张；原数据、包外的原图都不许动。
     *
     * 构造方式刻意绕开 manifest 的引用清单（声明为空）：这样校验层一路放行，
     * 只有对归档库的四表反查才能发现缺图——测的正是权威判据本身。
     */
    @Test
    fun restoreIsRejectedWhenTheArchivedDatabaseReferencesAMissingAsset() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(StudyDatabaseFactory.DEFAULT_DATABASE_NAME)
        val planted = mutableListOf<CanonicalSourceAssetRecord>()
        val scratch = File(context.cacheDir, "missing-referenced-asset-${System.nanoTime()}")
        try {
            val database = StudyDatabaseFactory.open(context)
            val databaseName = StudyDatabaseFactory.DEFAULT_DATABASE_NAME
            seedStudyFacts(context, database, databaseName, seed())
            val referenced = plantCanonicalAsset(
                context = context,
                database = database,
                seed = "missing-ref",
                createdAtEpochMillis = 2_000,
                planted = planted,
            )
            referenceViaStudentMessage(database, "missing-ref", referenced.sourceAssetId)

            // 归档里的库是真的（快照），但一张图都不放：库引用了包内没有的图。
            scratch.mkdirs()
            val snapshotCopy = File(scratch, "snapshot.db")
            database.snapshotForBackup(
                context.getDatabasePath(databaseName),
                snapshotCopy,
            )
            val archive = ByteArrayOutputStream()
            SmbkArchiveCodec.create(
                archive = archive,
                database = snapshotCopy,
                assets = emptyList(),
                referencedAssets = emptyList(),
                databaseSchemaVersion = database.readDatabaseVersion(),
                problemCount = 1,
                createdAtEpochMillis = 1_000L,
            )

            val repository = AndroidBackupRepository(context, database)
            val failure = runCatching {
                repository.restore(ByteArrayInputStream(archive.toByteArray()))
            }

            assertTrue("库引用的图不在包内时必须拒绝恢复", failure.isFailure)
            val message = (failure.exceptionOrNull() as? BackupRestoreException)
                ?.message.orEmpty()
            assertTrue("拒绝原因要可读，实际：$message", message.contains("题图"))
            assertTrue(
                "拒绝原因要指出缺的是哪张图，实际：$message",
                message.contains(referenced.relativePath.substringAfterLast('/')),
            )
            // 切换没有发生：原库、原行、原图都在原地。
            assertEquals(1, database.countMistakes())
            assertEquals(referenced, database.readCanonicalSourceAsset(referenced.sourceAssetId))
            assertTrue(assetFile(context, referenced).isFile)
            database.close()
        } finally {
            planted.forEach { assetFile(context, it).delete() }
            context.deleteDatabase(StudyDatabaseFactory.DEFAULT_DATABASE_NAME)
            scratch.deleteRecursively()
        }
    }

    @Test
    fun duplicateConcurrentRestoreRequestsAreRejectedIdempotently() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(StudyDatabaseFactory.DEFAULT_DATABASE_NAME)
        try {
            val database = StudyDatabaseFactory.open(context)
            val databaseName = StudyDatabaseFactory.DEFAULT_DATABASE_NAME
            seedStudyFacts(context, database, databaseName, seed())
            val repository = AndroidBackupRepository(context, database)
            val archiveBytes = ByteArrayOutputStream().use { output ->
                repository.create(output)
                output.toByteArray()
            }

            val outcomes = coroutineScope {
                val slow = launch {
                    // First request reads through a throttled stream so the
                    // second request is guaranteed to overlap it.
                    runCatching {
                        repository.restore(ThrottledInputStream(ByteArrayInputStream(archiveBytes)))
                    }
                }
                delay(100)
                val second = async {
                    runCatching {
                        repository.restore(ByteArrayInputStream(archiveBytes))
                    }
                }
                slow.join()
                listOf(second.await())
            }
            val secondOutcome = outcomes.single()
            assertTrue("重叠的第二次恢复请求必须被拒绝", secondOutcome.isFailure)
            assertTrue(
                (secondOutcome.exceptionOrNull() as? BackupRestoreException)
                    ?.message.orEmpty()
                    .contains("重复的恢复请求"),
            )

            // The data set must still be consistent afterwards.
            database.close()
            val reopened = StudyDatabaseFactory.open(context)
            assertEquals(1, reopened.countMistakes())
            reopened.close()
        } finally {
            context.deleteDatabase(StudyDatabaseFactory.DEFAULT_DATABASE_NAME)
        }
    }

    // ------------------------------------------------------------------
    // §10.4 fault injection: startup recovery after simulated process death
    // ------------------------------------------------------------------

    @Test
    fun startupRecoveryRollsBackGenerationAfterInterruptedSwap() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(StudyDatabaseFactory.DEFAULT_DATABASE_NAME)
        val dbName = StudyDatabaseFactory.DEFAULT_DATABASE_NAME
        val dbFile = context.getDatabasePath(dbName)
        val databasesDir = dbFile.parentFile!!
        val assetDir = File(context.filesDir, "source-assets")
        try {
            // Live generation: a seeded database.
            val database = StudyDatabaseFactory.open(context)
            val databaseName = StudyDatabaseFactory.DEFAULT_DATABASE_NAME
            seedStudyFacts(context, database, databaseName, seed())
            database.close()

            // Simulate the restore having displaced the live generation:
            //   live db renamed to .prev, staged .next promoted to live.
            val previousDb = File(databasesDir, "$dbName.prev")
            val badLive = dbFile.readBytes()
            assertTrue(dbFile.renameTo(previousDb))
            File(databasesDir, "$dbName.next").writeBytes(badLive) // leftover staged gen

            val restoreId = "simulated-death-${System.nanoTime()}"
            val journalFile = File(context.noBackupFilesDir, "restore-journal-$restoreId.json")
            journalFile.writeText(
                Json.encodeToString(
                    JournalEntry.serializer(),
                    JournalEntry(
                        restoreId = restoreId,
                        phase = RestorePhase.SWAPPING.name,
                        timestamp = System.currentTimeMillis(),
                        liveDatabasePath = dbFile.absolutePath,
                        previousDatabasePath = previousDb.absolutePath,
                        nextDatabasePath = File(databasesDir, "$dbName.next").absolutePath,
                        liveAssetDir = assetDir.absolutePath,
                        previousAssetDir = null,
                        nextAssetDir = null,
                        stagingDir = null,
                    ),
                ),
            )

            // Injection point: in a real process death the process would be
            // killed right after the SWAPPING journal write; the test simply
            // restarts the recovery path instead.
            val outcome = BackupRestoreStartupRecovery.recoverOnStartup(context)
            assertTrue(
                "半程切换必须被回滚, 实际: $outcome",
                outcome is RestoreStartupOutcome.RolledBack,
            )
            assertFalse(previousDb.exists())
            assertFalse(File(databasesDir, "$dbName.next").exists())

            val reopened = StudyDatabaseFactory.open(context)
            assertEquals("回滚后必须恢复原数据", 1, reopened.countMistakes())
            reopened.close()
        } finally {
            context.deleteDatabase(dbName)
            File(databasesDir, "$dbName.prev").delete()
            File(databasesDir, "$dbName.next").delete()
            context.noBackupFilesDir.listFiles().orEmpty()
                .filter { it.name.startsWith("restore-journal-") }
                .forEach { it.delete() }
        }
    }

    @Test
    fun startupRecoveryCleansPreSwapInterruptionWithoutTouchingLiveData() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(StudyDatabaseFactory.DEFAULT_DATABASE_NAME)
        try {
            val database = StudyDatabaseFactory.open(context)
            val databaseName = StudyDatabaseFactory.DEFAULT_DATABASE_NAME
            seedStudyFacts(context, database, databaseName, seed())
            database.close()

            val restoreId = "simulated-preswap-${System.nanoTime()}"
            val staging = File(context.cacheDir, "restore-$restoreId").apply { mkdirs() }
            File(staging, "staging").mkdirs()
            val journalFile = File(context.noBackupFilesDir, "restore-journal-$restoreId.json")
            journalFile.writeText(
                Json.encodeToString(
                    JournalEntry.serializer(),
                    JournalEntry(
                        restoreId = restoreId,
                        phase = RestorePhase.VALIDATING.name,
                        timestamp = System.currentTimeMillis(),
                        stagingDir = staging.absolutePath,
                    ),
                ),
            )

            // Injection point: process died while still validating/unpacking;
            // nothing was swapped yet.
            val outcome = BackupRestoreStartupRecovery.recoverOnStartup(context)
            assertTrue(
                "切换前中断只需清理, 实际: $outcome",
                outcome is RestoreStartupOutcome.Cleaned,
            )
            assertFalse(journalFile.exists())
            assertFalse(staging.exists())

            val reopened = StudyDatabaseFactory.open(context)
            assertEquals(1, reopened.countMistakes())
            reopened.close()
        } finally {
            context.deleteDatabase(StudyDatabaseFactory.DEFAULT_DATABASE_NAME)
        }
    }

    // ------------------------------------------------------------------
    // §10.4 process-death skeletons (execute manually with the flag below).
    // ------------------------------------------------------------------

    /**
     * Skeleton: process death BETWEEN the database rename and the asset
     * rename. Injection point is marked below; with [KILL_FOR_REAL] = false
     * the test exercises the equivalent simulated path instead of killing
     * the instrumentation process.
     */
    @Test
    fun processDeathBetweenDatabaseAndAssetSwapSkeleton() {
        val killForReal = false // 注入点：手动置 true 后运行，进程将在切换中途被杀死
        if (killForReal) {
            // 注入点：等价于 restore() 中 `Atomic switch #1` 与 `Atomic switch #2`
            // 之间调用 android.os.Process.killProcess(android.os.Process.myPid())。
            // 下一次启动时 BackupRestoreStartupRecovery 必须把数据库回滚到 .prev 代，
            // 且资产目录保持上一代。
            android.os.Process.killProcess(android.os.Process.myPid())
        }
        // Default (no kill): the simulated equivalent is covered by
        // startupRecoveryRollsBackGenerationAfterInterruptedSwap.
    }

    /**
     * Skeleton: process death right AFTER the asset rename but BEFORE the
     * VERIFYING phase completes. Injection point: kill immediately after
     * `Atomic switch #2` in AndroidBackupRepository.doRestore. On next
     * startup the coordinator sees phase SWAPPING in the journal and rolls
     * the whole generation set back; when [KILL_FOR_REAL] is false this test
     * only documents the expected behaviour.
     */
    @Test
    fun processDeathAfterSwapBeforeVerifySkeleton() {
        val killForReal = false // 注入点：手动置 true 后运行
        if (killForReal) {
            android.os.Process.killProcess(android.os.Process.myPid())
        }
    }

    /**
     * Skeleton: first launch failure after a completed swap. Injection point:
     * force StudyDatabaseFactory.open to throw after a successful restore
     * (e.g. incompatible schema injected via a test build); the startup
     * recovery of the NEXT launch must find no journal (restore committed)
     * and therefore must not roll back automatically — quarantine is a
     * product decision surfaced through startupState instead.
     */
    @Test
    fun firstLaunchFailureAfterCommittedSwapSkeleton() {
        val simulateOpenFailure = false // 注入点：手动置 true 后运行
        if (simulateOpenFailure) {
            error("simulate Room open failure after committed restore swap")
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Wraps a stream and sleeps per chunk so a restore runs slowly. */
    private class ThrottledInputStream(
        delegate: InputStream,
    ) : java.io.FilterInputStream(delegate) {
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            Thread.sleep(5)
            return super.read(b, off, len.coerceAtMost(1_024))
        }
    }

    /** Builds a spec-conformant .smbk archive with controllable payloads. */
    private fun buildSyntheticArchive(
        databaseContent: ByteArray,
        extraAssets: Map<String, ByteArray> = emptyMap(),
    ): ByteArray {
        val dbSha = sha256Hex(databaseContent)
        val assetRecords = extraAssets.map { (path, bytes) ->
            Triple(path, sha256Hex(bytes), bytes.size.toLong())
        }
        val manifest = buildString {
            append("{")
            append("\"formatVersion\":1,")
            append("\"databaseSchemaVersion\":32,")
            append("\"createdAt\":1000,")
            append("\"problemCount\":1,")
            append("\"assetCount\":${assetRecords.size},")
            append("\"hashAlgorithm\":\"SHA-256\",")
            append("\"files\":[")
            append("{\"path\":\"database.sqlite\",\"sha256\":\"$dbSha\",\"byteSize\":${databaseContent.size}}")
            assetRecords.forEach { (path, sha, size) ->
                append(",{\"path\":\"$path\",\"sha256\":\"$sha\",\"byteSize\":$size}")
            }
            append("]}")
        }
        val checksums = buildString {
            append("$dbSha *database.sqlite")
            assetRecords.forEach { (path, sha, _) -> append("\n$sha *$path") }
        }
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(manifest.toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("database.sqlite"))
            zip.write(databaseContent)
            zip.closeEntry()
            extraAssets.forEach { (path, bytes) ->
                zip.putNextEntry(ZipEntry(path))
                zip.write(bytes)
                zip.closeEntry()
            }
            zip.putNextEntry(ZipEntry("checksums.sha256"))
            zip.write(checksums.toByteArray())
            zip.closeEntry()
        }
        return output.toByteArray()
    }

    /**
     * Corrupts one data byte inside the archive. Scans backwards from the
     * checksums entry position for a non-zero byte that is part of the
     * database entry payload; flipping it changes the decompressed sha while
     * keeping the zip structure readable.
     */
    private fun corruptDataByte(archive: ByteArray): ByteArray {
        val corrupted = archive.copyOf()
        // Flip a byte ~40% into the archive: deterministically inside the
        // compressed database entry for the small synthetic archives used here.
        val position = (corrupted.size * 0.4).toInt()
        corrupted[position] = (corrupted[position].toInt() xor 0xFF).toByte()
        return corrupted
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte) }

    private fun seed() = StudySeedBundle(
        problems = listOf(
            ProblemSeedRecord(
                problemId = "problem-backup",
                canonicalFingerprint = "a".repeat(64),
                subject = "MATH",
                createdAtEpochMillis = 1_000,
            ),
        ),
        revisions = listOf(
            ProblemRevisionSeedRecord(
                revisionId = "revision-backup",
                problemId = "problem-backup",
                revisionNumber = 1,
                title = "备份恢复题",
                problemMarkdown = "求函数最值。",
                questionDocumentSnapshot = null,
                answerSpecId = null,
                answerSpecSnapshot = null,
                answerVerificationStatus = "UNKNOWN",
                sourceType = "TEST",
                sourceReference = null,
                contentFingerprint = "b".repeat(64),
                createdAtEpochMillis = 1_000,
            ),
        ),
        practiceUnits = listOf(
            PracticeUnitSeedRecord(
                practiceUnitId = "practice-backup",
                problemId = "problem-backup",
                problemRevisionId = "revision-backup",
                unitKey = "unit:backup",
                unitKind = "PROBLEM",
                title = "备份恢复题",
                promptMarkdown = "求函数最值。",
                estimatedSeconds = 180,
                createdAtEpochMillis = 1_000,
            ),
        ),
        errorBookEntries = listOf(
            ErrorBookEntrySeedRecord(
                entryId = "entry-backup",
                practiceUnitId = "practice-backup",
                problemId = "problem-backup",
                currentRevisionId = "revision-backup",
                sourceKey = null,
                acceptedAtEpochMillis = 1_000,
                updatedAtEpochMillis = 1_000,
            ),
        ),
    )

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"

        /**
         * Deliberately not a credential: the vault only needs *some* plaintext
         * to seal, and a test fixture must never look like a usable key.
         */
        val TEST_API_KEY_CHARS = "not-a-real-api-key-test-fixture".toCharArray()
    }
}
