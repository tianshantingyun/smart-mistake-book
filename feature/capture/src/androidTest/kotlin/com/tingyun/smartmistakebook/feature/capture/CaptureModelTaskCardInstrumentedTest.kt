package com.tingyun.smartmistakebook.feature.capture

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performScrollTo
import com.tingyun.smartmistakebook.core.ui.SmartMistakeBookTheme
import org.junit.Assert.assertEquals

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.tingyun.smartmistakebook.core.model.CaptureAssessment
import com.tingyun.smartmistakebook.core.model.CaptureAssessmentDecision
import com.tingyun.smartmistakebook.core.model.CaptureAssessmentAction
import com.tingyun.smartmistakebook.core.model.CaptureAssessmentIssue
import com.tingyun.smartmistakebook.core.model.CaptureAssessmentIssueCode
import com.tingyun.smartmistakebook.core.model.CaptureAssessmentSeverity
import com.tingyun.smartmistakebook.core.model.CaptureAssessmentInput
import com.tingyun.smartmistakebook.core.model.CaptureAssessmentOrigin
import com.tingyun.smartmistakebook.core.model.CaptureAssessmentOutput
import com.tingyun.smartmistakebook.core.model.ModelFailureCode
import com.tingyun.smartmistakebook.core.model.ModelTaskFailure
import com.tingyun.smartmistakebook.core.model.ModelTaskFingerprint
import com.tingyun.smartmistakebook.core.model.ModelTaskKind
import com.tingyun.smartmistakebook.core.model.ModelTaskRequest
import com.tingyun.smartmistakebook.core.model.ModelTaskSnapshot
import com.tingyun.smartmistakebook.core.model.ModelTaskStage
import com.tingyun.smartmistakebook.core.model.ModelTaskStatus
import com.tingyun.smartmistakebook.core.model.NormalizedSourceRegion
import com.tingyun.smartmistakebook.core.model.ProviderCapabilitySnapshot
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CaptureModelTaskCardInstrumentedTest {
    @Test fun splitChoiceActionsKeepFortyEightDpTargetsAtDoubleFontScale() {
        val base = succeededDemoSnapshot()
        val snapshot = base.copy(provider = requireNotNull(base.provider).copy(isDemo = false),
            output = CaptureAssessmentOutput(CaptureAssessment(
                decision = CaptureAssessmentDecision.SPLIT,
                issues = listOf(CaptureAssessmentIssue(code = CaptureAssessmentIssueCode.MULTIPLE_QUESTIONS,
                    severity = CaptureAssessmentSeverity.BLOCKING, message = "画面中有多道独立题目")),
                suggestedActions = emptyList(),
                questionRegions = listOf(NormalizedSourceRegion(0.05, 0.05, 0.95, 0.45),
                    NormalizedSourceRegion(0.05, 0.55, 0.95, 0.95)), modelVersion = "demo/capture-v1")))
        var autoCalls = 0
        var manualCalls = 0
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) { SmartMistakeBookTheme {
                Column(Modifier.width(320.dp).verticalScroll(rememberScrollState())) {
                    CaptureModelTaskCard(snapshot, onRetry = {}, onOpenModelSettings = {},
                        splitPendingChoice = true, splitInProgress = false,
                        onAutoSplit = { autoCalls++ }, onManualSplit = { manualCalls++ })
                }
            } }
        }
        val minimumPx = with(composeRule.density) { 48.dp.toPx() }
        listOf("capture_split_auto_button", "capture_split_manual_button").forEach { tag ->
            val node = composeRule.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
            assertTrue("$tag must remain at least 48dp high", node.fetchSemanticsNode().boundsInRoot.height >= minimumPx)
            node.performClick()
        }
        composeRule.runOnIdle { assertEquals(1, autoCalls); assertEquals(1, manualCalls) }
    }
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun demoProviderRoutesToModelSettingsWithoutManualEntry() {
        var settingsOpened = false
        composeRule.setContent {
            MaterialTheme {
                CaptureModelTaskCard(
                    snapshot = succeededDemoSnapshot(),
                    onRetry = {},
                    onOpenModelSettings = { settingsOpened = true },
                )
            }
        }

        composeRule.onNodeWithText("请先设置模型").assertExists()
        composeRule.onNodeWithTag("capture_manual_entry_button").assertDoesNotExist()
        composeRule.onNodeWithTag("capture_model_settings_button").performClick()
        composeRule.runOnIdle { assertTrue(settingsOpened) }
    }

    @Test
    fun missingModelShowsOnlySettingsRecovery() {
        var settingsOpened = false
        composeRule.setContent {
            MaterialTheme {
                CaptureModelTaskCard(
                    snapshot = failureSnapshot(ModelFailureCode.MODEL_NOT_CONFIGURED),
                    onRetry = {},
                    onOpenModelSettings = { settingsOpened = true },
                )
            }
        }

        composeRule.onNodeWithTag("capture_manual_entry_button").assertDoesNotExist()
        composeRule.onNodeWithTag("capture_model_retry_button").assertDoesNotExist()
        composeRule.onNodeWithTag("capture_model_egress_consent_card").assertDoesNotExist()
        composeRule.onNodeWithTag("capture_model_egress_approve_button").assertDoesNotExist()
        composeRule.onNodeWithText("继续整理这道题").assertDoesNotExist()
        composeRule.onNodeWithText("本次只发送当前题图").assertDoesNotExist()
        composeRule.onNodeWithTag("capture_model_settings_button").performClick()
        composeRule.runOnIdle { assertTrue(settingsOpened) }
    }

    @Test
    fun temporaryParseFailureInvokesOnlyTheParseRetry() {
        var assessmentRetried = false
        var parseRetried = false
        composeRule.setContent {
            MaterialTheme {
                CaptureModelTaskCard(
                    snapshot = failureSnapshot(ModelFailureCode.NETWORK_UNAVAILABLE),
                    parseSnapshot = failureSnapshot(ModelFailureCode.TIMEOUT).copy(taskId = "parse"),
                    onRetry = { assessmentRetried = true },
                    onRetryParse = { parseRetried = true },
                    onOpenModelSettings = {},
                )
            }
        }

        composeRule.onNodeWithTag("capture_manual_entry_button").assertDoesNotExist()
        composeRule.onNodeWithTag("capture_model_retry_button").performClick()
        composeRule.runOnIdle {
            assertTrue(parseRetried)
            assertTrue(!assessmentRetried)
        }
    }

    @Test
    fun cancelledTaskOffersRestartWithoutPushingManualEntry() {
        var retried = false
        composeRule.setContent {
            MaterialTheme {
                CaptureModelTaskCard(
                    snapshot = cancelledSnapshot(),
                    onRetry = { retried = true },
                    onOpenModelSettings = {},
                )
            }
        }

        composeRule.onNodeWithText("整理已停止").assertExists()
        composeRule.onNodeWithText("处理已停止，原图已保存。可重新整理。").assertExists()
        composeRule.onNodeWithTag("capture_manual_entry_button").assertDoesNotExist()
        composeRule.onNodeWithTag("capture_model_retry_button").performClick()
        composeRule.runOnIdle { assertTrue(retried) }
    }

    @Test
    fun incompleteQuestionOnlyOffersAppendWithoutDiscardingSavedPages() {
        var addPageRequested = false
        val snapshot = succeededDemoSnapshot().copy(
            output = CaptureAssessmentOutput(
                CaptureAssessment(
                    decision = CaptureAssessmentDecision.NEED_MORE_IMAGE,
                    issues = listOf(
                        CaptureAssessmentIssue(
                            code = CaptureAssessmentIssueCode.KEY_TEXT_UNREADABLE,
                            severity = CaptureAssessmentSeverity.BLOCKING,
                            message = "题干延续到了下一页",
                        ),
                    ),
                    suggestedActions = listOf(CaptureAssessmentAction.ADD_IMAGE),
                    modelVersion = "demo/capture-v1",
                ),
            ),
        )
        composeRule.setContent {
            MaterialTheme {
                CaptureModelTaskCard(
                    snapshot = snapshot,
                    onRetry = {},
                    onOpenModelSettings = {},
                    onAddPage = { addPageRequested = true },
                )
            }
        }

        composeRule.onNodeWithText("已保存的页面不会丢失", substring = true).assertExists()
        composeRule.onNodeWithTag("capture_manual_entry_button").assertDoesNotExist()
        composeRule.onNodeWithTag("capture_model_add_page_button").performClick()
        composeRule.runOnIdle {
            assertTrue(addPageRequested)
        }
    }

    @Test
    fun multipleQuestionsAreOrganizedWithoutAskingForManualCropping() {
        val snapshot = succeededDemoSnapshot().copy(
            provider = succeededDemoSnapshot().provider?.copy(isDemo = false),
            output = CaptureAssessmentOutput(
                CaptureAssessment(
                    decision = CaptureAssessmentDecision.SPLIT,
                    issues = listOf(
                        CaptureAssessmentIssue(
                            code = CaptureAssessmentIssueCode.MULTIPLE_QUESTIONS,
                            severity = CaptureAssessmentSeverity.BLOCKING,
                            message = "画面中有多道独立题目",
                        ),
                    ),
                    suggestedActions = emptyList(),
                    questionRegions = listOf(
                        NormalizedSourceRegion(0.05, 0.05, 0.95, 0.45),
                        NormalizedSourceRegion(0.05, 0.55, 0.95, 0.95),
                    ),
                    modelVersion = "demo/capture-v1",
                ),
            ),
        )
        composeRule.setContent {
            MaterialTheme {
                CaptureModelTaskCard(
                    snapshot = snapshot,
                    onRetry = {},
                    onOpenModelSettings = {},
                    splitInProgress = true,
                )
            }
        }

        composeRule.onNodeWithText("正在拆分本页题目，原图已保存。").assertExists()
        composeRule.onNodeWithTag("capture_model_retake_button").assertDoesNotExist()
        composeRule.onNodeWithTag("capture_manual_entry_button").assertDoesNotExist()
    }

    private fun succeededDemoSnapshot(): ModelTaskSnapshot {
        val request = request()
        return ModelTaskSnapshot(
            taskId = "task-1",
            request = request,
            requestFingerprint = ModelTaskFingerprint.of(request),
            status = ModelTaskStatus.SUCCEEDED,
            stateVersion = 4,
            stage = ModelTaskStage.COMPLETE,
            userMessage = "图片理解已完成",
            attemptCount = 1,
            provider = ProviderCapabilitySnapshot(
                providerId = "demo",
                providerDisplayName = "演示模型",
                modelId = "capture-demo-v1",
                supportedTasks = setOf(ModelTaskKind.CAPTURE_ASSESS),
                supportsImageInput = true,
                supportsStructuredOutput = true,
                supportsStreaming = true,
                isDemo = true,
            ),
            output = CaptureAssessmentOutput(
                CaptureAssessment(
                    decision = CaptureAssessmentDecision.PASS,
                    issues = emptyList(),
                    suggestedActions = emptyList(),
                    modelVersion = "demo/capture-v1",
                ),
            ),
            createdAtEpochMillis = 100,
            updatedAtEpochMillis = 200,
        )
    }

    private fun failureSnapshot(code: ModelFailureCode): ModelTaskSnapshot {
        val request = request()
        return ModelTaskSnapshot(
            taskId = "task-1",
            request = request,
            requestFingerprint = ModelTaskFingerprint.of(request),
            status = ModelTaskStatus.RETRYABLE_FAILURE,
            stateVersion = 2,
            stage = ModelTaskStage.PREPARING,
            userMessage = "配置可用的模型后会从这里继续",
            attemptCount = 0,
            failure = ModelTaskFailure(
                code = code,
                message = "这次没有完成",
                retryable = true,
            ),
            createdAtEpochMillis = 100,
            updatedAtEpochMillis = 200,
        )
    }

    private fun cancelledSnapshot(): ModelTaskSnapshot {
        val request = request()
        return ModelTaskSnapshot(
            taskId = "task-cancelled",
            request = request,
            requestFingerprint = ModelTaskFingerprint.of(request),
            status = ModelTaskStatus.CANCELLED,
            stateVersion = 3,
            stage = ModelTaskStage.WAITING,
            userMessage = "任务已结束",
            attemptCount = 0,
            createdAtEpochMillis = 100,
            updatedAtEpochMillis = 200,
        )
    }

    private fun request() = ModelTaskRequest(
        requestId = "capture-assess:ui-test",
        input = CaptureAssessmentInput(
            draftId = "draft-ui-test",
            sourceAssetId = "asset-ui-test",
            origin = CaptureAssessmentOrigin.LIBRARY,
            imageWidth = 1080,
            imageHeight = 1440,
        ),
        occurredAtEpochMillis = 100,
    )
}
