# E-02 QuickJS 引擎全量（KD-70 P1–P5，批次 24 T2 新夹具）

| 字段 | 值 |
|---|---|
| evidence-id | E-02 |
| convention-version | 1 |
| batch | 2026-10-26-batch-24 |
| step | 二（KD-70） |
| gate-or-kd | KD-70（P2 消息丢失 + 堆比值归属） |
| verdict | PASS（20/20；P2 新形状成立，精确 `mallocSize` 未打印，见 §7） |
| executed-at | 2026-10-26T21:34:10+08:00 |
| operator | 设备验证轨（subagent，批次 24） |
| head | 5bc8bdf868e1296049ab901cf1da69e136062006 |
| worktree | dirty（他线在飞；含 T2 未提交修复） |
| device-serial | emulator-5554 |
| device-avd | test_device |
| device-api | 34 |
| device-abi | x86_64 |
| device-page-size | 4096 |
| apk | `core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk`（自含测试 APK） |
| apk-sha256 | `8ff654e95686ea5ef89a73cc797f6ae7688c720877b01a50700dff6d5d605281` |
| apk-version | — |
| exit-code | 0（adb 进程退出码；判据是 summary） |
| raw-log | `build/device-track-batch24/step02-kd70.log`（289 行）、`step02-kd70-logcat.txt`（138 行） |
| record-ref | `docs/research/2026-10-26-batch-24-record.md` §4 |
| sanitized | 是（§6；扫描 0 命中） |

## 1. 目的与判据

按批次 24 T2 的新 P2 夹具（链表头节点每轮钉到 `globalThis.__visualHeapProbeHead`）复跑
`QuickJsVisualExecutionEngineInstrumentedTest` 全量 20 例，重点记录：

- P1 `memoryOverTheHeapBudgetIsRejectedAsAMemoryLimit`：`MEMORY_LIMIT` + 诊断含 `16777216` 与 `out of memory`；
- **P2 `heapExhaustionWithLostErrorMessageIsAttributedByMemoryUsage`**：`MEMORY_LIMIT` + `attributed by memoryUsage` + `mallocLimit=16777216` + `PENDING_CALIBRATION`，且 `mallocSize != -1`；
- P3/P4/P5 负控与其余契约用例不回归。

批次 23 基线：全量 19/20，唯一红为 P2，实际诊断
`INVALID_PROGRAM: [UNATTRIBUTED] ... mallocSize=177216, mallocLimit=16777216`（旧夹具只把链挂在局部变量上，OOM 展开时整链被同步释放）。

## 2. 命令（可复跑）

```bash
A=C:/Android/Sdk/platform-tools/adb.exe
export MSYS_NO_PATHCONV=1
$A -s emulator-5554 install -r -t core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk
$A -s emulator-5554 logcat -c
$A -s emulator-5554 shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.data.visual.QuickJsVisualExecutionEngineInstrumentedTest \
  com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner
$A -s emulator-5554 logcat -v threadtime -d > build/device-track-batch24/step02-kd70-logcat.txt
```

- 工作目录：仓库根
- 安装：`Success`，退出码 0
- 运行耗时：约 10 s（`Time: 8.275`）

## 3. 结果摘要

| 指标 | 值 | 判据 |
|---|---|---|
| tests run | 20 | 期望 20 |
| failures | 0 | 期望 0 |
| skipped | 0 | 期望 0 |
| `OK (20 tests)` | 是 | 是 |
| `INSTRUMENTATION_CODE` | -1 | 非判据（adb 侧 exit=0） |
| 7 类崩溃签名命中 | 0 | 期望 0 |

逐例状态（全部 `1 → 0`）：

```text
aProgramRendersWithFrozenFrameInputAndFrozenContentReferences
bootstrapClosesDateAndReplaysMathRandomDeterministicallyFromTheSeed
anInfiniteLoopIsCooperativelyInterruptedAndReportedAsAPhaseTimeout
memoryOverTheHeapBudgetIsRejectedAsAMemoryLimit
heapExhaustionWithLostErrorMessageIsAttributedByMemoryUsage
stringLengthCapIsNotAHeapOutOfMemory
nullThrownAtLowMemoryIsUnattributedInvalidProgram
forgedOutOfMemoryMessageIsInvalidProgram
globalsOutsideTheSandboxDoNotExist
aSecondTransactionCannotObserveGlobalsDeclaredByTheFirst
aMissingVisualObjectAndASyntaxErrorAreInvalidPrograms
aJsRuntimeErrorIsReportedAsABoundedDiagnosticNotAnException
renderCannotReferenceContentNotFrozenInThisTransaction
prepareOverTheContentRequestBudgetIsRejected
issue240SelfQueuingJobIsKnownOpenAndLeaksOnlyDaemonThreads
aConcurrentExecutionIsRejectedAsBusy
theHostAbandoningAnInFlightTransactionFreesTheSingleFlightSlot
closingIsIdempotentAndExecuteAfterCloseIsRejected
closeDuringAnInFlightTransactionWaitsAndRejectsIt
concurrentCloseAndExecuteRoundsNeverReportAnUnexpectedOutcome
```

## 4. 关键输出（有界）

```text
INSTRUMENTATION_STATUS: numtests=20
...
Time: 8.275

OK (20 tests)

INSTRUMENTATION_CODE: -1
```

**P2 实际形态（由通过断言约束，非日志打印）.** 用例通过意味着以下断言在设备上全部成立
（`core/data/src/androidTest/.../QuickJsVisualExecutionEngineInstrumentedTest.kt:315-325`）：

```text
rejected.reason == MEMORY_LIMIT
diagnostics.contains("attributed by memoryUsage")
diagnostics.contains("mallocLimit=16777216")
diagnostics.contains("PENDING_CALIBRATION")
!diagnostics.contains("mallocSize=-1")
```

分类器的比值判据是 `mallocSize * 100 >= mallocLimit * 90`（`VisualJsFailureClassification.kt:54`），
`mallocLimit=16777216` ⇒ **`mallocSize >= 15,099,495`（≥90.0%）**。精确读数没有被测试打印，
因此"精确到字节的 `mallocSize`"在本步是 **UNVERIFIED**；可确证的是它**不小于 15,099,495**，
上界未打印。

## 5. 失败 / 跳过 / 异常

无失败、无跳过。P2 由批次 23 的红转为绿，且绿的是"消息丢失 + 比值证据"这一条中央分支，
不是"有界拒绝即可"的放宽：

- 用例断言 `mallocSize != -1`（快照真的取到）；
- `PENDING_CALIBRATION` 仍在，说明分类器没有把尚未校准的比值伪装成已校准；
- 90% 阈值与 `MEMORY_LIMIT` 映射未改动（T2 自述与代码 `VisualJsFailureClassification.kt:54` 一致）。

`issue240SelfQueuingJobIsKnownOpenAndLeaksOnlyDaemonThreads` 绿仍是**已知形态观察**（5 s 内不返回 + 后续 BUSY），不是"可终止"证据。

## 6. 脱敏与去噪

- 删除：adb 安装进度行。
- 保留：全部逐例状态、summary、crash 签名零计数。
- 扫描：见 `00-index.md` §6（0 命中）。
- 二进制/截图：无。

## 7. 判定

**PASS（20/20）**：批次 24 T2 的新 P2 夹具在 emulator-5554 / API 34 / 上述 APK 上成立，P2
得到 `MEMORY_LIMIT`（比值证据 + `PENDING_CALIBRATION`），批次 23 的 `mallocSize=177216`
形状未再出现。

未覆盖 / 保留项：

1. 精确 `mallocSize` 未打印（测试无 println），只由断言约束为 ≥15,099,495（上界未打印）；
2. 只测 x86_64 / API 34；arm64、16KB 页上的 QuickJS 堆语义未测（16KB 步只验证装载）；
3. `PENDING_CALIBRATION` 是设计上的"尚未设备校准"标记，是否关闭 KD-70 由主会话按判据裁定，本步不做关闭动作；
4. `issue #240` 的自续 job 仍泄漏 daemon 线程，与 KD-66 的关闭门无关。

## 8. 原始全量（不入仓）

- 路径：`build/device-track-batch24/step02-kd70.log`（289 行 / 约 20 KiB）、`step02-kd70-logcat.txt`（138 行 / 约 12 KiB）
- 生成命令：见 §2
- 说明：`build/` 被 `.gitignore` 忽略；关闭判据只认本文件。
