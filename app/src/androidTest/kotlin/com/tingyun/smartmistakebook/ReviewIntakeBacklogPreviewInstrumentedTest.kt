package com.tingyun.smartmistakebook

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.tingyun.smartmistakebook.core.domain.StudyProfileOverview
import com.tingyun.smartmistakebook.core.domain.StudyReviewOverview
import com.tingyun.smartmistakebook.core.ui.SmartMistakeBookTheme
import com.tingyun.smartmistakebook.feature.review.ReviewRoute
import org.junit.Rule
import org.junit.Test

/**
 * Instrumented verification of the intake-backlog coverage preview (spec
 * `batch-intake-spec.md` §6 P3) and the KD-68 honest split:
 *
 * - schedulable backlog renders "每天约 X 题，约 M 天安排完";
 * - items whose true duration exceeds the whole daily budget are listed apart as
 *   "需专项会话，不会进入每日计划" and are NOT counted in the daily promise;
 * - an all-oversized backlog gets no daily-introduction promise at all;
 * - an empty backlog stays silent.
 *
 * KD-68 reopen condition: this test covers "全超预算项显示为不可调度/专项会话" and
 * the review surface no longer promises a daily slot for them.
 */
class ReviewIntakeBacklogPreviewInstrumentedTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun nonEmptyBacklogShowsTheCoveragePreview() {
        composeRule.setContent {
            SmartMistakeBookTheme {
                ReviewRoute(
                    overview = StudyReviewOverview(
                        scheduledCount = 0,
                        intakeBacklogCount = 45,
                        intakeMedianEstimateSeconds = 100,
                    ),
                    profile = StudyProfileOverview(hasLearningEvidence = true),
                    onStartReview = {},
                )
            }
        }

        composeRule.onNodeWithTag("review_intake_backlog_preview").assertExists()
        // preview(45, 100s, 1200s budget): slice = 1200/3 = 400s, 400/100 = 4/day,
        // 45/4 = 11.25 → 12 days. No unschedulable items → no special-session line.
        composeRule.onNodeWithText("45 题待学").assertExists()
        composeRule.onNodeWithText("每天约 4 题，约 12 天安排完").assertExists()
        composeRule.onNodeWithText("需专项会话", substring = true).assertDoesNotExist()
    }

    @Test
    fun unschedulableItemsAreListedApartAndExcludedFromTheDailyPromise() {
        composeRule.setContent {
            SmartMistakeBookTheme {
                ReviewRoute(
                    overview = StudyReviewOverview(
                        scheduledCount = 0,
                        intakeBacklogCount = 45,
                        intakeMedianEstimateSeconds = 100,
                        intakeUnschedulableCount = 2,
                    ),
                    profile = StudyProfileOverview(hasLearningEvidence = true),
                    onStartReview = {},
                )
            }
        }

        composeRule.onNodeWithTag("review_intake_backlog_preview").assertExists()
        composeRule.onNodeWithText("45 题待学").assertExists()
        // Only the 43 schedulable items enter the promise: 43/4 → 11 days.
        composeRule.onNodeWithText("可每日引入的 43 题：每天约 4 题，约 11 天安排完").assertExists()
        composeRule.onNodeWithTag("review_intake_unschedulable")
            .assertTextContains("另有 2 道题单次超过整日预算，需专项会话，不会进入每日计划")
    }

    /** KD-68 的关闭用例：全部积压项都超过整日预算时，界面不得再给任何每日承诺。 */
    @Test
    fun anAllOversizedBacklogPromisesNoDailyIntroduction() {
        composeRule.setContent {
            SmartMistakeBookTheme {
                ReviewRoute(
                    overview = StudyReviewOverview(
                        scheduledCount = 0,
                        intakeBacklogCount = 3,
                        intakeMedianEstimateSeconds = 100,
                        intakeUnschedulableCount = 3,
                    ),
                    profile = StudyProfileOverview(hasLearningEvidence = true),
                    onStartReview = {},
                )
            }
        }

        composeRule.onNodeWithTag("review_intake_backlog_preview").assertExists()
        composeRule.onNodeWithText("3 题待学").assertExists()
        composeRule.onNodeWithTag("review_intake_unschedulable")
            .assertTextContains("另有 3 道题单次超过整日预算，需专项会话，不会进入每日计划")
        composeRule.onNodeWithText("每天约", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("约 3 天", substring = true).assertDoesNotExist()
    }

    @Test
    fun emptyBacklogHidesThePreview() {
        composeRule.setContent {
            SmartMistakeBookTheme {
                ReviewRoute(
                    overview = StudyReviewOverview(
                        scheduledCount = 3,
                        intakeBacklogCount = 0,
                        intakeMedianEstimateSeconds = 0,
                    ),
                    profile = StudyProfileOverview(hasLearningEvidence = true),
                    onStartReview = {},
                )
            }
        }

        composeRule.onNodeWithTag("review_intake_backlog_preview").assertDoesNotExist()
    }
}
