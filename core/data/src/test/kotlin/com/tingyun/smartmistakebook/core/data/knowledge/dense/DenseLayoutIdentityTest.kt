package com.tingyun.smartmistakebook.core.data.knowledge.dense

import java.security.MessageDigest
import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * `.vec` 布局的**漂移门**（JVM 侧，构建期红，不进运行期）。
 *
 * 它盯的失败很具体：`ids` 只证明"向量行属于哪些节点"，不证明"这些行对应的文本是什么"。
 * canonical name 或**等量**别名文本被改、而 ids 与行数都不变时，`packSha256` 可以被
 * "改包 + 重钉哈希"绕过——现有 10 项 dense 门全绿，端侧会拿"旧文本的向量"去比"新词条的文本"。
 * 本门把 `(node_id, kind, surface)` 的**有序**序列做成摘要，与旁车 `corpus.layoutSha256` 对账。
 *
 * 序列化口径（与 `tools/dense_build/dense_asset.py:layout_sha256` 逐字节相同）：
 * 每行 `node_id + '\t' + kind + '\t' + surface + '\n'`，UTF-8，SHA-256 小写十六进制。
 *
 * 已知边界：当前包有 6 条别名含换行符，该编码不是无歧义编码；本门只声称漂移检测，
 * 不声称抗构造碰撞（预注册 §1.4）。
 *
 * 覆盖边界：本测试不覆盖运行期装配（[DenseRecallAssembly] 不解析 30MB 主包、不做布局复核），
 * 也不替代 [DenseAssetProvenanceTest] 的三处身份门。
 */
class DenseLayoutIdentityTest {

    private val pack: JsonObject by lazy {
        Json.parseToJsonElement(DenseTestFixtures.text(PACK_PATH)).jsonObject
    }
    private val sidecar: JsonObject by lazy {
        // 资源路径取自生产常量，常量换路径时本测试跟着换。
        Json.parseToJsonElement(DenseTestFixtures.text(DenseRecallAssembly.VECTOR_ASSET_PATH + ".json")).jsonObject
    }
    private val assetBytes: ByteArray by lazy { DenseTestFixtures.bytes(DenseRecallAssembly.VECTOR_ASSET_PATH) }

    private data class LayoutRow(val nodeId: String, val kind: String, val surface: String)

    // ------------------------------------------------------------------
    // 正例：两侧（Python 基线 / 旁车 / .vec 行序）逐条一致
    // ------------------------------------------------------------------

    @Test
    fun `layout sha256 matches sidecar pack and vector row order`() {
        val rows = layoutRows(pack)

        // 1) 行集规模（预注册 §1.4 冻结读数）
        assertEquals("布局行数与以包为准的冻结读数不符", 44_121, rows.size)
        assertEquals("canonical 行数不符", 3_280, rows.count { it.kind == "canonical" })
        assertEquals("alias 行数不符", 40_841, rows.count { it.kind == "alias" })

        // 2) 基线摘要与 Python 权威实现逐字符相同（跨语言钉住序列化）
        val computed = layoutSha256(rows)
        assertEquals(
            "Kotlin 计算的布局摘要与 Python 侧基线不一致（序列化实现漂移或主包已变）",
            BASELINE_LAYOUT_SHA256, computed,
        )

        // 3) 旁车记录的布局摘要 == 当前包布局
        assertEquals("corpus.layoutSha256 与当前包布局不一致", computed, recordedLayoutSha256())

        // 4) 资产能按生产常量读入，行数/节点数自洽
        val asset = DenseVectorAsset.read(assetBytes, expectedSha256 = DenseRecallAssembly.VECTOR_ASSET_SHA256)
        assertEquals("资产行数与包布局行数不一致", rows.size, asset.count)
        assertEquals("资产节点数与包布局唯一 node_id 数不一致", rows.map { it.nodeId }.distinct().size, asset.nodeCount)

        // 5) 逐行、逐序：`.vec` 的 ids 块与包布局同序（失败信息指到首个差异）
        assertRowsInOrder(rows, asset.rowNodeIds())

        // 6) 旁车自身账目（与 provenance 门重叠也无妨：这是"布局门自洽"的一部分）
        assertEquals("旁车 format.count 与布局行数不一致", rows.size, sidecar.getValue("format").jsonObject.intField("count"))
        assertEquals("旁车 corpus.vectorCount 与布局行数不一致", rows.size, sidecar.getValue("corpus").jsonObject.intField("vectorCount"))
    }

    // ------------------------------------------------------------------
    // 负例（N1/N2/N3）：改解析后的 JsonObject，再走同一条 解析 → 行构造 → 摘要 链
    // ------------------------------------------------------------------

    @Test
    fun `canonical name change with stable ids is red`() {
        val mutated = mutateFirstNonEmptyPoint(pack) { point ->
            JsonObject(point.toMutableMap().apply { put("name", JsonPrimitive("函数的概念（改）")) })
        }
        assertOnlyLayoutChanged(mutated, "28fc9985374da6cbde5944d1c856a07e9e90d543cc2d92d8020e51850c4c9296")
    }

    @Test
    fun `equal count alias replacement is red`() {
        val mutated = mutateFirstNonEmptyPoint(pack) { point ->
            val aliases = point.aliases()
            val replaced = aliases.toMutableList().also { it[0] = "等量替换别名-函数的概念" }
            assertEquals("N2 前提：别名条数必须不变", aliases.size, replaced.size)
            point.withAliases(replaced)
        }
        assertOnlyLayoutChanged(mutated, "235faec73c011bc7fec8c3154a23b33763b0c6d9aee25463942fb49f8eea6494")
    }

    @Test
    fun `alias order swap is red`() {
        val mutated = mutateFirstNonEmptyPoint(pack) { point ->
            val aliases = point.aliases()
            require(aliases.size >= 2) { "N3 前提：该点至少两条别名" }
            val swapped = aliases.toMutableList().also { it[0] = aliases[1]; it[1] = aliases[0] }
            point.withAliases(swapped)
        }
        assertOnlyLayoutChanged(mutated, "ca519f35e78b6c96a8c9dd9e9eae0845bb10e5e0e2dbd052b796510bbe774d3d")
    }

    /**
     * 负例共同断言：ids 序列不变（证明"现有 ids 门看不见它"）、摘要命中冻结值、
     * 门函数对改后包抛错且失败信息含 `corpus.layoutSha256`。
     */
    private fun assertOnlyLayoutChanged(mutated: JsonObject, expectedSha256: String) {
        val baselineRows = layoutRows(pack)
        val mutatedRows = layoutRows(mutated)
        assertEquals("负例前提：ids 序列必须与基线完全相同", baselineRows.map { it.nodeId }, mutatedRows.map { it.nodeId })
        assertEquals("负例前提：行数必须与基线完全相同", baselineRows.size, mutatedRows.size)
        assertEquals("负例的布局摘要与预注册冻结值不符（跨语言序列化漂移或主包已变）", expectedSha256, layoutSha256(mutatedRows))
        val failure = assertThrows(AssertionError::class.java) { verifyLayout(mutated, sidecar) }
        val message = failure.message.orEmpty()
        if (!message.contains("corpus.layoutSha256")) {
            throw AssertionError("失败信息必须点出 corpus.layoutSha256，实际：$message")
        }
    }

    // ------------------------------------------------------------------
    // 第二读实现（Python 权威、Kotlin 独立实现，与 DenseVectorAsset 对 dense_asset.py 的关系相同）
    // ------------------------------------------------------------------

    private fun layoutRows(pack: JsonObject): List<LayoutRow> {
        val taxonomy = pack.string("taxonomyVersion")
        val rows = ArrayList<LayoutRow>()
        for (subject in pack.array("subjects")) {
            val subjectKey = subject.obj.string("subject").lowercase(Locale.ROOT)
            for (topic in subject.obj.array("topics")) {
                for (point in topic.obj.array("knowledgePoints")) {
                    val nodeId = "kb:$taxonomy:$subjectKey:atomic:${point.obj.string("slug")}"
                    rows += LayoutRow(nodeId, "canonical", point.obj.string("name"))
                    for (alias in point.obj.aliases().distinct()) rows += LayoutRow(nodeId, "alias", alias)
                }
            }
        }
        return rows
    }

    private fun layoutSha256(rows: List<LayoutRow>): String {
        val payload = buildString {
            for (row in rows) {
                append(row.nodeId).append('\t').append(row.kind).append('\t')
                    .append(row.surface).append('\n')
            }
        }
        return MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }

    /** 门函数：布局摘要与旁车记录必须相等，否则抛 [AssertionError]。 */
    private fun verifyLayout(pack: JsonObject, sidecar: JsonObject) {
        val rows = layoutRows(pack)
        val recorded = sidecar.getValue("corpus").jsonObject["layoutSha256"]!!.jsonPrimitive.content
        assertEquals("corpus.layoutSha256 与当前包布局不一致", layoutSha256(rows), recorded)
    }

    private fun assertRowsInOrder(rows: List<LayoutRow>, assetRowIds: List<String>) {
        assertEquals("资产逐行 node_id 长度与布局不一致", rows.size, assetRowIds.size)
        for (index in rows.indices) {
            if (rows[index].nodeId != assetRowIds[index]) {
                throw AssertionError(
                    "资产行序与包布局不一致：首个差异下标 $index（包 ${rows[index].nodeId} vs 资产 ${assetRowIds[index]}）",
                )
            }
        }
    }

    private fun recordedLayoutSha256(): String =
        sidecar.getValue("corpus").jsonObject["layoutSha256"]?.jsonPrimitive?.contentOrNull
            ?: error("旁车缺 corpus.layoutSha256（先跑 tools/dense_build/pack_dense_asset.py）")

    /**
     * 把**首个非空知识点**替换为 [mutate] 的返回值；其余对象原样保留。
     * 包里有空 topic（0 个知识点），因此不能写死 `topics[0].knowledgePoints[0]`。
     */
    private fun mutateFirstNonEmptyPoint(pack: JsonObject, mutate: (JsonObject) -> JsonObject): JsonObject {
        val subjects = pack.array("subjects")
        var done = false
        val newSubjects = ArrayList<JsonElement>(subjects.size)
        for (subjectElement in subjects) {
            val subject = subjectElement.obj
            if (done) {
                newSubjects += subject
                continue
            }
            val topics = subject.array("topics")
            var subjectDone = false
            val newTopics = ArrayList<JsonElement>(topics.size)
            for (topicElement in topics) {
                val topic = topicElement.obj
                if (subjectDone) {
                    newTopics += topic
                    continue
                }
                val points = topic.array("knowledgePoints")
                if (points.isEmpty()) {
                    newTopics += topic
                    continue
                }
                val newPoints = ArrayList<JsonElement>(points.size)
                var first = true
                for (pointElement in points) {
                    newPoints += if (first) {
                        first = false
                        mutate(pointElement.obj)
                    } else {
                        pointElement
                    }
                }
                newTopics += JsonObject(topic.toMutableMap().apply { put("knowledgePoints", JsonArray(newPoints)) })
                subjectDone = true
                done = true
            }
            newSubjects += JsonObject(subject.toMutableMap().apply { put("topics", JsonArray(newTopics)) })
        }
        check(done) { "包里找不到任何非空 topic——测试夹具失效" }
        return JsonObject(pack.toMutableMap().apply { put("subjects", JsonArray(newSubjects)) })
    }

    private fun JsonObject.aliases(): List<String> =
        (this["aliases"] as? JsonArray)?.map { it.jsonPrimitive.content } ?: emptyList()

    private fun JsonObject.withAliases(aliases: List<String>): JsonObject =
        JsonObject(toMutableMap().apply { put("aliases", JsonArray(aliases.map { JsonPrimitive(it) })) })

    private fun JsonObject.string(name: String): String =
        (this[name] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotEmpty() }
            ?: error("包缺字符串字段 $name（或在 $name 为空）")

    private fun JsonObject.intField(name: String): Int =
        (this[name] as? JsonPrimitive)?.int ?: error("缺整数字段 $name")

    private fun JsonObject.array(name: String): List<JsonElement> =
        (this[name] as? JsonArray) ?: error("包缺数组字段 $name")

    private val JsonElement.obj: JsonObject
        get() = jsonObject

    private companion object {
        /** 主包资源路径（与 `tools/dense_build/dense_asset.py` 的 `PACK_RELATIVE` 指向同一文件）。 */
        const val PACK_PATH = "knowledge/moe-2025-four-subjects-v1.json"

        /** 预注册 §1.4 冻结基线（Python 侧实算，两端必须逐字符相同）。 */
        const val BASELINE_LAYOUT_SHA256 = "1aa8142e381d1b2b40b2ba3d5eec2783e5781830321613fcb78fff7f61e9d75c"
    }
}
