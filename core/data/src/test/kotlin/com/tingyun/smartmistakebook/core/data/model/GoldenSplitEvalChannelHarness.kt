package com.tingyun.smartmistakebook.core.data.model

import com.tingyun.smartmistakebook.core.data.model.wire.protocolFor
import com.tingyun.smartmistakebook.core.domain.isUsableSplitRegionSet
import com.tingyun.smartmistakebook.core.model.CaptureAssessmentInput
import com.tingyun.smartmistakebook.core.model.CaptureAssessmentOrigin
import com.tingyun.smartmistakebook.core.model.CaptureAssessmentOutput
import com.tingyun.smartmistakebook.core.model.ModelProviderProtocol
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * AUD-08 批次 48 WS2 导出通道：金标页面图像 → 生产切分 wire → 同 schema JSON。
 *
 * **它消灭的具体失败**：批次 48 之前，"对金标页跑切分"没有可重跑入口——应用导出只能来自
 * 设备 DB 快照（`export_split_eval.py`），而设备上从未配置过能看图的 Provider。本通道把
 * **生产 wire**（真实 `OpenAiModelProtocol.requestBody` + 真实 `parseCompletion` +
 * 真实 `CaptureAssessment` 契约校验 + 真实 `isUsableSplitRegionSet` 消费侧判据）原样跑在
 * 金标页面图像上，产出与 `export_split_eval.py` 同 schema 的页面框集，供
 * `measure_split_accuracy.py` / `check_split_gate.py` 消费。
 *
 * **与生产链路的差异（如实登记，不得当"完整产品路径"引用）**：
 * 1. 传输层用 HttpURLConnection 直连 Provider（生产走 `OpenAiCompatibleModelGateway` 的
 *    授权/预算/事件管线）——请求体字节同源（同一 `requestBody` 构建），响应解析同一函数。
 * 2. 图像出网副本由调用方脚本按 `EgressImageBudget` 的预算口径准备（长边 2048、JPEG q88、
 *    超 1.5MiB 回落 q70），本 harness 用真实 `EgressImageBudget.scaledSize` 复算目标尺寸
 *    逐字节断言；像素重采样是 PIL，不是 Android Skia（android.jar 无 ImageIO，单测编译
 *    类路径不含 AWT——这是通道形态的硬约束，不是选择）。
 * 3. 模型配置来自环境变量（key 经进程环境注入，不落仓；证据只记 sha8）。
 *
 * **盲标红线**：本文件不读 `tools/split_golden/` 下任何金标/切分输出；输入只有页面出网副本
 * 及其元数据，输出只有应用侧框集。dev/acceptance 的页清单由调用方脚本按
 * `split_golden_v2.json` 的 `split` 块传入，本文件不做任何页级选择逻辑。
 *
 * 激活方式：默认（无 `SPLIT_EVAL_OUT_DIR`）以 assumeTrue 跳过——不进常规单测门、
 * 不伪造通过；只有显式按 run 激活才执行。
 */
class GoldenSplitEvalChannelHarness {

    /** 证据只允许记 key 的 sha256 前 8 位（批次 48 裁定）；不匹配即拒绝执行。 */
    private val expectedKeySha8 = "cd10efbc"

    /** 唯一允许的端点主机（SSRF 边界；与批次 48 Provider 冒烟同一主机）。 */
    private val allowedBaseUrl = "https://api.deepseek.com"

    /** 原始回包留痕上限（批次 51 dev 调参；单页回包远小于此值，超出即异常）。 */
    private val MAX_RAW_RESPONSE_CHARS = 200_000

    @Test
    fun exportGoldenPageSplits() {
        val outDirEnv = System.getenv("SPLIT_EVAL_OUT_DIR")
        assumeTrue("导出通道仅按需激活（SPLIT_EVAL_OUT_DIR 未设 → 跳过，不是伪造通过）", outDirEnv != null)
        val outDir = File(requireNotNull(outDirEnv))
        val egressDir = File(requireNotNull(System.getenv("SPLIT_EVAL_EGRESS_DIR")) { "SPLIT_EVAL_EGRESS_DIR" })
        val runId = requireNotNull(System.getenv("SPLIT_EVAL_RUN_ID")) { "SPLIT_EVAL_RUN_ID" }
        val modelId = requireNotNull(System.getenv("SPLIT_EVAL_MODEL")) { "SPLIT_EVAL_MODEL" }
        val pages = requireNotNull(System.getenv("SPLIT_EVAL_PAGES")) { "SPLIT_EVAL_PAGES" }
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        require(pages.isNotEmpty()) { "SPLIT_EVAL_PAGES is empty" }
        val keyChars = requireNotNull(System.getenv("SMB_REAL_PROVIDER_KEY")) { "SMB_REAL_PROVIDER_KEY" }
            .toCharArray()
        val keySha8 = MessageDigest.getInstance("SHA-256")
            .digest(String(keyChars).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(8)
        check(keySha8 == expectedKeySha8) { "provider key sha8 mismatch: $keySha8" }

        outDir.mkdirs()
        val protocol = protocolFor(ModelProviderProtocol.OPENAI_CHAT_COMPLETIONS)

        val manifestPages = mutableListOf<JsonObject>()
        for (page in pages) {
            val record = runOnePage(protocol, egressDir, page, modelId, keyChars, allowedBaseUrl)
            val outFile = File(outDir, page.removeSuffix(".jpg") + ".json")
            outFile.writeText(
                Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), record),
                Charsets.UTF_8,
            )
            manifestPages += buildJsonObject {
                put("file", JsonPrimitive(page))
                put("record", JsonPrimitive(outFile.name))
                put("status", JsonPrimitive(record["status"]!!.jsonPrimitive.content))
                put("regionCount", JsonPrimitive(record["regions"]?.jsonArray?.size ?: 0))
            }
            // 进度走 stderr，不污染 JUnit 结果流。
            System.err.println("[split-eval] $runId $page -> ${record["status"]}")
        }
        val manifest = buildJsonObject {
            put("runId", JsonPrimitive(runId))
            put("channel", JsonPrimitive("golden-split-eval-harness-v1"))
            put("wireProtocol", JsonPrimitive(ModelProviderProtocol.OPENAI_CHAT_COMPLETIONS.wireId))
            put("model", JsonPrimitive(modelId))
            put("endpoint", JsonPrimitive(allowedBaseUrl))
            put("keySha8", JsonPrimitive(keySha8))
            put("divergences", buildJsonArray {
                add(JsonPrimitive("transport: plain HTTPS POST instead of gateway authorization pipeline (same requestBody builder, same parseCompletion)"))
                add(JsonPrimitive("image egress copy: prepared by caller per EgressImageBudget plan (long edge 2048, JPEG q88, q70 fallback); PIL resampling, not Android Skia bytes; scaledSize re-verified here"))
            })
            put("pages", buildJsonArray { manifestPages.forEach { add(it) } })
        }
        File(outDir, "manifest.json").writeText(
            Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), manifest),
            Charsets.UTF_8,
        )
        System.err.println("[split-eval] $runId manifest written: ${manifestPages.size} pages")
    }

    private fun runOnePage(
        protocol: com.tingyun.smartmistakebook.core.data.model.wire.ModelWireProtocol,
        egressDir: File,
        page: String,
        modelId: String,
        keyChars: CharArray,
        baseUrl: String,
    ): JsonObject {
        // 回包文本在 try 外持有：catch 里也要留诊断痕迹（try 内声明的 catch 不可见）。
        var lastResponseBody = ""
        return try {
        // 调用方按 EgressImageBudget 预算准备好的出网副本与元数据（Python 侧生成）。
        val egressFile = File(egressDir, page)
        require(egressFile.isFile) { "egress copy missing: ${egressFile.path}" }
        val meta = Json.parseToJsonElement(
            File(egressDir, page + ".meta.json").readText(Charsets.UTF_8),
        ).jsonObject
        val egressBytes = egressFile.readBytes()
        val sourceWidth = meta["sourceWidth"]!!.jsonPrimitive.content.toInt()
        val sourceHeight = meta["sourceHeight"]!!.jsonPrimitive.content.toInt()
        // 预算不变量用真实类复算（消灭"调用方脚本口径漂移而无人报红"）：
        val target = EgressImageBudget.scaledSize(sourceWidth, sourceHeight)
        val egressWidth = meta["egressWidth"]!!.jsonPrimitive.content.toInt()
        val egressHeight = meta["egressHeight"]!!.jsonPrimitive.content.toInt()
        check(egressWidth == target.width && egressHeight == target.height) {
            "egress copy size $egressWidth x $egressHeight != budget target ${target.width} x ${target.height}"
        }
        check(egressBytes.size <= EgressImageBudget.MAX_IMAGE_BYTES) {
            "egress copy exceeds MAX_IMAGE_BYTES: ${egressBytes.size}"
        }
        check(egressFile.length() <= Int.MAX_VALUE)

        // 生产语义：CaptureAssessmentInput 携带原始（canonical）资产尺寸。
        val input = CaptureAssessmentInput(
            draftId = "golden-split-eval:$page",
            sourceAssetId = page,
            origin = CaptureAssessmentOrigin.LIBRARY,
            imageWidth = sourceWidth,
            imageHeight = sourceHeight,
        )
        val started = System.currentTimeMillis()
        val requestBody = OpenAiModelProtocol.requestBody(
            modelId = modelId,
            input = input,
            images = listOf(ApprovedImage(EgressImageBudget.egressMimeType("image/jpeg"), egressBytes)),
            stream = false,
            enableNativeTools = false,
        )
        val connection = URL("$baseUrl/chat/completions").openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 30_000
        connection.readTimeout = 180_000
        connection.doOutput = true
        connection.setRequestProperty("Authorization", "Bearer " + String(keyChars))
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.outputStream.use { it.write(requestBody.toByteArray(Charsets.UTF_8)) }
        val status = connection.responseCode
        val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
            ?.readBytes()?.toString(Charsets.UTF_8) ?: ""
        lastResponseBody = body
        val elapsed = System.currentTimeMillis() - started

        if (status !in 200..299) {
            buildJsonObject {
                put("file", JsonPrimitive(page))
                put("status", JsonPrimitive("HTTP_ERROR"))
                put("httpStatus", JsonPrimitive(status))
                put("errorHead", JsonPrimitive(body.take(400)))
                // 批次 51 dev 调参：原始回包全文留痕（模型输出，无凭据；供逐轮归因与复算）。
                put("responseRaw", JsonPrimitive(body.take(MAX_RAW_RESPONSE_CHARS)))
                put("elapsedMs", JsonPrimitive(elapsed))
                put("image", buildJsonObject {
                    put("egressBytes", JsonPrimitive(egressBytes.size))
                    put("sourceWidth", JsonPrimitive(sourceWidth))
                    put("sourceHeight", JsonPrimitive(sourceHeight))
                    put("mode", JsonPrimitive(meta["mode"]!!.jsonPrimitive.content))
                })
            }
        } else {
            // 诊断留痕：原始回包（模型输出，无凭据；只进 run 记录）。批次 51 起保留全文，
            // 供 dev 调参逐轮归因（旧字段 responseHead 只留 1600 字符，无法复盘契约拒收细节）。
            val responseHead = body.take(1600)
            // 生产同一解析入口：非流式回包 → content JSON → CaptureAssessment 契约校验。
            val output = protocol.parseCompletion(body, input, modelVersion = modelId)
            val assessment = (output as CaptureAssessmentOutput).assessment
            val regions = assessment.questionRegions
            val usable = regions.isUsableSplitRegionSet()
            buildJsonObject {
                put("file", JsonPrimitive(page))
                put("status", JsonPrimitive("OK"))
                put("httpStatus", JsonPrimitive(status))
                put("elapsedMs", JsonPrimitive(elapsed))
                put("responseHead", JsonPrimitive(responseHead))
                put("responseRaw", JsonPrimitive(body.take(MAX_RAW_RESPONSE_CHARS)))
                put("decision", JsonPrimitive(assessment.decision.name))
                put("issueCodes", buildJsonArray {
                    assessment.issues.forEach { add(JsonPrimitive(it.code.name)) }
                })
                put("regions", buildJsonArray {
                    regions.forEach { region ->
                        add(buildJsonObject {
                            put("left", JsonPrimitive(region.left))
                            put("top", JsonPrimitive(region.top))
                            put("right", JsonPrimitive(region.right))
                            put("bottom", JsonPrimitive(region.bottom))
                        })
                    }
                })
                put("usableSplitRegionSet", JsonPrimitive(usable))
                // 生产消费侧语义（BatchSplitRecognizer）：判据不过 → 不切，0 框。
                put("appliedRegionCount", JsonPrimitive(if (usable) regions.size else 0))
                put("image", buildJsonObject {
                    put("egressBytes", JsonPrimitive(egressBytes.size))
                    put("egressWidth", JsonPrimitive(egressWidth))
                    put("egressHeight", JsonPrimitive(egressHeight))
                    put("sourceWidth", JsonPrimitive(sourceWidth))
                    put("sourceHeight", JsonPrimitive(sourceHeight))
                    put("mode", JsonPrimitive(meta["mode"]!!.jsonPrimitive.content))
                })
                put("requestBodySha256", JsonPrimitive(sha256(requestBody.toByteArray(Charsets.UTF_8))))
                put("promptSha256", JsonPrimitive(sha256OfPromptText(requestBody)))
            }
        }
        } catch (failure: Exception) {
            buildJsonObject {
                put("file", JsonPrimitive(page))
                put("status", JsonPrimitive("ERROR"))
                put("errorType", JsonPrimitive(failure::class.simpleName ?: "Exception"))
                put("errorMessage", JsonPrimitive((failure.message ?: "").take(400)))
                put("responseHead", JsonPrimitive(lastResponseBody.take(1600)))
                // 契约拒收（InvalidModelResponseException）时全文回包是唯一归因材料。
                put("responseRaw", JsonPrimitive(lastResponseBody.take(MAX_RAW_RESPONSE_CHARS)))
            }
        }
    }

    private fun sha256OfPromptText(requestBody: String): String = try {
        val doc = Json.parseToJsonElement(requestBody).jsonObject
        val content = doc["messages"]!!.jsonArray[1].jsonObject["content"]!!.jsonArray
        val promptText = content[0].jsonObject["text"]!!.jsonPrimitive.content
        sha256(promptText.toByteArray(Charsets.UTF_8))
    } catch (_: Exception) {
        "UNAVAILABLE"
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
}
