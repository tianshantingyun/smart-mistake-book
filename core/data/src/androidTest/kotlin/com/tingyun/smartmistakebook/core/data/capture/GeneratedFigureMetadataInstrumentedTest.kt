package com.tingyun.smartmistakebook.core.data.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.database.StudyDbValue
import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
 *
 * 批次 21 T5 扩展：TC260 规范层（AIGC XMP 包）的**文件级**用例——本批 JVM 探针实测
 * `ExifInterface` 离开 Android 运行时不可用（`saveAttributes` 依赖 `android.system.Os`），
 * 所以容器级写入→读回只能在这里盖：
 * 1. PNG：TAG_XMP 走 iTXt 块，与既有 EXIF 四字段共存，字节拷贝后仍在；
 * 2. JPEG：TAG_XMP 走独立 APP1 段；
 * 3. 非 AI 图（无 provenance 落盘）：不得出现 TC260 包，读回 fail-closed 为非 AI。
 * （TC260 包的序列化层用例在 JVM：`GeneratedFigureAigcXmpTest`。）
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

    @Test
    fun theTc260AigcLabelIsWrittenNextToTheExifFieldsAndSurvivesACopy() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val vault = AndroidCanonicalAssetVault(context)
        val figureId = "figure-aigc-xmp-1"

        val record = vault.persistCleanImageBytes(
            bytes = compressedPngBytes(),
            mimeType = "image/png",
            sourceType = StudyDbValue.SourceAssetType.GENERATED_FIGURE,
            createdAtEpochMillis = 4_000,
            provenance = GeneratedFigureProvenance(
                model = "gpt-image-2",
                figureId = figureId,
                purpose = "CLEAN_REDRAW",
            ),
            figureIdentity = figureId,
        )
        try {
            val file = File(context.filesDir, record.relativePath)
            assertTrue("生成图文件必须落盘", file.isFile)

            // TC260 三字段从 TAG_XMP 读回（PNG 走 iTXt 块，容器由 exifinterface 分流）。
            val xmp = ExifInterface(file).getAttribute(ExifInterface.TAG_XMP)
            val fields = requireNotNull(parseAigcXmpPacket(xmp)) {
                "TC260 AIGC 包必须随 PNG iTXt 可读回：${xmp?.take(200)}"
            }
            assertEquals("1", fields.label)
            assertEquals("SmartMistakeBook", fields.contentProducer)
            assertEquals(figureId, fields.produceId)

            // 与既有 EXIF 四字段共存：两层判据并列，读回仍通过且互不挤占。
            val metadata = readGeneratedFigureMetadata(file)
            assertTrue(metadata.aiGenerated)
            assertEquals("gpt-image-2", metadata.model)
            assertEquals(figureId, metadata.figureId)

            // iTXt 块插入后仍是合法 PNG。
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            assertTrue(
                "插入 XMP 后必须仍可解码：${bounds.outWidth}x${bounds.outHeight}",
                bounds.outWidth > 0 && bounds.outHeight > 0,
            )

            // 字节拷贝后的那一份：TC260 标识随字节走（与路径/数据库无关）。
            val copied = File(context.cacheDir, "copied-aigc-figure.png")
            copied.writeBytes(file.readBytes())
            try {
                val copiedFields = requireNotNull(
                    parseAigcXmpPacket(ExifInterface(copied).getAttribute(ExifInterface.TAG_XMP)),
                ) { "字节拷贝后 TC260 标识必须仍在" }
                assertEquals(figureId, copiedFields.produceId)
            } finally {
                copied.delete()
            }
        } finally {
            runCatching { vault.delete(record) }
        }
    }

    @Test
    fun theTc260AigcLabelIsWrittenIntoJpegBytesAsWell() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val vault = AndroidCanonicalAssetVault(context)
        val figureId = "figure-aigc-xmp-jpeg-1"

        val record = vault.persistCleanImageBytes(
            bytes = compressedJpegBytes(),
            mimeType = "image/jpeg",
            sourceType = StudyDbValue.SourceAssetType.GENERATED_FIGURE,
            createdAtEpochMillis = 5_000,
            provenance = GeneratedFigureProvenance(
                model = "gpt-image-2",
                figureId = figureId,
                purpose = "CLEAN_REDRAW",
            ),
            figureIdentity = figureId,
        )
        try {
            val file = File(context.filesDir, record.relativePath)
            val xmp = ExifInterface(file).getAttribute(ExifInterface.TAG_XMP)
            val fields = requireNotNull(parseAigcXmpPacket(xmp)) {
                "TC260 AIGC 包必须随 JPEG 独立 APP1 段可读回：${xmp?.take(200)}"
            }
            assertEquals("1", fields.label)
            assertEquals(figureId, fields.produceId)
            assertTrue(readGeneratedFigureMetadata(file).aiGenerated)

            // APP1 段插入后仍是合法 JPEG。
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            assertTrue(
                "插入 XMP 后必须仍可解码：${bounds.outWidth}x${bounds.outHeight}",
                bounds.outWidth > 0 && bounds.outHeight > 0,
            )
        } finally {
            runCatching { vault.delete(record) }
        }
    }

    @Test
    fun anImagePersistedWithoutProvenanceCarriesNoAigcLabel() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val vault = AndroidCanonicalAssetVault(context)
        // 非 AI 图 = 无 provenance 的普通资产（生产上只有生成路径构造 provenance；vault 以
        // `provenance?.let { … }` 单点选择——这里从文件字节侧验证它的下游效果）。
        val record = vault.persistCleanImageBytes(
            bytes = compressedPngBytes(),
            mimeType = "image/png",
            sourceType = StudyDbValue.SourceAssetType.PHOTO_PICKER,
            createdAtEpochMillis = 6_000,
        )
        try {
            val file = File(context.filesDir, record.relativePath)
            val xmp = ExifInterface(file).getAttribute(ExifInterface.TAG_XMP)
            assertNull("非生成图不得写 TC260 AIGC 包（非 AI 图不写）", xmp)
            assertNull(parseAigcXmpPacket(xmp))
            val metadata = readGeneratedFigureMetadata(file)
            assertFalse("非生成图读回必须 fail-closed 为非 AI", metadata.aiGenerated)
        } finally {
            runCatching { vault.delete(record) }
        }
    }

    /** 结构合法的 PNG 夹具（真实 Bitmap 编码；插入元数据后要能被 `BitmapFactory` 解码）。 */
    private fun compressedPngBytes(): ByteArray = ByteArrayOutputStream().use { output ->
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(0xFF3366AA.toInt())
        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        bitmap.recycle()
        output.toByteArray()
    }

    /** 结构合法的 JPEG 夹具（vault 的 `preferJpeg` 路径会按 mime 重编码为 JPEG）。 */
    private fun compressedJpegBytes(): ByteArray = ByteArrayOutputStream().use { output ->
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(0xFF3366AA.toInt())
        check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output))
        bitmap.recycle()
        output.toByteArray()
    }
}
