package com.tingyun.smartmistakebook.core.data.pdf

/**
 * `/AIGC` 读回器（R1 §3.4 读回校验、§7.4 测试矩阵）。
 *
 * 用途：
 * - JVM 测试断言"注入后最新 Info 只有一个 `/AIGC`、值可读回"；
 * - 诊断/对账时读取最新修订的 Info 字符串。
 *
 * **不是独立读者**：与注入器共用同一套解析器，只能证明"自洽"，不能替代设备上的
 * `PdfRenderer` 或外部读者（R1 §7.4 第 7 条）。独立验证见测试脚本里的 pypdf/PyMuPDF。
 */
object PdfAigcReader {

    data class ReadBack(
        val infoObjectNumber: Int,
        val infoGeneration: Int,
        val json: String,
        val fields: Map<String, String>,
    ) {
        val label: String get() = fields.getValue(PdfAigcMetadata.KEY_LABEL)
        val contentProducer: String get() = fields.getValue(PdfAigcMetadata.KEY_CONTENT_PRODUCER)
        val produceId: String get() = fields.getValue(PdfAigcMetadata.KEY_PRODUCE_ID)
    }

    /** 读取最新修订 Info 的 `/AIGC`；没有 Info 或没有 `/AIGC` 返回 null。 */
    fun readLatestAigc(
        source: ByteArray,
        limits: PdfAigcLimits = PdfAigcLimits(),
    ): ReadBack? {
        val index = PdfCrossReferenceIndex.parse(source, limits)
        val trailer = index.newestTrailer
        val info = resolveInfo(source, index, trailer) ?: return null
        val value = info.dictionary["AIGC"] ?: return null
        val string = value as? PdfValue.Str ?: return null
        val json = string.token.decodedText()
        val fields = PdfAigcJson.parseObject(json)
        val expected = PdfAigcMetadata.KEY_ORDER.toSet()
        if (fields.keys != expected) {
            throw PdfAigcInjectionException(
                PdfAigcErrorCode.AIGC_SCHEMA_INVALID,
                "/AIGC JSON keys mismatch on read-back",
            )
        }
        val label = fields.getValue(PdfAigcMetadata.KEY_LABEL)
        if (label !in READABLE_LABELS) {
            throw PdfAigcInjectionException(
                PdfAigcErrorCode.AIGC_SCHEMA_INVALID,
                "/AIGC Label '$label' is not 1/2/3 on read-back",
            )
        }
        return ReadBack(
            infoObjectNumber = info.number,
            infoGeneration = info.generation,
            json = json,
            fields = fields,
        )
    }

    /** 读取最新修订 Info 字典里 `/AIGC` 键的数量（0/1）；重复键会被字典解析器当场拒绝。 */
    fun readLatestAigcKeyCount(
        source: ByteArray,
        limits: PdfAigcLimits = PdfAigcLimits(),
    ): Int {
        val index = PdfCrossReferenceIndex.parse(source, limits)
        val info = resolveInfo(source, index, index.newestTrailer) ?: return 0
        return info.dictionary.entries.keys.count { it == "AIGC" }
    }

    /** 读取最新 trailer/xref 流字典的 `/ID` 两段原始字节；没有 `/ID` 返回 null。 */
    fun readLatestIdSegments(
        source: ByteArray,
        limits: PdfAigcLimits = PdfAigcLimits(),
    ): List<ByteArray>? {
        val index = PdfCrossReferenceIndex.parse(source, limits)
        val array = index.newestTrailer["ID"] as? PdfValue.Arr ?: return null
        if (array.items.size != 2) return null
        return array.items.map { item ->
            (item as? PdfValue.Str)?.token?.decoded ?: return null
        }
    }

    /** 读取最新 Info 字典里的所有文本字符串键（Title/Author/...）；非字符串键忽略。 */
    fun readInfoStrings(
        source: ByteArray,
        limits: PdfAigcLimits = PdfAigcLimits(),
    ): Map<String, String> {
        val index = PdfCrossReferenceIndex.parse(source, limits)
        val info = resolveInfo(source, index, index.newestTrailer) ?: return emptyMap()
        val result = LinkedHashMap<String, String>()
        for ((key, entry) in info.dictionary.entries) {
            val value = entry.value as? PdfValue.Str ?: continue
            result[key] = value.token.decodedText()
        }
        return result
    }

    /**
     * 通过 `/Root -> /Pages -> /Count` 读取页数。只做结构检查，**不渲染**；
     * 真实打开/页数归设备轨 `PdfRenderer`（本批未跑）。
     */
    fun readStructuralPageCount(
        source: ByteArray,
        limits: PdfAigcLimits = PdfAigcLimits(),
    ): Int? {
        val index = PdfCrossReferenceIndex.parse(source, limits)
        val rootRef = PdfDictionaryOps.ref(index.newestTrailer, "Root") ?: return null
        val root = resolveObjectDictionary(source, index, rootRef) ?: return null
        val pagesRef = PdfDictionaryOps.ref(root, "Pages") ?: return null
        val pages = resolveObjectDictionary(source, index, pagesRef) ?: return null
        val count = (pages["Count"] as? PdfValue.Num)?.value ?: return null
        return count.toInt()
    }

    private data class Info(
        val number: Int,
        val generation: Int,
        val dictionary: PdfValue.Dict,
    )

    private fun resolveInfo(
        source: ByteArray,
        index: PdfCrossReferenceIndex,
        trailer: PdfValue.Dict,
    ): Info? {
        val reference = trailer["Info"] as? PdfValue.Ref ?: return null
        val entry = index.resolve(reference.number) as? PdfXrefEntry.InUse ?: return null
        if (entry.generation != reference.generation) return null
        val dictionary = resolveObjectDictionary(source, index, reference) ?: return null
        return Info(reference.number, reference.generation, dictionary)
    }

    private fun resolveObjectDictionary(
        source: ByteArray,
        index: PdfCrossReferenceIndex,
        reference: PdfValue.Ref,
    ): PdfValue.Dict? {
        val entry = index.resolve(reference.number) as? PdfXrefEntry.InUse ?: return null
        if (entry.generation != reference.generation) return null
        if (entry.offset <= 0L || entry.offset >= source.size.toLong()) return null
        return try {
            val lexer = PdfLexer(source, entry.offset.toInt(), source.size)
            val header = lexer.readObjectHeader() ?: return null
            if (header.number != reference.number || header.generation != reference.generation) return null
            lexer.skipWhitespaceAndComments()
            lexer.parseValue() as? PdfValue.Dict
        } catch (_: PdfSyntaxException) {
            null
        }
    }

    private val READABLE_LABELS = setOf("1", "2", "3")
}
