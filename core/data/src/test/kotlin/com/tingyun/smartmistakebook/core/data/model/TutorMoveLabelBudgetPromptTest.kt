package com.tingyun.smartmistakebook.core.data.model

import com.tingyun.smartmistakebook.core.model.ContentBlock
import com.tingyun.smartmistakebook.core.model.ModelPromptPolicyVersions
import com.tingyun.smartmistakebook.core.model.QuestionDocument
import com.tingyun.smartmistakebook.core.model.TutorKnowledgeEvidence
import com.tingyun.smartmistakebook.core.model.TutorEvidenceLevel
import com.tingyun.smartmistakebook.core.model.TutorPlanInput
import com.tingyun.smartmistakebook.core.model.TutorRespondInput
import com.tingyun.smartmistakebook.core.model.TutorSuggestedMove
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * KD-38 ②：动作标签的预算必须**写进提示词**，且提示词一改就得 bump 策略版本。
 *
 * 消灭的失败：提示词里此前完全没有预算声明，模型没有理由把按钮文案压到 32 个字符以内，
 * 而越界标签会让整轮永久失败。声明与版本是同一条纪律的两半——提示词是学生授权的范围，
 * 变了就必须换版本号，否则 egress 授权回执会冒充同一份提示词。
 */
class TutorMoveLabelBudgetPromptTest {

    @Test
    fun thePlanPromptDeclaresTheMoveLabelBudget() {
        val prompt = OpenAiModelTaskAdapters.prompt(planInput())

        assertTrue(
            "计划规则 12 必须声明 label 的字符预算，否则模型没有理由把按钮文案压进预算",
            prompt.contains(LABEL_BUDGET_DECLARATION),
        )
    }

    @Test
    fun theRespondPromptDeclaresTheMoveLabelBudget() {
        val prompt = OpenAiModelTaskAdapters.prompt(respondInput())

        assertTrue(
            "回应规则 8 必须声明 label 的字符预算",
            prompt.contains(LABEL_BUDGET_DECLARATION),
        )
    }

    @Test
    fun theDeclaredBudgetIsTheContractDefault() {
        // 数值单独钉字面量：提示词里的那句话由常量插值，常量一改，这里先红——逼一次显式决定。
        assertEquals(32, TutorSuggestedMove.MAX_LABEL_CHARS)
        assertTrue(LABEL_BUDGET_DECLARATION.contains("32"))
    }

    @Test
    fun bothTutorPromptVersionsWereBumpedWithTheDeclaration() {
        assertEquals("tutor-plan-v19-move-label-budget", ModelPromptPolicyVersions.TUTOR_PLAN)
        assertEquals("tutor-respond-v26-move-label-budget", ModelPromptPolicyVersions.TUTOR_RESPOND)
    }

    private fun planInput() = TutorPlanInput(
        sessionId = "session-1",
        draftRevisionNumber = 1,
        subject = "MATH",
        questionDocument = QuestionDocument(
            id = "question-current",
            blocks = listOf(ContentBlock.Paragraph("stem", "求函数的单调区间")),
        ),
        relevantLearningEvidence = listOf(
            TutorKnowledgeEvidence(
                knowledgeNodeId = "node-derivative",
                displayName = "导数符号",
                level = TutorEvidenceLevel.LEARNING,
                independentCorrectLowerBound = 0.3,
            ),
        ),
        projectionIsCurrent = true,
    )

    private fun respondInput() = TutorRespondInput(
        sessionId = "session-1",
        draftRevisionNumber = 1,
        subject = "MATH",
        questionDocument = planInput().questionDocument,
        relevantLearningEvidence = emptyList(),
        projectionIsCurrent = true,
        responseOrdinal = 1,
        cycleOrdinal = 1,
        turnOrdinal = 1,
        studentMessage = "这一步怎么来的？",
    )

    private companion object {
        val LABEL_BUDGET_DECLARATION = "label不超过${TutorSuggestedMove.MAX_LABEL_CHARS}个字符"
    }
}
