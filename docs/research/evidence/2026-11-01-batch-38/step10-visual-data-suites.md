# E-10 core:data 视觉组七套件不回退（仓库 12 / parity 16 / painter 24 / PDF 16 / 本地序号 1 / 对账 6 / 引擎 27）

| 字段 | 值 |
|---|---|
| evidence-id | E-10 |
| convention-version | 1 |
| batch | 2026-11-01-batch-38 |
| step | 步骤三·既有套件（data APK 视觉组串行） |
| gate-or-kd | 批次 38 §1 三·仓库 12 / parity 16 / painter 24 / PDF 16 / 本地序号 1 / 对账 6 / 引擎 27 不回退 |
| verdict | PASS（七套件全绿，计数逐一相符） |
| executed-at | 2026-11-01T05:0x–05:2x+08:00（step16/17/18/20/22/23/24，串行） |
| operator | 设备验证轨（批次 38 工作流子代理） |
| head | `dcfdc5b5` |
| device-serial / avd / api / abi / page-size | emulator-5562 / test_device / 34 / x86_64 / 4096 |
| apk | data androidTest（窗口 B 构建，E-01 §4） |
| apk-sha256 | `e86fce38…9bcc0` |
| exit-code | 各步 0 |
| raw-log | `build/device-track-batch38/step{16,17,18,20,22-local-ordinal,23-reconciler,24-engine}-*.log` + 各 `-logcat.txt` |
| record-ref | `docs/research/2026-11-01-batch-38-record.md` 设备节 |
| sanitized | 是（00-index §6） |

## 1. 目的与判据

七套件不回退，设备 `OK (N)` == 构建前计数（12/16/24/16/1/6/27）；无跳过；九类签名 0。对账 6 例覆盖"JS 成功不可伪造判据"（`method='JS' AND reservation_id IS NULL`）回归钉；本地序号 1 例钉 JS attempt 负序号无预留；引擎 27 例为进程内 QuickJS 引擎门（不经隔离服务）。

## 2. 命令（可复跑，逐步之间 logcat -c / logcat -d）

```bash
export MSYS_NO_PATHCONV=1; A=C:/Android/Sdk/platform-tools/adb.exe; S=emulator-5562
PKG=com.tingyun.smartmistakebook.core.data.test; VIS=com.tingyun.smartmistakebook.core.data.visual
$A -s $S shell am instrument -w -r -e class $VIS.TutorVisualCommitRepositoryInstrumentedTest      $PKG/androidx.test.runner.AndroidJUnitRunner
$A -s $S shell am instrument -w -r -e class $VIS.VisualDrawOpsParityInstrumentedTest              $PKG/androidx.test.runner.AndroidJUnitRunner
$A -s $S shell am instrument -w -r -e class $VIS.VisualDrawOpsPainterInstrumentedTest             $PKG/androidx.test.runner.AndroidJUnitRunner
$A -s $S shell am instrument -w -r -e class $VIS.VisualDrawOpsPdfPainterInstrumentedTest          $PKG/androidx.test.runner.AndroidJUnitRunner
$A -s $S shell am instrument -w -r -e class $VIS.FigureGenerationLocalAttemptOrdinalInstrumentedTest $PKG/androidx.test.runner.AndroidJUnitRunner
$A -s $S shell am instrument -w -r -e class $VIS.FigureGenerationReconcilerInstrumentedTest       $PKG/androidx.test.runner.AndroidJUnitRunner
$A -s $S shell am instrument -w -r -e class $VIS.QuickJsVisualExecutionEngineInstrumentedTest     $PKG/androidx.test.runner.AndroidJUnitRunner
```

## 3. 结果摘要

| step | 套件 | 构建前计数 | 设备结果 | Time (s) | 签名命中 |
|---|---|---:|---|---:|---:|
| 16 | TutorVisualCommitRepository | 12 | **OK (12 tests)** | 11.007 | 0 |
| 17 | VisualDrawOpsParity | 16 | **OK (16 tests)** | 7.605 | 0 |
| 18 | VisualDrawOpsPainter | 24 | **OK (24 tests)** | 1.613 | 0 |
| 20 | VisualDrawOpsPdfPainter | 16 | **OK (16 tests)** | 15.778 | 0 |
| 22 | FigureGenerationLocalAttemptOrdinal | 1 | **OK (1 test)** | 1.526 | 0 |
| 23 | FigureGenerationReconciler | 6 | **OK (6 tests)** | 8.861 | 0 |
| 24 | QuickJsVisualExecutionEngine | 27 | **OK (27 tests)** | 10.174 | 0 |

skip/assumption：全部 0。

## 4. 关键输出（有界）

```text
== step16-repository: OK (12 tests)   == step17-parity: OK (16 tests)
== step18-painter:    OK (24 tests)   == step20-pdf: OK (16 tests)
== step22-local-ordinal: OK (1 test)  == step23-reconciler: OK (6 tests)
== step24-engine:     OK (27 tests)
```

## 5. 失败 / 跳过 / 异常

无。

## 6. 脱敏与去噪

- 删除：无；保留：签名计数（0）。
- 扫描：00-index §6 → 0 hits。

## 7. 判定

**PASS**：102 例（12+16+24+16+1+6+27）全部在 data APK `e86fce38…` 上执行通过，计数与构建前复读逐一相符。
