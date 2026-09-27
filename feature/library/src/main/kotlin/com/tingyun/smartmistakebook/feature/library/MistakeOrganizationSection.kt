package com.tingyun.smartmistakebook.feature.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tingyun.smartmistakebook.core.domain.ConfirmedMistakeOrganization
import com.tingyun.smartmistakebook.core.domain.KnowledgeBaseAvailability
import com.tingyun.smartmistakebook.core.domain.KnowledgeBaseNotReadyException
import com.tingyun.smartmistakebook.core.domain.MistakeOrganizationPreparation
import com.tingyun.smartmistakebook.core.domain.MistakeOrganizationRepository
import com.tingyun.smartmistakebook.core.domain.MistakeRevisionKey
import com.tingyun.smartmistakebook.core.domain.ModelTaskRepository
import com.tingyun.smartmistakebook.core.domain.StudyCatalogEntry
import com.tingyun.smartmistakebook.core.domain.StudyProfileOverview
import com.tingyun.smartmistakebook.core.domain.isReady
import com.tingyun.smartmistakebook.core.model.ModelEgressManifest
import com.tingyun.smartmistakebook.core.model.ModelEgressPurpose
import com.tingyun.smartmistakebook.core.model.ModelExecutionLocation
import com.tingyun.smartmistakebook.core.model.ModelPromptPolicyVersions
import com.tingyun.smartmistakebook.core.model.ModelTaskKind
import com.tingyun.smartmistakebook.core.model.ModelTaskRequest
import com.tingyun.smartmistakebook.core.model.ModelTaskSnapshot
import com.tingyun.smartmistakebook.core.model.ModelTaskStatus
import com.tingyun.smartmistakebook.core.model.ProblemOrganizationInput
import com.tingyun.smartmistakebook.core.model.ProblemOrganizationOutput
import com.tingyun.smartmistakebook.core.model.ProviderCapabilitySnapshot
import com.tingyun.smartmistakebook.core.model.PROBLEM_ORGANIZATION_CONTENT_DIMENSIONS
import com.tingyun.smartmistakebook.core.model.PROBLEM_ORGANIZATION_RELATION_KINDS
import com.tingyun.smartmistakebook.core.model.ActionType
import com.tingyun.smartmistakebook.core.model.AppFailure
import com.tingyun.smartmistakebook.core.model.AppFailureCode
import com.tingyun.smartmistakebook.core.model.Retryability
import com.tingyun.smartmistakebook.core.model.appFailure
import com.tingyun.smartmistakebook.core.ui.Ink
import com.tingyun.smartmistakebook.core.ui.InkSecondary
import com.tingyun.smartmistakebook.core.ui.JadeActive
import com.tingyun.smartmistakebook.core.ui.JadeSoft
import com.tingyun.smartmistakebook.core.ui.Outline
import com.tingyun.smartmistakebook.core.ui.PaperDivider
import com.tingyun.smartmistakebook.core.ui.SectionHeader
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun MistakeOrganizationSection(
    key: MistakeRevisionKey,
    organizationRepository: MistakeOrganizationRepository,
    modelTasks: ModelTaskRepository,
    profile: StudyProfileOverview,
    catalogEntries: List<StudyCatalogEntry> = emptyList(),
    onOpenRelatedMistake: (String) -> Unit = {},
    onOpenModelSettings: () -> Unit,
    /**
     * 知识能力就绪位（D-Q3）。未就绪时本屏不发起整理（改前是 prepare 内联触发一次安装，
     * 首装 16.7 秒卡在这里），只如实说"准备中"；就绪后本组合自动重来一遍。
     */
    knowledgeBaseAvailability: KnowledgeBaseAvailability = KnowledgeBaseAvailability.Ready,
) {
    val scope = rememberCoroutineScope()
    var provider by remember { mutableStateOf<ProviderCapabilitySnapshot?>(null) }
    var capabilityLoadFailed by rememberSaveable(key) { mutableStateOf(false) }
    var capabilityLoadAttempt by rememberSaveable(key) { mutableStateOf(0) }
    var preparation by remember(key) { mutableStateOf<MistakeOrganizationPreparation?>(null) }
    var task by remember(key) { mutableStateOf<ModelTaskSnapshot?>(null) }
    var recoveryComplete by remember(key, modelTasks) { mutableStateOf(false) }
    var trackedRequestId by remember(key, modelTasks) { mutableStateOf<String?>(null) }
    var requestToResume by remember(key, modelTasks) {
        mutableStateOf<ModelTaskRequest?>(null)
    }
    var locallyResumedRequestId by remember(key, modelTasks) { mutableStateOf<String?>(null) }
    var recoveredPendingRequest by remember(key, modelTasks) {
        mutableStateOf<ModelTaskRequest?>(null)
    }
    var isContinuingPausedOrganization by remember(key, modelTasks) { mutableStateOf(false) }
    var message by rememberSaveable(key) { mutableStateOf<String?>(null) }
    var error by remember(key) { mutableStateOf<AppFailure?>(null) }
    var isPreparing by rememberSaveable(key) { mutableStateOf(false) }
    var attempt by rememberSaveable(key) { mutableStateOf(0) }
    // 整理与拍照/讲题同口径：准备完成即自动发起（见下方 LaunchedEffect）；
    // 这个标志只是学生主动取消后本次会话内的退路。
    var organizationDisabledByUser by rememberSaveable(key) { mutableStateOf(false) }
    var applyState by remember(key) {
        mutableStateOf<AutomaticOrganizationState>(AutomaticOrganizationState.Idle)
    }
    var applyRetry by rememberSaveable(key) { mutableStateOf(0) }
    /** 已自动发起过执行的请求 id：自动只做一次，失败后留下手动重试。 */
    var autoStartedRequestId by rememberSaveable(key) { mutableStateOf<String?>(null) }
    var correctionVisible by rememberSaveable(key) { mutableStateOf(false) }
    val confirmedFlow = remember(key, organizationRepository) {
        organizationRepository.observeConfirmed(key)
    }
    val confirmed by confirmedFlow.collectAsStateWithLifecycle(
        initialValue = ConfirmedMistakeOrganization(),
    )
    val restartOrganization: () -> Unit = {
        attempt += 1
        preparation = null
        task = null
        trackedRequestId = null
        requestToResume = null
        recoveredPendingRequest = null
        isContinuingPausedOrganization = false
        applyState = AutomaticOrganizationState.Idle
        correctionVisible = false
        organizationDisabledByUser = false
        autoStartedRequestId = null
        message = null
        error = null
    }
    val retryAutomaticApply: () -> Unit = {
        applyState = AutomaticOrganizationState.Idle
        applyRetry += 1
    }
    val startPreparedOrganization: () -> Unit = {
        scope.launch {
            message = null
            error = null
            try {
                val now = System.currentTimeMillis()
                val prepared = checkNotNull(preparation)
                val approvedRequest = prepared.request.copy(
                    egressManifest = prepared.request.egressManifest?.copy(
                        approvedAtEpochMillis = now,
                    ),
                )
                preparation = prepared.copy(request = approvedRequest)
                trackedRequestId = approvedRequest.requestId
                modelTasks.execute(approvedRequest).collect { snapshot ->
                    task = snapshot
                    recoveredPendingRequest = snapshot.request.takeIf {
                        snapshot.status == ModelTaskStatus.RETRYABLE_FAILURE &&
                            it.egressManifest != null
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                error = appFailure(
                    code = AppFailureCode.NETWORK_UNAVAILABLE,
                    title = "整理没有完成",
                    message = "整理失败，请稍后重试",
                    dataPreserved = true,
                    retryability = Retryability.RETRYABLE,
                    primaryAction = ActionType.RETRY,
                )
            }
        }
    }
    val continueRecoveredOrganization: () -> Unit = continueRecoveredOrganization@{
        val persistedRequest = recoveredPendingRequest ?: return@continueRecoveredOrganization
        val availableProvider = provider ?: return@continueRecoveredOrganization
        if (
            !availableProvider.supports(ModelTaskKind.PROBLEM_CLASSIFY) ||
            availableProvider.executionLocation == ModelExecutionLocation.UNAVAILABLE
        ) {
            error = appFailure(
                code = AppFailureCode.PROVIDER_CAPABILITY_MISMATCH,
                title = "暂时无法继续整理",
                message = "暂时无法继续整理",
                dataPreserved = true,
                primaryAction = ActionType.OPEN_SETTINGS,
            )
            return@continueRecoveredOrganization
        }
        val resumedRequest = persistedRequest.renewOrganizationRequest(
            provider = availableProvider,
            approvedAtEpochMillis = System.currentTimeMillis(),
        )
        preparation = resumedRequest.toOrganizationPreparation()
        trackedRequestId = resumedRequest.requestId
        isContinuingPausedOrganization = true
        message = null
        error = null
        scope.launch {
            try {
                modelTasks.execute(resumedRequest).collect { snapshot ->
                    preparation = snapshot.toOrganizationPreparation()
                    trackedRequestId = snapshot.request.requestId
                    task = snapshot
                    recoveredPendingRequest = snapshot.request.takeIf {
                        snapshot.status == ModelTaskStatus.RETRYABLE_FAILURE &&
                            it.egressManifest != null
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                error = appFailure(
                    code = AppFailureCode.NETWORK_UNAVAILABLE,
                    title = "整理没有完成",
                    message = "整理失败，请稍后重试",
                    dataPreserved = true,
                    retryability = Retryability.RETRYABLE,
                    primaryAction = ActionType.RETRY,
                )
            } finally {
                isContinuingPausedOrganization = false
            }
        }
    }

    LaunchedEffect(modelTasks, capabilityLoadAttempt) {
        capabilityLoadFailed = false
        provider = null
        provider = try {
            modelTasks.capabilities()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            capabilityLoadFailed = true
            null
        }
    }

    // Preparing before durable history arrives can reuse a request id with a different timestamp.
    LaunchedEffect(key, modelTasks) {
        modelTasks.observeBySubject(key.problemRevisionId, ModelTaskKind.PROBLEM_CLASSIFY)
            .collect { snapshots ->
                if (!recoveryComplete) {
                    val matchingTasks = snapshots.filter { it.matchesOrganization(key) }
                    val recovered = matchingTasks.maxWithOrNull(
                        compareBy<ModelTaskSnapshot> { it.updatedAtEpochMillis }
                            .thenBy { it.stateVersion }
                            .thenBy { it.createdAtEpochMillis }
                            .thenBy { it.request.requestId },
                    )
                    recoveryComplete = true
                    if (recovered != null) {
                        trackedRequestId = recovered.request.requestId
                        preparation = recovered.toOrganizationPreparation()
                        task = recovered
                        attempt = maxOf(attempt, matchingTasks.distinctBy { it.request.requestId }.lastIndex)
                        if (
                            !recovered.status.isTerminal ||
                            recovered.status == ModelTaskStatus.RETRYABLE_FAILURE
                        ) {
                            recoveredPendingRequest = recovered.request
                        }
                    }
                } else {
                    snapshots.firstOrNull { it.request.requestId == trackedRequestId }
                        ?.takeIf { it.matchesOrganization(key) }
                        ?.let { persisted ->
                            preparation = persisted.toOrganizationPreparation()
                            task = persisted
                            if (
                                persisted.status.isTerminal &&
                                persisted.status != ModelTaskStatus.RETRYABLE_FAILURE &&
                                recoveredPendingRequest?.requestId == persisted.request.requestId
                            ) {
                                recoveredPendingRequest = null
                            }
                        }
                }
            }
    }

    LaunchedEffect(provider, recoveredPendingRequest?.requestId) {
        val persistedRequest = recoveredPendingRequest ?: return@LaunchedEffect
        val availableProvider = provider ?: return@LaunchedEffect
        if (
            availableProvider.executionLocation == ModelExecutionLocation.LOCAL_NO_EGRESS &&
            persistedRequest.egressManifest == null
        ) {
            recoveredPendingRequest = null
            requestToResume = persistedRequest
        }
    }

    LaunchedEffect(modelTasks, requestToResume?.requestId) {
        val persistedRequest = requestToResume ?: return@LaunchedEffect
        if (locallyResumedRequestId == persistedRequest.requestId) return@LaunchedEffect
        locallyResumedRequestId = persistedRequest.requestId
        message = null
        error = null
        try {
            modelTasks.execute(persistedRequest).collect { snapshot ->
                task = snapshot
                if (
                    snapshot.status == ModelTaskStatus.RETRYABLE_FAILURE &&
                    snapshot.request.egressManifest != null
                ) {
                    recoveredPendingRequest = snapshot.request
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            error = appFailure(
                code = AppFailureCode.NETWORK_UNAVAILABLE,
                title = "整理没有完成",
                message = "整理失败，请稍后重试",
                dataPreserved = true,
                retryability = Retryability.RETRYABLE,
                primaryAction = ActionType.RETRY,
            )
        }
    }

    LaunchedEffect(
        key,
        provider,
        attempt,
        organizationDisabledByUser,
        recoveryComplete,
        // D-Q3：就绪位进 key——内容就位后本效果自动重跑，学生不必手动再点一次（"就绪后自动放行"）。
        knowledgeBaseAvailability,
    ) {
        if (!recoveryComplete) return@LaunchedEffect
        // 知识内容没就位：不发起（也不在这里自己装一次），下方渲染"准备中"卡。
        if (!knowledgeBaseAvailability.isReady) return@LaunchedEffect
        val availableProvider = provider ?: return@LaunchedEffect
        if (
            organizationDisabledByUser ||
            preparation != null ||
            task != null ||
            !availableProvider.supports(ModelTaskKind.PROBLEM_CLASSIFY) ||
            availableProvider.executionLocation == ModelExecutionLocation.UNAVAILABLE
        ) {
            return@LaunchedEffect
        }
        isPreparing = true
        message = null
        error = null
        try {
            val now = System.currentTimeMillis()
            preparation = organizationRepository.prepare(
                key = key,
                profile = profile,
                provider = availableProvider,
                attempt = attempt,
                occurredAtEpochMillis = now,
                approvedAtEpochMillis = now,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (notReady: KnowledgeBaseNotReadyException) {
            // 竞态兜底（就绪位在发起的一瞬间变了）：这是"还没准备好"，不是"模型没配好"。
            // 不写 error、也不改 message——就绪位自己是事实来源，它会驱动本屏渲染
            // KnowledgePreparing 卡；这里只留下日志，方便把学生的反馈对上原因。
            android.util.Log.i(
                "MistakeOrganization",
                "prepare withheld: knowledge base not ready (${notReady.availability})",
            )
        } catch (_: Exception) {
            error = appFailure(
                code = AppFailureCode.PROVIDER_NOT_CONFIGURED,
                title = "暂时无法准备智能整理",
                message = "暂时无法准备智能整理",
                dataPreserved = true,
                primaryAction = ActionType.OPEN_SETTINGS,
            )
        } finally {
            isPreparing = false
        }
    }

    LaunchedEffect(task?.request?.requestId, task?.status, applyRetry) {
        val successfulTask = task?.takeIf { it.status == ModelTaskStatus.SUCCEEDED }
            ?: return@LaunchedEffect
        val requestId = successfulTask.request.requestId
        if (applyState.requestId == requestId) return@LaunchedEffect
        applyState = AutomaticOrganizationState.Applying(requestId)
        applyState = try {
            val confirmation = organizationRepository.applySuccessfulOrganization(requestId)
            if (confirmation.applied) {
                AutomaticOrganizationState.Applied(requestId)
            } else if (confirmation.preservedUserCorrection) {
                AutomaticOrganizationState.PreservedUserCorrection(requestId)
            } else {
                AutomaticOrganizationState.Incomplete(requestId)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            AutomaticOrganizationState.Failed(
                requestId = requestId,
                message = "这次整理没有完成，请再试一次",
            )
        }
    }

    PaperDivider(Modifier.padding(vertical = 18.dp))
    SectionHeader("智能整理")
    Spacer(Modifier.height(10.dp))
    ConfirmedOrganizationSummary(
        confirmed = confirmed,
        catalogEntries = catalogEntries,
        onOpenRelatedMistake = onOpenRelatedMistake,
    )
    Spacer(Modifier.height(12.dp))

    val currentProvider = provider
    val output = task?.output as? ProblemOrganizationOutput
    val surfaceState = resolveMistakeOrganizationSurface(
        MistakeOrganizationSurfaceFacts(
            providerAvailability = when {
                currentProvider == null && capabilityLoadFailed ->
                    OrganizationProviderAvailability.LOAD_FAILED
                currentProvider == null -> OrganizationProviderAvailability.LOADING
                !currentProvider.supports(ModelTaskKind.PROBLEM_CLASSIFY) ||
                    currentProvider.executionLocation == ModelExecutionLocation.UNAVAILABLE ->
                    OrganizationProviderAvailability.UNAVAILABLE
                else -> OrganizationProviderAvailability.READY
            },
            hasPreparation = preparation != null,
            isPreparing = isPreparing,
            preparationDismissed = organizationDisabledByUser,
            hasRecoveredRequest = recoveredPendingRequest != null,
            isContinuingRecoveredRequest = isContinuingPausedOrganization,
            taskStatus = task?.status,
            taskMessage = task?.userMessage,
            taskRequestId = task?.request?.requestId,
            hasUsableOutput = output != null,
            applyState = applyState,
            knowledgeBaseAvailability = knowledgeBaseAvailability,
        ),
    )
    // 整理与拍照/讲题同口径：准备完成即发起（发起即发送），不再逐次确认。
    // 自动执行每个请求只做一次——失败后停在卡片上由用户手动重试；
    // paused 的恢复请求保持手动"继续整理"（与"只显示一次继续"的边界一致）。
    val autoStartRequestId = (surfaceState as? MistakeOrganizationSurfaceState.Consent)
        ?.takeUnless { it.paused }
        ?.let { preparation?.request?.requestId }
    LaunchedEffect(autoStartRequestId) {
        val requestId = autoStartRequestId ?: return@LaunchedEffect
        if (autoStartedRequestId != requestId) {
            autoStartedRequestId = requestId
            startPreparedOrganization()
        }
    }
    when (val state = surfaceState) {
        MistakeOrganizationSurfaceState.CapabilityLoadFailed -> ModelCapabilityFailure(
            onRetry = { capabilityLoadAttempt += 1 },
            onOpenModelSettings = onOpenModelSettings,
        )

        MistakeOrganizationSurfaceState.CapabilityLoading,
        MistakeOrganizationSurfaceState.PreparationLoading,
        -> OrganizationLoading()

        MistakeOrganizationSurfaceState.ProviderUnavailable ->
            ModelUnavailable(onOpenModelSettings)

        // D-Q3：内容没就位时说的话，与"模型没配好"是两句不同的话，出路也不同。
        is MistakeOrganizationSurfaceState.KnowledgePreparing -> KnowledgePreparingCard(
            availability = state.availability,
        )

        is MistakeOrganizationSurfaceState.Consent -> {
            OrganizationConsentCard(
                preparation = checkNotNull(preparation),
                provider = checkNotNull(currentProvider),
                isRunning = state.running,
                isPaused = state.paused,
                onCancel = {
                    preparation = null
                    task = null
                    trackedRequestId = null
                    requestToResume = null
                    recoveredPendingRequest = null
                    message = null
                    organizationDisabledByUser = true
                },
                onApprove = if (state.paused) {
                    continueRecoveredOrganization
                } else {
                    startPreparedOrganization
                },
            )
        }

        MistakeOrganizationSurfaceState.PreparationRetry -> OutlinedButton(
            onClick = restartOrganization,
            modifier = Modifier.testTag("mistake_organization_prepare_retry"),
        ) { Text("再次整理") }

        is MistakeOrganizationSurfaceState.ExecutionFailed -> {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = JadeSoft.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, Outline),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(state.message, color = InkSecondary)
                    OutlinedButton(
                        onClick = restartOrganization,
                    ) { Text("重新生成") }
                }
            }
        }

        is MistakeOrganizationSurfaceState.Running -> OrganizationRunning(state.message)

        MistakeOrganizationSurfaceState.UnusableResult -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "这次整理结果无法使用",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("mistake_organization_unusable_result"),
                )
                OutlinedButton(
                    onClick = restartOrganization,
                    modifier = Modifier.testTag("mistake_organization_unusable_retry"),
                ) { Text("重新整理") }
            }
        }

        MistakeOrganizationSurfaceState.Applying -> OrganizationApplying()

        MistakeOrganizationSurfaceState.Incomplete -> IncompleteOrganizationCard(
            onRetry = restartOrganization,
        )

        is MistakeOrganizationSurfaceState.ApplyFailed -> OrganizationApplyFailure(
            message = state.message,
            onRetry = retryAutomaticApply,
        )

        MistakeOrganizationSurfaceState.PreservedUserCorrection -> Text(
            text = "已保留你修改过的分类",
            color = InkSecondary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.testTag("mistake_organization_user_correction_preserved"),
        )

        MistakeOrganizationSurfaceState.Applied -> {
            Text(
                text = "已自动整理到错题本",
                color = InkSecondary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("mistake_organization_applied"),
            )
            TextButton(
                onClick = { correctionVisible = !correctionVisible },
                modifier = Modifier.testTag("mistake_organization_correct_toggle"),
            ) {
                Text(if (correctionVisible) "收起修改" else "分类有误，修改")
            }
            if (correctionVisible) {
                OrganizationCorrectionEditor(
                    requestId = checkNotNull(task).request.requestId,
                    input = checkNotNull(task).request.input as ProblemOrganizationInput,
                    output = checkNotNull(output),
                    confirmed = confirmed,
                    organizationRepository = organizationRepository,
                    onConfirmed = {
                        correctionVisible = false
                        message = "已保存分类修改"
                    },
                    onFailure = { failure -> message = failure },
                )
            }
        }
    }
    message?.let {
        Spacer(Modifier.height(10.dp))
        Text(
            text = it,
            color = InkSecondary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.testTag("mistake_organization_message"),
        )
    }
    error?.let { organizationError ->
        Spacer(Modifier.height(10.dp))
        Text(
            text = organizationError.message,
            color = InkSecondary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.testTag("mistake_organization_error"),
        )
    }
    Spacer(Modifier.height(12.dp))
}

private fun ModelTaskSnapshot.matchesOrganization(key: MistakeRevisionKey): Boolean {
    val input = request.input as? ProblemOrganizationInput ?: return false
    return input.problemId == key.problemId && input.problemRevisionId == key.problemRevisionId
}

private fun ModelTaskSnapshot.toOrganizationPreparation(): MistakeOrganizationPreparation {
    return request.toOrganizationPreparation()
}

private fun ModelTaskRequest.toOrganizationPreparation(): MistakeOrganizationPreparation {
    val input = input as ProblemOrganizationInput
    return MistakeOrganizationPreparation(
        request = this,
        relatedCandidateTitles = input.relationCandidates.map { it.title },
        knowledgeContextCount = input.knowledgeBaseNodes.size,
    )
}

private fun ModelTaskRequest.renewOrganizationRequest(
    provider: ProviderCapabilitySnapshot,
    approvedAtEpochMillis: Long,
): ModelTaskRequest {
    val newRequestId = "problem-organization-resume:${UUID.randomUUID()}"
    val newManifest = when (provider.executionLocation) {
        ModelExecutionLocation.EXTERNAL_PROVIDER -> ModelEgressManifest(
            authorizationId = "authorization:${UUID.randomUUID()}",
            subjectId = input.subjectId,
            purpose = ModelEgressPurpose.CLASSIFICATION,
            authorizedTaskKinds = setOf(ModelTaskKind.PROBLEM_CLASSIFY),
            providerId = provider.providerId,
            modelId = provider.modelId,
            providerConfigurationVersion = provider.providerConfigurationVersion,
            promptPolicyVersion = ModelPromptPolicyVersions.PROBLEM_ORGANIZATION,
            approvedAtEpochMillis = maxOf(approvedAtEpochMillis, occurredAtEpochMillis),
            assets = emptyList(),
            disclosedData = ModelEgressManifest.PROBLEM_ORGANIZATION_DISCLOSURE,
        )
        ModelExecutionLocation.LOCAL_NO_EGRESS -> null
        ModelExecutionLocation.UNAVAILABLE -> error("Organization provider is unavailable")
    }
    return ModelTaskRequest(
        requestId = newRequestId,
        input = input,
        occurredAtEpochMillis = occurredAtEpochMillis,
        egressManifest = newManifest,
    )
}

@Composable
private fun OrganizationApplying() {
    Row(
        modifier = Modifier.testTag("mistake_organization_applying"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp)
        Text("正在整理到错题本…", modifier = Modifier.padding(start = 10.dp), color = InkSecondary)
    }
}

@Composable
private fun IncompleteOrganizationCard(onRetry: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("mistake_organization_incomplete"),
        shape = RoundedCornerShape(10.dp),
        color = JadeSoft.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, Outline),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("暂未整理完整", color = Ink, fontWeight = FontWeight.SemiBold)
            Text("现有分类不会被覆盖。", color = InkSecondary, style = MaterialTheme.typography.bodySmall)
            OutlinedButton(
                onClick = onRetry,
                modifier = Modifier.testTag("mistake_organization_retry"),
            ) { Text("再整理一次") }
        }
    }
}

@Composable
private fun OrganizationApplyFailure(message: String, onRetry: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("mistake_organization_apply_failure"),
        shape = RoundedCornerShape(10.dp),
        color = JadeSoft.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, Outline),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(message, color = InkSecondary)
            OutlinedButton(
                onClick = onRetry,
                modifier = Modifier.testTag("mistake_organization_apply_retry"),
            ) { Text("再整理一次") }
        }
    }
}

@Composable
private fun ConfirmedOrganizationSummary(
    confirmed: ConfirmedMistakeOrganization,
    catalogEntries: List<StudyCatalogEntry>,
    onOpenRelatedMistake: (String) -> Unit,
) {
    val contentClassifications = confirmed.classifications.filter {
        it.dimension in PROBLEM_ORGANIZATION_CONTENT_DIMENSIONS
    }
    val contentRelations = confirmed.relations.filter {
        it.kind in PROBLEM_ORGANIZATION_RELATION_KINDS
    }
    if (contentClassifications.isEmpty() && contentRelations.isEmpty()) return
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("mistake_organization_confirmed"),
        shape = RoundedCornerShape(10.dp),
        color = JadeSoft.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, Outline),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("已整理", color = Ink, fontWeight = FontWeight.SemiBold)
            contentClassifications.groupBy { it.dimension }.forEach { (dimension, items) ->
                Text(
                    text = "${dimension.contentLabel()}：${items.joinToString("、") { it.displayName }}",
                    color = InkSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (contentRelations.isNotEmpty()) {
                Text(
                    text = "相关题目",
                    color = Ink,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "相关题目会随整理结果更新。",
                    color = InkSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
                contentRelations.forEachIndexed { index, relation ->
                    val exactTarget = catalogEntries.firstOrNull { entry ->
                        entry.problemId == relation.targetProblemId &&
                            entry.problemRevisionId == relation.targetProblemRevisionId
                    }
                    val newerTarget = catalogEntries.firstOrNull { entry ->
                        entry.problemId == relation.targetProblemId
                    }
                    val targetTitle = exactTarget?.title ?: newerTarget?.title ?: "关联题"
                    val availability = when {
                        exactTarget != null -> "点按打开"
                        newerTarget != null -> "目标题面已更新，请重新整理关系"
                        else -> "已不在当前错题本"
                    }
                    val rowModifier = Modifier
                        .fillMaxWidth()
                        .testTag("mistake_confirmed_relation_$index")
                        .then(
                            if (exactTarget != null) {
                                Modifier.clickable {
                                    onOpenRelatedMistake(exactTarget.entryId)
                                }
                            } else {
                                Modifier
                            },
                        )
                    Surface(
                        modifier = rowModifier,
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                        border = BorderStroke(1.dp, Outline),
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Text(
                                text = checkNotNull(relation.kind.confirmedRelationLabel()),
                                color = JadeActive,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = targetTitle,
                                color = Ink,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = availability,
                                color = InkSecondary,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OrganizationLoading() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp)
        Text("正在准备智能整理…", modifier = Modifier.padding(start = 10.dp), color = InkSecondary)
    }
}

@Composable
private fun OrganizationRunning(message: String?) {
    Row(
        modifier = Modifier.testTag("mistake_organization_running"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp)
        Text(
            text = message?.takeIf { it.isNotBlank() } ?: "正在整理…",
            modifier = Modifier.padding(start = 10.dp),
            color = InkSecondary,
        )
    }
}

@Composable
private fun ModelUnavailable(onOpenModelSettings: () -> Unit) {
    Surface(
        color = JadeSoft.copy(alpha = 0.35f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Outline),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("配置大模型后，才能自动整理板块、知识点和相关题目。", color = InkSecondary)
            OutlinedButton(onClick = onOpenModelSettings) { Text("去配置模型") }
        }
    }
}

/**
 * 知识内容还没就位（D-Q3）。
 *
 * 两句话分开：[KnowledgeBaseAvailability.Preparing] 是"等一会就好"，
 * [KnowledgeBaseAvailability.Unavailable] 是"这次没准备好"（出路在顶部横幅的重试）。
 * 两者都不出现"知识包/安装/索引"这类内部词，也不把学生指向模型设置页——这里和模型配置无关。
 */
@Composable
private fun KnowledgePreparingCard(availability: KnowledgeBaseAvailability) {
    val message = when (availability) {
        KnowledgeBaseAvailability.Ready -> return
        KnowledgeBaseAvailability.Preparing ->
            "整理要用的知识目录还在准备中，准备好后这里会自动开始。"
        is KnowledgeBaseAvailability.Unavailable ->
            "整理要用的知识目录这次没能准备好，可以点上方提示里的重试再试一次。"
    }
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("mistake_organization_knowledge_preparing"),
        color = JadeSoft.copy(alpha = 0.35f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Outline),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (availability == KnowledgeBaseAvailability.Preparing) {
                CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp)
            }
            Text(
                text = message,
                modifier = Modifier.padding(start = 10.dp),
                color = InkSecondary,
            )
        }
    }
}

@Composable
private fun ModelCapabilityFailure(
    onRetry: () -> Unit,
    onOpenModelSettings: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("mistake_organization_capability_failure"),
        color = JadeSoft.copy(alpha = 0.35f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Outline),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("暂时无法读取模型配置", color = InkSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onRetry) { Text("重试") }
                TextButton(onClick = onOpenModelSettings) { Text("检查模型设置") }
            }
        }
    }
}

@Composable
private fun OrganizationConsentCard(
    preparation: MistakeOrganizationPreparation,
    provider: ProviderCapabilitySnapshot,
    isRunning: Boolean,
    isPaused: Boolean = false,
    onCancel: () -> Unit,
    onApprove: () -> Unit,
) {
    val knowledgeScope = if (preparation.knowledgeContextCount > 0) {
        "和相关学科知识目录"
    } else {
        ""
    }
    // 第六号消费点（D-Q3）：零命中此前完全无声——确认卡悄悄少一项，学生无从知道
    // "这次没有匹配到知识点"。就绪门已经把"内容还没装好"挡在外面，所以走到这里的
    // 零命中是真结论，如实说出来即可（它不改变发送范围，只是把范围讲清楚）。
    val knowledgeScopeNote = if (preparation.knowledgeContextCount > 0) {
        null
    } else {
        "这次没有匹配到对应的知识目录，只整理板块和题目之间的关系。"
    }
    Surface(
        modifier = Modifier.fillMaxWidth().testTag(
            if (isPaused) "mistake_organization_paused" else "mistake_organization_consent",
        ),
        shape = RoundedCornerShape(10.dp),
        color = JadeSoft.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, Outline),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(
                text = if (isPaused) "整理已暂停" else "确认本次发送范围",
                color = Ink,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "会把当前题面、${preparation.relatedCandidateTitles.size} 道同科目题面" +
                    "${knowledgeScope}发给${provider.providerDisplayName}，只用于整理板块、" +
                    "细化知识点和题目关系；不发送原图或学习记录。",
                color = InkSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
            knowledgeScopeNote?.let { note ->
                Text(
                    text = note,
                    modifier = Modifier.testTag("mistake_organization_no_knowledge_scope"),
                    color = InkSecondary,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onCancel, enabled = !isRunning) { Text("暂不整理") }
                Button(
                    onClick = onApprove,
                    enabled = !isRunning,
                    colors = ButtonDefaults.buttonColors(containerColor = JadeActive),
                    modifier = Modifier.testTag(
                        if (isPaused) "mistake_organization_continue" else "mistake_organization_approve",
                    ),
                ) {
                    if (isRunning) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(end = 8.dp).height(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                        )
                    }
                    Text(
                        when {
                            isPaused && isRunning -> "正在继续"
                            isPaused -> "继续整理"
                            isRunning -> "正在整理"
                            else -> "开始整理"
                        },
                    )
                }
            }
        }
    }
}
