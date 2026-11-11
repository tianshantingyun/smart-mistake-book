package com.tingyun.smartmistakebook.feature.library

import com.tingyun.smartmistakebook.core.model.ErrorCause
import com.tingyun.smartmistakebook.core.model.ErrorCauseAcceptanceSource
import com.tingyun.smartmistakebook.core.model.ErrorCauseChannel
import com.tingyun.smartmistakebook.core.model.ErrorCauseDetail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 批次 50 / 5c：错因候选面板的展示契约（JVM，不需设备）。
 *
 * 这些断言钉的是**产品口径**，不是排版：
 * 1. 面板标题必须是「教学候选（未成为标签）」——D-001 冻结合同要求错因不作为错题本的
 *    可见分类/标签；本面板只展示"教学候选"，落款必须自己说明这一点；
 * 2. 四类词表每类都有行为词的展示名，且带稳定码（诊断可回查），四类不重名；
 * 3. 细码必须能归属到唯一顶层类（错码挂错类的展示会误导学生）；
 * 4. 置信度只做四舍五入百分比，不显示成"事实概率"以外的推断；
 * 5. 空态文案不得替学生猜原因。
 */
class ErrorCausePanelStateTest {

    @Test
    fun `panel title states the candidate is not a label`() {
        assertEquals("教学候选（未成为标签）", ERROR_CAUSE_PANEL_TITLE)
    }

    @Test
    fun `every top-level cause has a distinct behavioural label carrying its stable code`() {
        val labels = ErrorCause.entries.map { errorCauseLabel(it) }
        assertTrue("四类都要有展示名", labels.all { it.isNotBlank() })
        assertEquals("四类展示名不得重复", labels.size, labels.toSet().size)
        ErrorCause.entries.forEach { cause ->
            assertTrue(
                "${cause.name} 的展示名必须带稳定码 ${cause.code}，诊断才能回查",
                errorCauseLabel(cause).contains(cause.code),
            )
        }
        assertNotEquals(
            "E03 不能叫成 E02：粗心与步骤执行是两个教学响应",
            errorCauseLabel(ErrorCause.CARELESS),
            errorCauseLabel(ErrorCause.PROCEDURAL),
        )
    }

    @Test
    fun `every detail code has a label and belongs to exactly one top-level cause`() {
        ErrorCauseDetail.entries.forEach { detail ->
            assertTrue("细码 ${detail.code} 缺展示名", errorCauseDetailLabel(detail).isNotBlank())
        }
        assertEquals(
            "细码总数与枚举一致（没有漏映射）",
            ErrorCauseDetail.entries.size,
            ErrorCauseDetail.entries.map { errorCauseDetailLabel(it) }.toSet().size,
        )
    }

    @Test
    fun `legend lists the four causes in stable code order`() {
        assertEquals(
            "四类图例按 E01→E04 固定顺序",
            listOf(
                ErrorCause.CONCEPTUAL,
                ErrorCause.PROCEDURAL,
                ErrorCause.CARELESS,
                ErrorCause.MISIDENTIFICATION,
            ),
            ERROR_CAUSE_LEGEND_ORDER,
        )
        assertTrue(ERROR_CAUSE_LEGEND_TEXT.startsWith("E01"))
        assertTrue(ERROR_CAUSE_LEGEND_TEXT.contains("E04"))
    }

    @Test
    fun `confidence renders as a rounded percentage`() {
        assertEquals("86%", errorCauseConfidenceText(0.864))
        assertEquals("100%", errorCauseConfidenceText(1.0))
        assertEquals("0%", errorCauseConfidenceText(0.0))
    }

    @Test
    fun `every channel has a label that does not claim certainty`() {
        ErrorCauseChannel.entries.forEach { channel ->
            val label = errorCauseChannelLabel(channel)
            assertTrue("通道 $channel 缺展示名", label.isNotBlank())
            assertTrue("通道展示名不得出现'已确认'", !label.contains("已确认"))
        }
    }

    @Test
    fun `acceptance source tells auto from user`() {
        assertEquals("模型自动落账", errorCauseAcceptanceLabel(ErrorCauseAcceptanceSource.AUTO_GATE))
        assertEquals("你确认", errorCauseAcceptanceLabel(ErrorCauseAcceptanceSource.USER_CONFIRMED))
        assertEquals("你改判", errorCauseAcceptanceLabel(ErrorCauseAcceptanceSource.USER_CORRECTED))
    }

    @Test
    fun `empty candidate copy does not guess the cause`() {
        assertEquals(
            "还没有候选。只有讲题或重做里留下证据时，才可能出现候选。",
            ERROR_CAUSE_EMPTY_CANDIDATES_TEXT,
        )
        assertTrue(
            "空态不得替学生下结论",
            listOf("你应该是", "你一定是", "概念错", "粗心").none {
                ERROR_CAUSE_EMPTY_CANDIDATES_TEXT.contains(it)
            },
        )
    }

    @Test
    fun `panel state keeps the auto gate and candidates apart`() {
        val state = ErrorCausePanelState(
            autoBookingClosed = true,
            candidates = listOf(candidate(observationId = "obs-1")),
        )
        assertTrue("门关闭时面板必须如实说明", state.autoBookingClosed)
        assertEquals(1, state.candidates.size)
        assertEquals("没有现行标签", null, state.active)
    }

    private fun candidate(observationId: String) = ErrorCauseCandidateItem(
        observationId = observationId,
        cause = ErrorCause.PROCEDURAL,
        detail = ErrorCauseDetail.PROC_STEP_ORDER,
        confidence = 0.86,
        channel = ErrorCauseChannel.TUTOR_TRACE,
        rationaleMarkdown = "学生把两步顺序调换了",
        observedAtEpochMillis = 1_000,
        evidenceCount = 1,
    )
}
