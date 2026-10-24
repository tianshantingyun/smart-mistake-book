package com.tingyun.smartmistakebook.core.data.backup

import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import androidx.core.app.NotificationManagerCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tingyun.smartmistakebook.core.data.settings.DataStoreReviewReminderRepository
import com.tingyun.smartmistakebook.core.data.settings.DataStoreSchedulingSettingsStore
import com.tingyun.smartmistakebook.core.data.study.seedStudyFacts
import com.tingyun.smartmistakebook.core.database.ErrorBookEntrySeedRecord
import com.tingyun.smartmistakebook.core.database.KnowledgeNodeSeedRecord
import com.tingyun.smartmistakebook.core.database.KnowledgeSearchFeatureExtractor
import com.tingyun.smartmistakebook.core.database.PracticeUnitSeedRecord
import com.tingyun.smartmistakebook.core.database.ProblemRevisionSeedRecord
import com.tingyun.smartmistakebook.core.database.ProblemSeedRecord
import com.tingyun.smartmistakebook.core.database.StudyDatabaseFactory
import com.tingyun.smartmistakebook.core.database.StudySeedBundle
import com.tingyun.smartmistakebook.core.domain.BackupContentCategory
import com.tingyun.smartmistakebook.core.domain.BackupOptions
import com.tingyun.smartmistakebook.core.domain.BackupSelection
import com.tingyun.smartmistakebook.core.domain.ExamCalendarEntry
import com.tingyun.smartmistakebook.core.domain.RestoreSettingsOutcome
import com.tingyun.smartmistakebook.core.domain.ReviewReminderDelivery
import com.tingyun.smartmistakebook.core.domain.ReviewReminderPreferences
import com.tingyun.smartmistakebook.core.domain.SchedulingOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * D1 换机演练（6C 退出门唯一未做项，批次 19 设备轨）。
 *
 * 每条 mask（全量 15 / 子集 9 / 子集 13）走一遍完整换机链：
 * **备份 → 清数据（[deleteAllData]）→ 恢复**，随后逐项对拍：
 *  1. 逐表 COUNT 对拍（Phase 2c.1 同口径：恢复库实测行数 == 清单 `selection.rowCounts`
 *     的逐表声明；mask=15 也比，没有"全量免检"）；
 *  2. 设置往返：保持率 / 考试日历 / 提醒偏好三组键经 `settings.json` 往返；
 *  3. Key/租约不入包：归档条目白名单 + `settings.json` 字段集恰为偏好六键，
 *     模型 API Key（Keystore/保险库）与授权租约、投递历史一律不随包；
 *  4. 提醒不复活旧授权：系统通知授权位恢复前后原样；旧设备的"今天已提醒过"
 *     投递台账不搬家（新设备上同一天仍可认领）；
 *  5. 恢复后派生索引两张表恒为 0、待重建标记在场，重开后按科**自重建完成**
 *     （两科都重建、标记被消费、检索可用）；
 *  6. 恢复失败必须停在交换前：篡改清单的选择段后恢复被拒，原库不动、
 *     无半库（无 `.prev`/`.next` 残留）、无标记；
 *  7. 含一次**真实杀进程**后恢复：交接件落盘 → `killProcess` → 新进程读交接件
 *     清数据后恢复成功（见 [killProcessHandoffStagesArchiveThenDies] 与
 *     [restoreFromHandoffAfterProcessDeath]，分两次 `am instrument` 调用执行）。
 *
 * 与 [BackupSelectionRestoreInstrumentedTest] 的分工：那边钉"非法包在交换前被拒"的
 * 校验矩阵；本类钉"合法包在换机语义（清数据后恢复）下的端到端对拍"。
 *
 * 设备守卫：会调 [AndroidBackupRepository.deleteAllData]（清测试应用的数据目录），
 * 与 app 模块 `DestructiveDeviceTestGuard` 同参 `smb_allow_data_reset`——默认跳过，
 * 只在专用测试设备显式开启（core:data 不能依赖 app 模块，守卫按同一参数名内联）。
 */
@RunWith(AndroidJUnit4::class)
class DeviceMigrationDrillInstrumentedTest {

    private lateinit var context: Context
    private val databaseName = StudyDatabaseFactory.DEFAULT_DATABASE_NAME
    private val cleanupJobs = mutableListOf<Job>()
    private val cleanupRoots = mutableListOf<File>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        requireExplicitDataResetOptIn()
        context.deleteDatabase(databaseName)
        RestoreIndexRebuildMarker.clear(context)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(databaseName)
        RestoreIndexRebuildMarker.clear(context)
        cleanupJobs.forEach { job ->
            job.cancel()
            runBlocking { job.join() }
        }
        cleanupRoots.forEach { it.deleteRecursively() }
    }

    // ------------------------------------------------------------------
    // 三条 mask 的换机演练
    // ------------------------------------------------------------------

    @Test
    fun fullMaskFifteenMigrationDrillRoundTripsEveryTableAndSettings() = runBlocking {
        drillMigration(
            selection = BackupSelection.FULL,
            expectTutorConversations = 1L,
        )
    }

    @Test
    fun subsetMaskNineMigrationDrillDropsSessionsAndRebuildsBothSubjects() = runBlocking {
        drillMigration(
            selection = BackupSelection.of(
                setOf(BackupContentCategory.ISSUE_BOOK, BackupContentCategory.IMAGES),
            ),
            expectTutorConversations = 0L,
        )
    }

    @Test
    fun subsetMaskThirteenMigrationDrillKeepsSessionsAndSettings() = runBlocking {
        drillMigration(
            selection = BackupSelection.of(
                setOf(
                    BackupContentCategory.ISSUE_BOOK,
                    BackupContentCategory.SESSIONS,
                    BackupContentCategory.IMAGES,
                ),
            ),
            expectTutorConversations = 1L,
        )
    }

    // ------------------------------------------------------------------
    // 失败停在交换前（不落半库）
    // ------------------------------------------------------------------

    /**
     * 换机链上的失败面：一份清单选择段被改坏的包，恢复必须在交换前被拒——
     * 换机语义里此刻"原库"就是清数据后刚恢复/仍在的库，失败后它必须原样可读，
     * 且不留下 `.prev`/`.next` 代际残件或待重建标记（半库）。
     */
    @Test
    fun corruptedSelectionStopsBeforeSwapLeavingNoHalfDatabase() = runBlocking {
        val database = openSeededDatabase()
        insertTutorConversation()
        val archive = createArchive(database, BackupSelection.of(issueBookAndImages()))
        database.close()

        val tamperedManifest = JSONObject(manifestJson(archive)).apply {
            getJSONObject("selection").put("closureDigest", "0".repeat(64))
        }
        val tampered = rewriteManifest(archive, tamperedManifest.toString())

        val freshDatabase = StudyDatabaseFactory.open(context, databaseName)
        try {
            val failure = assertThrows(BackupRestoreException::class.java) {
                runBlocking {
                    AndroidBackupRepository(context, freshDatabase)
                        .restore(ByteArrayInputStream(tampered))
                }
            }
            assertTrue(
                "拒因必须指向备份内容选择：${failure.message}",
                failure.message.orEmpty().contains("备份内容选择"),
            )
            assertEquals("拒绝后原库必须原样可读（两科各一题）", 2, freshDatabase.countMistakes())
            val base = context.getDatabasePath(databaseName).absolutePath
            assertFalse("不得留下上一代数据库", File("$base.prev").exists())
            assertFalse("不得留下下一代数据库", File("$base.next").exists())
            assertFalse("不得留下待重建标记", RestoreIndexRebuildMarker.exists(context))
        } finally {
            runCatching { freshDatabase.close() }
        }
    }

    // ------------------------------------------------------------------
    // 真实杀进程交接（两阶段，分两次 am instrument 调用）
    // ------------------------------------------------------------------

    /**
     * 阶段 A（真实杀进程）：旧设备完成备份、把包写到交接目录后**进程死亡**。
     * 只在 `-e smb_d1_kill_stage true` 时执行；本次 instrument 调用会随进程死亡而
     * 非零退出——这是本用例的预期产物，随后用 [restoreFromHandoffAfterProcessDeath]
     * 在新进程里完成恢复对拍。
     */
    @Test
    fun killProcessHandoffStagesArchiveThenDies() = runBlocking {
        assumeTrue(
            "真实杀进程阶段只在显式 -e $KILL_STAGE_ARGUMENT true 时执行（预期本次调用随进程死亡非零退出）",
            killStageEnabled(),
        )
        val database = openSeededDatabase()
        insertTutorConversation()
        val archive = createArchive(database, BackupSelection.of(issueBookAndImages()))
        database.close()

        val handoff = handoffArchiveFile()
        handoff.parentFile?.mkdirs()
        handoff.outputStream().use { output -> output.write(archive) }
        assertTrue("交接件必须已落盘（杀进程后仍要在）", handoff.isFile && handoff.length() > 0L)

        // 旧设备进程死亡：等价于换机里"备份完成后设备离线"。
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    /**
     * 阶段 B（杀进程后恢复）：新进程读交接件 → 清数据 → 恢复 → mask=9 对拍。
     * 交接件不在（未跑阶段 A）时按 JUnit 假设跳过，不冒充通过。
     */
    @Test
    fun restoreFromHandoffAfterProcessDeath() = runBlocking {
        val handoff = handoffArchiveFile()
        assumeTrue(
            "杀进程交接件不存在：先以 -e $KILL_STAGE_ARGUMENT true 跑 killProcessHandoffStagesArchiveThenDies",
            handoff.isFile,
        )
        val archive = handoff.readBytes()
        val manifest = JSONObject(manifestJson(archive))
        assertEquals("交接件必须是 mask=9 的子集包", 9, manifest.getJSONObject("selection").getInt("categoryMask"))
        val declaredCounts = rowCountsOf(manifest.getJSONObject("selection"))

        // 清数据（换机：新设备先回到空态）。
        val wiper = AndroidBackupRepository(context, StudyDatabaseFactory.open(context, databaseName))
        wiper.deleteAllData()
        assertFalse(context.getDatabasePath(databaseName).exists())

        val receipt = AndroidBackupRepository(
            context,
            StudyDatabaseFactory.open(context, databaseName),
        ).restore(ByteArrayInputStream(archive))
        assertEquals(9, receipt.selection?.mask)
        assertEquals(2, receipt.problemCount)

        val restoredFile = context.getDatabasePath(databaseName)
        assertRestoredCountsMatchManifest(restoredFile, declaredCounts)
        assertEquals("mask=9 未勾选会话：恢复库必须裁净", 0L, rowCount(restoredFile, "tutor_conversation"))
        assertEquals(0L, rowCount(restoredFile, "knowledge_search_feature"))
        assertEquals(0L, rowCount(restoredFile, "knowledge_search_index_state"))
        assertTrue(RestoreIndexRebuildMarker.exists(context))

        val reopened = StudyDatabaseFactory.open(context, databaseName)
        try {
            val outcome = RestoreIndexRebuildCoordinator.rebuildPending(context, reopened)
            assertEquals(RestoreIndexRebuildStatus.REBUILT, outcome.status)
            assertEquals(listOf("MATH", "PHYSICS"), outcome.rebuiltSubjects)
            assertFalse(RestoreIndexRebuildMarker.exists(context))
        } finally {
            reopened.close()
        }
    }

    // ------------------------------------------------------------------
    // 演练主体
    // ------------------------------------------------------------------

    private suspend fun drillMigration(selection: BackupSelection, expectTutorConversations: Long) {
        val oldDeviceRoot = isolatedRoot("old")
        val oldScheduling = DataStoreSchedulingSettingsStore(
            IsolatedStorageContext(context, oldDeviceRoot),
            newScope(),
        )
        val oldReminders = DataStoreReviewReminderRepository(
            IsolatedStorageContext(context, oldDeviceRoot),
            newScope(),
        )

        // 旧设备：两科种子 + 一条会话 + 已建好的派生索引 + 偏好。
        val database = openSeededDatabase()
        insertTutorConversation()
        insertDerivedIndexRows()
        val backupExams = listOf(
            ExamCalendarEntry(
                entryId = "exam-d1-math",
                subject = "MATH",
                examEpochDay = 20_700,
                title = "数学期末",
            ),
            ExamCalendarEntry(
                entryId = "exam-d1-physics",
                subject = "PHYSICS",
                examEpochDay = 20_710,
                title = "物理期末",
            ),
        )
        oldScheduling.setOptions(SchedulingOptions(0.9))
        backupExams.forEach { oldScheduling.addExam(it) }
        oldReminders.setEnabled(true)
        oldReminders.setReminderTime(18 * 60 + 30)
        val deliveredOnOldDevice = ReviewReminderDelivery(
            localEpochDay = 20_655,
            zoneId = "Asia/Shanghai",
        )
        assertTrue(
            "夹具必须让旧设备的投递认领成功一次（形成'那天已提醒'台账）",
            oldReminders.claimNotificationDelivery(deliveredOnOldDevice),
        )

        val archive = createArchive(
            database,
            selection,
            schedulingSettings = oldScheduling,
            reviewReminders = oldReminders,
        )
        val manifest = JSONObject(manifestJson(archive))
        val declaredCounts = rowCountsOf(manifest.getJSONObject("selection"))
        assertEquals("清单声明的 mask 必须与请求一致", selection.mask, manifest.getJSONObject("selection").getInt("categoryMask"))

        // Key/租约不入包：条目白名单 + 设置字段恰为偏好六键。
        assertPackageCarriesPreferencesOnly(archive)

        // 清数据（旧设备退役：本地一切清空；设置 DataStore 的隔离根随 cacheDir 一并清除）。
        AndroidBackupRepository(context, database).deleteAllData()
        assertFalse("清数据后库文件必须不存在", context.getDatabasePath(databaseName).exists())
        assertFalse("清数据后不得残留待重建标记", RestoreIndexRebuildMarker.exists(context))

        // 新设备：全新 DataStore（默认值）+ 记录系统通知授权位。
        val newDeviceRoot = isolatedRoot("new")
        val newScheduling = DataStoreSchedulingSettingsStore(
            IsolatedStorageContext(context, newDeviceRoot),
            newScope(),
        )
        val newReminders = DataStoreReviewReminderRepository(
            IsolatedStorageContext(context, newDeviceRoot),
            newScope(),
        )
        assertEquals("新设备首启：无考试日历", emptyList<ExamCalendarEntry>(), newScheduling.exams.first())
        assertFalse("新设备首启：提醒默认关", newReminders.current().enabled)
        val notificationsAuthorizedBeforeRestore =
            NotificationManagerCompat.from(context).areNotificationsEnabled()

        // 恢复（换机：把旧设备的包恢复进新设备空态）。
        val receipt = AndroidBackupRepository(
            context,
            StudyDatabaseFactory.open(context, databaseName),
            newScheduling,
            newReminders,
        ).restore(ByteArrayInputStream(archive))
        assertEquals(selection.mask, receipt.selection?.mask)
        assertEquals("设置必须完整写入", RestoreSettingsOutcome.APPLIED, receipt.settings)
        assertEquals(2, receipt.problemCount)

        // 提醒不复活旧授权：系统通知授权位原样（偏好恢复 ≠ 授权复活）。
        assertEquals(
            "恢复只写偏好，不复活也不改动系统通知授权",
            notificationsAuthorizedBeforeRestore,
            NotificationManagerCompat.from(context).areNotificationsEnabled(),
        )
        // 设置往返：保持率 / 考试日历 / 提醒偏好。
        assertEquals(0.9, newScheduling.options.first().desiredRetention, 1e-9)
        assertEquals(
            backupExams.sortedBy { it.entryId },
            newScheduling.exams.first().sortedBy { it.entryId },
        )
        assertEquals(
            ReviewReminderPreferences(enabled = true, minutesAfterMidnight = 18 * 60 + 30),
            newReminders.current(),
        )
        assertTrue(
            "投递历史不入包：旧设备的'那天已提醒过'不得搬到新设备（同一天仍可认领）",
            newReminders.claimNotificationDelivery(deliveredOnOldDevice),
        )

        // 逐表 COUNT 对拍（mask=15 同样比，无全量免检）。
        val restoredFile = context.getDatabasePath(databaseName)
        assertTrue(restoredFile.isFile)
        assertRestoredCountsMatchManifest(restoredFile, declaredCounts)
        assertEquals("会话按 mask 保留/裁净", expectTutorConversations, rowCount(restoredFile, "tutor_conversation"))
        assertEquals("派生索引恢复侧恒裁", 0L, rowCount(restoredFile, "knowledge_search_feature"))
        assertEquals("派生索引恢复侧恒裁", 0L, rowCount(restoredFile, "knowledge_search_index_state"))
        assertTrue("恢复后必须留下待重建标记", RestoreIndexRebuildMarker.exists(context))

        // 派生索引自重建完成：两科都重建、标记被消费、每科检索可用。
        val reopened = StudyDatabaseFactory.open(context, databaseName)
        try {
            val outcome = RestoreIndexRebuildCoordinator.rebuildPending(context, reopened)
            assertEquals(RestoreIndexRebuildStatus.REBUILT, outcome.status)
            assertEquals(listOf("MATH", "PHYSICS"), outcome.rebuiltSubjects)
            assertFalse("重建成功后标记必须清除", RestoreIndexRebuildMarker.exists(context))
            assertEquals(2L, rowCount(restoredFile, "knowledge_search_index_state"))
            assertTrue(rowCount(restoredFile, "knowledge_search_feature") >= 2L)

            val recalledMath = reopened.readSubjectKnowledgeRecallCandidates(
                subject = "MATH",
                searchFeatures = KnowledgeSearchFeatureExtractor.fromQuestion("函数"),
                limit = 10,
            )
            assertTrue(
                "重建后 MATH 必须能检索到恢复进来的知识点，实际 ${recalledMath.map { it.knowledgeNodeId }}",
                recalledMath.any { it.knowledgeNodeId == MATH_KNOWLEDGE_NODE_ID },
            )
            val recalledPhysics = reopened.readSubjectKnowledgeRecallCandidates(
                subject = "PHYSICS",
                searchFeatures = KnowledgeSearchFeatureExtractor.fromQuestion("力学"),
                limit = 10,
            )
            assertTrue(
                "重建后 PHYSICS 必须能检索到恢复进来的知识点，实际 ${recalledPhysics.map { it.knowledgeNodeId }}",
                recalledPhysics.any { it.knowledgeNodeId == PHYSICS_KNOWLEDGE_NODE_ID },
            )
        } finally {
            reopened.close()
        }
    }

    // ------------------------------------------------------------------
    // 对拍与断言助手
    // ------------------------------------------------------------------

    /**
     * Phase 2c.1 同口径的逐表对拍，但对象换成**恢复后的库**：对拍范围是 schema 机器清单
     * [SmbkSelectionPlan.allTables]（86 张，`library_search_fts` 虚拟表本体在内、其 FTS4
     * 影子表是模块内部件不在清单）∪ 清单声明键；表不在恢复库里按实测 0 对拍。
     * 另加一个方向：恢复库里凡**既不在机器清单、也不是虚拟表影子/Room 元数据**的用户表
     * 必须为 0 行（堵"夹带一张清单外的表"）。
     */
    private fun assertRestoredCountsMatchManifest(restoredFile: File, declaredCounts: Map<String, Long>) {
        SQLiteDatabase.openDatabase(
            restoredFile.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY,
        ).use { database ->
            val actualTables = mutableSetOf<String>()
            database.rawQuery(
                "SELECT name FROM sqlite_master WHERE type = 'table' " +
                    "AND name NOT LIKE 'sqlite_%' " +
                    "AND name NOT IN ('android_metadata', 'room_master_table')",
                null,
            ).use { cursor ->
                while (cursor.moveToNext()) actualTables += cursor.getString(0)
            }
            val virtualTableShadows = mutableSetOf<String>()
            database.rawQuery(
                "SELECT name FROM sqlite_master WHERE type = 'table' " +
                    "AND sql LIKE 'CREATE VIRTUAL TABLE%'",
                null,
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val virtualName = cursor.getString(0)
                    virtualTableShadows += virtualName
                    actualTables.filter { it.startsWith("${virtualName}_") }
                        .forEach { virtualTableShadows += it }
                }
            }

            val planTables = SmbkSelectionPlan.allTables
            assertTrue(
                "夹带审计前提：机器清单必须覆盖清单声明键",
                declaredCounts.keys.all { it in planTables },
            )
            val mismatched = planTables.mapNotNull { table ->
                val quoted = "\"" + table.replace("\"", "\"\"") + "\""
                val actual = if (table in actualTables) {
                    database.rawQuery("SELECT COUNT(*) FROM $quoted", null).use { cursor ->
                        assertTrue("无法读取恢复库表 $table 的行数", cursor.moveToFirst())
                        cursor.getLong(0)
                    }
                } else {
                    0L
                }
                val declared = declaredCounts[table] ?: 0L
                if (declared != actual) "$table declared=$declared actual=$actual" else null
            }
            assertTrue(
                "换机对拍：恢复库逐表行数必须与清单声明一致（机器清单 ${planTables.size} 张表），" +
                    "不符：${mismatched.take(8).joinToString("、")}" +
                    if (mismatched.size > 8) "…共 ${mismatched.size} 张" else "",
                mismatched.isEmpty(),
            )

            val smuggled = actualTables
                .filter { it !in planTables && it !in virtualTableShadows }
                .mapNotNull { table ->
                    val quoted = "\"" + table.replace("\"", "\"\"") + "\""
                    val count = database.rawQuery("SELECT COUNT(*) FROM $quoted", null).use { cursor ->
                        assertTrue(cursor.moveToFirst())
                        cursor.getLong(0)
                    }
                    if (count > 0L) "$table=$count" else null
                }
            assertTrue(
                "恢复库不得携带机器清单与虚拟表影子之外的非空表：${smuggled.joinToString("、")}",
                smuggled.isEmpty(),
            )
        }
    }

    /** 归档条目白名单 + settings.json 字段集恰为偏好六键（Key/租约/投递历史不入包）。 */
    private fun assertPackageCarriesPreferencesOnly(archive: ByteArray) {
        val allowedEntry = { name: String ->
            name == SmbkArchiveCodec.MANIFEST_ENTRY ||
                name == SmbkArchiveCodec.DATABASE_ENTRY ||
                name == SmbkArchiveCodec.SETTINGS_ENTRY ||
                name == CHECKSUMS_ENTRY ||
                name.startsWith("assets/")
        }
        var sawSettings = false
        ZipInputStream(ByteArrayInputStream(archive)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                assertTrue(
                    "归档条目必须出自白名单（数据库/清单/设置/校验和/资产）：${entry.name}",
                    allowedEntry(entry.name),
                )
                if (entry.name == SmbkArchiveCodec.SETTINGS_ENTRY) {
                    sawSettings = true
                    val raw = zip.readBytes().toString(Charsets.UTF_8)
                    val settings = JSONObject(raw)
                    val keys = mutableSetOf<String>()
                    val iterator = settings.keys()
                    while (iterator.hasNext()) keys += iterator.next()
                    assertEquals(
                        "settings.json 只允许携带偏好六键（保持率/考试日历/FSRS 参数/提醒两键/版本）",
                        PREFERENCE_ONLY_SETTINGS_KEYS,
                        keys,
                    )
                    listOf("apiKey", "api_key", "lease", "secret", "delivery", "credential").forEach { marker ->
                        assertFalse(
                            "settings.json 不得出现 $marker 类载荷（Key/租约/投递历史不入包）",
                            raw.contains(marker, ignoreCase = true),
                        )
                    }
                }
                zip.closeEntry()
            }
        }
        assertTrue("成包装了设置口时包内必须有 settings.json", sawSettings)
    }

    // ------------------------------------------------------------------
    // 夹具
    // ------------------------------------------------------------------

    private fun openSeededDatabase() =
        StudyDatabaseFactory.open(context, databaseName).also { database ->
            seedStudyFacts(context, database, databaseName, drillSeedBundle())
        }

    /** 两科各一题一知识点（派生索引自重建要对"按科"有判据）+ 一条错题。 */
    private fun drillSeedBundle() = StudySeedBundle(
        problems = listOf(
            ProblemSeedRecord(
                problemId = "problem-d1-math",
                canonicalFingerprint = "a".repeat(64),
                subject = "MATH",
                createdAtEpochMillis = 1_000,
            ),
            ProblemSeedRecord(
                problemId = "problem-d1-physics",
                canonicalFingerprint = "c".repeat(64),
                subject = "PHYSICS",
                createdAtEpochMillis = 1_000,
            ),
        ),
        revisions = listOf(
            ProblemRevisionSeedRecord(
                revisionId = "revision-d1-math",
                problemId = "problem-d1-math",
                revisionNumber = 1,
                title = "数学换机题",
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
            ProblemRevisionSeedRecord(
                revisionId = "revision-d1-physics",
                problemId = "problem-d1-physics",
                revisionNumber = 1,
                title = "物理换机题",
                problemMarkdown = "分析斜面受力。",
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
                practiceUnitId = "practice-d1-math",
                problemId = "problem-d1-math",
                problemRevisionId = "revision-d1-math",
                unitKey = "unit:d1-math",
                unitKind = "PROBLEM",
                title = "数学换机题",
                promptMarkdown = "求函数最值。",
                estimatedSeconds = 180,
                createdAtEpochMillis = 1_000,
            ),
            PracticeUnitSeedRecord(
                practiceUnitId = "practice-d1-physics",
                problemId = "problem-d1-physics",
                problemRevisionId = "revision-d1-physics",
                unitKey = "unit:d1-physics",
                unitKind = "PROBLEM",
                title = "物理换机题",
                promptMarkdown = "分析斜面受力。",
                estimatedSeconds = 180,
                createdAtEpochMillis = 1_000,
            ),
        ),
        errorBookEntries = listOf(
            ErrorBookEntrySeedRecord(
                entryId = "entry-d1-math",
                practiceUnitId = "practice-d1-math",
                problemId = "problem-d1-math",
                currentRevisionId = "revision-d1-math",
                sourceKey = null,
                acceptedAtEpochMillis = 1_000,
                updatedAtEpochMillis = 1_000,
            ),
            ErrorBookEntrySeedRecord(
                entryId = "entry-d1-physics",
                practiceUnitId = "practice-d1-physics",
                problemId = "problem-d1-physics",
                currentRevisionId = "revision-d1-physics",
                sourceKey = null,
                acceptedAtEpochMillis = 1_000,
                updatedAtEpochMillis = 1_000,
            ),
        ),
        knowledgeNodes = listOf(
            KnowledgeNodeSeedRecord(
                knowledgeNodeId = MATH_KNOWLEDGE_NODE_ID,
                stableCode = "MATH.FUNCTION",
                subject = "MATH",
                displayName = "函数",
                parentKnowledgeNodeId = null,
                taxonomyVersion = "test-v1",
                createdAtEpochMillis = 1_000,
                canonicalName = "函数",
                verificationStatus = "CURATED",
            ),
            KnowledgeNodeSeedRecord(
                knowledgeNodeId = PHYSICS_KNOWLEDGE_NODE_ID,
                stableCode = "PHYSICS.MECHANICS",
                subject = "PHYSICS",
                displayName = "力学",
                parentKnowledgeNodeId = null,
                taxonomyVersion = "test-v1",
                createdAtEpochMillis = 1_000,
                canonicalName = "力学",
                verificationStatus = "CURATED",
            ),
        ),
    )

    private fun issueBookAndImages(): Set<BackupContentCategory> = setOf(
        BackupContentCategory.ISSUE_BOOK,
        BackupContentCategory.IMAGES,
    )

    private suspend fun createArchive(
        database: com.tingyun.smartmistakebook.core.database.StudyDatabasePort,
        selection: BackupSelection,
        schedulingSettings: DataStoreSchedulingSettingsStore? = null,
        reviewReminders: DataStoreReviewReminderRepository? = null,
    ): ByteArray {
        val output = ByteArrayOutputStream()
        AndroidBackupRepository(context, database, schedulingSettings, reviewReminders)
            .create(output, BackupOptions(selection))
        return output.toByteArray()
    }

    private fun insertTutorConversation() {
        withWritableDatabase { database ->
            database.insertOrThrow(
                "tutor_conversation",
                null,
                ContentValues().apply {
                    put("conversation_id", "d1-conversation")
                    put("anchor_kind", "TEXT_ONLY")
                    putNull("anchor_id")
                    putNull("anchor_revision_id")
                    putNull("anchor_problem_revision_id")
                    putNull("anchor_practice_unit_id")
                    put("status", "ACTIVE")
                    put("title", "D1 会话")
                    put("created_at_epoch_millis", 1_000L)
                    put("updated_at_epoch_millis", 1_000L)
                    put("last_turn_ordinal", 1)
                },
            )
        }
    }

    /** 旧设备"已建好索引"形态：两科各一条锚点行 + MATH 一条特征行。 */
    private fun insertDerivedIndexRows() {
        withWritableDatabase { database ->
            database.insertOrThrow(
                "knowledge_search_index_state",
                null,
                ContentValues().apply {
                    put("subject", "MATH")
                    put("index_version", KnowledgeSearchFeatureExtractor.INDEX_VERSION)
                },
            )
            database.insertOrThrow(
                "knowledge_search_index_state",
                null,
                ContentValues().apply {
                    put("subject", "PHYSICS")
                    put("index_version", KnowledgeSearchFeatureExtractor.INDEX_VERSION)
                },
            )
            database.insertOrThrow(
                "knowledge_search_feature",
                null,
                ContentValues().apply {
                    put("subject", "MATH")
                    put("search_feature", "函数")
                    put("knowledge_node_id", MATH_KNOWLEDGE_NODE_ID)
                },
            )
        }
    }

    private fun withWritableDatabase(block: (SQLiteDatabase) -> Unit) {
        SQLiteDatabase.openDatabase(
            context.getDatabasePath(databaseName).absolutePath,
            null,
            SQLiteDatabase.OPEN_READWRITE,
        ).use(block)
    }

    private fun rowCount(databaseFile: File, table: String): Long =
        SQLiteDatabase.openDatabase(
            databaseFile.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY,
        ).use { database -> DatabaseUtils.queryNumEntries(database, table) }

    private fun manifestJson(archive: ByteArray): String {
        ZipInputStream(ByteArrayInputStream(archive)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == SmbkArchiveCodec.MANIFEST_ENTRY) {
                    return zip.readBytes().toString(Charsets.UTF_8)
                }
            }
        }
        error("归档缺少 manifest.json")
    }

    /** 只替换 manifest 条目；其余条目逐字节保留。 */
    private fun rewriteManifest(archive: ByteArray, manifest: String): ByteArray {
        val output = ByteArrayOutputStream()
        ZipInputStream(ByteArrayInputStream(archive)).use { zip ->
            ZipOutputStream(output).use { zipOutput ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    zipOutput.putNextEntry(ZipEntry(entry.name))
                    if (entry.name == SmbkArchiveCodec.MANIFEST_ENTRY) {
                        zipOutput.write(manifest.toByteArray(Charsets.UTF_8))
                    } else {
                        zipOutput.write(zip.readBytes())
                    }
                    zipOutput.closeEntry()
                    zip.closeEntry()
                }
            }
        }
        return output.toByteArray()
    }

    private fun rowCountsOf(selection: JSONObject): Map<String, Long> {
        val rowCounts = selection.getJSONObject("rowCounts")
        val keys = rowCounts.keys()
        val counts = LinkedHashMap<String, Long>()
        while (keys.hasNext()) {
            val key = keys.next()
            counts[key] = rowCounts.getLong(key)
        }
        return counts
    }

    private fun isolatedRoot(stage: String): File =
        File(context.cacheDir, "d1-drill/$stage-${UUID.randomUUID()}").also { cleanupRoots += it }

    private fun newScope(): CoroutineScope {
        val job = SupervisorJob()
        cleanupJobs += job
        return CoroutineScope(job + Dispatchers.IO)
    }

    private fun handoffArchiveFile(): File =
        File(File(context.filesDir, HANDOFF_DIRECTORY), HANDOFF_ARCHIVE)

    /** 与 app 模块 DestructiveDeviceTestGuard 同参同语义（core:data 不依赖 app 模块）。 */
    private fun requireExplicitDataResetOptIn() {
        val enabled = InstrumentationRegistry.getArguments()
            .getString(ALLOW_DATA_RESET_ARGUMENT)
            ?.toBooleanStrictOrNull() == true
        assumeTrue(
            "这会清空测试应用的数据目录（deleteAllData）；只在专用测试设备上传 " +
                "-e $ALLOW_DATA_RESET_ARGUMENT true 再跑。",
            enabled,
        )
    }

    private fun killStageEnabled(): Boolean =
        InstrumentationRegistry.getArguments()
            .getString(KILL_STAGE_ARGUMENT)
            ?.toBooleanStrictOrNull() == true

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

    private companion object {
        const val ALLOW_DATA_RESET_ARGUMENT = "smb_allow_data_reset"
        const val KILL_STAGE_ARGUMENT = "smb_d1_kill_stage"
        const val HANDOFF_DIRECTORY = "d1-kill-handoff"
        const val HANDOFF_ARCHIVE = "archive-mask9.bin"
        const val CHECKSUMS_ENTRY = "checksums.sha256"
        const val MATH_KNOWLEDGE_NODE_ID = "kn-d1-math-function"
        const val PHYSICS_KNOWLEDGE_NODE_ID = "kn-d1-physics-mechanics"

        /** [com.tingyun.smartmistakebook.core.data.backup.SmbkSettingsDocumentV1] 的全部 @SerialName 键。 */
        val PREFERENCE_ONLY_SETTINGS_KEYS = setOf(
            "schemaVersion",
            "desiredRetention",
            "examCalendar",
            "optimizedFsrsParameters",
            "reviewReminderEnabled",
            "reviewReminderMinutesAfterMidnight",
        )
    }
}
