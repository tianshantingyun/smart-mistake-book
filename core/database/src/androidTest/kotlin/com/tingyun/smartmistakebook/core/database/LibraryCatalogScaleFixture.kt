package com.tingyun.smartmistakebook.core.database

import androidx.room3.withWriteTransaction
import com.tingyun.smartmistakebook.core.database.entity.ProblemClassificationBindingEntity

/**
 * **S18 目录规模夹具**（`docs/research/2026-10-05-stage3c-part2-plan.md` §3 批 2）：
 * `library_catalog` 视图的 5 万行形态，**补齐 mastery / 分类 / 投影三块**。
 *
 * 为什么必须补齐（本夹具要消灭的具体失败）：三块缺失时视图的三条相关子查询走的是
 * **空连接**——`mastery_id` 恒 `'unknown'`、`chapter_labels`/`knowledge_labels` 恒 NULL，
 * 于是 `LibraryQueryDao` 的 `sectionId` EXISTS、`masteryId` 过滤与 `instr(...)` 文本拼接
 * 全部退化（拿空结果/零成本冒充真实成本），量出来的不是生产形态（4A 批 1 的 5 万行夹具
 * 与批 1 报告已记录该缺口）。
 *
 * 补齐后的行数分布（`count` = 5 万）：
 * - `problem` / `problem_revision` / `practice_unit` / `error_book_entry`：各 [count] 行；
 * - `knowledge_node`：[KNOWLEDGE_NODE_COUNT] 行（每点都挂一条绑定与一行掌握态）；
 * - `practice_unit_knowledge_binding`：[count] 行（每单元一条，`basis_revision_id` 对齐）；
 * - `learner_problem_memory_state`：[count] 行（题卡记忆态；视图借它的 `learner_id`
 *   去 join 掌握态，缺了它 `mastery_id` 仍然恒 `'unknown'`）；
 * - `learner_knowledge_mastery_state`：[KNOWLEDGE_NODE_COUNT] 行，状态四桶循环
 *   （LEARNING / MASTERED / STALE / CONFLICTED），让 mastery facet 与过滤有真实分布；
 * - `problem_classification_binding`：每个问题 2 行（CHAPTER × [CHAPTER_COUNT] 桶、
 *   KNOWLEDGE × [KNOWLEDGE_LABEL_COUNT] 桶），让章节筛选与标签拼接有真实文本。
 *
 * 落库方式：**一个写事务**、全批量 DAO + 预编译语句直写（夹具成本不是被测热路径；
 * 同 [ProblemDaoActiveMistakesPerformanceInstrumentedTest.insertFixture] 与
 * [PerformanceGateTest.insertTestData] 先例）。
 */
internal object LibraryCatalogScale {

    const val LEARNER_ID = "learner:local"
    const val PROJECTION_NAME = "study-experience-v1"
    const val PROJECTOR_VERSION = "library-scale-projector-v1"
    const val TAXONOMY_VERSION = "library-scale-taxonomy"
    const val KNOWLEDGE_NODE_COUNT = 2_000
    const val CHAPTER_COUNT = 20
    const val KNOWLEDGE_LABEL_COUNT = 50

    /** 夹具文本里的检索词（与 4A 批 1 的 5 万行夹具同形：每 100 行一条）。 */
    const val UNIQUE_SEARCH_TOKEN = "独特检索词"

    const val FIXTURE_EPOCH_MILLIS = 1_700_000_000_000L

    fun nodeId(index: Int): String = "kc-scale-$index"

    fun chapterLabelId(index: Int): String = "chapter-${index % CHAPTER_COUNT}"

    fun chapterLabelName(index: Int): String = "章节${index % CHAPTER_COUNT}"

    fun knowledgeLabelId(index: Int): String = "knowledge-${index % KNOWLEDGE_LABEL_COUNT}"

    fun knowledgeLabelName(index: Int): String = "知识点标签${index % KNOWLEDGE_LABEL_COUNT}"

    /**
     * 每个知识点在掌握态四桶里的循环取值（与 `mastery_id` 视图 CASE 的取值一一对应）。
     *
     * 按 **每 [CHAPTER_COUNT] 个知识点一块**轮转 `(index / CHAPTER_COUNT) % 4`，而不是逐点
     * `index % 4`：章节按 `index % CHAPTER_COUNT` 分桶，逐点轮转会让每个掌握桶固定落在
     * 桶号 ≡ (0/1/2/3) (mod 4) 的章节上——章节 × 掌握组合查询恒 0 命中，组合路径量到的
     * 是空结果形态（复核发现）。分块轮转后每个章节桶都含四种掌握态，总量仍各占 1/4。
     */
    fun masteryStatus(index: Int): String = when ((index / CHAPTER_COUNT) % 4) {
        0 -> "LEARNING"
        1 -> "MASTERED"
        2 -> "STALE"
        else -> "CONFLICTED"
    }

    /** 视图 `mastery_id` 对 [masteryStatus] 的折叠结果（同四桶，另一种措辞）。 */
    fun masteryId(index: Int): String = when (masteryStatus(index)) {
        "LEARNING" -> "learning"
        "MASTERED" -> "mastered"
        "STALE" -> "stale"
        else -> "conflicted"
    }
}

/**
 * 往 [StudyDatabase] 落 [count] 行目录夹具（列/文本形态见 [LibraryCatalogScale]）。
 * 调用方自己决定库是内存还是文件；两种都能用（同一写事务、同一批语句）。
 */
internal suspend fun StudyDatabase.seedLibraryCatalogScale(count: Int) {
    val problems = List(count) { index ->
        ProblemSeedRecord(
            problemId = "problem-$index",
            canonicalFingerprint = index.toString(16).padStart(64, '0'),
            subject = if (index % 2 == 0) "MATH" else "PHYSICS",
            createdAtEpochMillis = index + 1L,
        )
    }
    val revisions = List(count) { index ->
        ProblemRevisionSeedRecord(
            revisionId = "revision-$index",
            problemId = "problem-$index",
            revisionNumber = 1,
            title = "分页题目 ${index + 1}",
            problemMarkdown = if (index % 100 == 0) {
                "${LibraryCatalogScale.UNIQUE_SEARCH_TOKEN}$index"
            } else {
                "普通题面 $index"
            },
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
            practiceUnitId = "practice-$index",
            problemId = "problem-$index",
            problemRevisionId = "revision-$index",
            unitKey = "whole-problem",
            unitKind = "WHOLE_PROBLEM",
            title = "分页题目 ${index + 1}",
            promptMarkdown = if (index % 100 == 0) {
                "${LibraryCatalogScale.UNIQUE_SEARCH_TOKEN}$index"
            } else {
                "普通题面"
            },
            estimatedSeconds = 180,
            createdAtEpochMillis = index + 1L,
        )
    }
    val entries = List(count) { index ->
        ErrorBookEntrySeedRecord(
            entryId = "entry-$index",
            practiceUnitId = "practice-$index",
            problemId = "problem-$index",
            currentRevisionId = "revision-$index",
            sourceKey = null,
            acceptedAtEpochMillis = index + 1L,
            updatedAtEpochMillis = index + 1L,
        )
    }
    val nodes = List(LibraryCatalogScale.KNOWLEDGE_NODE_COUNT) { index ->
        KnowledgeNodeSeedRecord(
            knowledgeNodeId = LibraryCatalogScale.nodeId(index),
            stableCode = "library-scale.kc.$index",
            subject = if (index % 2 == 0) "MATH" else "PHYSICS",
            displayName = "规模知识点$index",
            parentKnowledgeNodeId = null,
            taxonomyVersion = LibraryCatalogScale.TAXONOMY_VERSION,
            createdAtEpochMillis = 1L,
            granularity = "ATOMIC",
        )
    }
    val bindings = List(count) { index ->
        KnowledgeBindingSeedRecord(
            bindingId = "binding-$index",
            practiceUnitId = "practice-$index",
            knowledgeNodeId = LibraryCatalogScale.nodeId(index % LibraryCatalogScale.KNOWLEDGE_NODE_COUNT),
            basisRevisionId = "revision-$index",
            strength = 1.0,
            sourceType = "VERIFIED",
            taxonomyVersion = LibraryCatalogScale.TAXONOMY_VERSION,
            acceptedAtEpochMillis = index + 1L,
        )
    }
    val classifications = buildList {
        for (index in 0 until count) {
            add(
                ProblemClassificationBindingEntity(
                    bindingId = "class-chapter-$index",
                    problemId = "problem-$index",
                    basisRevisionId = "revision-$index",
                    dimension = "CHAPTER",
                    labelId = LibraryCatalogScale.chapterLabelId(index),
                    displayName = LibraryCatalogScale.chapterLabelName(index),
                    taxonomyVersion = LibraryCatalogScale.TAXONOMY_VERSION,
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
                    labelId = LibraryCatalogScale.knowledgeLabelId(index),
                    displayName = LibraryCatalogScale.knowledgeLabelName(index),
                    taxonomyVersion = LibraryCatalogScale.TAXONOMY_VERSION,
                    acceptanceSource = "LOCAL_POLICY_ACCEPTED",
                    acceptedAtEpochMillis = index + 1L,
                ),
            )
        }
    }

    val database = this
    database.withWriteTransaction {
        val problemDao = database.problemDao()
        problemDao.insertProblems(problems.map { it.toEntity() })
        problemDao.insertRevisions(revisions.map { it.toEntity() })
        problemDao.insertPracticeUnits(units.map { it.toEntity() })
        problemDao.insertErrorBookEntries(entries.map { it.toEntity() })
        problemDao.insertKnowledgeNodes(nodes.map { it.toEntity() })
        problemDao.insertKnowledgeBindings(bindings.map { it.toEntity() })
        database.problemOrganizationDao().insertClassificationBindings(classifications)

        usePrepared(INSERT_SCALE_PROJECTION_HEADER) { statement ->
            statement.bindText(1, LibraryCatalogScale.PROJECTION_NAME)
            statement.bindText(2, LibraryCatalogScale.LEARNER_ID)
            statement.bindLong(3, 0)
            statement.bindLong(4, 0)
            statement.bindLong(5, 0)
            statement.bindText(6, LibraryCatalogScale.PROJECTOR_VERSION)
            statement.bindLong(7, LibraryCatalogScale.FIXTURE_EPOCH_MILLIS)
            statement.bindLong(8, LibraryCatalogScale.FIXTURE_EPOCH_MILLIS)
            statement.bindNull(9)
            statement.bindText(10, "CURRENT")
            statement.bindText(11, "CURRENT")
            statement.step()
        }
        usePrepared(INSERT_SCALE_MEMORY_STATE) { statement ->
            for (index in 0 until count) {
                statement.bindText(1, LibraryCatalogScale.PROJECTION_NAME)
                statement.bindText(2, LibraryCatalogScale.LEARNER_ID)
                statement.bindText(3, "practice-$index")
                statement.bindDouble(4, 3.0)
                statement.bindDouble(5, 5.0)
                statement.bindLong(6, LibraryCatalogScale.FIXTURE_EPOCH_MILLIS)
                statement.bindLong(7, LibraryCatalogScale.FIXTURE_EPOCH_MILLIS + index)
                statement.bindLong(8, 0)
                statement.bindLong(9, 0)
                statement.bindLong(10, 0)
                statement.bindLong(11, 0)
                statement.bindNull(12)
                statement.bindLong(13, 0)
                statement.bindNull(14)
                statement.bindText(15, LibraryCatalogScale.PROJECTOR_VERSION)
                statement.bindLong(16, 0)
                statement.bindNull(17)
                statement.bindNull(18)
                statement.bindLong(19, 0)
                statement.bindLong(20, 0)
                statement.step()
                statement.reset()
            }
        }
        usePrepared(INSERT_SCALE_MASTERY_STATE) { statement ->
            for (index in 0 until LibraryCatalogScale.KNOWLEDGE_NODE_COUNT) {
                statement.bindText(1, LibraryCatalogScale.PROJECTION_NAME)
                statement.bindText(2, LibraryCatalogScale.LEARNER_ID)
                statement.bindText(3, LibraryCatalogScale.nodeId(index))
                statement.bindDouble(4, (index % 100) / 100.0)
                statement.bindDouble(5, (index % 100) / 100.0)
                statement.bindDouble(6, 1.0)
                statement.bindNull(7)
                statement.bindNull(8)
                statement.bindText(9, LibraryCatalogScale.masteryStatus(index))
                statement.bindText(10, "UNKNOWN")
                statement.bindText(11, LibraryCatalogScale.PROJECTOR_VERSION)
                statement.bindLong(12, 0)
                statement.bindLong(13, LibraryCatalogScale.FIXTURE_EPOCH_MILLIS)
                statement.bindNull(14)
                statement.bindNull(15)
                statement.bindNull(16)
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
    }
}

private val INSERT_SCALE_PROJECTION_HEADER = """
    INSERT INTO learner_projection_snapshot (
        projection_name, learner_id, state_version, checkpoint_sequence,
        known_ledger_head_sequence, projector_version, projected_at_epoch_millis,
        generated_at_epoch_millis, correction_watermark_epoch_millis, freshness,
        projection_status
    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
""".trimIndent()

private val INSERT_SCALE_MEMORY_STATE = """
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

private val INSERT_SCALE_MASTERY_STATE = """
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
