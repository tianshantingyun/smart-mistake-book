package com.tingyun.smartmistakebook.feature.tutor

import com.tingyun.smartmistakebook.core.model.ModelLiveKind
import com.tingyun.smartmistakebook.core.model.ModelLiveText
import com.tingyun.smartmistakebook.core.model.TutorToolName
import com.tingyun.smartmistakebook.core.model.TutorToolTraceEntry
import com.tingyun.smartmistakebook.core.model.TutorTurnToolTrace
import com.tingyun.smartmistakebook.core.model.encodeTutorTurnToolTrace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 在途一轮的实时文本（大厅与会话共用的那一条流）。
 *
 * 消灭的失败：这条通道上的三条文本互相覆盖——网关按"当前这一条"发，后到的通道会把前一条
 * 从 map 里顶掉（`RoomModelTaskRepository.publishLiveText`）。折叠时若不保留上一条，学生就会
 * 看到思考链在回答开始写的那一刻整段消失；这条测试把"保留"钉住。
 */
class TutorLiveTurnTest {

    @Test
    fun laterChannelsReplaceTheirOwnSlotWithoutErasingTheOthers() {
        val folded = TutorLiveTurn.EMPTY
            .withLive(ModelLiveText(ModelLiveKind.THINKING, "先看定义域"))
            .withLive(ModelLiveText(ModelLiveKind.TOOL, "正在查错题本"))
            .withLive(ModelLiveText(ModelLiveKind.ANSWER, "第一步是求导"))

        assertEquals("先看定义域", folded.thinking)
        assertEquals("正在查错题本", folded.toolNote)
        assertEquals("第一步是求导", folded.answer)
        // 回答一开始写，思考卡收起（由渲染侧用 hasAnswer 决定展开与否），但思考链还在。
        assertTrue(folded.hasAnswer)
        assertEquals("先看定义域", folded.thinkingText)
    }

    @Test
    fun theToolLineIsNotPartOfTheThinkingCard() {
        // B1：思考是模型在推理、查阅是它对外做的事——合成一段之后学生分不出两者，
        // 而且工具行还要能点开看明细（混进卡片就点不到了）。
        val folded = TutorLiveTurn.EMPTY
            .withLive(ModelLiveText(ModelLiveKind.THINKING, "先看定义域"))
            .withLive(ModelLiveText(ModelLiveKind.TOOL, "正在查阅错题本…"))

        assertEquals("思考卡里只有思考链", "先看定义域", folded.thinkingText)
        assertFalse(
            "思考卡里不许出现工具行",
            folded.thinkingText.orEmpty().contains("查阅"),
        )
        assertEquals("工具行单独一行", "正在查阅错题本…", folded.toolTraceDisplay?.headline)
        assertFalse(
            "还没拿到明细时不给展开出口（不给点了没反应的入口）",
            folded.toolTraceDisplay?.hasDetails ?: true,
        )
    }

    @Test
    fun aDecodedTraceBecomesTheToolLineWithItsDetails() {
        // 实时那一轮一旦拿到痕迹（编码 JSON），小字与明细都从它来——与重开会话读回来的是
        // 同一个渲染函数，两处不可能漂成两句话。
        val traceJson = encodeTutorTurnToolTrace(
            TutorTurnToolTrace(
                entries = listOf(
                    TutorToolTraceEntry(
                        tool = TutorToolName.NOTEBOOK_READ,
                        resultCount = 2,
                        ok = true,
                    ),
                ),
            ),
        )

        val folded = TutorLiveTurn.EMPTY
            .withLive(ModelLiveText(ModelLiveKind.TOOL, "正在查阅错题本…"))
            .copy(toolTraceJson = traceJson)

        assertEquals("已查阅 · 1 项", folded.toolTraceDisplay?.headline)
        assertEquals(
            listOf("错题本 · 2 条"),
            folded.toolTraceDisplay?.rows?.map { row -> row.text },
        )
        assertTrue(folded.toolTraceDisplay?.hasDetails == true)
    }

    @Test
    fun theSameChannelOverwritesItself() {
        val folded = TutorLiveTurn.EMPTY
            .withLive(ModelLiveText(ModelLiveKind.ANSWER, "第一"))
            .withLive(ModelLiveText(ModelLiveKind.ANSWER, "第一第二"))

        assertEquals("第一第二", folded.answer)
    }

    @Test
    fun aNullLiveTextLeavesTheTurnUntouched() {
        val turn = TutorLiveTurn(thinking = "在想", answer = "在写")

        assertEquals(turn, turn.withLive(null))
    }

    @Test
    fun blankChannelsDoNotBecomeAThinkingCard() {
        val folded = TutorLiveTurn.EMPTY
            .withLive(ModelLiveText(ModelLiveKind.THINKING, "   "))
            .withLive(ModelLiveText(ModelLiveKind.TOOL, ""))

        assertNull(folded.thinkingText)
        assertEquals("", folded.toolNote)
        assertFalse(folded.hasAnswer)
    }
}
