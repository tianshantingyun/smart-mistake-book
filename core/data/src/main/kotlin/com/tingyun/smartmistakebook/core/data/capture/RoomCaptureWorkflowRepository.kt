package com.tingyun.smartmistakebook.core.data.capture

import android.content.Context
import android.net.Uri
import android.util.Log
import com.tingyun.smartmistakebook.core.database.AppendProblemDraftSourceAssetCommand
import com.tingyun.smartmistakebook.core.database.CommitProblemDraftCommand
import com.tingyun.smartmistakebook.core.database.CommitTutorSessionCommand
import com.tingyun.smartmistakebook.core.database.ConfirmAndCommitProblemDraftFromWorkspaceCommand
import com.tingyun.smartmistakebook.core.database.CanonicalSourceAssetRecord
import com.tingyun.smartmistakebook.core.database.ConsumeProblemDraftEditWorkspaceCommand
import com.tingyun.smartmistakebook.core.database.CreateSplitImportJobCommand
import com.tingyun.smartmistakebook.core.database.ConfirmTutorSessionFromWorkspaceCommand
import com.tingyun.smartmistakebook.core.database.CreateProblemDraftCommand
import com.tingyun.smartmistakebook.core.database.EndTutorSessionCommand
import com.tingyun.smartmistakebook.core.database.ImmutablePayloadConflictException
import com.tingyun.smartmistakebook.core.database.ProblemDraftRevisionRecord
import com.tingyun.smartmistakebook.core.database.ProblemDraftSourceAssetRecord
import com.tingyun.smartmistakebook.core.database.ProblemDraftRecord
import com.tingyun.smartmistakebook.core.database.ProblemDraftEditWorkspaceRecord
import com.tingyun.smartmistakebook.core.database.ProblemDraftWriteResult
import com.tingyun.smartmistakebook.core.database.PendingCaptureDraftRecord
import com.tingyun.smartmistakebook.core.database.ReviseProblemDraftCommand
import com.tingyun.smartmistakebook.core.database.ReplaceProblemDraftCommand
import com.tingyun.smartmistakebook.core.database.SaveProblemDraftEditWorkspaceCommand
import com.tingyun.smartmistakebook.core.database.SplitImportQuestionSeed
import com.tingyun.smartmistakebook.core.database.SplitProblemDraftCommand
import com.tingyun.smartmistakebook.core.database.StudyDatabasePort
import com.tingyun.smartmistakebook.core.database.StudyDbValue
import com.tingyun.smartmistakebook.core.database.TutorSessionRecord
import com.tingyun.smartmistakebook.core.data.model.OpenAiImageGenerationChannel
import com.tingyun.smartmistakebook.core.data.model.sha256Hex
import com.tingyun.smartmistakebook.core.data.splitimport.RoomSplitImportRepository
import com.tingyun.smartmistakebook.core.domain.CaptureDraftImportRequest
import com.tingyun.smartmistakebook.core.domain.AppendCaptureDraftPageRequest
import com.tingyun.smartmistakebook.core.domain.CaptureDraftWorkspaceSnapshot
import com.tingyun.smartmistakebook.core.domain.SplitRegionDraftsRequest
import com.tingyun.smartmistakebook.core.domain.CaptureDraftSummary
import com.tingyun.smartmistakebook.core.domain.CaptureDraftSplitResult
import com.tingyun.smartmistakebook.core.domain.CaptureEntryOrigin
import com.tingyun.smartmistakebook.core.domain.CaptureInputSource
import com.tingyun.smartmistakebook.core.domain.CaptureSourcePage
import com.tingyun.smartmistakebook.core.domain.CaptureWorkflowRepository
import com.tingyun.smartmistakebook.core.domain.CleanImageGenerator
import com.tingyun.smartmistakebook.core.domain.CapturedProblemCommitSummary
import com.tingyun.smartmistakebook.core.domain.ModelTaskRepository
import java.io.File
import com.tingyun.smartmistakebook.core.domain.ConfirmCapturedProblemRequest
import com.tingyun.smartmistakebook.core.domain.ConfirmedTutorSession
import com.tingyun.smartmistakebook.core.domain.ConsumeCaptureDraftWorkspaceRequest
import com.tingyun.smartmistakebook.core.domain.EndTutorSessionWithoutSaveRequest
import com.tingyun.smartmistakebook.core.domain.EndTutorSessionWithoutSaveResult
import com.tingyun.smartmistakebook.core.domain.PendingCaptureItem
import com.tingyun.smartmistakebook.core.domain.PendingCaptureStage
import com.tingyun.smartmistakebook.core.domain.ResumableCaptureDraft
import com.tingyun.smartmistakebook.core.domain.ReplaceCaptureDraftRequest
import com.tingyun.smartmistakebook.core.domain.SaveTutorSessionRequest
import com.tingyun.smartmistakebook.core.domain.SaveCaptureDraftWorkspaceRequest
import com.tingyun.smartmistakebook.core.domain.SplitCaptureDraftRequest
import com.tingyun.smartmistakebook.core.model.CapturedQuestionDocument
import com.tingyun.smartmistakebook.core.model.CapturedQuestionDocumentFingerprint
import com.tingyun.smartmistakebook.core.model.CaptureDraftWorkspaceCodec
import com.tingyun.smartmistakebook.core.model.CaptureDraftWorkspaceFingerprint
import com.tingyun.smartmistakebook.core.model.ContentBlock
import com.tingyun.smartmistakebook.core.model.ImagePipelineClassifyOutput
import com.tingyun.smartmistakebook.core.model.ImagePipelineProblemKind
import com.tingyun.smartmistakebook.core.model.ModelTaskRequest
import com.tingyun.smartmistakebook.core.model.ModelTaskSnapshot
import com.tingyun.smartmistakebook.core.model.ModelTaskStatus
import com.tingyun.smartmistakebook.core.model.NormalizedSourceRegion
import com.tingyun.smartmistakebook.core.model.QuestionBlockEvidence
import com.tingyun.smartmistakebook.core.model.QuestionBlockProvenance
import com.tingyun.smartmistakebook.core.model.QuestionBlockReviewStatus
import com.tingyun.smartmistakebook.core.model.QuestionDocument
import com.tingyun.smartmistakebook.core.model.QuestionDocumentMarkdownProjection
import com.tingyun.smartmistakebook.core.model.WritingLayer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.map

class RoomCaptureWorkflowRepository internal constructor(
    private val database: StudyDatabasePort,
    private val assetVault: AndroidCanonicalAssetVault,
    private val localTextRecognizer: LocalQuestionTextRecognizer,
    private val cleanRedraw: CleanImageGenerator? = null,
    private val cleanRedrawScope: CoroutineScope? = null,
    private val modelTasks: ModelTaskRepository? = null,
    /**
     * Whether this build may send model rounds at all (flavour capability). A save path
     * only runs the model-decided redraw round when this returns true.
     */
    private val captureEgressAllowed: () -> Boolean = { false },
    /** Split-review ledger; null in tests that never split. */
    private val splitImports: RoomSplitImportRepository? = null,
) : CaptureWorkflowRepository {
    /**
     * AUD-09 净化作业执行器：把"分类 → 重绘 → 挂接"升级成有持久身份、可续跑、不重复付费的作业。
     * 装配就是它的依赖缝——出网门与调用点同一实值（[captureEgressAllowed]），分类轮走模型台账
     * （[modelTasks]，终态即重放），生成轮走 [cleanRedraw]（凭证门在实现里），挂接走 [database]。
     */
    private val cleanRedrawTasks = CleanRedrawTaskRunner(
        tasks = database,
        egressAllowed = captureEgressAllowed,
        readSourceAsset = { assetId -> database.readCanonicalSourceAsset(assetId) },
        // 读取时再核对一次（AUD-09：出网前逐位校验）：从"决定重绘"到真正出网之间文件仍可能
        // 被替换；核对不过即放弃重绘，题面保留原图，失败落 SOURCE_UNREADABLE。
        readSourceBytes = { record ->
            runCatching { assetVault.resolve(record).readBytes() }.getOrNull()
        },
        classify = { request -> classifyProblemKind(request) },
        generateClean = { record, bytes ->
            cleanRedraw?.generateClean(bytes, record.mimeType)
        },
        attach = { revisionId, bytes, mimeType ->
            attachCleanRedrawImage(
                problemRevisionId = revisionId,
                cleanImageBytes = bytes,
                cleanImageMimeType = mimeType,
            )
        },
    )

    override fun observePendingCaptures(): Flow<List<PendingCaptureItem>> =
        database.observePendingCaptureDrafts().map { records ->
            records.map { record -> record.toPendingCaptureItem() }
        }.flowOn(Dispatchers.IO)

    override suspend fun readPendingCapture(draftId: String): ResumableCaptureDraft? =
        withContext(Dispatchers.IO) {
            require(draftId.isNotBlank()) { "Pending capture draft id must not be blank" }
            database.readPendingCaptureDraft(draftId)?.toResumableCaptureDraft()
        }

    override suspend fun abandonPendingCapture(
        draftId: String,
        expectedRevisionNumber: Int,
        abandonedAtEpochMillis: Long,
    ): Boolean = withContext(Dispatchers.IO) {
        require(draftId.isNotBlank()) { "Pending capture draft id must not be blank" }
        require(expectedRevisionNumber > 0) { "Pending capture revision must be positive" }
        require(abandonedAtEpochMillis > 0) {
            "Pending capture abandonment time must be positive"
        }
        database.abandonPendingProblemDraft(
            draftId = draftId,
            expectedRevisionNumber = expectedRevisionNumber,
            abandonedAtEpochMillis = abandonedAtEpochMillis,
        )
    }

    override suspend fun readDraftWorkspace(
        draftId: String,
    ): CaptureDraftWorkspaceSnapshot? = withContext(Dispatchers.IO) {
        require(draftId.isNotBlank()) { "Capture workspace draft id must not be blank" }
        database.readProblemDraftEditWorkspace(draftId)?.toDomainWorkspaceSnapshot()
    }

    override suspend fun saveDraftWorkspace(
        request: SaveCaptureDraftWorkspaceRequest,
    ): CaptureDraftWorkspaceSnapshot = withContext(Dispatchers.IO) {
        val encoded = CaptureDraftWorkspaceCodec.encode(request.workspace)
        val result = database.saveProblemDraftEditWorkspace(
            SaveProblemDraftEditWorkspaceCommand(
                draftId = request.draftId,
                basisRevisionNumber = request.basisRevisionNumber,
                expectedWorkspaceVersion = request.expectedWorkspaceVersion,
                expectedWorkspaceFingerprint = request.expectedWorkspaceFingerprint,
                snapshotSchemaVersion = request.workspace.schemaVersion,
                workspaceSnapshot = encoded,
                workspaceFingerprint = CaptureDraftWorkspaceFingerprint.ofEncoded(encoded),
                updatedAtEpochMillis = request.occurredAtEpochMillis,
            ),
        )
        result.workspace.toDomainWorkspaceSnapshot()
    }

    override suspend fun consumeDraftWorkspace(
        request: ConsumeCaptureDraftWorkspaceRequest,
    ): Boolean = withContext(Dispatchers.IO) {
        database.consumeProblemDraftEditWorkspace(
            ConsumeProblemDraftEditWorkspaceCommand(
                draftId = request.identity.draftId,
                basisRevisionNumber = request.identity.basisRevisionNumber,
                expectedWorkspaceVersion = request.identity.workspaceVersion,
                expectedWorkspaceFingerprint = request.identity.workspaceFingerprint,
            ),
        )
    }

    override suspend fun importDraft(request: CaptureDraftImportRequest): CaptureDraftSummary =
        withContext(Dispatchers.IO) {
            val draftId = stableId("draft", request.requestId)
            val imported = assetVault.import(
                localUri = request.localUri,
                sourceType = request.source.toDbValue(),
                createdAtEpochMillis = request.occurredAtEpochMillis,
            )
            val existing = database.readProblemDraft(draftId)
            val draft = if (existing != null) {
                validateImportReplay(existing, request, imported)
                existing
            } else {
                val created = createInitialDraft(draftId, request, imported)
                if (!created.created) validateImportReplay(created.draft, request, imported)
                created.draft
            }
            if (draft.currentRevision.author != StudyDbValue.ProblemDraftAuthor.CAPTURE_IMPORT) {
                return@withContext draft.toSummary()
            }
            recognizePendingDraft(draft)
        }

    override suspend fun appendDraftPage(
        request: AppendCaptureDraftPageRequest,
    ): CaptureDraftSummary = withContext(Dispatchers.IO) {
        val imported = assetVault.import(
            localUri = request.localUri,
            sourceType = request.source.toDbValue(),
            createdAtEpochMillis = request.occurredAtEpochMillis,
        )
        database.appendProblemDraftSourceAsset(
            AppendProblemDraftSourceAssetCommand(
                draftId = request.draftId,
                expectedRevisionNumber = request.expectedRevisionNumber,
                expectedSourceAssetCount = request.expectedPageCount,
                sourceAsset = imported,
                appendedAtEpochMillis = request.occurredAtEpochMillis,
            ),
        ).draft.toSummary()
    }

    override suspend fun replaceDraft(
        request: ReplaceCaptureDraftRequest,
    ): CaptureDraftSummary = withContext(Dispatchers.IO) {
        val replacementDraftId = stableId(
            "replacement-draft",
            "${request.replacedDraftId}:${request.requestId}",
        )
        val replaced = database.readProblemDraft(request.replacedDraftId)
            ?: error("Capture draft no longer exists")
        database.readProblemDraft(replacementDraftId)?.let { completed ->
            validateCompletedReplacement(replaced, completed, request)
            val replayedAsset = try {
                assetVault.import(
                    localUri = request.localUri,
                    sourceType = request.source.toDbValue(),
                    createdAtEpochMillis = request.occurredAtEpochMillis,
                )
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            replayedAsset?.let {
                if (!completed.sourceAsset.hasSameCanonicalContent(replayedAsset)) {
                    throw ImmutablePayloadConflictException(
                        "capture_replacement_request",
                        request.requestId,
                    )
                }
            }
            return@withContext recognizeReplacementBestEffort(completed)
        }
        val imported = assetVault.import(
            localUri = request.localUri,
            sourceType = request.source.toDbValue(),
            createdAtEpochMillis = request.occurredAtEpochMillis,
        )
        val replacementCommand = initialDraftCommand(
            draftId = replacementDraftId,
            sourceAsset = imported,
            origin = replaced.origin,
            occurredAtEpochMillis = request.occurredAtEpochMillis,
            requestFingerprint = replacementRequestFingerprint(replaced, request),
        )
        val replacement = database.replaceProblemDraft(
            ReplaceProblemDraftCommand(
                replacedDraftId = request.replacedDraftId,
                expectedReplacedRevisionNumber = request.expectedReplacedRevisionNumber,
                replacement = replacementCommand,
                replacedAtEpochMillis = request.occurredAtEpochMillis,
            ),
        ).replacement
        recognizeReplacementBestEffort(replacement)
    }

    override suspend fun splitDraft(
        request: SplitCaptureDraftRequest,
    ): CaptureDraftSplitResult = withContext(Dispatchers.IO) {
        val replaced = database.readProblemDraft(request.draftId)
            ?: error("Capture draft no longer exists")
        require(replaced.currentRevision.revisionNumber == request.expectedRevisionNumber) {
            "Capture draft changed before it could be split"
        }
        require(replaced.sourceAssets.size == 1 && replaced.sourceAsset.sourceAssetId == request.sourceAssetId) {
            "Only one-page captures can be split automatically"
        }
        val splitDraftIds = request.regions.mapIndexed { index, region ->
            splitDraftId(request, index, region)
        }
        val completed = splitDraftIds.map { splitDraftId ->
            database.readProblemDraft(splitDraftId)
        }
        if (completed.all { it != null }) {
            require(
                replaced.status == StudyDbValue.ProblemDraftStatus.ABANDONED &&
                    replaced.updatedAtEpochMillis == request.occurredAtEpochMillis,
            ) { "Capture split replay does not match the completed operation" }
            val splitDrafts = completed.filterNotNull().map { it.toSummary() }
            return@withContext CaptureDraftSplitResult(
                created = false,
                replacedDraftId = replaced.draftId,
                splitDrafts = splitDrafts,
                splitJobId = registerSplitJob(request, replaced, splitDrafts),
            )
        }
        require(completed.all { it == null }) { "Capture split is only partially present" }

        val croppedAssets = mutableListOf<CanonicalSourceAssetRecord>()
        try {
            request.regions.forEach { region ->
                croppedAssets += assetVault.crop(
                    source = replaced.sourceAsset,
                    region = region,
                    createdAtEpochMillis = request.occurredAtEpochMillis,
                )
            }
            val replacements = croppedAssets.mapIndexed { index, asset ->
                initialDraftCommand(
                    draftId = splitDraftIds[index],
                    sourceAsset = asset,
                    origin = replaced.origin,
                    occurredAtEpochMillis = request.occurredAtEpochMillis,
                    requestFingerprint = splitRequestFingerprint(
                        replaced = replaced,
                        request = request,
                        region = request.regions[index],
                        regionIndex = index,
                        cropped = asset,
                    ),
                )
            }
            val result = database.splitProblemDraft(
                SplitProblemDraftCommand(
                    replacedDraftId = replaced.draftId,
                    expectedReplacedRevisionNumber = request.expectedRevisionNumber,
                    replacements = replacements,
                    splitAtEpochMillis = request.occurredAtEpochMillis,
                ),
            )
            val splitDrafts = result.replacements.map { it.toSummary() }
            CaptureDraftSplitResult(
                created = result.created,
                replacedDraftId = replaced.draftId,
                splitDrafts = splitDrafts,
                splitJobId = registerSplitJob(request, replaced, splitDrafts),
            )
        } catch (failure: Exception) {
            croppedAssets.forEach { asset ->
                if (database.readCanonicalSourceAsset(asset.sourceAssetId) == null) {
                    runCatching { assetVault.delete(asset) }
                }
            }
            throw failure
        }
    }

    /**
     * Register the split-review job the review page reads. Runs after the draft
     * split so each question row can carry its pre-created draft id; replay-safe
     * because the deterministic fingerprint and job id reuse the stored job.
     */
    private suspend fun registerSplitJob(
        request: SplitCaptureDraftRequest,
        replaced: ProblemDraftRecord,
        splitDrafts: List<CaptureDraftSummary>,
    ): String? {
        val splitImports = splitImports ?: return null
        val jobId = "capture-split:" + request.requestId
        val command = CreateSplitImportJobCommand(
            jobId = jobId,
            sourceKind = StudyDbValue.SplitImportSourceKind.SINGLE_PAGE,
            sourceFingerprint = requestFingerprint(
                "capture-split-job-v1",
                request.requestId,
                request.draftId,
                request.assessmentRequestId,
                request.occurredAtEpochMillis.toString(),
            ),
            sourceUri = Uri.fromFile(assetVault.resolve(replaced.sourceAsset)).toString(),
            pageCount = 1,
            createdAtEpochMillis = request.occurredAtEpochMillis,
        )
        val questions = request.regions.mapIndexed { index, region ->
            SplitImportQuestionSeed(
                left = region.left,
                top = region.top,
                right = region.right,
                bottom = region.bottom,
                pageIndex = 0,
                prioritised = true,
                splitDraftId = splitDrafts.getOrNull(index)?.draftId,
            )
        }
        splitImports.createSplitJob(command, questions)
        // A false result means the job was already past PREPARING (a replay
        // promoted it, or the student discarded it first), so it is not worth
        // escalating. The case that does matter — a crash between these two
        // writes — is repaired by the startup pass in
        // RoomSplitImportRepository.reconcileStuckJobs.
        splitImports.markReady(jobId, questions.size, request.occurredAtEpochMillis)
        return jobId
    }

    override suspend fun createSplitRegionDrafts(
        request: SplitRegionDraftsRequest,
    ): List<CaptureDraftSummary> = withContext(Dispatchers.IO) {
        val source = database.readCanonicalSourceAsset(request.sourceAssetId)
            ?: error("Split source asset no longer exists")
        request.regions.mapIndexed { index, region ->
            val draftId = stableId(
                "batch-split-draft",
                "${request.requestId}:${request.sourceAssetId}:$index:${region.fingerprintValue()}",
            )
            database.readProblemDraft(draftId)?.toSummary() ?: run {
                val cropped = assetVault.crop(
                    source = source,
                    region = region,
                    createdAtEpochMillis = request.occurredAtEpochMillis,
                )
                try {
                    database.createProblemDraft(
                        initialDraftCommand(
                            draftId = draftId,
                            sourceAsset = cropped,
                            origin = request.origin.toDbValue(),
                            occurredAtEpochMillis = request.occurredAtEpochMillis,
                            requestFingerprint = requestFingerprint(
                                "batch-split-draft-v1",
                                request.requestId,
                                request.sourceAssetId,
                                index.toString(),
                                region.fingerprintValue(),
                                cropped.contentSha256,
                            ),
                        ),
                    ).draft.toSummary()
                } catch (failure: Exception) {
                    if (database.readCanonicalSourceAsset(cropped.sourceAssetId) == null) {
                        runCatching { assetVault.delete(cropped) }
                    }
                    throw failure
                }
            }
        }
    }

    override suspend fun confirmAndCommit(
        request: ConfirmCapturedProblemRequest,
    ): CapturedProblemCommitSummary =
        withContext(Dispatchers.IO) {
            val draft = database.readProblemDraft(request.draftId)
                ?: error("Capture draft no longer exists")
            require(draft.origin == StudyDbValue.CaptureOrigin.LIBRARY) {
                "Only mistake-library captures may be saved during confirmation"
            }
            val originalFile = assetVault.resolve(draft.sourceAsset)
            val expected = request.workspaceIdentity.toDatabaseExpectation()
            val suffix = confirmationStableSuffix(request.workspaceIdentity)
            val outcome = database.confirmAndCommitProblemDraftFromWorkspace(
                ConfirmAndCommitProblemDraftFromWorkspaceCommand(
                    workspace = expected,
                    commit = CommitProblemDraftCommand(
                        commandId = "commit-$suffix",
                        draftId = request.draftId,
                        expectedRevisionNumber = expected.basisRevisionNumber + 1,
                        problemId = "problem-$suffix",
                        problemRevisionId = "revision-$suffix",
                        practiceUnitId = "practice-$suffix",
                        errorBookEntryId = "entry-$suffix",
                        estimatedSeconds = DEFAULT_ESTIMATED_SECONDS,
                        committedAtEpochMillis = expected.finalOccurredAtEpochMillis,
                    ),
                ),
            )
            val receipt = outcome.commit.receipt
            val summary = CapturedProblemCommitSummary(
                draftId = request.draftId,
                problemId = receipt.problemId,
                problemRevisionId = receipt.problemRevisionId,
                practiceUnitId = receipt.practiceUnitId,
                errorBookEntryId = receipt.errorBookEntryId,
                created = outcome.commit.created,
                reusedExistingProblem = outcome.reusedExistingProblem,
            )
            // 切分台账的提交回执（T4/AUD-03）：**这一份草稿真的入库了**是 SAVED 的唯一依据，
            // 所以这一笔写在拿到回执的地方，而不是等界面观察到"保存成功"。两个反面都是真失败：
            // 提前预标会把没交的题显示成已录入；放在界面事件里则可能"题已经进错题本、台账还写
            // 待提交"（提交落库与界面事件之间被杀进程）。不是切分来的草稿这里什么都不会命中。
            // 放在重绘之前：重绘要等模型轮，标 SAVED 不该被它拖住。
            recordSplitImportCommit(request.draftId, expected.finalOccurredAtEpochMillis)
            // Model-decided clean redraw: the model looks at the photo and decides
            // whether it is figure-bearing. Only WITH_FIGURE problems are redrawn;
            // text-only problems keep the original. Never blocks or fails the commit —
            // a missing/failing model, generator, or classify round leaves the revision
            // with the original photo only.
            if (outcome.commit.created) {
                decideAndRedraw(
                    sourceAsset = draft.sourceAsset,
                    revisionId = receipt.problemRevisionId,
                    subjectId = draft.draftId,
                )
            }
            summary
        }

    /**
     * Runs one clean-redraw job for a just-committed problem revision (AUD-09).
     *
     * 触发范围口径（测试同批钉住）：**只有提交成功（`created == true`）触发**；纯文字题
     * （TEXT_ONLY）**不动作**——分类轮判定之后作业直接落 `SUCCEEDED`，不生成、不挂接。
     *
     * 装配了 [cleanRedrawScope] 时整段作业进后台（提交调用不等它：分类轮与生成轮都可能出网，
     * 这正是"保存要快、作业慢慢做"的既有取舍）；没装配时内联（测试路径）。两条路径都
     * fail-closed：作业的失败只落台账与日志，绝不把已经落库的提交改判成失败。
     */
    private suspend fun decideAndRedraw(
        sourceAsset: CanonicalSourceAssetRecord,
        revisionId: String,
        subjectId: String,
    ) {
        // 没有模型任务链 = 这个装配根本不发模型轮（与 `captureEgressAllowed` 同一口径的
        // 装配侧门）：不建作业行、不记账——没有尝试过就没有欠账。
        if (modelTasks == null) return
        val identity = CleanRedrawTaskIdentity(
            revisionId = revisionId,
            sourceAssetId = sourceAsset.sourceAssetId,
            subjectId = subjectId,
        )
        val scope = cleanRedrawScope
        if (scope != null) {
            scope.launch { runCleanRedrawJob(identity) }
        } else {
            runCleanRedrawJob(identity)
        }
    }

    /**
     * 跑一次净化作业并把结局**记录下来**（AUD-09 ⑤："返回值不再丢弃"）。
     *
     * 不吞取消（进程/作用域收尾不是失败）；其余异常只记录——提交早已落库，作业的意外
     * 失败不许把它改判成失败。行本身留在台账里，由下次启动的对账按尝试上限续跑。
     */
    private suspend fun runCleanRedrawJob(identity: CleanRedrawTaskIdentity) {
        val outcome = try {
            cleanRedrawTasks.run(identity)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Log.w(TAG, "Clean redraw ${identity.revisionId} failed before its ledger write", failure)
            return
        }
        Log.i(TAG, "Clean redraw ${identity.revisionId} -> $outcome")
    }

    /**
     * AUD-09 ④ 启动对账：接上一进程留下的净化作业接着做完（`PENDING`/`FAILED` 且尝试次数
     * 未耗尽），返回本次**实际动过**的行数。
     *
     * [staleBeforeEpochMillis] 是租约判据（应用传"本进程启动时刻"）：本进程刚发起的作业不会被
     * 对账抢走双跑（那会重复付费），而上一进程被杀留下的行一定早于它。出网门关着时一行也不动
     * （fail-closed）——留待下次启动。
     */
    override suspend fun resumePendingCleanRedraws(
        staleBeforeEpochMillis: Long,
    ): Int = withContext(Dispatchers.IO) {
        require(staleBeforeEpochMillis >= 0) {
            "A clean redraw resume cutoff must not be negative"
        }
        val processed = try {
            cleanRedrawTasks.resumePending(staleBeforeEpochMillis = staleBeforeEpochMillis)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Log.w(TAG, "Clean redraw resume pass failed", failure)
            0
        }
        if (processed > 0) {
            Log.i(TAG, "Resumed $processed clean redraw task(s)")
        }
        processed
    }

    /** 分类轮（AUD-09 ①③）：终端快照的产物是分类判断才认；失败给 null（调用方落失败码）。 */
    private suspend fun classifyProblemKind(request: ModelTaskRequest): ImagePipelineProblemKind? {
        val tasks = modelTasks ?: return null
        return try {
            val terminal = tasks.execute(request).last()
            if (terminal.status != ModelTaskStatus.SUCCEEDED) return null
            (terminal.output as? ImagePipelineClassifyOutput)?.problemKind
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun attachCleanRedrawImage(
        problemRevisionId: String,
        cleanImageBytes: ByteArray,
        cleanImageMimeType: String,
    ): Boolean = withContext(Dispatchers.IO) {
        require(cleanImageMimeType == "image/jpeg" || cleanImageMimeType == "image/png") {
            "Clean redraw must be a JPEG or PNG"
        }
        // A2：重绘图也是**模型生成的图**——sourceType 用 GENERATED_FIGURE（与工具链同值），
        // 编号按输入字节的 sha 稳定派生（同一张重绘重复落盘是同一行同一文件），并把隐式 AI
        // 标识写进文件字节；canonical 行 + CLEAN_IMAGE 引用由 attachCleanRedrawAsset 建立。
        val figureId = "clean-" + sha256Hex(cleanImageBytes)
        val asset = assetVault.persistCleanImageBytes(
            bytes = cleanImageBytes,
            mimeType = cleanImageMimeType,
            sourceType = StudyDbValue.SourceAssetType.GENERATED_FIGURE,
            createdAtEpochMillis = System.currentTimeMillis(),
            provenance = GeneratedFigureProvenance(
                model = OpenAiImageGenerationChannel.DEFAULT_MODEL_ID,
                figureId = figureId,
                purpose = "CLEAN_REDRAW",
            ),
            figureIdentity = figureId,
        )
        database.attachCleanRedrawAsset(
            revisionId = problemRevisionId,
            asset = asset,
        )
    }

    override suspend fun confirmForTutoring(
        request: ConfirmCapturedProblemRequest,
    ): ConfirmedTutorSession = withContext(Dispatchers.IO) {
        val draft = database.readProblemDraft(request.draftId)
            ?: error("Capture draft no longer exists")
        require(draft.origin == StudyDbValue.CaptureOrigin.TUTOR) {
            "Only tutor captures may create a tutor session"
        }
        assetVault.resolve(draft.sourceAsset)
        val expected = request.workspaceIdentity.toDatabaseExpectation()
        database.confirmTutorSessionFromWorkspace(
            ConfirmTutorSessionFromWorkspaceCommand(
                workspace = expected,
                sessionId = "tutor-session-${confirmationStableSuffix(request.workspaceIdentity)}",
            ),
        ).session.toDomainTutorSession()
    }

    override suspend fun readTutorSession(sessionId: String): ConfirmedTutorSession? =
        withContext(Dispatchers.IO) {
            require(sessionId.isNotBlank()) { "Tutor session id must not be blank" }
            database.readTutorSession(sessionId)?.toDomainTutorSession()
        }

    override suspend fun readTutorSessionSheetBytes(sessionId: String): ByteArray? =
        withContext(Dispatchers.IO) {
            require(sessionId.isNotBlank()) { "Tutor session id must not be blank" }
            val session = database.readTutorSession(sessionId)?.toDomainTutorSession()
                ?: return@withContext null
            // 出网前核对（2026-09-14）：题面字节必须与**规范记录**逐位一致。此前按
            // session.sourceImageUri 直接读文件，是图片链上唯一还按"路径"取字节的地方——
            // 磁盘上的文件被替换或损坏时，这些字节会被原样 POST 给模型；而其它出网路径
            // 一律经 vault.resolve(record) 核对 sha256 与字节数后才读。
            // 记录缺失或校验不过 → null：调用方按"没有图"处理，这条链本就 fail-closed。
            val record = database.readProblemDraft(session.draftId)?.sourceAsset
                ?: return@withContext null
            runCatching { assetVault.resolve(record).readBytes() }.getOrNull()
        }

    override suspend fun saveTutorSession(
        request: SaveTutorSessionRequest,
    ): CapturedProblemCommitSummary = withContext(Dispatchers.IO) {
        val session = database.readTutorSession(request.sessionId)
            ?: error("Tutor session no longer exists")
        assetVault.resolve(session.sourceAsset)
        val suffix = stableSuffix(request.requestId)
        val outcome = database.commitTutorSession(
            CommitTutorSessionCommand(
                sessionId = request.sessionId,
                commit = CommitProblemDraftCommand(
                    commandId = "commit-$suffix",
                    draftId = session.draftId,
                    expectedRevisionNumber = session.draftRevisionNumber,
                    problemId = "problem-$suffix",
                    problemRevisionId = "revision-$suffix",
                    practiceUnitId = "practice-$suffix",
                    errorBookEntryId = "entry-$suffix",
                    estimatedSeconds = DEFAULT_ESTIMATED_SECONDS,
                    committedAtEpochMillis = request.occurredAtEpochMillis,
                ),
            ),
        )
        CapturedProblemCommitSummary(
            draftId = session.draftId,
            problemId = outcome.commit.receipt.problemId,
            problemRevisionId = outcome.commit.receipt.problemRevisionId,
            practiceUnitId = outcome.commit.receipt.practiceUnitId,
            errorBookEntryId = outcome.commit.receipt.errorBookEntryId,
            created = outcome.commit.created,
            reusedExistingProblem = outcome.reusedExistingProblem,
        ).also { summary ->
            // 同一笔回执语义（T4/AUD-03）：讲题来源的切分题在会话保存入库时才算真的进了错题本
            // ——「结束讲解不保存」走 endTutorSessionWithoutSaving，这里不会被调到。
            recordSplitImportCommit(session.draftId, request.occurredAtEpochMillis)
            // Model-decided clean redraw for a tutor session saved into the
            // mistake book; identical fail-closed semantics to library commits.
            if (summary.created) {
                decideAndRedraw(
                    sourceAsset = session.sourceAsset,
                    revisionId = summary.problemRevisionId,
                    subjectId = session.draftId,
                )
            }
        }
    }

    /**
     * 把「这份草稿真的入库了」写回切分台账（T4/AUD-03）。台账没配（测试里的非切分装配）
     * 或这份草稿不是切分来的，这一笔就什么也不做——回执落在台账之外不是错误。
     *
     * 不吞异常：这里的失败是数据库失败，不是"这份草稿不是切分来的"；静默吞掉会让一道已经
     * 入库的题永远显示待提交，正是本轨要消灭的那种假象。
     */
    private suspend fun recordSplitImportCommit(draftId: String, occurredAtEpochMillis: Long) {
        splitImports?.recordCommittedDraft(draftId, occurredAtEpochMillis)
    }

    override suspend fun endTutorSessionWithoutSaving(
        request: EndTutorSessionWithoutSaveRequest,
    ): EndTutorSessionWithoutSaveResult = withContext(Dispatchers.IO) {
        val result = database.endTutorSession(
            EndTutorSessionCommand(
                sessionId = request.sessionId,
                endedAtEpochMillis = request.occurredAtEpochMillis,
            ),
        )
        EndTutorSessionWithoutSaveResult(
            sessionId = result.sessionId,
            draftId = result.draftId,
            endedAtEpochMillis = result.endedAtEpochMillis,
            created = result.created,
        )
    }

    private fun TutorSessionRecord.toDomainTutorSession(): ConfirmedTutorSession {
        require(origin == StudyDbValue.CaptureOrigin.TUTOR) {
            "Tutor session refers to a non-tutor capture"
        }
        val subject = confirmedRevision.subject
            ?: error("Tutor session has no confirmed subject")
        val sourceFile = assetVault.resolve(sourceAsset)
        return ConfirmedTutorSession(
            sessionId = sessionId,
            draftId = draftId,
            draftRevisionNumber = draftRevisionNumber,
            subject = subject,
            title = confirmedRevision.title,
            questionDocument = confirmedRevision.questionDocument,
            sourceImageUri = Uri.fromFile(sourceFile).toString(),
            createdAtEpochMillis = createdAtEpochMillis,
            isSaved = commitReceipt != null,
            isEndedWithoutSave = draftStatus == StudyDbValue.ProblemDraftStatus.ABANDONED,
            errorBookEntryId = commitReceipt?.errorBookEntryId,
        )
    }

    private fun PendingCaptureDraftRecord.toPendingCaptureItem(): PendingCaptureItem {
        val workspaceSnapshot = editWorkspace?.toDomainWorkspaceSnapshot()
        val workspace = workspaceSnapshot?.workspace
        val sessionId = validTutorSessionId()
        val sourceAvailable = runCatching { assetVault.resolve(draft.sourceAsset) }.isSuccess
        val stage = when {
            !sourceAvailable -> PendingCaptureStage.SOURCE_UNAVAILABLE
            sessionId != null -> PendingCaptureStage.TUTOR_SESSION_READY
            else -> stageFor(validatedTasks())
        }
        return PendingCaptureItem(
            draftId = draft.draftId,
            origin = draft.origin.toDomainOrigin(),
            subject = if (workspace != null) workspace.subject else draft.currentRevision.subject,
            title = workspace?.workingDocument?.document?.title
                ?.takeIf(String::isNotBlank)
                ?: if (workspace == null) draft.currentRevision.title else "新拍题目",
            currentRevisionNumber = draft.currentRevision.revisionNumber,
            updatedAtEpochMillis = maxOf(
                draft.updatedAtEpochMillis,
                workspaceSnapshot?.updatedAtEpochMillis ?: 0,
            ),
            stage = stage,
            tutorSessionId = sessionId.takeIf {
                stage == PendingCaptureStage.TUTOR_SESSION_READY
            },
        )
    }

    private fun PendingCaptureDraftRecord.toResumableCaptureDraft(): ResumableCaptureDraft {
        val sourcePages = draft.toDomainSourcePages()
        val sourceFile = assetVault.resolve(draft.sourceAsset)
        val tasks = validatedTasks()
        val workspaceSnapshot = editWorkspace?.toDomainWorkspaceSnapshot()
        val workspace = workspaceSnapshot?.workspace
        val document = workspace?.workingDocument ?: draft.currentRevision.questionDocument
        val title = workspace?.workingDocument?.document?.title
            ?.takeIf(String::isNotBlank)
            ?: if (workspace == null) draft.currentRevision.title else "新拍题目"
        return ResumableCaptureDraft(
            draftId = draft.draftId,
            origin = draft.origin.toDomainOrigin(),
            sourceImageUri = Uri.fromFile(sourceFile).toString(),
            sourceAssetId = draft.sourceAsset.sourceAssetId,
            sourceAssetSha256 = draft.sourceAsset.contentSha256,
            sourceWidth = draft.sourceAsset.width,
            sourceHeight = draft.sourceAsset.height,
            sourceByteSize = draft.sourceAsset.byteSize,
            sourcePages = sourcePages,
            draftCreatedAtEpochMillis = draft.createdAtEpochMillis,
            currentRevisionNumber = draft.currentRevision.revisionNumber,
            currentRevisionDocumentFingerprint = draft.currentRevision.documentFingerprint,
            currentRevisionCreatedAtEpochMillis = draft.currentRevision.createdAtEpochMillis,
            subject = if (workspace != null) workspace.subject else draft.currentRevision.subject,
            title = title,
            questionDocument = document,
            transcription = QuestionDocumentMarkdownProjection.project(document.document).trim(),
            writingLayer = document.captureWritingLayer(),
            latestAssessmentTask = tasks.assessment,
            sourcePageAssessmentTasks = tasks.assessments,
            latestParseTask = tasks.parse,
            tutorSessionId = validTutorSessionId(),
            workspace = workspaceSnapshot,
            updatedAtEpochMillis = maxOf(
                draft.updatedAtEpochMillis,
                workspaceSnapshot?.updatedAtEpochMillis ?: 0,
            ),
        )
    }

    private suspend fun createInitialDraft(
        draftId: String,
        request: CaptureDraftImportRequest,
        imported: CanonicalSourceAssetRecord,
    ): ProblemDraftWriteResult {
        return database.createProblemDraft(
            initialDraftCommand(
                draftId = draftId,
                sourceAsset = imported,
                origin = request.origin.toDbValue(),
                occurredAtEpochMillis = request.occurredAtEpochMillis,
                requestFingerprint = importRequestFingerprint(request, imported),
            ),
        )
    }

    private fun initialDraftCommand(
        draftId: String,
        sourceAsset: CanonicalSourceAssetRecord,
        origin: String,
        occurredAtEpochMillis: Long,
        requestFingerprint: String,
    ): CreateProblemDraftCommand {
        val initialDocument = CapturedQuestionDocument(
            document = QuestionDocument(
                id = "document-$draftId",
                blocks = listOf(ContentBlock.Paragraph(id = "stem", markdown = "")),
            ),
            blockEvidence = listOf(
                QuestionBlockEvidence(
                    blockId = "stem",
                    sourceAssetId = sourceAsset.sourceAssetId,
                    sourceRegion = FULL_IMAGE_REGION,
                    writingLayer = WritingLayer.UNKNOWN,
                    provenance = QuestionBlockProvenance.IMPORTED_STRUCTURE,
                    reviewStatus = QuestionBlockReviewStatus.NEEDS_REVIEW,
                    producerVersion = CAPTURE_IMPORT_VERSION,
                ),
            ),
        )
        val revision = ProblemDraftRevisionRecord(
            draftId = draftId,
            revisionNumber = 1,
            basisRevisionNumber = null,
            subject = null,
            title = "新拍题目",
            questionDocument = initialDocument,
            documentFingerprint = CapturedQuestionDocumentFingerprint.of(initialDocument),
            author = StudyDbValue.ProblemDraftAuthor.CAPTURE_IMPORT,
            createdAtEpochMillis = occurredAtEpochMillis,
        )
        return CreateProblemDraftCommand(
            sourceAsset = sourceAsset,
            draftId = draftId,
            origin = origin,
            initialRevision = revision,
            requestFingerprint = requestFingerprint,
        )
    }

    private suspend fun recognizeReplacementBestEffort(
        replacement: ProblemDraftRecord,
    ): CaptureDraftSummary {
        if (replacement.currentRevision.author != StudyDbValue.ProblemDraftAuthor.CAPTURE_IMPORT) {
            return replacement.toSummary()
        }
        return try {
            recognizePendingDraft(replacement)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            replacement.toSummary(recognitionFailed = true)
        }
    }

    private suspend fun recognizePendingDraft(
        draft: ProblemDraftRecord,
    ): CaptureDraftSummary {
        val canonicalFile = assetVault.resolve(draft.sourceAsset)
        val recognition = try {
            localTextRecognizer.recognize(
                canonicalFile = canonicalFile,
                imageWidth = draft.sourceAsset.width,
                imageHeight = draft.sourceAsset.height,
            )
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return draft.toSummary(recognitionFailed = true)
        }
        val document = LocalOcrQuestionDocumentMapper.map(
            draftId = draft.draftId,
            sourceAssetId = draft.sourceAsset.sourceAssetId,
            recognition = recognition,
        )
        val revision = ProblemDraftRevisionRecord(
            draftId = draft.draftId,
            revisionNumber = draft.currentRevision.revisionNumber + 1,
            basisRevisionNumber = draft.currentRevision.revisionNumber,
            subject = null,
            title = "新拍题目",
            questionDocument = document,
            documentFingerprint = CapturedQuestionDocumentFingerprint.of(document),
            author = StudyDbValue.ProblemDraftAuthor.LOCAL_OCR,
            createdAtEpochMillis = draft.currentRevision.createdAtEpochMillis,
        )
        val revised = try {
            database.reviseProblemDraft(
                ReviseProblemDraftCommand(
                    draftId = draft.draftId,
                    expectedRevisionNumber = draft.currentRevision.revisionNumber,
                    revision = revision,
                ),
            ).draft
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            val latest = database.readProblemDraft(draft.draftId)
            if (latest != null && latest.currentRevision.revisionNumber > draft.currentRevision.revisionNumber) {
                latest
            } else {
                throw failure
            }
        }
        return revised.toSummary()
    }

    private fun ProblemDraftRecord.toSummary(
        recognitionFailed: Boolean = false,
    ) =
        CaptureDraftSummary(
            draftId = draftId,
            sourceAssetId = sourceAsset.sourceAssetId,
            sourceAssetSha256 = sourceAsset.contentSha256,
            revisionNumber = currentRevision.revisionNumber,
            width = sourceAsset.width,
            height = sourceAsset.height,
            byteSize = sourceAsset.byteSize,
            status = status,
            recognition = recognitionSummary(recognitionFailed),
            sourcePages = toDomainSourcePages(),
        )

    private fun ProblemDraftRecord.toDomainSourcePages(): List<CaptureSourcePage> =
        sourceAssets.map { page ->
            val asset = page.sourceAsset
            CaptureSourcePage(
                pageIndex = page.pageIndex,
                imageUri = Uri.fromFile(assetVault.resolve(asset)).toString(),
                sourceAssetId = asset.sourceAssetId,
                sourceAssetSha256 = asset.contentSha256,
                width = asset.width,
                height = asset.height,
                byteSize = asset.byteSize,
                createdAtEpochMillis = asset.createdAtEpochMillis,
            )
        }

    private companion object {
        const val CAPTURE_IMPORT_VERSION = "capture-import-v1"
        const val DEFAULT_ESTIMATED_SECONDS = 180
        val FULL_IMAGE_REGION = NormalizedSourceRegion(0.0, 0.0, 1.0, 1.0)

        /** 净化作业的日志标签（AUD-09 ⑤：结局要留痕，不只是"失败被吞"）。 */
        const val TAG = "CleanRedraw"
    }
}

object CaptureWorkflowRepositoryFactory {
    fun create(
        context: Context,
        database: StudyDatabasePort,
        cleanRedraw: CleanImageGenerator? = null,
        cleanRedrawScope: CoroutineScope? = null,
        modelTasks: ModelTaskRepository? = null,
        captureEgressAllowed: () -> Boolean = { false },
        splitImports: RoomSplitImportRepository? = null,
    ): CaptureWorkflowRepository =
        RoomCaptureWorkflowRepository(
            database = database,
            assetVault = AndroidCanonicalAssetVault(context.applicationContext),
            localTextRecognizer = MlKitChineseQuestionTextRecognizer(context.applicationContext),
            cleanRedraw = cleanRedraw,
            cleanRedrawScope = cleanRedrawScope,
            modelTasks = modelTasks,
            captureEgressAllowed = captureEgressAllowed,
            splitImports = splitImports,
        )
}
