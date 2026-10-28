# 批次 30 基线核对（2026-10-29，基线核对员）

> **只读核对**：本轮未运行 Gradle、未编译、未执行任何 JVM/仪器化/设备用例、未 `git fetch`、未做任何 git 写操作（本文件是唯一写盘产物）。设备面只执行只读 `adb devices -l` / `adb -s <serial> emu avd name` 与文件系统 `ls`。对照计划：`.zcode/plans/plan-2026-10-29-batch-30-device-persist-fixes.md` §0（`:5-10`，下称"计划"）。工单第 1–9 项逐项落盘。
>
> **快照时点**：2026-10-29，`git status --porcelain` 冻结 **673 行（201 M + 472 ??）**；**暂存区 0 条**（`git diff --cached --name-only | wc -l` = 0）。
>
> "干净"定义：`git status --porcelain -- <path>` 无输出（不代表内容无缺陷）。

---

## 1. HEAD / 工作树 / DB schema / 请求 schema（工单第 1 项）

| 项 | 实测 | 证据（命令/路径行号） |
|---|---|---|
| HEAD | `a806525eccd8918b2b7b6e1be3a7c02906858187`（`main`） | `git rev-parse HEAD` |
| origin/main | `a806525eccd8918b2b7b6e1be3a7c02906858187`（**与 HEAD 相同**） | `git rev-parse origin/main`；**本轮未 `git fetch`**，为本地远端跟踪引用，远端实时 hash 未核（UNVERIFIED） |
| HEAD 构成 | `a806525e` = 批次 29 台账/契约/清单同步（纯文档，整文件纳入）；父 `74d80df0` = 批次 29 证据后半；再父 `2be7d9ad` 设备证据前半、`372f1d8d` 研究 | `git log --oneline -4` 实读 |
| **与计划 §0:7 对照** | 计划写"批次 29 已推送（`a806525e`）"；**实测一致，无偏差** | 同上 |
| 工作树（冻结） | **201 M + 472 ?? = 673 行；staged = 0** | `git status --porcelain \| wc -l`=673；`awk` 计数 ` M`=201、`??`=472；`git diff --cached --name-only \| wc -l`=0 |
| DB schema | `internal const val STUDY_DATABASE_VERSION = 69`（`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabase.kt:134`）；导出目录 69 个 json，最高 `69.json`；`StudyDatabase.kt` 不在 core/database 的 8 个 M 之内（干净） | `grep -n STUDY_DATABASE_VERSION` + `ls core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/ \| wc -l`=69 + `git status --porcelain -- core/database` |
| 请求 schema | `const val TUTOR_VISUAL_CONTEXT_SCHEMA_VERSION = 18`、`const val CURRENT_SCHEMA_VERSION = TUTOR_VISUAL_CONTEXT_SCHEMA_VERSION`（`core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/ModelTasks.kt:546-547`）；`core/model` 仅 `?? core/model/gradle.lockfile`，`ModelTasks.kt` 自身干净 | `sed -n '540,552p'` + `git status --porcelain core/model` |
| 规划 vals | 计划 §0:9 "DB schema 69、请求 schema 18；批次 30 起 schema 70"——**两项实测均一致**；"批次 30 起 70"是计划前瞻，本基线无 70 产物（schemas 目录最高 69） | 同上 |

### 1.1 工作树 M 分组（实数，201）

- feature **96**（tutor **37** / library **30** / capture **15** / review **11** / profile **3**）
- core **38**（ui **11** / data **10** / database **8** / export **5** / domain **4**）
- docs **36**（research 10 / current 7 / superpowers 4 / history 3 / design 1 / 根散点 11）
- app/src **23**；tools **4**（dense_build 2 / tests 1 / kb_build 1）；仓库根 **3**（`.gitignore`、`dynamic_gui_architecture_plan.md`、`settings.gradle.kts`）；knowledge-production **1**。
- 合计 96+38+36+23+4+3+1 = **201**。
- **观察（非断言）**：该分组与批次 28 基线 §1.1（`docs/research/2026-10-28-batch-28-baseline.md:31`）逐组同值、总数同为 673（201+472）。批次 29 的四个提交把 visual/ 新文件与证据文档从脏列移除，同时又新增了他线未跟踪产物，净额相等；**逐文件同一性未核**（只核了分组计数）。

---

## 2. 热区快照对照（工单第 2 项；计划 §0:10）

**计划全文无 `HOT_ZONES` 字面量**：`grep -c "HOT_ZONES" .zcode/plans/plan-2026-10-29-batch-30-device-persist-fixes.md` = **0**（与批次 22–29 同型）。按计划 §0:10 清单逐项对照：

| 计划 §0:10 热区 | 当前实测 | 判定 |
|---|---|---|
| feature 五模块 | tutor **37**、library **30**、capture **15**、review **11**、profile **3** 全脏 | ✓ |
| core/ui | **11 M**（`build.gradle.kts` + 9 main + 1 test）+ 24 ??（Claude* 组件与 4 个测试 + lockfile） | ✓（脏 + 大量未跟踪新组件） |
| core/markdown | **整目录未跟踪**（`?? core/markdown/`，内含 `LICENSE`/`README.md`/`build`/`build.gradle.kts`/`gradle.lockfile`/`src`/`upstream-manifest.json`） | ✓ |
| core/export | **5 M**（`build.gradle.kts` + 1 main + 3 test）+ `?? core/export/gradle.lockfile` | ✓ |
| core/domain | **4 M**，全 `src/main`（§2.1 点名） | ✓ |
| settings.gradle.kts | **M**（diff +1 行） | ✓ |
| Root | `app/src/main/kotlin/com/tingyun/smartmistakebook/SmartMistakeBookRoot.kt` **M** | ✓ |
| SecondaryScreens | `app/src/main/kotlin/com/tingyun/smartmistakebook/SecondaryScreens.kt` **M** | ✓ |

计划清单外同脏（清单为子集、非穷举）：core/data **10 M**、core/database **8 M**、app/src **23 M**、docs **36 M**、tools **4 M**、knowledge-production 1、`.gitignore`、`dynamic_gui_architecture_plan.md`。

> 计划 §0:10 括注"（批次 29 T4 实测九项仍 pending）"的**九项清单本体不在计划内**；批次 29 记录 `docs/research/2026-10-28-batch-29-record.md:147` 给出九项顺序（G2→G1→AWAITING→5b-1/KD-68→AUD-08→6B-3→5b-2/阶段 6→5c，全部仍 pending、目标文件均 M）。本基线只核"M"（上表），**"九项仍 pending"的逐项语义按该记录原文引用，未独立重判**。

### 2.1 core/database 开工即脏 M 点名（逐文件；工单第 2 项）

`git status --porcelain -- core/database` 输出 **9 条 = 8 M + 1 ??**（计划 §0:38 与 §2:38 写"8 M"，**M 口径一致**）：

**M（8）：**
1. `core/database/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/database/LibraryFtsCountPathInstrumentedTest.kt`
2. `core/database/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/database/LibraryFtsCountPathScale50kInstrumentedTest.kt`
3. `core/database/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/database/LibraryFtsRankingPathInstrumentedTest.kt`
4. `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/RoomBatchImportStore.kt`
5. `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/TutorConversationDatabaseContract.kt`
6. `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/dao/BatchImportDao.kt`
7. `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/dao/TutorConversationDao.kt`
8. `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/port/CleanRedrawTaskPort.kt`

**??（1）：** `core/database/gradle.lockfile`（未跟踪锁，**不得入库**，§8）。

**关键：`StudyDatabase.kt` 与 `core/database/schemas/` 均干净**——D-01 轨（schema 70）开工时单写者的目标文件无预存脏，符合计划 §2 "schema/migration 只由 D-01 轨写"。

---

## 3. 批次 29 修复在树核对（工单第 3 项）

四个载体的 git 状态实测：`core/data/src/main/.../visual/`、`core/data/src/androidTest/.../visual/`、`app/src/main/kotlin/.../visualjs/`、`app/src/androidTest/kotlin/.../visualjs/` 的 `git status --porcelain` **全部无输出**（干净，已入库于 HEAD）；`docs/known-defects.md` 与 `docs/current/visual-generation-contract.md` 同样干净。

| 修复 | 位置（实读行号） | 在树原文 |
|---|---|---|
| **度量侧 flags 显式装配** | `app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualContentMeasuring.kt:469` | `paint.flags = VisualMeasureEnvironment.DEFAULT_PAINT_FLAGS`（`val paint = Paint()` 于 `:464`，显式置 0 后再设 `textSize`）；`:474-478` 复验 `paint.flags != environment.paintFlags` 即抛 `VisualMeasurementEnvironmentException`（实际 Paint 比对，非 environment 自述）；常量 `DEFAULT_PAINT_FLAGS: Int = 0` 于 `:192` |
| 度量指纹含 flags | 同文件 `:144`（`val paintFlags: Int = DEFAULT_PAINT_FLAGS`）、`:174`（`"\|paintFlags=$paintFlags"` 进 `fingerprint()`）、`:435`（`paintFlagsForTest()`） | 与 KD-78 ④ 的"flags 进指纹"一致 |
| **回放侧 REPLAY_TEXT_PAINT_FLAGS 装配** | `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualDrawOpsPainter.kt:54`；常量 `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualDrawOpsPainterPure.kt:87`；观测面 `VisualDrawOpsPainter.kt:273` | `private val textPaint = Paint().apply { flags = REPLAY_TEXT_PAINT_FLAGS }`；`internal const val REPLAY_TEXT_PAINT_FLAGS = 0`；`internal fun textPaintFlagsForTest(): Int = textPaint.flags` |
| **PDF 写侧跟踪流** | `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualDrawOpsPdfPainter.kt:98-109`（`val sink = PdfWriteFailureTrackingStream(out)` → `pdf.writeTo(sink)` → `catch (IOException)` 兜底 → `sink.firstFailure?.let { throw paintingFailure(PAINTER_PDF_IO, …) }`）；类体 `:117-163`（`recordOnFailure` 记录首失败后**重抛**，覆盖 `write(int)`/`write(byte[],off,len)`/`flush`/`close`）；码 `PAINTER_PDF_IO` 定义于 `VisualDrawOpsPdfPainterPure.kt:33` | 与 KD-79 的 Fix direction 逐字对应；KDoc `:92-97` 记录 API 34 native `ExceptionDescribe()+ExceptionClear()` 吞异常的机制 |
| **KD-78 台账原文** | `docs/known-defects.md:2875`（标题）→ 块域止于 `:2912`（下一 KD-79 于 `:2913`）；四个 Progress 块：`:2890`（T5 落码）、`:2900`（T6 台账同步）、`:2902`（设备轨：painter 21 例 1 红、判定"④ 落码假设被设备否证"）、`:2904`（复核修复轮：两侧显式装配 + R3 门序 + R10 两例；文末"现树 `grep -c "@Test"` = 24"） | 标题自述：`open … ② 设备像素证据已出；④ 显式装配修复已落码，待设备复跑` |
| **KD-79 台账原文** | `docs/known-defects.md:2913`（标题，`open, 2026-10-28 批次 29 复核修复轮登记；高`）→ 文件末 `:2921` | 标题：`PDF 写盘失败 fail-open：PdfDocument.writeTo 在 API 34 吞掉 OutputStream 异常，PAINTER_PDF_IO 不可达，调用方把截断 PDF 当成功`；Reopen/关闭条件 = "设备轨在 API 34 上复跑 PDF 13 例（`pdfWriteFailureIsCoded` 绿）且 logcat 仍出现 native 打印的模拟 IOException"；"验证状态：本会话未运行 Gradle/设备，修复的绿轮 **UNVERIFIED**" |

**台账锚点 vs 现树行号漂移（实质发现，归批次 30 T12 文档一致性）**：KD-78 `:2902` / `:2904` 引用的 app 侧锚点是**修复前**行号，现树因插 KDoc 整体下移：

| KD-78 台账所引 | 现树实测 | 差 |
|---|---|---|
| `REPLAY_TEXT_PAINT_FLAGS` `VisualDrawOpsPainterPure.kt:78`（`:2902`、`:2900`） | `:87` | +9 |
| `DEFAULT_PAINT_FLAGS = 0` `VisualContentMeasuring.kt:184`（`:2902`） | `:192` | +8 |
| 指纹三锚 `VisualContentMeasuring.kt:141/:171/:459`（`:2900`） | 现树 `:144`（字段）/`:174`（指纹串）/`:474`（throw） | +3/+3/+15 |
| 观测面 `VisualContentMeasuring.kt:426`、`forEnvironment :456-464`（`:2900`/`:2902`） | `:435`、`:474-478` | +9/+18 |

core 侧锚点（`VisualDrawOpsPainter.kt:162/169/195`、`Pure.kt:160`）与本文实读一致，未逐行复算 delta。

---

## 4. 设备证据计数 vs 现树计数（工单第 4 项）

**设备侧口径**：批次 29 设备轨在 **修复前源**（HEAD `3d9372e5` + 脏树）上执行，证据 `docs/research/evidence/2026-10-28-batch-29/00-index.md:60-63` 与各 step。**现树口径**：`grep -c '@Test'` 逐文件实测。

| 套件 | 设备证据 | 证据出处 | 现树 @Test | 漂移 |
|---|---|---|---|---|
| painter `VisualDrawOpsPainterInstrumentedTest.kt` | **21**（1 红：`theReplayTextPaintKeepsTheDefaultFlagsMirroredByTheMeasureFingerprint`，expected 0 / actual 1283） | `step01-painter.md:10,55`；`00-index.md:60` | **24** | **+3** |
| parity `VisualDrawOpsParityInstrumentedTest.kt`（原型门） | **10**（`OK (10 tests)`） | `step04-pdf-parity.md:10,53`；`00-index.md:63` | **11** | **+1** |
| 纯层 `VisualDrawOpsPainterPureTest.kt`（JVM，非设备） | 非设备轨执行；批次 29 T5 JVM 门报 `OK (48 tests)` = Validator 35 + Pure **13** | `docs/known-defects.md:2890` | **13** | **0**（相对批 28 基线 10 为 +3，属批次 29 内变更，非"设备→现树"漂移） |
| PDF `VisualDrawOpsPdfPainterInstrumentedTest.kt` | **13**（1 红：`pdfWriteFailureIsCoded`） | `step04-pdf-parity.md:10,53`；`00-index.md:63` | **13** | 0 |
| 跨侧 flags `VisualMeasureReplayPaintFlagsInstrumentedTest.kt` | **2**（1 红） | `step03-parser-t5-device.md:10,80` | **2** | 0 |
| 隔离 `VisualJsIsolationInstrumentedTest.kt` | **13**（12 红，warm-up 全灭） | `step02-isolation-race.md:10,55` | **13** | 0 |
| 编译器 `QuickJsSceneCompilerInstrumentedTest.kt` | **6**（`OK (6 tests)`） | `step03-parser-t5-device.md:10` | **6** | 0 |

**全部漂移归因（逐条）**：
1. **painter 21 → 24（+3）**：`VisualDrawOpsPainterInstrumentedTest.kt:428` `aStrokeOnlyPathWithAnOddDashReportsTheSameFirstGateAsTheGenerators`（复核 R3：门序负例）+ `:489` `drawRenderedReplaysTheFrozenCommandSequence` + `:511` `drawRenderedFailsClosedOnMissingOrInvalidCommands`（复核 R10）。批次 29 记录 `:253` 自报"painter 21→24（+门序负例、+2 `drawRendered`）"，**与实读一致**。
2. **parity 10 → 11（+1）**：`VisualDrawOpsParityInstrumentedTest.kt:243-264` `n08UniformInteriorColorCastStaysRedInsteadOfBeingDowngradedToF5`（复核 R8，F5 隔离带加占比条件）。记录 `:126`/`:253` 自报"N08 只在树，**未上设备**（设备跑的是 10 例旧版）"，**与实读一致**。
3. **PDF 13 → 13（0 例数）但实现已变**：写侧由裸 `writeTo` 改为跟踪流（§3），旧绿/旧红均不覆盖现树实现（KD-79 关闭条件要求复跑）。
4. **PureTest 13 = 13**：批次 29 T5 JVM 门已按 13 例跑过（`OK (48 tests)`），无"设备→现树"漂移；但该 JVM 绿轮同样早于复核修复轮的两侧显式装配改动，**现树未重跑**。
5. 附：`QuickJsVisualExecutionEngineInstrumentedTest.kt` 现树 **26** @Test（批次 23 设备轨跑的是 20 例），本批未列入复跑清单，仅登记为背景计数。

**结论**：计划 T1/T12 的 24/11/13 口径与现树一致；批次 29 记录 `:220` "设备数字与现树计数的时点差"表述准确。

---

## 5. 解析器现状（工单第 5 项；`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualDrawOpsParser.kt`，853 行，git 干净）

| 面 | 现状（实读） | 判定 |
|---|---|---|
| **`parse(text)` 的门** | `:83-105`：① 字节预算 `figureUsageUtf8Bytes(text) > MAX_TRANSACTION_JSON_BYTES` → `DRAWOPS_BUDGET_BYTES`；② `json.parseToJsonElement` 失败 → `DRAWOPS_NOT_JSON`（先接 `SerializationException`，再 `IllegalArgumentException`）；③ 根非 `JsonObject` → `DRAWOPS_TYPE`；④ 委托 `validateDocument`（结构键存在性 `requireKeys` `:815`、未知键 `checkKeys` `:801`、词表/值域/有限性/预算/stats 复算） | 门完整 |
| `parse(rendered)` | `:112-125`：先验外壳 `rendered.drawSchemaVersion != 1` → `DRAWOPS_SCHEMA_VERSION`；`drawCommandsJson == null` → `DRAWOPS_MISSING_COMMANDS`；再走文本入口 | 门完整 |
| **`serialize()` 是否跑同一生成门** | `:620-663` 是纯发射器：`StringBuilder` 追加固定键序，**不调用 `validateDocument`、不调用 `VisualDrawOpsValidator`、不复验词表/预算/stats**；唯一防线是 `num()` `:770-772` 的 `require(value.isFinite())` | **缺口 1 成立**：`serialize` 与 `parse` 不同门（计划 T8 第 1 条即此） |
| **路径命令解析（多余元素）** | `parseCommands` `:558-592`：`array[index] as? JsonArray` → 取 `[0]` 为命令码，`coordinate(i)` 用 `getOrNull(i)` 取值，`M/L` 读 1–2、`C` 读 1–6；**不检查数组长度**——`["M",1,2,3]`、`["C",…,7 个分量]`、`["Z",0]` 的多余元素被静默忽略；只要求"非空且首条为 MoveTo"（`:589-591`） | **缺口 2 成立**：对比同文件 `concat` 的 `m` 有 `matrixElement.size != 6` 硬门（`:359-361`），命令数组无对应门 |
| **重复 JSON 键** | Kotlin 侧**无任何重复键检测**：`grep` 全仓仅命中 JS 编译器的 `SCENE_PARSE_DUP_KEY`（`core/data/src/main/resources/visual/visual-compiler.js:280`，注释 `:87`）；解析器唯一近似项是 `DRAWOPS_DUP_ID`（`VisualDrawOpsParser.kt:317`，**hitRegion id 去重**，与 JSON 对象重复键无关） | **缺口 3 成立**；且**库行为未取证**——批次 29 记录 `:211` 明确登记"kotlinx 对重复键的具体行为**未验证**"，与计划 T8"按库行为取证，不得假设"一致，本轮亦**未做实验取证**（未运行 JVM/脚本） |

`serialize` 的调用面（`grep -rn "VisualDrawOpsParser.serialize"`）：**仅测试**——`core/data/src/test/.../VisualDrawOpsParserTest.kt:110/164/169/188/192`；`grep -rn "fun serialize"` 在 `core/data/src/main`、`core/domain/src/main`、`app/src/main` 只命中这一处，生产侧零调用。

---

## 6. KD-71 / KD-73 / KD-77 原文与当前状态（工单第 6 项）

| KD | 标题原文 | 状态行原文 | 当前状态判定 | 现树对照 |
|---|---|---|---|---|
| **KD-71** | `docs/known-defects.md:2727`：`## KD-71 (open, 2026-10-26 登记（批次 21 T3 复核发现 #22；低）) · QuickJsVisualExecutionEngine close/execute 竞态：closed 检查与 currentTransaction.set 之间的窗口` | 标题仍 `open`；`:2740` 批次 23 设备轨 Progress 记两条用例 `STATUS_CODE 0`，"Reopen condition 的两条设备判据…已满足；**是否关闭待主会话按批次记录回链裁定**"；`:2743` Reopen condition 不追加新条件 | **open（判据已满足、待裁定）**——即计划 T7"配可复现红测"的对象 | `QuickJsVisualExecutionEngine.kt` 属 `core/data/src/main/.../visual/`，**干净**；设备用例在 `QuickJsVisualExecutionEngineInstrumentedTest.kt`（现树 26 @Test，其中 `closeDuringAnInFlightTransactionWaitsAndRejectsIt`、`concurrentCloseAndExecuteRoundsNeverReportAnUnexpectedOutcome` 各 1） |
| **KD-73** | `:2770`：`## KD-73 (open, 2026-10-26 复核返修 #11 登记；中) · 运行时 kotlin-stdlib 强制降回 2.3.21：字节码/API 兼容证据只覆盖 quickjs AAR，不是全解析图` | 标题仍 `open`；`:2774` Progress（批次 23）"本批**没有**新增全解析图 API 兼容证据…**本条保持 open**，Reopen condition 不变"；Fix direction 二选一（完整解析图扫描 unresolved=0 / 升 2.4.10） | **open**；计划 T9 明确"需改构建文件则停项登记（热区）" | `build.gradle.kts:47-57`、`gradle/libs.versions.toml:52-60`（本文未逐行复读，按 KD 原文引用） |
| **KD-77** | `:2865`：`## KD-77 (open, 2026-10-27 登记（API 23 冒烟评估；中）) · API 23 运行时未验证：本机无 android-23 平台/系统镜像，未执行 v21 APK 冒烟` | 标题 `open`；Reopen condition = "API 23 AVD 或真机上 v21 APK 完成装机 + 冷启动 + 最小冒烟…镜像仍不可得时本条保持 open" | **open；本机镜像仍不可得（本轮实测，见下）** | `ls /c/Android/Sdk/platforms` = `android-34`、`android-37.0`；`ls /c/Android/Sdk/system-images` = `android-34`、`android-36`；`ls -d …/android-23` 两者均 `No such file or directory` |

**KD-77 本轮实测（只读 `ls`，与 KD 原文 2026-10-27 记录一致，无变化）**：无 `platforms;android-23`、无 `system-images;android-23*`。计划 T11"装 android-23 平台/镜像跑冒烟；不可得则如实登记"的"不可得"路径在本机当前状态下**成立**（除非本批获准下载镜像）。

---

## 7. AUD-08：门脚本 / 金标 / `app-split.json` 生产者（工单第 7 项）

`tools/split_golden/` **git 干净且全 tracked**（`git status --porcelain -- tools/split_golden/` 无输出；`git ls-files` 8 项）：

| 文件 | 字节 | 作用（读码） |
|---|---|---|
| `check_split_gate.py` | 22,890 | 门脚本（只判门/报数/落证据，不切产品开关） |
| `measure_split_accuracy.py` | 26,856 | 三指标度量：`COUNT_GATE = 0.9`（`:63`）、`FRAGMENT_GATE = 0.05`（`:64`）、`gatePassed = count>=0.9 and fragment<=0.05`（`:361`）；默认输入 `DEFAULT_GOLDEN`（`:58`）、`DEFAULT_APP = REPO/"build"/"split-eval"/"app-split.json"`（`:59`） |
| **`export_split_eval.py`** | 12,971 | **仓内生产者已存在**：设备 DB 快照 → `build/split-eval/app-split.json`（文件头 `:3-20` 自述"它消灭的具体失败"：复跑无入口 / 无溯源 / 坏数据无门；页名解析标 `nameSource`）。入库于 `7b2d6905`（批次 26） |
| `validate_split_golden.py` | 15,433 | 金标校验 |
| `tables/split_golden_v1.json` + `.sha256` | — | 金标 v1：**7 页 / 16 框**；sha256 文件内容 `f9e92c47b399252d2e92ecd633c2a2b76a52b0bfcdcff78868a513dedd1e4c9f  split_golden_v1.json`（`cat` 实读） |
| `GATE.md` / `README.md` | 7,863 / 4,093 | 门说明（v1，2026-10-27）：门式 ①≥0.9 且 ③≤0.05；**当前判定 = FAIL**（① 0.5714 < 0.9，③ 0.0000） |

**`build/split-eval/app-split.json` 现状（只读解析实算）**：存在，**1,482 B**，sha256 `9b49b0c474d68409eba1617b888e7cf823507341ef63f54a65d9f4d68f4c2fd6`（与 `GATE.md` 所记 `9b49b0c4…` 一致）；7 页，题框合计 **8**（`IMG…3928` 4 框、`IMG…0349` 4 框、其余 5 页 0 框）——即"7 页 8 框、5/7 页无应用输出"的原始形态，与 GATE.md"门 0.5714（4/7）"自洽。该文件被 `.gitignore:15`（`**/build/`）忽略，`git check-ignore -v` 实跑命中。

**结论修正**：批次 24/26 早期研究稿（`docs/research/2026-10-26-ui-queue-sequencing.md:409`、`2026-10-26-batch-24-record.md:70`）写的"`app-split.json` **无仓内生产者**"**已被批次 26 交付取代**——`export_split_eval.py` 在树且 tracked/clean。计划 T10"生产者半边"的**代码面已完成**；剩余是"逐页裁定落盘"与"真设备 DB 实跑"（批次 26 记录 `:97`：真设备 DB 实跑 **UNVERIFIED**）。

**本轮未运行门脚本**（`check_split_gate.py` / `measure_split_accuracy.py` / `export_split_eval.py` 均未执行）：门脚本会落证据写盘，与"唯一写盘产物"纪律冲突；上表门值来自 `GATE.md` 文档值与我对输入文件形状的只读复算，**不是本轮门跑结果**。

---

## 8. 锁窗口（工单第 8 项；批次 29 R3 分支 B 复核）

**六个解析图 build 面（`git status --porcelain -- <path>` 实测，6/6 不干净）**：

| 文件 | 状态 | `git diff --stat` |
|---|---|---|
| `settings.gradle.kts` | M | `1 +` |
| `core/ui/build.gradle.kts` | M | `2 +-`（1 增 1 删） |
| `feature/tutor/build.gradle.kts` | M | `2 ++` |
| `core/export/build.gradle.kts` | M | `3 +++` |
| `.gitignore` | M | `3 +++` |
| `core/markdown/build.gradle.kts` | **`??`（整目录未跟踪）** | — |

**锁计数（实测）**：
- **暂存区 = 0**（`git diff --cached --name-only | wc -l` = 0）→ 暂存区锁文件 **0**。
- **git 可见未跟踪锁 = 13**（`git status --porcelain | grep -c lockfile` = 13）：`app`、`core/data`、`core/database`、`core/domain`、`core/export`、`core/model`、`core/ui`、`feature/capture`、`feature/library`、`feature/profile`、`feature/review`、`feature/tutor` 的 `gradle.lockfile` + 根 `settings-gradle.lockfile`。
- **盘上锁文件 = 14**（`find . -maxdepth 4 -name "*.lockfile" …` 实跑）：上列 13 条 + **`core/markdown/gradle.lockfile`**（被折叠进 `?? core/markdown/`，不单独出条目）。
- **跟踪锁 = 0**（全部未跟踪）。

**对 R3 分支 B 最小等待清单的复核对照**（`docs/research/2026-10-28-lock-window-and-batch30-prep.md:78-90`，判定"分支 B —— 窗口不可行"于 `:67`）：

| # | 等待项 | R3 记录实测 | 本轮实测 | 变化 |
|---|---|---|---|---|
| W1 | 六文件入库（6/6 clean） | 6/6 不满足 | **6/6 不满足**（§8 表） | 未变 |
| W2 | 树收口 `porcelain \| wc -l` = 0 | `674 → 676`（201 ` M` + **475** `??`） | **673**（201 ` M` + **472** `??`） | **?? 少 3**（M 不变） |
| W3 | 暂存区无锁 | 已满足 | **已满足（0）** | 未变 |
| W4 | 14 个盘上锁不得 `git add` | 14 文件 / 13 可见条目 | **14 文件 / 13 可见条目** | 未变 |
| W5 | `:benchmark` 缺锁 | 缺 | 未见 `benchmark/gradle.lockfile`（14 个锁中无） | 未变 |
| W6 | 一次负控（KD-72 ③） | 从未执行 | 本轮亦未执行 | 未变 |
| W7 | Gradle 环境预检 | UNVERIFIED（禁跑 Gradle） | **仍未核**（本轮不跑 Gradle） | 未变 |

**判定：锁窗口仍未开**——J1（6 文件入库）红、J3（干净树）红、跟踪锁 0，与 R3 分支 B 同结论。计划 §2"锁文件不提交（KD-72 仍 open）"相容。唯一量级变化是 `??` 472（R3 记 475），"仍远非干净树"的结论不受影响。

---

## 9. 设备在线状态（工单第 9 项，只读）

`C:/Android/Sdk/platform-tools/adb.exe devices -l`（实跑，`EXIT=0`）：

```
List of devices attached
emulator-5562          device product:sdk_gphone64_x86_64 model:sdk_gphone64_x86_64 device:emu64xa transport_id:18
emulator-5566          device product:sdk_gphone64_x86_64 model:sdk_gphone64_x86_64 device:emu64xa transport_id:20
emulator-5584          device product:sdk_gphone64_x86_64 model:sdk_gphone64_x86_64 device:emu64xa transport_id:7
```

`adb -s <serial> emu avd name` 实读映射：

| serial | AVD | 归属（按既有记录） |
|---|---|---|
| emulator-5562 | `test_device` | 批次 29 设备轨主机（API 34 / x86_64 / 4 KB） |
| emulator-5566 | `audit_full_ui_forest_20261028` | 他线（审计轨） |
| emulator-5584 | `kb_test_device` | 他线（从批次 21 起未触碰） |

- **3 台全部 `device` 态**，无 offline/unauthorized。
- **ps16k（`ps16k_x86_64`，16 KB 页）本轮不在线**：`emulator.exe -list-avds` 有 `ps16k_arm64`/`ps16k_x86_64`，但 `adb devices` 无对应实例（批次 28 基线的 emulator-5556 已不存在）。批次 30 若需 16 KB 面，需另起 AVD。
- 本轮仅只读查询，未安装/未执行/未改设备态。

---

## 10. 与计划 §0 不符处（汇总）

1. **`HOT_ZONES` 字面量不存在**（计划 `grep -c` = 0）：工单第 2 项按计划 §0:10 清单对照，结论为"热区项项脏"（§2），与既往批次同型，**非计划缺陷**。
2. **计划 §0:10 "批次 29 T4 实测九项仍 pending"的九项本体不在计划内**：只能在批次 29 记录 `:147` 找到九项顺序；本基线只独立核对"M"（目标文件全 M），"仍 pending"的语义按记录原文引用（§2 注）。
3. **计划 §2:38 "core/database 开工即脏 8 M"**：M 口径**成立（8 个 M）**；但模块 dirty 实为 **9 条**——另有 `?? core/database/gradle.lockfile`（未跟踪锁，按纪律不得入库，不属"M"）。与批次 28 基线同型口径差，非新增偏差（§2.1）。
4. **计划 §0 未给 parity 设备计数**：计划 T1 写"一致性门 11"（= 现树），而批次 29 设备证据是 **10**（N08 未上设备）。计划表述本身**不与现树冲突**，但与设备证据存在 +1 时点差，已在 §4 逐条列明。
5. **计划 T12 "计数漂移回改（painter 24/parity 11/PureTest 13）"与现树完全一致**（`grep -c @Test` 实测 24/11/13），T12 的改文档前提成立（§4）。
6. **AUD-08 "无仓内生产者"的旧结论已过期**：`tools/split_golden/export_split_eval.py` 在树、tracked、clean（批次 26 交付）。计划 T10 若仍按"缺生产者"排期，应改为"生产者已在库，剩余逐页裁定落盘 + 真设备 DB 实跑"（§7）。
7. **KD-78 台账锚点行号漂移**（§3 表）：`REPLAY_TEXT_PAINT_FLAGS` 记 `:78`、实 `:87`；`DEFAULT_PAINT_FLAGS` 记 `:184`、实 `:192`；`forEnvironment` 记 `:456-464`、实 `:474-478`。计划 T12 未列此项，建议并入"文档一致性"。
8. 其余逐项（HEAD=origin/main=`a806525e`、工作树 673=201+472、DB 69、请求 schema 18、批次 29 四处修复在树、KD-71/73/77 仍 open、锁窗口未开、设备 3 台在线）**与计划 §0 一致**。

---

## 11. 本轮未验证项（如实）

- **UNVERIFIED**：origin/main 远端实时 hash（未 `git fetch`，对照为本地跟踪引用）。
- **UNVERIFIED**：**修复后设备复跑**——painter 24 / parity 11 / flags 2 / PDF 13 / 隔离 13 全部未在本轮重跑（本基线不执行设备用例）；这是批次 30 T1 的对象，也是 KD-78 ④ 与 KD-79 关闭的唯一依据。
- **UNVERIFIED**：kotlinx.serialization 对**重复 JSON 键**的实际行为（§5 缺口 3）——需实验取证，本轮未运行任何 JVM/脚本。
- **UNVERIFIED**：`serialize()` 缺门在真实调用链上的暴露面（生产零调用，仅测试），未做运行时验证。
- **UNVERIFIED**：AUD-08 门脚本本轮**未运行**；门值 0.5714 / 0.0000 来自 `GATE.md` 文档值，app-split 形状与 sha 为只读实算（§7）。
- **UNVERIFIED**：`build.gradle.kts:47-57` 的 stdlib 2.3.21 覆盖现状（KD-73）与 `check_dependency_pins.py` / `check_lockfiles.py` 退出码——本轮未跑脚本、未读这两处源码。
- **UNVERIFIED**：批次 29 T4 九项 pending 的逐项当前态（§2 注）；`core/markdown/` 内容的实现完成度。
- 本轮**未运行 Gradle/编译/任何 JVM/仪器化测试**；未改任何工作树文件（本文件为唯一写盘产物，落盘后自身成为新的 `??`）。

## 12. 复核命令清单（可重跑）

```bash
git rev-parse HEAD && git rev-parse origin/main            # 均 a806525e…
git status --porcelain | wc -l                             # 673
git status --porcelain | awk '{print $1}' | sort | uniq -c  # 201 M / 472 ??
git diff --cached --name-only | wc -l                      # 0（暂存区整空 → 暂存锁 0）
grep -c "HOT_ZONES" .zcode/plans/plan-2026-10-29-batch-30-device-persist-fixes.md   # 0
git status --porcelain -- core/database                     # 9 = 8 M + ?? gradle.lockfile
grep -n "STUDY_DATABASE_VERSION" core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabase.kt  # :134 = 69
ls core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/ | wc -l  # 69，最高 69.json
sed -n '540,552p' core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/ModelTasks.kt   # TUTOR_VISUAL_CONTEXT_SCHEMA_VERSION = 18
grep -n "paint.flags = VisualMeasureEnvironment.DEFAULT_PAINT_FLAGS" app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualContentMeasuring.kt  # :469
grep -n "flags = REPLAY_TEXT_PAINT_FLAGS" core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualDrawOpsPainter.kt  # :54
grep -n "PdfWriteFailureTrackingStream\|firstFailure" core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualDrawOpsPdfPainter.kt
grep -c '@Test' core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualDrawOpsPainterInstrumentedTest.kt   # 24
grep -c '@Test' core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualDrawOpsParityInstrumentedTest.kt    # 11
grep -c '@Test' core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualDrawOpsPainterPureTest.kt                 # 13
grep -n "fun serialize\|private fun parseCommands\|DRAWOPS_DUP_ID" core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualDrawOpsParser.kt  # :620 / :558 / :317
grep -n "SCENE_PARSE_DUP_KEY" core/data/src/main/resources/visual/visual-compiler.js   # :280
git status --porcelain -- tools/split_golden/               # 空（全 tracked/clean）
cat tools/split_golden/tables/split_golden_v1.json.sha256   # f9e92c47… split_golden_v1.json
git status --porcelain -- settings.gradle.kts core/ui/build.gradle.kts feature/tutor/build.gradle.kts core/export/build.gradle.kts .gitignore core/markdown/build.gradle.kts   # 6/6 脏
git status --porcelain | grep -c lockfile                   # 13（盘上 14，含 core/markdown/gradle.lockfile）
"C:/Android/Sdk/platform-tools/adb.exe" devices -l          # emulator-5562/5566/5584 三台 device 态
"C:/Android/Sdk/platform-tools/adb.exe" -s emulator-5562 emu avd name   # test_device
```
