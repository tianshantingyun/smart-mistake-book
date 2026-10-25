# G5 自适应选题 API 扩展形态提案（R5，只研究+提案，不实现）

- 日期：2026-10-25（批次 20 研究轨 R5）
- 状态：**提案，待用户裁定**。本文件不构成实现承诺；G5 接线（含本提案任何选项）均属 5B 阶段范围，API 扩展口径为登记在案的待裁项（决策台账 `docs/agent-first-refactor-decisions-2026-09-23.md:2078`"需另裁 API 扩展"；`docs/research/2026-10-11-new-stages-spec.md:89`"G5 的 API 扩展口径（待裁）"）。
- 检索通道：`gh search` / `gh api`（已认证）+ `curl`（CRAN/raw.githubusercontent）+ WebSearch（Z.ai web_search_prime）。全部来源见 §7。

---

## 1. 本项目现状事实闭包（全部为本会话实读/实跑）

### 1.1 选择器契约已完整，生产零调用

`core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/AdaptiveQuestionSelector.kt`（本次全读，305 行）：

- 输入 `AdaptiveSelectionRequest`（`:82`）：`learnerSnapshot`、`targetKnowledgeNodeIds`、`requestedTargetKind`、fail-closed 授权位 `userRequestedQuestion`（`:87`，学生不主动要题就不出题）、候选列表、`recentItemFamilyIds`、`deterministicSeed`、KF-16 先修稳定度表 `prerequisiteStabilityDaysByNode`（`:100`）。
- 候选 `AdaptiveQuestionCandidate`（`:54`）：`assessmentItemId`、`knowledgeNodeIds`、`itemFamilyId`、`targetKind`、`contentVerified`、`answerWouldBeRevealed`、`informationValue`、**`predictedCorrectness: PredictedCorrectnessInterval?`**、`fatigueCost`、`additionalEligibleTargetKinds`。
- 硬挑战走廊（`:244-251`）：FOUNDATION 预测正确率 **0.55–0.85**；TRANSFER/BOUNDARY/COUNTEREXAMPLE **0.35–0.75**；CALIBRATION 恒真。
- 打分（`:260-263`）：`2×informationValue + (1−|midpoint−0.7|) − fatigueCost`，FNV 种子确定性平局拆解（`:265-269`）。
- 决策族（`:19-25`）：ASK / ASK_CALIBRATION / SKIP_MASTERED_FOUNDATION / CLARIFY_OR_CONFIRM / NO_SAFE_CANDIDATE；边界注释（`:133`）"Deterministic policy boundary; model wording may consume this decision but cannot replace it"。
- 预测区间 `PredictedCorrectnessInterval`（`:27-52`）自带 `modelVersion/generatedAt/validUntil`——**设计上就预期一个上游预测器**。

**生产零调用实证**（本会话 grep）：`grep -rn "AdaptiveQuestionSelector(" core app feature --include="*.kt" | grep -v worktrees | grep -v Test` → 零命中；再按类名全量 grep，仅命中定义本体、其单测 `core/domain/src/test/.../AdaptiveQuestionSelectorTest.kt`、版本常量注释 `LearningCoreVersions.kt:115`。与审-A2 记录一致（`docs/requirements-fused-final-2026-10-11.md:157`）。

### 1.2 两个未绑定的输入端口 = 缺口本体

选择器的请求契约完整，但生产侧没有任何代码：

1. **候选装配端口**：谁把库里的题（错题/已生成的测验题）变成 `AdaptiveQuestionCandidate` 列表；
2. **预测生产端口**：谁给每个候选算 `PredictedCorrectnessInterval`。

现有系统**没有每题难度**：`ReviewDifficultyBand` 是从掌握风险派生的（`core/domain/.../KnowledgeNodeScorer.kt:136` `knowledgeDifficultyBand(masteryRisk)`）——是"该知识点对这个学生多弱"的档，不是"这道题多难"的估计。因此即便接上候选装配，`predictedCorrectness` 也无来源，走廊判据必然 fail-closed（`PREDICTION_UNKNOWN_STALE_OR_OUTSIDE_CORRIDOR`，`:195`）。

### 1.3 既有模型任务环（"任务环"）

- `ModelTaskKind` 12 种（`core/model/.../ModelTasks.kt:12-25`），每种有机器可核契约：输入字段白名单、披露集、资产策略、提示词策略版本、输出 schema+parser、能力要求、派发上限（`ModelTaskRemoteDispatchPolicy.MAX_DISPATCHES=6`，`:58`）。状态机 WAITING→QUEUED→RUNNING→STREAMING→终态（`:27-51`）。
- **与 G5 最近的任务是 `KNOWLEDGE_QUIZ`**（`core/model/.../KnowledgeQuizTasks.kt:23-96`）：输入 = 知识点 + 教材标题/内容/边界 markdown + `lastMasteryScore/lastEvidenceAt`；输出 = `questionMarkdown + choices + correctChoiceId + modelVersion`。协议侧 wire keys 三键白名单（`core/data/.../model/KnowledgeQuizWire.kt:8-36`）。
- **题面落库**：`KnowledgeQuizItemStore`（`core/data/.../study/KnowledgeQuizItemStore.kt:19-47`）复用既有 `assessment_item_snapshot` 表（呈现内容与学习证据分表的既有语义），身份 = `(learner, 会话, 知识点)`，不可变、首见即真。
- **作答证据**：quiz 作答走既有 chat evidence 台账（`KnowledgeQuizFeedbackWriter`/`KnowledgeQuizEvidenceAudit`），即 Elo 所需的"对/错 + 选项数 + 时间"原始流**已经在落库**。
- 出题范围：`StudyReviewPlannerService.quizAbleScopeNodes`（`:582`、`:691`）已按"队列范围 ∩ 有教材材料"筛可出题节点。
- 5B 规格（`new-stages-spec.md:82`）：G5 = "`AdaptiveQuestionSelector` 接入新模型任务/出题工具……出题结果走确认卡（本地动作白名单模式）"；验收门 = "G5 有生产调用点"。

---

## 2. 外部先例调研

### 2.1 catR（R，CRAN v3.17，GPL-3）——经典 CAT 的 API 分层

来源：CRAN 页 + 官方 PDF（本会话 `curl` 拉取 CRAN index.html 与 catR.pdf，`pdftotext` 抽取 nextItem 章）。

- 许可 **GPL (≥3)**，版本 3.17，CRAN 发布 2022-06-24（文档最近构建 2026-05-08；`cran/catR` GitHub 镜像 2026-09 仍有活动）。维护者 Cheng Hua；原作者 Magis/Raiche/Barrada。
- 核心 API 分三层：**`thetaEst`（能力估计）↔ `nextItem`（选题）↔ `checkStopRule`（停测）**，互不掺和。
- `nextItem(itemBank, model, theta, out, x, criterion="MFI", …, randomesque=1, random.seed, nAvailable, maxItems=50, cbControl, cbGroup)`：
  - `criterion` 14 种选题准则（MFI/bOpt/MLWI/MPWI/MEI/MEPV/KL/progressive/proportional/GDI/random…）；
  - **曝光控制 `randomesque`**：从最优 n 个里随机挑一个，配 `random.seed` 可复现——与本项目 `deterministicSeed` + `recentItemFamilyIds` 排除同型；
  - **内容平衡 `cbControl/cbGroup`**：按内容域约束可选集——与 `targetKnowledgeNodeIds` 候选过滤同型；
  - `out` = 已施测题集合——同 `recentItemFamilyIds`；
  - `progressive`/`proportional` 准则：测试早期偏随机、后期偏信息量，**受控探索**的成例（对"新题冷启动"有直接借鉴）。
- 附属 `randomCAT`/`simulateRespondents` 提供离线仿真验收 harness。

**对本项目的价值**：API 形态权威参照——"估计器与选择器分离、曝光/内容约束作为选题器正交参数、离线仿真验收"。GPL-3 不可嵌入 App（一票否决作为依赖），仅作设计引用。

### 2.2 catsim（Python，BSD-3-Clause，活跃）——组合式 CAT 组件

来源：`gh repo view douglasrizzo/catsim` + 源码树 + README（本会话实跑）。

- 许可 **BSD-3-Clause**；153 stars；**最近推送 2026-10-23**（维护活跃）。
- 架构：`src/catsim/{initialization,selection,estimation,stopping}/` 组件目录 + `simulation.py`/`engine.py`/`item_bank.py`。README 明言组件"can either be used in a standalone fashion to power other software"，用法即组合：
  ```python
  initializer = RandomInitializer(); selector = MaxInfoSelector()
  estimator = NumericalSearchEstimator(); stopper = MinErrorStopper(0.4, max_items=20)
  runner = SimulationRunner(ItemBank.generate_item_bank(100), initializer, selector, estimator, stopper)
  ```
- 仓库带 Architecture Decision Records 与仿真 notebooks。

**对本项目的价值**：第二个独立来源印证 catR 的分层（Initializer/Selector/Estimator/Stopper 正交组合）——"多独立来源一致"级证据。Python 不可嵌入 Android，仅设计引用。文档站 readthedocs 首页本会话 404，以 README+源码树替代核验（已注明）。

### 2.3 OATutor（CAHLR，JS/React+Firebase，MIT，活跃）——开放自适应辅导系统的选题器形态

来源：`gh search` + `gh api` 拉源文件（本会话实读）。

- 许可 **MIT**；271 stars；最近更新 2026-10-24。CAHLR（斯坦福分支研究组）维护；配套 pyBKT 库（MIT，290 stars，C++/Python）。
- **自适应在估计器，不在选择器**：
  - BKT 更新 `src/models/BKT/BKT-brain.js`（19 行）：`probMastery` 按对错做贝叶斯更新 + `probTransit`；
  - 选题启发式 `src/models/BKT/problem-select-heuristics/defaultHeuristic.js`（27 行）：跳过已完成/无关/已达掌握阈值的题，**在余下题中选组合掌握度最低者**，等掌握度随机平拆。
- 选题器是纯本地确定性（近乎平凡）函数；所有"智能"在每 KC 的掌握度估计里。

**对本项目的价值**：证明"选择器保持笨而确定、估计器承载自适应"是生产系统真实采用的形态；其启发式（跳过已掌握 + 选最薄弱 + 随机平拆）与本项目 `SKIP_MASTERED_FOUNDATION` + 薄弱优先 + `deterministicSeed` 结构同源。MIT 许可可自由借鉴代码形态（但 JS/Firebase 栈不可直接复用）。

### 2.4 proso-apps（adaptive-learning org，Python/Django，MIT）——Elo 型在线难度估计的生产实现

来源：`gh api` 拉取 `proso/models/prediction.py`（本会话全读）；背景论文 Pelánek (2016)《Applications of the Elo Rating System in Adaptive Educational Practice》（CAE，281+ 引用，WebSearch 定位）。

- 许可 **MIT**；生产背景：slepemapy.cz / anatom.cz（Pelánek 组的线上自适应练习系统）；仓库最后推送 **2022-11-22、2 stars——社区维护度低，按"参考实现+论文背书"对待，不作依赖**。
- 三相位端口 `PredictiveModel`：`prepare`（读环境）→ `predict`（出预测）→ `update`（写回）。核心 `PriorCurrentPredictiveModel`：
  - 状态三项：用户 `prior_skill`、题 `difficulty`、(用户,题) `current_skill`；
  - 预测：`p = logistic(prior_skill − difficulty)`，**按选项数做猜测修正**；
  - 更新：`skill += K·(result − prediction)`、`difficulty −= α(n)·(result − prediction)`，其中 **动态步长 `α(n) = 0.8/(1+0.05n)`（n=已答次数）**——新题收敛快、老题稳定，天然冷启动策略；
  - 时间衰减：久未答向 prior 技能回落（`time_shift/seconds_ago`）。
- **这正是 `PredictedCorrectnessInterval` 缺失的生产者**：输出就是 [0,1] 正确率预测；答次 n 可同时派生区间宽度（答得少→区间宽→落不进走廊→fail-closed），与既有判据语义严丝合缝。

### 2.5 候选评分表（按研究纪律逐项核对）

| 候选 | license | 维护度 | 与本项目兼容 | 结论 |
|---|---|---|---|---|
| catR | GPL-3（**一票否决作依赖**） | CRAN 3.17（2022），稳定 | R 不可嵌入 | **设计引用**（API 分层/曝光/内容平衡/progressive 探索） |
| catsim | BSD-3 | 活跃（2026-10） | Python 不可嵌入 | **设计引用**（组合式分层第二来源） |
| OATutor | MIT | 活跃（2026-10） | JS/Firebase 栈不复用 | **形态引用**（笨选择器+估计器承载） |
| proso-apps | MIT | 低（2022 停更） | Python，但核心 ~30 行可移植 | **算法移植源**（Elo 三相位，注明出处） |
| pyBKT (CAHLR) | MIT | 活跃 | C++/Python | 备选估计器（重于 Elo，暂不取） |
| open-mike/elo-kotlin | MIT | 0 star、2021 停更 | Kotlin 但非教育场景、无难度维度 | 弃（质量不足） |
| Kotlin/JVM CAT/IRT 库 | — | **未找到**（3 轮关键词：kotlin irt / kotlin adaptive testing / elo kotlin） | — | 无现成依赖，自移植是唯一路径 |

**结论：不存在可直接引入的 Kotlin/Android 自适应选题依赖。** "抄高质量开源"在本题上的落点是：API 形态抄 catR/catsim 的分层、估计器抄 proso-apps 的 Elo（MIT，可移植需注明）、选择器保持本项目既有形态（与 OATutor 同构）。

---

## 3. API 扩展形态选项（三选一或多阶段组合；**均待用户裁定**）

选项都遵守两条硬边界：① 选择器一行不改、走廊与 fail-closed 判据不动（`AdaptiveQuestionSelector.kt:133` 注释边界）；② 不新增隐私面、不引外部服务（批次纪律）。

### 选项 A —— 本地双端口：读装配器 + Elo 预测器（零模型任务扩展）

**形态**：G5 的"API 扩展"= 在 core:data 读侧新增两个纯本地端口，`AdaptiveSelectionRequest` 从此有生产装配者：

```
core/domain:  interface AdaptiveCorrectnessPredictor {            // 端口
                fun predict(candidate): PredictedCorrectnessInterval
                fun update(answer): PredictorState                 // 纯函数+台账回放
              }
              class EloCorrectnessPredictor(...)                   // proso 式三状态实现
core/data:     class AdaptiveSelectionAssembler(...)               // 读侧装配：
               // 候选 = assessment_item_snapshot(知识点复习题, KnowledgeQuizItemStore 既有写口)
               //      + 既有 itemFamily 映射的错题(StudyReviewPlannerService.kt:373 同源)
               // 预测输入 = chat evidence 台账既有作答流(对/错/选项数/时间)
               // 快照/先修稳定度 = 既有 LearnerSnapshot 读面 + KnowledgePrerequisiteReader
```

Elo 状态可**由台账惰性重放派生**（纯函数、确定性、零新表——符合本项目"固定账本重放逐位不变"纪律），或落一张小状态表（若裁定要求增量更新）。

- **来源**：OATutor（§2.3，笨选择器+估计器承载）；proso-apps `prediction.py`（§2.4，Elo 数学，MIT 可移植需注明）；catR（§2.1，估计器/选择器分离）。
- **改动面**：core/domain 新增 1-2 个纯类+单测；core/data study 包新增装配器+单测；知识点复习会话 VM 一处调用点。**零 ModelTaskKind、零提示词、零请求 schema**；DB 视惰性/增量裁定为 0 或 1 张表。
- **任务环关系**：模型仍只是内容生产者（`KNOWLEDGE_QUIZ` 原样）；选择器决定"要不要把已生成的某道题端给学生"；`NO_SAFE_CANDIDATE` 时退化为现状（新起一次 KNOWLEDGE_QUIZ 会话出题，路径不变）。选择决策不经模型——与":133 模型措辞可消费决策但不可替代决策"的边界完全一致。
- **消灭的具体失败**：① G5 验收门"G5 有生产调用点"（new-stages-spec.md:91）无实现路径；② KF-16 读取侧接线收尾项悬置（台账 :2078）；③ 系统无每题难度→走廊判据永不满足→选择器永远空转。
- **弱点（如实）**：候选池冷启动小（每 (learner,会话,知识点) 一题快照），早期走廊常 fail-closed——但 fail-closed 即设计默认，行为不劣于现状。proso 超参（0.8/0.05/猜测修正）是别家生产值，**未在本项目数据上验证**（真实效果 UNVERIFIED，Wave X 校准线）。

### 选项 B —— 决策透传入 KNOWLEDGE_QUIZ：模型按走廊瞄准出题（输入契约扩展）

**形态**：扩展 `KnowledgeQuizInput`，把选择器当次决策的目标透传给出题任务：`targetKind`、走廊上下界、`avoidItemFamilyIds`、当前预测区间；提示词要求模型按目标难度档出题；输出契约不变。

- **来源**：**未找到先例**。catR/catsim/OATutor/proso 四个系统全部把难度放在题库参数或估计器里，没有一个让"内容生成器瞄准难度"。LLM 难度瞄准能力在无 key 环境下不可测（真实质量 UNVERIFIED，PRD-09 同款约束）。
- **改动面**：core/model 输入契约 + 提示词策略版本 bump + wire/parser 白名单核对 + 请求 schema 19。
- **任务环关系**：每次提问 = 一次模型派发（预算 6 内占 1）；走廊核对仍是事后（预测来自本地估计器，非模型自评）——出题落不进走廊即白烧一次派发。
- **评估**：证据基础最弱的选项。单独采用不推荐；其输入扩展字段在选项 C 中作为可选子集保留。

### 选项 C —— 闭环双阶段：A 的双端口 + 「无安全候选」触发生成补池、选择器终审

**形态**：选项 A 全量 + 一个编排服务：当用户要题且选择器返回 `NO_SAFE_CANDIDATE`（池空/全出走廊）时，按 `requestedTargetKind` 与避重家旅生成新题（复用 KNOWLEDGE_QUIZ，可带 B 的走廊提示字段），新题入池后**重跑选择器终审**；新题按 catR `progressive`/`randomesque` 先例给受控探索额度（如前 k 答给宽区间/暂免走廊，k 由裁定定）。出题结果走确认卡（本地动作白名单模式，5B 规格 :82 原文要求）。

- **来源**：catR `progressive`/`randomesque`（受控探索、曝光控制）；catsim 组合式组件；proso 动态步长（新题快速收敛）；OATutor 确定性选择器。B 的透传字段为可选子集。
- **改动面**：A 的全部 + 编排服务 + 输入扩展（如采 B 字段）+ 确认卡 UI 接线 + 请求 schema bump。5B 阶段量级。
- **任务环关系**：任务环新增一个"生成补池"触发器角色；模型派发仅在本地判定池不足时发生（fail-closed 授权语义不变：学生不主动要题，编排器不生成）。证据回流复用 quiz 既有作答链。
- **消灭的具体失败**：A 的冷启动池小问题（生成补池）；5B "接入新模型任务/出题工具"的完整语义。
- **弱点**：改动面最大；探索额度 k 与走廊豁免规则是新判据，必须先裁后写（防"为过门放宽走廊"——本提案不预设数值）。

---

## 4. 推荐（待用户裁定）

**推荐分阶段：先 A 后 C，B 不单独采用。**

1. **A 是唯一同时满足三件事的最小形态**：5B 验收门"G5 有生产调用点"、KF-16 收尾项闭环、为走廊判据提供合法的预测来源（Elo 由既有台账派生，零隐私面、零外部服务、可确定性重放）。它不触碰模型任务契约，失败模式全部 fail-closed 到现状行为。
2. **C 是 5B 完整形态的目标态**，以 A 已上线的预测器与装配器为地基，追加生成补池与确认卡；探索额度等新判据届时单独呈裁。
3. **B 单独不推荐**：无开源先例支撑"生成器瞄准难度"（§3B 如实标注未找到），且真实 Provider 无 key、难度瞄准质量不可验证；每题一次派发的预算占用也不如 C 的"仅补池时派发"。

裁定要点（需用户定的三件事）：① 采 A / C / 其他；② Elo 状态取"台账惰性派生"还是"落表增量"；③ 若采 C，新题探索额度与走廊豁免规则的数值口径。

---

## 5. 待验证清单

- proso Elo 超参（0.8 / 0.05 / 猜测修正 / time_shift=80s）来自其生产配置，**未在本项目数据分布上验证**——单一来源，落地前需用真实作答台账回放校准（Wave X 线）。
- 选项 B 的"模型能按难度档出题"：**未找到开源先例**；本仓真实 Provider 无 key，UNVERIFIED。
- OATutor 的 BKT 参数与 MASTERY_THRESHOLD 配置值未逐项核对（本会话只核了 `BKT-brain.js` 更新公式与 `defaultHeuristic.js` 选题启发式）。
- catsim 文档站首页本会话 404；其组件结构以 README+`src/catsim/` 源码树核验（单通道降级，已注明）。
- 选项 A 的候选池冷启动规模：每知识点可复用题快照的实际数量**未做数据面统计**（需设备/真实库查询，非本轨范围）。
- `assessment_item_snapshot` 复用为 G5 候选池时，其"会话分量身份"（KnowledgeQuizItemStore.kt:33-35）是否允许跨会话复用旧题面，属语义裁定项，提案未代裁。

## 6. 本提案未做（范围声明）

- 未实现任何代码、未跑任何 Gradle 门（纪律：门禁由脚本串行执行）。
- 未改决策台账/master line——裁定权在用户；若裁定通过，5B 批次再登记。
- 未代定探索额度、Elo 落表形态等数值/口径（§4 裁定要点）。

## 7. 来源清单

项目内（本会话实读）：
- `core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/AdaptiveQuestionSelector.kt`（全读）
- `core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/ModelTasks.kt`、`KnowledgeQuizTasks.kt`
- `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/model/KnowledgeQuizWire.kt`
- `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/KnowledgeQuizItemStore.kt`
- `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/StudyReviewPlannerService.kt`（:373/:515/:582/:691）
- `core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/KnowledgeNodeScorer.kt`（:136 难度档派生）
- `docs/agent-first-refactor-decisions-2026-09-23.md:2076-2078`、`docs/research/2026-10-11-new-stages-spec.md` §5B、`docs/requirements-fused-final-2026-10-11.md:157`（审-A2）

外部（本会话实拉）：
- catR：https://cran.r-project.org/web/packages/catR/index.html ；官方 PDF（nextItem 章）https://cran.r-project.org/web/packages/catR/catR.pdf ；镜像 https://github.com/cran/catR
- catsim：https://github.com/douglasrizzo/catsim （BSD-3；README+src/catsim 源码树）
- OATutor：https://github.com/CAHLR/OATutor ；BKT 更新 https://github.com/CAHLR/OATutor/blob/main/src/models/BKT/BKT-brain.js ；选题启发式 https://github.com/CAHLR/OATutor/blob/main/src/models/BKT/problem-select-heuristics/defaultHeuristic.js
- proso-apps：https://github.com/adaptive-learning/proso-apps ；Elo 实现 https://github.com/adaptive-learning/proso-apps/blob/master/proso/models/prediction.py
- Pelánek (2016) 论文：https://www.fi.muni.cz/~xpelanek/publications/CAE-elo.pdf
- pyBKT：https://github.com/CAHLR/pyBKT
