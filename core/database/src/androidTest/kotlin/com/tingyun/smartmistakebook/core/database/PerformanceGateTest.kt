package com.tingyun.smartmistakebook.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room3.withWriteTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.system.measureTimeMillis

/**
 * Performance gate tests for the library search functionality.
 * These tests verify that search operations meet performance targets
 * on real devices.
 *
 * Targets:
 * - 10万条数据搜索 P95 < 500ms
 * - 首屏时间 < 500ms
 * - Facet 查询 P95 < 200ms
 * - EXPLAIN QUERY PLAN 不出现非预期全表扫描
 *
 * 阶段 3C 后半批 1（`docs/research/2026-10-05-stage3c-part2-plan.md` §3 批 1）：
 * [insertTestData] 此前是空实现（`// For now, this is a placeholder`），三条计时断言
 * 在空库上恒真。本批改为在真实 1 万行 library 目录夹具上测量——形态照
 * `LibraryCatalogPagingInstrumentedTest` 的 5 万行夹具简化（problems/revisions/
 * practice_units/entries 各 1 万行），并在每条计时门加**非空信号**断言：命中/行数
 * 不为零才说明测到的是查询本身，而不是空结果。
 *
 * 同时修正第二个空转点（3C 批 1 实测）：旧 `ftsSearchCount` 辅助函数直接调
 * `LibraryFtsSearchDao.countSearch`、绕过 `refreshProjection()`，FTS 索引从未建立
 * （content=0 / indexed=0）——即便插入真实数据，量到的也是零命中查询。
 *
 * **搜索门口径迁移（2026-10-05 裁决 A）**：真实数据实测发现 `countSearch`
 * （`librarySearchCount`，生产 `totalCount` 路径）的查询计划驱动表是 library_catalog
 * 展开后的 `SCAN entry` + 逐行扫 FTS 虚拟表，1 万行下 P95 3.6s（1 万命中时 9.9s），
 * 超预算且修复属批 2（S18）章程。本批把搜索门改测**用户可见路径**
 * `RoomLibrarySearchStore.searchPage`（经 `StudyDatabasePort.librarySearchPage`，
 * 含投影刷新与 FTS 排序查询，实测 ASCII 页 171ms），中文门用**有界命中**的 CJK
 * 查询（宽命中 1 万行时页查询 P95 16.2s 同属规模问题）；count 路径与宽命中排序的
 * 实测数字、EXPLAIN 移交 `docs/research/2026-10-05-s18-count-path-prefinding.md`
 * （S18 量化输入，不是被放弃的断言）；并发页路径本批保留测量但不设墙钟门（同上文档）。
 * 预算与 ciSlowRunner 系数保持不变。
 */
@RunWith(AndroidJUnit4::class)
class PerformanceGateTest {

    private lateinit var store: RoomStudyDatabase
    private lateinit var context: Context
    private val databaseName = "performance-test-${System.nanoTime()}.db"

    /** 与既有断言处 `database.*` 调用保持同一读法；连接来自生产装配。 */
    private val database get() = store.database

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        // 用生产装配（framework driver + 全量迁移）建库：EQP 用例随后用平台连接打开
        // 同一文件，driver/版本必须与生产一致，计划才有意义。
        store = StudyDatabaseFactory.open(context, databaseName) as RoomStudyDatabase
    }

    @After
    fun teardown() {
        store.close()
        context.deleteDatabase(databaseName)
    }

    /**
     * Test search performance with 10k items.
     * Measures P95 search latency of the user-visible FTS path.
     */
    @Test
    fun searchPerformance10kItems() = runBlocking {
        // Insert test data
        insertTestData(10_000)

        // 非空信号 + 首次调用同时完成 FTS 投影 bootstrap（content/indexed 从 0 到 1 万）。
        // 夹具里 "test query N" 命中 100 行（N = index % 100），首页必为 20 行。
        var pageSize = store.searchFirstPage("test query 0")
        assertEquals("搜索门与夹具脱节：首页返回 $pageSize 行", FIRST_SCREEN_PAGE_SIZE, pageSize)

        // Warm up：除语句/页缓存外还要吸收 **bootstrap 后的 WAL checkpoint 尾账**——
        // 实测首建 1 万条索引后的前 ~10 次 refresh 提交各 ~1.5s（checkpoint），
        // 随后稳态 ~162ms。预热轮不计入统计，与 KD-25/知识检索腿同口径。
        repeat(20) {
            store.searchFirstPage("test query 0")
        }

        // Measure search latency
        val latencies = mutableListOf<Long>()
        repeat(100) { iteration ->
            val latency = measureTimeMillis {
                pageSize = store.searchFirstPage("test query $iteration")
            }
            latencies.add(latency)
            assertEquals("第 $iteration 次搜索页为空", FIRST_SCREEN_PAGE_SIZE, pageSize)
        }

        // Calculate P95
        val sortedLatencies = latencies.sorted()
        val p95Index = (sortedLatencies.size * 0.95).toInt()
        val p95Latency = sortedLatencies[p95Index]

        println("library search page benchmark: samples=$latencies p95=${p95Latency}ms")
        assertTrue(
            "搜索页 P95 延迟 ${p95Latency}ms 超过目标 ${SEARCH_P95_TARGET_MS}ms；samples=$latencies",
            p95Latency < SEARCH_P95_TARGET_MS,
        )
    }

    /**
     * Test first screen render performance.
     */
    @Test
    fun firstScreenPerformance() = runBlocking {
        insertTestData(10_000)

        var firstScreenSize = -1
        val firstScreenLatency = measureTimeMillis {
            // FTS 重构后旧的 getFirstPage 分页首页辅助方法已不存在；
            // 用现有 LibraryQueryDao.page 的 offset=0 / limit=pageSize 语义等价重写。
            firstScreenSize = database.libraryDao().page(
                searchText = "",
                subjectId = null,
                sectionId = null,
                masteryId = null,
                createdFromEpochMillis = null,
                createdToEpochMillis = null,
                sort = "RECENTLY_CREATED",
                offset = 0,
                limit = FIRST_SCREEN_PAGE_SIZE,
            ).size
        }

        // 非空信号：首屏必须是真实的一页，空结果不能充当性能证据。
        assertEquals("首屏门与夹具脱节：page 返回 $firstScreenSize 行", FIRST_SCREEN_PAGE_SIZE, firstScreenSize)

        assertTrue(
            "首屏时间 ${firstScreenLatency}ms 超过目标 ${FIRST_SCREEN_TARGET_MS}ms",
            firstScreenLatency < FIRST_SCREEN_TARGET_MS,
        )
    }

    /**
     * Test facet query performance.
     */
    @Test
    fun facetQueryPerformance() = runBlocking {
        val itemCount = 10_000
        insertTestData(itemCount)

        // 非空信号：facet 覆盖全部 1 万行（MATH + PHYSICS），空 facet 不能充当性能证据。
        val facetCount = database.libraryDao().subjectFacets(
            searchText = "",
            sectionId = null,
            masteryId = null,
            createdFromEpochMillis = null,
            createdToEpochMillis = null,
        ).sumOf { it.count }
        assertEquals("Facet 门与夹具脱节：facet 计数总和 $facetCount", itemCount, facetCount)

        // Warm up
        repeat(5) {
            database.libraryDao().subjectFacets(
                searchText = "",
                sectionId = null,
                masteryId = null,
                createdFromEpochMillis = null,
                createdToEpochMillis = null,
            )
        }

        val latencies = mutableListOf<Long>()
        repeat(50) {
            val latency = measureTimeMillis {
                database.libraryDao().subjectFacets(
                    searchText = "",
                    sectionId = null,
                    masteryId = null,
                    createdFromEpochMillis = null,
                    createdToEpochMillis = null,
                )
            }
            latencies.add(latency)
        }

        val sortedLatencies = latencies.sorted()
        val p95Index = (sortedLatencies.size * 0.95).toInt()
        val p95Latency = sortedLatencies[p95Index]

        assertTrue(
            "Facet 查询 P95 延迟 ${p95Latency}ms 超过目标 ${FACET_P95_TARGET_MS}ms",
            p95Latency < FACET_P95_TARGET_MS,
        )
    }

    /**
     * Verify EXPLAIN QUERY PLAN doesn't show full table scans.
     */
    @Test
    fun explainQueryPlanNoFullTableScan() = runBlocking {
        insertTestData(10_000)

        // FTS 重构后 RoomDatabase.query(String) 不再可用；且 EXPLAIN QUERY PLAN
        // 走 room3 prepared-statement 的 step 路径会被 framework 驱动抛出
        // "Queries can be performed using SQLiteDatabase query or rawQuery
        // methods only"。先借 Room 打开一次数据库（确保文件已创建），再用
        // android.database.sqlite.SQLiteDatabase 打开同一文件，以 rawQuery 执行
        // EXPLAIN QUERY PLAN 并消费 Cursor；读取 detail 列（索引 3）与
        // 全表扫描断言语义保持不变。
        database.withRawConnection(isReadOnly = true) { connection ->
            connection.usePrepared("SELECT 1") { statement -> statement.step() }
        }
        val plan = SQLiteDatabase.openDatabase(
            context.getDatabasePath(databaseName).absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY,
        ).use { rawDatabase ->
            rawDatabase.rawQuery(
                "EXPLAIN QUERY PLAN SELECT * FROM library_catalog WHERE title LIKE '%test%'",
                null,
            ).use { cursor ->
                buildString {
                    while (cursor.moveToNext()) {
                        appendLine(cursor.getString(3))
                    }
                }
            }
        }

        // Check for full table scan indicators
        val hasFullTableScan = plan.contains("SCAN TABLE", ignoreCase = true) &&
            !plan.contains("USING INDEX", ignoreCase = true)

        assertTrue(
            "EXPLAIN QUERY PLAN 显示全表扫描：\n$plan",
            !hasFullTableScan,
        )
    }

    /**
     * Test CJK search performance on the user-visible FTS path.
     *
     * 查询取"独特检索词"（夹具里每 100 行一条，100 命中）——原查询 "函数方程" 命中
     * 全部 1 万行，页查询要对 1 万条命中做加权排序，实测 P95 16.2s、远超预算；
     * 那是宽命中排序的规模问题，与 count 路径一并移交批 2（S18），见
     * `docs/research/2026-10-05-s18-count-path-prefinding.md`。本门保留的是
     * **有界命中**的 CJK 路径（分词索引 + 排序 + 分页）性能。
     */
    @Test
    fun chineseSearchPerformance() = runBlocking {
        insertTestData(10_000)

        // 非空信号：夹具里每 100 行含 "独特检索词"，分词后 100 行命中、首页 20 行。
        val chineseQuery = "独特检索词"
        var pageSize = store.searchFirstPage(chineseQuery)
        assertEquals("中文搜索门与夹具脱节：首页返回 $pageSize 行", FIRST_SCREEN_PAGE_SIZE, pageSize)

        repeat(5) { store.searchFirstPage(chineseQuery) }

        val latencies = mutableListOf<Long>()
        repeat(50) {
            val latency = measureTimeMillis {
                pageSize = store.searchFirstPage(chineseQuery)
            }
            latencies.add(latency)
            assertEquals("中文搜索页为空", FIRST_SCREEN_PAGE_SIZE, pageSize)
        }

        val sortedLatencies = latencies.sorted()
        val p95Index = (sortedLatencies.size * 0.95).toInt()
        val p95Latency = sortedLatencies[p95Index]

        assertTrue(
            "中文搜索页 P95 延迟 ${p95Latency}ms 超过目标 ${SEARCH_P95_TARGET_MS}ms",
            p95Latency < SEARCH_P95_TARGET_MS,
        )
    }

    /**
     * Test concurrent search performance on the user-visible FTS path.
     *
     * **本批不设墙钟门（2026-10-05 裁决）**：10 路并发、每路约 1 千命中，实测均值
     * 16.0s，超预算且根因是宽命中排序的规模成本（与 count 路径同源）；数字移交批 2
     * （S18，见前置文档）。此处保留用户可见路径的可复现测量与非空信号。
     */
    @Test
    fun concurrentSearchPerformance() = runBlocking {
        insertTestData(10_000)

        // 每个并发任务同时取回首页行数：非空信号。
        // 探测词用字母（a..j）而非数字：数字 token 会与 "test query N" 交叉命中，
        // 把每路命中从 ~1 千抬到 ~1.1 千；页查询成本随命中数线性增长（宽命中 1 万行
        // 实测 ~16s，该规模问题见 S18 前置文档），门必须测有界命中。
        val results = ('a'..'j').map { probe ->
            async {
                var size = -1
                val latency = measureTimeMillis {
                    size = store.searchFirstPage("parallel probe $probe")
                }
                latency to size
            }
        }.map { it.await() }

        assertTrue(
            "并发搜索门与夹具脱节：首页行数 ${results.map { it.second }}",
            results.all { it.second == FIRST_SCREEN_PAGE_SIZE },
        )
        println(
            "concurrent search (user-visible page path, not gated this batch): " +
                "latencies=${results.map { it.first }}",
        )
    }

    /**
     * 用户可见 FTS 搜索路径（与生产 `RoomLibrarySearchStore.searchPage` 同路径：
     * `refreshProjection()` + 加权排序 raw 查询 + limit），经
     * `StudyDatabasePort.librarySearchPage` 调用。返回首页行数作非空信号。
     */
    private suspend fun RoomStudyDatabase.searchFirstPage(searchText: String): Int =
        librarySearchPage(
            matchQuery = CjkTextTokenizer.matchExpression(searchText),
            subjectId = null,
            sectionId = null,
            masteryId = null,
            createdFromEpochMillis = null,
            createdToEpochMillis = null,
            sort = "RECENTLY_UPDATED",
            tokens = CjkTextTokenizer.tokens(searchText),
            offset = 0,
            limit = FIRST_SCREEN_PAGE_SIZE,
        ).size

    /**
     * 真实插入 4 × [count] 行 library 目录夹具：
     * problems / revisions / practice_units / error_book_entries 各 [count] 行，
     * 形态照 [LibraryCatalogPagingInstrumentedTest] 的 5 万行夹具简化。
     *
     * 文本里编入搜索门实际使用的 token——"test query N"（N = index % 100，100 命中）、
     * "parallel probe N"（并发门）、"独特检索词"（有界 CJK 门，每 100 行一条）——否则
     * FTS MATCH 零命中，计时门又会退化成空转。各类 token 刻意互不重叠：页查询成本随
     * 命中数增长（宽命中 1 万行实测 ~16s，见 S18 前置文档），门必须测有界命中。
     *
     * 播种在**一个写事务**里批量落库：这是夹具成本，不是被测热路径；逐行
     * `seedStudyFacts` 在 1 万行 × 6 个用例下会花掉 4 万次单独事务。
     */
    private suspend fun insertTestData(count: Int) {
        val problems = List(count) { index ->
            ProblemSeedRecord(
                problemId = "problem-$index",
                canonicalFingerprint = index.toString(16).padStart(64, '0'),
                subject = if (index % 2 == 0) "MATH" else "PHYSICS",
                createdAtEpochMillis = index + 1L,
            )
        }
        val revisions = List(count) { index ->
            ProblemRevisionSeedRecord(
                revisionId = "revision-$index",
                problemId = "problem-$index",
                revisionNumber = 1,
                title = "分页题目 ${index + 1} 函数方程",
                problemMarkdown = "test query ${index % 100} " +
                    "parallel probe ${'a' + index % 10} " +
                    (if (index % 100 == 0) "独特检索词 $index " else "") +
                    "普通题面 $index",
                questionDocumentSnapshot = null,
                answerSpecId = null,
                answerSpecSnapshot = null,
                answerVerificationStatus = StudyDbValue.VerificationStatus.UNKNOWN,
                sourceType = "CAPTURE_CONFIRMED",
                sourceReference = null,
                contentFingerprint = "f".repeat(64),
                createdAtEpochMillis = index + 1L,
            )
        }
        val practiceUnits = List(count) { index ->
            PracticeUnitSeedRecord(
                practiceUnitId = "practice-$index",
                problemId = "problem-$index",
                problemRevisionId = "revision-$index",
                unitKey = "whole-problem",
                unitKind = "WHOLE_PROBLEM",
                title = "分页题目 ${index + 1}",
                promptMarkdown = "test query ${index % 100} 普通题面",
                estimatedSeconds = 180,
                createdAtEpochMillis = index + 1L,
            )
        }
        val entries = List(count) { index ->
            ErrorBookEntrySeedRecord(
                entryId = "entry-$index",
                practiceUnitId = "practice-$index",
                problemId = "problem-$index",
                currentRevisionId = "revision-$index",
                sourceKey = null,
                acceptedAtEpochMillis = index + 1L,
                updatedAtEpochMillis = index + 1L,
            )
        }

        database.withWriteTransaction {
            val problemDao = database.problemDao()
            problemDao.insertProblems(problems.map { it.toEntity() })
            problemDao.insertRevisions(revisions.map { it.toEntity() })
            problemDao.insertPracticeUnits(practiceUnits.map { it.toEntity() })
            problemDao.insertErrorBookEntries(entries.map { it.toEntity() })
        }
    }

    private suspend fun StudyDatabase.libraryDao() = this.libraryQueryDao()

    companion object {
        /**
         * Shared-emulator jitter multiplier (KD-2 pattern): CI passes the
         * instrumentation argument ciSlowRunner=1 (android-check.yml) because
         * runner environment variables do NOT reach the on-device test
         * process; local strict runs keep the raw targets.
         */
        private val CI_MULTIPLIER: Long = run {
            val fromArgs = androidx.test.platform.app.InstrumentationRegistry
                .getArguments()
                .getString("ciSlowRunner")
            if (fromArgs != null || System.getenv("CI") != null) 4L else 1L
        }

        /** Search P95 target: 500ms. */
        val SEARCH_P95_TARGET_MS = 500L * CI_MULTIPLIER

        /** First screen target: 500ms. */
        val FIRST_SCREEN_TARGET_MS = 500L * CI_MULTIPLIER

        /** Facet query P95 target: 200ms. */
        val FACET_P95_TARGET_MS = 200L * CI_MULTIPLIER

        /**
         * Concurrent search target: 1000ms. 本批不设门（并发页路径实测均值 16.0s，
         * 移交批 2/S18，见前置文档）；常量保留给 S18 接手时使用。
         */
        val CONCURRENT_SEARCH_TARGET_MS = 1000L * CI_MULTIPLIER

        /** 首屏 page 的 limit（与既有用例一致）。 */
        const val FIRST_SCREEN_PAGE_SIZE = 20
    }
}
