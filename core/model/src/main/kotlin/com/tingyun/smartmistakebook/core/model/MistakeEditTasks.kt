package com.tingyun.smartmistakebook.core.model

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import java.text.Normalizer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EditBasis(val entryId: String, val revisionId: String, val draftSha256: String) {
    init {
        entryId.requireSafeModelText("Edit entry id", ModelTaskRequest.MAX_ID_CHARS, false)
        revisionId.requireSafeModelText("Edit revision id", ModelTaskRequest.MAX_ID_CHARS, false)
        require(draftSha256.matches(Regex("[a-f0-9]{64}"))) { "Edit basis needs an exact draft SHA-256" }
    }
}

fun normalizeEditInstruction(value: String): String = Normalizer.normalize(value.replace("\r\n", "\n").replace('\r', '\n'), Normalizer.Form.NFC).trim()

/** Length-prefixing prevents ambiguous identities when user text contains delimiters. */
fun editOperationKey(basis: EditBasis, targetBlockId: String, sourceSha256: String?, instruction: String): String {
    targetBlockId.requireSafeModelText("Edit target", ModelTaskRequest.MAX_ID_CHARS, false)
    require(sourceSha256 == null || sourceSha256.matches(Regex("[a-f0-9]{64}")))
    val normalized = normalizeEditInstruction(instruction)
    normalized.requireSafeModelText("Edit instruction", MistakeEditInput.MAX_INSTRUCTION_CHARS, true)
    val bytes = ByteArrayOutputStream()
    DataOutputStream(bytes).use { stream ->
        listOf("mistake-edit-v1", basis.entryId, basis.revisionId, basis.draftSha256, targetBlockId, sourceSha256.orEmpty(), normalized).forEach { part ->
            val utf8 = part.toByteArray(Charsets.UTF_8)
            stream.writeInt(utf8.size); stream.write(utf8)
        }
    }
    return MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()).joinToString("") { "%02x".format(it) }
}

@Serializable
data class StoredFigureReference(val blockId: String, val assetId: String, val contentSha256: String, val role: VisualRole) {
    init {
        blockId.requireSafeModelText("Stored figure block", ModelTaskRequest.MAX_ID_CHARS, false)
        assetId.requireSafeModelText("Stored figure asset", ModelTaskRequest.MAX_ID_CHARS, false)
        require(contentSha256.matches(Regex("[a-f0-9]{64}")))
        require(role != VisualRole.SOURCE_EVIDENCE) { "An adopted figure is not a source photograph" }
    }
}

@Serializable
@SerialName("mistake_edit")
data class MistakeEditInput(
    val basis: EditBasis,
    val draft: CapturedQuestionDocument,
    val targetBlockIds: Set<String>,
    val instruction: String,
    val sourceImages: List<ModelEgressAssetGrant> = emptyList(),
    val editTitle: Boolean = false,
    /** Frozen shared figure policy assembled from actual backend capabilities by the host. */
    val figureGenerationPromptBody: String? = null,
) : ModelTaskInput {
    override val kind: ModelTaskKind get() = ModelTaskKind.MISTAKE_EDIT
    override val subjectId: String get() = basis.revisionId
    override val isAgentConsentEligible: Boolean get() = true
    override val requestsImageBytes: Boolean get() = sourceImages.isNotEmpty()

    init {
        instruction.requireSafeModelText("Edit instruction", MAX_INSTRUCTION_CHARS, true)
        require(normalizeEditInstruction(instruction).isNotEmpty())
        require(basis.draftSha256 == CapturedQuestionDocumentFingerprint.of(draft)) { "Edit basis does not match the current draft" }
        val ids = draft.document.blocks.map { it.id }
        require(ids.isNotEmpty() && ids.distinct().size == ids.size)
        require(targetBlockIds.size <= 32 && (targetBlockIds.isNotEmpty() || editTitle))
        require(targetBlockIds.all { it in ids }) { "Edit targets must be host-allocated blocks" }
        require(sourceImages.size <= 8 && sourceImages.map { it.assetId }.distinct().size == sourceImages.size)
        require(sourceImages.all { it.selectedRegion == null }) { "Edit images must be canonical whole assets" }
        require(figureGenerationPromptBody == null || figureGenerationPromptBody.length in 1..FigureGenerationPrompt.MAX_BODY_CHARS)
    }

    companion object { const val MAX_INSTRUCTION_CHARS = 4_000 }
}

@Serializable
sealed interface MistakeEditChange {
    val blockId: String

    @Serializable @SerialName("replace_block")
    data class ReplaceBlock(val block: ContentBlock) : MistakeEditChange {
        override val blockId: String get() = block.id
        init { require(block !is ContentBlock.Figure && block !is ContentBlock.Unknown) { "Figures must use the shared generation proposal" } }
    }

    @Serializable @SerialName("replace_figure")
    data class ReplaceFigure(override val blockId: String, val proposal: FigureGenerationProposal) : MistakeEditChange
}

@Serializable
enum class MistakeEditDecision { SUGGEST_CHANGES, NO_CHANGE, NEED_SOURCE, UNAVAILABLE }

@Serializable
data class MistakeEditSuggestion(
    val summaryMarkdown: String,
    val changes: List<MistakeEditChange>,
    val title: String? = null,
    val decision: MistakeEditDecision = MistakeEditDecision.SUGGEST_CHANGES,
) {
    init {
        requireEditText(summaryMarkdown, 500)
        require(changes.size <= 32)
        require(if (decision == MistakeEditDecision.SUGGEST_CHANGES) changes.isNotEmpty() || title != null
            else changes.isEmpty() && title == null) { "Non-edit decisions cannot contain an adoptable patch" }
        require(changes.map { it.blockId }.distinct().size == changes.size)
        title?.let { requireEditText(it, 160) }
    }

    fun requireMatches(input: MistakeEditInput) {
        require(title == null || input.editTitle) { "Title was not selected for editing" }
        require(changes.all { it.blockId in input.targetBlockIds }) { "Model output targets an unselected block" }
        changes.forEach { change -> when (change) {
            is MistakeEditChange.ReplaceBlock -> when (val block = change.block) {
                is ContentBlock.Paragraph -> requireEditText(block.markdown, 20_000)
                is ContentBlock.Formula -> { requireEditText(block.latex, 8_000); requireEditText(block.alternativeText, 2_000) }
                is ContentBlock.ChoiceGroup -> {
                    val original = input.draft.document.blocks.single { it.id == block.id } as? ContentBlock.ChoiceGroup
                    require(original != null && original.choices.map { it.id } == block.choices.map { it.id }) { "Choice identities must remain unchanged" }
                    requireEditText(block.promptMarkdown, 8_000)
                    block.choices.forEach { requireEditText(it.markdown, 8_000) }
                    require(block.selectedChoiceId == original.selectedChoiceId && block.enabled == original.enabled)
                    require(original.choices.zip(block.choices).all { (before, after) ->
                        before.enabled == after.enabled && before.accessibilityLabel == after.accessibilityLabel
                    }) { "Choice interaction metadata is host-owned" }
                }
                else -> error("Unsupported replacement block")
            }
            is MistakeEditChange.ReplaceFigure -> {
                require(change.proposal.policyVersion == FigureGenerationPolicyVersion.VALUE)
                require(change.proposal.decision != FigureGenerationDecision.GENERATE ||
                    (change.proposal.method != null && change.proposal.purpose != null && change.proposal.fidelity != null)) {
                    "A generated figure needs an explicit method, purpose and fidelity"
                }
            }
        } }
    }
}

@Serializable @SerialName("mistake_edit_output")
data class MistakeEditOutput(val basis: EditBasis, val suggestion: MistakeEditSuggestion, val modelVersion: String) : ModelTaskOutput {
    init { modelVersion.requireSafeModelText("Edit model version", 200, false) }
}

private val UNSAFE_EDIT_TEXT = Regex("""(?i)(https?|file|content|javascript|data|mailto|tel|sms):|[a-z][a-z0-9+.-]*://|<\s*/?\s*(script|iframe|img|style|svg|object|embed|link|meta)\b|```|!\[|\[[^\]]+\]\([^)]+\)""")

fun requireEditText(text: String, maxChars: Int) {
    text.requireSafeModelText("Edit text", maxChars, true)
    require(!UNSAFE_EDIT_TEXT.containsMatchIn(text)) {
        "Edit text cannot embed a URI, image, active HTML or executable code"
    }
}
