# 算法版本清单（Wave 0 / W0-3 · Q1）

> **2026-10-18 绘图路线更新：** 所有生图入口统一判定，由模型按共享规则选择JS或图像模型；两者均为生图方式，正式统一接线待实施。当前约束见[教学图形契约](../current/visual-generation-contract.md)。本文既有日期、提交、测试、缺陷与旧方案作为历史事实保留，不代表新路线已实现。

> 建立于 2026-09-30（内核修复路线图 Wave 0 的 W0-3）。
> 上游：`docs/research/2026-09-28-kernel-remediation-roadmap.md` §0 执行守则、
> `docs/agent-first-refactor-decisions-2026-09-23.md`「阶段 3A 号段登记」。
> 姊妹文档：`docs/research/kernel-projection-rollback.md`（投影回退流程）。

## 1. 这份清单回答什么

审计 Q1 的原话是"投影版本仅 3 次 bump 历史，无发布清单"——**改了数值之后没人能回答"改了什么、
凭什么改、拿什么证明、能不能回退"**。所以每次 bump 必须在这里留一行，五项缺一不可：

| 项 | 含义 | 不写的后果 |
|---|---|---|
| **版本串** | 被 bump 的常量与它的新值（`LearningCoreVersions.*`、`STUDY_DATABASE_VERSION`） | 版本对不上时无法定位是哪一次改动 |
| **变更公式** | 被改的公式/常数，改前 → 改后（逐字） | "数值变了"与"公式变了"分不清，复核无从下手 |
| **数据来源** | 依据（审计编号 / 裁定编号 / 官方文档 / 实测数据），带 file:line 或链接 | 变成"某次会话觉得该这么改" |
| **测试证据** | 证明这次改动按预期生效的测试名与结果 | 改完没人知道它到底生效了没有 |
| **archive 是否已落** | bump 前旧投影是否已进 `projection_archive`（W0-1 ③） | 出问题时旧投影已被覆盖，回退无门 |

**纪律**（与 roadmap §0 同源）：
1. 动投影公式 → 必 bump `LearningCoreVersions.PROJECTOR`（复合串变 → 版本不匹配 → 自动全量重放）；
   **bump 前**旧投影必须已归档（`projection_archive`，见回退文档）。
2. 动 Room schema → bump `STUDY_DATABASE_VERSION` + 非破坏迁移 + 导出新 JSON。
3. **「待裁」值不得自行取值**：先验/θ/ε/γ/fuzz 之类需要裁定的数值，先过用户（裁决落
   `docs/agent-first-refactor-decisions-2026-09-23.md`），再在这里登记。

## 2. 号段登记（阶段 3A，已占号）

| 资源 | Wave 0 前 | 3A 占用 | 用途 | 状态 |
|---|---|---|---|---|
| `STUDY_DATABASE_VERSION` | 53 | **54** | Wave 0：`projection_archive` 新表 + `review_plan` 指纹列合并 | ✅ 已用（Wave 0） |
| `STUDY_DATABASE_VERSION` | 53 | **55** | Wave 2：`review_log.state`（v54→55 迁移，含回填） | ✅ 已用（Wave 2） |
| `STUDY_DATABASE_VERSION` | 53 | **56** | Wave 3 批次 1：`learner_knowledge_mastery_state` 六列——`success_weight`/`failure_weight`（β-二项）+ `memory_stability_days`/`memory_difficulty`/`last_attempt_at_epoch_millis`/`last_attempt_study_day`（知识点记忆卡）；**不回填**，全量重放重建 | ✅ 已用（Wave 3 批次 1） |
| `STUDY_DATABASE_VERSION` | 53 | **57** | 阶段 3B 步骤一（D-M M5，批次 B1）：删 `projection_consumption` 整表 + `learner_problem_memory_state` 两死列（`last_reviewed_epoch_day`/`last_attempt_id`）；非破坏（重建搬运旧行），零算法 bump | ✅ 已用（3B B1） |
| `STUDY_DATABASE_VERSION` | 53 | **58** | 阶段 3B 步骤一（D-M M1，批次 B2）：删两张 legacy fixture 投影表（`problem_memory_state`/`knowledge_mastery_state`）+ fixture 播种系统退场（`seedFixture` 端口/`FixtureSeedDao`/`StudyFixtureSource` 与 debug 源集）；DROP 只删 fixture 专表、真实学习数据一行不碰，零算法 bump | ✅ 已用（3B B2） |
| `STUDY_DATABASE_VERSION` | 53 | **59** | 阶段 3B 步骤三（KF-32，批次 B4）：新表 `binding_change_event`（改绑补偿事件 `BINDING_CHANGED` 的载荷行）+ 索引；**非破坏**（纯新增，既有表一列不删一行不动）。结构面零数值影响；同批 `PROJECTOR`/`ATTRIBUTION`/`LEDGER` 的 bump 来自重派生语义（§3.13） | ✅ 已用（3B B4） |
| `LearningCoreVersions.PROJECTOR` | `projector-v7` | **`projector-v8`** | **Wave 2**：w20 个性化接线 + 学习日界 04:00（两处数值口径变更合并为一次 bump） | ✅ 已用（Wave 2） |
| `LearningCoreVersions.PROJECTOR` | `projector-v8` | **`projector-v9`** | Wave 3 批次 1：β-二项表示 + 知识点记忆卡 + E 判据（`SKIP_POLICY` 同批 v3 → v4） | ✅ 已用（Wave 3 批次 1） |
| `LearningCoreVersions.PROJECTOR` | `projector-v9` | **`projector-v10`** | Wave 3 批次 3（P5）：首答不计跨日连击 + 曝光计数每条 +1（两处投影输出修正） | ✅ 已用（Wave 3 批次 3） |
| `LearningCoreVersions.REVIEW_PLANNER` | `review-planner-v6` | **`review-planner-v7`** | Wave 3 批次 3：V1 排程曲线 FSRS 唯一化 + 接投影同源个性化 decay（**curve 归属记在本串**：`FORGETTING_CURVE` 保持 curve-v3，投影侧语义未变） | ✅ 已用（Wave 3 批次 3） |
| `ReviewPlannerV2.VERSION`（V2 的有效串，独立于 REVIEW_PLANNER） | `review-planner-v2` | **`review-planner-v3`** | Wave 3 批次 3：KF-08 前置门改硬过滤；`PLAN_FINGERPRINT_SCHEMA_VERSION` 同批 `canonical-v7 → v8` | ✅ 已用（Wave 3 批次 3） |
| `LearningCoreVersions.REVIEW_PLANNER` | `review-planner-v7` | **`review-planner-v8`** | 批次 R（裁决 28 读侧语义闭合）：V1 跳过/风险判据改 `effectiveStatus` 现算（45 天窗并入出口；锚点 = 最后作答，双钟关闭）；V1 指纹 `canonical-v5 → v6` 同批 | ✅ 已用（批次 R） |
| `LearningCoreVersions.REVIEW_PLANNER` + `REVIEW_COMPOSITE` | `review-planner-v8` | **退场（不占号）** | 阶段 3B 步骤二（D-M M6，批次 B3）：V1 `ReviewPlanner` 类/`plan()`/版本串/复合串/`useReviewPlannerV2` 回滚开关一并删除——V1 生产不可达（默认 V2 且无翻闸点）；知识点打分抽成 `KnowledgeNodeScorer`。**零 bump**：删的是不可达路径，计划行输出不变（定格测试与证明见 §3.12） | ✅ 已退场（3B B3） |
| `ReviewPlannerV2.VERSION` | `review-planner-v3` | **`review-planner-v4`** | 批次 R：V2 掌握风险分支改 `effectiveStatus`（KF-16 先修压制接线）；指纹 `canonical-v8 → v9` 同批 | ✅ 已用（批次 R） |
| `LearningCoreVersions.SELECTOR` | `selector-v6` | **`selector-v7`** | 批次 R：`requiresCalibration` 的状态/新鲜度门槛改 `effectiveStatus` 现算 | ✅ 已用（批次 R） |
| `LearningCoreVersions.PROJECTOR` | `projector-v10` | **`projector-v11`** | W4-2 投影批（2026-10-02）：①裁决 26「看答案」补 KC 归因（reveal 也 lapse 该题绑定的知识点记忆卡）；②KF-17（裁决 8）同批/source 新建卡到期日按序 +1 天错峰。复合串 `learning-core-v10 → v11`（三串同前缀） | ✅ 已用（W4-2） |
| `LearningCoreVersions.ATTRIBUTION` | `attribution-v2` | **`attribution-v3`** | W4-2：新增 **REVEAL → 知识点记忆卡** 归因路径（裁决 26），归因路径集合变化 | ✅ 已用（W4-2） |
| `LearningCoreVersions.EVIDENCE` | `evidence-v4` | **`evidence-v5`** | W4-2（裁决 26 落用本号段）：「看答案」语义——独立 `AnswerRevealOutcome` 事件除题卡外也 lapse 绑定的知识点记忆卡；号段原登记用途（Wave 1 证据定价 + 看答案口径）中「看答案口径」在此落用 | ✅ 已用（W4-2） |
| `LearningCoreVersions.PROJECTOR` | `projector-v11` | **`projector-v12`** | 阶段 3B 步骤三（KF-32，批次 B4）：改绑 → 重放历史（upcasting）——重放期知识归因由"写时快照"改为按当前 `practice_unit_knowledge_binding` 重派生；复合串 `learning-core-v11 → v12` | ✅ 已用（3B B4，§3.13） |
| `LearningCoreVersions.ATTRIBUTION` | `attribution-v3` | **`attribution-v4`** | 3B B4（KF-32）：归因**来源**由写时钉死改为重放期按当前绑定重派生（历史证据重挂新节点） | ✅ 已用（3B B4，§3.13） |
| `LearningCoreVersions.LEDGER` | `ledger-v2` | **`ledger-v3`** | 3B B4（KF-32）：账本新增补偿事件 kind `BINDING_CHANGED`（仅全量重放消费，与 `ATTEMPT_CORRECTION` 同类） | ✅ 已用（3B B4，§3.13） |
| `LearningCoreVersions.PROJECTOR` | `projector-v12` | **`projector-v13`** | K1（3D 内核侧余件批 2）：空绑定（诚实未分类）题的重放改挂 `pseudo:<科目>` 兜底桶——重放装配侧复用既有 `ensurePseudoKnowledgeBinding` 物化伪绑定事实，与写路径逐条同规则；复合串 `learning-core-v12 → v13` | ✅ 已用（K1 批 2，§3.19） |
| `LearningCoreVersions.ATTRIBUTION` | `attribution-v4` | **`attribution-v5`** | K1 批 2：同一根轴上的第二个来源变更——空绑定题的重放归因来源由"退回写时快照"改为"伪绑定事实重派生" | ✅ 已用（K1 批 2，§3.19） |

**Wave 0 不动版本串**：本波只做回退能力、读时校验、常数收敛与清单，**没有任何投影输出变化**，
所以 `PROJECTOR` / `EVIDENCE` / `REVIEW_PLANNER` 一律不动（改公式才 bump）。

## 3. 记录

### 3.1 追溯登记（Wave 0 之前的 bump，凭源码注释与审计回填）

| 版本串 | 变更 | 公式/口径 | 数据来源 | 测试证据 | archive |
|---|---|---|---|---|---|
| `projector-v5` → `v6` | 2026-09-11 | `projectChatEvidence` 开始写 `lastEvidenceAt` / `lastEvidenceDirection`（同一条账本事件的投影结果变了，增量不重算已消费事件 → 必须靠版本不匹配触发全量重放） | 审计 `AUDIT-ALGORITHM-2026-09-09` §3.5；`LearningCoreVersions.kt:8-14` | `core:domain` 投影等价用例（`LearningProjectorTest` 等） | ❌ 当时无归档表（archive 到 2026-09-30 才有） |
| `projector-v6` → `v7` | 2026-09-11 | 跨日判定 `delta_t` 改由 `lastReviewedAtEpochMillis` + 事件 UTC 偏移现算，不再读 `ProblemMemoryState.lastReviewedEpochDay`（该字段会被曝光通道写成 UTC 日序默认值） | 审计 §3.7；`LearningCoreVersions.kt:15-21` | 同上 + `LearningProjectorTest` 的日序边界用例 | ❌ 同上 |

### 3.2 Wave 0（本波，2026-09-30）

| 版本串 | 变更 | 公式/口径 | 数据来源 | 测试证据 | archive |
|---|---|---|---|---|---|
| `STUDY_DATABASE_VERSION` 53 → **54** | 加表 `projection_archive`（W0-1 ③）；`review_plan` 去掉 `input_fingerprint`（W0-2，两列恒同值） | **无算法公式变更**：投影输出、计划指纹、计划 id 逐位不变 | roadmap W0-1/W0-2；审计 Q2/Q4/Q5 | `KernelWave0SchemaContractTest`（5 例，绿）；`FullMigrationMatrixInstrumentedTest`（1→54 矩阵 + 合并保行 + 归档落库，绿） | ➖ 本波建立归档能力；`PROJECTOR` 未 bump，因此没有跨版本重放要归档 |
| `PROJECTOR` / `EVIDENCE` / `REVIEW_PLANNER` / `SKIP_POLICY` 等 | **未 bump** | 常数收敛（W0-4）只删同值副本，值一个没改 | roadmap W0-4；审计 Q5 | `core:domain` 496 / `core:database` 93 / `core:data` 563（既有红 5 条 KB 金标）——`ReviewPlannerTest` / `ReviewPlannerV2Test` 全绿（指纹与选序逐位不变） | ➖ |

### 3.3 Wave 2 · L2 罚项 γ 与 σ（**已取值，2026-10-01**）

| 项 | 内容 |
|---|---|
| 变更公式 | `FsrsParameterOptimizer` 的拟合目标改为 `mean BCE + γ·Σ((w−w_init)/σ)²/N`（序列末态 loss，官方 BPTT 口径） |
| **取值（裁定 14：对齐官方默认）** | **γ = 1.0**；σ = 官方 `DEFAULT_PARAMS_STDDEV_TENSOR` 的 21 个先验标准差（6.43, 9.66, 17.58, 27.85, 0.57, 0.28, 0.6, 0.12, 0.39, 0.18, 0.33, 0.3, 0.09, 0.16, 0.57, 0.25, 1.03, 0.31, 0.32, 0.14, 0.27），逐字落 `FsrsParameterOptimizer.PARAMETER_SIGMA` |
| **数据来源（溯源）** | `open-spaced-repetition/fsrs-optimizer` **v6.5.0**（release 2026-02-02），`src/fsrs_optimizer/fsrs_optimizer.py`：γ 默认 `gamma: float = 1`（:446，Trainer.__init__）/ `gamma: float = 1.0`（:1424）；罚项 `Σ((w − w_init)²/σ²)·γ·batch/epoch`（:514-523 训练）、`BCE.mean() + penalty·γ/train_set_size`（:579-584 评估）；σ 定义 :79-100 |
| 测试证据 | `FsrsParameterRecoveryTest`（800 卡合成数据，w20 回到真值 ±0.08；loss 随 w20 变化）；`SchedulingEvaluationTest` 的采纳门/门槛/剔除用例 |
| archive | ➖ 本波 `PROJECTOR` bump 走 W0-1 的归档 → 重放路径（首次实战） |

### 3.4 Wave 2（2026-10-01）：一次 bump 覆盖两处数值变更

| 版本串 | 变更 | 公式/口径 | 数据来源 | 测试证据 | archive |
|---|---|---|---|---|---|
| `STUDY_DATABASE_VERSION` 54 → **55** | `review_log` 加 `state INTEGER NOT NULL DEFAULT 0` + **回填**（无前条=New/前条 AGAIN=Relearning/同日=Learning/跨日=Review） | 无算法数值变更（新增列是拟合侧输入；`delta_t_days` 语义不变） | roadmap W2-4/KF-23；fix-plan KF-23（回填口径 UNVERIFIED → 真库用例核验） | `KernelWave2SchemaContractTest`（3 例）；`KernelWave2MigrationInstrumentedTest`（真库回填 [0,1,2,1,3]）；`ReviewLogStateTest`（写侧同口径） | ➖ |
| `PROJECTOR` v7 → **v8**（`PROJECTION_COMPOSITE` → `learning-core-v8(...)`） | ①W2-1/KF-01：`retention/factor/intervalDays` 去隐式默认，稳定性更新/间隔反函数/投影器毕业间隔全部走模型自己的 `-w20`；②W2-4/KF-25：学习日界 00:00 → 04:00（`StudyDayMath.DAY_START_HOUR`） | 有优化参数的学习者在线 R/间隔口径随之个性化；跨 00:00–04:00 的复习归属前一学习日 → `elapsedCalendarDays`/streak/毕业判定随口径变化 | roadmap W2-1/W2-4；审计 KF-01/KF-25 | `FsrsParameterRecoveryTest`（恢复+灵敏度）；`StudyDayMathTest`（04:00 边界 5 例）；`FsrsProjectionBehaviorTest`（跨日/毕业/leech 夹具按新口径重定基）；JVM 全套 + 全量 `:core:database` 仪器化门 | ✅ 本波首次实战：bump 触发全量重放，`projection_archive` 先归档旧投影 |
| `REVIEW_COMPOSITE` 同步（含 `PROJECTOR` 与 `review-planner-v6`） | 当日计划按新学习日口径留位；`isCurrentPlannerVersion` 读时校验会把旧计划判 STALE 重排 | — | W0-2 的读时门 + 本波口径变更 | `RoomBackedStudyExperienceRepositoryTest` 的版本门对照用例 | ➖ |
| `EVIDENCE` | **未 bump**（维持 `evidence-v4`） | 揭示链路（Wave 1）账本逐位不变，见 §3.4 尾注 | 台账裁决 1(B)/18 | — | ➖ |

**W1-1 / W1-2 不在本波**：台账裁决 3 把采集侧（毫秒时长、展示时长、hint 上报链）后移到阶段 5
新复习栏一并实现；`evidence-v5` 号段继续保留给届时（或 Wave 3）的语义变更。

### 3.5 Wave 3（2026-09-30/10-01）：三次 bump + 一批零 bump 判定

| 版本串 | 变更 | 公式/口径 | 数据来源 | 测试证据 | archive |
|---|---|---|---|---|---|
| `STUDY_DATABASE_VERSION` 55 → **56** | 掌握状态表六列（s/f + 记忆卡） | 表示重做（W3-1）；**不回填**——`projector-v9` bump 触发全量重放重建 | 台账裁决 12/13 修订；roadmap W3-1 | `KernelWave3SchemaContractTest`；`MasteryMemoryCardTest`；矩阵 1→56 3/3 | ✅ 重放路径先归档（W0-1 第二次实战） |
| `PROJECTOR` v8 → **v9**（复合串 learning-core-v9；`SKIP_POLICY` v3 → v4 同批） | KC 表示换 β-二项 + Wilson（展示层）；新增知识点记忆卡；MASTERED 改判 E 判据（稳定度 ≥ 21 天 ∧ 当前召回概率 ≥ 0.9） | W3-1/W3-2；判据换轴（台账「裁决 13 · 修订」） | 同上 + `docs/research/2026-09-30-mastery-criterion-evidence.md` | `MasteryMemoryCardTest`（1 题第 5 次/26 天、2 题第 11 次/70 天）；仪器化 183 例（2 例性能门环境档，点名）；矩阵 1→56 3/3 | ✅ |
| `ReviewPlannerV2.VERSION` v2 → **v3** + 指纹 `canonical-v7 → v8` | 前置门硬过滤（KF-08）：缺前置候选不进计划 | 候选集变化 | fix-plan KF-08；roadmap W3-3 | `ReviewPlannerV2Test`（硬过滤 + 先修恢复回池）；`RoomBackedStudyExperienceRepositoryTest` 端到端 | ➖（计划层，无投影归档） |
| `REVIEW_PLANNER` v6 → **v7** | V1 排程曲线 FSRS 唯一化（`ForgettingCurveAlgorithm` 枚举删除）+ 接投影同源个性化 decay | 排程口径与投影同源；**curve 归属**：`FORGETTING_CURVE` 保持 curve-v3（投影侧未变），变化记本串 | DEC:1942「规划侧个性化随 Wave 3」；批次 3 | `ForgettingCurveTest`（FSRS 4 例）；全量 JVM 绿 | ➖ |
| `PROJECTOR` v9 → **v10** | P5 两处投影输出修正：首答不计跨日连击；导师曝光 `answerRevealCount` 每条 +1 | 存量行带旧计数且被毕业/leech/特征输入消费 → 全量重放 | fix-plan P5；审计 P5 行 | `FsrsProjectionBehaviorTest` / `LearningProjectorTest` 6 处断言按新语义改钉 | ✅ |
| **零 bump 判定**（W3 批次 2 整批 + KF-11） | P9 基址统一 / P8 展示统一 / KF-20 读面 / KF-11 删 legacy | 逐位不变：`review_log.rating` 落库值、投影输出、schema 均未动 | 台账「批次 2 完成记录」「批次 3 完成记录」 | `KernelWave2SchemaContractTest`（SQL 值逐字）；`StoredRatingBasisTest` 配对契约 | ➖ |

### 3.6 批次 R · 读侧语义闭合（2026-10-01，裁决 28）：零投影 bump 的三串

| 版本串 | 变更 | 口径 | 数据来源 | 测试证据 | archive |
|---|---|---|---|---|---|
| `REVIEW_PLANNER` v7 → **v8**（V1 指纹 `canonical-v5 → v6`） | V1 `masteryRiskFor` 与 `scoreKnowledgeNode` 的跳过判据改 `ClearlyMasteredForSkipPolicy.effectiveStatus`（E 判据此刻成立才跳过；45 天窗并入出口；锚点 = `lastAttemptAt`，chat"评论钟"不再单独构成新鲜） | 跳过集合变化 → V1 计划输出变化，旧计划按读时校验重排 | 裁决 28（台账 :2329-2348） | `KnowledgeReviewPlannerTest` 7 例（同源跳过/双钟回归/KF-16 压制）；`KnowledgeReviewQueueTest` 15 例 | ➖（计划层） |
| `ReviewPlannerV2.VERSION` v3 → **v4**（指纹 `canonical-v8 → v9`） | V2 掌握风险分支改 `effectiveStatus`（CONFLICTED/UNKNOWN/STALE 三支与展示面同出口；先修稳定度接线做 KF-16 压制） | 风险值与理由集变化 | 同上 | `ReviewPlannerV2Test` 17 例（含双钟回归；KF-08 四例保持绿） | ➖ |
| `SELECTOR` v6 → **v7** | `requiresCalibration` 的状态三项 + `hasFreshEvidence` 45 天窗改 `effectiveStatus` 现算 | 决策输出可能变化 | 同上 | `AdaptiveQuestionSelectorTest` 17 例 | ➖ |
| **零 bump 判定** | 展示面重判（`toProfileOverview` strengths/weaknesses/newlyMasteredCount、`conservativeMasteryStatus`、summary `status`）+ KF-16 读侧接线 + `ForgettingCurve.decay` 公开读值 | 不落库、不重放：展示面读时现算；`PROJECTOR`/`EVIDENCE`/`SKIP_POLICY`/`FORGETTING_CURVE`/`STUDY_DATABASE_VERSION(56)` 均不动 | 裁决 28 第①③门；README 式说明见台账 | `MasteryEffectiveStatusTest` 9 例；`ReadSideMasteryClosureTest` 4 例；`RoomBackedStudyExperienceRepositoryTest` 端到端（已忘/先修压制掉出 strengths） | ➖ |

### 3.7 Wave 4 · W4-1 排程放大（2026-10-02）：零 bump 判定

| 版本串 | 变更 | 口径 | 数据来源 | 测试证据 | archive |
|---|---|---|---|---|---|
| `ReviewPlannerV2.VERSION` / `PLAN_FINGERPRINT_SCHEMA_VERSION` | **未 bump**（维持 `review-planner-v4` / `review-plan-canonical-v9`） | S12–S16 性能重构：beam 备选池池级预计算 + 子状态延迟物化 + 步数按预算收敛、localSwap 增量维护、`computeConfusablePartners` 共享先修倒排、`scoreCandidate` observations 单遍扫描（并入 `MasterySmoothing.evaluate`）、`StudyReviewPlannerService` 组装批量化（考试表一次读 / review_log 样本一次读共用 / 新题难度档批量读）。**选卡序列、每项 reasons、分值 hex、计划指纹逐位不变** | roadmap W4-1；审计 `2026-09-28-kernel-scale-precision-audit.md` S12–S16 | `ReviewPlannerScaleBenchmarkTest`：3 组固定金样（约 60/500/5000 候选，**改前捕获**，含分值 hex）逐位一致；5000 候选 best-of-5 改前 1054ms → 改后 168ms（完整套件并发时 631ms，门 <1000ms）。A/B 探针（HEAD 旧实现 vs 改后，33 组夹具含单 KC 池/双半池/空 KC/并列/极端预算/3000 池）`planFingerprint` + 选卡序列逐字符一致（diff 空）。既有 `ReviewPlannerV2Test` 17 例、`:core:domain` 538 例全绿；`:core:data` 577 例仅 5 条 KB 金标既有红 | ➖（计划层，无投影归档） |
| **本波未落（登记项）** | ① 候选池预筛（audit「一刀切」）：**未加** —— 硬约束要求预筛谓词是 `scoreCandidate` 非空集的可靠超集，而 null 判据（到期/早复习放行）本身就是打分主体，低成本安全上界保留近乎全池、无收益；改由 S12–S16 达标（5000 候选 168ms）。② KF-17 错峰：**`kf17=deferred-to-projection`** —— 排程侧只读投影 `nextReviewAtEpochMillis` 并抄进计划队列（`ReviewPlannerV2.kt` dueAt 赋值），队列副本无任何调度消费方（仅落库/回读），改它不会改变真实到期日；唯一干净落点是投影侧到期日计算（`MemoryUpdateModel`/`ForgettingCurve` → `LearningProjector`），并入 W4-2 投影批同一次 bump | W4-1 实施期源码勘定 | — | — |

### 3.8 Wave 4 · W4-2 投影热路径（2026-10-02）：一次投影 bump 覆盖两处输出变化 + 四个零输出重构

| 版本串 | 变更 | 公式/口径 | 数据来源 | 测试证据 | archive |
|---|---|---|---|---|---|
| `PROJECTOR` v10 → **v11**（复合串 `learning-core-v10 → v11`）；`EVIDENCE` v4 → **v5**；`ATTRIBUTION` v2 → **v3**；`SKIP_POLICY` / `FORGETTING_CURVE` 不动 | ①**裁决 26**：独立 `AnswerRevealOutcome` 事件新增知识点归因——除题卡（裁决 1 的 AGAIN）外，也把该题 **DIRECT** 绑定的知识点记忆卡按一次遗忘失败 lapse（`nextMemoryState(..., rating = AGAIN)`，只动卡字段：稳定度/难度/上次作答与学习日 + 状态检查点；s/f、观察列表、`lastEvidence*`、冲突态不动）。**没有卡就不建卡**（卡由作答流创建）。与题卡共用同一道呈现态门：一次呈现最多贡献一次 reveal 记忆更新；其后的作答仍走既有因果（答对 → NONE/w=0 不计分；答错 → `INCORRECT_AFTER_REVEAL` 再记一次），两条路径不重复计罚。②**KF-17（裁决 8）**：投影侧到期日错峰——同批（一次 project/replay 调用内）、同 `sourceBundleId`、同学习日的**新建卡**按事件序 +0/+1/+2… 天加在 `nextReviewAtEpochMillis` 上；不动 FSRS 参数，单题（组内唯一）恒 +0，已有卡的复习不重复错峰。W4-1 的 `kf17=deferred-to-projection` 在此落用 | 两处都改变投影输出（KC 卡数值 / 到期日）；存量投影不带新口径 → `projection_archive` 先归档再全量重放 | 台账裁决 26（`:2271-2288`）、裁决 8（`:1657-1680`）；fix-plan KF-17；W4-1 勘定（§3.7 登记项） | `MasteryMemoryCardTest`（reveal lapse KC 卡/答错再 lapse 不重复计罚/答对不计分；无卡不建卡）；`LearningProjectorTest`（同批 3 题三日错峰、单题不受影响、跨学习日不错峰、增量与全量重放逐位一致）；`:core:domain` 542 例 0 失败 | ✅ 走 W0-1 归档 → 重放路径（`ProjectionArchiveDrainerTest` 继续钉"先归档再覆盖"；`ProjectionDrainerDispatcherTest` 钉 drain 侧调度与快照复用） |
| **零 bump 判定（S1/S3/S4/S7/S10）** | S1 `commitProjection` 每批全删全插 7 张投影表 → 变更行 `@Upsert` + 消失行按主键差集删除 + 观察表增量 append；S3 `projectMastery` 观察列表全拷贝 + 3–5 遍扫描 → 无新增时共享原列表、单遍聚合；S4 drain 批内快照复用（一次调用读一次）；S7 `ModelTaskEntity.toSnapshot` 读到即重算 → 懒校验 + 显式 `verifyIntegrity` 入口（写入/迁移/测试用）；S10 drainer 显式切 `Dispatchers.Default`/`IO` | **不变的是输出**：S1 的差集=清空重写的结果（行内容完整相等比较）、S3 逐位等价（比较/累加顺序不变）、S4 只少读不换值、S7 只改校验时机、S10 只改执行上下文——`PROJECTOR`/`EVIDENCE`/`ATTRIBUTION` 的变化全部来自上两行，与这五个重构无关 | W4-2 必做点 1–5；S1 先例 = 仓内 `RoomKnowledgeContentReconciler`（@Upsert 而非 REPLACE） | `:core:database` 104 例 0 失败（含新 `ModelTaskIntegrityTest`：篡改仍被 `verifyIntegrity` 捕获、读路径懒校验边界、failure 列两路皆拒）；`:core:data` 579 例 5 失败（恰为 5 条 KB 金标既有红，无第 6 条）；仪器化（真 Room/SQLite）：`StudyDatabaseInstrumentedTest` **29/29**——其中新增 `shrinkingProjectionCommitDeletesVanishedRowsAndKeepsUnchangedRowsBitIdentical` 覆盖 S1 五个删除分支（题卡/掌握态/观察行/已应用作答/已应用修正）＋未变行逐位不变＋变更行重写，`ModelTaskDatabaseInstrumentedTest` 9/9（S7 读写路径）；`FullMigrationMatrixInstrumentedTest` **未跑**（非本批门） | ➖（重构本身零输出） |

### 3.9 Wave 4 · W4-3 重放路径批量读（2026-10-02）：零 bump 判定

| 版本串 | 变更 | 公式/口径 | 数据来源 | 测试证据 | archive |
|---|---|---|---|---|---|
| `PROJECTOR` / `EVIDENCE` / `ATTRIBUTION` / `SKIP_POLICY` / `FORGETTING_CURVE` / `REVIEW_PLANNER` / `ReviewPlannerV2.VERSION`+`PLAN_FINGERPRINT_SCHEMA_VERSION` / `SELECTOR` | **全部未 bump**（维持 W4-2 的 `learning-core-v11` 复合串等） | ①**S5**：`loadLearningLedger` 从"单事务逐行 2-3 次查询 + 每行 SHA-256"改为**事务外分块 + 每块每表一次 IN 批读**——块大小 900，每块 8 条查询（outbox 1 + 五类载荷 5 + 证据快照 1 + 归因 1），块循环在事务外、每块一个短事务；**分配头 `last_allocated_sequence` 与块行读自同一事务快照**（复核阻断项修复：第一版把尾部头读放事务外单读，并发追写——chat 证据写入不与 study 仓库共享互斥——在"最后一块读之后、尾部头读之前"提交时会得到 `allocated > 前缀末` 而该行其实存在，误报假 GAP 并让 drainer 抛完整性异常；同快照内读消除该中间态，与旧实现"整本行+分配头同一事务"判定一致，真洞仍判 GAP）；行身份三连与载荷指纹校验函数与单行读共用，首个坏行的 GAP/CONFLICT 判定逐字保留。②**S8**：事件指纹不再在重放管道里每事件重算 3-4 次——读边界（`loadLearningLedger`/`loadProjectionBatch`）已验的规范指纹经 `canonicalFingerprints` 传入 `LearningProjector.replay`（缺项/未给时本 pass 内按 `ledgerEventId` 记忆化现算一次）；`commitProjection` 的回执校验与 applied 窗口校验在同一事务内共用一次解析结论（窗口 ≤4096/类），第二次不再取数 + 重算 | **不变的是输出**：S5 只改读法与事务边界——outbox 只增不改，块读仍是同一有效前缀，行内容/指纹/判定逐位不变；S8 复用值就是读边界逐位校验过的 `LearningLedgerFingerprint.event`，写进 applied 记录的值与重算逐位相同（说错话在提交侧仍被 CAS 拒）。投影输出、指纹值、计划输出均未动 | 审计 `2026-09-28-kernel-scale-precision-audit.md` S5/S8；roadmap 3B「ledger 联表批量读；SHA-256 只在不信任边界重算」；W4-3 独立复核阻断项（S5 并发追写假 GAP） | `LearningProjectorReplayFingerprintTest`：混合账本 + **5 万事件夹具**下 `replay(读边界指纹) == replay(重算)`（整个 `LearningProjectionResult` 数据类相等，含四类 applied 记录指纹；夹具覆盖五类事件与修正）；单次（无预热）50k 重放：重算 1076ms → 复用 176ms，孤立 50k 指纹重算 347ms。`ReplayFingerprintDrainerTest` 2 例（drainer 全量重放快照 == 投影器重算逐位一致；读边界值原样转发而非重算）。`LearningLedgerChunkReadTest` 4 例（DAO 读路径 JVM 假件）：最后一块之后提交的追写不再假 GAP、块间追写由下一块读到、真洞仍判 GAP（blockedAt 指首个缺失序号）、载荷指纹不符仍判 CONFLICT；**A/B 定性**：把尾部头读临时放回事务外单读时"最后一块之后的追写"用例当场失败（假 GAP 复现），修复后 4/4 绿。仪器化（真 Room/SQLite）：`StudyDatabaseInstrumentedTest` **30/30**（新增 910 条跨 900 块边界用例：序列/顺序/指纹逐位一致；910 条 2 块读 1154ms，改前口径不在二进制内故只有单侧值）、`ChatEvidenceLedgerIntegrationTest` 4/4、`TutorJudgedReviewSettleInstrumentedTest` 2/2。JVM：`:core:domain` 546 / `:core:data` 581 / `:core:database` 108 例，0 失败（工作树含他线在飞的 dense/KB 夹具改动；W4-2 登记的 5 条 KB 金标既有红在本轮实测为绿，本批未触碰这些文件） | ➖（读路径重构，无投影输出变化） |

### 3.10 Wave 4 · W4-4 KB 索引（2026-10-02）：零 bump 判定（S19 诊断判「不加索引」+ S21 安装期预热）

| 版本串 | 变更 | 公式/口径 | 数据来源 | 测试证据 | archive |
|---|---|---|---|---|---|
| `STUDY_DATABASE_VERSION` / 全部算法串 | **未 bump**（维持 56 与 `learning-core-v11` 复合串等） | ①**S19 诊断判「不加索引」**：桌面 SQLite 3.50.4 按 56.json 逐字建表、3.6 万节点 / 86.4 万特征行复现——召回查询**已在用复合 PK 的内部覆盖索引**（`SEARCH feature USING COVERING INDEX sqlite_autoindex_knowledge_search_feature_1 (subject=? AND search_feature=?)`；官方优化器文档：`IN` 为索引可用谓词、等值+IN 可作复合索引前缀、内部索引是真实持久索引）。显式加 `(subject, search_feature)` 反而因失去 COVERING 属性**变慢 4.7×**（p50 6.1ms→28.5ms），且正撞官方「不要让一个索引是另一个的前缀」；ANALYZE 前后计划/耗时无差；`countIndexed` 的 DISTINCT 子查询替代式 535.4ms（差 54×）。**结论：不加索引、不引 ANALYZE、不改召回 SQL**；每次召回的固定读数开销（自愈两计数 ≈12.6ms 桌面档）登记为后续项。②**S21 安装期预热**：整科重建从「首访问自愈」挪到**内容安装期**——`RoomKnowledgeContentReconciler.applyKnowledgeContentUpdate` 对本轮节点有增改的科目调用新 `KnowledgeSearchIndexBuilder.rebuildSubject`，锚点随安装写好（锚点最后写的崩溃安全纪律不变）；读时自愈保留为抽取规则换版/锚点缺失的兜底并**改委托同一构建器**（消灭两处重建实现）。③**批量 IN 删除分块**（附带修复，同一安装链）：四条 `… IN (…)` 删除（材料绑定/来源绑定/前置边/搜索特征）按 900 分块——消灭的失败 = 包规模越过 SQLite 绑定变量上限（旧平台 999；实测 5 万材料包在 API 34 上越过 32766）时整包安装抛 `SQLITE_ERROR: too many SQL variables`、首装横幅「本地知识包尚未准备好」常驻、检索端到端用例同点崩。 | **存储行内容不变**：同一 extractor、同一行集合，只是写入时机从「首查」提前到「安装」；命中语义/投影输出/计划输出均未动。16.6s 长尾的最可疑来源（首访问整科重建）被移除，真机因果若仍见长尾按证据如实记录 | 审计 S19/S21（`2026-09-28-kernel-scale-precision-audit.md:102,104`）；诊断与原始输出 `docs/research/2026-10-02-s19-retrieval-diagnosis.md`；sqlite.org 优化器文档（IN 谓词/内部索引/前缀规则）；分块修复失败证据 = `KnowledgeContextRetrievalInstrumentedTest.bundledSubjectsRecall…` 的 `SQLITE_ERROR` 原始栈 + 首装横幅截图 | `KnowledgeSearchIndexInstallInstrumentedTest` 2/2（安装完成、**任何召回之前**锚点已在位且两节点特征已建；改名 upsert 后重建且锚点保持当前）；`KnowledgeContentReconciliationInstrumentedTest` **7/7**（含新增 `bulkDeletesChunkPastTheSqliteVariableCapOnLargePacks`：1,200 节点+1,200 材料整包落地、重跑幂等）；JVM 全量 **2127 例 0 失败**；`core:database` 仪器化全套（ciSlowRunner=1，CI 同形）**188/0**（冷启健康模拟器 5m15s；污染态模拟器上矩阵单测曾出现 41 分钟病理性时长 + 系统崩溃空 `<failure>`，判环境档）；core:data 检索仪器化 **2/2**（含真实 5 万材料包 bundled 召回与 20k 点 p95 门）；app 三屏仪器化 5/5；R8 冒烟净（构建 1m58s、装机/启动/logcat 零错、首装横幅随分块修复消失） | ➖（无 schema 变更；行内容逐位不变） |

### 3.11 阶段 3B 步骤一 · D-M M5 + M1（2026-10-02，批次 B1/B2）：schema 57→58 + 零算法 bump 判定

| 版本串 | 变更 | 公式/口径 | 数据来源 | 测试证据 | archive |
|---|---|---|---|---|---|
| `STUDY_DATABASE_VERSION` 56 → **57** | ①删 `projection_consumption` 整表——写-only 死面：全仓**零 SELECT**（幂等由 `projection_outbox` 状态 + 快照 CAS 保证，回执行没有读者）；②删 `learner_problem_memory_state` 的 `last_reviewed_epoch_day` / `last_attempt_id` 两列——写后无读者（跨日 delta_t 由 `lastReviewedAtEpochMillis` + 事件 UTC 偏移现算；"上次作答 id"无消费方）。**表本身是活体保留**（`library_catalog` 视图 / `ProblemDao` / `readCurrentSnapshot` 都在读）。删列走表重建（建新表 → INSERT SELECT 逐列搬运 → DROP → RENAME → 重建两索引），**非破坏**、不回填；历史迁移 SQL 一字不动 | **零算法 bump**：纯删死面，**同一账本重放，存活字段逐位不变**。证明：①两字段/表在 main 代码**零读取**（全仓 grep：字段仅剩历史注释、表零 SELECT），消费方读不到差异；②投影器改动只是删掉两处对已删除字段的赋值（`LearningProjector` 曝光/记忆路径），其余赋值逐字未动，`projectMemory` 顺带删掉只为该列存在的 `eventId` 形参；③`core:domain` 全部重放/投影等价用例与改前同一断言集全绿（见右） | 计划 `docs/research/2026-10-02-stage3b-plan.md` §3 步骤一 + 附录 A·M5；死面确认（零 SELECT / 写后无读者） | **JVM**：`:core:domain` **546/0**（纯 JVM 模块，`test` 任务；含 `LearningProjectorTest` 全量重放/增量等价、`ReviewPlannerV2Test` 等）；`:core:data` **581/0**（W4-2/W4-3 登记的 5 条 KB 金标既有红**本轮实测为绿**）；`:core:database` **114/0**（含新 `KernelWave4SchemaContractTest` 5 例：只少一表两列/其余表 DDL 与索引逐字不变/重建 DDL 与 57.json 同形且逐列搬运/无回填不碰死列/删表一句 DROP；新 `RoomStudyDatabaseMappingsTest` 记忆行实体↔模型逐字段回环）。**仪器化（真 Room/SQLite，`ciSlowRunner=1`）**：`core:database` 全套 **189/0**（0 skipped；共享 AVD `test_device` 未冷启、uptime ≈2h20m；总时长 **7m02s**，其中矩阵 1→57 **186.98s**——未出现污染态 41 分钟病理性时长；新 `m5DropKeepsMemoryRowsAndRemovesConsumptionTable` 4.07s：真库 v56 建库+带数据迁移后两死列/整表消失、旧行值逐位保留、`readDatabaseVersion()==57`）。`PerformanceGateTest` 6 例本轮报告 ok，但按**环境档**点名：不声称绿、不记回归（本机共享 AVD） | ➖（无投影输出变化，不触发重放） |
| `PROJECTOR` / `EVIDENCE` / `ATTRIBUTION` / `SKIP_POLICY` / `FORGETTING_CURVE` / `REVIEW_PLANNER` / `ReviewPlannerV2.VERSION`+`PLAN_FINGERPRINT_SCHEMA_VERSION` / `SELECTOR` | **全部未 bump**（维持 W4-2 的 `learning-core-v11` 复合串等） | 同上：删的是只写不读的列/表，投影输出、计划输出、指纹、存储行（除被删列外）均未动 | 同上 | 同上；`PerformanceGateTest`（首屏/并发搜索）按**环境档**点名：不声称绿、不记回归（本机共享 AVD） | ➖ |
| `STUDY_DATABASE_VERSION` 57 → **58** | **D-M M1 fixture 系统退场**：①删两张 legacy fixture 投影表 `problem_memory_state` / `knowledge_mastery_state`（fixture 专表，main 源集除已删的 `FixtureSeedDao` 外零 SELECT；真实投影在 `learner_*` 表，一行不碰）；②删 fixture 播种系统——`seedFixture` 端口与实现、`FixtureSeedDao`、`StudySeedBundle.problemMemoryStates/knowledgeMasteryStates`、`StudyFixtureSource`/`EmptyStudyFixtureSource`/`StudyFixtureRegistry`、debug 源集 `M1CuratedFixtureSource`/`M1FixtureInitProvider`（含 AndroidManifest init provider）；③**提交/揭示路径去 fixture 目录门**：`StudyWriteContext.requireTeachingArtifact`（release 里对任何 practice unit 抛 `outside the verified M1 catalog`）删除，改由新 `StudyPracticeUnitFacts` 从 practice unit 记录 + 当前绑定 + 题目字段派生（无绑定走 pseudo 桶）；④21 个测试文件的 `seedFixture` 引用面改「直写 DAO（core:database）/ 库文件直写（core:data/app）/ 真实写路径（review plan/session 走 `ReviewDao.savePlan/saveSession`）」，`M1CuratedStudySeedTest` 删除、curated 内容以**测试源集**夹具留存（三份：core:data JVM、core:data androidTest、app androidTest；记录于各文件 KDoc）。迁移 `KERNEL_WAVE5_MIGRATION_57_58` = 两句 `DROP TABLE`，不搬运不回填；历史迁移 SQL 一字不动；58.json 构建生成 | **零算法 bump**：①`core/domain` 与 `core/model` **零改动**（`git diff --name-only` 两路径为空）——投影器/计划器/打分公式一字未动；②全部算法版本串与指纹 schema 未动（本行右列同一结论）；③投影/计划输出**在同一账本 + 同一库内事实上逐位不变**——变化只发生在"fixture 提供的元数据"这一来源上：release 构建从未注册过 fixture（`EmptyStudyFixtureSource` 恒空），故生产路径的投影/计划输出在改前改后同为空目录行为；fixture 播种的测试态按计划改由测试夹具直写等价的库内事实。**新派生口径**：证据快照 id 内容寻址（题+revision+taxonomy+归属集）、`capturedAt` 取 revision 创建时刻（同 id 重写逐位 no-op）；itemFamilyId = `saved-question:<unit>`、sourceBundleId = null（practice unit 记录无来源包列）；归属权重按绑定数均分、DIRECT 总和守恒；无绑定物化 `pseudo:<SUBJECT>`（taxonomy `pseudo-evidence-v1`） | 计划 `docs/research/2026-10-02-stage3b-plan.md` §3 步骤一第 2/3/4 条 + 附录 A·M1；台账 D-M M1；fixture 专表死面确认（`ProblemDao.kt:198-203` 注释 + 全仓 grep） | **JVM**：`:core:domain` **546/0**（`test`，`--rerun-tasks` 强制重跑）；`:core:database` **118/0**（含新 `KernelWave5SchemaContractTest` 4 例：只少两表/其余表 DDL 与索引逐字不变/迁移恰为两句 DROP 不搬运不回填/现役视图不引用被删表）；`:core:data` **574/0**。**仪器化（真 Room/SQLite，冷启 `-wipe-data` 模拟器）**：`:core:database` 全套 **188/0**（0 skipped；含新 `m1DropRemovesLegacyFixtureProjectionTablesAndKeepsRealRows`：真库 v57 建库+带数据迁移后两表消失、真实 practice_unit 行逐位保留、`readDatabaseVersion()==58`；矩阵 1→58 **105.5s**）；`:core:data` 5 个迁移面用例类 **40/0/0**（含新 `StudySubmissionWithoutFixtureInstrumentedTest` **2/2**：无 fixture 源的构建完成「录入→提交→揭示」全链 + 无绑定落 pseudo 桶；BackupRestore 20/0、MistakeOrganization 7/0、RoomModelTaskToolLoop 9/0、TutorJudgedReviewSettle 2/0）；`:app:connectedLocalFirstDebugAndroidTest` **三屏门 5/0/0**（LearningMastery 1 + SchedulingSettings 2 + SourceCalibration 2，class 过滤）；fixture 迁移面 root 类另跑 **16/0/0**（RootExperience 10——含此前依赖 curated 队列的 `submittedReviewItemIsAlreadyAdvancedWhenUserLeavesBeforeNext`、RootVisualCapture 1、RootTutorFailClosed 5）。**R8 冒烟（localFirst release，debug keystore 口径）**：`assembleLocalFirstRelease` **3m05s** 成功；装机/启动（pidof=8764）/logcat 零 FATAL 与反射缺失（grep `FATAL EXCEPTION|AndroidRuntime|ClassNotFoundException|NoSuchMethodError|NoClassDefFoundError` 空）；首屏截图 = 复习根空态（「暂无学习记录/暂无待复习题」——无 fixture 的 release 如实行为） | ➖（无投影输出变化，不触发重放） |

### 3.12 阶段 3B 步骤二 · D-M M4 + M6 + 选择器先修接线（2026-10-02，批次 B3）：零 bump 判定

> 编号说明：§3.12 由本批（步骤二）占用；计划预留的 KF-32 步骤三记录顺延为 **§3.13**。

| 版本串 | 变更 | 公式/口径 | 数据来源 | 测试证据 | archive |
|---|---|---|---|---|---|
| `ReviewPlannerV2.VERSION`（`review-planner-v4`）+ `PLAN_FINGERPRINT_SCHEMA_VERSION`（`canonical-v9`）/ `SELECTOR` / `PROJECTOR` / `EVIDENCE` / `ATTRIBUTION` / `SKIP_POLICY` / `FORGETTING_CURVE` / `LEDGER` | **全部未 bump**（复合串维持 `learning-core-v11`；`REVIEW_PLANNER`/`REVIEW_COMPOSITE` 已删） | ①**M4 写通道并一**（`KnowledgeEvidenceWriter`）：讲题通道逐字段不变（锚定/三路配额/D9 半权/幂等 id 同规则）；测验通道 accepted 行的 weight/direction/时间戳/幂等 id 逐位不变（`anchor_class=CONFIRMED` → 全权重，D9 不减半），新增 rejected 观察行（`rejected_reason` 非空，**不进投影**）与 `anchor_class` 审计列；置信 1.0 由裸字面量改具名常量 `OBJECTIVE_ANSWER_CONFIDENCE`（值不变）。②**M6 V1 退场**：V1 `ReviewPlanner` 类/`plan()`/版本串/回滚开关生产不可达（默认 V2、无翻闸点）→ 删；`scoreKnowledgeNode`/`masteryRiskFor`/难度档/`DIFFICULTY_CYCLE` 原样搬进 `KnowledgeNodeScorer`（纯移动，公式一字未改）；队列三常量（0.3/0.2/1.5）改引用 `AlgorithmConstants.ReviewScoring`（同值，塌掉第三份副本）。③**选择器先修接线**：`AdaptiveSelectionRequest` 新增 `prerequisiteStabilityDaysByNode`（默认空 = 无已知先修）；跳过判据 `isSatisfied` 增传该表——只有显式传入才改变决策。 | 计划 `docs/research/2026-10-02-stage3b-plan.md` §3 步骤二 + 附录 A·M4/M6；台账 D-M M4/M6；裁决 28 登记项（选择器先修接线） | **定格测试（零 bump 的证明载体）**：`RoomBackedStudyExperienceRepositoryTest.the default plan is stamped by V2 and never carries the retired V1 version string`——默认路径写出的计划行 `plannerVersion == ReviewPlannerV2.VERSION`、不含 `learning-core-` 前缀、不含 `review-planner-v8`。**全量 JVM（`--rerun-tasks`，2026-10-02）**：`:core:domain` **545/0**、`:core:data` **581/0**、`:core:database` **118/0**（`BUILD SUCCESSFUL`）。**定向**：`RoomTutorToolRunnerTest` 44/0（讲题通道全链含 D9 半权/rejected 观察行/结果回显）；`KnowledgeQuizFeedbackWriteTest` 5/0（测验被拒观察行新断言：weight 0 + `KNOWLEDGE_NODE_NOT_ANCHORED` + anchor_class）；`MasteryUpdateEvidenceIdTest` 6/0（单源 id 规则：讲题/测验形状、通道隔离、空命名空间唯一回退）；`AdaptiveQuestionSelectorTest` 17→19（弱先修 5 天 → 不再 SKIP_MASTERED_FOUNDATION 改出题；先修恢复 → 回到跳过）。**仪器化三屏门**：`:app:connectedLocalFirstDebugAndroidTest` 三屏类（LearningMastery/SchedulingSettings/SourceCalibration，class 过滤）**5/0/0**（`TEST-…_app-localFirst.xml`：tests=5 failures=0 errors=0 skipped=0；共享 AVD 未冷启——功能门，不作性能观测） | ➖（无投影/计划输出变化，不触发重放） |
| `STUDY_DATABASE_VERSION` | **不动（58）** | 本批无 schema/迁移面改动（M4 复用既有 `anchor_class` 列与 rejected 列；M6 纯代码收敛） | 同上 | 同上（`:core:database` 118/0 含迁移矩阵 JVM 面） | ➖ |

**V1 独有断言逐条核对（`ReviewPlannerTest` 13 例，不静默丢）**：转移 10 例入 `ReviewPlannerV2Test`（17→27）——过期风险+弱点理由、重复错题优先且改指纹、更老未排候选胜出、缺失/冲突知识点风险、时钟回拨、过期校准保守、stale 快照拒排、规划时刻早于快照/修正水位两道 request 不变量、5000 积压逐日轮换；**3 例随 V1 语义退场并记录**：①"同族/同源硬排除"（V2 的设计是动态软罚 + 同族不连续硬约束；覆盖见 `same item family never appears in consecutive positions`）；②"平局难度循环"（V1 错题排程独有；循环现由知识点队列承载，覆盖见 `KnowledgeReviewQueueTest.cyclesDifficultyBandsWhenScoresTie`）；③"计划身份"（由 `same input and same model state replay to the same fingerprint` + `BlockingLearningCoreReviewTest.review queue identity changes across learner and local day` 覆盖）。上述三例的行为差异只存在于**生产不可达**的 V1 路径。

**选择器先修接线 · 调用点实况（如实登记）**：全仓核实 `AdaptiveQuestionSelector` **无生产构造点**（唯一构造在 `AdaptiveQuestionSelectorTest`；审计 R-06/2026-09-12 已记"生产无构造"）。因此计划中"仓库调用点填（既有 reader）"在当前代码里**没有可填的调用点**——本批落的是：字段 + 跳过路径消费 + 效果用例；字段默认空表 = 既有调用行为不变。将来接线时由既有 `KnowledgePrerequisiteReader` 解析该表填入（与知识点队列同源）。

### 3.13 阶段 3B 步骤三 · KF-32 改绑→重放历史（2026-10-02，批次 B4）：一次 bump 覆盖三串

| 版本串 | 变更 | 公式/口径 | 数据来源 | 测试证据 | archive |
|---|---|---|---|---|---|
| `PROJECTOR` v11 → **v12**（复合串 `learning-core-v11 → v12`）；`ATTRIBUTION` v3 → **v4**；`LEDGER` v2 → **v3**；`EVIDENCE` / `SKIP_POLICY` / `FORGETTING_CURVE` / `SELECTOR` 不动 | ①**新补偿事件 `BINDING_CHANGED`**（仅全量重放消费，与 `ATTEMPT_CORRECTION` 同类）：`RoomProblemOrganizationStore.confirm`（自动接受 + 离线纠正共用）在**绑定集合真的变化**时于同一事务追加事件 + outbox（载荷行 `binding_change_event`，schema 58→59 非破坏新增）；`loadProjectionBatch` 遇该 kind 即 `FULL_REPLAY_REQUIRED`；增量提交许可表**不含**它。**顺带核实 `CHAT_EVIDENCE_SUBMITTED` 契约缺失**（见下行）。②**重放期归因重派生（upcasting）**：`LearningProjector.replay` 对 attempt/reveal 的知识归因不再读 `assessmentSnapshot.attributions`，改按 `practiceUnitId → **当前**绑定集合` 现派生（单源 `derivePracticeUnitBindingAttributions`，与写入期 `StudyPracticeUnitFacts` 同规则：taxonomy 取字典序最小、权重按数量均分、按 bindingId 排序 PRIMARY/SECONDARY、DIRECT；`basisRevisionId` 用该 attempt 快照自己的 revision，历史归属锚点保留）。旧 bindingId/旧节点复用既有 `KnowledgeNodeSuccessors` 链。**增量 `project()` 保持写时快照**（传默认空映射即行为逐位不变）；重放里无当前绑定的题也退回写时快照（不整块丢证据）。**"当前"的口径（B4 修正）**：= **最近一次确认那一批**（`accepted_at_epoch_millis == MAX(problem_organization_receipt.accepted_at)`；该题从未确认过则全部）——**不是表里的全部行**：`deleteUnreferencedKnowledgeBindings` 会保留被证据归因引用的旧绑定（`assessment_evidence_attribution` 对绑定表 RESTRICT，物删违反外键；归因行不可改写），把它算进来历史证据会同时挂新旧两节点、旧节点永远清不掉，增量证据也继续喂旧节点。规则单源在 `ProblemOrganizationDao.readCurrentKnowledgeBindingsForPracticeUnit`（与 `observeCurrentKnowledgeNodeIds` 的"当前目录"同口径，只差"不要求 KNOWLEDGE 分类"——命令可只绑 grounded 原子节点）；写时快照与重放共用同一读口 `readCurrentPracticeUnitKnowledgeBindings`（默认退回全量读，真库覆写）。配套：confirm 在回执落库后、对**命令携带的** binding id 执行 `touchKnowledgeBindings` 刷新 `accepted_at`（绑定 id 内容寻址不含时间，重确认时 IGNORE 会让旧行留在旧时间而掉出当前集合；被保留的审计旧绑定不在其中，其旧时间正是"非当前"的依据）。③**触发**：改绑事件 + 本版本 bump（存量库没有绑定事件行，靠 `previous.checkpoint.projectorVersion != VERSION` 走同一条 `commitFullReplay`），archive 先归档（W0-1 既有机制）。④**旧节点归零**：重放从空表累加 + `applyProjectionTables` 差集删除（`ProjectionTransactionDao:1073-1085`），**不新增清零路径** | 重放输出变化（同一账本在改绑后得到不同掌握度归属）→ 必须 bump + 全量重放；两处外部依据：Azure《Event Sourcing Pattern》「never update the event data… add a compensating event」+「read-time upcasting」；Marten《Events Versioning》「explicit event type instead」「upcasting on the fly」 | 计划 `docs/research/2026-10-02-stage3b-plan.md` §3 步骤三第 1 条 + 附录 A·KF-32；fix-plan KF-32（`:401-414`）；台账 D-M M3/裁决 22 修订二；外部资料见计划 §2.1/§2.2；"当前绑定集合"口径与保留语义的实测依据见 B4 交付记录（`deleteUnreferencedKnowledgeBindings` 的 RESTRICT 保留 + `sameAcceptedFact` 不允许命令复活退役节点） | `LearningProjectorReplayFingerprintTest`（新增"重放按当前绑定重派生"直接用例：改绑后历史证据落新节点、旧节点归零、重放幂等；合并×改绑组合；缺项退回写时快照）；`BindingChangedReplayDrainerTest`（drainer 全管道读**当前**绑定读口、无改绑不重放）；`LearningLedgerChunkReadTest` 假 DAO 补新 kind 分支（读回 + 篡改判 CONFLICT）；`KernelWave0SchemaContractTest`（chat 回执放行 / `BINDING_CHANGED` 仅重放放行）；仪器化（真机 emulator-5554）：`ProblemOrganizationDatabaseInstrumentedTest` **15/0/0**（含新 `reconfirmingTheSameBindingIdentityRefreshesItIntoTheCurrentSet`，A/B 实测停用 touch 时以 `expected:<4000> but was:<2000>` 失败）、`BindingChangedReplayDrillInstrumentedTest` **3/0/0**（5 连跑稳定；含"先合并再改绑→证据落 successor、旧节点归零"）、`KnowledgeContentUpdateDrillInstrumentedTest` **1/0/0**、`StudySubmissionWithoutFixtureInstrumentedTest` **2/0/0**；JVM `:core:domain` 548 / `:core:data` 583 / `:core:database` 121，0 失败 | ✅ 走 W0-1 归档 → 重放（`ProjectionArchiveDrainerTest` 继续钉"先归档再覆盖"） |
| **顺带核实：`CHAT_EVIDENCE_SUBMITTED` 契约缺失（历史遗漏，已修）** | `DatabaseContract.validateProjectionCommit` 的"已知 kind"白名单缺 `CHAT_EVIDENCE_SUBMITTED` | chat 证据自 v41 起就在账本里分配序列并落 outbox 行（`ChatEvidenceDao.insertAsLedgerEvents`，注释明说"both the incremental batch path and the full-replay path see the event"）；投影器两条路径都实现了它（增量 `projectChatEvidence`/重放同支），`loadProjectionBatch` 与 `readLedgerChunk` 都有解码分支，drainer 也把它映射成回执——唯独提交校验会把含该 kind 的回执判为 `Unknown ledger event kind`，即 chat 写入后的**下一批投影必被 CAS 拒**（`ProjectionCasConflictException`×4 → drain 抛错）。判为**历史遗漏**（另建提交通道的假设不成立：全仓无第二条账本提交通道），修法 = 白名单补 `CHAT_EVIDENCE_SUBMITTED`（增量许可表同步补，否则又成半截合同） | 全仓 grep：`CHAT_EVIDENCE_SUBMITTED` 的写入/解码/投影/回执映射四处齐全，仅校验白名单缺项；`ChatEvidenceDao` KDoc 与 `ChatEvidenceMigration` v40→41 回填注释均声明两条路径可见 | 计划 §3 步骤三第 1 条（"顺带核实 `CHAT_EVIDENCE_SUBMITTED` 在两表缺失的既有不一致"）；台账 §5 裁决 ⑩ | `KernelWave0SchemaContractTest`（提交校验：含 chat 回执的增量提交放行、含 `BINDING_CHANGED` 的增量提交仍拒）；`:core:database` 既有 118 例全绿 | ➖（修的是放行口，不改变任何投影输出） |

> **存量库缺口（v58 及以前，登记不修）**：v58 的 `confirm` 没有 touch——重确认同一份绑定身份时
> IGNORE 保留旧 `accepted_at`，于是"有回执、但没有任何行落在 `MAX(receipt.accepted_at)`"的单元在
> 新口径下读到空集（`readCurrentKnowledgeBindingsForPracticeUnit` 无回退分支）。升级到 v59 后、该题
> **下一次确认之前**：新写证据退到 pseudo 桶、重放重派生为空而退回写时快照——无数据损坏，下次确认即
> 自愈；迁移不回填（应用未发布，真实存量仅开发库）。§3.13 复核登记（2026-10-02 独立复核 P2）。
> **更新（K1 批 2，2026-10-09）**：本缺口下的重放行为已由 §3.19 取代——重放与写路径同规则改挂
> pseudo 桶，不再"退回写时快照"；本行其余文字为 v12 当时的历史登记，保持原样。

### 3.14 阶段 3B 步骤三 · M2/M7/插眼 8（2026-10-03，批次 B5）：零算法 bump

| 版本串 | 变更 | 公式/口径 | 数据来源 | 测试证据 | archive |
|---|---|---|---|---|---|
| `LearningCoreVersions` 全部串**未 bump**（复合串维持 `learning-core-v12`）；`ModelEgress` 提示词版本（**非算法台账号段**，自记）：`tutor-plan-v14-advisory-tools` / `tutor-respond-v21-advisory-tools` / `tutor-lobby-v12-advisory-tools` | ①**M2 提交期 HINT 接线**（非 UI）：`hintCount>0` → 一条 `PersistedAssessmentAssistance`(HINT)（合成序号=揭示序号+1、无揭示=1；`responseSequence` 水位=最大协助序号+1；揭示与 hint 并存时判定顺序不变、揭示支配）。两档死分支（`CORRECT_AFTER_HINT` / `INCORRECT_AFTER_HINT`）可达——"三档答对里只有独立答对是真的"的失败被消灭。**零 bump 证明（同 §3.4 尾注先例）**：分支可达性变化 = 提交期输入事实接入；证据行写时定价、账本已写入行不重算、投影/计划对固定账本逐位不变。②**M7 advisory 一等工具**：`ADVISORY_READ` / `ADVISORY_WRITE`（枚举 + 描述单源 `TutorToolDescriptions`（curate 语义）/ 协议两路由同名同义 / 任何轮次档位 / 每参数服务端作用域校验 / 三 kind 限枚举 / 稳定键 upsert / `payload≤600`；`MAX_TOOL_DECLARATIONS` 5→7）。零 bump：工具面只影响新请求的声明集与模型行为，不进账本/投影；提示词版本已 bump（egress 授权回执不再冒充同一提示词）。③**插眼 8**：会话代号表显式登记本科「未分类」桶（`UNCLASSIFIED_BUCKET` → `pseudo:<SUBJECT>`，复用既有 ensure 幂等创建；编造代号仍拒；桶不进召回面；证据落桶按 DISCLOSED 半权）。零 bump：只扩展会话披露面，`MODEL_CANDIDATE` 过滤未动。 | 计划 `docs/research/2026-10-02-stage3b-plan.md` §3 步骤三第 2/3 条；台账 D-M M2/M7；裁决 22 修订二（`:2146-2156`） | JVM（`--rerun-tasks`，2026-10-03）：`:core:domain` **550/0**、`:core:model` **394/0**、`:core:data` **604/0**、`:core:database` **121/0**、`:feature:tutor` **199/0**；仪器化：`PseudoUnclassifiedBucketInstrumentedTest` **1/0/0**、`RoomModelTaskToolLoopInstrumentedTest` 9 + `RoomModelTaskT6MasteryInstrumentedTest` 3 **12/0/0**；`app` 双 flavor 编译绿。定向：`MasteryEvidencePolicyTest` 六档各一例（新补 `INCORRECT_ON_RETRY`；六档 reason 全部钉死）、`RoomBackedStudyExperienceRepositoryTest` 三条端到端（有提示答对/答错、揭示支配 hint）、`ModelTaskFingerprintStabilityTest` 4 面（旧 5 工具名往返稳定（codec 往返，非 frozen 旧行——复核注）/ 7 工具面往返 / 咨询声明=另一逻辑操作 / 桶条目往返）、`RoomTutorToolRunnerTest` 含 `advisoryWriteLandsOnTheUnclassifiedBucketCode`（ADVISORY_WRITE 落桶直接用例） | ➖（无投影输出变化，不触发重放） |

> 复核整改（2026-10-03，B5 修复轮；独立复核 approve-with-notes、五项全处置）：①advisory 写行 PK 改 learner 前缀
> （`advisory_id = "<learner>:<source>:<kind>"`）——与唯一索引 `(learner_id, source_id, advisory_kind)` 同冲突面
> （原形跨 learner 的 REPLACE 会删他人行；单 learner 下不可达，属防患修法）；②`withSessionKnowledgeCodes` 的
> fail-open 不再吞 `CancellationException`；③PROBLEM 作用域"接受孤儿行"边界写入 KDoc（表无 FK，读者按
> practice unit 过滤）；④`MasteryEvidencePolicyTest` 六档 reason 全部钉死；⑤补 ADVISORY_WRITE 落桶用例。

> 复核登记（2026-10-03，非本批引入）：`toTutorToolRequestsOutput`（native tool_calls 路由）不解析
> `extendedResult`，MASTERY_READ 扩展预算实际只在 json_object 路由生效——既有缺陷，登记 KD-30。

### 3.15 阶段 3B 步骤三 · 回退工具化与演练（2026-10-03，批次 B6）：零算法 bump

| 版本串 | 变更 | 公式/口径 | 数据来源 | 测试证据 | archive |
|---|---|---|---|---|---|
| `LearningCoreVersions` 全部串**未 bump**（复合串维持 `learning-core-v12`）；零 schema/迁移/版本串变动 | ①**archive 读回**：`PersistedProjectionArchive` + DAO/端口 `readLatestArchivedProjection`（`archived_at DESC, archive_id DESC`）；②**专用恢复路径** `restoreArchivedProjection`（runbook §4 的工具版）：**8 道判定全在写之前**——形状 / `NO_ARCHIVE` / 版本列 / `MALFORMED_ARCHIVE`（坏载荷）/ 载荷版本 / `RESTORED_AT_IN_PAST`（恢复时刻单调，防"连续回退原地打转"）/ `CHECKPOINT_AHEAD`（防篡改归档令增量静默跳过事件）/ `PRESENTATION_AHEAD`（恢复目标早于现存呈现终局——增量排空把未回滚呈现行读成权威，显式拒绝而非楔死；无历史版本可精确回滚呈现态）；通过后"**覆盖前归档**"（回退本身可逆）→ **单事务**重建 8 张表（`applyProjectionTables` 7 张 + 头部；`presentation_projection_state` 不在归档 JSON 内、不触碰），`state_version` = 当前+1，`checkpoint`/`known_ledger_head`/`projector_version` 与归档逐字一致；**不复用** `commitProjection`（防降级门按设计拒旧版本）；③`schema_ddl` **单源** `projectionTablesDdl()`——归档写入与恢复比对同函数。 | 零 bump 证明：恢复是**维护面**（把归档原样写回），不改任何投影/计划公式；对固定账本，"恢复 + 重排空"与归档时逐位一致（演练实测）；恢复无生产调用点，期望版本由调用层显式传入（`core:database` 按依赖方向看不到 `LearningProjector.VERSION`） | 计划 `docs/research/2026-10-02-stage3b-plan.md` §3 步骤三第 4 条（用户裁"工具化 + 演练"）；`docs/research/kernel-projection-rollback.md` §4；复核 2026-10-03 | 演练仪器化（真 Room + 真 drainer，冷启模拟器）：`ProjectionRollbackDrillInstrumentedTest` **9/0/0**（主链逐位一致 + 归档只增 0→1→2→3 + 4 类拒绝 + 3 守卫 + 呈现态超前拒绝）；`:core:database` 全量仪器化 **200/0/0**（性能门 6/0 冷启严格口径、矩阵 1→59 6/0）；JVM `:core:domain` 550 / `:core:model` 394 / `:core:data` 604 / `:core:database` 121 全 0；记录 `docs/research/2026-10-03-projection-rollback-drill.md`（§4 逐步映射 + 实测原始输出 + 只增裁定与复看触发点） | ✅ 归档表只增；恢复不改归档行 |

> 复核整改（2026-10-03，B6 修复轮；独立复核 approve-with-notes、P1 全处置）：P1「呈现态不随恢复回滚」的文档过度声明
> 已订正（重派生**仅全量重放路径**；增量路径以未回滚呈现行为权威 → 早于现存终局即**显式拒绝**）；三守卫
> （`CHECKPOINT_AHEAD` / `RESTORED_AT_IN_PAST` / `MALFORMED_ARCHIVE`）与文档口径（drill §4-4/§4-5、runbook §4-4）全处置。
> 未做反向实证（无守卫时的楔死形态未直接复现，依据为 `PresentationProjectionState` 的 require 链 + 新用例在真实构造上触发）——已登记 UNVERIFIED。

### 3.16 阶段 4A 批 1 · 数据与读侧面（2026-10-03，L1 + L2 + L5 + S17）：schema 60 + 零算法 bump

| 版本串 | 变更 | 公式/口径 | 数据来源 | 测试证据 | archive |
|---|---|---|---|---|---|
| `STUDY_DATABASE_VERSION` 59 → **60** | **重建 `library_catalog` 视图**：标题列 `unit.title` → `revision.title`（L2 读侧单源；视图本就 JOIN `problem_revision`）。迁移 `KERNEL_WAVE7_MIGRATION_59_60` = DROP + CREATE，**不动任何表、任何行**；`schemas/60.json` 生成。写侧双列与 `practice_unit.title` 列保持不动（计划 §5②：列删除收益不抵 RESTRICT 子表重建风险） | **无算法公式变更**：投影输出、计划指纹、账本逐位不变；库内唯一变化是视图定义（同库同数据的查询结果对同一读者同值） | 计划 `docs/research/2026-10-03-stage4a-plan.md` §3 批 1 + §4；L 条裁定 `docs/agent-first-refactor-decisions-2026-09-23.md:1058-1068`（L1/L2/L5） | `KernelWave7SchemaContractTest`（59/60 视图翻列、迁移 CREATE 与 60.json 逐字一致、全部表与另一视图逐字不变）；`KernelWave0SchemaContractTest`（版本常量 == 导出最高版本 == 60）；仪器化 `FullMigrationMatrixInstrumentedTest.libraryCatalogRebuildReadsRevisionTitleWithoutTouchingStoredRows`（真库迁移后视图读 revision.title、枢纽列与条目行原样）+ 矩阵 1→60；单源证明 `MistakeDetailDatabaseInstrumentedTest.catalogAndDirectoryReadRevisionTitleWhilePracticeUnitTitleStaysLegacy` | ➖（不改投影输出，不触发重放） |
| `LearningCoreVersions` 全部串**未 bump**（复合串维持 `learning-core-v12`） | ①**L1**：删 `TRASHED` 死枚举三处（`StudyDbValue` / `DatabaseContract` 校验白名单 / `core:model` 枚举，全库零写入点）+ 归档即移出文案；②**L5**：`LibrarySort` 收敛为 `{RECENTLY_UPDATED（默认）, RECENTLY_CREATED}`、删知识点筛选参数/筛选层、新增「录入时间段」（创建时间闭区间：query 起止 + DAO 过滤 + UI 控件）；③**S17**：`observeActiveMistakes` 逐行相关子查询 + 嵌套 EXISTS → 派生表 + LEFT JOIN 聚合 | **零算法口径**：L1 是枚举退场（无写入点、无读者）；L5 是筛选/排序面收敛（不改任何评分或计划输入）；S17 是**等价重写**——同一库态下新查询与旧 SQL 逐列等价，`next_review_at`/掌握度 facet 等读出的仍是同一投影事实 | 同上 | S17：`ProblemDaoAggregationEquivalenceInstrumentedTest`（新 DAO 与 S17 前 SQL 原文逐行逐列对照；覆盖无回执/有回执/ARCHIVED 等形态——**同 unit 双条目在 schema 上不可达**（`error_book_entry.practice_unit_id` 唯一索引），用例如实声明不构造）；L5：`LibraryCatalogTwoValueSortInstrumentedTest`（两值排序给出不同序——夹具保证排序失效必红，非"互为逆序"；+ FTS 路径 + mastery facet 计数）、`LibraryCatalogPagingInstrumentedTest.createdRangeFilterNarrowsCatalogFtsCountsAndFacets`（目录/FTS/计数/facets 同窗口）、`LibraryCatalogTest`（JVM：时间段换算/默认排序/清除筛选）；L1 零残留：全仓代码 grep 无 `TRASHED` 引用（仅历史文档保留记述） | ➖（无投影/计划输出变化） |

> 批 1 纪律记录：正式门（DB 仪器化全套 / app 三屏）由协调方跑；本批定向证据见上表，完整清单与未验证项见批 1 交付回报。
> S17 既有性能/EXPLAIN 门（`PerformanceGateTest` library 段）保持原断言，仅随 L5 参数面同步调用签名。
> 复核登记（2026-10-03，批 1 独立复核 approve-with-notes）：① **S17 性能未量化**（无专属 EXPLAIN/负载门；`PerformanceGateTest.insertTestData` 为既有空实现）——UNVERIFIED 登记；② **L2 尾巴**：`PracticeUnitAssessmentDao.kt:49` 的 `unit.title` 读者（讲题工件标题）在本批锚定范围外，双写成立时无分叉，将来「改写既有 revision」落地时需一并收口；③ **订正**：计划 §1 旧引用的「S17 既有缺陷登记 `known-defects.md:265`」实为 KD-7（无关），已同步订正计划原文；④ 时间段 UI 上界生产化（`createdToEpochMillis = now`，闭区间），该参数不再是"无生产设置点"。

### 3.17 阶段 4A 批 4 · 导出后台化（2026-10-03，L7，A 形态）：schema 61 + 零算法 bump

| 版本串 | 变更 | 公式/口径 | 数据来源 | 测试证据 | archive |
|---|---|---|---|---|---|
| `STUDY_DATABASE_VERSION` 60 → **61** | 新增 `mistake_export_record`（导出记录表：`export_id` / `kind` / `display_name` / `status` / `input_sha256` / `pdf_sha256` / `page_count` / `failure_message` / `created_at` / `finished_at`）。迁移 `KERNEL_WAVE8_MIGRATION_60_61` = 单句 `CREATE TABLE`，**非破坏、不回填**（存量库没有导出记录，不编造行）；`schemas/61.json` 生成 | **无算法公式变更**：投影输出、计划指纹、账本逐位不变；导出记录只是"后台任务做没做完"的持久身份 | 计划 `docs/research/2026-10-03-stage4a-plan.md` §3 批 4 + §4；L7 裁定 `docs/agent-first-refactor-decisions-2026-09-23.md:1068`；外部资料见计划 §2.1/§2.2（expedited 普通 Worker、不引入 FGS 类型） | `KernelWave8SchemaContractTest`（只在 61 多一张表、其余表/视图逐字不变、迁移 CREATE 与 61.json 逐字一致、列形状钉死）；`KernelWave0SchemaContractTest`（版本头 == 61）；仪器化 `FullMigrationMatrixInstrumentedTest.mistakeExportRecordTableIsCreatedEmptyWithoutTouchingStoredRows`（真库 v60→61：新表建出且为空、旧行原样）+ 矩阵 1→61 | ➖（新增表，不触发重放） |
| `LearningCoreVersions` 全部串**未 bump**（复合串维持 `learning-core-v12`） | ①`ExportPdfWorker`（expedited `CoroutineWorker`，配额不足按 `RUN_AS_NON_EXPEDITED_WORK_REQUEST` 降级；`getForegroundInfo` 兼容 API<31 的 WorkManager 前台服务路径；targetSdk 36 下不引入长任务 FGS 类型）；②渲染核心抽成 JVM 可测单元：`MistakePdfPagePlanner`（纯排版：折行/分页/行预算/页预算，宽度经 `PdfTextMeasure` 注入）、`PdfFigureLayout`（图形占高从 android 对象拆出）、`MistakeExportJobRunner`（读快照 → 判定 → 渲染 → 完整性核对，三态结果）；③完成/失败两态通知 + Android 13+ 未授权退化到应用内「导出成果」；④`Routes.ExportResults` 入口（列表 → 分享/保存/打印走既有 `MistakePdfDelivery`），详情/库页导出动作改为"入队后即可离开"；⑤清理策略：记录保留至用户处理，表上限 30 条只删最旧，产物文件由既有 exporter 缓存上限（24 个 / 64MB / 24h TTL）兜底 | **零算法口径**：导出是"把已确认题面排版成 PDF"的纯输出面，不改账本/投影/计划；L7 只改任务归属（页面生命周期 → WorkManager）与结果落点（前台页 → 记录 + 通知 + 成果入口） | 同上 | JVM：`MistakePdfPagePlannerTest`（7）、`MistakeExportJobRunnerTest`（9）、`MistakePdfExportCopyTest`（5）、`ExportPdfWorkerTest`（5）；仪器化：`MistakeExportBackgroundInstrumentedTest`（离开页面后仍完成并可取回）、`MistakeExportNotificationsInstrumentedTest`（授权两态 / 未授权不发）、`MistakeExportDeliveryInstrumentedTest`（真产物 → CREATE_DOCUMENT / SEND 意图 + 打印动作）、`MistakeExportHubInstrumentedTest`（5）、feature/library 迁移用例 | ➖（无投影/计划输出变化） |

> 批 4 纪律记录：正式门（全量 JVM / DB 仪器化全套 / app 三屏）由协调方跑；本批定向证据与未验证项见批 4 交付回报。
> 被替换的既有导出门用例去向：`MistakeExportNavigationInstrumentedTest` → 迁到讲题路由（导出不再带 key 走路由）；`MistakeExportUiPolicyTest` 的文案/命名断言 → `core:export` 的 `MistakePdfExportCopyTest` + `MistakeExportJobRunnerTest`；导出页预览用例 → `MistakeExportHubInstrumentedTest` + core:export 既有 `MistakePdfExporterInstrumentedTest`（交付链本身）。
> **A4 预览面登记**：`MistakePdfPreview` 随旧导出页退场后**生产零消费者**（grep 只剩 core:export 内部与仪器化用例）——不是漏迁：预览是"前台导出页"的界面面，A 形态下用户要的是落盘 + 成果入口；类保留，交付链覆盖（真 PDF 打开/逐页渲染）仍在 `MistakePdfExporterInstrumentedTest`。若将来成果入口要加缩略图，从这里接回。

### 3.18 阶段 4B · 生图工具面与文档导出（2026-10-03/04，四批）：零算法 bump

> 编号说明：§3.18 由阶段 4B 占用。四批均为产品功能面（工具环第 8 枚 `GENERATE_FIGURE`、
> 生成图持久化/幂等、导出排版参数化、智能体触发导出），未触碰账本/投影/计划/打分公式。
> 完成记录：`docs/research/2026-10-03-stage4b-completion-record.md`（边界与 UNVERIFIED 见其 §7）。

| 版本串 | 变更 | 公式/口径 | 数据来源 | 测试证据 | archive |
|---|---|---|---|---|---|
| `STUDY_DATABASE_VERSION` / `LearningCoreVersions` 全部串 | **未 bump**（schema 维持 **61**；复合串维持 `learning-core-v12`） | **零 schema**（无表/列改动；`GENERATED_FIGURE` 是既有 TEXT 列 `source_type` 的新代码值，非迁移——`StudyDbValue.kt:50`）；生图与导出都是「把已确认题面/已生成资产做持久化与排版输出」，**不改投影输出、计划指纹、账本行**；A1 只改出网请求的声明集与模型行为，A2 只增资产行+引用，B1–B4/B6 只改排版输出，B3 只改本地动作 payload/确认流程 | 计划 `docs/research/2026-10-03-stage4b-plan.md` §4；设计契约 `docs/research/2026-09-28-imagegen-and-export-design.md`；四批提交 `7a44bf40` / `92764af2` / `d2317f5d` / `c11193a8` | 批级正式门：批 1 全量 JVM（243 任务）+ core:data 工具环 **12/0/0** + app 三屏 **5/0/0**；批 2 全量 JVM + DB 全套 **210/0/0** + core:data **14/0/0** + feature:tutor **64/0/0** + app 三屏 **5/0/0**；批 3 全量 JVM + core:export **19/0/0** + feature:library **9/0/0** + app **8/0/0**；批 4 全量 JVM + core:data **12/0/0** + feature:tutor **64/0/0** + feature:library **9/0/0** + app 五类 **9/0/0**。4B 综合门：全量 JVM **2344/0/0**（app 95 / core:data 648 / core:database 129 / core:domain 558 / core:export 63 / core:model 410 / core:ui 38 / feature:capture 110 / feature:library 39 / feature:profile 6 / feature:review 39 / feature:tutor 209）、DB 全套仪器化 **210/0/0**（2026-10-03T23:22:51Z）、app 全套仪器化 **53/0/0**（23:24:59Z）、R8 冒烟净（PID 20321 / logcat 零 FATAL）。退出门「生图→导出→打印真机走查」因模拟器无模型凭据**未完整走通，登记 UNVERIFIED** | ➖（无投影/计划输出变化，不触发重放） |
| `ModelEgress` 提示词版本（**非算法台账号段**，自记） | 批 1：`tutor-plan-v16-figure-no-preview` / `tutor-respond-v23-figure-no-preview` / `tutor-lobby-v14-figure-no-preview`；批 2：`tutor-plan-v18-figure-conditional` / `tutor-respond-v25-figure-conditional` / `tutor-lobby-v16-figure-conditional`；批 3：**不动**；批 4：`tutor-lobby-v17-export-layout-proposal`（plan/respond 不动） | 工具声明集 7→8（`GENERATE_FIGURE`）与 `START_EXPORT` 参数声明改变出网请求形状 → bump 提示词策略版本（egress 授权回执不再冒充同一提示词）。**不改变任何算法数值** | `ModelEgress.kt:53/63/79`（实际值经逐提交核对：`git show <7a44bf40\|92764af2\|d2317f5d\|c11193a8>:core/model/.../ModelEgress.kt`） | 同左栏各批门 | ➖ |

> 4B 登记要点（详见完成记录 §7）：隐式 AI 标识不随 App 的 PDF 重绘走（对外分发 PDF 无标识，显式标识未落）；兼容链 GC 后可能重复付费 / PROCESS 两链幂等键不同 / 多实例并发残余；`figure-<hex>` 不符合 `validateSourceAsset` 路径不变量但无生产入口；金样为单机登记；hub 行无「解析区已跳过」说明（零 schema 无列）；提示词 v17 对真实 provider 行为未验证；微信接收 PDF 未文档化；真机走查生图段不可达。

### 3.19 K1 · 3D 内核侧余件批 2 · KF-32 空绑定重放改挂 pseudo（2026-10-09）：一次投影 bump 覆盖两串

> 编号说明：§3.19 由 K1 批 2 占用。**取代登记**：K1 计划 §4 按"零算法版本 bump"立预算，
> 经裁定由本行取代（判据：①本批确实改变重放输出，而 §3.13 把旧行为登记为 v12 既定语义，
> 静默改会让台账失真；②先例 v11→v12 属同一类重放语义变更；③不 bump 则"已按 v12 重放过的库"
> 永远拿不到修复——重放只由版本不匹配或改绑事件触发，静默无效）。计划 §4 的原文与本次取代的
> 理由须在 K1 完成记录里写明。
>
> 旧行为登记原文：3C 批 1 记录 §4.4（`docs/research/2026-10-04-stage3c-part1-completion-record.md`）
> ——"空绑定确认会触发 `BINDING_CHANGED`，但重放派生为空后退回写时快照，历史证据仍挂旧 topic、
> 不清零也不改挂 pseudo"；§3.13 尾注同述该回退为 v12 登记行为。本批闭合该缺口。
>
> **"bump 前旧投影必须已归档"由既有机制保证**：`StudyProjectionDrainer.commitFullReplay` 先
> `archiveDisplacedSnapshot` 再重放（W0-1 ③，v8 bump 起既有），`LearningProjector.replay` 对
> 版本不符的被替换快照强制要求"已归档"——本批不新增归档动作，也不改归档路径。

| 版本串 | 变更 | 公式/口径 | 数据来源 | 测试证据 | archive |
|---|---|---|---|---|---|
| `PROJECTOR` v12 → **v13**（复合串 `learning-core-v12 → v13`）；`ATTRIBUTION` v4 → **v5**；`EVIDENCE` / `SKIP_POLICY` / `FORGETTING_CURVE` / `SELECTOR` / `LEDGER` 不动（`SELECTOR_COMPOSITE` 内嵌 `PROJECTOR`，随复合串同步为 `learning-core-v13`） | 空绑定（诚实未分类）题的**重放归属来源**变更：v12 = 重放派生为空 → 退回写时快照（历史证据留在旧知识点）；v13 = 装配侧（`StudyProjectionDrainer.readCurrentPracticeUnitBindings`）在当前绑定集合为空时**复用既有** `ensurePseudoKnowledgeBinding` 物化 `pseudo:<科目>` 事实，再走同一条单源派生 `derivePracticeUnitBindingAttributions`。输出变化：同一账本下空绑定题的历史证据由旧节点改挂 pseudo 桶，旧节点因"重放从空表累加 + 差集删除"归零，证据不丢 | 与写路径 `StudyPracticeUnitFacts.attributionSetFor` 的空绑定分支逐条同规则：taxonomy 同常量 `pseudo-evidence-v1`、绑定 revision 取该题当前 revision、科目取 `problem.subject`、时间取 revision 创建时刻（确定性 ⇒ 重放幂等）；单条伪绑定派生为权重 1.0 / `PRIMARY` / `DIRECT`，`basisRevisionId` = 该 attempt 快照自己的 revision。物化只对 attempt / 揭示两个归属消费点触发（曝光事件不读归属，不为它写行）。写路径与增量 `project()` 一行未动，`PROJECTOR` bump 是重放语义变更触发的版本门 | 触发与登记：3C 批 1 记录 §4.4；旧行为登记 §3.13（本批取代）；K1 计划 §2d；本批升级裁定（见本节前言） | JVM 定向（本批自验，`2026-10-09`）：`BindingChangedReplayDrainerTest` **5/0/0**（新增：空绑定→pseudo+旧节点归零+归档保真、两态幂等（伪绑定可见/不可见）、曝光-only 负向不物化）；`BundledKnowledgeBaseInstallerTest` **6/0/0**（含 D-5 快路径判定两态纯函数用例）；`RoomBackedStudyExperienceRepositoryTest` **31/0/0**（写路径/增量逐位不变）；`ReplayFingerprintDrainerTest` + `ProjectionArchiveDrainerTest` + `ProjectionDrainerSuccessorsCacheTest` + `ProjectionDrainerDispatcherTest` **8/0/0**；`LearningProjectorReplayFingerprintTest` + `LearningProjectorTest` + `ProjectionVersionGuardTest` **27/0/0**；`KernelWave0SchemaContractTest` **7/0/0**（定串同步 v13——版本钉用例的职责，非放宽断言）。原始命令与末行见 K1 批 2 交付报告（正式门由协调方跑） | ✅ 走既有机制：bump 触发的重放先归档被替换的 v12 投影再覆盖（`StudyProjectionDrainer` 的 archive-before-replay），`projection_archive` 只增 |
| `LearningCoreVersions` 其余串（含 `STUDY_DATABASE_VERSION` 61） | **不动** | 同批 D-5 装机快路径回归钉：`BundledKnowledgeBaseInstaller.install()` 的"`recorded.contentVersion == manifest.contentVersion` 即跳过"判定抽成 internal 纯函数 `isInstallUpToDate`——**纯抽取、判定口径逐字未变**、零投影/计划/账本输出，零 schema | K1 计划 §2e；契约 §5.2（只写侧车不 bump → 字段静默不到设备） | `BundledKnowledgeBaseInstallerTest` **6/0/0**（新增两态纯函数用例：一致 → 跳过；不一致 / 无进度行 / 无 manifest → 全量；非恒真）；既有 `install()` 快路径/全量路径用例同绿 | ➖（无投影输出变化，不触发重放） |

### 3.20 KD-38 批 · 讲题动作标签预算（2026-10-15 修复 / 2026-10-16 提交 `038d50c0`）：提示词版本 v19/v26（零算法 bump）

> 编号说明：§3.20 由 KD-38 批占用。本行只登记 `ModelEgress` 的**提示词策略版本**（非算法台账号段，
> 自记）；§3.18 的批 1/批 2 行是历史记录、**不动**——那里记的 `v18`/`v25` 是那一批的值，本批是它
> 之上的下一次 bump。KD-38 批自身不动 `LearningCoreVersions` / `STUDY_DATABASE_VERSION`
> （本行的登记面只有提示词版本；同批其它轨的算法串变更按各自的行登记）。

| 版本串 | 变更 | 公式/口径 | 数据来源 | 测试证据 | archive |
|---|---|---|---|---|---|
| `ModelEgress` 提示词版本（**非算法台账号段**，自记）：`tutor-plan-v18-figure-conditional` → **`tutor-plan-v19-move-label-budget`**；`tutor-respond-v25-figure-conditional` → **`tutor-respond-v26-move-label-budget`**；`tutor-lobby-v17-export-layout-proposal` **不动**（`ModelEgress.kt:55` / `:67` / `:83`） | 计划规则 12（`OpenAiModelTaskAdapters.kt:252`）与回应规则 8（`:349`）各补一句 `nextMoves[].label` 预算声明（渲染为「label不超过32个字符」，数值以 `TutorSuggestedMove.MAX_LABEL_CHARS` 插值）；同批解析期对**短标签**按预算收敛（动作标签 32 / 知识点标签 96 / 锚点词 64，`OpenAiModelResponseParsers.kt:353/357/364/408/633/646`），超预算改抛 `ModelTextBudgetExceededException`（`ModelTextBudget.kt:19`）→ 网关归**可重试** `INCOMPLETE_OUTPUT`（`OpenAiCompatibleModelGateway.kt:281-285`、`:441-452`、`:891`）；构造器与 `requireSafeModelText` 的校验一律**未放松**，正文级超预算仍走可重试失败 | **不改变任何算法数值**：账本 / 投影输出 / 计划公式一律不变；变的是"模型输出如何被接受"——一个 33 字符的动作标签此前把整轮计划判死且不可重试，现在解析期收敛（§3.18 同族：提示词策略版本改变出网请求形状）。带 egress 清单的**请求指纹**随清单里的 `promptPolicyVersion` 变（`ModelEgressManifest.promptPolicyVersion`；`ModelTasks.kt` 的 `fingerprintPayload()` 负载含整份请求，故指纹随之变） | KD-38（`docs/known-defects.md:1747`）；修复提交 `038d50c0`；版本字面量经逐字核对 `core/model/.../ModelEgress.kt:55`（plan，KDoc `:52-53`）/`:67`（respond，KDoc `:64-65`）/`:83`（lobby 不动）；预算常量 `TutorTasks.kt:152`（96）/`:207`（32）/`:683`（64） | `core:data`：`TutorMoveLabelBudgetPromptTest`（计划/回应两条提示词含预算声明与两个版本字面量——提示词一改，钉版本的用例先红）、`TutorLabelBudgetTruncationTest`（97 字标签截到 96、66 字锚词截到 64 且仍逐字绑定本轮题、直接构造 33 字标签仍抛 `ModelTextBudgetExceededException`）、`OpenAiCompatibleModelGatewayTest`（计划/回应 33 字标签截断后整轮成功；正文级超预算 → 可重试 `INCOMPLETE_OUTPUT`）。以上为 KD-38 批验收证据（批内自跑 + 独立复核复跑，见 known-defects 同条）；**设备前后对照未达**（服务商周配额 429），触发频率自登记起未取样 | ➖（无投影/计划输出变化，不触发重放） |

> 同批（T10）的 KD-38 **剩余项**修复也**不 bump**：`String.takeBudgetChars`（`core/model/.../ModelTasks.kt`）
> 让流式预览的按字符截断不再切碎代理对（`Progress.of`；原 `take(n)` 留下孤立高代理、渲染成「�」）。
> 零版本串、零投影/计划/账本输出变化，只在预算边界正好落在成对代理中间时少留一个码元；
> 证据见 `core:model` 的 `ModelTextSurrogateTruncationTest`。KD-38 同条「未达 / 剩余」里
> **解析期**那批 `take(n)`（`core/data` 解析器）与网关 `streamingPrefix` 的同类切法**已在收尾修复批补齐**（§3.22）。

### 3.21 T9/AUD-11 ②③ · 冷却限通道 + 可观察性与审计面（2026-10-16，批次 3E/6C/AUD-09 同批）：零算法 bump

> 编号说明：§3.21 由 T9（AUD-11 实施轨）占用；KD-38 批的 `v19`/`v26` 在 §3.20，不占新号。

| 版本串 | 变更 | 公式/口径 | 数据来源 | 测试证据 | archive |
|---|---|---|---|---|---|
| `LearningCoreVersions.*`（`PROJECTOR`/`EVIDENCE`/`ATTRIBUTION`）/ `STUDY_DATABASE_VERSION`（65）/ 各指纹 schema / 提示词策略版本 | **全部未 bump**（一条不动） | ①**冷却范围是准入参数、不是公式**：AUD-11 ① 把 12h 同 KC 冷却限定在 MODEL_CHAT 通道（`MasteryWriteGate.SAME_KC_COOLDOWN_MILLIS` 的文档口径本就是"another **model** evidence write"）——变的是"哪些写被门放行"，不是放行后怎么记账；放行后的权重仍由既有常量与 D9 唯一数值分支决定（一个字节没改），投影器只读 `weight`/`direction`/时间戳 → **同一账本重放输出逐位不变**。②**不新增列、不动事件载荷**：AUD-11 ③ 的题面/键版本引用走**既有身份分量推导**（`KnowledgeQuizEvidenceAudit`：`evidence_id` + `conversation_id` + 知识点 → 题面行 id），`ChatEvidenceSubmitted` 与 `LearningLedgerFingerprint.chatEvidence` 一字未动；`ChatEvidenceWriteReceipt`/`KnowledgeEvidenceLedgerStatus` 是**读侧回报**，不落库、不进指纹。③校准分桶（`source_kind` 维）与报告新增字段是诊断读面，`GateObservation` 新字段全带默认空值 → 旧调用者口径逐字不变 | 裁定：`docs/research/2026-10-15-aud-11-verdict-granularity-cooldown-answer-key.md` §2.3（冷却限通道）+ §3.3（可观察性先行）；"投影层不读 `source_kind`"的源码事实：`core/domain/.../LearningProjector.kt:1546-1547`（来源由账本行自己的 `source_kind` 与 `conversation_id` 承载，投影层不再复制） | **本轨未跑 Gradle**（按轨纪律：门禁由批次串行执行）→ 待门禁实跑：`KnowledgeQuizCooldownChannelScopeInstrumentedTest`（真库：模型→小测放行 / 反向亦然 / 模型自冷却保持且严格 `<` 12h 边界 / IGNORE 回报三档 / 校准分桶带通道维）、`KnowledgeQuizEvidenceAuditInstrumentedTest`（真库：证据行对回题面 + 键版本；坏行只报键版本）、JVM：`KnowledgeEvidenceLedgerStatusTest`、`KnowledgeQuizEvidenceAuditTest`、`ChatEvidenceGateCalibrationTest`（新增 3 例，既有 6 例不动）。**既有 AUD-01/02 用例不回退**同样待门禁（其断言未改，仅两处注释随语义更新） | ➖（无投影输出变化，不触发重放） |

> **同批写明的行为变化（诚实登记，不是 bump 条件）**：改后"同一用户操作序列"可能产生**不同的账本**
> ——模型写不再挡当晚的小测客观证据、小测写也不再挡模型写（报告 §2.2 实测：改前当晚小测落库 0/3）。
> 版本串管的是"同一账本重放是否给出同一结果"，与"哪些写会进账本"是两件事；后者正是本裁定要改的
> 准入面。洪泛面复核读数已随本批落进校准面（`acceptedPerSourceKindPerHour` / `peakAcceptedBySourceKind`）。

### 3.22 批次 12 收尾 · T8 判词归属落账 / T5 3E / T6 schema 66 / 截断补齐（2026-10-17）：零算法 bump（除 schema 66 的存储面）

> 编号说明：本批 T9/T10 的登记在 §3.21/§3.20；本条覆盖同批 T8/T5/T6 与收尾修复。

| 版本串 | 变更 | 公式/口径 | 数据来源 | 测试证据 | archive |
|---|---|---|---|---|---|
| `LearningCoreVersions.*`（`PROJECTOR`/`EVIDENCE`/`ATTRIBUTION`）**全部未 bump**；`STUDY_DATABASE_VERSION` 65→**66**（新增 `clean_redraw_task` 表，非破坏迁移）；各指纹 schema 未动 | **T8 判词归属落账（`TutorJudgedReviewSettler`）**：模型判词分支把驱动证据落成快照的一条 `KnowledgeEvidenceAttribution`（节点 + 现役绑定 + 生效权重）；快照 id 从 `stableId("tutor-judged-snapshot", requestId)` 改为**内容寻址**（追加归属分量；同内容重放命中同一行，内容真变写新行——防 KD-37 同类身份死路）。**T5 3E**：知识点复习候选加第二档（全库「有绑定题的活跃知识点 ∩ 可出题材料」− 今日候选池，`FULL_LIBRARY_SCAN_DAILY_CAP=10`）；**T6**：净化作业持久表 + 源图 sha 指纹 + 同指纹跳过 + 启动对账重试 + SUPERSEDED 保护；**收尾**：解析期 6 处 `take(n)` + 网关 `streamingPrefix` 改 `takeBudgetChars`（不切代理对）；`CleanRedrawTaskDao.claimTaskAttempt` 改以 UPDATE 命中行数为准（并发 CAS 修正） | **零算法 bump 的判据（逐条）**：① T8——投影器与两条归属派生路径**一行未动**（`LearningProjector.attributionsForAttempt` 的 upcast 派生自 KF-32 起就在「题有当前绑定（含 pseudo）」时从绑定事实派生归属；`projectMastery` 只读 `attempt.evidence.weight`）；新增内容只出现在**新**快照上，对**固定账本**重放逐位不变（判别先例 §3.12/§3.14）。② T5——知识点队列**非持久化**（会话内现排，`KnowledgeNodeScorer` 无版本串，同口径见 §3.12 相关说明）；错题计划（`ReviewPlannerV2.VERSION`）一行未动。③ T6/收尾——净化作业是**新增写路径**（新表、不参与既有投影/指纹）；截断与 CAS 修正不改变任何数值输出。④ schema 66 是**存储面**变更（新表 + 迁移），非算法数值 | T8 裁定=2026-10-16 用户批准（锚定 + 归属落账）；T5 裁定=同批（每日 10）；实现与证据见批次记录 `docs/research/2026-10-16-batch-12-twelve-track-record.md` §2 T5/T6/T8 与 §8 | T8：`TutorJudgedReviewSettlerTest`（前置 KC 证据 → NO_VERDICT；同节点证据仍判；归属指向驱动证据；坏绑定不伪造归属）；T5：`KnowledgeReviewQueueTest` 扩展 + `RoomBackedStudyExperienceRepositoryTest`（范围改写保留原断言为子集）；T6：`CleanRedrawTaskRunnerTest` 17/0、`CleanRedrawTaskInstrumentedTest` 2/2（设备）、`FullMigrationMatrixInstrumentedTest` 13/0（设备，1h36m）；收尾：单测门三模块 BUILD SUCCESSFUL（2026-10-17 主会话实跑）。**设备模型轨（KD-37/38 前后对照）仍 429 未达** | ➖（固定账本投影逐位不变，不触发重放）；**诚实登记**：题级 0.5 权重经归属/派生进入被绑定 KC 的流向在「题有当前绑定」路径上**先于本批存在**，T8 只在「绑定为空/派生为空」的回退路径上改变行为；与 `model-judged-verdict-pricing.md` §4(ii)「模型判对的 KC 写入上限 0.15」的张力属**既有**待裁定项（批次记录 §7 第 3 条） |

### 3.23 批次 13 · KD-44 pseudo 自动结算 / schema 67 / KD-51 分块重建 / T3 回执字段（2026-10-18）：零算法 bump（除 schema 67 的存储面）

> 编号说明：本条覆盖批次 13（6B-1/4C/KD-51/KD-44/低危包/6C-2 设计件）中触及算法纪律面的四条。

| 版本串 | 变更 | 公式/口径 | 数据来源 | 测试证据 | archive |
|---|---|---|---|---|---|
| `LearningCoreVersions.*`（`PROJECTOR`/`EVIDENCE`/`ATTRIBUTION`）**全部未 bump**；`STUDY_DATABASE_VERSION` 66→**67**（`error_book_entry` 加 `is_favorite` + `problem_draft.source_asset_id` 改可空，非破坏迁移）；各指纹 schema / 提示词策略版本未动 | **KD-44 pseudo 自动结算（语义变更，按 2026-10-17 裁定实施）**：`TutorJudgedReviewSettler` 在本题 KC 集为空（诚实未分类）时把锚定集换成该题 revision 的**伪绑定**（`pseudo:<科目>`，已物化则直接取、未物化则复用既有物化先例就地物化）→ 判词正常结算到 pseudo、归属指向伪绑定行；**零用户参与**（无新文案/指引）。**KD-51**：整科重建改「读相 + 分块短写事务（删/插各 64 节点一块）+ 块间可取消」，锚点最后写语义保持。**T3**：提交摘要加 `reusedExistingProblem` 如实回执（判据一行未动）。**收尾**：docx CRLF 归一、worker 坏 format 不重试、回执文案按两种情形都为真改写 | **零算法 bump 的判据（逐条）**：① KD-44——投影器与归属派生路径**一行未动**：`attributionsForAttempt` 自 KF-32 起就从**当前绑定**派生（伪绑定事实由装配侧物化、同为绑定事实），pseudo 结算只改变「新快照的归属内容」；对**固定账本**重放逐位不变（判别先例 §3.12/§3.14/§3.22）。② KD-51——分块只改**事务边界**，重建输出行集逐位不变（`KnowledgeSearchIndexRebuildChunkingInstrumentedTest` 有等价断言）；锚点语义未变。③ T3——`reusedExistingProblem` 是**读侧回执字段**，不落库、不进指纹；`created` 语义未动。④ schema 67 是**存储面**变更（加列/放宽 NOT NULL），非算法数值。**诚实登记**：pseudo 上自此开始出现判定权重（与证据通道既有的 pseudo 写入同口径）；题后来被绑定/改绑后，重放按当前绑定改挂真实节点（KF-32 自纠） | 裁定=2026-10-17 用户批准（「后台自动做好、零用户参与、用户不需要知道」）；实现与证据见批次记录 `docs/research/2026-10-17-batch-13-nine-track-record.md` §2 T4/T5/§3.2/§8 | KD-44：`TutorJudgedReviewSettlerTest`（空 KC 集题正常结算到 pseudo、归属指向伪绑定、不再 NO_VERDICT；真·无判词仍 NO_VERDICT）+ 设备 `TutorJudgedReviewSettleInstrumentedTest` 2/0；KD-51：`KnowledgeSearchIndexRebuildChunkingInstrumentedTest` 4/0 + 设备强制全量安装 0 条连接饥饿；T3：`CaptureCommitReuseVisibilityInstrumentedTest` 3/3（设备，收尾修复后）+ `CaptureCommittedReceiptTest`；schema 67：`FavoriteAndManualDraftMigrationInstrumentedTest` 2/0 + 矩阵 13/0 | ➖（固定账本投影逐位不变，不触发重放）；**待裁定遗留**：题级 0.5 经归属进 KC 与定价文 §4(ii) 0.15 上限的张力（既有项，§3.22 同注） |

### 3.24 批次 14 · 4E.1 波次 1 统一生图底座 / KD-53·54 备份栈（2026-10-19）：零算法 bump

> 编号说明：本条覆盖批次 14（4E.1 波次 1 / A8 生产者 / 6B-2 / KD-53·54 / KD-55 / 4C A7）中触及算法纪律面的两条；A8/6B-2/A7 为产品面、不涉数值。

| 版本串 | 变更 | 公式/口径 | 数据来源 | 测试证据 | archive |
|---|---|---|---|---|---|
| `LearningCoreVersions.*`（`PROJECTOR`/`EVIDENCE`/`ATTRIBUTION`）**全部未 bump**；`STUDY_DATABASE_VERSION` **67 不动**（本批零 schema 变更）；各指纹 schema / 提示词策略版本未动 | **4E.1 波次 1**：新增统一生图契约/策略/协调器端口与 `DefaultFigureGenerationCoordinator`/`FigurePromptAssembler`/`RoomFigureDispatchLedger`；图像通道改为只接受绑定 operation/method/源证据/配置的**已预留 dispatch context**（POST 前复核、旧无预留入口 fail-closed）；讲题 GENERATE_FIGURE/保存净化/后台重绘三入口统一转调（IMAGE_MODEL only，JS 不广告）。**KD-53**：备份条目上限按类型分层（DB 1 GiB/题图 100 MiB；总量 2 GiB、压缩比 100 保留）。**KD-54**：备份期写「让路」门（BackupWriteGate + 两仓库接入） | **零算法 bump 的判据**：① 4E.1 波次 1 是**准入与派发层**——投影器/判据公式/账本载荷一行未动；`FigureGenerationPolicy` 的预算门是入口声明（真门是既有 `model_task_operation.dispatch_count`，见 KD-60 的语义登记）；固定账本重放逐位不变。② KD-53/54 是备份**资源与并发**面（上限常量、写门），不进入投影/指纹/计划输出。**诚实登记**：图像预留与讲题工具环共享同一派发预算（KD-60，low）；「无预留→0 HTTP」防线在接口层偏弱（KD-59，low） | 4E 裁定=2026-10-18 用户裁定（统一生图、模型选方法；本波次按计划 §0.1 先图像后端）；KD-53/54 裁定=批次 13 设备实测后按计划 T4 落地。实现与证据见批次记录 `docs/research/2026-10-18-batch-14-eight-track-record.md` §2 T1/T4/§8 | T1：`FigureGenerationPolicyTest`/`FigurePromptParityTest`/`FigureGenerationCoordinatorTest`/`FigureGenerationWiringTest`（fake 后端端到端：统一 policyVersion、预留先于 POST、无预留调用=0）+ 设备 `TutorCommandRecompositionInstrumentedTest` 2/0；T4：`SmbkArchiveCodecTest`/`BackupRestoreInstrumentedTest` 27/0（设备）/`BackupCreateWriteGateInstrumentedTest` 1/0/`CaptureBackupWriteGateInstrumentedTest` 1/0；收尾门禁实跑：七模块单测 + feature:tutor exit=0、androidTest 编译四模块 + 整包 exit=0（2026-10-19 主会话） | ➖（不触发重放）；**未接**：WG-02/04/05/07/08、QuickJS 运行库、真实 Provider 质量（下一波） |

### 3.25 批次 15 T2 · PRD-02 答案键信任与定向纠错（2026-10-19）：schema 68 + 投影/证据/账本三串同 bump

> 编号说明：本条由 T2（PRD-02/KD-43）占用；schema 68 由本轨唯一写入，4E 下一批用 69。

| 版本串 | 变更 | 公式/口径 | 数据来源 | 测试证据 | archive |
|---|---|---|---|---|---|
| `STUDY_DATABASE_VERSION` 67→**68**（`learner_chat_evidence.corrects_evidence_id TEXT NULL` + 索引，非破坏迁移）；`PROJECTOR` v13→**v14**；`EVIDENCE` v5→**v6**；`LEDGER` v3→**v4**；复合串 `learning-core-v13 → v14`（`SELECTOR_COMPOSITE` 同源常量随之） | **账本新增补偿事件 `EVIDENCE_CORRECTION`**（同 outbox kind 承载、载荷按 `source_kind` 区分）：`VOID` = 从有效证据集合排除目标；`REPLACE` = 按账本序列最终有效替代重算，且**挂回原作答位置/时间**（修复日不进学习时钟）。目标可指更早证据或更早更正（“改两次/撤销更正”），目标必须更早 ⇒ 不成环；悬空/自指在重放入口拒绝。**键信任两轴拆分**：`scoringMode=AUTO_VERIFIED` 仍表示“本地 ID 比较确定”，题面 `verificationStatus` 改为 `UNKNOWN`（模型生成无独立支持）；未核验/争议/迟到旧键只落 rejected 观察行（保留真实选择），不新增数值掌握证据。**旧行一字不改、不自动晋升旧反馈** | **为什么必须 bump（不是零 bump）**：①投影器新增“同一账本在有无更正事件时输出不同”的分支——固定无更正账本逐位不变，但含更正的账本无法靠增量路径表达（增量只累加）；②证据语义改变（被撤销证据不再贡献数值掌握）；③账本事件 kind 增加。三条各自都是 `LearningCoreVersions` 的登记轴，故三串同 bump；旧快照靠版本不匹配走 `commitFullReplay`（`projection_archive` 先归档） | 计划 `.zcode/plans/plan-2026-10-20-batch-15-prd-a-b-backup-wg05.md` §T2；PRD-02（`docs/superpowers/plans/2026-10-19-product-readiness-followup-plan.md:44-57`）；设计 §3.2/§3.3（`docs/superpowers/specs/2026-10-19-product-readiness-design.md:41-57`）；外部对照：QTI 3.0.1（correctResponse 是评分声明而非核验证明）、xAPI voiding、W3C PROV-O revision/invalidation、OATutor（人工键→BKT，模型只做 hint）——单一来源/未直读项按研究结论标注处理 | **本轨未跑 Gradle**（按轨纪律：门禁由批次串行执行）→ 待门禁实跑：`ChatEvidenceCorrectionReplayTest`（位置等价/撤销排除/改两次/撤销更正/同 KC 与其他 KC 不误伤/悬空自指拒绝）、`AnswerKeyTrustPolicyTest`（两轴判定表）、`AnswerKeyCorrectionInstrumentedTest`（真库：错键判错→改键、正确→撤销、重复请求、旧键迟到、纠错中被杀、增量==全量重放）、`KernelWave0SchemaContractTest`（68 头/列/索引）、`FullMigrationMatrixInstrumentedTest`（67→68 保行 + 可写更正 + 幂等） | ➖（尚未实跑；`commitFullReplay` 会在首个真机门禁时先归档旧投影） |

> **批次 15 收尾补记（2026-10-20，记录员；只读核对，未跑 Gradle）**：
> ① **门禁实跑已有部分结果**：`KernelWave0SchemaContractTest` 8/0、`AnswerKeyTrustPolicyTest` 5/0、`ChatEvidenceCorrectionReplayTest` 9/0、
> `KnowledgeQuizFeedbackWriteTest` 5/0（XML 时点 02:13-02:17；由串行门禁执行，记录员未重跑）。但工单结论仍是**单测门第 3 轮 `exit=1`**，
> `core:ui`/`app` 无当前结果，因此本行不能改写成“门禁通过”。`AnswerKeyCorrectionInstrumentedTest`（11 例）与 `FullMigrationMatrixInstrumentedTest`
> 的 67→68 用例**未执行**，archive 未在真机门禁触发。
> ② **68.json 当前 `identityHash=607250e58442607e8d8a83d2471d2f67`**，与 67 的 `9bf16ae8…` 不同（文件 mtime 01:39；由 KSP 重写还是手工更新未核实）；T2 原报告“从 67 复制”的问题在记录时点不再复现。
> 但仍需注意 `ExportedSchemaContractTest` 只校验非空与 32 位十六进制，不能防“复制旧 hash”。
> ③ **增量路径的边界照实写**：`ProjectionTransactionDao.kt:634-647` 对 `EVIDENCE_CORRECTION` 直接 `FULL_REPLAY_REQUIRED`；
> `AnswerKeyCorrectionInstrumentedTest.kt:81-93` 所谓“增量 vs 全量”实际是“持久快照（drainer 产出） vs 从空快照全量重放”（full-vs-full），
> 等价性由结构强制，不是该用例证明的。该用例也未跑。
> ④ **不是零 bump** 的判据不变：投影输出、证据语义、账本 kind 三条都变；固定无更正账本逐位不变，但含更正的账本必须全量重放。
> 若后续决定退回零 bump，必须逐条证明账本/指纹/投影输出逐位不变，本批没有这样的证明。
> ⑤ 设备/真机首跑时 `commitFullReplay` 会先归档旧投影；本轮没有执行该路径，不能声称 archive 已落。
> ⑥ **主会话收尾实跑（2026-10-20）**：单测门 11 模块 `exit=0`（含 core:ui/app）；`AnswerKeyCorrectionInstrumentedTest` **11/11 设备通过**
> （错键→改键、撤销、改两次、重复请求、同 KC 不误伤、纠错中被杀、旧键迟到、未核验只观察、已作答回落争议），
> `FullMigrationMatrixInstrumentedTest` **14/14**（13 首轮通过 + 修复 `getText` 读 NULL 断言后单跑 1/1；67→68 保行/可写/幂等过）。
> `commitFullReplay` 在真机重放路径已执行（archive 先归档）；68.json `identityHash` 由本轮构建保持 `607250e5…`。

### 3.26 批次 16 · PRD-05 attempt VOID 哨兵 / 4E WG-04 请求 schema 18（2026-10-21）：零 DB schema、零算法 bump

> 编号说明：本条由批次 16 占用。T1a 选择**无 schema 的 VOID 哨兵**，所以 `STUDY_DATABASE_VERSION` 维持 **68**；
> 请求 schema 17→18 属模型请求合同、不是算法台账号段（随本行自记）。schema **69 本批未被占用**，按 T8 设计件留批次 17 的 WG-08。
> 本条同时登记批次 16 的“零 bump 判据”，避免把新语义写成“没改过”。

| 版本串 | 变更 | 公式/口径 | 数据来源 | 测试证据 | archive |
|---|---|---|---|---|---|
| `STUDY_DATABASE_VERSION` **68 不动**（零迁移；`review_log.scheduling_eligible` 在 67/68 已存在）；`LearningCoreVersions.*`（`PROJECTOR`/`EVIDENCE`/`ATTRIBUTION`/`LEDGER`）**全部未 bump**；各指纹 schema 未动 | **PRD-05 attempt 级 undo 用 VOID 哨兵**：`AttemptCorrection.reasonMarkdown` 以 `[void-attempt:v1]` 开头、载荷固定为 `NONE / weight=0 / ANSWER_REVEALED`（schema 合法零证据形状，构造期 `require` 禁止带前缀却携带真实证据，`LearningState.kt:715-767`）。投影器在**重放路径**进入 `projectMemory` **之前**识别并跳过该 attempt 的记忆/掌握度/揭示因果，但**保留** attempt 行、`responseOrdinal`、曝光与审计（`LearningProjector.kt:586`、`:810` 输出 `voidedAttemptIds`；增量投影遇更正走 `FULL_REPLAY_REQUIRED`）。DB 侧在同一写事务里追加更正 + 回退会话 + `review_log.scheduling_eligible=0`（`RoomStudyDatabase.kt:1502-1537`、`LearningDao.kt:234`） | **零 bump 判据（逐条）**：① 本批之前 `appendAttemptCorrection` **没有生产写入方**（只有测试调用），因此任何已存库都不可能出现该哨兵；② 不含哨兵的固定账本重放输出逐位不变（新增分支只在 `isVoid` 为真时进入）；③ 哨兵与普通零权重 replacement **不同形**：无前缀的 `NONE/0/ANSWER_REVEALED` 仍按“看答案”推进，前缀由 `voidOf` 唯一构造，不会把真实 reveal 误判为撤销；④ DB 列/表未变，`scheduling_eligible` 是既有列；⑤ `voidedAttemptIds` 是投影结果字段，不落新表。**若将来出现历史库含哨兵、或哨兵语义再改**，必须重新评估是否 bump `PROJECTOR`（当前无此路径）。**未采用**“零权重 replacement”直接表达 VOID：`applyAttempt` 先 `projectMemory` 再在 `weight==0` 返回，会被解释成一次 AGAIN/lapse（外部研究 R1 同结论） | 计划 `.zcode/plans/plan-2026-10-21-batch-16-prd05-wg04-capabilities.md:26-42`（T1 优先 VOID 哨兵；不可表达才占 69）；PRD-05 三键/曝光/undo 语义（`docs/superpowers/plans/2026-10-19-product-readiness-followup-plan.md:81-91`、`docs/superpowers/specs/2026-10-19-product-readiness-design.md:53-63`）；外部对照 R1（Microsoft Event Sourcing / Compensating Transaction；Anki undo 行为矩阵；FSRS 四档评分） | **本记录员未运行 Gradle**。只读门禁产物：`ReviewUndoReplayTest` 6/0（core:domain 2026-10-21 02:19）、`ReviewSelfReportContractTest` 7/0（core:data 05:07）；`ReviewUndoTransactionInstrumentedTest`（原子 undo/幂等/拒绝撤销更早 attempt/undo→重评/增量 FULL_REPLAY_REQUIRED/全重放排除/曝光后重评仍非独立）与 `SelfReportedReviewInstrumentedTest` **已写未跑**。这些 XML 不能代替整轮门禁（工单第 3 轮 exit=1） | ➖（零 bump，不触发重放） |
| 请求 schema `CURRENT_SCHEMA_VERSION` 17 → **18**（`TUTOR_VISUAL_CONTEXT_SCHEMA_VERSION`） | **4E WG-04b**：`TutorVisualContext`（源码/state/诊断，`@EncodeDefault(NEVER)`，null 时键完全省略、旧 schema 17 行编码与指纹逐位不变）；`TutorToolCall` 增 `figureMethod/figureProgram/figureImageOperation/figureImagePrompt`（原生 schema 与 Route B 共用一个 `toTutorToolCall` 解析点，JS 一律拒、只广告 IMAGE_MODEL） | **非算法数值变更**：只改模型请求/响应合同与提示词声明面；账本/投影/计划/打分公式未动。`@EncodeDefault(NEVER)` 是“旧指纹不变”的结构保证（`TutorVisualContext.kt:17`、`ModelTasks.kt:546-547`） | 计划 §T3（`:49-52`）；4E 计划 `:242-250`；契约 `docs/current/visual-generation-contract.md:128/136/138/169` | 已写未跑：`TutorVisualContextFingerprintTest`（旧指纹不变、非空 context 变指纹、schema 18）、`TutorFigureMethodDualParseTest`（两路由逐字同义、JS/program 双拒）、`TutorFigureProposalExecutionTest`、`TutorToolFigureTraceFieldsTest`；只读 XML 7/0（2026-10-21 05:07） | ➖（无投影输出变化） |

> **本批另有两个“非算法”常量需要追溯**：`OpenAiSse.kt:563` 的 512KiB、`:566` 的 64KiB 是
> `docs/current/visual-generation-contract.md:84` 的**产品预算**（待设备性能验收），不是 OpenAI/SSE 外部标准；
> `:572` 的 128 tool calls 以官方 Node SDK 为工程锚点。三者只作用于工具参数累积/解码，不进入投影/计划/打分。
>
> **批次 16 收尾实跑（2026-10-21）**：单测门 11 模块 **3,029/0**、设备 **64/0**（含 PRD-05 自评/undo 24/0、
> 6B-2 原生编辑 8/8、能力快照 1/1、批次 15 备份回归 27/0）。零 bump 判据不变；另修两处**不改算法输出**的工程缺陷：
> ① 呈现态校验对“确有 VOID 更正”的呈现只放开 `memoryProjectionApplied` 一位（`ProjectionTransactionDao`），
> 防止全量重放把合法的 true→false 当冲突；② 首轮空 `toolDeclarations` 先补默认工具集再按就绪位裁剪
> （`RoomModelTaskRepository`），防止模型看不到任何工具被工具环丢弃。两条只影响守卫/广告面，不改变投影/计划/打分输出。

### 3.27 批次 17 · 4E WG-08 schema 69 / operation·attempt 审计镜像（2026-10-22）：schema 69 + 零算法 bump

> 编号说明：本条由批次 17 占用。`STUDY_DATABASE_VERSION` 68→**69**，新增两张图生成审计表与四个追加列；
> 学习投影/计划/打分/提示词版本**全部未 bump**。请求 schema 仍为 18（批次 16 已登记，本批未动）。

| 版本串 | 变更 | 公式/口径 | 数据来源 | 测试证据 | archive |
|---|---|---|---|---|---|
| `STUDY_DATABASE_VERSION` 68 → **69**（`FigureGenerationMigration_68_69`） | **4E WG-08 审计持久化**：新增 `figure_generation_operation`（`operation_id` PK、`source_kind`/`source_id`/`basis_fingerprint`、`status`、三个审计镜像计数）与 `figure_generation_attempt`（`attempt_id` PK、operation FK RESTRICT、`(operation_id,attempt_ordinal)` UNIQUE、`output_asset_id` FK RESTRICT）；追加 `tutor_message_source_asset.role`/`basis_revision_id`、`clean_redraw_task.operation_id`、`problem_revision_source_asset.ordinal DEFAULT 0` | **不是算法数值变更**。审计表只记录“谁申请/派发/产出”，不参与投影、复习计划、掌握度或预算判定；预算权威仍是 `model_task_operation.dispatch_count` 与 `clean_redraw_task.attempt_count`（两表 68/69 逐字不变，审计表无指向预算表的外键）。`VisualRole` 是持久角色枚举（CLEAN_IMAGE/TUTOR_EXPLANATION/QUESTION_DIAGRAM/SOURCE_EVIDENCE），只影响读侧分组/导出过滤，不进任何公式。导出 `mistake-export-v3`/`mistake-batch-v3` 是内容指纹版本，不是算法版本 | 计划 `.zcode/plans/plan-2026-10-21-batch-17-wg08-wg06-wg07.md:21-35`；设计件 `docs/research/2026-10-21-4e-wg060708-schema-design.md`；迁移/实体 `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/FigureGenerationMigration_68_69.kt:36`、`entity/FigureGenerationEntities.kt` | **本记录员未运行 Gradle**。门禁材料：单测门第 3 轮 `exit=0`、强门第 1 轮 `exit=0`（脚本自报）；D1 设备迁移矩阵 `FullMigrationMatrixInstrumentedTest` `OK (15 tests)`（含 68→69 保行/幂等、1→69 全循环）；69.json 静态核对：`version=69`、`identityHash=6633b7c2fe223041c4a2927203853f4f`、86 实体/2 视图/92 FK（本记录员 Python 读取） | ➖（无投影输出变化，不触发重放） |
| `LearningCoreVersions.*`（`PROJECTOR`/`EVIDENCE`/`ATTRIBUTION`/`LEDGER`）**全部未 bump**；各指纹 schema 未动；提示词版本未动 | 批次 17 的 WG-06/07 改动集中在角色持久化、保存复制时序、详情/导出读侧过滤与显式重绘状态机 | **零 bump 判据（逐条）**：① 新表/新列没有投影器、复习计划器、打分器、校准读取（`figure_generation_*` 只被审计写入与启动对账读写）；② operation 的三个计数是审计镜像，生产代码无预算读取口，预算仍走既有账本；③ 68→69 是纯追加迁移，旧行保行、旧表/索引/视图逐字不变（`FigureGenerationSchemaContractTest` + 设备矩阵）；④ 角色只改变“哪些资产进题目区/解析区”，旧单 CLEAN_IMAGE 的既有输出由 golden 钉住（T4 JVM）；⑤ 显式重绘复用 `clean_redraw_task` 与其尝试上限，没有新增调度/打分路径。**若将来审计行被用于预算/调度，或角色语义进入投影输入，必须重新评估 bump** | 计划 `:35`、`:116-117`；T1a/T4 契约测试与 golden | 已写未由本记录员执行：`FigureGenerationSchemaContractTest`、`KernelWave0SchemaContractTest`、`MistakeExportVisualRoleTest`；D1 自报 JVM `MistakeExportVisualRoleTest`/`DocxMistakeRendererTest` 与设备 17/17 通过 | ➖（零 bump，不触发重放） |

> **本批非算法常量追溯**：T1a 的四个可空列与两表 DDL 由迁移常量定义，`69.json` 由 KSP 生成后与迁移 DDL 逐字一致（T1a 静态比对自报）；
> D1 的设备迁移是运行时证据，但真实 Provider 质量/费用仍 UNVERIFIED（本批全部模型验证为 fake/夹具）。
> 批次 17 记录见 `docs/research/2026-10-22-batch-17-wg08-wg06-wg07-record.md`。

### 3.28 批次 18 · 6C-2 内容选择备份 v3 / KD-55 有界读入（2026-10-23）：DB schema 69 不动、零算法 bump

> 编号说明：本条由批次 18 占用。`STUDY_DATABASE_VERSION` 维持 **69**；请求 schema 维持 **18**；
> `LearningCoreVersions.*`（`PROJECTOR`/`EVIDENCE`/`ATTRIBUTION`/`LEDGER`）与各投影/计划/打分指纹**全部未 bump**。
> 本条登记的 `formatVersion=3`/`selection.schemaVersion=2`/`closureVersion=2` 是**备份归档合同版本**，不是学习算法版本。
> 零 bump 判据：选择只决定“哪些表/资产进包”和恢复后是否重建派生索引；投影器、复习计划器、打分器、校准均不读
> `selection`，派生索引只是缓存，DB schema 未变；旧账本/旧投影的固定输入重放输出逐位不变。

| 版本串 | 变更 | 公式/口径 | 数据来源 | 测试证据 | archive |
|---|---|---|---|---|---|
| 备份清单 `SmbkManifest.FORMAT_VERSION` 2→**3**；`SmbkSelectionRecord.CURRENT_SCHEMA_VERSION=2`；`SmbkSelectionPlan.CLOSURE_VERSION=2`；digest 前缀 `smbk-selection-v2` | 6C-2 内容选择备份：manifest 新增 `selection` 块（categoryMask/categories/assetPolicy/closureVersion/closureDigest/rowCounts/trimmedTableCount）；4 类别位 ISSUE_BOOK=1/MASTERY=2/SESSIONS=4/IMAGES=8；只允许 mask 9/13/15；闭包 5 条；86 表 rowCounts（0 也写）；派生两张表恒裁；恢复后按科重建 | **digest v2**：`SHA-256("smbk-selection-v2\nclosureVersion=2\ncategories=" + categories.sorted().joinToString(",") + "\nassetPolicy=" + assetPolicy + "\n" + rowCounts.keys.sorted().joinToString("") { "$it=${rowCounts[it]}\n" })`；空库全选 golden `d6ec73ac07e596a11ff97ac292cf01a41e0f962659a2b542a6d8a15c56a5dad6`；`rowCounts` 必须覆盖 86 键。**非算法数值变更**：不进入投影/计划/打分/校准公式 | 计划 `.zcode/plans/plan-2026-10-23-batch-18-6c2-backup-selection.md:9-13,34-43`；设计 `docs/research/2026-10-17-6c2-content-selection-design.md:122-190,336-386,448-501,1388-1439,1586-2191`；实现 `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/backup/SmbkSelectionPlan.kt:38-63,192-271,604-637`、`SmbkArchiveCodec.kt:84-148,298-311,435-482,1043-1063,1142-1226` | **本记录员未运行 Gradle**。只读构建工件：`BackupSelectionTest` 14/0、`SmbkSelectionPlanTest` 11/0、`SmbkArchiveCodecTest` 30/0（2026-10-24 02:41–02:43，failures/errors 均 0）；D1 设备材料：迁移 15/15、裁剪 7/7、成包 3/3、恢复选择域 7/7、恢复回归 33/33、UI 10/10、365MiB 1/1（最终树测试例数已增至 9/8/11，未复跑）。独立核对材料报告 trimOrder 对 92 条 FK 0 违规、空库全选 digest 逐字命中 golden | ➖（无投影输出变化，不触发重放） |
| `LearningCoreVersions.*` / 各指纹 schema / 请求 schema 18 **全部未 bump**；`STUDY_DATABASE_VERSION` **69 不动** | 批次 18 只改备份写读合同、恢复校验、派生索引重建与 UI 勾选；KD-55 只改 DOCX 图片读入（有界读 + 同字节 sha/长度复核） | **零 bump 判据（逐条）**：① `selection` 只被备份成包/恢复路径读取，投影/计划/打分/校准无读取口；② `rowCounts` 是裁剪后快照的 `COUNT(*)`，不是学习事实；③ 派生索引两张表是 `knowledge_node` 的缓存，恒裁+重建不改变知识事实；④ DB schema 69 未改（无新表/列/迁移）；⑤ 旧包 v1/v2 缺 `selection` 读作全量，旧账本/投影的固定输入重放逐位不变；⑥ KD-55 只影响 DOCX 读图是否 fail-closed，不进入任何算法输入。**若将来 `selection` 被用于学习算法或派生索引参与数值判定，必须重新评估 bump** | 计划 `:18-43`；设计 §3.2/§9.7；实现 `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/backup/AndroidBackupRepository.kt:184-188,218-243,1196-1271,1287-1367`、`RestoreIndexRebuild.kt:144-229`、`core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/DocxImageSource.kt:45-94` | JVM 工件：`SmbkArchiveCodecTest` 30/0、`DocxImageSourceTest` 8/0、`DocxMistakeRendererTest` 17/0；`BackupSelectionRestoreInstrumentedTest` 当前 8 例、D1 材料 7/7（最终树未复跑） | ➖（零 bump，不触发重放） |

> **本批非算法常量/合同追溯**：`allowedMasks={9,13,15}`、`closureDigest` v2、`formatVersion=3`、`selection.schemaVersion=2`、
> `closureVersion=2` 均只作用于 `.smbk` 归档的写入/读取，不改变学习投影、复习计划、掌握度或预算。`trimmedTableCount` 是报告字段，
> 不是算法输出。KD-55 的 `expectedByteSize` 是有界读入上界，不是算法参数。批次 18 记录见
> `docs/research/2026-10-23-batch-18-6c2-record.md`。

### 3.29 批次 19 · 5B-G6 考前扩容爬升/薄弱优先 + G8 回收域层半边 + 6C-2 加固（2026-10-24）：DB schema 69 不动、零版本 bump、新常量 PENDING_CALIBRATION 登记

> 编号说明：本条由批次 19 占用。`STUDY_DATABASE_VERSION` 维持 **69**（T5 零 schema，`schemas/` 止于 69.json）；
> 请求 schema 维持 **18**；`ReviewPlannerV2.VERSION="review-planner-v4"` **未 bump 且该文件零改动**（错题排程器
> 完全未触碰）；`LearningCoreVersions.*`（投影/证据/归属/账本）全部未 bump。本条登记的不是算法版本变更，而是
> **新能力常量的落账**（G6 爬升幅度）与零 bump 判据。

| 版本串 | 变更 | 公式/口径 | 数据来源 | 测试证据 | archive |
|---|---|---|---|---|---|
| 新常量 `AlgorithmConstants.ReviewScoring.EXAM_CAPACITY_RAMP_MAX_EXTRA_FRACTION = 0.5`（`AlgorithmConstants.kt:148`，KDoc 标 **PENDING_CALIBRATION**）；消费点 `StudyReviewPlannerService.examCapacityBudgetSeconds`（:140-174）：窗口内预算 = 基础 ×（1 + 0.5 × ramp）；窗口常量 `EXAM_RAMP_DAYS = 14`（`StudyReviewPlannerService.kt:800`，复用插眼 1 已裁默认窗口）；3E 扫描到期压力接既有 EXAM 通道（:310-330 读门、:383-397 `maxOf(ramp, scanBackedDuePressure(...))` 仅 ramp>0 生效，复用 `EXAM_WEIGHT` 不加新权重） | 5B-G6 排程半边：考试窗口内每日复习时间预算随临近**线性放大**（考试当天 +50%、窗口边缘 0）；无考试日/窗口外**逐位不变**（`ramp<=0.0 return baseBudgetSeconds` 短路、扫描读门 `any{ in 0..EXAM_RAMP_DAYS }` 为 false→emptyMap、候选 examPriority 在 ramp==0 时原样返回） | **PENDING_CALIBRATION**：0.5 是起步占位量级、**不是校准值**——插眼 1（2026-09-25）只裁形状与窗口（重要性 × 线性衰减、窗口默认 14 天/范围 1–60）与重要性档（高/中/低=1.0/0.7/0.4；`ExamCalendarEntry` 尚无 importance 字段未接线，当前全按高档 1.0）；幅度无外部先例（Deckline/Deadline2 只做每日目标层不动预算），待 **Wave X 离线模拟校准**，改数属产品裁定面。零 bump 判据：无考试日/窗口外输入的计划逐位不变（结构保证 + 指纹级测试断言 planFingerprint 相等、timeBudget 均 1200、扫描读计数 0）；窗口内预算变化是**新能力**（此前预算恒定），不改变任何既有输入的历史重放输出；预算只影响排程建议、不进投影/证据/账本 | 计划 `.zcode/plans/plan-2026-10-23-batch-19-6c2-closeout-5b-halves.md:28-31,56-61`；实现 `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/StudyReviewPlannerService.kt:123-174,310-330,383-397,800`、`core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/AlgorithmConstants.kt:131-148`；决策台账「批次 19」小节（G6 常量登记） | **无 Gradle 执行证据**：`StudyReviewPlannerExamWindowTest`（六组用例）在树但受 core:data 测试编译红连坐从未运行、修复后未复跑（单测门第 3 轮 exit=1）——执行 UNVERIFIED；不变性以结构读码闭合（复核发现#3 逐路径核对）。G6 关联 3E 侧 `KnowledgeReviewQueueTest` 22/0（门禁轨 2026-10-24 定向实跑） | ➖（无既有输入的输出变化，不触发重放） |
| `PaperAnswer*` 五件（Sheet/KeyChain/RecycleJudge/EvidenceFeedback/SheetRecycler）+ `InMemoryPaperAnswerRecycleStore`：**零 DB schema**、零 UI、生产零消费 | 5B-G8 回收域层半边：答卷身份幂等键（指纹+题+学习日）、出卷答案键链模型、客观题本地判纯函数（键不可信→`PendingVerification` 不写数值掌握）、LOCAL_CHECKED 回灌端口（幂等义务在契约 KDoc；判词通道默认抛 UnsupportedOperationException） | **不进入任何算法公式**：域模型+纯函数+接口+内存仓储（`putIfAbsent` 原子去重），无投影/计划/打分读取口；键信任判定复用 `core:model` 的 `answerKeyTrustDecision` 单源；真实落库批次若取 schema 70 须另行登记 | 计划 `:63-69`；实现 `core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/PaperAnswer*.kt`（5 件）、`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/InMemoryPaperAnswerRecycleStore.kt` | 37 用例（8+11+6+5+7）在树、断言实质（复核逐项核对），**无 Gradle 执行证据**（UNVERIFIED）；接线批次必带端口并发幂等验收 | ➖（无生产消费，不触发重放） |
| 6C-2 加固（批次 18 遗留五项）+ KD-61 quiesce：写侧拒负 rowCounts、`BackupSelectionSummary.assetPolicy`、deleteAllData 清标记、S3 软引用重查（`RestoreSoftReferenceRecheck`）、I5 SQL 单源化（`CanonicalAssetReferenceSites`） | 备份归档合同内的加固，不触 `formatVersion=3`/`selection.schemaVersion=2`/`closureVersion=2`（批次 18 已登记） | 全部为校验/清理/显示面：不进入投影/计划/打分/校准公式；`assetPolicy` 是 manifest 字段的摘要透传 | 批次 19 记录 `docs/research/2026-10-23-batch-19-record.md` §2.3 | 设备实跑：备份回归四类 **56/56**（EXIT=0；批次 18 的 53 例不回退+T2 新增 3 例）；core:data 四个 JVM 测试类无 Gradle 执行证据（编译红连坐，修复后未复跑） | ➖（归档合同不变，不触发重放） |

> **本批非算法常量追溯**：`EXAM_CAPACITY_RAMP_MAX_EXTRA_FRACTION=0.5` 是本批唯一新增数值常量，**唯一口径 =
> 代码 KDoc + 决策台账「批次 19」小节 + 本条**（Wave X 校准前不是校准值，不许顺手调整）；`EXAM_RAMP_DAYS=14` 是
> 插眼 1 已裁窗口的私有伴生复用，非新裁定。批次 19 记录见 `docs/research/2026-10-23-batch-19-record.md`。

### 3.30 批次 20 · 零 bump 判据登记（2026-10-25）：DB schema 69 / 请求 schema 18 / ReviewPlannerV2 / LearningCoreVersions 全部未动，本批无任何算法版本变更

> 编号说明：本条登记批次 20（4E WG-02/03/10 + JS 地基 + 五研究线 + beta 准备件，记录
> `docs/research/2026-10-25-batch-20-record.md`）的**零 bump 判据**——本批不 bump 任何版本串，落账原因如下。

| 版本串 | 变更 | 公式/口径 | 数据来源 | 测试证据 | archive |
|---|---|---|---|---|---|
| `STUDY_DATABASE_VERSION` 维持 **69**（本批零 schema：`core/database/schemas/` 无 70.json、git status 空，记录员复测）；请求 schema 维持 **18**（`ModelTasks.kt:546-547` 未动）；`ReviewPlannerV2.VERSION="review-planner-v4"` 未 bump 且该文件 clean；`LearningCoreVersions.*` 全部未 bump；`AdaptiveQuestionSelector` 本批零改动（R5 G5 只研究+提案） | 批次 20 的算法面新增均为**只读视图/端口/文档模型**，不进任何投影/计划/打分/校准公式：① T9 `LongRangeSampleLedger`（合格长程样本台账只读分类视图：分类器判据逐字复用 `predictableSampleCount` 同口径并有等价对拍用例 `LongRangeSampleLedgerReadTest.kt:68` 钉住；400 门只引用常量 gateFloor=MIN_SAMPLES_FOR_FITTING 不做达标判定；零 UI 零启用开关、生产零消费）；② T4 `VisualExecutionEngine` 执行端口（纯 JVM 端口+fake+17 契约用例，非算法组件、零依赖引入）；③ T2 `CapturedFigureReference`（题目文档的类型化图引用模型，`@EncodeDefault(NEVER)` 保证空引用不落键、旧文档 decode→重算指纹逐位稳定——不改变任何既有投影/排程输入）；④ T3/WG-03 净化续跑改造（clean_redraw_task 状态机扩 NO_FIGURE/AWAITING_VERIFICATION，DB 列复用既有 status、零 schema） | 零 bump 判据：无任何版本串变更；无任何既有输入的历史重放输出变化（T9 只读无写口、T4 无生产消费、T2 指纹兼容有测试钉住、T3 状态机只影响生图任务生命周期不进排程/投影公式）；T10 设备用例 1 绿 2 红（KD-64）暴露的是 `NewIntroductionPolicy` 既有缺陷，非本批算法改动 | 批次 20 记录 §3.2/§3.3/§3.4/§3.9/§3.10、§6；复核 #16/#22（指纹兼容、G5 零改动）；KD-64/KD-65 登记 | T5 fake 机制测试 18 passed（复核实跑 pytest）；JVM/设备执行证据归门禁转录（记录员未复跑 Gradle/设备）；`CapturedFigureReferenceTest`（core:model）14 例、`LongRangeSampleLedgerReadTest` 等在树 | ➖（零 bump，无既有输入的输出变化，不触发重放） |

> **本批非算法常量追溯**：T8 的 `ndk.abiFilters=arm64-v8a+x86_64`（`app/build.gradle.kts:39`）是构建打包配置，
> 与学习算法无关（影响=放弃 32 位设备安装面）；T6 诊断账本三重封顶（6 文件×64KiB×300 条）是运维参数；
> 两者均不登记为本表算法变更。R5 的 Elo/探索额度等提案数值**未实现**（待用户裁定），不存在常量落账。

### 3.31 批次 21 T2 · G5-A 双端口（2026-10-25）：新增 `PREDICTOR_ELO="predictor-elo-v1"`，其余零 bump；超参逐项 PENDING_CALIBRATION

> 编号说明：本条由批次 21 T2（计划 `.zcode/plans/plan-2026-10-25-batch-21-kd64-g5a-quickjs.md` §2 的 T2；
> 主会话裁定 1 = G5 选项 A）占用。`STUDY_DATABASE_VERSION` 维持 **69**（零新表、零迁移）；请求 schema 维持 **18**
> （零 ModelTaskKind、零提示词、零请求 schema）；`LearningCoreVersions` 仅**新增** `PREDICTOR_ELO`，
> `PROJECTOR`/`EVIDENCE`/`ATTRIBUTION`/`LEDGER`/`SELECTOR`/`SELECTOR_COMPOSITE` 全部未 bump；
> `AdaptiveQuestionSelector` 的选择判据/走廊未改（fail-closed 语义原样）；决策对象新增 `predictionModelVersion` 审计字段（见 §5 #18 复核修正），不属于算法判据变化、不 bump 版本串。

| 版本串 | 变更 | 公式/口径 | 数据来源 | 测试证据 | archive |
|---|---|---|---|---|---|
| 新增 `LearningCoreVersions.PREDICTOR_ELO = "predictor-elo-v1"`（`LearningCoreVersions.kt`，`PREDICTION_INTERVAL` 同级）；**不进** `SELECTOR_COMPOSITE` | G5-A 预测端口 `AdaptiveCorrectnessPredictor` + `EloCorrectnessPredictor`（`core/domain`，移植 proso-apps `PriorCurrentPredictiveModel`，MIT）与读侧装配器 `AdaptiveSelectionAssembler`（`core/data/study`） | **点预测**：`p = guess + (1−guess)·sigmoid(skill)`，`guess = 1/选项数`（选项数缺失/≤0 → guess=0，修正关闭）；`skill` 两分支——无历史 `prior − difficulty`，重答 `current_skill + timeShift/max(seconds_ago, 0.001)`。**更新**：局部技能每次 `current += (pfaeGood|pfaeBad)·(result−p)`（首答基线 = prior − difficulty）；首答门 `prior += α(u)·residual`、`difficulty −= α(i)·residual`，`α(n)=eloAlpha/(1+eloDynamicAlpha·n)`，u=`user_first_answers`（该学习者已首答的节点数，pre-increment）、i=`item_first_answers`（该节点被首答次数，单学习者恒 0）。**区间**：`h(n)=clamp(0.5/√(1+n), 0.05, 0.5)`，界 = `roundTo(6)(p±h)` 夹到 [0,1]（n=0 → 全宽 → 走廊必拒）。**降级**：缺/≤0/倒挂的上次时间 → 先验分支（不猜插值）；GAP/CONFLICT/读失败 → 候选无预测。**全部超参 PENDING_CALIBRATION**（proso 生产值 0.8/0.05/3.4/0.3/80.0；h0=0.5、24h 有效期为移植补全的新判据） | 公式权威 = proso-apps 源码 https://github.com/adaptive-learning/proso-apps/blob/master/proso/models/prediction.py:164-236,353-364（MIT，2026-10-25 `gh api` 实拉全文核对）；移植口径 `docs/research/2026-10-25-elo-predictor-spec.md` §1/§4；实现 `core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/EloCorrectnessPredictor.kt`、`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/AdaptiveSelectionAssembler.kt` | **执行证据（非 Gradle 直跑）**：`python tmp/verify_batch21.py` → 69/69（工单）；记录员实读 XML：`EloCorrectnessPredictorTest` 14/0、`AdaptiveSelectionAssemblerTest` 10/0、`AdaptiveQuestionSelectorTest` 21/0（2026-10-26 05:20–05:22）。Gradle 单测门 `exit=0` 由主会话脚本执行（工单转录；记录员未跑 Gradle）。用例：`core/domain/src/test/.../EloCorrectnessPredictorTest.kt`（公式金样/确定性/降级/走廊边界/O(n) 比例探针/超参防误用）、`core/data/src/test/.../AdaptiveSelectionAssemblerTest.kt`（通道过滤/修正链/GAP/候选装配/缓存透明/取消传播）。金样值由 JVM Temurin 21 `jshell` 按同公式算出后硬编码（1e-9 容差） | ➖（读侧派生：状态是台账前缀纯函数，零新表、零写入、不进投影快照——无既有输入的重放输出变化，不触发重放） |

**两处对 R4 规格的显式偏差（以主源为准，按全局规则 8 记录）**：

1. **首答 α 的两个计数**：R4 §4.1 规则 3 草拟"user/item 合并为节点级一个 n（数学等价）"。主源实测为**两个独立计数分别
   作用 prior 与 difficulty**（`prediction.py:229-236`）；单学习者下 item 计数在首答时恒 0、user 计数随已首答节点数增长，
   两者只在第一个节点相等——"数学等价"前提不成立。实现按主源两计数形态（R4 §4.1 的状态数据类本身也同时携带两个计数），
   首答步长因此逐节点衰减（0.8 → 0.8/1.05 → …）。
2. **缺/坏上次时间的降级**：proso 对 falsy `last_time` 取 `seconds_ago=315460000`（≈无回升，`prediction.py:197`）；R4 §2.2.4/§4.5
   规定更保守的**先验分支**。实现按 R4（不猜插值；时间倒挂同理，而非 proso 的 `max(seconds_ago, 0.001)` 巨幅回升）。

**零 bump / 不进 COMPOSITE 判据**：Elo 状态是读侧纯折叠，无持久投影面，版本不匹配不存在"全量重放"消费者；预测身份随
`PredictedCorrectnessInterval.modelVersion`（= `PREDICTOR_ELO`）逐区间携带。`SELECTOR`/`SELECTOR_COMPOSITE` 的分量都是持久投影/
判据版本串，本批选择器零改动、无输出变化 → 不 bump（判定已留痕于 `LearningCoreVersions.kt` 的 `PREDICTOR_ELO` KDoc）。

**登记项**：R4 §4.3.3 要求的决策台账登记（proso 超参 PENDING_CALIBRATION + §2.2.5 时长扩展登记项）已由本批记录员在
`docs/agent-first-refactor-decisions-2026-09-23.md` 的"批次 21 主会话四项裁定"小节折入（引计划 §1）。
跨会话复用旧题面的候选池语义仍为 R5 §5 未裁项（装配器本批只装当次会话池）；装配器候选字段映射（错题 `contentVerified=false`、
`targetKind=FOUNDATION`、`informationValue=0.0`）是保守读侧映射，不是算法数值，随生产调用点接线批次一并复核。

**与 AUD-17 的口径隔离（复核发现 #25）**：Elo 有效流消费全部已接受 `knowledge-quiz` 事件（含首答、同日重答、重答；只按
VOID/REPLACE 折叠），而 AUD-17 可预测长程样本排除 `REVOKED_OR_OBSERVED`/`MODEL_JUDGED`/`REVEAL`/`FIRST_OF_CARD`/
`SAME_DAY_REPEAT`。两者不是同一台账、同一分母；Wave X 不得把 Elo 状态当 AUD-17 样本使用，也不得把 Elo 预测效果与
AUD-17 校准结果互相替代。

> **本批非算法常量追溯**：`PREDICTOR_ELO="predictor-elo-v1"` 是本批唯一新增版本串；`EloHyperparameters.PROSO_PRODUCTION_DEFAULTS`
> 七个字段全部标 PENDING_CALIBRATION，**唯一口径 = 代码 KDoc + R4 §4.1/§4.4 + 本条**（Wave X 校准前不是校准值，
> 不许顺手调整；校准改值必须 bump `PREDICTOR_ELO`）。装配器的 `QUIZ_REVEAL_STATE_HIDDEN="HIDDEN"` 是既有列值字面，
> `saved-question:` 前缀复用 `StudyReviewPlannerService` 既有映射，均非算法参数。

## 4. 谁在什么时候写这一行

- **每次 bump 的同一个提交里**（不是事后补）：改常量/公式的那次改动，连同本表的行一起提交；
  没登记 = 改动不完整。
- 复核入口：`git log --follow docs/research/algorithm-version-ledger.md` 能读出"这个版本串是怎么来的"。
- 与 `projection_archive` 的关系：本表说"为什么改"，archive 存"改之前的旧值"。两者合起来才构成
  "可逆"：只有清单没有归档 = 回不去；只有归档没有清单 = 不知道为什么要回去。
