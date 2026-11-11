package com.tingyun.smartmistakebook.core.export

import com.tingyun.smartmistakebook.core.domain.MistakeDetail
import com.tingyun.smartmistakebook.core.domain.MistakeDetailIdentity
import com.tingyun.smartmistakebook.core.domain.MistakeDetailState
import com.tingyun.smartmistakebook.core.domain.MistakeSourceAsset
import com.tingyun.smartmistakebook.core.domain.MistakeSourceLocation
import com.tingyun.smartmistakebook.core.domain.MistakeSourceSet
import com.tingyun.smartmistakebook.core.model.CapturedQuestionDocument
import com.tingyun.smartmistakebook.core.model.ContentBlock
import com.tingyun.smartmistakebook.core.model.FigureAxis
import com.tingyun.smartmistakebook.core.model.FigureSchema
import com.tingyun.smartmistakebook.core.model.MistakePdfLayout
import com.tingyun.smartmistakebook.core.model.NormalizedSourceRegion
import com.tingyun.smartmistakebook.core.model.QuestionBlockEvidence
import com.tingyun.smartmistakebook.core.model.QuestionBlockProvenance
import com.tingyun.smartmistakebook.core.model.QuestionBlockReviewStatus
import com.tingyun.smartmistakebook.core.model.QuestionDocument
import com.tingyun.smartmistakebook.core.model.StructuredChoice
import com.tingyun.smartmistakebook.core.model.WritingLayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MistakePdfEligibilityTest {
    @Test
    fun eligibleTextQuestionRetainsItsRevisionWhenFrozenForPaperReview() {
        val input = eligibleInput(readyState(blocks = listOf(ContentBlock.Paragraph("stem", "题目"))))

        val frozen = MistakePdfPaperEdit.fromInputs(listOf(input)).toDraft().frozenQuestions.single()

        assertEquals("revision-1", frozen.basis.revisionId)
        assertEquals("entry-1", frozen.entryId)
    }

    @Test
    fun frozenQuestionUsesTheExportedImageIdentityAndLeavesCameraEvidenceOut() {
        val state = readyState(
            blocks = listOf(ContentBlock.Paragraph("stem", "题目")),
            source = MistakeSourceSet.Present(listOf(
                sourceAsset("CAMERA", "original", "file:///original.png"),
                sourceAsset("CLEAN_IMAGE", "clean", "file:///clean.png"),
            )),
        )

        val frozen = MistakePdfPaperEdit.fromInputs(listOf(eligibleInput(state))).toDraft().frozenQuestions.single()

        assertEquals(listOf("clean"), frozen.sourceImages.map { it.assetId })
        assertEquals("clean", frozen.figures.single().assetId)
    }

    @Test
    fun readyConfirmedSupportedDocumentMapsWithoutFallbackMarkdown() {
        val state = readyState(
            blocks = listOf(
                ContentBlock.Paragraph("stem", "已知 **函数**：\$f(x)\$"),
                ContentBlock.Formula("formula", "f(x)=x^2", "f x 等于 x 平方"),
                ContentBlock.ChoiceGroup(
                    id = "choices",
                    promptMarkdown = "请选择",
                    choices = listOf(
                        StructuredChoice("a", "单调递增"),
                        StructuredChoice("b", "先减后增"),
                    ),
                    selectedChoiceId = "b",
                ),
            ),
        )

        val result = MistakePdfEligibility.check(state) as MistakePdfEligibilityResult.Eligible

        assertEquals("二次函数", result.input.title)
        assertEquals(3, result.input.blocks.size)
        assertEquals(
            "已知 函数：\$f(x)\$",
            (result.input.blocks[0] as MistakePdfBlock.Paragraph).text,
        )
        val choice = (result.input.blocks[2] as MistakePdfBlock.ChoiceGroup).choices[1]
        assertTrue(choice.selected)
        assertFalse(result.input.title.contains("绝不能导出的 fallback"))
    }

    @Test
    fun legacyAndCorruptStatesFailClosed() {
        val legacy = MistakeDetailState.Legacy(detail())
        val corrupt = MistakeDetailState.CorruptSnapshot(detail().identity)

        assertEquals(
            setOf(MistakePdfIneligibility.LEGACY_CONTENT),
            (MistakePdfEligibility.check(legacy) as MistakePdfEligibilityResult.Ineligible).reasons,
        )
        assertEquals(
            setOf(MistakePdfIneligibility.CORRUPT_CONTENT),
            (MistakePdfEligibility.check(corrupt) as MistakePdfEligibilityResult.Ineligible).reasons,
        )
    }

    @Test
    fun unconfirmedBlockFailsClosed() {
        val state = readyState(
            blocks = listOf(ContentBlock.Paragraph("stem", "题目")),
            reviewStatus = QuestionBlockReviewStatus.NEEDS_REVIEW,
        )

        val reasons = (MistakePdfEligibility.check(state) as MistakePdfEligibilityResult.Ineligible)
            .reasons

        assertTrue(MistakePdfIneligibility.UNCONFIRMED_OR_INVALID_DOCUMENT in reasons)
    }

    @Test
    fun confirmedFigureMapsToDeterministicPdfBlock() {
        val state = readyState(
            blocks = listOf(
                ContentBlock.Figure(
                    id = "figure",
                    alternativeText = "函数图像",
                    schema = FigureSchema.Cartesian(
                        xAxis = FigureAxis(-1.0, 1.0),
                        yAxis = FigureAxis(-1.0, 1.0),
                    ),
                ),
            ),
        )

        val result = MistakePdfEligibility.check(state) as MistakePdfEligibilityResult.Eligible
        val figure = result.input.blocks.single() as MistakePdfBlock.Figure

        assertEquals("函数图像", figure.alternativeText)
        assertTrue(figure.schema is FigureSchema.Cartesian)
    }

    @Test
    fun danglingSelectedChoiceFailsClosed() {
        val state = readyState(
            blocks = listOf(
                ContentBlock.ChoiceGroup(
                    id = "choices",
                    promptMarkdown = "请选择",
                    choices = listOf(StructuredChoice("a", "选项 A")),
                    selectedChoiceId = "missing",
                ),
            ),
        )

        val reasons = (MistakePdfEligibility.check(state) as MistakePdfEligibilityResult.Ineligible)
            .reasons

        assertTrue(MistakePdfIneligibility.INVALID_CHOICE_STATE in reasons)
    }

    /**
     * B6：A2 落地的题面重绘（`problem_revision_source_asset` role=CLEAN_IMAGE）必须
     * 自然进入导出取图——它和采集链的干净题面走同一条 `assets.role == CLEAN_IMAGE` 读口。
     */
    @Test
    fun cleanRedrawAssetIsExportedAsTheCleanImage() {
        val state = readyState(
            blocks = listOf(ContentBlock.Paragraph("stem", "题目")),
            source = MistakeSourceSet.Present(
                listOf(
                    sourceAsset(role = "CAMERA", sourceAssetId = "original", uri = "file:///original.jpg")
                        // 干扰项：完整性字段与 CLEAN_IMAGE 不同，导出必须带**干净图那条**记录。
                        .copy(contentSha256 = "c".repeat(64), byteSize = 2_048L),
                    sourceAsset(
                        role = "CLEAN_IMAGE",
                        sourceAssetId = "redraw",
                        uri = "file:///redraw.png",
                    ),
                ),
            ),
        )

        val input = (MistakePdfEligibility.check(state) as MistakePdfEligibilityResult.Eligible)
            .input

        assertEquals("file:///redraw.png", input.cleanImageLocalUri)
        // KD-55：URI 与完整性字段必须来自同一条记录（CLEAN_IMAGE，而不是排在前面的 CAMERA）。
        assertEquals("a".repeat(64), input.cleanImageSha256)
        assertEquals(1_024L, input.cleanImageByteSize)
    }

    /**
     * F7：旧库同一 revision 多张 CLEAN_IMAGE 时，导出必须用**最新**那张（与详情/重绘源
     * 的"当前干净题面"同一判据）；否则用户重绘成功后 PDF/DOCX 仍是旧图。
     */
    @Test
    fun theNewestCleanRedrawIsExportedWhenLegacyRowsAccumulated() {
        val older = sourceAsset(
            role = CLEAN_IMAGE_SOURCE_ROLE,
            sourceAssetId = "clean-old",
            uri = "file:///clean-old.png",
        ).copy(createdAtEpochMillis = 1_000L)
        val newer = sourceAsset(
            role = CLEAN_IMAGE_SOURCE_ROLE,
            sourceAssetId = "clean-new",
            uri = "file:///clean-new.png",
        ).copy(
            createdAtEpochMillis = 2_000L,
            contentSha256 = "b".repeat(64),
        )
        val state = readyState(
            blocks = listOf(ContentBlock.Paragraph("stem", "题目")),
            source = MistakeSourceSet.Present(listOf(older, newer)),
        )

        val input = (MistakePdfEligibility.check(state) as MistakePdfEligibilityResult.Eligible)
            .input

        assertEquals("file:///clean-new.png", input.cleanImageLocalUri)
        assertEquals("b".repeat(64), input.cleanImageSha256)
    }

    /** B6 回退分支：没有重绘图时按现状——结构化图块照常渲染，原图不导出。 */
    @Test
    fun withoutRedrawOnlyTheStructuredFigureIsExported() {
        val figureBlock = ContentBlock.Figure(
            id = "figure",
            alternativeText = "函数图像",
            schema = FigureSchema.Cartesian(
                xAxis = FigureAxis(-1.0, 1.0),
                yAxis = FigureAxis(-1.0, 1.0),
            ),
        )
        val state = readyState(
            blocks = listOf(figureBlock),
            source = MistakeSourceSet.Present(
                listOf(
                    sourceAsset(role = "CAMERA", sourceAssetId = "original", uri = "file:///original.jpg"),
                ),
            ),
        )

        val input = (MistakePdfEligibility.check(state) as MistakePdfEligibilityResult.Eligible)
            .input

        assertEquals(null, input.cleanImageLocalUri)
        val figure = input.blocks.single() as MistakePdfBlock.Figure
        assertEquals("函数图像", figure.alternativeText)
        assertTrue(figure.schema is FigureSchema.Cartesian)
    }

    /** includeNote 的数据来源：条目的 userNote 必须在入口映射进导出输入。 */
    @Test
    fun learnerNoteIsMappedIntoTheExportInput() {
        val state = readyState(
            blocks = listOf(ContentBlock.Paragraph("stem", "题目")),
            userNote = "**先看导数的符号。**",
        )

        val input = (MistakePdfEligibility.check(state) as MistakePdfEligibilityResult.Eligible)
            .input

        assertEquals("先看导数的符号。", input.userNote)
    }

    @Test
    fun visibleNotesAndImageBytesInvalidateTheCacheButHiddenNotesDoNot() {
        val first = readyState(listOf(ContentBlock.Paragraph("stem", "题目")), userNote = "旧备注")
        val second = first.copy(detail = first.detail.copy(userNote = "新备注"))
        fun fingerprint(state: MistakeDetailState.Ready, showNote: Boolean) =
            (MistakePdfEligibility.check(state, com.tingyun.smartmistakebook.core.model.MistakePdfLayout(
                includeNote = showNote)) as MistakePdfEligibilityResult.Eligible).input.inputSha256
        org.junit.Assert.assertNotEquals(fingerprint(first, true), fingerprint(second, true))
        assertEquals(fingerprint(first, false), fingerprint(second, false))
        val image = sourceAsset("CLEAN_IMAGE", "clean", "file:///image.png")
        val withImage = first.copy(detail = first.detail.copy(source = MistakeSourceSet.Present(listOf(image))))
        val changedImage = withImage.copy(detail = withImage.detail.copy(source = MistakeSourceSet.Present(
            listOf(image.copy(contentSha256 = "b".repeat(64))))))
        org.junit.Assert.assertNotEquals(fingerprint(withImage, false), fingerprint(changedImage, false))
    }

    /**
     * AUD-06 ②：净化图是逐字节画进 PDF 的，指纹必须跟着「是哪张图」走——同一个 URI 换了
     * 字节（重绘覆盖了同一个文件）与同一份字节换了 URI（资产行 / 存放位置变了）都是新产物。
     * 只比 URI 或只比字节各漏一半。
     */
    @Test
    fun cleanImageIdentityAndByteChangesRewriteTheFingerprint() {
        val blocks = listOf(ContentBlock.Paragraph("stem", "题目"))
        val asset = sourceAsset(CLEAN_IMAGE_SOURCE_ROLE, "clean-a", "file:///clean-a.png")
        val base = readyState(blocks, source = MistakeSourceSet.Present(listOf(asset)))
        val rebytes = readyState(
            blocks,
            source = MistakeSourceSet.Present(listOf(asset.copy(contentSha256 = "b".repeat(64)))),
        )
        val moved = readyState(
            blocks,
            source = MistakeSourceSet.Present(
                listOf(asset.copy(location = MistakeSourceLocation.Available("file:///clean-b.png"))),
            ),
        )

        assertEquals("file:///clean-a.png", eligibleInput(base).cleanImageLocalUri)
        assertNotEquals(fingerprintOf(base), fingerprintOf(rebytes))
        assertNotEquals(fingerprintOf(base), fingerprintOf(moved))
    }

    /**
     * AUD-06 ③（防误失效）：指纹只收会渲染的东西。默认版式不渲染备注，`fallbackMarkdown`
     * 从不进 PDF——它们变了不该换键，否则同一份产物会有两把键，每导一次都要重排重画，
     * 等于没有缓存。
     */
    @Test
    fun unrenderedFieldsDoNotInvalidateTheFingerprint() {
        val blocks = listOf(ContentBlock.Paragraph("stem", "题目"))
        val base = readyState(blocks, userNote = "旧备注")
        val renamed = base.copy(detail = base.detail.copy(userNote = "新备注"))
        val otherFallback = base.copy(detail = base.detail.copy(fallbackMarkdown = "另一份兜底文字"))

        assertFalse(MistakePdfLayout.DEFAULT.includeNote)
        assertEquals("新备注", eligibleInput(renamed).userNote)
        // 正对照：同一份改动在 includeNote=true（会渲染备注）时必须换键，否则下面的相等
        // 断言可能只是因为「这个字段根本没进任何地方」而白通过。
        assertNotEquals(
            fingerprintOf(base, MistakePdfLayout(includeNote = true)),
            fingerprintOf(renamed, MistakePdfLayout(includeNote = true)),
        )
        assertEquals(fingerprintOf(base), fingerprintOf(renamed))
        assertEquals(fingerprintOf(base), fingerprintOf(otherFallback))
    }

    /**
     * AUD-06 ④：相同内容重跑必须命中同一把键，否则预渲染产物永远匹配不上。这里带上两样
     * 新增输入（可见备注 + 净化图），覆盖的是新增字段自身的确定性——安全内联化与资产取值
     * 都不能引入随机或依赖遍历顺序。
     */
    @Test
    fun identicalContentRunsHitTheSameFingerprint() {
        val layout = MistakePdfLayout(includeNote = true)
        val asset = sourceAsset(CLEAN_IMAGE_SOURCE_ROLE, "clean-a", "file:///clean-a.png")
        fun build(): MistakeDetailState.Ready = readyState(
            listOf(ContentBlock.Paragraph("stem", "题目")),
            source = MistakeSourceSet.Present(listOf(asset)),
            userNote = "**先看导数的符号。**",
        )

        val first = build()
        val second = build()

        assertEquals("先看导数的符号。", eligibleInput(first, layout).userNote)
        assertEquals(fingerprintOf(first, layout), fingerprintOf(second, layout))
    }

    // ------------------------------------------- KD-75 C2：模型解析文本事实

    @Test
    fun `model parsed evidence sets the model text flag`() {
        val state = readyState(
            blocks = listOf(ContentBlock.Paragraph("stem", "模型解析出的题干")),
            provenance = QuestionBlockProvenance.MODEL_DOCUMENT_PARSE,
        )

        assertTrue(eligibleInput(state).hasModelParsedText)
    }

    @Test
    fun `pure user document keeps the model text flag false`() {
        val state = readyState(blocks = listOf(ContentBlock.Paragraph("stem", "用户自己敲的题干")))

        assertFalse(eligibleInput(state).hasModelParsedText)
    }

    /**
     * 边界（R1 §2.4）：用户只改了**部分**块时，未被改写的块仍是 `MODEL_DOCUMENT_PARSE`，
     * 文档必须打标。逐块全改后不再有该 provenance 属有意保守的反向边界（另由判据层用例钉死）。
     */
    @Test
    fun `partially corrected document still counts as model parsed text`() {
        val state = readyState(
            blocks = listOf(
                ContentBlock.Paragraph("kept", "模型原文"),
                ContentBlock.Paragraph("edited", "用户改写的文字"),
            ),
        )
        val mixed = state.copy(
            questionDocument = state.questionDocument.copy(
                blockEvidence = listOf(
                    state.questionDocument.blockEvidence[0].copy(
                        provenance = QuestionBlockProvenance.MODEL_DOCUMENT_PARSE,
                        producerVersion = "model-test-1",
                    ),
                    state.questionDocument.blockEvidence[1].copy(
                        provenance = QuestionBlockProvenance.USER_CORRECTION,
                    ),
                ),
            ),
        )

        assertTrue(eligibleInput(mixed).hasModelParsedText)
    }

    /**
     * 消灭的失败：将来有人把文档指纹改成"只对 blocks 取摘要"，模型文本题与纯用户题的缓存键
     * 相撞，互相复用对方产物（一个漏标、一个多标）。
     */
    @Test
    fun `provenance change rewrites the export cache key`() {
        val user = readyState(blocks = listOf(ContentBlock.Paragraph("stem", "同一段文字")))
        val model = user.copy(
            questionDocument = user.questionDocument.copy(
                blockEvidence = user.questionDocument.blockEvidence.map { evidence ->
                    evidence.copy(
                        provenance = QuestionBlockProvenance.MODEL_DOCUMENT_PARSE,
                        producerVersion = "model-test-1",
                    )
                },
            ),
        )

        assertNotEquals(fingerprintOf(user), fingerprintOf(model))
    }

    private fun eligibleInput(
        state: MistakeDetailState.Ready,
        layout: MistakePdfLayout = MistakePdfLayout.DEFAULT,
    ): MistakePdfExportInput =
        (MistakePdfEligibility.check(state, layout) as MistakePdfEligibilityResult.Eligible).input

    private fun fingerprintOf(
        state: MistakeDetailState.Ready,
        layout: MistakePdfLayout = MistakePdfLayout.DEFAULT,
    ): String = eligibleInput(state, layout).inputSha256

    private fun sourceAsset(
        role: String,
        sourceAssetId: String,
        uri: String,
    ): MistakeSourceAsset = MistakeSourceAsset(
        role = role,
        sourceAssetId = sourceAssetId,
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
        blocks: List<ContentBlock>,
        reviewStatus: QuestionBlockReviewStatus = QuestionBlockReviewStatus.USER_CONFIRMED,
        source: MistakeSourceSet = MistakeSourceSet.Missing,
        userNote: String? = null,
        provenance: QuestionBlockProvenance = QuestionBlockProvenance.USER_CORRECTION,
    ): MistakeDetailState.Ready = MistakeDetailState.Ready(
        detail = detail(source = source, userNote = userNote),
        questionDocument = CapturedQuestionDocument(
            document = QuestionDocument(
                id = "document-1",
                title = "二次函数",
                blocks = blocks,
            ),
            blockEvidence = blocks.map { block ->
                QuestionBlockEvidence(
                    blockId = block.id,
                    sourceAssetId = "source-1",
                    sourceRegion = NormalizedSourceRegion(0.0, 0.0, 1.0, 1.0),
                    writingLayer = WritingLayer.PRINTED,
                    provenance = provenance,
                    reviewStatus = reviewStatus,
                    // 非用户来源缺 producerVersion 会被 validateForCommit 拒绝（合法状态也未变）。
                    producerVersion = if (provenance == QuestionBlockProvenance.USER_CORRECTION ||
                        provenance == QuestionBlockProvenance.USER_TRANSCRIPTION
                    ) {
                        null
                    } else {
                        "model-test-1"
                    },
                )
            },
        ),
    )

    private fun detail(
        source: MistakeSourceSet = MistakeSourceSet.Missing,
        userNote: String? = null,
    ) = MistakeDetail(
        identity = MistakeDetailIdentity(
            errorBookEntryId = "entry-1",
            problemId = "problem-1",
            problemRevisionId = "revision-1",
            revisionNumber = 1,
            title = "二次函数",
            subject = "数学",
        ),
        fallbackMarkdown = "绝不能导出的 fallback",
        source = source,
        userNote = userNote,
    )
}
