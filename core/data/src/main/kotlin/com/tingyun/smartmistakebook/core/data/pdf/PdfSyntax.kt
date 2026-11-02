package com.tingyun.smartmistakebook.core.data.pdf

import java.io.ByteArrayOutputStream

/** 词法/结构错误；调用方按上下文映射到 [PdfAigcErrorCode]（不直接外泄）。 */
internal class PdfSyntaxException(message: String) : RuntimeException(message)

internal data class PdfStringToken(
    val decoded: ByteArray,
    val hex: Boolean,
    val rawStart: Int,
    val rawEnd: Int,
) {
    fun decodedText(): String = decoded.toString(Charsets.ISO_8859_1)
}

internal data class PdfEntry(
    val key: String,
    val value: PdfValue,
    val keyStart: Int,
    val valueStart: Int,
    val valueEnd: Int,
)

internal sealed class PdfValue {
    data class Num(val value: Long) : PdfValue()
    data class Ref(val number: Int, val generation: Int) : PdfValue()
    data class Name(val value: String) : PdfValue()
    data class Str(val token: PdfStringToken) : PdfValue()
    data class Arr(val items: List<PdfValue>) : PdfValue()
    data class Dict(
        val entries: LinkedHashMap<String, PdfEntry>,
        val start: Int,
        val end: Int,
    ) : PdfValue() {
        operator fun get(key: String): PdfValue? = entries[key]?.value
        fun raw(key: String, bytes: ByteArray): ByteArray? {
            val entry = entries[key] ?: return null
            return bytes.copyOfRange(entry.valueStart, entry.valueEnd)
        }
    }

    data class Bool(val value: Boolean) : PdfValue()
    object Null : PdfValue()
    data class Other(val rawStart: Int, val rawEnd: Int) : PdfValue()
}

/**
 * 字节级 PDF token 读取器（R1 §2、§4.2）。
 *
 * 设计要点：
 * - 只在**当前持有**的字节数组上做绝对偏移读取，不引入第三方库；
 * - 字符串/hex/注释/嵌套字典都按 token 跳过，避免"字符串里出现 `>>`"把字典结束位置找错；
 * - 任何越界/不配对都抛 [PdfSyntaxException]，由调用方映射成 typed code。
 */
internal class PdfLexer(
    val bytes: ByteArray,
    var pos: Int = 0,
    val limit: Int = bytes.size,
) {

    fun atEnd(): Boolean = pos >= limit

    fun skipWhitespaceAndComments() {
        while (pos < limit) {
            val byte = bytes[pos].toInt() and 0xFF
            when {
                byte == 0x00 || byte == 0x09 || byte == 0x0A || byte == 0x0C ||
                    byte == 0x0D || byte == 0x20 -> pos++
                byte == 0x25 -> { // '%' comment to EOL
                    pos++
                    while (pos < limit) {
                        val next = bytes[pos].toInt() and 0xFF
                        if (next == 0x0A || next == 0x0D) break
                        pos++
                    }
                }
                else -> return
            }
        }
    }

    fun requireWhitespaceOrComment(): Boolean {
        val before = pos
        skipWhitespaceAndComments()
        return pos > before
    }

    fun peekByte(): Int? = if (pos >= limit) null else bytes[pos].toInt() and 0xFF

    fun matchKeyword(keyword: String): Boolean {
        if (pos + keyword.length > limit) return false
        for (index in keyword.indices) {
            if (bytes[pos + index].toInt() and 0xFF != keyword[index].code) return false
        }
        val after = pos + keyword.length
        if (after >= limit) return true
        return isWhitespaceOrDelimiter(bytes[after].toInt() and 0xFF)
    }

    fun consumeKeyword(keyword: String) {
        if (!matchKeyword(keyword)) throw PdfSyntaxException("expected keyword '$keyword' at $pos")
        pos += keyword.length
    }

    /** 读取十进制整数；不消费前导空白。没有数字时返回 null 且不移动位置。 */
    fun readLongToken(): Long? {
        if (pos >= limit) return null
        val start = pos
        var sign = 1L
        var byte = bytes[pos].toInt() and 0xFF
        if (byte == 0x2B) { // '+'
            pos++
        } else if (byte == 0x2D) { // '-'
            sign = -1L
            pos++
        }
        var digits = 0
        var value = 0L
        while (pos < limit) {
            byte = bytes[pos].toInt() and 0xFF
            if (byte < 0x30 || byte > 0x39) break
            val digit = (byte - 0x30).toLong()
            if (value > (Long.MAX_VALUE - digit) / 10L) {
                pos = start
                throw PdfSyntaxException("integer overflow at $start")
            }
            value = value * 10L + digit
            digits++
            pos++
        }
        if (digits == 0) {
            pos = start
            return null
        }
        return sign * value
    }

    /** 读取对象头 `N G obj`；失败返回 null 且不移动位置。 */
    fun readObjectHeader(): PdfObjectHeader? {
        val start = pos
        skipWhitespaceAndComments()
        val number = readLongToken() ?: run { pos = start; return null }
        if (!requireWhitespaceOrComment()) { pos = start; return null }
        val generation = readLongToken() ?: run { pos = start; return null }
        if (!requireWhitespaceOrComment()) { pos = start; return null }
        if (!matchKeyword("obj")) { pos = start; return null }
        pos += 3
        if (number < 0 || number > Int.MAX_VALUE || generation < 0 || generation > 65_535) {
            pos = start
            return null
        }
        return PdfObjectHeader(number.toInt(), generation.toInt(), start, pos)
    }

    fun parseValue(): PdfValue {
        skipWhitespaceAndComments()
        if (atEnd()) throw PdfSyntaxException("unexpected end of file while parsing value")
        return when (bytes[pos].toInt() and 0xFF) {
            0x3C -> { // '<'
                if (pos + 1 < limit && bytes[pos + 1].toInt() and 0xFF == 0x3C) {
                    readDictionary()
                } else {
                    readHexString()
                }
            }
            0x28 -> readLiteralString() // '('
            0x2F -> { // '/'
                val name = readName()
                PdfValue.Name(name)
            }
            0x5B -> readArray() // '['
            0x74 -> { // 't'
                if (matchKeyword("true")) {
                    pos += 4
                    PdfValue.Bool(true)
                } else {
                    readOtherToken()
                }
            }
            0x66 -> { // 'f'
                if (matchKeyword("false")) {
                    pos += 5
                    PdfValue.Bool(false)
                } else {
                    readOtherToken()
                }
            }
            0x6E -> { // 'n'
                if (matchKeyword("null")) {
                    pos += 4
                    PdfValue.Null
                } else {
                    readOtherToken()
                }
            }
            0x2B, 0x2D, 0x30, 0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37, 0x38, 0x39 -> {
                readNumberOrReference()
            }
            else -> readOtherToken()
        }
    }

    fun readDictionary(): PdfValue.Dict {
        skipWhitespaceAndComments()
        if (pos + 1 >= limit || bytes[pos] != '<'.code.toByte() || bytes[pos + 1] != '<'.code.toByte()) {
            throw PdfSyntaxException("expected '<<' at $pos")
        }
        val start = pos
        pos += 2
        val entries = LinkedHashMap<String, PdfEntry>()
        while (true) {
            skipWhitespaceAndComments()
            if (pos + 1 < limit && bytes[pos] == '>'.code.toByte() && bytes[pos + 1] == '>'.code.toByte()) {
                pos += 2
                return PdfValue.Dict(entries, start, pos)
            }
            if (atEnd()) throw PdfSyntaxException("unterminated dictionary starting at $start")
            if (bytes[pos] != '/'.code.toByte()) {
                throw PdfSyntaxException("dictionary key must be a name at $pos")
            }
            val keyStart = pos
            val key = readName()
            if (key.isEmpty()) throw PdfSyntaxException("empty dictionary key at $keyStart")
            skipWhitespaceAndComments()
            val valueStart = pos
            val value = parseValue()
            val valueEnd = pos
            if (entries.containsKey(key)) {
                throw PdfSyntaxException("duplicate dictionary key /$key at $keyStart")
            }
            entries[key] = PdfEntry(key, value, keyStart, valueStart, valueEnd)
        }
    }

    private fun readArray(): PdfValue.Arr {
        pos++ // '['
        val items = ArrayList<PdfValue>()
        while (true) {
            skipWhitespaceAndComments()
            if (atEnd()) throw PdfSyntaxException("unterminated array at $pos")
            if (bytes[pos] == ']'.code.toByte()) {
                pos++
                return PdfValue.Arr(items)
            }
            items.add(parseValue())
        }
    }

    private fun readName(): String {
        if (pos >= limit || bytes[pos] != '/'.code.toByte()) {
            throw PdfSyntaxException("expected name at $pos")
        }
        pos++
        val builder = StringBuilder()
        while (pos < limit) {
            val byte = bytes[pos].toInt() and 0xFF
            if (isWhitespaceOrDelimiter(byte)) break
            if (byte == 0x23) { // '#'
                if (pos + 2 >= limit) throw PdfSyntaxException("truncated #-escape in name at $pos")
                val high = hexValue(bytes[pos + 1].toInt() and 0xFF)
                val low = hexValue(bytes[pos + 2].toInt() and 0xFF)
                if (high < 0 || low < 0) throw PdfSyntaxException("invalid #-escape in name at $pos")
                builder.append(((high shl 4) or low).toChar())
                pos += 3
            } else {
                builder.append(byte.toChar())
                pos++
            }
        }
        return builder.toString()
    }

    private fun readLiteralString(): PdfValue.Str {
        val rawStart = pos
        pos++ // '('
        val out = ByteArrayOutputStream()
        var depth = 1
        while (pos < limit) {
            val byte = bytes[pos].toInt() and 0xFF
            when (byte) {
                0x5C -> { // backslash
                    pos++
                    if (pos >= limit) throw PdfSyntaxException("unterminated escape in string at $rawStart")
                    when (val escaped = bytes[pos].toInt() and 0xFF) {
                        0x6E -> { out.write(0x0A); pos++ } // n
                        0x72 -> { out.write(0x0D); pos++ } // r
                        0x74 -> { out.write(0x09); pos++ } // t
                        0x62 -> { out.write(0x08); pos++ } // b
                        0x66 -> { out.write(0x0C); pos++ } // f
                        0x28 -> { out.write(0x28); pos++ } // (
                        0x29 -> { out.write(0x29); pos++ } // )
                        0x5C -> { out.write(0x5C); pos++ } // backslash
                        0x0D -> { // line continuation; CR or CRLF
                            pos++
                            if (pos < limit && bytes[pos].toInt() and 0xFF == 0x0A) pos++
                        }
                        0x0A -> pos++ // line continuation LF
                        in 0x30..0x37 -> { // octal, up to 3 digits
                            var value = 0
                            var count = 0
                            while (count < 3 && pos < limit) {
                                val digit = bytes[pos].toInt() and 0xFF
                                if (digit < 0x30 || digit > 0x37) break
                                value = (value shl 3) or (digit - 0x30)
                                pos++
                                count++
                            }
                            out.write(value and 0xFF)
                        }
                        else -> { out.write(escaped); pos++ }
                    }
                }
                0x28 -> { depth++; out.write(0x28); pos++ }
                0x29 -> {
                    depth--
                    pos++
                    if (depth == 0) {
                        return PdfValue.Str(
                            PdfStringToken(out.toByteArray(), hex = false, rawStart = rawStart, rawEnd = pos),
                        )
                    }
                    out.write(0x29)
                }
                0x0D -> { // normalize CR / CRLF to LF
                    out.write(0x0A)
                    pos++
                    if (pos < limit && bytes[pos].toInt() and 0xFF == 0x0A) pos++
                }
                0x0A -> { out.write(0x0A); pos++ }
                else -> { out.write(byte); pos++ }
            }
        }
        throw PdfSyntaxException("unterminated string starting at $rawStart")
    }

    private fun readHexString(): PdfValue.Str {
        val rawStart = pos
        pos++ // '<'
        val out = ByteArrayOutputStream()
        var highNibble = -1
        while (pos < limit) {
            val byte = bytes[pos].toInt() and 0xFF
            pos++
            if (byte == 0x3E) { // '>'
                if (highNibble >= 0) out.write(highNibble shl 4)
                return PdfValue.Str(
                    PdfStringToken(out.toByteArray(), hex = true, rawStart = rawStart, rawEnd = pos),
                )
            }
            if (isWhitespace(byte)) continue
            val nibble = hexValue(byte)
            if (nibble < 0) throw PdfSyntaxException("invalid hex digit in string at ${pos - 1}")
            if (highNibble < 0) {
                highNibble = nibble
            } else {
                out.write((highNibble shl 4) or nibble)
                highNibble = -1
            }
        }
        throw PdfSyntaxException("unterminated hex string starting at $rawStart")
    }

    private fun readNumberOrReference(): PdfValue {
        val start = pos
        val first = readLongToken() ?: throw PdfSyntaxException("expected number at $start")
        val afterFirst = pos
        if (!requireWhitespaceOrComment()) return PdfValue.Num(first)
        val second = readLongToken()
        if (second == null) {
            pos = afterFirst
            return PdfValue.Num(first)
        }
        if (!requireWhitespaceOrComment()) {
            pos = afterFirst
            return PdfValue.Num(first)
        }
        if (!matchKeyword("R")) {
            pos = afterFirst
            return PdfValue.Num(first)
        }
        pos += 1
        if (first < 0 || first > Int.MAX_VALUE || second < 0 || second > 65_535) {
            throw PdfSyntaxException("invalid indirect reference at $start")
        }
        return PdfValue.Ref(first.toInt(), second.toInt())
    }

    private fun readOtherToken(): PdfValue.Other {
        val start = pos
        while (pos < limit) {
            val byte = bytes[pos].toInt() and 0xFF
            if (isWhitespaceOrDelimiter(byte)) break
            pos++
        }
        if (pos == start) throw PdfSyntaxException("unexpected byte 0x${(bytes[pos].toInt() and 0xFF).toString(16)} at $pos")
        return PdfValue.Other(start, pos)
    }

    companion object {
        fun isWhitespace(byte: Int): Boolean =
            byte == 0x00 || byte == 0x09 || byte == 0x0A || byte == 0x0C || byte == 0x0D || byte == 0x20

        fun isDelimiter(byte: Int): Boolean = when (byte) {
            0x28, 0x29, 0x3C, 0x3E, 0x5B, 0x5D, 0x7B, 0x7D, 0x2F, 0x25 -> true
            else -> false
        }

        fun isWhitespaceOrDelimiter(byte: Int): Boolean = isWhitespace(byte) || isDelimiter(byte)

        fun hexValue(byte: Int): Int = when (byte) {
            in 0x30..0x39 -> byte - 0x30
            in 0x41..0x46 -> byte - 0x41 + 10
            in 0x61..0x66 -> byte - 0x61 + 10
            else -> -1
        }

        fun indexOf(bytes: ByteArray, needle: ByteArray, from: Int, to: Int): Int {
            if (needle.isEmpty()) return from
            val end = (to - needle.size).coerceAtMost(bytes.size - needle.size)
            var index = from.coerceAtLeast(0)
            while (index <= end) {
                var matches = true
                for (offset in needle.indices) {
                    if (bytes[index + offset] != needle[offset]) {
                        matches = false
                        break
                    }
                }
                if (matches) return index
                index++
            }
            return -1
        }

        fun lastIndexOf(bytes: ByteArray, needle: ByteArray, from: Int, to: Int): Int {
            if (needle.isEmpty()) return to
            var index = (to - needle.size).coerceAtMost(bytes.size - needle.size)
            val lower = from.coerceAtLeast(0)
            while (index >= lower) {
                var matches = true
                for (offset in needle.indices) {
                    if (bytes[index + offset] != needle[offset]) {
                        matches = false
                        break
                    }
                }
                if (matches) return index
                index--
            }
            return -1
        }

        fun ascii(text: String): ByteArray = text.toByteArray(Charsets.US_ASCII)
    }
}

internal data class PdfObjectHeader(
    val number: Int,
    val generation: Int,
    val start: Int,
    val end: Int,
)

/** 把 [PdfSyntaxException] 映射成带 code 的对外异常，不吞掉其他 typed error。 */
internal inline fun <T> withSyntaxCode(code: PdfAigcErrorCode, block: () -> T): T =
    try {
        block()
    } catch (error: PdfSyntaxException) {
        throw PdfAigcInjectionException(code, error.message ?: "pdf syntax error", cause = error)
    }
