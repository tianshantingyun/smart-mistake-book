# E-07 flags 2 例 + V-01 app 版 3 例（均全绿）

| 字段 | 值 |
|---|---|
| evidence-id | E-07 |
| convention-version | 1 |
| batch | 2026-11-01-batch-38 |
| step | 步骤三·既有套件（`VisualMeasureReplayPaintFlagsInstrumentedTest` 2 例；`V01VisualChainInstrumentedTest` app 版 3 例） |
| gate-or-kd | 批次 38 §1 三·flags 2 / V-01 app 3 不回退（批次 37 收口态：V-01 app 3 例绿、④ PDF 腿留降级变体） |
| verdict | PASS（两套件全绿） |
| executed-at | 2026-11-01T04:2x+08:00（flags 04:1x、V-01 04:2x，step15/step19） |
| operator | 设备验证轨（批次 38 工作流子代理） |
| head | `dcfdc5b5` |
| device-serial / avd / api / abi / page-size | emulator-5562 / test_device / 34 / x86_64 / 4096 |
| apk | app androidTest（E-01 §3 同包） |
| apk-sha256 | `33e1d6a2…161fa` |
| exit-code | 0 |
| raw-log | `build/device-track-batch38/step15-flags.log`(+logcat)、`step19-v01-app.log`(+logcat) |
| record-ref | `docs/research/2026-11-01-batch-38-record.md` 设备节 |
| sanitized | 是（00-index §6） |

## 1. 目的与判据

- flags 2 例：度量绘制与重放文本绘制共享指纹 flags；指纹不符 fail-closed（判据 `OK (2 tests)`）。
- V-01 app 版 3 例（批次 37 迁移后形态）：①真 `:visual_js` 隔离引擎（`workerUidForTest()` ≠ 宿主 uid）+ 生成腿四断言位（审计 reserved/used=0、attempt `method='JS' ∧ reservation_id IS NULL`、账本零触碰、产物恒 Program）；②生产装配负控（三参 → UNAVAILABLE 零审计行）；③冻结重放（重开库 → restore → DrawOps 文本+contentIds 逐字节穿越"提交→真实重开"）。判据 = `OK (3 tests)` == 构建前计数 3，断言面不缩水（批次 37 判据 §2.1 语义底线沿袭）。

## 2. 命令（可复跑）

```bash
export MSYS_NO_PATHCONV=1; A=C:/Android/Sdk/platform-tools/adb.exe; S=emulator-5562
$A -s $S logcat -c
$A -s $S shell am instrument -w -r -e class com.tingyun.smartmistakebook.visualjs.VisualMeasureReplayPaintFlagsInstrumentedTest \
  com.tingyun.smartmistakebook.offline.test/androidx.test.runner.AndroidJUnitRunner
$A -s $S logcat -v threadtime -d
$A -s $S logcat -c
$A -s $S shell am instrument -w -r -e class com.tingyun.smartmistakebook.visualjs.V01VisualChainInstrumentedTest \
  com.tingyun.smartmistakebook.offline.test/androidx.test.runner.AndroidJUnitRunner
$A -s $S logcat -v threadtime -d
```

## 3. 结果摘要

| 套件 | tests run | failures | skipped | summary / Time | 签名命中 |
|---|---|---|---|---|---|
| flags | 2 | 0 | 0 | **OK (2 tests)** / 0.764 s | 0 |
| V-01 app | 3 | 0 | 0 | **OK (3 tests)** / 15.827 s | 0（spawn=2，watchdog kill=0） |

## 4. 关键输出（有界）

```text
INSTRUMENTATION_STATUS: test=theMeasuringPaintAndTheReplayTextPaintShareTheFingerprintedFlags …
OK (2 tests)
INSTRUMENTATION_STATUS: stream= …（V-01 三例逐例 start/code 0）
OK (3 tests)
```

## 5. 失败 / 跳过 / 异常

无。**V-01 app 3/3 绿同时是 E-02/E-06 首跑红例的环境归因反证**：case A 与 A-09 正例同链同形（真隔离引擎 + label 执行 + 四断言位），在同一 APK、同一时段带内通过——本批度量面改动（他轨 M-01/M-03，`VisualContentMeasuring.kt` +196/−43）不影响该链在预算内完成。

## 6. 脱敏与去噪

- 删除：无；保留：签名计数（0）。
- 扫描：00-index §6 → 0 hits。

## 7. 判定

**PASS**：flags 2/2、V-01 app 3/3 在指定设备与 APK 上确实执行通过；V-01 收口语义沿批次 37 口径维持（④ PDF/painter/restore 策略腿留 core/data 降级变体——该变体本批仍未修复，见 E-01 §6 登记，不进套件单）。
