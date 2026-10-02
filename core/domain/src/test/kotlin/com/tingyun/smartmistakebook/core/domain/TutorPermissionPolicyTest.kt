package com.tingyun.smartmistakebook.core.domain

import com.tingyun.smartmistakebook.core.model.TutorLocalAction
import com.tingyun.smartmistakebook.core.model.TutorToolName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 权限三档（规格 §3.3）。这一份测的是**档位表本身**：每个对象在哪一档、需要确认卡的条件、
 * 以及"档位不随入口变"这条纪律（策略签名里根本没有入口参数——编译器替我们守住它）。
 */
class TutorPermissionPolicyTest {
    private val allTools = TutorToolName.entries.map(TutorPermissionSubject::Tool)
    private val allActions = TutorLocalAction.entries.map(TutorPermissionSubject::LocalAction)

    @Test
    fun everyToolAndActionHasATierFromTheSettledTable() {
        val tiers = (allTools + allActions).associateWith(::tutorPermissionTier)

        assertEquals(
            TutorPermissionTier.ALLOW,
            tiers[TutorPermissionSubject.Tool(TutorToolName.KNOWLEDGE_READ)],
        )
        assertEquals(
            TutorPermissionTier.ALLOW,
            tiers[TutorPermissionSubject.Tool(TutorToolName.NOTEBOOK_READ)],
        )
        assertEquals(
            TutorPermissionTier.ALLOW,
            tiers[TutorPermissionSubject.Tool(TutorToolName.MASTERY_READ)],
        )
        // D-M M7：咨询读数按读工具档（自动执行、行为可见）。
        assertEquals(
            TutorPermissionTier.ALLOW,
            tiers[TutorPermissionSubject.Tool(TutorToolName.ADVISORY_READ)],
        )
        assertEquals(
            TutorPermissionTier.ASK,
            tiers[TutorPermissionSubject.Tool(TutorToolName.NOTEBOOK_WRITE)],
        )
        assertEquals(
            TutorPermissionTier.AUTO_VISIBLE,
            tiers[TutorPermissionSubject.Tool(TutorToolName.MASTERY_UPDATE)],
        )
        // D-M M7：咨询写入与 MASTERY_UPDATE 同档——全自动、行为可见、被拒给理由；
        // "任何轮次"由档位不随上下文翻转保证（不是按轮次分叉）。
        assertEquals(
            TutorPermissionTier.AUTO_VISIBLE,
            tiers[TutorPermissionSubject.Tool(TutorToolName.ADVISORY_WRITE)],
        )
        allActions.forEach { subject ->
            assertEquals("$subject must be an ask-tier object", TutorPermissionTier.ASK, tiers[subject])
        }
        // 每一个工具都有一档（when 是穷尽的，这里只是把"没有漏网对象"钉在测试里）。
        assertEquals(TutorToolName.entries.size, allTools.mapNotNull(tiers::get).size)
    }

    /**
     * 档位是**对象的属性**：本轮上下文只改"放不放行"，不改"在哪一档"。
     * 这条断言就是"不要按入口/栏分叉"的可执行版本——上下文里没有栏。
     */
    @Test
    fun roundContextChangesAdmissionButNeverTheTier() {
        val subject = TutorPermissionSubject.Tool(TutorToolName.NOTEBOOK_WRITE)

        val admitted = tutorPermissionDecision(
            subject,
            TutorRoundPermissionContext(),
        )
        val refused = tutorPermissionDecision(
            subject,
            TutorRoundPermissionContext(allowedTools = emptySet()),
        )

        assertEquals(TutorPermissionTier.ASK, admitted.tier)
        assertEquals(TutorPermissionTier.ASK, refused.tier)
        assertTrue(admitted.admitted)
        assertFalse(refused.admitted)
    }

    @Test
    fun onlyAnAdmittedAskSubjectNeedsAConfirmationCard() {
        assertTrue(
            tutorPermissionDecision(
                TutorPermissionSubject.LocalAction(TutorLocalAction.START_EXPORT),
                TutorRoundPermissionContext(),
            ).requiresConsentCard,
        )
        // 没准入 = 连卡都不出现（模型这次调用被拒，不是"学生还没点"）。
        assertFalse(
            tutorPermissionDecision(
                TutorPermissionSubject.LocalAction(TutorLocalAction.START_EXPORT),
                TutorRoundPermissionContext(allowedActions = emptySet()),
            ).requiresConsentCard,
        )
        // 读工具与掌握库写入都不挂卡：前者自动执行，后者全自动、不需批准（被拒也要可见）。
        assertFalse(
            tutorPermissionDecision(
                TutorPermissionSubject.Tool(TutorToolName.MASTERY_UPDATE),
                TutorRoundPermissionContext(),
            ).requiresConsentCard,
        )
        assertFalse(
            tutorPermissionDecision(
                TutorPermissionSubject.Tool(TutorToolName.KNOWLEDGE_READ),
                TutorRoundPermissionContext(),
            ).requiresConsentCard,
        )
    }

    @Test
    fun onlyTheFiveAskObjectsMapToAPendingRequestKind() {
        assertEquals(
            AgentPendingRequestKind.NOTEBOOK_WRITE,
            agentPendingRequestKind(TutorPermissionSubject.Tool(TutorToolName.NOTEBOOK_WRITE)),
        )
        assertEquals(
            setOf(
                AgentPendingRequestKind.OPEN_PROBLEM,
                AgentPendingRequestKind.SAVE_TO_NOTEBOOK,
                AgentPendingRequestKind.START_EXPORT,
                AgentPendingRequestKind.ADD_TO_REVIEW_PLAN,
            ),
            allActions.mapNotNull(::agentPendingRequestKind).toSet(),
        )
        // 不挂卡的对象没有 kind——行不该为它们存在。
        assertNull(agentPendingRequestKind(TutorPermissionSubject.Tool(TutorToolName.MASTERY_UPDATE)))
        assertNull(agentPendingRequestKind(TutorPermissionSubject.Tool(TutorToolName.KNOWLEDGE_READ)))
        assertNull(agentPendingRequestKind(TutorPermissionSubject.Tool(TutorToolName.NOTEBOOK_READ)))
        assertNull(agentPendingRequestKind(TutorPermissionSubject.Tool(TutorToolName.MASTERY_READ)))
        assertEquals(
            "Ask-tier objects and pending-request kinds must be the same five",
            5,
            (allTools + allActions).count { agentPendingRequestKind(it) != null },
        )
    }
}
