# 批次 20 基线核对（2026-10-25，只读复核）

核对对象：`.zcode/plans/plan-2026-10-25-batch-20-4e-js-beta-prep.md` §0。全部结论来自本日本会话实测（命令与文件行号随条附上）；无当前证据的一律标 UNVERIFIED。本核对不跑 Gradle、不改代码。

## 1. HEAD / 工作树 / schema / 70.json

| 项 | 计划 §0 | 实测（本日） | 判定 |
|---|---|---|---|
| HEAD | `866122e9` | `558185c9e2945c52949d265645a23da0fa22521d`（`git rev-parse HEAD`） | **不符（落后一档）**：计划落笔后 main 又前进了一个提交 `558185c9`（kb 批次19 A1 别名臂），计划快照停在 `866122e9` |
| origin/main | = HEAD | `558185c9...`（同 HEAD，`git rev-parse origin/main`） | 符合（本地与远端同步，仅都前移了一提交） |
| 工作树 M 计数 | 157 M | `git status --porcelain | grep -c '^ M'` = **157**；另有未跟踪 443、staged 0 | 符合（157 精确一致） |
| DB schema | 69 | `core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/` 最大号 **69.json**；`version=69`，`identityHash=6633b7c2fe223041c4a2927203853f4f`（python 读 JSON 实测） | 符合 |
| 请求 schema | 18 | `core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/ModelTasks.kt:546` `TUTOR_VISUAL_CONTEXT_SCHEMA_VERSION = 18`；`:547` `CURRENT_SCHEMA_VERSION` 同指 18 | 符合 |
| 70.json 占用 | （预计零 schema） | `git ls-files '*70.json'` 空；`git status --porcelain -- core/database/schemas/` 空（无 M/无未跟踪） | **70.json 未占用**；但见 §2——core/database 主源码六文件被另一线改脏，若该线先动 schema 会抢占 70 |

## 2. 热区快照（157 M 按模块归组 vs 计划 HOT_ZONES）

归组命令：`git status --porcelain | grep '^ M' | sed 's|^ M ||' | awk -F/ ...`。当前分布（→ 与计划 §0 数字对比）：

| 模块 | 现在 M | 计划 §0 | 增减 |
|---|---|---|---|
| feature/tutor | 28 | 19 | +9 |
| feature/library | 19 | 15 | +4 |
| feature/capture | 10 | 7 | +3 |
| core/ui | 10 | 9 | +1 |
| core/data（含 test） | 10 | （未列主源码） | **新热区**（见下） |
| app/src | 10 | 6 | +4（新增 LearningMasteryScreen、ReviewReminderCoordinator/Screen、SchedulingSettingsScreen、StartupStateBanner；Root 仍在列） |
| feature/review | 9 | 8 | +1 |
| docs/research | 9 | （未列） | **新热区** |
| core/database（含 androidTest） | 9 | 仅点名 CleanRedrawTaskContract.kt | **新热区扩大**（见下） |
| docs/current | 7 | （4E 线文档） | +3 |
| core/export | 5 | （未列） | **新热区** |
| docs/superpowers | 4 | （未列） | 新 |
| core/domain | 4 | 4 | 持平（BatchImportRepository、CleanImageGenerator、MistakeDetailRepository、TutorJudgedReview） |
| tools / settings.gradle.kts / feature/profile / knowledge-production / docs/*.md / .gitignore | 3/1/1/1/12+/1 | （未列） | 新增散点 |

**对照计划 HOT_ZONES 的新增冲突（计划没点名、现在已脏、与本批相关的）**：

1. `core/database/src/main/.../port/CleanRedrawTaskPort.kt` —— 计划 §0 只点名 `CleanRedrawTaskContract.kt`；Port 现也 M。**T3 若触碰 clean_redraw 契约族，须把 Port + Contract 两个 M 文件都整文件纳入并注明保留他线 hunk**。
2. `core/database/src/main` 另四文件 M：`RoomBatchImportStore.kt`、`TutorConversationDatabaseContract.kt`、`dao/BatchImportDao.kt`、`dao/TutorConversationDao.kt`（+3 个 FTS androidTest）。这条线在改 DB 主源码——**70.json 有被该线先占用的现实风险**。
3. `core/data/src/main` 两文件 M：`mistake/RoomMistakeDetailRepository.kt`、`study/RoomTutorToolRunner.kt`。后者在 4E 计划 WG-04 的修改清单里（`docs/superpowers/plans/2026-10-18-flexible-js-drawing-implementation.md:244`）——4E 邻接文件已脏。
4. `core/export` 主源码 M：`MistakeExportJobRunner.kt` + build.gradle.kts + 3 个测试（PRD-04/WG-07 邻接区）。
5. `docs/research` 9 个 M 均为历史记录文档（2026-09-28 至 10-15），与本批新增 baseline 文件不同名，无落盘冲突。
6. `settings.gradle.kts`、`.gitignore`、`tools/` 3 个 python M——提交流程需注意分块归属。

**4E 干净区复核（防回卷）**：对计划 §0 点名的全部 4E 主管线文件逐一 `git status --porcelain | grep <名>`，**全部仍 clean**：

- `core/data/.../capture/RoomCaptureWorkflowRepository.kt` ✓
- `core/data/.../capture/ConfiguredCleanImageGenerator.kt` ✓
- `core/data/.../model/AttachedImageGenerator.kt`、`AttachedImageGeneratorFactory.kt` ✓
- `core/data/.../model/TutorFigureGenerator.kt` ✓
- `core/data/.../model/OpenAiModelProtocol.kt` ✓
- `core/data/.../model/OpenAiImageGenerationChannel.kt` ✓
- `core/data/.../visual/` 整目录 clean（`DefaultFigureGenerationCoordinator.kt`、`FigureGenerationAuditRecorder.kt`、`FigureGenerationReconciler.kt`、`RoomFigureDispatchLedger.kt`、`RoomFigureGenerationAuditRecorder.kt`）✓
- `core/data/.../study/StudyReviewPlannerService.kt` ✓
- `app/.../SmartMistakeBookApplication.kt` ✓
- `core/ui/.../ClaudeMotion.kt` 仍 untracked（`?? core/ui/src/main/java/com/tingyun/smartmistakebook/core/ui/ClaudeMotion.kt`，另有 `?? feature/tutor/.../ClaudeMotionInstrumentedTest.kt`）——Root 禁改禁提交约束依旧成立。

## 3. 4E 现状复核（防"已实现"）

### 3.1 ⚠️ 重大不符：T1/WG-10 的"三处直接图像执行旁路"已不存在

计划 §4 T1 的前提（"三处直接图像执行……全部改经 coordinator/ledger"待做）**已过时**。实测：

- 全库主源码 grep `channel\.(generate|redrawClean)`（core/data/src/main + app/src/main）：唯二命中在协调器**内部** `core/data/.../visual/DefaultFigureGenerationCoordinator.kt:339`、`:349`——那是合法执行位置。三处旁路的直调点一个都不剩。
- `AttachedImageGeneratorFactory.kt`（全文 143 行）：类注释（`:17-27`）明确"**不再有图像后端**"，cache-only 读取 + 显式重生成经注入的共享 `FigureGenerationCoordinator`（`:116` 注入、无任何 channel 构造）。
- `TutorFigureGenerator.kt:406`：`val outcome = effectiveCoordinator.generate(generationRequest, proposal)`；`:48-49` 注释原文"消灭的失败：**此前**两处直接 `channel.generate`（本类与 `AttachedImageGeneratorFactory`）……统一到 coordinator 后，没有预留的图像 HTTP 调用为 0"。
- `ConfiguredCleanImageGenerator.kt:140`：`effectiveCoordinator.generate(generationRequest, proposal)`；工厂 `:264-274` 接收 app 注入的共享 coordinator；`:62-75` 未注入时也走 `DefaultFigureGenerationCoordinator`（同一 policy/ledger 代码）。
- 静态守卫已在树：`core/data/src/test/.../model/ImageDispatchReservationGuardTest.kt:25,177`（正则静态对拍"全仓生产源码里 generate/redrawClean 调用必须带 dispatch"）。
- 迁移时间线：三文件最后改动均为批次 17 提交 `ecb1b3e6`；`docs/REFACTOR-MASTER-LINE.md:71` 4E 行记载三入口统一转调在**批次 14（`759148ba`）**落树、"fake 后端 wiring 测试过、无预留图像 HTTP 调用=0"（`FigureGenerationWiringTest`）。

**结论**：T1 按字面已是已实现工作。WG-10 验收（4E 计划 `:304-306`）中仍开放的只剩：真实 Provider 两方法/跨入口/换方法/预算验证（无 key → 照旧 UNVERIFIED）与完成台账登记。**建议 T1 改写为"复验 + 台账登记"而不是"迁移"**。

### 3.2 T2/WG-02 锚点（确属未做）

- CAPTURE_PARSE 解析器：`core/data/.../model/OpenAiModelResponseParsers.kt:148-241` `toCapturedDocument`。现状：`pageIndex`→`sourceAssets` 查表（`:159-160`）、region 必须落在 selectedRegion 内（`:162-165`）；`figure` 块只转写为 `ContentBlock.Figure(title/alternativeText/schema)`（`:199-204`）；每块落 `QuestionBlockEvidence(sourceAssetId, sourceRegion, writingLayer, provenance, confidence, reviewStatus, producerVersion)`（`:221-233`）；输出 `CaptureParseOutput(capturedDocument, modelVersion)`（`:234-240`）——**没有任何 FigureGenerationProposal 绑定**，与 WG-02 验收（4E 计划 `:226-229`："CAPTURE_PARSE需要图时返回同型proposal……"）对比确认缺口属实。
- 请求侧：`OpenAiModelTaskAdapters.kt:46`（prompt=PARSE_PROMPT）、`:64`（parse 分派）；出口计量 `core/model/.../ModelEgress.kt:90,297`。主文件 `RoomCaptureWorkflowRepository.kt`（1190 行）内无 `CAPTURE_PARSE` 字样——转写任务不在该文件内装配。

### 3.3 T3/WG-03 锚点（两条都确认仍存在，属"真缺"）

- **TEXT_ONLY 捷径仍在**：`core/data/.../capture/CleanRedrawTaskRunner.kt:281-298`——非显式路径先分类（`:283-289`），`kind != WITH_FIGURE` 即 `finish(SUCCEEDED)` 直接当生成完成（`:293-297`，注释"TEXT_ONLY：触发范围口径说'不动作'"）。显式重绘强制路径（`runExplicit :218-229`，`forceRedraw` 跳过分类）不受影响。这正是计划 T3 要移除的捷径；注意 4E 计划 WG-03 验收原文（`:235`）要求"NO_FIGURE 记无需生成的终态，不伪报已产图"。
- **ByteArray 回调仍在**：域层 `core/domain/.../CleanImageGenerator.kt:62-63` `CleanImageResult(bytes: ByteArray, ...)`；`ConfiguredCleanImageGenerator.kt:144-157` `toCleanImageResult()` 仍回字节 + attemptId。T3 的"升级为类型化 FigureArtifactRef"未做。
- 契约族文件现状：`CleanRedrawTaskContract.kt` M（计划已知）+ `CleanRedrawTaskPort.kt` **M（计划未点名，新冲突）**。

### 3.4 契约与验收原文位置

- `docs/current/visual-generation-contract.md`（201 行；状态行 `:3`："方案确定……目前仅执行 IMAGE_MODEL，JS 仍不可用，正式全链未验收"）。与 WG-02/03/10 直接相关的口径：方法只有 JS/IMAGE_MODEL、decision 四值（`:29`）；CAPTURE_PARSE 可附同型 proposal、problemKind 不再硬编码（`:35`）；源证据 VisualSourceBasis 差距（`:37`）；预算六派发/终答预留（`:138`）；类型化 FigureArtifactRef、token/费用另记（`:140`）；`figure_generation_attempt` 列口径（`:147`）；完整门含 API23/arm64/16KB/R8、六类截图样例（`:185`）。
- 4E 计划 WG 验收原文：WG-02 `docs/superpowers/plans/2026-10-18-flexible-js-drawing-implementation.md:222-229`；WG-03 `:231-240`；WG-10 `:302-306`。`:308` 明示"六类截图仅作工程样例"；`:328`（§12）明示"本轮没有验证任何WG项全部完成"。

## 4. PRD-09 现状

- **六类样例**：目前只有设计表——4E 计划 `:154-164`（截图1 比例条／2 F-x 面积／3 解题流程／4 问答(4E.2)／5 受力图／6 拔塞阶段＋新组合波叠加）。"落实为可核查题意夹具"在 PRD-09 中是**未勾选**项（`docs/superpowers/plans/2026-10-19-product-readiness-followup-plan.md:131`）；仓库内未找到六张截图/夹具实体文件（docs、.jez 下 png/jpg/webp 搜索为空）。
- **桌面 33 帧/13 检查研究证据**：`docs/research/2026-10-18-flexible-js-drawing-research.md:43-49`——QuickJS-emscripten 0.32.0、3 个手写程序 × 11 状态 = **33 场景**、13 项检查（原文用"33 场景"，计划转写为"33 帧"）；实物在 `.jez/artifacts/flexible-js-drawing-2026-10-17/output/`（实测存在：`results.json`、`programs.json`、`cork.svg`、`work.svg`、`waves.svg`、`preview.html`）。T4 若要复用，移植面 = 该目录的三个手写程序与检查清单。
- **PRD-09 验收原文**：`docs/superpowers/plans/2026-10-19-product-readiness-followup-plan.md:126-140`（120 盲测冻结、开发集另设、独立事实预期与评审来源、首图 p50/p95/总时长/token/费用记录、费用未知记未知、六派发总门不变）。
- **既有生图成本/预算记账字段**：`core/database/.../entity/FigureGenerationEntities.kt`——`figure_generation_operation`（`:60-75`：policy_version、status、reserved_remote_dispatches、used_remote_dispatches、final_answer_reserved、两时间戳，均"审计镜像、不是预算权威"）+ `figure_generation_attempt`（`:127-154`：attempt_id、operation_id、attempt_ordinal、method、status、provider_snapshot、model_snapshot、source_sha256、input_fingerprint、output_asset_id、reservation_id、diagnostics、started/ended）。**缺口：无 token/费用/首图延迟字段**——PRD-09 T5 要的记录面目前不存在。

## 5. AUD-17 口径与 3E 排计划器可测入口

- **AUD-17 口径原文**：`docs/research/2026-09-28-algorithm-fix-plan.md:491-496`——"按SchedulingEvaluation实际筛选规则输出**原始数、MODEL_JUDGED/REVEAL排除、首条/同日重复、缺时间/缺状态、最终可预测长程数**；400门只看最后一列"；另 `docs/REFACTOR-MASTER-LINE.md:78,150`（原始/排除/可预测数分列；不劣采纳与回退实测）、`docs/research/2026-10-11-new-stages-spec.md:194`（不把 MODEL_JUDGED 凑入 400 门）。筛选规则实现位：`core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/SchedulingEvaluation.kt`（T9 只读查询面应与此同口径）。
- **3E 排计划器可测入口（T10 设备仪器化构造点）**：`core/data/src/main/.../study/StudyReviewPlannerService.kt`——`internal class` 构造器 `:53-71`，依赖 15 项（`StudyDatabasePort`、learnerId、studyZoneId、clock、reviewTimeBudgetSeconds、planningDecay、`ReviewPlannerV2`、`KnowledgeNodeScorer`、`LogDurationModel`、`ReviewLogSink`、`SchedulingSettingsStore?`、`HLRPredictionAuditService`、`PredictionAuditSink`、`KnowledgePrerequisiteReader`、`learnerSnapshot`）。公共 API：`createReviewPlan(mistakes, learnerSnapshot, planningContext)` `:301`、`currentReviewPlan :259`、`planningContext :613`、`examCapacityBudgetSeconds :153`（G6 考试爬升纯函数）。生产装配点：`RoomBackedStudyExperienceRepository.kt:223`。既有缝：JVM 测试 `StudyReviewPlannerExamWindowTest.kt:341` 直接构造；**androidTest 目前无任何直接构造该服务的用例**（grep core/data/src/androidTest 为空）——批次 19 设备侧 UNVERIFIED 的缺口属实，T10 需新写。

## 6. 原生库对齐现状（litert .so 与工具）

- **core:data 原生库清单**（实测 `core/data/build/intermediates/merged_native_libs/debugAndroidTest/.../out/lib/`）：
  - arm64-v8a / armeabi-v7a / x86_64 各 5 个：`libLiteRt.so`、`libLiteRtClGlAccelerator.so`、`liblitert_jni.so`、`libmlkit_google_ocr_pipeline.so`、`libdatastore_shared_counter.so`
  - x86 只有 2 个：`libdatastore_shared_counter.so`、`libmlkit_google_ocr_pipeline.so`（**litert 族不出 x86**）
  - 计划 §0 只点名 `libLiteRt.so`/`liblitert_jni.so`——实际面更大（+ClGlAccelerator、+mlkit OCR、+datastore counter），QuickJS 引入后的 .so 总数按此基数估。
- **litert 版本与引入方式**：`gradle/libs.versions.toml:32-41`——litert 2.1.6；litert-api 因 AGP9 namespace 冲突改走"构建期派生的本地 AAR"。
- **对齐检查工具可用性（实测）**：
  - `zipalign` ✓：`C:\Android\Sdk\build-tools\37.0.0\zipalign.exe`（build-tools 34/36/37 并存；usage 确认支持 `-P <pagesize_kb>` 16KB 档）。
  - `llvm-readelf`/`llvm-objdump` ✗：`C:\Android\Sdk` 下**无 ndk 目录**，Git Bash PATH 无 readelf/objdump/binutils。**ELF 级 PT_LOAD/GNU_RELRO 检查当前本机无工具**——T8/R4 需先装 NDK 或另寻工具，此为基线缺口（研究文档 `2026-10-18-flexible-js-drawing-research.md:70` 也指出 GNU_RELRO 余数才是 16KB 崩溃点，zipalign 过 ≠ 库对）。
- 各 .so 的 16KB 对齐实际状态：**UNVERIFIED**（本核对只做清单，未运行任何 ELF 检查工具）。

## 7. 与计划 §0 不符处汇总

1. **HEAD 前移一提交**：§0 写 `866122e9`，实测 `558185c9`（= origin/main，同步无漂移）。计划自带"开工前复跑核对"条款，此为正常演进，但开工判据应以本基线为准。
2. **T1 前提失效（最重要）**：§4 T1 所述"三处直接图像执行旁路"自批次 14/17 起已全部经 coordinator/ledger（证据见 §3.1），该任务应改写为复验+台账登记，否则会"实现"一遍已有代码。
3. **热区扩大**：core/database 主源码 6 文件 M（含计划未点名的 `CleanRedrawTaskPort.kt`）、core/data 主源码 2 文件 M（含 4E 邻接的 `RoomTutorToolRunner.kt`）、core/export 5、docs/research 9、settings.gradle.kts 等——提交分块与整文件纳入清单须按 §2 重算。
4. **70.json 风险面**：70.json 当前未占用，但 core/database 正被另一线改主源码；零 schema 预期与该线的 schema 动作存在竞争。
5. **计划 §0 的 litert 清单不完整**：实际 .so 共 5 种（含 mlkit OCR 与 datastore counter），且 litert 族不覆盖 x86 ABI。
6. **工具缺口**：本机无 NDK/llvm-readelf，T8 的 ELF 对齐核查工具未就位（zipalign 可用）。
7. **其余核对项全部符合**：157 M 计数、DB schema 69（identityHash `6633b7c2...`）、请求 schema 18、4E 干净区全部文件仍 clean、ClaudeMotion.kt 仍 untracked、TEXT_ONLY 捷径与 ByteArray 回调仍在（T3 属真缺）、CAPTURE_PARSE 无 proposal 绑定（T2 属真缺）、PRD-09 夹具未落仓、AUD-17/3E 入口如文。

## 8. 复核修复环补记（2026-10-25）：设备轨运行数字未落盘——登记与义务

> 本节由复核修复环写入（第 1 轮复核发现 F1，非低）。只登记实测事实与义务，**不声称任何设备轨数字为真**。

### 8.1 实测（修复环本会话命令与输出）

- 全仓 `grep -rn "52/52\|78/78" docs/ out/`：仅命中 `docs/kb-batch11-2026-10-14.md`、`docs/kb-batch15-2026-10-21.md`、`docs/scenario-registry.md`（历史批次/场景登记），**非本批设备轨**。
- `core/data/build/outputs/androidTest-results/connected/debug` mtime=2026-10-20（陈旧）；`out/plan.md`=2026-09-20、`out/report.md`、`out/verify.jsonl`=2026-09-29（9 月旧工作流）；`.tmp_t10/part01.txt` 实读为讲义 OCR 文本（"第03讲 分子结构与性质、化学键"），非 T10 日志。
- 根因：设备轨协议（计划 §5）是 `assembleDebugAndroidTest + adb install -r -t + am instrument` 直跑，**不产生 XML 产物**——运行输出只存在于编排器内存（`deviceOutcome.evidence`），进程结束即不可审计。

### 8.2 义务（记录阶段必须执行）

批次记录（`docs/research/2026-10-25-batch-20-record.md`）必须逐步落盘设备轨五组运行的**命令+exit 码+用例数**：①QuickJS .so 装载与沙箱/硬终止/内存上限；②WG-02/03 生图链回归（fake 后端）；③T10 3E 排计划用例；④备份/迁移回归抽核；⑤R8 冒烟。**落盘之前，上述数字（52/52、78/78、T6 端到端、T10 1绿2红、QuickJS 17/17 等）一律 UNVERIFIED，不得写入任何"已验证/完成"结论。**

### 8.3 T10 崩溃机制（读码证明可达；未执行）

`core/domain/.../NewIntroductionPolicy.kt:68` `require(grantedSliceSeconds >= 0)`；`:154` `grantedSliceSeconds = effectiveSlice - remaining`；oversized 分支 `:135-137` `sliceBudget = topDuration.coerceAtMost(timeBudgetSeconds)` 而 `capBudget = topDuration` 无上限——全部候选 `estimatedDurationSeconds > timeBudgetSeconds` 时全数 deferred，remaining 停在 timeBudget > effectiveSlice → granted < 0 → IllegalArgumentException。时长上界缺失：`:52-53` 仅 require>0。具体触发例：timeBudget=300、单候选 duration=600 → granted = 100−300 = −200。**T10 实跑 2 红是否即此崩溃：UNVERIFIED（无设备+修复环 Gradle 禁，无法复跑）。**

### 8.4 复跑命令清单（设备轨可执行）

协议照计划 §5：`gradlew.bat <模块>:assembleDebugAndroidTest` + `adb install -r -t` + `adb shell am instrument -w -e class <类名>`（禁 connectedAndroidTest）。已知类名示例：T10 = `com.tingyun.smartmistakebook.core.data.study.StudyReviewPlanner3EScanInstrumentedTest`；其余组类名以各轨 evidence 与 `CleanRedrawTaskInstrumentedTest` 等本批新增用例为准。
