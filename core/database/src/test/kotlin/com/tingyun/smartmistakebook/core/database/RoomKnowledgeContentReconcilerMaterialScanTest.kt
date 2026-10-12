package com.tingyun.smartmistakebook.core.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * KD-32：材料分页扫描的三块纯逻辑（段划分 / 前缀上界 / 绑定组定位）。
 *
 * 消灭的失败：改前材料差分一次把整包 5 万行连同正文读回内存（`kb_peak3.hprof` 对比基线
 * ≈ +30.5 万个 char[]／+49MB），再叠加 `existingById` 与每材料一张的 `groupBy` 两个中转
 * 结构，把 192MiB 上限的堆顶到 191.8MB 后 OOM。分页后"哪些行会被读到"完全由这三块决定：
 * 段漏一段，库里在、包里没有的行会被静默留下（永不退役）；段重一段，同一行会被当两次
 * 退役候选；绑定组定位错，材料会被误判成"没有任何知识节点绑定"而整条跳过。
 *
 * 这三块不死在 Room 里（Room 死不了"段有没有接上"），所以在这里逐条钉住。
 */
class RoomKnowledgeContentReconcilerMaterialScanTest {

    @Test
    fun `segments tile the pack prefix so every page and every standing row is covered once`() {
        val prefix = "moe-test-v1:"
        val end = stableCodePrefixEnd(prefix)
        val pages = listOf(
            "moe-test-v1:math:teaching:keep-0000",
            "moe-test-v1:math:teaching:keep-0500",
            "moe-test-v1:math:teaching:keep-1000",
        )

        val segments = materialScanSegments(prefix, end, pages)

        assertEquals("头段 + 3 页 = 4 段", 4, segments.size)
        assertEquals(prefix, segments.first().fromStableCode)
        assertEquals(end, segments.last().throughStableCode)
        assertTrue(
            "每段都必须是非空区间",
            segments.all { it.fromStableCode < it.throughStableCode },
        )
        segments.zipWithNext().forEach { (left, right) ->
            assertEquals(
                "相邻段必须首尾相接（不重不漏）",
                left.throughStableCode,
                right.fromStableCode,
            )
        }
        assertEquals(listOf(null, 0, 1, 2), segments.map { it.pageIndex })

        pages.forEachIndexed { index, code ->
            val matched = segments.filter { it.fromStableCode <= code && code < it.throughStableCode }
            assertEquals("页内材料 $code 必须恰好落在一段里", 1, matched.size)
            assertEquals("页内材料必须落在自己那一页的段", index, matched.single().pageIndex)
        }

        // 包外但在前缀内的行：排序在第一条材料之前 → 头段（无页可处理，纯退役候选）
        assertEquals(
            "前缀头段只归集退役候选",
            null,
            segmentContaining(segments, "moe-test-v1:math:teaching:abandon-head").pageIndex,
        )
        // 排序在最后一条材料之后 → 与最后一页同段（尾段），同样要被读到
        assertEquals(
            "前缀尾段与最后一页一起处理",
            2,
            segmentContaining(segments, "moe-test-v1:math:teaching:zzz-gone").pageIndex,
        )

        // 前缀之外的行任何段都读不到：别的包/别的前缀不会被误退役
        listOf(
            "moe-test-v10:math:teaching:x",
            "moe-test-v1",
            "moe-test-v1;other-pack:math:teaching:x",
            "MOE-TEST-V1:math:teaching:x",
        ).forEach { code ->
            assertEquals(
                "$code 不在本包前缀内，任何段都不该读到它",
                0,
                segments.count { it.fromStableCode <= code && code < it.throughStableCode },
            )
        }
    }

    @Test
    fun `an empty pack still reads and retires its whole prefix`() {
        val prefix = "moe-test-v1:"
        val end = stableCodePrefixEnd(prefix)

        val segments = materialScanSegments(prefix, end, emptyList())

        assertEquals("没有材料页时只剩一个段", 1, segments.size)
        val only = segments.single()
        assertEquals(prefix, only.fromStableCode)
        assertEquals(end, only.throughStableCode)
        assertEquals("没有页要处理，但这个段仍会读回全部残留行", null, only.pageIndex)
    }

    @Test
    fun `the prefix upper bound is exclusive and byte exact`() {
        val prefix = "moe-2025-four-subjects-v1:"
        val end = stableCodePrefixEnd(prefix)

        assertEquals("':' 的后继字符", "moe-2025-four-subjects-v1;", end)
        listOf(
            prefix,
            "moe-2025-four-subjects-v1:math:teaching:x",
        ).forEach { code ->
            assertTrue("$code 应落在 [prefix, end) 内", code >= prefix && code < end)
        }
        listOf(
            "moe-2025-four-subjects-v1", // 少一个 ':'，排序在前缀之前
            "moe-2025-four-subjects-v10:x", // '0' < ':'，排序在前缀之前
            "moe-2025-four-subjects-v1;x", // ';' 已是上界之外
            "moe-2025-four-subjects-v2:x",
            "MOE-2025-four-subjects-v1:x", // 区间字节精确，不再像 LIKE 那样折叠 ASCII 大小写
        ).forEach { code ->
            assertTrue("$code 不应落在 [prefix, end) 内", !(code >= prefix && code < end))
        }

        assertThrows(IllegalArgumentException::class.java) { stableCodePrefixEnd("") }
        assertThrows(IllegalStateException::class.java) { stableCodePrefixEnd("\uFFFF") }
    }

    @Test
    fun `binding groups are located by material identity and keep their relative order`() {
        val sorted = listOf(
            binding("kb-material:a", "node:1"),
            binding("kb-material:a", "node:3"),
            binding("kb-material:b", "node:2"),
            binding("kb-material:c", "node:9"),
            binding("kb-material:c", "node:4"),
            binding("kb-material:c", "node:7"),
        )

        assertEquals(
            "首组（边界在最前）不漏",
            listOf("node:1", "node:3"),
            bindingsForMaterial(sorted, "kb-material:a").map { it.knowledgeNodeId },
        )
        assertEquals(
            "单条组",
            listOf("node:2"),
            bindingsForMaterial(sorted, "kb-material:b").map { it.knowledgeNodeId },
        )
        assertEquals(
            "末组沿升序保留原有相对顺序",
            listOf("node:9", "node:4", "node:7"),
            bindingsForMaterial(sorted, "kb-material:c").map { it.knowledgeNodeId },
        )
        assertEquals(
            "没有绑定的材料得到空组，不是别的材料的组",
            emptyList<KnowledgeTeachingMaterialNodeBindingRecord>(),
            bindingsForMaterial(sorted, "kb-material:missing"),
        )
        assertEquals(
            "空绑定表上也是空组",
            emptyList<KnowledgeTeachingMaterialNodeBindingRecord>(),
            bindingsForMaterial(emptyList(), "kb-material:a"),
        )
    }

    private fun segmentContaining(
        segments: List<MaterialScanSegment>,
        stableCode: String,
    ): MaterialScanSegment =
        segments.single { it.fromStableCode <= stableCode && stableCode < it.throughStableCode }

    private fun binding(materialId: String, knowledgeNodeId: String) =
        KnowledgeTeachingMaterialNodeBindingRecord(
            materialId = materialId,
            knowledgeNodeId = knowledgeNodeId,
            role = "PRIMARY",
        )
}
