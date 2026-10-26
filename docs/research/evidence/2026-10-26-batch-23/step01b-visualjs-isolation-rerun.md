# E-01b VisualJs 隔离进程复跑（第二轮，supersedes E-01）

| 字段 | 值 |
|---|---|
| evidence-id | E-01b |
| convention-version | 1 |
| batch | 2026-10-26-batch-23 |
| step | T1（隔离进程） |
| gate-or-kd | 设备功能门 / KD-66 |
| verdict | FAIL |
| executed-at | 2026-10-26T16:15:00+08:00 |
| operator | 设备验证轨（subagent，批次 23） |
| head | f0a145f850931887385c4bf2e1f798a0e8f2e391 |
| worktree | dirty（他线在飞；本轨未改产品源） |
| device-serial | emulator-5554 |
| device-avd | test_device |
| device-api | 34 |
| device-abi | x86_64 |
| device-page-size | 4096 |
| apk | `app/build/outputs/apk/strictOffline/debug/app-strictOffline-debug.apk` |
| apk-sha256 | `e5ec3a89ec93ca1289f232082371e04a37c77a1c78c5c4c8ac0debc5720bb441` |
| apk-version | 0.2.0-offline |
| exit-code | 0（adb 进程退出码；非判据） |
| raw-log | `build/device-track-batch23/step01b-visualjs-isolation-rerun.log`（140 行）、`step01b-logcat.txt`（955 行） |
| record-ref | `docs/research/2026-10-26-batch-23-record.md` §4 |
| sanitized | 是（§6；扫描 0 命中） |
| supersedes | E-01（首轮 2 红；本文件为同一命令的第二轮） |

## 1. 目的与判据

复跑整类，区分 E-01 的两红是瞬时连接超时还是稳定失败；并验证 dup 修复后的隔离往返与 watchdog 在全量类内仍成立。

## 2. 命令（可复跑）

```bash
A=C:/Android/Sdk/platform-tools/adb.exe
$A -s emulator-5554 logcat -c
MSYS_NO_PATHCONV=1 $A -s emulator-5554 shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.visualjs.VisualJsIsolationInstrumentedTest \
  com.tingyun.smartmistakebook.offline.test/androidx.test.runner.AndroidJUnitRunner
$A -s emulator-5554 logcat -v threadtime -d > build/device-track-batch23/step01b-logcat.txt
```

- 工作目录：仓库根；adb 退出码：0；耗时约 15 s。

## 3. 结果摘要

| 指标 | 值 | 判据 |
|---|---|---|
| tests run | 5 | 期望 5 |
| failures | 1 | 期望 0 |
| skipped | 0 | — |
| `INSTRUMENTATION_CODE` | -1 | 非判据 |
| 12 类日志签名命中 | 0 | 期望 0 |

## 4. 关键输出（有界）

```text
current=1 test=theClientRebuildsAtMostOnceAfterConfirmedAbandons  STATUS_CODE -2 (失败)
current=2 test=aStaleGenerationIsRejectedAndAStaleTerminateIsANoOp  STATUS_CODE 0
current=3 test=theClientRebuildsOnceAfterADeathAndTheNewWorkerIsNotBusy STATUS_CODE 0
current=4 test=workerIsIsolatedAndAProgramRoundTripsWithoutBeingKilledLater STATUS_CODE 0
current=5 test=theWatchdogKillsAHungWorkerAndAFreshGenerationHasNoBusyState STATUS_CODE 0

java.lang.AssertionError: the first abandon did not confirm death
  at VisualJsIsolationInstrumentedTest.kt:210
...
FAILURES!!!
Tests run: 5,  Failures: 1
INSTRUMENTATION_CODE: -1
```

关键时间线（`step01b-logcat.txt`）：

```text
08:15:30.934 TestRunner: started: theClientRebuildsAtMostOnceAfterConfirmedAbandons
08:15:31.662 ActivityManager: Ignoring finishAttachApplication from uid: 99016. pid: 21006  # attach 0.7 s
08:15:31.678 ActivityManager: Killing 21006:...visual_js... (adj 0): isolated not needed
08:15:32.719 TestRunner: failed: ... "the first abandon did not confirm death"
```

## 5. 失败 / 跳过 / 异常

- 唯一红例：`theClientRebuildsAtMostOnceAfterConfirmedAbandons`（`:210`，`assertTrue(engine.abandonForTest())`）。服务进程在 attach 后 16 ms 内被 `terminate(generation)` 的 SIGKILL 终止，但客户端 1 s 内没有收到 binder death → `abandonForTest()` 返回 false。根因见 E-01c（`abandon()` 先 `unlinkDeath` 再等 death）。
- E-01 的两条红中，`theClientRebuildsOnceAfterADeathAndTheNewWorkerIsNotBusy` 本轮转绿（死亡→重建→新 generation 无 BUSY 成立）；`initial connect failed` 未复现（attach 0.7 s）。
- 无 skipped；12 类签名 0 命中。

## 6. 脱敏与去噪

同 E-01。证据目录统一扫描见 `00-index.md` §6。

## 7. 判定

**FAIL（1/5）**。核心隔离能力成立：AIDL 往返、过期 generation no-op、watchdog 硬杀 + 新进程无 BUSY、死亡后重建均绿。剩余 1 红是 client `abandon()`（宿主阶段超时路径）的死亡确认结构性失败，E-01c 单独复现。

## 8. 原始全量（不入仓）

- 路径：`build/device-track-batch23/step01b-visualjs-isolation-rerun.log`、`step01b-logcat.txt`
- 行数：140 行 / 8.9 KiB；logcat 955 行 / 149 KiB
- 生成命令：见 §2
