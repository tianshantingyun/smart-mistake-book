package com.tingyun.smartmistakebook.feature.library

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.domain.MistakeDetail
import com.tingyun.smartmistakebook.core.domain.MistakeDetailIdentity
import com.tingyun.smartmistakebook.core.domain.MistakeDetailState
import com.tingyun.smartmistakebook.core.domain.MistakeSourceSet
import com.tingyun.smartmistakebook.core.model.CapturedQuestionDocument
import com.tingyun.smartmistakebook.core.model.ContentBlock
import com.tingyun.smartmistakebook.core.model.ErrorCause
import com.tingyun.smartmistakebook.core.model.ErrorCauseAcceptanceSource
import com.tingyun.smartmistakebook.core.model.ErrorCauseChannel
import com.tingyun.smartmistakebook.core.model.ErrorCauseDetail
import com.tingyun.smartmistakebook.core.model.QuestionDocument
import com.tingyun.smartmistakebook.core.ui.SmartMistakeBookTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 批次 50 / 5c：错因候选面板的设备级 UI 契约。
 *
 * 钉四件事（都是"说错话"的失败，而不是排版）：
 * 1. 标题与落款必须说明"还不是标签、不进错题本归类/筛选/卡片"——D-001 冻结合同；
 * 2. 门关闭时必须如实写"模型不会自动写"；
 * 3. 确认/撤销只在点击时回调，busy 时按钮禁用（双击不写两次）；
 * 4. 面板**不挂在历史版本**上（旧修订只读，确认会挂到看不见的版本；这是 app 层
 *    host 的 scope 风险，页面侧必须挡住入口）。
 */
@RunWith(AndroidJUnit4::class)
class MistakeErrorCausePanelInstrumentedTest {

    @get:Rule val composeRule = createComposeRule()

    @Test
    fun panelStatesItIsNotALabelAndKeepsTheAutoGateHonest() {
        showPanel(ErrorCausePanelState(autoBookingClosed = true))

        composeRule.onNodeWithText(ERROR_CAUSE_PANEL_TITLE).assertIsDisplayed()
        composeRule.onNodeWithTag("mistake_detail_error_cause_legend").assertIsDisplayed()
        composeRule.onNodeWithText(ERROR_CAUSE_FOOTNOTE, substring = true).assertIsDisplayed()
        composeRule.onNodeWithText(ERROR_CAUSE_GATE_CLOSED_FOOTNOTE, substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag("mistake_detail_error_cause_empty").assertIsDisplayed()
    }

    @Test
    fun candidateRowShowsCauseDetailConfidenceAndConfirmCallsBack() {
        var confirmed: String? = null
        showPanel(
            ErrorCausePanelState(
                candidates = listOf(
                    ErrorCauseCandidateItem(
                        observationId = "obs-1",
                        cause = ErrorCause.PROCEDURAL,
                        detail = ErrorCauseDetail.PROC_STEP_ORDER,
                        confidence = 0.864,
                        channel = ErrorCauseChannel.TUTOR_TRACE,
                        rationaleMarkdown = "学生把两步顺序调换了",
                        observedAtEpochMillis = 1_000,
                        evidenceCount = 1,
                    ),
                ),
            ),
            onConfirm = { confirmed = it },
        )

        composeRule.onNodeWithText("E02 步骤执行").assertIsDisplayed()
        composeRule.onNodeWithText("步骤顺序错").assertIsDisplayed()
        composeRule.onNodeWithText("86%", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("讲题记录", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag("mistake_detail_error_cause_candidate_obs-1").assertIsDisplayed()

        composeRule.onNodeWithTag("mistake_detail_error_cause_confirm_obs-1").performClick()
        assertEquals("obs-1", confirmed)
    }

    @Test
    fun activeTagOffersUndoAndBusyDisablesBothActions() {
        var undone: String? = null
        showPanel(
            ErrorCausePanelState(
                active = ErrorCauseActiveTag(
                    bindingId = "binding-1",
                    cause = ErrorCause.CONCEPTUAL,
                    detail = ErrorCauseDetail.CONCEPT_RULE_INVALID,
                    acceptanceSource = ErrorCauseAcceptanceSource.USER_CONFIRMED,
                    confidence = 0.9,
                ),
                candidates = listOf(
                    ErrorCauseCandidateItem(
                        observationId = "obs-2",
                        cause = ErrorCause.MISIDENTIFICATION,
                        detail = ErrorCauseDetail.READ_SIGN,
                        confidence = 0.8,
                        channel = ErrorCauseChannel.MODEL,
                        rationaleMarkdown = "把减号看成了加号",
                        observedAtEpochMillis = 2_000,
                        evidenceCount = 2,
                    ),
                ),
            ),
            onUndo = { undone = it },
        )

        composeRule.onNodeWithTag("mistake_detail_error_cause_active").assertIsDisplayed()
        composeRule.onNodeWithText("你确认", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag("mistake_detail_error_cause_undo").performClick()
        assertEquals("binding-1", undone)
    }

    @Test
    fun busyPanelDisablesConfirmAndUndo() {
        showPanel(
            ErrorCausePanelState(
                busy = true,
                active = ErrorCauseActiveTag(
                    bindingId = "binding-1",
                    cause = ErrorCause.CONCEPTUAL,
                    detail = null,
                    acceptanceSource = ErrorCauseAcceptanceSource.USER_CONFIRMED,
                    confidence = 0.9,
                ),
                candidates = listOf(
                    ErrorCauseCandidateItem(
                        observationId = "obs-3",
                        cause = ErrorCause.CARELESS,
                        detail = null,
                        confidence = 0.7,
                        channel = ErrorCauseChannel.REDO,
                        rationaleMarkdown = "立即重做对了",
                        observedAtEpochMillis = 3_000,
                        evidenceCount = 1,
                    ),
                ),
            ),
        )

        composeRule.onNodeWithTag("mistake_detail_error_cause_undo").assertIsNotEnabled()
        composeRule.onNodeWithTag("mistake_detail_error_cause_confirm_obs-3").assertIsNotEnabled()
    }

    @Test
    fun unreadableLedgerIsNotReportedAsNoCandidates() {
        showPanel(ErrorCausePanelState(unavailable = true))

        composeRule.onNodeWithTag("mistake_detail_error_cause_unavailable").assertIsDisplayed()
        composeRule.onNodeWithText(ERROR_CAUSE_UNAVAILABLE_TEXT).assertIsDisplayed()
        composeRule.onNodeWithTag("mistake_detail_error_cause_empty").assertDoesNotExist()
    }

    @Test
    fun panelOnlyRendersForTheCurrentRevision() {
        var slotCalls = 0
        val state = readyState()
        composeRule.setContent {
            SmartMistakeBookTheme {
                MistakeDetailContent(
                    state = state,
                    onBack = {},
                    onExport = {},
                    selectedRevisionId = "revision-old",
                    errorCausePanel = {
                        slotCalls += 1
                        MistakeErrorCausePanel(
                            state = ErrorCausePanelState(),
                            onConfirm = {},
                            onUndo = {},
                        )
                    },
                )
            }
        }

        composeRule.onNodeWithTag("mistake_detail_error_cause").assertDoesNotExist()
        assertEquals("历史版本不得触发候选面板装配（确认会挂到看不见的版本）", 0, slotCalls)
    }

    @Test
    fun panelRendersForTheCurrentRevision() {
        val state = readyState()
        composeRule.setContent {
            SmartMistakeBookTheme {
                MistakeDetailContent(
                    state = state,
                    onBack = {},
                    onExport = {},
                    selectedRevisionId = null,
                    errorCausePanel = {
                        MistakeErrorCausePanel(
                            state = ErrorCausePanelState(),
                            onConfirm = {},
                            onUndo = {},
                        )
                    },
                )
            }
        }

        composeRule.onNodeWithTag("mistake_detail_error_cause").assertIsDisplayed()
    }

    private fun showPanel(
        state: ErrorCausePanelState,
        onConfirm: (String) -> Unit = {},
        onUndo: (String) -> Unit = {},
    ) {
        composeRule.setContent {
            SmartMistakeBookTheme {
                MistakeErrorCausePanel(
                    state = state,
                    onConfirm = onConfirm,
                    onUndo = onUndo,
                )
            }
        }
    }

    private fun readyState() = MistakeDetailState.Ready(
        detail = MistakeDetail(
            identity = MistakeDetailIdentity(
                errorBookEntryId = "entry-1",
                problemId = "problem-1",
                problemRevisionId = "revision-1",
                revisionNumber = 1,
                title = "函数最值",
                subject = "MATH",
            ),
            fallbackMarkdown = "求函数最值。",
            source = MistakeSourceSet.Missing,
        ),
        questionDocument = CapturedQuestionDocument(
            document = QuestionDocument(
                id = "question",
                title = "函数最值",
                blocks = listOf(ContentBlock.Paragraph(id = "stem", markdown = "求函数最值。")),
            ),
            blockEvidence = emptyList(),
        ),
    )
}
