package com.tingyun.smartmistakebook.core.data.pdf

import java.security.MessageDigest

/**
 * PDF 增量更新 `/AIGC` 注入器（R1 §7.2；WS1 1–3、5、7）。
 *
 * **只追加，不改原字节**：原文件 + 换行（若需要）+ 新 Info 对象副本 + 同型 xref 段 +
 * 带直接 `/Prev` 的 trailer/xref 流 + `%%EOF`。读者以最新修订为准（ISO 32000-1 §7.5.6）。
 *
 * **Info 合并不覆盖**：对既有 Info 对象做 token-aware 字节拼接，只在最外层 `>>` 前插入
 * `/AIGC`；Title/Author/Subject/未知自定义键逐字节保留。已有 `/AIGC` 且值相同 → 返回原字节
 * （字节幂等）；值不同 → 只替换值 token 并追加新修订。
 *
 * **fail-closed**：任何无法证明"能安全追加"的输入抛 [PdfAigcInjectionException]，不返回半成品。
 *
 * 线程安全：无共享可变状态，实例可复用。
 */
class PdfAigcInjector(
    private val limits: PdfAigcLimits = PdfAigcLimits(),
) {

    /** 以已校验的 [PdfAigcMetadata] 注入。 */
    fun inject(source: ByteArray, metadata: PdfAigcMetadata): ByteArray {
        val json = metadata.toJson()
        PdfDocumentProbe.validateHeader(source, limits)
        PdfDocumentProbe.rejectLinearized(source)

        val index = PdfCrossReferenceIndex.parse(source, limits)
        val trailer = index.newestTrailer
        PdfDictionaryOps.requireRoot(trailer)
        PdfDictionaryOps.validateId(trailer)

        val existingInfo = resolveInfoObject(source, index, trailer)
        val jsonToken = literalString(json)

        val newInfoBytes: ByteArray
        val informationObjectNumber: Int
        val informationGeneration: Int
        var mergeResult: MergeResult
        if (existingInfo == null) {
            informationObjectNumber = allocateObjectNumber(index, index.size)
            informationGeneration = 0
            newInfoBytes = buildNewInfoObject(informationObjectNumber, jsonToken)
            mergeResult = MergeResult.Appended(newInfoBytes)
        } else {
            informationObjectNumber = existingInfo.number
            informationGeneration = existingInfo.generation
            mergeResult = mergeInformationObject(source, existingInfo, jsonToken, json)
            if (mergeResult is MergeResult.Unchanged) return source
            newInfoBytes = (mergeResult as MergeResult.Appended).bytes
        }

        val needsEol = source.isNotEmpty() && source.last().toInt() and 0xFF !in intArrayOf(0x0A, 0x0D)
        val sourceWithEol = if (needsEol) source + byteArrayOf(0x0A) else source
        val newObjectOffset = sourceWithEol.size.toLong()
        val base = sourceWithEol + newInfoBytes

        val idDigest = sha256(base)
        val output = if (index.previousIsStream) {
            writeStreamRevision(
                source,
                base,
                index,
                informationObjectNumber,
                informationGeneration,
                newObjectOffset,
                idDigest,
            )
        } else {
            writeTableRevision(
                source,
                base,
                index,
                informationObjectNumber,
                informationGeneration,
                newObjectOffset,
                idDigest,
            )
        }
        if (output.size > limits.maxOutputBytes) {
            throw PdfAigcInjectionException(
                PdfAigcErrorCode.OUTPUT_TOO_LARGE,
                "labeled PDF is ${output.size} bytes, limit is ${limits.maxOutputBytes}",
            )
        }
        return output
    }

    /** 以 JSON 文本注入；先解析为 7 键 schema，再按 canonical 形式写入。 */
    fun inject(source: ByteArray, json: String): ByteArray = inject(source, PdfAigcMetadata.parse(json))

    // ---------------------------------------------------------------- Info 合并

    private data class ExistingInfo(
        val number: Int,
        val generation: Int,
        val objectOffset: Int,
        val dictionary: PdfValue.Dict,
    )

    private sealed interface MergeResult {
        data class Unchanged(val bytes: ByteArray) : MergeResult
        data class Appended(val bytes: ByteArray) : MergeResult
    }

    /**
     * 沿 xref 链解析 trailer 的 `/Info`。返回 null 表示"原文件没有 Info，走新建分支"
     * （R1 §4.1 的 `INFO_MISSING_ALLOWED`，不是错误）。
     */
    private fun resolveInfoObject(
        source: ByteArray,
        index: PdfCrossReferenceIndex,
        trailer: PdfValue.Dict,
    ): ExistingInfo? {
        val infoValue = trailer["Info"] ?: return null
        val reference = infoValue as? PdfValue.Ref
            ?: throw PdfAigcInjectionException(
                PdfAigcErrorCode.INFO_NOT_INDIRECT,
                "/Info is not an indirect reference",
            )
        val entry = index.resolve(reference.number)
            ?: throw PdfAigcInjectionException(
                PdfAigcErrorCode.INFO_OBJECT_MISSING,
                "/Info ${reference.number} ${reference.generation} R is not in the xref chain",
            )
        when (entry) {
            is PdfXrefEntry.Free -> throw PdfAigcInjectionException(
                PdfAigcErrorCode.INFO_OBJECT_MISSING,
                "/Info points at a free object ${reference.number}",
            )
            is PdfXrefEntry.Compressed -> throw PdfAigcInjectionException(
                PdfAigcErrorCode.INFO_IN_OBJECT_STREAM,
                "/Info object ${reference.number} lives in an object stream",
            )
            is PdfXrefEntry.InUse -> {
                if (entry.generation != reference.generation) {
                    throw PdfAigcInjectionException(
                        PdfAigcErrorCode.INFO_OBJECT_MISSING,
                        "/Info generation mismatch: xref ${entry.generation}, reference ${reference.generation}",
                    )
                }
                if (entry.offset <= 0L || entry.offset >= source.size.toLong()) {
                    throw PdfAigcInjectionException(
                        PdfAigcErrorCode.INFO_OBJECT_MISSING,
                        "/Info offset ${entry.offset} is outside file",
                    )
                }
                val lexer = PdfLexer(source, entry.offset.toInt(), source.size)
                val header = lexer.readObjectHeader()
                    ?: throw PdfAigcInjectionException(
                        PdfAigcErrorCode.INFO_OBJECT_MISSING,
                        "offset ${entry.offset} does not start with an object header",
                    )
                if (header.number != reference.number || header.generation != reference.generation) {
                    throw PdfAigcInjectionException(
                        PdfAigcErrorCode.INFO_OBJECT_MISSING,
                        "object header at ${entry.offset} is ${header.number} ${header.generation} obj, " +
                            "expected ${reference.number} ${reference.generation} obj",
                    )
                }
                lexer.skipWhitespaceAndComments()
                val value = try {
                    lexer.parseValue()
                } catch (error: PdfSyntaxException) {
                    throw PdfAigcInjectionException(
                        PdfAigcErrorCode.INFO_OBJECT_TRUNCATED,
                        "Info object ${reference.number} is truncated: ${error.message}",
                        cause = error,
                    )
                }
                val dictionary = value as? PdfValue.Dict
                    ?: throw PdfAigcInjectionException(
                        PdfAigcErrorCode.INFO_NOT_DICTIONARY,
                        "Info object ${reference.number} is not a dictionary",
                    )
                lexer.skipWhitespaceAndComments()
                if (lexer.matchKeyword("stream")) {
                    throw PdfAigcInjectionException(
                        PdfAigcErrorCode.INFO_NOT_DICTIONARY,
                        "Info object ${reference.number} is a stream",
                    )
                }
                return ExistingInfo(
                    number = reference.number,
                    generation = reference.generation,
                    objectOffset = entry.offset.toInt(),
                    dictionary = dictionary,
                )
            }
        }
    }

    private fun mergeInformationObject(
        source: ByteArray,
        info: ExistingInfo,
        jsonToken: ByteArray,
        json: String,
    ): MergeResult {
        val dictionary = info.dictionary
        val existing = dictionary.entries["AIGC"]
        if (existing == null) {
            val insertion = dictionary.end - 2
            val builder = java.io.ByteArrayOutputStream()
            builder.write(source, info.objectOffset, insertion - info.objectOffset)
            builder.write(PdfLexer.ascii("\n/AIGC "))
            builder.write(jsonToken)
            builder.write(PdfLexer.ascii("\n"))
            builder.write(source, insertion, dictionary.end - insertion)
            builder.write(PdfLexer.ascii("\nendobj\n"))
            return MergeResult.Appended(builder.toByteArray())
        }
        val string = existing.value as? PdfValue.Str
            ?: throw PdfAigcInjectionException(
                PdfAigcErrorCode.AIGC_VALUE_NOT_STRING,
                "existing /AIGC in Info object ${info.number} is not a text string",
            )
        val existingText = string.token.decodedText()
        // 与解码后的 JSON 文本比较，而不是与 PDF 转义后的 token 比较：
        // 可选字段若含 '"'、'\'、'('、')'，literalString 会做 PDF 转义，直接比 token 会误判为不同值。
        if (existingText == json) {
            return MergeResult.Unchanged(source)
        }
        val builder = java.io.ByteArrayOutputStream()
        builder.write(source, info.objectOffset, existing.valueStart - info.objectOffset)
        builder.write(jsonToken)
        builder.write(source, existing.valueEnd, dictionary.end - existing.valueEnd)
        builder.write(PdfLexer.ascii("\nendobj\n"))
        return MergeResult.Appended(builder.toByteArray())
    }

    private fun buildNewInfoObject(objectNumber: Int, jsonToken: ByteArray): ByteArray {
        val builder = java.io.ByteArrayOutputStream()
        builder.write(PdfLexer.ascii("$objectNumber 0 obj\n<< /AIGC "))
        builder.write(jsonToken)
        builder.write(PdfLexer.ascii(" >>\nendobj\n"))
        return builder.toByteArray()
    }

    // ---------------------------------------------------------------- 写出新修订

    private fun writeTableRevision(
        source: ByteArray,
        base: ByteArray,
        index: PdfCrossReferenceIndex,
        infoNumber: Int,
        infoGeneration: Int,
        infoOffset: Long,
        idDigest: ByteArray,
    ): ByteArray {
        val xrefOffset = base.size.toLong()
        val maxObjectNumber = maxOf(index.highestObjectNumber, infoNumber)
        val newSize = maxOf(index.size, maxObjectNumber.toLong() + 1L)
        val trailerBytes = buildTrailerDictionary(
            source = source,
            prior = index.newestTrailer,
            index = index,
            newSize = newSize,
            infoRefRaw = PdfLexer.ascii("$infoNumber $infoGeneration R"),
            idDigest = idDigest,
            forStream = false,
        )

        val builder = java.io.ByteArrayOutputStream(base.size + 256)
        builder.write(base)
        builder.write(PdfLexer.ascii("xref\n"))
        builder.write(PdfLexer.ascii("$infoNumber 1\n"))
        builder.write(PdfLexer.ascii(String.format(java.util.Locale.ROOT, "%010d %05d n \n", infoOffset, infoGeneration)))
        builder.write(PdfLexer.ascii("trailer\n"))
        builder.write(trailerBytes)
        builder.write(PdfLexer.ascii("\nstartxref\n$xrefOffset\n%%EOF\n"))
        return builder.toByteArray()
    }

    private fun writeStreamRevision(
        source: ByteArray,
        base: ByteArray,
        index: PdfCrossReferenceIndex,
        infoNumber: Int,
        infoGeneration: Int,
        infoOffset: Long,
        idDigest: ByteArray,
    ): ByteArray {
        val baseAllocation = maxOf(index.size, index.highestObjectNumber.toLong() + 1L)
        val xrefStreamNumber = if (infoNumber >= baseAllocation) {
            infoNumber.toLong() + 1L
        } else {
            baseAllocation
        }
        if (xrefStreamNumber > MAX_OBJECT_NUMBER) {
            throw PdfAigcInjectionException(
                PdfAigcErrorCode.OBJECT_NUMBER_LIMIT_EXCEEDED,
                "xref stream object number $xrefStreamNumber exceeds $MAX_OBJECT_NUMBER",
            )
        }
        val xrefStreamNumberInt = xrefStreamNumber.toInt()

        val xrefObjectOffset = base.size.toLong()
        val maxObjectNumber = maxOf(index.highestObjectNumber, infoNumber, xrefStreamNumberInt)
        val newSize = maxOf(index.size, maxObjectNumber.toLong() + 1L)

        val entries = sortedMapOf<Int, PdfXrefEntry>()
        entries[infoNumber] = PdfXrefEntry.InUse(infoOffset, infoGeneration)
        entries[xrefStreamNumberInt] = PdfXrefEntry.InUse(xrefObjectOffset, 0)

        val offsetWidth = bytesNeeded(maxOf(infoOffset, xrefObjectOffset))
        val generationWidth = bytesNeeded(entries.values.maxOf { it.generationOf() }.toLong())
        val widths = intArrayOf(1, offsetWidth, generationWidth)
        val data = buildStreamData(entries, widths)
        val indexRanges = groupIndexRanges(entries.keys.toList())

        val dictionary = buildTrailerDictionary(
            source = source,
            prior = index.newestTrailer,
            index = index,
            newSize = newSize,
            infoRefRaw = PdfLexer.ascii("$infoNumber $infoGeneration R"),
            idDigest = idDigest,
            forStream = true,
            streamKeys = StreamKeys(
                widths = widths,
                indexRanges = indexRanges,
                length = data.size,
            ),
        )

        val builder = java.io.ByteArrayOutputStream(base.size + data.size + 512)
        builder.write(base)
        builder.write(PdfLexer.ascii("$xrefStreamNumberInt 0 obj\n"))
        builder.write(dictionary)
        builder.write(PdfLexer.ascii("\nstream\n"))
        builder.write(data)
        builder.write(PdfLexer.ascii("\nendstream\nendobj\n"))
        builder.write(PdfLexer.ascii("startxref\n$xrefObjectOffset\n%%EOF\n"))
        return builder.toByteArray()
    }

    private data class StreamKeys(
        val widths: IntArray,
        val indexRanges: List<Pair<Int, Int>>,
        val length: Int,
    )

    private fun buildStreamData(
        entries: Map<Int, PdfXrefEntry>,
        widths: IntArray,
    ): ByteArray {
        val rowBytes = widths.sum()
        val bytes = ByteArray(entries.size * rowBytes)
        var cursor = 0
        for ((_, entry) in entries) {
            val type: Int
            val field2: Long
            val field3: Long
            when (entry) {
                is PdfXrefEntry.InUse -> {
                    type = 1
                    field2 = entry.offset
                    field3 = entry.generation.toLong()
                }
                is PdfXrefEntry.Free -> {
                    type = 0
                    field2 = entry.nextFree
                    field3 = entry.generation.toLong()
                }
                is PdfXrefEntry.Compressed -> {
                    type = 2
                    field2 = entry.objectStream.toLong()
                    field3 = entry.index.toLong()
                }
            }
            writeUnsigned(bytes, cursor, widths[0], type.toLong())
            cursor += widths[0]
            writeUnsigned(bytes, cursor, widths[1], field2)
            cursor += widths[1]
            writeUnsigned(bytes, cursor, widths[2], field3)
            cursor += widths[2]
        }
        return bytes
    }

    private fun writeUnsigned(target: ByteArray, start: Int, width: Int, value: Long) {
        var remaining = value
        for (index in width - 1 downTo 0) {
            target[start + index] = (remaining and 0xFF).toByte()
            remaining = remaining ushr 8
        }
        if (remaining != 0L) {
            throw PdfAigcInjectionException(
                PdfAigcErrorCode.OBJECT_NUMBER_LIMIT_EXCEEDED,
                "value $value does not fit in $width bytes",
            )
        }
    }

    private fun groupIndexRanges(numbers: List<Int>): List<Pair<Int, Int>> {
        if (numbers.isEmpty()) return emptyList()
        val ranges = ArrayList<Pair<Int, Int>>()
        var start = numbers[0]
        var count = 1
        for (index in 1 until numbers.size) {
            val number = numbers[index]
            if (number == start + count) {
                count++
            } else {
                ranges.add(start to count)
                start = number
                count = 1
            }
        }
        ranges.add(start to count)
        return ranges
    }

    /**
     * 复制上一 trailer 的全部条目（R1 §2.4/§7.5.6），排除本注入自己重写的键。
     * 值按原始字节复制，避免重新序列化未知自定义键。
     */
    private fun buildTrailerDictionary(
        source: ByteArray,
        prior: PdfValue.Dict,
        index: PdfCrossReferenceIndex,
        newSize: Long,
        infoRefRaw: ByteArray,
        idDigest: ByteArray,
        forStream: Boolean,
        streamKeys: StreamKeys? = null,
    ): ByteArray {
        val builder = java.io.ByteArrayOutputStream()
        builder.write(PdfLexer.ascii("<<"))

        if (forStream && streamKeys != null) {
            builder.write(PdfLexer.ascii("\n/Type /XRef"))
            builder.write(PdfLexer.ascii("\n/Size $newSize"))
            builder.write(PdfLexer.ascii("\n/Index ["))
            streamKeys.indexRanges.forEachIndexed { position, (start, count) ->
                if (position > 0) builder.write(PdfLexer.ascii(" "))
                builder.write(PdfLexer.ascii("$start $count"))
            }
            builder.write(PdfLexer.ascii("]"))
            builder.write(
                PdfLexer.ascii(
                    "\n/W [${streamKeys.widths[0]} ${streamKeys.widths[1]} ${streamKeys.widths[2]}]",
                ),
            )
            builder.write(PdfLexer.ascii("\n/Length ${streamKeys.length}"))
            builder.write(PdfLexer.ascii("\n/Prev ${index.startxref}"))
        } else {
            builder.write(PdfLexer.ascii("\n/Size $newSize"))
            builder.write(PdfLexer.ascii("\n/Prev ${index.startxref}"))
        }

        // Root：必须是间接引用；保留原始 token（含生成号）。
        val rootRaw = prior.raw("Root", source)
            ?: throw PdfAigcInjectionException(PdfAigcErrorCode.TRAILER_ROOT_MISSING, "trailer has no /Root")
        builder.write(PdfLexer.ascii("\n/Root "))
        builder.write(rootRaw)

        builder.write(PdfLexer.ascii("\n/Info "))
        builder.write(infoRefRaw)

        val idRaw = prior.raw("ID", source)
        if (idRaw != null) {
            builder.write(PdfLexer.ascii("\n/ID "))
            builder.write(rewriteId(prior, source, idDigest))
        }

        val skip = if (forStream) STREAM_KEYS else TABLE_KEYS
        for ((key, entry) in prior.entries) {
            if (key in skip) continue
            builder.write(PdfLexer.ascii("\n/"))
            builder.write(PdfLexer.ascii(key))
            builder.write(' '.code)
            builder.write(source, entry.valueStart, entry.valueEnd - entry.valueStart)
        }
        builder.write(PdfLexer.ascii("\n>>"))
        return builder.toByteArray()
    }

    private fun rewriteId(
        prior: PdfValue.Dict,
        source: ByteArray,
        idDigest: ByteArray,
    ): ByteArray {
        val array = prior["ID"] as? PdfValue.Arr
            ?: throw PdfAigcInjectionException(PdfAigcErrorCode.ID_MALFORMED, "/ID is not an array")
        if (array.items.size != 2) {
            throw PdfAigcInjectionException(PdfAigcErrorCode.ID_MALFORMED, "/ID must have two elements")
        }
        val first = array.items[0] as? PdfValue.Str
            ?: throw PdfAigcInjectionException(PdfAigcErrorCode.ID_MALFORMED, "/ID[0] is not a string")
        val builder = java.io.ByteArrayOutputStream()
        builder.write('['.code)
        builder.write(source, first.token.rawStart, first.token.rawEnd - first.token.rawStart)
        builder.write(' '.code)
        builder.write('<'.code)
        builder.write(PdfLexer.ascii(idDigest.toHex()))
        builder.write('>'.code)
        builder.write(']'.code)
        return builder.toByteArray()
    }

    // ---------------------------------------------------------------- 工具

    private fun allocateObjectNumber(index: PdfCrossReferenceIndex, preferred: Long): Int {
        val candidate = maxOf(preferred, index.highestObjectNumber.toLong() + 1L)
        if (candidate > MAX_OBJECT_NUMBER) {
            throw PdfAigcInjectionException(
                PdfAigcErrorCode.OBJECT_NUMBER_LIMIT_EXCEEDED,
                "object number $candidate exceeds $MAX_OBJECT_NUMBER",
            )
        }
        return candidate.toInt()
    }

    private fun bytesNeeded(value: Long): Int {
        if (value <= 0L) return 1
        var remaining = value
        var bytes = 0
        while (remaining > 0L) {
            bytes++
            remaining = remaining ushr 8
        }
        return bytes
    }

    private fun PdfXrefEntry.generationOf(): Int = when (this) {
        is PdfXrefEntry.InUse -> generation
        is PdfXrefEntry.Free -> generation
        is PdfXrefEntry.Compressed -> 0
    }

    private fun literalString(value: String): ByteArray {
        val builder = java.io.ByteArrayOutputStream(value.length + 2)
        builder.write('('.code)
        for (char in value) {
            when (char) {
                '\\' -> {
                    builder.write('\\'.code)
                    builder.write('\\'.code)
                }
                '(' -> {
                    builder.write('\\'.code)
                    builder.write('('.code)
                }
                ')' -> {
                    builder.write('\\'.code)
                    builder.write(')'.code)
                }
                else -> {
                    val code = char.code
                    if (code in 0x20..0x7E) {
                        builder.write(code)
                    } else {
                        // schema 已拒绝控制字符；保留 \ddd 作为纵深防御。
                        builder.write('\\'.code)
                        builder.write('0'.code + ((code shr 6) and 0x07))
                        builder.write('0'.code + ((code shr 3) and 0x07))
                        builder.write('0'.code + (code and 0x07))
                    }
                }
            }
        }
        builder.write(')'.code)
        return builder.toByteArray()
    }

    private fun sha256(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)

    private fun ByteArray.toHex(): String {
        val builder = StringBuilder(size * 2)
        for (byte in this) {
            val value = byte.toInt() and 0xFF
            builder.append(HEX[value ushr 4])
            builder.append(HEX[value and 0x0F])
        }
        return builder.toString()
    }

    private companion object {
        const val MAX_OBJECT_NUMBER = 8_388_607L
        val TABLE_KEYS = setOf("Size", "Prev", "XRefStm", "Encrypt", "Root", "Info", "ID")
        val STREAM_KEYS = TABLE_KEYS + setOf(
            "Type",
            "Index",
            "W",
            "Length",
            "Filter",
            "DecodeParms",
            "DL",
        )
        val HEX = "0123456789abcdef".toCharArray()
    }
}
