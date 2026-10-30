package com.tingyun.smartmistakebook.core.database

import com.tingyun.smartmistakebook.core.model.KnowledgeMaterialBindingVerdict
import com.tingyun.smartmistakebook.core.model.KnowledgeMaterialBindingVerdictSource
import com.tingyun.smartmistakebook.core.model.KnowledgeMaterialDerivationKind
import com.tingyun.smartmistakebook.core.model.KnowledgeMaterialNodeRole
import com.tingyun.smartmistakebook.core.model.KnowledgeNodeGranularity
import com.tingyun.smartmistakebook.core.model.KnowledgeNodeVerificationStatus
import com.tingyun.smartmistakebook.core.model.KnowledgeSourceContentUsePolicy
import com.tingyun.smartmistakebook.core.model.KnowledgeSourceLicenseStatus
import com.tingyun.smartmistakebook.core.model.KnowledgeTeachingMaterialType
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Import boundary between the reviewed knowledge corpus and durable teaching context.
 *
 * Worked examples, complete solutions, and derivations are valid teaching content. What this
 * boundary excludes is assessment behavior: materials have no practice-unit identity, answer key,
 * score, difficulty, or scheduling metadata.
 */
object KnowledgeTeachingMaterialContract {
    // 容量不设总量上限（用户 2026-09-19 决定）：材料数/绑定数/总字符数都不再设门。
    // 只保留每条材料自身的结构约束（绑定数 1..16、恰好一个 PRIMARY）。
    private const val MAX_BINDINGS_PER_MATERIAL = 16
    private val uppercaseSha256 = Regex("[A-F0-9]{64}")
    private val materialTypes = enumValues<KnowledgeTeachingMaterialType>().mapTo(hashSetOf()) {
        it.name
    }
    private val derivationKinds = enumValues<KnowledgeMaterialDerivationKind>().mapTo(hashSetOf()) {
        it.name
    }
    private val bindingRoles = enumValues<KnowledgeMaterialNodeRole>().mapTo(hashSetOf()) {
        it.name
    }
    private val bindingVerdicts = enumValues<KnowledgeMaterialBindingVerdict>().mapTo(hashSetOf()) {
        it.name
    }
    private val bindingVerdictSources = enumValues<KnowledgeMaterialBindingVerdictSource>()
        .mapTo(hashSetOf()) { it.name }

    fun validate(
        materials: List<KnowledgeTeachingMaterialRecord>,
        bindings: List<KnowledgeTeachingMaterialNodeBindingRecord>,
        nodes: List<KnowledgeNodeSeedRecord>,
        sources: List<KnowledgeSourceSeedRecord>,
    ) {
        if (materials.isEmpty() && bindings.isEmpty()) return
        validate(
            materials = materials,
            bindings = bindings,
            nodesById = nodes.associateBy(KnowledgeNodeSeedRecord::knowledgeNodeId),
            sourcesById = sources.associateBy(KnowledgeSourceSeedRecord::sourceId),
        )
    }

    /**
     * 与 [validate] **逐字相同**的校验主体，只是节点/来源索引由调用方提供。
     *
     * 消灭的失败（2026-10-31 剖析）：逐材料调用的 [problemWith] 走 [validate]，而 [validate]
     * 每次都会为 3,600+ 节点重建 `nodesById`。整包 6.5 万材料 = 2.3 亿次 map 写入，
     * 设备实测这一段 **33,650 ms**（`planMaterials` 39,246 ms 的 86%）。索引只依赖
     * `acceptedNodes` / `teachingSources`，两者在整个循环里不变，所以可以整包建一次。
     *
     * 等价性不是"应该一样"而是可判定的：`associateBy` 是纯函数，同一个列表在同一循环内
     * 产出的 map 逐项相同；循环体只读这两个 map，从不改它们。结果（跳过理由、计数、写出的行）
     * 因此逐字节不变。
     */
    private fun validate(
        materials: List<KnowledgeTeachingMaterialRecord>,
        bindings: List<KnowledgeTeachingMaterialNodeBindingRecord>,
        nodesById: Map<String, KnowledgeNodeSeedRecord>,
        sourcesById: Map<String, KnowledgeSourceSeedRecord>,
    ) {
        requireValid(materials.isNotEmpty()) {
            "Knowledge teaching-material bindings require imported materials"
        }
        // 不设总量上限（用户 2026-09-19 决定：知识库容量不设限，越全越好）。
        // 只保留结构不变量：每条材料至少一个绑定，故 bindings 数不少于 materials 数。
        requireValid(bindings.size >= materials.size) {
            "Knowledge teaching-material binding count is invalid"
        }
        requireValid(materials.map(KnowledgeTeachingMaterialRecord::materialId).distinct().size == materials.size) {
            "Knowledge teaching-material ids must be unique"
        }
        requireValid(materials.map(KnowledgeTeachingMaterialRecord::stableCode).distinct().size == materials.size) {
            "Knowledge teaching-material stable codes must be unique"
        }
        requireValid(
            materials.map(KnowledgeTeachingMaterialRecord::contentFingerprint).distinct().size ==
                materials.size,
        ) { "Knowledge teaching-material content fingerprints must be unique" }
        requireValid(
            bindings.map {
                it.materialId to it.knowledgeNodeId
            }.distinct().size == bindings.size,
        ) { "A teaching material may bind to a knowledge point only once" }

        val materialsById = materials.associateBy(KnowledgeTeachingMaterialRecord::materialId)
        val bindingsByMaterial = bindings.groupBy(
            KnowledgeTeachingMaterialNodeBindingRecord::materialId,
        )

        materials.forEach { material ->
            id(material.materialId, "materialId")
            id(material.stableCode, "stableCode")
            known(material.materialType, "materialType", materialTypes)
            known(material.derivationKind, "derivationKind", derivationKinds)
            text(material.subject, "material.subject", 32)
            text(material.title, "material.title", 240)
            text(material.summaryMarkdown, "material.summaryMarkdown", 4_000)
            text(material.applicabilityMarkdown, "material.applicabilityMarkdown", 8_000)
            text(material.contentMarkdown, "material.contentMarkdown", 32_000)
            text(material.boundaryMarkdown, "material.boundaryMarkdown", 8_000)
            text(material.sourceLocator, "material.sourceLocator", 2_000)
            requireValid(uppercaseSha256.matches(material.contentFingerprint)) {
                "material.contentFingerprint must be an uppercase SHA-256 digest"
            }
            requireValid(
                material.contentFingerprint == KnowledgeTeachingMaterialFingerprint.expected(material),
            ) { "material.contentFingerprint does not match the reviewed material payload" }
            requireValid(material.reviewedAtEpochMillis > 0) {
                "material.reviewedAtEpochMillis must be positive"
            }
            val source = sourcesById[material.sourceId]
                ?: invalid("Every teaching material needs a reviewed source")
            requireValid(source.subject == material.subject) {
                "Teaching material and source must belong to the same subject"
            }
            requireValid(material.reviewedAtEpochMillis >= source.importedAtEpochMillis) {
                "Teaching-material review cannot predate its source import"
            }
            validateDerivationLicense(material, source)

            val materialBindings = bindingsByMaterial[material.materialId].orEmpty()
            requireValid(materialBindings.size in 1..MAX_BINDINGS_PER_MATERIAL) {
                "Every teaching material needs a bounded set of knowledge-point bindings"
            }
            requireValid(materialBindings.count { it.role == KnowledgeMaterialNodeRole.PRIMARY.name } == 1) {
                "Every teaching material needs exactly one primary knowledge point"
            }
        }
        // 总量字符预算同样取消（2026-09-19：容量不设限）。

        bindings.forEach { binding ->
            val material = materialsById[binding.materialId]
                ?: invalid("Teaching-material binding references an unknown material")
            val node = nodesById[binding.knowledgeNodeId]
                ?: invalid("Teaching-material binding references an unknown knowledge point")
            known(binding.role, "binding.role", bindingRoles)
            validateBindingVerdict(binding)
            requireValid(
                node.subject == material.subject &&
                    node.granularity == KnowledgeNodeGranularity.ATOMIC.name &&
                    node.verificationStatus == KnowledgeNodeVerificationStatus.SOURCE_GROUNDED.name,
            ) {
                "Teaching materials may bind only to reviewed fine-grained knowledge in one subject"
            }
            requireValid(material.reviewedAtEpochMillis >= node.createdAtEpochMillis) {
                "Teaching-material review cannot predate its knowledge point"
            }
        }
    }

    /**
     * **逐条**校验一条材料：通过返回 null，不通过返回原因。
     *
     * 调和循环用它实现"坏对象只跳过自己"。整批校验是全有或全无的——KD-15 就是
     * 80 条材料的 `reviewedAt` 早于来源 `importedAt` 几毫秒，把整个材料集挡在门外、
     * 每次启动弹横幅。
     *
     * 复用 [validate] 本身（只传这一条材料）而不是另写规则：两套规则迟早漂移。
     */
    fun problemWith(
        material: KnowledgeTeachingMaterialRecord,
        bindings: List<KnowledgeTeachingMaterialNodeBindingRecord>,
        nodes: List<KnowledgeNodeSeedRecord>,
        sources: List<KnowledgeSourceSeedRecord>,
    ): String? = runCatching {
        validate(listOf(material), bindings, nodes, sources)
    }.exceptionOrNull()?.message

    /**
     * 与四参重载**逐字相同**的判定，但节点/来源索引由调用方整包建一次后传入
     * （见私有 [validate] 的 KDoc：剖析实测每次重建 3,600+ 节点的索引占逐材料校验的绝大部分）。
     */
    internal fun problemWith(
        material: KnowledgeTeachingMaterialRecord,
        bindings: List<KnowledgeTeachingMaterialNodeBindingRecord>,
        nodesById: Map<String, KnowledgeNodeSeedRecord>,
        sourcesById: Map<String, KnowledgeSourceSeedRecord>,
    ): String? = runCatching {
        validate(listOf(material), bindings, nodesById, sourcesById)
    }.exceptionOrNull()?.message

    /**
     * The optional D-0 binding-verdict triple is all-or-none: a half-written judgement would leave
     * a reader unable to tell "never judged" from "judged, field lost on the way", and the second
     * state is not representable. The vocabulary and the timestamp are checked here too, because
     * records also arrive from paths that never went through the sidecar codec.
     */
    private fun validateBindingVerdict(binding: KnowledgeTeachingMaterialNodeBindingRecord) {
        val verdict = binding.verdict
        val verdictSource = binding.verdictSource
        val judgedAtEpochMillis = binding.judgedAtEpochMillis
        val presentCount = listOf(verdict, verdictSource, judgedAtEpochMillis).count { it != null }
        requireValid(presentCount == 0 || presentCount == 3) {
            "binding verdict fields must be all present or all absent"
        }
        if (verdict != null && verdictSource != null && judgedAtEpochMillis != null) {
            known(verdict, "binding.verdict", bindingVerdicts)
            known(verdictSource, "binding.verdictSource", bindingVerdictSources)
            requireValid(judgedAtEpochMillis >= 0) {
                "binding.judgedAtEpochMillis must not be negative"
            }
        }
    }

    private fun validateDerivationLicense(
        material: KnowledgeTeachingMaterialRecord,
        source: KnowledgeSourceSeedRecord,
    ) {
        val allowed = mutableSetOf(KnowledgeMaterialDerivationKind.REVIEWED_SYNTHESIS.name)
        when (source.contentUsePolicy) {
            KnowledgeSourceContentUsePolicy.EXCERPT_ALLOWED.name -> when (source.licenseStatus) {
                KnowledgeSourceLicenseStatus.PUBLIC_OFFICIAL.name ->
                    allowed += KnowledgeMaterialDerivationKind.PUBLIC_OFFICIAL_EXCERPT.name
                KnowledgeSourceLicenseStatus.LICENSED.name ->
                    allowed += KnowledgeMaterialDerivationKind.LICENSED_EXCERPT.name
            }
            KnowledgeSourceContentUsePolicy.ADAPTATION_ALLOWED.name -> {
                if (source.licenseStatus == KnowledgeSourceLicenseStatus.PUBLIC_OFFICIAL.name) {
                    allowed += KnowledgeMaterialDerivationKind.PUBLIC_OFFICIAL_EXCERPT.name
                }
                if (source.licenseStatus == KnowledgeSourceLicenseStatus.LICENSED.name) {
                    allowed += KnowledgeMaterialDerivationKind.LICENSED_EXCERPT.name
                    allowed += KnowledgeMaterialDerivationKind.LICENSED_ADAPTATION.name
                }
            }
        }
        requireValid(material.derivationKind in allowed) {
            "Teaching-material derivation is incompatible with its source content-use policy"
        }
    }

    private fun id(value: String, name: String) {
        requireValid(value.isNotBlank() && value == value.trim() && value.length <= 256) {
            "$name must be a trimmed non-blank string of at most 256 characters"
        }
        requireValid(value.none(Char::isISOControl)) { "$name contains control characters" }
    }

    private fun text(value: String, name: String, maxLength: Int) {
        requireValid(value.isNotBlank() && value == value.trim() && value.length <= maxLength) {
            "$name must be trimmed, non-blank, and at most $maxLength characters"
        }
        requireValid(
            value.none { character ->
                (character.isISOControl() && character !in "\n\r\t") ||
                    character == '\u061C' ||
                    character == '\u200E' ||
                    character == '\u200F' ||
                    character in '\u202A'..'\u202E' ||
                    character in '\u2066'..'\u2069'
            },
        ) { "$name contains unsupported control characters" }
    }

    private fun known(value: String, name: String, allowed: Set<String>) {
        requireValid(value in allowed) { "$name has unknown value '$value'" }
    }

    private inline fun requireValid(condition: Boolean, message: () -> String) {
        if (!condition) throw DatabaseContractViolationException(message())
    }

    private fun invalid(message: String): Nothing =
        throw DatabaseContractViolationException(message)
}

object KnowledgeTeachingMaterialFingerprint {
    /** 字段分隔符：改前 `joinToString("\u001F")` 的同一个字符，喂给 digest 的字节相同。 */
    private const val FIELD_SEPARATOR = 0x1F.toByte()

    private val HEX_DIGITS = "0123456789ABCDEF".toCharArray()

    /**
     * 材料内容指纹：11 个字段以 U+001F 连接后的 UTF-8 的 SHA-256，大写十六进制（64 字符）。
     *
     * **2026-10-31 剖析后的实现替换（输出逐字节不变）**：改前每次调用先 `joinToString`
     * 把整份正文再复制一遍，再用 `"%02X".format(...)` 逐字节格式化 32 次。设备实测这段
     * 在侧车解析（`recordMap` 合计 ≈16s）与逐材料校验（≈12.5s）里都是主体。
     * - 摘要值：同一串字节按同样顺序喂给同一个 SHA-256，**digest 逐字节相同**；
     * - 编码：查表产出 2 位大写十六进制，与 `%02X` 对 0..255 的输出逐字符相同
     *   （契约正则 `[A-F0-9]{64}` 与随包里既有的 64,973 条指纹本身就是这条串的判据：
     *   任何一位不同都会让校验拒绝整条材料，`BundledContentReconciliationInstrumentedTest`
     *   的 `skippedCount==0` 会立即变红）。
     */
    fun expected(material: KnowledgeTeachingMaterialRecord): String {
        val fields = listOf(
            material.subject,
            material.materialType,
            material.title,
            material.summaryMarkdown,
            material.applicabilityMarkdown,
            material.contentMarkdown,
            material.boundaryMarkdown,
            material.derivationKind,
            material.sourceId,
            material.sourceLocator,
            material.reviewedAtEpochMillis.toString(),
        )
        val digest = MessageDigest.getInstance("SHA-256")
        fields.forEachIndexed { index, field ->
            if (index > 0) digest.update(FIELD_SEPARATOR)
            digest.update(field.toByteArray(StandardCharsets.UTF_8))
        }
        val bytes = digest.digest()
        val hex = CharArray(bytes.size * 2)
        for (index in bytes.indices) {
            val value = bytes[index].toInt() and 0xff
            hex[index * 2] = HEX_DIGITS[value ushr 4]
            hex[index * 2 + 1] = HEX_DIGITS[value and 0x0f]
        }
        return String(hex)
    }
}
