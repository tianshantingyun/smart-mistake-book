package com.tingyun.smartmistakebook.core.domain

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
        // 每个 kind 允许的键：存题三种目标、打开题/复习计划只认"哪道题"、导出不接受参数。
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
            emptySet<String>(),
            agentPendingRequestPayloadKeys(AgentPendingRequestKind.START_EXPORT),
        )
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
