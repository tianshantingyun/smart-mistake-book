package com.tingyun.smartmistakebook.core.database

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * D-02 的**唯一**身份来源：`visualId = programId` 由内容派生，而不是调用方随手给。
 *
 * **它消灭的具体失败**：改前"同一 program 被原地改写源码"——已经被消息/题面引用的图会
 * 静默变成另一张，而所有引用仍然有效。把身份改成
 *
 * ```
 * programId = "visual-program-" + sha256(
 *     u32be(len(sourceJs))       ‖ sourceJs UTF-8
 *   ‖ u32be(len(initialState))   ‖ initialStateJson UTF-8
 *   ‖ u32be(len(basis))          ‖ basisFingerprint UTF-8
 *   ‖ u32be(len(apiVersion))     ‖ apiVersion decimal
 *   ‖ u32be(len(sdkVersion))     ‖ sdkVersion UTF-8
 *   ‖ u32be(len(role))           ‖ role UTF-8
 * )
 * ```
 *
 * 之后，源码/初始 state/basis/API/SDK/角色的**任一变化都是另一个 id**，旧行原样保留
 * （数据库层没有 UPDATE 路径）。**长度前缀不是装饰**：`"ab"+"c"` 与 `"a"+"bc"` 必须摘要到
 * 不同 id（同型先例 `cleanRedrawTaskId`，`CleanRedrawTaskContract.kt:94-111`）。
 *
 * 放在 `core:database` 而不是调用方：`ensureTutorVisualProgram` 会在写入前**重新派生并比对**，
 * 所以"传一个与内容不符的 programId"不是 code review 问题，而是写入直接被拒。
 *
 * 与设计字面的一处**加强**：设计草图的长度前缀只画在三个变长字段上，本实现给六个字段
 * 全部加前缀。理由相同且更强：`(sdk="ab", role="c")` 与 `(sdk="a", role="bc")` 也不该撞。
 * 仓内此前无任何 `visual-program-` 生产点，因此不存在需要迁移的历史身份。
 */
object TutorVisualIdentity {

    const val PROGRAM_ID_PREFIX = "visual-program-"

    /** 快照标识前缀；派生式见 [deriveSnapshotId]。 */
    const val SNAPSHOT_ID_PREFIX = "visual-snapshot-"

    fun deriveProgramId(
        sourceJs: String,
        initialStateJson: String,
        basisFingerprint: String,
        apiVersion: Int,
        sdkVersion: String,
        role: String,
    ): String {
        require(sourceJs.isNotEmpty()) { "A visual program needs source" }
        require(initialStateJson.isNotEmpty()) { "A visual program needs its initial state" }
        require(basisFingerprint.isNotBlank()) { "A visual program needs a basis fingerprint" }
        require(apiVersion >= 1) { "A visual program api version starts at 1" }
        require(sdkVersion.isNotBlank()) { "A visual program needs an sdk version" }
        require(role.isNotBlank()) { "A visual program needs a role" }

        val digest = MessageDigest.getInstance(SHA_256)
        digest.updateLengthPrefixed(sourceJs)
        digest.updateLengthPrefixed(initialStateJson)
        digest.updateLengthPrefixed(basisFingerprint)
        digest.updateLengthPrefixed(apiVersion.toString())
        digest.updateLengthPrefixed(sdkVersion)
        digest.updateLengthPrefixed(role)
        return PROGRAM_ID_PREFIX + digest.digest().toHex()
    }

    /**
     * 快照标识（schema 71 / S1）：
     *
     * ```
     * "visual-snapshot-" + sha256(
     *     lp(programId) ‖ lp(stateSha256) ‖ lp(drawCommandsSha256)
     *   ‖ lp(rendererVersion) ‖ lp(fontManifestVersion)
     * )[:32]
     * ```
     *
     * 与 `TutorVisualSnapshotEntity` 的唯一键
     * `(program_id, state_sha256, draw_commands_sha256, renderer_version, font_manifest_version)`
     * **同源**：同内容 + 同版本的重复提交落到同一个 id（重试天然幂等），而**版本变化产生另一个
     * id**。
     *
     * **为什么版本必须进派生式**（v70 的三参版本是缺陷）：派生 id 只含内容三列时，painter 或
     * 字体升级后同内容的再提交会落到**旧行的 id**，`INSERT OR IGNORE` + 唯一键把它吞掉，
     * 恢复侧按 `rendererVersion != CURRENT` 判只读——历史图从此不可逆降级。派生式、唯一键、
     * DAO 查重三者必须同时含版本，任何一处漏掉都会让 S1 复发。
     *
     * 五段全部长度前缀（沿用 [deriveProgramId] 的写法）：`("ab","c")` 与 `("a","bc")` 是两组
     * 不同输入，无前缀拼接会把它们摘要成同一串。
     */
    fun deriveSnapshotId(
        programId: String,
        stateSha256: String,
        drawCommandsSha256: String,
        rendererVersion: String,
        fontManifestVersion: String,
    ): String {
        require(programId.isNotBlank()) { "A visual snapshot needs its program id" }
        require(stateSha256.isNotBlank()) { "A visual snapshot needs a state digest" }
        require(drawCommandsSha256.isNotBlank()) { "A visual snapshot needs a draw digest" }
        require(rendererVersion.isNotBlank()) { "A visual snapshot needs a renderer version" }
        require(fontManifestVersion.isNotBlank()) { "A visual snapshot needs a font manifest version" }
        val digest = MessageDigest.getInstance(SHA_256)
        digest.updateLengthPrefixed(programId)
        digest.updateLengthPrefixed(stateSha256)
        digest.updateLengthPrefixed(drawCommandsSha256)
        digest.updateLengthPrefixed(rendererVersion)
        digest.updateLengthPrefixed(fontManifestVersion)
        return SNAPSHOT_ID_PREFIX + digest.digest().toHex().take(32)
    }

    fun sha256Hex(value: String): String =
        sha256Hex(value.toByteArray(StandardCharsets.UTF_8))

    fun sha256Hex(value: ByteArray): String =
        MessageDigest.getInstance(SHA_256).digest(value).toHex()

    private fun MessageDigest.updateLengthPrefixed(value: String) {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        update(
            byteArrayOf(
                (bytes.size ushr 24).toByte(),
                (bytes.size ushr 16).toByte(),
                (bytes.size ushr 8).toByte(),
                bytes.size.toByte(),
            ),
        )
        update(bytes)
    }

    private fun ByteArray.toHex(): String {
        val out = CharArray(size * 2)
        val digits = HEX_DIGITS
        for (index in indices) {
            val value = this[index].toInt() and 0xFF
            out[index * 2] = digits[value ushr 4]
            out[index * 2 + 1] = digits[value and 0x0F]
        }
        return String(out)
    }

    private const val SHA_256 = "SHA-256"
    private val HEX_DIGITS = "0123456789abcdef".toCharArray()
}
