package com.tingyun.smartmistakebook

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.data.model.ModelTaskRepositoryFactory
import com.tingyun.smartmistakebook.core.database.StudyDatabaseFactory
import com.tingyun.smartmistakebook.core.domain.KnowledgeBaseAvailability
import com.tingyun.smartmistakebook.core.domain.ModelGateway
import com.tingyun.smartmistakebook.core.domain.TUTOR_TOOL_DECLARATIONS
import com.tingyun.smartmistakebook.core.model.ContentBlock
import com.tingyun.smartmistakebook.core.model.FigureGenerationCapabilities
import com.tingyun.smartmistakebook.core.model.ModelExecutionLocation
import com.tingyun.smartmistakebook.core.model.ModelGatewayEvent
import com.tingyun.smartmistakebook.core.model.ModelGatewayExecution
import com.tingyun.smartmistakebook.core.model.ModelTaskKind
import com.tingyun.smartmistakebook.core.model.ModelTaskOutput
import com.tingyun.smartmistakebook.core.model.ModelTaskRequest
import com.tingyun.smartmistakebook.core.model.ModelTaskStatus
import com.tingyun.smartmistakebook.core.model.ProviderCapabilitySnapshot
import com.tingyun.smartmistakebook.core.model.QuestionDocument
import com.tingyun.smartmistakebook.core.model.TutorIntentDecision
import com.tingyun.smartmistakebook.core.model.TutorMemoryPreference
import com.tingyun.smartmistakebook.core.model.TutorMessageIntent
import com.tingyun.smartmistakebook.core.model.TutorPlanInput
import com.tingyun.smartmistakebook.core.model.TutorPlanOutput
import com.tingyun.smartmistakebook.core.model.TutorRequestedLocalCapability
import com.tingyun.smartmistakebook.core.model.TutorToolCall
import com.tingyun.smartmistakebook.core.model.TutorToolName
import com.tingyun.smartmistakebook.core.model.TutorToolRequestsOutput
import com.tingyun.smartmistakebook.core.model.TutorTurnPlan
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Production assembly + real isolated QuickJS + RUN_JAVASCRIPT host bridge.
 *
 * A scripted provider asks the agent to run JavaScript that calls APP_CONTEXT; the result must
 * come back into the next model dispatch as a real tool result, then the plan completes.
 */
@RunWith(AndroidJUnit4::class)
class TutorAgentJsToolInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<SmartMistakeBookApplication>()
    private val databaseName = "agent-js-${System.nanoTime()}.db"

    @After
    fun tearDown() {
        context.deleteDatabase(databaseName)
    }

    private val provider = ProviderCapabilitySnapshot(
        providerId = "agent-js-provider",
        providerDisplayName = "Agent JS test",
        modelId = "agent-js-model",
        supportedTasks = setOf(ModelTaskKind.TUTOR_PLAN),
        supportsImageInput = false,
        supportsStructuredOutput = true,
        supportsStreaming = false,
        executionLocation = ModelExecutionLocation.LOCAL_NO_EGRESS,
    )

    private class ScriptedGateway(
        private val provider: ProviderCapabilitySnapshot,
        private val script: List<ModelTaskOutput>,
    ) : ModelGateway {
        val requests = mutableListOf<ModelTaskRequest>()
        override suspend fun capabilities() = provider
        override fun execute(execution: ModelGatewayExecution): Flow<ModelGatewayEvent> = flow {
            val index = requests.size
            requests += execution.request
            emit(ModelGatewayEvent.Started(provider))
            emit(ModelGatewayEvent.Completed(script[index]))
        }
    }

    private fun request() = ModelTaskRequest(
        requestId = "agent-js-plan",
        input = TutorPlanInput(
            sessionId = "agent-js-session",
            draftRevisionNumber = 1,
            subject = "MATH",
            questionDocument = QuestionDocument(
                id = "agent-js-question",
                blocks = listOf(ContentBlock.Paragraph("stem", "求函数的最值。")),
            ),
            relevantLearningEvidence = emptyList(),
            projectionIsCurrent = true,
            toolDeclarations = TUTOR_TOOL_DECLARATIONS.toList(),
        ),
        occurredAtEpochMillis = 1_000,
        agentConsentGranted = true,
    )

    private fun jsToolRound() = TutorToolRequestsOutput(
        intentDecision = TutorIntentDecision(
            intent = TutorMessageIntent.CURRENT_QUESTION_HELP,
            confidence = 0.95,
            explicitActionRequest = false,
            memoryPreference = TutorMemoryPreference.UNCHANGED,
            requestedLocalCapability = TutorRequestedLocalCapability.NONE,
        ),
        calls = listOf(
            TutorToolCall(
                tool = TutorToolName.RUN_JAVASCRIPT,
                rationale = "读取真实应用上下文",
                script = "return agent.callTool('APP_CONTEXT', {}).data.subject;",
            ),
        ),
        modelVersion = "agent-js-model",
    )

    private fun finalPlan() = TutorPlanOutput(
        sessionId = "agent-js-session",
        draftRevisionNumber = 1,
        questionDocumentId = "agent-js-question",
        plan = TutorTurnPlan(
            openingMarkdown = "先看这道题的结构。",
            solutionMarkdown = "完整讲解。",
            alternateMethodMarkdown = "另一种切入。",
            difficultyReasonMarkdown = "难点在步骤。",
            targetedEvidenceLabels = emptyList(),
            inferredKnowledgeLabels = listOf("函数"),
        ),
        modelVersion = "agent-js-model",
    )

    @Test
    fun javascriptRunsInTheRealIsolatedEngineAndItsHostResultReachesTheNextRound() = runBlocking {
        val host = requireNotNull(context.visualJsFigureHost)
        assertTrue("The real isolated QuickJS worker must be ready", host.prepare())
        assertTrue("Production JS readiness must enable the agent tool", host.available())
        val database = StudyDatabaseFactory.open(context, databaseName)
        try {
            val gateway = ScriptedGateway(provider, listOf(jsToolRound(), finalPlan()))
            val repository = ModelTaskRepositoryFactory.create(
                database = database,
                gateway = gateway,
                knowledgeBaseAvailability = MutableStateFlow(KnowledgeBaseAvailability.Ready),
                figureCapabilities = { FigureGenerationCapabilities.NONE },
                visualEngine = host.engine,
                visualEngineReady = { host.available() },
            )

            val snapshots = repository.execute(request()).toList()

            assertEquals(ModelTaskStatus.SUCCEEDED, snapshots.last().status)
            assertEquals(2, gateway.requests.size)
            val round = gateway.requests[1].input
                as com.tingyun.smartmistakebook.core.model.TutorPlanInput
            val js = round.toolRoundResults.single().outcomes.single()
            assertEquals(TutorToolName.RUN_JAVASCRIPT, js.tool)
            assertTrue(js.summaryMarkdown, js.ok)
            assertEquals("\"MATH\"", js.dataJson)
        } finally {
            database.close()
        }
    }
}
