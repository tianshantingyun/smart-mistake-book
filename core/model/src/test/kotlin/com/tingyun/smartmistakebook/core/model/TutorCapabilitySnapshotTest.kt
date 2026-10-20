package com.tingyun.smartmistakebook.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **单一能力快照**（PRD-06）：广告面 = 已实现处理器 ∩ 配置/依赖就绪 ∩ 本轮声明。
 *
 * 判别格是"还没接通的东西不会出现在模型面前"：
 * - `ADD_TO_REVIEW_PLAN` 没有执行处理器（落 `NOT_WIRED_YET`），因此永远不在快照里——
 *   它既不该被广告，也不该被准入；旧行由执行端如实说"还不能自动做"（那不是广告面）；
 * - 本地动作只在大厅轮出现（确认卡只长在那条交互面上）；讲题/计划轮广告了也没人接；
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
    fun anUnwiredActionIsNeverPartOfTheSnapshot() {
        assertFalse(
            "ADD_TO_REVIEW_PLAN 还没有执行处理器，不能进已实现集合",
            TutorLocalAction.ADD_TO_REVIEW_PLAN in TutorCapabilitySnapshot.IMPLEMENTED_LOCAL_ACTIONS,
        )
        val snapshot = TutorCapabilitySnapshot.forRound(lobby())

        assertEquals(
            setOf(
                TutorLocalAction.OPEN_PROBLEM,
                TutorLocalAction.SAVE_TO_NOTEBOOK,
                TutorLocalAction.START_EXPORT,
            ),
            snapshot.localActions,
        )
        assertFalse(snapshot.advertises(TutorLocalAction.ADD_TO_REVIEW_PLAN))
    }

    @Test
    fun localActionsAreOfferedOnlyWhereAConfirmationCardExists() {
        assertTrue(TutorCapabilitySnapshot.forRound(plan()).localActions.isEmpty())
        assertEquals(
            TutorCapabilitySnapshot.IMPLEMENTED_LOCAL_ACTIONS,
            TutorCapabilitySnapshot.forRound(lobby()).localActions,
        )
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
}
