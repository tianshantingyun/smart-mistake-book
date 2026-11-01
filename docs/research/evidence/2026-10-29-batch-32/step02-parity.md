# E-02 一致性原型门 `VisualDrawOpsParityInstrumentedTest`（PASS 16/16，15 条负控全红）

| 字段 | 值 |
|---|---|
| evidence-id | E-02 |
| convention-version | 1 |
| batch | 2026-10-29-batch-32 |
| step | 二（判据 §2.1–§2.5） |
| gate-or-kd | 一致门（**原型门**，`product-gate=no`）；`calibrated-r2` |
| verdict | PASS（套件绿 + 负控全部 red=true；仍是原型门，非产品/发版结论） |
| executed-at | 2026-10-29T11:18:17+08:00（`Time: 0.989`） |
| operator | 设备验证轨（subagent，批次 32） |
| head | `0b4bdbbe` |
| worktree | dirty（他线在飞；本轨零写入） |
| device-serial | `emulator-5562`（API 34 / x86_64 / 4096） |
| apk | `core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk` |
| apk-sha256 | `de27b081942695474091500d0d7f4a53e8b70e5b3106f1c152f3e1ae2325f15c` |
| apk-version | （测试包空） |
| exit-code | 宿主 `EXIT=0`（**非判据**） |
| raw-log | `build/device-track-batch32/step08-parity.log`（233 行）/ `step08-parity-logcat.txt`（221 行） |
| record-ref | `docs/research/2026-10-29-batch-32-record.md`（后写） |
| sanitized | 是（§6；扫描 0 命中） |

## 1. 目的与判据

- 套件目标 `OK (16 tests)`（构建前复读 16；批次 31 为 15，本批新增 N13）。
- 15 条 `PARITY_CONTROL ` 必须全部 `red=true`；N09-P06/P07、N10/N11/N12 任一绿 = 门失去分辨力（A 类，标定作废）。
- 日志必须显示 `thresholds=calibrated-r2`、方向相关 `blindSet`、`displacement-exits=bestShift(main)+bboxDev(aux)`。
- 必须是 `scope=PROTOTYPE product-gate=no`；不得写成产品门。

## 2. 命令（可复跑）

```bash
export MSYS_NO_PATHCONV=1
A=C:/Android/Sdk/platform-tools/adb.exe; S=emulator-5562
$A -s $S logcat -c
$A -s $S shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.data.visual.VisualDrawOpsParityInstrumentedTest \
  com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner \
  | tee build/device-track-batch32/step08-parity.log
$A -s $S logcat -v threadtime -d > build/device-track-batch32/step08-parity-logcat.txt
```

- 工作目录：仓库根；`EXIT=0`；`Time: 0.989`。

## 3. 结果摘要

| 指标 | 值 | 判据 |
|---|---|---|
| tests run | 16 | 期望 16（构建前复读） |
| failures / errors / skipped | 0 / 0 / 0 | 0 |
| `INSTRUMENTATION_STATUS_CODE: 0` | 16 | 每例一条 |
| `PARITY_GATE scope=PROTOTYPE product-gate=no …` | 16 | == 用例数 |
| `thresholds=calibrated-r2` | 16 | == 用例数 |
| `blindSet=text-centroid B(-v,tau_c) per-fixture (direction-dependent)` | 16 | == 用例数 |
| `displacement-exits=bestShift(main)+bboxDev(aux)` | 16 | == 用例数 |
| `PARITY_CONTROL ` | 15 | == N01×4+N02–N07×6+N09×2+N10+N11+N12 |
| `PARITY_CONTROL ` 非 `red=true` | 0 | 必须 0 |
| `PARITY_GATE_DESENSITIZED` / `PARITY_SYSTEMATIC_DRIFT` | 0 / 0 | 0 |
| `PARITY_DIAGNOSTIC_WRITTEN` | 0 | 0（仅失败写 PNG） |
| `PARITY_ENV` | `replayTextPaintFlags=0`、`sdk=34`、`abi=x86_64+arm64-v8a`、`fontScale=1.0` | 必含 `replayTextPaintFlags=0` |
| `PARITY_F5 … class=TEXT` | 0 | 0 |
| 旧包判别串 `calibrated-r1` / `blindWindow=text-centroid(0,0.76)` | 0 / 0 | 0 |

## 4. 关键输出（有界）

```text
Time: 0.989
OK (16 tests)
```

15 条负控逐名（均 `red=true`）：

```text
N01-P01 F4_GEOMETRY  N01-P02 F4_GEOMETRY  N01-P03 F4_GEOMETRY  N01-P05 F4_GEOMETRY
N02 F4_COLOR  N03 F4_GEOMETRY  N04 F4_GEOMETRY  N05 F4_GEOMETRY  N06 F4_COLOR  N07 F4_GEOMETRY
N09-P06 F4_GEOMETRY  N09-P07 F4_GEOMETRY
N10 F4_GEOMETRY  N11 (text centroid=1.1665px)  N12 F4_GEOMETRY
```

标定读数（`PARITY_CONTROL_DETAIL`）：

```text
N09-P06 centroid=1.2624767812258337 bboxDev=2 mass=0.9910995721651372 bestShift=(1,0)
N09-P07 centroid=0.608282132824767  bboxDev=2 mass=0.9574246771879573 bestShift=(1,0)
N12     centroid=3.56014654994809   bboxDev=5
N13     centroid=0.0 mass=1.0 bboxDev=5 bestShift=(2,0) bestIou=0.7778 verdict=F4_GEOMETRY
```

- P06 与常量 `N09_P06_CENTROID_PX = 1.2624767812258337`（`VisualDrawOpsParityInstrumentedTest.kt:902`）逐位相等。
- P07 实测 `0.608282132824767`，常量 `0.608282132823731`（`:904`），差 `1.04e-12` ≪ 容差 0.02；且 `<= 0.75`，质心对 1 px 位移确为盲区，红来自 `bboxDev=2`/`bestShift`——与 `calibrated-r2` 的方向相关声明一致。这是 N09-P07 首次上设备（批次 31 在 P06 中止）。
- N13（`:368`）证明“质心为 0 时由 bbox/shift 兜底”：`bboxDev=5`、`bestShift=(2,0)`。
- **原期望为何错（N09 纠正）**：旧期望把 `centroidDeltaPx` 当成“同侧 1 px 平移 = 1.000”。它实际是屏↔PDF
  **跨栅格欧氏距离**；同为 +1 px 的 P06 实测 `1.2625`、P07 实测 `0.6083`，两者不同即证明位移与距离非 1:1。
  本批设备读数与 `calibrated-r2` 的方向相关声明一致，判决红由 `bboxDev=2` 承担，不是放宽阈值。

## 5. 失败 / 跳过 / 异常

无。9 类崩溃签名见 E-12。

## 6. 脱敏与去噪

- 删除：无（logcat 全量留 `build/`）。
- 保留：`PARITY_*` 全部行、summary、逐用例状态码。
- 扫描：R4 §4.4 → 0 hits。
- 二进制/截图：无（`PARITY_DIAGNOSTIC_WRITTEN`=0，未写 PNG）。

## 7. 判定

PASS：16/16 绿，15 条负控全部保持红；`calibrated-r2` 首次获得设备绿轮。
**边界**：一致门仍是原型门（`VisualDrawOpsParityInstrumentedTest.kt:100` `product-gate=no ci-gate=no release-gate=no`），只证“PDF 路径相对屏路径未引入新偏差”，不得写成产品门/发版结论；不覆盖跨设备/跨 API/fontScale。

## 8. 原始全量（不入仓）

- 路径：`build/device-track-batch32/step08-parity.log`（233 行 / 11,827 B）、`step08-parity-logcat.txt`（221 行 / 43,169 B）
- 生成命令：见 §2。
- 说明：`build/` 被 `.gitignore:15` 忽略；关闭判据只认本文件。
