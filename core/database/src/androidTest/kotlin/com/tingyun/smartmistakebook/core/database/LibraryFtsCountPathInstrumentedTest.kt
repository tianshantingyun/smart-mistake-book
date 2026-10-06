package com.tingyun.smartmistakebook.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
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
 * **S18 尾批 1 门**（`docs/research/2026-10-06-s18-fts-search-plan.md` §3 批 1）：
 * `LibraryFtsSearchDao.countSearch` 与三个 FTS 分面查询由"目录视图驱动 + 逐行回探 FTS"
 * 改为 `CROSS JOIN` 顺序锁（FTS 最左）后，本类在真实 1 万行库上同时钉三件事：
 *
 * 1. **等价性（主门）**：同一真库、同一批行，生产端口路径（`librarySearchCount` /
 *    `librarySearchFacets`）与改前 SQL 原文（`PRE_CHANGE_*`，git 59fd8a32 的逐字副本，
 *    副本新鲜度由 JVM 守卫 `LibraryFtsCountPathQueryCopyContractTest` 钉住）逐列/逐行
 *    对照——count 相等，三个分面 id/label/count 排序逐行相等；筛选组合抽样矩阵覆盖
 *    subject/section/mastery/createdFrom/createdTo 的全空/单筛/组合，并含 1 万命中与
 *    零命中两档。
 * 2. **驱动结构（主门）**：EXPLAIN QUERY PLAN 断言"MATCH 驱动"三合取判据——FTS 虚表循环
 *    是主程序第一个循环、计划里没有 `SCAN catalog` 驱动、catalog 只被
 *    `SEARCH catalog USING … INDEX (problem_revision_id=?)` 探测；同一判据对**改前 SQL**
 *    的旧计划必须判失败（红样例，证可证伪）。口径迁移说明见
 *    [ftsScanDrivesTheJoinForEveryRewrittenQuery]（"MATERIALIZE 消失或后置"的字面项
 *    在设备 3.39.2 上不成立：该视图不能展平，物化是一次性且必然列在最前）。
 * 3. **计时 backstop**：100 命中 count（生产 totalCount 口径，含 `refreshProjection`）
 *    P95 ≤ 500ms×ciSlowRunner；1 万命中与设备 SQLite 基线只记录、不设断言。
 *    实测（2026-10-07，test_device AVD API 34；全类 5/5 绿的那一轮）：夹具建库
 *    seed=47898ms + bootstrap=19746ms；100 命中 count P95=91ms（样本 66–97ms）；
 *    1 万命中 P95=95ms；改前 SQL 同夹具单次 13936ms；设备 SQLite 3.39.2、fts4=true、
 *    fts5=false、`sqlite_compileoption_used` 不可用（`SQLITE_OMIT_COMPILEOPTION_DIAGNOSTICS`）。
 *
 * 夹具：`seedLibraryCatalogScale(10_000)`（problems/revisions/units/entries + 分类 +
 * 掌握态齐全，见 [LibraryCatalogScale] 的"必须补齐"说明）+ 一次
 * `refreshLibrarySearchProjection()` bootstrap；类级共享、只读。
 */
@RunWith(AndroidJUnit4::class)
class LibraryFtsCountPathInstrumentedTest {

    /**
     * 设备 SQLite 基线（诊断文档 §2 的待验证项）：`sqlite_version()` 与 FTS4/FTS5 可用性
     * 实测记录。注意 `sqlite_compileoption_used()` 在 Android framework SQLite 上**不可用**
     * （`SQLITE_OMIT_COMPILEOPTION_DIAGNOSTICS`），因此改用**功能探针**（内存库上试建
     * FTS4/FTS5 虚表）回答同一个问题：这台设备的 SQLite 到底支不支持 FTS5。
     */
    @Test
    fun deviceSqliteBaselineIsRecorded() = runBlocking {
        val version = scalarText("SELECT sqlite_version()")
        val compileOptionProbe = runCatching {
            scalarText("SELECT sqlite_compileoption_used('ENABLE_FTS5')")
        }
        val fts4Available = ftsModuleProbe("fts4")
        val fts5Available = ftsModuleProbe("fts5")
        println(
            "S18 device SQLite baseline: sqlite_version=$version fts4=$fts4Available " +
                "fts5=$fts5Available compileoption_used=${compileOptionProbe.fold(
                    onSuccess = { "available=$it" },
                    onFailure = { "unavailable(${it.javaClass.simpleName})" },
                )}",
        )
        assertTrue("sqlite_version() 不可读：$version", VERSION_PATTERN.matches(version))
        // FTS 投影的实际依赖：FTS4 功能探针必须成功（失败即夹具/门全部无意义）。
        assertTrue("设备 SQLite 无法创建 FTS4 虚表", fts4Available)
    }

    /**
     * 等价性主门：改前 SQL 原文（PRE_CHANGE 副本）↔ 生产端口路径（新 SQL，DAO 经 Room
     * 编译）↔ 改后 SQL 副本（CROSS JOIN）三方逐列/逐行对照，覆盖 [MATRIX] 的 13 组。
     */
    @Test
    fun countAndFacetsMatchPreChangeSqlAcrossFilterMatrix() = runBlocking {
        // 夹具非退化信号：FTS **索引**必须真的建起来。注意 `SELECT COUNT(*) FROM
        // library_search_fts` 对外部内容表会回读 content 表（实测），恒等于行数、
        // 证明不了索引——这里用一次真实 MATCH 探测（1 万命中口径）。
        assertEquals(
            "夹具退化：FTS 索引未覆盖全部 $ENTRY_COUNT 条（MATCH 探测）",
            ENTRY_COUNT.toLong(),
            scalarLong(
                "SELECT COUNT(*) FROM library_search_fts WHERE library_search_fts MATCH :matchQuery",
                mapOf("matchQuery" to WIDE_MATCH),
            ),
        )

        MATRIX.forEach { case ->
            val where = "${case.label}(match=${case.matchQuery})"
            val oldCount = readScalarInt(QueryKind.COUNT_SEARCH, SqlCopy.PRE_CHANGE, case)
            val portCount = store.librarySearchCount(
                matchQuery = case.matchQuery,
                subjectId = case.filters.subjectId,
                sectionId = case.filters.sectionId,
                masteryId = case.filters.masteryId,
                createdFromEpochMillis = case.filters.createdFromEpochMillis,
                createdToEpochMillis = case.filters.createdToEpochMillis,
            )
            val newCopyCount = readScalarInt(QueryKind.COUNT_SEARCH, SqlCopy.CROSS_JOIN, case)
            assertEquals("count 不等价（生产路径 vs 改前 SQL）：$where", oldCount, portCount)
            assertEquals("count 不等价（改后副本 vs 改前 SQL）：$where", oldCount, newCopyCount)
            case.expectedCount?.let { expected ->
                assertEquals("count 与夹具口径不符（筛选退化信号）：$where", expected, portCount)
            }

            FacetKind.entries.forEach { facet ->
                val oldRows = readFacetRows(facet, SqlCopy.PRE_CHANGE, case)
                val portRows = store.librarySearchFacets(
                    matchQuery = case.matchQuery,
                    subjectId = case.filters.subjectId,
                    sectionId = case.filters.sectionId,
                    masteryId = case.filters.masteryId,
                    createdFromEpochMillis = case.filters.createdFromEpochMillis,
                    createdToEpochMillis = case.filters.createdToEpochMillis,
                    facet = facet.name,
                ).map { FacetTuple(it.id, it.label, it.count) }
                val newCopyRows = readFacetRows(facet, SqlCopy.CROSS_JOIN, case)
                assertEquals(
                    "$facet 分面逐行不等价（生产路径 vs 改前 SQL）：$where",
                    oldRows,
                    portRows,
                )
                assertEquals(
                    "$facet 分面逐行不等价（改后副本 vs 改前 SQL）：$where",
                    oldRows,
                    newCopyRows,
                )
            }
        }
    }

    /**
     * 1 万命中下的分面分布非退化信号：三条分面在真实分布上必须齐全
     * （科目 2 桶 / 章节 20 桶 / 掌握 4 桶，桶和 = 命中数）。退化的空连接夹具
     * （分类或掌握态为空）会先在这里红，等价性矩阵不至于拿空结果冒充证据。
     */
    @Test
    fun wideMatchFacetDistributionIsNonDegenerate() {
        val wide = MatrixCase("wide-all-empty", WIDE_MATCH, Filters(), expectedCount = ENTRY_COUNT)
        assertEquals(ENTRY_COUNT, readScalarInt(QueryKind.COUNT_SEARCH, SqlCopy.CROSS_JOIN, wide))

        val subjects = readFacetRows(FacetKind.SUBJECT, SqlCopy.PRE_CHANGE, wide)
        assertEquals(setOf("MATH", "PHYSICS"), subjects.map { it.id }.toSet())
        assertEquals(ENTRY_COUNT, subjects.sumOf { it.count })

        val sections = readFacetRows(FacetKind.SECTION, SqlCopy.CROSS_JOIN, wide)
        assertEquals(LibraryCatalogScale.CHAPTER_COUNT, sections.size)
        assertEquals(ENTRY_COUNT, sections.sumOf { it.count })

        val mastery = readFacetRows(FacetKind.MASTERY, SqlCopy.CROSS_JOIN, wide)
        assertEquals(
            setOf("learning", "mastered", "stale", "conflicted"),
            mastery.map { it.id }.toSet(),
        )
        assertEquals(ENTRY_COUNT, mastery.sumOf { it.count })
    }

    /**
     * 驱动结构门（主门之一）：四条改写查询 + 一条带筛选的 count 的 EXPLAIN QUERY PLAN
     * 都必须满足"**MATCH 驱动**"三合取判据（见 [ftsDrivesTheJoin]）：
     * ① FTS 虚表循环是主程序第一个循环（`MATERIALIZE library_catalog` 作为一次性物化
     * 子程序列在最前不判失败）；② 计划里没有 `SCAN catalog` 驱动行；③ catalog 只以
     * `SEARCH catalog USING … INDEX (problem_revision_id=?)` 形态被探测。
     *
     * **口径迁移记录（2026-10-07，协调方裁决）**：计划原话是"`MATERIALIZE library_catalog`
     * 消失或后置"——它写于未知设备 SQLite 版本行为之前。设备实测（framework 3.39.2）该视图
     * **不能展平**，`MATERIALIZE` 是一次性物化子程序、必然列在计划最前（本地 3.50.4 才展平，
     * 所以写计划时看到的形态不同）。门要拦的失败是"**catalog 当驱动 + 逐行回探 FTS**"，
     * 因此判据从"字面物化消失"迁移为"驱动角色判定"；这不是放宽：同一判据对**改前 SQL**
     * 的旧计划（红样例，见下）必须判失败。
     *
     * 可证伪性由**红样例**钉住：[PRE_CHANGE_PLAN_FROM_DIAGNOSIS]（改前 countSearch 的设备
     * 计划原文，诊断文档 §3）必须被同一判据判失败——门能发现退化。红样例用**固定计划文本**
     * 而不是"现跑改前 SQL 的计划"：后者依赖设备的视图展平行为（3.39.2 不展平→红；
     * 能展平的版本上改前 SQL 也会被 FTS 驱动，就不再是红样例），固定文本才与版本无关。
     * 现跑改前 SQL 的计划仍会打印（`S18 FTS pre-change plan[...]`）供复核对照。
     */
    @Test
    fun ftsScanDrivesTheJoinForEveryRewrittenQuery() {
        val greenPlans = readPlans(context, databaseName, CROSS_JOIN_EQP_QUERIES)
        greenPlans.forEach { (name, plan) ->
            println("S18 FTS count/facet plan[$name]:\n${plan.joinToString("\n") { "  $it" }}")
        }
        greenPlans.forEach { (name, plan) ->
            assertTrue(
                "$name：MATCH 未驱动（判据三合取失败）：\n${plan.joinToString("\n")}",
                ftsDrivesTheJoin(plan),
            )
        }

        // 红样例（固定文本，版本无关）：判据必须判失败。
        assertFalse(
            "红样例（诊断文档 §3 的改前计划）被本判据误判为通过（门失效）：\n" +
                PRE_CHANGE_PLAN_FROM_DIAGNOSIS.joinToString("\n"),
            ftsDrivesTheJoin(PRE_CHANGE_PLAN_FROM_DIAGNOSIS),
        )

        // 现跑改前 SQL 的计划：只打印（设备版本相关，见 KDoc），供复核对照。
        readPlans(context, databaseName, PRE_CHANGE_EQP_QUERIES).forEach { (name, plan) ->
            println("S18 FTS pre-change plan[$name]:\n${plan.joinToString("\n") { "  $it" }}")
        }
    }

    /**
     * "MATCH 驱动"判据（三合取）。对改前 SQL 的旧计划必须为 false（红样例），
     * 对新计划必须为 true——这是门能发现退化的证明。
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
        return "problem_revision_id=?" in catalogProbe
    }

    /**
     * 计时 backstop：100 命中 count（生产 `librarySearchCount` 口径，含投影刷新）
     * P95 ≤ 500ms×ciSlowRunner。1 万命中与改前基线只记录（进报告），不设断言。
     */
    @Test
    fun boundedCountStaysWithinBudgetAndWideCountIsRecorded() = runBlocking {
        assertEquals(
            "计时门与夹具脱节：100 命中口径的计数不是 $BOUNDED_MATCH_HITS",
            BOUNDED_MATCH_HITS,
            boundedCount(),
        )

        // Warm up：吸收语句缓存与 bootstrap 后的 WAL checkpoint 尾账（同
        // PerformanceGateTest 的预热口径）。
        repeat(COUNT_WARMUP) { boundedCount() }
        val samples = List(COUNT_SAMPLES) { measureTimeMillis { boundedCount() } }
        val p95 = samples.percentile95()
        println(
            "S18 FTS count benchmark: match=100hits samples=$samples p95=${p95}ms " +
                "budget=${COUNT_P95_BUDGET_MS}ms",
        )
        assertTrue(
            "100 命中 count P95=${p95}ms 超过预算 ${COUNT_P95_BUDGET_MS}ms；samples=$samples",
            p95 < COUNT_P95_BUDGET_MS,
        )

        // 1 万命中：记录项（不设门）。改前基线（同一夹具、同一 SQL 原文、单次）一并记录，
        // 供报告量化驱动修正的收益。
        assertEquals(ENTRY_COUNT, wideCount())
        repeat(WIDE_WARMUP) { wideCount() }
        val wideSamples = List(WIDE_SAMPLES) { measureTimeMillis { wideCount() } }
        println(
            "S18 FTS count wide-hits record (not gated): match=10000hits " +
                "samples=$wideSamples p95=${wideSamples.percentile95()}ms",
        )
        val preChangeMillis = measureTimeMillis {
            readScalarInt(
                QueryKind.COUNT_SEARCH,
                SqlCopy.PRE_CHANGE,
                MatrixCase("wide-all-empty", WIDE_MATCH, Filters()),
            )
        }
        println(
            "S18 FTS count pre-change baseline (same fixture, single shot): " +
                "match=10000hits ${preChangeMillis}ms",
        )
    }

    // ------------------------------------------------------------------
    // 三方读取：生产端口 / 改前 SQL 副本 / 改后 SQL 副本
    // ------------------------------------------------------------------

    private suspend fun boundedCount(): Int = store.librarySearchCount(
        matchQuery = BOUNDED_MATCH,
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

    private fun readScalarInt(kind: QueryKind, copy: SqlCopy, case: MatrixCase): Int =
        withDriverConnection { connection ->
            val sql = sqlFor(kind, copy)
            val statement = connection.prepare(sql)
            try {
                bindNamed(statement, sql, paramsFor(kind, case))
                check(statement.step()) { "$kind 标量查询没有返回行" }
                statement.getInt(0)
            } finally {
                statement.close()
            }
        }

    private fun readFacetRows(kind: FacetKind, copy: SqlCopy, case: MatrixCase): List<FacetTuple> =
        withDriverConnection { connection ->
            val sql = sqlFor(kind.queryKind, copy)
            val statement = connection.prepare(sql)
            try {
                bindNamed(statement, sql, paramsFor(kind.queryKind, case))
                val rows = mutableListOf<FacetTuple>()
                while (statement.step()) {
                    rows += FacetTuple(
                        id = statement.getText(0).orEmpty(),
                        label = statement.getText(1).orEmpty(),
                        count = statement.getInt(2),
                    )
                }
                rows
            } finally {
                statement.close()
            }
        }

    private fun <T> withDriverConnection(block: (SQLiteConnection) -> T): T {
        val connection = AndroidSQLiteDriver().open(databasePath().absolutePath)
        try {
            return block(connection)
        } finally {
            connection.close()
        }
    }

    private suspend fun scalarText(sql: String): String {
        val holder = arrayOfNulls<String>(1)
        store.database.withRawConnection(isReadOnly = true) { connection ->
            connection.usePrepared(sql) { statement ->
                if (statement.step()) {
                    holder[0] = statement.getText(0)
                }
            }
        }
        return holder[0].orEmpty()
    }

    private suspend fun scalarLong(sql: String, params: Map<String, Any?> = emptyMap()): Long {
        var value = -1L
        store.database.withRawConnection(isReadOnly = true) { connection ->
            connection.usePrepared(sql) { statement ->
                bindNamed(statement, sql, params)
                if (statement.step()) {
                    value = statement.getLong(0)
                }
            }
        }
        return value
    }

    /** 功能探针：内存库上试建 FTS 虚表——比编译期常量更直接地回答"这台设备支不支持"。 */
    private fun ftsModuleProbe(module: String): Boolean {
        val ddl = when (module) {
            "fts5" -> "CREATE VIRTUAL TABLE fts_probe USING fts5(content)"
            else -> "CREATE VIRTUAL TABLE fts_probe USING fts4(content)"
        }
        return SQLiteDatabase.openOrCreateDatabase(":memory:", null).use { database ->
            runCatching { database.execSQL(ddl) }.isSuccess
        }
    }

    private fun databasePath(): File = context.getDatabasePath(databaseName)

    /** 平台连接读 EXPLAIN QUERY PLAN（room3 prepared-statement 路径拒绝 EXPLAIN）。 */
    private fun readPlans(
        context: Context,
        databaseName: String,
        queries: Map<String, Pair<String, Array<String?>>>,
    ): Map<String, List<String>> = SQLiteDatabase.openDatabase(
        context.getDatabasePath(databaseName).absolutePath,
        null,
        SQLiteDatabase.OPEN_READONLY,
    ).use { database ->
        queries.mapValues { (_, query) ->
            database.rawQuery("EXPLAIN QUERY PLAN ${query.first}", query.second).use { cursor ->
                val detail = cursor.getColumnIndexOrThrow("detail")
                buildList {
                    while (cursor.moveToNext()) add(cursor.getString(detail))
                }
            }
        }
    }

    private fun List<Long>.percentile95(): Long {
        require(isNotEmpty())
        val sorted = sorted()
        return sorted[((sorted.size - 1) * 95) / 100]
    }

    private data class Filters(
        val subjectId: String? = null,
        val sectionId: String? = null,
        val masteryId: String? = null,
        val createdFromEpochMillis: Long? = null,
        val createdToEpochMillis: Long? = null,
    )

    private data class MatrixCase(
        val label: String,
        val matchQuery: String,
        val filters: Filters,
        val expectedCount: Int? = null,
    )

    private data class FacetTuple(val id: String, val label: String, val count: Int)

    private enum class FacetKind(val queryKind: QueryKind) {
        SUBJECT(QueryKind.SUBJECT_FACETS),
        SECTION(QueryKind.SECTION_FACETS),
        MASTERY(QueryKind.MASTERY_FACETS),
    }

    private enum class QueryKind {
        COUNT_SEARCH,
        SUBJECT_FACETS,
        SECTION_FACETS,
        MASTERY_FACETS,
    }

    private enum class SqlCopy { PRE_CHANGE, CROSS_JOIN }

    companion object {
        private const val ENTRY_COUNT = 10_000
        private const val BOUNDED_MATCH_HITS = 100
        private const val COUNT_WARMUP = 10
        private const val COUNT_SAMPLES = 30
        private const val WIDE_WARMUP = 3
        private const val WIDE_SAMPLES = 5

        private val VERSION_PATTERN = Regex("""\d+\.\d+(\.\d+)?""")

        /** SQL 命名参数：同名参数在 SQLite 里只占一个绑定位。 */
        private val NAMED_PARAMETER = Regex(""":([A-Za-z_][A-Za-z0-9_]*)""")

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

        private val CI_MULTIPLIER: Long = run {
            val fromArgs = androidx.test.platform.app.InstrumentationRegistry
                .getArguments()
                .getString("ciSlowRunner")
            if (fromArgs != null || System.getenv("CI") != null) 4L else 1L
        }

        /** 与 `PerformanceGateTest.SEARCH_P95_TARGET_MS` 同值同口径。 */
        private val COUNT_P95_BUDGET_MS = 500L * CI_MULTIPLIER

        /**
         * 100 命中：夹具里每 100 行的题面含"独特检索词 N"，分词后前四字各自成词
         * （末字与数字粘连为 `词N`，故取"独特检索"而不取整词——实测）。
         */
        private val BOUNDED_MATCH = CjkTextTokenizer.matchExpression("独特检索")

        /** 1 万命中：每条目录行的标题都是"分页题目 N"，分词后 分/页/题/目 各自成词。 */
        private val WIDE_MATCH = CjkTextTokenizer.matchExpression("分页题目")

        private val ZERO_MATCH = CjkTextTokenizer.matchExpression("不存在词")

        /** EXPLAIN 绑定用的"不启用该筛选"占位值（值不影响计划形状，见 [eqpEntry]）。 */
        private const val NO_FILTER_PLACEHOLDER = "\u0001none"

        /**
         * 抽样矩阵：全空 / 五个筛选面各自单筛 / 组合，覆盖 100 命中与 1 万命中两档 +
         * 零命中（空结果也必须等价）。expectedCount 是夹具口径的精确值（非退化信号）。
         */
        private val MATRIX: List<MatrixCase> = listOf(
            MatrixCase("bounded-all-empty", BOUNDED_MATCH, Filters(), BOUNDED_MATCH_HITS),
            MatrixCase(
                "bounded-subject",
                BOUNDED_MATCH,
                Filters(subjectId = "MATH"),
                BOUNDED_MATCH_HITS,
            ),
            MatrixCase("bounded-subject-miss", BOUNDED_MATCH, Filters(subjectId = "PHYSICS"), 0),
            MatrixCase(
                "bounded-section",
                BOUNDED_MATCH,
                Filters(sectionId = "chapter-0"),
                BOUNDED_MATCH_HITS,
            ),
            MatrixCase("bounded-mastery", BOUNDED_MATCH, Filters(masteryId = "learning"), 25),
            MatrixCase(
                "bounded-created-from",
                BOUNDED_MATCH,
                Filters(createdFromEpochMillis = 5_000L),
                50,
            ),
            MatrixCase(
                "bounded-created-to",
                BOUNDED_MATCH,
                Filters(createdToEpochMillis = 5_000L),
                50,
            ),
            MatrixCase(
                "bounded-combined",
                BOUNDED_MATCH,
                Filters(
                    subjectId = "MATH",
                    sectionId = "chapter-0",
                    masteryId = "mastered",
                    createdFromEpochMillis = 1L,
                    createdToEpochMillis = 10_000L,
                ),
                25,
            ),
            MatrixCase("wide-all-empty", WIDE_MATCH, Filters(), ENTRY_COUNT),
            MatrixCase("wide-section", WIDE_MATCH, Filters(sectionId = "chapter-3"), 500),
            MatrixCase("wide-mastery", WIDE_MATCH, Filters(masteryId = "mastered"), 2_500),
            MatrixCase(
                "wide-combined",
                WIDE_MATCH,
                Filters(
                    subjectId = "PHYSICS",
                    sectionId = "chapter-7",
                    masteryId = "learning",
                    createdFromEpochMillis = 2_000L,
                    createdToEpochMillis = 8_000L,
                ),
            ),
            MatrixCase("zero-all-empty", ZERO_MATCH, Filters(), 0),
        )

        /**
         * 绿样例（改后 SQL 副本）：四条改写查询 + 一条带筛选的 count。
         * `by lazy`：它引用文件末尾的 SQL 副本常量；companion 属性按声明顺序初始化，
         * 直接求值会读到未初始化的 null。
         */
        private val CROSS_JOIN_EQP_QUERIES: Map<String, Pair<String, Array<String?>>> by lazy {
            buildMap {
                val bounded = MatrixCase("eqp", BOUNDED_MATCH, Filters())
                val filtered = MatrixCase(
                    "eqp-filtered",
                    BOUNDED_MATCH,
                    Filters(
                        sectionId = "chapter-0",
                        masteryId = "mastered",
                        createdFromEpochMillis = 1L,
                    ),
                )
                put(
                    "count-search",
                    eqpEntry(QueryKind.COUNT_SEARCH, bounded, SqlCopy.CROSS_JOIN),
                )
                put(
                    "count-search-filtered",
                    eqpEntry(QueryKind.COUNT_SEARCH, filtered, SqlCopy.CROSS_JOIN),
                )
                put(
                    "subject-facets",
                    eqpEntry(QueryKind.SUBJECT_FACETS, bounded, SqlCopy.CROSS_JOIN),
                )
                put(
                    "section-facets",
                    eqpEntry(QueryKind.SECTION_FACETS, bounded, SqlCopy.CROSS_JOIN),
                )
                put(
                    "mastery-facets",
                    eqpEntry(QueryKind.MASTERY_FACETS, bounded, SqlCopy.CROSS_JOIN),
                )
            }
        }

        /** 红样例（改前 SQL 原文）：判据必须判失败，证明门能发现退化。 */
        private val PRE_CHANGE_EQP_QUERIES: Map<String, Pair<String, Array<String?>>> by lazy {
            buildMap {
                val bounded = MatrixCase("eqp", BOUNDED_MATCH, Filters())
                put(
                    "pre-change-count-search",
                    eqpEntry(QueryKind.COUNT_SEARCH, bounded, SqlCopy.PRE_CHANGE),
                )
                put(
                    "pre-change-subject-facets",
                    eqpEntry(QueryKind.SUBJECT_FACETS, bounded, SqlCopy.PRE_CHANGE),
                )
                put(
                    "pre-change-section-facets",
                    eqpEntry(QueryKind.SECTION_FACETS, bounded, SqlCopy.PRE_CHANGE),
                )
                put(
                    "pre-change-mastery-facets",
                    eqpEntry(QueryKind.MASTERY_FACETS, bounded, SqlCopy.PRE_CHANGE),
                )
            }
        }

        private lateinit var context: Context
        private lateinit var store: RoomStudyDatabase
        private lateinit var databaseName: String

        private fun eqpEntry(
            kind: QueryKind,
            case: MatrixCase,
            copy: SqlCopy,
        ): Pair<String, Array<String?>> {
            val sql = sqlFor(kind, copy)
            val params = paramsFor(kind, case)
            return sql to namedParameterOrder(sql).map { name ->
                require(params.containsKey(name)) { "SQL 参数 :$name 没有 EXPLAIN 绑定值" }
                // 平台 rawQuery 的 String[] 绑定拒绝 null（bindAllArgsAsStrings 实测抛
                // IllegalArgumentException），而计划在 prepare 期已定、与绑定值无关
                // （同 LibraryCatalogScalePerformanceInstrumentedTest 实测），null 用哨兵串。
                params[name]?.toString() ?: NO_FILTER_PLACEHOLDER
            }.toTypedArray()
        }

        private fun sqlFor(kind: QueryKind, copy: SqlCopy): String = when (copy) {
            SqlCopy.PRE_CHANGE -> when (kind) {
                QueryKind.COUNT_SEARCH -> PRE_CHANGE_COUNT_SEARCH_SQL
                QueryKind.SUBJECT_FACETS -> PRE_CHANGE_SUBJECT_FACETS_SQL
                QueryKind.SECTION_FACETS -> PRE_CHANGE_SECTION_FACETS_SQL
                QueryKind.MASTERY_FACETS -> PRE_CHANGE_MASTERY_FACETS_SQL
            }
            SqlCopy.CROSS_JOIN -> when (kind) {
                QueryKind.COUNT_SEARCH -> CROSS_JOIN_COUNT_SEARCH_SQL
                QueryKind.SUBJECT_FACETS -> CROSS_JOIN_SUBJECT_FACETS_SQL
                QueryKind.SECTION_FACETS -> CROSS_JOIN_SECTION_FACETS_SQL
                QueryKind.MASTERY_FACETS -> CROSS_JOIN_MASTERY_FACETS_SQL
            }
        }

        private fun paramsFor(kind: QueryKind, case: MatrixCase): Map<String, Any?> = when (kind) {
            QueryKind.COUNT_SEARCH -> mapOf(
                "matchQuery" to case.matchQuery,
                "subjectId" to case.filters.subjectId,
                "sectionId" to case.filters.sectionId,
                "masteryId" to case.filters.masteryId,
                "createdFromEpochMillis" to case.filters.createdFromEpochMillis,
                "createdToEpochMillis" to case.filters.createdToEpochMillis,
            )
            QueryKind.SUBJECT_FACETS -> mapOf(
                "matchQuery" to case.matchQuery,
                "sectionId" to case.filters.sectionId,
                "masteryId" to case.filters.masteryId,
                "createdFromEpochMillis" to case.filters.createdFromEpochMillis,
                "createdToEpochMillis" to case.filters.createdToEpochMillis,
            )
            QueryKind.SECTION_FACETS -> mapOf(
                "matchQuery" to case.matchQuery,
                "subjectId" to case.filters.subjectId,
                "masteryId" to case.filters.masteryId,
                "createdFromEpochMillis" to case.filters.createdFromEpochMillis,
                "createdToEpochMillis" to case.filters.createdToEpochMillis,
            )
            QueryKind.MASTERY_FACETS -> mapOf(
                "matchQuery" to case.matchQuery,
                "subjectId" to case.filters.subjectId,
                "sectionId" to case.filters.sectionId,
                "createdFromEpochMillis" to case.filters.createdFromEpochMillis,
                "createdToEpochMillis" to case.filters.createdToEpochMillis,
            )
        }

        private fun namedParameterOrder(sql: String): List<String> =
            NAMED_PARAMETER.findAll(sql).map { it.groupValues[1] }.distinct().toList()

        /** 按命名参数首次出现顺序绑定；缺值在这里炸，而不是让参数静默变 NULL。 */
        private fun bindNamed(statement: SQLiteStatement, sql: String, params: Map<String, Any?>) {
            namedParameterOrder(sql).forEachIndexed { index, name ->
                require(params.containsKey(name)) {
                    "SQL 参数 :$name 没有绑定值（提供：${params.keys}）"
                }
                when (val value = params[name]) {
                    null -> statement.bindNull(index + 1)
                    is Long -> statement.bindLong(index + 1, value)
                    is Int -> statement.bindLong(index + 1, value.toLong())
                    else -> statement.bindText(index + 1, value.toString())
                }
            }
        }

        /**
         * 类级共享夹具：1 万行目录 + 分类 + 掌握态（`seedLibraryCatalogScale`）与一次
         * FTS bootstrap。13 组矩阵 × 4 条查询都是只读，夹具建一次。
         */
        @JvmStatic
        @BeforeClass
        fun buildSharedFixture() {
            context = ApplicationProvider.getApplicationContext()
            databaseName = "s18-fts-count-path-${System.nanoTime()}.db"
            context.deleteDatabase(databaseName)
            runBlocking {
                store = StudyDatabaseFactory.open(context, databaseName) as RoomStudyDatabase
                val seedMillis = measureTimeMillis {
                    store.database.seedLibraryCatalogScale(ENTRY_COUNT)
                }
                val bootstrapMillis = measureTimeMillis { store.refreshLibrarySearchProjection() }
                // Room 惰性打开：借一次读把 schema/连接立起来，平台连接随后读同一文件。
                store.database.withRawConnection(isReadOnly = true) { connection ->
                    connection.usePrepared("SELECT 1") { statement -> statement.step() }
                }
                println(
                    "S18 FTS count-path fixture built: $ENTRY_COUNT entries " +
                        "seed=${seedMillis}ms bootstrap=${bootstrapMillis}ms",
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

        // --------------------------------------------------------------
        // SQL 副本（逐字；与 DAO 的对应关系由 JVM 守卫
        // LibraryFtsCountPathQueryCopyContractTest 钉住）
        // --------------------------------------------------------------

        /**
         * **红样例**：改前 `countSearch` 的设备 EXPLAIN 原文
         * （`docs/research/2026-10-05-s18-count-path-prefinding.md` §3，API 34 / SQLite 3.39.2）。
         * 病征：`SCAN catalog` 当驱动 → 逐目录行 `SEARCH content` → 最内层
         * `SCAN library_search_fts VIRTUAL TABLE INDEX 11:` 回探（MATCH 不驱动）。
         * 判据 [ftsDrivesTheJoin] 对它必须判失败。
         */
        private val PRE_CHANGE_PLAN_FROM_DIAGNOSIS: List<String> = listOf(
            "MATERIALIZE library_catalog",
            "SEARCH entry USING INDEX index_error_book_entry_status_updated_at_epoch_millis (status=?)",
            "SEARCH problem USING INDEX sqlite_autoindex_problem_1 (problem_id=?)",
            "SEARCH unit USING COVERING INDEX sqlite_autoindex_practice_unit_1 (practice_unit_id=?)",
            "SEARCH revision USING INDEX index_problem_revision_problem_id_revision_id (problem_id=? AND revision_id=?)",
            "SEARCH memory USING INDEX index_learner_problem_memory_state_practice_unit_id (practice_unit_id=?) LEFT-JOIN",
            "CORRELATED SCALAR SUBQUERY 4",
            "SEARCH binding USING COVERING INDEX " +
                "index_practice_unit_knowledge_binding_practice_unit_id_knowledge_node_id_basis_revision_id_taxonomy_version " +
                "(practice_unit_id=?)",
            "SEARCH mastery USING INDEX sqlite_autoindex_learner_knowledge_mastery_state_1 " +
                "(projection_name=? AND learner_id=? AND knowledge_node_id=?)",
            "CORRELATED SCALAR SUBQUERY 4",
            "SEARCH binding USING COVERING INDEX " +
                "index_practice_unit_knowledge_binding_practice_unit_id_knowledge_node_id_basis_revision_id_taxonomy_version " +
                "(practice_unit_id=?)",
            "SEARCH mastery USING INDEX sqlite_autoindex_learner_knowledge_mastery_state_1 " +
                "(projection_name=? AND learner_id=? AND knowledge_node_id=?)",
            "CORRELATED SCALAR SUBQUERY 5",
            "SEARCH classification USING INDEX " +
                "index_problem_classification_binding_problem_id_basis_revision_id_dimension_label_id " +
                "(problem_id=? AND basis_revision_id=? AND dimension=?)",
            "CORRELATED SCALAR SUBQUERY 6",
            "SEARCH classification USING INDEX " +
                "index_problem_classification_binding_problem_id_basis_revision_id_dimension_label_id " +
                "(problem_id=? AND basis_revision_id=? AND dimension=?)",
            "SCAN catalog",
            "CORRELATED SCALAR SUBQUERY 1",
            "SEARCH classification USING COVERING INDEX " +
                "index_problem_classification_binding_problem_id_basis_revision_id_dimension_label_id " +
                "(problem_id=? AND basis_revision_id=? AND dimension=? AND label_id=?)",
            "SEARCH content USING COVERING INDEX index_library_search_content_problem_revision_id (problem_revision_id=?)",
            "SCAN library_search_fts VIRTUAL TABLE INDEX 11:",
        )

        /** 改前（git 59fd8a32）`countSearch` 的逐字原文。 */
        private val PRE_CHANGE_COUNT_SEARCH_SQL: String = """
            SELECT COUNT(*) FROM library_search_fts JOIN library_search_content AS content ON content.content_row_id = library_search_fts.docid JOIN library_catalog AS catalog ON catalog.problem_revision_id = content.problem_revision_id WHERE library_search_fts MATCH :matchQuery AND (:subjectId IS NULL OR catalog.subject = :subjectId) AND ( :sectionId IS NULL OR EXISTS ( SELECT 1 FROM problem_classification_binding AS classification WHERE classification.problem_id = catalog.problem_id AND classification.basis_revision_id = catalog.problem_revision_id AND classification.dimension = 'CHAPTER' AND classification.label_id = :sectionId ) ) AND (:masteryId IS NULL OR catalog.mastery_id = :masteryId) AND ( :createdFromEpochMillis IS NULL OR catalog.created_at_epoch_millis >= :createdFromEpochMillis ) AND ( :createdToEpochMillis IS NULL OR catalog.created_at_epoch_millis <= :createdToEpochMillis )
        """.trimIndent()

        /** 改前 `searchSubjectFacets` 的逐字原文。 */
        private val PRE_CHANGE_SUBJECT_FACETS_SQL: String = """
            SELECT catalog.subject AS id, catalog.subject AS label, COUNT(*) AS count FROM library_catalog AS catalog JOIN library_search_content AS content ON content.problem_revision_id = catalog.problem_revision_id JOIN library_search_fts ON library_search_fts.docid = content.content_row_id WHERE library_search_fts MATCH :matchQuery AND (:sectionId IS NULL OR EXISTS (SELECT 1 FROM problem_classification_binding AS c WHERE c.problem_id = catalog.problem_id AND c.basis_revision_id = catalog.problem_revision_id AND c.dimension = 'CHAPTER' AND c.label_id = :sectionId)) AND (:masteryId IS NULL OR catalog.mastery_id = :masteryId) AND (:createdFromEpochMillis IS NULL OR catalog.created_at_epoch_millis >= :createdFromEpochMillis) AND (:createdToEpochMillis IS NULL OR catalog.created_at_epoch_millis <= :createdToEpochMillis) GROUP BY catalog.subject ORDER BY COUNT(*) DESC, id ASC
        """.trimIndent()

        /** 改前 `searchSectionFacets` 的逐字原文。 */
        private val PRE_CHANGE_SECTION_FACETS_SQL: String = """
            SELECT classification.label_id AS id, classification.display_name AS label, COUNT(*) AS count FROM library_catalog AS catalog JOIN problem_classification_binding AS classification ON classification.problem_id = catalog.problem_id AND classification.basis_revision_id = catalog.problem_revision_id AND classification.dimension = 'CHAPTER' JOIN library_search_content AS content ON content.problem_revision_id = catalog.problem_revision_id JOIN library_search_fts ON library_search_fts.docid = content.content_row_id WHERE library_search_fts MATCH :matchQuery AND (:subjectId IS NULL OR catalog.subject = :subjectId) AND (:masteryId IS NULL OR catalog.mastery_id = :masteryId) AND (:createdFromEpochMillis IS NULL OR catalog.created_at_epoch_millis >= :createdFromEpochMillis) AND (:createdToEpochMillis IS NULL OR catalog.created_at_epoch_millis <= :createdToEpochMillis) GROUP BY classification.label_id, classification.display_name ORDER BY COUNT(*) DESC, id ASC
        """.trimIndent()

        /** 改前 `searchMasteryFacets` 的逐字原文。 */
        private val PRE_CHANGE_MASTERY_FACETS_SQL: String = """
            SELECT catalog.mastery_id AS id, catalog.mastery_id AS label, COUNT(*) AS count FROM library_catalog AS catalog JOIN library_search_content AS content ON content.problem_revision_id = catalog.problem_revision_id JOIN library_search_fts ON library_search_fts.docid = content.content_row_id WHERE library_search_fts MATCH :matchQuery AND (:subjectId IS NULL OR catalog.subject = :subjectId) AND (:sectionId IS NULL OR EXISTS (SELECT 1 FROM problem_classification_binding AS c WHERE c.problem_id = catalog.problem_id AND c.basis_revision_id = catalog.problem_revision_id AND c.dimension = 'CHAPTER' AND c.label_id = :sectionId)) AND (:createdFromEpochMillis IS NULL OR catalog.created_at_epoch_millis >= :createdFromEpochMillis) AND (:createdToEpochMillis IS NULL OR catalog.created_at_epoch_millis <= :createdToEpochMillis) GROUP BY catalog.mastery_id ORDER BY COUNT(*) DESC, id ASC
        """.trimIndent()

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
    }
}
