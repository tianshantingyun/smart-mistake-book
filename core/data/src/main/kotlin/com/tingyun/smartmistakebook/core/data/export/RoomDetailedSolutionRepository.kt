package com.tingyun.smartmistakebook.core.data.export

import android.content.Context
import com.tingyun.smartmistakebook.core.data.capture.AndroidCanonicalAssetVault
import com.tingyun.smartmistakebook.core.data.capture.GeneratedFigureProvenance
import com.tingyun.smartmistakebook.core.data.model.FigurePromptAssembler
import com.tingyun.smartmistakebook.core.data.mistake.MistakeEditAssetAccess
import com.tingyun.smartmistakebook.core.data.visual.openGuardedFigureImageBackend
import com.tingyun.smartmistakebook.core.database.StudyDatabasePort
import com.tingyun.smartmistakebook.core.database.CanonicalSourceAssetRecord
import com.tingyun.smartmistakebook.core.database.port.PersistedAnswerManifest
import com.tingyun.smartmistakebook.core.database.port.PersistedDetailedSolution
import com.tingyun.smartmistakebook.core.domain.DetailedSolutionRepository
import com.tingyun.smartmistakebook.core.domain.DetailedSolutionPreparation
import com.tingyun.smartmistakebook.core.domain.DetailedSolutionItemState
import com.tingyun.smartmistakebook.core.domain.ModelTaskRepository
import com.tingyun.smartmistakebook.core.domain.ModelConfigurationStore
import com.tingyun.smartmistakebook.core.domain.FigureGenerationCoordinator
import com.tingyun.smartmistakebook.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

class RoomDetailedSolutionRepository internal constructor(
    private val database: StudyDatabasePort,
    private val tasks: ModelTaskRepository,
    private val coordinator: FigureGenerationCoordinator,
    private val assets: MistakeEditAssetAccess,
    private val figureCapabilities: suspend () -> FigureGenerationCapabilities,
    private val clock: () -> Long = System::currentTimeMillis,
) : DetailedSolutionRepository {
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val activeManifests = ConcurrentHashMap<String, FrozenAnswerManifest>()
    private data class ReadyDisplay(val documentSha256: String, val uris: Map<String, String>)
    private val readyDisplays = ConcurrentHashMap<String, ReadyDisplay>()

    override suspend fun freeze(manifest: FrozenAnswerManifest): String = withContext(Dispatchers.IO) {
        manifest.questions.forEach { verifySources(it) }
        val id = "answer-manifest:" + DetailedSolutionCodec.manifestSha256(manifest)
        database.freezeAnswerManifest(PersistedAnswerManifest(id, manifest, createdAtEpochMillis = clock()))
        id
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observe(manifestId: String): Flow<DetailedSolutionPreparation?> = database.observeAnswerManifest(manifestId).flatMapLatest { record ->
        if (record == null) flowOf(null) else database.observeDetailedSolutions(record.manifest.questions.map { detailedSolutionId(it.basis) })
            .map { rows -> assemble(record, rows) }
    }.flowOn(Dispatchers.IO)

    override suspend fun read(manifestId: String): DetailedSolutionPreparation? = withContext(Dispatchers.IO) {
        val record = database.readAnswerManifest(manifestId) ?: return@withContext null
        assemble(record, record.manifest.questions.mapNotNull { database.readDetailedSolution(detailedSolutionId(it.basis)) }, freshIntegrityCheck = true)
    }

    private suspend fun assemble(record: PersistedAnswerManifest, rows: List<PersistedDetailedSolution>, freshIntegrityCheck: Boolean = false): DetailedSolutionPreparation {
        val byId = rows.associateBy { it.solutionId }
        val items = record.manifest.questions.map { question ->
            val row = byId[detailedSolutionId(question.basis)]
            if (row == null) DetailedSolutionItemState(question.ordinal, question.basis, DetailedSolutionPreparationStatus.QUEUED)
            else {
                val documentSha = row.document?.let(DetailedSolutionCodec::documentSha256)
                val cached = if (freshIntegrityCheck) null else readyDisplays[row.solutionId]?.takeIf { it.documentSha256 == documentSha }
                var uris = cached?.uris.orEmpty()
                val valid = row.status == DetailedSolutionPreparationStatus.READY && (cached != null || try {
                    require(database.verifyDetailedSolutionFigures(row.solutionId)); verifyPrepared(row)
                    uris = row.figures.associate { ref -> ref.assetId to assets.localUri(requireNotNull(database.readCanonicalSourceAsset(ref.assetId))) }
                    readyDisplays[row.solutionId] = ReadyDisplay(requireNotNull(documentSha), uris)
                    true
                } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { false }
                )
                val status = if (row.status == DetailedSolutionPreparationStatus.READY && !valid) DetailedSolutionPreparationStatus.FAILED else row.status
                DetailedSolutionItemState(question.ordinal, question.basis, status, if (valid) row.document else null,
                    if (row.status == DetailedSolutionPreparationStatus.READY && !valid) "答案或配图已失效，请重新准备" else row.message,
                    if (valid) uris else emptyMap())
            }
        }
        val complete = items.all { it.status == DetailedSolutionPreparationStatus.READY && it.document != null }
        val status = when {
            record.status in setOf(DetailedSolutionPreparationStatus.CANCELLED, DetailedSolutionPreparationStatus.STALE) -> record.status
            complete -> DetailedSolutionPreparationStatus.READY
            items.any { it.status in setOf(DetailedSolutionPreparationStatus.FAILED, DetailedSolutionPreparationStatus.CANCELLED, DetailedSolutionPreparationStatus.STALE) } -> DetailedSolutionPreparationStatus.FAILED
            else -> record.status
        }
        return DetailedSolutionPreparation(record.manifestId, record.manifestSha256, record.manifest, status, items,
            if (complete) DetailedSolutionCodec.solutionsSha256(record.manifest, items.map { requireNotNull(it.document) }) else null)
    }

    override suspend fun prepare(manifestId: String, agentConsentGranted: Boolean) = withContext(Dispatchers.IO) {
        var manifest = requireNotNull(database.readAnswerManifest(manifestId))
        if (manifest.status in setOf(DetailedSolutionPreparationStatus.CANCELLED, DetailedSolutionPreparationStatus.STALE)) return@withContext
        if (!database.updateAnswerManifest(manifestId, manifest.stateVersion, DetailedSolutionPreparationStatus.RUNNING, maxOf(clock(), manifest.updatedAtEpochMillis))) return@withContext
        manifest = requireNotNull(database.readAnswerManifest(manifestId))
        activeManifests[manifestId] = manifest.manifest
        try {
            for (question in manifest.manifest.questions) {
                currentCoroutineContext().ensureActive()
                if (database.readAnswerManifest(manifestId)?.status != DetailedSolutionPreparationStatus.RUNNING) return@withContext
                locks.getOrPut(detailedSolutionId(question.basis)) { Mutex() }.withLock {
                    try { prepareQuestion(question, agentConsentGranted) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) {
                        database.readDetailedSolution(detailedSolutionId(question.basis))?.takeIf {
                            it.status in setOf(DetailedSolutionPreparationStatus.QUEUED, DetailedSolutionPreparationStatus.RUNNING, DetailedSolutionPreparationStatus.READY)
                        }?.let { fail(it, "题面、答案或图片无法读取，请检查后重试") }
                    }
                }
            }
            val prepared = requireNotNull(read(manifestId))
            val current = requireNotNull(database.readAnswerManifest(manifestId))
            if (current.status == DetailedSolutionPreparationStatus.RUNNING) database.updateAnswerManifest(manifestId, current.stateVersion,
                if (prepared.complete) DetailedSolutionPreparationStatus.READY else DetailedSolutionPreparationStatus.FAILED, maxOf(clock(), current.updatedAtEpochMillis))
        } finally { activeManifests.remove(manifestId) }
    }

    private suspend fun prepareQuestion(question: FrozenExportQuestion, consent: Boolean) {
        val id = detailedSolutionId(question.basis)
        var row = database.readDetailedSolution(id) ?: run {
            val now = clock()
            database.createDetailedSolution(PersistedDetailedSolution(id, ModelTaskRequest(requestId = "$id.0",
                input = DetailedSolutionInput(question.copy(ordinal = 1), promptBody()), occurredAtEpochMillis = now, agentConsentGranted = consent), createdAtEpochMillis = now))
        }
        if (row.status == DetailedSolutionPreparationStatus.READY) { verifyPrepared(row); return }
        if (row.status in setOf(DetailedSolutionPreparationStatus.FAILED, DetailedSolutionPreparationStatus.CANCELLED, DetailedSolutionPreparationStatus.STALE)) return
        if (!consent) { fail(row, "请先在设置中启用智能体，再准备缺失的答案"); return }
        verifySources(question)
        val wasRunning = row.status == DetailedSolutionPreparationStatus.RUNNING
        if (!wasRunning) {
            row = move(row, DetailedSolutionPreparationStatus.RUNNING, "正在准备详细答案") ?: return
        }
        if (row.output == null) {
            val task = database.readModelTask(row.request.requestId)
            val safe = task == null || task.attemptCount == 0 && task.status in setOf(ModelTaskStatus.WAITING_FOR_MODEL, ModelTaskStatus.QUEUED)
            if (task?.status != ModelTaskStatus.SUCCEEDED && !safe) { fail(row, "上次准备已中断，请重试；已完成的答案会保留"); return }
            val completed = task?.takeIf { it.status == ModelTaskStatus.SUCCEEDED } ?: tasks.execute(row.request).last()
            val output = completed.output as? DetailedSolutionOutput
            if (completed.status != ModelTaskStatus.SUCCEEDED || output == null) { fail(row, completed.failure?.message?.take(500) ?: "未能准备完整答案，请检查模型设置"); return }
            val next = row.copy(output = output, stateVersion = row.stateVersion + 1, updatedAtEpochMillis = maxOf(clock(), row.updatedAtEpochMillis), message = "正在整理答案配图")
            if (!database.updateDetailedSolution(next)) return
            row = next
        } else if (wasRunning && row.figures.map { it.blockId }.toSet() != row.output?.document?.requiredFigureBlockIds) {
            fail(row, "上次配图已中断，请重试；已完成的图片会保留"); return
        }
        val output = requireNotNull(row.output)
        for (figure in output.figureProposals) {
            currentCoroutineContext().ensureActive()
            if (row.figures.any { it.blockId == figure.blockId }) continue
            val capabilities = figureCapabilities()
            val source = if (figure.proposal.imageOperation == FigureImageOperation.EDIT) {
                val grant = row.input.question.sourceImages.singleOrNull() ?: run { fail(row, "这张讲解图需要明确的一张源图"); return }
                val asset = requireNotNull(database.readCanonicalSourceAsset(grant.assetId))
                FigureSourceEvidence(asset.sourceAssetId, asset.contentSha256, asset.mimeType, assets.verifiedBytes(asset))
            } else null
            val budget = FigureGenerationBudget()
            val request = FigureGenerationRequest(detailedSolutionFigureOperationId(id, figure.blockId), FigureGenerationPurpose.EXPLANATION,
                FigureGenerationTarget(requireNotNull(figure.proposal.fidelity)), capabilities, budget,
                FigureDispatchParent.ModelTask(row.request.requestId, reserveChatFinalAnswer = false), source,
                problemContext = row.input.question.document.title?.take(2_000),
                prompt = if (capabilities.availableMethods.isEmpty()) null else FigurePromptAssembler.assemble(FigureGenerationPurpose.EXPLANATION, capabilities, budget),
                sourceKind = FigureGenerationSourceKind.PROBLEM_REVISION, sourceId = question.basis.revisionId,
                basisFingerprint = detailedSolutionFigureBasis(row.request, output, figure.blockId))
            val result = coordinator.generate(request, figure.proposal)
            val artifact = result.artifact as? FigureArtifact.ImageAsset
            val attempt = result.attemptId
            if (artifact == null || attempt == null) { fail(row, result.diagnostics?.take(500) ?: "讲解配图暂时无法生成，请重试"); return }
            require(artifact.contentSha256 == figureSha256Hex(artifact.imageBytes()))
            val asset = assets.persist(artifact, attempt, clock())
            row = database.attachDetailedSolutionFigure(id, row.stateVersion, figure.blockId, attempt, asset, maxOf(clock(), row.updatedAtEpochMillis)) ?: return
        }
        verifyPrepared(row)
        move(row, DetailedSolutionPreparationStatus.READY, "答案已准备好")
    }

    override suspend fun cancel(manifestId: String) {
        val record = database.readAnswerManifest(manifestId) ?: return
        if (record.status == DetailedSolutionPreparationStatus.STALE) return
        if (!database.updateAnswerManifest(manifestId, record.stateVersion, DetailedSolutionPreparationStatus.CANCELLED, maxOf(clock(), record.updatedAtEpochMillis))) return
        record.manifest.questions.forEach { question ->
            val shared = activeManifests.any { (other, manifest) -> other != manifestId && manifest.questions.any { it.basis == question.basis } }
            if (!shared) database.readDetailedSolution(detailedSolutionId(question.basis))?.takeIf { it.status in setOf(DetailedSolutionPreparationStatus.QUEUED, DetailedSolutionPreparationStatus.RUNNING) }?.let { row ->
                if (move(row, DetailedSolutionPreparationStatus.CANCELLED, "已停止准备") != null) tasks.cancel(row.request.requestId)
            }
        }
    }

    override suspend fun retry(manifestId: String, agentConsentGranted: Boolean) {
        require(agentConsentGranted) { "请先在设置中启用智能体" }
        val manifest = requireNotNull(database.readAnswerManifest(manifestId))
        require(manifest.status != DetailedSolutionPreparationStatus.STALE) { "卷面已变化，请重新准备答案" }
        manifest.manifest.questions.forEach { question ->
            val id = detailedSolutionId(question.basis)
            locks.getOrPut(id) { Mutex() }.withLock {
                var current = database.readDetailedSolution(id) ?: return@withLock
                if (current.status == DetailedSolutionPreparationStatus.READY) {
                    try { verifyPrepared(current); return@withLock }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { current = move(current, DetailedSolutionPreparationStatus.FAILED, "配图已失效，等待重新准备") ?: return@withLock }
                }
                if (current.status !in setOf(DetailedSolutionPreparationStatus.FAILED, DetailedSolutionPreparationStatus.CANCELLED)) return@withLock
                val generation = current.generation + 1
                val intactFigures = current.figures.filter { ref ->
                    try { val asset = requireNotNull(database.readCanonicalSourceAsset(ref.assetId)); require(asset.contentSha256 == ref.contentSha256); assets.verifiedBytes(asset); true }
                    catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { false }
                }
                val request = if (current.output != null) current.request else ModelTaskRequest(requestId = "$id.$generation",
                    input = current.input.copy(figureGenerationPromptBody = promptBody()), occurredAtEpochMillis = clock(), agentConsentGranted = agentConsentGranted)
                database.updateDetailedSolution(current.copy(request = request, generation = generation, stateVersion = current.stateVersion + 1, figures = intactFigures,
                    status = DetailedSolutionPreparationStatus.QUEUED, message = "等待重新准备", updatedAtEpochMillis = maxOf(clock(), current.updatedAtEpochMillis)))
            }
        }
        database.updateAnswerManifest(manifestId, manifest.stateVersion, DetailedSolutionPreparationStatus.QUEUED, maxOf(clock(), manifest.updatedAtEpochMillis))
    }

    override suspend fun invalidate(manifestId: String) {
        val record = database.readAnswerManifest(manifestId) ?: return
        database.updateAnswerManifest(manifestId, record.stateVersion, DetailedSolutionPreparationStatus.STALE, maxOf(clock(), record.updatedAtEpochMillis))
    }

    private suspend fun verifySources(question: FrozenExportQuestion) {
        question.sourceImages.forEach { grant ->
            val asset = requireNotNull(database.readCanonicalSourceAsset(grant.assetId))
            require(asset.contentSha256 == grant.sha256 && asset.byteSize == grant.byteSize && asset.width == grant.width && asset.height == grant.height)
            assets.verifiedBytes(asset)
        }
    }

    private suspend fun verifyPrepared(row: PersistedDetailedSolution) {
        row.figures.forEach { ref -> val asset = requireNotNull(database.readCanonicalSourceAsset(ref.assetId)); require(asset.contentSha256 == ref.contentSha256); assets.verifiedBytes(asset) }
        require(canReuseSolution(requireNotNull(row.document), row.basis, figuresIntact = true))
    }

    private suspend fun move(row: PersistedDetailedSolution, status: DetailedSolutionPreparationStatus, message: String): PersistedDetailedSolution? {
        val next = row.copy(status = status, message = message, stateVersion = row.stateVersion + 1, updatedAtEpochMillis = maxOf(clock(), row.updatedAtEpochMillis))
        return if (database.updateDetailedSolution(next)) next else null
    }
    private suspend fun fail(row: PersistedDetailedSolution, message: String) { move(row, DetailedSolutionPreparationStatus.FAILED, message) }
    private suspend fun promptBody(): String? {
        val capabilities = figureCapabilities()
        return if (capabilities.availableMethods.isEmpty()) null else FigurePromptAssembler.assemble(FigureGenerationPurpose.EXPLANATION, capabilities, FigureGenerationBudget()).body
    }

    companion object {
        fun create(context: Context, database: StudyDatabasePort, tasks: ModelTaskRepository, coordinator: FigureGenerationCoordinator?,
            configurationStore: ModelConfigurationStore?, networkRequestsAllowed: Boolean): RoomDetailedSolutionRepository {
            val vault = AndroidCanonicalAssetVault(context.applicationContext)
            return RoomDetailedSolutionRepository(database, tasks, coordinator ?: com.tingyun.smartmistakebook.core.data.visual.FigureGenerationCoordinationFactory.unavailable, object : MistakeEditAssetAccess {
                override fun verifiedBytes(asset: CanonicalSourceAssetRecord) = vault.resolve(asset).readBytes()
                override fun localUri(asset: CanonicalSourceAssetRecord) = vault.resolve(asset).toURI().toString()
                override suspend fun persist(artifact: FigureArtifact.ImageAsset, attemptId: String, at: Long): CanonicalSourceAssetRecord {
                    val id = mistakeEditFigureAssetId(attemptId)
                    database.readCanonicalSourceAsset(id)?.let { existing -> verifiedBytes(existing); return existing }
                    return vault.persistCleanImageBytes(artifact.imageBytes(), artifact.mimeType, "DETAILED_SOLUTION", at,
                        GeneratedFigureProvenance(artifact.modelId ?: "unknown", id, artifact.purpose.name), id)
                }
            }, { configurationStore?.let { openGuardedFigureImageBackend(it, networkRequestsAllowed) }?.use { FigureGenerationCapabilities.IMAGE_MODEL_ONLY } ?: FigureGenerationCapabilities.NONE })
        }
    }
}
