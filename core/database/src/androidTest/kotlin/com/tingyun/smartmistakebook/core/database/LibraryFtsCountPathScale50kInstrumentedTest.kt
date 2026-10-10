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
 * **K1 批 1 · 50k FTS 规模补测（count 路径）**：S18 尾的 1 万行夹具（[LibraryFtsCountPathInstrumentedTest]）
 * 上"`MATERIALIZE` 一次成本随目录行数线性"只有推断，没有 5 万行实测。本类把同一夹具/查询形态
 * 扩到 **5 万行**并钉两件事：**计时记录**与 **结构/EQP 断言**（K5 加固一——50k 档从此不止证计时）。
 *
 * - **为什么独立成类**：10k 的既有门类带 13 组等价性矩阵（含改前 SQL 逐行回探，单次宽命中 15s
 *   量级）+ EQP 结构门；同一矩阵在 50k 上会成倍膨胀，且会让既有 10k 门（S18 锚点）的
 *   "100 命中 P95"计时与夹具形态脱钩。既有 10k 门**一字不改**，50k 在这里单独建库、单独记录。
 * - **门口径**：100 命中仍是门（P95 ≤ 500ms×ciSlowRunner，与 `PerformanceGateTest` 同值同口径）；
 *   1 万命中与 **5 万命中**只记录（打印 P95），**不设新门**——"5 万命中 count 该多快"没有
 *   需求侧预算，拿一次模拟器读数当墙会把抖动变成假红。**不新增任何计时门**。
 * - **结构/EQP（K5 加固一）**：四条改写查询（count + 三个分面）在 5 万行夹具上的
 *   EXPLAIN QUERY PLAN 必须满足"MATCH 驱动"四合取判据（形态与判据逐条照 10k 门
 *   [LibraryFtsCountPathInstrumentedTest]）；固定红样例必须被同一判据判失败（防判据恒真）。
 *   副本与 DAO 的一致性由 JVM 守卫 `LibraryFtsCountPathQueryCopyContractTest` 同时钉住
 *   10k / 50k 两份副本。
 * - **夹具**：`seedLibraryCatalogScale(50_000, boundedTokenEvery = 500)` ⇒ "独特检索"仍恰好
 *   **100 命中**（不是 500）；"分页题目"= 全量 **5 万命中**；"万级探针"注入每第 5 行 ⇒
 *   **1 万命中**（单次 bootstrap 前注入）。三项命中数与 FTS MATCH 探针逐条断言，防夹具退化。
 */
@RunWith(AndroidJUnit4::class)
class LibraryFtsCountPathScale50kInstrumentedTest {

    /** 夹具非退化信号：FTS **索引**必须真的覆盖（对外部内容表 COUNT(*) 恒等于行数，证明不了索引）。 */
    @Test
    fun fixtureCoversAllThreeHitTiers() = runBlocking {
        assertEquals(
            "100 命中档与夹具脱节（MATCH 探针）",
            BOUNDED_MATCH_HITS.toLong(),
            scalarLong(
                "SELECT COUNT(*) FROM library_search_fts WHERE library_search_fts MATCH :matchQuery",
                BOUNDED_MATCH,
            ),
        )
        assertEquals(
            "1 万命中档与夹具脱节（MATCH 探针）",
            MID_MATCH_HITS.toLong(),
            scalarLong(
                "SELECT COUNT(*) FROM library_search_fts WHERE library_search_fts MATCH :matchQuery",
                MID_MATCH,
            ),
        )
        assertEquals(
            "5 万命中档与夹具脱节（MATCH 探针）",
            ENTRY_COUNT.toLong(),
            scalarLong(
                "SELECT COUNT(*) FROM library_search_fts WHERE library_search_fts MATCH :matchQuery",
                WIDE_MATCH,
            ),
        )
        assertEquals(100, boundedCount())
        assertEquals(MID_MATCH_HITS, midCount())
        assertEquals(ENTRY_COUNT, wideCount())
    }

    /**
     * 100 命中（门，与 10k 类同值同口径）+ 1 万命中与 5 万命中（记录，不设门）。
     * 夹具建库成本（seed / marker / bootstrap）随第一次用例打印。
     */
    @Test
    fun boundedCountStaysWithinBudgetAndWideCountsAreRecorded() = runBlocking {
        assertEquals(
            "计时门与夹具脱节：100 命中口径的计数不是 $BOUNDED_MATCH_HITS",
            BOUNDED_MATCH_HITS,
            boundedCount(),
        )

        repeat(COUNT_WARMUP) { boundedCount() }
        val samples = List(COUNT_SAMPLES) { measureTimeMillis { boundedCount() } }
        val p95 = samples.percentile95()
        println(
            "K1 50k FTS count benchmark: match=100hits entries=$ENTRY_COUNT " +
                "samples=$samples p95=${p95}ms budget=${COUNT_P95_BUDGET_MS}ms",
        )
        assertTrue(
            "100 命中 count P95=${p95}ms 超过预算 ${COUNT_P95_BUDGET_MS}ms；samples=$samples",
            p95 < COUNT_P95_BUDGET_MS,
        )

        // 1 万命中：记录项（不设门）。
        repeat(WIDE_WARMUP) { midCount() }
        val midSamples = List(WIDE_SAMPLES) { measureTimeMillis { midCount() } }
        println(
            "K1 50k FTS count mid-hits record (not gated): match=10000hits " +
                "samples=$midSamples p95=${midSamples.percentile95()}ms",
        )

        // 5 万命中：记录项（不设门）。
        repeat(WIDE_WARMUP) { wideCount() }
        val wideSamples = List(WIDE_SAMPLES) { measureTimeMillis { wideCount() } }
        println(
            "K1 50k FTS count wide-hits record (not gated): match=50000hits " +
                "samples=$wideSamples p95=${wideSamples.percentile95()}ms",
        )

        println(
            "K1 50k FTS count fixture cost: entries=$ENTRY_COUNT seed=${FixtureCost.seedMillis}ms " +
                "marker=${FixtureCost.markerMillis}ms bootstrap=${FixtureCost.bootstrapMillis}ms",
        )
    }

    /**
     * 结构/EQP 门（K5 加固一；判据与形态逐条照 10k 门 [LibraryFtsCountPathInstrumentedTest]）：
     * 四条改写查询（count + 三个分面 SQL）在 **5 万行**夹具上的 EXPLAIN QUERY PLAN 必须满足
     * "MATCH 驱动"四合取判据——① FTS 虚表循环是主程序第一个循环（`MATERIALIZE library_catalog`
     * 作为一次性物化子程序列在最前不判失败）；② 无 `SCAN catalog` 驱动；③ catalog 只被
     * `SEARCH … INDEX (problem_revision_id=?)` 探测；④ FTS 行使用**非 0** 的
     * `VIRTUAL TABLE INDEX <n>`（`INDEX 0:` = 未用 MATCH 索引，判失败）。
     *
     * 红样例（固定文本、版本无关）必须被同一判据判失败——证明这段 50k 判据不是恒真：
     * 一条 `SCAN catalog` 驱动形态、一条 `INDEX 0:` 形态。
     *
     * 50k 的计时结论因此建立在"驱动结构未随规模退化"之上；本用例**不设任何计时门**。
     */
    @Test
    fun ftsDrivesTheJoinOnThe50kFixture() {
        val plans = readPlans(context, databaseName, EQP_QUERIES)
        plans.forEach { (name, plan) ->
            println("K1 50k FTS count/facet plan[$name]:\n${plan.joinToString("\n") { "  $it" }}")
        }
        plans.forEach { (name, plan) ->
            assertTrue(
                "$name：MATCH 未驱动（四合取判据失败）：\n${plan.joinToString("\n")}",
                ftsDrivesTheJoin(plan),
            )
        }

        assertFalse(
            "红样例（SCAN catalog 驱动）被本判据误判为通过（门失效）：\n" +
                SCAN_CATALOG_PLAN_RED_SAMPLE.joinToString("\n"),
            ftsDrivesTheJoin(SCAN_CATALOG_PLAN_RED_SAMPLE),
        )
        assertFalse(
            "红样例（FTS 行 VIRTUAL TABLE INDEX 0:，未用 MATCH 索引）被本判据误判为通过" +
                "（门失效）：\n" + ZERO_INDEX_PLAN_RED_SAMPLE.joinToString("\n"),
            ftsDrivesTheJoin(ZERO_INDEX_PLAN_RED_SAMPLE),
        )
    }

    /**
     * "MATCH 驱动"判据（四合取），与 10k 门逐条同口径（见该类的 KDoc 与口径迁移记录）：
     * 对红样例必须为 false、对现场计划必须为 true——这是门能发现退化的证明。
     */
    private fun ftsDrivesTheJoin(plan: List<String>): Boolean {
        val ftsIndex = plan.indexOfFirst { FTS_TABLE_LOOP.containsMatchIn(it) }
        if (ftsIndex < 0) return false
        // ① FTS 是主程序第一个循环：FTS 之前不得有 content/catalog 的循环行；
        //    其它循环行只允许属于 MATERIALIZE 一次性子程序（列在 MATERIALIZE 行之后）。
        val contentOrCatalogIndex = plan.indexOfFirst { CONTENT_OR_CATALOG_LOOP.containsMatchIn(it) }
        if (contentOrCatalogIndex in 0 until ftsIndex) return false
        val materializeIndex = plan.indexOfFirst { MATERIALIZE_LINE.containsMatchIn(it) }
        val loopsBeforeFts = (0 until ftsIndex).filter { LOOP_LINE.containsMatchIn(plan[it]) }
        if (loopsBeforeFts.any { it < materializeIndex }) return false
        // ② catalog 不再是驱动：不得有 SCAN catalog。
        if (plan.any { CATALOG_SCAN.containsMatchIn(it) }) return false
        // ③ catalog 只被索引探测（problem_revision_id 等值约束）。
        val catalogProbe = plan.firstOrNull { CATALOG_SEARCH.containsMatchIn(it) } ?: return false
        if ("problem_revision_id=?" !in catalogProbe) return false
        // ④ FTS 行必须真的用了 MATCH 索引：`VIRTUAL TABLE INDEX <n>` 且 n ≠ 0。
        val ftsIndexNumber = FTS_VIRTUAL_INDEX.find(plan[ftsIndex])?.groupValues?.get(1)
        return ftsIndexNumber != null && ftsIndexNumber != "0"
    }

    private fun readPlans(
        context: Context,
        databaseName: String,
        queries: Map<String, String>,
    ): Map<String, List<String>> = SQLiteDatabase.openDatabase(
        context.getDatabasePath(databaseName).absolutePath,
        null,
        SQLiteDatabase.OPEN_READONLY,
    ).use { database ->
        queries.mapValues { (_, sql) ->
            database.rawQuery("EXPLAIN QUERY PLAN $sql", sentinelArgs(sql)).use { cursor ->
                val detail = cursor.getColumnIndexOrThrow("detail")
                buildList {
                    while (cursor.moveToNext()) add(cursor.getString(detail))
                }
            }
        }
    }

    /**
     * 平台 rawQuery 的 String[] 绑定拒绝 null，而计划在 prepare 期已定、与绑定值无关
     * （同 10k 门口径）：全部命名参数绑同一个哨兵串。
     */
    private fun sentinelArgs(sql: String): Array<String?> = Array(
        NAMED_PARAMETER.findAll(sql).map { it.groupValues[1] }.distinct().count(),
    ) { NO_FILTER_PLACEHOLDER }

    private suspend fun boundedCount(): Int = store.librarySearchCount(
        matchQuery = BOUNDED_MATCH,
        subjectId = null,
        sectionId = null,
        masteryId = null,
        createdFromEpochMillis = null,
        createdToEpochMillis = null,
    )

    private suspend fun midCount(): Int = store.librarySearchCount(
        matchQuery = MID_MATCH,
        subjectId = null,
        sectionId = null,
        masteryId = null,
        createdFromEpochMillis = null,
        createdToEpochMillis = null,
    )

    private suspend fun wideCount(): Int = store.librarySearchCount(
        matchQuery = WIDE_MATCH,
        subjectId = null,
        sectionId = null,
        masteryId = null,
        createdFromEpochMillis = null,
        createdToEpochMillis = null,
    )

    private suspend fun scalarLong(sql: String, matchQuery: String): Long {
        var value = -1L
        store.database.withRawConnection(isReadOnly = true) { connection ->
            connection.usePrepared(sql) { statement ->
                statement.bindText(1, matchQuery)
                if (statement.step()) {
                    value = statement.getLong(0)
                }
            }
        }
        return value
    }

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
        private const val BOUNDED_MATCH_HITS = 100
        private const val MID_MATCH_HITS = 10_000

        /** 每第 [BOUNDED_TOKEN_EVERY] 行一条"独特检索词" ⇒ 5 万行恰好 100 命中。 */
        private const val BOUNDED_TOKEN_EVERY = 500

        /** 1 万命中探针：每第 5 行注入（`revision_id` 序号 % 5 == 0）。 */
        private const val MID_MARKER = "万级探针"

        private const val COUNT_WARMUP = 10
        private const val COUNT_SAMPLES = 30
        private const val WIDE_WARMUP = 3
        private const val WIDE_SAMPLES = 5

        private val CI_MULTIPLIER: Long = run {
            val fromArgs = androidx.test.platform.app.InstrumentationRegistry
                .getArguments()
                .getString("ciSlowRunner")
            if (fromArgs != null || System.getenv("CI") != null) 4L else 1L
        }

        /** 与 `PerformanceGateTest.SEARCH_P95_TARGET_MS` 同值同口径。 */
        private val COUNT_P95_BUDGET_MS = 500L * CI_MULTIPLIER

        /** 100 命中：夹具里每 500 行一条"独特检索词 N"（末字与数字粘连，取"独特检索"）。 */
        private val BOUNDED_MATCH = CjkTextTokenizer.matchExpression("独特检索")

        /** 1 万命中：注入的"万级探针"分词后 万/级/探/针 各自成词。 */
        private val MID_MATCH = CjkTextTokenizer.matchExpression("万级探针")

        /** 5 万命中：每条目录行的标题都是"分页题目 N"。 */
        private val WIDE_MATCH = CjkTextTokenizer.matchExpression("分页题目")

        /**
         * 结构/EQP 门用的四条改写查询（改后 CROSS_JOIN 形态）：副本逐字来自
         * `LibraryFtsSearchDao` 的 `@Query`，由 JVM 守卫 `LibraryFtsCountPathQueryCopyContractTest`
         * 同时钉住 10k / 50k 两份副本（守卫缺失 = EQP 门可能测着旧 SQL 报绿）。
         * `by lazy`：引用文件末尾的 SQL 副本常量，companion 属性按声明顺序初始化。
         */
        private val EQP_QUERIES: Map<String, String> by lazy {
            mapOf(
                "count-search" to CROSS_JOIN_COUNT_SEARCH_SQL,
                "subject-facets" to CROSS_JOIN_SUBJECT_FACETS_SQL,
                "section-facets" to CROSS_JOIN_SECTION_FACETS_SQL,
                "mastery-facets" to CROSS_JOIN_MASTERY_FACETS_SQL,
            )
        }

        /** 命名参数扫描（与 10k 门同一口径）：同名参数在 SQLite 里只占一个绑定位。 */
        private val NAMED_PARAMETER = Regex(""":([A-Za-z_][A-Za-z0-9_]*)""")

        private const val NO_FILTER_PLACEHOLDER = "\u0001none"

        /** 设备 EQP 词表的两代形态：`SCAN <alias>` 与 `SCAN TABLE <name>`。 */
        private val LOOP_LINE = Regex("""(?i)^(SCAN|SEARCH|MATERIALIZE)\b""")
        private val FTS_TABLE_LOOP = Regex(
            """(?i)^(SCAN|SEARCH)\s+(TABLE\s+)?(library_search_fts|fts)(\s|$)""",
        )
        private val CONTENT_OR_CATALOG_LOOP = Regex(
            """(?i)^(SCAN|SEARCH)\s+(TABLE\s+)?(content|library_search_content|catalog|library_catalog)(\s|$)""",
        )
        private val MATERIALIZE_LINE = Regex("""(?i)^MATERIALIZE\s+\S+""")
        private val CATALOG_SCAN = Regex(
            """(?i)^SCAN\s+(TABLE\s+)?(catalog|library_catalog)(\s|$)""",
        )
        private val CATALOG_SEARCH = Regex(
            """(?i)^SEARCH\s+(TABLE\s+)?(catalog|library_catalog)(\s|$)""",
        )

        /** FTS 计划行的索引号：`VIRTUAL TABLE INDEX <n>:`；`INDEX 0:` = 未用 MATCH，判失败。 */
        private val FTS_VIRTUAL_INDEX = Regex("""VIRTUAL TABLE INDEX\s+(\d+)""")

        /** 红样例（固定文本、版本无关）：`SCAN catalog` 当驱动——四合取判据必须判失败。 */
        private val SCAN_CATALOG_PLAN_RED_SAMPLE: List<String> = listOf(
            "MATERIALIZE library_catalog",
            "SCAN catalog",
            "SEARCH content USING COVERING INDEX " +
                "index_library_search_content_problem_revision_id (problem_revision_id=?)",
            "SCAN library_search_fts VIRTUAL TABLE INDEX 11:",
        )

        /** 红样例 2（固定文本）：FTS 行 `VIRTUAL TABLE INDEX 0:`——满足①②③，只有合取④能判失败。 */
        private val ZERO_INDEX_PLAN_RED_SAMPLE: List<String> = listOf(
            "MATERIALIZE library_catalog",
            "SCAN library_search_fts VIRTUAL TABLE INDEX 0:",
            "SEARCH catalog USING AUTOMATIC COVERING INDEX (problem_revision_id=?)",
        )

        // --------------------------------------------------------------
        // 手抄 SQL 副本（改后 CROSS_JOIN 形态；与 10k 门 / DAO 逐字一致，
        // 由 LibraryFtsCountPathQueryCopyContractTest 守卫）
        // --------------------------------------------------------------

        /** 改后 `countSearch` 的逐字副本（FTS 最左 CROSS JOIN 驱动）。 */
        private val CROSS_JOIN_COUNT_SEARCH_SQL: String = """
            SELECT COUNT(*) FROM library_search_fts CROSS JOIN library_search_content AS content ON content.content_row_id = library_search_fts.docid CROSS JOIN library_catalog AS catalog ON catalog.problem_revision_id = content.problem_revision_id WHERE library_search_fts MATCH :matchQuery AND (:subjectId IS NULL OR catalog.subject = :subjectId) AND ( :sectionId IS NULL OR EXISTS ( SELECT 1 FROM problem_classification_binding AS classification WHERE classification.problem_id = catalog.problem_id AND classification.basis_revision_id = catalog.problem_revision_id AND classification.dimension = 'CHAPTER' AND classification.label_id = :sectionId ) ) AND (:masteryId IS NULL OR catalog.mastery_id = :masteryId) AND ( :createdFromEpochMillis IS NULL OR catalog.created_at_epoch_millis >= :createdFromEpochMillis ) AND ( :createdToEpochMillis IS NULL OR catalog.created_at_epoch_millis <= :createdToEpochMillis )
        """.trimIndent()

        /** 改后 `searchSubjectFacets` 的逐字副本。 */
        private val CROSS_JOIN_SUBJECT_FACETS_SQL: String = """
            SELECT catalog.subject AS id, catalog.subject AS label, COUNT(*) AS count FROM library_search_fts CROSS JOIN library_search_content AS content ON content.content_row_id = library_search_fts.docid CROSS JOIN library_catalog AS catalog ON catalog.problem_revision_id = content.problem_revision_id WHERE library_search_fts MATCH :matchQuery AND (:sectionId IS NULL OR EXISTS (SELECT 1 FROM problem_classification_binding AS c WHERE c.problem_id = catalog.problem_id AND c.basis_revision_id = catalog.problem_revision_id AND c.dimension = 'CHAPTER' AND c.label_id = :sectionId)) AND (:masteryId IS NULL OR catalog.mastery_id = :masteryId) AND (:createdFromEpochMillis IS NULL OR catalog.created_at_epoch_millis >= :createdFromEpochMillis) AND (:createdToEpochMillis IS NULL OR catalog.created_at_epoch_millis <= :createdToEpochMillis) GROUP BY catalog.subject ORDER BY COUNT(*) DESC, id ASC
        """.trimIndent()

        /** 改后 `searchSectionFacets` 的逐字副本。 */
        private val CROSS_JOIN_SECTION_FACETS_SQL: String = """
            SELECT classification.label_id AS id, classification.display_name AS label, COUNT(*) AS count FROM library_search_fts CROSS JOIN library_search_content AS content ON content.content_row_id = library_search_fts.docid CROSS JOIN library_catalog AS catalog ON catalog.problem_revision_id = content.problem_revision_id JOIN problem_classification_binding AS classification ON classification.problem_id = catalog.problem_id AND classification.basis_revision_id = catalog.problem_revision_id AND classification.dimension = 'CHAPTER' WHERE library_search_fts MATCH :matchQuery AND (:subjectId IS NULL OR catalog.subject = :subjectId) AND (:masteryId IS NULL OR catalog.mastery_id = :masteryId) AND (:createdFromEpochMillis IS NULL OR catalog.created_at_epoch_millis >= :createdFromEpochMillis) AND (:createdToEpochMillis IS NULL OR catalog.created_at_epoch_millis <= :createdToEpochMillis) GROUP BY classification.label_id, classification.display_name ORDER BY COUNT(*) DESC, id ASC
        """.trimIndent()

        /** 改后 `searchMasteryFacets` 的逐字副本。 */
        private val CROSS_JOIN_MASTERY_FACETS_SQL: String = """
            SELECT catalog.mastery_id AS id, catalog.mastery_id AS label, COUNT(*) AS count FROM library_search_fts CROSS JOIN library_search_content AS content ON content.content_row_id = library_search_fts.docid CROSS JOIN library_catalog AS catalog ON catalog.problem_revision_id = content.problem_revision_id WHERE library_search_fts MATCH :matchQuery AND (:subjectId IS NULL OR catalog.subject = :subjectId) AND (:sectionId IS NULL OR EXISTS (SELECT 1 FROM problem_classification_binding AS c WHERE c.problem_id = catalog.problem_id AND c.basis_revision_id = catalog.problem_revision_id AND c.dimension = 'CHAPTER' AND c.label_id = :sectionId)) AND (:createdFromEpochMillis IS NULL OR catalog.created_at_epoch_millis >= :createdFromEpochMillis) AND (:createdToEpochMillis IS NULL OR catalog.created_at_epoch_millis <= :createdToEpochMillis) GROUP BY catalog.mastery_id ORDER BY COUNT(*) DESC, id ASC
        """.trimIndent()

        private lateinit var context: Context
        private lateinit var store: RoomStudyDatabase
        private lateinit var databaseName: String

        @JvmStatic
        @BeforeClass
        fun buildSharedFixture() {
            context = ApplicationProvider.getApplicationContext()
            databaseName = "k1-fts-count-50k-${System.nanoTime()}.db"
            context.deleteDatabase(databaseName)
            runBlocking {
                store = StudyDatabaseFactory.open(context, databaseName) as RoomStudyDatabase
                FixtureCost.seedMillis = measureTimeMillis {
                    store.database.seedLibraryCatalogScale(
                        count = ENTRY_COUNT,
                        boundedTokenEvery = BOUNDED_TOKEN_EVERY,
                    )
                }
                FixtureCost.markerMillis = measureTimeMillis {
                    store.database.withWriteTransaction {
                        usePrepared(
                            "UPDATE problem_revision " +
                                "SET problem_markdown = problem_markdown || ' $MID_MARKER' " +
                                "WHERE CAST(substr(revision_id, 10) AS INTEGER) % 5 = 0",
                        ) { statement -> statement.step() }
                    }
                }
                FixtureCost.bootstrapMillis = measureTimeMillis { store.refreshLibrarySearchProjection() }
                // Room 惰性打开：借一次读把 schema/连接立起来。
                store.database.withRawConnection(isReadOnly = true) { connection ->
                    connection.usePrepared("SELECT 1") { statement -> statement.step() }
                }
                println(
                    "K1 50k FTS count-path fixture built: $ENTRY_COUNT entries " +
                        "seed=${FixtureCost.seedMillis}ms marker=${FixtureCost.markerMillis}ms " +
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
