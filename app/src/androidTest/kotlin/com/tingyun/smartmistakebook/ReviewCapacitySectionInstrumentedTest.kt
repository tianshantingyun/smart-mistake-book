package com.tingyun.smartmistakebook

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.tingyun.smartmistakebook.core.domain.StudyProfileOverview
import com.tingyun.smartmistakebook.core.domain.StudyReviewCapacity
import com.tingyun.smartmistakebook.core.domain.StudyReviewOverview
import com.tingyun.smartmistakebook.core.domain.StudySubjectCapacity
import com.tingyun.smartmistakebook.core.ui.SmartMistakeBookTheme
import com.tingyun.smartmistakebook.feature.review.ReviewRoute
import org.junit.Rule
import org.junit.Test

/**
 * 5b 容量 UI：复习页必须把快照上的容量读数（今日容量/考试缺口/逐科 granted-unmet/
 * 积压追赶状态）如实渲染，且没有容量事实时显示**如实空态**——不显示"今日容量 0 题"。
 */
class ReviewCapacitySectionInstrumentedTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun plannedCapacityRendersTodayCapacityExamGapAndPerSubjectReadout() {
        composeRule.setContent {
            SmartMistakeBookTheme {
                ReviewRoute(
                    overview = StudyReviewOverview(
                        scheduledCount = 2,
                        capacity = StudyReviewCapacity(
                            examSubjectsInWindow = 1,
                            examDeficitCount = 5,
                            examUnmetCount = 2,
                            rebalancePlanned = false,
                            subjects = listOf(
                                StudySubjectCapacity(
                                    subjectId = "MATH",
                                    daysUntilExam = 7,
                                    demand = 5,
                                    granted = 3,
                                    unmet = 2,
                                    feasibleBeforeExam = false,
                                ),
                            ),
                            totalGranted = 3,
                            dailyCapacity = 12,
                            maxDailyCapacity = 18,
                        ),
                    ),
                    profile = StudyProfileOverview(hasLearningEvidence = true),
                    onStartReview = {},
                )
            }
        }

        composeRule.onNodeWithTag("review_capacity_section").assertExists()
        composeRule.onNodeWithTag("review_capacity_today")
            .assertExists()
        composeRule.onNodeWithText("今日容量 12 题（最多 18 题）").assertExists()
        composeRule.onNodeWithText("考试缺口 5 题，今天排不下 2 题").assertExists()
        composeRule.onNodeWithText("数学：名额 3 / 应排 5 题，未满足 2 题，考前做不完").assertExists()
        composeRule.onNodeWithText("积压追赶：本轮未规划").assertExists()
    }

    @Test
    fun noCapacityFactsShowAnHonestEmptyStateInsteadOfZeroCapacity() {
        composeRule.setContent {
            SmartMistakeBookTheme {
                ReviewRoute(
                    overview = StudyReviewOverview(scheduledCount = 1),
                    profile = StudyProfileOverview(hasLearningEvidence = true),
                    onStartReview = {},
                )
            }
        }

        composeRule.onNodeWithTag("review_capacity_empty").assertExists()
        composeRule.onNodeWithText("今天没有临近的考试，暂无容量读数；到期的复习照常安排。")
            .assertExists()
        composeRule.onNodeWithText("今日容量", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("0 题", substring = true).assertDoesNotExist()
    }

    @Test
    fun anEmptyLibraryDoesNotShowACapacityReadout() {
        composeRule.setContent {
            SmartMistakeBookTheme {
                ReviewRoute(
                    overview = StudyReviewOverview(),
                    profile = StudyProfileOverview(),
                    onStartReview = {},
                    savedQuestionCount = 0,
                )
            }
        }

        composeRule.onNodeWithTag("review_capacity_section").assertDoesNotExist()
    }
}
