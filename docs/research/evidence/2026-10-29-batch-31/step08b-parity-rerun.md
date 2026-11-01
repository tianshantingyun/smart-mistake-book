# E-08b 一致性原型门复跑（`supersedes` 无；与 E-08 同判定，用于排除偶发/环境）

| 字段 | 值 |
|---|---|
| evidence-id | E-08b |
| convention-version | 1 |
| batch | 2026-10-29-batch-31 |
| step | 五（红例归因的 D 排除：同包同机复跑） |
| gate-or-kd | 一致性门（原型门）；`calibrated-r1` |
| verdict | **FAIL（与 E-08 完全一致）**：`Tests run: 15, Failures: 1`，失败值与首跑**逐位相同** |
| executed-at | 2026-10-29T06:53:45–06:53:54+0800（设备 UTC 22:53:45–22:53:54） |
| operator | 设备验证轨（subagent，批次 31） |
| head | `59d4a03f` |
| worktree | dirty（本轨零写入；复跑前后未重建、未重装） |
| device-serial | `emulator-5562`（API 34 / x86_64 / 4096） |
| apk | `core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk` |
| apk-sha256 | `34151d4d9571daffc27e6a370172afb6775b7e730742dc3484ffd5fdb2e7b952`（与 E-08 同一包，未重装） |
| apk-version | （测试包 versionName 空） |
| exit-code | 宿主 shell `EXIT=0`（非判据） |
| raw-log | `build/device-track-batch31/step08b-parity-rerun.log`（240 行 / 12801 B）/ `-logcat.txt`（249 行 / 43185 B） |
| record-ref | `docs/research/2026-10-29-batch-31-record.md`（后写） |
| sanitized | 是（§6；扫描 0 命中） |

## 1. 目的与判据

E-08 判 A（负控期望值算错）。归因规则（判据 §6.2-4）要求先排除 D（设备/旧包/偶发）。
本文件用**同设备、同 APK、未重建未重装**再跑一次，检查失败是否可复现。

## 2. 命令（可复跑）

```bash
export MSYS_NO_PATHCONV=1
A=C:/Android/Sdk/platform-tools/adb.exe; S=emulator-5562
$A -s $S logcat -c
$A -s $S shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.data.visual.VisualDrawOpsParityInstrumentedTest \
  com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner \
  | tee build/device-track-batch31/step08b-parity-rerun.log
$A -s $S logcat -v threadtime -d > build/device-track-batch31/step08b-parity-rerun-logcat.txt
```

## 3. 结果摘要

| 指标 | 首跑 E-08 | 复跑 E-08b | 一致 |
|---|---|---|---|
| tests run | 15 | 15 | 是 |
| failures | 1（`n09OnePixelTextShiftIsCaught`） | 1（同名） | 是 |
| 失败值 | `1.2624767812258337` | `1.2624767812258337` | **逐位相同** |
| `PARITY_CONTROL ` | 14 | 14 | 是 |
| `thresholds=calibrated-r1` 行 | 15 | 15 | 是 |
| P06 正例基线质心 | 0.32（`F2_AA`） | 0.32（`F2_AA`，`:179`） | 是 |
| P07 正例基线质心 | 0.52（`:220`） | 0.52（`:181` / `:197`） | 是 |
| Time | 0.827 s | 0.802 s | — |

## 4. 关键输出（有界）

```text
Time: 0.802
Tests run: 15,  Failures: 1

java.lang.AssertionError: N09-P06: a whole-block 1 px shift must read centroid=1.000
  (translation equivariance) expected:<1.0> but was:<1.2624767812258337>
  at …VisualDrawOpsParityInstrumentedTest.n09OnePixelTextShiftIsCaught(…:308)

step08b-parity-rerun-logcat.txt:179  PARITY fixture=P06 … text=bboxDev=1 centroid=0.32 mass=0.991 verdict=F2_AA
step08b-parity-rerun-logcat.txt:181  PARITY fixture=P07 … text=bboxDev=1 centroid=0.52 mass=0.957 verdict=F2_AA
```

## 5. 失败 / 跳过 / 异常

与 E-08 相同：1 例在 `…ParityInstrumentedTest.kt:308` 的等式上失败；`assertRed` 已先通过
（门级判红）。复跑后仍未执行到 N09-P07（循环在 P06 中止），故 `PARITY_CONTROL` 仍为 14。

## 6. 脱敏与去噪

- 删除：无。
- 保留：summary、失败值、两例基线读数。
- 扫描：R4 §4.4 → 0 命中（含本文件）。
- 二进制/截图：无。

## 7. 判定

FAIL，与 E-08 一致。**D 类排除成立**：失败值逐位可复现，不是偶发/环境抖动；
不是旧包（同 sha256、15 例全启动）；P06/P07 正例基线读数两次相同（0.32 / 0.52）。
E-08 的 A 类归因维持不变；未放宽断言、未改他线文件。

## 8. 原始全量（不入仓）

- 路径：`build/device-track-batch31/step08b-parity-rerun.log` / `-logcat.txt`
- 行数/大小：240 行 / 12801 B；logcat 249 行 / 43185 B
- 说明：`build/` 被 `.gitignore:15` 忽略。
