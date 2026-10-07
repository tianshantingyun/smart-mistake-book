package com.tingyun.smartmistakebook.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.SQLiteStatement
import androidx.room3.withWriteTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.database.entity.ProblemClassificationBindingEntity
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
 * **S18 尾批 2 门**（`docs/research/2026-10-06-s18-fts-search-plan.md` §3 批 2）：
 * `searchPage`（用户可见搜索页/导出分页的用户路径，`RoomLibrarySearchStore.searchPage`）
 * 的宽命中排序在 1 万行库上从 **逐命中行 × 9–12 次列级 FTS 回探**（相关子查询）改为
 * **集合级命中集 join**（每个标志一次 `MATERIALIZE hit_*` + 按 docid 的自动覆盖索引探测）。
 *
 * 本类在真实夹具上钉三件事（数字与 EXPLAIN 原文落在
 * `docs/research/2026-10-06-s18-fts-ranking-report.md`）：
 *
 * 1. **同序（主门）**：[PRE_CHANGE_SEARCH_PAGE_SQL] 是改前 SQL 的逐字冻结副本
 *    （运行时从改前 builder 取出的原文，见报告 §EXPLAIN），与生产 builder 的新 SQL
 *    在**同一真库**上逐列（`SELECT catalog.*, snippet(...)` 全列）对照：100 / 1k / 10k
 *    命中 × 两种排序 × 两个窗口 × 筛选档全部逐行相等；内存小夹具另钉**并列键**
 *    （同 rank 同 updated_at → entry_id ASC）与**列权重**（stem 4 / solution 3 /
 *    knowledge 2 / subject 2 / options 1 / chapter 1）的真实序。
 * 2. **驱动结构（主门）**：EXPLAIN QUERY PLAN 上"排序表达式不得出现逐行 FTS 相关子查询"
 *    的判据——[rowLevelFtsProbesOf]：紧跟在 `CORRELATED SCALAR SUBQUERY` 头之后的
 *    FTS 虚表访问行即逐行探测。判据对改前计划的**冻结红样例**
 *    （[PRE_CHANGE_RANKING_PLAN]）必须判失败、对现场 live SQL 计划必须判空；另有正向
 *    断言"12 个命中集必须被 MATERIALIZE"（退化回相关子查询时红样例先红）。
 * 3. **计时**：100 命中（用户真实规模）与 1k 命中的 `searchPage` P95 ≤ 500ms×ciSlowRunner
 *    （与 [PerformanceGateTest.SEARCH_P95_TARGET_MS] 同值同口径）；10k 命中记录样本与
 *    P95，另设**宽松 backstop** P95 ≤ 2000ms×ciSlowRunner（实测 144ms，≈14× 余量；
 *    只拦"退化到秒级"，不是性能预算）；并发档（10 并发 × 1k 命中）只记录不设墙钟门
 *    （含 2 核模拟器与 refreshProjection 串行化效应，量级参考；同 S17 前置文档口径）。
 *
 * 为什么预算门槛放在 100/1k 两档：改前实测 100 命中 P95=146ms（达标）、1k=784ms（超预算）、
 * 10k=19.2s（病理）；批 2 的章程是"千级即超预算才重写"，这两档就是重写的触发条件与
 * 达标判据。10k 不设性能预算门（用户库的百级命中是真实规模），只留 2000ms×CI 的宽松
 * backstop 拦秒级退化，避免把一次模拟器抖动变成 CI 红灯。
 */
@RunWith(AndroidJUnit4::class)
class LibraryFtsRankingPathInstrumentedTest {

    /**
     * 结构门：live SQL 的排序表达式不得出现逐行 FTS 相关子查询；改前计划的冻结红样例
     * 必须被同一判据判失败（门能发现退化），且 12 个命中集必须被物化。
     */
    @Test
    fun rankingPlanUsesMaterializedHitSetsNotRowProbes() {
        val liveSql = liveSearchPageSql(TOKENS_WIDE)
        val plan = readPlans(
            context,
            databaseName,
            mapOf("ranking-live" to (liveSql to sentinelArgs(liveSql))),
        ).getValue("ranking-live")
        println("S18 ranking plan[live]:\n${plan.joinToString("\n") { "  $it" }}")

        val rowLevelProbes = rowLevelFtsProbesOf(plan)
        assertTrue(
            "排序路径退化为逐行 FTS 相关子查询（live 计划）：\n${rowLevelProbes.joinToString("\n")}\n" +
                "完整计划：\n${plan.joinToString("\n")}",
            rowLevelProbes.isEmpty(),
        )
        // 红样例（改前计划的冻结原文，版本无关）：同一判据必须判失败。
        val redSampleProbes = rowLevelFtsProbesOf(PRE_CHANGE_RANKING_PLAN)
        assertFalse(
            "红样例（改前计划）被本判据误判为通过（门失效）：\n" +
                PRE_CHANGE_RANKING_PLAN.joinToString("\n"),
            redSampleProbes.isEmpty(),
        )
        // 正向形态：12 个命中集各 MATERIALIZE 一次（退化回相关子查询时这里先红）。
        val materializedSets = plan.filter { it.startsWith("MATERIALIZE hit_") }
        assertEquals(
            "命中集未按 9 列 + 3 额外 token 物化：\n${plan.joinToString("\n")}",
            HIT_SET_COUNT,
            materializedSets.size,
        )
        assertTrue(
            "命中集物化后没有按 docid 的探测行（AUTOMATIC 索引）：\n${plan.joinToString("\n")}",
            plan.any { "SEARCH hit_stem_text USING" in it && "hit_docid=?" in it },
        )
        // 驱动角色：主循环仍从 FTS MATCH 驱动（SCAN library_search_fts VIRTUAL TABLE），
        // catalog 只被自动覆盖索引探测，不得出现 `SCAN catalog` 驱动（那是 count 路径的病根）。
        assertTrue(
            "排序路径的 FTS 主扫描消失（MATCH 不再是驱动）：\n${plan.joinToString("\n")}",
            plan.any { FTS_MAIN_SCAN.containsMatchIn(it) },
        )
        assertFalse(
            "排序路径出现 SCAN catalog 驱动（目录当驱动 + 逐行回探的病根形态）：\n" +
                plan.joinToString("\n"),
            plan.any { CATALOG_SCAN.containsMatchIn(it) },
        )
    }

    /**
     * 计时档位：100（用户真实规模，门）与 1k（重写触发档，门）的 P95 ≤ 500ms×ciSlowRunner；
     * 10k 记录样本与 P95，另设**宽松 backstop** P95 ≤ 2000ms×ciSlowRunner（实测 144ms，
     * ≈14× 余量；只拦秒级退化，改前 19208ms 会红）。非空信号防夹具脱节。
     */
    @Test
    fun userScaleAndThousandHitTiersStayWithinBudget() = runBlocking {
        assertEquals(100, countHits(TOKENS_HUNDRED, "100 命中口径"))
        assertEquals(1_000, countHits(TOKENS_ONE_K, "1k 命中口径"))

        // 100 命中：预热 + 20 样本（口径同 PerformanceGateTest）。
        repeat(WARMUP_COUNT) { page(TOKENS_HUNDRED) }
        val hundred = List(SAMPLE_COUNT) { measureTimeMillis { page(TOKENS_HUNDRED) } }
        println(
            "S18 ranking benchmark tier=100 samples=$hundred " +
                "p95=${hundred.percentile95()}ms budget=${PAGE_P95_BUDGET_MS}ms",
        )
        assertTrue(
            "100 命中 searchPage P95=${hundred.percentile95()}ms 超过预算 ${PAGE_P95_BUDGET_MS}ms；" +
                "samples=$hundred",
            hundred.percentile95() < PAGE_P95_BUDGET_MS,
        )

        // 1k 命中：批 2 的触发档，重写后必须回到预算内。
        repeat(WARMUP_COUNT) { page(TOKENS_ONE_K) }
        val oneK = List(SAMPLE_COUNT) { measureTimeMillis { page(TOKENS_ONE_K) } }
        println(
            "S18 ranking benchmark tier=1k samples=$oneK " +
                "p95=${oneK.percentile95()}ms budget=${PAGE_P95_BUDGET_MS}ms",
        )
        assertTrue(
            "1k 命中 searchPage P95=${oneK.percentile95()}ms 超过预算 ${PAGE_P95_BUDGET_MS}ms；" +
                "samples=$oneK",
            oneK.percentile95() < PAGE_P95_BUDGET_MS,
        )

        // 10k 命中：记录 + 宽松 backstop（实测 P95=144ms；2000ms×CI 只拦"退化到秒级"，
        // 改前 19208ms 量级会红——收口修复轮补，避免未来退化到秒级仍绿）。
        assertEquals(10_000, countHits(TOKENS_WIDE, "10k 命中口径"))
        repeat(1) { page(TOKENS_WIDE) }
        val tenK = List(TEN_K_SAMPLE_COUNT) { measureTimeMillis { page(TOKENS_WIDE) } }
        val tenKP95 = tenK.percentile95()
        println(
            "S18 ranking benchmark tier=10k samples=$tenK p95=${tenKP95}ms " +
                "backstop=${TEN_K_P95_BACKSTOP_MS}ms",
        )
        assertTrue(
            "10k 命中 searchPage P95=${tenKP95}ms 超过宽松 backstop ${TEN_K_P95_BACKSTOP_MS}ms" +
                "（排序路径退化到秒级）；samples=$tenK",
            tenKP95 < TEN_K_P95_BACKSTOP_MS,
        )
    }

    /**
     * 并发档复测（10 并发 × 1k 命中，生产端口路径）：只记录均值/分布（不设墙钟门，
     * 2 核模拟器 + refreshProjection 串行化，量级参考；改前均值 10574ms）。
     */
    @Test
    fun concurrentThousandHitPagesAreRecorded() = runBlocking {
        val latencies = (1..CONCURRENT_TASKS).map {
            async {
                var size = -1
                val latency = measureTimeMillis { size = page(TOKENS_ONE_K).size }
                check(size == FIRST_SCREEN_LIMIT) { "并发页返回 $size 行" }
                latency
            }
        }.awaitAll()
        println(
            "S18 ranking concurrent (10x, 1k tier, recorded not gated): " +
                "latencies=$latencies mean=${latencies.average().toLong()}ms",
        )
        assertTrue(
            "并发页路径与夹具脱节：latencies=$latencies",
            latencies.all { it > 0 },
        )
    }

    /**
     * 同序主门：改前 SQL 冻结副本 vs 生产 live SQL，在 100 / 1k / 10k 命中、两种排序、
     * 两个窗口（含 offset 分页）与筛选档上**逐列**（全 `catalog.*` + snippet）对照。
     * 两条 SQL 都跑在**生产连接**上（`withRawConnection`，同库同驱动），另用生产端口
     * 路径（Room 编译/绑定/游标转换）复核 entryId 序。
     */
    @Test
    fun pageOrderMatchesPreChangeSqlAcrossTiersAndSorts() = runBlocking {
        val cases = buildList {
            // 窗口按档位取：100 档只有 100 行，offset 不能超过 80（否则空页，非空信号先红）。
            listOf(
                Triple(TOKENS_HUNDRED, "100", 20),
                Triple(TOKENS_ONE_K, "1k", 100),
            ).forEach { (tokens, label, secondOffset) ->
                listOf("RECENTLY_UPDATED", "RECENTLY_CREATED").forEach { sort ->
                    listOf(0, secondOffset).forEach { offset ->
                        add(EquivalenceCase("$label/$sort/offset$offset", tokens, sort, offset, Filters()))
                    }
                }
                add(
                    EquivalenceCase(
                        "$label/subject",
                        tokens,
                        "RECENTLY_UPDATED",
                        0,
                        Filters(subjectId = "MATH"),
                    ),
                )
                // 组合筛选按夹具真实分布挑桶：100 档 index=100m、1k 档 index=10m，
                // 两档都落在 subject=MATH（index 偶）、chapter-0（index%20==0）、
                // mastery=learning（100 档 m%4==0；1k 档 m%8==0）的交集里，且时间窗内仍有行
                // （空页不算等价证据，非空信号在断言里兜底）。
                add(
                    EquivalenceCase(
                        "$label/combined-filter",
                        tokens,
                        "RECENTLY_UPDATED",
                        0,
                        COMBINED_FILTER,
                    ),
                )
            }
            listOf(0, 200).forEach { offset ->
                add(
                    EquivalenceCase(
                        "10k/RECENTLY_UPDATED/offset$offset",
                        TOKENS_WIDE,
                        "RECENTLY_UPDATED",
                        offset,
                        Filters(),
                    ),
                )
            }
        }

        cases.forEach { case ->
            val oldRows = rawRows(preChangeQuery(case))
            val liveRows = rawRows(liveQuery(case))
            assertTrue(
                "同序门与夹具脱节：case=${case.label} 改前 SQL 返回空页（空结果不构成等价证据）",
                oldRows.rows.isNotEmpty(),
            )
            assertEquals(
                "逐列不等价（live vs 改前）：case=${case.label}\n" +
                    "  old=${oldRows.entries()}\n  new=${liveRows.entries()}",
                oldRows.entries(),
                liveRows.entries(),
            )
            // 生产端口路径（Room 编译/绑定/游标转换）必须给出同一 entryId 序列。
            val portIds = page(case.tokens, case.sort, case.offset, case.filters)
            assertEquals(
                "端口路径 entryId 序不等价（vs 改前 SQL）：case=${case.label}",
                oldRows.rows.map { it[0] },
                portIds,
            )
        }
    }

    /**
     * 并列键与列权重的内存小夹具（与真库解耦，不依赖 1 万行夹具的分布）：
     *
     * - 8 行都含 token "w"，按列/权重造出 7 / 6 / 5 / 4 / 4 / 4 / 3 / 1 的 rank，
     *   `updated_at` 与 rank **反序**排列——排序失效（退回 updated_at）必然给出不同序；
     *   其中两行 rank 与 updated_at 完全相同（并列键），必须按 entry_id ASC 决胜。
     * - 两行用 query "MATH" 钉 subject 列（权重 2）与 stem（权重 4）：subject 命中行
     *   updated_at 更新，排序失效会把它排到前面。
     *
     * 两种路径（改前 SQL 冻结副本 / 生产 live SQL）都必须给出这条序。
     */
    @Test
    fun tiedKeysAndColumnWeightsKeepTheExactPreChangeOrder() = runBlocking {
        val expectedW = listOf(
            "entry-rank7",
            "entry-rank6",
            "entry-rank5",
            "entry-tie-a",
            "entry-tie-b",
            "entry-rank4",
            "entry-rank3",
            "entry-rank1",
        )
        val expectedMath = listOf("entry-alpha", "entry-beta")

        val rows = listOf(
            WeightRow("entry-rank7", title = "w 题面", solution = "w 解答", updatedAt = 1_000),
            WeightRow("entry-rank6", title = "w 题面", knowledgeLabel = "w 知识点", updatedAt = 2_000),
            WeightRow("entry-rank5", title = "w 题面", options = "w 选项", updatedAt = 3_000),
            WeightRow("entry-tie-b", title = "w 题面", updatedAt = 7_000),
            WeightRow("entry-tie-a", title = "w 题面", updatedAt = 7_000),
            WeightRow("entry-rank4", title = "w 题面", updatedAt = 4_000),
            WeightRow("entry-rank3", title = "普通题面", solution = "w 解答", updatedAt = 5_000),
            WeightRow("entry-rank1", title = "普通题面", chapterLabel = "w 章节", updatedAt = 6_000),
            // entryId 不得含 query token（FTS 会把 `entry-alpha` 拆成 entry/alpha；
            // 旧夹具里 `entry-math-*` 的 id 自带 "math" 使两行的 stem 命中同时翻倍——
            // 已实测踩到，这里用无 token 的 id）。
            WeightRow("entry-alpha", title = "MATH 题面", updatedAt = 200, subject = "PHYSICS"),
            WeightRow("entry-beta", title = "普通题面", updatedAt = 999, subject = "MATH"),
        )

        val memoryStore = StudyDatabaseFactory.openInMemory(
            ApplicationProvider.getApplicationContext(),
        ) as RoomStudyDatabase
        try {
            seedWeightFixture(memoryStore, rows)
            memoryStore.refreshLibrarySearchProjection()

            listOf("w" to expectedW, "MATH" to expectedMath).forEach { (query, expected) ->
                val tokens = CjkTextTokenizer.tokens(query)
                val case = EquivalenceCase(
                    "weights/$query",
                    tokens = tokens,
                    sort = "RECENTLY_UPDATED",
                    offset = 0,
                    filters = Filters(),
                    matchQuery = CjkTextTokenizer.matchExpression(query),
                )
                val live = memoryStore.librarySearchPage(
                    matchQuery = case.matchQuery,
                    subjectId = null,
                    sectionId = null,
                    masteryId = null,
                    createdFromEpochMillis = null,
                    createdToEpochMillis = null,
                    sort = case.sort,
                    tokens = tokens,
                    offset = 0,
                    limit = FIRST_SCREEN_LIMIT,
                ).map { it.entryId }
                val preChange = rawRows(preChangeQuery(case), memoryStore.database).rows.map { it[0] }
                assertEquals("列权重序不符（query=$query）", expected, live)
                assertEquals("改前 SQL 与期望序不符（query=$query）", expected, preChange)
            }
        } finally {
            memoryStore.close()
        }
    }

    // ------------------------------------------------------------------
    // 夹具行 / 查询构造
    // ------------------------------------------------------------------

    private data class WeightRow(
        val entryId: String,
        val title: String,
        val subject: String = "PHYSICS",
        val solution: String? = null,
        val options: String? = null,
        val knowledgeLabel: String? = null,
        val chapterLabel: String? = null,
        val updatedAt: Long,
    )

    private suspend fun seedWeightFixture(store: RoomStudyDatabase, rows: List<WeightRow>) {
        val problems = rows.map { row ->
            ProblemSeedRecord(
                problemId = "problem-${row.entryId}",
                canonicalFingerprint = row.entryId.hashCode().toUInt().toString(16).padStart(64, '0'),
                subject = row.subject,
                createdAtEpochMillis = row.updatedAt,
            )
        }
        val revisions = rows.map { row ->
            ProblemRevisionSeedRecord(
                revisionId = "revision-${row.entryId}",
                problemId = "problem-${row.entryId}",
                revisionNumber = 1,
                title = row.title,
                problemMarkdown = "题面 ${row.entryId}",
                questionDocumentSnapshot = row.options,
                answerSpecId = null,
                answerSpecSnapshot = row.solution,
                answerVerificationStatus = StudyDbValue.VerificationStatus.UNKNOWN,
                sourceType = "CAPTURE_CONFIRMED",
                sourceReference = null,
                contentFingerprint = "f".repeat(64),
                createdAtEpochMillis = row.updatedAt,
            )
        }
        val units = rows.map { row ->
            PracticeUnitSeedRecord(
                practiceUnitId = "practice-${row.entryId}",
                problemId = "problem-${row.entryId}",
                problemRevisionId = "revision-${row.entryId}",
                unitKey = "whole-problem",
                unitKind = "WHOLE_PROBLEM",
                title = row.title,
                promptMarkdown = "题面 ${row.entryId}",
                estimatedSeconds = 180,
                createdAtEpochMillis = row.updatedAt,
            )
        }
        val entries = rows.map { row ->
            ErrorBookEntrySeedRecord(
                entryId = row.entryId,
                practiceUnitId = "practice-${row.entryId}",
                problemId = "problem-${row.entryId}",
                currentRevisionId = "revision-${row.entryId}",
                sourceKey = null,
                acceptedAtEpochMillis = row.updatedAt,
                updatedAtEpochMillis = row.updatedAt,
            )
        }
        store.seedStudyFacts(
            StudySeedBundle(
                problems = problems,
                revisions = revisions,
                practiceUnits = units,
                errorBookEntries = entries,
            ),
        )
        val classifications = buildList {
            rows.filter { it.chapterLabel != null }.forEach { row ->
                add(
                    ProblemClassificationBindingEntity(
                        bindingId = "class-chapter-${row.entryId}",
                        problemId = "problem-${row.entryId}",
                        basisRevisionId = "revision-${row.entryId}",
                        dimension = "CHAPTER",
                        labelId = "chapter-x",
                        displayName = requireNotNull(row.chapterLabel),
                        taxonomyVersion = "ranking-test-taxonomy",
                        acceptanceSource = "LOCAL_POLICY_ACCEPTED",
                        acceptedAtEpochMillis = row.updatedAt,
                    ),
                )
            }
            rows.filter { it.knowledgeLabel != null }.forEach { row ->
                add(
                    ProblemClassificationBindingEntity(
                        bindingId = "class-knowledge-${row.entryId}",
                        problemId = "problem-${row.entryId}",
                        basisRevisionId = "revision-${row.entryId}",
                        dimension = "KNOWLEDGE",
                        labelId = "knowledge-x",
                        displayName = requireNotNull(row.knowledgeLabel),
                        taxonomyVersion = "ranking-test-taxonomy",
                        acceptanceSource = "LOCAL_POLICY_ACCEPTED",
                        acceptedAtEpochMillis = row.updatedAt,
                    ),
                )
            }
        }
        if (classifications.isNotEmpty()) {
            store.database.problemOrganizationDao().insertClassificationBindings(classifications)
        }
    }

    private data class Filters(
        val subjectId: String? = null,
        val sectionId: String? = null,
        val masteryId: String? = null,
        val createdFromEpochMillis: Long? = null,
        val createdToEpochMillis: Long? = null,
    )

    private data class EquivalenceCase(
        val label: String,
        val tokens: List<String>,
        val sort: String,
        val offset: Int,
        val filters: Filters,
        val matchQuery: String = CjkTextTokenizer.matchExpression(
            tokens.joinToString(separator = " "),
        ),
    )

    /** 改前 SQL 冻结副本的绑定数组（顺序 = 原文里 `?` 的出现顺序）。 */
    private fun preChangeQuery(case: EquivalenceCase): BuiltQuery {
        val bindings = mutableListOf<Any>()
        bindings += case.matchQuery
        case.filters.subjectId?.let { bindings += it }
        case.filters.sectionId?.let { bindings += it }
        case.filters.masteryId?.let { bindings += it }
        case.filters.createdFromEpochMillis?.let { bindings += it }
        case.filters.createdToEpochMillis?.let { bindings += it }
        val primary = CjkTextTokenizer.quotedPhrase(case.tokens.first())
        repeat(HIT_SET_COUNT - EXTRA_TOKEN_COUNT) { bindings += primary }
        val extras = case.tokens.drop(1).take(EXTRA_TOKEN_COUNT).map(CjkTextTokenizer::quotedPhrase)
        repeat(EXTRA_TOKEN_COUNT) { index ->
            bindings += extras.getOrElse(index) { CjkTextTokenizer.quotedPhrase("\uFFFD") }
        }
        bindings += FIRST_SCREEN_LIMIT.toLong()
        bindings += case.offset.toLong()
        return BuiltQuery(
            preChangeSearchPageSql(case.sort, case.filters),
            bindings,
        )
    }

    private fun liveQuery(case: EquivalenceCase): BuiltQuery = BuiltQuery(
        liveSearchPageSql(
            tokens = case.tokens,
            matchQuery = case.matchQuery,
            sort = case.sort,
            filters = case.filters,
            offset = case.offset,
        ),
        liveBindings(case),
    )

    private fun liveBindings(case: EquivalenceCase): List<Any> {
        val bindings = mutableListOf<Any>()
        val primary = CjkTextTokenizer.quotedPhrase(case.tokens.first())
        repeat(HIT_SET_COUNT - EXTRA_TOKEN_COUNT) { bindings += primary }
        val extras = case.tokens.drop(1).take(EXTRA_TOKEN_COUNT).map(CjkTextTokenizer::quotedPhrase)
        repeat(EXTRA_TOKEN_COUNT) { index ->
            bindings += extras.getOrElse(index) { CjkTextTokenizer.quotedPhrase("\uFFFD") }
        }
        bindings += case.matchQuery
        case.filters.subjectId?.let { bindings += it }
        case.filters.sectionId?.let { bindings += it }
        case.filters.masteryId?.let { bindings += it }
        case.filters.createdFromEpochMillis?.let { bindings += it }
        case.filters.createdToEpochMillis?.let { bindings += it }
        bindings += FIRST_SCREEN_LIMIT.toLong()
        bindings += case.offset.toLong()
        return bindings
    }

    /** 生产 builder 的 live SQL 原文（本类所有用例都带 LIMIT/OFFSET）。 */
    private fun liveSearchPageSql(
        tokens: List<String>,
        matchQuery: String = CjkTextTokenizer.matchExpression(tokens.joinToString(" ")),
        sort: String = "RECENTLY_UPDATED",
        filters: Filters = Filters(),
        offset: Int = 0,
    ): String = RoomLibrarySearchStore(store.database).buildLibrarySearchRawQuery(
        matchQuery = matchQuery,
        subjectId = filters.subjectId,
        sectionId = filters.sectionId,
        masteryId = filters.masteryId,
        createdFromEpochMillis = filters.createdFromEpochMillis,
        createdToEpochMillis = filters.createdToEpochMillis,
        sort = sort,
        tokens = tokens,
        limit = FIRST_SCREEN_LIMIT,
        offset = offset,
    ).sql

    // ------------------------------------------------------------------
    // 改前 SQL / 计划的冻结副本（与 git 59fd8a32→批 1 期间的 ranking builder 逐字）
    // ------------------------------------------------------------------

    private fun preChangeSearchPageSql(sort: String, filters: Filters): String {
        val filterText = buildString {
            if (filters.subjectId != null) append("\n  AND catalog.subject = ?")
            if (filters.sectionId != null) {
                append(
                    "\n  AND EXISTS (\n" +
                        "      SELECT 1 FROM problem_classification_binding AS classification\n" +
                        "      WHERE classification.problem_id = catalog.problem_id\n" +
                        "        AND classification.basis_revision_id = catalog.problem_revision_id\n" +
                        "        AND classification.dimension = 'CHAPTER'\n" +
                        "        AND classification.label_id = ?\n" +
                        "  )",
                )
            }
            if (filters.masteryId != null) append("\n  AND catalog.mastery_id = ?")
            if (filters.createdFromEpochMillis != null) {
                append("\n  AND catalog.created_at_epoch_millis >= ?")
            }
            if (filters.createdToEpochMillis != null) {
                append("\n  AND catalog.created_at_epoch_millis <= ?")
            }
        }
        val ranking = StringBuilder()
        listOf(
            "stem_text" to 4,
            "solution_text" to 3,
            "knowledge_points" to 2,
            "subject" to 2,
            "options_text" to 1,
            "chapter" to 1,
            "tags" to 1,
            "error_reason" to 1,
            "formula_tokens" to 1,
        ).forEachIndexed { index, (column, weight) ->
            if (index > 0) ranking.append("\n  + ")
            ranking.append(
                "$weight * (CASE WHEN EXISTS (\n" +
                    "    SELECT 1 FROM library_search_fts AS ranked\n" +
                    "    WHERE ranked.docid = content.content_row_id\n" +
                    "      AND ranked.$column MATCH ?\n" +
                    ") THEN 1 ELSE 0 END)",
            )
        }
        repeat(EXTRA_TOKEN_COUNT) {
            ranking.append(
                "\n  + (CASE WHEN EXISTS (\n" +
                    "    SELECT 1 FROM library_search_fts\n" +
                    "    WHERE library_search_fts.docid = content.content_row_id\n" +
                    "      AND library_search_fts MATCH ?\n" +
                    ") THEN 1 ELSE 0 END)",
            )
        }
        val sortClause = when (sort) {
            "RECENTLY_CREATED" -> "catalog.created_at_epoch_millis DESC,\n    "
            else -> ""
        }
        return "SELECT catalog.*,\n" +
            "       snippet(library_search_fts, '【', '】', '…', -1, 12) AS snippet\n" +
            "FROM library_search_fts\n" +
            "JOIN library_search_content AS content\n" +
            "    ON content.content_row_id = library_search_fts.docid\n" +
            "JOIN library_catalog AS catalog\n" +
            "    ON catalog.problem_revision_id = content.problem_revision_id\n" +
            "WHERE library_search_fts MATCH ?$filterText\n" +
            "ORDER BY (\n" +
            "    $ranking\n" +
            ") DESC,\n" +
            "    $sortClause" +
            "catalog.updated_at_epoch_millis DESC,\n" +
            "    catalog.entry_id ASC\n" +
            "LIMIT ? OFFSET ?"
    }

    // ------------------------------------------------------------------
    // raw 执行 / 计划读取
    // ------------------------------------------------------------------

    private data class RawRows(val rows: List<List<String?>>) {
        fun entries(): List<String> = rows.map { row ->
            row.joinToString(separator = "\u0001") { it ?: "\u0000" }
        }
    }

    private data class BuiltQuery(val sql: String, val bindings: List<Any>)

    /** 在**生产连接**上执行冻结副本 / live SQL（`withRawConnection`，同库同驱动）。 */
    private suspend fun rawRows(
        query: BuiltQuery,
        database: StudyDatabase = store.database,
    ): RawRows = database.withRawConnection(isReadOnly = true) { transactor ->
        transactor.usePrepared(query.sql) { statement ->
            bindPositional(statement, query.bindings)
            val rows = mutableListOf<List<String?>>()
            while (statement.step()) {
                rows += (0 until statement.getColumnCount()).map { index ->
                    if (statement.isNull(index)) null else statement.getText(index)
                }
            }
            RawRows(rows)
        }
    }

    private fun bindPositional(statement: SQLiteStatement, values: List<Any>) {
        values.forEachIndexed { index, value ->
            when (value) {
                is Long -> statement.bindLong(index + 1, value)
                is Int -> statement.bindLong(index + 1, value.toLong())
                else -> statement.bindText(index + 1, value.toString())
            }
        }
    }

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
                buildList { while (cursor.moveToNext()) add(cursor.getString(detail)) }
            }
        }
    }

    /** 平台 rawQuery 的 String[] 绑定拒绝 null；计划与绑定值无关（同既有门口径）。 */
    private fun sentinelArgs(sql: String): Array<String?> =
        Array(sql.count { it == '?' }) { NO_FILTER_PLACEHOLDER }

    /**
     * 判据：紧跟在 `CORRELATED SCALAR SUBQUERY n` 头之后的 FTS 虚表访问行 = 逐行探测。
     * 改前计划里 12 个排序探测都是这种"头 + 一行 FTS 扫描"的形状；新计划里 FTS 集合
     * 扫描行前面是 `MATERIALIZE hit_*`（或另一条 FTS 行），不会被误判。
     */
    private fun rowLevelFtsProbesOf(plan: List<String>): List<String> = plan.filterIndexed { index, line ->
        index > 0 &&
            FTS_VIRTUAL_TABLE_LINE.containsMatchIn(line) &&
            CORRELATED_SUBQUERY_HEADER.containsMatchIn(plan[index - 1])
    }

    private fun List<Long>.percentile95(): Long {
        require(isNotEmpty())
        val sorted = sorted()
        return sorted[((sorted.size - 1) * 95) / 100]
    }

    // ------------------------------------------------------------------
    // 共享夹具上的读写辅助
    // ------------------------------------------------------------------

    private suspend fun page(
        tokens: List<String>,
        sort: String = "RECENTLY_UPDATED",
        offset: Int = 0,
        filters: Filters = Filters(),
        matchQuery: String = CjkTextTokenizer.matchExpression(tokens.joinToString(" ")),
    ): List<String> = store.librarySearchPage(
        matchQuery = matchQuery,
        subjectId = filters.subjectId,
        sectionId = filters.sectionId,
        masteryId = filters.masteryId,
        createdFromEpochMillis = filters.createdFromEpochMillis,
        createdToEpochMillis = filters.createdToEpochMillis,
        sort = sort,
        tokens = tokens,
        offset = offset,
        limit = FIRST_SCREEN_LIMIT,
    ).map { it.entryId }

    private suspend fun countHits(tokens: List<String>, label: String): Int {
        val count = store.librarySearchCount(
            matchQuery = CjkTextTokenizer.matchExpression(tokens.joinToString(" ")),
            subjectId = null,
            sectionId = null,
            masteryId = null,
            createdFromEpochMillis = null,
            createdToEpochMillis = null,
        )
        return count.also { println("S18 ranking fixture $label=$it") }
    }

    private val store: RoomStudyDatabase get() = sharedStore

    companion object {
        private const val ENTRY_COUNT = 10_000
        private const val FIRST_SCREEN_LIMIT = 20
        private const val SAMPLE_COUNT = 20
        private const val WARMUP_COUNT = 3
        private const val TEN_K_SAMPLE_COUNT = 20
        private const val CONCURRENT_TASKS = 10
        private const val HIT_SET_COUNT = 12
        private const val EXTRA_TOKEN_COUNT = 3
        private const val NO_FILTER_PLACEHOLDER = "\u0001none"

        /** 1k 命中探针：夹具注入到每第 10 行（`revision_id GLOB 'revision-*0'`）的 token。 */
        private const val ONE_K_MARKER = "千级探针"

        private val TOKENS_HUNDRED = CjkTextTokenizer.tokens("独特检索")
        private val TOKENS_ONE_K = CjkTextTokenizer.tokens(ONE_K_MARKER)
        private val TOKENS_WIDE = CjkTextTokenizer.tokens("分页题目")

        /**
         * 100 档与 1k 档都有真实交集的组合筛选（见用例内注释的桶推算）：
         * subject=MATH + chapter-0 + mastery=learning + created ∈ [2000, 8000]。
         */
        private val COMBINED_FILTER = Filters(
            subjectId = "MATH",
            sectionId = "chapter-0",
            masteryId = "learning",
            createdFromEpochMillis = 2_000L,
            createdToEpochMillis = 8_000L,
        )

        private val CI_MULTIPLIER: Long = run {
            val fromArgs = androidx.test.platform.app.InstrumentationRegistry
                .getArguments()
                .getString("ciSlowRunner")
            if (fromArgs != null || System.getenv("CI") != null) 4L else 1L
        }

        /** 与 `PerformanceGateTest.SEARCH_P95_TARGET_MS` 同值同口径。 */
        private val PAGE_P95_BUDGET_MS = 500L * CI_MULTIPLIER

        /**
         * 10k 档宽松 backstop：实测 P95=144ms（最终轮；四轮区间 139–167ms），取
         * 2000ms×ciSlowRunner（本地 ≈13.9× 余量、CI ×4 后 ≈55×）——只拦"退化到秒级"
         * （改前 19208ms 量级），不是性能预算；100/1k 两档的 500ms 门仍是性能判据。
         */
        private val TEN_K_P95_BACKSTOP_MS = 2_000L * CI_MULTIPLIER

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
         * **红样例**：改前 ranking SQL 的设备计划原文（2026-10-07 由改前 builder 现场
         * EXPLAIN 取出，API 34 / framework SQLite 3.39.2）。病征：主循环 `SCAN
         * library_search_fts` 之后 12 个排序标志各是一个 `CORRELATED SCALAR SUBQUERY`
         * \+ 一行 `SCAN ranked VIRTUAL TABLE INDEX n:`（逐命中行回探 FTS）。
         */
        private val PRE_CHANGE_RANKING_PLAN: List<String> = listOf(
            "MATERIALIZE library_catalog",
            "SEARCH entry USING INDEX index_error_book_entry_status_updated_at_epoch_millis (status=?)",
            "SEARCH problem USING INDEX sqlite_autoindex_problem_1 (problem_id=?)",
            "SEARCH unit USING COVERING INDEX sqlite_autoindex_practice_unit_1 (practice_unit_id=?)",
            "SEARCH revision USING INDEX index_problem_revision_problem_id_revision_id (problem_id=? AND revision_id=?)",
            "SEARCH memory USING INDEX " +
                "index_learner_problem_memory_state_projection_name_practice_unit_id_learner_id " +
                "(projection_name=? AND practice_unit_id=?) LEFT-JOIN",
            "CORRELATED SCALAR SUBQUERY 15",
            "SEARCH binding USING COVERING INDEX " +
                "index_practice_unit_knowledge_binding_practice_unit_id_knowledge_node_id_basis_revision_id_taxonomy_version " +
                "(practice_unit_id=?)",
            "SEARCH mastery USING INDEX sqlite_autoindex_learner_knowledge_mastery_state_1 " +
                "(projection_name=? AND learner_id=? AND knowledge_node_id=?)",
            "CORRELATED SCALAR SUBQUERY 16",
            "SEARCH classification USING INDEX " +
                "index_problem_classification_binding_problem_id_basis_revision_id_dimension_label_id " +
                "(problem_id=? AND basis_revision_id=? AND dimension=?)",
            "CORRELATED SCALAR SUBQUERY 17",
            "SEARCH classification USING INDEX " +
                "index_problem_classification_binding_problem_id_basis_revision_id_dimension_label_id " +
                "(problem_id=? AND basis_revision_id=? AND dimension=?)",
            "SCAN library_search_fts VIRTUAL TABLE INDEX 11:",
            "SEARCH content USING INTEGER PRIMARY KEY (rowid=?)",
            "SEARCH catalog USING AUTOMATIC COVERING INDEX (problem_revision_id=?)",
            "CORRELATED SCALAR SUBQUERY 1",
            "SCAN ranked VIRTUAL TABLE INDEX 2:",
            "CORRELATED SCALAR SUBQUERY 2",
            "SCAN ranked VIRTUAL TABLE INDEX 4:",
            "CORRELATED SCALAR SUBQUERY 3",
            "SCAN ranked VIRTUAL TABLE INDEX 7:",
            "CORRELATED SCALAR SUBQUERY 4",
            "SCAN ranked VIRTUAL TABLE INDEX 5:",
            "CORRELATED SCALAR SUBQUERY 5",
            "SCAN ranked VIRTUAL TABLE INDEX 3:",
            "CORRELATED SCALAR SUBQUERY 6",
            "SCAN ranked VIRTUAL TABLE INDEX 6:",
            "CORRELATED SCALAR SUBQUERY 7",
            "SCAN ranked VIRTUAL TABLE INDEX 8:",
            "CORRELATED SCALAR SUBQUERY 8",
            "SCAN ranked VIRTUAL TABLE INDEX 9:",
            "CORRELATED SCALAR SUBQUERY 9",
            "SCAN ranked VIRTUAL TABLE INDEX 10:",
            "CORRELATED SCALAR SUBQUERY 10",
            "SCAN library_search_fts VIRTUAL TABLE INDEX 11:",
            "CORRELATED SCALAR SUBQUERY 11",
            "SCAN library_search_fts VIRTUAL TABLE INDEX 11:",
            "CORRELATED SCALAR SUBQUERY 12",
            "SCAN library_search_fts VIRTUAL TABLE INDEX 11:",
            "USE TEMP B-TREE FOR ORDER BY",
        )

        private lateinit var context: Context
        private lateinit var sharedStore: RoomStudyDatabase
        private lateinit var databaseName: String

        @JvmStatic
        @BeforeClass
        fun buildSharedFixture() {
            context = ApplicationProvider.getApplicationContext()
            databaseName = "s18-fts-ranking-${System.nanoTime()}.db"
            context.deleteDatabase(databaseName)
            runBlocking {
                sharedStore = StudyDatabaseFactory.open(context, databaseName) as RoomStudyDatabase
                val seedMillis = measureTimeMillis {
                    sharedStore.database.seedLibraryCatalogScale(ENTRY_COUNT)
                }
                val bootstrapMillis = measureTimeMillis {
                    // 1k 档探针：每第 10 行注入 token，再走同一增量投影刷新。
                    sharedStore.database.withWriteTransaction {
                        usePrepared(
                            "UPDATE problem_revision " +
                                "SET problem_markdown = problem_markdown || ' $ONE_K_MARKER' " +
                                "WHERE revision_id GLOB 'revision-*0'",
                        ) { statement -> statement.step() }
                    }
                    sharedStore.refreshLibrarySearchProjection()
                }
                // Room 惰性打开：借一次读把 schema/连接立起来，平台连接随后读同一文件。
                sharedStore.database.withRawConnection(isReadOnly = true) { connection ->
                    connection.usePrepared("SELECT 1") { statement -> statement.step() }
                }
                println(
                    "S18 ranking fixture built: $ENTRY_COUNT entries " +
                        "seed=${seedMillis}ms marker+bootstrap=${bootstrapMillis}ms",
                )
            }
        }

        @JvmStatic
        @AfterClass
        fun closeSharedFixture() {
            if (::sharedStore.isInitialized) {
                sharedStore.close()
            }
            context.deleteDatabase(databaseName)
        }
    }
}
