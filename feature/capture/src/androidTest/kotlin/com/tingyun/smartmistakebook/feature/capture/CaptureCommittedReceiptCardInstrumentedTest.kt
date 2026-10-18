package com.tingyun.smartmistakebook.feature.capture

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

/**
 * 4C·B5：录入界面的「已存入错题本」卡片要如实区分「新建入库」与「已存在同一题（已复用）」，
 * 不能两笔提交长一样。
 */
class CaptureCommittedReceiptCardInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun newEntryCardSaysItIsANewEntry() {
        composeRule.setContent {
            MaterialTheme {
                CaptureCommittedCard(
                    reusedExistingProblem = false,
                    onView = {},
                    onCaptureAnother = {},
                )
            }
        }

        composeRule.onNodeWithTag("capture_committed").assertExists()
        composeRule.onNodeWithText("已经存入错题本").assertExists()
        composeRule.onNodeWithText(captureCommittedReceiptLine(reusedExistingProblem = false))
            .assertExists()
    }

    @Test
    fun reusedEntryCardSaysItReusedTheExistingProblem() {
        composeRule.setContent {
            MaterialTheme {
                CaptureCommittedCard(
                    reusedExistingProblem = true,
                    onView = {},
                    onCaptureAnother = {},
                )
            }
        }

        composeRule.onNodeWithTag("capture_committed_receipt").assertExists()
        composeRule.onNodeWithText(captureCommittedReceiptLine(reusedExistingProblem = true))
            .assertExists()
    }
}
