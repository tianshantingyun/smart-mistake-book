# 批次 21 基线核对（2026-10-25，基线核对员）

只读核对，未跑 Gradle/测试/构建。所有行号与 git 状态均为本轮实读实测；与计划 §0（`.zcode/plans/plan-2026-10-25-batch-21-kd64-g5a-quickjs.md`）逐项对照，不符处在 §8 汇总。工作树中存在 `.worktrees/*`、`.artifacts/*`、`build/agent-batch11/*` 等历史副本目录，本报告所有 grep 均已排除，结论只基于主树。

## 1. HEAD / 工作树 / DB schema / 请求 schema / 70.json

| 项 | 实测 | 命令/证据 |
|---|---|---|
| HEAD | `ce01508f622e290ceaec580122a84a19120eeb61`（2026-10-25 22:49 +0800，批次20 记录补推送结果） | `git rev-parse HEAD` |
| origin/main | `ce01508f622e290ceaec580122a84a19120eeb61`（同上） | `git rev-parse origin/main` |
| 工作树 | **160 M + 448 ??**（无 staged 冲突标记） | `git status --porcelain \| awk '{print $1}' \| sort \| uniq -c` |
| DB schema | `STUDY_DATABASE_VERSION = 69`（`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabase.kt:134`，`version =` 于 `:233`）；导出 schema 最高 `69.json`；**StudyDatabase.kt git 干净** | `git ls-files`、`ls core/database/schemas/...`、`git status --porcelain` |
| 70.json | **未占用**：`git ls-files '*70.json'` 空、`core/database/schemas/` 无 dirty/未跟踪、磁盘无 70.json | 三路空输出实测 |
| 请求 schema | `CURRENT_SCHEMA_VERSION = TUTOR_VISUAL_CONTEXT_SCHEMA_VERSION = 18`（`core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/ModelTasks.kt:546-547`）；**ModelTasks.kt git 干净** | `sed -n 540,555p` + porcelain |

注意：DB 版本常量文件干净，但 **core/database 主源码另 5 文件 M**（见 §2）——70.json 的竞争风险载体是那些文件，不是 StudyDatabase.kt 本身。

## 2. 热区快照 vs 计划 HOT_ZONES

计划 §0 热区（他线在飞，禁改）与当前实测对照（`git status --porcelain` 全表实数）：

| 热区 | 计划 §0 | 当前实测 | 漂移 |
|---|---|---|---|
| feature/tutor | 19 | **28** | +9 |
| feature/library | 15 | **19** | +4 |
| core/ui | 9 | **10**（build.gradle.kts + 9 kt） | +1 |
| feature/review | 8 | **10** | +2 |
| feature/capture | 7 | **10** | +3 |
| app | 6 M（含 Root） | **13** | +7 |
| core/domain | 4 M | 4 | 一致 ✓ |
| docs/current | 7 M | 7 | 一致 ✓ |

计划 HOT_ZONES **未列、当前实脏**（新增冲突面）：

- **core/database：8 M**——`RoomBatchImportStore.kt`、`TutorConversationDatabaseContract.kt`、`dao/BatchImportDao.kt`、`dao/TutorConversationDao.kt`、`port/CleanRedrawTaskPort.kt` + 3 个 FTS androidTest（`LibraryFtsCountPathInstrumentedTest.kt`、`LibraryFtsCountPathScale50kInstrumentedTest.kt`、`LibraryFtsRankingPathInstrumentedTest.kt`）。这是 70.json 竞争风险的直接载体（batch-20 baseline §2 曾点名"另四文件"，现又多出 `CleanRedrawTaskPort.kt`）。
- **core/export：5 M**（build.gradle.kts、MistakeExportJobRunner.kt、3 个 Pdf 测试）。
- **core/data 测试面：8 M**——计划只点名 main 两文件（`RoomMistakeDetailRepository.kt`、`RoomTutorToolRunner.kt`，仍在）；另有 5 androidTest（BatchImport/SplitImport×2/ModelTask/StudySubmission）+ 3 test（`GoldenRetrievalJvmTest.kt`、`OpenAiNativeToolsProtocolTest.kt`、`TutorToolRequestDualParseTest.kt`）。
- **docs/research 9 M**、docs/superpowers 4 M、docs/history 3 M、docs 根散点 11 M、docs/design 1 M。
- **tools**：`tools/tests/test_kb_merge_candidate_verdicts.py`、`tools/kb_build/merge_candidate_verdicts.py`、`tools/dense_build/gen_device_parity_fixture.py` 各 1 M（`tools/figure_eval/**` 仍干净 ✓）。
- **根级**：`settings.gradle.kts`、`.gitignore`、`dynamic_gui_architecture_plan.md`、`knowledge-production/wusan/materials.jsonl` 各 1 M；feature/profile 1 M（`ProfileRoute.kt`）。
- **未跟踪分布**：core/ui 23（含 `ClaudeMotion.kt`）、feature/tutor 20、docs/research 12、tools/kb_coverage 11、feature/library 7、app/src 4；**core/domain|data|model|database 下零未跟踪**。另有 `x/`、`nul/`、`tmp*/` 等杂项未跟踪（Windows 产物，与本批无关但占 ?? 计数）。

**core/domain 开工即脏 M 全路径（4 个，全量点名）**：

1. `core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/BatchImportRepository.kt`
2. `core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/MistakeDetailRepository.kt`
3. `core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/SchedulingEvaluation.kt`
4. `core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/TutorJudgedReview.kt`

core/domain 无其他 M、无未跟踪；计划 §0"本批关键干净区"中 `NewIntroductionPolicy.kt`、`AdaptiveQuestionSelector.kt`、`VisualExecutionEngine.kt`、`KnowledgeReviewQueue.kt`、`StudyExperienceRepository.kt` 均 git 干净（逐个 porcelain 实测）。

Root→ClaudeMotion 引用属实：`app/src/main/kotlin/com/tingyun/smartmistakebook/SmartMistakeBookRoot.kt:13-14` import `core.ui.ClaudeMotion`/`LocalClaudeMotionEnabled`，`ClaudeMotion.kt` 未跟踪于 **core/ui**（`?? core/ui/src/main/java/.../ClaudeMotion.kt`），另有 `?? feature/tutor/.../ClaudeMotionInstrumentedTest.kt`。

## 3. KD-64 与 NewIntroductionPolicy.kt 现状

**条目全文**：`docs/known-defects.md:2551-2556`（KD-64，open，2026-10-25 批次 20 设备轨 T10 登记，中），git 干净。要点：`IllegalArgumentException: Granted slice must not be negative`，栈 `IntakeDecision.<init>(NewIntroductionPolicy.kt:68) ← decide(:151) ← StudyReviewPlannerService.createReviewPlan(:435)`；机制 = 全超尺寸死锁分支 sliceBudget 被 coerce 但 capBudget 无上界 + granted = effectiveSlice − remaining 可为负；触发例 timeBudget=300、单候选 duration=600。

**当前源码锚点**（`core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/NewIntroductionPolicy.kt`，git 干净，HEAD 态）：

| KD-64 引用 | 条目写法 | 当前实测 | 判定 |
|---|---|---|---|
| require | `:68` | `:68` `require(grantedSliceSeconds >= 0) { "Granted slice must not be negative" }`（IntakeDecision init） | **精确命中** |
| decide() 返回 | `decide(:151)` | `:151` `return IntakeDecision(` | **精确命中** |
| granted 计算 | `:154` 附近 | `:154` `grantedSliceSeconds = effectiveSlice - remaining,` | **精确命中** |
| 全超尺寸死锁分支 | `:122-129` | **`:123-130`**（`allOversized` 判定 `:123-124`；`val (sliceBudget, capBudget) = if (allOversized) { topDuration.coerceAtMost(timeBudgetSeconds) to topDuration } else { effectiveSlice to effectiveCap }` `:125-130`；`remaining = sliceBudget` `:131`） | **漂移 +1 行**，形状不变 |
| 时长仅 require>0 无上界 | `:52-53` | init 块 `:52-56`，`require(estimatedDurationSeconds > 0)` 在 `:53` | **基本命中**（块界略宽） |

调用点：`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/StudyReviewPlannerService.kt:435` `val decision = NewIntroductionPolicy.decide(`，该文件 git 干净。机制复算（读码）：topDuration > timeBudgetSeconds 时 sliceBudget = timeBudgetSeconds，候选 duration ≤ capBudget（= topDuration）故不被 cap 拒，但 duration > remaining → deferred、remaining 停在 timeBudgetSeconds，granted = effectiveSlice − timeBudgetSeconds < 0 → `:68` 抛。与条目描述一致。

## 4. G5 现状

**AdaptiveQuestionSelector 契约**（`core/domain/.../AdaptiveQuestionSelector.kt`，git 干净）：

- 请求 `AdaptiveSelectionRequest`（`:82-100`）：`learnerSnapshot`、`targetKnowledgeNodeIds`、`requestedTargetKind`、`userRequestedQuestion`（fail-closed 授权）、`candidates`、`decisionAtEpochMillis`、`recentItemFamilyIds`、`deterministicSeed`、`prerequisiteStabilityDaysByNode`（KF-16 读侧先修稳定度，默认空表）。
- 候选 `AdaptiveQuestionCandidate`（`:54-65`）：`assessmentItemId`、`knowledgeNodeIds`、`itemFamilyId`、`targetKind`、`contentVerified`、`answerWouldBeRevealed`、`informationValue`、`predictedCorrectness: PredictedCorrectnessInterval?`、`fatigueCost`、`additionalEligibleTargetKinds`。
- 走廊判据 `withinHardChallengeCorridor`（`:238-253`）：FOUNDATION **0.55..0.85**，TRANSFER/BOUNDARY/COUNTEREXAMPLE **0.35..0.75**，CALIBRATION 恒过；要求 `prediction.isCurrent` 且区间整体落在走廊内（lower ≥ start && upper ≤ end）。评分 `2.0×informationValue + (1−|midpoint−0.7|) − fatigueCost`（`:260-263`），FNV 种子决胜（`:265-269`）。
- `VERSION = LearningCoreVersions.SELECTOR_COMPOSITE`（`:300`）。批次 20 对该文件零改动（与算法台账声称一致）。

**assessment_item_snapshot 写口**：`core/data/.../study/KnowledgeQuizItemStore.kt`（git 干净）`save()` `:109` → 既有端口 `StudyDatabasePort.saveAssessmentItemSnapshot`（实现 `RoomStudyDatabase.kt:1567`）与 `overwriteAssessmentItemSnapshot`（`:1585`）——复用既有快照表，零新表；读口 `read()` `:42`、`readSnapshot()` `:83`。

**作答证据台账**：表 `learner_chat_evidence`（`core/database/.../entity/LearnerChatEvidenceEntity.kt:30`，git 干净）。字段映射（G5-A 预测器输入面）：**对/错** → `direction`（POSITIVE/NEGATIVE，由 `knowledgeQuizMasteryVerdict` 本地机械判定，见 `KnowledgeQuizFeedbackWriter.kt:70-72`）；**时间** → `created_at_epoch_millis`；**选项数 → 台账无结构化列**——已选选项仅以文本落在 `reason_markdown`（"学生选择 $selectedChoiceId"），选项数须另读 `assessment_item_snapshot` 题面（`KnowledgeQuizItemStore.readSnapshot` 的 `KnowledgeQuizAnswerSpec`）；权重/置信/锚定/拒绝理由在 `weight`/`confidence`/`anchor_class`/`rejected_reason`。**唯一读入口**：`readChatEvidenceByLearner`（声明 `core/database/.../ModelTaskDatabasePort.kt:221`，实现 `RoomStudyDatabase.kt:1774`，均 git 干净）；审计对账读口 `KnowledgeQuizEvidenceAudit.scan()/scanRows()`（`KnowledgeQuizEvidenceAudit.kt:58/:90`，只读）。写侧唯一入口 `KnowledgeEvidenceWriter`（D-M M4），测验通道语义输入 `KnowledgeQuizFeedbackWriter.submit`（`:66-...`，键信任门 PRD-02）。

**LearnerSnapshot 与 KnowledgePrerequisiteReader 读面**：

- `LearnerSnapshot`：`core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/LearningState.kt:1122`（`@Serializable`，git 干净）。字段：`learnerId`、`problemMemoryStates`、`knowledgeMasteryStates`、`checkpoint`、`knownLedgerHeadSequence`、`generatedAtEpochMillis`、`correctionWatermarkEpochMillis`、`freshness`、`projectionStatus`、`applied*Records`。
- `KnowledgePrerequisiteReader`：`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/KnowledgePrerequisiteReader.kt`（**internal**，git 干净），唯一入口 `graphFor(knowledgeNodeIds): KnowledgePrerequisiteGraph`（dependent→prerequisites + nodesById，含前置节点行回取、按 subject 分区 + 256 分块）；排程侧（StudyReviewPlannerService）与快照侧（StudySnapshotBuilder `:31` 注入）共用同一实例。
- `learnerSnapshot()` 以 `suspend () -> LearnerSnapshot` lambda 注入 `StudyReviewPlannerService`（`:70`）与 `StudySnapshotBuilder`（`:31`）；快照构建 `StudySnapshotBuilder.kt` git 干净。

**知识点复习会话启动 seam——feature/review VM 不是唯一 seam，core:data 有干净 seam**：

- 队列装配点：`StudyReviewPlannerService.currentKnowledgeReviewPlan(...)`（`core/data/.../study/StudyReviewPlannerService.kt:638`，**git 干净**）——今日错题绑定 KC ∪ 全库扫描第二档、F17 配额 + F8 时间预算双封顶、退役 KC 过滤、可出题过滤都在这里完成，产出 `KnowledgeReviewSessionPlan`（定义 `core/domain/.../KnowledgeReviewQueue.kt:47`，干净）。
- 链路：app `SmartMistakeBookRoot.kt:777/818` 调 `repository.currentKnowledgeReviewPlan`（接口 `core/domain/.../StudyExperienceRepository.kt:518`，实现 `RoomBackedStudyExperienceRepository.kt:999` → plannerService，两文件均干净）→ 宿主 `KnowledgeReviewSessionScreen`（feature/review，**M 热区**）→ `KnowledgeReviewSessionViewModelFactory`（feature/review，M）把 plan 作为不可变快照交 VM。
- 结论：G5-A 若在**计划装配期**注入双端口（预测器 + 装配器），可全部落在干净 core:data/core/domain；只有"会话中途自适应换题"才会触碰 feature/review 热区 VM（`KnowledgeReviewSessionViewModel.kt`/`Screen.kt`/`Factory.kt` 当前均 M）。计划 §5 风险"唯一 seam 是热区 VM"在装配期场景下**不成立**。

## 5. QuickJS 现状

- **端口与 fake（批次 20 落地）**：`core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/VisualExecutionEngine.kt`（端口 + `VisualExecutionRequest/Result/Rejection` 语义，KDoc 明示保守分支：只落端口 + fake + 契约用例、**零依赖引入**、生产零消费）；fake `core/domain/src/test/kotlin/com/tingyun/smartmistakebook/core/domain/FakeVisualExecutionEngine.kt`；契约用例 `VisualExecutionEngineContractTest.kt`。三文件 git 均干净。**注意：端口在 core/domain，不在 core/data/visual**（core/data/visual 现存 FigureGeneration* 协调/审计/台账文件，亦全干净）。
- **契约 §5 模块处方**（`docs/current/visual-generation-contract.md:102`，git 干净）原文：「原生执行编排由 app 的 AndroidVisualExecutionEngine 实现 core:domain.VisualExecutionEngine 端口，组合 core:visual-js 客户端、core:visual 内容布局和纯 compiler。core:data 只通过该端口做来源/幂等/持久化/CAS，不直接引用 AIDL/原生排版。core:ui/export 只依赖 core:visual 回放，不依赖 JS worker。」（§5 其余：SceneV1 显示列表 `:92-100`、持久化 `:104`、MathBudget `:106`。）
- **`:22` RELRO 判据原文**（本批按官方豁免改写的目标行）：「发布 AAR 的 PT_LOAD 为 16 KB 对齐，但 GNU_RELRO 结束未对齐；APK zipalign 通过不能解除该问题。源码重建、API 23、arm64、16 KB 真正加载、R8 尚未验证，全部是启用前阻断门。[Android 官方检查要求](https://developer.android.com/guide/practices/page-sizes#check-relro)」——含外链，回写时注意保留或更新链接语境。
- **libs.versions.toml**：`gradle/libs.versions.toml` 无任何 quickjs 条目（grep 空），git 干净——T4 AAR 钉版（`io.github.dokar3:quickjs-kt-android:1.0.15` + sha256 记录）是空位，可直接落。
- 计划 §1 裁定 2 引用的"契约 :22 已干净"属实（git 干净）。

## 6. PRD-09 JsPortStub

- 位置：`tools/figure_eval/figure_eval_gate.py:288` `class JsPortStub`（STATUS="NOT_WIRED"，CLASSIFICATION="JS_STUB_NOT_WIRED"，`evaluate(task)` 回固定留桩记录）；git 干净。
- 切换机制：`run_fake_evaluation(..., js_port: Any | None = None, ...)`（`:466`）内 `js = js_port if js_port is not None else JsPortStub()`（`:478`）——T4 落地后实现同形 `evaluate(task) -> 与 IMAGE_MODEL 同形记录` 的适配器、经 `js_port` 参数注入即可，不必改函数体。`tools/figure_eval/README.md:49-54` 明示该接入点与"JS 在产品侧仍 UNAVAILABLE、不广告"边界；README 也干净。
- PRD-09 文档本体 = `docs/superpowers/plans/2026-10-19-product-readiness-followup-plan.md:126-140`（README 引用，本轮未复读原文——UNVERIFIED 仅限该行段内容，与本报告其他结论无关）。

## 7. SecondaryScreens 与 SmartMistakeBookApplication

- `app/src/main/kotlin/com/tingyun/smartmistakebook/SecondaryScreens.kt`：存在，**git 干净**。
- `app/src/main/kotlin/com/tingyun/smartmistakebook/SmartMistakeBookApplication.kt`：存在，**git 干净**。
- 同目录 app main 下 M 的是：`LearningMasteryScreen.kt`、`ReviewReminderCoordinator.kt`、`ReviewReminderScreen.kt`、`SchedulingSettingsScreen.kt`、`SmartMistakeBookRoot.kt`、`StartupStateBanner.kt`（+ 2 test + 5 androidTest）。计划 §0 把 SecondaryScreens 列入"本批关键干净区（T0 复核）"——T0 前提目前成立。

## 8. 与计划 §0 不符处汇总

1. **热区计数全面漂移（方向均为增脏）**：feature/tutor 19→28、feature/library 15→19、core/ui 9→10、feature/review 8→10、feature/capture 7→10、app 6→13；core/domain 4 与 docs/current 7 仍一致。计划 §0 的计数已过期，开工前须以本报告 §2 实数为准。
2. **计划 HOT_ZONES 漏列的实脏区**：core/database 8 M（70.json 竞争风险载体，含新 M 的 `port/CleanRedrawTaskPort.kt`）、core/export 5 M、core/data 测试面 8 M、docs/research 9 M、docs/superpowers 4 M、docs/history 3 M、docs 根散点 11 M、tools 3 M、根级 4 M、feature/profile 1 M。
3. **KD-64 锚点小幅漂移**：死锁分支现为 `:123-130`（条目写 `:122-129`）；其余锚点（`:68`/`:151`/`:154`）精确命中，机制描述与当前源码逐句吻合。修复时可沿用条目全文，仅行号按 §3 表更新。
4. **端口位置澄清**：VisualExecutionEngine 端口 + fake 在 **core/domain**（计划 §0 干净区列的是 `core/data/visual/**` 作为真实现落点——两者不矛盾：core/data/visual 现存协调/审计文件全干净，§5 处方也是"app 实现 core:domain 端口"）。
5. **G5 seam 判定修正**：计划 §5 风险"生产调用点若唯一 seam 是热区 VM"——实测队列装配 seam 在干净 `StudyReviewPlannerService.currentKnowledgeReviewPlan`（core:data），装配期注入不需要碰 feature/review 热区；仅会话中途换题场景才涉及热区 VM。
6. **G5-A 预测器输入缺口提示**：作答证据台账无"选项数"结构化列（选项数在 `assessment_item_snapshot` 题面、已选选项仅是 `reason_markdown` 文本）——G5-A 双端口若需"选项数/时间"特征，读面是 `readChatEvidenceByLearner` + `KnowledgeQuizItemStore.readSnapshot` 两口拼合，落码前按此现实设计。

**UNVERIFIED**：① 各脏文件的"他线归属"（哪条线在改）无法由 git 证实，只能证实文件脏；② 批次 20 研究产出（R1/R4/R5 内容正确性）本轮未复核，仅确认相关文档存在（`docs/research/2026-10-25-js-engine-selection.md` 等）；③ 本轮按任务约束未跑 Gradle/测试/构建，任何编译或测试绿红状态不在本报告断言范围。
