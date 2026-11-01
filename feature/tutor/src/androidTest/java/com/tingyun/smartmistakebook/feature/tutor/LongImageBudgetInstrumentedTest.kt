package com.tingyun.smartmistakebook.feature.tutor

import android.os.Debug
import android.os.SystemClock
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.tingyun.smartmistakebook.core.ui.LocalImageViewer
import com.tingyun.smartmistakebook.core.ui.SmartMistakeBookTheme
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Real 96-megapixel PNG inputs; fixture encoding never allocates a full-sized bitmap. */
class LongImageBudgetInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun repeatedLongPagesStayBoundedAndExposeTheirActualPreviewResolution() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val files = (0..2).map { page ->
            File(context.cacheDir, "ui-long-page-$page.png").also { writeLongPage(it, page) }
        }
        val openPage = mutableStateOf<Int?>(null)
        val samples = JSONArray()
        try {
            compose.setContent {
                SmartMistakeBookTheme {
                    Text("原来的多页列表")
                    openPage.value?.let { page ->
                        LocalImageViewer(files[page].toURI().toString(), "原图 ${page + 1} / 3",
                            onDismiss = { openPage.value = null })
                    }
                }
            }
            val baselinePss = settledPss()
            var peakPss = baselinePss
            repeat(12) { iteration ->
                val page = iteration % files.size
                val started = SystemClock.elapsedRealtime()
                compose.runOnIdle { openPage.value = page }
                compose.waitUntil(20_000) {
                    compose.onAllNodesWithTag("image_viewer_resolution").fetchSemanticsNodes().isNotEmpty()
                }
                val label = compose.onNodeWithTag("image_viewer_resolution")
                    .fetchSemanticsNode().config[SemanticsProperties.Text].joinToString { it.text }
                val dimensions = Regex("(\\d+) × (\\d+)").find(label)!!
                val width = dimensions.groupValues[1].toInt()
                val height = dimensions.groupValues[2].toInt()
                assertTrue("A sampled preview must have positive, bounded dimensions: $label",
                    width in 1..1600 && height in 1..1600)
                assertTrue("The real long input must retain its portrait aspect ratio", height > width)
                compose.onNodeWithTag("image_viewer_resolution").assertTextContains("放大不增加细节", substring = true)
                val pss = Debug.getPss()
                peakPss = maxOf(peakPss, pss)
                samples.put(JSONObject().put("page", page).put("width", width).put("height", height)
                    .put("readyMillis", SystemClock.elapsedRealtime() - started).put("pssKb", pss))
                if (iteration == 0) UiAuditEvidence.capture("long-image-sampled-preview")
                compose.onNodeWithContentDescription("关闭图片查看").performClick()
                compose.waitForIdle()
                assertEquals(null, openPage.value)
            }
            val retainedPss = settledPss()
            val directory = File(context.getExternalFilesDir(null), "ui-audit").apply { mkdirs() }
            File(directory, "long-image-memory.json").writeText(JSONObject()
                .put("inputWidth", 8000).put("inputHeight", 12000).put("iterations", 12)
                .put("baselinePssKb", baselinePss).put("peakObservedPssKb", peakPss)
                .put("retainedPssKb", retainedPss).put("samples", samples).toString(2))
        } finally {
            files.forEach { it.delete() }
        }
    }

    private fun settledPss(): Long {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        Runtime.getRuntime().gc()
        System.runFinalization()
        Thread.sleep(150)
        return Debug.getPss()
    }

    private fun writeLongPage(file: File, page: Int) {
        val row = ByteArray(1 + 8000 * 3)
        for (pixel in 0 until 8000) {
            row[1 + pixel * 3] = (190 + page * 20).toByte()
            row[2 + pixel * 3] = 210.toByte()
            row[3 + pixel * 3] = 180.toByte()
        }
        val compressed = ByteArrayOutputStream()
        val deflater = Deflater(Deflater.BEST_SPEED)
        try {
            DeflaterOutputStream(compressed, deflater).use { output -> repeat(12000) { output.write(row) } }
        } finally {
            deflater.end()
        }
        DataOutputStream(file.outputStream()).use { output ->
            output.write(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10))
            val header = ByteArrayOutputStream()
            DataOutputStream(header).use { it.writeInt(8000); it.writeInt(12000); it.write(byteArrayOf(8, 2, 0, 0, 0)) }
            writeChunk(output, "IHDR", header.toByteArray())
            writeChunk(output, "IDAT", compressed.toByteArray())
            writeChunk(output, "IEND", byteArrayOf())
        }
    }

    private fun writeChunk(output: DataOutputStream, type: String, bytes: ByteArray) {
        val name = type.toByteArray(Charsets.US_ASCII)
        val crc = CRC32().apply { update(name); update(bytes) }
        output.writeInt(bytes.size)
        output.write(name)
        output.write(bytes)
        output.writeInt(crc.value.toInt())
    }
}
