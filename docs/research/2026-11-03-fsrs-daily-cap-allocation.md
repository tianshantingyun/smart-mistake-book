# 批次 41 · R1：多科共享每日复习容量时如何分配才不超总量

> 研究线 R1（计划 `plan-2026-11-03-batch-41.md` §1 第 16 行）。
> 参照实现：`ankitects/anki` scheduler v3（`rslib`）的 daily limit / new-per-day / review-per-day；
> 对照：FSRS 官方文档（Anki 手册 FSRS 章）的 desired retention 与负载管理。
> 落点：`ReviewCapacityScheduler` / `ExamQuotaPlanner` / `MissedDayRebalancePolicy`（均在 `core/data`，非热区）。
>
> 写于 2026-11-03。本轮**只读**：读了仓内实现与本轮抓取的外部源码，**未改任何源码、未运行 Gradle**（门禁由脚本串行执行）。
> 通道：`gh api`（GitHub 内容/代码搜索，`ankitects/anki` HEAD `136bd42bfb`，committer date 2026-10-29T14:24:13Z；最新 release `26.09.3`，2026-09-23）。
> 本会话实算的数字一律标"本会话计算"并附命令（§11）；外部结论一律标来源；单来源标"待验证"。

---

## 0. 结论（先看这里）

| # | 结论 | 类型 | 证据 |
|---|---|---|---|
| C1 | **Anki 的总量约束在"牌组树"这一层，不是一个全局计数器**：选中牌组的 limit 是当天总上限；每个子牌组的 limit 只是"从该子牌组最多抽几道"的子配额；子配额用不完，剩余容量可以由兄弟牌组消费（根的总量与子的配额是两份独立配额）。 | 事实 | 手册 "The selected deck's limits control the total number of cards that will be shown"（`docs-site/manual/deck-options.mdx:91`）；`LimitTreeMap` 建树与父→子 `cap_to`（`rslib/src/decks/limits.rs:231,316-328`）；取一张卡就沿祖先链递减（`limits.rs:376-389`，`queue/builder/gathering.rs:52-57`） |
| C2 | Anki 的"今日已用"是**当日已学张数**（`new_studied`/`review_studied`，跨日清零），剩余 = limit − 已用；且默认**新卡受复习剩余额度压制**（`new_limit = min(new_limit, review_limit)`、`cap_new_to_review`）。 | 事实 | `rslib/src/decks/counts.rs:22-28`；`limits.rs:91-118`、`:131-149` |
| C3 | Anki **不做跨日结转**，也不自动补账。原文："If you study fewer than the limit, or miss a day, the next day the counts will be back to their original setting: **you won't be given more cards than your limit allows.**" 超限的卡只是**继续留在 due 池**（不重排），congrats 页提示可以调高上限。 | 事实 | 手册 `deck-options.mdx:87-89`、`:104-112` |
| C4 | Anki 的模拟器把"超限"显式建模为**推迟到次日**并累加次日 due 计数（与真实调度器"留在 due 池"不同，但方向一致），另有**每日时间上限** `max_cost_perday`（默认 1800 s = 30 min）。 | 事实 | `src/simulation.rs:1983-2011`（超限分支 `card.due = day_index + 1`、`due_cnt_per_day[day+1] += 1`）、`:586-620`（配置字段与默认值） |
| C5 | 考试临近的"重分配"在 Anki 里只有**三个内建杠杆**：① 单牌组 **Today only** 日上限（`DayLimit{today,limit}`）；② 单牌组 **desired retention 覆盖**（proto `Deck.Normal.desired_retention`，作用于该牌组每张卡的间隔计算）；③ Easy Days（按星期几**重排**，不降总量）。**没有**考试日/倒计时/配额概念（全仓 `deadline` 代码搜索 1 命中且是 CSP 测试文件）。 | 事实 | `proto/anki/decks.proto:73-87`；`rslib/src/scheduler/answering/mod.rs:484,512-515`；手册 `deck-options.mdx:119-127`、`:382`、`:437`；`gh api search/code?q=repo:ankitects/anki+deadline` → 仅 `qt/tests/qwebengine_csp_smoke.py` |
| C6 | 改 desired retention **默认不立即改变负载**："The default is not to reschedule cards: future reviews will use the new scheduling, but there will be no immediate change to your workload." 想"考试前把某科拉紧并马上做"，必须先重排（会写 revlog、手册自查不建议）或走 Helper/筛选牌组。 | 事实 | 手册 `deck-options.mdx:500-514` |
| C7 | 本仓的"总量"目前**只在容量门面内部成立**（`ReviewCapacityScheduler` 把同一份容量分给考试与再平衡），**没有"到期复习已占多少"的账本**：`createReviewPlan` 不调用该门面（`grep` 生产调用点只剩 `ReviewCapacityPreviewReader`）。因此"全天总量"这一层**尚不存在**，现在成立的是"考试+再平衡 ≤ 日容量"。 | 事实（缺口） | `core/data/.../study/ReviewCapacityScheduler.kt:46-93`；`core/data/.../study/StudyReviewPlannerService.kt:372` 是唯一生产调用点（`grep -rn` 见 §11-①） |
| C8 | 现有分科算法是**按应取数等比切分**（`allocateByDayWeights(dailyCapacity, desiredQuotas)`），它保总量但对"紧迫度"盲：容量 6、数学明天考（缺口 10）、物理 14 天后考（缺口 60）时切成 **[3,3]**，数学只拿到应取的 6 的一半；按 Anki 的"取用顺序 + 子配额"语义应为 **[6,0]**。 | 事实（读码）+ 计算 | `ReviewCapacityScheduler.kt:66-75`；数值见 §4.3（本会话计算） |
| C9 | 现有两处可指认缺陷（不是"风格"）：① 再平衡**先取题后去重**，重复题白占名额（测试里容量 6、去重后只 4、4 道合格积压题没被取）；② 预览把**已爬坡**预算当基准传入，门面再乘 1.5 → 可达 **2.25×** 基准预算。 | 事实（读码+测试即证据） | `ReviewCapacitySchedulerTest.kt:60-80`（dedup=2 且 total=4 < 6）；`StudyReviewPlannerService.kt:366-382` 传 `effectiveBudgetSeconds`，`ReviewCapacityScheduler.kt:47-54` 再乘 `MAX_DAILY_GROWTH` |

---

## 1. 证据口径

### 1.1 一手来源（本轮实读）

| 来源 | 版本 / 状态 | 许可 | 读到的关键内容 | 访问方式 |
|---|---|---|---|---|
| `ankitects/anki` 手册源 `docs-site/manual/deck-options.mdx` | HEAD `136bd42bfb`（2026-10-29） | 仓库整体 AGPL-3.0-or-later（`LICENSE` 首段）；手册文本 | Daily Limits 全节；Desired Retention；Easy Days；Reschedule Cards on Change；The Simulator；CMRR **已于 25.07 移除** | `gh api` 内容 API |
| `rslib/src/decks/limits.rs` | 同上 | AGPL-3.0-or-later（文件头） | `RemainingLimits` 公式；`cap_to`；`LimitTreeMap` 建树/递减/封顶子孙 | `gh api` |
| `rslib/src/decks/counts.rs` | 同上 | 同上 | `new_rev_counts(today)`：当日已学张数，跨日归零 | `gh api` |
| `rslib/src/scheduler/queue/builder/gathering.rs` | 同上 | 同上 | 取卡顺序（learning → review → new）与"每取一张就递减本牌组+祖先" | `gh api` |
| `rslib/src/scheduler/queue/builder/mod.rs` | 同上 | 同上 | `apply_all_parent_limits` 决定是否把祖先牌组并入限制树；`new_cards_ignore_review_limit` | `gh api` |
| `proto/anki/decks.proto` | 同上 | 同上 | `DayLimit`、`review_limit`/`new_limit`（单牌组覆盖）、`*_limit_today`（仅今天）、`desired_retention`（单牌组覆盖） | `gh api` |
| `rslib/src/scheduler/answering/mod.rs` | 同上 | 同上 | `home_deck.effective_desired_retention(&config)` → `fsrs.next_states(...)`（单牌组 r* 真的进间隔） | `gh api` |
| `rslib/src/scheduler/states/load_balancer.rs` | 同上 | 同上 | 负载均衡：`weight = (1/cards_due)^2.15 · (1/target_interval)^3`，窗口 `MAX_LOAD_BALANCE_INTERVAL = 90`；Easy Days 修饰权重 | `gh api` |
| `open-spaced-repetition/fsrs-rs` `src/simulation.rs` | HEAD `3be7d2af54`（2026-10-28）；最新 release `v6.6.2`（2026-08-28） | BSD-3-Clause | `SimulatorConfig`（`learn_limit`/`review_limit`/`new_cards_ignore_review_limit`/`max_cost_perday`）；超限推迟到次日；CMRR 目标 `total_cost/total_memorized`；`pub fn optimal_retention` 仍在库内 | `gh api` |
| 批次 40 R3 `docs/research/2026-11-02-fsrs-capacity.md` | 本仓 | — | FSRS 三参数/`r*_exam`/F8/F9/F18 的裁定输入；本轮**复用了它的数值口径**，并更正其中一处过期结论（§1.3） | 本仓读 |

### 1.2 交叉验证

| 事实 | 来源 A | 来源 B | 判定 |
|---|---|---|---|
| 总量 = 选中牌组 limit，子牌组 limit 只是子配额 | 手册 `:91` | 代码：选根建树 + 子节点 `cap_to(父)`（`limits.rs:328`）+ 取卡沿祖先递减（`limits.rs:376-389`） | **一致**，采纳 |
| 新卡默认受复习剩余压制 | 手册 "By default, the review limit also applies to new cards"（`:131`） | 代码 `review_limit -= new_today_count; new_limit = min(new_limit, review_limit)`（`limits.rs:106-108`） | **一致**，采纳 |
| 不做跨日结转 | 手册 `:87-89` | 代码：`counts.rs:22-28` 跨日归零；`limits.rs:98-104` 剩余 = limit − 今日已学 | **一致**，采纳 |
| 超限卡留在 due 池、不重排 | 手册 `:104-112` | 模拟器反而"推到次日"（`simulation.rs:1995-2011`） | **不一致但可解释**：真实调度器不重排；模拟器为了可算而推到次日。本仓取"不重排"，见 §3 |
| 考试重分配靠 today-only 上限 + 单牌组 r\* | 手册 `:119-127`、`:437` | proto 字段 `:82-87`；调度侧 `answering/mod.rs:484` | **一致**，采纳 |
| "CMRR 可用来选 r\*" | 批次 40 R3 文档 §1.1 | 本轮手册 `:516-525` 标注 **Removed Feature (25.07)**；`fsrs-rs` 库内函数仍在 | **批次 40 结论过期** → 更正：算法仍在上游库，Anki UI 与手册已移除；不得再引"Anki 现有 CMRR 入口" |

### 1.3 复用判定（GitHub 优先纪律）

| 候选 | 可复用性 | 许可/维护 | 结论 |
|---|---|---|---|
| `ankitects/anki` `LimitTreeMap` | 层级配额 + 递减封顶的**语义** | AGPL-3.0-or-later；2026-10 仍活跃（HEAD 2026-10-29） | **只借语义与公式，不复制代码**（AGPL 与本仓不兼容；且不变量可自写纯函数表达） |
| `ankitects/anki` `RemainingLimits` | `limit − 已学`、`new ≤ review` 两条公式 | 同上 | 采纳为**数学**（公式不受版权保护），以本仓命名重写 |
| `anki` Today-only `DayLimit` | "单次、只对今天生效的科目上限" | 同上 | 采纳入 `ExamQuotaPlanner` 的 `quotaCap` 语义（已有，需补"只对今天生效"的记账） |
| `anki` Easy Days | "重排不改总量" | 同上 | 采纳为 F18 的既有不变量证据（`allocateByDayWeights` 守恒，手册 `:382`："all days to Reduced … same workload"） |
| `anki` Load Balancer | 按天负载平滑的权重式 | 同上 | **不采纳**：它是"单卡间隔在 ±(90/1.1) 天窗口内加权随机"（`load_balancer.rs:21,29-35,419-431`），与本仓"投影必须确定性可重放"（`FsrsScheduleMath.kt:11-12`）冲突；随机源会污染投影 |
| `fsrs-rs` `optimal_retention` | r\* 的工作量目标函数 | BSD-3-Clause | **不新增**：本仓 `OptimalRetention` 已按同目标实现（批次 40 C1）；本批只借 `total_cost/total_memorized` 作为"负载管理"的口径说明 |
| `anki` `new_per_day_minimum` | 疑似"新卡保底" | 同上 | **不采纳（且不可用）**：全仓代码搜索 3 命中（`deckconfig/mod.rs:46,236-241`、`schema11.rs`、`deck_config.proto`），**无一处调度消费**；不能拿它当"保底配额"为我们的公平性背书 |

---

## 2. Q1：总量约束在哪一层（全局 / 科 / 牌组）

### 2.1 Anki 的答案：三层同时生效，但只有一层是"总量"

```
[LimitTreeMap]  根（选中牌组，limit = 当日总量）
   └─ 子牌组 A（limit = 从 A 最多抽几张）
   └─ 子牌组 B（limit = 从 B 最多抽几张）
```

- **剩余额度**（`decks/limits.rs:91-118`）：
  ```
  review_limit = (牌组今日覆盖 ?? preset.reviews_per_day) − review_studied_today
  new_limit    = (牌组今日覆盖 ?? preset.new_per_day)      − new_studied_today
  if !new_cards_ignore_review_limit:
      review_limit -= new_studied_today
      new_limit     = min(new_limit, review_limit)
  ```
- **父→子封顶**（`limits.rs:316-328`）：子节点建树时 `child_limits.cap_to(parent_limits)`，即 `子.remaining = min(子.own, 父.remaining)`。
- **取一张就沿祖先递减**（`limits.rs:376-389`）：本节点减 1 后递归父节点；某节点减到 0 时 `cap_node_and_descendants` 把子孙一起压到该值（防止子配额总量超过父总量）。
- **祖先是否参与**由 `apply_all_parent_limits` 决定（`queue/builder/mod.rs:138-146`）；手册：默认**不参与**（"the daily limits of a higher-level deck do not apply if you select one of its subdecks"，`:139-143`）。
- **取用顺序**（`gathering.rs:14-20`）：intraday learning → interday learning → review → new。总量是先到先得，**不是按子牌组等比切分**。
- **没有公平保底**：Anki 没有"每个子牌组至少 1 张"的机制（`new_per_day_minimum` 未被调度消费，§1.3）。

### 2.2 本仓的三层，与真实缺口

| 层 | 载体 | 现状 | 结论 |
|---|---|---|---|
| 全局（一天多少分钟/多少题） | `reviewTimeBudgetSeconds`（默认 1200 s，`RoomBackedStudyExperienceRepository.kt:98`）→ `ReviewPlannerV2` 的装箱上限（`core/domain/.../ReviewPlannerV2.kt:126,145,226`） | **存在，但同一份预算被两个消费者各自整份使用**：`createReviewPlan` 把整份（含考试爬坡的）预算交给 V2（到期复习 + 新引入 1/3 片都在里面），容量门面又拿同一份预算装考试+再平衡。二者之间**没有预留账本** | **缺"根"**：全天总量必须是一份账本，`dueReserved + intake + exam + rebalance ≤ capacity(total)` |
| 科 | 只在 `examEpochDayBySubject` / `subjectId` 出现 | 考试通道按科分组（`ReviewCapacityScheduler.kt:39-45`），但没有"科"级 cap 概念，只有等比切分 | **缺"子配额"语义**：应改成 `cap_i = min(demand_i, 剩余)`，按紧迫度依次取 |
| 牌组 / 题 | `ReviewCapacityFacts`（单题） | 单题时长由 `LogDurationModel` 三档给（`p50DurationSeconds` 是**全科同一个中位数**） | 计数容量 + 统一 p50 是近似；真实时长差会让"6 题"变"8 题的时间" |

### 2.3 关键判断（决定 WS1 item 1 该怎么改）

1. **"每科各拿整份 budget"这一条在当前树里已经修掉了**：`ReviewCapacityScheduler.kt:66-75` 在 `Σdesired > dailyCapacity` 时把同一份容量按比例分给各科（`allocateByDayWeights`），`ReviewCapacityPlan` 的 `init` 也已有 `require(examPicked.size <= dailyCapacity)`（`:175`）与 `require(totalPickedCount <= maxDailyCapacity)`（`:178`）。批次 40 §8 发现 15 描述的 `:39-62` 老形态**已不存在**。
2. **真正缺的是三件**：① 紧迫度（现在是等比）；② 到期复习的预留（现在没有）；③ 时间口径（只数张数，不核 `Σduration`）。
3. **不要新增"牌组"概念**：本仓没有 deck，只有 subject。Anki 的"牌组树"在本仓的等价物是"subject 列表 + 一个根总量"，用平铺的 `cap_i` 就能表达，不需要树。

---

## 3. Q2：超限时如何推迟

### 3.1 Anki 的答案（三条硬事实）

1. **不重排、不结转**：超限的 review 卡"stay due"，第二天还是 due；每日额度回到原值（手册 `:87-89`、`:104-112`）。
2. **新卡先被压**：复习额度到 0 时默认连新卡也不给（`limits.rs:106-108`），手册专门建议"有积压时先别引入新卡，否则积压更糟"（`:131-135`）。
3. **提示而非静默**：被限制隐藏时 congrats 页会提示"考虑调高上限"（`:108-110`）。即"今天欠了多少"是**可见事实**，不是暗账。

模拟器为了可计算，把超限显式推到次日（`simulation.rs:1995-2011`）：

```rust
if /* 超限 */ {
    if !is_learn { due_cnt_per_day[day] -= 1; due_cnt_per_day[day+1] += 1; }
    card.due = day_index as f32 + 1.0;      // 次日再试
    continue;                                // 时间上限 max_cost_perday 走同一个推迟分支
}
```

### 3.2 本仓映射

| Anki 行为 | 本仓已有对应 | 需补 |
|---|---|---|
| 超限留 due，不写新到期日 | 未取中的题**不动** `nextReviewAtEpochMillis`（`ReviewCapacityFacts` 是只读事实，门面不写回） | 把它写成**不变量**：`picked` 之外的事实不得被修改（现在天然成立，需用例钉住） |
| 不结转、次日重算 | `ExamQuotaPlanner` 每天用 `ceil(剩余缺口 / 剩余学习日)` 重算（`ExamQuotaPlanner.kt:77-82`）→ 漏做自动进入下一天，**无需状态** | 把"重算"与"结转"区分写进注释，防止以后有人加 `carryOver` 字段 |
| 超限可见 | `ExamQuotaPlan.unmetCount`（`:92`）、`RebalancePlan.ineligibleCount`（`MissedDayRebalancePolicy.kt:96`）、`ReviewCapacityPreview`（`ReviewCapacityPreviewReader.kt:150-157`） | 逐科 `unmet`（现在只有一个总数） |
| 新卡先被压 | **没有**：`NewIntroductionPolicy` 取固定 1/3 时间片（`core/domain/.../NewIntroductionPolicy.kt:31,120`），积压时照旧引新 | 见 §5.4：积压期新引入份额应为 0；落点在 `core/domain`（热区）→ 本批只登记 |
| 不做 reschedule | 没有 reschedule 通道 | **保持不做**：手册明说重排会写 revlog 且"not recommended"（`:500-514`） |

**裁定（推迟策略）**：取"留在 due 池 + 当日如实报 `unmet` + 次日按剩余天数重算"三件，**不引入**"把到期日往后推"的通道。理由：重排会污染记忆状态/写 revlog，且与本仓"投影必须事件确定性可重放"的既有纪律冲突（`FsrsScheduleMath.kt:11-12`）。

---

## 4. Q3：考试临近如何重分配

### 4.1 Anki 的答案：三个杠杆，都在"牌组"上，且都受根总量约束

| 杠杆 | 机制 | 能否把容量**从别的科目挪过来** |
|---|---|---|
| **Today only** 日上限 | `Deck.Normal.review_limit_today = DayLimit{limit, today}`；`current_review_limit` 先看今日覆盖再看常规值（`limits.rs:22-36`） | **不能单向**：抬高本科上限不会抬高根总量；要真正挪，必须同时抬根或压低别科 |
| **单牌组 desired retention** | `Deck.Normal.desired_retention`（`decks.proto:87`）→ `home_deck.effective_desired_retention` → `fsrs.next_states`（`answering/mod.rs:484,512-515`） | **改的是需求侧，不是配额**：r\* 高 → 到期更多 → 更容易撞上限；且默认不重排 → **当天没有立即变化**（`:500-514`） |
| **Easy Days** | 按星期几给 `load_modifier`，`0` 表示这天不排（`load_balancer.rs:37-70,284-300`） | **只是重排**："all days to Reduced/Minimum = same workload as Normal"（手册 `:382`） |

**结论**：Anki 里"考试临近多给某科"等价于——**根总量不动，把本科 today-only 上限提到 `min(本科需求, 根剩余)`，并把其他科的 today-only 上限压到 0**；r\* 只用来解释"为什么这科的需求变多了"。这正是"转移"，不是"各科各自加码"。

### 4.2 建议算法：按紧迫度贪心 + 子配额（Anki 取用顺序语义，平铺版）

```kotlin
// 纯函数，新增在 core/data 的 ReviewCapacityPolicy（非热区）
fun allocateSubjectCaps(
    demands: List<Int>,            // 各科应取（= min(ceil(deficit/daysLeft), capacity)）
    daysUntilExam: List<Int>,      // 各科距考天数（1..14）
    deficit: List<Int>,            // 各科缺口题数（仅用于同紧迫度内的排序）
    capacity: Int,                 // 共享的当日剩余容量
): List<Int>
```

规则（三条，全部可测）：

1. **顺序**：按 `(daysUntilExam asc, deficit desc, subjectId asc)` 排序（全序 → 结果与输入顺序无关，`T12`）。
2. **取用**：`cap_i = min(demand_i, remaining)`，`remaining -= cap_i`；`Σ cap == min(Σ demand, capacity)`（守恒 + 工作守恒，`T10`）。
3. **不设保底**：`demand_i > 0` 但 `remaining == 0` 的科得到 0，**并进 `starvedSubjects` 如实上报**。

> **为什么不加"每科至少 1 张"**（第 12.2 条自检）：它会制造一个具体失败——"考前 1 天的科被考前 14 天的科抢走 1 个名额"，而后者明天仍然能拿（窗口还有 13 天），前者明天 `d=0` 就**没有机会**（`ExamQuotaPlanner.kt:46-49` 当天零输出）。所以保底在这里不是公平，是**把最后一次机会换成一次可延后的机会**。Anki 同样没有保底（§1.3），一致。

### 4.3 数值反例（本会话计算，命令见 §11-②）

设定：容量 `capacity = 6`（1080 s / 180 s，或 1200/180 取整 6）。

| 科 | 距考 d | 缺口 | 应取 `min(ceil(缺口/d), 6)` | **现有等比分配** | **建议紧迫度贪心** |
|---|---|---|---|---|---|
| 数学 | 1 | 10 | `min(10,6)=6` | **3**（`allocate(6,[6,5])=[3,3]`） | **6** |
| 物理 | 14 | 60 | `min(5,6)=5` | **3** | **0**（今天不需要它：5 道/天 × 13 天仍够） |

等比分配把"明天就考"的科砍到应取的一半，把名额给了"还有 13 天"的科。这不是容量不够，而是**分配规则与紧迫度无关**。

三科场景 `allocate(6,[6,5,5]) = [2,2,2]`（同上命令）：容量 6 时每科 2，离考最近的科同样只拿 2。

### 4.4 落点：`ExamQuotaPlanner` 不变的部分与要变的部分

| 项 | 现状 | 裁定 |
|---|---|---|
| 缺口定义 `deficit = max(0, r*_exam − R(examDay))` | `ExamQuotaPlanner.kt:65-70` | **保留**（spec §2.17 的落地，批次 40 已裁） |
| 应取 `ceil(缺口/剩余学习日)`，夹 `capacity` | `:77-82` + `ReviewCapacityPolicy.dailyQuota` | **保留** |
| 缺口排序（deficit desc → R(exam) asc → due asc → id） | `:71-76` | **保留**（科内排序；科间排序是新增层，见 §4.2） |
| `quotaCap` 入参 | `:37,81` | **保留**，但语义明确为"该科今天的分得名额"，由 `allocateSubjectCaps` 提供 |
| 新增：`feasibleBeforeExam` | — | `deficit ≤ quotaCap × studyDaysLeft`；false 时该科**无论怎么排都赶不上**，必须作为诚实读数上报（`T9`） |

> `feasibleBeforeExam` 消灭的具体失败：等比/贪心都能算出"今天取几道"，但没有一处回答"**按今天这个名额，考前做得完吗**"。批次 40 §3.4 只在文档里算过（"300 道缺口考前 21 天不可兑现"），代码里没有这个布尔值，产品层就会继续承诺做不到的覆盖（KD-68 同类）。

---

## 5. Q4：欠账如何回补

### 5.1 Anki 的答案：不补，只提示 + 建议停新卡

- 手册原文（`:87-89`）：漏一天，第二天额度**回到原值**，不会补给你。
- 手册原文（`:131-135`）：有积压时"stop introducing new cards until you catch up … Continuing to introduce new cards when you're already behind can make the backlog worse."
- 因此 Anki 的"回补"只有三条路：调高上限（手动）、筛选牌组/Helper 的 Advance/Postpone（手动）、重排（写 revlog，手册不建议）。

### 5.2 本仓是有意分歧：1.5× 预算追赶

`MissedDayRebalancePolicy` 每天按"剩余积压 ÷ 剩余学习日"重算，并把**预算**放大到最多 `MAX_DAILY_GROWTH = 1.5`（`MissedDayRebalancePolicy.kt:56-72`）。这是**内部裁定**（批次 40 §6.2），不是 Anki 行为，文档中必须一直这样标注。

### 5.3 偿还率公式（本会话计算揭示的边界）

设正常日容量 `C`（张/日）、积压 `b`（以 `C` 为单位的"正常日"）、每日新到达 `a`（同样以 `C` 为单位）、成长上限 `g`：

```
每日净偿还 = (g − a)·C          ⇒  b 清空所需天数 = ceil(b / (g − a))   ，仅当 a < g
若 a ≥ g → 永远清不完（积压不再下降）
```

`ReviewCapacityPolicy.daysToClear(b, g) = ceil(b/(g−1))`（`ReviewCapacityPolicy.kt:200-209`）就是 **a = 1.0** 的特例。

| 到达率 a | b = 14 时清空天数 | 说明 |
|---|---|---|
| 1.0（只有常规需求） | **28** | 与现有公式一致 |
| 1.2（该科本身偏重） | **47** | 现在会被报成 28 天，**少报 19 天** |
| 1.5 | **永不** | 现在会被报成 28 天 |
| 1.6 | **永不** | 同上 |

> 可指认的失败：预览对"偏重科目"报一个永远达不到的清账日（28 天），用户据此安排复习 → 到第 28 天发现还是欠着。修法：`daysToClear` 增加可选 `arrivalMultiplier`（默认 1.0 保持逐位不变），`a ≥ g` 时返回 `Int.MAX_VALUE` 或让调用方读 `clearsAtAll = false`；**不得**静默把 ∞ 夹成某个有限天数。

### 5.4 积压期该不该继续引新题（Anki 的做法值得抄）

Anki 默认 `new_cards_ignore_review_limit = false`（`limits.rs:106-108`），复习额度用完就不再给新卡；手册还专门建议积压时停新卡（`:131-135`）。本仓 `NewIntroductionPolicy` 固定给新引入 1/3 时间片（`core/domain/.../NewIntroductionPolicy.kt:31,120`），**与是否有积压无关**。

- 结论：这是一个真实缺口（"积压期间还在引入新题，把 backlog 越做越大"）。
- 落点：`NewIntroductionPolicy` 在 `core/domain`（**热区**，本批禁改）。本批只登记为 WS1 之后的接线项，不实现。
- 若要实现，最小形态：`rebalanceBacklogCount > 0 && rebalanceEnabled` → 新引入份额 0（不是 1/3），并保持"无积压时逐位不变"。判据：同一输入下净引入数从 2 → 0，其余字段逐位相同。

---

## 6. 可直接落地的算法（三个类 + 一个读数）

> 以下全部在 `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/`（非热区；`git status` 实跑该目录仅 `TutorFigureProposalExecutionTest.kt` 一个他线 M，见 §11-③）。本文件只出**设计**，不改码。

### 6.1 `ReviewCapacityScheduler.plan`：加"根账本"与"紧迫度分配"

```kotlin
fun plan(
    facts: List<ReviewCapacityFacts>,
    todayEpochDay: Long,
    examEpochDayBySubject: Map<String, Long>,
    baseRetention: Double = ReviewCapacityPolicy.BASE_TARGET_RETENTION,
    budgetSeconds: Int,
    p50DurationSeconds: Int,
    rebalanceEnabled: Boolean = false,
    remainingStudyDays: Int = 0,
    studyDayWeightsBySubject: Map<String, List<Int>> = emptyMap(),
    reservedByDueReviewCount: Int = 0,      // 新增：V2 已占的名额（默认 0 → 逐位不变）
    growthCeilingBudgetSeconds: Int? = null, // 新增：绝对上限所用的预算（null = budgetSeconds×1.5）
): ReviewCapacityPlan
```

改动点（每一条都对应一个具体失败）：

先定"根账本"（三行，取代现在的 `:46-54`）：

```kotlin
val dailyCapacity = (ReviewCapacityPolicy.capacityPerDay(budgetSeconds, p50DurationSeconds)
    - reservedByDueReviewCount).coerceAtLeast(0)          // 剩余名额，可为 0
val ceilingBudget = growthCeilingBudgetSeconds ?: ReviewCapacityPolicy.grownBudgetSeconds(
    baseBudgetSeconds = budgetSeconds, multiplier = ReviewCapacityPolicy.MAX_DAILY_GROWTH)
val maxDailyCapacity = ReviewCapacityPolicy.capacityPerDay(ceilingBudget, p50DurationSeconds)
```

| 改动 | 消灭的失败 |
|---|---|
| 剩余容量必须从"扣除预留后的预算"算，**不得直接对剩余秒数调 `capacityPerDay`** | `capacityPerDay(0, p50)` 仍返回 1（`ReviewCapacityPolicy.kt:148-152` 的 `coerceAtLeast(1)`）：预算已被到期复习用尽时，会凭空多排一道题 |
| `maxDailyCapacity` 由 `growthCeilingBudgetSeconds` 算（默认 `budget×1.5`） | 2.25× 双爬坡（C9-②） |
| 分科 cap 改 `allocateSubjectCaps`（§4.2） | 等比切分让近考科拿不到应取名（C8） |
| 再平衡入参改 `facts.filter { it.practiceUnitId !in examIds }` | 先取后去重白占名额（C9-①） |
| `ReviewCapacityPlan` 新增 `subjectReadouts: List<SubjectCapacity>`、`starvedSubjects`、`reservedByDueReviewCount` | 只有总数，看不到"哪一科被挤"（WS1 item 3） |

不变量（全部写进 `init`，违反即构造期抛错）：

```kotlin
require(subjectReadouts.sumOf { it.granted } == examPicked.size)
require(examPicked.size + (rebalance?.picked?.size ?: 0) - deduplicatedCount == totalPickedCount)
require(totalPickedCount <= maxDailyCapacity)
require(examPicked.isEmpty() || totalPickedCount <= dailyCapacity)
require(deduplicatedCount == 0)                        // 取题前已排除 → 交集必须为空
require(starvedSubjects.size + subjectReadouts.count { it.granted > 0 } == subjectReadouts.size)
```

### 6.2 `ExamQuotaPlanner.plan`：保持公式，补两个诚实读数

`quotaCap` 语义不变（该科今天分到的名额）。新增返回：

```kotlin
data class ExamQuotaPlan(
    ...,
    val feasibleBeforeExam: Boolean,   // deficit <= quota × studyDaysLeft
    val unmeetableDeficit: Int,        // 即使按 capacity 满额也做不完的缺口（今日口径）
)
```
`require(deficitCount == picked.size + unmetCount)` 保留（`ExamQuotaPlanner.kt:122-124`）。

### 6.3 `MissedDayRebalancePolicy.plan`：去重前置 + 到达率

```kotlin
fun plan(
    backlog: List<ReviewCapacityFacts>,
    baseBudgetSeconds: Int,
    p50DurationSeconds: Int,
    remainingStudyDays: Int,
    growthCap: Double = ReviewCapacityPolicy.MAX_DAILY_GROWTH,
    itemCap: Int? = null,
    excludedPracticeUnitIds: Set<String> = emptySet(),  // 新增：考试通道已取
    arrivalMultiplier: Double = 1.0,                     // 新增：默认 = 现有 daysToClear 口径
): RebalancePlan
```
新增派生量 `clearsAtAll = arrivalMultiplier < growthCap`；`daysNeeded = if (!clearsAtAll) Int.MAX_VALUE else ceil(b/(growthCap − arrivalMultiplier))`。

### 6.4 `ReviewCapacityPreviewReader.preview`：逐科 + 总量

`ReviewCapacityPreview` 增加：

```kotlin
data class SubjectCapacity(
    val subjectId: String,
    val daysUntilExam: Int,
    val demand: Int,
    val granted: Int,
    val unmet: Int,
    val feasibleBeforeExam: Boolean,
)
// 字段：subjects, totalGranted, dailyCapacity, reservedByDueReview
require(totalGranted == subjects.sumOf { it.granted })
require(totalGranted <= dailyCapacity)
```

---

## 7. 可测判据（每条：断言 → 落点 → 现状）

> "现状"= 本会话读码结论。"有"表示已有等价断言或 `require`；"缺"表示必须新增用例。

| # | 断言（可执行） | 落点 | 现状 |
|---|---|---|---|
| T1 | `sum(picked_i) <= dailyCapacity`（逐科求和 ≤ 日容量） | `ReviewCapacitySchedulerTest` | **有等价**：`examPicked.size <= dailyCapacity`（`ReviewCapacitySchedulerTest.kt:100-104`）+ `require`（`ReviewCapacityScheduler.kt:175`） |
| T2 | `totalPickedCount <= maxDailyCapacity` | 同上 | **有**：`require` `:178`，用例 `:148` |
| T3 | 有考试取题时 `totalPickedCount <= dailyCapacity` | 同上 | **有**：`require` `:181`，用例 `:127` |
| T4 | `Σ duration(picked) <= budgetSeconds`（时间口径，不只是张数） | 同上（新增 `totalPickedDurationSeconds`） | **缺**：现在只数张数，统一 p50 近似，无法证明"没有超时" |
| T5 | `rebalance.picked ∩ examPicked == ∅` 且 `deduplicatedCount == 0` | `ReviewCapacitySchedulerTest` + `init` | **缺（且当前不成立）**：`ReviewCapacitySchedulerTest.kt:60-80` 断 `deduplicatedCount == 2`，即两通道确实重叠 |
| T6 | 工作守恒：任一 `eligible` 积压题未被取时，不得存在空余名额（`totalPickedCount == min(maxDailyCapacity, examPicked + eligibleDistinct)`） | 同上 | **缺**：同用例容量 6、实取 4、4 道合格题未取 |
| T7 | `maxDailyCapacity <= capacity(budgetBase × 1.5)`，即入参不得是已爬坡预算 | `ReviewCapacitySchedulerTest`（新用例：传 `baseBudget=1200`、`growthCeiling=1800`） | **缺（且当前可达 2.25×）**：`StudyReviewPlannerService.kt:366-382` 传已爬坡值 |
| T8 | 预留：`dailyCapacity == capacityPerDay(budget) − reserved` 且 `totalPickedCount <= dailyCapacity`；`reserved=0` 时与改动前逐位相同 | 同上 | **缺**（字段不存在） |
| T9 | 紧迫度：`capacity=6, d=[1,14], deficit=[10,60] → caps=[6,0]`；且 `feasibleBeforeExam` 对 d=1 科为 `true` | `ReviewCapacityPolicyTest`（`allocateSubjectCaps`） | **缺**：现为 `[3,3]`（§4.3） |
| T10 | 守恒：`Σ allocateSubjectCaps(...) == min(Σdemand, capacity)`，随机 200 组；`capacity=0 → 全 0` | 同上 | **缺** |
| T11 | 科级上限不得超本科需求：`cap_i <= demand_i`（等价于 Anki 的"子配额只是上限"） | 同上 | **缺** |
| T12 | 确定性：输入 `facts` 打乱顺序，输出逐位相同（全序排序键含 `subjectId`/`practiceUnitId`） | `ReviewCapacitySchedulerTest` | **部分**：科内有序（`ExamQuotaPlanner.kt:71-76`），科间顺序依赖 `sortedBy { it.key }`（`:45`）→ 已确定；补打乱输入用例 |
| T13 | fail-closed：`facts` 含重复 `practiceUnitId` → 抛 `IllegalArgumentException`；`budget<=0`/`p50<=0` → 抛错 | 同上 | **部分**：picked 去重已查（`ReviewCapacityScheduler.kt:172`），`facts` 入参**未**查重 |
| T14 | 考试当天/窗口外零输出：`d=0` → `ExamQuotaPlan.none`，`examPicked` 空 | `ExamQuotaPlannerTest:16-34` | **有** |
| T15 | 到达率：`daysToClear(14, 1.5, a=1.2) == 47`；`a=1.5 → clearsAtAll=false`；`a=1.0` 时与现值 28 逐位相同 | `ReviewCapacityPolicyTest` / `MissedDayRebalancePolicyTest` | **缺**：`daysToClear` 只有两参（`ReviewCapacityPolicy.kt:200`） |
| T16 | 逐科读数：`totalGranted == subjects.sumOf{granted}`，`unmet_i == demand_i − granted_i`，且只有被挤的科进 `starvedSubjects` | `ReviewCapacityPreviewReaderTest` | **缺**：预览无逐科字段（`ReviewCapacityPreviewReader.kt:150-157`） |
| T17 | 无考试 + 再平衡关闭 → `ReviewCapacityPreview.EMPTY` 逐位不变 | `ReviewCapacityPreviewReaderTest:92-104` | **有** |

**禁止**：为让 T5/T6 变绿而删 `deduplicatedCount` 断言或放宽 `require`；正确做法是"取题前排除"，`deduplicatedCount` 随之恒 0。

---

## 8. 与 WS1 item 1–3 的对应（本批该怎么用这份文档）

| WS1 项 | 本文档提供的 | 仍需人做 |
|---|---|---|
| item 1 `ReviewCapacityScheduler` 总量 ≤ 日容量 | 总量已在（C1/T1–T3）；**新增**：紧迫度分配（§4.2）、预留（T8）、时间口径（T4）、去重前置（T5/T6）、单次爬坡（T7） | 改码 + 先红后绿用例 |
| item 2 `ExamQuotaPlanner` / `MissedDayRebalancePolicy` 合并去重后总量口径 | 去重前置（§6.3）、`feasibleBeforeExam`（§6.2）、到达率（§5.3） | 同上 |
| item 3 `ReviewCapacityPreviewReader` 逐科 + 总量 | §6.4 的字段与两条 `require` | 同上 |

---

## 9. 未决 / 待裁定

| # | 问题 | 需谁裁 | 本文倾向 |
|---|---|---|---|
| U1 | 分科顺序用"距考天数"还是"缺口密度 `deficit/daysLeft`" | 主会话 | 距考天数（Anki 取用顺序语义；密度会让 14 天后的大科目压过明天的科目） |
| U2 | `reservedByDueReviewCount` 由谁提供 | 主会话 | `createReviewPlan` 在调用门面时传 `plan.picked.size`（V2 输出即真相）；未接线前默认 0 |
| U3 | 积压期新引入份额是否归零（§5.4） | 产品 | 归零；落点 `NewIntroductionPolicy` 在 `core/domain` 热区 → 本批只登记 |
| U4 | `arrivalMultiplier` 的观测源 | 工程 | 近 7 天每日实际完成数 / `capacityPerDay`；没有账本前默认 1.0 |
| U5 | 重建"根总量"是否要 bump `ReviewPlannerV2.VERSION` | 主会话 | 只要**不改变默认输出**（`reserved=0`、`growthCeiling=null` 时逐位不变）就不必 bump；一旦把门面结果并入默认候选集，必须 bump（core/domain 热区，批次 40 §5.5 已登记） |

---

## 10. 来源清单

1. Anki 手册（Deck Options）：https://docs.ankiweb.net/deck-options.html
   - 源文件 `docs-site/manual/deck-options.mdx`（HEAD `136bd42bfb`）：
     - `:83-102` New Cards/Day（含"漏一天不补"、"子牌组限制只管该子牌组、选中牌组管总量"）
     - `:104-118` Maximum Reviews/Day（含 interday learning 计入 review 数）
     - `:119-127` Per-Deck Daily Limits（Preset / This deck / Today only）
     - `:129-135` New Cards Ignore Review Limit（积压时停新卡的建议）
     - `:137-143` Limits Start From Top（默认关）
     - `:378-384` Easy Days（句子在 `:382`）（"all days Reduced = same workload"）
     - `:409`、`:417-437` Desired Retention（默认 90%，>97% 会压垮；**同一 preset 内可给不同牌组设不同 r\***）
     - `:527-545` The Simulator（按 reviews/day 或 minutes/day 估负载，可设 new/day 与 max reviews/day）
     - `:500-514` Reschedule Cards on Change（默认不重排 → 改 r\* 当天无变化）
     - `:516-525` CMRR：**标注 Removed Feature（标题写 <25.07，正文 `:519-521` 写明 25.07 移除）**
2. `ankitects/anki`（AGPL-3.0-or-later，HEAD `136bd42bfb`，2026-10-29）：
   - `rslib/src/decks/limits.rs:22-46`（今日覆盖优先）、`:91-118`（剩余额度公式）、`:125-149`（`cap_to`/`decrement`/`cap_new_to_review`）、`:156-166`（默认 9999）、`:231-330`（`LimitTreeMap::build` + 子节点 `cap_to(父)`）、`:358-394`（`root_limit_reached`/`limit_reached`/沿祖先递减/封顶子孙）
   - `rslib/src/decks/counts.rs:22-28`（`new_rev_counts`：跨日归零）
   - `rslib/src/scheduler/queue/builder/gathering.rs:14-20,35-60,63-110`（取用顺序；每取一张递减本牌组+祖先）
   - `rslib/src/scheduler/queue/builder/mod.rs:130-147`（`apply_all_parent_limits`）、`:189-228`（`build` 输出 counts）
   - `proto/anki/decks.proto:73-87`（`DayLimit`、`review_limit`/`new_limit`、`*_limit_today`、`desired_retention`）
   - `rslib/src/scheduler/answering/mod.rs:484,512-515`（单牌组 r\* 进 `fsrs.next_states`）
   - `rslib/src/scheduler/states/load_balancer.rs:21,29-35,197-210,284-300,419-431`（负载均衡权重与 Easy Days 修饰）
   - `rslib/src/deckconfig/mod.rs:44-46,80,229-241`（`new_per_day`/`reviews_per_day`/`desired_retention`/`new_per_day_minimum` 仅校验）
3. `open-spaced-repetition/fsrs-rs`（BSD-3-Clause，HEAD `3be7d2af54`，2026-10-28）：`src/simulation.rs:573-585`（CMRR 目标 `total_cost/total_memorized`）、`:586-620`（`SimulatorConfig` 与默认值：`max_cost_perday=1800`、`learn_limit`/`review_limit`/`new_cards_ignore_review_limit`）、`:1983-2011`（超限推到次日并累加 due 计数）、`:2270-2296`（`optimal_retention` 仍在库内）
4. 本仓实现（本轮实读）：
   - `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/ReviewCapacityScheduler.kt:39-129,144-191`
   - `.../ExamQuotaPlanner.kt:29-95,101-147`
   - `.../MissedDayRebalancePolicy.kt:25-99,107-131`
   - `.../ReviewCapacityPolicy.kt:105-112,122-131,148-152,163-194,200-244`
   - `.../ReviewCapacityPreviewReader.kt:86-135,150-178`
   - `.../StudyReviewPlannerService.kt:154-181`（`examCapacityBudgetSeconds` +50%）、`:343-401`（`reviewCapacityPreview`；`:366-382` 传已爬坡预算）
   - `core/data/src/test/.../study/ReviewCapacitySchedulerTest.kt:59-128`
5. 批次 40 R3 `docs/research/2026-11-02-fsrs-capacity.md`（FSRS 三参数/`r*_exam`/1.5× 的裁定输入；本轮更正其 CMRR 一条）
6. 批次 40 记录 `docs/research/2026-11-02-batch-40-record.md` §8 发现 15（`ReviewCapacityScheduler` 多科过载）——**本轮复核：该状态在 `ReviewCapacityScheduler.kt:66-93` 已被修掉，剩余缺口见 §2.3**

---

## 11. 复现命令（本轮实际执行）

① 生产调用点（确认"总量只在门面内部"）：
```bash
cd "D:/smart mistake book"
grep -rn "ReviewCapacityScheduler\|ReviewCapacityPreviewReader" --include=*.kt . | grep -v "/test/"
# → 生产侧只有 StudyReviewPlannerService.kt:372 一处构造 Reader；createReviewPlan 不调用门面
```

② 分配反例与爬坡/偿还算术（本会话计算，§4.3 / §5.3 / C9-②）：
```bash
python -I -c "
import math
def alloc(rem,w):
    tw=sum(w); fl=[math.floor(rem*x/tw) for x in w]
    fr=[rem*x/tw-f for x,f in zip(w,fl)]; left=rem-sum(fl)
    out=fl[:]
    for i in sorted(range(len(w)),key=lambda i:(-fr[i],i)):
        if left>0: out[i]+=1; left-=1
    return out
print('allocate(6,[6,5]) =',alloc(6,[6,5]))
print('allocate(6,[6,5,5]) =',alloc(6,[6,5,5]))
base=1200; grown=base+int(base*0.5*1.0); print(base,grown,int(grown*1.5),int(grown*1.5)/base)
for arr in [1.0,1.2,1.5,1.6]:
    b,d=14.0,0
    while b>1e-9 and d<365: b=max(0.0,b-(1.5-arr)); d+=1
    print(arr, d if b<=1e-9 else 'never')
"
# 输出：allocate(6,[6,5]) = [3, 3]；allocate(6,[6,5,5]) = [2, 2, 2]
#       1200 1800 2700 2.25
#       1.0 28；1.2 47；1.5 never；1.6 never
```

③ 落点目录的工作树状态（确认非热区、无本线改动）：
```bash
git status --porcelain -- core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/ \
                          core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/study/
# → 仅 " M .../TutorFigureProposalExecutionTest.kt"（他线），本线目标文件均干净
```

④ 外部源码取证（Anki / fsrs-rs）：
```bash
gh api repos/ankitects/anki/contents/rslib/src/decks/limits.rs --jq '.content' | base64 -d | sed -n '91,118p'
gh api repos/ankitects/anki/contents/rslib/src/scheduler/queue/builder/gathering.rs --jq '.content' | base64 -d | sed -n '35,60p'
gh api repos/ankitects/anki/contents/docs-site/manual/deck-options.mdx --jq '.content' | base64 -d | sed -n '83,145p'
gh api repos/open-spaced-repetition/fsrs-rs/contents/src/simulation.rs --jq '.content' | base64 -d | sed -n '1983,2011p'
gh api "search/code?q=repo:ankitects/anki+deadline" --jq '.items[].path'   # → 仅 qt/tests/qwebengine_csp_smoke.py
```

---

## 12. 未验证 / 边界

- **未运行 Gradle**：本文件所有"当前不成立/成立"的判断来自**读码 + 读现有测试源码**，没有执行任何用例。T1–T17 中标注"有"的断言，其"通过"状态**本轮未验证**（`UNVERIFIED`）。
- **`gh api` 代码搜索的覆盖**：`search/code` 只索引默认分支，结论"`new_per_day_minimum` 无调度消费"仅在该范围内成立。
- **2.25× 的可达性**：需要"考试在窗 + 该科缺口为 0 + 有积压 + 再平衡开启"同时成立（读码推得，未构造运行用例）。
- **Anki 版本漂移**：本轮读的是 HEAD `136bd42bfb` 与 release `26.09.3`；手册 CMRR 一节已标 25.07 移除，若后续版本恢复，需重核 §1.2。
- **`p50DurationSeconds` 全科同值**：这是现有实现的事实，不是本文档引入；T4 只是把它的后果（张数≠时间）变成可测判据，并未提出新的估时模型。
