package com.tingyun.smartmistakebook.feature.review

import com.tingyun.smartmistakebook.core.domain.AdaptiveClarificationCandidate
import com.tingyun.smartmistakebook.core.domain.AdaptiveClarificationRequest
import com.tingyun.smartmistakebook.core.domain.AdaptiveTargetKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * KD-67 产品面：`CLARIFY_OR_CONFIRM` 的确认卡状态机（feature/review 消费者）。
 *
 * 消灭的失败：
 * - 选择器算出"目标上的题面还没核验"后，**没有任何消费方**——决策被算出来就丢掉，
 *   学生看不到原因、也没有确认/换题/取消的出口（KD-67 死端之二）；
 * - 同一决策在重组/重放时会重复回调消费者，若每次都重置卡片，学生正在点的"确认"
 *   会被冲掉、忙态丢失（接口明文要求实现方幂等）；
 * - 若卡片允许确认一个不在候选列表里的题面，确认结果会落到另一道题上。
 */
class KnowledgeClarificationPresenterTest {

    private fun candidate(id: String, family: String = "saved-question:$id") =
        AdaptiveClarificationCandidate(
            assessmentItemId = id,
            itemFamilyId = family,
            knowledgeNodeIds = setOf("kc-1"),
        )

    private fun request(
        candidates: List<AdaptiveClarificationCandidate> = listOf(candidate("item-a")),
        reasonCodes: Set<String> = setOf("TARGET_CONTENT_NOT_VERIFIED"),
        decisionAtEpochMillis: Long = 1_000L,
    ) = AdaptiveClarificationRequest(
        learnerId = "learner:local",
        targetKnowledgeNodeIds = setOf("kc-1"),
        requestedTargetKind = AdaptiveTargetKind.FOUNDATION,
        candidates = candidates,
        reasonCodes = reasonCodes,
        selectorVersion = "adaptive-selector-v1",
        decisionAtEpochMillis = decisionAtEpochMillis,
    )

    @Test
    fun `a clarify decision opens the card with its candidates and reasons`() = runBlocking {
        val presenter = KnowledgeClarificationPresenter()

        presenter.onClarificationRequired(request())

        val state = presenter.state.value as KnowledgeClarificationState.Asking
        assertEquals(request(), state.request)
        assertFalse("新打开的卡片不是忙态", state.busy)
        assertEquals(null, state.error)
        val model = knowledgeClarificationCardModel(state.request)
        assertEquals(listOf("item-a"), model.candidates.map { it.assessmentItemId })
        assertEquals(
            listOf("这道题的答案还没有经过独立核对，这次只记录你的选择，不计入掌握度。"),
            model.reasonLines,
        )
    }

    @Test
    fun `an unknown reason code degrades to an honest generic line instead of leaking the code`() {
        val model = knowledgeClarificationCardModel(request(reasonCodes = setOf("SOME_NEW_CODE")))

        assertEquals(listOf("这道题暂时不能直接用于复习。"), model.reasonLines)
        assertFalse(model.reasonLines.joinToString().contains("SOME_NEW_CODE"))
    }

    @Test
    fun `every known clarify reason maps to a student readable line`() {
        val codes = setOf(
            "TARGET_CONTENT_NOT_VERIFIED",
            "PREDICTION_UNKNOWN_STALE_OR_OUTSIDE_CORRIDOR",
            "LEARNING_EVIDENCE_NOT_CURRENT",
            "PROJECTION_NOT_CURRENT",
        )
        val model = knowledgeClarificationCardModel(request(reasonCodes = codes))

        assertEquals("每个原因一行，且都翻成人话", codes.size, model.reasonLines.size)
        model.reasonLines.forEach { line ->
            assertTrue("原因行不得是裸枚举：$line", line !in codes)
            assertFalse("原因行必须是人话句子：$line", line.all { it.isDigit() || it == '_' })
        }
    }

    @Test
    fun `a resolved decision is not reopened by the same request replay`() = runBlocking {
        val presenter = KnowledgeClarificationPresenter()
        presenter.onClarificationRequired(request())
        assertTrue(presenter.beginConfirm("item-a"))
        presenter.completeConfirm()

        presenter.onClarificationRequired(request())

        assertEquals(
            KnowledgeClarificationState.Resolved(KnowledgeClarificationOutcome.CONFIRMED),
            presenter.state.value,
        )
    }

    @Test
    fun `a replayed request while the card is busy keeps the busy state`() = runBlocking {
        val presenter = KnowledgeClarificationPresenter()
        presenter.onClarificationRequired(request())
        assertTrue(presenter.beginConfirm("item-a"))

        presenter.onClarificationRequired(request())

        val state = presenter.state.value as KnowledgeClarificationState.Asking
        assertTrue("重放不得把忙态冲掉", state.busy)
        assertFalse("忙态中的重复确认必须被拒", presenter.beginConfirm("item-a"))
    }

    @Test
    fun `confirming a candidate outside the request is refused`() = runBlocking {
        val presenter = KnowledgeClarificationPresenter()
        presenter.onClarificationRequired(request())

        assertFalse(presenter.beginConfirm("item-other"))
        assertEquals(
            KnowledgeClarificationState.Asking(request()),
            presenter.state.value,
        )
    }

    @Test
    fun `a failed confirmation returns to asking with a visible reason`() = runBlocking {
        val presenter = KnowledgeClarificationPresenter()
        presenter.onClarificationRequired(request())
        assertTrue(presenter.beginConfirm("item-a"))

        presenter.failConfirm("这次确认没有写下来，请再试一次。")

        val state = presenter.state.value as KnowledgeClarificationState.Asking
        assertFalse(state.busy)
        assertEquals("这次确认没有写下来，请再试一次。", state.error)
        assertEquals(request(), state.request)
        assertTrue("失败后允许重试", presenter.beginConfirm("item-a"))
    }

    @Test
    fun `a different decision opens the card even after one was resolved`() = runBlocking {
        val presenter = KnowledgeClarificationPresenter()
        presenter.onClarificationRequired(request())
        presenter.finish(KnowledgeClarificationOutcome.CANCELLED)

        presenter.onClarificationRequired(request(decisionAtEpochMillis = 2_000L))

        val state = presenter.state.value as KnowledgeClarificationState.Asking
        assertEquals(2_000L, state.request.decisionAtEpochMillis)
    }

    @Test
    fun `dismissing the card records the chosen outcome without confirming anything`() = runBlocking {
        val presenter = KnowledgeClarificationPresenter()
        presenter.onClarificationRequired(request())

        presenter.finish(KnowledgeClarificationOutcome.NEW_QUESTION)

        assertEquals(
            KnowledgeClarificationState.Resolved(KnowledgeClarificationOutcome.NEW_QUESTION),
            presenter.state.value,
        )
        assertNotEquals(
            KnowledgeClarificationOutcome.CONFIRMED,
            (presenter.state.value as KnowledgeClarificationState.Resolved).outcome,
        )
    }

    @Test
    fun `the card exposes the selector version so a stale card can be told apart`() = runBlocking {
        val presenter = KnowledgeClarificationPresenter()
        presenter.onClarificationRequired(request())

        val model = knowledgeClarificationCardModel(
            (presenter.state.value as KnowledgeClarificationState.Asking).request,
        )

        assertEquals("adaptive-selector-v1", model.selectorVersion)
        assertFalse("候选列表不得为空", model.candidates.isEmpty())
    }

    @Test
    fun `a clarify decision that is not about an unverified key offers no confirmation`() = runBlocking {
        // 选择器对"证据过期/预测越界"等原因同样返回 CLARIFY_OR_CONFIRM；那些原因下
        // "确认答案"不是解法——卡不能承诺确认后就能出题。
        val presenter = KnowledgeClarificationPresenter()
        presenter.onClarificationRequired(
            request(reasonCodes = setOf("PREDICTION_UNKNOWN_STALE_OR_OUTSIDE_CORRIDOR")),
        )

        assertFalse(presenter.beginConfirm("item-a"))
        val model = knowledgeClarificationCardModel(
            (presenter.state.value as KnowledgeClarificationState.Asking).request,
        )
        assertFalse("非键未核验原因不得出现确认动作", model.confirmable)
    }

    @Test
    fun `a key unverified decision is confirmable`() = runBlocking {
        val presenter = KnowledgeClarificationPresenter()
        presenter.onClarificationRequired(request())

        val model = knowledgeClarificationCardModel(
            (presenter.state.value as KnowledgeClarificationState.Asking).request,
        )

        assertTrue(model.confirmable)
        assertTrue(presenter.beginConfirm("item-a"))
    }

    @Test
    fun `only the on-screen question can be confirmed`() {
        val unverified = request()
        assertEquals(
            "屏幕上这道题在候选里 → 可以确认",
            "item-a",
            knowledgeClarificationConfirmCandidateId(unverified, currentItemId = "item-a"),
        )
        assertEquals(
            "屏幕上是另一道题 → 不给确认（旧候选不能替新题确认）",
            null,
            knowledgeClarificationConfirmCandidateId(unverified, currentItemId = "item-other"),
        )
        assertEquals(
            "没有题面（还在加载）→ 不给确认",
            null,
            knowledgeClarificationConfirmCandidateId(unverified, currentItemId = null),
        )
        assertEquals(
            "非键未核验原因 → 不给确认",
            null,
            knowledgeClarificationConfirmCandidateId(
                request(reasonCodes = setOf("PROJECTION_NOT_CURRENT")),
                currentItemId = "item-a",
            ),
        )
    }

    @Test
    fun `the state flow is observable by the screen`() = runBlocking {
        val presenter = KnowledgeClarificationPresenter()
        presenter.onClarificationRequired(request())

        val observed = presenter.state.first()

        assertTrue(observed is KnowledgeClarificationState.Asking)
    }
}
