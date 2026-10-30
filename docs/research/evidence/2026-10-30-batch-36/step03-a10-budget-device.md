# E-03 A-10 派发预算边界（设备真库，3 例）

| 字段 | 值 |
|---|---|
| evidence-id | E-03 |
| convention-version | 1 |
| batch | 2026-10-30-batch-36 |
| step | 步 20（`step20-a10-budget`） |
| gate-or-kd | 计划任务 3（A-10 逐阈值边界）设备半边 |
| verdict | PARTIAL |
| executed-at | 2026-10-30T08:44–08:45+08:00（宿主 mtime） |
| operator | 设备轨（原始日志执行者）；本文件由证据落盘员摘录 |
| head | `4f11cd5432e6c259038fc92bd2e975aee8d304b4` |
| worktree | 706 项（仅记录） |
| device-serial | `emulator-5562` |
| device-avd | 未采集 |
| device-api | 34 |
| device-abi | x86_64 |
| device-page-size | 4096 |
| apk | data 测试包 `core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk` |
| apk-sha256 | `269e2a9a0fb750e2e945e6231111d5fc477f00af5bb1eb40248a294415e4f5b3` |
| apk-version | —（未读） |
| exit-code | 未固化（同 E-01 §5） |
| raw-log | `build/device-track-batch36/step20-a10-budget.log`（99 行）+ `step20-a10-budget-logcat.txt`（17,143 B） |
| record-ref | `docs/research/2026-10-30-batch-36-record.md`（未落盘） |
| sanitized | 是（本批 `00-index.md` §7） |

## 1. 目的与判据

用真 Room 库验证 A-10 的派发预算边界：第 7 次操作派发被拒且报真实计数、换方法不重置计数、图像预算耗尽后讲题终答位仍可用。用例源：`core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/visual/FigureDispatchBudgetBoundaryInstrumentedTest.kt`（`@Test` 3 个：`:53`、`:84`、`:122`），用 `RoomFigureDispatchLedger`（`:51-52`）+ `withDatabase` 真库。

## 2. 命令（可复跑）

```bash
export MSYS_NO_PATHCONV=1
adb -s emulator-5562 logcat -c
adb -s emulator-5562 shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.data.visual.FigureDispatchBudgetBoundaryInstrumentedTest \
  com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5562 logcat -v threadtime -d > build/device-track-batch36/step20-a10-budget-logcat.txt
```

- 工作目录：仓库根；构建输入同 E-02

## 3. 结果摘要

| 指标 | 值 | 判据 |
|---|---|---|
| tests run | 3 | 期望 3 |
| failures | **1** | 期望 0 |
| skipped | 0（日志内 0 条 skip/assume 行） | 0 |
| 崩溃签名 | 0（`step20-a10-budget-logcat.txt` 九类模式 0 命中） | 0 |

逐例：

| 方法 | 结果 |
|---|---|
| `aSeventhOperationDispatchIsDeniedWithTheRealCount`（`:53`） | 通过 |
| `switchingMethodDoesNotResetTheOperationDispatchCount`（`:84`） | 通过 |
| `theChatFinalAnswerSlotSurvivesFigureBudgetExhaustion`（`:122`） | **失败** |

## 4. 关键输出（有界）

```text
step20-a10-budget.log:95  FAILURES!!!
step20-a10-budget.log:96  Tests run: 3,  Failures: 1

java.lang.IllegalArgumentException: Only a queued model task may reserve a remote dispatch
  at com.tingyun.smartmistakebook.core.database.ReserveModelTaskRemoteDispatchCommand.<init>(ModelTaskDatabasePort.kt:52)
  at …FigureDispatchBudgetBoundaryInstrumentedTest$…$1$1.invokeSuspend(FigureDispatchBudgetBoundaryInstrumentedTest.kt:149)
  at …FigureDispatchBudgetBoundaryInstrumentedTest.theChatFinalAnswerSlotSurvivesFigureBudgetExhaustion(FigureDispatchBudgetBoundaryInstrumentedTest.kt:122)
```

## 5. 失败 / 跳过 / 异常

- **失败点**：用例在图像预算耗尽后用 `database.reserveModelTaskRemoteDispatch(...)`（`:149`）占第 6 个名额，`ReserveModelTaskRemoteDispatchCommand` 的前置校验拒绝。
- **归因（本批新增用例夹具，非产品缺陷）**：生产守卫要求 `expectedStatus == ModelTaskStatus.QUEUED`（`core/database/.../ModelTaskDatabasePort.kt:52-54`）；夹具 `seedModelTask`（`:173`）用 `createModelTask`，其落库初态为 `WAITING_FOR_MODEL`（`core/database/.../dao/ModelTaskTransactionDao.kt:204`），并把 `created.snapshot.status` 原样传入（`:194`）。夹具没有把父任务推进到 `QUEUED`，守卫行为符合其不变式；**不是**预算语义被证伪。
- **代价**：A-10 的"耗尽后终答位仍可用（第 6 个名额）"这条子门**未被证明**；不得以两例通过代替。
- 断言未放宽、用例未删。

## 6. 脱敏与去噪

- 删除：协程内部帧截断；重复的 `INSTRUMENTATION_STATUS` 头行未全量转录。
- 保留：`FAILURES!!!`、`Tests run`、失败首行与项目帧、`INSTRUMENTATION_CODE`。
- 扫描：见 `00-index.md` §7；二进制/截图：无。

## 7. 判定

PARTIAL：3 例中 2 例通过（第 7 次派发拒绝、换方法不重置），1 例失败且失败点在夹具状态前置（父任务未置 `QUEUED`），A-10 的"耗尽→终答位保留"子门未获证据。未覆盖：128 KiB 累计源码、≤6 布局尝试、10 s 累计墙钟、两份最终图、文字降级等阈值（其中 128 KiB/513 KiB 等 JVM 向量见 E-05）。
