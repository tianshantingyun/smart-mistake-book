package com.tingyun.smartmistakebook

import com.tingyun.smartmistakebook.core.domain.FsrsParameterFit
import com.tingyun.smartmistakebook.core.domain.PlannedReasonCalibration
import com.tingyun.smartmistakebook.core.domain.SchedulingCalibrationView
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 校准可见页的**文案口径**（批次 49 P2-12）。
 *
 * 这些字符串是学生判断"参数拟合到底有没有在工作"的唯一入口，所以逐字钉住：
 * - 参数值用点号小数（`Locale.ROOT`），中文系统区域不能把 0.5 显示成 "0,5"；
 * - "最近拟合"必须带本地时区的时间，不能只给一个样本数；
 * - 没有验证对时必须说"暂不可算"，不能拿 0.0 冒充一个损失；
 * - 按原因校准的 200 样本门必须写成建议性的（人工决定），不能写成系统会自动改权重。
 */
class SchedulingCalibrationCopyTest {

    @Test
    fun `parameter values print with a dot decimal and three decimals`() {
        assertEquals("0.212", schedulingParameterValueText(0.2119))
        assertEquals("1.000", schedulingParameterValueText(1.0))
        assertEquals("100.000", schedulingParameterValueText(100.0))
        assertEquals("0.001", schedulingParameterValueText(0.001))
    }

    @Test
    fun `parameter source distinguishes local fit default and imported sets`() {
        assertEquals(
            "本地拟合参数",
            schedulingParameterSourceText(SchedulingCalibrationView.ParameterSource.LOCAL_FIT),
        )
        assertEquals(
            "默认参数（本机尚未完成拟合）",
            schedulingParameterSourceText(SchedulingCalibrationView.ParameterSource.DEFAULT),
        )
        assertEquals(
            "已导入参数（没有本地拟合记录）",
            schedulingParameterSourceText(SchedulingCalibrationView.ParameterSource.IMPORTED),
        )
    }

    @Test
    fun `fit record text carries local time sample count and both losses`() {
        val zone = ZoneId.of("Asia/Shanghai")
        val adoptedAt = LocalDateTime.of(2026, 11, 7, 23, 30)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
        val fit = FsrsParameterFit(
            adoptedAtEpochMillis = adoptedAt,
            sampleCount = 1_234,
            validationLogLoss = 0.312,
            baselineValidationLogLoss = 0.355,
        )

        assertEquals(
            "最近拟合 2026-11-07 23:30 · 可预测样本 1234 例 · 验证损失 0.312（默认参数基线 0.355）",
            schedulingFitRecordText(fit, zone),
        )
    }

    @Test
    fun `fit record text tells the truth when the baseline loss is missing`() {
        val zone = ZoneId.of("Asia/Shanghai")
        val fit = FsrsParameterFit(
            adoptedAtEpochMillis = 0L,
            sampleCount = 400,
            validationLogLoss = 0.4,
            baselineValidationLogLoss = null,
        )

        val text = schedulingFitRecordText(fit, zone)

        assertTrue("缺基线时不能编一个数字出来", text.contains("默认参数基线 —"))
    }

    @Test
    fun `validation loss text never substitutes zero for an unmeasurable loss`() {
        assertEquals(
            "当前参数验证损失 — · 默认参数基线 —（没有验证对，暂不可算）",
            schedulingValidationLossText(current = null, baseline = null),
        )
        assertEquals(
            "当前参数验证损失 0.301 · 默认参数基线 0.355（同一 80/20 时间切分）",
            schedulingValidationLossText(current = 0.3012, baseline = 0.3554),
        )
    }

    @Test
    fun `reason verdicts keep the 200 sample gate advisory`() {
        val below = PlannedReasonCalibration("DUE_RECALL_RISK", 199, 0.5, 0.6)
        val at = PlannedReasonCalibration("DUE_RECALL_RISK", 200, 0.5, 0.6)

        assertEquals("样本 199 例：还不够（需 ≥200 例），暂不建议调这个理由的权重。", schedulingReasonVerdictText(below))
        assertEquals("样本 200 例：达到观察门槛，可人工讨论权重（不自动改写）。", schedulingReasonVerdictText(at))
    }

    @Test
    fun `reason row text shows realized recall against the overall baseline`() {
        val row = PlannedReasonCalibration("WEAK_KNOWLEDGE", 240, 0.625, 0.75)

        assertEquals("实现回忆率 63% · 总体基线 75%", schedulingReasonRecallText(row))
    }
}
