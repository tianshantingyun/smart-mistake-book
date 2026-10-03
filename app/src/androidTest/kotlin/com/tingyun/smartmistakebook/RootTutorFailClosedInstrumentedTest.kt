package com.tingyun.smartmistakebook

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.printToString
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tingyun.smartmistakebook.core.domain.AdaptiveDecision
import com.tingyun.smartmistakebook.core.domain.AdaptiveDecisionKind
import com.tingyun.smartmistakebook.core.domain.FsrsParameterOptimizer
import com.tingyun.smartmistakebook.core.domain.SaveTutorProblemCommand
import com.tingyun.smartmistakebook.core.domain.SaveTutorProblemReceipt
import com.tingyun.smartmistakebook.core.domain.SchedulingEvaluationReport
import com.tingyun.smartmistakebook.core.domain.StudyAnswerRevealRequest
import com.tingyun.smartmistakebook.core.domain.StudyAnswerRevealResult
import com.tingyun.smartmistakebook.core.domain.StudyCatalogEntry
import com.tingyun.smartmistakebook.core.domain.StudyChoiceSubmission
import com.tingyun.smartmistakebook.core.domain.StudyChoiceSubmissionResult
import com.tingyun.smartmistakebook.core.domain.StudyDataStatus
import com.tingyun.smartmistakebook.core.domain.StudyExperienceRepository
import com.tingyun.smartmistakebook.core.domain.StudyExperienceSnapshot
import com.tingyun.smartmistakebook.core.domain.StudyReviewChoiceSubmissionResult
import com.tingyun.smartmistakebook.core.domain.StudyReviewOverview
import com.tingyun.smartmistakebook.core.domain.StudyReviewSessionProgress
import com.tingyun.smartmistakebook.core.domain.StudyReviewSessionStatus
import com.tingyun.smartmistakebook.core.domain.TutorJudgedReviewSettlement
import com.tingyun.smartmistakebook.core.domain.TutorJudgedReviewSettlementResult
import com.tingyun.smartmistakebook.core.domain.TutorJudgedReviewSettlementStatus
import com.tingyun.smartmistakebook.core.model.LearningEvidenceReason
import com.tingyun.smartmistakebook.core.model.TeachingArtifactVerification
import com.tingyun.smartmistakebook.core.model.TutorAssessmentItem
import com.tingyun.smartmistakebook.core.model.TutorChoice
import com.tingyun.smartmistakebook.core.model.VerifiedTeachingArtifact
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement

/**
 * 讲题/复习两条装载路径的 fail-closed 不变量（阶段 2c 之后的落点）。
 *
 * 这三条不变量原先钉在**已删除的第三路径**（`tutorPracticeUnitId + teachingArtifact` →
 * `TutorScreen` 的判定面）上：它们等的 `tutor_choice_a` / `tutor_submit_answer` /
 * `tutor_reveal_answer` / `tutor_full_explanation` 全仓已无定义，重新贴标签既修不好也不该修
 * （那条路径到不了，见 `TutorRoute` 的 KDoc）。这里把三条**不变量本身**（不是它们的旧界面）
 * 重新落到今天真能被违反的地方：
 *
 * 1. 陈旧/未校验载荷不得注入一道题 —— 载荷仍由 fake 发（READY + `tutorPracticeUnitId` +
 *    `tutorDecision`），断言**根讲题页**是会话面、载荷里的那道题一个字都不出现。今天唯一的
 *    保证在 `StudySnapshotBuilder`（恒置 null）与已删的装载器，这一条是回归守卫：谁把判定面
 *    接回根讲题页，这里就红。
 * 2. 延迟产物不得配到更新的练习单元 —— 落到**复习会话**的 artifact 装载（
 *    `SmartMistakeBookDestinations` 的 `TeachingArtifactLoad`，今天的唯一消费者）：A 的题在
 *    页上、B 的产物在途时切到 B，页上只许说"正在读取题目…"，不许拿 A 的题/选项/产物顶上；
 *    释放 B 之后出现的是 B 的题，提交记录的 `practiceUnitId` 也必须是当时页上那一道。
 * 3. 揭示身份不得泄漏到下一个单元 —— 同一个复习会话里 A 揭示过、切到 B 之后，A 的"已揭示"
 *    不许跟过来（讲解面不在、按钮回到未揭示措辞），第二次揭示的**出站请求身份**必须各自正确：
 *    `practiceUnitId` 是各自那一道、`presentationId` 互不相同。
 */
@RunWith(AndroidJUnit4::class)
class RootTutorFailClosedInstrumentedTest {
    private val repository = ControllableStudyExperienceRepository()
    private val repositoryRule = StudyRepositoryOverrideRule(repository)
    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(repositoryRule)
        .around(composeRule)

    @Before
    fun requireTutorTeachingCapability() {
        val application = InstrumentationRegistry.getInstrumentation()
            .targetContext
            .applicationContext as SmartMistakeBookApplication
        kotlinx.coroutines.runBlocking {
            application.studyDatabase.clearAllData()
        }
        assumeTrue(
            "The strict-offline diagnostic flavor intentionally has no semantic tutor.",
            application.capabilities.tutorTeachingEnabled,
        )
    }

    @Test
    fun readyQuestionIsRemovedDuringLoadingAndErrorThenRecovers() {
        navigateToTutor()
        // 正面锚点：根讲题页是**会话面**（阶段 2 起的唯一交互面）。没有这个锚点，
        // "题面计数为 0"在页面根本没起来时也会空过。
        assertTutorRootIsAConversationSurface()

        // fake 发一个"有题、有判定"的 READY 载荷：这正是旧第三路径会据以渲染题面的输入。
        repository.publishReady(TUTOR_PRACTICE_UNIT_ID)
        assertTutorRootIsAConversationSurface()

        repository.publishStatusPreservingPayload(StudyDataStatus.LOADING)
        waitForText(LOADING_COPY)
        assertTutorRootIsAConversationSurface()

        repository.publishStatusPreservingPayload(StudyDataStatus.ERROR)
        waitForText(ERROR_COPY)
        assertTutorRootIsAConversationSurface()

        // 恢复 READY：拿到手的仍是一个**没有题**的讲题页——载荷不被当成本轮要讲的那道题。
        repository.publishReady(TUTOR_PRACTICE_UNIT_ID)
        assertTutorRootIsAConversationSurface()
    }

    @Test
    fun delayedArtifactCannotPairWithNewerPracticeUnitOrSubmission() {
        repository.publishReviewSession(
            practiceUnitIds = listOf(TUTOR_PRACTICE_UNIT_ID, SECOND_PRACTICE_UNIT_ID),
        )
        startReviewSession()
        waitForText(TUTOR_TITLE)
        waitForTag("review_choice_A")

        // A 的题已经在页上（自己的产物已到）；让 B 的产物迟到，再切到 B。
        repository.delayArtifact(SECOND_PRACTICE_UNIT_ID)
        submitChoiceOnTheDisplayedUnit()
        advanceToTheNextUnitInTheQueue()

        // B 的读取确实在途：此刻页上只许出现如实的"正在读取题目…"，
        // 不许把刚刚那一道（A）的产物、题干或选项当作 B 的题面顶上。
        waitUntil { SECOND_PRACTICE_UNIT_ID in repository.artifactRequests }
        waitForText(LOADING_ARTIFACT_COPY)
        composeRule.onAllNodesWithTag("review_session_root").assertCountEquals(0)
        composeRule.onAllNodesWithText(TUTOR_TITLE, substring = false).assertCountEquals(0)
        composeRule.onAllNodesWithTag("review_choice_A").assertCountEquals(0)
        composeRule.onAllNodesWithTag("review_choice_B").assertCountEquals(0)

        // 释放 B 的产物：出现的是 B 的题（标题与题号都是 B 的），A 的内容一个都不留。
        repository.releaseArtifact(SECOND_PRACTICE_UNIT_ID)
        waitForText(SECOND_TITLE)
        waitForText("第 2 / 2 题")
        waitForTag("review_session_root")
        waitForTag("review_choice_B")
        composeRule.onAllNodesWithText(TUTOR_TITLE, substring = false).assertCountEquals(0)

        // 页上的装载器只读**它正在显示**的那一道：读到的 unit 必须全部来自这条会话队列，
        // 切到 B 之后不许回头读 A。同一道题被再读一遍是允许的——实测同一生产者会在页面
        // 首次组合后 ~110ms 再跑一次（重新组合重建了装载效果），读的仍是当时页上那一道；
        // 把它钉成"每个 unit 恰好读一次"等于钉住组合次数，不是这条不变量。
        val loadedUnits = repository.artifactRequests
        assertTrue(
            "读到队列之外的单元才是配对失败（实际读到：$loadedUnits）",
            loadedUnits.all {
                it == TUTOR_PRACTICE_UNIT_ID || it == SECOND_PRACTICE_UNIT_ID
            },
        )
        assertEquals(
            "切到 B 之后最后一次读的必须是 B（实际读到：$loadedUnits）",
            SECOND_PRACTICE_UNIT_ID,
            loadedUnits.last(),
        )
        assertEquals(
            "切到 B 之后不许再回头读 A（实际读到：$loadedUnits）",
            listOf(SECOND_PRACTICE_UNIT_ID),
            loadedUnits.dropWhile { it != SECOND_PRACTICE_UNIT_ID }.distinct(),
        )
        assertEquals(
            listOf(TUTOR_PRACTICE_UNIT_ID),
            repository.submissions.map(StudyChoiceSubmission::practiceUnitId),
        )
        submitChoiceOnTheDisplayedUnit()
        assertEquals(
            listOf(TUTOR_PRACTICE_UNIT_ID, SECOND_PRACTICE_UNIT_ID),
            repository.submissions.map(StudyChoiceSubmission::practiceUnitId),
        )
        assertEquals(
            "两次作答是两个呈现，不许共用一个 presentationId",
            2,
            repository.submissions.map(StudyChoiceSubmission::presentationId).distinct().size,
        )
    }

    @Test
    fun revealedExplanationAndRequestIdentityDoNotLeakIntoNextPracticeUnit() {
        repository.publishReviewSession(
            practiceUnitIds = listOf(TUTOR_PRACTICE_UNIT_ID, SECOND_PRACTICE_UNIT_ID),
        )
        startReviewSession()
        waitForText(TUTOR_TITLE)
        waitForTag("review_reveal_answer")

        composeRule.onNodeWithTag("review_reveal_answer").performScrollTo().performClick()
        waitUntil { repository.revealRequests.size == 1 }
        waitForTag("review_explanation")
        assertEquals(
            TUTOR_PRACTICE_UNIT_ID,
            repository.revealRequests.single().practiceUnitId,
        )

        submitChoiceOnTheDisplayedUnit()
        advanceToTheNextUnitInTheQueue()
        waitForText(SECOND_TITLE)

        // 上一单元的"已揭示"不许跟过来：讲解面不在，按钮回到未揭示的措辞。
        composeRule.onAllNodesWithTag("review_explanation").assertCountEquals(0)
        composeRule.onAllNodesWithText(REVEAL_OPEN_COPY).assertCountEquals(0)
        composeRule.onNodeWithText(REVEAL_HIDDEN_COPY).assertExists()

        composeRule.onNodeWithTag("review_reveal_answer").performScrollTo().performClick()
        waitUntil { repository.revealRequests.size == 2 }
        waitForTag("review_explanation")

        val first = repository.revealRequests[0]
        val second = repository.revealRequests[1]
        assertEquals(TUTOR_PRACTICE_UNIT_ID, first.practiceUnitId)
        assertEquals(SECOND_PRACTICE_UNIT_ID, second.practiceUnitId)
        assertEquals(
            "两个单元的揭示是两个呈现，presentationId 必须互不相同",
            2,
            repository.revealRequests.map(StudyAnswerRevealRequest::presentationId).distinct().size,
        )
    }

    @Test
    fun emptyStudySnapshotShowsTheTrueNewUserLibraryAndReviewStates() {
        repository.publishEmptyReady()

        waitForTag("nav_library")
        composeRule.onNodeWithTag("nav_library").performClick()
        waitForTag("library_empty_state")
        waitForText("还没有错题")
        composeRule.onAllNodesWithTag("library_capture_button").assertCountEquals(1)
        composeRule.onAllNodesWithTag("library_search_field").assertCountEquals(0)
        composeRule.onAllNodesWithText(TUTOR_TITLE, substring = false).assertCountEquals(0)

        // L6：空态入口与列表入口同指唯一的录入入口，方式选择（含整卷/PDF）可达。
        // 这条空态入口是"四类入口同指"里的第二类（第一类是列表非空态的主按钮）。
        composeRule.onNodeWithTag("library_capture_button").performScrollTo().performClick()
        waitForTag("capture_screen")
        waitForTag("capture_mode_chooser")
        waitForTag("capture_mode_files")
        composeRule.onNodeWithTag("capture_back_button").performClick()
        waitForTag("root_library")
        composeRule.onAllNodesWithTag("library_capture_button").assertCountEquals(1)

        composeRule.onNodeWithTag("nav_profile").performClick()
        waitForTag("root_profile")
        composeRule.onAllNodesWithText("当前学习情况").assertCountEquals(0)
        composeRule.onAllNodesWithText("当前薄弱点").assertCountEquals(0)
        composeRule.onAllNodesWithText("学习次数").assertCountEquals(0)
        composeRule.onNodeWithTag("profile_learning_mastery").assertExists()

        composeRule.onNodeWithTag("nav_review").performClick()
        waitForText("暂无学习记录")
        waitForText("暂无待复习题")
    }

    @Test
    fun savedCapturedQuestionOffersOnlyTheTutorJudgedReview() {
        repository.publishCapturedReviewReady()

        waitForText("1")
        waitForText("道计划复习")
        composeRule.onNodeWithTag("review_start_button").performClick()
        waitForTag("captured_review_session_root")
        waitForText(CAPTURED_QUESTION_MARKDOWN)
        composeRule.onAllNodesWithText(TUTOR_TITLE, substring = false).assertCountEquals(0)

        // 自评/评级通道已拆：不再有任何"由学生决定对错"的按钮，唯一作答面是讲题判定。
        composeRule.onAllNodesWithTag("review_self_report_recalled").assertCountEquals(0)
        composeRule.onAllNodesWithTag("review_self_report_effort").assertCountEquals(0)
        composeRule.onAllNodesWithTag("review_self_report_stuck").assertCountEquals(0)
        composeRule.onAllNodesWithTag("review_rating_easy").assertCountEquals(0)

        composeRule.onNodeWithTag("captured_review_tutor_judged_button").performClick()
        waitForTag("saved_mistake_tutor_screen")

        // 结算被尝试过，但这次讲题还没有形成判定 → 队列不推进（不伪造记录）。
        assertTrue(repository.settleCalls.isNotEmpty())
        assertEquals(CAPTURED_PRACTICE_UNIT_ID, repository.settleCalls.first().practiceUnitId)
    }

    private fun navigateToTutor() {
        waitForTag("nav_tutor")
        composeRule.onNodeWithTag("nav_tutor").performClick()
        waitForTag("root_tutor")
    }

    private fun startReviewSession() {
        waitForTag("review_start_button")
        // 快照发布是异步可见的：先等按钮真的换成"有会话可继续"的那句，再点，
        // 否则可能点在还没启用（scheduledCount = 0）的按钮上，什么都不发生。
        waitForText(REVIEW_START_COPY)
        composeRule.onNodeWithTag("review_start_button").performScrollTo().performClick()
        waitForTag("review_session_root")
    }

    /**
     * 在页上那一道题里选一项并提交：提交结果由 fake 按**提交里带的** practiceUnitId 记账，
     * 所以这一次作答落在哪一道上，就是这个断言要钉的"配对"。
     */
    private fun submitChoiceOnTheDisplayedUnit() {
        val submittedBefore = repository.submissions.size
        composeRule.onNodeWithTag("review_choice_A").performScrollTo().performClick()
        composeRule.onNodeWithTag("review_submit_answer").performScrollTo().performClick()
        waitUntil { repository.submissions.size == submittedBefore + 1 }
    }

    /** 真实学生的推进路径：提交结果里的 `nextPracticeUnitId` 决定下一道，点的是那次推进的按钮。 */
    private fun advanceToTheNextUnitInTheQueue() {
        waitForTag("review_next_item")
        composeRule.onNodeWithTag("review_next_item").performScrollTo().performClick()
    }

    /**
     * 根讲题页是**会话面**，不是题面：载荷里的那道题（标题、判定面）一个字都不出现。
     *
     * 后四个标签是**旧第三路径**的判定面（全仓已无定义）——0 是回归守卫而不是现役保护：
     * 它们能红只在"有人把判定面接回这一页"时发生，这正是要拦的那种改动。
     */
    private fun assertTutorRootIsAConversationSurface() {
        composeRule.onNodeWithTag("tutor_conversation_screen").assertExists()
        composeRule.onNodeWithTag("tutor_empty_state").assertExists()
        composeRule.onAllNodesWithText(TUTOR_TITLE, substring = false).assertCountEquals(0)
        composeRule.onAllNodesWithTag("tutor_choice_a").assertCountEquals(0)
        composeRule.onAllNodesWithTag("tutor_submit_answer").assertCountEquals(0)
        composeRule.onAllNodesWithTag("tutor_reveal_answer").assertCountEquals(0)
        composeRule.onAllNodesWithTag("tutor_full_explanation").assertCountEquals(0)
        assertTrue(
            "讲题页不得为一个它不信任的载荷去读产物",
            repository.artifactRequests.isEmpty(),
        )
    }

    private fun waitForTag(tag: String) {
        waitUntil {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForText(text: String) {
        try {
            waitUntil {
                composeRule.onAllNodesWithText(text, substring = true)
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
        } catch (failure: Throwable) {
            throw AssertionError(
                "Timed out waiting for text '$text'. Current semantics:\n" +
                    composeRule.onRoot(useUnmergedTree = true).printToString(),
                failure,
            )
        }
    }

    private fun waitUntil(condition: () -> Boolean) {
        composeRule.waitUntil(timeoutMillis = 20_000, condition = condition)
    }

    companion object {
        // Local literal mirroring the curated tutor practice unit of the debug
        // fixture; androidTest must not reference the debug-only seed directly
        // (audit section 9.2 / PR-05).
        const val TUTOR_PRACTICE_UNIT_ID = "practice:m1:derivative-sign-change:whole"
        const val SECOND_PRACTICE_UNIT_ID = "practice:m1:closed-interval-extrema:whole"
        const val CAPTURED_PRACTICE_UNIT_ID = "practice:captured:exact-original"
        const val CAPTURED_QUESTION_MARKDOWN = "已保存原题：若 x + 3 = 7，求 x。"
        const val TUTOR_TITLE = "由导数符号判断单调区间"
        const val SECOND_TITLE = "闭区间上的函数最值"

        /** 如实状态文案（`StudyDataStatusLine` 逐字）。 */
        const val LOADING_COPY = "正在读取本机学习记录"
        const val ERROR_COPY = "本机学习数据暂时无法更新"

        /** 复习会话装载 artifact 期间的那句如实等待（`ReviewSessionDestination`）。 */
        const val LOADING_ARTIFACT_COPY = "正在读取题目"

        /** 复习首页的开始按钮在"已有会话可继续"时的措辞（`ReviewRoute`）。 */
        const val REVIEW_START_COPY = "继续今日复习"

        /** 揭示按钮的两种措辞（`ReviewRevealStatus`）。 */
        const val REVEAL_HIDDEN_COPY = "不会做，查看完整讲解"
        const val REVEAL_OPEN_COPY = "完整讲解已打开"
    }
}

private class StudyRepositoryOverrideRule(
    private val replacement: StudyExperienceRepository,
) : TestRule {
    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            val application = InstrumentationRegistry.getInstrumentation()
                .targetContext
                .applicationContext as SmartMistakeBookApplication
            val repositoryField = SmartMistakeBookApplication::class.java
                .getDeclaredField("studyRepository")
                .apply { isAccessible = true }
            val original = application.studyRepository
            repositoryField.set(application, replacement)
            try {
                base.evaluate()
            } finally {
                repositoryField.set(application, original)
                replacement.close()
            }
        }
    }
}

private class ControllableStudyExperienceRepository : StudyExperienceRepository {
    private val mutableSnapshot = MutableStateFlow(StudyExperienceSnapshot())
    private val artifactGates = ConcurrentHashMap<String, CompletableDeferred<Unit>>()

    override val snapshot: StateFlow<StudyExperienceSnapshot> = mutableSnapshot
    val artifactRequests = CopyOnWriteArrayList<String>()
    val completedArtifactRequests = CopyOnWriteArrayList<String>()
    val submissions = CopyOnWriteArrayList<StudyChoiceSubmission>()
    val settleCalls = CopyOnWriteArrayList<TutorJudgedReviewSettlement>()
    val revealRequests = CopyOnWriteArrayList<StudyAnswerRevealRequest>()

    fun publishReady(practiceUnitId: String) {
        val artifact = requireArtifact(practiceUnitId)
        mutableSnapshot.value = StudyExperienceSnapshot(
            status = StudyDataStatus.READY,
            tutorPracticeUnitId = practiceUnitId,
            tutorDecision = artifact.askDecision(),
        )
    }

    fun publishEmptyReady() {
        mutableSnapshot.value = StudyExperienceSnapshot(status = StudyDataStatus.READY)
    }

    /**
     * 一条**真的在跑的复习会话**：队列、当前题号、会话 id 都在快照里，
     * 复习首页可以据此开始，会话页据此显示第 [ordinal] 道。
     */
    fun publishReviewSession(
        practiceUnitIds: List<String>,
        ordinal: Int = 0,
        stateVersion: Long = 1L,
    ) {
        require(practiceUnitIds.isNotEmpty()) { "A review session needs at least one unit" }
        mutableSnapshot.value = StudyExperienceSnapshot(
            status = StudyDataStatus.READY,
            review = StudyReviewOverview(
                planId = REVIEW_PLAN_ID,
                scheduledCount = practiceUnitIds.size,
                scheduledPracticeUnitIds = practiceUnitIds,
                activeSessionId = CAPTURED_REVIEW_SESSION_ID,
                currentOrdinal = ordinal,
                sessionStateVersion = stateVersion,
            ),
        )
    }

    fun publishCapturedReviewReady() {
        mutableSnapshot.value = StudyExperienceSnapshot(
            status = StudyDataStatus.READY,
            catalog = listOf(
                StudyCatalogEntry(
                    entryId = "entry:captured:exact-original",
                    problemId = "problem:captured:exact-original",
                    problemRevisionId = "revision:captured:exact-original",
                    practiceUnitId = RootTutorFailClosedInstrumentedTest.CAPTURED_PRACTICE_UNIT_ID,
                    subject = "MATH",
                    title = "一次方程原题",
                    problemMarkdown = RootTutorFailClosedInstrumentedTest.CAPTURED_QUESTION_MARKDOWN,
                    sourceKey = "capture:android-test",
                    isCuratedExample = false,
                    nextReviewAtEpochMillis = null,
                    retrievability = null,
                ),
            ),
            review = StudyReviewOverview(
                planId = CAPTURED_REVIEW_PLAN_ID,
                scheduledCount = 1,
                estimatedSeconds = 120,
                scheduledPracticeUnitIds = listOf(
                    RootTutorFailClosedInstrumentedTest.CAPTURED_PRACTICE_UNIT_ID,
                ),
            ),
        )
    }

    fun publishStatusPreservingPayload(status: StudyDataStatus) {
        mutableSnapshot.value = mutableSnapshot.value.copy(status = status)
    }

    fun delayArtifact(practiceUnitId: String) {
        artifactGates[practiceUnitId] = CompletableDeferred()
    }

    fun releaseArtifact(practiceUnitId: String) {
        artifactGates[practiceUnitId]?.complete(Unit)
    }

    override suspend fun initialize() = Unit

    override suspend fun saveTutorProblem(
        command: SaveTutorProblemCommand,
    ): SaveTutorProblemReceipt =
        // Fail-closed fake: it holds no problem objects, so saving any
        // referenced problem must report the reference as missing.
        SaveTutorProblemReceipt.ReferenceNotFound(
            reason = "This fail-closed test repository holds no problem object for ${command.conversationId}",
        )

    override suspend fun teachingArtifact(practiceUnitId: String): VerifiedTeachingArtifact? {
        artifactRequests += practiceUnitId
        if (practiceUnitId == RootTutorFailClosedInstrumentedTest.CAPTURED_PRACTICE_UNIT_ID) {
            completedArtifactRequests += practiceUnitId
            return null
        }
        val artifact = artifactGates[practiceUnitId]?.let { gate ->
            withContext(NonCancellable) {
                gate.await()
                requireArtifact(practiceUnitId).also {
                    completedArtifactRequests += practiceUnitId
                }
            }
        } ?: requireArtifact(practiceUnitId).also {
            completedArtifactRequests += practiceUnitId
        }
        return artifact
    }

    override suspend fun submitChoice(
        submission: StudyChoiceSubmission,
    ): StudyChoiceSubmissionResult {
        submissions += submission
        val isCorrect = requireArtifact(submission.practiceUnitId)
            .assessmentItems
            .single()
            .evaluateChoice(submission.selectedChoiceId)
            .isCorrect
        return StudyChoiceSubmissionResult(
            attemptId = "attempt:${submission.requestId}",
            created = true,
            isCorrect = isCorrect,
            evidenceReason = if (isCorrect) {
                LearningEvidenceReason.INDEPENDENT_CORRECT
            } else {
                LearningEvidenceReason.INDEPENDENT_INCORRECT
            },
        )
    }

    /**
     * 复习会话的作答：记下这一次提交，并按**队列**给出推进（下一道 / 完成）。
     *
     * 这一条替代了此前的 `error("Review is outside this root Tutor test")`：不变量 2 与 3
     * 今天唯一的落点就是复习会话的 artifact 装载与揭示身份，没有这条推进路径，两条不变量
     * 都无法从页面上被违反、也就无法被验证。推进结果写回快照（与真实仓库一样按会话推进），
     * 所以同一队列里的第二次提交算的是第二道，不会把第一道的账再记一遍。
     */
    override suspend fun submitReviewChoice(
        sessionId: String,
        expectedStateVersion: Long,
        submission: StudyChoiceSubmission,
    ): StudyReviewChoiceSubmissionResult {
        submissions += submission
        val review = mutableSnapshot.value.review
        val scheduled = review.scheduledPracticeUnitIds
        val artifact = requireArtifact(submission.practiceUnitId)
        val isCorrect = artifact.assessmentItems
            .single()
            .evaluateChoice(submission.selectedChoiceId)
            .isCorrect
        val nextOrdinal = review.currentOrdinal + 1
        val completed = nextOrdinal >= scheduled.size
        val progress = StudyReviewSessionProgress(
            sessionId = sessionId,
            planId = review.planId ?: REVIEW_PLAN_ID,
            currentOrdinal = if (completed) scheduled.size else nextOrdinal,
            queueSize = scheduled.size,
            stateVersion = expectedStateVersion + 1,
            status = if (completed) {
                StudyReviewSessionStatus.COMPLETED
            } else {
                StudyReviewSessionStatus.ACTIVE
            },
        )
        mutableSnapshot.value = mutableSnapshot.value.copy(
            review = review.copy(
                currentOrdinal = progress.currentOrdinal,
                sessionStateVersion = progress.stateVersion,
            ),
        )
        return StudyReviewChoiceSubmissionResult(
            attempt = StudyChoiceSubmissionResult(
                attemptId = "attempt:${submission.requestId}",
                created = true,
                isCorrect = isCorrect,
                evidenceReason = if (isCorrect) {
                    LearningEvidenceReason.INDEPENDENT_CORRECT
                } else {
                    LearningEvidenceReason.INDEPENDENT_INCORRECT
                },
            ),
            progress = progress,
            nextPracticeUnitId = if (completed) null else scheduled[nextOrdinal],
        )
    }

    override suspend fun settleTutorJudgedReview(
        settlement: TutorJudgedReviewSettlement,
    ): TutorJudgedReviewSettlementResult {
        settleCalls += settlement
        val review = mutableSnapshot.value.review
        return TutorJudgedReviewSettlementResult(
            status = TutorJudgedReviewSettlementStatus.NO_VERDICT,
            progress = StudyReviewSessionProgress(
                sessionId = settlement.sessionId,
                planId = CAPTURED_REVIEW_PLAN_ID,
                currentOrdinal = review.currentOrdinal,
                queueSize = 1,
                stateVersion = review.sessionStateVersion ?: 0,
                status = com.tingyun.smartmistakebook.core.domain.StudyReviewSessionStatus.ACTIVE,
            ),
            nextPracticeUnitId = settlement.practiceUnitId,
        )
    }

    override suspend fun recordTeachingFocus(
        sessionId: String,
        practiceUnitId: String,
        labels: List<String>,
        cycleOrdinal: Int,
    ) = Unit

    override suspend fun submitKnowledgeQuizFeedback(
        requestId: String,
        knowledgeNodeId: String,
        correctChoiceId: String,
        selectedChoiceId: String,
        occurredAtEpochMillis: Long,
        conversationId: String,
    ) = com.tingyun.smartmistakebook.core.domain.KnowledgeQuizFeedbackResult(
        isCorrect = selectedChoiceId == correctChoiceId,
        evidenceRecorded = true,
    )

    override fun observeTeachingAdvisories(
        practiceUnitId: String?,
    ) = kotlinx.coroutines.flow.flowOf(emptyList<com.tingyun.smartmistakebook.core.model.TeachingAdvisoryRecord>())

    override suspend fun recordMisconceptionAdvisory(
        sessionId: String,
        practiceUnitId: String,
        payloadMarkdown: String,
        cycleOrdinal: Int,
    ) = Unit

    override suspend fun evaluateSchedulingModels(): SchedulingEvaluationReport? = null

    override suspend fun optimizeSchedulingParameters(): FsrsParameterOptimizer.Result? = null

    override suspend fun revealAnswer(
        request: StudyAnswerRevealRequest,
    ): StudyAnswerRevealResult {
        revealRequests += request
        return StudyAnswerRevealResult(
            outcomeId = "reveal:${request.requestId}",
            created = true,
            explanationMarkdown = requireArtifact(request.practiceUnitId).explanationMarkdown,
        )
    }

    override suspend fun startOrResumeReviewSession(
        requestId: String,
        occurredAtEpochMillis: Long,
    ): StudyReviewSessionProgress? {
        val review = mutableSnapshot.value.review
        val scheduled = review.scheduledPracticeUnitIds
        if (scheduled.isEmpty()) return null
        val progress = StudyReviewSessionProgress(
            sessionId = review.activeSessionId ?: CAPTURED_REVIEW_SESSION_ID,
            planId = review.planId ?: CAPTURED_REVIEW_PLAN_ID,
            currentOrdinal = review.currentOrdinal,
            queueSize = scheduled.size,
            stateVersion = review.sessionStateVersion ?: 0,
            status = StudyReviewSessionStatus.ACTIVE,
        )
        mutableSnapshot.value = mutableSnapshot.value.copy(
            review = review.copy(
                activeSessionId = progress.sessionId,
                currentOrdinal = progress.currentOrdinal,
                sessionStateVersion = progress.stateVersion,
            ),
        )
        return progress
    }

    override fun close() {
        artifactGates.values.forEach { it.complete(Unit) }
    }

    private companion object {
        const val CAPTURED_REVIEW_PLAN_ID = "review-plan:captured:exact-original"
        const val CAPTURED_REVIEW_SESSION_ID = "review-session:captured:exact-original"
        const val REVIEW_PLAN_ID = "review-plan:android-test"
    }
}

/**
 * D-M M1：fixture 目录退场后，本用例的**测试本地**工件（它跑在假仓库上，不需要库内目录）。
 * 两个 practice unit 各自带一道四选一检查题与标准答案。
 */
private fun requireArtifact(practiceUnitId: String): VerifiedTeachingArtifact {
    val (title, markdown, correctChoiceId) = when (practiceUnitId) {
        RootTutorFailClosedInstrumentedTest.TUTOR_PRACTICE_UNIT_ID -> Triple(
            RootTutorFailClosedInstrumentedTest.TUTOR_TITLE,
            "设函数 f(x) 在实数集上可导，且 f'(x)=(x-1)(x+2)。下列关于单调性的判断正确的是哪一项？",
            "A",
        )
        RootTutorFailClosedInstrumentedTest.SECOND_PRACTICE_UNIT_ID -> Triple(
            RootTutorFailClosedInstrumentedTest.SECOND_TITLE,
            "已知函数 f(x)=x^3-3x+1，求它在闭区间 [-2,2] 上的最大值与最小值。",
            "B",
        )
        else -> error("No test teaching artifact is registered for $practiceUnitId")
    }
    return VerifiedTeachingArtifact(
        id = "test-artifact:$practiceUnitId",
        subject = "MATH",
        title = title,
        problemMarkdown = markdown,
        explanationMarkdown = "先独立判断，再核对标准答案。",
        verification = TeachingArtifactVerification.DETERMINISTICALLY_VALIDATED,
        assessmentItems = listOf(
            TutorAssessmentItem(
                id = "test-assessment:$practiceUnitId",
                stemMarkdown = markdown,
                choices = listOf(
                    TutorChoice(id = "A", markdown = "选项 A"),
                    TutorChoice(id = "B", markdown = "选项 B"),
                    TutorChoice(id = "C", markdown = "选项 C"),
                    TutorChoice(id = "D", markdown = "选项 D"),
                ),
                correctChoiceId = correctChoiceId,
                promptMarkdown = "先独立判断，再选择最符合条件的一项。",
                knowledgeNodeIds = setOf("knowledge:test:$practiceUnitId"),
            ),
        ),
        followUps = emptyList(),
        knowledgeNodeIds = setOf("knowledge:test:$practiceUnitId"),
    )
}

private fun VerifiedTeachingArtifact.askDecision(): AdaptiveDecision = AdaptiveDecision(
    kind = AdaptiveDecisionKind.ASK,
    selectedAssessmentItemId = assessmentItems.single().id,
    reasonCodes = setOf("ANDROID_TEST_VERIFIED_ITEM"),
    selectorVersion = "root-fail-closed-test-v1",
    projectionCheckpointSequence = 0,
)
