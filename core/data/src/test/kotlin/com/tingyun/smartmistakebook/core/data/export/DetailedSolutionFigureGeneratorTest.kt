package com.tingyun.smartmistakebook.core.data.export

import com.tingyun.smartmistakebook.core.domain.FigureGenerationCoordinator
import com.tingyun.smartmistakebook.core.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DetailedSolutionFigureGeneratorTest {
    private val question = QuestionDocument("q", blocks = listOf(ContentBlock.Paragraph("stem", "1+1=?")))
    private val input = DetailedSolutionInput(FrozenExportQuestion(1, "entry",
        SolutionBasis("rev", solutionQuestionSha256(question, emptyList())), question),
        availableFigureMethods = setOf(FigureGenerationMethod.JS))
    private val original = ModelTaskRequest(requestId = "answer", input = input, occurredAtEpochMillis = 1, agentConsentGranted = true)
    private val proposal = FigureGenerationProposal(decision = FigureGenerationDecision.GENERATE,
        purpose = FigureGenerationPurpose.EXPLANATION, method = FigureGenerationMethod.JS, reason = "two units",
        fidelity = FigureFidelity.ILLUSTRATIVE, program = "bad program")
    private val figure = SolutionFigureProposal("step-2", proposal)
    private val output = DetailedSolutionOutput(DetailedSolutionDocument(input.question.basis, "2",
        QuestionDocument("solution-steps", "解题步骤", listOf(ContentBlock.Paragraph("step-1", "1+1=2"),
            ContentBlock.Paragraph("step-2", "two units"))), origin = SolutionOrigin.MODEL,
        verification = SolutionVerification.UNVERIFIED,
        requiredFigureBlockIds = setOf("step-2")), listOf(figure), "model")
    private val request = FigureGenerationRequest("figure-op", FigureGenerationPurpose.EXPLANATION,
        FigureGenerationTarget(FigureFidelity.ILLUSTRATIVE), FigureGenerationCapabilities(setOf(FigureGenerationMethod.JS)),
        FigureGenerationBudget(), FigureDispatchParent.ModelTask(original.requestId, reserveChatFinalAnswer = false),
        lineageFingerprint = detailedSolutionFigureBasis(original, output, figure.blockId))

    @Test fun executionDiagnosticsRepairTheProgramWithinTheSameOperationAndBudget() = runBlocking {
        val calls = mutableListOf<FigureGenerationRequest>()
        val programs = mutableListOf<String?>()
        val repairs = mutableListOf<ModelTaskRequest>()
        val coordinator = object : FigureGenerationCoordinator {
            override suspend fun generate(request: FigureGenerationRequest, proposal: FigureGenerationProposal): FigureGenerationOutcome {
                calls += request
                programs += proposal.program
                return if (programs.size == 1) failure() else FigureGenerationOutcome(request.operationId, request.purpose,
                    proposal.method, FigureGenerationStatus.GENERATED,
                    artifact = FigureArtifact.Program(request.purpose, "program", requireNotNull(proposal.program)))
            }
        }
        val result = DetailedSolutionFigureGenerator(coordinator, { envelope ->
            repairs += envelope
            val repairedInput = envelope.input as DetailedSolutionInput
            assertEquals("INVALID_PROGRAM: missing render", repairedInput.figureRepair?.diagnostics)
            assertEquals(proposal, repairedInput.figureRepair?.failedProposal)
            assertEquals(output.document, repairedInput.existingGenericAnswer)
            assertEquals(ModelTaskLogicalOperationFingerprint.of(original), ModelTaskLogicalOperationFingerprint.of(envelope))
            succeeded(envelope, output.copy(figureProposals = listOf(figure.copy(proposal = proposal.copy(program = "fixed program")))))
        }).generate(request, original, output, figure)
        assertEquals(FigureGenerationStatus.GENERATED, result.status)
        assertEquals(listOf("bad program", "fixed program"), programs)
        assertEquals(2, calls.size)
        assertTrue(calls.all { it === request })
        assertEquals(1, repairs.size)
    }

    @Test fun infrastructureFailuresDoNotRequestProgramRepair() = runBlocking {
        val coordinator = object : FigureGenerationCoordinator {
            override suspend fun generate(request: FigureGenerationRequest, proposal: FigureGenerationProposal) =
                failure(FigureGenerationStatus.UNAVAILABLE, "INVALID_PROGRAM: [UNATTRIBUTED] unavailable")
        }
        val result = DetailedSolutionFigureGenerator(coordinator, { error("unexpected repair") })
            .generate(request, original, output, figure)
        assertEquals(FigureGenerationStatus.UNAVAILABLE, result.status)
    }

    @Test fun modelCanReselectAnActuallyAvailableMethodDuringRepair() = runBlocking {
        val seen = mutableListOf<FigureGenerationMethod?>()
        val both = request.copy(capabilities = FigureGenerationCapabilities.IMAGE_MODEL_ONLY.copy(
            availableMethods = setOf(FigureGenerationMethod.JS, FigureGenerationMethod.IMAGE_MODEL)))
        val coordinator = object : FigureGenerationCoordinator {
            override suspend fun generate(request: FigureGenerationRequest, proposal: FigureGenerationProposal): FigureGenerationOutcome {
                assertTrue(request === both)
                seen += proposal.method
                return failure()
            }
        }
        val image = proposal.copy(method = FigureGenerationMethod.IMAGE_MODEL, program = null,
            imageOperation = FigureImageOperation.GENERATE, imagePrompt = "two units")
        DetailedSolutionFigureGenerator(coordinator, { envelope ->
            assertEquals(both.capabilities.availableMethods, (envelope.input as DetailedSolutionInput).availableFigureMethods)
            succeeded(envelope, output.copy(figureProposals = listOf(figure.copy(proposal = image))))
        }).generate(both, original, output, figure)
        assertEquals(listOf(FigureGenerationMethod.JS, FigureGenerationMethod.IMAGE_MODEL), seen)
    }

    @Test fun repeatedFailedPayloadStopsWithoutReexecutingIt() = runBlocking {
        var executions = 0
        val coordinator = object : FigureGenerationCoordinator {
            override suspend fun generate(request: FigureGenerationRequest, proposal: FigureGenerationProposal): FigureGenerationOutcome {
                executions++
                return failure()
            }
        }
        val result = DetailedSolutionFigureGenerator(coordinator, { succeeded(it, output) })
            .generate(request, original, output, figure)
        assertEquals(FigureGenerationStatus.FAILED, result.status)
        assertEquals(1, executions)
    }

    private fun failure(status: FigureGenerationStatus = FigureGenerationStatus.FAILED,
        diagnostics: String = "INVALID_PROGRAM: missing render") =
        FigureGenerationOutcome(request.operationId, request.purpose, FigureGenerationMethod.JS, status, diagnostics = diagnostics)

    private fun succeeded(envelope: ModelTaskRequest, result: DetailedSolutionOutput) = ModelTaskSnapshot(
        "task-${envelope.requestId}", envelope, ModelTaskFingerprint.of(envelope), ModelTaskStatus.SUCCEEDED,
        1, ModelTaskStage.COMPLETE, "ready", 1, output = result, createdAtEpochMillis = 2, updatedAtEpochMillis = 2)
}
