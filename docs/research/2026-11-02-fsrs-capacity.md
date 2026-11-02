# 批次 40 · R3：FSRS 容量模型、考试截止日配额与欠账再平衡

> 研究线 R3，驱动 WS3 六项（计划 `plan-2026-11-02-batch-40-5x.md` §1 item 17–22）。
> 写于 2026-11-02。本轮**只读**代码与外部资料，未改任何源码，**未运行 Gradle**（门禁由脚本串行执行）。
> 访问通道：`gh api`（GitHub 内容/代码搜索）、WebFetch（Anki 官方手册）。所有外部结论标来源；本轮实算的数字标"本会话计算"并附命令（§10）。

---

## 0. 结论（先看这里）

| # | 结论 | 类型 | 证据 |
|---|---|---|---|
| C1 | FSRS 三参数（S/D/R）与目标可提取度的**最小实现已在仓内**：`FsrsScheduleMath`（纯公式，21 参数，无训练）、`MemoryUpdateModel`、`ForgettingCurve`、`OptimalRetention`。WS3 item 18 若再写一套"简化容量模型"，就是**第二套调度数学**。 | 事实 | `core/domain/.../FsrsScheduleMath.kt:14-188`、`MemoryUpdateModel.kt:44-140`、`ForgettingCurve.kt:64-96`、`OptimalRetention.kt:1-150` |
| C2 | 计划 §0 的第二半前提**不成立**：`exam date → daily quota` 与 `missed days rebalance` **不是** FSRS / open-spaced-repetition 的算法能力。py-fsrs 调度器、fsrs-rs 模拟器均无 exam/deadline/quota/backlog 标识；fsrs4anki-helper 仓库在 GitHub 代码搜索（默认分支）里 `deadline` = 0 命中。它们是**应用层规划**（Deadline2/Deckline）与 **Helper 的手动工具**（Advance/Postpone/Schedule a Break/Flatten）。 | 事实（更正） | §1.2、§3.1；命令见 §10-②③④ |
| C3 | 现有考试容量只有**预算线性 +50%**（`examCapacityBudgetSeconds`）；spec §2.17 的 `R < r*_exam` 队列准入**未实现**——代码用固定 `EARLY_REVIEW_MAX_RETRIEVABILITY = 0.8`。注意：spec 的实现注记把 `r*_exam` **排除出投影**（`:313`，理由：投影必须事件确定性可重放），所以缺的是**规划/准入层**的 `r*_exam`，不是投影层。 | 事实（缺口，含口径） | `StudyReviewPlannerService.kt:153-179`、`AlgorithmConstants.kt:104-112`、`ReviewPlannerV2.kt:636-655,1073-1078`、`docs/specs/mastery-scheduling-spec.md:138-139,313` |
| C4 | 每日配额已有一个 count 版：`NewIntroductionPolicy` 的 `ceil(remaining/daysLeft)`，与 Deadline2 的 `cards_per_day` **逐式同形**（含"今天算一天"）。它只管**新引入积压**，不管到期复习，也不管保留率缺口。 | 事实 | `NewIntroductionPolicy.kt:126-140`、Deadline2 `__init__.py:114-122` |
| C5 | 欠账再平衡**不存在**：全仓 `.kt` 中 `rebalance` 0 命中；F9 现存只有 KF-17 错峰（同批同源新建卡 +1/+2 天），不是休假/积压追赶。 | 事实 | §10-①；`LearningProjector.kt:1042,1122` |
| C6 | 量化主表（本会话实算，长程模拟）：相对 r\*=0.90，长期每日复习量在 r\*=0.95 约 **1.43–1.72×**、r\*=0.97 约 **2.11–2.43×**。**朴素"间隔比"（2.48×/4.49×）显著高估**，因为降间隔会换来更大的稳定性增益。 | 计算 | §2.5；§10-⑤ |
| C7 | F8 的真缺口不是"没有预算"，而是**预算没有持久化**：DataStore 只有 `desired_retention`/`exam_calendar`/`optimized_fsrs_parameters` 三个键；预算只是 `RoomBackedStudyExperienceRepository` 的构造默认 1200s。 | 事实 | `DataStoreSchedulingSettingsStore.kt:108-115`、`RoomBackedStudyExperienceRepository.kt:98,1113` |
| C8 | 本批可安全落地的 WS3 面：**纯策略函数 + JVM 用例 + 只读容量读数**（不改变默认计划输出）。真正"激活"配额/再平衡要改 `ReviewPlannerV2.VERSION`（core/domain 热区）并加设置 UI（feature 热区）——**本批阻塞，登记不伪造**。 | 裁定建议 | §5.3–§5.5 |

---

## 1. 证据口径

### 1.1 一手来源（本轮实读）

| 来源 | 版本/状态（读取时） | 许可 | 读到的关键内容 | 访问方式 |
|---|---|---|---|---|
| Anki 官方手册 `deck-options.html` | 2026-11-02 抓取 | Anki 项目文档（公开站点；许可本轮未核） | "Desired retention … The default is 90%, which offers a good balance of retention and workload."；"Higher retention leads to shorter intervals and more reviews per day."；"Above 90% the workload increases very quickly, and above 97% the workload can be overwhelming."；新卡/复习每日上限是**张数**上限 | WebFetch |
| `open-spaced-repetition/py-fsrs` | v6.3.2（2026-08-09 发布，pushed 2026-08-09） | MIT | `FSRS_DEFAULT_DECAY = 0.1542`；`desired_retention` 默认 `0.9`、`maximum_interval` 默认 `36500`；`_next_interval` 用 `round()` + 下限 1 + 上限 max；`R = (1+FACTOR·t/S)^DECAY` | `gh api` 内容 API |
| `open-spaced-repetition/fsrs-rs` | v6.6.2（2026-08-28 发布，pushed 2026-10-28）；源码另含 `model_v7.rs`/`analytic_v7.rs` 等 v7 相关文件（发布状态本轮未核） | BSD-3-Clause | `model_v6.rs:31-42` 与 py-fsrs 同式；`SimulatorConfig` 字段为 `deck_size/learn_span/max_cost_perday/max_ivl/learn_limit/review_limit/...`，**无 exam/deadline/quota** | `gh api` 内容 API |
| `open-spaced-repetition/fsrs4anki` 教程 | main，pushed 2026-08-14 | MIT | r\* 可设区间 **0.70–0.97**（Anki 23.10.1+ 放宽到 0.99）；默认 90%；"Compute minimum recommended retention" 用模拟最小化 工作量/记住量；**备考做法**：把 "Days to simulate" 设为到考试天数；A9 明确说"考试想早点看，用 Helper 的 Advance，且不污染评分历史" | `gh api` 内容 API |
| `open-spaced-repetition/fsrs4anki-helper` | main，pushed 2026-09-17 | MIT | Postpone / Advance / Schedule a Break / Load Balance / Easy Days / Flatten 的**语义与代码**；Advance 的 0.13 与 Postpone 的 0.15 相对偏离阈值；Schedule a Break 的 `cost = abs(day − original_due) / original_interval` | `gh api` 内容 API + 代码搜索 |
| `BSCrumpton/Deadline2` | master，pushed 2024-02-26，7 stars | GPL-3.0 | `cards_per_day = ceil((new_cards + new_today) / days_left)`（`:114-122`），`include_today=True`（`:96-102`）；每次重算并写回每日新卡上限 | `gh api` 内容 API |
| `Kwinties/Deckline` | main，pushed 2026-11-01，7 stars，仓库**无 LICENSE 文件** | 未声明（分发免费/付费分层） | 目标算法五步（分 NEW/REVIEW 两阶段、排除休息日、剩余工作量 ÷ 剩余学习日、对比当日完成量）；FAQ 原文："**Does Deckline modify FSRS or scheduling? No, it only provides planning and feedback on top of your existing scheduling.**" | `gh api` 内容 API |

### 1.2 交叉验证

| 事实 | 来源 A | 来源 B | 判定 |
|---|---|---|---|
| R(t,S) 幂律 + 间隔反函数 `I = S/FACTOR·(r*^(1/decay) − 1)`，`FACTOR = 0.9^(1/decay) − 1` | py-fsrs `scheduler.py:187-188,679-693` | fsrs-rs `model_v6.rs:31-42`（独立 Rust 实现） | **一致**，采纳 |
| 默认目标保留率 0.90 | Anki 手册 | py-fsrs `desired_retention: float = 0.9`；fsrs4anki 教程 "90% should work fine" | **一致**，采纳 |
| 支持区间 0.70–0.97 | fsrs4anki 教程（0.99 为放宽后的额外允许） | 本仓 `MemoryUpdateModel.kt:68`、`StudyExperienceRepository.kt:737` 两处 `require(0.7..0.97)` | **一致**（本仓取窄口径），采纳 |
| 提高 r\* 会显著提高每日复习量，90% 以上加速 | Anki 手册（定性） | 本会话长程模拟（定量，§2.5） | 方向一致；**幅度**只认模拟值，不认朴素间隔比 |
| exam-date daily quota 属 FSRS 能力 | 计划 §0 的表述 | py-fsrs/fsrs-rs 源码无该标识；Helper 仓库 `deadline` 代码搜索 0 命中；Deckline FAQ 自述"不改调度" | **冲突 → 以源码为准，判定计划表述不成立**（C2） |
| missed-days rebalance 属 FSRS 能力 | 计划 §0 的表述 | 同上；Helper 把"休假后积压"做成**手动** Postpone / Schedule a Break | **冲突 → 判定为应用层工具** |

### 1.3 复用判定（GitHub 优先纪律）

| 候选 | 可复用性 | 许可/维护 | 结论 |
|---|---|---|---|
| py-fsrs | 公式与常量 | MIT；2026-08 仍在更新 | 仓内已按源码逐式移植（`FsrsScheduleMath` 头注释 "verbatim port … source-verified 2026-08-29"），**不再引依赖** |
| fsrs-rs | 长程模拟/CMRR 目标 | BSD-3-Clause；2026-10 更新 | 仓内 `OptimalRetention` 已按 `simulation.rs` 的 CMRR 目标简化实现；本批**复用该模拟器**，不新写 |
| fsrs4anki-helper | Advance/Postpone/Break 的**选择与代价函数** | MIT；2026-09 更新 | 可移植算法与代码（需保留版权声明）。本批只借语义与阈值，代码不复制 |
| Deadline2 | `ceil(remaining/days)` | **GPL-3.0**；2024 年后未更新 | **只借算法（数学不受版权保护），不复制代码**；维护度低，不作依赖 |
| Deckline | 两阶段 + 休息日的产品语义 | **无 LICENSE 文件**（实读仓库根目录列表） | 只作语义参照，**不复制任何内容** |

> 仓库根目录**无 LICENSE/COPYING 文件**（`ls` 实读）。因此凡涉及"复制代码"的路径都要先解决许可问题；本文件的建议一律落在"自写纯函数 + 引用公式"。

---

## 2. Q1：FSRS 三参数与目标可提取度的最小实现形态（不引 ML）

### 2.1 公式（三方对照，逐式一致）

```
FACTOR(decay) = 0.9^(1/decay) − 1                     // decay = −w20（默认 −0.1542）
R(t, S)       = (1 + FACTOR·t/S)^decay                // 当前可提取度
I(r*, S)      = round( (S/FACTOR)·(r*^(1/decay) − 1) )  // 整日化，夹在 1..36500
S'            = S·(1 + e^w8·(11−D)·S^(−w9)·(e^((1−R)·w10) − 1)·HP·EB)   // 跨日成功
S'_lapse      = min( w11·D^(−w12)·((S+1)^w13 − 1)·e^((1−R)·w14), S/e^(w17·w18) )
D'            = w7·D0(EASY) + (1−w7)·(D + (10−D)·(−w6·(G−3))/9)         // 夹在 1..10
```

- py-fsrs：`fsrs/scheduler.py:187-188`（FACTOR/DECAY）、`:679-693`（`_next_interval`）、`:234`（R）。
- fsrs-rs：`src/model_v6.rs:31-42`（同式，独立实现）。
- 本仓：`core/domain/.../FsrsScheduleMath.kt:71-94`（R/间隔）、`:113-187`（S/D 更新）、`:24-46`（21 个默认参数，含 `w20 = 0.1542`）。

### 2.2 仓内已经具备什么（不要重造）

| 能力 | 载体 | 说明 |
|---|---|---|
| 三参数与更新 | `FsrsScheduleMath` + `FsrsMemoryUpdateModel` | FSRS-6 全量；`MemoryUpdateModel.kt:68` 已钉 0.7–0.97 |
| R 的读取 | `ForgettingCurve.estimateAt/retentionAt` | 返回 `RetentionEstimate(probability, clockAnomaly)`；`decay` 可注入个性化参数 |
| "按目标 r\* 排到哪天" | `ForgettingCurve.reviewAtTargetRetention` | 间隔反函数的现成封装 |
| 长程负载/最优保留率 | `OptimalRetention.recommend` | CMRR 目标，365 天模拟，<20 张卡返回 null（拒绝噪声） |
| 每日准入与预算 | `NewIntroductionPolicy.decide` + `ReviewPlannerV2` | 时间份额、考试追赶、超尺寸防死锁、诚实预览 |
| 到期风险 | `ReviewPlannerV2` 的 `dueRisk = 1 − R + min(1, overdueDays/30)` | `ReviewPlannerV2.kt:499-520` |

**结论**：item 18 的"FSRS 三参数简化落 core/model"应当**改写为"只读容量读数"**，而不是第二套更新/间隔数学。

### 2.3 最小实现形态（建议的 API 形状）

落点建议：`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/ReviewCapacityPolicy.kt`（**新文件**）。
理由：`core/data` 依赖 `core:domain` 与 `core:model`（`core/data/build.gradle.kts:169-171`），能拿到 `ForgettingCurve`；而 `core/model` 在依赖方向上**早于** `core:domain`（`AlgorithmConstants.kt:32-35` 注释明说），放 `core/model` 会迫使它重写曲线——正是要避免的第二套数学。

```kotlin
// 纯函数、无时钟、无 IO、确定性。R 由调用方（core/data）用 ForgettingCurve 现算后传入。
data class CapacityFacts(
    val practiceUnitId: String,
    val retrievabilityNow: Double,        // R(t_now)，调用方用 ForgettingCurve 现算
    val retrievabilityAtExam: Double?,    // R(examDay)；无考试日 = null
    val nextReviewAtEpochMillis: Long,
    val stabilityDays: Double,
    val lapseCount: Int,
)

object ReviewCapacityPolicy {
    /** 每日应做量：ceil(remaining / daysLeft)，夹在 [0, cap]。 */
    fun dailyQuota(remaining: Int, studyDaysLeft: Int, capPerDay: Int): Int

    /** spec §2.17 的考前目标保留率：r*_exam(d) = min(0.97, r* + (0.97−r*)·(1 − d/14))。 */
    fun examTargetRetention(baseRetention: Double, daysUntilExam: Int): Double

    /** 考试缺口：max(0, r*_exam − R(examDay))，0..1。 */
    fun examDeficit(retrievabilityAtExam: Double, targetRetentionAtExam: Double): Double

    /** F18：按日权重把 remaining 分配到未来若干天（权重 0 = 休息日）。总量守恒。 */
    fun allocateByDayWeights(remaining: Int, dayWeights: List<Int>): List<Int>

    /** F9：给定积压（以"正常日容量"为单位）与上限 g，算出清账所需天数。 */
    fun daysToClear(backlogDays: Double, maxDailyGrowth: Double): Int
}
```

不变量（必须有用例钉住）：
1. `dailyQuota` 单调：`remaining` ↑ 不减；`studyDaysLeft` ↑ 不增；输出恒 `∈ [0, cap]`；`studyDaysLeft ≤ 0` 时返回 `cap`（当日尽力，不除零）。
2. `allocateByDayWeights` 输出和恒等于 `remaining`（除非权重全 0 → 抛 `IllegalArgumentException`，fail-closed 而不是静默丢题）。
3. `examTargetRetention` 恒 `∈ [baseRetention, 0.97]`，且 `daysUntilExam ≥ 14` 时恒等于 `baseRetention`（窗口外逐位不变）。
4. `examDeficit` 恒 `∈ [0, 1]`；`R ≥ r*_exam` 时为 0。

### 2.4 明确不做（"不引 ML"的边界）

| 不做 | 理由 |
|---|---|
| 运行时参数拟合 / 训练 | 优化器是离线可选面（`FsrsParameterOptimizer`），本批不接；默认 21 参数已 source-verified |
| fuzz / 随机抖动 | 仓内明确"Fuzzing is deliberately not ported: projections must be deterministic"（`FsrsScheduleMath.kt:11-12`） |
| learning steps / 短时记忆步 | `MemoryUpdateModel` 已禁用学习步，同日走短程分支（`:96`、`:115-117`） |
| 每评级耗时表（fsrs-rs `state_rating_costs`） | 仓内用 `LogDurationModel` 的本地估时（90/180/300s 三档），不引第二套 |
| 新写遗忘曲线 / 新写间隔反函数 | 见 C1；任何"简化版"都会与投影侧分叉 |

### 2.5 量化：r\* 与长期复习负载（本会话计算）

方法：按 `OptimalRetention.simulate` 的同一分支逻辑用 Python 复算（确定性期望分支、单卡、Good-only、每次遗忘 +1 次重学、horizon=3650 天、`round` 半偶、夹取 1..36500）。**这是 Python 复实现，不是运行 Kotlin 代码**（Gradle 本批禁止）。

| r\* | 朴素间隔比 `I(0.9)/I(r*)`（一阶，**会高估**） | 长程复习量比（S0=2.31） | 长程复习量比（S0=50） | 一个"20 分钟@0.9"的牌组在 0.9→r\* 后需要 |
|---|---|---|---|---|
| 0.80 | 0.302 | 0.683 | 0.655 | 13.7 min |
| 0.85 | 0.525 | 0.784 | 0.836 | 15.7 min |
| **0.90** | 1.000 | 1.000 | 1.000 | **20.0 min** |
| 0.92 | 1.367 | 1.107 | 1.178 | 22.1 min |
| 0.95 | 2.484 | **1.434** | **1.718** | 28.7 min |
| 0.97 | 4.489 | **2.112** | **2.434** | 42.2 min |

读法（写入裁定时必须带的三句）：
1. **不要用朴素间隔比做预算**：它把 r\* 的代价夸大约 1.7–2.1 倍（0.95: 2.48 vs 1.43；0.97: 4.49 vs 2.11），因为高 r\* 下每次复习的稳定性增益更大（R 越低增益越大，`FsrsScheduleMath.kt:137-159`）。
2. S0 越大，"高 r\* 的额外负载"越重（0.95: 1.43→1.72；0.97: 2.11→2.43）——成熟牌组对 r\* 更敏感。
3. 绝对负载依赖牌组规模与稳定性分布；上表只给**倍率**，不可当"每人每天几张"。

---

## 3. Q2：考试截止日每日配额算法

### 3.1 参照实现的三种形态（都**不是** FSRS 本身）

| 形态 | 代表 | 算法 | 优点 | 已知缺陷 |
|---|---|---|---|---|
| A. 纯 count 配额 | Deadline2 | `perDay = ceil((newCards + seenToday) / daysLeft)`，`include_today=True`；每日重算写回 | 极简；分子用"剩余"，天然吸收漏做 | 不看时间预算；不看到期复习；不看保留率缺口；GPL-3.0 |
| B. 两阶段 + 休息日 | Deckline | ① 先 NEW 到 cutoff，再 REVIEW 到 deadline；② 排除休息/休假；③ 剩余工作量 ÷ 剩余**学习日**；④ 对比当日完成量 | 分阶段、尊重休息日、可反推 pace | 闭源；仍以"张数"计，不按时间/保留率 |
| C. 手动再分配工具 | fsrs4anki-helper | Advance/Postpone（按"相对偏离"排序取最小损伤）；Schedule a Break（`cost = abs(day − due) / interval`，容量按天均分）；Flatten（超每日上限的往后推） | 损伤可度量；不污染评分历史 | **手动触发**；不是自动每日配额 |

### 3.2 建议算法（两层，别混分子）

**层 A：积压覆盖（已有，保留；但触发条件是"考试"不是"缺勤"）**
`quotaA = ceil(remainingIntakeItems / daysLeftToExam)`，再换算成时间片并夹在"全天预算"内。
证据：`NewIntroductionPolicy.kt:126-140`；与 Deadline2 同式（§3.1-A）。这是 F9 与 G6 的新题半边。

本轮读码发现的两个口径问题（接线时必须处理，不要照抄）：
1. **触发源是考试日，不是学习日**：`daysLeftToExam` 来自 `daysUntilNearestExamOf`（`StudyReviewPlannerService.kt:132-137`），`decide` 只在 `> 0` 时启用（`NewIntroductionPolicy.kt:126`）。因此**休假回来的第一天，如果没有考试，配额仍然是固定 1/3 时间片**——积压不会被摊到剩余天数。这正是 F9 需要独立触发器的原因（见 §4）。
2. **考试科目未过滤**：`daysUntilNearestExamOf` 对所有考试取最小天数（无 subject 过滤），而新引入候选是**全科**的；一场 3 天后的数学考试会把英语积压也按 `ceil(N/3)` 拉高。接线层 B 时应改为"按科目各自的最近考试日"，否则跨科配额串味。

**层 B：考试保留率缺口（新增，spec §2.17 的落地）**
```
r*_exam(d) = min(0.97, r* + (0.97 − r*)·(1 − d/14))      d = 到考试天数，d ∈ [0,14]
deficit_i  = max(0, r*_exam(d) − R_i(examDay))
N_deficit  = count(deficit_i > 0)
quotaB     = ceil(N_deficit_remaining / studyDaysLeft)
```
- 只在考试窗口内算（`d ∈ [1,14]`；`d=0` 是考试当天，不排配额），窗口外 `r*_exam = r*`、`deficit` 恒 0 → **零输出**，默认计划逐位不变。
- 优先级：`deficit` 降序；同分按 `R_now` 升序（更可能已忘的先做）；再按 `nextReviewAt` 升序。
- **just-in-time**：一次复习就能覆盖考试的条件是 `examDay − t ≤ I(r*_exam, S_after)`（复习后到考试日的间隔不超过目标间隔）。所以层 B 只取**已进入最后窗口**的题（`examDay − now ≤ I`），更早的题留在池里等窗口——避免窗口第一天把整章刷一遍、到考试又衰减，也避免每道题被迫做两次。若容量不足需要提前，则如实计入"多做一次"的代价，不假装一次就够。
- **与 spec 收窄的关系**：spec 的实现注记（`docs/specs/mastery-scheduling-spec.md:313`）把 `r*_exam` **有意排除出投影**（投影必须事件确定性可重放，考试日历是可变外部状态）。本层不碰投影：`R(examDay)` 由 `ForgettingCurve` 在**读侧**现算，只影响当日准入，不写回记忆状态——因此与那条收窄不冲突。

**合并与封顶**
```
dailyDemandSeconds = dueReviewSeconds + quotaA_seconds + quotaB_seconds
if dailyDemandSeconds > budgetSeconds:
    按优先级截断（到期复习 > 考试缺口 > 新引入），并把未满足量作为"缺口事实"上报
```
这条"到期复习优先"与 Anki 的既有语义一致（review limit 会先拦新卡），也是本仓 `NewIntroductionPolicy` 的 1/3 份额想表达的同一件事（`NewIntroductionPolicy.kt:8-16`）。

### 3.3 伪代码（建议实现，落 `core/data` 非热区）

```kotlin
fun planExamQuota(
    cards: List<CapacityFacts>,        // 只含今日本科候选
    examEpochDay: Long,
    todayEpochDay: Long,
    baseRetention: Double,
    budgetSeconds: Int,
    p50DurationSeconds: Int,
    studyDayWeights: List<Int>?,        // F18；null = 每天等权
): QuotaResult {
    val d = (examEpochDay - todayEpochDay).toInt()
    if (d !in 1..14) return QuotaResult.none()          // 窗口外/考试当天：零输出
    val rExam = examTargetRetention(baseRetention, d)
    val deficits = cards.mapNotNull { c ->
        val rAtExam = c.retrievabilityAtExam ?: return@mapNotNull null   // 调用方已用 ForgettingCurve 在 examDay 现算
        val deficit = examDeficit(rAtExam, rExam)
        if (deficit > 0.0) Demand(c.practiceUnitId, deficit, rAtExam) else null
    }.sortedWith(compareByDescending<Demand> { it.deficit }.thenBy { it.retrievabilityAtExam })

    val capacity = budgetSeconds / p50DurationSeconds            // 张/日
    val quota = dailyQuota(deficits.size, studyDaysLeft(todayEpochDay, examEpochDay), capacity)
    val picked = deficits.take(quota)
    val unmet = deficits.size - picked.size
    return QuotaResult(picked, unmet, rExam, capacity)           // unmet 进诚实预览，不静默丢
}
```

### 3.4 数值示例（本会话算术）

设全天预算 1200s、中档估时 180s → 容量 6 张/日。

| 场景 | 缺口 N | 剩余天 | `ceil(N/天)` | 对容量 | 判定 |
|---|---|---|---|---|---|
| 考前 21 天，60 道缺口题 | 60 | 21 | 3 | 3 ≤ 6 | **可兑现** |
| 考前 21 天，300 道缺口题 | 300 | 21 | 15 | 15 > 6 | **不可兑现**（2.5× 超容） |
| 同上 + 考试爬坡 +50% | 300 | 21 | 15 | 9 | 仍不可兑现（1.67× 超容） |
| 考前 7 天，60 道 | 60 | 7 | 9 | 9 > 6 | 不可兑现；+50% 后 9 = 9，**恰好压线**（无余量） |

结论：**配额不是"算出个数就完事"，必须同时报"容量是否够"**。`+50%` 爬坡只在"缺口/天 ≤ 1.5×容量"时有用；再大的缺口只能缩范围（科目/知识点）或加场次。

### 3.5 失败模式（fail-closed）

| 输入 | 行为 | 理由 |
|---|---|---|
| `studyDaysLeft = 0`（考试当天/已过） | 不排考试配额；当日计划按常规预算 | 避免除零；考试当天再加压没有意义 |
| `budgetSeconds ≤ 0` / `p50Duration ≤ 0` | 抛 `IllegalArgumentException` | 构造期就该挡住（对齐 `NewIntroductionPolicy.kt:112`） |
| 缺口集合里有 `R` 非有限 / 越界 | 该题剔除并计数，不参与配额 | 时钟异常已有 `ClockAnomaly.TIME_ROLLBACK` 通道（`ForgettingCurve.kt:71-84`），此处不重复猜 |
| `r*` 越界 | 构造期 `require(0.7..0.97)` | 两处已有（`MemoryUpdateModel.kt:68`、`StudyExperienceRepository.kt:737`） |
| 全部候选都超单日容量 | 报 `unmet = N`，不产生"假配额" | 同 KD-64/KD-68 的既有口径（`NewIntroductionPolicy.kt:199-206`） |

---

## 4. Q2b：缺勤再平衡（missed days rebalance）

### 4.1 现状

- 全仓 `.kt` 中 `rebalance` **0 命中**（§10-①）。
- F9 现存只有 **KF-17 错峰**：同批同源新建卡按第 2、3… 张分别 +1、+2 天（`LearningProjector.kt:1042,1122`）。
- 已有的"考试追赶"（`NewIntroductionPolicy`）**在无考试时不触发**（`daysLeftToExam` 为 null/0 → 走固定 1/3 片），所以它不能当 F9 用（§3.2 口径问题 1）。
- 到期复习的"追赶"目前是隐式的：`ReviewPlannerV2` 用 `overdueDays/30` 进入 `dueRisk`（`ReviewPlannerV2.kt:512-520`），**只影响排序，不产生配额**；超预算的题当天直接不进队列。
- 结论：缺勤后没有"补账"机制，只有"逾期越久排越前"。这与 F9 的"休假/积压后追赶策略"不是一回事。

### 4.2 公式与上限（建议裁定）

设正常日容量 `C`（张/日，由预算 ÷ 估时得出）、积压 `B`（张）、剩余学习日 `D`（可排除休息日）：

```
每日预算/容量 = base × (1 + b/D)        b = B/C（积压折合"正常日"数）
成长上限 g（建议 1.5，与考试爬坡同值；**作用在预算上**，不是固定预算下的张数）
若 1 + b/D > g  →  不抬预算，改为延长 horizon：D_need = ceil(b / (g − 1))
```

> 关键约束：g 必须作用在**当日预算**（20 → 30 min）上。若只乘张数而预算不动，就是"每天 9 张但只给 20 分钟"——F8 的承诺当场被自己违反。这与 `examCapacityBudgetSeconds` 的既有做法同构（`StudyReviewPlannerService.kt:153-179`）。

| 积压 b（正常日） | D=3 | D=7 | D=14 | D=30 | 在 g=1.5 下清账所需天数 |
|---|---|---|---|---|---|
| 1 | 1.33× | 1.14× | 1.07× | 1.03× | 2 |
| 2 | 1.67× | 1.29× | 1.14× | 1.07× | 4 |
| 3 | 2.00× | 1.43× | 1.21× | 1.10× | 6 |
| 5 | 2.67× | 1.71× | 1.36× | 1.17× | 10 |
| 10 | 4.33× | 2.43× | 1.71× | 1.33× | 20 |

（1.5 的值不是外部标准，而是与本仓 `EXAM_CAPACITY_RAMP_MAX_EXTRA_FRACTION = 0.5` 对齐的**内部一致性选择**：同一个"每日最多多干 50%"的承诺，不应在考试与休假两条路上给出两个数。`AlgorithmConstants.kt:137-148`）

**拉前哪些题**（借 Helper 的"相对偏离"阈值）：
- Helper Advance：按 `1 − elapsed/I` 升序取，**相对偏离 < 0.13** 的算"安全可提前"（`schedule/advance.py:86-99`）。
- Helper Postpone：按 `elapsed/I − 1` 升序取，**< 0.15** 的算"安全可推后"（`schedule/postpone.py:83-95`）。
- 两者是同一把尺子的两端，但常数不同（0.13/0.15）。**建议本仓统一为 0.15**（更保守），并作为常量单点定义；这是我们的选择，不是上游强制。

### 4.3 与考试配额的区别（不要混用分子）

| 维度 | 考试配额（层 B） | 缺勤再平衡（F9） |
|---|---|---|
| 分子 | 保留率缺口题数（`R(exam) < r*_exam`） | 积压张数（含逾期 + 未引入） |
| 时间窗 | 考试前 0–14 天 | 任意，直到清账 |
| 触发 | 有考试日 + 窗口内 | 用户开启追赶开关 / 检测到积压 |
| 目标 | 考试日 R ≥ r\*_exam | 回到正常日需求 |
| 副作用 | 提前复习会牺牲部分稳定性增益 | 同上；且不得把未到期强题拉前 |

**硬规则**：同一天两者都在算时，先满足"今日到期复习"，再按 `deficit` 排考试缺口，最后才是积压追赶；且**同一道题的两种需求只计一次**（取 max，不叠加时间）。

### 4.4 伪代码

```kotlin
fun rebalance(
    backlogItems: List<Item>,
    baseBudgetSeconds: Int,
    p50DurationSeconds: Int,
    remainingStudyDays: Int,
    growthCap: Double = 1.5,
): RebalancePlan {
    val normalDailyCapacity = baseBudgetSeconds / p50DurationSeconds
    val b = backlogItems.size.toDouble() / normalDailyCapacity
    val requiredMult = 1.0 + b / remainingStudyDays
    val allowedMult = minOf(requiredMult, growthCap)
    // 乘子作用在预算上：20 min × 1.5 = 30 min，而不是"20 分钟里塞 1.5 倍张数"
    val effectiveBudgetSeconds = ceil(baseBudgetSeconds * allowedMult).toInt()
    val perDay = effectiveBudgetSeconds / p50DurationSeconds
    val daysNeeded = if (requiredMult <= growthCap) remainingStudyDays
                     else daysToClear(b, growthCap)
    // 只从"接近到期"的一端取：elapsed/I ≥ 1 − 0.15
    val eligible = backlogItems.filter { it.elapsedFraction >= 0.85 }
    return RebalancePlan(
        dailyTake = perDay,
        dailyBudgetSeconds = effectiveBudgetSeconds,
        daysNeeded = daysNeeded,
        picked = eligible.sortedByDescending { it.elapsedFraction }.take(perDay),
        ineligibleCount = backlogItems.size - eligible.size,   // 诚实计数，不静默
    )
}
```

### 4.5 与诚实预览（KD-68）的接口

积压追赶和考试配额都会产生"今天做不完"的量。现有诚实预览只覆盖**新引入积压**（`intakeUnschedulableCount`，`StudyReviewPlannerService.kt:299-335` → `StudySnapshotBuilder.kt:88-95,132`），且 KD-68 台账明说：数据侧半边已落，**UI 仍调粗略算术 `preview` 而不是 `previewBacklog`**，`unschedulableCount` 在生产 main/feature/app 无消费者（`docs/known-defects.md:2652`，本轮实读；feature/review 是热区）。本设计新增的两个计数（`unmetExamDeficit`、`ineligibleBacklog`）应走**同一条**"数据侧产出 + 读模型字段"的路，不要新开一个 UI 专用通道——否则 KD-68 的同类失败（承诺与实际调度不一致）会在考试配额上再演一次。

---

## 5. Q3：与现有调度器（core/data 复习队列）的接缝

### 5.1 现有链路（本轮实读）

```
StudySnapshotBuilder.build
  └─ plannerService.planningContext(projection)               // 04:00 学习日界 (StudyDayMath)
  └─ observeActiveReviewPlan / observeCurrentReviewPlan       // 版本门 + 保留/重排
  └─ plannerService.createReviewPlan(mistakes, snapshot, ctx) // :67-71
       ├─ readExams()                                         // 一次读考试表 :107-109
       ├─ examCapacityBudgetSeconds(...)                       // :308-313，+50% 线性坡道 :153-179
       ├─ 候选组装（difficulty/duration/examPriority）          // :440-475
       ├─ NewIntroductionPolicy.decide(..., daysLeftToExam)     // :506-528，ceil 追赶 :126-140
       ├─ ReviewPlannerV2.plan(request)                        // :543
       │    ├─ beamSearchSelection / greedySelection           // 按时间预算装箱 :130-160,:236-300
       │    ├─ dueRisk = 1−R + overdue/30                      // :499-520
       │    └─ 提前复习闸 R < 0.8                              // :636-655
       └─ 落 review_plan / review_queue（planFingerprint、plannerVersion） :560-600
  └─ intakeUnschedulableCount(...)                             // :299-335 → 快照字段 :132
currentKnowledgeReviewPlan                                     // :705-855
  ├─ 今日档：今日错题绑定 KC 的 R 聚合（取最小 R）
  └─ 第二档：全库扫描（FULL_LIBRARY_SCAN_DAILY_CAP=10）+ 时间预算双封顶  :818-830
```

### 5.2 接缝表

| # | 接缝 | 现状 | 本批可否改 | 改动形态 |
|---|---|---|---|---|
| S1 | 每日时间预算 | 构造默认 1200s（`RoomBackedStudyExperienceRepository.kt:1113`）；**未持久化** | ✅ `core/data` 干净 | 新 DataStore 键 + 注入；不碰 `SchedulingOptions`（core/domain 热） |
| S2 | 考试预算爬坡 | `examCapacityBudgetSeconds` `:153-179`，+50% | ⚠️ 该文件可改，但改行为需 bump VERSION（core/domain 热） | 本批只读/只测，不改公式 |
| S3 | 考试保留率爬坡 `r*_exam` | **不存在**（只有固定 0.8 提前闸） | ✅ 可在 `core/data` 新纯函数里算，作为**准入过滤** | 新函数 + 用例；默认关闭 |
| S4 | 新引入追赶 | `NewIntroductionPolicy` ceil（core/domain 热） | ❌ 不改 | 保留；层 A 复用其结果 |
| S5 | 排程排序/打分 | `ReviewPlannerV2`（core/domain 热） | ❌ 不改 | **不新增权重/乘子**，避免二次计分 |
| S6 | 知识点全库扫描 | 10 名额 + 时间双封顶 `:818-830` | ❌ 判定面（F17）不改 | 只读 |
| S7 | 诚实预览 | 模型层 `unschedulableCount` 齐（`NewIntroductionPolicy.kt:205`、快照 `:132`） | ✅ 数据侧可加字段 | 与 S3/S4 的计数同一出口 |
| S8 | 计划版本/指纹 | `ReviewPlannerV2.VERSION` 门（`StudyReviewPlannerService.kt:345-369`） | ❌ core/domain 热 | **任何改变默认输出的接线都必须 bump；本批做不到** |

### 5.3 热区与允许改动（逐条对照本批纪律）

- 禁改：`feature/*`、`core/ui`、`core/export`、`core/domain`（开工即脏 M）、`core/database`（他线 M）、`settings.gradle.kts`。
- 可改（`git status --porcelain -- <两文件>` 实跑：均无输出 = 干净）：`core/data/.../study/StudyReviewPlannerService.kt`、`core/data/.../settings/DataStoreSchedulingSettingsStore.kt`；新文件落 `core/data` 不受影响。
- `core/model` 不在禁改清单，但① 它是**设备绿绑定源快照**，新增文件必须重跑设备用例；② 该模块 `ProblemCatalog.kt` 当前是 ` M`（他线在改）。本批不建议往 `core/model` 放逻辑（见 §2.3 的分层理由）。
- **结论**：配额/再平衡的"策略"可以落 `core/data`；"激活"（改默认候选集）必须动 `core/domain` 的 `VERSION` 或 `NewIntroductionPolicy`，**本批阻塞**。

### 5.4 每项新增 → 消灭的具体失败（第 12.2 条自检）

| 新增 | 消灭的失败 | 不做的后果 |
|---|---|---|
| `ReviewCapacityPolicy.dailyQuota` | 现有 quota 只有"新引入"一条；到期复习与考试缺口没有配额，超预算时**静默截断**，学生不知道今天欠了多少 | "每天 6 张"变成"看运气"；积压无声增长 |
| `examTargetRetention` + `examDeficit` | spec §2.17 写了 `r*_exam`，代码只有固定 0.8 闸——**考试前该拉多紧没有随距离变化**，考前 14 天与考前 1 天同权 | 考前冲刺只能靠 +50% 预算与优先级，题多时仍排不进去 |
| `allocateByDayWeights`（F18） | 目前没有"周末多学/平日少学"的表达；用户只能改总预算 | 有意愿的学生被迫每天均摊，周末容量浪费 |
| 预算持久化（S1） | 预算只是构造默认，重装/重启后用户改不了也存不住（F8 "设置弱"的实指） | 设置屏无法兑现"自定义" |
| `unmetExamDeficit` / `ineligibleBacklog` 读数 | 超容与"未到安全提前窗"的题目前不进入任何计数 | 预览继续承诺做不到的覆盖（KD-68 同类） |

### 5.5 本批阻塞清单（不伪造完成）

| 项 | 阻塞点 | 需要的动作 |
|---|---|---|
| item 19/20/21 的**默认激活** | 改变默认候选集 → 必须 bump `ReviewPlannerV2.VERSION`（`core/domain` 热区） | 等非热窗口；或由主会话裁定"只做默认关闭的纯策略" |
| F8 设置屏 | `feature/review` / `app` 热区 | 只出数据侧 + 键名约定，UI 另派 |
| F9 追赶开关 | 开关状态需落设置；`SchedulingOptions` 在 core/domain（热 M） | 用**新的** `core/data` 设置类型，不改 domain 接口；或登记待接 |
| F17 "真每日累计" | 需持久账本/schema | 维持 KD-46 近似口径（每次排计划 ≤10），另立设计 |

---

## 6. Q4：F8 / F9 / F18 预算裁定的量化依据

> 本节是**裁定输入**（供 WS3 item 17 落文），不是既成事实。所有"建议值"都注明推导；无外部先例的明确写"内部裁定"。

### 6.1 F8：每日复习时间预算

**现状**：默认 1200s（20 min），构造参数，**未持久化**（C7）。全库扫描第二档另有 10 个知识点的名额上限（`KnowledgeReviewFullLibraryScan.kt:30`），其"双封顶"注释明确时间预算沿用 20 分钟（`:19-21`）。

**容量换算**（本会话算术，估时取 `LogDurationModel` 三档 90/180/300s，`LogDurationModel.kt:108-110`）：

| 预算 | 易档(90s) | 中档(180s) | 难档(300s) | 新引入 1/3 片（中档） |
|---|---|---|---|---|
| 10 min (600s) | 6 | 3 | 2 | 1 |
| 15 min (900s) | 10 | 5 | 3 | 1 |
| **20 min (1200s)** | **13** | **6** | **4** | **2** |
| 30 min (1800s) | 20 | 10 | 6 | 3 |
| 45 min (2700s) | 30 | 15 | 9 | 5 |
| 60 min (3600s) | 40 | 20 | 12 | 6 |

**外部先例**：Anki 的每日限制是**张数**（new/day、max reviews/day），没有时间预算；Deadline2/Deckline 也只做张数目标。因此"时间预算的档位"**没有外部标准可引**，只能由内部容量换算 + 产品判断定。

**建议裁定（F8）**：
1. 保留默认 **20 min**（现网行为不变的最低风险值）。
2. 用户可调区间 **10–60 min，5 min 步进**。依据：
   - 下限 10 min → 中档 3 张、新引入 1 张：低于此已不构成一次有意义的复习会话（少于 3 题无法覆盖"到期 + 新引入"两类）。
   - 上限 60 min → 中档 20 张；再高则单日会话长度超过多数学生的可持续专注窗口，且会把"欠账"伪装成"今天能做完"（诚实预览反而更难成立）。
   - 30 min 是一个有用的锚：正好等于 F17 全库扫描 10 个知识点 × 180s 的时间量——低于 30 min 时，F17 的名额上限实际由**时间**而非名额封顶（这一点必须在设置页文案里说清，否则用户以为"每天能捞 10 个")。
3. 设置页必须同时显示**保留率负载倍率**（§2.5 表）：改 r\* 不改预算 = 静默改负载。示例文案："当前 20 min @ 90%；若改到 95%，同样牌组约需 29 min。"
4. 预算持久化键建议：`review_time_budget_minutes`（Int，10..60，默认 20），校验在读取处 fail-closed 回默认，不抛给 UI。

### 6.2 F9：休假/积压追赶

**现状**：只有 KF-17 错峰（新建卡 +1/+2 天），**无追赶开关**（C5）。

**建议裁定（F9）**：
1. **语义**：追赶 = 每日重算分子（剩余积压）÷ 剩余学习日，**不是**"一次性把缺口摊到固定 7 天"。分子每天变小、分母每天变小，漏做自动进入下一天——这正是 Deadline2 的做法，也是唯一不需要额外状态的做法。
2. **上限**：每日**预算**相对正常最多 **1.5×**（20 → 30 min，与考试爬坡同值），不是"预算不动、硬塞 1.5 倍张数"。超过 1.5× 不抬预算，改为**延长 horizon**：`D_need = ceil(b/(g−1)) = 2b`（§4.2 表）。这样不会出现"休假 7 天、回来每天 3 倍"的劝退曲线。
3. **拉前范围**：只拉"已用掉 ≥85% 间隔"的题（Helper 的 0.15 相对偏离阈值反推），不把刚复习过的强题拉前（避免无谓的稳定性损失；R 依赖的稳定性增益见 `FsrsScheduleMath.kt:137-159`）。
4. **默认关闭**，关闭时逐位不变（与 `examCapacityBudgetSeconds` 的 `ramp<=0` 短路同纪律，`StudyReviewPlannerService.kt:170-171`）。
5. **不可兑现**：`b/(D) > g` 且 `D_need` 超出可接受窗口（建议 30 天）时，报"按当前预算需 N 天清账"，并把 `ineligibleBacklog` 一并给出；不要静默降级。

### 6.3 F18：周末/集中清空模式

**需求原文**（`docs/requirements-fused-final-2026-10-11.md:119`）："模式**不固定，可以选择**（可选模式，不做默认节奏）"。

**建议裁定（F18）**：
1. **日权重模型**：`quota_i = round(remaining × w_i / Σ_{j≥i} w_j)`，权重 `w_i ∈ {0,1,2}`（0=休息日，1=平日，2=周末/集中日）。用最大余数法保证 `Σ quota_i = remaining`。
2. **默认**：关闭。开启时默认 `平日 1 / 周末 2`，允许把任意一天设为 0（休息）。
3. **容量约束（必须校验）**：`w_weekend × C / Σw × 天数 ≤ 单日预算`。以 20 min/中档 6.7 张为例，周末 2× 只有在平日 ≤3.3 张时才不超预算；否则要么降权重，要么抬预算——两者都必须在设置页显式提示，不能由代码偷偷夹取。
4. **与 F9 的叠加**：先算 F9 的 `perDay`，再按 `w_i` 分配到具体日；**不二次放大**（即 F18 只重排，不改总量）。顺序写进算法台账，避免两处各乘一次。
5. **示例**（本会话算术）：剩余 60 题、14 天、其中 4 个周末日 → 权重和 `10×1 + 4×2 = 18`；平日配额 `60/18 = 3.33`、周末 `6.67`；总量守恒后约"平日 3–4、周末 6–7"。

### 6.4 F17（顺带，供交叉核对）

- spec §2.18 原文是**三件事**（`docs/specs/mastery-scheduling-spec.md:141-144`）：① 每会话**维护配额 ≥25%** 给"非最弱但近到期"题；② 同 KC 每会话 ≤2 题；③ weakness 用 7 天半衰 EMA。
- 实现现状（本轮读码）：
  - ②③ 有（`MasterySmoothing` + 队列配额）；
  - ① **没有**。代码里确实有一个 `0.25`，但语义**相反**：`MAX_WEAKNESS_ONLY_SHARE = 0.25` 是"最弱项占比超过 25% 就罚 3.0 分"（`ReviewPlannerV2.kt:801-810,1079-1080`），是**限制最弱项**，不是**给维护项保底**。
  - spec 的实现注记自己写的也是"最弱项占比 >25% 罚分"（`docs/specs/mastery-scheduling-spec.md:315`），即实现把同一个 25% 用到了另一半语义上。
- 量化：若要做"真维护配额"，25% × 6 张/日 = 1.5 → 实际至少 **2 个名额**（向上取整），否则中等预算下会退化为 0/1，起不到防振荡作用。
- 外部无对应实现（Anki 没有"维护配额"概念）→ 这是**内部裁定**，不要在文档里伪装成行业标准；F17 的现状应记作"维护配额未实现，另有最弱项份额罚分"。 

### 6.5 与 M5（r\* 70–97）的耦合

- 入口已存在：`SchedulingOptions.desiredRetention` 默认 0.9、`require(0.7..0.97)`（`StudyExperienceRepository.kt:732-740`）；设置存储 `DESIRED_RETENTION`。
- **量化规则**（建议写进设置页与验收）：
  - r\* 每 +2 个百分点（0.90→0.92）：负载约 +11%（§2.5）。
  - 0.90→0.95：+43%（新卡）到 +72%（成熟卡）。
  - 0.90→0.97：+111% 到 +143%。
  - 0.90→0.85：−16% 到 −22%。
- 因此 F8 的预算与 M5 的 r\* **必须一起呈现**；单独调任一个都会得到"看起来合理、实际不可兑现"的组合。

### 6.6 尚缺的校准（Wave X，不能声称已标定）

| 缺口 | 为什么现在不能定 |
|---|---|
| 真实每题耗时分布 | `LogDurationModel` 的三档是基线，不是本机实测；`IntakeDurationBaseline` 只是映射 |
| 每日可用时长（用户真实预算） | 无遥测；不引隐私外发 |
| 漏做率/休假频率 | 需要本地账本；当前无 |
| r\* 与真实保留率的偏差 | 需要 ≥ 一个优化周期的 review_log；优化器是离线可选面 |
| 本机 FSRS 参数 | 默认 21 参数是群体中位数（fsrs4anki 教程 A18），个体差异未拟合 |

→ 本文所有"建议值"都只能称**起步值**，与 `EXAM_CAPACITY_RAMP_MAX_EXTRA_FRACTION` 的 `PENDING_CALIBRATION` 同级。

---

## 7. 验收与测试建议（本轮**未执行**，Gradle 禁止）

> 以下只是给 WS3 的用例清单；本会话**没有运行**任何测试，不构成"通过"。

纯函数用例（JVM，新增文件，不碰门禁脚本）：
1. `dailyQuota`：`remaining=0 → 0`；`daysLeft=0 → cap`；`ceil` 边界（60/21→3；61/21→3；60/20→3；61/20→4）；`cap` 夹取。
2. `examTargetRetention`：`d=14 → r*`；`d=0 → 0.97`；`r*=0.97 → 恒 0.97`；`d=20 → r*`（窗口外）。
3. `examDeficit`：`R=0.9, target=0.9 → 0`；`R=0.5, target=0.9 → 0.4`；越界夹取。
4. `allocateByDayWeights`：总量守恒（随机 100 组）；全 0 权重抛错；单日权重 0 该日得 0。
5. `daysToClear`：`b=5, g=1.5 → 10`；`b=0 → 0`；`g<=1` 抛错。
6. 逐位不变回归：无考试/窗口外/开关关闭时，`createReviewPlan` 产出的 `planFingerprint` 与现状相同（对齐 `StudyReviewPlannerExamWindowTest.kt:82-118` 的既有做法）。
7. 新增只读读数：`unmetExamDeficit` 与 `ineligibleBacklog` 不影响 `introduced/deferred/grantedSliceSeconds`（对齐 `NewIntroductionPolicy.kt:90-98` 的 "purely descriptive" 纪律）。

禁止：为了让用例变绿而放宽 `require`、改 `cap`、或删"窗口外逐位不变"的断言。

---

## 8. 未决 / 待裁定

| # | 问题 | 需要的裁定 | 本文倾向 |
|---|---|---|---|
| U1 | F8 档位是否 10–60/5min | 产品 | 是（§6.1）；至少保留 20 默认 |
| U2 | F9 成长上限 1.5 还是 2.0 | 产品 | 1.5，与考试爬坡同值 |
| U3 | F18 周末权重默认 2 还是 3 | 产品 | 2（2× 已能把 20min 预算打满，3× 必超） |
| U4 | 拉前阈值统一 0.13 还是 0.15 | 工程 | 0.15（更保守；上游两处不一致） |
| U5 | 考试缺口与积压追赶同日冲突时谁优先 | 产品 | 到期复习 > 考试缺口 > 新引入/积压 |
| U6 | F17 维持"每次 ≤10"还是做真每日累计 | 产品 + schema | 维持（KD-46），另立设计 |
| U7 | 激活所需的 `VERSION` bump 由哪个非热窗口执行 | 主会话 | 本批登记阻塞 |

---

## 9. 来源清单

1. Anki Manual — Deck Options / FSRS：https://docs.ankiweb.net/deck-options.html （WebFetch，2026-11-02）
2. py-fsrs v6.3.2（MIT）：https://github.com/open-spaced-repetition/py-fsrs
   - `fsrs/scheduler.py`（`FSRS_DEFAULT_DECAY`、`desired_retention=0.9`、`_next_interval`）
3. fsrs-rs v6.6.2（BSD-3-Clause）：https://github.com/open-spaced-repetition/fsrs-rs
   - `src/model_v6.rs:31-42`（R 与间隔反函数）；`src/simulation.rs:587-620`（SimulatorConfig）
4. FSRS4Anki 教程（MIT）：https://github.com/open-spaced-repetition/fsrs4anki/blob/main/docs/tutorial.md
   - r\* 0.70–0.97 / 默认 90% / "Days to simulate = 到考试天数" / A9 "Advance"
5. FSRS Helper（MIT）：https://github.com/open-spaced-repetition/fsrs4anki-helper
   - README（Advance/Postpone/Schedule a Break/Load Balance/Easy Days/Flatten）
   - `schedule/advance.py`（0.13 阈值）、`schedule/postpone.py`（0.15 阈值）、`schedule/schedule_break.py`（`cost = abs(day − due) / interval`、按天均分容量）
6. Deadline2（GPL-3.0）：https://github.com/BSCrumpton/Deadline2 — `__init__.py`（`cards_per_day = ceil((new+seenToday)/days)`）
7. Deckline（无 LICENSE 文件）：https://github.com/Kwinties/Deckline — README "How targets are calculated"、FAQ "Does Deckline modify FSRS or scheduling?"
8. 本仓规范：`docs/specs/mastery-scheduling-spec.md:138-139`（§2.17 原文）、`:141-144`（§2.18 原文）、`:313,315`（两条实现注记：`r*_exam` 不入投影 / 25% 罚分）；`docs/requirements-fused-final-2026-10-11.md:115,117,119`（F8/F9/F18）；`docs/REFACTOR-MASTER-LINE.md:190`（5b）
9. 本仓基线：`docs/research/2026-11-02-batch-40-baseline.md` §5.1（WS3 现状；本轮对其中"FSRS 已完整存在/rebalance 0 命中"两条做了独立复核，结论一致）

---

## 10. 复现命令（本轮实际执行）

① 缺"再平衡"与配额实现：
```bash
cd "D:/smart mistake book"
grep -rin "rebalance" --include=*.kt core/ app/ feature/ | wc -l            # → 0
grep -rin "examQuota\|deadlineQuota\|dailyQuota\|exam_deadline" --include=*.kt core/ app/ feature/ | wc -l   # → 0
grep -rn "time_budget\|budget_minutes\|review_budget" --include=*.kt core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/settings/ | wc -l  # → 0
```

② py-fsrs 无配额/考试实现：
```bash
gh api repos/open-spaced-repetition/py-fsrs/contents/fsrs/scheduler.py --jq '.content' | base64 -d | grep -n -i "exam\|quota\|daily\|limit\|backlog"
# → 仅 2 行 "example" 误命中；无排队/日志/考试逻辑
```

③ fsrs-rs 模拟器无考试域：
```bash
gh api repos/open-spaced-repetition/fsrs-rs/contents/src/simulation.rs --jq '.content' | base64 -d | grep -n -i "exam\|deadline\|quota\|backlog"
# → 空
```

④ Helper 无 deadline 代码（GitHub 代码搜索，默认分支索引）：
```bash
gh api "search/code?q=repo:open-spaced-repetition/fsrs4anki-helper+deadline" --jq '.total_count'   # → 0
```

⑤ 负载倍率（§2.5 表）——完整可重跑：
```bash
python -I -c "
import math
w=[0.212,1.2931,2.3065,8.2956,6.4133,0.8334,3.0194,0.001,1.8722,0.1666,0.796,
   1.4835,0.0614,0.2629,1.6483,0.6014,1.0,0.5425,0.0912,0.0658,0.1542]
decay=-w[20]; factor=0.9**(1/decay)-1
R=lambda t,S: (1+factor*t/S)**decay
def I(r,S):
    n=round((S/factor)*(r**(1/decay)-1)); return min(max(n,1),36500)
def Srec(D,S,r): return min(max(S*(1+math.exp(w[8])*(11-D)*S**(-w[9])*(math.exp((1-r)*w[10])-1)),0.001),36500)
def Sfor(D,S,r): return min(max(min(w[11]*D**(-w[12])*((S+1)**w[13]-1)*math.exp((1-r)*w[14]),
                                     S/math.exp(w[17]*w[18])),0.001),36500)
def sim(r,S0,D0,H=3650):
    S,D=S0,D0; rev=0.0; t=0.0
    while t<H:
        iv=I(r,S); t+=iv; rr=R(iv,S); fail=min(max(1-rr,0),1)
        rev+=1+fail; S=fail*Sfor(D,S,rr)+(1-fail)*Srec(D,S,rr)
    return rev/H
D0=w[4]-math.exp(w[5]*2)+1
b=sim(0.9,2.3065,D0)
[print(r, round(sim(r,2.3065,D0)/b,3)) for r in [0.8,0.85,0.9,0.92,0.95,0.97]]
"
# 输出：0.8→0.683；0.85→0.784；0.9→1.0；0.92→1.107；0.95→1.434；0.97→2.112
# 注意：这是按 OptimalRetention.simulate 的逻辑用 Python 复实现，**不是**运行 Kotlin 代码。
```

⑥ 容量换算与追赶表（§6.1/§4.2）：
```bash
python -I -c "
for b in [900,1200,1800,2700,3600]: print(b, b//90, b//180, b//300)
for b in [1,2,3,5,10]: print(b, [round(1+b/d,3) for d in [3,7,14,30]])
"
```

---

## 附：一句话交付

**FSRS 的三参数数学仓内已足；本批真正缺的是"把预算持久化、把考试缺口算出来、把追赶的上限写死"这三件规划层的事——它们都不该、也不需要再写第二套调度数学。**
