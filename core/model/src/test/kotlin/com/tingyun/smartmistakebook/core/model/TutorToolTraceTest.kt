package com.tingyun.smartmistakebook.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * 工具痕迹（B1）的存储形状与**渲染模型**：这一份是整个 B1 呈现的唯一文案源，
 * 所以"学生看到的那句话是什么"在这里逐字钉住，界面层不再自己拼字符串。
 *
 * 消灭的失败：工具痕迹此前是两行内存活标签（"正在查阅…" / "已查阅…（N 条结果）"），
 * 轮次一完就消失；而"N 条结果"在一条都没查到时会读成失败（B4）。这里钉住两件事：
 * 0 条时说「已查阅 · N 项」这种中性话（N = 发起过几次查阅），被拒的调用单独说并给出理由。
 */
class TutorToolTraceTest {

    @Test
    fun anEmptyTraceIsNeverStored() {
        assertNull(encodeTutorTurnToolTrace(TutorTurnToolTrace()))
        assertNull(decodeTutorTurnToolTrace(null))
        assertNull(decodeTutorTurnToolTrace("   "))
    }

    @Test
    fun aStoredTraceRoundTripsWithItsResultCodes() {
        val trace = TutorTurnToolTrace(
            entries = listOf(
                TutorToolTraceEntry(
                    tool = TutorToolName.NOTEBOOK_READ,
                    resultCount = 3,
                    ok = true,
                ),
                TutorToolTraceEntry(
                    tool = TutorToolName.MASTERY_UPDATE,
                    ok = false,
                    errorKind = "rejected:EVIDENCE_BELOW_CONFIDENCE",
                ),
            ),
        )

        val decoded = decodeTutorTurnToolTrace(encodeTutorTurnToolTrace(trace))

        assertEquals(trace, decoded)
    }

    /**
     * B3-3：`NOTEBOOK_READ` 的本地留痕条目 id 随痕迹存储形状往返——导出 sheet 的候选题
     * 就靠它；空列表不落键（空载体抹平），旧行解码仍是空列表。
     */
    @Test
    fun aNotebookReadTraceRoundTripsWithItsLocalProblemEntryIds() {
        val trace = TutorTurnToolTrace(
            entries = listOf(
                TutorToolTraceEntry(
                    tool = TutorToolName.NOTEBOOK_READ,
                    resultCount = 2,
                    ok = true,
                    problemEntryIds = listOf("entry-a", "entry-b"),
                ),
            ),
        )

        val encoded = requireNotNull(encodeTutorTurnToolTrace(trace))
        val decoded = decodeTutorTurnToolTrace(encoded)

        assertEquals(listOf("entry-a", "entry-b"), decoded?.entries?.single()?.problemEntryIds)
        // 空载体不落键：没有 id 的条目编码里不出现这个字段（旧行形状不变）。
        val withoutIds = requireNotNull(
            encodeTutorTurnToolTrace(
                TutorTurnToolTrace(
                    entries = listOf(
                        TutorToolTraceEntry(
                            tool = TutorToolName.NOTEBOOK_READ,
                            resultCount = 0,
                            ok = true,
                        ),
                    ),
                ),
            ),
        )
        assertFalse(withoutIds.contains("problemEntryIds"))
    }

    @Test
    fun onlyANotebookReadMayCarryLocalProblemEntryIds() {
        // 别的工具带条目 id = 构造错误（候选来源必须是检索事实，不是随便什么工具的输出）。
        val failure = runCatching {
            TutorToolTraceEntry(
                tool = TutorToolName.KNOWLEDGE_READ,
                resultCount = 1,
                ok = true,
                problemEntryIds = listOf("entry-a"),
            )
        }.exceptionOrNull()

        assertEquals(IllegalArgumentException::class.java, failure?.javaClass)
    }

    @Test
    fun aGarbledOrAlienJsonDecodesToNoTrace() {
        // 旧行/坏行都不许把渲染打断：不是痕迹的东西一律当"没有痕迹"。
        assertNull(decodeTutorTurnToolTrace("{not json"))
        assertNull(decodeTutorTurnToolTrace("""{"entries":[]}"""))
        assertNull(decodeTutorTurnToolTrace("""{"entries":[{"unexpected":true}]}"""))
    }

    @Test
    fun anOversizedTraceDropsTheTailAndSaysHowMany() {
        // 体积上限是"病态情况也不把消息行撑爆"的硬门；丢掉的条目必须**说出来**，
        // 否则一份残缺的痕迹看起来像完整的一轮。
        val entries = (1..200).map { index ->
            TutorToolTraceEntry(
                tool = TutorToolName.MASTERY_UPDATE,
                ok = false,
                errorKind = "rejected:EVIDENCE_BELOW_CONFIDENCE_$index",
            )
        }
        val encoded = requireNotNull(encodeTutorTurnToolTrace(TutorTurnToolTrace(entries = entries)))

        assertTrue(
            "编码结果必须落在上限内：${encoded.length}",
            encoded.length <= TUTOR_TOOL_TRACE_MAX_CHARS,
        )
        val decoded = requireNotNull(decodeTutorTurnToolTrace(encoded))
        assertTrue("尾部条目应被丢掉", decoded.entries.size < entries.size)
        assertTrue("丢掉的条数要说出来", decoded.omittedCount > 0)
        assertEquals(
            "留下的必须是前几条（先看到的先留）",
            entries.take(decoded.entries.size),
            decoded.entries,
        )
        assertNotNull(
            "截断必须标注（不许看起来像完整）",
            tutorToolTraceDisplay(decoded).omittedNote,
        )
    }

    @Test
    fun anEmptyScopeRoundReadsNeutralInsteadOfZeroResults() {
        // B4：一条都没读到（无科目上下文 / 无匹配）时，学生看到的是"这一轮查过几次"，
        // 而不是"0 条结果"——后者读起来像失败。
        val display = tutorToolTraceDisplay(
            TutorTurnToolTrace(
                entries = listOf(
                    TutorToolTraceEntry(
                        tool = TutorToolName.NOTEBOOK_READ,
                        resultCount = 0,
                        ok = true,
                    ),
                    TutorToolTraceEntry(
                        tool = TutorToolName.MASTERY_READ,
                        resultCount = 0,
                        ok = true,
                    ),
                ),
            ),
        )

        assertEquals("已查阅 · 2 项", display.headline)
        assertFalse("中性说法里不许出现 0 条", display.headline.contains("0"))
        assertEquals(
            listOf("错题本 · 本轮无可读范围", "掌握情况 · 本轮无可读范围"),
            display.rows.map(TutorToolTraceRow::text),
        )
        assertTrue(display.rows.all { row -> row.detail == null })
    }

    @Test
    fun consultedCountsAndRefusalsAreSaidSeparately() {
        val display = tutorToolTraceDisplay(
            TutorTurnToolTrace(
                entries = listOf(
                    TutorToolTraceEntry(
                        tool = TutorToolName.KNOWLEDGE_READ,
                        resultCount = 5,
                        ok = true,
                    ),
                    TutorToolTraceEntry(
                        tool = TutorToolName.NOTEBOOK_READ,
                        resultCount = 2,
                        ok = true,
                    ),
                    TutorToolTraceEntry(
                        tool = TutorToolName.MASTERY_UPDATE,
                        ok = false,
                        errorKind = "invalid_knowledge_code",
                    ),
                ),
            ),
        )

        assertEquals("已查阅 · 3 项（1 项未执行）", display.headline)
        assertEquals(
            listOf("知识点 · 5 条", "错题本 · 2 条", "掌握记录 · 未计入掌握度"),
            display.rows.map(TutorToolTraceRow::text),
        )
        assertEquals(
            "这个知识点本轮没有披露，没有执行",
            display.rows.last().detail,
        )
        assertFalse(display.rows.last().ok)
    }

    @Test
    fun aRoundThatOnlyRefusedSaysSo() {
        val display = tutorToolTraceDisplay(
            TutorTurnToolTrace(
                entries = listOf(
                    TutorToolTraceEntry(
                        tool = TutorToolName.NOTEBOOK_WRITE,
                        ok = false,
                        errorKind = "no_conversation",
                    ),
                ),
            ),
        )

        assertEquals("已查阅 · 1 项（均未执行）", display.headline)
        assertEquals("错题本 · 未保存", display.rows.single().text)
        assertEquals("这次会话没有可保存的题目", display.rows.single().detail)
    }

    @Test
    fun writesReadAsDoneNotAsCounts() {
        val display = tutorToolTraceDisplay(
            TutorTurnToolTrace(
                entries = listOf(
                    TutorToolTraceEntry(tool = TutorToolName.NOTEBOOK_WRITE, ok = true),
                    TutorToolTraceEntry(tool = TutorToolName.MASTERY_UPDATE, ok = true),
                ),
            ),
        )

        assertEquals(listOf("错题本 · 已保存", "掌握记录 · 已记录"), display.rows.map { it.text })
        assertEquals("已查阅 · 2 项", display.headline)
    }

    @Test
    fun theFigureToolReadsAsPicturesNotAsCounts() {
        // 4B A1：生图没有"条数"可言——出没出图是它唯一的产出（与写工具同一话术纪律）。
        val done = tutorToolTraceDisplay(
            TutorTurnToolTrace(
                entries = listOf(TutorToolTraceEntry(tool = TutorToolName.GENERATE_FIGURE, ok = true)),
            ),
        )
        assertEquals("配图 · 已生成", done.rows.single().text)
        assertEquals("已查阅 · 1 项", done.headline)

        val refused = tutorToolTraceDisplay(
            TutorTurnToolTrace(
                entries = listOf(
                    TutorToolTraceEntry(
                        tool = TutorToolName.GENERATE_FIGURE,
                        ok = false,
                        errorKind = "figure_unavailable",
                    ),
                ),
            ),
        )
        assertEquals("配图 · 未生成", refused.rows.single().text)
        assertEquals("当前没有可用的生图通道，没有生成", refused.rows.single().detail)
        assertEquals("配图", TutorToolName.GENERATE_FIGURE.displayLabel())
    }

    @Test
    fun everyGateRejectionReasonHasAStudentFacingSentence() {
        // 被拒要给理由（D-K2d）：门那边产出的每个码都得有一句人话，且不许出现内部机制词。
        val reasons = listOf(
            "EVIDENCE_BELOW_CONFIDENCE",
            "KNOWLEDGE_NODE_NOT_ANCHORED",
            "MASTERED_WITHOUT_EVIDENCE_ANCHOR",
            "POSITIVE_WITHOUT_EVIDENCE_ANCHOR",
            "SAME_KC_IN_COOLDOWN",
            "CONVERSATION_QUOTA_EXHAUSTED",
            "LEARNER_WINDOW_QUOTA_EXHAUSTED",
            "ATTENTION_BELOW_FLOOR",
            "CONTRADICTORY_SEMANTICS",
            "OBJECTIVE_ANSWER_CONTRADICTS_POSITIVE",
            "INTENT_BELOW_ROUTE_CONFIDENCE",
            "missing_semantics",
        )

        reasons.forEach { reason ->
            val text = tutorToolRefusalText("rejected:$reason")
            assertTrue("拒因 $reason 必须有人话", text.isNotBlank())
            assertFalse(
                "界面不许出现内部机制词：$reason -> $text",
                text.contains(reason) || text.contains("rejected"),
            )
        }
    }

    @Test
    fun anUnknownResultCodeStillReadsAsASentence() {
        assertEquals("这次查询没有完成", tutorToolRefusalText("failed"))
        assertEquals("没有完成", tutorToolRefusalText("something_new"))
    }

    @Test
    fun theRunningLineNamesToolsInStudentWords() {
        assertEquals(
            "正在查阅错题本、掌握情况…",
            tutorToolTraceRunningText(
                listOf(TutorToolName.NOTEBOOK_READ, TutorToolName.MASTERY_READ),
            ),
        )
        // 同一个工具在一轮里出现两次不重复念（重复的名称读起来像两次不同的东西）。
        assertEquals(
            "正在查阅错题本…",
            tutorToolTraceRunningText(
                listOf(TutorToolName.NOTEBOOK_READ, TutorToolName.NOTEBOOK_WRITE),
            ),
        )
        assertEquals("正在查阅", tutorToolTraceRunningText(emptyList()))
    }

    @Test
    fun aRefusedEntryCannotClaimAResultCount() {
        // 不变量：被拒的调用没有"条数"可言（否则渲染层会写出"未执行 · 3 条"这种自相矛盾的话）。
        runCatching {
            TutorToolTraceEntry(
                tool = TutorToolName.NOTEBOOK_READ,
                resultCount = 3,
                ok = false,
                errorKind = "failed",
            )
        }.onSuccess { fail("被拒的条目不许带条数") }
    }

    // ------------------------------------------------------------------
    // A2/A4（4B 批 2）：生图事实半——记账文案、失败文案、持久引用提取。
    // ------------------------------------------------------------------

    @Test
    fun aGeneratedFigureSaysTheChargeWasRecorded() {
        // 只记事实：本次真出网才说"已计入额度"，且不猜金额。
        val display = tutorToolTraceDisplay(
            TutorTurnToolTrace(
                entries = listOf(
                    TutorToolTraceEntry(
                        tool = TutorToolName.GENERATE_FIGURE,
                        ok = true,
                        figure = TutorToolFigureTrace(
                            kind = TutorFigureKind.GENERATE_PROCESS,
                            figureId = "figure-abc",
                            model = "gpt-image-2",
                            generatedNow = true,
                        ),
                    ),
                ),
            ),
        )

        assertEquals("配图 · 已生成", display.rows.single().text)
        assertEquals(FIGURE_CHARGE_NOTE, display.rows.single().detail)
        assertTrue(display.rows.single().ok)
    }

    @Test
    fun aCachedFigureDoesNotClaimAFreshCharge() {
        val display = tutorToolTraceDisplay(
            TutorTurnToolTrace(
                entries = listOf(
                    TutorToolTraceEntry(
                        tool = TutorToolName.GENERATE_FIGURE,
                        ok = true,
                        figure = TutorToolFigureTrace(
                            kind = TutorFigureKind.GENERATE_PROCESS,
                            figureId = "figure-abc",
                            generatedNow = false,
                        ),
                    ),
                ),
            ),
        )

        assertEquals(FIGURE_REUSED_NOTE, display.rows.single().detail)
        assertFalse(display.rows.single().detail!!.contains("计入额度"))
    }

    @Test
    fun aFailedRedrawSaysTheOriginalIsKept() {
        // 重绘静默失败此前没有任何界面出口；学生至少要知道"原图还在"。
        val display = tutorToolTraceDisplay(
            TutorTurnToolTrace(
                entries = listOf(
                    TutorToolTraceEntry(
                        tool = TutorToolName.GENERATE_FIGURE,
                        ok = false,
                        errorKind = "figure_failed",
                        figure = TutorToolFigureTrace(kind = TutorFigureKind.REDRAW_PROBLEM),
                    ),
                ),
            ),
        )

        assertEquals("配图 · 未生成", display.rows.single().text)
        assertEquals(FIGURE_REDRAW_FAILED_NOTE, display.rows.single().detail)
        assertFalse(display.rows.single().ok)
    }

    @Test
    fun aFailedProcessFigureKeepsTheGenericRefusal() {
        val display = tutorToolTraceDisplay(
            TutorTurnToolTrace(
                entries = listOf(
                    TutorToolTraceEntry(
                        tool = TutorToolName.GENERATE_FIGURE,
                        ok = false,
                        errorKind = "figure_failed",
                        figure = TutorToolFigureTrace(kind = TutorFigureKind.GENERATE_PROCESS),
                    ),
                ),
            ),
        )

        assertEquals("这次配图没有生成成功", display.rows.single().detail)
    }

    @Test
    fun anUnavailableRedrawKeepsTheChannelExplanation() {
        // 通道不可用与"这次没成"不是一回事：前者要保留"没有可用通道"的解释，
        // 学生才知道该去配模型，而不是以为只是这次运气不好。
        val display = tutorToolTraceDisplay(
            TutorTurnToolTrace(
                entries = listOf(
                    TutorToolTraceEntry(
                        tool = TutorToolName.GENERATE_FIGURE,
                        ok = false,
                        errorKind = "figure_unavailable",
                        figure = TutorToolFigureTrace(kind = TutorFigureKind.REDRAW_PROBLEM),
                    ),
                ),
            ),
        )

        assertEquals("当前没有可用的生图通道，没有生成", display.rows.single().detail)
    }

    @Test
    fun anEmptyScopeFigureRoundReadsAsNoDrawableProblemNotAsGenerated() {
        // A4：空范围是 ok=true（K2a），但**没有图**——此前工具卡会渲染成"已生成"（一句假话）。
        val display = tutorToolTraceDisplay(
            TutorTurnToolTrace(
                entries = listOf(
                    TutorToolTraceEntry(
                        tool = TutorToolName.GENERATE_FIGURE,
                        ok = true,
                        figure = TutorToolFigureTrace(kind = TutorFigureKind.REDRAW_PROBLEM),
                    ),
                ),
            ),
        )

        assertEquals("配图 · 本轮没有可画的题", display.rows.single().text)
        assertNull(display.rows.single().detail)
        assertTrue(display.rows.single().ok)
    }

    @Test
    fun figureFactsRoundTripThroughTheStoredTrace() {
        val trace = TutorTurnToolTrace(
            entries = listOf(
                TutorToolTraceEntry(
                    tool = TutorToolName.GENERATE_FIGURE,
                    ok = true,
                    figure = TutorToolFigureTrace(
                        kind = TutorFigureKind.REDRAW_PROBLEM,
                        figureId = "figure-xyz",
                        model = "gpt-image-2",
                        generatedNow = true,
                    ),
                ),
            ),
        )

        assertEquals(trace, decodeTutorTurnToolTrace(encodeTutorTurnToolTrace(trace)))
    }

    @Test
    fun figureAssetIdsAreExtractedInOrderAndDeduplicated() {
        val traceJson = encodeTutorTurnToolTrace(
            TutorTurnToolTrace(
                entries = listOf(
                    TutorToolTraceEntry(
                        tool = TutorToolName.GENERATE_FIGURE,
                        ok = true,
                        figure = TutorToolFigureTrace(
                            kind = TutorFigureKind.REDRAW_PROBLEM,
                            figureId = "figure-a",
                        ),
                    ),
                    TutorToolTraceEntry(tool = TutorToolName.NOTEBOOK_READ, resultCount = 1, ok = true),
                    TutorToolTraceEntry(
                        tool = TutorToolName.GENERATE_FIGURE,
                        ok = true,
                        figure = TutorToolFigureTrace(
                            kind = TutorFigureKind.GENERATE_PROCESS,
                            figureId = "figure-a",
                        ),
                    ),
                    TutorToolTraceEntry(
                        tool = TutorToolName.GENERATE_FIGURE,
                        ok = false,
                        errorKind = "figure_failed",
                        figure = TutorToolFigureTrace(kind = TutorFigureKind.REDRAW_PROBLEM),
                    ),
                ),
            ),
        )

        assertEquals(listOf("figure-a"), tutorTurnFigureAssetIds(traceJson))
        assertTrue(tutorTurnFigureAssetIds(null).isEmpty())
        assertTrue(tutorTurnFigureAssetIds("{not json").isEmpty())
    }

    @Test
    fun figureFactsAreOnlyAllowedOnTheFigureTool() {
        runCatching {
            TutorToolTraceEntry(
                tool = TutorToolName.NOTEBOOK_READ,
                resultCount = 1,
                ok = true,
                figure = TutorToolFigureTrace(kind = TutorFigureKind.GENERATE_PROCESS),
            )
        }.onSuccess { fail("非生图工具不许带生图事实") }
        runCatching {
            TutorToolTraceEntry(
                tool = TutorToolName.GENERATE_FIGURE,
                ok = false,
                errorKind = "figure_failed",
                figure = TutorToolFigureTrace(
                    kind = TutorFigureKind.REDRAW_PROBLEM,
                    figureId = "figure-x",
                ),
            )
        }.onSuccess { fail("失败的条目不许带资产 id") }
    }
}
