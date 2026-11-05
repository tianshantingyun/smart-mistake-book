package com.tingyun.smartmistakebook

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.domain.ExamCalendarEntry
import com.tingyun.smartmistakebook.core.domain.SchedulingOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 审计 2026-09-09：`setOptions` / `declareExam` / `removeExam` 此前全仓无调用方，
 * 排程参数与考试日历在 UI 上完全不可写。本测试锁定这条写入链路。
 */
@RunWith(AndroidJUnit4::class)
class SchedulingSettingsInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun schedulingSettingsExposeRetentionSliderWithoutTheRemovedFsrsSwitch() {
        composeRule.setContent {
            SchedulingSettingsScreen(
                options = SchedulingOptions(),
                exams = emptyList(),
                onSetOptions = {},
                onAddExam = {},
                onRemoveExam = {},
                onBack = {},
            )
        }

        composeRule.onNodeWithTag("scheduling_retention_slider").assertIsDisplayed()
        // KF-11（2026-10-01）：FSRS 开关随 kill-switch 退场（FSRS 唯一化）。
        composeRule.onNodeWithTag("scheduling_fsrs_switch").assertDoesNotExist()
    }

    @Test
    fun retentionSliderRightEdgeStoresAValueTheDomainAccepts() {
        val saved = mutableListOf<SchedulingOptions>()
        composeRule.setContent {
            SchedulingSettingsScreen(
                options = SchedulingOptions(),
                exams = emptyList(),
                onSetOptions = { saved.add(it) },
                onAddExam = {},
                onRemoveExam = {},
                onBack = {},
            )
        }

        // 0.97f 就是滑杆右端读数。修复前它经 Float→Double 变成 0.9700000286102295，
        // options.copy 在 SchedulingOptions.init 的 require(0.7..0.97) 处抛
        // IllegalArgumentException，主线程 FATAL（批次 44 soak，F-055/F-057）。
        composeRule.onNodeWithTag("scheduling_retention_slider")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(0.97f) }

        composeRule.runOnIdle {
            assertEquals(0.97, saved.last().desiredRetention, 0.0)
        }
    }

    @Test
    fun addingAndRemovingAnExamCallsTheStore() {
        val added = mutableListOf<ExamCalendarEntry>()
        val removed = mutableListOf<String>()
        val existing = ExamCalendarEntry(
            entryId = "exam-existing",
            subject = "数学",
            examEpochDay = 20_000,
            title = "期中考试",
        )
        composeRule.setContent {
            SchedulingSettingsScreen(
                options = SchedulingOptions(),
                exams = listOf(existing),
                onSetOptions = {},
                onAddExam = added::add,
                onRemoveExam = removed::add,
                onBack = {},
            )
        }

        composeRule.onNodeWithTag("exam_subject_field").performTextInput("物理")
        composeRule.onNodeWithTag("exam_title_field").performTextInput("月考")
        composeRule.onNodeWithTag("exam_days_field").performTextInput("14")
        composeRule.onNodeWithTag("exam_add_button").performClick()

        composeRule.runOnIdle {
            assertEquals(1, added.size)
            assertEquals("物理", added.single().subject)
            assertEquals("月考", added.single().title)
            assertTrue(added.single().examEpochDay > 0)
        }

        composeRule.onNodeWithTag("exam_remove_exam-existing").performClick()
        composeRule.runOnIdle { assertEquals(listOf("exam-existing"), removed) }
    }
}
