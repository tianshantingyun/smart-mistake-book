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
 * **K1 批 1 · 50k FTS 规模补测（排序路径）**：S18 尾批 2 的 1 万行排序门
 * （[LibraryFtsRankingPathInstrumentedTest]）只测到 10k；"集合级命中集 join 的宽命中成本
 * 随目录行数增长"在 5 万行下没有实测。本类在 **5 万行**夹具上记录窄命中（12 命中）、
 * 100 / 1k 命中（沿用既有 P95 门口径）与 1 万 / 宽命中（**只记录，不设新门**）。
 *
 * 取舍（与 count 的 50k 类同）：独立成类，不把既有 10k 等价性/结构门参数化——那会让
 * S18 锚点门的运行时与夹具形态双重漂移；本类只做"计时 + 非退化计数"记录。
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
