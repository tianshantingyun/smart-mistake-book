# E-02 隔离 13 例 + Race 2 例设备复跑（隔离 FAIL 12/13；Race PASS）

| 字段 | 值 |
|---|---|
| evidence-id | E-02 |
| convention-version | 1 |
| batch | 2026-10-28-batch-29 |
| step | 二（隔离 13 例与 Race 2 例不回退） |
| gate-or-kd | T1 §4/§5 不回退判据；P-01 下限门 |
| verdict | **隔离 FAIL**：`Tests run: 13, Failures: 12`，12/12 红均因 worker warm-up 抛 `VisualMeasurementEnvironmentException`（paint flags 1283 ≠ 指纹 0）；**Race PASS**：`OK (2 tests)` |
| executed-at | 2026-10-28T22:34:00+08:00 前后（设备 UTC 14:35） |
| operator | 设备验证轨（subagent，批次 29） |
| head | `3d9372e5` |
| worktree | dirty（app visualjs 主源 T5 改动：`VisualContentMeasuring.kt` 为 M） |
| device-serial | emulator-5562（test_device / API 34 / x86_64 / PAGE_SIZE 4096） |
| apk | app `05a5b8bd…cd24d7` + app androidTest `229cd58e…6edd68`（设备侧一致） |
| apk-version | 0.2.0-offline |
| exit-code | 两条命令宿主 `EXIT=0`（非判据；判据 = summary） |
| raw-log | `build/device-track-batch29/step02-isolation.log`/`-logcat.txt`、`step02-race.log`/`-logcat.txt` |
| record-ref | 批次 29 记录（后写） |
| sanitized | 是 |

## 1. 判据

- 隔离：`OK (13 tests)`、0 failures/errors/skipped；9 类签名 0；spawn↔ready 按 pid 1:1；
  kill 全归因（四类之和 = worker 总数）、零意外杀；无存活 worker。
- Race：`OK (2 tests)`、0 失败/跳过；9 类签名 0；`Start proc .*:visual_js` = 0。
- **不得**用批次 28 的 13/13、2/2 替代本树当前证据（T1 §7.1）。

## 2. 命令（可复跑）

```bash
export MSYS_NO_PATHCONV=1
A=C:/Android/Sdk/platform-tools/adb.exe; S=emulator-5562
# 隔离
$A -s $S logcat -c
$A -s $S shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.visualjs.VisualJsIsolationInstrumentedTest \
  com.tingyun.smartmistakebook.offline.test/androidx.test.runner.AndroidJUnitRunner \
  > build/device-track-batch29/step02-isolation.log 2>&1; echo "EXIT=$?"
$A -s $S logcat -v threadtime -d > build/device-track-batch29/step02-isolation-logcat.txt
# Race
$A -s $S logcat -c
$A -s $S shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.visualjs.VisualJsHandshakeRaceInstrumentedTest \
  com.tingyun.smartmistakebook.offline.test/androidx.test.runner.AndroidJUnitRunner \
  > build/device-track-batch29/step02-race.log 2>&1; echo "EXIT=$?"
$A -s $S logcat -v threadtime -d > build/device-track-batch29/step02-race-logcat.txt
```

## 3. 隔离结果与红例

```text
Time: 7.965
Tests run: 13,  Failures: 12
INSTRUMENTATION_CODE: -1
```

12 个红例（原样名字）：

1. `workerIsIsolatedAndAProgramRoundTripsWithoutBeingKilledLater`
2. `aStaleGenerationIsRejectedAndAStaleTerminateIsANoOp`
3. `theWatchdogKillsAHungWorkerAndAFreshGenerationHasNoBusyState`
4. `theClientRebuildsAtMostOnceAfterConfirmedAbandons`
5. `aColdStartAttachBeyondTheOldConnectBudgetStillRendersTheFirstProgram`
6. `theClientRebuildsOnceAfterADeathAndTheNewWorkerIsNotBusy`
7. `aHungWorkerReleasesTheCallerWithinHardPlus500ms`
8. `aMalformedCallerRequestIsRejectedWithoutTakingTheFlightSlot`
9. `anOverBudgetRequestEnvelopeIsRejectedByTheBoundedRead`
10. `aSecondConcurrentRequestIsRejectedBusyWhileTheFirstIsInFlight`
11. `anExecuteAcquiredDuringWarmUpIsRefusedBoundedlyAndNeverWatchdogKilled`
12. `aFirstConnectTimeoutFailsHonestlyAndTheNextExecuteRetriesWithoutLatching`

唯一绿例 `aReadyTimeoutFailsHonestlyWithoutLatchingAndTheNextExecuteRebinds` 是"期望 ready 超时"的
负向用例；worker 因 R1 直接死亡也会满足该期望，**不当作正面证据**。

失败消息形态：`initial connect failed` / `the worker never became ready (warm-up failed or hung)` /
`the visual_js worker died during the handshake`——都是 worker 起不来的下游表现。

### 3.1 根因（logcat 原文，12 次同形）

```text
10-28 14:35:17.794  4376  4407 W VisualJsService: visual_js warm-up failed
10-28 14:35:17.794  4376  4407 W VisualJsService: com.tingyun.smartmistakebook.visualjs.VisualMeasurementEnvironmentException:
    measure paint flags 1283 do not match the environment fingerprint paintFlags=0
    at com.tingyun.smartmistakebook.visualjs.PaintVisualTextMeasureBackend$Companion.forEnvironment(VisualContentMeasuring.kt:460)
    at com.tingyun.smartmistakebook.visualjs.VisualContentMeasuringKt.visualContentMeasurerForService(VisualContentMeasuring.kt:491)
    at com.tingyun.smartmistakebook.visualjs.VisualJsService.warmUpOnJsExecutor$lambda$0(VisualJsService.kt:149)
    ...
10-28 14:35:17.795  4376  4407 W VisualJsService: killing :visual_js worker: warm-up failed: VisualMeasurementEnvironmentException
```

- 与 step01 的 painter 红例**同根因 R1**：`Paint()` 默认 flags = 1283（AOSP `Paint.java`：
  `Paint(){this(ANTI_ALIAS_FLAG);}` + `DEV_KERN|EMBEDDED_BITMAP|FILTER_BITMAP`），而
  `DEFAULT_PAINT_FLAGS = 0`（`VisualContentMeasuring.kt:184`），`forEnvironment` 第 459-463 行
  fail-closed 抛异常 → `warmUpOnJsExecutor` 失败 → service 自杀 → 所有依赖真实 worker 的用例红。
- 归因类别：**C（产品缺陷）**，不是环境：异常确定性、带明确数值、进程内可复现 12 次；
  同设备 Race 2/2 绿、编译器 6/6 绿、painter 20/21 绿，排除设备/装载面。
- 修复建议：同 step01 §4.3（首选显式把两侧 Paint flags 设为指纹值 0）。
  修复后必须复跑本套件取新证据；**本轨未改代码**。

### 3.2 worker 生命周期（T1 §4.2 判据）

| 指标 | 本树实测 | 判据 | 结论 |
|---|---|---|---|
| `Start proc .*:visual_js` | 14 | — | worker 总数 14 |
| `warm-up finished` | **0** | 与 spawn 1:1（批次 28 参考 17/17） | **FAIL** |
| `visual_js warm-up failed`（首行） | 12 | — | 12 个 worker 死于 R1 |
| `killing :visual_js worker` | 13 | — | 12 warm-up + 1 unbound |
| `hard deadline (1500 ms) for request` | 0 | 四类之一 | — |
| `terminate(generation` | 0 | 四类之一 | — |
| `the host unbound the visual_js worker` | 1 | 四类之一 | — |
| `due to start instr` | 0 | 四类之一 | — |
| 四类之和 | 1 | = 14 | **FAIL：13 个杀因不可归入四类** |
| ACC `isolated not needed` | 2 | 系统回收，不计杀因 | — |
| 存活 worker（`ps -A -o PID,NAME`） | 空 | 应为空 | PASS |

## 4. Race 结果

```text
Time: 1.038
OK (2 tests)
INSTRUMENTATION_CODE: -1
```

- 2/2 绿：`markDeadWinningTheHandshakeRaceIsNeverPublishedAsConnected`、
  `aDeathDuringTheRebuildHandshakeSpendsTheRebuildBudgetExactlyOnce`。
- `Start proc .*:visual_js` = 0（fake binder 面），符合判据。
- 9 类签名 0（见 step06）。Race 不构造真实测量后端，故不受 R1 影响。

## 5. 判定

- 隔离：**FAIL（不回退判据不成立）**，红因 = 本批 T5 R1 产品缺陷；不是 P-01 回退
  （用例本身未改，批次 28 源零 diff 的结论仍成立，见 T1 §3.5），而是新增环境门把 worker 打死。
- Race：**PASS**。
- **未**改任何测试、未 `@Ignore`、未缩小 `-e class`。
