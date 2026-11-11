# 批次 49 · 复习标定报告（G6 幅度 / 权重坐标 / 掌握阈值读数）

| 字段 | 值 |
|---|---|
| 角色 | 标定报告员（只写本报告与同名 JSON，**不改**任何源码 / 常量 / 测试 / 金样 / 台账） |
| 写于 | 2026-11-08 |
| 预注册 | `docs/research/2026-11-08-review-calibration-prereg.md`，sha256 `042776e829fe943a736007074ef77ac0e191f7bbeca0169b58d3d1a3be96b843` |
| 方法论 | `docs/research/2026-11-08-calibration-method.md`（R1） |
| 实验代码基线 | HEAD `ba7b5624ea0c2bc3f09dcee247f5852e4f428122` + §1.2 逐文件 sha256 |
| 留出集执行 id | `batch49-holdout-20261107T1455Z`（2026-11-07T14:42:43Z → 14:53:37Z，653 s） |
| 结论性质 | **全部 model-based**（30 KC / 300 题 / 120 天的合成世界）。真库样本侧 **BLOCKED**（本批零样本）。A 线 FSRS 拟合 **NOT RUN** |
| 最终裁决 | **G6 维持 0.5；17 个权重臂全部维持现值；掌握阈值只读不改；`ReviewPlannerV2.VERSION` 不 bump；三组金样不重生成** |

> **本会话实跑范围（必须与"实验执行"分开读）**：本报告会话**没有**重跑模拟、没有跑 Gradle、没有碰设备、
> 没有读真库。实际执行的只有下面四类（命令与退出码见 §11）：
> ① 用 `g6-runs.csv` / `weight-runs.csv` 的原始行**独立复算**全部 21 个臂的裁决，并与执行者 JSON 交叉核对；
> ② 重算 11 个文件的 sha256（prereg + 9 个 manifest 钉表文件 + driver）与 HEAD；
> ③ 从 `thresholds.csv` / `prefix-identity.json` / 各 CSV 汇总机制读数；
> ④ 由证据 JSON 生成十进制臂表与本报告同名 JSON（纯格式转换，不重新解释）。
> 所有"实验已执行"的事实来自执行者落盘的证据文件，逐条标了出处；所有"我核对过"的结论来自本会话命令。

---

## 0. 一屏结论

| 线 | 裁决 | 依据（一句） | 采纳 |
|---|---|---|---|
| **G6 幅度 `f`** | **无可采纳臂 → 维持现值 0.5** | 4 个候选无一过门：2 个 `UNDERPOWERED`（等时合格 world 仅 6/10）、2 个效应量或符号不足、**4/4 全部护栏不过** | 无；`AlgorithmConstants.kt:148` 不动 |
| **权重（5 坐标 / 17 臂）** | **全部维持现值** | 0/17 过门；最好者 `W/due/x1.25` 中位 Δ = +0.0833（2.5 KC）但符号只 6/8、护栏不过、且落在网格端点 | 无；`ReviewScoring` 与 `DEFAULT_KC_DROP_WEIGHT=2.5` 不动 |
| **掌握阈值** | **只读，不产生提案** | 三组读数已出（§3.3）；ask 硬约束：21 天 / 0.9 不改 | 无 |
| **版本与金样** | **不 bump / 不重生成** | 未采纳任何非默认值，预注册 §11 的一次性 bundle 未触发 | `VERSION = "review-planner-v4"`（`ReviewPlannerV2.kt:1097`） |
| **A 线（FSRS 拟合 / 上线门）** | **BLOCKED（未运行）** | 执行者 manifest 明写 `A-line FSRS fitting (§7) not run`；`evaluateSchedulingModels` 本批无断言 | 无数字可报 |

**机制层最重要的一条**（解释"为什么没有差异"，且**不得**读成"权重无作用"）：
planner 的每日选题步数被 `MAX_BEAM_STEPS = 20` 封顶（`ReviewPlannerV2.kt:192` `minOf(MAX_BEAM_STEPS, timeBudgetSeconds / minDuration)`，常量在 `:1133`），
而实验里**每个臂、每个 world 的 `max_daily_items` 中位数都恰好是 20**，典型 world 有 22–31 天顶在 20 题上限（§6.1）。
G6 的扩容增量因此**买不到更多选题步数**，只能改变"选哪 20 题"——这是本世界形状下 `f` 效应被压平的机械原因。

---

## 1. 基线与可复现性

### 1.1 执行事实（来自 `calibration/run-manifest.json`）

| 项 | 值 |
|---|---|
| 物理 run 数 | 3 120 |
| 逻辑 run 数（含缓存复用） | 3 334 |
| 预注册 §8.1 预算 | 69–74 / world × 20 world = 1 380–1 480 |
| **对账** | **超预算约 2.1–2.3 倍**。原因不是事故：权重臂按预注册 §5.3 要求"时长不等时套 ρ 网格"（11 个 ρ），而 §8.1 的 run 矩阵把权重臂按每 world 1 次计。**登记为矩阵低估，不是二次裁决**（§10.1） |
| 等时容差 | 1.5%（`equal_time_tolerance = 0x1.eb851eb851eb8p-7`） |
| Brier 容差 | +0.005（`brier_delta_tolerance = 0x1.47ae147ae147bp-8`） |
| NO_EQUAL_TIME 单元格 | G6 22/80（dev 10 + holdout 12）；权重 105/340（dev 59 + holdout 46） |

### 1.2 被钉文件的哈希（本会话 `sha256sum` 实跑，与 manifest 逐位一致）

```text
（公共路径前缀已省略；逐条完整命令见 §11）
$ sha256sum <11 个文件，逐个执行>    # prereg + 9 个 manifest 钉表文件 + driver
042776e829fe943a…96b843  prereg
ed6ae4e50147fc0f…c9651c  ReviewPlannerV2.kt          （预注册 §1.1 基线 ee89aba3…0ecf → 落 seam 后变）
e695418f5a24f56a…ccfd84  AlgorithmConstants.kt       （与预注册 §1.1 **相同** = 常量零变更）
62143174c3a4086b…0320f2  ReviewPlanner.kt            （基线 e5d66b5b…bfbfa → 删旧 KC_DROP_WEIGHT 后变）
3cb0af7c7c199a98…98599d  ClearlyMasteredForSkipPolicy.kt
e0f3f26aaac5dd15…780f7338  SchedulingEvaluation.kt
d72a2ed9aa8bc91f…342560  ReviewPlannerScaleBenchmarkTest.kt
2bd8c40bb6352e5d…f4d6948d  StudyReviewPlannerService.kt
62d1eeac482ab165…63e65ef  SimulatedLearner.kt         （基线 d170338b…f655cc2dc → 加 scoringWeights 默认参数后变）
94a27fae700e681f…d99212e  SimulatedLearnerTest.kt
6f2ec17aceb06b13…c8d74a   ReviewCalibrationExperimentTest.kt（driver 本体，报告时点）
$ git rev-parse HEAD
ba7b5624ea0c2bc3f09dcee247f5852e4f428122
```

> **报告后漂移（2026-11-08 修复环登记，详见 §10.3）**：上表是报告时点
> （`2026-11-07T14:53Z`）的历史快照，不是最终树。最终树实测：
> `StudyReviewPlannerService.kt=1cedc668…cc1bbf`（P2-14 在 `:944` 加
> `reasons = scored.reasons`）、`ReviewCalibrationExperimentTest.kt=d9e649a1…fdb5c6`
> （driver 在门禁/证据之后被改）。G6/权重/金样只依赖 `ReviewPlannerV2.kt`
> （`ed6ae4e5…` 仍匹配），裁决本身不受这两个漂移影响；“实验代码基线可逐位复现”
> 只对**报告时点**成立。

- **预注册未被篡改**（机检 1 通过）：实测 sha256 等于 manifest 的 `prereg_sha256`。
- **常量未被顺手改**（机检 12 通过）：`AlgorithmConstants.kt` 哈希与预注册 §1.1 的**实验前基线完全相同**，
  实测 `MIN_STABILITY_DAYS = 21.0`（`:63`）、`MIN_CURRENT_RETENTION = 0.9`（`:66`）、
  `EXAM_CAPACITY_RAMP_MAX_EXTRA_FRACTION = 0.5`（`:148`）。
- 三个 `.kt` 的哈希变化在预注册 §1.1 中是**预期内**的（seam 落地），并在 manifest `known_deviations` 登记。
  机检 2 的字面要求"报告 `file_sha256` == §1.1"因此**不成立**；本报告改为对**实验后**哈希对账（全等），
  这一偏离登记在 §10.2。

### 1.3 确定性与前缀同一性

| 检查 | 结果 | 出处 |
|---|---|---|
| 同进程重跑（3 组） | 3/3 `digest` 逐位相同 | `calibration/determinism.json` `in_process_all_equal: true` |
| 跨进程复跑 | 本会话比对：G6 共同键 7/7、权重共同键 28/28 `digest` 逐位相同 | `cross-process-rerun/`（101 + 901 两个 world） |
| 前缀同一性（30/60/90 ⊂ 120） | **120/120 检查通过**（20 world × 2 真值臂 × 3 horizon） | `calibration/prefix-identity.json`，本会话 `supplementary_readings.py` 汇总 `both_equal=120 all_equal_flag=True` |

前缀同一性通过 = 预注册 §6.2 的三个读数日**可加**；§6.3 的作废条件未触发。

---

## 2. 选择规则（冻结自预注册，不得事后改）

- 主指标 `Y` = 5 场考试的**考前窗口（14 天含当天）内考试科 KC 覆盖率**的算术平均。
- 分辨率：1 KC = **3.33pp**；最小效应门 = 1 KC。
- 通过条件（G6）：`median Δ ≥ 1/30` ∧ `Δ>0 的 world ≥ 8/10` ∧ G1–G4 全过 ∧ **非网格端点** ∧ `n ≥ 8`。
- 通过条件（权重）：同上，且本批**至多采纳 1 个坐标**。
- 护栏：G1 日时长不超预算；G2 非考试科不挨饿（按科 ≥ 0.9× 基线近期占比）；G3 `kcGapP95 ≤ max(21, 基线)`；G4 `median(Brier Δ) ≤ +0.005`。
- 端点：due/weak/diversity `{0.75, 1.25}`；exam `{0.875, 1.25}`；kc-drop `{0.75}`；G6 `{0.50}`。

**8/10 不是显著性检验**：H0 = 硬币时 `P(X≥8) = 56/1024 = 0.0547`。本报告的"符号一致"读数一律按此口径陈述，不得写成"显著"。

---

## 3. 留出集结果

### 3.1 G6 幅度 `f`（等时臂，holdout 10 world）

| 臂 | f | n（等时合格） | 中位 ΔY | = 几个 KC | Δ>0 | dev 中位（符号） | G1 | G2 | G3 | G4 | 过门 | 拒绝原因 | bootstrap 95% |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| G6/A1 | 0.15 | 6 | −0.0333 | −1.0 | 3 | +0.0000 (3/7) | ✅ | ❌ | ❌ | ✅ | ❌ | `UNDERPOWERED`（n=6<8） | [−0.1167, +0.1167] |
| G6/A2 | 0.25 | 6 | +0.0500 | +1.5 | 4 | +0.0167 (4/8) | ✅ | ❌ | ❌ | ✅ | ❌ | `UNDERPOWERED`（n=6<8） | [−0.0667, +0.1500] |
| G6/A3 | 0.35 | 8 | +0.0167 | +0.5 | 4 | +0.0333 (5/8) | ✅ | ❌ | ❌ | ✅ | ❌ | `median_delta_below_one_kc` | [−0.0667, +0.0667] |
| G6/A4 | 0.50 | 8 | +0.0333 | +1.0 | 5 | +0.0333 (5/7) | ✅ | ❌ | ❌ | ✅ | ❌ | `sign_consistency_below_8_of_10`（5/8）；且**端点不采纳** | [−0.0333, +0.1333] |

逐 world 配对差（`Δ = Y_arm − Y_A1`；`NO_EQUAL_TIME` 表示 11 个 ρ 里没有一档把总时长拉到基线 ±1.5% 内）：

| seed | f=0.15 | f=0.25 | f=0.35 | f=0.50 |
|---|---|---|---|---|
| 901 | −0.0667 (−2 KC) | −0.0667 (−2 KC) | −0.0667 (−2 KC) | NO_EQUAL_TIME |
| 902 | NO_EQUAL_TIME | NO_EQUAL_TIME | +0.0667 (+2 KC) | +0.1000 (+3 KC) |
| 903 | +0.1000 (+3 KC) | +0.2000 (+6 KC) | +0.0000 (0) | +0.2333 (+7 KC) |
| 904 | NO_EQUAL_TIME | NO_EQUAL_TIME | +0.0333 (+1 KC) | +0.0333 (+1 KC) |
| 905 | −0.1667 (−5 KC) | +0.0333 (+1 KC) | +0.0000 (0) | +0.0000 (0) |
| 906 | NO_EQUAL_TIME | NO_EQUAL_TIME | NO_EQUAL_TIME | −0.0333 (−1 KC) |
| 907 | +0.0000 (0) | +0.1000 (+3 KC) | +0.0333 (+1 KC) | +0.0333 (+1 KC) |
| 908 | NO_EQUAL_TIME | +0.0667 (+2 KC) | +0.0667 (+2 KC) | +0.1333 (+4 KC) |
| 909 | −0.0667 (−2 KC) | −0.0667 (−2 KC) | −0.0667 (−2 KC) | −0.0333 (−1 KC) |
| 910 | +0.1333 (+4 KC) | NO_EQUAL_TIME | NO_EQUAL_TIME | NO_EQUAL_TIME |

> 注：`907 @ f=0.15` 的全精度差是 `0x1.0p-53`（≈1.1e-16），在 6 位小数的 CSV 里显示为 0；
> 驱动器按严格 `Δ > 0` 把它计成正号（所以该臂正号是 3 而不是 2）。这是**浮点尘**，不是 1 个 KC 的收益（§4）。

**怎么读这张表（三条都必须一起说）**：

1. **方向是正的但不稳**：`f=0.35/0.50` 的中位差都在 +0.5–1.0 个 KC，可 bootstrap 95% 区间**都跨 0**（f=0.50 的下界 −0.0333）。
   同号要求还差 3 个 world（5/8 vs 需 8/10）。
2. **9 号 world 是反例**：`909` 在 4 个幅度上全是负的（−1 至 −2 KC），`905`、`901` 也偏负。这些是**结构性反例**，
   不是噪声可吸收的离群点——它们的护栏违反（§6.2）说明扩容把题从别的科目上抢走了。
3. **端点规则**把 `f=0.50` 与 `f=0` 都排除在采纳集之外：本网格**在结构上无法采纳现值**。
   所以本轮对 G6 能给出的最强结论只是"**0.15–0.35 三个候选都没过门 → 不动**"。

### 3.2 权重坐标（17 臂，holdout 10 world）

| 臂 | 坐标 | × | n | 中位 ΔY | = KC | Δ>0 | dev 中位（符号） | G2 | G3 | 过门 | 拒绝原因 | bootstrap 95% |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| W/due/x0.75 | due | 0.750 | 6 | +0.1000 | +3.0 | 4 | +0.1000 (4/5) | ❌ | ✅ | ❌ | `UNDERPOWERED` | [−0.1000, +0.2167] |
| W/due/x0.875 | due | 0.875 | 6 | +0.0833 | +2.5 | 5 | +0.1000 (4/6) | ❌ | ❌ | ❌ | `UNDERPOWERED` | [−0.0833, +0.1333] |
| W/due/x1.125 | due | 1.125 | 7 | +0.0667 | +2.0 | 6 | +0.0667 (4/7) | ❌ | ❌ | ❌ | `UNDERPOWERED` | [+0.0333, +0.1000] |
| W/due/x1.25 | due | 1.250 | 8 | +0.0833 | +2.5 | 6 | +0.0333 (5/8) | ❌ | ❌ | ❌ | `sign_consistency_below_8_of_10` + **端点** | [−0.0333, +0.1333] |
| W/weak/x0.75 | weak | 0.750 | 2 | +0.1000 | +3.0 | 1 | −0.0333 (0/1) | ❌ | ❌ | ❌ | `UNDERPOWERED`（n=2） | [−0.1000, +0.3000] |
| W/weak/x0.875 | weak | 0.875 | 6 | +0.0167 | +0.5 | 3 | +0.0667 (3/5) | ❌ | ❌ | ❌ | `UNDERPOWERED` | [−0.0833, +0.1167] |
| W/weak/x1.125 | weak | 1.125 | 8 | +0.0833 | +2.5 | 6 | +0.1333 (6/7) | ❌ | ❌ | ❌ | `sign_consistency_below_8_of_10` | [−0.0333, +0.1333] |
| W/weak/x1.25 | weak | 1.250 | 10 | +0.0833 | +2.5 | 7 | +0.0000 (4/9) | ❌ | ❌ | ❌ | `sign_consistency_below_8_of_10`（7/10）+ **端点** | [−0.0000, +0.1500] |
| W/exam/x0.875 | exam | 0.875 | 10 | +0.0000 | 0 | 3 | +0.0000 (1/10) | ✅ | ❌ | ❌ | `median_delta_below_one_kc` + **端点** | [+0.0000, +0.0333] |
| W/exam/x1.125 | exam | 1.125 | 10 | +0.0000 | 0 | 4 | +0.0000 (4/10) | ❌ | ❌ | ❌ | `median_delta_below_one_kc` | [+0.0000, +0.0333] |
| W/exam/x1.25 | exam | 1.250 | 10 | +0.0333 | +1.0 | 6 | +0.0000 (2/10) | ❌ | ❌ | ❌ | `median_delta_below_one_kc` + **端点** | [+0.0000, +0.0500] |
| W/kc-drop/x0.75 | kc-drop | 0.750 | 9 | +0.0667 | +2.0 | 6 | +0.0333 (2/4) | ❌ | ❌ | ❌ | `sign_consistency_below_8_of_10` + **端点** | [−0.1333, +0.1333] |
| W/kc-drop/x0.875 | kc-drop | 0.875 | 8 | +0.0667 | +2.0 | 5 | +0.0667 (3/6) | ❌ | ❌ | ❌ | `sign_consistency_below_8_of_10` | [+0.0000, +0.1667] |
| W/diversity/x0.75 | diversity | 0.750 | 8 | +0.0667 | +2.0 | 7 | +0.0000 (4/9) | ❌ | ❌ | ❌ | `sign_consistency_below_8_of_10`（7/8）+ **端点** | [+0.0000, +0.1333] |
| W/diversity/x0.875 | diversity | 0.875 | 5 | +0.1000 | +3.0 | 4 | +0.0167 (3/6) | ❌ | ✅ | ❌ | `UNDERPOWERED` | [−0.0333, +0.2667] |
| W/diversity/x1.125 | diversity | 1.125 | 7 | −0.0333 | −1.0 | 3 | +0.0167 (2/4) | ❌ | ❌ | ❌ | `UNDERPOWERED` | [−0.1667, +0.2000] |
| W/diversity/x1.25 | diversity | 1.250 | 4 | +0.0500 | +1.5 | 2 | +0.1333 (4/4) | ✅ | ❌ | ❌ | `UNDERPOWERED`（n=4）+ **端点** | [−0.1000, +0.2000] |

**G1、G4 在所有臂上都过**（`overrun_strict = overrun_tolerance = 0` 逐 world；`|median Brier Δ| ≤ 0.0076`，最差为改善方向）。
所以本轮**没有一条臂是被"超预算"或"校准变坏"拒掉的**——这是 G2/G3 的独占性作用（§6.2）。

### 3.3 掌握阈值读数（只测不改；n=20 = dev 10 + holdout 10 合并）

| 真值 | 策略 (S 天, R) | day 30 | day 60 | day 90 | day 120 | 误判率中位 | 结构零 |
|---|---|---|---|---|---|---|---|
| W-slip | **T0 (21, 0.90) 现值** | 1 | 2.5 | 4 | 6 | 0 | 是（构造） |
| W-slip | T1 (14, 0.90) | 2 | 5 | 8 | 9 | 0 | 是（构造） |
| W-slip | T2 (21, 0.85) | 1 | 2.5 | 5 | 7 | 0 | 是（构造） |
| W-decay | **T0 现值** | 1 | 2 | 5 | 6 | 0 | 否 |
| W-decay | T1 (14, 0.90) | 2 | 3.5 | 7 | 9 | 0 | 否 |
| W-decay | T2 (21, 0.85) | 1 | 2 | 5 | 6 | 0 | 否 |

（单位：30 个 KC 中被判"已掌握"的个数中位；`thresholds.csv` 为逐 world 原始行，`threshold-readings.json` 为合并聚合。）

**保持率（锚点 day 30，holdout）**：分母 = 第 30 天末掌握集，**中位只有 1–2 个 KC**（W-slip T0 有 3/10 world 掌握集为空 → 记 `null`，不记 0）。

| 真值 | 策略 | 30→60 | 30→90 | 30→120 | 有效 world 数 |
|---|---|---|---|---|---|
| W-slip | T0 | 0.0 | 0.0 | 0.5 | 7 / 7 / 7 |
| W-slip | T1 | 0.5 | 0.333 | 0.4 | 9 / 9 / 9 |
| W-slip | T2 | 0.5 | 0.5 | 1.0 | 7 / 7 / 7 |
| W-decay | T0 | 1.0 | 1.0 | 1.0 | 6 / 6 / 6 |
| W-decay | T1 | 0.5 | 0.5 | 0.5 | 10 / 10 / 10 |
| W-decay | T2 | 1.0 | 1.0 | 1.0 | 6 / 6 / 6 |

**读数边界（必须与数字一起引用）**：

- **"更宽松的阈值覆盖更多"是方向性观察，不是建议**：T1（14 天）在 day 120 的中位覆盖是 9/30，T0 是 6/30、T2 是 6–7/30（pooled）；
  但 T1 的 30→120 保持率在 W-slip 只有 0.4（T0 0.5、T2 1.0）。
  这批读数的**分母只有 1–2 个 KC**，单独拿保持率做判断会被单点翻转。
- **误判率中位恒为 0**：W-slip 是**构造性为零**（真值衰减 = 产品衰减，标签不可能说谎）；W-decay 的 0 是**实测**——
  本世界形状下 `meetsMemoryCriterion` 判出的掌握集里没有 KC 的真值召回跌破各自门槛。**这不等于"阈值安全"**，
  只等于"这个 30 KC 世界里没抓到反例"（§8）。
- **不生成任何阈值变更提案**（ask 硬约束）。任何变更需用户单独裁定 + 新预注册 + `algorithm-version-ledger.md` + `SKIP_POLICY` 版本纪律。

### 3.4 各坐标的建议值（本批输出）

| 坐标 | 现值 | 单一来源 | 建议值 | 理由 |
|---|---|---|---|---|
| `due` | 5.0 | `AlgorithmConstants.kt:114` | **5.0（不变）** | 最好臂 `x1.25` 中位 +0.0833 但符号 6/8、护栏不过、端点在网格上 |
| `weakness` | 3.0 | `AlgorithmConstants.kt:115` | **3.0（不变）** | `x1.25` 7/10 最接近，仍差 1 个 world 且端点；`x0.75` 只有 2 个等时 world |
| `exam` | 2.0 | `AlgorithmConstants.kt:119` | **2.0（不变）** | 三个臂中位 Δ 都 ≤ 1 KC；`x1.25` dev 只有 2/10 同号，**方向不一致** |
| `kc-drop` | 2.5 | `ReviewPlannerV2.kt:53` `DEFAULT_KC_DROP_WEIGHT`（**单源**；旧 `ReviewPlanner.kt:22` 已删，仅剩注释） | **2.5（不变）** | `x0.75` 6/9 同号 + 端点；`x0.875` 5/8 |
| `diversity` | 0.3 / 0.2（family / source），上限 1.5 | `AlgorithmConstants.kt:123-125` | **0.3 / 0.2 / 1.5（不变）** | `x0.75` 7/8 同号（最接近）但端点、且 CI 下界 0；`x1.125` 中位为负 |
| （`avoidance`） | 1.0 | `AlgorithmConstants.kt:118` | **1.0（不变）** | **不可测**：模拟里 `ReviewCandidate.avoidance` 恒 false，本轮未扫（预注册 §2.4 / §10 #10） |

> `KC_DROP_WEIGHT` 的"单源"是 P1-9 的落地结果，本会话用 `grep -rn "KC_DROP_WEIGHT" core/ --include=*.kt` 复核：
> 定义只剩 `ReviewPlannerV2.kt:53` 一处常量（其余为 KDoc 提及与测试引用），消费点经 `ReviewScoringWeights.kcDropWeight`。

---

## 4. 不确定性

| 来源 | 量级 | 处置 |
|---|---|---|
| **世界形状分辨率** | 1 KC = 3.33pp | 小于 1 KC 的差异**不可分辨**；"无差异"≠"无作用" |
| **world 数** | 10 配对 world | cluster bootstrap（重抽 world、不重抽复习行，10 000 次、seed 20261108）；区间已在上表给出 |
| **符号一致性门** | 8/10 ⇒ p = 0.0547（H0 硬币） | **不是** 5% 检验；报告不写"显著" |
| **等时化失败** | G6 12/40、权重 46/170 holdout 单元格 `NO_EQUAL_TIME` | 这些单元格**剔除**、不插值；导致 10 个臂 `n<8` → `UNDERPOWERED` |
| **等时误差** | 逐臂中位 0.21%–1.23%（全局 0.0015%–1.49%），全部 < 1.5% 容差 | 剩余不平衡最多 ~1.5% 时长，可能贡献 ~1 个 KC 量级的方向噪声 |
| **真值错配强度** | 只有 `W-slip`（5% 粗心）与 `W-decay`（w20×1.3） | 未实现 `W-transfer` / 科目级 slip；结论强度低于 R1 设想 |
| **护栏可达性** | G2 在每个臂都至少 1 个 world 违反 | 门在**本轮世界形状下不可达**；见 §6.2，不得读成"效应不存在" |
| **CSV 十进制舍入** | 落盘 CSV 只有 6 位小数 ⇒ 分辨不了 `<1e-6` 的差 | 严格 `Δ>0` 会把 1e-16 的浮点尘记成正号；本批 2 个臂因此相差 1（f=0.15 3 vs 2、diversity×0.75 7 vs 6），均远低于 8，**不改变任何裁决**。下批预注册应给 `Δ>0` 加数值门槛（如 1e-9） |

---

## 5. 被拒配置原因（按拒绝机制归类）

| 拒绝机制 | 臂数（共 21） | 具体臂 |
|---|---|---|
| `UNDERPOWERED`（等时合格 world < 8） | **10** | G6/A1、G6/A2、W/due/x0.75、W/due/x0.875、W/due/x1.125、W/weak/x0.75、W/weak/x0.875、W/diversity/x0.875、W/diversity/x1.125、W/diversity/x1.25 |
| `median_delta_below_one_kc`（效应 < 1 KC） | **4** | G6/A3、W/exam/x0.875、W/exam/x1.125、W/exam/x1.25（`W/weak/x0.875` 的中位 +0.0167 也低于 1 KC，但其首个命中分支是 `UNDERPOWERED`） |
| `sign_consistency_below_8_of_10`（方向不稳） | **7** | G6/A4、W/due/x1.25、W/weak/x1.125、W/weak/x1.25、W/kc-drop/x0.75、W/kc-drop/x0.875、W/diversity/x0.75 |
| **网格端点不采纳**（R7，即使过门） | **9 权重臂 + G6/A4** | W/due/x0.75、W/due/x1.25、W/weak/x0.75、W/weak/x1.25、W/exam/x0.875、W/exam/x1.25、W/kc-drop/x0.75、W/diversity/x0.75、W/diversity/x1.25 |
| 护栏违反（G2/G3） | **全部 21 臂** | 逐臂明细见 §3.1/§3.2 的 G2/G3 列与 `decision-recompute.txt` 的 `G3 violations` 行 |

一条臂可同时命中多条（例如 `W/weak/x0.75`：n=2 + 端点 + G2/G3）。驱动器的 `reason` 字段只取**第一个**命中的分支，所以
`g6-decisions.json` 里 G6/A4 写的是 `sign_consistency_below_8_of_10`，而不是 `guardrail_violated`——**两者都为真**，报告同时列出。

**端点规则的实际后果**：本轮 17 个权重臂里有 **9** 个落在网格端点、G6 的 4 个候选里有 1 个（`f=0.50`）落在端点。
要在下一轮真正裁决 `due`（现值 5.0 恰在 `x1.0`，两侧端点是 0.75/1.25 的乘性坐标）或 `f`，
**必须扩网格 + 新预注册**（旧 holdout 永久退役）。

---

## 6. 机制解释（为什么没有可测差异）

### 6.1 每日选题步数被 `MAX_BEAM_STEPS = 20` 封顶

`ReviewPlannerV2.kt:192`：`val maxSteps = minOf(MAX_BEAM_STEPS, timeBudgetSeconds / minDuration)`；常量 `:1133` = 20。
预算 1200s、科时长画像 45–85s ⇒ `1200/45 ≈ 26` 步，被截到 **20**；扩容 50% 后 `1800/45 = 40`，**仍被截到 20**。

证据（本会话从 `g6-runs.csv` 汇总）：**每个臂、每一层的 `max_daily_items` 中位都恰好 = 20.0**；
典型 world 有 **22–31 天**顶着 20 题上限（`days_at_item_cap` 中位；f=0.50 holdout 为 25 天）。

**结论**：G6 的"扩容"在本世界形状下**不转化为更多选题步数**，只改变"同样的 20 题里选哪些"。
`f` 效应被机械压平，这是"测不出差异"的**测量范围**限制；**不得**据此断言"考前扩容对产品无效"。

### 6.2 G2/G3 在本世界形状下不可达（门本身的结构性问题）

- **G2（非考试科不挨饿）**：15/17 权重臂 + 4/4 G6 臂在至少一个 holdout world 把某科的近期覆盖率压到基线的 90% 以下。
  典型违规 world：`901/903/904/905/907/908/909/910`。
- **G3（KC 间隔）**：基线自身的 `kcGapP95` 在 **7/10** holdout world 就是 **90 天**（= 至少 2 个 KC 在 31–120 天内从未复习，最近秩定义下 30 个值的 P95 = 第 29 名）。
  也就是说基线本来就没有把 30 个 KC 都覆盖到；`max(21, 90) = 90` 让 G3 在这些 world 上极宽松，
  而在基线 `kcGapP95 ≤ 34` 的 `901/908` 上极紧——**同一护栏在不同 world 上严格度相差 3 倍**。

**这是本轮最重要的方法论发现**：`G2` 的"≥0.9× 基线近期占比"在 6 KC/科的离散世界里等价于"一个 KC 都不能少"，
而任何把时间往考试科移动的配置必然在别处少一个 KC。于是
**"提升考试科覆盖"与"非考试科不挨饿"在本世界形状下互相排斥，门在任何臂上都过不了**。
这不是"权重没用"，是**门的设计把可裁决区间压成了空集**。下一份预注册应改成
"按科设下限 / 允许 1 个 KC 的抖动 / 只在考试窗内放松 G2"这类可达形式——**本轮不得事后改规则**。

### 6.3 基线水平与可达上限

基线的考试科窗口覆盖 `Y`（holdout）：min 0.5667、中位 0.7333、max 0.8333（5 个取值）；dev：0.6667–0.8。
即基线已经覆盖了 5 场考试窗口内约 **73%** 的考试科 KC，**剩余可提升空间只有 ~27%**（≈8 个 KC 的加权）。
在 1 KC 分辨率 + 10 world + G2 约束下，这个空间不足以支撑"中位 +1 KC 且 8/10 同号"的判定。

---

## 7. 建议（本批结论，全部 model-based）

| 对象 | 建议 | 动作 |
|---|---|---|
| **G6 `EXAM_CAPACITY_RAMP_MAX_EXTRA_FRACTION`** | **0.5（维持现值）** | 不改常量、不 bump VERSION、不重生成金样 |
| **权重 `ReviewScoring` 5 坐标** | **全部维持现值**（due 5.0 / weak 3.0 / exam 2.0 / kc-drop 2.5 / diversity 0.3·0.2·上限 1.5） | 同上 |
| **掌握阈值** | **不实施任何变更**；三组读数（21/0.9、14/0.9、21/0.85）只作候选记录 | 同上 |
| **版本** | `review-planner-v4` 保持 | `git diff` 不含 `AlgorithmConstants.kt` |
| **金样** | 三组金样保持逐位不变 | 未触发 §11 bundle |

**为什么"没有采纳"本身是合格结果**：预注册 §4.3/§5.4 的默认分支就是 `KEEP`。本轮**没有**在看到 holdout 后调网格、
调容差或换指标；所有门与网格在实验前落盘（sha256 已核）。

**下一批（若要继续，需新预注册）的三个前置**：

1. **门可达性**：G2 改为按科下限或允许 1 个 KC 抖动，否则任何臂都过不了（§6.2）。
2. **测量面**：G6 需要解除 `MAX_BEAM_STEPS=20` 的封顶，或把 `f` 的效果改到"同样 20 题内的排序质量"上（§6.1）。
3. **功效**：10 个 world 在 1 KC 分辨率下功效不足；扩到 30+ world 或提高世界规模（更多 KC/科），
   同时补 `W-transfer` 与科目级 slip 两种错配臂（预注册 §2.4 已登记未实现）。

---

## 8. 结论性质：哪些是 model-based / BLOCKED / 未验证

| 项 | 状态 | 说明 |
|---|---|---|
| 本报告全部 G6 / 权重 / 阈值数字 | **model-based** | 只在 30 KC / 300 题 / 120 天 / 1200s 的合成世界上成立；对外表述必须带 `model-based` |
| 真库 per-reason / G6 因果效应 | **BLOCKED** | 本批零真库样本、无反事实；不得用模拟数替代 |
| A 线 FSRS 拟合与 `fsrsBeatsBaseline` 上线门 | **BLOCKED（未运行）** | 执行者 manifest：`A-line FSRS fitting (§7) not run in this ask`；本会话也未运行 |
| 参数可辨识性 / 参数恢复 | **BLOCKED: WORLD_TOO_SMALL_FOR_IDENTIFIABILITY** | `predictableSampleCount` 千级 < 5 000（预注册 §7.3） |
| `avoidance` 权重 | **不可测** | 模拟候选恒 false |
| A0（无考试记录真空臂） | **未测** | `Y` 无定义 |
| 容量预览路径的 G6 净效应 | **不在裁决范围** | 模拟只走计划队列路径；预览路径的 `growthCeilingBudgetSeconds` 未建模 |
| 异机 / 异 JDK 复现 | **未验证** | 只在同一开发机、同一 JDK 复跑 |
| 本报告未覆盖的产品闭环（KD-67 / 校准页 / 投影冲突） | **不在本报告范围** | 属计划 §P2，不属标定 |

---

## 9. 机检清单对账（预注册 §8 的 14 条）

| # | 规则 | 状态 | 证据 / 谁跑的 |
|---|---|---|---|
| 1 | 预注册未被篡改 | ✅ | 本会话 `sha256sum` = `042776e8…`，与 manifest 一致 |
| 2 | 代码基线可复现 | ⚠️ 部分 | 报告时点：manifest 的 **9 个**文件哈希全部匹配；prereg 哈希另行匹配；driver 不在 manifest 钉表内。**最终树已漂移**：`StudyReviewPlannerService.kt` 2bd8c40b→1cedc668（P2-14 `reasons = scored.reasons`，报告后）、driver 6f2ec17a→d9e649a1（报告/门禁后）；漂移登记在 manifest/report JSON 的 `post_report_drift`（§10.3）。另：manifest 记录的是**实验后**（seam 落地后）哈希，与预注册 §1.1 的**实验前**基线有 3 个文件不同（预期内，见 §1.2、§10.2） |
| 3 | 种子层不混用 | 未在本会话运行 | 用例 `holdout seeds are disjoint…`（`SimulatedLearnerTest.kt`）；执行者记录 `tests=6 failures=0` |
| 4 | 同 seed 逐位一致 | ✅ | `determinism.json` 3/3 相同 + 本会话跨进程比对 7/7、28/28 相同 |
| 5 | 默认臂 == 基线（seam S3） | 未在本会话运行 | `seam-verification.txt`：`ReviewScoringWeightsSeamTest tests=3 failures=0`（执行者跑） |
| 6 | golden 世界进 CI | 未在本会话运行 | `SimulationDeterminismContractTest.golden world digest is pinned`，钉 `55fe92cc…aacfe`（seed=5001/60 天/120 题） |
| 7 | 前缀同一性 | ✅ | `prefix-identity.json` 120/120；本会话汇总 `all_equal=True` |
| 8 | 日预算不超发 | ✅ | 本会话复算：所有行 `overrun_strict = overrun_tolerance = 0` |
| 9 | 等时合格率 | ✅ | 本会话汇总：G6 12/40、权重 46/170 holdout `NO_EQUAL_TIME`，逐臂 n 已列 |
| 10 | 无数据不填数 | ✅ | JSON 用 `null`；`thresholds.csv` 保持率分母 0 记空 |
| 11 | holdout 只跑一次 | ⚠️ **部分违反并登记** | `cross-process-rerun/` 用 **holdout seed 901** 重跑了一次（每层 1 world）；`digest` 逐位相同、不参与裁决。`holdout_run_id` 两次相同 ⇒ **该 id 不能证明单次执行**（§10.1） |
| 12 | 掌握阈值未动 | ✅ | `AlgorithmConstants.kt` 哈希与实验前基线相同；实测 21.0 / 0.9 |
| 13 | 金样差异被记录 | N/A | 未采纳任何值 → 无重生成 |
| 14 | 裁决函数可复算 | ✅ | `report-recompute/recompute_decisions.py`（本会话实跑）：**n / guard / pass 21/21 全等**，median 差异 ≤ 1e-6（CSV 6 位小数）；`positive` 在 2 个臂上差 1（f=0.15、diversity×0.75），根因是 `Δ≈1e-16` 被 CSV 舍成 0，**以全精度 JSON 为准** |

---

## 10. 偏离与污染登记

### 10.1 执行侧

| # | 事项 | 影响 | 处置 |
|---|---|---|---|
| 1 | 物理 run 3 120（预注册预算 1 380–1 480） | 计划外 ρ 扫描（§5.3 要求） | 登记为矩阵低估；未改变裁决规则 |
| 2 | `cross-process-rerun/` 重跑了 holdout seed 901 | 一次性 holdout 被二次执行 | 两次 `digest` 全等（7/7、28/28），且**无任何臂过门**（不存在用该 world 调参的路径）；登记，旧 holdout **不**判污染，但下批需换新 seed 基 |
| 3 | `holdout_run_id` 两次相同（`BATCH49_HOLDOUT_RUN_ID` 环境变量固定） | 机检 11 的 id 判据失效 | 报告改为"两 manifest 的 `started_at`/run 数"作单次性证据 |
| 4 | `threshold-readings.json` 的 `n=20` 合并了 dev 与 holdout | 聚合值不是 holdout-only | 本报告 §3.3 明确标注合并口径；逐层原始值在 `thresholds.csv` |
| 5 | W-slip 误判率结构性为 0 | 该臂读数无信息量 | JSON 已标 `structural_zero: true`，报告同样标注 |
| 6 | 落盘 CSV 仅 6 位小数 | 复算符号计数在 2 个臂上差 1（浮点尘 `1e-16` 被舍成 0） | 裁决以全精度 JSON 为准（差距 1 远低于门 8，不改结论）；下批给 `Δ>0` 加 1e-9 门槛 |

### 10.2 与预注册文本的字面偏离

| # | 预注册要求 | 实际 | 说明 |
|---|---|---|---|
| 1 | 机检 2：报告 `file_sha256` == §1.1 | seam 落地使 3 个文件哈希改变 | 预注册 §1.1 自己写了"未跟踪文件按工作树哈希钉"；seam 是 §5.1 的前置。本报告改为对**实验后**哈希对账（全等）并在 §1.2 列出 |
| 2 | 权重线在 seam 落地前应标 `BLOCKED: NO_INJECTION_SEAM` | seam 已落地（S1–S5 全绿） | 权重线**实际跑通**，不再是 BLOCKED |
| 3 | 报告 JSON 的 `arm.metrics.Y` 用 `Double.toHexString` | 原 JSON 用 hex；本报告 JSON 同时给 hex 与 decimal | 便于人读，不改变值 |

### 10.3 报告后代码漂移（批次 49 修复环登记）

| 文件 | 报告时点钉表 | 最终树 | 原因 / 影响 |
|---|---|---|---|
| `StudyReviewPlannerService.kt` | `2bd8c40bb6352e5d…d6948d` | `1cedc66844261a04…cc1bbf` | P2-14 在报告后加入 `reasons = scored.reasons`（`:944`），把实际选中理由写进返回；不影响 G6/权重/金样（三者只依赖 `ReviewPlannerV2.kt`，其哈希仍匹配） |
| `ReviewCalibrationExperimentTest.kt`（driver） | `6f2ec17aceb06b13…c8d74a` | `d9e649a1e7760a7d…fdb5c6` | driver 在 unit/strong gate、设备绿跑、acceptance 索引之后被改（mtime 11-08 05:11）；不在 manifest 钉表，产出 CSV 不能仅凭哈希回溯到最终 driver。修复环用最终 driver 重跑并逐位比对 `g6-runs.csv` / `weight-runs.csv`（结果见 `docs/research/evidence/2026-11-08-batch-49/repair/calibration-driver-drift.md`） |

两份 JSON 的 `post_report_drift` 字段是上述漂移的机器可读登记；`meta.file_sha256` 保持报告时点值不动，
避免把历史快照改写成“最终树”。

---

## 11. 复跑命令

```bash
# 1) 实验（串行；执行者已跑过，本会话未重跑）
./gradlew :core:domain:test --tests "com.tingyun.smartmistakebook.core.domain.simulation.ReviewCalibrationExperimentTest" --console=plain
#   输出目录：docs/research/evidence/2026-11-08-batch-49/calibration/

# 2) 独立复算裁决（本会话实跑，exit=0）
cd docs/research/evidence/2026-11-08-batch-49/calibration
python -I -X utf8 report-recompute/recompute_decisions.py g6-runs.csv weight-runs.csv > report-recompute/decision-recompute.txt

# 3) 补充读数（本会话实跑，exit=0）
python -I -X utf8 report-recompute/supplementary_readings.py > report-recompute/supplementary-readings.txt

# 4) 十进制臂表 + 报告 JSON（本会话实跑，exit=0）
python -I -X utf8 report-recompute/arm_tables.py > report-recompute/arm-tables.md
python -I -X utf8 report-recompute/build_report_json.py > ../../../../2026-11-08-review-calibration-report.json

# 5) 哈希与 HEAD（本会话实跑）
sha256sum docs/research/2026-11-08-review-calibration-prereg.md
sha256sum core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/ReviewPlannerV2.kt
sha256sum core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/AlgorithmConstants.kt
sha256sum core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/ReviewPlanner.kt
sha256sum core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/ClearlyMasteredForSkipPolicy.kt
sha256sum core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/SchedulingEvaluation.kt
sha256sum core/domain/src/test/kotlin/com/tingyun/smartmistakebook/core/domain/ReviewPlannerScaleBenchmarkTest.kt
sha256sum core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/StudyReviewPlannerService.kt
sha256sum core/domain/src/test/kotlin/com/tingyun/smartmistakebook/core/domain/simulation/SimulatedLearner.kt
sha256sum core/domain/src/test/kotlin/com/tingyun/smartmistakebook/core/domain/simulation/SimulatedLearnerTest.kt
sha256sum core/domain/src/test/kotlin/com/tingyun/smartmistakebook/core/domain/simulation/ReviewCalibrationExperimentTest.kt
git rev-parse HEAD
```

> `-X utf8` 是必需的：Windows 下 `python -I` 会忽略 `PYTHONIOENCODING`，重定向默认走 cp936，
> 落盘文件会变成非 UTF-8（本会话第一次生成即踩到，已重生成并 `utf-8` 解码校验通过）。

**机器可读版**：`docs/research/2026-11-08-review-calibration-report.json`（`meta` / `arms` / `decisions` / `blocked` / `reproduce`）。

---

## 12. 一句话交接

> 在这个 30 KC / 300 题 / 120 天 / 1200s 的**合成世界**里，**没有任何 G6 幅度或权重坐标通过预注册门**：
> 一半的臂根本没有足够的等时 world（`NO_EQUAL_TIME`），另一半效应不足或方向不稳，而**所有**臂都至少违反一条护栏（主要是 G2）。
> 因此本批**不改任何常量、不 bump 版本、不重生成金样**；`f` 维持 0.5，5 个权重坐标全部维持现值，掌握阈值只留下三组读数。
> 真正的下一步不是再扫一遍网格，而是**先把门改成可达形式、解除 20 题封顶、把 world 数补到有功效**——这三条都要新预注册。
> 所有结论 **model-based**；真库与 A 线均为 **BLOCKED**。
