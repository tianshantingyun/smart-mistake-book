package com.tingyun.smartmistakebook.feature.review

import androidx.lifecycle.SavedStateHandle
import com.tingyun.smartmistakebook.core.domain.AdaptiveDecisionKind
import com.tingyun.smartmistakebook.core.domain.KnowledgeReviewQueueEntry
import com.tingyun.smartmistakebook.core.domain.KnowledgeReviewSessionPlan
import com.tingyun.smartmistakebook.core.model.ReviewReason
import com.tingyun.smartmistakebook.core.model.TutorAssessmentItem
import com.tingyun.smartmistakebook.core.model.TutorChoice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 冲突 KC 卡的会话状态机（批次 49 P2-14）。
 *
 * 钉的是**默认语义**：卡只是解释 + 一个可选动作，不阻断、不排除、不改变作答流程；
 * "先做校准题"是学生对本次节点的显式选择（持久化在 SavedStateHandle，进程重建后仍生效），
 * 且不推进队列；换到下一个节点时状态不串台。
 */
class KnowledgeConflictSessionStateTest {

    private val plan = KnowledgeReviewSessionPlan(
        queue = listOf(
            entry("kc-confl", conflicted = true),
            entry("kc-fresh", conflicted = false),
        ),
    )

    @Test
    fun aConflictedNodeShowsTheCardWithoutRequiringAnAction() {
        val viewModel = KnowledgeReviewSessionViewModel(SavedStateHandle(), plan)

        assertTrue(viewModel.showConflictCard)
        assertFalse("默认不是校准题：冲突 KC 默认仍按正常复习", viewModel.calibrationRequested)
        assertEquals("kc-confl", viewModel.currentEntry?.knowledgeNodeId)
        assertEquals(0, viewModel.currentIndex)
        assertTrue(
            "冲突点必须仍在队列里（不硬排除）",
            viewModel.currentEntry?.isConflicted == true,
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun loadCurrentQuizIsNotGatedByTheConflictCard() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val viewModel = KnowledgeReviewSessionViewModel(SavedStateHandle(), plan)
            val loaded = mutableListOf<String>()

            viewModel.loadCurrentQuiz { entry, _ ->
                loaded += entry.knowledgeNodeId
                KnowledgeQuizLoadResult.Ready(quizItem(entry.knowledgeNodeId))
            }
            advanceUntilIdle()

            assertEquals(
                "冲突卡不得阻断取题：学生什么都不点，正常出题流程也必须走完",
                listOf("kc-confl"),
                loaded,
            )
            assertEquals(KnowledgeQuizLoadStatus.LOADED, viewModel.loadStatus)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun startCalibrationMarksTheNodeWithoutAdvancingOrBlocking() {
        val viewModel = KnowledgeReviewSessionViewModel(SavedStateHandle(), plan)

        viewModel.startCalibration()

        assertTrue(viewModel.calibrationRequested)
        assertFalse("学生已选择校准，卡不再重复出现", viewModel.showConflictCard)
        assertEquals("选校准不是跳过：当前节点不变", 0, viewModel.currentIndex)
        assertEquals(2, viewModel.queueSize)
        assertEquals(KnowledgeQuizLoadStatus.IDLE, viewModel.loadStatus)
    }

    @Test
    fun continuingReviewDismissesTheCardWithoutCalibrationIntent() {
        val viewModel = KnowledgeReviewSessionViewModel(SavedStateHandle(), plan)

        viewModel.dismissConflictCard()

        assertFalse(viewModel.showConflictCard)
        assertFalse(
            "仍然复习就是正常复习：不得偷偷把本次标成校准题",
            viewModel.calibrationRequested,
        )
        assertEquals(0, viewModel.currentIndex)
    }

    @Test
    fun theNextNodeStartsWithItsOwnConflictState() {
        val viewModel = KnowledgeReviewSessionViewModel(SavedStateHandle(), plan)

        viewModel.startCalibration()
        viewModel.skipCurrentNode()

        assertEquals("kc-fresh", viewModel.currentEntry?.knowledgeNodeId)
        assertFalse("上一个节点的校准选择不得串到下一个节点", viewModel.calibrationRequested)
        assertFalse("无冲突证据的节点不该出现冲突卡", viewModel.showConflictCard)
    }

    @Test
    fun processRestoreKeepsTheChoiceAndTheNodeReasons() {
        val handle = SavedStateHandle()
        val original = KnowledgeReviewSessionViewModel(handle, plan)
        original.startCalibration()

        val restored = KnowledgeReviewSessionViewModel(processRestoredHandle(handle), plan)

        assertEquals("kc-confl", restored.currentEntry?.knowledgeNodeId)
        assertTrue("进程重建后仍是同一场会话的校准选择", restored.calibrationRequested)
        assertFalse(restored.showConflictCard)
        assertTrue(
            "队列项的理由必须随快照恢复——否则重建后冲突卡再也判断不出来",
            restored.currentEntry?.isConflicted == true,
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun requestCalibrationQuestionConsumesTheSelectorDecision() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val viewModel = KnowledgeReviewSessionViewModel(SavedStateHandle(), plan)
            viewModel.startCalibration()
            val entry = requireNotNull(viewModel.currentEntry)

            viewModel.requestCalibrationQuestion(entry) {
                KnowledgeCalibrationDecision(
                    decisionKind = AdaptiveDecisionKind.ASK_CALIBRATION,
                    reasonCodes = setOf("CALIBRATION_REQUESTED", "CALIBRATION_ONLY"),
                    selectedAssessmentItemId = "knowledge-quiz:item:kc-confl",
                )
            }
            advanceUntilIdle()

            assertEquals(
                KnowledgeCalibrationDecisionStatus.READY,
                viewModel.calibrationDecisionStatus,
            )
            assertTrue(requireNotNull(viewModel.calibrationDecision).isCalibrationQuestion)
            assertTrue(
                "选择器给了校准题面时，状态行必须说「已选中」而不是笼统的「按校准题处理」",
                knowledgeCalibrationStatusText(
                    viewModel.calibrationDecisionStatus,
                    viewModel.calibrationDecision,
                ).contains("已选中校准题面"),
            )
        } finally {
            Dispatchers.resetMain()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun requestCalibrationQuestionReportsWhenNoQuestionWasSelected() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val viewModel = KnowledgeReviewSessionViewModel(SavedStateHandle(), plan)
            viewModel.startCalibration()
            val entry = requireNotNull(viewModel.currentEntry)

            viewModel.requestCalibrationQuestion(entry) {
                KnowledgeCalibrationDecision(
                    decisionKind = AdaptiveDecisionKind.NO_SAFE_CANDIDATE,
                    reasonCodes = setOf("NO_SAFE_CALIBRATION_CANDIDATE"),
                    selectedAssessmentItemId = null,
                )
            }
            advanceUntilIdle()

            assertEquals(
                KnowledgeCalibrationDecisionStatus.READY,
                viewModel.calibrationDecisionStatus,
            )
            assertFalse(requireNotNull(viewModel.calibrationDecision).isCalibrationQuestion)
            val text = knowledgeCalibrationStatusText(
                viewModel.calibrationDecisionStatus,
                viewModel.calibrationDecision,
            )
            assertTrue("未给出校准题必须如实说明", text.contains("选择器未给出校准题"))
            assertFalse("不得继续承诺有一道校准题", text.contains("已选中校准题面"))
        } finally {
            Dispatchers.resetMain()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun requestCalibrationQuestionFailureDoesNotFakeAQuestion() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val viewModel = KnowledgeReviewSessionViewModel(SavedStateHandle(), plan)
            viewModel.startCalibration()
            val entry = requireNotNull(viewModel.currentEntry)

            viewModel.requestCalibrationQuestion(entry) { null }
            advanceUntilIdle()

            assertEquals(
                KnowledgeCalibrationDecisionStatus.FAILED,
                viewModel.calibrationDecisionStatus,
            )
            assertNull(viewModel.calibrationDecision)
            assertTrue(
                knowledgeCalibrationStatusText(
                    viewModel.calibrationDecisionStatus,
                    viewModel.calibrationDecision,
                ).contains("校准选择没有完成"),
            )
        } finally {
            Dispatchers.resetMain()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun processRestoreKeepsTheCalibrationDecision() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val handle = SavedStateHandle()
            val original = KnowledgeReviewSessionViewModel(handle, plan)
            original.startCalibration()
            val entry = requireNotNull(original.currentEntry)
            original.requestCalibrationQuestion(entry) {
                KnowledgeCalibrationDecision(
                    decisionKind = AdaptiveDecisionKind.ASK_CALIBRATION,
                    reasonCodes = setOf("CALIBRATION_ONLY"),
                    selectedAssessmentItemId = "knowledge-quiz:item:kc-confl",
                )
            }
            advanceUntilIdle()

            val restored = KnowledgeReviewSessionViewModel(processRestoredHandle(handle), plan)

            assertEquals(
                "进程重建后仍是同一条选择结果",
                KnowledgeCalibrationDecisionStatus.READY,
                restored.calibrationDecisionStatus,
            )
            assertEquals(
                "knowledge-quiz:item:kc-confl",
                restored.calibrationDecision?.selectedAssessmentItemId,
            )
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun processRestoredHandle(handle: SavedStateHandle): SavedStateHandle {
        val state = handle.keys().associateWith { key ->
            val value = handle.get<Any>(key)
            assertTrue(
                "会话快照不能依赖只在内存中可用的对象：$key",
                value == null || value is String || value is Int || value is Long ||
                    value is ArrayList<*> && value.all { it is String },
            )
            value
        }
        val restored = state.mapValues { (_, value) ->
            if (value is ArrayList<*>) ArrayList(value) else value
        }
        return SavedStateHandle(restored)
    }

    private fun entry(knowledgeNodeId: String, conflicted: Boolean) = KnowledgeReviewQueueEntry(
        knowledgeNodeId = knowledgeNodeId,
        subject = "MATH",
        displayName = if (conflicted) "冲突的知识点" else "无证据知识点",
        masteryScore = 0.3,
        lastEvidenceAtEpochMillis = 1_000L,
        reasons = if (conflicted) {
            setOf(ReviewReason.CONFLICTED_KNOWLEDGE, ReviewReason.CALIBRATION_CHECK)
        } else {
            setOf(ReviewReason.CALIBRATION_CHECK)
        },
    )

    private fun quizItem(nodeId: String) = TutorAssessmentItem(
        id = "knowledge-quiz:$nodeId:1",
        stemMarkdown = "考察 $nodeId 的选择题",
        choices = listOf(
            TutorChoice(id = "A", markdown = "正确项"),
            TutorChoice(id = "B", markdown = "干扰项"),
        ),
        correctChoiceId = "A",
        knowledgeNodeIds = setOf(nodeId),
    )
}
