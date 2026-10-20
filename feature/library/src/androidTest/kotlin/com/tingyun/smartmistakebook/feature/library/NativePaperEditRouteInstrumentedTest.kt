package com.tingyun.smartmistakebook.feature.library

import androidx.compose.ui.test.assertIsNotEnabled
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
import com.tingyun.smartmistakebook.core.domain.MistakeRevisionKey
import com.tingyun.smartmistakebook.core.domain.MistakeRevisionSummary
import com.tingyun.smartmistakebook.core.domain.MistakeSourceSet
import com.tingyun.smartmistakebook.core.export.MistakePaperDraft
import com.tingyun.smartmistakebook.core.export.MistakePdfBatchEligibility
import com.tingyun.smartmistakebook.core.export.MistakePdfBatchEligibilityResult
import com.tingyun.smartmistakebook.core.export.MistakePdfBlock
import com.tingyun.smartmistakebook.core.export.MistakePdfPaperEdit
import com.tingyun.smartmistakebook.core.model.CapturedQuestionDocument
import com.tingyun.smartmistakebook.core.model.ContentBlock
import com.tingyun.smartmistakebook.core.model.MistakeExportFormat
import com.tingyun.smartmistakebook.core.model.MistakePdfLayout
import com.tingyun.smartmistakebook.core.model.NormalizedSourceRegion
import com.tingyun.smartmistakebook.core.model.QuestionBlockEvidence
import com.tingyun.smartmistakebook.core.model.QuestionBlockProvenance
import com.tingyun.smartmistakebook.core.model.QuestionBlockReviewStatus
import com.tingyun.smartmistakebook.core.model.QuestionDocument
import com.tingyun.smartmistakebook.core.model.WritingLayer
import com.tingyun.smartmistakebook.core.ui.SmartMistakeBookTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 6B-2 原生编辑入口（T6）：不经过模型 `START_EXPORT` 卡也能从原生页面进入编辑 → 导出。
 *
 * 覆盖：
 * - 原生编辑路由（加载 / 改文字 / 空白 fail-closed / 无可编辑题面）；
 * - 未编辑确认与正常批量导出同一份快照（逐位不变）；
 * - 编辑后换指纹、但来源题面文档指纹不变；
 * - 「导出成果」与错题详情的两个原生入口（入口只交出装配处提供的来源，不凭记录猜题）。
 */
@RunWith(AndroidJUnit4::class)
class NativePaperEditRouteInstrumentedTest {
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

    private fun setRoute(onExport: (MistakePaperDraft, MistakeExportFormat) -> Unit) {
        composeRule.setContent {
            SmartMistakeBookTheme {
                MistakePaperEditRoute(
                    entryIds = listOf("entry-a", "entry-b"),
                    loadPaper = { ids -> load(ids) },
                    onBack = {},
                    onExport = onExport,
                )
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("native_paper_edit_question_entry-a")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    @Test
    fun uneditedNativeExportMatchesTheNormalBatchSnapshot() {
        var captured: MistakePaperDraft? = null
        var capturedFormat: MistakeExportFormat? = null
        setRoute { draft, format ->
            captured = draft
            capturedFormat = format
        }

        composeRule.onNodeWithTag("native_paper_edit_export").performScrollTo().performClick()
        composeRule.onNodeWithTag("mistake_export_format_confirm").performClick()

        composeRule.runOnIdle {
            val draft = checkNotNull(captured)
            val direct = MistakePdfBatchEligibility.check(states, MistakePdfLayout.DEFAULT)
                as MistakePdfBatchEligibilityResult.Eligible
            assertEquals(MistakeExportFormat.PDF, capturedFormat)
            assertFalse(draft.edited)
            assertEquals(draft.sourceInputSha256, draft.input.inputSha256)
            assertEquals(direct.input.inputSha256, draft.input.inputSha256)
            assertEquals(direct.input.blocks, draft.input.blocks)
            assertEquals(direct.input.subtitle, draft.input.subtitle)
        }
    }

    @Test
    fun editingATitleRecomputesTheFingerprintWithoutTouchingTheSourceQuestion() {
        var captured: MistakePaperDraft? = null
        setRoute { draft, _ -> captured = draft }

        composeRule.onNodeWithTag("native_paper_edit_title_entry-a")
            .performScrollTo()
            .performTextReplacement("改过的题 A")
        composeRule.onNodeWithTag("native_paper_edit_export").performScrollTo().performClick()
        composeRule.onNodeWithTag("mistake_export_format_confirm").performClick()

        composeRule.runOnIdle {
            val draft = checkNotNull(captured)
            val direct = MistakePdfBatchEligibility.check(states, MistakePdfLayout.DEFAULT)
                as MistakePdfBatchEligibilityResult.Eligible
            assertTrue(draft.edited)
            assertNotEquals(direct.input.inputSha256, draft.input.inputSha256)
            assertEquals(direct.input.questionDocumentSha256, draft.input.questionDocumentSha256)
            assertTrue(
                draft.input.blocks.any { block ->
                    block is MistakePdfBlock.SectionHeading && block.text == "1. 改过的题 A"
                },
            )
            // 原题没有反写：来源详情仍是原来的标题。
            assertEquals(
                "题 A",
                (states.first() as MistakeDetailState.Ready).detail.identity.title,
            )
        }
    }

    @Test
    fun aBlankTitleFailsClosedBeforeAnyExportSheetOpens() {
        setRoute { _, _ -> error("A blank paper must not be exportable") }

        composeRule.onNodeWithTag("native_paper_edit_title_entry-a")
            .performScrollTo()
            .performTextReplacement("")

        composeRule.onNodeWithTag("native_paper_edit_blank_warning").assertExists()
        composeRule.onNodeWithTag("native_paper_edit_export").assertIsNotEnabled()
    }

    @Test
    fun noEditableQuestionIsExplainedInsteadOfShowingAnEmptyEditor() {
        composeRule.setContent {
            SmartMistakeBookTheme {
                MistakePaperEditRoute(
                    entryIds = listOf("entry-missing"),
                    loadPaper = { null },
                    onBack = {},
                    onExport = { _, _ -> error("Nothing can be exported") },
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("native_paper_edit_unavailable")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithText("没有可编辑的卷面", substring = true).assertExists()
    }

    @Test
    fun theExportHubOffersTheNativeEditEntryWithTheSourcesSuppliedByTheCaller() {
        var openedWith: List<String>? = null
        composeRule.setContent {
            SmartMistakeBookTheme {
                MistakeExportHubRoute(
                    records = emptyList(),
                    onBack = {},
                    paperEditEntryIds = listOf("entry-a", "entry-b"),
                    onEditPaper = { entryIds -> openedWith = entryIds },
                )
            }
        }

        composeRule.onNodeWithTag("export_hub_edit_paper").performScrollTo().performClick()

        composeRule.runOnIdle {
            assertEquals(listOf("entry-a", "entry-b"), openedWith)
        }
    }

    @Test
    fun theExportHubDoesNotGuessWhereAnEditedPaperComesFrom() {
        composeRule.setContent {
            SmartMistakeBookTheme {
                MistakeExportHubRoute(records = emptyList(), onBack = {})
            }
        }

        composeRule.onNodeWithTag("export_hub_edit_paper").assertDoesNotExist()
    }

    @Test
    fun theMistakeDetailOffersTheNativeEditEntryForTheCurrentRevision() {
        var requested: MistakeRevisionKey? = null
        composeRule.setContent {
            SmartMistakeBookTheme {
                MistakeDetailContent(
                    state = states.first(),
                    onBack = {},
                    onExport = {},
                    onEditPaper = { key -> requested = key },
                )
            }
        }

        composeRule.onNodeWithTag("mistake_detail_edit_paper").performScrollTo().performClick()

        composeRule.runOnIdle {
            assertNotNull(requested)
            assertEquals("entry-a", requested?.entryId)
            assertEquals("revision-entry-a", requested?.problemRevisionId)
        }
    }

    @Test
    fun aHistoricalRevisionDoesNotOfferPaperEditing() {
        composeRule.setContent {
            SmartMistakeBookTheme {
                MistakeDetailContent(
                    state = states.first(),
                    onBack = {},
                    onExport = {},
                    revisionHistory = revisionHistory(),
                    selectedRevisionId = "revision-1",
                    onEditPaper = {},
                )
            }
        }

        composeRule.onNodeWithTag("mistake_detail_edit_paper").assertDoesNotExist()
    }

    // ---- fixtures ----

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

    private fun revisionHistory() = listOf(
        MistakeRevisionSummary(
            entryId = "entry-a",
            problemId = "problem-entry-a",
            problemRevisionId = "revision-entry-a",
            revisionNumber = 3,
            title = "题 A",
            createdAtEpochMillis = 1_752_988_800_000,
            isCurrent = true,
        ),
        MistakeRevisionSummary(
            entryId = "entry-a",
            problemId = "problem-entry-a",
            problemRevisionId = "revision-1",
            revisionNumber = 1,
            title = "题 A（初版）",
            createdAtEpochMillis = 1_752_902_400_000,
            isCurrent = false,
        ),
    )
}
