package com.tingyun.smartmistakebook.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TutorTurnSendStateMachineTest {
    /** 一整轮走到"已派发、正在生成"的公共路径。 */
    private fun dispatching(
        logicalOperationId: String,
        messageId: String,
    ): TutorSendState = TutorTurnSendStateMachine.reduce(
        TutorTurnSendStateMachine.reduce(
            TutorSendState(),
            TutorSendAction.StudentMessagePersisted(logicalOperationId, messageId),
        ),
        TutorSendAction.ConsentGranted(logicalOperationId, messageId),
    )

    @Test
    fun aNewStudentMessageAfterAFailureOpensTheNextRound() {
        val failed = TutorTurnSendStateMachine.reduce(
            dispatching("logical-1", "message-1"),
            TutorSendAction.DispatchFailed(
                "logical-1",
                "message-1",
                errorCode = "TIMEOUT",
                retryable = true,
            ),
        )
        assertEquals(TutorSendPhase.RETRYABLE_FAILURE, failed.phase)

        val nextRound = TutorTurnSendStateMachine.reduce(
            failed,
            TutorSendAction.StudentMessagePersisted("logical-2", "message-2"),
        )

        assertEquals(TutorSendPhase.PERSISTING, nextRound.phase)
        assertEquals("logical-2", nextRound.logicalOperationId)
        assertEquals("message-2", nextRound.messageId)
        assertNull(nextRound.errorCode)
    }

    @Test
    fun aLateResultOfASupersededRoundIsIgnored() {
        val nextRound = TutorTurnSendStateMachine.reduce(
            TutorTurnSendStateMachine.reduce(
                dispatching("logical-1", "message-1"),
                TutorSendAction.DispatchFailed(
                    "logical-1",
                    "message-1",
                    errorCode = "TIMEOUT",
                    retryable = true,
                ),
            ),
            TutorSendAction.StudentMessagePersisted("logical-2", "message-2"),
        )
        val dispatchingNextRound = TutorTurnSendStateMachine.reduce(
            nextRound,
            TutorSendAction.ConsentGranted("logical-2", "message-2"),
        )

        listOf(
            TutorSendAction.DispatchFailed(
                "logical-1",
                "message-1",
                errorCode = "TIMEOUT",
                retryable = false,
            ),
            TutorSendAction.DispatchSucceeded("logical-1", "message-1"),
            TutorSendAction.DispatchStarted("logical-1", "message-1"),
        ).forEach { lateResult ->
            val after = TutorTurnSendStateMachine.reduce(dispatchingNextRound, lateResult)
            assertEquals(dispatchingNextRound, after)
        }
        assertEquals(TutorSendPhase.DISPATCHING, dispatchingNextRound.phase)
        assertEquals("logical-2", dispatchingNextRound.logicalOperationId)
    }

    @Test
    fun aLateResultAfterAStopIsIgnored() {
        val stopped = TutorTurnSendStateMachine.reduce(
            dispatching("logical-1", "message-1"),
            TutorSendAction.Reset,
        )
        assertEquals(TutorSendState(), stopped)

        val afterLateFailure = TutorTurnSendStateMachine.reduce(
            stopped,
            TutorSendAction.DispatchFailed(
                "logical-1",
                "message-1",
                errorCode = "TIMEOUT",
                retryable = false,
            ),
        )

        assertEquals(TutorSendState(), afterLateFailure)
    }

    @Test
    fun aNewStudentMessageAfterACancelledRoundOpensTheNextRound() {
        val cancelled = TutorTurnSendStateMachine.reduce(
            dispatching("logical-1", "message-1"),
            TutorSendAction.DispatchFailed(
                "logical-1",
                "message-1",
                errorCode = "CANCELLED",
                retryable = false,
            ),
        )
        assertEquals(TutorSendPhase.PERMANENT_FAILURE, cancelled.phase)

        val nextRound = TutorTurnSendStateMachine.reduce(
            cancelled,
            TutorSendAction.StudentMessagePersisted("logical-2", "message-2"),
        )

        assertEquals(TutorSendPhase.PERSISTING, nextRound.phase)
        assertEquals("logical-2", nextRound.logicalOperationId)
    }

    @Test
    fun aNewStudentMessageAfterACompletedRoundOpensTheNextRound() {
        val completed = TutorTurnSendStateMachine.reduce(
            TutorTurnSendStateMachine.reduce(
                dispatching("logical-1", "message-1"),
                TutorSendAction.DispatchStarted("logical-1", "message-1"),
            ),
            TutorSendAction.DispatchSucceeded("logical-1", "message-1"),
        )
        assertEquals(TutorSendPhase.COMPLETED, completed.phase)

        val nextRound = TutorTurnSendStateMachine.reduce(
            completed,
            TutorSendAction.StudentMessagePersisted("logical-2", "message-2"),
        )

        assertEquals(TutorSendPhase.PERSISTING, nextRound.phase)
        assertEquals("logical-2", nextRound.logicalOperationId)
    }

    @Test
    fun aRoundCarriesNoDispatchBudget() {
        var state = dispatching("logical-1", "message-1")
        repeat(9) {
            state = TutorTurnSendStateMachine.reduce(
                state,
                TutorSendAction.DispatchFailed(
                    "logical-1",
                    "message-1",
                    errorCode = "TIMEOUT",
                    retryable = true,
                ),
            )
            state = TutorTurnSendStateMachine.reduce(
                state,
                TutorSendAction.ConsentGranted("logical-1", "message-1"),
            )
        }

        // 界面不再有"第 N 次"这道闸门：重试多少次都由内核账本裁决。
        assertEquals(TutorSendPhase.DISPATCHING, state.phase)
    }

    @Test
    fun processRestartRequiresExplicitContinueBeforeDispatching() {
        val persisted = TutorTurnSendStateMachine.reduce(
            TutorSendState(),
            TutorSendAction.StudentMessagePersisted("logical-1", "message-1"),
        )

        val restarted = TutorTurnSendStateMachine.reduce(
            persisted,
            TutorSendAction.ResumeAfterRestart("logical-1", "message-1"),
        )

        assertEquals(TutorSendPhase.AWAITING_CONSENT, restarted.phase)

        // 停止/换新回合走 Reset：回到空闲，且不带任何一轮的标识。
        assertEquals(TutorSendState(), TutorTurnSendStateMachine.reduce(restarted, TutorSendAction.Reset))
    }
}
