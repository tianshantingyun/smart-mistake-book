package com.tingyun.smartmistakebook.core.data.pdf

/**
 * PDF Info 字典 `/AIGC` 的值（R1 §3；TC260-PG-20258A §6.3 模板）。
 *
 * **载体是 Info 字典里的一个 JSON 文本串**，不是 PDF 子字典、不是 XMP：
 * `/AIGC({"Label":"1",...})`。7 个键按官方模板顺序输出，后 4 键为本方无数据的空串。
 *
 * ## 与图片路径的 3 键 builder 的关系（已登记的冲突）
 *
 * 图片 XMP 路径 `capture/GeneratedFigureMetadata.kt` 的 `aigcJson` **只写 3 键**，且
 * `GeneratedFigureAigcXmpTest` 用 `assertFalse` 明确禁止后 4 键出现。R1 要求"只有一个 builder"，
 * 但在不放宽既有断言的前提下无法复用；本批按 R1 §3.3 的保守分支**只给 PDF 路径建 7 键
 * builder**，不改图片路径、不改其测试。统一 builder 需主会话/法务先裁定 3 vs 7（R1 U4）。
 *
 * ## 校验（写前 fail-closed，R1 §3.4）
 *
 * 1. 恰好 7 键、无重复、无额外键；
 * 2. `Label == "1"`（写侧）；
 * 3. `ContentProducer` 匹配 `[A-Za-z0-9 ._-]{1,128}`；
 * 4. `ProduceID` 为 64 位小写 hex（PDF 文档级 = `inputSha256`；图片路径用 `figureId`，
 *    两者不要互相替换）；
 * 5. 其余 4 键存在且为字符串（允许空）；
 * 6. 所有值 ASCII 0x20–0x7E，无控制字符；
 * 7. 序列化后 ≤ 1024 字节。
 *
 * 非 ASCII 直接拒绝：不引入"UTF-16BE 包 JSON、检测器按 UTF-8 解析"的互操作风险（R1 §3.4）。
 */
data class PdfAigcMetadata(
    val label: String = LABEL_AI_GENERATED,
    val contentProducer: String = DEFAULT_CONTENT_PRODUCER,
    val produceId: String,
    val reservedCode1: String = "",
    val contentPropagator: String = "",
    val propagateId: String = "",
    val reservedCode2: String = "",
) {

    init {
        validateForWrite()
    }

    /** 固定顺序、无额外键的 7 键 JSON；值全部为字符串。 */
    fun toJson(): String = buildString(256) {
        append('{')
        appendJsonField(KEY_LABEL, label, first = true)
        appendJsonField(KEY_CONTENT_PRODUCER, contentProducer)
        appendJsonField(KEY_PRODUCE_ID, produceId)
        appendJsonField(KEY_RESERVED_CODE_1, reservedCode1)
        appendJsonField(KEY_CONTENT_PROPAGATOR, contentPropagator)
        appendJsonField(KEY_PROPAGATE_ID, propagateId)
        appendJsonField(KEY_RESERVED_CODE_2, reservedCode2)
        append('}')
    }.also { json ->
        if (json.toByteArray(Charsets.US_ASCII).size > MAX_JSON_BYTES) {
            throw schemaError("serialized /AIGC JSON exceeds $MAX_JSON_BYTES bytes")
        }
    }

    /** 写前校验；任何不满足都抛 [PdfAigcErrorCode.AIGC_SCHEMA_INVALID]。 */
    fun validateForWrite() {
        if (label != LABEL_AI_GENERATED) {
            throw schemaError("Label must be \"$LABEL_AI_GENERATED\" on the write path, was \"$label\"")
        }
        if (!CONTENT_PRODUCER_PATTERN.matches(contentProducer)) {
            throw schemaError("ContentProducer does not match ${CONTENT_PRODUCER_PATTERN.pattern}")
        }
        if (!DOCUMENT_SHA256_PATTERN.matches(produceId)) {
            throw schemaError("ProduceID must be 64 lowercase hex characters on the PDF write path")
        }
        listOf(reservedCode1, contentPropagator, propagateId, reservedCode2).forEachIndexed { index, value ->
            requirePrintableAscii("optional[$index]", value)
        }
    }

    companion object {
        const val LABEL_AI_GENERATED: String = "1"
        const val DEFAULT_CONTENT_PRODUCER: String = "SmartMistakeBook"

        const val KEY_LABEL: String = "Label"
        const val KEY_CONTENT_PRODUCER: String = "ContentProducer"
        const val KEY_PRODUCE_ID: String = "ProduceID"
        const val KEY_RESERVED_CODE_1: String = "ReservedCode1"
        const val KEY_CONTENT_PROPAGATOR: String = "ContentPropagator"
        const val KEY_PROPAGATE_ID: String = "PropagateID"
        const val KEY_RESERVED_CODE_2: String = "ReservedCode2"

        /** 官方模板顺序；测试与检测器按此顺序可做逐字节断言。 */
        val KEY_ORDER: List<String> = listOf(
            KEY_LABEL,
            KEY_CONTENT_PRODUCER,
            KEY_PRODUCE_ID,
            KEY_RESERVED_CODE_1,
            KEY_CONTENT_PROPAGATOR,
            KEY_PROPAGATE_ID,
            KEY_RESERVED_CODE_2,
        )

        private const val MAX_JSON_BYTES = 1024

        private val CONTENT_PRODUCER_PATTERN = Regex("[A-Za-z0-9 ._-]{1,128}")
        private val DOCUMENT_SHA256_PATTERN = Regex("[a-f0-9]{64}")

        /**
         * 文档级工厂：`ProduceID = 输入指纹`（64 位小写 hex，与 `MistakePdfExportInput.inputSha256`
         * 同口径）。图片路径用 `figureId`，两者不要互相替换（R1 §3.2）。
         */
        fun document(
            inputSha256: String,
            contentProducer: String = DEFAULT_CONTENT_PRODUCER,
        ): PdfAigcMetadata {
            if (!DOCUMENT_SHA256_PATTERN.matches(inputSha256)) {
                throw schemaError("document ProduceID must be 64 lowercase hex characters")
            }
            return PdfAigcMetadata(
                contentProducer = contentProducer,
                produceId = inputSha256,
            )
        }

        /**
         * 从既有 JSON 文本构造（写前仍会全量校验）。用于"二次注入同值"的幂等比较与测试读回。
         *
         * 解析器只接受**恰好 7 个键、值全部是 JSON 字符串**的对象；嵌套对象/数组/数字/布尔一律
         * 拒绝。键顺序不敏感，输出时固定为 [KEY_ORDER]。
         */
        fun parse(json: String): PdfAigcMetadata {
            val map = PdfAigcJson.parseObject(json)
            val expected = KEY_ORDER.toSet()
            if (map.keys != expected) {
                val missing = expected - map.keys
                val extra = map.keys - expected
                throw schemaError("AIGC JSON keys mismatch; missing=$missing extra=$extra")
            }
            // 构造器会执行写侧全量校验（Label=="1"、ASCII、长度等）；键顺序不敏感，
            // 输出时固定为 KEY_ORDER（canonical form 由 toJson() 保证）。
            return PdfAigcMetadata(
                label = map.getValue(KEY_LABEL),
                contentProducer = map.getValue(KEY_CONTENT_PRODUCER),
                produceId = map.getValue(KEY_PRODUCE_ID),
                reservedCode1 = map.getValue(KEY_RESERVED_CODE_1),
                contentPropagator = map.getValue(KEY_CONTENT_PROPAGATOR),
                propagateId = map.getValue(KEY_PROPAGATE_ID),
                reservedCode2 = map.getValue(KEY_RESERVED_CODE_2),
            )
        }

        internal fun schemaError(message: String): PdfAigcInjectionException =
            PdfAigcInjectionException(PdfAigcErrorCode.AIGC_SCHEMA_INVALID, message)

        private fun requirePrintableAscii(name: String, value: String) {
            for (index in value.indices) {
                val code = value[index].code
                if (code < 0x20 || code > 0x7E) {
                    throw schemaError("$name contains non-printable/non-ASCII character at $index")
                }
            }
        }
    }

    private fun StringBuilder.appendJsonField(key: String, value: String, first: Boolean = false) {
        if (!first) append(',')
        appendJsonString(key)
        append(':')
        appendJsonString(value)
    }

    private fun StringBuilder.appendJsonString(value: String) {
        append('"')
        for (char in value) {
            when (char) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> {
                    if (char.code < 0x20) {
                        append("\\u")
                        append(char.code.toString(16).padStart(4, '0'))
                    } else {
                        append(char)
                    }
                }
            }
        }
        append('"')
    }
}

/**
 * 极小 JSON 解析器：只服务 `/AIGC` 的"对象 + 字符串值"形状，零第三方依赖。
 *
 * 拒绝重复键、非字符串值、尾随内容；不实现数字/数组/嵌套对象——出现即视为 schema 违规。
 */
internal object PdfAigcJson {

    fun parseObject(text: String): LinkedHashMap<String, String> {
        val parser = Parser(text)
        val result = parser.parseTopLevelObject()
        parser.skipWhitespace()
        if (!parser.atEnd()) parser.fail("trailing content after JSON object")
        return result
    }

    private class Parser(private val text: String) {
        private var pos = 0

        fun atEnd(): Boolean = pos >= text.length

        fun skipWhitespace() {
            while (!atEnd()) {
                when (text[pos]) {
                    ' ', '\t', '\r', '\n' -> pos++
                    else -> return
                }
            }
        }

        fun parseTopLevelObject(): LinkedHashMap<String, String> {
            skipWhitespace()
            expect('{')
            val map = LinkedHashMap<String, String>()
            skipWhitespace()
            if (peek() == '}') {
                pos++
                return map
            }
            while (true) {
                skipWhitespace()
                val key = parseString()
                skipWhitespace()
                expect(':')
                skipWhitespace()
                val value = parseString()
                if (map.put(key, value) != null) fail("duplicate key \"$key\"")
                skipWhitespace()
                when (peek()) {
                    ',' -> pos++
                    '}' -> {
                        pos++
                        return map
                    }
                    else -> fail("expected ',' or '}'")
                }
            }
        }

        private fun parseString(): String {
            expect('"')
            val builder = StringBuilder()
            while (true) {
                if (atEnd()) fail("unterminated string")
                val char = text[pos++]
                when (char) {
                    '"' -> return builder.toString()
                    '\\' -> {
                        if (atEnd()) fail("unterminated escape")
                        when (val escaped = text[pos++]) {
                            '"' -> builder.append('"')
                            '\\' -> builder.append('\\')
                            '/' -> builder.append('/')
                            'b' -> builder.append('\b')
                            'f' -> builder.append('\u000C')
                            'n' -> builder.append('\n')
                            'r' -> builder.append('\r')
                            't' -> builder.append('\t')
                            'u' -> {
                                if (pos + 4 > text.length) fail("truncated \\u escape")
                                val hex = text.substring(pos, pos + 4)
                                val code = hex.toIntOrNull(16) ?: fail("invalid \\u escape")
                                builder.append(code.toChar())
                                pos += 4
                            }
                            else -> fail("invalid escape \\$escaped")
                        }
                    }
                    else -> {
                        if (char.code < 0x20) fail("raw control character in string")
                        builder.append(char)
                    }
                }
            }
        }

        private fun peek(): Char? = if (atEnd()) null else text[pos]

        private fun expect(expected: Char) {
            if (atEnd() || text[pos] != expected) fail("expected '$expected'")
            pos++
        }

        fun fail(message: String): Nothing =
            throw PdfAigcMetadata.schemaError("invalid /AIGC JSON at $pos: $message")
    }
}
