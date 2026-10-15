package com.tingyun.smartmistakebook.feature.review

import androidx.lifecycle.SavedStateHandle
import com.tingyun.smartmistakebook.core.domain.KnowledgeQuizFeedbackResult
import com.tingyun.smartmistakebook.core.domain.KnowledgeReviewQueueEntry
import com.tingyun.smartmistakebook.core.domain.KnowledgeReviewSessionPlan
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
 * 知识点复习会话单元（spec dual-review-entry §3.3/§3.4）：单知识点展示单元——进入后向模型
 * 要题（loading）→ 得选择题 → 学生选 → 判答（本地 evaluateChoice）→ 回写掌握度 →
 * 下一知识点/完成。ViewModel 只持状态 + 命令持久化，dispatch 经回调注入，纯 JVM 可测。
 */
class KnowledgeReviewSessionViewModelTest {

    private val plan = KnowledgeReviewSessionPlan(
        queue = listOf(
            entry("kc-confl", "冲突的知识点"),
            entry("kc-fresh", "无证据知识点"),
        ),
    )

    private fun entry(knowledgeNodeId: String, displayName: String) = KnowledgeReviewQueueEntry(
        knowledgeNodeId = knowledgeNodeId,
        subject = "MATH",
        displayName = displayName,
        masteryScore = 0.3,
        lastEvidenceAtEpochMillis = 1_000,
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

    @Test
    fun startsAtFirstNodeIdleWithoutItem() {
        val viewModel = KnowledgeReviewSessionViewModel(
            savedStateHandle = SavedStateHandle(),
            plan = plan,
        )
        assertEquals(KnowledgeQuizLoadStatus.IDLE, viewModel.loadStatus)
        assertEquals(2, viewModel.queueSize)
        assertEquals("kc-confl", viewModel.currentEntry?.knowledgeNodeId)
        assertNull(viewModel.currentItem)
        assertFalse(viewModel.completed)
    }

    @Test
    fun restoreFromProcessDeathKeepsNodeIndex() {
        val handle = SavedStateHandle().apply {
            set(KnowledgeReviewSessionViewModel.CURRENT_INDEX_KEY, 1)
        }
        val viewModel = KnowledgeReviewSessionViewModel(
            savedStateHandle = handle,
            plan = plan,
        )
        assertEquals("kc-fresh", viewModel.currentEntry?.knowledgeNodeId)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun loadingCurrentQuizRunsTheInjectDispatcherAndShowsItem() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = KnowledgeReviewSessionViewModel(
            savedStateHandle = SavedStateHandle(),
            plan = plan,
        )
        val loaded = mutableListOf<String>()

        try {
            viewModel.loadCurrentQuiz { entry, _ ->
                loaded += entry.knowledgeNodeId
                KnowledgeQuizLoadResult.Ready(quizItem(entry.knowledgeNodeId))
            }
            advanceUntilIdle()

            assertEquals(listOf("kc-confl"), loaded)
            assertEquals(KnowledgeQuizLoadStatus.LOADED, viewModel.loadStatus)
            assertEquals("kc-confl", viewModel.currentItem?.knowledgeNodeIds?.single())
        } finally {
            Dispatchers.resetMain()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun aFailedLoadIsRetryableWithoutLosingTheNode() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = KnowledgeReviewSessionViewModel(
            savedStateHandle = SavedStateHandle(),
            plan = plan,
        )

        try {
            viewModel.loadCurrentQuiz { _, _ -> error("模型暂时不可用") }  // 抛异常仍归 FAILED
            advanceUntilIdle()
            assertEquals(KnowledgeQuizLoadStatus.FAILED, viewModel.loadStatus)
            assertEquals("kc-confl", viewModel.currentEntry?.knowledgeNodeId)

            viewModel.loadCurrentQuiz { entry, _ ->
                KnowledgeQuizLoadResult.Ready(quizItem(entry.knowledgeNodeId))
            }
            advanceUntilIdle()
            assertEquals(KnowledgeQuizLoadStatus.LOADED, viewModel.loadStatus)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun skippingAFailedNodeAdvancesWithoutRecordingAnything() {
        val handle = SavedStateHandle().apply {
            set(
                KnowledgeReviewSessionViewModel.LOAD_STATUS_KEY,
                KnowledgeQuizLoadStatus.FAILED.name,
            )
        }
        val viewModel = KnowledgeReviewSessionViewModel(
            savedStateHandle = handle,
            plan = plan,
        )

        viewModel.skipCurrentNode()

        // 跳过是零权重：不写判决、不回写掌握度，只是本会话内前进。
        assertEquals("kc-fresh", viewModel.currentEntry?.knowledgeNodeId)
        assertNull(viewModel.submittedChoice)
        assertNull(viewModel.feedbackResult)
        assertEquals(KnowledgeQuizLoadStatus.IDLE, viewModel.loadStatus)
        assertEquals(KnowledgeQuizSubmitStatus.IDLE, viewModel.submitStatus)
        assertFalse(viewModel.completed)
    }

    @Test
    fun skippingTheLastNodeEndsTheSessionWithoutAVerdict() {
        val handle = SavedStateHandle().apply {
            set(KnowledgeReviewSessionViewModel.CURRENT_INDEX_KEY, 1)
        }
        val viewModel = KnowledgeReviewSessionViewModel(
            savedStateHandle = handle,
            plan = plan,
        )

        viewModel.skipCurrentNode()

        assertNull(viewModel.currentEntry)
        assertNull(viewModel.submittedChoice)
    }

    /**
     * AUD-02（改前语义的回归钉）：重建后 currentItem 只存内存、必然丢失，但题面已随会话落库——
     * LOADING/LOADED 不再回到 IDLE（那会变成"重新出题再出网"），而是保留 LOADED，
     * 由 Screen 走**读回**（[restoreCurrentQuiz]）。只有"从没出过题"（IDLE）才现场出题。
     */
    @Test
    fun restoredLoadedStatusKeepsTheQuizForReadBackInsteadOfRegenerating() {
        listOf(
            KnowledgeQuizLoadStatus.LOADING,
            KnowledgeQuizLoadStatus.LOADED,
        ).forEach { restored ->
            val handle = SavedStateHandle().apply {
                set(
                    KnowledgeReviewSessionViewModel.LOAD_STATUS_KEY,
                    restored.name,
                )
            }
            val viewModel = KnowledgeReviewSessionViewModel(
                savedStateHandle = handle,
                plan = plan,
            )

            assertEquals(KnowledgeQuizLoadStatus.LOADED, viewModel.loadStatus)
            assertNull(viewModel.currentItem)
        }

        // 全新会话（没有出过题）仍是 IDLE：那是"该出题"的状态，不是"该读回"。
        val fresh = KnowledgeReviewSessionViewModel(SavedStateHandle(), plan)
        assertEquals(KnowledgeQuizLoadStatus.IDLE, fresh.loadStatus)
    }

    /**
     * AUD-02 恢复用例（SavedStateHandle 覆盖系统回收）：题面与已选都从持久态读回。
     * 同时钉住取题分支规则——恢复态走**读回**（不是出题），读回后不再触发任何取题动作
     * （否则每次重组都会再动一次）。
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun aRestoredSessionReadsTheQuizBackWithoutGeneratingAgain() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val handle = SavedStateHandle().apply {
            set(KnowledgeReviewSessionViewModel.SESSION_STARTED_AT_KEY, 7_000L)
            set(
                KnowledgeReviewSessionViewModel.LOAD_STATUS_KEY,
                KnowledgeQuizLoadStatus.LOADED.name,
            )
            set(KnowledgeReviewSessionViewModel.SELECTED_CHOICE_KEY, "B")
        }
        val viewModel = KnowledgeReviewSessionViewModel(
            savedStateHandle = handle,
            plan = plan,
        )
        val restoredCalls = mutableListOf<Pair<String, Long>>()

        try {
            // 已选与状态先按持久态还原（"读回原题面/选项/已选"里的"已选"）。
            assertEquals("B", viewModel.selectedChoice)
            assertEquals(KnowledgeQuizLoadStatus.LOADED, viewModel.loadStatus)
            assertEquals(
                "恢复态只能读回：这条规则错了就会重新出题、再次出网",
                KnowledgeQuizLoadPath.RESTORE,
                knowledgeQuizLoadPath(
                    loadStatus = viewModel.loadStatus,
                    hasItem = viewModel.currentItem != null,
                    alreadySubmitted = false,
                ),
            )

            viewModel.restoreCurrentQuiz { entry, sessionId ->
                restoredCalls += entry.knowledgeNodeId to sessionId
                KnowledgeQuizLoadResult.Ready(quizItem(entry.knowledgeNodeId))
            }
            advanceUntilIdle()

            assertEquals(listOf("kc-confl" to 7_000L), restoredCalls)
            assertEquals(KnowledgeQuizLoadStatus.LOADED, viewModel.loadStatus)
            assertEquals("kc-confl", viewModel.currentItem?.knowledgeNodeIds?.single())
            assertEquals("B", viewModel.selectedChoice)
            assertEquals(
                "题面已在手，取题动作应当停止（不再读、也不再出）",
                KnowledgeQuizLoadPath.NONE,
                knowledgeQuizLoadPath(
                    loadStatus = viewModel.loadStatus,
                    hasItem = viewModel.currentItem != null,
                    alreadySubmitted = false,
                ),
            )
        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * AUD-02 损坏/缺失出口：恢复读不回（缺失或损坏）落 FAILED——走既有失败卡片
     * （重试出题 / 跳过这个知识点 / 返回），既不停在"已加载但无题"的空白，也不自动重出题。
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun aRestoreThatCannotReadTheQuizFallsBackToTheFailureExit() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            listOf(
                KnowledgeQuizLoadResult.Unavailable,
                KnowledgeQuizLoadResult.NoMaterial,
                KnowledgeQuizLoadResult.KnowledgePreparing,
            ).forEach { unreadable ->
                val handle = SavedStateHandle().apply {
                    set(
                        KnowledgeReviewSessionViewModel.LOAD_STATUS_KEY,
                        KnowledgeQuizLoadStatus.LOADED.name,
                    )
                }
                val viewModel = KnowledgeReviewSessionViewModel(
                    savedStateHandle = handle,
                    plan = plan,
                )

                viewModel.restoreCurrentQuiz { _, _ -> unreadable }
                advanceUntilIdle()

                assertEquals(KnowledgeQuizLoadStatus.FAILED, viewModel.loadStatus)
                assertNull(viewModel.currentItem)
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * AUD-01①：提交身份 = 会话 + 题面 + 选项。同一次会话里的重试 → 同一个身份（下游按身份
     * 幂等 = 一笔证据）；隔日另一场会话 → 另一个身份（同选项各自一笔）。
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun aFailedSubmitRetriesWithTheSameIdentityWhileANextSessionGetsItsOwn() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val sessionStartedAt = 5_000L
        val nextSessionStartedAt = sessionStartedAt + 24 * 60 * 60 * 1_000L

        try {
            val handle = SavedStateHandle().apply {
                set(KnowledgeReviewSessionViewModel.SESSION_STARTED_AT_KEY, sessionStartedAt)
            }
            val viewModel = KnowledgeReviewSessionViewModel(
                savedStateHandle = handle,
                plan = plan,
            )
            viewModel.loadCurrentQuiz { entry, _ ->
                KnowledgeQuizLoadResult.Ready(quizItem(entry.knowledgeNodeId))
            }
            advanceUntilIdle()
            viewModel.select("A")

            val attemptIdentities = mutableListOf<String>()
            viewModel.submitAnswer(
                occurredAtEpochMillis = 1_000,
                submit = { requestId, _, _, _, _, _ ->
                    attemptIdentities += requestId
                    error("写入超时")
                },
            )
            advanceUntilIdle()
            assertEquals(KnowledgeQuizSubmitStatus.FAILED, viewModel.submitStatus)

            // 同一次会话里的重试：身份必须一模一样（下游按身份幂等，重试等于同一笔）。
            viewModel.submitAnswer(
                occurredAtEpochMillis = 2_000,
                submit = { requestId, _, _, _, _, _ ->
                    attemptIdentities += requestId
                    KnowledgeQuizFeedbackResult(isCorrect = true, evidenceRecorded = true)
                },
            )
            advanceUntilIdle()

            assertEquals(KnowledgeQuizSubmitStatus.RECORDED, viewModel.submitStatus)
            assertEquals(2, attemptIdentities.size)
            assertEquals(
                "同一会话的重试必须是同一笔证据身份",
                attemptIdentities[0],
                attemptIdentities[1],
            )
            assertTrue(
                "身份里必须带会话分量",
                attemptIdentities[0].contains(sessionStartedAt.toString()),
            )

            // 隔日再来（另一场会话）：身份必须不同，否则第二笔会被幂等键吞掉。
            val nextHandle = SavedStateHandle().apply {
                set(KnowledgeReviewSessionViewModel.SESSION_STARTED_AT_KEY, nextSessionStartedAt)
            }
            val nextSessionViewModel = KnowledgeReviewSessionViewModel(
                savedStateHandle = nextHandle,
                plan = plan,
            )
            nextSessionViewModel.loadCurrentQuiz { entry, _ ->
                KnowledgeQuizLoadResult.Ready(quizItem(entry.knowledgeNodeId))
            }
            advanceUntilIdle()
            nextSessionViewModel.select("A")
            var nextSessionIdentity: String? = null
            nextSessionViewModel.submitAnswer(
                occurredAtEpochMillis = 90_000_000,
                submit = { requestId, _, _, _, _, _ ->
                    nextSessionIdentity = requestId
                    KnowledgeQuizFeedbackResult(isCorrect = true, evidenceRecorded = true)
                },
            )
            advanceUntilIdle()

            assertFalse(
                "隔日另一场会话必须是另一笔（否则第二笔会被幂等键吞掉）",
                attemptIdentities[0] == nextSessionIdentity,
            )
            assertTrue(
                "身份携带的是各自会话的分量",
                requireNotNull(nextSessionIdentity).contains(nextSessionStartedAt.toString()),
            )
        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * D-Q3（消费点④）：取不到题的两种原因必须是两个状态——"内容还在准备"（等一会再试）
     * 与"这个知识点没有材料"（重试没用，跳过它）。改前它们都是 FAILED。
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun theTwoCannotLoadReasonsBecomeDistinctStatuses() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = KnowledgeReviewSessionViewModel(
            savedStateHandle = SavedStateHandle(),
            plan = plan,
        )

        try {
            viewModel.loadCurrentQuiz { _, _ -> KnowledgeQuizLoadResult.KnowledgePreparing }
            advanceUntilIdle()
            assertEquals(KnowledgeQuizLoadStatus.KNOWLEDGE_PREPARING, viewModel.loadStatus)
            assertNull(viewModel.currentItem)

            viewModel.loadCurrentQuiz { _, _ -> KnowledgeQuizLoadResult.NoMaterial }
            advanceUntilIdle()
            assertEquals(KnowledgeQuizLoadStatus.NO_MATERIAL, viewModel.loadStatus)
        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * "还在准备"在进程重建后回到 IDLE：那时内容多半已经就位，Screen 会重走 loader——
     * 这就是"就绪后自动放行"，而不是停在准备态等学生自己发现。
     */
    @Test
    fun restoredKnowledgePreparingReopensTheLoadPath() {
        val handle = SavedStateHandle().apply {
            set(
                KnowledgeReviewSessionViewModel.LOAD_STATUS_KEY,
                KnowledgeQuizLoadStatus.KNOWLEDGE_PREPARING.name,
            )
        }
        val viewModel = KnowledgeReviewSessionViewModel(
            savedStateHandle = handle,
            plan = plan,
        )

        assertEquals(KnowledgeQuizLoadStatus.IDLE, viewModel.loadStatus)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun correctAnswerRecordsVerdictAndAllowsContinue() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = KnowledgeReviewSessionViewModel(
            savedStateHandle = SavedStateHandle(),
            plan = plan,
        )
        val submitted = mutableListOf<Triple<String, String, String>>()

        try {
            viewModel.loadCurrentQuiz { entry, _ ->
                KnowledgeQuizLoadResult.Ready(quizItem(entry.knowledgeNodeId))
            }
            advanceUntilIdle()
            viewModel.select("A")
            viewModel.submitAnswer(
                occurredAtEpochMillis = 1_000,
                submit = { _, nodeId, correctChoiceId, selectedChoiceId, _, _ ->
                    submitted += Triple(nodeId, correctChoiceId, selectedChoiceId)
                    KnowledgeQuizFeedbackResult(isCorrect = true, evidenceRecorded = true)
                },
            )
            advanceUntilIdle()

            assertEquals(listOf(Triple("kc-confl", "A", "A")), submitted)
            assertEquals(KnowledgeQuizSubmitStatus.RECORDED, viewModel.submitStatus)
            assertEquals("A", viewModel.submittedChoice)
            assertEquals(true, viewModel.feedbackResult?.isCorrect)
            assertTrue(viewModel.canContinue)
            assertFalse(viewModel.completed)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun wrongAnswerStillRecordsNegativeVerdictAndAllowsContinue() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = KnowledgeReviewSessionViewModel(
            savedStateHandle = SavedStateHandle(),
            plan = plan,
        )

        try {
            viewModel.loadCurrentQuiz { entry, _ ->
                KnowledgeQuizLoadResult.Ready(quizItem(entry.knowledgeNodeId))
            }
            advanceUntilIdle()
            viewModel.select("B")
            viewModel.submitAnswer(
                occurredAtEpochMillis = 1_000,
                submit = { _, _, _, selectedChoiceId, _, _ ->
                    KnowledgeQuizFeedbackResult(
                        isCorrect = selectedChoiceId == "A",
                        evidenceRecorded = true,
                    )
                },
            )
            advanceUntilIdle()

            assertEquals("B", viewModel.submittedChoice)
            assertEquals(false, viewModel.feedbackResult?.isCorrect)
            assertTrue(viewModel.canContinue)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun continueToNextAdvancesAndClearsVerdictBeforeLoadingNext() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = KnowledgeReviewSessionViewModel(
            savedStateHandle = SavedStateHandle(),
            plan = plan,
        )

        try {
            viewModel.loadCurrentQuiz { entry, _ ->
                KnowledgeQuizLoadResult.Ready(quizItem(entry.knowledgeNodeId))
            }
            advanceUntilIdle()
            viewModel.select("A")
            viewModel.submitAnswer(
                occurredAtEpochMillis = 1_000,
                submit = { _, _, _, _, _, _ -> KnowledgeQuizFeedbackResult(true, true) },
            )
            advanceUntilIdle()

            viewModel.continueToNext()
            assertEquals("kc-fresh", viewModel.currentEntry?.knowledgeNodeId)
            assertNull(viewModel.submittedChoice)
            assertNull(viewModel.feedbackResult)
            assertEquals(KnowledgeQuizLoadStatus.IDLE, viewModel.loadStatus)
            assertFalse(viewModel.canContinue)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun recordingTheLastAnswerCompletesTheSession() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val handle = SavedStateHandle().apply {
            set(KnowledgeReviewSessionViewModel.CURRENT_INDEX_KEY, 1)
        }
        val viewModel = KnowledgeReviewSessionViewModel(
            savedStateHandle = handle,
            plan = plan,
        )

        try {
            viewModel.loadCurrentQuiz { entry, _ ->
                KnowledgeQuizLoadResult.Ready(quizItem(entry.knowledgeNodeId))
            }
            advanceUntilIdle()
            viewModel.select("A")
            viewModel.submitAnswer(
                occurredAtEpochMillis = 1_000,
                submit = { _, _, _, _, _, _ -> KnowledgeQuizFeedbackResult(true, true) },
            )
            advanceUntilIdle()

            assertTrue(viewModel.canContinue)
            assertTrue(viewModel.completed)
            viewModel.continueToNext()
            assertNull(viewModel.currentEntry)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun aFailedWritebackIsRetryableAndDoesNotLockTheNode() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = KnowledgeReviewSessionViewModel(
            savedStateHandle = SavedStateHandle(),
            plan = plan,
        )

        try {
            viewModel.loadCurrentQuiz { entry, _ ->
                KnowledgeQuizLoadResult.Ready(quizItem(entry.knowledgeNodeId))
            }
            advanceUntilIdle()
            viewModel.select("A")
            viewModel.submitAnswer(
                occurredAtEpochMillis = 1_000,
                submit = { _, _, _, _, _, _ -> error("写入超时") },
            )
            advanceUntilIdle()

            assertEquals(KnowledgeQuizSubmitStatus.FAILED, viewModel.submitStatus)
            assertFalse(viewModel.canContinue)
            viewModel.submitAnswer(
                occurredAtEpochMillis = 1_000,
                submit = { _, _, _, _, _, _ -> KnowledgeQuizFeedbackResult(true, true) },
            )
            advanceUntilIdle()
            assertEquals(KnowledgeQuizSubmitStatus.RECORDED, viewModel.submitStatus)
        } finally {
            Dispatchers.resetMain()
        }
    }
}
