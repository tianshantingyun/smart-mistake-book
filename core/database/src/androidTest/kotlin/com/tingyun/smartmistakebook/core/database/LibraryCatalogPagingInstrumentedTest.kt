package com.tingyun.smartmistakebook.core.database

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryCatalogPagingInstrumentedTest {
    private lateinit var store: RoomStudyDatabase

    @Before
    fun setUp() {
        store = StudyDatabaseFactory.openInMemory(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        store.close()
    }

    @Test
    fun fiftyThousandRowsPageSearchAndFacetCountsInSql() = runBlocking {
        val count = 50_000
        // 阶段 3C 后半批 2（S18）：夹具由「只有 problem/revision/unit/entry」补齐为
        // 「mastery / 分类 / 投影」齐全的目录形态（见 LibraryCatalogScaleFixture）——
        // 旧夹具下 library_catalog 的三条相关子查询走空连接，mastery/标签全是退化值，
        // 本用例的 count/page/facets 因此只压到退化路径。补齐后同样的断言在真实子查询
        // 形态上重跑，并新增非退化断言（标签/掌握桶/筛选命中）。
        store.database.seedLibraryCatalogScale(count)
        val dao = store.database.libraryQueryDao()

        assertEquals(count, dao.count("", null, null, null, null, null))

        val lastPage = dao.page(
            searchText = "",
            subjectId = null,
            sectionId = null,
            masteryId = null,
            createdFromEpochMillis = null,
            createdToEpochMillis = null,
            sort = "RECENTLY_CREATED",
            offset = count - 10,
            limit = 10,
        )
        assertEquals(10, lastPage.size)
        assertTrue(lastPage.first().entryId == "entry-9")

        val searchHits = dao.page(
            searchText = "独特检索词",
            subjectId = null,
            sectionId = null,
            masteryId = null,
            createdFromEpochMillis = null,
            createdToEpochMillis = null,
            sort = "RECENTLY_CREATED",
            offset = 0,
            limit = 1_000,
        )
        assertEquals(count / 100, searchHits.size)
        assertTrue(searchHits.all { it.problemMarkdown.startsWith("独特检索词") })

        val subjectFacets = dao.subjectFacets(
            searchText = "",
            sectionId = null,
            masteryId = null,
            createdFromEpochMillis = null,
            createdToEpochMillis = null,
        )
        assertEquals(setOf("MATH", "PHYSICS"), subjectFacets.map { it.id }.toSet())
        assertEquals(count, subjectFacets.sumOf { it.count })

        // ---- 非退化断言（旧夹具下这些全会是 unknown / 空）----
        // ① 分类标签：每行都有章节 + 知识点标签，facet 桶数与夹具桶数一致。
        assertTrue(
            "夹具退化：首屏仍有空 chapter_labels",
            dao.page(
                searchText = "",
                subjectId = null,
                sectionId = null,
                masteryId = null,
                createdFromEpochMillis = null,
                createdToEpochMillis = null,
                sort = "RECENTLY_UPDATED",
                offset = 0,
                limit = 20,
            ).all { !it.chapterLabels.isNullOrEmpty() && !it.knowledgeLabels.isNullOrEmpty() },
        )
        val sectionFacets = dao.sectionFacets(
            searchText = "",
            subjectId = null,
            masteryId = null,
            createdFromEpochMillis = null,
            createdToEpochMillis = null,
        )
        assertEquals(LibraryCatalogScale.CHAPTER_COUNT, sectionFacets.size)
        assertEquals(count, sectionFacets.sumOf { it.count })

        // ② 掌握态四桶：mastery facet 不再只有 'unknown'。
        val masteryFacets = dao.masteryFacets(
            searchText = "",
            subjectId = null,
            sectionId = null,
            createdFromEpochMillis = null,
            createdToEpochMillis = null,
        )
        assertEquals(
            setOf("learning", "mastered", "stale", "conflicted"),
            masteryFacets.map { it.id }.toSet(),
        )
        assertEquals(count, masteryFacets.sumOf { it.count })

        // ③ 非空筛选命中：section / mastery 过滤真的收窄结果（旧夹具下前者 0 行、
        //    后者 0 行——过滤"命中"为空正是退化路径的信号）。
        assertEquals(
            count / LibraryCatalogScale.CHAPTER_COUNT,
            dao.count("", null, "chapter-3", null, null, null),
        )
        assertEquals(
            count / 4,
            dao.count("", null, null, "mastered", null, null),
        )
    }

    /**
     * 阶段 4A 批 1 · L5：「录入时间段」= 按条目创建时间（`accepted_at`）闭区间过滤，
     * 目录路径与 FTS 路径、计数、facets 必须同一口径。
     */
    @Test
    fun createdRangeFilterNarrowsCatalogFtsCountsAndFacets() = runBlocking {
        store.seedStudyFacts(
            StudySeedBundle(
                problems = listOf(
                    ProblemSeedRecord("problem-early", "fp-early", "MATH", 1_000),
                    ProblemSeedRecord("problem-mid", "fp-mid", "PHYSICS", 2_000),
                    ProblemSeedRecord("problem-late", "fp-late", "MATH", 3_000),
                ),
                revisions = listOf(
                    revision("revision-early", "problem-early", "早期题"),
                    revision("revision-mid", "problem-mid", "中期题"),
                    revision("revision-late", "problem-late", "晚期题"),
                ),
                practiceUnits = listOf(
                    unit("practice-early", "problem-early", "revision-early", "早期题"),
                    unit("practice-mid", "problem-mid", "revision-mid", "中期题"),
                    unit("practice-late", "problem-late", "revision-late", "晚期题"),
                ),
                errorBookEntries = listOf(
                    entry("entry-early", "practice-early", "problem-early", "revision-early", 1_000),
                    entry("entry-mid", "practice-mid", "problem-mid", "revision-mid", 2_000),
                    entry("entry-late", "practice-late", "problem-late", "revision-late", 3_000),
                ),
            ),
        )
        val dao = store.database.libraryQueryDao()

        val windowed = dao.page(
            searchText = "",
            subjectId = null,
            sectionId = null,
            masteryId = null,
            createdFromEpochMillis = 1_500,
            createdToEpochMillis = 2_500,
            sort = "RECENTLY_UPDATED",
            offset = 0,
            limit = 10,
        )
        assertEquals(listOf("entry-mid"), windowed.map { it.entryId })
        assertEquals(
            1,
            dao.count(
                searchText = "",
                subjectId = null,
                sectionId = null,
                masteryId = null,
                createdFromEpochMillis = 1_500,
                createdToEpochMillis = 2_500,
            ),
        )
        assertEquals(
            listOf("PHYSICS"),
            dao.subjectFacets(
                searchText = "",
                sectionId = null,
                masteryId = null,
                createdFromEpochMillis = 1_500,
                createdToEpochMillis = 2_500,
            ).map { it.id },
        )

        // FTS 路径（同一条 MATCH + 同一时间窗）必须同口径
        val ftsWindowed = store.librarySearchPage(
            matchQuery = CjkTextTokenizer.matchExpression("题"),
            subjectId = null,
            sectionId = null,
            masteryId = null,
            createdFromEpochMillis = 1_500,
            createdToEpochMillis = 2_500,
            sort = "RECENTLY_UPDATED",
            tokens = CjkTextTokenizer.tokens("题"),
            offset = 0,
            limit = 10,
        )
        assertEquals(listOf("entry-mid"), ftsWindowed.map { it.entryId })
        assertEquals(
            1,
            store.librarySearchCount(
                matchQuery = CjkTextTokenizer.matchExpression("题"),
                subjectId = null,
                sectionId = null,
                masteryId = null,
                createdFromEpochMillis = 1_500,
                createdToEpochMillis = 2_500,
            ),
        )
    }

    private fun revision(revisionId: String, problemId: String, title: String) =
        ProblemRevisionSeedRecord(
            revisionId = revisionId,
            problemId = problemId,
            revisionNumber = 1,
            title = title,
            problemMarkdown = "$title 的题面",
            questionDocumentSnapshot = null,
            answerSpecId = null,
            answerSpecSnapshot = null,
            answerVerificationStatus = StudyDbValue.VerificationStatus.UNKNOWN,
            sourceType = "CAPTURE_CONFIRMED",
            sourceReference = null,
            contentFingerprint = "f".repeat(64),
            createdAtEpochMillis = 1_000,
        )

    private fun unit(unitId: String, problemId: String, revisionId: String, title: String) =
        PracticeUnitSeedRecord(
            practiceUnitId = unitId,
            problemId = problemId,
            problemRevisionId = revisionId,
            unitKey = "whole-problem",
            unitKind = "WHOLE_PROBLEM",
            title = title,
            promptMarkdown = "$title 的题面",
            estimatedSeconds = 180,
            createdAtEpochMillis = 1_000,
        )

    private fun entry(
        entryId: String,
        unitId: String,
        problemId: String,
        revisionId: String,
        acceptedAt: Long,
    ) = ErrorBookEntrySeedRecord(
        entryId = entryId,
        practiceUnitId = unitId,
        problemId = problemId,
        currentRevisionId = revisionId,
        sourceKey = null,
        acceptedAtEpochMillis = acceptedAt,
        updatedAtEpochMillis = acceptedAt,
    )
}
