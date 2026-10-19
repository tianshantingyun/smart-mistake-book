# 八轨批记录：4E.1 统一生图底座 + A8 手动建题 + 6B-2 卷面编辑 + KD-53/54 备份栈 + KD-55/47/51 小尾 + 4C A7 分享入口（批次 14，2026-10-18/19）

计划来源：`.zcode/plans/plan-2026-10-18-batch-14-4e1-a8-6b2-backup.md`（8,147 字节；标题即「下一批（八轨）：4E.1 波次 1（统一生图契约+协调器）+ A8 生产者 + 6B-2 编辑 + 备份修复（KD-53/54）+ 小尾 + 4C A7」；
§1 写明「八轨（六实施轨 + 设备轨 + 收尾轨）」，T1–T6 为实施轨、T7 为设备轨、T8 为收尾轨）。

运行脚本：`.zcode/workflow-runs/dwfrun-1ab2473b-30eb-4cf0-9c9c-87aac91b0d23.mjs`（44,322 字节；本记录者只读核对，§4 的命令原文与 `KNOWN_REDS` 均出自该脚本）。
脚本把 `{tracks, unitGateExit, unitGateVerdict, unitGateRounds, androidTestCompileExit, assembleExit, strongVerdict, device, check, review}` 序列化后取前 60,000 字符交给记录员；
本文件收到的材料在 `check` 段第 21 条 claim 的 evidence 中途截断（原文止于 `SecurityException: ... has no access to content://media/external/images/media/`），
`review`（对抗性复核 findings）整段未随材料附上，`check.corrections` 亦未随材料附上。

**记录口径**：本记录的事实来自本轮运行给出的批次材料（T1–T6 逐轨材料 + 单测门/强门禁结论 + 设备段 + 独立核对 check 段），并逐处注明来源
（轨自述 / 门禁执笔 / 设备执笔 / 独立核对执笔 / 本记录者只读核对）。**本记录者未运行 Gradle、未接设备、未做独立复核**；凡「通过」均注明由哪一环跑出。
材料未覆盖处一律写「未记录」。T7 设备轨无逐轨材料，其产出以 `device` 段 + `check` 段给出；T8 收尾轨的关账/台账同步/提交结果未随材料附上（见 §8）。

**本记录者只读核对（2026-10-19 19:57 前后）**：

1. `git log -1` HEAD=`e76ffd28`（批次 13 记录补推送结果），`git status --porcelain` **591** 条；目标文件 `docs/research/2026-10-18-batch-14-eight-track-record.md` 此前**不存在**（本记录即新建）。
2. 六轨 `files` 并集去重 **109 个路径**在共享工作树**全部存在**（逐轨 29/33/19/13/13/17；重复出现 15 次：`RoomCaptureWorkflowRepository.kt` 被 T1/T2/T4 三轨列出〔多 2 次〕，其余 13 个文件各被两轨列出〔多 13 次〕）。
   其中 **78 个 `M`、31 个 `??`**；按顶层计 core 70、feature 27、app 11、tmp 1。
3. `git diff --check -- <109 路径中的已跟踪文件>` → **exit 0，无输出**（未跟踪文件不在该检查内）。
4. 只读扫描 `build/test-results` 的 XML（未执行 Gradle）：core/model 43 文件/460 例、core/domain 76/586、core/data 110/864、core/database 43/196、core/export 13/132、feature/tutor 27/219、feature/review 7/48、feature/library 8/41，**全部 failures=0、errors=0**；mtime 见 §4。
5. ``grep -rn '`[^`]*/\*' --include=*.kt core feature app`` → **无命中**（exit 1）；`ManualTranscriptionCaptureInstrumentedTest.kt:15-16` 已 import `core.model.*`，`CaptureWorkflowRecoveryInstrumentedTest.kt:199` 已用 `checkNotNull(original.width)` 收窄。
6. `.zcode/` 被 `.gitignore:44` 忽略；`tmp/t6_selfcheck.py` **未被 `.gitignore` 命中**（`git check-ignore` 无输出），属未跟踪文件，提交口径需另行决定。
7. 抽查多处行号：多数内容存在，行号有 3–10 行漂移；本记录采用实测行号（见 §6.5）。

---

## 1. 八轨批结果总表

T7（设备轨）与 T8（收尾轨）无逐轨 `files`/summary；T7 的设备产出见 §3，T8 未随材料附上。

| 轨 | 文件数 | 结果 | 实跑证据（来源） | 未达 / 未验证 |
|---|---|---|---|---|
| T1 4E.1 波次 1：统一生图契约 + 协调器 + 提示词装配 + 图像后端适配 | 29 | 新增 `FigureGeneration`/`FigureGenerationPolicy`/协调器与账本端口/`FigurePromptAssembler`；图像通道只接受绑定 operation/method/源证据/配置的**已预留 dispatch context**，旧无预留入口 fail-closed；讲题 `GENERATE_FIGURE`、保存净化、`CleanRedrawTaskRunner` 统一经 coordinator；app 装配一个共享 coordinator/ledger；**未改 schema**，`RoomModelTaskRepository` 原文件未动 | 轨内 6 项非 Gradle 自检：`python tools/check_braces_t1.py` 对 21 个文件全部 OK、`python tools/_t1_brace_check.py` 对 11 个关键文件复检 OK；`git diff --check` exit 0；`grep -rn "channel.generate(request)"` 仅命中 `AttachedImageGeneratorFactory.kt:48`；androidTest/feature 无 `ImageGenerationChannel` 遗留 fake；`supportsImageInput` 在两条配置链无命中。设备：`TutorCommandRecompositionInstrumentedTest` **2/0**；T1 两入口在无模型配置时均 fail-closed（§3.4） | Gradle 全部未跑；真实图像 Provider 未跑；Room 账本真实路径未跑；模型侧决策提示词注入未接线（WG-02/04）；历史 `attachedImages` 缓存缺失改为 fail-closed（WG-05 全量接线留后）；未新增 `remoteKind` 专用审计列，持久层无法按 kind 单独统计；图像预留只递增 operation 计数，模型任务 `attemptCount` 要等下一轮模型预留才刷新 |
| T2 A8 手动建题生产者（KD-52/AUD-28） | 33 | 录入屏（仅错题本来源）新增「手动建题」→ `startManualDraft` → `importDraft(localUri=null)` 建空源草稿（`USER_TRANSCRIPTION`、空 `source_asset_id`、无区域、作者 USER）；工作区默认 `MANUAL_TRANSCRIPTION`；提交跳过 resolve/重绘，空源草稿可 `created=true` 入库并在详情/导出/resume 走通；数据契约源图字段可空，放行只按 `provenance==USER_TRANSCRIPTION` | 轨内：`python C:/Users/听云/AppData/Local/Temp/t2_a8_selfcheck.py` → `OK: 33 files balanced; A8 invariants present`；`git diff --check` 仅 CRLF 警告无空白错误；`grep` 已无 `checkNotNull(sourceAssets.firstOrNull())`/`has no page-zero source asset` 硬崩；读取路径直接解引用扫描无命中；位置参数构造扫描无命中。设备：`ManualTranscriptionCaptureInstrumentedTest` **3/0**、`MistakePdfMissingSourceInstrumentedTest` **1/0**；A8 全链设备**PASS**（手动建题→入库→详情→导出 PDF，§3.4） | Gradle 编译/单测/androidTest 全部未由本轨跑；新增/改动用例未由本轨执行；空源草稿 Compose 端到端仅设备走查一条；`RoomBatchImportRepository` 空源 bundle 跳过无专门用例；TUTOR 来源手动空源被 repository 拒绝（未提供讲题手动空源会话）；共享文件含他线未提交 hunk，整份提交需注明 |
| T3 6B-2 卷面编辑半边 | 19（17 个 Kotlin + 2 个 `build.gradle.kts`） | 新增 `MistakePdfPaperEdit`（增题/删题/调序/改文字/改卷面备注；未编辑返回与正常批量导出同一个 `buildBatchExportInput` 快照与指纹，编辑后重算 `inputSha256`，保留来源题面文档指纹、entryId 与图块完整性字段）与 `MistakePaperDraft/Store`（JSON+摘要落盘，WorkManager Data 只带 draft id）；扩展 `Edited` request/codec/`runEdited`，复用 6B-1 `renderPdf`/`renderDocx`；`TutorExportSheet` 两步态（选题→编辑→确认），状态在 VM；编辑/导出路径无模型端口；KD-55 的 clean image sha/byte size 在新文件中保留 | 轨内：`python C:/Users/听云/AppData/Local/Temp/t3_kotlin_syntax_check.py <17 个 Kotlin 文件>` 每文件 OK（仅括号平衡/同一行双 import 检查，不是编译器）；`git status --short` 列出本轨 19 个文件；用例已写未跑（`MistakePdfPaperEditTest`、`MistakeExportJobRunnerTest`、`ExportPdfWorkerTest`） | 未运行 Gradle 单测/编译；未跑设备/adb；编辑→导出真机走查（PDF/docx）未达：可编辑卷面只在模型 `START_EXPORT` 卡打开的 `TutorExportSheet` 内，模型不可达时无本地入口（§3.6）；「导出成果」hub 未加编辑动作；未改 `MistakePdfPagePlanner.kt`（工作树中该文件的修改来自他线）；KD-55 共享文件最终编译以整树门禁为准 |
| T4 KD-53/54 备份栈修复 | 13 | KD-53：`database.sqlite` 独立预算 1 GiB（题图仍 100 MiB），总量上限 2 GiB、压缩比 100、压缩归档 1 GiB 保留；`validate`/`unpack` 经 `budgetFor` 同口径，`create` 在写字节前核对数据库/题图/条目数/清单长度/总量；新增 365,133,824 B 大 DB 的 create→validate→unpack 逐位 round-trip 用例。KD-54：新增进程内 `BackupWriteGate`（DrainingWriters/Snapshotting、快照前排空、快照期间写入口排队、超时抛可重试 `BackupWriteDeferredException`、成功/失败/取消在 `NonCancellable` 下放行），备份 create 的 checkpoint+VACUUM 段、restore、采集仓库写入口接入（材料摘要称 **13 个数据库写入口**、证据称 **17 个 gate 点**、本记录者 grep 到 **16 个 `admittedWrite(` 调用点**，三处口径不一致）；存储页显示快照状态，采集 VM 给可重试提示；未改 VACUUM 一致性机制、未改 schema | 轨内：`python C:/Users/听云/AppData/Local/Temp/t4_static_check.py` → `OK: 13 files balanced; KD-53/54 wiring and test names present`；`git diff --check` exit 0；用例已写未跑。设备：KD-53 校验 **PASS**（63,467,871 B 归档，4 文件全部通过；归档 `database.sqlite` 未压缩 365,133,824 B）；KD-54 备份成功（60,855,651 B），`BackupRestoreInstrumentedTest` **27/0**、`BackupCreateWriteGateInstrumentedTest` **1/0**、`CaptureBackupWriteGateInstrumentedTest` **1/0** | Gradle 全部未跑；365,133,824 B round-trip 未实际执行；设备 KD-53 恢复**未达**（两次 `SQLException: Connection pool is closed` 崩溃+启动回滚，§3.3）；KD-54 快照期间仍有 **9 条 `unable to grant`、最坏 90.014s**，分钟级饥饿未解除；「备份期间做一次采集保存」未执行（备份时 UI 屏蔽）；写门未覆盖评审/讲题/模型任务/批量导入/删除全部数据等 DAO 直写；压缩归档 1 GiB 外层界存在「成包成功但校验被压缩包上限拒绝」边缘；共享文件含 T5/T1 区段 |
| T5 小尾：KD-55 + KD-47 文案 + KD-51 门加固 | 13 | KD-55：`DocxImageSource.read` 增加 `File.length()` 预检、读后长度复核、读后同一份字节 sha256 复核，不符按既有「读不出」口径省略图；导出输入/批量 Image 块/卷面编辑/草稿 DTO 全链携带来源资产记录。KD-47：日志改为「设置未能完整写入（多键顺序写，可能有部分设置已变）；数据库已恢复」，`applyRestoredSettings` KDoc 如实化，测试 KDoc 改「部分写入」并新增「失败前已成功的键保留」断言；**UI 文案未改**（位于他线未提交 hunk 的 `+` 行）。KD-51：indexed 计数改为 JOIN `knowledge_node` 并同筛 `verification_status`，新增 I=2>R=1 被门挡、只补缺后可召回的仪器化用例 | 轨内：`python C:/Users/听云/AppData/Local/Temp/t5_selfcheck.py` → **38/38 checks passed**（源码级文本断言，不替代编译）；`git diff --check` 对 11 个已跟踪文件 exit 0；用例已写未跑。设备：`BackupRestoreInstrumentedTest` **27/0**（T4 点名；T5 新增 KD-47 断言未单列） | Gradle 编译与 JVM 单测未跑；androidTest 未编译/未执行（`KnowledgeSearchIndexCompletenessInstrumentedTest` 新用例与 `BackupRestoreInstrumentedTest` KD-47 断言均未设备验证）；KD-47 面向用户文案仍为旧句 `SecondaryScreens.kt:735`；KD-55 的批量/编辑/草稿接线落在 T3 在飞文件，若 T3 后续整份重写需复核四处目标区段；KD-47/54 共用 `AndroidBackupRepository.kt` 与 `BackupRestoreInstrumentedTest.kt`，未跑两线合并后的编译 |
| T6 4C·A7 系统分享进 App（+A6 导入核验） | 17（16 个代码/测试文件 + `tmp/t6_selfcheck.py`） | A7：`MainActivity` 在 `onCreate`/`onNewIntent` 收 `ACTION_SEND`/`ACTION_SEND_MULTIPLE`（`EXTRA_STREAM`/`ClipData`/`data` 兜底、去重），投到 StateFlow；Root 消费后进 `capture/shared` 接收页；单图经有界拷贝落自有 `question_*.img` 后交录入页既有 `ApplyAsNew` 路径自动建草稿，多图交既有 `BatchImportRepository`，单 PDF 交既有 `createPdfImport`；失败按类型/数量/大小/可读性/落库分档，接收页给「再试一次 / 去手动录入 / 关闭」并清理预拷件。外部输入门：`content://`、≤30 个（先于 provider 访问）、`image/*` 或 `application/pdf`、图片 20 MB/张 + 总量 256 MB（声明值与实际拷贝值双门）、PDF 128 MB + `%PDF-` 头；图片魔数复用 `CaptureCache`。A6 只补核验用例不重建 | 轨内：`python tmp/t6_selfcheck.py` → `OK: kotlin balance, manifest filters, declaration uniqueness, no debug markers`（含 manifest XML 解析、SEND/SEND_MULTIPLE、关键声明唯一、无 TODO/FIXME/println）；`git diff --check` exit 0；forbidden-file scan 无命中。设备：`SharedCaptureEntryInstrumentedTest` **5/0**；`CaptureShareIntakeInstrumentedTest` **initializationError，0 用例执行**（§3.2）；`am start` 分享入口如实拒绝（shell 无 URI 授权，§3.4） | 全部 JVM 单测与 instrumented 未由本轨运行；编译未验证；「实际拷贝总量 >256 MB 且声明 SIZE 缺失/撒谎」分支只做代码审阅；A6 新用例断言 `IMPORT_FAILED` 基于源码映射，未设备确认；共享文件含 A8/6B-2/KD-54 等他线 hunk；share PDF/multi-image 的 UI 全链只覆盖 multi-image；未做「分享 PDF→BatchImport 页面」仪器化 |
| T7 设备轨 | 未记录（无逐轨 `files`） | 网络诊断与 DNAT 修复；安装 `:app:installLocalFirstDebug`；8 个仪器化类逐类过滤（7 类通过、`CaptureShareIntakeInstrumentedTest` 整类初始化失败）；KD-53 校验通过、恢复崩溃回滚；KD-54 备份成功但仍有分钟级饥饿；A8 全链通过；本地导出一致；T1 双入口 fail-closed；T6 分享入口如实拒绝；发生 `connectedAndroidTest` 卸载清数据的设备事故 | `device.performed`/`device.evidence`；独立核对 `check.claims` 逐条复核（§3.5） | 模型项整块未达（配置缺失/无凭据）；KD-53 恢复未达；KD-54「备份期间采集保存」未执行；6B-2 编辑→导出未达；T6 成功分享路径未人工复现；净化作业未运行；未做 git 提交 |
| T8 收尾轨 | 未记录 | 未记录（关账/台账同步/提交/推送结果均未随材料附上） | 未记录 | 未记录 |

（模块计〔本记录者按 §6.1 清单口径核对〕：core 70、feature 27、app 11、tmp 1，合计 **109**；其中 78 `M`、31 `??`。）

---

## 2. 各轨明细

### T1 4E.1 波次 1：统一生图契约 + 协调器 + 提示词装配 + 图像后端适配

**任务**（计划 §1 T1）：只做 4E.1 的公共底座 + 图像后端（IMAGE_MODEL）验通；不引入 QuickJS、不广告 JS、不做 schema 变更、不改模型请求 schema 17；proposal 由入口适配层按真实可用方法构造（`method=IMAGE_MODEL`、`purpose` 来自入口）。

- **契约与策略**：`core/model/.../FigureGeneration.kt` 定义 Request/Proposal/Decision，`purpose` 与 `method` 正交，`FigureArtifact` 类型化；
  `FigureGenerationPolicy.kt:34` 的 `validate` 覆盖能力/目标/预算合法性，`:68` 的 `imageModelProposal` 构造提案，`:126` 对 `GENERATE` 缺 method 返回 `GENERATE_WITHOUT_METHOD`。
- **预留先于 POST**：`core/data/.../visual/DefaultFigureGenerationCoordinator.kt:208` 先 `ledger.reserve(...)`，`:255` 才 `backend.channel()`；通道只在 `Granted` 后建立，拒绝时**不发 HTTP**。
- **图像通道 fail-closed**：`core/data/.../model/ImageGenerationChannel.kt:44` 为带 dispatch 的 `generate` 重载；`:63` 与 `:69` 的旧无预留入口直接抛 `ImageDispatchNotReservedException`（异常类 `:122`）。
  `OpenAiImageGenerationChannel.kt:46`/`:74` 在组请求/POST 前调用 `requireServerBound`（`:92`），校验 operation/method/源证据/配置。
- **账本适配（未加列）**：`core/data/.../visual/RoomFigureDispatchLedger.kt:35` 分派父操作，`:47` 模型任务父操作只递增既有 `model_task_operation.dispatch_count`（不推进任务行状态机），`:99` 净化作业核验已领取的 `clean_redraw_task` 尝试（复用 `attempt_count`）。
  端口/DAO 接线：`core/database/.../ModelTaskDatabasePort.kt:201`、`core/database/.../dao/ModelTaskTransactionDao.kt:95`/`:101`（增量 SQL）与 `:318`（KDoc）、`core/database/.../RoomStudyDatabase.kt:1404`；均为无 schema 变更的接线。
- **三入口统一**：讲题 `GENERATE_FIGURE`（`TutorFigureGenerator`）、保存净化（`ConfiguredCleanImageGenerator`）、`CleanRedrawTaskRunner` 统一转调 coordinator，不按入口选后端。
- **app 装配**：`app/.../SmartMistakeBookApplication.kt:378` 创建共享 `FigureGenerationCoordinationFactory`，`:396`/`:414` 注入讲题与净化入口；该文件同时含并行 T3（6B-2）区段，本轨只加装配 hunk。
- **测试（已写，未由本轨执行）**：新增 `FigureGenerationPolicyTest`、`FigurePromptParityTest`、`FigureGenerationCoordinatorTest`、`FigureGenerationWiringTest`；既有 `OpenAiImageGenerationChannelTest`、`ConfiguredTutorFigureGeneratorTest`、`ConfiguredCleanImageGeneratorTest`、`CleanRedrawTaskRunnerTest`、`CleanRedrawTaskInstrumentedTest`、`CaptureWorkflowInstrumentedTest` 已按新签名适配。
- **轨内自检（材料原文）**：
  - `python tools/check_braces_t1.py` 对 21 个新增/修改 Kotlin 文件做括号/引号平衡检查，全部 OK、exit 0；随后 `python tools/_t1_brace_check.py` 对其中 11 个关键文件复检，全部 OK、BAD 0；两个脚本均已删除。
  - `git diff --check -- core/model core/domain core/data core/database app/src/main/kotlin/com/tingyun/smartmistakebook/SmartMistakeBookApplication.kt` → exit 0（仅仓库既有 CRLF 警告，无空白错误）。
  - `grep -rn "channel.generate(request)" core/data/src/main app/src/main` → 仅 `core/data/.../AttachedImageGeneratorFactory.kt:48`；该调用落在旧无预留入口并 fail-closed。
  - `grep -rln "ImageGenerationChannel" core/data/src/androidTest app/src/androidTest feature` → 无命中；`grep -rn "supportsImageInput"` 在两条配置链无命中，`ImageCredentialGateSingleSourceTest` 三条链仍只经 `resolveImageCredential`。
- **未验证（轨内声明）**：Gradle 全部未跑（`:core:model:test`、`:core:domain:test`、`:core:data:testDebugUnitTest`、`:core:data` 与 app 的 androidTest 编译、app assemble）；四个新测试与更新后的既有用例均未实际执行；真实图像 Provider 未跑（无真实凭证/设备），fake 成功不等于模型质量通过；`RoomFigureDispatchLedger` 的真实 Room 路径未跑 JVM/仪器化（`FigureGenerationWiringTest` 用 fake 账本）；模型侧决策提示词（`TUTOR_PLAN`/`RESPOND`、`CAPTURE_PARSE`、`IMAGE_PIPELINE_CLASSIFY`）运行时注入未接线，属 WG-02/04；`FigurePromptAssembler` 目前作为唯一正文来源被两个入口用于请求判定上下文，并由 `FigurePromptParityTest` 对照；历史 `attachedImages` 兼容链缓存命中仍可读，缓存缺失时因图像通道要求预留而 fail-closed，不再自动生成，WG-05 全量接线留后；未新增 `remoteKind` 专用审计列/行，本波次不改 schema，只复用共享 `dispatch_count` 与 `clean_redraw_task` 尝试，持久层无法按 kind 单独统计；图像预留只递增 operation 计数、不推进任务行，模型任务 snapshot 的 `attemptCount` 要等下一轮模型预留才刷新。

### T2 A8 手动建题生产者（KD-52/AUD-28）

**任务**（计划 §1 T2）：创建入口 + 草稿链空源分支 + 提交路径空源分支 + 读取路径空源分支；放行只按 `provenance=USER_TRANSCRIPTION`，不放宽其它来源；全链 instrumented。

- **入口**：`feature/capture/.../CaptureViewModel.kt:155-179` `startManualDraft`（仅 `LIBRARY` 来源）与 `:421-474` `runManualDraft`（复用同一 requestId 重试、KD-54 让路文案）；
  `CaptureScreen.kt:212-231` 手动草稿判定/提交门、`:763` 标题、`:813`/`:867` 不渲染模型卡、`:895-903` 表单参数、`:956-966` 入口回调；`components/CaptureScreenComponents.kt:288-305` 的 `capture_mode_manual` 入口与副文案「没有照片也能建题：直接敲题面，确认后存入错题本。」
- **草稿链**：`core/data/.../capture/RoomCaptureWorkflowRepository.kt:229-266` `importDraft` 空 URI 分支（+TUTOR 拒绝+手动重放指纹）；`:949-1038` `createInitialDraft`/`initialDraftCommand`（空 `sourceAsset` → 空白段落 + `USER_TRANSCRIPTION` + author USER）；`:1116-1127` `toSummary` 可空源；空源提交跳过 `decideAndRedraw`（`:590-599`）；手动导入重放判据 `RoomCaptureWorkflowMappings.kt:242-265`。
- **读取路径（5 处）**：`ProblemDraftTransactionDao.kt:1141-1160` 空态合法、半空坏行 fail-closed；`RoomStudyDatabase.kt:842-849` 委托 DAO，KDoc 明确空源合法；`RoomBatchImportRepository.kt:433-470` 空源批次页跳过切分而非传 null、`:519-525` 空源 bundle 跳过；`RoomTutorToolRunner.kt:975-989` `REDRAW_PROBLEM` 空源按 K2a `ok=true` 空范围，不生成/不付费；`RoomCaptureWorkflowRepository.kt:548`（`confirmAndCommit` 仅照片 resolve）、`:736`（`confirmForTutoring` 仅照片 resolve）、`:764`（sheet bytes 空源→null）。
- **放行口径**：除 captured validator 外的所有新分支均为 `provenance == USER_TRANSCRIPTION && sourceAssetId.isBlank()`：
  `ProblemDraftTransactionDao.kt:411-417`（revise）、`ProblemDraftEditWorkspaceDao.kt:130-141`/`:301-310`（写入/读取）、`RoomPendingCaptureStore.kt:173-186`（pending 工作区）；候选侧原有 `loadTutorSession` 口径未动。
- **工作区/恢复**：`CaptureWorkspacePolicy.kt:65-90`/`:144-316` 恢复 `MANUAL_TRANSCRIPTION`、编辑保持 provenance、`manualTranscriptionCommitReady`；`CapturePendingCaptures.kt:117-133` 无源草稿不谎称已保存原图。
- **测试（已写，未由本轨执行）**：`core:data` 新增 `ManualTranscriptionCaptureInstrumentedTest.kt` 3 例（全链落库+详情 `MistakeSourceSet.Missing`、重放幂等+TUTOR 拒绝、空源 resume 与「非 `USER_TRANSCRIPTION` 空白源必须拒」）；`core:export` 新增 `MistakePdfMissingSourceInstrumentedTest.kt`（Missing 源仍经 `MistakePdfEligibility`+`Exporter.prepare` 渲染出 PDF）；`feature:capture` 的 `CaptureScreenInstrumentedTest.kt:109-137`、`:73`/`:102`，`CaptureWorkspacePolicyTest.kt:120-160`，`CaptureViewModelTest.kt:423-479`。
  设备段实跑：`ManualTranscriptionCaptureInstrumentedTest` **3/0**、`MistakePdfMissingSourceInstrumentedTest` **1/0**（§3.2）；A8 人工全链 PASS（§3.4）。
- **共享树避让（材料原文）**：`RoomCaptureWorkflowRepository.kt` 保留 T4 KD-54 的 `admittedWrite`/`BackupWriteGate`/`CleanImageRequest` hunk（如 `:105-152`、`:175-225`）；`RoomTutorToolRunner.kt` 保留 T1 的 `parentOperationId`（`:997-998`）；`CaptureScreen.kt` 保留 T6 的 `initialSharedSourceUri`/`onInitialSharedSourceConsumed`（`:103-110`、`:337-343`）；`RoomBatchImportRepository.kt` 保留 KD-42/`BatchImportDraftState` 他线 hunk；`RoomStudyDatabase.kt` 保留 T1 的 `reserveModelTaskOperationDispatch`（`:1401-1404`）；`CaptureViewModelTest.kt` 保留 T4 备份让路用例。
- **未验证（轨内声明）**：Gradle 编译/单测/androidTest 全部未跑（本轨不执行 Gradle，也未用设备/adb）；33 个改动文件的 Kotlin 编译正确性只有静态自检（括号配平+不变式 grep）支撑；新增/改动用例未由本轨执行；空源草稿 Compose 端到端未走（仅 ViewModel/仓储/Compose fake 层之外由设备补一条人工链）；`RoomBatchImportRepository` 空源 bundle 跳过无专门用例（正常批次链不可能出现，只有防御分支）；手动入口仅对 `LIBRARY` 开放，TUTOR 来源手动创建被 repository require 拒绝并在 DAO 侧以 `canonical_source_asset` 冲突码失败，未提供讲题手动空源会话；共享文件含他线未提交 hunk，整份提交时需按先例注明；本轨未做任何 git 写操作。

### T3 6B-2 卷面编辑半边

**任务**（计划 §1 T3）：卷面编辑模型（增删题/调序/改文字 → 编辑后的 `MistakePdfExportInput` 块序列）+ 编辑入口（`TutorExportSheet`/导出 hub 的编辑态）+ 重渲染（PDF/docx 复用 6B-1）+ 导出内容断言（编辑必变、未编辑逐位不变）；不碰 `MistakePdfPagePlanner`。

- **卷面编辑模型**：`core/export/.../MistakePdfPaperEdit.kt:27` 类；`:86` `figureReviewRequired`（改标题/非图块标记原图需复核，调序不触发）；`:96` `withQuestionMoved`；`:106` `withQuestionRemoved`；`:203` `toDraft`（未编辑返回 `buildBatchExportInput` 原快照；编辑后 subtitle 按当前题数重算）；`:376` `buildPaperBlocks`（批量导出与编辑共用同一装配）。
- **草稿落盘**：`core/export/.../MistakePaperDraftStore.kt:25` `MistakePaperDraft` 不变式（未编辑必须保持来源指纹）；`:60` Store；`:63` `write`；`:86` `read`（第一行 `sha256(body)` 先核后解析，坏文件/版本不符返回 null；`schema_kind` 判别键避开 `FigureSchema.Unknown.type` 撞键）。
- **重渲染**：`core/export/.../MistakeExportJobRunner.kt:44` `Edited` 分支；`:132` `runEdited`（只读草稿、不重读条目；草稿缺失/版式不一致如实失败；编辑过用「-已编辑」文件名）。
- **worker**：`app/.../ExportPdfWorker.kt:295` `DRAFT_ID_KEY`；`:348`/`:390` `Edited` 编解码（`draftId` 缺失整条拒绝）；`:106` 解码失败也清草稿；终态后清理草稿、重试期间保留。
- **app 装配**：`SmartMistakeBookApplication.kt:693` `buildMistakePaperEdit`（`Dispatchers.Default` 读题面，无模型派发）；`:708` `startMistakeExportEdited`（草稿先落盘再入队；跨平台安全 `draftId`）。
- **UI/状态**：`TutorExportSheet.kt:74` `paperEdit`/`editLoading`/`editMessage`；`:290` 编辑态分支；`:556` 「下一步：编辑卷面」按钮与「不编辑，直接导出」保留原直出路径；两步态在 VM 中，旋转不丢，返回选题保留勾选。
  `TutorExportEditSheet.kt:49` 编辑界面（标题/段落/公式/选项/备注、上移下移删除加回、原图复核提示、空文字禁用确认）；`TutorConversationViewModel.kt:404` `onExportEditRequest`（读题面建卷面；>100 道与无读者如实提示；取消异常不吞），`:479-506` 各编辑动作只改 `paperEdit`，`:508` `updatePaperEdit`。
- **测试（已写，未执行）**：`MistakePdfPaperEditTest` 覆盖未编辑 docx 字节金样/编辑必变/调序重编号/删题加回恢复原快照/最后一题不可删/外部加题/图复核标记/不污染来源与题面文档指纹/空白文字拒绝/未知 id/`fromStates` 略过与全不可导出/store 全块型 round-trip/损坏与缺失/路径逃逸/TTL；`MistakeExportJobRunnerTest` 覆盖 `Edited` 只渲染草稿且 `readExact`/`readByEntryId` 调用计数 0（core:export 无模型端口，0 模型派发为结构性）、草稿/读者缺失、版式不一致、未编辑保留批量名、docx 已编辑名；`ExportPdfWorkerTest` 覆盖 `Edited` codec 往返与缺 `draftId` 拒绝。
- **未验证（轨内声明）**：未运行 Gradle 单测/编译；未跑设备/adb 与 instrumented；编辑→导出真机走查、Android `PdfDocument` 对编辑输入的 PDF 渲染未验证（docx 侧有 JVM 字节金样用例但同样未执行）；「导出成果」hub 未加编辑动作（记录行不携带卷面模型/条目 id，本批零 schema；编辑入口落在唯一有选题步骤的 `TutorExportSheet`）；KD-55 在并行共享树中改了 `MistakePdfEligibility.kt`/`DocxImageSource` 等，并在本轨新建的 `MistakePdfPaperEdit.kt`/`MistakePaperDraftStore.kt` 中补了 clean image 字段；本轨保留并补了图块/落盘断言，但最终编译与全量测试以共享树门禁为准；未改 `MistakePdfPagePlanner.kt`（工作树中该文件的修改来自他线）。

### T4 KD-53/54 备份栈修复

**任务**（计划 §1 T4）：KD-53 按条目类型设预算（`database.sqlite` 单独上限，保留压缩比/总量/磁盘预检），`SmbkArchiveCodec` + 校验/恢复两侧同口径；KD-54 实现备份「让路」形态（显式状态 + 其它写入口短暂排队/提示 + 备份前后排空），不改快照一致性机制。

- **KD-53 预算**：`core/data/.../backup/SmbkArchiveCodec.kt:129` `maxDatabaseEntryBytes = 1 GiB`；`:161` `budgetFor` 按条目类型取预算（`database.sqlite` → DB 预算，其余 → 题图预算）；`:238` `create` 写字节前检查数据库预算；`:792` `expandEntry` 用 `entryBudget`，校验/解包同口径；总量 2 GiB、压缩比 100、压缩归档 1 GiB 保留在 `:136-142`。
- **KD-53 测试**：`SmbkArchiveCodecTest.kt:423`（数据库用独立预算）、`:472`（数据库超自身预算双侧拒绝）、`:523`（create 前失败不留半包）、`:550`（总量门）、`:583`（365,133,824 B 大 DB round-trip，超时 300s）；
  `BackupRestoreInstrumentedTest.kt:1208`（题图单条目超限，按新双预算语义更新夹具）、`:1240`（DB 高于单图上限、低于 DB 上限时 validate+unpack 都通过）、`:1281`（DB 超自身预算双侧拒绝）。
- **KD-54 门**：`core/domain/.../BackupWriteGate.kt:88` 类；`:123` `activity` StateFlow；`:135` `runBackup`（drain→Snapshotting→`finally` 在 `NonCancellable` 下放行）；`:187` `withWriteAdmission`（短暂排队/超时提示/独占段旁路）。
  `AndroidBackupRepository.kt:75` 注入共享门、`:77` activity、`:162` checkpoint+VACUUM 走 `runBackup`、`:325` restore 也走门、`:120` 孤儿图清理走门。
  `RoomCaptureWorkflowRepository.kt:110` 注入门、`:153` `admittedWrite` 只包 DB 写；本记录者 `grep` 到 16 个调用点：`:177`/`:197`/`:217`（弃稿/工作区保存/消费）、`:276`（追加页）、`:333`（替换）、`:410`（切分）、`:480`/`:509`（切分作业/切分草稿）、`:551`（保存题目）、`:718`（挂接重绘）、`:738`/`:776`/`:827`/`:835`（讲题确认/保存/台账/结束会话）、`:955`（初建草稿）、`:1093`（OCR 写）。材料摘要称「13 个数据库写入口」、证据称「17 个 gate 点」；本记录者 `grep` 得 17 行 `admittedWrite(`（1 个定义 + 16 个调用）。三处口径不一致，原因未记录。
- **KD-54 测试/UI**：`BackupWriteGateTest.kt:29`/`:62`/`:97`/`:128`/`:160`/`:185`/`:216`/`:242`（排队后继续、超时明确提示、备份前排空、排空超时失败、并发备份拒绝、失败放行、取消放行、独占段旁路）；
  `CaptureBackupWriteGateInstrumentedTest.kt:36`（快照期间采集写排队、结束后执行）、`BackupCreateWriteGateInstrumentedTest.kt:33`（create 的快照段确在门内、写入口排队后放行）；
  `SecondaryScreens.kt:654`/`:837`（备份快照显式状态与文案）、`CaptureViewModel.kt:252-270`（被让路的保存提示「没有开始/可重试」）、`CaptureViewModelTest.kt:384`（该提示与 `retryable`/`dataPreserved` 用例）。
- **轨内自检**：`python C:/Users/听云/AppData/Local/Temp/t4_static_check.py` → `OK: 13 files balanced; KD-53/54 wiring and test names present`（括号/字符串剥离后的结构检查 + 关键接线字符串，不是 Kotlin 编译）；`git diff --check`（13 文件）→ exit 0，仅 CRLF 归一警告。
- **设备实测（§3.3）**：KD-53 校验侧 PASS；恢复侧两次崩溃+回滚，**未达**；KD-54 备份成功，但快照期间 9 条 `unable to grant`、最坏 90.014s，「备份期间采集保存」未执行。
- **未验证（轨内声明）**：未运行任何 Gradle 任务（新增/修改 Kotlin 未编译，单测与仪器化未执行）；KD-53 的 365,133,824 B round-trip 未实际执行，磁盘峰值（约 1.5 GB）与耗时未实测；设备/仪器化未跑（≥365 MB 自建备份在 app 内可校验/可恢复、AUD-05 缺引用资产专用门真实触发、备份期间无分钟级写饥饿，均留 T7）；写入路径残留：评审/讲题/模型任务/批量导入/删除全部数据等 DAO 直写未接入 `BackupWriteGate`；`create` 无法预知压缩后体积，接近 1 GiB 且不可压缩的库存在「成包成功但校验被压缩包上限拒绝」边缘（已在 `SmbkResourceLimits` KDoc 说明）；共享文件含 T5 KD-47、T1 4E、T2 A8、他线 UI 区段；`BackupRestoreInstrumentedTest` 的既有 `singleEntryOversizeIsRejected` 夹具按 KD-53 新语义改为「题图超单条目预算」（原夹具用 1024 B 数据库验证，在新双预算下不再命中该门）；断言强度未降低，并新增数据库预算正/负用例。

### T5 小尾：KD-55 + KD-47 文案 + KD-51 门加固

**任务**（计划 §1 T5）：KD-55 docx 图片渲染前按记录 sha/长度复核；KD-47 UI 文案如实措辞（若他线在飞不可分离则只改日志/枚举）；KD-51 indexed 计数同筛 `verification_status`；各带用例。

- **KD-55 读口**：`core/export/.../DocxImageSource.kt:34-67` `read(localUri, expectedSha256, expectedByteSize)`；`:44` `File.length()` 预检；`:48` 读后长度复核；`:49` sha256 复核（`ignoreCase`）；全部在 PNG/JPEG 解析与返回之前。
- **KD-55 渲染器**：`DocxMistakeRenderer.kt:97-151` `imageExpectations` 汇总 clean image/Image 块记录（`:138-151`），`DocxImageSource.read(..., expectedSha256/expectedByteSize)` 放行后才构造 media 部件（`:109-129`）。
- **KD-55 记录来源**：`MistakePdfEligibility.kt:144-159` `cleanImageAsset` 一次取出 `role=CLEAN_IMAGE` 且 `Available` 的同一条资产记录，uri/sha/byteSize 同时进 `MistakePdfExportInput`（`:233-241` 字段）；口径与 `AndroidCanonicalAssetVault.kt:273-281` 的 `canonicalAssetFileIsIntact(file, byteSize, sha)` 一致。
- **KD-55 批量/卷面/草稿**：`MistakePdfPaperEdit.kt:237-238`（编辑后输入保留记录）、`:339-348`（题模型字段）、`:395-402`（Image 块携带记录）；`MistakePaperDraftStore.kt:178-179`/`:201-202`（输入 DTO）、`:242-248`/`:270-276`（Image DTO 往返）、`:308-311`/`:364-371`（DTO 字段）。两文件是 T3 在飞文件，只改这些目标区段。
- **KD-55 用例**：`DocxImageSourceTest.kt:51-129`（匹配通过、长度不符拒绝、等长换字节 sha 拒绝）；`DocxMistakeRendererTest.kt:258-349`（干净图匹配嵌入、换图后省略且文本保留、批量块按记录复核）；`MistakePdfEligibilityTest.kt:151-156`（CAMERA 干扰项在前仍取 CLEAN_IMAGE 记录的 sha/长度）；`MistakePdfBatchEligibilityTest.kt:136-157`（批量 Image 块携带记录）。
- **KD-47**：`core/data/.../backup/AndroidBackupRepository.kt:604-630` 日志改为「设置未能完整写入（多键顺序写，可能有部分设置已变）；数据库已恢复」，`applyRestoredSettings` KDoc 增设 KD-47 如实措辞段；
  `BackupRestoreInstrumentedTest.kt:600-653` KDoc 改为部分写入，新增断言 restore 失败后调度键已是备份的 0.9（而非恢复前 0.8，delta 1e-9），钉住非原子性。
  **UI 未动证据**：`app/.../SecondaryScreens.kt:735` 仍为「设置未能写入，本机设置保持原样。」；`git diff -U2` 显示该字符串两行均为他线未提交 hunk 的 `+` 行（`@@ -730,16 +727,25 @@ settingsNote` 新增），按计划取「不可分离」分支，只改日志/枚举（枚举与回执 KDoc 批次 13 已如实）。
- **KD-51 SQL**：`core/database/.../dao/ProblemOrganizationDao.kt:224-249`（KDoc 起于 224，查询在 239-244）的 `countIndexedKnowledgeNodesBySubject` 改为 `COUNT(DISTINCT feature.knowledge_node_id)` + `INNER JOIN knowledge_node ON id` + `node.verification_status IN ('CURATED','SOURCE_GROUNDED','USER_CONFIRMED')`。
- **KD-51 用例**：`KnowledgeSearchIndexCompletenessInstrumentedTest.kt:283-361` 直插 1 个无特征行的 `SOURCE_GROUNDED` 节点 + 2 个各带特征行的 `MODEL_CANDIDATE` 节点，锚点置当前；断言分母=1、新分子=0（旧口径=2 会骗过门），且同一次召回把已审校节点补进索引并可召回。
- **轨内自检**：`python C:/Users/听云/AppData/Local/Temp/t5_selfcheck.py` → **38/38 checks passed**（源码级文本断言，含 SQL/接线/用例名/括号平衡，不替代编译）；`git diff --check`（11 个已跟踪文件）→ exit 0。
- **未验证（轨内声明）**：Gradle 编译、core:export JVM 单测、androidTest 编译与执行、设备验证均未跑；`DocxImageSourceTest`/`DocxMistakeRendererTest`/`MistakePdfEligibilityTest`/`MistakePdfBatchEligibilityTest` 未执行；`KnowledgeSearchIndexCompletenessInstrumentedTest` 与 `BackupRestoreInstrumentedTest` 的 KD-47 断言未设备验证；KD-47 面向用户文案未改；KD-55 的批量/编辑/草稿接线落在 T3 在飞文件，若 T3 后续整份重写需复核四处目标区段；KD-47/54 共用文件由 T4 同文件在飞，未跑两线合并后的完整性。

### T6 4C·A7 系统分享进 App（+A6 导入核验）

**任务**（计划 §1 T6）：A7 系统分享 intent → 录入入口（`CaptureAcquisitionLaunchers` 族扩展 + manifest + 单/多文件处理 + 失败恢复）；A6 既有 PDF/图片导入链的类型范围与失败恢复核验（补用例，不重建）；外部输入路径/大小门必须显式。

- **manifest**：`app/src/main/AndroidManifest.xml:27` `singleTop`；`:35` `ACTION_SEND`、`:41` `ACTION_SEND_MULTIPLE`（材料记 `:33-43`；实际 action 行见实测）。
- **intent 投递**：`MainActivity.kt:22` `sharedCaptureRequests` StateFlow；`:38` `onNewIntent`；`:59-88` 外部分享解析/去重/投递；`SmartMistakeBookRoot.kt:227-266` 消费与导航、`:737-775` `Routes.SharedCapture` 接收页；`SmartMistakeBookRoutes.kt:40` `capture/shared`。
- **单图接收**：`CaptureScreen.kt:110-112`/`:356-360` 一次性入参；`CaptureAcquisitionCommands.kt:170-194` 有界拷贝到自有 `question_*.img`。
- **门与计划**：`CaptureSharePolicy.kt:104-179`；常量 `:25`（≤30 个）、`:34`（图片 20 MB/张）、`:37`（PDF 128 MB）、`:40`（总量 256 MB）；intent 解析 `CaptureShareIntent.kt:20-68`；接收/落盘 `CaptureShareIntake.kt:43-235`；失败恢复 UI `CaptureShareIntakeRoute.kt:51-148`。
- **取舍（材料原文）**：分享单图来源复用 `PHOTO_PICKER`（不新增 `CaptureInputSource`，避免牵动库内 token/schema）；分享不是 `ActivityResult` launcher，故扩展落在 `CaptureAcquisitionCommands`/`CaptureCache` 这一族并新增 `CaptureScreen` 一次性入参，未改 `CaptureAcquisitionLaunchers` 数据结构。
- **测试（已写，未由本轨执行）**：
  - JVM `CaptureSharePolicyTest.kt`：空/重复/31 数量门先于 provider/非 `content://`/类型/混合/多 PDF/20 MB 与 128 MB 恰等与 +1/总量 300 MB/未知大小/魔数映射/预算常量一致/可重试性。
  - app UI `SharedCaptureEntryInstrumentedTest.kt`：单图落草稿并核对规范资产 `file://`、坏文件拒绝且无草稿、31 文件拒绝且无 job、20 MB+1 拒绝、两图整卷 job 两页两草稿。
  - app 接收层 `CaptureShareIntakeInstrumentedTest.kt`：manifest 注册、单图自有缓存、坏图无残留、超限不落盘、缺失 URI→`UNREADABLE`、多图交管线且预拷件删除、PDF 交管线、非 PDF 在文件头门拒绝。
  - A6 `BatchImportSourceRangeInstrumentedTest.kt`：PDF 字节当图片页→`IMPORT_FAILED` 且另一页 `READY`、1 页 PDF 拒绝、非 PDF 拒绝、31 请求范围门拒绝。
- **轨内自检**：`python tmp/t6_selfcheck.py` → `OK: kotlin balance, manifest filters, declaration uniqueness, no debug markers`（9 个新文件 + 7 个改动文件的括号/字符串/注释配平、manifest XML 可解析且含 SEND/SEND_MULTIPLE、关键声明唯一、无 TODO/FIXME/println）；`git diff --check` exit 0；forbidden-file scan 对 `RoomBatchImportRepository`/`OpenAiModelProtocol`/`RoomModelTaskRepository`/`RoomBatchImportStore`/`BatchImportDao`/`MistakePdfPagePlanner`/`TutorSessionPanel`/`BatchImportRoute`/`core.domain.BatchImportRepository` 执行 `grep 'A7（4C）|CaptureShare'` → 无命中。
- **设备（§3.2/§3.4）**：`SharedCaptureEntryInstrumentedTest` 5/0 PASS；`CaptureShareIntakeInstrumentedTest` **initializationError，0 用例执行**；人工 `am start` 分享因 shell 无法给 MediaStore URI 授权而如实拒绝（不是产品缺陷）；成功分享路径未人工复现。
- **未验证（轨内声明）**：全部 JVM 单测与 instrumented 用例未运行（无 Gradle、无设备），编译是否通过未验证；「实际拷贝总量 >256 MB 且声明 SIZE 缺失/撒谎」分支只做代码审阅，没有用会撒谎的 `ContentProvider` 做仪器化；A6 新用例断言的 `IMPORT_FAILED` 基于源码映射（`AndroidCanonicalAssetVault.decodeBounds` 的 `check` 抛 `IllegalStateException` → `toBatchFailureCode` 兜底 `IMPORT_FAILED`），未在设备上跑过确认；共享工作树里 `CaptureScreen.kt`（T2 A8）、`SmartMistakeBookRoot.kt`（T3 6B-2）、`MainActivity.kt`（他线 onResume 提醒刷新）、`RoomCaptureWorkflowRepository.kt`（T4 KD-54）等文件同时含有他线改动；本轨只用定点 Edit 插入目标区段并已重读确认保留，但若他线在读取之后又落改动，仍需整包编译复核；share PDF/multi-image 的 UI 全链只覆盖 multi-image；PDF 的整卷落点由接收层测试 + A6 PDF 链核验覆盖，未做「分享 PDF→BatchImport 页面」的仪器化。

---

## 3. 设备段（材料原文；执行者为设备走查员，本记录者未接设备）

材料声明：主设备 **emulator-5554**（AVD=`test_device`，Android 14，`sdk_gphone64_x86_64`），全程单机走查；**emulator-5556**（AVD=`audit_user_visible_20261016`）仅用于 DNS 对照、模型配置检查，以及独立核对员复跑仪器化。
adb 路径 `C:/Android/Sdk/platform-tools/adb.exe`；下述全部为设备轨实跑；独立核对（`check` 段）对其中若干条做了复跑更正，凡更正处均注明。

### 3.1 网络、安装与设备事故

- **网络诊断**：宿主 `nslookup opencode.ai` 正常；模拟器 DNS 全灭，且不是 `-dns-server` 缺失：5554 启动参数本就含 `-dns-server 8.8.8.8`（仍失败）；换 AVD 5556 同样失败；`private_dns_mode` 为 `null`（等同 off，no-op）。
  实测模拟器出口 UDP/53 直连 8.8.8.8、192.168.1.1、223.5.5.5 全部有应答，唯独 `10.0.2.3:53`（模拟器内建 DNS 代理）20s 无响应 ⇒ 根因=模拟器 DNS 代理，而非上游网络。
  以 `iptables -t nat OUTPUT DNAT 10.0.2.3:53→8.8.8.8:53`（udp+tcp）恢复：`ping opencode.ai` 解析到 172.65.90.23 并回包。
- **安装**：设备走查员自述 `cmd /c gradlew.bat :app:installLocalFirstDebug` 成功（`Installing APK 'app-localFirst-debug.apk' on 'test_device(AVD) - 14' / BUILD SUCCESSFUL in 28s`）。
  **独立核对 `holds=false`**：核对员未复跑该 Gradle 命令，不能复核输出与耗时；只核对到 `dumpsys package` 的 versionName `0.2.0-localfirst`、firstInstallTime/lastUpdateTime `2026-10-19 04:19:36`、`pm path` 有 base.apk，以及仓库存在 `app/build/outputs/apk/localFirst/debug/app-localFirst-debug.apk`。
- **PATH 注**：本机 PATH 里 `~/AppData/Roaming/npm/cmd` 影子了 Windows `cmd.exe`；`type -a cmd` 与 npm 目录下 423B 的 `cmd` 可证实影子机制。**独立核对 `holds=false`**：前两次「too many arguments」的现场输出无法复核（未复现该报错）。
- **设备事故（必须记录）**：`:app:connectedLocalFirstDebugAndroidTest` 跑完时 AGP 的卸载步骤把 `com.tingyun.smartmistakebook.localfirst` 整个卸载，连带删除 app 数据：原 **386,736,128 B** 的 `smart-mistake-book.db` 与其上的模型配置一起消失（此前只跑了 `installLocalFirstDebug`，未用 `-wipe-data`）。
  KD-53 恢复因此是在「新装空库」上做的，其回滚语义与 crash 不受影响；但模型配置无法在该设备继续使用。事后已重装 app 并靠 `/sdcard` 上的批次 13 备份把库拉回约 385 MB 量级继续走查。

### 3.2 仪器化逐类过滤

命令形如 `cmd /c gradlew.bat :core:data:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<类全名>`（core:export / app / feature:tutor 同形）；设备走查员在 5554 上逐类跑，独立核对员在 5556 上用仓库内本次构建的测试 APK（app 12:15、core/data 12:12、core/export 12:14、feature/tutor 12:17）逐类复跑。

| 轨标注 | 类 | 设备走查员（5554） | 独立核对（5556） |
|---|---|---|---|
| T2 | `ManualTranscriptionCaptureInstrumentedTest` | 3/0 PASS | 3/0 OK |
| T2 | `MistakePdfMissingSourceInstrumentedTest` | 1/0 PASS | 1/0 OK |
| T4 | `BackupCreateWriteGateInstrumentedTest` | 1/0 PASS | 1/0 OK |
| T4 | `CaptureBackupWriteGateInstrumentedTest` | 1/0 PASS | 1/0 OK |
| T4 | `BackupRestoreInstrumentedTest`（T4 改动版） | 27/0 PASS | 27/0 OK（32.0s） |
| T6 | `SharedCaptureEntryInstrumentedTest` | 5/0 PASS | 5/0 OK（28.9s） |
| T1 | `TutorCommandRecompositionInstrumentedTest` | 2/0 PASS | 2/0 OK |
| T6 | `CaptureShareIntakeInstrumentedTest` | **initializationError，0 用例执行** | 原样复现同一 `initializationError` |

- **T6 初始化失败原文**：`org.junit.runners.model.InvalidTestClassError: Invalid test class ...: 1. Method aSharedImageIsStagedIntoTheOwnedCaptureCache() should be void`。
  本记录者实读该类有 **8 个 `@Test`**：`:63` 一个块体用例，`:82`/`:105`/`:123`/`:148`/`:170`/`:204`/`:218` 七个 `fun ... = runBlocking { ... }` 表达式体；JUnit4 在 runner 初始化阶段即拒绝该类，整类 0 用例执行。材料文字称「7 个 @Test 全是表达式体」，与实读的 8 个/7 个表达式体有 1 个块体用例的差异。
- **核对员说明**：通过的 7 类源码 `@Test` 计数与通过数一致，且无 `@Ignore`/`assume` 跳过守卫（grep 无命中）；核对员用 `am instrument` 直跑而非 5554 上的 Gradle `connectedAndroidTest`，耗时不具可比性。

### 3.3 KD-53 校验/恢复与 KD-54 备份

- **KD-53 校验侧=通过**：app 内「校验一个备份文件」选 `/sdcard/Download/smart-mistake-book-1792298681299.smbk`（**63,467,871 B**，批次 13 自建件）→ UI 回报「备份有效：4 个文件全部通过校验。」。
  归档内 `database.sqlite` 单条目未压缩大小 **365,133,824 B**（>旧 100 MB 单条目上限），不再报单条目上限；manifest 记 `problemCount=0`、`assetCount=2`。独立核对用 python `zipfile` 直接检视：6 个条目（manifest/checksums/4 个载荷）、`database.sqlite` 365,133,824 B、`checksums.sha256` 逐条重算全部匹配（含 database.sqlite 流式 sha256=True）。
- **KD-53 恢复侧=未达（两次复现崩溃+回滚）**：app 内「从备份恢复」同一文件：
  - 第一次 `04:26:33.441` FATAL EXCEPTION main `android.database.SQLException: Error code: 21, message: Connection pool is closed`（`androidx.room3.coroutines.PassthroughConnectionPool.useConnection`）→ 进程死亡；重启后启动对账回滚，横幅「备份恢复没有完成／已回到恢复之前的数据」。
  - 第二次 `04:38:23.381` 同样 FATAL EXCEPTION/同样异常。
  - **独立核对**：崩溃栈与设备侧遗留的 `restore-validate-*.db.lck`（04:26/04:38）可对上；第一条诊断号 `startup:restore-reverted:815bed99-...` **无法复核**（logcat/临时文件/DB 均无命中），第二条 `startup:restore-reverted:d73c2a84-c34d-413b-94df-55d70dc153ea` 在截图中可见。
  - **回滚判定**：归档库 `user_version=67`、`knowledge_node=3916`、`canonical_source_asset=2`；app 库在设备走查时点为 67/3661/0，核对员拉取当前活动库为 67/3662/1（此后新增 1 个不在备份中的 `pseudo:GENERAL` 节点与 1 个 `PHOTO_PICKER` 题图资产），回滚结论（归档≠app、恢复未生效）仍成立。
- **KD-54 备份侧**：在设备自述「385,200,128 B 库」上「创建完整备份」成功，新产出 `smart-mistake-book-1792384840190.smbk` = **60,855,651 B**（04:43）。独立核对拉回解包：内部 `database.sqlite` 347,885,568 B、schemaVersion 67、problemCount 0/assetCount 0、sha256 与 manifest 相符；**「385,200,128 B 库」这一具体尺寸无法复核**（见核对员的更正）。
  快照期间 logcat **9 条** `unable to grant a connection`，最坏 **90.014s**，3 个 `DefaultDispatcher` worker（flags 0x2）在等；池警告里阻塞语句就是 `VACUUM INTO '…/cache/backup-snapshots/snapshot-83220915712873.db'`。批次 13 记录为 601s/40 条，本次量级下降但分钟级饥饿仍在。
- **「备份期间做一次采集保存」=未能执行**：备份在跑时 UI 被显式屏蔽：按 BACK 被 `BackHandler(enabled = operationBusy) { }` 吞掉、存储页 header 返回也被禁用（`SecondaryScreens.kt:807`/`:809`），无法在备份进行中从 UI 进入采集保存；这 9 条等待来自后台协程而非用户采集写。核对员另更正：**「无底部导航」不是备份期特有**。

### 3.4 A8 全链、导出、T1 与 T6 走查

- **A8 全链=PASS**：错题本→录入→录入方式含「手动建题」（副文案「没有照片也能建题：直接敲题面，确认后存入错题本。」）→ 表单出现「手动建题／把题目完整敲进下面／没有拍照：题面以你输入的内容为准」+学科 chips+标题+题面 → 填标题「S」、题面「SmokeTest:given1」→「保存修改并存入错题本」→ 回执「已存入错题本／这道题是新建入库的：错题本里之前没有同一道题。」→ 错题本显示「1 道题」→ 详情页题面为 `SmokeTest:given1`。
  独立核对：`problem`/`practice_unit`/`problem_revision` 各 1 行，`title='S'`、`problem_markdown='# S\n\nSmokeTest:given1'`、`source_type=CAPTURE_CONFIRMED`；截图逐项吻合。题面用 ASCII 是因 `adb input text` 不支持非 ASCII、MCP `type_text` 同通道报 NPE。
- **本地导出（A8 空源路径）**：错题本「导出当前 1 道」→ 导出成果「错题练习-1道.pdf · 已完成 · 共 1 页」，可保存/分享/打印；拉回设备内 `cache/mistake_pdf_exports/.../mistake.pdf`（51,910 B）经 `pdftotext` 得 «1. S / GENERAL · 1 / SmokeTest:given1 / 1 / 1»；`mistake_export_record` 1 行、`status=SUCCEEDED`、`page_count=1`、`pdf_sha256` 与实物一致。空源（手动建题无照片）题面导出内容与该题一致、不崩。
- **T1=双入口 fail-closed，无伪造成功**：讲题入口发消息 →「当前模型还不能处理对话，先完成模型配置和能力测试。」+按钮「检查模型设置」；净化/整理入口相册取图后 →「配置好模型后，拍照题图才会交给模型整理」+「去设置」。两处均为可行动提示，未假装进入编辑/讲解。
- **T6 分享入口（附带走查）**：`am start` 发 `ACTION_SEND`（`content://media/external/images/media/1000000161`）后 app 打开「接收分享」页（`root_capture_share`），因读不到 URI 被如实拒绝：「这个分享没有导入／这个文件读不出来，可能已损坏或没有读取权限。／原文件还在原来的位置；也可以直接从录入页手动选图。」+ 再试一次/去手动录入/关闭。
  logcat 对应 `SecurityException: com.tingyun.smartmistakebook.localfirst has no access to content://media/external/images/media/1000000161` —— 这是取证方式所致（shell 非该 provider URI 的属主，grant 不生效），**不能据此判产品缺陷**。
- **模型配置缺失**：5554 `run-as ... ls -l files/datastore/` 只有 `sleep_journal.preferences_pb` 172B，无 `model_configuration.preferences_pb`；5556 的 `model_configuration.preferences_pb.preferences_pb` 为 0 字节；app 内大模型设置页四字段均空。**模型项整块未执行**。
- **秘密处置（仅报告位置，不含值）**：宿主 `C:/Users/听云/.config/opencode/opencode.json` 内 `provider.options.apiKey` 为明文密钥；建议改为环境变量或凭据库，并轮换。本记录不复述该值。
  设备材料另记：宿主 `~/.local/share/opencode/auth.json` 存在名为 `opencode-go` 的条目、与 app 旧 provider 同名；把外部密钥移植进设备属未授权操作，本次**未做、也未读取其值**。

### 3.5 独立核对段（check；材料截断）

独立核对员对设备自述逐条复核（claim/holds/evidence）。材料在**第 21 条 claim 的 evidence 中途截断**；可见的 21 条中：

- **holds=true（17 条）**：设备选择与 AVD；宿主/模拟器 DNS 对照；`-dns-server` 不是缺失项；UDP/53 探测定位 10.0.2.3；iptables DNAT 恢复；8 个仪器化类的计数（7 类通过、1 类初始化失败）；`CaptureShareIntakeInstrumentedTest` 初始化失败；KD-53 校验通过；KD-53 恢复崩溃+回滚；第二条诊断号可见；回滚库计数（带 +1/+1 说明）；KD-54 备份成功；9 条 grant/90.014s；备份期 UI 屏蔽；A8 全链；本地导出；T1 双入口 fail-closed。
- **holds=false（3 条）**：安装命令「BUILD SUCCESSFUL in 28s」未复跑；PATH「too many arguments」未复现；第一条诊断号 `815bed99-...` 无法复核。
- **第 21 条（T6 分享入口）**：材料在 evidence 中途截断，`holds` 与该条之后的 claims 未记录；`check.corrections` 整段未随材料附上。

关键更正/补强（照录）：

1. 安装一条只能证明包在设备上且版本/安装时间一致，不能证明该次 Gradle 输出。
2. 第一条诊断号不可复核；第二条诊断号由截图证实。
3. 回滚库计数在核对时点为 67/3662/1（非自述的 67/3661/0），原因是此后新增了 1 个 `pseudo:GENERAL` 节点与 1 个 `PHOTO_PICKER` 资产；归档侧 67/3916/2 不变，回滚结论不变。
4. 「385,200,128 B 库」具体尺寸不可复核；KD-54 备份内部库为 347,885,568 B。
5. 「无底部导航」不是备份期特有。
6. `CaptureShareIntakeInstrumentedTest` 的失败是测试类方法签名问题，不是产品代码失败。

### 3.6 设备段未验证项（照录 device.unverified）

1. **模型项整块未达**（KD-37 UI 端到端「失败→重新判定→成功」、KD-38 前后对照、AUD-16 剩余第二次复习/异常恢复/导出）：网络侧已恢复（`ping opencode.ai` 通），但 5554 的模型配置已随 app 数据被 `connectedAndroidTest` 卸载删除，且无凭据可重播种——repo/docs 内无该端点（`https://opencode.ai/zen/go/v1`）的凭据；5556 的 `model_configuration.preferences_pb` 为 0 字节。
2. **6B-2「编辑卷面（增删/调序/改文字）→ 导出 PDF/docx → 内容变化」未达**：可编辑卷面与格式选择都在 `TutorExportSheet`，而该 sheet 只由模型 `START_EXPORT` 卡打开（`TutorPendingRequestCommands.kt:433`）；模型不可达 ⇒ 设备上没有本地入口可进入编辑卷面，docx 分支亦然。
3. **KD-53 恢复未达**（不视为通过）：两次 app 内恢复均以 main 线程 `SQLException: Connection pool is closed` 崩溃并触发启动回滚，用户看到的终态是「备份恢复没有完成」；KD-53 的关闭条件（大库自建备份可在 app 内校验与恢复）只满足校验半边。
4. **KD-54 不能据此关账**：`unable-to-grant` 只观测到 9 条/最坏 90s，与关条件「备份期间无分钟级写饥饿」不符；且「备份期间做一次采集保存」未执行（UI 屏蔽），本次饥饿计数来自后台协程，不能等同于「采集保存被饿死」的直接证据。
5. **`CaptureShareIntakeInstrumentedTest` 0 用例执行**（整类 `initializationError`）——T6 的「分享层把什么交出去」这一层本次无设备/仪器化结论。
6. **T6 成功导入的设备路径未达**：shell 无法为 MediaStore URI 授权（`SecurityException`），分享只走到「如实拒绝」分支；成功路径本次仅由 `SharedCaptureEntryInstrumentedTest` 5/0 覆盖，未由人工分享操作系统应用复现。
7. **净化作业本身未运行观测**（AUD-09 clean_redraw 需模型），只验了入口的 fail-closed 文案。
8. **未做 git 提交**（按要求）；工作区改动保持原样。

---

## 4. 门禁与跑测口径

**口径**：本批门禁分两段——① 单测门（七模块，材料 `unitGateExit=0`、`unitGateRounds=5`）；② 强门禁（androidTest 编译四模块 + 整包编译，材料字段 `androidTestCompileExit=1`、`assembleExit=1`，但 `strongVerdict` 的末轮执笔称两门全 0）。
两段均由运行脚本串行执行 + 门禁归因员归因；**本记录者未跑 Gradle**，命令原文读自 §开头所列运行脚本（只读核对），结果数字照录材料。

### 4.1 单测门（七模块；材料 exit=0，修复环 5 轮）

- **命令原文（读自脚本 `runUnitGate`）**：
  `cmd /c gradlew.bat :core:model:test :core:domain:test :core:data:testDebugUnitTest :core:database:testDebugUnitTest :core:export:testDebugUnitTest :feature:review:testDebugUnitTest :feature:library:testDebugUnitTest --continue`（timeout 3,600,000 ms）。
- **轮次口径**：材料字段 `unitGateRounds=5`；脚本赋值 `unitGateRounds = gateRound + finalRound`（修复环轮数，不含首次执行），故不能把 5 直接读成「执行 5 次」；材料未给首跑+回跑的完整执行次数。
- **执笔的逐模块 XML 聚合（照录）**：core/model 43 文件/460 例、core/data 110/864、core/export 13/132（本轮重跑，mtime 19:46–19:49）；core/domain 76/586、feature/tutor 27/219、feature/review 7/48、feature/library 8/41（当日更早的绿色产物，UP-TO-DATE）；七模块合计 classes=284、tests=2,350、failures=0、errors=0、skipped=0。
- **本记录者只读复扫（2026-10-19 19:57）**：上述七模块 XML 计数与执笔逐项一致；另 `core:database` 也有 43 文件/196 例/0 失败（mtime 09:38）。本记录者未执行 Gradle，只读 XML。
- **集合口径矛盾（必须点名）**：脚本命令包含 `:core:database:testDebugUnitTest`、**不含** `:feature:tutor:testDebugUnitTest`；但 `unitGateVerdict` 的逐模块清单列了 `feature:tutor`、**未列** `core:database`，并称「core:database 的 SECTION facet 本就不在这七模块单测集合内」。两者不能同时为真。
  当前磁盘上两者都有绿色 XML（core:database 43/196、feature:tutor 27/219），因此「零失败」可以在当前磁盘复核，但「七模块」的具体集合与材料自述不一致；提交前应以命令为准重跑，或要求运行方说明实际执行集。
- **判红口径（脚本 `KNOWN_REDS` 照录）**：开工基线=批次 13 收尾后七模块单测实跑 exit=0；三类例外可判既有红：① core:data 的 GoldenRetrieval 系列（KB 线在飞）；② core:database 的 SECTION facet 边际门（环境红）；③ 失败可归因到他线在飞文件（81 个未提交：`RoomModelTaskRepository`/`OpenAiModelProtocol`/`TutorSessionPanel`/`KernelWave0SchemaContractTest`/`MistakePdfPagePlanner`/`BatchImport*`/`MistakeDetailRoute` 等）。判既有红必须在证据里写明它与本批 diff 无关的理由。
- **门禁判定字段（照录）**：`knownRedsSeen=[]`、`newFailures=[]`、`onlyKnownReds=true`。

### 4.2 强门禁（androidTest 编译四模块 + 整包；材料字段 1/1，末轮执笔称 0/0）

- **命令原文（读自脚本 world-read#2/#3）**：
  - `cmd /c gradlew.bat :core:data:compileDebugAndroidTestKotlin :core:database:compileDebugAndroidTestKotlin :feature:library:compileDebugAndroidTestKotlin :feature:capture:compileDebugAndroidTestKotlin`（timeout 2,400,000 ms）；
  - `cmd /c gradlew.bat :app:assembleLocalFirstDebug`（timeout 2,400,000 ms）。
- **材料字段与执笔的冲突**：`androidTestCompileExit=1`、`assembleExit=1`；`strongVerdict.evidence` 却写「两个门全 0（androidTest 编译 exit=0、整包编译 exit=0）」。
  读脚本可见原因：`atCompile`/`assemble` 保存的是**首轮** `world-read#2/#3` 的结果；修复环内用 `at2`/`as2` 重跑，但从未回写外层变量，最终导出的字段仍是首轮 1/1，而 `strongVerdict` 是末轮归因员的转述。因此：
  - **首轮确实 exit=1/1**（材料字段）；
  - **末轮是否 0/0 只有执笔转述，没有末轮原始退出码字段**；不能把 `strongVerdict` 的「全 0」当成当前原始证据。
- **末轮执笔的修复核对（照录要点）**：
  - RC1 包名写错已修：`ManualTranscriptionCaptureInstrumentedTest.kt:15-16` 已 import `core.model.CaptureDraftEditedField`/`CaptureDraftEditorMode`（上轮是 `core.domain`）；本记录者复读一致。
  - RC2 可空性已修：`CaptureWorkflowRecoveryInstrumentedTest.kt:199` 已用 `checkNotNull(original.width)` 收窄；`SplitImportCommitReceiptInstrumentedTest.kt:171` 与 `SplitImportReceiptReconciliationInstrumentedTest.kt:242` 同形收窄。生产侧 `CaptureWorkflowRepository.kt:365-372` 的 `SplitCaptureDraftRequest.sourceAssetId` 仍为非空 `String`，即本批选择「测试侧收窄」而非放宽生产类型。
  - 注释嵌套缺陷已清：``grep -rn '`[^`]*/\*' --include=*.kt core feature app`` 零命中；此前 `SmbkArchiveCodec.kt:155` / `CaptureSharePolicy.kt:91` 的命中已消失；三个假阳性（`CaptureSharePolicyTest.kt`、`CaptureShareIntakeInstrumentedTest.kt`、`SharedCaptureEntryInstrumentedTest.kt`）的多余 `/*` 在 `"image/*"` 字符串字面量内，不构成注释。本记录者复跑该 grep，同样零命中。
  - 执笔声明未亲跑 Gradle；两条门的 exit 与尾部取自材料/脚本，结论建立在「失败集为空」+ 现场代码核实。
- **既有红点名（本轮）**：`knownRedsSeen=[]`、`newFailures=[]`、`onlyKnownReds=true`（单测门与强门禁同）。GoldenRetrieval 系列、SECTION facet 边际门本轮未观测——SECTION facet 属 core:database，而单测门执笔的七模块清单未列 core:database（见 §4.1 矛盾），故不能据此说它已通过。
- **设备面另有 1 条红（不在门禁范围）**：`CaptureShareIntakeInstrumentedTest` 整类 `initializationError`、0 用例执行（§3.2）——这是测试类方法签名问题，需修测试，不是产品红。

### 4.3 跑测范围与未跑项

- 本批**已实跑**：单测门七模块（脚本串行，修复环 5 轮；其中 core/model、core/data、core/export 本轮重跑，另外四模块沿用当日更早的绿色产物）、强门禁两段（首轮 1/1，末轮执笔称 0/0）、设备端 8 个仪器化类逐类过滤、A8 人工全链、KD-53/54 人工走查、T1/T6 入口走查。
- 本批**未跑**：整包 release；`tools/tests` 全量 Python；各轨**轨内**未自行跑 Gradle——T1 的 `:core:model:test`/`:core:domain:test`/`:core:data:testDebugUnitTest` 与 androidTest 编译、T2 的编译/单测/androidTest、T3 的 JVM 单测、T4 的 JVM 单测（含 365 MB 大样本）、T5 的 JVM 单测与 androidTest、T6 的 JVM 单测与仪器化（门禁与设备段另跑的范围见 §4.1/§4.2/§3.2；设备只跑了 `SharedCaptureEntryInstrumentedTest`，`CaptureShareIntakeInstrumentedTest` 未执行任何用例）；T2 的 `CaptureWorkflowInstrumentedTest`、`CaptureScreenInstrumentedTest`、`CaptureWorkspacePolicyTest` 等新增/改动用例未在设备段点名。
- **本记录者没有运行 Gradle，也没有接设备**；§4 的「exit=0/1」均为材料/脚本字段，不是本记录者实测。

---

## 5. 未验证项总表

### A. 仍成立（至材料末仍未验证 / 未达）

| 轨/段 | 未验证项 |
|---|---|
| T1 | Gradle 全部未跑；真实图像 Provider 未跑；`RoomFigureDispatchLedger` 真实 Room 路径未跑；模型侧决策提示词注入未接线（WG-02/04）；历史 `attachedImages` 缓存缺失改为 fail-closed（WG-05 全量接线留后）；未新增 `remoteKind` 专用审计列；图像预留只递增 operation 计数，模型任务 `attemptCount` 刷新滞后。 |
| T2 | Gradle 编译/单测/androidTest 未由本轨跑；新增/改动用例未由本轨执行；空源草稿 Compose 端到端仅设备一条；`RoomBatchImportRepository` 空源 bundle 跳过无专门用例；TUTOR 手动空源会话被拒绝；共享文件含他线 hunk。 |
| T3 | Gradle 单测/编译未跑；未跑设备；编辑→导出真机走查未达（模型 `START_EXPORT` 卡是唯一入口）；hub 未加编辑动作；`MistakePdfPagePlanner` 未碰；KD-55 共享文件最终编译待整树门禁。 |
| T4 | Gradle 未跑；365,133,824 B round-trip 未执行；设备 KD-53 恢复崩溃+回滚；KD-54 仍有 9 条 grant/90.014s；「备份期间采集保存」未执行；写门未覆盖评审/讲题/模型任务/批量导入/删除全部数据等 DAO 直写；压缩归档 1 GiB 边缘；共享文件含 T5/T1 区段。 |
| T5 | Gradle 编译/JVM 单测/androidTest 未跑；KD-47 UI 文案仍为旧句（`SecondaryScreens.kt:735`）；KD-55 批量/编辑/草稿接线落在 T3 在飞文件；KD-47/54 共用文件未跑合并编译。 |
| T6 | 全部 JVM 单测与 instrumented 未由本轨运行；编译未验证；「实际拷贝总量 >256 MB 且声明 SIZE 缺失/撒谎」未用会撒谎的 provider 仪器化；A6 `IMPORT_FAILED` 未设备确认；共享文件含他线 hunk；PDF UI 全链未覆盖；`CaptureShareIntakeInstrumentedTest` 0 用例。 |
| T7 设备 | 模型项整块未达；6B-2 编辑→导出未达；KD-53 恢复未达；KD-54 关账条件未满足；备份期采集保存未执行；T6 成功分享路径未人工复现；净化作业未运行；未做 git 提交。 |
| 门禁 | 单测门「七模块」集合与脚本命令不一致（脚本含 core:database，执笔清单含 feature:tutor 且称 core:database 不在集合）；强门禁首轮字段 1/1、末轮执笔称 0/0，缺末轮原始退出码；本记录者未跑 Gradle。 |
| 材料 | `check` 第 21 条 claim 之后未记录；`review`（对抗性复核 findings）整段未附；`check.corrections` 未附；T8 收尾轨材料未附。 |
| 设备事故 | `connectedAndroidTest` 卸载删除 app 数据（386,736,128 B DB + 模型配置）；5554 模型配置无法继续使用。 |

### B. 已被批次后段证据覆盖（轨内写作时点「未跑/未验证」，后由设备段实跑）

1. T2 `ManualTranscriptionCaptureInstrumentedTest` → 设备 **3/0**；`MistakePdfMissingSourceInstrumentedTest` → **1/0**（§3.2）。
2. T2 A8 人工全链 → **PASS**（手动建题→入库→详情→导出 PDF，§3.4）。
3. T4 `BackupRestoreInstrumentedTest`（T4 改动版）→ **27/0**；`BackupCreateWriteGateInstrumentedTest` **1/0**；`CaptureBackupWriteGateInstrumentedTest` **1/0**（§3.2）。
4. T4 KD-53 校验侧 → **PASS**（63,467,871 B 归档、4 文件通过、DB 365,133,824 B 不再触发单条目上限，§3.3）；KD-53 恢复侧 → **未达**（崩溃+回滚）。
5. T4 KD-54 备份侧 → **成功**（60,855,651 B）；但饥饿未解除（9 条/90.014s）。
6. T6 `SharedCaptureEntryInstrumentedTest` → **5/0**；但 `CaptureShareIntakeInstrumentedTest` → **0 用例/initializationError**（§3.2）。
7. T1 `TutorCommandRecompositionInstrumentedTest` → **2/0**；T1 两入口在无模型配置时 **fail-closed**（§3.4）。
8. 单测门 XML 当前磁盘复核：脚本命令的七模块与 `feature:tutor` 均有绿色 XML（core:database 43/196、feature:tutor 27/219），全部 0 失败；但门命令集合与执笔清单不一致（§4.1）。
9. 强门禁末轮执笔称 androidTest 编译/整包编译全 0；首轮字段为 1/1；本记录者无法用原始退出码复核末轮（§4.2）。

### C. 材料自身矛盾/缺口（必须与「通过」分开读）

1. **单测门集合矛盾**：脚本命令含 `:core:database:testDebugUnitTest`、不含 `:feature:tutor:testDebugUnitTest`；`unitGateVerdict.evidence` 反过来列 `feature:tutor`、不列 `core:database`，并称 core:database 不在七模块内。
2. **强门禁退出码矛盾**：材料字段 `androidTestCompileExit=1`、`assembleExit=1`；`strongVerdict` 执笔称末轮两门全 0。按脚本结构，字段是首轮、执笔是末轮，但末轮原始退出码未附。
3. **T6 测试计数差异**：材料称 `CaptureShareIntakeInstrumentedTest` 7 个 `@Test` 全是表达式体；本记录者实读为 8 个 `@Test`，其中 7 个表达式体、1 个块体（`:63`）。JUnit 初始化失败的结论不变。
4. **T4 gate 点计数差异**：材料摘要称 13 个数据库写入口、证据称 17 个 gate 点；本记录者 grep 到 17 行 `admittedWrite(`（1 个定义 + 16 个调用）。三处口径不一致。
5. **设备自述与核对**：安装耗时、PATH 报错、第一条诊断号三条 `holds=false`；「385,200,128 B 库」具体尺寸不可复核；「无底部导航」不是备份期特有。
6. **材料截断**：`check` 第 21 条 claim 之后、`review.findings`、`check.corrections` 均未记录；T8 未附。

---

## 6. 共享树说明（提交者必读）

### 6.1 本线文件清单（六轨 `files` 并集去重，共 **109** 个路径）

本记录者只读核对（2026-10-19 19:57 前后）：109 个路径在共享工作树**全部存在**；其中 **78 个 `M`、31 个 `??`**。
`RoomCaptureWorkflowRepository.kt` 被 T1/T2/T4 三轨共同列出，另有 13 个文件被两轨共同列出（§6.2）。
`tmp/t6_selfcheck.py` 未被 `.gitignore` 命中，属未跟踪文件；`.zcode/`（含运行脚本与计划）被 `.gitignore:44` 忽略，**不进提交**。

```
app/src/androidTest/kotlin/com/tingyun/smartmistakebook/CaptureShareIntakeInstrumentedTest.kt
app/src/androidTest/kotlin/com/tingyun/smartmistakebook/SharedCaptureEntryInstrumentedTest.kt
app/src/androidTest/kotlin/com/tingyun/smartmistakebook/SharedCaptureTestFiles.kt
app/src/main/AndroidManifest.xml
app/src/main/kotlin/com/tingyun/smartmistakebook/ExportPdfWorker.kt
app/src/main/kotlin/com/tingyun/smartmistakebook/MainActivity.kt
app/src/main/kotlin/com/tingyun/smartmistakebook/SecondaryScreens.kt
app/src/main/kotlin/com/tingyun/smartmistakebook/SmartMistakeBookApplication.kt
app/src/main/kotlin/com/tingyun/smartmistakebook/SmartMistakeBookRoot.kt
app/src/main/kotlin/com/tingyun/smartmistakebook/SmartMistakeBookRoutes.kt
app/src/test/kotlin/com/tingyun/smartmistakebook/ExportPdfWorkerTest.kt
core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/backup/BackupCreateWriteGateInstrumentedTest.kt
core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/backup/BackupRestoreInstrumentedTest.kt
core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/backup/CaptureBackupWriteGateInstrumentedTest.kt
core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/capture/BatchImportSourceRangeInstrumentedTest.kt
core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/capture/CaptureWorkflowInstrumentedTest.kt
core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/capture/CaptureWorkflowRecoveryInstrumentedTest.kt
core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/capture/CaptureWorkflowTestBase.kt
core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/capture/CleanRedrawTaskInstrumentedTest.kt
core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/capture/ManualTranscriptionCaptureInstrumentedTest.kt
core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/model/RestrictedModelAssetSourceInstrumentedTest.kt
core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/backup/AndroidBackupRepository.kt
core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/backup/SmbkArchiveCodec.kt
core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/capture/CleanRedrawTaskRunner.kt
core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/capture/ConfiguredCleanImageGenerator.kt
core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/capture/RoomBatchImportRepository.kt
core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/capture/RoomCaptureWorkflowMappings.kt
core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/capture/RoomCaptureWorkflowRepository.kt
core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/model/FigurePromptAssembler.kt
core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/model/ImageGenerationChannel.kt
core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/model/OpenAiImageGenerationChannel.kt
core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/model/TutorFigureGenerator.kt
core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/RoomTutorToolRunner.kt
core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/DefaultFigureGenerationCoordinator.kt
core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/RoomFigureDispatchLedger.kt
core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/backup/SmbkArchiveCodecTest.kt
core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/capture/CleanRedrawTaskRunnerTest.kt
core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/capture/ConfiguredCleanImageGeneratorTest.kt
core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/capture/RoomCaptureWorkflowMappingsTest.kt
core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/model/ConfiguredTutorFigureGeneratorTest.kt
core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/model/FigurePromptParityTest.kt
core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/model/OpenAiImageGenerationChannelTest.kt
core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/visual/FigureGenerationCoordinatorTest.kt
core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/visual/FigureGenerationWiringTest.kt
core/database/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/database/KnowledgeSearchIndexCompletenessInstrumentedTest.kt
core/database/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/database/ProblemDraftDatabaseInstrumentedTest.kt
core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/DatabaseContract.kt
core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/ModelTaskDatabasePort.kt
core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/RoomPendingCaptureStore.kt
core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/RoomStudyDatabase.kt
core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabaseRecords.kt
core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/dao/ModelTaskTransactionDao.kt
core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/dao/ProblemDraftEditWorkspaceDao.kt
core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/dao/ProblemDraftTransactionDao.kt
core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/dao/ProblemOrganizationDao.kt
core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/BackupRepository.kt
core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/BackupWriteGate.kt
core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/CaptureWorkflowRepository.kt
core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/CleanImageGenerator.kt
core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/FigureGenerationCoordinator.kt
core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/RemoteModelDispatchLedger.kt
core/domain/src/test/kotlin/com/tingyun/smartmistakebook/core/domain/BackupWriteGateTest.kt
core/export/build.gradle.kts
core/export/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfMissingSourceInstrumentedTest.kt
core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/DocxImageSource.kt
core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/DocxMistakeRenderer.kt
core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakeExportJobRunner.kt
core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakePaperDraftStore.kt
core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfBatchEligibility.kt
core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfEligibility.kt
core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfExportCopy.kt
core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfPaperEdit.kt
core/export/src/test/kotlin/com/tingyun/smartmistakebook/core/export/DocxImageSourceTest.kt
core/export/src/test/kotlin/com/tingyun/smartmistakebook/core/export/DocxMistakeRendererTest.kt
core/export/src/test/kotlin/com/tingyun/smartmistakebook/core/export/MistakeExportJobRunnerTest.kt
core/export/src/test/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfBatchEligibilityTest.kt
core/export/src/test/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfEligibilityTest.kt
core/export/src/test/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfPaperEditTest.kt
core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/FigureGeneration.kt
core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/FigureGenerationPolicy.kt
core/model/src/test/kotlin/com/tingyun/smartmistakebook/core/model/FigureGenerationPolicyTest.kt
feature/capture/src/androidTest/kotlin/com/tingyun/smartmistakebook/feature/capture/CaptureScreenInstrumentedTest.kt
feature/capture/src/main/java/com/tingyun/smartmistakebook/feature/capture/CaptureAcquisitionCommands.kt
feature/capture/src/main/java/com/tingyun/smartmistakebook/feature/capture/CaptureCache.kt
feature/capture/src/main/java/com/tingyun/smartmistakebook/feature/capture/CaptureCorrectionForm.kt
feature/capture/src/main/java/com/tingyun/smartmistakebook/feature/capture/CaptureDraftLifecyclePolicy.kt
feature/capture/src/main/java/com/tingyun/smartmistakebook/feature/capture/CaptureDraftStateCommands.kt
feature/capture/src/main/java/com/tingyun/smartmistakebook/feature/capture/CapturePendingCaptures.kt
feature/capture/src/main/java/com/tingyun/smartmistakebook/feature/capture/CaptureResumeCommands.kt
feature/capture/src/main/java/com/tingyun/smartmistakebook/feature/capture/CaptureScreen.kt
feature/capture/src/main/java/com/tingyun/smartmistakebook/feature/capture/CaptureShareIntake.kt
feature/capture/src/main/java/com/tingyun/smartmistakebook/feature/capture/CaptureShareIntakeRoute.kt
feature/capture/src/main/java/com/tingyun/smartmistakebook/feature/capture/CaptureShareIntent.kt
feature/capture/src/main/java/com/tingyun/smartmistakebook/feature/capture/CaptureSharePolicy.kt
feature/capture/src/main/java/com/tingyun/smartmistakebook/feature/capture/CaptureUiState.kt
feature/capture/src/main/java/com/tingyun/smartmistakebook/feature/capture/CaptureViewModel.kt
feature/capture/src/main/java/com/tingyun/smartmistakebook/feature/capture/CaptureWorkspacePolicy.kt
feature/capture/src/main/java/com/tingyun/smartmistakebook/feature/capture/components/CaptureScreenComponents.kt
feature/capture/src/test/kotlin/com/tingyun/smartmistakebook/feature/capture/CaptureSharePolicyTest.kt
feature/capture/src/test/kotlin/com/tingyun/smartmistakebook/feature/capture/CaptureViewModelTest.kt
feature/capture/src/test/kotlin/com/tingyun/smartmistakebook/feature/capture/CaptureWorkspacePolicyTest.kt
feature/tutor/build.gradle.kts
feature/tutor/src/main/java/com/tingyun/smartmistakebook/feature/tutor/TutorConversationViewModel.kt
feature/tutor/src/main/java/com/tingyun/smartmistakebook/feature/tutor/TutorConversationViewModelFactory.kt
feature/tutor/src/main/java/com/tingyun/smartmistakebook/feature/tutor/TutorExportEditSheet.kt
feature/tutor/src/main/java/com/tingyun/smartmistakebook/feature/tutor/TutorExportSheet.kt
feature/tutor/src/main/java/com/tingyun/smartmistakebook/feature/tutor/TutorLobbyRoute.kt
feature/tutor/src/main/java/com/tingyun/smartmistakebook/feature/tutor/TutorRoute.kt
tmp/t6_selfcheck.py
```

（顶层计：core 70、feature 27、app 11、tmp 1，合计 109。本记录文件 `docs/research/2026-10-18-batch-14-eight-track-record.md` 不在上述轨文件清单内，提交时需另行纳入 docs。）

### 6.2 同批内多轨共编文件（评审/提交勿按单轨 hunk 挑拣）

- `core/data/.../capture/RoomCaptureWorkflowRepository.kt`：**T1/T2/T4** 三轨（T1 净化/图像预留、T2 A8 空源、T4 KD-54 `admittedWrite`/`BackupWriteGate`）。
- `core/data/.../study/RoomTutorToolRunner.kt`：**T1/T2**（T1 图像入口、T2 空源 `REDRAW_PROBLEM`）。
- `core/database/.../RoomStudyDatabase.kt`：**T1/T2**（T1 `reserveModelTaskOperationDispatch`、T2 A8 读路径）。
- `app/.../SmartMistakeBookApplication.kt`：**T1/T3**（T1 生图 coordinator 装配、T3 卷面编辑装配）。
- `core/data/src/androidTest/.../CaptureWorkflowInstrumentedTest.kt`：**T1/T2**（T1 图像适配、T2 A8 空源链）。
- `feature/capture/.../CaptureViewModel.kt`：**T2/T4**（T2 手动建题、T4 备份让路提示）。
- `feature/capture/.../CaptureScreen.kt`：**T2/T6**（T2 手动建题表单、T6 分享一次性入参）。
- `feature/capture/src/test/.../CaptureViewModelTest.kt`：**T2/T4**。
- `app/src/androidTest/.../SharedCaptureEntryInstrumentedTest.kt`：**T2/T6**。
- `app/.../SmartMistakeBookRoot.kt`：**T3/T6**。
- `core/export/.../MistakePaperDraftStore.kt`：**T3/T5**。
- `core/export/.../MistakePdfPaperEdit.kt`：**T3/T5**。
- `core/data/.../backup/AndroidBackupRepository.kt`：**T4/T5**。
- `core/data/src/androidTest/.../backup/BackupRestoreInstrumentedTest.kt`：**T4/T5**。

### 6.3 他线在飞与共享区段（不属于本批六实施轨，勿混提 / 勿覆盖）

- 计划 §0 称他线在飞 **81 个文件**（其中若干共享文件本批也需定点改目标区段），点名含 `RoomModelTaskRepository`/`OpenAiModelProtocol`/`TutorSessionPanel`/`KernelWave0SchemaContractTest`/`MistakePdfPagePlanner`/`SecondaryScreens`/`SmartMistakeBookRoot`/`BatchImport*`/`MistakeDetailRoute` 等（mtime 10-14/10-16）。
- 本批规避：**不碰 `MistakePdfPagePlanner`**（T3 明确未改；工作树中该文件的修改来自他线）、不碰 `TutorSessionPanel`、不碰 `OpenAiModelProtocol`、不做 schema 契约测试改动；`RoomModelTaskRepository` 按 T1 材料「原文件未动」处理（工作树中该文件仍有他线 hunk，提交整份时需注明）。
- 共享文件中的他线/跨轨 hunk（材料点名）：
  - `RoomCaptureWorkflowRepository.kt` 保留 KD-54 `admittedWrite`/`BackupWriteGate`/`CleanImageRequest`、A8 空源、KD-42 等区段。
  - `RoomBatchImportRepository.kt` 保留 KD-42/`BatchImportDraftState` 他线 hunk。
  - `CaptureScreen.kt` 保留 T6 `initialSharedSourceUri`/`onInitialSharedSourceConsumed`，同时 T2 加手动建题分支。
  - `RoomTutorToolRunner.kt` 保留 T1 `parentOperationId`。
  - `RoomStudyDatabase.kt` 保留 T1 `reserveModelTaskOperationDispatch`。
  - `CaptureViewModelTest.kt` 保留 T4 备份让路用例。
  - `AndroidBackupRepository.kt`/`BackupRestoreInstrumentedTest.kt` 含 T4 KD-53/54 与 T5 KD-47 两轨区段。
  - `SecondaryScreens.kt` 含他线 UI 段落（KD-47 旧文案 `:735` 位于他线未提交 hunk 的 `+` 行内）。
  - `SmartMistakeBookApplication.kt` 含他线 6B-2 区段；`SmartMistakeBookRoot.kt` 含 6B-2/分享/提醒等他线区段；`MainActivity.kt` 含他线 `onResume` 提醒刷新。
- 结论：**整份提交这些共享文件时必须注明含他线区段**；不要按单轨 hunk 挑拣，也不要用本轨版本覆盖他线新改动。

### 6.4 提交口径要点（各轨/材料声明汇总）

- **本批不改 schema**：T1 明确「不改 schema、不改模型请求 schema 17」；T2 复用 schema 67 已放宽的 `problem_draft.source_asset_id`；T4 只改代码常量/校验面；无新增 `67.json` 类产物。
- **自检脚本**：
  - T1 `tools/check_braces_t1.py` 与 `tools/_t1_brace_check.py` 已删除，不进提交。
  - T2/T3/T4/T5 的自检脚本均在宿主 `Temp`（`C:/Users/听云/AppData/Local/Temp/...`），不在仓库。
  - T6 `tmp/t6_selfcheck.py` 在仓库内且未跟踪、未被 `.gitignore` 命中；提交前需决定是否纳入（材料未声明）。
  - `.zcode/`（计划、运行脚本）被 `.gitignore:44` 忽略，不进提交。
- **docs/台账**：材料中六实施轨均未声明改 `docs/known-defects.md`、主线、台账；KD-52~55 的关账/登记、主线/台账同步归 T8 收尾轨（材料未附，§8 留空）。
- **提交状态**：HEAD=`e76ffd28`；本记录者未见本批任何提交；六轨均声明未做 git 写操作；工作区改动保持原样（含他线 81 文件与未跟踪产物）。

### 6.5 本记录者抽查记录（行号漂移与现状，供提交者对照）

- 逐字/语义符合：`FigureGenerationPolicy.kt:34/:68/:126`；`DefaultFigureGenerationCoordinator.kt:208/:255`；`RoomFigureDispatchLedger.kt:35/:47/:99`；`ModelTaskDatabasePort.kt:201`；`RoomStudyDatabase.kt:1404`；`SmartMistakeBookApplication.kt:378/:396/:414`。
- 逐字/语义符合：`ProblemDraftTransactionDao.kt:411-417`、`loadDraft` 的 `:1141-1160`；`RoomStudyDatabase.kt:842-849`；`RoomCaptureWorkflowRepository.kt:229-266/:590-599/:949-1038`；`ManualTranscriptionCaptureInstrumentedTest.kt` 3 个 `@Test`；`MistakePdfMissingSourceInstrumentedTest.kt` 1 个。
- 逐字/语义符合：`MistakePdfPaperEdit.kt:27/:86/:96/:106/:203/:376`；`MistakePaperDraftStore.kt:25/:60/:63/:86`；`MistakeExportJobRunner.kt:44/:132`；`ExportPdfWorker.kt:295/:348/:390`；`TutorExportSheet.kt:74/:290/:556`；`TutorExportEditSheet.kt:49`。
- 逐字/语义符合：`SmbkArchiveCodec.kt:129/:161/:238/:792`；`BackupWriteGate.kt:88/:123/:135/:187`；`AndroidBackupRepository.kt:75/:77/:162/:325`；`DocxImageSource.kt:34-67/:44/:48/:49`；`DocxMistakeRenderer.kt:97-151`；`MistakePdfEligibility.kt:144-159`；`ProblemOrganizationDao.kt:239-244`（KDoc 起于 224）。
- 逐字/语义符合：`AndroidManifest.xml:27/:35/:41`；`MainActivity.kt:22/:38/:59-88`；`SmartMistakeBookRoot.kt:227-266/:737-775`；`SmartMistakeBookRoutes.kt:40`；`CaptureSharePolicy.kt:25/:34/:37/:40/:104-179`；`CaptureShareIntake.kt:43-235`。
- **行号漂移（内容均在，引用以实测为准）**：T1 材料记 `ImageGenerationChannel.kt:29/:44`、`:62/:69`，实测 `:44/:63/:69`；T1 材料记 `RoomStudyDatabase.kt:1399`，实测 `:1404`；T3 材料记 `ExportPdfWorker.kt:321/:350/:389`，实测 `:348/:390`；T6 材料记 manifest `:33-43`，实测 action 在 `:35/:41`；T6 材料称 `CaptureShareIntakeInstrumentedTest` 7 个 `@Test`，实测 8 个（7 个表达式体 + 1 个块体）；T4 材料摘要称 13 个数据库写入口、证据称 17 个 gate 点，实测 17 行 `admittedWrite(`（1 定义 + 16 调用）。
- **强门禁与单测门的矛盾**见 §4；**设备自述与核对的三条 `holds=false`**见 §3.5；这些不是行号漂移，不能按「内容存在」放过。

---

## 7. 待裁定 / 建议登记

1. **KD-53 恢复崩溃（最高优先）**：两次 `SQLException: Connection pool is closed` / `PassthroughConnectionPool.useConnection` 在 main 线程崩溃并触发启动回滚，用户终态是「备份恢复没有完成」。KD-53 只满足「校验」半边，**不能关账**；建议先修恢复流程的连接池/生命周期问题，再在设备上用同一 63,467,871 B 归档复验「校验→恢复→重启→数据一致」全链。
2. **KD-54 不能关账**：快照期间仍有 9 条 `unable to grant`、最坏 90.014s；「备份期间做一次采集保存」未执行（UI 屏蔽）。建议在备份期间用非 UI 写入口（或临时允许排队写）实测「采集保存完成且无分钟级饥饿」，否则关闭条件不成立。
3. **门禁证据复核**：① 单测门脚本命令含 `core:database`、执笔清单含 `feature:tutor` 且称 core:database 不在集合——请运行方明确实际执行集并附原始命令/退出码；② 强门禁首轮 1/1、末轮执笔 0/0，但末轮原始退出码未附——请复跑两段并保留 `at2`/`as2` 的 exit code，而不是只保留 `strongVerdict` 转述。
4. **T6 测试类修复**：`CaptureShareIntakeInstrumentedTest` 的 7 个表达式体 `fun ... = runBlocking { ... }` 被 JUnit4 判为「should be void」，整类 0 用例执行。建议改为块体 `fun x() { runBlocking { ... } }`（或 `@RunWith` 兼容写法），再在设备上跑全部 8 例；这是测试缺陷，不应记到产品头上。
5. **T6 成功分享路径补验**：shell 无法给 MediaStore URI 授权，人工 `am start` 只走到「如实拒绝」；建议用系统分享 UI 从相册/文件管理器真实分享一次，覆盖单图、多图、PDF 三条成功路径，并确认预拷件清理。
6. **6B-2 设备入口**：编辑卷面只在模型 `START_EXPORT` 卡打开的 `TutorExportSheet` 内，模型不可达时无本地入口；「导出成果」hub 未加编辑动作。请裁定是否补本地入口（或明确 6B-2 只在模型可用时可达）。
7. **设备数据/凭据事故**：`connectedAndroidTest` 的卸载步骤删除了 app 数据（386,736,128 B DB + 模型配置）。建议后续设备门禁默认 `install -r`/避免 `connectedAndroidTest` 的卸载，或在跑前备份 app 数据；模型配置需重新播种。宿主 `C:/Users/听云/.config/opencode/opencode.json` 的 `provider.options.apiKey` 为明文，建议改环境变量/凭据库并轮换（本记录不含值）。
8. **T2 TUTOR 手动空源**：目前手动建题只对 `LIBRARY` 开放，TUTOR 来源被 repository/DAO 拒绝。请裁定是否需要「讲题会话手动空源题」；若需要，另行设计 provenance/会话绑定，不在本批 schema 范围内硬塞。
9. **T1 历史 `attachedImages` 行为变化**：缓存缺失时旧链不再自动生成，而是 fail-closed；这是有意收紧（无预留不得发 HTTP），但会影响旧缓存缺失场景。建议在 WG-05 全量接线前给出用户可见说明或迁移策略。
10. **T1 账本粒度**：未新增 `remoteKind` 专用审计列，只有 operation `dispatch_count` 与 `clean_redraw_task.attempt_count`；若需要按图像后端/方法统计，需下一波 schema 或审计设计，不应在本波硬塞。
11. **T4 写门覆盖**：`BackupWriteGate` 只接采集、恢复、孤儿清理与备份自身；评审/讲题/模型任务/批量导入/删除全部数据等 DAO 直写仍可能在快照期间落 room3 连接池。建议按真实写入口清单补齐，或在 KDoc/关账条件中明确「未覆盖」。
12. **T4 压缩归档 1 GiB 边缘**：`create` 无法预知压缩后体积，接近 1 GiB 且不可压缩的库可能「成包成功但校验被压缩包上限拒绝」。建议在 UI/文档中如实说明，或把压缩归档上限与数据库预算联动重新设计。
13. **T5 KD-47 UI 文案**：`SecondaryScreens.kt:735` 仍是旧句，位于他线未提交 hunk 的 `+` 行；待他线落定后改为「设置可能只写入了一部分」类如实措辞，并补 UI 断言。
14. **T8 收尾与 KD 状态**：本批材料未附 T8；KD-52~55 的关账/部分落地、主线/台账同步、提交/推送结果均未在材料中记录。建议收尾轨逐条登记：KD-52（A8 生产者已落地）、KD-53（校验通过/恢复未达）、KD-54（门已接/饥饿未解除）、KD-55（docx 复核已落地）、KD-47（日志/测试已改/UI 未改）。
15. **材料补齐**：`check` 第 21 条 claim 之后、`review.findings`、`check.corrections` 未随材料附上；如需评估对抗性复核发现（ledger 旁路、空源下游、zip bomb、编辑篡改原题面、分享路径门），需向运行方索取完整材料或读运行台账。

---

## 8. 收尾补记（主会话，2026-10-19）

**提交**：`759148ba` —— 六轨全部（记录 §6.1 的 107 路径剔除 `tmp/t6_selfcheck.py`，共 **106 文件**；`MistakePdfPagePlanner`/`RoomModelTaskRepository`/`OpenAiModelProtocol`/`TutorSessionPanel`/schema 契约测试等他线文件不在清单、未纳入；`SecondaryScreens`/`SmartMistakeBookRoot`/`MainActivity` 等共享文件按 §6.3 整份纳入并注明含他线区段）；docs 提交随后。

**门禁实跑复核（本记录 §4 的矛盾以本节为准）**：主会话按脚本原命令重跑两段——① 单测门（脚本七模块 **+** `:feature:tutor:testDebugUnitTest` 消歧）→ **exit=0**（全 UP-TO-DATE，沿用批内绿色产物）；② 强门禁（androidTest 编译四模块 + `:app:assembleLocalFirstDebug`）→ **exit=0**。即：材料字段的 `1/1` 是**首轮**值、修复环末轮已修好（`strongVerdict` 的「全 0」为真），导出字段陈旧。**集合矛盾处置**：以脚本命令为准（含 core:database、不含 feature:tutor）；本轮补跑 feature:tutor 消歧，两集合均绿。

**收尾修复（主会话，均已验证）**：
- **[high] 既有回归 `CaptureWorkspacePolicyTest` 空白照片段落误放行**：根因=照片草稿的块 provenance 也是
  `USER_TRANSCRIPTION`（`CaptureWorkspacePolicy.kt` editBlock 的 A8 注释），`editSingleParagraph` 的放行判据只
  看了 provenance、缺**空源**一维。修法：判据加 `evidence.sourceAssetId.isBlank()`（照片草稿 asset-1 ≠ 手动草稿
  空串，fixture 对得上）；`:feature:capture:testDebugUnitTest` **142/142 绿**。
- **[medium] `CaptureShareIntakeInstrumentedTest` 整类 0 用例（initializationError）**：7 个 `@Test` 表达式体
  末值非 Unit → JUnit 拒类。修法：`runBlocking` → `runBlocking<Unit>`（7 处）；**设备复跑 8/8 绿**（emulator-5556）。
- **[low] 两处机械插入吞换行**（`ModelTaskTransactionDao.kt:387`、`SmartMistakeBookApplication.kt:758`）已复原；
  `:core:database:compileDebugKotlin` + `:app:compileLocalFirstDebugKotlin` 绿。
- **`feature:capture` 单测此前不在门禁集合**（本批新改的模块）——已纳入本节实跑；后续批次门禁集合应含它。

**登记（主会话）**：**KD-52 关闭**（A8 生产者落地 + 设备全链 PASS）；**KD-55 关闭**（docx 图片 sha/长度复核落地）；
**KD-53 更新**（校验侧设备关闭、恢复侧未达、staging 写盘次序低危并入）；**KD-54 更新**（让路仅覆盖两仓库、
设备 9 条/90s 未关账）；**KD-47 再核**（日志已改、UI 文案仍在他线在飞文件内）；**新增 KD-56（high：大库恢复
main 线程 `Connection pool is closed` 崩溃 + 回滚，两次复现）/ KD-57（分享重试死路）/ KD-58（attachedImages
静默失败）/ KD-59（预留防线偏弱，low）/ KD-60（图像与工具环共享派发预算，low）**。

**仍未做 / 未达（照录）**：设备模型项整块未达（**事故**：`:app:connectedLocalFirstDebugAndroidTest` 的 AGP
卸载步骤删掉 app 数据，含模型配置；DNS 已用 `iptables nat OUTPUT DNAT 10.0.2.3:53→8.8.8.8` 修复——根因=模拟器
内建 DNS 代理）；6B-2 编辑→导出设备链未达（入口只在模型 START_EXPORT 卡里）；KD-53 恢复崩溃（KD-56）；
4E JS/QuickJS 与模型侧 method（WG-02/04 下一波）；6C-2 未实施。

**共享树归因矛盾（照录）**：`MistakePdfPagePlanner.kt` 的选项行去后缀与两条 golden 摘要重登记——计划列为
「他线在飞、本批不碰」，但 golden 测试文件在本批清单里；golden 注释自称 2026-10-16 UV-10 他线修复。**提交时
该组文件按他线改动对待**（不认领、不重登记），批次记录与主线不据此声称「6B-2 未编辑逐位不变」已覆盖该段。
