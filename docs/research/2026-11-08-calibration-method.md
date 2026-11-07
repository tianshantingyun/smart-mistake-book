# 批次 49 · R1：离线标定方法论（FSRS 参数拟合 / per-reason 权重 / 考前扩容）

| 字段 | 值 |
|---|---|
| 角色 | 方法论研究（R1）。**只写本文件**：不改任何源码/常量/测试/金样/台账，不跑 Gradle，不跑设备 |
| 写于 | 2026-11-08 |
| 服务对象 | 计划 `.zcode/plans/plan-2026-11-08-mastery-review-quality.md` §P1-6/7/8/10（模拟器、预注册、报告、上线门） |
| 本会话实跑 | `gh api`（fsrs-optimizer / anki / fsrs-rs / srs-benchmark 源码与文档）、`WebFetch`（Anki 手册与 FAQ）、`grep`/`sed`/`Read`（仓内源码与既有研究）；命令与输出见 §10 与证据文件 |
| 明确不做 | 不写预注册（P1-7）、不写标定报告与 JSON（P1-8）、不定任何常量的新值；本文件只给**方法与门槛**，数值裁决留给预注册冻结后的一次性执行 |
| 硬约束继承 | 掌握阈值 21 天 / 0.9 不改（只作护栏读数）；模拟结论标 `model-based`；真库样本不足标 `BLOCKED` |

---

## 0. 结论（先看这里）

| # | 裁定 | 依据 |
|---|---|---|
| T1 | **三条线的决策对象不同，指标不可互换**：FSRS 拟合是**预测**问题（有真值标签，可用 logLoss/Brier/ECE）；per-reason 权重与 G6 是**策略**问题（不输出概率，logLoss 只能事后附报，不能当选择准则）。 | 本文件 §2；`SchedulingEvaluation.kt:217-225` vs `:316-334` |
| T2 | **per-reason realized recall 只能当诊断，不能当回归目标**：它条件在"该题被选中"，且原因本身定义了信号方向（`WEAK_KNOWLEDGE` 天然低召回），比值不是"该通道准不准"。 | 本文件 §2.1；`SchedulingEvaluation.kt:316-334` |
| T3 | **策略线的反事实只能靠模拟**：换权重会改变"哪些题被复习"，真实日志离线重加权即 off-policy 评估，系统性有偏。故权重/G6 的**真实库标定在结构上不可得**，只能登记 `BLOCKED`，不得用模拟数冒充。 | 本文件 §2.2；计划 §P1-6 |
| T4 | **模拟世界必须故意错配于 planner 内部模型**，否则 planner 在自己的目标函数上必然最优，测量退化为"自己证明自己对"。 | 本文件 §3.2 |
| T5 | **单一决策标尺 = 固定时间比结果 / 固定结果比时间**；G6 的主对照必须是**等时替代**，否则任何扩容都会赢。 | 本文件 §4.5 |
| T6 | **G6 的 `f=0` 不是空臂**：考试记录仍在，`EXAM_WEIGHT` 与"R<0.8 才可早复习"闸门仍生效。真空臂 = **无考试记录**。 | `ReviewPlannerV2.kt:659-668`、`:636-648` |
| T7 | **G6 的 {0.15,0.25,0.35} 在"容量预览"路径上低于既有 F9 的 1.5× 上限**——即"扩容"在该路径上可能反而**压低日容量上限**。两条路径必须分开测。 | `ReviewCapacityPolicy.kt:66`、`StudyReviewPlannerService.kt:392-393`、`ReviewCapacityScheduler.kt:56-63` |
| T8 | **样本分母唯一口径 = 可预测长程数**（`predictableSampleCount`；`LongRangeSampleLedger` 同名分桶），不是原始行数、不是可拟合行数。 | `SchedulingEvaluation.kt:580-600`、`LongRangeSampleLedger.kt:105-107` |
| T9 | 既有恢复测试的真值 = **先验中心**（`DEFAULT_PARAMETERS`），L2 罚项会把估计拉回真值 → 它只证明"不是完全没学到"，**不证明"能识别个体偏离"**；必须补"真值≠先验中心"的扰动臂。 | `FsrsParameterRecoveryTest.kt:22`、`SchedulingEvaluation.kt:615`、`:665-680` |
| T10 | 采纳规则统一为**配对差 + 符号一致 + 最小效应 + 无护栏破坏**，否则维持现值（"不劣即保留"，与 Anki 采纳门同向）。 | 本文件 §5；`StudySchedulingCalibration.kt:134-143`、Anki `params.rs:153-156` |
| T11 | **报告禁止用 `0.0` 冒充"无数据"**：`CalibrationReportBuilder` 在空输入上把 Brier/ECE 返回 `0.0`，而 0.0 是这两个指标的**最优值**——"没数据"读起来就是"完美校准"。 | `CalibrationReportBuilder.kt:74-87` |
| T12 | 掌握阈值（21 天 / 0.9）**只作护栏与读数**，不进任何扫描网格；本项目没有"用标定改阈值"这一项。 | `AlgorithmConstants.kt:63,66`；计划 §硬约束 |

> 一句话：**先分清"预测"与"策略"，再谈指标；策略线只有在"等时 + 错配世界 + 配对种子 + 预注册裁决"四件齐备时，模拟数才有裁决力。**

---

## 1. 外部一手核验（本轮实读，含一条对题面的更正）

| 事实 | 一手证据（本会话读取） | 对本方法论的约束 |
|---|---|---|
| fsrs-optimizer 目标是 **BCE + L2 先验罚项**；罚项 `Σ((w−w_init)²/σ²)·γ`，`γ=1`，`σ=DEFAULT_PARAMS_STDDEV_TENSOR`；训练取每序列末态 `outputs[seq_lens−1]`；评估式 `BCE.mean() + penalty·γ/train_set_size` | `fsrs_optimizer.py:79`（σ）、`:446`（γ=1）、`:470`（`BCELoss(reduction="none")`）、`:509`/`:575`（末态取 stability）、`:513`（加权求和）、`:514-523`（训练罚项）、`:579-584`（评估罚项缩放） | 本仓 `objectiveFor`/`bceFor` 与之一一对应（`SchedulingEvaluation.kt:665-680`）；标定报告必须区分**含罚 objective** 与**纯 BCE 上报值**（`:543-550`） |
| Anki 的采纳门 = 同一批数据上**新参数 log loss 不劣于存量**才写回（`:153-156`，另含重学步数分支）；健康检查 `>300` 条时跑，判据 `adjusted_log_loss ≤ 1.11 或 adjusted_rmse ≤ 1.53`；`400` 是"低于此值报样本不足"的门槛口径 | `rslib/src/scheduler/fsrs/params.rs:78`（400 注释）、`:66-76`（两个 adjustment 公式）、`:153-156`（采纳门）、`:182-198`（健康检查） | 采纳门是**"不劣"而非"显著更优"**——本仓 `shouldAdopt` 同口径（`StudySchedulingCalibration.kt:134-143`）；模拟裁决则必须更严（§5）。**400 的强制点在 collection/UI 层，本轮未逐行定位**，只采用其阈值语义 |
| **Anki 没有 "exam mode" 这个功能**。一手可查范围内：`decks.proto` 的考试/截止日字段命中 **0**；`fsrs-rs` 的 `simulation.rs` 命中 **0**；官方"备考大考"页只给**设置建议**（新卡数按"总天数减 14–30 天"折算）与 filtered deck，不提任何模式 | `decks.proto:74-87`（只有 `limit`/`today`/`review_limit`/`desired_retention`）、`grep -c -i "exam\|deadline"` → `0`；`simulation.rs` 同上 → `0`；FAQ `docs-site/faqs/settings-for-using-anki-to-prepare-for-a-large-exam.mdx`（WebFetch） | **题面"参考 Anki 的 exam mode"没有可引对象**。G6 是自研产品层，结论只能标 `model-based`，不得借 Anki 权威背书（与 `2026-11-02-fsrs-capacity.md` C2、`2026-11-03-fsrs-daily-cap-allocation.md` C5/C6 一致） |
| Anki 的考试相关杠杆（**本会话一手可查**）只有两项：单牌组 `desired_retention` 覆盖、单牌组 **Today only** 日上限。Easy Days 与"改 r\* 默认不重排、当天负载不变"来自仓内既有研究（**二手，本轮未复跑**） | `decks.proto:74-87`；手册 `deck-options.html`（WebFetch：默认 90%、>90% 负载快速上升、>97% 不堪重负）；二手见 `2026-11-03-fsrs-daily-cap-allocation.md` C5/C6 | 我们的 G6 改的是**预算**，不是 `r*`；spec §2.17 的 `r*_exam` 队列准入**未实现**（代码用固定 0.8 闸门），故本轮测不到"提高 r*"那条杠杆 |
| CMRR 在 Anki 25.07 已**移除**；`Days to simulate` 属 Simulator（"controls the duration of the simulated study history"），不是 CMRR 参数 | 手册 `deck-options.html`（WebFetch 原文引用） | 本仓 `OptimalRetention`（CMRR 简化实现）只能标 `model-based` 建议，**不得**写成"Anki 现役功能对齐" |
| fsrs-rs 模拟器用**优先级钩子 + 每日成本上限**比较调度策略：`ReviewPriorityFn(Arc<dyn Fn(&Card) -> i32 + Sync + Send>)`、`max_cost_perday` 默认 `1800`、目标 `total_cost / total_memorized` | `simulation.rs:281`（钩子）、`:587-606`（`SimulatorConfig`，无 exam 字段）、`:613`（1800.0）、`:579-581`（CMRR 目标） | **"同成本上限、不同优先级"就是上游的评估范式**——正好是本仓权重扫描的形态（同 `timeBudgetSeconds`、不同权重） |
| srs-benchmark 协议：`TimeSeriesSplit` 按时间切（旧训新测），**第一折不进评估**；指标 Log Loss / RMSE(bins) / AUC；同日复习在主表**只训不评**（`:141` 另有纳入同日的一张表）；默认 5 折；提供"训练=评估"的过拟合诊断开关 | `README.md:19-23`、`:29-35`、`:44`、`:83`、`:141`、`:290`、`:310`、`:320` | 时序切分是外部硬口径；本仓 `TRAIN_FRACTION=0.8`（优化器）与 `trainFraction=0.7`（evaluate）**两口径并存**，报告必须写明用哪条（§3.3） |

> 读取版本说明：本轮 `gh api` 读的是各仓 **`main` HEAD**（2026-11-07 取），未钉 tag。仓内既有文档引用的 fsrs-optimizer **v6.5.0** 行号（`:446`/`:1424`/`:79-100`）与本次读取一致，可交叉印证；其余行号以 HEAD 为准。

---

## 2. 三条线为什么不能共用一套指标

### 2.1 决策对象对照

| 线 | 决策对象 | 是否输出概率 | 反事实可得性 | 主指标 | 可作护栏 | 禁用为选择准则 |
|---|---|---|---|---|---|---|
| **A. FSRS 参数拟合** | 21 个参数中的 15 个自由维（`0..2,4..14,20`） | 是（`R(t,S)`） | 有：（间隔, 结果）是监督信号 | held-out **logLoss**（纯 BCE） | 工作量漂移、参数边界命中率 | realized recall（二值化后信息量低） |
| **B. per-reason 权重** | 8 个加性权重（DUE 5.0 / WEAKNESS 3.0 / LAPSE 1.0 / REPEAT 2.0 / AVOID 1.0 / EXAM 2.0 / WAIT 1.5 / KC_DROP 2.5） | **否**（只决定排序） | **无真实反事实**，只能模拟 | 等时下的**考试日召回 / 掌握数** | 各科覆盖、饥饿时长、队列抖动、多样性上限 | logLoss（权重不产生概率）；per-reason recall（选择偏倚） |
| **C. G6 考前扩容** | 一个标量 `f ∈ {0,0.15,0.25,0.35,0.5}` | 否 | 同上，只能模拟 | 等时下的考试日召回；增时臂的**每分钟边际收益** | 窗口外逐位不变、总超发=0、考后欠账 | 总复习量（扩容必然抬高它） |

权重与 `KC_DROP_WEIGHT` 的现值：`AlgorithmConstants.kt:114-120`、`ReviewPlannerV2.kt:1081-1090`；`KC_DROP_WEIGHT` 仍是**裸常量**（`ReviewPlanner.kt:22`），不在 `AlgorithmConstants` 里——扫描前必须先单源（计划 §P1-9），否则"改了权重但金样没变"这类静默分叉会重演。

### 2.2 两个结构性陷阱

**（1）per-reason recall 的选择偏倚。** 原因不是随机分配的：`WEAK_KNOWLEDGE` 恰恰标在掌握度低的题上，`EXAM_PRIORITY` 标在考试科的题上。因此"某原因召回低"既可能是该通道排序错了，也可能只是它专门挑难卡。**它是覆盖率/分派量的诊断，不是准确率。** 真正的因果问题只有一种问法："把这个权重调大，考试日的结果会变好吗？"——只能模拟。

**（2）策略线的反事实不可得。** 日志里只有"被选中并被作答"的题；换权重后未被选中的题没有观测。用旧日志给新权重复算权重（off-policy reweighting）需要**倾向得分**（每题每天被选中的概率），而 beam search + 份额上限 + 先修硬过滤使倾向得分不可解析。结论：**权重/G6 的真实库离线标定只能标 `BLOCKED`**，可做的真实库检查只有两项——(a) 现状的 per-reason 分布与样本量（诊断），(b) 规格不变量（窗口外逐位不变等）。这一点必须在报告里写死，否则"我们做过标定"会变成一句无法验证的话（该风险已在 `docs/audit-2026-09-12-kernel-readiness.md:1910` 记录过同型问题）。

---

## 3. 评估协议：预注册 + 模拟学生 + 留出 seed

### 3.0 模拟学生的形状与日循环（P1-6 的实现口径）

规模取自计划 §P1-6（30 KC / 5 科 / 300 题 / 先修链）；下列其余参数是**本文件建议的默认值**，预注册可改但必须冻结。

| 项 | 默认 | 说明 |
|---|---|---|
| 知识点 | 30 个，5 科 × 6 个 | 每 KC 1–2 条先修边（`knowledgePrerequisites`，进 `ReviewPlanningRequest`） |
| 题目 | 300 道，每 KC 绑定 10 道 | 题级各自有 `ProblemMemoryState`；KC 级掌握态由该 KC 绑定题的事件投影得出（与生产 `LearningProjector` 同口径） |
| 学习者 | **1 个 learner / world** | 产品是单学习者个性化；不要把 10 个 world 当"10 个用户"做总体推断，它们只是**重复实验** |
| 初始状态 | 全部 NEW；无历史 | 前 30 天是"冷启动段"，指标从第 30 天才开始累计（避开无状态期） |
| horizon | 120 学习日 | 30 天冷启动 + 64 天稳定期 + 26 天考试窗（最早一场的第 94 天起，至第 120 天） |
| 考试 | 5 科各 1 场：第 108/112/116/118/120 天 | 故意错开，才能走到"多科并行取最大 ramp"的分支（`StudyReviewPlannerService.kt:174-181`） |
| 每日预算 | `dailyReviewMinutes = 20` → 1200s（`StudyExperienceRepository.kt:737-739`） | 题均 60s ⇒ 约 20 题/日；这是"时间预算"杠杆的唯一入口 |
| 日循环 | `plan(day) → 逐题作答 → FSRS 状态更新 → 写 review_log → 次日` | 与生产同序；`review_log` 字段与产线一致（`rating` / `sourceKind=ATTEMPT` / `plannedReason` / `state` / `deltaTDays`） |
| 作答生成 | `correct ~ Bernoulli(R(t,S) × (1−slip))`；`rating = AGAIN`（错）/ `HARD`（对且 `p<0.55`）/ `GOOD`（对） | 三档词表与生产一致（EASY 被 `FsrsScheduleMath.kt:275` 折成 GOOD）；HARD 必须有一定占比，否则 `w6/w7` 不可辨识 |
| 考试日探针 | 每场考试对本科 6 个 KC 各抽 20 次（`correct ~ Bernoulli(R × (1−slip_exam))`） | **不入 review_log、不更新记忆**；这是"考试结果"的唯一来源 |
| `slip` | 科目级 0.03–0.08（预注册冻结具体值） | 世界错配项（§3.2） |
| 确定性 | `java.util.Random(worldSeed)` 建结构、`Random(noiseSeed)` 抽结果 | 同 seed 逐位可复现（§3.6） |

**为什么冷启动期不计指标**：前 30 天所有卡都是 NEW，`predictableSampleCount` 主要来自同日/短期复习，logLoss 与召回都被"学习阶段"主导；把它计入会让"哪条臂先引入更多新题"变成主效应，掩盖被测的策略差异。

### 3.1 三层集合（冻结，不得互换）

| 层 | 规模 | 用途 | 允许看几次 |
|---|---|---|---|
| `dev` | 10 个 world seed | 开发/调网格/找 bug | 无限次 |
| `holdout` | 10 个 world seed（与 dev 不同基） | **一次性裁决** | **1 次**；看后任何改动都算新预注册 |
| `golden` | 1 个小 world（可 60 天/120 题） | CI 逐位一致回归（防"同 seed 不同结果"） | 每次 CI |

预注册文件必须冻结：**seed 字面量列表、网格、指标清单、护栏、裁决规则、模拟世界形状、错配参数、代码 SHA**。执行时把预注册文件的 `sha256` 写进报告 meta（仓内已有 `E-FILES-SHA256.txt` 的证据惯例）。

### 3.2 两级种子与"故意错配"

- `worldSeed`：控制**结构**（先修图、科目归属、题-知识点绑定、难度分布、各 KC 的真稳定性乘子）。
- `noiseSeed`：控制**作答随机性**（Bernoulli 抽样）。
- 同一 `worldSeed` 下换 `noiseSeed` 只测噪声；同一 `noiseSeed` 换臂只测策略。**配对发生在 world 层**，不要用 noise 层当独立重复。

**世界必须错配于 planner 内部模型**（T4），否则 planner 的目标函数就是世界的真值函数，任何权重都"对"。预注册里至少选两种错配臂：

| 臂 | 错配内容 | 它消灭的失败 |
|---|---|---|
| `W-slip` | `P(答对) = R(t,S) × (1 − slip)`，`slip` 按科目取 0.03–0.08，planner 不知道 | "FSRS 概率=真值"导致的过度自信；让 logLoss 有下界外的可改进空间 |
| `W-transfer` | 考试日探针成功 = `R` × `g(窗口内该 KC 的复习次数)`，`g` 饱和（如 `1−e^{−0.8n}`），planner 未建模 | "多复习必然线性提升"的幻觉；让 G6 的边际收益出现递减，从而可被测量 |

可选第三臂 `W-hetero`：每 KC 稳定性乘 `lognormal(0, 0.35)`，planner 只用点估计。**裁决必须在 `W-slip` 与 `W-transfer` 两臂同号**；只有一臂通过 → 报告为"条件性结论"，不采纳。

### 3.3 时序切分（三条硬规则）

1. 模拟里的 FSRS 拟合只许用**最早那场考试窗口开始前**的数据（按 §3.0 的考试日，即第 94 天前），窗口内与考试日的结局一律是评估集。理由与 srs-benchmark `TimeSeriesSplit` 同构（`README.md:19-23`）。
2. 真实库上，`FsrsParameterOptimizer` 用 `TRAIN_FRACTION = 0.8`（`SchedulingEvaluation.kt:713`），`SchedulingEvaluationHarness.evaluate` 默认 `trainFraction = 0.7`（`:339`）。**两个 logLoss 不是同一个数**，报告必须写出所用切分；跨批比较只许同口径比。
3. 同日重复只训不评：srs-benchmark 主表口径如此（`README.md:44`、`:83`），README `:141` 另有一张表把同日纳入评估——**本仓取前一口径**，并已按"每卡只发末态一对"实现（`SchedulingEvaluation.kt:145-215`、`:688-698`）。这是**防伪重复**的核心，报告不得把逐复习对当独立样本。

### 3.4 配对比较与不确定性

- 每个 world 内算 `Δ = metric(arm) − metric(baseline)`；跨 10 个 world 取中位数与符号计数。
- 置信区间用 **world 级 cluster bootstrap**（重抽 world，不重抽复习行）。理由：同一学习者的多题强相关，把 2,000 条复习当 2,000 个独立样本会把 CI 缩窄 3–10 倍。
- 二项噪声只用来算**单 world 的分辨率**，不当成跨 world 的误差。例：考试日探针 30 KC × 20 次 = 600 次抽样，`p=0.8` → `SE = sqrt(0.8·0.2/600) = 1.63pp`；10 个配对 world 的均值差 `SE` 约 0.5–0.9pp（取决于 world 间相关）。**所以 1pp 是能分辨的最小量级，0.3pp 不是。**
- 报告必须同时给 `n`（分子/分母）与 `NaN` 的格子，不给"看起来像 0"的占位值。

### 3.5 决定规则模板（预注册必须逐条填死）

```text
臂：<line>=<value>，baseline=<baseline 定义>
主指标：<metric>，方向=<越大越好|越小越好>
裁决：
  1) 符号一致：10 个 holdout world 中 ≥8 个 Δ 方向与假设一致
  2) 效应下限：median(Δ) ≥ <最小效应>，且 cluster-bootstrap 95% CI 下界 > 0
  3) 护栏：<逐条列，任一破即拒绝>
  4) 边界：最优值不得落在网格端点（是则只报方向，不采纳）
  5) 失败处置：拒绝并保留现值；不得改网格重跑同一 holdout
```

### 3.6 逐位一致与摘要

- 同 `(worldSeed, noiseSeed, arm, codeSha)` → 指标 JSON **逐位相同**。摘要用 `Double.toHexString` 进入规范化 JSON 再 `sha256`（仓内金样即用 hex 钉分值，`ReviewPlannerScaleBenchmarkTest.kt:85`）。
- 不允许用十进制格式化后的字符串做摘要（会掩盖末位差异）。
- `golden` world 进 CI：任一指标 hex 变化即红，逼作者解释"是有意变更还是漂移"。

---

## 4. 指标定义（公式 / 落点 / 已知偏差 / 用途）

### 4.1 logLoss（BCE）

- 定义：`−(1/n)Σ[y·ln p + (1−y)·ln(1−p)]`，`p` 夹在 `[1e-6, 1−1e-6]`。
- 落点：`SchedulingReplay.bceLogLoss`（`SchedulingEvaluation.kt:217-225`），夹逼在 `:221`。
- 用途：**A 线主指标**；B/C 线仅作事后附报（世界被策略改变，前后 logLoss 不是同一分布上的量）。
- 偏差：夹逼使"极端自信的错误"最多罚 `ln(1e6)≈13.8`；样本少时 logLoss 的方差很大（400 条时 `SE` 量级 0.03–0.05），**不足以支持"0.01 的提升"这类结论**。
- 禁例：`bceFor` 在无对时返回哨兵 `10.0`（`:655-663`）——该值**不得**出现在报告里；报告只接受 `validationLogLoss` 的 `null` 语义（`:558-572`）。

### 4.2 Brier

- 定义：`(1/n)Σ(p−y)²`；`CalibrationReportBuilder` 逐桶算再按桶样本数加权（`CalibrationReportBuilder.kt:43-51`、`:74-78`），代数上等于全局均方误差。
- 用途：A 线第二指标（对概率尺度不如 logLoss 敏感，但对"系统性偏高/偏低"更直观）；B/C 线护栏。
- 偏差：与 logLoss 同向冗余，**不要**把两者都当独立证据做"双重通过"。

### 4.3 ECE（期望校准误差）

- 定义：10 个**等宽**桶，`ECE = Σ (n_b/n)·|mean_p(b) − rate(b)|`（`CalibrationReportBuilder.kt:15`、`:84-87`）。
- 用途：**诊断/回归告警**，不做采纳准则。
- 三个必须写进报告的偏差：
  1. **空输入返回 0.0 = 最优值**（`:74-87`）——"没数据"与"完美校准"不可区分（T11）。报告必须先断言 `resolvedPredictions > 0`，否则 ECE 记 `null`。
  2. 等宽桶 + FSRS 预测集中在 0.7–0.99 → 多数桶为空，ECE 实际由 2–3 个桶决定；必须同时报 `非空桶数`、`最小桶 n`、`maximumCalibrationDeviation`（`:88-90`）。
  3. 每桶的经验率是用桶内成员自己拟合的 → 桶越稀疏，ECE 越**乐观**。跨批比较仅在"同 n 量级、同预测分布"时成立。
- 外部参照：srs-benchmark 用 `RMSE(bins)`（按间隔/复习次数/遗忘次数分桶）而非等宽 ECE（`README.md:32`）——若将来要跨模型比校准，应迁到 bins 口径，但那是一次独立变更。

### 4.4 realized recall

- 定义：`correct ⇔ rating ≠ AGAIN`（`SchedulingEvaluation.kt:37`）。
- 用途：可解释读数；与 `r*` 的偏差告警（spec §2.20：周实际回忆率与 r* 偏差 > 0.1 告警）。
- 偏差：把 HARD/GOOD/EASY 折叠成同一档，**丢失强度信息**；对"刚好想起来"与"轻松想起"不再区分。故它适合当"是否崩"的护栏，不适合当"是否变好"的主指标。
- 在模拟里，考试日召回**不是** realized recall：探针不入 review_log、不更新记忆（见 §5.3）。

### 4.5 工作量守恒（workload conservation）

这是本文件对 G6 的核心贡献：**没有守恒约束，扩容必然"赢"**。

```text
对每个 world w、臂 a、日 d：
  B_d      = 当日基础预算（设置存储优先；默认 dailyReviewMinutes=20 → 1200s）
  E_d(a)   = examCapacityBudgetSeconds 的增量 = int(B_d · f · ramp_d)
  U_d(a)   = 当日计划内题目的估时之和（LogDurationModel）
收集：
  totalUsed(a)        = Σ_d U_d(a)
  budgetUtilization   = totalUsed / Σ_d (B_d + E_d)
  overrunDays         = #{d : U_d > B_d + E_d}          ← 应为 0
  conservationDelta   = (totalUsed(a) − totalUsed(0)) / totalUsed(0)
  postWindowBacklog   = 考试后 14 天内"到期未复习"的估时之和
```

- **等时臂（主对照）**：对臂 `a` 用二分法调低基础预算 `B`（乘 `ρ`），使 `totalUsed(a,ρ) = totalUsed(0)`，再比结果。这样比的是**同一份时间怎么花**，不是"多花时间"。
- **增时臂（附报）**：保留 `E_d`，报 `每分钟边际收益 = Δ考试日召回 / (totalUsed(a) − totalUsed(0))`。若等时臂无增益而增时臂有 → 结论是"拿时间买召回"，交产品裁定，不自动采纳。
- 不变式（可机检）：
  1. 窗口外 `E_d = 0` 且当日计划与无考试臂**逐位相同**（既有用例已盖到计划层：`StudyReviewPlannerExamWindowTest.kt:83`；模拟必须逐日断言）。
  2. `U_d ≤ B_d + E_d`（超发=0）。
  3. `Σ_d E_d ≤ Σ_d B_d·f·ramp_d`（扩容不漏）。

### 4.6 策略级补充指标

| 指标 | 定义 | 为什么需要 |
|---|---|---|
| 考试日召回 | 考试日对**考试科每个 KC** 抽 `K` 次探针（Bernoulli 于真实状态），取均值；探针不入账、不更新记忆 | G6/B 线主指标；这是"考试要的东西"，不是"复习要的东西" |
| 考试科覆盖 | 窗口内至少复习一次的考试科 KC 占比 | 区分"排序变好"与"覆盖变好" |
| 掌握数 | horizon 末 `稳定度 ≥ 21 ∧ R ≥ 0.9` 的 KC 数（`AlgorithmConstants.kt:63,66`） | 长期结果读数；**只读不改阈值** |
| 饥饿时长 | 每个 KC 相邻两次复习的最大间隔天数（p95/max） | 防"主指标涨、某科饿死" |
| 队列抖动 | 相邻两日选中集合的 Jaccard；跨臂的 flips 比例 | 防权重把排序变成噪声 |
| per-reason 分派量 | 每原因被选中的题数/占比 | 解释"权重改后到底改了什么"，不是准确率 |

### 4.7 指标分配表（谁主谁护谁禁）

| 指标 | A. FSRS | B. 权重 | C. G6 |
|---|---|---|---|
| logLoss | **主** | 禁（仅附报） | 禁（仅附报） |
| Brier | 次 | 护栏 | 护栏 |
| ECE | 诊断 | 诊断 | 诊断 |
| realized recall | 护栏 | 护栏 | 护栏 |
| 考试日召回（探针） | 不用 | **主** | **主** |
| 掌握数 | 护栏 | 次 | 次 |
| 工作量/守恒 | 护栏（防漂移） | **约束（等时）** | **约束（等时）+ 附报（每分钟）** |
| 覆盖/饥饿/抖动 | 护栏 | 护栏 | 护栏 |

---

## 5. 三条线的具体设计

### 5.1 A 线：FSRS 参数拟合

**既有协议（不得另立）**：过滤口径 `fittableReviewSamples`（`SchedulingEvaluation.kt:251-254`，排除 MODEL_JUDGED / REVEAL）→ 可预测样本计数（`:580-600`）→ `<400` 返回 `INSUFFICIENT_DATA`（`:488-498`、`:701`）→ 15 个自由维（`:501`）→ 80/20 全局时间分位（`:509-517`、`:713`）→ 纯 BCE + L2 罚项（`:665-680`，`γ=1`、σ 见 `:724-727`）→ 采纳门"不劣于存量"（`StudySchedulingCalibration.kt:134-143`）。

**模拟设计**：

| 项 | 取值/做法 | 理由 |
|---|---|---|
| 世界真值 `θ_true` | `DEFAULT_PARAMETERS` **乘扰动**（预注册冻结，如 `w20×1.3`、`w8×0.7`、`w5×1.2`） | T9：真值=先验中心时，L2 会把估计拉回真值，测不出识别力 |
| 评级词表 | 只用 `AGAIN/HARD/GOOD` 三档（生产 `schedulingRatingFor` 把 EASY 折成 GOOD：`FsrsScheduleMath.kt:260-275`） | 否则 `w3/w16` 在模拟里可辨识、在线上不可辨识，结论不可迁移 |
| 样本量 | ≥5,000 个可预测点/world（既有恢复测试用 800 卡 × 7 间隔 = 5,600，量级一致：`FsrsParameterRecoveryTest.kt:26,58-60`） | 400 是**采纳门**不是**可辨识门**（15 维 ÷ 400 ≈ 27 点/维，靠先验撑住） |
| 拟合窗口 | 只用第 94 天前（最早考试窗口之前） | §3.3 |
| 基线臂 | 不拟合（默认参数） | "拟合有没有用"必须先于"拟合得好不好" |
| 成功判据 | ① held-out logLoss 优于默认臂；② 恢复误差：每个自由维 `\|θ̂−θ_true\| ≤ 0.25×(上界−下界)`；③ **不得**出现 `logLoss(θ̂) < logLoss(θ_true) − 0.01` | ③ 是金丝雀：真值应接近最优，若估计"打败真值"，先查泄漏/切分而不是庆祝 |
| 边界守卫 | 任一自由维在 ≥3/10 world 命中边界（`LOWER_BOUNDS`/`UPPER_BOUNDS`，`:454-461`）→ 判"该维在本样本量下不可辨识"，从拟合集剔除并登记 | 采纳边界值等于把"数据不够"写成"参数如此" |

**真实库**：400 门以下 → `INSUFFICIENT_DATA`，报告标 `BLOCKED`（不填数）。400–1,000 条时先验主导、参数移动很小，**正确结论是"采纳门（不劣）通过/不通过"，不是"参数已个性化"**。

**既有恢复测试的定位（诚实登记）**：`FsrsParameterRecoveryTest` 的真值就是 `DEFAULT_PARAMETERS[20]`（`:22`），且先验中心也是它（`:615`、`:676`）——它证明"loss 对 w20 有梯度、拟合器会动"，**不证明"能识别偏离默认的个体"**。补扰动臂是新增用例，不是改既有断言。

### 5.2 B 线：per-reason 权重

**可行域（先验，不许破）**：spec §6.6 要求"调整保持单调序（due > weakness > 重错≈考前 > 等待/lapse）"（`docs/specs/mastery-scheduling-spec.md:241`）。现值 `5.0 > 3.0 > 2.0 ≈ 2.0 > 1.5 > 1.0` 正好落在该序上（`AlgorithmConstants.kt:114-120`）。**扫描是"在序锥内移动"，不是"自由重估 8 个数"**——这把搜索空间压掉一个量级，也是唯一能防"权重互相抵消"的手段。

`KC_DROP_WEIGHT = 2.5`（`ReviewPlanner.kt:22`）落在 weakness 与 repeat/exam **之间**，spec §6.6 的序里没有它；把它纳入扫描前必须先由人裁定它在序中的位置（默认**不扫**，只单源化）。

**网格（建议，预注册冻结）**：每个权重乘 `{0.5, 0.75, 1.0, 1.25, 1.5}`，**单坐标**，一次只动一个；`1.0` 是空臂。8 个权重 → 每 world 32 个非空臂 + 1 基线。**禁止**多坐标联合扫描、禁止连续优化/贝叶斯优化（在 10 个 world 上拟合响应面就是再过拟合一次）。

**为什么必须走完整 planner**：打分不是纯加性——`MAX_WEAKNESS_ONLY_SHARE = 0.25`（`ReviewPlannerV2.kt:1079`）、`MAX_PER_KNOWLEDGE_NODE_PER_SESSION = 2`（`:1064`）、`MAX_SAME_SUBJECT_RUN = 3`（`:1092`）、beam search 与预算截断都会改写结果。**只在 `scoreCandidate` 上算分值变化是无效测量**，必须 `plan()` 出队列再看结局。

**主指标**：等时下的考试日召回（考试科）。**次**：horizon 末掌握数。**护栏**：任一 KC 饥饿 >21 天且当时可复习 → 拒绝；多样性上限违反 → 拒绝；掌握数下降 >2% → 拒绝。

**最小效应**：`median Δ ≥ +1.0pp` 且 ≥8/10 world 同号（§3.4 的分辨率算术）。低于此值 → "无可测差异"，**保留现值**。

**关于 spec §6.6 的"附前后 log-loss 对照"**：权重不产生概率，这个对照只能在**模拟出的两条复习流各算一次**，且两条流的样本分布不同——它是**描述**，不是判据。建议在报告里写成"附报（非同分布，不可作裁决）"，并在 spec 后续修订时澄清（本文件不改 spec）。

### 5.3 C 线：G6 考前扩容

**被测量的对象先钉死**：

- 计划队列路径：`createReviewPlan` 把 `examCapacityBudgetSeconds(...)` 的结果**直接**作为 `timeBudgetSeconds`（`StudyReviewPlannerService.kt:479-488`、`:615`、`:636`）；即 `B_d → B_d·(1 + f·ramp_d)`，`ramp_d = 1 − d/14`，窗口 `d ∈ [0,14]`（`:169-189`、`:960`）。
- 容量预览/配额路径：`reviewCapacityPreview` 传入 `baseBudgetSeconds = effectiveBudgetSeconds` 且 `growthCeilingBudgetSeconds = effectiveBudgetSeconds.takeIf { it > baseBudgetSeconds }`（`:383-393`、`:405`、`:409`）。于是**原本的 `MAX_DAILY_GROWTH = 1.5` 上限被替换掉**（`ReviewCapacityPolicy.kt:66`、`ReviewCapacityScheduler.kt:56-63`）。**后果**：`f=0` 时 `takeIf` 不成立（effective == base）→ 仍走 1.5×；`f=0.15` 起上限被换成 1.15×。即从 0 到 0.15，**日容量上限是下降的**（1.5× → 1.15×），"扩容"反而缩容。这是 T7，必须在报告里单列，否则响应面会出现无法解释的非单调。

**臂定义**：

| 臂 | 考试记录 | `f` | 说明 |
|---|---|---|---|
| `A0` | 无 | — | **真空臂**（唯一能回答"加考试这件事有没有用"） |
| `A1` | 有 | 0 | 只有 `EXAM_WEIGHT=2.0` 与 `R<0.8` 早复习闸门；**不是空臂**（T6） |
| `A2..A5` | 有 | 0.15 / 0.25 / 0.35 / 0.5 | 计划 §P1-7 的网格 |

**归因分解（每次必须报四列）**：窗口内考试科候选数 → 其中"R<0.8 因而可被考试理由提前"的题数（`ReviewPlannerV2.kt:636-648`、`EARLY_REVIEW_MAX_RETRIEVABILITY` 见 `AlgorithmConstants.kt:111`）→ 实际入选数 → 实际作答数。**若第二列很小，实验对"形状"是无信息的（underpowered），结论只能写"该世界下 G6 无可测作用"，不能写"G6 无效"。**

**主指标与裁决**：

1. 等时臂 `median Δ考试日召回 ≥ +1.0pp` 且 ≥8/10 同号 → 采纳该 `f`。
2. 多个 `f` 都过门 → **取最小的 `f`**（最小侵入；更大 `f` 的额外收益必须另行证明超过分辨率）。
3. 等时臂不过、增时臂过 → 报"每分钟边际收益"，**不采纳**，交产品裁定。
4. 最优值落在网格端点（0 或 0.5）→ 只报方向，不采纳（说明网格不够宽或响应面单调，需要新预注册）。

**必须报的护栏**：窗口外逐位不变（逐日）；`overrunDays = 0`；`Σ E_d` 不超；考后 14 天 `postWindowBacklog` 不明显上升；非考试科在考后 30 天的覆盖不低于 A0 的 90%。

**真实库**：G6 没有随机化/switchback 数据，**真实库无法裁决**；只能验证"窗口外逐位不变"这一条不变量。报告标 `BLOCKED`（结构原因），并注明"结论为 model-based"。

---

## 6. 样本门槛（既有 vs 本文件建议）

**分母口径（唯一）**：可预测长程数 = `predictableSampleCount`（`SchedulingEvaluation.kt:580-600`）= `LongRangeSampleLedger` 的 `PREDICTABLE_LONG_RANGE` 桶（`LongRangeSampleLedger.kt:105-107`）。原始行数、可拟合行数都**不是**门槛分母。

| # | 门槛 | 值 | 性质 | 不足时动作 |
|---|---|---|---|---|
| 1 | FSRS 拟合触发 | 400 可预测长程 | **既有** `MIN_SAMPLES_FOR_FITTING`（`:701`）；Anki 的 400 门槛注释同口径（`params.rs:78`，强制点未逐行定位） | `INSUFFICIENT_DATA` + 默认参数；报告 `BLOCKED` |
| 2 | FSRS 评估门 | 200 评估样本 | **既有** `MIN_EVALUATION_SAMPLES`（`:140`） | `fsrsBeatsBaseline=false`；不得据此改参数 |
| 3 | w15 解锁 | ≥5,000 预测点 ∧ ≥100 HARD ∧ 验证增益 >2% | **既有**（`:529-540`、`:703-711`） | 不解锁（w15 固定默认） |
| 4 | per-reason 标定激活 | 每原因 200 | **既有** `PlannedReasonCalibration.MIN_SAMPLES`（`:746-750`）；spec §6.6 | 只出 `n`，不出比率 |
| 5 | per-reason **采纳** | 每原因每臂 ≥200，且跨 world 合并 ≥2,000 | **本文件建议** | 该原因不参与本轮裁决 |
| 6 | source 校准 | 30 对 | **既有** `MIN_PAIRED_OUTCOMES`（`:112`），降级边际 0.15 | 不出建议 |
| 7 | 期望保留率推荐 | 20 张卡 | **既有** `OptimalRetention.MIN_CARDS`（`OptimalRetention.kt:34`） | 返回 null |
| 8 | 拟合器自身验证（模拟） | ≥5,000 预测点/world | **本文件建议**（与既有恢复测试 5,600 同量级） | 实验无效，扩 world/延长 horizon |
| 9 | 考试日探针 | ≥6 个考试科 KC × 20 次/world | **本文件建议**（600 次抽样 → 单 world SE≈1.6pp） | 该 world 标 underpowered |
| 10 | world 数 | dev 10 / holdout 10 | **计划 §P1-7** | 不得用 dev 冒充 holdout |
| 11 | 最小效应 | A 线 logLoss ≥0.02（dev）/≥0.01（holdout）；B/C 线 ≥1.0pp | **本文件建议** | 判"无可测差异"，保留现值 |

> 门槛 5/8/9/11 是**新提出的**，必须在预注册里冻结后才生效；它们不替换任何既有常数（1/2/3/4/6/7 一个都不动）。

---

## 7. 防过拟合规则（12 条硬规则）

| # | 规则 | 违反后的后果（必须写进报告） |
|---|---|---|
| R1 | **预注册冻结**：网格、指标、护栏、seed 列表、裁决规则、错配参数、代码 SHA | 报告作废；结论标 `UNREGISTERED` |
| R2 | **holdout 只跑一次**；看后再改 = 新预注册，旧 holdout 标"已污染" | 该 holdout 永久退役，换新 seed 基 |
| R3 | **配对世界**：同 world、同 noise，仅换臂 | 未配对的比较降级为"观察"，不作裁决 |
| R4 | **网格 + 单坐标**；禁自由优化、禁贝叶斯优化、禁多坐标联合 | 结果只报方向 |
| R5 | **先验约束**：spec §6.6 单调序当作可行域 | 越序的方案直接不评估（不是"评估后拒绝"） |
| R6 | **最小效应 + 符号一致 + cluster CI**；三者缺一即"不采纳" | 默认维持现值 |
| R7 | **边界不采纳**：最优在网格端点 → 只报方向 | 需新预注册扩网格 |
| R8 | **一轮一变量**；采纳必须一次性完成 `VERSION` bump + 三组金样重生成 + 集成回归（计划 §硬约束） | 半途变更视为未发生；回滚 |
| R9 | **同数据训练+评估不得当成绩**；只可作过拟合诊断（srs-benchmark `README.md:290` 同法） | 该数字只进"overfit gap"一节 |
| R10 | **伪重复防御**：每卡末态一对；CI 按 world 聚类 | 以复习行当独立样本的 CI 一律不采信 |
| R11 | **不可辨识即冻结**：`w3` 结构性消失、`w16` 钉 1.0（`:499-501`）；边界命中 ≥3/10 → 剔除 | 该维不进采纳集 |
| R12 | **无数据不填数**：`NaN`/`null` + `BLOCKED`；禁止 `0.0` 冒充（T11） | 报告校验失败 |

补充两条工程纪律：

- **确定性即防作弊**：`FsrsParameterOptimizer.optimize` 是确定性的（固定初值 `:615`、固定迭代 `:712`、中心差分），"反复点重拟合"不会产生"挑最好一次"的通道——这条性质要保住，禁止给它加随机重启。
- **金样语义**：`ReviewPlannerScaleBenchmarkTest` 的三组金样钉的是"选卡序列 + reasons + 分值 hex"，**不含版本串**（`:24-36`、`:63-79`）。所以 VERSION bump 不会让它们红，**权重/G6 一变它们必红**——这正是"权重变更必须一次性重生成金样"的机制，不能改成"跳过"。

---

## 8. 报告与证据格式（供 P1-8 直接消费）

每个 `(world, arm)` 一条记录；规范化 JSON + `sha256`。**下面所有数值只是形状示例，不是任何实测结果**：

```json
{
  "meta": {"batch": "49", "line": "G6", "prereg_sha256": "<hex>", "code_sha": "<hex>", "model_based": true},
  "world": {"world_seed": 101, "noise_seed": 7, "kc_count": 30, "subject_count": 5,
             "question_count": 300, "horizon_days": 120,
             "exam_days": [108, 112, 116, 118, 120],
             "mis_spec": "W-slip+W-transfer"},
  "arm": {"value": 0.25, "budget_neutral_rho": 0.972},
  "counts": {"predictable_samples": 5123, "scored_points": 4980, "probes": 600,
              "per_reason": {"WEAK_KNOWLEDGE": 412, "EXAM_PRIORITY": 88}},
  "metrics": {"log_loss": "0x1.a5p-2", "brier": "0x1.75p-3", "ece": "0x1.fp-6",
               "realized_recall": "0x1.9ep-1", "exam_day_recall": "0x1.8cp-1"},
  "workload": {"total_used_seconds": 1234567, "budget_utilization": "0x1.e1p-1",
                "overrun_days": 0, "extra_vs_baseline": "0x1.02p-5",
                "post_window_backlog_seconds": 4200},
  "guardrails": {"out_of_window_bit_identical": true, "starved_kc_max_gap_days": 18,
                  "diversity_cap_violations": 0, "mastery_count": 173},
  "digest": "sha256:<hex>"
}
```

格式纪律：

- 浮点用 `Double.toHexString`，十进制只用于人读副本（两者都留，摘要只用 hex）。
- `counts` 的分母与 §6 的口径一致；分母为 0 时指标写 `null`，不写 `0`。
- 缺样本的格子写 `{"n": 37, "value": null, "reason": "below_floor_200"}`。
- 每条记录必须带 `model_based` 与 `mis_spec`，否则读者无法判断这是"世界里的结论"还是"产品的结论"。

---

## 9. 未验证 / BLOCKED 登记

| 项 | 状态 | 原因 | 下一步 |
|---|---|---|---|
| 真实库 per-reason 权重的因果效应 | **BLOCKED（结构性）** | 无反事实、无倾向得分（§2.2） | 只能模拟 + 在线随机化（若将来做 A/B/switchback） |
| 真实库 G6 的因果效应 | **BLOCKED（结构性）** | 无随机化；单用户时间序列无法分离"考试临近"与"季节/动机" | 只能模拟 + 不变量检查 |
| spec §2.17 的 `r*_exam` 队列准入 | **未实现** | 代码用固定 `EARLY_REVIEW_MAX_RETRIEVABILITY=0.8`（`AlgorithmConstants.kt:111`），非 `r*_exam` 坡道（`2026-11-02-fsrs-capacity.md` C3） | 本轮不测；若将来实现，另立预注册 |
| 容量预览路径的 G6 净效应 | **本轮只登记机制**（T7） | `growthCeilingBudgetSeconds` 替换 1.5× 上限（`:392-393`） | 模拟报告必须分路径出数 |
| 本文件建议的门槛（§6 #5/#8/#9/#11） | **未验证的提案** | 由分辨率算术与既有测试量级推出，未在本会话跑过任何模拟 | 预注册冻结时确认；首轮模拟后按实测方差复核 |
| Anki "exam mode" | **不存在（一手核验为负）** | `decks.proto`/`simulation.rs` 命中 0；FAQ 只给设置建议 | 报告中不得引用该功能名 |
| `fsrsBeatsBaseline` 的生产消费者 | **仍为零** | 全仓仅 `SchedulingEvaluation.kt:134` 定义；`evaluateSchedulingModels()` 无 app/feature 调用（`docs/audit-2026-09-12-kernel-readiness.md:535`） | 计划 §P1-10 的"上线门"必须先在模拟 + 真库两侧**各有一个读点**，否则仍是"不会响的门" |
| spec §2.11 文本 | **与代码不一致（未改）** | spec 仍写"8/64 双门槛 + patience=5 早停"（`docs/specs/mastery-scheduling-spec.md:119`），代码已是 400 门、无早停（`SchedulingEvaluation.kt:488-498`、`:602-608`） | 文档对齐项，另行处理；本文件按代码口径 |

---

## 10. 来源

**仓内（本会话逐行读过）**

- `core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/SchedulingEvaluation.kt`（`:37`、`:112`、`:134-141`、`:145-225`、`:251-254`、`:316-334`、`:339`、`:349-382`、`:454-461`、`:488-501`、`:509-540`、`:543-572`、`:580-600`、`:602-698`、`:701-732`、`:746-750`）
- `core/domain/src/main/kotlin/.../CalibrationReportBuilder.kt`（`:15`、`:43-59`、`:72-90`）
- `core/domain/src/main/kotlin/.../AlgorithmConstants.kt`（`:63`、`:66`、`:111`、`:114-120`、`:148`）
- `core/domain/src/main/kotlin/.../ReviewPlannerV2.kt`（`:636-668`、`:1055`、`:1064`、`:1079`、`:1092`、`:1097`）
- `core/domain/src/main/kotlin/.../ReviewPlanner.kt:22`；`.../OptimalRetention.kt:34-38,62-64`；`.../LongRangeSampleLedger.kt:105-107`；`.../FsrsScheduleMath.kt:16-46,198-275`
- `core/domain/src/test/kotlin/.../FsrsParameterRecoveryTest.kt`（`:22`、`:26`、`:37`、`:40-50`、`:58-100`）；`.../ReviewPlannerScaleBenchmarkTest.kt`（`:24-36`、`:63-79`、`:85`）
- `core/data/src/main/kotlin/.../StudySchedulingCalibration.kt`（`:41-49`、`:95-143`、`:191`）；`.../StudyReviewPlannerService.kt`（`:169-189`、`:392-409`、`:479-488`、`:615`、`:636`、`:960`）；`.../ReviewCapacityPolicy.kt:66`；`.../ReviewCapacityScheduler.kt:56-63`
- `core/domain/src/main/kotlin/.../StudyExperienceRepository.kt:736-739`；`docs/specs/mastery-scheduling-spec.md`（§2.11 `:108,119`、§2.17 `:138`、§2.20 `:148`、§6.6 `:241`）
- 既有研究：`docs/research/2026-09-25-mastery-mechanism-review.md:66,105`；`docs/research/2026-11-02-fsrs-capacity.md` C2/C3；`docs/research/2026-11-03-fsrs-daily-cap-allocation.md` C5/C6；`docs/audit-2026-09-12-kernel-readiness.md:535,1910`

**上游（本会话一手取数）**

- fsrs-optimizer `src/fsrs_optimizer/fsrs_optimizer.py`：`:79`、`:446`、`:470`、`:509`、`:513`、`:514-523`、`:575`、`:579-584`
- Anki `rslib/src/scheduler/fsrs/params.rs`：`:66-76`、`:78`、`:153-156`、`:182-198`；`proto/anki/decks.proto:74-87`；`docs-site/faqs/settings-for-using-anki-to-prepare-for-a-large-exam.mdx`
- Anki 手册 `https://docs.ankiweb.net/deck-options.html`（desired retention 默认 90%、>90% 负载快速上升、>97% 不堪重负；CMRR 自 25.07 移除；"Days to simulate" 属 Simulator）
- srs-benchmark `README.md:19-23,29-35,44,83,178,290,310,320`
- fsrs-rs `src/simulation.rs:281,579-581,587-606,613`

**核验命令（本会话实跑；此处只保留被引用的行，完整 stdout 见 `docs/research/evidence/2026-11-08-batch-49/R1-calibration-method-evidence.md`）**

```text
$ gh api repos/open-spaced-repetition/fsrs-optimizer/contents/src/fsrs_optimizer/fsrs_optimizer.py --jq '.content' 2>/dev/null | base64 -d | grep -n "binary_cross_entropy\|def _loss\|penalty\|gamma\|seq_len\|DEFAULT_PARAMS_STDDEV" | head -40
79:DEFAULT_PARAMS_STDDEV_TENSOR = torch.tensor(
446:        gamma: float = 1,
470:        self.loss_fn = nn.BCELoss(reduction="none")
509:                stabilities = outputs[seq_lens - 1, torch.arange(real_batch_size), 0]
514:                penalty = (
519:                    * self.gamma
523:                loss += penalty
575:                stabilities = outputs[seq_lens - 1, torch.arange(real_batch_size), 0]
584:                loss += penalty * self.gamma / train_set_size

$ gh api repos/ankitects/anki/contents/rslib/src/scheduler/fsrs/params.rs --jq '.content' 2>/dev/null | base64 -d | grep -n "400\|MIN_\|log_loss\|RMSE\|rmse\|health" | head -30
78:    /// Note this does not return an error if there are less than 400 items -
153:            let current_log_loss = current_fsrs.evaluate(items.clone(), |_| true)?.log_loss;
155:            let optimized_log_loss = optimized_fsrs.evaluate(items.clone(), |_| true)?.log_loss;
156:            if current_log_loss <= optimized_log_loss {
182:        let health_check_passed = if health_check && input.train_set.len() > 300 {
195:                    let adjusted_log_loss = eval.log_loss / log_loss_adjustment(r);
198:                    adjusted_log_loss <= 1.11 || adjusted_rmse <= 1.53

$ gh api repos/ankitects/anki/contents/proto/anki/decks.proto --jq '.content' 2>/dev/null | base64 -d | grep -c -i "exam\|deadline"
0
$ gh api repos/open-spaced-repetition/fsrs-rs/contents/src/simulation.rs --jq '.content' 2>/dev/null | base64 -d | grep -c -i "exam\|deadline\|quota"
0
```

> 未跑：任何 Gradle 任务、任何模拟、任何设备用例。本文件的所有"建议值"均为设计推导，不是实测结果。
