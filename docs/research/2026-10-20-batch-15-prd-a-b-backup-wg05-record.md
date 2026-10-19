# 批次 15 记录（2026-10-20）：PRD A/B 批 + KD-56 恢复 P0 + 4E WG-05

> 记录员说明：本文件是批次 15（计划 `.zcode/plans/plan-2026-10-20-batch-15-prd-a-b-backup-wg05.md`）的收尾记录。
> 本轨只写记录/台账文档，未改代码、未运行 Gradle、未执行 `git add/commit/stash/checkout/reset`、未输出任何密钥或凭据。
> 记录时点：2026-10-20 02:33（终稿；工作树计数为该时点）。HEAD 仍为 `dfe231bf`，与 `origin/main` 同一提交（`0	0`）。
> **批次结论：未完成。** 工单给定的门禁结论是「单测门第 3 轮 exit=1、强门未通过、D1 设备轨阻塞」；本记录中的测试数字均来自
> 工作树中构建产出的 JUnit XML（执行者非记录员；记录员只读核对，未重跑）。代码/策略级通过不等于设备全链或真实质量通过，未跑项一律写 `UNVERIFIED`。

## 1. 范围与基线

### 1.1 依据

- 实施计划：`.zcode/plans/plan-2026-10-20-batch-15-prd-a-b-backup-wg05.md`（下称「计划」）。本批范围 = PRD-01（基线）、PRD-02、PRD-03、PRD-04、PRD-06 空态半边、
  PRD-07 前半（大库备份/恢复/写饥饿）、PRD-08；4E 只做 WG-05/KD-58~60；KD-56 必须关闭。**不做** PRD-05、PRD-09、6B-2 本地编辑入口、KD-47 UI 文案、5B/6C-2、
  UI-00~11、任何模型凭据播种（计划 §1 第 18-20 行）。
- 基线文档：`docs/research/2026-10-20-batch-15-baseline.md`（T0，已写；HEAD/origin、工作树归属、Room 67/请求 17、依赖版本、四栏与 open 缺陷）。
- 外部研究 R1（SQLite/Room 备份恢复）与 R2（答案键 provenance/纠错语义）结论见 §5；不自行给产品阈值。

### 1.2 基线事实与记录时点的变化

| 项 | 基线（T0 时点） | 记录时点（2026-10-20 02:33） |
| --- | --- | --- |
| HEAD / origin | `dfe231bf` / 同一提交，`0	0` | 未变；仍 `0	0` |
| 工作树 | 496 行 = 94 modified + 402 untracked | **655 行 = 218 modified + 437 untracked**（`git status --porcelain` 逐行计数） |
| Room schema | 67 | **68**（`StudyDatabase.kt:130`；迁移 `AnswerKeyCorrectionMigration_67_68.kt` 已注册） |
| 模型请求 schema | 17 | 17（未动） |
| 设备 | `adb devices` 空 | **`emulator-5554	device` 在线**（02:25 实测）；但本批未执行任何设备用例，见 §4 |
| 门禁 | 未跑 | 工单结论：单测门第 3 轮 `exit=1`；观察到的模块 XML 见 §3 |

说明：工作树增量包含本批 T1~T8/I1 的实现、测试与文档，也包含他线在飞改动；**没有任何本批代码被提交**。HEAD 树仍不可独立编译
（Root 依赖未跟踪的 `DatabaseRestartRequiredScreen.kt`，SecondaryScreens 依赖未跟踪的 `CapabilityFormViewModel.kt`；基线 §7 已证明，本批未修复）。

### 1.3 轨与最终状态

| 轨 | 内容 | 本记录状态 | 依据 |
| --- | --- | --- | --- |
| T0 | 基线登记 | 完成（文档） | `2026-10-20-batch-15-baseline.md` |
| T1 | KD-56 + KD-54 + KD-53 恢复侧（PRD-07 前半） | **部分**：代码与 JVM 生命周期门落地；KD-56/54/53 均未关闭 | §2.1、§3、§4 |
| T2 | PRD-02 / KD-43 答案键信任与定向纠错（schema 68） | **部分**：schema/账本/投影/写门/UI 代码级落地，JVM 用例有结果；instrumented/设备未跑，KD-43 保持 open | §2.2 |
| T3 | PRD-03 原题修订 | **代码级完成，设备未跑**；不能按计划验收宣称完成 | §2.3 |
| T4 | PRD-04 原生 PDF/DOCX 格式选择 | **部分**：格式 sheet 与 I1 接线落地；仪器化/设备未跑 | §2.4 |
| T5 | PRD-08 草稿竞态 | **代码级完成，JVM 8/0**；真实进程/设备未跑 | §2.5 |
| T6 | WG-05 / KD-58~60 | **部分**：KD-59/60 代码级关闭；KD-58 部分；设备/UI 用例未跑 | §2.6 |
| T7 | KD-57 分享重试与 Expired | **部分**：代码与 JVM 策略测试落地；instrumented/设备未跑，KD-57 保持 open | §2.7 |
| T8 | PRD-06 空态半边 | **部分**：四态与入口代码级落地，JVM 14/0；UI/设备未跑；能力快照半边顺延 | §2.8 |
| I1 | 集成（唯一可改 Root/Application） | **部分**：T2/T4/T6/T8/T1 接线落地；整包未编译 | §2.9 |
| D1 | 设备轨 | **阻塞**：工单结论强门未绿；记录时点设备在线但本批未执行设备用例 | §4 |
| R1/R2 | 外部研究 | 完成（研究结论，证据等级见 §5） | §5 |
| C1 | 收尾记录 | 本文件 + 四处台账增量 | §8 |

## 2. 逐轨交付

### 2.1 T1 备份恢复 P0 + 写饥饿（KD-56 / KD-54 / KD-53 恢复侧）

**交付（代码级）**

- 快照优先使用**独立于 Room 的 SQLite 连接**执行 `VACUUM INTO`：`AndroidBackupRepository.kt:198`；独立快照失败才落回旧路径
  `writeGate.runBackup { checkpoint + snapshotForBackup }`（`:205-212`）。`FrameworkVacuumIntoSnapshotEngine`（`:1355` 起）先试
  `OPEN_READONLY`、失败再试独立 `OPEN_READWRITE`，要求源库为 WAL，目标需 `quick_check=ok` 且 `user_version` 与源一致；framework SQLite < 3.27 时返回 false。
- 恢复顺序前移重活：`verifyPreparedDatabase(nextDb)` 在关库前对**将要晋升的同一份字节**跑全量 `integrity_check`（`:529`）；`admission.beginCommit()`
  排空/挡写（`:542`）；先 `restartNotifier` → 有界宽限（默认 `observerQuiesceMillis=750ms`，`:108`）→ `markDatabaseClosedForRestart` → `database.close()`
  （`:547-556`）；关库后的 rename/fsync/journal 段整体 `NonCancellable`（`:565-621`）；关库后只做 `verifySwappedDatabaseShape`（`:600`），不再调用注入端口。
- 写门终态：`BackupWriteGate.kt:294`（备份任务与恢复互斥）、`:334`（恢复）、`:358`（关库终态）、`:395`（提交段）；`withWriteAdmission` 在终态立即抛
  `BackupWriteDeferredException`（`:251`），不再落进已关闭的 `PassthroughConnectionPool`。
- 生产重启通知已接：`SmartMistakeBookApplication.kt:467` 给 `BackupRepositoryFactory.create` 传 `onRestartRequired = ::requireDatabaseRestart`
  （工厂参数见 `AndroidBackupRepository.kt:1530/1537`）。

**测试（已写；数字来自门禁 XML，记录员未重跑）**

- JVM `BackupWriteGateRestoreLifecycleTest` 11/0（02:17；准备期不阻塞写、交换段排队/排空/超时、备份与恢复互斥、关库终态立即失败等）。
- 仪器化（**未执行**）：`BackupRestoreLifecycleInstrumentedTest.kt:48` 在 `Dispatchers.Main` 对已 close 的 Room 调 `countMistakes()`，断言异常含
  “Connection pool is closed”；`:72` 断言关库前已通知重启、关库后端口零调用、新 Room 可读；`:153` 坏包在交换前失败且活库可读。
  `BackupLargeDatabaseScaleInstrumentedTest.kt:58` 以默认 `smbkBackupScaleMiB=365` 造库并断言快照期写门无失败/最坏 <5s、恢复后数据可读。
- 桌面 SQLite 探针（R1，仅核心语义）：`VACUUM INTO` 在只读连接成功、快照行数/字节一致；`query_only=1` 则报只读写失败。**不代表 Android framework 放行只读**。

**未达 / UNVERIFIED**

- KD-56 关闭条件（大库自建备份设备恢复不崩、数据可读）**未执行**：无设备用例结果、无强门绿灯。
- KD-54 关闭条件（设备对照无分钟级写饥饿）**未执行**；独立只读/RW 快照在 Android framework 的放行未实测；写门仍只覆盖备份仓库与采集仓库
  （`grep withWriteAdmission` 生产命中仅 `AndroidBackupRepository.kt:153`、`RoomCaptureWorkflowRepository.kt:154`），未接入的 DAO 直写仍可能在关库瞬间命中连接池。
- 旧回退的 `checkpointForBackup` 仍**消费并忽略** `PRAGMA wal_checkpoint(TRUNCATE)` 的结果行（`RoomBackupSupportStore.kt:15-19`），一致性依赖“备份期无写者”的假定。
- 365MiB 设备用例的磁盘/耗时、`randomblob`/`execSQL` 绑定行为均未验证；低磁盘/坏包/坏 hash/杀进程路径未在本轮复跑。

### 2.2 T2 答案键信任与定向纠错（PRD-02 / KD-43，schema 68）

**交付（代码级）**

- Schema 68：`learner_chat_evidence` 增 `corrects_evidence_id TEXT NULL` + 索引；`AnswerKeyCorrectionMigration_67_68.kt:20-38` 注册迁移；
  `StudyDatabase.kt:130` 版本 68；`schemas/.../68.json` 的实体含该列与 `index_learner_chat_evidence_corrects_evidence_id`（本记录用 Python 读 JSON 核对）。
- 账本新增 `EVIDENCE_CORRECTION`（`LearningState.kt:551/556`）：`VOID` 从有效证据集合排除目标，`REPLACE` 在账本序列中按最终有效替代重算并挂回原作答位置/时间；
  目标必须更早，悬空/自指在重放入口拒绝（`LearningProjector.kt:543/720/732/799-831`）。
- 版本纪律：`LearningCoreVersions.kt:13` `evidence-v6`、`:80` `projector-v14`、`:107` `ledger-v4`、`:122-125` `learning-core-v14`；登记见
  `docs/research/algorithm-version-ledger.md` §3.25（T2 已写，本批收尾补记见该节末尾）。
- 键信任两轴：`KnowledgeQuizItemStore.kt:136-140/157` 把模型题面写成 `verificationStatus=UNKNOWN`、`scoringMode=AUTO_VERIFIED`（仅表示本地可机械判分）；
  `KnowledgeQuizFeedbackWriter.kt` 对未核验/争议/迟到旧键只写 rejected 观察行，不新增数值掌握证据。
- 小测“答案有问题”入口：`KnowledgeReviewSessionScreen.kt:64/87/348-412`（中性入口/对话框/确认标签）；I1 在 `SmartMistakeBookRoot.kt:748-815`
  映射为 `AnswerKeyCorrectionCommand`（`voidTarget=true`）。
- 复核报告列出、当前树已修的代码（相关文件 mtime 01:29-02:09；非本记录所改）：`KnowledgeQuizFeedbackWriter.kt:443-452` 在“已作答但只落成 rejected 观察行”时回落到争议观察行，
  不再丢 `TARGET_NOT_FOUND`；`:626-647` 的 `resolveTarget` 只认原始 `KNOWLEDGE_QUIZ` 且 `corrects_evidence_id == null` 的行，避免第二次报告命中第一条
  `KEY_VOID`；`MasteryOverviewDao.kt:149` 聚合排除 `KEY_CORRECTION`/`KEY_VOID`；`expectedKeyVersion` 的“读版本→落账”由 companion `Mutex` 串行化
  （`KnowledgeQuizFeedbackWriter.kt:414-426` 附近 KDoc 与 `submitLocked`）。

**测试（已写；数字来自门禁 XML）**

- `ChatEvidenceCorrectionReplayTest` 9/0、`KnowledgeQuizFeedbackWriteTest` 5/0（core:data 02:17）；`AnswerKeyTrustPolicyTest` 5/0、`MistakeRevisionDiffTest` 6/0
  （core:domain 02:14）；`KernelWave0SchemaContractTest` 8/0（core:database 02:14）。
- `AnswerKeyCorrectionInstrumentedTest` 现有 11 个用例（含错键→改键、正确→撤销、改两次/撤销、重复请求、同 KC/其他 KC、纠错中被杀、旧键迟到、未核验只观察、
  中性争议、已作答未核验键争议、二次报告不命中第一条 VOID）——**未执行**。
- `FullMigrationMatrixInstrumentedTest` 的 67→68 保行/可写纠正用例**未执行**。

**未达 / UNVERIFIED / 残留**

- 无 instrumented/设备结果；KD-43 保持 open（不能把 JVM 重放用例当作真库/设备验收）。
- 读侧“最新替代键”`latestCorrectedKey` 按 `created_at_epoch_millis` 行序取（`KnowledgeQuizFeedbackWriter.kt:296-317` + `ChatEvidenceDao.kt:152-158`），
  投影按账本序列取；同毫秒/时钟回拨时可能分叉（低危，未修）。
- `expectedKeyVersion` 只有进程内互斥；生产唯一 UI 调用点不传该字段，实际并发键版本冲突不会被检测（低危）。
- 模型生成的 UNKNOWN 键当前只能“争议/暂停计分”，不能通过 UI 走 `REPLACE` 真正改键；旧 VERIFIED 行与“已被独立核验”仍不可区分，本批不批量回填/重判。
- KD-43 的另一半（同身份撞被拒行）未另立裁定：`KnowledgeEvidenceWriter.kt:170/270-279` 仍报 `BLOCKED_BY_REJECTED_ROW`，本批未改成覆盖/显式冲突。
- `68.json` 的 `identityHash` 在记录时点为 `607250e58442607e8d8a83d2471d2f67`，与 67 的 `9bf16ae8…` 已不同（文件 mtime 01:39；由 KSP 重写还是手工更新未核实）；`ExportedSchemaContractTest`
  仍只校验非空与 32 位十六进制，不能证明“没有复制旧 hash”。

### 2.3 T3 原题修订（PRD-03）

**交付（代码级）**

- 受控 `reviseProblem`（CAS + 持久幂等回执 + 显式冲突）：`MistakeDetailRepository.kt:245/271/301`；新 revision 追加、原图复制、旧 basis 的模型任务/重绘/关系失效、
  机器可判题改用新 practice unit；`MistakeDetailDao.kt:764/968/994/1023`；`RoomStudyDatabase.kt:44/1753` 类尾实现 `MistakeRevisionWritePort`，未动他线 KDoc。
- 数据层 `RoomMistakeDetailRepository.kt:144/481` 通过 `as? MistakeRevisionWritePort` 取写端口，取不到如实 `NotSupported`。
- UI：详情页“修改这道题”入口 `MistakeDetailRoute.kt:427`；新编辑 route/VM `MistakeRevisionEditRoute.kt:54`、`MistakeRevisionEditViewModel.kt:79/131/173`；
  保存前差异预览使用真实 `baselineAnswerSpecPresent`（`MistakeRevisionEditViewModel.kt:393`，复核发现的硬编码 false 已修）。
- 测试：`MistakeRevisionDiffTest` 6/0（02:14）；另写 `ProblemRevisionEditDatabaseInstrumentedTest`、`MistakeRevisionEditInstrumentedTest`、
  `MistakeRelationAndRevisionInstrumentedTest` 新用例——**均未执行**。

**未达 / UNVERIFIED**

- 计划验收「编辑→杀进程→重开→复习→导出版本一致」未跑；A/B 并发、旧生成晚到不污染新题、历史卷/历史作答可读均无设备证据。
- “历史作答仍可读”未找到按旧 revision/practice unit 读取历史 attempt 的明确 UI 路径（复核列为未复核）；本批只保证数据保留。
- 机器可判题编辑会新建 practice unit（无答案规格/记忆态），排程按新题处理；这是有意取舍，未做数据/设备验证。

### 2.4 T4 原生 PDF/DOCX 格式选择（PRD-04）

**交付（代码级）**

- 新 `MistakeExportFormatSheet.kt:57`（默认 PDF、可选 Word、可取消、testTag 齐全）；JVM `MistakeExportFormatSelectionTest` 3/0（02:14）。
- I1 已接：`SmartMistakeBookRoot.kt:178-192`（PendingExportTarget）、`:258-262`、`:611-615`（批量只登记目标）、`:1046`（单题目标）、`:1148-1176`（sheet 确认后入队并导航）；
  `SmartMistakeBookApplication.kt:657-668` 的 `startMistakeExportSingle(key, format)` 把 format 放进 `MistakeExportJobRequest.Single`。
- 格式身份链：`ExportPdfWorker.kt:330-353/403-414` 编解码；`MistakeExportJobRunner.kt:160-175` 按 format 分流，DOCX 无渲染器 fail-closed；缓存目录分离；
  记录格式身份 = 文件名扩展名（`MistakeExportFormat.kt:47-55`）。
- 既有 app 回归已同步：`MistakeExportBackgroundInstrumentedTest.kt:70-73/163-166` 现在先等 `mistake_export_format_sheet` 并点
  `mistake_export_format_confirm` 再等 `mistake_export_hub`（文件已是 ` M`，记录时点已改）。

**未达 / UNVERIFIED**

- `MistakeExportFormatSheetInstrumentedTest`、`MistakeExportHubInstrumentedTest` 与 app 回归**未执行**；整包未编译。
- 无设备链：单题/批量两种格式的保存、真实接收应用打开、无 Key/断网/401/429、ZIP 内 media/hash、取消/返回/进程中断均未验证。
- 分享失败分支中“分享方式打不开”无自动化用例；默认不含答案仅由既有版式保证。

### 2.5 T5 草稿竞态（PRD-08）

**交付（代码级）**

- 先写 8 个 JVM 反例/回归：`TutorConversationDraftRaceTest.kt:89/110/134/155/183/209/236`（旧读覆盖、旧读延迟、读失败、提交清稿吞新输入、迟到旧写、写失败重建、重建不外发）。
- `TutorConversationSavedState.kt:29/51/66-73/85` 持久化 `draftVersion`；`TutorConversationViewModel.kt:166-182` 唯一草稿写口与版本、
  `:833-842` 旧读守卫、`:848-859` 版本推进、`:874-896` 串行 CAS；发送命令接同一写口（`:293`）。
- 门禁 XML：`TutorConversationDraftRaceTest` 8/0（feature:tutor 02:14）。

**未达 / UNVERIFIED**

- 未做真实进程死亡/设备恢复；JVM 用同一 SavedStateHandle 构造第二个 VM 模拟，不等同 Android 进程恢复。
- 草稿持久写失败没有学生可见提示（UiState/界面不在本轨所有权内）；多实例并发/强杀（无 SavedState）仍可能回读旧草稿。

### 2.6 T6 旧 attachedImages 收口 + 预留防线（WG-05 / KD-58/59/60）

**交付（代码级）**

- 历史 `attachedImages` 只读本地资产：`AttachedImageGenerator.kt:79-85`；缓存未命中/损坏/缺源/读失败给可读原因（`:194-227`）；显式重生成绑定原消息
  `parentRequestId` 并走共享 coordinator/派发账本（`:96-192`）。
- 旧工厂 `AttachedImageGeneratorFactory.kt:32-49` 改为 cache-only；`:71-104` 提供带 coordinator 的生产 generator；`AttachedImageRuntimeResolver`
  （`:119-139`）同时实现旧 `String?` 函数与 `AttachedImageResolutionPort`。生产装配：`SmartMistakeBookApplication.kt:895-907` 返回该对象；
  Root `:1009` 与 Destinations `:403` 传给路由；`TutorChatConversation.kt:328-365` 做端口能力检测，卡片拿真实原因与“重新生成”。
  **因此复核报告所说“生产按钮未接线”在当前树已不成立**（当前树已接线；`AttachedImageGeneratorFactory.kt` mtime 02:09）。
- `ImageGenerationChannel.kt:72/83` 两个旧无预留重载 `@Deprecated(level=ERROR)`；`FigureGenerationCoordinator.kt:41` binding 构造器 `internal`、
  `:77` `fromReservation`、`:94` `forTest` + `RequiresOptIn(ERROR)`；`ImageDispatchReservationGuardTest` 6/0。
- `FigureGenerationBudgets.kt:18-24` 只声明“上限/共享/未预读余量=null”；`RoomFigureDispatchLedger.kt:65-91` 拒绝消息带真实已派发次数、上限、共享与“未出网”；
  `FigurePromptAssembler.kt:164-180` 分列文本；`FigureGenerationBudgetDeclarationTest` 4/0。
- `AttachedImageCard.kt:59-145` 未就绪/原因/“重新生成”文案与显式动作；`AttachedImageCardTest` 8 例（含缓存未命中原因/动作、损坏不盲目重生成）已写，
  **最后一次运行仍是 2026-10-13 的 5 例，未重跑**。

**未达 / UNVERIFIED**

- KD-58 只到“部分达成”：core:ui 8 例未跑、无设备点击证据；KD-58 保持 open。
- KD-59/60 代码级关闭（见 `docs/known-defects.md`），均只到 JVM/静态证据。
- 真实 Provider 质量、真实 HTTP 0 调用、设备 UI 未验证。

### 2.7 T7 分享重试与 Expired（KD-57）

**交付（代码级）**

- 授权只在成功/明确放弃时释放：`CaptureShareIntake.kt:49-77` 成功才 `releaseReadGrants`，`:84-86` 明确放弃入口；可重试失败保留授权。
- `AUTHORIZATION_EXPIRED` 独立理由（`CaptureSharePolicy.kt:69/186/194`）；10 分钟接收窗口与纯函数（`:204/213-219`）；
  `CaptureShareIntakeRoute.kt:91-100` 由 `request==null || expiredByResult || 超时` 派生 Expired，`:105-112` 到点主动翻过期，`:116-154` 每次尝试前校验，
  `:159-167` 仅手动录入/关闭/返回释放授权；系统返回键已接 `BackHandler { abandonShare(onDismiss) }`（`:180`，复核发现已修）。
- JVM `CaptureShareAuthorizationPolicyTest` 6/0（feature:capture 02:14）；`SharedCaptureEntryInstrumentedTest` 全链用例已写（`:148`）。

**未达 / UNVERIFIED**

- `CaptureShareAuthorizationInstrumentedTest`（4 例）、`CaptureShareIntakeRouteInstrumentedTest`（4 例）与全链仪器化**未执行**；真实外部应用分享/授权失效设备项未跑。
- 10 分钟窗口是 T7 自选的产品值（计划未给数字），需产品确认；`importPdf` 对 `createPdfImport` 的 `SecurityException` 仍统一映射 `IMPORT_FAILED`（低危）。
- KD-57 保持 open（“再试一次真的成功 + Expired 可达”尚无可执行设备证据）。

### 2.8 T8 首装/无到期/失败待处理空态（PRD-06 空态半边）

**交付（代码级）**

- `ReviewRoute.kt:97-158` 四态渲染；`:235-296` 纯策略 `resolveReviewSurfaceState`（NORMAL/EMPTY_LIBRARY/NO_DUE_QUESTIONS/PENDING_FAILED_TASKS/UNKNOWN）与文案；
  空库 CTA `review_capture_button`、待处理继续 `review_continue_pending_tasks`；题数未知不谎称空库。
- `LibraryRoute.kt:182-196` 待处理入口 `library_pending_tasks`；`:661-669` 首装“没有照片也可手动建题”提示。
- I1 已接：`SmartMistakeBookRoot.kt:265-277` 统计 pending 三态；`:413-424`/`:599-602` 传 `savedQuestionCount`、`pendingFailedTaskCount`、`onCapture`、
  `onContinuePendingTasks`。
- JVM `ReviewRoutePolicyTest` 11/0、`LibraryPendingTasksEntryTest` 3/0（02:13/02:14）；`LibraryPendingTasksEntryInstrumentedTest` 已写未跑。
- 既有 `RootTutorFailClosedInstrumentedTest.kt:269` 已改为等待“还没有可复习的题”（文件 ` M`，复核发现已修）。

**未达 / UNVERIFIED**

- 无 Compose UI/设备测试结果；TalkBack/大字号未验；PRD-06 能力快照半边（TutorLocalAction/native schema/Route B 能力广告）按计划顺延下一批，本批未做。

### 2.9 I1 集成

**交付（代码级）**

- T2 纠错入口、T4 格式 sheet（单题/批量）、T6 attachedImages 能力端口、T8 空态/待处理计数、T1 恢复关库前重启通知均已接（见各轨）。
- 两处既有 app instrumented 用例已同步（导出先过格式 sheet、Review 空库新文案）；复核报告所指“未同步”在当前树已不成立。
- Root/Application 仍受本批铁律保护，只由 I1 改；本记录只读核对，未改。

**未达 / UNVERIFIED**

- 未运行 `:app:compileLocalFirstDebug`/`assembleLocalFirstDebug`/androidTest 编译；整包是否可编译未知。
- Root 对 T2 的映射、T2 中性争议的 `conversation_id` 合成值、Storage 取消与重启页交互均无设备验证。
- **提交风险**：9 个被生产代码引用的新文件仍是 `??`（见 §8）；漏提交任一将编译失败。

## 3. 门禁原始结论

### 3.1 工单结论（本记录采用）

- **单测门第 3 轮 `exit=1`**（工单原文）；强门未通过；D1 设备轨因此阻塞。
- 计划 §3 的单测命令为 11 模块 + `--continue`（`core:model/domain/data/database/export/ui`、`feature:review/library/capture/tutor`、`app:testLocalFirstDebugUnitTest`）；
  强门为 6 个 androidTest 编译目标 + `:app:assembleLocalFirstDebug`。本轮未重跑。

### 3.2 记录时点观察到的模块 XML（非本记录执行）

| 模块 | tests / failures / errors | XML 时点 | 备注 |
| --- | --- | --- | --- |
| core:model | 461 / 0 / 0 | 02:13 | 43 个 XML |
| core:domain | 597 / 0 / 0 | 02:14 | 78 个 XML，含 `AnswerKeyTrustPolicyTest` 5/0、`MistakeRevisionDiffTest` 6/0 |
| core:data | 905 / 0 / 0 | 02:17 | 115 个 XML，含 T2/T6/T1 新用例（9/0、12/0、3/0、6/0、4/0、11/0） |
| core:database | 198 / 0 / 0 | 02:14 | 含 `KernelWave0SchemaContractTest` 8/0 |
| core:export | 132 / 0 / 0 | 02:13 | 13 个 XML |
| core:ui | 38 / 0 / 0 | **10-13 21:24（旧）** | 7 个 XML；不覆盖 T6 新增 8 例 |
| feature:review | 56 / 0 / 0 | 02:13 | 含 `ReviewRoutePolicyTest` 11/0 |
| feature:library | 47 / 0 / 0 | 02:14 | 含 `LibraryPendingTasksEntryTest` 3/0、`MistakeExportFormatSelectionTest` 3/0 |
| feature:capture | 148 / 0 / 0 | 02:14 | 含 `CaptureShareAuthorizationPolicyTest` 6/0 |
| feature:tutor | 227 / 0 / 0 | 02:14 | 含 `TutorConversationDraftRaceTest` 8/0 |
| app | 108 / 0 / 0 | **10-18 16:23（旧）** | 26 个 XML；不覆盖 T4 新用例 |

**解读**：02:13-02:17 确实有一轮覆盖了 9 个模块且全 0 失败；但 `core:ui` 与 `app` 没有当前结果，记录时点仍有一个 `java.exe`（约 3.8GB）在跑，
因此**不能把这轮观察写成“单测门通过”**。工单的 `exit=1` 仍是本批门禁结论。复核报告曾写“core:data 只剩 00:45 的单类 XML”，那是更早的快照；
当前 core:data 已有 02:17 的 115 个 XML，但这不改变官方门禁结论。

### 3.3 已知红与归因

- 计划 §3 点名照录：`GoldenRetrievalInstrumentedTest`/`GoldenRetrievalJvmTest`（KB 在飞）、SECTION facet 边际门（环境）、
  `Stage1LexicalLabTest`/`Stage2LexicalScoresExportTest`（KB 在飞：章映射与金标脱节 / 回读 MISS 46 vs 42；本批不改 `knowledge-production/*`）。
- 本轮门禁失败的具体用例**未能从工单材料归因**（第 3 轮 exit=1，未给失败类名）；记录时点观察到的 9 模块 XML 全 0 失败，但 core:ui/app 未覆盖，
  不能排除失败在这两个模块或 Gradle 基础设施。

## 4. 设备结论

- **D1 工单结论：阻塞（强门未通过），设备轨未执行。** 本批没有任何设备全链结果、截图或 logcat。
- 记录时点 `C:/Android/Sdk/platform-tools/adb.exe devices` 输出：

  ```
  List of devices attached
  emulator-5554	device
  ```

  设备在线是记录时点的环境变化，**不能倒推 D1 已执行**；本记录未安装/启动 App、未跑仪器化、未做 UI 点击。
- 计划 D1 必跑项逐条状态：
  1. KD-56 ≥365MB 自建包 → 校验 → 恢复 → 重启 → 数据可读：`UNVERIFIED`。
  2. KD-54 备份快照期间并发写无分钟级 `unable to grant`：`UNVERIFIED`。
  3. PRD-04 单题/批量两格式保存/分享、真实接收应用打开：`UNVERIFIED`。
  4. KD-57 授权有效“再试一次”成功、失效走 Expired：`UNVERIFIED`。
  5. PRD-03 编辑→杀进程→重开→复习→导出版本一致：`UNVERIFIED`。
  6. PRD-02 纠错/撤销/重放 instrumented、键信任门：`UNVERIFIED`。
  7. 回归（A8 手动题、分享入口、备份校验）：`UNVERIFIED`（历史 PASS 只作历史证据）。
- 模型轨：凭据已随数据删除，本批不播种密钥；真实 Provider 成功率/时延/成本/数学正确性整块 `UNVERIFIED`。

## 5. 外部研究结论与证据等级

### 5.1 R1：SQLite/Room 3 备份恢复

**结论（多来源一致）**

- SQLite 3.27.0（2019-02-07）新增 `VACUUM INTO`；官方称输出是源库的一致性快照，目标文件必须不存在或为空；`VACUUM`（不含 `INTO`）才是写操作。
  3.28.0 起 `VACUUM INTO` 支持只读源连接（源码在 ATTACH 目标前临时清 `SQLITE_OPEN_READONLY`，对源库只开读事务）。
- WAL 语义：读不阻塞写、写不阻塞读；但长读事务会阻止 checkpoint 推进/截断，`TRUNCATE`/`RESTART` checkpoint 可能阻塞读者。独立快照仍有 WAL 增长与 I/O 竞争成本。
- Android framework SQLite 版本：API 29/3.22.0、API 30/3.28.0、API 31/33/3.32.2、API 34/3.39.2；framework `VACUUM INTO`（含只读支持）需 API 30+，
  项目 `minSdk 23` 必须回退或自带新 SQLite。
- Android `SQLiteDatabase(OPEN_READONLY)` 对 `VACUUM INTO` 的语句守卫存在（`sqlite3_stmt_readonly=0`），真机是否放行**未实测**（R1 标为待验证）。
- Room 3 生产路径 `AndroidSQLiteDriver`（`hasConnectionPool=true`）→ `PassthroughConnectionPool`：`useConnection` 不使用 `isReadOnly`，
  `withRawConnection(isReadOnly=true)` 仍是同一条可写连接；KD-54 的写饥饿因此不是“独立只读连接”能自动消除的。
- Room 3 `close()` 是终态：`PassthroughConnectionPool.close()` 置 `isClosed=true`，之后 `useConnection` 抛 `SQLITE_MISUSE (21)` “Connection pool is closed”，没有 reopen。
  推荐流程：先停/取消所有 DB 消费者与写者，再 close、删 `-wal/-shm`、原子换文件；随后杀进程或换新 `RoomDatabase` 并替换全部注入；下次启动由 startup recovery 校验并打开新库。
- 低 API 回退：`checkpoint + 主库字节拷贝` 只有在拷贝期无写者提交时才一致；仓库当前忽略 `wal_checkpoint` 结果行。更稳的工程路线是自带新 SQLite
  （`androidx.sqlite:sqlite-bundled` 2.7.0 的 `BundledSQLiteDriver`，AAR 内 SQLite 3.50.1，`hasConnectionPool=false`）或 native/bundled SQLite 的在线备份 API。
- 可复用资料按维护时间/许可证/Room 3 兼容性选：SQLite 官方文档与 `src/vacuum.c`、Room3 3.0.0 源/POM、androidx/androidx（Apache-2.0）、sqlite-bundled 2.7.0；
  `sqlcipher-android`/`requery/sqlite-android` 虽活跃，但只文档覆盖 Room 2 集成，Room 3 需额外 `SQLiteDriver` 适配，不能当 drop-in。

**证据等级**

- 官方文档/源码多来源一致：`VACUUM INTO` 语义、只读支持、WAL、Room 3 close 终态、Android SQLite 版本。
- 桌面探针（SQLite 3.50.4）只证明核心语义；Android framework `OPEN_READONLY` 放行与设备侧只读 `VACUUM INTO` 为 `UNVERIFIED`。
- 本轮未改仓库代码、未跑 Gradle/仪器化。

**对本批的映射**

- T1 的“独立连接优先（只读→独立 RW）、WAL 门、quick_check/user_version 复核、失败回退写门”与 R1 推荐一致；但未设备验证，且写门覆盖仍不完整。
- KD-56 的根因判断（`PassthroughConnectionPool.close()` 永久终态）与 R1 一致；当前代码把通知/终态前移，方向正确，设备证据仍缺。

### 5.2 R2：答案键 provenance 与纠错建模

**结论（规范/项目多来源；单一来源项已标）**

- QTI 3.0.1 的 `qti-correct-response` 是**评分声明**，不是“键已验证”的证明；QTI ASI/Metadata 没有 `verified` 字段，`lifecycle.status` 是生命周期而非正确性核验。
  QTI 对非自适应 item 只报告第一次 response processing 的 outcomes，不定义“改键后重放/重算历史”的语义。
- 主流 LMS 把当前键当评分权威、改键走显式 regrade（Canvas 的选项与 attempt history；页面需登录，**单一来源待验证**）。
  大规模测评把键核验放在事后独立流程（Georgia Milestones 技术报告 §6.5.1 用 distractor point-biserial > 0.05 标 possible key error，再由人工复核；
  PDF 在本环境 SSL 失败，**单一来源待验证**）。
- 补偿事件（event sourcing）：事件不可变、append-only，撤销/更正只能追加反转或纠正旧效果的新事件；回放时原事件和补偿都参与重建，净效果取决于投影是否应用补偿。
- 替代/void（xAPI）：statement 不可变，voiding statement 用 `StatementRef` 指向原 statement；默认查询不返回被 void 的 statement，`voidingStatementId` 可显式取，
  voiding statement 自身不能被 void。版本化（W3C PROV-O）：`wasRevisionOf`/`invalidatedAtTime`/`wasInvalidatedBy`；evidence 固定到 key version 才能确定性回放。
- OATutor 主仓库的键来自人工 curated JSON；`checkAnswer` 的布尔值直接进 BKT，没有“键已验证”闸；OATutor-LLM-Gen 只是第三方 fork（0 star），LLM 只做短答反馈，不写掌握度。
  PLOS ONE 2024 的 ChatGPT 只生成 hint（不是键），32% 未过三点检查，自一致性把代数错误降到接近 0%，作者仍要求人工监督。
- 本地原实现把“本地可机械判分”写成 `verificationStatus=VERIFIED`、`AUTO_VERIFIED`；本批 T2 已改为模型题面 `UNKNOWN`（只表示本地可判），与 R2 的“本地比较≠键已验证”一致。

**三种纠错语义（不替产品选，本批采用“补偿事件 + 版本化键”组合）**

1. **补偿事件**：保留错判并追加反向/纠正事件；投影必须把补偿应用到掌握度/排期等派生状态，而不只是写一条事件。
2. **替代/void**：追加 `KeyVoided/KeySuperseded`，默认投影排除旧键；下游掌握度/统计不会自动回滚，必须重算；审计仍可取回。
3. **版本化键**：新 key version + evidence pin version + 显式 regrade；最利于确定性回放，但要求证据能定位题面 id + key version。

本批 T2 的 `EVIDENCE_CORRECTION`（VOID/REPLACE）+ 账本序解析属于 1/3 的组合；AUD-11 报告指出的“证据行没有题面 id”在本批由 `corrects_evidence_id` 与
`itemId+choice` 后缀补上，但读侧最新键排序与投影排序仍可能分叉（见 §2.2）。

**证据等级**

- QTI/PROV-O/xAPI/OATutor 仓库/论文为官方或多来源一致；Canvas/Georgia/Moodle 为单一来源或未直读（Moodle docs 403），按待验证处理。
- “OATutor 若把模型键塞进 `stepAnswer`，BKT 会照写”是代码路径推断，未执行注入实验。

## 6. 独立核对 / 对抗复核发现与处置

> 说明：复核报告列出的一部分问题在当前树已被修复（相关文件 mtime 01:29-02:09，mtimes 可证）；下表的“当前处置”以**记录时点源码**为准；
> “已修”只表示代码已改，不表示 instrumented/设备已跑。所有行均为读码/XML 级证据，未执行 Gradle/设备。

| # | 发现（严重度） | 当前处置（记录时点） | 证据 |
| ---: | --- | --- | --- |
| 1 | 单测门 exit=1 无法归因，core:data 结果缺失；最后一轮后有改动（高） | **部分更新**：02:13-02:17 有一轮 9 模块全 0 失败；`core:ui`/`app` 仍为旧结果，门禁结论仍 exit=1；改动（01:29-02:09）早于 02:13，core 模块已覆盖，ui/app 未覆盖 | `ls`/XML 聚合见 §3.2；`stat` mtime |
| 2 | KD-56 无设备/强门证据（高） | **仍存在**：代码顺序前移、JVM 11/0；无设备/强门；750ms 宽限与非门控 DAO 写者仍在 | §2.1；`AndroidBackupRepository.kt:547-556` |
| 3 | KD-54 无设备对照、framework 只读 `VACUUM INTO` 未实测（中） | **仍存在**：独立快照引擎代码在树；无设备；写门只覆盖两仓库；回退忽略 checkpoint 结果 | §2.1；`RoomBackupSupportStore.kt:15-19`；`grep withWriteAdmission` |
| 4 | UNKNOWN 键作答后报告“答案有问题”返回 `TARGET_NOT_FOUND`（高） | **当前源码已修**：`KnowledgeQuizFeedbackWriter.kt:443-452` 回落到争议观察行；instrumented 用例 `:448` 已写**未跑** | 读码；`AnswerKeyCorrectionInstrumentedTest.kt:448` |
| 5 | 第二次不同理由报告命中第一条 `KEY_VOID`，级联撤销第一条更正（中） | **当前源码已修**：`resolveTarget` 只认原始 `KNOWLEDGE_QUIZ` 且 `corrects_evidence_id == null`（`:626-647`）；用例 `:491` 已写**未跑** | 读码；`AnswerKeyCorrectionInstrumentedTest.kt:491` |
| 6 | `68.json` 的 identityHash 与 67 相同（中） | **记录时点不再复现**：68=`607250e5…`、67=`9bf16ae8…`；schema 列/索引存在；`ExportedSchemaContractTest` 仍只校验格式，不能防错复制 | Python 读两份 JSON；`ExportedSchemaContractTest.kt:33-40` |
| 7 | `expectedKeyVersion` CAS 是两次独立事务（中） | **进程内已修**：companion `Mutex` 串行“读版本→落账”；生产 UI 不传该字段，跨进程未覆盖，残留低危 | `KnowledgeQuizFeedbackWriter.kt:414-426` KDoc/submitLocked |
| 8 | “增量 vs 全量逐位一致”用例实为 full-vs-full（低） | **仍存在**：`ProjectionTransactionDao.kt:634-647` 对纠正强制 `FULL_REPLAY_REQUIRED`；`AnswerKeyCorrectionInstrumentedTest.kt:81-93` 比较全量重放与持久快照；等价性由结构强制，非该用例证明；用例未跑 | 读码 |
| 9 | 既有 app instrumented 未随格式 sheet/空态文案更新（高） | **已修**：`MistakeExportBackgroundInstrumentedTest.kt:70-73/163-166` 先点 `mistake_export_format_confirm`；`RootTutorFailClosedInstrumentedTest.kt:269` 等“还没有可复习的题”；两文件均 ` M` | 读码 + `git status` |
| 10 | 9 个被引用的新生产文件仍未跟踪（高） | **仍存在**：T2 迁移、schema 68、T3 diff/port/编辑 route/VM、T4 sheet、T6 AttachedImageResolution/FigureGenerationBudgets；漏提交即编译失败 | `git status --porcelain` 见 §8 |
| 11 | T6 生产“重新生成”未接线（高） | **已修**：`AttachedImageRuntimeResolver` + Application `attachedImageResolver` + Root/Destinations 透传 + TutorChatConversation 端口检测；JVM 12/0、3/0；core:ui/设备未跑 | `AttachedImageGeneratorFactory.kt:119-139`；`SmartMistakeBookApplication.kt:895-907`；`TutorChatConversation.kt:328-365` |
| 12 | 分享页系统返回不释放读授权（中） | **已修**：`CaptureShareIntakeRoute.kt:180` `BackHandler { abandonShare(onDismiss) }`；instrumented/设备未跑 | 读码 |
| 13 | T3 保存前不显示答案被移除（中） | **已修**：`MistakeRevisionEditViewModel.kt:393` 使用 `baselineAnswerSpecPresent`（非硬编码 false）；设备未跑 | 读码 |
| 14 | 设置恢复 WRITE_FAILED 仍说“本机设置保持原样”（中） | **仍存在**：`SecondaryScreens.kt:735` 原串未改；文件属他线在飞，本批未动 | 读码；`docs/known-defects.md` KD-47 |
| 15 | 掌握度聚合把纠正/撤销行算作接受证据（中） | **已修**：`MasteryOverviewDao.kt:149` 排除 `KEY_CORRECTION`/`KEY_VOID`；本记录未读到该修复的专门用例 | 读码 |
| 16 | 生产 resolver 把 DAMAGED/缺源/读失败折叠成 NOT_GENERATED（中） | **生产路径已修**：Application 返回端口对象，`TutorChatConversation` 用 `resolve` 拿真实原因；普通 lambda 回退仍折叠，但不再是生产路径 | 读码 |
| 17 | 进程在成包/校验中途被杀，`backup-snapshot-*` 无启动清理（低） | **仍存在**：`RestoreRecoveryCoordinator.cleanupOrphans` 只删 `restore-*`/`restore-rollback-*` 与 30 天隔离区，不覆盖 `backup-snapshot-*`/`cache/backup-snapshots` | `RestoreRecoveryCoordinator.kt:357-373` |
| 18 | “历史作答仍可读”无界面读路径证据（中，未复核） | **UNVERIFIED**：详情/历史视图只读 revision 题面；未找到按旧 revision/practice unit 读历史 attempt 的明确 UI 路径 | 复核报告；本批未跑 |
| 19 | T3/T7/T8 自评“完成”高于验收（中） | **已纠正**：本记录将三者记为“代码级完成/部分，设备未跑”；只有 T5 的 JVM 8/0 是当前执行证据 | §1.3、§2 |
| 20 | 门禁脚本曾不含 app/core:ui 两模块（中） | **已修**：`.zcode/workflow-drafts/...ts:376-389` 的 `UNIT_ARGS` 已含 `:core:ui:testDebugUnitTest` 与 `:app:testLocalFirstDebugUnitTest` | 读脚本 |

**复核后仍未处置的实质风险**：KD-56/53/54 无设备；T2 的 instrumented/读侧排序/CAS 生产未用；T4/T7/T8 设备；T6 UI/设备；未跟踪文件；KD-47 文案；
以及“单测门 exit=1”本身未修复。以上不能由本轮任何“代码级/模块级”证据抵消。

## 7. 遗留与下一批

1. **P0 KD-56**：必须拿到大库（≥365MB）设备恢复不崩、数据可读的证据；在此之前保持 open。关库前 750ms 只是启发式，非门控 DAO 直写仍需 quiesce 或全部接入写门。
2. **门禁**：单测门第 3 轮 exit=1 未解决；强门未绿。下一轮需在 core:ui/app 也有当前结果后再谈“门禁通过”。
3. **D1**：设备已在线（02:25），但 KD-56/54/57、PRD-02/03/04、回归均未执行；按计划设备协议用 `assembleDebug`+`adb install -r`+`am instrument`，禁止 `connectedLocalFirstDebugAndroidTest`。
4. **提交面**：9 个新生产文件未跟踪；另有 T2/T3/T4/T6/T7/T8 的新测试文件未跟踪。主会话必须按显式清单逐文件纳入，不能只提交 Root 引用的 sheet。
5. **T2 残留**：instrumented 11 例未跑；读侧最新替代键按 `created_at` 与投影账本序可能分叉；`expectedKeyVersion` 生产不传；UNKNOWN 键只能争议暂停、不能 UI 改键；旧 VERIFIED 不回填。
6. **T3**：设备“编辑→杀→复习→导出一致”未跑；历史 attempt 的 UI 读路径未找到；新 practice unit 的排程表现未验。
7. **T4**：仪器化/设备两格式交付未跑；分享失败分支覆盖不全。
8. **T6**：core:ui 8 例未跑、设备未验；KD-58 仍 open；KD-59/60 仅代码级关闭。
9. **T7**：instrumented/设备未跑；10 分钟窗口待产品确认；KD-57 仍 open。
10. **T8**：UI/设备未跑；PRD-06 能力快照半边顺延下一批。
11. **KD-47**：只剩 `SecondaryScreens.kt:735` 用户文案；该文件属他线，需其 owner 或下一批处理。
12. **后续批次**：按计划 A→B→C，下一批 PRD-05（无模型三键与 undo）；4E 下一波 WG-06/07/08 使用 schema 69（本批 68 由 T2 独占）。
13. **真实质量 / 模型轨**：凭据已删，本批不播种；Provider 质量、图义提示词 v1 接入、PRD-09 均 `UNVERIFIED`，不得把工程绿灯当真实质量。

## 8. 提交 / 推送占位

- **工作流自身未执行 git 写操作**；主会话收尾按显式清单分两笔提交。
- **代码/测试提交 `032fce0e`（99 文件）**：22,976 insertions / 537 deletions；包含 9 个新生产文件、全部新测试与 schema 68。
- **文档提交**：本记录与 `known-defects.md`/主线/台账/决策台账 5 个文件随后提交（哈希见本节末尾）。
- 9 个新生产文件已全部纳入；列表保留如下：
  1. `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/AnswerKeyCorrectionMigration_67_68.kt`
  2. `core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/68.json`
  3. `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/port/MistakeRevisionWritePort.kt`
  4. `core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/MistakeRevisionDiff.kt`
  5. `core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/AttachedImageResolution.kt`
  6. `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/model/FigureGenerationBudgets.kt`
  7. `feature/library/src/main/kotlin/com/tingyun/smartmistakebook/feature/library/MistakeExportFormatSheet.kt`
  8. `feature/library/src/main/kotlin/com/tingyun/smartmistakebook/feature/library/MistakeRevisionEditRoute.kt`
  9. `feature/library/src/main/kotlin/com/tingyun/smartmistakebook/feature/library/MistakeRevisionEditViewModel.kt`
- 本记录新增/修改的文档（同样未提交）：本文件、`docs/known-defects.md`、`docs/REFACTOR-MASTER-LINE.md`、
  `docs/research/algorithm-version-ledger.md`、`docs/agent-first-refactor-decisions-2026-09-23.md`。
- 他线未跟踪的 `CapabilityFormViewModel.kt`、`DatabaseRestartRequiredScreen.kt` 仍使 HEAD 树不可独立编译；本批不代提交，也不修 HEAD。
- **代码提交 `032fce0e`（99 文件）**；**文档提交 `aa499d88`（6 文件）**；**推送结果（2026-10-20）**：`dfe231bf..aa499d88` fast-forward 到 `origin/main`，`git ls-remote` 复核 = `aa499d88`；工作树剩余为其他线在飞文件，本批不代提交。

## 9. 主会话收尾实跑（2026-10-20，覆盖 §3 的旧门禁结论）

> 工作流在复核修复后已达到单测门第 2 轮 exit=0，但记录员按工单旧结论写成“门禁红”。主会话在同一工作树重新实跑，结果如下；以本节为准。

### 9.1 门禁

- **单测门（11 模块，`--continue`）exit=0**：core:model 461/0、core:domain 597/0、core:data 905/0、core:database 198/0、core:export 132/0、core:ui 41/0、feature:review 56/0、feature:library 47/0、feature:capture 148/0、feature:tutor 227/0、app:testLocalFirstDebug 58/0；合计 2,870 tests / 0 failures。
- **强门 exit=0**：六个 androidTest 编译目标 + `:app:assembleLocalFirstDebug`。首轮发现并修复 4 处测试侧编译缺口：
  1. `LegacyProjectionUpgradeDrillInstrumentedTest.eventKindOf` 缺 `is EvidenceCorrection` 分支；
  2. `StudyDatabaseInstrumentedTest.projectedPresentationStates` 缺同分支；
  3. `StudyDatabaseInstrumentedTest.toReceipt` 缺同分支；
  4. `CaptureShareIntakeRouteInstrumentedTest` 误 import 成员函数 `assertDoesNotExist`（该函数是 `SemanticsNodeInteraction` 成员，不需要 import）。
- 修复后复跑强门 exit=0；`68.json` 已由 KSP 重写为 `identityHash=607250e58442607e8d8a83d2471d2f67`（与 67 的 `9bf16ae8…` 不同），此前“hash 从 67 复制”的发现不再复现。

### 9.2 设备（`assembleDebugAndroidTest` + `adb install -r` + `am instrument`；禁用 connectedAndroidTest）

| 用例 | 结果 | 证据 |
| --- | --- | --- |
| `BackupLargeDatabaseScaleInstrumentedTest`（365MiB） | **1/0，39.045s** | 大库造数→备份→校验→恢复→新 Room 读回；快照期并发写 0 失败、最坏 <5s |
| `BackupRestoreLifecycleInstrumentedTest` | 3/0 | 关库前通知重启、关库后无端口调用、写门终态；坏包交换前失败不关库 |
| `BackupCreateWriteGateInstrumentedTest` | 2/0 | 独立快照期间写入口直接放行、activity=Idle、Room checkpoint/快照 0 调用；回退路径可见 Snapshotting |
| `AnswerKeyCorrectionInstrumentedTest` | 11/0 | 错键→改键、撤销、改两次、重复请求、同 KC 不误伤、纠错中被杀、旧键迟到、未核验键只观察、已作答后报告回落争议 |
| `MistakeRevisionEditInstrumentedTest` | 2/0 | 生产编辑链 |
| `ProblemRevisionEditDatabaseInstrumentedTest` | 5/0 | 新 revision/CAS/幂等/旧 basis 失效/关库重开 |
| `BackupRestoreInstrumentedTest`（含 AUD-05 缺资产拒恢复） | 27/0 | 备份删除/恢复、旧 schema 迁移、设置往返、缺引用资产交换前拒绝 |
| library `MistakeExportFormatSheet` + `Hub` + `RelationAndRevision` + `PendingTasks` | 21/0 | 默认 PDF/选 Word/取消、hub 失败如实、编辑链、空态入口 |
| capture `CaptureShareAuthorization` + `CaptureShareIntakeRoute` | 8/0 | 授权有效时“再试一次”成功、Expired 可达、明确放弃才撤权、PDF 授权过期分档 |
| app `MistakeExportBackground` + `RootTutorFailClosed` + `SharedCaptureEntry` + `KnowledgeReviewSession` | 18/0 | 格式 sheet 接线、空态文案、分享全链、复习会话恢复 |
| `FullMigrationMatrixInstrumentedTest` | 14/14 | 1→68 全路径矩阵；首轮 13 过、1 例 `getText` 读 NULL 断言 NPE，改 `isNull(5)` 后单跑 1/1 |

设备合计 **112 tests / 0 failures**（71 关键链 + 27 AUD-05/恢复 + 14 迁移矩阵）。

### 9.3 设备侧测试夹具修复（只改测试，不改产品行为）

- `BackupCreateWriteGateInstrumentedTest`：先做一次真实写事务把新建 Room 库落到 WAL；否则独立快照引擎按设计走旧回退，测不到独立连接。
- `AnswerKeyCorrectionInstrumentedTest#aCorrectionCommittedBeforeDrainIsReplayedAfterAProcessKill`：先 `markKeyVerified()`（未核验键只落 rejected 观察行，纠错没有账本目标），并等待重新打开后的投影追上快照；原 NPE/超时是夹具前置缺失，不是纠错链丢事件。
- `ProblemRevisionEditDatabaseInstrumentedTest`：源资产 `relativePath` 改为 `source-assets/<sha256>.jpg`（契约不变量）；重试/冲突后的 revision 总数断言改为 2（seed 的 r1 + 新 r2）；重开用例统一使用 `REVISION_TWO`。

### 9.4 仍未关闭 / 未覆盖

- `FullMigrationMatrixInstrumentedTest` **14/14**：首轮 13 通过、1 例因 `getText` 读 NULL 的测试断言 NPE 失败；改为 `isNull(5)` 后单跑该例 1/1 通过（67→68 保行/可写/幂等全过）。
- **KD-53 已关闭**：365MiB 自建包校验+恢复 1/0（39.045s）；`BackupRestoreInstrumentedTest` **27/0**，其中 AUD-05 缺引用资产拒恢复门真实触发（交换前拒绝、原库/包外原图不动）。
- **KD-43**：最小纠错链（a）已实现并 11/11 设备通过；（b）同身份撞被拒行的行为仍只回报 `acceptedBlockedByRejectedRowEvidenceIds`，未裁定；读侧最新替代键按 `created_at` 与投影账本序在极端时间戳下可能分叉；`expectedKeyVersion` 生产 UI 不传。保持 open。
- **KD-47**：仅剩 `SecondaryScreens.kt:735` 用户文案，文件属他线，保持 open。
- **T3 历史作答 UI 读路径**：数据保留已验，按旧 revision/practice unit 的界面读取路径未找到/未验。
- **PRD-06 能力快照半边**、**4E WG-02/04**、**6B-2 本地编辑入口**、**模型真实质量/Provider**：按计划顺延或 UNVERIFIED。
- **HEAD 不可独立编译**：他线未跟踪 `CapabilityFormViewModel.kt`/`DatabaseRestartRequiredScreen.kt`；本批不代提交。
- 10 分钟分享接收窗口是自选产品值，未由用户逐条确认。
