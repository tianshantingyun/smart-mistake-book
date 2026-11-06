package com.tingyun.smartmistakebook.core.data.telemetry

/**
 * 同意状态（R5 §3.1）：用三态枚举而不是 `Boolean enabled`，因为布尔字段一旦被某处默认写成
 * `true` 就很难审计。`UNKNOWN` 是首次安装默认值，等价于 OFF；`WITHDRAWN` 也等价于 OFF。
 */
enum class TelemetryConsentState(val wireName: String) {
    UNKNOWN("unknown"),
    GRANTED("granted"),
    WITHDRAWN("withdrawn"),
}

enum class TelemetryAgeBand(val wireName: String) {
    UNDER_14("under_14"),
    AGE_14_17("age_14_17"),
    AGE_18_PLUS("age_18_plus"),
    UNDISCLOSED("undisclosed"),
}

data class TelemetryPolicy(val version: String, val hash: String) {
    init {
        require(version.isNotBlank()) { "policy version must not be blank" }
        require(hash.isNotBlank()) { "policy hash must not be blank" }
    }
}

/**
 * 同意快照。默认构造 = 首次安装 = `UNKNOWN`，因此 `isEnabled` 为 false；没有任何默认值能
 * 让收集在未经用户动作时打开。
 */
data class TelemetryConsentSnapshot(
    val state: TelemetryConsentState = TelemetryConsentState.UNKNOWN,
    val policyVersion: String? = null,
    val policyHash: String? = null,
    val grantedAtEpochMillis: Long? = null,
    val withdrawnAtEpochMillis: Long? = null,
    val ageBand: TelemetryAgeBand? = null,
    val guardianConfirmed: Boolean = false,
) {
    val isEnabled: Boolean get() = state == TelemetryConsentState.GRANTED
}

/**
 * 构建配置：默认关闭；`endpoint` 默认为空（v1 只做本机指标面，不建立上传通道）。
 */
data class TelemetryBuildConfig(
    val telemetryBuildEnabled: Boolean = false,
    val endpoint: String = "",
)

/**
 * 纯函数同意门（R5 §3.2）。不读网络/数据库/系统设置，方便单元测试。
 *
 * - [isCollectionAllowed]：本地聚合/落盘门。要求 GRANTED + 构建开关 + 政策哈希一致 + 年龄门。
 * - [isUploadAllowed]：上传门。在收集门之上再要求已配置端点与 INTERNET 权限；v1 端点为空，
 *   因此结构上不会上传。
 *
 * 任一层失败即 false（fail-closed）；不得“默认放行”。
 */
object TelemetryGate {
    fun ageGateSatisfied(ageBand: TelemetryAgeBand?, guardianConfirmed: Boolean): Boolean = when (ageBand) {
        TelemetryAgeBand.UNDER_14 -> guardianConfirmed
        // 14–17 仍受未成年人条例约束；无法可靠验证监护人时采用保守口径（R5 §3.4）。
        TelemetryAgeBand.AGE_14_17 -> guardianConfirmed
        TelemetryAgeBand.AGE_18_PLUS -> true
        TelemetryAgeBand.UNDISCLOSED, null -> false
    }

    fun isCollectionAllowed(
        consent: TelemetryConsentSnapshot,
        build: TelemetryBuildConfig,
        currentPolicy: TelemetryPolicy,
    ): Boolean =
        consent.state == TelemetryConsentState.GRANTED &&
            build.telemetryBuildEnabled &&
            consent.policyVersion == currentPolicy.version &&
            consent.policyHash == currentPolicy.hash &&
            ageGateSatisfied(consent.ageBand, consent.guardianConfirmed)

    fun isUploadAllowed(
        consent: TelemetryConsentSnapshot,
        build: TelemetryBuildConfig,
        currentPolicy: TelemetryPolicy,
        hasInternetPermission: Boolean,
    ): Boolean =
        isCollectionAllowed(consent, build, currentPolicy) &&
            build.endpoint.isNotBlank() &&
            hasInternetPermission
}
