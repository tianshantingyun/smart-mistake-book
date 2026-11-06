package com.tingyun.smartmistakebook.core.export

import com.tingyun.smartmistakebook.core.model.*

data class DetailedAnswerExportInput(
    val manifest: FrozenAnswerManifest,
    val documents: List<DetailedSolutionDocument>,
    val format: MistakeExportFormat,
    val layout: MistakePdfLayout = manifest.layout,
    val figureResolver: (StoredFigureReference) -> MistakePdfExportImage? = { null },
) {
    init {
        require(layout.validate().isEmpty())
        require(documents.size == manifest.questions.size)
        require(documents.zip(manifest.questions).all { (document, question) ->
            canReuseSolution(document, question.basis, figuresIntact = true)
        }) { "Every frozen question needs its own complete exact-basis answer" }
    }

    fun toNativeInput(): MistakePdfExportInput {
        val blocks = buildList {
            documents.zip(manifest.questions).forEach { (document, question) ->
                val prefix = "answer-${question.ordinal}"
                add(MistakePdfBlock.SectionHeading("$prefix-heading", "第 ${question.ordinal} 题 · 第 ${question.revisionNumber} 版"))
                add(MistakePdfBlock.Paragraph("$prefix-source", when (document.origin) {
                    SolutionOrigin.EXISTING -> "来源：已有解答"
                    SolutionOrigin.MODEL -> "来源：智能体生成"
                } + if (document.verification == SolutionVerification.VERIFIED) " · 已核对" else " · 未经独立核对"))
                val figures = document.figures.associateBy { it.blockId }
                document.steps.blocks.forEach { block ->
                    val id = "$prefix-${block.id}"
                    val ref = figures[block.id]
                    if (ref != null) {
                        val image = requireNotNull(figureResolver(ref)) { "An answer figure is unavailable" }
                        require(image.role == VisualRole.TUTOR_EXPLANATION.name && image.contentSha256 == ref.contentSha256 && image.byteSize != null)
                        require(DocxImageSource.read(image.localUri, image.contentSha256, image.byteSize) != null) { "An answer figure failed its integrity check" }
                        add(MistakePdfBlock.Image(id, image.localUri, "解题配图", image.contentSha256, image.byteSize, image.role))
                    } else when (block) {
                        is ContentBlock.Paragraph -> add(MistakePdfBlock.Paragraph(id, block.markdown))
                        is ContentBlock.Formula -> add(MistakePdfBlock.Formula(id, block.latex, block.alternativeText, block.display))
                        else -> error("Unsupported detailed answer block")
                    }
                }
                add(MistakePdfBlock.SectionHeading("$prefix-final-heading", "最终答案"))
                add(MistakePdfBlock.Paragraph("$prefix-final", document.finalAnswerMarkdown))
            }
        }
        val manifestSha = DetailedSolutionCodec.manifestSha256(manifest)
        val solutionsSha = DetailedSolutionCodec.solutionsSha256(manifest, documents)
        val answerLayout = layout.copy(blockOrder = emptyList(), includeAnswer = false, includeSolution = false, includeNote = false,
            templateId = if (layout.templateId == MistakePdfLayout.TEMPLATE_PRACTICE_SHEET) MistakePdfLayout.TEMPLATE_COMPACT else layout.templateId)
        val fingerprint = exportFingerprint("detailed-answer-export-v1", MistakeExportArtifactRole.DETAILED_ANSWERS.name,
            manifestSha, solutionsSha, format.token, layout.canonicalForm(), answerLayout.canonicalForm())
        return MistakePdfExportInput("answers-$manifestSha", "answers-$manifestSha", manifestSha, 1, "详细答案", "",
            "共 ${documents.size} 题 · 与题目卷题号一致", null, blocks, solutionsSha, fingerprint,
            maxPages = MistakePdfExportLimits.MAX_BATCH_PAGES, maxRenderedLines = MistakePdfExportLimits.MAX_BATCH_RENDERED_LINES,
            maxPdfBytes = MistakePdfExportLimits.MAX_BATCH_PDF_BYTES, layout = answerLayout,
            hasModelParsedText = documents.any { it.origin == com.tingyun.smartmistakebook.core.model.SolutionOrigin.MODEL },
            artifactRole = MistakeExportArtifactRole.DETAILED_ANSWERS)
    }
}
