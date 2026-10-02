package com.tingyun.smartmistakebook.core.database.dao

import androidx.room3.ColumnInfo
import androidx.room3.Dao
import androidx.room3.Query

/**
 * D-M M1：机器可判题的题目事实读面。practice unit 是作答/提交的入口 id，
 * 题面与答案规格在其 problem revision 上（v2 起 assessment_item_snapshot 只是
 * 一条可选镜像，fixture 退场后不再作为提交路径的目录来源）。
 */
internal data class PracticeUnitAssessmentRow(
    @ColumnInfo(name = "practice_unit_id")
    val practiceUnitId: String,
    @ColumnInfo(name = "problem_id")
    val problemId: String,
    @ColumnInfo(name = "problem_revision_id")
    val problemRevisionId: String,
    val subject: String,
    @ColumnInfo(name = "unit_title")
    val unitTitle: String,
    @ColumnInfo(name = "prompt_markdown")
    val promptMarkdown: String,
    @ColumnInfo(name = "question_document_snapshot")
    val questionDocumentSnapshot: String?,
    @ColumnInfo(name = "answer_spec_id")
    val answerSpecId: String?,
    @ColumnInfo(name = "answer_spec_snapshot")
    val answerSpecSnapshot: String?,
    @ColumnInfo(name = "answer_verification_status")
    val answerVerificationStatus: String,
    @ColumnInfo(name = "source_type")
    val sourceType: String,
    @ColumnInfo(name = "source_reference")
    val sourceReference: String?,
    @ColumnInfo(name = "revision_created_at_epoch_millis")
    val revisionCreatedAtEpochMillis: Long,
)

@Dao
internal abstract class PracticeUnitAssessmentDao {
    @Query(
        """
        SELECT
            unit.practice_unit_id AS practice_unit_id,
            unit.problem_id AS problem_id,
            unit.problem_revision_id AS problem_revision_id,
            problem.subject AS subject,
            unit.title AS unit_title,
            unit.prompt_markdown AS prompt_markdown,
            revision.question_document_snapshot AS question_document_snapshot,
            revision.answer_spec_id AS answer_spec_id,
            revision.answer_spec_snapshot AS answer_spec_snapshot,
            revision.answer_verification_status AS answer_verification_status,
            revision.source_type AS source_type,
            revision.source_reference AS source_reference,
            revision.created_at_epoch_millis AS revision_created_at_epoch_millis
        FROM practice_unit AS unit
        JOIN problem AS problem
            ON problem.problem_id = unit.problem_id
        JOIN problem_revision AS revision
            ON revision.revision_id = unit.problem_revision_id
           AND revision.problem_id = unit.problem_id
        WHERE unit.practice_unit_id = :practiceUnitId
        LIMIT 1
        """,
    )
    abstract suspend fun readPracticeUnitAssessment(
        practiceUnitId: String,
    ): PracticeUnitAssessmentRow?
}
