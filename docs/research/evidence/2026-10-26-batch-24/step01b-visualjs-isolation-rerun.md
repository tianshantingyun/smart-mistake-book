# E-01b VisualJs 隔离门复跑（同批第二次；产物身份 supersedes E-01）

| 字段 | 值 |
|---|---|
| evidence-id | E-01b |
| convention-version | 1 |
| batch | 2026-10-26-batch-24 |
| step | 一（隔离门，复跑） |
| gate-or-kd | 设备功能门 / KD-66（隔离 6 例） |
| verdict | PASS（6/6） |
| executed-at | 2026-10-26T22:14:10+08:00 |
| operator | 设备验证轨（subagent，批次 24） |
| head | 5bc8bdf868e1296049ab901cf1da69e136062006 |
| worktree | dirty（他线在飞；含 T1 未提交修复） |
| device-serial | emulator-5554 |
| device-avd | test_device |
| device-api | 34 |
| device-abi | x86_64 |
| device-page-size | 4096 |
| apk | `app/build/outputs/apk/strictOffline/debug/app-strictOffline-debug.apk`（21:37:29 重建件）+ `app/build/outputs/apk/androidTest/strictOffline/debug/app-strictOffline-debug-androidTest.apk` |
| apk-sha256 | app `782cebd66eb12c8656ccc5fc518600cdf63ddd8572f7a6908d143e31930173c6`；test `343b637af805798ac1dfadfe8f2ebd0c2a59f5aec390c9b6a3e8f35760e08c0e` |
| apk-version | 0.2.0-offline |
| exit-code | 0（adb 进程退出码；判据是 summary） |
| raw-log | `build/device-track-batch24/step01b-isolation-rerun.log`（93 行）、`step01b-isolation-rerun-logcat.txt`（1,065 行） |
| record-ref | `docs/research/2026-10-26-batch-24-record.md` §4 |
| sanitized | 是（§6；扫描 0 命中） |

## 1. 目的与判据

E-01 执行后，`app-strictOffline-debug.apk` 在 21:37:29 被另一条在飞构建重建（字节数
189,363,367 → 189,363,334，sha256 `53c3a453…6eba` → `782cebd6…73c6`），`visualjs` 源文件
mtime 仍为 20:07、未变。为消除"证据对应旧产物"的歧义，在**当前安装件**上按同一判据复跑整类 6 例。

判据与 E-01 §1 完全相同，不重复展开；本文件只回答"当前产物上是否仍全绿"。

> 产物身份说明：E-01 的 6/6 与 E-01b 的 6/6 是同一份 T1 源码、两次独立构建/执行。
> 两者都成立；涉及"当前盘上 APK"的引用以本文件为准。

## 2. 命令（可复跑）

```bash
A=C:/Android/Sdk/platform-tools/adb.exe
export MSYS_NO_PATHCONV=1
$A -s emulator-5554 install -r -t app/build/outputs/apk/strictOffline/debug/app-strictOffline-debug.apk
$A -s emulator-5554 logcat -c
$A -s emulator-5554 shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.visualjs.VisualJsIsolationInstrumentedTest \
  com.tingyun.smartmistakebook.offline.test/androidx.test.runner.AndroidJUnitRunner
$A -s emulator-5554 logcat -v threadtime -d > build/device-track-batch24/step01b-isolation-rerun-logcat.txt
```

- 安装：`Success`（`build/device-track-batch24/step01b-install.log`）
- 运行耗时：约 26 s（`Time: 22.208`）
- 设备侧 `pm path` + `sha256sum` = `782cebd6…73c6`，与盘上产物一致

## 3. 结果摘要

| 指标 | 值 | 判据 |
|---|---|---|
| tests run | 6 | 6 |
| failures | 0 | 0 |
| skipped | 0 | 0 |
| `OK (6 tests)` | 是 | 是 |
| `INSTRUMENTATION_CODE` | -1 | 非判据（adb 侧 exit=0） |
| 6 类崩溃签名命中 | 0 | 期望 0 |
| `death was not confirmed` 警告 | 0 | 期望 0 |

逐例状态（`1 → 0`，6/6）：

```text
theClientRebuildsAtMostOnceAfterConfirmedAbandons                        => 1, 0
aStaleGenerationIsRejectedAndAStaleTerminateIsANoOp                     => 1, 0
theClientRebuildsOnceAfterADeathAndTheNewWorkerIsNotBusy                => 1, 0
workerIsIsolatedAndAProgramRoundTripsWithoutBeingKilledLater            => 1, 0
theWatchdogKillsAHungWorkerAndAFreshGenerationHasNoBusyState            => 1, 0
aColdStartAttachBeyondTheOldConnectBudgetStillRendersTheFirstProgram    => 1, 0
```

## 4. 关键输出（有界）

```text
INSTRUMENTATION_STATUS: numtests=6
...
Time: 22.208

OK (6 tests)

INSTRUMENTATION_CODE: -1
```

## 5. 失败 / 跳过 / 异常

无失败、无跳过。与 E-01 相比耗时缩短约 1.7 s（23.945 → 22.208），两轮都在同一量级；
没有出现批次 23 冷启动时 5/5 红的加载态形状。

## 6. 脱敏与去噪

同 E-01。扫描结果见 `00-index.md` §6（0 命中）。

## 7. 判定

**PASS（6/6，当前安装件 782cebd6）**：T1 修复在 E-01 与 E-01b 两次独立执行中均成立，
批次 23 的 abandon 红未复现。

限制与 E-01 §7 相同：**不**关闭 KD-66（hard+500 ms 关闭门未覆盖）；不构成 JS 广告依据；
不证明服务端 warm-up/ready 或 #240 可终止。

## 8. 原始全量（不入仓）

- 路径：`build/device-track-batch24/step01b-isolation-rerun.log`（93 行）、`step01b-isolation-rerun-logcat.txt`（1,065 行）
- 生成命令：见 §2
- 说明：`build/` 被 `.gitignore` 忽略；关闭判据只认本文件。
