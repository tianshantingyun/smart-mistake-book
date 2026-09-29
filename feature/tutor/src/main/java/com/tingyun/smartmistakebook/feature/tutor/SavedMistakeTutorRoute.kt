package com.tingyun.smartmistakebook.feature.tutor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tingyun.smartmistakebook.core.domain.AgentPendingRequestRepository
import com.tingyun.smartmistakebook.core.domain.MistakeDetailRepository
import com.tingyun.smartmistakebook.core.domain.LobbyMessageImageIntake
import com.tingyun.smartmistakebook.core.domain.MistakeDetailState
import com.tingyun.smartmistakebook.core.domain.ConfirmedMistakeOrganization
import com.tingyun.smartmistakebook.core.domain.MistakeOrganizationRepository
import com.tingyun.smartmistakebook.core.domain.MistakeRevisionKey
import com.tingyun.smartmistakebook.core.domain.ModelTaskRepository
import com.tingyun.smartmistakebook.core.domain.StudyCatalogEntry
import com.tingyun.smartmistakebook.core.domain.StudyProfileOverview
import com.tingyun.smartmistakebook.core.domain.StudyQuestionMemory
import com.tingyun.smartmistakebook.core.domain.TutorAttachedImageIntake
import com.tingyun.smartmistakebook.core.domain.TutorAttachedQuestionReader
import com.tingyun.smartmistakebook.core.domain.TutorConversationAreas
import com.tingyun.smartmistakebook.core.domain.TutorConversationRepository
import com.tingyun.smartmistakebook.core.domain.TutorInteractionRepository
import com.tingyun.smartmistakebook.core.domain.TutorKnowledgeContextLoader
import com.tingyun.smartmistakebook.core.domain.TutorRoundQuestionRetriever
import com.tingyun.smartmistakebook.core.domain.TutorSessionProblemAnchor
import com.tingyun.smartmistakebook.core.domain.TutorTeachingReferenceRepository
import com.tingyun.smartmistakebook.core.model.AttachedImage
import com.tingyun.smartmistakebook.core.model.ModelTaskKind
import com.tingyun.smartmistakebook.core.model.TutorPlanOutput
import com.tingyun.smartmistakebook.core.model.QuestionDocumentMarkdownProjection
import com.tingyun.smartmistakebook.core.model.TutorConversationIds
import com.tingyun.smartmistakebook.core.model.TutorDebriefOutput
import com.tingyun.smartmistakebook.core.model.TutorKnowledgeCode
import com.tingyun.smartmistakebook.core.model.TutorTeachingReference
import com.tingyun.smartmistakebook.core.ui.InkSecondary
import com.tingyun.smartmistakebook.core.ui.Ink
import com.tingyun.smartmistakebook.core.ui.JadeSoft
import com.tingyun.smartmistakebook.core.ui.LocalModeLine
import com.tingyun.smartmistakebook.core.ui.Outline
import com.tingyun.smartmistakebook.core.ui.SectionHeader
import com.tingyun.smartmistakebook.core.ui.StructuredContentRenderer
import com.tingyun.smartmistakebook.core.ui.studentSubjectLabel
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun SavedMistakeTutorRoute(
    key: MistakeRevisionKey,
    repository: MistakeDetailRepository,
    organizationRepository: MistakeOrganizationRepository,
    teachingReferenceRepository: TutorTeachingReferenceRepository,
    modelTasks: ModelTaskRepository,
    interactions: TutorInteractionRepository,
    /** 学生文字落库用；缺省 null 时该界面不落库（门控按空语料 fail-closed）。 */
    conversations: TutorConversationRepository? = null,
    catalogEntries: List<StudyCatalogEntry> = emptyList(),
    /**
     * 本轮候选菜单的本地检索源。错题讲题页拿到它才能组出"这一轮在说哪一道"的候选；
     * null 时菜单只剩"上一轮绑定的题"，本轮多半是无题轮。
     */
    roundQuestionRetriever: TutorRoundQuestionRetriever? = null,
    /** 加号菜单「从错题库选择」选中后的题面读取器；null 时该菜单项不出现。 */
    attachedQuestionReader: TutorAttachedQuestionReader? = null,
    /**
     * 知识点代号通道的预披露取数（D5）：已确认绑定节点 + 前置 → 未赋码条目；
     * null 时维持旧行为（无代号披露）。
     */
    knowledgeContextLoader: TutorKnowledgeContextLoader? = null,
    profile: StudyProfileOverview,
    learningMemory: StudyQuestionMemory? = null,
    /** 学生消息附图的资产读取器；null 时会话页不提供附图入口。 */
    imageIntake: LobbyMessageImageIntake? = null,
    /** 附图上库的落点（A4 执行路径 ③）：null = 这一页不接"把图存进错题本"。 */
    attachedImageIntake: TutorAttachedImageIntake? = null,
    /**
     * 打开错题本：给 id 就打开那道题的详情，给 null 只打开列表——与确认卡落点的
     * `openNotebook(problemId)` 同语义（A4 执行路径 ② 直达那道题，不再只落到列表）。
     */
    onOpenMistakeNotebook: (problemId: String?) -> Unit = {},
    onOpenProfile: () -> Unit = {},
    onOpenModelSettings: () -> Unit,
    /** 讲题历史入口；与大厅、拍照会话共用同一个页面标题栏，所以三个入口都有它。 */
    onOpenHistory: (() -> Unit)? = null,
    onBack: () -> Unit,
    /** Silent teaching-focus persistence (three-store loop); no UI surface. */
    onRecordTeachingFocus: (sessionId: String, practiceUnitId: String, labels: List<String>) -> Unit = { _, _, _ -> },
    /** Silent misconception debrief request on session exit; no UI surface. */
    onRequestDebrief: (sessionId: String, practiceUnitId: String, stemMarkdown: String, transcriptMarkdown: String, labels: List<String>) -> Unit = { _, _, _, _, _ -> },
    /** Silent misconception advisory write when a debrief completes. */
    onRecordMisconception: (sessionId: String, practiceUnitId: String, payloadMarkdown: String) -> Unit = { _, _, _ -> },
    /** Stored advisories injected into the tutor prompt (read side of the loop). */
    priorTeachingAdvisories: List<String> = emptyList(),
    /**
     * 模型要求的配图（重绘题面 / 生成过程图）的解析器。
     * null 时会话页里这类图**整段不渲染**——错题讲题页此前拿不到它，只有拍照会话传了。
     */
    attachedImageResolver: (suspend (AttachedImage) -> String?)? = null,
    /**
     * 确认卡的落库端口（A4）：null = 这个入口不接确认卡（无库界面与测试替身）。
     *
     * 接上之后这一页与拍照入口、智能体栏走**同一条**本地动作通道：模型提出请求 → 挂一张
     * **落库**的卡 → 学生点了才执行 → 裁决落终态并回喂下一轮。此前这一页没有它，模型在这一页
     * 申请的本地动作（"把这一轮的题存进错题本"）没有出口，只能在界面流里消失。
     */
    pendingRequests: AgentPendingRequestRepository? = null,
    modifier: Modifier = Modifier,
) {
    val stateFlow: Flow<MistakeDetailState> = remember(key, repository) {
        repository.observeExact(key)
    }
    val state by stateFlow.collectAsStateWithLifecycle(MistakeDetailState.Loading)
    val organizationFlow: Flow<ConfirmedMistakeOrganization?> = remember(key, organizationRepository) {
        organizationRepository.observeConfirmed(key)
    }
    val organization by organizationFlow.collectAsStateWithLifecycle(initialValue = null)
    val teachingReferences by produceState<List<TutorTeachingReference>>(
        initialValue = emptyList(),
        key1 = organization,
        key2 = state,
        key3 = teachingReferenceRepository,
    ) {
        val ready = state as? MistakeDetailState.Ready
        val confirmed = organization
        value = if (ready == null || confirmed == null) {
            emptyList()
        } else {
            try {
                teachingReferenceRepository.referencesFor(
                    subject = ready.detail.identity.subject,
                    knowledgeNodeIds = confirmed.knowledgeNodeIds,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                emptyList()
            }
        }
    }
    // 代号通道预披露（D5）：已确认绑定 + 前置（未赋码；K1..Kn 由仓库会话注册表分配）。
    // 与材料取数同样不静默：失败只影响代号披露（映射表空），不阻塞讲题。
    val knowledgePreDisclosures by produceState<List<TutorKnowledgeCode>>(
        initialValue = emptyList(),
        key1 = organization,
        key2 = state,
        key3 = knowledgeContextLoader,
    ) {
        val ready = state as? MistakeDetailState.Ready
        val confirmed = organization
        val loader = knowledgeContextLoader
        if (ready == null || confirmed == null || loader == null) {
            value = emptyList()
            return@produceState
        }
        try {
            value = loader.knowledgePreDisclosure(
                subject = ready.detail.identity.subject,
                confirmedBindingNodeIds = confirmed.knowledgeNodeIds.toList(),
                questionText = null,
            ).preDisclosures
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            value = emptyList()
        }
    }

    when (val current = state) {
        is MistakeDetailState.Ready -> if (organization == null) {
            // 非就绪帧（知识点归位还没读出来）也走**同一条交互面**：输入区常驻（A5），
            // 只是此刻确实还没有可以发过去的对象——原因写在输入区里。
            TutorConversationScreen(
                config = TutorSurfaceConfig(
                    header = {
                        TutorPageHeader(
                            onOpenCapabilitySettings = onOpenModelSettings,
                            onOpenHistory = onOpenHistory,
                            onBack = onBack,
                        )
                    },
                    composerPlaceholder = "这道题还没有打开",
                    liveAnswerTestTag = "saved_mistake_non_ready_reply",
                ),
                composer = {
                    TutorSurfaceComposer(
                        value = "",
                        onValueChange = {},
                        onSend = {},
                        block = TutorComposerAvailability(
                            providerReady = true,
                            questionNotReady = true,
                        ).block(),
                        placeholder = "这道题还没有打开",
                        reasonTestTag = "saved_mistake_non_ready_reason",
                    )
                },
                autoScrollVersion = current,
                modifier = modifier.testTag("saved_mistake_tutor_screen"),
            ) {
                item("saved_mistake_organization_loading") { LoadingTutorQuestion() }
            }
        } else {
            SavedMistakeTutorContent(
                state = current,
                modelTasks = modelTasks,
                interactions = interactions,
                conversations = conversations,
                catalogEntries = catalogEntries,
                roundQuestionRetriever = roundQuestionRetriever,
                attachedQuestionReader = attachedQuestionReader,
                profile = profile,
                learningMemory = learningMemory,
                imageIntake = imageIntake,
                relatedKnowledgeNodeIds = requireNotNull(organization).knowledgeNodeIds,
                reviewedTeachingReferences = teachingReferences,
                knowledgePreDisclosures = knowledgePreDisclosures,
                onOpenMistakeNotebook = onOpenMistakeNotebook,
                onOpenProfile = onOpenProfile,
                onOpenModelSettings = onOpenModelSettings,
                onOpenHistory = onOpenHistory,
                onBack = onBack,
                onRecordTeachingFocus = onRecordTeachingFocus,
                onRequestDebrief = onRequestDebrief,
                onRecordMisconception = onRecordMisconception,
                priorTeachingAdvisories = priorTeachingAdvisories,
                attachedImageResolver = attachedImageResolver,
                pendingRequests = pendingRequests,
                modifier = modifier.testTag("saved_mistake_tutor_screen"),
            )
        }

        // 找不到 / 旧快照 / 读不出来：同样是这条交互面 + 常驻输入区（A5），原因写在输入区里。
        else -> TutorConversationScreen(
            config = TutorSurfaceConfig(
                header = {
                    TutorPageHeader(
                        onOpenCapabilitySettings = onOpenModelSettings,
                        onOpenHistory = onOpenHistory,
                        onBack = onBack,
                    )
                },
                composerPlaceholder = "这道题还没有打开",
                liveAnswerTestTag = "saved_mistake_non_ready_reply",
            ),
            composer = {
                TutorSurfaceComposer(
                    value = "",
                    onValueChange = {},
                    onSend = {},
                    block = TutorComposerAvailability(
                        providerReady = true,
                        questionNotReady = true,
                    ).block(),
                    placeholder = "这道题还没有打开",
                    reasonTestTag = "saved_mistake_non_ready_reason",
                )
            },
            autoScrollVersion = current,
            modifier = modifier.testTag("saved_mistake_tutor_screen"),
        ) {
            item("saved_mistake_non_ready") {
                when (current) {
                    MistakeDetailState.Loading -> LoadingTutorQuestion()
                    is MistakeDetailState.Legacy -> TutorQuestionUnavailable(
                        title = "这道题需要重新拍摄",
                        detail = "旧题面不够完整，重新拍摄后即可讲解。",
                        onRetry = onBack,
                    )
                    is MistakeDetailState.CorruptSnapshot -> TutorQuestionUnavailable(
                        title = "题面需要重新上传",
                        detail = "这道题保存得不完整，重新拍摄后即可讲解。",
                        onRetry = onBack,
                    )
                    MistakeDetailState.NotFound -> TutorQuestionUnavailable(
                        title = "没有找到这道错题",
                        detail = "它可能已归档、删除或切换到了新的修订。",
                        onRetry = onBack,
                    )
                    is MistakeDetailState.Ready -> Unit
                }
            }
        }
    }
}

@Composable
internal fun SavedMistakeTutorContent(
    state: MistakeDetailState.Ready,
    modelTasks: ModelTaskRepository,
    interactions: TutorInteractionRepository,
    /** 学生文字落库用；缺省 null 时该界面不落库（门控按空语料 fail-closed）。 */
    conversations: TutorConversationRepository? = null,
    catalogEntries: List<StudyCatalogEntry> = emptyList(),
    /**
     * 本轮候选菜单的本地检索源；null 时菜单只剩"上一轮绑定的题"（见 [SavedMistakeTutorRoute]）。
     */
    roundQuestionRetriever: TutorRoundQuestionRetriever? = null,
    /**
     * 加号菜单「从错题库选择」选中后的题面读取器；null 时该菜单项不出现
     * （见 [SavedMistakeTutorRoute]）。
     */
    attachedQuestionReader: TutorAttachedQuestionReader? = null,
    profile: StudyProfileOverview,
    learningMemory: StudyQuestionMemory?,
    imageIntake: LobbyMessageImageIntake? = null,
    attachedImageIntake: TutorAttachedImageIntake? = null,
    relatedKnowledgeNodeIds: Set<String> = emptySet(),
    reviewedTeachingReferences: List<TutorTeachingReference> = emptyList(),
    knowledgePreDisclosures: List<TutorKnowledgeCode> = emptyList(),
    onOpenMistakeNotebook: (problemId: String?) -> Unit = {},
    onOpenProfile: () -> Unit = {},
    onOpenModelSettings: () -> Unit,
    onOpenHistory: (() -> Unit)? = null,
    onBack: () -> Unit = {},
    /** Silent teaching-focus persistence (three-store loop); no UI surface. */
    onRecordTeachingFocus: (sessionId: String, practiceUnitId: String, labels: List<String>) -> Unit = { _, _, _ -> },
    /** Silent misconception debrief request on session exit; no UI surface. */
    onRequestDebrief: (sessionId: String, practiceUnitId: String, stemMarkdown: String, transcriptMarkdown: String, labels: List<String>) -> Unit = { _, _, _, _, _ -> },
    /** Silent misconception advisory write when a debrief completes. */
    onRecordMisconception: (sessionId: String, practiceUnitId: String, payloadMarkdown: String) -> Unit = { _, _, _ -> },
    /** Stored advisories injected into the tutor prompt (read side of the loop). */
    priorTeachingAdvisories: List<String> = emptyList(),
    /** 模型要求的配图解析器；null 时会话页不渲染这类图（见 [SavedMistakeTutorRoute]）。 */
    attachedImageResolver: (suspend (AttachedImage) -> String?)? = null,
    /** 确认卡的落库端口（A4）：null = 这个入口不接确认卡（见 [SavedMistakeTutorRoute]）。 */
    pendingRequests: AgentPendingRequestRepository? = null,
    clock: () -> Long = System::currentTimeMillis,
    modifier: Modifier = Modifier,
) {
    // A1（进入不接管旧会话）：这次进入用的讲题会话 id 是显式的，每进入一次新开一个。
    // `rememberSaveable` 保证旋转/进程死亡之后仍是**同一次进入**的同一个会话（换一个 id
    // 就等于把学生正在说的这一轮丢掉），而"从历史列表点回来"走的是另一条显式 id 的路径。
    val entrySessionId = rememberSaveable(state.detail.identity) {
        "mistake-tutor:${UUID.randomUUID()}"
    }
    val question = remember(
        state.detail.identity,
        state.questionDocument,
        entrySessionId,
        learningMemory,
        relatedKnowledgeNodeIds,
        reviewedTeachingReferences,
        knowledgePreDisclosures,
    ) {
        savedMistakeTutorQuestion(
            sessionId = entrySessionId,
            state = state,
            learningMemory = learningMemory,
            relatedKnowledgeNodeIds = relatedKnowledgeNodeIds,
            reviewedTeachingReferences = reviewedTeachingReferences,
            priorTeachingAdvisories = priorTeachingAdvisories,
            knowledgeCodes = knowledgePreDisclosures,
        )
    }
    val identity = state.detail.identity
    // Three-store loop write side: silent by product decision.
    var debriefDraft by remember(question.sessionId) { mutableStateOf(DebriefDraft.EMPTY) }
    DisposableEffect(question.sessionId, modelTasks) {
        onDispose {
            // Silent debrief on session exit (user-approved, no UI surface).
            val stem = com.tingyun.smartmistakebook.core.model.QuestionDocumentMarkdownProjection
                .project(question.questionDocument.document)
            val draft = debriefDraft
            if (draft.labels.isNotEmpty() && stem.isNotBlank()) {
                onRequestDebrief(
                    question.sessionId,
                    identity.practiceUnitId,
                    stem.take(com.tingyun.smartmistakebook.core.model.TutorDebriefInput.MAX_DEBRIEF_STEM_CHARS),
                    draft.transcript.take(com.tingyun.smartmistakebook.core.model.TutorDebriefInput.MAX_DEBRIEF_TRANSCRIPT_CHARS),
                    draft.labels,
                )
            }
        }
    }
    LaunchedEffect(question.sessionId, modelTasks) {
        modelTasks
            .observeRecentBySubject(
                TutorConversationIds.captured(question.sessionId),
                ModelTaskKind.TUTOR_PLAN,
                limit = 8,
            )
            .distinctUntilChanged()
            .collect { tasks ->
                tasks.forEach { task ->
                    val output = task.output as? TutorPlanOutput ?: return@forEach
                    val labels = output.plan.targetedEvidenceLabels +
                        output.plan.inferredKnowledgeLabels
                    if (labels.isEmpty()) return@forEach
                    debriefDraft = debriefDraft.copy(labels = labels.distinct())
                    onRecordTeachingFocus(
                        output.sessionId,
                        identity.practiceUnitId,
                        labels,
                    )
                }
            }
    }
    LaunchedEffect(question.sessionId, modelTasks) {
        modelTasks
            .observeRecentBySubject(
                TutorConversationIds.captured(question.sessionId),
                ModelTaskKind.TUTOR_RESPOND,
                limit = 20,
            )
            .distinctUntilChanged()
            .collect { tasks ->
                val transcript = tasks
                    .sortedBy { it.createdAtEpochMillis }
                    .joinToString(separator = "\n") { task ->
                        (task.output as? com.tingyun.smartmistakebook.core.model.TutorRespondOutput)
                            ?.messageMarkdown
                            .orEmpty()
                    }
                debriefDraft = debriefDraft.copy(transcript = transcript)
            }
    }
    LaunchedEffect(question.sessionId, modelTasks, onRecordMisconception) {
        modelTasks
            .observeRecentBySubject(question.sessionId, ModelTaskKind.LEARNING_SUMMARIZE, limit = 4)
            .distinctUntilChanged()
            .collect { tasks ->
                tasks.forEach { task ->
                    val output = task.output as? TutorDebriefOutput ?: return@forEach
                    val misconception = output.misconceptionMarkdown ?: return@forEach
                    onRecordMisconception(
                        output.sessionId,
                        output.practiceUnitId,
                        misconception,
                    )
                }
            }
    }
    LaunchedEffect(question.sessionId, identity.problemRevisionId, identity.practiceUnitId) {
        interactions.anchorSession(
            savedMistakeTutorAnchor(
                sessionId = question.sessionId,
                problemRevisionId = identity.problemRevisionId,
                practiceUnitId = identity.practiceUnitId,
                anchoredAtEpochMillis = System.currentTimeMillis(),
            ),
        )
    }
    TutorModelPanel(
        question = question,
        profile = profile,
        modelTasks = modelTasks,
        interactions = interactions,
        conversations = conversations,
        catalogEntries = catalogEntries,
        roundQuestionRetriever = roundQuestionRetriever,
        attachedQuestionReader = attachedQuestionReader,
        imageIntake = imageIntake,
        onOpenModelSettings = onOpenModelSettings,
        // 模型要的配图（重绘图 / 过程图）要在这一页渲染出来：此前这里没传，整段被跳过。
        attachedImageResolver = attachedImageResolver,
        // 确认卡（A4）：模型在这一页申请的本地动作挂成库里的行，学生点了才执行。
        pendingRequests = pendingRequests,
        // 三条执行路径的落点：这一页的真实出口就是"打开错题本"（这道题已经在里面），
        // 与拍照入口、智能体栏是**同一个**装配函数，不另写一套。
        localActionLandings = tutorLocalActionLandings(
            openNotebook = onOpenMistakeNotebook,
            attachedImageIntake = attachedImageIntake,
        ),
        clock = clock,
        // 这条交互面的差异（C1）：同一个页面标题栏、这道题的题面卡、以及"打开错题本"出口。
        // 会话内容本身与智能体栏、拍照会话共用同一条交互面。
        surface = TutorSurfaceConfig(
            area = TutorConversationAreas.AGENT,
            header = {
                TutorPageHeader(
                    onOpenCapabilitySettings = onOpenModelSettings,
                    onOpenHistory = onOpenHistory,
                    onBack = onBack,
                )
            },
            composerPlaceholder = "问这道题，或说出你卡住的步骤",
            // 这一条**不自动开首轮**（与拍照入口相反）：错题本这条路的合同是"入库后不自动讲题、
            // 由学生显式发起"（`docs/product-information-architecture.md` §错题本、
            // `docs/m1-exhaustive-product-contract.md` 的"自动开始讲题"一栏）。学生进来看到的是
            // 这道题的题面与记忆卡，第一轮由他说出问题才开始——页面不替他发问。
            autoStartFirstTurn = false,
            onOpenAttachedQuestionDetail = { onOpenMistakeNotebook(null) },
            // 这一页锚着的那道**已在错题本里**的题（A4 执行路径 ②）：模型申请"存/打开这一轮
            // 这道题"时，本地可执行的目标就是它——没有它，这类申请连卡都挂不出来（本地没有
            // 目标就不该出现一张点了无处落地的卡，见 `tutorLocalActionAdmission`），
            // 于是只能在界面流里消失。用错题本条目 id（与错题详情同一条入口的键）。
            libraryProblemId = identity.errorBookEntryId,
            leadingContent = {
                // A1：进入即新会话，不再接管上一次。文案跟着改——旧文案承诺"再次打开会接着
                // 上次讲题"，而那正是被删掉的行为。
                LocalModeLine("已存入错题本 · 这一轮从这道题开始")
                SectionHeader(question.title, modifier = Modifier.padding(top = 10.dp))
                Text(
                    text = question.subject.studentSubjectLabel(),
                    modifier = Modifier.padding(top = 4.dp),
                    color = InkSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(14.dp))
                StructuredContentRenderer(
                    document = question.questionDocument.document,
                    choicesEnabled = false,
                )
                learningMemory?.let { memory ->
                    TutorQuestionMemoryCard(
                        memory = memory,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            },
            trailingContent = { Spacer(Modifier.height(12.dp)) },
        ),
        modifier = modifier,
    )
}

internal fun savedMistakeTutorAnchor(
    sessionId: String,
    problemRevisionId: String,
    practiceUnitId: String,
    anchoredAtEpochMillis: Long,
) = TutorSessionProblemAnchor(
    sessionId = sessionId,
    problemRevisionId = problemRevisionId,
    practiceUnitId = practiceUnitId,
    anchoredAtEpochMillis = anchoredAtEpochMillis,
)

/**
 * 这道错题这一轮的讲题上下文。
 *
 * [sessionId] 是**显式**的（A1）：由本页这次进入的人给（路由每进入一次新开一个，
 * `rememberSaveable` 保证旋转/进程死亡后仍是同一次进入的同一个会话）。**没有例外**——
 * 这道题当初是不是从拍照会话确认存下来的、详情里有没有一条会话联结（
 * `MistakeDetailState.Ready.tutorConversation`），都不改变"进入即新开"：那是**上一次**
 * 讲题，而 D-Q6-4 定的是"进任何入口都是新对话，不续旧会话"，页面上那句「这一轮从这道题
 * 开始」说的也是同一件事。
 *
 * 这里此前自己从"题面 id + 修订号"派生出 `mistake-tutor-<hash>`：同一个题面进来的人因此
 * 都会拿到同一个会话 id，而会话行是按 id 建的——于是"进入即新开"变成了"进入即静默接管
 * 上一次"，上一次的上下文、上一次的助手行全都接着用。派生式已删；随后那条"来自拍照会话就
 * 用旧会话 id"的例外也删了——它把同一个失败换了个触发条件，还顺带把本轮修订号换成旧会话
 * 里的那个数字（页面上摆着的是 `identity.revisionNumber` 那一版），并丢掉本轮的
 * `priorTeachingAdvisories`。
 */
internal fun savedMistakeTutorQuestion(
    sessionId: String,
    state: MistakeDetailState.Ready,
    learningMemory: StudyQuestionMemory? = null,
    relatedKnowledgeNodeIds: Set<String> = emptySet(),
    reviewedTeachingReferences: List<TutorTeachingReference> = emptyList(),
    priorTeachingAdvisories: List<String> = emptyList(),
    knowledgeCodes: List<TutorKnowledgeCode> = emptyList(),
): TutorQuestionContext {
    val identity = state.detail.identity
    return TutorQuestionContext(
        sessionId = sessionId,
        revisionNumber = identity.revisionNumber,
        subject = identity.subject,
        title = identity.title,
        questionDocument = state.questionDocument,
        learningMemory = learningMemory,
        relatedKnowledgeNodeIds = relatedKnowledgeNodeIds,
        reviewedTeachingReferences = reviewedTeachingReferences,
        priorTeachingAdvisories = priorTeachingAdvisories,
        knowledgeCodes = knowledgeCodes,
    )
}

@Composable
internal fun TutorQuestionMemoryCard(
    memory: StudyQuestionMemory,
    modifier: Modifier = Modifier,
) {
    val now = remember(memory) { System.currentTimeMillis() }
    // Qualitative retention bands only: precise probabilities require a
    // calibrated model (audit section 6.4).
    val retentionLabel =
        com.tingyun.smartmistakebook.core.ui.retentionBandLabel(
            memory.retrievabilityAtSnapshot,
        )
    val status = when {
        !memory.projectionIsCurrent -> "学习记录正在重新计算，暂不判断当前掌握度"
        memory.nextReviewAtEpochMillis <= now -> "$retentionLabel · 已到复习时间"
        else -> "$retentionLabel · 下次复习已安排"
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("saved_mistake_learning_memory"),
        color = JadeSoft.copy(alpha = 0.34f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Outline),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = "这道题的学习记忆",
                color = Ink,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = buildString {
                    append("独立答对 ${memory.independentRecallCount} 次")
                    append(" · 提示后答对 ${memory.assistedRecallCount} 次")
                    append(" · 遗忘 ${memory.retrievalFailureCount} 次")
                    if (memory.answerRevealCount > 0) {
                        append(" · 看过答案 ${memory.answerRevealCount} 次")
                    }
                },
                color = InkSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(status, color = InkSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Accumulated silent-debrief inputs for one tutoring visit. */
internal data class DebriefDraft(
    val labels: List<String> = emptyList(),
    val transcript: String = "",
) {
    companion object {
        val EMPTY = DebriefDraft()
    }
}
