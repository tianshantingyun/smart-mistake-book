package com.tingyun.smartmistakebook.core.data.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.database.StudyDbValue
import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A2（4B 批 2）：生成图的**隐式 AI 标识写进文件字节**并可读回，且插入标识后像素仍可解码。
 *
 * 消灭的失败：生成图此前只是普通 PNG——文件被原样带走后无法分辨是不是 AI 生成，也没有
 * 提供者与编号可追溯；标识只存在内存/数据库里时，文件一离开 App 就丢。
 *
 * 读回用**字节拷贝**（模拟原样复制/备份后的那份文件），证明标识随文件字节走而不是随路径走。
 * 边界：App 的 PDF 导出会重绘，不随 PDF 走（见 `GeneratedFigureMetadata` KDoc，已登记）。
 */
@RunWith(AndroidJUnit4::class)
class GeneratedFigureMetadataInstrumentedTest {

    @Test
    fun theAiProvenanceIsWrittenIntoTheFileBytesAndSurvivesACopy() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val vault = AndroidCanonicalAssetVault(context)
        val pngBytes = ByteArrayOutputStream().use { output ->
            val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(0xFF3366AA.toInt())
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            bitmap.recycle()
            output.toByteArray()
        }
        val figureId = "figure-metadata-instrumented-1"

        val record = vault.persistCleanImageBytes(
            bytes = pngBytes,
            mimeType = "image/png",
            sourceType = StudyDbValue.SourceAssetType.GENERATED_FIGURE,
            createdAtEpochMillis = 1_000,
            provenance = GeneratedFigureProvenance(
                model = "gpt-image-2",
                figureId = figureId,
                purpose = "GENERATE_PROCESS",
            ),
            figureIdentity = figureId,
        )
        try {
            assertEquals(figureId, record.sourceAssetId)
            assertEquals(
                StudyDbValue.SourceAssetType.GENERATED_FIGURE,
                record.sourceType,
            )
            val file = File(context.filesDir, record.relativePath)
            assertTrue("生成图文件必须落盘", file.isFile)

            val metadata = readGeneratedFigureMetadata(file)
            assertTrue("属性（AI 生成）必须写进字节", metadata.aiGenerated)
            assertEquals("提供者（模型）必须写进字节", "gpt-image-2", metadata.model)
            assertEquals("编号必须写进字节", figureId, metadata.figureId)
            assertEquals("用途必须写进字节", "GENERATE_PROCESS", metadata.purpose)

            // 标识插入不得破坏像素解码（eXIf 块插入后仍是合法 PNG）。
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            assertTrue("插入元数据后必须仍可解码：${bounds.outWidth}x${bounds.outHeight}",
                bounds.outWidth > 0 && bounds.outHeight > 0)

            // 字节拷贝后的那一份：标识仍在（与路径/数据库无关）。
            val copied = File(context.cacheDir, "copied-figure.png")
            copied.writeBytes(file.readBytes())
            try {
                val copiedMetadata = readGeneratedFigureMetadata(copied)
                assertTrue(copiedMetadata.aiGenerated)
                assertEquals(figureId, copiedMetadata.figureId)
                assertEquals("gpt-image-2", copiedMetadata.model)
            } finally {
                copied.delete()
            }
        } finally {
            runCatching { vault.delete(record) }
        }
    }
}
