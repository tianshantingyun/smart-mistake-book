package com.tingyun.smartmistakebook.feature.tutor

import com.tingyun.smartmistakebook.core.domain.StudyCatalogEntry
import com.tingyun.smartmistakebook.core.domain.TutorMessage
import com.tingyun.smartmistakebook.core.domain.TutorMessageRole
import com.tingyun.smartmistakebook.core.domain.TutorMessageStatus
import com.tingyun.smartmistakebook.core.model.MistakePdfLayout
import com.tingyun.smartmistakebook.core.model.TutorToolName
import com.tingyun.smartmistakebook.core.model.TutorToolTraceEntry
import com.tingyun.smartmistakebook.core.model.TutorTurnToolTrace
import com.tingyun.smartmistakebook.core.model.encodeTutorTurnToolTrace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 导出 sheet 的纯逻辑（4B B3-3/B3-4）：候选题来源、可读摘要与"会跳过"提示。
 *
 * 判别格是**候选只来自本地留痕**：模型正文里写什么都进不了候选；只有那一轮 `NOTEBOOK_READ`
 * 的 `problemEntryIds`（本地写进消息行的 tool_trace_json）算数。
 */
class TutorExportSheetTest {

    @Test
    fun candidatesComeOnlyFromTheRoundsNotebookReadTrace() {
        val matching = assistantMessage(
            logicalOperationId = "op-1",
            traceJson = traceJson(
                TutorToolTraceEntry(
                    tool = TutorToolName.NOTEBOOK_READ,
                    resultCount = 2,
                    ok = true,
                    problemEntryIds = listOf("entry-a", "entry-b"),
                ),
                // 别的工具的痕迹不产出候选；模型可见正文里出现"entry-fake"也不作数。
                TutorToolTraceEntry(tool = TutorToolName.KNOWLEDGE_READ, resultCount = 1, ok = true),
                TutorToolTraceEntry(
                    tool = TutorToolName.NOTEBOOK_READ,
                    resultCount = 1,
                    ok = true,
                    errorKind = null,
                ),
            ),
        )
        val otherRound = assistantMessage(
            logicalOperationId = "op-2",
            traceJson = traceJson(
                TutorToolTraceEntry(
                    tool = TutorToolName.NOTEBOOK_READ,
                    resultCount = 1,
                    ok = true,
                    problemEntryIds = listOf("entry-other"),
                ),
            ),
        )
        val student = studentMessage()

        val candidates = exportCandidateEntryIds(
            messages = listOf(student, matching, otherRound),
            logicalOperationId = "op-1",
        )

        assertEquals(listOf("entry-a", "entry-b"), candidates)
    }

    @Test
    fun aRoundWithoutAnyNotebookReadTraceHasNoCandidates() {
        assertEquals(
            emptyList<String>(),
            exportCandidateEntryIds(
                messages = listOf(
                    assistantMessage(
                        logicalOperationId = "op-1",
                        traceJson = traceJson(
                            TutorToolTraceEntry(
                                tool = TutorToolName.KNOWLEDGE_READ,
                                resultCount = 3,
                                ok = true,
                            ),
                        ),
                    ),
                ),
                logicalOperationId = "op-1",
            ),
        )
    }

    @Test
    fun resolvingCandidatesKeepsTraceOrderAndCountsTheOnesGoneFromTheLibrary() {
        val entries = listOf(
            catalogEntry("entry-b", "二次函数图像题", "数学"),
            catalogEntry("entry-a", "二次函数最值综合题", "数学"),
        )

        val resolution = resolveExportCandidates(
            candidateEntryIds = listOf("entry-a", "entry-gone", "entry-b"),
            entries = entries,
        )

        assertEquals(listOf("entry-a", "entry-b"), resolution.candidates.map { it.entryId })
        assertEquals("二次函数最值综合题", resolution.candidates.first().title)
        assertEquals(1, resolution.missingCount)
    }

    @Test
    fun pickingAnExtraQuestionAddsItToCandidatesAndToTheSubmittedSet() {
        // P1-b 回归：「从错题本再选」选中的 id 必须并入候选集合——否则行不出现、计数不变、
        // 确认按钮永远禁用（选完什么也没发生的静默死路）。
        val entries = listOf(
            catalogEntry("entry-a", "二次函数最值综合题", "数学"),
            catalogEntry("entry-new", "二次函数图像题", "数学"),
        )
        val sheet = TutorExportSheetState(
            layout = MistakePdfLayout.DEFAULT,
            candidateEntryIds = listOf("entry-a"),
            selectedEntryIds = setOf("entry-a"),
        )

        val picked = tutorExportSheetWithCandidateToggled(sheet, "entry-new", selected = true)
        val resolution = resolveExportCandidates(picked.candidateEntryIds, entries)

        // 行出现（能解析出标题）且进入提交集合：渲染的勾选态与提交集合一致。
        assertEquals(listOf("entry-a", "entry-new"), resolution.candidates.map { it.entryId })
        assertEquals(listOf("entry-a", "entry-new"), picked.submittableEntryIds(resolution))
        assertEquals(0, resolution.missingCount)

        // 取消勾选：移出提交集合，候选行保留（可以再勾回来）。
        val unchecked = tutorExportSheetWithCandidateToggled(picked, "entry-new", selected = false)
        assertEquals(listOf("entry-a"), unchecked.submittableEntryIds(resolution))
        assertTrue("entry-new" in unchecked.candidateEntryIds)
    }

    @Test
    fun theSummaryNamesTheTemplateAndTheKeyParameters() {
        assertEquals(
            "练习卷 · 双栏 · 字号大 · 含答案",
            exportLayoutSummary(
                MistakePdfLayout(
                    templateId = MistakePdfLayout.TEMPLATE_PRACTICE_SHEET,
                    columnCount = 2,
                    fontScale = 3,
                    includeAnswer = true,
                ),
            ),
        )
        assertEquals(
            "紧凑版 · 单栏 · 字号标准 · 不含答案",
            exportLayoutSummary(MistakePdfLayout.DEFAULT),
        )
        // with_answers 模板等价于打开答案区（模型只给模板、没给 includeAnswer 也要说清）。
        assertTrue(
            exportLayoutSummary(
                MistakePdfLayout(templateId = MistakePdfLayout.TEMPLATE_WITH_ANSWERS),
            ).contains("含答案"),
        )
        // P2-4：非默认的边距/图形缩放/块顺序也要进摘要（"图小一点"这类提议不许悄悄消失）。
        assertEquals(
            "练习卷 · 双栏 · 字号大 · 含答案 · 边距 60pt · 图 80% · 块序 段落→选择题",
            exportLayoutSummary(
                MistakePdfLayout(
                    templateId = MistakePdfLayout.TEMPLATE_PRACTICE_SHEET,
                    columnCount = 2,
                    fontScale = 3,
                    includeAnswer = true,
                    marginPt = 60,
                    imageScale = 0.8f,
                    blockOrder = listOf("paragraph", "choice_group"),
                ),
            ),
        )
    }

    @Test
    fun theSkipNoticeTellsTheStudentTheSolutionSectionWillBeSkipped() {
        // 没请求解析区 = 没有提示（空载体不渲染）。
        assertNull(exportSkipNotice(MistakePdfLayout.DEFAULT))
        // 请求了解析区 = 如实说会被跳过（与分页器的 PdfPlan.skipped、worker 通知同一判据）。
        val notice = exportSkipNotice(MistakePdfLayout(includeSolution = true))
        assertEquals("解析区暂不可用：本次导出会跳过它，不会留空占位。", notice)
    }

    private fun traceJson(vararg entries: TutorToolTraceEntry): String =
        requireNotNull(encodeTutorTurnToolTrace(TutorTurnToolTrace(entries = entries.toList())))

    private fun assistantMessage(logicalOperationId: String, traceJson: String) = TutorMessage(
        messageId = "assistant-$logicalOperationId",
        conversationId = "conversation-1",
        ordinal = 2,
        role = TutorMessageRole.ASSISTANT,
        bodyMarkdown = "错题本里查到 2 条（entry-fake 只是正文里的字，不是候选）。",
        status = TutorMessageStatus.SUCCEEDED,
        logicalOperationId = logicalOperationId,
        replyToMessageId = "student-$logicalOperationId",
        createdAtEpochMillis = 100,
        completedAtEpochMillis = 120,
        errorCode = null,
        toolTraceJson = traceJson,
    )

    private fun studentMessage() = TutorMessage(
        messageId = "student-op-1",
        conversationId = "conversation-1",
        ordinal = 1,
        role = TutorMessageRole.STUDENT,
        bodyMarkdown = "帮我找两道二次函数的题",
        status = TutorMessageStatus.SUCCEEDED,
        logicalOperationId = "op-1",
        replyToMessageId = null,
        createdAtEpochMillis = 90,
        completedAtEpochMillis = 95,
        errorCode = null,
    )

    private fun catalogEntry(entryId: String, title: String, subject: String) = StudyCatalogEntry(
        entryId = entryId,
        problemId = "problem-$entryId",
        problemRevisionId = "revision-$entryId",
        practiceUnitId = "unit-$entryId",
        subject = subject,
        title = title,
        problemMarkdown = "题面",
        sourceKey = null,
        isCuratedExample = false,
        nextReviewAtEpochMillis = null,
        retrievability = null,
    )
}
