# 阶段 4A「错题本与录入」实施计划（2026-10-03）

> 依据：主线 4A 行（`docs/REFACTOR-MASTER-LINE.md:50`）、phasing（`docs/superpowers/specs/2026-09-25-agent-first-refactor-phasing.md:38`）、
> **L1–L7 定稿裁定**（`docs/agent-first-refactor-decisions-2026-09-23.md:1058-1068`）。
> **用户 2026-10-03 四项裁定**：下一阶段 = 4A；L1–L7 全做、四批；仅 S17 随 4A（S18/S20 留 3C 尾）；L7 取 A 形态（应用内落盘 + 通知 + 成果入口）。
> **退出门（phasing 原文）**：列表与详情同源；录入一条流；导出不随页丢。
> **不做**：4B 的排版/生图（phasing 已裁转 4B）；S18/S20；3D 面（他线）；`practice_unit.title` 列删除（见 §5②）。

## 1. 关键事实基线（2026-10-03 勘察，file:line 已核）

- **模块**：错题本 `feature/library`（`LibraryRoute` 列表 / `MistakeDetailRoute` 详情 / `MistakeExportRoute` 导出）；录入 `feature/capture`（`CaptureScreen` 单题 + `BatchImportRoute` 批量 + `SplitImportReviewRoute` 拆分）。挂载点都在 `app/src/main/kotlin/com/tingyun/smartmistakebook/SmartMistakeBookRoot.kt`（Library :436、CaptureTutor :559、CaptureLibrary :588、BatchImport :617、SplitImportReview :641、CaptureResume :664）。
- **L1（TRASHED）**：三处枚举定义零写入——`core/database/.../StudyDbValue.kt:104`、`DatabaseContract.kt:51-55`（校验白名单）、`core/model/.../ProblemCatalog.kt:89-93`；文案待修点 `MistakeDetailRoute.kt:809`（"这条记录可能已删除…"）。归档即移出语义在 `RoomMistakeDetailRepository.kt:86-98`（archive/restore/observeArchived）。
- **L2（标题单源）**：提交时双列同写（`ProblemDraftTransactionDao.kt:972` revision.title、`:991` practice_unit.title）；读侧分叉——列表/目录读 `unit.title`（`LibraryCatalogView.kt:15`、`ProblemDao.kt:94/:216`），详情/历史/FTS 读 `revision.title`（`MistakeDetailDao.kt:178+`、`LibrarySearchFtsMigration.kt:78`、`LibraryFtsSearchDao.kt:100`）。**`library_catalog` 视图已 JOIN `problem_revision`（`revision.revision_id = entry.current_revision_id`）→ 改单源 = 视图一行 SQL**；视图 DDL 另逐字存在于迁移史（如 `MasterySchedulingMigration.kt:108`）。
- **L3（归类收敛）**：离线自由文本编辑器在线——入口 `MistakeDetailRoute.kt:423-428`、编辑器 `MistakeOfflineCorrectionEditor.kt:55-260`（保存走 `correctConfirmedOrganization` :222）；在线编辑器 `MistakeOrganizationCorrectionEditor.kt:57-140`（保存走 `confirm(requestId, selection)` :316，宿主 `MistakeOrganizationSection.kt:577`，自动应用 :395）；**`observeOrganizationOptions` 有实现零 UI 消费**（接口 `core/domain/MistakeOrganizationRepository.kt:165`、实现 `RoomMistakeOrganizationRepository.kt:599`）。
- **L4（待处理）**：模型齐备零 UI 消费——`CaptureWorkflowRepository.kt:40-76`（`PendingCaptureStage` 8 态 + `PendingCaptureItem`）、`:578-580`（`observePendingCaptures`/`readPendingCapture`）、实现 `RoomCaptureWorkflowRepository.kt:691-718`；`BatchImportRoute.kt:433` 文案"继续留在待处理题目中"无承载界面。
- **L5（筛选排序）**：`LibrarySort` 4 值、**默认 `RECENTLY_UPDATED`**（`core/domain/LibraryCatalogRepository.kt:5-19`）；`LibraryQuery.knowledgePointId` 现存（:15）；UI 无排序控件（`LibraryViewModel.kt:209-245` 只带 search/subject/chapter/knowledge/mastery）；facets 四层（`LibraryModels.kt:11-24`、`LibraryRoute.kt:364-374`）；排序 SQL `LibraryQueryDao.kt:72/:123`、`LibraryCatalogSorts.kt:9,33`、`RoomLibrarySearchStore.kt:164-166`；`LibraryLeastMasteredSortInstrumentedTest` 钉着将被删的 LEAST_MASTERED。
- **L6（录入统一）**：三套 Route + 三套 repository；错题本栏两个并列按钮（`LibraryRoute.kt:166` `library_capture_button`、`:281` `library_batch_import`）。
- **L7（导出后台化）**：导出是页面生命周期内前台任务（`MistakeExportRoute.kt:112-176`，保存/分享/打印 `rememberCoroutineScope` :200/:222/:268-296；渲染 `MistakePdfExporter.kt:121-176`；交付 `MistakePdfDelivery.kt`）；WorkManager 先例 `app/.../BatchImportDriver.kt:38-54`、`OrphanAssetGc.kt:19-29`；`POST_NOTIFICATIONS` 已声明（`app/src/main/AndroidManifest.xml:4`）；compileSdk 37 / **targetSdk 36** / minSdk 23 / work 2.10.5。
- **S17**：`observeActiveMistakes` 每行相关子查询 + 嵌套 EXISTS（`ProblemDao.kt:205-265`），既有缺陷登记 `docs/known-defects.md:265`。
- **冲突面**：`feature/library`、`feature/capture`、`core/data/mistake`、`core/export` 全部干净；他线在飞 = KB 文档/工具（`docs/agent-first-refactor-decisions-2026-09-23.md`、`docs/superpowers/specs/*`、`tools/kb_build/*`）。**schema 号段 59→60/61 由本阶段声明占用**（登记于本文件；台账他线在飞、回填待其静止）。

## 2. 外部资料（2026-10-03 收集，落进设计）

1. **Android 官方《Support for long-running workers》**（developer.android.com）：targeting API 34+ 的**所有长运行 Worker 必须指定前台服务类型**（清单 `SystemForegroundService` + `ForegroundInfo(serviceType)`）；用户发起的数据传输官方推荐 **user-initiated data transfer job**；**Android 16 起长 Worker 会耗尽 app 的 job 配额**。→ 导出是秒级本地渲染：选 **expedited 普通 `CoroutineWorker`**（不进 FGS、不过 10 分钟线、不占长期配额），完成用普通通知。
2. **Android 官方 SAF 文档**（developer.android.com/training/data-storage/shared/documents-files）：`ACTION_CREATE_DOCUMENT` **不能覆盖**已有文件（同名自动加序号）；后台写入需 `takePersistableUriPermission(READ|WRITE)`；**文件被移动/删除后持久权限失效**。→ 支撑 L7 取 A 形态：先落应用内、用户在场时再走既有 SAF 流程，失败面最小。
3. **GitHub 相似项目（单通道检索，标注"单一来源、未做多通道交叉"）**：`wttwins/wrong-notebook`（736★，Next.js Web 版，有"一键导出筛选后错题/打印/存 PDF"）、`tjunsh/ai-wrong-notebook`（97★）、`PaperAirplane-Dev-Team/Mistake_Collection-Android`（8★，弱）。跨栈不可移植，仅功能点对照。

## 3. 四批设计与验收

### 批 1 · 数据与读侧面（L1 + L2 + L5 + S17；schema 59→60）

- **L1**：删 `TRASHED` 三处（`StudyDbValue` / `DatabaseContract` 白名单 / `core:model` 枚举；`when` 穷尽修）+ `MistakeDetailRoute.kt:809` 文案；全仓确认零读者/零写入残留。
- **L2**：读侧单源改 `revision.title`——`library_catalog` 视图 `unit.title`→`revision.title`；`ProblemDao` 目录条目两处（:94/:216）改走 revision；**写侧双列与列本身保持不动**。迁移 59→60 = 重建视图（DROP + CREATE）+ `schemas/60.json`；矩阵 1→60；`KernelWave0SchemaContractTest` 字面量同步。**验收证据**：构造"unit.title ≠ revision.title"的库内状态，断言列表/目录读的是 revision.title（单源证明）。
- **L5**：`LibrarySort` 收敛为 `{RECENTLY_UPDATED（默认，维持现状）, RECENTLY_CREATED}`；删 `NEXT_REVIEW`/`LEAST_MASTERED` 及 `knowledgePointId` 查询参数（domain/DAO/搜索 store/ViewModel/facets）；**新增"录入时间段"筛选**（按创建时间区间：query 起止参数 + UI 控件）；删"知识点"筛选层（保留科目/板块/掌握程度）；加排序控件（两值）；`LibraryLeastMasteredSortInstrumentedTest` 随 LEAST_MASTERED 退场重做（不静默丢断言）。
- **S17**：`observeActiveMistakes` 重写为 LEFT JOIN 聚合（消除每行子查询），结果集逐位一致 + 既有性能/EXPLAIN 门不回归。
- **门**：JVM 全量 + `:core:database` 仪器化全套（含矩阵 1→60）+ app 三屏 + 库列表定向仪器化；独立复核；显式清单提交。

### 批 2 · 错题本界面收敛（L3 + L4；零 schema）

- **L3**：删离线自由文本编辑器（`MistakeOfflineCorrectionEditor` + `MistakeDetailRoute` 接线 + 相关测试）；在线编辑器加**"从知识树选择"**（消费既有 `observeOrganizationOptions`；选项形状不足时按既有 KB 读口补，不新造目录；选择结果并入 `confirm` 的 selection）。
- **L4**：`CaptureScreen` 入口态加**"待处理"列表**（消费 `observePendingCaptures`：恢复/废弃）；`BatchImportRoute.kt:433` 文案获得落点。
- **门**：JVM + feature/library 与 feature/capture 定向仪器化 + app 三屏；复核；提交。

### 批 3 · 录入统一入口（L6；零 schema）

- 错题本等入口的"拍照/批量"统一为**一个"录入"动作** → 方式选择（拍照/相册/文件与目录）；批量与拆分复核**收敛为录入内部步骤**（保留既有 pipeline，不重写）；命名与文案统一。
- **门**：JVM + capture/app 仪器化（含 `RootExperience` 等受导航影响用例）+ 真机四入口走查；复核；提交。

### 批 4 · 导出后台化（L7，A 形态；schema 60→61）

- `ExportPdfWorker`（expedited `CoroutineWorker`；渲染核心抽成 JVM 可测单元）+ **完成通知**（未授权时退化为应用内入口）+ **"导出成果"入口**（列表 → 分享/保存/打印走既有 `MistakePdfDelivery`）+ 清理策略（KDoc：保留至用户处理，上限 N）。
- 导出记录表（新表，schema 60→61；id/kind/文件名/状态/时间）+ `schemas/61.json` + 矩阵 1→61。
- **门**：JVM（渲染单元 + 状态机）+ app 仪器化（导出导航/新入口）+ **真机走查：导出→离开页面→完成通知→保存/打印**；复核；提交。

### 4A 综合门与收尾

全量 JVM + DB 仪器化全套 + app 仪器化（三屏 + 库/录入/导出定向）+ R8 冒烟；**三条退出门逐条落证**；完成记录独立成文 + 主线 4A 行收口 + 台账回填（他线静止时）+ 推送。

## 4. 版本与迁移预算

- `STUDY_DATABASE_VERSION` **59→60**（批 1：视图重建）；**60→61**（批 4：导出记录表）。
- **零算法 bump**（纯 UI/查询/导航面，`LearningCoreVersions` 不动）：台账 §3.16 零 bump 行 + 各批记录。
- `schemas/60.json`、`61.json`；`KernelWave*SchemaContractTest` 字面量同步；矩阵用例 1→60/61。

## 5. 冲突裁决与登记（本轮勘察发现）

① L 编号以裁定表为准（L1=TRASHED、L2=标题单源、L3=归类收敛、L4=待处理、L5=筛选排序、L6=录入统一、L7=导出后台化）；
② L2 **不删列**（`practice_unit` 枢纽 + RESTRICT 子表重建风险，收益不抵）——登记死面候选；
③ L5 默认排序读法 = 维持 `RECENTLY_UPDATED`（"时间先后"= 最近更新在前；可一句翻转）；
④ S18/S20 留 3C 尾（roadmap"同批"注记被推迟，完成记录登记）；
⑤ L6 只收敛入口/命名/导航，不重写 pipeline；
⑥ L7 取 A 形态（用户裁）；
⑦ 批 2 的知识树选择若 `observeOrganizationOptions` 形状不足，按既有 KB 读口补齐，不新造目录。

## 6. 纪律与门

共享工作树：不碰他线在飞文件；显式文件清单提交；每批"实施 → 正式门 → 独立复核（新 Agent、只读、对抗性）→ 修复轮 → 提交"；批 1/批 4 开工前出批级细案（锚点见附录 A）。门口径同 3B：JVM 全量、DB 仪器化冷启严格口径、app 定向仪器化、R8 冒烟；不宣称项目安全（Mimosa 边界）。

## 7. 风险与 UNVERIFIED

- **视图重建迁移**：Room 对 `@DatabaseView` 做身份校验，`60.json` 须与视图 SQL 逐字一致；矩阵 1→60 全覆盖（风险中）。
- **L6 导航收敛**牵动既有 app 仪器化用例（`RootExperience` 等），预留用例迁移预算。
- **L7 通知**：Android 13+ 需 `POST_NOTIFICATIONS` 运行时授权，未授权时退化为应用内成果入口。
- **S17 重写**需既有 EXPLAIN/性能门不回归。
- 批 2/批 3 的交互细节（待处理列表形态、方式选择器）在批级细案定；本阶段级计划不含像素级设计。

## 附录 A · 批 1 实施锚点（供批级细案与实施）

- **schema 面**：视图 DDL 现文 `LibraryCatalogView.kt`（`@DatabaseView` 值）+ 迁移史副本；迁移类命名惯例 `KernelWave*Migration_*`；`StudyDatabase.kt` 版本与迁移注册；`schemas/` 目录 59.json 为当前头。
- **L5 参数面**：`LibrarySearchStore`/`LibraryQueryDao`/`LibraryCatalogSorts`/`RoomLibraryCatalogRepository` 四处的 sort 与 filter 传递链；`LibraryModels.kt` facet 树 + `LibraryViewModel.kt:141-153` 层级收敛逻辑 + `:209-245` 查询构造。
- **S17**：`ProblemDao.kt:205-265` 现 SQL；结果消费 `RoomLibraryCatalogRepository`/experience catalog；既有门 `PerformanceGateTest`（library 段）与相关仪器化类。
- **测试迁移面**：`LibraryLeastMasteredSortInstrumentedTest`（LEAST_MASTERED 退场）、`LibrarySearchMigrationInstrumentedTest`、`LibraryCatalogPagingInstrumentedTest`、`LibraryCatalogTest`（JVM）、`MistakeDetailDatabaseInstrumentedTest`（标题断言）、`FullMigrationMatrixInstrumentedTest`（1→60）。
- **验收证据（L2）**：构造 unit.title 与 revision.title 相异的库态（走真实 revision 改写路径），断言列表与详情同值（双向）。
