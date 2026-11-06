package com.tingyun.smartmistakebook.core.data.telemetry

/**
 * 维度枚举（R5 §2.1：所有维度都是枚举，禁止题面/节点 ID/文件名/自由文本）。
 * 这些枚举的 `wireName` 是批次 JSON 中唯一允许出现的属性值。
 */
enum class TelemetryStage(val wireName: String) {
    FEATURE_EXTRACT("feature_extract"),
    SQL_RECALL("sql_recall"),
    DENSE_ENCODE("dense_encode"),
    DENSE_SEARCH("dense_search"),
    RERANK("rerank"),
    PREREQ_HOP("prereq_hop"),
    MATERIAL_LOAD("material_load"),
    RENDER("render"),
}

enum class TelemetryOutcome(val wireName: String) {
    OK("ok"),
    EMPTY("empty"),
    ERROR("error"),
    CANCELLED("cancelled"),
}

enum class TelemetryTrigger(val wireName: String) {
    TUTOR("tutor"),
    REVIEW("review"),
    PREDISCLOSURE("predisclosure"),
}

enum class TelemetrySubject(val wireName: String) {
    CHINESE("chinese"),
    MATH("math"),
    ENGLISH("english"),
    PHYSICS("physics"),
    CHEMISTRY("chemistry"),
    BIOLOGY("biology"),
    HISTORY("history"),
    GEOGRAPHY("geography"),
    POLITICS("politics"),
}

enum class TelemetryFallback(val wireName: String) {
    TOPIC("topic"),
}

enum class TelemetryReason(val wireName: String) {
    NO_FEATURES("no_features"),
    NO_CANDIDATES("no_candidates"),
    KB_NOT_READY("kb_not_ready"),
    LOAD_FAILED("load_failed"),
    CANCELLED("cancelled"),
}

enum class TelemetryErrorClass(val wireName: String) {
    SQLITE("SQLITE"),
    IO("IO"),
    DENSE_MODEL("DENSE_MODEL"),
    PARSE("PARSE"),
    UNKNOWN("UNKNOWN"),
}

enum class TelemetryDropReason(val wireName: String) {
    QUEUE_FULL("queue_full"),
    DISK_CAP("disk_cap"),
    SAMPLING("sampling"),
}

enum class TelemetryFlavor(val wireName: String) {
    STRICT_OFFLINE("strictOffline"),
    LOCAL_FIRST("localFirst"),
}

enum class TelemetryRamClass(val wireName: String) {
    LOW("low"),
    MID("mid"),
    HIGH("high"),
}

/**
 * 属性袋：字段全部是可空枚举/布尔，没有任何 `String` 字段可以塞自由文本。
 * [toWireMap] 只输出非空项，键集合固定（[ALLOWED_KEYS]），测试据此断言白名单。
 */
data class TelemetryAttributes(
    val stage: TelemetryStage? = null,
    val outcome: TelemetryOutcome? = null,
    val coldStart: Boolean? = null,
    val subject: TelemetrySubject? = null,
    val trigger: TelemetryTrigger? = null,
    val fallback: TelemetryFallback? = null,
    val reason: TelemetryReason? = null,
    val kbReady: Boolean? = null,
    val errorClass: TelemetryErrorClass? = null,
    val retryable: Boolean? = null,
    val dropReason: TelemetryDropReason? = null,
) {
    init {
        // `reason` 是同一个 wire key：跳过/空上下文原因与丢弃原因不得同时出现。
        require(reason == null || dropReason == null) {
            "reason and dropReason share the wire key 'reason' and cannot both be set"
        }
    }

    fun toWireMap(): Map<String, String> = buildMap {
        stage?.let { put("stage", it.wireName) }
        outcome?.let { put("outcome", it.wireName) }
        coldStart?.let { put("cold_start", it.toString()) }
        subject?.let { put("subject", it.wireName) }
        trigger?.let { put("trigger", it.wireName) }
        fallback?.let { put("fallback", it.wireName) }
        reason?.let { put("reason", it.wireName) }
        kbReady?.let { put("kb_ready", it.toString()) }
        errorClass?.let { put("error_class", it.wireName) }
        retryable?.let { put("retryable", it.toString()) }
        dropReason?.let { put("reason", it.wireName) }
    }

    companion object {
        /** 批次 JSON 里允许出现的全部属性键；测试与批次构造都走这一份。 */
        val ALLOWED_KEYS: Set<String> = setOf(
            "stage",
            "outcome",
            "cold_start",
            "subject",
            "trigger",
            "fallback",
            "reason",
            "kb_ready",
            "error_class",
            "retryable",
        )
    }
}
