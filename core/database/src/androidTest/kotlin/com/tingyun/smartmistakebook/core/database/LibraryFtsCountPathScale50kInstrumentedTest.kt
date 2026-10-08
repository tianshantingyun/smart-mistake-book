package com.tingyun.smartmistakebook.core.database

import android.content.Context
import androidx.room3.withWriteTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.system.measureTimeMillis

/**
 * **K1 批 1 · 50k FTS 规模补测（count 路径）**：S18 尾的 1 万行夹具（[LibraryFtsCountPathInstrumentedTest]）
 * 上"`MATERIALIZE` 一次成本随目录行数线性"只有推断，没有 5 万行实测。本类把同一夹具/查询形态
 * 扩到 **5 万行**并只做一件事：**记录**。取舍写清楚：
 *
 * - **为什么独立成类**：10k 的既有门类带 13 组等价性矩阵（含改前 SQL 逐行回探，单次宽命中 15s
 *   量级）+ EQP 结构门；同一矩阵在 50k 上会成倍膨胀，且会让既有 10k 门（S18 锚点）的
 *   "100 命中 P95"计时与夹具形态脱钩。既有 10k 门**一字不改**，50k 在这里单独建库、单独记录。
 * - **门口径**：100 命中仍是门（P95 ≤ 500ms×ciSlowRunner，与 `PerformanceGateTest` 同值同口径）；
 *   1 万命中与 **5 万命中**只记录（打印 P95），**不设新门**——"5 万命中 count 该多快"没有
 *   需求侧预算，拿一次模拟器读数当墙会把抖动变成假红。
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
