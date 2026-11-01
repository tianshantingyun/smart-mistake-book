package com.tingyun.smartmistakebook.feature.capture

import com.tingyun.smartmistakebook.core.ui.SmartMistakeBookTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.tingyun.smartmistakebook.core.domain.CaptureDraftSplitResult
import com.tingyun.smartmistakebook.core.domain.CaptureDraftSummary
import com.tingyun.smartmistakebook.core.domain.CaptureEntryOrigin
import com.tingyun.smartmistakebook.core.domain.CaptureRecognitionSummary
import com.tingyun.smartmistakebook.core.domain.CaptureSourcePage
import com.tingyun.smartmistakebook.core.domain.CapturedProblemCommitSummary
import com.tingyun.smartmistakebook.core.domain.ConfirmedTutorSession
import com.tingyun.smartmistakebook.core.domain.CaptureWorkflowRepository
import com.tingyun.smartmistakebook.core.domain.CaptureWritingLayer
import com.tingyun.smartmistakebook.core.domain.ModelTaskRepository
import com.tingyun.smartmistakebook.core.domain.ResumableCaptureDraft
import com.tingyun.smartmistakebook.core.model.CapturedQuestionDocument
import com.tingyun.smartmistakebook.core.model.ContentBlock
import com.tingyun.smartmistakebook.core.model.ModelTaskRequest
import com.tingyun.smartmistakebook.core.model.ModelTaskSnapshot
import com.tingyun.smartmistakebook.core.model.ProviderCapabilitySnapshot
import com.tingyun.smartmistakebook.core.model.QuestionBlockEvidence
import com.tingyun.smartmistakebook.core.model.QuestionBlockProvenance
import com.tingyun.smartmistakebook.core.model.QuestionBlockReviewStatus
import com.tingyun.smartmistakebook.core.model.QuestionDocument
import com.tingyun.smartmistakebook.core.model.WritingLayer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Screen-level composition seam for [CaptureScreen]. Component tests cover the
 * leaves; this test pins that the giant screen composable still assembles its
 * fresh-entry UI (top bar, entry actions) over empty fakes, so the planned
 * state-web refactor fails here instead of silently changing the capture flow.
 */
class CaptureScreenInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun freshTutorCaptureScreenComposesEntryModeChooser() {
        composeRule.setContent {
            SmartMistakeBookTheme {
                CaptureScreen(
                    entryOrigin = CaptureEntryOrigin.TUTOR,
                    repository = FakeCaptureRepository(),
                    modelTasks = FakeModelTasks(),
                    onOpenModelSettings = {},
                    onTutorSessionReady = {},
                    onLibraryEntryReady = {},
                    onSplitReady = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag("capture_screen").assertExists()
        composeRule.onNodeWithText("录入").assertExists()
        composeRule.onNodeWithTag("capture_mode_chooser").assertExists()
        composeRule.onNodeWithTag("capture_take_picture_button").assertExists()
        composeRule.onNodeWithTag("capture_pick_photo_button").assertExists()
        // 讲题来源不提供「整卷/PDF」：它的完成态是进入讲解，整卷管线的落点是错题本入库，
        // 在这里放一个终点不一致的入口就是死路（能力说明见 CaptureScreen 的参数 KDoc）。
        composeRule.onNodeWithTag("capture_mode_files").assertDoesNotExist()
        // A8：手动建题的完成态是错题本入库；讲题来源需要原题照片，不提供这个入口。
        composeRule.onNodeWithTag("capture_mode_manual").assertDoesNotExist()
    }

    @Test
    fun freshLibraryCaptureScreenComposesAllThreeModesAndOpensTheFileStep() {
        var fileImportOpened = 0
        composeRule.setContent {
            SmartMistakeBookTheme {
                CaptureScreen(
                    entryOrigin = CaptureEntryOrigin.LIBRARY,
                    repository = FakeCaptureRepository(),
                    modelTasks = FakeModelTasks(),
                    onOpenModelSettings = {},
                    onTutorSessionReady = {},
                    onLibraryEntryReady = {},
                    onSplitReady = {},
                    onOpenFileImport = { fileImportOpened += 1 },
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag("capture_screen").assertExists()
        composeRule.onNodeWithText("录入").assertExists()
        composeRule.onNodeWithTag("capture_mode_chooser").assertExists()
        composeRule.onNodeWithTag("capture_take_picture_button").assertExists()
        composeRule.onNodeWithTag("capture_pick_photo_button").assertExists()
        composeRule.onNodeWithTag("capture_mode_files").assertExists()
        // A8：错题本来源提供「手动建题」（无照片建题）。
        composeRule.onNodeWithTag("capture_mode_manual").assertExists()
        composeRule.captureCaptureVisualEvidence("capture-entry.png")
        composeRule.onNodeWithTag("capture_mode_files").performClick()
        composeRule.runOnIdle {
            assertEquals(1, fileImportOpened)
        }
    }

    @Test
    fun manualTranscriptionResumeShowsEditableFormWithoutSourceImage() {
        composeRule.setContent {
            SmartMistakeBookTheme {
                CaptureScreen(
                    entryOrigin = CaptureEntryOrigin.LIBRARY,
                    repository = FakeCaptureRepository(resumable = manualResumableDraft()),
                    modelTasks = FakeModelTasks(),
                    onOpenModelSettings = {},
                    onTutorSessionReady = {},
                    onLibraryEntryReady = {},
                    onSplitReady = {},
                    onBack = {},
                    resumeDraftId = "manual-draft",
                )
            }
        }

        // A8：空源草稿恢复回来就是可编辑的手动转写表单——不是崩溃，也不是空的"录入方式"。
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("capture_correction_form")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithTag("capture_screen").assertExists()
        composeRule.onNodeWithTag("capture_correction_form").assertExists()
        composeRule.onNodeWithTag("capture_transcription_input").assertExists()
        composeRule.onNodeWithTag("capture_mode_chooser").assertDoesNotExist()
        composeRule.onNodeWithText("题面仅保存在本机").assertExists()
        composeRule.onNodeWithText("原图已保存在本机").assertDoesNotExist()
        composeRule.captureCaptureVisualEvidence("capture-manual.png")
    }
}

private fun manualResumableDraft(): ResumableCaptureDraft = ResumableCaptureDraft(
    draftId = "manual-draft",
    origin = CaptureEntryOrigin.LIBRARY,
    sourceImageUri = null,
    sourceAssetId = null,
    sourceAssetSha256 = null,
    sourceWidth = null,
    sourceHeight = null,
    sourceByteSize = null,
    draftCreatedAtEpochMillis = 1_000,
    currentRevisionNumber = 1,
    currentRevisionDocumentFingerprint = "a".repeat(64),
    currentRevisionCreatedAtEpochMillis = 1_000,
    subject = null,
    title = "新拍题目",
    questionDocument = CapturedQuestionDocument(
        document = QuestionDocument(
            id = "document-manual",
            blocks = listOf(ContentBlock.Paragraph("stem", "")),
        ),
        blockEvidence = listOf(
            QuestionBlockEvidence(
                blockId = "stem",
                sourceAssetId = "",
                sourceRegion = null,
                writingLayer = WritingLayer.UNKNOWN,
                provenance = QuestionBlockProvenance.USER_TRANSCRIPTION,
                confidence = null,
                reviewStatus = QuestionBlockReviewStatus.NEEDS_REVIEW,
                producerVersion = null,
            ),
        ),
    ),
    transcription = "",
    writingLayer = CaptureWritingLayer.UNKNOWN,
    latestAssessmentTask = null,
    latestParseTask = null,
    tutorSessionId = null,
    workspace = null,
    updatedAtEpochMillis = 1_000,
)

private class FakeCaptureRepository(
    private val resumable: ResumableCaptureDraft? = null,
) : CaptureWorkflowRepository {
    override fun observePendingCaptures(): Flow<List<com.tingyun.smartmistakebook.core.domain.PendingCaptureItem>> =
        flowOf(emptyList())

    override suspend fun readPendingCapture(draftId: String): ResumableCaptureDraft? = resumable

    override suspend fun abandonPendingCapture(
        draftId: String,
        expectedRevisionNumber: Int,
        abandonedAtEpochMillis: Long,
    ): Boolean = false

    override suspend fun readDraftWorkspace(
        draftId: String,
    ): com.tingyun.smartmistakebook.core.domain.CaptureDraftWorkspaceSnapshot? = null

    override suspend fun saveDraftWorkspace(
        request: com.tingyun.smartmistakebook.core.domain.SaveCaptureDraftWorkspaceRequest,
    ): com.tingyun.smartmistakebook.core.domain.CaptureDraftWorkspaceSnapshot =
        error("not used in this test")

    override suspend fun consumeDraftWorkspace(
        request: com.tingyun.smartmistakebook.core.domain.ConsumeCaptureDraftWorkspaceRequest,
    ): Boolean = true

    override suspend fun importDraft(
        request: com.tingyun.smartmistakebook.core.domain.CaptureDraftImportRequest,
    ): CaptureDraftSummary = error("not used in this test")

    override suspend fun appendDraftPage(
        request: com.tingyun.smartmistakebook.core.domain.AppendCaptureDraftPageRequest,
    ): CaptureDraftSummary = error("not used in this test")

    override suspend fun replaceDraft(
        request: com.tingyun.smartmistakebook.core.domain.ReplaceCaptureDraftRequest,
    ): CaptureDraftSummary = error("not used in this test")

    override suspend fun splitDraft(
        request: com.tingyun.smartmistakebook.core.domain.SplitCaptureDraftRequest,
    ): CaptureDraftSplitResult = error("not used in this test")

    override suspend fun confirmForTutoring(
        request: com.tingyun.smartmistakebook.core.domain.ConfirmCapturedProblemRequest,
    ): ConfirmedTutorSession = error("not used in this test")

    override suspend fun readTutorSession(sessionId: String): ConfirmedTutorSession? = null

    override suspend fun saveTutorSession(
        request: com.tingyun.smartmistakebook.core.domain.SaveTutorSessionRequest,
    ): CapturedProblemCommitSummary = error("not used in this test")

    override suspend fun endTutorSessionWithoutSaving(
        request: com.tingyun.smartmistakebook.core.domain.EndTutorSessionWithoutSaveRequest,
    ): com.tingyun.smartmistakebook.core.domain.EndTutorSessionWithoutSaveResult = error("not used in this test")

    override suspend fun confirmAndCommit(
        request: com.tingyun.smartmistakebook.core.domain.ConfirmCapturedProblemRequest,
    ): CapturedProblemCommitSummary = error("not used in this test")

    override suspend fun attachCleanRedrawImage(
        problemRevisionId: String,
        cleanImageBytes: ByteArray,
        cleanImageMimeType: String,
    ): Boolean = false
}

private class FakeModelTasks : ModelTaskRepository {
    // The screen wraps capabilities() in runCatching; a missing provider keeps
    // the fresh-entry UI free of any external-provider gating.
    override suspend fun capabilities(): ProviderCapabilitySnapshot =
        error("no provider configured in this test")

    override fun observe(requestId: String): Flow<ModelTaskSnapshot?> = flowOf(null)

    override fun execute(request: ModelTaskRequest): Flow<ModelTaskSnapshot> =
        flow { error("not used in this test") }
}
