package com.tingyun.smartmistakebook.core.data.mistake

import android.content.Context
import com.tingyun.smartmistakebook.core.data.capture.AndroidCanonicalAssetVault
import com.tingyun.smartmistakebook.core.database.CanonicalSourceAssetRecord
import com.tingyun.smartmistakebook.core.database.MistakeDetailRecord
import com.tingyun.smartmistakebook.core.database.MistakeDetailSourceAssetRecord
import com.tingyun.smartmistakebook.core.database.MistakeRevisionSummaryRecord
import com.tingyun.smartmistakebook.core.database.StudyDatabasePort
import com.tingyun.smartmistakebook.core.database.port.MistakeRevisionWritePort
import com.tingyun.smartmistakebook.core.database.port.ReviseCommittedProblemCommand
import com.tingyun.smartmistakebook.core.database.port.ReviseCommittedProblemResult
import com.tingyun.smartmistakebook.core.database.port.RevisedProblemRevisionRecord
import com.tingyun.smartmistakebook.core.domain.ArchivedMistakeRef
import com.tingyun.smartmistakebook.core.domain.MistakeDetail
import com.tingyun.smartmistakebook.core.domain.MistakeDetailIdentity
import com.tingyun.smartmistakebook.core.domain.MistakeDetailRepository
import com.tingyun.smartmistakebook.core.domain.MistakeDetailState
import com.tingyun.smartmistakebook.core.domain.MistakeRevisionChange
import com.tingyun.smartmistakebook.core.domain.MistakeRevisionDiff
import com.tingyun.smartmistakebook.core.domain.MistakeRevisionKey
import com.tingyun.smartmistakebook.core.domain.MistakeRevisionReceipt
import com.tingyun.smartmistakebook.core.domain.MistakeRevisionSummary
import com.tingyun.smartmistakebook.core.domain.MistakeSourceAsset
import com.tingyun.smartmistakebook.core.domain.MistakeSourceLocation
import com.tingyun.smartmistakebook.core.domain.MistakeSourceSet
import com.tingyun.smartmistakebook.core.domain.ReviseMistakeProblemCommand
import com.tingyun.smartmistakebook.core.domain.ReviseMistakeProblemResult
import com.tingyun.smartmistakebook.core.domain.TutorConversationReference
import com.tingyun.smartmistakebook.core.model.CapturedQuestionDocument
import com.tingyun.smartmistakebook.core.model.CapturedQuestionDocumentCodec
import com.tingyun.smartmistakebook.core.model.CapturedQuestionDocumentFingerprint
import com.tingyun.smartmistakebook.core.model.CapturedQuestionDocumentValidator
import com.tingyun.smartmistakebook.core.model.QuestionDocumentMarkdownProjection
import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

internal fun interface MistakeDetailRecordReader {
    suspend fun read(errorBookEntryId: String): MistakeDetailRecord?
}

internal fun interface MistakeNoteWriter {
    suspend fun write(entryId: String, note: String?, updatedAtEpochMillis: Long): Boolean
}

/** C11 收藏开关的写端口（与备注同一层：行级私有状态，不在修订链上）。 */
internal fun interface MistakeFavoriteWriter {
    suspend fun write(entryId: String, isFavorite: Boolean, updatedAtEpochMillis: Long): Boolean
}

internal fun interface MistakeArchiver {
    suspend fun archive(entryId: String, at: Long): Boolean
}

internal fun interface MistakeRestorer {
    suspend fun restore(entryId: String, at: Long): Boolean
}

internal fun interface ArchivedObserver {
    fun observe(): Flow<List<com.tingyun.smartmistakebook.core.domain.ArchivedMistakeRef>>
}

internal fun interface ExactMistakeDetailRecordReader {
    suspend fun read(key: MistakeRevisionKey): MistakeDetailRecord?
}

internal fun interface CurrentMistakeDetailBatchRecordReader {
    suspend fun read(entryIds: List<String>): List<MistakeDetailRecord>
}

internal fun interface CanonicalAssetUriResolver {
    fun resolve(sourceAsset: CanonicalSourceAssetRecord): String
}

internal fun interface MistakeRevisionHistoryReader {
    suspend fun read(errorBookEntryId: String): List<MistakeRevisionSummaryRecord>
}

/** Narrow write seam so read-only test doubles never need a database. */
internal fun interface MistakeRevisionWriter {
    suspend fun revise(command: ReviseCommittedProblemCommand): ReviseCommittedProblemResult
}

internal class RoomMistakeDetailRepository(
    private val recordReader: MistakeDetailRecordReader,
    private val exactRecordReader: ExactMistakeDetailRecordReader,
    private val batchRecordReader: CurrentMistakeDetailBatchRecordReader? = null,
    private val revisionHistoryReader: MistakeRevisionHistoryReader =
        MistakeRevisionHistoryReader { emptyList() },
    private val assetUriResolver: CanonicalAssetUriResolver,
    private val noteWriter: MistakeNoteWriter =
        MistakeNoteWriter { _, _, _ -> false },
    private val favoriteWriter: MistakeFavoriteWriter =
        MistakeFavoriteWriter { _, _, _ -> false },
    private val archiver: MistakeArchiver =
        MistakeArchiver { _, _ -> false },
    private val restorer: MistakeRestorer =
        MistakeRestorer { _, _ -> false },
    private val archivedObserver: ArchivedObserver =
        ArchivedObserver { kotlinx.coroutines.flow.flowOf(emptyList()) },
    private val revisionWriter: MistakeRevisionWriter? = null,
) : MistakeDetailRepository {
    override suspend fun archiveEntry(entryId: String, at: Long): Boolean {
        require(entryId.isNotBlank()) { "entryId must not be blank" }
        require(at >= 0) { "at must not be negative" }
        return archiver.archive(entryId, at)
    }

    override suspend fun restoreEntry(entryId: String, at: Long): Boolean {
        require(entryId.isNotBlank()) { "entryId must not be blank" }
        require(at >= 0) { "at must not be negative" }
        return restorer.restore(entryId, at)
    }

    override fun observeArchived(): Flow<List<ArchivedMistakeRef>> = archivedObserver.observe()
    override suspend fun updateUserNote(
        entryId: String,
        note: String?,
        updatedAtEpochMillis: Long,
    ): Boolean {
        require(entryId.isNotBlank()) { "entryId must not be blank" }
        require(updatedAtEpochMillis >= 0) { "updatedAtEpochMillis must not be negative" }
        val normalized = note?.trim()?.takeIf(String::isNotEmpty)
        return noteWriter.write(entryId, normalized, updatedAtEpochMillis)
    }

    override suspend fun setFavorite(
        entryId: String,
        isFavorite: Boolean,
        updatedAtEpochMillis: Long,
    ): Boolean {
        require(entryId.isNotBlank()) { "entryId must not be blank" }
        require(updatedAtEpochMillis >= 0) { "updatedAtEpochMillis must not be negative" }
        return favoriteWriter.write(entryId, isFavorite, updatedAtEpochMillis)
    }
    override suspend fun reviseProblem(
        command: ReviseMistakeProblemCommand,
    ): ReviseMistakeProblemResult = withContext(Dispatchers.IO) {
        val writer = revisionWriter ?: return@withContext ReviseMistakeProblemResult.NotSupported
        val resolvedTitle = resolveRevisionTitle(command)
            ?: return@withContext ReviseMistakeProblemResult.InvalidDocument(
                listOf("MISSING_TITLE"),
            )
        if (resolvedTitle.length > MAX_REVISION_TITLE_CHARS) {
            return@withContext ReviseMistakeProblemResult.InvalidDocument(
                listOf("TITLE_TOO_LONG"),
            )
        }
        val document = command.questionDocument.copy(
            document = command.questionDocument.document.copy(title = resolvedTitle),
        )
        val issues = CapturedQuestionDocumentValidator.validateForCommit(document)
        if (issues.isNotEmpty()) {
            return@withContext ReviseMistakeProblemResult.InvalidDocument(
                issues.map { issue -> "${issue.code}:${issue.blockId.orEmpty()}" },
            )
        }
        val snapshot = try {
            CapturedQuestionDocumentCodec.encode(document)
        } catch (failure: IllegalArgumentException) {
            return@withContext ReviseMistakeProblemResult.InvalidDocument(
                listOf(failure.message ?: "INVALID_STRUCTURED_CONTENT"),
            )
        }
        val markdown = QuestionDocumentMarkdownProjection.project(document.document)
        if (markdown.isBlank()) {
            return@withContext ReviseMistakeProblemResult.InvalidDocument(listOf("EMPTY_QUESTION"))
        }
        val fingerprint = CapturedQuestionDocumentFingerprint.of(document)
        val sourceReference = revisionReceiptToken(
            command = command,
            resolvedTitle = resolvedTitle,
            contentFingerprint = fingerprint,
        )
        val result = writer.revise(
            ReviseCommittedProblemCommand(
                commandId = command.commandId,
                sourceReference = sourceReference,
                entryId = command.entryId,
                expectedRevisionId = command.expectedRevisionId,
                expectedRevisionNumber = command.expectedRevisionNumber,
                newRevisionId = "revision-edit-${command.commandId}",
                title = resolvedTitle,
                problemMarkdown = markdown,
                questionDocumentSnapshot = snapshot,
                contentFingerprint = fingerprint,
                revisedAtEpochMillis = command.revisedAtEpochMillis,
            ),
        )
        result.toDomainResult(resolvedTitle)
    }

    private suspend fun ReviseCommittedProblemResult.toDomainResult(
        requestedTitle: String,
    ): ReviseMistakeProblemResult = when (this) {
        is ReviseCommittedProblemResult.Saved -> ReviseMistakeProblemResult.Saved(
            receipt = record.toDomainReceipt(requestedTitle),
            created = created,
        )
        is ReviseCommittedProblemResult.Unchanged -> ReviseMistakeProblemResult.Unchanged(
            // Nothing was written: there is no before/after to report.
            receipt = record.toDomainReceipt(requestedTitle, computeChanges = false),
        )
        is ReviseCommittedProblemResult.Conflict -> ReviseMistakeProblemResult.Conflict(
            expectedRevisionId = expectedRevisionId,
            expectedRevisionNumber = expectedRevisionNumber,
            currentRevisionId = currentRevisionId,
            currentRevisionNumber = currentRevisionNumber,
            currentRevisionTitle = currentTitle,
        )
        is ReviseCommittedProblemResult.DuplicateCommandPayloadConflict ->
            ReviseMistakeProblemResult.DuplicateCommandConflict(commandId)
        ReviseCommittedProblemResult.NotFound -> ReviseMistakeProblemResult.NotFound
    }

    private suspend fun RevisedProblemRevisionRecord.toDomainReceipt(
        requestedTitle: String,
        computeChanges: Boolean = true,
    ): MistakeRevisionReceipt {
        val changes = if (computeChanges) revisionChanges(this, requestedTitle) else emptyList()
        return MistakeRevisionReceipt(
            entryId = entryId,
            problemId = problemId,
            problemRevisionId = problemRevisionId,
            revisionNumber = revisionNumber,
            previousRevisionId = previousRevisionId,
            previousRevisionNumber = previousRevisionNumber,
            title = title,
            contentFingerprint = contentFingerprint,
            answerSpecPresent = answerSpecPresent,
            createdAtEpochMillis = createdAtEpochMillis,
            changes = changes,
            cancelledModelTaskCount = invalidation.cancelledModelTaskCount,
            supersededCleanRedrawCount = invalidation.supersededCleanRedrawCount,
            staleRelationCount = invalidation.staleRelationCount,
        )
    }

    private suspend fun revisionChanges(
        record: RevisedProblemRevisionRecord,
        requestedTitle: String,
    ): List<MistakeRevisionChange> {
        val previous = readExactState(
            MistakeRevisionKey(record.entryId, record.problemId, record.previousRevisionId),
        ) as? MistakeDetailState.Ready
            ?: return emptyList()
        val next = readExactState(
            MistakeRevisionKey(record.entryId, record.problemId, record.problemRevisionId),
        ) as? MistakeDetailState.Ready
            ?: return emptyList()
        return MistakeRevisionDiff.between(
            previousTitle = previous.detail.identity.title,
            previousDocument = previous.questionDocument,
            nextTitle = requestedTitle,
            nextDocument = next.questionDocument,
            previousHadAnswerSpec = record.previousAnswerSpecPresent,
        )
    }

    private fun resolveRevisionTitle(command: ReviseMistakeProblemCommand): String? =
        command.title?.trim()?.takeIf(String::isNotEmpty)
            ?: command.questionDocument.document.title?.trim()?.takeIf(String::isNotEmpty)

    private fun revisionReceiptToken(
        command: ReviseMistakeProblemCommand,
        resolvedTitle: String,
        contentFingerprint: String,
    ): String {
        val payload = buildString {
            append(command.entryId).append('\n')
            append(command.expectedRevisionId).append('\n')
            append(command.expectedRevisionNumber).append('\n')
            append(contentFingerprint).append('\n')
            append(resolvedTitle)
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(payload.toByteArray(StandardCharsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
        return "${ReviseCommittedProblemCommand.RECEIPT_PREFIX}${command.commandId}:" +
            "${command.expectedRevisionId}:$digest"
    }

    override fun observe(errorBookEntryId: String): Flow<MistakeDetailState> {
        require(errorBookEntryId.isNotBlank()) { "errorBookEntryId must not be blank" }
        return flow {
            emit(MistakeDetailState.Loading)
            val record = recordReader.read(errorBookEntryId)
            emit(record?.toState() ?: MistakeDetailState.NotFound)
        }.flowOn(Dispatchers.IO)
    }

    override fun observeExact(key: MistakeRevisionKey): Flow<MistakeDetailState> = flow {
        emit(MistakeDetailState.Loading)
        emit(readExactState(key))
    }.flowOn(Dispatchers.IO)

    override suspend fun readExact(key: MistakeRevisionKey): MistakeDetailState =
        withContext(Dispatchers.IO) { readExactState(key) }

    override suspend fun readExact(
        keys: List<MistakeRevisionKey>,
    ): List<MistakeDetailState> = withContext(Dispatchers.IO) {
        require(keys.size <= MAX_BATCH_SIZE) { "Mistake-detail batch is too large" }
        if (keys.isEmpty()) return@withContext emptyList()
        val reader = batchRecordReader
            ?: return@withContext keys.map { key -> readExactState(key) }
        val recordsByEntryId = reader
            .read(keys.map(MistakeRevisionKey::entryId).distinct())
            .associateBy(MistakeDetailRecord::entryId)
        keys.map { key ->
            recordsByEntryId[key.entryId]
                ?.takeIf { record ->
                    record.problemId == key.problemId &&
                        record.problemRevisionId == key.problemRevisionId
                }
                ?.toState()
                ?: MistakeDetailState.NotFound
        }
    }

    override fun observeRevisionHistory(
        errorBookEntryId: String,
    ): Flow<List<MistakeRevisionSummary>> {
        require(errorBookEntryId.isNotBlank()) { "errorBookEntryId must not be blank" }
        return flow {
            val history = revisionHistoryReader.read(errorBookEntryId)
                .map { record -> record.toDomain() }
            check(history.count(MistakeRevisionSummary::isCurrent) <= 1) {
                "A mistake entry cannot have multiple current revisions"
            }
            emit(history)
        }.flowOn(Dispatchers.IO)
    }

    private suspend fun readExactState(key: MistakeRevisionKey): MistakeDetailState =
        exactRecordReader.read(key)?.toState() ?: MistakeDetailState.NotFound

    private fun MistakeDetailRecord.toState(): MistakeDetailState {
        val identity = MistakeDetailIdentity(
            errorBookEntryId = entryId,
            problemId = problemId,
            problemRevisionId = problemRevisionId,
            practiceUnitId = practiceUnitId,
            revisionNumber = revisionNumber,
            title = title,
            subject = subject,
        )
        val snapshot = questionDocumentSnapshot
        if (snapshot == null) {
            return MistakeDetailState.Legacy(toDetail(identity))
        }
        val document = decodeCommittedDocument(snapshot)
            ?: return MistakeDetailState.CorruptSnapshot(identity)
        if (CapturedQuestionDocumentFingerprint.of(document) != contentFingerprint) {
            return MistakeDetailState.CorruptSnapshot(identity)
        }
        return MistakeDetailState.Ready(
            detail = toDetail(identity),
            questionDocument = document,
            answerSpecPresent = answerSpecPresent,
        )
    }

    private fun MistakeDetailRecord.toDetail(identity: MistakeDetailIdentity) = MistakeDetail(
        identity = identity,
        fallbackMarkdown = problemMarkdown,
        source = if (sourceAssets.isEmpty()) {
            MistakeSourceSet.Missing
        } else {
            MistakeSourceSet.Present(sourceAssets.map { sourceAsset -> sourceAsset.toDomain() })
        },
        tutorConversation = tutorConversationReference(),
        userNote = userNote,
        archived = archived,
    )

    private fun MistakeDetailRecord.tutorConversationReference(): TutorConversationReference? {
        check((tutorSessionId == null) == (tutorQuestionRevisionNumber == null)) {
            "Tutor conversation locator must be complete"
        }
        val sessionId = tutorSessionId ?: return null
        return TutorConversationReference(
            sessionId = sessionId,
            questionRevisionNumber = checkNotNull(tutorQuestionRevisionNumber),
        )
    }

    private fun MistakeDetailSourceAssetRecord.toDomain(): MistakeSourceAsset =
        MistakeSourceAsset(
            role = role,
            sourceAssetId = sourceAsset.sourceAssetId,
            contentSha256 = sourceAsset.contentSha256,
            mimeType = sourceAsset.mimeType,
            byteSize = sourceAsset.byteSize,
            width = sourceAsset.width,
            height = sourceAsset.height,
            sourceType = sourceAsset.sourceType,
            createdAtEpochMillis = sourceAsset.createdAtEpochMillis,
            location = resolveLocation(sourceAsset),
        )

    private fun resolveLocation(sourceAsset: CanonicalSourceAssetRecord): MistakeSourceLocation =
        try {
            val localUri = assetUriResolver.resolve(sourceAsset)
            require(URI(localUri).scheme == "file") { "Canonical asset URI must use file scheme" }
            MistakeSourceLocation.Available(localUri)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            MistakeSourceLocation.Unavailable
        }

    private fun decodeCommittedDocument(snapshot: String): CapturedQuestionDocument? = try {
        CapturedQuestionDocumentCodec.decode(snapshot).takeIf { document ->
            CapturedQuestionDocumentValidator.validateForCommit(document).isEmpty()
        }
    } catch (_: Exception) {
        null
    }

    private fun MistakeRevisionSummaryRecord.toDomain() = MistakeRevisionSummary(
        entryId = entryId,
        problemId = problemId,
        problemRevisionId = problemRevisionId,
        revisionNumber = revisionNumber,
        title = title,
        createdAtEpochMillis = createdAtEpochMillis,
        isCurrent = isCurrent,
    )

    private companion object {
        const val MAX_BATCH_SIZE = 100
        const val MAX_REVISION_TITLE_CHARS = 160
    }
}

object MistakeDetailRepositoryFactory {
    fun create(
        context: Context,
        database: StudyDatabasePort,
    ): MistakeDetailRepository {
        val assetVault = AndroidCanonicalAssetVault(context.applicationContext)
        return RoomMistakeDetailRepository(
            recordReader = MistakeDetailRecordReader(database::readMistakeDetail),
            exactRecordReader = ExactMistakeDetailRecordReader { key ->
                database.readExactMistakeDetail(
                    entryId = key.entryId,
                    problemId = key.problemId,
                    problemRevisionId = key.problemRevisionId,
                )
            },
            batchRecordReader = CurrentMistakeDetailBatchRecordReader(
                database::readCurrentMistakeDetails,
            ),
            revisionHistoryReader = MistakeRevisionHistoryReader(
                database::readMistakeRevisionHistory,
            ),
            assetUriResolver = CanonicalAssetUriResolver { sourceAsset ->
                assetVault.resolve(sourceAsset).toURI().toASCIIString()
            },
            noteWriter = MistakeNoteWriter(database::updateErrorBookEntryNote),
            favoriteWriter = MistakeFavoriteWriter(database::setErrorBookEntryFavorite),
            archiver = MistakeArchiver(database::archiveErrorBookEntry),
            restorer = MistakeRestorer(database::restoreErrorBookEntry),
            archivedObserver = ArchivedObserver {
                database.observeArchivedErrorBookEntries().map { rows ->
                    rows.map { row ->
                        ArchivedMistakeRef(entryId = row.entryId, title = row.title)
                    }
                }
            },
            // Production's StudyDatabasePort is also the revision-write port. A read-only
            // deployment (or a test double) keeps the existing read behavior and reports edits
            // as NotSupported instead of pretending they were saved.
            revisionWriter = (database as? MistakeRevisionWritePort)?.let { port ->
                MistakeRevisionWriter { command -> port.reviseCommittedProblem(command) }
            },
        )
    }
}
