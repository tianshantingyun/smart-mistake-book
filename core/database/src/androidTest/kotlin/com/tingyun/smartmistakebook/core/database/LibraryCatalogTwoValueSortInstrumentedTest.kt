package com.tingyun.smartmistakebook.core.database

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.domain.LearningProjector
import com.tingyun.smartmistakebook.core.model.CalibrationSupport
import com.tingyun.smartmistakebook.core.model.KnowledgeMasteryState
import com.tingyun.smartmistakebook.core.model.LearnerSnapshot
import com.tingyun.smartmistakebook.core.model.LearnerSnapshotFreshness
import com.tingyun.smartmistakebook.core.model.MasteryStatus
import com.tingyun.smartmistakebook.core.model.ProblemMemoryState
import com.tingyun.smartmistakebook.core.model.ProjectionCheckpoint
import com.tingyun.smartmistakebook.core.model.ProjectionStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 阶段 4A 批 1 · L5 的两值排序契约（`docs/research/2026-10-03-stage4a-plan.md` §3 批 1）。
 *
 * 本文件是 `LibraryLeastMasteredSortInstrumentedTest` 的重做（旧文件随 `LEAST_MASTERED`
 * 退场删除）。旧断言逐条处置：
 * 1. `leastMasteredOrdersByNumericMasteryWeakestFirst` —— **退场**：`LEAST_MASTERED`
 *    排序被用户裁定删除（`docs/agent-first-refactor-decisions-2026-09-23.md:1066`），
 *    "最弱掌握度在前"的数值排序不再存在，没有等价物可转移。
 * 2. `leastMasteredOrdersByNumericMasteryOnTheFtsPathToo` —— **转移**为 FTS 路径的
 *    两值排序断言（`recentlyCreatedOrdersByCreationTimeOnTheFtsPathToo`）：旧例要钉的是
 *    "运行期自建 SQL 的第二份排序副本不得漂移"；两值排序同样有两份实现（`LibraryQueryDao`
 *    的 `@Query` 与 `RoomLibrarySearchStore` 的 raw SQL），FTS 侧仍需自己的行为断言。
 * 3. `leastMasteredStillAgreesWithTheMasteryFacetItSorts` —— 前半（排序与掌握度同源）
 *    **退场**：两值排序不再声明读掌握度；后半（facet 计数）**转移**为
 *    `masteryFacetStillCountsTheFixture`，保留"掌握程度筛选面照常工作"的覆盖。
 *
 * 新用例的夹具让**创建时间与更新时间各有一条不同的序**（entry-a 创建最早但更新最新），
 * 这样"排序没生效（退回 updated_at DESC）"或"两个值被当成同一个"都必然失败，
 * 而不是碰巧通过。
 */
@RunWith(AndroidJUnit4::class)
class LibraryCatalogTwoValueSortInstrumentedTest {
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
    fun recentlyUpdatedIsTheDefaultAndOrdersByUpdateTime() = runBlocking {
        store.seedStudyFacts(catalogSeed())
        store.commitProjection(projectionWithMastery())

        val byDefault = store.database.libraryQueryDao().page(
            searchText = "",
            subjectId = null,
            sectionId = null,
            masteryId = null,
            createdFromEpochMillis = null,
            createdToEpochMillis = null,
            sort = "RECENTLY_UPDATED",
            offset = 0,
            limit = 10,
        )
        val byUnknownValue = store.database.libraryQueryDao().page(
            searchText = "",
            subjectId = null,
            sectionId = null,
            masteryId = null,
            createdFromEpochMillis = null,
            createdToEpochMillis = null,
            sort = "",
            offset = 0,
            limit = 10,
        )

        // updated_at: entry-a 9000 > entry-c 3000 > entry-b 2000
        assertEquals(
            listOf("entry-a", "entry-c", "entry-b"),
            byDefault.map { it.entryId },
        )
        // 默认口径 = 非 RECENTLY_CREATED 的一切值都落到 updated_at DESC
        assertEquals(byDefault.map { it.entryId }, byUnknownValue.map { it.entryId })

        // 再加一条更新时刻最新的条目，证明"最近更新"真的在动
        store.seedStudyFacts(newestUpdatedEntrySeed())
        val afterNewest = store.database.libraryQueryDao().page(
            searchText = "",
            subjectId = null,
            sectionId = null,
            masteryId = null,
            createdFromEpochMillis = null,
            createdToEpochMillis = null,
            sort = "RECENTLY_UPDATED",
            offset = 0,
            limit = 10,
        )
        assertEquals("entry-newest-update", afterNewest.first().entryId)
    }

    @Test
    fun recentlyCreatedOrdersByCreationTime() = runBlocking {
        store.seedStudyFacts(catalogSeed())

        val ordered = store.database.libraryQueryDao().page(
            searchText = "",
            subjectId = null,
            sectionId = null,
            masteryId = null,
            createdFromEpochMillis = null,
            createdToEpochMillis = null,
            sort = "RECENTLY_CREATED",
            offset = 0,
            limit = 10,
        )

        // created_at: entry-c 3000 > entry-b 2000 > entry-a 1000。
        // 若 RECENTLY_CREATED 没生效（退回 updated_at DESC），顺序会是 a, c, b —— 必然失败。
        assertEquals(
            listOf("entry-c", "entry-b", "entry-a"),
            ordered.map { it.entryId },
        )
    }

    @Test
    fun recentlyCreatedOrdersByCreationTimeOnTheFtsPathToo() = runBlocking {
        // FTS 搜索路径在运行期自建 SQL、另带一份排序子句，所以它需要自己的行为断言。
        store.seedStudyFacts(catalogSeed())

        val ordered = store.librarySearchPage(
            matchQuery = CjkTextTokenizer.matchExpression(SEARCH_TERM),
            subjectId = null,
            sectionId = null,
            masteryId = null,
            createdFromEpochMillis = null,
            createdToEpochMillis = null,
            sort = "RECENTLY_CREATED",
            tokens = CjkTextTokenizer.tokens(SEARCH_TERM),
            offset = 0,
            limit = 10,
        )

        assertEquals(
            listOf("entry-c", "entry-b", "entry-a"),
            ordered.map { it.entryId },
        )
    }

    @Test
    fun masteryFacetStillCountsTheFixture() = runBlocking {
        // 旧第 3 例的 facet 覆盖转移到这里：排序不再与掌握度同源，但"掌握程度"筛选面
        // 必须照常给出正确计数（三行都是 LEARNING，facet 无法区分——这正是旧例的前提）。
        store.seedStudyFacts(catalogSeed())
        store.commitProjection(projectionWithMastery())

        val facets = store.database.libraryQueryDao().masteryFacets(
            searchText = "",
            subjectId = null,
            sectionId = null,
            createdFromEpochMillis = null,
            createdToEpochMillis = null,
        )

        assertEquals(listOf("learning"), facets.map { it.id })
        assertEquals(3, facets.sumOf { it.count })
    }

    /**
     * 创建时间升序（a→b→c），更新时间另有一条序（a 最新、b 最旧），
     * 于是两种排序的预期顺序不同；"排序没生效"必然读出 updated_at 的序。
     */
    private fun catalogSeed(): StudySeedBundle {
        val nodes = listOf(
            Triple("kc-a", "entry-a", 1_000L),
            Triple("kc-b", "entry-b", 2_000L),
            Triple("kc-c", "entry-c", 3_000L),
        )
        val updatedByEntry = mapOf(
            "entry-a" to 9_000L,
            "entry-b" to 2_000L,
            "entry-c" to 3_000L,
        )
        return StudySeedBundle(
            problems = nodes.map { (node, entry, stamp) ->
                ProblemSeedRecord("problem-$node", "fp-$node", "MATH", stamp)
            },
            revisions = nodes.map { (node, entry, stamp) ->
                ProblemRevisionSeedRecord(
                    revisionId = "revision-$node",
                    problemId = "problem-$node",
                    revisionNumber = 1,
                    title = "题 $entry",
                    problemMarkdown = "题面 $entry",
                    answerSpecId = null,
                    answerSpecSnapshot = null,
                    answerVerificationStatus = StudyDbValue.VerificationStatus.UNKNOWN,
                    sourceType = "CAPTURE_CONFIRMED",
                    sourceReference = null,
                    contentFingerprint = "f".repeat(64),
                    createdAtEpochMillis = stamp,
                )
            },
            practiceUnits = nodes.map { (node, entry, stamp) ->
                PracticeUnitSeedRecord(
                    practiceUnitId = "unit-$node",
                    problemId = "problem-$node",
                    problemRevisionId = "revision-$node",
                    unitKey = "whole-problem",
                    unitKind = "WHOLE_PROBLEM",
                    title = "题 $entry",
                    promptMarkdown = "题面 $entry",
                    estimatedSeconds = 180,
                    createdAtEpochMillis = stamp,
                )
            },
            errorBookEntries = nodes.map { (node, entry, stamp) ->
                ErrorBookEntrySeedRecord(
                    entryId = entry,
                    practiceUnitId = "unit-$node",
                    problemId = "problem-$node",
                    currentRevisionId = "revision-$node",
                    sourceKey = null,
                    acceptedAtEpochMillis = stamp,
                    updatedAtEpochMillis = updatedByEntry.getValue(entry),
                )
            },
            knowledgeNodes = nodes.map { (node, _, stamp) ->
                KnowledgeNodeSeedRecord(
                    knowledgeNodeId = node,
                    stableCode = "math.test.$node",
                    subject = "MATH",
                    displayName = node,
                    parentKnowledgeNodeId = null,
                    taxonomyVersion = TAXONOMY_VERSION,
                    createdAtEpochMillis = stamp,
                )
            },
            knowledgeBindings = nodes.map { (node, _, stamp) ->
                KnowledgeBindingSeedRecord(
                    bindingId = "binding-$node",
                    practiceUnitId = "unit-$node",
                    knowledgeNodeId = node,
                    basisRevisionId = "revision-$node",
                    strength = 1.0,
                    sourceType = "VERIFIED",
                    taxonomyVersion = TAXONOMY_VERSION,
                    acceptedAtEpochMillis = stamp,
                )
            },
        )
    }

    /** Fourth entry whose `updated_at` exceeds all three seeds, without touching mastery. */
    private fun newestUpdatedEntrySeed() = StudySeedBundle(
        problems = listOf(
            ProblemSeedRecord("problem-newest", "fp-newest", "MATH", 500L),
        ),
        revisions = listOf(
            ProblemRevisionSeedRecord(
                revisionId = "revision-newest",
                problemId = "problem-newest",
                revisionNumber = 1,
                title = "最新更新题",
                problemMarkdown = "题面 最新更新题",
                answerSpecId = null,
                answerSpecSnapshot = null,
                answerVerificationStatus = StudyDbValue.VerificationStatus.UNKNOWN,
                sourceType = "CAPTURE_CONFIRMED",
                sourceReference = null,
                contentFingerprint = "a".repeat(64),
                createdAtEpochMillis = 500,
            ),
        ),
        practiceUnits = listOf(
            PracticeUnitSeedRecord(
                practiceUnitId = "unit-newest",
                problemId = "problem-newest",
                problemRevisionId = "revision-newest",
                unitKey = "whole-problem",
                unitKind = "WHOLE_PROBLEM",
                title = "最新更新题",
                promptMarkdown = "题面 最新更新题",
                estimatedSeconds = 180,
                createdAtEpochMillis = 500,
            ),
        ),
        errorBookEntries = listOf(
            ErrorBookEntrySeedRecord(
                entryId = "entry-newest-update",
                practiceUnitId = "unit-newest",
                problemId = "problem-newest",
                currentRevisionId = "revision-newest",
                sourceKey = null,
                acceptedAtEpochMillis = 500,
                updatedAtEpochMillis = 10_000,
            ),
        ),
    )

    /**
     * Mastery rows come from a real projection commit, not `seedFixture`: the
     * fixture writes the legacy `knowledge_mastery_state` table, while the
     * catalog reads `learner_knowledge_mastery_state`. A memory row is required
     * too — the view resolves the learner from `learner_problem_memory_state`.
     */
    private fun projectionWithMastery() = ProjectionCommit(
        projectionName = PROJECTION,
        learnerId = LEARNER,
        expectedPreviousCheckpoint = 0,
        expectedPreviousStateVersion = 0,
        mode = ProjectionCommitMode.FULL_REPLAY,
        knownLedgerHeadSequence = 0,
        consumedLedgerEvents = emptyList(),
        presentationProjectionStates = emptyMap(),
        expectedProjectorVersion = PROJECTOR_VERSION,
        snapshot = LearnerSnapshot(
            learnerId = LEARNER,
            problemMemoryStates = MASTERY.keys.associate { node ->
                "unit-$node" to ProblemMemoryState(
                    practiceUnitId = "unit-$node",
                    stabilityDays = 3.0,
                    difficulty = 5.0,
                    lastReviewedAtEpochMillis = 1_000,
                    nextReviewAtEpochMillis = 2_000,
                    projectorVersion = PROJECTOR_VERSION,
                    checkpointSequence = 0,
                )
            },
            knowledgeMasteryStates = MASTERY.mapValues { (node, lowerBound) ->
                KnowledgeMasteryState(
                    knowledgeNodeId = node,
                    masteryScore = (lowerBound + 0.1).coerceAtMost(1.0),
                    conservativeMasteryScore = lowerBound,
                    evidenceMass = 1.0,
                    status = MasteryStatus.LEARNING,
                    calibrationSupport = CalibrationSupport.SUPPORTED,
                    projectorVersion = PROJECTOR_VERSION,
                    checkpointSequence = 0,
                )
            },
            checkpoint = ProjectionCheckpoint(
                lastSequence = 0,
                projectorVersion = PROJECTOR_VERSION,
                projectedAtEpochMillis = 1_000,
            ),
            generatedAtEpochMillis = 1_000,
            freshness = LearnerSnapshotFreshness.CURRENT,
            projectionStatus = ProjectionStatus.CURRENT,
        ),
    )

    private companion object {
        const val LEARNER = "learner-sort-test"
        const val PROJECTION = "study-experience-v1"
        const val PROJECTOR_VERSION = LearningProjector.VERSION
        const val TAXONOMY_VERSION = "taxonomy-sort-v1"

        /** Present in every seeded problem's markdown, so the FTS path returns all three rows. */
        const val SEARCH_TERM = "题面"

        /** Conservative mastery per knowledge node; deliberately distinct. */
        val MASTERY = linkedMapOf(
            "kc-a" to 0.20,
            "kc-b" to 0.50,
            "kc-c" to 0.80,
        )
    }
}
