package com.tingyun.smartmistakebook.core.data.pdf

import java.util.zip.DataFormatException
import java.util.zip.Inflater

/** 注入器资源上限（R1 §5.2 D 类）。默认值与 24 MB 导出上限对齐。 */
data class PdfAigcLimits(
    val maxInputBytes: Int = 24 * 1024 * 1024,
    val maxOutputBytes: Int = 24 * 1024 * 1024,
    val maxDecodedXrefStreamBytes: Int = 16 * 1024 * 1024,
    val maxXrefSections: Int = 512,
)

internal sealed class PdfXrefEntry {
    data class InUse(val offset: Long, val generation: Int) : PdfXrefEntry()
    data class Free(val nextFree: Long, val generation: Int) : PdfXrefEntry()
    data class Compressed(val objectStream: Int, val index: Int) : PdfXrefEntry()
}

internal sealed interface PdfXrefSection {
    val offset: Long
    val dictionary: PdfValue.Dict
    val entries: Map<Int, PdfXrefEntry>
    val isStream: Boolean

    data class Table(
        override val offset: Long,
        override val dictionary: PdfValue.Dict,
        override val entries: Map<Int, PdfXrefEntry>,
    ) : PdfXrefSection {
        override val isStream: Boolean get() = false
    }

    data class XrefStream(
        override val offset: Long,
        override val dictionary: PdfValue.Dict,
        override val entries: Map<Int, PdfXrefEntry>,
    ) : PdfXrefSection {
        override val isStream: Boolean get() = true
    }
}

/**
 * 文档头、尾 `startxref` 与线性化探针（R1 §2.2、§2.10、§5.2）。
 *
 * 所有偏移都是**从文件第一个字节起算的绝对字节偏移**；不把行号/对象内偏移当偏移。
 */
internal object PdfDocumentProbe {

    private val HEADER = PdfLexer.ascii("%PDF-")
    private val STARTXREF = PdfLexer.ascii("startxref")
    private val LINEARIZED = PdfLexer.ascii("/Linearized")
    private const val TAIL_WINDOW_BYTES = 64 * 1024
    private const val LINEARIZED_PREFIX_BYTES = 8 * 1024

    fun validateHeader(source: ByteArray, limits: PdfAigcLimits) {
        if (source.size > limits.maxInputBytes) {
            throw PdfAigcInjectionException(
                PdfAigcErrorCode.INPUT_TOO_LARGE,
                "input is ${source.size} bytes, limit is ${limits.maxInputBytes}",
            )
        }
        if (source.size < 8) {
            throw PdfAigcInjectionException(PdfAigcErrorCode.HEADER_TRUNCATED, "file is shorter than 8 bytes")
        }
        for (index in HEADER.indices) {
            if (source[index] != HEADER[index]) {
                throw PdfAigcInjectionException(PdfAigcErrorCode.NOT_PDF_HEADER, "missing %PDF- header")
            }
        }
        val major = source[5].toInt() and 0xFF
        val dot = source[6].toInt() and 0xFF
        val minor = source[7].toInt() and 0xFF
        val versionValid = dot == '.'.code &&
            ((major == '1'.code && minor in '0'.code..'9'.code) || (major == '2'.code && minor == '0'.code))
        if (!versionValid) {
            throw PdfAigcInjectionException(
                PdfAigcErrorCode.HEADER_VERSION_INVALID,
                "unsupported PDF version header",
            )
        }
    }

    /** 从尾部向前找最后一个真实 `startxref`，返回其十进制偏移。 */
    fun findStartxref(source: ByteArray): Long {
        val windowStart = (source.size - TAIL_WINDOW_BYTES).coerceAtLeast(0)
        var searchTo = source.size
        while (true) {
            val index = PdfLexer.lastIndexOf(source, STARTXREF, windowStart, searchTo)
            if (index < 0) {
                throw PdfAigcInjectionException(PdfAigcErrorCode.STARTXREF_MISSING, "no startxref in tail window")
            }
            val lexer = PdfLexer(source, index, source.size)
            if (!lexer.matchKeyword("startxref")) {
                searchTo = index
                continue
            }
            lexer.pos += STARTXREF.size
            lexer.skipWhitespaceAndComments()
            val offset = lexer.readLongToken()
                ?: throw PdfAigcInjectionException(
                    PdfAigcErrorCode.STARTXREF_NOT_INTEGER,
                    "startxref is not a decimal integer",
                    diagnostic = "startxref@$index",
                )
            if (offset <= 0L || offset >= source.size.toLong()) {
                throw PdfAigcInjectionException(
                    PdfAigcErrorCode.STARTXREF_OUT_OF_RANGE,
                    "startxref offset $offset is outside 0..${source.size}",
                    diagnostic = "startxref@$index",
                )
            }
            return offset
        }
    }

    /**
     * 线性化文件拒绝（R1 §2.10）：前 8 KiB 粗筛 `/Linearized`，再解析**第一个间接对象**确认，
     * 避免把正文/注释里的同名字面量误判成线性化。
     */
    fun rejectLinearized(source: ByteArray) {
        val prefixLimit = minOf(source.size, LINEARIZED_PREFIX_BYTES)
        if (PdfLexer.indexOf(source, LINEARIZED, 0, prefixLimit) < 0) return
        val firstObject = firstObjectDictionary(source, prefixLimit) ?: return
        if (firstObject["Linearized"] != null) {
            throw PdfAigcInjectionException(
                PdfAigcErrorCode.LINEARIZED,
                "linearized PDFs are not supported for incremental /AIGC updates",
            )
        }
    }

    private fun firstObjectDictionary(source: ByteArray, limit: Int): PdfValue.Dict? {
        var search = 0
        while (search < limit) {
            val lexer = PdfLexer(source, search, limit)
            lexer.skipWhitespaceAndComments()
            val start = lexer.pos
            val header = lexer.readObjectHeader() ?: run {
                search = start + 1
                continue
            }
            lexer.skipWhitespaceAndComments()
            return try {
                val value = lexer.parseValue()
                value as? PdfValue.Dict
            } catch (_: PdfSyntaxException) {
                // 粗筛命中但首个对象不可解析：不拒绝（R1 §8.7 的"确认步骤"要求）。
                null
            }
        }
        return null
    }
}

/**
 * 交叉引用链解析（R1 §2.3–§2.6）。
 *
 * 新段用哪个形态由**上一段**决定；Info 的 `(对象号, 生成号)` 沿 `/Prev` 链查找，最新段优先。
 */
internal class PdfCrossReferenceIndex private constructor(
    val source: ByteArray,
    val sections: List<PdfXrefSection>,
    val entries: Map<Int, PdfXrefEntry>,
    val size: Long,
    val highestObjectNumber: Int,
    val startxref: Long,
) {

    val newestTrailer: PdfValue.Dict get() = sections.first().dictionary
    val previousIsStream: Boolean get() = sections.first().isStream

    fun resolve(number: Int): PdfXrefEntry? = entries[number]

    companion object {
        private const val MAX_OBJECT_NUMBER = 8_388_607L

        fun parse(source: ByteArray, limits: PdfAigcLimits): PdfCrossReferenceIndex {
            val startxref = PdfDocumentProbe.findStartxref(source)
            val sections = ArrayList<PdfXrefSection>()
            val seen = HashSet<Long>()
            var current: Long? = startxref
            while (current != null) {
                if (!seen.add(current)) {
                    throw PdfAigcInjectionException(
                        PdfAigcErrorCode.TRAILER_MALFORMED,
                        "cyclic /Prev chain at $current",
                    )
                }
                if (sections.size >= limits.maxXrefSections) {
                    throw PdfAigcInjectionException(
                        PdfAigcErrorCode.TRAILER_MALFORMED,
                        "xref chain exceeds ${limits.maxXrefSections} sections",
                    )
                }
                val section = parseSection(source, current, limits)
                rejectUnsupportedTrailerFeatures(section.dictionary)
                sections.add(section)
                current = readPrev(section.dictionary, source.size)
            }
            if (sections.isEmpty()) {
                throw PdfAigcInjectionException(PdfAigcErrorCode.STARTXREF_MISSING, "empty xref chain")
            }

            val merged = LinkedHashMap<Int, PdfXrefEntry>()
            for (section in sections) {
                for ((number, entry) in section.entries) {
                    if (!merged.containsKey(number)) merged[number] = entry
                }
            }

            val newest = sections.first()
            val declaredSize = requireSize(newest.dictionary)
            val highest = merged.keys.maxOrNull() ?: 0
            if (highest.toLong() > MAX_OBJECT_NUMBER) {
                throw PdfAigcInjectionException(
                    PdfAigcErrorCode.OBJECT_NUMBER_LIMIT_EXCEEDED,
                    "object number $highest exceeds $MAX_OBJECT_NUMBER",
                )
            }
            validateFreeHead(sections, merged)

            return PdfCrossReferenceIndex(
                source = source,
                sections = sections,
                entries = merged,
                size = declaredSize,
                highestObjectNumber = highest,
                startxref = startxref,
            )
        }

        private fun parseSection(
            source: ByteArray,
            offset: Long,
            limits: PdfAigcLimits,
        ): PdfXrefSection {
            if (offset <= 0L || offset >= source.size.toLong()) {
                throw PdfAigcInjectionException(
                    PdfAigcErrorCode.XREF_KEYWORD_MISSING,
                    "xref offset $offset outside file",
                )
            }
            val lexer = PdfLexer(source, offset.toInt(), source.size)
            lexer.skipWhitespaceAndComments()
            if (lexer.matchKeyword("xref")) {
                return parseTable(source, offset, lexer)
            }
            val header = lexer.readObjectHeader()
                ?: throw PdfAigcInjectionException(
                    PdfAigcErrorCode.XREF_KEYWORD_MISSING,
                    "offset $offset is neither 'xref' nor an object header",
                )
            return parseXrefStream(source, offset, header, limits)
        }

        private fun parseTable(
            source: ByteArray,
            offset: Long,
            lexer: PdfLexer,
        ): PdfXrefSection.Table {
            lexer.pos += 4 // xref
            val entries = LinkedHashMap<Int, PdfXrefEntry>()
            while (true) {
                lexer.skipWhitespaceAndComments()
                if (lexer.matchKeyword("trailer")) {
                    lexer.pos += 7
                    val dictionary = withSyntaxCode(PdfAigcErrorCode.TRAILER_MALFORMED) {
                        lexer.readDictionary()
                    }
                    return PdfXrefSection.Table(offset, dictionary, entries)
                }
                if (lexer.atEnd()) {
                    throw PdfAigcInjectionException(
                        PdfAigcErrorCode.TRAILER_MISSING,
                        "xref table at $offset has no trailer",
                    )
                }
                val start = lexer.readLongToken()
                    ?: throw PdfAigcInjectionException(
                        PdfAigcErrorCode.XREF_TABLE_MALFORMED,
                        "invalid subsection header at ${lexer.pos}",
                    )
                if (!lexer.requireWhitespaceOrComment()) {
                    throw PdfAigcInjectionException(
                        PdfAigcErrorCode.XREF_TABLE_MALFORMED,
                        "missing whitespace after subsection start",
                    )
                }
                val count = lexer.readLongToken()
                    ?: throw PdfAigcInjectionException(
                        PdfAigcErrorCode.XREF_TABLE_MALFORMED,
                        "invalid subsection count at ${lexer.pos}",
                    )
                if (start < 0 || count < 0 || start + count > Int.MAX_VALUE) {
                    throw PdfAigcInjectionException(
                        PdfAigcErrorCode.XREF_TABLE_MALFORMED,
                        "invalid subsection [$start $count]",
                    )
                }
                lexer.skipWhitespaceAndComments()
                for (index in 0 until count.toInt()) {
                    val entryStart = lexer.pos
                    if (entryStart + 20 > source.size) {
                        throw PdfAigcInjectionException(
                            PdfAigcErrorCode.XREF_TABLE_MALFORMED,
                            "truncated xref entry at $entryStart",
                        )
                    }
                    val entry = parseTableEntry(source, entryStart)
                    val objectNumber = (start + index).toInt()
                    if (entries.put(objectNumber, entry) != null) {
                        throw PdfAigcInjectionException(
                            PdfAigcErrorCode.XREF_TABLE_MALFORMED,
                            "duplicate object $objectNumber in one xref subsection",
                        )
                    }
                    lexer.pos = entryStart + 20
                }
            }
        }

        private fun parseTableEntry(source: ByteArray, start: Int): PdfXrefEntry {
            fun digits(from: Int, count: Int): Long {
                var value = 0L
                for (index in 0 until count) {
                    val byte = source[from + index].toInt() and 0xFF
                    if (byte < '0'.code || byte > '9'.code) {
                        throw PdfAigcInjectionException(
                            PdfAigcErrorCode.XREF_TABLE_MALFORMED,
                            "non-digit in xref entry at ${from + index}",
                        )
                    }
                    value = value * 10 + (byte - '0'.code)
                }
                return value
            }

            val offset = digits(start, 10)
            if (source[start + 10].toInt() and 0xFF != ' '.code) {
                throw PdfAigcInjectionException(PdfAigcErrorCode.XREF_TABLE_MALFORMED, "missing space in xref entry")
            }
            val generation = digits(start + 11, 5).toInt()
            if (source[start + 16].toInt() and 0xFF != ' '.code) {
                throw PdfAigcInjectionException(PdfAigcErrorCode.XREF_TABLE_MALFORMED, "missing space in xref entry")
            }
            val marker = source[start + 17].toInt() and 0xFF
            val eol0 = source[start + 18].toInt() and 0xFF
            val eol1 = source[start + 19].toInt() and 0xFF
            val validEol = (eol0 == ' '.code && (eol1 == 0x0D || eol1 == 0x0A)) ||
                (eol0 == 0x0D && eol1 == 0x0A)
            if (!validEol) {
                throw PdfAigcInjectionException(
                    PdfAigcErrorCode.XREF_TABLE_MALFORMED,
                    "invalid 20-byte xref entry terminator at $start",
                )
            }
            return when (marker) {
                'n'.code -> PdfXrefEntry.InUse(offset, generation)
                'f'.code -> PdfXrefEntry.Free(offset, generation)
                else -> throw PdfAigcInjectionException(
                    PdfAigcErrorCode.XREF_TABLE_MALFORMED,
                    "invalid xref entry type '${marker.toChar()}' at $start",
                )
            }
        }

        private fun parseXrefStream(
            source: ByteArray,
            offset: Long,
            header: PdfObjectHeader,
            limits: PdfAigcLimits,
        ): PdfXrefSection.XrefStream {
            val lexer = PdfLexer(source, header.end, source.size)
            lexer.skipWhitespaceAndComments()
            val dictionary = withSyntaxCode(PdfAigcErrorCode.XREF_STREAM_MALFORMED) {
                lexer.readDictionary()
            }
            val type = dictionary["Type"] as? PdfValue.Name
            if (type == null || type.value != "XRef") {
                throw PdfAigcInjectionException(
                    PdfAigcErrorCode.XREF_STREAM_MALFORMED,
                    "object at $offset is not /Type /XRef",
                )
            }
            val widths = readWidths(dictionary)
            val declaredSize = requireSize(dictionary)
            val indexRanges = readIndexRanges(dictionary, declaredSize)
            val length = (dictionary["Length"] as? PdfValue.Num)?.value
                ?: throw PdfAigcInjectionException(
                    PdfAigcErrorCode.XREF_STREAM_MALFORMED,
                    "xref stream /Length must be a direct integer",
                )
            if (length < 0) {
                throw PdfAigcInjectionException(
                    PdfAigcErrorCode.XREF_STREAM_MALFORMED,
                    "negative xref stream /Length",
                )
            }
            lexer.skipWhitespaceAndComments()
            if (!lexer.matchKeyword("stream")) {
                throw PdfAigcInjectionException(
                    PdfAigcErrorCode.XREF_STREAM_MALFORMED,
                    "xref stream object has no 'stream' keyword",
                )
            }
            lexer.pos += 6
            // stream 关键字后允许 CRLF / LF / CR；其它字节不宽纵。
            if (lexer.pos < source.size && source[lexer.pos].toInt() and 0xFF == 0x0D) {
                lexer.pos++
                if (lexer.pos < source.size && source[lexer.pos].toInt() and 0xFF == 0x0A) lexer.pos++
            } else if (lexer.pos < source.size && source[lexer.pos].toInt() and 0xFF == 0x0A) {
                lexer.pos++
            } else {
                throw PdfAigcInjectionException(
                    PdfAigcErrorCode.XREF_STREAM_MALFORMED,
                    "missing EOL after xref stream keyword",
                )
            }
            val dataStart = lexer.pos
            if (length > Int.MAX_VALUE || dataStart + length > source.size) {
                throw PdfAigcInjectionException(
                    PdfAigcErrorCode.XREF_STREAM_DATA_TRUNCATED,
                    "xref stream data exceeds file size",
                )
            }
            val dataEnd = dataStart + length.toInt()
            val afterData = PdfLexer(source, dataEnd, source.size)
            afterData.skipWhitespaceAndComments()
            if (!afterData.matchKeyword("endstream")) {
                throw PdfAigcInjectionException(
                    PdfAigcErrorCode.XREF_STREAM_DATA_TRUNCATED,
                    "xref stream data is not followed by endstream",
                )
            }
            val rawData = source.copyOfRange(dataStart, dataEnd)
            val decoded = decodeStreamData(rawData, dictionary, widths, limits)
            val entries = readStreamEntries(decoded, widths, indexRanges)
            return PdfXrefSection.XrefStream(offset, dictionary, entries)
        }

        private fun readWidths(dictionary: PdfValue.Dict): IntArray {
            val array = dictionary["W"] as? PdfValue.Arr
                ?: throw PdfAigcInjectionException(
                    PdfAigcErrorCode.XREF_STREAM_W_INVALID,
                    "xref stream has no /W array",
                )
            if (array.items.size != 3) {
                throw PdfAigcInjectionException(
                    PdfAigcErrorCode.XREF_STREAM_W_INVALID,
                    "/W must have exactly 3 integers",
                )
            }
            val widths = IntArray(3)
            for (index in 0..2) {
                val value = (array.items[index] as? PdfValue.Num)?.value
                    ?: throw PdfAigcInjectionException(
                        PdfAigcErrorCode.XREF_STREAM_W_INVALID,
                        "/W[$index] is not an integer",
                    )
                if (value < 0 || value > 8) {
                    throw PdfAigcInjectionException(
                        PdfAigcErrorCode.XREF_STREAM_W_INVALID,
                        "/W[$index]=$value is outside 0..8",
                    )
                }
                widths[index] = value.toInt()
            }
            if (widths.sum() == 0) {
                throw PdfAigcInjectionException(PdfAigcErrorCode.XREF_STREAM_W_INVALID, "/W sums to zero")
            }
            return widths
        }

        private fun readIndexRanges(dictionary: PdfValue.Dict, size: Long): List<Pair<Int, Int>> {
            val index = dictionary["Index"] as? PdfValue.Arr
            if (index == null) {
                if (size > Int.MAX_VALUE) {
                    throw PdfAigcInjectionException(
                        PdfAigcErrorCode.XREF_STREAM_INDEX_INVALID,
                        "default /Index [0 Size] exceeds Int range",
                    )
                }
                return listOf(0 to size.toInt())
            }
            if (index.items.size % 2 != 0) {
                throw PdfAigcInjectionException(
                    PdfAigcErrorCode.XREF_STREAM_INDEX_INVALID,
                    "/Index must contain pairs",
                )
            }
            val ranges = ArrayList<Pair<Int, Int>>(index.items.size / 2)
            var previousEnd = -1
            for (pair in 0 until index.items.size / 2) {
                val startValue = (index.items[pair * 2] as? PdfValue.Num)?.value
                val countValue = (index.items[pair * 2 + 1] as? PdfValue.Num)?.value
                if (startValue == null || countValue == null || startValue < 0 || countValue < 0) {
                    throw PdfAigcInjectionException(
                        PdfAigcErrorCode.XREF_STREAM_INDEX_INVALID,
                        "/Index contains a negative or non-integer value",
                    )
                }
                if (startValue > Int.MAX_VALUE || countValue > Int.MAX_VALUE) {
                    throw PdfAigcInjectionException(
                        PdfAigcErrorCode.XREF_STREAM_INDEX_INVALID,
                        "/Index value exceeds Int range",
                    )
                }
                val start = startValue.toInt()
                val count = countValue.toInt()
                if (start.toLong() + count.toLong() > size) {
                    throw PdfAigcInjectionException(
                        PdfAigcErrorCode.XREF_STREAM_INDEX_INVALID,
                        "/Index range [$start $count] exceeds /Size $size",
                    )
                }
                if (count > 0 && start <= previousEnd) {
                    throw PdfAigcInjectionException(
                        PdfAigcErrorCode.XREF_STREAM_INDEX_INVALID,
                        "/Index ranges overlap or are unsorted",
                    )
                }
                if (count > 0) previousEnd = start + count - 1
                ranges.add(start to count)
            }
            return ranges
        }

        private fun decodeStreamData(
            raw: ByteArray,
            dictionary: PdfValue.Dict,
            widths: IntArray,
            limits: PdfAigcLimits,
        ): ByteArray {
            val filter = dictionary["Filter"]
            val flate = when (filter) {
                null -> false
                is PdfValue.Name -> when (filter.value) {
                    "FlateDecode", "Fl" -> true
                    else -> throw unsupportedFilter(filter.value)
                }
                is PdfValue.Arr -> {
                    if (filter.items.size != 1) throw unsupportedFilter("filter array")
                    val only = filter.items[0] as? PdfValue.Name
                        ?: throw unsupportedFilter("non-name filter")
                    when (only.value) {
                        "FlateDecode", "Fl" -> true
                        else -> throw unsupportedFilter(only.value)
                    }
                }
                else -> throw unsupportedFilter("non-name /Filter")
            }
            val inflated = if (flate) inflate(raw, limits) else raw
            val predictor = readPredictor(dictionary)
            return when (predictor) {
                1 -> inflated
                // PDF 的 PNG predictor（10–15）每行都以一个 filter 字节开头；10–14 是固定
                // filter 的提示，15 是逐行选择。pypdf/PyMuPDF 都按"每行一个 lead byte"解码，
                // 本实现与真实读者对齐（R1 §8.7 的 Predictor 12 未跑项）。
                in 10..15 -> undoPngPredictor(
                    inflated,
                    columns = readColumns(dictionary, widths),
                )
                else -> throw unsupportedFilter("predictor $predictor")
            }
        }

        private fun inflate(raw: ByteArray, limits: PdfAigcLimits): ByteArray {
            val inflater = Inflater()
            try {
                inflater.setInput(raw)
                val output = java.io.ByteArrayOutputStream(minOf(raw.size * 2 + 64, 64 * 1024))
                val buffer = ByteArray(16 * 1024)
                var total = 0
                while (!inflater.finished()) {
                    val read = try {
                        inflater.inflate(buffer)
                    } catch (error: DataFormatException) {
                        throw PdfAigcInjectionException(
                            PdfAigcErrorCode.XREF_STREAM_MALFORMED,
                            "FlateDecode failed: ${error.message}",
                            cause = error,
                        )
                    }
                    if (read == 0) {
                        if (inflater.needsInput() || inflater.needsDictionary()) {
                            throw PdfAigcInjectionException(
                                PdfAigcErrorCode.XREF_STREAM_DATA_TRUNCATED,
                                "truncated FlateDecode stream",
                            )
                        }
                    } else {
                        total += read
                        if (total > limits.maxDecodedXrefStreamBytes) {
                            throw PdfAigcInjectionException(
                                PdfAigcErrorCode.DECODED_STREAM_TOO_LARGE,
                                "decoded xref stream exceeds ${limits.maxDecodedXrefStreamBytes} bytes",
                            )
                        }
                        output.write(buffer, 0, read)
                    }
                }
                return output.toByteArray()
            } finally {
                inflater.end()
            }
        }

        private fun decodeParms(dictionary: PdfValue.Dict): PdfValue.Dict? =
            when (val value = dictionary["DecodeParms"]) {
                null -> null
                is PdfValue.Dict -> value
                is PdfValue.Arr -> {
                    if (value.items.size != 1) throw unsupportedFilter("DecodeParms array")
                    (value.items[0] as? PdfValue.Dict) ?: throw unsupportedFilter("DecodeParms element")
                }
                else -> throw unsupportedFilter("non-dictionary DecodeParms")
            }

        private fun readPredictor(dictionary: PdfValue.Dict): Int {
            val parms = decodeParms(dictionary) ?: return 1
            val predictor = (parms["Predictor"] as? PdfValue.Num)?.value ?: 1L
            if (predictor < 1 || predictor > Int.MAX_VALUE) throw unsupportedFilter("predictor $predictor")
            return predictor.toInt()
        }

        private fun readColumns(dictionary: PdfValue.Dict, widths: IntArray): Int {
            val parms = decodeParms(dictionary) ?: return widths.sum()
            val columns = (parms["Columns"] as? PdfValue.Num)?.value ?: return widths.sum()
            if (columns <= 0 || columns > 1_000_000) throw unsupportedFilter("Columns $columns")
            return columns.toInt()
        }

        /**
         * PNG predictor 反演（R1 §2.5 要求支持 `/DecodeParms /Predictor 12`）。
         *
         * Predictor 10–15 的每一行都是 `[filter][columns 字节]`；filter ∈ 0..4。pypdf 与
         * PyMuPDF 都按这个布局解码（本轮独立读者实测：无 lead byte 的写法会让 PyMuPDF 报
         * "Unsupported PNG filter 87"）。固定 predictor 10–14 的 lead byte 仍按实际值处理，
         * 与真实读者一致。
         *
         * bpp 固定 1：xref 流字段按字节解释，Colors=1、BitsPerComponent=8。
         */
        private fun undoPngPredictor(
            data: ByteArray,
            columns: Int,
        ): ByteArray {
            if (columns <= 0) throw unsupportedFilter("Columns $columns")
            val rowSize = columns
            val sourceRowSize = rowSize + 1
            if (data.size % sourceRowSize != 0) {
                throw PdfAigcInjectionException(
                    PdfAigcErrorCode.XREF_STREAM_DATA_TRUNCATED,
                    "predictor row size does not divide decoded data",
                )
            }
            val rows = data.size / sourceRowSize
            val output = ByteArray(rows * rowSize)
            val previous = ByteArray(rowSize)
            for (row in 0 until rows) {
                val sourceOffset = row * sourceRowSize
                val filter = data[sourceOffset].toInt() and 0xFF
                if (filter !in 0..4) throw unsupportedFilter("PNG filter $filter")
                val rowOffset = row * rowSize
                for (column in 0 until rowSize) {
                    val raw = data[sourceOffset + 1 + column].toInt() and 0xFF
                    val left = if (column > 0) output[rowOffset + column - 1].toInt() and 0xFF else 0
                    val up = previous[column].toInt() and 0xFF
                    val upLeft = if (column > 0) previous[column - 1].toInt() and 0xFF else 0
                    val value = when (filter) {
                        0 -> raw
                        1 -> raw + left
                        2 -> raw + up
                        3 -> raw + ((left + up) ushr 1)
                        else -> raw + paeth(left, up, upLeft)
                    }
                    output[rowOffset + column] = value.toByte()
                }
                System.arraycopy(output, rowOffset, previous, 0, rowSize)
            }
            return output
        }

        private fun paeth(left: Int, up: Int, upLeft: Int): Int {
            val prediction = left + up - upLeft
            val leftDistance = kotlin.math.abs(prediction - left)
            val upDistance = kotlin.math.abs(prediction - up)
            val upLeftDistance = kotlin.math.abs(prediction - upLeft)
            return when {
                leftDistance <= upDistance && leftDistance <= upLeftDistance -> left
                upDistance <= upLeftDistance -> up
                else -> upLeft
            }
        }

        private fun unsupportedFilter(detail: String): PdfAigcInjectionException =
            PdfAigcInjectionException(
                PdfAigcErrorCode.XREF_STREAM_UNSUPPORTED_FILTER,
                "unsupported xref stream filter/predictor: $detail",
            )

        private fun readStreamEntries(
            decoded: ByteArray,
            widths: IntArray,
            ranges: List<Pair<Int, Int>>,
        ): Map<Int, PdfXrefEntry> {
            val rowBytes = widths.sum()
            if (rowBytes <= 0) {
                throw PdfAigcInjectionException(PdfAigcErrorCode.XREF_STREAM_W_INVALID, "/W sums to zero")
            }
            val entries = LinkedHashMap<Int, PdfXrefEntry>()
            var cursor = 0
            for ((start, count) in ranges) {
                for (index in 0 until count) {
                    if (cursor + rowBytes > decoded.size) {
                        throw PdfAigcInjectionException(
                            PdfAigcErrorCode.XREF_STREAM_DATA_TRUNCATED,
                            "xref stream data ends before /Index entries",
                        )
                    }
                    val type = if (widths[0] == 0) {
                        1L
                    } else {
                        readUnsigned(decoded, cursor, widths[0])
                    }
                    cursor += widths[0]
                    val field2 = readUnsigned(decoded, cursor, widths[1])
                    cursor += widths[1]
                    val field3 = readUnsigned(decoded, cursor, widths[2])
                    cursor += widths[2]
                    val objectNumber = start + index
                    val entry = when (type) {
                        0L -> PdfXrefEntry.Free(field2, field3.toInt())
                        1L -> PdfXrefEntry.InUse(field2, field3.toInt())
                        2L -> PdfXrefEntry.Compressed(field2.toInt(), field3.toInt())
                        else -> throw PdfAigcInjectionException(
                            PdfAigcErrorCode.XREF_STREAM_MALFORMED,
                            "unknown xref stream entry type $type for object $objectNumber",
                        )
                    }
                    if (entries.put(objectNumber, entry) != null) {
                        throw PdfAigcInjectionException(
                            PdfAigcErrorCode.XREF_STREAM_MALFORMED,
                            "duplicate object $objectNumber in xref stream",
                        )
                    }
                }
            }
            return entries
        }

        private fun readUnsigned(bytes: ByteArray, start: Int, width: Int): Long {
            var value = 0L
            for (index in 0 until width) {
                value = (value shl 8) or (bytes[start + index].toLong() and 0xFF)
            }
            return value
        }

        private fun readPrev(dictionary: PdfValue.Dict, fileSize: Int): Long? {
            val value = dictionary["Prev"] ?: return null
            val number = (value as? PdfValue.Num)?.value
                ?: throw PdfAigcInjectionException(PdfAigcErrorCode.TRAILER_MALFORMED, "/Prev is not an integer")
            if (number <= 0L || number >= fileSize.toLong()) {
                throw PdfAigcInjectionException(
                    PdfAigcErrorCode.TRAILER_MALFORMED,
                    "/Prev $number is outside file",
                )
            }
            return number
        }

        private fun rejectUnsupportedTrailerFeatures(dictionary: PdfValue.Dict) {
            if (dictionary["Encrypt"] != null) {
                throw PdfAigcInjectionException(PdfAigcErrorCode.ENCRYPTED, "encrypted PDFs are not supported")
            }
            if (dictionary["XRefStm"] != null) {
                throw PdfAigcInjectionException(PdfAigcErrorCode.HYBRID_XREF, "hybrid-reference PDFs are not supported")
            }
        }

        private fun validateFreeHead(
            sections: List<PdfXrefSection>,
            entries: Map<Int, PdfXrefEntry>,
        ) {
            val head = entries[0]
            if (head == null) {
                // 纯流型文档允许从上一段继承对象 0；表型（Skia 产物）必须有 free 头。
                if (sections.any { !it.isStream }) {
                    throw PdfAigcInjectionException(
                        PdfAigcErrorCode.XREF_FREE_HEAD_MISSING,
                        "xref chain has no object 0 free head",
                    )
                }
                return
            }
            if (head !is PdfXrefEntry.Free || head.generation != 65_535) {
                throw PdfAigcInjectionException(
                    PdfAigcErrorCode.XREF_FREE_HEAD_MISSING,
                    "object 0 is not a free head with generation 65535",
                )
            }
        }

        internal fun requireSize(dictionary: PdfValue.Dict): Long {
            val value = dictionary["Size"]
                ?: throw PdfAigcInjectionException(PdfAigcErrorCode.SIZE_MISSING, "trailer has no /Size")
            val size = (value as? PdfValue.Num)?.value
                ?: throw PdfAigcInjectionException(PdfAigcErrorCode.SIZE_INVALID, "/Size is not an integer")
            if (size < 0) {
                throw PdfAigcInjectionException(PdfAigcErrorCode.SIZE_INVALID, "/Size is negative")
            }
            return size
        }
    }
}

/** 供注入器/读回器复用的 trailer/Info 取值辅助。 */
internal object PdfDictionaryOps {

    fun string(dictionary: PdfValue.Dict, key: String): String? =
        (dictionary[key] as? PdfValue.Str)?.token?.decodedText()

    fun name(dictionary: PdfValue.Dict, key: String): String? =
        (dictionary[key] as? PdfValue.Name)?.value

    fun ref(dictionary: PdfValue.Dict, key: String): PdfValue.Ref? = dictionary[key] as? PdfValue.Ref

    fun requireRoot(dictionary: PdfValue.Dict): PdfValue.Ref {
        val value = dictionary["Root"]
            ?: throw PdfAigcInjectionException(PdfAigcErrorCode.TRAILER_ROOT_MISSING, "trailer has no /Root")
        return value as? PdfValue.Ref
            ?: throw PdfAigcInjectionException(
                PdfAigcErrorCode.TRAILER_ROOT_NOT_INDIRECT,
                "/Root is not an indirect reference",
            )
    }

    fun validateId(dictionary: PdfValue.Dict) {
        val id = dictionary["ID"] ?: return
        val array = id as? PdfValue.Arr
            ?: throw PdfAigcInjectionException(PdfAigcErrorCode.ID_MALFORMED, "/ID is not an array")
        if (array.items.size != 2 || array.items.any { it !is PdfValue.Str }) {
            throw PdfAigcInjectionException(
                PdfAigcErrorCode.ID_MALFORMED,
                "/ID must be an array of exactly two strings",
            )
        }
    }
}
