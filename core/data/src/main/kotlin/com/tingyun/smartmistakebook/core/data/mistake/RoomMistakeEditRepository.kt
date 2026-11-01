package com.tingyun.smartmistakebook.core.data.mistake

import android.content.Context
import com.tingyun.smartmistakebook.core.data.capture.AndroidCanonicalAssetVault
import com.tingyun.smartmistakebook.core.data.capture.GeneratedFigureProvenance
import com.tingyun.smartmistakebook.core.data.model.FigurePromptAssembler
import com.tingyun.smartmistakebook.core.data.visual.openGuardedFigureImageBackend
import com.tingyun.smartmistakebook.core.database.CanonicalSourceAssetRecord
import com.tingyun.smartmistakebook.core.database.StudyDatabasePort
import com.tingyun.smartmistakebook.core.database.port.PersistedMistakeEditJob
import com.tingyun.smartmistakebook.core.domain.FigureGenerationCoordinator
import com.tingyun.smartmistakebook.core.domain.MistakeEditJob
import com.tingyun.smartmistakebook.core.domain.MistakeEditRepository
import com.tingyun.smartmistakebook.core.domain.MistakeEditRequest
import com.tingyun.smartmistakebook.core.domain.ModelConfigurationStore
import com.tingyun.smartmistakebook.core.domain.ModelTaskRepository
import com.tingyun.smartmistakebook.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.map
import java.util.concurrent.ConcurrentHashMap

internal interface MistakeEditAssetAccess {
    fun verifiedBytes(asset: CanonicalSourceAssetRecord): ByteArray
    fun localUri(asset: CanonicalSourceAssetRecord): String
    suspend fun persist(artifact: FigureArtifact.ImageAsset, attemptId: String, at: Long): CanonicalSourceAssetRecord
}

/** Application-owned work; collecting its flow or closing the editor never starts/stops a provider. */
class RoomMistakeEditRepository internal constructor(
    private val database: StudyDatabasePort,
    private val tasks: ModelTaskRepository,
    private val coordinator: FigureGenerationCoordinator,
    private val assets: MistakeEditAssetAccess,
    private val figureCapabilities: suspend () -> FigureGenerationCapabilities,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) : MistakeEditRepository {
    private val active = ConcurrentHashMap<String, Job>()

    override suspend fun requestEdit(request: MistakeEditRequest, agentConsentGranted: Boolean): String = withContext(Dispatchers.IO) {
        val detail = requireNotNull(database.readMistakeDetail(request.key.entryId)) { "题目不存在" }
        require(!detail.archived && detail.problemId == request.key.problemId && detail.problemRevisionId == request.key.problemRevisionId) { "题目版本已更新，请重新打开编辑页" }
        val grants = request.sourceImageAssetIds.sorted().map { id ->
            val asset = detail.sourceAssets.singleOrNull { it.sourceAsset.sourceAssetId == id }?.sourceAsset
                ?: error("请选择当前题目的原图或已保存配图")
            assets.verifiedBytes(asset)
            ModelEgressAssetGrant(asset.sourceAssetId, asset.contentSha256, asset.byteSize, asset.width, asset.height)
        }
        val input = MistakeEditInput(EditBasis(request.key.entryId, request.key.problemRevisionId, CapturedQuestionDocumentFingerprint.of(request.draft)),
            request.draft, request.targetBlockIds, normalizeEditInstruction(request.instruction), grants, request.editTitle, promptBody())
        val id = mistakeEditJobId(input)
        val now = clock()
        val job = database.createMistakeEditJob(PersistedMistakeEditJob(id,
            ModelTaskRequest(requestId = "$id.0", input = input, occurredAtEpochMillis = now, agentConsentGranted = agentConsentGranted), createdAtEpochMillis = now))
        if (job.status == MistakeEditJobStatus.QUEUED) {
            if (agentConsentGranted) start(id, recovery = true)
            else move(job, MistakeEditJobStatus.UNAVAILABLE, "请先在设置中启用智能体")
        }
        id
    }

    override fun observe(operationId: String): Flow<MistakeEditJob?> = database.observeMistakeEditJob(operationId).map { it?.toDomain() }

    override suspend fun readySuggestion(operationId: String, currentBasis: EditBasis): MistakeEditJob? = withContext(Dispatchers.IO) {
        val job = database.readMistakeEditJob(operationId) ?: return@withContext null
        if (job.status !in setOf(MistakeEditJobStatus.READY, MistakeEditJobStatus.ADOPTED) || job.committedRevisionId != null) return@withContext null
        if (job.input.basis != currentBasis || !database.isMistakeEditBasisCurrent(currentBasis)) {
            move(job, MistakeEditJobStatus.SUPERSEDED, "题面已修改，请重新提出修改请求")
            return@withContext null
        }
        try {
            require(database.verifyMistakeEditFigures(operationId))
            verifyFiles(job)
            val prepared = applyPreparedMistakeEdit(job.input, requireNotNull(job.output), job.figures)
            job.toDomain().copy(sourceDraft = job.input.draft, preparedDraft = prepared, figureUris = job.figures.associate { figure ->
                figure.assetId to assets.localUri(requireNotNull(database.readCanonicalSourceAsset(figure.assetId)))
            })
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { move(job, MistakeEditJobStatus.FAILED, "修改结果不完整，请重试"); null }
    }

    override suspend fun markAdopted(operationId: String, currentBasis: EditBasis): Boolean {
        if (readySuggestion(operationId, currentBasis) == null) return false
        val job = database.readMistakeEditJob(operationId) ?: return false
        if (job.status == MistakeEditJobStatus.ADOPTED) return job.input.basis == currentBasis && job.committedRevisionId == null
        return move(job, MistakeEditJobStatus.ADOPTED, "已采用，保存后生效")
    }

    override suspend fun cancel(operationId: String) {
        val job = database.readMistakeEditJob(operationId) ?: return
        if (job.committedRevisionId != null || job.status in setOf(MistakeEditJobStatus.CANCELLED, MistakeEditJobStatus.SUPERSEDED)) return
        if (move(job, MistakeEditJobStatus.CANCELLED, "已停止修改")) {
            active[operationId]?.cancelAndJoin()
            tasks.cancel(job.request.requestId)
        }
    }

    override suspend fun retry(operationId: String, agentConsentGranted: Boolean) = withContext(Dispatchers.IO) {
        require(agentConsentGranted) { "请先在设置中启用智能体" }
        active[operationId]?.join()
        val job = requireNotNull(database.readMistakeEditJob(operationId))
        require(database.isMistakeEditBasisCurrent(job.input.basis)) { "题目版本已更新" }
        val reuseOutput = job.output?.suggestion?.decision == MistakeEditDecision.SUGGEST_CHANGES
        job.input.sourceImages.forEach { grant ->
            val asset = requireNotNull(database.readCanonicalSourceAsset(grant.assetId))
            require(asset.contentSha256 == grant.sha256 && asset.byteSize == grant.byteSize && asset.width == grant.width && asset.height == grant.height)
            assets.verifiedBytes(asset)
        }
        val intactFigures = job.figures.filter { ref ->
            try { val asset = requireNotNull(database.readCanonicalSourceAsset(ref.assetId)); require(asset.contentSha256 == ref.contentSha256); assets.verifiedBytes(asset); true }
            catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { false }
        }
        val generation = job.generation + 1
        val next = job.copy(generation = generation, stateVersion = job.stateVersion + 1, status = MistakeEditJobStatus.QUEUED,
            request = if (reuseOutput) job.request else ModelTaskRequest(requestId = "$operationId.$generation", input = job.input.copy(figureGenerationPromptBody = promptBody()), occurredAtEpochMillis = clock(), agentConsentGranted = agentConsentGranted),
            output = if (reuseOutput) job.output else null, figures = if (reuseOutput) intactFigures else emptyList(),
            message = "等待修改", updatedAtEpochMillis = maxOf(clock(), job.updatedAtEpochMillis))
        if (database.transitionMistakeEditJob(next)) start(operationId, recovery = false)
    }

    override suspend fun resumePendingJobs(agentConsentGranted: Boolean) {
        if (!agentConsentGranted) return
        var after: String? = null
        do {
            val page = database.readPendingMistakeEditJobs(after, 32)
            page.forEach { start(it.operationId, recovery = true) }
            after = page.lastOrNull()?.operationId
        } while (page.size == 32)
    }

    override suspend fun latestForBasis(basis: EditBasis): MistakeEditJob? = database.latestMistakeEditJob(basis)?.toDomain()

    private fun start(id: String, recovery: Boolean) {
        val worker = scope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
            try { prepare(id, recovery) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                database.readMistakeEditJob(id)?.takeIf { it.status in PENDING }?.let { move(it, MistakeEditJobStatus.FAILED, "修改未完成，请重试") }
            } finally { active.remove(id, currentCoroutineContext()[Job]) }
        }
        if (active.putIfAbsent(id, worker) == null) worker.start() else worker.cancel()
    }

    private suspend fun prepare(id: String, recovery: Boolean) {
        var job = database.readMistakeEditJob(id) ?: return
        if (job.status !in PENDING) return
        if (!database.isMistakeEditBasisCurrent(job.input.basis)) { move(job, MistakeEditJobStatus.SUPERSEDED, "题目版本已更新"); return }
        verifyFiles(job)
        if (job.output == null) {
            val existing = database.readModelTask(job.request.requestId)
            val safelyUndispatched = existing?.attemptCount == 0 && existing.status in setOf(ModelTaskStatus.WAITING_FOR_MODEL, ModelTaskStatus.QUEUED)
            if (existing != null && existing.status != ModelTaskStatus.SUCCEEDED && recovery && !safelyUndispatched) {
                move(job, MistakeEditJobStatus.FAILED, "上次修改已中断，请重试"); return
            }
            if (job.status == MistakeEditJobStatus.QUEUED) {
                if (!move(job, MistakeEditJobStatus.REQUESTING, "正在准备修改建议")) return
                job = requireNotNull(database.readMistakeEditJob(id))
            }
            val task = existing?.takeIf { it.status == ModelTaskStatus.SUCCEEDED } ?: tasks.execute(job.request).last()
            val output = task.output as? MistakeEditOutput
            if (task.status != ModelTaskStatus.SUCCEEDED || output == null) {
                move(job, MistakeEditJobStatus.FAILED, task.failure?.message?.take(500) ?: "未能生成修改建议，请检查模型设置"); return
            }
            val status = when (output.suggestion.decision) {
                MistakeEditDecision.NO_CHANGE -> MistakeEditJobStatus.NO_CHANGE
                MistakeEditDecision.NEED_SOURCE -> MistakeEditJobStatus.NEED_SOURCE
                MistakeEditDecision.UNAVAILABLE -> MistakeEditJobStatus.UNAVAILABLE
                MistakeEditDecision.SUGGEST_CHANGES -> if (output.suggestion.changes.any { it is MistakeEditChange.ReplaceFigure }) MistakeEditJobStatus.GENERATING_FIGURES else MistakeEditJobStatus.READY
            }
            val next = job.copy(status = status, output = output, message = output.suggestion.summaryMarkdown, stateVersion = job.stateVersion + 1, updatedAtEpochMillis = maxOf(clock(), job.updatedAtEpochMillis))
            if (!database.transitionMistakeEditJob(next)) return
            job = next
            if (status != MistakeEditJobStatus.GENERATING_FIGURES) return
        } else if (job.status == MistakeEditJobStatus.QUEUED) {
            if (!move(job, MistakeEditJobStatus.GENERATING_FIGURES, "继续准备配图")) return
            job = requireNotNull(database.readMistakeEditJob(id))
        } else if (recovery) {
            val targets = requireNotNull(job.output).suggestion.changes.filterIsInstance<MistakeEditChange.ReplaceFigure>().map { it.blockId }.toSet()
            if (job.figures.map { it.blockId }.toSet() == targets) {
                move(job, MistakeEditJobStatus.READY, "修改建议已恢复，请预览后采用")
                return
            }
            // An interrupted paid attempt with no admitted output is never silently replayed.
            move(job, MistakeEditJobStatus.FAILED, "上次配图已中断，已完成的图片会保留，请重试"); return
        }
        val output = requireNotNull(job.output)
        for (change in output.suggestion.changes.filterIsInstance<MistakeEditChange.ReplaceFigure>()) {
            currentCoroutineContext().ensureActive()
            if (job.figures.any { it.blockId == change.blockId }) continue
            val purpose = requireNotNull(change.proposal.purpose)
            val capabilities = figureCapabilities()
            val budget = FigureGenerationBudget()
            val source = if (change.proposal.imageOperation == FigureImageOperation.EDIT) {
                val grant = job.input.sourceImages.singleOrNull() ?: run { move(job, MistakeEditJobStatus.UNAVAILABLE, "请只选择一张需要修改的源图"); return }
                val asset = requireNotNull(database.readCanonicalSourceAsset(grant.assetId))
                require(asset.contentSha256 == grant.sha256 && asset.byteSize == grant.byteSize && asset.width == grant.width && asset.height == grant.height)
                FigureSourceEvidence(asset.sourceAssetId, asset.contentSha256, asset.mimeType, assets.verifiedBytes(asset))
            } else null
            val request = FigureGenerationRequest(mistakeEditFigureOperationId(id, change.blockId), purpose,
                FigureGenerationTarget(requireNotNull(change.proposal.fidelity)), capabilities, budget,
                FigureDispatchParent.ModelTask(job.request.requestId, reserveChatFinalAnswer = false), source,
                problemContext = job.input.draft.document.title?.take(2_000),
                prompt = if (capabilities.availableMethods.isEmpty()) null else FigurePromptAssembler.assemble(purpose, capabilities, budget),
                sourceKind = FigureGenerationSourceKind.PROBLEM_REVISION, sourceId = job.input.basis.revisionId,
                basisFingerprint = mistakeEditFigureBasis(ModelTaskFingerprint.of(job.request), output, change.blockId))
            val result = coordinator.generate(request, change.proposal)
            val artifact = result.artifact as? FigureArtifact.ImageAsset
            val attemptId = result.attemptId
            if (artifact == null || attemptId == null) { move(job, MistakeEditJobStatus.UNAVAILABLE, result.diagnostics?.take(500) ?: "当前无法生成这张配图，请检查模型能力后重试"); return }
            require(artifact.contentSha256 == figureSha256Hex(artifact.imageBytes()))
            val asset = assets.persist(artifact, attemptId, clock())
            job = database.attachMistakeEditFigure(id, job.stateVersion, change.blockId, attemptId, asset, maxOf(clock(), job.updatedAtEpochMillis)) ?: return
        }
        move(job, MistakeEditJobStatus.READY, "修改建议已就绪，请预览后采用")
    }

    private suspend fun verifyFiles(job: PersistedMistakeEditJob) {
        job.input.sourceImages.forEach { grant ->
            val asset = requireNotNull(database.readCanonicalSourceAsset(grant.assetId))
            require(asset.contentSha256 == grant.sha256 && asset.byteSize == grant.byteSize && asset.width == grant.width && asset.height == grant.height)
            assets.verifiedBytes(asset)
        }
        job.figures.forEach { figure ->
            val asset = requireNotNull(database.readCanonicalSourceAsset(figure.assetId))
            require(asset.contentSha256 == figure.contentSha256)
            assets.verifiedBytes(asset)
        }
    }

    private suspend fun promptBody(): String? {
        val capabilities = figureCapabilities()
        return if (capabilities.availableMethods.isEmpty()) null else FigurePromptAssembler.assemble(FigureGenerationPurpose.SOURCE_RECONSTRUCTION, capabilities, FigureGenerationBudget()).body
    }

    private suspend fun move(job: PersistedMistakeEditJob, status: MistakeEditJobStatus, message: String): Boolean = database.transitionMistakeEditJob(
        job.copy(status = status, message = message, stateVersion = job.stateVersion + 1, updatedAtEpochMillis = maxOf(clock(), job.updatedAtEpochMillis)))

    private fun PersistedMistakeEditJob.toDomain() = MistakeEditJob(operationId, input.basis, status, output, figures, message)

    companion object {
        private val PENDING = setOf(MistakeEditJobStatus.QUEUED, MistakeEditJobStatus.REQUESTING, MistakeEditJobStatus.GENERATING_FIGURES)

        fun create(context: Context, database: StudyDatabasePort, tasks: ModelTaskRepository, coordinator: FigureGenerationCoordinator?,
            configurationStore: ModelConfigurationStore?, networkRequestsAllowed: Boolean, applicationScope: CoroutineScope): RoomMistakeEditRepository {
            val vault = AndroidCanonicalAssetVault(context.applicationContext)
            return RoomMistakeEditRepository(database, tasks, coordinator ?: com.tingyun.smartmistakebook.core.data.visual.FigureGenerationCoordinationFactory.unavailable, object : MistakeEditAssetAccess {
                override fun verifiedBytes(asset: CanonicalSourceAssetRecord) = vault.resolve(asset).readBytes()
                override fun localUri(asset: CanonicalSourceAssetRecord) = vault.resolve(asset).toURI().toString()
                override suspend fun persist(artifact: FigureArtifact.ImageAsset, attemptId: String, at: Long): CanonicalSourceAssetRecord {
                    val id = mistakeEditFigureAssetId(attemptId)
                    database.readCanonicalSourceAsset(id)?.let { existing -> verifiedBytes(existing); return existing }
                    return vault.persistCleanImageBytes(artifact.imageBytes(), artifact.mimeType, "MISTAKE_EDIT", at,
                        GeneratedFigureProvenance(artifact.modelId ?: "unknown", id, artifact.purpose.name), id)
                }
            }, figureCapabilities = {
                configurationStore?.let { openGuardedFigureImageBackend(it, networkRequestsAllowed) }?.use { FigureGenerationCapabilities.IMAGE_MODEL_ONLY }
                    ?: FigureGenerationCapabilities.NONE
            }, scope = applicationScope)
        }
    }
}
