package com.tingyun.smartmistakebook.core.export

import com.tingyun.smartmistakebook.core.model.FigureSchema
import com.tingyun.smartmistakebook.core.model.MistakePdfLayout
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 一份**已定稿、可直接渲染**的卷面草稿（6B-2）。
 *
 * 它只带渲染输入本身与三条元信息：是否真的改过（决定文件名与"未编辑逐位不变"）、题数、
 * 以及编辑前那份批量快照的输入指纹（用于核对来源）。落盘由 [MistakePaperDraftStore] 负责，
 * 走既有 JobRequest 形态：WorkManager 的 Data 里只放一个 draft id，块序列不塞进 10KB 输入。
 *
 * **不变式**：未编辑的草稿，[input] 必须就是编辑前的那份快照（指纹相同）；编辑过的草稿，
 * `questionDocumentSha256` 仍是来源题面文档的指纹——卷面编辑不产生新的 revision。
 */
class MistakePaperDraft internal constructor(
    val input: MistakePdfExportInput,
    val edited: Boolean,
    val questionCount: Int,
    val sourceInputSha256: String,
) {
    init {
        require(questionCount >= 1) { "A paper draft needs at least one question" }
        require(sourceInputSha256.matches(SHA_256)) { "Source fingerprint is invalid" }
        require(input.inputSha256.matches(SHA_256)) { "Input fingerprint is invalid" }
        require(input.blocks.isNotEmpty()) { "A paper draft needs blocks to render" }
        if (!edited) {
            require(input.inputSha256 == sourceInputSha256) {
                "An unedited draft must keep the source input fingerprint"
            }
        }
    }

    private companion object {
        val SHA_256 = Regex("[a-f0-9]{64}")
    }
}

/**
 * 卷面草稿的本地落盘（6B-2）。
 *
 * 形态与 worker 的既有约束对齐：`MistakeExportJobRequest` 只携带 draft id（短字符串，远低于
 * WorkManager 10KB 输入上限）；真正的块序列在应用私有目录里，由 worker 渲染前读回。
 *
 * 文件 = 第一行 `sha256(body)` + 第二行起 JSON。读取时先核摘要再解析：坏文件、被截断、
 * 版本不认识都返回 null（worker 如实失败，不拿半份草稿渲染）。
 *
 * **不做跨进程锁**：写入发生在入队前（同一调用方的协程），读取发生在 worker 执行时；
 * 发布用"临时文件 → 替换"避免读到未写完的文件。目录按 TTL 清理遗留草稿。
 */
class MistakePaperDraftStore(
    private val directory: File,
) {
    fun write(draftId: String, draft: MistakePaperDraft) {
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("Cannot create the paper draft directory")
        }
        val target = fileFor(draftId)
        val body = JSON.encodeToString(draft.toFileDto())
        val bodyBytes = body.toByteArray(StandardCharsets.UTF_8)
        require(bodyBytes.size <= MAX_DRAFT_BYTES) { "Draft is too large" }
        val payload = sha256Of(bodyBytes) + "\n" + body
        cleanup()
        val temporary = File(directory, "${target.name}.tmp")
        temporary.writeText(payload, StandardCharsets.UTF_8)
        if (target.exists() && !target.delete()) {
            temporary.delete()
            throw IOException("Cannot replace the previous paper draft")
        }
        if (!temporary.renameTo(target)) {
            temporary.copyTo(target, overwrite = true)
            temporary.delete()
        }
    }

    /** 读回一份草稿；任何形状/摘要/版本问题都返回 null（fail-closed）。 */
    fun read(draftId: String): MistakePaperDraft? = try {
        val file = fileFor(draftId)
        if (!file.isFile || file.length() !in 2..MAX_DRAFT_BYTES) {
            null
        } else {
            val payload = file.readText(StandardCharsets.UTF_8)
            val separator = payload.indexOf('\n')
            if (separator != 64) {
                null
            } else {
                val recorded = payload.substring(0, separator)
                val body = payload.substring(separator + 1)
                if (recorded != sha256Of(body.toByteArray(StandardCharsets.UTF_8))) {
                    null
                } else {
                    val dto = JSON.decodeFromString<PaperDraftFileDto>(body)
                    dto.toModelOrNull()
                }
            }
        }
    } catch (_: Exception) {
        null
    }

    fun delete(draftId: String) {
        runCatching { fileFor(draftId).delete() }
    }

    /** 清掉 TTL 之前的遗留草稿（写入时顺带调用；失败不影响本次写入）。 */
    fun cleanup(nowEpochMillis: Long = System.currentTimeMillis()) {
        runCatching {
            if (!directory.isDirectory) return@runCatching
            directory.listFiles()
                ?.filter { file ->
                    file.name.endsWith(DRAFT_SUFFIX) &&
                        nowEpochMillis - file.lastModified() > DRAFT_TTL_MILLIS
                }
                ?.forEach(File::delete)
        }
    }

    private fun fileFor(draftId: String): File {
        require(DRAFT_ID.matches(draftId)) { "A draft id must be a plain file token" }
        val file = File(directory, "$draftId$DRAFT_SUFFIX")
        // 二道闸：即使正则以后被放宽，也不许草稿落到目录之外。
        require(file.canonicalFile.parentFile == directory.canonicalFile) {
            "A draft must stay inside its directory"
        }
        return file
    }

    private fun MistakePaperDraft.toFileDto(): PaperDraftFileDto = PaperDraftFileDto(
        version = FORMAT_VERSION,
        edited = edited,
        questionCount = questionCount,
        sourceInputSha256 = sourceInputSha256,
        input = input.toDto(),
    )

    private fun PaperDraftFileDto.toModelOrNull(): MistakePaperDraft? {
        if (version != FORMAT_VERSION) return null
        if (questionCount < 1) return null
        if (!SHA_256.matches(sourceInputSha256)) return null
        val model = input.toModelOrNull() ?: return null
        if (model.blocks.isEmpty()) return null
        if (!edited && model.inputSha256 != sourceInputSha256) return null
        return runCatching {
            MistakePaperDraft(
                input = model,
                edited = edited,
                questionCount = questionCount,
                sourceInputSha256 = sourceInputSha256,
            )
        }.getOrNull()
    }

    private fun MistakePdfExportInput.toDto(): PaperInputDto = PaperInputDto(
        errorBookEntryId = errorBookEntryId,
        problemId = problemId,
        problemRevisionId = problemRevisionId,
        revisionNumber = revisionNumber,
        title = title,
        subject = subject,
        subtitle = subtitle,
        documentTitle = documentTitle,
        blocks = blocks.map { block -> block.toDto() },
        questionDocumentSha256 = questionDocumentSha256,
        inputSha256 = inputSha256,
        maxPages = maxPages,
        maxRenderedLines = maxRenderedLines,
        maxPdfBytes = maxPdfBytes,
        cleanImageLocalUri = cleanImageLocalUri,
        cleanImageSha256 = cleanImageSha256,
        cleanImageByteSize = cleanImageByteSize,
        questionImageRole = questionImageRole,
        solutionImages = solutionImages.map { image -> image.toDto() },
        layout = layout,
        userNote = userNote,
    )

    private fun PaperInputDto.toModelOrNull(): MistakePdfExportInput? = runCatching {
        MistakePdfExportInput(
            errorBookEntryId = errorBookEntryId,
            problemId = problemId,
            problemRevisionId = problemRevisionId,
            revisionNumber = revisionNumber,
            title = title,
            subject = subject,
            subtitle = subtitle,
            documentTitle = documentTitle,
            blocks = blocks.map { block -> block.toModel() },
            questionDocumentSha256 = questionDocumentSha256,
            inputSha256 = inputSha256,
            maxPages = maxPages,
            maxRenderedLines = maxRenderedLines,
            maxPdfBytes = maxPdfBytes,
            cleanImageLocalUri = cleanImageLocalUri,
            cleanImageSha256 = cleanImageSha256,
            cleanImageByteSize = cleanImageByteSize,
            questionImageRole = questionImageRole,
            solutionImages = solutionImages.map { image -> image.toModel() },
            layout = layout,
            userNote = userNote,
        )
    }.getOrNull()

    private fun MistakePdfExportImage.toDto(): PaperImageDto = PaperImageDto(
        role = role,
        localUri = localUri,
        contentSha256 = contentSha256,
        byteSize = byteSize,
    )

    private fun PaperImageDto.toModel(): MistakePdfExportImage = MistakePdfExportImage(
        role = role,
        localUri = localUri,
        contentSha256 = contentSha256,
        byteSize = byteSize,
    )

    private companion object {
        const val FORMAT_VERSION = 1
        const val DRAFT_SUFFIX = ".draft"
        const val MAX_DRAFT_BYTES = 8L * 1024L * 1024L
        const val DRAFT_TTL_MILLIS = 48L * 60L * 60L * 1000L
        // 草稿 id 会直接成为文件名：只允许跨平台安全字符（不含 ':'，Windows JVM 测试也能跑）。
        val DRAFT_ID = Regex("[A-Za-z0-9._-]{1,160}")
        val SHA_256 = Regex("[a-f0-9]{64}")
        val JSON = Json {
            encodeDefaults = true
            explicitNulls = true
            ignoreUnknownKeys = false
            // 不与 `FigureSchema.Unknown.type` 撞键：类判别字段用独立的 `schema_kind`。
            // （core:model 的文档编解码用 "type"；本文件只服务卷面草稿，格式自持。）
            classDiscriminator = "schema_kind"
        }
    }
}

private fun MistakePdfBlock.toDto(): PaperBlockDto = when (this) {
    is MistakePdfBlock.Paragraph -> PaperBlockDto.Paragraph(id, text)
    is MistakePdfBlock.SectionHeading -> PaperBlockDto.SectionHeading(id, text)
    is MistakePdfBlock.Formula -> PaperBlockDto.Formula(id, latex, alternativeText, display)
    is MistakePdfBlock.ChoiceGroup -> PaperBlockDto.ChoiceGroup(
        id = id,
        prompt = prompt,
        choices = choices.map { choice ->
            PaperChoiceDto(
                id = choice.id,
                text = choice.text,
                selected = choice.selected,
                enabled = choice.enabled,
            )
        },
        enabled = enabled,
    )
    is MistakePdfBlock.Figure -> PaperBlockDto.Figure(id, title, alternativeText, schema)
    is MistakePdfBlock.Image -> PaperBlockDto.Image(
        id = id,
        localUri = localUri,
        title = title,
        expectedContentSha256 = expectedContentSha256,
        expectedByteSize = expectedByteSize,
        role = role,
    )
    is MistakePdfBlock.Note -> PaperBlockDto.Note(id, text)
}

private fun PaperBlockDto.toModel(): MistakePdfBlock = when (this) {
    is PaperBlockDto.Paragraph -> MistakePdfBlock.Paragraph(id, text)
    is PaperBlockDto.SectionHeading -> MistakePdfBlock.SectionHeading(id, text)
    is PaperBlockDto.Formula -> MistakePdfBlock.Formula(id, latex, alternativeText, display)
    is PaperBlockDto.ChoiceGroup -> MistakePdfBlock.ChoiceGroup(
        id = id,
        prompt = prompt,
        choices = choices.map { choice ->
            MistakePdfChoice(
                id = choice.id,
                text = choice.text,
                selected = choice.selected,
                enabled = choice.enabled,
            )
        },
        enabled = enabled,
    )
    is PaperBlockDto.Figure -> MistakePdfBlock.Figure(id, title, alternativeText, schema)
    is PaperBlockDto.Image -> MistakePdfBlock.Image(
        id = id,
        localUri = localUri,
        title = title,
        expectedContentSha256 = expectedContentSha256,
        expectedByteSize = expectedByteSize,
        role = role,
    )
    is PaperBlockDto.Note -> MistakePdfBlock.Note(id, text)
}

private fun sha256Of(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

@Serializable
private data class PaperDraftFileDto(
    val version: Int,
    val edited: Boolean,
    val questionCount: Int,
    val sourceInputSha256: String,
    val input: PaperInputDto,
)

@Serializable
private data class PaperInputDto(
    val errorBookEntryId: String,
    val problemId: String,
    val problemRevisionId: String,
    val revisionNumber: Int,
    val title: String,
    val subject: String,
    val subtitle: String,
    val documentTitle: String? = null,
    val blocks: List<PaperBlockDto>,
    val questionDocumentSha256: String,
    val inputSha256: String,
    val maxPages: Int,
    val maxRenderedLines: Int,
    val maxPdfBytes: Long,
    val cleanImageLocalUri: String? = null,
    /** 干净题面图的记录完整性（KD-55）；旧草稿缺这两项时按"不携带记录"处理。 */
    val cleanImageSha256: String? = null,
    val cleanImageByteSize: Long? = null,
    /** 题目区图角色（WG-07）；旧草稿缺省 = 不携带角色（按 CLEAN_IMAGE 处理）。 */
    val questionImageRole: String? = null,
    /** 解析区图（WG-07）：卷面副本必须原样保存全部视觉引用；旧草稿缺省为空。 */
    val solutionImages: List<PaperImageDto> = emptyList(),
    val layout: MistakePdfLayout,
    val userNote: String? = null,
)

@Serializable
private data class PaperImageDto(
    val role: String,
    val localUri: String,
    val contentSha256: String? = null,
    val byteSize: Long? = null,
)

@Serializable
private data class PaperChoiceDto(
    val id: String,
    val text: String,
    val selected: Boolean,
    val enabled: Boolean,
)

@Serializable
private sealed interface PaperBlockDto {
    val id: String

    @Serializable
    @SerialName("paragraph")
    data class Paragraph(override val id: String, val text: String) : PaperBlockDto

    @Serializable
    @SerialName("section_heading")
    data class SectionHeading(override val id: String, val text: String) : PaperBlockDto

    @Serializable
    @SerialName("formula")
    data class Formula(
        override val id: String,
        val latex: String,
        val alternativeText: String,
        val display: Boolean,
    ) : PaperBlockDto

    @Serializable
    @SerialName("choice_group")
    data class ChoiceGroup(
        override val id: String,
        val prompt: String,
        val choices: List<PaperChoiceDto>,
        val enabled: Boolean,
    ) : PaperBlockDto

    @Serializable
    @SerialName("figure")
    data class Figure(
        override val id: String,
        val title: String?,
        val alternativeText: String,
        val schema: FigureSchema,
    ) : PaperBlockDto

    @Serializable
    @SerialName("image")
    data class Image(
        override val id: String,
        val localUri: String,
        val title: String,
        /** 记录完整性（KD-55）；旧草稿缺这两项时按"不携带记录"处理。 */
        val expectedContentSha256: String? = null,
        val expectedByteSize: Long? = null,
        /** 图角色（WG-07）；旧草稿缺省 = 不携带角色。 */
        val role: String? = null,
    ) : PaperBlockDto

    @Serializable
    @SerialName("note")
    data class Note(override val id: String, val text: String) : PaperBlockDto
}
