package com.tingyun.smartmistakebook.core.data.study

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D-M M4：幂等证据 id 规则**单源**在 [knowledgeEvidenceId]（两条写通道共用一条规则）。
 * 本套用例锁规则本身：确定性、命名空间/通道/节点隔离、无命名空间时的唯一回退、
 * 以及既有行形状（讲题/测验各自的拼法）不被改造破坏。
 */
class MasteryUpdateEvidenceIdTest {

    @Test
    fun `same namespace channel discriminator and kc derive the same id`() {
        val first = knowledgeEvidenceId(
            namespace = "request-1",
            channel = KnowledgeEvidenceChannel.MODEL_CHAT,
            knowledgeNodeId = "node-algebra",
            discriminator = "MASTERY_UPDATE",
        )
        val second = knowledgeEvidenceId(
            namespace = "request-1",
            channel = KnowledgeEvidenceChannel.MODEL_CHAT,
            knowledgeNodeId = "node-algebra",
            discriminator = "MASTERY_UPDATE",
        )
        assertEquals(first, second)
        assertEquals("chat-ev:request-1:MASTERY_UPDATE:node-algebra", first)
    }

    @Test
    fun `different kc namespace channel or discriminator derives a different id`() {
        val base = knowledgeEvidenceId(
            "request-1",
            KnowledgeEvidenceChannel.MODEL_CHAT,
            "node-algebra",
            "MASTERY_UPDATE",
        )
        assertNotEquals(
            base,
            knowledgeEvidenceId(
                "request-1",
                KnowledgeEvidenceChannel.MODEL_CHAT,
                "node-geometry",
                "MASTERY_UPDATE",
            ),
        )
        assertNotEquals(
            base,
            knowledgeEvidenceId(
                "request-2",
                KnowledgeEvidenceChannel.MODEL_CHAT,
                "node-algebra",
                "MASTERY_UPDATE",
            ),
        )
        assertNotEquals(
            base,
            knowledgeEvidenceId(
                "request-1",
                KnowledgeEvidenceChannel.MODEL_CHAT,
                "node-algebra",
                "KNOWLEDGE_READ",
            ),
        )
        assertNotEquals(
            base,
            knowledgeEvidenceId("request-1", KnowledgeEvidenceChannel.KNOWLEDGE_QUIZ, "node-algebra"),
        )
    }

    @Test
    fun `knowledge quiz ids keep their existing row shape`() {
        assertEquals(
            "knowledge-quiz:req-correct:kc-monotonicity",
            knowledgeEvidenceId("req-correct", KnowledgeEvidenceChannel.KNOWLEDGE_QUIZ, "kc-monotonicity"),
        )
    }

    @Test
    fun `null namespace falls back to a unique non idempotent id`() {
        val a = knowledgeEvidenceId(
            null,
            KnowledgeEvidenceChannel.MODEL_CHAT,
            "node-algebra",
            "MASTERY_UPDATE",
        )
        val b = knowledgeEvidenceId(
            null,
            KnowledgeEvidenceChannel.MODEL_CHAT,
            "node-algebra",
            "MASTERY_UPDATE",
        )
        // nanoTime fallback must still be non-blank and prefixed.
        assertTrue(a.startsWith("chat-ev-"))
        assertTrue(b.startsWith("chat-ev-"))
        assertNotEquals(a, b)
    }

    @Test
    fun `blank namespace also falls back to a unique id for the quiz channel`() {
        // 空 requestId 不构成命名空间：确定性 id 对它没有意义（同 id 会跨"不同调用"撞键）。
        val a = knowledgeEvidenceId("", KnowledgeEvidenceChannel.KNOWLEDGE_QUIZ, "kc-a")
        val b = knowledgeEvidenceId(" ", KnowledgeEvidenceChannel.KNOWLEDGE_QUIZ, "kc-a")
        assertTrue(a.startsWith("knowledge-quiz-"))
        assertTrue(b.startsWith("knowledge-quiz-"))
        assertNotEquals(a, b)
    }

    @Test
    fun `derived id stays within the chat evidence id budget`() {
        val id = knowledgeEvidenceId(
            "request-1",
            KnowledgeEvidenceChannel.MODEL_CHAT,
            "node-algebra",
            "MASTERY_UPDATE",
        )
        // evidence_id is the Room PK of learner_chat_evidence — keep it short enough
        // for the TEXT PK and any outbox id composition.
        assertTrue("derived id length ${id.length}", id.length <= 120)
    }
}
