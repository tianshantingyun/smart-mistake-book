package com.tingyun.smartmistakebook.feature.capture

import com.tingyun.smartmistakebook.core.domain.CaptureSourcePage
import com.tingyun.smartmistakebook.core.model.CaptureAssessment
import com.tingyun.smartmistakebook.core.model.CaptureAssessmentAction
import com.tingyun.smartmistakebook.core.model.CaptureAssessmentDecision
import com.tingyun.smartmistakebook.core.model.CaptureAssessmentIssue
import com.tingyun.smartmistakebook.core.model.CaptureAssessmentIssueCode
import com.tingyun.smartmistakebook.core.model.CaptureAssessmentOrigin
import com.tingyun.smartmistakebook.core.model.CaptureAssessmentOutput
import com.tingyun.smartmistakebook.core.model.CaptureAssessmentSeverity
import com.tingyun.smartmistakebook.core.model.ModelExecutionLocation
import com.tingyun.smartmistakebook.core.model.ModelTaskFingerprint
import com.tingyun.smartmistakebook.core.model.ModelTaskKind
import com.tingyun.smartmistakebook.core.model.ModelTaskRequest
import com.tingyun.smartmistakebook.core.model.ModelTaskSnapshot
import com.tingyun.smartmistakebook.core.model.ModelTaskStage
import com.tingyun.smartmistakebook.core.model.ModelTaskStatus
import com.tingyun.smartmistakebook.core.model.NormalizedSourceRegion
import com.tingyun.smartmistakebook.core.model.ProviderCapabilitySnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureModelTaskPolicyTest {
    @Test
    fun failedAndCancelledTasksClearAuthorization() {
        assertTrue(captureFailedTaskClearsAuthorization(ModelTaskStatus.RETRYABLE_FAILURE))
        assertTrue(captureFailedTaskClearsAuthorization(ModelTaskStatus.PERMANENT_FAILURE))
        assertTrue(captureFailedTaskClearsAuthorization(ModelTaskStatus.CANCELLED))
        assertFalse(captureFailedTaskClearsAuthorization(ModelTaskStatus.SUCCEEDED))
        assertFalse(captureFailedTaskClearsAuthorization(ModelTaskStatus.RUNNING))
        assertFalse(captureFailedTaskClearsAuthorization(null))
    }

    @Test
    fun splitRunsOnlyForASucceededNonDemoSinglePage() {
        val page = page(0, ASSET_A)
        val snapshot = succeededAssessment(
            requestId = "assess-split",
            assetId = ASSET_A,
            assessment = splitAssessment(),
            demo = false,
        )

        val decision = captureSplitDecision(
            snapshot = snapshot,
            draftId = DRAFT_ID,
            revisionNumber = 2,
            sourcePages = listOf(page),
        )

        assertTrue(decision is CaptureSplitDecision.Run)
        val run = decision as CaptureSplitDecision.Run
        assertEquals(DRAFT_ID, run.draftId)
        assertEquals(2, run.revisionNumber)
        assertEquals(ASSET_A, run.page.sourceAssetId)
        assertEquals(CaptureAssessmentDecision.SPLIT, run.assessment.decision)

        val request = captureSplitDraftRequest(run)
        assertEquals("capture-split:assess-split", request.requestId)
        assertEquals(DRAFT_ID, request.draftId)
        assertEquals(2, request.expectedRevisionNumber)
        assertEquals("assess-split", request.assessmentRequestId)
        assertEquals(ASSET_A, request.sourceAssetId)
        assertEquals(snapshot.updatedAtEpochMillis, request.occurredAtEpochMillis)
        assertEquals(2, request.regions.size)
    }

    @Test
    fun splitNeedsASinglePageAndSkipsDemoOrMismatchedAssets() {
        val snapshot = succeededAssessment(
            requestId = "assess-split",
            assetId = ASSET_A,
            assessment = splitAssessment(),
            demo = false,
        )

        assertEquals(
            CaptureSplitDecision.NeedSinglePage,
            captureSplitDecision(snapshot, DRAFT_ID, 1, listOf(page(0, ASSET_A), page(1, ASSET_B))),
        )
        assertEquals(
            CaptureSplitDecision.Skip,
            captureSplitDecision(
                snapshot.copy(provider = provider(demo = true)),
                DRAFT_ID,
                1,
                listOf(page(0, ASSET_A)),
            ),
        )
        assertEquals(
            CaptureSplitDecision.Skip,
            captureSplitDecision(snapshot, DRAFT_ID, 1, listOf(page(0, ASSET_B))),
        )
        assertEquals(
            CaptureSplitDecision.Skip,
            captureSplitDecision(null, DRAFT_ID, 1, listOf(page(0, ASSET_A))),
        )
        assertEquals(
            CaptureSplitDecision.Skip,
            captureSplitDecision(
                succeededAssessment(
                    requestId = "assess-pass",
                    assetId = ASSET_A,
                    assessment = passAssessment(),
                    demo = false,
                ),
                DRAFT_ID,
                1,
                listOf(page(0, ASSET_A)),
            ),
        )
    }

    /**
     * A3②③：模型层不再判分题区域（只保留 wire 形状与坐标合法性），区域能不能切改由消费侧判：
     * 超过 12 个 / 尺寸过小 / 重叠超限 / 重复 / 一个都没有，自动提交一律被拒
     * ——不切、整页草稿、可重试。但选择卡必须照旧出现（判定仍返回 Run）：
     * 判据只挡自动提交，不挡手动框选这条退路。
     */
    @Test
    fun autoSplitRejectsModelRegionsTheLocalCutCannotUse() {
        val unusable = listOf(
            emptyList(),
            List(13) { index ->
                NormalizedSourceRegion(0.05, index * 0.07, 0.95, index * 0.07 + 0.06)
            },
            listOf(
                NormalizedSourceRegion(0.05, 0.05, 0.95, 0.45),
                NormalizedSourceRegion(0.05, 0.55, 0.13, 0.62),
            ),
            listOf(
                NormalizedSourceRegion(0.05, 0.05, 0.5, 0.5),
                NormalizedSourceRegion(0.06, 0.06, 0.45, 0.45),
            ),
            listOf(
                NormalizedSourceRegion(0.05, 0.05, 0.95, 0.45),
                NormalizedSourceRegion(0.05, 0.05, 0.95, 0.45),
            ),
        )

        unusable.forEach { regions ->
            val decision = captureSplitDecision(
                snapshot = succeededAssessment(
                    requestId = "assess-split",
                    assetId = ASSET_A,
                    assessment = splitAssessment(regions),
                    demo = false,
                ),
                draftId = DRAFT_ID,
                revisionNumber = 1,
                sourcePages = listOf(page(0, ASSET_A)),
            )

            assertTrue("判定仍要给出选择卡：$regions", decision is CaptureSplitDecision.Run)
            assertFalse("自动提交必须被拒：$regions", captureAutoSplitRegionsUsable(decision))
        }
    }

    @Test
    fun autoSplitAcceptsAModelRegionSetTheLocalCutCanUse() {
        val decision = captureSplitDecision(
            snapshot = succeededAssessment(
                requestId = "assess-split",
                assetId = ASSET_A,
                assessment = splitAssessment(),
                demo = false,
            ),
            draftId = DRAFT_ID,
            revisionNumber = 1,
            sourcePages = listOf(page(0, ASSET_A)),
        )

        assertTrue(decision is CaptureSplitDecision.Run)
        assertTrue(captureAutoSplitRegionsUsable(decision))
    }

    @Test
    fun parseReadinessAllowsNeedMoreImageOnlyOnEarlierPages() {
        val pages = listOf(page(0, ASSET_A), page(1, ASSET_B))
        val firstNeedsMore = succeededAssessment(
            requestId = "assess-a",
            assetId = ASSET_A,
            assessment = needMoreAssessment(),
            demo = false,
        )
        val secondPass = succeededAssessment(
            requestId = "assess-b",
            assetId = ASSET_B,
            assessment = passAssessment(),
            demo = false,
        )

        val ready = captureParseReadiness(pages, listOf(firstNeedsMore, secondPass))
        assertEquals(listOf("assess-a", "assess-b"), ready?.assessmentRequestIds)
        assertEquals(20L, ready?.occurredAtEpochMillis)

        assertNull(captureParseReadiness(pages, listOf(secondPass, firstNeedsMore)))
        assertNull(captureParseReadiness(pages, listOf(firstNeedsMore, null)))
        assertNull(captureParseReadiness(emptyList(), emptyList()))
        assertFalse(captureAssessmentsReadyForParse(pages, listOf(firstNeedsMore)))
    }

    @Test
    fun dispatchPrefersMatchingRecoveryThenPersistedRequest() {
        val pending = captureAssessmentRequest(
            requestId = "assess-1",
            draftId = DRAFT_ID,
            sourceAssetId = ASSET_A,
            origin = CaptureAssessmentOrigin.LIBRARY,
            imageWidth = 10,
            imageHeight = 20,
            occurredAtEpochMillis = 1,
            agentConsentGranted = false,
        )
        val persisted = captureAssessmentRequest(
            requestId = "assess-1",
            draftId = DRAFT_ID,
            sourceAssetId = ASSET_A,
            origin = CaptureAssessmentOrigin.TUTOR,
            imageWidth = 10,
            imageHeight = 20,
            occurredAtEpochMillis = 2,
            agentConsentGranted = false,
        )
        val built = captureAssessmentRequest(
            requestId = "assess-1",
            draftId = DRAFT_ID,
            sourceAssetId = ASSET_A,
            origin = CaptureAssessmentOrigin.LIBRARY,
            imageWidth = 10,
            imageHeight = 20,
            occurredAtEpochMillis = 3,
            agentConsentGranted = false,
        )

        assertSame(
            pending,
            captureTaskRequestToDispatch("assess-1", pending, persisted) { built },
        )
        assertSame(
            persisted,
            captureParseRequestToDispatch("assess-1", null, persisted) { built },
        )
        assertSame(
            built,
            captureTaskRequestToDispatch("assess-1", null, null) { built },
        )
        val otherPending = captureAssessmentRequest(
            requestId = "assess-other",
            draftId = DRAFT_ID,
            sourceAssetId = ASSET_A,
            origin = CaptureAssessmentOrigin.LIBRARY,
            imageWidth = 10,
            imageHeight = 20,
            occurredAtEpochMillis = 4,
            agentConsentGranted = false,
        )
        assertSame(
            built,
            captureTaskRequestToDispatch("assess-1", otherPending, null) { built },
        )
    }

    @Test
    fun pageSnapshotUpdateReplacesOnlyTheMatchingAsset() {
        val pages = listOf(page(0, ASSET_A), page(1, ASSET_B))
        val first = succeededAssessment("assess-a", ASSET_A, passAssessment(), demo = false)
        val second = succeededAssessment("assess-b", ASSET_B, passAssessment(), demo = false)
        val updated = captureUpdatedPageAssessmentSnapshots(
            sourcePages = pages,
            existing = listOf(first, null),
            snapshot = second,
        )
        assertSame(first, updated[0])
        assertSame(second, updated[1])
        assertEquals(
            listOf(first, null),
            captureUpdatedPageAssessmentSnapshots(pages, listOf(first, null), null),
        )
    }

    @Test
    fun appendKeepsEarlierSnapshotsAndClearsFromTheNewPage() {
        val first = succeededAssessment("assess-a", ASSET_A, passAssessment(), demo = false)
        val second = succeededAssessment("assess-b", ASSET_B, passAssessment(), demo = false)
        assertEquals(
            listOf(first, null, null),
            captureAppendedPageAssessmentSnapshots(
                existing = listOf(first, second),
                newPageCount = 3,
                appendedPageIndex = 1,
            ),
        )
    }

    @Test
    fun resumePicksTheFirstUnsuccessfulPageOrTheLastPage() {
        val first = succeededAssessment("assess-a", ASSET_A, passAssessment(), demo = false)
        val second = succeededAssessment("assess-b", ASSET_B, passAssessment(), demo = false)
            .copy(status = ModelTaskStatus.RUNNING, output = null, stage = ModelTaskStage.WAITING)
        assertEquals(1, captureResumePendingAssessmentPageIndex(2, listOf(first, second)))
        assertEquals(1, captureResumePendingAssessmentPageIndex(2, listOf(first, first)))
        assertEquals(0, captureResumePendingAssessmentPageIndex(1, listOf(null)))
        assertEquals(0, captureResumePendingAssessmentPageIndex(0, emptyList()))
    }

    private fun succeededAssessment(
        requestId: String,
        assetId: String,
        assessment: CaptureAssessment,
        demo: Boolean,
    ): ModelTaskSnapshot {
        val request = captureAssessmentRequest(
            requestId = requestId,
            draftId = DRAFT_ID,
            sourceAssetId = assetId,
            origin = CaptureAssessmentOrigin.LIBRARY,
            imageWidth = 10,
            imageHeight = 20,
            occurredAtEpochMillis = if (assetId == ASSET_B) 20 else 10,
            agentConsentGranted = false,
        )
        return ModelTaskSnapshot(
            taskId = "task-$requestId",
            request = request,
            requestFingerprint = ModelTaskFingerprint.of(request),
            status = ModelTaskStatus.SUCCEEDED,
            stateVersion = 3,
            stage = ModelTaskStage.COMPLETE,
            userMessage = "已完成",
            attemptCount = 1,
            provider = provider(demo),
            output = CaptureAssessmentOutput(assessment),
            createdAtEpochMillis = 100,
            updatedAtEpochMillis = 200,
        )
    }

    private fun splitAssessment(
        regions: List<NormalizedSourceRegion> = listOf(
            NormalizedSourceRegion(0.05, 0.05, 0.95, 0.45),
            NormalizedSourceRegion(0.05, 0.55, 0.95, 0.95),
        ),
    ) = CaptureAssessment(
        decision = CaptureAssessmentDecision.SPLIT,
        issues = listOf(
            CaptureAssessmentIssue(
                code = CaptureAssessmentIssueCode.MULTIPLE_QUESTIONS,
                severity = CaptureAssessmentSeverity.BLOCKING,
                message = "画面中有两道独立题目",
            ),
        ),
        suggestedActions = emptyList(),
        questionRegions = regions,
        modelVersion = "fixture-v1",
    )

    private fun passAssessment() = CaptureAssessment(
        decision = CaptureAssessmentDecision.PASS,
        issues = emptyList(),
        suggestedActions = emptyList(),
        modelVersion = "fixture-v1",
    )

    private fun needMoreAssessment() = CaptureAssessment(
        decision = CaptureAssessmentDecision.NEED_MORE_IMAGE,
        issues = listOf(
            CaptureAssessmentIssue(
                code = CaptureAssessmentIssueCode.MISSING_OPTIONS,
                severity = CaptureAssessmentSeverity.BLOCKING,
                message = "选项没有拍全",
            ),
        ),
        suggestedActions = listOf(CaptureAssessmentAction.ADD_IMAGE),
        modelVersion = "fixture-v1",
    )

    private fun provider(demo: Boolean) = ProviderCapabilitySnapshot(
        providerId = if (demo) "demo" else "external",
        providerDisplayName = if (demo) "演示" else "外部",
        modelId = "model-v1",
        supportedTasks = setOf(ModelTaskKind.CAPTURE_ASSESS),
        supportsImageInput = true,
        supportsStructuredOutput = true,
        supportsStreaming = false,
        isDemo = demo,
        executionLocation = if (demo) {
            ModelExecutionLocation.LOCAL_NO_EGRESS
        } else {
            ModelExecutionLocation.EXTERNAL_PROVIDER
        },
        providerConfigurationVersion = "v1",
    )

    private fun page(index: Int, assetId: String) = CaptureSourcePage(
        pageIndex = index,
        imageUri = "file:///$assetId.png",
        sourceAssetId = assetId,
        sourceAssetSha256 = "a".repeat(64),
        width = 10,
        height = 20,
        byteSize = 100L,
        createdAtEpochMillis = 1,
    )

    private companion object {
        const val DRAFT_ID = "draft-model-task-policy"
        const val ASSET_A = "asset-a"
        const val ASSET_B = "asset-b"
    }
}
