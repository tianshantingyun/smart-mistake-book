package com.tingyun.smartmistakebook.core.export

import com.tingyun.smartmistakebook.core.domain.MistakeDetail
import com.tingyun.smartmistakebook.core.domain.MistakeDetailIdentity
import com.tingyun.smartmistakebook.core.domain.MistakeDetailState
import com.tingyun.smartmistakebook.core.domain.MistakeSourceAsset
import com.tingyun.smartmistakebook.core.domain.MistakeSourceLocation
import com.tingyun.smartmistakebook.core.domain.MistakeSourceSet
import com.tingyun.smartmistakebook.core.model.CapturedQuestionDocument
import com.tingyun.smartmistakebook.core.model.ContentBlock
import com.tingyun.smartmistakebook.core.model.MistakePdfLayout
import com.tingyun.smartmistakebook.core.model.NormalizedSourceRegion
import com.tingyun.smartmistakebook.core.model.QuestionBlockEvidence
import com.tingyun.smartmistakebook.core.model.QuestionBlockProvenance
import com.tingyun.smartmistakebook.core.model.QuestionBlockReviewStatus
import com.tingyun.smartmistakebook.core.model.QuestionDocument
import com.tingyun.smartmistakebook.core.model.WritingLayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MistakePdfBatchEligibilityTest {
    @Test
    fun notesRemainInsideTheirOwnQuestionWhenExportedTogether() {
        val first = readyState("first", "第一题").let {
            it.copy(detail = it.detail.copy(userNote = "先检查定义域"))
        }
        val second = readyState("second", "第二题").let {
            it.copy(detail = it.detail.copy(userNote = "别忘记单位"))
        }
        val input = (MistakePdfBatchEligibility.check(listOf(first, second),
            com.tingyun.smartmistakebook.core.model.MistakePdfLayout(includeNote = true)) as
            MistakePdfBatchEligibilityResult.Eligible).input
        val lines = MistakePdfPagePlanner.plan(input, PdfTextMeasure { _, start, end, _ ->
            (end - start) * 6f
        }).pages.flatten().filterIsInstance<PdfPlannedItem.Line>().map { it.text }
        val secondHeading = lines.indexOf("2. 第二题")
        assertTrue("First note must remain before the next question", lines.indexOf("先检查定义域") in 1 until secondHeading)
        assertTrue("Second note must remain with the second question", lines.indexOf("别忘记单位") > secondHeading)
    }

    @Test
    fun preservesVisibleOrderAndAutomaticallyOmitsUnavailableEntries() {
        val result = MistakePdfBatchEligibility.check(
            listOf(
                readyState("entry-b", "第二题"),
                MistakeDetailState.NotFound,
                readyState("entry-a", "第一题"),
            ),
        ) as MistakePdfBatchEligibilityResult.Eligible

        assertEquals(2, result.includedCount)
        assertEquals(1, result.omittedCount)
        assertEquals(MistakePdfExportLimits.MAX_BATCH_PAGES, result.input.maxPages)
        assertEquals(
            listOf("1. 第二题", "2. 第一题"),
            result.input.blocks
                .filterIsInstance<MistakePdfBlock.SectionHeading>()
                .map(MistakePdfBlock.SectionHeading::text),
        )
        assertEquals(
            result.input.blocks.size,
            result.input.blocks.map(MistakePdfBlock::id).distinct().size,
        )
    }

    @Test
    fun fingerprintChangesWhenVisibleOrderChanges() {
        val first = readyState("entry-a", "第一题")
        val second = readyState("entry-b", "第二题")

        val forward = MistakePdfBatchEligibility.check(
            listOf(first, second),
        ) as MistakePdfBatchEligibilityResult.Eligible
        val reversed = MistakePdfBatchEligibility.check(
            listOf(second, first),
        ) as MistakePdfBatchEligibilityResult.Eligible

        assertNotEquals(forward.input.inputSha256, reversed.input.inputSha256)
    }

    @Test
    fun emptyOversizedAndEntirelyUnavailableListsFailBeforeRendering() {
        assertEquals(
            MistakePdfBatchIneligibility.EMPTY,
            (MistakePdfBatchEligibility.check(emptyList()) as
                MistakePdfBatchEligibilityResult.Ineligible).reason,
        )
        assertEquals(
            MistakePdfBatchIneligibility.TOO_MANY_QUESTIONS,
            (
                MistakePdfBatchEligibility.check(
                    List(MistakePdfBatchEligibility.MAX_QUESTIONS + 1) {
                        MistakeDetailState.NotFound
                    },
                ) as MistakePdfBatchEligibilityResult.Ineligible
                ).reason,
        )
        assertEquals(
            MistakePdfBatchIneligibility.NO_READY_QUESTION,
            (
                MistakePdfBatchEligibility.check(
                    listOf(
                        MistakeDetailState.NotFound,
                        MistakeDetailState.Legacy(detail("legacy", "旧题")),
                    ),
                ) as MistakePdfBatchEligibilityResult.Ineligible
                ).reason,
        )
    }

    @Test
    fun batchSnapshotNeverCopiesFallbackMarkdown() {
        val result = MistakePdfBatchEligibility.check(
            listOf(readyState("entry-a", "第一题")),
        ) as MistakePdfBatchEligibilityResult.Eligible

        val exportedText = result.input.blocks.joinToString { block ->
            when (block) {
                is MistakePdfBlock.Image -> block.title
                is MistakePdfBlock.Note -> block.text
                is MistakePdfBlock.Paragraph -> block.text
                is MistakePdfBlock.SectionHeading -> block.text
                is MistakePdfBlock.Formula -> block.alternativeText
                is MistakePdfBlock.ChoiceGroup -> block.prompt
                is MistakePdfBlock.Figure -> block.alternativeText
            }
        }
        assertTrue("绝不能导出的备用文字" !in exportedText)
    }

    /**
     * KD-55：批量 Image 块必须携带来源资产记录的 sha/长度——否则批量 docx 的题面图
     * 会绕过渲染前复核（单题路径有，批量/卷面路径没有）。
     */
    @Test
    fun batchImageBlocksCarryTheCleanImageRecordIntegrity() {
        val result = MistakePdfBatchEligibility.check(
            listOf(
                readyState(
                    "entry-a",
                    "第一题",
                    source = MistakeSourceSet.Present(listOf(sourceAsset("file:///clean-a.png"))),
                ),
            ),
        ) as MistakePdfBatchEligibilityResult.Eligible

        val image = result.input.blocks.filterIsInstance<MistakePdfBlock.Image>().single()
        assertEquals("file:///clean-a.png", image.localUri)
        assertEquals("a".repeat(64), image.expectedContentSha256)
        assertEquals(1_024L, image.expectedByteSize)
    }

    /**
     * AUD-06 ①②：批量键 = f(题数, 版式, 每题的单题键)，批量自己不再重算内容，所以这里钉的是
     * 「每题键确实被批量键消费」——改可见备注、或改净化图（同 URI 换字节 / 同字节换 URI），
     * 整批都必须换键，否则一次批量导出会命中旧 PDF。
     */
    @Test
    fun visibleNoteAndCleanImageChangesRewriteTheBatchFingerprint() {
        val visibleNote = MistakePdfLayout(includeNote = true)
        val before = readyState("entry-a", "第一题", userNote = "旧备注")
        val renamed = readyState("entry-a", "第一题", userNote = "新备注")

        assertNotEquals(
            batchFingerprint(listOf(before), visibleNote),
            batchFingerprint(listOf(renamed), visibleNote),
        )

        // 净化图与备注勾选无关：有 clean-image 就一律进渲染（见 MistakePdfBatchEligibility 的
        // Image 块），所以这里用默认版式（includeNote=false）同样要求换键。
        val asset = sourceAsset("file:///clean-a.png")
        val withImage = readyState("entry-a", "第一题", source = MistakeSourceSet.Present(listOf(asset)))
        val rebytes = readyState(
            "entry-a",
            "第一题",
            source = MistakeSourceSet.Present(listOf(asset.copy(contentSha256 = "b".repeat(64)))),
        )
        val moved = readyState(
            "entry-a",
            "第一题",
            source = MistakeSourceSet.Present(
                listOf(asset.copy(location = MistakeSourceLocation.Available("file:///clean-b.png"))),
            ),
        )

        assertNotEquals(
            batchFingerprint(listOf(withImage)),
            batchFingerprint(listOf(rebytes)),
        )
        assertNotEquals(
            batchFingerprint(listOf(withImage)),
            batchFingerprint(listOf(moved)),
        )
    }

    /**
     * AUD-06 ③④：不渲染的字段不参与批量键（勾掉备注时的备注本身、从不进 PDF 的兜底文字），
     * 相同内容重跑命中同一把键——否则整批导出每次都要重排重画。
     */
    @Test
    fun unrenderedFieldsKeepTheBatchFingerprintStableAndRerunsMatch() {
        val hiddenNote = MistakePdfLayout.DEFAULT
        val visibleNote = MistakePdfLayout(includeNote = true)
        val base = readyState("entry-a", "第一题", userNote = "旧备注")
        val renamed = readyState("entry-a", "第一题", userNote = "新备注")
        val otherFallback = base.copy(detail = base.detail.copy(fallbackMarkdown = "另一份兜底文字"))

        assertEquals(
            batchFingerprint(listOf(base), hiddenNote),
            batchFingerprint(listOf(renamed), hiddenNote),
        )
        assertEquals(
            batchFingerprint(listOf(base), hiddenNote),
            batchFingerprint(listOf(otherFallback), hiddenNote),
        )
        // 正对照：同一份改动在 includeNote=true（会渲染备注）时必须换整批的键。
        assertNotEquals(
            batchFingerprint(listOf(base), visibleNote),
            batchFingerprint(listOf(renamed), visibleNote),
        )

        val image = sourceAsset("file:///clean-a.png")
        fun build(): List<MistakeDetailState> = listOf(
            readyState("entry-a", "第一题", userNote = "先检查定义域"),
            readyState("entry-b", "第二题", source = MistakeSourceSet.Present(listOf(image))),
        )

        assertEquals(
            batchFingerprint(build(), visibleNote),
            batchFingerprint(build(), visibleNote),
        )
    }

    private fun batchFingerprint(
        states: List<MistakeDetailState>,
        layout: MistakePdfLayout = MistakePdfLayout.DEFAULT,
    ): String = (
        MistakePdfBatchEligibility.check(states, layout) as MistakePdfBatchEligibilityResult.Eligible
        ).input.inputSha256

    private fun sourceAsset(uri: String): MistakeSourceAsset = MistakeSourceAsset(
        role = CLEAN_IMAGE_SOURCE_ROLE,
        sourceAssetId = "clean-a",
        contentSha256 = "a".repeat(64),
        mimeType = "image/png",
        byteSize = 1_024L,
        width = 100,
        height = 100,
        sourceType = "GENERATED_FIGURE",
        createdAtEpochMillis = 1L,
        location = MistakeSourceLocation.Available(uri),
    )

    private fun readyState(
        entryId: String,
        title: String,
        source: MistakeSourceSet = MistakeSourceSet.Missing,
        userNote: String? = null,
    ): MistakeDetailState.Ready {
        val block = ContentBlock.Paragraph("stem", "$title 的正式题面")
        return MistakeDetailState.Ready(
            detail = detail(entryId, title, source, userNote),
            questionDocument = CapturedQuestionDocument(
                document = QuestionDocument(
                    id = "document-$entryId",
                    title = title,
                    blocks = listOf(block),
                ),
                blockEvidence = listOf(
                    QuestionBlockEvidence(
                        blockId = block.id,
                        sourceAssetId = "source-$entryId",
                        sourceRegion = NormalizedSourceRegion(0.0, 0.0, 1.0, 1.0),
                        writingLayer = WritingLayer.PRINTED,
                        provenance = QuestionBlockProvenance.USER_CORRECTION,
                        reviewStatus = QuestionBlockReviewStatus.USER_CONFIRMED,
                    ),
                ),
            ),
        )
    }

    private fun detail(
        entryId: String,
        title: String,
        source: MistakeSourceSet = MistakeSourceSet.Missing,
        userNote: String? = null,
    ) = MistakeDetail(
        identity = MistakeDetailIdentity(
            errorBookEntryId = entryId,
            problemId = "problem-$entryId",
            problemRevisionId = "revision-$entryId",
            revisionNumber = 1,
            title = title,
            subject = "数学",
        ),
        fallbackMarkdown = "绝不能导出的备用文字",
        source = source,
        userNote = userNote,
    )
}
