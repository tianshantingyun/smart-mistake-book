package com.tingyun.smartmistakebook.core.domain

import com.tingyun.smartmistakebook.core.model.CapturedQuestionDocument
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Read-only projection of the current formal revision behind one error-book entry. */
interface MistakeDetailRepository {
    fun observe(errorBookEntryId: String): Flow<MistakeDetailState>

    /** Observes one immutable problem revision selected independently of the entry's current head. */
    fun observeExact(key: MistakeRevisionKey): Flow<MistakeDetailState>

    /** Reads one immutable problem revision without an intermediate [MistakeDetailState.Loading]. */
    suspend fun readExact(key: MistakeRevisionKey): MistakeDetailState

    /**
     * Saves a corrected question document as the next immutable revision of the entry, using the
     * caller's expected current revision as a compare-and-swap guard.
     *
     * The command is idempotent by [ReviseMistakeProblemCommand.commandId]: retrying the same
     * command (even with a fresh timestamp) returns the receipt of the revision it already
     * created. A different command that lands on a moved head returns
     * [ReviseMistakeProblemResult.Conflict] instead of overwriting the newer revision.
     *
     * The default is honest "not supported" so non-database repositories never report a write
     * they did not perform.
     */
    suspend fun reviseProblem(
        command: ReviseMistakeProblemCommand,
    ): ReviseMistakeProblemResult = ReviseMistakeProblemResult.NotSupported

    /**
     * Reads an ordered immutable snapshot. Implementations may collapse this into one database
     * transaction; the default preserves compatibility for non-database repositories.
     */
    suspend fun readExact(keys: List<MistakeRevisionKey>): List<MistakeDetailState> =
        keys.map { key -> readExact(key) }

    /** Lists every immutable formal revision belonging to one error-book entry. */
    fun observeRevisionHistory(errorBookEntryId: String): Flow<List<MistakeRevisionSummary>> =
        flowOf(emptyList())

    /**
     * Updates the learner's private note on an error-book entry. The note is
     * user text (never model egress) and lives on the entry, not the
     * append-only revision. Blank/whitespace notes are stored as null.
     */
    suspend fun updateUserNote(
        entryId: String,
        note: String?,
        updatedAtEpochMillis: Long,
    ): Boolean = false

    /**
     * C11 收藏：把条目标成已收藏/未收藏（行级更新，只改 `is_favorite` 与更新时间）。
     * 返回 false 表示没写进去（条目已不在）——调用方必须如实报失败。
     */
    suspend fun setFavorite(
        entryId: String,
        isFavorite: Boolean,
        updatedAtEpochMillis: Long,
    ): Boolean = false

    /** Reversibly hides an error-book entry from all surfaces; history preserved. */
    suspend fun archiveEntry(entryId: String, at: Long): Boolean = false

    suspend fun restoreEntry(entryId: String, at: Long): Boolean = false

    /** 归档条目摘要（id+标题）：错题本"已移出"入口的展示与恢复。 */
    fun observeArchived(): Flow<List<ArchivedMistakeRef>> = flowOf(emptyList())
}

data class ArchivedMistakeRef(
    val entryId: String,
    val title: String,
) {
    init {
        require(entryId.isNotBlank()) { "Archived entry id must not be blank" }
    }
}

data class MistakeRevisionSummary(
    val entryId: String,
    val problemId: String,
    val problemRevisionId: String,
    val revisionNumber: Int,
    val title: String,
    val createdAtEpochMillis: Long,
    val isCurrent: Boolean,
) {
    init {
        require(entryId.isNotBlank())
        require(problemId.isNotBlank())
        require(problemRevisionId.isNotBlank())
        require(revisionNumber > 0)
        require(title.isNotBlank())
        require(createdAtEpochMillis >= 0)
    }

    fun toKey(): MistakeRevisionKey = MistakeRevisionKey(
        entryId = entryId,
        problemId = problemId,
        problemRevisionId = problemRevisionId,
    )
}

data class MistakeRevisionKey(
    val entryId: String,
    val problemId: String,
    val problemRevisionId: String,
) {
    init {
        require(entryId.isNotBlank()) { "Error-book entry id must not be blank" }
        require(problemId.isNotBlank()) { "Problem id must not be blank" }
        require(problemRevisionId.isNotBlank()) { "Problem revision id must not be blank" }
    }
}

sealed interface MistakeDetailState {
    data object Loading : MistakeDetailState

    data class Ready(
        val detail: MistakeDetail,
        val questionDocument: CapturedQuestionDocument,
        /**
         * 这一版题面是否带机器可判题的答案与解析（`problem_revision.answer_spec_id`）。
         *
         * 编辑页用它把“保存后会丢掉旧答案”放进**保存前**差异；缺省 false 只适用于
         * 不来自数据库的替身/旧构造点，生产读取必须给出真实值。
         */
        val answerSpecPresent: Boolean = false,
    ) : MistakeDetailState

    /** A pre-structured-content record. Markdown is authoritative only in this state. */
    data class Legacy(
        val detail: MistakeDetail,
    ) : MistakeDetailState

    /** A non-empty structured snapshot that failed strict decoding or commit validation. */
    data class CorruptSnapshot(
        val identity: MistakeDetailIdentity,
    ) : MistakeDetailState

    data object NotFound : MistakeDetailState
}

data class MistakeDetail(
    val identity: MistakeDetailIdentity,
    val fallbackMarkdown: String,
    val source: MistakeSourceSet,
    val tutorConversation: TutorConversationReference? = null,
    /** Learner's private note on the entry; never leaves the device in a model egress payload. */
    val userNote: String? = null,
    /** True when the entry has been archived (hidden from surfaces, history preserved). */
    val archived: Boolean = false,
) {
    init {
        require(fallbackMarkdown.isNotBlank()) { "Mistake fallback Markdown must not be blank" }
    }
}

/** Stable locator for a conversation that began before this formal revision was saved. */
data class TutorConversationReference(
    val sessionId: String,
    val questionRevisionNumber: Int,
) {
    init {
        require(sessionId.isNotBlank()) { "Tutor conversation session id must not be blank" }
        require(questionRevisionNumber > 0) {
            "Tutor conversation question revision must be positive"
        }
    }
}

data class MistakeDetailIdentity(
    val errorBookEntryId: String,
    val problemId: String,
    val problemRevisionId: String,
    val revisionNumber: Int,
    val title: String,
    val subject: String,
    val practiceUnitId: String = "legacy-practice-unit",
) {
    init {
        require(errorBookEntryId.isNotBlank()) { "Error-book entry id must not be blank" }
        require(problemId.isNotBlank()) { "Problem id must not be blank" }
        require(problemRevisionId.isNotBlank()) { "Problem revision id must not be blank" }
        require(revisionNumber > 0) { "Problem revision number must be positive" }
        require(title.isNotBlank()) { "Problem title must not be blank" }
        require(subject.isNotBlank()) { "Problem subject must not be blank" }
        require(practiceUnitId.isNotBlank()) { "Practice unit id must not be blank" }
    }
}

sealed interface MistakeSourceSet {
    data object Missing : MistakeSourceSet

    data class Present(
        val assets: List<MistakeSourceAsset>,
    ) : MistakeSourceSet {
        init {
            require(assets.isNotEmpty()) { "A present source set must contain an asset" }
        }
    }
}

data class MistakeSourceAsset(
    val role: String,
    val sourceAssetId: String,
    val contentSha256: String,
    val mimeType: String,
    val byteSize: Long,
    val width: Int,
    val height: Int,
    val sourceType: String,
    val createdAtEpochMillis: Long,
    val location: MistakeSourceLocation,
) {
    init {
        require(role.isNotBlank()) { "Source role must not be blank" }
        require(sourceAssetId.isNotBlank()) { "Source asset id must not be blank" }
        require(SHA_256.matches(contentSha256)) { "Source asset SHA-256 is invalid" }
        require(mimeType.isNotBlank()) { "Source asset MIME type must not be blank" }
        require(byteSize > 0) { "Source asset byte size must be positive" }
        require(width > 0 && height > 0) { "Source asset dimensions must be positive" }
        require(sourceType.isNotBlank()) { "Source asset type must not be blank" }
        require(createdAtEpochMillis >= 0) { "Source asset creation time must not be negative" }
    }

    private companion object {
        val SHA_256 = Regex("[a-f0-9]{64}")
    }
}

sealed interface MistakeSourceLocation {
    data class Available(
        val localUri: String,
    ) : MistakeSourceLocation {
        init {
            require(localUri.isNotBlank()) { "Available source URI must not be blank" }
        }
    }

    data object Unavailable : MistakeSourceLocation
}

/**
 * Stable command identity for one revision edit. [commandId] is the idempotency key; callers
 * must keep it stable across retries (a new timestamp alone does not make a new command).
 */
data class ReviseMistakeProblemCommand(
    val commandId: String,
    val entryId: String,
    val expectedRevisionId: String,
    val expectedRevisionNumber: Int,
    val questionDocument: CapturedQuestionDocument,
    val revisedAtEpochMillis: Long,
    val title: String? = null,
) {
    init {
        require(commandId.matches(COMMAND_ID_PATTERN)) {
            "Revision command id must be a stable identifier"
        }
        require(entryId.isNotBlank()) { "Error-book entry id must not be blank" }
        require(expectedRevisionId.isNotBlank()) { "Expected revision id must not be blank" }
        require(expectedRevisionNumber > 0) { "Expected revision number must be positive" }
        require(revisedAtEpochMillis >= 0) { "Revision time must not be negative" }
    }

    private companion object {
        // No ':' (the durable receipt token is colon-delimited), no LIKE wildcards.
        val COMMAND_ID_PATTERN = Regex("[A-Za-z0-9.-]{1,128}")
    }
}

/** Receipt for the immutable revision that is now (or was already) the entry's head. */
data class MistakeRevisionReceipt(
    val entryId: String,
    val problemId: String,
    val problemRevisionId: String,
    val revisionNumber: Int,
    val previousRevisionId: String,
    val previousRevisionNumber: Int,
    val title: String,
    val contentFingerprint: String,
    /** 这次回执指向的 revision 是否带机器可判题的答案（撤销回旧版时可能为 true）。 */
    val answerSpecPresent: Boolean = false,
    val createdAtEpochMillis: Long,
    val changes: List<MistakeRevisionChange>,
    val cancelledModelTaskCount: Int = 0,
    val supersededCleanRedrawCount: Int = 0,
    val staleRelationCount: Int = 0,
) {
    init {
        require(entryId.isNotBlank())
        require(problemId.isNotBlank())
        require(problemRevisionId.isNotBlank())
        require(revisionNumber > 0)
        require(previousRevisionNumber > 0)
        require(title.isNotBlank())
        require(contentFingerprint.matches(SHA_256))
    }

    private companion object {
        val SHA_256 = Regex("[a-f0-9]{64}")
    }
}

sealed interface ReviseMistakeProblemResult {
    /**
     * The edit was written (or an identical command retry found its existing receipt). [created]
     * is false when no new revision row was written: an idempotent retry, or a content-identical
     * revert to an older immutable revision.
     */
    data class Saved(
        val receipt: MistakeRevisionReceipt,
        val created: Boolean,
    ) : ReviseMistakeProblemResult

    /** The edited document is identical to the current revision; nothing was written. */
    data class Unchanged(
        val receipt: MistakeRevisionReceipt,
    ) : ReviseMistakeProblemResult

    /** The entry moved on (or was never at the caller's expected revision). Nothing was written. */
    data class Conflict(
        val expectedRevisionId: String,
        val expectedRevisionNumber: Int,
        val currentRevisionId: String?,
        val currentRevisionNumber: Int?,
        val currentRevisionTitle: String? = null,
    ) : ReviseMistakeProblemResult

    /** The same command id was reused with a different payload; nothing was written. */
    data class DuplicateCommandConflict(
        val commandId: String,
    ) : ReviseMistakeProblemResult

    /** The structured document failed commit validation; nothing was written. */
    data class InvalidDocument(
        val issues: List<String>,
    ) : ReviseMistakeProblemResult

    data object NotFound : ReviseMistakeProblemResult

    /** This repository cannot write revisions (e.g. a read-only test double). */
    data object NotSupported : ReviseMistakeProblemResult
}
