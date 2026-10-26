package com.tingyun.smartmistakebook

import android.os.Bundle
import android.os.Parcel
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.SAVED_STATE_REGISTRY_OWNER_KEY
import androidx.lifecycle.VIEW_MODEL_STORE_OWNER_KEY
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.enableSavedStateHandles
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.compose.LocalSavedStateRegistryOwner
import com.tingyun.smartmistakebook.core.domain.KnowledgeQuizFeedbackResult
import com.tingyun.smartmistakebook.feature.review.KnowledgeQuizLoadResult
import com.tingyun.smartmistakebook.core.domain.KnowledgeReviewQueueEntry
import com.tingyun.smartmistakebook.core.domain.KnowledgeReviewSessionPlan
import com.tingyun.smartmistakebook.core.model.TutorAssessmentItem
import com.tingyun.smartmistakebook.core.model.TutorChoice
import com.tingyun.smartmistakebook.core.ui.SmartMistakeBookTheme
import com.tingyun.smartmistakebook.feature.review.KnowledgeReviewSessionScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * 知识点复习会话屏（spec dual-review-entry §3.3/§3.4）：进屏自动取题 → 显示选择题 →
 * 作答 → 判答回写 → 下一知识点/完成。取题（含恢复读回）与回写都是注入回调，本测试验证
 * 整条 UI 会话状态机在真实 Compose 环境里走通（含 ViewModel 的 LaunchedEffect 驱动）。
 *
 * 全新会话（SavedStateHandle 里没有加载状态）走**出题**分支：[restoreQuiz] 一次都不该被调；
 * 恢复分支（LOADED 但内存无题）在 ViewModel 单测里覆盖（那里能造出回收后的 SavedStateHandle）。
 */
class KnowledgeReviewSessionInstrumentedTest {
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

    @Test
    fun singleNodeSessionLoadsQuizzesAnswersAndFinishes() {
        var finished = false
        var submitted: Pair<String, String>? = null
        var restoreCalls = 0
        composeRule.setContent {
            SmartMistakeBookTheme {
                KnowledgeReviewSessionScreen(
                    plan = KnowledgeReviewSessionPlan(queue = listOf(node)),
                    onBack = {},
                    restoreQuiz = { _, _ ->
                        restoreCalls += 1
                        KnowledgeQuizLoadResult.Unavailable
                    },
                    loadQuiz = { _, _ -> KnowledgeQuizLoadResult.Ready(quizItem) },
                    submitAnswer = { _, knowledgeNodeId, correctChoiceId, selectedChoiceId, _, _ ->
                        submitted = knowledgeNodeId to selectedChoiceId
                        KnowledgeQuizFeedbackResult(
                            isCorrect = selectedChoiceId == correctChoiceId,
                            evidenceRecorded = true,
                        )
                    },
                    onFinished = { finished = true },
                )
            }
        }

        // 自动取题：选择题题干出现。
        composeRule.waitForIdle()
        composeRule.onNodeWithText("函数单调性的判定？").assertIsDisplayed()
        assertEquals("全新会话不该走恢复读回", 0, restoreCalls)

        // 选正确答案并提交。
        composeRule.onNodeWithTag("knowledge_review_choice_A").performClick()
        composeRule.onNodeWithTag("knowledge_review_submit").assertIsEnabled()
        composeRule.onNodeWithTag("knowledge_review_submit").performClick()
        composeRule.waitForIdle()

        assertEquals("kc-monotonicity" to "A", submitted)
        composeRule.onNodeWithText("回答正确").assertIsDisplayed()

        // 单节点会话已完成：点完成回到首页。
        composeRule.onNodeWithTag("knowledge_review_finish").assertIsDisplayed()
        composeRule.onNodeWithTag("knowledge_review_finish").performClick()
        assertTrue(finished)
    }

    @Test
    fun loadFailureShowsRetryThatCanRecover() {
        var attempts = 0
        composeRule.setContent {
            SmartMistakeBookTheme {
                KnowledgeReviewSessionScreen(
                    plan = KnowledgeReviewSessionPlan(queue = listOf(node)),
                    onBack = {},
                    restoreQuiz = { _, _ -> KnowledgeQuizLoadResult.Unavailable },
                    loadQuiz = { _, _ ->
                        attempts += 1
                        if (attempts == 1) {
                            KnowledgeQuizLoadResult.Unavailable
                        } else {
                            KnowledgeQuizLoadResult.Ready(quizItem)
                        }
                    },
                    submitAnswer = { _, _, _, _, _, _ -> KnowledgeQuizFeedbackResult(true, true) },
                    onFinished = {},
                )
            }
        }

        composeRule.waitForIdle()
        // 首次取题失败 → 重试按钮；重试后题目出现。
        composeRule.onNodeWithTag("knowledge_review_load_failed").assertIsDisplayed()
        composeRule.onNodeWithTag("knowledge_review_retry").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("函数单调性的判定？").assertIsDisplayed()
        assertTrue(attempts >= 2)
    }

    @Test
    fun skippingTheOnlyNodeShowsAFinishActionWithoutRecordingAVerdict() {
        var finished = false
        var submissions = 0
        composeRule.setContent {
            SmartMistakeBookTheme {
                KnowledgeReviewSessionScreen(
                    plan = KnowledgeReviewSessionPlan(listOf(node)),
                    onBack = {},
                    restoreQuiz = { _, _ -> KnowledgeQuizLoadResult.Unavailable },
                    loadQuiz = { _, _ -> KnowledgeQuizLoadResult.NoMaterial },
                    submitAnswer = { _, _, _, _, _, _ ->
                        submissions += 1
                        KnowledgeQuizFeedbackResult(true, true)
                    },
                    onFinished = { finished = true },
                )
            }
        }

        composeRule.onNodeWithTag("knowledge_review_skip").performClick()
        composeRule.onNodeWithText("本次知识点复习已结束").assertIsDisplayed()
        composeRule.onNodeWithText("跳过的知识点没有记录本次结果，之后仍可复习。")
            .assertIsDisplayed()
        composeRule.onNodeWithTag("knowledge_review_finish").assertIsDisplayed().performClick()
        assertTrue(finished)
        assertEquals(0, submissions)
    }

    @Test
    fun savedStateParcelRoundTripRestoresTheOriginalNodeAndChoiceWithAnEmptyNewPlan() {
        val nextNode = node.copy(knowledgeNodeId = "kc-limit", displayName = "函数极限")
        val nextQuiz = quizItem.copy(
            id = "knowledge-quiz:kc-limit:1",
            stemMarkdown = "函数极限如何判断？",
            knowledgeNodeIds = setOf(nextNode.knowledgeNodeId),
        )
        lateinit var initialOwner: ReviewSessionOwner
        composeRule.runOnIdle { initialOwner = ReviewSessionOwner() }
        var owner by mutableStateOf(initialOwner)
        var currentPlan by mutableStateOf(KnowledgeReviewSessionPlan(listOf(node, nextNode)))
        var generations = 0
        val restoredNodes = mutableListOf<String>()
        var submission: Pair<String, String>? = null
        composeRule.setContent {
            key(owner) {
                CompositionLocalProvider(
                    LocalLifecycleOwner provides owner,
                    LocalViewModelStoreOwner provides owner,
                    LocalSavedStateRegistryOwner provides owner,
                ) {
                    SmartMistakeBookTheme {
                        KnowledgeReviewSessionScreen(
                            plan = currentPlan,
                            onBack = {},
                            restoreQuiz = { entry, _ ->
                                restoredNodes += entry.knowledgeNodeId
                                KnowledgeQuizLoadResult.Ready(nextQuiz)
                            },
                            loadQuiz = { entry, _ ->
                                generations += 1
                                if (entry.knowledgeNodeId == node.knowledgeNodeId) {
                                    KnowledgeQuizLoadResult.NoMaterial
                                } else {
                                    KnowledgeQuizLoadResult.Ready(nextQuiz)
                                }
                            },
                            submitAnswer = { _, nodeId, _, choiceId, _, _ ->
                                submission = nodeId to choiceId
                                KnowledgeQuizFeedbackResult(false, true)
                            },
                            onFinished = {},
                        )
                    }
                }
            }
        }
        composeRule.onNodeWithTag("knowledge_review_skip").performClick()
        composeRule.onNodeWithText("函数极限如何判断？").assertIsDisplayed()
        composeRule.onNodeWithTag("knowledge_review_choice_B").performClick()

        composeRule.runOnIdle {
            val previousOwner = owner
            val savedState = previousOwner.parcelledSavedState()
            currentPlan = KnowledgeReviewSessionPlan()
            owner = ReviewSessionOwner(savedState)
            previousOwner.destroy()
        }

        composeRule.onNodeWithText("函数极限").assertIsDisplayed()
        composeRule.onNodeWithText("函数极限如何判断？").assertIsDisplayed()
        composeRule.onNodeWithTag("knowledge_review_submit").assertIsEnabled().performClick()
        composeRule.runOnIdle {
            assertEquals(listOf(nextNode.knowledgeNodeId), restoredNodes)
            assertEquals(2, generations)
            assertEquals(nextNode.knowledgeNodeId to "B", submission)
        }
    }
}

/** 独立 ViewModelStore + 经 Parcel 的 registry Bundle，覆盖真正的保存/重建边界。 */
private class ReviewSessionOwner(restoredState: Bundle? = null) :
    SavedStateRegistryOwner,
    ViewModelStoreOwner,
    HasDefaultViewModelProviderFactory {
    private val registry = LifecycleRegistry(this)
    private val controller = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle = registry
    override val savedStateRegistry: SavedStateRegistry = controller.savedStateRegistry
    override val viewModelStore = ViewModelStore()
    override val defaultViewModelProviderFactory = ViewModelProvider.NewInstanceFactory()
    override val defaultViewModelCreationExtras: CreationExtras
        get() = MutableCreationExtras().apply {
            this[SAVED_STATE_REGISTRY_OWNER_KEY] = this@ReviewSessionOwner
            this[VIEW_MODEL_STORE_OWNER_KEY] = this@ReviewSessionOwner
        }

    init {
        controller.performAttach()
        enableSavedStateHandles()
        controller.performRestore(restoredState)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun parcelledSavedState(): Bundle {
        val state = Bundle()
        controller.performSave(state)
        val output = Parcel.obtain()
        val input = Parcel.obtain()
        return try {
            output.writeBundle(state)
            val bytes = output.marshall()
            input.unmarshall(bytes, 0, bytes.size)
            input.setDataPosition(0)
            requireNotNull(input.readBundle(javaClass.classLoader))
        } finally {
            output.recycle()
            input.recycle()
        }
    }

    fun destroy() {
        registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        viewModelStore.clear()
    }
}
