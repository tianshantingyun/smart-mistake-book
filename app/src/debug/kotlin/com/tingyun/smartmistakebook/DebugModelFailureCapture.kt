package com.tingyun.smartmistakebook

import android.content.Context
import android.util.Log
import com.tingyun.smartmistakebook.core.data.model.ModelCallFailureDiagnostics
import com.tingyun.smartmistakebook.core.data.model.ModelCallFailureRecord
import java.io.File
import java.util.Locale

/**
 * debug 专用：把**失败的**模型调用响应原文落进应用私有目录
 * （`filesDir/debug-model-failures/`），默认关，靠 [TestCaptureModelFailuresReceiver] 的广播开/关。
 *
 * **它消灭的失败**：失败构成——上游拒收的报文、被截断的响应、形状/预算越界的那一版原文——在日志里
 * 从来留不下（网关的 logModelCallFailure 只记 class + message），logcat 一轮转证据就没了，于是
 * "同一道题连续 4 次失败"只能停在推测上（KD-38 的证据缺口）。
 * 开关制 + 64 KiB/条 + 最多 20 条，是为了让"打开捕获 → 复现一次 → 把原文拿出来核对"成为一条可执行
 * 的路径，而不是让一份无上限的学生数据留在盘上。
 *
 * **本文件写出的每个条目都含学生内容（题面/图上读出来的文字），仅本机调试用，不要外传。**
 *
 * 边界（都不是靠约定）：
 * - 本类只存在于 `app/src/debug`，release / benchmark 变体里没有它 ⇒ 那边
 *   [ModelCallFailureDiagnostics.sink] 永远是 null（`tools/ci/check_release_manifest.py` 另把
 *   debug 组件挡在 release 合并清单之外）；
 * - 每个条目文件第一行写明"含学生内容，仅本机调试"；
 * - 响应原文超过 [MAX_CAPTURED_RESPONSE_BYTES] 就截断，并在条目里写明原始字节数；
 * - 目录里最多留 [MAX_CAPTURED_ENTRIES] 条，再写进来就按文件名（时间在前）删最旧的。
 *
 * 用完清掉（debug 变体可 `run-as`；卸载应用也会一并删）：
 * ```
 * adb shell run-as com.tingyun.smartmistakebook.localfirst rm -rf files/debug-model-failures
 * ```
 */
object DebugModelFailureCapture {
    /** 私有目录下的调试文件目录名。 */
    const val DIRECTORY_NAME = "debug-model-failures"

    /** 单条记录里**响应原文**的截断上限（UTF-8 字节）。 */
    const val MAX_CAPTURED_RESPONSE_BYTES = 64 * 1024

    /** 目录里最多保留的条目数；第 21 条写进来时删掉最旧的一条。 */
    const val MAX_CAPTURED_ENTRIES = 20

    /** 条目文件名：`failure-<毫秒>-<两位序号>-<TASK_KIND>.txt`。 */
    const val FILE_PREFIX = "failure-"

    const val FILE_SUFFIX = ".txt"

    /** 每个条目文件第一行的提示：条目被单独转走时也带着它。 */
    const val STUDENT_CONTENT_NOTICE = "含学生内容，仅本机调试；不要外传。"

    const val LOG_TAG = "ModelFailureCapture"

    fun directory(context: Context): File = File(context.filesDir, DIRECTORY_NAME)

    /** 武装捕获（广播开）。重复开是幂等的。 */
    fun install(context: Context) {
        val root = directory(context)
        ModelCallFailureDiagnostics.sink = { record -> write(root, record) }
        Log.i(LOG_TAG, "model failure capture ON -> ${root.absolutePath}（含学生内容，仅本机调试）")
    }

    /** 卸下捕获（广播关）。已经落盘的条目不动，供复现之后复核。 */
    fun uninstall() {
        ModelCallFailureDiagnostics.sink = null
        Log.i(LOG_TAG, "model failure capture OFF")
    }

    /**
     * 写一条；返回落盘的文件（写不进去时返回 null）。
     *
     * 自己吞掉写盘异常是对的：捕获是旁路，不能把一次"失败"变成"崩溃"。但**必须留痕**——静默就等于
     * "开关开着却什么都没抓到"，那正是这条通道要消灭的失败里最坏的一种。
     */
    private fun write(directory: File, record: ModelCallFailureRecord): File? = try {
        if (!directory.exists() && !directory.mkdirs()) {
            Log.w(LOG_TAG, "model failure capture: cannot create ${directory.absolutePath}")
            null
        } else {
            val target = nextTarget(directory, record)
            target.writeBytes(entryText(record).toByteArray(Charsets.UTF_8))
            pruneOldest(directory)
            Log.i(LOG_TAG, "model failure captured: ${target.name}（含学生内容，仅本机调试）")
            target
        }
    } catch (failure: Throwable) {
        Log.w(LOG_TAG, "model failure capture failed: ${failure.javaClass.simpleName}")
        null
    }

    /**
     * 同一毫秒内重复失败不能互相覆盖（一条失败 = 一个条目，"最多 N 条"的口径靠它成立），所以序号
     * 零填充两位：文件名排序即时间排序，"删最旧"才可靠。
     */
    private fun nextTarget(directory: File, record: ModelCallFailureRecord): File {
        var ordinal = 0
        while (true) {
            val candidate = File(directory, fileName(record, ordinal))
            if (!candidate.exists()) return candidate
            ordinal += 1
        }
    }

    private fun fileName(record: ModelCallFailureRecord, ordinal: Int): String =
        "$FILE_PREFIX${record.occurredAtEpochMillis}-" +
            String.format(Locale.ROOT, "%02d", ordinal) +
            "-${record.taskKind}$FILE_SUFFIX"

    private fun entryText(record: ModelCallFailureRecord): String {
        val raw = record.rawResponse
        val truncated = raw != null && utf8Bytes(raw) > MAX_CAPTURED_RESPONSE_BYTES
        val kept = when {
            raw == null -> ""
            truncated -> truncateToUtf8Bytes(raw, MAX_CAPTURED_RESPONSE_BYTES)
            else -> raw
        }
        return buildString {
            appendLine(STUDENT_CONTENT_NOTICE)
            appendLine("时间：${record.occurredAtEpochMillis}")
            appendLine("任务：${record.taskKind}")
            appendLine("归类：${record.failureCode}（可重试=${record.retryable}）")
            appendLine("HTTP：${record.httpStatus ?: "（没有响应：连接层失败）"}")
            appendLine(
                "异常：" + when {
                    record.exceptionClass == null -> "（没有异常：上游状态码本身即失败）"
                    else -> "${record.exceptionClass}: ${record.exceptionMessage ?: ""}"
                },
            )
            appendLine(
                "响应原文：" + when {
                    raw == null -> "（无：连接层失败，没有响应体）"
                    truncated -> "${utf8Bytes(raw)} 字节，超过 $MAX_CAPTURED_RESPONSE_BYTES 字节上限，" +
                        "只保留前 ${utf8Bytes(kept)} 字节"
                    else -> "${utf8Bytes(raw)} 字节（未截断）"
                },
            )
            appendLine("---------- 响应原文 ----------")
            append(kept)
        }
    }

    /** 按文件名（时间前缀在前）从新到旧留 [MAX_CAPTURED_ENTRIES] 条，其余删掉。 */
    private fun pruneOldest(directory: File) {
        val entries = directory.listFiles { file ->
            file.isFile && file.name.startsWith(FILE_PREFIX) && file.name.endsWith(FILE_SUFFIX)
        } ?: return
        entries.sortedByDescending { it.name }.drop(MAX_CAPTURED_ENTRIES).forEach { it.delete() }
    }

    /**
     * 截到 UTF-8 字节上限，且不切碎代理对（否则落盘的会是半个字符）。
     * 孤立代理按 3 字节记账、实际编码更短，所以只会**少留**、不会越过上限。
     */
    private fun truncateToUtf8Bytes(text: String, limitBytes: Int): String {
        var used = 0
        var end = 0
        while (end < text.length) {
            val width = utf8Width(text, end)
            if (used + width > limitBytes) break
            used += width
            end += if (width == 4) 2 else 1
        }
        return text.substring(0, end)
    }

    private fun utf8Width(text: String, index: Int): Int {
        val char = text[index]
        return when {
            char.code < 0x80 -> 1
            char.code < 0x800 -> 2
            char.isHighSurrogate() &&
                index + 1 < text.length &&
                text[index + 1].isLowSurrogate() -> 4
            else -> 3
        }
    }

    private fun utf8Bytes(text: String): Int = text.toByteArray(Charsets.UTF_8).size
}
