package com.tingyun.smartmistakebook.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `TutorToolCall` 的构造约束里，凡是"只对某个工具合法"的字段都必须 fail closed：
 * 模型可以在 JSON 里塞任何键，但解析后构造失败即整轮契约拒，不会静默降级。
 */
class TutorToolCallTest {

    @Test
    fun onlyTheMasteryReadToolMayAskForTheExtendedBudget() {
        // 扩展预算放大的是出网体量，只有那个会随学情增长的读工具配用它。
        TutorToolCall(
            tool = TutorToolName.MASTERY_READ,
            rationale = "需要看本科目完整清单",
            extendedResult = true,
        )

        assertThrows(IllegalArgumentException::class.java) {
            TutorToolCall(
                tool = TutorToolName.NOTEBOOK_READ,
                rationale = "检索错题本",
                terms = listOf("函数"),
                extendedResult = true,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            TutorToolCall(
                tool = TutorToolName.MASTERY_UPDATE,
                rationale = "学生答对了",
                terms = listOf("kc-1"),
                direction = TutorEvidenceDirection.POSITIVE,
                understanding = TutorUnderstandingTier.CONFIDENT,
                extendedResult = true,
            )
        }
    }

    @Test
    fun theExtendedBudgetDefaultsOff() {
        val call = TutorToolCall(
            tool = TutorToolName.MASTERY_READ,
            rationale = "看看掌握情况",
        )

        assertEquals(false, call.extendedResult)
    }

    @Test
    fun onlyTheSubjectScopedReadsMayOmitTerms() {
        // 留空 = "本科目清单"模式（MASTERY_READ），或按科目/当前题的作用域读（ADVISORY_READ，
        // D-M M7）；其它工具没有这种语义。
        TutorToolCall(tool = TutorToolName.MASTERY_READ, rationale = "本科目清单")
        TutorToolCall(
            tool = TutorToolName.ADVISORY_READ,
            rationale = "回顾本科目教学备注",
            advisoryScope = TutorAdvisoryScope.SUBJECT,
        )

        assertThrows(IllegalArgumentException::class.java) {
            TutorToolCall(tool = TutorToolName.KNOWLEDGE_READ, rationale = "查材料")
        }
        assertThrows(IllegalArgumentException::class.java) {
            TutorToolCall(tool = TutorToolName.ADVISORY_WRITE, rationale = "缺全部咨询字段")
        }
    }

    @Test
    fun advisoryWriteEnforcesItsScopeKindAndPayloadContract() {
        val valid = TutorToolCall(
            tool = TutorToolName.ADVISORY_WRITE,
            rationale = "学生把负号漏掉了",
            advisoryScope = TutorAdvisoryScope.NODE,
            advisoryKind = TutorAdvisoryKind.MISCONCEPTION,
            payloadMarkdown = "解不等式两边乘负数时忘记变号。",
            terms = listOf("K1"),
        )
        assertEquals(TutorAdvisoryScope.NODE, valid.advisoryScope)
        assertEquals(TutorAdvisoryKind.MISCONCEPTION, valid.advisoryKind)

        // 缺 scope / kind / payload 一律契约拒（不静默降级）。
        assertThrows(IllegalArgumentException::class.java) {
            TutorToolCall(
                tool = TutorToolName.ADVISORY_WRITE,
                rationale = "缺 scope",
                advisoryKind = TutorAdvisoryKind.MISCONCEPTION,
                payloadMarkdown = "误区。",
                terms = listOf("K1"),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            TutorToolCall(
                tool = TutorToolName.ADVISORY_WRITE,
                rationale = "缺 kind",
                advisoryScope = TutorAdvisoryScope.SUBJECT,
                payloadMarkdown = "误区。",
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            TutorToolCall(
                tool = TutorToolName.ADVISORY_WRITE,
                rationale = "缺 payload",
                advisoryScope = TutorAdvisoryScope.SUBJECT,
                advisoryKind = TutorAdvisoryKind.TEACHING_FOCUS,
            )
        }
        // payload 超上限（模型工具参数有本地硬上限，不靠 provider 的软约束）。
        assertThrows(IllegalArgumentException::class.java) {
            TutorToolCall(
                tool = TutorToolName.ADVISORY_WRITE,
                rationale = "超长 payload",
                advisoryScope = TutorAdvisoryScope.SUBJECT,
                advisoryKind = TutorAdvisoryKind.TEACHING_FOCUS,
                payloadMarkdown = "长".repeat(TutorToolCall.MAX_ADVISORY_PAYLOAD_CHARS + 1),
            )
        }
        // NODE 必须以代号为目标；PROBLEM/SUBJECT 不接受 terms（模型给了本地又不读 = 静默丢弃）。
        assertThrows(IllegalArgumentException::class.java) {
            TutorToolCall(
                tool = TutorToolName.ADVISORY_WRITE,
                rationale = "NODE 无代号",
                advisoryScope = TutorAdvisoryScope.NODE,
                advisoryKind = TutorAdvisoryKind.MISCONCEPTION,
                payloadMarkdown = "误区。",
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            TutorToolCall(
                tool = TutorToolName.ADVISORY_WRITE,
                rationale = "SUBJECT 带 terms",
                advisoryScope = TutorAdvisoryScope.SUBJECT,
                advisoryKind = TutorAdvisoryKind.MISCONCEPTION,
                payloadMarkdown = "误区。",
                terms = listOf("K1"),
            )
        }
        // 难度档只有"当前题"有读者：其它作用域拒（写了也没人消费 = 假记录）。
        assertThrows(IllegalArgumentException::class.java) {
            TutorToolCall(
                tool = TutorToolName.ADVISORY_WRITE,
                rationale = "科目级难度",
                advisoryScope = TutorAdvisoryScope.SUBJECT,
                advisoryKind = TutorAdvisoryKind.DIFFICULTY_TIER,
                payloadMarkdown = "HARD",
            )
        }
    }

    @Test
    fun generateFigureEnforcesItsKindAndDescriptionContract() {
        // 4B A1：kind + description 是生图工具的全部模型输入；缺一个、超长、夹带 terms
        // 都在契约层拒（模型可以塞任何键，但构造失败即整轮拒，不静默降级）。
        val valid = TutorToolCall(
            tool = TutorToolName.GENERATE_FIGURE,
            rationale = "当前题的图形是理解关键",
            figureKind = TutorFigureKind.REDRAW_PROBLEM,
            figureDescription = "重绘题面去手写",
        )
        assertEquals(TutorFigureKind.REDRAW_PROBLEM, valid.figureKind)
        assertEquals("重绘题面去手写", valid.figureDescription)
        assertTrue(valid.terms.isEmpty())

        // 缺 kind。
        assertThrows(IllegalArgumentException::class.java) {
            TutorToolCall(
                tool = TutorToolName.GENERATE_FIGURE,
                rationale = "缺 kind",
                figureDescription = "画一张图",
            )
        }
        // 缺 description / 空白 description。
        assertThrows(IllegalArgumentException::class.java) {
            TutorToolCall(
                tool = TutorToolName.GENERATE_FIGURE,
                rationale = "缺 description",
                figureKind = TutorFigureKind.GENERATE_PROCESS,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            TutorToolCall(
                tool = TutorToolName.GENERATE_FIGURE,
                rationale = "空 description",
                figureKind = TutorFigureKind.GENERATE_PROCESS,
                figureDescription = "   ",
            )
        }
        // description 超上限（本地硬上限，不靠 provider 的软约束）。
        assertThrows(IllegalArgumentException::class.java) {
            TutorToolCall(
                tool = TutorToolName.GENERATE_FIGURE,
                rationale = "超长 description",
                figureKind = TutorFigureKind.GENERATE_PROCESS,
                figureDescription = "长".repeat(TutorToolCall.MAX_FIGURE_DESCRIPTION_CHARS + 1),
            )
        }
        // terms 本地不消费：给了就是静默丢弃的输入，拒。
        assertThrows(IllegalArgumentException::class.java) {
            TutorToolCall(
                tool = TutorToolName.GENERATE_FIGURE,
                rationale = "夹带 terms",
                terms = listOf("函数"),
                figureKind = TutorFigureKind.GENERATE_PROCESS,
                figureDescription = "画一张图",
            )
        }
        // 控制字符（含换行）不是画面说明。
        assertThrows(IllegalArgumentException::class.java) {
            TutorToolCall(
                tool = TutorToolName.GENERATE_FIGURE,
                rationale = "换行夹带",
                figureKind = TutorFigureKind.GENERATE_PROCESS,
                figureDescription = "第一行\n第二行",
            )
        }
        // 恰好到上限是合法的。
        TutorToolCall(
            tool = TutorToolName.GENERATE_FIGURE,
            rationale = "到上限",
            figureKind = TutorFigureKind.GENERATE_PROCESS,
            figureDescription = "长".repeat(TutorToolCall.MAX_FIGURE_DESCRIPTION_CHARS),
        )
    }

    @Test
    fun figureFieldsAreRejectedOnOtherTools() {
        assertThrows(IllegalArgumentException::class.java) {
            TutorToolCall(
                tool = TutorToolName.NOTEBOOK_READ,
                rationale = "夹带生图字段",
                terms = listOf("函数"),
                figureKind = TutorFigureKind.GENERATE_PROCESS,
                figureDescription = "画一张图",
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            TutorToolCall(
                tool = TutorToolName.MASTERY_READ,
                rationale = "只夹带 description",
                figureDescription = "画一张图",
            )
        }
    }

    @Test
    fun advisoryFieldsAreRejectedOnOtherTools() {
        assertThrows(IllegalArgumentException::class.java) {
            TutorToolCall(
                tool = TutorToolName.MASTERY_UPDATE,
                rationale = "夹带咨询字段",
                terms = listOf("K1"),
                direction = TutorEvidenceDirection.POSITIVE,
                understanding = TutorUnderstandingTier.CONFIDENT,
                advisoryScope = TutorAdvisoryScope.SUBJECT,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            TutorToolCall(
                tool = TutorToolName.NOTEBOOK_READ,
                rationale = "夹带 payload",
                terms = listOf("函数"),
                payloadMarkdown = "不该在这",
            )
        }
    }
}
