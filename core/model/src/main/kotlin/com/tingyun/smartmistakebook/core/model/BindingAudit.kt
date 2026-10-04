package com.tingyun.smartmistakebook.core.model

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.WeekFields
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * KF-29 绑定正确性监测：一条抽样绑定的**快照**（阶段 3C 批 1，schema 61→62）。
 *
 * **它消灭的失败**：改前"模型/用户把这道题绑到了哪些知识点、依据是什么、用的是哪个模型版本"
 * 只活在写入那一刻的内存里——事后没有任何地方能回答"这条绑定对不对"。抽检员只能看到绑定表
 * 的最终行（题 → 节点），看不到当时的题面、模型给出的理由与逐条绑定强度，复核无从谈起，
 * 绑定错误率也就永远无法测量。快照把"复核需要的最小现场"固化在抽样行里。
 *
 * **口径**：
 * - 一条快照 = 一次**新绑定**（某 practice unit 的绑定集合被确认写入），不是单个知识点行；
 * - 快照是**有界**的审计辅助，不是完整归档：题面超出 [BindingAuditSnapshotCodec.MAX_QUESTION_CHARS]
 *   时截断并置 [questionTruncated]（复核屏必须如实显示截断，不能让审阅者以为看到了全文）；
 *   绑定点/分类/步骤证据各有条数上限（写入方截断，见 `buildBindingAuditSampleCommand`）。
 * - 完整绑定事实仍在 `practice_unit_knowledge_binding` 等表；快照只是"当时那一版"的切片。
 */
@Serializable
data class BindingAuditSnapshot(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    /** 学科（`SubjectKind.name`），聚合维度之一。 */
    val subject: String,
    /** 模型版本（组织任务输出自报；用户离线纠正没有模型版本 → null）。聚合维度之二。 */
    val modelVersion: String? = null,
    /** 供应方 id（可选上下文；模型版本的可读性补充，不参与聚合分组）。 */
    val providerId: String? = null,
    /** 绑定权威来源（`BindingAcceptanceSource.name`：LOCAL_POLICY_ACCEPTED / USER_CORRECTED）。 */
    val acceptanceSource: String,
    /** 本次绑定被确认的时刻（抽样周窗口按它计算）。 */
    val acceptedAtEpochMillis: Long,
    val problemId: String,
    val problemRevisionId: String,
    val practiceUnitId: String,
    /** 模型当时看到的题面投影（可能截断，见 [questionTruncated]）。 */
    val questionMarkdown: String,
    val questionTruncated: Boolean = false,
    /** 绑定点：本次确认写入的每个知识点绑定（含强度与粒度）。 */
    val bindings: List<BindingAuditSnapshotBinding> = emptyList(),
    /** 分类（章节/知识点标签）与模型的理由——"为什么绑到它"的原文依据。 */
    val classifications: List<BindingAuditSnapshotClassification> = emptyList(),
    /** 模型对解题步骤的拆分与步骤→知识点的对应（原文依据的步骤级切片）。 */
    val stepEvidence: List<BindingAuditSnapshotStep> = emptyList(),
) {
    init {
        require(schemaVersion >= 1) { "Binding audit snapshot schema version must be positive" }
        require(subject.isNotBlank()) { "Binding audit snapshot subject must not be blank" }
        require(acceptanceSource.isNotBlank()) { "Binding audit snapshot acceptance source must not be blank" }
        require(acceptedAtEpochMillis > 0) { "Binding audit snapshot acceptance time must be positive" }
        require(problemId.isNotBlank() && problemRevisionId.isNotBlank() && practiceUnitId.isNotBlank()) {
            "Binding audit snapshot must identify its problem revision and practice unit"
        }
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

/** 一个绑定点（写入 `practice_unit_knowledge_binding` 的一行）。 */
@Serializable
data class BindingAuditSnapshotBinding(
    val knowledgeNodeId: String,
    /** 复核屏可读名；节点上下文缺失时回退为节点 id（不许编造名字）。 */
    val displayName: String,
    val granularity: String,
    val strength: Double,
    val taxonomyVersion: String,
)

/** 一条已确认分类 + 模型当时的理由。 */
@Serializable
data class BindingAuditSnapshotClassification(
    val dimension: String,
    val labelId: String,
    val displayName: String,
    /** 模型/用户给出的理由；历史快照可能没有（缺省 null，不编造）。 */
    val rationaleMarkdown: String? = null,
)

/** 一条步骤级证据：步骤摘要 + 它对应到的已绑定知识点。 */
@Serializable
data class BindingAuditSnapshotStep(
    val stepOrdinal: Int,
    val stepSummaryMarkdown: String,
    val knowledgeNodeIds: List<String> = emptyList(),
)

/**
 * 快照的编解码与预算。解码失败/版本不认识一律返回 null（聚合口径把它计入"不可读"，
 * 不许静默丢掉一行——那正是本管线要消灭的"看不见"）。
 */
object BindingAuditSnapshotCodec {
    /** 单条快照的编码预算：有界是硬约束（表里最多每周每科 5 条，仍不许无限膨胀）。 */
    const val MAX_ENCODED_CHARS = 128_000

    /** 题面入快照的字符上限；超出即截断并在快照上如实标记。 */
    const val MAX_QUESTION_CHARS = 8_000

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(snapshot: BindingAuditSnapshot): String {
        val encoded = json.encodeToString(BindingAuditSnapshot.serializer(), snapshot)
        require(encoded.length <= MAX_ENCODED_CHARS) {
            "Binding audit snapshot exceeds its encoded budget"
        }
        return encoded
    }

    /** 解码当前版本的快照；格式坏、超预算或版本不认识 → null。 */
    fun decode(encoded: String): BindingAuditSnapshot? = runCatching {
        require(encoded.length <= MAX_ENCODED_CHARS)
        json.decodeFromString(BindingAuditSnapshot.serializer(), encoded)
            .takeIf { snapshot -> snapshot.schemaVersion == BindingAuditSnapshot.CURRENT_SCHEMA_VERSION }
    }.getOrNull()
}

/**
 * 抽样配额口径（KF-29）：**每科每周首 N 条新绑定**。
 *
 * - 周窗口 = 以 UTC 计算的 ISO-8601 周（`YYYY-Www`）。它不是学生可见的日历口径，只是配额窗口：
 *   选 UTC 是为了让"同一次写入 → 同一个周键"与设备时区无关、可复现、可测试。
 * - 配额键 [quotaPrefix] 同时是抽样行 `sample_id` 的前缀：**一个学科一周一个前缀**，
 *   入队时的计数与插入在同一事务里完成（见 `BindingAuditSampleDao.record`），
 *   因此并发写入下也恰好封顶 N，不会因"先读后写"超发。
 * - [sampleId] 对同一 (题版本, 绑定集合) 内容寻址：确认重放得到同一个 id，重复入队被主键忽略，
 *   不会把同一次绑定记成两条样本。
 */
object BindingAuditSampling {
    const val DEFAULT_SAMPLES_PER_SUBJECT_WEEK = 5

    private const val NAMESPACE = "binding-audit"

    fun quotaPrefix(subject: String, epochMillis: Long): String {
        require(subject.isNotBlank()) { "Binding audit subject must not be blank" }
        require(subject.none { it == ':' }) { "Binding audit subject must not contain a colon" }
        require(epochMillis > 0) { "Binding audit sampling time must be positive" }
        val date = Instant.ofEpochMilli(epochMillis).atZone(ZoneOffset.UTC).toLocalDate()
        val weekFields = WeekFields.ISO
        val weekBasedYear = date.get(weekFields.weekBasedYear())
        val weekOfYear = date.get(weekFields.weekOfWeekBasedYear())
        return "$NAMESPACE:$subject:$weekBasedYear-W${weekOfYear.toString().padStart(2, '0')}"
    }

    /** 同一配额键下按绑定集合内容寻址的样本 id（前缀 = [quotaPrefix]，供配额计数）。 */
    fun sampleId(
        quotaPrefix: String,
        practiceUnitId: String,
        problemRevisionId: String,
        bindingIds: Collection<String>,
    ): String {
        require(quotaPrefix.startsWith("$NAMESPACE:")) { "Binding audit quota prefix is invalid" }
        require(practiceUnitId.isNotBlank()) { "Binding audit practice unit must not be blank" }
        require(problemRevisionId.isNotBlank()) { "Binding audit problem revision must not be blank" }
        require(bindingIds.isNotEmpty()) { "A binding audit sample needs at least one binding" }
        require(bindingIds.none(String::isBlank)) { "Binding audit binding ids must not be blank" }
        val canonical = buildString {
            append(practiceUnitId).append('\u001F')
            append(problemRevisionId).append('\u001F')
            bindingIds.sorted().forEach { id -> append(id).append('\u001F') }
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(StandardCharsets.UTF_8))
            .take(16)
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
        return "$quotaPrefix:$digest"
    }
}
