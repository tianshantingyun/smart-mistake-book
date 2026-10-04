package com.tingyun.smartmistakebook.core.model

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * KF-29 抽样口径的 JVM 钉死：周键（UTC ISO 周）与内容寻址样本 id。
 *
 * 这些是纯函数——迁移/入队/聚合都依赖同一口径，这里先把它钉住（真库行为由
 * `BindingAuditSampleInstrumentedTest` 与 `FullMigrationMatrixInstrumentedTest` 钉）。
 */
class BindingAuditSamplingTest {

    @Test
    fun `quota prefix is the subject plus the utc iso week`() {
        assertEquals(
            "binding-audit:MATH:2026-W01",
            BindingAuditSampling.quotaPrefix("MATH", utcMillis("2026-01-01")),
        )
        assertEquals(
            "binding-audit:PHYSICS:2026-W40",
            BindingAuditSampling.quotaPrefix("PHYSICS", utcMillis("2026-10-04")),
        )
    }

    /**
     * ISO 周边界在 UTC 的周日→周一之间：2025-12-29（周一）已经属于 2026-W01
     * （周所在年由周四决定），2026-10-04 23:59:59Z 仍是 W40、2026-10-05 00:00Z 进入 W41。
     * 这条用例消灭的失败：把周窗口算成"日历周"或本地时区，配额会在跨年/跨周时漂移。
     */
    @Test
    fun `quota prefix follows the iso week boundary in utc`() {
        assertEquals(
            "binding-audit:MATH:2026-W01",
            BindingAuditSampling.quotaPrefix("MATH", utcMillis("2025-12-29")),
        )
        assertEquals(
            "binding-audit:MATH:2026-W40",
            BindingAuditSampling.quotaPrefix("MATH", utcMillis("2026-10-04", LocalTime.of(23, 59, 59))),
        )
        assertEquals(
            "binding-audit:MATH:2026-W41",
            BindingAuditSampling.quotaPrefix("MATH", utcMillis("2026-10-05")),
        )
    }

    @Test
    fun `sample id is content addressed and keeps its quota prefix`() {
        val quota = BindingAuditSampling.quotaPrefix("MATH", utcMillis("2026-10-04"))
        val first = BindingAuditSampling.sampleId(
            quotaPrefix = quota,
            practiceUnitId = "practice-1",
            problemRevisionId = "revision-1",
            bindingIds = listOf("binding-b", "binding-a"),
        )
        val replay = BindingAuditSampling.sampleId(
            quotaPrefix = quota,
            practiceUnitId = "practice-1",
            problemRevisionId = "revision-1",
            bindingIds = listOf("binding-a", "binding-b"),
        )
        assertEquals("集合顺序不改变内容寻址结果（同一确认重放必须是同一个样本）", first, replay)
        assertTrue("样本 id 必须保留配额前缀（配额计数按它做前缀匹配）", first.startsWith("$quota:"))
        assertNotEquals(
            "绑定集合变了就是另一次新绑定",
            first,
            BindingAuditSampling.sampleId(
                quotaPrefix = quota,
                practiceUnitId = "practice-1",
                problemRevisionId = "revision-1",
                bindingIds = listOf("binding-a", "binding-c"),
            ),
        )
        assertNotEquals(
            "同一绑定集合换一周是另一条样本（配额窗口独立）",
            first,
            BindingAuditSampling.sampleId(
                quotaPrefix = BindingAuditSampling.quotaPrefix("MATH", utcMillis("2026-10-05")),
                practiceUnitId = "practice-1",
                problemRevisionId = "revision-1",
                bindingIds = listOf("binding-a", "binding-b"),
            ),
        )
    }

    @Test
    fun `sample id requires at least one binding and a namespace prefixed quota`() {
        val quota = BindingAuditSampling.quotaPrefix("MATH", utcMillis("2026-10-04"))
        assertTrue(
            "空绑定集合不是一次绑定，拒绝",
            runCatching {
                BindingAuditSampling.sampleId(quota, "practice-1", "revision-1", emptyList())
            }.isFailure,
        )
        assertTrue(
            "配额前缀不合法（会数错配额）必须拒绝",
            runCatching {
                BindingAuditSampling.sampleId("not-a-quota", "practice-1", "revision-1", listOf("b"))
            }.isFailure,
        )
    }

    @Test
    fun `snapshot codec round trips every field and rejects unknown or oversized payloads`() {
        val snapshot = BindingAuditSnapshot(
            subject = "MATH",
            modelVersion = "model-x-1",
            providerId = "provider:test",
            acceptanceSource = "LOCAL_POLICY_ACCEPTED",
            acceptedAtEpochMillis = utcMillis("2026-10-04"),
            problemId = "problem-1",
            problemRevisionId = "revision-1",
            practiceUnitId = "practice-1",
            questionMarkdown = "题面",
            bindings = listOf(
                BindingAuditSnapshotBinding(
                    knowledgeNodeId = "knowledge:1",
                    displayName = "二次函数",
                    granularity = "ATOMIC",
                    strength = 0.75,
                    taxonomyVersion = "organization-v1",
                ),
            ),
            classifications = listOf(
                BindingAuditSnapshotClassification(
                    dimension = "KNOWLEDGE",
                    labelId = "math:knowledge:abc",
                    displayName = "二次函数",
                    rationaleMarkdown = "由对称性可得",
                ),
            ),
            stepEvidence = listOf(
                BindingAuditSnapshotStep(
                    stepOrdinal = 1,
                    stepSummaryMarkdown = "配方",
                    knowledgeNodeIds = listOf("knowledge:1"),
                ),
            ),
        )
        val decoded = BindingAuditSnapshotCodec.decode(BindingAuditSnapshotCodec.encode(snapshot))
        assertEquals(snapshot, decoded)

        assertNull(
            "版本不认识的快照必须解码失败（聚合计入不可读，不许当成当前格式）",
            BindingAuditSnapshotCodec.decode(
                """{"schemaVersion":99,"subject":"MATH","acceptanceSource":"LOCAL_POLICY_ACCEPTED",""" +
                    """"acceptedAtEpochMillis":1,"problemId":"p","problemRevisionId":"r","practiceUnitId":"u",""" +
                    """"questionMarkdown":""}""",
            ),
        )
        assertNull(
            "超过预算的载荷拒绝",
            BindingAuditSnapshotCodec.decode("x".repeat(BindingAuditSnapshotCodec.MAX_ENCODED_CHARS + 1)),
        )
        assertNull("坏 JSON 拒绝", BindingAuditSnapshotCodec.decode("{not json"))

        // F4 修复轮：encode 对超预算载荷必须抛（写入方据此走 best-effort 吞掉，不落半条样本）。
        assertTrue(
            "超预算快照必须拒绝编码",
            runCatching {
                BindingAuditSnapshotCodec.encode(
                    snapshot.copy(
                        questionMarkdown = "x".repeat(BindingAuditSnapshotCodec.MAX_ENCODED_CHARS + 1),
                    ),
                )
            }.isFailure,
        )
    }

    private fun utcMillis(date: String, time: LocalTime = LocalTime.MIDNIGHT): Long =
        LocalDate.parse(date).atTime(time).toInstant(ZoneOffset.UTC).toEpochMilli()
}
