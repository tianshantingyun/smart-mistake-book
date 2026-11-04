package com.tingyun.smartmistakebook.core.database

import android.content.Context
import android.os.Build
import androidx.room3.immediateTransaction
import androidx.room3.useWriterConnection
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * B34 / API 23 raw-DDL 兼容门（`docs/research/2026-11-03-kb-b34-api23-ddl-prereg.md` §3.2）。
 *
 * 消灭的失败：SQLite 3.8.10.2（API 23）把「索引已存在时的 `CREATE INDEX IF NOT EXISTS`」和
 * 「索引不存在时的 `DROP INDEX IF EXISTS`」判成只读语句，Android 框架据此申请只读连接并抛
 * `Cannot execute this statement ... connection is read-only`，`BundledKnowledgeBaseInstaller`
 * 在 install 第一步即红。修复形态是 **`sqlite_master` 目录门控 + `BEGIN IMMEDIATE` 包裹**：
 * no-op DDL 不执行，真 DDL 在写事务里执行。
 *
 * 本类钉住的是行为（索引存在性 / 列序 / 事务内），不是字符串：不构造、不改写生产 SQL 文本。
 * 每个用例一个全新内存库，用例之间不共享连接状态。
 */
@RunWith(AndroidJUnit4::class)
class RoomImportDdlCompatibilityInstrumentedTest {

    private lateinit var context: Context
    private lateinit var store: RoomStudyDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        store = StudyDatabaseFactory.openInMemory(context)
    }

    @After
    fun tearDown() {
        store.close()
    }

    /** #1 模拟「索引已存在」路径：Room 建库时已建好两条索引，ensure 不得抛异常。 */
    @Test
    fun ensureFeatureIndexesSucceedsWhenIndexesAlreadyExist() = runBlocking {
        assertEquals(
            "夹具退化：Room 建库后两条索引应已存在",
            FEATURE_INDEX_NAMES,
            store.featureIndexNames(),
        )
        store.ensureKnowledgeSearchFeatureIndexes()
        assertEquals(FEATURE_INDEX_NAMES, store.featureIndexNames())
    }

    /** #2 连调 3 次均成功；列名/列序与 schema 一致。 */
    @Test
    fun ensureFeatureIndexesIsIdempotentAcrossThreeCalls() = runBlocking {
        repeat(3) { store.ensureKnowledgeSearchFeatureIndexes() }

        assertEquals(FEATURE_INDEX_NAMES, store.featureIndexNames())
        assertEquals(listOf("knowledge_node_id"), store.indexColumns(NODE_INDEX))
        assertEquals(listOf("subject", "knowledge_node_id"), store.indexColumns(SUBJECT_INDEX))
    }

    /** #3 窗口内两条索引不存在；退出后（成功路径）补回。 */
    @Test
    fun deferredWindowDropsThenRestoresBothIndexes() = runBlocking {
        var insideWindow: Set<String> = emptySet()
        store.withDeferredKnowledgeSearchFeatureIndexes {
            insideWindow = store.featureIndexNames()
        }
        assertEquals("窗口内两条二级索引必须被移除", emptySet<String>(), insideWindow)
        assertEquals("窗口退出后必须补建", FEATURE_INDEX_NAMES, store.featureIndexNames())
    }

    /** #4 block 抛异常时 finally 仍补建（失败路径）。 */
    @Test
    fun deferredWindowRestoresIndexesWhenBlockThrows() {
        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                store.withDeferredKnowledgeSearchFeatureIndexes {
                    throw IllegalStateException("boom")
                }
            }
        }
        runBlocking {
            assertEquals("finally 路径必须补建", FEATURE_INDEX_NAMES, store.featureIndexNames())
        }
    }

    /** #5 模拟崩溃残留（手工 DROP 一条）后，下一次 ensure 自愈。 */
    @Test
    fun ensureAfterSimulatedCrashWindowRepairsMissingIndex() = runBlocking {
        store.dropFeatureIndexInTestTransaction(NODE_INDEX)
        assertEquals(setOf(SUBJECT_INDEX), store.featureIndexNames())

        store.ensureKnowledgeSearchFeatureIndexes()
        assertEquals(FEATURE_INDEX_NAMES, store.featureIndexNames())
    }

    /** #6 DDL 确实运行在写事务内（钉住 `BEGIN IMMEDIATE` 机制，不钉 SQLite 版本行为）。 */
    @Test
    fun ddlRunsInsideWriteTransaction() = runBlocking {
        val maintenance = RoomKnowledgeImportMaintenance(store.database)
        val inTransaction = maintenance.inIndexDdlTransaction { transactor ->
            transactor.inTransaction()
        }
        assertTrue("索引 DDL 必须在 BEGIN IMMEDIATE 写事务内执行", inTransaction)
    }

    /**
     * #7 并发建索引：两条索引都缺失（崩溃窗口）时，多个协程同时 ensure。
     *
     * 目录检查与执行之间仍有窗口；`IF NOT EXISTS` 是兜底。写连接只有一条，
     * `immediateTransaction` 让这些调用串行化——本用例钉「不抛异常且收敛到两条」。
     */
    @Test
    fun concurrentEnsureCallsAfterCrashWindowConvergeWithoutError() = runBlocking {
        store.dropFeatureIndexInTestTransaction(NODE_INDEX)
        store.dropFeatureIndexInTestTransaction(SUBJECT_INDEX)
        assertEquals(emptySet<String>(), store.featureIndexNames())

        val attempts = (1..CONCURRENT_ENSURE_CALLS).map {
            async(Dispatchers.IO) {
                runCatching { store.ensureKnowledgeSearchFeatureIndexes() }
            }
        }.awaitAll()
        attempts.forEachIndexed { index, result ->
            assertTrue("并发 ensure #$index 失败：${result.exceptionOrNull()}", result.isSuccess)
        }
        assertEquals(FEATURE_INDEX_NAMES, store.featureIndexNames())
    }

    /**
     * #8 API 23 分支：索引已存在时，**未门控**的 no-op DDL 在 SQLite < 3.36 上会被框架判成只读；
     * 门控路径必须先查目录、根本不执行它。
     *
     * 判据是「修复契约」而非 OEM 版本：API <= 33 上若探针真的失败，失败必须是框架只读原文
     * （OEM 已打补丁、探针不红时只记录——预注册 §3.1 规定版本差异只记录）；API >= 34 上
     * SQLite 3.36+ 强制判为非只读，探针必须成功。两种情况下门控 ensure 都必须通过。
     */
    @Test
    fun legacyNoOpDdlBranchIsBypassedBySqliteMasterGating() = runBlocking {
        val version = store.sqliteVersion()
        println("[B34] sdk=${Build.VERSION.SDK_INT} sqlite_version()=$version")

        // 根因探针：与修复前生产路径相同——不查目录、不走事务，直接执行 no-op CREATE。
        val probe = runCatching {
            store.database.withRawConnection(isReadOnly = false) { connection ->
                connection.usePrepared(FEATURE_INDEX_ENSURE_DDL.first()) { statement ->
                    statement.step()
                }
            }
        }
        if (Build.VERSION.SDK_INT <= LEGACY_SDK_CEILING) {
            probe.exceptionOrNull()?.let { failure ->
                assertTrue(
                    "API<=33 上 no-op DDL 的失败必须是框架只读拦截；实际=${failure.message}",
                    failure.message.orEmpty()
                        .contains("might modify the database but the connection is read-only"),
                )
            }
        } else {
            assertTrue(
                "API>=34 上 no-op DDL 应被强制判为非只读；实际=${probe.exceptionOrNull()}",
                probe.isSuccess,
            )
        }

        // 门控路径：索引已存在 ⇒ 目录命中 ⇒ 不执行 no-op DDL ⇒ 任何 SQLite 版本都不抛。
        store.ensureKnowledgeSearchFeatureIndexes()
        assertEquals(FEATURE_INDEX_NAMES, store.featureIndexNames())
    }

    // ---- 夹具 ----

    /** 只返回本表的两条**生产**索引名（`sqlite_autoindex_*` 主键自动索引不计入）。 */
    private suspend fun RoomStudyDatabase.featureIndexNames(): Set<String> {
        val names = mutableSetOf<String>()
        database.useWriterConnection { connection ->
            connection.usePrepared(SELECT_INDEX_NAMES) { statement ->
                while (statement.step()) names += statement.getText(0)
            }
        }
        return names.intersect(FEATURE_INDEX_NAMES)
    }

    /** `PRAGMA index_info` 的列名（列序即 seqno 顺序）；索引名只用本类常量，不拼接外部输入。 */
    private suspend fun RoomStudyDatabase.indexColumns(indexName: String): List<String> {
        val columns = mutableListOf<String>()
        database.useWriterConnection { connection ->
            connection.usePrepared("PRAGMA index_info(`$indexName`)") { statement ->
                // index_info 列序：seqno, cid, name。
                while (statement.step()) columns += statement.getText(2)
            }
        }
        return columns
    }

    private suspend fun RoomStudyDatabase.sqliteVersion(): String {
        var version = ""
        database.useWriterConnection { connection ->
            connection.usePrepared("SELECT sqlite_version()") { statement ->
                if (statement.step()) version = statement.getText(0)
            }
        }
        return version
    }

    /**
     * 测试夹具的「手工 DROP」：在自己的 `immediateTransaction` 里执行（预注册 §3.2 用例 5）。
     * 这是模拟崩溃残留，不是生产路径。
     */
    private suspend fun RoomStudyDatabase.dropFeatureIndexInTestTransaction(indexName: String) {
        database.useWriterConnection { connection ->
            connection.immediateTransaction {
                usePrepared("DROP INDEX IF EXISTS `$indexName`") { statement -> statement.step() }
            }
        }
    }

    private companion object {
        const val NODE_INDEX = "index_knowledge_search_feature_knowledge_node_id"
        const val SUBJECT_INDEX = "index_knowledge_search_feature_subject_knowledge_node_id"
        const val LEGACY_SDK_CEILING = 33
        const val CONCURRENT_ENSURE_CALLS = 4

        const val SELECT_INDEX_NAMES =
            "SELECT name FROM sqlite_master WHERE type = 'index' " +
                "AND tbl_name = 'knowledge_search_feature'"

        val FEATURE_INDEX_NAMES = setOf(NODE_INDEX, SUBJECT_INDEX)
    }
}
