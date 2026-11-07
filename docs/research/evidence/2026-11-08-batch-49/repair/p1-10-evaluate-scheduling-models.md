# P1-10 上线门：evaluateSchedulingModels 在模拟世界断言 fsrsBeatsBaseline

## 问题

计划 P1-10：「`evaluateSchedulingModels` 在模拟+真库断言 `fsrsBeatsBaseline`」。
批次 49 报告把 A 线（FSRS 拟合/上线门）整体记为 BLOCKED，模拟侧也未运行——
上线门只有「未运行」，没有任何可复算断言。真库零样本标 BLOCKED 符合硬约束，
但模拟侧可以且应当跑。

## 修复环实跑

新增 `core/domain/src/test/kotlin/com/tingyun/smartmistakebook/core/domain/simulation/SchedulingEvaluationOnSimulationTest.kt`：

- 用预注册冻结的 10 个 dev seed（101–110）各跑一次 `SimulatedLearner`，取
  `fittableReviewSamples(reviewLog)`；
- 按 world 前缀隔离 `practiceUnitId`（`seed101:` 等）后合并，避免把不同世界的同一道题
  当成一条连续卡史；
- 直接调用**生产评估器** `SchedulingEvaluationHarness.evaluate(samples)`，断言
  `SchedulingEvaluationReport.fsrsBeatsBaseline`（内部仍要求 fsrs/baseline 均有效、
  `evaluationSampleCount >= 200`、FSRS logLoss < baseline）。

```bash
./gradlew :core:domain:test \
  --tests 'com.tingyun.smartmistakebook.core.domain.simulation.SchedulingEvaluationOnSimulationTest' \
  --console=plain
# BUILD SUCCESSFUL; 1 test passed
```

实得（JUnit `system-out`，XML 见同目录）：

```text
[batch49-p1-10] samples=21489 fsrsLogLoss=0.15014319150694827 baselineLogLoss=0.373101209289151
fsrsValid=true baselineValid=true sampleCount=885 beats=true
```

结论：**模拟侧上线门通过**（FSRS 在同一证据流上 logLoss 0.1501 < 基线 0.3731，885 个评估样本）。

## 真库侧（仍 BLOCKED）

`StudySchedulingCalibration.evaluateSchedulingModels()` 在
`fittableReviewSamples(...)` 为空或没有任何 `practiceUnitId` 出现 ≥2 次时返回 null
（`StudySchedulingCalibration.kt:44-51`）。本机真库零样本，因此：

- 不填任何真库数字；
- 不宣称 `fsrsBeatsBaseline` 在真库成立；
- 出口仍是报告 §A 的 BLOCKED（需要真实使用历史 ≥ 每卡 2 次可拟合行）。

## 消灭的具体失败

把「模拟世界是 FSRS 生成的，所以 FSRS 当然赢」当默认结论；或把真库零样本记成
「无数据 = 通过」。现在模拟侧有 10-world 合并的可复算断言，真库侧明确 BLOCKED。
