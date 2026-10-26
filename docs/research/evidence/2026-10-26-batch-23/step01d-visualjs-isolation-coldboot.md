# E-01d VisualJs 隔离：冷启动后立即复跑（环境未稳定）

| 字段 | 值 |
|---|---|
| evidence-id | E-01d |
| convention-version | 1 |
| batch | 2026-10-26-batch-23 |
| step | T1（冷启动控制） |
| gate-or-kd | 设备功能门 / KD-66（环境控制，不作为产品判定） |
| verdict | FAIL（环境未稳定；不作为产品判定） |
| executed-at | 2026-10-26T16:48:00+08:00 |
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
| raw-log | `build/device-track-batch23/step01d-visualjs-isolation-coldboot.log`（381 行）、`step01d-logcat.txt`（7356 行） |
| record-ref | `docs/research/2026-10-26-batch-23-record.md` §4 |
| sanitized | 是（§6；扫描 0 命中） |

## 1. 目的与判据

按"模拟器劣化先冷启再归因"的纪律，对 emulator-5554 执行 `adb reboot`，重装 strictOffline debug 双 APK 后立即跑全类，检验 E-01 首轮的两红是否只是加载态。

## 2. 命令（可复跑）

```bash
A=C:/Android/Sdk/platform-tools/adb.exe
$A -s emulator-5554 reboot && $A -s emulator-5554 wait-for-device   # 等到 sys.boot_completed=1
$A -s emulator-5554 install -r -t app/build/outputs/apk/strictOffline/debug/app-strictOffline-debug.apk
$A -s emulator-5554 install -r -t app/build/outputs/apk/androidTest/strictOffline/debug/app-strictOffline-debug-androidTest.apk
$A -s emulator-5554 logcat -c
MSYS_NO_PATHCONV=1 $A -s emulator-5554 shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.visualjs.VisualJsIsolationInstrumentedTest \
  com.tingyun.smartmistakebook.offline.test/androidx.test.runner.AndroidJUnitRunner
```

## 3. 结果摘要

| 指标 | 值 | 判据 |
|---|---|---|
| tests run | 5 | 5 |
| failures | 5 | 期望 0 |
| skipped | 0 | — |
| `INSTRUMENTATION_CODE` | -1 | 非判据 |

失败首行：`initial connect failed`、`a stale terminate killed the current worker: status=-3`、`first execution: null … -3`、`the new process inherited the old BUSY state … -3`。

## 4. 关键输出（有界）

```text
08:47:39.406 TestRunner: started: theClientRebuildsAtMostOnceAfterConfirmedAbandons
08:47:41.104 ActivityManager: Start proc 3846:...visual_js.../u0ai9000 for service {...}
08:47:45.682 ActivityManager: Ignoring finishAttachApplication from uid: 99000. pid: 3846  # 4.6 s 后才 attach
08:47:45.812 ActivityManager: Killing 3846 ... isolated not needed                    # 客户端 5 s 超时已 unbind
08:47:46.523 ActivityManager: Start proc 3920:...visual_js.../u0i1
08:47:54.005 ActivityManager: Ignoring finishAttachApplication from uid: 99001. pid: 3920  # 7.5 s
08:47:57.163 VisualJsService: hard deadline (1500 ms) for request live-after-stale-terminate; killing the worker
...
Tests run: 5,  Failures: 5
```

## 5. 失败 / 跳过 / 异常

- 刚重启的模拟器（2.5 GB RAM、`/proc/loadavg` 5 分钟 5.25、system_server/GMS 正在启动）上，隔离进程 attach 4.6–7.5 s，超过客户端 `CONNECT_TIMEOUT_MILLIS=5_000`；同时 JS 硬期限 1500 ms 在冷启动负载下也被触发。与产品代码无关。
- 该 run **不作为**产品判定；用于证明"该 5 例门对加载态敏感"。

## 6. 脱敏与去噪

同 E-01。证据目录统一扫描见 `00-index.md` §6。

## 7. 判定

FAIL（5/5，环境未稳定）。结论：冷启动后的第一轮不可用于归因；需等系统负载回落后复跑（E-01e）。

## 8. 原始全量（不入仓）

- 路径：`build/device-track-batch23/step01d-visualjs-isolation-coldboot.log`、`step01d-logcat.txt`
- 行数：381 行 / 约 25 KiB；logcat 7356 行 / 约 1.4 MiB
