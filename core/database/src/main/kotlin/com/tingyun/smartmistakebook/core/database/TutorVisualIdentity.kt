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

    /**
     * 快照标识：`"visual-snapshot-" + sha256(programId ‖ stateSha256 ‖ drawSha256)[:32]`。
     *
     * 与 [TutorVisualSnapshotWrite] 的唯一键 `(program, state, draw)` 同源：同内容重复提交
     * 落到同一个 id，重试天然幂等。
     */
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

    fun deriveSnapshotId(
        programId: String,
        stateSha256: String,
        drawCommandsSha256: String,
    ): String {
        require(programId.isNotBlank()) { "A visual snapshot needs its program id" }
        require(stateSha256.isNotBlank()) { "A visual snapshot needs a state digest" }
        require(drawCommandsSha256.isNotBlank()) { "A visual snapshot needs a draw digest" }
        val digest = sha256Hex(programId + stateSha256 + drawCommandsSha256)
        return SNAPSHOT_ID_PREFIX + digest.take(32)
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
