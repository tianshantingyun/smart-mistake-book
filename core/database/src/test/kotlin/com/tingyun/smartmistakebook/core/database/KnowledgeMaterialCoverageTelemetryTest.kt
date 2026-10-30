package com.tingyun.smartmistakebook.core.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * M1a 材料覆盖率那行日志的 JVM 钉：字段齐全、计数口径正确、`emptyNodeIds` 有序且截断、
 * 捕获实现出错不改变产品结果。
 *
 * 分工（如实说明，避免把局部当全量）：这里钉的是**取数返回后**的产出契约
 * （[KnowledgeMaterialCoverageTelemetry.record] 经注入的 sink 捕获到的行）。真正的
 * `RoomKnowledgeBaseStore` 取数路径要 Room，只能由仪器化用例覆盖；本测试证明的是
 * "给定这次查询的事实，产出的行就是这一行"，不是"store 一定调用了它"。
 */
class KnowledgeMaterialCoverageTelemetryTest {

    @Test
    fun `one material fetch emits one structured coverage line`() {
        val captured = capture {
            KnowledgeMaterialCoverageTelemetry.record(
                subject = "math",
                queriedNodeIds = setOf("kc-a", "kc-b", "kc-c"),
                nodeIdsWithMaterial = setOf("kc-c", "kc-a"),
                returnedMaterials = 2,
            )
        }
        assertEquals(
            "v=1 metric=material_coverage subject=math queriedNodes=3 returnedMaterials=2 " +
                "nodesWithMaterial=2 nodesWithoutMaterial=1 emptyNodeIds=[kc-b]",
            captured.single(),
        )
    }

    @Test
    fun `a fetch that returns nothing still emits the full denominator`() {
        val captured = capture {
            KnowledgeMaterialCoverageTelemetry.record(
                subject = "physics",
                queriedNodeIds = setOf("kc-c", "kc-a", "kc-b"),
                nodeIdsWithMaterial = emptySet(),
                returnedMaterials = 0,
            )
        }
        assertEquals(
            "v=1 metric=material_coverage subject=physics queriedNodes=3 returnedMaterials=0 " +
                "nodesWithMaterial=0 nodesWithoutMaterial=3 emptyNodeIds=[kc-a, kc-b, kc-c]",
            captured.single(),
        )
    }

    @Test
    fun `empty node ids are sorted and capped at ten while the count stays complete`() {
        val queried = (1..12).map { "kc-$it" }.toSet()
        val captured = capture {
            KnowledgeMaterialCoverageTelemetry.record(
                subject = "math",
                queriedNodeIds = queried,
                nodeIdsWithMaterial = emptySet(),
                returnedMaterials = 0,
            )
        }
        val line = captured.single()
        assertTrue(line.contains(" nodesWithoutMaterial=12 "))
        assertTrue(
            "只留前 10 个，且按 id 排序（kc-1, kc-10, kc-11, kc-12, kc-2 ...）",
            line.endsWith(
                "emptyNodeIds=[kc-1, kc-10, kc-11, kc-12, kc-2, kc-3, kc-4, kc-5, kc-6, kc-7]",
            ),
        )
    }

    @Test
    fun `requested node ids the query did not return are the only ones counted`() {
        val captured = capture {
            KnowledgeMaterialCoverageTelemetry.record(
                subject = "math",
                queriedNodeIds = setOf("kc-a"),
                // 调用方多传的 id 不是本次请求的节点，不得把分子抬到分母之外。
                nodeIdsWithMaterial = setOf("kc-a", "kc-not-queried"),
                returnedMaterials = 2,
            )
        }
        assertEquals(
            "v=1 metric=material_coverage subject=math queriedNodes=1 returnedMaterials=2 " +
                "nodesWithMaterial=1 nodesWithoutMaterial=0 emptyNodeIds=[]",
            captured.single(),
        )
    }

    @Test
    fun `a throwing capture does not escape into the read path`() {
        val previous = KnowledgeMaterialCoverageTelemetry.sink
        KnowledgeMaterialCoverageTelemetry.sink = { throw IllegalStateException("capture failed") }
        try {
            KnowledgeMaterialCoverageTelemetry.record(
                subject = "math",
                queriedNodeIds = setOf("kc-a"),
                nodeIdsWithMaterial = setOf("kc-a"),
                returnedMaterials = 1,
            )
        } catch (unexpected: Throwable) {
            fail("遥测是旁路，捕获实现出错不得冒泡：$unexpected")
        } finally {
            KnowledgeMaterialCoverageTelemetry.sink = previous
        }
    }

    private fun capture(emit: () -> Unit): List<String> {
        val lines = mutableListOf<String>()
        val previous = KnowledgeMaterialCoverageTelemetry.sink
        KnowledgeMaterialCoverageTelemetry.sink = { lines += it }
        try {
            emit()
        } finally {
            KnowledgeMaterialCoverageTelemetry.sink = previous
        }
        return lines
    }
}
