# E-01 VisualJs 隔离进程复跑（首轮）

| 字段 | 值 |
|---|---|
| evidence-id | E-01 |
| convention-version | 1 |
| batch | 2026-10-26-batch-23 |
| step | T1（隔离进程 dup 修复后复跑） |
| gate-or-kd | 设备功能门 / KD-66 |
| verdict | FAIL |
| executed-at | 2026-10-26T16:13:00+08:00 |
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
| exit-code | 0（adb 进程退出码；**不**作为用例判定，判定看 summary） |
| raw-log | `build/device-track-batch23/step01-visualjs-isolation.log`（207 行）、`step01-logcat.txt`（1008 行） |
| record-ref | `docs/research/2026-10-26-batch-23-record.md` §4 |
| sanitized | 是（§6；扫描 0 命中） |

## 1. 目的与判据

批次 22 的 `:visual_js` 隔离进程设备门 4/4 红，根因是服务端把已完整读入的 pipe 判成失败；修复措施是 `VisualJsService.execute` 在 Binder 线程先 `request.dup()`（`app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsService.kt:191-204`）。本步验证修复后：

1. 隔离 UID 成立且纯几何程序 AIDL 往返成功（`workerUid != Process.myUid()` 且 `RESULT_OK`）；
2. 过期 generation 的 execute/terminate 被拒且 no-op；
3. issue #240 自续 job 挂死时 L2 watchdog 在硬期限后 SIGKILL 进程，新进程/新 generation 无 BUSY；
4. client 在确认死亡后最多重建一次（abandon 路径）。

## 2. 命令（可复跑）

```bash
A=C:/Android/Sdk/platform-tools/adb.exe
$A -s emulator-5554 install -r -t app/build/outputs/apk/strictOffline/debug/app-strictOffline-debug.apk
$A -s emulator-5554 install -r -t app/build/outputs/apk/androidTest/strictOffline/debug/app-strictOffline-debug-androidTest.apk
$A -s emulator-5554 logcat -c
MSYS_NO_PATHCONV=1 $A -s emulator-5554 shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.visualjs.VisualJsIsolationInstrumentedTest \
  com.tingyun.smartmistakebook.offline.test/androidx.test.runner.AndroidJUnitRunner
$A -s emulator-5554 logcat -v threadtime -d > build/device-track-batch23/step01-logcat.txt
```

- 工作目录：仓库根
- adb 退出码：0
- 耗时：约 20 s

## 3. 结果摘要

| 指标 | 值 | 判据 |
|---|---|---|
| tests run | 5 | 期望 5 |
| failures | 2 | 期望 0 |
| skipped | 0 | — |
| `INSTRUMENTATION_CODE` | -1 | 非判据（runner 固定 RESULT_OK，见 R4 §8.1） |
| 12 类日志签名命中 | 0 | 期望 0（逐项见 `build/device-track-batch23/signature-matrix.txt`） |

## 4. 关键输出（有界）

逐用例状态（`step01-visualjs-isolation.log`）：

```text
current=1 test=theClientRebuildsAtMostOnceAfterConfirmedAbandons  STATUS_CODE -2 (失败)
current=2 test=aStaleGenerationIsRejectedAndAStaleTerminateIsANoOp  STATUS_CODE 0
current=3 test=theClientRebuildsOnceAfterADeathAndTheNewWorkerIsNotBusy STATUS_CODE -2 (失败)
current=4 test=workerIsIsolatedAndAProgramRoundTripsWithoutBeingKilledLater STATUS_CODE 0
current=5 test=theWatchdogKillsAHungWorkerAndAFreshGenerationHasNoBusyState STATUS_CODE 0
...
FAILURES!!!
Tests run: 5,  Failures: 2
INSTRUMENTATION_CODE: -1
```

失败首行：

```text
java.lang.AssertionError: initial connect failed
  at VisualJsIsolationInstrumentedTest.kt:206
java.lang.AssertionError: the client did not rebuild after the worker death
  at VisualJsIsolationInstrumentedTest.kt:240
```

服务侧时间线（`step01-logcat.txt`）：

```text
08:12:52.656 ActivityManager: Start proc 20751:...:visual_js:.../u0i9 for service {...VisualJsService}
08:12:57.679 ActivityManager: Killing 20751:...:visual_js:... (adj 0): isolated not needed
   [... 该进程在 5 s 内没有 `Ignoring finishAttachApplication` 记录；超时后客户端 unbind 触发系统杀死 ...]
08:13:01.262 ActivityManager: Start proc 20806:...:visual_js.../u0i11   # client 首次连接（第 3 例）成功
08:13:03.430 VisualJsService: hard deadline (1500 ms) ... killing the worker
08:13:03.430 Process : Sending signal. PID: 20806 SIG: 9
```

## 5. 失败 / 跳过 / 异常

- `theClientRebuildsAtMostOnceAfterConfirmedAbandons`：`initial connect failed`（`:206`）。日志显示隔离进程已 fork，但 5 s 内没有 attach 完成记录；5 s 超时后客户端 unbind，系统以 `isolated not needed` 杀死。第 3 例同一客户端路径在 13 s 后首次连接成功（attach 0.6 s），复跑（E-01b）也成功 → 归因为**首轮首个隔离进程冷启动在负载下超过客户端 5 s `CONNECT_TIMEOUT_MILLIS`**（`VisualJsClientEngine.kt:808`），不是 dup 修复问题。
- `theClientRebuildsOnceAfterADeathAndTheNewWorkerIsNotBusy`：死亡后的重建也在 5 s 内未 attach（`step01-logcat.txt:822-836`），`awaitConnectedForTest` 返回 false。复跑该用例转绿（E-01b），同样归因负载下重建连接超时。
- 无 skipped；无 `FATAL EXCEPTION`/`ANR`/`UnsatisfiedLinkError` 等 12 类签名命中。

## 6. 脱敏与去噪

- 删除：logcat 中与判定无关的 GMS/媒体库心跳与重复进度行（全量仍在 `build/`）。
- 保留：进程 start/kill、watchdog 行、失败栈、`SIG: 9`。
- 扫描：见 `00-index.md` §6（0 命中）。
- 二进制/截图：无。

## 7. 判定

**FAIL（2/5）**。dup 修复本身成立（第 4 例 AIDL 往返 `RESULT_OK`；第 5 例 watchdog 真杀进程）；两红均为客户端 5 s 连接超时，首轮表现为首次隔离进程 attach 慢。是否稳定需看复跑 E-01b（复跑后剩余 1 红为 abandon 路径的结构性失败）。

## 8. 原始全量（不入仓）

- 路径：`build/device-track-batch23/step01-visualjs-isolation.log`、`step01-logcat.txt`
- 生成命令：见 §2
- 行数/大小：207 行 / 15.1 KiB；logcat 1008 行 / 151 KiB
- 说明：`build/` 被 `.gitignore:15` 忽略；关闭判据只认本文件。
