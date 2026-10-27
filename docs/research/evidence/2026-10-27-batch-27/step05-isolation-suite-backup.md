# E-05 隔离套件（Isolation 13 + Race 2）与备份抽验——Isolation 10 红（本批缺陷，设备首曝）+ Race/备份绿

| 字段 | 值 |
|---|---|
| evidence-id | E-05 |
| convention-version | 1 |
| batch | 2026-10-27-batch-27 |
| step | 五（隔离套件与备份抽验，不回退） |
| gate-or-kd | 隔离面（P-09/P-04 设备复验）+ KD-76 ⑤ 复跑 + §64 队列负向复跑 + 备份门 |
| verdict | **Isolation 13 例 10 红——全部同因 = 批次 27 T1 compile 阶段接线缺陷（设备首曝；宿主把 JSON 文本直插 JS 源，编译器实参类型错）**；3 绿（两注入夹具 + BUSY 负向，均不达 compile）；Race **2/2 绿**；备份 **34/34 绿**（不回退）；core:data 引擎套件 26 例 9 红（同因归因）；9 类崩溃签名全 0；16/16 worker ready、3 条 watchdog kill 全部归因负控注入 |
| executed-at | 2026-10-28T03:14:00+08:00（Isolation，Time 56.723）～03:42:00+08:00（备份） |
| operator | 设备验证轨（subagent，批次 27） |
| head | caa0726a02f1862371a3b2770c8c29663ba7fa02 |
| worktree | dirty（他线在飞 + 批次 27 T1–T6 落层） |
| device-serial | emulator-5554（沉降态） |
| device-avd | test_device |
| device-api | 34 |
| device-abi | x86_64 |
| device-page-size | 4096 |
| apk | debug 三件套（step00 §4） |
| apk-sha256 | app `43adac58…`、test `cde7e27b…`（=批次 26 逐字节一致）、data-test `64264e50…`（设备侧一致） |
| apk-version | 0.2.0-offline |
| exit-code | 0（adb 进程退出码；判定是 summary） |
| raw-log | `build/device-track-batch27/`：`step01-isolation.log`（815 行）、`step01-isolation-logcat.txt`（1,983 行）、`step01-race.log`、`step05-backup.log`、`attrib-engine-suite.log` |
| record-ref | 批次 27 记录（由记录员后写） |
| sanitized | 是（§8） |

## 1. 目的与判据

隔离面批次 27 源全量设备执行（不回退判据：Race 2/2 与备份 34/34 相对批次 26 E-01/E-05
不回退；Isolation 13 例预期全绿——批次 26 E-01 曾 13/13）。红则按 R1 归因树（本批/他线/
环境）处置，不放宽断言。

## 2. 命令（可复跑）

```bash
A=C:/Android/Sdk/platform-tools/adb.exe
export MSYS_NO_PATHCONV=1
$A -s emulator-5554 logcat -c
$A -s emulator-5554 shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.visualjs.VisualJsIsolationInstrumentedTest \
  com.tingyun.smartmistakebook.offline.test/androidx.test.runner.AndroidJUnitRunner
# → Tests run: 13, Failures: 10, Time: 56.723（isolation.log 815 行）
$A -s emulator-5554 logcat -v threadtime -d > step01-isolation-logcat.txt
$A -s emulator-5554 shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.visualjs.VisualJsHandshakeRaceInstrumentedTest \
  com.tingyun.smartmistakebook.offline.test/androidx.test.runner.AndroidJUnitRunner
# → OK (2 tests)
$A -s emulator-5554 shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.data.backup.BackupRestoreInstrumentedTest \
  com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner
# → OK (34 tests)
# 归因补充（工单五步之外、登记为归因证据）：
$A -s emulator-5554 shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.data.visual.QuickJsVisualExecutionEngineInstrumentedTest \
  com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner
# → Tests run: 26, Failures: 9, Time: 8.253
```

## 3. 结果摘要

### 3.1 Isolation（13 例，10 红 3 绿；失败清单按 instrument 输出序）

| # | 用例 | 结果 | 失败点 |
|---|---|---|---|
| 1 | aHungWorkerReleasesTheCallerWithinHardPlus500ms | 红 | 预热 execute（`pureGeometryRequest`）在挂死注入**之前**即 INVALID_SCENE（KDoc"负控"分支先行变红） |
| 2 | theClientRebuildsAtMostOnceAfterConfirmedAbandons | 红 | 重建后 execute 期望 Rendered，得 INVALID_SCENE |
| 3 | anOverBudgetRequestEnvelopeIsRejectedByTheBoundedRead | 红 | "after-over-budget" 合法请求期望 OK，得 INVALID_SCENE（超预算负向子断言本身通过） |
| 4 | aMalformedCallerRequestIsRejectedWithoutTakingTheFlightSlot | 红 | "after-malformed" 合法请求期望 OK，得 INVALID_SCENE（三个 BAD_REQUEST 子断言本身通过） |
| 5 | aStaleGenerationIsRejectedAndAStaleTerminateIsANoOp | 红 | "live-after-stale-terminate" 期望 OK，得 INVALID_SCENE（STALE/terminate 子断言通过） |
| 6 | anExecuteAcquiredDuringWarmUpIsRefusedBoundedlyAndNeverWatchdogKilled | 红 | ready 后 execute 期望 OK，得 INVALID_SCENE（warm-up 有界子断言通过） |
| 7 | theClientRebuildsOnceAfterADeathAndTheNewWorkerIsNotBusy | 红 | 重建后 fresh 请求期望 Rendered，得 INVALID_SCENE（死亡→重建→熔断拍全通过） |
| 8 | workerIsIsolatedAndAProgramRoundTripsWithoutBeingKilledLater | 红 | `:134` "first execution" 期望 OK，得 INVALID_SCENE——**其前 `:123` 的 `workerUid() != myUid` 断言已执行并通过** |
| 9 | theWatchdogKillsAHungWorkerAndAFreshGenerationHasNoBusyState | 红 | kill/重建拍全通过，fresh execute 期望 Rendered 得 INVALID_SCENE |
| 10 | aColdStartAttachBeyondTheOldConnectBudgetStillRendersTheFirstProgram | 红 | 7.6 s 迟到 attach 后首 execute 期望 Rendered，得 INVALID_SCENE（首连预算行为本身成立） |
| — | aFirstConnectTimeoutFailsHonestlyAndTheNextExecuteRetriesWithoutLatching | **绿** | 注入夹具（attach 推迟 16 s），不达 compile |
| — | aReadyTimeoutFailsHonestlyWithoutLatchingAndTheNextExecuteRebinds | **绿** | fake binder，不 spawn 真实 worker |
| — | aSecondConcurrentRequestIsRejectedBusyWhileTheFirstIsInFlight | **绿** | BUSY 断言在 ready 门/watchdog 路径内，不达 compile |

统一失败签名（10 例全部一致）：

```text
Rejected(reason=INVALID_SCENE, diagnostics=scene rejected in phase compile:
SceneCompileError: SCENE_FIELD_TYPE: __visualCompile expects three JSON text arguments
    at SceneCompileError (visual-compiler.js:71:26)
    at fail (visual-c…
```

### 3.2 归因（R1 归因树：本批）

- **缺陷点（读码定位，本轨未改）**：`core/data/src/main/kotlin/com/tingyun/smartmistakebook/
  core/data/visual/QuickJsVisualExecutionEngine.kt:751`
  `js.evaluate<String>("__visualCompile($sceneJson, $metricsJsonText, $expectJson)")`——
  三个 JSON **文本**被未加引号地直插 JS 源，编译器收到的实参是 JS 对象值；而编译器唯一
  暴露面契约是三个 JSON 文本参数（`core/data/src/main/resources/visual/visual-compiler.js:2`
  与 `:1079-1081` 的 `typeof !== 'string'` 门，`fail('SCENE_FIELD_TYPE', '__visualCompile
  expects three JSON text arguments')`）→ 全部折 `INVALID_SCENE`。
- **为何此前未绿过/未红过**：Node 测试台（`tools/visual_compiler/run_tests.mjs`，84 断言）
  直接调编译器导出面、不经过该 Kotlin 接线；JVM 单测门不执行真机 QuickJS 路径；本步为
  compile 接线的**首次真机执行**（T1 轨自登记"Kotlin 侧 UNVERIFIED 归批次双门"，双门不跑
  设备）。render 阶段（`:390-393`）直插对象字面量是**设计内**（`visual.render(input, metrics)`
  吃值），compile 阶段复用了同款插值形态但契约是文本——缺陷为本批 T1 新增代码，**非环境、
  非他线**。
- **交叉证（非夹具问题）**：core:data 引擎套件（T1/T3 本批升级的夹具）26 例 9 红，首个
  红点 `QuickJsVisualExecutionEngineInstrumentedTest.kt:626 assertTrue(first is
  VisualExecutionResult.Rendered)`（WRITER 程序的首次 execute）——同一 INVALID_SCENE 形状，
  排除"app 套件夹具过时"的替代解释。
- **影响面**：全部真实 execute 路径（Rendered 不可能在任何变体产生）直至修复；隔离进程/
  装载/ready/kill/传输/预算门不受影响（下述 §3.3 全绿面）。

### 3.3 绿面（回归判据全部成立）

| 判据 | 结果 |
|---|---|
| Race 套件（R15 修复后复跑，fake-binder） | **OK (2 tests)**（Time 1.0 级） |
| 备份抽验（不回退） | **OK (34 tests)**（=批次 26 E-05 同数，不回退） |
| worker 生命周期 | 16/16 spawn 到达 ready（`warm-up finished` ×16）；批次 27 T2 度量器探针随 warm-up 全部成功 |
| watchdog 硬杀 | 3 条 `hard deadline (1500 ms) for request …` kill 行全部归因负控注入：`in-flight-1`（用例 #—绿）/`663f3c3f…`（用例 7）/`issue-240`（用例 9）；`aHungWorker` 因预热先红未及注入（§3.1 #1）；**零意外杀** |
| kill 原因分布 | `watchdog hard deadline` ×3、`the host unbound` ×7、`terminate(generation=…) accepted` ×5——全部为设计内生命周期回收 |
| 崩溃签名扫描（两份 logcat，9 类） | `FATAL EXCEPTION`/`ANR in`/`NoSuchMethodError`/`NoClassDefFoundError`/`UnsatisfiedLinkError`/`SIGSEGV`/`CANNOT LINK`/`failed to map segment`/`death was not confirmed` **全部 0** |

### 3.4 红绿判定边界（不放宽声明）

10 红例的失败断言全部是"合法请求应得 `RESULT_OK`/`Rendered`"——被测行为本身（BAD_REQUEST、
超预算、STALE、BUSY、watchdog、重建、loading 不锁存）的子断言**在被红中断前均逐条通过**；
无任何断言被放宽或跳过。3 绿例绿在"不达 compile 门"的注入路径，不构成 compile 面证据。

## 4. 关键输出（有界）

```text
# isolation 尾部
Time: 56.723
FAILURES!!!
Tests run: 13,  Failures: 10
INSTRUMENTATION_CODE: -1

# race 尾部
OK (2 tests)

# backup 尾部
OK (34 tests)

# 统一失败签名（10 例同文）
Rejected(reason=INVALID_SCENE, diagnostics=scene rejected in phase compile:
SceneCompileError: SCENE_FIELD_TYPE: __visualCompile expects three JSON text arguments …)
```

## 5. 修复与复跑（归批次修复步；本轨零源码改动）

- 缺陷定位与最小修复形态已给出（§3.2：编译器三参须以 JS 字符串字面量传递——需正确的
  JSON→JS 字面量转义；`visual.render` 两参的对象字面量插值不动）。
- 修复后的设备复跑 = `00-index` §7 命令（Isolation 13 例预期全绿 + Race 2 + 引擎套件），
  按 R4 以 `stepNNb` 追加落盘。

## 6. 失败 / 跳过 / 异常

10 红全部归因本批 T1（§3.2），零环境归因、零他线归因；绿面判据零回退。本步未修改任何
断言/夹具/源代码。

## 7. 复跑入口

见 `00-index.md` §7（四条 instrument 命令原文）。

## 8. 脱敏与去噪

- 删除：安装进度行；logcat 全量留 `build/`。
- 保留：逐例结果表、统一失败签名、kill 归因、9 类零计数。
- 扫描：见 `00-index.md` §6。二进制/截图：无。

## 9. 判定

**隔离面：红（10/13，本批 T1 compile 接线缺陷，设备首曝）**——批次 26 的 13/13 在批次 27
新源上**回退**，回退原因已定位到文件行级并给出最小修复形态；**Race 2/2 与备份 34/34：
绿（不回退）**；装载/ready/kill/传输/预算/进程隔离的设备面全部保持成立（16/16 ready、
3 kill 全归因、9 类签名 0）。JS 仍不广告（KD-76 open + MVA 不成立维持，本红不减广告门
风险面）。
