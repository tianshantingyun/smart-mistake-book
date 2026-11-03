package com.tingyun.smartmistakebook.core.export

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.os.Process
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tingyun.smartmistakebook.core.domain.MistakeDetail
import com.tingyun.smartmistakebook.core.domain.MistakeDetailIdentity
import com.tingyun.smartmistakebook.core.domain.MistakeDetailState
import com.tingyun.smartmistakebook.core.domain.MistakeSourceAsset
import com.tingyun.smartmistakebook.core.domain.MistakeSourceLocation
import com.tingyun.smartmistakebook.core.domain.MistakeSourceSet
import com.tingyun.smartmistakebook.core.model.CapturedQuestionDocument
import com.tingyun.smartmistakebook.core.model.ContentBlock
import com.tingyun.smartmistakebook.core.model.NormalizedSourceRegion
import com.tingyun.smartmistakebook.core.model.QuestionBlockEvidence
import com.tingyun.smartmistakebook.core.model.QuestionBlockProvenance
import com.tingyun.smartmistakebook.core.model.QuestionBlockReviewStatus
import com.tingyun.smartmistakebook.core.model.QuestionDocument
import com.tingyun.smartmistakebook.core.model.WritingLayer
import com.tingyun.smartmistakebook.core.model.pdf.PdfAigcMetadata
import com.tingyun.smartmistakebook.core.model.pdf.PdfAigcReader
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * WS2 的设备侧（计划 items 18–22、24）：真实导出链 → 打标 → 独立读者。
 *
 * 覆盖：
 * 1. 真实 `MistakePdfExporter.prepare`（含 CLEAN_IMAGE）产物带 `/AIGC`，`PdfRenderer` 打开页数不变，
 *    且注入前后原始字节前缀逐字节相同（显式图注因此逐位不变）；
 * 2. 纯用户内容的真实导出读不到 `/AIGC`；
 * 3. 同输入二次 `prepare` 命中缓存且只有一个 `/AIGC`；
 * 4. 旧的无标记同键产物 + 旧 token：`reopenVerified` 返回 null，随后 `prepare` 重建带标记；
 * 5. 打印整份保留 `/AIGC`，部分页 `writeSubset` 丢失文档级 `/AIGC`（现状登记，重注入落在
 *    `MistakePdfDelivery.kt`，本批禁改）。
 *
 * **独立读者（pypdf）不在本类断言**：设备落盘路径由 `evidence=` 日志给出，宿主侧执行
 * `python -I -c "import pypdf,sys; r=pypdf.PdfReader(sys.argv[1]); print(r.metadata)" <file>`。
 * 设备绿必须同时绑定源快照与 APK sha（计划 §4）；禁止 `:app:connectedLocalFirstDebugAndroidTest`。
 *
 * 单独筛选（core:export 的 androidTest 包名，按实际 flavor 核对）：
 * ```
 * adb shell am instrument -w -r \
 *   -e class com.tingyun.smartmistakebook.core.export.MistakePdfExporterAigcInstrumentedTest \
 *   com.tingyun.smartmistakebook.core.export.test/androidx.test.runner.AndroidJUnitRunner
 * ```
 */
@RunWith(AndroidJUnit4::class)
class MistakePdfExporterAigcInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val evidenceDirectory: File by lazy {
        val directory = File(context.filesDir, "pdf-aigc-export/run-$RUN_STAMP")
        check(directory.isDirectory || directory.mkdirs() || directory.isDirectory) {
            "cannot create evidence directory $directory"
        }
        directory
    }

    @Test
    fun realExportWithAiImageIsLabeledAndKeepsRenderedBytesAndPageCount() {
        val png = writeSolidPng()
        try {
            val input = eligibleInputWithCleanImage(png, extraBlocks = emptyList())
            val baseline = File(context.cacheDir, "aigc-baseline-${UUID.randomUUID()}.pdf")
            try {
                DeterministicMistakePdfRenderer.render(input, baseline)

                val prepared = MistakePdfExporter(context).prepare(input)

                assertTrue(prepared.verifyIntegrity())
                assertEquals(input.inputSha256, prepared.file.parentFile?.name)
                assertEquals(pageCountOf(baseline), prepared.pageCount)
                val labeled = prepared.file.readBytes()
                assertArrayEquals(
                    "the incremental /AIGC update must append and keep the rendered bytes as a prefix " +
                        "(显式图注在内容流里，因此逐位不变)",
                    baseline.readBytes(),
                    labeled.copyOfRange(0, baseline.length().toInt()),
                )

                val readBackValue = PdfAigcReader.readLatestAigc(labeled)
                assertNotNull("the real export chain must carry /AIGC", readBackValue)
                val readBack = readBackValue!!
                assertEquals(PdfAigcMetadata.LABEL_AI_GENERATED, readBack.label)
                assertEquals(PdfAigcMetadata.DEFAULT_CONTENT_PRODUCER, readBack.contentProducer)
                assertEquals(input.inputSha256, readBack.produceId)
                assertEquals(1, PdfAigcReader.readLatestAigcKeyCount(labeled))

                val evidence = File(evidenceDirectory, "real-export-labeled.pdf")
                evidence.writeBytes(labeled)
                Log.i(
                    TAG,
                    "case=real-export source=${baseline.length()} labeled=${labeled.size} " +
                        "pages=${prepared.pageCount} sha256=${prepared.sha256} evidence=${evidence.absolutePath}",
                )
            } finally {
                baseline.delete()
            }
        } finally {
            png.delete()
        }
    }

    @Test
    fun pureUserContentExportHasNoAigcInfoEntry() {
        val prepared = MistakePdfExporter(context).prepare(plainInput())
        val bytes = prepared.file.readBytes()

        assertTrue(prepared.verifyIntegrity())
        assertNull("a pure user document must not carry /AIGC", PdfAigcReader.readLatestAigc(bytes))
        assertFalse(PdfAigcReader.readInfoStrings(bytes).containsKey("AIGC"))

        val evidence = File(evidenceDirectory, "pure-user-unlabeled.pdf")
        evidence.writeBytes(bytes)
        Log.i(TAG, "case=pure-user bytes=${bytes.size} evidence=${evidence.absolutePath}")
    }

    @Test
    fun secondPrepareReusesTheSameLabeledArtifact() {
        val png = writeSolidPng()
        try {
            val input = eligibleInputWithCleanImage(png, extraBlocks = emptyList())
            val exporter = MistakePdfExporter(context)
            val first = exporter.prepare(input)
            val second = exporter.prepare(input)

            assertEquals(first.sha256, second.sha256)
            assertEquals(1, PdfAigcReader.readLatestAigcKeyCount(second.file.readBytes()))
            assertEquals(
                input.inputSha256,
                PdfAigcReader.readLatestAigc(second.file.readBytes())!!.produceId,
            )
        } finally {
            png.delete()
        }
    }

    @Test
    fun oldUnmarkedArtifactUnderTheSameKeyIsRejectedAndRebuiltWithAigc() {
        val png = writeSolidPng()
        try {
            val aiInput = eligibleInputWithCleanImage(png, extraBlocks = emptyList())
            val exportRoot = File(context.cacheDir, MistakePdfExporter.EXPORT_DIRECTORY).apply { mkdirs() }
            // 代际哨兵由第一次 prepare/reopenVerified 创建；本用例必须从"上一代"开始。
            File(exportRoot, MistakePdfExporter.AIGC_POLICY_SENTINEL).delete()

            val staleDirectory = File(exportRoot, aiInput.inputSha256).apply { check(mkdirs()) }
            val stalePdf = File(staleDirectory, PREPARED_PDF_FILE_NAME)
            DeterministicMistakePdfRenderer.render(plainInput(), stalePdf)
            val staleSha = sha256File(stalePdf)
            File(staleDirectory, PREPARED_DIGEST_FILE_NAME)
                .writeText("$staleSha\n", Charsets.US_ASCII)
            assertNull("the stale artifact must start unlabelled", PdfAigcReader.readLatestAigc(stalePdf.readBytes()))

            val oldToken = PreparedMistakePdfToken(
                inputSha256 = aiInput.inputSha256,
                pdfSha256 = staleSha,
                pageCount = pageCountOf(stalePdf),
            )
            val exporter = MistakePdfExporter(context)

            assertNull(
                "an old token must not reopen a pre-generation unlabelled artifact",
                exporter.reopenVerified(oldToken),
            )
            assertFalse("the sentinel cleanup must delete the stale published pair", staleDirectory.exists())

            val rebuilt = exporter.prepare(aiInput)

            assertTrue(rebuilt.verifyIntegrity())
            assertEquals(PdfAigcMetadata.LABEL_AI_GENERATED, PdfAigcReader.readLatestAigc(rebuilt.file.readBytes())!!.label)
            assertEquals(aiInput.inputSha256, PdfAigcReader.readLatestAigc(rebuilt.file.readBytes())!!.produceId)
            assertTrue(File(exportRoot, MistakePdfExporter.AIGC_POLICY_SENTINEL).isFile)
            Log.i(TAG, "case=stale-token staleSha=$staleSha rebuiltSha=${rebuilt.sha256}")
        } finally {
            png.delete()
        }
    }

    @Test
    fun wholePrintKeepsAigcWhilePartialPrintDropsTheDocumentLevelEntry() {
        val png = writeSolidPng()
        try {
            val input = eligibleInputWithCleanImage(
                png,
                extraBlocks = listOf(
                    ContentBlock.Paragraph("long", "函数图像分析。".repeat(700)),
                ),
            )
            val prepared = MistakePdfExporter(context).prepare(input)
            assertTrue("the fixture must span at least two pages", prepared.pageCount >= 2)

            val whole = File(context.cacheDir, "print-whole-${UUID.randomUUID()}.pdf")
            val subset = File(context.cacheDir, "print-subset-${UUID.randomUUID()}.pdf")
            try {
                openForWrite(whole).use { descriptor ->
                    assertEquals(
                        prepared.sha256,
                        PreparedPdfPrintWriter.copyWhole(prepared, descriptor, CancellationSignal()),
                    )
                }
                assertNotNull(
                    "whole-document print copies the prepared bytes and keeps /AIGC",
                    PdfAigcReader.readLatestAigc(whole.readBytes()),
                )

                openForWrite(subset).use { descriptor ->
                    PreparedPdfPrintWriter.writeSubset(
                        prepared = prepared,
                        pageIndexes = listOf(0),
                        destination = descriptor,
                        cancellationSignal = CancellationSignal(),
                    )
                }
                assertEquals(1, pageCountOf(subset))
                // 现状登记：writeSubset 逐页重绘进新 PdfDocument，新文档只有 Skia 的 /Info（无 /AIGC）。
                // 处置（缓冲子集 → 按 prepared 的 /AIGC 重注入 → 一次写出）落在 MistakePdfDelivery.kt，
                // 该文件本批禁改；本条断言在重注入落地时必须改为"读回同一份 JSON"。
                assertNull(
                    "known gap: partial-page print loses the document-level /AIGC until relabel lands",
                    PdfAigcReader.readLatestAigc(subset.readBytes()),
                )
                Log.i(
                    TAG,
                    "case=print whole=${whole.length()} subset=${subset.length()} " +
                        "subsetPages=1 evidence=${whole.absolutePath}",
                )
            } finally {
                whole.delete()
                subset.delete()
            }
        } finally {
            png.delete()
        }
    }

    // ---------------------------------------------------------------- 夹具

    private fun plainInput(): MistakePdfExportInput = (
        MistakePdfEligibility.check(
            readyState(
                blocks = listOf(ContentBlock.Paragraph("stem", "纯用户录入的题干")),
                source = MistakeSourceSet.Missing,
            ),
        ) as MistakePdfEligibilityResult.Eligible
        ).input

    private fun eligibleInputWithCleanImage(
        png: File,
        extraBlocks: List<ContentBlock>,
    ): MistakePdfExportInput = (
        MistakePdfEligibility.check(
            readyState(
                blocks = listOf(ContentBlock.Paragraph("stem", "题干文字在重绘图的下面。")) + extraBlocks,
                source = MistakeSourceSet.Present(
                    listOf(
                        MistakeSourceAsset(
                            role = CLEAN_IMAGE_SOURCE_ROLE,
                            sourceAssetId = "redraw-${UUID.randomUUID()}",
                            contentSha256 = sha256File(png),
                            mimeType = "image/png",
                            byteSize = png.length(),
                            width = 200,
                            height = 100,
                            sourceType = "GENERATED_FIGURE",
                            createdAtEpochMillis = png.lastModified(),
                            location = MistakeSourceLocation.Available("file://${png.absolutePath}"),
                        ),
                    ),
                ),
            ),
        ) as MistakePdfEligibilityResult.Eligible
        ).input

    private fun readyState(
        blocks: List<ContentBlock>,
        source: MistakeSourceSet,
    ): MistakeDetailState.Ready {
        val unique = UUID.randomUUID().toString()
        return MistakeDetailState.Ready(
            detail = MistakeDetail(
                identity = MistakeDetailIdentity(
                    errorBookEntryId = "entry-$unique",
                    problemId = "problem-$unique",
                    problemRevisionId = "revision-$unique",
                    revisionNumber = 1,
                    title = "AIGC 导出用例",
                    subject = "数学",
                ),
                fallbackMarkdown = "fallback must never be used",
                source = source,
            ),
            questionDocument = CapturedQuestionDocument(
                document = QuestionDocument(
                    id = "document-$unique",
                    title = "AIGC 导出用例",
                    blocks = blocks,
                ),
                blockEvidence = blocks.map { block ->
                    QuestionBlockEvidence(
                        blockId = block.id,
                        sourceAssetId = "source-$unique",
                        sourceRegion = NormalizedSourceRegion(0.0, 0.0, 1.0, 1.0),
                        writingLayer = WritingLayer.PRINTED,
                        provenance = QuestionBlockProvenance.USER_CORRECTION,
                        reviewStatus = QuestionBlockReviewStatus.USER_CONFIRMED,
                    )
                },
            ),
        )
    }

    private fun writeSolidPng(): File {
        val file = File(context.cacheDir, "aigc-clean-${UUID.randomUUID()}.png")
        val bitmap = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.rgb(20, 20, 20))
            FileOutputStream(file).use { output ->
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
        } finally {
            bitmap.recycle()
        }
        return file
    }

    private fun openForWrite(file: File): ParcelFileDescriptor =
        ParcelFileDescriptor.open(
            file,
            ParcelFileDescriptor.MODE_READ_WRITE or
                ParcelFileDescriptor.MODE_CREATE or
                ParcelFileDescriptor.MODE_TRUNCATE,
        )

    private fun pageCountOf(file: File): Int =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer -> renderer.pageCount }
        }

    private companion object {
        const val TAG = "MistakePdfExporterAigc"

        /** 同一 instrumentation 进程的所有用例共享；新进程 = 新证据目录。 */
        val RUN_STAMP = "${System.currentTimeMillis()}-${Process.myPid()}"
    }
}
