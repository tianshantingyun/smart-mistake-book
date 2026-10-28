# E-01 SceneV1 修复后复跑：隔离套件 13 + Race 2（app androidTest）+ worker/签名取证

| 字段 | 值 |
|---|---|
| evidence-id | E-01 |
| convention-version | 1 |
| batch | 2026-10-28-batch-28 |
| step | 一（SceneV1 修复后全绿复跑·app 侧）+ 二（T1 判据 1/2/5 核销） |
| gate-or-kd | SceneV1 设备门（批次 27 记录 §"修复后设备复验 UNVERIFIED" 的解除面）/ KD-66 复验 / KD-76 ⑤ 行为层复验 |
| verdict | **PASS**：Isolation `OK (13 tests)`（批次 27 10 红→全绿）、Race `OK (2 tests)`；9 签名 ×2 套件全 0；17/17 worker spawn→ready 1:1；17 个 kill 事件全归因、零意外杀 |
| executed-at | 2026-10-28T07:26:00+08:00 前后（设备时钟 UTC 23:30–23:31） |
| operator | 设备验证轨（subagent，批次 28） |
| head | 8116ad98 |
| worktree | dirty（他线在飞） |
| device-serial | emulator-5554（test_device / API 34 / x86_64 / PAGE_SIZE 4096） |
| apk | app debug `9ad2cd73…47329aa` + app androidTest `cde7e27b…9becb293`（设备侧 sha256 实测一致，`step00` §3） |
| apk-version | 0.2.0-offline |
| exit-code | 0 / 0 |
| raw-log | `build/device-track-batch28/`：`step01-isolation.log`（隔离 stdout）、`step01-isolation-logcat.txt`（1,268 行）、`step01-race.log`、`step01-race-logcat.txt`（665 行） |
| record-ref | 批次 28 记录（记录员后写） |
| sanitized | 是（本文件 §6） |

## 1. 目的与判据

批次 27 设备隔离套件 13 例 10 红（全部同因 = compile 接线 `$` 直插缺陷；`evidence/2026-10-27-batch-27/step05-isolation-suite-backup.md` §3）。修复 F1（三参经 `jsStringLiteral` 编码为 JS 字符串字面量）与 F2（原型链逃逸负向钉）已在树。本步判据：**同套件同设备复跑 `OK (13 tests)` 全绿**（工单一），Race 2 例保持绿不回退，且 9 类崩溃签名 0、worker spawn→ready 配对、kill 全归因（T1 判据 5）。

## 2. 命令（可复跑）

```bash
export MSYS_NO_PATHCONV=1
A=C:/Android/Sdk/platform-tools/adb.exe
$A -s emulator-5554 logcat -c
$A -s emulator-5554 shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.visualjs.VisualJsIsolationInstrumentedTest \
  com.tingyun.smartmistakebook.offline.test/androidx.test.runner.AndroidJUnitRunner
$A -s emulator-5554 logcat -v threadtime -d > build/device-track-batch28/step01-isolation-logcat.txt
$A -s emulator-5554 logcat -c
$A -s emulator-5554 shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.visualjs.VisualJsHandshakeRaceInstrumentedTest \
  com.tingyun.smartmistakebook.offline.test/androidx.test.runner.AndroidJUnitRunner
$A -s emulator-5554 logcat -v threadtime -d > build/device-track-batch28/step01-race-logcat.txt
```

- 退出码：隔离 `EXIT=0`、Race `EXIT=0`。
- 前置：Node 台先跑（`node tools/visual_compiler/run_tests.mjs` → `89 passed, 0 failed`，EXIT=0；T1 判据 7 前置，本批设备轨实跑）。

## 3. 结果摘要

| 指标 | 值 | 判据 |
|---|---|---|
| Isolation tests | **13 run / 0 failures / `OK (13 tests)`** / Time 61.749 | 期望 13 全绿（批次 27 同套件 `Tests run: 13, Failures: 10`） |
| Race tests | **2 run / 0 failures / `OK (2 tests)`** / Time 1.078 | 期望 2 全绿（批次 27 已绿，不回退） |
| 9 类崩溃签名（两套件 logcat） | isolation 9×0；race 9×0 | 期望全 0 |
| worker spawn→ready | **17 Start proc ↔ 17 warm-up finished，按 pid 1:1** | 期望全配对 |
| kill 事件归因 | 17/17 归因（§4），意外杀 0 | 期望 0 意外 |
| `INSTRUMENTATION_CODE` | -1 / -1 | 非判据（AndroidJUnitRunner 恒 RESULT_OK，R4 §8.1） |

## 4. 关键输出（有界）

隔离套件收尾（`step01-isolation.log`）：

```text
Time: 61.749
OK (13 tests)
INSTRUMENTATION_CODE: -1
```

worker 生命周期样例（`step01-isolation-logcat.txt:1` 起首个 spawn； AMS 行 + ready 行）：

```text
10-27 23:30:47.799   544   576 I ActivityManager: Start proc 15339:com.tingyun.smartmistakebook.offline:visual_js:com.tingyun.smartmistakebook.visualjs.VisualJsService/u0i57 for service {…VisualJsService}
10-27 23:30:48.654 15339 15357 I VisualJsService: visual_js warm-up finished; worker ready for requests
```

spawn 集合 {15339…15735} 共 17 个 pid，`warm-up finished` 同集合 17 个，diff 仅测试进程 15298（`Start proc 15298:…offline/u0a214 for added application`，无 warm-up 行属正常）→ **17/17 全配对**。

kill 全量归因（`grep -n "VisualJsService: killing\|hard deadline" step01-isolation-logcat.txt` 全 21 行，按类归并）：

| 类别 | 次数 | pid | 归因 |
|---|---|---|---|
| `watchdog hard deadline`（先 `hard deadline (1500 ms) for request …`） | 5 | 15339、15562、15609、15631、15688 | 全部负控注入测试（挂死夹具；请求名含 `in-flight-1`、`issue-240` 等注入标识）→ KD-66 hard+500ms 门复验载体 |
| `terminate(generation=…) accepted` | 4 | 15382、15407、15426、15735 | 用例显式 terminate（stale-terminate no-op 例与夹具收尾） |
| `the host unbound the visual_js worker` | 7 | 15450、15475、15499、15523、15543、15657、15711 | 夹具收尾 unbind（每例一 worker） |
| AMS `due to start instr` | 1 | 15107 | instrumentation 起跑时杀旧 app 进程（非 visual_js worker） |
| AMS `isolated not needed`（对 terminate 后的清理） | 12 | — | 系统回收，非杀因 |

→ 5+4+7+1 = 17 = worker 总数，**零意外杀**；`has died` 5 行均对应 watchdog 负控。

Race 套件（`step01-race.log`）：`Time: 1.078` / `OK (2 tests)`，logcat 9 签名全 0、无 worker spawn（fake binder 面）。

## 5. 失败 / 跳过 / 异常

无失败无跳过。13 例中批次 27 的 10 红例（`aHungWorkerReleasesTheCallerWithinHardPlus500ms`、`theClientRebuildsAtMostOnceAfterConfirmedAbandons`、`anOverBudgetRequestEnvelopeIsRejectedByTheBoundedRead`、`aMalformedCallerRequestIsRejectedWithoutTakingTheFlightSlot`、`aStaleGenerationIsRejectedAndAStaleTerminateIsANoOp`、`anExecuteAcquiredDuringWarmUpIsRefusedBoundedlyAndNeverWatchdogKilled`、`theClientRebuildsOnceAfterADeathAndTheNewWorkerIsNotBusy`、`workerIsIsolatedAndAProgramRoundTripsWithoutBeingKilledLater`、`theWatchdogKillsAHungWorkerAndAFreshGenerationHasNoBusyState`、`aColdStartAttachBeyondTheOldConnectBudgetStillRendersTheFirstProgram`）逐一转绿；3 绿例（两注入夹具 + BUSY 负向）保持绿。

## 6. 脱敏与去噪

- 删除：无（命令直出文件，未做行删；重复进度行不存在）。
- 保留：全部 FATAL/ANR/签名行（0 命中）、kill 行全量、chatty/lost 行未过滤。
- 扫描：见 `00-index.md` §6（目录级四则扫描 + 人工通读）。
- 二进制/截图：无。

## 7. 判定

**PASS**：断言在指定设备/产物上真实执行并成立——隔离 13/13、Race 2/2、签名 18 项全 0、17/17 spawn→ready、17/17 kill 归因。批次 27 记录中"隔离套件相对批次 26 的 13/13 属真实回退"自此解除（本步 supersedes `evidence/2026-10-27-batch-27/step05-isolation-suite-backup.md` 的隔离红判定与 `00-index.md` §3 step05 行）。KD-66 的 hard+500ms 用例在新源上重验绿（关闭状态无 reopen 条件触发）；KD-76 ⑤ 行为层证据同轮刷新（③/29.5s 链/arm64 仍 UNVERIFIED，open 维持）。

## 8. 原始全量（不入仓）

- `build/device-track-batch28/step01-isolation.log`（~1,100 行）/ `step01-isolation-logcat.txt`（1,268 行）/ `step01-race.log` / `step01-race-logcat.txt`（665 行）。
- 生成命令：见 §2；`build/` 被 `.gitignore:15` 忽略，关闭判据只认本文件。
