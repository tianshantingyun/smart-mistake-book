package com.tingyun.smartmistakebook.core.data.study

import com.tingyun.smartmistakebook.core.domain.KnowledgeNodeSuccessors
import com.tingyun.smartmistakebook.core.domain.LearningProjector
import com.tingyun.smartmistakebook.core.model.BindingChanged
import com.tingyun.smartmistakebook.core.model.ChatEvidenceSubmitted
import com.tingyun.smartmistakebook.core.model.EvidenceCorrection
import com.tingyun.smartmistakebook.core.model.EvidenceCorrectionKind
import com.tingyun.smartmistakebook.core.model.KnowledgeMasteryState
import com.tingyun.smartmistakebook.core.model.LearningEvidenceDirection
import com.tingyun.smartmistakebook.core.model.LearningLedgerEvent
import com.tingyun.smartmistakebook.core.model.LearningLedgerFingerprint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T2/PRD-02（KD-43）：chat evidence 的**定向纠正/撤销**在纯投影器上的回放语义。
 *
 * 消灭的失败（AUD-11 §3）：错键落下的证据没有任何补偿/替代/版本化落点，重放只能确定性
 * 复现错判；若把纠错写成"在修复日追加一笔答对"，掌握度会在错误的时间 +1 次。
 *
 * 本用例钉住四条语义（对应设计 §3.3）：
 * 1. **撤销 = 从有效证据集合排除**（不是零权重继续推进记忆卡）；
 * 2. **替代 = 按账本序列最终有效替代重算**，且**挂回原作答位置**（不是修复日新增一笔）；
 * 3. `VOID` 指向一条更正 = **撤销更正**（回退到原证据），不是把原证据也撤掉；
 * 4. 禁止循环/悬空引用；同 KC 其他证据与其他 KC 不受误伤；更正不把修复日当成练习日。
 */
class ChatEvidenceCorrectionReplayTest {

    private val projector = LearningProjector()

    @Test
    fun aKeyCorrectionReplacesTheOriginalEvidenceAtItsOriginalLedgerPosition() {
        val original = chatEvidence(
            id = "ev-original",
            sequence = 1,
            direction = LearningEvidenceDirection.NEGATIVE,
            weight = 0.35,
            occurredAt = 1_000,
        )
        val correction = replacement(
            id = "corr-1",
            sequence = 2,
            target = original.evidenceId,
            direction = LearningEvidenceDirection.POSITIVE,
            weight = 0.15,
            occurredAt = 900_000,
        )

        val corrected = replay(original, correction)

        // 位置等价：与"从一开始这条证据就是更正后的方向/权重"逐位一致。
        val positional = replay(original.copy(
            direction = LearningEvidenceDirection.POSITIVE,
            weight = 0.15,
        ))
        assertEquals(positional.snapshot.knowledgeMasteryStates, corrected.snapshot.knowledgeMasteryStates)
        assertEquals(0.15, corrected.snapshot.knowledgeMasteryStates.getValue(NODE).successWeight, 1e-12)
        assertEquals(0.0, corrected.snapshot.knowledgeMasteryStates.getValue(NODE).failureWeight, 1e-12)
        assertEquals(
            "更正挂回原作答时间，不把修复日当成新练习日",
            1_000L,
            corrected.snapshot.knowledgeMasteryStates.getValue(NODE).lastEvidenceAtEpochMillis,
        )
    }

    @Test
    fun aVoidExcludesTheEvidenceInsteadOfIncreasingItWithZeroWeight() {
        val original = chatEvidence(
            id = "ev-original",
            sequence = 1,
            direction = LearningEvidenceDirection.NEGATIVE,
            weight = 0.35,
            occurredAt = 1_000,
        )
        val void = void(
            id = "corr-void",
            sequence = 2,
            target = original.evidenceId,
            occurredAt = 2_000,
        )

        val replayed = replay(original, void)

        assertTrue(
            "撤销 = 从有效证据集合排除：该 KC 不应留下一条零权重证据",
            replayed.snapshot.knowledgeMasteryStates.isEmpty(),
        )
        assertEquals(setOf("corr-void"), replayed.appliedEvidenceCorrectionIds)
    }

    @Test
    fun correctingTwiceUsesTheLastEffectiveReplacementInLedgerOrder() {
        val original = chatEvidence(
            id = "ev-original",
            sequence = 1,
            direction = LearningEvidenceDirection.NEGATIVE,
            weight = 0.35,
            occurredAt = 1_000,
        )
        val first = replacement(
            id = "corr-1",
            sequence = 2,
            target = original.evidenceId,
            direction = LearningEvidenceDirection.POSITIVE,
            weight = 0.15,
            occurredAt = 2_000,
        )
        val second = replacement(
            id = "corr-2",
            sequence = 3,
            target = first.correctionId,
            direction = LearningEvidenceDirection.NEGATIVE,
            weight = 0.35,
            occurredAt = 3_000,
        )

        val replayed = replay(original, first, second)

        assertEquals(
            "最终有效替代按账本序列决定：第二次更正（NEGATIVE 0.35）胜出",
            0.0,
            replayed.snapshot.knowledgeMasteryStates.getValue(NODE).successWeight,
            1e-12,
        )
        assertEquals(0.35, replayed.snapshot.knowledgeMasteryStates.getValue(NODE).failureWeight, 1e-12)
        assertEquals(2, replayed.appliedEvidenceCorrectionIds.size)
    }

    @Test
    fun voidingACorrectionRestoresTheOriginalEvidence() {
        val original = chatEvidence(
            id = "ev-original",
            sequence = 1,
            direction = LearningEvidenceDirection.NEGATIVE,
            weight = 0.35,
            occurredAt = 1_000,
        )
        val correction = replacement(
            id = "corr-1",
            sequence = 2,
            target = original.evidenceId,
            direction = LearningEvidenceDirection.POSITIVE,
            weight = 0.15,
            occurredAt = 2_000,
        )
        // 撤销更正：指向"更正行"的 VOID = 那条更正不再生效（回退到原证据），
        // 不是把原证据也一起撤掉。
        val undo = void(
            id = "corr-undo",
            sequence = 3,
            target = correction.correctionId,
            occurredAt = 3_000,
        )

        val replayed = replay(original, correction, undo)

        assertEquals(
            "撤销更正后回到原证据的方向/权重",
            0.35,
            replayed.snapshot.knowledgeMasteryStates.getValue(NODE).failureWeight,
            1e-12,
        )
        assertEquals(0.0, replayed.snapshot.knowledgeMasteryStates.getValue(NODE).successWeight, 1e-12)
    }

    @Test
    fun correctingOneEvidenceDoesNotHarmOtherEvidenceOnTheSameKnowledgeNode() {
        val first = chatEvidence(
            id = "ev-first",
            sequence = 1,
            direction = LearningEvidenceDirection.NEGATIVE,
            weight = 0.35,
            occurredAt = 1_000,
        )
        val second = chatEvidence(
            id = "ev-second",
            sequence = 2,
            direction = LearningEvidenceDirection.POSITIVE,
            weight = 0.15,
            occurredAt = 2_000,
        )
        val voidFirst = void(
            id = "corr-void-first",
            sequence = 3,
            target = first.evidenceId,
            occurredAt = 3_000,
        )

        val replayed = replay(first, second, voidFirst)
        val onlySecond = replay(second.copy(eventSequence = 1))

        // checkpointSequence 是账本位置（第二笔在两种账本里都是 seq 2 vs seq 1），
        // 不参与“证据是否被误伤”的比较；其余字段逐位一致。
        assertEquals(
            withoutCheckpoint(onlySecond.snapshot.knowledgeMasteryStates),
            withoutCheckpoint(replayed.snapshot.knowledgeMasteryStates),
        )
    }

    @Test
    fun correctingOneKnowledgeNodeDoesNotTouchAnother() {
        val first = chatEvidence(
            id = "ev-first",
            sequence = 1,
            direction = LearningEvidenceDirection.NEGATIVE,
            weight = 0.35,
            occurredAt = 1_000,
        )
        val other = chatEvidence(
            id = "ev-other",
            sequence = 2,
            direction = LearningEvidenceDirection.POSITIVE,
            weight = 0.15,
            occurredAt = 2_000,
            knowledgeNodeId = "kc-other",
        )
        val voidFirst = void(
            id = "corr-void-first",
            sequence = 3,
            target = first.evidenceId,
            occurredAt = 3_000,
        )

        val replayed = replay(first, other, voidFirst)
        val otherOnly = replay(other.copy(eventSequence = 1))

        assertEquals(
            withoutCheckpoint(otherOnly.snapshot.knowledgeMasteryStates),
            withoutCheckpoint(replayed.snapshot.knowledgeMasteryStates),
        )
    }

    @Test
    fun aDanglingOrSelfReferentialCorrectionIsRejectedBeforeItCanChangeAnything() {
        val original = chatEvidence(
            id = "ev-original",
            sequence = 1,
            direction = LearningEvidenceDirection.NEGATIVE,
            weight = 0.35,
            occurredAt = 1_000,
        )

        assertThrows(IllegalArgumentException::class.java) {
            replay(original, void(id = "corr-dangling", sequence = 2, target = "ev-missing"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            replay(
                original,
                void(id = "corr-self", sequence = 2, target = "corr-self"),
            )
        }
    }

    @Test
    fun aCorrectionHasItsOwnCanonicalFingerprintAndDoesNotReuseTheChatEvidenceSchema() {
        val original = chatEvidence(
            id = "ev-original",
            sequence = 1,
            direction = LearningEvidenceDirection.NEGATIVE,
            weight = 0.35,
            occurredAt = 1_000,
        )
        val correction = replacement(
            id = "corr-1",
            sequence = 2,
            target = original.evidenceId,
            direction = LearningEvidenceDirection.POSITIVE,
            weight = 0.15,
            occurredAt = 2_000,
        )

        val chatFingerprint = LearningLedgerFingerprint.chatEvidence(original)
        val correctionFingerprint = LearningLedgerFingerprint.event(correction)

        assertNotEquals(chatFingerprint, correctionFingerprint)
        assertEquals(
            "同一更正的规范指纹必须确定",
            correctionFingerprint,
            LearningLedgerFingerprint.event(correction.copy()),
        )
    }

    @Test
    fun aCorrectionStillAppliesAfterTheKnowledgeNodeWasMergedAway() {
        val original = chatEvidence(
            id = "ev-original",
            sequence = 1,
            direction = LearningEvidenceDirection.NEGATIVE,
            weight = 0.35,
            occurredAt = 1_000,
            knowledgeNodeId = "kc-old",
        )
        val correction = replacement(
            id = "corr-1",
            sequence = 2,
            target = original.evidenceId,
            direction = LearningEvidenceDirection.POSITIVE,
            weight = 0.15,
            occurredAt = 2_000,
        )
        val bindingChange = BindingChanged(
            bindingChangeId = "binding-change-1",
            practiceUnitId = "pu-1",
            previousKnowledgeNodeIds = listOf("kc-old"),
            newKnowledgeNodeIds = listOf("kc-new"),
            occurredAtEpochMillis = 1_500,
            eventSequence = 3,
        )

        val replayed = projector.replay(
            learnerId = LEARNER_ID,
            ledger = listOf(original, correction, bindingChange),
            knowledgeNodeSuccessors = KnowledgeNodeSuccessors(mapOf("kc-old" to "kc-new")),
        )

        assertEquals(
            "改绑后的整本重放同样消费更正：替代效果挂到当前节点",
            0.15,
            replayed.snapshot.knowledgeMasteryStates.getValue("kc-new").successWeight,
            1e-12,
        )
        assertTrue("旧节点不再持有证据", "kc-old" !in replayed.snapshot.knowledgeMasteryStates)
    }

    // ---- fixtures ----

    private fun replay(vararg events: LearningLedgerEvent) =
        projector.replay(learnerId = LEARNER_ID, ledger = events.toList())

    private fun withoutCheckpoint(
        states: Map<String, KnowledgeMasteryState>,
    ): Map<String, KnowledgeMasteryState> = states.mapValues { (_, state) ->
        state.copy(checkpointSequence = 0)
    }

    private fun chatEvidence(
        id: String,
        sequence: Long,
        direction: LearningEvidenceDirection,
        weight: Double,
        occurredAt: Long,
        knowledgeNodeId: String = NODE,
    ) = ChatEvidenceSubmitted(
        evidenceId = id,
        conversationId = "conversation-1",
        knowledgeNodeId = knowledgeNodeId,
        direction = direction,
        weight = weight,
        reasonMarkdown = "知识点复习作答",
        confidence = 1.0,
        occurredAtEpochMillis = occurredAt,
        eventSequence = sequence,
    )

    private fun replacement(
        id: String,
        sequence: Long,
        target: String,
        direction: LearningEvidenceDirection,
        weight: Double,
        occurredAt: Long = 10_000,
    ) = EvidenceCorrection(
        correctionId = id,
        targetEvidenceId = target,
        kind = EvidenceCorrectionKind.REPLACE,
        replacementDirection = direction,
        replacementWeight = weight,
        reasonMarkdown = "答案键更正",
        occurredAtEpochMillis = occurredAt,
        eventSequence = sequence,
    )

    private fun void(
        id: String,
        sequence: Long,
        target: String,
        occurredAt: Long = 10_000,
    ) = EvidenceCorrection(
        correctionId = id,
        targetEvidenceId = target,
        kind = EvidenceCorrectionKind.VOID,
        replacementDirection = LearningEvidenceDirection.NONE,
        replacementWeight = 0.0,
        reasonMarkdown = "撤销该证据",
        occurredAtEpochMillis = occurredAt,
        eventSequence = sequence,
    )

    private companion object {
        const val LEARNER_ID = "learner:local"
        const val NODE = "kc-monotonicity"
    }
}
