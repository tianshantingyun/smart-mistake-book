# 批次 49 · P1-9 行为门裁决：维持现值（2026-11-08）

> 执行角色：core:domain 工程师。本 ask 的授权边界是「**仅当预注册门通过才改行为**」。
> 本会话实跑：`grep` / `git` / `sha256sum` / 两次定向 Gradle（串行）/ 读预注册、报告与证据 JSON。
> **未改**任何生产源码、常量、测试、金样、VERSION、台账。设备与真库**未触碰**；
> 未跑项在 §6 如实登记，不把缺失的门当已通过。

## 0. 一屏结论

| 线 | 预注册门结果（证据出处） | 本会话动作 |
|---|---|---|
| **G6 幅度 `f`** | 0/4 过门；`adopted_f = null`，`verdict = KEEP_0.50`（`calibration/g6-decisions.json:26-27`） | 不改 `AlgorithmConstants.kt:148`（维持 `0.5`） |
| **权重 5 坐标 / 17 臂** | 0/17 过门；`passing = 0`，`adopted = null`，`verdict = KEEP_ALL_CURRENT_WEIGHTS`（`calibration/weight-decisions.json:27-28`） | 不改 `AlgorithmConstants.kt:114-125`；`DEFAULT_KC_DROP_WEIGHT = 2.5` 不动 |
| **掌握阈值** | 只读（ask 硬约束）；报告 §3.3 只出读数 | 不改 `AlgorithmConstants.kt:63`（21.0）/ `:66`（0.9） |
| **VERSION** | 未采纳任何非默认值 → 预注册 §11 bundle 未触发 | `review-planner-v4` 不 bump（`ReviewPlannerV2.kt:1097`） |
| **三组金样** | 同上，§11 未触发 | 未重生成；`ReviewPlannerScaleBenchmarkTest` 2/2 绿、金样逐位一致（§4） |
| **算法台账** | 预注册 §11-5 只在**采纳**时记「旧值 → 新值」一行 | **不新增台账行**（无值变更可记） |

一句话：**报告未给出任何过门的 G6/权重建议，因此本线不改行为，只登记「维持现值 + 证据」。**

## 1. 门裁决证据（读自本批已落盘证据，非本会话重跑实验）

| # | 文件 | 本会话读到的关键字段 |
|---|---|---|
| 1 | `docs/research/evidence/2026-11-08-batch-49/calibration/g6-decisions.json` | `"no_equal_time_cells": 22`、`"adopted_f": null`、`"verdict": "KEEP_0.50 (no arm passed the pre-registered gate)"`；4 臂 `pass` 全为 `false`，理由分别为 `UNDERPOWERED`×2 / `median_delta_below_one_kc` / `sign_consistency_below_8_of_10` |
| 2 | `docs/research/evidence/2026-11-08-batch-49/calibration/weight-decisions.json` | 17 臂 `pass` 全为 `false`；`:25` `"passing": 0`、`:26` `"adopted": null`、`:28` `"verdict": "KEEP_ALL_CURRENT_WEIGHTS"` |
| 3 | `docs/research/evidence/2026-11-08-batch-49/calibration/decision-recompute.txt` | 独立复算：G6 `-> effect-only adopt: None`；权重 `-> effect-only passing arms: 0` |
| 4 | `docs/research/2026-11-08-review-calibration-report.md` | `:12` 最终裁决「G6 维持 0.5；17 个权重臂全部维持现值…VERSION 不 bump；三组金样不重生成」；`:28-29` 逐线理由；`:294-295` 建议表；`:416` 同结论 |
| 5 | `docs/research/2026-11-08-review-calibration-report.json` | `decision` 块：`"constants_changed": false`、`"version_bump": false`、`"version": "review-planner-v4"`、`"golden_regenerated": false` |

裁决规则本身来自冻结的预注册（`docs/research/2026-11-08-review-calibration-prereg.md` §4.3/§5.4）：
`pass = (median Δ ≥ 1 KC) ∧ (Δ>0 ≥ 8/10) ∧ 护栏全过 ∧ 非网格端点 ∧ n ≥ 8`；
`pass 集为空 → KEEP 现值`。本次报告正是该默认分支。

## 2. 当前值逐行核对（本会话 `grep -n` 实跑）

```text
$ grep -n "MIN_STABILITY_DAYS\|MIN_CURRENT_RETENTION\|DUE_WEIGHT\|WEAKNESS_WEIGHT\|LAPSE_WEIGHT\|REPEAT_MISTAKE_WEIGHT\|AVOIDANCE_WEIGHT\|EXAM_WEIGHT\|WAITING_WEIGHT\|FAMILY_PENALTY_WEIGHT\|SOURCE_PENALTY_WEIGHT\|MAX_DIVERSITY_PENALTY\|EXAM_CAPACITY_RAMP_MAX_EXTRA_FRACTION" \
    core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/AlgorithmConstants.kt
63:        const val MIN_STABILITY_DAYS: Double = 21.0
66:        const val MIN_CURRENT_RETENTION: Double = 0.9
114:        const val DUE_WEIGHT: Double = 5.0
115:        const val WEAKNESS_WEIGHT: Double = 3.0
116:        const val LAPSE_WEIGHT: Double = 1.0
117:        const val REPEAT_MISTAKE_WEIGHT: Double = 2.0
118:        const val AVOIDANCE_WEIGHT: Double = 1.0
119:        const val EXAM_WEIGHT: Double = 2.0
120:        const val WAITING_WEIGHT: Double = 1.5
123:        const val FAMILY_PENALTY_WEIGHT: Double = 0.3
124:        const val SOURCE_PENALTY_WEIGHT: Double = 0.2
125:        const val MAX_DIVERSITY_PENALTY: Double = 1.5
148:        const val EXAM_CAPACITY_RAMP_MAX_EXTRA_FRACTION: Double = 0.5
```

- `ReviewPlannerV2.kt:1097`：`const val VERSION = "review-planner-v4"`（本会话 `grep -rn "VERSION = "` 实跑，唯一命中）。
- `KC_DROP_WEIGHT` 单源（本会话 `grep -rn "KC_DROP_WEIGHT" core/ --include=*.kt` 实跑）：
  - 定义：`ReviewPlannerV2.kt:53` `const val DEFAULT_KC_DROP_WEIGHT: Double = 2.5`；
  - `ReviewPlanner.kt:23` 仅剩注释（旧 `internal const val KC_DROP_WEIGHT = 2.5` 已在 seam 落地时删除）；
  - 其余命中为测试引用（`ReviewCalibrationExperimentTest.kt:1181`）与 KDoc 提及。
  - 即：生产代码里**不再有第二处字面量**，P1-9 的「单源」已成立且本轮未再改。

## 3. 「行为未变」的可复现核对（本会话实跑）

### 3.1 常量文件零 diff

```text
$ git diff --stat core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/AlgorithmConstants.kt; echo "exit=$?"
exit=0
（无 stat 输出 = 与 HEAD 逐字节相同）
```

### 3.2 当前文件哈希 == 实验时 manifest 钉表（`sha256sum` 实跑）

| 文件 | 本会话实测 | manifest `file_sha256`（`calibration/run-manifest.json`） |
|---|---|---|
| `ReviewPlannerV2.kt` | `ed6ae4e50147fc0f76580f5185cf0f8b9f8c7fc8e395db3fc7a9eda7fdc9651c` | 同值 |
| `AlgorithmConstants.kt` | `e695418f5a24f56ab38ec11d44dd84f7030423c69bb57fd27942e34a36ccfd84` | 同值（且 == 预注册 §1.1 实验前基线） |
| `ReviewPlanner.kt` | `62143174c3a4086b745044cc0a5b69275fce9810b93dcac35b88b4610d0320f2` | 同值 |
| `ClearlyMasteredForSkipPolicy.kt` | `3cb0af7c7c199a98dec451338288dccad469d6db47aaf5cec1b214c80098599d` | 同值 |
| `SchedulingEvaluation.kt` | `e0f3f26aaac5dd1545bfa8eb8fda2e3fd37d8dd4b3dc9aba66e5b0fe780f7338` | 同值 |
| `ReviewPlannerScaleBenchmarkTest.kt` | `d72a2ed9aa8bc91f6471d75a0905475c5788af9f4a20c336d23e97c9a0342560` | 同值（且 == 预注册 §1.1 基线 → 金样文件未重生成） |
| `StudyReviewPlannerService.kt` | `2bd8c40bb6352e5dd01b8a0998b9f7843d2c32a2d6c8602b073f3673f4d6948d`（报告时点） | 同值 |
| `SimulatedLearner.kt` | `62d1eeac482ab165e26920a5665d3e54719334dc3674b235a3149d88b63e65ef` | 同值 |
| `SimulatedLearnerTest.kt` | `94a27fae700e681f08b5542df68de813bb86c111bdee7cd2ab9f37eebd99212e` | 同值 |
| `prereg` | `042776e829fe943a736007074ef77ac0e191f7bbeca0169b58d3d1a3be96b843` | 同值（manifest `prereg_sha256`） |

结论（已按修复环更正）：上表是**实验/报告时点**的对账，不是最终树。最终树
`StudyReviewPlannerService.kt` 实测 `1cedc66844261a04…cc1bbf`——批次 49 P2-14 在报告后加入
`reasons = scored.reasons`（`:944`）。因此原文“实验落盘后没有任何字节改动”只对报告时点成立；
漂移已登记在 `calibration/run-manifest.json` 与报告 JSON 的 `post_report_drift`。G6/权重/金样
只依赖 `ReviewPlannerV2.kt`（`ed6ae4e5…` 仍匹配），本批“维持现值、未采纳任何臂”的裁决不受影响。

## 4. 定向 Gradle（本会话串行实跑，两次）

前置：本会话开工时检查到另一线 Gradle 客户端
`gradlew :app:assembleStrictOfflineDebug :core:data:assembleDebugAndroidTest` 正在运行，
等待其结束后（daemon 日志 `Marking the daemon as idle`，23:17:25）才起本线构建，未并行。

### 4.1 命令 1（5 个类，一次 Gradle 调用）

```text
$ ./gradlew :core:domain:test \
    --tests "com.tingyun.smartmistakebook.core.domain.ReviewPlannerScaleBenchmarkTest" \
    --tests "com.tingyun.smartmistakebook.core.domain.ReviewPlannerV2Test" \
    --tests "com.tingyun.smartmistakebook.core.domain.SchedulingEvaluationTest" \
    --tests "com.tingyun.smartmistakebook.core.domain.ReviewScoringWeightsSeamTest" \
    --tests "com.tingyun.smartmistakebook.core.domain.simulation.SimulatedLearnerTest" \
    --console=plain
BUILD SUCCESSFUL in 18s
7 actionable tasks: 1 executed, 6 up-to-date
```

XML（`core/domain/build/test-results/test/`，timestamp `2026-11-07T15:18Z` = 本地 23:18）：

| 类 | tests | skipped | failures | errors | time(s) |
|---|---|---|---|---|---|
| `ReviewPlannerScaleBenchmarkTest` | 2 | 0 | 0 | 0 | 5.296 |
| `ReviewPlannerV2Test` | 27 | 0 | 0 | 0 | 1.901 |
| `ReviewScoringWeightsSeamTest` | 3 | 0 | 0 | 0 | 0.006 |
| `SimulatedLearnerTest` | 6 | 0 | 0 | 0 | 5.865 |

- `ReviewPlannerScaleBenchmarkTest` 的 `golden selection sequences stay bit identical` 通过
  = 三组金样（60/500/5000 夹具）与硬编码常量逐位一致，默认路径未被 seam 或本轮任何改动扰动。
- `ReviewScoringWeightsSeamTest` 3/3 通过 = 显式 `DEFAULT` 与隐式默认指纹相同、默认值等于
  `AlgorithmConstants.ReviewScoring`、10 个坐标均为活旋钮（无死旋钮）。
- `SimulatedLearnerTest` 6/6 通过 = 同 seed 逐位一致、预算不超发、dev/holdout 种子不混用等。

### 4.2 命令 2（ask 点名的 SchedulingEvaluation；实际类名为 `SchedulingEvaluationHarnessTest`）

`core/domain/src/test/.../SchedulingEvaluationTest.kt:11` 的类名是 `SchedulingEvaluationHarnessTest`，
按文件名过滤不会命中；以真实类名重跑：

```text
$ ./gradlew :core:domain:test --tests "com.tingyun.smartmistakebook.core.domain.SchedulingEvaluationHarnessTest" --console=plain
BUILD SUCCESSFUL in 8s
7 actionable tasks: 1 executed, 6 up-to-date
```

XML：`SchedulingEvaluationHarnessTest` `tests="24" skipped="0" failures="0" errors="0" time="0.398"`。

> 说明：ask 写的「SchedulingEvaluationTest」在本仓对应文件 `SchedulingEvaluationTest.kt`、
> 类 `SchedulingEvaluationHarnessTest`（同文件另含 `TimeOfDaySignalsTest`，本会话未单独跑；
> 它不属 ask 点名范围）。

## 5. 本轮未改动清单（逐项）

| 项 | 状态 |
|---|---|
| `AlgorithmConstants.kt`（掌握阈值 / `ReviewScoring` / G6） | **零改动**（§3.1、§3.2） |
| `ReviewPlannerV2.VERSION` | 未 bump，仍 `review-planner-v4`（`ReviewPlannerV2.kt:1097`） |
| `ReviewPlannerScaleBenchmarkTest` 三组金样 | 未重生成（文件哈希 == 预注册基线，§3.2；用例绿） |
| `ReviewPlannerV2Test` / `SchedulingEvaluationHarnessTest` / `ReviewScoringWeightsSeamTest` / `SimulatedLearnerTest` | 未改断言、未放宽门（本轮只读+跑） |
| `docs/research/algorithm-version-ledger.md` | 未新增行（无值变更；预注册 §11-5 只在采纳时记） |
| `RoomStudyDatabase.kt` / DAO | 未触碰（本线不涉及） |
| 热区 `feature/review`、`app` | 未触碰。开工时 `git status` 显示他线改动（`feature/review/.../KnowledgeReviewSessionViewModelTest.kt` 等），按硬约束不整文件抢占 |

本会话唯一写入 = 本证据文件。

## 6. 未验证 / BLOCKED（不当作已通过）

| 项 | 状态 | 依据 |
|---|---|---|
| 真库 per-reason / G6 因果效应 | **BLOCKED**（本批零真库样本） | 报告 §8；`run-manifest.json` `known_deviations` 第 4 条 |
| A 线 FSRS 拟合 / `fsrsBeatsBaseline` 上线门 | **未运行** | 报告 §0/§8；`run-manifest.json` `known_deviations` 第 3 条 |
| 参数可辨识性 | **BLOCKED: WORLD_TOO_SMALL_FOR_IDENTIFIABILITY** | 预注册 §7.3、报告 §8 |
| 12 模块单测门 / 强门 / 设备用例 / 2h soak | **未运行**（本 ask 范围外） | 本会话只跑 `:core:domain:test` 定向 5 类 |
| 全树构建 | **当前 FAIL**，但**与本线无关**：他线在编文件 `core/data/src/androidTest/.../RoutingEvalDeviceExportInstrumentedTest.kt:73` 语法错误（`core/data:compileDebugAndroidTestKotlin`），本会话未触碰该文件、未修 | 23:17 daemon 日志（他线 `assembleStrictOfflineDebug` + `core:data:assembleDebugAndroidTest`） |
| 异机 / 异 JDK 复现 | **未验证** | 只在当前开发机、JDK 21 实跑 |

## 7. 结论

预注册门在本批**未通过任何一条 G6/权重臂**，报告建议为「全部维持现值」。按 ask 授权，
本线**不修改任何行为**：常量、VERSION、金样、台账均保持原状；`core:domain` 定向 5 类测试
本会话实跑全绿（62 例、0 失败），三组金样逐位一致。所有 G6/权重数字仍是 **model-based**
（30 KC / 300 题 / 120 天合成世界），不构成真库结论。
