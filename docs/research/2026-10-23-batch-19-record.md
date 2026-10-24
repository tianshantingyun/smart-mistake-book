# 批次 19 记录（2026-10-23）：6C-2 收口尾 + 5B 两半边 + 3E/PRD-11 设备

> 记录时点：2026-10-25（+08:00）。本文件由记录员在共享工作树 `D:\smart mistake book` 只读核对计划、基线、源码、git 状态与工单给定材料（各轨交付、门禁、设备 evidence、复核发现、修复环第 2 轮）后写入，并同步主线/台账/KD/PRD 计划/算法台账五处文档。
> **本记录员未运行 Gradle、未运行 adb/am instrument、未改任何代码、未执行任何 git 写操作、未提交/推送。** 文中门禁与设备结论来自工单给定材料；记录员自跑的只有只读命令（见附录）。凡无当前执行证据的验收一律标 **UNVERIFIED**。
> 计划：`.zcode/plans/plan-2026-10-23-batch-19-6c2-closeout-5b-halves.md`（下称"计划"）。
> 基线：`docs/research/2026-10-23-batch-19-baseline.md`（下称"基线"）。
> **记录边界：本批单测门第 3 轮 `exit=1`、强门未跑——工程门未过，本记录不声称"批次全绿"；不关闭未达验收；提交建议附前置条件（§6）。**

## 1. 范围与基线

### 1.1 计划范围

- **做**（计划 `:24`）：6C-2 文档债回写与五项加固；KD-61 写门扩展（或核实+收窄登记）；6C 换机演练设备门（全量+子集）；5B-G6 排程半边；5B-G8 回收域层半边；3E 真实形态设备复验；PRD-11 可行矩阵（R8/本地任务线）；门禁/强门/设备/双复核/记录/提交/自动推送。
- **不做**（计划 `:26`）：4E WG 余项、PRD-09 真实生图质量（无凭据 UNVERIFIED）、**G5 API 扩展（台账待裁）**、阶段 6 UI、5b/5c、UI-00~11、6B-3 app 注入、AUD-08/AUD-06、考纲范围匹配（D-4）、真实 Provider、`SchedulingSettingsScreen` 任何改动。

### 1.2 产品裁定（不得改，计划 §2）

1. 6C-2 三裁定维持：不提供 mask=8、禁止 mask=0、派生索引恒裁+恢复后重建；允许组合仅 9/13/15。
2. 插眼 1 已裁值：考试重要性高/中/低=1.0/0.7/0.4、窗口默认 14 天（1–60）；扩容爬升复用已裁值与既有常量，确需新常量标 `PENDING_CALIBRATION`。
3. 本批只做 5B-G8 回收半边：不实现 G14、不代填权重；开放题走既有判词通道的**接口形态**，模型侧不实现。
4. 回灌事实必须是学生作答证据：生成图/清图不得冒充答卷；重传幂等不重复计分；不同卷/不同日不撞键（AUD-01/02 身份复用）。
5. 本地判"选项 ID 对照"不等于答案键可信：沿 PRD-02 规则，键不可信时该题判"待核验"，不写数值掌握。
6. 真实 Provider 无凭据：一切模型质量 UNVERIFIED，不声称。

### 1.3 基线摘要（引自基线文档，T0 已落盘）

**与计划 §0 一致的项（T0 实测）**：HEAD = origin/main = `311f7de1`；DB schema 69（identityHash `6633b7c2fe223041c4a2927203853f4f`），`70.json` 不存在；请求 schema 18（`ModelTasks.kt:546-547`）；KD open 共 6 条（KD-24/27/28/29/32/61，KD-62③ 同归 KD-61）；G6 加权/提前已接线（`ReviewPlannerV2.kt:647` 消费 `EARLY_REVIEW_MAX_RETRIEVABILITY=0.8`、`:666` 消费 `EXAM_WEIGHT=2.0`）、扩容爬升不存在（T4-2 需新建）、错题排程侧未消费 3E 分数（T4-3 需接线）；G8 无答案键字族、回灌通道存在（`StudyExperienceRepository`）。

**6C-2 批次 18 遗留五项全部代码级证实**：写侧 `requireWritableSelection`（`SmbkArchiveCodec.kt:435-482`）无负 rowCounts 检查而读侧 `:1197` 有；`BackupSelectionSummary`（`BackupSelection.kt:177-191`）仅 mask/categories 无 assetPolicy；全清 `deleteAllData`（`AndroidBackupRepository.kt:883`）第 10 步 `:996-1001` 漏 `restore-index-rebuild-pending.json`（`RestoreIndexRebuild.kt:29`）；恢复侧 S3 软引用检查两文件 0 命中；I5 双源 = `PendingCaptureDao.kt:78` 五 NOT EXISTS ↔ `BackupSnapshotTrimmer.kt:1247` 五 EXISTS。KD-61 写门生产覆盖实测仍仅 `AndroidBackupRepository.kt:155` 与 `RoomCaptureWorkflowRepository.kt:166`。

**基线与计划不符处（T0 如实登记）**：工作树 574 项（142 M + 432 ??）≠ 计划 569；`SmartMistakeBookRoot.kt`、`CaptureScreen.kt`/feature/capture 全模块、feature/library（13 M）、core/ui（9 M+19 未跟踪）、`feature/profile/ProfileRoute.kt` 均漂移为事实热区。本记录员开工时复测：HEAD/origin/main 仍同为 `311f7de1`，工作树已漂至 **658 项**（他线继续在飞）。

## 2. 逐轨交付

> 判定口径：**代码级完成** = 改动在当前树且记录员/复核静态核对属实；执行证据分列（设备实跑 / JVM 工件 / 独立编译执行 / 无证据=UNVERIFIED）。本批单测门未过（§3），凡被 core:data 测试编译红连坐、修复后又无 Gradle 复跑绿证据的测试一律标"未复跑"。

### 2.1 T0 基线（文档）

- 交付：`docs/research/2026-10-23-batch-19-baseline.md`（基线文档，未跟踪待提交）。判定：**完成**（文档落盘，只读采集）。

### 2.2 T1 6C-2 文档债回写轨

- 文件：`docs/research/2026-10-17-6c2-content-selection-design.md`。
- 交付：批次 18 三项裁定（不提供 mask=8、禁止 mask=0、派生索引恒裁+恢复后按科重建）与机器清单实测数字回写 §9.9（:1591-1602）/§9.11（:1012-1017）/§7（:563-567）/§9.8，均见 2026-10-23/24 回写痕迹（复核证据）。
- 复核：Python 解析设计件 2 个 json fenced block 均 parseable；机器块 `maskPolicy.openMasks` 恰 `[{8,REJECTED},{0,REJECTED}]`、`closedMasks=[15,13,9]`；`SmbkSelectionPlanTest.kt:356-362` 断言 `openMasks={0,8}` 且均不可创建——测试解析契约不变。
- 判定：**完成**（文档级，复核解析证据）。

### 2.3 T2 6C-2 加固轨（六项，先红测后实现）

- 文件（19 个，git status 全部对上：11 M + 8 ??，见 §6 清单）：
  - 生产：`core/domain/.../BackupSelection.kt`、`core/data/.../backup/SmbkArchiveCodec.kt`、`AndroidBackupRepository.kt`、`BackupSnapshotTrimmer.kt`、`RestoreSoftReferenceRecheck.kt`（新）、`core/database/.../dao/PendingCaptureDao.kt`、`dao/CanonicalAssetReferenceSites.kt`（新）、`app/.../SecondaryScreens.kt`
  - 测试：`SmbkArchiveCodecTest.kt`、`SmbkSelectionPlanTest.kt`、`CanonicalAssetReferenceSingleSourceTest.kt`（新）、`RestoreSoftReferenceRecheckTest.kt`（新）、`BackupRestoreInstrumentedTest.kt`、`BackupSelectionRestoreInstrumentedTest.kt`、`app/.../StorageRestoreFeedbackTest.kt`、`BackupSelectionUiInstrumentedTest.kt`
- 六项逐条（复核已对拍 file:line）：
  1. 写侧拒负 rowCounts：`SmbkArchiveCodec.kt:469-472`（批注 T2-1）↔ `SmbkArchiveCodecTest.kt:1109-1152` 断言拒因逐字等于读侧文案；
  2. `BackupSelectionSummary` 补 `assetPolicy`（`BackupSelection.kt:182-189` 默认值）↔ UI 四处同口径（`SecondaryScreens.kt:1192-1278`：创建摘要/创建回执/恢复前确认/恢复回执）↔ `SmbkArchiveCodecTest.kt:1158-1186`；
  3. `deleteAllData` 清待重建标记：`AndroidBackupRepository.kt:1030-1036` ↔ `BackupRestoreInstrumentedTest.kt:125-161`（先写标记断言 exists → deleteAllData → 断言 !exists）；
  4. 恢复交换前 S3 软引用重查：`RestoreSoftReferenceRecheck.kt`（新文件）+ `AndroidBackupRepository.kt:562-573`（注释明示"仍在 Phase 3 之前"）↔ `RestoreSoftReferenceRecheckTest` 7 例；两侧规则逐项对拍（deleted-model-task-kinds 同一单源函数、O_seed/O_ref/A_keep 公式一致、clean_redraw 恢复侧不设 issueFlag 门=更宽松、mask=null/15 跳过与裁剪器 FULL 早退同口径、未知 kind 双侧 fail-closed）——**未发现误拒合法包路径**；遗留注记见 §4 发现#9（finally 删 -wal/-shm 边车）；
  5. I5 五站点 SQL 单源化：`CanonicalAssetReferenceSites.kt:28-66` 双常量 ↔ `PendingCaptureDao.kt:47` `@Query(常量)` ↔ `BackupSnapshotTrimmer.kt:1254-1256`；旧 DAO SQL 与新常量逐字一致、旧五 EXISTS 析取 ≡ 新 NOT(五 NOT EXISTS)（德摩根）、全库无第三份手抄、KSP 生成物（10-24 23:38）含同 SQL（编译证明归门禁，未跑）；
  6. 子集备份按 mask 显示实际内容：`StorageRestoreFeedbackTest.kt:63-105`（四处断言含"（不含掌握库、会话）"）+ `BackupSelectionUiInstrumentedTest.kt:247-251`。
- 执行证据：
  - **设备实跑（P2 备份回归，门禁轨 2026-10-24）**：`BackupSnapshotTrimmerInstrumentedTest,BackupSelectionCreateInstrumentedTest,BackupSelectionRestoreInstrumentedTest,BackupRestoreInstrumentedTest` → EXIT=0，`OK (56 tests)`（逐类 9/3/10/34；批次 18 的 53 例不回退，多出 3 例为本批 T2 新增）。
  - JVM 工件旁证：`StorageRestoreFeedbackTest` XML（2026-10-24 22:42）tests=7 failures=0。
  - **未复跑**：`SmbkArchiveCodecTest`/`SmbkSelectionPlanTest`/`CanonicalAssetReferenceSingleSourceTest`/`RestoreSoftReferenceRecheckTest`（core:data JVM）在复核时点受发现#1 编译红连坐，修复后无 Gradle 绿工件 → **UNVERIFIED**；`BackupSelectionUiInstrumentedTest`（app androidTest）无本批运行证据 → **UNVERIFIED**。
- 判定：**代码级完成 + 设备回归 56/0 实跑**；core:data 四个 JVM 测试类与 app UI 仪器化执行未复跑（门禁未过）。

### 2.4 T3 KD-61 写门扩展（收窄落法）

- 文件（7 个）：`core/data/.../visual/FigureGenerationReconciler.kt`、`AndroidBackupRepository.kt`（共享）、`app/.../SmartMistakeBookApplication.kt`、`BackgroundWriterQuiesce.kt`（新）、`FigureGenerationReconcilerTest.kt`、`app/.../BackgroundWriterQuiesceTest.kt`（新）、`RestoreBackgroundQuiesceInstrumentedTest.kt`（新）+ `docs/known-defects.md`（KD-61 条目）。
- 交付（按证据收窄）：
  1. 图生成僵尸对账接入既有写门（`FigureGenerationReconciler` 构造改收 `BackupWriteGate`，`FigureGenerationReconciler.kt:39-50`）；
  2. 恢复/清库关库前硬性 quiesce 钩子：`AndroidBackupRepository.kt:648`（`beginCommit` 之后、750ms 宽限与 close 之前）与 `:955`（deleteAllData 对称接入）；生产装配传"取消整个 applicationScope + 限时 2s join 全部子协程"（`BackgroundWriterQuiesce.kt:31-44`）；四个 DataStore 工厂拆独立 `dataStoreScope`（`SmartMistakeBookApplication.kt:135` 与 :309/:413/:414/:1028 四工厂），保 Phase 8 设置写不被 quiesce 取消；钩子抛异常中止恢复且不关库（交换前失败分支）。
  3. KD-61 条目更新为 **open（收窄）**（`docs/known-defects.md:2466-2508`），旁路清单按入口家族登记（热区直写面/WorkManager 在途 SQL/UI 写/quiesce 超时路径四类残余），如实声明本轨未跑 Gradle。记录员本次已重读该条目：处置内容与 T3 证据一致。
- 执行证据：
  - `FigureGenerationReconcilerTest`：修复环第 2 轮以 **K2JVMCompiler 2.3.21**（与 `gradle/libs.versions.toml:3` 同版；junit 4.13.2/coroutines 1.11.0/stdlib 2.3.21 与项目一致）对真实源文件闭包独立编译 → OK，**JUnitCore 执行 OK (7 tests)**（非 Gradle 通道）。
  - `BackgroundWriterQuiesceTest`（app JVM）：最近一次 Gradle 工件（2026-10-24 22:42:50）**4 例 1 红**（`an in-flight writer finishes its write before quiesce returns`，测试自身有竞态：scope.launch 后立即 quiesce，writer 协程可能从未启动），发生在最后一次源码修改（22:01）之后；此后无重跑工件 → **未达验收，未闭合**（§4 发现#2）。
  - `RestoreBackgroundQuiesceInstrumentedTest`：无运行证据 → **UNVERIFIED**。
- 判定：**收窄落法完成（代码级 + KD 条目 + Reconciler 独立执行 7/0）**；quiesce 的 app JVM 测试红点与仪器化未跑如实登记。

### 2.5 T4 5B-G6 排程半边

- 文件（3 个）：`core/data/.../study/StudyReviewPlannerService.kt`、`core/domain/.../AlgorithmConstants.kt`、`StudyReviewPlannerExamWindowTest.kt`（新）。
- 交付：
  - **扩容爬升**：`examCapacityBudgetSeconds`（`StudyReviewPlannerService.kt:140-174`）窗口内预算线性放大——`ramp<=0.0` 原值返回（无考试日/窗口外逐位不变），放大系数 = `1 + EXAM_CAPACITY_RAMP_MAX_EXTRA_FRACTION × ramp`；新常量 `EXAM_CAPACITY_RAMP_MAX_EXTRA_FRACTION=0.5`（`AlgorithmConstants.kt:148`，KDoc 标 **PENDING_CALIBRATION**：插眼 1 只裁形状与窗口/重要性档，幅度是起步占位，改数属产品裁定面）；窗口 `EXAM_RAMP_DAYS=14`（`StudyReviewPlannerService.kt:800`）复用插眼 1 已裁默认窗口。
  - **薄弱优先**：3E 扫描到期压力接入既有 EXAM 通道——扫描读门 `exams.any { (it.examEpochDay - localDayEpochDay) in 0..EXAM_RAMP_DAYS }`（:323）才 `readFullLibraryScanNodes`；候选 `examPriority` 仅 `ramp > 0` 时 `maxOf(ramp, scanBackedDuePressure(...))`（:383-397）；不加新权重常量。
  - `ReviewPlannerV2` **零改动零 bump**（复核：该文件非 dirty，`VERSION="review-planner-v4"` :1055 未变——记录员本次复核确认）。
- 执行证据：六组验证单测 `StudyReviewPlannerExamWindowTest` 在树（含指纹级对照：无考试 vs 窗口外同科目考试 `planFingerprint` 相等、timeBudget 均 1200、全库扫描读计数 0），**因发现#1 编译红从未在 Gradle 下运行、修复后未复跑** → 执行 **UNVERIFIED**。无考试日不变性另有结构性支撑（§4 发现#3：短路原值返回/读门 false→emptyMap/ramp==0 原样返回），但"金样对照"字面验收未证明（全树无以旧输出为金样的调用方；旧二进制金样不可行）。
- 判定：**代码级完成；验证单测执行 UNVERIFIED（未运行）**。

### 2.6 T5 5B-G8 回收域层半边

- 文件（11 个，全 ??）：域层五件 `PaperAnswerSheet.kt`/`PaperAnswerKeyChain.kt`/`PaperAnswerRecycleJudge.kt`/`PaperAnswerEvidenceFeedback.kt`/`PaperAnswerSheetRecycler.kt` + 四个域测试 + `core/data/.../study/InMemoryPaperAnswerRecycleStore.kt` + 其测试。
- 交付（复核逐项核对）：
  - **答卷身份**：指纹+题+学习日派生幂等键；同卷同题重传不重复计分、不同卷/不同日不撞键（`PaperAnswerSheetIdentityTest.kt:19-101`）；仓储 `ConcurrentHashMap.putIfAbsent` 原子去重（`InMemoryPaperAnswerRecycleStore.kt:39-48`，并发同 key 恰一条 created=true）。
  - **出卷答案键链**：模型+仓储接口+内存实现（`PaperAnswerKeyChain.kt:99-111` 的 `answerKeyTrustDecision` 复用 `core:model` 单源判定）。
  - **客观题本地判**：纯函数（`PaperAnswerRecycleJudge.kt:203-213` `PendingVerification` 档——键不可信→待核验不写数值掌握；`PaperAnswerRecycleJudgeTest` 表驱动）。
  - **证据回灌**：`LOCAL_CHECKED` 证据回灌端口+编排——仅 `LocalCheckedCorrect/Incorrect` 档可构造命令（`PaperAnswerEvidenceFeedback.kt:49-52` 命令 init 只收 VERIFIED/USER_ASSERTED，三重锁死"待核验"不当有效证据消费）；"回灌总是提交 + 端口按 attemptKey 幂等"收敛杀进程重放；判词通道 `PaperOpenAnswerJudgingChannel:69-76` 默认抛 `UnsupportedOperationException`（只留接口）。
  - **零 schema 零 UI**：`core/database/schemas/` 止于 69.json、`StudyDatabase.kt` 无改动；生产零消费（`grep PaperAnswerEvidenceFeedbackPort app/ core/ feature/` 生产消费 0 命中；无 Application/Root 接线）。
- 执行证据：37 用例（8+11+6+5+7）全部在树、断言实质；**无任何 Gradle 执行证据**（core:domain 本批只定向跑了 `KnowledgeReviewQueueTest` 22/0；core:data 被编译红连坐）→ **UNVERIFIED（未运行）**。
- 判定：**域层半边代码级完成；37 用例执行 UNVERIFIED；导出侧接线/UI 入口/真实落库登记遗留（§5）**。

### 2.7 I1 集成

- T3 所需接线完成：`SmartMistakeBookApplication.kt`（quiesce 装配 + 四 DataStore 工厂独立 scope）。
- T2/T5 免接线判定（复核"集成轨四项免接线判定与树相符"）：T2 加固在既有备份装配内闭环；T5 生产零消费（域层+内存仓储先行，接线批次后置）。
- `SmartMistakeBookRoot.kt` **本批未改**（复核：diff 全为他线 NavHost 过渡动画，引用未跟踪 `core/ui/.../ClaudeMotion.kt`；本批标记 grep 0 命中）——提交时必须排除（§4 发现#7）。
- 判定：**完成（静态）**；设备端恢复链路经 D1 实跑覆盖（§2.8）。

### 2.8 设备轨（D1 换机演练 + T6 3E + T7 PRD-11）

环境：`emulator-5554`（API 34）；全部裸 `am instrument`（未用 `connectedAndroidTest`）；destructive 用例以 `-e smb_allow_data_reset true` 显式 opt-in。

| # | 链路 | 结果 | 关键证据 |
| --- | --- | --- | --- |
| P1 | 迁移矩阵 `FullMigrationMatrixInstrumentedTest` | **EXIT=0，OK (15 tests)**，15 个 code-0 | 矩阵为 `for (version in 1..STUDY_DATABASE_VERSION)` 动态循环（`:38`），STUDY_DATABASE_VERSION=69；T5 零 schema → 无 1→70/69→70 需扩，实际覆盖 1→69 |
| P2 | 备份回归四类（T2） | **EXIT=0，OK (56 tests)**，code-0=56、-2/-3/-4 全 0 | 逐类 9/3/10/34；批次 18 的 53 例不回退，+3 为 T2 新增 |
| P3-D1 | 换机演练 `DeviceMigrationDrillInstrumentedTest`（新文件，6 用例） | run1 `-e smb_allow_data_reset true` → **EXIT=0，OK (6 tests)**：4 过（mask=15/9/13 三条 + corruptedSelection）+ 2 杀进程阶段按设计 assumeTrue 跳过 | 三条 mask 各覆盖：备份→deleteAllData（守卫）→恢复→Phase 2c.1 同口径逐表 COUNT 对拍（`SmbkSelectionPlan.allTables` 86 表+夹带表审计，mask=15 同比）、设置往返（保持率 0.9/两科考试日历/提醒 18:30）、Key/租约不入包（归档条目白名单+settings.json 字段集恰为偏好六键）、提醒不复活旧授权、派生索引恒裁+两科（MATH/PHYSICS）重开自重建 REBUILT/标记消费/检索可用；corruptedSelection 断言失败停在交换前（原库可读、无 .prev/.next、无标记）。**先红后绿实据**：首跑 3 例红为对拍口径错误（`library_search_fts_stat` 是 FTS4 影子表、不在机器清单），按 2c.1 口径修正后全绿（/tmp/p3-d1-run1.log 3 失败 vs /tmp/p3-d1-run1b.log OK） |
| P3-D1 | 真实杀进程两阶段 | A 阶段（`-e smb_d1_kill_stage true -e class …#killProcessHandoffStagesArchiveThenDies`）进程在测试中真实死亡（`INSTRUMENTATION_RESULT: shortMsg=Process crashed.`），交接件落盘存活（`run-as … ls files/d1-kill-handoff/` → archive-mask9.bin 28294 字节）；B 阶段 `#restoreFromHandoffAfterProcessDeath` → **EXIT=0，OK (1 test)** | 新进程清数据后恢复 mask=9、对拍+两科重建全过 |
| P4-3E | 3E JVM `:core:domain:test --tests …KnowledgeReviewQueueTest` | **BUILD SUCCESSFUL**，XML（2026-10-24 23:22 本轮时间戳）tests=22 failures=0 errors=0 | 覆盖第二档全库捞回、FULL_LIBRARY_SCAN_DAILY_CAP=10 封顶、timeBudget 封顶；core:data 侧 ExamWindowTest 当时被编译红阻塞（已修，见 §3）。**3E 设备侧/无排计划仪器化：未做 → UNVERIFIED** |
| P5-R8 | release 冒烟 `:app:assembleLocalFirstDebug(+AndroidTest)` | 构建成功 → 装 → 启动（10s 后 pidof 存活）→ logcat `grep -cE "FATAL EXCEPTION\|ANR in\|Fatal signal\|Tombstone written"` = 0（启动窗+走查窗两次）→ 卸载 Success ×2 | 包名 `com.tingyun.smartmistakebook.localfirst` |
| P5-网络 | 本地任务线网络观测=0 | dumpsys netstats mAppUidStatsMap 中 uid=10194 无行（无行=零字节）+ `/proc/net/tcp{,6}` 归属该 uid socket=0 条，启动与全链走查后复读仍 0/0 | 应用级旁证：Prd06 与手动建题用例断言 model_task 行数=0 |
| P5-T7 | 本地任务线走查（`LocalTaskLineDeviceInstrumentedTest,Prd06…,MistakeExportDelivery…`，`-e smb_allow_data_reset true`） | **EXIT=0，OK (5 tests)**，0 失败 | 两新用例：manualCreate（真实落库 problem=1、回执、BACK 回根、错题本重开可见、model_task=0）+ nativeDocxExport（真 exporter 设备渲染 OOXML、`[Content_Types].xml`+`word/document.xml` 含题面、CREATE_DOCUMENT/SEND 意图 OOXML MIME、`file://` 拒收、MediaStore 真保存摘要一致）+ Prd06 首装空态 1 + PDF 保存/分享/打印 2。**"修改"步骤未在证据中点名 → 未覆盖** |
| P5-可行矩阵 | API23/arm64/16KB/双 flavor | **不可行+理由，如实标注** | 宿主镜像仅 android-34；abilist=x86_64,arm64-v8a（arm64 仅 nativebridge 翻译，非真 arm64）；PAGESIZE=4096；flavor 四选一仅 localFirst 全链已验，strictOffline/benchmark/internal 未跑不冒充；R8 未跑 |

- 判定：**D1 换机演练完成（6C 退出门该项实测达标：三条 mask 全过、失败停交换前、杀进程两阶段真实死亡+恢复）**；3E 设备侧、任务线"修改"步骤、三 flavor、API23/arm64/16KB 如实 UNVERIFIED/不可行。

## 3. 门禁与设备证据

| 门 | 状态 | 说明 |
| --- | --- | --- |
| 单测门（11 模块 `--continue`） | **第 3 轮 `exit=1`（未过）** | 已知归因（复核取证）：① core:data `testDebugUnitTest` 整体编译红——`FigureGenerationReconcilerTest.kt` 尾随 lambda 传 `()->Long` 而构造末参为 `BackupWriteGate`（发现#1；连坐 T2 四个 JVM 类、T4/T5 core:data 测试无法编译，build 目录确无运行产物）；② app `BackgroundWriterQuiesceTest` 4 例 1 红（发现#2）。①已在树修复（修复环），但**此后单测门复跑仍 `exit=1`，第 3 轮完整失败清单未在材料中给出**——门禁未过如实登记，不得声称全绿 |
| 强门（六模块 androidTest 编译 + `assembleLocalFirstDebug`） | **未跑** | 单测门未过，按计划顺序不进强门。注：`:app:assembleLocalFirstDebug(+AndroidTest)` 在 P5-R8 冒烟中已构建成功（非完整强门清单） |
| 设备 | 见 §2.8（P1/P2/P3-D1/P4/P5 均有实跑 exit 码） | 全部裸 `am instrument`，build 目录无本批 androidTest XML（复核：core:data connected XML 停在 10-20、app localFirst 停在 10-19）——数字以 stdout/日志旁证为准，旁证与声明全部一致 |
| 真实 Provider | **UNVERIFIED** | 无凭据、未调用 |

**修复环第 2 轮（材料摘录，本轮零新改动）**：第 2 轮五条发现与第 1 轮逐字相同（evidence 时间戳仍是第 1 轮采集）；处置 = 在当前树上逐条重新核实。**发现#1（Reconciler 编译红）缺陷已在树修复，本轮实跑确认绿**——当前源 `FigureGenerationReconcilerTest.kt:33/61/80/100` 均为 `FigureGenerationReconciler(audit, { NOW })`（坏写法残留 0），`:96` 已是 `runBlocking`；K2JVMCompiler 2.3.21（与 `libs.versions.toml:3` 同版）对真实源闭包编译 → `R2-RECONCILER-COMPILE-OK`，JUnitCore 执行 → **OK (7 tests)**。复核 evidence 中"build 目录无新 XML/21:48 mtime"是第 1 轮时点快照。**单测门复跑 exit=1（编译红修复后仍无一次 Gradle exit=0）。** 第 2 轮材料仅含发现#1 的处置明细，其余发现按第 1 轮结论登记（§4）。

**提交前置条件（记录员意见）**：单测门一次 `exit=0` 复跑（含发现#2 的归因或修复）+ 强门通过，方可提交代码块（§6）。

## 4. 复核发现与处置（14 条，severity/status 按原复核）

| # | 位置 | 发现 | 级别 | 处置/现状 |
| --- | --- | --- | --- | --- |
| 1 | `FigureGenerationReconcilerTest.kt:33/61/80/100` ↔ `FigureGenerationReconciler.kt:39-50` | T3 自己的文件对编译红（尾随 lambda 绑 `writeGate: BackupWriteGate`，`()->Long` 型不匹配）→ core:data testDebugUnitTest 整体编译失败；设备轨曾误归因"他线在飞"，两文件均在 T3 工单内。后果：T2 四个 JVM 测试、T4/T5 测试无编译运行证据 | 高·已独立复核 | **已修复**：修复环改 `FigureGenerationReconciler(audit, { NOW })`（now 与 writeGate 各归其位）+ `:96` runBlocking；第 2 轮 K2JVMCompiler+JUnitCore 同版工具链实跑 **OK (7 tests)**。Gradle 门尚无 exit=0 复跑 |
| 2 | `app/build/test-results/testLocalFirstDebugUnitTest/TEST-…BackgroundWriterQuiesceTest.xml`（2026-10-24 22:42:50） | T3 quiesce 半边 app JVM 测试最近 Gradle 产物 **4 例 1 红**（在途写先于返回完成），失败发生在最后一次源码修改之后，无重跑；测试自身有竞态（launch 后立即 quiesce，writer 可能从未启动） | 高·已独立复核 | **未闭合**：修复环第 2 轮零新改动、无该测试绿证据；登记为遗留（§5），下一批修复竞态用例并复跑 |
| 3 | `StudyReviewPlannerExamWindowTest.kt:83` | "无考试日逐位不变"的证据是同二进制指纹对照，非旧输出金样（全树无金样调用方；金样不可行）；且因发现#1 该测试从未运行 | 中·已独立复核 | 结构性支撑成立（ramp≤0 原值返回/扫描读有窗口门/扫描压力仅 ramp>0 生效，复核读码闭合）；**测试执行 UNVERIFIED**，随门禁复跑补证 |
| 4 | 各 `build/outputs/androidTest-results/`（本批无产物） | 设备轨 suite 数字无法与 build 目录 XML 对拍（本批全用裸 am instrument）；旁证全部与声明一致（15/56/6/1/22/5 逐类核对、/tmp 日志可溯） | 中·已独立复核 | 如实登记口径：设备数字以 stdout/日志为据；可行 |
| 5 | `docs/research/2026-10-23-batch-19-record.md`（复核时不存在）↔ T5 summary | T5"导出侧接线登记遗留"的登记尚未落地（known-defects/计划/批次记录均无） | 低·已独立复核 | **本记录（P10）落账**：见 §5 遗留清单 G8 项 |
| 6 | T1/T2/T3(条目)/T5/集成 通过项汇总 | 复核逐项核对全部相符（T1 机器块 JSON 可解析、openMasks 契约不变；T2 六项生产+测试在树且断言实质；T3 KD 条目如实；T5 幂等/不撞键/待核验用例实质、无旁路投影写；集成免接线判定相符） | 低·已独立复核 | 采信为本记录 §2 各轨判定基础 |
| 7 | `app/.../SmartMistakeBookRoot.kt`（diff +38 行 NavHost 动画，import 未跟踪 `core/ui/.../ClaudeMotion.kt`） | 提交期风险：若照搬"共享 clean 文件开工时变脏→整文件纳入"会提交引用未提交文件的动画改造，产生无法编译的提交 | 中·已独立复核 | **提交清单明确排除 Root**（§6）；本批各轨未改 Root（批次标记 grep 0 命中） |
| 8 | `AndroidBackupRepository.kt` deleteAllNonDatabaseFiles 第 10 步（~1028-1040） | deleteAllData 清"待重建"标记在最后一步（库已删、写门已重放行）；若进程在"库已删、标记未删"窗口崩溃，残留标记会让下次启动对空库重跑重建、失败科目每启动重试 | 低·已独立复核 | **新缺陷登记 KD-63**（本批 P10）；建议把标记删除提前到不可逆段之前 |
| 9 | `RestoreSoftReferenceRecheck.kt:192`（finally 删 `-wal/-shm`） | S3 重查 finally 无条件删 staged 库边车——防御性清理；数据结局与旧代码相同（Phase 3 本来只 copy 主文件），不是新数据丢失模式；建议注明防御性质 | 低·已独立复核 | 登记注记（KD-63 附带说明）；重查两侧规则逐项对拍**未发现误拒合法包** |
| 10 | `AlgorithmConstants.kt`（EXAM_CAPACITY_RAMP_MAX_EXTRA_FRACTION） | G6 常量代码内 PENDING_CALIBRATION 标注充分，但 docs/ 全库**零登记**——"唯一口径是代码"会成为后续误当校准值的入口 | 低·已独立复核 | **本批 P10 落账**：决策台账 + 算法台账 §3.29（本记录 §2.5） |
| 11 | `DeviceMigrationDrillInstrumentedTest.kt:810-818`；`DestructiveDeviceTestGuard.kt` | 设备清数据守卫 = "默认跳过 + 显式参数闸"，**代码中无设备身份校验**——"只在 test_device 生效"由 P7 执行纪律保证而非代码保证（与既有 app 守卫同约定，非本批弱化）；守卫顺序已核无误 | 低·已独立复核 | 如实登记边界；杀进程阶段另有独立 `-e smb_d1_kill_stage` 闸，交接件缺失诚实跳过 |
| 12 | `PaperAnswerEvidenceFeedback.kt:18-22`；`InMemoryPaperAnswerRecycleStore.kt:39-48` | T5 重传幂等：答卷侧 `putIfAbsent` 原子；**证据端口只有契约无实现**——并发同 attemptKey 原子去重要到真实落库批次才受考验（契约 KDoc 已写明幂等义务）；杀进程重放由"总是提交+幂等"收敛；"待核验"三重锁死不当有效证据消费 | 低·已独立复核 | 接线批次**必带验收点**：端口实现的并发用例（§5 遗留） |
| 13 | `CanonicalAssetReferenceSites.kt:28-66`；`PendingCaptureDao.kt` @Query；`BackupSnapshotTrimmer.kt` | T2-5 SQL 单源化语义逐位不变——新旧 SQL 逐字一致/德摩根等价/无第三份手抄/KSP 生成物含同 SQL；schema 70 零 schema 达成 | 低·已独立复核 | 通过项记录（编译证明归门禁，未跑） |
| 14 | `StudyReviewPlannerService.kt:140-174/310-330/383-397`；`SecondaryScreens.kt` backupSelectionContentLabel/backupAssetPolicyPhrase | T4 无考试日零变化（代码路径逐位守恒+指纹测试钉住，无旧二进制金样以代码读闭合）与 assetPolicy 显示不误导（读侧先拒非 INCLUDED、写侧恒 INCLUDED、UI 四处同口径、v1/v2 回执不声称策略）均核通过 | 低·已独立复核 | 通过项记录 |

## 5. 遗留 / UNVERIFIED 清单

**工程门（提交前置）**
1. 单测门第 3 轮 `exit=1`：编译红已修但无一次 Gradle `exit=0` 复跑；第 3 轮失败全清单未定。
2. 强门未跑。
3. `BackgroundWriterQuiesceTest` 1/4 红（竞态用例）未修未复跑（发现#2）。
4. core:data 四个新/改 JVM 测试类（`SmbkArchiveCodecTest`/`SmbkSelectionPlanTest`/`CanonicalAssetReferenceSingleSourceTest`/`RestoreSoftReferenceRecheckTest`）、`StudyReviewPlannerExamWindowTest`、T5 全部 37 用例：**无 Gradle 执行证据**（发现#1 连坐，修复后未复跑）。
5. `BackupSelectionUiInstrumentedTest`（app androidTest，11 例）与 `RestoreBackgroundQuiesceInstrumentedTest`：无本批运行证据。
6. T4"无考试日旧行为金样对照"：结构性支撑+指纹测试（未运行）；金样本身不可行，以代码读闭合并如实记录。

**5B-G8（回收半边遗留，接线批次做）**
7. 导出侧接线：答案键链持久化随导出记录（`MistakeExportRecord` 无 answerKey 字段族，本批只落域模型+内存仓储；`MistakeExportJobRunner` 为他线热区只读登记）。
8. UI 入口：零 UI（留 G14/5B 契约后）；G14 不实现、权重不代填（已裁）。
9. 判词通道：仅接口（默认抛 `UnsupportedOperationException`），模型侧不实现（已裁）。
10. `PaperAnswerEvidenceFeedbackPort` 无生产实现；接线批次必带**并发幂等验收**（同 attemptKey 并发提交恰一次计分）。
11. 真实落库仓储（若需新表现为 schema 70，本批零 schema）。

**5B-G6（排程半边遗留）**
12. `EXAM_CAPACITY_RAMP_MAX_EXTRA_FRACTION=0.5` 为 **PENDING_CALIBRATION** 占位（插眼 1 只裁形状/窗口/重要性档；幅度待 Wave X 离线模拟校准，改数属产品裁定面）——已登记决策台账与算法台账。
13. `ExamCalendarEntry` 无 importance 字段、设置屏未接：当前所有考试按高档 1.0 处理（代码 KDoc 如实注明）。
14. 考纲范围匹配依赖 D-4（KB 线），本批不做（计划已裁）。

**KD / 新缺陷**
15. **KD-63（新登记，本批复核发现#8/#9）**：`deleteAllData` 清"待重建"标记时机在第 10 步留窄崩溃窗口（库已删、标记未删 → 空库被当恢复后待重建重跑）；附带 S3 重查 finally 无条件删 `-wal/-shm` 的防御性清理注记。
16. KD-61 保持 **open（收窄）**：quiesce + 图生成对账接门已落；热区直写面（BatchImport/SplitImport 全族、ExportJobRunner、RoomMistakeDetailRepository、RoomTutorToolRunner、CleanRedrawTaskPort、ReviewReminderCoordinator、knowledge 写、RoomModelTaskToolRounds、RoomAgentPendingRequestRepository）、WorkManager 在途 SQL、UI 写、quiesce 超时路径四类残余仍在门外。

**设备 / 环境**
17. 3E 设备侧复验未做（无排计划仪器化；JVM 22/0 已复跑）。
18. 本地任务线"修改"步骤未在设备证据中覆盖。
19. API23/arm64/16KB 页：宿主不可行（仅 android-34 镜像、arm64 仅 nativebridge、PAGESIZE=4096）——如实标注，不伪称覆盖。
20. flavor 四选一仅 localFirst 验证；strictOffline/benchmark/internal 未跑；R8 未跑。
21. 设备数字以 stdout/日志旁证（build 目录无本批 androidTest XML）。

**待裁 / 他线**
22. G5 API 扩展：台账待裁，本批不动。
23. 真实 Provider / PRD-09 真实生图质量：无凭据，**UNVERIFIED**。
24. 阶段 6 UI（LearningMasteryScreen 他线在飞）、5b/5c（feature/review 他线）、UI-00~11（core/ui 他线）、6B-3 app 注入、AUD-08/AUD-06：均未做（计划已裁不做）。
25. `SmartMistakeBookRoot.kt`：他线动画改造引用未跟踪 `ClaudeMotion.kt`——本批提交必须排除（发现#7）。

## 6. 提交清单建议（记录员只读核对 git status 后列出；**提交由主会话执行**）

**前置条件**：单测门 `exit=0` 复跑（发现#1 修复后）+ 发现#2 处置（修复或归因）+ 强门通过。未过门前不建议提交任何代码块。

本批文件（git status 实测：19 M + 21 ?? + 7 文档 = 47 路径；已排除他线）。分块 add（≤6 路径/块）：

- **块 1（T1 文档债）**：`docs/research/2026-10-17-6c2-content-selection-design.md`
- **块 2（T2 域/库单源）**：`core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/BackupSelection.kt`、`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/dao/PendingCaptureDao.kt`、`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/dao/CanonicalAssetReferenceSites.kt`、`core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/backup/SmbkSelectionPlanTest.kt`、`core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/backup/CanonicalAssetReferenceSingleSourceTest.kt`
- **块 3（T2 备份加固 core:data）**：`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/backup/SmbkArchiveCodec.kt`、`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/backup/AndroidBackupRepository.kt`、`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/backup/BackupSnapshotTrimmer.kt`、`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/backup/RestoreSoftReferenceRecheck.kt`、`core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/backup/SmbkArchiveCodecTest.kt`、`core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/backup/RestoreSoftReferenceRecheckTest.kt`
- **块 4（T2 androidTest + app 面）**：`core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/backup/BackupRestoreInstrumentedTest.kt`、`core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/backup/BackupSelectionRestoreInstrumentedTest.kt`、`app/src/main/kotlin/com/tingyun/smartmistakebook/SecondaryScreens.kt`、`app/src/test/kotlin/com/tingyun/smartmistakebook/StorageRestoreFeedbackTest.kt`、`app/src/androidTest/kotlin/com/tingyun/smartmistakebook/BackupSelectionUiInstrumentedTest.kt`
- **块 5（T3 KD-61）**：`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/FigureGenerationReconciler.kt`、`core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/visual/FigureGenerationReconcilerTest.kt`、`app/src/main/kotlin/com/tingyun/smartmistakebook/SmartMistakeBookApplication.kt`、`app/src/main/kotlin/com/tingyun/smartmistakebook/BackgroundWriterQuiesce.kt`、`app/src/test/kotlin/com/tingyun/smartmistakebook/BackgroundWriterQuiesceTest.kt`、`core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/backup/RestoreBackgroundQuiesceInstrumentedTest.kt`
- **块 6（T4 G6）**：`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/StudyReviewPlannerService.kt`、`core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/AlgorithmConstants.kt`、`core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/study/StudyReviewPlannerExamWindowTest.kt`
- **块 7（T5 G8 域）**：`core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/PaperAnswerSheet.kt`、`core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/PaperAnswerKeyChain.kt`、`core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/PaperAnswerRecycleJudge.kt`、`core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/PaperAnswerEvidenceFeedback.kt`、`core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/PaperAnswerSheetRecycler.kt`、`core/domain/src/test/kotlin/com/tingyun/smartmistakebook/core/domain/PaperAnswerSheetIdentityTest.kt`
- **块 8（T5 G8 测试+仓储）**：`core/domain/src/test/kotlin/com/tingyun/smartmistakebook/core/domain/PaperAnswerKeyChainTest.kt`、`core/domain/src/test/kotlin/com/tingyun/smartmistakebook/core/domain/PaperAnswerRecycleJudgeTest.kt`、`core/domain/src/test/kotlin/com/tingyun/smartmistakebook/core/domain/PaperAnswerSheetRecyclerTest.kt`、`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/InMemoryPaperAnswerRecycleStore.kt`、`core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/study/InMemoryPaperAnswerRecycleStoreTest.kt`
- **块 9（设备轨）**：`core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/backup/DeviceMigrationDrillInstrumentedTest.kt`、`app/src/androidTest/kotlin/com/tingyun/smartmistakebook/LocalTaskLineDeviceInstrumentedTest.kt`
- **块 10（记录/台账/KD/PRD）**：`docs/research/2026-10-23-batch-19-baseline.md`、`docs/research/2026-10-23-batch-19-record.md`、`docs/known-defects.md`、`docs/REFACTOR-MASTER-LINE.md`、`docs/agent-first-refactor-decisions-2026-09-23.md`、`docs/research/algorithm-version-ledger.md`
- **块 11（PRD 计划）**：`docs/superpowers/plans/2026-10-19-product-readiness-followup-plan.md`

**明确排除**：`app/src/main/kotlin/com/tingyun/smartmistakebook/SmartMistakeBookRoot.kt`（他线动画改造引用未跟踪 `core/ui/.../ClaudeMotion.kt`，纳入即产生无法编译的提交——复核发现#7）；其余他线热区文件（feature/*、core/ui、BatchImport/SplitImport 族、tools/kb_*、knowledge-production、docs/research 他线未跟踪记录）一律不动。

**安全边界**：Mimosa 无完整扫描结论，不宣称项目安全；本文件不含任何密钥/令牌/凭据。

## 附录：本记录员实际执行的只读核对（可复跑；未运行 Gradle/设备/任何 git 写命令）

```bash
# 基线与漂移（开工时）
git rev-parse HEAD origin/main          # 两值同为 311f7de1b49eafdb63616c0e5196e72b2e994404
git status --porcelain=v1 | wc -l       # 658（他线继续在飞）

# 本批 38 个轨文件 + 设备轨 2 个新测试逐个 git status 对上（19 M + 21 ??，§6 清单来源）
git status --porcelain=v1 -- <本批路径清单>
git status --porcelain=v1 | grep -iE "DeviceMigrationDrill|LocalTaskLine|Prd06|MistakeExportDelivery"
#   → 仅 LocalTaskLineDeviceInstrumentedTest.kt(??) 与 DeviceMigrationDrillInstrumentedTest.kt(??)；
#     Prd06/MistakeExportDelivery 为既有已提交测试（不在 status，符合"既有用例"口径）

# 待同步文档开工时状态（全部 clean，安全编辑）
git status --porcelain=v1 -- docs/REFACTOR-MASTER-LINE.md docs/agent-first-refactor-decisions-2026-09-23.md \
  docs/research/algorithm-version-ledger.md docs/superpowers/plans/2026-10-19-product-readiness-followup-plan.md
#   → 无输出（clean）；docs/research/2026-10-23-batch-19-baseline.md 为 ??（本批 T0）

# 关键代码事实（引用行号来源）
grep -n "EXAM_CAPACITY_RAMP_MAX_EXTRA_FRACTION" core/domain/.../AlgorithmConstants.kt        # :148 =0.5，KDoc PENDING_CALIBRATION
grep -n "EXAM_RAMP_DAYS" core/data/.../StudyReviewPlannerService.kt                           # :123/:160/:323 消费，:800 =14（私有 companion）
grep -n "VERSION" core/domain/.../ReviewPlannerV2.kt | head -3                                # :1055 = "review-planner-v4"；文件 clean（git status 空）
grep -n "PendingVerification\|LOCAL_CHECKED" core/domain/.../PaperAnswerRecycleJudge.kt ...   # :64/:203 待核验档；EvidenceFeedback :49-52 命令 init 校验

# KD 登记簿编号空间
grep -n "^## KD-" docs/known-defects.md | tail -5    # 最高 KD-62 → 新缺陷登记为 KD-63
# KD-61 条目重读（:2439 头部 open；:2466-2508 批次 19 T3 处置 open（收窄））——与 T3 证据一致
```

> 未执行：任何 `./gradlew`、`adb`、`am instrument`、`connectedAndroidTest`；任何 git 写命令。门禁/设备结论均标注来源（工单材料/修复环材料/复核材料），不冒充本记录员实跑。

## 7. 主会话收口（2026-10-25 凌晨，实跑证据）

> 本节由主会话在提交/推送前实测补记；修复环材料与记录员时点（§3"单测门第 3 轮 exit=1、强门未跑"）之后发生的事实以此为准。

### 7.1 门禁复跑（发现#1/#2 修复后的最终树）

- **单测门**（11 模块 `--continue`，主会话实跑）：本批全部绿。core:data 1061 例中 3 失败——`ProductionLexicalLegExportTest.exportProductionV1LexicalScoresAsFusionInput`（MRR 期望 0.6206718346253229 实测 …232）、`Stage1LexicalLabTest.stage1ArmsScoreFrozenGoldenSetAndWriteArtifacts`、`Stage2LexicalScoresExportTest.exportArmAFts5ScoresAsFusionInput`（87 vs 101）——**三处均为他线 KB/dense 在飞金标漂移**（`ProductionLexicalLegExportTest.kt` 本身是他线 M 改动、`tools/dense_build/*` 同批在飞），本批零触碰，点名归因不改。本批关键类当轮 XML 全绿（01:26）：RestoreSoftReferenceRecheck 7/0、CanonicalAssetReferenceSingleSource 2/0、StudyReviewPlannerExamWindow 6/0、InMemoryPaperAnswerRecycleStore 7/0、FigureGenerationReconciler 7/0、SmbkArchiveCodec 32/0、SmbkSelectionPlan 12/0。
- **发现#2 关账**：`BackgroundWriterQuiesceTest` 以 `--tests` 强制重跑 → **4/0 绿**（01:29:07 XML）。修复环给测试加的 `writerStarted` 闩（launch 后确认协程体已执行再 quiesce）消除了"cancel 赶在协程体首行之前到达"的自竞态。
- **强门**（六 androidTest 编译 + `:app:assembleLocalFirstDebug`）：**exit=0**（BUILD SUCCESSFUL）。
- **`BackupSelectionUiInstrumentedTest` 设备复跑**（§5 登记的最后一个未复跑项，2026-10-25 01:4x）：重建双 APK + `adb install -r -t` + `am instrument -e class …localfirst.test/androidx.test.runner.AndroidJUnitRunner`（`emulator-5554`）→ **OK (11 tests)**——T2-6 文案改动后的 UI 面（子集回执「（不含掌握库、会话）」点名断言）在最终提交树上设备实测通过。

### 7.2 提交与推送

- 提交 `0f4fd012`（46 文件：19 M + 25 ?? + 2 新记录 = 5,819 插入/133 删除），分 11 块 add（≤6 路径/块）；**`SmartMistakeBookRoot.kt` 按发现#7 明确排除**（他线 NavHost 动画改造引用未跟踪 `ClaudeMotion.kt`，本批未改）。
- 推送 `311f7de1..0f4fd012`（origin/main，快进一次成功）。
- Mimosa 提交/推送仍无完整扫描结论（scanner_enobufs）——不宣称项目安全。
