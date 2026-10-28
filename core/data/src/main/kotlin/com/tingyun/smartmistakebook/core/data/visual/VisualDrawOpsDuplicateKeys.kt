package com.tingyun.smartmistakebook.core.data.visual

/**
 * DrawOps V1 文本的**重复 JSON 键扫描**（批次 30 T8；从 [VisualDrawOpsParser] 拆出以守住
 * 文件大小门——解析器本体已 850+ 行）。
 *
 * **它消灭的失败**：kotlinx-serialization-json 1.9.0（钉版，`Json` 默认配置）的树解析器把
 * 对象成员写进 `LinkedHashMap`（本地 `javap` 实证：`JsonTreeReader.readObject` 的
 * `new java/util/LinkedHashMap` → `Map.put`；官方 v1.9.0 源同为 `linkedMapOf` +
 * `result[key] = element`），重复键**后者覆盖前者且不报错**——文档里同时写
 * `"alpha":1` 与 `"alpha":0.2`，解析结果只剩 0.2，两份文本不可区分。契约 §4:90 要求
 * "严格拒绝重复 JSON 键"，JS 编译器侧已有 `SCENE_PARSE_DUP_KEY`；这里是消费侧的同一门。
 *
 * **语义边界**：
 * - 键比较用**解码后**的字符串（`"\u0061"` 与 `"a"` 是同一个键），与 JS 侧 `parseString`
 *   的行为一致；字符串值里的 `{`/`"`/`:` 不会被当结构（扫描器完整处理转义）。
 * - 文本不是合法 JSON 时返回 null，由解析器的 `DRAWOPS_NOT_JSON` 统一负责——本扫描只做
 *   额外门，**不**承担 JSON 合法性判定；因此它只可能让拒绝更早/更具体，不会把合法文档
 *   判坏（合法文档的字符串/数字/结构它都完整走通）。
 * - 深度超过 [MAX_DUP_KEY_SCAN_DEPTH] 视为不可判定（放行给 JSON 解析器）：合法 DrawOps
 *   文档的深度只有 document → ops/path → command 三层，超深文本必被结构门拒绝。
 *
 * 纯函数、零 android 符号；JVM 门（`VisualDrawOpsParserTest`）无需 Robolectric。
 */
internal fun findDuplicateDrawOpsKey(text: String): String? {
    val scanner = DuplicateKeyScanner(text, maxDepth = MAX_DUP_KEY_SCAN_DEPTH)
    return if (scanner.scanFully()) scanner.duplicateKey else null
}

private const val MAX_DUP_KEY_SCAN_DEPTH = 128

private class DuplicateKeyScanner(
    private val text: String,
    private val maxDepth: Int,
) {
    var duplicateKey: String? = null
        private set

    private var pos = 0

    fun scanFully(): Boolean {
        skipWhitespace()
        if (!parseValue(0)) return false
        skipWhitespace()
        return pos == text.length
    }

    private fun parseValue(depth: Int): Boolean {
        if (depth > maxDepth) return false
        skipWhitespace()
        if (pos >= text.length) return false
        return when (val c = text[pos]) {
            '{' -> parseObject(depth)
            '[' -> parseArray(depth)
            '"' -> parseString() != null
            't' -> consumeLiteral("true")
            'f' -> consumeLiteral("false")
            'n' -> consumeLiteral("null")
            else -> if (c == '-' || c in '0'..'9') parseNumber() else false
        }
    }

    private fun parseObject(depth: Int): Boolean {
        pos += 1 // '{'
        val keys = HashSet<String>()
        skipWhitespace()
        if (peek() == '}') {
            pos += 1
            return true
        }
        while (true) {
            skipWhitespace()
            val key = parseString() ?: return false
            if (!keys.add(key) && duplicateKey == null) duplicateKey = key
            skipWhitespace()
            if (peek() != ':') return false
            pos += 1
            if (!parseValue(depth + 1)) return false
            skipWhitespace()
            when (peek()) {
                ',' -> pos += 1
                '}' -> {
                    pos += 1
                    return true
                }

                else -> return false
            }
        }
    }

    private fun parseArray(depth: Int): Boolean {
        pos += 1 // '['
        skipWhitespace()
        if (peek() == ']') {
            pos += 1
            return true
        }
        while (true) {
            if (!parseValue(depth + 1)) return false
            skipWhitespace()
            when (peek()) {
                ',' -> pos += 1
                ']' -> {
                    pos += 1
                    return true
                }

                else -> return false
            }
        }
    }

    /** 解码后的 JSON 字符串（含 `\uXXXX`）；非法转义或未闭合返回 null。 */
    private fun parseString(): String? {
        if (peek() != '"') return null
        pos += 1
        val builder = StringBuilder()
        while (pos < text.length) {
            val c = text[pos]
            pos += 1
            when {
                c == '"' -> return builder.toString()
                c == '\\' -> {
                    if (pos >= text.length) return null
                    when (val escaped = text[pos]) {
                        '"', '\\', '/' -> {
                            builder.append(escaped)
                            pos += 1
                        }

                        'b' -> {
                            builder.append('\b')
                            pos += 1
                        }

                        'f' -> {
                            builder.append('\u000C')
                            pos += 1
                        }

                        'n' -> {
                            builder.append('\n')
                            pos += 1
                        }

                        'r' -> {
                            builder.append('\r')
                            pos += 1
                        }

                        't' -> {
                            builder.append('\t')
                            pos += 1
                        }

                        'u' -> {
                            pos += 1
                            var value = 0
                            repeat(4) {
                                if (pos >= text.length) return null
                                val digit = hexDigit(text[pos]) ?: return null
                                value = value * 16 + digit
                                pos += 1
                            }
                            builder.append(value.toChar())
                        }

                        else -> return null
                    }
                }

                c < ' ' -> return null
                else -> builder.append(c)
            }
        }
        return null
    }

    /** 跳过 JSON 数字（只看形状；值域由解析器负责）。 */
    private fun parseNumber(): Boolean {
        if (peek() == '-') pos += 1
        when (peek()) {
            '0' -> pos += 1
            in '1'..'9' -> {
                pos += 1
                while (peek() in '0'..'9') pos += 1
            }

            else -> return false
        }
        if (peek() == '.') {
            pos += 1
            if (peek() !in '0'..'9') return false
            while (peek() in '0'..'9') pos += 1
        }
        if (peek() == 'e' || peek() == 'E') {
            pos += 1
            if (peek() == '+' || peek() == '-') pos += 1
            if (peek() !in '0'..'9') return false
            while (peek() in '0'..'9') pos += 1
        }
        return true
    }

    private fun consumeLiteral(literal: String): Boolean {
        if (!text.startsWith(literal, pos)) return false
        pos += literal.length
        return true
    }

    private fun skipWhitespace() {
        while (pos < text.length) {
            when (text[pos]) {
                ' ', '\t', '\n', '\r' -> pos += 1
                else -> return
            }
        }
    }

    private fun peek(): Char = if (pos < text.length) text[pos] else '\u0000'

    private fun hexDigit(c: Char): Int? = when (c) {
        in '0'..'9' -> c - '0'
        in 'a'..'f' -> c - 'a' + 10
        in 'A'..'F' -> c - 'A' + 10
        else -> null
    }
}
