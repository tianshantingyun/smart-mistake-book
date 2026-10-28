# 批次 29 T1 设备判据：painter 24 / 隔离 13 / Race 2（判据核验员；复核修复轮刷新）

> **只读核验**：本轮未运行 Gradle、未编译、未安装/执行任何仪器化或设备用例、未做 git 写操作。
> 只读命令：`grep -c "@Test"`、`git status --porcelain -- <落点>`、`git diff --stat 8116ad98..HEAD -- <visualjs/painter>`、
> `ls <APK 输出目录>`、`git log`。设备执行归设备轨（计划 §2 T1，`.zcode/plans/plan-2026-10-28-batch-29-pdf-replay-kd78.md:23`）。
> 判据输入：计划 §0 遗留①（`:7`）、批次 28 设备证据 E-01/E-03、现树源码实读。
>
> **刷新说明（2026-10-28 复核修复轮；原 T1 写入时点已过时）**：T1 写入时 painter 设备套件为
> **19 例旧版**；T5 更新三例并为 8 位色 alpha/回放 flags 新增两例 → **21 例**（设备轨实测
> `Tests run: 21, Failures: 1`，见 `evidence/2026-10-28-batch-29/00-index.md` §9.1）；复核修复轮
> 再增 3 例（门序组合负例 1 + `drawRendered` 2）→ 现树 **24 例**（`grep -c "@Test"` 实测）。
> 原"19 例必 3 红 / `OK (19 tests)` 不可达"的阻断结论**已随 T5 测试更新消失**；设备轨一律以
> 落码后的 `grep -c "@Test"` 实测为准，**不得沿用 19**。

---

## 1. 三套件硬判据总表

用例计数为本轮 `grep -c "@Test"` 实测。

| # | 套件 | 类（模块） | 现树用例数 | 通过字符串（硬判据） | 附带硬判据 | 批次 28 基线 |
|---|---|---|---|---|---|---|
| 1 | painter | `com.tingyun.smartmistakebook.core.data.visual.VisualDrawOpsPainterInstrumentedTest`（`core:data` androidTest） | **24** | **`OK (24 tests)`**，0 failures / 0 errors / 0 skipped | 9 类崩溃签名 0（painter logcat）；APK 身份双侧一致 | `OK (17 tests)`（修复轮前源，E-03）；T5 后 21 例设备实测 20/21（R1 flags） |
| 2 | 隔离 | `com.tingyun.smartmistakebook.visualjs.VisualJsIsolationInstrumentedTest`（`app` androidTest） | **13** | **`OK (13 tests)`**，0 failures / 0 errors / 0 skipped | 9 类签名 0；spawn↔ready 按 pid 1:1；kill 全归因、零意外杀 | `OK (13 tests)`（E-01） |
| 3 | Race | `com.tingyun.smartmistakebook.visualjs.VisualJsHandshakeRaceInstrumentedTest`（`app` androidTest） | **2** | **`OK (2 tests)`**，0 failures / 0 errors / 0 skipped | 9 类签名 0；`Start proc .*:visual_js` = 0（fake binder 面） | `OK (2 tests)`（E-01） |

- 计数证据：`grep -c "@Test"` → painter **24**、isolation **13**、race **2**（复核修复轮实跑；T1 写入时点为 19/13/2）。
- `INSTRUMENTATION_CODE: -1` **不是判据**：AndroidJUnitRunner 恒返回 RESULT_OK，失败也常为 `-1`
  （E-01 §3，`docs/research/evidence/2026-10-28-batch-28/step01-scenev1-app-suites.md:56`）。
  **shell 退出码同样不是判据**：套件内断言失败时 `am instrument` 进程仍可能 exit=0；只认 summary 行。
- 全量判据口径 = 三个 summary 字符串 + 0 失败/0 跳过 + 签名扫描 +（隔离）worker 配对/杀因归因；
  **不得用 `connectedAndroidTest`**（计划 §3，`:29`）。
- **† painter 的 `OK (24 tests)` 已包含 T5 三例改写**（生成门负向 + hand-built 回放兜底）与复核
  修复轮三例；原"当前树跑 19 例必 3 红"是 T5 测试未更新前的时点结论，**已不成立**（见 §3.1）。

---

## 2. painter 24 例逐例设备判据（T1 时点 19 例 + T5/复核修复轮 5 例）

文件：`core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualDrawOpsPainterInstrumentedTest.kt`。
夹具走生产管线 JSON → `VisualDrawOpsValidator` → `VisualDrawOpsDocument` → painter；
位图默认 `pxPerDu=2`，viewBox 48×32 → 96×64。坐标判据均为单设备逐像素断言。

> **行号提示**：下表"用例（行）"是 T1 写入时点的行号；T5/复核轮插入用例后已漂移，**以用例名
> 与判据为准**。另 5 例（T5 的 8 位色 alpha、回放 flags 显式装配；复核轮的门序组合负例与
> `drawRendered` 两例）判据见用例注释，未单列在此表。

| # | 用例（行） | 设备必须成立 | 批次 28 设备证据 |
|---|---|---|---|
| 1 | `theSameOpsPaintIdenticalPixelsWithinOneMethod`（`:134`） | 同一 painter 实例连渲两遍，`getPixels` 整阵 `Arrays.equals` 逐位相等；宽度 96 | 有（E-03 17/17，修复前源） |
| 2 | `theSameOpsPaintIdenticalPixelsInAFreshTestMethod`（`:146`） | 全新 painter + 全新位图两次渲染逐位相等 | 有（同上） |
| 3 | `aShearMatrixPlacesInkByContractColumnOrder`（`:157`） | 设备 `(5,8)=0xFFFF0000` 且 `(8,5)=0x00000000`（矩阵列序 `[a,c,e,b,d,f]` 正例） | 有 |
| 4 | `aNestedGroupClipCutsInRootSpace`（`:168`） | `(10,4)=0xFF00AA00` 且 `(30,4)=0x00000000`（clip 在根空间求交，非双重乘） | 有 |
| 5 | `aGroupLabelLandsAtTheProjectedRootSpacePosition`（`:180`） | `Paint.measureText("H")` 的 `advance.toInt()` > 0；带 `29..31+advance.toInt()` 内有墨；`0..26` 与 `36+advance.toInt()..95` 内零墨（label 不随组矩阵双乘） | 有 |
| 6 | `halfAlphaFillsFloorTheQuantizedAlpha`（`:206`） | 中心 `(24,16)=0xFFFF8080`（floor(0.5×255)=127 预乘混色），角 `(1,1)=0xFFFFFFFF` | 有 |
| 7 | `backgroundColorsTheCanvasAndNullLeavesItTransparent`（`:217`） | 背景 `#123456` 时 `(0,0)` 与 `(95,63)=0xFF123456`；`background:null` 时两点透明 `0x00000000` | 有 |
| 8 | `stateIsRestoredAfterGroupClose`（`:227`） | `(20,8)` 与 `(36,8)=0xFFFF0000`，`(48,8)=0x00000000`（restore 后平移不泄漏） | 有 |
| 9 | `aStrokePhasePaintsOutsideTheFillBand`（`:239`） | `(17,32)` 与 `(40,32)=0xFF00AA00`，`(8,32)=0x00000000`（stroke 相位存在） | 有 |
| 10 | `aShorterDashAfterALongerDashOnTheSamePainterKeepsItsOwnPattern`（`:251`） | victim strip `(8..88, 54..58)` 有墨，且与"只画 victim"逐位相等（同实例 donor→victim 无 dash 残尾） | **无（修复环新增）** |
| 11 | `aForeignSchemaVersionFailsClosed`（`:273`） | 抛 `PAINTER_SCHEMA_VERSION` | 有 |
| 12 | `aRestoreWithoutSaveFailsClosedInsideTheWalk`（`:288`） | 抛 `PAINTER_BALANCE`，消息含 `ops[0]` | 有 |
| 13 | `aStrokeOnlyPathFailsClosed`（`:304`） | 校验器放行后 painter 抛 `PAINTER_STROKE_WITHOUT_FILL`，消息含 `ops[0]` | 有 |
| 14 | `anOddDashFailsClosedEvenThoughValidationAcceptsIt`（`:313`） | `dash=[2,4,2]` 校验通过，painter 抛 `PAINTER_DASH_ODD`，消息含 `ops[0]` | 有 |
| 15 | `anEmptyDashFailsClosedEvenThoughValidationAcceptsIt`（`:323`） | `dash=[]` 抛 `PAINTER_DASH_ODD` 且含 `ops[0]`；同文档"实 dash 后跟空 dash"抛同码且含 `ops[1]`（**不得**出现无码 `ArrayIndexOutOfBoundsException`） | **无（修复环新增）** |
| 16 | `aLabelWithoutResolvableContentFailsClosed`（`:341`） | 抛 `PAINTER_UNKNOWN_LABEL`，消息含 `ops[0]` | 有 |
| 17 | `aMathLabelFailsClosedThisBatch`（`:349`） | 抛 `PAINTER_LABEL_MATH`，消息含 `ops[0]` | 有 |
| 18 | `anUnusableReplayScaleFailsClosed`（`:369`） | `pxPerDu` 为 `0f` / `NaN` / `+Inf` 三种均抛 `PAINTER_BOUNDS`（位图本身用合法比例建布） | 有（批次 28 首红后绿，E-03 §4） |
| 19 | `aLegalSceneGoesFromCompileToColoredBitmap`（`:380`） | 真引擎 compile → `Rendered.drawCommandsJson` 非空 → 校验通过；位图角点白、dot 中心非白非透明、label 带内有墨、全图有墨 | 有 |

**码值锚**：`PAINTER_SCHEMA_VERSION`/`PAINTER_BALANCE`/`PAINTER_UNKNOWN_LABEL`/`PAINTER_STROKE_WITHOUT_FILL`/
`PAINTER_DASH_ODD`/`PAINTER_LABEL_MATH`/`PAINTER_BOUNDS`/`PAINTER_COLOR`/`PAINTER_ALPHA` 定义于
`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualDrawOpsPainterPure.kt:30-67`；
T5 新增 `REPLAY_TEXT_PAINT_FLAGS` 于 `:78`。形状门现位于 `VisualDrawOpsPainter.kt:137-154`
（stroke-only `:137-143`；dash 偶数且 ≥2 `:144-154`），dash 精确装配 `:243-252`
（T5 在飞，行号可能漂移）。

**现树可达性**：本表 13/14/15 的"判据"指**回放门本身**；当前工作树的生成门会先拒其夹具，
三例在 `validatedDocument` 处即红——见 §3.1。

---

## 3. 证据时点差：17/17 不能直接充当现树证据

这是本批判据的关键前提，设备轨**不得**把批次 28 的 `OK (17 tests)` 当作现树 24 例的通过依据。

1. **17/17 的执行时点**：2026-10-28 07:42–07:47，HEAD `8116ad98`，APK v3 `a24d2c85…fd79857`
   （E-03 头部，`docs/research/evidence/2026-10-28-batch-28/step03-painter-smoke.md:11-19`）。
2. **修复轮新增两例**：`:251`（残尾 dash）与 `:323`（空 dash）为复核修复回归用例
   （批次 28 记录 `docs/research/2026-10-28-batch-28-record.md:7`、`:175`）；
   现树 `grep -c "@Test"` = 19（T1 时点；T5 后 21、复核修复轮后 24），故设备证据只有 17。
3. **主源在同日更晚时刻被修复轮改动**：painter 主源提交 `df7ee1d9` 的时间为
   `2026-10-28 19:59:36 +0800`，晚于设备执行（07:42–07:47）与提交 `d5b7ec71`（20:00:10）；
   该提交信息明写"**dash 精确分配**"，现树 `VisualDrawOpsPainter.kt:243-247` 注释自述
   "复核 2026-10-28 三失败（残尾错画 / 空数组无码崩溃 / 空 dash 复用旧 pattern）… 精确尺寸后…"，
   即该行为在设备 17/17 之后才改。该轮源当时是未跟踪工作树文件，无法从 git 复原，
   只能以"修复轮后源码自述 + 提交时间"确认现树已不同于被跑源。
4. **结论**：现树 painter 面**没有可用的旧设备证据**可用于 `OK (24 tests)`。设备轨必须重新构建
   （不得复用批次 28 的 v3 APK，也不得复用 T5 后的 21 例 APK）、重新安装、重跑全 24 例；判据写
   `OK (24 tests)`，通过后 17 例才算"在现源上复核"，其余新增例才算首次上设备。
5. **对照组（隔离/race）则不同**：`git diff --stat 8116ad98..HEAD -- app/src/main/.../visualjs
   app/src/androidTest/.../visualjs` 输出为空（本轮实跑），即两套件源码自批次 28 绿跑后零改动；
   其 13/13、2/2 对现树仍是有效的不回退基线（仍须按 §6 重跑取新证据）。

补充：批次 28 的 painter JVM 纯测 10 例（`VisualDrawOpsPainterPureTest.kt`）不在本判据面（JVM 门由脚本执行）。

### 3.1 原阻断已处置：T5 三例已改写，现树判据 = 24 例

> **复核修复轮实读**：T1 写入时点"19 例旧版、3 例夹具被生成门先拒"的阻断**已不存在**。
> `VisualDrawOpsPainterInstrumentedTest.kt` 现为 **24 例**（`grep -c "@Test"` 实测）：
> T5 把原三例拆成"生成门负向 + hand-built 回放兜底"（`aStrokeOnlyPathStillFailsClosedInTheReplayFallback`
> `anOddDashStillFailsClosedInTheReplayFallback` `anEmptyDashStillFailsClosedInTheReplayFallback`），
> 并新增 8 位色 alpha 与回放 flags 两例；复核修复轮再增门序组合负例 1 例 + `drawRendered` 2 例。

T5（KD-78 ①/②）生成门在树（现树行号）：

- `VisualDrawOpsParser.kt` `fill == null && stroke != null` → 拒绝 `PAINTER_STROKE_WITHOUT_FILL`；
- `VisualDrawOpsParser.kt` `entries.size < 2 || entries.size % 2 != 0` → 拒绝 `PAINTER_DASH_ODD`；
- `VisualDrawOpsValidator.kt` `validate` 直接委托 `VisualDrawOpsParser.parse`。

原三例的"回放兜底"部分改走 hand-built 文档，因此**不再在 `validatedDocument(...)` 处先失败**；
设备轨按 §6.4 跑现树 24 例即可。若设备仍出现旧 21 例计数，说明装了旧 APK（必须重装）。

**纪律不变**：不得把生成门拒正当成 painter 产品缺陷；不得为变绿回退/放宽生成门；判据一律以
落码后的 `grep -c "@Test"` 实测为准（当前 24）。

---

## 4. 隔离 13 例：不回退判据

文件：`app/src/androidTest/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsIsolationInstrumentedTest.kt`。
"不回退"的基线 = 批次 28 E-01 的 `OK (13 tests)` / Time 61.749（`step01-scenev1-app-suites.md:51`），
其中批次 27 的 10 红例（`:93` 逐一列名）在修复 F1 后全部转绿。

### 4.1 逐例清单（判据 = 各用例原断言全部成立，本文件不复制断言内容）

| # | 用例（行） | 关注面 |
|---|---|---|
| 1 | `workerIsIsolatedAndAProgramRoundTripsWithoutBeingKilledLater`（`:116`） | 进程隔离 + 程序往返 + 事后不被杀 |
| 2 | `aStaleGenerationIsRejectedAndAStaleTerminateIsANoOp`（`:163`） | 过期 generation 拒绝；过期 terminate 无副作用 |
| 3 | `theWatchdogKillsAHungWorkerAndAFreshGenerationHasNoBusyState`（`:191`） | 挂死 worker 被 watchdog 杀；新 generation 无 BUSY 残留 |
| 4 | `theClientRebuildsAtMostOnceAfterConfirmedAbandons`（`:239`） | 确认放弃后客户端最多重建一次 |
| 5 | `aColdStartAttachBeyondTheOldConnectBudgetStillRendersTheFirstProgram`（`:274`） | 冷启动超过旧连接预算仍能渲染首程序 |
| 6 | `theClientRebuildsOnceAfterADeathAndTheNewWorkerIsNotBusy`（`:289`） | 死亡后重建一次；新 worker 非 BUSY |
| 7 | `aHungWorkerReleasesTheCallerWithinHardPlus500ms`（`:373`） | 挂死 worker 在 hard deadline +500 ms 内释放调用方（KD-66 关闭门） |
| 8 | `aMalformedCallerRequestIsRejectedWithoutTakingTheFlightSlot`（`:445`） | 畸形请求被拒且不占在飞槽 |
| 9 | `anOverBudgetRequestEnvelopeIsRejectedByTheBoundedRead`（`:493`） | 超预算信封被有界读取拒绝 |
| 10 | `aSecondConcurrentRequestIsRejectedBusyWhileTheFirstIsInFlight`（`:523`） | 首个在飞时第二个并发请求得 BUSY |
| 11 | `anExecuteAcquiredDuringWarmUpIsRefusedBoundedlyAndNeverWatchdogKilled`（`:565`） | warm-up 期 execute 有界拒绝、不被 watchdog 误杀（KD-76 ⑤ 载体） |
| 12 | `aFirstConnectTimeoutFailsHonestlyAndTheNextExecuteRetriesWithoutLatching`（`:599`） | 首连超时如实失败、下次 execute 重试、不锁存 |
| 13 | `aReadyTimeoutFailsHonestlyWithoutLatchingAndTheNextExecuteRebinds`（`:642`） | ready 超时如实失败、不锁存、下次重新 bind |

### 4.2 套件级不回退判据（全部成立才算 PASS）

1. summary 行 **`OK (13 tests)`**，无 `Failures`、无 `Error`、无 `Skipped`；
2. 9 类崩溃签名在 isolation logcat 中 **全 0**（签名清单见 §6.4）；
3. **worker spawn↔ready 按 pid 1:1**：`Start proc .*:visual_js` 的 pid 集合与 `warm-up finished` 的 pid 集合相等
   （批次 28 参考值 17/17，`step01-scenev1-app-suites.md:75`）；任一 spawn 无 ready 或反之 = 失败；
4. **kill 全归因、零意外杀**：`hard deadline (1500 ms) for request` + `terminate(generation` +
   `the host unbound the visual_js worker` + `due to start instr` 之和 = worker 总数
   （批次 28 参考 5+4+7+1=17，`:81-87`）；出现无法归类的杀因 = 失败；
5. P-01 下限门不回退：批次 24 的六法全部含于 13 例（`00-index.md:128`），任一红即回退。

---

## 5. Race 2 例：不回退判据

文件：`app/src/androidTest/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsHandshakeRaceInstrumentedTest.kt`。

| # | 用例（行） | 关注面 |
|---|---|---|
| 1 | `markDeadWinningTheHandshakeRaceIsNeverPublishedAsConnected`（`:76`） | markDead 赢得握手竞态时不得被发布为 connected |
| 2 | `aDeathDuringTheRebuildHandshakeSpendsTheRebuildBudgetExactlyOnce`（`:126`） | 重建握手期间死亡只消耗一次重建预算 |

套件级判据：**`OK (2 tests)`**、0 failures/errors/skipped、race logcat 9 类签名 0、
`Start proc .*:visual_js` = 0（fake binder 面，E-01 `:89`）。Time 1.078 s 仅为参考值，不作门。

---

## 6. 设备轨串行命令清单与逐步判据

> 以下命令由设备轨执行；**严格串行**（一条完成再下一条），不得 `connectedAndroidTest`。
> `$S` 为 §6.0 登记的 serial；`export MSYS_NO_PATHCONV=1` 全程生效（E-00 §1）。

### 6.0 步 0：设备再登记（批次 28 的 5554/5556 已不在线）

批次 29 基线只读实测：在线为 `emulator-5562`（emu64xa）与 `emulator-5584`；
`-5554`/`-5556`（含 ps16k）不在线，且 `-5584` 属他线
（`docs/research/2026-10-28-batch-29-baseline.md:246-253`）。设备轨必须先重新登记：

```bash
A=C:/Android/Sdk/platform-tools/adb.exe
$A devices -l
# 选定 $S（不使用 -5584 他线设备）
$A -s "$S" shell getprop ro.build.version.sdk
$A -s "$S" shell getprop ro.product.cpu.abi
$A -s "$S" shell getconf PAGE_SIZE
$A -s "$S" shell getprop ro.build.fingerprint
$A -s "$S" shell cat /proc/uptime /proc/loadavg
```

**通过判据**：设备为 `device` 态、非 offline/unauthorized；serial/API/ABI/PAGE_SIZE/fingerprint 五项写进证据头。
- 首选与批次 28 同型环境（API 34 / x86_64 / 4 KB），便于与 E-01/E-03 对照；若只能换设备，
  **照常执行但注明不能跨设备比较像素值**（见 §7 归因 D）。
- 未登记设备身份即开跑 = 证据不可用。

### 6.1 步 1：构建（`assemble*`，EXIT=0）

```bash
./gradlew --console=plain \
  :app:assembleStrictOfflineDebug \
  :app:assembleStrictOfflineDebugAndroidTest \
  :core:data:assembleDebugAndroidTest
```

**通过判据**：`EXIT=0` 且 `BUILD SUCCESSFUL`（批次 28 参考 29 s，E-00 §3）。
构建后立即记录三个 APK 的字节数与 sha256：

```bash
sha256sum app/build/outputs/apk/strictOffline/debug/app-strictOffline-debug.apk
sha256sum app/build/outputs/apk/androidTest/strictOffline/debug/app-strictOffline-debug-androidTest.apk
sha256sum core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk
```

- **不得复用批次 28 的 painter APK**（v3 `a24d2c85…`）：其源早于修复轮提交 `df7ee1d9`（§3）。
- `:core:data:assembleDebugAndroidTest` 的产物是 painter 判据载体；app 两包是隔离/race 载体。

### 6.2 步 2：安装（`adb install -r -t`）

```bash
$A -s "$S" install -r -t app/build/outputs/apk/strictOffline/debug/app-strictOffline-debug.apk
$A -s "$S" install -r -t app/build/outputs/apk/androidTest/strictOffline/debug/app-strictOffline-debug-androidTest.apk
$A -s "$S" install -r -t core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk
```

**通过判据**：三条均输出 `Success`。设备侧身份双侧一致：

```bash
for p in com.tingyun.smartmistakebook.offline \
         com.tingyun.smartmistakebook.offline.test \
         com.tingyun.smartmistakebook.core.data.test; do
  path=$($A -s "$S" shell pm path "$p" | tr -d '\r' | sed 's/^package://')
  $A -s "$S" shell sha256sum "$path"
done
```

`INSTALL_FAILED_UPDATE_INCOMPATIBLE` → 先 `adb uninstall <精确包名>` 再装；卸载/重装舞步记入证据。

### 6.3 步 3：dex 符号门（防旧包跑新门）

```bash
for apk in app/build/outputs/apk/androidTest/strictOffline/debug/app-strictOffline-debug-androidTest.apk \
           core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk; do
  unzip -p "$apk" 'classes*.dex' | grep -a -E \
    'VisualJsIsolationInstrumentedTest|VisualJsHandshakeRaceInstrumentedTest|VisualDrawOpsPainterInstrumentedTest|VisualDrawOpsPainter' | head -5
done
```

**通过判据**：app 测试 APK 含前两个类名；core:data 测试 APK 含 `VisualDrawOpsPainterInstrumentedTest` 与 `VisualDrawOpsPainter`。
批次 28 以 dexdump 做过同型核对（E-00 §3）；此步是"装错包"的负向门。

### 6.4 步 4–6：三套件（每套件前清 logcat、后落 raw log）

```bash
mkdir -p build/device-track-batch29

# 4) 隔离 13
$A -s "$S" logcat -c
$A -s "$S" shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.visualjs.VisualJsIsolationInstrumentedTest \
  com.tingyun.smartmistakebook.offline.test/androidx.test.runner.AndroidJUnitRunner \
  | tee build/device-track-batch29/step01-isolation.log
$A -s "$S" logcat -v threadtime -d > build/device-track-batch29/step01-isolation-logcat.txt

# 5) Race 2
$A -s "$S" logcat -c
$A -s "$S" shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.visualjs.VisualJsHandshakeRaceInstrumentedTest \
  com.tingyun.smartmistakebook.offline.test/androidx.test.runner.AndroidJUnitRunner \
  | tee build/device-track-batch29/step01-race.log
$A -s "$S" logcat -v threadtime -d > build/device-track-batch29/step01-race-logcat.txt

# 6) painter 24（现树 `grep -c "@Test"` 实测）
$A -s "$S" logcat -c
$A -s "$S" shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.data.visual.VisualDrawOpsPainterInstrumentedTest \
  com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner \
  | tee build/device-track-batch29/step03-painter.log
$A -s "$S" logcat -v threadtime -d > build/device-track-batch29/step03-painter-logcat.txt
```

逐步汇总判据：

```bash
grep -E "OK \(|Tests run:|Time:" build/device-track-batch29/step01-isolation.log | tail -3  # OK (13 tests)
grep -E "OK \(|Tests run:|Time:" build/device-track-batch29/step01-race.log      | tail -3  # OK (2 tests)
grep -E "OK \(|Tests run:|Time:" build/device-track-batch29/step03-painter.log   | tail -3  # OK (24 tests)
```

任一出现 `Failures: [1-9]`、`Error`、`Skipped` 或 summary 非目标字符串 → **停在该步**，按 §7 归因，
不得跳到下一步"先跑完再说"。

### 6.5 步 7：签名扫描 + worker 生命周期（隔离）

```bash
for f in step01-isolation-logcat.txt step01-race-logcat.txt step03-painter-logcat.txt; do
  grep -cE "FATAL EXCEPTION|ANR in |NoSuchMethodError|NoClassDefFoundError|UnsatisfiedLinkError|SIGSEGV|CANNOT LINK|failed to map segment|death was not confirmed" \
    "build/device-track-batch29/$f"
done   # 期望 0 0 0（9 类签名，批次 28 同口径：2026-10-28-batch-28-record.md:270-273）

grep -c "Start proc .*:visual_js" build/device-track-batch29/step01-isolation-logcat.txt
grep -c "warm-up finished"        build/device-track-batch29/step01-isolation-logcat.txt
# 两数相等；批次 28 参考 17/17（E-01 §3）

grep -c "hard deadline (1500 ms) for request"     build/device-track-batch29/step01-isolation-logcat.txt
grep -c "terminate(generation"                    build/device-track-batch29/step01-isolation-logcat.txt
grep -c "the host unbound the visual_js worker"   build/device-track-batch29/step01-isolation-logcat.txt
grep -c "due to start instr"                      build/device-track-batch29/step01-isolation-logcat.txt
# 四项之和 = spawn 数；批次 28 参考 5/4/7/1（E-01 §4）
# 另：AMS 的 isolated not needed（批次 28 计 12 行）是系统回收，不计入杀因；
# 若出现无法归入上述四类的 VisualJsService 杀因，判失败并归因。
```

**收口**：三套件 summary 达标 + 三份 logcat 9 签名全 0 + 隔离 worker 配对/杀因归因；
另做"无存活 worker"检查（`ps -A -o PID,NAME | grep visual_js` 应为空，E-00 §5 同型）。

### 6.6 证据落盘（R4）

- 证据文件：`docs/research/evidence/2026-10-28-batch-29/`；单文件 ≤64 KiB / ≤400 行，
  整批 ≤16 文件 / ≤512 KiB（计划 §3，`:30`）。
- raw log 留 `build/device-track-batch29/`（gitignored），证据文件只引用其路径与摘要行。
- 每份证据头至少含：serial / API / ABI / PAGE_SIZE / fingerprint / APK sha256（宿主与设备双侧）/
  head / worktree 状态 / exit-code / executed-at / operator。

---

## 7. 红例归因规则（强制顺序，禁止直接放宽）

### 7.1 绝对禁止

- 删除或弱化断言、放大 delta/容差、缩小扫描范围、`@Ignore`/`Assume` 跳过、改 `-e class` 过滤掉红例、
  把 `OK (N tests)` 的 N 改小、把失败改写成"已知问题"后仍声称通过。
- 先改测试让它变绿、再回头找原因（顺序反了）。
- 以"批次 28 曾 17/17 绿"或"另一台设备绿"替代当前树的当前证据。

### 7.2 归因五步

1. **抓原样**：完整失败行、断言消息、stack（项目帧 + 框架帧首行）、`Tests run:` 行；不截断归因前不删 log。
2. **定位第一项目帧**：落在哪个文件哪一行。
3. **对照权威源**（至少两个独立来源）：`docs/current/visual-generation-contract.md`、
   编译器/校验器源码、Node 台 `tools/visual_compiler/run_tests.mjs`、批次设计文档、AOSP 行为。
4. **分类**（下表），按类别处置；**只有 A/B 允许改测试，且必须有权威源支撑**。
   **顺序硬约束**：先排除 A（夹具缺陷）与 B（断言漂移）——两者都不成立、且有权威源证明
   测试期望值正确时，才允许判 C（产品缺陷）。不得跳过测试层排查直接归因产品。
5. **先红后绿闭环**：红 log → 归因证据 → 改动清单（精确到文件/行/牵动面）→ 重建重装（新 sha256）→
   同命令复跑绿 log。未复跑 = UNVERIFIED，不得写成通过。

### 7.3 四类归因与处置

| 类 | 判据 | 处置 | 批次 28 实例 |
|---|---|---|---|
| **A. 测试夹具缺陷** | 失败发生在测试辅助代码（建位图/构造夹具/时序），产品门根本没被执行到；产品源与权威源一致 | 改测试夹具；登记"新增消灭的失败"；产品源零改动 | `anUnusableReplayScaleFailsClosed`：坏 scale 先乘进位图尺寸 → `createBitmap(0,…)` 在夹具抛，`PAINTER_BOUNDS` 门未执行（E-03 §4，`step03-painter-smoke.md:54-68`） |
| **B. 测试断言漂移** | 实际值与契约/编译器/Node 台/探针一致，测试期望值写错 | **改成权威值**（不是改成实测值），保留 delta 0.0（非放宽）；补权威源出处 | `dotPath.alpha expected 1.0 but was 0.5`：契约 `:98` + 编译器 + Node 台 + V8 探针四源一致，改断言 `1.0→0.5`（E-02 §4，`step02-scenev1-coredata-suites.md:55-73`） |
| **C. 产品缺陷** | 测试期望符合权威源，产品输出/行为错 | 修产品；保留/加强测试；若为已知缺口登记 KD，**不得删测试或放宽** | 批次 27 的 `$` 直插接线缺陷 → 隔离 10 红 + 引擎 9 红，修 `jsStringLiteral`（批次 27 记录 `docs/research/2026-10-27-batch-27-record.md:124`） |
| **D. 环境/设备** | 设备 offline、装错旧包、API/ABI/PAGE_SIZE 不符、字体差异导致跨设备像素比较 | 重登记设备、重建重装、核对 sha256；**跨设备像素差异不作产品缺陷**；只在单设备内比较像素 | 批次 28 设备身份与 APK 双侧 sha256 核对（E-00 §2–§3） |

### 7.4 painter 专用红例陷阱（先按此自查，再下结论）

- **不要把 17/17 当基线**：现树 painter 主源含修复轮与复核修复轮改动（`VisualDrawOpsPainter.kt`），
  全部 24 例都必须在新 APK 上重跑；第 10/15 例首跑红时，先确认 APK 是否含修复（dex/sha），再查产品。
- **空 dash 若出现无码 `ArrayIndexOutOfBoundsException`**：说明 `PAINTER_DASH_ODD` 前置门没生效
  （门在 fill 上墨前，`VisualDrawOpsPainter.kt:144-154`），属产品缺陷（C），不是测试问题。
- **残尾 dash 的 strip 不等**：检查 `bindPathPaint` 是否按 `dash.size` 精确分配（`:248-251`）；
  不得缩小 strip 范围或改用"近似相等"。
- **alpha 期望值**：`0.5` 的 floor 结果是 `127`，混色为 `0xFFFF8080`；`0xFFFF7F80`/`0xFFFF8180` 均判红，
  不得放宽容差到 ±1。
- **label 带**：带宽由设备 `Paint.measureText("H")` 动态给出（`:184-199`）；设备字体不同导致 advance 不同是预期的，
  但"左界外零墨/右界外零墨"必须成立；不得删左/右零墨断言。
- **e2e**：只断言"着色/非空白"，精确混色归专用 alpha 探针（`:430-438`）；e2e 红不得靠放宽成"非全白"。

### 7.5 归因记录的最低字段

红例名 + 行号 + 失败消息 + 第一项目帧 + 四源核对结果（逐源路径:行）+ 类别（A/B/C/D）+
处置（改哪个文件哪几行/或不改的理由）+ 重建 sha256 + 绿轮 summary。缺任一字段 = 归因不成立。

---

## 8. 本文件边界与未验证项

- **UNVERIFIED**：painter 24 例、隔离 13 例、Race 2 例均*未*在复核修复轮执行；本文件只给判据与命令。
- **UNVERIFIED**：`OK (24 tests)` 是现树目标判据；修复轮后的设备证据为 0（§3）。
- **已核（只读）**：三套件 `@Test` 计数 24/13/2（复核修复轮 `grep -c` 实测）；`8116ad98..HEAD` 对
  `app/.../visualjs` 零 diff；painter 主源提交时间晚于批次 28 设备执行；T5/复核轮已把
  三例改写为 hand-built 回放兜底，本文件引用的行号会随改动漂移，设备轨以符号名 + 落盘时点为准。
- **不在本判据面**：编译器 6 例、引擎 26 例、备份 34 例、Node 台 89 例、R-04（R8 往返，计划 §0 遗留②阻塞维持）、
  arm64/真机/16 KB 页、生产签名、真实 Provider；这些由各自判据或设备轨其它步骤承担。
- **设备依赖**：批次 28 的 `emulator-5554`/`-5556` 不在线；`-5584` 属他线不得触碰；
  设备轨须先按 §6.0 重新登记，否则本判据不可执行。

---

## 9. 收口核对清单（设备轨逐项打勾）

- [ ] §6.0 设备已登记：serial / API / ABI / PAGE_SIZE / fingerprint 五值入证据头；未触碰 `-5584`。
- [ ] §3.1 已处置：T5 三例已改写为"生成门负向 + hand-built 回放兜底"，现树重基线为 24 例；未把三红当产品缺陷。
- [ ] §6.1 构建 `EXIT=0` + `BUILD SUCCESSFUL`；三个 APK 的字节数/sha256 已记录；未复用批次 28 的 painter APK。
- [ ] §6.2 三条 `install -r -t` 均 `Success`；设备侧 `pm path` + `sha256sum` 与宿主逐字节一致。
- [ ] §6.3 dex 符号门通过（app 两测试类 + core:data painter 两类）。
- [ ] §6.4 隔离 `OK (13 tests)`、Race `OK (2 tests)`、painter `OK (24 tests)`；均 0 failures/errors/skipped。
- [ ] §6.5 三份 logcat 9 类签名全 0；隔离 spawn↔ready 1:1；kill 四项之和 = spawn 数、零意外杀；无存活 worker。
- [ ] §6.6 证据按 R4 落盘（≤64 KiB/400 行/文件；≤16 文件/512 KiB/批），raw log 在 `build/device-track-batch29/`。
- [ ] 任一红例按 §7 完成归因五步并留下红/绿双 log；**未**放宽任何断言或过滤任何用例。
- [ ] 若未全部达成：如实写 UNVERIFIED/失败项，不把目标判据写成已通过。
