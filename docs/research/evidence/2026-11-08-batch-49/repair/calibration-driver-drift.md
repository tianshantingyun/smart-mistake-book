# 标定实验 driver 后置漂移：最终 driver 复跑逐位比对（批次 49 修复环）

## 问题

`docs/research/2026-11-08-review-calibration-report.md` 记录 driver
`ReviewCalibrationExperimentTest.kt` 的报告时点哈希 `6f2ec17aceb06b13…c8d74a`；
最终树实测 `d9e649a1e7760a7d…fdb5c6`（mtime 2026-11-08 05:11，晚于 unit gate 01:42、
strong gate 01:50、设备绿跑 02:06、acceptance 索引 04:36）。driver 不在
`calibration/run-manifest.json` 的 `file_sha256` 钉表内，因此仅凭报告哈希无法证明
`g6-runs.csv` / `weight-runs.csv` 是最终 driver 产出的。

## 修复环实跑（最终 driver）

```bash
cd "D:/smart mistake book"
BATCH49_CALIBRATION=run \
BATCH49_CALIBRATION_OUT="D:/smart mistake book/build/repair/calibration-rerun" \
./gradlew :core:domain:test \
  --tests 'com.tingyun.smartmistakebook.core.domain.simulation.ReviewCalibrationExperimentTest' \
  --rerun-tasks --console=plain
# BUILD SUCCESSFUL in 12m 14s; EXIT=0; wall_clock_seconds=709
```

产物已复制到 `repair/calibration-rerun/`（9 份）。逐字节比对：

| 文件 | 与归档 `calibration/` 比对 |
|---|---|
| `g6-runs.csv` | **IDENTICAL**（`cmp` 无输出） |
| `weight-runs.csv` | **IDENTICAL** |
| `thresholds.csv` | **IDENTICAL** |
| `g6-decisions.json` | **IDENTICAL** |
| `weight-decisions.json` | **IDENTICAL** |
| `threshold-readings.json` | **IDENTICAL** |
| `prefix-identity.json` | **IDENTICAL** |
| `determinism.json` | **IDENTICAL** |
| `confirmation.json` | **IDENTICAL** |

复跑 manifest 的 `file_sha256` 与归档逐项对比：8/9 MATCH；只有
`StudyReviewPlannerService.kt` 从 `2bd8c40b…` 变为 `1cedc668…`（P2-14 报告后加的
`reasons = scored.reasons`，`:944`），而实验裁决与金样只依赖 `ReviewPlannerV2.kt`
（`ed6ae4e5…`，MATCH）。**结论：最终 driver 逐位复现全部实验产出；裁决不依赖漂移文件。**

## 登记

- `calibration/run-manifest.json` 与 `docs/research/2026-11-08-review-calibration-report.json`
  增加 `post_report_drift`：报告时点哈希保持不动，最终树哈希与影响另列。
- 报告 §1.2/§8 机检 2/§10.3 已把“11 个文件全部匹配”更正为“报告时点匹配；最终树 2 处漂移，
  已复跑比对”。
- **holdout 再执行登记（诚实补充）**：本次复跑与归档实验一样覆盖 holdout seeds
  901–910（`worlds_per_layer=10`），目的仅是逐位复现，不是新的裁决；全部产物与归档
  逐位相同，未改变任何臂/选择；`holdout_run_id` 使用新的随机值
  （`batch49-holdout-1794088985575`），不再复用归档 id。它不改变报告对机检 11 的
  `PARTIAL_VIOLATION_REGISTERED` 结论。
- **driver 未入钉表的原因**：`run-manifest.json` 的 `file_sha256` 由 driver 在运行时对
  §1.1 列出的 9 个源文件计算；driver 自身不在列表内。修复环没有改写 driver，
  而是用最终字节复跑并登记哈希，避免把历史驱动伪装成最终驱动。
