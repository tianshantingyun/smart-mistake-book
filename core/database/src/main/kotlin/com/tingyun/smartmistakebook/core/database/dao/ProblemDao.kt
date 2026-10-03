package com.tingyun.smartmistakebook.core.database.dao

import androidx.room3.ColumnInfo
import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import com.tingyun.smartmistakebook.core.database.entity.ErrorBookEntryEntity
import com.tingyun.smartmistakebook.core.database.entity.KnowledgeNodeEntity
import com.tingyun.smartmistakebook.core.database.entity.PracticeUnitEntity
import com.tingyun.smartmistakebook.core.database.entity.PracticeUnitKnowledgeBindingEntity
import com.tingyun.smartmistakebook.core.database.entity.ProblemEntity
import com.tingyun.smartmistakebook.core.database.entity.ProblemRelationEntity
import com.tingyun.smartmistakebook.core.database.entity.ProblemRevisionEntity
import kotlinx.coroutines.flow.Flow

internal data class MistakeRow(
    @ColumnInfo(name = "entry_id")
    val entryId: String,
    @ColumnInfo(name = "problem_id")
    val problemId: String,
    @ColumnInfo(name = "problem_revision_id")
    val problemRevisionId: String,
    @ColumnInfo(name = "practice_unit_id")
    val practiceUnitId: String,
    @ColumnInfo(name = "source_key")
    val sourceKey: String?,
    val subject: String,
    val title: String,
    @ColumnInfo(name = "problem_markdown")
    val problemMarkdown: String,
    val status: String,
    @ColumnInfo(name = "created_at_epoch_millis")
    val createdAtEpochMillis: Long,
    @ColumnInfo(name = "updated_at_epoch_millis")
    val updatedAtEpochMillis: Long,
    @ColumnInfo(name = "estimated_seconds")
    val estimatedSeconds: Int,
    @ColumnInfo(name = "next_review_at_epoch_millis")
    val nextReviewAtEpochMillis: Long?,
    val retrievability: Double?,
    @ColumnInfo(name = "knowledge_node_ids")
    val knowledgeNodeIds: String?,
    @ColumnInfo(name = "chapter_labels")
    val chapterLabels: String?,
    @ColumnInfo(name = "knowledge_labels")
    val knowledgeLabels: String?,
    @ColumnInfo(name = "capture_occurrence_count")
    val captureOccurrenceCount: Int,
)

@Dao
internal interface ProblemDao {

    @Query(
        "SELECT * FROM knowledge_question_lattice " +
            "WHERE memory_learner_id IS NULL OR memory_learner_id = :learnerId " +
            "ORDER BY practice_unit_id, knowledge_node_id",
    )
    fun observeKnowledgeQuestionLattice(learnerId: String): Flow<List<com.tingyun.smartmistakebook.core.database.entity.KnowledgeQuestionLatticeView>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertProblems(problems: List<ProblemEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRevisions(revisions: List<ProblemRevisionEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPracticeUnits(practiceUnits: List<PracticeUnitEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertErrorBookEntries(entries: List<ErrorBookEntryEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertKnowledgeNodes(nodes: List<KnowledgeNodeEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertKnowledgeBindings(
        bindings: List<PracticeUnitKnowledgeBindingEntity>,
    ): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRelations(relations: List<ProblemRelationEntity>): List<Long>

    @Query(
        """
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
        """,
    )
    /**
     * Memory facts come from the learner projection, the only table the
     * projector writes. Joining the pre-projection `problem_memory_state`
     * returned NULL for every production row — only fixture seeding wrote it,
     * so the gap stayed invisible on device. `retrievability` is derived from
     * stability plus "now" and is computed by the reader instead.
     *
     * S17（阶段 4A 批 1，`docs/research/2026-10-03-stage4a-plan.md` §3 批 1；缺陷线索见
     * `docs/known-defects.md` 中 `observeActiveMistakes` 的既有登记）：此前的 SELECT 列表逐行跑
     * 四个相关子查询（计数、绑定节点集合、章节标签、知识点标签），其中标签子查询还嵌了
     * EXISTS/MAX。重写为**派生表 + LEFT JOIN 聚合**：每个聚合只按 (practice_unit, revision) 或
     * (problem, revision) 算一遍，外层逐行只做等值连接，结果集与旧口径逐列等价
     * （`ProblemDaoAggregationEquivalenceInstrumentedTest` 用旧 SQL 对照实测）。
     */
    fun observeActiveMistakes(): Flow<List<MistakeRow>>

    @Query(
        """
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
            (
                SELECT COUNT(*)
                FROM problem_draft_commit_receipt AS receipt
                WHERE receipt.practice_unit_id = unit.practice_unit_id
            ) AS capture_occurrence_count,
            (
                SELECT GROUP_CONCAT(binding.knowledge_node_id, CHAR(31))
                FROM practice_unit_knowledge_binding AS binding
                INNER JOIN knowledge_node AS node
                    ON node.knowledge_node_id = binding.knowledge_node_id
                WHERE binding.practice_unit_id = unit.practice_unit_id
                  AND binding.basis_revision_id = revision.revision_id
                  AND (
                      NOT EXISTS (
                          SELECT 1
                          FROM problem_organization_receipt AS receipt
                          WHERE receipt.problem_id = problem.problem_id
                            AND receipt.problem_revision_id = revision.revision_id
                      )
                      OR (
                          binding.accepted_at_epoch_millis = (
                              SELECT MAX(receipt.accepted_at_epoch_millis)
                              FROM problem_organization_receipt AS receipt
                              WHERE receipt.problem_id = problem.problem_id
                                AND receipt.problem_revision_id = revision.revision_id
                          )
                          AND EXISTS (
                              SELECT 1
                              FROM problem_classification_binding AS classification
                              WHERE classification.problem_id = problem.problem_id
                                AND classification.basis_revision_id = revision.revision_id
                                AND classification.dimension = 'KNOWLEDGE'
                                AND classification.taxonomy_version =
                                    binding.taxonomy_version
                                AND classification.accepted_at_epoch_millis =
                                    binding.accepted_at_epoch_millis
                          )
                      )
                  )
            ) AS knowledge_node_ids,
            NULL AS chapter_labels,
            NULL AS knowledge_labels
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
        WHERE entry.source_key = :sourceKey
        LIMIT 1
        """,
    )
    /** Same projection-backed memory source as [observeActiveMistakes]. */
    suspend fun findMistakeBySourceKey(sourceKey: String): MistakeRow?

    @Query("SELECT COUNT(*) FROM error_book_entry WHERE status = 'ACTIVE'")
    suspend fun countActiveMistakes(): Int

    @Query(
        """
        UPDATE problem_relation
        SET status = :staleStatus,
            updated_at_epoch_millis = :updatedAtEpochMillis
        WHERE status != :staleStatus
          AND (
            source_basis_revision_id = :problemRevisionId
            OR target_basis_revision_id = :problemRevisionId
          )
        """,
    )
    suspend fun markRelationsStaleForRevision(
        problemRevisionId: String,
        staleStatus: String,
        updatedAtEpochMillis: Long,
    ): Int

    @Query("SELECT status FROM problem_relation WHERE relation_id = :relationId")
    suspend fun relationStatus(relationId: String): String?
}
