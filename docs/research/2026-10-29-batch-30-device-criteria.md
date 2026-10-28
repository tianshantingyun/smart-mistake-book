# 批次 30 T1 设备判据：五套件计数 / KD-78 ④ / KD-79 / 命令链 / 归因规则

> **只读核验**：本轮未运行 Gradle、未编译、未安装/执行任何仪器化或设备用例、未做 git 写操作。
> 只读命令：`grep -c "@Test"`、`sed`/`Read` 源码、`git status --porcelain`、`sha256sum`、
> `unzip -p … | grep -a`、`ls -l`。设备执行归设备轨（计划
> `.zcode/plans/plan-2026-10-29-batch-30-device-persist-fixes.md:40`、`:15-16`）。
>
> **判据输入**：计划 §1 A（`:15-16`）、批次 29 设备证据（`docs/known-defects.md:2902`、`:2915`）、
> 批次 30 基线（`docs/research/2026-10-29-batch-30-baseline.md:100-121`）、现树源码与测试实读。
>
> **快照时点**：2026-10-29。**PDF 主源与 PDF 测试在快照期间被他轨改动**（见 §1.3），PDF 判据以
> "落码后重建的 APK" 为执行对象；本文件记录的 **16** 是 01:08 复读的测试文件计数，不是实现版本号。

---

## 0. 结论速览

| 项 | 现树判据 | 状态 |
|---|---|---|
| painter `VisualDrawOpsPainterInstrumentedTest` | `grep -c "@Test"` = **24** → `OK (24 tests)` | 判据成立；**设备 UNVERIFIED** |
| 隔离 `VisualJsIsolationInstrumentedTest` | **13** → `OK (13 tests)` | 判据成立；**设备 UNVERIFIED** |
| flags 跨侧 `VisualMeasureReplayPaintFlagsInstrumentedTest` | **2** → `OK (2 tests)` | 判据成立；**设备 UNVERIFIED** |
| PDF `VisualDrawOpsPdfPainterInstrumentedTest` | **16** → `OK (16 tests)` | 基线快照为 13；C.6 轨并发落码 +3（§1.3）；**设备 UNVERIFIED** |
| 一致性门 `VisualDrawOpsParityInstrumentedTest` | **11** → `OK (11 tests)` | 计数成立；**仍是原型门，不是产品/发版结论** |
| 旧 APK 负向门 | 现盘 `data-debug-androidTest.apk` **不含**新方法/跟踪流符号 | 该 APK **必须判旧包**（§5.3 实测） |

**计数证据（本轮实跑）**

```bash
grep -c '@Test' core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualDrawOpsPainterInstrumentedTest.kt   # 24
grep -c '@Test' app/src/androidTest/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsIsolationInstrumentedTest.kt                  # 13
grep -c '@Test' app/src/androidTest/kotlin/com/tingyun/smartmistakebook/visualjs/VisualMeasureReplayPaintFlagsInstrumentedTest.kt      # 2
grep -c '@Test' core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualDrawOpsPdfPainterInstrumentedTest.kt # 16（01:08 并发落码后；快照起点 13）
grep -c '@Test' core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualDrawOpsParityInstrumentedTest.kt  # 11
```

- painter/隔离/flags/parity 与基线 §4 的现树列逐项一致（`docs/research/2026-10-29-batch-30-baseline.md:106-111`）；
  **PDF 例外**：基线为 13，01:08 复读为 16（C.6 并发落码 +3，§1.3），以最终落码计数为准。
- **不得沿用批次 29 设备数字**：painter 21、parity 10、隔离 13（12 红）、PDF 13（1 红）、
  flags 2（1 红）都是在**修复前源**上跑的（`docs/known-defects.md:2902`、`:2915`；
  基线 `:102-111`）。

---

## 1. 现树计数、证据时点与并发改动

### 1.1 设备证据与现树的时点差（逐套件）

| 套件 | 批次 29 设备证据 | 现树 @Test | 差 | 处置 |
|---|---|---|---|---|
| painter | 21（1 红：flags 观测面） | 24 | +3 | 必须重建重跑；旧 20 绿不覆盖新增 3 例 |
| 隔离 | 13（12 红：warm-up 全灭） | 13 | 0 | 必须重跑；12 红的因由已修，但绿轮未出 |
| flags 跨侧 | 2（1 红） | 2 | 0 | 必须重跑 |
| PDF | 13（1 红：`pdfWriteFailureIsCoded`） | 16 | +3 | C.6 轨并发落码新增 mid-stream / 文件回读 / 只读目标 3 例；旧绿/旧红都不覆盖现树 |
| 一致性门 | 10（`OK (10 tests)`） | 11 | +1 | N08 只在树、未上设备（基线 `:116`） |

**结论**：五套件全部没有"现源上的绿轮"；设备轨不得复用任何批次 29 APK 或数字。

### 1.2 现盘 APK 已是旧包（本轮只读实测）

| APK | 文件时间 | sha256（宿主） | 结论 |
|---|---|---|---|
| `app/build/outputs/apk/strictOffline/debug/app-strictOffline-debug.apk` | 2026-10-28 22:31:57 | `05a5b8bd28ece00b1d84ea086bc9b99a414d36e4111201214e6994c921cd24d7` | 早于 PDF 主源 2026-10-29 01:06 改动 |
| `app/build/outputs/apk/androidTest/strictOffline/debug/app-strictOffline-debug-androidTest.apk` | 2026-10-28 22:32:08 | `229cd58eb2fb68b83684498b75fbc21582544967c9a68fc6ad1ffd074f6edd68` | 含新 flags 方法名（见 §5.3），仍须随主包重建 |
| `core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk` | 2026-10-28 22:31:57 | `4d152b5afbd9f3fa5eb451774370fe56d683a1f17a7e42101c9a67e6fa72cd81` | **判旧包**：新方法/跟踪流符号 0 命中（§5.3） |

`unzip -p … 'classes*.dex' | grep -a -c` 实测（旧核心包）：

```text
drawRenderedReplaysTheFrozenCommandSequence                 0
PdfWriteFailureTrackingStream                                0
n08UniformInteriorColorCastStaysRedInsteadOfBeingDowngraded… 0
theReplayTextPaintKeepsThe…                                  1
```

即该 APK 是**批次 29 修复前/修复中**的产物，`grep -c "@Test"` 的现树 24/13/16/11 对它不成立。

### 1.3 快照期间的并发改动（必须登记，不得读成已完成）

`git status --porcelain` 实测：

```text
 M core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualDrawOpsPdfPainter.kt
?? core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/PdfWriteIntegrity.kt
```

- `VisualDrawOpsPdfPainter.kt` 现有 `write(OutputStream)`（D1∧D2）与
  `writeFile(File)`（D1∧D2∧D3：临时件 → flush+fd.sync → 长度/尾标回读 → `PdfRenderer`
  页数回读 → `renameTo`）：`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualDrawOpsPdfPainter.kt:76`、`:108`、`:185`、`:243`。
- `PdfWriteIntegrity.kt` 是未跟踪新文件（`PDF_TRAILER_MARKER`、`PdfWriteTelemetry`、
  `PdfWriteFailureTrackingStream`、`pdfWriteFailureReason`、`pdfFileFailureReason`）。
- **测试已在同一时段同步落码（本轮 01:08 复读）**：`VisualDrawOpsPdfPainterInstrumentedTest.kt`
  由 13 例变 **16 例**，新增 `pdfWriteFailureMidStreamKeepsTheFirstFailureText`（`:458`）、
  `pdfFileWriteRoundTripsThroughReadback`（`:477`）、`pdfReadOnlyTargetFailsClosedBeforeAnyWrite`
  （`:499`）；JVM 另新增未跟踪 `PdfWriteIntegrityTest.kt`（15 例，helper 级）。
- **仍缺的设备判据**：磁盘满（ENOSPC）模拟、静默短写、页数回读不符注入、`renameTo` 失败注入
  在设备面仍无用例；JVM 15 例只覆盖 `pdfFileFailureReason`/尾窗/长度等纯层行为，不能替代设备
  文件系统与 `PdfRenderer` 回读。
- 按计划 §2 `:38` 的"同一落点单一拥有者"纪律，本文只登记；设备轨必须以**最终落码**后的
  `grep -c "@Test"` 与 dex 方法名重取判据（若 16 再变，重取，不沿用本文数字）。

---

## 2. 五套件套件级硬判据

所有套件共用：

1. `am instrument` summary 行必须是目标字符串（`OK (N tests)`），**0 failures / 0 errors / 0 skipped**；
2. `INSTRUMENTATION_CODE: -1` **不是判据**（AndroidJUnitRunner 恒返回 RESULT_OK，失败也常为 -1）；
3. shell 退出码**不是判据**（套件内断言失败时 `am instrument` 仍可能 exit=0）；
4. 每套件独立 `logcat -c` → 执行 → `logcat -d` 落 raw log；
5. 9 类崩溃签名全 0（清单见 §5.5）；
6. **禁用 `connectedAndroidTest`**（计划 §2 `:40`）。

### 2.1 painter 24 例（逐例判据）

文件：`core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualDrawOpsPainterInstrumentedTest.kt`。
夹具一律 `JSON → VisualDrawOpsValidator → VisualDrawOpsDocument → painter`；位图
`pxPerDu=2`、viewBox 48×32 → 96×64。行号为现树。

| # | 用例（行） | 设备必须成立 |
|---|---|---|
| 1 | `theSameOpsPaintIdenticalPixelsWithinOneMethod`（`:169`） | 同 painter 实例连渲两遍，`getPixels` 整阵逐位相等；宽 96 |
| 2 | `theSameOpsPaintIdenticalPixelsInAFreshTestMethod`（`:181`） | 全新 painter + 全新位图两次渲染逐位相等 |
| 3 | `aShearMatrixPlacesInkByContractColumnOrder`（`:192`） | `(5,8)=0xFFFF0000`、`(8,5)=0x00000000`（列序 `[a,c,e,b,d,f]`） |
| 4 | `aNestedGroupClipCutsInRootSpace`（`:203`） | `(10,4)=0xFF00AA00`、`(30,4)=0x00000000`（clip 根空间求交） |
| 5 | `aGroupLabelLandsAtTheProjectedRootSpacePosition`（`:215`） | `Paint.measureText("H")` advance > 0；`29..31+advance` 有墨；`0..26` 与 `36+advance..95` 零墨 |
| 6 | `halfAlphaFillsFloorTheQuantizedAlpha`（`:242`） | 中心 `(24,16)=0xFFFF8080`（floor 0.5×255=127）；角 `(1,1)=0xFFFFFFFF` |
| 7 | `anEightDigitColorAlphaMultipliesWithTheOpAlphaInsteadOfBeingOverwritten`（`:253`） | `#ff000080` 白底中心 `(24,16)=0xFFFF7F7F`；`(1,1)` 白（KD-78 ③ 设备正例） |
| 8 | `theReplayTextPaintKeepsTheExplicitlyPinnedFlagsMirroredByTheMeasureFingerprint`（`:264`） | `VisualDrawOpsPainter().textPaintFlagsForTest() == REPLAY_TEXT_PAINT_FLAGS == 0`（KD-78 ④） |
| 9 | `backgroundColorsTheCanvasAndNullLeavesItTransparent`（`:276`） | `#123456` 时两角同色；`null` 时两角 `0x00000000` |
| 10 | `stateIsRestoredAfterGroupClose`（`:286`） | `(20,8)`/`(36,8)=0xFFFF0000`、`(48,8)=0x00000000` |
| 11 | `aStrokePhasePaintsOutsideTheFillBand`（`:298`） | `(17,32)`/`(40,32)=0xFF00AA00`、`(8,32)=0x00000000` |
| 12 | `aShorterDashAfterALongerDashOnTheSamePainterKeepsItsOwnPattern`（`:310`） | victim strip 有墨且与"只画 victim"逐位相等（无 donor 残尾） |
| 13 | `aForeignSchemaVersionFailsClosed`（`:332`） | 抛 `PAINTER_SCHEMA_VERSION` |
| 14 | `aRestoreWithoutSaveFailsClosedInsideTheWalk`（`:347`） | 抛 `PAINTER_BALANCE`，消息含 `ops[0]` |
| 15 | `aStrokeOnlyPathStillFailsClosedInTheReplayFallback`（`:363`） | hand-built 文档抛 `PAINTER_STROKE_WITHOUT_FILL`，含 `ops[0]` |
| 16 | `anOddDashStillFailsClosedInTheReplayFallback`（`:376`） | `dash=[2,4,2]` 抛 `PAINTER_DASH_ODD`，含 `ops[0]` |
| 17 | `anEmptyDashStillFailsClosedInTheReplayFallback`（`:391`） | `dash=[]` 抛 `PAINTER_DASH_ODD`；实 dash 后跟空 dash 抛同码且含 `ops[1]`；**不得**出现无码 `ArrayIndexOutOfBoundsException` |
| 18 | `aStrokeOnlyPathWithAnOddDashReportsTheSameFirstGateAsTheGenerators`（`:428`） | 无 fill + 奇数 dash → `PAINTER_DASH_ODD`（门序与生成侧同码同序） |
| 19 | `aLabelWithoutResolvableContentFailsClosed`（`:450`） | 抛 `PAINTER_UNKNOWN_LABEL`，含 `ops[0]` |
| 20 | `aMathLabelFailsClosedThisBatch`（`:458`） | 抛 `PAINTER_LABEL_MATH`，含 `ops[0]` |
| 21 | `anUnusableReplayScaleFailsClosed`（`:478`） | `pxPerDu` 为 `0f`/`NaN`/`+Inf` 均抛 `PAINTER_BOUNDS` |
| 22 | `drawRenderedReplaysTheFrozenCommandSequence`（`:489`） | `validatedDocument` 与 `drawRendered` 两路像素逐位相等 |
| 23 | `drawRenderedFailsClosedOnMissingOrInvalidCommands`（`:511`） | `null` → `PAINTER_DRAW_COMMANDS` 且内嵌 `DRAWOPS_MISSING_COMMANDS`；截断 → 同外码且内嵌 `DRAWOPS_MISSING_FIELD` |
| 24 | `aLegalSceneGoesFromCompileToColoredBitmap`（`:543`） | 真引擎 compile → 指令非空 → 校验通过；角点白、dot 中心非白非透明、label 带有墨、全图有墨 |

**套件级**：`OK (24 tests)`、0 失败/0 错误/0 跳过；painter logcat 9 类签名 0；
`measure paint flags .* do not match the environment fingerprint` = 0。

### 2.2 隔离 13 例

文件：`app/src/androidTest/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsIsolationInstrumentedTest.kt`。

| # | 用例（行） | 关注面 |
|---|---|---|
| 1 | `workerIsIsolatedAndAProgramRoundTripsWithoutBeingKilledLater`（`:116`） | 进程隔离 + 程序往返 + 事后不被杀；`awaitReady()` 为真 |
| 2 | `aStaleGenerationIsRejectedAndAStaleTerminateIsANoOp`（`:163`） | 过期 generation 拒绝；过期 terminate 无副作用；`awaitReady()` |
| 3 | `theWatchdogKillsAHungWorkerAndAFreshGenerationHasNoBusyState`（`:191`） | 挂死 worker 被 watchdog 杀；新 generation 无 BUSY 残留；两次 `awaitReady()` |
| 4 | `theClientRebuildsAtMostOnceAfterConfirmedAbandons`（`:239`） | 确认放弃后最多重建一次 |
| 5 | `aColdStartAttachBeyondTheOldConnectBudgetStillRendersTheFirstProgram`（`:274`） | 冷启动超旧连接预算仍渲染首程序 |
| 6 | `theClientRebuildsOnceAfterADeathAndTheNewWorkerIsNotBusy`（`:289`） | 死亡后重建一次；新 worker 非 BUSY |
| 7 | `aHungWorkerReleasesTheCallerWithinHardPlus500ms`（`:373`） | 挂死 worker 在 hard deadline +500 ms 内释放调用方（KD-66 关闭门） |
| 8 | `aMalformedCallerRequestIsRejectedWithoutTakingTheFlightSlot`（`:445`） | 畸形请求被拒且不占在飞槽；`awaitReady()` |
| 9 | `anOverBudgetRequestEnvelopeIsRejectedByTheBoundedRead`（`:493`） | 超预算信封被有界读取拒绝；`awaitReady()` |
| 10 | `aSecondConcurrentRequestIsRejectedBusyWhileTheFirstIsInFlight`（`:523`） | 首个在飞时第二个并发请求得 BUSY；`awaitReady()` |
| 11 | `anExecuteAcquiredDuringWarmUpIsRefusedBoundedlyAndNeverWatchdogKilled`（`:565`） | warm-up 期 execute 有界拒绝、不被 watchdog 误杀；ready 后同 worker 可用；`awaitReady()` |
| 12 | `aFirstConnectTimeoutFailsHonestlyAndTheNextExecuteRetriesWithoutLatching`（`:599`） | 首连超时如实失败、下次 execute 重试、不锁存 |
| 13 | `aReadyTimeoutFailsHonestlyWithoutLatchingAndTheNextExecuteRebinds`（`:642`） | ready 超时如实失败、不锁存、下次重新 bind（fake binder） |

**套件级**（全部成立才 PASS）：

1. summary 行 **`OK (13 tests)`**，无 Failures/Error/Skipped；
2. isolation logcat 9 类签名全 0；
3. **`Start proc .*:visual_js` 的 pid 集合 = `visual_js warm-up finished` 的 pid 集合**（按 pid 1:1）；
4. `visual_js warm-up failed` = **0**；
5. **kill 全归因**：`hard deadline (1500 ms) for request` + `terminate(generation` +
   `the host unbound the visual_js worker` + `due to start instr` 四类之和 = spawn 数；
   出现无法归类的 `VisualJsService` 杀因 = 失败；AMS 的 `isolated not needed` 是系统回收，不计入；
6. 事后 `ps -A -o PID,NAME | grep visual_js` 为空。

> 批次 29 的失败形态（供归因对照）：`Start proc` 14、`warm-up finished` 0、`warm-up failed` 12、
> 四类杀因之和 1（`docs/known-defects.md:2902`）。修复后若仍是 0 ready，直接按 §7 判 C/D。

### 2.3 flags 跨侧 2 例

文件：`app/src/androidTest/kotlin/com/tingyun/smartmistakebook/visualjs/VisualMeasureReplayPaintFlagsInstrumentedTest.kt`。

| # | 用例（行） | 设备必须成立 |
|---|---|---|
| 1 | `theMeasuringPaintAndTheReplayTextPaintShareTheFingerprintedFlags`（`:43`） | `backend.paintFlagsForTest() == DEFAULT_PAINT_FLAGS`；`environment.paintFlags == 0`；`environment.fingerprint().endsWith("|paintFlags=0")`；`Paint().flags != 0`（显式装配必要性的常驻钉子） |
| 2 | `aFingerprintThatDisagreesWithTheActualPaintFailsClosed`（`:57`） | `environment(paintFlags=1)` 调 `forEnvironment` 抛 `VisualMeasurementEnvironmentException` 且消息含 `paintFlags=1` |

**套件级**：`OK (2 tests)`、0 失败/错误/跳过；flags logcat 9 类签名 0；
`measure paint flags .* do not match the environment fingerprint` = 0。
第 2 例是**负例**：它自身绿 = 坏指纹被拒；若它红（未抛/消息不含），KD-78 ④ 关闭不成立。

### 2.4 PDF 16 例（快照 13 + C.6 并发落码 3）

文件：`core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualDrawOpsPdfPainterInstrumentedTest.kt`。

| # | 用例（行） | 类别 | 设备必须成立 |
|---|---|---|---|
| 1 | `pdfPageSizeUsesCeilOfViewBoxTimesPtPerDu`（`:265`） | 正 | 1.0→48×32；1.5→72×48；0.3@7×5→3×2（ceil） |
| 2 | `pdfFillMatchesTheScreenOnTheNormalizedPixelGrid`（`:277`） | 正 | bbox ±1；内部色 ±2；y≥36 零墨（原点不翻转） |
| 3 | `pdfAndScreenShareTheSameGridAtAnyPageScale`（`:298`） | 正 | 1.0@scale2 与 2.0@scale1 同网格（bbox ±1、色 ±2） |
| 4 | `pdfBackgroundCoversTheRoundedUpPage`（`:315`） | 正 | 3×2 pt @scale10 = 30×20，四角背景色 ±2 |
| 5 | `pdfNestedGroupClipCutsInRootSpace`（`:331`） | 正 | clip 内 `(10..22,2..14)` 有墨；`x≥26,y≤15` 零墨 |
| 6 | `pdfGroupLabelLeavesInkAtTheProjectedRootSpacePosition`（`:342`） | 正 | advance > 0；label 带有墨；左界外零墨；bbox ±2 |
| 7 | `pdfHalfAlphaTracksTheScreenQuantizedAlpha`（`:374`） | 正 | 屏/PDF 中心色差 ≤3；g/b ∈ 118..136 |
| 8 | `pdfDoesNotPaintHitRegions`（`:391`） | 正 | 全图零墨（PDF 与屏同判） |
| 9 | `pdfRasterIsDeterministicAcrossTwoIndependentBuilds`（`:405`） | 正 | 两次构建栅格逐位相等；同字节回读两次逐位相等 |
| 10 | `pdfUnusablePtPerDuFailsClosed`（`:422`） | 负 | `0f`/`NaN`/`+Inf` 均抛 `PAINTER_PDF_PAGE_BOUNDS` |
| 11 | `pdfOversizePageFailsClosed`（`:439`） | 负 | 400 pt/du 抛 `PAINTER_PDF_PAGE_BOUNDS` |
| 12 | `pdfWriteFailureIsCoded`（`:447`） | 负 | `FailingOutputStream` → `PAINTER_PDF_IO`（KD-79 核心） |
| 13 | `pdfWriteFailureMidStreamKeepsTheFirstFailureText`（`:458`） | 负（C.6） | 首块后开始抛 → `PAINTER_PDF_IO`；消息含 `MID_STREAM_WRITE_FAILURE`；`out.acceptedBytes > 0`（证明是中途而非首写） |
| 14 | `pdfFileWriteRoundTripsThroughReadback`（`:477`） | 正（D3） | 目标文件存在且非空；含 `%%EOF`；回读页尺寸 48×32；无 `.pending-` 残留 |
| 15 | `pdfReadOnlyTargetFailsClosedBeforeAnyWrite`（`:499`） | 负（D3） | 目录 0555 → `PAINTER_PDF_IO`；消息含 `not writable`；目标未发布；fixture 目录可恢复删除 |
| 16 | `pdfReplayFailuresKeepTheScreenPainterCodeFamily`（`:527`） | 负 | stroke-only → `PAINTER_STROKE_WITHOUT_FILL`；奇数 dash → `PAINTER_DASH_ODD`；均含 `ops[0]` |

**套件级**：`OK (16 tests)`（若再落码则按新计数）、0 失败/错误/跳过；PDF logcat 9 类签名 0。

> **D3 覆盖面（快照）**：第 14/15 例已覆盖文件目标的正常发布、长度/尾标、`PdfRenderer` 页数
> 回读与只读目录 open 失败；**仍无设备用例**：磁盘满（ENOSPC）、静默短写、页数回读不符注入、
> `renameTo` 失败注入。因此 16 绿不能把 D3 全路径写成已覆盖；KD-79 的 §4 关闭表仍按缺口逐条判。

### 2.5 一致性门 11 例（原型门）

文件：`core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualDrawOpsParityInstrumentedTest.kt`。
**定位**：`PARITY_GATE scope=PROTOTYPE product-gate=no ci-gate=no release-gate=no`
（`VisualDrawOpsParityInstrumentedTest.kt:76-83`）。**不得**引用为产品门/发版结论。

| # | 用例（行） | 设备必须成立 |
|---|---|---|
| 1 | `thePrototypeGateJudgesEveryPositiveFixture`（`:98`） | 仪器确定性前置门通过；P01–P08 全部非红；需位移敏感类 gain ≥ `MIN_SHIFT_SENSITIVITY_GAIN`；F5 ≤ 2 |
| 2 | `backgroundNullIsObservedButNeverJudged`（`:135`） | 只观察；`GRID_WIDTH/HEIGHT` 断言通过；不进入判决面 |
| 3 | `n01OnePixelRasterShiftIsCaught`（`:154`） | P01/P02/P03/P05 右移 1 px **全部红**；`bestShift=(1,0)` |
| 4 | `n02SingleChannelShiftIsCaught`（`:172`） | P01 蓝通道 +16 **红**；`interiorMax ≥ 16` |
| 5 | `n03DroppedClipIsCaught`（`:187`） | P03 删 clip **红** |
| 6 | `n04LabelShiftIsCaught`（`:197`） | P06 label x+1du **红** |
| 7 | `n05GlyphSubstitutionIsCaught`（`:210`） | P06 "H"→"M" **红**（F1 或 F4 均可） |
| 8 | `n06AlphaMagnitudeChangeIsCaught`（`:222`） | P02 alpha 0.5→0.6 **红** |
| 9 | `n07DroppedOpsAreCaught`（`:235`） | P01 ops 清空 **红**（IoU=0） |
| 10 | `n08UniformInteriorColorCastStaysRedInsteadOfBeingDowngradedToF5`（`:244`） | 整块 +4 偏色 **F4_COLOR 红**，不得降级 F5 |
| 11 | `parityMetricsUseUnionForIouAndStayInsideErosionBounds`（`:274`） | IoU 并集分母 = 0.5；1×1 腐蚀后 interior=0；3×3 腐蚀后剩 1 |

**套件级判据**（全部成立才算"原型门当轮有效"）：

1. `OK (11 tests)`、0 失败/错误/跳过；
2. logcat 有且仅有一轮 `PARITY_GATE scope=PROTOTYPE product-gate=no ci-gate=no release-gate=no`；
3. `PARITY_ENV` 行含 `replayTextPaintFlags=0`；
4. `grep -c "PARITY_CONTROL"` = **10**（N01 的 P01/P02/P03/P05 四个子控 + N02–N07 各一；N08 用例内部直接断言 `F4_COLOR.isRed`，**不打印** `PARITY_CONTROL`），且每行含 `red=true`；
5. `PARITY_GATE_DESENSITIZED` = 0；`PARITY_SYSTEMATIC_DRIFT` = 0；`PARITY_F5` ≤ 2；
6. 绿轮 `PARITY_DIAGNOSTIC_WRITTEN` = 0（PNG 只在失败时写）；
7. 11 绿**不等于**产品门通过；它只证明"PDF 路径相对屏路径未引入新偏差"。

---

## 3. KD-78 ④ 关闭判据（逐条）

④ 的缺陷原文：measure/replay flags 不在环境指纹；回放 `textPaint` 假设平台默认 = 0，
而 API 34 实际 1283（`docs/known-defects.md:2882`、`:2902`）。修复落码：两侧显式装配 0
（`docs/known-defects.md:2904-2911`；现树 `VisualContentMeasuring.kt:469`、
`VisualDrawOpsPainter.kt:54`、`VisualDrawOpsPainterPure.kt:87`）。

> **范围**：以下只判 ④。KD-78 整条（①②③④）是否关闭，按 `docs/known-defects.md:2888` 四项逐项
> 核销；④ 绿**不自动**等于 KD-78 关闭。

| ID | 类型 | 用例/检查 | 通过条件 | 证据 |
|---|---|---|---|---|
| ④-P1 | 正例绿 | painter `theReplayTextPaintKeepsTheExplicitlyPinnedFlagsMirroredByTheMeasureFingerprint` | 真实 `textPaint.flags == REPLAY_TEXT_PAINT_FLAGS == 0` | painter log；`:264-272` |
| ④-P2 | 正例绿 | flags `theMeasuringPaintAndTheReplayTextPaintShareTheFingerprintedFlags` | 度量 Paint flags = 0；`environment.paintFlags = 0`；指纹以 `|paintFlags=0` 结尾；`Paint().flags != 0` | flags log；`:43-54` |
| ④-P3 | 正例绿 | 隔离 13 全绿 | 7 个依赖真实 warm-up 的用例 `awaitReady()` 全真（8 处断言） | isolation log；`:120,168,195,222,450,497,527,579` |
| ④-P4 | 正例绿 | 隔离 logcat | `visual_js warm-up finished` 的 pid 集合 = `Start proc .*:visual_js` 的 pid 集合；`warm-up failed` = 0 | isolation logcat |
| ④-N1 | 负例红 | flags `aFingerprintThatDisagreesWithTheActualPaintFailsClosed` | 坏指纹 `paintFlags=1` 被 `VisualMeasurementEnvironmentException` 拒绝，消息含 `paintFlags=1` | flags log；`:57-66` |
| ④-N2 | 负例红（常驻钉子） | `Paint().flags != 0` | API 34 上平台默认 ≠ 0（1283）；证明"必须显式置 0"不是空话 | flags log；`:53` |
| ④-L1 | logcat | painter + flags + isolation 三份 logcat | `grep -c "measure paint flags .* do not match the environment fingerprint"` = **0** | 三份 raw log |
| ④-L2 | logcat | 隔离 logcat | `visual_js warm-up failed` = 0；kill 四类之和 = spawn 数；无未分类杀因 | isolation logcat |
| ④-L3 | logcat | 三份 logcat | 9 类崩溃签名全 0；无 `FATAL EXCEPTION` | 三份 raw log |

**关闭规则**：④-P1…P4 全绿 ∧ ④-N1/N2 绿 ∧ ④-L1…L3 满足 → ④ 可关闭。
任一不满足 → ④ 保持 open；**不得**用"代码已改"或"JVM 测绿"替代设备证据
（`docs/known-defects.md:2911` 明确修复后绿轮 UNVERIFIED）。

**未执行的逻辑负例（不得写成已跑）**：把 `DEFAULT_PAINT_FLAGS`/`REPLAY_TEXT_PAINT_FLAGS`
改回平台默认或删除显式赋值，上述 P1/P2 应变红。本轮**未做**该源码改动，故只作为
"若回归会红"的逻辑推断，不构成执行证据。

---

## 4. KD-79 关闭判据（逐条）

缺陷原文：API 34 `PdfDocument.writeTo` 由 native `ExceptionDescribe()+ExceptionClear()` 吞掉
OutputStream 异常，`PAINTER_PDF_IO` 不可达，调用方把截断 PDF 当成功
（`docs/known-defects.md:2913-2917`）。台账 Reopen 条件写的是"PDF 13 例复跑且
`pdfWriteFailureIsCoded` 绿、logcat 仍出现 native 打印的模拟 IOException"
（`docs/known-defects.md:2921`）；现树已因 C.6 增至 **16** 例，应按 16 例复跑。

**现树实现（快照）**：`write(OutputStream)` 做 D1（跟踪流无首失败）∧ D2（接受字节 > 0 且
尾窗含 `%%EOF`）；`writeFile(File)` 追加 D3（长度匹配、页数回读、`renameTo` 返回值）
（`VisualDrawOpsPdfPainter.kt:76-88`、`:108-178`、`:185-234`、`:243-252`；
`PdfWriteIntegrity.kt:51-196`）。**测试已同步至 16 例**（§1.3；mid-stream + D3 正常发布 + 只读目标）。

| ID | 类型 | 用例/检查 | 通过条件 | 证据 |
|---|---|---|---|---|
| 79-P1 | 正例绿 | PDF 16 例全绿 | `OK (16 tests)`；9 个正常渲染/确定性用例 + D3 正常发布用例不因 D2/D3 复验误红 | PDF log |
| 79-P2 | 正例绿 | `pdfWriteFailureIsCoded` | 失败流 → `PAINTER_PDF_IO`（不是裸 `IOException`，也不是无异常） | PDF log；`:447-454` |
| 79-P3 | 正例绿（现树） | `pdfFileWriteRoundTripsThroughReadback` | 目标文件存在；非空；含 `%%EOF`；`PdfRenderer` 回读页尺寸 48×32；无 `.pending-` 残留 | PDF log；`:477-496` |
| 79-N1 | 负例红 | `pdfWriteFailureIsCoded` | 首写即抛 → 有码 `PAINTER_PDF_IO` | PDF log；`:447` |
| 79-N2 | 负例红（现树） | `pdfWriteFailureMidStreamKeepsTheFirstFailureText` | 首块后抛 → `PAINTER_PDF_IO`；消息含 `MID_STREAM_WRITE_FAILURE`；`acceptedBytes > 0` | PDF log；`:458-472` |
| 79-N3 | 负例红（仍缺） | 磁盘满模拟 | 目标写满 → `PAINTER_PDF_IO`；无成功发布；临时件清理 | **无用例**；需新增或记 UNVERIFIED |
| 79-N4 | 负例红（仍缺设备面） | 静默短写/尾标缺失 | 委托流不抛但少收字节或 `%%EOF` 不在尾窗 → `PAINTER_PDF_IO` | JVM `PdfWriteIntegrityTest.kt` 15 例 + `PdfWriteIntegrity.kt:160-196`；**设备面无用例** |
| 79-N5 | 负例红（仍缺） | 页数回读不符 | 回读页数 ≠ 1 → `PAINTER_PDF_IO`（若可注入） | **无用例** + `VisualDrawOpsPdfPainter.kt:159-165` |
| 79-N6 | 负例红（现树覆盖一部分） | 只读目标 / `renameTo` 失败 | 目录 0555 → `PAINTER_PDF_IO` 且消息含 `not writable`、目标未发布；`renameTo` 返回 false 的注入仍无用例 | PDF log；`:499-524` + `:166-171` |
| 79-L1 | logcat | PDF logcat | `grep -c "java.io.IOException: simulated write failure"` **> 0**：证明确实走了 native 吞异常路径，修复把它转成有码异常 | PDF raw log |
| 79-L2 | logcat | PDF logcat | 9 类签名全 0；无 `FATAL EXCEPTION` | PDF raw log |
| 79-L3 | 失败诊断 | 若 79-P2 红 | 失败行必须显示期望 `PAINTER_PDF_IO` 与实际值/无异常；不得用"已修"口头关闭 | PDF log |

**关闭规则**：

- **最低口径（台账 `:2921`）**：79-P1 + 79-P2 + 79-L1 + 79-L2 满足 → KD-79 可按台账关闭。
- **计划 C.6 口径**：另需 79-P3 + 79-N2；磁盘满（N3）不可行时如实记 UNVERIFIED。
  N4 若只有 JVM 覆盖、N5/N6 未落码，则 D3 的"静默短写/页数不符/rename 失败"仍为缺口，
  **不得**以 16 绿声称 D3 全路径完成。
- **磁盘满不可行**：若设备轨无法在不破坏设备的前提下构造 ENOSPC，如实写 UNVERIFIED；
  不得用"代码有 `pdfFileFailureReason`"或 JVM 15 例替代设备证据。

> **logcat 证据的含义**：`java.io.IOException: simulated write failure` 是 native
> `ExceptionDescribe()` 打到 `System.err` 的（研究稿 `docs/research/2026-10-29-pdf-write-safety.md:84`）。
> 它 **> 0** 证明"fail-open 路径真的被走到"；它 = 0 而测试绿，说明失败没被触发，不能关闭。
> 该行的具体条数随 chunk 数浮动，**不作固定值断言**（批次 29 参考 230 条）。

---

## 5. 设备轨串行命令与逐步判据

> 以下命令由设备轨执行；严格串行，一条完成再下一条。`$S` 用 §5.0 登记的 serial。
> `export MSYS_NO_PATHCONV=1` 全程生效。Gradle 命令由门禁脚本串行执行；设备轨不并行跑，
> 不跑 `connectedAndroidTest`（计划 §2 `:40`）。

### 5.0 步 0：设备再登记

批次 30 基线只读实测在线：`emulator-5562`（`test_device`，批次 29 主机）、
`emulator-5566`（审计他线）、`emulator-5584`（`kb_test_device`，他线，**禁碰**）
（基线 `docs/research/2026-10-29-batch-30-baseline.md:207-228`）。

```bash
A=C:/Android/Sdk/platform-tools/adb.exe
$A devices -l
# 选定 $S=emulator-5562（不使用 -5566/-5584）
$A -s "$S" shell getprop ro.build.version.sdk
$A -s "$S" shell getprop ro.product.cpu.abi
$A -s "$S" shell getconf PAGE_SIZE
$A -s "$S" shell getprop ro.build.fingerprint
$A -s "$S" shell cat /proc/uptime /proc/loadavg
```

**通过判据**：设备 `device` 态、非 offline/unauthorized；serial/API/ABI/PAGE_SIZE/fingerprint
五值写进证据头。优先 API 34 / x86_64 / 4 KB，便于与批次 29 对照；换设备则注明
**不能跨设备比较像素**（§7）。

### 5.1 步 1：构建（EXIT=0）

```bash
./gradlew --console=plain \
  :app:assembleStrictOfflineDebug \
  :app:assembleStrictOfflineDebugAndroidTest \
  :core:data:assembleDebugAndroidTest
```

**通过判据**：`EXIT=0` 且 `BUILD SUCCESSFUL`。构建后立即记录字节数与 sha256：

```bash
sha256sum app/build/outputs/apk/strictOffline/debug/app-strictOffline-debug.apk
sha256sum app/build/outputs/apk/androidTest/strictOffline/debug/app-strictOffline-debug-androidTest.apk
sha256sum core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk
```

- **不得复用现盘三包**（§1.2：2026-10-28 22:31/22:32，早于 PDF 主源 01:06）。
- 若 C.6 轨道仍在改 `VisualDrawOpsPdfPainter.kt`/新增 `PdfWriteIntegrity.kt`，构建前必须等其
  落码完成（同一落点单一拥有者，计划 §2 `:38`）；否则构建产物不含最新实现。

### 5.2 步 2：安装（`adb install -r -t`）

包名已由构建配置/合并清单实测：

- app：`com.tingyun.smartmistakebook.offline`（`app/build.gradle.kts:45-49`）
- app test：`com.tingyun.smartmistakebook.offline.test`
  （`app/build/intermediates/merged_manifest/strictOfflineDebugAndroidTest/…/AndroidManifest.xml:3`）
- core:data test：`com.tingyun.smartmistakebook.core.data.test`
  （`core/data/build/outputs/apk/androidTest/debug/output-metadata.json`）

```bash
$A -s "$S" install -r -t app/build/outputs/apk/strictOffline/debug/app-strictOffline-debug.apk
$A -s "$S" install -r -t app/build/outputs/apk/androidTest/strictOffline/debug/app-strictOffline-debug-androidTest.apk
$A -s "$S" install -r -t core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk
```

**通过判据**：三条均 `Success`；设备侧 `pm path` + `sha256sum` 与宿主逐字节一致：

```bash
for p in com.tingyun.smartmistakebook.offline \
         com.tingyun.smartmistakebook.offline.test \
         com.tingyun.smartmistakebook.core.data.test; do
  path=$($A -s "$S" shell pm path "$p" | tr -d '\r' | sed 's/^package://')
  $A -s "$S" shell sha256sum "$path"
done
```

`INSTALL_FAILED_UPDATE_INCOMPATIBLE` → 先 `adb uninstall <精确包名>` 再装，舞步记入证据。

### 5.3 步 3：dex 符号门（防旧包跑新门）

```bash
set -e
need() {
  apk="$1"; shift
  for s in "$@"; do
    unzip -p "$apk" 'classes*.dex' | grep -a -q "$s" \
      || { echo "MISSING [$s] in $apk"; return 1; }
  done
  echo "DEX OK: $apk"
}

# app 测试包：隔离 13 + flags 2 的类与新方法名
need app/build/outputs/apk/androidTest/strictOffline/debug/app-strictOffline-debug-androidTest.apk \
  VisualJsIsolationInstrumentedTest \
  VisualMeasureReplayPaintFlagsInstrumentedTest \
  theMeasuringPaintAndTheReplayTextPaintShareTheFingerprintedFlags \
  aFingerprintThatDisagreesWithTheActualPaintFailsClosed \
  anExecuteAcquiredDuringWarmUpIsRefusedBoundedlyAndNeverWatchdogKilled

# core:data 测试包：painter 24 + PDF 16 + parity 11 + 新方法/跟踪流
need core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk \
  VisualDrawOpsPainterInstrumentedTest \
  VisualDrawOpsPdfPainterInstrumentedTest \
  VisualDrawOpsParityInstrumentedTest \
  drawRenderedReplaysTheFrozenCommandSequence \
  drawRenderedFailsClosedOnMissingOrInvalidCommands \
  aStrokeOnlyPathWithAnOddDashReportsTheSameFirstGateAsTheGenerators \
  theReplayTextPaintKeepsTheExplicitlyPinnedFlagsMirroredByTheMeasureFingerprint \
  n08UniformInteriorColorCastStaysRedInsteadOfBeingDowngradedToF5 \
  pdfWriteFailureMidStreamKeepsTheFirstFailureText \
  pdfFileWriteRoundTripsThroughReadback \
  pdfReadOnlyTargetFailsClosedBeforeAnyWrite \
  PdfWriteFailureTrackingStream \
  PdfWriteTelemetry
```

**通过判据**：两个 `DEX OK`；任一 `MISSING` = 旧包，**停**，重建重装，不得继续跑。

- **本轮实测**：现盘 `data-debug-androidTest.apk` 对
  `drawRenderedReplaysTheFrozenCommandSequence`、`PdfWriteFailureTrackingStream`、
  `n08UniformInteriorColorCast…`、`pdfFileWriteRoundTripsThroughReadback` 均为 **0** 命中
  （§1.2）→ 该命令能正确拒绝旧包。
- C.6 已新增的 3 个 PDF 方法名已列入第二组；若再新增测试方法，**继续加进第二组**再跑；
  门清单随落码刷新，不冻结。
- `grep -a` 在二进制 dex 上工作；`classes*.dex` 展开为全部 dex。

### 5.4 步 4–8：五套件（每套件前清 logcat、后落 raw log）

```bash
mkdir -p build/device-track-batch30

# 4) 隔离 13
$A -s "$S" logcat -c
$A -s "$S" shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.visualjs.VisualJsIsolationInstrumentedTest \
  com.tingyun.smartmistakebook.offline.test/androidx.test.runner.AndroidJUnitRunner \
  | tee build/device-track-batch30/step01-isolation.log
$A -s "$S" logcat -v threadtime -d > build/device-track-batch30/step01-isolation-logcat.txt

# 5) flags 跨侧 2
$A -s "$S" logcat -c
$A -s "$S" shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.visualjs.VisualMeasureReplayPaintFlagsInstrumentedTest \
  com.tingyun.smartmistakebook.offline.test/androidx.test.runner.AndroidJUnitRunner \
  | tee build/device-track-batch30/step02-flags.log
$A -s "$S" logcat -v threadtime -d > build/device-track-batch30/step02-flags-logcat.txt

# 6) painter 24
$A -s "$S" logcat -c
$A -s "$S" shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.data.visual.VisualDrawOpsPainterInstrumentedTest \
  com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner \
  | tee build/device-track-batch30/step03-painter.log
$A -s "$S" logcat -v threadtime -d > build/device-track-batch30/step03-painter-logcat.txt

# 7) PDF 16（若再落码以落码计数为准）
$A -s "$S" logcat -c
$A -s "$S" shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.data.visual.VisualDrawOpsPdfPainterInstrumentedTest \
  com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner \
  | tee build/device-track-batch30/step04-pdf.log
$A -s "$S" logcat -v threadtime -d > build/device-track-batch30/step04-pdf-logcat.txt

# 8) 一致性门 11（原型门）
$A -s "$S" logcat -c
$A -s "$S" shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.data.visual.VisualDrawOpsParityInstrumentedTest \
  com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner \
  | tee build/device-track-batch30/step05-parity.log
$A -s "$S" logcat -v threadtime -d > build/device-track-batch30/step05-parity-logcat.txt
```

逐步汇总判据：

```bash
for f in step01-isolation step02-flags step03-painter step04-pdf step05-parity; do
  echo "== $f"; grep -E "OK \(|Tests run:|Failures:|Error|Skipped|Time:" \
    "build/device-track-batch30/$f.log" | tail -4
done
# 期望：OK (13 tests) / OK (2 tests) / OK (24 tests) / OK (16 tests) / OK (11 tests)
```

任一 `Failures: [1-9]`、`Error`、`Skipped`、或 summary 非目标字符串 → **停在该步**，按 §7 归因，
不得"先跑完再说"。

### 5.5 步 9：签名扫描 + 生命周期 + 专项证据

```bash
# 9 类崩溃签名（五份 logcat）
for f in step01-isolation-logcat.txt step02-flags-logcat.txt step03-painter-logcat.txt \
         step04-pdf-logcat.txt step05-parity-logcat.txt; do
  printf "%s: " "$f"
  grep -cE "FATAL EXCEPTION|ANR in |NoSuchMethodError|NoClassDefFoundError|UnsatisfiedLinkError|SIGSEGV|CANNOT LINK|failed to map segment|death was not confirmed" \
    "build/device-track-batch30/$f"
done   # 期望全 0

# 隔离 worker 生命周期
grep -c "Start proc .*:visual_js"              build/device-track-batch30/step01-isolation-logcat.txt
grep -c "visual_js warm-up finished"           build/device-track-batch30/step01-isolation-logcat.txt
grep -c "visual_js warm-up failed"             build/device-track-batch30/step01-isolation-logcat.txt   # 期望 0
grep -c "hard deadline (1500 ms) for request"  build/device-track-batch30/step01-isolation-logcat.txt
grep -c "terminate(generation"                 build/device-track-batch30/step01-isolation-logcat.txt
grep -c "the host unbound the visual_js worker" build/device-track-batch30/step01-isolation-logcat.txt
grep -c "due to start instr"                   build/device-track-batch30/step01-isolation-logcat.txt
# 前两数相等（按 pid 1:1）；后四数之和 = spawn 数；无未分类杀因
$A -s "$S" shell ps -A -o PID,NAME | grep visual_js   # 期望空

# KD-78 ④：三份 logcat 的装配级恒抛必须为 0
for f in step01-isolation-logcat.txt step02-flags-logcat.txt step03-painter-logcat.txt; do
  printf "%s: " "$f"
  grep -c "measure paint flags .* do not match the environment fingerprint" \
    "build/device-track-batch30/$f"
done   # 期望 0 0 0

# KD-79：native 吞异常的打印必须 > 0
grep -c "java.io.IOException: simulated write failure" \
  build/device-track-batch30/step04-pdf-logcat.txt   # 期望 > 0

# 一致性门：原型声明 + 11 个负控 + F5 上限
grep -c "PARITY_GATE scope=PROTOTYPE product-gate=no ci-gate=no release-gate=no" \
  build/device-track-batch30/step05-parity-logcat.txt   # 期望 1
grep -c "PARITY_CONTROL" build/device-track-batch30/step05-parity-logcat.txt          # 期望 10（N08 不打印该行）
grep "PARITY_CONTROL" build/device-track-batch30/step05-parity-logcat.txt | grep -vc "red=true"  # 期望 0
grep -c "PARITY_GATE_DESENSITIZED" build/device-track-batch30/step05-parity-logcat.txt # 期望 0
grep -c "PARITY_SYSTEMATIC_DRIFT" build/device-track-batch30/step05-parity-logcat.txt  # 期望 0
grep -c "PARITY_F5" build/device-track-batch30/step05-parity-logcat.txt                # 期望 ≤ 2
grep -c "PARITY_DIAGNOSTIC_WRITTEN" build/device-track-batch30/step05-parity-logcat.txt # 绿轮期望 0
grep "PARITY_ENV" build/device-track-batch30/step05-parity-logcat.txt | tail -1        # 含 replayTextPaintFlags=0
```

### 5.6 步 10：证据落盘（R4）

- 精编证据：`docs/research/evidence/2026-10-29-batch-30/`；单文件 ≤64 KiB / ≤400 行，
  整批 ≤16 文件 / ≤512 KiB。
- raw log 留 `build/device-track-batch30/`（gitignored），证据文件只引用路径与摘要行。
- 每份证据头至少含：serial / API / ABI / PAGE_SIZE / fingerprint / 三个 APK 的宿主+设备双侧
  sha256 / HEAD / worktree 状态 / exit-code / executed-at / operator。
- **不得**把 `INSTRUMENTATION_CODE: -1`、shell exit=0、或"另一台设备曾绿"写成通过依据。

---

## 6. 设备轨收口核对清单

- [ ] 5.0 设备已登记五值；未触碰 `-5566/-5584`。
- [ ] 5.1 构建 `EXIT=0`、`BUILD SUCCESSFUL`；三包字节/sha256 已记录；未复用 2026-10-28 22:31 三包。
- [ ] 5.2 三条 `install -r -t` 均 `Success`；设备侧 `pm path` + `sha256sum` 与宿主一致。
- [ ] 5.3 dex 符号门两包均 `DEX OK`；旧核心包（§1.2）会被该门拒绝。
- [ ] 5.4 五套件 summary 分别为 `OK (13/2/24/16/11 tests)`（PDF 以最终落码计数为准）；0 失败/错误/跳过。
- [ ] 5.5 五份 logcat 9 类签名全 0；隔离 spawn↔ready 1:1、`warm-up failed`=0、kill 四项之和=spawn、
      无存活 worker；flags 恒抛 0；PDF native IOException > 0；parity 10 个 `PARITY_CONTROL`
      全 `red=true`，且 N08 用例自身绿（F4_COLOR）。
- [ ] KD-78 ④ 按 §3 逐项核销；KD-79 按 §4 逐项核销（D3/C.6 未落码则 UNVERIFIED，不关闭）。
- [ ] 5.6 证据按 R4 落盘；raw log 留在 `build/device-track-batch30/`。
- [ ] 任一红例按 §7 完成归因并留红/绿双 log；未放宽任何断言、未过滤任何用例。

---

## 7. 红例归因规则（强制顺序，禁止直接放宽）

### 7.1 绝对禁止

- 删除/弱化断言、放大 delta/容差、缩小扫描范围、`@Ignore`/`Assume` 跳过、改 `-e class`
  过滤掉红例、把 `OK (N tests)` 的 N 改小、把失败改写成"已知问题"后仍声称通过。
- 先改测试让它变绿、再回头找原因。
- 以"批次 29 曾 20/21 绿"、"批次 28 曾 17/17 绿"或"另一台设备绿"替代当前树/当前 APK 的证据。
- 把一致性门（prototype）的红/绿写成产品门或发版结论。

### 7.2 归因五步

1. **抓原样**：完整失败行、断言消息、stack（项目帧 + 框架帧首行）、`Tests run:` 行；
   归因前不删 log。
2. **定位第一项目帧**：落在哪个文件哪一行。
3. **对照权威源**（至少两个独立来源）：`docs/current/visual-generation-contract.md`、
   编译器/校验器/painter 源码、Node 台 `tools/visual_compiler/run_tests.mjs`、
   批次设计文档、AOSP 行为。
4. **分类**（§7.3），按类别处置；只有 A/B 允许改测试，且必须有权威源支撑。
   **顺序硬约束**：先排除 A（夹具缺陷）与 D（环境/旧包），两者都不成立、且有权威源证明
   测试期望正确时，才判 C（产品缺陷）。
5. **先红后绿闭环**：红 log → 归因证据 → 改动清单（精确到文件/行/牵动面）→ 重建重装
   （新 sha256）→ 同命令复跑绿 log。未复跑 = UNVERIFIED。

### 7.3 四类归因与处置

| 类 | 判据 | 处置 |
|---|---|---|
| **A. 测试夹具/门逻辑缺陷** | 失败在测试辅助代码、夹具、或原型门自身判定逻辑；产品门未被执行到，或门的分辨力断言写错 | 改夹具/门逻辑；登记"新增消灭的失败"；产品源零改动。**不得**借机放宽阈值 |
| **B. 测试断言漂移** | 实际值与契约/编译器/Node 台/探针一致，测试期望写错 | **改成权威值**（不是改成实测值），保留 delta 0.0（非放宽）；补权威源出处 |
| **C. 产品缺陷** | 测试期望符合权威源，产品输出/行为错 | 修产品；保留/加强测试；若为已知缺口登记 KD，**不得删测试或放宽** |
| **D. 环境/设备/旧包** | 设备 offline、装错旧包、API/ABI/PAGE_SIZE 不符、字体差异导致跨设备像素比较、构建早于源改动 | 重登记设备、重建重装、核对 sha256 与 dex 符号；**跨设备像素差异不作产品缺陷**；只在单设备内比较像素 |

### 7.4 批次 30 专用红例陷阱（先按此自查，再下结论）

1. **summary 是 21/10 而不是 24/11**：装的是旧包。先跑 §5.3 dex 门与 §5.2 sha256；
   判 D，重建重装。**不得**把 21 例通过读成"少 3 例也过"。
2. **隔离 12 红且消息为 `measure paint flags 1283 do not match the environment fingerprint
   paintFlags=0`**：先确认 APK 含显式装配（sha256/dex；源码 `VisualContentMeasuring.kt:469`、
   `VisualDrawOpsPainter.kt:54`）。若新包仍 1283 → **C（产品缺陷/修复未生效）**；
   若旧包 → D。批次 29 的 12 红即此形态（`docs/known-defects.md:2902`）。
3. **`pdfWriteFailureIsCoded` 红且消息为 `expected VisualDrawOpsPaintingException`**：
   说明写失败没有被转成有码异常（fail-open 仍在）。先排除旧包（D），再判 C。
   若它绿但 logcat 无 `java.io.IOException: simulated write failure` → 失败路径没被触发，
   按"证据不足"处理，不关闭 KD-79。
4. **parity 正例红 / 负控不红**：正例红先看 `PARITY` 指标（IoU/interiorMax/frac2/bbox），
   按 A/B/C 归因；负控不红是 `PARITY_GATE_DESENSITIZED`（门失去分辨力），判 A（门逻辑），
   **不得**放宽阈值让它绿。原型门的任何结论都不得升级为产品门。
5. **PDF 的 `writeFile` 新用例红（`:477`/`:499`）**：先确认该用例与 `PdfWriteIntegrity.kt`
   在同一 APK（dex 门），再按 D1/D2/D3 逐项定位；只读目录用例在部分设备/文件系统上
   `setWritable(false,false)` 可能失败（fixture 前置断言 `cache dir must accept chmod 0555`），
   这是 **A（夹具/环境）**，不是产品缺陷；不要把它归因到屏 painter。
6. **跨设备像素差**：label advance、AA/字体、PDFium 栅格差属 D；只在单设备内比较。
   painter 的 label 带由设备 `Paint.measureText("H")` 动态给出，带宽差异是预期的，
   但"左界外零墨/右界外零墨"必须成立，不得删。
7. **构建早于源改动**：本轮 PDF 主源在 2026-10-29 01:06 仍在改（§1.3）。若 APK 时间早于
   最后一次源改动，一律 D，重建后再谈产品。

### 7.5 归因记录的最低字段

红例名 + 行号 + 失败消息 + 第一项目帧 + 四源核对结果（逐源 `path:line`）+ 类别（A/B/C/D）+
处置（改哪个文件哪几行/或不改的理由）+ 重建 sha256 + 绿轮 summary。缺任一字段 = 归因不成立。

---

## 8. 本文件边界与未验证项

- **UNVERIFIED**：五套件均**未**在本轮执行；本文件只给判据与命令。
- **UNVERIFIED**：`OK (24/13/2/16/11 tests)` 是目标字符串，不是已通过结果。
- **已核（只读）**：五套件 `@Test` 计数 24/13/2/16/11（`grep -c` 实测；PDF 于 01:08 复读为 16）；
  现盘核心测试 APK 缺新方法/跟踪流符号（`unzip -p | grep -a -c` 实测）；
  现盘三包时间 2026-10-28 22:31/22:32 与 sha256（`ls -l` + `sha256sum`）；
  包装名与 runner（`app/build.gradle.kts:29,45-49`、合并清单、`output-metadata.json`）；
  KD-78 ④/KD-79 的修复锚点（`docs/known-defects.md:2904-2911`、`:2919-2921` 与现树源码）。
- **UNVERIFIED**：`writeFile` D3 路径的设备行为；第 14/15 例已覆盖正常发布与只读目标，
  但磁盘满、静默短写、页数回读不符、`renameTo` 失败仍无设备用例（§1.3、§4）。
- **UNVERIFIED**：C.6 落码后的最终 PDF 套件计数；若再新增用例，以落码后 `grep -c "@Test"` 与
  新方法名 dex 门为准，不得沿用 16。JVM `PdfWriteIntegrityTest.kt` 15 例不在设备判据面。
- **不在本判据面**：编译器 6 例、引擎 26 例、备份 34 例、PureTest 13（JVM）、Node 台 94 断言、
  D-01/D-02/D-05 持久化用例、R8 往返、arm64/真机/16 KB 页、生产签名、真实 Provider。
- **设备依赖**：`emulator-5562` 在线（基线 `:207-228`）；若掉线，先按 §5.0 重新登记。
- **本文件自身是新增 `??` 产物**；未做 git 写操作，未改任何热区文件。
