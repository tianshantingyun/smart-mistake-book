package com.tingyun.smartmistakebook.core.export

import com.tingyun.smartmistakebook.core.domain.MistakeDetailState
import com.tingyun.smartmistakebook.core.domain.MistakeSourceAsset
import com.tingyun.smartmistakebook.core.domain.MistakeSourceLocation
import com.tingyun.smartmistakebook.core.domain.MistakeSourceSet
import com.tingyun.smartmistakebook.core.model.CapturedQuestionDocumentFingerprint
import com.tingyun.smartmistakebook.core.model.CapturedQuestionDocumentValidator
import com.tingyun.smartmistakebook.core.model.ContentBlock
import com.tingyun.smartmistakebook.core.model.FigureSchema
import com.tingyun.smartmistakebook.core.model.SafeInlineMarkdown
import com.tingyun.smartmistakebook.core.model.MistakePdfLayout
import com.tingyun.smartmistakebook.core.model.VisualRole
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * 题目区干净题面的持久角色值（4E WG-07：与 `VisualRole` 单源，不再手写第二份字面量）。
 *
 * 旧调用点（仪器化用例等）仍按这个名字引用；值就是枚举名，不会与保存/读侧漂开。
 */
internal val CLEAN_IMAGE_SOURCE_ROLE: String = VisualRole.CLEAN_IMAGE.name

enum class MistakePdfIneligibility {
    LOADING,
    LEGACY_CONTENT,
    CORRUPT_CONTENT,
    NOT_FOUND,
    UNCONFIRMED_OR_INVALID_DOCUMENT,
    UNSUPPORTED_BLOCK,
    EMPTY_SUPPORTED_BLOCK,
    INVALID_CHOICE_STATE,
}

sealed interface MistakePdfEligibilityResult {
    data class Eligible(val input: MistakePdfExportInput) : MistakePdfEligibilityResult

    data class Ineligible(
        val reasons: Set<MistakePdfIneligibility>,
    ) : MistakePdfEligibilityResult {
        init {
            require(reasons.isNotEmpty()) { "An ineligible export must have a reason" }
        }
    }
}

/**
 * Strict bridge from the trusted mistake-detail projection into an immutable export snapshot.
 * fallbackMarkdown is deliberately absent from this contract.
 *
 * 4E WG-07 起只放行**按角色筛过**的视觉资产：题目区图（CLEAN_IMAGE 优先、其次
 * QUESTION_DIAGRAM）与解析区图（仅 TUTOR_EXPLANATION，且仅请求解析区时）。原题照片、
 * 源证据与其它角色仍然不进入导出输入。
 */
object MistakePdfEligibility {
    /**
     * @param layout 版式参数（B1）。默认 [MistakePdfLayout.DEFAULT] 与参数化前的固定版式
     *   等价；非法参数在这里 fail-closed（不进入渲染）。
     */
    fun check(
        state: MistakeDetailState,
        layout: MistakePdfLayout = MistakePdfLayout.DEFAULT,
    ): MistakePdfEligibilityResult {
        val violations = layout.validate()
        require(violations.isEmpty()) { "Invalid export layout: $violations" }
        return when (state) {
            MistakeDetailState.Loading -> ineligible(MistakePdfIneligibility.LOADING)
            is MistakeDetailState.Legacy -> ineligible(MistakePdfIneligibility.LEGACY_CONTENT)
            is MistakeDetailState.CorruptSnapshot -> ineligible(MistakePdfIneligibility.CORRUPT_CONTENT)
            MistakeDetailState.NotFound -> ineligible(MistakePdfIneligibility.NOT_FOUND)
            is MistakeDetailState.Ready -> checkReady(state, layout)
        }
    }

    private fun checkReady(
        state: MistakeDetailState.Ready,
        layout: MistakePdfLayout,
    ): MistakePdfEligibilityResult {
        val reasons = buildSet {
            if (
                CapturedQuestionDocumentValidator
                    .validateForCommit(state.questionDocument)
                    .isNotEmpty()
            ) {
                add(MistakePdfIneligibility.UNCONFIRMED_OR_INVALID_DOCUMENT)
            }
            state.questionDocument.document.blocks.forEach { block ->
                when (block) {
                    is ContentBlock.Paragraph -> {
                        if (block.markdown.isBlank()) {
                            add(MistakePdfIneligibility.EMPTY_SUPPORTED_BLOCK)
                        }
                    }
                    is ContentBlock.Formula -> {
                        if (block.latex.isBlank() && block.alternativeText.isBlank()) {
                            add(MistakePdfIneligibility.EMPTY_SUPPORTED_BLOCK)
                        }
                    }
                    is ContentBlock.ChoiceGroup -> {
                        if (block.choices.isEmpty() || block.choices.any { it.markdown.isBlank() }) {
                            add(MistakePdfIneligibility.EMPTY_SUPPORTED_BLOCK)
                        }
                        if (
                            block.selectedChoiceId != null &&
                            block.choices.none { it.id == block.selectedChoiceId }
                        ) {
                            add(MistakePdfIneligibility.INVALID_CHOICE_STATE)
                        }
                    }
                    is ContentBlock.Figure -> Unit
                    is ContentBlock.Unknown -> add(MistakePdfIneligibility.UNSUPPORTED_BLOCK)
                }
            }
        }
        if (reasons.isNotEmpty()) return MistakePdfEligibilityResult.Ineligible(reasons)

        val identity = state.detail.identity
        val document = state.questionDocument
        val blocks = document.document.blocks.map { block ->
            when (block) {
                is ContentBlock.Paragraph -> MistakePdfBlock.Paragraph(
                    id = block.id,
                    text = safeInlineText(block.markdown),
                )
                is ContentBlock.Formula -> MistakePdfBlock.Formula(
                    id = block.id,
                    latex = block.latex,
                    alternativeText = block.alternativeText,
                    display = block.display,
                )
                is ContentBlock.ChoiceGroup -> MistakePdfBlock.ChoiceGroup(
                    id = block.id,
                    prompt = safeInlineText(block.promptMarkdown),
                    choices = block.choices.map { choice ->
                        MistakePdfChoice(
                            id = choice.id,
                            text = safeInlineText(choice.markdown),
                            selected = choice.id == block.selectedChoiceId,
                            enabled = choice.enabled,
                        )
                    },
                    enabled = block.enabled,
                )
                is ContentBlock.Figure -> MistakePdfBlock.Figure(
                    id = block.id,
                    title = block.title,
                    alternativeText = block.alternativeText,
                    schema = block.schema,
                )
                is ContentBlock.Unknown -> error("Unsupported blocks were rejected before mapping")
            }
        }
        val documentFingerprint = CapturedQuestionDocumentFingerprint.of(document)
        // 同一份资产记录同时给出 URI 与完整性字段（KD-55）：渲染前按它复核文件字节。
        val questionImage = questionImageAsset(state.detail.source)
        val availableQuestionUri =
            (questionImage?.location as? MistakeSourceLocation.Available)?.localUri
        // 解析区只收 TUTOR_EXPLANATION，且只在学生请求解析区时才进导出输入（WG-07）。
        val solutionImages = solutionImageAssets(state.detail.source, layout.includeSolution)
        return MistakePdfEligibilityResult.Eligible(
            MistakePdfExportInput(
                errorBookEntryId = identity.errorBookEntryId,
                problemId = identity.problemId,
                problemRevisionId = identity.problemRevisionId,
                revisionNumber = identity.revisionNumber,
                title = identity.title,
                subject = identity.subject,
                subtitle = "${identity.subject} · 第 ${identity.revisionNumber} 版",
                documentTitle = document.document.title,
                blocks = blocks,
                cleanImageLocalUri = availableQuestionUri,
                cleanImageSha256 = questionImage?.contentSha256,
                cleanImageByteSize = questionImage?.byteSize,
                questionImageRole = questionImage?.role,
                solutionImages = solutionImages.map { asset ->
                    MistakePdfExportImage(
                        role = asset.role,
                        localUri = (asset.location as MistakeSourceLocation.Available).localUri,
                        contentSha256 = asset.contentSha256,
                        byteSize = asset.byteSize,
                    )
                },
                userNote = state.detail.userNote?.let(::safeInlineText)?.takeIf(String::isNotBlank),
                questionDocumentSha256 = documentFingerprint,
                inputSha256 = exportFingerprint(
                    "mistake-export-v3",
                    identity.errorBookEntryId,
                    identity.problemId,
                    identity.problemRevisionId,
                    identity.revisionNumber.toString(),
                    identity.title,
                    identity.subject,
                    documentFingerprint,
                    layout.canonicalForm(),
                    if (layout.includeNote) state.detail.userNote?.let(::safeInlineText).orEmpty() else "",
                    availableQuestionUri.orEmpty(),
                    questionImage?.contentSha256.orEmpty(),
                    questionImage?.role.orEmpty(),
                    solutionImages.size.toString(),
                    *solutionImages
                        .flatMap { asset ->
                            listOf(
                                asset.role,
                                (asset.location as MistakeSourceLocation.Available).localUri,
                                asset.contentSha256,
                            )
                        }
                        .toTypedArray(),
                ),
                layout = layout,
            ),
        )
    }

    /**
     * 题目区图的资产记录：**CLEAN_IMAGE 优先，其次 QUESTION_DIAGRAM**（4E WG-07）。
     *
     * 只认有可用位置的记录；URI 与完整性字段（sha/长度）必须来自**同一条**记录（KD-55），
     * 所以这里一次取出整条记录。讲解图（TUTOR_EXPLANATION）不是题目本身，永远不参与这里
     * 的选择——这正是"题目区画着讲解图"的像素级失败被挡住的地方。
     *
     * 旧库可能残留同一 revision 的多张 CLEAN_IMAGE：取**创建时间最新**的那张（同刻按资产
     * id），与详情/重绘源的"当前干净题面"口径一致。attach 侧已改为替换旧链接，这里对旧数据
     * 兜底，避免重绘成功后导出仍拿第一张旧图。
     */
    private fun questionImageAsset(source: MistakeSourceSet): MistakeSourceAsset? {
        val available = availableAssets(source)
        return available.filter { asset -> asset.role == VisualRole.CLEAN_IMAGE.name }
            .maxWithOrNull(compareBy<MistakeSourceAsset> { it.createdAtEpochMillis }
                .thenBy { it.sourceAssetId })
            ?: available.filter { asset -> asset.role == VisualRole.QUESTION_DIAGRAM.name }
                .maxWithOrNull(compareBy<MistakeSourceAsset> { it.createdAtEpochMillis }
                    .thenBy { it.sourceAssetId })
    }

    /**
     * 解析区图的资产记录：只收 role=TUTOR_EXPLANATION；未请求解析区时返回空。
     *
     * 排序按（创建时间, 资产 id）：讲解过程可能有多张图，顺序必须确定，否则同一份输入会
     * 排出不同的卷（缓存键相同、产物不同）。详情读侧的排序只保证 role/资产 id，
     * 这里补上时间维度。
     */
    private fun solutionImageAssets(
        source: MistakeSourceSet,
        includeSolution: Boolean,
    ): List<MistakeSourceAsset> {
        if (!includeSolution) return emptyList()
        return availableAssets(source)
            .filter { asset -> asset.role == VisualRole.TUTOR_EXPLANATION.name }
            .sortedWith(
                compareBy({ asset -> asset.createdAtEpochMillis }, { asset -> asset.sourceAssetId }),
            )
    }

    private fun availableAssets(source: MistakeSourceSet): List<MistakeSourceAsset> =
        (source as? MistakeSourceSet.Present)?.assets
            ?.filter { asset -> asset.location is MistakeSourceLocation.Available }
            .orEmpty()

    private fun safeInlineText(value: String): String = buildString {
        SafeInlineMarkdown.parse(value).forEach { token ->
            when (token) {
                is com.tingyun.smartmistakebook.core.model.InlineToken.Text -> append(token.value)
                is com.tingyun.smartmistakebook.core.model.InlineToken.Strong -> append(token.value)
                is com.tingyun.smartmistakebook.core.model.InlineToken.Emphasis -> append(token.value)
                is com.tingyun.smartmistakebook.core.model.InlineToken.Code -> append(token.value)
                is com.tingyun.smartmistakebook.core.model.InlineToken.Formula -> {
                    append('$')
                    append(token.value)
                    append('$')
                }
                com.tingyun.smartmistakebook.core.model.InlineToken.LineBreak -> append('\n')
            }
        }
    }

    private fun ineligible(reason: MistakePdfIneligibility) =
        MistakePdfEligibilityResult.Ineligible(setOf(reason))
}

/**
 * 导出输入里的一张**已按角色筛过**的图（4E WG-07）。
 *
 * 它消灭的失败：解析区的讲解图只能以"某张图"的身份混在块序列里，渲染侧无从判断它是不是
 * 题目图——资格层一次定下 role + 位置 + 记录完整性，排版与交付只读这份事实，不再各自猜。
 * [localUri] 是 `file://` 本地路径；[contentSha256]/[byteSize] 来自同一条资产记录（KD-55），
 * 渲染前用它复核文件字节。
 */
data class MistakePdfExportImage(
    val role: String,
    val localUri: String,
    val contentSha256: String? = null,
    val byteSize: Long? = null,
) {
    init {
        require(role.isNotBlank()) { "An export image needs a role" }
        require(localUri.isNotBlank()) { "An export image needs a local URI" }
        require(contentSha256 == null || SHA_256.matches(contentSha256)) {
            "An export image SHA-256 is invalid"
        }
        require(byteSize == null || byteSize > 0) { "An export image byte size must be positive" }
    }

    private companion object {
        val SHA_256 = Regex("[a-f0-9]{64}")
    }
}

/**
 * 题目区图的标题（PDF 小节标题 / Word 小标题 / 卷面图块标题共用）。
 *
 * CLEAN_IMAGE（或旧调用方不带角色）仍是"干净题面"——旧输出逐位不变；QUESTION_DIAGRAM
 * 是题目自带的示意图，叫"题图"而不是谎称它是干净重绘。
 */
internal fun questionImageLabel(role: String?): String =
    if (role == VisualRole.QUESTION_DIAGRAM.name) "题图" else "干净题面"

class MistakePdfExportInput internal constructor(
    val errorBookEntryId: String,
    val problemId: String,
    val problemRevisionId: String,
    val revisionNumber: Int,
    val title: String,
    val subject: String,
    val subtitle: String,
    val documentTitle: String?,
    val blocks: List<MistakePdfBlock>,
    val questionDocumentSha256: String,
    val inputSha256: String,
    val maxPages: Int = MistakePdfExportLimits.MAX_SINGLE_PAGES,
    val maxRenderedLines: Int = MistakePdfExportLimits.MAX_SINGLE_RENDERED_LINES,
    val maxPdfBytes: Long = MistakePdfExportLimits.MAX_SINGLE_PDF_BYTES,
    val cleanImageLocalUri: String? = null,
    /**
     * 干净题面图在资产记录里的内容 sha（KD-55）；docx 渲染前用它复核 `file://` 读到的
     * 字节。null = 输入不携带记录（仅旧调用方/测试直接构造），此时不做该项复核。
     */
    val cleanImageSha256: String? = null,
    /** 干净题面图在资产记录里的字节长度（KD-55），与 [cleanImageSha256] 同一条记录。 */
    val cleanImageByteSize: Long? = null,
    /**
     * 题目区图（[cleanImageLocalUri]）的资产角色：CLEAN_IMAGE / QUESTION_DIAGRAM；
     * null = 旧调用方不携带角色（按 CLEAN_IMAGE 处理，输出与参数化前一致）。
     */
    val questionImageRole: String? = null,
    /**
     * 解析区图：**只允许 role=TUTOR_EXPLANATION**，且只在 `layout.includeSolution=true` 时
     * 由资格层填充。解析区是独立成区的图块（题目区之后），不是题目图。
     */
    val solutionImages: List<MistakePdfExportImage> = emptyList(),
    /** 版式参数；默认 = 参数化前的固定版式（见 [MistakePdfLayout.DEFAULT]）。 */
    val layout: MistakePdfLayout = MistakePdfLayout.DEFAULT,
    /** 条目备注（includeNote 时才进渲染）；入口已做安全内联化。 */
    val userNote: String? = null,
) {
    init {
        val violations = layout.validate()
        require(violations.isEmpty()) { "Invalid export layout: $violations" }
        require(
            questionImageRole == null ||
                questionImageRole == VisualRole.CLEAN_IMAGE.name ||
                questionImageRole == VisualRole.QUESTION_DIAGRAM.name,
        ) {
            "A question image role must be CLEAN_IMAGE or QUESTION_DIAGRAM"
        }
        require(solutionImages.all { image -> image.role == VisualRole.TUTOR_EXPLANATION.name }) {
            "A solution image must carry the TUTOR_EXPLANATION role"
        }
    }
}

sealed interface MistakePdfBlock {
    val id: String

    /**
     * 批量快照里的题面图。[expectedContentSha256]/[expectedByteSize] 是资产记录里的完整性
     * 字段（KD-55）：docx 渲染前用它复核读到的字节。默认 null = 旧调用方不携带记录。
     */
    data class Image(
        override val id: String,
        val localUri: String,
        val title: String,
        val expectedContentSha256: String? = null,
        val expectedByteSize: Long? = null,
        /**
         * 这张图在卷面里的角色（4E WG-07）：CLEAN_IMAGE / QUESTION_DIAGRAM 是题目区图；
         * TUTOR_EXPLANATION 是解析区图，**不得在题目区渲染**（排版与 Word 两侧都按它过滤）。
         * null = 旧卷面块（按题目区图处理，输出逐位不变）。
         */
        val role: String? = null,
    ) : MistakePdfBlock

    data class Note(override val id: String, val text: String) : MistakePdfBlock

    data class Paragraph(
        override val id: String,
        val text: String,
    ) : MistakePdfBlock

    data class SectionHeading(
        override val id: String,
        val text: String,
    ) : MistakePdfBlock

    data class Formula(
        override val id: String,
        val latex: String,
        val alternativeText: String,
        val display: Boolean,
    ) : MistakePdfBlock

    data class ChoiceGroup(
        override val id: String,
        val prompt: String,
        val choices: List<MistakePdfChoice>,
        val enabled: Boolean,
    ) : MistakePdfBlock

    data class Figure(
        override val id: String,
        val title: String?,
        val alternativeText: String,
        val schema: FigureSchema,
    ) : MistakePdfBlock
}

data class MistakePdfChoice(
    val id: String,
    val text: String,
    val selected: Boolean,
    val enabled: Boolean,
)

internal fun ByteArray.toHex(): String = joinToString(separator = "") { byte ->
    "%02x".format(byte)
}

internal fun exportFingerprint(vararg values: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    values.forEach { value ->
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        digest.update((bytes.size ushr 24).toByte())
        digest.update((bytes.size ushr 16).toByte())
        digest.update((bytes.size ushr 8).toByte())
        digest.update(bytes.size.toByte())
        digest.update(bytes)
    }
    return digest.digest().toHex()
}
