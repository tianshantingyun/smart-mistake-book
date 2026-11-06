package com.tingyun.smartmistakebook.core.data.telemetry

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 本地批次 JSON（R5 §2.2）。字段全部是固定类型/枚举；没有任何自由文本槽位。
 *
 * `batch_id` 每批随机、批次之间不可关联；`window` 只到小时；不含设备 ID/IP/位置。
 */
@Serializable
data class TelemetryBatch(
    val schema: Int = SCHEMA_VERSION,
    @SerialName("batch_id") val batchId: String,
    val window: TelemetryWindow,
    val app: TelemetryApp,
    val env: TelemetryEnvironment,
    val consent: TelemetryConsentDescriptor,
    val sampling: TelemetrySampling,
    val metrics: List<TelemetryMetricSnapshot>,
) {
    init {
        require(schema == SCHEMA_VERSION) { "unsupported telemetry schema: $schema" }
        require(batchId.isNotBlank()) { "batchId must not be blank" }
        require(window.startUtc.isNotBlank()) { "window.start_utc must not be blank" }
        require(window.endUtc.isNotBlank()) { "window.end_utc must not be blank" }
        require(app.versionName.isNotBlank()) { "app.version_name must not be blank" }
        require(sampling.rate in 0.0..1.0) { "sampling.rate must be within [0, 1]: ${sampling.rate}" }
        require(consent.policyVersion.isNotBlank()) { "consent.policy_version must not be blank" }
        require(consent.policyHash.isNotBlank()) { "consent.policy_hash must not be blank" }
    }

    companion object {
        const val SCHEMA_VERSION = 1
        const val CONSENT_SCOPE_PRODUCT_METRICS = "product_metrics"
    }
}

@Serializable
data class TelemetryWindow(
    @SerialName("start_utc") val startUtc: String,
    @SerialName("end_utc") val endUtc: String,
)

@Serializable
data class TelemetryApp(
    @SerialName("version_name") val versionName: String,
    @SerialName("version_code") val versionCode: Long,
    val flavor: String,
) {
    init {
        require(TelemetryFlavor.entries.any { it.wireName == flavor }) {
            "app.flavor must be one of ${TelemetryFlavor.entries.map { it.wireName }}: $flavor"
        }
    }
}

@Serializable
data class TelemetryEnvironment(
    val api: Int,
    @SerialName("ram_class") val ramClass: String,
) {
    init {
        require(ramClass in TelemetryRamClass.entries.map { it.wireName }) {
            "env.ram_class must be one of ${TelemetryRamClass.entries.map { it.wireName }}: $ramClass"
        }
    }
}

@Serializable
data class TelemetryConsentDescriptor(
    @SerialName("policy_version") val policyVersion: String,
    @SerialName("policy_hash") val policyHash: String,
    val scope: String = TelemetryBatch.CONSENT_SCOPE_PRODUCT_METRICS,
) {
    init {
        require(scope == TelemetryBatch.CONSENT_SCOPE_PRODUCT_METRICS) {
            "consent.scope must be '${TelemetryBatch.CONSENT_SCOPE_PRODUCT_METRICS}': $scope"
        }
    }
}

@Serializable
data class TelemetrySampling(val rate: Double)

/**
 * 单条指标的批次表示。histogram 带 bounds/counts/count/sum/min/max；counter/gauge 带 value。
 * `attrs` 的键必须落在 [TelemetryAttributes.ALLOWED_KEYS] 内。
 */
@Serializable
data class TelemetryMetricSnapshot(
    val name: String,
    val type: String,
    val unit: String,
    val bounds: List<Long>? = null,
    val counts: List<Long>? = null,
    val count: Long? = null,
    val sum: Long? = null,
    val min: Long? = null,
    val max: Long? = null,
    val value: Double? = null,
    val attrs: Map<String, String> = emptyMap(),
) {
    init {
        val unknownKeys = attrs.keys - TelemetryAttributes.ALLOWED_KEYS
        require(unknownKeys.isEmpty()) { "unknown telemetry attribute keys: $unknownKeys" }
        when (type) {
            TelemetryMetricType.COUNTER.wireName,
            TelemetryMetricType.GAUGE.wireName -> {
                require(value != null) { "$name ($type) needs a value" }
                require(bounds == null && counts == null) { "$name ($type) must not carry histogram fields" }
            }

            TelemetryMetricType.HISTOGRAM.wireName -> {
                val declaredBounds = requireNotNull(bounds) { "$name (histogram) needs bounds" }
                val declaredCounts = requireNotNull(counts) { "$name (histogram) needs counts" }
                require(declaredBounds.size <= TelemetryBucketBounds.MAX_BUCKET_BOUNDARIES) {
                    "$name declares ${declaredBounds.size} boundaries; max is " +
                        TelemetryBucketBounds.MAX_BUCKET_BOUNDARIES
                }
                require(declaredCounts.size == declaredBounds.size + 1) {
                    "$name counts must have bounds.size + 1 entries"
                }
                val declaredCount = requireNotNull(count) { "$name (histogram) needs count" }
                require(declaredCounts.sum() == declaredCount) { "$name count must equal the sum of counts" }
                require(value == null) { "$name (histogram) must not carry a scalar value" }
            }

            else -> error("unknown telemetry metric type: $type")
        }
    }
}

/** 批次编码器：`encodeDefaults=true` 让 schema/scope 显式出现；null 字段不落盘。 */
object TelemetryJson {
    val format: Json = Json {
        encodeDefaults = true
        explicitNulls = false
    }

    fun encode(batch: TelemetryBatch): String = format.encodeToString(TelemetryBatch.serializer(), batch)

    fun decode(raw: String): TelemetryBatch = format.decodeFromString(TelemetryBatch.serializer(), raw)
}

/**
 * 落盘批次的元信息（来自调用方，不在遥测内部读系统设置）。
 * 没有设备 ID/IP/位置字段；`batchId` 默认 UUIDv4，每批独立。
 */
data class TelemetryBatchMetadata(
    val batchId: String,
    val windowStartUtc: String,
    val windowEndUtc: String,
    val appVersionName: String,
    val appVersionCode: Long,
    val flavor: TelemetryFlavor,
    val apiLevel: Int,
    val ramClass: TelemetryRamClass,
    val samplingRate: Double,
) {
    init {
        require(batchId.isNotBlank()) { "batchId must not be blank" }
        require(windowStartUtc.isNotBlank()) { "windowStartUtc must not be blank" }
        require(windowEndUtc.isNotBlank()) { "windowEndUtc must not be blank" }
        require(appVersionName.isNotBlank()) { "appVersionName must not be blank" }
        require(appVersionCode >= 0L) { "appVersionCode must not be negative" }
        require(apiLevel > 0) { "apiLevel must be positive" }
        require(samplingRate in 0.0..1.0) { "samplingRate must be within [0, 1]: $samplingRate" }
    }
}
