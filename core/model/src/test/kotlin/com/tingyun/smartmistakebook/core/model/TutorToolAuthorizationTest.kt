package com.tingyun.smartmistakebook.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TutorToolAuthorizationTest {
    @Test
    fun everyIntentCanComposeTheDeclaredToolsAtAnyClassificationConfidence() {
        val declared = TutorToolName.entries.toSet()
        TutorMessageIntent.entries.forEach { intent ->
            listOf(0.0, 0.3, 0.45, 1.0).forEach { confidence ->
                val result = tutorToolAuthorization(decision(intent, confidence), declared)
                assertEquals("$intent at $confidence", declared, result.allowedTools)
                assertTrue(result.routeEligible)
            }
        }
    }

    @Test
    fun notebookWriteDoesNotRequireAnAdditionalModelReportedPermission() {
        listOf(false, true).forEach { explicitRequest ->
            val result = tutorToolAuthorization(
                decision(TutorMessageIntent.CASUAL_CONVERSATION).copy(
                    explicitActionRequest = explicitRequest,
                ),
                setOf(TutorToolName.NOTEBOOK_WRITE),
            )
            assertEquals(setOf(TutorToolName.NOTEBOOK_WRITE), result.allowedTools)
        }
    }

    @Test
    fun undeclaredToolsRemainUnavailableAcrossAllIntents() {
        val declared = setOf(TutorToolName.NOTEBOOK_READ)
        TutorMessageIntent.entries.forEach { intent ->
            assertEquals(declared, tutorToolAuthorization(decision(intent), declared).allowedTools)
        }
    }

    @Test
    fun anEmptyDeclarationExecutesNoTools() {
        val result = tutorToolAuthorization(decision(TutorMessageIntent.AMBIGUOUS), emptySet())
        assertTrue(result.allowedTools.isEmpty())
        assertTrue(result.routeEligible)
    }

    private fun decision(intent: TutorMessageIntent, confidence: Double = 0.9) = TutorIntentDecision(
        intent = intent,
        confidence = confidence,
        explicitActionRequest = false,
        memoryPreference = TutorMemoryPreference.UNCHANGED,
        requestedLocalCapability = TutorRequestedLocalCapability.NONE,
    )
}
