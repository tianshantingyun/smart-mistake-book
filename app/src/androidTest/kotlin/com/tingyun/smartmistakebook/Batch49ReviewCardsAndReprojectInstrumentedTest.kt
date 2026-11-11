package com.tingyun.smartmistakebook

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.tingyun.smartmistakebook.core.domain.AdaptiveClarificationCandidate
import com.tingyun.smartmistakebook.core.domain.AdaptiveClarificationRequest
import com.tingyun.smartmistakebook.core.domain.AdaptiveDecisionKind
import com.tingyun.smartmistakebook.core.domain.AdaptiveTargetKind
import com.tingyun.smartmistakebook.core.domain.KnowledgeQuizFeedbackResult
import com.tingyun.smartmistakebook.core.domain.KnowledgeReviewQueueEntry
import com.tingyun.smartmistakebook.core.domain.KnowledgeReviewSessionPlan
import com.tingyun.smartmistakebook.core.domain.StudyProjectionIssue
import com.tingyun.smartmistakebook.core.model.ProjectionStatus
import com.tingyun.smartmistakebook.core.model.ReviewReason
import com.tingyun.smartmistakebook.core.model.TutorAssessmentItem
import com.tingyun.smartmistakebook.core.model.TutorChoice
import com.tingyun.smartmistakebook.core.ui.SmartMistakeBookTheme
import com.tingyun.smartmistakebook.feature.review.KnowledgeClarificationState
import com.tingyun.smartmistakebook.feature.review.KnowledgeCalibrationDecision
import com.tingyun.smartmistakebook.feature.review.KnowledgeQuizLoadResult
import com.tingyun.smartmistakebook.feature.review.KnowledgeReviewSessionScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * 批次 49 设备面：三张学生可见的卡片必须在**真实 Compose 会话**里真的出现、真的可操作。
 *
 * 覆盖（逐条对应计划）：
 * - KD-67 确认卡（P2-11）：原因行、候选、确认只在已作答时可用、换题/取消出口；
 * - 冲突 KC 卡（P2-14）：两个动作、默认仍可复习、选择后卡必须收起；
 * - 投影冲突卡 + 「重新整理」（P2-13）：动作走注入的 reproject，失败必须说"没有改变"。
 *
 * 消灭的具体失败：这些组件原先只有 JVM 纯函数/状态机测试（`KnowledgeClarificationPresenterTest`、
 * `KnowledgeConflictSessionStateTest`、`ProjectionConflictReprojectTest`），
 * 屏幕是否真的把状态接上屏、按钮是否真的可点，设备上一次都没跑过——
 * 本文件就是那条"状态 → 屏幕"的断链检测。
 */
class Batch49ReviewCardsAndReprojectInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val node = KnowledgeReviewQueueEntry(
        knowledgeNodeId = "kc-monotonicity",
        subject = "MATH",
        displayName = "函数单调性",
        masteryScore = null,
        lastEvidenceAtEpochMillis = null,
    )

    private val quizItem = TutorAssessmentItem(
        id = "knowledge-quiz:kc-monotonicity:1",
        stemMarkdown = "函数单调性的判定？",
        choices = listOf(
            TutorChoice(id = "A", markdown = "看导数符号"),
            TutorChoice(id = "B", markdown = "看函数值大小"),
        ),
        correctChoiceId = "A",
        knowledgeNodeIds = setOf("kc-monotonicity"),
    )

    private fun clarificationRequest(reasonCodes: Set<String>) = AdaptiveClarificationRequest(
        learnerId = "learner:local",
        targetKnowledgeNodeIds = setOf(node.knowledgeNodeId),
        requestedTargetKind = AdaptiveTargetKind.FOUNDATION,
        candidates = listOf(
            AdaptiveClarificationCandidate(
                assessmentItemId = quizItem.id,
                itemFamilyId = "family:kc-monotonicity",
                knowledgeNodeIds = setOf(node.knowledgeNodeId),
            ),
        ),
        reasonCodes = reasonCodes,
        selectorVersion = "test-selector",
        decisionAtEpochMillis = 1_000L,
    )

    private fun setSessionContent(
        entry: KnowledgeReviewQueueEntry = node,
        clarificationState: KnowledgeClarificationState = KnowledgeClarificationState.Hidden,
        onConfirm: (String, String, String) -> Unit = { _, _, _ -> },
        onNewQuestion: (String) -> Unit = {},
        onCancel: () -> Unit = {},
        onRequestCalibrationQuiz: suspend (KnowledgeReviewQueueEntry) -> KnowledgeCalibrationDecision? =
            { null },
    ) {
        composeRule.setContent {
            SmartMistakeBookTheme {
                KnowledgeReviewSessionScreen(
                    plan = KnowledgeReviewSessionPlan(queue = listOf(entry)),
                    onBack = {},
                    restoreQuiz = { _, _ -> KnowledgeQuizLoadResult.Unavailable },
                    loadQuiz = { _, _ -> KnowledgeQuizLoadResult.Ready(quizItem) },
                    submitAnswer = { _, _, correctChoiceId, selectedChoiceId, _, _ ->
                        KnowledgeQuizFeedbackResult(
                            isCorrect = selectedChoiceId == correctChoiceId,
                            evidenceRecorded = true,
                        )
                    },
                    onFinished = {},
                    clarificationState = clarificationState,
                    onClarificationConfirm = onConfirm,
                    onClarificationNewQuestion = onNewQuestion,
                    onClarificationCancel = onCancel,
                    onRequestCalibrationQuiz = onRequestCalibrationQuiz,
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("函数单调性的判定？").assertIsDisplayed()
    }

    // ---------------------------------------------------------------- KD-67

    @Test
    fun clarificationCardConfirmsTheDisplayedQuestionOnlyAfterAnswering() {
        var confirmed: Triple<String, String, String>? = null
        setSessionContent(
            clarificationState = KnowledgeClarificationState.Asking(
                request = clarificationRequest(setOf("TARGET_CONTENT_NOT_VERIFIED")),
            ),
            onConfirm = { candidateId, correctChoiceId, selectedChoiceId ->
                confirmed = Triple(candidateId, correctChoiceId, selectedChoiceId)
            },
        )

        composeRule.onNodeWithTag("knowledge_clarification_card").assertIsDisplayed()
        composeRule.onNodeWithTag("knowledge_clarification_candidate_${quizItem.id}")
            .assertIsDisplayed()
        composeRule.onNodeWithText("这道题的答案还没有经过独立核对，这次只记录你的选择，不计入掌握度。")
            .assertIsDisplayed()
        composeRule.onNodeWithTag("knowledge_clarification_selector_version")
            .assertTextEquals("选择器版本：test-selector")

        // 还没作答：确认没有可确认的事实，按钮必须不可点。
        composeRule.onNodeWithTag("knowledge_clarification_confirm")
            .assertIsNotEnabled()
            .assertTextEquals("先做完这道题，再确认答案")
        composeRule.onNodeWithContentDescription("作答后才能确认这道题的答案").assertExists()

        // 作答后：确认可用，且只落在屏幕上这道题（候选 id = 题面 id）。
        composeRule.onNodeWithTag("knowledge_review_choice_A").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("knowledge_review_submit")
            .performScrollTo()
            .assertIsEnabled()
            .performClick()
        composeRule.waitForIdle()
        // 卡片较长时判决会被推到首屏下方；exists 已证明判决真的渲染出来了。
        composeRule.onNodeWithText("回答正确").assertExists()
        composeRule.onNodeWithTag("knowledge_clarification_confirm")
            .performScrollTo()
            .assertIsEnabled()
            .performClick()
        composeRule.waitForIdle()

        assertEquals(Triple(quizItem.id, "A", "A"), confirmed)

        // P2-15 设备证据补件：真实渲染的确认卡 PNG + uiautomator 语义树。
        Batch49DeviceArtifacts.capturePng(composeRule, "batch49-clarification-card.png")
        Batch49DeviceArtifacts.dumpA11y("batch49-clarification-card")
    }

    @Test
    fun clarificationCardWithoutTheKeyReasonOffersNoConfirmButton() {
        setSessionContent(
            clarificationState = KnowledgeClarificationState.Asking(
                request = clarificationRequest(setOf("LEARNING_EVIDENCE_NOT_CURRENT")),
            ),
        )

        composeRule.onNodeWithTag("knowledge_clarification_card").assertIsDisplayed()
        composeRule.onNodeWithTag("knowledge_clarification_confirm").assertDoesNotExist()
        composeRule.onNodeWithTag("knowledge_clarification_no_confirm").assertIsDisplayed()
        composeRule.onNodeWithTag("knowledge_clarification_new_question").assertIsEnabled()
        composeRule.onNodeWithTag("knowledge_clarification_cancel").assertIsEnabled()
    }

    // ------------------------------------------------------------ 冲突 KC 卡

    private fun conflictedEntry() = node.copy(reasons = setOf(ReviewReason.CONFLICTED_KNOWLEDGE))

    @Test
    fun conflictedKnowledgeNodeShowsTheOptionalCardAndStillLoadsTheQuiz() {
        setSessionContent(entry = conflictedEntry())

        composeRule.onNodeWithTag("knowledge_conflict_card").assertIsDisplayed()
        composeRule.onNodeWithText("这个知识点最近有波动").assertIsDisplayed()
        composeRule.onNodeWithTag("knowledge_conflict_continue").assertIsEnabled()
        composeRule.onNodeWithTag("knowledge_conflict_start_calibration").assertIsEnabled()
        // 卡不阻断：题面照常出现，学生可以什么都不点直接作答。
        composeRule.onNodeWithText("函数单调性的判定？").assertIsDisplayed()
        composeRule.onNodeWithTag("knowledge_review_choice_A").assertIsEnabled()

        // P2-15 设备证据补件：真实渲染的冲突卡 PNG + uiautomator 语义树。
        Batch49DeviceArtifacts.capturePng(composeRule, "batch49-conflict-card.png")
        Batch49DeviceArtifacts.dumpA11y("batch49-conflict-card")

        composeRule.onNodeWithTag("knowledge_review_choice_A").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("knowledge_review_submit").assertIsEnabled()
    }

    @Test
    fun continueReviewDismissesTheConflictCard() {
        setSessionContent(entry = conflictedEntry())

        composeRule.onNodeWithTag("knowledge_conflict_continue").performScrollTo().performClick()
        composeRule.waitForIdle()

        // 「仍然复习」= 学生已处理这张卡；卡必须真的收起（会话 ViewModel 契约
        // `showConflictCard == false`，设备面此前从未验证过这条接线）。
        composeRule.onNodeWithTag("knowledge_conflict_card").assertDoesNotExist()
        composeRule.onNodeWithText("函数单调性的判定？").assertIsDisplayed()
    }

    @Test
    fun startCalibrationDismissesTheCardAndMarksThisRoundAsCalibration() {
        var calibratedEntry: KnowledgeReviewQueueEntry? = null
        setSessionContent(
            entry = conflictedEntry(),
            onRequestCalibrationQuiz = { entry ->
                calibratedEntry = entry
                // 修复环：屏幕必须消费选择器决策；这里注入 ASK_CALIBRATION，
                // 断言状态行真的显示"已选中校准题面"，而不是只显示通用文案。
                KnowledgeCalibrationDecision(
                    decisionKind = AdaptiveDecisionKind.ASK_CALIBRATION,
                    reasonCodes = setOf("CALIBRATION_REQUESTED", "CALIBRATION_ONLY"),
                    selectedAssessmentItemId = quizItem.id,
                )
            },
        )

        composeRule.onNodeWithTag("knowledge_conflict_start_calibration").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("knowledge_conflict_card").assertDoesNotExist()
        composeRule.onNodeWithTag("knowledge_review_calibration_mode").assertIsDisplayed()
        composeRule.onNodeWithText(
            "本次按校准题处理：选择器已选中校准题面（${quizItem.id}）。作答仍计入这个知识点的掌握度。",
        ).assertIsDisplayed()
        assertEquals("kc-monotonicity", calibratedEntry?.knowledgeNodeId)
    }

    @Test
    fun startCalibrationWithoutASelectedQuestionSaysSoInsteadOfClaimingOne() {
        setSessionContent(
            entry = conflictedEntry(),
            onRequestCalibrationQuiz = {
                KnowledgeCalibrationDecision(
                    decisionKind = AdaptiveDecisionKind.NO_SAFE_CANDIDATE,
                    reasonCodes = setOf("NO_SAFE_CALIBRATION_CANDIDATE"),
                    selectedAssessmentItemId = null,
                )
            },
        )

        composeRule.onNodeWithTag("knowledge_conflict_start_calibration").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("knowledge_review_calibration_mode").assertIsDisplayed()
        composeRule.onNodeWithText(
            "本次按校准题处理；选择器未给出校准题（NO_SAFE_CALIBRATION_CANDIDATE），本次仍按正常复习记录。",
        ).assertIsDisplayed()
    }

    // -------------------------------------------------- 投影冲突 + 重新整理

    @Test
    fun projectionConflictBannerReprojectsThroughTheInjectedAction() {
        var reprojects = 0
        composeRule.setContent {
            SmartMistakeBookTheme {
                StudyProjectionIssueBanner(
                    issue = StudyProjectionIssue(ProjectionStatus.CONFLICTED),
                    onReproject = { reprojects += 1 },
                )
            }
        }

        composeRule.onNodeWithTag("study_projection_issue").assertIsDisplayed()
        composeRule.onNodeWithText("学习记录里有两处互相矛盾的内容，投影暂时停在冲突处，学习数据无法继续更新。")
            .assertIsDisplayed()
        composeRule.onNodeWithTag("study_projection_issue_reproject")
            .assertIsEnabled()
            .performClick()
        composeRule.waitForIdle()
        assertEquals(1, reprojects)
        composeRule.onNodeWithContentDescription("用本机学习记录重新整理；不会删除任何作答").assertExists()

        // P2-15 设备证据补件：投影冲突卡 PNG + 语义树。
        Batch49DeviceArtifacts.capturePng(composeRule, "batch49-projection-conflict.png")
        Batch49DeviceArtifacts.dumpA11y("batch49-projection-conflict")
    }

    @Test
    fun projectionConflictBannerSaysNothingChangedWhenReprojectFails() {
        composeRule.setContent {
            SmartMistakeBookTheme {
                StudyProjectionIssueBanner(
                    issue = StudyProjectionIssue(ProjectionStatus.CONFLICTED),
                    onReproject = { throw IllegalStateException("ledger still conflicted") },
                )
            }
        }

        composeRule.onNodeWithTag("study_projection_issue_reproject").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("study_projection_issue_error")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("study_projection_issue_error")
            .assertIsDisplayed()
            .assertTextEquals("重新整理没有完成，学习记录没有改变。可以换一会再试。")
    }

    @Test
    fun projectionGapBannerExplainsTheMissingSegmentDistinctly() {
        composeRule.setContent {
            SmartMistakeBookTheme {
                StudyProjectionIssueBanner(
                    issue = StudyProjectionIssue(ProjectionStatus.WAITING_FOR_GAP),
                    onReproject = {},
                )
            }
        }

        composeRule.onNodeWithText("学习记录缺少一段连续内容，投影停在缺口前，学习数据无法继续更新。")
            .assertIsDisplayed()
        // 缺口与冲突是两种账本事实，不得共用同一句原因。
        composeRule.onNodeWithText("学习记录里有两处互相矛盾的内容，投影暂时停在冲突处，学习数据无法继续更新。")
            .assertDoesNotExist()
    }
}
