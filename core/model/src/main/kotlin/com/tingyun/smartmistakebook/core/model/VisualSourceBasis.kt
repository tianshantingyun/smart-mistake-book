package com.tingyun.smartmistakebook.core.model

import java.nio.charset.StandardCharsets

/**
 * 视觉生成的**源证据基准**（X-02；契约 `visual-generation-contract.md` §3.1 的冻结载体）。
 *
 * 契约原文："先冻结VisualSourceBasis（subject/revision、canonical引用/SHA、允许披露范围），
 * 各后端取得其必需证据：JS产码模型需实际看到原图或足够且可追溯的结构化源事实；
 * IMAGE_MODEL编辑执行需收到已授权原图。不能把本地有图当参与生成的模型已看图。"
 *
 * **它消灭的具体失败**：宿主本地有图（canonical 资产在库）却被当成"参与生成的模型已看图"，
 * 以及"授权不足"无从判定——改前 [FigureGenerationRequest.source] 只有字节证据，没有
 * "这个 subject 允许把什么披露给哪个后端"的宿主决定。本类型是宿主在派发前冻结的决定：
 * - 引用面：[canonicalAssetId] + [contentSha256]（成对，缺一即拒）；
 * - 披露面：[allowsImageDisclosure]（原图字节可随派发出网）与
 *   [allowsStructuredFactsDisclosure]（可追溯结构化源事实可交给产码/规划后端）；
 * - 事实面：[structuredFacts] 有界文本 + [structuredFactsSha256] 追溯摘要（同一份事实
 *   重放命中同一摘要，"可追溯"不是口头承诺）。
 *
 * **"已发送"不在这里**：冻结基准只声明"允许披露什么"，永远不声明"模型已经看过什么"。
 * 实际发送的记录是 [VisualSourceSeenRecord]，只能由派发路径在源图字节真的出网后产出
 * （core:data 协调器；持久孪生 = `figure_generation_attempt` 行的 `reservation_id` +
 * `source_sha256`）。"同批准备不冒充已发送"由这一分离保证：基准构造得再完整，也造不出
 * seen 记录。
 *
 * 字节不进本类型：像素经 [FigureSourceEvidence] 携带并在策略门里与本基准的 SHA 绑定核对
 * （`FigureGenerationPolicy.validate` 的源证据门）。本类型不是模型输出 schema 的一部分。
 */
data class VisualSourceBasis(
    /** 题目主体 id（讲题会话/修订/净化作业/解析作业的宿主身份）；不参与模型输入。 */
    val subjectId: String,
    /** 题面版本号；0 = 宿主没有版本化题面（讲题上下文 revisionNumber 从 1 起）。 */
    val revision: Int,
    /** canonical 资产引用（`canonical_source_asset.source_asset_id`）；无原题资产 = null。 */
    val canonicalAssetId: String?,
    /** 引用内容的 SHA-256（小写 hex-64），与 [canonicalAssetId] 成对。 */
    val contentSha256: String?,
    /** 足够且可追溯的结构化源事实（有界文本）；无 = null。 */
    val structuredFacts: String? = null,
    /** 允许把已授权原图字节交给执行后端（IMAGE_MODEL 的派发载荷）。 */
    val allowsImageDisclosure: Boolean = false,
    /** 允许把结构化源事实交给产码/规划后端。 */
    val allowsStructuredFactsDisclosure: Boolean = false,
) {
    init {
        require(subjectId.isNotBlank()) { "A visual source basis needs a non-blank subject" }
        require(revision >= 0) { "A visual source basis revision must not be negative" }
        require((canonicalAssetId == null) == (contentSha256 == null)) {
            "A visual source basis canonical reference and sha must be paired"
        }
        require(canonicalAssetId == null || canonicalAssetId.isNotBlank()) {
            "A visual source basis canonical reference must not be blank"
        }
        require(
            contentSha256 == null ||
                (contentSha256.length == SHA_256_HEX_CHARS && contentSha256.all(Char::isLowerHexDigit)),
        ) { "A visual source basis sha must be a lowercase SHA-256" }
        require(
            !allowsImageDisclosure || (canonicalAssetId != null && contentSha256 != null),
        ) { "Image disclosure requires a frozen canonical reference/sha" }
        require(structuredFacts == null || (structuredFacts.isNotBlank() && structuredFacts.length <= MAX_STRUCTURED_FACTS_CHARS)) {
            "Structured source facts must be non-blank and within the ${MAX_STRUCTURED_FACTS_CHARS} char budget"
        }
        require(!allowsStructuredFactsDisclosure || structuredFacts != null) {
            "Structured facts disclosure requires frozen facts"
        }
    }

    /** 事实的追溯摘要（小写 hex-64）；无事实 = null。 */
    val structuredFactsSha256: String?
        get() = structuredFacts?.let { facts ->
            figureSha256Hex(facts.toByteArray(StandardCharsets.UTF_8))
        }

    override fun toString(): String =
        "VisualSourceBasis(subject=$subjectId, revision=$revision, asset=$canonicalAssetId, " +
            "sha=$contentSha256, factsSha=${structuredFactsSha256 ?: "-"}, " +
            "imageDisclosure=$allowsImageDisclosure, factsDisclosure=$allowsStructuredFactsDisclosure)"

    companion object {
        /**
         * 事实文本的字符预算（与 [FigureGenerationRequest.MAX_PROBLEM_CONTEXT_CHARS] 同值同理由：
         * 有界注入，不回显进日志/诊断）。
         */
        const val MAX_STRUCTURED_FACTS_CHARS: Int = 2_000
    }
}

/**
 * "源证据真的随一次派发发出去了"的记录（X-02；契约 :39 "实际发送后记录
 * sourceSeenDispatchId/字节摘要"）。
 *
 * **它消灭的具体失败**：改前"这次派发带没带源图"只活在协程里——没有源字节的纯文生图
 * attempt 与带源编辑 attempt 回给调用方的都是同一个 attemptId，调用方无法区分"模型看过
 * 原图"与"模型只看过文字"。本记录把两者分开：[sourceSeenDispatchId] 是携带源图字节的
 * attempt 行 id，[contentSha256] 是实际出网字节的摘要。
 *
 * 生产唯一产出点 = core:data 协调器在 `markDispatched` 之后（源图字节已进入派发载荷；
 * 与账本 used=1 的记账点同一语义）。持久孪生 = `figure_generation_attempt` 行
 * （`reservation_id` + `source_sha256`），台账没写成（无 attemptId）时协调器不产出本记录
 * ——没有可指向的持久 attempt，就没有"已发送"证据。冻结的
 * [VisualSourceBasis] 造不出本记录："同批准备不冒充已发送"。
 */
data class VisualSourceSeenRecord(
    /** 携带源图字节的那次 attempt 的 id（与 [FigureGenerationOutcome.attemptId] 同值）。 */
    val sourceSeenDispatchId: String,
    /** 实际出网源图字节的 SHA-256（小写 hex-64）。 */
    val contentSha256: String,
) {
    init {
        require(sourceSeenDispatchId.isNotBlank()) { "A seen record needs its dispatch id" }
        require(
            contentSha256.length == SHA_256_HEX_CHARS && contentSha256.all(Char::isLowerHexDigit),
        ) { "A seen record digest must be a lowercase SHA-256" }
    }
}
