# E-03 步骤三·app 套件首跑（A-09/P-09/P-10/P-04-05/flags/隔离 15 + V-01 全套）

| 字段 | 值 |
|---|---|
| evidence-id | E-03 |
| convention-version | 1 |
| batch | 2026-11-01-batch-39 |
| step | 步骤三·既有套件不回退（app 模块 7 套件首跑） |
| gate-or-kd | 批 38 底册 24 套件不回退（app 半边：15/3/1/1/1/2/4） |
| verdict | **PARTIAL**：a09 3/3、p09 1/1、p10 1/1、p0405 1/1、flags 2/2 绿；isolation 9/15（**6 红**）；v01app 3/4（**1 红**）——红例归因与复跑见 E-06 |
| executed-at | 2026-11-01T10:24–10:31+08:00 |
| operator | 设备验证轨（批次 39 工作流子代理） |
| head | `a0641bb7392f380b5716afe95dd3c371dd0c7670` |
| worktree | 737 条（仅记录） |
| device-serial / avd / api / abi / page-size | emulator-5562 / test_device / 34 / x86_64 / 4096 |
| apk | app androidTest `e20d923a8e5d76190ac24f2c7e7d82a55448bba6944520c59683e9e41178b17d` |
| exit-code | 各套件 adb 层 0 |
| raw-log | `build/device-track-batch39/suite-{a09,p09,p10,p0405,flags,isolation,v01app}.log` + 同名 `-logcat.txt` |
| record-ref | `docs/research/2026-11-01-batch-39-record.md`（记录员落笔位） |
| sanitized | 是（00-index §6 统一扫描） |

## 1. 命令（可复跑）

```bash
export MSYS_NO_PATHCONV=1; A=C:/Android/Sdk/platform-tools/adb.exe; S=emulator-5562
$A -s $S logcat -c
$A -s $S shell am instrument -w -r -e class com.tingyun.smartmistakebook.visualjs.<套件名> \
  com.tingyun.smartmistakebook.offline.test/androidx.test.runner.AndroidJUnitRunner
$A -s $S shell logcat -v threadtime -d
```

## 2. 结果摘要

| 套件 | 结果 | 判据计数 |
|---|---|---|
| `VisualJsAdvertiseThenExecuteInstrumentedTest`（A-09） | **OK (3 tests)** | 3 相符 |
| `VisualJsClientFrameQueueDeviceInstrumentedTest`（P-09） | **OK (1 test)** | 1 相符 |
| `VisualJsDeathUnconfirmedFailClosedInstrumentedTest`（P-10） | **OK (1 test)** | 1 相符 |
| `VisualJsReconnectBudgetInstrumentedTest`（P-04/P-05） | **OK (1 test)** | 1 相符 |
| `VisualMeasureReplayPaintFlagsInstrumentedTest`（flags） | **OK (2 tests)** | 2 相符 |
| `VisualJsIsolationInstrumentedTest`（隔离 15） | Tests run: 15, **Failures: 6** | 15 相符；红见 §3 |
| `V01VisualChainInstrumentedTest` | Tests run: 4, Failures: 1 | 4 相符（3→4 增量为 ④ 腿）；红=③，④ 绿（E-02 §4） |

## 3. isolation 6 红首录（归因结论见 E-06）

六红堆栈同族（`suite-isolation.log` stack= 块实录）：

```text
status=-3 diagnostics=null expected:<0> but was:<-3>                      ×2
the rebuilt worker was busy: Rejected(reason=HARD_DEADLINE_EXCEEDED, diagnostics=visual_js worker died: binder died)
first execution: null expected:<0> but was:<-3>
the new process inherited the old BUSY state: status=-3 diagnostics=null expected:<0> but was:<-3>
the explicit retry must render through the isolated worker, got Rejected(HARD_DEADLINE_EXCEEDED, … binder died)
the first execution after a 7600 ms cold-start attach was misclassified…: Rejected(HARD_DEADLINE_EXCEEDED, … onServiceDisconnected)
```

红例方法：`anExecuteAcquiredDuringWarmUpIsRefusedBoundedlyAndNeverWatchdogKilled` / `theClientRebuildsOnceAfterADeathAndTheNewWorkerIsNotBusy` / `workerIsIsolatedAndAProgramRoundTripsWithoutBeingKilledLater` / `theWatchdogKillsAHungWorkerAndAFreshGenerationHasNoBusyState` / `anExplicitRetryOfAnInjectedFusedProgramRendersAndReleasesTheFuse` / `aColdStartAttachBeyondTheOldConnectBudgetStillRendersTheFirstProgram`。

同窗 logcat 形状：worker 均由**服务端 watchdog hard deadline 杀**（`killing :visual_js worker: watchdog hard deadline`，多个 pid：16561/16812/16845/16867/16889/16909/16928）后 `has died: vis FGS`，与批 38 E-06 首跑红形同族（执行时长突刺 >1500 ms）；本会话宿主 4 模拟器在线高负载（loadavg 实测 3.2–4.0）。九类崩溃签名 8 类全 0（E-07）。

## 4. 脱敏与去噪

- 本文件不含密钥/凭据/邮箱/物理序列号/用户目录；原始全量在 `build/device-track-batch39/`。
