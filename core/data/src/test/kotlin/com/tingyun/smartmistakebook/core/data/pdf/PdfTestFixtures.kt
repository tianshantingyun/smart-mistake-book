package com.tingyun.smartmistakebook.core.data.pdf

import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.zip.Deflater

/**
 * WS1 JVM 夹具：手写最小但结构完整的 PDF（2 页 + Info），不依赖 Android/第三方 PDF 库。
 *
 * - `tablePdf`：经典 xref 表（Skia/Android `PdfDocument` 的形态）；
 * - `streamPdf`：PDF 1.5+ xref 流，可选 FlateDecode + PNG Predictor 12。
 *
 * 所有偏移按实际写出位置计算；测试不靠"猜偏移"。
 */
internal object PdfTestFixtures {

    const val ORIGINAL_TITLE: String = "Original Title"
    const val ORIGINAL_AUTHOR: String = "Original Author"
    const val HEX_ID: String = "00112233445566778899aabbccddeeff"

    const val DEFAULT_INFO_BODY: String =
        "<< /Title (Original Title) /Author (Original Author) /Producer (fixture) >>"

    data class Build(
        val bytes: ByteArray,
        val objectOffsets: IntArray,
    )

    fun tablePdf(
        includeId: Boolean = false,
        includeInfo: Boolean = true,
        infoBody: String = DEFAULT_INFO_BODY,
        infoGeneration: Int = 0,
        trailerExtra: String = "",
        startxrefOverride: Long? = null,
        linearizedFirstObject: Boolean = false,
        corruptXrefEntryObject: Int? = null,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        out.writeAscii("%PDF-1.4\n")
        val offsets = IntArray(8)
        fun objectBody(number: Int, generation: Int, body: String) {
            offsets[number] = out.size()
            out.writeAscii("$number $generation obj\n")
            out.writeAscii(body)
            out.writeAscii("\nendobj\n")
        }

        val catalog = if (linearizedFirstObject) {
            "<< /Linearized 1 /Type /Catalog /Pages 2 0 R >>"
        } else {
            "<< /Type /Catalog /Pages 2 0 R >>"
        }
        objectBody(1, 0, catalog)
        objectBody(2, 0, "<< /Type /Pages /Kids [3 0 R 5 0 R] /Count 2 >>")
        objectBody(3, 0, "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 200 200] /Contents 4 0 R >>")
        objectBody(4, 0, "<< /Length 0 >>\nstream\n\nendstream")
        objectBody(5, 0, "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 200 200] /Contents 6 0 R >>")
        objectBody(6, 0, "<< /Length 0 >>\nstream\n\nendstream")
        objectBody(7, infoGeneration, infoBody)

        val xrefOffset = out.size()
        out.writeAscii("xref\n0 8\n")
        out.writeAscii(entry(0, 65_535, 'f', corrupt = false))
        for (number in 1..7) {
            out.writeAscii(
                entry(
                    offsets[number],
                    if (number == 7) infoGeneration else 0,
                    'n',
                    corrupt = number == corruptXrefEntryObject,
                ),
            )
        }
        out.writeAscii("trailer\n<< /Size 8 /Root 1 0 R")
        if (includeInfo) out.writeAscii(" /Info 7 $infoGeneration R")
        if (includeId) out.writeAscii(" /ID [<$HEX_ID> <$HEX_ID>]")
        if (trailerExtra.isNotEmpty()) out.writeAscii(trailerExtra)
        out.writeAscii(" >>\n")
        out.writeAscii("startxref\n${startxrefOverride ?: xrefOffset}\n%%EOF\n")
        return out.toByteArray()
    }

    fun streamPdf(
        includeId: Boolean = false,
        infoInObjectStream: Boolean = false,
        flate: Boolean = false,
        predictor: Int = 1,
        trailerExtra: String = "",
    ): ByteArray {
        val out = ByteArrayOutputStream()
        out.writeAscii("%PDF-1.5\n")
        val offsets = IntArray(9)
        fun objectBody(number: Int, body: String) {
            offsets[number] = out.size()
            out.writeAscii("$number 0 obj\n")
            out.writeAscii(body)
            out.writeAscii("\nendobj\n")
        }
        objectBody(1, "<< /Type /Catalog /Pages 2 0 R >>")
        objectBody(2, "<< /Type /Pages /Kids [3 0 R 5 0 R] /Count 2 >>")
        objectBody(3, "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 200 200] /Contents 4 0 R >>")
        objectBody(4, "<< /Length 0 >>\nstream\n\nendstream")
        objectBody(5, "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 200 200] /Contents 6 0 R >>")
        objectBody(6, "<< /Length 0 >>\nstream\n\nendstream")
        objectBody(7, DEFAULT_INFO_BODY)

        val xrefStreamOffset = out.size()

        // 9 个条目（对象 0..8）；W = [1 4 2]，行宽 7。
        val rows = ByteArray(9 * 7)
        fun row(index: Int, type: Int, field2: Long, field3: Long) {
            val base = index * 7
            rows[base] = type.toByte()
            rows[base + 1] = ((field2 ushr 24) and 0xFF).toByte()
            rows[base + 2] = ((field2 ushr 16) and 0xFF).toByte()
            rows[base + 3] = ((field2 ushr 8) and 0xFF).toByte()
            rows[base + 4] = (field2 and 0xFF).toByte()
            rows[base + 5] = ((field3 ushr 8) and 0xFF).toByte()
            rows[base + 6] = (field3 and 0xFF).toByte()
        }
        row(0, 0, 0, 65_535)
        for (number in 1..7) {
            if (number == 7 && infoInObjectStream) {
                row(7, 2, 8, 0)
            } else {
                row(number, 1, offsets[number].toLong(), 0)
            }
        }
        row(8, 1, xrefStreamOffset.toLong(), 0)

        var streamData = if (predictor == 12) pngUpEncode(rows, columns = 7) else rows
        if (flate) streamData = deflate(streamData)

        out.writeAscii(
            buildString {
                append("8 0 obj\n<<\n/Type /XRef\n/Size 9\n/Index [0 9]\n/W [1 4 2]\n")
                append("/Length ${streamData.size}\n/Root 1 0 R")
                append("\n/Info 7 0 R")
                if (includeId) append("\n/ID [<$HEX_ID> <$HEX_ID>]")
                if (flate) append("\n/Filter /FlateDecode")
                if (predictor == 12) append("\n/DecodeParms << /Predictor 12 /Columns 7 >>")
                if (trailerExtra.isNotEmpty()) append("\n${trailerExtra.trim()}")
                append("\n>>\nstream\n")
            },
        )
        out.write(streamData)
        out.writeAscii("\nendstream\nendobj\n")
        out.writeAscii("startxref\n$xrefStreamOffset\n%%EOF\n")
        return out.toByteArray()
    }

    fun replaceAscii(bytes: ByteArray, old: String, new: String): ByteArray {
        val text = bytes.toString(Charsets.ISO_8859_1)
        val replaced = text.replaceLast(old, new)
        check(replaced != text) { "fixture does not contain '$old'" }
        return replaced.toByteArray(Charsets.ISO_8859_1)
    }

    private fun String.replaceLast(old: String, new: String): String {
        val index = lastIndexOf(old)
        if (index < 0) return this
        return substring(0, index) + new + substring(index + old.length)
    }

    private fun entry(offset: Int, generation: Int, type: Char, corrupt: Boolean): String {
        val offsetText = String.format(Locale.ROOT, "%010d", offset)
        val rendered = if (corrupt) "X" + offsetText.substring(1) else offsetText
        return String.format(Locale.ROOT, "%s %05d %c \n", rendered, generation, type)
    }

    private fun pngUpEncode(raw: ByteArray, columns: Int): ByteArray {
        require(columns > 0 && raw.size % columns == 0)
        // PNG predictor 12：每行 [filter=2][columns 字节]。真实读者（pypdf/PyMuPDF）都按这个
        // 布局解码；不带 lead byte 会让 PyMuPDF 把首个数据字节当成 filter。
        val rows = raw.size / columns
        val out = ByteArray(rows * (columns + 1))
        var previous = ByteArray(columns)
        for (row in 0 until rows) {
            val rowOffset = row * columns
            val outputOffset = row * (columns + 1)
            out[outputOffset] = 2 // Up
            for (index in 0 until columns) {
                out[outputOffset + 1 + index] =
                    ((raw[rowOffset + index].toInt() and 0xFF) - (previous[index].toInt() and 0xFF)).toByte()
            }
            previous = raw.copyOfRange(rowOffset, rowOffset + columns)
        }
        return out
    }

    private fun deflate(data: ByteArray): ByteArray {
        val deflater = Deflater()
        try {
            deflater.setInput(data)
            deflater.finish()
            val out = ByteArrayOutputStream(data.size)
            val buffer = ByteArray(4096)
            while (!deflater.finished()) {
                val written = deflater.deflate(buffer)
                out.write(buffer, 0, written)
            }
            return out.toByteArray()
        } finally {
            deflater.end()
        }
    }

    private fun ByteArrayOutputStream.writeAscii(text: String) {
        write(text.toByteArray(Charsets.US_ASCII))
    }
}
