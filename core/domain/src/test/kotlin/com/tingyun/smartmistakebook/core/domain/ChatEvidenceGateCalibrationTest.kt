package com.tingyun.smartmistakebook.core.domain

import com.tingyun.smartmistakebook.core.domain.ChatEvidenceGateCalibration.GateObservation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatEvidenceGateCalibrationTest {

    @Test
    fun `below the sample floor no suggestions are emitted`() {
        // 5 accepted + 20 rejected = 25 total observations, below the 30 floor.
        val report = ChatEvidenceGateCalibration.calibrate(
            GateObservation(
                acceptedCount = 5,
                rejectedByReason = mapOf("SAME_KC_IN_COOLDOWN" to 20),
                acceptedPerHour = mapOf(1000L to 5),
            ),
        )
        assertFalse(report.hasSufficientObservations)
        assertTrue("expected no suggestions below floor, got ${report.suggestions}", report.suggestions.isEmpty())
    }

    @Test
    fun `window pressure at or above threshold suggests loosening the quota`() {
        // 90 accepted in the peak hour against a 100 cap = 0.9 pressure.
        val report = ChatEvidenceGateCalibration.calibrate(
            GateObservation(
                acceptedCount = 90,
                rejectedByReason = emptyMap(),
                acceptedPerHour = mapOf(1000L to 90, 999L to 5),
            ),
        )
        assertTrue(report.hasSufficientObservations)
        assertEquals(0.9, report.windowPeakPressure, 1e-9)
        assertTrue(
            "expected window-pressure suggestion, got ${report.suggestions}",
            report.suggestions.any { it.contains("窗口配额压力") },
        )
    }

    @Test
    fun `low pressure produces no quota suggestion`() {
        val report = ChatEvidenceGateCalibration.calibrate(
            GateObservation(
                acceptedCount = ChatEvidenceGateCalibration.MIN_OBSERVATIONS.toLong().toInt(),
                rejectedByReason = emptyMap(),
                acceptedPerHour = mapOf(1000L to 20),
            ),
        )
        assertTrue(report.suggestions.isEmpty())
    }

    @Test
    fun `throttling rejections dominate suggests reviewing cooldown and quotas`() {
        // accepted 35 + SAME_KC 20 + WINDOW 15 = 70 total; WINDOW share
        // 15/70 ≈ 21% crosses the 20% suggestion threshold, SAME_KC 29%.
        val report = ChatEvidenceGateCalibration.calibrate(
            GateObservation(
                acceptedCount = 35,
                rejectedByReason = mapOf(
                    "SAME_KC_IN_COOLDOWN" to 20,
                    "LEARNER_WINDOW_QUOTA_EXHAUSTED" to 15,
                ),
                acceptedPerHour = mapOf(1000L to 35),
            ),
        )
        assertTrue(
            report.suggestions.any { it.contains("SAME_KC_IN_COOLDOWN") && it.contains("节流") },
        )
        assertTrue(
            report.suggestions.any { it.contains("LEARNER_WINDOW_QUOTA_EXHAUSTED") },
        )
    }

    @Test
    fun `quality rejections dominate suggests checking model behavior`() {
        val report = ChatEvidenceGateCalibration.calibrate(
            GateObservation(
                acceptedCount = 20,
                rejectedByReason = mapOf("KNOWLEDGE_NODE_NOT_ANCHORED" to 40),
                acceptedPerHour = emptyMap(),
            ),
        )
        assertTrue(
            report.suggestions.any { it.contains("KNOWLEDGE_NODE_NOT_ANCHORED") && it.contains("质量") },
        )
    }

    @Test
    fun `rejection shares are computed over all observations`() {
        val report = ChatEvidenceGateCalibration.calibrate(
            GateObservation(
                acceptedCount = 60,
                rejectedByReason = mapOf("ATTENTION_BELOW_FLOOR" to 40),
                acceptedPerHour = emptyMap(),
            ),
        )
        assertEquals(0.4, report.rejectionShares.getValue("ATTENTION_BELOW_FLOOR"), 1e-9)
        assertEquals(100, report.totalObservations)
    }

    @Test
    fun `the channel dimension is reported per source kind when observations carry it`() {
        // AUD-11 ①：收窄冷却前后都要读得出"被挡掉的是哪一路"。
        val report = ChatEvidenceGateCalibration.calibrate(
            GateObservation(
                acceptedCount = 60,
                rejectedByReason = mapOf(
                    "SAME_KC_IN_COOLDOWN" to 25,
                    "KNOWLEDGE_NODE_NOT_ANCHORED" to 15,
                ),
                acceptedPerHour = mapOf(1000L to 40, 1001L to 20),
                rejectedBySourceKindAndReason = mapOf(
                    "MODEL_CHAT" to mapOf("SAME_KC_IN_COOLDOWN" to 25),
                    "KNOWLEDGE_QUIZ" to mapOf("KNOWLEDGE_NODE_NOT_ANCHORED" to 15),
                ),
                acceptedPerSourceKindPerHour = mapOf(
                    "MODEL_CHAT" to mapOf(1000L to 30, 1001L to 5),
                    "KNOWLEDGE_QUIZ" to mapOf(1000L to 10, 1001L to 15),
                ),
            ),
        )

        assertEquals(
            "占比用同一分母（全部观测），两路并列可比",
            mapOf(
                "MODEL_CHAT" to mapOf("SAME_KC_IN_COOLDOWN" to 0.25),
                "KNOWLEDGE_QUIZ" to mapOf("KNOWLEDGE_NODE_NOT_ANCHORED" to 0.15),
            ),
            report.rejectionSharesBySourceKind,
        )
        assertEquals(
            "各通道自己的单小时峰值（洪泛面读数，不是配额压力）",
            mapOf("MODEL_CHAT" to 30, "KNOWLEDGE_QUIZ" to 15),
            report.peakAcceptedBySourceKind,
        )
        assertEquals(
            "总口径不变：拒因合计仍是同一批事实",
            40,
            report.rejectionShares.values.sumOf { (it * 100).toInt() },
        )
    }

    @Test
    fun `a throttling suggestion names the channel that was blocked`() {
        val report = ChatEvidenceGateCalibration.calibrate(
            GateObservation(
                acceptedCount = 10,
                rejectedByReason = mapOf("SAME_KC_IN_COOLDOWN" to 30),
                acceptedPerHour = emptyMap(),
                rejectedBySourceKindAndReason = mapOf(
                    "MODEL_CHAT" to mapOf("SAME_KC_IN_COOLDOWN" to 28),
                    "KNOWLEDGE_QUIZ" to mapOf("SAME_KC_IN_COOLDOWN" to 2),
                ),
            ),
        )

        val suggestion = report.suggestions.single { it.contains("SAME_KC_IN_COOLDOWN") }
        assertTrue(
            "建议要回答'被挡掉的是哪一路'，实际：$suggestion",
            // 通道名按字典序（KNOWLEDGE_QUIZ < MODEL_CHAT）：顺序稳定、与 Map 迭代序无关。
            suggestion.contains("来路：KNOWLEDGE_QUIZ 2 / MODEL_CHAT 28"),
        )
    }

    @Test
    fun `without the channel dimension the report and its wording stay exactly as before`() {
        // 向后兼容：纯函数调用者（含既有用例）不传通道维时，报告多出的两图是空的、
        // 建议文案逐字不变——新维不许改变旧读法。
        val report = ChatEvidenceGateCalibration.calibrate(
            GateObservation(
                acceptedCount = 35,
                rejectedByReason = mapOf("SAME_KC_IN_COOLDOWN" to 20),
                acceptedPerHour = mapOf(1000L to 35),
            ),
        )

        assertTrue(report.rejectionSharesBySourceKind.isEmpty())
        assertTrue(report.peakAcceptedBySourceKind.isEmpty())
        val suggestion = report.suggestions.single()
        assertFalse("没有通道维就不该出现'来路'", suggestion.contains("来路"))
        assertEquals(
            "拒写「SAME_KC_IN_COOLDOWN」占比 36%：节流类拒写占比过高，检查冷却/配额是否与真实学习节奏冲突。",
            suggestion,
        )
    }
}
