# 批次 17 记录（2026-10-22）：4E WG-08 / WG-06 / WG-07 + PRD-05/06/08 + 6C-2 重算

> 记录时点：2026-10-22。本文件由记录员在共享工作树 `D:\smart mistake book` 只读核对源码、计划、基线、工单门禁/设备材料后写入。
> **本记录员未运行 Gradle、未运行 adb/设备用例、未运行任何 git 命令、未改代码。** “门禁 exit=0”“设备 99 例 97 过”等结论来自工单给定的脚本/设备轨材料，本记录员未复跑；凡无当前执行证据的验收一律写 UNVERIFIED。
> 计划：`.zcode/plans/plan-2026-10-21-batch-17-wg08-wg06-wg07.md`（下称“计划”）。基线：`docs/research/2026-10-22-batch-17-baseline.md`。
> 记录边界：本文件不代提交、不代修他线语义、不关闭任何未达验收；已知缺陷只按任务要求在 `docs/known-defects.md` 更新 KD-61。

## 1. 范围与基线

### 1.1 依据与范围

- 计划 §1（`:16-19`）做：WG-08 最小 schema 69 + operation/attempt 记录 + GC/备份/僵尸对账；WG-06 保存/详情角色 + 显式重绘；WG-07 PDF/DOCX 角色过滤与交付；PRD-05 设备 UI + settler 版本口径；PRD-06 空态 CTA→手动入口设备链；PRD-08 recreate 设备用例；6C-2 机器清单重算（只写文档）；收尾。
- 计划 §1（`:19`）明确不做：WG-02/03/09/10、JS/QuickJS、4E.2/3、PRD-09 真实质量、5B/5b/5c 实现、6C-2 产品实现（三项待裁定）、6B-3 新模型任务类型/请求 schema、PRD-05 真实数据启用（3D 门）、UI-00~11、KD-61 关闭。
- 顺序：P0 基线 → P1 T1a/T6/T7 → P2 T1b/T5/T8 → P3 T2a → P4 T2b/T4 → P5 T3 → P6 I1 → P7/P8 门禁修复 → P9 设备 → P10 双复核 → P11 修复环 → P12 记录（计划 `:100-101`）。
- 文件所有权：`SmartMistakeBookRoot.kt`、`SmartMistakeBookApplication.kt` 由 I1 独占；`MistakePdfPagePlanner.kt` 由 T4 整文件纳入但必须保留 UV-10 hunk；他线在飞文件（TutorSessionPanel/BatchImport*/SplitImport*/RoomCaptureWorkflowRepository/KnowledgeReviewSessionViewModel/ReviewReminderCoordinator/LearningMasteryScreen/core/ui 图片组件/OpenAiModelProtocol/RoomModelTaskRepository/docs/current/tools/kb_*/knowledge-production）只读。

### 1.2 基线事实与记录时点

| 项 | 基线（T0，2026-10-21） | 记录时点（2026-10-22） |
| --- | --- | --- |
| HEAD / origin/main | `5bc8313628f8b22dfe888fb795ae5b1bdf1fb413`（0/0） | 本记录员未运行 git；按基线仍为 `5bc83136` |
| DB schema | 68；`69.json` 不存在 | **69**：`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabase.kt:134` `STUDY_DATABASE_VERSION = 69`、`:233` version、`:305` `figureGenerationDao()`、`:396` 迁移注册；`schemas/.../69.json` 存在（358,389 字节，2026-10-21 21:43） |
| 69.json 形状 | — | 本记录员用 Python 实际读取：`version=69`、`identityHash=6633b7c2fe223041c4a2927203853f4f`、**86 entities / 2 views / 92 条 FK**；`figure_generation_operation` 索引 `(source_kind,source_id)`、`(status,updated_at_epoch_millis)`；`figure_generation_attempt` 唯一 `(operation_id,attempt_ordinal)` + `output_asset_id` 索引 |
| 请求 schema | 18 | 18（`core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/ModelTasks.kt:546-547`），本批未改 |
| 工作树 | 482 项（75 M + 407 ??），全部他线在飞 | 本批各轨自报文件清单见 §2（跨轨有共享文件，未去重）；未提交；他线改动仍在 |
| 计划勾选 | PRD 50 未勾/0 已勾 | 本批按要求**未勾选**任何 PRD/UI 项 |

### 1.3 轨与最终状态

| 轨 | 自报 | 记录员判定（记录时点） | schema |
| --- | --- | --- | --- |
| T0 基线 | 完成 | 完成（基线文档落盘） | 68 |
| T1a WG-08 schema 69 | 完成 | 代码级完成；69.json 静态形状已由本记录员复核；设备迁移 15/15 由 D1 自报；Gradle 门禁由脚本 exit=0 | 69 |
| T1b operation/attempt + 角色单源 | 完成 | 代码级完成；审计/生成 JVM 由 D1 跑过指定类 exit=0；真实 Provider 未验 | 69 |
| T2a WG-06 保存角色 | 完成 | 代码级完成；设备保存/修订 11/11 + 关库重开 1/1（D1 自报） | 69 |
| T2b WG-06 详情角色 | 完成 | 代码级完成；详情分区/旋转 0 派发设备 5/5（D1 自报，含 T3 UI） | 69 |
| T3 WG-06 显式重绘 | 完成 | 代码级完成；JVM + 设备重绘 4/4 + UI 5/5（D1 自报）；真实 Provider 未验 | 69 |
| T4 WG-07 导出角色 | 完成 | 代码级完成；JVM 15/16/21、设备 17/17（D1 自报）；UV-10 hunk 保留 | 69 |
| T5 WG-08 GC/备份/僵尸对账 | 完成 | 代码级完成；备份 32/32、大库 1/1（D1 自报）；365MiB+visual 组合未验 | 69 |
| T6 PRD-05 设备 UI + settler | **部分** | **未达标**：设备 7 例中 2 例红（good 键、旋转）；修复已落树（静态）但**修复后设备复跑未提供** | —（请求 schema 18） |
| T7 PRD-06/08 设备用例 | **部分** | 两个设备用例 D1 自报 OK(2 tests)；但用例会清空生产库，隔离缺陷未修（高） | — |
| T8 6C-2 重算 | 完成 | 文档完成；本记录员复核 69.json 数字与 §9 章节存在；三项待裁定保持 OPEN，未实现 | 69 |
| I1 集成 | 完成 | Application 装配完成（静态）；Root 未改；他线并发改 Root 的归属冲突按 I1 自报保留 | 69 |
| D1 设备轨 | 部分 | 99 例 97 过 2 红（自报）；失败为 PRD-05 真实缺陷 | 69 |

## 2. 逐轨交付

### 2.1 T0 基线

- 交付 `docs/research/2026-10-22-batch-17-baseline.md`。
- 登记：HEAD/origin/main 一致 0/0、工作树 482→483 项归属、schema 68 与 69.json 缺失、请求 schema 18、他线热区（含 `MistakePdfPagePlanner.kt:180-191` UV-10 hunk）、本批入口与不做项。
- 基线自报未运行 Gradle/设备/git 写操作；本记录员沿用其只读结论，不把基线当成当前门禁证据。

### 2.2 T1a WG-08 schema 69

- 新表/迁移：`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/FigureGenerationMigration_68_69.kt:36`（`Migration(68,69)`）、`:53`/`:77`（两表 DDL）、`:95`（唯一索引）、`:109`/`:116`/`:123`/`:131`（四条 `ALTER TABLE ... ADD COLUMN`）。
- 实体：`entity/FigureGenerationEntities.kt`（operation/attempt 两实体）；追加列：`entity/TutorConversationEntities.kt`（role/basis_revision_id）、`entity/CleanRedrawTaskEntity.kt`（operation_id）、`entity/CaptureEntities.kt`（ordinal DEFAULT 0）。
- 测试：`core/database/src/test/.../FigureGenerationSchemaContractTest.kt`、`KernelWave0SchemaContractTest.kt`；`core/database/src/androidTest/.../FullMigrationMatrixInstrumentedTest.kt`（68→69 保行/幂等、1→69 全链）。
- 本记录员独立静态复核：69.json 的版本/identityHash/86 实体/2 视图/92 FK/两张 figure 表的索引与唯一约束均与上述记录一致（Python 读取，见 §1.2）；未复跑 KSP、未跑契约/矩阵测试。
- 预算权威：`model_task_operation.dispatch_count` / `clean_redraw_task.attempt_count` 未变；两张审计表无指向预算表的外键（T1a 静态比对自报；本记录员未重跑该脚本）。

### 2.3 T1b operation/attempt 记录 + 角色单源

- 契约：`core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/FigureGeneration.kt` 增 `sourceKind/sourceId/basisFingerprint` 与 `Outcome.attemptId`；新增 `VisualRole.kt:14` 四角色、`:35` `QUESTION_SOURCE_ROLE`、`:43` `TutorFigureKind.visualRole()`、`:54` `carriesAcrossTextRevision()`。
- 状态机：`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/DefaultFigureGenerationCoordinator.kt` 以 operation PENDING→RUNNING→终态记录；取消用 `NonCancellable` 落 CANCELLED；审计计数只写不读。
- 回填：`RoomCaptureWorkflowRepository.kt:149` 已接 `onAttached`（保存期净化在产物落盘后回填 `figure_generation_attempt.output_asset_id`，比较 `CleanRedrawTaskRunner.kt:119` 默认空实现与 `:347` 调用点）；`ExplicitRedrawService.kt:162` 显式路径同样回填。
- D1 实跑（自报）：`:core:data:testDebugUnitTest --tests ExplicitRedrawServiceTest --tests CleanRedrawTaskRunnerTest --tests FigureGenerationAuditRecorderTest` exit=0，XML 计数 14/21/7、failures=0。
- 未验证：真实 Provider；operation/attempt 审计与预算不双计只有 schema 形状与静态约束，无运行时计数断言。

### 2.4 T2a WG-06 保存角色

- `ProblemDraftTransactionDao.kt:1050` 在 `commitTutorSession` 事务内调用 `:1094 copyAssistantVisualLinksToRevision`：只读本会话助手消息、跳过 role=NULL、按已有 QUESTION_SOURCE 去重、按消息 ordinal 续排（轨自报，本记录员未逐行复读该函数体；事务归属与调用点已核）。
- 文本修订白名单：`MistakeDetailDao.kt:892` `carriesAcrossTextRevision(link.role)`，只复制 QUESTION_SOURCE/SOURCE_EVIDENCE。
- 角色挂接：`DraftPort.kt:103` 默认 CLEAN_IMAGE；`RoomTutorToolRunner.kt:1184` 传 `kind.visualRole().name`；`RoomCaptureWorkflowRepository.kt` 未改。
- D1 实跑（自报）：`TutorSessionVisualRoleDatabaseInstrumentedTest,ProblemRevisionEditDatabaseInstrumentedTest` → `OK (11 tests)`；新增 `TutorSessionVisualRoleReopenInstrumentedTest` → `OK (1 test)`（关库重开后详情仍读回三角色）。
- 未验证：历史 role=NULL 不回填（设计如此）；`basis_revision_id` 无生产写入者（见 §5）。

### 2.5 T2b WG-06 详情角色

- `feature/library/src/main/kotlin/com/tingyun/smartmistakebook/feature/library/MistakeDetailRoute.kt:1326 detailAssets()`：题目区 `filterNot TUTOR_EXPLANATION`、讲解区只收 `TUTOR_EXPLANATION`；同一 revision 多张 CLEAN_IMAGE 时最新在前（`:1333-1336`）。
- 详情 UI：题目区只渲染 `detailAssets().question`（`:1157`），讲解折叠区渲染 `.explanation`（`:1203`）；旧 CLEAN_IMAGE 标签逻辑保留。
- 读侧外溢修复：`LibraryInlineImages.kt:109-112` 改用 `displayAssets()`；`displayAssets()`（`MistakeDetailRoute.kt:1351-1363`）明确排除 TUTOR_EXPLANATION；`feature/review/.../CapturedReviewQuestionContent.kt:48-50` 的“查看原图”同时排除 CLEAN_IMAGE 与 TUTOR_EXPLANATION。新增 `MistakeDetailUiPolicyTest.kt:137-177` 钉住这两条。
- D1 实跑（自报）：`MistakeDetailRoleSectionsInstrumentedTest` → OK（题目区过滤讲解图、默认折叠、旋转恢复 0 写 0 派发）。
- 未验证：设备端“0 派发”只在 UI 用例中断言 repository 写为 0，未做生产库行数计数。

### 2.6 T3 WG-06 显式重绘

- 新 `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/capture/ExplicitRedrawService.kt`：命令含 `problemRevisionId/expectedRevisionId/role/reason`；复用 `clean_redraw_task` 确定性 taskId、尝试上限、`FigureDispatchParent.CleanRedrawTask` 预算父操作；forced 路径跳过分类/TEXT_ONLY；current-revision CAS；晚到 SUPERSEDED；失败码；详情按钮/进行中/失败/重试；启动 `resumePending` 只接 `explicit-redraw-op:` 行。
- 并发领取修复（复核后落树）：`ExplicitRedrawService.kt:253-256` 对 `PENDING && attemptCount > 0` 直接返回 Accepted，不再 launch 第二个执行者；`CleanRedrawTaskDao.kt` 的 `claimAttempt` 只允许 PENDING 在 `attempt_count = 0` 或已过 stale 截止时被领取。本记录员读码确认，未跑并发用例。
- 装配：I1 在 `SmartMistakeBookApplication.kt` 用同一 `cleanImageGenerator` 构造服务并注入 `MistakeDetailRepository`；启动时单独 `resumePending`。
- D1 实跑（自报）：`CleanRedrawTaskInstrumentedTest,FigureGenerationReconcilerInstrumentedTest,GeneratedFigureMetadataInstrumentedTest` → `OK (4 tests)`；`MistakeDetailExplicitRedrawInstrumentedTest` → OK（仅用户动作派发、RUNNING/失败/重试/成功展示）。
- 未验证：真实 Provider 成功率/费用；凭证门已改为请求前 `isAvailable()` 预检（§5.3），但真实调用未验。

### 2.7 T4 WG-07 导出角色

- 题目区：`core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfEligibility.kt:226-233` `questionImageAsset` 按 CLEAN_IMAGE 优先、其次 QUESTION_DIAGRAM，多张 CLEAN_IMAGE 取创建时间最新；`:243-250` 解析区只收 TUTOR_EXPLANATION 且仅 `includeSolution=true`。
- 指纹：`MistakePdfEligibility.kt` 的 `inputSha256` 升为 `mistake-export-v3`，含题目图 role/uri/sha 与逐张解析图 role/uri/sha（T4 自报）；`MistakePdfBatchEligibility.kt` 同步 `mistake-batch-v3`。
- 版式/渲染：`MistakePdfPagePlanner.kt` 题目区过滤解析图块、解析图独立成区；`DeterministicMistakePdfRenderer.kt` 把解析图 URI 纳入尺寸表（拿不到=GENERATED_PDF_INVALID）；`DocxMistakeRenderer.kt:71` 题目区排除 `TUTOR_EXPLANATION`，`:120`/`:151`/`:308` 缺必图整份 fail-closed、不写半份。
- 卷面副本/草稿：`MistakePdfPaperEdit.kt` 保留题图 role 与 `solutionImages`（`includeSolution=true` 时聚合）；`MistakePaperDraftStore.kt` 旧草稿缺省兼容。
- 完成通知修复（复核后落树）：`ExportPdfWorker.kt:475 mistakeExportSkippedNotice` 现在接受 `solutionSectionRendered`，真排出讲解图时不再说“解析区暂不可用，已跳过”；`ExportPdfWorkerTest.kt:216-224` 已加对应断言。
- UV-10：本记录员确认 `MistakePdfPagePlanner.kt` 仍存在题面选项不打印已选/不可选后缀的 hunk（基线 `:180-191`，当前文件同段保留）；T4 自报整文件纳入且保留。
- D1 实跑（自报）：`MistakeExportVisualRoleInstrumentedTest,MistakePdfMissingSourceInstrumentedTest,MistakePdfCleanImageExportInstrumentedTest,MistakePdfExporterInstrumentedTest` → `OK (17 tests)`；JVM `MistakeExportVisualRoleTest/DocxMistakeRendererTest/MistakeExportJobRunnerTest` exit=0，XML 15/16/21。
- 未验证：`includeSolution=false` 时卷面副本不含讲解图（代码如此；注释声称“开关打开即可用”与代码不符，见 §5 低危残留）；PDF 系统字体/时间戳逐字节 golden 未做（T4 自述口径）。

### 2.8 T5 WG-08 GC / 备份 / 僵尸对账

- GC 引用：`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/dao/PendingCaptureDao.kt:73` 第五支 `NOT EXISTS (... figure_generation_attempt.output_asset_id ...)`；KDoc（`:30-42`）写明 SUCCEEDED/RUNNING/带产物失败行都 pin，无产物失败/取消不 pin。备份/恢复用同一补集口径自动纳入（`RoomStudyDatabase.kt` 的 readReferencedCanonicalAssets，T5 自报）。
- 对账：`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/FigureGenerationReconciler.kt:51 reconcileStaleRunning`；`:64` `staleBefore = max(进程启动时刻, now-30min)`；`:65-67` 截止不在过去时直接返回 0；`:85` 诊断前缀 `RECONCILED_STALE_RUNNING` 写明“产物保留、不自动重派”。端口 `FigureGenerationAuditPort` 只有“读僵尸+标 FAILED”，无 INSERT/派发入口。
- 用例：`FigureGenerationReconcilerTest`（JVM）、`FigureGenerationReconcilerInstrumentedTest`、`BackupRestoreInstrumentedTest` 新增 68→69 恢复/GC pin×2/备份闭包/缺产物拒绝（T5 自报行号）。
- D1 实跑（自报）：`BackupRestoreInstrumentedTest` → `OK (32 tests)`；`BackupLargeDatabaseScaleInstrumentedTest` → `OK (1 test)`（365MiB，未缩断言）。
- 未验证：365MiB 库 + visual 资产组合规模；对账与 capture 对账的真实启动时序；审计 `finishAttempt/finishOperation` 无 `status='RUNNING'` 守卫（T5 自报残余，见 §5）。

### 2.9 T6 PRD-05 设备 UI + settler 版本口径

- settler 口径已改为：`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/TutorJudgedReviewSettler.kt:100-110` 用 `practiceUnitId` 定位队列项、`:113` 用 `session.currentOrdinal == queueItem.ordinal` 判队首；版本只作写事务 CAS。KDoc（`:101-106`）明确 undo 后版本与 ordinal 解耦、重放可能晚于推进。
- 新增设备 UI：`app/src/androidTest/kotlin/com/tingyun/smartmistakebook/ReviewSelfReportDeviceUiInstrumentedTest.kt` 7 例（三键、双击、旋转、提交中断、undo→重评、无模型已存答案），使用按测试命名的隔离库（`:129-160`）。
- D1 实跑（自报）：7 例 **2 失败** —— `goodKeyRecordsTheSelfReportAndShowsTheStoredAnswerWithoutAModel`、`rotationKeepsTheRecordedReceiptAndUndoAction` 均命中 `review_reveal_write_failed`；根因 = 自评写 `self-report-snapshot` 快照建 presentation，随后 reveal 用机判快照写同一 presentationId，`AttemptTransactionDao.kt:700-705` 抛 `ImmutablePayloadConflictException`。
- 修复已落树（静态）：`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/StudyPracticeUnitFacts.kt:162-178` 改为“可机判题复用机判快照身份”，只有不可机判题才落 `self-report-snapshot`（`:373` 常量）。本记录员读码确认口径已改；**修复后的设备/JVM 复跑结果未提供 → PRD-05 设备验收仍 UNVERIFIED**。
- 轨自报状态“部分”，与此一致。

### 2.10 T7 PRD-06 / PRD-08 设备用例

- 新增 `app/src/androidTest/kotlin/com/tingyun/smartmistakebook/Prd06EmptyLibraryCaptureCtaInstrumentedTest.kt`、`Prd08TutorDraftRecreationInstrumentedTest.kt`，未改生产代码。
- 断言：空库 CTA→LIBRARY 手动建题可用且 `model_task=0`；Tutor 大厅输入草稿→会话行/任务均 0→`recreate` 后草稿恢复→仍 0。
- D1 实跑（自报）：两用例 `OK (2 tests)`。
- **隔离缺陷（高）**：两用例 `@Before` 调 `application.studyDatabase.clearAllData()`（`:47`、`:50`），`BackupRestoreInstrumentedTest` 的 68→69 用例还会 `deleteDatabase(DEFAULT_DATABASE_NAME)`；`RoomBackupSupportStore.clearAllData` 会删除全部表。非专用设备跑一次即清空用户数据；D1 已在 `emulator-5554` 实跑。用例 KDoc 自认“仅用于专用测试设备”，但没有隔离库或设备守卫。本批未修。

### 2.11 T8 6C-2 机器清单/闭包重算

- 只改 `docs/research/2026-10-17-6c2-content-selection-design.md`（+1246 行，T8 自报）；新增 §9.0–§9.12（本记录员确认章节存在：`:1013` 起，`:1531` 三项待裁定，`:2195` 复跑与校验）。
- 结论（T8 自报并落盘）：schema 69 = 86 表/2 视图/92 条 FK；类别闭包 5 条；裁剪顺序 78 项；软引用 20 条（改为 no-new-dangling）；`closureVersion=2`；空库全选 digest golden `d6ec73ac…`。本记录员独立读取 69.json 复核 86/2/92 与 identityHash 一致。
- 共享 operation/attempt 按宿主与资产引用分行；三项产品裁定（仅图片 mask=8、空选择 mask=0、派生索引恒裁）保持 **OPEN**，未实现、未勾 6C-2。
- 未验证：空库 digest 为 Python 计算，非 Android SQLite/真实备份往返；`PROBLEM_REVISION`/`CAPTURE_PARSE` 无生产写入者。

### 2.12 I1 集成

- `SmartMistakeBookApplication.kt`：T1b recorder 由 `DefaultFigureGenerationCoordinator` 工厂内建；T5 启动对账；T3 `ExplicitRedrawService` 装配并注入 `MistakeDetailRepository`；启动 `resumePending`。
- Root：I1 自报无需新增回调/路由，未改 Root；但工作期间 Root 被外部并发写入（`consumeWindowInsets`、padding 26→16、底栏 height/weight 5+/5-），I1 未回滚、未审查其意图。归属冲突按 I1 blocker 保留给主会话裁决。
- 本记录员未运行 Gradle，装配只有静态签名/调用点核对。

### 2.13 D1 设备轨

- 环境：`emulator-5554`；只用 `assembleDebugAndroidTest` + `adb install -r` + `am instrument`，未用 connectedAndroidTest（计划 §5 要求）。
- 总计 **99 例 / 97 过 / 2 红**（D1 自报）：迁移 15/15、保存/修订 11/11、关库重开 1/1、重绘 4/4、详情+显式重绘 UI 5/5、WG-07 17/17、备份 32/32 + 大库 1/1、T7 2/2、T6 7 例中 2 红。
- D1 另跑 JVM：core:data 指定 3 类 exit=0（14/21/7），core:export 指定 3 类 exit=0（15/16/21）。
- 未验证：365MiB+visual 组合；真实 Provider；修复后的 PRD-05 设备复跑；设备测试会清空生产库（§2.10）。

## 3. 门禁

| 门 | 结果 | 证据边界 |
| --- | --- | --- |
| 单测门（11 模块 `--continue`） | **第 3 轮 exit=0** | 工单给定，脚本串行执行；本记录员未运行 Gradle，未读取整轮 XML 汇总 |
| 强门（六模块 androidTest 编译 + `:app:assembleLocalFirstDebug`） | **第 1 轮 exit=0** | 同上 |
| T1a 契约/迁移矩阵（JVM） | 未由本记录员执行 | 脚本门覆盖；D1 只跑了设备迁移矩阵 |
| D1 指定 JVM 类 | core:data 14/21/7、core:export 15/16/21，均 failures=0 | D1 自报（`--tests` 指定类，不是整轮） |
| 设备编译/安装 | `assembleDebugAndroidTest` exit=0、`adb install -r` Success | D1 自报 |
| 真实 Provider | **UNVERIFIED** | 无凭据、未调用 |

结论：工程门与设备门按工单材料均无红；但 T6 设备验收 2 红，且修复后未复跑，因此**不能把“门禁 exit=0”写成“本批全部验收通过”**。

## 4. 设备

- 主跑设备：`emulator-5554`（D1 自报另有 5562 在线但未用）。
- 覆盖：迁移 1→69/68→69（15/15）、WG-06 保存角色/关库重开/重绘/详情 UI、WG-07 角色过滤与像素层断言、T5 GC/备份/僵尸对账、T7 空态与 recreate、T6 PRD-05 7 例。
- 红点：T6 的 good 键与旋转 2 例，界面显示 `review_reveal_write_failed`；根因是 presentation 快照冲突（`AttemptTransactionDao.kt:700-705`）。
- 修复后状态：自评快照口径已改（`StudyPracticeUnitFacts.kt:162-178`），但**没有修复后的设备复跑证据**；相关 UI 断言（`ReviewSelfReportDeviceUiInstrumentedTest.kt:187`、`:267`）仍以旧结果记为失败。
- 设备隔离风险：PRD-06/PRD-08 用例清空生产库（§2.10），`BackupRestoreInstrumentedTest` 68→69 用例删除默认库；这是**高**风险残留，下批必须先修再跑。
- 未验证：真实 Provider；365MiB 库 + visual 组合；进程强停（PRD-08 只覆盖 Activity 重建）。

## 5. 独立核对 / 对抗复核

工单给出两份只读复核（独立核对、对抗复核）；本记录员在此摘录结论并按**当前工作树代码**逐条核对处置状态。两份复核本身未运行 Gradle/adb。

### 5.1 独立核对结论

- 静态主体与代码一致：schema 69/迁移/旧行、operation/attempt 只审计、保存复制同事务、role 分区、显式重绘 CAS/SUPERSEDED、导出角色、GC pin、PRD-05 settler 口径均通过其只读核对。
- 但不能给“批次 17 全绿”：PRD-05 设备红、T1b 回填声明与代码不符（当时）、HEAD 部分提交不一致、跨页面角色泄漏。

### 5.2 对抗复核结论

- 主路径静态成立；提出 4 个高风险：设备用例清库、显式重绘在途重复领取、同 revision 多 CLEAN_IMAGE 无“当前”判据、PRD-05 设备红；另有若干中/低危（读侧讲解图、导出通知、凭证门、审计假成功、计数重置、惰性列、T6 文件归属、卷面讲解图）。

### 5.3 逐项处置（当前工作树）

| 复核发现 | 级别 | 当前状态（本记录员静态核对） | 证据 |
| --- | --- | --- | --- |
| 设备用例清空生产库 | 高 | **未修** | `Prd06EmptyLibraryCaptureCtaInstrumentedTest.kt:47`、`Prd08TutorDraftRecreationInstrumentedTest.kt:50` 仍调 `clearAllData()` |
| 显式重绘 PENDING 并发重复领取 | 高 | **修复已落树**；并发复跑未提供 | `ExplicitRedrawService.kt:253-256`；`CleanRedrawTaskDao.kt` claim SQL 限定 PENDING 仅在 `attempt_count=0` 或 stale 时领取 |
| 同 revision 多 CLEAN_IMAGE 无“当前” | 高 | **修复已落树**；新用例已写 | `ProblemDraftTransactionDao.kt:885-896` 先删除同角色旧链接再插入；`MistakeDetailRoute.kt:1333-1336`、`MistakePdfEligibility.kt:222-233`、`MistakeDetailUiPolicyTest.kt:156-178` 均按最新选图 |
| PRD-05 设备 2 红 | 高 | **修复已落树（快照复用）**；设备复跑未提供 → 验收仍 UNVERIFIED | `StudyPracticeUnitFacts.kt:162-178`；`AttemptTransactionDao.kt:700-705` |
| 书库卡片/复习“查看原图”把讲解图当题图 | 中 | **修复已落树** | `MistakeDetailRoute.kt:1351-1370`（displayAssets 排除讲解图）、`CapturedReviewQuestionContent.kt:48-50`、`LibraryInlineImages.kt:109-112`；测试 `MistakeDetailUiPolicyTest.kt:137-177` |
| `sheetSourceAsset` 回退到讲解图 | 中 | **修复已落树** | `SmartMistakeBookDestinations.kt:641-653`：CLEAN_IMAGE 取最新，否则 QUESTION_SOURCE→SOURCE_EVIDENCE→QUESTION_DIAGRAM，最末兜底仍**排除 TUTOR_EXPLANATION** |
| 导出完成通知固定说“解析区暂不可用” | 中 | **修复已落树** | `ExportPdfWorker.kt:475 mistakeExportSkippedNotice(layout, solutionSectionRendered)`；`ExportPdfWorkerTest.kt:216-224` |
| 未配置凭证时显式重绘仍建行/耗额度 | 中 | **修复已落树**（请求/续跑前先问 `isAvailable()`，无凭证直接 Unavailable、不建行） | `ExplicitRedrawService.kt:187-193`、`:295`、`:328-336` 调 `imageCapabilityAvailable()` → `ConfiguredCleanImageGenerator.kt:78-84 isAvailable()`（`resolveImageCredential`，不发送） |
| 审计先写 SUCCEEDED、产物后落盘 | 中 | **修复已落树**（启动对账第二段把“SUCCEEDED 且无产物”纠正为 FAILED；本进程内失败要等下一轮对账/重启） | `FigureGenerationReconcileDao.kt:113-167`（SUCCEEDED + output NULL + 结束早于租约）；`DefaultFigureGenerationCoordinator.kt:438-446` KDoc；`FigureGenerationReconciler.kt:20-28` |
| 保存期净化 attempt 产物未回填 | 低 | **修复已落树** | `RoomCaptureWorkflowRepository.kt:146-156` 的 `onAttached` 调 `attachFigureGenerationAttemptOutput` |
| operation 审计计数每次重置 1/1 | 低 | **口径未明确**（KDoc 称“操作结束时的事实”，重试复用同一 operationId 时会覆盖为最后一次的 1/1，不累计） | `DefaultFigureGenerationCoordinator.kt:405-435`、`FigureGenerationDao.kt` 的 `finishOperation` KDoc 与 SQL |
| `tutor_message_source_asset.basis_revision_id` 惰性列 | 低 | **仍未使用** | 实体 `TutorConversationEntities.kt` 定义列；生产写入/读取均无（grep 只命中测试与无关分类字段） |
| T6 自报文件含无 diff 的 settler | 低 | **未复核**（本记录禁止 git） | 对抗复核材料：`git status` 对该两文件无输出、最近提交为 `f2adf825` |
| 卷面副本讲解图仅 `includeSolution=true` | 低 | **未修** | `MistakePdfPaperEdit.kt:244-248`；Root 原生编辑固定 `MistakePdfLayout.DEFAULT`（`SmartMistakeBookRoot.kt:1131`） |
| 365MiB+visual 组合未跑 | 低 | **未跑** | D1 只跑 `BackupLargeDatabaseScaleInstrumentedTest`（纯 DB 膨胀） |
| 独立核对/对抗复核/D1 与门禁均为自报 | 低 | 记录边界 | 本记录员只读复核静态代码，未运行 Gradle/adb/git |

### 5.4 记录员结论

- 本批主体（schema 69、WG-06/07 角色链、GC/备份闭包、6C-2 文档）静态成立，工程门/设备门按给定材料无红。
- **不能声明“批次 17 全绿/可关闭”**：PRD-05 设备验收在修复后没有复跑证据；设备测试清库是**高危未修**；审计假成功/凭证门已有对应修复代码（静态），计数口径、惰性列、卷面副本等低危仍在；**所有修复后的执行证据都缺失**。
- 两份复核提出的高危项中，3 项已有对应修复代码（重复领取、多 CLEAN_IMAGE、PRD-05 快照），但**修复后的执行证据缺失**，只能记“已修（静态）”，不能记“已验证”。

## 6. 遗留与下一批

### 6.1 必须先处理（高）

1. **设备测试隔离**：PRD-06/PRD-08/68→69 恢复用例改用按测试命名的隔离库或设备守卫，禁止再对生产库 `clearAllData()`/`deleteDatabase()`；修前不得在非专用设备跑。
2. **PRD-05 修复复验**：对 `ReviewSelfReportDeviceUiInstrumentedTest` 7 例重跑（尤其 good 键、旋转、undo→重评），确认 `review_reveal_write_failed` 消失；同时跑 `TutorJudgedReviewSettlerTest` 回归。
3. **修复项的并发/设备复验**：显式重绘在途点击、旧库多 CLEAN_IMAGE、书库/复习读侧过滤，均需设备或 JVM 复跑证据。
4. **HEAD 一致性**：对抗复核指出 HEAD 含部分 T1b/T5 文件而依赖（VisualRole、端口、实体/DAO/迁移/69.json）仍为未跟踪；提交时必须整组纳入，否则 HEAD 不可编译。本记录员未运行 git，按复核材料保留。

### 6.2 中/低危

- 审计在本进程 persist/attach 失败时会短暂显示 SUCCEEDED，需等下一轮对账/重启才被纠正；`finishAttempt/finishOperation` 无 `RUNNING` 守卫（对账可先落 FAILED，理论上的晚到活协程可能覆盖——T5 自报残余）。
- 凭证门已在显式重绘请求/续跑前用 `isAvailable()` 预检（静态）；`egressAllowed` 本身仍只判配置存储存在，但能力判定已下移，需复跑确认。
- operation 审计计数按“操作结束时的事实”写 1/1，不累计；重试复用同一 `operationId` 时会覆盖为最后一次尝试的计数（KDoc 未写累计口径，低危）。
- `basis_revision_id` 无生产写入/读取，属惰性列（下一批要么接入，要么在文档中明确“预留”并给失败指认）。
- 卷面副本讲解图只在 `includeSolution=true` 进入；`MistakePdfPaperEdit.toDraft` 注释与代码不符（注释称“开关打开即可用”，实际默认编辑稿里已无引用）。
- T6 自报文件清单含无 diff 文件，提交边界需按实际 diff 重列。
- 365MiB 库 + visual 资产组合备份未验。

### 6.3 下一批入口

- 先复跑修复后的单测门/强门与 D1（PRD-05 + 隔离修复），再谈关闭 PRD-05。
- 真实 Provider 质量/费用（PRD-09）仍 UNVERIFIED；本批全部模型相关验证为 fake/夹具。
- 6C-2 三项产品裁定仍 OPEN（仅图片 mask=8、空选择 mask=0、派生索引恒裁），本批不代签、不实现。
- WG-02/03/09/10、JS/QuickJS、4E.2/3 未开工；KD-61 保持 open（见 `docs/known-defects.md`）。

## 7. 提交 / 推送结果

- **提交**：`ecb1b3e6`（79 files changed，20,687 insertions，337 deletions）。
- **推送**：`30f7a287..ecb1b3e6` → `origin/main`（`git push` exit=0）；本地 `main` 与远端一致。
- **纳入边界**：按 §2/§8 的显式文件清单分块暂存（每组 ≤6 个路径）；共享脏文件整文件纳入并保留他线 hunk：`RoomCaptureWorkflowRepository`（含保存期 onAttached 与他线 `checkNotNull`）、`MistakePdfPagePlanner`（保留 UV-10）、`SmartMistakeBookApplication`/`SmartMistakeBookDestinations`、`ProblemDraftTransactionDao` 等。未提交 `SmartMistakeBookRoot.kt` 的他线布局改动、`BatchImport*`、`docs/current/*`、`tools/kb_*`、`knowledge-production/*` 与根目录临时产物。
- **安全边界**：Mimosa 在 commit/push 前未得到完整扫描结论（`scanner_enobufs`），按兼容策略继续；**不宣称项目安全**。
- **设备副作用**：PRD-06/08 的 opt-in 用例在 `emulator-5554` 上清空了已安装 App 的数据；这是专用测试设备上的用例行为，不是产品行为。
- **提交前复跑**：单测门 11 模块 exit=0、强门 exit=0；设备复验见 §8.2。未跑真实 Provider/365MiB+visual/强停恢复，仍按 §8.3 标 UNVERIFIED。


## 附录：本记录员实际执行的只读命令（可复跑）

```bash
# schema / 69.json 形状
grep -n "STUDY_DATABASE_VERSION\|addMigrations\|FIGURE_GENERATION_MIGRATION_68_69" core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabase.kt
ls -l core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/69.json
python -c "import json; d=json.load(open('core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/69.json',encoding='utf-8'))['database']; print(d['version'],d['identityHash'],len(d['entities']),len(d.get('views',[])),sum(len(e.get('foreignKeys',[])) for e in d['entities']))"
grep -n "CURRENT_SCHEMA_VERSION\|TUTOR_VISUAL_CONTEXT_SCHEMA_VERSION" core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/ModelTasks.kt

# 角色 / GC / 重绘 / 读侧
sed -n '1,70p' core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/VisualRole.kt
sed -n '20,110p' core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/dao/PendingCaptureDao.kt
sed -n '120,160p' core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/dao/CleanRedrawTaskDao.kt
sed -n '230,275p' core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/capture/ExplicitRedrawService.kt
sed -n '860,900p' core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/dao/ProblemDraftTransactionDao.kt
sed -n '1320,1380p' feature/library/src/main/kotlin/com/tingyun/smartmistakebook/feature/library/MistakeDetailRoute.kt
sed -n '40,60p' feature/review/src/main/kotlin/com/tingyun/smartmistakebook/feature/review/CapturedReviewQuestionContent.kt
sed -n '95,130p' core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/TutorJudgedReviewSettler.kt
sed -n '155,180p' core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/StudyPracticeUnitFacts.kt
sed -n '680,710p' core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/dao/AttemptTransactionDao.kt

# 导出 / 通知 / 卷面
grep -n "questionImageAsset\|solutionImageAssets\|TUTOR_EXPLANATION" core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfEligibility.kt
sed -n '60,160p' core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/DocxMistakeRenderer.kt
sed -n '460,505p' app/src/main/kotlin/com/tingyun/smartmistakebook/ExportPdfWorker.kt
sed -n '630,665p' app/src/main/kotlin/com/tingyun/smartmistakebook/SmartMistakeBookDestinations.kt

# 设备隔离 / 写门
grep -n "clearAllData" app/src/androidTest/kotlin/com/tingyun/smartmistakebook/Prd06EmptyLibraryCaptureCtaInstrumentedTest.kt app/src/androidTest/kotlin/com/tingyun/smartmistakebook/Prd08TutorDraftRecreationInstrumentedTest.kt
grep -rn "withWriteAdmission" --include=*.kt core feature app
```

> 未执行：`./gradlew ...`、`adb`、`am instrument`、任何 `git` 命令（含只读 status）。因此本记录中的门禁/设备结果均标注来源，不冒充本记录员实跑。

## 8. 主会话收口复验（2026-10-23）

> 本节由主会话在工作流完成后实跑，覆盖 §3/§4/§5.3/§6.1 中“修复后未复跑”的结论。命令均在 `D:\smart mistake book`、`emulator-5554` 上执行；未使用 `connectedAndroidTest`。

### 8.1 工程门

| 门 | 命令（节选） | 当前结果 |
| --- | --- | --- |
| 单测门（11 模块） | `./gradlew.bat :core:model:test :core:domain:test :core:data:testDebugUnitTest :core:database:testDebugUnitTest :core:export:testDebugUnitTest :core:ui:testDebugUnitTest :feature:review:testDebugUnitTest :feature:library:testDebugUnitTest :feature:capture:testDebugUnitTest :feature:tutor:testDebugUnitTest :app:testLocalFirstDebugUnitTest --continue` | **exit=0**（BUILD SUCCESSFUL，1m43s） |
| 强门（六模块 androidTest 编译 + app 装配） | `./gradlew.bat :core:data:compileDebugAndroidTestKotlin :core:database:compileDebugAndroidTestKotlin :feature:library:compileDebugAndroidTestKotlin :feature:capture:compileDebugAndroidTestKotlin :feature:tutor:compileDebugAndroidTestKotlin :core:export:compileDebugAndroidTestKotlin :app:compileLocalFirstDebugAndroidTestKotlin :app:assembleLocalFirstDebug` | **exit=0**（BUILD SUCCESSFUL） |
| 显式重绘定向 JVM | `:core:data:testDebugUnitTest --tests ...ExplicitRedrawServiceTest` | **17 tests / 0 failures**（含新增的在途第二次点击回归） |
| 详情角色 JVM | `:feature:library:testDebugUnitTest --tests ...MistakeDetailUiPolicyTest` | BUILD SUCCESSFUL（含“最新 CLEAN_IMAGE 即当前”） |

### 8.2 设备复验（emulator-5554）

| 链路 | 命令类 | 当前结果 |
| --- | --- | --- |
| PRD-05 三键/双击/旋转/中断/undo→重评/无模型已存答案 | `am instrument ... -e class ...ReviewSelfReportDeviceUiInstrumentedTest` | **OK (7 tests)**，45.973s；good 键与旋转均通过，`review_reveal_write_failed` 不再出现 |
| PRD-06/08 默认守卫 | 不传 `smb_allow_data_reset` | **OK (2 tests)**，两条均以 `AssumptionViolatedException` 跳过（`DestructiveDeviceTestGuard.kt:33`），未清库 |
| PRD-06/08 显式 opt-in | `-e smb_allow_data_reset true` | **OK (2 tests)**，12.21s；仅在这台测试模拟器上清库 |
| 迁移 1→69 / 68→69 | `FullMigrationMatrixInstrumentedTest` | **OK (15 tests)**，489.14s |
| 保存角色 + 关库重开 + 多 CLEAN_IMAGE 替换 | `TutorSessionVisualRoleDatabaseInstrumentedTest,ProblemRevisionEditDatabaseInstrumentedTest,TutorSessionVisualRoleReopenInstrumentedTest` | **OK (13 tests)**，2.408s；含 `reattachingACleanRedrawReplacesThePreviousCurrentImage` |
| 重绘/对账/元数据 | `CleanRedrawTaskInstrumentedTest,FigureGenerationReconcilerInstrumentedTest,GeneratedFigureMetadataInstrumentedTest` | **OK (5 tests)**，2.152s |
| 详情角色/显式重绘 UI | `MistakeDetailRoleSectionsInstrumentedTest,MistakeDetailExplicitRedrawInstrumentedTest` | **OK (5 tests)**，11.821s |
| 导出角色/缺源 | `MistakeExportVisualRoleInstrumentedTest,MistakePdfMissingSourceInstrumentedTest,MistakePdfCleanImageExportInstrumentedTest,MistakePdfExporterInstrumentedTest` | **OK (17 tests)**，3.379s |

### 8.3 对前文的更正

- **设备清库高危险已不再是“未修”**：`DestructiveDeviceTestGuard` 已存在并在 `clearAllData()` 之前调用；默认跳过、显式 opt-in 才执行，两条路径均已实测（8.2）。
- **PRD-05 不再是“2 红未复跑”**：修复后的 7 例已实跑全绿（8.2）；T6 的设备验收从 UNVERIFIED 改为**已验证**。
- **显式重绘在途重复领取**：服务层 `PENDING && attemptCount > 0` 直接返回 Accepted，claim SQL 只允许 `attempt_count=0` 或过 stale 截止的 PENDING；新增 JVM 回归用例覆盖“第一次在生成器内挂起时第二次点击”，17/17 通过（8.1）。
- **同 revision 多 CLEAN_IMAGE**：挂接时先删同角色旧链接再插新链接；详情/导出按创建时间取最新；设备 13/13 含替换用例（8.2），JVM 策略测试通过（8.1）。
- **HEAD 归属**：当前 HEAD 为 `7784b04c`（他线 KB 提交），不含本批代码；本批依赖仍在工作树中，提交时必须把 schema 69、端口/实体/DAO/迁移、VisualRole、69.json 与各轨代码整组纳入。此前记录提到的 `f2adf825` 是更早的 HEAD，不代表当前状态。
- **复验边界**：真实 Provider 质量/费用、365MiB 库 + visual 资产组合备份、PRD-08 强停/进程死亡恢复仍未验证；operation 审计计数 1/1 不累计、`basis_revision_id` 惰性列等低危仍按 §6.2 保留。设备 opt-in 用例在本模拟器上清空了 App 数据，这是测试设备行为，不作为产品行为。

