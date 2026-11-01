package com.tingyun.smartmistakebook.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

const val DETAILED_SOLUTION_PROTOCOL_VERSION = "detailed-solution-v1"

@Serializable
data class SolutionBasis(val revisionId: String, val questionSha256: String) {
    init { require(revisionId.isNotBlank() && revisionId.length <= 256); require(questionSha256.matches(Regex("[a-f0-9]{64}"))) }
}

@Serializable enum class SolutionOrigin { EXISTING, MODEL }
@Serializable enum class SolutionVerification { UNVERIFIED, VERIFIED }
enum class DetailedSolutionPreparationStatus { QUEUED, RUNNING, READY, CANCELLED, FAILED, STALE }

fun detailedSolutionId(basis: SolutionBasis): String = "solution:" + figureSha256Hex(
    (DETAILED_SOLUTION_PROTOCOL_VERSION + "\n" + basis.revisionId.length + ":" + basis.revisionId + "\n" + basis.questionSha256).toByteArray(Charsets.UTF_8))

fun detailedSolutionFigureOperationId(solutionId: String, blockId: String): String =
    "$solutionId.figure.${figureSha256Hex(blockId.toByteArray(Charsets.UTF_8)).take(24)}"

fun detailedSolutionFigureBasis(request: ModelTaskRequest, output: DetailedSolutionOutput, blockId: String): String = figureSha256Hex(
    (ModelTaskFingerprint.of(request) + "\n" + figureSha256Hex(ModelTaskCodec.encodeOutput(output).toByteArray(Charsets.UTF_8)) + "\n" + blockId).toByteArray(Charsets.UTF_8))

@Serializable
data class DetailedSolutionDocument(
    val basis: SolutionBasis,
    val finalAnswerMarkdown: String,
    val steps: QuestionDocument,
    val figures: List<StoredFigureReference> = emptyList(),
    val origin: SolutionOrigin,
    val verification: SolutionVerification,
    val requiredFigureBlockIds: Set<String> = emptySet(),
    val protocolVersion: String = DETAILED_SOLUTION_PROTOCOL_VERSION,
) {
    init {
        require(protocolVersion == DETAILED_SOLUTION_PROTOCOL_VERSION)
        requireEditText(finalAnswerMarkdown, 8_000)
        require(finalAnswerMarkdown.isNotBlank() && steps.blocks.size in 1..96)
        steps.title?.let { requireEditText(it, 160) }
        require(steps.blocks.map { it.id }.distinct().size == steps.blocks.size)
        require(requiredFigureBlockIds.size <= 8 && requiredFigureBlockIds.all { id -> steps.blocks.any { it.id == id } })
        require(figures.size <= 8 && figures.map { it.blockId }.distinct().size == figures.size)
        require(figures.all { it.role == VisualRole.TUTOR_EXPLANATION && it.blockId in requiredFigureBlockIds })
        require(steps.blocks.sumOf { block -> when (block) {
            is ContentBlock.Paragraph -> block.markdown.also { requireEditText(it, 12_000) }.length
            is ContentBlock.Formula -> {
                requireEditText(block.latex, 8_000); requireEditText(block.alternativeText, 2_000)
                block.latex.length + block.alternativeText.length
            }
            else -> error("Detailed answers contain only explanation text, formulas and host-bound figure slots")
        } } <= 80_000)
    }
}

/** Figure integrity is a host fact; a DTO carrying a SHA never verifies a file by itself. */
fun canReuseSolution(document: DetailedSolutionDocument, target: SolutionBasis,
    figuresIntact: Boolean = document.figures.isEmpty(), revoked: Boolean = false): Boolean =
    !revoked && document.basis == target && document.protocolVersion == DETAILED_SOLUTION_PROTOCOL_VERSION &&
        document.finalAnswerMarkdown.isNotBlank() && document.steps.blocks.isNotEmpty() && figuresIntact &&
        document.figures.map { it.blockId }.toSet() == document.requiredFigureBlockIds

@Serializable
data class FrozenExportQuestion(
    val ordinal: Int,
    val entryId: String,
    val basis: SolutionBasis,
    val document: QuestionDocument,
    val figures: List<StoredFigureReference> = emptyList(),
    val sourceImages: List<ModelEgressAssetGrant> = emptyList(),
    val revisionNumber: Int = 1,
) {
    init {
        require(ordinal > 0 && entryId.isNotBlank() && revisionNumber > 0)
        require(document.blocks.size in 1..256 && document.blocks.map { it.id }.distinct().size == document.blocks.size)
        require(figures.size <= 32 && figures.map { it.blockId }.distinct().size == figures.size)
        require(figures.all { ref -> document.blocks.any { it.id == ref.blockId } })
        require(sourceImages.size <= 8 && sourceImages.map { it.assetId }.distinct().size == sourceImages.size)
        require(sourceImages.all { it.selectedRegion == null })
        require(basis.questionSha256 == solutionQuestionSha256(document, figures)) { "Frozen question fingerprint does not match its content" }
    }
}

/** Normalizes presentation identities, selected choices and interactivity out of semantic reuse. */
fun solutionQuestionSha256(document: QuestionDocument, figures: List<StoredFigureReference>): String {
    val ids = document.blocks.mapIndexed { index, block -> block.id to "block-${index + 1}" }.toMap()
    val canonical = document.copy(id = "question", blocks = document.blocks.map { block ->
        val id = ids.getValue(block.id)
        when (block) {
            is ContentBlock.Paragraph -> block.copy(id = id)
            is ContentBlock.Formula -> block.copy(id = id)
            is ContentBlock.Figure -> block.copy(id = id)
            is ContentBlock.ChoiceGroup -> block.copy(id = id, selectedChoiceId = null, enabled = true,
                choices = block.choices.mapIndexed { index, choice -> choice.copy(id = "choice-${index + 1}", enabled = true, accessibilityLabel = null) })
            is ContentBlock.Unknown -> error("Unsupported frozen question content")
        }
    })
    return figureSha256Hex(buildString {
        append(DetailedSolutionCodec.json.encodeToString(QuestionDocument.serializer(), canonical))
        append('\n')
        figures.sortedBy { ids.getValue(it.blockId) }.forEach { ref ->
            append(ids.getValue(ref.blockId)).append(':').append(ref.contentSha256).append(':').append(ref.role.name).append('\n')
        }
    }.toByteArray(Charsets.UTF_8))
}

@Serializable
data class FrozenAnswerManifest(
    val paperDraftId: String,
    val inputSha256: String,
    val layout: MistakePdfLayout,
    val questions: List<FrozenExportQuestion>,
    /** Full canonical paper-cache payload: enough to rebuild the exact question file after TTL cleanup. */
    val paperSnapshot: String,
) {
    init {
        require(paperDraftId.isNotBlank() && paperDraftId.length <= 256)
        require(inputSha256.matches(Regex("[a-f0-9]{64}")))
        require(layout.validate().isEmpty())
        require(questions.size in 1..100 && questions.map { it.ordinal } == (1..questions.size).toList())
        require(questions.map { it.entryId }.distinct().size == questions.size)
        require(paperSnapshot.length in 1..4_000_000)
    }
}

@Serializable @SerialName("mistake_solution")
data class DetailedSolutionInput(val question: FrozenExportQuestion, val figureGenerationPromptBody: String? = null) : ModelTaskInput {
    override val kind: ModelTaskKind get() = ModelTaskKind.MISTAKE_SOLUTION
    override val subjectId: String get() = question.basis.revisionId
    override val isAgentConsentEligible: Boolean get() = true
    override val requestsImageBytes: Boolean get() = question.sourceImages.isNotEmpty()
    init { require(figureGenerationPromptBody == null || figureGenerationPromptBody.length in 1..FigureGenerationPrompt.MAX_BODY_CHARS) }
}

@Serializable
data class SolutionFigureProposal(val blockId: String, val proposal: FigureGenerationProposal) {
    init { require(blockId.isNotBlank()); require(proposal.purpose == FigureGenerationPurpose.EXPLANATION || proposal.decision != FigureGenerationDecision.GENERATE) }
}

@Serializable @SerialName("detailed_solution_output")
data class DetailedSolutionOutput(val document: DetailedSolutionDocument, val figureProposals: List<SolutionFigureProposal>, val modelVersion: String) : ModelTaskOutput {
    init {
        require(modelVersion.isNotBlank() && modelVersion.length <= 200)
        require(figureProposals.size <= 8 && figureProposals.map { it.blockId }.toSet() == document.requiredFigureBlockIds)
        require(figureProposals.map { it.blockId }.distinct().size == figureProposals.size)
        require(document.origin == SolutionOrigin.MODEL && document.verification == SolutionVerification.UNVERIFIED && document.figures.isEmpty())
    }
}

object DetailedSolutionCodec {
    internal val json = Json { encodeDefaults = true; ignoreUnknownKeys = false; explicitNulls = true }
    fun encodeDocument(value: DetailedSolutionDocument): String = json.encodeToString(DetailedSolutionDocument.serializer(), value)
    fun decodeDocument(value: String): DetailedSolutionDocument { require(value.length <= 160_000); return json.decodeFromString(DetailedSolutionDocument.serializer(), value) }
    fun encodeManifest(value: FrozenAnswerManifest): String = json.encodeToString(FrozenAnswerManifest.serializer(), value)
    fun decodeManifest(value: String): FrozenAnswerManifest { require(value.length <= 8_000_000); return json.decodeFromString(FrozenAnswerManifest.serializer(), value) }
    fun documentSha256(value: DetailedSolutionDocument): String = figureSha256Hex(encodeDocument(value).toByteArray(Charsets.UTF_8))
    fun manifestSha256(value: FrozenAnswerManifest): String = figureSha256Hex(encodeManifest(value).toByteArray(Charsets.UTF_8))
    fun solutionsSha256(manifest: FrozenAnswerManifest, documents: List<DetailedSolutionDocument>): String {
        require(documents.size == manifest.questions.size && documents.zip(manifest.questions).all { (doc, question) -> doc.basis == question.basis })
        return figureSha256Hex(json.encodeToString(kotlinx.serialization.serializer<List<String>>(),
            listOf(manifestSha256(manifest)) + documents.map(::documentSha256)).toByteArray(Charsets.UTF_8))
    }
}

internal fun solutionLogicalOperationPayload(input: DetailedSolutionInput): String = DetailedSolutionCodec.json.encodeToString(
    kotlinx.serialization.builtins.ListSerializer(kotlinx.serialization.serializer<String>()),
    listOf(DETAILED_SOLUTION_PROTOCOL_VERSION, input.question.basis.revisionId, input.question.basis.questionSha256) + input.question.sourceImages.map { it.sha256 }.sorted(),
)
