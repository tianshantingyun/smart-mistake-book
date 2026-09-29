package com.tingyun.smartmistakebook.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 确认卡的执行路由与 payload（A4）。
 *
 * 判别格：**三条路径各自被选中一次**（拍照草稿 / 附图 / 错题本）、**没有目标不挂卡**、
 * **尚未接线的动作不假装执行**、**payload 是固定形状且空载体不落键**（进程死亡后重建/执行
 * 都靠它，所以它的形状必须有测试钉住）。
 */
class TutorLocalActionExecutionTest {

    @Test
    fun aCaptureSessionWinsBecauseItIsTheMostSpecificSaveTarget() {
        val context = TutorLocalActionContext(
            captureSessionId = "tutor-session-1",
            libraryProblemId = "problem-1",
            attachedImageAssetIds = listOf("asset-1"),
        )

        assertEquals(
            TutorLocalActionTarget.SAVE_CAPTURE_DRAFT,
            tutorLocalActionTarget(AgentPendingRequestKind.SAVE_TO_NOTEBOOK, context),
        )
        // 工具拼写（NOTEBOOK_WRITE）与动作拼写是同一件事，路由必须同一条。
        assertEquals(
            TutorLocalActionTarget.SAVE_CAPTURE_DRAFT,
            tutorLocalActionTarget(AgentPendingRequestKind.NOTEBOOK_WRITE, context),
        )
    }

    @Test
    fun attachedImagesRouteTheSaveThroughTheIntakePipeline() {
        val context = TutorLocalActionContext(attachedImageAssetIds = listOf("asset-1", "asset-2"))

        assertEquals(
            TutorLocalActionTarget.INTAKE_ATTACHED_IMAGES,
            tutorLocalActionTarget(AgentPendingRequestKind.SAVE_TO_NOTEBOOK, context),
        )
    }

    @Test
    fun anAlreadySavedProblemRoutesToTheNotebookDetail() {
        val context = TutorLocalActionContext(libraryProblemId = "problem-1")

        assertEquals(
            TutorLocalActionTarget.LIBRARY_PROBLEM_DETAIL,
            tutorLocalActionTarget(AgentPendingRequestKind.SAVE_TO_NOTEBOOK, context),
        )
        assertEquals(
            TutorLocalActionTarget.LIBRARY_PROBLEM_DETAIL,
            tutorLocalActionTarget(AgentPendingRequestKind.OPEN_PROBLEM, context),
        )
        // 连题都没有的"打开某题"仍然打开错题本：真动作，不是空操作。
        assertEquals(
            TutorLocalActionTarget.LIBRARY_PROBLEM_DETAIL,
            tutorLocalActionTarget(
                AgentPendingRequestKind.OPEN_PROBLEM,
                TutorLocalActionContext(),
            ),
        )
    }

    @Test
    fun actionsThatHaveNoWiringYetAreNotPretendedToExecute() {
        val context = TutorLocalActionContext(
            captureSessionId = "tutor-session-1",
            attachedImageAssetIds = listOf("asset-1"),
        )

        // 尚未接通的两件仍然是**真实的一张卡**（学生同意/拒绝都是真实决定），只是执行那一步
        // 还没有落点——它们走 NOT_WIRED_YET，由执行方如实说"还不能自动做"（不假装成功）。
        assertEquals(
            TutorLocalActionTarget.NOT_WIRED_YET,
            tutorLocalActionTarget(AgentPendingRequestKind.START_EXPORT, context),
        )
        assertEquals(
            TutorLocalActionTarget.NOT_WIRED_YET,
            tutorLocalActionTarget(AgentPendingRequestKind.ADD_TO_REVIEW_PLAN, context),
        )
    }

    @Test
    fun anEmptyContextIsKnownToBeEmpty() {
        assertTrue(TutorLocalActionContext().isEmpty)
        assertFalse(TutorLocalActionContext(attachedImageAssetIds = listOf("asset-1")).isEmpty)
    }

    @Test
    fun thePayloadCarriesEveryTargetAndNoEmptyCarrier() {
        val filled = TutorLocalActionContext(
            captureSessionId = "tutor-session-1",
            libraryProblemId = "problem-1",
            attachedImageAssetIds = listOf("asset-1", "asset-2"),
        ).toAgentPendingRequestPayload()

        assertTrue(filled.startsWith("{"))
        assertTrue(filled.endsWith("}"))
        assertEquals(filled, tutorLocalActionContext(filled).toAgentPendingRequestPayload())
        val roundTripped = tutorLocalActionContext(filled)
        assertEquals("tutor-session-1", roundTripped.captureSessionId)
        assertEquals("problem-1", roundTripped.libraryProblemId)
        assertEquals(listOf("asset-1", "asset-2"), roundTripped.attachedImageAssetIds)

        // 空载体不落键：只有图的那张卡不该凭空多出两个 null 键（同一条纪律：指纹/行都不因
        // "多了一个空键"而变）。
        val imagesOnly = TutorLocalActionContext(attachedImageAssetIds = listOf("asset-1"))
            .toAgentPendingRequestPayload()
        assertFalse(imagesOnly.contains("captureSessionId"))
        assertFalse(imagesOnly.contains("libraryProblemId"))
        assertEquals(
            TutorLocalActionContext(attachedImageAssetIds = listOf("asset-1")),
            tutorLocalActionContext(imagesOnly),
        )
    }

    @Test
    fun aCorruptedPayloadDegradesToAnEmptyContextInsteadOfCrashingTheRebuild() {
        // 进程死亡后重建那张卡时读的就是这列：坏行只该让这张卡没有目标（执行时说"没执行"），
        // 不该让整个页面崩掉。
        assertEquals(TutorLocalActionContext(), tutorLocalActionContext("not json"))
        assertEquals(TutorLocalActionContext(), tutorLocalActionContext("{}"))
    }

    @Test
    fun resolvedRowsBecomeFeedbackRecordsWithTheSameTextTheStudentSaw() {
        val resolved = AgentPendingRequest(
            requestId = "agent-req:op-1:SAVE_TO_NOTEBOOK:0000000000000001",
            conversationArea = TutorConversationAreas.AGENT,
            conversationId = "conv-1",
            logicalOperationId = "op-1",
            messageId = "message-1",
            kind = AgentPendingRequestKind.SAVE_TO_NOTEBOOK,
            payloadJson = """{"imageAssetIds":["asset-1"]}""",
            status = AgentPendingRequestStatus.ACCEPTED,
            createdAtEpochMillis = 10,
            resolvedAtEpochMillis = 20,
            resolutionNote = "已经放进录入，稍后可以在录入界面继续处理这张图。",
        )
        val stillPending = resolved.copy(
            requestId = "agent-req:op-2:SAVE_TO_NOTEBOOK:0000000000000002",
            logicalOperationId = "op-2",
            messageId = "message-2",
            status = AgentPendingRequestStatus.PENDING,
            resolvedAtEpochMillis = null,
            resolutionNote = null,
        )

        val records = listOf(resolved, stillPending).toLocalActionOutcomeRecords(limit = 8)

        assertEquals(1, records.size)
        assertEquals("SAVE_TO_NOTEBOOK", records.single().kind)
        assertEquals("ACCEPTED", records.single().decision)
        assertEquals(resolved.resolutionNote, records.single().detail)
    }
}
