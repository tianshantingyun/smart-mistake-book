package com.tingyun.smartmistakebook.core.data.knowledge.dense

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.security.MessageDigest

/**
 * **A2 诊断件（批 11）**：把 129 条金标查询在**设备上**编码并落盘，供离线逐条对拍。
 *
 * 它回答的问题（此前只能靠推断）：设备的查询向量到底是"原文"还是"前缀+原文"的变换产物、
 * 以及离线 ONNX 向量与端侧 TFLite 向量在**这 129 条**上的逐条 cosine（既有
 * `DenseEncoderParityInstrumentedTest` 只覆盖它的 290 条 fixture，不含金标全量）。
 *
 * 编码入口 = 生产装配同一条路（[DenseRecallAssembly.MODEL_ASSET_PATH] +
 * [LiteRtDenseQueryEncoder.openFromAssets]）。两臂各落一份裸 float32（小端，行序 = 金标序）：
 * - `golden-query-device-raw-<stamp>.f32`（原样 `case.query`——生产调用点传的文本；
 *   RoomKnowledgeBaseStore/RoomTutorKnowledgeContextLoader 等均不加前缀，grep 证据见批 11 记录）；
 * - `golden-query-device-prefixed-<stamp>.f32`（`为这个句子生成表示以用于检索相关文章：` + query，
 *   前缀常量照抄 `tools/dense_build/dense_asset.py` 的 profile.queryPrefix，用于离线侧交叉验证）。
 *
 * **只读题面、只写设备 Download 目录**：不改判据、不参与任何门。取回命令在日志里。
 */
@RunWith(AndroidJUnit4::class)
class GoldenQueryEncodingProbeInstrumentedTest {

    @Test
    fun encodeGoldenQueriesInBothTransformsAndWriteDeviceVectors() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val queries = loadGoldenQueries(context)
        assertTrue("金标条数异常：${queries.size}", queries.size in 120..140)

        val vocabLines = readResourceText(DenseRecallAssembly.VOCAB_ASSET_PATH).split('\n')
        val tokenizer = DenseTokenizer(DenseVocab.fromLines(vocabLines))
        val encoder = LiteRtDenseQueryEncoder.openFromAssets(
            context = context,
            assetPath = DenseRecallAssembly.MODEL_ASSET_PATH,
            tokenizer = tokenizer,
        )

        val raw = queries.map { encoder.encode(it) }
        val prefixed = queries.map { encoder.encode(QUERY_PREFIX + it) }
        val dim = raw.first().size
        assertEquals("两臂维度必须一致", dim, prefixed.first().size)
        assertTrue("维度异常：$dim", dim > 0)

        val rawUri = writeVectors(context, "golden-query-device-raw", raw)
        val prefixedUri = writeVectors(context, "golden-query-device-prefixed", prefixed)
        println(
            "golden-query-probe: cases=${queries.size} dim=$dim " +
                "raw=$rawUri prefixed=$prefixedUri",
        )
        println("golden-query-probe: raw sha256=${sha256Hex(toBytes(raw))}")
        println("golden-query-probe: prefixed sha256=${sha256Hex(toBytes(prefixed))}")
        println(
            "golden-query-probe: 取回 adb pull /sdcard/Download/<name> build/agent-batch11/d3/<name>",
        )
    }

    private fun loadGoldenQueries(context: Context): List<String> {
        val json = context.assets.open(ASSET_JSON).use { it.bufferedReader().readText() }
        val seal = context.assets.open(ASSET_SHA256).use { it.bufferedReader().readText().trim() }
        assertEquals("金标 assets 副本与 sha256 封存不符", seal, sha256Hex(json.toByteArray(Charsets.UTF_8)))
        return Json.parseToJsonElement(json).jsonArray.map { element ->
            element.jsonObject["query"]!!.jsonPrimitive.content
        }
    }

    private fun toBytes(rows: List<FloatArray>): ByteArray {
        val buffer = ByteBuffer.allocate(rows.size * rows.first().size * Float.SIZE_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
        rows.forEach { row -> row.forEach { buffer.putFloat(it) } }
        return buffer.array()
    }

    private fun writeVectors(context: Context, stem: String, rows: List<FloatArray>): Uri {
        val name = stem + "-" + System.currentTimeMillis() + ".f32"
        val uri = requireNotNull(
            context.contentResolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                },
            ),
        ) { "MediaStore 没建出向量条目（Download/ 不可写？）" }
        context.contentResolver.openOutputStream(uri)!!.use { out ->
            out.write(toBytes(rows))
        }
        context.contentResolver.update(
            uri,
            ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
            null,
            null,
        )
        println("golden-query-probe: 落盘 = /sdcard/Download/$name rows=${rows.size}")
        return uri
    }

    private fun readResourceText(path: String): String {
        val stream = javaClass.classLoader?.getResourceAsStream(path)
            ?: error("bundled dense resource is missing: $path")
        return stream.use { it.readBytes().decodeToString() }
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private companion object {
        const val ASSET_JSON = "golden/golden_queries_v2.json"
        const val ASSET_SHA256 = "golden/golden_queries_v2.json.sha256"

        /** 与 `tools/dense_build/dense_asset.py` 的 profile.queryPrefix 逐字一致（离线交叉验证用）。 */
        const val QUERY_PREFIX = "为这个句子生成表示以用于检索相关文章："
    }
}
