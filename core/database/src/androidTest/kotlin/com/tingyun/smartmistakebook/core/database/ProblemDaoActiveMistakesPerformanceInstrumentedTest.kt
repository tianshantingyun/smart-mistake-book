package com.tingyun.smartmistakebook.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room3.withWriteTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.database.entity.ProblemClassificationBindingEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.system.measureTimeMillis

/**
 * **S17 专属性能门**（`docs/research/2026-10-05-stage3c-part2-plan.md` §3 批 1）：
 *
 * `observeActiveMistakes` 已在 4A 批 1 从"逐行相关子查询 + 嵌套 EXISTS"重写为
 * "派生表 + LEFT JOIN 聚合"（`ProblemDao.kt:85-240`，等价性由
 * [ProblemDaoAggregationEquivalenceInstrumentedTest] 逐列钉住），但**性能未量化**：
 * 本轮给它配 5000 行 ACTIVE 条目夹具 + 查询计划结构断言 + 计时 backstop。
 *
 * 结构断言是主门（EQP 输出格式无稳定性契约，官方标注 "intended for interactive
 * debugging only"——只断言结构不变量，不比对完整字符串）；计时是宽松 backstop
 * （CI 用 `ciSlowRunner` 系数，口径同 [PerformanceGateTest]）。
 */
@RunWith(AndroidJUnit4::class)
class ProblemDaoActiveMistakesPerformanceInstrumentedTest {

    @Test
    fun fiveThousandActiveMistakesPlanAndLatency() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "s17-active-mistakes-scale-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        try {
            val store = StudyDatabaseFactory.open(context, databaseName) as RoomStudyDatabase
            try {
                insertFixture(store, ACTIVE_MISTAKE_COUNT)

                // 借 Room 先把表/视图落盘，再用平台连接打开**同一文件**读 EXPLAIN
                // （room3 prepared-statement 的 step 路径对 EXPLAIN 会抛
                // "Queries can be performed using SQLiteDatabase query or rawQuery
                // methods only"；先用 SELECT 1 确保 schema 已建）。
                store.database.withRawConnection(isReadOnly = true) { connection ->
                    connection.usePrepared("SELECT 1") { statement -> statement.step() }
                }
                val plan = readQueryPlan(context, databaseName)
                println(
                    "S17 observeActiveMistakes EXPLAIN QUERY PLAN ($ACTIVE_MISTAKE_COUNT rows):\n" +
                        plan.joinToString("\n"),
                )

                // ① 结构断言：外层两处关键访问都走索引（出现目标索引/SEARCH）——
                // entry 走 (status, updated_at) 索引、problem 走主键唯一索引。
                val planText = plan.joinToString("\n")
                assertTrue(
                    "S17 计划里 entry 未走索引：\n$planText",
                    plan.any { it.startsWith("SEARCH entry USING INDEX") },
                )
                assertTrue(
                    "S17 计划里 problem 未走索引：\n$planText",
                    plan.any { it.startsWith("SEARCH problem USING INDEX") },
                )
                // ② 结构断言：不得对 problem 表全表扫描（`SCAN problem`；老版本 EQP
                // 文字是 `SCAN TABLE problem`）。注意与 `SCAN problem_organization_receipt`
                // 之类同前缀表名区分——断言按整词匹配。
                assertTrue(
                    "S17 计划对 problem 表全表扫描：\n$planText",
                    plan.none { PROBLEM_TABLE_SCAN.containsMatchIn(it) },
                )
                // ③ 结构断言：不得出现整段 ORDER BY 的临时排序。
                // 设备 SQLite（API 34）对本查询报的是
                // "USE TEMP B-TREE FOR RIGHT PART OF ORDER BY"——左键
                // (status, updated_at) 由索引覆盖、只对右半键 entry_id 补排；
                // 该补排是既有呈现排序的固有成本（S17 前后同形），不是派生表改写的信号。
                assertFalse(
                    "S17 计划出现整段 ORDER BY 临时排序：\n$planText",
                    plan.any { it.contains("TEMP B-TREE FOR ORDER BY") },
                )
                // ④ 结构断言（S17 可证伪信号）：不得回到"逐行相关标量子查询"形态。
                // 这正是 4A 批 1 重写消灭的具体失败（旧形态每行 2-4 个子查询 + 嵌套 EXISTS）；
                // 临时把查询退化成旧形态时本断言必红（红/绿证据见报告）。
                assertFalse(
                    "S17 计划出现相关标量子查询（旧形态回归）：\n$planText",
                    plan.any { it.contains("CORRELATED SCALAR SUBQUERY") },
                )

                // ③ 计时 backstop + 非空信号（行数与标签列必须在真实夹具上非空）。
                val dao = store.database.problemDao()
                suspend fun readActiveMistakes() = dao.observeActiveMistakes().first()

                var rows = readActiveMistakes()
                repeat(WARMUP_COUNT) { rows = readActiveMistakes() }
                assertEquals(ACTIVE_MISTAKE_COUNT, rows.size)
                assertTrue(
                    "夹具脱节：并非每行都有 knowledge_node_ids",
                    rows.all { !it.knowledgeNodeIds.isNullOrEmpty() },
                )
                assertEquals(
                    "夹具脱节：章节标签非空行数",
                    ACTIVE_MISTAKE_COUNT / 10,
                    rows.count { !it.chapterLabels.isNullOrEmpty() },
                )
                assertEquals(
                    "夹具脱节：知识点标签非空行数",
                    ACTIVE_MISTAKE_COUNT / 10,
                    rows.count { !it.knowledgeLabels.isNullOrEmpty() },
                )

                val samples = mutableListOf<Long>()
                repeat(SAMPLE_COUNT) {
                    samples += measureTimeMillis { rows = readActiveMistakes() }
                }
                val sorted = samples.sorted()
                val p95 = sorted[((sorted.size - 1) * 95) / 100]
                println("S17 observeActiveMistakes benchmark: samples=$samples p95=${p95}ms")
                assertTrue(
                    "observeActiveMistakes p95=${p95}ms 超过 backstop " +
                        "${ACTIVE_MISTAKES_P95_BUDGET_MS}ms；samples=$samples",
                    p95 < ACTIVE_MISTAKES_P95_BUDGET_MS,
                )
            } finally {
                store.close()
            }
        } finally {
            context.deleteDatabase(databaseName)
        }
    }

    /**
     * 5000 行 ACTIVE 条目夹具：problems / revisions / practice_units /
     * error_book_entries 各 [count] 行；每行一条知识绑定（无组织回执 →
     * 绑定全部算当前），每 10 行一组 CHAPTER + KNOWLEDGE 分类（同 taxonomy /
     * accepted_at 与绑定对齐）。真实落库、全批量、一个写事务。
     */
    private suspend fun insertFixture(store: RoomStudyDatabase, count: Int) {
        val problems = List(count) { index ->
            ProblemSeedRecord(
                problemId = "problem-$index",
                canonicalFingerprint = index.toString(16).padStart(64, '0'),
                subject = "MATH",
                createdAtEpochMillis = index + 1L,
            )
        }
        val revisions = List(count) { index ->
            ProblemRevisionSeedRecord(
                revisionId = "revision-$index",
                problemId = "problem-$index",
                revisionNumber = 1,
                title = "S17 题 ${index + 1}",
                problemMarkdown = "S17 夹具题面 $index",
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
        val units = List(count) { index ->
            PracticeUnitSeedRecord(
                practiceUnitId = "unit-$index",
                problemId = "problem-$index",
                problemRevisionId = "revision-$index",
                unitKey = "whole-problem",
                unitKind = "WHOLE_PROBLEM",
                title = "S17 题 ${index + 1}",
                promptMarkdown = "S17 夹具题面",
                estimatedSeconds = 180,
                createdAtEpochMillis = index + 1L,
            )
        }
        val entries = List(count) { index ->
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
                knowledgeNodeId = "kc-$index",
                stableCode = "s17.kc.$index",
                subject = "MATH",
                displayName = "知识点$index",
                parentKnowledgeNodeId = null,
                taxonomyVersion = TAXONOMY_VERSION,
                createdAtEpochMillis = 1L,
            )
        }
        val bindings = List(count) { index ->
            KnowledgeBindingSeedRecord(
                bindingId = "binding-$index",
                practiceUnitId = "unit-$index",
                knowledgeNodeId = "kc-${index % KNOWLEDGE_NODE_COUNT}",
                basisRevisionId = "revision-$index",
                strength = 1.0,
                sourceType = "VERIFIED",
                taxonomyVersion = TAXONOMY_VERSION,
                acceptedAtEpochMillis = index + 1L,
            )
        }
        val classifications = buildList {
            for (index in 0 until count step 10) {
                add(
                    ProblemClassificationBindingEntity(
                        bindingId = "class-chapter-$index",
                        problemId = "problem-$index",
                        basisRevisionId = "revision-$index",
                        dimension = "CHAPTER",
                        labelId = "chapter-$index",
                        displayName = "章节$index",
                        taxonomyVersion = TAXONOMY_VERSION,
                        acceptanceSource = "LOCAL_POLICY_ACCEPTED",
                        acceptedAtEpochMillis = index + 1L,
                    ),
                )
                add(
                    ProblemClassificationBindingEntity(
                        bindingId = "class-knowledge-$index",
                        problemId = "problem-$index",
                        basisRevisionId = "revision-$index",
                        dimension = "KNOWLEDGE",
                        labelId = "knowledge-$index",
                        displayName = "知识点标签$index",
                        taxonomyVersion = TAXONOMY_VERSION,
                        acceptanceSource = "LOCAL_POLICY_ACCEPTED",
                        acceptedAtEpochMillis = index + 1L,
                    ),
                )
            }
        }

        store.database.withWriteTransaction {
            val dao = store.database.problemDao()
            dao.insertProblems(problems.map { it.toEntity() })
            dao.insertRevisions(revisions.map { it.toEntity() })
            dao.insertPracticeUnits(units.map { it.toEntity() })
            dao.insertErrorBookEntries(entries.map { it.toEntity() })
            dao.insertKnowledgeNodes(nodes.map { it.toEntity() })
            dao.insertKnowledgeBindings(bindings.map { it.toEntity() })
            store.database.problemOrganizationDao().insertClassificationBindings(classifications)
        }
    }

    private fun readQueryPlan(context: Context, databaseName: String): List<String> =
        SQLiteDatabase.openDatabase(
            context.getDatabasePath(databaseName).absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY,
        ).use { database ->
            database.rawQuery(
                "EXPLAIN QUERY PLAN $ACTIVE_MISTAKES_SQL",
                null,
            ).use { cursor ->
                val detailColumn = cursor.getColumnIndexOrThrow("detail")
                buildList {
                    while (cursor.moveToNext()) add(cursor.getString(detailColumn))
                }
            }
        }

    private companion object {
        const val ACTIVE_MISTAKE_COUNT = 5_000
        const val KNOWLEDGE_NODE_COUNT = 600
        const val TAXONOMY_VERSION = "s17-scale-taxonomy"
        const val WARMUP_COUNT = 3
        const val SAMPLE_COUNT = 20

        /** `SCAN problem` / 老版本 `SCAN TABLE problem` 的同一条全表扫描信号。 */
        val PROBLEM_TABLE_SCAN = Regex("""(?i)^SCAN\s+(TABLE\s+)?problem(\s|$)""")

        /**
         * 计时 backstop（宽松；结构断言才是主门）。CI 由 `ciSlowRunner` 传参乘 4，
         * 口径与 [PerformanceGateTest]/`KnowledgeContextRetrievalInstrumentedTest` 一致。
         */
        private val CI_MULTIPLIER: Long = run {
            val fromArgs = androidx.test.platform.app.InstrumentationRegistry
                .getArguments()
                .getString("ciSlowRunner")
            if (fromArgs != null || System.getenv("CI") != null) 4L else 1L
        }
        val ACTIVE_MISTAKES_P95_BUDGET_MS = 1_500L * CI_MULTIPLIER

        /**
         * `ProblemDao.observeActiveMistakes` 生产 SQL 的**逐字副本**
         * （`core/database/src/main/kotlin/.../dao/ProblemDao.kt:85-224` 的
         * `@Query` 值；不含 Room 生成的 bind 参数——该查询无参数）。
         *
         * 为什么是副本：room3 的 prepared-statement 路径拒绝执行 EXPLAIN（见测试内注释），
         * 只能用平台 rawQuery；`@Query` 注解是 BINARY retention，运行时反射取不到原文。
         * **DAO 改动此查询时必须同步本副本**（EQP 门测的就是这里的文本）。
         */
        val ACTIVE_MISTAKES_SQL: String = """
            SELECT
                entry.entry_id,
                problem.problem_id,
                revision.revision_id AS problem_revision_id,
                unit.practice_unit_id,
                entry.source_key,
                problem.subject,
                revision.title,
                revision.problem_markdown,
                entry.status,
                entry.accepted_at_epoch_millis AS created_at_epoch_millis,
                entry.updated_at_epoch_millis AS updated_at_epoch_millis,
                unit.estimated_seconds,
                memory.next_review_at_epoch_millis,
                NULL AS retrievability,
                COALESCE(capture_counts.capture_occurrence_count, 0) AS capture_occurrence_count,
                knowledge.knowledge_node_ids,
                chapters.chapter_labels,
                knowledge_labels.knowledge_labels
            FROM error_book_entry AS entry
            JOIN practice_unit AS unit
                ON unit.practice_unit_id = entry.practice_unit_id
            JOIN problem AS problem
                ON problem.problem_id = unit.problem_id
            JOIN problem_revision AS revision
                ON revision.revision_id = entry.current_revision_id
            LEFT JOIN learner_problem_memory_state AS memory
                ON memory.practice_unit_id = unit.practice_unit_id
               AND memory.projection_name = 'study-experience-v1'
            LEFT JOIN (
                SELECT receipt.practice_unit_id, COUNT(*) AS capture_occurrence_count
                FROM problem_draft_commit_receipt AS receipt
                GROUP BY receipt.practice_unit_id
            ) AS capture_counts
                ON capture_counts.practice_unit_id = unit.practice_unit_id
            LEFT JOIN (
                SELECT
                    binding.practice_unit_id,
                    binding.basis_revision_id,
                    GROUP_CONCAT(binding.knowledge_node_id, CHAR(31)) AS knowledge_node_ids
                FROM practice_unit_knowledge_binding AS binding
                INNER JOIN knowledge_node AS node
                    ON node.knowledge_node_id = binding.knowledge_node_id
                INNER JOIN practice_unit AS binding_unit
                    ON binding_unit.practice_unit_id = binding.practice_unit_id
                LEFT JOIN (
                    SELECT problem_id, problem_revision_id,
                        MAX(accepted_at_epoch_millis) AS current_accepted_at
                    FROM problem_organization_receipt
                    GROUP BY problem_id, problem_revision_id
                ) AS binding_receipt_head
                    ON binding_receipt_head.problem_id = binding_unit.problem_id
                   AND binding_receipt_head.problem_revision_id = binding.basis_revision_id
                LEFT JOIN (
                    SELECT DISTINCT problem_id, basis_revision_id, taxonomy_version,
                        accepted_at_epoch_millis
                    FROM problem_classification_binding
                    WHERE dimension = 'KNOWLEDGE'
                ) AS binding_classification
                    ON binding_classification.problem_id = binding_unit.problem_id
                   AND binding_classification.basis_revision_id = binding.basis_revision_id
                   AND binding_classification.taxonomy_version = binding.taxonomy_version
                   AND binding_classification.accepted_at_epoch_millis =
                       binding.accepted_at_epoch_millis
                WHERE binding_receipt_head.problem_id IS NULL
                   OR (
                       binding.accepted_at_epoch_millis = binding_receipt_head.current_accepted_at
                       AND binding_classification.problem_id IS NOT NULL
                   )
                GROUP BY binding.practice_unit_id, binding.basis_revision_id
            ) AS knowledge
                ON knowledge.practice_unit_id = unit.practice_unit_id
               AND knowledge.basis_revision_id = revision.revision_id
            LEFT JOIN (
                SELECT classification.problem_id, classification.basis_revision_id,
                    GROUP_CONCAT(classification.display_name, CHAR(31)) AS chapter_labels
                FROM problem_classification_binding AS classification
                WHERE classification.dimension = 'CHAPTER'
                GROUP BY classification.problem_id, classification.basis_revision_id
            ) AS chapters
                ON chapters.problem_id = problem.problem_id
               AND chapters.basis_revision_id = revision.revision_id
            LEFT JOIN (
                SELECT
                    entry_keys.practice_unit_id,
                    entry_keys.problem_id,
                    entry_keys.basis_revision_id,
                    GROUP_CONCAT(classification.display_name, CHAR(31)) AS knowledge_labels
                FROM (
                    SELECT DISTINCT
                        entry.practice_unit_id AS practice_unit_id,
                        entry.current_revision_id AS basis_revision_id,
                        unit.problem_id AS problem_id
                    FROM error_book_entry AS entry
                    INNER JOIN practice_unit AS unit
                        ON unit.practice_unit_id = entry.practice_unit_id
                    WHERE entry.status = 'ACTIVE'
                ) AS entry_keys
                LEFT JOIN (
                    SELECT problem_id, problem_revision_id,
                        MAX(accepted_at_epoch_millis) AS current_accepted_at
                    FROM problem_organization_receipt
                    GROUP BY problem_id, problem_revision_id
                ) AS label_receipt_head
                    ON label_receipt_head.problem_id = entry_keys.problem_id
                   AND label_receipt_head.problem_revision_id = entry_keys.basis_revision_id
                LEFT JOIN problem_classification_binding AS classification
                    ON classification.problem_id = entry_keys.problem_id
                   AND classification.basis_revision_id = entry_keys.basis_revision_id
                   AND classification.dimension = 'KNOWLEDGE'
                LEFT JOIN (
                    SELECT DISTINCT binding.practice_unit_id, binding.basis_revision_id,
                        binding.taxonomy_version, binding.accepted_at_epoch_millis
                    FROM practice_unit_knowledge_binding AS binding
                    INNER JOIN knowledge_node AS node
                        ON node.knowledge_node_id = binding.knowledge_node_id
                ) AS label_binding_key
                    ON label_binding_key.practice_unit_id = entry_keys.practice_unit_id
                   AND label_binding_key.basis_revision_id = entry_keys.basis_revision_id
                   AND label_binding_key.taxonomy_version = classification.taxonomy_version
                   AND label_binding_key.accepted_at_epoch_millis =
                       classification.accepted_at_epoch_millis
                WHERE classification.problem_id IS NULL
                   OR label_receipt_head.problem_id IS NULL
                   OR (
                       classification.accepted_at_epoch_millis =
                           label_receipt_head.current_accepted_at
                       AND label_binding_key.practice_unit_id IS NOT NULL
                   )
                GROUP BY entry_keys.practice_unit_id, entry_keys.problem_id,
                    entry_keys.basis_revision_id
            ) AS knowledge_labels
                ON knowledge_labels.practice_unit_id = unit.practice_unit_id
               AND knowledge_labels.problem_id = problem.problem_id
               AND knowledge_labels.basis_revision_id = revision.revision_id
            WHERE entry.status = 'ACTIVE'
            ORDER BY entry.updated_at_epoch_millis DESC, entry.entry_id ASC
        """.trimIndent()
    }
}
