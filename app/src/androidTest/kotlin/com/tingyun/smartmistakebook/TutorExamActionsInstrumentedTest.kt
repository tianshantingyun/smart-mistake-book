package com.tingyun.smartmistakebook

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.domain.TutorLocalActionCommand
import com.tingyun.smartmistakebook.core.domain.TutorLocalActionContext
import com.tingyun.smartmistakebook.core.model.TutorLocalAction
import com.tingyun.smartmistakebook.core.model.TutorLocalActionRequest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Real app landing for the exam-calendar actions added to the agent action surface. */
@RunWith(AndroidJUnit4::class)
class TutorExamActionsInstrumentedTest {
    @Test
    fun addingAndRemovingAnExamWritesTheRealSchedulingStore() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<SmartMistakeBookApplication>()
        val key = "instrumented-exam-${System.nanoTime()}"
        val examDay = 20_000L + (System.currentTimeMillis() % 1_000)

        val add = app.executeTutorApplicationAction(
            TutorLocalActionCommand(
                logicalOperationId = key,
                conversationId = "instrumented-exam-conversation",
                request = TutorLocalActionRequest(
                    TutorLocalAction.ADD_EXAM,
                    mapOf(
                        "subject" to "数学",
                        "examEpochDay" to examDay.toString(),
                        "title" to "仪器化考试",
                    ),
                ),
                context = TutorLocalActionContext(),
            ),
            effectKey = key,
            attemptKey = "$key-attempt",
        )

        assertTrue(add.detail, add.ok == true)
        val entry = app.schedulingSettingsStore.exams.first()
            .single { it.title == "仪器化考试" }
        assertEquals("数学", entry.subject)
        assertEquals(examDay, entry.examEpochDay)

        val remove = app.executeTutorApplicationAction(
            TutorLocalActionCommand(
                logicalOperationId = "$key-remove",
                conversationId = "instrumented-exam-conversation",
                request = TutorLocalActionRequest(
                    TutorLocalAction.REMOVE_EXAM,
                    mapOf("examEntryId" to entry.entryId),
                ),
                context = TutorLocalActionContext(),
            ),
            effectKey = "$key-remove",
            attemptKey = "$key-remove-attempt",
        )

        assertTrue(remove.detail, remove.ok == true)
        assertTrue(
            app.schedulingSettingsStore.exams.first().none { it.entryId == entry.entryId },
        )
    }
}
