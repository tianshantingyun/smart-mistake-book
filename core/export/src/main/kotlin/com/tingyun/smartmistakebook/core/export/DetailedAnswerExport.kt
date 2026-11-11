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
            canReuseSolution(document, question.basis, figuresIntact = true, requireTeachingFigure = true)
        }) { "Every frozen question needs its own complete exact-basis answer" }
    }

    fun toNativeInput(): MistakePdfExportInput {
        val blocks = buildList {
            documents.zip(manifest.questions).forEach { (document, question) ->
                val prefix = "answer-${question.ordinal}"
                add(MistakePdfBlock.SectionHeading("$prefix-heading", "第 ${question.ordinal} 题"))
                val figures = document.figures.associateBy { it.blockId }
                document.steps.blocks.forEach { block ->
                    val id = "$prefix-${block.id}"
                    val ref = figures[block.id]
                    if (ref != null) {
                        val image = requireNotNull(figureResolver(ref)) { "An answer figure is unavailable" }
                        require(image.role == VisualRole.TUTOR_EXPLANATION.name && image.contentSha256 == ref.contentSha256 && image.byteSize != null)
                        require(DocxImageSource.read(image.localUri, image.contentSha256, image.byteSize) != null) { "An answer figure failed its integrity check" }
                        val caption = (block as? ContentBlock.Paragraph)?.markdown?.takeIf(String::isNotBlank) ?: "解题配图"
                        add(MistakePdfBlock.Image(id, image.localUri, "", image.contentSha256, image.byteSize, image.role))
                        add(MistakePdfBlock.Paragraph("$id-caption", caption))
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
        val fingerprint = exportFingerprint("detailed-answer-export-v7-concise-quarter-area", MistakeExportArtifactRole.DETAILED_ANSWERS.name,
            manifestSha, solutionsSha, format.token, layout.canonicalForm(), answerLayout.canonicalForm())
        return MistakePdfExportInput("answers-$manifestSha", "answers-$manifestSha", manifestSha, 1, "详细答案", "",
            "", null, blocks, solutionsSha, fingerprint,
            maxPages = MistakePdfExportLimits.MAX_BATCH_PAGES, maxRenderedLines = MistakePdfExportLimits.MAX_BATCH_RENDERED_LINES,
            maxPdfBytes = MistakePdfExportLimits.MAX_BATCH_PDF_BYTES, layout = answerLayout,
            hasModelParsedText = documents.any { it.origin == com.tingyun.smartmistakebook.core.model.SolutionOrigin.MODEL },
            artifactRole = MistakeExportArtifactRole.DETAILED_ANSWERS)
    }
}

/** Halving both axes gives one quarter of the previously bounded illustration area. */
internal fun detailedAnswerImageWidth(widthPx: Int, heightPx: Int, columnWidth: Float,
    contentHeight: Float, imageScale: Float): Float = minOf(
    columnWidth * minOf(imageScale, .72f),
    contentHeight * .32f * widthPx / heightPx,
) * .5f
