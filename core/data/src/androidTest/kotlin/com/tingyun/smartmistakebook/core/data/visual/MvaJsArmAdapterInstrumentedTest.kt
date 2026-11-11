package com.tingyun.smartmistakebook.core.data.visual

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tingyun.smartmistakebook.core.domain.VisualContentKind
import com.tingyun.smartmistakebook.core.domain.VisualContentMeasureBatch
import com.tingyun.smartmistakebook.core.domain.VisualContentMeasureResult
import com.tingyun.smartmistakebook.core.domain.VisualContentMeasurer
import com.tingyun.smartmistakebook.core.domain.VisualExecutionRequest
import com.tingyun.smartmistakebook.core.domain.VisualExecutionResult
import com.tingyun.smartmistakebook.core.domain.VisualMeasuredContent
import com.tingyun.smartmistakebook.core.domain.VisualViewport
import com.tingyun.smartmistakebook.core.model.figureSha256Hex
import java.io.File
import java.nio.charset.StandardCharsets
import kotlin.math.ceil
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * MVA JS 臂设备适配器（批次 51 / G4 T25）：程序 JSON → 真引擎 → 四件产物。
 *
 * **它消灭的具体失败**：MVA 的 JS 臂此前没有"程序→图→证据"的记录面——`figure_eval_gate`
 * 的 `JsPort` 只留 `scene_sha256/scene_bytes/content_ids`（`figure_eval_gate.py:596-600`），
 * 事实提取器拿不到 DrawOps 与 prepare 批文本，门只能读 0 个可评样本。本用例把每条题解
 * 程序经**产品同一条回放链**跑一遍，逐题落四件产物：
 *
 * 1. `scene.json`：引擎 `Rendered.sceneJson`（SceneV1，校验后）；
 * 2. `drawops.json`：`Rendered.drawCommandsJson`（DrawOps V1，painter 真正回放的东西）；
 * 3. `metrics.json`：prepare 批的 `contentId → payload/width/height/baseline`（**文本不在
 *    显示列表里**，没有这件 label_set/label_absent 全不可判，研究 §2.2）；
 * 4. `render.png`：`VisualDrawOpsParser.parse(rendered)` → `VisualDrawOpsPainter` 画到
 *    Bitmap（pxPerDu=2，与 360×240 du 视口同构）——C 通道校准/判官的输入面。
 *
 * 外加 `record.json`（状态/延迟/四件 sha/拒绝码）与 `manifest.json`（设备/密度/计数）。
 *
 * **与生产链路的差异（如实登记，不得当完整产品路径引用）**：
 * - 本用例直接实例化 `QuickJsVisualExecutionEngine`（core:data），不经过 app 的
 *   `VisualJsClientEngine` + `:visual_js` 隔离进程/AIDL 队列——两者共用同一引擎实现，
 *   但本轨不覆盖进程隔离/重建/1500 ms watchdog 的宿主侧路径；
 * - 度量器是本用例内的 Paint 度量（与 `V01VisualChainInstrumentedTest.DeviceTextMeasurer`
 *   同型），不是 app 的 `AndroidVisualContentMeasurer`（math lowering 不在本轨）；
 * - 每条程序新建并关闭引擎（隔离单个坏程序；生产服务是常驻进程）。
 *
 * 激活方式：默认跳过（不是伪造通过）。显式运行：
 * ```
 * adb push <programs-dir> /sdcard/Android/data/com.tingyun.smartmistakebook.core.data.test/files/mva/js-arm/input
 * ANDROID_SERIAL=emulator-5590 ./gradlew :core:data:connectedDebugAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.class=com.tingyun.smartmistakebook.core.data.visual.MvaJsArmAdapterInstrumentedTest \
 *   -Pandroid.testInstrumentationRunnerArguments.mvaProgramsDir=/sdcard/Android/data/com.tingyun.smartmistakebook.core.data.test/files/mva/js-arm/input
 * adb pull /sdcard/Android/data/com.tingyun.smartmistakebook.core.data.test/files/mva/js-arm/out
 * ```
 */
@RunWith(AndroidJUnit4::class)
class MvaJsArmAdapterInstrumentedTest {

    @Test
    fun theProgramSetIsRenderedIntoTypedDrawOpsMetricsAndPng() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val arguments = InstrumentationRegistry.getArguments()
        val inputArgument = arguments.getString(ARG_PROGRAMS_DIR)
        assumeTrue(
            "MVA 适配器仅按需激活（$ARG_PROGRAMS_DIR 未设 → 跳过，不是伪造通过）",
            inputArgument != null,
        )
        val inputDir = File(requireNotNull(inputArgument))
        assertTrue("programs dir does not exist: ${inputDir.absolutePath}", inputDir.isDirectory)

        val context = instrumentation.targetContext
        val density = context.resources.displayMetrics.density
        val fontScale = context.resources.displayMetrics.scaledDensity / density
        val outRoot = arguments.getString(ARG_OUT_DIR)?.let(::File)
            ?: File(context.getExternalFilesDir(null), "mva/js-arm")
        val outDir = File(outRoot, "out").apply { mkdirs() }

        val programs = inputDir.listFiles { file -> file.isFile && file.name.endsWith(".json") }
            ?.sortedBy { it.name }
            .orEmpty()
        assertTrue("no *.json programs under ${inputDir.absolutePath}", programs.isNotEmpty())

        val records = mutableListOf<JSONObject>()
        programs.forEachIndexed { index, file ->
            val record = runOneProgram(file, index, density, fontScale, outDir)
            records.add(record)
            System.err.println(
                "[mva-adapter] ${record.optString("task_id")} -> ${record.optString("status")} " +
                    "${record.optString("reason")} ${record.optInt("latency_ms")}ms",
            )
        }

        val manifest = JSONObject()
            .put("generated_by", "MvaJsArmAdapterInstrumentedTest")
            .put("device_model", Build.MODEL)
            .put("device_manufacturer", Build.MANUFACTURER)
            .put("sdk_int", Build.VERSION.SDK_INT)
            .put("density", density.toDouble())
            .put("font_scale", fontScale.toDouble())
            .put("px_per_du", PX_PER_DU.toDouble())
            .put("engine", "QuickJsVisualExecutionEngine (core:data, per-program instance)")
            .put("divergences", JSONArray().apply {
                put("no app VisualJsClientEngine / :visual_js isolated process / AIDL queue")
                put("measurer is an in-test Paint measurer, not app AndroidVisualContentMeasurer")
                put("one engine instance per program (production runs a resident service)")
            })
            .put("program_count", programs.size)
            .put("status_counts", JSONObject().apply {
                records.groupingBy { it.optString("status") }.eachCount()
                    .forEach { (status, count) -> put(status, count) }
            })
            .put("records", JSONArray(records))
        File(outDir, "manifest.json").writeText(manifest.toString(2), Charsets.UTF_8)

        assertEquals("every input program must produce exactly one record",
            programs.size, records.size)
        assertTrue("manifest must list every record", records.size == manifest.getJSONArray("records").length())
    }

    private suspend fun runOneProgram(
        file: File,
        index: Int,
        density: Float,
        fontScale: Float,
        outDir: File,
    ): JSONObject {
        val baseName = file.name.removeSuffix(".json").replace(SAFE_NAME, "_") +
            "-" + index.toString().padStart(3, '0')
        val record = JSONObject()
            .put("input_file", file.name)
            .put("program_sha256", figureSha256Hex(file.readBytes()))
        try {
            val payload = JSONObject(file.readText(Charsets.UTF_8))
            val taskId = payload.optString("task_id", baseName)
            record.put("task_id", taskId)
            val sourceJs = payload.optString("source_js")
            if (sourceJs.isBlank()) {
                record.put("status", "ADAPTER_ERROR")
                    .put("reason", "ADAPTER_MISSING_SOURCE_JS")
                return record
            }
            val viewportJson = payload.optJSONObject("viewport")
            val viewport = VisualViewport(
                widthDu = viewportJson?.optInt("widthDu", 360) ?: 360,
                heightDu = viewportJson?.optInt("heightDu", 240) ?: 240,
            )
            record.put("viewport", JSONObject()
                .put("widthDu", viewport.widthDu).put("heightDu", viewport.heightDu))

            val measurer = RecordingTextMeasurer(density, fontScale)
            val engine = QuickJsVisualExecutionEngine(measurer)
            val startedAt = SystemClock.elapsedRealtime()
            val result = try {
                engine.execute(
                    VisualExecutionRequest(
                        programId = payload.optString("program_id", "mva-" + record.getString("task_id")),
                        sourceJs = sourceJs,
                        sceneRevision = payload.optInt("scene_revision", 1),
                        stateJson = null,
                        viewport = viewport,
                        timeMs = payload.optLong("time_ms", 0L),
                        seed = payload.optLong("seed", 42L),
                        theme = payload.optString("theme", "light"),
                    ),
                )
            } finally {
                engine.close()
            }
            val latencyMs = SystemClock.elapsedRealtime() - startedAt
            record.put("latency_ms", latencyMs)
            record.put("measurement", measurer.toJson())

            when (result) {
                is VisualExecutionResult.Rejected -> {
                    record.put("status", "REJECTED")
                        .put("reason", result.reason.name)
                        .put("diagnostics", result.diagnostics)
                    if (measurer.unsupportedMath) {
                        record.put("classification", "MEASUREMENT_MATH_UNSUPPORTED")
                    }
                }
                is VisualExecutionResult.Rendered -> renderArtifacts(
                    record, result, viewport, measurer, fontScale, outDir, baseName,
                )
            }
        } catch (failure: Throwable) {
            record.put("status", "ADAPTER_ERROR")
                .put("reason", "ADAPTER_EXCEPTION")
                .put("diagnostics", "${failure.javaClass.simpleName}: ${failure.message}")
        } finally {
            // 早退分支（缺 sourceJs/缺 drawCommands）也必须落盘：记录是证据，不是返回值。
            File(outDir, "$baseName.record.json").writeText(record.toString(2), Charsets.UTF_8)
        }
        return record
    }

    private fun renderArtifacts(
        record: JSONObject,
        rendered: VisualExecutionResult.Rendered,
        viewport: VisualViewport,
        measurer: RecordingTextMeasurer,
        fontScale: Float,
        outDir: File,
        baseName: String,
    ) {
        record.put("status", "RENDERED")
        val sceneBytes = rendered.sceneJson.toByteArray(StandardCharsets.UTF_8)
        File(outDir, "$baseName.scene.json").writeBytes(sceneBytes)
        record.put("scene_sha256", figureSha256Hex(sceneBytes))
        record.put("scene_bytes", sceneBytes.size)
        record.put("scene_schema_version", rendered.sceneSchemaVersion)

        val drawJson = rendered.drawCommandsJson
        if (drawJson.isNullOrBlank()) {
            record.put("status", "ADAPTER_ERROR")
                .put("reason", "ADAPTER_MISSING_DRAW_COMMANDS")
            return
        }
        val drawBytes = drawJson.toByteArray(StandardCharsets.UTF_8)
        File(outDir, "$baseName.drawops.json").writeBytes(drawBytes)
        record.put("draw_sha256", figureSha256Hex(drawBytes))
        record.put("draw_bytes", drawBytes.size)
        record.put("draw_schema_version", rendered.drawSchemaVersion)
        record.put("content_ids", JSONArray(rendered.contentIds.sorted()))

        val document = when (val validation = VisualDrawOpsParser.parse(rendered)) {
            is VisualDrawOpsValidation.Valid -> validation.document
            is VisualDrawOpsValidation.Invalid -> {
                record.put("status", "ADAPTER_ERROR")
                    .put("reason", "ADAPTER_DRAW_OPS_INVALID")
                    .put("diagnostics", "${validation.code}: ${validation.message}")
                return
            }
        }
        val width = ceil(viewport.widthDu * PX_PER_DU).toInt()
        val height = ceil(viewport.heightDu * PX_PER_DU).toInt()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val labelContent = measurer.labelContent(fontScale)
        try {
            VisualDrawOpsPainter().draw(document, Canvas(bitmap), PX_PER_DU) { contentId ->
                labelContent[contentId]
            }
            val pngFile = File(outDir, "$baseName.png")
            pngFile.outputStream().use { stream ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                    "PNG encode failed for $baseName"
                }
            }
            record.put("png_sha256", figureSha256Hex(pngFile.readBytes()))
            record.put("png_bytes", pngFile.length())
            record.put("png_width", width)
            record.put("png_height", height)
            record.put("stats", JSONObject()
                .put("nodes", document.stats.nodes)
                .put("pathCommands", document.stats.pathCommands)
                .put("labels", document.stats.labels)
                .put("ops", document.stats.ops))
        } catch (failure: VisualDrawOpsPaintingException) {
            record.put("status", "ADAPTER_ERROR")
                .put("reason", "ADAPTER_PAINT_FAILED")
                .put("diagnostics", "${failure.code}: ${failure.message}")
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * 记录型文本度量：contentId 由宿主按批内序分配（与 `DeviceTextMeasurer` 同约定），
     * 并把 payload 一起留下来——没有它，提取器只知道 contentId，`label_set/label_absent`
     * 全不可判（研究 §2.2 的三缺口之一）。
     *
     * math 不在本轨：抛出不支持（引擎折 ENGINE_CLOSED + MEASUREMENT_UNAVAILABLE 标记），
     * 记录里用 [unsupportedMath] 把它与"度量设施故障"区分开。
     */
    private class RecordingTextMeasurer(
        private val density: Float,
        private val fontScale: Float,
        private val sizeSp: Float = 16f,
    ) : VisualContentMeasurer {
        private val paint = Paint().apply {
            flags = REPLAY_TEXT_PAINT_FLAGS
            textSize = sizeSp * density * fontScale
        }
        private val entries = mutableListOf<JSONObject>()
        var unsupportedMath: Boolean = false
            private set

        override suspend fun measure(batch: VisualContentMeasureBatch): VisualContentMeasureResult {
            if (batch.entries.isEmpty()) {
                return VisualContentMeasureResult(emptyList())
            }
            val fontMetrics = paint.fontMetrics
            val lineHeightPx = fontMetrics.descent - fontMetrics.ascent
            val measured = batch.entries.mapIndexed { index, entry ->
                if (entry.kind != VisualContentKind.TEXT) {
                    unsupportedMath = true
                    throw UnsupportedOperationException("MVA_ADAPTER_MATH_UNSUPPORTED")
                }
                // 与生产 `VisualContentMeasuring` 同形：contentId = "c{index}-{digest 前 8}"。
                // 消灭"适配器用 c<index>、生产用 c<index>-<digest>"的契约分叉——作者若硬编码
                // 'c0' 在设备上能过、到生产会 SCENE_REF_CONTENT；逼作者引用 metrics[i].contentId。
                val digest = figureSha256Hex(entry.payload.toByteArray(StandardCharsets.UTF_8))
                val contentId = "c$index-" + digest.take(8)
                val widthDu = paint.measureText(entry.payload) / density
                val heightDu = lineHeightPx / density
                val baselineDu = -fontMetrics.ascent / density
                entries.add(JSONObject()
                    .put("contentId", contentId)
                    .put("payload", entry.payload)
                    .put("widthDu", widthDu.toDouble())
                    .put("heightDu", heightDu.toDouble())
                    .put("baselineDu", baselineDu.toDouble())
                    .put("fontSizeSp", sizeSp.toDouble())
                    .put("digest", digest))
                VisualMeasuredContent(
                    contentId = contentId,
                    widthDu = widthDu,
                    heightDu = heightDu,
                    baselineDu = baselineDu,
                    fontSizeSp = sizeSp,
                    digest = digest,
                )
            }
            return VisualContentMeasureResult(measured)
        }

        fun toJson(): JSONArray = JSONArray(entries)

        fun labelContent(fontScale: Float): Map<String, VisualLabelContent> =
            entries.associate { entry ->
                val payload = entry.getString("payload")
                val lines = mutableListOf<VisualLabelLine>()
                val segments = payload.split('\n')
                var offset = 0
                segments.forEachIndexed { index, segment ->
                    if (segment.isNotEmpty()) {
                        lines.add(VisualLabelLine(offset, offset + segment.length))
                    }
                    offset += segment.length
                    if (index != segments.lastIndex) offset += 1
                }
                if (lines.isEmpty()) {
                    lines.add(VisualLabelLine(0, payload.length))
                }
                entry.getString("contentId") to VisualLabelContent.Text(
                    payload = payload,
                    sizeSp = entry.getDouble("fontSizeSp").toFloat(),
                    fontScale = fontScale,
                    heightDu = entry.getDouble("heightDu").toFloat(),
                    lines = lines,
                )
            }
    }

    private companion object {
        const val ARG_PROGRAMS_DIR = "mvaProgramsDir"
        const val ARG_OUT_DIR = "mvaOutDir"
        const val PX_PER_DU = 2f
        val SAFE_NAME = Regex("[^A-Za-z0-9._-]")
    }
}
