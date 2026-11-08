package com.tingyun.smartmistakebook.feature.library

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.domain.MistakeDetail
import com.tingyun.smartmistakebook.core.domain.MistakeDetailIdentity
import com.tingyun.smartmistakebook.core.domain.MistakeDetailState
import com.tingyun.smartmistakebook.core.domain.MistakeSourceSet
import com.tingyun.smartmistakebook.core.export.MistakePdfPaperEdit
import com.tingyun.smartmistakebook.core.model.CapturedQuestionDocument
import com.tingyun.smartmistakebook.core.model.ContentBlock
import com.tingyun.smartmistakebook.core.model.MistakePdfLayout
import com.tingyun.smartmistakebook.core.model.NormalizedSourceRegion
import com.tingyun.smartmistakebook.core.model.PaperEditInput
import com.tingyun.smartmistakebook.core.model.PaperEditModelPort
import com.tingyun.smartmistakebook.core.model.PaperEditOutput
import com.tingyun.smartmistakebook.core.model.QuestionBlockEvidence
import com.tingyun.smartmistakebook.core.model.QuestionBlockProvenance
import com.tingyun.smartmistakebook.core.model.QuestionBlockReviewStatus
import com.tingyun.smartmistakebook.core.model.QuestionDocument
import com.tingyun.smartmistakebook.core.model.WritingLayer
import com.tingyun.smartmistakebook.core.ui.SmartMistakeBookTheme
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 6B-3 卷面页自然语言入口（批次 50，设备侧）：输入要求 → 预览 diff → 采用/取消。
 *
 * 用 fake `PaperEditModelPort` 钉住真实 Provider 不可达时也能验收的行为：
 * - 预览出现前不碰卷面；只有"采用"才把副本写回编辑态；
 * - 失败（未知 entryId）只说明原因并保留原文/原要求，不摆"已改好"；
 * - 等待期间手动改过卷面 → 结果丢弃（学生改动优先）；
 * - 没有端口时页面不摆 AI 区（不广告未接线能力）。
 */
@RunWith(AndroidJUnit4::class)
class NativePaperEditModelInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val states = listOf(
        readyState("entry-a", "题 A", "原始文字AAA"),
        readyState("entry-b", "题 B", "原始文字BBB"),
    )

    private suspend fun load(ticket: List<String>): MistakePdfPaperEdit? =
        MistakePdfPaperEdit.fromStates(ticket, MistakePdfLayout.DEFAULT) { entryId ->
            states.first { state ->
                (state as MistakeDetailState.Ready).detail.identity.errorBookEntryId == entryId
            }
        }

    private fun setRoute(
        port: PaperEditModelPort?,
        consentGranted: Boolean = true,
        initialModelEdit: Boolean = false,
    ) {
        composeRule.setContent {
            SmartMistakeBookTheme {
                MistakePaperEditRoute(
                    entryIds = listOf("entry-a", "entry-b"),
                    loadPaper = { ids -> load(ids) },
                    onBack = {},
                    onExport = { _, _ -> },
                    modelPort = port,
                    modelConsentGranted = consentGranted,
                    initialModelEdit = initialModelEdit,
                )
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("native_paper_edit_question_entry-a")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private fun openAiSection() {
        composeRule.onNodeWithTag("native_paper_edit_ai_toggle")
            .performScrollTo()
            .performClick()
    }

    @Test
    fun theAiSectionIsNotAdvertisedWithoutAModelPort() {
        setRoute(port = null)

        composeRule.onNodeWithTag("native_paper_edit_ai_toggle").assertDoesNotExist()
    }

    @Test
    fun aModelPreviewIsShownBeforeAdoptionAndOnlyAdoptionChangesThePaper() {
        val port = RecordingPort {
            output("""{"commands":[{"type":"set_question_title","entryId":"entry-a","title":"题 A 新"}]}""")
        }
        setRoute(port)

        openAiSection()
        composeRule.onNodeWithTag("native_paper_edit_ai_instruction")
            .performScrollTo()
            .performTextReplacement("把第一题标题改短")
        composeRule.onNodeWithTag("native_paper_edit_ai_generate")
            .performScrollTo()
            .performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("native_paper_edit_ai_preview")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        // 预览阶段卷面不动：标题仍是原文。
        composeRule.onNodeWithTag("native_paper_edit_title_entry-a")
            .performScrollTo()
            .assertTextContains("题 A")
        composeRule.onNodeWithText("「题 A 新」 · 改标题").assertExists()
        composeRule.onNodeWithTag("native_paper_edit_ai_preview")
            .assertTextContains("1 条命令", substring = true)

        composeRule.onNodeWithTag("native_paper_edit_ai_adopt")
            .performScrollTo()
            .performClick()

        composeRule.onNodeWithTag("native_paper_edit_title_entry-a")
            .performScrollTo()
            .assertTextContains("题 A 新")
        composeRule.onNodeWithTag("native_paper_edit_ai_preview").assertDoesNotExist()

        val input = port.inputs.single()
        assertEquals("把第一题标题改短", input.instruction)
        assertTrue(input.paperId.startsWith("native-paper-edit:"))
        assertTrue(input.revision >= 0)
        assertEquals(listOf(true), port.consents)
    }

    @Test
    fun cancellingAPreviewKeepsThePaperUntouched() {
        val port = RecordingPort {
            output("""{"commands":[{"type":"set_question_title","entryId":"entry-a","title":"模型标题"}]}""")
        }
        setRoute(port)

        openAiSection()
        composeRule.onNodeWithTag("native_paper_edit_ai_instruction")
            .performScrollTo()
            .performTextReplacement("改标题")
        composeRule.onNodeWithTag("native_paper_edit_ai_generate")
            .performScrollTo()
            .performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("native_paper_edit_ai_preview")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        composeRule.onNodeWithTag("native_paper_edit_ai_cancel")
            .performScrollTo()
            .performClick()

        composeRule.onNodeWithTag("native_paper_edit_ai_preview").assertDoesNotExist()
        composeRule.onNodeWithText("已取消这次修改", substring = true).assertExists()
        composeRule.onNodeWithTag("native_paper_edit_title_entry-a")
            .performScrollTo()
            .assertTextContains("题 A")
    }

    @Test
    fun changingTheInstructionInvalidatesTheOldPreview() {
        val port = RecordingPort {
            output("""{"commands":[{"type":"set_question_title","entryId":"entry-a","title":"模型标题"}]}""")
        }
        setRoute(port)

        openAiSection()
        composeRule.onNodeWithTag("native_paper_edit_ai_instruction")
            .performScrollTo()
            .performTextReplacement("改标题")
        composeRule.onNodeWithTag("native_paper_edit_ai_generate")
            .performScrollTo()
            .performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("native_paper_edit_ai_preview")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        composeRule.onNodeWithTag("native_paper_edit_ai_instruction")
            .performScrollTo()
            .performTextReplacement("换一个要求")

        composeRule.onNodeWithTag("native_paper_edit_ai_preview").assertDoesNotExist()
        composeRule.onNodeWithText("修改要求已改", substring = true).assertExists()
        composeRule.onNodeWithTag("native_paper_edit_title_entry-a")
            .performScrollTo()
            .assertTextContains("题 A")
    }

    @Test
    fun aRejectedModelReplyKeepsThePaperAndTheStudentsInstruction() {
        val port = RecordingPort {
            output("""{"commands":[{"type":"remove_question","entryId":"entry-missing"}]}""")
        }
        setRoute(port)

        openAiSection()
        composeRule.onNodeWithTag("native_paper_edit_ai_instruction")
            .performScrollTo()
            .performTextReplacement("删掉那道不存在的题")
        composeRule.onNodeWithTag("native_paper_edit_ai_generate")
            .performScrollTo()
            .performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("native_paper_edit_ai_message")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        composeRule.onNodeWithText("卷面保持原样", substring = true).assertExists()
        composeRule.onNodeWithTag("native_paper_edit_ai_preview").assertDoesNotExist()
        composeRule.onNodeWithTag("native_paper_edit_title_entry-a")
            .performScrollTo()
            .assertTextContains("题 A")
        // 失败保留学生写下的要求，便于直接改写/重试。
        composeRule.onNodeWithTag("native_paper_edit_ai_instruction")
            .performScrollTo()
            .assertTextContains("删掉那道不存在的题")
    }

    @Test
    fun aManualEditWhileWaitingPreventsTheStalePreviewFromBeingApplied() {
        val reply = CompletableDeferred<PaperEditOutput>()
        val port = object : PaperEditModelPort {
            override suspend fun commandsFor(
                input: PaperEditInput,
                agentConsentGranted: Boolean,
            ): PaperEditOutput = reply.await()
        }
        setRoute(port)

        openAiSection()
        composeRule.onNodeWithTag("native_paper_edit_ai_instruction")
            .performScrollTo()
            .performTextReplacement("改标题")
        composeRule.onNodeWithTag("native_paper_edit_ai_generate")
            .performScrollTo()
            .performClick()
        // 等待期间学生手动改了同一道题的标题。
        composeRule.onNodeWithTag("native_paper_edit_title_entry-a")
            .performScrollTo()
            .performTextReplacement("手动改的标题")

        reply.complete(
            output("""{"commands":[{"type":"set_question_title","entryId":"entry-a","title":"模型标题"}]}"""),
        )
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("native_paper_edit_ai_message")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        composeRule.onNodeWithText("等待时被改过", substring = true).assertExists()
        composeRule.onNodeWithTag("native_paper_edit_ai_preview").assertDoesNotExist()
        composeRule.onNodeWithTag("native_paper_edit_title_entry-a")
            .performScrollTo()
            .assertTextContains("手动改的标题")
    }

    @Test
    fun withoutConsentTheSectionExplainsWhyItCannotRun() {
        val port = RecordingPort {
            output("""{"commands":[{"type":"set_question_title","entryId":"entry-a","title":"新"}]}""")
        }
        setRoute(port, consentGranted = false, initialModelEdit = true)

        composeRule.onNodeWithText("尚未启用模型").assertExists()
        composeRule.onNodeWithTag("native_paper_edit_ai_generate").assertIsNotEnabled()
        assertEquals(0, port.inputs.size)
    }

    private class RecordingPort(
        private val reply: () -> PaperEditOutput,
    ) : PaperEditModelPort {
        val inputs = mutableListOf<PaperEditInput>()
        val consents = mutableListOf<Boolean>()
        private val calls = AtomicInteger()

        override suspend fun commandsFor(
            input: PaperEditInput,
            agentConsentGranted: Boolean,
        ): PaperEditOutput {
            calls.incrementAndGet()
            inputs += input
            consents += agentConsentGranted
            return reply()
        }
    }

    private fun output(json: String): PaperEditOutput =
        PaperEditOutput(commandsJson = json, modelVersion = "fake-paper-edit-1")

    private fun readyState(
        entryId: String,
        title: String,
        paragraphText: String,
    ): MistakeDetailState.Ready {
        val block = ContentBlock.Paragraph("stem-$entryId", paragraphText)
        return MistakeDetailState.Ready(
            detail = MistakeDetail(
                identity = MistakeDetailIdentity(
                    errorBookEntryId = entryId,
                    problemId = "problem-$entryId",
                    problemRevisionId = "revision-$entryId",
                    revisionNumber = 3,
                    title = title,
                    subject = "MATH",
                ),
                fallbackMarkdown = "备用题面",
                source = MistakeSourceSet.Missing,
                userNote = "这题的备注",
            ),
            questionDocument = CapturedQuestionDocument(
                document = QuestionDocument(
                    id = "document-$entryId",
                    title = title,
                    blocks = listOf(block),
                ),
                blockEvidence = listOf(
                    QuestionBlockEvidence(
                        blockId = block.id,
                        sourceAssetId = "asset-$entryId",
                        sourceRegion = NormalizedSourceRegion(0.0, 0.0, 1.0, 1.0),
                        writingLayer = WritingLayer.PRINTED,
                        provenance = QuestionBlockProvenance.USER_CORRECTION,
                        reviewStatus = QuestionBlockReviewStatus.USER_CONFIRMED,
                    ),
                ),
            ),
        )
    }
}
