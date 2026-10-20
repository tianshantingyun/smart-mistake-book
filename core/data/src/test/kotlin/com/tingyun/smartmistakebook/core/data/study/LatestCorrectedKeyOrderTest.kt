package com.tingyun.smartmistakebook.core.data.study

import com.tingyun.smartmistakebook.core.database.entity.LearnerChatEvidenceEntity
import com.tingyun.smartmistakebook.core.model.LearningEvidenceDirection
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * KD-43：`latestCorrectedKey` 的读侧排序。
 *
 * 消灭的失败：读侧按 `created_at_epoch_millis` 的行序取"最新键"，而投影按**账本序**取；
 * 同毫秒或时钟回拨时两者分叉——屏幕/后续作答用旧键，账本却认为新键已生效。
 *
 * 规则：账本序可用且覆盖全部候选 → 取账本序最后一条；否则**显式降级**为时间序
 * （同毫秒用行序 tie-break）。降级不是"偷偷回退"，是函数 KDoc 写明的已知边界。
 */
class LatestCorrectedKeyOrderTest {

    private val nodeId = "kc-monotonicity"
    private val itemId = "knowledge-quiz:item:kc-monotonicity"

    @Test
    fun `ledger order wins even when wall-clock timestamps disagree`() {
        // 时钟回拨：后一笔更正的时间戳更早。
        val firstByClock = correction(
            id = "key-correction:first",
            createdAt = 200,
            newKey = "C",
        )
        val secondByLedger = correction(
            id = "key-correction:second",
            createdAt = 100,
            newKey = "D",
        )

        val key = latestCorrectedKey(
            rows = listOf(firstByClock, secondByLedger),
            knowledgeNodeId = nodeId,
            itemId = itemId,
            ledgerOrder = mapOf(
                "key-correction:first" to 1L,
                "key-correction:second" to 2L,
            ),
        )

        assertEquals("账本序第二条才是最新有效替代", "D", key)
    }

    @Test
    fun `a voided later correction falls back to the earlier effective key`() {
        val first = correction(id = "key-correction:first", createdAt = 100, newKey = "C")
        val second = correction(id = "key-correction:second", createdAt = 200, newKey = "D")
        val void = LearnerChatEvidenceEntity(
            evidence_id = "key-void:second",
            learner_id = LEARNER_ID,
            conversation_id = CONVERSATION_ID,
            knowledge_node_id = nodeId,
            direction = LearningEvidenceDirection.NONE.name,
            weight = 0.0,
            reason_markdown = "撤销第二次更正",
            confidence = 1.0,
            source_kind = LearnerChatEvidenceEntity.SOURCE_KIND_KEY_VOID,
            created_at_epoch_millis = 300,
            corrects_evidence_id = "key-correction:second",
        )

        val key = latestCorrectedKey(
            rows = listOf(first, second, void),
            knowledgeNodeId = nodeId,
            itemId = itemId,
            ledgerOrder = mapOf(
                "key-correction:first" to 1L,
                "key-correction:second" to 2L,
                "key-void:second" to 3L,
            ),
        )

        assertEquals("被撤销的更正不再是有效替代", "C", key)
    }

    @Test
    fun `without a complete ledger order the reader explicitly degrades to timestamp order`() {
        val older = correction(id = "key-correction:older", createdAt = 100, newKey = "C")
        val newer = correction(id = "key-correction:newer", createdAt = 200, newKey = "D")

        // null = 账本读不完整/不可用（调用方显式传 null）；退化为时间序。
        assertEquals(
            "降级路径：时间戳更晚者胜出",
            "D",
            latestCorrectedKey(
                rows = listOf(older, newer),
                knowledgeNodeId = nodeId,
                itemId = itemId,
                ledgerOrder = null,
            ),
        )

        // 映射不覆盖全部候选（例如账本 GAP）同样按降级处理，不混用两套序。
        assertEquals(
            "账本序不完整时不混用：整段退回时间序",
            "D",
            latestCorrectedKey(
                rows = listOf(older, newer),
                knowledgeNodeId = nodeId,
                itemId = itemId,
                ledgerOrder = mapOf("key-correction:newer" to 1L),
            ),
        )
    }

    @Test
    fun `a user-asserted answer row counts as a key correction`() {
        // UNKNOWN 键的 REPLACE 落成一条普通作答证据（source_kind = KNOWLEDGE_QUIZ），
        // 理由带同一机器前缀；读侧必须把它当作键替代，否则改正后的键不会生效。
        val asserted = LearnerChatEvidenceEntity(
            evidence_id = "knowledge-quiz:req:item:B:$nodeId:key-fix:B",
            learner_id = LEARNER_ID,
            conversation_id = CONVERSATION_ID,
            knowledge_node_id = nodeId,
            direction = LearningEvidenceDirection.POSITIVE.name,
            weight = 0.18,
            reason_markdown = knowledgeQuizKeyCorrectionReasonPrefix(itemId, "B") + "答案键应为 B",
            confidence = OBJECTIVE_ANSWER_CONFIDENCE,
            source_kind = "KNOWLEDGE_QUIZ",
            created_at_epoch_millis = 100,
        )
        val laterDirected = correction(id = "key-correction:later", createdAt = 200, newKey = "C")

        val key = latestCorrectedKey(
            rows = listOf(asserted, laterDirected),
            knowledgeNodeId = nodeId,
            itemId = itemId,
            ledgerOrder = mapOf(
                asserted.evidence_id to 1L,
                "key-correction:later" to 2L,
            ),
        )

        assertEquals("两类来源共用同一条账本序", "C", key)
    }

    private fun correction(
        id: String,
        createdAt: Long,
        newKey: String,
    ) = LearnerChatEvidenceEntity(
        evidence_id = id,
        learner_id = LEARNER_ID,
        conversation_id = CONVERSATION_ID,
        knowledge_node_id = nodeId,
        direction = LearningEvidenceDirection.NONE.name,
        weight = 0.0,
        reason_markdown = knowledgeQuizKeyCorrectionReasonPrefix(itemId, newKey) + "用户更正",
        confidence = 1.0,
        source_kind = LearnerChatEvidenceEntity.SOURCE_KIND_KEY_CORRECTION,
        created_at_epoch_millis = createdAt,
        corrects_evidence_id = "knowledge-quiz:req:item:A:$nodeId",
    )

    private companion object {
        const val LEARNER_ID = "learner:latest-key"
        const val CONVERSATION_ID = "knowledge-quiz-review:5000"
    }
}
