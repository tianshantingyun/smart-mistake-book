# E-02 V-01 全链设备用例（4 例：生成 / 生产装配未接通 / 冻结重放 / PDF）

| 字段 | 值 |
|---|---|
| evidence-id | E-02 |
| convention-version | 1 |
| batch | 2026-10-30-batch-36 |
| step | 步 19（`step19-v01-chain`） |
| gate-or-kd | 计划任务 5（V-01 全链设备绿，或降级登记） |
| verdict | FAIL |
| executed-at | 2026-10-30T08:44+08:00（宿主 mtime；logcat 设备时钟见 §6） |
| operator | 设备轨（原始日志执行者）；本文件由证据落盘员摘录 |
| head | `4f11cd5432e6c259038fc92bd2e975aee8d304b4` |
| worktree | 706 项（仅记录） |
| device-serial | `emulator-5562` |
| device-avd | 未采集 |
| device-api | 34 |
| device-abi | x86_64 |
| device-page-size | 4096 |
| apk | data 测试包 `core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk` |
| apk-sha256 | `269e2a9a0fb750e2e945e6231111d5fc477f00af5bb1eb40248a294415e4f5b3`（`four-apk-sha.txt`） |
| apk-version | —（未读） |
| exit-code | 未固化（同 E-01 §5；判定用 summary 行） |
| raw-log | `build/device-track-batch36/step19-v01-chain.log`（171 行）+ `step19-v01-chain-logcat.txt`（28,119 B） |
| record-ref | `docs/research/2026-10-30-batch-36-record.md`（未落盘） |
| sanitized | 是（本批 `00-index.md` §7） |

## 1. 目的与判据

验证 V-01 纵向链：tool proposal → coordinator → 执行引擎 → 真实度量 → compiler → painter → 保存 / 重启恢复 → PDF，全程 0 Provider 派发。用例源：`core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/visual/V01VisualChainInstrumentedTest.kt`（`@Test` 4 个：`:138` 生成腿、`:238` 生产装配未接通、`:271` 冻结重放、`:317` PDF）。

## 2. 命令（可复跑）

```bash
export MSYS_NO_PATHCONV=1
adb -s emulator-5562 logcat -c
adb -s emulator-5562 shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.data.visual.V01VisualChainInstrumentedTest \
  com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5562 logcat -v threadtime -d > build/device-track-batch36/step19-v01-chain-logcat.txt
```

- 工作目录：仓库根
- 构建输入：`core/data` debug androidTest 包（E-01 §3 同批四包）

## 3. 结果摘要

| 指标 | 值 | 判据 |
|---|---|---|
| tests run | 4 | 期望 4 |
| failures | **2** | 期望 0 |
| skipped | 0（日志内 0 条 skip/assume 行） | 0 |
| 崩溃签名 | 0（`step19-v01-chain-logcat.txt` 九类模式 0 命中） | 0 |

逐例：

| 方法 | 结果 |
|---|---|
| `aJsProposalThroughTheCoordinatorRendersWithZeroRemoteDispatchWitnesses`（`:138`） | 通过 |
| `theProductionAssemblyWithoutAnInjectedEngineKeepsJsUnavailableWithoutAuditRows`（`:238`） | 通过 |
| `theFrozenDrawCommandsReplayToTheSameNonBlankBitmapBeforeAndAfterARealReopen`（`:271`） | **失败** |
| `theRestoredDocumentWritesAPdfPassingIntegrityAndRendererReadback`（`:317`） | **失败** |

## 4. 关键输出（有界）

```text
step19-v01-chain.log:167  FAILURES!!!
step19-v01-chain.log:168  Tests run: 4,  Failures: 2

java.lang.IllegalStateException: one label entry must be measured
  at …V01VisualChainInstrumentedTest.requirePresent(V01VisualChainInstrumentedTest.kt:898)
  at …V01VisualChainInstrumentedTest$ChainArtifacts.labelResolver(V01VisualChainInstrumentedTest.kt:550)
  at …V01VisualChainInstrumentedTest$…$1.invokeSuspend(V01VisualChainInstrumentedTest.kt:274)   # 冻结重放例
  at …V01VisualChainInstrumentedTest$…$1.invokeSuspend(V01VisualChainInstrumentedTest.kt:328)   # PDF 例
```

## 5. 失败 / 跳过 / 异常

- **失败点**：两个用例都在 `ChainArtifacts.labelResolver()` 的 `requirePresent(measurer.measured.singleOrNull(), …)`（`:549`–`:550`，`requirePresent` 定义 `:898`）抛 `IllegalStateException`。
- **归因（本批新增用例，非产品回归）**：`runChainToRestoredReplay()` 复用同一个 `DeviceTextMeasurer`（`:411`）并让程序执行两次——协调器 `generate` 内部一次、测试为取 drawOps 再 `engine.execute` 一次（`:432`）；度量器每次 `measure` 都向 `measured` 追加（`:636`，`contentId = "c$index"`）。因此 `measured` 有 2 条，`singleOrNull()` 为 null。这是**用例夹具/断言与"整链只度量一次"假设不一致**；断言本身未放宽（按纪律不删例、不改成功含义）。
- **通过的两例**不使用 `labelResolver()`，故不受影响——也说明失败不是整链断路。
- **语义底线未满足（独立于红绿）**：本套件注入的是 `core/data` 的进程内 `QuickJsVisualExecutionEngine`（`V01VisualChainInstrumentedTest.kt:667-668`；类声明 `QuickJsVisualExecutionEngine.kt:168`），不是 app 侧隔离服务客户端 `VisualJsClientEngine(context)`（`app/.../visualjs/VisualJsClientEngine.kt:100`）；套件对 `com.tingyun.smartmistakebook.visualjs` 的 import 计数为 **0**。判据 §2.1① 要求"真 `VisualJsClientEngine`（`:visual_js` 隔离进程）"——该语义即使转绿也未满足，须与实现轨单独结清。

## 6. 脱敏与去噪

- 删除：堆栈中系统/协程帧已截断（保留项目包名帧）。
- 保留：`FAILURES!!!`、`Tests run`、`INSTRUMENTATION_CODE: -1`、失败首行与项目帧。
- 时间：logcat 首行 `10-30 00:44:24`（设备时钟，较宿主约 −8h）；宿主窗以 mtime 记。
- 扫描：见 `00-index.md` §7；二进制/截图：无。

## 7. 判定

FAIL：4 例中 2 例失败（`Tests run: 4, Failures: 2`），失败点为用例夹具缺陷（度量条目累积 2 条），非产品断言被证伪；另：注入引擎非隔离 `VisualJsClientEngine`，V-01 的"隔离进程"语义仍未覆盖。未覆盖项：UI 卡片段按计划用测试内最小渲染替代（文件 KDoc 自述），非生产 UI 路径。
