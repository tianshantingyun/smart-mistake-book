# E-02 QuickJS 引擎全量（KD-70 OOM 探针）

| 字段 | 值 |
|---|---|
| evidence-id | E-02 |
| convention-version | 1 |
| batch | 2026-10-26-batch-23 |
| step | KD-70 OOM 探针（P1–P5） |
| gate-or-kd | KD-70 |
| verdict | FAIL |
| executed-at | 2026-10-26T16:17:00+08:00 |
| operator | 设备验证轨（subagent，批次 23） |
| head | f0a145f850931887385c4bf2e1f798a0e8f2e391 |
| worktree | dirty（他线在飞；本轨未改产品源） |
| device-serial | emulator-5554 |
| device-avd | test_device |
| device-api | 34 |
| device-abi | x86_64 |
| device-page-size | 4096 |
| apk | `core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk`（自含测试 APK） |
| apk-sha256 | `5ef6ff9b46165517c0196f6b2926e8a07b64cf1fcfa8e62c5c82eb6901e72e75` |
| apk-version | — |
| exit-code | 0（adb 进程退出码；非判据） |
| raw-log | `build/device-track-batch23/step02-quickjs-oom.log`（353 行）、`step02-quickjs-oom-logcat.txt`（193 行） |
| record-ref | `docs/research/2026-10-26-batch-23-record.md` §4 |
| sanitized | 是（§6；扫描 0 命中） |

## 1. 目的与判据

批次 22 设备轨 20 例仅 P2 红，但 P2 的实际 reason/diagnostics 未落盘。本步真跑全类（无 log-only 参数），目标：

- P1 `memoryOverTheHeapBudgetIsRejectedAsAMemoryLimit`：64 MiB ArrayBuffer → `MEMORY_LIMIT` 且诊断含 `16777216`、`out of memory`；
- P2 `heapExhaustionWithLostErrorMessageIsAttributedByMemoryUsage`：50 万小对象链表 → 若消息丢失则靠 `mallocSize >= 90% mallocLimit` 归属 `MEMORY_LIMIT`；
- P3/P4/P5 负控：`string too long` 不是 OOM、低堆 `throw null` → `[UNATTRIBUTED]`、伪造 OOM 文案不误报。

## 2. 命令（可复跑）

```bash
A=C:/Android/Sdk/platform-tools/adb.exe
$A -s emulator-5554 install -r -t core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk
$A -s emulator-5554 logcat -c
MSYS_NO_PATHCONV=1 $A -s emulator-5554 shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.data.visual.QuickJsVisualExecutionEngineInstrumentedTest \
  com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner
$A -s emulator-5554 logcat -v threadtime -d > build/device-track-batch23/step02-quickjs-oom-logcat.txt
```

## 3. 结果摘要

| 指标 | 值 | 判据 |
|---|---|---|
| tests run | 20 | 期望 20 |
| failures | 1 | 期望 0 |
| skipped | 0 | — |
| `INSTRUMENTATION_CODE` | -1 | 非判据 |
| 12 类日志签名命中 | 0 | 期望 0 |

逐例（`STATUS_CODE 0=通过，-2=失败`）：

```text
aMissingVisualObjectAndASyntaxErrorAreInvalidPrograms 0
prepareOverTheContentRequestBudgetIsRejected 0
renderCannotReferenceContentNotFrozenInThisTransaction 0
aSecondTransactionCannotObserveGlobalsDeclaredByTheFirst 0
bootstrapClosesDateAndReplaysMathRandomDeterministicallyFromTheSeed 0
globalsOutsideTheSandboxDoNotExist 0
aConcurrentExecutionIsRejectedAsBusy 0
forgedOutOfMemoryMessageIsInvalidProgram 0
aProgramRendersWithFrozenFrameInputAndFrozenContentReferences 0
issue240SelfQueuingJobIsKnownOpenAndLeaksOnlyDaemonThreads 0
concurrentCloseAndExecuteRoundsNeverReportAnUnexpectedOutcome 0
stringLengthCapIsNotAHeapOutOfMemory 0
anInfiniteLoopIsCooperativelyInterruptedAndReportedAsAPhaseTimeout 0
memoryOverTheHeapBudgetIsRejectedAsAMemoryLimit 0        # P1 绿
theHostAbandoningAnInFlightTransactionFreesTheSingleFlightSlot 0
closeDuringAnInFlightTransactionWaitsAndRejectsIt 0
heapExhaustionWithLostErrorMessageIsAttributedByMemoryUsage -2   # P2 红
closingIsIdempotentAndExecuteAfterCloseIsRejected 0
aJsRuntimeErrorIsReportedAsABoundedDiagnosticNotAnException 0
nullThrownAtLowMemoryIsUnattributedInvalidProgram 0      # P4 绿
```

## 4. 关键输出（有界）

P1 断言全过（测试状态 0），说明设备上 64 MiB ArrayBuffer 路径拿到可读的 `InternalError: out of memory`，诊断含 `16777216`。

P2 实际输出（这是批次 22 缺失的关键事实）：

```text
java.lang.AssertionError: actual: INVALID_PROGRAM:
  [UNATTRIBUTED] phase prepare failed with a message-less engine error
  (raw=null, mallocSize=177216, mallocLimit=16777216)
  expected:<MEMORY_LIMIT> but was:<INVALID_PROGRAM>
  at QuickJsVisualExecutionEngineInstrumentedTest.kt:285
```

P3/P4/P5 均绿：`string too long` 归 `INVALID_PROGRAM`、低堆 `throw null` 带 `[UNATTRIBUTED]`、伪造 `RangeError: out of memory` 不被误报。

## 5. 失败 / 跳过 / 异常

- **P2 红且原因明确**：`raw=null` 符合"消息丢失"分支，但读到的堆只有 `mallocSize=177216`（约 1.06% of 16 MiB），远低于 90% 阈值，因此分类器按设计落到 `UNATTRIBUTED → INVALID_PROGRAM`。这不是分类器阈值问题，而是**夹具在设备上没有把堆推到上限**（或在错误处理/堆快照前已回收到 177 KiB）。KD-70 的 P2 中央分支未成立。
- 该结果排除了批次 22 无法区分的两种可能："消息仍是 null 且堆证据不足"已确认；不能据此断言 `memoryUsage` 读取时机错误，因为同路径 P1 可读。
- issue #240 观察用例仍按预期"5 s 内不返回"并只泄漏 daemon 线程（测试绿是"已知形态"而非"可终止"）。
- 无 skipped；12 类签名 0 命中。

## 6. 脱敏与去噪

同 E-01。证据目录统一扫描见 `00-index.md` §6。

## 7. 判定

**FAIL（19/20）**。KD-70 设备判据仍未达成：P1 的 `MEMORY_LIMIT` 主路径已在设备成立（比批次 22 更明确），但 P2 的"消息丢失 + 堆证据"分支实际拿到 `mallocSize=177216`，不能归因为堆上限。后续要么换能稳定推满堆的夹具（并在注释里解释为何 500k 小对象在 AAR 上会提前失败），要么按 KD-70 Fix direction ② 先裁定等价判据；**不得下调 90% 阈值**。

## 8. 原始全量（不入仓）

- 路径：`build/device-track-batch23/step02-quickjs-oom.log`、`step02-quickjs-oom-logcat.txt`
- 行数：353 行 / 21.2 KiB；logcat 193 行 / 27.7 KiB
- 生成命令：见 §2
