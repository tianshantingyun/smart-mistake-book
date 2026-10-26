# E-01e VisualJs 隔离：冷启动沉降后复跑（产品证据）

| 字段 | 值 |
|---|---|
| evidence-id | E-01e |
| convention-version | 1 |
| batch | 2026-10-26-batch-23 |
| step | T1（冷启动沉降后的控制复跑） |
| gate-or-kd | 设备功能门 / KD-66 |
| verdict | FAIL（1/5，与 E-01b 一致） |
| executed-at | 2026-10-26T16:51:00+08:00 |
| operator | 设备验证轨（subagent，批次 23） |
| head | f0a145f850931887385c4bf2e1f798a0e8f2e391 |
| worktree | dirty（他线在飞；本轨未改产品源） |
| device-serial | emulator-5554 |
| device-avd | test_device |
| device-api | 34 |
| device-abi | x86_64 |
| device-page-size | 4096 |
| apk | `app/.../app-strictOffline-debug.apk`（冷启动后重装） |
| apk-sha256 | `e5ec3a89ec93ca1289f232082371e04a37c77a1c78c5c4c8ac0debc5720bb441` |
| apk-version | 0.2.0-offline |
| exit-code | 0（adb 进程退出码；非判据） |
| raw-log | `build/device-track-batch23/step01e-visualjs-isolation-postboot-settled.log`（140 行）、`step01e-logcat.txt`（982 行） |
| record-ref | `docs/research/2026-10-26-batch-23-record.md` §4 |
| sanitized | 是（§6；扫描 0 命中） |

## 1. 目的与判据

在 E-01d 冷启动后等待约 2 分钟、`/proc/loadavg` 回落到 1.72 后再跑同一全类，作为**冷启动控制后的产品判定**：区分"加载态超时"与"结构性失败"。

## 2. 命令（可复跑）

```bash
A=C:/Android/Sdk/platform-tools/adb.exe
$A -s emulator-5554 logcat -c
MSYS_NO_PATHCONV=1 $A -s emulator-5554 shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.visualjs.VisualJsIsolationInstrumentedTest \
  com.tingyun.smartmistakebook.offline.test/androidx.test.runner.AndroidJUnitRunner
$A -s emulator-5554 logcat -v threadtime -d > build/device-track-batch23/step01e-logcat.txt
```

## 3. 结果摘要

| 指标 | 值 | 判据 |
|---|---|---|
| tests run | 5 | 5 |
| failures | 1 | 期望 0 |
| skipped | 0 | — |
| `INSTRUMENTATION_CODE` | -1 | 非判据 |
| 12 类日志签名命中 | 0 | 期望 0 |

```text
theClientRebuildsAtMostOnceAfterConfirmedAbandons            -2  # "the first abandon did not confirm death"
aStaleGenerationIsRejectedAndAStaleTerminateIsANoOp          0
theClientRebuildsOnceAfterADeathAndTheNewWorkerIsNotBusy     0
workerIsIsolatedAndAProgramRoundTripsWithoutBeingKilledLater 0
theWatchdogKillsAHungWorkerAndAFreshGenerationHasNoBusyState 0
Tests run: 5,  Failures: 1
```

## 4. 关键输出（有界）

```text
08:51:00.604 ActivityManager: Start proc 4814:...visual_js.../u0i6
08:51:01.482 ActivityManager: Ignoring finishAttachApplication from uid: 99006. pid: 4814   # attach 0.88 s
...
08:51:14.106 VisualJsService: hard deadline (1500 ms) for request issue-240; killing the worker  # 设计内
...
java.lang.AssertionError: the first abandon did not confirm death
  at VisualJsIsolationInstrumentedTest.kt:210
```

## 5. 失败 / 跳过 / 异常

- 唯一红仍是 E-01c 的 abandon 结构性缺陷；attach 恢复正常（0.6–0.9 s）后其余 4 例全绿，证明 E-01d 的 5 红是加载态。
- 无 skipped；12 类签名 0 命中。

## 6. 脱敏与去噪

同 E-01。证据目录统一扫描见 `00-index.md` §6。

## 7. 判定

**FAIL（1/5，与 E-01b 一致）**。冷启动对照成立：加载态（E-01d）→ 5 红；沉降后（E-01e）→ 仅 abandon 红。产品结论仍是"dup 修复与 watchdog/重建路径成立，abandon 路径有结构性缺陷"。

## 8. 原始全量（不入仓）

- 路径：`build/device-track-batch23/step01e-visualjs-isolation-postboot-settled.log`、`step01e-logcat.txt`
- 行数：140 行 / 8.9 KiB；logcat 982 行 / 约 190 KiB
