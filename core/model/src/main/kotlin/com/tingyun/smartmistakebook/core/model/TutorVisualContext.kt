package com.tingyun.smartmistakebook.core.model

import java.nio.charset.StandardCharsets
import kotlinx.serialization.Serializable

/**
 * 4E.1 WG-04b：向下一次**已有预算内**模型回合携带的**有界**视觉上下文（请求 schema 18）。
 *
 * 权威约束见 `docs/current/visual-generation-contract.md` §7.1：
 * - 只携带当前题最近一份程序源码、当前 state、截断诊断与能力版本；
 * - 不把历史全部源码累积回喂；
 * - 源码 64 KiB、state 16 KiB、诊断 2 KiB，且受原请求总 token/字节预算约束；放不下时不自动重试。
 *
 * 消灭的具体失败：修复回合里模型看不到上一份程序/状态/截断原因，只能凭对话猜；或者反过来把
 * 整段历史源码塞进 prompt，挤掉题目与终答预算。本类型是**模型可见**字段的一等载体，因此：
 * - 非空即参与 `ModelTaskLogicalOperationFingerprint` / `ModelTaskFingerprint`（另一次逻辑输入）；
 * - null 时由 `@EncodeDefault(NEVER)` 完全省略键，旧 schema 17 行的编码与指纹逐位不变。
 *
 * 本波次（4E.1）JS 运行库尚未过门，生产入口不构造本对象；它先作为契约与指纹边界存在，
 * 不允许拿它广告或执行 JS。
 */
@Serializable
data class TutorVisualContext(
    /**
     * 生图/视觉能力版本（例如 `figure-generation-policy-v1`）。模型据此知道当前协商的是哪一版
     * 能力，不得把旧诊断当成本版规则。
     */
    val capabilityVersion: String,
    /** 当前题最近一份程序的原始源码（UTF-8 ≤ 64 KiB）；null = 本回合没有源码可带。 */
    val sourceJs: String? = null,
    /** 当前 state 的 JSON 文本（UTF-8 ≤ 16 KiB）；null = 没有可续用的状态。 */
    val stateJson: String? = null,
    /** 截断/失败诊断（≤ 2 KiB 字符）；null = 没有诊断。 */
    val diagnostics: String? = null,
) {
    init {
        require(capabilityVersion.isNotBlank()) {
            "A tutor visual context needs a capability version"
        }
        require(capabilityVersion.length <= MAX_CAPABILITY_VERSION_CHARS) {
            "A tutor visual capability version must be at most $MAX_CAPABILITY_VERSION_CHARS chars"
        }
        require(capabilityVersion.none(Char::isISOControl)) {
            "A tutor visual capability version must not carry control characters"
        }
        require(sourceJs != null || stateJson != null || diagnostics != null) {
            "An empty tutor visual context must be null, not an empty carrier"
        }
        sourceJs?.let { source ->
            require(utf8Bytes(source) <= MAX_SOURCE_JS_UTF8_BYTES) {
                "Tutor visual sourceJs exceeds $MAX_SOURCE_JS_UTF8_BYTES UTF-8 bytes"
            }
        }
        stateJson?.let { state ->
            require(utf8Bytes(state) <= MAX_STATE_UTF8_BYTES) {
                "Tutor visual stateJson exceeds $MAX_STATE_UTF8_BYTES UTF-8 bytes"
            }
        }
        diagnostics?.let { diagnostic ->
            require(diagnostic.isNotBlank()) { "A tutor visual diagnostic must not be blank" }
            require(diagnostic.length <= MAX_DIAGNOSTICS_CHARS) {
                "Tutor visual diagnostics must be at most $MAX_DIAGNOSTICS_CHARS chars"
            }
            require(diagnostic.none { char -> char.isISOControl() && char != '\n' }) {
                "Tutor visual diagnostics must not carry control characters"
            }
        }
    }

    companion object {
        const val MAX_CAPABILITY_VERSION_CHARS = 64
        const val MAX_SOURCE_JS_UTF8_BYTES = 64 * 1_024
        const val MAX_STATE_UTF8_BYTES = 16 * 1_024
        const val MAX_DIAGNOSTICS_CHARS = 2 * 1_024

        private fun utf8Bytes(value: String): Int = value.toByteArray(StandardCharsets.UTF_8).size
    }
}
