package com.tingyun.smartmistakebook.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
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
 * **S18 目录路径规模门**（`docs/research/2026-10-05-stage3c-part2-plan.md` §3 批 2）：
 * `library_catalog` 视图在 5 万行、且 **mastery / 分类 / 投影齐全**（见
 * [seedLibraryCatalogScale]）的真实形态下，三条目录路径的 EQP 结构断言 + 计时 backstop：
 *
 * | 路径 | SQL | 预算（×ciSlowRunner） |
 * |---|---|---|
 * | page（首屏） | `LibraryQueryDao.page` | 500ms（`PerformanceGateTest.FIRST_SCREEN_TARGET_MS` 同值） |
 * | count | `LibraryQueryDao.count` | 500ms（首屏口径） |
 * | facets | `subjectFacets` / `sectionFacets` / `masteryFacets` | 200ms（`FACET_P95_TARGET_MS` 同值） |
 *
 * 结构断言是主门（EQP 输出格式无稳定性契约，官方标注 "intended for interactive
 * debugging only"——只断言结构不变量，不比对完整字符串）；计时是宽松 backstop
 * （CI 用 `ciSlowRunner` 系数，口径同 [PerformanceGateTest]）。
 *
 * **夹具共享**：5 万行 ×（条目 + 记忆态 + 分类）+ 知识点/绑定/掌握态是**单次 ~60 秒**
 * 的建库成本（2026-10-05 实测 60,149ms，见报告），三条路径各自重建是三倍成本——
 * 本类用类级共享夹具（`@BeforeClass` 建一次，`@AfterClass` 删），路径之间只共享**只读**状态。
 *
 * **本类不做物化**：量出来的数字决定是否触发（"实测超预算才物化"）；结论与 EXPLAIN
 * 原文落在 `docs/research/2026-10-05-s9-s18-quantification-report.md`。
 */
@RunWith(AndroidJUnit4::class)
class LibraryCatalogScalePerformanceInstrumentedTest {

    @Test
    fun catalogPagePlanAndLatency() = runBlocking {
        val dao = store.database.libraryQueryDao()

        val plan = readPlans(context, databaseName, PAGE_QUERIES)
        printPlans("catalog page", plan)

        // ① 结构断言（无搜索文本的首屏形态）：驱动表 entry 必须走
        //    (status, updated_at) 索引，不得整表扫 error_book_entry。
        val firstScreenPlan = plan.getValue("page-first-screen")
        assertTrue(
            "page 首屏未走 entry 状态索引：\n${firstScreenPlan.joinToString("\n")}",
            firstScreenPlan.any { "SEARCH entry USING INDEX" in it },
        )
        assertFalse(
            "page 首屏整表扫 error_book_entry：\n${firstScreenPlan.joinToString("\n")}",
            firstScreenPlan.any { ENTRY_TABLE_SCAN.containsMatchIn(it) },
        )
        assertFalse(
            "page 首屏整表扫 problem_classification_binding：\n${firstScreenPlan.joinToString("\n")}",
            firstScreenPlan.any { CLASSIFICATION_TABLE_SCAN.containsMatchIn(it) },
        )
        // ② 结构断言（section 过滤形态）：EXISTS 必须走分类索引。
        val sectionPlan = plan.getValue("page-section-filter")
        assertTrue(
            "page + section 过滤未走分类索引：\n${sectionPlan.joinToString("\n")}",
            sectionPlan.any { "SEARCH classification USING INDEX" in it },
        )
        assertFalse(
            "page + section 过滤整表扫分类表：\n${sectionPlan.joinToString("\n")}",
            sectionPlan.any { CLASSIFICATION_TABLE_SCAN.containsMatchIn(it) },
        )
        // ③ 结构断言（S18 回归防线）：记忆态连接必须走索引——不许退回"主键前缀逐行扫"
        //    （2026-10-05 实测无此索引时 count 3.97s / subjectFacets 78.9s）。
        plan.values.forEach { assertMemoryJoinUsesIndex(it, "page") }
        plan.values.forEach { assertMemoryJoinUsesConnectIndex(it, "page") }

        // ④ 计时 backstop + 非空信号（标签/掌握态必须真实，不能是退化空连接）。
        val firstScreen = page(firstScreenLimit = FIRST_SCREEN_LIMIT)
        assertEquals(FIRST_SCREEN_LIMIT, firstScreen.size)
        assertTrue(
            "夹具退化：首屏仍有空 chapter_labels",
            firstScreen.all { !it.chapterLabels.isNullOrEmpty() && !it.knowledgeLabels.isNullOrEmpty() },
        )
        assertTrue(
            "夹具退化：首屏仍有 mastery_id='unknown'",
            firstScreen.all { it.masteryId != "unknown" },
        )

        repeat(PAGE_WARMUP_COUNT) { page() }
        val samples = List(SAMPLE_COUNT) { measureTimeMillis { page() } }
        val p95 = samples.percentile95()
        println("S18 catalog page benchmark: samples=$samples p95=${p95}ms budget=${PAGE_P95_BUDGET_MS}ms")
        assertTrue(
            "目录首屏 page p95=${p95}ms 超过 backstop ${PAGE_P95_BUDGET_MS}ms；samples=$samples",
            p95 < PAGE_P95_BUDGET_MS,
        )

        // 带筛选的首屏（用户实际会用的"章节 + 掌握程度"组合）：同样必须过预算。
        repeat(PAGE_WARMUP_COUNT) { page(sectionId = SECTION_FILTER_ID, masteryId = MASTERY_FILTER_ID) }
        val filteredSamples = List(SAMPLE_COUNT) {
            measureTimeMillis { page(sectionId = SECTION_FILTER_ID, masteryId = MASTERY_FILTER_ID) }
        }
        val filteredP95 = filteredSamples.percentile95()
        println(
            "S18 catalog filtered page benchmark: samples=$filteredSamples " +
                "p95=${filteredP95}ms budget=${PAGE_P95_BUDGET_MS}ms",
        )
        assertTrue(
            "目录筛选首屏 page p95=${filteredP95}ms 超过 backstop ${PAGE_P95_BUDGET_MS}ms；" +
                "samples=$filteredSamples",
            filteredP95 < PAGE_P95_BUDGET_MS,
        )
    }

    @Test
    fun catalogCountPlanAndLatency() = runBlocking {
        val dao = store.database.libraryQueryDao()

        val plan = readPlans(context, databaseName, COUNT_QUERIES)
        printPlans("catalog count", plan)
        val blankPlan = plan.getValue("count-blank")
        assertTrue(
            "count 空白搜索未走 entry 状态索引：\n${blankPlan.joinToString("\n")}",
            blankPlan.any { "SEARCH entry USING INDEX" in it },
        )
        assertFalse(
            "count 空白搜索整表扫 error_book_entry：\n${blankPlan.joinToString("\n")}",
            blankPlan.any { ENTRY_TABLE_SCAN.containsMatchIn(it) },
        )
        assertFalse(
            "count 空白搜索整表扫分类表：\n${blankPlan.joinToString("\n")}",
            blankPlan.any { CLASSIFICATION_TABLE_SCAN.containsMatchIn(it) },
        )
        val sectionPlan = plan.getValue("count-section-mastery-filter")
        assertTrue(
            "count + section 过滤未走分类索引：\n${sectionPlan.joinToString("\n")}",
            sectionPlan.any { "SEARCH classification USING INDEX" in it },
        )
        // S18 回归防线：记忆态连接必须走复合索引（无它时实测 3.97s），不许退回主键前缀扫。
        plan.values.forEach { assertMemoryJoinUsesIndex(it, "count") }
        plan.values.forEach { assertMemoryJoinUsesConnectIndex(it, "count") }

        // 非空信号：三条筛选组合各自真的收窄（退化夹具下会是 0）。
        assertEquals(ENTRY_COUNT, count())
        assertEquals(
            ENTRY_COUNT / LibraryCatalogScale.CHAPTER_COUNT,
            count(sectionId = SECTION_FILTER_ID),
        )
        assertEquals(ENTRY_COUNT / 4, count(masteryId = MASTERY_FILTER_ID))

        repeat(COUNT_WARMUP_COUNT) { count(sectionId = SECTION_FILTER_ID, masteryId = MASTERY_FILTER_ID) }
        val samples = List(SAMPLE_COUNT) {
            measureTimeMillis { count(sectionId = SECTION_FILTER_ID, masteryId = MASTERY_FILTER_ID) }
        }
        val p95 = samples.percentile95()
        println("S18 catalog count benchmark: samples=$samples p95=${p95}ms budget=${COUNT_P95_BUDGET_MS}ms")
        assertTrue(
            "目录 count p95=${p95}ms 超过 backstop ${COUNT_P95_BUDGET_MS}ms；samples=$samples",
            p95 < COUNT_P95_BUDGET_MS,
        )
    }

    @Test
    fun catalogFacetsPlanAndLatency() = runBlocking {
        val dao = store.database.libraryQueryDao()

        val plan = readPlans(context, databaseName, FACET_QUERIES)
        printPlans("catalog facets", plan)
        val sectionPlan = plan.getValue("section-facets")
        assertTrue(
            "sectionFacets 未走分类索引：\n${sectionPlan.joinToString("\n")}",
            sectionPlan.any { "SEARCH classification USING INDEX" in it },
        )
        assertFalse(
            "sectionFacets 整表扫分类表：\n${sectionPlan.joinToString("\n")}",
            sectionPlan.any { CLASSIFICATION_TABLE_SCAN.containsMatchIn(it) },
        )
        plan.values.forEach { facetPlan ->
            assertFalse(
                "facet 整表扫 error_book_entry：\n${facetPlan.joinToString("\n")}",
                facetPlan.any { ENTRY_TABLE_SCAN.containsMatchIn(it) },
            )
        }
        // S18 回归防线：三条 facet 的记忆态连接都走复合索引（无它时 subjectFacets 实测 78.9s）。
        plan.values.forEach { assertMemoryJoinUsesIndex(it, "facet") }
        plan.values.forEach { assertMemoryJoinUsesConnectIndex(it, "facet") }

        // 非空信号：三组 facet 的桶数与总量都必须真实（退化夹具下只有 subject 两项）。
        assertEquals(setOf("MATH", "PHYSICS"), subjectFacets().map { it.id }.toSet())
        assertEquals(ENTRY_COUNT, subjectFacets().sumOf { it.count })
        assertEquals(LibraryCatalogScale.CHAPTER_COUNT, sectionFacets().size)
        assertEquals(ENTRY_COUNT, sectionFacets().sumOf { it.count })
        assertEquals(
            setOf("learning", "mastered", "stale", "conflicted"),
            masteryFacets().map { it.id }.toSet(),
        )
        assertEquals(ENTRY_COUNT, masteryFacets().sumOf { it.count })

        // 计时：三条 facet 路径分别采样（预算是单条路径的 200ms）。
        FacetKind.entries.forEach { facet ->
            repeat(FACET_WARMUP_COUNT) { loadFacet(facet) }
            val samples = List(SAMPLE_COUNT) { measureTimeMillis { loadFacet(facet) } }
            val p95 = samples.percentile95()
            println(
                "S18 catalog facet ${facet.name} benchmark: samples=$samples p95=${p95}ms " +
                    "budget=${FACET_P95_BUDGET_MS}ms",
            )
            assertTrue(
                "目录 facet ${facet.name} p95=${p95}ms 超过 backstop ${FACET_P95_BUDGET_MS}ms；" +
                    "samples=$samples",
                p95 < FACET_P95_BUDGET_MS,
            )
        }
    }

    private enum class FacetKind { SUBJECT, SECTION, MASTERY }

    private suspend fun loadFacet(facet: FacetKind) = when (facet) {
        FacetKind.SUBJECT -> subjectFacets()
        FacetKind.SECTION -> sectionFacets()
        FacetKind.MASTERY -> masteryFacets()
    }

    private suspend fun subjectFacets() = store.database.libraryQueryDao().subjectFacets(
        searchText = "",
        sectionId = null,
        masteryId = null,
        createdFromEpochMillis = null,
        createdToEpochMillis = null,
    )

    private suspend fun sectionFacets() = store.database.libraryQueryDao().sectionFacets(
        searchText = "",
        subjectId = null,
        masteryId = null,
        createdFromEpochMillis = null,
        createdToEpochMillis = null,
    )

    private suspend fun masteryFacets() = store.database.libraryQueryDao().masteryFacets(
        searchText = "",
        subjectId = null,
        sectionId = null,
        createdFromEpochMillis = null,
        createdToEpochMillis = null,
    )

    private suspend fun count(
        sectionId: String? = null,
        masteryId: String? = null,
    ) = store.database.libraryQueryDao().count(
        searchText = "",
        subjectId = null,
        sectionId = sectionId,
        masteryId = masteryId,
        createdFromEpochMillis = null,
        createdToEpochMillis = null,
    )

    private suspend fun page(
        sectionId: String? = null,
        masteryId: String? = null,
        firstScreenLimit: Int = FIRST_SCREEN_LIMIT,
    ) = store.database.libraryQueryDao().page(
        searchText = "",
        subjectId = null,
        sectionId = sectionId,
        masteryId = masteryId,
        createdFromEpochMillis = null,
        createdToEpochMillis = null,
        sort = "RECENTLY_UPDATED",
        offset = 0,
        limit = firstScreenLimit,
    )

    /** 读 [queries] 里每条 SQL 的 EXPLAIN QUERY PLAN（同一库文件、平台连接）。 */
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

    private fun printPlans(label: String, plans: Map<String, List<String>>) {
        plans.forEach { (name, plan) ->
            println("S18 $label plan[$name]:\n${plan.joinToString("\n") { "  $it" }}")
        }
    }

    /** 记忆态连接必须走索引，不许整表扫 `learner_problem_memory_state`。 */
    private fun assertMemoryJoinUsesIndex(plan: List<String>, label: String) {
        val planText = plan.joinToString("\n")
        assertTrue(
            "$label 的记忆态连接未走索引：\n$planText",
            plan.any { it.startsWith("SEARCH memory ") && "USING" in it },
        )
        assertFalse(
            "$label 整表扫 learner_problem_memory_state：\n$planText",
            plan.any { MEMORY_TABLE_SCAN.containsMatchIn(it) },
        )
    }

    /**
     * S18（schema 63）回归防线：记忆态连接必须走复合索引
     * `index_learner_problem_memory_state_projection_name_practice_unit_id_learner_id`。
     * 这条断言钉的是可证伪的结构信号——把它临时退回 62 的形态（索引不存在）时，
     * 规划器会改用主键前缀计划，断言当场变红（红/绿证据见量化报告）。
     */
    private fun assertMemoryJoinUsesConnectIndex(plan: List<String>, label: String) {
        assertTrue(
            "$label 的记忆态连接未走 S18 复合索引 $MEMORY_CONNECT_INDEX：\n${plan.joinToString("\n")}",
            plan.any { MEMORY_CONNECT_INDEX in it },
        )
    }

    private fun List<Long>.percentile95(): Long {
        require(isNotEmpty())
        val sorted = sorted()
        return sorted[((sorted.size - 1) * 95) / 100]
    }

    companion object {
        private const val ENTRY_COUNT = 50_000
        private const val FIRST_SCREEN_LIMIT = 20
        private const val SECTION_FILTER_ID = "chapter-3"
        private const val MASTERY_FILTER_ID = "mastered"
        private const val SAMPLE_COUNT = 24
        private const val PAGE_WARMUP_COUNT = 3
        private const val COUNT_WARMUP_COUNT = 3
        private const val FACET_WARMUP_COUNT = 3

        /** EXPLAIN 用的"不启用该筛选"占位值（绑定值不影响计划；见 PAGE_QUERIES 注释）。 */
        private const val _NO_FILTER = "\u0001none"

        private lateinit var context: Context
        private lateinit var store: RoomStudyDatabase
        private lateinit var databaseName: String

        /**
         * 类级共享夹具：5 万行目录 + mastery/分类/投影（一次 ~7 分钟量级，见报告）。
         * JUnit 在**所有方法之前**调用一次；测的是读路径，夹具只读。
         */
        @JvmStatic
        @BeforeClass
        fun buildSharedFixture() {
            context = ApplicationProvider.getApplicationContext()
            databaseName = "s18-library-catalog-scale-${System.nanoTime()}.db"
            context.deleteDatabase(databaseName)
            runBlocking {
                store = StudyDatabaseFactory.open(context, databaseName) as RoomStudyDatabase
                val fixtureMillis = measureTimeMillis {
                    store.database.seedLibraryCatalogScale(ENTRY_COUNT)
                }
                // Room 惰性打开：借一次读把 schema/连接立起来，平台连接随后读同一文件。
                store.database.withRawConnection(isReadOnly = true) { connection ->
                    connection.usePrepared("SELECT 1") { statement -> statement.step() }
                }
                println("S18 shared fixture built: ${ENTRY_COUNT} entries in ${fixtureMillis}ms")
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

        private val CI_MULTIPLIER: Long = run {
            val fromArgs = androidx.test.platform.app.InstrumentationRegistry
                .getArguments()
                .getString("ciSlowRunner")
            if (fromArgs != null || System.getenv("CI") != null) 4L else 1L
        }

        /** 与 `PerformanceGateTest.FIRST_SCREEN_TARGET_MS` 同值同口径。 */
        val PAGE_P95_BUDGET_MS = 500L * CI_MULTIPLIER

        /** count 走首屏口径（同值）。 */
        val COUNT_P95_BUDGET_MS = 500L * CI_MULTIPLIER

        /** 与 `PerformanceGateTest.FACET_P95_TARGET_MS` 同值同口径。 */
        val FACET_P95_BUDGET_MS = 200L * CI_MULTIPLIER

        private val ENTRY_TABLE_SCAN = Regex("""(?i)^SCAN\s+(TABLE\s+)?error_book_entry(\s|$)""")
        private val CLASSIFICATION_TABLE_SCAN =
            Regex("""(?i)^SCAN\s+(TABLE\s+)?problem_classification_binding(\s|$)""")
        private val MEMORY_TABLE_SCAN =
            Regex("""(?i)^SCAN\s+(TABLE\s+)?learner_problem_memory_state(\s|$)""")

        /** stage 3C 后半批 2 / S18（schema 63）新增的连接索引。 */
        private const val MEMORY_CONNECT_INDEX =
            "index_learner_problem_memory_state_projection_name_practice_unit_id_learner_id"

        /**
         * `LibraryQueryDao.page`（`LibraryQueryDao.kt:19-66`）的逐字副本。
         * 为什么是副本：room3 的 prepared-statement 路径拒绝执行 EXPLAIN；
         * `@Query` 是 BINARY retention，运行时取不到原文（同
         * [ProblemDaoActiveMistakesPerformanceInstrumentedTest] 先例）。DAO 改动必须同步。
         */
        private val PAGE_SQL = """
            SELECT * FROM library_catalog AS catalog
            WHERE (:subjectId IS NULL OR catalog.subject = :subjectId)
              AND (
                  :sectionId IS NULL OR EXISTS (
                      SELECT 1 FROM problem_classification_binding AS classification
                      WHERE classification.problem_id = catalog.problem_id
                        AND classification.basis_revision_id = catalog.problem_revision_id
                        AND classification.dimension = 'CHAPTER'
                        AND classification.label_id = :sectionId
                  )
              )
              AND (:masteryId IS NULL OR catalog.mastery_id = :masteryId)
              AND (
                  :createdFromEpochMillis IS NULL OR
                      catalog.created_at_epoch_millis >= :createdFromEpochMillis
              )
              AND (
                  :createdToEpochMillis IS NULL OR
                      catalog.created_at_epoch_millis <= :createdToEpochMillis
              )
              AND (
                  :searchText = '' OR instr(
                      lower(
                          catalog.title || CHAR(10) || catalog.problem_markdown || CHAR(10) ||
                          catalog.subject || CHAR(10) ||
                          COALESCE(catalog.chapter_labels, '') || CHAR(10) ||
                          COALESCE(catalog.knowledge_labels, '')
                      ),
                      lower(:searchText)
                  ) > 0
              )
            ORDER BY
                CASE :sort WHEN 'RECENTLY_CREATED' THEN catalog.created_at_epoch_millis END DESC,
                catalog.updated_at_epoch_millis DESC,
                catalog.entry_id ASC
            LIMIT :limit OFFSET :offset
        """.trimIndent()

        /** `LibraryQueryDao.count`（`LibraryQueryDao.kt:68-162`）的逐字副本（无 ORDER BY/LIMIT）。 */
        private val COUNT_SQL = """
            SELECT COUNT(*) FROM library_catalog AS catalog
            WHERE (:subjectId IS NULL OR catalog.subject = :subjectId)
              AND (
                  :sectionId IS NULL OR EXISTS (
                      SELECT 1 FROM problem_classification_binding AS classification
                      WHERE classification.problem_id = catalog.problem_id
                        AND classification.basis_revision_id = catalog.problem_revision_id
                        AND classification.dimension = 'CHAPTER'
                        AND classification.label_id = :sectionId
                  )
              )
              AND (:masteryId IS NULL OR catalog.mastery_id = :masteryId)
              AND (
                  :createdFromEpochMillis IS NULL OR
                      catalog.created_at_epoch_millis >= :createdFromEpochMillis
              )
              AND (
                  :createdToEpochMillis IS NULL OR
                      catalog.created_at_epoch_millis <= :createdToEpochMillis
              )
              AND (
                  :searchText = '' OR instr(
                      lower(
                          catalog.title || CHAR(10) || catalog.problem_markdown || CHAR(10) ||
                          catalog.subject || CHAR(10) ||
                          COALESCE(catalog.chapter_labels, '') || CHAR(10) ||
                          COALESCE(catalog.knowledge_labels, '')
                      ),
                      lower(:searchText)
                  ) > 0
              )
        """.trimIndent()

        /** `LibraryQueryDao.subjectFacets` 的逐字副本。 */
        private val SUBJECT_FACETS_SQL = """
            SELECT catalog.subject AS id, catalog.subject AS label, COUNT(*) AS count
            FROM library_catalog AS catalog
            WHERE (:sectionId IS NULL OR EXISTS (
                      SELECT 1 FROM problem_classification_binding AS classification
                      WHERE classification.problem_id = catalog.problem_id
                        AND classification.basis_revision_id = catalog.problem_revision_id
                        AND classification.dimension = 'CHAPTER'
                        AND classification.label_id = :sectionId
                  ))
              AND (:masteryId IS NULL OR catalog.mastery_id = :masteryId)
              AND (
                  :createdFromEpochMillis IS NULL OR
                      catalog.created_at_epoch_millis >= :createdFromEpochMillis
              )
              AND (
                  :createdToEpochMillis IS NULL OR
                      catalog.created_at_epoch_millis <= :createdToEpochMillis
              )
              AND (
                  :searchText = '' OR instr(
                      lower(
                          catalog.title || CHAR(10) || catalog.problem_markdown || CHAR(10) ||
                          catalog.subject || CHAR(10) ||
                          COALESCE(catalog.chapter_labels, '') || CHAR(10) ||
                          COALESCE(catalog.knowledge_labels, '')
                      ),
                      lower(:searchText)
                  ) > 0
              )
            GROUP BY catalog.subject
            ORDER BY COUNT(*) DESC, id ASC
        """.trimIndent()

        /** `LibraryQueryDao.sectionFacets` 的逐字副本。 */
        private val SECTION_FACETS_SQL = """
            SELECT classification.label_id AS id, classification.display_name AS label, COUNT(*) AS count
            FROM library_catalog AS catalog
            INNER JOIN problem_classification_binding AS classification
                ON classification.problem_id = catalog.problem_id
               AND classification.basis_revision_id = catalog.problem_revision_id
               AND classification.dimension = 'CHAPTER'
            WHERE (:subjectId IS NULL OR catalog.subject = :subjectId)
              AND (:masteryId IS NULL OR catalog.mastery_id = :masteryId)
              AND (
                  :createdFromEpochMillis IS NULL OR
                      catalog.created_at_epoch_millis >= :createdFromEpochMillis
              )
              AND (
                  :createdToEpochMillis IS NULL OR
                      catalog.created_at_epoch_millis <= :createdToEpochMillis
              )
              AND (
                  :searchText = '' OR instr(
                      lower(
                          catalog.title || CHAR(10) || catalog.problem_markdown || CHAR(10) ||
                          catalog.subject || CHAR(10) ||
                          COALESCE(catalog.chapter_labels, '') || CHAR(10) ||
                          COALESCE(catalog.knowledge_labels, '')
                      ),
                      lower(:searchText)
                  ) > 0
              )
            GROUP BY classification.label_id, classification.display_name
            ORDER BY COUNT(*) DESC, id ASC
        """.trimIndent()

        /** `LibraryQueryDao.masteryFacets` 的逐字副本。 */
        private val MASTERY_FACETS_SQL = """
            SELECT catalog.mastery_id AS id, catalog.mastery_id AS label, COUNT(*) AS count
            FROM library_catalog AS catalog
            WHERE (:subjectId IS NULL OR catalog.subject = :subjectId)
              AND (:sectionId IS NULL OR EXISTS (
                      SELECT 1 FROM problem_classification_binding AS classification
                      WHERE classification.problem_id = catalog.problem_id
                        AND classification.basis_revision_id = catalog.problem_revision_id
                        AND classification.dimension = 'CHAPTER'
                        AND classification.label_id = :sectionId
                  ))
              AND (
                  :createdFromEpochMillis IS NULL OR
                      catalog.created_at_epoch_millis >= :createdFromEpochMillis
              )
              AND (
                  :createdToEpochMillis IS NULL OR
                      catalog.created_at_epoch_millis <= :createdToEpochMillis
              )
              AND (
                  :searchText = '' OR instr(
                      lower(
                          catalog.title || CHAR(10) || catalog.problem_markdown || CHAR(10) ||
                          catalog.subject || CHAR(10) ||
                          COALESCE(catalog.chapter_labels, '') || CHAR(10) ||
                          COALESCE(catalog.knowledge_labels, '')
                      ),
                      lower(:searchText)
                  ) > 0
              )
            GROUP BY catalog.mastery_id
            ORDER BY COUNT(*) DESC, id ASC
        """.trimIndent()

        /**
         * 绑定顺序与 DAO 参数顺序逐位一致。
         *
         * 用 [_NO_FILTER] 而不是 null：平台 `rawQuery(sql, String[])` 的
         * `bindAllArgsAsStrings` 拒绝 null 元素。计划在 prepare 期就已确定（绑定发生在
         * 之后），筛选参数取什么值不影响 EXPLAIN 输出——占位符形态与生产一致。
         */
        private val PAGE_QUERIES: Map<String, Pair<String, Array<String?>>> = mapOf(
            "page-first-screen" to (
                PAGE_SQL to arrayOf("", _NO_FILTER, _NO_FILTER, _NO_FILTER, _NO_FILTER, _NO_FILTER, "RECENTLY_UPDATED", "20", "0")
                ),
            "page-section-filter" to (
                PAGE_SQL to arrayOf("", _NO_FILTER, SECTION_FILTER_ID, _NO_FILTER, _NO_FILTER, _NO_FILTER, "RECENTLY_UPDATED", "20", "0")
                ),
            "page-mastery-filter" to (
                PAGE_SQL to arrayOf("", _NO_FILTER, _NO_FILTER, MASTERY_FILTER_ID, _NO_FILTER, _NO_FILTER, "RECENTLY_UPDATED", "20", "0")
                ),
        )

        private val COUNT_QUERIES: Map<String, Pair<String, Array<String?>>> = mapOf(
            "count-blank" to (
                COUNT_SQL to arrayOf("", _NO_FILTER, _NO_FILTER, _NO_FILTER, _NO_FILTER, _NO_FILTER)
                ),
            "count-section-mastery-filter" to (
                COUNT_SQL to arrayOf("", _NO_FILTER, SECTION_FILTER_ID, MASTERY_FILTER_ID, _NO_FILTER, _NO_FILTER)
                ),
        )

        private val FACET_QUERIES: Map<String, Pair<String, Array<String?>>> = mapOf(
            "subject-facets" to (
                SUBJECT_FACETS_SQL to arrayOf("", _NO_FILTER, _NO_FILTER, _NO_FILTER, _NO_FILTER)
                ),
            "section-facets" to (
                SECTION_FACETS_SQL to arrayOf("", _NO_FILTER, _NO_FILTER, _NO_FILTER, _NO_FILTER)
                ),
            "mastery-facets" to (
                MASTERY_FACETS_SQL to arrayOf("", _NO_FILTER, _NO_FILTER, _NO_FILTER, _NO_FILTER)
                ),
        )
    }
}
