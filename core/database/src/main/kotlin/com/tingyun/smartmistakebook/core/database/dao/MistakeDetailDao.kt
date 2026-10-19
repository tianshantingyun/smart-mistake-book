package com.tingyun.smartmistakebook.core.database.dao

import androidx.room3.ColumnInfo
import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import com.tingyun.smartmistakebook.core.database.CanonicalSourceAssetRecord
import com.tingyun.smartmistakebook.core.database.CleanRedrawTaskStatus
import com.tingyun.smartmistakebook.core.database.MistakeDetailRecord
import com.tingyun.smartmistakebook.core.database.MistakeDetailSourceAssetRecord
import com.tingyun.smartmistakebook.core.database.MistakeRevisionSummaryRecord
import com.tingyun.smartmistakebook.core.database.StudyDbValue
import com.tingyun.smartmistakebook.core.database.entity.ModelTaskEventEntity
import com.tingyun.smartmistakebook.core.database.entity.PracticeUnitEntity
import com.tingyun.smartmistakebook.core.database.entity.ProblemRevisionEntity
import com.tingyun.smartmistakebook.core.database.entity.ProblemRevisionSourceAssetEntity
import com.tingyun.smartmistakebook.core.database.port.ReviseCommittedProblemCommand
import com.tingyun.smartmistakebook.core.database.port.ReviseCommittedProblemResult
import com.tingyun.smartmistakebook.core.database.port.RevisedBasisInvalidation
import com.tingyun.smartmistakebook.core.database.port.RevisedProblemRevisionRecord
import kotlinx.coroutines.flow.Flow

internal data class MistakeDetailRow(
    @ColumnInfo(name = "entry_id")
    val entryId: String,
    @ColumnInfo(name = "problem_id")
    val problemId: String,
    @ColumnInfo(name = "problem_revision_id")
    val problemRevisionId: String,
    @ColumnInfo(name = "practice_unit_id")
    val practiceUnitId: String,
    @ColumnInfo(name = "revision_number")
    val revisionNumber: Int,
    val subject: String,
    val title: String,
    @ColumnInfo(name = "problem_markdown")
    val problemMarkdown: String,
    @ColumnInfo(name = "question_document_snapshot")
    val questionDocumentSnapshot: String?,
    @ColumnInfo(name = "content_fingerprint")
    val contentFingerprint: String,
    @ColumnInfo(name = "answer_spec_present")
    val answerSpecPresent: Boolean = false,
    @ColumnInfo(name = "tutor_session_id")
    val tutorSessionId: String?,
    @ColumnInfo(name = "tutor_question_revision_number")
    val tutorQuestionRevisionNumber: Int?,
    @ColumnInfo(name = "user_note")
    val userNote: String? = null,
    @ColumnInfo(name = "status")
    val entryStatus: String? = null,
)

internal data class EntryRevisionRow(
    @ColumnInfo(name = "entry_id")
    val entryId: String,
    @ColumnInfo(name = "current_revision_id")
    val currentRevisionId: String,
)

/** 归档条目摘要（id+标题）：错题本"已移出"入口的展示与恢复都要用。 */
data class ArchivedEntrySummaryRow(
    val entryId: String,
    val title: String,
)

internal data class MistakeDetailSourceAssetRow(
    val role: String,
    @ColumnInfo(name = "source_asset_id")
    val sourceAssetId: String,
    @ColumnInfo(name = "content_sha256")
    val contentSha256: String,
    @ColumnInfo(name = "relative_path")
    val relativePath: String,
    @ColumnInfo(name = "mime_type")
    val mimeType: String,
    @ColumnInfo(name = "byte_size")
    val byteSize: Long,
    val width: Int,
    val height: Int,
    @ColumnInfo(name = "source_type")
    val sourceType: String,
    @ColumnInfo(name = "created_at_epoch_millis")
    val createdAtEpochMillis: Long,
)

internal data class MistakeDetailBatchSourceAssetRow(
    @ColumnInfo(name = "problem_revision_id")
    val problemRevisionId: String,
    val role: String,
    @ColumnInfo(name = "source_asset_id")
    val sourceAssetId: String,
    @ColumnInfo(name = "content_sha256")
    val contentSha256: String,
    @ColumnInfo(name = "relative_path")
    val relativePath: String,
    @ColumnInfo(name = "mime_type")
    val mimeType: String,
    @ColumnInfo(name = "byte_size")
    val byteSize: Long,
    val width: Int,
    val height: Int,
    @ColumnInfo(name = "source_type")
    val sourceType: String,
    @ColumnInfo(name = "created_at_epoch_millis")
    val createdAtEpochMillis: Long,
)

internal data class MistakeRevisionSummaryRow(
    @ColumnInfo(name = "entry_id")
    val entryId: String,
    @ColumnInfo(name = "problem_id")
    val problemId: String,
    @ColumnInfo(name = "problem_revision_id")
    val problemRevisionId: String,
    @ColumnInfo(name = "revision_number")
    val revisionNumber: Int,
    val title: String,
    @ColumnInfo(name = "created_at_epoch_millis")
    val createdAtEpochMillis: Long,
    @ColumnInfo(name = "is_current")
    val isCurrent: Boolean,
)

/** Current head facts needed to CAS one controlled revision edit. */
internal data class RevisionHeadRow(
    @ColumnInfo(name = "problem_id")
    val problemId: String,
    @ColumnInfo(name = "current_revision_id")
    val currentRevisionId: String,
    @ColumnInfo(name = "practice_unit_id")
    val practiceUnitId: String,
    @ColumnInfo(name = "revision_number")
    val revisionNumber: Int,
    val title: String,
    @ColumnInfo(name = "content_fingerprint")
    val contentFingerprint: String,
    @ColumnInfo(name = "answer_spec_id")
    val answerSpecId: String?,
)

/** Existing revision found either by durable command receipt or by identical content. */
internal data class RevisionEditReceiptRow(
    @ColumnInfo(name = "revision_id")
    val revisionId: String,
    @ColumnInfo(name = "revision_number")
    val revisionNumber: Int,
    val title: String,
    @ColumnInfo(name = "content_fingerprint")
    val contentFingerprint: String,
    @ColumnInfo(name = "source_reference")
    val sourceReference: String?,
    @ColumnInfo(name = "created_at_epoch_millis")
    val createdAtEpochMillis: Long,
    @ColumnInfo(name = "answer_spec_id")
    val answerSpecId: String?,
)

internal data class RevisionSourceLinkRow(
    @ColumnInfo(name = "source_asset_id")
    val sourceAssetId: String,
    val role: String,
)

internal data class CancellableModelTaskRow(
    @ColumnInfo(name = "task_id")
    val taskId: String,
    @ColumnInfo(name = "state_version")
    val stateVersion: Long,
    val status: String,
    val stage: String,
    @ColumnInfo(name = "attempt_count")
    val attemptCount: Int,
    @ColumnInfo(name = "provider_snapshot")
    val providerSnapshot: String?,
)

@Dao
internal abstract class MistakeDetailDao {
    @Query(
        """
        UPDATE error_book_entry
        SET user_note = :note,
            updated_at_epoch_millis = :updatedAtEpochMillis
        WHERE entry_id = :entryId
        """,
    )
    protected abstract suspend fun updateEntryNote(
        entryId: String,
        note: String?,
        updatedAtEpochMillis: Long,
    ): Int

    open suspend fun setUserNote(
        entryId: String,
        note: String?,
        updatedAtEpochMillis: Long,
    ): Boolean {
        require(entryId.isNotBlank()) { "entryId must not be blank" }
        require(updatedAtEpochMillis >= 0) { "updatedAtEpochMillis must not be negative" }
        return updateEntryNote(entryId, note, updatedAtEpochMillis) == 1
    }

    /**
     * C11 收藏的行级开关：只改这一行（收藏是条目的私有状态，不写修订链、不碰其它列）。
     * 返回是否命中一行——调用方必须如实报失败，不能把没写进去当成写成了。
     */
    @Query(
        """
        UPDATE error_book_entry
        SET is_favorite = :isFavorite,
            updated_at_epoch_millis = :updatedAtEpochMillis
        WHERE entry_id = :entryId
        """,
    )
    protected abstract suspend fun updateEntryFavorite(
        entryId: String,
        isFavorite: Boolean,
        updatedAtEpochMillis: Long,
    ): Int

    open suspend fun setEntryFavorite(
        entryId: String,
        isFavorite: Boolean,
        updatedAtEpochMillis: Long,
    ): Boolean {
        require(entryId.isNotBlank()) { "entryId must not be blank" }
        require(updatedAtEpochMillis >= 0) { "updatedAtEpochMillis must not be negative" }
        return updateEntryFavorite(entryId, isFavorite, updatedAtEpochMillis) == 1
    }

    @Query(
        """
        UPDATE error_book_entry
        SET status = :status,
            updated_at_epoch_millis = :updatedAtEpochMillis
        WHERE entry_id = :entryId
        """,
    )
    protected abstract suspend fun updateEntryStatus(
        entryId: String,
        status: String,
        updatedAtEpochMillis: Long,
    ): Int

    open suspend fun setEntryStatus(
        entryId: String,
        status: String,
        updatedAtEpochMillis: Long,
    ): Boolean {
        require(entryId.isNotBlank()) { "entryId must not be blank" }
        require(updatedAtEpochMillis >= 0) { "updatedAtEpochMillis must not be negative" }
        return updateEntryStatus(entryId, status, updatedAtEpochMillis) == 1
    }

    @Query(
        """
        SELECT entry_id, current_revision_id
        FROM error_book_entry
        WHERE entry_id = :entryId
        LIMIT 1
        """,
    )
    protected abstract suspend fun findEntryRevision(entryId: String): EntryRevisionRow?

    open suspend fun readEntryRevision(entryId: String): EntryRevisionRow? =
        findEntryRevision(entryId)

    @Query(
        """
        SELECT catalog.entry_id AS entryId, revision.title AS title
        FROM error_book_entry AS catalog
        JOIN problem_revision AS revision
          ON revision.problem_id = catalog.problem_id
         AND revision.revision_id = catalog.current_revision_id
        WHERE catalog.status = 'ARCHIVED'
        ORDER BY catalog.updated_at_epoch_millis DESC
        """,
    )
    protected abstract fun observeArchivedEntryIds(): Flow<List<ArchivedEntrySummaryRow>>

    open fun archivedEntries(): Flow<List<ArchivedEntrySummaryRow>> = observeArchivedEntryIds()

    @Query(
        """
        SELECT
            entry.entry_id,
            entry.problem_id,
            revision.revision_id AS problem_revision_id,
            entry.practice_unit_id,
            revision.revision_number,
            problem.subject,
            revision.title,
            revision.problem_markdown,
            revision.question_document_snapshot,
            revision.content_fingerprint,
            CASE WHEN revision.answer_spec_id IS NOT NULL THEN 1 ELSE 0 END AS answer_spec_present,
            entry.user_note,
            entry.status AS entry_status,
            tutor.anchor_id AS tutor_session_id,
            tutor.capture_draft_revision_number AS tutor_question_revision_number
        FROM error_book_entry AS entry
        JOIN problem AS problem
            ON problem.problem_id = entry.problem_id
        JOIN problem_revision AS revision
            ON revision.revision_id = entry.current_revision_id
            AND revision.problem_id = entry.problem_id
        LEFT JOIN problem_draft_commit_receipt AS receipt
            ON receipt.problem_revision_id = revision.revision_id
        LEFT JOIN tutor_conversation AS tutor
            ON tutor.capture_draft_id = receipt.draft_id
            AND tutor.capture_draft_revision_number = receipt.draft_revision_number
        WHERE entry.entry_id = :errorBookEntryId
        LIMIT 1
        """,
    )
    protected abstract suspend fun findDetail(errorBookEntryId: String): MistakeDetailRow?

    @Query(
        """
        SELECT
            entry.entry_id,
            entry.problem_id,
            revision.revision_id AS problem_revision_id,
            entry.practice_unit_id,
            revision.revision_number,
            problem.subject,
            revision.title,
            revision.problem_markdown,
            revision.question_document_snapshot,
            revision.content_fingerprint,
            CASE WHEN revision.answer_spec_id IS NOT NULL THEN 1 ELSE 0 END AS answer_spec_present,
            entry.user_note,
            entry.status AS entry_status,
            tutor.anchor_id AS tutor_session_id,
            tutor.capture_draft_revision_number AS tutor_question_revision_number
        FROM error_book_entry AS entry
        JOIN problem AS problem
            ON problem.problem_id = entry.problem_id
        JOIN problem_revision AS revision
            ON revision.revision_id = :problemRevisionId
            AND revision.problem_id = entry.problem_id
        LEFT JOIN problem_draft_commit_receipt AS receipt
            ON receipt.problem_revision_id = revision.revision_id
        LEFT JOIN tutor_conversation AS tutor
            ON tutor.capture_draft_id = receipt.draft_id
            AND tutor.capture_draft_revision_number = receipt.draft_revision_number
        WHERE entry.entry_id = :entryId
          AND entry.problem_id = :problemId
        LIMIT 1
        """,
    )
    protected abstract suspend fun findExactDetail(
        entryId: String,
        problemId: String,
        problemRevisionId: String,
    ): MistakeDetailRow?

    @Query(
        """
        SELECT
            entry.entry_id,
            entry.problem_id,
            revision.revision_id AS problem_revision_id,
            entry.practice_unit_id,
            revision.revision_number,
            problem.subject,
            revision.title,
            revision.problem_markdown,
            revision.question_document_snapshot,
            revision.content_fingerprint,
            CASE WHEN revision.answer_spec_id IS NOT NULL THEN 1 ELSE 0 END AS answer_spec_present,
            entry.user_note,
            entry.status AS entry_status,
            NULL AS tutor_session_id,
            NULL AS tutor_question_revision_number
        FROM error_book_entry AS entry
        JOIN problem AS problem
            ON problem.problem_id = entry.problem_id
        JOIN problem_revision AS revision
            ON revision.revision_id = entry.current_revision_id
            AND revision.problem_id = entry.problem_id
        WHERE entry.entry_id IN (:entryIds)
        """,
    )
    protected abstract suspend fun findCurrentDetails(
        entryIds: List<String>,
    ): List<MistakeDetailRow>

    @Query(
        """
        SELECT
            link.role,
            asset.source_asset_id,
            asset.content_sha256,
            asset.relative_path,
            asset.mime_type,
            asset.byte_size,
            asset.width,
            asset.height,
            asset.source_type,
            asset.created_at_epoch_millis
        FROM problem_revision_source_asset AS link
        JOIN canonical_source_asset AS asset
            ON asset.source_asset_id = link.source_asset_id
        WHERE link.problem_revision_id = :problemRevisionId
        ORDER BY link.role ASC, asset.source_asset_id ASC
        """,
    )
    protected abstract suspend fun findSourceAssets(
        problemRevisionId: String,
    ): List<MistakeDetailSourceAssetRow>

    @Query(
        """
        SELECT
            link.problem_revision_id,
            link.role,
            asset.source_asset_id,
            asset.content_sha256,
            asset.relative_path,
            asset.mime_type,
            asset.byte_size,
            asset.width,
            asset.height,
            asset.source_type,
            asset.created_at_epoch_millis
        FROM problem_revision_source_asset AS link
        JOIN canonical_source_asset AS asset
            ON asset.source_asset_id = link.source_asset_id
        WHERE link.problem_revision_id IN (:problemRevisionIds)
        ORDER BY link.problem_revision_id ASC, link.role ASC, asset.source_asset_id ASC
        """,
    )
    protected abstract suspend fun findSourceAssetsForRevisions(
        problemRevisionIds: List<String>,
    ): List<MistakeDetailBatchSourceAssetRow>

    @Query(
        """
        SELECT
            entry.entry_id,
            revision.problem_id,
            revision.revision_id AS problem_revision_id,
            revision.revision_number,
            revision.title,
            revision.created_at_epoch_millis,
            revision.revision_id = entry.current_revision_id AS is_current
        FROM error_book_entry AS entry
        JOIN problem_revision AS revision
            ON revision.problem_id = entry.problem_id
        WHERE entry.entry_id = :errorBookEntryId
        ORDER BY revision.revision_number DESC
        """,
    )
    protected abstract suspend fun findRevisionHistory(
        errorBookEntryId: String,
    ): List<MistakeRevisionSummaryRow>

    @Transaction
    open suspend fun read(errorBookEntryId: String): MistakeDetailRecord? {
        val detail = findDetail(errorBookEntryId) ?: return null
        return detail.toRecord(findSourceAssets(detail.problemRevisionId))
    }

    @Transaction
    open suspend fun readExact(
        entryId: String,
        problemId: String,
        problemRevisionId: String,
    ): MistakeDetailRecord? {
        val detail = findExactDetail(entryId, problemId, problemRevisionId) ?: return null
        return detail.toRecord(findSourceAssets(detail.problemRevisionId))
    }

    @Transaction
    open suspend fun readCurrentBatch(entryIds: List<String>): List<MistakeDetailRecord> {
        require(entryIds.size <= MAX_BATCH_SIZE) { "Mistake-detail batch is too large" }
        if (entryIds.isEmpty()) return emptyList()
        val details = findCurrentDetails(entryIds.distinct())
        val assetsByRevision = findSourceAssetsForRevisions(
            details.map(MistakeDetailRow::problemRevisionId).distinct(),
        ).groupBy(MistakeDetailBatchSourceAssetRow::problemRevisionId)
        return details.map { detail ->
            detail.toRecord(
                assetsByRevision[detail.problemRevisionId]
                    .orEmpty()
                    .map(MistakeDetailBatchSourceAssetRow::toSourceAssetRow),
            )
        }
    }

    open suspend fun readRevisionHistory(
        errorBookEntryId: String,
    ): List<MistakeRevisionSummaryRecord> = findRevisionHistory(errorBookEntryId).map { row ->
        MistakeRevisionSummaryRecord(
            entryId = row.entryId,
            problemId = row.problemId,
            problemRevisionId = row.problemRevisionId,
            revisionNumber = row.revisionNumber,
            title = row.title,
            createdAtEpochMillis = row.createdAtEpochMillis,
            isCurrent = row.isCurrent,
        )
    }

    // ------------------------------------------------------------------
    // Controlled revision edit (PRD-03)
    // ------------------------------------------------------------------

    @Query(
        """
        SELECT entry.problem_id,
               entry.current_revision_id,
               entry.practice_unit_id,
               revision.revision_number,
               revision.title,
               revision.content_fingerprint,
               revision.answer_spec_id
        FROM error_book_entry AS entry
        JOIN problem_revision AS revision
            ON revision.revision_id = entry.current_revision_id
           AND revision.problem_id = entry.problem_id
        WHERE entry.entry_id = :entryId
        LIMIT 1
        """,
    )
    protected abstract suspend fun findRevisionHead(entryId: String): RevisionHeadRow?

    @Query(
        """
        SELECT revision_id,
               revision_number,
               title,
               content_fingerprint,
               source_reference,
               created_at_epoch_millis,
               answer_spec_id
        FROM problem_revision
        WHERE problem_id = :problemId
          AND source_reference = :sourceReference
        LIMIT 1
        """,
    )
    protected abstract suspend fun findRevisionByReceipt(
        problemId: String,
        sourceReference: String,
    ): RevisionEditReceiptRow?

    /**
     * Any revision already carrying this command id (with any payload). Used only to turn a
     * reused command id into an explicit conflict instead of a silent second write.
     */
    @Query(
        """
        SELECT revision_id,
               revision_number,
               title,
               content_fingerprint,
               source_reference,
               created_at_epoch_millis,
               answer_spec_id
        FROM problem_revision
        WHERE problem_id = :problemId
          AND source_reference LIKE :commandPrefix
        ORDER BY revision_number ASC
        LIMIT 1
        """,
    )
    protected abstract suspend fun findRevisionByCommandPrefix(
        problemId: String,
        commandPrefix: String,
    ): RevisionEditReceiptRow?

    @Query(
        """
        SELECT revision_id,
               revision_number,
               title,
               content_fingerprint,
               source_reference,
               created_at_epoch_millis,
               answer_spec_id
        FROM problem_revision
        WHERE problem_id = :problemId
          AND content_fingerprint = :contentFingerprint
        LIMIT 1
        """,
    )
    protected abstract suspend fun findRevisionByContent(
        problemId: String,
        contentFingerprint: String,
    ): RevisionEditReceiptRow?

    @Query(
        """
        SELECT answer_spec_id
        FROM problem_revision
        WHERE problem_id = :problemId AND revision_id = :revisionId
        LIMIT 1
        """,
    )
    protected abstract suspend fun findRevisionAnswerSpecId(
        problemId: String,
        revisionId: String,
    ): String?

    @Query(
        "SELECT estimated_seconds FROM practice_unit WHERE practice_unit_id = :practiceUnitId LIMIT 1",
    )
    protected abstract suspend fun findPracticeUnitEstimatedSeconds(
        practiceUnitId: String,
    ): Int?

    /**
     * The practice unit that already belongs to an exact revision. Used when an edit reverts to a
     * historical revision so its original schedule/evidence pair is reused instead of attaching
     * the old content to the newer unit.
     */
    @Query(
        """
        SELECT practice_unit_id
        FROM practice_unit
        WHERE problem_revision_id = :revisionId
          AND unit_key = 'whole-problem'
        LIMIT 1
        """,
    )
    protected abstract suspend fun findPracticeUnitForRevision(revisionId: String): String?

    @Insert
    protected abstract suspend fun insertPracticeUnit(entity: PracticeUnitEntity)

    @Insert
    protected abstract suspend fun insertProblemRevision(entity: ProblemRevisionEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertRevisionSourceAsset(
        entity: ProblemRevisionSourceAssetEntity,
    ): Long

    @Query(
        """
        SELECT source_asset_id, role
        FROM problem_revision_source_asset
        WHERE problem_revision_id = :revisionId
        ORDER BY role ASC, source_asset_id ASC
        """,
    )
    protected abstract suspend fun findRevisionSourceLinks(
        revisionId: String,
    ): List<RevisionSourceLinkRow>

    @Query(
        """
        UPDATE error_book_entry
        SET current_revision_id = :nextRevisionId,
            practice_unit_id = :nextPracticeUnitId,
            updated_at_epoch_millis = :updatedAtEpochMillis
        WHERE entry_id = :entryId
          AND problem_id = :problemId
          AND current_revision_id = :expectedRevisionId
          AND practice_unit_id = :expectedPracticeUnitId
        """,
    )
    protected abstract suspend fun advanceEntryRevision(
        entryId: String,
        problemId: String,
        expectedRevisionId: String,
        expectedPracticeUnitId: String,
        nextRevisionId: String,
        nextPracticeUnitId: String,
        updatedAtEpochMillis: Long,
    ): Int

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
    protected abstract suspend fun markRevisionRelationsStale(
        problemRevisionId: String,
        staleStatus: String,
        updatedAtEpochMillis: Long,
    ): Int

    @Query(
        """
        SELECT task_id, state_version, status, stage, attempt_count, provider_snapshot
        FROM model_task
        WHERE subject_id = :subjectId
          AND task_kind = :taskKind
          AND status IN (:statuses)
        ORDER BY created_at_epoch_millis ASC, task_id ASC
        """,
    )
    protected abstract suspend fun findCancellableModelTasks(
        subjectId: String,
        taskKind: String,
        statuses: List<String>,
    ): List<CancellableModelTaskRow>

    @Query(
        """
        UPDATE model_task
        SET status = :nextStatus,
            state_version = state_version + 1,
            user_message = :message,
            failure_code = NULL,
            failure_message = NULL,
            failure_retryable = NULL,
            updated_at_epoch_millis = :updatedAtEpochMillis
        WHERE task_id = :taskId
          AND state_version = :expectedStateVersion
          AND status = :expectedStatus
        """,
    )
    protected abstract suspend fun cancelModelTask(
        taskId: String,
        expectedStateVersion: Long,
        expectedStatus: String,
        nextStatus: String,
        message: String,
        updatedAtEpochMillis: Long,
    ): Int

    @Insert
    protected abstract suspend fun insertModelTaskEvent(entity: ModelTaskEventEntity)

    @Query(
        """
        UPDATE clean_redraw_task
        SET status = :supersededStatus,
            last_failure_code = NULL,
            updated_at_epoch_millis = :updatedAtEpochMillis
        WHERE revision_id = :revisionId
          AND status IN (:statuses)
        """,
    )
    protected abstract suspend fun supersedeCleanRedrawTasks(
        revisionId: String,
        supersededStatus: String,
        statuses: List<String>,
        updatedAtEpochMillis: Long,
    ): Int

    /**
     * One controlled edit: compare-and-swap the head, append an immutable revision, copy the
     * original-photo links, and invalidate everything anchored to the previous basis.
     *
     * **`practice_unit.problem_revision_id` is deliberately left on the old revision.** Historical
     * `assessment_evidence_snapshot`/attempt rows have a RESTRICT foreign key to
     * `(practice_unit_id, problem_revision_id)`; rewriting that pair would either fail or orphan
     * student history. Every user-facing read (catalog view, detail, review content, export
     * identity) follows `error_book_entry.current_revision_id`, so the next review/export uses the
     * new revision while old answers stay readable against the old one. The new revision carries no
     * answer spec, so old grading can never be applied to it.
     */
    @Transaction
    open suspend fun reviseCommittedProblem(
        command: ReviseCommittedProblemCommand,
    ): ReviseCommittedProblemResult {
        val head = findRevisionHead(command.entryId)
            ?: return ReviseCommittedProblemResult.NotFound

        // 1) Durable idempotency: the exact command token already produced a revision. The
        // payload must still match the stored revision; a token collision with different content
        // is a reused command, not a replay.
        findRevisionByReceipt(head.problemId, command.sourceReference)?.let { existing ->
            if (
                existing.contentFingerprint != command.contentFingerprint ||
                existing.title != command.title
            ) {
                return ReviseCommittedProblemResult.DuplicateCommandPayloadConflict(
                    command.commandId,
                )
            }
            return ReviseCommittedProblemResult.Saved(
                record = existing.toRevisedRecord(
                    entryId = command.entryId,
                    problemId = head.problemId,
                    previousRevisionId = command.expectedRevisionId,
                    previousRevisionNumber = command.expectedRevisionNumber,
                    invalidation = RevisedBasisInvalidation(),
                ),
                created = false,
            )
        }
        // 2) Same command id, different payload: never write a second revision silently.
        findRevisionByCommandPrefix(
            head.problemId,
            ReviseCommittedProblemCommand.receiptPrefix(command.commandId) + "%",
        )?.let {
            return ReviseCommittedProblemResult.DuplicateCommandPayloadConflict(command.commandId)
        }

        // 3) CAS: only the caller's expected head may advance.
        if (
            head.currentRevisionId != command.expectedRevisionId ||
            head.revisionNumber != command.expectedRevisionNumber
        ) {
            return ReviseCommittedProblemResult.Conflict(
                expectedRevisionId = command.expectedRevisionId,
                expectedRevisionNumber = command.expectedRevisionNumber,
                currentRevisionId = head.currentRevisionId,
                currentRevisionNumber = head.revisionNumber,
                currentTitle = head.title,
            )
        }

        // 4) A prior immutable revision may already hold exactly this content. Reuse it instead
        // of violating the (problem_id, content_fingerprint) unique index; this is a controlled
        // revert, not a rewrite of history.
        findRevisionByContent(head.problemId, command.contentFingerprint)?.let { existing ->
            if (existing.revisionId == head.currentRevisionId) {
                return ReviseCommittedProblemResult.Unchanged(
                    record = existing.toRevisedRecord(
                        entryId = command.entryId,
                        problemId = head.problemId,
                        previousRevisionId = head.currentRevisionId,
                        previousRevisionNumber = head.revisionNumber,
                        invalidation = RevisedBasisInvalidation(),
                    ),
                )
            }
            val revertPracticeUnitId = resolveRevertPracticeUnit(
                head = head,
                targetRevisionId = existing.revisionId,
                command = command,
            )
            check(
                advanceEntryRevision(
                    entryId = command.entryId,
                    problemId = head.problemId,
                    expectedRevisionId = command.expectedRevisionId,
                    expectedPracticeUnitId = head.practiceUnitId,
                    nextRevisionId = existing.revisionId,
                    nextPracticeUnitId = revertPracticeUnitId,
                    updatedAtEpochMillis = command.revisedAtEpochMillis,
                ) == 1,
            ) { "The error-book entry head changed during a revision edit" }
            val invalidation = invalidateOldBasis(
                previousRevisionId = command.expectedRevisionId,
                at = command.revisedAtEpochMillis,
            )
            return ReviseCommittedProblemResult.Saved(
                record = existing.toRevisedRecord(
                    entryId = command.entryId,
                    problemId = head.problemId,
                    previousRevisionId = command.expectedRevisionId,
                    previousRevisionNumber = command.expectedRevisionNumber,
                    invalidation = invalidation,
                ),
                created = false,
            )
        }

        // 5) Append the new immutable revision.
        insertProblemRevision(
            ProblemRevisionEntity(
                revisionId = command.newRevisionId,
                problemId = head.problemId,
                revisionNumber = head.revisionNumber + 1,
                title = command.title,
                problemMarkdown = command.problemMarkdown,
                questionDocumentSnapshot = command.questionDocumentSnapshot,
                answerSpecId = null,
                answerSpecSnapshot = null,
                answerVerificationStatus = StudyDbValue.VerificationStatus.UNKNOWN,
                sourceType = USER_REVISION_SOURCE_TYPE,
                sourceReference = command.sourceReference,
                contentFingerprint = command.contentFingerprint,
                createdAtEpochMillis = command.revisedAtEpochMillis,
            ),
        )
        // Keep the original photos visible on the new revision; generated clean redraws belong to
        // the old basis and stay only on the historical revision.
        findRevisionSourceLinks(command.expectedRevisionId)
            .filterNot { link -> link.role == CLEAN_IMAGE_SOURCE_ROLE }
            .forEach { link ->
                insertRevisionSourceAsset(
                    ProblemRevisionSourceAssetEntity(
                        problemRevisionId = command.newRevisionId,
                        sourceAssetId = link.sourceAssetId,
                        role = link.role,
                    ),
                )
            }
        // A question whose old revision was machine-checkable must not keep using that revision's
        // teaching artifact for the new content. It gets a fresh practice unit (no answer spec,
        // no memory state), so the old verified answer cannot be applied. Captured questions
        // without an answer spec keep their unit so the review schedule and history stay attached.
        val nextPracticeUnitId = if (head.answerSpecId != null) {
            ensurePracticeUnitForRevision(
                head = head,
                revisionId = command.newRevisionId,
                title = command.title,
                problemMarkdown = command.problemMarkdown,
                at = command.revisedAtEpochMillis,
            )
        } else {
            head.practiceUnitId
        }
        check(
            advanceEntryRevision(
                entryId = command.entryId,
                problemId = head.problemId,
                expectedRevisionId = command.expectedRevisionId,
                expectedPracticeUnitId = head.practiceUnitId,
                nextRevisionId = command.newRevisionId,
                nextPracticeUnitId = nextPracticeUnitId,
                updatedAtEpochMillis = command.revisedAtEpochMillis,
            ) == 1,
        ) { "The error-book entry head changed during a revision edit" }

        val invalidation = invalidateOldBasis(
            previousRevisionId = command.expectedRevisionId,
            at = command.revisedAtEpochMillis,
        )
        return ReviseCommittedProblemResult.Saved(
            record = RevisedProblemRevisionRecord(
                entryId = command.entryId,
                problemId = head.problemId,
                problemRevisionId = command.newRevisionId,
                revisionNumber = head.revisionNumber + 1,
                previousRevisionId = command.expectedRevisionId,
                previousRevisionNumber = command.expectedRevisionNumber,
                title = command.title,
                contentFingerprint = command.contentFingerprint,
                createdAtEpochMillis = command.revisedAtEpochMillis,
                answerSpecPresent = false,
                previousAnswerSpecPresent =
                    findRevisionAnswerSpecId(head.problemId, command.expectedRevisionId) != null,
                invalidation = invalidation,
            ),
            created = true,
        )
    }

    private suspend fun resolveRevertPracticeUnit(
        head: RevisionHeadRow,
        targetRevisionId: String,
        command: ReviseCommittedProblemCommand,
    ): String {
        findPracticeUnitForRevision(targetRevisionId)?.let { return it }
        return if (head.answerSpecId != null) {
            ensurePracticeUnitForRevision(
                head = head,
                revisionId = targetRevisionId,
                title = command.title,
                problemMarkdown = command.problemMarkdown,
                at = command.revisedAtEpochMillis,
            )
        } else {
            head.practiceUnitId
        }
    }

    /**
     * Creates (or reuses) the `whole-problem` unit for an exact revision. The unit is what
     * `readPracticeUnitAssessment(practiceUnitId)` follows; pointing it at the new revision is what
     * makes the old verified answer spec unreachable from the new content.
     */
    private suspend fun ensurePracticeUnitForRevision(
        head: RevisionHeadRow,
        revisionId: String,
        title: String,
        problemMarkdown: String,
        at: Long,
    ): String {
        findPracticeUnitForRevision(revisionId)?.let { return it }
        val practiceUnitId = "practice-edit-$revisionId"
        insertPracticeUnit(
            PracticeUnitEntity(
                practiceUnitId = practiceUnitId,
                problemId = head.problemId,
                problemRevisionId = revisionId,
                unitKey = WHOLE_PROBLEM_UNIT_KEY,
                unitKind = WHOLE_PROBLEM_UNIT_KIND,
                title = title,
                promptMarkdown = problemMarkdown,
                estimatedSeconds = findPracticeUnitEstimatedSeconds(head.practiceUnitId)
                    ?: DEFAULT_ESTIMATED_SECONDS,
                createdAtEpochMillis = at,
            ),
        )
        return practiceUnitId
    }

    private suspend fun invalidateOldBasis(
        previousRevisionId: String,
        at: Long,
    ): RevisedBasisInvalidation {
        val staleRelations = markRevisionRelationsStale(
            problemRevisionId = previousRevisionId,
            staleStatus = StudyDbValue.RelationStatus.STALE,
            updatedAtEpochMillis = at,
        )
        val cancelledTasks = cancelInFlightOrganizationTasks(previousRevisionId, at)
        val supersededRedraws = supersedeCleanRedrawTasks(
            revisionId = previousRevisionId,
            supersededStatus = CleanRedrawTaskStatus.SUPERSEDED,
            statuses = listOf(CleanRedrawTaskStatus.PENDING, CleanRedrawTaskStatus.FAILED),
            updatedAtEpochMillis = at,
        )
        return RevisedBasisInvalidation(
            cancelledModelTaskCount = cancelledTasks,
            supersededCleanRedrawCount = supersededRedraws,
            staleRelationCount = staleRelations,
        )
    }

    /**
     * Organization requests use `subject_id = problem_revision_id`. Cancelling the unfinished
     * rows through their `state_version` CAS means a late provider response can no longer advance
     * the task to SUCCEEDED and attach old-basis output to the new question. The event row keeps
     * the append-only audit trail complete.
     */
    private suspend fun cancelInFlightOrganizationTasks(
        previousRevisionId: String,
        at: Long,
    ): Int {
        var cancelled = 0
        findCancellableModelTasks(
            subjectId = previousRevisionId,
            taskKind = MODEL_TASK_KIND_PROBLEM_CLASSIFY,
            statuses = NON_TERMINAL_MODEL_STATUSES,
        ).forEach { row ->
            val updated = cancelModelTask(
                taskId = row.taskId,
                expectedStateVersion = row.stateVersion,
                expectedStatus = row.status,
                nextStatus = MODEL_TASK_STATUS_CANCELLED,
                message = REVISION_SUPERSEDED_MESSAGE,
                updatedAtEpochMillis = at,
            )
            if (updated == 1) {
                insertModelTaskEvent(
                    ModelTaskEventEntity(
                        taskId = row.taskId,
                        stateVersion = row.stateVersion + 1,
                        previousStatus = row.status,
                        nextStatus = MODEL_TASK_STATUS_CANCELLED,
                        stage = row.stage,
                        userMessage = REVISION_SUPERSEDED_MESSAGE,
                        attemptCount = row.attemptCount,
                        providerSnapshot = row.providerSnapshot,
                        outputSnapshot = null,
                        failureCode = null,
                        failureMessage = null,
                        failureRetryable = null,
                        createdAtEpochMillis = at,
                    ),
                )
                cancelled += 1
            }
        }
        return cancelled
    }

    private suspend fun RevisionEditReceiptRow.toRevisedRecord(
        entryId: String,
        problemId: String,
        previousRevisionId: String,
        previousRevisionNumber: Int,
        invalidation: RevisedBasisInvalidation,
    ) = RevisedProblemRevisionRecord(
        entryId = entryId,
        problemId = problemId,
        problemRevisionId = revisionId,
        revisionNumber = revisionNumber,
        previousRevisionId = previousRevisionId,
        previousRevisionNumber = previousRevisionNumber,
        title = title,
        contentFingerprint = contentFingerprint,
        createdAtEpochMillis = createdAtEpochMillis,
        answerSpecPresent = answerSpecId != null,
        previousAnswerSpecPresent =
            findRevisionAnswerSpecId(problemId, previousRevisionId) != null,
        invalidation = invalidation,
    )

    private companion object {
        const val MAX_BATCH_SIZE = 100
        const val USER_REVISION_SOURCE_TYPE = "USER_REVISION"
        const val CLEAN_IMAGE_SOURCE_ROLE = "CLEAN_IMAGE"
        const val WHOLE_PROBLEM_UNIT_KEY = "whole-problem"
        const val WHOLE_PROBLEM_UNIT_KIND = "WHOLE_PROBLEM"
        const val DEFAULT_ESTIMATED_SECONDS = 180
        const val MODEL_TASK_KIND_PROBLEM_CLASSIFY = "PROBLEM_CLASSIFY"
        const val MODEL_TASK_STATUS_CANCELLED = "CANCELLED"
        const val REVISION_SUPERSEDED_MESSAGE =
            "题面已修改，这条基于旧版题面的任务已失效"
        val NON_TERMINAL_MODEL_STATUSES = listOf(
            "WAITING_FOR_MODEL",
            "QUEUED",
            "RUNNING",
            "STREAMING",
            "RETRYABLE_FAILURE",
        )
    }
}

private fun MistakeDetailBatchSourceAssetRow.toSourceAssetRow() = MistakeDetailSourceAssetRow(
    role = role,
    sourceAssetId = sourceAssetId,
    contentSha256 = contentSha256,
    relativePath = relativePath,
    mimeType = mimeType,
    byteSize = byteSize,
    width = width,
    height = height,
    sourceType = sourceType,
    createdAtEpochMillis = createdAtEpochMillis,
)

private fun MistakeDetailRow.toRecord(
    sourceAssets: List<MistakeDetailSourceAssetRow>,
) = MistakeDetailRecord(
    entryId = entryId,
    problemId = problemId,
    problemRevisionId = problemRevisionId,
    practiceUnitId = practiceUnitId,
    revisionNumber = revisionNumber,
    subject = subject,
    title = title,
    problemMarkdown = problemMarkdown,
    questionDocumentSnapshot = questionDocumentSnapshot,
    contentFingerprint = contentFingerprint,
    answerSpecPresent = answerSpecPresent,
    sourceAssets = sourceAssets.map { row ->
        MistakeDetailSourceAssetRecord(
            role = row.role,
            sourceAsset = CanonicalSourceAssetRecord(
                sourceAssetId = row.sourceAssetId,
                contentSha256 = row.contentSha256,
                relativePath = row.relativePath,
                mimeType = row.mimeType,
                byteSize = row.byteSize,
                width = row.width,
                height = row.height,
                sourceType = row.sourceType,
                createdAtEpochMillis = row.createdAtEpochMillis,
            ),
        )
    },
    tutorSessionId = tutorSessionId,
    tutorQuestionRevisionNumber = tutorQuestionRevisionNumber,
    userNote = userNote,
    archived = entryStatus == com.tingyun.smartmistakebook.core.database.StudyDbValue.ErrorBookStatus.ARCHIVED,
)
