package com.tingyun.smartmistakebook.core.database

import com.tingyun.smartmistakebook.core.model.BindingAuditSnapshot
import com.tingyun.smartmistakebook.core.model.BindingAuditSnapshotCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * KF-29 聚合口径的 JVM 钉死（不依赖真库：聚合是纯函数）。
 *
 * 消灭的失败：口径散落在报告/复核屏两处、AMBIGUOUS 被当成"对"稀释错误率、快照坏掉的行
 * 从分母里静默蒸发。三条各自有用例。
 */
class BindingAuditAggregatorTest {

    @Test
    fun `error rate is wrong over correct plus wrong and ambiguous stays visible`() {
        val aggregation = BindingAuditAggregator.aggregate(
            listOf(
                reviewed("s1", "MATH", "model-a", BINDING_AUDIT_VERDICT_CORRECT),
                reviewed("s2", "MATH", "model-a", BINDING_AUDIT_VERDICT_WRONG),
                reviewed("s3", "MATH", "model-a", BINDING_AUDIT_VERDICT_AMBIGUOUS),
                // PENDING 不参与任何分组
                pending("s4", "MATH", "model-a"),
            ),
        )

        assertEquals(1, aggregation.groups.size)
        val math = aggregation.groups.single()
        assertEquals("MATH", math.subject)
        assertEquals("model-a", math.modelVersion)
        assertEquals(3, math.reviewedCount)
        assertEquals(1, math.correctCount)
        assertEquals(1, math.wrongCount)
        assertEquals(1, math.ambiguousCount)
        assertEquals(
            "错误率 = WRONG / (CORRECT + WRONG)：AMBIGUOUS 不进分子也不进分母",
            0.5,
            math.errorRate!!,
            0.0,
        )
        assertEquals(0, aggregation.unreadableReviewedCount)
    }

    @Test
    fun `groups are split by subject and model version`() {
        val aggregation = BindingAuditAggregator.aggregate(
            listOf(
                reviewed("s1", "MATH", "model-a", BINDING_AUDIT_VERDICT_WRONG),
                reviewed("s2", "MATH", "model-b", BINDING_AUDIT_VERDICT_CORRECT),
                reviewed("s3", "PHYSICS", null, BINDING_AUDIT_VERDICT_WRONG),
                reviewed("s4", "PHYSICS", null, BINDING_AUDIT_VERDICT_WRONG),
            ),
        )

        assertEquals(
            listOf("MATH" to "model-a", "MATH" to "model-b", "PHYSICS" to "UNSPECIFIED"),
            aggregation.groups.map { it.subject to it.modelVersion },
        )
        assertEquals(1.0, aggregation.groups[0].errorRate!!, 0.0)
        assertEquals(0.0, aggregation.groups[1].errorRate!!, 0.0)
        assertEquals(
            "离线用户纠正没有模型版本 → UNSPECIFIED 组（不许丢掉这些行）",
            1.0,
            aggregation.groups[2].errorRate!!,
            0.0,
        )
    }

    @Test
    fun `reviewed rows without a readable snapshot or verdict are counted not dropped`() {
        val aggregation = BindingAuditAggregator.aggregate(
            listOf(
                reviewed("s1", "MATH", "model-a", BINDING_AUDIT_VERDICT_CORRECT),
                BindingAuditSampleRow(
                    sampleId = "broken",
                    practiceUnitId = "unit",
                    bindingSnapshotJson = "{not json",
                    status = BINDING_AUDIT_STATUS_REVIEWED,
                    verdict = BINDING_AUDIT_VERDICT_WRONG,
                    reviewedAtEpochMillis = 2,
                ),
                BindingAuditSampleRow(
                    sampleId = "no-verdict",
                    practiceUnitId = "unit",
                    bindingSnapshotJson = snapshotJson("MATH", "model-a"),
                    status = BINDING_AUDIT_STATUS_REVIEWED,
                    verdict = null,
                    reviewedAtEpochMillis = 2,
                ),
            ),
        )

        assertEquals(1, aggregation.groups.size)
        assertEquals(1, aggregation.groups.single().reviewedCount)
        assertEquals(
            "读不出来的已复核行必须如实计数，不许从错误率里静默消失",
            2,
            aggregation.unreadableReviewedCount,
        )
    }

    @Test
    fun `an empty denominator reports no rate instead of a fabricated zero`() {
        val aggregation = BindingAuditAggregator.aggregate(
            listOf(reviewed("s1", "MATH", "model-a", BINDING_AUDIT_VERDICT_AMBIGUOUS)),
        )

        assertNull(
            "只有 AMBIGUOUS 时没有可判样本：错误率必须是 null（报告写'—'），不是 0",
            aggregation.groups.single().errorRate,
        )
        assertEquals(0, BindingAuditAggregator.aggregate(emptyList()).groups.size)
    }

    private fun reviewed(
        sampleId: String,
        subject: String,
        modelVersion: String?,
        verdict: String,
    ) = BindingAuditSampleRow(
        sampleId = sampleId,
        practiceUnitId = "unit-$sampleId",
        bindingSnapshotJson = snapshotJson(subject, modelVersion),
        status = BINDING_AUDIT_STATUS_REVIEWED,
        verdict = verdict,
        reviewedAtEpochMillis = 2,
    )

    private fun pending(sampleId: String, subject: String, modelVersion: String?) =
        BindingAuditSampleRow(
            sampleId = sampleId,
            practiceUnitId = "unit-$sampleId",
            bindingSnapshotJson = snapshotJson(subject, modelVersion),
            status = BINDING_AUDIT_STATUS_PENDING,
            verdict = null,
            reviewedAtEpochMillis = null,
        )

    private fun snapshotJson(subject: String, modelVersion: String?): String =
        BindingAuditSnapshotCodec.encode(
            BindingAuditSnapshot(
                subject = subject,
                modelVersion = modelVersion,
                acceptanceSource = "LOCAL_POLICY_ACCEPTED",
                acceptedAtEpochMillis = 1,
                problemId = "problem",
                problemRevisionId = "revision",
                practiceUnitId = "unit",
                questionMarkdown = "题面",
            ),
        )
}
