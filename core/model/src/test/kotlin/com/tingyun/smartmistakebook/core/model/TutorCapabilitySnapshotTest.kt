package com.tingyun.smartmistakebook.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Test

/**
 * **单一能力快照**（PRD-06）：广告面 = 已实现处理器 ∩ 配置/依赖就绪 ∩ 本轮声明。
 *
 * 判别格是"还没接通的东西不会出现在模型面前"：
 * - 每一枚本地动作都必须有执行处理器，且已实现集合与枚举一一对应（漏写分支 = 测试红）；
 * - 本地动作在大厅/讲题/计划轮共用同一份实现集合（不按栏分叉）；
 * - 图像后端或知识材料没就绪时，对应工具从快照里消失（admission/执行端读同一份声明）。
 */
class TutorCapabilitySnapshotTest {
    private fun lobby(
        declarations: List<TutorToolName> = TutorToolName.entries.toList(),
    ) = TutorLobbyInput(
        conversationId = "conv-1",
        messageOrdinal = 1,
        studentMessage = "帮我看看这道题",
        toolDeclarations = declarations,
    )

    private fun plan() = TutorPlanInput(
        sessionId = "session-1",
        draftRevisionNumber = 1,
        subject = "MATH",
        questionDocument = QuestionDocument(
            id = "question-1",
            blocks = listOf(ContentBlock.Paragraph("stem", "求函数的单调区间")),
        ),
        relevantLearningEvidence = emptyList(),
        projectionIsCurrent = true,
    )

    @Test
    fun actionOnlyRoundsConsumeTheSameBudgetAsToolRounds() {
        val initial = lobby(emptyList())
        assertTrue(TutorCapabilitySnapshot.forRound(initial).localActions.isNotEmpty())
        val exhausted = initial.copy(toolRoundsUsed = TutorToolRoundResult.MAX_TOOL_ROUNDS)
        val snapshot = TutorCapabilitySnapshot.forRound(exhausted)
        assertTrue(snapshot.localActions.isEmpty())
        assertTrue(snapshot.tools.isEmpty())
        assertTrue(
            TutorCapabilitySnapshot.forRound(
                initial.copy(toolRoundsUsed = TutorToolRoundResult.MAX_TOOL_ROUNDS - 1),
            ).localActions.isNotEmpty(),
        )
        assertThrows(IllegalArgumentException::class.java) { initial.copy(toolRoundsUsed = -1) }
        assertThrows(IllegalArgumentException::class.java) {
            initial.copy(toolRoundsUsed = TutorToolRoundResult.MAX_TOOL_ROUNDS + 1)
        }
    }

    @Test
    fun legacyResultsAlsoExhaustTheBudgetAndDefaultRoundCountKeepsItsOldEncoding() {
        val initial = lobby()
        val encoded = Json { encodeDefaults = true }.encodeToJsonElement(TutorLobbyInput.serializer(), initial).jsonObject
        assertFalse(encoded.containsKey("toolRoundsUsed"))
        val rounds = (1..TutorToolRoundResult.MAX_TOOL_ROUNDS).map { ordinal ->
            TutorToolRoundResult(ordinal, listOf(TutorToolOutcome(
                TutorToolName.NOTEBOOK_READ, true, "已查阅",
            )))
        }
        val legacy = TutorCapabilitySnapshot.forRound(initial.copy(toolRoundResults = rounds))
        assertTrue(legacy.localActions.isEmpty())
        assertTrue(legacy.tools.isEmpty())
        // Action rounds can occur between result-bearing tool rounds.
        val mixed = initial.copy(toolRoundResults = listOf(rounds[1]), toolRoundsUsed = 2)
        assertTrue(TutorCapabilitySnapshot.forRound(mixed).localActions.isNotEmpty())
    }

    @Test
    fun everyLocalActionHasAnImplementedHandler() {
        assertEquals(
            "新增一枚枚举却没有执行处理器时必须显式登记，不能默认被广告",
            TutorLocalAction.entries.toSet(),
            TutorCapabilitySnapshot.IMPLEMENTED_LOCAL_ACTIONS,
        )
        val snapshot = TutorCapabilitySnapshot.forRound(lobby())

        assertEquals(TutorCapabilitySnapshot.IMPLEMENTED_LOCAL_ACTIONS, snapshot.localActions)
        assertTrue(snapshot.advertises(TutorLocalAction.ADD_TO_REVIEW_PLAN))
    }

    @Test
    fun localActionsAreOfferedOnEveryTutorSurfaceWithTheSameBudget() {
        // 应用内操作已统一为自主执行：大厅、讲题与计划轮都读同一份实现集合。
        listOf(lobby(), plan()).forEach { input ->
            assertEquals(
                TutorCapabilitySnapshot.IMPLEMENTED_LOCAL_ACTIONS,
                TutorCapabilitySnapshot.forRound(input).localActions,
            )
        }
        val exhausted = plan().copy(
            toolDeclarations = TutorToolName.entries.toList(),
            toolRoundResults = (1..TutorToolRoundResult.MAX_TOOL_ROUNDS).map { ordinal ->
                TutorToolRoundResult(ordinal, listOf(TutorToolOutcome(
                    TutorToolName.NOTEBOOK_READ, true, "已查阅",
                )))
            },
        )
        assertTrue(TutorCapabilitySnapshot.forRound(exhausted).localActions.isEmpty())
    }

    @Test
    fun toolsComeFromTheRoundsDeclaredSet() {
        val snapshot = TutorCapabilitySnapshot.forRound(lobby(listOf(TutorToolName.NOTEBOOK_READ)))

        assertEquals(setOf(TutorToolName.NOTEBOOK_READ), snapshot.tools)
        assertTrue(TutorCapabilitySnapshot.forRound(lobby(emptyList())).tools.isEmpty())
    }

    @Test
    fun imageBackendAndKnowledgeMaterialReadinessGateTheirTools() {
        val ready = TutorCapabilitySnapshot.forRound(lobby())
        assertTrue(ready.advertises(TutorToolName.GENERATE_FIGURE))
        assertTrue(ready.advertises(TutorToolName.KNOWLEDGE_READ))

        val notReady = ready.withDependencies(
            imageBackendReady = false,
            knowledgeMaterialReady = false,
        )

        assertFalse(
            "图像后端没就绪时 GENERATE_FIGURE 不得被广告",
            notReady.advertises(TutorToolName.GENERATE_FIGURE),
        )
        assertFalse(
            "知识材料没就绪时 KNOWLEDGE_READ 不得被广告",
            notReady.advertises(TutorToolName.KNOWLEDGE_READ),
        )
        // 其余工具不受这两项依赖影响（依赖只裁它们各自负责的那一枚）。
        assertTrue(notReady.advertises(TutorToolName.NOTEBOOK_READ))
        assertTrue(notReady.advertises(TutorToolName.MASTERY_UPDATE))
    }

    @Test
    fun anUnreadyJsEngineOrTextOnlyProviderDoesNotGetJsAndFigureTools() {
        val ready = TutorCapabilitySnapshot.forRound(lobby())
        val gated = ready.withDependencies(
            imageBackendReady = true,
            knowledgeMaterialReady = true,
            javascriptToolReady = false,
            figureReadReady = false,
        )

        assertFalse(
            "JS 引擎未 ready 时 RUN_JAVASCRIPT 不得被广告",
            gated.advertises(TutorToolName.RUN_JAVASCRIPT),
        )
        assertFalse(
            "提供方不能看图时 READ_FIGURE 不得被广告",
            gated.advertises(TutorToolName.READ_FIGURE),
        )
        assertTrue(gated.advertises(TutorToolName.NOTEBOOK_READ))
    }
}
