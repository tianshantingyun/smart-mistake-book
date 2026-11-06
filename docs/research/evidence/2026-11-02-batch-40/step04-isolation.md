<!-- 体积自查：约 110 行；R4 上限 400 行 / 65536 B -->
# E-40-04 isolation 诚实复跑（低负载门 + 逐方法配对 + T4 控制轮）

| 字段 | 值 |
|---|---|
| evidence-id | E-40-04 |
| convention-version | 1 |
| batch | 2026-11-02-batch-40 |
| step | 步骤四（判据 §50） |
| gate-or-kd | 判据 I50-1…I50-9；T4 归因 |
| verdict | **PARTIAL**：单臂 15/15 绿；低负载门未建立、A3 逐方法分布不符、OFF/ON 配对红集与控制轮未做；**不得**写“T4 排除” |
| executed-at | 2026-11-02T10:50–10:51+08:00（设备时钟 02:50） |
| operator | 设备验证轨 |
| head | 执行时 HEAD 已变为 `dee81ea7`（10:45:47）；APK 仍为 10:13 建造 |
| worktree | 源漂移 169 行 / 60 文件；APK sha 未变 |
| device-serial | emulator-5584（API 34 / x86_64 / 4096） |
| apk-sha256 | app test `565fc85b1edadc79e6497261976acbf8e03e1f11326fa4a6e6de9abb9a357c7b` |
| exit-code | adb-exit=0；`OK (15 tests)`；code0=15，code-2=0，code-4=0 |
| raw-log | `build/device-track-batch40/suites/suite-isolation.log`、`suite-isolation-logcat.txt` |
| record-ref | `docs/research/2026-11-02-batch-40-record.md` |
| sanitized | 是 |

## 1. 单臂结果（A1/A2）

```text
$ adb -s emulator-5584 shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.visualjs.VisualJsIsolationInstrumentedTest \
  com.tingyun.smartmistakebook.offline.test/androidx.test.runner.AndroidJUnitRunner
Time: 60.848
OK (15 tests)
```

- `INSTRUMENTATION_STATUS_CODE: 0` = 15；`-`（-2/-4）= 0；无 `@Ignore`/`assumeTrue`（源码 `grep` = 0）。
- 15 个方法名均出现在日志；A1/A2 满足。

## 2. A3 逐方法 kill 分布（与判据不符）

判据期望：`{aHungWorker:1, aSecondConcurrent:1, theClientRebuildsAtMostOnce:2, theWatchdogKills:1}`，K=5。

实测（从 `suite-isolation-logcat.txt` 按 `TestRunner: started:` 归属）：

| 方法 | watchdog hard deadline | terminate accepted | 备注 |
|---|---|---|---|
| `aHungWorkerReleasesTheCallerWithinHardPlus500ms` | 1 | 1 | terminate 为用例后清理 |
| `aSecondConcurrentRequestIsRejectedBusyWhileTheFirstIsInFlight` | 1 | 0 | 设计 |
| `theClientRebuildsAtMostOnceAfterConfirmedAbandons` | 0 | 2 | 设计（判据期望这 2 次） |
| `theWatchdogKillsAHungWorkerAndAFreshGenerationHasNoBusyState` | 1 | 0 | 设计 |
| **`theClientRebuildsOnceAfterADeathAndTheNewWorkerIsNotBusy`** | **2** | 0 | **判据集合外的 kill** |
| `anExplicitRetryOfAnInjectedFusedProgramRendersAndReleasesTheFuse` | 0 | 1 | 清理/重试路径 |
| `aColdStartAttachBeyondTheOldConnectBudgetStillRendersTheFirstProgram` | 0 | 1 | 清理 |

- watchdog kill 总数 = **5**（设计基数）；terminate accepted = **5**（`aHungWorker…` 1、`theClientRebuildsAtMostOnce…` 2、`anExplicitRetry…` 1、`aColdStart…` 1；任务下发摘要只点了前两项，属子集，非全部）；另有 8 次 `the host unbound the visual_js worker`（teardown，不计设计杀）。
- 原始依据：`suite-isolation-logcat.txt:667,699,731,760,1029,1087,1119,1180,1234,1266`（kill/terminate 行）与 `:599..1153`（`TestRunner: started:` 归属边界）。
- **A3 判定：FAIL as written**——判据要求“出现其他方法 kill 或 K≠5 → 红”；`theClientRebuildsOnceAfterADeath…` 有 2 次 watchdog kill，`aHungWorker…` 有额外 terminate。若按“仅 watchdog kill 总数=5”读，则是 5；两种读法必须由主会话裁定，不能由执行轨择一洗绿。

## 3. A4 签名/生命周期

- `warm-up failed=0`、`death was not confirmed=0`（日志全文）。
- 九类签名（FATAL/ANR/NoSuchMethod/NoClassDef/SIGSEGV/CANNOT LINK/failed to map/death）全 0；`UnsatisfiedLinkError` 也 0。
- spawn=19、warm-up finished=19、unpaired=0（`Start proc .*:visual_js` vs `warm-up finished`）。
- 套件后 `ps -A | grep com.tingyun.smartmistakebook(:visual_js)?` = `NO_ORPHAN`。

## 4. P/G、配对红集与控制轮（未做）

| 判据 | 状态 |
|---|---|
| I50-1 P1–P4 + G（load<1.0 持续 5 min、p50≤1800/p95≤6000/max≤8000/failed=0/unpaired≤1） | **未建立**：同一宿主 4 模拟器 + 他线构建/写入；无 5 分钟干净窗；guest load 仅事后读 0.12，不作门 |
| I50-5 OFF/ON 两臂逐方法配对红集 | **未做**（只有单臂；无 OFF-1/ON-1/OFF-2/ON-2） |
| I50-6 字节可核 T4 控制轮（6 处写点回退、文件级备份/还原、sha 校验） | **未做**；无 `build-control.log`、无 `T4_REVERT_APPLIED`、无 engine.control.sha256 |
| I50-7 控制轮证据 | 缺失（控制臂引擎 sha 未产生） |
| I50-9 判定 | **无判定**；T4 未排除，也不归因 D |

## 5. 结论

- 单臂 15/15 绿，且 A4 生命周期干净；这只能写“单臂绿”。
- **不能**写“isolation 通过”或“T4 排除”：A3 分布与判据不符、低负载门未建立、控制轮未做。
- 本窗执行时 HEAD 已由 KB 线改为 `dee81ea7`（10:45:47），而 APK 仍是 10:13 建造；设备结果绑定 APK，不绑定当前树。
