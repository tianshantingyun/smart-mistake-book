package com.tingyun.smartmistakebook.core.database.port

/**
 * Write port for correcting the question text of an already saved mistake.
 *
 * This is deliberately separate from [MistakeReadPort]: reading a detail must stay available on
 * every database build, while writing a new immutable revision is a narrow, CAS-guarded command
 * whose durable idempotency token lives on the revision row itself (no extra schema).
 */
interface MistakeRevisionWritePort {
    /**
     * Appends (or reuses) one immutable revision and advances `error_book_entry.current_revision_id`.
     *
     * Guarantees, all inside one write transaction:
     * - the current head must equal [ReviseCommittedProblemCommand.expectedRevisionId] and
     *   [ReviseCommittedProblemCommand.expectedRevisionNumber], otherwise [ReviseCommittedProblemResult.Conflict];
     * - the same [ReviseCommittedProblemCommand.commandId] + payload returns the existing receipt
     *   without writing;
     * - the same command id with a different payload is an explicit
     *   [ReviseCommittedProblemResult.DuplicateCommandPayloadConflict];
     * - original photo links are copied to the new revision; generated (`CLEAN_IMAGE`) links are
     *   not, because they belong to the old basis;
     * - relations, unfinished organization model tasks, and pending clean-redraw jobs that were
     *   anchored to the previous revision are marked stale/superseded. Historical rows are never
     *   deleted or rewritten into the new revision.
     */
    suspend fun reviseCommittedProblem(
        command: ReviseCommittedProblemCommand,
    ): ReviseCommittedProblemResult
}

data class ReviseCommittedProblemCommand(
    val commandId: String,
    /** Durable idempotency token stored in `problem_revision.source_reference`. */
    val sourceReference: String,
    val entryId: String,
    val expectedRevisionId: String,
    val expectedRevisionNumber: Int,
    val newRevisionId: String,
    val title: String,
    val problemMarkdown: String,
    val questionDocumentSnapshot: String,
    val contentFingerprint: String,
    val revisedAtEpochMillis: Long,
) {
    init {
        // The id becomes a LIKE prefix, so it must not carry wildcard characters.
        require(commandId.matches(COMMAND_ID_PATTERN)) {
            "commandId must be a stable identifier"
        }
        require(sourceReference.startsWith(receiptPrefix(commandId))) {
            "sourceReference must carry the command id"
        }
        require(entryId.isNotBlank()) { "entryId must not be blank" }
        require(expectedRevisionId.isNotBlank()) { "expectedRevisionId must not be blank" }
        require(expectedRevisionNumber > 0) { "expectedRevisionNumber must be positive" }
        require(newRevisionId.isNotBlank()) { "newRevisionId must not be blank" }
        require(title.isNotBlank()) { "title must not be blank" }
        require(problemMarkdown.isNotBlank()) { "problemMarkdown must not be blank" }
        require(questionDocumentSnapshot.isNotBlank()) {
            "questionDocumentSnapshot must not be blank"
        }
        require(contentFingerprint.length == 64) { "contentFingerprint must be sha256" }
        require(revisedAtEpochMillis >= 0) { "revisedAtEpochMillis must not be negative" }
    }

    companion object {
        const val RECEIPT_PREFIX = "revision-edit:"

        fun receiptPrefix(commandId: String): String = "$RECEIPT_PREFIX$commandId:"

        private val COMMAND_ID_PATTERN = Regex("[A-Za-z0-9.-]{1,128}")
    }
}

/** Invalidation counts observed by the edit transaction (evidence for the UI and tests). */
data class RevisedBasisInvalidation(
    val cancelledModelTaskCount: Int = 0,
    val supersededCleanRedrawCount: Int = 0,
    val staleRelationCount: Int = 0,
)

data class RevisedProblemRevisionRecord(
    val entryId: String,
    val problemId: String,
    val problemRevisionId: String,
    val revisionNumber: Int,
    val previousRevisionId: String,
    val previousRevisionNumber: Int,
    val title: String,
    val contentFingerprint: String,
    val createdAtEpochMillis: Long,
    val answerSpecPresent: Boolean,
    /**
     * Whether the revision this edit replaced carried an answer spec. The new revision never
     * inherits it; the flag drives the "旧答案需要重新核验" diff row.
     */
    val previousAnswerSpecPresent: Boolean = false,
    val invalidation: RevisedBasisInvalidation = RevisedBasisInvalidation(),
)

sealed interface ReviseCommittedProblemResult {
    data class Saved(
        val record: RevisedProblemRevisionRecord,
        val created: Boolean,
    ) : ReviseCommittedProblemResult

    data class Unchanged(
        val record: RevisedProblemRevisionRecord,
    ) : ReviseCommittedProblemResult

    data class Conflict(
        val expectedRevisionId: String,
        val expectedRevisionNumber: Int,
        val currentRevisionId: String?,
        val currentRevisionNumber: Int?,
        val currentTitle: String?,
    ) : ReviseCommittedProblemResult

    data class DuplicateCommandPayloadConflict(
        val commandId: String,
    ) : ReviseCommittedProblemResult

    data object NotFound : ReviseCommittedProblemResult
}
