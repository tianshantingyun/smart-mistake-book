package com.tingyun.smartmistakebook

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Parcel
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.SAVED_STATE_REGISTRY_OWNER_KEY
import androidx.lifecycle.SavedStateViewModelFactory
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
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.data.study.RoomBackedStudyExperienceRepository
import com.tingyun.smartmistakebook.core.database.StudyDatabaseFactory
import com.tingyun.smartmistakebook.core.database.StudyDatabasePort
import com.tingyun.smartmistakebook.core.domain.ReviewStoredAnswer
import com.tingyun.smartmistakebook.core.domain.StudyProfileOverview
import com.tingyun.smartmistakebook.core.domain.StudyReviewAdvanceResult
import com.tingyun.smartmistakebook.core.domain.StudyReviewSessionProgress
import com.tingyun.smartmistakebook.core.domain.StudyReviewUndoRequest
import com.tingyun.smartmistakebook.core.domain.StudyReviewUndoResult
import com.tingyun.smartmistakebook.core.domain.StudySelfReportedReviewResult
import com.tingyun.smartmistakebook.core.domain.StudySelfReportedReviewSubmission
import com.tingyun.smartmistakebook.core.model.AppCapabilitySnapshot
import com.tingyun.smartmistakebook.core.model.NetworkMode
import com.tingyun.smartmistakebook.core.model.VerifiedTeachingArtifact
import com.tingyun.smartmistakebook.core.ui.SmartMistakeBookTheme
import com.tingyun.smartmistakebook.feature.review.ReviewSessionScreen
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * PRD-05 三键自评的设备 UI 闭环（**真 Room + 生产写路径 + 隔离库**）。
 *
 * 消灭的失败：批次 16 只有数据层设备用例（`SelfReportedReviewInstrumentedTest`）与 JVM 界面
 * 合同，三键、双击、旋转、提交中断、undo→重评、无模型已存答案这六条 UI 路径从未在设备上
 * 点过。本类每条用例都开一个按测试命名的数据库文件，改用例后删除；**不触碰**生产库
 * `smart-mistake-book.db`，也不读写真机用户的学习库。
 *
 * 隔离库的写法与 core:data 的设备用例一致：`StudyDatabaseFactory.open(context, name)` +
 * `seedAppStudyFacts` 直写 curated 夹具，再经 `RoomBackedStudyExperienceRepository` 起真实
 * 复习会话；屏幕回调全部指向同一个真库仓库，没有 fake。
 */
@RunWith(AndroidJUnit4::class)
class ReviewSelfReportDeviceUiInstrumentedTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var databaseName: String
    private lateinit var database: StudyDatabasePort
    private lateinit var applicationScope: CoroutineScope
    private lateinit var repository: RoomBackedStudyExperienceRepository
    private lateinit var started: StudyReviewSessionProgress
    private lateinit var practiceUnitId: String
    private lateinit var presentationId: String
    private lateinit var artifact: VerifiedTeachingArtifact
    private lateinit var storedAnswer: ReviewStoredAnswer

    private val capabilities = AppCapabilitySnapshot(
        networkMode = NetworkMode.STRICT_OFFLINE,
        cameraCaptureAvailable = false,
        trustedOcrAvailable = false,
        tutorTeachingEnabled = true,
        remoteModelConfigured = false,
    )

    @Before
    fun openIsolatedLibraryAndStartARealReviewSession() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        databaseName = "prd05-self-report-ui-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        database = StudyDatabaseFactory.open(context, databaseName)
        seedAppStudyFacts(
            context = context,
            database = database,
            databaseName = databaseName,
            bundle = AppCuratedStudyFixture.bundle(),
        )
        applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        repository = RoomBackedStudyExperienceRepository(
            database = database,
            applicationScope = applicationScope,
            closeDatabaseOnClose = true,
        )
        repository.initialize()
        started = requireNotNull(
            repository.startOrResumeReviewSession(
                requestId = "prd05-ui-start",
                occurredAtEpochMillis = System.currentTimeMillis(),
            ),
        ) { "隔离库没有安排出复习会话" }
        val plan = requireNotNull(
            database.observeReviewPlanForSession(started.sessionId).first(),
        ) { "复习会话没有对应的持久计划" }
        practiceUnitId = plan.queue.sortedBy { it.ordinal }.first().practiceUnitId
        presentationId = "presentation:review:${started.sessionId}:$practiceUnitId"
        artifact = requireNotNull(repository.teachingArtifact(practiceUnitId)) {
            "curated 题没有可复习的教学工件"
        }
        storedAnswer = requireNotNull(repository.storedReviewAnswer(practiceUnitId)) {
            "curated 题没有已存答案读数"
        }
        assertTrue("无模型路径依赖可信的已存答案", storedAnswer.isTrusted)
    }

    @After
    fun deleteTheIsolatedLibrary() {
        if (::applicationScope.isInitialized) runCatching { applicationScope.cancel() }
        if (::repository.isInitialized) runCatching { repository.close() }
        if (::database.isInitialized) runCatching { database.close() }
        if (::context.isInitialized && ::databaseName.isInitialized) {
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun goodKeyRecordsTheSelfReportAndShowsTheStoredAnswerWithoutAModel() {
        val owner = newOwnerState()
        setReviewContent(owner)
        waitForTag("review_self_report_root")
        chooseCorrectOption()
        clickKey("review_key_good")
        waitForTag("review_self_report_receipt")

        val receipt = composeRule.onNodeWithTag("review_self_report_receipt")
            .performScrollTo()
        receipt.assertTextContains("对（顺利回忆）", substring = true)
        receipt.assertTextContains("自评回忆成功", substring = true)
        composeRule.onNodeWithTag("review_self_report_objective")
            .performScrollTo()
            .assertTextContains("选择正确", substring = true)

        // 无模型路径的另一半：已存答案真的展示出来（不是只显示"没有可信答案"）。
        composeRule.onNodeWithTag("review_stored_answer")
            .performScrollTo()
            .assert(hasAnyDescendant(hasText(correctAnswerMarkdown())))
        composeRule.onNodeWithTag("review_reveal_write_failed").assertDoesNotExist()

        val samples = runBlocking { selfReportSamples() }
        assertEquals(1, samples.size)
        assertEquals("Good 原档 0.8 逐位落库", 0.8, samples.single().evidenceWeight, 1e-9)
        assertEquals(1, currentOrdinal())
    }

    @Test
    fun hardKeyRecordsEffortfulRecallAsPositiveEvidence() {
        val owner = newOwnerState()
        setReviewContent(owner)
        waitForTag("review_self_report_root")
        chooseCorrectOption()
        clickKey("review_key_hard")
        waitForTag("review_self_report_receipt")

        val receipt = composeRule.onNodeWithTag("review_self_report_receipt").performScrollTo()
        receipt.assertTextContains("卡顿（费力但想起来了）", substring = true)
        receipt.assertTextContains("自评回忆成功", substring = true)

        val samples = runBlocking { selfReportSamples() }
        assertEquals(1, samples.size)
        assertEquals("Hard 原档 0.7 不被折扣抬成 Good", 0.7, samples.single().evidenceWeight, 1e-9)
        assertEquals(1, currentOrdinal())
    }

    @Test
    fun againKeyRecordsAMissAsNegativeEvidence() {
        val owner = newOwnerState()
        setReviewContent(owner)
        waitForTag("review_self_report_root")
        chooseCorrectOption()
        clickKey("review_key_again")
        waitForTag("review_self_report_receipt")

        val receipt = composeRule.onNodeWithTag("review_self_report_receipt").performScrollTo()
        receipt.assertTextContains("错（没想起来/答错）", substring = true)
        receipt.assertTextContains("自评没想起来", substring = true)

        val samples = runBlocking { selfReportSamples() }
        assertEquals(1, samples.size)
        assertEquals(1.0, samples.single().evidenceWeight, 1e-9)
        assertEquals(1, currentOrdinal())
    }

    @Test
    fun doubleTapOnAKeySubmitsExactlyOneAttempt() {
        val owner = newOwnerState()
        setReviewContent(owner)
        waitForTag("review_self_report_root")
        chooseCorrectOption()

        composeRule.onNodeWithTag("review_key_good")
            .performScrollTo()
            .performTouchInput { doubleClick() }
        waitForTag("review_self_report_receipt")
        composeRule.waitForIdle()

        assertEquals(1, runBlocking { selfReportSamples() }.size)
        assertEquals(1, currentOrdinal())
    }

    @Test
    fun rotationKeepsTheRecordedReceiptAndUndoAction() {
        val owner = newOwnerState()
        setReviewContent(owner)
        waitForTag("review_self_report_root")
        chooseCorrectOption()
        clickKey("review_key_good")
        waitForTag("review_self_report_receipt")

        rotate(owner)

        composeRule.onNodeWithTag("review_self_report_receipt")
            .performScrollTo()
            .assertTextContains("对（顺利回忆）", substring = true)
        composeRule.onNodeWithTag("review_undo_answer")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("review_reveal_write_failed").assertDoesNotExist()
        assertEquals("旋转不能制造第二笔", 1, runBlocking { selfReportSamples() }.size)
    }

    @Test
    fun interruptedSubmissionRetriesTheSameRequestAndWritesOnce() {
        val owner = newOwnerState()
        val firstWriteDone = CompletableDeferred<StudySelfReportedReviewResult>()
        val requestIds = mutableListOf<String>()
        var invocations = 0
        val submit: suspend (StudySelfReportedReviewSubmission) -> StudySelfReportedReviewResult =
            { submission ->
                invocations += 1
                requestIds += submission.requestId
                if (invocations == 1) {
                    // 写侧真的落库，但响应再也回不到界面（模拟提交中断/进程死亡）。
                    val result = repository.submitReviewSelfReport(submission)
                    firstWriteDone.complete(result)
                    suspendCancellableCoroutine { /* response lost */ }
                } else {
                    repository.submitReviewSelfReport(submission)
                }
            }
        setReviewContent(owner, onSubmit = submit)
        waitForTag("review_self_report_root")
        chooseCorrectOption()
        clickKey("review_key_good")
        composeRule.waitUntil(timeoutMillis = 15_000) { firstWriteDone.isCompleted }

        rotate(owner)

        composeRule.onNodeWithTag("review_self_report_status")
            .performScrollTo()
            .assertTextContains("暂时无法确认记录结果", substring = true)
        // 提交中断后只允许原判定键重试：换键会制造与已落库那笔矛盾的 attempt。
        composeRule.onNodeWithTag("review_key_good").assertIsEnabled()
        composeRule.onNodeWithTag("review_key_hard").assertIsNotEnabled()
        composeRule.onNodeWithTag("review_key_again").assertIsNotEnabled()
        composeRule.onNodeWithTag("review_key_good").performScrollTo().performClick()
        waitForTag("review_self_report_receipt")

        assertEquals(2, invocations)
        assertEquals("重试必须复用第一次的 request id", requestIds[0], requestIds[1])
        assertEquals("中断重试只生效一次", 1, runBlocking { selfReportSamples() }.size)
        assertEquals(1, currentOrdinal())
    }

    @Test
    fun undoThenReevaluateUsesTheRewoundVersionAndAdvancesAgain() {
        val owner = newOwnerState()
        val submitted = mutableListOf<StudySelfReportedReviewResult>()
        var rewound: StudyReviewSessionProgress? = null
        val submit: suspend (StudySelfReportedReviewSubmission) -> StudySelfReportedReviewResult =
            { submission ->
                repository.submitReviewSelfReport(submission).also { submitted += it }
            }
        setReviewContent(
            owner,
            onSubmit = submit,
            onSessionRewound = { progress -> rewound = progress },
        )
        waitForTag("review_self_report_root")
        chooseCorrectOption()
        clickKey("review_key_good")
        waitForTag("review_self_report_receipt")

        composeRule.onNodeWithTag("review_undo_answer").performScrollTo().performClick()
        waitForTag("review_undo_reattempt_notice")
        composeRule.onNodeWithTag("review_undo_reattempt_notice")
            .performScrollTo()
            .assertTextContains("第 2 次作答", substring = true)
        composeRule.runOnIdle {
            assertEquals(0, rewound?.currentOrdinal)
            assertEquals(
                "撤销后版本继续递增、指针回退：版本不能当序号",
                2L,
                rewound?.stateVersion,
            )
        }

        clickKey("review_key_again")
        waitForTag("review_self_report_receipt")
        composeRule.onNodeWithTag("review_self_report_receipt")
            .performScrollTo()
            .assertTextContains("错（没想起来/答错）", substring = true)

        assertEquals(2, submitted.size)
        assertNotEquals(
            "撤销后重评必须是新的 attempt",
            submitted[0].attemptId,
            submitted[1].attemptId,
        )
        assertNotNull(
            "撤销只追加 VOID 更正，不删除原 attempt",
            runBlocking { database.readAttemptP0(submitted[0].attemptId) },
        )
        val samples = runBlocking { selfReportSamples() }
        assertEquals("原 attempt 与重评各一行", 2, samples.size)
        assertEquals("被撤销的那行不再是有效证据", 1, samples.count { it.schedulingEligible })
        assertEquals(1, currentOrdinal())
    }

    // ---- screen harness -----------------------------------------------------

    /** 在 main 线程上建 owner：LifecycleRegistry 要求主线程。 */
    private fun newOwnerState(): MutableState<ReviewUiOwner?> {
        val state = mutableStateOf<ReviewUiOwner?>(null)
        composeRule.runOnIdle { state.value = ReviewUiOwner() }
        return state
    }

    @Test
    fun doubleFontKeepsAllThreeKeysReachableWithRealTouchAndOneStoredAttempt() {
        val owner = newOwnerState()
        setReviewContent(owner, fontScale = 2f, viewportWidth = 320.dp)
        waitForTag("review_self_report_root")
        chooseCorrectOption()
        val tags = listOf("review_key_good", "review_key_hard", "review_key_again")
        composeRule.onNodeWithTag("review_self_report_keys").performScrollTo()
        tags.forEach { composeRule.onNodeWithTag(it).assertIsDisplayed() }
        val bounds = tags.map { composeRule.onNodeWithTag(it).fetchSemanticsNode().boundsInRoot }
        val minimum = 48 * context.resources.displayMetrics.density
        bounds.forEach { assertTrue("Each real key must retain its 48dp target: $it",
            it.width >= minimum - 1 && it.height >= minimum - 1) }
        bounds.forEachIndexed { index, a ->
            bounds.drop(index + 1).forEach { b ->
                assertTrue("Review keys must not overlap: $a, $b", !a.overlaps(b))
            }
        }
        val supporting = composeRule.onNodeWithText("费力但想起来了", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertTrue("The explanation must read across the page rather than form a tall column: $supporting",
            supporting.width > supporting.height)
        val directory = requireNotNull(context.getExternalFilesDir("ui-completion")).apply { mkdirs() }
        val screenshot = requireNotNull(androidx.test.platform.app.InstrumentationRegistry
            .getInstrumentation().uiAutomation.takeScreenshot())
        try {
            File(directory, "review-three-keys-320-font2.png").outputStream().use {
                check(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally {
            screenshot.recycle()
        }
        composeRule.onNodeWithTag("review_key_good").performTouchInput { click() }
        waitForTag("review_self_report_receipt")
        assertEquals(1, runBlocking { selfReportSamples() }.size)
    }

    private fun setReviewContent(
        owner: State<ReviewUiOwner?>,
        onSubmit: suspend (StudySelfReportedReviewSubmission) -> StudySelfReportedReviewResult =
            { submission -> repository.submitReviewSelfReport(submission) },
        onUndo: suspend (StudyReviewUndoRequest) -> StudyReviewUndoResult =
            { request -> repository.undoReviewSelfReport(request) },
        onContinue: (StudyReviewAdvanceResult) -> Unit = {},
        onSessionRewound: (StudyReviewSessionProgress) -> Unit = {},
        fontScale: Float? = null,
        viewportWidth: Dp? = null,
    ) {
        composeRule.setContent {
            val currentOwner = requireNotNull(owner.value)
            val density = LocalDensity.current
            key(currentOwner) {
                CompositionLocalProvider(
                    LocalLifecycleOwner provides currentOwner,
                    LocalViewModelStoreOwner provides currentOwner,
                    LocalSavedStateRegistryOwner provides currentOwner,
                    LocalDensity provides Density(density.density, fontScale ?: density.fontScale),
                ) {
                    Box(if (viewportWidth != null) Modifier.width(viewportWidth) else Modifier) {
                    SmartMistakeBookTheme {
                        ReviewSessionScreen(
                            onBack = {},
                            capabilities = capabilities,
                            practiceUnitId = practiceUnitId,
                            presentationId = presentationId,
                            teachingArtifact = artifact,
                            profile = StudyProfileOverview(hasLearningEvidence = true),
                            queuePosition = started.currentOrdinal + 1,
                            queueSize = started.queueSize,
                            onSubmitChoice = { error("机判通道不在本类断言面内") },
                            onRevealAnswer = { request -> repository.revealAnswer(request) },
                            onContinue = onContinue,
                            onSubmitSelfReport = onSubmit,
                            onUndoReview = onUndo,
                            storedAnswer = storedAnswer,
                            reviewSessionId = started.sessionId,
                            reviewStateVersion = started.stateVersion,
                            onSessionRewound = onSessionRewound,
                        )
                    }
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    /**
     * 旋转/进程重建：保存 owner 的 registry Bundle，换一个 owner 重建整棵 Compose 内容，
     * 销毁旧 owner（其 ViewModelStore 被清空）。这样 ViewModel 的 SavedStateHandle 走的是
     * 真实的保存/恢复边界，而不是只测 rememberSaveable。
     */
    private fun rotate(owner: MutableState<ReviewUiOwner?>) {
        composeRule.runOnIdle {
            val previous = requireNotNull(owner.value)
            val restored = ReviewUiOwner(previous.parcelledSavedState())
            owner.value = restored
            previous.destroy()
        }
        composeRule.waitForIdle()
    }

    private fun chooseCorrectOption() {
        composeRule.onNodeWithTag("review_choice_${correctChoiceId()}")
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
    }

    private fun clickKey(tag: String) {
        composeRule.onNodeWithTag(tag).performScrollTo().performClick()
        composeRule.waitForIdle()
    }

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun correctChoiceId(): String = artifact.assessmentItems.single().correctChoiceId

    private fun correctAnswerMarkdown(): String = artifact.assessmentItems.single().let { item ->
        item.choices.single { it.id == item.correctChoiceId }.markdown
    }

    private fun currentOrdinal(): Int = runBlocking {
        val plan = requireNotNull(database.observeReviewPlanForSession(started.sessionId).first())
        requireNotNull(plan.latestSession).currentOrdinal
    }

    private suspend fun selfReportSamples() =
        database.readReviewLogSamples(
            learnerId = RoomBackedStudyExperienceRepository.DEFAULT_LEARNER_ID,
            limit = 50,
        ).filter { it.sourceKind == SOURCE_KIND_SELF_REPORT }

    private companion object {
        /** `ReviewLogSink.SOURCE_KIND_SELF_REPORT`（internal，跨模块按字面值钉住）。 */
        const val SOURCE_KIND_SELF_REPORT = "SELF_REPORT"
    }
}

/** 独立 ViewModelStore + 经 Parcel 的 SavedStateRegistry Bundle（真实保存/恢复边界）。 */
private class ReviewUiOwner(restoredState: Bundle? = null) :
    SavedStateRegistryOwner,
    ViewModelStoreOwner,
    HasDefaultViewModelProviderFactory {
    private val registry = LifecycleRegistry(this)
    private val controller = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle = registry
    override val savedStateRegistry: SavedStateRegistry = controller.savedStateRegistry
    override val viewModelStore = ViewModelStore()

    /**
     * 屏幕里的 `viewModel(...)` 是 SavedStateHandle 构造型 VM；owner 必须像
     * NavBackStackEntry/ComponentActivity 一样提供 `SavedStateViewModelFactory`，
     * 否则 `viewModel()` 会拿到只能建无参 VM 的工厂。
     */
    override val defaultViewModelProviderFactory: ViewModelProvider.Factory =
        SavedStateViewModelFactory(
            ApplicationProvider.getApplicationContext<Application>(),
            this,
        )

    override val defaultViewModelCreationExtras: CreationExtras
        get() = MutableCreationExtras().apply {
            this[SAVED_STATE_REGISTRY_OWNER_KEY] = this@ReviewUiOwner
            this[VIEW_MODEL_STORE_OWNER_KEY] = this@ReviewUiOwner
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
