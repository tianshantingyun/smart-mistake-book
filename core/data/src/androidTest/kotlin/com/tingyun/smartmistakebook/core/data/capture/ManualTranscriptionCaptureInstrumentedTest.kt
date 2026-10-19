package com.tingyun.smartmistakebook.core.data.capture

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.data.mistake.MistakeDetailRepositoryFactory
import com.tingyun.smartmistakebook.core.database.StudyDbValue
import com.tingyun.smartmistakebook.core.domain.CaptureDraftImportRequest
import com.tingyun.smartmistakebook.core.domain.CaptureDraftWorkspaceSnapshot
import com.tingyun.smartmistakebook.core.domain.CaptureEntryOrigin
import com.tingyun.smartmistakebook.core.domain.ConfirmCapturedProblemRequest
import com.tingyun.smartmistakebook.core.domain.MistakeDetailState
import com.tingyun.smartmistakebook.core.domain.MistakeSourceSet
import com.tingyun.smartmistakebook.core.domain.PendingCaptureStage
import com.tingyun.smartmistakebook.core.domain.ResumableCaptureDraft
import com.tingyun.smartmistakebook.core.domain.SaveCaptureDraftWorkspaceRequest
import com.tingyun.smartmistakebook.core.model.CaptureDraftEditedField
import com.tingyun.smartmistakebook.core.model.CaptureDraftEditorMode
import com.tingyun.smartmistakebook.core.model.CaptureDraftWorkspace
import com.tingyun.smartmistakebook.core.model.CaptureFinalConfirmationRequestIdentity
import com.tingyun.smartmistakebook.core.model.CapturedQuestionDocument
import com.tingyun.smartmistakebook.core.model.CapturedQuestionDocumentCodec
import com.tingyun.smartmistakebook.core.model.ContentBlock
import com.tingyun.smartmistakebook.core.model.QuestionBlockEvidence
import com.tingyun.smartmistakebook.core.model.QuestionBlockProvenance
import com.tingyun.smartmistakebook.core.model.QuestionBlockReviewStatus
import com.tingyun.smartmistakebook.core.model.QuestionDocument
import com.tingyun.smartmistakebook.core.model.WritingLayer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * **A8 手动建题全链（KD-52/AUD-28）**：无照片建题 → `created=true` 且题面证据是
 * `USER_TRANSCRIPTION` → 库内可见；空源草稿在 resume / 详情 / 导出入口不崩。
 *
 * 放行只按 provenance：非 `USER_TRANSCRIPTION` 的空白源块在每个写入口都必须被拒。
 */
@RunWith(AndroidJUnit4::class)
class ManualTranscriptionCaptureInstrumentedTest : CaptureWorkflowTestBase() {

    @Test
    fun manualTranscriptionDraftCommitsIntoLibraryWithoutSourceImage() = runBlocking {
        val imported = repository.importDraft(manualRequest("manual-commit"))
        assertNull(imported.sourceAssetId)
        assertNull(imported.sourceAssetSha256)
        assertNull(imported.width)
        assertNull(imported.height)
        assertNull(imported.byteSize)
        assertTrue(imported.sourcePages.isEmpty())

        val pending = checkNotNull(repository.readPendingCapture(imported.draftId))
        assertNull(pending.sourceImageUri)
        assertNull(pending.sourceAssetId)
        assertTrue(pending.sourcePages.isEmpty())
        assertTrue(pending.sourcePageAssessmentTasks.isEmpty())

        val pendingItem = repository.observePendingCaptures().first()
            .single { it.draftId == imported.draftId }
        assertFalse("手动草稿没有原图，不能声称已保存原图", pendingItem.hasSourceImage)
        assertEquals(PendingCaptureStage.READY_TO_CONTINUE, pendingItem.stage)

        val workspace = saveManualWorkspace(
            pending = pending,
            text = MANUAL_TEXT,
            requestId = "manual-confirm",
            occurredAtEpochMillis = 2_000,
        )
        val committed = repository.confirmAndCommit(
            ConfirmCapturedProblemRequest(
                draftId = imported.draftId,
                workspaceIdentity = workspace.identity,
            ),
        )
        assertTrue(committed.created)

        val draftAfter = checkNotNull(database.readProblemDraft(imported.draftId))
        assertEquals(StudyDbValue.ProblemDraftStatus.COMMITTED, draftAfter.status)
        assertNull("提交后的草稿仍然没有源图", draftAfter.sourceAsset)
        assertTrue(draftAfter.sourceAssets.isEmpty())

        // 库内可见：错题详情能读回这道题，题面证据仍是用户转写。
        val detail = checkNotNull(database.readMistakeDetail(committed.errorBookEntryId))
        assertTrue("手动题不应凭空多出原图引用", detail.sourceAssets.isEmpty())
        val document = CapturedQuestionDocumentCodec.decode(
            checkNotNull(detail.questionDocumentSnapshot),
        )
        val evidence = document.blockEvidence.single()
        assertEquals(QuestionBlockProvenance.USER_TRANSCRIPTION, evidence.provenance)
        assertEquals("", evidence.sourceAssetId)
        assertNull("手敲的块没有照片里的位置", evidence.sourceRegion)
        assertEquals(
            MANUAL_TEXT,
            (document.document.blocks.single() as ContentBlock.Paragraph).markdown,
        )

        // 详情读口（UI 的 MistakeSourceSet 映射）：空源是 Missing，不是"图丢了"以外的假图。
        val detailRepository = MistakeDetailRepositoryFactory.create(context, database)
        val detailState = detailRepository.observe(committed.errorBookEntryId).last()
        val ready = detailState as MistakeDetailState.Ready
        assertEquals(MistakeSourceSet.Missing, ready.detail.source)
        assertEquals(
            QuestionBlockProvenance.USER_TRANSCRIPTION,
            ready.questionDocument.blockEvidence.single().provenance,
        )
    }

    @Test
    fun manualDraftReplayIsIdempotentAndTutorOriginIsRejected() = runBlocking {
        val request = manualRequest("manual-replay")
        val first = repository.importDraft(request)
        val second = repository.importDraft(request)

        assertEquals("同一请求重放必须落在同一份草稿上", first.draftId, second.draftId)
        assertEquals(first.revisionNumber, second.revisionNumber)
        assertEquals(
            1,
            repository.observePendingCaptures().first().count { it.draftId == first.draftId },
        )

        val tutorFailure = runCatching {
            repository.importDraft(
                CaptureDraftImportRequest(
                    requestId = "manual-tutor",
                    origin = CaptureEntryOrigin.TUTOR,
                    occurredAtEpochMillis = 3_000,
                ),
            )
        }.exceptionOrNull()
        assertTrue(
            "讲题需要原题照片，手动来源不能建死路草稿：$tutorFailure",
            tutorFailure is IllegalArgumentException,
        )
    }

    @Test
    fun emptySourceDraftResumesAndOnlyUserTranscriptionMayLeaveTheSourceBlank() = runBlocking {
        val imported = repository.importDraft(manualRequest("manual-gate"))
        val resumed = checkNotNull(repository.readPendingCapture(imported.draftId))
        assertNull(resumed.sourceImageUri)
        assertTrue(resumed.sourcePages.isEmpty())
        assertNull("还没有保存工作区时没有快照", resumed.workspace)
        assertNull(repository.readDraftWorkspace(imported.draftId))

        // 空 source_asset_id + 非 USER_TRANSCRIPTION 在任何写入口都必须拒（不放宽其它来源）。
        val rejected = runCatching {
            saveManualWorkspace(
                pending = resumed,
                text = MANUAL_TEXT,
                requestId = "manual-bad-provenance",
                occurredAtEpochMillis = 2_500,
                provenance = QuestionBlockProvenance.LOCAL_OCR,
            )
        }.exceptionOrNull()
        assertTrue(
            "非 USER_TRANSCRIPTION 的空白源必须被契约拒绝：$rejected",
            // 工作区编码器先按 CapturedQuestionDocumentValidator 拒（IllegalArgumentException），
            // 若越过它还会撞上数据库契约（DatabaseContractViolationException 同为 IAE）。
            rejected is IllegalArgumentException,
        )
        assertNull(
            "被拒的写入不许留下半份工作区",
            repository.readDraftWorkspace(imported.draftId),
        )

        // 合法转写工作区写入后再 resume：不崩，快照与证据形态都还在。
        saveManualWorkspace(
            pending = resumed,
            text = MANUAL_TEXT,
            requestId = "manual-saved",
            occurredAtEpochMillis = 2_600,
        )
        val resumedAgain = checkNotNull(repository.readPendingCapture(imported.draftId))
        val snapshot = checkNotNull(resumedAgain.workspace)
        assertEquals(CaptureDraftEditorMode.MANUAL_TRANSCRIPTION, snapshot.workspace.editorMode)
        assertEquals(
            QuestionBlockProvenance.USER_TRANSCRIPTION,
            snapshot.workspace.workingDocument.blockEvidence.single().provenance,
        )
        assertTrue(
            "恢复出的题面包含用户输入：${resumedAgain.transcription}",
            resumedAgain.transcription.contains(MANUAL_TEXT),
        )
        assertTrue(resumedAgain.sourcePages.isEmpty())
        assertNull(resumedAgain.sourceImageUri)
        assertEquals(
            snapshot.identity,
            checkNotNull(repository.readDraftWorkspace(imported.draftId)).identity,
        )
    }

    private fun manualRequest(requestId: String) = CaptureDraftImportRequest(
        requestId = requestId,
        origin = CaptureEntryOrigin.LIBRARY,
        occurredAtEpochMillis = 1_000,
    )

    /**
     * Mimics what the capture screen writes on the final commit: the user's own text as a
     * single paragraph, evidence marked `USER_TRANSCRIPTION` with no source image/region,
     * and the final confirmation identity persisted in the workspace.
     */
    private suspend fun saveManualWorkspace(
        pending: ResumableCaptureDraft,
        text: String,
        requestId: String,
        occurredAtEpochMillis: Long,
        provenance: QuestionBlockProvenance = QuestionBlockProvenance.USER_TRANSCRIPTION,
    ): CaptureDraftWorkspaceSnapshot {
        val paragraph = ContentBlock.Paragraph(id = "stem", markdown = text)
        val document = CapturedQuestionDocument(
            document = QuestionDocument(
                id = "document-${pending.draftId}",
                title = "手敲的函数题",
                blocks = listOf(paragraph),
            ),
            blockEvidence = listOf(
                QuestionBlockEvidence(
                    blockId = "stem",
                    sourceAssetId = "",
                    sourceRegion = null,
                    writingLayer = WritingLayer.UNKNOWN,
                    provenance = provenance,
                    confidence = null,
                    reviewStatus = QuestionBlockReviewStatus.USER_CONFIRMED,
                    producerVersion = if (provenance == QuestionBlockProvenance.USER_TRANSCRIPTION) {
                        null
                    } else {
                        "fixture-v1"
                    },
                ),
            ),
        )
        return repository.saveDraftWorkspace(
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
                        requestId = requestId,
                        occurredAtEpochMillis = occurredAtEpochMillis,
                    ),
                ),
                occurredAtEpochMillis = occurredAtEpochMillis,
            ),
        )
    }

    private companion object {
        const val MANUAL_TEXT = "求函数 f(x)=x^2 的单调区间。"
    }
}
