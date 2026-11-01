package com.tingyun.smartmistakebook.feature.tutor

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tingyun.smartmistakebook.core.domain.AgentPendingRequest
import com.tingyun.smartmistakebook.core.domain.AgentPendingRequestKind
import com.tingyun.smartmistakebook.core.domain.AgentPendingRequestStatus
import com.tingyun.smartmistakebook.core.model.MistakePdfLayout
import com.tingyun.smartmistakebook.core.ui.ClaudeSegment
import com.tingyun.smartmistakebook.core.ui.ClaudeSegmentedControl
import com.tingyun.smartmistakebook.core.ui.ClaudeTextButton
import com.tingyun.smartmistakebook.core.ui.ClaudeButton
import com.tingyun.smartmistakebook.core.ui.OutlineActionChip
import com.tingyun.smartmistakebook.core.ui.SmartMistakeBookTheme
import com.tingyun.smartmistakebook.core.ui.LocalImageViewer
import com.tingyun.smartmistakebook.core.ui.ClaudeAlertDialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.semantics.SemanticsActions
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ClaudeInteractionCoverageInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val fixtures = mutableListOf<File>()
    @After fun removeFixtures() { fixtures.forEach { it.delete() } }

    @Test fun aFocusedPrimaryButtonHasAVisibleContrastingIndicator() {
        val before = FocusRequester()
        val primary = FocusRequester()
        lateinit var inputMode: InputModeManager
        compose.setContent { SmartMistakeBookTheme {
            inputMode = LocalInputModeManager.current
            Column {
                ClaudeTextButton({}, Modifier.focusRequester(before)) { Text("前一项") }
                ClaudeButton({}, Modifier.focusRequester(primary).testTag("primary_focus")) { Text("继续") }
            }
        } }
        compose.runOnIdle { inputMode.requestInputMode(InputMode.Keyboard); before.requestFocus() }
        val node = compose.onNodeWithTag("primary_focus").assertIsNotFocused()
        val original = node.captureToImage().toPixelMap()
        val y = with(compose.density) { 1.dp.roundToPx() }
        val originalEdge = original[original.width / 2, y]
        compose.runOnIdle { primary.requestFocus() }
        val focused = node.assertIsFocused().captureToImage().toPixelMap()
        val focusedEdge = focused[focused.width / 2, y]
        val a = originalEdge.luminance(); val b = focusedEdge.luminance()
        val contrast = (maxOf(a, b) + .05f) / (minOf(a, b) + .05f)
        assertTrue("Focus indicator must contrast with its button; actual ratio=$contrast", contrast >= 3f)
    }

    @Test fun keyboardFocusIsVisibleEvenWhenTheSegmentWasAlreadySelected() {
        val before = FocusRequester()
        lateinit var inputMode: InputModeManager
        compose.setContent { SmartMistakeBookTheme {
            inputMode = LocalInputModeManager.current
            Column {
                ClaudeTextButton({}, Modifier.focusRequester(before)) { Text("前一项") }
                ClaudeSegmentedControl(listOf(ClaudeSegment("a", "选项 A", "focus_a"),
                    ClaudeSegment("b", "选项 B", "focus_b")), "b", {})
            }
        } }
        compose.runOnIdle { inputMode.requestInputMode(InputMode.Keyboard); before.requestFocus() }
        val node = compose.onNodeWithTag("focus_b").assertIsSelected().assertIsNotFocused()
        val original = node.captureToImage().toPixelMap()
        val y = with(compose.density) { 1.dp.roundToPx() }
        val originalEdge = original[original.width / 2, y]
        node.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        val focused = node.assertIsFocused().captureToImage().toPixelMap()
        val focusedEdge = focused[focused.width / 2, y]
        val a = originalEdge.luminance(); val b = focusedEdge.luminance()
        val ratio = (maxOf(a, b) + .05f) / (minOf(a, b) + .05f)
        assertTrue("Selected styling must not hide keyboard focus; ratio=$ratio", ratio >= 3f)
    }

    @Test fun tabEntersOnlyTheSelectedSegmentAndArrowsMoveItsFocus() {
        val before = FocusRequester()
        lateinit var inputMode: InputModeManager
        compose.setContent { SmartMistakeBookTheme {
            inputMode = LocalInputModeManager.current
            var selected by remember { mutableStateOf("b") }
            Column {
                ClaudeTextButton({}, Modifier.focusRequester(before).testTag("before")) { Text("前一项") }
                ClaudeSegmentedControl(listOf(ClaudeSegment("a", "选项 A", "a"),
                    ClaudeSegment("b", "选项 B", "b"), ClaudeSegment("c", "选项 C", "c")), selected, { selected = it })
                ClaudeTextButton({}, Modifier.testTag("after")) { Text("后一项") }
            }
        } }
        compose.runOnIdle { inputMode.requestInputMode(InputMode.Keyboard); before.requestFocus() }
        compose.onNodeWithTag("before").performKeyInput { pressKey(Key.Tab) }
        compose.onNodeWithTag("b").assertIsFocused()
        compose.onNodeWithTag("b").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("c").assertIsSelected().assertIsFocused()
        compose.onNodeWithTag("c").performKeyInput { pressKey(Key.Tab) }
        compose.onNodeWithTag("after").assertIsFocused()
        compose.onNodeWithTag("after").performKeyInput { withKeyDown(Key.ShiftLeft) { pressKey(Key.Tab) } }
        compose.onNodeWithTag("c").assertIsFocused()
    }

    @Test fun rtlOverflowPreservesHomeEndAndASingleTabEntryAtDoubleFontScale() {
        val before = FocusRequester()
        lateinit var inputMode: InputModeManager
        val selections = mutableListOf<String>()
        compose.setContent { SmartMistakeBookTheme {
            inputMode = LocalInputModeManager.current
            val density = LocalDensity.current
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl,
                LocalDensity provides Density(density.density, 2f)) {
                var selected by remember { mutableStateOf("c") }
                Column {
                    ClaudeTextButton({}, Modifier.focusRequester(before).testTag("rtl_before")) { Text("前一项") }
                    ClaudeSegmentedControl(listOf(ClaudeSegment("a", "条件 A", "rtl_a"),
                        ClaudeSegment("b", "条件 B", "rtl_b"), ClaudeSegment("c", "条件 C", "rtl_c"),
                        ClaudeSegment("d", "条件 D", "rtl_d")), selected, { selected = it; selections += it })
                    ClaudeTextButton({}, Modifier.testTag("rtl_after")) { Text("后一项") }
                }
            }
        } }
        compose.runOnIdle { inputMode.requestInputMode(InputMode.Keyboard); before.requestFocus() }
        compose.onNodeWithTag("rtl_before").performKeyInput { pressKey(Key.Tab) }
        compose.onNodeWithTag("rtl_c").assertIsFocused().assertIsDisplayed()
            .performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("rtl_b").assertIsFocused().assertIsSelected()
            .performKeyInput { pressKey(Key.MoveHome) }
        compose.onNodeWithTag("rtl_a").assertIsFocused().assertIsSelected()
            .performKeyInput { pressKey(Key.MoveEnd) }
        compose.onNodeWithTag("rtl_d").assertIsFocused().assertIsDisplayed().assertWidthIsAtLeast(150.dp)
            .performKeyInput { pressKey(Key.MoveEnd); pressKey(Key.Tab) }
        compose.onNodeWithTag("rtl_after").assertIsFocused()
        compose.runOnIdle { assertEquals(listOf("b", "a", "d"), selections) }
    }

    @Test fun wrappedProtocolLabelsShareTheTrackHeightAndItsBottomRemainsClickable() {
        var choice = "chat"
        compose.setContent { SmartMistakeBookTheme {
            var selected by remember { mutableStateOf("chat") }
            Column(Modifier.width(320.dp)) {
                ClaudeSegmentedControl(listOf(
                    ClaudeSegment("chat", "OpenAI 兼容（默认）", "wrap_chat"),
                    ClaudeSegment("responses", "OpenAI Responses", "wrap_responses"),
                    ClaudeSegment("anthropic", "Anthropic Messages", "wrap_anthropic"),
                    ClaudeSegment("gemini", "Google Gemini", "wrap_gemini")), selected, { selected = it; choice = it })
            }
        } }
        val heights = listOf("wrap_chat", "wrap_responses", "wrap_anthropic", "wrap_gemini").map {
            compose.onNodeWithTag(it).fetchSemanticsNode().boundsInRoot.height
        }
        assertTrue("All option hit areas must fill their shared track; heights=$heights", heights.max() - heights.min() < 1f)
        compose.onNodeWithTag("wrap_gemini").performTouchInput { click(Offset(center.x, height - 2f)) }
        compose.onNodeWithTag("wrap_gemini").assertIsSelected()
        compose.runOnIdle { assertEquals("gemini", choice) }
    }

    @Test fun choiceChipsExposeSelectionWhileOrdinaryActionsStayOrdinary() {
        compose.setContent { SmartMistakeBookTheme { Column {
            OutlineActionChip("选中的条件", {}, selected = true)
            OutlineActionChip("未选的条件", {}, selected = false)
            OutlineActionChip("查看详情", {})
        } } }
        compose.onNodeWithText("选中的条件").assertIsSelected()
        compose.onNodeWithText("未选的条件").assertIsNotSelected()
        compose.onNodeWithText("查看详情").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Selected))
    }

    @Test fun processingARealConfirmationDisablesBothDecisions() {
        var decisions = 0
        val request = AgentPendingRequest(requestId = "request-a", conversationArea = "AGENT",
            conversationId = "conversation-a", logicalOperationId = "operation-a", messageId = "message-a",
            kind = AgentPendingRequestKind.SAVE_TO_NOTEBOOK,
            payloadJson = """{"captureSessionId":"capture-a"}""",
            status = AgentPendingRequestStatus.PENDING, createdAtEpochMillis = 1)
        compose.setContent { SmartMistakeBookTheme {
            TutorPendingRequestCard(request, busy = true, onDecide = { decisions++ })
        } }
        compose.onNodeWithTag("tutor_pending_request_busy").assertIsDisplayed()
        compose.onNodeWithTag("tutor_pending_request_accept").assertIsNotEnabled().performClick()
        compose.onNodeWithTag("tutor_pending_request_decline").assertIsNotEnabled().performClick()
        compose.runOnIdle { assertEquals(0, decisions) }
    }

    @Test fun localAttachmentNameOverflowHintAndViewerIdentityStayVisible() {
        val cache = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        val file = File.createTempFile("worksheet-source-", ".png", cache).also { fixtures += it }
        val bitmap = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        val images = List(9) { PendingMessageImage(file.toURI().toString()) }
        var removed = -1
        compose.setContent { SmartMistakeBookTheme { Column(Modifier.width(280.dp)) {
            PendingMessageImagesRow(images, { removed = it }, "coverage")
        } } }
        compose.waitUntil(5_000) { compose.onAllNodesWithText(file.name).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("coverage_images_overflow").assertIsDisplayed()
        compose.onNodeWithContentDescription("待发送图片 1，${file.name}").performClick()
        compose.onNodeWithText("待发送图片 1 · ${file.name}").assertIsDisplayed()
        compose.onNodeWithContentDescription("关闭图片查看").performClick()
        compose.onNodeWithTag("coverage_pending_image_remove_0").performClick()
        compose.runOnIdle { assertEquals(0, removed) }
    }

    @Test fun exportOptionsWrapAtLargeFontAndPreparationLocksThePicker() {
        compose.setContent { SmartMistakeBookTheme {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                TutorExportSheet(TutorExportSheetState(MistakePdfLayout(), emptyList(), emptySet(), editLoading = true),
                    catalogEntries = emptyList(), onLayoutChange = {}, onFormatChange = {},
                    onToggleCandidate = { _, _ -> }, onDismiss = {}, onConfirm = { _, _, _ -> },
                    onEditRequest = {}, onEditBack = {}, onEditMove = { _, _ -> }, onEditRemove = {}, onEditRestore = {},
                    onEditTitle = { _, _ -> }, onEditBlockText = { _, _, _ -> }, onEditChoiceText = { _, _, _, _ -> },
                    onEditNote = { _, _ -> }, modelEditAvailable = false, onEditInstructionChange = {},
                    onEditWithModel = {}, onConfirmEdited = {})
            }
        } }
        for (scale in 1..3) compose.onNodeWithTag("tutor_export_sheet_font_$scale").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("tutor_export_sheet_image_scale_increase").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("tutor_export_sheet_pick_more").performScrollTo().assertIsDisplayed().assertIsNotEnabled()
    }

    @Test fun imageDialogEscapeReturnsKeyboardFocusToItsActualTrigger() {
        val trigger = FocusRequester()
        var opens = 0
        lateinit var inputMode: InputModeManager
        compose.setContent { SmartMistakeBookTheme {
            inputMode = LocalInputModeManager.current
            var open by remember { mutableStateOf(false) }
            ClaudeTextButton({ opens++; open = true }, Modifier.focusRequester(trigger).testTag("image_trigger")) { Text("查看题目图") }
            if (open) LocalImageViewer("file:///missing-keyboard-fixture.png", "题目图", { open = false })
        } }
        compose.runOnIdle { inputMode.requestInputMode(InputMode.Keyboard); trigger.requestFocus() }
        compose.onNodeWithTag("image_trigger").assertIsFocused()
        compose.onNodeWithTag("image_trigger").performKeyInput { pressKey(Key.Enter) }
        compose.runOnIdle { assertEquals(1, opens) }
        compose.onNodeWithContentDescription("关闭图片查看").assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.onNodeWithContentDescription("关闭图片查看").performKeyInput { pressKey(Key.Escape) }
        compose.onNodeWithContentDescription("关闭图片查看").assertDoesNotExist()
        compose.onNodeWithTag("image_trigger").assertIsFocused()
    }

    @Test fun escapeDismissesAConfirmationWithoutExecutingItsConfirmAction() {
        lateinit var inputMode: InputModeManager
        var confirms = 0
        var dismissals = 0
        compose.setContent { SmartMistakeBookTheme {
            inputMode = LocalInputModeManager.current
            var open by remember { mutableStateOf(true) }
            if (open) ClaudeAlertDialog(onDismissRequest = { dismissals++; open = false },
                title = { Text("确认操作") }, confirmButton = {
                    ClaudeTextButton({ confirms++ }, Modifier.testTag("confirm_escape")) { Text("确认") }
                })
        } }
        compose.runOnIdle { inputMode.requestInputMode(InputMode.Keyboard) }
        compose.onNodeWithTag("confirm_escape").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.onNodeWithTag("confirm_escape").performKeyInput { pressKey(Key.Escape) }
        compose.onNodeWithTag("confirm_escape").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, confirms); assertEquals(1, dismissals) }
    }

    @Test fun escapeCannotBypassANonDismissibleConfirmation() {
        lateinit var inputMode: InputModeManager
        var dismissals = 0
        compose.setContent { SmartMistakeBookTheme {
            inputMode = LocalInputModeManager.current
            ClaudeAlertDialog(onDismissRequest = { dismissals++ },
                properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
                title = { Text("必须明确决定") }, confirmButton = {
                    ClaudeTextButton({}, Modifier.testTag("required_confirm")) { Text("确认") }
                })
        } }
        compose.runOnIdle { inputMode.requestInputMode(InputMode.Keyboard) }
        compose.onNodeWithTag("required_confirm").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.onNodeWithTag("required_confirm").performKeyInput { pressKey(Key.Escape) }
        compose.onNodeWithTag("required_confirm").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, dismissals) }
    }
}
