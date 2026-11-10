package com.tingyun.smartmistakebook.core.data.visual

import com.tingyun.smartmistakebook.core.domain.VisualExecutionResult
import com.tingyun.smartmistakebook.core.model.FigureArtifact
import com.tingyun.smartmistakebook.core.model.FigureGenerationProposal
import com.tingyun.smartmistakebook.core.model.FigureGenerationPurpose
import com.tingyun.smartmistakebook.core.model.FigureGenerationRequest
import com.tingyun.smartmistakebook.core.model.FigureGenerationSourceKind
import com.tingyun.smartmistakebook.core.model.VisualRole
import com.tingyun.smartmistakebook.core.model.figureJsBasisFingerprint
import com.tingyun.smartmistakebook.core.model.figureSha256Hex

/** Publishes static JS drawings through the same bounded snapshot and asset stores. */
class JsFigureArtifactAdapter(
    private val snapshots: TutorVisualCommitRepository,
    private val preview: TutorVisualPreviewRenderer,
    private val labels: (VisualFrameSnapshot, VisualExecutionResult.Rendered) -> List<TutorVisualPreviewLabel>,
    private val runtimeVersion: String,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private fun program(request: FigureGenerationRequest, proposal: FigureGenerationProposal) = TutorVisualProgramInput(
        learnerId = "learner:local",
        role = when (request.purpose) {
            FigureGenerationPurpose.SOURCE_TRANSCRIPTION, FigureGenerationPurpose.SOURCE_RECONSTRUCTION,
            FigureGenerationPurpose.PIXEL_CLEANUP -> VisualRole.QUESTION_DIAGRAM.name
            else -> VisualRole.TUTOR_EXPLANATION.name
        },
        sourceJs = requireNotNull(proposal.program),
        initialStateJson = "{}",
        basisFingerprint = request.basisFingerprint ?: figureJsBasisFingerprint(
            request.operationId, request.source?.contentSha256, requireNotNull(proposal.program)),
        apiVersion = 1,
        sdkVersion = figureSha256Hex(VisualJsAssets.sdkJs.toByteArray(Charsets.UTF_8)),
        basisRevisionId = request.sourceId.takeIf { request.sourceKind == FigureGenerationSourceKind.PROBLEM_REVISION },
    )

    fun programId(request: FigureGenerationRequest, proposal: FigureGenerationProposal): String =
        snapshots.programIdFor(program(request, proposal))

    suspend fun publish(
        request: FigureGenerationRequest,
        proposal: FigureGenerationProposal,
        frame: VisualFrameSnapshot,
        rendered: VisualExecutionResult.Rendered,
    ): FigureArtifact.Program {
        val program = program(request, proposal)
        val text = labels(frame, rendered)
        require(text.map { it.contentId }.toSet() == rendered.contentIds) { "Drawing labels are incomplete" }
        val now = clock()
        val previewResult = preview.renderPreview(TutorVisualPreviewRequest(
            rendered, frame.viewport.widthDu, frame.viewport.heightDu, frame.fontScaleMilli, now,
            labels = text, measuredEntries = rendered.contents.map { it.measured },
            preferredPxPerDu = 3f,
        ))
        val assetId = (previewResult as? TutorVisualPreviewOutcome.Accepted)?.previewAssetId
            ?: error((previewResult as TutorVisualPreviewOutcome.Refused).diagnostic)
        val snapshot = snapshots.commitGeneratedArtifact(
            program,
            TutorVisualSnapshotInput(
                stateJson = program.initialStateJson,
                sceneSchemaVersion = rendered.sceneSchemaVersion,
                sceneJson = rendered.sceneJson,
                renderSchemaVersion = rendered.drawSchemaVersion,
                drawCommandsJson = requireNotNull(rendered.drawCommandsJson),
                contentIds = rendered.contentIds,
                viewportWidthDu = frame.viewport.widthDu,
                viewportHeightDu = frame.viewport.heightDu,
                fontScaleMilli = frame.fontScaleMilli,
                theme = frame.theme, timeMs = frame.timeMs, seed = frame.seed,
                runtimeVersion = runtimeVersion,
                compilerVersion = figureSha256Hex(VisualJsAssets.compilerJs.toByteArray(Charsets.UTF_8)),
                rendererVersion = "visual-preview-v2-3x",
                fontManifestVersion = figureSha256Hex(text.joinToString("\n") { it.artifact.digest }.toByteArray(Charsets.UTF_8)),
                questionVerificationStatus = "NOT_CHECKED", previewAssetId = assetId,
            ),
            ownerRequestId = request.operationId,
        )
        return FigureArtifact.Program(request.purpose, snapshots.programIdFor(program), program.sourceJs,
            snapshot.snapshotId, assetId)
    }
}
