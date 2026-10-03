package com.tingyun.smartmistakebook.feature.capture

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.domain.CaptureDraftImportRequest
import com.tingyun.smartmistakebook.core.domain.CaptureDraftSplitResult
import com.tingyun.smartmistakebook.core.domain.CaptureDraftSummary
import com.tingyun.smartmistakebook.core.domain.CaptureDraftWorkspaceSnapshot
import com.tingyun.smartmistakebook.core.domain.CaptureEntryOrigin
import com.tingyun.smartmistakebook.core.domain.CaptureWorkflowRepository
import com.tingyun.smartmistakebook.core.domain.CapturedProblemCommitSummary
import com.tingyun.smartmistakebook.core.domain.ConfirmCapturedProblemRequest
import com.tingyun.smartmistakebook.core.domain.ConfirmedTutorSession
import com.tingyun.smartmistakebook.core.domain.EndTutorSessionWithoutSaveRequest
import com.tingyun.smartmistakebook.core.domain.EndTutorSessionWithoutSaveResult
import com.tingyun.smartmistakebook.core.domain.ModelTaskRepository
import com.tingyun.smartmistakebook.core.domain.PendingCaptureItem
import com.tingyun.smartmistakebook.core.domain.PendingCaptureStage
import com.tingyun.smartmistakebook.core.domain.ResumableCaptureDraft
import com.tingyun.smartmistakebook.core.domain.SaveCaptureDraftWorkspaceRequest
import com.tingyun.smartmistakebook.core.domain.SaveTutorSessionRequest
import com.tingyun.smartmistakebook.core.domain.AppendCaptureDraftPageRequest
import com.tingyun.smartmistakebook.core.domain.ConsumeCaptureDraftWorkspaceRequest
import com.tingyun.smartmistakebook.core.domain.ReplaceCaptureDraftRequest
import com.tingyun.smartmistakebook.core.domain.SplitCaptureDraftRequest
import com.tingyun.smartmistakebook.core.model.ModelTaskRequest
import com.tingyun.smartmistakebook.core.model.ModelTaskSnapshot
import com.tingyun.smartmistakebook.core.model.ProviderCapabilitySnapshot
import com.tingyun.smartmistakebook.core.ui.SmartMistakeBookTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * L4：录入界面入口态的「待处理」列表——恢复与废弃两条出路。
 *
 * 这些用例覆盖已删除的错题本栏 inbox 仪器化用例（随 2f0b98a9 删除）的同一批事实，
 * 列表新家是录入界面，因此在这里重建。
 */
@RunWith(AndroidJUnit4::class)
class CapturePendingCapturesInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun entryStateShowsEveryPendingStageTruthfullyAndOpensTheDraft() {
        var openedDraftId: String? = null
        val repository = FakePendingCaptureRepository(
            pending = listOf(
                pendingItem("draft-working", PendingCaptureStage.MODEL_WORKING, "函数单调性题"),
                pendingItem("draft-ready", PendingCaptureStage.READY_TO_REVIEW, "导数综合题"),
                pendingItem("draft-retake", PendingCaptureStage.RECAPTURE_REQUIRED, "立体几何题"),
            ),
        )
        composeRule.setContent {
            SmartMistakeBookTheme {
                CaptureScreen(
                    entryOrigin = CaptureEntryOrigin.LIBRARY,
                    repository = repository,
                    modelTasks = FakePendingModelTasks(),
                    onOpenModelSettings = {},
                    onTutorSessionReady = {},
                    onLibraryEntryReady = {},
                    onSplitReady = {},
                    onBack = {},
                    onOpenPendingDraft = { openedDraftId = it },
                )
            }
        }

        composeRule.onNodeWithText("待处理题目").performScrollTo().assertExists()
        composeRule.onNodeWithText("3 道临时题记录保留在本机").assertExists()
        composeRule.onNodeWithText("正在整理 1 道 · 等你继续 1 道 · 需要处理 1 道")
            .assertExists()
        composeRule.onNodeWithText("正在整理题目，可稍后再来").assertExists()
        composeRule.onNodeWithText("题面已整理，可继续").assertExists()
        composeRule.onNodeWithText("关键内容看不清，请重新拍摄").assertExists()

        composeRule.onNodeWithTag("capture_pending_draft-ready")
            .performScrollTo()
            .performClick()

        assertEquals("draft-ready", openedDraftId)
    }

    @Test
    fun tutorReadyPendingItemOpensItsWaitingTutorSession() {
        var openedSessionId: String? = null
        val repository = FakePendingCaptureRepository(
            pending = listOf(
                pendingItem(
                    draftId = "draft-tutor",
                    stage = PendingCaptureStage.TUTOR_SESSION_READY,
                    title = "待讲题",
                    tutorSessionId = "session-9",
                ),
            ),
        )
        composeRule.setContent {
            SmartMistakeBookTheme {
                CaptureScreen(
                    entryOrigin = CaptureEntryOrigin.TUTOR,
                    repository = repository,
                    modelTasks = FakePendingModelTasks(),
                    onOpenModelSettings = {},
                    onTutorSessionReady = { openedSessionId = it },
                    onLibraryEntryReady = {},
                    onSplitReady = {},
                    onBack = {},
                    onOpenPendingDraft = {},
                )
            }
        }

        composeRule.onNodeWithText("题目已保存，可以开始讲解").performScrollTo().assertExists()
        composeRule.onNodeWithTag("capture_pending_draft-tutor")
            .performScrollTo()
            .performClick()

        assertEquals("session-9", openedSessionId)
    }

    @Test
    fun discardingAPendingItemGoesThroughTheRevisionCasAndSaysWhatHappened() {
        var openedDraftId: String? = null
        val repository = FakePendingCaptureRepository(
            pending = listOf(
                pendingItem("draft-ready", PendingCaptureStage.READY_TO_REVIEW, "导数综合题"),
            ),
        )
        composeRule.setContent {
            SmartMistakeBookTheme {
                CaptureScreen(
                    entryOrigin = CaptureEntryOrigin.LIBRARY,
                    repository = repository,
                    modelTasks = FakePendingModelTasks(),
                    onOpenModelSettings = {},
                    onTutorSessionReady = {},
                    onLibraryEntryReady = {},
                    onSplitReady = {},
                    onBack = {},
                    onOpenPendingDraft = { openedDraftId = it },
                )
            }
        }

        composeRule.onNodeWithTag("capture_pending_discard_draft-ready")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText("不再保留这道题？").assertExists()
        composeRule.onNodeWithTag("capture_pending_discard_confirm").performClick()

        composeRule.waitUntil(timeoutMillis = 20_000) {
            repository.abandonedDrafts.isNotEmpty()
        }
        val abandoned = repository.abandonedDrafts.single()
        assertEquals("draft-ready", abandoned.first)
        assertEquals(1, abandoned.second)
        assertTrue(abandoned.third > 0)
        composeRule.onNodeWithText("已从待处理里移除，这道题不会进入错题本。").assertExists()
        // 废弃不等于打开：点"不再保留"不会触发恢复。
        assertEquals(null, openedDraftId)
    }

    @Test
    fun failedDiscardIsReportedInsteadOfPretendingSuccess() {
        val repository = FakePendingCaptureRepository(
            pending = listOf(
                pendingItem("draft-ready", PendingCaptureStage.READY_TO_REVIEW, "导数综合题"),
            ),
            abandonResult = false,
        )
        composeRule.setContent {
            SmartMistakeBookTheme {
                CaptureScreen(
                    entryOrigin = CaptureEntryOrigin.LIBRARY,
                    repository = repository,
                    modelTasks = FakePendingModelTasks(),
                    onOpenModelSettings = {},
                    onTutorSessionReady = {},
                    onLibraryEntryReady = {},
                    onSplitReady = {},
                    onBack = {},
                    onOpenPendingDraft = {},
                )
            }
        }

        composeRule.onNodeWithTag("capture_pending_discard_draft-ready")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("capture_pending_discard_confirm").performClick()

        composeRule.waitUntil(timeoutMillis = 20_000) {
            repository.abandonedDrafts.isNotEmpty()
        }
        composeRule.onNodeWithText(
            "这次没有移除成功：这道题可能刚有更新或已处理，请稍后再试。",
        ).assertExists()
        assertTrue(repository.abandonedDrafts.single().first == "draft-ready")
    }

    @Test
    fun entryStateWithoutPendingItemsShowsACalmEmptyMessage() {
        val repository = FakePendingCaptureRepository(pending = emptyList())
        composeRule.setContent {
            SmartMistakeBookTheme {
                CaptureScreen(
                    entryOrigin = CaptureEntryOrigin.LIBRARY,
                    repository = repository,
                    modelTasks = FakePendingModelTasks(),
                    onOpenModelSettings = {},
                    onTutorSessionReady = {},
                    onLibraryEntryReady = {},
                    onSplitReady = {},
                    onBack = {},
                    onOpenPendingDraft = {},
                )
            }
        }

        composeRule.onNodeWithTag("capture_pending_empty").performScrollTo().assertExists()
        composeRule.onNodeWithText("没有待处理题目").assertExists()
    }

    private fun pendingItem(
        draftId: String,
        stage: PendingCaptureStage,
        title: String,
        tutorSessionId: String? = null,
    ) = PendingCaptureItem(
        draftId = draftId,
        origin = if (tutorSessionId != null) {
            CaptureEntryOrigin.TUTOR
        } else {
            CaptureEntryOrigin.LIBRARY
        },
        subject = "数学",
        title = title,
        currentRevisionNumber = 1,
        updatedAtEpochMillis = 1_752_988_800_000,
        stage = stage,
        tutorSessionId = tutorSessionId,
    )
}

private class FakePendingCaptureRepository(
    private val pending: List<PendingCaptureItem>,
    private val abandonResult: Boolean = true,
) : CaptureWorkflowRepository {
    val abandonedDrafts = mutableListOf<Triple<String, Int, Long>>()

    override fun observePendingCaptures(): Flow<List<PendingCaptureItem>> = flowOf(pending)

    override suspend fun abandonPendingCapture(
        draftId: String,
        expectedRevisionNumber: Int,
        abandonedAtEpochMillis: Long,
    ): Boolean {
        abandonedDrafts += Triple(draftId, expectedRevisionNumber, abandonedAtEpochMillis)
        return abandonResult
    }

    override suspend fun readPendingCapture(draftId: String): ResumableCaptureDraft? = null

    override suspend fun readDraftWorkspace(draftId: String): CaptureDraftWorkspaceSnapshot? = null

    override suspend fun saveDraftWorkspace(
        request: SaveCaptureDraftWorkspaceRequest,
    ): CaptureDraftWorkspaceSnapshot = error("not used in this test")

    override suspend fun consumeDraftWorkspace(
        request: ConsumeCaptureDraftWorkspaceRequest,
    ): Boolean = true

    override suspend fun importDraft(request: CaptureDraftImportRequest): CaptureDraftSummary =
        error("not used in this test")

    override suspend fun appendDraftPage(request: AppendCaptureDraftPageRequest): CaptureDraftSummary =
        error("not used in this test")

    override suspend fun replaceDraft(request: ReplaceCaptureDraftRequest): CaptureDraftSummary =
        error("not used in this test")

    override suspend fun splitDraft(request: SplitCaptureDraftRequest): CaptureDraftSplitResult =
        error("not used in this test")

    override suspend fun confirmForTutoring(
        request: ConfirmCapturedProblemRequest,
    ): ConfirmedTutorSession = error("not used in this test")

    override suspend fun readTutorSession(sessionId: String): ConfirmedTutorSession? = null

    override suspend fun saveTutorSession(
        request: SaveTutorSessionRequest,
    ): CapturedProblemCommitSummary = error("not used in this test")

    override suspend fun endTutorSessionWithoutSaving(
        request: EndTutorSessionWithoutSaveRequest,
    ): EndTutorSessionWithoutSaveResult = error("not used in this test")

    override suspend fun confirmAndCommit(
        request: ConfirmCapturedProblemRequest,
    ): CapturedProblemCommitSummary = error("not used in this test")

    override suspend fun attachCleanRedrawImage(
        problemRevisionId: String,
        cleanImageBytes: ByteArray,
        cleanImageMimeType: String,
    ): Boolean = false
}

private class FakePendingModelTasks : ModelTaskRepository {
    override suspend fun capabilities(): ProviderCapabilitySnapshot =
        error("no provider configured in this test")

    override fun observe(requestId: String): Flow<ModelTaskSnapshot?> = flowOf(null)

    override fun execute(request: ModelTaskRequest): Flow<ModelTaskSnapshot> =
        flow { error("not used in this test") }
}
