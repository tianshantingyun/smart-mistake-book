# E-03 KD-76 设备判据 ③④⑤：冷启动即跑套件 + warm 挂死负控 + 加载态复跑

| 字段 | 值 |
|---|---|
| evidence-id | E-03 |
| convention-version | 1 |
| batch | 2026-10-27-batch-26 |
| step | 三（KD-76 ③④⑤ 设备判据，可跑则跑） |
| gate-or-kd | KD-76（③ 冷启动 CPU 竞争首请求 / ④ warm 服务挂死负控 / ⑤ ready 失败加载态） |
| verdict | **PARTIAL：④ 强证据（计时门形状 + kill 行）、⑤ 复跑绿（行为层）、③ 尝试成功但边界如实（13/13 绿零意外杀；负载轻于 E-01d；产品进程/UI 面 UNVERIFIED）** |
| executed-at | 2026-10-27T07:23:00+08:00 |
| operator | 设备验证轨（subagent，批次 26） |
| head | 405c83739cfa5385aa7652ef61f3488f953c66ce |
| worktree | dirty（他线在飞） |
| device-serial | emulator-5554 |
| device-avd | test_device |
| device-api | 34 |
| device-abi | x86_64 |
| device-page-size | 4096 |
| apk | 同 E-01（`758c0caf…` / `cde7e27b…`；`adb reboot` 后包持续在盘，未重装） |
| exit-code | 0（adb 进程退出码；判据是 summary + logcat 归因） |
| raw-log | `build/device-track-batch26/step03-coldboot-suite.log`（191 行）、`step03-coldboot-logcat.txt`（6,374 行） |
| record-ref | 批次 26 记录（由记录员后写） |
| sanitized | 是（§6；扫描见 00-index §6） |

## 1. 目的与判据（台账原文实读）

KD-76 Fix direction 尾段（`docs/known-defects.md` KD-76 条目，2026-10-27 实读）：

- **③** 冷启动/CPU 竞争下首个 `execute` 返回 `Rendered`，且日志无该请求的 watchdog hard deadline；
- **④** 负控——同一 warm 服务上挂死程序仍在 1500 ms 内 SIGKILL（不得因 warm-up 分支放宽）；
- **⑤** 首连失败/ready 超时走明确加载态，不永久 `unavailable`。

验收判据原文："设备冷启动（最好含低内存/高负载条件）→ 首个真实请求成功；同一设备 warm 后
挂死程序仍 1500 ms 硬杀；`onFatal`/logcat 证明没有'首个请求被 hard deadline 杀掉'的签名。"

## 2. 命令（可复跑）

```bash
A=C:/Android/Sdk/platform-tools/adb.exe
export MSYS_NO_PATHCONV=1
# ③：冷启动后【不沉降】立即跑全类（E-01d 先例；红绿均按 R1 §4.5 归因树处理）
$A -s emulator-5554 reboot && $A -s emulator-5554 wait-for-device
# …轮询 sys.boot_completed=1（实测 14 s 后 =1）
$A -s emulator-5554 shell cat /proc/loadavg        # 0.75 0.16 0.05（开机即测）
$A -s emulator-5554 logcat -c
$A -s emulator-5554 shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.visualjs.VisualJsIsolationInstrumentedTest \
  com.tingyun.smartmistakebook.offline.test/androidx.test.runner.AndroidJUnitRunner
$A -s emulator-5554 logcat -v threadtime -d > build/device-track-batch26/step03-coldboot-logcat.txt
# ④⑤：证据取自 E-01（沉降轮）+ 本冷启动轮两套件中的对应用例与 logcat 锚点
```

- 设备身份 reboot 后复核（AVD/API/ABI/PAGE_SIZE 三值不变；`up 0 min`）。
- 冷启动负载实测：boot_completed 时 loadavg **0.75**；套件跑完后 **4.31**（ contention 存在，
  但低于批次 23 E-01d 的 5.25，见 §5 边界）。

## 3. 结果摘要

| 指标 | 冷启动即跑（③ 形状） | 判据 |
|---|---|---|
| tests run / failures | **13 / 0（`OK (13 tests)`，Time 64.072）** | 13/0 |
| 8 类崩溃签名 + `death was not confirmed` | 0 | 0 |
| `hard deadline (1500 ms)` 行总数 | **5**（全部为负向夹具**设计内**挂死，见 §4 归因表） | 首请求 0 |
| 首请求被 hard deadline 杀掉的签名 | **无**（验收判据要求的"无该签名"成立） | 无 |
| `warm-up finished` 行总数 | 17（13+ 次进程启动，含死亡重建） | — |

**③ 形状的关键时间轴**（套件第一个执行的用例即 T1 门用例，`step03-coldboot-logcat.txt:3016/3454/4267`）：

```text
23:23:27      boot_completed=1（loadavg 0.75）        # 冷启动，无沉降
23:23:43.867  TestRunner: started: aHungWorkerReleasesTheCallerWithinHardPlus500ms
23:23:45.811  VisualJsService: warm-up finished       # 冷态原生装载 ~1.9 s（竞争下）
23:23:46–47   预热 execute（pureGeometryRequest）→ Rendered（A5 前置负控，用例绿）
23:23:47.623  hard deadline (1500 ms) for request 5b9c3853-…; killing the worker   # 设计内 #240 挂死
23:23:48.132  Start proc 3771（死亡后重建）→ 23:23:49.752 warm-up finished
23:23:49.993  TestRunner: finished: …HardPlus500ms    # 绿
```

即：真实冷启动 + CPU 竞争下，**服务冷启动 → warm-up/ready → 首个 execute `Rendered` 全链成立**，
首个请求无 watchdog hard deadline。

## 4. 5 条 hard deadline kill 行逐条归因（命令 + 源码实读）

```bash
grep -n "hard deadline\|TestRunner: started\|TestRunner: finished" build/device-track-batch26/step03-coldboot-logcat.txt
grep -n "in-flight-1\|issue-240" app/src/androidTest/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsIsolationInstrumentedTest.kt
```

| # | 时间 | 请求 ID | 落入用例窗口 | 归因（源码锚点） | 设计内？ |
|---|---|---|---|---|---|
| 1 | 23:23:47.623 | `5b9c3853-…`（引擎生成 UUID） | aHungWorker…HardPlus500ms（43.867–49.993） | 门用例计时注入 `engine.execute(issue240Request())`（`:387`） | 是 |
| 2 | 23:24:29.110 | `in-flight-1` | aSecondConcurrentRequest…Busy（26.915–29.133） | 夹具首请求自旋至 SIGKILL（`:533`；KDoc `:520` 自述"至少持续到 1500 ms watchdog SIGKILL"） | 是 |
| 3 | 23:24:32.205 | `bfaaf9e6-…`（UUID） | theClientRebuildsOnce…NotBusy（30.018–34.566） | 该用例第一次 #240 死亡注入（`:299`） | 是 |
| 4 | 23:24:34.537 | `a6159141-…`（UUID） | 同上用例（34.566 前） | 该用例第二次 #240 死亡注入（`:321`，explicitUserRetry） | 是 |
| 5 | 23:24:39.387 | `issue-240`（夹具字面 ID） | theWatchdogKillsAHungWorker…（37.219–40.181） | 6 s 观察用例注入（`:201`，直连 `service.execute`） | 是 |

**结论：零意外 kill、零首请求 kill**——所有 kill 均为负向用例的设计内注入；13/13 绿与此归因
自洽（若存在首请求误杀，对应用例必然红或出现 BUSY 泄漏断言失败）。

### 4.1 ④ 的证据（warm 服务挂死负控）

- **计时门形状**（E-02 三轮绿）：warm 服务（预热 execute `Rendered` + ready 门）上 #240 挂死 →
  A2 `HARD_DEADLINE_EXCEEDED` + A5 `elapsed ≤ 1500+500 ms` → warm-up 分支**没有**放宽硬杀。
- **服务级 kill 行**（本冷启动轮 + 沉降轮）：`theWatchdogKillsAHungWorkerAndAFreshGenerationHasNoBusyState`
  在 `awaitReady()`（`:200`，KDoc `:199` 自述"批次 25 T2：warm 服务上的挂死负控（KD-76 ④）——
  ready 完成后 1500 ms 硬杀不变"）后注入挂死，kill 行实测：
  沉降轮 warm-up finished 23:19:37.620 → kill 39.419；冷启动轮 23:24:37.839 → kill 39.387
  （提交后 ~1.52–1.55 s，即 1500 ms 期限 + kill 执行延迟）；两轮该用例均绿（新进程无 BUSY）。
- 边界（如实）："1500 ms 内 SIGKILL"的精确到毫秒的 kill 延迟分布未单独断言（观察窗口是 6 s
  弱口径）；强口径以 E-02 的 hard+500 ms 调用方门为准。

### 4.2 ⑤ 的证据（加载态，行为层复跑 ×2 轮）

`aFirstConnectTimeoutFailsHonestlyAndTheNextExecuteRetriesWithoutLatching` +
`aReadyTimeoutFailsHonestlyWithoutLatchingAndTheNextExecuteRebinds` +
`anExecuteAcquiredDuringWarmUpIsRefusedBoundedlyAndNeverWatchdogKilled` 在**沉降轮（E-01）与
冷启动轮（本 E-file）两轮均绿**：首连超时如实 FAILED 不锁存 + 下次 execute 有界重试、ready
超时不锁存 + 重新 bind、warm-up 中 acquire 有界拒绝不误杀。"如实显示加载态"的 UI/文案面与
R3 广告门文本同步仍未做（属 T4/主会话，非设备轨）——⑤ 行为层证据成立，广告门完整性仍缺
R3 文本升级（KD-76 保持 open 的另一半原因）。

## 5. ③ 的边界与 UNVERIFIED（如实，不得读成 ③ 达成）

1. **负载轻于 E-01d**：本次 boot_completed 时 loadavg 0.75（批次 23 E-01d 实测 5.25、attach
   4.6–7.5 s 超旧预算）；本批冷启动下 attach+warm-up ~1.9 s，未触达竞争上界。"高负载条件"
   只部分覆盖（跑中 loadavg 4.31），低内存条件未覆盖。
2. **产品进程/UI 面未覆盖**：本 ③ 证据的调用方是测试 APK 内的生产 client 引擎
   （`VisualJsClientEngine`），不是真实 app 进程经 UI 触发的首个用户请求；契约 §4 worst-case
   29.5 s 启动链（P-04）与生产进程调度分布仍未设备实测（沿批次 25 缺口 7）。
3. 一次实跑、一台模拟器（x86_64/API 34）；arm64/真机 UNVERIFIED。
4. **KD-76 不因本批设备轨关闭**：③ 边界如上、R3/契约广告门文本升级未完成（批次 25 9.3 同判）。
   关闭动作归主会话；本轨只提供判据对照。

## 6. 脱敏与去噪

- 删除：reboot 轮询噪声；保留 boot 时间、loadavg、时间轴、逐条 kill 归因表。
- 扫描：见 `00-index.md` §6。二进制/截图：无。

## 7. 判定

**PARTIAL（KD-76 ③④⑤）**：

1. **④ 强证据**：warm 服务挂死在 1500 ms 硬期限被杀（kill 行实测）+ 调用方 hard+500 ms 有界
   返回（E-02 三轮）+ 新 generation 无 BUSY（两轮用例绿）。
2. **⑤ 行为层证据 ×2 轮**：加载态三用例（首连超时/ready 超时/warm-up acquire）沉降轮与冷启动
   轮全绿；UI/文案与 R3 文本同步未做。
3. **③ 尝试成功但边界如实**：真实冷启动（无沉降）即跑 13/13 全绿、零意外 kill、零首请求 kill、
   冷态 warm-up→首请求 `Rendered` 全链成立；但负载轻于 E-01d、产品进程/UI 首请求与 29.5 s
   链 UNVERIFIED。**JS 仍不广告（本批纪律）**。

## 8. 原始全量（不入仓）

- 路径：`build/device-track-batch26/step03-coldboot-suite.log`（191 行）、
  `step03-coldboot-logcat.txt`（6,374 行）
- 生成命令：见 §2。`build/` 被 `.gitignore` 忽略；关闭判据只认本文件。

## 9. 更正（2026-10-27，落盘复核轨追加；R4 §7.6 只追加，上方原行保留不改）

复核命令（本轮实跑，可复现）：

```bash
grep -cF "UnsatisfiedLinkError" build/device-track-batch26/step03-coldboot-logcat.txt   # 1
grep -n "UnsatisfiedLinkError" build/device-track-batch26/step03-coldboot-logcat.txt    # :2162
grep -n "engine.execute(issue240Request())\|pureGeometryRequest(explicitUserRetry" \
  app/src/androidTest/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsIsolationInstrumentedTest.kt
```

1. **§3 签名扫描行"8 类崩溃签名 + `death was not confirmed` 0"按原始 grep 口径更正**：
   对 `step03-coldboot-logcat.txt` 全量原始 grep，`UnsatisfiedLinkError` 命中 **1**（`:2162`，
   `23:23:41.427 3177 3260 W NativeCrashHandlerImpl: java.lang.UnsatisfiedLinkError: dlopen
   failed: library "libnative_crash_handler_jni.so" not found`）。归因：PID 3177 是系统/Google
   组件（上下文 `:2160-2165` 为 `JobScheduler.Quota` 对 `com.google.android.contacts` 的配额
   日志与其附带崩溃处理器的装载告警），**不是被测应用**——应用进程为 TestRunner 2829
   （`Start proc 2829` 于 `:471`）与 `:visual_js` 3550/4159/4209/4232/4262/4294/4332/4356；
   同一文件其余 7 类签名与 `death was not confirmed` 原始 grep 均为 0。E-01 的
   `step01-isolation-logcat.txt` 复核 9 项全 0，不受影响。**判定不变**：13/13 绿、零**应用**
   崩溃签名、零首请求 kill；本更正只修签名计数的口径（"0"应读作"应用进程 0，全量 logcat
   含 1 条系统进程噪声，已归因"）。
2. **§4 归因表第 3/4 行源码锚点更正**（off-by-one/错行；判定与归因结论不变）：
   - 第 3 行"第一次 #240 死亡注入 `:299`"→ 实际注入行是 **`:298`**（`val first =
     engine.execute(issue240Request())`），`:299` 是其断言 `assertEquals(` 起始行。
   - 第 4 行"第二次 #240 死亡注入 `:321`，explicitUserRetry"→ 实际注入行是 **`:327`**
     （`val secondDeath = engine.execute(issue240Request(explicitUserRetry = true))`）；
     `:316` 是熔断执行（同 programHash 死亡后不再下发，不产生 kill）、`:323` 是重建后的
     `pureGeometryRequest(explicitUserRetry = true)` 渲染（无 kill）、`:321` 是空行。该用例
     两条 kill（`bfaaf9e6`/`a6159141`）分别对应 `:298`/`:327` 两次 #240 下发，与"两次
     死亡注入"的归因结论一致。
