# 批次 49 · P1-6 模拟学生（SimulatedLearner）证据（2026-11-08）

> 执行角色：core:domain 测试工程师。本 ask 只新建**测试源码**（`core/domain/src/test/.../simulation/`），
> **未改**任何生产文件、未动掌握阈值（21 天 / 0.9）、未动 G6 / 权重 / `ReviewPlannerV2.VERSION` /
> 金样 / 台账。所有命令均为本会话实跑；指标数字只对"模拟世界"成立（**model-based**），
> 不构成真库结论。
>
> 方法论口径来自 `docs/research/2026-11-08-calibration-method.md` §3–§4（R1），
> 未自行另立协议。

## 0. 落点与归属

| 文件 | 性质 | 说明 |
|---|---|---|
| `core/domain/src/test/kotlin/.../domain/simulation/SimulatedLearner.kt` | **新增** | 模拟器（世界构造 + 日循环 + 指标 + 规范化摘要） |
| `core/domain/src/test/kotlin/.../domain/simulation/SimulatedLearnerTest.kt` | **新增** | 6 条用例 |

- 开工前 `git status --porcelain core/domain/` 已有他线脏文件（`SchedulingEvaluation.kt`、
  `TutorJudgedReview.kt`、`MistakeOrganizationRepositoryTest.kt`、`PlannedReasonCalibrationTest.kt`、
  `gradle.lockfile`）——本 ask **一个都没碰**；结束后同一命令只多出
  `?? core/domain/src/test/kotlin/.../simulation/`。
- `RoomStudyDatabase.kt` 无改动（本 ask 不涉及 DAO）。

## 1. 红 → 绿

| 轮 | 命令 | 结果 |
|---|---|---|
| 红 | `./gradlew :core:domain:test --tests "com.tingyun.smartmistakebook.core.domain.simulation.*" --console=plain`（先只落测试文件） | **BUILD FAILED**，`:core:domain:compileTestKotlin`：`Unresolved reference 'metrics'` / `Cannot infer type for value parameter 'coverage'`（`SimulatedLearner`/`SimulationConfig` 不存在） |
| 绿 | 同一命令（落 `SimulatedLearner.kt` 后） | **BUILD SUCCESSFUL**，`tests=4 skipped=0 failures=0 errors=0`（后补至 6） |

红轮是"类不存在"的编译失败，不是行为断言失败——本 ask 的交付物本身是模拟器，先落用例再落实现在本仓
是唯一可行的 TDD 顺序；行为面的失败由下面第 2 节的断言各自钉住。

## 2. 六条用例（各自消灭的失败）

命令（最终轮）：

```text
./gradlew :core:domain:test --tests "com.tingyun.smartmistakebook.core.domain.simulation.*" --console=plain
```

XML：`core/domain/build/test-results/test/TEST-…simulation.SimulatedLearnerTest.xml`
→ `tests="6" skipped="0" failures="0" errors="0" time="4.086"`

| # | 用例 | 断言要点 | 消灭的失败 |
|---|---|---|---|
| 1 | `same seed reproduces every metric bit for bit` | 同 config 两次构造：`metrics` 相等、`digest()` 相等、`reviewLog` 逐行相等；换 seed 摘要必须不同 | 模拟含未受控随机源（共享 RNG 流 / 遍历顺序），同 seed 两次跑出不同指标；或摘要是常量、复现性无意义 |
| 2 | `every simulated day stays inside its review budget` | 每日 `plannedSeconds <= budgetSeconds`、`answeredSeconds == plannedSeconds`、`f=0` 时 `budgetSeconds == 1200`、`overrunDays == 0`、总量不超总预算 | 排程器选超预算；或模拟层把没作答的题算成工作量（工作量指标虚高） |
| 3 | `holdout seeds are disjoint from the dev seeds and name distinct worlds` | dev/holdout 各 10 个、交集为空、20 个 `worldFingerprint()` 互不相同 | dev 与 holdout 复用同一批世界（或换标签不换世界），一次性裁决被反复偷看 |
| 4 | `a full run populates every required metric` | 世界形状 30 KC / 300 题；`scoredPredictionCount>0`；Brier/ECE 非 null 且在 0..1；5 条各科覆盖 + 5 条考试覆盖；per-reason 样本总数 == 复习行数；掌握数在 0..30；review_log 行数 == 作答数 | "模拟什么都没产出、但别的用例因为全是空集而全绿"；以及报告用 0.0 冒充"无数据" |
| 5 | `the careless rate actually moves the simulated outcomes` | `slip=0` 的实际召回 > `slip=0.2` 的召回，且两臂摘要不同 | `carelessRate` 参数收下了却没用（死旋钮） |
| 6 | `a perturbed truth parameter set changes the simulated world` | 同 seed 下把真值 `w20 × 1.3`，摘要必须不同 | `truthParameters` 收下了却没用（P1-8 的 T9 扰动臂依赖它；真值恒等于先验中心会让"参数恢复"测不出识别力） |

### 2.1 指标清单（ask 逐项对照）

| ask 要求 | 落点 |
|---|---|
| Brier / ECE | `SimulationMetrics.brierScore` / `expectedCalibrationError` / `logLoss` / `maximumCalibrationDeviation`，由 `CalibrationReportBuilder.build` 计算（`CalibrationReportBuilder.kt:17-102`）；空集走 `null`（T11：0.0 是这两个指标的最优值，不能用它冒充无数据） |
| 日工作量 / 预算 | `dailyWorkload: List<DailyWorkload>`（day / budgetSeconds / plannedSeconds / answeredSeconds / plannedItemCount / utilization）+ `totalBudgetSeconds` / `totalAnsweredSeconds` / `overrunDays` |
| 考试科覆盖 | `examSubjectCoverage`（5 场考试各一条；窗口 = 考前 14 天含考试当天，与 `StudyReviewPlannerService.EXAM_RAMP_DAYS` 同窗） |
| 各科覆盖 | `perSubjectCoverage`（5 科 × 6 KC 的 horizon 内覆盖） |
| 掌握数 | `masteredKnowledgeNodeCount`：末态上用 `ClearlyMasteredForSkipPolicy.meetsMemoryCriterion`（21 天 ∧ 0.9）重算，**不改阈值、不读冻结 status** |
| per-reason recall | `perReasonRecall: List<PlannedReasonCalibration>`，直接复用 `SchedulingEvaluationHarness.calibratePlannedReasons`（与生产同一函数、同一 `fittableReviewSamples` 口径） |

### 2.2 review_log（内存 list）

- `SimulationRun.reviewLog: List<ReviewSample>`，字段口径对齐 `ReviewLogSink.record`：
  `rating` 是调度实际使用的档（`FsrsEvidenceRatingMapper.schedulingRatingFor`）、`sourceKind=ATTEMPT`、
  `plannedReason` = 原因名排序逗号连接（`StudyReviewPlannerService.kt:683` 同格式）、
  `state` 按"无前条=0 / 前条 AGAIN=3 / 同日=1 / 跨日=2"派生、`deltaTDays` 是学习日差（首条 0.0）。
- 可直接喂 `FsrsParameterOptimizer`：种子 101 实测
  `predictableSampleCount(reviewLog) = 1935` > 400 门（`MIN_SAMPLES_FOR_FITTING`）。

## 3. 全模块回归

```text
./gradlew :core:domain:test --console=plain
→ BUILD SUCCESSFUL
→ suites=92 tests=727 skipped=0 failures=0 errors=0
```

（计数口径说明：本 ask **未在落文件前**跑过该模块基线。三次测量：用例 4 条时 725、
5 条时 726、最终 6 条时 **727**；每次都 0 红。）

## 4. 跨进程逐位复现（独立两次 Gradle 调用）

临时探针（已删除）打印 `SimulatedLearner(SimulationConfig(seed = 101L)).run().metrics.digest()`：

```text
./gradlew :core:domain:test --tests "...DigestProbe" --console=plain --rerun-tasks   # 第 1 次
DIGEST101=3d64a84e9eafe9194472232638022c12f01ee25eb654656f2bc0be9caccbefea
./gradlew :core:domain:test --tests "...DigestProbe" --console=plain --rerun-tasks   # 第 2 次
DIGEST101=3d64a84e9eafe9194472232638022c12f01ee25eb654656f2bc0be9caccbefea
```

摘要把浮点以 `java.lang.Double.toHexString` 进串（R1 §3.6），因此"摘要相同"同时排除末位漂移、
`-0.0/0.0` 与 `NaN` 的混淆。**证据边界**：两次都在同一台开发机、同一 JDK 下跑；未在异机/异 JDK
复跑，登记为未验证项。

## 5. 夹具迭代中发现并修掉的两个世界伪影（如实记录）

第一版跑出来 10/24 个 KC 永久 `CONFLICTED`、科目覆盖 4–6/6、只练到 139/300 题。定位到两处
**夹具缺陷**（不是产品行为），已修：

| # | 缺陷 | 后果 | 修法 |
|---|---|---|---|
| 1 | 每个 KC 只给 1 个题族 | `CONFLICTED` 的恢复判据要求题目族广度 ≥2（`ClearlyMasteredForSkipPolicy.REQUIRED_FAMILIES`），结构性不可满足 → 一次"已掌握后遗忘"就永久留在冲突态，队列被 `CALIBRATION_CHECK` 假信号灌满 | 每 KC 2 个题族（同族 5 题）；顺带让"同族不得相邻"这条硬约束有约束面 |
| 2 | 题目列表恒按科目聚簇 | planner 的平局排序沿用输入顺序 → 后几个科目系统性挨饿（纯夹具伪影） | world seed 决定的 `java.util.Collections.shuffle` |

改前 / 改后（同一 seed 101，临时 debug 用例打印，用例已删除）：

| 读数 | 改前 | 改后 |
|---|---|---|
| 覆盖到的 KC | 24 / 30 | **29 / 30** |
| 各科覆盖 | 5,5,6,4,4 | **6,6,5,6,6** |
| 考试科覆盖（窗口内） | 5,4,5,3,3 | **5,5,4,3,6** |
| KC 终态 status | CONFLICTED=10 / LEARNING=8 / MASTERED=6 | **MASTERED=10 / LEARNING=19 / CONFLICTED=0** |
| 掌握数（E 判据读） | 6 / 30 | **9 / 30** |
| Brier / ECE | 0.1023 / 0.0456 | **0.0994 / 0.0320** |
| 复习到的题数 | 139 / 300 | **165 / 300** |
| 可预测长程样本 | 2114 | **1935** |

改后仍有非空读数的"真实产品信号"（不是夹具缺陷，供 P1-8 解读）：第 6 天只排到 7 题（439s）——
冷启动期根 KC 的题被练完、后继 KC 又被 KF-08 先修硬过滤挡在池外，是真实的前置门动态；
考试科覆盖在 subject-3 只有 3/6，说明 `EXAM_WEIGHT=2.0` 在本世界里弱于 `WEAKNESS_WEIGHT=3.0`——
正是权重扫描要测的那类差异。

## 6. 复用与偏离（如实登记）

**复用**：
- `CalibrationReportBuilder.build`（Brier/ECE/logLoss/maxDeviation）；
- `SchedulingEvaluationHarness.calibratePlannedReasons`（per-reason recall）；
- `ReviewSample` / `fittableReviewSamples` / `predictableSampleCount` 的样本口径；
- `ReviewPlannerScaleBenchmarkTest` 的夹具形状（空 `LogDurationModel` 语义被"每科固定时长画像"替代，
  目的：让"日工作量/预算"不是 1200s 的恒等式）；
- 评级词表只用 `AGAIN/HARD/GOOD`（EASY 在生产 `schedulingRatingFor` 里结构性不可达）。

**偏离 ask 字面的一处（请 main 裁决是否需要改回）**：
ask 写"→`FsrsMemoryUpdateModel`→"；实现走的是**生产投影器** `LearningProjector`
（构造时显式注入 `FsrsMemoryUpdateModel(truthParameters)`），因此每次记忆更新仍然只经
`FsrsMemoryUpdateModel`，但知识点掌握态（β-二项 s/f、观察列表、校准状态、CONFLICTED 判定）
由 `LearningProjector.projectMastery` 一处定义。理由：在夹具里再抄一份掌握度公式会重演本仓
反复关掉的"同一规则两处权威"（`AlgorithmConstants` / `MasterySmoothing` 的 KDoc 都记着同型缺陷）。
代价：模拟器依赖 `core:domain` 的内部测试入口 `LearningProjector.project(previous, events, head)`
（`LearningProjector.kt:74-83`，`internal`，同模块测试可见）。

## 7. 未建模 / 不可测 / 未验证（不得读成"已测"）

| 项 | 状态 | 原因 / 下一步 |
|---|---|---|
| 考试日召回探针（每 KC 20 次抽样） | **未建模** | R1 §3.0 把它列为 G6 的结局变量，属 P1-8 的臂；本类只给"考试科覆盖"这类可从复习流直接读出的量 |
| `AVOIDANCE_WEIGHT` | **本世界不可测** | 模拟里没有"切走/低分"行为通道，`ReviewCandidate.avoidance` 恒 false；不造假信号 |
| 同日重复作答 | **未建模** | 排程计划不重复练习题；且同日重复在拟合口径里只训不评 |
| G6 臂 | **可跑但未跑扫描** | `examCapacityExtraFraction` 已按 `examCapacityExtraFraction` 形状接线（默认 0.0），扫描与预注册属 P1-7/P1-8 |
| 世界真值 ≠ 产品参数 | **默认相同** | `truthParameters` 默认 = `DEFAULT_PARAMETERS`；R1 §5.1 的扰动臂（T9）应由预注册冻结后传入，本 ask 不预设 |
| 异机/异 JDK 复现 | **未验证** | 只在开发机同 JDK 下跑了两次独立 Gradle 调用 |
| 真库任何指标 | **BLOCKED** | 本 ask 不接触真库；真库 per-reason/G6 的因果效应在 R1 §9 已登记为结构性 BLOCKED |

## 8. 复跑命令

```text
# 6 条用例
./gradlew :core:domain:test --tests "com.tingyun.smartmistakebook.core.domain.simulation.*" --console=plain

# 模块回归
./gradlew :core:domain:test --console=plain
```

XML 计数口径：`core/domain/build/test-results/test/TEST-*.xml` 聚合。
