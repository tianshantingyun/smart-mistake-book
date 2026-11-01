package com.tingyun.smartmistakebook.core.data.backup

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tingyun.smartmistakebook.core.data.study.seedStudyFacts
import com.tingyun.smartmistakebook.core.database.ErrorBookEntrySeedRecord
import com.tingyun.smartmistakebook.core.database.KnowledgeNodeSeedRecord
import com.tingyun.smartmistakebook.core.database.PracticeUnitSeedRecord
import com.tingyun.smartmistakebook.core.database.ProblemRevisionSeedRecord
import com.tingyun.smartmistakebook.core.database.ProblemSeedRecord
import com.tingyun.smartmistakebook.core.database.StudyDatabaseFactory
import com.tingyun.smartmistakebook.core.database.StudyDatabasePort
import com.tingyun.smartmistakebook.core.database.StudySeedBundle
import com.tingyun.smartmistakebook.core.domain.BackupOptions
import com.tingyun.smartmistakebook.core.domain.BackupSelection
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream

/**
 * **批次 34 / KD-80 80-C2 的设备侧逐名用例。**
 *
 * 本文件消灭的具体失败：KD-80 此前只有 `assertEquals(rowCountKeys, rowCounts.keys)` 这种
 * "90 键集合一次相等"的断言，以及主源码 `closureDigest` 里的 `forEach`。集合相等在绿轮日志里
 * 只剩 `OK (N tests)`，无法证明设备上真的逐个见过 90 个表名；而 `71.json` 与
 * `SmbkSelectionPlan.allTables` 一旦漂移，只要两边同时错成同一份集合，集合相等也照样绿。
 *
 * 两个用例分别钉住：
 * 1. manifest 的 `rowCounts` 逐名覆盖**设备 APK 内 `assets/StudyDatabase/71.json` 的 entities
 *    表集合**（不是源码常量自证）：90 个键另从原始 manifest 文本解析（`JSONObject` 会静默
 *    覆盖重复键，重复必须在原始文本层才测得出来），逐键 `assertTrue("缺少表 $table")`，并断言
 *    缺失 / 多余 / 重复键三者都判红、两个视图不进 `rowCounts`；同一用例再断言
 *    `SmbkSelectionPlan.allTables` 与 71.json 逐名一致。
 * 2. **包内库**与**恢复后库**的实际表集合从 `sqlite_master` 逐名读出，与 71.json 的 90 张表
 *    双向差集都为空（缺失、多余、重复均判红）；Room/SQLite 簿记表与 FTS4 影子表以显式白名单
 *    放行。随后每个表名的 `rowCounts[table]` 都等于两库对该表的 `COUNT(*)`（全选下只有两张
 *    派生索引被恒裁）。逐表单条断言，失败消息带表名与两个数。
 *
 * 期望表集合来自测试 APK 的 schema asset，不复制 SQL、不硬编码 90 个表名：这样"71.json 是
 * 权威源"在设备上成立，而不是在开发机脚本里成立一次。用例只新增文件，不改 34 例基线。
 */
@RunWith(AndroidJUnit4::class)
class BackupRowCountsCoverageInstrumentedTest {

    private lateinit var context: Context
    private lateinit var databaseName: String

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        databaseName = StudyDatabaseFactory.DEFAULT_DATABASE_NAME
        context.deleteDatabase(databaseName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun manifestRowCountsEnumerateTheNinetySchemaTablesByName() = runBlocking {
        val store = openSeededStore()
        try {
            val archive = createArchive(store, BackupSelection.FULL)
            val rawManifest = manifestJson(archive)
            val manifest = selectionManifest(rawManifest)
            val rowCounts = manifest.rowCounts
            val schema = schemaAsset()

            assertEquals("71.json entities 必须恰有 90 项", 90, schema.tableNames.size)
            assertEquals(
                "71.json entities 不得出现重复表名",
                schema.tableNames.size,
                schema.tables.size,
            )
            assertEquals(
                "71.json 的 database.version 必须等于 SmbkSelectionPlan.SCHEMA_VERSION",
                SmbkSelectionPlan.SCHEMA_VERSION,
                schema.version,
            )
            assertEquals(
                "71.json 的 identityHash 必须等于 SmbkSelectionPlan.IDENTITY_HASH",
                SmbkSelectionPlan.IDENTITY_HASH,
                schema.identityHash,
            )
            assertEquals("71.json entities 必须是 90 张表", 90, schema.tables.size)
            assertEquals("71.json views 必须是 2 个", 2, schema.views.size)
            assertEquals(
                "SmbkSelectionPlan.allTables 必须与 71.json 的 entities 逐名一致",
                schema.tables.sorted(),
                SmbkSelectionPlan.allTables.sorted(),
            )
            assertEquals(
                "SmbkSelectionPlan.rowCountKeys 必须与 71.json 的 entities 逐名一致",
                schema.tables,
                SmbkSelectionPlan.rowCountKeys,
            )
            assertEquals(
                "manifest 的 databaseSchemaVersion 必须是 71",
                SmbkSelectionPlan.SCHEMA_VERSION,
                manifest.databaseSchemaVersion,
            )

            // 备份清单的**原始文本键序列**：重复键会被 JSONObject 静默合并，只有在这一层
            // 才能把"重复"判红；缺失/多余/重复三项都断言。
            val rawKeys = rowCountsKeysFromRawManifest(rawManifest)
            assertEquals("manifest rowCounts 原始键必须恰好 90 个", 90, rawKeys.size)
            assertEquals(
                "manifest rowCounts 不得出现重复键",
                rawKeys.size,
                rawKeys.toSet().size,
            )
            assertEquals(
                "manifest rowCounts 必须逐名覆盖 71.json 的 90 张表",
                schema.tables,
                rawKeys.toSet(),
            )
            assertEquals("rowCounts 必须恰好 90 个键", 90, rowCounts.size)
            assertEquals(
                "rowCounts 不得出现 71.json 之外的表",
                emptySet<String>(),
                rowCounts.keys - schema.tables,
            )
            assertEquals(
                "rowCounts 不得漏掉 71.json 的表",
                emptySet<String>(),
                schema.tables - rowCounts.keys,
            )

            // 逐名断言：这是 80-C2 设备侧"90 键逐名"的可执行形态，不是打印。
            schema.tables.sorted().forEach { table ->
                assertTrue("rowCounts 缺少 71.json 表 $table", rowCounts.containsKey(table))
            }
            schema.views.forEach { view ->
                assertTrue("视图 $view 不是表，不得出现在 rowCounts", view !in rowCounts)
            }
        } finally {
            runCatching { store.close() }
        }
    }

    @Test
    fun everyRowCountMatchesThePackagedAndRestoredDatabaseByName() = runBlocking {
        val store = openSeededStore()
        try {
            val archive = createArchive(store, BackupSelection.FULL)
            val rowCounts = selectionManifest(manifestJson(archive)).rowCounts
            val schema = schemaAsset()

            assertEquals(
                "rowCounts 必须逐名覆盖 71.json 的 90 张表",
                schema.tables,
                rowCounts.keys,
            )
            // 全选也必须裁掉两张派生索引（产品裁定 ③）：manifest 报 0，包内库也必须是 0。
            SmbkSelectionPlan.derivedTables.forEach { table ->
                assertEquals("全选也必须裁掉派生索引 $table", 0L, rowCounts.getValue(table))
            }

            val packagedFile = extractArchiveDatabaseFile(archive)
            assertActualTableSetMatches(packagedFile, schema, "包内库")
            val packagedCounts = countEveryTable(packagedFile, schema.tables)
            schema.tables.sorted().forEach { table ->
                assertEquals(
                    "包内库 COUNT(*) 与 manifest rowCounts 不符：$table",
                    packagedCounts.getValue(table),
                    rowCounts.getValue(table),
                )
            }

            store.close()
            restore(archive)
            val restoredFile = context.getDatabasePath(databaseName)
            assertActualTableSetMatches(restoredFile, schema, "恢复库")
            val restoredCounts = countEveryTable(restoredFile, schema.tables)
            schema.tables.sorted().forEach { table ->
                assertEquals(
                    "恢复库 COUNT(*) 与 manifest rowCounts 不符：$table",
                    restoredCounts.getValue(table),
                    rowCounts.getValue(table),
                )
            }
        } finally {
            runCatching { store.close() }
        }
    }

    // ------------------------------------------------------------ fixture

    private fun openSeededStore(): StudyDatabasePort {
        val store = StudyDatabaseFactory.open(context, databaseName)
        seedStudyFacts(context, store, databaseName, seedBundle())
        insertRawFixture()
        return store
    }

    /**
     * 让夹具净值覆盖两个类别（题面 + 会话）和一张恒随包表；派生索引写一行，用来证明全选下
     * 它会被裁成 0，而不是"本来就没有行"。
     */
    private fun seedBundle() = StudySeedBundle(
        problems = listOf(
            ProblemSeedRecord(
                problemId = "problem-rowcounts",
                canonicalFingerprint = "a".repeat(64),
                subject = "MATH",
                createdAtEpochMillis = 1_000,
            ),
        ),
        revisions = listOf(
            ProblemRevisionSeedRecord(
                revisionId = REVISION_ID,
                problemId = "problem-rowcounts",
                revisionNumber = 1,
                title = "行数逐名题",
                problemMarkdown = "1 + 1 = ?",
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
                practiceUnitId = "practice-rowcounts",
                problemId = "problem-rowcounts",
                problemRevisionId = REVISION_ID,
                unitKey = "unit:rowcounts",
                unitKind = "PROBLEM",
                title = "行数逐名题",
                promptMarkdown = "1 + 1 = ?",
                estimatedSeconds = 60,
                createdAtEpochMillis = 1_000,
            ),
        ),
        errorBookEntries = listOf(
            ErrorBookEntrySeedRecord(
                entryId = "entry-rowcounts",
                practiceUnitId = "practice-rowcounts",
                problemId = "problem-rowcounts",
                currentRevisionId = REVISION_ID,
                sourceKey = null,
                acceptedAtEpochMillis = 1_000,
                updatedAtEpochMillis = 1_000,
            ),
        ),
        knowledgeNodes = listOf(
            KnowledgeNodeSeedRecord(
                knowledgeNodeId = "kn-rowcounts",
                stableCode = "MATH.ADDITION",
                subject = "MATH",
                displayName = "加法",
                parentKnowledgeNodeId = null,
                taxonomyVersion = "test-v1",
                createdAtEpochMillis = 1_000,
                canonicalName = "加法",
                verificationStatus = "CURATED",
            ),
        ),
    )

    private fun insertRawFixture() {
        SQLiteDatabase.openDatabase(
            context.getDatabasePath(databaseName).absolutePath,
            null,
            SQLiteDatabase.OPEN_READWRITE,
        ).use { db ->
            db.insertOrThrow(
                "knowledge_search_index_state",
                null,
                ContentValues().apply {
                    put("subject", "MATH")
                    put("index_version", 1)
                },
            )
            db.insertOrThrow(
                "tutor_conversation",
                null,
                ContentValues().apply {
                    put("conversation_id", "rowcounts-conversation-1")
                    put("anchor_kind", "TEXT_ONLY")
                    putNull("anchor_id")
                    putNull("anchor_revision_id")
                    putNull("anchor_problem_revision_id")
                    putNull("anchor_practice_unit_id")
                    put("status", "ACTIVE")
                    put("title", "rowcounts")
                    put("created_at_epoch_millis", 1_000L)
                    put("updated_at_epoch_millis", 1_000L)
                    put("last_turn_ordinal", 1)
                },
            )
        }
    }

    // ------------------------------------------------------------ backup / restore

    private suspend fun createArchive(
        store: StudyDatabasePort,
        selection: BackupSelection,
    ): ByteArray = ByteArrayOutputStream().also { output ->
        AndroidBackupRepository(context, store).create(output, BackupOptions(selection))
    }.toByteArray()

    private suspend fun restore(archive: ByteArray) {
        val port = StudyDatabaseFactory.open(context, databaseName)
        try {
            AndroidBackupRepository(context, port)
                .restore(ByteArrayInputStream(archive))
        } finally {
            runCatching { port.close() }
        }
    }

    // ------------------------------------------------------------ schema asset

    private data class SchemaAsset(
        val version: Int,
        val identityHash: String,
        /** 71.json `entities` 数组的原始顺序（含可能出现的重复项）。 */
        val tableNames: List<String>,
        val tables: Set<String>,
        val views: Set<String>,
    )

    private fun schemaAsset(): SchemaAsset {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val text = assets.open(SCHEMA_ASSET).bufferedReader(Charsets.UTF_8).use { it.readText() }
        val database = JSONObject(text).getJSONObject("database")
        val entities = database.getJSONArray("entities")
        val tableNames = ArrayList<String>(entities.length())
        for (index in 0 until entities.length()) {
            tableNames += entities.getJSONObject(index).getString("tableName")
        }
        val viewArray = database.getJSONArray("views")
        val views = LinkedHashSet<String>(viewArray.length())
        for (index in 0 until viewArray.length()) {
            views += viewArray.getJSONObject(index).getString("viewName")
        }
        return SchemaAsset(
            version = database.getInt("version"),
            identityHash = database.getString("identityHash"),
            tableNames = tableNames,
            tables = tableNames.toSet(),
            views = views,
        )
    }

    // ------------------------------------------------------------ manifest / db

    private data class SelectionManifest(
        val databaseSchemaVersion: Int,
        val rowCounts: Map<String, Long>,
    )

    private fun selectionManifest(manifestJson: String): SelectionManifest {
        val manifest = JSONObject(manifestJson)
        assertEquals("归档必须是 v3", 3, manifest.getInt("formatVersion"))
        val selection = manifest.getJSONObject("selection")
        assertEquals("全选包的 categoryMask", 15, selection.getInt("categoryMask"))
        val rowCounts = selection.getJSONObject("rowCounts")
        val keys = rowCounts.keys()
        val counts = LinkedHashMap<String, Long>()
        while (keys.hasNext()) {
            val key = keys.next()
            counts[key] = rowCounts.getLong(key)
        }
        return SelectionManifest(
            databaseSchemaVersion = manifest.getInt("databaseSchemaVersion"),
            rowCounts = counts,
        )
    }

    /**
     * 从**原始 manifest 文本**取出 `selection.rowCounts` 的键序列（保留重复）。
     * `JSONObject` 解析时会把同名键静默覆盖，因此"重复键判红"只能在这一层做。
     */
    private fun rowCountsKeysFromRawManifest(manifestJson: String): List<String> {
        val marker = "\"rowCounts\""
        val markerIndex = manifestJson.indexOf(marker)
        check(markerIndex >= 0) { "manifest 缺少 rowCounts 键" }
        val open = manifestJson.indexOf('{', markerIndex + marker.length)
        check(open >= 0) { "manifest rowCounts 不是 JSON 对象" }
        var depth = 0
        var inString = false
        var escaped = false
        var close = -1
        for (index in open until manifestJson.length) {
            val char = manifestJson[index]
            if (inString) {
                when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    char == '"' -> inString = false
                }
                continue
            }
            when (char) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        close = index
                        break
                    }
                }
            }
        }
        check(close > open) { "manifest rowCounts 对象未闭合" }
        val body = manifestJson.substring(open + 1, close)
        return ROW_COUNTS_KEY_PATTERN.findAll(body).map { it.groupValues[1] }.toList()
    }

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

    private fun extractArchiveDatabaseFile(archive: ByteArray): File {
        val staging = File(context.cacheDir, "rowcounts-archive-${System.nanoTime()}").apply { mkdirs() }
        ZipInputStream(ByteArrayInputStream(archive)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == SmbkArchiveCodec.DATABASE_ENTRY) {
                    File(staging, "database.sqlite").outputStream().use { output ->
                        output.write(zip.readBytes())
                    }
                    break
                }
                zip.closeEntry()
            }
        }
        val file = File(staging, "database.sqlite")
        check(file.exists()) { "归档缺少 ${SmbkArchiveCodec.DATABASE_ENTRY}" }
        file.deleteOnExit()
        staging.deleteOnExit()
        return file
    }

    // ------------------------------------------------------------ sqlite_master sets

    private data class ActualDatabaseObjects(
        val tableNames: List<String>,
        val virtualTableNames: Set<String>,
        val viewNames: List<String>,
    )

    private fun readActualDatabaseObjects(file: File): ActualDatabaseObjects {
        check(file.exists()) { "数据库文件不存在：$file" }
        SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            val tableNames = mutableListOf<String>()
            db.rawQuery("SELECT name FROM sqlite_master WHERE type = 'table'", null).use { cursor ->
                while (cursor.moveToNext()) tableNames += cursor.getString(0)
            }
            val virtualTableNames = mutableSetOf<String>()
            db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type = 'table' " +
                    "AND sql LIKE 'CREATE VIRTUAL TABLE%'",
                null,
            ).use { cursor ->
                while (cursor.moveToNext()) virtualTableNames += cursor.getString(0)
            }
            val viewNames = mutableListOf<String>()
            db.rawQuery("SELECT name FROM sqlite_master WHERE type = 'view'", null).use { cursor ->
                while (cursor.moveToNext()) viewNames += cursor.getString(0)
            }
            return ActualDatabaseObjects(tableNames, virtualTableNames, viewNames)
        }
    }

    /**
     * 实际表集合与 71.json **逐名**比对：`sqlite_master` 里的重复名、71.json 有而库里没有
     * （缺失）、库里有而 71.json 没有（多余）三者都判红。白名单只含 Room/SQLite 自身簿记表
     * 与由 `sqlite_master` 里的虚拟表**推导**出的 FTS4 影子表——不硬编码任何业务表名。
     */
    private fun assertActualTableSetMatches(file: File, schema: SchemaAsset, label: String) {
        val actual = readActualDatabaseObjects(file)
        assertEquals(
            "$label 的 sqlite_master 表名不得重复",
            actual.tableNames.size,
            actual.tableNames.toSet().size,
        )
        val actualTables = actual.tableNames.toSet()

        // 缺失：先给差集，再逐名断言（失败消息带表名，便于设备日志定位）。
        val missing = schema.tables - actualTables
        assertTrue("$label 缺少 71.json 表：${missing.sorted()}", missing.isEmpty())
        schema.tables.sorted().forEach { table ->
            assertTrue("$label 缺少 71.json 表 $table", actualTables.contains(table))
        }

        val infrastructure = buildSet {
            add("android_metadata")
            add("room_master_table")
            add("sqlite_sequence")
            actual.virtualTableNames.forEach { virtual ->
                actualTables
                    .filter { it != virtual && it.startsWith("${virtual}_") }
                    .forEach { add(it) }
            }
        }
        val unexpected = actualTables
            .filterNot { it in schema.tables }
            .filterNot { it.startsWith("sqlite_") }
            .filterNot { it in infrastructure }
        assertTrue("$label 出现 71.json 之外的表：${unexpected.sorted()}", unexpected.isEmpty())

        assertEquals(
            "$label 的 sqlite_master 视图名不得重复",
            actual.viewNames.size,
            actual.viewNames.toSet().size,
        )
        assertEquals(
            "$label 的视图必须与 71.json 逐名一致",
            schema.views,
            actual.viewNames.toSet(),
        )
    }

    private fun countEveryTable(file: File, tables: Set<String>): Map<String, Long> {
        check(file.exists()) { "数据库文件不存在：$file" }
        SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            val counts = LinkedHashMap<String, Long>(tables.size)
            tables.sorted().forEach { table ->
                val quoted = "\"" + table.replace("\"", "\"\"") + "\""
                try {
                    db.rawQuery("SELECT COUNT(*) FROM $quoted", null).use { cursor ->
                        if (!cursor.moveToFirst()) {
                            throw AssertionError("无法读取表 $table 的 COUNT(*)")
                        }
                        counts[table] = cursor.getLong(0)
                    }
                } catch (failure: AssertionError) {
                    throw failure
                } catch (failure: Exception) {
                    throw AssertionError("数据库缺少 71.json 表 $table：${failure.message}", failure)
                }
            }
            return counts
        }
    }

    private companion object {
        private const val SCHEMA_ASSET =
            "com.tingyun.smartmistakebook.core.database.StudyDatabase/71.json"
        private const val REVISION_ID = "revision-rowcounts"

        /** 匹配 `"table":` 形式的 JSON 键（值只是数字，rowCounts 对象内无嵌套字符串值）。 */
        private val ROW_COUNTS_KEY_PATTERN = Regex("\"((?:[^\"\\\\]|\\\\.)*)\"\\s*:")
    }
}
