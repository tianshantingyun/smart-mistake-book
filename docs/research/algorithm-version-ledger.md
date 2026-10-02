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


## 4. 谁在什么时候写这一行

- **每次 bump 的同一个提交里**（不是事后补）：改常量/公式的那次改动，连同本表的行一起提交；
  没登记 = 改动不完整。
- 复核入口：`git log --follow docs/research/algorithm-version-ledger.md` 能读出"这个版本串是怎么来的"。
- 与 `projection_archive` 的关系：本表说"为什么改"，archive 存"改之前的旧值"。两者合起来才构成
  "可逆"：只有清单没有归档 = 回不去；只有归档没有清单 = 不知道为什么要回去。
