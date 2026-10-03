package com.tingyun.smartmistakebook.core.domain

import com.tingyun.smartmistakebook.core.model.MistakePdfLayout
import com.tingyun.smartmistakebook.core.model.TutorLocalAction
import com.tingyun.smartmistakebook.core.model.TutorLocalActionRequest
import com.tingyun.smartmistakebook.core.model.TutorToolName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 本地动作的**准入三步**（D-K2e）：形状 → 权限档 → 本轮目标。
 *
 * 判别格是"什么情况下不挂卡"：参数键不在声明里（模型造了参数）、本轮不放行（不在白名单里）、
 * 存题没有可存的东西——三种都返回 null 而不是"挂一张点了没反应的卡"。另外钉住两种拼写
 * （工具 `NOTEBOOK_WRITE` 与动作 `SAVE_TO_NOTEBOOK`）落到**同一个 kind 映射**上。
 */
class TutorLocalActionAdmissionTest {
    private val context = TutorLocalActionContext(
        captureSessionId = "tutor-session-1",
        attachedImageAssetIds = listOf("asset-1"),
    )

    @Test
    fun aDeclaredActionWithALocalTargetIsAdmittedAsAsk() {
        val admission = tutorLocalActionAdmission(
            request = TutorLocalActionRequest(action = TutorLocalAction.SAVE_TO_NOTEBOOK),
            context = context,
        )

        assertNotNull(admission)
        assertEquals(AgentPendingRequestKind.SAVE_TO_NOTEBOOK, admission!!.kind)
        assertEquals(TutorLocalActionTarget.SAVE_CAPTURE_DRAFT, admission.target)
        assertEquals(true, admission.decision.requiresConsentCard)
        // 卡的形状 = 这个 kind 声明的键（不是"整个上下文"）：三个键都在，读回才拿得到目标。
        requireAgentPendingRequestPayload(admission.kind, admission.payloadJson)
        assertEquals("tutor-session-1", tutorLocalActionContext(admission.payloadJson).captureSessionId)
    }

    @Test
    fun theToolSpellingAdmitsTheSameThing() {
        val admission = tutorLocalActionAdmission(
            subject = TutorPermissionSubject.Tool(TutorToolName.NOTEBOOK_WRITE),
            context = TutorLocalActionContext(attachedImageAssetIds = listOf("asset-1")),
        )

        assertNotNull(admission)
        assertEquals(AgentPendingRequestKind.NOTEBOOK_WRITE, admission!!.kind)
        assertEquals(TutorLocalActionTarget.INTAKE_ATTACHED_IMAGES, admission.target)
        assertNull(admission.action)
    }

    @Test
    fun anUndeclaredParameterMakesTheWholeRequestInvalid() {
        val admission = tutorLocalActionAdmission(
            request = TutorLocalActionRequest(
                action = TutorLocalAction.SAVE_TO_NOTEBOOK,
                parameters = mapOf("problemId" to "problem-7"),
            ),
            context = context,
        )

        assertNull(admission)
    }

    @Test
    fun anActionThatIsNotAdmittedThisRoundHangsNoCard() {
        val admission = tutorLocalActionAdmission(
            request = TutorLocalActionRequest(action = TutorLocalAction.OPEN_PROBLEM),
            context = context,
            permissionContext = TutorRoundPermissionContext(
                allowedActions = setOf(TutorLocalAction.START_EXPORT),
            ),
        )

        assertNull(admission)
    }

    @Test
    fun aSaveWithoutAnythingToSaveHangsNoCard() {
        assertNull(
            tutorLocalActionAdmission(
                request = TutorLocalActionRequest(action = TutorLocalAction.SAVE_TO_NOTEBOOK),
                context = TutorLocalActionContext(),
            ),
        )
        assertNull(
            tutorLocalActionAdmission(
                subject = TutorPermissionSubject.Tool(TutorToolName.NOTEBOOK_WRITE),
                context = TutorLocalActionContext(),
            ),
        )
    }

    @Test
    fun openingTheNotebookItselfIsStillARealAction() {
        // 打开某题没有具体题时落到错题本本身（真动作），所以它不需要额外的"目标"。
        val admission = tutorLocalActionAdmission(
            request = TutorLocalActionRequest(action = TutorLocalAction.OPEN_PROBLEM),
            context = TutorLocalActionContext(),
        )

        assertNotNull(admission)
        assertEquals(TutorLocalActionTarget.LIBRARY_PROBLEM_DETAIL, admission!!.target)
        assertEquals("{}", admission.payloadJson)
    }

    @Test
    fun thePayloadShapeIsPerKind() {
        // 每个 kind 允许的键：存题三种目标、打开题/复习计划只认"哪道题"、导出是模板 + 完整版式。
        assertEquals(
            setOf("captureSessionId", "libraryProblemId", "imageAssetIds"),
            agentPendingRequestPayloadKeys(AgentPendingRequestKind.SAVE_TO_NOTEBOOK),
        )
        assertEquals(
            setOf("captureSessionId", "libraryProblemId", "imageAssetIds"),
            agentPendingRequestPayloadKeys(AgentPendingRequestKind.NOTEBOOK_WRITE),
        )
        assertEquals(
            setOf("libraryProblemId"),
            agentPendingRequestPayloadKeys(AgentPendingRequestKind.OPEN_PROBLEM),
        )
        assertEquals(
            setOf("libraryProblemId"),
            agentPendingRequestPayloadKeys(AgentPendingRequestKind.ADD_TO_REVIEW_PLAN),
        )
        assertEquals(
            setOf("templateId", "layout"),
            agentPendingRequestPayloadKeys(AgentPendingRequestKind.START_EXPORT),
        )
    }

    // --- START_EXPORT（4B B3-1）：模型只能提模板名 + 版式参数，越界/缺字段不挂卡 ---

    @Test
    fun anExportRequestWithADeclaredLayoutIsAdmittedWithACompleteProposal() {
        val admission = tutorLocalActionAdmission(
            request = TutorLocalActionRequest(
                action = TutorLocalAction.START_EXPORT,
                parameters = mapOf(
                    "templateId" to "practice_sheet",
                    "columnCount" to "2",
                    "fontScale" to "3",
                    "includeAnswer" to "false",
                ),
            ),
            context = TutorLocalActionContext(),
        )

        assertNotNull(admission)
        assertEquals(AgentPendingRequestKind.START_EXPORT, admission!!.kind)
        assertEquals(TutorLocalActionTarget.EXPORT_SHEET, admission.target)
        assertEquals(
            MistakePdfLayout(
                templateId = MistakePdfLayout.TEMPLATE_PRACTICE_SHEET,
                fontScale = 3,
                columnCount = 2,
            ),
            tutorLocalActionExportProposal(admission.payloadJson),
        )
    }

    @Test
    fun anExportRequestWithoutTemplateOrWithOutOfRangeValuesHangsNoCard() {
        // 缺 templateId：不替模型挑默认模板（必填项缺失 = 这条请求不成立）。
        assertNull(
            tutorLocalActionAdmission(
                request = TutorLocalActionRequest(
                    action = TutorLocalAction.START_EXPORT,
                    parameters = mapOf("columnCount" to "2"),
                ),
                context = TutorLocalActionContext(),
            ),
        )
        // 模板枚举外：拒。
        assertNull(
            tutorLocalActionAdmission(
                request = TutorLocalActionRequest(
                    action = TutorLocalAction.START_EXPORT,
                    parameters = mapOf("templateId" to "poster"),
                ),
                context = TutorLocalActionContext(),
            ),
        )
        // 越界（fontScale=9）：拒。
        assertNull(
            tutorLocalActionAdmission(
                request = TutorLocalActionRequest(
                    action = TutorLocalAction.START_EXPORT,
                    parameters = mapOf("templateId" to "compact", "fontScale" to "9"),
                ),
                context = TutorLocalActionContext(),
            ),
        )
        // 非数字 / 非布尔：拒（不静默按默认值处理）。
        assertNull(
            tutorLocalActionAdmission(
                request = TutorLocalActionRequest(
                    action = TutorLocalAction.START_EXPORT,
                    parameters = mapOf("templateId" to "compact", "marginPt" to "wide"),
                ),
                context = TutorLocalActionContext(),
            ),
        )
        assertNull(
            tutorLocalActionAdmission(
                request = TutorLocalActionRequest(
                    action = TutorLocalAction.START_EXPORT,
                    parameters = mapOf("templateId" to "compact", "includeAnswer" to "yes"),
                ),
                context = TutorLocalActionContext(),
            ),
        )
        // 未声明的键（例如题 id）：形状层就拒——"模型可写题 id"的通道不存在。
        assertNull(
            tutorLocalActionAdmission(
                request = TutorLocalActionRequest(
                    action = TutorLocalAction.START_EXPORT,
                    parameters = mapOf("templateId" to "compact", "problemIds" to "p1"),
                ),
                context = TutorLocalActionContext(),
            ),
        )
    }

    @Test
    fun everyDeclaredExportParameterNameIsUnderstoodByTheLocalParser() {
        // 声明（core:model）与解析（core:domain）必须逐字同名：这里用声明集合构造一份全参数，
        // 解析成功且每个字段都落到布局上——任一侧改名，这条用例先红。
        val parameters = mapOf(
            "templateId" to MistakePdfLayout.TEMPLATE_PRACTICE_SHEET,
            "columnCount" to "2",
            "fontScale" to "3",
            "marginPt" to "60",
            "imageScale" to "0.8",
            "blockOrder" to "paragraph,choice_group",
            "includeAnswer" to "true",
            "includeSolution" to "true",
            "includeNote" to "true",
        )
        assertEquals(
            TutorLocalAction.START_EXPORT.parameters.map { it.parameterName }.toSet(),
            parameters.keys,
        )

        val layout = exportLayoutFromActionParameters(parameters)

        assertNotNull(layout)
        assertEquals(MistakePdfLayout.TEMPLATE_PRACTICE_SHEET, layout!!.templateId)
        assertEquals(2, layout.columnCount)
        assertEquals(3, layout.fontScale)
        assertEquals(60, layout.marginPt)
        assertEquals(0.8f, layout.imageScale)
        assertEquals(listOf("paragraph", "choice_group"), layout.blockOrder)
        assertEquals(true, layout.includeAnswer)
        assertEquals(true, layout.includeSolution)
        assertEquals(true, layout.includeNote)
    }

    @Test
    fun theTraceOfAnAskTierToolSpellingBecomesItsKind() {
        // 工具拼写走工具环、ask 档不执行——那一轮留下的唯一信号是痕迹里这条 awaiting_consent。
        val trace = com.tingyun.smartmistakebook.core.model.TutorTurnToolTrace(
            entries = listOf(
                com.tingyun.smartmistakebook.core.model.TutorToolTraceEntry(
                    tool = TutorToolName.NOTEBOOK_WRITE,
                    ok = false,
                    errorKind = com.tingyun.smartmistakebook.core.model.TOOL_AWAITING_CONSENT_ERROR_KIND,
                ),
                com.tingyun.smartmistakebook.core.model.TutorToolTraceEntry(
                    tool = TutorToolName.KNOWLEDGE_READ,
                    ok = true,
                    resultCount = 3,
                ),
            ),
        )

        assertEquals(
            setOf(AgentPendingRequestKind.NOTEBOOK_WRITE),
            awaitingConsentPendingRequestKinds(trace),
        )
        assertEquals(emptySet<AgentPendingRequestKind>(), awaitingConsentPendingRequestKinds(null))
    }
}
