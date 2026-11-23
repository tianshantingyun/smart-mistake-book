package com.tingyun.smartmistakebook

import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.MediaStore
import android.view.WindowInsets
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.tingyun.smartmistakebook.core.database.StudyDatabaseFactory
import com.tingyun.smartmistakebook.core.domain.AppendTutorAssistantMessageCommand
import com.tingyun.smartmistakebook.core.domain.AppendTutorStudentMessageCommand
import com.tingyun.smartmistakebook.core.domain.CreateTutorConversationCommand
import com.tingyun.smartmistakebook.core.domain.TutorConversationAnchorKind
import com.tingyun.smartmistakebook.core.domain.TutorMessageStatus
import com.tingyun.smartmistakebook.core.domain.MistakeExportStatus
import com.tingyun.smartmistakebook.core.export.MistakePdfExporter
import com.tingyun.smartmistakebook.core.export.PreparedMistakePdfToken
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RootExperienceInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun seedCuratedFixtureExplicitlyForUiScenarios() {
        DestructiveDeviceTestGuard.requireExplicitOptIn()
        runBlocking {
            val application = composeRule.activity.application as SmartMistakeBookApplication
            application.studyDatabase.clearAllData()
            // D-M M1：curated 内容改由**测试源集**的夹具直写库文件（生产 fixture 系统已退场）。
            seedAppStudyFacts(
                context = application,
                database = application.studyDatabase,
                databaseName = StudyDatabaseFactory.DEFAULT_DATABASE_NAME,
                bundle = AppCuratedStudyFixture.bundle(includeTutorMistake = true),
            )
            application.studyRepository.refresh()
        }
    }

    @Test
    fun fourBottomDestinationsExposeTheirOwnRootContent() {
        waitForTag("root_review")

        navigateAndWait("nav_tutor", "root_tutor")
        navigateAndWait("nav_library", "root_library")
        navigateAndWait("nav_profile", "root_profile")
        navigateAndWait("nav_review", "root_review")
    }

    @Test
    fun reviewDoesNotExposeAnyCaptureShortcut() {
        waitForTag("root_review")

        composeRule.onAllNodesWithTag("review_capture_shortcut").assertCountEquals(0)
        composeRule.onAllNodesWithTag("review_capture_button").assertCountEquals(0)
    }

    @Test
    fun tutorStartsFromAUserProvidedQuestionWithoutBuiltInCalibration() {
        navigateAndWait("nav_tutor", "root_tutor")
        // 空态（阶段 2 起是能力目录）在：这是"讲题页没有内置的一道题"的**正面**证据。
        // 反向断言（旧文案、旧判定面）此前没有正面锚点，等于空过——先锚住新面再反向断言。
        waitForTag("tutor_empty_state")
        waitForTag("tutor_conversation_screen")
        composeRule.onAllNodesWithText("只处理你现在提出的事").assertCountEquals(0)
        composeRule.onAllNodesWithTag("tutor_choice_a").assertCountEquals(0)
        composeRule.onAllNodesWithTag("tutor_reveal_answer").assertCountEquals(0)

        composeRule.onNodeWithTag("tutor_capture_shortcut").performClick()
        waitForTag("capture_screen")
        navigateBackAndWait("root_tutor")

        // 「从错题本选择」是页内动作：在这个页面里打开错题选择器，挑中的题作为这一轮要讲的
        // 那道题进同一个讲题页面——不再跳去错题本、挑完再点「讲解这道题」绕回来。
        //
        // 能力目录把这一条排在第三条：在根讲题页（有标题栏和底部导航，视口比整屏小）里它在
        // 折叠线以下，直接 performClick 等于在窗口外落一次点击（不报错，也不生效），所以先
        // 滚到它再点。选中的标签前缀仍由这个入口给（TutorLobbyRoute 传 "lobby"）。
        composeRule.onNodeWithTag("tutor_choose_existing_button")
            .performScrollTo()
            .performClick()
        waitForTag("lobby_picker_cancel")
        composeRule.onNodeWithTag("lobby_picker_cancel").performClick()
        waitForTag("root_tutor")
        composeRule.onAllNodesWithTag("root_library").assertCountEquals(0)
    }

    @Test
    @SdkSuppress(minSdkVersion = 30)
    fun tutorComposerSitsJustAboveTheKeyboardInTheAppWindow() {
        navigateAndWait("nav_tutor", "root_tutor")
        val activity = composeRule.activity
        val input = composeRule.onNodeWithTag("tutor_chat_composer").assertIsDisplayed()
        input.performClick()
        val maxGap = with(composeRule.density) { 24.dp.toPx() }
        composeRule.waitUntil(
            conditionDescription = "Composer aligns above the visible keyboard after the IME animation",
            timeoutMillis = 5_000,
        ) {
            val keyboardTop = composeRule.runOnIdle {
                val insets = activity.window.decorView.rootWindowInsets
                if (insets?.isVisible(WindowInsets.Type.ime()) == true) {
                    activity.windowManager.currentWindowMetrics.bounds.height() -
                        insets.getInsets(WindowInsets.Type.ime()).bottom
                } else {
                    null
                }
            } ?: return@waitUntil false
            val gap = keyboardTop - input.fetchSemanticsNode().boundsInWindow.bottom
            gap in -1f..maxGap
        }
    }

    @Test
    fun tutorLearningToolsPrepareTheirRequestsInChatAndPreserveTheDraft() {
        navigateAndWait("nav_tutor", "root_tutor")
        val composer = composeRule.onNodeWithTag("tutor_chat_composer")
        val original = "原对话草稿"
        composer.performTextInput(original)
        val tools = listOf(
            "lobby_tool_paper_assembly" to "请从错题本选 6 道值得巩固的题，题量不足时少选。",
            "lobby_tool_review_planning" to "请安排未来三天的复习，给出重点、题量和主观题比例。",
        )
        for ((tool, prompt) in tools) {
            composeRule.onNodeWithTag("tutor_chat_attach").performSemanticsAction(SemanticsActions.OnClick)
            waitForTag(tool)
            composeRule.onNodeWithTag(tool).performScrollTo().performClick()
            waitForTag("root_agent_tool_conversation")
            waitForText(prompt)
            composer.assertTextContains(prompt, substring = true)
            navigateBackAndWait("root_tutor")
            composer.assertTextContains(original)
        }
    }

    @Test
    fun pickingAMistakeKeepsTheDraftInTheSameConversation() {
        navigateAndWait("nav_tutor", "root_tutor")
        val app = composeRule.activity.application as SmartMistakeBookApplication
        val entry = app.studyRepository.snapshot.value.catalog.first()
        val composer = composeRule.onNodeWithTag("tutor_chat_composer")
        composer.performTextInput("先帮我看第一步")
        composeRule.onNodeWithTag("tutor_chat_attach").performSemanticsAction(SemanticsActions.OnClick)
        waitForTag("lobby_attach_library")
        composeRule.onNodeWithTag("lobby_attach_library").performScrollTo().performClick()
        val item = "lobby_picker_item_${entry.problemId}"
        waitForTag(item)
        composeRule.onNodeWithTag(item).performClick()
        waitForText(entry.title)
        composeRule.onNodeWithTag("lobby_attached_question").assertTextContains(entry.title)
        composer.assertTextContains("先帮我看第一步")
        composeRule.onNodeWithTag("root_tutor").assertIsDisplayed()
    }

    @Test
    fun newConversationLeavesReviewPlanningMode() {
        navigateAndWait("nav_tutor", "root_tutor")
        composeRule.onNodeWithTag("tutor_chat_attach").performSemanticsAction(SemanticsActions.OnClick)
        waitForTag("lobby_tool_review_planning")
        composeRule.onNodeWithTag("lobby_tool_review_planning").performScrollTo().performClick()
        waitForTag("root_agent_tool_conversation")
        waitForText("使用本机学习记录 · 智能体复习规划")
        composeRule.onNodeWithTag("tutor_new_conversation_button").performClick()
        waitForTag("tutor_empty_state")
        composeRule.onAllNodesWithText("使用本机学习记录 · 智能体复习规划").assertCountEquals(0)
    }

    @Test
    fun confirmedAgentSelectionProducesAVerifiedPdfInTheChat() {
        val app = composeRule.activity.application as SmartMistakeBookApplication
        val snapshot = app.studyRepository.snapshot.value
        val prompt = paperAgentPrompt(snapshot)
        val code = snapshot.catalog.first().paperCode()
        val (conversationId, responseId) = seedToolReply(
            prompt,
            "按薄弱点选了一道题。\n【组卷确认数据】" + """{"ids":["$code"]}""",
        )
        openToolHistory(conversationId)
        waitForTag("agent_confirm_paper")
        composeRule.onNodeWithTag("agent_confirm_paper").performScrollTo().performClick()
        waitForTag("agent_paper_file")
        waitForText("下载")
        waitForText("分享")
        val exportId = "agent-paper-" + agentEffectId("$conversationId\n$responseId")
        val record = runBlocking { app.mistakeExportRepository.readRecord(exportId) }
        assertEquals(MistakeExportStatus.SUCCEEDED, record?.status)
        val token = requireNotNull(PreparedMistakePdfToken.fromPersistedValue(record?.preparedPdfValueOrNull()))
        assertNotNull(MistakePdfExporter(app).reopenVerified(token))
    }

    @Test
    fun acceptedReviewAdvicePersistsFutureDaysAndRefreshesTodaysQueue() {
        val app = composeRule.activity.application as SmartMistakeBookApplication
        val today = LocalDate.now()
        val tomorrow = today.plusDays(1)
        val (conversationId, _) = seedToolReply(
            "【智能体复习规划】\n每日至多十道。",
            "先做少量巩固。\n【复习确认数据】" + """{"days":[{"date":"$today","focus":[],"questionLimit":1,"subjectivePercent":40},{"date":"$tomorrow","focus":[],"questionLimit":2,"subjectivePercent":70}]}""",
        )
        openToolHistory(conversationId)
        waitForTag("agent_confirm_review")
        composeRule.onNodeWithTag("agent_confirm_review").performScrollTo().performClick()
        waitForTag("agent_review_applied")
        val preferences = runBlocking { app.schedulingSettingsStore.reviewDayPreferences.first() }
        assertEquals(1, preferences.single { it.epochDay == today.toEpochDay() }.questionLimit)
        assertEquals(70, preferences.single { it.epochDay == tomorrow.toEpochDay() }.subjectivePercent)
        composeRule.waitUntil(20_000) { app.studyRepository.snapshot.value.review.scheduledCount <= 1 }
        assertTrue(app.studyRepository.snapshot.value.review.planId != null)
    }

    private fun seedToolReply(prompt: String, response: String): Pair<String, String> {
        val app = composeRule.activity.application as SmartMistakeBookApplication
        val conversations = app.tutorConversationRepository
        val id = "test-tool-${UUID.randomUUID()}"
        val studentId = "$id-student"
        val responseId = "$id-reply"
        val now = System.currentTimeMillis()
        runBlocking {
            conversations.createConversation(CreateTutorConversationCommand(
                conversationId = id, anchorKind = TutorConversationAnchorKind.TEXT_ONLY,
                anchorId = null, anchorRevisionId = null, title = "工具回复测试", createdAtEpochMillis = now,
            ))
            conversations.appendStudentMessage(AppendTutorStudentMessageCommand(
                conversationId = id, messageId = studentId, bodyMarkdown = prompt,
                logicalOperationId = id, createdAtEpochMillis = now,
            ))
            conversations.appendAssistantMessage(AppendTutorAssistantMessageCommand(
                conversationId = id, messageId = responseId, replyToMessageId = studentId,
                bodyMarkdown = response, logicalOperationId = id, status = TutorMessageStatus.SUCCEEDED,
                createdAtEpochMillis = now, completedAtEpochMillis = now, errorCode = null,
            ))
        }
        return id to responseId
    }

    private fun openToolHistory(conversationId: String) {
        navigateAndWait("nav_tutor", "root_tutor")
        composeRule.onNodeWithTag("tutor_history_button").performClick()
        val row = "tutor_history_row_$conversationId"
        waitForTag(row)
        composeRule.onNodeWithTag(row).performClick()
        waitForTag("root_tutor")
    }

    @Test
    fun settingsPagesShowOnlyCapabilitiesThatExistNow() {
        navigateAndWait("nav_profile", "root_profile")
        composeRule.onNodeWithTag("profile_storage_setting").assertTextContains("存储与导出")

        composeRule.onNodeWithTag("profile_privacy_setting").performClick()
        waitForText("数据与隐私")
        waitForText("智能服务")
        // The privacy copy differs by flavor's NetworkMode; assert the one
        // this build actually shows instead of the localFirst wording.
        // 2026-09-09: the localFirst copy now states the global-consent model
        // (发起即交给已配置模型，不再逐次询问), so the expectation follows it.
        val localFirstCopy = "拍照、讲题或整理是你主动发起时才会进行"
        val strictOfflineCopy = "当前版本不使用联网智能服务"
        val expectedPrivacyCopy = if (
            com.tingyun.smartmistakebook.BuildConfig.FLAVOR == "strictOffline"
        ) {
            strictOfflineCopy
        } else {
            localFirstCopy
        }
        composeRule.onAllNodesWithText(
            expectedPrivacyCopy,
            substring = true,
        ).assertCountEquals(1)
        composeRule.onAllNodesWithText("尚未开放").assertCountEquals(0)
        composeRule.onAllNodesWithText("云同步", substring = true).assertCountEquals(0)
        navigateBackAndWait("root_profile")

        composeRule.onNodeWithTag("profile_storage_setting").performClick()
        waitForText("存储、备份与导出")
        composeRule.onAllNodesWithText("加密备份", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("暂未开放", substring = true).assertCountEquals(0)
    }

    @Test
    fun librarySummariesExposeReadableMathInsteadOfFormulaSource() {
        navigateAndWait("nav_library", "root_library")

        composeRule.onAllNodesWithText("$", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("\\frac", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("\\right", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("f(x)=x³-3x+1", substring = true).assertCountEquals(1)
        composeRule.onAllNodesWithText("⇌", substring = true).assertCountEquals(1)
    }

    @Test
    fun libraryItemOpensRepositoryBackedDetailAndReturnsToCatalog() {
        navigateAndWait("nav_library", "root_library")

        waitForTag("library_item_entry:m1:closed-interval-extrema")
        composeRule.onNodeWithTag("library_item_entry:m1:closed-interval-extrema")
            .performScrollTo()
            .performClick()
        waitForTag("mistake_detail_ready")
        waitForText("当前题面")
        waitForText("智能整理")

        composeRule.onNodeWithTag("mistake_detail_back").performClick()
        waitForTag("root_library")
    }

    @Test
    fun savedMistakeTutorKeepsStableBottomNavigationWithoutLeavingTheQuestion() {
        navigateAndWait("nav_library", "root_library")
        // Cold-start: the study snapshot publishes the curated catalog after the
        // shell renders, so wait for the entry instead of asserting immediately.
        waitForTag("library_item_entry:m1:closed-interval-extrema")
        composeRule.onNodeWithTag("library_item_entry:m1:closed-interval-extrema")
            .performScrollTo()
            .performClick()
        waitForTag("mistake_detail_ready")

        composeRule.onNodeWithTag("mistake_detail_start_tutor")
            .performScrollTo()
            .performClick()
        waitForTag("saved_mistake_tutor_screen")
        listOf("nav_review", "nav_tutor", "nav_library", "nav_profile").forEach(::waitForTag)
        // 同一个讲题页面：从错题详情进来看到的标题栏与大厅是同一个（历史 + 能力设置），
        // 而不是"另一个页面"。
        waitForTag("tutor_history_button")
        waitForTag("tutor_capability_settings_button")
        captureFullTutorShell()

        composeRule.onNodeWithTag("nav_tutor").performClick()
        waitForTag("saved_mistake_tutor_screen")

        composeRule.onNodeWithTag("tutor_page_back").performClick()
        waitForTag("mistake_detail_ready")
        waitForText("当前题面")
    }

    @Test
    fun savedCredentialsImmediatelyUpdateTheProfileWithoutClaimingAConnectionProbe() {
        navigateAndWait("nav_profile", "root_profile")
        composeRule.onNodeWithTag("profile_capability_setting").performClick()
        waitForTag("capability_screen")

        val isLocalFirst = composeRule.activity.packageName.endsWith(".localfirst")
        if (!isLocalFirst) {
            composeRule.onNodeWithTag("capability_strict_offline_notice")
                .performScrollTo()
                .assertTextContains("不显示服务商", substring = true)
            composeRule.onAllNodesWithTag("capability_api_key").assertCountEquals(0)
            return
        }

        composeRule.onAllNodesWithTag("capability_strict_offline_notice").assertCountEquals(0)
        composeRule.onNodeWithTag("capability_provider").performTextInput("OpenAI-compatible")
        composeRule.onNodeWithTag("capability_model_id").performTextInput("test-model")
        composeRule.onNodeWithTag("capability_base_url").performTextInput("https://example.com")
        composeRule.onNodeWithTag("capability_api_key").performTextInput("test-secret-not-real")
        composeRule.onNodeWithTag("capability_save").performScrollTo().performClick()
        waitForText("配置已保存在本机")

        navigateBackAndWait("root_profile")
        waitForText("配置已保存")
        composeRule.onAllNodesWithText("请测试模型", substring = true)
            .assertCountEquals(1)

        composeRule.onNodeWithTag("profile_capability_setting").performClick()
        waitForTag("capability_screen")
        composeRule.onNodeWithTag("capability_clear").performScrollTo().performClick()
        // 清除配置不可撤销，先过确认框。
        composeRule.onNodeWithTag("capability_clear_confirm").performClick()
        waitForText("本机配置与密钥已清除。")
        navigateBackAndWait("root_profile")
        waitForText("设置模型服务")
    }

    @Test
    fun profileOpensStudentLearningMasteryWithoutKnowledgeBaseInternals() {
        navigateAndWait("nav_profile", "root_profile")
        composeRule.onNodeWithTag("profile_learning_mastery")
            .performScrollTo()
            .performClick()

        waitForTag("learning_mastery_screen")
        waitForText("学习掌握")
        listOf(
            "原子知识",
            "知识本体",
            "检索召回",
            "学习投影",
            "grounding",
            "taxonomy",
            "embedding",
            "置信度",
            "分类依据",
            "审核",
            "批准",
        ).forEach { internalTerm ->
            composeRule.onAllNodesWithText(internalTerm, substring = true).assertCountEquals(0)
        }

        navigateBackAndWait("root_profile")
    }

    @Test
    fun submittedReviewItemIsAlreadyAdvancedWhenUserLeavesBeforeNext() {
        val application = composeRule.activity.application as SmartMistakeBookApplication
        assumeTrue(
            "The strict-offline diagnostic flavor intentionally has no interactive teaching artifact.",
            application.capabilities.tutorTeachingEnabled,
        )
        waitForTag("root_review")
        composeRule.onNodeWithTag("review_start_button").performScrollTo().performClick()
        waitForTag("review_self_report_root")
        waitForTag("review_choice_A")
        val beforeSubmit = application.studyRepository.snapshot.value.review
        val expectedOrdinal = beforeSubmit.currentOrdinal + 1
        val queueSize = beforeSubmit.scheduledCount
        assumeTrue(
            "The retained review session must have another item for the re-entry assertion.",
            expectedOrdinal < queueSize,
        )

        composeRule.onNodeWithTag("review_choice_A").performScrollTo().performClick()
        composeRule.onNodeWithTag("review_key_good").performScrollTo().performClick()
        waitForTag("review_self_report_receipt")
        waitForTag("review_next_item")
        composeRule.waitUntil(timeoutMillis = 20_000) {
            application.studyRepository.snapshot.value.review.currentOrdinal == expectedOrdinal
        }

        navigateBackAndWait("root_review")
        waitForText("继续今日复习")
        composeRule.onNodeWithTag("review_start_button").performClick()
        waitForTag("review_self_report_root")
        waitForText("第 ${expectedOrdinal + 1} / $queueSize 题")
        navigateBackAndWait("root_review")
    }

    private fun navigateAndWait(navigationTag: String, rootTag: String) {
        composeRule.onNodeWithTag(navigationTag).performClick()
        waitForTag(rootTag)
    }

    private fun captureFullTutorShell() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val resolver = instrumentation.targetContext.contentResolver
        val collection = MediaStore.Images.Media.getContentUri(
            MediaStore.VOLUME_EXTERNAL_PRIMARY,
        )
        // A fixed display name collides across re-runs on the same emulator
        // (MediaStore refuses to build a unique file that already exists), so
        // the screenshot name is unique per invocation.
        val displayName = "tutor-shell-${System.currentTimeMillis()}.png"
        val uri = checkNotNull(
            resolver.insert(
                collection,
                ContentValues().apply {
                    put(
                        MediaStore.Images.Media.DISPLAY_NAME,
                        displayName,
                    )
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        "Pictures/SmartMistakeBookQA/",
                    )
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                },
            ),
        )
        val screenshot = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val encoded = checkNotNull(resolver.openOutputStream(uri)).use { stream ->
            screenshot.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
        screenshot.recycle()
        check(encoded) { "Tutor shell screenshot could not be encoded." }
        resolver.update(
            uri,
            ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
            null,
            null,
        )
    }

    private fun waitForTag(tag: String) {
        awaitComposeIdle()
        try {
            composeRule.waitUntil(timeoutMillis = 20_000) {
                composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
            }
        } catch (timeout: ComposeTimeoutException) {
            // 超时时打印当时的语义树：否则只看到"等不到 tag"，不知道页面停在哪一态
            // （2026-09-15 加，用于定位 mistake_detail_ready 类超时）。
            composeRule.onRoot().printToString(maxDepth = 8).let { tree ->
                System.err.println("waitForTag('$tag') timed out; semantics tree:" + "\n" + tree)
            }
            val application = composeRule.activity.application as SmartMistakeBookApplication
            throw AssertionError("Waiting for $tag; review=${application.studyRepository.snapshot.value.review}\n" +
                composeRule.onRoot(useUnmergedTree = true).printToString(), timeout)
        }
    }

    private fun waitForText(text: String) {
        awaitComposeIdle()
        composeRule.waitUntil(timeoutMillis = 20_000) {
            composeRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun awaitComposeIdle() {
        composeRule.waitForIdle()
    }

    private fun navigateBackAndWait(rootTag: String, hasBottomNavigation: Boolean = true) {
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        waitForTag(rootTag)
        if (hasBottomNavigation) waitForTag("nav_review")
    }
}
