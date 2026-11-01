# E-13 批次 39 收尾轮·KD-76 冷启动连接阶段校准

| 字段 | 值 |
|---|---|
| evidence-id | E-13 |
| convention-version | 1 |
| batch | 2026-11-01-batch-39（收尾轮 2026-11-02） |
| step | 收尾轮·步五 |
| gate-or-kd | KD-76（open；本校准不关闭 KD-76） |
| verdict | **FAIL / UNVERIFIED**：G1 红（20 轮中 11 轮失败），有效样本 n=9 < 预注册 20；**主口径 M=S-bind+S-warmup 的 p95 = 9813 ms > 5000 ms → 主口径 FAIL**（旧用例只对 S-warmup 单段施门，其 "G2 PASS" 已被本文件 §4.1 复核推翻） |
| executed-at | 2026-11-02T03:05:00+08:00 – 03:10:07 |
| operator | 设备验证轨（子代理） |
| head | `f10a1e4e` |
| worktree | 510 M/??（他线在飞） |
| device-serial | `emulator-5562` |
| device-avd | `test_device` |
| device-api | 34 |
| device-abi | x86_64 |
| device-page-size | 4096 |
| apk | app `fe9452aab64d6fcf9d840a0e5e5c542d43daed176da2bf5aa71370d766d69e3e`；appTest `3c222fca7560327ab7200523b5c1332ebfd6228277a0eb133c34338985f9bfaf` |
| apk-version | `0.2.0-offline` |
| exit-code | adb-exit=0；`INSTRUMENTATION_CODE: -1` |
| raw-log | `build/device-track-batch39-tail/suite-kd76-calibration{,-logcat}.txt`、`kd76-load-window.txt`、`kd76-before-load.txt` |
| record-ref | `docs/research/evidence/2026-11-01-batch-39/00-index.md` §11 |
| sanitized | 是 |

## 1. 用例落树状态

`app/src/androidTest/.../visualjs/VisualJsConnectStageCalibrationInstrumentedTest.kt`（untracked，mtime 00:42）——1 个 `@Test`（`coldStartConnectStagesMeetTheReadyHalfBudgetAcrossTwoBatches`），预注册 20 轮 = 两批各 10 轮，`nearest-rank (ceil)` 估计量。dex 门已证类进入 appTest APK。

**执行形状与预注册的偏差（本轮复核补登，必须先读）**：

- 预注册主口径（`docs/research/2026-11-01-kd76-timing-calibration.md:132`）要求**真实冷启动轮**：`adb reboot` 后不沉降即跑，n≥10。本用例是**单个 instrumentation 进程内 20 轮**（两批各 10），跨轮页缓存与 JIT 是热的；用例 KDoc `:82-92` 自承是"冷进程 + 热页缓存"，不是 reboot 后首启。
- 因此本文件 §4 的数字是**"冷进程 + 热页缓存"观测**，不是主口径要求的执行条件；它也**不满足**"两批是两次独立设备运行"的合并前提。即使某次 n=20 全绿，也不能单独用作关闭 KD-76 的主口径证据。
- **raw log 与设备轨执行记录中没有 reboot 动作**：`grep -rn "reboot" build/device-track-batch39-tail/*.txt` 为 0 命中（本复核轮实跑）。本文件为登记偏差新增了 `adb reboot` 字样，那是程序说明，不是执行记录。

## 2. 首跑（A 类夹具缺陷，红）

```text
AssertionError: A-01 INVALID: /proc/loadavg is unreadable, so environment attribution (A8)
cannot be done; raw='null'
  at VisualJsConnectStageCalibrationInstrumentedTest.kt:653
```

根因：Android 14 `untrusted_app` 域读不到 `/proc/loadavg`，A8 的 `assertNotNull` 使用例在设备上不可运行。A 类夹具修正（不改门、不改测量）：不可读时记 `-1.0` 并 `Log.w`，A8 归因改由设备轨外部 `adb shell cat /proc/loadavg` 采样（`kd76-load-window.txt`，30 s 一次）。修正后重建 appTest APK 并重装（设备侧 sha 核对一致）。

## 3. 第二跑（数据质量 FAIL）

```bash
adb -s emulator-5562 shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.visualjs.VisualJsConnectStageCalibrationInstrumentedTest \
  com.tingyun.smartmistakebook.offline.test/androidx.test.runner.AndroidJUnitRunner
# Time: 312.636；Tests run: 1, Failures: 1
```

G1 原文（节选）：

```text
G1 red: 11 cold-start round(s) failed … A-07: execute produced
Rejected/HARD_DEADLINE_EXCEEDED: visual_js worker died: binder died (censored ≥ 2500 ms);
B-01/B-02/B-05/B-07/B-10: connectForTest() == false (censored ≥ 15000 ms);
B-03/B-04/B-09: execute HARD_DEADLINE_EXCEEDED/binder died (censored ≥ 2500 ms);
B-06/B-08: host phase deadline (2500 ms) exceeded (censored ≥ 2500 ms)
  at VisualJsConnectStageCalibrationInstrumentedTest.kt:139
```

批次形状：batch A success 9 / fail 1；batch B success 0 / fail 10。

## 4. 有效样本分布（n=9，nearest-rank ceil）

| 段 | min | p50 | p90 | p95 | max |
|---|---|---|---|---|---|
| **M = S-bind + S-warmup（主口径，逐轮求和后取 p95）** | **1474** | **1821** | **9813** | **9813** | **9813** |
| S-bind（C1） | 937 | 1362 | 9229 | 9229 | 9229 |
| S-warmup（C2，**主口径的分量**，不是门） | 209 | 520 | 1319 | 1319 | 1319 |
| S-readyGap（C3 子集） | 3 | 43 | 226 | 226 | 226 |
| S-firstByte（C3 子集） | 368 | 764 | 1535 | 1535 | 1535 |
| S-total | 2019 | 2588 | 11467 | 11467 | 11467 |

`M` 的逐轮原值（bind+warmup，按样本出现顺序）：
`[1571, 9813, 2960, 2125, 1821, 2199, 1777, 1511, 1474]`；
sorted `[1474, 1511, 1571, 1777, 1821, 2125, 2199, 2960, 9813]`。

`sorted` 原样（禁删离群）：bind `[937, 1189, 1212, 1257, 1362, 1641, 1675, 1897, 9229]`；warmup `[209, 302, 322, 450, 520, 537, 584, 609, 1319]`。

**复算命令（本复核轮实跑）**：

```bash
python -I -c "import math; vals=[(1362,209),(9229,584),(1641,1319),(1675,450),(1212,609),(1897,302),(1257,520),(1189,322),(937,537)]; M=sorted(a+b for a,b in vals); W=sorted(b for a,b in vals); p=lambda x:x[min(len(x)-1,math.ceil(len(x)*0.95)-1)]; print('M',M,p(M),'W',W,p(W))"
# M [1474, 1511, 1571, 1777, 1821, 2125, 2199, 2960, 9813] 9813 W [209, 302, 322, 450, 520, 537, 584, 609, 1319] 1319
```

### 4.1 判读（主口径 FAIL）

- **主口径**：`M.p95 = 9813 ms > READY_TIMEOUT/2 = 5000 ms` → **FAIL**。按 T1 §5.3 的判别分支，这正落在"M 超 5 s 而 S2 单段 p95 ≤ 5 s → 慢在 S1，不得据此改 `READY_TIMEOUT`"的形状：S-bind 的 9229 ms 离群是主因，S-warmup 单段 1319 ms 只是分量。
- 旧用例只对 `warmUpMillis` 单段施门，并在 raw log 打出 `G2: S-warmup p95=1319 ms vs gate=5000 ms => PASS`（`suite-kd76-calibration-logcat.txt:3415`）。该判词**口径错误**：预注册主口径是 `bind 请求→ready` 的 `S-bind + S-warmup`（`2026-11-01-kd76-timing-calibration.md:132`），不是 `ready − bindConnected`。本复核轮已把用例门改为 M（见 §8），raw log 里的 PASS 是旧二进制输出，**不得再被引用为 KD-76 的通过证据**。
- **不可据此关闭 KD-76**：预注册 n=20，G1 红，有效样本只有 9（>1 个无效 = 数据质量 FAIL）。主口径本身也已 FAIL。

## 5. 环境归因（D）

窗内设备 loadavg（外部采样，`kd76-load-window.txt`）：

```text
2.66 → 4.11 → 6.12 → 9.82 → 11.25 → 8.91 → 11.12 → 10.29 → 9.82 → 9.50
```

11 个失败轮全部是 1500 ms 服务硬期限/首连 15 s 预算在重负载下被越过的形状，属 **D（环境/负载）**；batch B 全失败的形状与设备重负载一致，不改任何产品常量。

**归因证据的时点更正（本复核轮）**：原句"同一时段他线在持续构建（`core/database` jar 02:21 重建、源文件 02:25→02:59 仍在变，见 E-14）"引用的构建/变动**早于本窗**（本窗 03:05–03:10；E-14 的 28 个他线路径变动记录在 01:57–02:59），不构成本窗内的因果证据。本窗可直接引用的证据只有：① 窗内 loadavg 2.66→11.25；② 失败形状全部落在 1500 ms 硬期限/15 s 首连预算；③ 同时段主机侧是否有构建**未在本窗采样**（E-14 的源快照门在 03:12 才复测）。因此 D 归因是**与高负载一致的推断**，不是被隔离出的单一原因。

## 6. 29.5 s 链分段

| 段 | 预算 | 本次状态 |
|---|---|---|
| S1 首连 | 15 000 ms | **实测（部分，热页缓存口径）**：n=9，p95 9229 ms（含一个 9229 离群；p50 1362）。数值系统性偏小（页缓存热、无 reboot），不得当作真实冷启动分布 |
| S2 warm-up ready | 10 000 ms | **实测（部分，热页缓存口径）**：n=9，p95 1319 ms；是主口径 M 的分量，不可单独引用为"客户端可感口径通过" |
| S3 宿主阶段 | 2 500 ms | **门未取得**：原写"沿 `aHungWorkerReleasesTheCallerWithinHardPlus500ms`（hard 1500 + 500）通过"不成立——本批 isolation 基线窗该用例就是红例 #1，失败在"the worker must render before the hang is injected"预热断言（`VisualJsIsolationInstrumentedTest.kt:479`，`suite-isolation.log:27`），hard+500 门**根本没有执行**；控制窗同样红。S3 只能标"名义值、门未取得" |
| S4 死亡确认 | 2 000 ms | **UNVERIFIED**：无计时点；本轮 3 条 `death was not confirmed within 2000 ms; fail-closed` 是产品日志，不构成段值 |

`Σ_partial = max(S1) + max(S2) = 9229 + 1319 = 10548 ms`（仅 9 个有效样本、热页缓存口径），S3 名义 2500、S4 未测，**不得声称 29.5 s 链已实测**。

## 7. 崩溃签名披露

校准窗 logcat 有 1 条 `NativeCrashHandlerImpl: java.lang.UnsatisfiedLinkError: dlopen failed: library "libnative_crash_handler_jni.so" not found`（测试基础设施告警，非 QuickJS/产品库），及 3 条产品 fail-closed `death was not confirmed`；9 类其他签名 0。

## 8. 第 2 轮复核修复（2026-11-02；零 Gradle/零设备）

本节由复核修复轮追加，只修口径与证据，**不重跑、不改写原始 raw log**：

1. **用例门改为主口径 M**：`VisualJsConnectStageCalibrationInstrumentedTest.kt` 的 G2 从 `warmUpMillis.p95 ≤ 5000` 改为 `(bindMillis + warmUpMillis).p95 ≤ 5000`（逐轮求和后取 p95）；S-warmup 单段只作分量打印。旧二进制产生的 `G2: S-warmup … PASS` 判词不再有效。
2. **报告判词加守卫**：预注册样本未达成（`samples.size < 20`）或 G1 红时，报告只打印 `G2: NOT EVALUABLE` 与信息性 p95，不打印 PASS/FAIL；避免 n=9 的数值被下游当判词引用。
3. **S3 更正**：见 §6（门未取得）。
4. **执行条件偏差登记**：见 §1（无 reboot、单 instrumentation 进程、热页缓存；不是主口径形状）。
5. **归因时点更正**：见 §5（引用的构建/变动早于本窗）。
6. **采样程序更正**：若设备轨按 T1 §5.2 逐样本 `adb reboot` + 每次 `am instrument` 执行，取样本行不能用 `grep Kd76Calibration | tail -1`——`tail -1` 会取到无数字的 `stats/envelope` 尾行（本 raw log 共 75 行 `Kd76Calibration`，末行为 envelope 说明）。应改为 `grep -E 'Kd76Calibration: *(A|B)-[0-9]{2} .*S-bind=' | tail -1`（或按 roundId 精确取行）。本批执行是单次 20 轮，不是该逐样本程序。
7. **`connectForTest` 与 execute 的顺序偏差**：T1 §5.1 要求由首个 execute 触达 bind（不在预热 execute 之前单独 `connectForTest()`）；本用例现树在 `:309` 先显式 `engine.connectForTest()`、`:346` 再 execute。两者读的是同一 seam 的 bind→ready 段，但执行形状不同（本用例把连接失败与 execute 失败分开归类）。改它会改变失败分类与 A5 删失口径，故本轮**只登记偏差、不改测试**；主口径复跑时应按 T1 形状由 execute 触发 bind，或明确接受该偏差。
8. **worker UID 断言已补**：用例新增 `workerUidForTest() != Process.myUid()`（A11/T1 §5.1），本轮未上设备，新增断言的效果 UNVERIFIED。
9. **中止时也落报告（复核第 2 轮 #6④）**：用例把两次 `runBatch` 包进 `try/catch`；任一断言在 `emit` 之前中止时，先 `emit` 已获得的部分批次报告（`BatchOutcome` 可为空）与一行 `KD-76 calibration ABORTED before a complete report: <reason>`（含 fingerprint/SDK/apkSha256），再重抛。这样 T1 形状的 `adb reboot` 后熄屏、批首 `screenIsInteractive()` 失败时，不会"一行都不落盘"。**设备轨仍须在每次 `am instrument` 前唤醒屏幕**（如 `adb shell input keyevent KEYCODE_WAKEUP`），否则该轮只会得到 INVALID 报告而不是样本；本轮未上设备，该分支 UNVERIFIED。

**本节改动文件**：`app/src/androidTest/.../VisualJsConnectStageCalibrationInstrumentedTest.kt`、本文件。
