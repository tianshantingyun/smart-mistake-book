# 批次 R · 读侧语义闭合（裁决 28）完成记录 · 2026-10-01

> 依据：台账「修订批（2026-10-01，计划评审后裁定）」裁决 28（`docs/agent-first-refactor-decisions-2026-09-23.md:2329-2348`）；
> 主计划行：`docs/REFACTOR-MASTER-LINE.md:47`（读侧语义闭合，并行工作流）。
> 本记录为**实施线**产出；记录主体落本文件（独立成文），原因见 §6。

## 1. 验收门与结论

| # | 验收门（裁决 28 原文） | 结论 | 证据 |
|---|---|---|---|
| ① | 构造「曾经掌握、久不作答」的知识点 → **不再出现在 strengths** | ✅ | `ReadSideMasteryClosureTest`（4 例）；`RoomBackedStudyExperienceRepositoryTest.profileStrengthsDropForgottenAndPrerequisiteSuppressedNodes`（端到端） |
| ② | `ReviewPlanner` 的跳过判据与 **E 判据同源** | ✅ | V1 `scoreKnowledgeNode` 跳过改 `effectiveStatus == MASTERED`（`KnowledgeReviewPlannerTest` 7 例，含双钟回归）；V2 风险分支同出口（`ReviewPlannerV2Test` 17 例） |
| ③ | KF-16 压制**有生产调用点**且有效果用例 | ✅ | 三个生产调用点：展示面（`StudySnapshotBuilder` 解析先修图 → `toProfileOverview`）、V1 知识点队列（`StudyReviewPlannerService.currentKnowledgeReviewPlan` → `selectKnowledgeReviewQueue`）、V2 候选路径（`ReviewPlanningRequest.knowledgePrerequisites`）。效果用例：`ReadSideMasteryClosureTest`、`KnowledgeReviewPlannerTest.weakPrerequisiteKeepsADurableNodeInTheQueue`、`RoomBacked…` 端到端、`ReviewPlannerV2Test`（KF-08 四例保持绿） |
| ④ | `MasteryStatus.STALE` 从死枚举收口（45 天窗统一一处） | ✅ | 新 `effectiveStatus` 是唯一"证据过期"判定点；V1/V2/选择器的窗全部改道；枚举补 KDoc 说明"唯一产出方 = 读侧出口" |

## 2. 实现（按文件）

### R-1 读侧统一出口（core:domain）
- `ClearlyMasteredForSkipPolicy.effectiveStatus(state, atEpochMillis, decay, policy, prerequisiteStabilityDays)`：null/证据量<1 → UNKNOWN；存储 CONFLICTED → CONFLICTED（读侧不改判）；E 判据此刻成立（含 KF-16 压制）→ MASTERED；锚点（`lastAttemptAt ?: lastEvidenceAt`）缺失/时钟回拨/超 45 天 → STALE；否则 LEARNING。投影写入侧不动（仍是"自身真相"）。
- `ForgettingCurve.decay` 由 private 改**公开读值**：读取侧用**同一份**个性化 decay 重算召回概率（此前无法取到，会退回默认值分叉）。
- `MasteryStatus` 枚举 KDoc：写明 UNKNOWN/LEARNING/MASTERED/CONFLICTED 由写入侧设置，**STALE 无写入方、由读侧出口产出**。

### R-2 展示面重判 + KF-16 接线（core:data）
- `StudyExperienceMappers`：`toProfileOverview` 增参 `atEpochMillis`（与排程同钟 = `planningContext.planningAtEpochMillis`）、`decay`（`forgettingCurve.decay`，同源个性化）、`prerequisiteStabilityDaysByNode`（**无默认值**——漏传即编译失败，不允许再有静默把已忘点算成强项的路径）。strengths / weaknesses / `newlyMasteredCount` / summary `status` / `conservativeMasteryStatus`（catalog）全部改由 `effectiveStatus` 现算。
- `StudySnapshotBuilder`：接 `KnowledgePrerequisiteReader`，对本次快照引用的 KC 集合解析一次先修图，组 `节点 → 先修记忆稳定度` 表（查不到 = 无已知先修，"未知 ≠ 缺失"）。
- `RoomBackedStudyExperienceRepository`：把既有 `knowledgePrerequisites` 字段接进 builder。

### R-3 决策面同源（core:domain + core:data）
- V1 `ReviewPlanner`：`masteryRiskFor` 三高风险分支改 `effectiveStatus`；`scoreKnowledgeNode` 跳过判据改 `effectiveStatus == MASTERED`（含先修参数）；`plan()` 把 `request.knowledgePrerequisites` 传入候选打分。
- `KnowledgeReviewQueue.selectKnowledgeReviewQueue` + `StudyReviewPlannerService`：知识点队列路径接先修稳定度表（生产调用点，此前 `prerequisiteStabilityDays` 全仓为零）。
- V2 `ReviewPlannerV2.scoreCandidate`：掌握风险分支改 `effectiveStatus`（先修稳定度取自既有 `knowledgePrerequisites`）。
- `AdaptiveQuestionSelector`：`requiresCalibration` 的状态三项 + 旧 `hasFreshEvidence`（45 天窗）合并为 `effectiveStatus`；**登记未做**：选择器先修接线（`AdaptiveSelectionRequest` 不带先修图，见 §5）。

### R-5 版本与指纹
- `REVIEW_PLANNER`（V1）`review-planner-v7 → v8`；V1 指纹 `review-plan-canonical-v5 → v6`。
- `ReviewPlannerV2.VERSION` `review-planner-v3 → v4`；指纹 `review-plan-canonical-v8 → v9`。
- `SELECTOR` `selector-v6 → v7`（组合串随之）。
- **零 bump**：`PROJECTOR` / `EVIDENCE` / `SKIP_POLICY` / `FORGETTING_CURVE` / `STUDY_DATABASE_VERSION(56)` —— 不落库、无投影输出变化。
- 版本台账：`algorithm-version-ledger.md` §2 三行 + §3.6 记录（随代码同笔提交）。

### 附带订正
- `KernelWave0SchemaContractTest` 的 `CURRENT_PROJECTOR_VERSION` 字面量从 Wave 2 时代的 `learning-core-v8(...)` 订正为实际当前值（自洽旧值让"携带当前版本被接受"断言的语义失真）。

## 3. 测试与验证（当前轮，全部本机实跑）

| 门 | 命令 | 结果 |
|---|---|---|
| 全量 JVM | `./gradlew test --continue` | **2104 例 / 5 条失败**——5 条全部为 KB 金标既有红（`GoldenRetrievalJvmTest` / `ProductionLexicalLegExportTest` / `Stage1LexicalLabTest` / `Stage2LexicalScoresExportTest` / `DenseTokenizerParityTest`，HEAD 上即红，非本波引入） |
| 本波新增/更新套件 | 同上 | `MasteryEffectiveStatusTest` 9/0；`ReadSideMasteryClosureTest` 4/0；`KnowledgeReviewPlannerTest` 7/0；`ReviewPlannerV2Test` 17/0；`AdaptiveQuestionSelectorTest` 17/0；`KnowledgeReviewQueueTest` 15/0；`ReviewPlannerTest` 13/0；`MasteryIntervalMappingTest` 2/0；`RoomBackedStudyExperienceRepositoryTest` 26/0；`KernelWave0SchemaContractTest` 绿 |
| 定向仪器化（app） | `./gradlew :app:connectedLocalFirstDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=…LearningMasteryScreenInstrumentedTest,…SchedulingSettingsInstrumentedTest,…SourceCalibrationSectionInstrumentedTest` | **5/0**（LearningMasteryScreen 1 + SchedulingSettings 2 + SourceCalibrationSection 2） |
| R8 冒烟 | `:app:assembleLocalFirstRelease`（debug keystore 签名，口径同 Wave 3）+ 装机/启动/截图/logcat | 见 §4 |
| schema | 本波未动 schema → 迁移矩阵不需要（沿用 Wave 3 已绿的 1→56 3/3） | ➖ |

## 4. R8 冒烟（2026-10-01）

- `:app:assembleLocalFirstRelease` **BUILD SUCCESSFUL 3m 2s**（`minifyLocalFirstReleaseWithR8` 实跑，R8EXIT=0）；
  口径同 Wave 3：本机无 `RELEASE_*` 环境变量，以标准 debug keystore 给 release 变体签名——被测对象是
  **R8 混淆与启动**，不是签名链。
- 装机 `Success` → 启动后进程存活（`pidof` 命中，PID 30401）→ 主屏渲染截图
  （`build/r8-smoke-r-batch-2026-10-01.png`：复习栏、问候、底栏四入口）→ 该进程 logcat 中
  FATAL / AndroidRuntime / R8 反射类缺失（MissingClass/ClassNotFound/NoSuchMethod/NoSuchField）**零命中**。

## 5. UNVERIFIED 与登记项（如实）

1. **选择器先修接线未做**：`AdaptiveQuestionSelector` 的跳过（`SKIP_MASTERED_FOUNDATION`）仍按无先修图调用 `isSatisfied` —— `AdaptiveSelectionRequest` 不带先修图，本波按"最小改"不加字段；KF-16 压制在该路径不生效，**登记为后续项**（与 3B/3C 的接线一并评估）。
2. **`scoreKnowledgeNode` 的 dueRisk 分支仍以 `lastEvidenceAt` 估"过期程度"**：这是**排序启发式**（"多久没动过"的缓升权重），不是状态判定；状态与跳过已全部同源。保持原语义，不做无谓的数值变更。
3. **`KnowledgePrerequisiteReader.graphFor` 的新增调用成本**：展示面每次 build 对引用 KC 集解析一次先修图（分块 256、按科目分组，纯读）。未做基准测量；数据规模下有界（沿用排程侧同一解析器）。
4. 真实模型路径不冒烟（沿用既有口径）；`PerformanceGateTest` 环境档不声称绿（本波未复跑该套件，其"本机共享 AVD 环境档"判定沿用 Wave 3 记录）。

## 6. 记录落点说明（共享工作树）

台账与 master line 当前含**另一线未提交的「修订批」改动**（裁决 26–31 及规格/阶段文件）。为避免整文件提交裹挟他线在飞内容，本记录独立成文于 `docs/research/`：

- 台账「完成记录」段落与 `REFACTOR-MASTER-LINE.md:47` 的状态翻 ✅ + 指针回填，**待他线提交后进行**（一笔最小 diff）。
- 版本台账（`algorithm-version-ledger.md`）无他线改动，已随 C2 同笔提交。
