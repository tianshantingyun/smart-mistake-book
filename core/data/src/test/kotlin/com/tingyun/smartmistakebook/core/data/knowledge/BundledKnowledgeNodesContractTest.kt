package com.tingyun.smartmistakebook.core.data.knowledge

import com.tingyun.smartmistakebook.core.database.DatabaseContractViolationException
import com.tingyun.smartmistakebook.core.database.KnowledgeBaseImportContract
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 内置包的**每一个**知识节点都必须过数据库导入契约的逐条判据。
 *
 * 它消灭的失败：契约里曾有一条凭空的 `require(aliases.size <= 32)`（无 KDoc、无具名常量、
 * 不从任何预算推导；真正的预算在特征提取器的 16 个片段，与别名条数无关），而内容里最多的
 * 节点有 84 条别名。于是 25 个知识点在调和时被逐条跳过，**进不了任何用户的库**，只在进度表
 * 里留一行。契约夹具测试用的是自造的小节点，照不到这个形态；只有"内置包 × 真契约"能照到。
 *
 * 判据用的是调和器决定的那个函数——同一个 [KnowledgeBaseImportContract.problemWith]、
 * 同一份 `byId`，不是另写一套规则：两套规则迟早漂移，而漂移的方向是"校验说没问题、
 * 导入却跳过"，正是这条测试要防的。
 */
class BundledKnowledgeNodesContractTest {

    @Test
    fun everyBundledKnowledgeNodeIsAcceptedByTheImportContract() {
        val packs = BundledKnowledgePackResources.load()
        val problems = mutableListOf<String>()
        var checked = 0
        for (pack in packs) {
            val nodesById = pack.nodes.associateBy { it.knowledgeNodeId }
            for (node in pack.nodes) {
                checked += 1
                KnowledgeBaseImportContract.problemWith(node, nodesById)?.let { problem ->
                    problems += "${node.knowledgeNodeId}: $problem"
                }
            }
        }
        assertTrue("内置包应有节点可校验", checked > 0)
        assertTrue("内置节点被导入契约拒绝：${problems.take(10)}", problems.isEmpty())
    }

    /**
     * P4（§2.5）：**主包合成源**也要过契约。
     *
     * 合成源不走 sidecar，而是由 loader 用顶层 `sourceUri` + 每科三元组现造。过去它完全
     * 不经过 `validateSourcesOnly`，所以"sidecar 干净、主包仍带 example.edu"这种形态测不出来。
     */
    @Test
    fun syntheticSourcesProducedByTheLoaderPassTheImportContract() {
        val packs = BundledKnowledgePackResources.load()
        for (pack in packs) {
            KnowledgeBaseImportContract.validateSourcesOnly(pack.sources + pack.teachingSources)
        }
        val current = packs.single { it.packId == "moe-2025-four-subjects-v1" }
        assertEquals("2025 包应有 4 条合成源", 4, current.sources.size)
        current.sources.forEach { source ->
            assertNull(
                "2025 包没有与 2025 版次对应的官方 URI，合成源必须为 null：${source.sourceId}",
                source.sourceUri,
            )
            assertTrue("合成源必须带 issuer：${source.sourceId}", !source.publisher.isNullOrBlank())
            assertTrue("合成源必须带 yearOrEdition：${source.sourceId}", !source.edition.isNullOrBlank())
        }
        val historical = packs.single { it.packId == "moe-2020-foundation-v1" }
        assertEquals("2020 九科包应有 9 条合成源", 9, historical.sources.size)
        historical.sources.forEach { source ->
            assertTrue(
                "2020 包已有真实官方 URI，不得回退成 null：${source.sourceId}",
                source.sourceUri?.startsWith("https://www.moe.gov.cn/") == true,
            )
        }
    }

    /**
     * N6（§2.5 / §2.6）：主包顶层 `sourceUri` 为占位域时，loader 现造出来的合成源必须被拒收。
     *
     * 必须走**真实 loader**（[ReviewedKnowledgePackJsonCodec.decode] → `toSource`），不能只手写
     * fixture——否则"顶层占位域"这条路径没有被覆盖。
     */
    @Test
    fun syntheticSourcesAreRejectedWhenThePackTopLevelUriIsAPlaceholder() {
        val raw = checkNotNull(
            BundledKnowledgeNodesContractTest::class.java.classLoader
                ?.getResourceAsStream("knowledge/moe-2025-four-subjects-v1.json"),
        ) { "找不到内置主包" }.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val root = Json { isLenient = false }.parseToJsonElement(raw).jsonObject
        val mutated = buildJsonObject {
            root.forEach { (key, value) -> if (key != "sourceUri") put(key, value) }
            put("sourceUri", JsonPrimitive("https://www.example.edu/curriculum/2025-four-subjects"))
        }
        val pack = ReviewedKnowledgePackJsonCodec.decode(mutated.toString())
        assertEquals("真实 loader 必须造出 4 条合成源", 4, pack.sources.size)
        val thrown = runCatching {
            KnowledgeBaseImportContract.validateSourcesOnly(pack.sources)
        }.exceptionOrNull()
        assertTrue(
            "顶层占位域必须被拒收，实际：$thrown",
            thrown is DatabaseContractViolationException,
        )
        assertTrue(
            "失败信息必须指名违约字段，实际：${thrown?.message}",
            thrown?.message.orEmpty().contains("source.sourceUri"),
        )
    }

    /**
     * §3.9 / §4.3：内容包全量扫描——0 个占位域；49 个来源的三分类计数 A=1 / B=4 / C=44。
     *
     * 分类判据与内容改写脚本一致（A 按精确 sourceId、B 按前缀、其余为 C），不按 title 猜。
     */
    @Test
    fun bundledSourcesCarryNoPlaceholderUriAndKeepTheThreeWaySplit() {
        val packs = BundledKnowledgePackResources.load()
        // 「49 源」指 sidecar 来源（教学材料卷）；主包合成源是另一条路径（见 P4 / N6）。
        val teaching = packs.flatMap { it.teachingSources }
        val ids = teaching.map { it.sourceId }.toSet()
        assertEquals("sidecar 应有 49 个唯一来源", 49, ids.size)
        val classes = teaching.distinctBy { it.sourceId }.groupingBy { source ->
            when {
                source.sourceId == "source:moe-2020:math:teaching" -> "A"
                source.sourceId.startsWith("registry-gap-gaokao-2025:") -> "B"
                else -> "C"
            }
        }.eachCount()
        assertEquals("A 类（真实可核 URI）", 1, classes["A"])
        assertEquals("B 类（来源不明占位）", 4, classes["B"])
        assertEquals("C 类（可定位但无公开可核 URI）", 44, classes["C"])

        // 全量扫描（sidecar + 合成源）走同一条契约；再补一条显式 host 检查。
        val all = packs.flatMap { it.sources + it.teachingSources }
        KnowledgeBaseImportContract.validateSourcesOnly(all)
        val placeholderHosts = setOf(
            "example.com", "example.net", "example.org", "example.edu", "localhost",
            "127.0.0.1", "0.0.0.0", "::1",
        )
        val offenders = all.filter { source ->
            listOfNotNull(source.sourceUri, source.licenseUri).any { uri ->
                val host = uri.removePrefix("https://").removePrefix("http://")
                    .substringBefore('/').substringBefore(':').lowercase()
                host in placeholderHosts || placeholderHosts.any { host.endsWith(".$it") }
            }
        }
        assertTrue("内置包仍带占位域：${offenders.map { it.sourceId }}", offenders.isEmpty())
    }

    /** 占位类只能靠三元组与 marker 兜底，校验器不应允许它携带 URI。 */
    @Test
    fun placeholderStatusSourcesInTheBundleCarryNoUri() {
        val packs = BundledKnowledgePackResources.load()
        val placeholders = packs.flatMap { it.teachingSources }
            .filter { it.licenseStatus == "UNVERIFIED_PLACEHOLDER" }
        assertEquals("B 类 4 条占位来源", 4, placeholders.size)
        placeholders.forEach { source ->
            assertNull(source.sourceUri)
            assertTrue(source.attributionText.orEmpty().contains("来源不明（占位）"))
        }
    }
}
