# 内核算法：验证结论与实施级修复计划（2026-09-28）

> **补充卷**：`docs/research/2026-09-28-kernel-scale-precision-audit.md`（五维判定 + 精确 P1–P12 +
> 灵活 F1–F8 + 高质量 Q1–Q6 + 规模化 S1–S21，编号继续 KF-35 起）。本卷含对 KF-15/对照表/KF-33
> 的三处修正声明，实施前必读。
> **路线图**：`docs/research/2026-09-28-kernel-remediation-roadmap.md`（六波次执行手册：
> 每条目七段可执行改法 + 退出门 + 研究附录 A–E 公式全量 + 已裁项汇总）。**实施顺序以路线图为准。**

- 日期：2026-09-28
- 状态：验证完成（FSRS 与 py-fsrs 逐式比对）；**34 条修复项写到可直接实施的程度**
- 上游：`docs/research/2026-09-27-kernel-deep-review.md`（四层病灶）、
  `docs/research/2026-09-25-mastery-mechanism-review.md`（阶段 0 研究）、
  `docs/agent-first-refactor-decisions-2026-09-23.md`（D1 修订：内核结构不动、表示与数值重做）
- 每项模板：现状证据 → 目标行为 → 具体改法（含完整公式）→ 投影/迁移影响 → 测试 → 风险/依赖。
- 证据等级沿用既有定义；未实测处标 UNVERIFIED。
- 阶段归属：**3A**=KF-01~06、08~28、33、34；**3B**=KF-32；**3C**=KF-07、29、30、31。

---

## 1. 正确性判定（对着权威源逐式核过）

**FSRS-6 移植逐式比对 py-fsrs `scheduler.py`（官方源码，A 级）**：

| 公式 | 判定 |
|---|---|
| 符号约定 `_DECAY=−w20`、`_FACTOR=0.9^(1/DECAY)−1` | ✓ 一致 |
| R(t,S) = (1 + FACTOR·t/S)^DECAY | ✓ |
| I(r*,S) = (S/FACTOR)·(r*^(1/DECAY)−1)，round、≥1、≤36500 | ✓ |
| 跨日答对 S′ = S·(1 + e^w8·(11−D)·S^(−w9)·(e^((1−R)·w10)−1)·HP·EB) | ✓ |
| 跨日答错 S′ = min(w11·D^(−w12)·((S+1)^w13−1)·e^((1−R)·w14), S/e^(w17·w18)) | ✓ |
| 难度 D′ = w7·D0(Easy) + (1−w7)·(D+(10−D)·(−w6·(G−3))/9) | ✓ |
| 同日 S′ = S·e^(w17·(G−3+w18))·S^(−w19)，G≥2 乘数 floor 1 | ✓ |
| 21 个默认参数 | ✓（唯一偏差 w16=1.0，理由成立） |

三类错误区分：真数学错误 1 处（Wald 套 EMA 输出）；结构设计错误 1 处（PREREQ 加性被抵消）；
接线错误（w20 梯度恒零 + 在线间隔写死默认 decay）。口径差异：官方 fuzz 我们刻意不移植（已裁：不做，裁决 15）。

---

## 2. P0 组（正确性 bug / 训练会坏）

### KF-01 · w20 梯度恒零 + 在线间隔写死默认 decay

- **现状证据**：`SchedulingReplay.predict`（`SchedulingEvaluation.kt:148`）调 `retention` 不传
  parameters → 默认 decay；`FsrsScheduleMath.intervalDays(:83)` 与 `factor(:71)` 默认参数写死
  `-DEFAULT_PARAMETERS[20]`；`FsrsMemoryUpdateModel` 的 `retention` 调用（`MemoryUpdateModel.kt:86,93`）
  同病。
- **目标行为**：拟合 loss 对 w20 有非零梯度；在线投影的 R 计算与间隔反函数使用当前个性化参数。
- **具体改法**：
  1. `FsrsScheduleMath.retention(elapsedDays, stabilityDays, decay)`——去掉默认值或改 required 参数；
     `factor(decay)` 同。
  2. `intervalDays(stabilityDays, desiredRetention, decay)` 加 decay 参数，`raw = (S/factor(decay))·
     (r*^(1/decay) − 1)`。
  3. `FsrsMemoryUpdateModel.updateMemory`：两处 `retention(...)` 传 `-parameters[20]`；末尾
     `intervalDays(...)` 传 `-parameters[20]`。
  4. `SchedulingReplay.predict`：`retention(elapsedDays, stability, decay = -parameters[20])`。
  5. `ForgettingCurve.estimateAt` 若含 FSRS 分支，同样传参（grep 全部 `retention(` 与 `intervalDays(`
     调用点，逐点显式传参，禁止再走默认）。
- **投影/迁移影响**：输出数值会变 → **必须 bump `LearningCoreVersions.PROJECTION_COMPOSITE`**，
  触发既有"版本不匹配 → 全量重放"路径（`StudyProjectionDrainer`），旧投影自动重算。
- **测试**：① 先红：固定小数据集，扰动 w20 断言 BCE loss 变化（梯度非零的回归钉）；
  ② 个性化参数与默认参数下 `intervalDays` 输出不同；③ 重放逐位一致（同一账本两次重放同结果）。
- **风险/依赖**：与 KF-04/05 同批做（同一拟合链）；改动集中在一个文件族，风险低。

### KF-02 · 看答案后答对记为独立答对 w=1.0

- **现状证据**：`StudySubmissionPreparer.kt:96` 硬编码 `revealedBeforeAnswer=false`；`:53-59` 构造
  `AssessmentSubmissionContext` 从不传 `persistedAssistance`；`MasteryEvidencePolicy` 的
  ANSWER_WAS_REVEALED 分支因此不可达。
- **目标行为**：本轮提交前该题已发生答案揭示 → 答对判 EXCLUDED（w=0、direction NONE、
  reason=ANSWER_REVEALED），答错判 INCORRECT_AFTER_REVEAL（w=0.6）。
- **具体改法**：
  1. 呈现状态查询：提交时读该 presentation 的 reveal 事实（`answer_reveal_outcome` /
     `assessment_answer_reveal_event` 已存在，`StudyAnswerRevealService` 已有落点）——
     在 `prepareChoiceSubmission` 入参加 `persistedAssistance: PersistedAssessmentAssistance?`。
  2. 上游调用点（`submitChoice`/`submitReviewChoice`，`RoomBackedStudyExperienceRepository.kt:519,570`）
     在组装提交前查询"该 presentation 是否有 reveal 行"，有则传入；同时 `revealedBeforeAnswer`
     取真值落 attempt_event。
  3. 模型输入字段若新增（persistedAssistance 进 submission 形状）→ 指纹纪律：
     strip 空载体 + bump schema（`bf8be888` 教训，4 条必测）。
- **投影/迁移影响**：attempt_event 已有 `revealed_before_answer` 列（`LearningEntities.kt:353`），
  无 schema 变化；投影行为变化 → bump `PROJECTION_COMPOSITE`。
- **测试**：① 先红：看答案后答对 → 断言 EXCLUDED（w=0）而非 INDEPENDENT；
  ② 看答案后答错 → INCORRECT_AFTER_REVEAL w=0.6；③ 未看答案 → 行为不变；
  ④ 旧行读取不抛完整性异常；⑤ 指纹四用例（旧行/新行/空载体/升级）。
- **风险/依赖**：与 KF-03（语义统一）同批；依赖 M2 的 persistedAssistance 接线方向。

### KF-03 · 看答案三义分裂 + 伪装 AGAIN 进拟合集

- **现状证据**：policy=EXCLUDED(w=0)；`StudyAnswerRevealService.kt:49-62` 落 review_log
  rating=AGAIN/weight=0 且进拟合集；投影 `LearningProjector.kt:922-923` 计 lapseCount++。
- **目标行为**：一个行为一种语义；拟合集不含 reveal 行（或含但带显式标记被排除）。
- **具体改法**（最终语义口径**已裁：B**，裁决 1；两候选的完整后果见 §4-1；以下为共同部分）：
  1. review_log 增加/复用 `source_kind` 独立值 `REVEAL`（或在 `SchedulingEvaluation.fittableReviewSamples`
     排除 `source_kind==REVEAL` 与 rating 由 reveal 产生的行）。
  2. 投影侧按裁定语义实现：候选 A（不计证据+记忆侧降 S′ 不进 lapseCount）：
     `projectAnswerReveal` 输出 outcome=ANSWER_REVEALED 但 `isRetrievalFailure=false`；
     候选 B（评 AGAIN 计遗忘失败）：保持现状但把 policy 侧也改为"计一次失败"——两候选二选一。
  3. `FsrsEvidenceRatingMapper` 的 ANSWER_REVEALED→AGAIN 仅在候选 B 时保留。
- **投影/迁移影响**：语义变化 → bump `PROJECTION_COMPOSITE`。
- **测试**：两候选各自的新旧行为用例；拟合集排除 reveal 的回归（reveal 行不再改变拟合参数）。
- **风险/依赖**：**先裁语义再实施**（§4-1）；与 KF-02/06 同批。

### KF-04 · 无采纳门 + 每次启动静默覆盖存量参数

- **现状证据**：`StudySchedulingCalibration.kt:91` 无条件 `store.setOptimizedParameters(...)`；
  拟合从 DEFAULT 冷启动（`SchedulingEvaluation.kt:529`）。
- **目标行为**：新参数仅当在同一数据上 log loss **不劣于**存量参数才写入；否则保留存量
  （官方采纳门）。
- **具体改法**：
  1. 拟合输出加 `logLoss` 字段。
  2. 写入路径：`store.optimizedParameters` 为空 → 直接写；非空 → 用同一批样本对存量参数重放
     算 loss，比较（≤ 则写，> 则保留存量并记日志/审计行）。
  3. 实现顺序注意：拟合函数需支持"对给定参数算 loss"（`bceLogLoss(SchedulingReplay.predict(history, params))`
     已具备）。
- **投影/迁移影响**：无 schema 变化（DataStore 值）。
- **测试**：存量参数更优时不覆盖；更差时覆盖；空存量直接写；三次启动稳定（不再随机游走）。
- **风险/依赖**：与 KF-05 同批；依赖 KF-01（否则 w20 维度永远不劣）。

### KF-05 · 拟合门槛 8/64 → 400 硬门

- **现状证据**：`SchedulingEvaluation.kt:596-597` `MIN_SAMPLES_FOR_FITTING=8` / `FULL_FIT=64`。
- **目标行为**：可预测样本 <400 时不拟合、用默认参数（Anki 官方硬门；实践建议 ~1000）。
- **具体改法**：常量改为 `MIN_SAMPLES_FOR_FITTING=400`（FULL_FIT 可留 400 或并成单门槛）；
  `<400` 分支返回 INSUFFICIENT_DATA 并保持现参数/默认参数；启动路径不再静默跑拟合
  （或跑但必然 INSUFFICIENT，行为等价）。
- **测试**：399 条不拟合、400 条拟合；UI/日志口径同步。
- **风险/依赖**：与 KF-04 同批；注意用户侧"校准达标前用默认"的提示文案。

### KF-06 · 投影改写证据不回写账本（训练吃到未施行的 GOOD）

- **现状证据**：`applyRevealCausality`（`LearningProjector.kt:683-708`）投影期改写，不回写
  attempt_event/review_log。
- **目标行为**：拟合视图与实际施行的证据一致。
- **具体改法**（二选一，推荐 A）：
  - A：**拟合视图改用投影后证据**——review_log 的 rating 改在投影完成后、按 post-causality
    的 (reason, weight) 重算后落（把 `ReviewLogSink.record` 的调用点移到投影提交后，
    或增加一次补偿写入）；reveal 行按 KF-03 排除。
  - B：投影不改写，改在提交侧显式定价（依赖 KF-02 的 persistedAssistance，从源头就落
    EXCLUDED）——则 applyRevealCausality 可逐步退场。
- **投影/迁移影响**：A 无投影变化；B 需 bump `PROJECTION_COMPOSITE`。
- **测试**：看答案后答对的 review_log 行不再是 GOOD；拟合样本与投影一致性的端到端用例。
- **风险/依赖**：与 KF-02/03 同批，先定 A/B。

### KF-07 · 兜底分支把全部 topic 节点按 strength=1.0 全绑（落点 3C）

- **现状证据**：`RoomMistakeOrganizationRepository.kt:1021-1025`（用户路径 atom 全拒时
  `nodeIdsToBind = topicNodes.map{knowledgeNodeId}`）。
- **目标行为**：绑定失败时**落 `pseudo:<SUBJECT>` 占位**，不绑任何 topic 节点（宁可诚实未分类，
  不可全量错绑）。
- **具体改法**：兜底分支删除；返回空绑定 + 由 `ensurePseudoKnowledgeBinding`
  （`StudyReviewPlannerService.kt:177-189`）接占位；自动路径同改。
- **测试**：atom 全拒 → 无 topic 绑定行、有 pseudo；正常路径不变。
- **风险/依赖**：3C；与 KF-30 同批。

### KF-08 · PREREQ 加性惩罚被 EXAM/WEAKNESS 抵消

- **现状证据**：`ReviewPlannerV2.kt` 打分式最后一项 `− PREREQ_GAP_WEIGHT * prereqGap`
  （加性 −2.0）。
- **目标行为**：前置未达标**硬过滤**——不参与排序（研究 §3.2）。
- **具体改法**：在 `scoreCandidate` 开头（或 beam 候选组装处）：若 `prereqGap > 0`（最弱前置
  < READY_THRESHOLD）→ `return null`（与"未到期且 R̂≥0.8"的早退同模式）；同时提供前置补救卡
  的现有路径不受影响（`PrerequisiteRemediation` 是另一条流）。
- **投影/迁移影响**：纯排程策略变化；`PLAN_FINGERPRINT_SCHEMA_VERSION` bump（计划指纹）。
- **测试**：前置未达的题不进队列；补救路径仍可达；EXAM/WEAKNESS 高分不再把前置未达者捞进来。
- **风险/依赖**：3A；与 KF-09/10 同批重排打分。

---

## 3. P1 组（表示重做）

### KF-09 · 掌握表示换 β-二项（s/f 双计数）

- **现状证据**：`LearningProjector.kt:602-604,990-992` 固定增益 EMA；`:1118-1122` Wald 下界。
- **目标行为**：每个 KC 的掌握状态由 **(s, f) 计数 + 先验 (α, β)** 决定：
  - 点估计 p = (s + α) / (s + f + α + β)；
  - 正向证据 s += w，负向 f += w（w = 既有证据权重，折扣链不变）；
  - 先验默认 Jeffreys (α=β=0.5)（**已裁**，裁决 12）。
- **具体改法**：
  1. `KnowledgeMasteryState` 增加 s/f 字段（或替换 evidenceMass 语义）；`learner_knowledge_mastery_state`
     表加 `success_weight` / `failure_weight` 两列（新 schema 版本 + 非破坏迁移）。
  2. `projectMastery` / `projectChatEvidence`：把 EMA 行替换为 s/f 累加 + p 派生。
  3. 下界换 Wilson（KF-10）。
  4. MASTERED 判据：p ≥ θ ∧ 区间宽度 ≤ ε（区间 = 上界 − 下界）。
- **投影/迁移影响**：投影表加列；旧数据**经全量重放回填**（账本→投影架构的既有能力：
  `commitFullReplay`）；bump `PROJECTION_COMPOSITE`；`MasteryOverviewDao` 等读 masteryScore 的
  消费点逐一核对（p 语义不变，数值变）。
- **测试**：① 8 次全对轨迹对照新判据（p、下界、MASTERED 时点）；② 与 EMA 的分叉用例
  （1 对 1 错后的 p 差异）；③ 重放一致性；④ 迁移后旧行可读、重放回填正确。
- **风险/依赖**：核心表示变化，牵动 KF-10/16/22、排程 weakness 消费点、学习档案展示；
  **与 9 项裁决同时裁**。

### KF-10 · 下界换 Wilson/Jeffreys 区间

- **现状证据**：`masteryLowerBound`（`LearningProjector.kt:1118-1122`）Wald 式。
- **目标行为**：Wilson 区间下界（推荐，公式完整给出）：
  ```
  z = 1.96（单侧 97.5%，AMT 先例）
  n = s + f
  p̂ = (s + α) / (n + α + β)      # β-二项点估计（KF-09 落地后）
  下界 = ( p̂ + z²/2n − z·√( p̂(1−p̂)/n + z²/4n² ) ) / (1 + z²/n)
  ```
  或 Jeffreys equal-tailed Beta((s+α),(f+β)) 分位（更稳，计算略重）。
- **具体改法**：替换 `masteryLowerBound` 实现；n<1 时下界=先验均值（不做区间）。
- **测试**：小 n 边界（n=1/2/8）手算对照；与 Wald 的差异方向用例。
- **风险/依赖**：与 KF-09 同批。

### KF-11 · 删除 legacy 记忆模型（×0.45 无据）

- **现状证据**：`MemoryUpdateModel.kt:130-194` `LegacyExponentialMemoryUpdateModel`；
  ANSWER_REVEAL_STABILITY_FACTOR=0.45（研究定 D 级无据）。
- **目标行为**：FSRS 唯一化，kill-switch 退场。
- **具体改法**：删除 legacy 类 + `useFsrsScheduling` 分支（`RoomBackedStudyExperienceRepository.kt:147-160`）
  与设置项；`ForgettingCurve` 若仅被 legacy 使用则一并收束（检查调用点）。
- **投影/迁移影响**：bump `PROJECTION_COMPOSITE`；设置项清理。
- **测试**：删除后编译 + 既有 FSRS 全绿；任何引用 legacy 的测试改写。
- **风险/依赖**：3A 后期做（先让 FSRS 侧 KF-01/04/05 就位，再删回退通道）。

### KF-12 · w3 从拟合集剔除（EASY 档结构性消失）

- **现状证据**：映射永不产 EASY（`FsrsScheduleMath.kt:210-241`）；w16 已钉 1.0，w3 仍拟合。
- **目标行为**：三档评级是产品裁定；w3/w16 均不拟合。
- **具体改法**：`FsrsParameterOptimizer` 的 `fittedIndices` 剔除 3（与 16 并列）。
- **测试**：拟合后 w3 恒为默认值。
- **风险/依赖**：与 KF-01/04/05 同批。

### KF-13 · 常数依据标注（0.32/0.42、0.7 阈值）

- **现状证据**：`POSITIVE/NEGATIVE_LEARNING_RATE=0.32/0.42`；`MasteryWriteGate` 0.7。
- **目标行为**：文档化"固定增益（EMA 式）= 产品裁定，禁止按 p(T) 语义调参"；
  0.7 降级为多信号之一（保留值、改语义：与引文锚、冷却、配额并列的**一个**信号，并落库
  模型自报置信度）。
- **具体改法**：KDoc + 研究文档标注；`MasteryWriteGate` 注释改口径（行为暂不变，待校准裁决）。
- **风险/依赖**：与 9 项裁决同批。

---

## 4. P2 组（必须补机制 + 口径）

### KF-14 · RT 个人分位进证据门

- **目标行为**：按学习者自身作答时长的**个人分位**判"快答"（B-GLIRT 层级模型方向）；
  快答（低于个人分位阈值，如 10% 分位）的正向证据降权或标记 LOW_CONFIDENCE。
- **具体改法**：时长已采集（`attempt_event.duration_seconds`）；在 `StudySubmissionPreparer` 的
  折扣链后增加：维护 per-learner 的 RT 分位估计（滚动窗口），快答 → 复用既有
  `LOW_CONFIDENCE_CORRECT_CEILING` 通道降 HARD（FSRS 侧已存在该折点，掌握侧 weight 打折）。
- **测试**：快答正例降权、慢答不变；分位初值冷启动（无历史时用绝对阈值 3s 兜底）。
- **风险/依赖**：3A；与 KF-15 同批（同属"防蒙对"线）。

### KF-15 · RTE 快答猜测降权

- **目标行为**：RTE≥0.9（足够投入惯例）以下的正向证据降权。
- **具体改法**：RTE = 作答时长 / 展示时长（两值都已采集）；阈值 0.9（B 级先例）；
  低 RTE 且正确 → 证据降 HARD/折权（与 KF-14 合并成一条"投入折扣"）。
- **测试**：秒答对→降权；认真答对→不变；错答不受 RTE 影响。
- **风险/依赖**：与 KF-14 同批。

### KF-16 · 先修传递到后继估计

- **目标行为**：先修未达时，后继的掌握下界被压制（图基 KT 方向，RPKT/KQN）。
- **具体改法**：投影侧或消费侧加一层：后继 KC 的有效下界 = min(自身下界, 先修下界)；
  排程的 weakness 用有效值。（**已裁：做下行压制**，裁决 7。）
- **测试**：先修 0.4 / 后继 0.9 → 有效下界 0.4；先修恢复后解除。
- **风险/依赖**：与 KF-09/10 同批。

### KF-17 · Disperse siblings

- **目标行为**：同批到期（同日导入/同题簇）的卡分散到不同天（fsrs4anki-helper 口径）。
- **具体改法**：排程后处理：同 batch/source 的候选到期日按序错峰（每个后续 +1 天或按负载
  轮转），不改变 FSRS 参数本身。
- **测试**：同批 3 题 → 三日错峰；单题不受影响。
- **风险/依赖**：3A 排程侧。

### KF-18 · Postpone/Advance

- **目标行为**：学生可见"今天做不完 → 明天继续"与"考前提前"两条显式操作，不改 FSRS 参数
  （Deckline 式外生约束层）。
- **具体改法**：每日目标层：`dailyTarget` 概念 + 超出部分顺延（Postpone）；Advance 复用
  插眼 1 的考试窗口信号（考前把到期日提前）。
- **风险/依赖**：与插眼 1 同批（都是考试/负载外生约束）。

### KF-19 · CMRR 闭环

- **目标行为**：retention 预测 + 负载预测形成"改 retention 目标 → 预测未来负载"闭环。
- **具体改法**：现有"建议值"扩展为模拟器：对候选 desired-retention 用当前参数模拟 30 天负载，
  给建议（Anki Simulator 口径）；UI 只在设置页。
- **风险/依赖**：依赖 KF-01/04/05（参数可信后模拟才有意义）。

### KF-20 · Open Learner Model 解释层

- **目标行为**：学习档案的掌握值可解释——"这个 0.72 来自 3 次独立答对 + 1 次看答案（不计）"。
- **具体改法**：由区间 + 证据摘要驱动（s/f 计数天然支持）；显示区间而非单点（Glicko-2 口径）；
  文案遵守"学生语言"纪律。
- **风险/依赖**：依赖 KF-09/10；与学习档案视觉（插眼 4，先问用户）联动。

### KF-21 · HLR 训练与晋升

- **目标行为**：半衰期回归真正训练 + 输出晋升到投影的显式门。
- **具体改法**：训练流程（用 review_log 序列拟合并半衰期）；晋升门（新模型 log loss 不劣于
  FSRS 基线才可参与排程）；当前"never fed back"注释解除。
- **风险/依赖**：与 KF-19 的模拟器同批；样本量门槛同 400 口径。

### KF-23 · review_log 增 state{0..3} 列

- **目标行为**：New/Learning/Review/Relearning 可区分。
- **具体改法**：新列 `state Int`；取值规则：该卡首条=New(0)、同日重复=Learning(1)、
  跨日非 AGAIN 后=Review(2)、AGAIN 后=Relearning(3)；`SchedulingReplay` 按 state 选初始稳定度
  分支（替换 elapsed<1 启发式）。
- **迁移**：新列 + 非破坏迁移；旧行回填：按现有数据推导（有前一条 review_log 的=Review/Relearning，
  无=New）——UNVERIFIED 推导准确性，实施时核。
- **风险/依赖**：与 KF-24/25 同批。

### KF-24 · 每日每卡首条去重

- **目标行为**：拟合取样按 (card, 本地日) 只计首条（官方口径）。
- **具体改法**：`readReviewLogSamples` 侧按 `(source 卡, study_day)` 去重（纯拟合侧过滤，
  不动落库）。
- **测试**：同日两行 → 拟合只用首条。
- **风险/依赖**：与 KF-23 同批。

### KF-25 · day_start

- **目标行为**：本地日界默认 4:00（官方），可调。
- **具体改法**：`StudyWriteContext` 日界计算加 day_start 偏移；设置项。
- **迁移**：**历史数据断层**——旧行保持午夜口径、新行 4:00，文档化断层；或 bump
  `PROJECTION_COMPOSITE` + 重放按新口径重算（推荐后者，账本里 offset 分钟数已存，可重算）。
- **风险/依赖**：与 KF-23/24 同批；与 KF-33 时间语义同批修。

### KF-26 · MODEL_JUDGED 回归机制

- **目标行为**：判题证据在"校准达标后"进入拟合集。
- **具体改法**：加显式门：判题通道 κ/校准报告达标（阈值待裁）→ 该类样本解除排除；
  门与 KF-21 晋升联动。
- **风险/依赖**：依赖判题定价研究（既有 `model-judged-verdict-pricing.md`）。

### KF-27 · 新复习栏交互信号接线

- **目标行为**：scroll/away/interruption 在新卡片流里真实采集。
- **具体改法**：`ReviewInteractionTracker` 挂到新复习会话屏；`submitReviewChoice` 透传真值。
- **测试**：滚动/离屏/中断各落库一次；旧路径不变。
- **风险/依赖**：阶段 5 复习栏落地时同步（也可提前到 3A 数据侧）。

### KF-28 · scheduling_eligible 悬空

- **目标行为**：冷却抑制的观测行写 false 且拟合侧过滤。
- **具体改法**：spec 2.7 冷却落地时同步写 `scheduling_eligible=false`；`readReviewLogSamples`
  过滤 false。
- **风险/依赖**：与冷却机制（既有 spec）同批。

### KF-33 · 跨时区/DST 裂缝（D1/D2）

- **目标行为**：上一复习的本地日按**它自己的偏移**换算。
- **具体改法**（推荐 B）：重放/现算时用账本中该事件的 `utc_offset_minutes` 换算其本地日
  （`attempt_event` 已存该列），不再用当前事件偏移；A 案=新增"上一复习偏移"列（不推荐，
  账本已有原始数据）。
- **测试**：构造跨时区两次复习的账本，断言 elapsedCalendarDays 正确。
- **风险/依赖**：与 KF-25 同批。

### KF-34 · 会话配额语义（D6）

- **目标行为**：配额绑定第一条消息实际创建的会话行；禁止空串落库。
- **具体改法**：`RoomTutorToolRunner` 的 `conversationId.orEmpty()` 改为显式拒绝（null → 写门
  直接拒，不落空串）；大厅若未来开放写工具，先透传真实会话 id；50/会话文档口径改为
  "循环断路器"，防刷语义下沉到 (learner, KC, 12h) 与 (learner, 1h, 100)。
- **风险/依赖**：3A 门常数批次；与新会话模型（阶段 1/2）联动。

---

## 5. P3 组（坐标系可信度，落点 3C）

### KF-29 · 绑定正确性监测管线

- **具体改法**：
  1. 新表 `binding_audit_sample`：`sample_id`、`practice_unit_id`、`binding_snapshot_json`、
     `status(PENDING/REVIEWED)`、`verdict(CORRECT/WRONG/AMBIGUOUS)`、`reviewed_at`。
  2. 抽样规则：每科每周首 N 条（默认 5）新绑定进抽样队列（后台任务，D2 判据合规）。
  3. 复核屏：**debug 构建专用**（不进学生面）；显示题面 + 绑定点 + 原文依据，一键 verdict。
  4. 指标：错误率按科/按模型版本聚合，落 `docs/` 报告。
- **测试**：抽样入队、复核落判、聚合口径。
- **风险/依赖**：3C；与 KF-30 同批。

### KF-30 · 错误自动绑定自锁打破

- **具体改法**：`mergeAutomaticClassifications` 修正：自动接受的标签携带"来源=auto"标记，
  后续自动轮更高置信的提议可覆盖；用户确认的（USER_CONFIRMED 语义）不可。
- **测试**：auto 标签被更高置信 auto 覆盖、被用户标签保护。
- **风险/依赖**：3C；与 KF-07 同批。

### KF-31 · 重拆提议的产生路径

- **具体改法**（推荐方案）：
  1. **后台重审任务**：定期（默认每周）对"近期被复习过且绑定信心一般"的题重跑归类
     （复用组织路径），diff 当前绑定 vs 新提议，差异落**建议清单**（机制壳已保留，
     台账 D-Q1M6）。
  2. hook 接线：提议经 K3 四判据（节点合法/可重放/冷却/不碰用户确认）过滤后才可见。
  3. 备选：工具 `RECLASSIFY_PROPOSE`（智能体主动），与后台任务二选一或并存。
- **测试**：重审产提议 → hook 过滤 → 采纳/忽略留痕。
- **风险/依赖**：3C；依赖 KF-29 的监测（先有监测再开自动重审）。

### KF-32 · 改绑→重放历史（落点 3B）

- **具体语义定案**：**attempt 不存历史绑定；重放时按 practiceUnitId → 当前绑定重挂**
  （D-Q1G"按新绑定重放"的最简诚实实现）。
- **具体改法**：
  1. 重放期 attribution 重派生：`attribution` 由 (attempt.practiceUnitId → 当前
     `practice_unit_knowledge_binding`) 派生，替代"写时钉死 bindingId"。
  2. 改绑触发重放：`RoomProblemOrganizationStore.confirm`（含离线纠正）落账本事件
     （新事件 kind `BINDING_CHANGED` 或复用 CORRECTION 通道），`StudyProjectionDrainer`
     据此触发全量重放。
  3. upcaster 层：旧 bindingId 经 successors 链解析（合并/退役跟随，研究 §2.3）。
- **迁移**：新事件 kind + 非破坏迁移；重放前落旧投影快照（SCD2 教训）。
- **测试**：改绑 → 重放 → 历史证据挂到新节点；旧节点状态归零；重放幂等。
- **风险/依赖**：3B；依赖 KF-09/10 的表示重做（3A 先落地）。

---

## 6. 待裁项（已全部裁定 2026-09-30 → 台账「3A 前裁决门 · 收口」）

1. **看答案最终语义**（KF-03）→ **已裁：B**（评 AGAIN 计一次遗忘失败；否决原推荐 A，裁决 1）。
2. **β-二项先验 α/β** → **已裁：Jeffreys (0.5, 0.5) 起步**；分层经验先验留 Wave X（裁决 12）。
3. **fuzz 引入与否** → **已裁：不做**（确定性优先；聚集由 KF-17 确定性错峰承担，裁决 15）。
4. **排程权重表离线模拟** → **已裁：Wave X 离线模拟定案**（TimeSeriesSplit + 真实 review_log 回放，裁决 17）。
5. 研究"必须补 9 项"的逐项裁决 → **已裁**（裁决 5-11，含 HLR 删除等改判）。

---

## 7. 未验证边界（不得当事实）

- fsrs-optimizer 完整 BPTT 实现细节未逐行复现（结构理解到位、梯度断点已定位）。
- A−/B 级文献正文未读（出版商拦截），实施前按纪律补一手核验。
- day_start 偏移、跨时区裂缝触发频率未实测。
- KF-23 的旧行 state 回填推导未验证。
