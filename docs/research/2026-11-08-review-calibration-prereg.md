# 批次 49 · 复习标定预注册（G6 幅度 / 权重坐标 / 掌握阈值读数 / A 线拟合）

| 字段 | 值 |
|---|---|
| 角色 | 实验预注册员。**只写本文件**：不改源码/常量/测试/金样/台账，不跑 Gradle，不跑设备 |
| 写于 | 2026-11-08 |
| 服务对象 | 计划 `.zcode/plans/plan-2026-11-08-mastery-review-quality.md` §P1-7（预注册）、§P1-8（报告）、§P1-9（行为变更）、§P1-10（上线门） |
| 方法论来源 | `docs/research/2026-11-08-calibration-method.md`（下称 **R1**，sha256 `740cea28…cba2`）。本文件把 R1 的建议落成**可机械核对的冻结项**，不重开 R1 的方法争论 |
| 冻结代码 | HEAD `ba7b5624ea0c2bc3f09dcee247f5852e4f428122` + §1.1 逐文件 sha256（模拟器与 R1 尚未入库，按**工作树哈希**钉） |
| 本会话实跑 | 仅读文件 + `git status` + `sha256sum` + `grep` + 一次 `python -I` 序锥可行性算术（§5.2）。**未跑任何 Gradle / 模拟 / 设备用例** |
| 状态 | **冻结**。落盘后任何冻结项（网格 / 种子 / 指标 / 护栏 / 裁决函数 / 世界形状）变动 = 新预注册 + 新 holdout；旧 holdout 永久标"已污染" |

---

## 0. 冻结摘要（一屏）

| 线 | 冻结对象 | 臂数 | 本轮可裁决？ | 现值 | 裁决产出 |
|---|---|---|---|---|---|
| **G6** | 考前扩容幅度 `f` | 4（0.15/0.25/0.35/0.5），基线 `A1 = f=0` | **可**（harness 已支持 `examCapacityExtraFraction`） | `0.5`（`AlgorithmConstants.kt:148`，KDoc 明写 `PENDING_CALIBRATION`） | 采纳某个 `f`，或维持 0.5 |
| **权重** | 5 个坐标：due / weak / exam / kc-drop / diversity | 17 个非空臂（§5.2 表） | **否——缺注入 seam**（§5.1）。seam 落地并通过默认臂逐位校验后才可跑 | 见 `AlgorithmConstants.kt:114-125`；`KC_DROP_WEIGHT=2.5`（`ReviewPlanner.kt:22`） | 至多采纳 **1 个坐标**，或全部维持 |
| **掌握阈值** | 三组 `(稳定度天, 当前召回)` | 3 组 × 2 真值臂 × 4 读数日 | **只测不改**（ask 硬约束），无采纳 | `(21.0, 0.9)`（`AlgorithmConstants.kt:63,66`） | 只有读数，无提案 |
| **A 线** | FSRS 参数拟合 | 每 world 一次拟合 | 只报 `fsrsBeatsBaseline` 门状态；**可辨识性在本世界形状下不成立**（§7.3） | `DEFAULT_PARAMETERS` | 门通过/不通过/BLOCKED |

**唯一主指标**（G6 与权重共用）：`Y` = 5 场考试的**考前窗口内考试科 KC 覆盖率**的算术平均（§3.1）。
**目标/选择规则**（ask 冻结）的落法：考试科覆盖最大化 = `Y` 最大化；日实际时长 ≤ 预算含 5% 余量 = §3.2 护栏 G1；非考试科不挨饿 = G2 + G3；校准误差不升 = G4。

---

## 1. 冻结基线

### 1.1 代码与哈希（本会话实跑 `sha256sum`）

| 文件 | sha256 前 16 位 + 后 4 位 | 说明 |
|---|---|---|
| `core/domain/.../ReviewPlannerV2.kt` | `ee89aba3dfeb0798…0ecf` | 选卡链；`VERSION = "review-planner-v4"`（`:1055`） |
| `core/domain/.../AlgorithmConstants.kt` | `e695418f5a24f56a…fd84` | 权重表 `ReviewScoring`（`:99-149`）、掌握阈值（`:61-89`） |
| `core/domain/.../ReviewPlanner.kt` | `e5d66b5b00a22b6d…bfbfa` | 仅剩输入契约 + `KC_DROP_WEIGHT`（`:22`）与 `KC_DROP_MASTERY_THRESHOLD`（`:21`） |
| `core/domain/.../ClearlyMasteredForSkipPolicy.kt` | `3cb0af7c7c199a98…599d` | E 判据唯一出口（`:78-96`） |
| `core/domain/.../SchedulingEvaluation.kt` | `e0f3f26aaac5dd15…7338` | **工作树已脏（P0-3 改动）**，故按此哈希钉 |
| `core/domain/src/test/.../ReviewPlannerScaleBenchmarkTest.kt` | `d72a2ed9aa8bc91f…2560` | 三组金样载体 |
| `core/data/.../StudyReviewPlannerService.kt` | `2bd8c40bb6352e5d…948d` | G6 消费点（`:169-189`、`:185`） |
| `core/domain/src/test/.../simulation/SimulatedLearner.kt` | `d170338b35152f1a…c2dc` | **未跟踪（`??`）**，HEAD 不含此文件 |
| `core/domain/src/test/.../simulation/SimulatedLearnerTest.kt` | `94a27fae700e681f…212e` | 未跟踪；种子列表的唯一落盘处（`:197-203`） |
| `docs/research/2026-11-08-calibration-method.md` | `740cea2869469ce7…cba2` | 未跟踪 |

> 报告（P1-8）必须把这张表连同 HEAD 一起写进 `meta`。**未跟踪文件按哈希钉**：只写 HEAD 无法复现本轮实验。

### 1.2 常量现值（逐行核对，不得凭记忆）

| 量 | 现值 | 落点 |
|---|---|---|
| `DUE / WEAKNESS / LAPSE / REPEAT / AVOIDANCE / EXAM / WAITING` | `5.0 / 3.0 / 1.0 / 2.0 / 1.0 / 2.0 / 1.5` | `AlgorithmConstants.kt:114-120` |
| `FAMILY / SOURCE / MAX_DIVERSITY` | `0.3 / 0.2 / 1.5` | `AlgorithmConstants.kt:123-125` |
| `KC_DROP_WEIGHT` | `2.5`（**不在** `ReviewScoring` 里） | `ReviewPlanner.kt:22`，唯一消费点 `ReviewPlannerV2.kt:665` |
| `KC_DROP_MASTERY_THRESHOLD` | `0.6`（**不扫**，见 §5.3） | `ReviewPlanner.kt:21` |
| `G6 = EXAM_CAPACITY_RAMP_MAX_EXTRA_FRACTION` | `0.5` | `AlgorithmConstants.kt:148`；唯一消费点 `StudyReviewPlannerService.kt:185` |
| `EXAM_RAMP_DAYS` | `14` | `StudyReviewPlannerService.kt:960` |
| 掌握阈值 `(S, R)` | `(21.0, 0.9)` | `AlgorithmConstants.kt:63,66` |
| 排程内硬约束 | `MAX_PER_KC=2` / `MAX_WEAKNESS_ONLY_SHARE=0.25` / `MAX_SAME_SUBJECT_RUN=3` | `ReviewPlannerV2.kt:1064,1079,1092` |
| FSRS 先验中心 `w20`（衰减） | `0.1542`（`decay = −0.1542`） | `FsrsScheduleMath.kt:24-46`（本会话用 `python -I` 按索引 20 读出） |

### 1.3 工作树状态（开工前逐文件 `git status` 实测）

```text
$ git status --porcelain <本批目标文件>
 M core/domain/src/main/kotlin/.../SchedulingEvaluation.kt
?? docs/research/2026-11-08-calibration-method.md
?? core/domain/src/test/kotlin/.../simulation/            ← SimulatedLearner.kt + Test
（其余：ReviewPlannerV2 / AlgorithmConstants / ReviewPlanner / ClearlyMasteredForSkipPolicy /
  ReviewPlannerScaleBenchmarkTest / StudyReviewPlannerService → 空，即 clean）
$ git status --porcelain | wc -l   → 594
```

- **整树 594 条未提交路径**（他线在飞）。按 ask 硬约束：本批对上述 clean 文件只做**最小 hunk**；任何整文件抢占视为越界。
- 权重 seam（§5.1）必须落在 `ReviewPlannerV2.kt` 与 `AlgorithmConstants.kt`——**二者当前 clean**，是这一轮唯一"干净可改"的落点；若执行时发现被他人改动，停项、出补丁、登记，不改写。

---

## 2. 模拟世界（P1-6 harness 的冻结口径）

### 2.1 形状（不改）

| 项 | 值 | 落点 |
|---|---|---|
| KC | 30（5 科 × 6） | `SimulationConfig` 默认 `subjectCount=5`、`knowledgeNodesPerSubject=6`（`SimulatedLearner.kt:546-547`） |
| 题 | 300（每 KC 10，2 个题族） | `:548`、`:778` |
| 先修 | 每科首 KC 无先修；其后 1 条，偶数位再加 1 条 | `:816-825` |
| 考试 | 5 科各 1 场：第 **108/112/116/118/120** 天；窗口 14 天 | `:592-598`、`:552` |
| 日预算 | `1200s`（设置默认 20 分钟） | `:550`；生产同源 `StudyExperienceRepository.kt:737-739` |
| horizon | 120 学习日 | `:545` |
| 科目时长画像 | 45/55/65/75/85 s | `:601-607` |
| 时长模型 | 每日按 `LogDurationModel` 逐科预热 | `:80-84` |

### 2.2 种子（唯一落盘处 = 已存在的用例）

| 层 | 字面量 | 用途 |
|---|---|---|
| `dev` | `101,102,103,104,105,106,107,108,109,110` | 开发/冒烟/方差估计；**不得**用于改网格或改裁决 |
| `holdout` | `901,902,903,904,905,906,907,908,909,910` | **一次性裁决**，每个 (arm) 只跑一次 |
| `golden`（CI） | 本节新增：`seed=5001`，`horizonDays=60`，`questionsPerKnowledgeNode=4`（120 题） | 逐位一致回归；见 §8 机检 6 |

落点：`SimulatedLearnerTest.kt:197-203`；该用例的断言就是此三条（各 10 个、交集为空、20 个世界指纹互不相同，`:84-95`）。**本会话只读了源码，没有执行**；最近一轮执行记录见 `docs/research/evidence/2026-11-08-batch-49/p1-6-simulated-learner.md`（`tests="6" failures="0"`），该记录不是本会话产物。

> **与 R1 §3.1 的差别登记**：R1 的 golden 层是"建议"，此前从未落盘。本文件把它冻结为 `seed=5001/60 天/120 题`——这是一条**新增冻结项**，报告必须实际用它并在 CI 用例里钉摘要。

### 2.3 确定性与配对（为什么臂间可比）

- 单 seed 同时决定世界结构与作答噪声（`SimulationConfig.seed`，`:538-541`）。
- 作答抽样种子只由 `(seed, studyDay, practiceUnitId)` 决定（`:402-407`）——**与当日选了哪些题无关**。这是"配对世界"成立的前提：换臂不会打乱后续抽样。
- 同一 `(config)` 两次构造 → `metrics` / `digest()` / `reviewLog` 逐位相同（用例 `SimulatedLearnerTest.kt:25-46` 钉住；本会话未执行）。
- **本文件不引入 `noiseSeed` 第二层**（R1 §3.2 的两级种子）：harness 不区分结构与噪声，hack 一层会让 harness 与 R1 文本不一致而无人能机检。登记为差异，不做。

### 2.4 错配臂（T4：世界必须故意错配于 planner 内部模型）

| 臂 id | 参数 | 错配内容 | 消灭的失败 |
|---|---|---|---|
| `W-slip`（主） | `carelessRate = 0.05`（默认） | `P(答对) = R_world × (1 − 0.05)`，planner 的预测不知道这 5% | "FSRS 概率 = 真值"导致自证；让 Brier 有下界外的可改进空间 |
| `W-decay`（确认） | `truthParameters[20] × 1.3 = 0.20046` → `decay = −0.20046` | 世界遗忘更快，产品侧仍按 `−0.1542` 读 | 世界与 planner 完全同构时任何权重都"最优"；同时是 §6 误判率唯一非退化的测量面 |

- 两臂**各自 10 个 holdout world**；裁决要求两臂同号（§4.3 / §5.4）。
- **未实现的错配**：R1 §3.2 的 `W-transfer`（考试探针 + 复习次数饱和）与"科目级 slip 0.03–0.08"**harness 不支持**。本文件不假装测了它们（§10 登记）。
- **结构性地不建模**（照抄 harness 的自我声明，`:60-66`）：考试日召回探针、avoidance 信号、同日重复作答。因此 `AVOIDANCE_WEIGHT` 在本世界里**不可测**（`candidatesFor` 构造 `ReviewCandidate` 时根本不传 `avoidance`，`SimulatedLearner.kt:339-360`），§5 的 due/weak/exam/kc-drop/diversity 五个坐标里**不含 avoidance**（ask 也未列）。

---

## 3. 指标（定义 → 落点 → 用途）

### 3.1 主指标 `Y`（唯一裁决依据）

```text
Y(a, w) = (1/5) · Σ_{exam e}  reviewedKnowledgeNodeCountInWindow(e, a, w) / knowledgeNodeCount(e)

knowledgeNodeCount(e)            = 6          （该科 6 个 KC）
reviewedKnowledgeNodeCountInWindow(e) = 该科 6 个 KC 中，在 [examDay−13, examDay] 内至少复习过一次的个数
```

- 落点：`SimulationMetrics.examSubjectCoverage[].reviewedShare`（`SimulatedLearner.kt:265-279`，窗口定义 `firstCoveredDay = exam.day − examWindowDays + 1`，`:269`）。
- 方向：**越大越好**。
- **分辨率（必须先认账）**：`1` 个 KC = `1/30 = 3.33pp`。任何小于 1 个 KC 的差异在本世界形状下**不可分辨**；"无可测差异"≠"无作用"。故最小效应取 **1 个 KC = 0.0333**（§4.3），不是 R1 文本里的 1.0pp（后者来自 600 次探针的分辨率，本主指标不适用）。
- 附报（**非裁决**）：`Q(a,w)` = 5 个窗口内考试科**累计作答题数**（粒度 1 题，读数更细，但会被"重复刷同一 KC"抬高，故只作描述）。

### 3.2 护栏（任一 world 破任一护栏 → 该臂拒绝）

| id | 护栏 | 精确定义 | 落点 |
|---|---|---|---|
| **G1** | 日实际时长 ≤ 预算（含 5% 余量） | 对每个 world 的每天：`answeredSeconds ≤ budgetSeconds` 严格成立，**且** `answeredSeconds ≤ floor(1.05 × budgetSeconds)`。两者计数都必须为 0 | `SimulationMetrics.dailyWorkload`（`:216-222`）、`:304` 的 `overrunDays` |
| **G2** | 非考试科不挨饿（按科） | 对每科 `s`：`recentShare(a,s) ≥ 0.9 × recentShare(A1,s)`，其中 `recentShare(a,s)` = 该科 6 个 KC 中在第 91–120 天内至少复习过一次的占比（从 `run.attempts` 现算） | `SimulationRun.attempts`（`:196-205`：day/subjectId/knowledgeNodeId） |
| **G3** | 非考试科不挨饿（按 KC） | `kcGapP95(a) ≤ max(21, kcGapP95(A1))`。`kcGap` = 单个 KC 在第 31–120 天内相邻两次复习的最大间隔天数（期间从未复习记 90 天）；P95 用 nearest-rank 定义在 30 个 KC 上 | 同上（现算） |
| **G4** | 校准误差不升 | `median_w [ Brier(a,ρ*,w) − Brier(A1,w) ] ≤ +0.005`；ECE/logLoss 只附报（ECE 只有 2–3 个非空桶，R1 §4.3，不作门） | `SimulationMetrics.brierScore / expectedCalibrationError / logLoss`（`:292-294`） |

- **G3 的 21 天不是新发明**：直接复用 `AlgorithmConstants.Mastery.MIN_STABILITY_DAYS = 21`（`:63`）作为"mature"读数，**只读不改**。
- **G4 的 +0.005 是本文件预注册的设计容差**（未实测）。首个报告必须记录观测到的跨 world SD，下一份预注册可据此修订；**不得**在看到 holdout 后改这个数。
- **G1 在模拟里是结构性成立**的（planner 的 `fitting` 过滤保证 `estimatedDurationSeconds ≤ remainingSeconds`，`ReviewPlannerV2.kt:184-188`；模拟不丢题，`SimulatedLearner.kt:63-67` 用例已断言）。若 G1 破，先查 harness 而不是解读结果。

### 3.3 描述量（只进报告，不进裁决）

`masteredKnowledgeNodeCount`（`SimulatedLearner.kt:245-253`，产品侧 21/0.9 口径）、`perReasonRecall`（`:300`，`PlannedReasonCalibration`，`SchedulingEvaluation.kt:739-750`）、每科 horizon 覆盖（`:255-264`）、`Q`、`totalAnsweredSeconds`、`overrunDays`。

### 3.4 禁止当裁决准则的量（R1 T1/T2/T8 已裁定，本文件重申）

`logLoss`（策略线不产生概率）、`per-reason realized recall`（条件在"被选中"，选择偏倚）、原始行数（分母口径只认 `predictableSampleCount`）、`totalAnsweredSeconds`（扩容必然抬高它）。

---

## 4. G6 实验（考前扩容幅度 `f`）

### 4.1 臂

| 臂 | 考试记录 | `f` | 说明 |
|---|---|---|---|
| `A1` | 有 | `0` | **基线**。不是空臂：`EXAM_WEIGHT=2.0` 与 `R<0.8` 早复习闸门仍在（`ReviewPlannerV2.kt:636-648`、`:659-668`） |
| `A2` | 有 | `0.15` | |
| `A3` | 有 | `0.25` | |
| `A4` | 有 | `0.35` | |
| `A5` | 有 | `0.50` | **现值**（`AlgorithmConstants.kt:148`） |
| `A0` | 无 | — | **本批不测**（§10）：没有考试记录时 `examSubjectCoverage` 为空，`Y` 无定义；harness 缺"无考试窗口指标"。R1 T6 的 A0 问题保持开放 |

- 配置表达：`SimulationConfig(seed = w, examCapacityExtraFraction = f)`，其余默认。**不改 harness 默认值**（`SimulatedLearner.kt:552`）。
- 模拟只覆盖**计划队列路径**（`budgetSecondsFor` 复刻 `examCapacityBudgetSeconds` 形状，`:380-395`）。容量预览/配额路径（`StudyReviewPlannerService.kt:383-405` 的 `growthCeilingBudgetSeconds`，`ReviewCapacityPolicy.kt:66` 的 1.5× 上限被替换）**不在本轮裁决范围**——R1 T7 的机制登记继续有效，报告单列，不得把模拟数当那条路径的结论。

### 4.2 等时化（主对照；没有它"扩容必然赢"）

```text
B0 = 1200s
R  = {1.000, 0.975, 0.950, 0.925, 0.900, 0.875, 0.850, 0.825, 0.800, 0.775, 0.750}
对每个 (f>0, w)：
  T(f,ρ,w)  = 该配置的 totalAnsweredSeconds（dailyBudgetSeconds = floor(1200·ρ)）
  ρ*(f,w)   = argmin_{ρ∈R} |T(f,ρ,w) − T(A1,w)| / T(A1,w)     （并列取更大的 ρ）
  合格条件   = |T(f,ρ*,w) − T(A1,w)| / T(A1,w) ≤ 0.015
  不合格     = 该 (f,w) 记 NO_EQUAL_TIME，从该臂的中位数里剔除，报告写 n
```

- `ρ` 同时缩放**基础预算与扩容增量**（`extra = B·f·ramp`，`:391-393`），因为 harness 只有一个 `dailyBudgetSeconds` 入口。这是与 R1 §4.5"调低基础预算 B"字面一致、实现上必然耦合的一点，**在此冻结并声明**。
- 用**固定 ρ 网格**而非二分：`totalAnswered(B)` 未必单调（beam + localSwap + 整数时长），二分的前提不成立，宁可粗网格 + 明确容差。
- 主裁决一律用**等时臂**；`ρ = 1.0` 的 as-configured 结果作附报，并给出"每分钟边际收益 = ΔY / (Δtotal/60)"（单位 pp/分钟）。

### 4.3 裁决函数（伪代码，逐条可机检）

```text
DECIDE_G6():
  for f in {0.15, 0.25, 0.35, 0.50}:
    for w in HOLDOUT:
      Δ[f,w] = Y_eq(f,w) − Y(A1,w)
    pass[f] = (median_w Δ[f,w] ≥ 0.0333)                    # ≥ 1 个 KC（§3.1 分辨率）
            ∧ (#{w : Δ[f,w] > 0} ≥ 8)                        # 符号一致
            ∧ GUARD(f)                                        # G1–G3 逐 world、G4 取跨 world 中位（§3.2）
            ∧ f ≠ 0.50                                       # 网格端点不采纳（R7）
  if {f : pass[f]} == ∅            → KEEP 0.50，报告"无可测差异/未过门"
  f* = min{f : pass[f]}                                      # 多个过门取最小幅度（R1 §5.3）
  CONFIRM on W-decay 臂：同向世界 ≥ 8/10，否则 → CONDITIONAL（不采纳）
  ADOPT f*
```

- **符号一致性对应的一次性描述量**：10 配对 world、H0 = 硬币时 `P(X≥8) = 56/1024 = 0.0547`。**8/10 不是 5% 显著性检验**，报告必须原样写出这个 p 值，禁止写成"显著"。
- 不确定性区间：10 个 world 的配对差做 cluster bootstrap（重抽 world，不重抽复习行），10,000 次，**固定 seed `20261108`**，报 2.5/97.5 分位。
- 端点规则的效果：`f=0.5` 是现值也是网格上端，所以"最优在端点"= 维持现值，报告写"方向 + 不采纳"。

---

## 5. 权重坐标实验

### 5.1 前置：注入 seam（**当前不存在**，这是硬阻塞）

`ReviewPlannerV2` 的权重是 `private const val` 引用 `AlgorithmConstants.ReviewScoring`（`:1059-1096`），构造函数只有 `forgettingCurve` / `durationModel`（`:30-33`）；`KC_DROP_WEIGHT` 是 `ReviewPlanner.kt:22` 的 `internal const val`。**没有注入点，17 个权重臂一条都跑不了。**

冻结 seam 契约（供执行者落地，落地前权重线一律标 `BLOCKED: NO_INJECTION_SEAM`）：

```kotlin
data class ReviewScoringWeights(
    val dueWeight: Double = AlgorithmConstants.ReviewScoring.DUE_WEIGHT,
    val weaknessWeight: Double = ...,
    val lapseWeight: Double = ...,
    val repeatMistakeWeight: Double = ...,
    val avoidanceWeight: Double = ...,
    val examWeight: Double = ...,
    val waitingWeight: Double = ...,
    val familyPenaltyWeight: Double = ...,
    val sourcePenaltyWeight: Double = ...,
    val maxDiversityPenalty: Double = ...,
    val kcDropWeight: Double = 2.5,          // 从 ReviewPlanner.kt:22 迁入（单源，P1-9）
) { companion object { val DEFAULT = ReviewScoringWeights() } }

class ReviewPlannerV2(..., private val scoring: ReviewScoringWeights = ReviewScoringWeights.DEFAULT)
```

seam 的机检（缺一不可，全部要在**同一轮**完成）：

| # | 检查 | 若失败意味着 |
|---|---|---|
| S1 | `ReviewPlannerScaleBenchmarkTest` 三组金样（60/500/5000）逐位不变 | 默认路径被改了 → 不是 refactor，是行为变更 |
| S2 | `ReviewPlannerV2Test` 全绿 | 同上 |
| S3 | 显式传 `DEFAULT` 与不传，在三组金样夹具上 `plan().planFingerprint` 相同 | 默认值没对齐（某处硬编码） |
| S4 | `git diff core/domain/.../AlgorithmConstants.kt` 数值零变更 | 顺手改了权重 → 违反"本轮只加 seam" |
| S5 | `grep -rn "KC_DROP_WEIGHT" core/` 只剩 seam 定义一处引用 | 双源残留（Q5 同型分叉） |

- seam 本身**不 bump** `ReviewPlannerV2.VERSION`（S1–S3 证明输出逐位不变）；bump 只在采纳非默认值时发生（§11）。
- 若执行时选择"不改生产类、另建一个测试内 shadow planner"→ **本预注册作废该线**：R1 §5.2 明确"只在 `scoreCandidate` 上算分值变化是无效测量，必须出队列"，shadow 副本会让金样/生产分叉。

### 5.2 网格与序锥可行性（本会话 `python -I` 实算）

**先验约束**（spec §6.6，`docs/specs/mastery-scheduling-spec.md:241`）：`due > weakness > 重错≈考前 > 等待/lapse`。落成三条可判定不等式（`≈` 对按非严格、其余严格）：

```text
C1: DUE > WEAKNESS
C2: WEAKNESS > max(REPEAT, EXAM)
C3: min(REPEAT, EXAM) > max(WAITING, LAPSE)
现值全满足（5.0 > 3.0 > 2.0 > 1.5 > 1.0）。
```

**冻结网格 `M = {0.75, 0.875, 1.0, 1.125, 1.25}`**（单坐标、乘性；`1.0` = 空臂）。
选它而不选 R1 建议的 `{0.5,0.75,1.0,1.25,1.5}` 的理由是**算出来的**（同一次 `python -I`）：

| 坐标 | R1 粗网格下的可行臂 | 本文件 `M` 下的可行臂 |
|---|---|---|
| due | 3（0.5 破 C1：2.5 < 3.0） | **4**：0.75→3.75 / 0.875→4.375 / 1.125→5.625 / 1.25→6.25 |
| weak | 3（0.5 破 C2：1.5 < 2.0） | **4**：2.25 / 2.625 / 3.375 / 3.75 |
| exam | **1**（0.5 破 C3、0.75→1.5 破 C3、1.5→3.0 破 C2） | **3**：0.875→1.75 / 1.125→2.25 / 1.25→2.5 |
| kc-drop | 2（只向下） | **2**：0.75→1.875 / 0.875→2.1875 |
| diversity | 4 | **4**：family 0.225/0.2625/0.3375/0.375（source 同步 0.15/0.175/0.225/0.25） |
| **合计非空臂** | **13**（且 exam 单臂不可裁决） | **17** |

- `exam` 在粗网格下只剩 1 个非空臂 → 与 R1 §5.2"8 权重 → 32 个非空臂"的说法**不一致**（该数未做序锥过滤）。本文件按实算的 17 冻结，R1 的 32 标为笔误并在 §10 登记。
- `kc-drop` **只向下扫**：spec §6.6 未给它排位；向上越位（×1.25 → 3.125 > `WEAKNESS=3.0`，等于把 KC 掉档压力排到"弱点"之上）无规范依据，故整条向上方向不扫。若最优落在 0.75（下端点）→ 按 R7 只报方向。
- `diversity` 是**一个坐标**：`m` 同时乘 `FAMILY_PENALTY_WEIGHT` 与 `SOURCE_PENALTY_WEIGHT`，`MAX_DIVERSITY_PENALTY` **固定 1.5**。理由：上限是"多样性不得压过单题基础分"的护栏，跟着缩放会移动绑定点，那是另一个实验。
- `KC_DROP_MASTERY_THRESHOLD (0.6)` **不扫**：它是"哪些 KC 算掉档"的判据，不是权重。
- `avoidance` **不可测**（§2.4），不扫。

### 5.3 基线与其他固定项

- 基线 `W0` = `A1`（有考试、`f=0`、默认权重、`carelessRate=0.05`）——与 G6 共用同一次 run。
- 权重臂**不改预算**（`dailyBudgetSeconds=1200`）。等时检查：若某臂 `|T(a)−T(W0)|/T(W0) > 0.015`（选中的题时长画像不同），对该臂套用 §4.2 的 ρ 网格；仍不合格 → `NO_EQUAL_TIME`。
- 一次只动一个坐标（R4）。

### 5.4 裁决函数

```text
DECIDE_W():
  for a in 17 arms:
    for w in HOLDOUT: Δ[a,w] = Y_eq(a,w) − Y(W0,w)
    pass[a] = (median_w Δ[a,w] ≥ 0.0333) ∧ (#{w: Δ[a,w] > 0} ≥ 8) ∧ GUARD(a) ∧ a 不是该坐标的网格端点
  if {a: pass[a]} == ∅  → KEEP 全部现值
  best = argmax_a median_w Δ[a,w]      （并列：取 |m−1| 更小者；仍并列 → NO_ADOPT）
  CONFIRM on W-decay：同向 ≥ 8/10，否则 → CONDITIONAL（不采纳）
  ADOPT best —— 本批**至多一个坐标**
  其余 pass 的坐标只登记为"下一批候选"，不应用
```

- "至多一个坐标"是 R4（单坐标）的必然推论：两个坐标同时改就是联合变更，而联合效应**从未测量**。下一批要对联合配置另立预注册。
- 端点定义：due/weak/diversity 的 `{0.75, 1.25}`；exam 的 `{0.875, 1.25}`（0.875 是可行下端点，因为 0.75 不可行）；kc-drop 的 `{0.75}`。

---

## 6. 掌握阈值读数（**只测不改**）

### 6.1 三组阈值 = 三个 `MasteryDecisionPolicy` 实例（不改任何常量）

| id | 稳定度门槛（天） | 当前召回门槛 | 构造方式 |
|---|---|---|---|
| `T0`（现值） | 21.0 | 0.90 | `MasteryDecisionPolicy.DEFAULT`（`ClearlyMasteredForSkipPolicy.kt:43-51`） |
| `T1` | 14.0 | 0.90 | `MasteryDecisionPolicy(policyVersion="prereg-49", minimumStabilityDays=14.0, minimumCurrentRetention=0.90, decay=−0.1542, minimumItemFamilies=2, minimumStudyDays=2)` |
| `T2` | 21.0 | 0.85 | 同上，`minimumStabilityDays=21.0, minimumCurrentRetention=0.85` |

- 判定一律走 `ClearlyMasteredForSkipPolicy.meetsMemoryCriterion(..., policy = T)`（`:78-96`）——**这是唯一出口**，不得在报告脚本里重写公式。
- **不传** `prerequisiteStabilityDays`（与 harness 现有掌握读数同口径，`SimulatedLearner.kt:245-253` 也没传）。KF-16 先修压制在这三组读数里不生效——**在此声明**，避免有人把读数差异归因于压制。
- `decay` 固定 `−DEFAULT_PARAMETERS[20] = −0.1542`（产品侧口径），三组都用它，只动两个门槛。

### 6.2 三个读数（公式写死）

对 `d ∈ {30, 60, 90, 120}`、`truth ∈ {W-slip, W-decay}`、`T ∈ {T0,T1,T2}`：

```text
masteredSet(d, T, truth) = { KC : meetsMemoryCriterion(state_at_end_of_day_d, policy=T) }

① 覆盖数   coverage(d,T,truth) = |masteredSet|            （0..30，附占比 /30）
② 保持率   retention_30(T,truth) = |masteredSet(30) ∩ masteredSet(60)| / |masteredSet(30)|
           retention_60(T,truth) = |masteredSet(30) ∩ masteredSet(90)| / |masteredSet(30)|
           retention_90(T,truth) = |masteredSet(30) ∩ masteredSet(120)| / |masteredSet(30)|
           （|masteredSet(30)| = 0 时记 null，不记 0）
③ 误判率   misjudged(d,T,truth) = { KC ∈ masteredSet(d) : R_truth(end_of_day_d) < r_min(T) }
           misjudgmentRate = |misjudged| / |masteredSet(d)|      （分母 0 → null；
                                                                   r_min(T) 是该组自己的召回门槛：0.90 / 0.90 / 0.85）

R_truth(t) = FsrsScheduleMath.retention(t_days, state.memoryStabilityDays, −w20_truth)
             （`:71-78`，与产品同一公式，只换衰减）
```

- 误判的判据用**该组自己的** `r_min(T)`，不用固定 0.90：这样三组之间的"标签可靠性"才是同一件东西（"判掌握"这张标签承诺的召回，世界有没有兑现）。若固定 0.90，`T2`（0.85）会把 0.85–0.90 这一档**按定义**算成误判，与"标签说谎"混为一谈。

- **锚点选第 30 天**：第 30 天是冷启动段结束（R1 §3.0 口径），之后的 30/60/90 天正好是第 60/90/120 天，落在 horizon 内且不越界。
- **"30/60/90 天保持率"的口径只此一种**：锚点 = 第 30 天末，滞后 = 30/60/90 天。若按"horizon=30/60/90 的读数"理解，由上面的 `coverage(30/60/90)` 与 `misjudgmentRate(30/60/90)` 承接；两组数**不得混称**。
- **误判率在 `W-slip` 下结构性为 0**：真值参数 = 产品参数时 `R_truth ≡ R_product`，`meetsMemoryCriterion` 的召回门槛与它同式，所以"判掌握但真值 R<0.9"不可能发生。**这个 0 是构造，不是证据**；误判率只在 `W-decay` 臂有信息量。报告必须把 `W-slip` 的 0 标注为 `structural_zero`。
- 三个读数之间的关系必须原样报出：`T1`（14 天）预期覆盖更高、保持率更低；`T2`（0.85）预期覆盖最高、误判率最高。**报告只陈述观察，不做"哪个更好"的裁定**——阈值变更不在本批授权内。

### 6.3 前缀同一性（读数可加的前置断言）

第 30/60/90 天的状态来自 `horizonDays=30/60/90` 的三次独立 run。必须先断言：

```text
对同一 (seed, truth, f=0, 默认权重)：
  run(30).reviewLog  == run(120).reviewLog 的前缀（逐行相等，按 reviewedAtEpochMillis 升序）
  run(30).metrics.dailyWorkload == run(120).metrics.dailyWorkload.take(30)
```

理由：`horizonDays` 只应影响循环上界与读数时刻；若前缀不等，说明某处把 horizon 泄漏进了当日决策（例如 `validUntilEpochMillis = horizonEnd+1天`，`SimulatedLearner.kt:452`——按构造第 1..horizon 天的作答都在窗口内，故应无差异）。前缀不等 → 整个 §6 读数作废。

### 6.4 本批结论边界

- **不产生任何阈值变更提案**（ask 硬约束：21 天 / 0.9 不改）。
- 报告只能写"读数 + 若改阈值会怎样（model-based）"；任何变更必须由用户单独裁定 + 新预注册 + `algorithm-version-ledger.md` 记录 + `SKIP_POLICY` 版本纪律。

---

## 7. A 线：FSRS 拟合（次级；不 gate G6/权重）

> 来源：计划 §P1-8/§P1-10 + R1 §5.1。**ask 未把它列进冻结清单**，此处冻结是为了让 P1-10 的上线门有事先写好的判据（否则就是事后解释）。它不参与 §4/§5 的任何裁决。

### 7.1 协议

| 项 | 值 | 依据 |
|---|---|---|
| 拟合样本 | `fittableReviewSamples`（排除 MODEL_JUDGED/REVEAL）中 `reviewedAtEpochMillis < BASE + 94·DAY`（即第 1–93 天；第 94 天 ramp=0 也一并排除，保守） | `SchedulingEvaluation.kt:251-254`；窗口起点 = 108−14+1 = 95 |
| 拟合器 | `FsrsParameterOptimizer.optimize(preExamLog)`（确定性，固定初值/迭代） | `:489-540` |
| 门 1 | `predictableSampleCount(preExamLog) < 400` → `INSUFFICIENT_DATA` → **BLOCKED** | `:580-600`、`:701` |
| 评估 | `SchedulingEvaluationHarness.evaluate(fullLog, parameters = θ̂, trainFraction = 0.7)`（**声明用 0.7**；优化器内部是 0.8，两者不是同一个数） | `:330-390`、`:713` |
| 门 2 | `evaluationSampleCount < 200` → `fsrsBeatsBaseline=false` 只表示"样本不足"，标 **BLOCKED** | `:134-141` |
| 金丝雀 | 若 `logLoss(θ̂) < logLoss(θ_true) − 0.01` → 先查泄漏/切分，结论作废 | R1 §5.1 ③ |

### 7.2 判据

```text
A 线通过  ⇔ fsrsBeatsBaseline 在 holdout ≥ 8/10 world 为 true
A 线不通过 ⇔ 样本够（≥400）且 ≤3/10 world 为 true
其他（样本不足 / 二者之间） → UNDERPOWERED，只报 n 与两个 logLoss，不下结论
```

### 7.3 可辨识性（必须写进报告的限制）

本世界形状（30 KC / 300 题 / 120 天 / 1200s 日预算）的 `predictableSampleCount` 量级是**千级**，达不到 R1 §6 #8 的 ≥5,000/world。因此：

- 只报"采纳门状态"（不劣于基线），**不报"参数已个性化"**。
- 参数恢复/边界命中（R1 §5.1 的成功判据 ②/边界守卫）在本世界形状下**不做**，标 `BLOCKED: WORLD_TOO_SMALL_FOR_IDENTIFIABILITY`。
- 真库侧：若 `predictableSampleCount < 400` 或 `evaluationSampleCount < 200`，一律 `BLOCKED`，**不得**写成 FAIL，也不得填 0。

---

## 8. 机检清单（每条规则 → 一条可执行检查）

| # | 规则 | 检查（命令/断言，执行者实跑后把输出落 `docs/research/evidence/2026-11-08-batch-49/`） |
|---|---|---|
| 1 | 预注册未被篡改 | 报告 `meta.prereg_sha256` == `sha256sum docs/research/2026-11-08-review-calibration-prereg.md` |
| 2 | 代码基线可复现 | 报告 `meta.head` + `meta.file_sha256` == §1.1（逐文件重算） |
| 3 | 种子层不混用 | 现有用例 `holdout seeds are disjoint…`（`SimulatedLearnerTest.kt:84-95`）绿 |
| 4 | 同 seed 逐位一致 | 现有用例 `same seed reproduces every metric bit for bit`（`:25-46`）绿；另抽 1 个 (world,arm) 重跑，`digest()` 相同 |
| 5 | 默认臂 == 基线 | 权重线开跑前：`ReviewPlannerV2(scoring=DEFAULT)` 与默认构造在三组金样夹具上 `planFingerprint` 相同（seam 机检 S3） |
| 6 | golden 世界进 CI | 新用例：`SimulationConfig(seed=5001, horizonDays=60, questionsPerKnowledgeNode=4)` 的 `digest()` 等于钉住的 hex |
| 7 | 前缀同一性 | §6.3 两条断言（30/60/90 vs 120） |
| 8 | 日预算不超发 | 每个 (world, arm)：`overrunDays == 0` 且 5% 余量计数 == 0（§3.2 G1） |
| 9 | 等时合格率 | 报告列出每个 (f,w) 的 `ρ*` 与相对误差；`NO_EQUAL_TIME` 计数必须显式写出 |
| 10 | 无数据不填数 | 任一格子分母 0 → `null` + `reason`；禁止 `0.0`（R1 T11/R12） |
| 11 | holdout 只跑一次 | 报告含 `holdout_run_id`，每个 arm 一行，不得有重复执行的第二个 id |
| 12 | 掌握阈值未动 | `git diff --stat core/domain/.../AlgorithmConstants.kt` 不含 `Mastery` 段；报告脚本断言 `MIN_STABILITY_DAYS==21.0 && MIN_CURRENT_RETENTION==0.9` |
| 13 | 金样差异被记录 | 采纳权重 → 三组金样**必须**变并重生成（记录 diff）；采纳 G6 → 三组金样**应零差异**（见 §11 说明） |
| 14 | 裁决函数可复算 | 报告附一份"输入 = 每 world 的 Δ 表，输出 = 采纳/维持"的独立重算脚本或逐行表格，读者可手算复核 |

**新增测试的红→绿纪律**：第 6/7 条是新增用例，必须先写出失败版本（红：摘要常量不存在 / 前缀不等），再实现；**不得**为变绿放宽断言或把阈值改成实际值。两条各自指认的失败：
- 第 6 条（golden 摘要）消灭"模拟里混入未受控随机源、同 seed 悄悄漂移而无人察觉"；
- 第 7 条（前缀同一性）消灭"`horizonDays` 泄漏进当日决策（例如校准窗口按 horizon 截断），使 30/60/90 天读数不可加"。

### 8.1 运行矩阵（报告须逐项对账）

| 组 | 每个 world 的 run 数 | 说明 |
|---|---|---|
| 基线 `A1` | 1 | 同时是 G6 基线、权重基线 `W0`、§6 的 120 天居中真值臂、A 线拟合输入 |
| G6 `f>0` | 4 as-configured + 4×10 ρ 扫描 = 44 | ρ=1.000 复用 as-configured run |
| 权重 | 17 | `f=0`、默认真值、默认预算 |
| §6 阈值读数 | 3（居中真值 30/60/90）+ 4（`W-decay` 30/60/90/120）= 7 | 120 天居中臂复用基线 |
| 确认臂（`W-decay`） | ≤5 | 只对过门 finalist 跑 |
| **合计** | **69–74 / world；dev 10 + holdout 10 ⇒ 1380–1480 次 run** | |

- 耗时估计：`SimulatedLearnerTest` 的 6 条用例（含约 9 次完整 run）XML `time="4.086"` ⇒ **约 0.45 s/run**（推算，不是本矩阵实测）⇒ 纯模拟 10–11 分钟；加 Gradle/JVM 开销按 ask 纪律**串行**跑。
- 报告必须写出实际 run 计数并与 `69–74 × 20` 对账；少跑或多跑都要说明（少跑 = 未完成，多跑 = 事故/重跑，需登记 holdout 是否被二次执行）。
- **本实验全程在 JVM**（`core:domain` test 源集）。不涉设备、不跑 `connectedLocalFirstDebugAndroidTest`；设备侧（soak / 确认卡 / 校准页）属计划 §P3，不在本预注册范围。

---

## 9. 失败处置、污染与一次性

| 情形 | 处置 |
|---|---|
| 臂未过门 | 维持现值（G6 → 0.5；权重 → 现值），报告写"该世界形状下无可测差异" |
| 过门但在网格端点 | 只报方向，不采纳；扩网格需新预注册（R7） |
| 仅 `W-slip` 过、`W-decay` 不过 | `CONDITIONAL`，不采纳（R1 §3.2 同号要求） |
| `NO_EQUAL_TIME` 导致某臂样本 < 8/10 | 该臂标 `UNDERPOWERED`，不参与裁决，不得插值补齐 |
| holdout 已跑后要改网格/指标/代码 | 旧 holdout 永久退役并标"已污染"，换新 seed 基 + 新预注册（R2） |
| `G1` 破（超发） | 先判 harness/planner 缺陷，不解读指标 |
| 前缀同一性失败 | §6 全部读数作废 |
| 样本不足（A 线 / 真库） | `BLOCKED` + 写 n，不写 0、不写 FAIL |

---

## 10. 未测 / BLOCKED / 与 R1 的差异（诚实登记）

| # | 项 | 状态 | 原因 | 处置 |
|---|---|---|---|---|
| 1 | 权重 17 臂 | **BLOCKED（无注入 seam）** | `ReviewPlannerV2` 权重是 `private const val`（`:1059-1096`），构造无权重参数（`:30-33`） | 先落 §5.1 seam 并过 S1–S5；未落则整线 BLOCKED |
| 2 | `A0`（无考试记录真空臂） | **未测** | 无考试时 `examSubjectCoverage` 为空，`Y` 无定义；harness 缺"无考试窗口覆盖"指标 | R1 T6 的问题保持开放；若要做，需 harness 新指标 + 新预注册 |
| 3 | 考试日召回探针（R1 §4.6 的 B/C 线主指标） | **未实现** | harness 明确不建模探针（`SimulatedLearner.kt:60-66`） | 本文件把主指标改为 **考试科覆盖 `Y`**（ask 冻结的目标）；这是**与 R1 的实质性偏离**，报告必须写"主指标是覆盖，不是考试日召回" |
| 4 | `W-transfer` / 科目级 slip | **未实现** | harness 只有标量 `carelessRate` 与 `truthParameters` | 用 `W-decay`（w20×1.3）作第二错配臂替代；**不是同一件事**，结论强度低于 R1 设想 |
| 5 | 容量预览路径的 G6 净效应 | **不在裁决范围** | 模拟只走计划队列路径（`:380-395`）；预览路径的 `growthCeilingBudgetSeconds` 替换（`StudyReviewPlannerService.kt:392-393`、`ReviewCapacityPolicy.kt:66`）未建模 | 报告单列机制差异（R1 T7），不得用模拟数替代 |
| 6 | R1 §5.2 的"8 权重 → 32 非空臂" | **计算有误** | 未做序锥过滤；粗网格下 due/weak/exam 只有 3/3/1 个可行臂，合计 13（本会话 `python -I` 实算） | 本文件按 17 臂冻结；R1 该句标笔误 |
| 7 | `noiseSeed` 二级种子 | **不做** | harness 单 seed 同时决定结构与噪声 | 配对性由 `(seed, day, unit)` 的作答抽样保证（`:402-407`），已足够 |
| 8 | A 线参数可辨识性 | **BLOCKED（世界太小）** | `predictableSampleCount` 千级 < R1 §6 #8 的 5,000 | 只报采纳门，不报"参数已个性化" |
| 9 | 真库 per-reason 权重 / G6 因果效应 | **BLOCKED（结构性）** | 无反事实、无倾向得分（R1 §2.2） | 真库只做不变量检查（窗口外逐位不变）+ 现状分布诊断 |
| 10 | `avoidance` 权重 | **不可测** | 模拟候选 `avoidance` 恒 false | 该权重本轮不扫 |
| 11 | spec §2.17 的 `r*_exam` 准入 | **未实现** | 代码用固定 `EARLY_REVIEW_MAX_RETRIEVABILITY=0.8`（`AlgorithmConstants.kt:111`） | 本轮不测；G6 只改预算 |

---

## 11. 变更纪律（若采纳）

**一次性 bundle（缺一步视为未发生，回滚）**：

1. 改常量：
   - G6 → `AlgorithmConstants.ReviewScoring.EXAM_CAPACITY_RAMP_MAX_EXTRA_FRACTION = f*`；
   - 权重 → 对应 `ReviewScoring` 字段（或 seam 的默认值，二者必须单源）。
2. `ReviewPlannerV2.VERSION` bump（v4 → v5），必要时指纹 schema 同步。
3. 三组金样重生成 + `ReviewPlannerScaleBenchmarkTest` 绿；**记录差异**：
   - 采纳**权重** → 金样**必变**（评分 hex 与选序都会变），全量重生成；
   - 采纳 **G6** → 金样**应零差异**。证据：`EXAM_CAPACITY_RAMP_MAX_EXTRA_FRACTION` 的唯一消费点在 `core:data`（`StudyReviewPlannerService.kt:185`），而 `ReviewPlannerV2` 的预算来自 `request.timeBudgetSeconds`（`:71`、`:80`、`:117`），金样夹具显式传 `budgetSeconds = 1_800`（`ReviewPlannerScaleBenchmarkTest.kt:104`）。**零差异不是"没测到"，是金样不覆盖 G6 的预算面**；G6 的真实护栏是 `StudyReviewPlannerExamWindowTest` 与容量预览用例。报告必须把这句话写出来，否则"重生成金样"会变成一句没有内容的仪式。
4. 定向 + 集成回归（含 `StudyReviewPlannerExamWindowTest`、`StudyReviewPlannerServiceCapacityPreviewTest`）。
5. `docs/research/algorithm-version-ledger.md` 记一行：旧值 → 新值、预注册 sha256、holdout 结果摘要、`model-based` 标记。
6. 回滚 = 反向执行 1–3 + 台账追加"已回滚"。

**红线**：

- 不允许"先改常量、后补金样"；不允许"改一半"。
- 不允许把 holdout 结果用于挑网格（R2/R9）。
- 不允许把模拟结论写成产品结论：所有对外句子必须带 `model-based`，真库不足处带 `BLOCKED`。

---

## 12. 报告必须写的字段（P1-8 契约）

沿用 R1 §8 的规范化 JSON，另加本文件新增项：

```jsonc
{
  "meta": {
    "batch": "49",
    "prereg_sha256": "<hex>",            // §8 机检 1
    "head": "ba7b5624…",
    "file_sha256": { "ReviewPlannerV2.kt": "…", "SimulatedLearner.kt": "…" },  // §1.1 全表
    "model_based": true,
    "holdout_run_id": "<uuid 或时间戳>",
    "primary_metric": "exam_subject_coverage_in_window",
    "g6_grid": [0.15, 0.25, 0.35, 0.5],
    "weight_grid": [0.75, 0.875, 1.0, 1.125, 1.25],
    "thresholds_evaluated_not_changed": [[21.0,0.9],[14.0,0.9],[21.0,0.85]]
  },
  "world": { "seed": 901, "truth_arm": "W-decay", "mis_spec": ["w20*1.3"], "…": "…" },
  "arm":   { "id": "G6/A3/eq", "f": 0.25, "rho": 0.925, "equal_time_rel_error": "0x1.3p-7" },
  "metrics": { "Y": "0x1.8p-1", "Q": 214, "brier": "…", "log_loss": "…", "ece": "…",
               "mastered": 17, "kc_gap_p95": 12, "recent_share": {"subject-0": "0x1.9p-1"} },
  "guardrails": { "strict_overruns": 0, "tolerance_overruns": 0, "starved_subjects": [],
                  "brier_delta_median": "0x1.0p-9" },
  "decision": { "pass": false, "reason": "median_delta_below_one_kc" },
  "digest": "sha256:<hex>"
}
```

格式纪律（R1 §8 已定，重申两条最易犯的）：

- 浮点用 `Double.toHexString`；摘要只吃 hex，不吃十进制格式化串。
- 分母为 0 写 `{"n": 0, "value": null, "reason": "…"}`，**禁止 `0.0`**。

---

## 13. 本文件的边界（一句话）

本文件只冻结"怎么做实验、看什么、怎么判"。它**不**声称任何一条臂更好，**不**动任何常量，**不**替代用户对阈值/产品行为的裁定；所有 G6 与权重的结论在报告里都必须带 `model-based`，真库不足处必须带 `BLOCKED`。

**明确不涉及**（属计划 §P2 产品闭环，不属标定预注册，执行时仍按计划硬约束保持）：KD-67 确认卡、校准可见页、投影/知识点冲突处置；其中"冲突 KC 默认仍可选（学生可选'先校准'）"是产品语义约束，本文件既不测它也不改它。
