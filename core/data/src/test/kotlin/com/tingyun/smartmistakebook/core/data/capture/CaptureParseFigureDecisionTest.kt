package com.tingyun.smartmistakebook.core.data.capture

import com.tingyun.smartmistakebook.core.database.CanonicalSourceAssetRecord
import com.tingyun.smartmistakebook.core.model.CapturedFigureReference
import com.tingyun.smartmistakebook.core.model.CapturedQuestionDocument
import com.tingyun.smartmistakebook.core.model.ContentBlock
import com.tingyun.smartmistakebook.core.model.FigureGenerationDecision
import com.tingyun.smartmistakebook.core.model.FigureGenerationMethod
import com.tingyun.smartmistakebook.core.model.FigureGenerationProposal
import com.tingyun.smartmistakebook.core.model.FigureGenerationPurpose
import com.tingyun.smartmistakebook.core.model.FigureGenerationPolicyVersion
import com.tingyun.smartmistakebook.core.model.FigureImageOperation
import com.tingyun.smartmistakebook.core.model.ImagePipelineClassifyInput
import com.tingyun.smartmistakebook.core.model.ImagePipelineProblemKind
import com.tingyun.smartmistakebook.core.model.ModelTaskRequest
import com.tingyun.smartmistakebook.core.model.NormalizedSourceRegion
import com.tingyun.smartmistakebook.core.model.QuestionBlockEvidence
import com.tingyun.smartmistakebook.core.model.QuestionBlockProvenance
import com.tingyun.smartmistakebook.core.model.QuestionBlockReviewStatus
import com.tingyun.smartmistakebook.core.model.QuestionDocument
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * WG-02（4E）：已有有效 proposal 直接执行；缺省且需判定才调 IMAGE_PIPELINE_CLASSIFY；
 * 换题/重裁/SHA 变化使旧 proposal 失效；TEXT_ONLY/WITH_FIGURE 只是既有 runner 的路由位，
 * 不决定生成方法（方法由 proposal 携带）。
 */
class CaptureParseFigureDecisionTest {

    private val sourceSha = "c".repeat(64)

    private fun source(
        assetId: String = "asset-1",
        sha: String = sourceSha,
    ): CanonicalSourceAssetRecord = CanonicalSourceAssetRecord(
        sourceAssetId = assetId,
        contentSha256 = sha,
        relativePath = "capture/$assetId.jpg",
        mimeType = "image/jpeg",
        byteSize = 1024,
        width = 1000,
        height = 1400,
        sourceType = "CAMERA",
        createdAtEpochMillis = 1L,
    )

    private fun reference(
        decision: FigureGenerationDecision = FigureGenerationDecision.GENERATE,
        method: FigureGenerationMethod? = FigureGenerationMethod.IMAGE_MODEL,
        assetId: String = "asset-1",
        sha: String = sourceSha,
    ): CapturedFigureReference {
        // 载荷严格按 decision/method 派生：GENERATE+IMAGE_MODEL 成对带 EDIT+prompt；
        // JS 只带 program；非 GENERATE 不带任何载荷（否则引用构造层就先拒了）。
        val executableImageModel = decision == FigureGenerationDecision.GENERATE &&
            method == FigureGenerationMethod.IMAGE_MODEL
        return CapturedFigureReference(
            proposal = FigureGenerationProposal(
                policyVersion = FigureGenerationPolicyVersion.VALUE,
                decision = decision,
                purpose = FigureGenerationPurpose.SOURCE_TRANSCRIPTION,
                method = method,
                reason = "转写判定",
                imageOperation = if (executableImageModel) FigureImageOperation.EDIT else null,
                imagePrompt = if (executableImageModel) "重绘该区域" else null,
                program = if (method == FigureGenerationMethod.JS) "renderScene({});" else null,
            ),
            sourceAssetId = assetId,
            sourcePageIndex = 0,
            sourceRegion = NormalizedSourceRegion(0.0, 0.0, 1.0, 1.0),
            sourceImageSha256 = sha,
            basisRevisionNumber = 2,
        )
    }

    private fun document(
        references: List<CapturedFigureReference>,
    ): CapturedQuestionDocument = CapturedQuestionDocument(
        document = QuestionDocument(
            id = "document-1",
            blocks = listOf(ContentBlock.Paragraph(id = "stem", markdown = "题干")),
        ),
        blockEvidence = listOf(
            QuestionBlockEvidence(
                blockId = "stem",
                sourceAssetId = "asset-1",
                sourceRegion = NormalizedSourceRegion(0.0, 0.0, 1.0, 1.0),
                provenance = QuestionBlockProvenance.MODEL_DOCUMENT_PARSE,
                reviewStatus = QuestionBlockReviewStatus.NEEDS_REVIEW,
                producerVersion = "model-1",
            ),
        ),
        figureReferences = references,
    )

    @Test
    fun `valid image model proposal executes without judgment`() {
        val decision = captureParseFigureDecision(
            document = document(listOf(reference())),
            source = source(),
        )
        assertEquals(reference(), (decision as CaptureParseFigureDecision.ExecuteProposal).reference)
    }

    @Test
    fun `mixed generate and no figure still executes the generate proposal`() {
        val decision = captureParseFigureDecision(
            document = document(
                listOf(
                    reference(decision = FigureGenerationDecision.NO_FIGURE, method = null),
                    reference(),
                ),
            ),
            source = source(),
        )
        assertEquals(
            CaptureParseFigureDecision.ExecuteProposal::class,
            decision::class,
        )
    }

    @Test
    fun `no figure decision ends without generation`() {
        val decision = captureParseFigureDecision(
            document = document(
                listOf(reference(decision = FigureGenerationDecision.NO_FIGURE, method = null)),
            ),
            source = source(),
        )
        assertEquals(CaptureParseFigureDecision.NoFigureNeeded, decision)
    }

    @Test
    fun `need source decision prepares the source first`() {
        val decision = captureParseFigureDecision(
            document = document(
                listOf(reference(decision = FigureGenerationDecision.NEED_SOURCE, method = null)),
            ),
            source = source(),
        )
        assertEquals(CaptureParseFigureDecision.NeedsSourcePreparation, decision)
    }

    @Test
    fun `js proposal is recorded but not executed in this build`() {
        val decision = captureParseFigureDecision(
            document = document(
                listOf(reference(method = FigureGenerationMethod.JS)),
            ),
            source = source(),
        )
        assertEquals(CaptureParseFigureDecision.MethodUnavailable, decision)
    }

    @Test
    fun `source sha change invalidates the old proposal`() {
        val decision = captureParseFigureDecision(
            document = document(listOf(reference(sha = "d".repeat(64)))),
            source = source(),
        )
        assertEquals(CaptureParseFigureDecision.NeedsJudgment, decision)
    }

    @Test
    fun `question change invalidates the old proposal`() {
        val decision = captureParseFigureDecision(
            document = document(listOf(reference(assetId = "asset-other"))),
            source = source(),
        )
        assertEquals(CaptureParseFigureDecision.NeedsJudgment, decision)
    }

    @Test
    fun `missing references fall back to judgment`() {
        assertEquals(
            CaptureParseFigureDecision.NeedsJudgment,
            captureParseFigureDecision(document = null, source = source()),
        )
        assertEquals(
            CaptureParseFigureDecision.NeedsJudgment,
            captureParseFigureDecision(document = document(emptyList()), source = source()),
        )
    }

    @Test
    fun `existing proposal does not redispatch classification from transcription to commit`() =
        runBlocking {
            var fallbackDispatches = 0
            val committed = document(listOf(reference()))
            val kind = resolveCleanRedrawClassifyKind(
                request = classifyRequest(),
                readCommittedDocument = { committed },
                needsJudgment = {
                    fallbackDispatches += 1
                    ImagePipelineProblemKind.WITH_FIGURE
                },
            )

            assertEquals(ImagePipelineProblemKind.WITH_FIGURE, kind)
            assertEquals(0, fallbackDispatches)
        }

    @Test
    fun `no figure proposal short circuits classification without generation`() = runBlocking {
        var fallbackDispatches = 0
        val committed = document(
            listOf(reference(decision = FigureGenerationDecision.NO_FIGURE, method = null)),
        )
        val kind = resolveCleanRedrawClassifyKind(
            request = classifyRequest(),
            readCommittedDocument = { committed },
            needsJudgment = {
                fallbackDispatches += 1
                ImagePipelineProblemKind.TEXT_ONLY
            },
        )

        assertEquals(ImagePipelineProblemKind.TEXT_ONLY, kind)
        assertEquals(0, fallbackDispatches)
    }

    @Test
    fun `sha change or unbound proposal falls back to a real classification`() = runBlocking {
        var fallbackDispatches = 0
        val committed = document(listOf(reference(sha = "d".repeat(64))))
        val kind = resolveCleanRedrawClassifyKind(
            request = classifyRequest(),
            readCommittedDocument = { committed },
            needsJudgment = {
                fallbackDispatches += 1
                ImagePipelineProblemKind.WITH_FIGURE
            },
        )

        assertEquals(ImagePipelineProblemKind.WITH_FIGURE, kind)
        assertEquals(1, fallbackDispatches)
    }

    @Test
    fun `classify request without draft subject falls back to real classification`() = runBlocking {
        var fallbackDispatches = 0
        val kind = resolveCleanRedrawClassifyKind(
            request = ModelTaskRequest(
                requestId = "save-decision:rev-1",
                input = ImagePipelineClassifyInput(
                    sourceAssetId = "asset-1",
                    imageWidth = 1000,
                    imageHeight = 1400,
                    sourceImageSha256 = sourceSha,
                ),
                occurredAtEpochMillis = 1L,
            ),
            readCommittedDocument = { document(listOf(reference())) },
            needsJudgment = {
                fallbackDispatches += 1
                ImagePipelineProblemKind.TEXT_ONLY
            },
        )

        assertEquals(ImagePipelineProblemKind.TEXT_ONLY, kind)
        assertEquals(1, fallbackDispatches)
    }

    @Test
    fun `unreadable committed document falls back to real classification`() = runBlocking {
        var fallbackDispatches = 0
        val kind = resolveCleanRedrawClassifyKind(
            request = classifyRequest(),
            readCommittedDocument = { throw IllegalStateException("draft row unavailable") },
            needsJudgment = {
                fallbackDispatches += 1
                ImagePipelineProblemKind.WITH_FIGURE
            },
        )

        assertEquals(ImagePipelineProblemKind.WITH_FIGURE, kind)
        assertEquals(1, fallbackDispatches)
    }

    private fun classifyRequest(): ModelTaskRequest = ModelTaskRequest(
        requestId = "save-decision:rev-1",
        input = ImagePipelineClassifyInput(
            sourceAssetId = "asset-1",
            imageWidth = 1000,
            imageHeight = 1400,
            subjectIdOverride = "draft-1",
            sourceImageSha256 = sourceSha,
        ),
        occurredAtEpochMillis = 1L,
    )
}
