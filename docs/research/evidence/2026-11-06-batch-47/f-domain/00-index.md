# F 域证据索引（批次 47，2026-11-05 实跑）

> 目标：批次 46 的 9 条 F 域 FAIL 逐条修复。本目录只挂批次 47 新增证据；批次 44 原始
> 证据（`docs/research/evidence/2026-11-05-batch-44/f-domain/`）不变、仍被台账引用。
> 本文件是导航，不是台账证据。

## 0. 结论

| 处置 | 行 | 依据 |
|---|---|---|
| 转 PASS（7） | F-019 / F-028 / F-029 / F-034 / F-036 / F-064 / F-067 | 台账声称按原始证据改正或补原始日志；`fix-tickets-b46.md` #1–#5、#8、#9 的出口判据 |
| 维持 FAIL（2） | F-055 / F-057 | 根因已修（`587098f1`）但修复后 2h soak 未复跑；`fix-tickets-b46.md` #6/#7 出口判据未满足；缺陷单 FIX-T01/soak |

## 1. 设备与 APK 身份

- 复跑设备：`emulator-5584`（x86_64，API 34，设备时区=UTC，空闲 loadavg≈0.19）。
- export.test APK：`08ea26417f746daddf0344c8b12433702628e25670f7ca80283b2e2374e165c0`，
  从 emulator-5562 已装包拉取（`build/acceptance-b47/export-08ea.apk`）后安装到 5584，
  与批次 43/44 归档的 APK sha 逐字节一致。
- database.test APK：`32235e6323f7caa45bdbdb6a0c566f5f50c9fb4da26cef975163deb3a7501a61`（5584 已装）。
- 批次 44 测量设备 emulator-5566 本批不在线，未复跑其轮次；相关行只按原始归档证据修判定/声称。

## 2. 复跑命令与 exit（逐行）

```bash
# 设备复跑（F-029/F-034/F-036；每轮 ~5s，DB 门 ~108s）
bash docs/research/evidence/2026-11-06-batch-47/f-domain/f47-run-instr.sh \
  emulator-5584 com.tingyun.smartmistakebook.core.export.test \
  'com.tingyun.smartmistakebook.core.export.MistakePdfExporterInstrumentedTest#batchExportRendersBeyondSingleQuestionPageLimitWithinItsOwnBound' \
  export-batch47-r1 docs/research/evidence/2026-11-06-batch-47/f-domain   # EXIT=0, OK (1 test)
#   r1/r2/r3：TIME_S=1.201/1.213/1.216，MistakeBatchPerf elapsedMs=490/464/478
bash docs/research/evidence/2026-11-06-batch-47/f-domain/f47-run-instr.sh \
  emulator-5584 com.tingyun.smartmistakebook.core.database.test \
  'com.tingyun.smartmistakebook.core.database.PerformanceGateTest' \
  db-perf-b47-5584 docs/research/evidence/2026-11-06-batch-47/f-domain    # EXIT=0, OK (6 tests), 107.624s
#   原始 logcat 行：library search page benchmark: samples=[…] p95=55ms（目标 350ms，PerformanceTargets.kt:24）

# 复算（7 行；每行一份 <ID>-*.json + <ID>-*.cmd.log，EXIT=0）
python -I docs/research/evidence/2026-11-06-batch-47/f-domain/f47-recompute.py --target all
```

| 行 | 复算产物 | 关键复算值 | exit |
|---|---|---|---|
| F-019 | F-019-gfxinfo-recount.json/.cmd.log | p50=450/2050/2150/2100/2000/2350/300，范围 300-2350ms | 0 |
| F-028 | F-028-export-values-recount.json/.cmd.log | BATCH60=1.216/1.240/1.277s；PRODUCT=7.973/7.772/8.115s；LONGDOC=0.367/0.380s | 0 |
| F-029 | F-029-threshold-recount.json/.cmd.log | 批次44 断言 3/3（failures=0）；批次47 elapsedMs=490/464/478 均 5000 内 | 0 |
| F-034 | F-034-logcat-recount.json/.cmd.log | r1..r3 各 1 条 MistakeBatchPerf 行 + 原始 instr OK (1 test) | 0 |
| F-036 | F-036-regression-recount.json/.cmd.log | 批次43 545/569/518 vs 批次47 490/464/478（均值 -12.25%，无一轮更慢）；DB p95=55ms | 0 |
| F-064 | F-064-dropbox-recount.json/.cmd.log | data_app_entries=6，crash_buffer_lines=0，全部早于窗口 | 0 |
| F-067 | F-067-dropbox-stability.json/.cmd.log | count_after=6，new_events_in_window=0 | 0 |

## 3. F-055/F-057（维持 FAIL）的复跑命令（未执行）

```bash
# 前置：安装含 587098f1 修复的 app APK（当前 5584 安装的是 2026-11-04 20:44 的旧构建 f534b0dd…，
# 早于修复提交 2026-11-05 08:39；本批未替换共享模拟器上的 app，避免影响其他轨）
adb -s <serial> install -r app/build/outputs/apk/localFirst/debug/app-localFirst-debug.apk  # c5f5efd1…，已验证 classes4.dex 含 retentionFromSlider
# 2h soak（占设备 2 小时）
bash docs/research/evidence/2026-11-05-batch-44/f-domain/f-soak.sh emulator-5584 7200 \
  docs/research/evidence/2026-11-06-batch-47/f-domain
# 判据：soak-samples.csv 全程 FATAL/ANR 新增 0、dropbox app 计数无新增；否则维持 FAIL
```

修复侧既有证据（本批未重跑，Gradle 仅限门禁阶段）：
`docs/research/evidence/2026-11-06-batch-45/fix/xml/TEST-com.tingyun.smartmistakebook.SchedulingRetentionSliderValueTest.xml`
（4 tests / failures=0 / errors=0，2026-11-05T02:00:57Z）；源码
`app/src/main/kotlin/com/tingyun/smartmistakebook/SchedulingSettingsScreen.kt:39-53`
（`RETENTION_MIN/MAX` + `retentionFromSlider` 夹取，commit `587098f1`）。

## 4. 边界

- 批次 47 复跑在 x86_64 模拟器上；真机/arm64 仍属 F-006/F-015/F-024/F-033/F-042/F-051/F-060/F-069/F-078/F-087 的 BLOCKED，不在本批声称。
- 共享台账正被多个修复轨并发改行；本批以收敛时刻的台账 sha 为准（见 `build/acceptance-b47/converge-summary.json` 与门禁输出），其他轨的行漂移不归本批处置。
- 本目录产物由 `f47-run-instr.sh` / `f47-recompute.py` 生成，均为仓库内脚本、无外部依赖。
