# E-01c client abandon 死亡确认失败（单方法复现）

| 字段 | 值 |
|---|---|
| evidence-id | E-01c |
| convention-version | 1 |
| batch | 2026-10-26-batch-23 |
| step | T1（隔离进程 / L3 宿主阶段超时路径） |
| gate-or-kd | 设备功能门 / KD-66（L3 旁证） |
| verdict | FAIL |
| executed-at | 2026-10-26T16:16:00+08:00 |
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
| raw-log | `build/device-track-batch23/step01c-abandon-confirm.log`（84 行）、`step01c-logcat.txt`（739 行） |
| record-ref | `docs/research/2026-10-26-batch-23-record.md` §4 |
| sanitized | 是（§6；扫描 0 命中） |

## 1. 目的与判据

E-01b 唯一红例是 `theClientRebuildsAtMostOnceAfterConfirmedAbandons` 的 `first abandon did not confirm death`。本步单独跑该方法，排除类内测试相互影响，并把失败定位到具体代码行。

判据：`abandonForTest()` 必须在 terminate + unbind 后 1 s 内确认 binder death（契约 §4：确认旧实例终结后才允许一次重建）。

## 2. 命令（可复跑）

```bash
A=C:/Android/Sdk/platform-tools/adb.exe
$A -s emulator-5554 logcat -c
MSYS_NO_PATHCONV=1 $A -s emulator-5554 shell am instrument -w -r \
  -e class 'com.tingyun.smartmistakebook.visualjs.VisualJsIsolationInstrumentedTest#theClientRebuildsAtMostOnceAfterConfirmedAbandons' \
  com.tingyun.smartmistakebook.offline.test/androidx.test.runner.AndroidJUnitRunner
$A -s emulator-5554 logcat -v threadtime -d > build/device-track-batch23/step01c-logcat.txt
```

## 3. 结果摘要

| 指标 | 值 | 判据 |
|---|---|---|
| tests run | 1 | 1 |
| failures | 1 | 0 |
| skipped | 0 | — |
| `INSTRUMENTATION_CODE` | -1 | 非判据 |

## 4. 关键输出（有界）

```text
java.lang.AssertionError: the first abandon did not confirm death
  at com.tingyun.smartmistakebook.visualjs.VisualJsIsolationInstrumentedTest$...$1.invokeSuspend(VisualJsIsolationInstrumentedTest.kt:210)
...
Tests run: 1,  Failures: 1
INSTRUMENTATION_CODE: -1
```

设备侧（`step01c-logcat.txt`）：

```text
08:16:13.747 ActivityManager: Start proc 21256:...visual_js.../u0i23 for service {...VisualJsService}
08:16:14.390 ActivityManager: Ignoring finishAttachApplication from uid: 99023. pid: 21256
08:16:14.406 Process : Sending signal. PID: 21256 SIG: 9
08:16:14.406 ActivityManager: Killing 21256:...visual_js... (adj 0): isolated not needed
[... 客户端未在 1 s 内观察到 death；测试在 1 s 后失败 ...]
```

## 5. 失败归因（代码行）

`app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsClientEngine.kt`：

```kotlin
private suspend fun abandon(link: Link) {                     // :564
    ...
    runCatching { link.service?.terminate(link.generation) }
    link.service = null
    runCatching { context.unbindService(link.connection) }
    unlinkDeath(link)                                          // :570  ← 先摘 death recipient
    val confirmed = withTimeoutOrNull(DEATH_CONFIRM_TIMEOUT_MILLIS) {
        link.death.await()                                     // :571  ← 再等一个已无人能完成的 Deferred
    } != null
    if (!confirmed) {
        link.state = LinkState.DEATH_UNCONFIRMED
        unavailable = true                                     // :575  ← 于是永久 fail-closed
    }
    ...
}
```

- `link.death` 只在 `Link.markDead()`（`:758-765`）完成；`markDead` 由 `DeathRecipient` / `ServiceConnection` 回调触发。
- `unlinkDeath(link)`（`:646-651`）先 `binder.unlinkToDeath(...)` 并清空 `link.binder`，普通 `unbindService` 又不会回调 `onServiceDisconnected`，所以 1 s 等待必然超时；观察结果（服务确实 SIGKILL、客户端仍收不到 death）与该结构性原因一致。
- 后果：任何"宿主阶段超时 → abandon"路径都会把引擎置为 `unavailable=true` 且永不重建（`ensureConnected` 在 `:483` 直接返回 null），与测试期望的"确认死亡后重建一次"相反。
- 说明：本轨只做设备验证，未修改产品代码；修复归属主会话/对应轨。

## 6. 脱敏与去噪

同 E-01。证据目录统一扫描见 `00-index.md` §6。

## 7. 判定

**FAIL**。单方法稳定复现（E-01b 类内同样红），且有明确代码位置与设备侧 SIGKILL 对照。dup 修复不是本失败原因；这是 L3 `abandon()` 的既有实现缺陷（或测试 seam 与实现的语义冲突），应登记为新的缺陷/交主会话裁定。

## 8. 原始全量（不入仓）

- 路径：`build/device-track-batch23/step01c-abandon-confirm.log`、`step01c-logcat.txt`
- 行数：84 行 / 5.9 KiB；logcat 739 行 / 118 KiB
- 生成命令：见 §2
