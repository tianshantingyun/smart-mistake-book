package com.tingyun.smartmistakebook.core.database

import com.tingyun.smartmistakebook.core.model.KnowledgeNodeGranularity
import com.tingyun.smartmistakebook.core.model.KnowledgeNodeKind
import com.tingyun.smartmistakebook.core.model.KnowledgeNodeVerificationStatus
import com.tingyun.smartmistakebook.core.model.KnowledgeSourceContentUsePolicy
import com.tingyun.smartmistakebook.core.model.KnowledgeSourceLicenseStatus
import com.tingyun.smartmistakebook.core.model.KnowledgeSourceType
import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

/** The only boundary allowed to promote reviewed research into the durable ontology. */
object KnowledgeBaseImportContract {
    private const val MAX_IMPORT_RECORDS = 4_096
    private val uppercaseSha256 = Regex("[A-F0-9]{64}")
    private val subjects = setOf(
        "CHINESE",
        "MATH",
        "ENGLISH",
        "PHYSICS",
        "CHEMISTRY",
        "BIOLOGY",
        "POLITICS",
        "HISTORY",
        "GEOGRAPHY",
    )
    private val nodeKinds = enumValues<KnowledgeNodeKind>().map { it.name }.toSet()
    private val granularities = enumValues<KnowledgeNodeGranularity>().map { it.name }.toSet()
    private val verificationStatuses =
        enumValues<KnowledgeNodeVerificationStatus>().map { it.name }.toSet()
    private val sourceTypes = enumValues<KnowledgeSourceType>().map { it.name }.toSet()
    private val licenseStatuses = enumValues<KnowledgeSourceLicenseStatus>().map { it.name }.toSet()
    private val contentUsePolicies =
        enumValues<KnowledgeSourceContentUsePolicy>().map { it.name }.toSet()

    /**
     * 占位域（RFC 2606 / RFC 6761 的保留名 + 回环地址）。命中即拒收。
     *
     * 它消灭的具体失败：包里出现 `example.edu` 这类**不可能被任何人核实**的来源，
     * 而类型/状态全都合法，审阅者无从分辨"真实来源"与"为了过门而编的域名"。
     * 2026-11-03 的裁定要求对**任何出现的 URI** 生效（`source.sourceUri`、`source.licenseUri`、
     * 以及主包经 `toSource` 合成出来的源），不因 `licenseStatus` 宽松而放行。
     */
    private val placeholderHosts = setOf(
        "example.com",
        "example.net",
        "example.org",
        "example.edu",
        "localhost",
        "127.0.0.1",
        "0.0.0.0",
        "::1",
    )
    private val placeholderSuffixes = listOf(
        ".invalid",
        ".test",
        ".local",
        ".localhost",
        ".example",
    )

    /** 占位类的 attribution 必须写明的字样（与内容包的落盘口径一致）。 */
    private const val PLACEHOLDER_MARKER = "来源不明（占位）"

    /** `REFERENCE_ONLY` 且无 URI 时，attribution 必须写明的复用口径。 */
    private const val REFERENCE_ONLY_MARKER = "仅参考、独立重写"

    fun validate(
        sources: List<KnowledgeSourceSeedRecord>,
        nodes: List<KnowledgeNodeSeedRecord>,
        bindings: List<KnowledgeNodeSourceBindingSeedRecord>,
        existingSources: List<KnowledgeSourceSeedRecord> = emptyList(),
        existingParentNodes: List<KnowledgeNodeSeedRecord> = emptyList(),
    ) {
        requireImport(nodes.isNotEmpty()) { "A knowledge-base import needs at least one node" }
        requireImport(bindings.isNotEmpty()) {
            "A knowledge-base import needs reviewed provenance bindings"
        }
        requireImport(
            sources.size <= MAX_IMPORT_RECORDS &&
                nodes.size <= MAX_IMPORT_RECORDS &&
                bindings.size <= MAX_IMPORT_RECORDS,
        ) { "A knowledge-base import is too large" }

        validateSourcesOnly(sources)
        val newSourcesById = sources.associateBy(KnowledgeSourceSeedRecord::sourceId)
        val newNodesById = nodes.uniqueBy(KnowledgeNodeSeedRecord::knowledgeNodeId, "node ids")
        val sourcesById = existingSources.associateBy(KnowledgeSourceSeedRecord::sourceId) + newSourcesById
        val nodesById = existingParentNodes.associateBy(KnowledgeNodeSeedRecord::knowledgeNodeId) + newNodesById
        requireImport(nodes.map(KnowledgeNodeSeedRecord::stableCode).distinct().size == nodes.size) {
            "Knowledge node stable codes must be unique"
        }

        nodes.forEach { node -> validateNode(node, nodesById) }
        validateTopicHierarchyIsAcyclic(newNodesById.values, nodesById)
        validateBindings(bindings, newSourcesById, newNodesById, sourcesById)
    }

    /**
     * **逐条**校验一个节点：通过返回 null，不通过返回原因。
     *
     * 调和循环用它实现"坏对象只跳过自己"——整批校验是全有或全无的，一个越界字段会让
     * 整包停摆（KD-15 就是这个形态：80 条材料的时间戳倒挂，把另外一万条一起挡在门外）。
     *
     * 复用 [validateNode] 本身而不是另写一套规则：两套规则迟早会漂移，而漂移的方向是
     * "校验说没问题、导入却拒绝"或反过来。
     */
    fun problemWith(
        node: KnowledgeNodeSeedRecord,
        nodesById: Map<String, KnowledgeNodeSeedRecord>,
    ): String? = runCatching { validateNode(node, nodesById) }.exceptionOrNull()?.message

    fun validateSourcesOnly(sources: List<KnowledgeSourceSeedRecord>) {        requireImport(sources.size <= MAX_IMPORT_RECORDS) {
            "A knowledge-source import is too large"
        }
        sources.uniqueBy(KnowledgeSourceSeedRecord::sourceId, "source ids")
        requireImport(
            sources.map(KnowledgeSourceSeedRecord::contentFingerprint).distinct().size ==
                sources.size,
        ) { "Knowledge source fingerprints must be unique" }
        sources.forEach(::validateSource)
    }

    private fun validateSource(source: KnowledgeSourceSeedRecord) {
        id(source.sourceId, "sourceId")
        known(source.subject, "source.subject", subjects)
        known(source.sourceType, "source.sourceType", sourceTypes)
        text(source.title, "source.title", 1_000)
        source.publisher?.let { text(it, "source.publisher", 500) }
        source.edition?.let { text(it, "source.edition", 500) }
        known(source.licenseStatus, "source.licenseStatus", licenseStatuses)
        known(source.contentUsePolicy, "source.contentUsePolicy", contentUsePolicies)
        validateSourceReuseRights(source)
        requireImport(uppercaseSha256.matches(source.contentFingerprint)) {
            "source.contentFingerprint must be an uppercase SHA-256 digest"
        }
        requireImport(source.importedAtEpochMillis > 0) {
            "source.importedAtEpochMillis must be positive"
        }
        validateSourceUri(source)
    }

    private fun validateSourceReuseRights(source: KnowledgeSourceSeedRecord) {
        val directReuse = source.contentUsePolicy !=
            KnowledgeSourceContentUsePolicy.REVIEWED_SYNTHESIS_ONLY.name
        if (source.licenseStatus == KnowledgeSourceLicenseStatus.REFERENCE_ONLY.name) {
            requireImport(!directReuse) {
                "Reference-only knowledge sources permit reviewed synthesis only"
            }
        }
        if (directReuse) {
            text(
                source.licenseExpression.orEmpty(),
                "source.licenseExpression",
                240,
            )
            val licenseUri = source.licenseUri
                ?: throw DatabaseContractViolationException(
                    "Direct source reuse needs a license URI",
                )
            validateHttpsUri(licenseUri, "source.licenseUri")
            text(
                source.attributionText.orEmpty(),
                "source.attributionText",
                2_000,
            )
        } else {
            source.licenseExpression?.let {
                text(it, "source.licenseExpression", 240)
            }
            source.licenseUri?.let {
                validateHttpsUri(it, "source.licenseUri")
            }
            source.attributionText?.let {
                text(it, "source.attributionText", 2_000)
            }
        }
    }

    private fun validateNode(
        node: KnowledgeNodeSeedRecord,
        nodesById: Map<String, KnowledgeNodeSeedRecord>,
    ) {
        id(node.knowledgeNodeId, "knowledgeNodeId")
        id(node.stableCode, "stableCode")
        known(node.subject, "node.subject", subjects)
        text(node.displayName, "node.displayName", 500)
        text(node.canonicalName, "node.canonicalName", 500)
        node.parentKnowledgeNodeId?.let { id(it, "parentKnowledgeNodeId") }
        id(node.taxonomyVersion, "taxonomyVersion")
        requireImport(node.createdAtEpochMillis > 0) { "node.createdAtEpochMillis must be positive" }
        known(node.nodeKind, "node.nodeKind", nodeKinds)
        known(node.granularity, "node.granularity", granularities)
        known(node.verificationStatus, "node.verificationStatus", verificationStatuses)
        requireImport(node.verificationStatus != KnowledgeNodeVerificationStatus.MODEL_CANDIDATE.name) {
            "Model-candidate knowledge cannot enter the durable ontology"
        }
        requireImport(
            (node.nodeKind == KnowledgeNodeKind.TOPIC.name) ==
                (node.granularity == KnowledgeNodeGranularity.TOPIC.name),
        ) { "Only topic nodes may use topic granularity" }
        validateAliases(node.aliases)

        if (node.granularity == KnowledgeNodeGranularity.ATOMIC.name) {
            requireImport(node.verificationStatus == KnowledgeNodeVerificationStatus.SOURCE_GROUNDED.name) {
                "Atomic knowledge must be source-grounded before import"
            }
            text(node.boundaryMarkdown.orEmpty(), "node.boundaryMarkdown", 4_000)
            validateParent(node, nodesById, "Atomic knowledge needs a same-subject topic parent")
        } else {
            node.boundaryMarkdown?.let { text(it, "node.boundaryMarkdown", 4_000) }
            if (node.parentKnowledgeNodeId != null) {
                validateParent(node, nodesById, "Topic parents must remain inside one subject")
            }
        }
    }

    private fun validateParent(
        node: KnowledgeNodeSeedRecord,
        nodesById: Map<String, KnowledgeNodeSeedRecord>,
        errorPrefix: String,
    ) {
        val parent = node.parentKnowledgeNodeId?.let(nodesById::get)
        requireImport(
            parent != null &&
                parent.subject == node.subject &&
                parent.granularity == KnowledgeNodeGranularity.TOPIC.name &&
                parent.taxonomyVersion == node.taxonomyVersion,
        ) { "$errorPrefix in the same taxonomy" }
    }

    /**
     * 别名的**逐条**校验。这里**不设条数上限**。
     *
     * 曾经有一条 `require(aliases.size <= 32)`。它没有依据——没有 KDoc、没有具名常量、
     * 不从任何预算推导；真正的预算在 `KnowledgeSearchFeatureExtractor`（16 个片段 /
     * 192 个特征），而那与别名条数无关：84 条别名与 39 条别名都只产出 16 个片段。
     *
     * 保留它的代价却很实：实测 25 个节点（最多的生物「基因工程」84 条）被整条拒掉，
     * 于是**这 25 个知识点进不了任何用户的库**。删掉它是有意的——上限只让内容缺失，
     * 不换来任何安全。
     *
     * 逐条约束仍然保留：每条 ≤200 字、去空白、大小写不敏感唯一。
     */
    private fun validateAliases(aliases: Set<String>) {
        val normalized = aliases.map { alias ->
            text(alias, "node.alias", 200)
            requireImport(alias == alias.trim()) { "Knowledge aliases must be trimmed" }
            alias.lowercase(Locale.ROOT)
        }
        requireImport(normalized.distinct().size == normalized.size) {
            "Knowledge aliases must be unique ignoring case"
        }
    }

    private fun validateBindings(
        bindings: List<KnowledgeNodeSourceBindingSeedRecord>,
        newSourcesById: Map<String, KnowledgeSourceSeedRecord>,
        newNodesById: Map<String, KnowledgeNodeSeedRecord>,
        sourcesById: Map<String, KnowledgeSourceSeedRecord>,
    ) {
        val keys = bindings.map { Triple(it.knowledgeNodeId, it.sourceId, it.sourceLocator) }
        requireImport(keys.distinct().size == keys.size) { "Knowledge provenance bindings must be unique" }
        bindings.forEach { binding ->
            id(binding.knowledgeNodeId, "binding.knowledgeNodeId")
            id(binding.sourceId, "binding.sourceId")
            text(binding.sourceLocator, "binding.sourceLocator", 2_000)
            text(binding.derivationNote, "binding.derivationNote", 4_000)
            requireImport(binding.reviewedAtEpochMillis?.let { it > 0 } == true) {
                "Every provenance binding must record a positive review timestamp"
            }
            val node = newNodesById[binding.knowledgeNodeId]
            val source = sourcesById[binding.sourceId]
            requireImport(node != null && source != null && node.subject == source.subject) {
                "Every provenance binding must reference a new node and stay inside one subject"
            }
        }
        requireImport(bindings.mapTo(mutableSetOf()) { it.knowledgeNodeId } == newNodesById.keys) {
            "Every imported knowledge node must have reviewed provenance"
        }
        requireImport(newSourcesById.keys.all { sourceId -> bindings.any { it.sourceId == sourceId } }) {
            "Every imported knowledge source must support at least one node"
        }
    }

    /**
     * 来源 URI 的语义门（2026-11-03 裁定）：
     *
     * 1. **URI 必填只对 `LICENSED`**。曾经的 `uriRequired = licenseStatus != LICENSED`
     *    逼着 `PUBLIC_OFFICIAL` / `REFERENCE_ONLY` 也交一个 URL——内容侧于是填了假域名，
     *    契约反而成了伪造的推手。
     * 2. **占位域对任何出现的 URI 生效**（含 `licenseUri`，见 [validateHttpsUri]），
     *    不因状态宽松而放行。
     * 3. **URI 为 null 时必须有三元组**：`issuer = publisher`、`title`（[validateSource] 已保证
     *    非空）、`yearOrEdition = edition`。把"来源身份"从 URL 换成可核实的机构+文件名+版次，
     *    缺一即拒收——杜撰 URL 不再是唯一出路。
     * 4. `UNVERIFIED_PLACEHOLDER` 必须 null 且带显式标记；`REFERENCE_ONLY` 无 URI 时
     *    必须写明"仅参考、独立重写"。
     */
    private fun validateSourceUri(source: KnowledgeSourceSeedRecord) {
        val uriText = source.sourceUri
        val licensed = source.licenseStatus == KnowledgeSourceLicenseStatus.LICENSED.name
        val placeholder =
            source.licenseStatus == KnowledgeSourceLicenseStatus.UNVERIFIED_PLACEHOLDER.name
        if (uriText == null) {
            requireImport(!licensed) {
                "source.sourceUri is required for source.licenseStatus LICENSED"
            }
        } else {
            requireImport(!placeholder) {
                "source.sourceUri must be null for source.licenseStatus UNVERIFIED_PLACEHOLDER"
            }
            validateHttpsUri(uriText, "source.sourceUri")
        }
        if (uriText == null) {
            text(source.publisher.orEmpty(), "source.publisher", 500)
            text(source.edition.orEmpty(), "source.edition", 500)
        }
        when (source.licenseStatus) {
            KnowledgeSourceLicenseStatus.UNVERIFIED_PLACEHOLDER.name -> {
                requireImport(
                    source.contentUsePolicy ==
                        KnowledgeSourceContentUsePolicy.REVIEWED_SYNTHESIS_ONLY.name,
                ) {
                    "Placeholder knowledge sources permit reviewed synthesis only"
                }
                requireImport(
                    source.attributionText.orEmpty().contains(PLACEHOLDER_MARKER),
                ) {
                    "source.attributionText must mark a placeholder source as $PLACEHOLDER_MARKER"
                }
            }

            KnowledgeSourceLicenseStatus.REFERENCE_ONLY.name -> if (uriText == null) {
                requireImport(
                    source.attributionText.orEmpty().contains(REFERENCE_ONLY_MARKER),
                ) {
                    "source.attributionText must state $REFERENCE_ONLY_MARKER when source.sourceUri is null"
                }
            }
        }
    }

    private fun validateHttpsUri(uriText: String, fieldName: String) {
        text(uriText, fieldName, 2_000)
        val uri = try {
            URI(uriText)
        } catch (_: URISyntaxException) {
            throw DatabaseContractViolationException("$fieldName is not a valid URI")
        }
        requireImport(
            "https".equals(uri.scheme, ignoreCase = true) &&
                !uri.host.isNullOrBlank() &&
                uri.rawUserInfo == null &&
                uri.rawFragment == null,
        ) { "$fieldName must be an absolute HTTPS URI without credentials or fragments" }
        requireImport(!isPlaceholderHost(uri.host.orEmpty())) {
            "$fieldName must not use a placeholder host '${uri.host}'"
        }
    }

    /**
     * 占位域判定：`host` 小写、去尾点后，等于精确项、是精确项的子域，或以保留后缀结尾。
     *
     * 子域也拒收是因为占位域名常被写成 `www.example.edu`——只查精确等值会放过内容包里
     * 实际出现的那种形态（N1 用例即 `https://www.example.edu/material`）。
     * IPv6 的 `uri.host` 带方括号（`[::1]`），先去括号再比对。
     */
    private fun isPlaceholderHost(rawHost: String): Boolean {
        val host = rawHost.trim('[', ']').lowercase(Locale.ROOT).trimEnd('.')
        if (host.isEmpty()) return false
        if (host in placeholderHosts) return true
        if (placeholderHosts.any { host.endsWith(".$it") }) return true
        return placeholderSuffixes.any(host::endsWith)
    }

    private fun validateTopicHierarchyIsAcyclic(
        newNodes: Collection<KnowledgeNodeSeedRecord>,
        nodesById: Map<String, KnowledgeNodeSeedRecord>,
    ) {
        newNodes.asSequence()
            .filter { it.granularity == KnowledgeNodeGranularity.TOPIC.name }
            .forEach { start ->
                val visited = mutableSetOf<String>()
                var current: KnowledgeNodeSeedRecord? = start
                while (current != null) {
                    requireImport(visited.add(current.knowledgeNodeId)) {
                        "Knowledge topic hierarchy cannot contain a cycle"
                    }
                    current = current.parentKnowledgeNodeId?.let(nodesById::get)
                }
            }
    }

    private fun <T> List<T>.uniqueBy(
        selector: (T) -> String,
        label: String,
    ): Map<String, T> = associateBy(selector).also { indexed ->
        requireImport(indexed.size == size) { "Knowledge $label must be unique" }
    }

    private fun id(value: String, name: String) {
        requireImport(value.isNotBlank() && value == value.trim() && value.length <= 256) {
            "$name must be a trimmed non-blank string of at most 256 characters"
        }
        requireImport(value.none(Char::isISOControl)) { "$name contains control characters" }
    }

    private fun text(value: String, name: String, maxLength: Int) {
        requireImport(value.isNotBlank() && value.length <= maxLength) {
            "$name must be non-blank and at most $maxLength characters"
        }
        requireImport(
            value.none { character ->
                (character.isISOControl() && character !in "\n\r\t") ||
                    character.isBidirectionalControl()
            },
        ) {
            "$name contains unsupported control characters"
        }
    }

    private fun Char.isBidirectionalControl(): Boolean =
        this == '\u061C' || this == '\u200E' || this == '\u200F' ||
            this in '\u202A'..'\u202E' || this in '\u2066'..'\u2069'

    private fun known(value: String, name: String, allowed: Set<String>) {
        requireImport(value in allowed) { "$name has unknown value '$value'" }
    }

    private inline fun requireImport(condition: Boolean, message: () -> String) {
        if (!condition) throw DatabaseContractViolationException(message())
    }
}
