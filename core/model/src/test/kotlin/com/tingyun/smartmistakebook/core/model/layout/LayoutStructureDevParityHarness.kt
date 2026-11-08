package com.tingyun.smartmistakebook.core.model.layout

import java.io.File
import kotlin.math.abs
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * AUD-08 / 批次 50 item 21：**dev 16 页**上 Kotlin 生产实现 × Python 诊断探针的逐页对拍。
 *
 * **它消灭的具体失败**：`LayoutStructure` 的默认参数来自 dev 实测；一旦参数、平滑或投影口径
 * 漂移，只有"重跑整个探针再人工比对"才发现得了。本用例把两条**独立实现**（Python:
 * `tools/layout_probe/probe_layout.py`；Kotlin: 生产类）钉在同一批页面字节上逐页比对，
 * 并同时锁住"栏判与金标 `layout` 标签只在两页上不一致"这条已知事实。
 *
 * **它不做什么**：不判门、不写证据文件、不跑 acceptance。输入是探针导出的 `.gray` 夹具与
 * 探针 JSON（`build/` 下的产物，gitignored）。缺夹具时 `assumeTrue` 跳过——**跳过不是通过**，
 * 激活方式写在会话证据的命令行里。
 *
 * 夹具格式（`probe_layout.py:622-627`）：4 字节小端宽 + 4 字节小端高 + w×h 字节灰度。
 */
class LayoutStructureDevParityHarness {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun kotlinProductionLayoutMatchesThePythonProbeOnAllDevPages() {
        val repo = repositoryRoot()
        val fixtures = File(System.getenv("LAYOUT_DEV_FIXTURES") ?: "${repo.path}/build/layout-probe/gray")
        val reference = File(System.getenv("LAYOUT_DEV_PROBE") ?: "${repo.path}/build/layout-probe/dev-dump.json")
        val golden = File(System.getenv("LAYOUT_DEV_GOLDEN") ?: "${repo.path}/tools/split_golden/tables/split_golden_v2.json")
        assumeTrue("缺 .gray 夹具（$fixtures）→ 跳过，不是通过", fixtures.isDirectory)
        assumeTrue("缺探针参考 JSON（$reference）→ 跳过，不是通过", reference.isFile)
        assumeTrue("缺金标 v2（$golden）→ 跳过，不是通过", golden.isFile)

        val goldenRoot = json.parseToJsonElement(golden.readText(Charsets.UTF_8)).jsonObject
        val devPages = goldenRoot["split"]!!.jsonObject["dev"]!!.jsonArray.map { it.jsonPrimitive.content }
        val goldByPage = goldenRoot["pages"]!!.jsonArray.associate { page ->
            val obj = page.jsonObject
            obj["file"]!!.jsonPrimitive.content to obj
        }
        val probeByPage = json.parseToJsonElement(reference.readText(Charsets.UTF_8)).jsonObject["pages"]!!
            .jsonArray.associate { page ->
                val obj = page.jsonObject
                obj["file"]!!.jsonPrimitive.content to obj
            }

        assertEquals("dev 子集页数与金标 split.dev 不一致", 16, devPages.size)

        var columnMismatches = mutableListOf<String>()
        var blockDeltaMax = 0
        var hintMin = Int.MAX_VALUE
        var hintMax = 0
        var candidateCount = 0
        var goldQuestionCount = 0
        var candidateHits = 0
        val mismatchingColumns = mutableListOf<String>()

        devPages.forEach { page ->
            val fixture = File(fixtures, "$page.gray")
            assumeTrue("缺页夹具 $page → 跳过，不是通过", fixture.isFile)
            val (width, height, gray) = readGray(fixture)
            val structure = LayoutStructure.analyse(gray, width, height)
            val referenceAnalysis = probeByPage.getValue(page).jsonObject["cv"]!!.jsonObject
            val gold = goldByPage.getValue(page)

            // 1) 栏判：与独立实现逐页一致（不一致即算法口径漂移）
            assertEquals(
                "$page 栏数与 Python 探针不一致（Kotlin=${structure.columns.size}，" +
                    "Python=${referenceAnalysis["columns"]!!.jsonPrimitive.int}）",
                referenceAnalysis["columns"]!!.jsonPrimitive.int,
                structure.columns.size,
            )
            // 2) 行带块数：已知差异 ≤2（边缘钳位平滑 vs np.convolve 'same'，见研究 §5.3）
            val referenceBlocks = referenceAnalysis["blocks"]!!.jsonPrimitive.int
            val delta = abs(structure.separators.size - referenceBlocks)
            blockDeltaMax = maxOf(blockDeltaMax, delta)
            assertTrue(
                "$page 行带块数偏离 Python 探针 $delta（$referenceBlocks → ${structure.separators.size}）" +
                    "超过已登记的 ≤2 容忍度；口径漂移需重跑研究而不是放宽本断言",
                delta <= 2,
            )
            // 3) 提示词预算与可用性
            assertTrue("$page 被判为朝向不可用 → 提示词只剩一句降级文本", !structure.orientationUncertain)
            val hint = structure.toPromptBlock()
            hintMin = minOf(hintMin, hint.length)
            hintMax = maxOf(hintMax, hint.length)
            assertTrue("$page 提示块为空", hint.isNotEmpty())
            assertTrue("$page 提示块 ${hint.length} 字符超出 ${PageStructure.MAX_HINT_CHARS}", hint.length <= PageStructure.MAX_HINT_CHARS)

            // 4) 栏判 vs 金标 layout 标签：只允许出现两个已登记例外（04854 旋转页 / 11649 暗光页）
            val goldTwoColumn = gold["layout"]!!.jsonPrimitive.content == "two-column"
            if (goldTwoColumn != structure.twoColumn) {
                mismatchingColumns += page
                assertTrue(
                    "$page 栏判与金标不一致，且不在两个已登记例外内：$mismatchingColumns",
                    page in KNOWN_COLUMN_MISMATCHES,
                )
            }

            // 5) 题号候选对金标题顶的命中（诊断量，不设门、不进 prompt）
            val goldTops = gold["boxes"]!!.jsonArray.map {
                it.jsonObject["top"]!!.jsonPrimitive.content.toDouble()
            }
            val goldQuestions = gold["questionCount"]!!.jsonPrimitive.int
            if (page !in KNOWN_COORDINATE_FRAME_ISSUES) {
                val used = mutableSetOf<Int>()
                goldTops.forEach { goldTop ->
                    val match = structure.questionCandidates.withIndex()
                        .filter { (_, candidate) ->
                            abs(candidate.topPx.toDouble() / height - goldTop) in 0.0..TOP_TOLERANCE
                        }
                        .minByOrNull { (_, candidate) -> abs(candidate.topPx.toDouble() / height - goldTop) }
                    if (match != null && used.add(match.index)) candidateHits++
                }
                candidateCount += structure.questionCandidates.size
                goldQuestionCount += goldQuestions
            }

            System.err.println(
                "[layout-dev] $page columns=${structure.columns.size} blocks=${structure.separators.size} " +
                    "pitch=${structure.linePitchPx} candidates=${structure.questionCandidates.size} " +
                    "goldQuestions=$goldQuestions hintChars=${hint.length}",
            )
        }

        System.err.println(
            "[layout-dev] summary pages=${devPages.size} columnMismatches=${mismatchingColumns.size}$mismatchingColumns " +
                "blockDeltaMax=$blockDeltaMax hintChars=$hintMin..$hintMax " +
                "candidates=$candidateCount goldQuestions=$goldQuestionCount candidateHits=$candidateHits " +
                "candidatePrecision=${if (candidateCount == 0) "n/a" else "%.4f".format(candidateHits.toDouble() / candidateCount)}",
        )
        // 6) 覆盖面锁：14/16 一致是两个已登记例外的算术后果；少于 14 页说明又坏了一页。
        assertTrue(
            "栏判一致页数 ${devPages.size - mismatchingColumns.size}/16 低于已登记基线 14/16",
            devPages.size - mismatchingColumns.size >= 14,
        )
        assertTrue("候选命中数为 0 —— 几何口径可能整体失效", candidateHits > 0)
    }

    private fun readGray(file: File): Triple<Int, Int, ByteArray> {
        val bytes = file.readBytes()
        require(bytes.size >= 8) { "${file.name} too short for the gray header" }
        val width = leInt(bytes, 0)
        val height = leInt(bytes, 4)
        require(bytes.size == 8 + width * height) {
            "${file.name} size ${bytes.size} != 8 + $width*$height"
        }
        return Triple(width, height, bytes.copyOfRange(8, bytes.size))
    }

    private fun leInt(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)

    private fun repositoryRoot(): File {
        var current: File? = File(System.getProperty("user.dir")).absoluteFile
        while (current != null) {
            if (File(current, "tools/split_golden/tables/split_golden_v2.json").isFile) return current
            current = current.parentFile
        }
        return File(System.getProperty("user.dir")).absoluteFile
    }

    private companion object {
        /** 金标题顶命中容差（±1.5% 页高）——与研究 §5.2 的 `blockCoverageAtTol` 同口径。 */
        const val TOP_TOLERANCE = 0.015

        /**
         * 已登记的两个栏判例外（研究 §5.2）：`…04854` 金标框落在旋转 90° 的坐标系里，
         * `…11649` 是暗光页的假双栏。两者都不是"这一页真错了"。
         */
        val KNOWN_COLUMN_MISMATCHES = setOf("IMG20260810004854.jpg", "IMG20260810011649.jpg")

        /** 金标坐标系与像素坐标系不齐的页（研究 §3.6）：候选命中率对它不可算。 */
        val KNOWN_COORDINATE_FRAME_ISSUES = setOf("IMG20260810004854.jpg")
    }
}
