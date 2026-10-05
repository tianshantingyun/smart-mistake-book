package com.tingyun.smartmistakebook.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room3.withWriteTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.system.measureTimeMillis

/**
 * **S9 聚焦 `MASTERY_READ` 负载门**（`docs/research/2026-10-05-stage3c-part2-plan.md` §3 批 2）。
 *
 * 审计 S9 的对象是 `MasteryOverviewDao.readIndependentErrorAggregates`
 * （`MasteryOverviewDao.kt:109-127`）：`attempt_event × assessment_evidence_attribution`
 * 的 JOIN + `COUNT(DISTINCT attempt_id)`，口径（一题一次，不因多 attribution 重复计数）
 * 被 KDoc 与 [MasteryOverviewInstrumentedTest] 钉住——**本门不改这个口径**。
 * 只有**带词聚焦**的 `MASTERY_READ` 会走到这条聚合（`RoomTutorToolRunner.kt:635-638`；
 * 无词的清单读只调 `readSubjectMastery`）。
 *
 * 本类量化两件事，**不做物化/索引决策**（只在报告给出"实测超预算"原始数字后才触发）：
 * 1. **夹具**：2,000 条负向作答 × attribution（另 2,000 条正向作答作方向过滤的噪声），
 *    200 个知识点（每点 10 负 10 正）；**每次作答带两条 attribution**（主 + 相邻知识点，
 *    join 行数 = 作答数 × 2）。注：`attribution → snapshot` 的复合外键要求
 *    practice_unit/revision/taxonomy 与 snapshot 逐位一致，而 binding 的唯一索引禁止
 *    同 (unit, node, revision, taxonomy) 双行——"同 snapshot 同节点双 attribution"在当前
 *    FK 体系下不可表达；`COUNT(DISTINCT attempt_id)` 的防御语义由
 *    `MasteryOverviewInstrumentedTest.aNegativeAttemptCountsOnceThroughItsAttribution` 钉住。
 * 2. **门**：① EQP 结构断言（两侧都必须走索引、不得整表扫 `attempt_event` /
 *    `assessment_evidence_attribution`）；② p95 计时 backstop（预热 3 / 样本 24 /
 *    最近秩 p95 / `ciSlowRunner` ×4，口径逐字照
 *    `KnowledgeContextRetrievalInstrumentedTest`）；③ 附带 `(learner_id,
 *    evidence_direction)` 候选索引的 **EXPLAIN 前后对比**（本测试打印原文；是否加进
 *    schema 由量化报告决定——加索引是 schema 变更，必须"有据"）。
 *
 * 预算锚点：`MASTERY_READ_P95_BUDGET_MILLIS = 250ms`——与知识检索腿
 * （`KnowledgeContextRetrievalInstrumentedTest.kt:537`）的同一工具读面预算同值同口径。
 */
@RunWith(AndroidJUnit4::class)
class MasteryReadAggregateScaleInstrumentedTest {

    private lateinit var context: Context
    private lateinit var store: RoomStudyDatabase
    private val databaseName = "s9-mastery-read-scale-${System.nanoTime()}.db"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        store = StudyDatabaseFactory.open(context, databaseName) as RoomStudyDatabase
    }

    @After
    fun tearDown() {
        store.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun focusedMasteryReadPlanAndLatency() = runBlocking {
        insertFixture(store)

        // 借 Room 把 schema 落盘，再让平台连接打开**同一文件**读 EXPLAIN
        // （room3 prepared-statement 的 step 路径对 EXPLAIN 会抛异常；先 SELECT 1
        // 确保文件/表已建）。
        store.database.withRawConnection(isReadOnly = true) { connection ->
            connection.usePrepared("SELECT 1") { statement -> statement.step() }
        }

        val focusNodeIds = (0 until FOCUS_NODE_COUNT).map(::nodeId)
        val plan = readQueryPlan(context, databaseName, focusNodeIds)
        println(
            "S9 readIndependentErrorAggregates EXPLAIN QUERY PLAN " +
                "(${NEGATIVE_ATTEMPT_COUNT} negative + ${POSITIVE_ATTEMPT_COUNT} positive attempts, " +
                "${FOCUS_NODE_COUNT}-node focus):\n" + plan.joinToString("\n"),
        )

        // ① 结构断言：两侧都必须走索引（SEARCH ... USING ...）。
        assertTrue(
            "S9 计划里 attempt_event 未走索引：\n${plan.joinToString("\n")}",
            plan.any { it.startsWith("SEARCH attempt ") && "USING" in it },
        )
        assertTrue(
            "S9 计划里 attribution 未走索引：\n${plan.joinToString("\n")}",
            plan.any { it.startsWith("SEARCH attribution ") && "USING" in it },
        )
        // ② 结构断言：不得整表扫两张表（`SCAN attempt_event` / `SCAN TABLE attempt_event`）。
        assertFalse(
            "S9 计划对 attempt_event 全表扫描：\n${plan.joinToString("\n")}",
            plan.any { ATTEMPT_TABLE_SCAN.containsMatchIn(it) },
        )
        assertFalse(
            "S9 计划对 assessment_evidence_attribution 全表扫描：\n${plan.joinToString("\n")}",
            plan.any { ATTRIBUTION_TABLE_SCAN.containsMatchIn(it) },
        )

        // ③ 计时 backstop + 非空信号：聚焦读 = 清单读 + 聚合读（工具实际做的两跳）。
        var errorSum = -1
        repeat(WARMUP_COUNT) { errorSum = focusedRead(focusNodeIds) }
        assertTrue("夹具脱节：聚焦聚合没有任何独立错误计数", errorSum > 0)
        assertEquals(
            "夹具脱节：24 个聚焦点 × 每点 $ERRORS_PER_NODE 次独立错误" +
                "（10 次主 attribution + 10 次来自相邻单元的次级 attribution）",
            FOCUS_NODE_COUNT * ERRORS_PER_NODE,
            errorSum,
        )

        val samples = mutableListOf<Long>()
        repeat(SAMPLE_COUNT) {
            samples += measureTimeMillis { errorSum = focusedRead(focusNodeIds) }
        }
        val p95 = samples.percentile95()
        println(
            "S9 focused MASTERY_READ benchmark: samples=$samples p95=${p95}ms " +
                "budget=${FOCUSED_READ_P95_BUDGET_MS}ms",
        )
        assertTrue(
            "聚焦 MASTERY_READ p95=${p95}ms 超过 backstop ${FOCUSED_READ_P95_BUDGET_MS}ms；" +
                "samples=$samples",
            p95 < FOCUSED_READ_P95_BUDGET_MS,
        )

        // ④ 候选索引 EXPLAIN 前后对比（只打印证据，不改生产 schema）。
        val indexProbe = probeCandidateIndex(focusNodeIds)
        println(
            "S9 candidate index $CANDIDATE_INDEX_NAME probe:\n" +
                "  before:\n${indexProbe.before.joinToString("\n") { "    $it" }}\n" +
                "  after:\n${indexProbe.after.joinToString("\n") { "    $it" }}\n" +
                "  aggregate query (n=$PROBE_SAMPLE_COUNT each): " +
                "before=${indexProbe.beforeSamples} p95=${indexProbe.beforeSamples.percentile95()}ms | " +
                "after=${indexProbe.afterSamples} p95=${indexProbe.afterSamples.percentile95()}ms",
        )
    }

    private suspend fun focusedRead(focusNodeIds: List<String>): Int {
        val masteryRows = store.readSubjectMastery(LEARNER_ID, SUBJECT)
        assertEquals(KNOWLEDGE_NODE_COUNT, masteryRows.size)
        val focus = masteryRows
            .map { it.knowledgeNodeId }
            .filter { it in focusNodeIds }
        assertEquals(FOCUS_NODE_COUNT, focus.size)
        return store.readMasteryAggregates(LEARNER_ID, focus.toSet())
            .sumOf { it.independentErrorCount }
    }

    private data class IndexProbe(
        val before: List<String>,
        val after: List<String>,
        val beforeSamples: List<Long>,
        val afterSamples: List<Long>,
    )

    /**
     * 候选索引的前后对比：先在测试库里**删掉**该索引（当前 schema 本就没有；若将来
     * 快照里有，则这次删除就是"模拟 v62 旧 schema"），读计划并采样；再建回来读计划
     * 并采样。全部作用于本测试自己的临时库文件，不触生产 schema。计时只作证据，
     * 不作门（门的结论是"是否超预算"）。
     */
    private suspend fun probeCandidateIndex(focusNodeIds: List<String>): IndexProbe {
        val path = context.getDatabasePath(databaseName).absolutePath
        val before = SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL("DROP INDEX IF EXISTS $CANDIDATE_INDEX_NAME")
            db.readPlan(focusNodeIds)
        }
        repeat(PROBE_WARMUP_COUNT) { store.readMasteryAggregates(LEARNER_ID, focusNodeIds.toSet()) }
        val beforeSamples = List(PROBE_SAMPLE_COUNT) {
            measureTimeMillis { store.readMasteryAggregates(LEARNER_ID, focusNodeIds.toSet()) }
        }
        val after = SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS $CANDIDATE_INDEX_NAME " +
                    "ON attempt_event(learner_id, evidence_direction)",
            )
            db.readPlan(focusNodeIds)
        }
        repeat(PROBE_WARMUP_COUNT) { store.readMasteryAggregates(LEARNER_ID, focusNodeIds.toSet()) }
        val afterSamples = List(PROBE_SAMPLE_COUNT) {
            measureTimeMillis { store.readMasteryAggregates(LEARNER_ID, focusNodeIds.toSet()) }
        }
        return IndexProbe(before, after, beforeSamples, afterSamples)
    }

    private fun SQLiteDatabase.readPlan(nodeIds: List<String>): List<String> {
        val placeholders = nodeIds.joinToString(",") { "?" }
        val args = arrayOf(LEARNER_ID, *nodeIds.toTypedArray())
        return rawQuery(
            "EXPLAIN QUERY PLAN ${INDEPENDENT_ERROR_AGGREGATES_SQL.replace(IN_PLACEHOLDER, placeholders)}",
            args,
        ).use { cursor ->
            val detail = cursor.getColumnIndexOrThrow("detail")
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(detail))
            }
        }
    }

    private fun readQueryPlan(
        context: Context,
        databaseName: String,
        nodeIds: List<String>,
    ): List<String> = SQLiteDatabase.openDatabase(
        context.getDatabasePath(databaseName).absolutePath,
        null,
        SQLiteDatabase.OPEN_READONLY,
    ).use { database -> database.readPlan(nodeIds) }

    /**
     * 夹具（一个写事务）：200 题/单元/知识点/绑定/条目 + 1 条投影头 + 200 行掌握态 +
     * 200 行题卡记忆态 + 2,000 负向/2,000 正向作答（各带 submission/snapshot/attribution）。
     *
     * 直写行（op 表）而不是走 `recordAttempt` 生产写路径：本门测的是**读路径**的规模成本，
     * 夹具不是被测对象；同 [ProblemDaoActiveMistakesPerformanceInstrumentedTest] 的
     * `insertFixture` 先例。
     */
    private suspend fun insertFixture(store: RoomStudyDatabase) {
        val problems = List(KNOWLEDGE_NODE_COUNT) { index ->
            ProblemSeedRecord(
                problemId = "problem-$index",
                canonicalFingerprint = index.toString(16).padStart(64, '0'),
                subject = SUBJECT,
                createdAtEpochMillis = index + 1L,
            )
        }
        val revisions = List(KNOWLEDGE_NODE_COUNT) { index ->
            ProblemRevisionSeedRecord(
                revisionId = "revision-$index",
                problemId = "problem-$index",
                revisionNumber = 1,
                title = "S9 题 ${index + 1}",
                problemMarkdown = "S9 夹具题面 $index",
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
        val units = List(KNOWLEDGE_NODE_COUNT) { index ->
            PracticeUnitSeedRecord(
                practiceUnitId = "unit-$index",
                problemId = "problem-$index",
                problemRevisionId = "revision-$index",
                unitKey = "whole-problem",
                unitKind = "WHOLE_PROBLEM",
                title = "S9 题 ${index + 1}",
                promptMarkdown = "S9 夹具题面",
                estimatedSeconds = 180,
                createdAtEpochMillis = index + 1L,
            )
        }
        val entries = List(KNOWLEDGE_NODE_COUNT) { index ->
            ErrorBookEntrySeedRecord(
                entryId = "entry-$index",
                practiceUnitId = "unit-$index",
                problemId = "problem-$index",
                currentRevisionId = "revision-$index",
                sourceKey = null,
                acceptedAtEpochMillis = index + 1L,
                updatedAtEpochMillis = index + 1L,
            )
        }
        val nodes = List(KNOWLEDGE_NODE_COUNT) { index ->
            KnowledgeNodeSeedRecord(
                knowledgeNodeId = nodeId(index),
                stableCode = "s9.kc.$index",
                subject = SUBJECT,
                displayName = "知识点$index",
                parentKnowledgeNodeId = null,
                taxonomyVersion = TAXONOMY_VERSION,
                createdAtEpochMillis = 1L,
                granularity = "ATOMIC",
            )
        }
        val bindings = List(KNOWLEDGE_NODE_COUNT) { index ->
            KnowledgeBindingSeedRecord(
                bindingId = "binding-$index",
                practiceUnitId = "unit-$index",
                knowledgeNodeId = nodeId(index),
                basisRevisionId = "revision-$index",
                strength = 1.0,
                sourceType = "VERIFIED",
                taxonomyVersion = TAXONOMY_VERSION,
                acceptedAtEpochMillis = index + 1L,
            )
        }
        // 第二套 binding：每个单元再挂一个相邻知识点的绑定（同 taxonomy），让每次作答
        // 带两条 attribution（多知识点归属的常见形态，join 行数 = 作答数 × 2）。
        val secondaryBindings = List(KNOWLEDGE_NODE_COUNT) { index ->
            KnowledgeBindingSeedRecord(
                bindingId = "binding-secondary-$index",
                practiceUnitId = "unit-$index",
                knowledgeNodeId = nodeId((index + 1) % KNOWLEDGE_NODE_COUNT),
                basisRevisionId = "revision-$index",
                strength = 0.5,
                sourceType = "VERIFIED",
                taxonomyVersion = TAXONOMY_VERSION,
                acceptedAtEpochMillis = index + 1L,
            )
        }

        store.database.withWriteTransaction {
            val problemDao = store.database.problemDao()
            problemDao.insertProblems(problems.map { it.toEntity() })
            problemDao.insertRevisions(revisions.map { it.toEntity() })
            problemDao.insertPracticeUnits(units.map { it.toEntity() })
            problemDao.insertErrorBookEntries(entries.map { it.toEntity() })
            problemDao.insertKnowledgeNodes(nodes.map { it.toEntity() })
            problemDao.insertKnowledgeBindings(bindings.map { it.toEntity() })
            problemDao.insertKnowledgeBindings(secondaryBindings.map { it.toEntity() })

            usePrepared(INSERT_PROJECTION_HEADER) { statement ->
                statement.bindText(1, PROJECTION_NAME)
                statement.bindText(2, LEARNER_ID)
                statement.bindLong(3, 0)
                statement.bindLong(4, 0)
                statement.bindLong(5, 0)
                statement.bindText(6, PROJECTOR_VERSION)
                statement.bindLong(7, FIXTURE_EPOCH_MILLIS)
                statement.bindLong(8, FIXTURE_EPOCH_MILLIS)
                statement.bindNull(9)
                statement.bindText(10, "CURRENT")
                statement.bindText(11, "CURRENT")
                statement.step()
            }
            usePrepared(INSERT_MEMORY_STATE) { statement ->
                for (index in 0 until KNOWLEDGE_NODE_COUNT) {
                    statement.bindText(1, PROJECTION_NAME)
                    statement.bindText(2, LEARNER_ID)
                    statement.bindText(3, "unit-$index")
                    statement.bindDouble(4, 3.0)
                    statement.bindDouble(5, 5.0)
                    statement.bindLong(6, FIXTURE_EPOCH_MILLIS)
                    statement.bindLong(7, FIXTURE_EPOCH_MILLIS + DAY_MILLIS)
                    statement.bindLong(8, 0)
                    statement.bindLong(9, 0)
                    statement.bindLong(10, 0)
                    statement.bindLong(11, 0)
                    statement.bindNull(12)
                    statement.bindLong(13, 0)
                    statement.bindNull(14)
                    statement.bindText(15, PROJECTOR_VERSION)
                    statement.bindLong(16, 0)
                    statement.bindNull(17)
                    statement.bindNull(18)
                    statement.bindLong(19, 0)
                    statement.bindLong(20, 0)
                    statement.step()
                    statement.reset()
                }
            }
            usePrepared(INSERT_MASTERY_STATE) { statement ->
                for (index in 0 until KNOWLEDGE_NODE_COUNT) {
                    statement.bindText(1, PROJECTION_NAME)
                    statement.bindText(2, LEARNER_ID)
                    statement.bindText(3, nodeId(index))
                    statement.bindDouble(4, (index % 100) / 100.0)
                    statement.bindDouble(5, (index % 100) / 100.0)
                    statement.bindDouble(6, 1.0)
                    statement.bindNull(7)
                    statement.bindNull(8)
                    statement.bindText(9, if (index % 2 == 0) "LEARNING" else "MASTERED")
                    statement.bindText(10, "UNKNOWN")
                    statement.bindText(11, PROJECTOR_VERSION)
                    statement.bindLong(12, 0)
                    statement.bindLong(13, FIXTURE_EPOCH_MILLIS)
                    statement.bindNull(14)
                    statement.bindText(15, "INDEPENDENT_INCORRECT")
                    statement.bindText(16, "NEGATIVE")
                    statement.bindDouble(17, 1.0)
                    statement.bindDouble(18, 1.0)
                    statement.bindNull(19)
                    statement.bindNull(20)
                    statement.bindNull(21)
                    statement.bindNull(22)
                    statement.step()
                    statement.reset()
                }
            }
            usePrepared(INSERT_SNAPSHOT) { statement ->
                for (index in 0 until TOTAL_ATTEMPT_COUNT) {
                    val unitIndex = index % KNOWLEDGE_NODE_COUNT
                    statement.bindText(1, snapshotId(index))
                    statement.bindText(2, "assessment-item-$index")
                    statement.bindText(3, "unit-$unitIndex")
                    statement.bindText(4, "revision-$unitIndex")
                    statement.bindText(5, "answer-spec-$unitIndex")
                    statement.bindText(6, "family-$unitIndex")
                    statement.bindNull(7)
                    statement.bindText(8, TAXONOMY_VERSION)
                    statement.bindText(9, "VERIFIED")
                    statement.bindText(10, "UNKNOWN")
                    statement.bindText(11, "unknown-calibration-source")
                    statement.bindText(12, "unknown-calibration-v0")
                    statement.bindLong(13, 0)
                    statement.bindLong(14, 1)
                    statement.bindLong(15, index + 1L)
                    statement.step()
                    statement.reset()
                }
            }
            usePrepared(INSERT_ATTRIBUTION) { statement ->
                for (index in 0 until TOTAL_ATTEMPT_COUNT) {
                    val unitIndex = index % KNOWLEDGE_NODE_COUNT
                    statement.bindText(1, snapshotId(index))
                    statement.bindText(2, "binding-$unitIndex")
                    statement.bindText(3, "unit-$unitIndex")
                    statement.bindText(4, nodeId(unitIndex))
                    statement.bindDouble(5, 1.0)
                    statement.bindText(6, "revision-$unitIndex")
                    statement.bindText(7, TAXONOMY_VERSION)
                    statement.bindText(8, "PRIMARY")
                    statement.bindText(9, "DIRECT")
                    statement.step()
                    statement.reset()
                    // 同一 snapshot 的第二条 attribution：相邻知识点（多 KC 归属的常态）。
                    // 两条 attribution 不能指向同一节点——attribution→snapshot 的复合
                    // 外键要求 practice_unit/revision/taxonomy 与 snapshot 逐位一致，
                    // 而 binding 的唯一索引禁止同 (unit, node, revision, taxonomy) 双行，
                    // 所以"同 snapshot 同节点双 attribution"在当前 FK 体系下不可表达
                    // （COUNT(DISTINCT attempt_id) 的防御语义由
                    // [MasteryOverviewInstrumentedTest.aNegativeAttemptCountsOnceThroughItsAttribution]
                    // 钉住）。
                    statement.bindText(1, snapshotId(index))
                    statement.bindText(2, "binding-secondary-$unitIndex")
                    statement.bindText(3, "unit-$unitIndex")
                    statement.bindText(4, nodeId((unitIndex + 1) % KNOWLEDGE_NODE_COUNT))
                    statement.bindDouble(5, 0.5)
                    statement.bindText(6, "revision-$unitIndex")
                    statement.bindText(7, TAXONOMY_VERSION)
                    statement.bindText(8, "SECONDARY")
                    statement.bindText(9, "INFERRED")
                    statement.step()
                    statement.reset()
                }
            }
            usePrepared(INSERT_SUBMISSION) { statement ->
                for (index in 0 until TOTAL_ATTEMPT_COUNT) {
                    statement.bindText(1, "submission-$index")
                    statement.bindText(2, LEARNER_ID)
                    statement.bindText(3, "fingerprint-$index")
                    statement.step()
                    statement.reset()
                }
            }
            usePrepared(INSERT_ATTEMPT) { statement ->
                for (index in 0 until TOTAL_ATTEMPT_COUNT) {
                    val unitIndex = index % KNOWLEDGE_NODE_COUNT
                    statement.bindText(1, attemptId(index))
                    statement.bindText(2, LEARNER_ID)
                    statement.bindText(3, "submission-$index")
                    statement.bindLong(4, index + 1L)
                    statement.bindText(5, "fingerprint-$index")
                    statement.bindText(6, "presentation-$index")
                    statement.bindLong(7, 1)
                    statement.bindText(8, snapshotId(index))
                    statement.bindText(9, "choice-a")
                    statement.bindText(10, "选项 A")
                    statement.bindLong(11, FIXTURE_EPOCH_MILLIS)
                    statement.bindText(
                        12,
                        if (index < NEGATIVE_ATTEMPT_COUNT) "NEGATIVE" else "POSITIVE",
                    )
                    statement.bindDouble(13, 1.0)
                    statement.bindText(
                        14,
                        if (index < NEGATIVE_ATTEMPT_COUNT) {
                            "INDEPENDENT_INCORRECT"
                        } else {
                            "INDEPENDENT_CORRECT"
                        },
                    )
                    statement.bindText(
                        15,
                        if (index < NEGATIVE_ATTEMPT_COUNT) "RETRIEVAL_FAILURE" else "RETRIEVAL_SUCCESS",
                    )
                    statement.bindLong(16, FIXTURE_EPOCH_MILLIS)
                    statement.bindLong(17, 30)
                    statement.bindLong(18, STUDY_DAY)
                    statement.bindText(19, TIME_ZONE)
                    statement.bindLong(20, 480)
                    statement.bindLong(21, 0)
                    statement.bindLong(22, 0)
                    statement.bindNull(23)
                    statement.bindNull(24)
                    statement.bindLong(25, 0)
                    statement.step()
                    statement.reset()
                }
            }
        }
    }

    private fun nodeId(index: Int): String = "kc-s9-$index"
    private fun snapshotId(index: Int): String = "snapshot-s9-$index"
    private fun attemptId(index: Int): String = "attempt-s9-$index"

    /**
     * p95 口径 = `((size - 1) * 95) / 100`（最近秩），与
     * `KnowledgeContextRetrievalInstrumentedTest.kt:397` 逐字一致。
     */
    private fun List<Long>.percentile95(): Long {
        require(isNotEmpty())
        val sorted = sorted()
        return sorted[((sorted.size - 1) * 95) / 100]
    }

    private companion object {
        const val LEARNER_ID = "learner:s9-scale"
        const val SUBJECT = "MATH"
        const val PROJECTION_NAME = "study-experience-v1"
        const val PROJECTOR_VERSION = "s9-scale-projector-v1"
        const val TAXONOMY_VERSION = "s9-scale-taxonomy"
        const val TIME_ZONE = "Asia/Shanghai"
        const val FIXTURE_EPOCH_MILLIS = 1_700_000_000_000L
        const val DAY_MILLIS = 86_400_000L
        const val STUDY_DAY = 19_675L
        const val KNOWLEDGE_NODE_COUNT = 200

        /** 每点独立错误数 = 10 主 + 10 次级。 */
        const val ERRORS_PER_NODE = 20
        const val NEGATIVE_ATTEMPT_COUNT = 2_000
        const val POSITIVE_ATTEMPT_COUNT = 2_000
        const val TOTAL_ATTEMPT_COUNT = NEGATIVE_ATTEMPT_COUNT + POSITIVE_ATTEMPT_COUNT

        /** 与 `RoomTutorToolRunner.MASTERY_FOCUS_RESOLUTION_LIMIT` 同值的聚焦宽度上限。 */
        const val FOCUS_NODE_COUNT = 24

        const val WARMUP_COUNT = 3
        const val SAMPLE_COUNT = 24
        const val PROBE_WARMUP_COUNT = 5
        const val PROBE_SAMPLE_COUNT = 50
        const val CANDIDATE_INDEX_NAME = "index_attempt_event_learner_id_evidence_direction"

        /** 测试内联占位符：EXPLAIN 时替换成 `?` 列表。 */
        const val IN_PLACEHOLDER = "/*IN*/"

        val ATTEMPT_TABLE_SCAN = Regex("""(?i)^SCAN\s+(TABLE\s+)?attempt_event(\s|$)""")
        val ATTRIBUTION_TABLE_SCAN =
            Regex("""(?i)^SCAN\s+(TABLE\s+)?assessment_evidence_attribution(\s|$)""")

        private val CI_MULTIPLIER: Long = run {
            val fromArgs = androidx.test.platform.app.InstrumentationRegistry
                .getArguments()
                .getString("ciSlowRunner")
            if (fromArgs != null || System.getenv("CI") != null) 4L else 1L
        }

        /** 同工具读面的既有预算（`KnowledgeContextRetrievalInstrumentedTest` 的 MASTERY_READ 腿）。 */
        val FOCUSED_READ_P95_BUDGET_MS = 250L * CI_MULTIPLIER

        /**
         * `MasteryOverviewDao.readIndependentErrorAggregates`（`MasteryOverviewDao.kt:109-127`）
         * 的逐字副本（`IN (…)` 写成占位符，EXPLAIN 时按绑定个数展开）。DAO 改 SQL 时必须同步。
         */
        val INDEPENDENT_ERROR_AGGREGATES_SQL = """
            SELECT
                attribution.knowledge_node_id AS knowledge_node_id,
                COUNT(DISTINCT attempt.attempt_id) AS error_count,
                MAX(attempt.occurred_at_epoch_millis) AS latest_at_epoch_millis
            FROM attempt_event AS attempt
            INNER JOIN assessment_evidence_attribution AS attribution
                ON attribution.snapshot_id = attempt.assessment_snapshot_id
            WHERE attempt.learner_id = ?
              AND attempt.evidence_direction = 'NEGATIVE'
              AND attribution.knowledge_node_id IN ($IN_PLACEHOLDER)
            GROUP BY attribution.knowledge_node_id
        """.trimIndent()

        val INSERT_PROJECTION_HEADER = """
            INSERT INTO learner_projection_snapshot (
                projection_name, learner_id, state_version, checkpoint_sequence,
                known_ledger_head_sequence, projector_version, projected_at_epoch_millis,
                generated_at_epoch_millis, correction_watermark_epoch_millis, freshness,
                projection_status
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        val INSERT_MEMORY_STATE = """
            INSERT INTO learner_problem_memory_state (
                projection_name, learner_id, practice_unit_id, stability_days, difficulty,
                last_reviewed_at_epoch_millis, next_review_at_epoch_millis,
                independent_correct_count, assisted_correct_count, lapse_count,
                answer_reveal_count, last_lapse_at_epoch_millis, clock_anomaly_count,
                last_clock_anomaly_at_epoch_millis, projector_version, checkpoint_sequence,
                last_evidence_reason, last_evidence_direction, consecutive_cross_day_success,
                consecutive_cross_day_again
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        val INSERT_MASTERY_STATE = """
            INSERT INTO learner_knowledge_mastery_state (
                projection_name, learner_id, knowledge_node_id,
                probability_independent_correct, lower_bound_independent_correct, evidence_mass,
                last_independent_error_at_epoch_millis, last_independent_error_sequence, status,
                calibration_support, projector_version, checkpoint_sequence,
                last_evidence_at_epoch_millis, conflict_since_sequence, last_evidence_reason,
                last_evidence_direction, success_weight, failure_weight, memory_stability_days,
                memory_difficulty, last_attempt_at_epoch_millis, last_attempt_study_day
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        val INSERT_SNAPSHOT = """
            INSERT INTO assessment_evidence_snapshot (
                snapshot_id, assessment_item_id, practice_unit_id, problem_revision_id,
                answer_spec_id, item_family_id, source_bundle_id, taxonomy_version,
                verification, calibration_support, calibration_source_id, calibration_version,
                calibration_valid_from_epoch_millis, calibration_valid_until_epoch_millis,
                captured_at_epoch_millis
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        val INSERT_ATTRIBUTION = """
            INSERT INTO assessment_evidence_attribution (
                snapshot_id, binding_id, practice_unit_id, knowledge_node_id, weight,
                basis_revision_id, taxonomy_version, role, certainty
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        val INSERT_SUBMISSION = """
            INSERT INTO attempt_submission (submission_id, learner_id, payload_fingerprint)
            VALUES (?, ?, ?)
        """.trimIndent()

        val INSERT_ATTEMPT = """
            INSERT INTO attempt_event (
                attempt_id, learner_id, submission_id, event_sequence, canonical_fingerprint,
                presentation_id, response_ordinal, assessment_snapshot_id, submitted_choice_id,
                submitted_choice_markdown, response_submitted_at_epoch_millis, evidence_direction,
                evidence_weight, evidence_reason, problem_memory_outcome,
                occurred_at_epoch_millis, duration_seconds, study_day_epoch_day,
                study_day_time_zone_id, study_day_utc_offset_minutes, hint_count,
                revealed_before_answer, error_type, error_type_confidence, low_confidence_correct
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
    }
}
