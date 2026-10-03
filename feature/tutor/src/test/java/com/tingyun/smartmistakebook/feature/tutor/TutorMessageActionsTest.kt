package com.tingyun.smartmistakebook.feature.tutor

import com.tingyun.smartmistakebook.core.domain.AgentPendingRequestKind
import com.tingyun.smartmistakebook.core.domain.TutorMessage
import com.tingyun.smartmistakebook.core.domain.TutorMessageRole
import com.tingyun.smartmistakebook.core.domain.TutorMessageStatus
import com.tingyun.smartmistakebook.core.domain.exportProposalPayload
import com.tingyun.smartmistakebook.core.model.MistakePdfLayout
import com.tingyun.smartmistakebook.core.model.ModelLiveKind
import com.tingyun.smartmistakebook.core.model.ModelLiveText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 消息动作条与「已停止」的判据（A2），以及 UI 侧的信封守卫（A6）。
 *
 * 这几条都是**纯函数**：动作条该不该出现、出现的重试指哪条消息、哪段实时文本不许进正文。
 */
class TutorMessageActionsTest {

    @Test
    fun everyRoundOfTheConversationCanBeRetriedWhileItIsTheLastOne() {
        val student = message("m1", TutorMessageRole.STUDENT, 1, "这题怎么做")
        val succeeded = message("m2", TutorMessageRole.ASSISTANT, 2, "先看定义域").copy(
            status = TutorMessageStatus.SUCCEEDED,
            replyToMessageId = "m1",
        )
        val cancelled = succeeded.copy(
            messageId = "m3",
            ordinal = 3,
            status = TutorMessageStatus.CANCELLED,
            bodyMarkdown = TUTOR_CONVERSATION_STOPPED_REPLY_BODY,
        )

        // 成功的那一轮也能重试（重试 = 重发同类请求）；已停止的那一轮同样。
        assertEquals(student, succeeded.retryTargetOrNull(listOf(student, succeeded)))
        assertEquals(student, cancelled.retryTargetOrNull(listOf(student, cancelled)))
        // 已经被下一句取代的轮次不再给出口（给了就是假按钮：重发会把更晚的轮次塞进上下文）。
        assertNull(cancelled.retryTargetOrNull(listOf(student, cancelled, student.copy(messageId = "m4", ordinal = 4))))
        // 学生消息本身没有重试目标。
        assertNull(student.retryTargetOrNull(listOf(student)))
    }


    @Test
    fun theStoppedRoundIsNotAFailureCard() {
        // 「已停止」是一名灰字 + 可重试；它没有失败码，也不走失败卡那条路（失败卡的出口要求
        // 失败码属于"重发有意义"的那几个——已停止根本不在这条路上）。
        val stopped = message("m3", TutorMessageRole.ASSISTANT, 3, TUTOR_CONVERSATION_STOPPED_REPLY_BODY)
            .copy(status = TutorMessageStatus.CANCELLED, replyToMessageId = "m1", errorCode = null)

        assertEquals(TutorMessageStatus.CANCELLED, stopped.status)
        assertNull(stopped.errorCode)
        assertNull(stopped.resendTargetOrNull(listOf(stopped)))
        // 但它仍然可以重试（动作条那一条出口）。
        val student = message("m1", TutorMessageRole.STUDENT, 1, "这题怎么做")
        assertEquals(student, stopped.retryTargetOrNull(listOf(student, stopped)))
    }

    /**
     * 讲题侧的助手动作条（A2）：与智能体栏共用同一份判据——有正文就有「复制」（复制的是
     * **消息行里的原文 markdown**），学生停掉的那一轮只有灰字（这一轮没有答出来的正文可复制）。
     */
    @Test
    fun theTutoringSurfaceOffersTheCopyExitOnTheAssistantsOwnWords() {
        val succeeded = tutorReplyActionShape(bodyMarkdown = "先把定义域写出来")
        assertEquals("先把定义域写出来", succeeded.copyText)
        assertNull(succeeded.stoppedLabel)
        assertNull(succeeded.retryLabel)

        // 已停止：灰字「已停止」，没有可复制的"原文"（那句只是停止提示，不是模型答的话）。
        val stopped = tutorReplyActionShape(bodyMarkdown = null, stopped = true)
        assertNull(stopped.copyText)
        assertEquals(TUTOR_SURFACE_STOPPED_LABEL, stopped.stoppedLabel)

        // 三个动作都没有时整条不渲染（不留一条空动作条）。
        assertTrue(tutorReplyActionShape(bodyMarkdown = "   ").isEmpty)
    }

    @Test
    fun theCardCopyTellsTheStudentWhatTheClickWillDo() {
        val save = pendingRequestCopy(AgentPendingRequestKind.SAVE_TO_NOTEBOOK)
        assertTrue(save.title.contains("错题本"))
        assertTrue(save.detail.contains("点了才会存"))

        // 工具拼写与动作拼写是同一件事，学生的文案必须同一条。
        assertEquals(save, pendingRequestCopy(AgentPendingRequestKind.NOTEBOOK_WRITE))
        assertEquals(save, pendingRequestCopy(AgentPendingRequestKind.SAVE_TO_NOTEBOOK))
        // 导出（4B B3-2）：确认卡展示模型提议的模板名与关键参数（"两栏·含答案"这种可读摘要），
        // 并如实说"点了才打开导出设置、点开始导出才生成文件"。
        val exportCopy = pendingRequestCopy(
            AgentPendingRequestKind.START_EXPORT,
            exportProposalPayload(
                MistakePdfLayout(
                    templateId = MistakePdfLayout.TEMPLATE_PRACTICE_SHEET,
                    columnCount = 2,
                    includeAnswer = true,
                ),
            ),
        )
        assertTrue(exportCopy.detail.contains("练习卷"))
        assertTrue(exportCopy.detail.contains("双栏"))
        assertTrue(exportCopy.detail.contains("含答案"))
        assertTrue(exportCopy.detail.contains("开始导出"))
        // 未接线的复习计划仍如实说"这一版点了不会…"（不装作能执行）。
        assertTrue(
            pendingRequestCopy(AgentPendingRequestKind.ADD_TO_REVIEW_PLAN).detail.contains("不会改动计划"),
        )
    }

    @Test
    fun anEnvelopeIsNeverRenderedAsTheReplyBody() {
        // Route B 的 content 增量是 json_object 信封：界面上永远不许把它当正文画出来。
        val envelopeOnset = TutorLiveTurn.EMPTY
            .withLive(ModelLiveText(ModelLiveKind.ANSWER, """{"intentDec"""))
        val fullEnvelope = TutorLiveTurn.EMPTY
            .withLive(ModelLiveText(ModelLiveKind.ANSWER, """{"intentDecision":{"intent":"NONE"},"messageMarkdown":"你好"}"""))

        assertNull(envelopeOnset.answer)
        assertFalse(envelopeOnset.hasAnswer)
        assertNull(fullEnvelope.answer)
        // 正常的正文照常长出来（含以花括号开头的数学正文——它不带引号键，不是信封）。
        assertEquals("先把定义域写出来", TutorLiveTurn.EMPTY
            .withLive(ModelLiveText(ModelLiveKind.ANSWER, "先把定义域写出来")).answer)
        assertEquals("{x | x > 0} 就是这个集合", TutorLiveTurn.EMPTY
            .withLive(ModelLiveText(ModelLiveKind.ANSWER, "{x | x > 0} 就是这个集合")).answer)
    }

    @Test
    fun aToolRoundMovesTheNarrationOutOfTheBodyAndIntoTheToolUnit() {
        // 网关在认出工具轮时先撤回正文、再把叙述发到查阅单元；界面这一层要做的两件事：
        // 撤回（空正文）真的把正文清掉，叙述出现在**工具那一行**（B1 之后不再混进思考卡）。
        val moved = TutorLiveTurn.EMPTY
            .withLive(ModelLiveText(ModelLiveKind.ANSWER, "我先查一下错题本"))
            .withLive(ModelLiveText(ModelLiveKind.ANSWER, ""))
            .withLive(ModelLiveText(ModelLiveKind.TOOL, "我先查一下错题本"))

        assertFalse(moved.hasAnswer)
        assertEquals("我先查一下错题本", moved.toolNote)
        assertEquals("我先查一下错题本", moved.toolTraceDisplay?.headline)
        assertNull("工具行不再进思考卡", moved.thinkingText)
    }

    private fun message(
        messageId: String,
        role: TutorMessageRole,
        ordinal: Int,
        body: String,
    ) = TutorMessage(
        messageId = messageId,
        conversationId = "conversation-1",
        ordinal = ordinal,
        role = role,
        bodyMarkdown = body,
        status = TutorMessageStatus.SUCCEEDED,
        logicalOperationId = "logical-1",
        replyToMessageId = null,
        createdAtEpochMillis = ordinal.toLong(),
        completedAtEpochMillis = ordinal.toLong(),
        errorCode = null,
    )
}
