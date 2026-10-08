# 算法版本清单（Wave 0 / W0-3 · Q1）

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

## 4. 谁在什么时候写这一行

- **每次 bump 的同一个提交里**（不是事后补）：改常量/公式的那次改动，连同本表的行一起提交；
  没登记 = 改动不完整。
- 复核入口：`git log --follow docs/research/algorithm-version-ledger.md` 能读出"这个版本串是怎么来的"。
- 与 `projection_archive` 的关系：本表说"为什么改"，archive 存"改之前的旧值"。两者合起来才构成
  "可逆"：只有清单没有归档 = 回不去；只有归档没有清单 = 不知道为什么要回去。
