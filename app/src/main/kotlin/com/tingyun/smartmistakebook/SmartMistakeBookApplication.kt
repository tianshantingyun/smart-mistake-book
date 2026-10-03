package com.tingyun.smartmistakebook

import android.app.Activity
import android.app.Application
import android.os.StrictMode
import com.tingyun.smartmistakebook.core.data.capture.CaptureWorkflowRepositoryFactory
import com.tingyun.smartmistakebook.core.data.capture.ConfiguredCleanImageGeneratorFactory
import com.tingyun.smartmistakebook.core.data.capture.BatchImportRepositoryFactory
import com.tingyun.smartmistakebook.core.data.backup.BackupRepositoryFactory
import com.tingyun.smartmistakebook.core.data.backup.BackupRestoreStartupRecovery
import com.tingyun.smartmistakebook.core.data.backup.RestoreRecoveryAttention
import com.tingyun.smartmistakebook.core.data.tutor.AgentPendingRequestRepositoryFactory
import com.tingyun.smartmistakebook.core.data.tutor.TutorAttachedImageIntakeFactory
import com.tingyun.smartmistakebook.core.data.tutor.LobbyMessageImageIntakeFactory
import com.tingyun.smartmistakebook.core.data.backup.RestoreStartupOutcome
import com.tingyun.smartmistakebook.core.data.backup.attentionRequired
import com.tingyun.smartmistakebook.core.data.knowledge.BundledKnowledgeBaseInstaller
import com.tingyun.smartmistakebook.core.data.knowledge.TutorKnowledgeContextLoaderFactory
import com.tingyun.smartmistakebook.core.data.knowledge.TutorTeachingReferenceRepositoryFactory
import com.tingyun.smartmistakebook.core.data.library.LibraryCatalogRepositoryFactory
import com.tingyun.smartmistakebook.core.data.tutor.TutorAttachedQuestionReaderFactory
import com.tingyun.smartmistakebook.core.data.tutor.TutorRoundQuestionRetrieverFactory
import com.tingyun.smartmistakebook.core.domain.TutorAttachedQuestionReader
import com.tingyun.smartmistakebook.core.domain.AgentPendingRequestRepository
import com.tingyun.smartmistakebook.core.domain.TutorAttachedImageIntake
import com.tingyun.smartmistakebook.core.domain.TutorRoundQuestionRetriever
import com.tingyun.smartmistakebook.core.model.MistakePdfLayout
import com.tingyun.smartmistakebook.core.data.mistake.MistakeDetailRepositoryFactory
import com.tingyun.smartmistakebook.core.data.mistake.MistakeOrganizationRepositoryFactory
import com.tingyun.smartmistakebook.core.data.export.MistakeExportRepositoryFactory
import com.tingyun.smartmistakebook.core.data.model.AttachedImageGeneratorFactory
import com.tingyun.smartmistakebook.core.data.model.ConfiguredModelGatewayFactory
import com.tingyun.smartmistakebook.core.data.model.ConfiguredModelCapabilityTesterFactory
import com.tingyun.smartmistakebook.core.data.model.ModelTaskRepositoryFactory
import com.tingyun.smartmistakebook.core.data.model.TutorFigureGeneratorFactory
import com.tingyun.smartmistakebook.core.data.model.RestrictedModelAssetSourceFactory
import com.tingyun.smartmistakebook.core.data.model.UnavailableModelGateway
import com.tingyun.smartmistakebook.core.data.study.StudyExperienceRepositoryFactory
import com.tingyun.smartmistakebook.core.data.tutor.TutorInteractionRepositoryFactory
import com.tingyun.smartmistakebook.core.data.tutor.TutorConversationRepositoryFactory
import com.tingyun.smartmistakebook.core.data.settings.DataStoreModelConfigurationStore
import com.tingyun.smartmistakebook.core.data.settings.DataStoreReviewReminderRepository
import com.tingyun.smartmistakebook.core.data.settings.DataStoreSleepJournalStore
import com.tingyun.smartmistakebook.core.data.settings.DataStoreSchedulingSettingsStore
import com.tingyun.smartmistakebook.core.database.StudyDatabaseFactory
import com.tingyun.smartmistakebook.core.data.knowledge.dense.DenseRecallAssembly
import com.tingyun.smartmistakebook.core.database.StudyDatabasePort
import com.tingyun.smartmistakebook.core.domain.CaptureWorkflowRepository
import com.tingyun.smartmistakebook.core.domain.BatchImportRepository
import com.tingyun.smartmistakebook.core.domain.BackupRepository
import com.tingyun.smartmistakebook.core.domain.MistakeDetailRepository
import com.tingyun.smartmistakebook.core.domain.MistakeDetailState
import com.tingyun.smartmistakebook.core.domain.MistakeOrganizationRepository
import com.tingyun.smartmistakebook.core.domain.ModelConfigurationStore
import com.tingyun.smartmistakebook.core.domain.ModelCapabilityTester
import com.tingyun.smartmistakebook.core.domain.ModelTaskRepository
import com.tingyun.smartmistakebook.core.domain.KnowledgeBaseAvailability
import com.tingyun.smartmistakebook.core.domain.KnowledgeBaseAvailabilityTracker
import com.tingyun.smartmistakebook.core.domain.LibraryCatalogRepository
import com.tingyun.smartmistakebook.core.domain.MISTAKE_EXPORT_RUNNING_TIMEOUT_MILLIS
import com.tingyun.smartmistakebook.core.domain.MistakeExportKind
import com.tingyun.smartmistakebook.core.domain.MistakeExportRepository
import com.tingyun.smartmistakebook.core.domain.MistakeRevisionKey
import com.tingyun.smartmistakebook.core.domain.ReviewReminderRepository
import com.tingyun.smartmistakebook.core.domain.SchedulingSettingsStore
import com.tingyun.smartmistakebook.core.domain.SleepJournalStore
import com.tingyun.smartmistakebook.core.domain.StudyExperienceRepository
import com.tingyun.smartmistakebook.core.domain.SplitImportRepository
import com.tingyun.smartmistakebook.core.data.splitimport.SplitImportRepositoryFactory
import com.tingyun.smartmistakebook.core.export.MistakeExportJobRequest
import com.tingyun.smartmistakebook.core.export.MistakeExportJobRunner
import com.tingyun.smartmistakebook.core.export.MistakePdfExporter
import com.tingyun.smartmistakebook.core.domain.TutorInteractionRepository
import com.tingyun.smartmistakebook.core.domain.LobbyMessageImageIntake
import com.tingyun.smartmistakebook.core.domain.TutorConversationRepository
import com.tingyun.smartmistakebook.core.domain.TutorKnowledgeContextLoader
import com.tingyun.smartmistakebook.core.domain.TutorTeachingReferenceRepository
import com.tingyun.smartmistakebook.feature.capture.CaptureCacheMaintenance
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

class SmartMistakeBookApplication : Application() {
    // SupervisorJob stops sibling cancellation but does NOT swallow a child's
    // uncaught exception — without a handler it reaches the process default
    // handler and kills the app. This backstop logs instead of crashing; the
    // full stack stays visible in logcat.
    private val coroutineCrashBackstop = CoroutineExceptionHandler { _, failure ->
        android.util.Log.e("SmartMistakeBook", "Uncaught applicationScope coroutine failure", failure)
    }

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default + coroutineCrashBackstop)

    val startupState = MutableStateFlow<StartupState>(StartupState.Initializing)

    /**
     * 知识能力就绪位（决策台账 D-Q3：首装后台化）。
     *
     * **与 [startupState] 分开放**：`Ready` 是"错题与复习立刻可用"的秒开语义，本就绪位说的是
     * 另一件事——本机知识内容有没有准备好。首装那十几秒两者短暂不同步，正是本次要**如实**
     * 表达的状态：学习能力照常，依赖知识库的能力说"准备中"。
     *
     * 唯一写入方是本类的安装协程（成功→[KnowledgeBaseAvailability.Ready]，失败→
     * [KnowledgeBaseAvailability.Unavailable]，重试前回到 Preparing）；消费方只读。
     */
    private val knowledgeBaseAvailabilityTracker = KnowledgeBaseAvailabilityTracker()

    val knowledgeBaseAvailability: StateFlow<KnowledgeBaseAvailability> =
        knowledgeBaseAvailabilityTracker.state

    lateinit var studyRepository: StudyExperienceRepository
    lateinit var schedulingSettingsStore: SchedulingSettingsStore
        private set
    lateinit var sleepJournalStore: SleepJournalStore
        private set

    lateinit var captureRepository: CaptureWorkflowRepository
        private set

    lateinit var batchImportRepository: BatchImportRepository
        private set

    lateinit var backupRepository: BackupRepository
        private set

    val studyDatabase: StudyDatabasePort
        get() = database

    lateinit var mistakeDetailRepository: MistakeDetailRepository

    /** 本轮候选菜单的本地检索源：由错题详情读口派生，无状态，可全局共用一个。 */
    lateinit var tutorRoundQuestionRetriever: TutorRoundQuestionRetriever
        private set

    /**
     * 加号菜单「从错题库选择」的题面读取器：与候选检索同一个错题详情读口，无状态。
     * 讲题会话（错题讲题页 / 拍照会话）拿到它才显示这一项。
     */
    lateinit var tutorAttachedQuestionReader: TutorAttachedQuestionReader
        private set

    lateinit var mistakeOrganizationRepository: MistakeOrganizationRepository
        private set

    lateinit var modelTaskRepository: ModelTaskRepository
        private set

    lateinit var tutorInteractionRepository: TutorInteractionRepository
        private set

    lateinit var tutorConversationRepository: TutorConversationRepository

    /** Lobby 消息附图的登记与解析（相机/相册选图 → 规范资产库）。 */
    lateinit var lobbyMessageImageIntake: LobbyMessageImageIntake
        private set

    /**
     * 待确认请求（A4 / 插眼 5）：确认卡落库的端口。挂起的卡是**行**，不是内存里的开关——
     * 进程死亡后重建的是同一张卡。
     */
    lateinit var agentPendingRequestRepository: AgentPendingRequestRepository
        private set

    /**
     * 聊天附图的上库接续（A4 的执行路径 ③）：把消息附带的图片送进既有录入链路
     * （单页 → 待处理草稿；多页 → 批量导入任务）。
     */
    lateinit var tutorAttachedImageIntake: TutorAttachedImageIntake
        private set

    lateinit var tutorTeachingReferenceRepository: TutorTeachingReferenceRepository
        private set

    /** 讲题会话知识点代号通道的预披露取数（两段式检索，拍照/错题讲题共用）。 */
    lateinit var tutorKnowledgeContextLoader: TutorKnowledgeContextLoader
        private set

    lateinit var libraryCatalogRepository: LibraryCatalogRepository
        private set

    lateinit var reviewReminderRepository: ReviewReminderRepository
        private set

    lateinit var splitImportRepository: SplitImportRepository
        private set

    /**
     * 导出记录（L7）：入队、worker 落终态、「导出成果」入口三处共用同一个仓库。
     */
    lateinit var mistakeExportRepository: MistakeExportRepository
        private set

    /**
     * 后台导出的渲染编排（L7）：worker 经它跑"读快照 → 判可导出 → 渲染 → 完整性核对"。
     * 只在这里装配一次——读口与 exporter 都是进程级无状态/带锁的。
     *
     * `internal`（而不是 `private set`）是**测试缝**：androidTest 注入一个渲染受闸门控制的
     * runner，钉住"离开页面瞬间记录仍在进行、离开之后才完成"（`MistakeExportBackgroundInstrumentedTest`）。
     * 生产没有第二个写入点。
     */
    internal lateinit var mistakeExportJobRunner: MistakeExportJobRunner

    /** 导出通知（两态 + 未授权退化）；worker 与前台服务路径共用。 */
    internal val mistakeExportNotifications by lazy { MistakeExportNotifications(this) }

    private lateinit var database: StudyDatabasePort
    private lateinit var reviewReminderCoordinator: ReviewReminderCoordinator

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build(),
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build(),
            )
        }
        CaptureCacheMaintenance.pruneExpiredFiles(this)
        try {
            // Repair any interrupted restore BEFORE the database is opened so
            // a half-swapped generation can never become visible to Room. The
            // outcome is kept: a rolled-back or quarantined restore means the
            // book is not what the student left, and staying silent about that
            // is indistinguishable from lying about their data.
            val restoreRecovery = BackupRestoreStartupRecovery.recoverOnStartup(this)
            // Stage-3 稠密腿（可选）：装配失败/未启用即返回 null，检索退回纯词面。
            // 总开关在 core:data 的 DenseRecallAssembly.ENABLED（一行回退）。
            database = StudyDatabaseFactory.open(this, denseRerank = DenseRecallAssembly.reranker(this))
            schedulingSettingsStore = DataStoreSchedulingSettingsStore(this, applicationScope)
            // Settings are read synchronously to mirror the synchronous database
            // open above; a scheduling-flag flip applies on the next launch so a
            // single session's projection model stays stable (spec 2.20).
            val schedulingOptions = runBlocking { schedulingSettingsStore.options.first() }
            val optimizedParameters = runBlocking { schedulingSettingsStore.optimizedParameters.first() }
            studyRepository = StudyExperienceRepositoryFactory.create(
                database = database,
                applicationScope = applicationScope,
                schedulingOptions = schedulingOptions,
                schedulingSettingsStore = schedulingSettingsStore,
                optimizedFsrsParameters = optimizedParameters,
            )
            mistakeDetailRepository = MistakeDetailRepositoryFactory.create(this, database)
            tutorRoundQuestionRetriever = TutorRoundQuestionRetrieverFactory.create(
                mistakeDetailRepository,
            )
            tutorAttachedQuestionReader = TutorAttachedQuestionReaderFactory.create(
                mistakeDetailRepository,
            )
            mistakeOrganizationRepository = MistakeOrganizationRepositoryFactory.create(
                database = database,
                knowledgeBaseAvailability = knowledgeBaseAvailability,
            )
            tutorInteractionRepository = TutorInteractionRepositoryFactory.create(database)
            tutorConversationRepository = TutorConversationRepositoryFactory.create(database)
            lobbyMessageImageIntake = LobbyMessageImageIntakeFactory.create(this, database)
            // 确认卡（A4 / 插眼 5）：模型申请的本地动作挂成一张**库里的行**（进程死亡后读得回来）。
            agentPendingRequestRepository = AgentPendingRequestRepositoryFactory.create(database)
            tutorTeachingReferenceRepository =
                TutorTeachingReferenceRepositoryFactory.create(database)
            tutorKnowledgeContextLoader = TutorKnowledgeContextLoaderFactory.create(
                database = database,
                knowledgeBaseAvailability = knowledgeBaseAvailability,
            )
            libraryCatalogRepository = LibraryCatalogRepositoryFactory.create(database)
            // L7 后台导出：记录仓库 + 渲染编排（读口与 exporter 都是既有实现，只做装配）。
            mistakeExportRepository = MistakeExportRepositoryFactory.create(database)
            val mistakePdfExporter = MistakePdfExporter(this)
            mistakeExportJobRunner = MistakeExportJobRunner(
                readExact = mistakeDetailRepository::readExact,
                readByEntryId = { entryId ->
                    mistakeDetailRepository.observe(entryId)
                        .first { it != MistakeDetailState.Loading }
                },
                renderPdf = mistakePdfExporter::prepare,
            )
            val roomSplitImportRepository = SplitImportRepositoryFactory.createConcrete(database)
            splitImportRepository = roomSplitImportRepository
            reviewReminderRepository = DataStoreReviewReminderRepository(this, applicationScope)
            sleepJournalStore = DataStoreSleepJournalStore(this, applicationScope)
            registerActivityLifecycleCallbacks(
                object : ActivityLifecycleCallbacks {
                    // Silent sleep-window collection (spec 2.14): every time
                    // the app becomes visible one activity stamp is journaled;
                    // the gaps between stamps become the inferred nights.
                    override fun onActivityStarted(activity: Activity) {
                        applicationScope.launch {
                            sleepJournalStore.recordActivity(System.currentTimeMillis())
                        }
                    }

                    override fun onActivityCreated(activity: Activity, savedInstanceState: android.os.Bundle?) = Unit

                    override fun onActivityResumed(activity: Activity) = Unit

                    override fun onActivityPaused(activity: Activity) = Unit

                    override fun onActivityStopped(activity: Activity) = Unit

                    override fun onActivitySaveInstanceState(activity: Activity, outState: android.os.Bundle) = Unit

                    override fun onActivityDestroyed(activity: Activity) = Unit
                },
            )
            reviewReminderCoordinator = ReviewReminderCoordinator(
                repository = reviewReminderRepository,
                platform = ReviewReminderPlatform(this),
                pendingReviewCount = {
                    studyRepository.refresh()
                    val review = studyRepository.snapshot.value.review
                    if (review.completedToday) {
                        0
                    } else {
                        (review.scheduledCount - review.currentOrdinal).coerceAtLeast(0)
                    }
                },
                scope = applicationScope,
            ).also(ReviewReminderCoordinator::start)
            val gateway = modelConfigurationStore?.let { configurationStore ->
                ConfiguredModelGatewayFactory.create(
                    configurationStore = configurationStore,
                    assetSource = RestrictedModelAssetSourceFactory.create(this, database),
                )
            } ?: UnavailableModelGateway()
            modelTaskRepository = ModelTaskRepositoryFactory.create(
                database = database,
                gateway = gateway,
                // 工具环里的 KNOWLEDGE_READ 读这个就绪位：内容没就位时回"还在准备"，
                // 而不是"知识库里没有匹配的知识点"（D-Q3 的六号消费点）。
                knowledgeBaseAvailability = knowledgeBaseAvailability,
                // 4B A1：工具环生图（GENERATE_FIGURE）复用同一份模型凭证与 SSRF 防护通道；
                // 没配模型时整条链保持 null（工具回"不可用"，不静默假成功）。
                // A2：生成结果登记 canonical 行（幂等键派生的 id），同键重放不再出网付费。
                figureGenerator = modelConfigurationStore?.let { configurationStore ->
                    TutorFigureGeneratorFactory.create(
                        context = this,
                        database = database,
                        configurationStore = configurationStore,
                        networkRequestsAllowed = capabilities.networkRequestsAllowed,
                    )
                },
            )
            captureRepository = CaptureWorkflowRepositoryFactory.create(
                context = this,
                database = database,
                // Clean redraw engine: reused by the save decision round once the
                // model classifies the committed photo as figure-bearing.
                cleanRedraw = modelConfigurationStore?.let { store ->
                    ConfiguredCleanImageGeneratorFactory.create(
                        configurationStore = store,
                        networkRequestsAllowed = capabilities.networkRequestsAllowed,
                    )
                },
                cleanRedrawScope = applicationScope,
                // Save path runs a model classify round to decide redraw. Egress needs only a
                // configured model: the global "model agent" consent toggle was removed
                // (2026-09-13), so a configured provider is the single condition. No model →
                // no round, original photo kept.
                modelTasks = modelTaskRepository,
                captureEgressAllowed = { modelConfigurationStore != null },
                // Capture splits register a review job so a multi-question photo
                // reaches the split-review page instead of stranding its drafts.
                splitImports = roomSplitImportRepository,
            )
            batchImportRepository = BatchImportRepositoryFactory.create(
                context = this,
                database = database,
                capture = captureRepository,
                processingScope = applicationScope,
                modelTasks = modelTaskRepository,
                splitImports = roomSplitImportRepository,
                // Batch page organization egresses page images; same single condition as the
                // capture save path above (configured model).
                modelEgressAllowed = { modelConfigurationStore != null },
                // The in-process pass dies with the process; this makes the durable
                // driving request exist before it is needed.
                onWorkScheduled = { BatchImportDriver.enqueue(this) },
            )
            backupRepository = BackupRepositoryFactory.create(this, database)
            // 确认卡执行路径 ③（A4）：聊天附图 → 既有录入链路。放在批量和采集之后——它两者都用到。
            tutorAttachedImageIntake = TutorAttachedImageIntakeFactory.create(
                images = lobbyMessageImageIntake,
                capture = captureRepository,
                batchImports = batchImportRepository,
            )
            OrphanAssetGc.enqueue(this)
            // An import interrupted by a process kill is resumed without waiting
            // for the student to reopen the screen; the worker no-ops quickly when
            // nothing is PROCESSING.
            BatchImportDriver.enqueue(this)
            // L7：上次运行里被系统/进程杀死的导出 worker 没人写终态，记录会永远"正在整理"。
            // 启动时对账一次（只动超过阈值的 RUNNING 行），把僵尸如实标成失败。
            applicationScope.launch {
                val now = System.currentTimeMillis()
                runCatching {
                    mistakeExportRepository.reconcileStaleRunningRecords(
                        staleBeforeEpochMillis = now - MISTAKE_EXPORT_RUNNING_TIMEOUT_MILLIS,
                        atEpochMillis = now,
                    )
                }
            }
            startupState.value = StartupState.Ready
            // Applied after Ready so a successful open is still reported as
            // usable: the book works, the student just has to know that a
            // restore did not land.
            restoreRecoveryFailure(restoreRecovery)?.let { failure ->
                startupState.value = failure
            }
            // A split job left in PREPARING by an interrupted create→ready sequence
            // is visible in the review screen but refuses every operation on it,
            // leaving the student only the option of discarding the model's work.
            // Repaired here, inside the block that owns an opened database, so an
            // unopenable database (FatalFailure, below) skips it by construction.
            applicationScope.launch {
                runCatching {
                    roomSplitImportRepository.reconcileStuckJobs(System.currentTimeMillis())
                }.onSuccess { promoted ->
                    if (promoted > 0) {
                        android.util.Log.i(
                            "SmartMistakeBook",
                            "Recovered $promoted split import job(s) stuck in PREPARING",
                        )
                    }
                }.onFailure { failure ->
                    android.util.Log.w(
                        "SmartMistakeBook",
                        "Split import recovery failed",
                        failure,
                    )
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            startupState.value = StartupState.FatalFailure(
                title = "应用数据无法打开",
                message = "数据库初始化失败，暂时不能安全读写学习记录。",
                diagnosticId = "startup:database:${System.currentTimeMillis().hashCode().toUInt()}",
                errorCategory = StartupErrorCategory.DATABASE,
            )
            return
        }
        // 首装的全量解析（实测 16.7s）挪出启动关键路径（D-Q3）：这条协程只负责"内容就位"，
        // 与下面那条"学习记录就绪"**并行**——学生的学习能力不必等知识内容解析完。
        // 就绪前依赖知识库的能力读 [knowledgeBaseAvailability]，如实说"准备中"。
        applicationScope.launch {
            knowledgeBaseAvailabilityTracker.markPreparing()
            try {
                BundledKnowledgeBaseInstaller.install(database)
                // R4a：调和全部完成，数据已在 Room——释放包对象图驻留（~40MB 级）。
                // 后续 install() 命中快路径（戳一致）时零解析，释放安全。
                BundledKnowledgeBaseInstaller.releaseResidentPacks()
                knowledgeBaseAvailabilityTracker.markReady()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                android.util.Log.e(
                    "SmartMistakeBook",
                    "Bundled knowledge install failed",
                    failure,
                )
                val diagnosticId = "startup:knowledge:${failure.hashCode().toUInt()}"
                // 同一编号贯穿横幅与就绪位：学生点"重试"时能对上日志里的这一次失败。
                knowledgeBaseAvailabilityTracker.markUnavailable(diagnosticId)
                startupState.value = StartupState.RecoverableFailure(
                    title = "本地知识包尚未准备好",
                    message = "错题和复习可以继续使用，自动分类会暂缓。",
                    diagnosticId = diagnosticId,
                    errorCategory = StartupErrorCategory.KNOWLEDGE_BASE,
                )
            }
        }
        // 学习记录就绪与知识内容安装解耦：改前 install() 排在 initialize() 之前，
        // 首装那十几秒里连"书"都打不开。失败由仓库自己的快照（StudyDataStatus.ERROR +
        // 状态行）报给学生，不再借用知识包横幅——那会把学习数据故障误报成知识包问题。
        applicationScope.launch {
            try {
                studyRepository.initialize()
                if (startupState.value is StartupState.Ready) {
                    startupState.value = StartupState.Ready
                }
                // Silent FSRS parameter refit (spec §2.11): self-gated by the
                // fsrs-rs data thresholds (>=64 samples for a full fit) and
                // failure-proof; fitted parameters apply on the next launch.
                applicationScope.launch {
                    runCatching { studyRepository.optimizeSchedulingParameters() }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                android.util.Log.e(
                    "SmartMistakeBook",
                    "Study experience initialization failed",
                    failure,
                )
            }
        }
    }

    fun handleReviewReminderBroadcast(action: String?, onFinished: () -> Unit) {
        applicationScope.launch {
            try {
                reviewReminderCoordinator.handleBroadcast(action)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // A reminder must never crash app startup; the next preference emission repairs it.
            } finally {
                onFinished()
            }
        }
    }

    fun refreshReviewReminderSchedule() {
        applicationScope.launch {
            reviewReminderCoordinator.refresh()
        }
    }

    /**
     * L7：启动一次**单题**后台导出。入队后即可离开页面——渲染归 WorkManager，成果在
     * 「导出成果」与完成通知里。
     */
    fun startMistakeExportSingle(key: MistakeRevisionKey) {
        enqueueMistakeExport(
            request = MistakeExportJobRequest.Single(
                exportId = newMistakeExportId(),
                key = key,
            ),
            kind = MistakeExportKind.SINGLE,
        )
    }

    /**
     * L7：启动一次**当前筛选**批量导出。入口已把候选收敛到上限内（超限时入口会如实说明
     * 原因、不调用这里）；这里仍去重，防同一道题在一个批次里出现两次。
     *
     * 4B 批 4：`START_EXPORT` 的导出 sheet 把学生改完的版式（[layout]）随任务交给 worker——
     * 版式是渲染与缓存键的一部分，不能只活在 sheet 的内存里。
     */
    fun startMistakeExportBatch(
        entryIds: List<String>,
        layout: MistakePdfLayout = MistakePdfLayout.DEFAULT,
    ) {
        val distinct = entryIds.distinct()
        if (distinct.isEmpty()) return
        enqueueMistakeExport(
            request = MistakeExportJobRequest.Batch(
                exportId = newMistakeExportId(),
                entryIds = distinct,
                layout = layout,
            ),
            kind = MistakeExportKind.BATCH,
        )
    }

    private fun enqueueMistakeExport(
        request: MistakeExportJobRequest,
        kind: MistakeExportKind,
    ) {
        applicationScope.launch {
            try {
                // 对账一次：上次被杀死的 worker 可能留下僵尸 RUNNING（"永久正在整理"）。
                // 只动超过阈值、仍无终态的行；正常排队中的导出不受影响。
                val now = System.currentTimeMillis()
                runCatching {
                    mistakeExportRepository.reconcileStaleRunningRecords(
                        staleBeforeEpochMillis = now - MISTAKE_EXPORT_RUNNING_TIMEOUT_MILLIS,
                        atEpochMillis = now,
                    )
                }
                val record = mistakeExportRepository.recordStarted(
                    exportId = request.exportId,
                    kind = kind,
                    atEpochMillis = now,
                )
                // 没落上记录就不入队：没有行的导出没有可回看的成果，"幽灵任务"只会白渲染。
                if (record == null) {
                    android.util.Log.w(
                        "SmartMistakeBook",
                        "Mistake export was not enqueued: no record row for ${request.exportId}",
                    )
                    return@launch
                }
                ExportPdfDriver.enqueue(this@SmartMistakeBookApplication, request)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                // 入队失败：不留 RUNNING 幽灵行（记录表上的"正在整理"必须对应一个真实任务）。
                runCatching {
                    mistakeExportRepository.recordFailed(
                        exportId = request.exportId,
                        failureMessage = "这次导出没有排上队，请稍后重新导出。",
                        atEpochMillis = System.currentTimeMillis(),
                    )
                }
                android.util.Log.e(
                    "SmartMistakeBook",
                    "Mistake export enqueue failed for ${request.exportId}",
                    failure,
                )
            }
        }
    }

    private fun newMistakeExportId(): String = "export:${UUID.randomUUID()}"

    fun refreshStudyExperience() {
        val currentStartup = startupState.value
        if (currentStartup is StartupState.FatalFailure) {
            // 数据库未初始化成功，无学习记录可刷新；onResume 每次都会调用这里。
            return
        }
        applicationScope.launch {
            if (
                currentStartup is StartupState.RecoverableFailure &&
                currentStartup.errorCategory == StartupErrorCategory.KNOWLEDGE_BASE
            ) {
                // 知识包失败是横幅重试唯一有实效的场景：重新安装，成功则收起横幅。
                // 就绪位同步走"准备中 → 就绪 / 仍旧不可用"：重试期间依赖知识库的能力
                // 回到"准备中"（而不是继续显示一个已经过期的成功态）。
                knowledgeBaseAvailabilityTracker.markPreparing()
                try {
                    BundledKnowledgeBaseInstaller.install(database)
                    // 与启动协程同口径：重试成功后驻留对象图同样释放。
                    BundledKnowledgeBaseInstaller.releaseResidentPacks()
                    knowledgeBaseAvailabilityTracker.markReady()
                    if (startupState.value == currentStartup) {
                        startupState.value = StartupState.Ready
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Throwable) {
                    // 保持失败横幅，学生可再次点击重试；就绪位停在"不可用"（编号沿用
                    // 横幅上那一个，重试失败不产生第二个编号让学生无从对照）。
                    knowledgeBaseAvailabilityTracker.markUnavailable(currentStartup.diagnosticId)
                }
            }
            try {
                studyRepository.refresh()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                // The repository publishes the recoverable state consumed by the root UI.
            }
        }
    }

    val capabilities by lazy {
        FlavorCapabilityFactory.create()
    }

    val modelConfigurationStore: ModelConfigurationStore? by lazy {
        if (capabilities.networkRequestsAllowed) {
            DataStoreModelConfigurationStore(this, applicationScope)
        } else {
            null
        }
    }

    val modelCapabilityTester: ModelCapabilityTester? by lazy {
        modelConfigurationStore?.let(ConfiguredModelCapabilityTesterFactory::create)
    }

    /**
     * A2（4B）：兼容配图链（`attachedImages`）的解析器装配口。
     *
     * `database` 是 Application 的私有成员，而 destinations 层两个路由各自需要不同的题面字节
     * 来源（拍照会话 vs 错题讲题页），所以这里只暴露"工厂 + 每路由的题面来源"这一条窄缝，
     * 不把整个数据库端口抬成公开面。生成结果登记 canonical 行并按幂等键复用。
     */
    fun attachedImageResolver(
        resolveCurrentSheetBytes: suspend () -> ByteArray?,
    ): suspend (com.tingyun.smartmistakebook.core.model.AttachedImage) -> String? =
        AttachedImageGeneratorFactory.create(
            context = this,
            database = database,
            configurationStore = modelConfigurationStore,
            networkRequestsAllowed = capabilities.networkRequestsAllowed,
            resolveCurrentSheetBytes = resolveCurrentSheetBytes,
        )

    override fun onTerminate() {
        studyRepository.close()
        database.close()
        applicationScope.cancel()
        super.onTerminate()
    }
}

/**
 * The startup state a recovery outcome warrants, or null when the student does
 * not need to hear about it.
 *
 * The book is usable in both reported cases — the record just is not what the
 * student last saw, so each message leads with what happened to their data and
 * avoids implying loss that did not occur. A clean start and a pre-swap cleanup
 * leave the live generation untouched and stay silent.
 */
private fun restoreRecoveryFailure(
    outcome: RestoreStartupOutcome,
): StartupState.RecoverableFailure? = when (outcome.attentionRequired()) {
    RestoreRecoveryAttention.NONE -> null

    RestoreRecoveryAttention.RESTORE_REVERTED -> StartupState.RecoverableFailure(
        title = "备份恢复没有完成",
        message = "已回到恢复之前的数据，错题记录仍然完整。需要的话可以重新恢复一次。",
        diagnosticId = "startup:restore-reverted:${outcome.diagnosticTag()}",
        errorCategory = StartupErrorCategory.DATABASE,
    )

    RestoreRecoveryAttention.DATA_QUARANTINED -> StartupState.RecoverableFailure(
        title = "恢复未完成，部分数据已隔离",
        message = "为避免读到损坏的数据，相关文件已单独隔离，当前记录可能不完整。",
        diagnosticId = "startup:restore-quarantined:${outcome.diagnosticTag()}",
        errorCategory = StartupErrorCategory.DATABASE,
    )
}

/** Stable per-incident identifier for support, never student-visible text. */
private fun RestoreStartupOutcome.diagnosticTag(): String = when (this) {
    RestoreStartupOutcome.NothingToRecover -> "none"
    is RestoreStartupOutcome.Cleaned -> restoreId
    is RestoreStartupOutcome.RolledBack -> restoreId
    is RestoreStartupOutcome.Quarantined -> restoreId
    is RestoreStartupOutcome.Unreadable -> reason.hashCode().toUInt().toString()
}
