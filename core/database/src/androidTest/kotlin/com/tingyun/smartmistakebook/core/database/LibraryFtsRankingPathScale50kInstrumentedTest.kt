package com.tingyun.smartmistakebook.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room3.withWriteTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.system.measureTimeMillis

/**
 * **K1 批 1 · 50k FTS 规模补测（排序路径）**：S18 尾批 2 的 1 万行排序门
 * （[LibraryFtsRankingPathInstrumentedTest]）只测到 10k；"集合级命中集 join 的宽命中成本
 * 随目录行数增长"在 5 万行下没有实测。本类在 **5 万行**夹具上记录窄命中（12 命中）、
 * 100 / 1k 命中（沿用既有 P95 门口径）与 1 万 / 宽命中（**只记录，不设新门**），并按
 * K5 加固一补**结构/EQP 断言**（排序表达式不得退化为逐行 FTS 相关子查询、命中集必须
 * 被 MATERIALIZE 且按 docid 探测、MATCH 仍是主驱动、不得整表扫 catalog）。
 *
 * 取舍（与 count 的 50k 类同）：独立成类，不把既有 10k 等价性/结构门参数化——那会让
 * S18 锚点门的运行时与夹具形态双重漂移；本类只做"计时记录 + 非退化计数 + 结构断言"，
 * **不新增任何计时门**。
 *
 * 夹具：`seedLibraryCatalogScale(50_000, boundedTokenEvery = 500)` ⇒ 100 命中；再在 bootstrap
 * 前注入 1k 探针（每 50 行）、1 万探针（每 5 行）、12 命中探针（显式 12 行）；宽命中 = 全量 5 万。
 */
@RunWith(AndroidJUnit4::class)
class LibraryFtsRankingPathScale50kInstrumentedTest {

    @Test
    fun fixtureCoversAllHitTiers() = runBlocking {
        assertEquals("12 命中档与夹具脱节", 12, countHits(TOKENS_TWELVE))
        assertEquals("100 命中档与夹具脱节", 100, countHits(TOKENS_HUNDRED))
        assertEquals("1k 命中档与夹具脱节", 1_000, countHits(TOKENS_ONE_K))
        assertEquals("1 万命中档与夹具脱节", 10_000, countHits(TOKENS_TEN_K))
        assertEquals(
            "宽命中（5 万）档与夹具脱节",
            ENTRY_COUNT,
            countHits(TOKENS_WIDE),
        )
    }

    /**
     * 窄命中（12，记录）→ 100 / 1k（沿用既有 500ms×ci 门）→ 1 万 / 5 万宽命中（记录）。
     * 每个档位先断言首页非空，防"空页冒充快"。
     */
    @Test
    fun narrowTierPagesStayWithinBudgetAndWideTiersAreRecorded() = runBlocking {
        // 12 命中：记录项（不设门）——窄页在 5 万目录上的排序形态参考。
        assertEquals("12 命中档首页必须返回全部 12 行（夹具退化）", 12, pageSize(TOKENS_TWELVE))
        repeat(WARMUP_COUNT) { pageSize(TOKENS_TWELVE) }
        val twelve = List(SAMPLE_COUNT) { measureTimeMillis { pageSize(TOKENS_TWELVE) } }
        println(
            "K1 50k FTS ranking tier=12 samples=$twelve p95=${twelve.percentile95()}ms (recorded, not gated)",
        )

        // 100 命中：与既有 10k 门同值同口径（非新增门）。
        assertEquals("100 命中档首页非退化", FIRST_SCREEN_LIMIT, pageSize(TOKENS_HUNDRED))
        repeat(WARMUP_COUNT) { pageSize(TOKENS_HUNDRED) }
        val hundred = List(SAMPLE_COUNT) { measureTimeMillis { pageSize(TOKENS_HUNDRED) } }
        println(
            "K1 50k FTS ranking tier=100 samples=$hundred " +
                "p95=${hundred.percentile95()}ms budget=${PAGE_P95_BUDGET_MS}ms",
        )
        assertTrue(
            "100 命中 searchPage P95=${hundred.percentile95()}ms 超过预算 ${PAGE_P95_BUDGET_MS}ms；" +
                "samples=$hundred",
            hundred.percentile95() < PAGE_P95_BUDGET_MS,
        )

        // 1k 命中：同上。
        assertEquals("1k 命中档首页非退化", FIRST_SCREEN_LIMIT, pageSize(TOKENS_ONE_K))
        repeat(WARMUP_COUNT) { pageSize(TOKENS_ONE_K) }
        val oneK = List(SAMPLE_COUNT) { measureTimeMillis { pageSize(TOKENS_ONE_K) } }
        println(
            "K1 50k FTS ranking tier=1k samples=$oneK " +
                "p95=${oneK.percentile95()}ms budget=${PAGE_P95_BUDGET_MS}ms",
        )
        assertTrue(
            "1k 命中 searchPage P95=${oneK.percentile95()}ms 超过预算 ${PAGE_P95_BUDGET_MS}ms；" +
                "samples=$oneK",
            oneK.percentile95() < PAGE_P95_BUDGET_MS,
        )

        // 1 万命中：记录项（不设门；既有 10k 类里的 2000ms backstop 是 10k 夹具口径，不搬到这里当墙）。
        assertEquals("1 万命中档首页非退化", FIRST_SCREEN_LIMIT, pageSize(TOKENS_TEN_K))
        repeat(1) { pageSize(TOKENS_TEN_K) }
        val tenK = List(TEN_K_SAMPLE_COUNT) { measureTimeMillis { pageSize(TOKENS_TEN_K) } }
        println(
            "K1 50k FTS ranking tier=10k samples=$tenK p95=${tenK.percentile95()}ms (recorded, not gated)",
        )

        // 宽命中（5 万）：记录项（不设门）。
        assertEquals("宽命中档首页非退化", FIRST_SCREEN_LIMIT, pageSize(TOKENS_WIDE))
        repeat(1) { pageSize(TOKENS_WIDE) }
        val wide = List(WIDE_SAMPLE_COUNT) { measureTimeMillis { pageSize(TOKENS_WIDE) } }
        println(
            "K1 50k FTS ranking tier=50k-wide samples=$wide p95=${wide.percentile95()}ms " +
                "(recorded, not gated)",
        )

        println(
            "K1 50k FTS ranking fixture cost: entries=$ENTRY_COUNT seed=${FixtureCost.seedMillis}ms " +
                "markers=${FixtureCost.markerMillis}ms bootstrap=${FixtureCost.bootstrapMillis}ms",
        )
    }

    /**
     * 结构/EQP 门（K5 加固一；形态照 10k 排序门 [LibraryFtsRankingPathInstrumentedTest]）：
     * 在 **5 万行**夹具上对**生产 builder 的 live SQL**（`RoomLibrarySearchStore.buildLibrarySearchRawQuery`
     * 原文，不是手抄副本）做 EXPLAIN QUERY PLAN，钉四件事——
     * ① 排序表达式**没有逐行 FTS 相关子查询**（`CORRELATED SCALAR SUBQUERY` 头之后紧跟 FTS
     * 虚表访问行 = 逐行回探）；② 12 个命中集必须被 `MATERIALIZE hit_*`；③ 命中集按 docid
     * 走索引探测（`SEARCH hit_stem_text USING … hit_docid=?`）；④ MATCH 仍是主驱动
     * （`SCAN library_search_fts VIRTUAL TABLE`）、不得出现 `SCAN catalog` 整表扫驱动。
     * 固定红样例（改前形态的冻结文本）必须被同一判据判失败——证明判据不是恒真。
     *
     * 本用例**不设任何计时门**。
     */
    @Test
    fun rankingPlanKeepsMaterializedHitSetsOnThe50kFixture() {
        val liveSql = RoomLibrarySearchStore(store.database).buildLibrarySearchRawQuery(
            matchQuery = CjkTextTokenizer.matchExpression(TOKENS_WIDE.joinToString(" ")),
            subjectId = null,
            sectionId = null,
            masteryId = null,
            createdFromEpochMillis = null,
            createdToEpochMillis = null,
            sort = "RECENTLY_UPDATED",
            tokens = TOKENS_WIDE,
            limit = FIRST_SCREEN_LIMIT,
            offset = 0,
        ).sql
        val plan = readPlans(context, databaseName, liveSql)
        println("K1 50k FTS ranking plan[live]:\n${plan.joinToString("\n") { "  $it" }}")

        // ① 不得退化为逐行 FTS 相关子查询。
        val rowLevelProbes = rowLevelFtsProbesOf(plan)
        assertTrue(
            "排序路径退化为逐行 FTS 相关子查询（50k live 计划）：\n" +
                "${rowLevelProbes.joinToString("\n")}\n完整计划：\n${plan.joinToString("\n")}",
            rowLevelProbes.isEmpty(),
        )
        // 红样例（改前形态的冻结文本，版本无关）：同一判据必须判失败。
        assertFalse(
            "红样例（逐行回探形态）被本判据误判为通过（门失效）：\n" +
                ROW_PROBE_PLAN_RED_SAMPLE.joinToString("\n"),
            rowLevelFtsProbesOf(ROW_PROBE_PLAN_RED_SAMPLE).isEmpty(),
        )
        // ② 12 个命中集各 MATERIALIZE 一次（退化回相关子查询时这里先红）。
        assertEquals(
            "命中集未按 9 列 + 3 额外 token 物化：\n${plan.joinToString("\n")}",
            HIT_SET_COUNT,
            plan.count { it.startsWith("MATERIALIZE hit_") },
        )
        // ③ 命中集物化后按 docid 的自动覆盖索引探测。
        assertTrue(
            "命中集物化后没有按 docid 的探测行（索引使用消失）：\n${plan.joinToString("\n")}",
            plan.any { "SEARCH hit_stem_text USING" in it && "hit_docid=?" in it },
        )
        // ④ MATCH 主驱动仍在，且 catalog 不得整表扫。
        assertTrue(
            "排序路径的 FTS 主扫描消失（MATCH 不再是驱动）：\n${plan.joinToString("\n")}",
            plan.any { FTS_MAIN_SCAN.containsMatchIn(it) },
        )
        assertFalse(
            "排序路径出现 SCAN catalog 整表扫驱动：\n${plan.joinToString("\n")}",
            plan.any { CATALOG_SCAN.containsMatchIn(it) },
        )
    }

    /**
     * 判据：紧跟在 `CORRELATED SCALAR SUBQUERY n` 头之后的 FTS 虚表访问行 = 逐行探测
     * （与 10k 排序门同口径）。改前计划的 12 个排序探测都是这种"头 + 一行 FTS 扫描"的形状；
     * 新计划的 FTS 集合扫描行前面是 `MATERIALIZE hit_*`（或另一条 FTS 行），不会被误判。
     */
    private fun rowLevelFtsProbesOf(plan: List<String>): List<String> = plan.filterIndexed { index, line ->
        index > 0 &&
            FTS_VIRTUAL_TABLE_LINE.containsMatchIn(line) &&
            CORRELATED_SUBQUERY_HEADER.containsMatchIn(plan[index - 1])
    }

    /** 平台连接读 EXPLAIN QUERY PLAN（room3 prepared-statement 路径拒绝 EXPLAIN）。 */
    private fun readPlans(context: Context, databaseName: String, sql: String): List<String> =
        SQLiteDatabase.openDatabase(
            context.getDatabasePath(databaseName).absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY,
        ).use { database ->
            database.rawQuery(
                "EXPLAIN QUERY PLAN $sql",
                Array(sql.count { it == '?' }) { NO_FILTER_PLACEHOLDER },
            ).use { cursor ->
                val detail = cursor.getColumnIndexOrThrow("detail")
                buildList { while (cursor.moveToNext()) add(cursor.getString(detail)) }
            }
        }

    /** 一档的实际首页行数（非退化信号）；计时只测生产端口路径 `librarySearchPage`。 */
    private suspend fun pageSize(tokens: List<String>): Int = store.librarySearchPage(
        matchQuery = CjkTextTokenizer.matchExpression(tokens.joinToString(separator = " ")),
        subjectId = null,
        sectionId = null,
        masteryId = null,
        createdFromEpochMillis = null,
        createdToEpochMillis = null,
        sort = "RECENTLY_UPDATED",
        tokens = tokens,
        offset = 0,
        limit = FIRST_SCREEN_LIMIT,
    ).size

    private suspend fun countHits(tokens: List<String>): Int = store.librarySearchCount(
        matchQuery = CjkTextTokenizer.matchExpression(tokens.joinToString(separator = " ")),
        subjectId = null,
        sectionId = null,
        masteryId = null,
        createdFromEpochMillis = null,
        createdToEpochMillis = null,
    )

    private fun List<Long>.percentile95(): Long {
        require(isNotEmpty())
        val sorted = sorted()
        return sorted[((sorted.size - 1) * 95) / 100]
    }

    private object FixtureCost {
        var seedMillis: Long = -1
        var markerMillis: Long = -1
        var bootstrapMillis: Long = -1
    }

    companion object {
        private const val ENTRY_COUNT = 50_000
        private const val FIRST_SCREEN_LIMIT = 20
        private const val SAMPLE_COUNT = 20
        private const val WARMUP_COUNT = 3
        private const val TEN_K_SAMPLE_COUNT = 10
        private const val WIDE_SAMPLE_COUNT = 5

        /** 100 命中：夹具每 500 行一条"独特检索词"（见 `seedLibraryCatalogScale` 的间隔参数）。 */
        private val TOKENS_HUNDRED = CjkTextTokenizer.tokens("独特检索")

        /** 1k 命中：每第 50 行注入。 */
        private const val ONE_K_MARKER = "千级探针"
        private val TOKENS_ONE_K = CjkTextTokenizer.tokens(ONE_K_MARKER)

        /** 1 万命中：每第 5 行注入。 */
        private const val TEN_K_MARKER = "万级探针"
        private val TOKENS_TEN_K = CjkTextTokenizer.tokens(TEN_K_MARKER)

        /** 12 命中：显式 12 行注入（夹具内唯一含"窄带"两字的文本）。 */
        private const val TWELVE_MARKER = "窄带探针十二"
        private val TOKENS_TWELVE = CjkTextTokenizer.tokens(TWELVE_MARKER)
        private val TWELVE_REVISION_IDS = listOf(
            "revision-3", "revision-17", "revision-29", "revision-53",
            "revision-101", "revision-211", "revision-337", "revision-503",
            "revision-719", "revision-1031", "revision-2417", "revision-7919",
        )

        private val TOKENS_WIDE = CjkTextTokenizer.tokens("分页题目")

        /** 命中集数：9 个列级标志 + 3 个额外 token 标志（与 10k 排序门同口径）。 */
        private const val HIT_SET_COUNT = 12

        /** EXPLAIN 绑定用的哨兵（计划在 prepare 期已定、与绑定值无关，同既有门口径）。 */
        private const val NO_FILTER_PLACEHOLDER = "\u0001none"

        private val CORRELATED_SUBQUERY_HEADER = Regex("""^CORRELATED SCALAR SUBQUERY\b""")
        private val FTS_VIRTUAL_TABLE_LINE = Regex(
            """(?i)^(SCAN|SEARCH)\s+(TABLE\s+)?(library_search_fts|ranked|fts)\s+VIRTUAL TABLE\b""",
        )
        private val FTS_MAIN_SCAN = Regex(
            """(?i)^SCAN\s+(TABLE\s+)?(library_search_fts|fts)\s+VIRTUAL TABLE\b""",
        )
        private val CATALOG_SCAN = Regex(
            """(?i)^SCAN\s+(TABLE\s+)?(catalog|library_catalog)(\s|$)""",
        )

        /**
         * 红样例（固定文本、版本无关）：改前排序形态的冻结片段——主循环 FTS 之后，
         * 排序标志是 `CORRELATED SCALAR SUBQUERY` + 一行 `SCAN ranked VIRTUAL TABLE`
         * （逐命中行回探 FTS）。判据 [rowLevelFtsProbesOf] 对它必须判失败。
         */
        private val ROW_PROBE_PLAN_RED_SAMPLE: List<String> = listOf(
            "MATERIALIZE library_catalog",
            "SCAN library_search_fts VIRTUAL TABLE INDEX 11:",
            "SEARCH content USING INTEGER PRIMARY KEY (rowid=?)",
            "CORRELATED SCALAR SUBQUERY 1",
            "SCAN ranked VIRTUAL TABLE INDEX 2:",
        )

        private val CI_MULTIPLIER: Long = run {
            val fromArgs = androidx.test.platform.app.InstrumentationRegistry
                .getArguments()
                .getString("ciSlowRunner")
            if (fromArgs != null || System.getenv("CI") != null) 4L else 1L
        }

        /** 与 `PerformanceGateTest.SEARCH_P95_TARGET_MS` 同值同口径（沿用既有 100/1k 门）。 */
        private val PAGE_P95_BUDGET_MS = 500L * CI_MULTIPLIER

        private lateinit var context: Context
        private lateinit var store: RoomStudyDatabase
        private lateinit var databaseName: String

        @JvmStatic
        @BeforeClass
        fun buildSharedFixture() {
            context = ApplicationProvider.getApplicationContext()
            databaseName = "k1-fts-ranking-50k-${System.nanoTime()}.db"
            context.deleteDatabase(databaseName)
            runBlocking {
                store = StudyDatabaseFactory.open(context, databaseName) as RoomStudyDatabase
                FixtureCost.seedMillis = measureTimeMillis {
                    store.database.seedLibraryCatalogScale(
                        count = ENTRY_COUNT,
                        boundedTokenEvery = 500,
                    )
                }
                FixtureCost.markerMillis = measureTimeMillis {
                    store.database.withWriteTransaction {
                        usePrepared(
                            "UPDATE problem_revision " +
                                "SET problem_markdown = problem_markdown || ' $ONE_K_MARKER' " +
                                "WHERE CAST(substr(revision_id, 10) AS INTEGER) % 50 = 0",
                        ) { statement -> statement.step() }
                        usePrepared(
                            "UPDATE problem_revision " +
                                "SET problem_markdown = problem_markdown || ' $TEN_K_MARKER' " +
                                "WHERE CAST(substr(revision_id, 10) AS INTEGER) % 5 = 0",
                        ) { statement -> statement.step() }
                        val placeholders = TWELVE_REVISION_IDS.joinToString(",") { "?" }
                        usePrepared(
                            "UPDATE problem_revision " +
                                "SET problem_markdown = problem_markdown || ' $TWELVE_MARKER' " +
                                "WHERE revision_id IN ($placeholders)",
                        ) { statement ->
                            TWELVE_REVISION_IDS.forEachIndexed { index, id ->
                                statement.bindText(index + 1, id)
                            }
                            statement.step()
                        }
                    }
                }
                FixtureCost.bootstrapMillis = measureTimeMillis { store.refreshLibrarySearchProjection() }
                store.database.withRawConnection(isReadOnly = true) { connection ->
                    connection.usePrepared("SELECT 1") { statement -> statement.step() }
                }
                println(
                    "K1 50k FTS ranking-path fixture built: $ENTRY_COUNT entries " +
                        "seed=${FixtureCost.seedMillis}ms markers=${FixtureCost.markerMillis}ms " +
                        "bootstrap=${FixtureCost.bootstrapMillis}ms",
                )
            }
        }

        @JvmStatic
        @AfterClass
        fun closeSharedFixture() {
            if (::store.isInitialized) {
                store.close()
            }
            context.deleteDatabase(databaseName)
        }
    }
}
