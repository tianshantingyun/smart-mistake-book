package com.tingyun.smartmistakebook

import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.Process
import android.os.StrictMode
import com.tingyun.smartmistakebook.core.data.capture.CaptureWorkflowRepositoryFactory
import com.tingyun.smartmistakebook.core.data.capture.ConfiguredCleanImageGeneratorFactory
import com.tingyun.smartmistakebook.core.data.capture.ExplicitRedrawServiceFactory
import com.tingyun.smartmistakebook.core.data.capture.BatchImportRepositoryFactory
import com.tingyun.smartmistakebook.core.data.backup.BackupRepositoryFactory
import com.tingyun.smartmistakebook.core.data.backup.BackupRestoreStartupRecovery
import com.tingyun.smartmistakebook.core.data.backup.RestoreIndexRebuildCoordinator
import com.tingyun.smartmistakebook.core.data.backup.RestoreIndexRebuildStatus
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
import com.tingyun.smartmistakebook.core.model.MistakeExportFormat
import com.tingyun.smartmistakebook.core.model.MistakePdfLayout
import com.tingyun.smartmistakebook.core.data.mistake.MistakeDetailRepositoryFactory
import com.tingyun.smartmistakebook.core.data.mistake.MistakeOrganizationRepositoryFactory
import com.tingyun.smartmistakebook.core.data.export.MistakeExportRepositoryFactory
import com.tingyun.smartmistakebook.core.data.model.AttachedImageGeneratorFactory
import com.tingyun.smartmistakebook.core.data.model.ConfiguredModelGatewayFactory
import com.tingyun.smartmistakebook.core.data.model.ConfiguredModelCapabilityTesterFactory
import com.tingyun.smartmistakebook.core.data.model.ModelTaskRepositoryFactory
import com.tingyun.smartmistakebook.core.data.model.TutorFigureGeneratorFactory
import com.tingyun.smartmistakebook.core.data.visual.FigureGenerationCoordinationFactory
import com.tingyun.smartmistakebook.core.data.visual.FigureGenerationReconciler
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
import com.tingyun.smartmistakebook.core.domain.FigureGenerationCoordinator
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
import com.tingyun.smartmistakebook.core.export.MistakeDocxExporter
import com.tingyun.smartmistakebook.core.export.MistakeExportJobRequest
import com.tingyun.smartmistakebook.core.export.MistakeExportJobRunner
import com.tingyun.smartmistakebook.core.export.MistakePaperDraft
import com.tingyun.smartmistakebook.core.export.MistakePaperDraftStore
import com.tingyun.smartmistakebook.core.export.MistakePdfExporter
import com.tingyun.smartmistakebook.core.export.MistakePdfPaperEdit
import com.tingyun.smartmistakebook.core.domain.TutorInteractionRepository
import com.tingyun.smartmistakebook.core.domain.LobbyMessageImageIntake
import com.tingyun.smartmistakebook.core.domain.TutorConversationRepository
import com.tingyun.smartmistakebook.core.domain.TutorKnowledgeContextLoader
import com.tingyun.smartmistakebook.core.domain.TutorTeachingReferenceRepository
import com.tingyun.smartmistakebook.feature.capture.CaptureCacheMaintenance
import com.tingyun.smartmistakebook.diagnostics.DiagnosticsBootstrap
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
import kotlinx.coroutines.withContext
import java.util.UUID

class SmartMistakeBookApplication : Application() {
    /**
     * KD-61：恢复/清库关库前等应用级 collector 排空的上限。取值与写门的
     * `maxWriteQueueWaitMillis` 默认同量级（2s）——"关库前多等 collector 一会"与
     * "接门写在恢复交换段排队多久"是同一类有界等待；超时如实记日志并把残余归
     * KD-61（恢复不能被挂死的 collector 无限拖住）。
     */
    private companion object {
        const val BACKGROUND_WRITER_QUIESCE_TIMEOUT_MILLIS: Long = 2_000L
    }

    // SupervisorJob stops sibling cancellation but does NOT swallow a child's
    // uncaught exception — without a handler it reaches the process default
    // handler and kills the app. This backstop logs instead of crashing; the
    // full stack stays visible in logcat.
    private val coroutineCrashBackstop = CoroutineExceptionHandler { _, failure ->
        // T6：协程未捕获异常在 logcat 之外追加落进本机诊断账本（重启后仍可经诊断包导出）。
        DiagnosticsBootstrap.recordCoroutineFailure(failure)
        android.util.Log.e("SmartMistakeBook", "Uncaught applicationScope coroutine failure", failure)
    }

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default + coroutineCrashBackstop)

    /**
     * KD-61（批次 19 T3）：DataStore 专用 scope，**不参与恢复关库前的应用级 collector
     * quiesce**。两个原因：
     * 1. DataStore 写的是偏好文件，不进 room3 连接池——它不在 KD-61 的写入口面里，
     *    quiesce 它没有任何收益；
     * 2. 恢复的 Phase 8（交换成功后）要经 `applyRestoredSettings` 往 DataStore 写回
     *    设置；若它的 actor scope 已被取消，`edit` 会因 actor 通道关闭而失败（设置恢复
     *    静默降级为 WRITE_FAILED），超时未排空路径下消息甚至永远无人消费（恢复挂死）。
     * 给偏好持久化一个与 collector 分离的存活面，Phase 8 行为与 quiesce 引入前逐位一致。
     */
    private val dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + coroutineCrashBackstop)

    val startupState = MutableStateFlow<StartupState>(StartupState.Initializing)

    /**
     * 本进程的启动时刻（应用对象创建即是进程开始）。AUD-09 的净化作业对账拿它当"租约判据"：
     * 只接手**最后更新早于本进程**的作业行——上一进程被杀留下的行一定早于它，而本进程刚
     * 发起的保存路径作业不会被对账抢走双跑（那会重复付费）。
     */
    private val processStartedAtEpochMillis = System.currentTimeMillis()

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

    /**
     * 6B-2：编辑后卷面的草稿落盘（draft id → 块序列）。worker 渲染前读回，终态后清理。
     * 放在 `filesDir`（不是 cacheDir）：草稿是后台任务的**输入**，被系统清掉会让已入队的
     * 导出变成"没有可渲染的卷面"。
     */
    internal lateinit var mistakePaperDraftStore: MistakePaperDraftStore

    /** 导出通知（两态 + 未授权退化）；worker 与前台服务路径共用。 */
    internal val mistakeExportNotifications by lazy { MistakeExportNotifications(this) }

    private lateinit var database: StudyDatabasePort
    private lateinit var reviewReminderCoordinator: ReviewReminderCoordinator

    /**
     * T6/WG-05：历史配图的显式重生成必须复用初始化阶段创建的**同一个**共享 coordinator
     * （policy/ledger/通道）。`attachedImageResolver` 是公开装配口，UI 只在 startup Ready
     * 后读取；@Volatile 只是防御未来把初始化移出主线程。没配模型时为 null，重生成如实回
     * 不可用。
     */
    @Volatile
    private var attachedImageCoordinator: FigureGenerationCoordinator? = null

    private val restartReason = MutableStateFlow<String?>(null)
    internal val databaseRestartReason: StateFlow<String?> = restartReason

    internal fun requireDatabaseRestart(message: String) {
        restartReason.value = message
    }

    override fun onCreate() {
        super.onCreate()
        // 契约 visual-generation-contract.md §4（批次 22 T1）：`:visual_js` 是
        // android:isolatedProcess="true" 的专用进程，UID 与应用 UID 不同；它不挂载应用数据
        // 目录、也没有应用权限。必须在打开 Room、恢复备份、维护 filesDir、装配 Provider/密钥
        // 工厂之前短路——只在 onCreate 末尾加 guard 等于让副作用先发生。
        //
        // 这是公开 API 写法（API 23 可用）；API 28+ 再叠加 Process.isIsolated() 作为
        // 纵深防御（R1 §9.4：applicationInfo.uid 在隔离进程中的实际值未设备验证）。
        // 字段初始化审计（R1 §7.2）：本类构造期的 applicationScope/dataStoreScope/
        // startupState/tracker 只分配对象与协程作用域；capabilities/modelConfigurationStore
        // 均为 by lazy，隔离进程不会触达。ContentProvider/AndroidX Startup 的实际装配面
        // 仍待设备 `dumpsys activity providers` 审计（T1 登记为 UNVERIFIED）。
        val isolatedWorker = Process.myUid() != applicationInfo.uid ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && Process.isIsolated())
        if (isolatedWorker) {
            return
        }
        // T6 beta 反馈闭环：诊断装配先于一切初始化——启动最早的崩溃也要有记录。
        // 崩溃 handler 链回系统 previous（杀进程行为不变）；看门狗只记录不杀进程；
        // 启动对账（"上次异常退出"横幅文案）放后台协程，不阻塞启动。各环节内部吞错，
        // 记录只落本机 filesDir/diagnostics（零外发；strictOffline 无 INTERNET 权限）。
        DiagnosticsBootstrap.install(this, BuildConfig.FLAVOR)
        applicationScope.launch {
            runCatching { DiagnosticsBootstrap.reconcileAtStartup(processStartedAtEpochMillis) }
        }
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
            schedulingSettingsStore = DataStoreSchedulingSettingsStore(this, dataStoreScope)
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
            val gateway = modelConfigurationStore?.let { configurationStore ->
                ConfiguredModelGatewayFactory.create(
                    configurationStore = configurationStore,
                    assetSource = RestrictedModelAssetSourceFactory.create(this, database),
                )
            } ?: UnavailableModelGateway()
            // 4E.1（WG-01）：一个共享 coordinator + 持久派发账本，注入讲题工具与保存净化
            // 两条入口；入口不再各自建图像通道/预算。没配模型时保持 null（各入口维持
            // 既有"不可用"行为，不出网）。
            // 4E WG-08 T1b：`FigureGenerationCoordinationFactory` 已把审计记录器接到这个
            // 共享 coordinator 上（operation/attempt 两张表）。审计只写证据，预算权威仍是
            // 派发账本；写不进审计不影响生成结论。
            val figureGenerationCoordinator = modelConfigurationStore?.let { configurationStore ->
                FigureGenerationCoordinationFactory.create(
                    database = database,
                    configurationStore = configurationStore,
                    networkRequestsAllowed = capabilities.networkRequestsAllowed,
                )
            }
            attachedImageCoordinator = figureGenerationCoordinator
            // 同一份净化引擎既给保存路径（captureRepository.cleanRedraw）也给用户显式重绘：
            // 两者共享同一 coordinator（凭证门/预算账本/审计记录）与同一套派发父操作。
            val cleanImageGenerator = modelConfigurationStore?.let { store ->
                figureGenerationCoordinator?.let { coordinator ->
                    ConfiguredCleanImageGeneratorFactory.create(
                        configurationStore = store,
                        networkRequestsAllowed = capabilities.networkRequestsAllowed,
                        coordinator = coordinator,
                    )
                }
            }
            // 4E WG-06 T3：详情页的"重绘干净题面"只有用户点击才建行/派发；落盘直接复用
            // capture 的 attach（同一份 CLEAN_IMAGE 引用 + current-revision 判据）。
            // attach 在这里只捕获装配对象，真正的仓库在下面创建，调用发生在启动完成之后。
            // 4E WG-03：挂接缝升级为类型化 FigureArtifactRef（canonical 图先核对内容 sha
            // 才绑定修订；provenance 按真实方法填写）。
            val explicitRedrawService = ExplicitRedrawServiceFactory.create(
                context = this,
                database = database,
                cleanImageGenerator = cleanImageGenerator,
                attach = { revisionId, artifact ->
                    captureRepository.attachCleanRedrawArtifact(revisionId, artifact)
                },
                egressAllowed = { modelConfigurationStore != null },
                scope = applicationScope,
            )
            mistakeDetailRepository = MistakeDetailRepositoryFactory.create(
                context = this,
                database = database,
                explicitRedraw = explicitRedrawService,
            )
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
            mistakePaperDraftStore = MistakePaperDraftStore(
                java.io.File(filesDir, "mistake_paper_drafts"),
            )
            mistakeExportJobRunner = MistakeExportJobRunner(
                readExact = mistakeDetailRepository::readExact,
                readByEntryId = { entryId ->
                    mistakeDetailRepository.observe(entryId)
                        .first { it != MistakeDetailState.Loading }
                },
                renderPdf = mistakePdfExporter::prepare,
                // 6B-1：Word 交付与 PDF 共用同一条读快照/判定/记录/通知管线，只有渲染器不同。
                renderDocx = MistakeDocxExporter(this)::prepare,
                // 6B-2：编辑后的卷面从草稿读——不按条目重读，编辑结果就是渲染输入。
                readPaperDraft = mistakePaperDraftStore::read,
            )
            val roomSplitImportRepository = SplitImportRepositoryFactory.createConcrete(database)
            splitImportRepository = roomSplitImportRepository
            reviewReminderRepository = DataStoreReviewReminderRepository(this, dataStoreScope)
            sleepJournalStore = DataStoreSleepJournalStore(this, dataStoreScope)
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
            modelTaskRepository = ModelTaskRepositoryFactory.create(
                database = database,
                gateway = gateway,
                // 工具环里的 KNOWLEDGE_READ 读这个就绪位：内容没就位时回"还在准备"，
                // 而不是"知识库里没有匹配的知识点"（D-Q3 的六号消费点）。
                knowledgeBaseAvailability = knowledgeBaseAvailability,
                // 4B A1：工具环生图（GENERATE_FIGURE）复用同一份模型凭证与 SSRF 防护通道；
                // 没配模型时整条链保持 null（工具回"不可用"，不静默假成功）。
                // 4E.1：统一转调共享 coordinator（policy/ledger/图像通道）。
                // A2：生成结果登记 canonical 行（幂等键派生的 id），同键重放不再出网付费。
                figureGenerator = modelConfigurationStore?.let { configurationStore ->
                    figureGenerationCoordinator?.let { coordinator ->
                        TutorFigureGeneratorFactory.create(
                            context = this,
                            database = database,
                            configurationStore = configurationStore,
                            networkRequestsAllowed = capabilities.networkRequestsAllowed,
                            coordinator = coordinator,
                        )
                    }
                },
            )
            captureRepository = CaptureWorkflowRepositoryFactory.create(
                context = this,
                database = database,
                // Clean redraw engine: reused by the save decision round once the
                // model classifies the committed photo as figure-bearing.
                // 4E.1：同一共享 coordinator；净化作业的持久领取尝试作为派发父操作。
                // 4E WG-06 T3：与详情页显式重绘共用同一个实例（同一凭证门/预算/审计）。
                cleanRedraw = cleanImageGenerator,
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
            // 6C-2 I1：这里的 `database` 同时也是 T5 的搜索索引重建端口
            // （`StudyDatabasePort` 继承 core:model 的 `KnowledgeSearchIndexRebuildPort`）。
            // 备份工厂拿到的就是这个实例；恢复完成后的「待重建」标记在**下一次数据库
            // 打开后**由下方协调器消费同一个实例，不另建第二份重建实现。
            backupRepository = BackupRepositoryFactory.create(
                context = this,
                database = database,
                // 6C-1：设置随包走。两个 DataStore 实例是启动时就装配好的那对（同一个文件
                // 不能再建第二个实例），这里只把读口/写口交给备份仓库。
                schedulingSettings = schedulingSettingsStore,
                reviewReminders = reviewReminderRepository,
                // KD-56：恢复在关闭 Room 连接池**之前**把"请先不要操作"交给 UI；不接这条，
                // 关库前的通知是 no-op，界面会继续撞已关闭的连接池（code 21）。
                onRestartRequired = ::requireDatabaseRestart,
                // KD-61：关库前对应用级 collector 做硬性 quiesce——取消整个 applicationScope
                // 并限时等子协程把最后一笔在途写跑完（collector 多数没接写门，且部分实现
                // 位于不可改的热区文件；见 BackgroundWriterQuiesce.kt 的失败指认）。此刻进程
                // 已注定重启，scope 终态是刻意的。
                backgroundQuiesce = {
                    val drained = quiesceApplicationScopeWriters(
                        scope = applicationScope,
                        timeoutMillis = BACKGROUND_WRITER_QUIESCE_TIMEOUT_MILLIS,
                    )
                    if (!drained) {
                        android.util.Log.w(
                            "SmartMistakeBook",
                            "Background writers not fully drained before database close " +
                                "(KD-61 residual); proceeding with restore",
                        )
                    }
                },
            )
            // 确认卡执行路径 ③（A4）：聊天附图 → 既有录入链路。放在批量和采集之后——它两者都用到。
            tutorAttachedImageIntake = TutorAttachedImageIntakeFactory.create(
                images = lobbyMessageImageIntake,
                capture = captureRepository,
                batchImports = batchImportRepository,
            )
            // 4E WG-08（T5）：上一进程在"operation/attempt 已置 RUNNING、终态还没落"时被杀，
            // 审计台账会永远显示"仍在生成"。启动时把早于本进程的僵尸行落成 FAILED：产物原样
            // 保留、诊断写"待核实"、不自动重派（审计端口结构上没有派发入口）。
            applicationScope.launch {
                runCatching {
                    FigureGenerationReconciler(database)
                        .reconcileStaleRunning(processStartedAtEpochMillis)
                }.onSuccess { report ->
                    if (report.failedOperations > 0 || report.failedAttempts > 0) {
                        android.util.Log.i(
                            "SmartMistakeBook",
                            "Reconciled ${report.failedOperations} figure operation(s) and " +
                                "${report.failedAttempts} attempt(s) stuck in RUNNING",
                        )
                    }
                }.onFailure { failure ->
                    android.util.Log.w(
                        "SmartMistakeBook",
                        "Figure generation reconcile failed",
                        failure,
                    )
                }
            }
            OrphanAssetGc.enqueue(this)
            // An import interrupted by a process kill is resumed without waiting
            // for the student to reopen the screen; the worker no-ops quickly when
            // nothing is PROCESSING.
            BatchImportDriver.enqueue(this)
            // AUD-09：上次运行里被进程杀死的净化作业（分类/重绘）没人收尾，题面会永远停在原图。
            // 启动时接着做完（PENDING/FAILED 且尝试次数未耗尽）；只接手"最后更新早于本进程启动"
            // 的行——本进程刚发起的作业不会被抢走双跑（那会重复付费）。没配模型时一行也不动
            // （出网门 fail-closed），留待下次启动。
            applicationScope.launch {
                runCatching {
                    captureRepository.resumePendingCleanRedraws(processStartedAtEpochMillis)
                }.onFailure { failure ->
                    android.util.Log.w(
                        "SmartMistakeBook",
                        "Clean redraw resume failed",
                        failure,
                    )
                }
            }
            // 4E WG-06（T3）：用户点过重绘、但上一进程在完成前被杀的行由保存期对账明确跳过
            // （两类作业不互相抢），必须在这里单独续跑：只接手带 `explicit-redraw-op:` 前缀、
            // PENDING/FAILED 且尝试额度未耗尽的显式行；启动本身不新建任何作业，出网门关着时
            // 一行也不动（fail-closed）。
            applicationScope.launch {
                runCatching {
                    explicitRedrawService.resumePending(processStartedAtEpochMillis)
                }.onSuccess { processed ->
                    if (processed > 0) {
                        android.util.Log.i(
                            "SmartMistakeBook",
                            "Resumed $processed explicit redraw task(s)",
                        )
                    }
                }.onFailure { failure ->
                    android.util.Log.w(
                        "SmartMistakeBook",
                        "Explicit redraw resume failed",
                        failure,
                    )
                }
            }
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
            // 6C-2 T5/I1（产品裁定 ③-a+c）：恢复侧把两张派生索引表**恒裁**，并把
            // 「哪些科需要重建」写进 `noBackupFilesDir` 的待重建标记。标记只能在数据库
            // **重新打开之后**消费——恢复那个进程已经关掉了 room3 连接池，本进程才能开库。
            // 没有标记时协调器立即返回；重建失败**不回滚**已恢复的数据库（索引只是派生
            // 缓存），标记保留到下一次启动；读时自愈仍是兜底。放后台，不拖慢 Ready。
            applicationScope.launch {
                try {
                    val rebuild = RestoreIndexRebuildCoordinator.rebuildPending(
                        context = this@SmartMistakeBookApplication,
                        database = database,
                    )
                    when (rebuild.status) {
                        RestoreIndexRebuildStatus.NOTHING_PENDING -> Unit
                        RestoreIndexRebuildStatus.REBUILT -> android.util.Log.i(
                            "SmartMistakeBook",
                            rebuild.message,
                        )
                        RestoreIndexRebuildStatus.PARTIAL_FAILURE -> android.util.Log.w(
                            "SmartMistakeBook",
                            rebuild.message,
                        )
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Throwable) {
                    // 协调器内部已按科兜住失败；这里只兜标记读写/列科目等意外，
                    // 保留标记（不删除），下一次启动再试。
                    android.util.Log.w(
                        "SmartMistakeBook",
                        "Search index rebuild after restore failed; pending marker kept",
                        failure,
                    )
                }
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
            // 丢失的提交回执（KD-39）：提交落库之后、写台账回执之前被杀进程，题已经进了错题本，
            // 台账却永远停在「待提交」。启动时按库内事实补写回执（幂等），不进复核页、不续做
            // 下一题也能追平；复核页装配与逐题续做各自还有同一笔读侧对账。单独一条协程：
            // 这两笔修复互相独立，一处失败不该把另一处一起跳过。
            applicationScope.launch {
                runCatching {
                    roomSplitImportRepository.reconcileCommittedQuestionDrafts(
                        System.currentTimeMillis(),
                    )
                }.onSuccess { caughtUp ->
                    if (caughtUp > 0) {
                        android.util.Log.i(
                            "SmartMistakeBook",
                            "Reconciled $caughtUp split import question(s) committed without a receipt",
                        )
                    }
                }.onFailure { failure ->
                    android.util.Log.w(
                        "SmartMistakeBook",
                        "Split import receipt reconciliation failed",
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
            } finally {
                // KD-32：释放必须覆盖**失败**路径。改前只有成功路径释放，
                // 安装一失败（如实测那次解析/调和 OOM）就把 ~100MB 的包对象图留在进程里——
                // 失败态 idle Dalvik Alloc 111,350 KB vs 快路径健康态 8,068 KB，随后一次
                // 只用 11.7MB Java 堆的批量导入会被这 100MB 挤到 OOM（PID 5016 的
                // 40 字节分配失败）。失败即整体放弃驻留对象图，只保留进度戳重试；
                // 下一次 install() 会重新解析，戳语义不变（进度行仍由 install() 最后写）。
                BundledKnowledgeBaseInstaller.releaseResidentPacks()
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
        if (restartReason.value != null) return
        applicationScope.launch {
            try {
                reviewReminderCoordinator.refresh()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // 系统恢复入口不能因暂时读不到偏好而使前台页面崩溃，下次恢复会再校准。
            }
        }
    }

    /**
     * L7：启动一次**单题**后台导出。入队后即可离开页面——渲染归 WorkManager，成果在
     * 「导出成果」与完成通知里。
     *
     * PRD-04 / T4：[format] 是原生详情页在格式 sheet 里选定的交付格式（缺省 PDF = 既有
     * 调用点行为不变）。格式必须随任务进 WorkManager——只在界面上选而不入队，学生就会
     * 「看到选了 Word、拿到 PDF」。
     */
    fun startMistakeExportSingle(
        key: MistakeRevisionKey,
        format: MistakeExportFormat = MistakeExportFormat.PDF,
    ) {
        enqueueMistakeExport(
            request = MistakeExportJobRequest.Single(
                exportId = newMistakeExportId(),
                key = key,
                format = format,
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
     *
     * 6B-1：[format] 是学生在导出 sheet 里选定的交付格式（缺省 PDF = 既有入口不变）。
     */
    fun startMistakeExportBatch(
        entryIds: List<String>,
        layout: MistakePdfLayout = MistakePdfLayout.DEFAULT,
        format: MistakeExportFormat = MistakeExportFormat.PDF,
    ) {
        val distinct = entryIds.distinct()
        if (distinct.isEmpty()) return
        enqueueMistakeExport(
            request = MistakeExportJobRequest.Batch(
                exportId = newMistakeExportId(),
                entryIds = distinct,
                layout = layout,
                format = format,
            ),
            kind = MistakeExportKind.BATCH,
        )
    }

    /**
     * 6B-2：选题后读题面、建可编辑卷面（纯本地读取，不派发任何模型任务）。
     *
     * 读取口与 worker 的 `readByEntryId` 同一口径（当前正式版）；不能导出的题被略过，
     * 一道都不剩时返回 null，由界面如实说明。
     */
    suspend fun buildMistakePaperEdit(
        entryIds: List<String>,
        layout: MistakePdfLayout,
    ): MistakePdfPaperEdit? = withContext(Dispatchers.Default) {
        MistakePdfPaperEdit.fromStates(entryIds, layout) { entryId ->
            mistakeDetailRepository.observe(entryId).first { it != MistakeDetailState.Loading }
        }
    }

    /**
     * 6B-2：入队一次**编辑后卷面**的导出。
     *
     * 草稿先落盘、再入队（worker 按 draft id 读回）；写失败时不入队，但落一条 FAILED 记录，
     * 让「导出成果」里有一句可读的失败原因，而不是一条什么都没有的点击。
     */
    fun startMistakeExportEdited(
        draft: MistakePaperDraft,
        format: MistakeExportFormat,
    ) {
        val exportId = newMistakeExportId()
        // 草稿文件名用跨平台安全字符（导出 id 里的 ':' 在 Windows JVM 测试里不是合法文件名）。
        val draftId = exportId.replace(':', '_')
        applicationScope.launch {
            try {
                mistakePaperDraftStore.write(draftId, draft)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                android.util.Log.e(
                    "SmartMistakeBook",
                    "Paper draft could not be written for $exportId",
                    failure,
                )
                val now = System.currentTimeMillis()
                runCatching {
                    mistakeExportRepository.recordStarted(
                        exportId = exportId,
                        kind = MistakeExportKind.BATCH,
                        atEpochMillis = now,
                    )
                }
                runCatching {
                    mistakeExportRepository.recordFailed(
                        exportId = exportId,
                        failureMessage = "这次编辑的卷面没有保存下来，请重新编辑后再导出。",
                        atEpochMillis = now,
                    )
                }
                return@launch
            }
            enqueueMistakeExport(
                request = MistakeExportJobRequest.Edited(
                    exportId = exportId,
                    draftId = draftId,
                    layout = draft.input.layout,
                    format = format,
                ),
                kind = MistakeExportKind.BATCH,
            )
        }
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
        if (restartReason.value != null) return
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
                } finally {
                    // 与启动协程同口径（KD-32）：成功与失败都释放驻留对象图。重试失败
                    // 若把它留在进程里，就是"又一次用 ~100MB 常驻换取一次失败"。
                    BundledKnowledgeBaseInstaller.releaseResidentPacks()
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
            DataStoreModelConfigurationStore(this, dataStoreScope)
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
            // 与工具环/净化共用同一个 coordinator；null = 没有配置模型，重生成不可用。
            coordinator = attachedImageCoordinator,
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
