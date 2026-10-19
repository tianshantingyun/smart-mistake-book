package com.tingyun.smartmistakebook

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tingyun.smartmistakebook.core.data.study.AnswerKeyCorrectionCommand
import com.tingyun.smartmistakebook.core.data.study.AnswerKeyCorrectionStatus
import com.tingyun.smartmistakebook.core.data.study.AnswerKeyCorrectionWriter
import com.tingyun.smartmistakebook.core.data.study.RoomBackedStudyExperienceRepository
import com.tingyun.smartmistakebook.core.domain.CaptureEntryOrigin
import com.tingyun.smartmistakebook.core.domain.ModelConfigurationSnapshot
import com.tingyun.smartmistakebook.core.domain.MistakeRevisionKey
import com.tingyun.smartmistakebook.core.domain.PendingCaptureStage
import com.tingyun.smartmistakebook.core.domain.currentCapabilityVerification
import com.tingyun.smartmistakebook.core.domain.StudyDataStatus
import com.tingyun.smartmistakebook.core.domain.isReady
import com.tingyun.smartmistakebook.core.domain.StudyReviewAdvanceResult
import com.tingyun.smartmistakebook.core.domain.StudyReviewSessionStatus
import com.tingyun.smartmistakebook.core.domain.PrerequisiteRemediation
import com.tingyun.smartmistakebook.core.domain.ReTeachOpening
import com.tingyun.smartmistakebook.core.model.VerifiedTeachingArtifact
import com.tingyun.smartmistakebook.core.model.TeachingAdvisoryRecord
import com.tingyun.smartmistakebook.core.ui.InkSecondary
import com.tingyun.smartmistakebook.core.ui.OutlineActionChip
import com.tingyun.smartmistakebook.core.ui.JadeActive
import com.tingyun.smartmistakebook.core.ui.JadeSoft
import com.tingyun.smartmistakebook.core.ui.Paper
import com.tingyun.smartmistakebook.core.ui.PaperDivider
import com.tingyun.smartmistakebook.core.ui.SmartDimens
import com.tingyun.smartmistakebook.feature.capture.CaptureScreen
import com.tingyun.smartmistakebook.feature.capture.CaptureShareIntakeRoute
import com.tingyun.smartmistakebook.feature.capture.CaptureShareRequest
import com.tingyun.smartmistakebook.feature.capture.CaptureShareRequestSaver
import com.tingyun.smartmistakebook.feature.library.BatchImportRoute
import com.tingyun.smartmistakebook.feature.library.LibraryExportCandidates
import com.tingyun.smartmistakebook.feature.library.LibraryRoute
import com.tingyun.smartmistakebook.feature.library.MAX_LIBRARY_BATCH_EXPORT_QUESTIONS
import com.tingyun.smartmistakebook.feature.library.MistakeExportFormatSheet
import com.tingyun.smartmistakebook.feature.library.MistakeExportHubRoute
import com.tingyun.smartmistakebook.feature.library.MistakeDetailRoute
import com.tingyun.smartmistakebook.core.domain.MistakeExportRecord
import com.tingyun.smartmistakebook.core.domain.SchedulingOptions
import com.tingyun.smartmistakebook.core.domain.SplitImportRepository
import com.tingyun.smartmistakebook.core.domain.KnowledgeReviewSessionPlan
import com.tingyun.smartmistakebook.feature.library.SplitImportReviewRoute
import com.tingyun.smartmistakebook.feature.profile.ProfileRoute
import com.tingyun.smartmistakebook.feature.review.CapturedReviewSessionScreen
import com.tingyun.smartmistakebook.feature.review.KnowledgeQuizAnswerIssue
import com.tingyun.smartmistakebook.feature.review.KnowledgeQuizLoadResult
import com.tingyun.smartmistakebook.feature.review.KnowledgeReviewQuizLoader
import com.tingyun.smartmistakebook.feature.review.KnowledgeReviewSessionScreen
import com.tingyun.smartmistakebook.feature.review.ReviewRoute
import com.tingyun.smartmistakebook.feature.review.ReviewSessionScreen
import com.tingyun.smartmistakebook.feature.tutor.CapturedTutorSessionRoute
import com.tingyun.smartmistakebook.feature.tutor.SavedMistakeTutorRoute
import com.tingyun.smartmistakebook.feature.tutor.TutorHistoryRoute
import com.tingyun.smartmistakebook.feature.tutor.buildTutorDebriefRequestForApp
import com.tingyun.smartmistakebook.feature.tutor.TutorRoute
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class TeachingArtifactLoad(
    val practiceUnitId: String? = null,
    val artifact: VerifiedTeachingArtifact? = null,
    /** Spec §2.16: non-null only when this card is a leech with reviewed material. */
    val reTeachOpening: ReTeachOpening? = null,
    /** Spec §2.9: non-null when a prerequisite of this card is below ready. */
    val prerequisiteRemediation: PrerequisiteRemediation? = null,
    val isLoaded: Boolean = false,
)

private data class RootDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val testTag: String,
)

private val rootDestinations = listOf(
    RootDestination(Routes.Review, "复习", Icons.Outlined.EventAvailable, "nav_review"),
    RootDestination(Routes.Tutor, "智能体", Icons.AutoMirrored.Outlined.Chat, "nav_tutor"),
    RootDestination(Routes.Library, "错题本", Icons.AutoMirrored.Outlined.MenuBook, "nav_library"),
    RootDestination(Routes.Profile, "我的", Icons.Outlined.ManageAccounts, "nav_profile"),
)

internal fun bottomBarRouteFor(route: String?): String? = when (route) {
    Routes.Review,
    Routes.Tutor,
    Routes.Library,
    Routes.Profile,
    -> route
    Routes.CapturedTutorSession,
    Routes.MistakeTutor,
    Routes.TutorHistory,
    Routes.TutorTextConversation,
    -> Routes.Tutor
    else -> null
}

/**
 * 库页导出动作的三态分流（L7）：只有 [LibraryExportCandidates.Candidates] 会入队，返回 null；
 * 超限/没有可导出的题返回要在「导出成果」里如实说明的原因（改前两者都显示"当前没有可导出的
 * 错题"，超限被说成了空）。
 */
internal fun libraryExportRejectionNotice(candidates: LibraryExportCandidates): String? =
    when (candidates) {
        is LibraryExportCandidates.Candidates -> null
        LibraryExportCandidates.TooManyVisible ->
            "当前结果超过 $MAX_LIBRARY_BATCH_EXPORT_QUESTIONS 道。按科目、板块、掌握程度或录入时间段筛选后，就能直接导出。"
        LibraryExportCandidates.NothingVisible -> "当前没有可导出的错题。"
    }

/**
 * PRD-04 / T4：原生导出入口（错题详情单题、错题本「导出当前筛选」）在**入队前**先选格式。
 *
 * 只在内存里存在：sheet 关闭（确认/取消）即清空。取消不导航、不入队；确认才把选定的
 * [com.tingyun.smartmistakebook.core.model.MistakeExportFormat] 原样交给
 * `startMistakeExportSingle/Batch`，与既有讲题页导出 sheet 走同一条格式身份链。
 */
private sealed interface PendingExportTarget {
    data class Single(val key: MistakeRevisionKey) : PendingExportTarget
    data class Batch(val entryIds: List<String>) : PendingExportTarget
}

/**
 * PRD-02/T2：一次"答案有问题"报告的稳定身份。同一 (知识点, 题面, 已选选项, 理由) 重复提交
 * 是同一笔（writer 回 REPLAYED，不堆重复观察行）；换目标或换理由才是另一笔。
 */
private fun knowledgeQuizAnswerIssueRequestId(issue: KnowledgeQuizAnswerIssue): String =
    "answer-issue:${issue.knowledgeNodeId}:${issue.itemId}:" +
        "${issue.selectedChoiceId ?: "neutral"}:${issue.reason.hashCode().toUInt()}"

@Composable
internal fun SmartMistakeBookRoot(
    reviewOpenRequests: StateFlow<Long>,
    exportOpenRequests: StateFlow<Long>,
    sharedCaptureRequests: StateFlow<CaptureShareRequest?>,
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val application = context.applicationContext as SmartMistakeBookApplication
    val databaseRestartReason by application.databaseRestartReason.collectAsStateWithLifecycle()
    databaseRestartReason?.let { message ->
        DatabaseRestartRequiredScreen(message = message, onClose = { activity?.closeForDatabaseRestart() })
        return
    }
    val startupState by application.startupState.collectAsStateWithLifecycle()
    // 知识能力就绪位（D-Q3）：与 startupState 分开——秒开语义由 startupState 保证，
    // 依赖知识库的入口读这一位，未就绪时如实说"准备中"，就绪后组合自动放行。
    val knowledgeBaseAvailability by application.knowledgeBaseAvailability
        .collectAsStateWithLifecycle()
    if (startupState is StartupState.FatalFailure) {
        // 数据库初始化失败时仓库等 lateinit 尚未就绪，任何触碰都会在组合期崩溃；
        // 这里只渲染错误卡，让用户看到"应用数据无法打开"而不是闪退。
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Paper)
                .statusBarsPadding(),
        ) {
            StartupStateBanner(state = startupState, onRetry = null)
        }
        return
    }
    val repository = application.studyRepository
    val baseCapabilities = application.capabilities
    val configurationStore = application.modelConfigurationStore
    val modelConfiguration = if (configurationStore != null) {
        configurationStore.configuration
            .collectAsStateWithLifecycle(initialValue = ModelConfigurationSnapshot())
            .value
    } else {
        ModelConfigurationSnapshot()
    }
    val verifiedModelCapabilities = modelConfiguration.currentCapabilityVerification()
    val capabilities = baseCapabilities.copy(
        remoteModelConfigured = modelConfiguration.isConfigured,
        remoteModelCapabilitiesTested = verifiedModelCapabilities != null,
        remoteModelImageInputVerified =
            verifiedModelCapabilities?.supportsImageInput == true,
        remoteModelStructuredOutputVerified =
            verifiedModelCapabilities?.supportsStructuredOutput == true,
        remoteModelAuthenticationFailed =
            verifiedModelCapabilities?.authenticationFailed == true,
    )
    val applicationUiScope = rememberCoroutineScope()
    // PRD-02 / T2：知识点复习"答案有问题"的唯一写入口。与判分反馈共用同一 learner 身份
    // （仓库默认 learner），只追加纠正/争议观察行，不改写原作答。
    val answerKeyCorrectionWriter = remember(application) {
        AnswerKeyCorrectionWriter(
            database = application.studyDatabase,
            learnerId = RoomBackedStudyExperienceRepository.DEFAULT_LEARNER_ID,
        )
    }
    val experience by repository.snapshot.collectAsStateWithLifecycle()
    // 讲题页的 artifact 装载器随"第三路径"一起删除（阶段 2c）：`experience.tutorPracticeUnitId`
    // 在生产里恒为 null，装载器每次都只是空转；错题复习那一侧有它自己的装载器
    // （SmartMistakeBookDestinations 里的同一个 `TeachingArtifactLoad`）。
    val navController = rememberNavController()
    // L7 导出后台化：错题本不再有前台导出页；这里只留"为什么这次没入队"的一次性说明，
    // 在「导出成果」入口里如实说（超限/没有可导出的题），入队成功的记录走数据库流。
    var pendingExportNotice by rememberSaveable { mutableStateOf<String?>(null) }
    // PRD-04/T4：原生导出先选格式；null = 没有待确认的导出（不渲染 sheet）。
    var pendingExportTarget by remember { mutableStateOf<PendingExportTarget?>(null) }
    // PRD-06/T8：待处理录入里需要学生继续的数量（只读现有状态，不新增模型调用）。
    // 只计入"学生还能继续"的失败态；TUTOR_SESSION_READY 等正常可续做的不算失败。
    val pendingFailedTaskCount by remember(application.captureRepository) {
        application.captureRepository.observePendingCaptures().map { captures ->
            captures.count { capture ->
                capture.stage == PendingCaptureStage.SOURCE_UNAVAILABLE ||
                    capture.stage == PendingCaptureStage.RECAPTURE_REQUIRED ||
                    capture.stage == PendingCaptureStage.RETRY_OR_MANUAL
            }
        }
    }.collectAsStateWithLifecycle(initialValue = 0)
    val exportRecords by application.mistakeExportRepository.observeRecords()
        .collectAsStateWithLifecycle(initialValue = emptyList<MistakeExportRecord>())
    val reviewOpenRequest by reviewOpenRequests.collectAsStateWithLifecycle()
    val exportOpenRequest by exportOpenRequests.collectAsStateWithLifecycle()
    // A7（4C）：系统分享的待接收请求。MainActivity 投递一次，这里取走并清空（消费语义），
    // 之后由接收页持有；旋转/进程重建时请求本身还能通过 Saver 恢复。
    val sharedCaptureRequest by sharedCaptureRequests.collectAsStateWithLifecycle()
    var pendingSharedCapture by rememberSaveable(stateSaver = CaptureShareRequestSaver) {
        mutableStateOf<CaptureShareRequest?>(null)
    }
    // 单图分享：分享层落成自有的 question_*.img，交给录入页一次；消费后清空。
    var pendingSharedImageUri by rememberSaveable { mutableStateOf<String?>(null) }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: Routes.Review
    val isRootDestination = rootDestinations.any { it.route == currentRoute }
    val selectedBottomRoute = bottomBarRouteFor(currentRoute)

    LaunchedEffect(reviewOpenRequest) {
        if (reviewOpenRequest > 0L) {
            navController.navigate(Routes.Review) {
                popUpTo(navController.graph.findStartDestination().id)
                launchSingleTop = true
            }
        }
    }

    // L7：导出完成通知点进来 → 「导出成果」（Android 13+ 未授权时没有通知，这一屏从
    // 错题本栏的常驻入口进）。
    LaunchedEffect(exportOpenRequest) {
        if (exportOpenRequest > 0L) {
            navController.navigate(Routes.ExportResults) {
                launchSingleTop = true
            }
        }
    }

    // A7（4C）：系统分享 → 接收页。先从这里取走 MainActivity 的投递（消费一次），接收页
    // 自己校验/落盘/分流；失败原因由接收页显示，不在这里吞掉。
    LaunchedEffect(sharedCaptureRequest?.requestId) {
        val request = sharedCaptureRequest ?: return@LaunchedEffect
        pendingSharedCapture = request
        (activity as? MainActivity)?.consumeSharedCapture(request.requestId)
        navController.navigate(Routes.SharedCapture) {
            launchSingleTop = true
        }
    }

    BackHandler(enabled = isRootDestination && activity != null) {
        activity?.moveTaskToBack(true)
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(Paper)
            .semantics { testTagsAsResourceId = true },
        containerColor = Paper,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (selectedBottomRoute != null) {
                SmartBottomBar(
                    selectedRoute = selectedBottomRoute,
                    onSelect = { destination ->
                        if (destination.route != bottomBarRouteFor(currentRoute)) {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            StartupStateBanner(
                state = startupState,
                onRetry = if (startupState.isRetryable) {
                    { application.refreshStudyExperience() }
                } else {
                    null
                },
            )
            StudyDataStatusLine(experience.status)
            NavHost(
                navController = navController,
                startDestination = Routes.Review,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(bottom = innerPadding.calculateBottomPadding()),
            ) {
            composable(Routes.Review) { entry ->
                val destinationLifecycle by entry.lifecycle.currentStateFlow
                    .collectAsStateWithLifecycle()
                if (destinationLifecycle == Lifecycle.State.RESUMED) {
                    // 今日可复习知识点数（spec dual-review-entry §3.1）：仅在复习首页可见且模型
                    // 可用时实算一次（含材料可出题过滤），避免把材料读取放进 snapshot 发布热路径。
                    // 键含 planId/profile：计划或掌握态变化时重算；失败/无计划 → null（不显示入口）。
                    val knowledgeReviewCount by produceState<Int?>(
                        initialValue = null,
                        key1 = experience.status,
                        key2 = Triple(
                            experience.review.planId,
                            experience.profile,
                            capabilities.remoteModelAvailable,
                        ),
                    ) {
                        value = if (
                            experience.status == StudyDataStatus.READY &&
                            capabilities.remoteModelAvailable
                        ) {
                            try {
                                repository.currentKnowledgeReviewPlan(
                                    requestId = "knowledge-review-count:${UUID.randomUUID()}",
                                    occurredAtEpochMillis = System.currentTimeMillis(),
                                )?.queue?.size
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                null
                            }
                        } else {
                            null
                        }
                    }
                    ReviewRoute(
                        overview = experience.review,
                        profile = experience.profile,
                        knowledgeReviewCount = knowledgeReviewCount,
                        // PRD-06/T8：只有数据确实 READY 才把题数交给空态判定；加载中/出错
                        // 时传 null，界面不会把"还不知道"说成"还没有题"。
                        savedQuestionCount = experience.catalog.size
                            .takeIf { experience.status == StudyDataStatus.READY },
                        pendingFailedTaskCount = pendingFailedTaskCount,
                        // 空库录入与"继续处理待处理"都指到含「手动建题」的同一录入入口
                        // （与错题本栏一致），不误入要求照片的讲题入口。
                        onCapture = {
                            navController.navigate(Routes.capture(CaptureEntryOrigin.LIBRARY))
                        },
                        onContinuePendingTasks = {
                            navController.navigate(Routes.capture(CaptureEntryOrigin.LIBRARY))
                        },
                        onStartReview = {
                            applicationUiScope.launch {
                                try {
                                    val progress = repository.startOrResumeReviewSession(
                                        requestId = "review-start:${UUID.randomUUID()}",
                                        occurredAtEpochMillis = System.currentTimeMillis(),
                                    )
                                    if (
                                        progress?.status == StudyReviewSessionStatus.ACTIVE &&
                                        entry.lifecycle.currentState == Lifecycle.State.RESUMED
                                    ) {
                                        navController.navigate(Routes.ReviewSession) {
                                            launchSingleTop = true
                                        }
                                    }
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) {
                                    // The shared repository snapshot exposes the fail-closed error state.
                                    // 顺手刷一次数据：瞬时错误恢复后 ERROR 清除、概览回到真实状态。
                                    runCatching { repository.refresh() }
                                }
                            }
                        },
                        // 知识点复习的取题依赖模型现场生成（KNOWLEDGE_QUIZ），错题复习用本地
                        // verified artifact——只有模型可用时才给出知识点入口，避免进会话后取题
                        // 必然失败、重试无用的死路（strictOffline / 未配置模型时隐藏该入口）。
                        onStartKnowledgeReview = if (capabilities.remoteModelAvailable) {
                            {
                                // 知识点复习不要求错题会话已启动：目标路由进入时自行经
                                // currentKnowledgeReviewPlan 组装今日知识点计划。
                                navController.navigate(Routes.KnowledgeReviewSession) {
                                    launchSingleTop = true
                                }
                            }
                        } else {
                            null
                        },
                        modifier = Modifier.testTag("root_review"),
                    )
                } else {
                    ReviewSessionGateMessage("正在打开今日复习…")
                }
            }
            composable(Routes.Tutor) {
                // 讲题页只有一条交互面（阶段 2c 删掉了 artifact 分支与 TutorViewModel）：
                // 会话内容由 TutorConversationViewModel 持有，这里只给入口自己的接线。
                TutorRoute(
                    onCapture = { navController.navigate(Routes.capture(CaptureEntryOrigin.TUTOR)) },
                    onOpenCapabilitySettings = { navController.navigate(Routes.Capability) },
                    onOpenMistakeNotebook = { navController.navigate(Routes.Library) },
                    onOpenProfile = { navController.navigate(Routes.Profile) },
                    onOpenHistory = { navController.navigate(Routes.TutorHistory) },
                    // 在错题库里挑中的题作为本轮附件进同一个讲题页面（Routes.MistakeTutor
                    // 渲染的也是这个页面），不离开底部「智能体」标签、不换页面。
                    onOpenMistakeTutor = { key -> navController.navigate(Routes.mistakeTutor(key)) },
                    conversations = application.tutorConversationRepository,
                    modelTasks = application.modelTaskRepository,
                    catalogEntries = experience.catalog,
                    imageIntake = application.lobbyMessageImageIntake,
                    // 确认卡（A4）：模型申请的本地动作挂成库里的行；学生点了才执行，三条
                    // 落点（拍照草稿 / 错题本 / 聊天附图）都是既有的真实路径。
                    pendingRequests = application.agentPendingRequestRepository,
                    captureRepository = application.captureRepository,
                    attachedImageIntake = application.tutorAttachedImageIntake,
                    onOpenLibraryProblem = { navController.navigate(Routes.Library) },
                    // 导出 sheet（B3-3）：学生定稿的版式随任务入队，然后直接去「导出成果」
                    // 看后台进度——与错题本批量导出走同一条 4A 管线。
                    onStartExport = { format, layout, entryIds ->
                        application.startMistakeExportBatch(entryIds, layout, format)
                        navController.navigate(Routes.ExportResults) {
                            launchSingleTop = true
                        }
                    },
                    // 6B-2：选题后读题面建可编辑卷面（纯本地读取）；编辑确认后草稿落盘、
                    // 后台重渲染，同样回到「导出成果」看结果。
                    readExportPaperEdit = { entryIds, layout ->
                        application.buildMistakePaperEdit(entryIds, layout)
                    },
                    onStartEditedExport = { draft, format ->
                        application.startMistakeExportEdited(draft, format)
                        navController.navigate(Routes.ExportResults) {
                            launchSingleTop = true
                        }
                    },
                    modifier = Modifier.testTag("root_tutor"),
                )
            }
            composable(Routes.TutorHistory) {
                TutorHistoryRoute(
                    conversations = application.tutorConversationRepository,
                    onArchive = { conversationId ->
                        applicationUiScope.launch {
                            application.tutorConversationRepository.archiveConversation(
                                com.tingyun.smartmistakebook.core.domain.ArchiveTutorConversationCommand(
                                    conversationId = conversationId,
                                    occurredAtEpochMillis = System.currentTimeMillis(),
                                ),
                            )
                        }
                    },
                    onDelete = { conversationId ->
                        applicationUiScope.launch {
                            application.tutorConversationRepository.deleteConversation(
                                com.tingyun.smartmistakebook.core.domain.DeleteTutorConversationCommand(
                                    conversationId = conversationId,
                                    occurredAtEpochMillis = System.currentTimeMillis(),
                                ),
                            )
                        }
                    },
                    onOpenTextConversation = { conversationId ->
                        navController.navigate(Routes.tutorTextConversation(conversationId)) {
                            launchSingleTop = true
                        }
                    },
                    onOpenCapturedSession = { sessionId ->
                        navController.navigate(Routes.capturedTutorSession(sessionId)) {
                            launchSingleTop = true
                        }
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            // 历史重开：纯文字会话落回同一个讲题页面（没有附件题），不另开页面。
            composable(Routes.TutorTextConversation) { entry ->
                val conversationId = entry.arguments?.getString("conversationId")
                    .orEmpty()
                if (conversationId.isBlank()) {
                    return@composable
                }
                TutorRoute(
                    onCapture = { navController.navigate(Routes.capture(CaptureEntryOrigin.TUTOR)) },
                    onOpenCapabilitySettings = { navController.navigate(Routes.Capability) },
                    onOpenMistakeNotebook = { navController.navigate(Routes.Library) },
                    onOpenProfile = { navController.navigate(Routes.Profile) },
                    onOpenHistory = { navController.navigate(Routes.TutorHistory) },
                    // 在这个页面上挑中的题作为本轮附件进同一个页面：不离开底部「智能体」标签。
                    onOpenMistakeTutor = { key -> navController.navigate(Routes.mistakeTutor(key)) },
                    conversations = application.tutorConversationRepository,
                    modelTasks = application.modelTaskRepository,
                    catalogEntries = experience.catalog,
                    imageIntake = application.lobbyMessageImageIntake,
                    initialConversationId = conversationId,
                    // 确认卡（A4）：模型申请的本地动作挂成库里的行；学生点了才执行，三条
                    // 落点（拍照草稿 / 错题本 / 聊天附图）都是既有的真实路径。
                    pendingRequests = application.agentPendingRequestRepository,
                    captureRepository = application.captureRepository,
                    attachedImageIntake = application.tutorAttachedImageIntake,
                    onOpenLibraryProblem = { navController.navigate(Routes.Library) },
                    // 历史重开也接同一条导出落点（本地动作只在大厅广告，行为一致）。
                    onStartExport = { format, layout, entryIds ->
                        application.startMistakeExportBatch(entryIds, layout, format)
                        navController.navigate(Routes.ExportResults) {
                            launchSingleTop = true
                        }
                    },
                    // 历史重开也接同一条编辑落点（行为一致）。
                    readExportPaperEdit = { entryIds, layout ->
                        application.buildMistakePaperEdit(entryIds, layout)
                    },
                    onStartEditedExport = { draft, format ->
                        application.startMistakeExportEdited(draft, format)
                        navController.navigate(Routes.ExportResults) {
                            launchSingleTop = true
                        }
                    },
                    modifier = Modifier.testTag("root_tutor"),
                )
            }
            composable(Routes.Library) {
                LibraryRoute(
                    entries = experience.catalog,
                    catalogRepository = application.libraryCatalogRepository,
                    mistakeDetailRepository = application.mistakeDetailRepository,
                    pendingFailedTaskCount = pendingFailedTaskCount,
                    onContinuePendingTasks = {
                        navController.navigate(Routes.capture(CaptureEntryOrigin.LIBRARY))
                    },
                    // 错题本栏只有一个「录入」动作（L6）：整卷/PDF 不再是这里的并列入口，
                    // 它是录入流内部的一步（见 Routes.Capture 的方式选择）。
                    onCapture = { navController.navigate(Routes.capture(CaptureEntryOrigin.LIBRARY)) },
                    // L7：批量导出改为"入队后即可离开"。PRD-04/T4：上限内先弹格式 sheet，
                    // 确认后才入队并跳「导出成果」；超限/没有题仍然不下单，把原因带到
                    // 「导出成果」里如实说清楚。
                    onExportVisible = { candidates ->
                        if (candidates is LibraryExportCandidates.Candidates) {
                            pendingExportTarget = PendingExportTarget.Batch(candidates.entryIds)
                        } else {
                            pendingExportNotice = libraryExportRejectionNotice(candidates)
                            navController.navigate(Routes.ExportResults) {
                                launchSingleTop = true
                            }
                        }
                    },
                    onOpenExportResults = {
                        pendingExportNotice = null
                        navController.navigate(Routes.ExportResults) {
                            launchSingleTop = true
                        }
                    },
                    onOpenItem = { itemId -> navController.navigate(Routes.mistakeDetail(itemId)) },
                    modifier = Modifier.testTag("root_library"),
                )
            }
            composable(Routes.Profile) {
                ProfileRoute(
                    overview = experience.profile,
                    review = experience.review,
                    capabilities = capabilities,
                    onOpenCapability = { navController.navigate(Routes.Capability) },
                    onOpenLearningMastery = {
                        navController.navigate(Routes.LearningMastery)
                    },
                    onOpenDataPrivacy = { navController.navigate(Routes.Privacy) },
                    onOpenReminder = { navController.navigate(Routes.Reminder) },
                    onOpenScheduling = { navController.navigate(Routes.Scheduling) },
                    onOpenStorage = { navController.navigate(Routes.Storage) },
                    modifier = Modifier.testTag("root_profile"),
                )
            }
            composable(Routes.ReviewSession) { entry ->
                ReviewSessionDestination(
                    details = application.mistakeDetailRepository,
                    entry = entry,
                    experience = experience,
                    capabilities = capabilities,
                    repository = repository,
                    navController = navController,
                )
            }
            composable(Routes.KnowledgeReviewSession) { entry ->
                val destinationLifecycle by entry.lifecycle.currentStateFlow
                    .collectAsStateWithLifecycle()
                val onKnowledgeBack = {
                    if (entry.lifecycle.currentState == Lifecycle.State.RESUMED) {
                        navController.popBackStack()
                    }
                    Unit
                }
                BackHandler(onBack = onKnowledgeBack)
                var planRetryNonce by remember { mutableIntStateOf(0) }
                val planState by produceState<KnowledgeReviewPlanState>(
                    initialValue = KnowledgeReviewPlanState.Loading,
                    key1 = experience.status,
                    key2 = planRetryNonce,
                ) {
                    value = if (experience.status == StudyDataStatus.READY) {
                        try {
                            KnowledgeReviewPlanState.Ready(
                                repository.currentKnowledgeReviewPlan(
                                    requestId = "knowledge-review-plan:${UUID.randomUUID()}",
                                    occurredAtEpochMillis = System.currentTimeMillis(),
                                ) ?: KnowledgeReviewSessionPlan(),
                            )
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            // 加载失败与"真的没有"是两种状态：失败给重试，不许误报"都已掌握"。
                            KnowledgeReviewPlanState.Failed
                        }
                    } else {
                        KnowledgeReviewPlanState.Loading
                    }
                }
                val quizLoader = remember {
                    KnowledgeReviewQuizLoader(
                        modelTasks = application.modelTaskRepository,
                        references = application.tutorTeachingReferenceRepository,
                        // 传 flow 而不是当前值：loader 在每次取题时读当时的值，
                        // 学生的"重试"因此不必等这里重组。
                        knowledgeBaseAvailability = application.knowledgeBaseAvailability,
                    )
                }
                when {
                    destinationLifecycle != Lifecycle.State.RESUMED ->
                        ReviewSessionGateMessage("正在打开知识点复习…")
                    experience.status != StudyDataStatus.READY ->
                        ReviewSessionGateMessage("学习记录暂时不可用，知识点复习已暂停。")
                    // D-Q3：知识内容还在后台就位时如实说"准备中"，不放进会话去撞一鼻子
                    // "这道题没有材料"。就绪后本组合自动重来一遍——这就是"就绪后自动放行"。
                    !knowledgeBaseAvailability.isReady ->
                        ReviewSessionGateMessage("知识点资料还在准备中，稍后就能开始。")
                    else -> when (val state = planState) {
                        KnowledgeReviewPlanState.Loading ->
                            ReviewSessionGateMessage("正在准备今天的知识点复习…")
                        KnowledgeReviewPlanState.Failed -> ReviewSessionGateMessage(
                            message = "今天的知识点复习暂时没有准备好。",
                            onRetry = { planRetryNonce += 1 },
                        )
                        is KnowledgeReviewPlanState.Ready ->
                                KnowledgeReviewSessionScreen(
                                    plan = state.plan,
                                    onBack = onKnowledgeBack,
                                    // 恢复：读回本会话已落库的题面（只读，不出网，AUD-02）。
                                    restoreQuiz = { entry, sessionId ->
                                        repository.readPersistedKnowledgeQuizItem(
                                            sessionId = sessionId,
                                            knowledgeNodeId = entry.knowledgeNodeId,
                                        )
                                            ?.let(KnowledgeQuizLoadResult::Ready)
                                            ?: KnowledgeQuizLoadResult.Unavailable
                                    },
                                    // 现场出题：生成成功后把题面落库，再把**库内那一行**交给
                                    // 会话——屏幕上的题面与之后恢复读回的题面因此是同一道题。
                                    loadQuiz = { entry, sessionId ->
                                        when (val loaded = quizLoader.loadQuiz(entry)) {
                                            is KnowledgeQuizLoadResult.Ready -> repository
                                                .persistKnowledgeQuizItem(
                                                    sessionId = sessionId,
                                                    knowledgeNodeId = entry.knowledgeNodeId,
                                                    item = loaded.item,
                                                )
                                                ?.let(KnowledgeQuizLoadResult::Ready)
                                                ?: KnowledgeQuizLoadResult.Unavailable
                                            else -> loaded
                                        }
                                    },
                                    submitAnswer = repository::submitKnowledgeQuizFeedback,
                                    onFinished = onKnowledgeBack,
                                    // PRD-02/T2：答案有问题 → 定向撤销/争议观察。写入口用
                                    // applicationScope（离开本页也照样落库）：未看答案时是
                                    // 中性预报告（暂停该题面计分），已作答时撤销该条证据并
                                    // 同时落争议行；若该作答只有 rejected 观察行（未核验键），
                                    // writer 回落为争议观察，不再把反馈丢成“没有记下来”。
                                    // 成功不额外弹提示（下次作答按暂停计分）。
                                    onReportAnswerIssue = { issue ->
                                        application.applicationScope.launch {
                                            // 失败必须让学生看见：对话框的确认语已经说
                                            // "记下"，把拒绝/异常吞掉就是假成功。
                                            val failure = try {
                                                val receipt = answerKeyCorrectionWriter.submit(
                                                    AnswerKeyCorrectionCommand(
                                                        requestId =
                                                            knowledgeQuizAnswerIssueRequestId(issue),
                                                        knowledgeNodeId = issue.knowledgeNodeId,
                                                        // UI 回调不带复习会话号；中性观察行只把
                                                        // 它当行的外键文本，键信任判定不读这一列。
                                                        conversationId =
                                                            "knowledge-quiz-review:answer-issue:" +
                                                                issue.itemId,
                                                        itemId = issue.itemId,
                                                        selectedChoiceId = issue.selectedChoiceId,
                                                        // 撤销目标证据；目标不存在/非账本证据时
                                                        // writer 会回 REJECTED，不静默假成功。
                                                        voidTarget = true,
                                                        reasonMarkdown = issue.reason,
                                                        occurredAtEpochMillis =
                                                            System.currentTimeMillis(),
                                                    ),
                                                )
                                                if (
                                                    receipt.status ==
                                                    AnswerKeyCorrectionStatus.REJECTED ||
                                                    receipt.status ==
                                                    AnswerKeyCorrectionStatus.CONFLICT
                                                ) {
                                                    "${receipt.status} ${receipt.reason}"
                                                } else {
                                                    null
                                                }
                                            } catch (cancelled: CancellationException) {
                                                throw cancelled
                                            } catch (failure: Throwable) {
                                                failure.toString()
                                            }
                                            if (failure != null) {
                                                android.util.Log.w(
                                                    "SmartMistakeBook",
                                                    "Answer-key issue was not recorded: $failure",
                                                )
                                                withContext(Dispatchers.Main) {
                                                    Toast.makeText(
                                                        context,
                                                        "这次反馈没有记下来，请再试一次。",
                                                        Toast.LENGTH_LONG,
                                                    ).show()
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier.testTag("root_knowledge_review_session"),
                                )
                    }
                }
            }
            // 录入的唯一入口（L6）：错题本、智能体（大厅与历史文字会话）都指到这一条；
            // 来源（进入讲题 / 存入错题本）只作为参数，方式选择与整卷/PDF、拆分复核
            // 都在这个入口内部完成。
            composable(
                route = Routes.Capture,
                arguments = listOf(
                    navArgument(Routes.CaptureOriginArgument) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { entry ->
                val entryOrigin = Routes.captureEntryOrigin(
                    entry.arguments?.getString(Routes.CaptureOriginArgument),
                )
                CaptureScreen(
                    entryOrigin = entryOrigin,
                    repository = application.captureRepository,
                    modelTasks = application.modelTaskRepository,
                    modelEgressAllowed = baseCapabilities.networkRequestsAllowed,
                    modelConfigured = modelConfiguration.isConfigured,
                    onOpenModelSettings = { navController.navigate(Routes.Capability) },
                    onOpenPendingDraft = { draftId ->
                        navController.navigate(Routes.captureResume(draftId))
                    },
                    // 「整卷/PDF」（多张照片 / 一份 PDF）是录入流内部的一步：今天只有错题本
                    // 来源提供它——讲题来源的完成态是进入讲解，而整卷管线把草稿落进错题本
                    // 入库路径（captureResume 按持久化来源恢复），在讲题入口给它是终点
                    // 不一致的死路。
                    onOpenFileImport = if (entryOrigin == CaptureEntryOrigin.LIBRARY) {
                        {
                            navController.navigate(Routes.BatchImport) {
                                launchSingleTop = true
                            }
                        }
                    } else {
                        null
                    },
                    onTutorSessionReady = { sessionId ->
                        navController.navigate(Routes.capturedTutorSession(sessionId)) {
                            popUpTo(Routes.Capture) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onLibraryEntryReady = { entryId ->
                        navController.navigate(Routes.mistakeDetail(entryId)) {
                            popUpTo(Routes.Capture) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onSplitReady = { jobId ->
                        navController.navigate(Routes.splitReview(jobId)) {
                            popUpTo(Routes.Capture) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onBack = navController::popBackStack,
                    // A7（4C）：系统分享的单张图片（分享层已拷成自有文件）。应用一次后由
                    // 下面这个回调清掉，返回/旋转不会重复套用。
                    initialSharedSourceUri = pendingSharedImageUri,
                    onInitialSharedSourceConsumed = { pendingSharedImageUri = null },
                )
            }
            // A7（4C）：系统分享的接收页。单图 → 录入；多图/PDF → 整卷管线；失败留在原地
            // 如实说原因，并给出重试 / 手动录入 / 关闭三条出路。
            composable(Routes.SharedCapture) {
                CaptureShareIntakeRoute(
                    request = pendingSharedCapture,
                    batchImports = application.batchImportRepository,
                    onSingleImageReady = { ownedUri ->
                        pendingSharedImageUri = ownedUri
                        pendingSharedCapture = null
                        navController.navigate(Routes.capture(CaptureEntryOrigin.LIBRARY)) {
                            popUpTo(Routes.SharedCapture) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onImportStarted = {
                        pendingSharedCapture = null
                        navController.navigate(Routes.BatchImport) {
                            popUpTo(Routes.SharedCapture) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onManualEntry = {
                        pendingSharedCapture = null
                        navController.navigate(Routes.capture(CaptureEntryOrigin.LIBRARY)) {
                            popUpTo(Routes.SharedCapture) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onDismiss = {
                        pendingSharedCapture = null
                        navController.popBackStack()
                    },
                    modifier = Modifier.testTag("root_capture_share"),
                )
            }
            composable(Routes.BatchImport) {
                BatchImportRoute(
                    repository = application.batchImportRepository,
                    onOpenSavedEntry = { entryId ->
                        navController.navigate(Routes.mistakeDetail(entryId))
                    },
                    onOpenDraft = { draftId ->
                        navController.navigate(Routes.captureResume(draftId))
                    },
                    onSplitReady = { jobId ->
                        navController.navigate(Routes.splitReview(jobId)) {
                            launchSingleTop = true
                        }
                    },
                    onBack = navController::popBackStack,
                )
            }
            composable(
                Routes.SplitReview,
                arguments = listOf(
                    navArgument("jobId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { entry ->
                SplitImportReviewRoute(
                    repository = application.splitImportRepository,
                    initialJobId = entry.arguments?.getString("jobId"),
                    onOpenDraft = { draftId ->
                        navController.navigate(Routes.captureResume(draftId)) {
                            launchSingleTop = true
                        }
                    },
                    onFinished = { navController.popBackStack() },
                    modifier = Modifier.testTag("root_split_review"),
                )
            }
            composable(Routes.ExportResults) {
                MistakeExportHubRoute(
                    records = exportRecords,
                    notice = pendingExportNotice,
                    onDismissNotice = { pendingExportNotice = null },
                    onBack = navController::popBackStack,
                )
            }
            composable(Routes.CaptureResume) { entry ->
                CaptureScreen(
                    // Recovery replaces this placeholder with the draft's persisted origin.
                    entryOrigin = CaptureEntryOrigin.LIBRARY,
                    resumeDraftId = entry.arguments?.getString("draftId").orEmpty(),
                    repository = application.captureRepository,
                    modelTasks = application.modelTaskRepository,
                    modelEgressAllowed = baseCapabilities.networkRequestsAllowed,
                    modelConfigured = modelConfiguration.isConfigured,
                    // 切分复核的逐题续做（T4/AUD-03）：这一题入库后同任务里还有未提交的题，
                    // 就地换成下一题的草稿重开这一页——学生勾了多道题就该一道接一道录完，
                    // 而不是录完第一题停在原地、或绕去题库详情。
                    splitImportReceipts = application.splitImportRepository,
                    onOpenNextSplitQuestion = { draftId ->
                        navController.navigate(Routes.captureResume(draftId)) {
                            popUpTo(Routes.CaptureResume) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onOpenModelSettings = { navController.navigate(Routes.Capability) },
                    onTutorSessionReady = { sessionId ->
                        navController.navigate(Routes.capturedTutorSession(sessionId)) {
                            popUpTo(Routes.CaptureResume) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onLibraryEntryReady = { entryId ->
                        navController.navigate(Routes.mistakeDetail(entryId)) {
                            popUpTo(Routes.CaptureResume) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onSplitReady = { jobId ->
                        navController.navigate(Routes.splitReview(jobId)) {
                            popUpTo(Routes.CaptureResume) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onBack = navController::popBackStack,
                )
            }
            // 拍照讲解：拍完就地落回讲题页面，把这次拍好的会话作为**本轮附件**带进去
            // （同一个页面、同一个标题栏、底栏仍停在「智能体」），而不是另开一个页面。
            composable(Routes.CapturedTutorSession) { entry ->
                val sessionId = entry.arguments?.getString("sessionId").orEmpty()
                CapturedTutorSessionRoute(
                    sessionId = sessionId,
                    repository = application.captureRepository,
                    modelTasks = application.modelTaskRepository,
                    interactions = application.tutorInteractionRepository,
                    conversations = application.tutorConversationRepository,
                    profile = experience.profile,
                    catalogEntries = experience.catalog,
                    attachedImageResolver = application.attachedImageResolver {
                        application.captureRepository.readTutorSessionSheetBytes(sessionId)
                    },
                    onOpenModelSettings = { navController.navigate(Routes.Capability) },
                    onOpenHistory = { navController.navigate(Routes.TutorHistory) },
                    onOpenMistakeNotebook = {
                        navController.navigate(Routes.Library) { launchSingleTop = true }
                    },
                    // 会话里也能像大厅一样给学生消息附图（例如自己的手写过程）。
                    imageIntake = application.lobbyMessageImageIntake,
                    // 加号菜单「从错题库选择」：与错题讲题页同一条读取口，选中后成为
                    // 这一轮要讲的那道题（显式附加）。
                    attachedQuestionReader = application.tutorAttachedQuestionReader,
                    onOpenProfile = {
                        navController.navigate(Routes.Profile) { launchSingleTop = true }
                    },
                    onBack = navController::popBackStack,
                    onEndedWithoutSave = { navController.popBackStack() },
                    // 拍照讲题知识注入（ADR 0001 / D5、审计 R2 断链一）：两段式检索候选 + 材料。
                    knowledgeContextLoader = application.tutorKnowledgeContextLoader,
                    teachingReferenceRepository = application.tutorTeachingReferenceRepository,
                    // 确认卡（A4 执行路径 ①）：这条会话锚着本次拍照，模型申请"加入错题本"时
                    // 挂出的卡执行的就是"把这次拍照的草稿存进错题本"（真实落库）。
                    pendingRequests = application.agentPendingRequestRepository,
                )
            }
            composable(Routes.MistakeDetail) { entry ->
                MistakeDetailRoute(
                    errorBookEntryId = entry.arguments?.getString("itemId").orEmpty(),
                    repository = application.mistakeDetailRepository,
                    organizationRepository = application.mistakeOrganizationRepository,
                    modelTasks = application.modelTaskRepository,
                    profile = experience.profile,
                    catalogEntries = experience.catalog,
                    onBack = navController::popBackStack,
                    // L7：导出改为后台任务——点下即入队，离开页面不再取消渲染；
                    // 跳到「导出成果」看进度/结果，通知是同一结果的另一条路径。
                    // PRD-04/T4：先选 PDF/Word，确认后才入队（取消不导航、不入队）。
                    onExport = { key ->
                        pendingExportTarget = PendingExportTarget.Single(key)
                    },
                    onTutor = { key ->
                        navController.navigate(Routes.mistakeTutor(key)) {
                            launchSingleTop = true
                        }
                    },
                    onOpenRelatedMistake = { relatedEntryId ->
                        navController.navigate(Routes.mistakeDetail(relatedEntryId))
                    },
                    knowledgeBaseAvailability = knowledgeBaseAvailability,
                    onOpenModelSettings = { navController.navigate(Routes.Capability) },
                )
            }
            composable(Routes.MistakeTutor) { entry ->
                SavedMistakeTutorDestination(
                    entry = entry,
                    experience = experience,
                    application = application,
                    navController = navController,
                )
            }
            composable(Routes.Capability) {
                CapabilityScreen(
                    capabilities = capabilities,
                    configurationStore = application.modelConfigurationStore,
                    capabilityTester = application.modelCapabilityTester,
                    calibrationReportProvider = { application.studyRepository.calibrationReport() },
                    sourceCalibrationProvider = { application.studyRepository.sourceCalibrations() },
                    onBack = navController::popBackStack,
                )
            }
            composable(Routes.LearningMastery) {
                LearningMasteryScreen(
                    overview = experience.profile,
                    onBack = navController::popBackStack,
                    onOpenLibrary = {
                        navController.navigate(Routes.Library) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    knowledgeBaseAvailability = knowledgeBaseAvailability,
                )
            }
            composable(Routes.Privacy) {
                DataPrivacyScreen(
                    capabilities = capabilities,
                    onBack = navController::popBackStack,
                )
            }
            composable(Routes.Reminder) {
                var suggestedReminderMinute by remember { mutableStateOf<Int?>(null) }
                LaunchedEffect(Unit) {
                    // 学习数据瞬时不可用时页面仍要能打开：只损失预填建议值。
                    suggestedReminderMinute = runCatching {
                        application.studyRepository.suggestedReminderMinute()
                    }.getOrNull()
                }
                ReminderScreen(
                    repository = application.reviewReminderRepository,
                    onRefreshSchedule = application::refreshReviewReminderSchedule,
                    onBack = navController::popBackStack,
                    suggestedMinute = suggestedReminderMinute,
                )
            }
            composable(Routes.Scheduling) {
                val schedulingOptions by application.schedulingSettingsStore.options
                    .collectAsStateWithLifecycle(initialValue = SchedulingOptions())
                val exams by application.schedulingSettingsStore.exams
                    .collectAsStateWithLifecycle(initialValue = emptyList())
                val schedulingScope = rememberCoroutineScope()
                var retentionHint by remember {
                    mutableStateOf<com.tingyun.smartmistakebook.core.domain.OptimalRetention.Recommendation?>(null)
                }
                LaunchedEffect(schedulingOptions) {
                    // 参考值加载失败不显示（与"样本足够才显示"同一语义），页面保持可用。
                    retentionHint = runCatching {
                        application.studyRepository.recommendedDesiredRetention()
                    }.getOrNull()
                }
                SchedulingSettingsScreen(
                    options = schedulingOptions,
                    exams = exams,
                    retentionHint = retentionHint,
                    onSetOptions = { updated ->
                        schedulingScope.launch {
                            application.schedulingSettingsStore.setOptions(updated)
                        }
                    },
                    onAddExam = { entry ->
                        schedulingScope.launch {
                            application.schedulingSettingsStore.addExam(entry)
                        }
                    },
                    onRemoveExam = { entryId ->
                        schedulingScope.launch {
                            application.schedulingSettingsStore.removeExam(entryId)
                        }
                    },
                    onBack = navController::popBackStack,
                )
            }
            composable(Routes.Storage) {
                StorageScreen(
                    onBack = navController::popBackStack,
                    backupRepository = application.backupRepository,
                    // 恢复仓库在关库前/返回前已经发布过准确的重启原因（含“设置未能完整
                    // 写入、可能有部分设置已变”）。存储页传回的 operationMessage 可能更旧
                    // 或更绝对（例如“本机设置保持原样”），不能覆盖仓库的最终通知；
                    // 只有仓库还没发布原因时（例如异常路径之外）才用它兜底。
                    onDatabaseClosed = { message ->
                        if (application.databaseRestartReason.value == null) {
                            application.requireDatabaseRestart(message)
                        }
                    },
                )
            }
            }
        }
    }
    // PRD-04/T4：原生单题/批量导出共用的格式 sheet。取消只清目标（不导航/不入队）；
    // 确认才把格式原样随任务交给 worker，并跳「导出成果」看后台进度。
    pendingExportTarget?.let { target ->
        MistakeExportFormatSheet(
            subtitle = when (target) {
                is PendingExportTarget.Single -> "导出这一道错题"
                is PendingExportTarget.Batch ->
                    "导出当前筛选的 ${target.entryIds.size} 道题"
            },
            onDismiss = { pendingExportTarget = null },
            onConfirm = { format ->
                pendingExportTarget = null
                when (target) {
                    is PendingExportTarget.Single ->
                        application.startMistakeExportSingle(target.key, format)
                    is PendingExportTarget.Batch ->
                        application.startMistakeExportBatch(target.entryIds, format = format)
                }
                pendingExportNotice = null
                navController.navigate(Routes.ExportResults) {
                    launchSingleTop = true
                }
            },
        )
    }
}

@Composable
internal fun ReviewSessionGateMessage(
    message: String,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 26.dp, vertical = 24.dp),
    ) {
        Text(
            text = message,
            modifier = Modifier.testTag("review_session_gate"),
            color = InkSecondary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
        )
        onRetry?.let { retry ->
            OutlineActionChip(
                text = "重试",
                onClick = retry,
                modifier = Modifier
                    .padding(top = 10.dp)
                    .testTag("review_session_gate_retry"),
            )
        }
    }
}

/** 知识点复习计划的三态：加载中 / 拉取失败（可重试）/ 就绪（可能为空计划）。 */
private sealed interface KnowledgeReviewPlanState {
    data object Loading : KnowledgeReviewPlanState
    data object Failed : KnowledgeReviewPlanState
    data class Ready(val plan: KnowledgeReviewSessionPlan) : KnowledgeReviewPlanState
}

@Composable
private fun StudyDataStatusLine(status: StudyDataStatus) {
    val message = when (status) {
        StudyDataStatus.LOADING -> "正在读取本机学习记录…"
        StudyDataStatus.ERROR -> "本机学习数据暂时无法更新；不会用空白结果替代已有记录。"
        StudyDataStatus.READY -> return
    }
    Text(
        text = message,
        modifier = Modifier
            .fillMaxWidth()
            .background(if (status == StudyDataStatus.ERROR) JadeSoft else Paper)
            .padding(horizontal = 26.dp, vertical = 8.dp),
        color = if (status == StudyDataStatus.ERROR) InkSecondary else JadeActive,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
private fun SmartBottomBar(
    selectedRoute: String,
    onSelect: (RootDestination) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(SmartDimens.BottomBarHeight)
            .background(Paper),
    ) {
        PaperDivider()
        NavigationBar(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .navigationBarsPadding(),
            containerColor = Paper,
            tonalElevation = 0.dp,
            windowInsets = WindowInsets(0),
        ) {
            rootDestinations.forEach { destination ->
                val selected = selectedRoute == destination.route
                NavigationBarItem(
                    selected = selected,
                    onClick = { onSelect(destination) },
                    icon = {
                        Icon(
                            imageVector = destination.icon,
                            contentDescription = null,
                            modifier = Modifier.size(27.dp),
                        )
                    },
                    label = {
                        Text(
                            text = destination.label,
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        )
                    },
                    modifier = Modifier.testTag(destination.testTag),
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = JadeActive,
                        selectedTextColor = JadeActive,
                        indicatorColor = JadeSoft,
                        unselectedIconColor = InkSecondary,
                        unselectedTextColor = InkSecondary,
                    ),
                    alwaysShowLabel = true,
                )
            }
        }
    }
}

internal fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
