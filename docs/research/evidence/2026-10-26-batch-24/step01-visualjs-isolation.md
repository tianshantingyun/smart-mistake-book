# E-01 VisualJs 隔离门复跑（批次 24 T1 修复后）

| 字段 | 值 |
|---|---|
| evidence-id | E-01 |
| convention-version | 1 |
| batch | 2026-10-26-batch-24 |
| step | 一（隔离门） |
| gate-or-kd | 设备功能门 / KD-66（隔离 6 例） |
| verdict | PASS（6/6；KD-66 关闭门未因此达成，见 §7） |
| executed-at | 2026-10-26T21:33:20+08:00 |
| operator | 设备验证轨（subagent，批次 24） |
| head | 5bc8bdf868e1296049ab901cf1da69e136062006 |
| worktree | dirty（他线在飞；含 T1 未提交修复） |
| device-serial | emulator-5554 |
| device-avd | test_device |
| device-api | 34 |
| device-abi | x86_64 |
| device-page-size | 4096 |
| apk | `app/build/outputs/apk/strictOffline/debug/app-strictOffline-debug.apk` + `app/build/outputs/apk/androidTest/strictOffline/debug/app-strictOffline-debug-androidTest.apk` |
| apk-sha256 | app `53c3a453219f10352535f4b2e8d4e3b7e4f1aa4dfadd31b089d90a8328026eba`；test `343b637af805798ac1dfadfe8f2ebd0c2a59f5aec390c9b6a3e8f35760e08c0e` |
| apk-version | 0.2.0-offline |
| exit-code | 0（adb 进程退出码；判据是 summary） |
| raw-log | `build/device-track-batch24/step01-isolation.log`（93 行）、`step01-isolation-logcat.txt`（945 行） |
| record-ref | `docs/research/2026-10-26-batch-24-record.md` §4 |
| sanitized | 是（§6；扫描 0 命中） |

## 1. 目的与判据

复跑批次 24 T1 修改后的隔离类全量 6 例，判据：

1. `workerIsIsolatedAndAProgramRoundTripsWithoutBeingKilledLater` — 隔离 UID + AIDL 往返 + 过期 watchdog 不误杀；
2. `aStaleGenerationIsRejectedAndAStaleTerminateIsANoOp` — 过期 generation/terminate no-op；
3. `theWatchdogKillsAHungWorkerAndAFreshGenerationHasNoBusyState` — 1500 ms 硬期限 SIGKILL + 新进程无 BUSY；
4. `theClientRebuildsOnceAfterADeathAndTheNewWorkerIsNotBusy` — 死亡后重建一次且新实例可用；
5. `theClientRebuildsAtMostOnceAfterConfirmedAbandons` — `abandon()` 确认死亡后重建一次，重建实例真实 `execute` 返回 `Rendered`；第二次确认后不再 bind；
6. `aColdStartAttachBeyondTheOldConnectBudgetStillRendersTheFirstProgram` — attach 回调推迟 7.6 s（旧 5 s 预算下必红）时首次 `execute` 仍成功且只连接一次。

批次 23 的基线是同一类 5 例 4 绿 1 红（第 5 例 `the first abandon did not confirm death`）；批次 24 T1 修改了 `VisualJsClientEngine.abandon()` 的死亡确认与首连/重建预算（`app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsClientEngine.kt:575-642`）。本步不修改任何源代码。

## 2. 命令（可复跑）

```bash
A=C:/Android/Sdk/platform-tools/adb.exe
export MSYS_NO_PATHCONV=1
$A -s emulator-5554 install -r -t app/build/outputs/apk/strictOffline/debug/app-strictOffline-debug.apk
$A -s emulator-5554 install -r -t app/build/outputs/apk/androidTest/strictOffline/debug/app-strictOffline-debug-androidTest.apk
$A -s emulator-5554 logcat -c
$A -s emulator-5554 shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.visualjs.VisualJsIsolationInstrumentedTest \
  com.tingyun.smartmistakebook.offline.test/androidx.test.runner.AndroidJUnitRunner
$A -s emulator-5554 logcat -v threadtime -d > build/device-track-batch24/step01-isolation-logcat.txt
```

- 工作目录：仓库根
- 安装：两个 APK 均 `Success`，退出码 0
- 运行耗时：约 28 s（`Time: 23.945`）

## 3. 结果摘要

| 指标 | 值 | 判据 |
|---|---|---|
| tests run | 6 | 期望 6 |
| failures | 0 | 期望 0 |
| skipped | 0 | 期望 0 |
| `OK (6 tests)` | 是 | 是 |
| `INSTRUMENTATION_CODE` | -1 | 非判据（adb 侧 exit=0） |
| 8 类崩溃签名命中 | 0 | 期望 0 |
| `Death was not confirmed` 警告 | 0 | 期望 0（确认路径未走 fail-closed） |

逐例状态（`INSTRUMENTATION_STATUS_CODE`：1=开始，0=通过）：

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
Time: 23.945

OK (6 tests)

INSTRUMENTATION_CODE: -1
```

生成方式：`grep -E "numtests=|Time:|OK \(|INSTRUMENTATION_CODE" build/device-track-batch24/step01-isolation.log`。

签名扫描（命令与计数）：

```bash
for s in "FATAL EXCEPTION" "ANR in" "NoSuchMethodError" "NoClassDefFoundError" \
         "UnsatisfiedLinkError" "SIGSEGV" "CANNOT LINK" "failed to map segment"; do
  printf '%-24s %s\n' "$s" "$(grep -c "$s" build/device-track-batch24/step01-isolation-logcat.txt)"
done
# 全部 0
grep -c "death was not confirmed" build/device-track-batch24/step01-isolation-logcat.txt
# 0
```

## 5. 失败 / 跳过 / 异常

无失败、无跳过。本步的关键变化：

- 批次 23 红的第 5 例本次绿：`abandonForTest()` 返回真，首次重建后 `engine.execute(...)` 返回 `Rendered`（若旧实例状态泄漏会得到 `BUSY` 而红），第二次 abandon 后 `connectForTest()` 为假且 `connectionCountForTest()` 保持 2；日志中没有 `visual_js death was not confirmed within 2000 ms` 告警，说明死亡确认走的是 death 事实或 `isBinderAlive` 轮询，而不是 2 s 超时后的 fail-closed 分支。
- 第 6 例冷启动用例只推迟 `onServiceConnected` 7.6 s（`DelayedAttachContext`），真实 `bindService` 立即执行；它证明首连 15 s 预算覆盖该形状，**不**证明真实设备冷启动 attach 的实测分布（批次 23 E-01d 的 4.6–7.5 s 仍是独立实测）。

## 6. 脱敏与去噪

- 删除：adb 安装进度行；Gradle 进度行不在本步。
- 保留：全部 `INSTRUMENTATION_STATUS`（含未通过码，若有）、summary、crash 签名零计数。
- 扫描：见 `00-index.md` §6（0 命中）。
- 二进制/截图：无。

## 7. 判定

**PASS（隔离门 6/6）**：断言在 emulator-5554 / API 34 / 上述 APK 上真实执行并通过，批次 23 的 abandon 结构性红已消失。

未覆盖（不得用本步冒充）：

1. **KD-66 的关闭门仍是 hard+500 ms 设备用例**。本类的 watchdog 用例以 ≤6 s 观察 L2 SIGKILL，弱于该判据；本步只证明隔离层行为，不关闭 KD-66。
2. issue #240 的自续 job 仍会泄漏 daemon 线程（引擎侧观察型用例另见 E-02），本步不改变该结论。
3. 服务端 warm-up/ready、连接超时后的退避重试未实现，仍是 KD-76 的已知风险。
4. JS 仍不得加入 availableMethods；本步不构成广告依据。

## 8. 原始全量（不入仓）

- 路径：`build/device-track-batch24/step01-isolation.log`（93 行 / 约 6 KiB）、`step01-isolation-logcat.txt`（945 行 / 约 90 KiB）
- 生成命令：见 §2
- 说明：`build/` 被 `.gitignore` 忽略；关闭判据只认本文件。
