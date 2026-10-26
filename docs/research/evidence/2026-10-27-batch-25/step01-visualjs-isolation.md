# E-01 VisualJs 隔离套件全量（批次 25 T1/T2/T3 落层后首次设备执行）

| 字段 | 值 |
|---|---|
| evidence-id | E-01 |
| convention-version | 1 |
| batch | 2026-10-27-batch-25 |
| step | 一（隔离套件全量） |
| gate-or-kd | 设备功能门 / KD-66（隔离面） / KD-76（warm-up/ready 设备首验） / §64 队列负向设备首验 |
| verdict | **PARTIAL（Isolation 11/12 + Race 2/2；1 例红 = 批次 25 T3 新用例夹具缺陷，§5 归因）** |
| executed-at | 2026-10-27T03:33:00+08:00 |
| operator | 设备验证轨（subagent，批次 25） |
| head | 25ddb60273e32ebd4100e8deb9f965bb0dfd4476 |
| worktree | dirty（他线在飞；含批次 25 T1/T2/T3 未提交落层） |
| device-serial | emulator-5554 |
| device-avd | test_device |
| device-api | 34 |
| device-abi | x86_64 |
| device-page-size | 4096 |
| apk | `app/build/outputs/apk/strictOffline/debug/app-strictOffline-debug.apk` + `app/build/outputs/apk/androidTest/strictOffline/debug/app-strictOffline-debug-androidTest.apk` |
| apk-sha256 | app `1af0f99af2e2bf3182f4838357ad2e5a93568ac4a33439524ffbde6214e7b737`；test `6623b0acebb3cca4bcf891051d0f5a41641d3d7e6c45750fcbac2d5302ec600c` |
| apk-version | 0.2.0-offline |
| exit-code | 0（adb 进程退出码；判据是 summary——本步 summary 含 1 失败，见 §3/§5） |
| raw-log | `build/device-track-batch25/step01-isolation.log`（258 行）、`step01-race.log`（37 行）、`step01-isolation-logcat.txt`（2,391 行）、`step01-install-app.log`/`step01-install-test.log`（各 2 行） |
| record-ref | 批次 25 记录（由记录员后写） |
| sanitized | 是（§6；扫描见 00-index §6） |

## 1. 目的与判据

批次 25 三条落层后的隔离面全量设备首验：

1. `VisualJsIsolationInstrumentedTest` 全量 **12 例**（批次 24 的 6 例 + T3 队列负向 3 例
   `aMalformedCallerRequestIsRejectedWithoutTakingTheFlightSlot` /
   `anOverBudgetRequestEnvelopeIsRejectedByTheBoundedRead` /
   `aSecondConcurrentRequestIsRejectedBusyWhileTheFirstIsInFlight` + T2 warm-up/ready 3 例
   `anExecuteAcquiredDuringWarmUpIsRefusedBoundedlyAndNeverWatchdogKilled` /
   `aFirstConnectTimeoutFailsHonestlyAndTheNextExecuteRetriesWithoutLatching` /
   `aReadyTimeoutFailsHonestlyWithoutLatchingAndTheNextExecuteRebinds`），预期全绿；
2. `VisualJsHandshakeRaceInstrumentedTest`（T1 新文件，fake-binder 夹具，**不**启动真实
   `:visual_js`，不作隔离门证据）2 例：`markDeadWinningTheHandshakeRaceIsNeverPublishedAsConnected`、
   `aDeathDuringTheRebuildHandshakeSpendsTheRebuildBudgetExactlyOnce`，预期全绿；
3. 红则抓栈并归因（本批/他线/环境）。

本步不修改任何源代码。

## 2. 命令（可复跑）

```bash
A=C:/Android/Sdk/platform-tools/adb.exe
export MSYS_NO_PATHCONV=1
# 冷启动恢复后沉降 150 s（见 step00 §2.1），然后：
$A -s emulator-5554 install -r -t app/build/outputs/apk/strictOffline/debug/app-strictOffline-debug.apk
$A -s emulator-5554 install -r -t app/build/outputs/apk/androidTest/strictOffline/debug/app-strictOffline-debug-androidTest.apk
$A -s emulator-5554 shell "pm path com.tingyun.smartmistakebook.offline | sed 's/package://'"   # + sha256sum
$A -s emulator-5554 logcat -c
$A -s emulator-5554 shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.visualjs.VisualJsIsolationInstrumentedTest \
  com.tingyun.smartmistakebook.offline.test/androidx.test.runner.AndroidJUnitRunner
$A -s emulator-5554 shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.visualjs.VisualJsHandshakeRaceInstrumentedTest \
  com.tingyun.smartmistakebook.offline.test/androidx.test.runner.AndroidJUnitRunner
$A -s emulator-5554 logcat -v threadtime -d > build/device-track-batch25/step01-isolation-logcat.txt
```

- 工作目录：仓库根
- 安装：两个 APK 均 `Success`；设备侧 `pm path`+`sha256sum` 与产物逐字节一致（§表 apk-sha256）
- 运行耗时：isolation `Time: 55.742`；race `Time: 1.04`

## 3. 结果摘要

| 指标 | Isolation（12 例） | Race（2 例） | 判据 |
|---|---|---|---|
| tests run | 12 | 2 | 12 / 2 |
| failures | **1** | 0 | 期望 0 |
| skipped | 0 | 0 | 0 |
| summary | `FAILURES!!! Tests run: 12, Failures: 1` | `OK (2 tests)` | — |
| `INSTRUMENTATION_CODE` | -1 | -1 | 非判据（adb 侧 exit=0） |
| 8 类崩溃签名命中 | 0 | 0 | 期望 0 |
| `death was not confirmed` 告警 | 0 | 0 | 期望 0 |

逐例状态（`INSTRUMENTATION_STATUS_CODE`：1=开始，0=通过，-2=失败）：

```text
# Isolation（11 通过 + 1 失败）
theClientRebuildsAtMostOnceAfterConfirmedAbandons                        => 1, 0
aReadyTimeoutFailsHonestlyWithoutLatchingAndTheNextExecuteRebinds        => 1, 0   # T2 新
anOverBudgetRequestEnvelopeIsRejectedByTheBoundedRead                    => 1, 0   # T3 新
aMalformedCallerRequestIsRejectedWithoutTakingTheFlightSlot              => 1, -2  # T3 新，红，见 §5
aStaleGenerationIsRejectedAndAStaleTerminateIsANoOp                      => 1, 0
aFirstConnectTimeoutFailsHonestlyAndTheNextExecuteRetriesWithoutLatching => 1, 0   # T2 新
aSecondConcurrentRequestIsRejectedBusyWhileTheFirstIsInFlight            => 1, 0   # T3 新
anExecuteAcquiredDuringWarmUpIsRefusedBoundedlyAndNeverWatchdogKilled    => 1, 0   # T2 新
# ……（其余 4 例为批次 24 既有 6 例中的其余成员，均 1→0；完整逐例见 raw-log）
```

```text
# Race（T1 交错用例，2/2）
aDeathDuringTheRebuildHandshakeSpendsTheRebuildBudgetExactlyOnce          => OK
markDeadWinningTheHandshakeRaceIsNeverPublishedAsConnected                => OK
```

签名扫描（对 `step01-isolation-logcat.txt`，两轮 instrument 共用的 logcat）：

```bash
for s in "FATAL EXCEPTION" "ANR in" "NoSuchMethodError" "NoClassDefFoundError" \
         "UnsatisfiedLinkError" "SIGSEGV" "CANNOT LINK" "failed to map segment"; do
  printf '%-24s %s\n' "$s" "$(grep -c "$s" build/device-track-batch25/step01-isolation-logcat.txt)"
done
# 全部 0；death was not confirmed：0
```

## 4. 关键输出（有界）

```text
# isolation 尾部
FAILURES!!!
Tests run: 12,  Failures: 1

INSTRUMENTATION_CODE: -1
```

```text
# race 尾部
Time: 1.04

OK (2 tests)

INSTRUMENTATION_CODE: -1
```

## 5. 失败 / 跳过 / 异常（红 → 抓栈 + 归因）

**失败例**：`aMalformedCallerRequestIsRejectedWithoutTakingTheFlightSlot`（批次 25 T3 新增
仪器化负向用例，本批首次设备执行）。

**栈（raw-log `step01-isolation.log`，`INSTRUMENTATION_STATUS: stack=` 全文）**：

```text
java.lang.RuntimeException: Bad file descriptor
	at android.os.Parcel.nativeWriteFileDescriptor(Native Method)
	at android.os.Parcel.writeFileDescriptor(Parcel.java:1196)
	at android.os.ParcelFileDescriptor.writeToParcel(ParcelFileDescriptor.java:1119)
	at com.tingyun.smartmistakebook.visualjs.IVisualJsService$_Parcel.writeTypedObject(IVisualJsService.java:345)
	at com.tingyun.smartmistakebook.visualjs.IVisualJsService$Stub$Proxy.execute(IVisualJsService.java:277)
	at com.tingyun.smartmistakebook.visualjs.VisualJsIsolationInstrumentedTest$aMalformedCallerRequestIsRejectedWithoutTakingTheFlightSlot$1.invokeSuspend(VisualJsIsolationInstrumentedTest.kt:375)
	...
```

**归因（逐项排除后定为：本批 T3 新用例的夹具缺陷，非环境、非他线、非引擎回归）**：

1. **环境排除**：`Bad file descriptor` 发生在 **binder 客户端编组阶段**（`Parcel.nativeWriteFileDescriptor`），
   与设备/模拟器无关——把已关闭的 fd 写进 Parcel 在任何 AOSP 版本/真机上都确定性抛该
   RuntimeException；同机同轮其余 11 例全绿，另两例 T3 负向用例（信封超预算、并发 BUSY）也绿。
2. **他线排除**：栈顶帧全部落在批次 25 T3 新增的用例与既有 AIDL 垫片内，无他线模块帧。
3. **根因（读源码定位）**：`VisualJsIsolationInstrumentedTest.kt` 该用例在第一个负向调用后
   `runCatching { pipe[0].close() }`（`:364` 附近），第三个负向调用
   `service.execute(generation, "no-callback", pipe[0], null)`（`:374-375`，断言 "a null callback
   must be rejected"）**复用了这个已关闭的 `pipe[0]`** ——客户端在事务发出前就抛
   RuntimeException，服务端的 `STATUS_BAD_REQUEST` 参数校验分支根本没被执行到。
   T3 轨自述未跑 Gradle/设备（"仅代码"），该夹具缺陷因此漏到设备轮才暴露。
4. **影响面**：仅此 1 例；用例级判定为失败，因此**该用例不构成任何通过证据**。事实拆解：
   三个负向子断言中，前两个（null requestId / null 描述符 → 服务端 `STATUS_BAD_REQUEST`）
   在异常点之前已真实执行并通过（栈落在 `:375` 即第三个子断言的调用行，前两处 assertEquals
   未抛）；**第三个子断言（null 回调 → BAD_REQUEST 且不占在飞槽）没有设备证据**——异常发生在
   客户端编组，事务未到达服务端。修复复跑前，不得引用本用例声称 A-14 全子项设备绿。
5. **修复建议（属 T3 轨/主会话，本轨不改源代码）**：第三个负向调用改用**新开**的
   `ParcelFileDescriptor.createReliablePipe()`（或把两次 close 挪到第三个调用之后），断言本身
   （`STATUS_BAD_REQUEST` + 后续合法 execute 照常）不放宽。修复后复跑命令 = §2。

**绿的部分（同样是本步结论）**：

- 批次 24 既有 6 例全绿（回归零破坏）；
- **T2 warm-up/ready 3 例设备首验全绿**：warm-up 中 acquire 有界拒绝不误杀、首连超时如实
  FAILED 不锁存 + 下次 execute 有界重试、ready 超时不锁存并重新 bind——KD-76 的 ③④⑤
  设备判据由此获得首份（仍是部分）设备证据；
- **T3 另外两例设备负向全绿**：>256 KiB 信封被有界读拒绝（INPUT_OVER_BUDGET）、并发第二
  请求服务端 BUSY；
- **T1 交错用例 2/2 设备首验全绿**（`OK (2 tests)`，Time 1.04 s）：markDead 赢得握手竞态时
  连接发布让位（bind 恰 1 次）、重建握手输给死亡时重建预算只扣一次（bind 恰 2 次后 fail-closed）。
  T1 轨按回归形状推演的"红条件"（发布点退回无条件置 CONNECTED 则 connectionCount 必变）
  未在本轮验证——本轮只有绿侧证据，红→绿推演仍属代码层论证。

## 6. 脱敏与去噪

- 删除：adb 安装进度行。
- 保留：全部逐例状态（含 -2 失败码）、summary、失败栈全文（仅项目包名帧 + 系统/框架帧）、
  崩溃签名零计数。
- 扫描：见 `00-index.md` §6。
- 二进制/截图：无。

## 7. 判定

**PARTIAL（隔离面）**：

1. `VisualJsIsolationInstrumentedTest` 11/12 + `VisualJsHandshakeRaceInstrumentedTest` 2/2 绿；
   批次 24 隔离门 6 例无回归，KD-76 warm-up/ready 与 §64 队列两条负向设备首验绿，T1 竞态
   交错用例设备首验绿。
2. **1 例红**：T3 新用例夹具缺陷（已关闭 PFD 复用），确定性红、客户端编组即抛、修复面在
   测试夹具一行级改动；修复并复跑前，A-14（畸形参数 BAD_REQUEST 不占在飞槽）**无设备证据**。
3. KD-66 不因本步变动（关闭门仍是 hard+500 ms 设备用例，见 E-02 登记）；JS 仍不广告。

## 8. 原始全量（不入仓）

- 路径：`build/device-track-batch25/step01-isolation.log`（258 行）、`step01-race.log`（37 行）、
  `step01-isolation-logcat.txt`（2,391 行）、`step01-install-app.log`、`step01-install-test.log`
- 生成命令：见 §2
- 说明：`build/` 被 `.gitignore` 忽略；关闭判据只认本文件。
