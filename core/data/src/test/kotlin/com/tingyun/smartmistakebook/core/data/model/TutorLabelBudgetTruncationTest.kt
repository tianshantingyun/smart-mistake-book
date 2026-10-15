package com.tingyun.smartmistakebook.core.data.model

import com.tingyun.smartmistakebook.core.model.ContentBlock
import com.tingyun.smartmistakebook.core.model.ModelTextBudgetExceededException
import com.tingyun.smartmistakebook.core.model.QuestionDocument
import com.tingyun.smartmistakebook.core.model.RelatedProblemCandidate
import com.tingyun.smartmistakebook.core.model.SubjectKind
import com.tingyun.smartmistakebook.core.model.TutorEvidenceLevel
import com.tingyun.smartmistakebook.core.model.TutorKnowledgeEvidence
import com.tingyun.smartmistakebook.core.model.TutorMoveType
import com.tingyun.smartmistakebook.core.model.TutorPlanInput
import com.tingyun.smartmistakebook.core.model.TutorPlanOutput
import com.tingyun.smartmistakebook.core.model.TutorRespondInput
import com.tingyun.smartmistakebook.core.model.TutorRespondOutput
import com.tingyun.smartmistakebook.core.model.TutorRoundQuestionDeclaration
import com.tingyun.smartmistakebook.core.model.TutorSuggestedMove
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * KD-38 ①：**解析期按格式预算收敛、构造器守完整性**。
 *
 * 超预算的模型文本此前一律在构造器里抛 `IllegalArgumentException`（网关归不可重试的
 * INVALID_RESPONSE）——一个 33 字符的动作标签就能让整轮永久失败。短标签字段因此在解析期
 * 按各自预算 `take(n)`，构造器保持严格（它同时守数据库回读），正文级文本仍走超预算异常。
 */
class TutorLabelBudgetTruncationTest {

    @Test
    fun anOverBudgetKnowledgeLabelIsTruncatedBeforeTheConstructorSeesIt() {
        // 97 个字符 > TutorKnowledgeEvidence.MAX_LABEL_CHARS(96)：解析后应只剩 96 个，
        // 且整轮不再判死（构造器看到的是收敛后的值）。
        val overBudgetLabel = "知".repeat(TutorKnowledgeEvidence.MAX_LABEL_CHARS + 1)

        val output = OpenAiModelTaskAdapters.parse(
            payload = buildJsonObject {
                put("openingMarkdown", "先看这一步的观察点。")
                put("solutionMarkdown", "完整讲解写在这里。")
                put("alternateMethodMarkdown", "换一种表示再讲一遍。")
                put("difficultyReasonMarkdown", "区分正负对应错误和变号遗漏。")
                put("targetedEvidenceLabels", buildJsonArray { add(JsonPrimitive("导数符号")) })
                put("inferredKnowledgeLabels", buildJsonArray { add(JsonPrimitive(overBudgetLabel)) })
            },
            input = planInput(),
            modelVersion = "test-model",
        ) as TutorPlanOutput

        val label = output.plan.inferredKnowledgeLabels.single()
        assertEquals(TutorKnowledgeEvidence.MAX_LABEL_CHARS, label.length)
        assertEquals(overBudgetLabel.take(TutorKnowledgeEvidence.MAX_LABEL_CHARS), label)
    }

    @Test
    fun anOverBudgetAnchorTermIsTruncatedAndStillBindsTheRoundQuestion() {
        // 锚词预算 64：超出的词在解析期收敛；收敛后仍是学生原话与题面标题的前缀，逐字锚照旧核得上。
        val anchorPhrase = "对称轴".repeat(22)
        assertTrue(anchorPhrase.length > TutorRoundQuestionDeclaration.MAX_ANCHOR_TERM_CHARS)
        val candidate = RelatedProblemCandidate(
            problemId = "problem-current",
            problemRevisionId = "revision-current",
            subject = SubjectKind.MATH,
            title = "二次函数$anchorPhrase",
            questionDocument = QuestionDocument(
                id = "question-current",
                blocks = listOf(ContentBlock.Paragraph("stem", "求二次函数的对称轴。")),
            ),
        )

        val output = OpenAiModelTaskAdapters.parse(
            payload = buildJsonObject {
                put("messageMarkdown", "我们从对称轴的位置重新看一遍。")
                put("solutionRevealed", false)
                put(
                    "boundQuestion",
                    buildJsonObject {
                        put("problemId", candidate.problemId)
                        put("problemRevisionId", candidate.problemRevisionId)
                        put("anchorTerms", buildJsonArray { add(JsonPrimitive(anchorPhrase)) })
                    },
                )
            },
            input = respondInput(
                candidate = candidate,
                studentMessage = "老师，$anchorPhrase，我还是没弄懂。",
            ),
            modelVersion = "test-model",
        ) as TutorRespondOutput

        val boundQuestion = requireNotNull(output.boundQuestion) {
            "锚词按预算收敛后仍必须锚住本轮这道题"
        }
        val anchorTerm = boundQuestion.anchorTerms.single()
        assertEquals(TutorRoundQuestionDeclaration.MAX_ANCHOR_TERM_CHARS, anchorTerm.length)
        assertEquals(anchorPhrase.take(TutorRoundQuestionDeclaration.MAX_ANCHOR_TERM_CHARS), anchorTerm)
    }

    @Test
    fun theConstructorStillRejectsAnOverBudgetLabelItIsHandedDirectly() {
        // 解析期收敛的前提是"本地构造/回读这一侧仍然严格"：绕过解析器的超长标签照旧被拒，
        // 且拒的是**专用**超预算类型（网关据此归可重试出口）。
        assertThrows(ModelTextBudgetExceededException::class.java) {
            TutorSuggestedMove(
                id = "move-1",
                label = "看".repeat(TutorSuggestedMove.MAX_LABEL_CHARS + 1),
                type = TutorMoveType.DEEPEN_REASONING,
            )
        }
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

    private fun respondInput(
        candidate: RelatedProblemCandidate,
        studentMessage: String,
    ) = TutorRespondInput(
        sessionId = "session-1",
        draftRevisionNumber = 1,
        subject = "MATH",
        questionDocument = candidate.questionDocument,
        relevantLearningEvidence = emptyList(),
        projectionIsCurrent = true,
        responseOrdinal = 1,
        cycleOrdinal = 1,
        turnOrdinal = 1,
        studentMessage = studentMessage,
        boundQuestionCandidates = listOf(candidate),
        knownRoundQuestion = candidate,
    )
}
