package com.tingyun.smartmistakebook.core.model

import java.security.MessageDigest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * 一条类型化证据引用（批次 40 / WS2 item 10）。
 *
 * **软引用，不建 FK**：`tutor_message` 等会话行会随会话生命周期删除；把错因 observation
 * 的外键挂到会话上，会让"删会话"与"保留错因审计行"互相拒斥（RESTRICT）或连带删除（CASCADE）。
 * 读侧遇到悬空引用必须容错并标注"证据已删除"，不得因此丢掉 observation 行——observation
 * 本身才是标签的权威来源。
 */
@Serializable
data class ErrorCauseEvidenceRef(
    val kind: ErrorCauseEvidenceKind,
    val id: String,
) {
    init {
        require(id.isNotBlank()) { "证据引用 id 不能为空" }
        require(id.length <= MAX_ID_CHARS) { "证据引用 id 过长：${id.length}" }
    }

    companion object {
        const val MAX_ID_CHARS: Int = 256
    }
}

/** `evidence_refs_json` 的稳定编解码；按写入顺序保存，便于审计逐字回读。 */
object ErrorCauseEvidenceRefsCodec {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }
    private val serializer = ListSerializer(ErrorCauseEvidenceRef.serializer())

    fun encode(refs: List<ErrorCauseEvidenceRef>): String = json.encodeToString(serializer, refs)

    fun decode(encoded: String): List<ErrorCauseEvidenceRef> =
        json.decodeFromString(serializer, encoded)
}

/**
 * 错因挂到 KC 时的归属确定性（R2 §5.3 P7）。
 *
 * 只有 [DIRECT] 允许写 `knowledge_node_id`；[INFERRED]/[UNKNOWN] 只写题级（NULL），
 * 否则会把"题级错因"错误分摊到多个节点。
 */
enum class ErrorCauseNodeCertainty {
    DIRECT,
    INFERRED,
    UNKNOWN,
}

/**
 * 错因的作用域 / 现行标签的唯一范围：`(learner, unit, revision, node)`。
 *
 * [activeKey] 是 `active_key` 唯一索引的写入值。**为什么用可空唯一活键而不是部分索引**：
 * Room 的 `@Index` 无法声明 `WHERE state='ACTIVE'`；迁移里建了谓词索引、实体里没有，
 * Room schema 校验会把它当漂移。仓内 `review_session.active_session_key` 已验证同一做法。
 *
 * 分隔符用 US（`\u001f`），id 不允许包含它——否则 `(a\u001f b, c)` 与 `(a, b\u001f c)`
 * 会拼出同一个活键，两条本应并存的标签互相覆盖。
 */
data class ErrorCauseScope(
    val learnerId: String,
    val practiceUnitId: String,
    val problemRevisionId: String,
    val knowledgeNodeId: String? = null,
) {
    init {
        require(learnerId.isNotBlank()) { "learnerId 不能为空" }
        require(practiceUnitId.isNotBlank()) { "practiceUnitId 不能为空" }
        require(problemRevisionId.isNotBlank()) { "problemRevisionId 不能为空" }
        require(knowledgeNodeId == null || knowledgeNodeId.isNotBlank()) {
            "knowledgeNodeId 只能为空或非空串（空串不是合法的题级表示）"
        }
        listOfNotNull(learnerId, practiceUnitId, problemRevisionId, knowledgeNodeId).forEach { id ->
            require(ACTIVE_KEY_SEPARATOR !in id) {
                "作用域 id 不能包含 US 分隔符：$id"
            }
        }
    }

    /** `learner US unit US revision US node`；题级标签的 node 段为空串。 */
    fun activeKey(): String = listOf(
        learnerId,
        practiceUnitId,
        problemRevisionId,
        knowledgeNodeId.orEmpty(),
    ).joinToString(ACTIVE_KEY_SEPARATOR.toString())

    companion object {
        const val ACTIVE_KEY_SEPARATOR: Char = '\u001f'

        /** 解析失败返回 null（读侧按"不是本作用域"处理，不抛给 UI）。 */
        fun fromActiveKey(key: String): ErrorCauseScope? {
            val parts = key.split(ACTIVE_KEY_SEPARATOR)
            if (parts.size != 4) return null
            return runCatching {
                ErrorCauseScope(
                    learnerId = parts[0],
                    practiceUnitId = parts[1],
                    problemRevisionId = parts[2],
                    knowledgeNodeId = parts[3].takeIf { it.isNotEmpty() },
                )
            }.getOrNull()
        }
    }
}

/**
 * 一条错因证据（批次 40 / WS2 item 10）。
 *
 * **它不是"事实"**：R2 的文献结论是"错因是推断，不是观测"（McNichols et al. 2023）。
 * 因此写入方必须给 [confidence]（不允许默认 0.5）、[channel] 与至少一条 [refs]；
 * 展示名不在这里、也不落库（产品合同：错因不得成为可见标签）。
 *
 * [rationaleMarkdown] 是**本地**理由：不得包含详情页 `user_note` 原文（备注原文不出网），
 * 也不保证可出网。该约束由调用方保证；模型层无法从字符串识别原文。
 */
data class ErrorCauseEvidence(
    val cause: ErrorCause,
    val detail: ErrorCauseDetail? = null,
    val confidence: Double,
    val channel: ErrorCauseChannel,
    val refs: List<ErrorCauseEvidenceRef>,
    val observedAtEpochMillis: Long,
    val rationaleMarkdown: String,
    val taxonomyVersion: String = ErrorCause.TAXONOMY_VERSION,
) {
    init {
        require(detail == null || detail.cause == cause) {
            "细码 ${detail?.code} 不属于顶层 ${cause.code}"
        }
        require(confidence.isFinite() && confidence in 0.0..1.0) {
            "置信度必须在 0..1：$confidence"
        }
        require(refs.isNotEmpty()) { "错因证据至少要有一条类型化引用" }
        require(refs.size == refs.toSet().size) { "证据引用不能重复：$refs" }
        require(observedAtEpochMillis >= 0) { "观察时间不能为负" }
        require(rationaleMarkdown.isNotBlank()) { "错因理由不能为空" }
        require(rationaleMarkdown.length <= MAX_RATIONALE_CHARS) {
            "错因理由过长：${rationaleMarkdown.length}"
        }
        require(taxonomyVersion.isNotBlank()) { "taxonomyVersion 不能为空" }
    }

    companion object {
        const val MAX_RATIONALE_CHARS: Int = 4_000
    }
}

/**
 * 提取器输出的**候选**：状态恒为 [ErrorCauseObservationStatus.PROPOSED]。
 *
 * 候选不落账是结构性的：提取器是纯函数，没有数据库依赖；只有 D-1c 门（默认关闭）能把
 * 候选升为 binding。见 `ErrorCauseGate` / `ErrorCauseRetractor`。
 */
data class ErrorCauseCandidate(
    val scope: ErrorCauseScope,
    val attemptId: String?,
    val evidence: ErrorCauseEvidence,
    val extractorVersion: String,
    val knowledgeNodeCertainty: ErrorCauseNodeCertainty = ErrorCauseNodeCertainty.UNKNOWN,
) {
    init {
        require(extractorVersion.isNotBlank()) { "extractorVersion 不能为空" }
        require(attemptId == null || attemptId.isNotBlank()) { "attemptId 只能为空或非空串" }
    }

    val status: ErrorCauseObservationStatus get() = ErrorCauseObservationStatus.PROPOSED
}

/**
 * observation 的内容寻址 id（R2 §4.4）。
 *
 * 同一 `(作用域, attempt, cause/detail, channel, extractorVersion, 证据引用集合)` 重放得到
 * 同一个 id；DAO 用主键冲突把重放变成幂等读回，而不是第二行。**不**把
 * confidence/rationale/observedAt 纳入：这些是同一事实的呈现，不是身份；把它们纳入会让
 * 重跑提取器时因时间戳不同而写出无限多条重复证据。
 *
 * 每段先写长度再写内容（长度前缀），避免 `("ab","c")` 与 `("a","bc")` 拼出同一摘要。
 */
object ErrorCauseIdentity {
    private const val ID_PREFIX = "ecobs-"

    fun observationId(
        scope: ErrorCauseScope,
        attemptId: String?,
        cause: ErrorCause,
        detail: ErrorCauseDetail?,
        channel: ErrorCauseChannel,
        extractorVersion: String,
        refs: List<ErrorCauseEvidenceRef>,
        supersedesObservationId: String? = null,
    ): String {
        require(extractorVersion.isNotBlank()) { "extractorVersion 不能为空" }
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = StringBuilder()
        fun appendField(value: String) {
            buffer.append(value.length).append(':').append(value)
        }
        appendField("error-cause-observation-v1")
        appendField(scope.learnerId)
        appendField(scope.practiceUnitId)
        appendField(scope.problemRevisionId)
        appendField(scope.knowledgeNodeId.orEmpty())
        appendField(attemptId.orEmpty())
        appendField(cause.code)
        appendField(detail?.code.orEmpty())
        appendField(channel.name)
        appendField(extractorVersion)
        appendField(supersedesObservationId.orEmpty())
        refs.sortedWith(compareBy({ it.kind.name }, { it.id })).forEach { ref ->
            appendField(ref.kind.name)
            appendField(ref.id)
        }
        digest.update(buffer.toString().toByteArray(Charsets.UTF_8))
        return ID_PREFIX + digest.digest().joinToString(separator = "") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
    }

    fun observationId(candidate: ErrorCauseCandidate): String = observationId(
        scope = candidate.scope,
        attemptId = candidate.attemptId,
        cause = candidate.evidence.cause,
        detail = candidate.evidence.detail,
        channel = candidate.evidence.channel,
        extractorVersion = candidate.extractorVersion,
        refs = candidate.evidence.refs,
        supersedesObservationId = null,
    )
}
