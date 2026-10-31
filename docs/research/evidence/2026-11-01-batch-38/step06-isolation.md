# E-06 隔离套件（15 例：首跑 2 红 → 同 APK 复跑 15/15 绿）

| 字段 | 值 |
|---|---|
| evidence-id | E-06 |
| convention-version | 1 |
| batch | 2026-11-01-batch-38 |
| step | 步骤三·既有套件（`VisualJsIsolationInstrumentedTest`，含 K6 两方法 + P-12③ 例） |
| gate-or-kd | 批次 38 §1 三·隔离 15 不回退（批次 37 判据 §3.1 step14 同表，期望 `OK (15 tests)`） |
| verdict | PARTIAL→**PASS（以复跑轮为准）**：首跑 13/15（2 红，环境归因 D），复跑 15/15 绿（同一 APK sha） |
| executed-at | 首跑 2026-11-01T04:18+08:00（step14）；复跑 2026-11-01T05:2x+08:00（step14b） |
| operator | 设备验证轨（批次 38 工作流子代理） |
| head | `dcfdc5b5` |
| device-serial / avd / api / abi / page-size | emulator-5562 / test_device / 34 / x86_64 / 4096 |
| apk | app androidTest（E-01 §3 同包） |
| apk-sha256 | `33e1d6a2…161fa`（两轮同一份） |
| exit-code | 两轮 adb 层均 0 |
| raw-log | `build/device-track-batch38/step14-isolation.log` + logcat；`step14b-isolation-rerun.log` + logcat |
| record-ref | `docs/research/2026-11-01-batch-38-record.md` 设备节 |
| sanitized | 是（00-index §6） |

## 1. 目的与判据

既有 15 例不回退（批次 37 同表 15 例全绿为基线），含 R-04 K6 两方法（`workerIsIsolatedAndAProgramRoundTripsWithoutBeingKilledLater`、`theWatchdogKillsAHungWorkerAndAFreshGenerationHasNoBusyState`）与 P-12③ 例（`anExplicitRetryOfAnInjectedFusedProgramRendersAndReleasesTheFuse`）。判据 = `OK (15 tests)` == 构建前计数 15，九类崩溃签名 0，不放宽任何断言。

## 2. 命令（可复跑）

```bash
export MSYS_NO_PATHCONV=1; A=C:/Android/Sdk/platform-tools/adb.exe; S=emulator-5562
$A -s $S logcat -c
$A -s $S shell am instrument -w -r -e class com.tingyun.smartmistakebook.visualjs.VisualJsIsolationInstrumentedTest \
  com.tingyun.smartmistakebook.offline.test/androidx.test.runner.AndroidJUnitRunner
$A -s $S logcat -v threadtime -d
```

- 复跑轮 = 完整套件重跑（同 APK 同命令），非红例过滤。

## 3. 结果摘要

| 指标 | 首跑 step14 | 复跑 step14b | 判据 |
|---|---|---|---|
| tests run | 15 | 15 | =构建前计数 15 |
| failures | **2** | **0** | 期望 0 |
| skipped/assumption | 0 | 0 | 期望 0 |
| `OK (N)` / Time | FAILURES（Time 104.9 s） | **OK (15 tests)**，Time 89.25 s | OK (15) |
| 签名命中（九类） | 0 | 0 | 期望 0 |
| watchdog kill | 7 = **5 设计杀 + 2 非设计** | **5（全部设计杀，15/15 绿本身即证）** | 设计杀机制由 issue-240/watchdog 用例按设计触发 |

## 4. 首跑红例（有界摘录）

```text
Error in anExplicitRetryOfAnInjectedFusedProgramRendersAndReleasesTheFuse(...):
java.lang.AssertionError: the explicit retry must render through the isolated worker,
  got Rejected(reason=HARD_DEADLINE_EXCEEDED, diagnostics=visual_js worker died: binder died)
Error in aColdStartAttachBeyondTheOldConnectBudgetStillRendersTheFirstProgram(...):
java.lang.AssertionError: the first execution after a 7600 ms cold-start attach was
  misclassified as a structural failure: Rejected(reason=HARD_DEADLINE_EXCEEDED,
  diagnostics=visual_js worker died: binder died)
```

logcat 同窗两非设计杀：pid 11260（P-12③ 的显式重试执行，ready 20:18:34.261 → kill 20:18:36.675）与 pid 11505（冷启动首执行，ready 20:19:24.300 → kill 20:19:25.864）——均与服务端 L2 看门狗 1500 ms 超限一致（E-02 §4 同签名）。首跑其余 13 例含 K6 两方法全绿；两个失败用例的程序均为**纯几何**（无 label/度量路径，`pureGeometryRequest` 形状）。

## 5. 失败归因

**D（旧包/环境）**，同 E-02 §5 证据链：①非确定分布——同套件内同形状执行大量通过（K6 往返、重建用例的 fresh 渲染等）；②同 APK 复跑 15/15 全绿（含两个红例原方法）；③两红例均为"执行时长突刺 >1500 ms"单一形状，且与宿主 4 模拟器高负载窗（loadavg 7.36）重合；④非 A（夹具两轮零改动）、非 B（失败执行不触他轨改动面——纯几何不经度量）、非 C（13/15 + 复跑 15/15 同树同包通过，若为产品回归应可稳定复现）。设计杀与非设计杀的区分依据：复跑轮（全绿）含 5 次 kill = 该套件设计面基数；首跑多出的 2 次恰对应两个失败用例的 worker。

## 6. 脱敏与去噪

- 删除：无；保留：两红例断言原文、kill 行、spawn/died 计数（首跑 spawn=20/died=6；复跑 spawn=19/died=5）。
- 扫描：00-index §6 → 0 hits。

## 7. 判定

**PASS（以 step14b 复跑轮为准）**：15/15 绿（含 K6 两方法 + P-12③ + 冷启动 7.6 s 首执行 + watchdog/重建设计杀用例），九类签名 0；首跑 2 红按 D（环境）归因登记并被复跑取代。未覆盖项：无。
