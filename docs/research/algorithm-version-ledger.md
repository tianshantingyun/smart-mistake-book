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
| `LearningCoreVersions.PROJECTOR` | `projector-v7` | **`projector-v8`** | **Wave 2**：w20 个性化接线 + 学习日界 04:00（两处数值口径变更合并为一次 bump） | ✅ 已用（Wave 2） |
| `LearningCoreVersions.PROJECTOR` | `projector-v8` | **`projector-v9`** | Wave 3 批次 1：β-二项表示 + 知识点记忆卡 + E 判据（`SKIP_POLICY` 同批 v3 → v4） | ✅ 已用（Wave 3 批次 1） |
| `LearningCoreVersions.PROJECTOR` | `projector-v9` | **`projector-v10`** | Wave 3 批次 3（P5）：首答不计跨日连击 + 曝光计数每条 +1（两处投影输出修正） | ✅ 已用（Wave 3 批次 3） |
| `LearningCoreVersions.REVIEW_PLANNER` | `review-planner-v6` | **`review-planner-v7`** | Wave 3 批次 3：V1 排程曲线 FSRS 唯一化 + 接投影同源个性化 decay（**curve 归属记在本串**：`FORGETTING_CURVE` 保持 curve-v3，投影侧语义未变） | ✅ 已用（Wave 3 批次 3） |
| `ReviewPlannerV2.VERSION`（V2 的有效串，独立于 REVIEW_PLANNER） | `review-planner-v2` | **`review-planner-v3`** | Wave 3 批次 3：KF-08 前置门改硬过滤；`PLAN_FINGERPRINT_SCHEMA_VERSION` 同批 `canonical-v7 → v8` | ✅ 已用（Wave 3 批次 3） |
| `LearningCoreVersions.REVIEW_PLANNER` | `review-planner-v7` | **`review-planner-v8`** | 批次 R（裁决 28 读侧语义闭合）：V1 跳过/风险判据改 `effectiveStatus` 现算（45 天窗并入出口；锚点 = 最后作答，双钟关闭）；V1 指纹 `canonical-v5 → v6` 同批 | ✅ 已用（批次 R） |
| `ReviewPlannerV2.VERSION` | `review-planner-v3` | **`review-planner-v4`** | 批次 R：V2 掌握风险分支改 `effectiveStatus`（KF-16 先修压制接线）；指纹 `canonical-v8 → v9` 同批 | ✅ 已用（批次 R） |
| `LearningCoreVersions.SELECTOR` | `selector-v6` | **`selector-v7`** | 批次 R：`requiresCalibration` 的状态/新鲜度门槛改 `effectiveStatus` 现算 | ✅ 已用（批次 R） |
| `LearningCoreVersions.EVIDENCE` | `evidence-v4` | `evidence-v5` | Wave 1 证据定价语义（`persistedAssistance` 链 + 看答案口径；裁决 26「独立看答案 lapse KC 卡」候落此号段） | 未用（W1 无 bump，见 §3.4） |

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

## 4. 谁在什么时候写这一行

- **每次 bump 的同一个提交里**（不是事后补）：改常量/公式的那次改动，连同本表的行一起提交；
  没登记 = 改动不完整。
- 复核入口：`git log --follow docs/research/algorithm-version-ledger.md` 能读出"这个版本串是怎么来的"。
- 与 `projection_archive` 的关系：本表说"为什么改"，archive 存"改之前的旧值"。两者合起来才构成
  "可逆"：只有清单没有归档 = 回不去；只有归档没有清单 = 不知道为什么要回去。
