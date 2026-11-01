package com.tingyun.smartmistakebook.core.model

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Versioned question AST plus the evidence needed to decide whether each block is safe to trust.
 * Markdown is only a leaf payload inside [QuestionDocument], never the authoritative whole record.
 */
@Serializable
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
data class CapturedQuestionDocument(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val document: QuestionDocument,
    val blockEvidence: List<QuestionBlockEvidence>,
    /**
     * WG-02（4E）：转写期图形的**类型化引用**。CAPTURE_PARSE 需要图时返回同型 proposal
     * （[FigureGenerationProposal]），图形通过这里进入题目文档——不把源码塞进正文、不写
     * 图片 URI；正文里只有占位说明（图示文字）。
     *
     * `@EncodeDefault(NEVER)`：空引用**不落键**。这是指纹稳定性的硬要求——
     * `RoomMistakeDetailRepository`/`RoomMistakeOrganizationRepository` 读回已提交文档时按
     * decode→重算→比对存库 `contentFingerprint` 判损坏，多一个空键就会把升级前的全部旧题
     * 判成 CorruptSnapshot（与 `ModelTasks.kt` 的 `bf8be888` 教训同一条纪律）。
     */
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER)
    val figureReferences: List<CapturedFigureReference> = emptyList(),
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

/**
 * 转写期一条图形的类型化引用（WG-02）：同型 proposal + 请求侧事实的源绑定。
 *
 * 绑定字段（[sourceAssetId]/[sourcePageIndex]/[sourceRegion]/[sourceImageSha256]/
 * [basisRevisionNumber]）由 parser 从 `CaptureParseInput` 填入，**不取信模型回填**——
 * 换题（assetId 变）、重裁（裁切产生新 asset）、源 SHA 变化都体现为绑定不匹配，
 * 复用判定（core:data 的净化作业入口）据此让旧 proposal 失效，而不是重新执行它。
 */
@Serializable
data class CapturedFigureReference(
    val proposal: FigureGenerationProposal,
    val sourceAssetId: String,
    val sourcePageIndex: Int,
    val sourceRegion: NormalizedSourceRegion,
    val sourceImageSha256: String,
    val basisRevisionNumber: Int,
) {
    init {
        require(sourceAssetId.isNotBlank() && sourceAssetId.length <= ModelTaskRequest.MAX_ID_CHARS) {
            "A captured figure reference needs its source asset id"
        }
        require(sourcePageIndex >= 0) {
            "A captured figure reference page index must not be negative"
        }
        require(sourceRegion.isValidModelRegion()) {
            "A captured figure reference region is invalid"
        }
        require(sourceImageSha256.isValidSourceImageSha256()) {
            "A captured figure reference sha must be a lowercase SHA-256 value"
        }
        require(basisRevisionNumber > 0) {
            "A captured figure reference basis revision must be positive"
        }
        require(validateCapturedFigureProposalShape(proposal) == null) {
            "A captured figure reference proposal does not match the transcription figure contract"
        }
    }

    companion object {
        /**
         * 一份题目文档携带的图形引用上限。取 8：转写引用与 `figure_request` 块一一对应
         * （一块一引用），而文档快照有 128k 字符硬预算——不设上限时，一个退化输出
         * （几十条各带图像载荷的引用）会把越界异常推迟到保存/落库路径深处才炸。
         */
        const val MAX_FIGURE_REFERENCES = 8
    }
}

/** 转写期同型 proposal 的形状判据（单一来源：core:data 的解析期与复用期共用，不各写一套）。 */
enum class CapturedFigureProposalShapeIssue {
    POLICY_VERSION_MISMATCH,
    PURPOSE_NOT_TRANSCRIPTION,
    GENERATE_WITHOUT_METHOD,
    METHOD_PAYLOAD_MISMATCH,
    PROGRAM_BLANK,
    PROGRAM_OVER_BUDGET,
    IMAGE_PROMPT_BLANK,
    IMAGE_PROMPT_OVER_BUDGET,
    DECISION_WITH_PAYLOAD,
}

/**
 * 同型 proposal 的**请求无关**形状校验（WG-02）。
 *
 * 它消灭的具体失败：模型侧 proposal 第一次进入系统（转写）时，若只靠
 * `FigureGenerationPolicy.validate`（需要完整宿主请求），要么构造不出请求而放过坏形状、
 * 要么各调用点各写一份判据。这里把不依赖宿主请求的规则收成一处：策略版本、宿主冻结的
 * 用途（题面转写）、GENERATE 必须显式给 method、方法与载荷严格正交、载荷预算。
 * 能力/预算/预留那半边仍由执行期的 `FigureGenerationPolicy` + coordinator 门守——
 * **形状合法不等于可执行**（本构建 JS 只记录不执行）。
 */
fun validateCapturedFigureProposalShape(
    proposal: FigureGenerationProposal,
): CapturedFigureProposalShapeIssue? {
    if (proposal.policyVersion != FigureGenerationPolicyVersion.VALUE) {
        return CapturedFigureProposalShapeIssue.POLICY_VERSION_MISMATCH
    }
    if (proposal.purpose != FigureGenerationPurpose.SOURCE_TRANSCRIPTION) {
        return CapturedFigureProposalShapeIssue.PURPOSE_NOT_TRANSCRIPTION
    }
    return when (proposal.decision) {
        FigureGenerationDecision.GENERATE -> {
            val method = proposal.method
                ?: return CapturedFigureProposalShapeIssue.GENERATE_WITHOUT_METHOD
            when (method) {
                FigureGenerationMethod.JS -> {
                    if (proposal.imageOperation != null || proposal.imagePrompt != null) {
                        return CapturedFigureProposalShapeIssue.METHOD_PAYLOAD_MISMATCH
                    }
                    val program = proposal.program
                        ?: return CapturedFigureProposalShapeIssue.METHOD_PAYLOAD_MISMATCH
                    if (program.isBlank()) {
                        return CapturedFigureProposalShapeIssue.PROGRAM_BLANK
                    }
                    if (figureUsageUtf8Bytes(program) > FigureGenerationPolicy.MAX_PROGRAM_UTF8_BYTES) {
                        return CapturedFigureProposalShapeIssue.PROGRAM_OVER_BUDGET
                    }
                    null
                }

                FigureGenerationMethod.IMAGE_MODEL -> {
                    if (proposal.program != null) {
                        return CapturedFigureProposalShapeIssue.METHOD_PAYLOAD_MISMATCH
                    }
                    if (proposal.imageOperation == null || proposal.imagePrompt == null) {
                        return CapturedFigureProposalShapeIssue.METHOD_PAYLOAD_MISMATCH
                    }
                    if (proposal.imagePrompt.isNullOrBlank()) {
                        return CapturedFigureProposalShapeIssue.IMAGE_PROMPT_BLANK
                    }
                    if (
                        figureUsageUtf8Bytes(proposal.imagePrompt) >
                        FigureGenerationPolicy.MAX_IMAGE_PROMPT_UTF8_BYTES
                    ) {
                        return CapturedFigureProposalShapeIssue.IMAGE_PROMPT_OVER_BUDGET
                    }
                    null
                }
            }
        }

        FigureGenerationDecision.NO_FIGURE,
        FigureGenerationDecision.NEED_SOURCE,
        FigureGenerationDecision.UNAVAILABLE,
        -> {
            if (
                proposal.method != null ||
                proposal.program != null ||
                proposal.imageOperation != null ||
                proposal.imagePrompt != null
            ) {
                CapturedFigureProposalShapeIssue.DECISION_WITH_PAYLOAD
            } else {
                null
            }
        }
    }
}

@Serializable
data class QuestionBlockEvidence(
    val blockId: String,
    val sourceAssetId: String,
    val sourceRegion: NormalizedSourceRegion? = null,
    val writingLayer: WritingLayer = WritingLayer.UNKNOWN,
    val provenance: QuestionBlockProvenance,
    val confidence: Double? = null,
    val reviewStatus: QuestionBlockReviewStatus,
    val producerVersion: String? = null,
)

@Serializable
data class NormalizedSourceRegion(
    val left: Double,
    val top: Double,
    val right: Double,
    val bottom: Double,
)

@Serializable
enum class WritingLayer {
    PRINTED,
    HANDWRITTEN,
    MIXED,
    DIAGRAM,
    UNKNOWN,
}

@Serializable
enum class QuestionBlockProvenance {
    USER_TRANSCRIPTION,
    USER_CORRECTION,
    LOCAL_OCR,
    OPTIONAL_REMOTE_OCR,
    MODEL_DOCUMENT_PARSE,
    DETERMINISTIC_RULE,
    IMPORTED_STRUCTURE,
}

@Serializable
enum class QuestionBlockReviewStatus {
    CANDIDATE,
    NEEDS_REVIEW,
    /** Accepted by deterministic local commit policy; this is not a student confirmation. */
    LOCAL_POLICY_ACCEPTED,
    USER_CONFIRMED,
}

enum class CapturedQuestionIssueCode {
    UNSUPPORTED_SCHEMA_VERSION,
    INVALID_STRUCTURED_CONTENT,
    EVIDENCE_DOES_NOT_MATCH_BLOCKS,
    BLANK_SOURCE_ASSET_ID,
    INVALID_SOURCE_REGION,
    MISSING_SOURCE_REGION,
    UNRESOLVED_WRITING_LAYER,
    INVALID_CONFIDENCE,
    MISSING_PRODUCER_VERSION,
    UNRESOLVED_BLOCK,
    EMPTY_QUESTION,
    /** WG-02：图形引用超出文档预算（形状/绑定校验在 [CapturedFigureReference] 构造层）。 */
    FIGURE_REFERENCE_BUDGET_EXCEEDED,
}

data class CapturedQuestionIssue(
    val code: CapturedQuestionIssueCode,
    val blockId: String? = null,
)

object CapturedQuestionDocumentValidator {
    fun validateDraft(value: CapturedQuestionDocument): List<CapturedQuestionIssue> = buildList {
        if (value.schemaVersion != CapturedQuestionDocument.CURRENT_SCHEMA_VERSION) {
            add(CapturedQuestionIssue(CapturedQuestionIssueCode.UNSUPPORTED_SCHEMA_VERSION))
        }
        val sanitized = StructuredContentSanitizer.sanitize(value.document)
        if (sanitized.issues.isNotEmpty() || sanitized.document != value.document) {
            add(CapturedQuestionIssue(CapturedQuestionIssueCode.INVALID_STRUCTURED_CONTENT))
        }

        val blockIds = value.document.blocks.map(ContentBlock::id)
        val evidenceIds = value.blockEvidence.map(QuestionBlockEvidence::blockId)
        if (blockIds.toSet().size != blockIds.size || evidenceIds != blockIds) {
            add(CapturedQuestionIssue(CapturedQuestionIssueCode.EVIDENCE_DOES_NOT_MATCH_BLOCKS))
        }

        // WG-02：图形引用只查预算（形状与源绑定在 CapturedFigureReference 构造层强制）；
        // 提交门（validateForCommit）不因引用拒绝——保存后引用仍在是验收项。
        if (value.figureReferences.size > CapturedFigureReference.MAX_FIGURE_REFERENCES) {
            add(CapturedQuestionIssue(CapturedQuestionIssueCode.FIGURE_REFERENCE_BUDGET_EXCEEDED))
        }

        value.blockEvidence.forEach { evidence ->
            // A8（用户转写合法化）：**用户自己敲进去的**题面没有源图，`source_asset_id` 空是
            // 诚实的形态（不是"丢了一张图"）。**只按 provenance 放行**——OCR/模型/导入结构
            // 的块仍然必须指向真实的源资产；用户**改正**（USER_CORRECTION）也不放行：
            // 它改的是别人产出的块，那块本该带源图。
            if (evidence.sourceAssetId.isBlank() && evidence.provenance !=
                QuestionBlockProvenance.USER_TRANSCRIPTION
            ) {
                add(
                    CapturedQuestionIssue(
                        CapturedQuestionIssueCode.BLANK_SOURCE_ASSET_ID,
                        evidence.blockId,
                    ),
                )
            }
            evidence.sourceRegion?.let { region ->
                if (!region.isValid()) {
                    add(
                        CapturedQuestionIssue(
                            CapturedQuestionIssueCode.INVALID_SOURCE_REGION,
                            evidence.blockId,
                        ),
                    )
                }
            }
            evidence.confidence?.let { confidence ->
                if (!confidence.isFinite() || confidence !in 0.0..1.0) {
                    add(
                        CapturedQuestionIssue(
                            CapturedQuestionIssueCode.INVALID_CONFIDENCE,
                            evidence.blockId,
                        ),
                    )
                }
            }
            if (
                evidence.provenance !in USER_PROVENANCE &&
                evidence.producerVersion.isNullOrBlank()
            ) {
                add(
                    CapturedQuestionIssue(
                        CapturedQuestionIssueCode.MISSING_PRODUCER_VERSION,
                        evidence.blockId,
                    ),
                )
            }
        }
    }.distinct()

    fun validateForCommit(value: CapturedQuestionDocument): List<CapturedQuestionIssue> = buildList {
        addAll(validateDraft(value))
        value.blockEvidence
            .filter { it.reviewStatus !in COMMIT_ACCEPTED_STATUSES }
            .forEach {
                add(CapturedQuestionIssue(CapturedQuestionIssueCode.UNRESOLVED_BLOCK, it.blockId))
            }
        value.blockEvidence
            // A8（用户转写合法化）：手敲的块没有"在照片里的位置"，缺区域是诚实的形态。
            // **只按 provenance 放行**（同上：USER_CORRECTION 不放行——它改的是有源图的块）。
            .filter {
                it.sourceRegion == null &&
                    it.provenance != QuestionBlockProvenance.USER_TRANSCRIPTION
            }
            .forEach {
                add(CapturedQuestionIssue(CapturedQuestionIssueCode.MISSING_SOURCE_REGION, it.blockId))
            }
        if (value.document.blocks.none { block -> block.hasMeaningfulQuestionContent() }) {
            add(CapturedQuestionIssue(CapturedQuestionIssueCode.EMPTY_QUESTION))
        }
    }.distinct()

    private fun NormalizedSourceRegion.isValid(): Boolean =
        left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite() &&
            left in 0.0..1.0 && top in 0.0..1.0 && right in 0.0..1.0 && bottom in 0.0..1.0 &&
            left < right && top < bottom

    private fun ContentBlock.hasMeaningfulQuestionContent(): Boolean = when (this) {
        is ContentBlock.Paragraph -> markdown.isNotBlank()
        is ContentBlock.Formula -> latex.isNotBlank() || alternativeText.isNotBlank()
        is ContentBlock.ChoiceGroup -> promptMarkdown.isNotBlank() || choices.any {
            it.markdown.isNotBlank()
        }
        is ContentBlock.Figure -> alternativeText.isNotBlank()
        is ContentBlock.Unknown -> false
    }

    private val USER_PROVENANCE = setOf(
        QuestionBlockProvenance.USER_TRANSCRIPTION,
        QuestionBlockProvenance.USER_CORRECTION,
    )

    private val COMMIT_ACCEPTED_STATUSES = setOf(
        QuestionBlockReviewStatus.LOCAL_POLICY_ACCEPTED,
        QuestionBlockReviewStatus.USER_CONFIRMED,
    )
}

/** Strict bounded codec used for Room snapshots and adapter boundaries. */
object CapturedQuestionDocumentCodec {
    const val MAX_ENCODED_CHARS = 128_000

    private val json = Json {
        classDiscriminator = "type"
        encodeDefaults = true
        explicitNulls = true
        ignoreUnknownKeys = false
    }

    fun encode(value: CapturedQuestionDocument): String {
        require(CapturedQuestionDocumentValidator.validateDraft(value).isEmpty()) {
            "Captured question document is invalid"
        }
        return json.encodeToString(CapturedQuestionDocument.serializer(), value).also {
            require(it.length <= MAX_ENCODED_CHARS) { "Captured question snapshot exceeds budget" }
        }
    }

    fun decode(value: String): CapturedQuestionDocument {
        require(value.length <= MAX_ENCODED_CHARS) { "Captured question snapshot exceeds budget" }
        return json.decodeFromString(CapturedQuestionDocument.serializer(), value).also {
            require(CapturedQuestionDocumentValidator.validateDraft(it).isEmpty()) {
                "Captured question snapshot is invalid"
            }
        }
    }
}

object CapturedQuestionDocumentFingerprint {
    fun of(value: CapturedQuestionDocument): String = sha256(
        CapturedQuestionDocumentCodec.encode(value),
    )

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte) }
}

/** Deterministic legacy/search projection. The structured snapshot remains authoritative. */
object QuestionDocumentMarkdownProjection {
    fun project(document: QuestionDocument): String = buildList {
        document.title?.takeIf(String::isNotBlank)?.let { add("# $it") }
        document.blocks.forEach { block ->
            when (block) {
                is ContentBlock.Paragraph -> add(block.markdown)
                is ContentBlock.Formula -> add(
                    if (block.display) "\$\$${block.latex}\$\$" else "\$${block.latex}\$",
                )
                is ContentBlock.ChoiceGroup -> {
                    if (block.promptMarkdown.isNotBlank()) add(block.promptMarkdown)
                    block.choices.forEachIndexed { index, choice ->
                        add("${('A'.code + index).toChar()}. ${choice.markdown}")
                    }
                }
                is ContentBlock.Figure -> add("[图形：${block.alternativeText}]")
                is ContentBlock.Unknown -> Unit
            }
        }
    }.filter(String::isNotBlank).joinToString("\n\n")
}
