# 批次 40 · WS3：F8 / F9 / F18 预算裁定（主会话裁定落文）

> 批次 40 计划 §1 item 17 的落文。裁定来源：主会话 2026-11-02 的 5b 口径
> "按 FSRS 目标可提取度 90% + 考试余量，数值写死并给依据"。
> 研究输入：`docs/research/2026-11-02-fsrs-capacity.md`（R3，2026-11-02）。
> 本文不是外部分析，是**本仓的裁定记录**；所有数值已写进
> `core/data/.../study/ReviewCapacityPolicy.kt` 的常量，代码以本文为准。
> 本会话未运行 Gradle（纪律禁），因此本文不声称任何测试通过。

---

## 0. 一句话裁定

**F8 = 20 min 默认、10–60 min / 5 min 步进；F9 = 预算最多 1.5×、只拉已用掉 ≥85% 间隔的题、
默认关闭；F18 = 按日权重 0/1/2、默认关闭、只重排不改总量；考试余量 = 考前 14 天把目标
保留率从 90% 线性抬到 97%，预算最多 +50%。**

这五个数都不是外部标准；它们是本仓在"FSRS 90% 默认 + 考试窗口"上的**起步裁定**，
与 `EXAM_CAPACITY_RAMP_MAX_EXTRA_FRACTION` 的 `PENDING_CALIBRATION` 同级。

---

## 1. 依据：FSRS 目标可提取度 90%

| 事实 | 来源 | 本仓落点 |
|---|---|---|
| 默认目标保留率 0.90 | Anki 手册 "The default is 90%, which offers a good balance of retention and workload"；py-fsrs `desired_retention=0.9`；fsrs4anki 教程 | `ReviewCapacityPolicy.BASE_TARGET_RETENTION = 0.90`；仓内既有 `ForgettingCurve.DEFAULT_STABILITY_RETENTION = 0.9`（`core/domain/.../ForgettingCurve.kt:99`） |
| 可设区间 0.70–0.97 | fsrs4anki 教程；本仓 `MemoryUpdateModel.kt:68`、`StudyExperienceRepository.kt:737` 两处 `require(0.7..0.97)` | 考试目标上限 `MAX_EXAM_TARGET_RETENTION = 0.97` |
| 90% 以上负载加速上升 | Anki 手册（定性）；R3 §2.5 长程模拟（定量） | 预算与 r\* 必须一起呈现（见 §5） |

R3 §2.5 的倍率表（本会话计算，非 Kotlin 运行）给出：相对 r\*=0.90，长期每日复习量在
r\*=0.95 约 **1.43–1.72×**、r\*=0.97 约 **2.11–2.43×**；朴素间隔比（2.48×/4.49×）高估。
因此本仓**不用朴素间隔比做预算**；预算只按"预算 × 倍率"的显式上限增长。

---

## 2. F8：每日复习时间预算

### 2.1 裁定值（写死）

| 参数 | 值 | 代码常量 |
|---|---|---|
| 默认预算 | **20 min（1200 s）** | `DEFAULT_DAILY_BUDGET_MINUTES = 20` |
| 用户可调下限 | **10 min（600 s）** | `MIN_DAILY_BUDGET_MINUTES = 10` |
| 用户可调上限 | **60 min（3600 s）** | `MAX_DAILY_BUDGET_MINUTES = 60` |
| 档位步进 | **5 min** | `BUDGET_STEP_MINUTES = 5` |
| 持久化键 | `review_time_budget_minutes`（Int，10..60，默认 20） | 键名约定；读取失败回默认，不抛给 UI |

### 2.2 依据

- **默认 20 min**：现网 `RoomBackedStudyExperienceRepository` 的构造默认就是
  `DEFAULT_REVIEW_TIME_BUDGET_SECONDS = 20 * 60`（`core/data/.../RoomBackedStudyExperienceRepository.kt:1113`），
  保留它 = 默认计划逐位不变，风险最低。
- **下限 10 min**：R3 容量换算（`LogDurationModel` 三档 90/180/300 s）下，中档仍有 3 题；
  少于 3 题无法同时覆盖"到期复习 + 新引入"两类。
- **上限 60 min**：中档 20 题；再高会把"今日欠账"伪装成"今天能做完"，诚实预览更难成立。
- **30 min 锚点**：F17 全库扫描 10 个知识点 × 180 s = 1800 s；低于 30 min 时名额上限实际由
  时间而非名额封顶（设置页文案必须说清，否则用户以为"每天能捞 10 个"）。
- **5 min 步进**：10–60 共 11 档，足够表达"少/中/多"三档意图，避免连续滑块制造无意义的精度。

### 2.3 状态与边界

- **持久化数据侧**：`DataStoreSchedulingSettingsStore` 目前只有
  `desired_retention` / `exam_calendar` / `optimized_fsrs_parameters` 三个键
  （`core/data/.../settings/DataStoreSchedulingSettingsStore.kt:108-115`），预算未持久化。
  新增键属 `core/data` 非热面，但会把设置值接到 `RoomBackedStudyExperienceRepository`
  的构造参数；设置屏在 `feature/review` / `app` 热区，**本批只落键名与校验规则，接线登记阻塞**
  （R3 §5.5）。
- **预算与 r\* 必须一起呈现**：改 r\* 不改预算 = 静默改负载。示例文案：
  "当前 20 min @ 90%；若改到 95%，同样牌组约需 29 min。"（R3 §6.1）
- **档位校验 fail-closed**：`ReviewCapacityPolicy.secondsForBudgetMinutes` 对越界与
  非 5 min 整档值抛 `IllegalArgumentException`；读取处回默认，不把异常抛给 UI。

---

## 3. F9：休假/积压追赶

### 3.1 裁定值（写死）

| 参数 | 值 | 代码常量 |
|---|---|---|
| 每日预算成长上限 | **1.5×**（20 → 30 min） | `MAX_DAILY_GROWTH = 1.5` |
| 可拉题门槛 | 已用掉 **≥85%** 原间隔（相对偏离 ≤0.15） | `REBALANCE_MIN_ELAPSED_FRACTION = 0.85` |
| 默认开关 | **关闭**（关闭时逐位不变） | 调用方 `rebalanceEnabled = false` |

### 3.2 语义

- 每天重算：`b = 积压张数 / 正常日容量`，`required = 1 + b / 剩余学习日`，
  `allowed = min(required, 1.5)`。分子每天变小、分母每天变小，漏做自动进入下一天；
  不需要额外持久账本（与 Deadline2 的 `ceil(remaining/days)` 同构，只借算法、不复制 GPL 代码）。
- **上限作用在预算上**：20 min × 1.5 = 30 min；不是"预算不动、硬塞 1.5 倍张数"。
  这与 `examCapacityBudgetSeconds` 的既有做法同构（`StudyReviewPlannerService.kt:153-179`）。
- **超过 1.5× 不抬预算，改为延长 horizon**：`D_need = ceil(b / (1.5 − 1)) = 2b`
  （`ReviewCapacityPolicy.daysToClear`）。例如积压 5 个正常日 → 10 天清账，而不是
  "休假 7 天、回来每天 3 倍"的劝退曲线。
- **只拉接近到期的题**：`elapsed / interval ≥ 0.85`。刚复习过的强题不提前，
  避免无谓的稳定性损失（FSRS 稳定性增益随 R 降低而增大）。
- **1.5 与考试爬坡同值**：`EXAM_CAPACITY_RAMP_MAX_EXTRA_FRACTION = 0.5`
  （`core/domain/.../AlgorithmConstants.kt:148`）就是 +50%；同一个"每天最多多干 50%"的
  承诺不应在考试与休假两条路上给出两个数。
- **不可兑现要如实报**：`RebalancePlan.daysNeeded` 与 `ineligibleCount` 都是读数；
  超窗口不静默降级。

---

## 4. F18：周末/集中清空模式

### 4.1 裁定值（写死）

| 参数 | 值 | 代码常量 |
|---|---|---|
| 权重集合 | **0（休息）/ 1（平日）/ 2（周末/集中日）** | `DAY_WEIGHT_REST/WEEKDAY/WEEKEND` |
| 默认开关 | **关闭** | 调用方不启用 |
| 开启时默认 | 平日 1 / 周末 2 | 裁定值 |
| 总量 | **守恒**：重排不改总题数 | `allocateByDayWeights` 最大余数法 |

### 4.2 语义与边界

- `quota_i` 按权重比例分配，最大余数法保证 `Σ quota_i = remaining`；全 0 权重抛错，
  fail-closed 而不是静默丢题。
- **容量校验必须显式**：`周末权重 × 单日容量` 不得超过单日预算；超过时设置页提示
  "降权重或抬预算"，代码不偷偷夹取。
- **与 F9 的叠加**：先算 F9 的每日总量，再按权重分配到具体日；F18 只重排、不改总量，
  避免两处各乘一次。
- **示例**：剩余 60 题、14 天、其中 4 个周末日 → 权重和 18；最大余数分配后
  平日约 3–4、周末约 6–7（R3 §6.3 算术）。

---

## 5. 考试余量（spec §2.17 的落地口径）

| 参数 | 值 | 代码 |
|---|---|---|
| 考试窗口 | **14 天** | `EXAM_WINDOW_DAYS = 14` |
| 考前目标保留率 | `r*_exam(d) = min(0.97, r* + (0.97 − r*)·(1 − d/14))`，`d ∈ [0,14]` | `ReviewCapacityPolicy.examTargetRetention` |
| 考试当天 | `d=0` 目标 0.97，但**不排当日配额**（避免除零与无意义加压） | `ExamQuotaPlanner` 窗口 `1..14` |
| 预算爬坡 | 窗口内线性 +50%（既有行为保留，不改公式） | `StudyReviewPlannerService.examCapacityBudgetSeconds` |
| 每日配额 | `ceil(缺口题数 / 剩余学习日)`，夹在单日容量内 | `ExamQuotaPlanner.plan` |
| 缺口定义 | `max(0, r*_exam − R(examDay)) > 0` | `ReviewCapacityPolicy.examDeficit` |
| 优先级 | 今日到期复习 > 考试缺口 > 新引入/积压 | `ReviewCapacityScheduler` KDoc |
| 同一道题 | 考试与积压只计一次（取考试通道） | `ReviewCapacityScheduler` 的 `deduplicatedCount` |

> 口径修正（R3 §3.2）：考试配额必须**按科目各自的最近考试日**计算。现有
> `daysUntilNearestExamOf` 取全科最小天数、`NewIntroductionPolicy` 的 catch-up 是
> 全科分子；新策略层按 `subject` 分组，避免"3 天后的数学考试把英语积压也拉高"。

---

## 6. 明确不做

| 不做 | 理由 |
|---|---|
| 运行时参数拟合 / ML 训练 | 优化器是离线可选面（`FsrsParameterOptimizer`），本批不接；默认 21 参数已 source-verified |
| 第二套遗忘曲线 / 间隔反函数 | 仓内 `FsrsScheduleMath` / `ForgettingCurve` 已是唯一数学；重复实现会与投影分叉 |
| fuzz / 随机抖动 | 仓内明确"projections must be deterministic"（`FsrsScheduleMath.kt:11-12`） |
| 改 `ReviewPlannerV2.VERSION` 或默认候选集 | core/domain 热区；默认激活登记阻塞（R3 §5.5） |
| 把数值说成外部标准 | Anki/Deadline2/Helper 只提供方向；档位是本仓内部裁定 |

---

## 7. 落地映射

| 项 | 产物 |
|---|---|
| item 18 容量爬坡模型 | `ReviewCapacityPolicy` + `ReviewCapacityFacts`（三参数：D/S/R 原样携带，不重算） |
| item 19 考试配额 | `ExamQuotaPlanner` + `ExamQuotaPlan` |
| item 20 缺勤再平衡 | `MissedDayRebalancePolicy` + `RebalancePlan` |
| item 21 调度只读接线 | `ReviewCapacityScheduler` + `ReviewCapacityPlan` |
| item 22 KD-68 数据侧 | `ReviewCapacityPreviewReader` + `ReviewCapacityPreview`；`StudyReviewPlannerService.reviewCapacityPreview` / `RoomBackedStudyExperienceRepository.reviewCapacityPreview` |
| 用例 | `ReviewCapacityPolicyTest` / `ExamQuotaPlannerTest` / `MissedDayRebalancePolicyTest` / `ReviewCapacitySchedulerTest` / `ReviewCapacityPreviewReaderTest` / `StudyReviewPlannerServiceCapacityPreviewTest` |

**阻塞（不伪造）**：把读数挂到 `StudyExperienceSnapshot`（core/domain 热区）与
`feature/review` 消费（feature 热区）仍需非热窗口；本批只落 `core/data` 生产入口与纯函数。

---

## 8. 校准状态

本文所有数值都是**起步值**，不是已标定值：

- 真实每题耗时分布未实测（`LogDurationModel` 三档是基线）；
- 用户真实每日可用时长无遥测；
- 漏做率/休假频率无本地账本；
- r\* 与真实保留率偏差需要 ≥ 一个优化周期的 `review_log`；
- 本机 FSRS 参数是群体中位数，未个体拟合。

→ 与 `EXAM_CAPACITY_RAMP_MAX_EXTRA_FRACTION` 的 `PENDING_CALIBRATION` 同级。

---

## 9. 来源

1. `docs/research/2026-11-02-fsrs-capacity.md`（R3，研究输入；本文的公式、倍率表与
   "考试配额/再平衡不是 FSRS 本身"的更正均来自该文）。
2. Anki Manual — Deck Options / FSRS：https://docs.ankiweb.net/deck-options.html
3. py-fsrs v6.3.2（MIT）：https://github.com/open-spaced-repetition/py-fsrs
4. fsrs-rs v6.6.2（BSD-3-Clause）：https://github.com/open-spaced-repetition/fsrs-rs
5. fsrs4anki 教程（MIT）：https://github.com/open-spaced-repetition/fsrs4anki
6. fsrs4anki-helper（MIT）：https://github.com/open-spaced-repetition/fsrs4anki-helper
   （0.13/0.15 相对偏离阈值；本仓统一取更保守的 0.15）
7. Deadline2（GPL-3.0）：https://github.com/BSCrumpton/Deadline2
   （只借 `ceil(remaining/days)` 算法，不复制代码）
8. Deckline（无 LICENSE 文件）：https://github.com/Kwinties/Deckline
   （只作产品语义参照，不复制任何内容）
9. 本仓规范：`docs/specs/mastery-scheduling-spec.md:138-139,313`；
   `docs/requirements-fused-final-2026-10-11.md:115,117,119`。
