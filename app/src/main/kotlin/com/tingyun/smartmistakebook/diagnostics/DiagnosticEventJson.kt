package com.tingyun.smartmistakebook.diagnostics

/**
 * [DiagnosticEvent] 的手写一行 JSON 编解码。app 模块没有 JSON 依赖（app/build.gradle.kts
 * 未启用 serialization 插件），为"一行一条"的账本引入依赖不值得；编解码正确性由
 * DiagnosticEventJsonTest 钉住（往返、敌意字符串、半行、缺字段、未知键前向兼容）。
 *
 * 解码是"防崩优先"：任何不合法输入都返回 null，绝不抛异常——消费方是崩溃后的读取路径。
 */
object DiagnosticEventJson {

    fun encode(event: DiagnosticEvent): String {
        val sb = StringBuilder(192)
        sb.append('{')
        sb.append("\"atEpochMillis\":").append(event.atEpochMillis).append(',')
        sb.append("\"category\":").append(quote(event.category)).append(',')
        sb.append("\"threadName\":").append(quote(event.threadName))
        event.message?.let { sb.append(",\"message\":").append(quote(it)) }
        event.stackTrace?.let { sb.append(",\"stackTrace\":").append(quote(it)) }
        event.versionName?.let { sb.append(",\"versionName\":").append(quote(it)) }
        event.versionCode?.let { sb.append(",\"versionCode\":").append(it) }
        event.flavor?.let { sb.append(",\"flavor\":").append(quote(it)) }
        sb.append('}')
        return sb.toString()
    }

    /** 解码一行；空行、半行、缺必需字段、结构不完整、原始控制字符都返回 null。 */
    fun decodeOrNull(line: String): DiagnosticEvent? {
        val parser = Parser(line.trim())
        return parser.parseEventOrNull()
    }

    private fun quote(value: String): String {
        val sb = StringBuilder(value.length + 16)
        sb.append('"')
        for (ch in value) {
            when (ch) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else ->
                    if (ch < ' ') {
                        // 其余控制字符一律 \uXXXX 转义，保证编码结果永不含有裸换行/控制符。
                        sb.append("\\u")
                        val hex = ch.code.toString(16)
                        repeat(4 - hex.length) { sb.append('0') }
                        sb.append(hex)
                    } else {
                        sb.append(ch)
                    }
            }
        }
        sb.append('"')
        return sb.toString()
    }

    private class Parser(private val text: String) {
        private var pos = 0

        fun parseEventOrNull(): DiagnosticEvent? {
            if (!consume('{')) return null
            var atEpochMillis: Long? = null
            var category: String? = null
            var threadName: String? = null
            var message: String? = null
            var stackTrace: String? = null
            var versionName: String? = null
            var versionCode: Long? = null
            var flavor: String? = null
            if (!consume('}')) {
                while (true) {
                    val key = readStringOrNull() ?: return null
                    if (!consume(':')) return null
                    when (key) {
                        "atEpochMillis" -> atEpochMillis = readLongOrNull() ?: return null
                        "category" -> category = readStringOrNull() ?: return null
                        "threadName" -> threadName = readStringOrNull() ?: return null
                        "message" -> message = readStringOrNull() ?: return null
                        "stackTrace" -> stackTrace = readStringOrNull() ?: return null
                        "versionName" -> versionName = readStringOrNull() ?: return null
                        "versionCode" -> versionCode = readLongOrNull() ?: return null
                        "flavor" -> flavor = readStringOrNull() ?: return null
                        else ->
                            // 未知键只跳过标量值：保证"未来版本加了字段"不破坏当前读取
                            //（前向兼容）；嵌套容器按不支持处理，整行拒绝（fail-safe）。
                            if (!skipScalarOrNull()) return null
                    }
                    when {
                        consume('}') -> break
                        consume(',') -> continue
                        else -> return null
                    }
                }
            }
            if (pos != text.length) return null
            val at = atEpochMillis ?: return null
            val cat = category ?: return null
            val thread = threadName ?: return null
            return DiagnosticEvent(
                atEpochMillis = at,
                category = cat,
                threadName = thread,
                message = message,
                stackTrace = stackTrace,
                versionName = versionName,
                versionCode = versionCode,
                flavor = flavor,
            )
        }

        private fun consume(expected: Char): Boolean {
            if (pos < text.length && text[pos] == expected) {
                pos++
                return true
            }
            return false
        }

        private fun readStringOrNull(): String? {
            if (pos >= text.length || text[pos] != '"') return null
            pos++
            val sb = StringBuilder()
            while (true) {
                if (pos >= text.length) return null
                val ch = text[pos]
                when {
                    ch == '"' -> {
                        pos++
                        return sb.toString()
                    }
                    ch == '\\' -> {
                        pos++
                        if (pos >= text.length) return null
                        when (val escape = text[pos]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'u' -> {
                                if (pos + 4 >= text.length) return null
                                val hex = text.substring(pos + 1, pos + 5)
                                val code = hex.toIntOrNull(16) ?: return null
                                sb.append(code.toChar())
                                pos += 4
                            }
                            else -> return null
                        }
                        pos++
                    }
                    ch < ' ' -> return null
                    else -> {
                        sb.append(ch)
                        pos++
                    }
                }
            }
        }

        private fun readLongOrNull(): Long? {
            val start = pos
            if (pos < text.length && text[pos] == '-') pos++
            var digits = 0
            while (pos < text.length && text[pos].isDigit()) {
                pos++
                digits++
            }
            if (digits == 0 || digits > 19) {
                pos = start
                return null
            }
            return text.substring(start, pos).toLongOrNull()
        }

        /** 只跳过 JSON 标量（字符串/数字/true/false/null）；容器不支持，返回 false。 */
        private fun skipScalarOrNull(): Boolean {
            if (pos >= text.length) return false
            return when {
                text[pos] == '"' -> readStringOrNull() != null
                text.startsWith("true", pos) -> {
                    pos += 4
                    true
                }
                text.startsWith("false", pos) -> {
                    pos += 5
                    true
                }
                text.startsWith("null", pos) -> {
                    pos += 4
                    true
                }
                text[pos] == '-' || text[pos].isDigit() -> {
                    if (text[pos] == '-') pos++
                    var sawDigit = false
                    while (pos < text.length && (text[pos].isDigit() || text[pos] in ".eE+-")) {
                        if (text[pos].isDigit()) sawDigit = true
                        pos++
                    }
                    sawDigit
                }
                else -> false
            }
        }
    }
}
