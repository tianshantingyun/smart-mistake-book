# 3E 全库薄弱点扫描 / 每日队列 / 预算封顶：真实数据与设备验证方案（批次 50 / R4）

> **性质**：研究 + 验证方案（**唯一写盘产物是本文件**）。本会话**未改任何源码、未改门阈值、未跑 Gradle、
> 未跑设备仪器化**；`docs/research/` 下他线在飞的 M 文件一律未触碰。文中"静态推断"与"本轮实跑"严格分开
> （§0.4 列实跑命令；§7 列未验证项）。
>
> **时点**：HEAD `a87fc70d`（本轮 `git rev-parse --short HEAD` 实测）。**工作树是脏的**，且
> `core/domain` / `core/data` 在 3E 路径上有他线未提交改动（见 §0 C2、§7 RK1）；本文件的代码行号
> **一律指工作树**（与读者实际构建的字节一致），只在需要区分 HEAD 时特别标注。
>
> **输入**（全部本轮实读）：计划 `.zcode/plans/plan-2026-11-09-batch-50-release.md:14,44-45,89`；
> 3E spec `docs/research/2026-10-11-new-stages-spec.md:15-29`（尤其 `:28`「只验真实队列/预算，按当前实现
> 区分单计划上限与每日上限」、`:29` 验收门）；需求定稿 `docs/requirements-fused-final-2026-10-11.md:114,115,118,228`；
> 批次 50 基线 `docs/research/2026-11-09-batch-50-baseline.md:231`；预算裁定
> `docs/research/2026-11-02-ws3-review-budget-adjudication.md:14,43-45,57`；
> 代码 `StudyReviewPlannerService.kt` / `KnowledgeReviewQueue.kt` / `KnowledgeReviewFullLibraryScan.kt` /
> `ProblemOrganizationDao.kt` / `RoomKnowledgeBaseStore.kt` / `DataStoreSchedulingSettingsStore.kt` /
> `StudyExperienceRepository.kt` / `ReviewCapacityPolicy.kt` / `LogDurationModel.kt` /
> `RoomBackedStudyExperienceRepository.kt` / `StudySnapshotBuilder.kt` / `LearningProjector.kt` /
> `SmartMistakeBookRoot.kt` / `SmartMistakeBookApplication.kt`；测试
> `StudyReviewPlanner3EScanInstrumentedTest.kt`（唯一 3E 设备用例）、`StudyReviewPlannerExamWindowTest.kt`、
> `KnowledgeReviewQueueTest.kt`、`RoomBackedStudyExperienceRepositoryTest.kt`、
> `StudySubmissionWithoutFixtureInstrumentedTest.kt`；缺陷 KD-46 `docs/known-defects.md:2055`、
> KD-64 `:2563`；台账 `docs/acceptance/ledger.md:154`（A-023）、`:515`（E-024）。
>
> **口径**：`已落` = 工作树有代码/夹具且本轮读过；`静态推断` = 由代码逐行推出、**未执行**；
> `实跑` = 本轮真跑过并留下输出（§0.4）。

---

## 0. 结论（先给可执行结论）

### 0.1 门到底要验三件事

3E 验收门原文（`docs/research/2026-10-11-new-stages-spec.md:29`）：

1. **弱掌握点离开今日错题并集仍可被捞回**（第二档全库扫描的召回）；
2. **F8 封顶生效**（时间预算绑住队列）；
3. **F17 封顶生效**（名额绑住第二档增量）。

外加 spec `:28` 的口径要求：**区分"单计划上限"与"每日上限"**，不得用一者冒充另一者。

### 0.2 十条结论

**C1（最高优先，会直接决定设备轮红绿）——仓内唯一的 3E 设备用例在 HEAD 上已因批次 49 的"预算同源"改造失效，静态推断 3 个用例都会红（每个至少 1 处断言），必须"先红后修"。**
`StudyReviewPlanner3EScanInstrumentedTest` 构造服务时**没有**传 `budgetResolution`（`git show HEAD:… | grep -c budgetResolution` = **0**），
于是走默认 `SETTINGS_THEN_CONSTRUCTOR`（`StudyReviewPlannerService.kt:80`）；而用例传入的是**真 DataStore**
（`:377-380`），`options` 恒有值且缺省 `dailyReviewMinutes = 20`（`DataStoreSchedulingSettingsStore.kt:45-53`）
→ 实际预算 **1200 s**，不是用例断言的 120 s。受影响断言：`:156-159`（预算 120）、`:150-154`（今日队列恰 2 题）、
`:251-255`（知识点队列恰 2）、`:279-280`（预算 120）。**这不是放宽断言能修的**：批 49 的 JVM 孪生用例
已按同一问题改成 `BudgetResolution.CONSTRUCTOR_ONLY`（`StudyReviewPlannerExamWindowTest.kt:344`），设备用例没跟上。

**C2（证据绑定的前提）——HEAD `a87fc70d` 自身不自洽，设备证据不能绑 HEAD，只能绑工作树快照。**
HEAD 的 `StudyReviewPlannerService.kt:949` 传 `reasons = scored.reasons`，而 HEAD 的
`KnowledgeReviewQueueEntry` 只有 5 个参数、没有 `reasons`（本轮 `git show HEAD:…KnowledgeReviewQueue.kt` 实读）；
工作树才补上该字段（`KnowledgeReviewQueue.kt:68-103`，`git diff` 计 +21 行）。同类未提交依赖还包括
`ReviewScoringWeights`（工作树 `ReviewPlannerV2.kt` 新增，HEAD 无此符号；其 seam 测试
`ReviewScoringWeightsSeamTest.kt` 仍是 `??` 未跟踪）。这与发布门 G-REL-00 已记录的"干净 HEAD 不可编译"
同类（`docs/research/2026-11-05-release-gates.md:10`），但在 3E 路径上**具体化**了：`core:domain` 在 HEAD 上
过不了编译。→ 3E 设备证据必须带**脏树清单**（`git status --porcelain` 的 sha256）+ APK sha，否则不可复现。

**C3（对门含义影响最大）——默认 20 min 预算下，知识点队列在数学上最多 6 个，"每日 10 个"在默认设置下不可达。**
知识点队列每项估时固定 180 s（`LogDurationModel.kt:109`；两档同值，`StudyReviewPlannerService.kt:913`、
`KnowledgeReviewFullLibraryScan.kt:70`），F17 名额是 10（`KnowledgeReviewFullLibraryScan.kt:30`）。
`floor(1200/180) = 6`。预算要到 **1800 s（30 min）** 才可能装下 10 个——预算裁定文档自己也写了这个锚点
（`2026-11-02-ws3-review-budget-adjudication.md:57`）。→ 验 F17 必须**显式把预算抬到 ≥30 min**，
否则观察到的是 F8 在封、不是 F17 在封；而产品对默认用户的真实承诺是"每次最多 6 个知识点"，不是 10。

**C4（口径）——不存在"严格每日总量"；cap 是"每次排计划（每次进屏）≤10"，同日多次可累计超过 10。**
`KnowledgeReviewSessionPlan` 全仓无持久化落点、cap 只在纯函数里 `.take(...)`（`KnowledgeReviewQueue.kt:200`；
`KnowledgeReviewFullLibraryScan.kt:6-30` 的 KD-46 口径）；生产有**两处**独立调用，各自带新 UUID：
首页计数（`SmartMistakeBookRoot.kt:649-652`）与会话进屏（`:963-966`）。KD-46 已按"接受近似"结案
（`docs/known-defects.md:2055`）。→ 证据里**不得**写"每日 ≤10"；要写"每次排计划 ≤10；同日累计未封顶（KD-46 已裁）"。

**C5（F8 的两套口径，需主会话登记）——调度实际吃 5..120 min，容量裁定写 10..60 min/5 步进，后者无生产调用点。**
`SchedulingOptions` 的构造校验是 `dailyReviewMinutes in 5..120`（`StudyExperienceRepository.kt:834`），
设置页也用 `in 5..120`（`app/.../SchedulingSettingsScreen.kt:107-108`），调度读的就是它
（`StudyReviewPlannerService.kt:117-121`）；`ReviewCapacityPolicy` 的 10..60/5 步进
（`ReviewCapacityPolicy.kt:33-42`、`:89-93`）**无生产调用点**（其分钟档校验 `secondsForBudgetMinutes`
本轮 grep 只命中定义与 `ReviewCapacityPolicyTest`；`ReviewCapacityScheduler`/`ExamQuotaPlanner` 亦零生产实例化）。
→ 设备验证取两套口径的**交集 10..60 min**，并把分叉登记为待裁项（§7 Q2）；**不得**顺手改任何一边。

**C6（F8 的生效时点）——知识点队列的预算取自"已落库计划"的 `timeBudgetSeconds`，不是当前设置。**
`StudyReviewPlannerService.kt:918` 传的是 `currentPlan.plan.timeBudgetSeconds`。→ 改设置后若今日计划已被保留，
知识点队列仍按旧预算排；设备判据要区分"新建计划"与"复用计划"两种情形（§3.3 F8-4）。

**C7（口径问题，需裁定）——错题队列与知识点队列各自吃满 F8，单日总时长上限约为 2× 预算。**
错题计划预算 = `examCapacityBudgetSeconds(...)`（`:479-483`）；知识点队列预算 = 同一值（`:918`）。
两队列不共享池。门只要求"各自封顶"，本文件按此判；但产品若承诺"每日复习 ≤ N 分钟"，当前实现不成立
（§7 Q3）。

**C8（覆盖现状，纠正基线的一处检索误判）——设备用例**存在**，缺的是"跑 + 证据"，不是"用例"。**
基线 `:231` 说"androidTest 文件名检索未命中 review 队列容量用例"；实际
`core/data/src/androidTest/.../StudyReviewPlanner3EScanInstrumentedTest.kt` 在册（`git ls-files` 可见），
只是文件名不含 queue/capacity。该用例已覆盖真 Room SQL 范围、真材料写口、真 DataStore 考试表、
名额/预算两态；**缺口**是 C1（预算口径已过期）、设置真实生效路径、单计划 vs 每日口径、以及"真实写入路径"。

**C9（负向面已钉住，不必重建）——无材料节点、无在册绑定题的节点、退役节点都不进扫描档。**
SQL 三条判定写死在 `ProblemOrganizationDao.kt:327-397`（ACTIVE 错题本 ∪ 非 RETIRED 节点 ∪ 材料科目=节点科目）；
领域侧两道范围条件在 `KnowledgeReviewFullLibraryScan.kt:51-73`；设备用例已断言两类负向
（`StudyReviewPlanner3EScanInstrumentedTest.kt:129-132,196-197`）。本轮无需新增，只需在证据里保留。

**C10（可复现性）——"真实数据"的现实上限是"真库 + 真材料 + 真投影 + 真设置"，不是"真拍照/真模型"。**
仓内没有离线可复现的整卷导入→绑定链路（绑定要么来自模型整理，要么手工）；可行的最强档是
：DB 级写入题/绑定（行形状与生产一致）+ **真 `submitChoice` 事件流** + 真投影 + 真 DataStore。
详见 §4（A/B/C/D 四档）。**不提议**造 `.smbk` 备份包作为首选（成本高、收益低，见 §4.1 D 档）。

### 0.3 一句话通过条件

> 在**同一棵工作树 + 同一 APK**上，用真 Room 库跑出：① 一个今日错题并集之外的弱掌握点被第二档捞回；
> ② 预算 1800 s 时第二档恰 10 个、1800 s 以下按 `floor(B/180)` 截断且首档不被挤掉；
> ③ 设置 10 min 时计划预算 = 600 s 且知识点队列 ≤3；④ 同日第二次排计划可再取 10 个（口径证据）。
> 四条全绿 = 3E 门可写 PASS；任一红 = FAIL 并附最小复现；未跑 = UNVERIFIED（不得写 PASS）。

### 0.4 本轮实跑（可重跑命令与输出）

| # | 命令 | 输出（摘要） |
|---|---|---|
| 1 | `git rev-parse --short HEAD` | `a87fc70d` |
| 2 | `git status --porcelain -- core/data/src/main/kotlin/.../study/ core/domain/src/main/kotlin/.../domain/ core/data/src/androidTest/kotlin/.../study/` | 9 行 `M`（含 `KnowledgeReviewQueue.kt`、`ReviewPlanner.kt`、`ReviewPlannerV2.kt`、`RoomBackedStudyExperienceRepository.kt`）；**`StudyReviewPlannerService.kt` 不在其中**（= 与 HEAD 逐字节相同） |
| 3 | `git show HEAD:core/…/StudyReviewPlanner3EScanInstrumentedTest.kt \| grep -c budgetResolution` | `0`（grep 退出码 1） |
| 4 | `git show HEAD:core/…/KnowledgeReviewQueue.kt \| sed -n 60,85p` | `KnowledgeReviewQueueEntry` 仅 5 参（`knowledgeNodeId/subject/displayName/masteryScore/lastEvidenceAtEpochMillis`），无 `reasons` |
| 5 | `git show HEAD:core/…/StudyReviewPlannerService.kt \| grep -n "reasons = scored.reasons"` | `949` |
| 6 | `C:/Android/Sdk/platform-tools/adb.exe devices -l` | `emulator-5590  device … sdk_gphone64_x86_64` |
| 7 | `adb -s emulator-5590 shell getprop ro.build.version.sdk` / `ro.product.cpu.abi` / `ro.boot.qemu.avd_name` / `ro.build.fingerprint` | `34` / `x86_64` / `ui_motion_20261107` / `google/sdk_gphone64_x86_64/emu64xa:14/UE1A.230829.050/12077443:userdebug/dev-keys` |
| 8 | `adb -s emulator-5590 shell pm list packages \| grep -i tingyun`；`dumpsys package … \| grep versionCode` | `…localfirst`、`…localfirst.test`；`versionCode=2 versionName=0.2.0-localfirst` |

**未跑**（如实）：任何 Gradle 任务（含 `:core:data:connectedDebugAndroidTest`）、任何 JVM 测试、任何设备时钟改动。

---

## 1. 需求与门定义（原文口径）

| 层 | 原文 | 出处 |
|---|---|---|
| 需求 F7 | "全库薄弱点主动扫描与捞回，**不受今日队列限制**；每日增量由 F8 预算 + F17 配额封顶" | `docs/requirements-fused-final-2026-10-11.md:114` |
| 需求 F8 | 每日复习量/时长预算自定义 | `:115` |
| 需求 F17 | 维护配额（留名额给将到期的强题）；**同时承担 F7 扫描量的封顶** | `:118` |
| 裁定 | "全库扫描；每日捞回量由 F8 预算 + F17 配额封顶" | `:228` |
| Spec 状态 | "只验真实队列/预算，按当前实现区分**单计划上限与每日上限**，不重建或重定既有数值" | `docs/research/2026-10-11-new-stages-spec.md:28` |
| Spec 门 | "弱掌握点离开今日错题并集仍可被捞回；F8/F17 封顶生效" | `:29` |
| 本批计划 | G2：17 "真实队列/预算设备验证（全库扫描/封顶/单计划口径）"、18 "3E 门证据" | `.zcode/plans/plan-2026-11-09-batch-50-release.md:44-45` |
| 纪律 | "掌握阈值、冲突 KC 默认语义不改；G6/权重无新证据不改" | `:86` |

**解读纪律**（本文件据此判）：

1. 门只要求"**每次排计划**"的两个封顶 + 召回；"每日总量"是 KD-46 已裁的近似，不是门条件。
2. F17 在本仓 3E 里的实现形态 = `FULL_LIBRARY_SCAN_DAILY_CAP = 10`（**名额**），
   `ReviewCapacityPolicy`/`ExamQuotaPlanner` 的"维护配额 C5"数学**不是** 3E 的封顶实现
   （`ExamQuotaPlanner` 只被 `ReviewCapacityScheduler` 引用，而后者**零生产实例化**——本轮 grep 无命中）。
3. F8 在本仓的可执行定义 = `plan.timeBudgetSeconds`；知识点队列另有一层"每项 180 s"的取队预算。

---

## 2. 代码事实闭包（3E 数据流）

```
error_book_entry(ACTIVE) ─┐
practice_unit_knowledge_binding ─┼─► readFullLibraryScanNodes()  [ProblemOrganizationDao.kt:327-397]
knowledge_node(status!='RETIRED') │        │  一条 JOIN 查询：节点 × 去重绑定题(CHAR(31) 连接) × MIN(material_id)
knowledge_teaching_material ──────┘        ▼
                                     fullLibraryScanCandidate()  [KnowledgeReviewFullLibraryScan.kt:51-73]
                                     ├─ 无绑定题 → null（无时间信号）
                                     └─ 无材料   → null（排了也出不了题）
                                            ▼
当前错题计划队列 ──► extractReviewKnowledgeScope ──► readActiveKnowledgeNodeIds ──► quizAbleScopeNodes
     （第一档）                                          （材料可出题过滤，单科 64 条上限）
                                            ▼
                              knowledgeRecallRiskByNode()  [KnowledgeReviewQueue.kt:118-141]
                              = min over 绑定题 FSRS R(elapsed, stability, decay)
                                            ▼
                          selectKnowledgeReviewQueue()  [KnowledgeReviewQueue.kt:171-242]
                          ① 第二档按分排序 → .take(cap=10)          ← F17
                          ② 与第一档并集（同 id 以第一档为准）
                          ③ 180 s/项 贪心取队，Σ ≤ timeBudgetSeconds ← F8
                                            ▼
                                KnowledgeReviewSessionPlan（不落库）
```

### 2.1 第一档（今日错题并集）

- 范围：当前错题计划的队列项 → 其绑定知识点（`StudyReviewPlannerService.kt:824-831`），再经
  `readActiveKnowledgeNodeIds` 去退役（`:835`）、`quizAbleScopeNodes` 要求**有讲解材料**（`:851-852`，
  材料读取单科上限 `MAX_KNOWLEDGE_QUIZ_SCOPE_MATERIALS = 64`，`:757`、`:962`）。
- 到期风险：`knowledgeRecallRiskByNode`（`KnowledgeReviewQueue.kt:118-141`）取**绑定题最小 R**；
  没有已知记忆的节点不进结果，由打分核回退掌握度分支。

### 2.2 第二档（全库扫描，3E 本体）

- 读口：`readFullLibraryScanNodes()` 一条查询（`ProblemOrganizationDao.kt:327-397`），返回
  `FullLibraryScanNodeRecord(knowledgeNodeId, subject, boundPracticeUnitIds, materialGroupId)`；
  CHAR(31) 分隔符在 `RoomKnowledgeBaseStore.kt:460-471` 解析。
- **减法用 `todayCandidateIds` 而不是 `requestedScope`**（`StudyReviewPlannerService.kt:866-872`）：
  今日错题碰到、但因材料查询 64 条截断没进今日候选池的点，必须留给第二档捞——这条是批次 20 对抗复核
  finding 4 的修复，别改回。
- 领域范围：`fullLibraryScanCandidate` 两道 null（无绑定题 / 无材料），并复用同一 `state`/`recallRisk`/
  180 s 估时（`KnowledgeReviewFullLibraryScan.kt:51-73`）。
- 空今日队列**不是**短路条件（`:818-823` 注释；JVM 用例
  `RoomBackedStudyExperienceRepositoryTest.kt:603-665` 钉住）。

### 2.3 F17：名额封顶的精确语义

- `scannedPicks`：只对**第二档候选**打分 → `compareByDescending(score).thenBy(nodeId)` →
  `.take(fullLibraryScanDailyCap)`（`KnowledgeReviewQueue.kt:191-201`）。**平局按节点 id 升序**，纯函数确定。
- 并集：`(candidates + scannedPicks).distinctBy(knowledgeNodeId)`（`:204-208`）——同 id 以**第一档**为准，
  即名额只封"增量"，不封今日档（"扫描不该把今日队列挤掉"）。
- `fullLibraryScanDailyCap = 0` = 关掉第二档（单测 `KnowledgeReviewQueueTest.kt:459-473`）。

### 2.4 F8：时间预算的精确语义

- 生产解析（单点）：`dailyReviewBudgetSeconds()`（`StudyReviewPlannerService.kt:117-121`）
  = 设置存储优先，构造参数仅在 store 为 null 时兜底；`CONSTRUCTOR_ONLY` 只给夹具（KDoc `:54-60` 明说
  用途是"按显式预算验证 G6/配额数学"，`SchedulingOptions` 5 min 粒度装不下 60/120 s 边界）。
- 考试扩容：`examCapacityBudgetSeconds()`（`:169-189`）= `base + int(base × 0.5 × ramp)`，
  `ramp = 1 − daysUntil/14`（窗口 0..14 天，`EXAM_RAMP_DAYS = 14`，`:963`；
  `EXAM_CAPACITY_RAMP_MAX_EXTRA_FRACTION = 0.5`，`AlgorithmConstants.kt:148`）。考试当天 = 1.5×。
- 取队：`remainingSeconds` 贪心，`fitting` 为空即 break（`KnowledgeReviewQueue.kt:210-240`）
  → Σ 估时 ≤ 预算；同时不会"明明还装得下却不装"（除非池空）。
- **知识点队列的预算来源是落库计划**：`timeBudgetSeconds = currentPlan.plan.timeBudgetSeconds`（`:918`）。
  该计划由 `StudySnapshotBuilder` 在快照构建时"保留或重排"（`StudySnapshotBuilder.kt:61-89`）：
  仅有 IN_PROGRESS 会话或已完成会话时才保留旧的 PLANNED 计划，否则重排并 `saveReviewPlan`。
- 生产装配：`SmartMistakeBookApplication.kt:353-365` 把 `DataStoreSchedulingSettingsStore` 与启动时读的
  `SchedulingOptions` 一起传入；`StudyExperienceRepositoryFactory.kt:36-48` 用同一份 options 做构造兜底。
  → 生产里"学生改的每日时长"确实生效（批次 49 P0 的修复目标），**但只在下一次排计划时生效**。

### 2.5 数值表（本文件判据用的算术，非新阈值）

| 每日预算（min） | 秒 | `floor(B/180)`（F8 上限） | F17 是否可能绑住 |
|---:|---:|---:|---|
| 5（代码允许，裁定外） | 300 | 1 | 否 |
| 10（裁定下限） | 600 | 3 | 否 |
| 20（默认） | 1200 | **6** | 否 |
| 30 | 1800 | **10** | **是（恰好）** |
| 60（裁定上限） | 3600 | 20 | 是（10 先绑） |
| 120（代码允许，裁定外） | 7200 | 40 | 是（10 先绑） |

考试当天（ramp=1，×1.5）：600→900（5 项）、1200→1800（10 项）、1800→2700（10 项，F17 绑）。**注**：上表的 `floor(B/180)` 是名额上限；若首档（今日错题所绑、且可出题）占 k 个名额，F17 要“恰好绑 10”需 `B ≥ 1800 + 180k`（如 k=2 需 2160 s = 36 min）。

---

## 3. F8 / F17 判据（可执行断言）

记号：`Q` = `KnowledgeReviewSessionPlan.queue`；`B` = `plan.timeBudgetSeconds`（= 生效预算）；下文用 `n(X)` 表示集合大小 `|X|`（表格内不写竖线，避免破坏 Markdown 表格）；
`K = 180`；`S` = 第二档候选集（过了 `fullLibraryScanCandidate` 且不在今日候选池）；`T` = 第一档候选；
`cap = 10`；`D(q)` = 该项估时（当前恒 180）。

### 3.1 R 组：全库捞回（门的第 1 条）

| ID | 判据（必须可机检） | 反例形态（会红） | 出处 |
|---|---|---|---|
| R-1 | 存在节点 `n ∉ T`，满足 `n ∈ S` 且 `n ∈ Q` | 第二档根本没接进并集；或今日空队列即早退 | `StudyReviewPlannerService.kt:865-922` |
| R-2 | 该 `n` 的入选理由是 `DUE_RECALL_RISK`（或 `MISSING_KNOWLEDGE_EVIDENCE`+`CALIBRATION_CHECK`），**不是** UI 自猜 | 理由集为空/不含到期类 | `KnowledgeNodeScorer.kt:56-108` |
| R-3 | 今日队列为空时仍能返回非空 `Q`（用缺前置硬过滤造空队列，见 JVM 用例形状） | 空队列 → 空计划 | `RoomBackedStudyExperienceRepositoryTest.kt:603-665` |
| R-4 | 捞回跨科（至少 2 个 subject 出现在 `S ∩ Q`） | 扫描只吃单科 | 设备用例 `:198-207` |

### 3.2 F17 组：名额封顶（门的第 2 条）

| ID | 判据 | 反例 | 出处 |
|---|---|---|---|
| F17-1 | `n(Q∩S) ≤ 10`，**任意** `B` 都成立 | 沙箱里 cap 被绕过/被改成 11 | `KnowledgeReviewQueue.kt:200` |
| F17-2 | 当 `n(S) ≥ 10` 且 `B ≥ 1800 + 180×n(T)`（有余额装下首档）时 `n(Q∩S) == 10`（**恰好**，不是 ≥） | cap 被 F8 提前截断（那就是 B 没给够，不是实现错）；或首档先吃满了名额 | 同上 |
| F17-3 | 第二档入选者 = 按 `(score DESC, nodeId ASC)` 的前 10（用同一 `KnowledgeNodeScorer` 在测试里重算并对拍） | 取了 10 个但不是最高分 10 个 | `:191-201` |
| F17-4 | 第一档不被名额挤掉：`T` 中装得下的节点全部留在 `Q`（当 `B` 足够） | 扫描把今日档挤掉 | `:159-162` 注释、`:204-208` |
| F17-5 | 同 id 同时出现在两档时，`Q` 里那一条来自第一档（`materialGroupId`/`state` 取第一档值） | 并集顺序颠倒 | `:204-208`（单测 `KnowledgeReviewQueueTest.kt:499-521`） |
| F17-6 | `cap = 0` → `Q ∩ S = ∅`（单测已覆盖，设备轮不必重造） | — | `KnowledgeReviewQueueTest.kt:459-473` |

### 3.3 F8 组：时间封顶（门的第 3 条）

| ID | 判据 | 反例 | 出处 |
|---|---|---|---|
| F8-1 | `Σ_{q∈Q} D(q) ≤ B` | 队列超预算 | `KnowledgeReviewQueue.kt:210-240` |
| F8-2 | 欠装检查：若 `n(Q) < floor(B/180)` 则**池已空**（不存在"还有装得下的候选却没装"） | 预算被错误地留白/提前 break | `:216-221` |
| F8-3 | 设置优先：真 DataStore 写 10 min → `plan.timeBudgetSeconds == 600` 且 `n(Q) ≤ 3`；写 30 min → `== 1800` 且 `n(Q) == 10`（预算被吃满；名额恰好的断言看 F17-2 的前提） | 构造参数（120）压过设置 | `StudyReviewPlannerService.kt:117-121` |
| F8-4 | 生效时点：同一份**已保留**计划内改设置，知识点队列大小不变；新建/重排计划后才变 | 改设置立刻改变已落库计划 | `:918`、`StudySnapshotBuilder.kt:61-89` |
| F8-5 | 考试扩容：考试日（ramp=1）`B = int(base×1.5)`，且知识点队列跟着放大（`base=1200 → 1800`，F17 由"6"变"10"） | 扩容只作用错题队列 | `:169-189`、`:479-483` |
| F8-6 | 错题队列同一预算：`Σ 错题估时 ≤ B`（冷启动 60 s/题，`LogDurationModel.kt:100`） | 知识点队列与错题队列用两个预算口径 | 设备用例 `:160-163` |

### 3.4 D 组：口径（spec `:28` 的硬要求）

| ID | 判据 | 证据形态 |
|---|---|---|
| D-1 | **单计划上限**：同一天连续两次排计划，每次 `n(Q_i∩S) ≤ 10`；两次之间候选池变化时 `n(Q_1∪Q_2) > 10` **是允许的** | 设备用例（今日档不占名额的夹具）：call#1 → 新增 1 个更弱的扫描候选 → call#2；断言 `n(Q_1)=10`、`n(Q_2)=10`、`n(Q_1∪Q_2)=11` |
| D-2 | **不宣称每日总量**：证据文件里不得出现"每日 ≤10"；若写"每日 10 个"必须同时写"KD-46 近似、同日可超" | 文档审查（本文件 §0 C4） |
| D-3 | **两处生产调用点各排一次**：首页计数与会话进屏各一次（同一天可 2 次），这是 D-1 的现实来源 | `SmartMistakeBookRoot.kt:649-652,963-966` |

### 3.5 N 组：负向（防止"全捞"把死点排进会话）

| ID | 判据 | 已覆盖处 |
|---|---|---|
| N-1 | 扫描行存在但 `materialGroupId = NULL` 的节点**不进** `Q` | 设备用例 `:129-132,196`；单测 `KnowledgeReviewQueueTest.kt:355-413` |
| N-2 | 只有材料、没有 ACTIVE 在册绑定题的节点**不在扫描行集里** | 设备用例 `:118-121,197`（INNER JOIN） |
| N-3 | 退役节点不在扫描行集里（`node.status != 'RETIRED'`） | SQL `ProblemOrganizationDao.kt:395`；**无设备用例**（§6 缺口） |
| N-4 | 无考试日/窗口外考试**不触发**扫描读（`createReviewPlan` 侧） | 设备用例 `:288-296`；JVM `:442-520` |

### 3.6 判读规则（与 runbook 对齐）

| 结论 | 条件 |
|---|---|
| **PASS** | R-1..R-4、F17-1..F17-5、F8-1..F8-6、D-1、N-1..N-3 全绿，且 XML `failures=0 errors=0`，且两次重跑队列摘要相同 |
| **FAIL** | 任一判据红；或断言被删/被放宽（视为不放行） |
| **UNVERIFIED** | 只跑了窄子集（如只跑 JVM）、或只有静态推断 |
| **BLOCKED** | 无可用设备/无法构建（`docs/acceptance/runbook.md:158-167` 的出口） |

---

## 4. 如何构造可复现的真实队列

### 4.0 原则

1. **可复现 = 固定时钟 + 固定输入 + 纯函数排序 tie-break**（`Clock.fixed`、`Asia/Shanghai`、
   `decay = -FsrsScheduleMath.DEFAULT_PARAMETERS[20]`、按 id 排序、`thenBy(nodeId)`）。
2. **"真实"分四层，别含糊**：真 SQLite（Room）→ 真写入端口（材料/提交）→ 真投影（事件→记忆态）→
   真设置（DataStore）。档 A 只到第 1–2 层，档 B 到第 4 层。
3. **任何夹具先自证**（fixture self-proof）：先断言"扫描行数/材料行数/候选数/记忆态时间戳"这些**输入事实**，
   再断言队列——否则队列断言绿了也可能是空集在骗人（批次 49 的教训）。

### 4.1 四档数据构造

| 档 | 构造 | 覆盖 | 优点 | 代价/风险 | 建议 |
|---|---|---|---|---|---|
| **A** | `seedStudyFacts` 直写题/绑定/节点（DB 级，行形状同生产）+ `importKnowledgeTeachingMaterials` 真写口 + **注入 `LearnerSnapshot`**（记忆态直给） | 队列数学、SQL 范围、材料过滤、F8/F17 数值 | 快、确定、无投影噪声 | 记忆态不经过投影 → 不是"真实数据"最外层 | 保留为"数学基线"（现有用例即此档） |
| **B** | 同 A 的题/绑定 + **真 `submitChoice`**（`occurredAt` 设在过去）→ 真投影落 `learner_problem_memory_state` + 真 `DataStoreSchedulingSettingsStore` + 真 `RoomBackedStudyExperienceRepository.currentKnowledgeReviewPlan` | 事件→投影→快照→排程→知识点队列的**整条生产链**，含 F8 设置生效 | 真实度够门用；仍可离线/确定 | 需要可为机器判分的题面（`single-choice.v1` 种子模板） | **门证据主档** |
| **C** | 在 B 上改用 `BundledKnowledgeBaseInstaller.install(database)` 装**真四科知识库**，绑定到真实节点 id | 真材料规模、真科目、真实节点 id 的扫描行 | 最接近发布形态 | 装机解析 4.4 MB + 多卷 sidecar（`core/data/src/main/resources/knowledge/`，单卷 4–5 MB ×16+），设备耗时/磁盘大 | 可选加强（时间盒：≥10 min 未完成则降级 B 并登记） |
| **D** | 造 `.smbk` 备份包（含 `learner_problem_memory_state`、`practice_unit_knowledge_binding`，`SmbkSelectionPlan.kt:81-133`）→ 走真恢复 | "导入数据"最强形态 | 一次造包可复用 | 需冻结格式/摘要（`tools/ci/make_v4_pack_fixture.py` 同族），成本高 | 仅在 A/B 被质疑时启用 |

**明确不做**：真拍照/OCR/模型整理/模型绑定。原因：3E 门是内核侧（spec 标题"内核侧"，
`docs/research/2026-10-11-new-stages-spec.md:15`），且模型依赖会让证据不可复现（无 key、非确定）。

### 4.2 导入数据的三条可行路（按成本）

1. **DB 级 fixture（A/B）**：`seedStudyFacts` 已封装（`StudySeedAndroidTestSupport.kt:27-61`，直写
   `problem/problem_revision/practice_unit/error_book_entry/knowledge_node/practice_unit_knowledge_binding`）。
   它绕过 Room DAO 但**不绕过 SQLite 外键**（`:22-25` 注释），行形状与生产一致。
2. **真写口**：题面材料 `importKnowledgeTeachingMaterials`（设备用例 `:601-605`）；作答走
   `repository.submitChoice`（`StudySubmissionWithoutFixtureInstrumentedTest.kt:82-107`）；知识库
   `BundledKnowledgeBaseInstaller.install`（`core/data/.../BundledKnowledgeBaseInstaller.kt:31-79`）。
3. **备份恢复（D）**：不推荐首选，理由见上。

### 4.3 时间推进的三条路（关键：3E 的"薄弱"由时间决定）

| 路 | 做法 | 依据 | 风险 | 建议 |
|---|---|---|---|---|
| **T1 事件时间（推荐）** | 把 `submitChoice(occurredAtEpochMillis = T0 − 90d)` 写进账本；投影按事件时间落 `lastReviewedAt`，排程时刻仍取 `T0` | `LearningProjector.kt:262`（`effectiveAt = max(projectedAt, occurredAt)`）、`:1298-1299`、`:1387`（`lastReviewedAt = effectiveAttemptAt`） | 事件的 `projectedAt` 必须单调（按时间升序提交）；跨时区/学习日按 `StudyDayMath` | **首选**：不碰设备时钟，完全可复现 |
| **T2 设备时钟** | `adb root` → `adb shell date …` 前推 | 同 T1 的 elapsed 公式 | **回拨**会累加 `clockAnomalyCount`（`:1397-1398`）；污染 DataStore/日志/TLS；失败后难复现 | 仅 T1 不可行时；必须记 `boot_id` 与前后 `date` |
| **T3 快照直注** | 测试直接构造 `LearnerSnapshot.problemMemoryStates` | 设备用例现状 `:430-453` | 绕过投影 → 只能当数学基线 | 仅档 A |

**T1 的具体配方（档 B 的核心）**：

```
T0 = Clock.fixed(Instant.parse("2026-01-02T08:00:00Z"), Asia/Shanghai)   // 与现有用例同锚
对每个"弱"题：repository.submitChoice(
    requestId = "3e-seed:<unit>", presentationId = "3e:<unit>",
    practiceUnitId = unit, selectedChoiceId = 错误选项,      // 判错 → 记忆态按 AGAIN/D 下降
    responseOrdinal = 1, durationSeconds = 30,
    occurredAtEpochMillis = T0 - 90d)                       // 过去时刻 = 时间推进
夹具自证：snapshot.problemMemoryStates[unit].lastReviewedAtEpochMillis == T0 - 90d
对"今日"题：occurredAt = T0 - 100d 且预算故意装不下 → 留在今日并集之外（第二档捞回才是真的）
```

### 4.4 确定性控制清单（每次跑逐项记录）

- 时钟：`Clock.fixed`；时区 `Asia/Shanghai`；学习日日界 04:00（`StudyDayMath`，`StudyReviewPlannerService.kt:777-788`）。
- decay：`planningDecay = -FsrsScheduleMath.DEFAULT_PARAMETERS[20]`（与生产 `activeFsrsDecay` 同源）。
- 排序：所有输入 `sortedBy(practiceUnitId)`；平局 `thenBy(knowledgeNodeId)`。
- 无网络/无模型：材料来自资源或本地写口；不触 `ModelTaskRepository`。
- 隔离：每用例独立库名 + 独立 `filesDir`（`IsolatedStorageContext`，设备用例 `:662-672`）——DataStore 单文件多实例会抛。
- 输出：`queue_digest = sha256(json([{id, score, reasons, band}...]))`；两次跑同 digest。

### 4.5 夹具自证清单（先证输入，再证输出）

1. `readFullLibraryScanNodes().size` == 预期（有 ACTIVE 绑定题的活跃节点数）；
2. 每个弱点行的 `materialGroupId` 非空；无材料负向行为 NULL；
3. `problemMemoryStates` 的 `lastReviewedAt` == 期望事件时刻（T1 档必查）；
4. `plan.timeBudgetSeconds` == 设置值（真 DataStore 写入后读回一致）；
5. 今日错题队列确实**不含**弱点题（否则第二档的"离开今日并集"是假的）；
6. 第二档候选数 > cap（否则封顶没在封）。

---

## 5. 设备验证执行方案

### 5.1 前置（每阶段开工前必做）

```bash
git status --porcelain -- core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/study/ \
                          core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/ \
                          core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/
git rev-parse HEAD
C:/Android/Sdk/platform-tools/adb.exe devices -l
```

规则（计划 `:84`）：他线在飞的 M 文件**不整文件抢占**；只加新文件/最小 hunk；冲突即停项登记。
> 本轮已知冲突面：`KnowledgeReviewQueue.kt`、`ReviewPlanner.kt`、`ReviewPlannerV2.kt`、
> `RoomBackedStudyExperienceRepository.kt` 都是 M（他线）。**3E 验证的改动应尽量只落在测试文件**
> （`StudyReviewPlanner3EScanInstrumentedTest.kt` 是干净文件）+ 新增测试文件，避免碰这些 M 文件。

### 5.2 步骤（先红 → 最小修 → 绿 → 复跑）

**Step 0（红，必须先跑）**：原样跑现有类，确认 C1 的静态推断。

```bash
ANDROID_SERIAL=emulator-5590 ./gradlew.bat :core:data:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.tingyun.smartmistakebook.core.data.study.StudyReviewPlanner3EScanInstrumentedTest \
  --console=plain
# 期望（静态推断，以实跑为准）：至少 3 处断言红（预算 120 / 队列 2 / 知识点 2）
```
证据：`gs0-red.log` + `gs0-red.xml`（`tests/failures` 计数照录）。

**Step 1（最小修，测试侧）**：给"按显式预算验证数学"的夹具传
`budgetResolution = BudgetResolution.CONSTRUCTOR_ONLY`（`StudyReviewPlannerService.kt:60` 的 internal 枚举；
androidTest 能访问 internal 主符号——现有用例已在用 `internal class StudyReviewPlannerService`，`:407`）。
这是**对齐语义**（与 JVM 孪生 `StudyReviewPlannerExamWindowTest.kt:344` 完全同法），**不是放宽断言**：
生产"设置优先"由 Step 2 的**新增**用例覆盖。

**Step 2（新增用例，覆盖 D/F8-3/F8-4）**：新增 1 个文件（不动现有 M 文件），至少 4 例：

| 用例 | 断言（对应判据） |
|---|---|
| `settingsBudgetDrivesTheKnowledgeQueueThroughTheRealStore` | 真 DataStore 写 10 min → `plan.timeBudgetSeconds == 600`、`n(Q) ≤ 3`（可用候选足够时 `== 3`）（F8-3） |
| `capBindsOnlyWhenTheBudgetReachesThirtyMinutes` | 30 min → `n(Q) == 10`（今日档为空的夹具上 `n(Q∩S) == 10`）（F17-2/F8-3）；20 min → `n(Q) ≤ 6`（F8-1） |
| `capIsPerPlanCompositionNotPerDay` | call#1 取 10；DB 增 1 个更弱扫描候选；call#2 取 10；`n(union) == 11`（D-1） |
| `realSubmissionProjectionFeedsTheScanTier`（档 B） | 真 `submitChoice`（T0−90d）→ 投影 → 仓库 `currentKnowledgeReviewPlan` → 弱点被捞回（R-1/R-2） |

**Step 3（绿 + 复跑）**：同一命令跑两次；两次必须同绿且 `queue_digest` 相同；XML 计数照录。

**Step 4（可选 C 档）**：装真四科知识库后重跑 Step 2/3；记录 install 耗时、节点数、材料卷数；
超时降级并登记。

### 5.3 命令（精确）

```bash
# 构建 androidTest APK（串行阶段；先记录 APK sha）
./gradlew.bat :core:data:assembleDebugAndroidTest --console=plain
sha256sum core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk

# 只跑 3E 类（禁用 :app:connectedLocalFirstDebugAndroidTest —— 计划红线）
ANDROID_SERIAL=emulator-5590 ./gradlew.bat :core:data:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.tingyun.smartmistakebook.core.data.study.StudyReviewPlanner3EScanInstrumentedTest \
  --console=plain

# 单方法（迭代期）
… -Pandroid.testInstrumentationRunnerArguments.class=…StudyReviewPlanner3EScanInstrumentedTest#fullLibraryScanRecallsWeakNodesOutsideTodaysQueueUpToTheDailyCap
```

说明：`:core:data` 是**无 flavor 的 library 模块**，所以 `connectedDebugAndroidTest` 合法；
`app` 才有 `networkMode` flavor（`app/build.gradle.kts:50-57`），其 `connectedLocalFirstDebugAndroidTest` 被计划禁止（`:89`）。

### 5.4 证据文件清单（落 `docs/research/evidence/2026-11-09-batch-50/3e/`）

| 文件 | 内容 |
|---|---|
| `00-index.md` | 结论表、设备身份、APK sha、命令、判读 |
| `gs0-red.log` / `gs0-red.xml` | Step 0 红（证明"先红"） |
| `gs1-tree.txt` | `git rev-parse HEAD`、`git status --porcelain` 的 sha256（脏树清单）、涉及文件的 `git diff --stat` |
| `gs1-apk-identity.txt` | androidTest APK sha256 + 设备侧 `base.apk` sha256（`adb pull` 后算，等价性同批次 45 做法） |
| `gs2-green.log` / `gs2.xml` | 绿轮（`tests/failures/errors/skipped`） |
| `gs3-queue-dump.json` | 每个用例：`B`、`n(S)`、`n(T)`、`Q`（id/score/reasons/materialGroup）、`queue_digest` |
| `gs4-rerun.diff` | 两次跑的 digest/队列逐位比较 |
| `gs5-recompute.py` + `gs5-recompute.out` | 从 dump 重算 F17-1/2/3 与 F8-1/2（独立于被测代码的算术复核） |
| `gs6-negative.txt` | N-1..N-3 的证据行（行集/队列成员） |
| `EVIDENCE.sha256` | 上述文件摘要 |

### 5.5 失败分类（先分类再决定重试/修复）

| 现象 | 类别 | 处置 |
|---|---|---|
| 预算断言 120 vs 1200 | **逻辑/过期夹具**（C1） | 按 Step 1 修夹具，不重试 |
| 队列数 ≠ 期望但预算对 | 候选池/材料/绑定夹具 | 先看夹具自证 1–6，再看 SQL 行集 |
| `IllegalArgumentException: Granted slice must not be negative` | 已知 KD-64 形态 | 检查预算是否 < 单题时长（KD-64 已修，但**再犯即重开**，`docs/known-defects.md:2563`） |
| DataStore 抛"multiple instances" | 夹具隔离缺陷 | 检查 `IsolatedStorageContext`（`:662-672`） |
| 安装/设备掉线 | 基础设施 | 重试 1 次；再失败标 BLOCKED |

### 5.6 红线

- 不得改 `FULL_LIBRARY_SCAN_DAILY_CAP`、`SchedulingOptions` 区间、`ReviewCapacityPolicy`、`EXAM_RAMP_DAYS`、
  `WEAKNESS_THRESHOLD`、权重表、`ReviewPlannerV2.VERSION`（计划 `:86`；`:89`）。
- 不得把"红 → 删断言"当修复；红必须能被一句话解释成"夹具口径过期"或"实现缺陷"。
- 不得在证据里把"每次 ≤10"写成"每日 ≤10"（C4/KD-46）。
- 不得跑 `:app:connectedLocalFirstDebugAndroidTest`（计划 `:89`）。

---

## 6. 覆盖矩阵（现有 vs 门要求）

| 判据 | 现有覆盖 | 位置 | 缺口 |
|---|---|---|---|
| R-1/R-4 捞回+跨科 | 设备用例 1 | `StudyReviewPlanner3EScanInstrumentedTest.kt:106-218` | 依赖预算 120（C1 后失效） |
| R-2 理由 | 单测 | `KnowledgeReviewQueueTest.kt:124-140` | 设备侧无断言 |
| R-3 空今日队列 | JVM 仓库用例 | `RoomBackedStudyExperienceRepositoryTest.kt:603-665` | 无设备版 |
| F17-1/2 名额 | 设备用例 1 + 单测 | 设备 `:176-191`；单测 `:435-458` | 设备侧预算必须 ≥1800（首档占 k 个名额时 ≥1800+180k；现用例 2700 构造值已失效） |
| F17-3 前 10 排序 | 设备用例 1（同分按 id） | `:187-191` | 只测同分平局，未测"不同分取高" |
| F17-4/5 首档不被挤 | 设备用例 1 | `:192-194`、单测 `:499-521` | — |
| F8-1/2 时间封顶 | 设备用例 2 | `:224-261` | 依赖预算 360（C1 后失效） |
| F8-3 设置优先 | JVM 仅函数级 | `StudyReviewPlannerExamWindowTest.kt:479-520` | **无设备/真 DataStore 用例** |
| F8-4 生效时点 | 无 | — | **完全缺失** |
| F8-5 考试扩容×知识点 | 设备用例 1 只验"窗口外不放大" | `:155-159` | 窗口内放大未验 |
| D-1 单计划 vs 每日 | 无 | — | **完全缺失** |
| N-1/N-2 负向 | 设备用例 1 | `:118-132,196-197` | — |
| N-3 退役节点 | 无 | — | 缺（SQL 有判定，无用例） |
| N-4 无考试不读扫描 | 设备用例 3 | `:267-297` | 依赖预算 120（C1 后失效） |

**结论**：测试**骨架齐全**，缺的是 ① 夹具口径更新（C1）、② 设置真实生效（F8-3/F8-4）、
③ 单计划/每日口径（D-1）、④ 退役节点负向（N-3）。

---

## 7. 未验证项、风险与需裁定项

### 7.1 未验证（本轮未跑，不得当结论）

| # | 项 | 状态 |
|---|---|---|
| U1 | 现有 3E 设备用例 3 例均红 | **静态推断**（C1 的代码链）；未运行 |
| U2 | HEAD 编译失败 | **静态推断**（C2）；未跑 `:core:domain:compileKotlin` |
| U3 | 设备上 `budgetResolution`（internal）在 androidTest 可见 | **推断**（同类 internal 类已在用）；未编译验证 |
| U4 | 档 B（真 `submitChoice` → 投影 → 队列）端到端可行 | **未跑**；配方基于 `StudySubmissionWithoutFixtureInstrumentedTest` 的既有形状 |
| U5 | 真四科知识库安装耗时/磁盘 | **未测**；只有资源体积（单卷 4–5 MB，共 16+ 卷） |
| U6 | 设备时钟回拨的实际影响 | **未测**（仅代码推断 `clockAnomalyCount`） |

### 7.2 风险

| # | 风险 | 影响 | 缓解 |
|---|---|---|---|
| RK1 | 他线继续改 `KnowledgeReviewQueue.kt`/`ReviewPlannerV2.kt` | 3E 证据与代码错位 | 证据绑 `git diff` sha；每次跑前复检 |
| RK2 | 判据 F17-2 用"恰好 10"在候选=10 时脆弱（少 1 个候选就红） | 误红 | 夹具固定 12 个弱点（>cap+1），并自证 `n(S) ≥ 10` |
| RK3 | 知识点队列预算取落库计划，若同时有 IN_PROGRESS 会话会"保留旧计划" | F8-3 断言随场景漂移 | F8-3 用在**全新库**上（无会话），F8-4 专门测保留场景 |
| RK4 | `quizAbleScopeNodes` 单科 64 条材料上限 | 今日档可能少节点 | 扫描档已兜底（`:866-871`）；夹具控制单科节点数 < 64 |
| RK5 | `ReviewCapacityPolicy` 与 `SchedulingOptions` 口径分叉 | 设备取值越界（如 5 min） | 设备轮取 10..60 min，分叉登记 Q2 |

### 7.3 需主会话裁定（本文件不擅自取值）

| # | 问题 | 为什么必须裁 |
|---|---|---|
| Q1 | 3E 门的"每日上限"是否就按 KD-46 的"每次排计划 ≤10"判？ | 若要求严格每日，需新增"当日已扫描集合"持久面（KD-46 的 fix direction ②）——那是产品裁定 + schema 变更，不在本批 |
| Q2 | F8 的有效区间取 5..120（代码）还是 10..60（裁定文）？ | 两处都在树里，改任一处都会改产品可设范围；本批只能如实登记 |
| Q3 | "F8 = 全天总预算"还是"每个队列各自预算"？ | 当前实现是后者（单日可达 2×）；产品文案/容量预览若承诺前者即不成立 |
| Q4 | 默认 20 min 下 F17 实际封不住（6 < 10），产品是否接受"默认用户每次最多 6 个知识点"？ | 影响设置页文案与验收口径，不是实现能自行定的 |

---

## 8. 参考（文件:行，均为本轮实读）

**需求/计划**
- `.zcode/plans/plan-2026-11-09-batch-50-release.md:14`（3E 研究结论）、`:44-45`（G2 任务）、`:86,89`（硬约束）
- `docs/research/2026-10-11-new-stages-spec.md:15-29`（3E spec，`:28` 口径、`:29` 门）
- `docs/requirements-fused-final-2026-10-11.md:114,115,118,228`
- `docs/research/2026-11-02-ws3-review-budget-adjudication.md:14,43-45,57`
- `docs/research/2026-11-09-batch-50-baseline.md:231`
- `docs/known-defects.md:2055`（KD-46）、`:2563`（KD-64）
- `docs/acceptance/ledger.md:154`（A-023；其 `方法` 列指向本设备用例，但 A 域"大样本旅程"仍 BLOCKED，本证据不单独清该行）、`:515`（E-024）
- `docs/research/2026-11-05-release-gates.md:10`（G-REL-00：干净 HEAD 不可编译的既有记录）

**生产代码**
- `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/StudyReviewPlannerService.kt:54-60`（预算口径枚举）、
  `:117-121`（预算解析）、`:169-189`（考试扩容）、`:479-492`（计划预算+扫描读门）、`:516-570`（候选组装/EXAM 通道）、
  `:757`（材料上限 64）、`:777-788`（学习日）、`:798-803`、`:824-852`（第一档）、`:865-922`（第二档/并集/预算来源）、
  `:918`、`:921`
- `core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/KnowledgeReviewQueue.kt:68-103`（reasons 字段+isConflicted）、
  `:118-141`（最小 R 聚合）、`:171-242`（取队/名额/预算）、`:260-280`（多样性降权）
- `core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/KnowledgeReviewFullLibraryScan.kt:6-30`（cap 口径/KD-46）、
  `:51-73`（范围两道门）
- `core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/KnowledgeNodeScorer.kt:56-108`（到期/掌握风险与理由）、
  `:198-201`（难度档）
- `core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/LogDurationModel.kt:100`（60 s 冷启动先验）、
  `:109`（180 s 中档基线）
- `core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/AlgorithmConstants.kt:88`（45 天证据窗）、
  `:105`（WEAKNESS_THRESHOLD=0.35）、`:148`（考试扩容 0.5）
- `core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/StudyExperienceRepository.kt:826-839`（SchedulingOptions 5..120）
- `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/ReviewCapacityPolicy.kt:33-42,89-93`（10..60/5 步进，无调用点）
- `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/dao/ProblemOrganizationDao.kt:327-397`（扫描 SQL）
- `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/RoomKnowledgeBaseStore.kt:460-471`（CHAR(31) 解析）
- `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/settings/DataStoreSchedulingSettingsStore.kt:43-53`（缺省 20 min）
- `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/RoomBackedStudyExperienceRepository.kt:100-112`（构造）、
  `:232-251`（planner 装配）、`:1055-1067`（知识点计划入口）、`:1158-1161`（快照源）
- `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/StudySnapshotBuilder.kt:61-89`（计划保留/重排+落库）
- `core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/LearningProjector.kt:262`（effectiveAt）、
  `:1298-1299`（时钟回拨/生效时刻）、`:1387`（lastReviewed 落点）
- `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/StudyExperienceRepositoryFactory.kt:36-48`（工厂）
- `app/src/main/kotlin/com/tingyun/smartmistakebook/SmartMistakeBookApplication.kt:353-365`（生产装配）
- `app/src/main/kotlin/com/tingyun/smartmistakebook/SmartMistakeBookRoot.kt:649-652`（首页计数调用）、`:963-966`（会话进屏调用）
- `app/src/main/kotlin/com/tingyun/smartmistakebook/SchedulingSettingsScreen.kt:88-108`（设置页 5..120）

**测试**
- `core/data/src/androidTest/kotlin/.../study/StudyReviewPlanner3EScanInstrumentedTest.kt:98-218`（用例 1）、`:224-261`（用例 2）、
  `:267-297`（用例 3）、`:313-353`（考试门夹具）、`:362-400`（harness）、`:403-423`（服务构造，**无 budgetResolution**）、
  `:430-467`（快照直注）、`:473-606`（种子）、`:650-660`（计数端口）、`:662-672`（隔离 Context）
- `core/data/src/androidTest/kotlin/.../study/StudySubmissionWithoutFixtureInstrumentedTest.kt:82-107`（真提交链）、`:295-344`（可判分题种子）
- `core/data/src/androidTest/kotlin/.../study/StudySeedAndroidTestSupport.kt:27-61`（DB 级种子）
- `core/data/src/test/kotlin/.../study/StudyReviewPlannerExamWindowTest.kt:344`（CONSTRUCTOR_ONLY 夹具）、`:479-520`（设置优先/无 store 兜底）
- `core/data/src/test/kotlin/.../study/RoomBackedStudyExperienceRepositoryTest.kt:451-600`（两档并集）、`:603-665`（空队列扫描）
- `core/domain/src/test/kotlin/.../KnowledgeReviewQueueTest.kt:355-413`（范围负向）、`:435-458`（cap）、`:459-473`（cap=0）、
  `:475-497`（预算）、`:499-521`（同 id 首档优先）、`:523-560`（5000 候选规模）
