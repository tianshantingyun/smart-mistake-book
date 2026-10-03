package com.tingyun.smartmistakebook.feature.library

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.domain.MistakeDetail
import com.tingyun.smartmistakebook.core.domain.MistakeDetailIdentity
import com.tingyun.smartmistakebook.core.domain.MistakeDetailState
import com.tingyun.smartmistakebook.core.domain.MistakeRevisionKey
import com.tingyun.smartmistakebook.core.domain.MistakeSourceSet
import com.tingyun.smartmistakebook.core.model.CapturedQuestionDocument
import com.tingyun.smartmistakebook.core.model.ContentBlock
import com.tingyun.smartmistakebook.core.model.NormalizedSourceRegion
import com.tingyun.smartmistakebook.core.model.QuestionBlockEvidence
import com.tingyun.smartmistakebook.core.model.QuestionBlockProvenance
import com.tingyun.smartmistakebook.core.model.QuestionBlockReviewStatus
import com.tingyun.smartmistakebook.core.model.QuestionDocument
import com.tingyun.smartmistakebook.core.model.WritingLayer
import com.tingyun.smartmistakebook.core.ui.SmartMistakeBookTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 错题详情上的导出动作（L7 起它只负责"把这一版交给后台导出"，不再打开前台导出页）。
 *
 * L7 迁移说明：改前的 `MistakeExportRoute` 预览/保存页用例（真实预览 + 三动作）随前台
 * 导出页一起退场；交付链本身的覆盖留在 `core:export` 的 `MistakePdfExporterInstrumentedTest`
 * （真实预览/分享/保存），导出编排留在 JVM 的 `MistakeExportJobRunnerTest`，成果入口见
 * `MistakeExportHubInstrumentedTest`。
 */
@RunWith(AndroidJUnit4::class)
class MistakeExportInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun readyDetailOffersTutorAndPassesTheVisibleExactRevision() {
        val state = readyState(
            listOf(ContentBlock.Paragraph("stem", "求函数 f(x)=x² 的单调区间。")),
        )
        var tutorKey: MistakeRevisionKey? = null
        composeRule.setContent {
            SmartMistakeBookTheme {
                MistakeDetailContent(
                    state = state,
                    onBack = {},
                    onExport = {},
                    onTutor = { tutorKey = it },
                )
            }
        }

        composeRule.onNodeWithTag("mistake_detail_start_tutor").performClick()

        composeRule.runOnIdle {
            assertEquals(EXACT_KEY, tutorKey)
        }
    }

    @Test
    fun readyDetailOffersExportAndPassesTheVisibleExactRevision() {
        val state = readyState(
            listOf(ContentBlock.Paragraph("stem", "求函数 f(x)=x² 的单调区间。")),
        )
        var exportedKey: MistakeRevisionKey? = null
        composeRule.setContent {
            SmartMistakeBookTheme {
                MistakeDetailContent(
                    state = state,
                    onBack = {},
                    onExport = { exportedKey = it },
                )
            }
        }

        composeRule.onNodeWithTag("mistake_detail_export_a4").performClick()

        composeRule.runOnIdle {
            assertEquals(EXACT_KEY, exportedKey)
        }
    }

    private fun readyState(blocks: List<ContentBlock>): MistakeDetailState.Ready =
        MistakeDetailState.Ready(
            detail = MistakeDetail(
                identity = identity(),
                fallbackMarkdown = "备用题面",
                source = MistakeSourceSet.Missing,
            ),
            questionDocument = CapturedQuestionDocument(
                document = QuestionDocument(
                    id = "document-1",
                    title = "函数单调区间",
                    blocks = blocks,
                ),
                blockEvidence = blocks.map { block ->
                    QuestionBlockEvidence(
                        blockId = block.id,
                        sourceAssetId = "asset-1",
                        sourceRegion = NormalizedSourceRegion(0.0, 0.0, 1.0, 1.0),
                        writingLayer = if (block is ContentBlock.Figure) {
                            WritingLayer.DIAGRAM
                        } else {
                            WritingLayer.PRINTED
                        },
                        provenance = QuestionBlockProvenance.USER_CORRECTION,
                        reviewStatus = QuestionBlockReviewStatus.USER_CONFIRMED,
                    )
                },
            ),
        )

    private fun identity() = MistakeDetailIdentity(
        errorBookEntryId = EXACT_KEY.entryId,
        problemId = EXACT_KEY.problemId,
        problemRevisionId = EXACT_KEY.problemRevisionId,
        revisionNumber = 3,
        title = "函数单调区间",
        subject = "MATH",
    )

    private companion object {
        val EXACT_KEY = MistakeRevisionKey(
            entryId = "entry-1",
            problemId = "problem-1",
            problemRevisionId = "revision-3",
        )
    }
}
