package com.tingyun.smartmistakebook.core.data.mistake

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.data.capture.CaptureWorkflowTestBase
import com.tingyun.smartmistakebook.core.database.StudyDatabaseFactory
import com.tingyun.smartmistakebook.core.domain.CaptureDraftImportRequest
import com.tingyun.smartmistakebook.core.domain.CaptureEntryOrigin
import com.tingyun.smartmistakebook.core.domain.ConfirmCapturedProblemRequest
import com.tingyun.smartmistakebook.core.domain.MistakeDetailState
import com.tingyun.smartmistakebook.core.domain.MistakeRevisionChangeKind
import com.tingyun.smartmistakebook.core.domain.MistakeRevisionKey
import com.tingyun.smartmistakebook.core.domain.ReviseMistakeProblemCommand
import com.tingyun.smartmistakebook.core.domain.ReviseMistakeProblemResult
import com.tingyun.smartmistakebook.core.domain.SaveCaptureDraftWorkspaceRequest
import com.tingyun.smartmistakebook.core.model.CaptureDraftEditedField
import com.tingyun.smartmistakebook.core.model.CaptureDraftEditorMode
import com.tingyun.smartmistakebook.core.model.CaptureDraftWorkspace
import com.tingyun.smartmistakebook.core.model.CaptureFinalConfirmationRequestIdentity
import com.tingyun.smartmistakebook.core.model.CapturedQuestionDocument
import com.tingyun.smartmistakebook.core.model.ContentBlock
import com.tingyun.smartmistakebook.core.model.QuestionBlockEvidence
import com.tingyun.smartmistakebook.core.model.QuestionBlockProvenance
import com.tingyun.smartmistakebook.core.model.QuestionBlockReviewStatus
import com.tingyun.smartmistakebook.core.model.QuestionDocument
import com.tingyun.smartmistakebook.core.model.WritingLayer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * PRD-03 end-to-end data proof: repository command -> durable revision -> reopen -> old revision
 * still readable -> retry/conflict semantics.
 */
@RunWith(AndroidJUnit4::class)
class MistakeRevisionEditInstrumentedTest : CaptureWorkflowTestBase() {

    @Test
    fun editIsDurableAcrossReopenAndKeepsOldRevisionAndHistoryReadable() = runBlocking {
        val entryId = commitManualProblem("revision-edit-flow")
        val details = MistakeDetailRepositoryFactory.create(context, database)
        val before = details.observe(entryId).first { it != MistakeDetailState.Loading }
            as MistakeDetailState.Ready
        val oldKey = MistakeRevisionKey(
            entryId = entryId,
            problemId = before.detail.identity.problemId,
            problemRevisionId = before.detail.identity.problemRevisionId,
        )

        val edited = before.questionDocument.copy(
            document = before.questionDocument.document.copy(
                title = "修改后的题",
                blocks = listOf(ContentBlock.Paragraph("stem", "修改后的题干")),
            ),
        )
        val command = ReviseMistakeProblemCommand(
            commandId = "revision-edit-command-1",
            entryId = entryId,
            expectedRevisionId = oldKey.problemRevisionId,
            expectedRevisionNumber = before.detail.identity.revisionNumber,
            questionDocument = edited,
            revisedAtEpochMillis = 10_000,
            title = "修改后的题",
        )

        val saved = details.reviseProblem(command) as ReviseMistakeProblemResult.Saved
        val receipt = saved.receipt
        assertTrue(saved.created)
        assertEquals(2, receipt.revisionNumber)
        assertEquals(oldKey.problemRevisionId, receipt.previousRevisionId)
        assertTrue(receipt.changes.any { it.kind == MistakeRevisionChangeKind.STEM })
        assertTrue(receipt.changes.any { it.kind == MistakeRevisionChangeKind.TITLE })

        val current = details.observe(entryId).first { it != MistakeDetailState.Loading }
            as MistakeDetailState.Ready
        assertEquals(receipt.problemRevisionId, current.detail.identity.problemRevisionId)
        assertEquals(2, current.detail.identity.revisionNumber)
        assertEquals("修改后的题", current.detail.identity.title)
        assertEquals(edited, current.questionDocument)

        // The old revision keeps its own exact document and identity.
        val old = details.readExact(oldKey) as MistakeDetailState.Ready
        assertEquals(before.detail.identity.title, old.detail.identity.title)
        assertEquals(before.questionDocument, old.questionDocument)

        // Retrying the same command (fresh timestamp) returns the same receipt, no second write.
        val retried = details.reviseProblem(
            command.copy(revisedAtEpochMillis = 20_000),
        ) as ReviseMistakeProblemResult.Saved
        val retriedReceipt = retried.receipt
        assertFalse(retried.created)
        assertEquals(receipt.problemRevisionId, retriedReceipt.problemRevisionId)
        assertEquals(2, details.observeRevisionHistory(entryId).first().size)

        // A stale expected revision is an explicit conflict and writes nothing.
        val conflict = details.reviseProblem(
            command.copy(commandId = "revision-edit-command-stale"),
        )
        assertTrue(conflict is ReviseMistakeProblemResult.Conflict)
        assertEquals(2, details.observeRevisionHistory(entryId).first().size)

        // Note edits are entry-level, never a revision and never clear history.
        assertTrue(details.updateUserNote(entryId, "排版与备注修改", 30_000))
        val afterNote = details.observe(entryId).first { it != MistakeDetailState.Loading }
            as MistakeDetailState.Ready
        assertEquals("排版与备注修改", afterNote.detail.userNote)
        assertEquals(
            listOf(2, 1),
            details.observeRevisionHistory(entryId).first().map { it.revisionNumber },
        )

        // Process-style kill/reopen: a fresh database handle and repository still see revision 2,
        // while revision 1 remains readable and exportable.
        database.close()
        database = StudyDatabaseFactory.open(context, databaseName)
        val reopened = MistakeDetailRepositoryFactory.create(context, database)
        val reopenedCurrent = reopened.observe(entryId)
            .first { it != MistakeDetailState.Loading } as MistakeDetailState.Ready
        assertEquals(2, reopenedCurrent.detail.identity.revisionNumber)
        assertEquals(edited, reopenedCurrent.questionDocument)
        assertEquals(
            before.questionDocument,
            (reopened.readExact(oldKey) as MistakeDetailState.Ready).questionDocument,
        )
        assertEquals(
            listOf(2, 1),
            reopened.observeRevisionHistory(entryId).first().map { it.revisionNumber },
        )
    }

    @Test
    fun invalidEditIsRejectedWithoutCreatingRevisionOrChangingHead() = runBlocking {
        val entryId = commitManualProblem("revision-edit-invalid")
        val details = MistakeDetailRepositoryFactory.create(context, database)
        val before = details.observe(entryId).first { it != MistakeDetailState.Loading }
            as MistakeDetailState.Ready
        val blank = before.questionDocument.copy(
            document = before.questionDocument.document.copy(
                blocks = listOf(ContentBlock.Paragraph("stem", "   ")),
            ),
        )

        val result = details.reviseProblem(
            ReviseMistakeProblemCommand(
                commandId = "revision-edit-invalid-command",
                entryId = entryId,
                expectedRevisionId = before.detail.identity.problemRevisionId,
                expectedRevisionNumber = before.detail.identity.revisionNumber,
                questionDocument = blank,
                revisedAtEpochMillis = 40_000,
                title = "空题",
            ),
        )

        assertTrue(result is ReviseMistakeProblemResult.InvalidDocument)
        val after = details.observe(entryId).first { it != MistakeDetailState.Loading }
            as MistakeDetailState.Ready
        assertEquals(before.detail.identity.problemRevisionId, after.detail.identity.problemRevisionId)
        assertEquals(1, details.observeRevisionHistory(entryId).first().size)
    }

    private suspend fun commitManualProblem(requestId: String): String {
        val imported = repository.importDraft(
            CaptureDraftImportRequest(
                requestId = requestId,
                origin = CaptureEntryOrigin.LIBRARY,
                occurredAtEpochMillis = 1_000,
            ),
        )
        val pending = checkNotNull(repository.readPendingCapture(imported.draftId))
        val document = CapturedQuestionDocument(
            document = QuestionDocument(
                id = "document-${imported.draftId}",
                title = "手敲的函数题",
                blocks = listOf(
                    ContentBlock.Paragraph("stem", "求函数 f(x)=x^2 的单调区间。"),
                ),
            ),
            blockEvidence = listOf(
                QuestionBlockEvidence(
                    blockId = "stem",
                    sourceAssetId = "",
                    sourceRegion = null,
                    writingLayer = WritingLayer.UNKNOWN,
                    provenance = QuestionBlockProvenance.USER_TRANSCRIPTION,
                    confidence = null,
                    reviewStatus = QuestionBlockReviewStatus.USER_CONFIRMED,
                    producerVersion = null,
                ),
            ),
        )
        val workspace = repository.saveDraftWorkspace(
            SaveCaptureDraftWorkspaceRequest(
                draftId = pending.draftId,
                basisRevisionNumber = pending.currentRevisionNumber,
                expectedWorkspaceVersion = 0,
                expectedWorkspaceFingerprint = null,
                workspace = CaptureDraftWorkspace(
                    subject = "MATH",
                    workingDocument = document,
                    editorMode = CaptureDraftEditorMode.MANUAL_TRANSCRIPTION,
                    userEditedFields = setOf(
                        CaptureDraftEditedField.SUBJECT,
                        CaptureDraftEditedField.TITLE,
                        CaptureDraftEditedField.TRANSCRIPTION,
                    ),
                    userEditedBlockIds = setOf("stem"),
                    baseCandidateFingerprint = pending.currentRevisionDocumentFingerprint,
                    finalConfirmationRequest = CaptureFinalConfirmationRequestIdentity(
                        requestId = "$requestId-confirm",
                        occurredAtEpochMillis = 2_000,
                    ),
                ),
                occurredAtEpochMillis = 2_000,
            ),
        )
        val committed = repository.confirmAndCommit(
            ConfirmCapturedProblemRequest(
                draftId = imported.draftId,
                workspaceIdentity = workspace.identity,
            ),
        )
        assertTrue(committed.created)
        return committed.errorBookEntryId
    }
}
