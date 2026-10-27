# E-01 VisualJs 隔离套件全量（A-14 夹具修复 + R15 修复后首验；含批次 26 T1 新门用例）

| 字段 | 值 |
|---|---|
| evidence-id | E-01 |
| convention-version | 1 |
| batch | 2026-10-27-batch-26 |
| step | 一（隔离套件全量） |
| gate-or-kd | 隔离面 / A-14 复跑 / R15 复跑 / KD-66 门首轮（套件轮） / KD-76 ⑤（注入形状复跑） / §64 队列负向复跑 |
| verdict | **PASS（Isolation 13/13 + Race 2/2 全绿）** |
| executed-at | 2026-10-27T07:19:00+08:00 |
| operator | 设备验证轨（subagent，批次 26） |
| head | 405c83739cfa5385aa7652ef61f3488f953c66ce |
| worktree | dirty（他线在飞；含批次 26 T1 未提交落层） |
| device-serial | emulator-5554（沉降态：up 3:54、load 0.03） |
| device-avd | test_device |
| device-api | 34 |
| device-abi | x86_64 |
| device-page-size | 4096 |
| apk | `app/build/outputs/apk/strictOffline/debug/app-strictOffline-debug.apk` + `app/build/outputs/apk/androidTest/strictOffline/debug/app-strictOffline-debug-androidTest.apk` |
| apk-sha256 | app `758c0caf217f8bd3a3aaeba1c686b69c83947edce1b07fa115e9ffb8c58406c9`；test `cde7e27b9a6c0ab90db06f05e6f62465c729e220593b262980f374cc9becb293`（设备侧 `pm path`+`sha256sum` 逐字节一致） |
| apk-version | 0.2.0-offline |
| exit-code | 0（adb 进程退出码；判据是 summary） |
| raw-log | `build/device-track-batch26/step01-isolation.log`（191 行）、`step01-race.log`（37 行）、`step01-isolation-logcat.txt`（1,922 行）、`step01-install-app.log`/`step01-install-test.log`（各 2 行） |
| record-ref | 批次 26 记录（由记录员后写） |
| sanitized | 是（§6；扫描见 00-index §6） |

## 1. 目的与判据

批次 25 两项修复（A-14 夹具缺陷修复 + R15 旗标竞态裁定修复）与批次 26 T1 新门用例落层后的
隔离面全量设备执行：

1. `VisualJsIsolationInstrumentedTest` 全量 **13 例**（批次 25 的 12 例 + T1 新增
   `aHungWorkerReleasesTheCallerWithinHardPlus500ms`，`VisualJsIsolationInstrumentedTest.kt:373`），
   预期全绿——**批次 25 红例 `aMalformedCallerRequestIsRejectedWithoutTakingTheFlightSlot`
   （A-14，夹具复用已关闭 pipe）修复后必须转绿**；
2. `VisualJsHandshakeRaceInstrumentedTest`（fake-binder 夹具，不启动真实 `:visual_js`，不作
   隔离门证据）2 例，预期全绿（R15 修复后复跑）；
3. 红则抓栈并归因（本批/他线/环境）。

本步不修改任何源代码。

## 2. 命令（可复跑）

```bash
A=C:/Android/Sdk/platform-tools/adb.exe
export MSYS_NO_PATHCONV=1
$A -s emulator-5554 install -r -t app/build/outputs/apk/strictOffline/debug/app-strictOffline-debug.apk
$A -s emulator-5554 install -r -t app/build/outputs/apk/androidTest/strictOffline/debug/app-strictOffline-debug-androidTest.apk
$A -s emulator-5554 shell "pm path com.tingyun.smartmistakebook.offline | sed 's/package://'"   # + sha256sum
$A -s emulator-5554 logcat -c
$A -s emulator-5554 shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.visualjs.VisualJsIsolationInstrumentedTest \
  com.tingyun.smartmistakebook.offline.test/androidx.test.runner.AndroidJUnitRunner
$A -s emulator-5554 shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.visualjs.VisualJsHandshakeRaceInstrumentedTest \
  com.tingyun.smartmistakebook.offline.test/androidx.test.runner.AndroidJUnitRunner
$A -s emulator-5554 logcat -v threadtime -d > build/device-track-batch26/step01-isolation-logcat.txt
```

- 工作目录：仓库根；两个 APK 均 `Success`；设备侧哈希与产物逐字节一致
- 运行耗时：isolation `Time: 59.33`；race `Time: 1.044`

## 3. 结果摘要

| 指标 | Isolation（13 例） | Race（2 例） | 判据 |
|---|---|---|---|
| tests run | 13 | 2 | 13 / 2 |
| failures | **0** | 0 | 0 |
| skipped | 0 | 0 | 0 |
| summary | `OK (13 tests)` | `OK (2 tests)` | — |
| `INSTRUMENTATION_STATUS_CODE: 0` 计数 | 13 | 2 | 13 / 2 |
| 8 类崩溃签名命中 | 0 | 0（共用 logcat） | 0 |
| `death was not confirmed` 告警 | 0 | 0 | 0 |

逐例（isolation 13 例全部 `STATUS_CODE: 0`；执行序取自 logcat `TestRunner: started` 行）：

```text
aHungWorkerReleasesTheCallerWithinHardPlus500ms                        => 0  # T1 新门用例（KD-66）
theClientRebuildsAtMostOnceAfterConfirmedAbandons                      => 0
aReadyTimeoutFailsHonestlyWithoutLatchingAndTheNextExecuteRebinds      => 0  # KD-76 ⑤
anOverBudgetRequestEnvelopeIsRejectedByTheBoundedRead                  => 0
aMalformedCallerRequestIsRejectedWithoutTakingTheFlightSlot            => 0  # A-14 红例修复后转绿
aStaleGenerationIsRejectedAndAStaleTerminateIsANoOp                    => 0
aFirstConnectTimeoutFailsHonestlyAndTheNextExecuteRetriesWithoutLatching => 0  # KD-76 ⑤
aSecondConcurrentRequestIsRejectedBusyWhileTheFirstIsInFlight          => 0
anExecuteAcquiredDuringWarmUpIsRefusedBoundedlyAndNeverWatchdogKilled  => 0
theClientRebuildsOnceAfterADeathAndTheNewWorkerIsNotBusy               => 0
workerIsIsolatedAndAProgramRoundTripsWithoutBeingKilledLater           => 0
theWatchdogKillsAHungWorkerAndAFreshGenerationHasNoBusyState           => 0
aColdStartAttachBeyondTheOldConnectBudgetStillRendersTheFirstProgram   => 0
# Race（R15 修复后复跑，2/2）
aDeathDuringTheRebuildHandshakeSpendsTheRebuildBudgetExactlyOnce        => OK
markDeadWinningTheHandshakeRaceIsNeverPublishedAsConnected              => OK
```

签名扫描（对 `step01-isolation-logcat.txt`，两轮 instrument 共用）：

```bash
for s in "FATAL EXCEPTION" "ANR in" "NoSuchMethodError" "NoClassDefFoundError" \
         "UnsatisfiedLinkError" "SIGSEGV" "CANNOT LINK" "failed to map segment" \
         "death was not confirmed"; do ... grep -c ...; done
# 全部 0
```

## 4. 关键输出（有界）

```text
# isolation 尾部
Time: 59.33
OK (13 tests)
INSTRUMENTATION_CODE: -1

# race 尾部
Time: 1.044
OK (2 tests)
INSTRUMENTATION_CODE: -1
```

**T1 门用例时间轴**（本套件轮，`step01-isolation-logcat.txt:595-729`）：

```text
23:19:36.775 TestRunner: started: aHungWorkerReleasesTheCallerWithinHardPlus500ms
23:19:36.811 ActivityManager: Start proc 6601 (:visual_js, u0i15)
23:19:37.620 VisualJsService: visual_js warm-up finished; worker ready for requests
23:19:39.419 VisualJsService: hard deadline (1500 ms) for request 747412e7-…; killing the worker
23:19:39.451 ActivityManager: Start proc 6645 (:visual_js, u0i16)   # 死亡后 32 ms 重建
23:19:40.163 VisualJsService: visual_js warm-up finished (6645)
23:19:40.331 TestRunner: finished: aHungWorkerReleasesTheCallerWithinHardPlus500ms  # 绿
```

## 5. 失败 / 跳过 / 异常

无失败、无跳过、无崩溃签名。要点：

1. **批次 25 唯一红例（A-14）修复后设备转绿**：`aMalformedCallerRequestIsRejectedWithoutTakingTheFlightSlot`
   `STATUS_CODE: 0`——夹具改新开 pipe 后，三个负向子断言（含批次 25 无设备证据的第三个
   "null 回调 → BAD_REQUEST 不占在飞槽"子断言）本轮全部在设备上真实执行并通过。
2. **T1 新门用例套件轮绿**（详见 E-02 独立登记；本轮为其第 1 次独立绿）。
3. **R15 修复后 Race 2/2 复绿**（批次 25 已绿，本轮确认修复未引入回归）。
4. 既有 10 例零回归。

## 6. 脱敏与去噪

- 删除：adb 安装进度行。
- 保留：全部逐例状态、summary、崩溃签名零计数、门用例时间轴。
- 扫描：见 `00-index.md` §6。二进制/截图：无。

## 7. 判定

**PASS（隔离面）**：`VisualJsIsolationInstrumentedTest` 13/13 + `VisualJsHandshakeRaceInstrumentedTest`
2/2 全绿；A-14 红例修复后转绿（用例级设备证据补齐）、R15 复跑绿、T1 门用例首轮绿、
warm-up/ready 与 §64 队列负向复跑零回归。KD-66 关闭判定见 E-02；JS 仍不广告（本批纪律）。

## 8. 原始全量（不入仓）

- 路径：`build/device-track-batch26/step01-isolation.log`（191 行）、`step01-race.log`（37 行）、
  `step01-isolation-logcat.txt`（1,922 行）、`step01-install-app.log`、`step01-install-test.log`
- 生成命令：见 §2。`build/` 被 `.gitignore` 忽略；关闭判据只认本文件。
