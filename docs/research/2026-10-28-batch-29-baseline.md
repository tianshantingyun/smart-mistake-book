# 批次 29 基线核对（2026-10-28，基线核对员）

> **只读核对**：本轮未运行 Gradle、未编译、未执行任何 JVM/仪器化/设备用例、未 `git fetch`、未做任何 git 写操作（本文件是唯一写盘产物）。设备面只执行只读 `adb devices -l`。
>
> **对照材料**：计划 `.zcode/plans/plan-2026-10-28-batch-29-pdf-replay-kd78.md` §0（`:3-9`，下称"计划 §0"）与 §1（`:11-19`，下称"计划 §1"）。前序基线 `docs/research/2026-10-28-batch-28-baseline.md`（tracked）作为对照值来源。
>
> **快照时点**：2026-10-28（本轮实跑），`git status --porcelain` **673 行（201 M + 472 ??）**；**暂存区 0 条**。
>
> "干净"定义：`git status --porcelain -- <path>` 无输出（不代表内容无缺陷）。

---

## 1. HEAD / origin/main / 工作树 / DB schema / 请求 schema（工单第 1 项）

| 项 | 实测 | 证据（命令/路径行号） |
|---|---|---|
| HEAD | `3d9372e5f6e8001fc16c4d11b2e3d8a5459fb1d5` | `git rev-parse HEAD` |
| origin/main（本地跟踪引用） | `8116ad98f6d0a7b6ef820a4a2fd1bdaa4d842b4c` | `git rev-parse origin/main`；`git for-each-ref refs/remotes/origin/main`；**本轮未 `git fetch`**，远端实时 hash 未核（UNVERIFIED） |
| HEAD vs origin/main | origin/main 是 HEAD 的祖先；**ahead 8 / behind 0** | `git merge-base --is-ancestor origin/main HEAD` → 真；`git rev-list --left-right --count origin/main...HEAD` → `0	8`；`git rev-list --count origin/main..HEAD` → 8 |
| HEAD 构成 | `3d9372e5` = 批次 28 记录补收口，**纯文档 1 文件 +42 行**（`docs/research/2026-10-28-batch-28-record.md`）；父 `c93241d5` | `git show --stat --oneline HEAD` 实读 |
| 工作树 | **201 M + 472 ?? = 673 行；staged = 0** | `git status --porcelain \| wc -l`=673；`awk '{print $1}'` 分组 → `201 M` / `472 ??`；`git diff --cached --name-only \| wc -l`=0 |
| DB schema | `STUDY_DATABASE_VERSION = 69`（`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabase.kt:134`；`version = STUDY_DATABASE_VERSION` 于 `:233`）；导出目录 `1.json`…`69.json`，**最高 69**；`StudyDatabase.kt` 与 `core/database/schemas/` **git 干净**（core/database 的 8 M 均不含它） | `grep -n "STUDY_DATABASE_VERSION\|version =" …`；`ls core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/`；`git status --porcelain -- core/database/` |
| 请求 schema | `TUTOR_VISUAL_CONTEXT_SCHEMA_VERSION = 18`、`CURRENT_SCHEMA_VERSION = TUTOR_VISUAL_CONTEXT_SCHEMA_VERSION`（`core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/ModelTasks.kt:546-547`）；`ModelTasks.kt` **git 干净**（core/model 仅 `?? gradle.lockfile`） | `sed -n '540,552p'` + `git status --porcelain -- core/model/` |

**与计划 §0 偏差（见 §10 #1）**：计划 `:5` 写 "HEAD = `c93241d5`（…）；其上 `3d9372e5` 为记录收口尾"。实测 **HEAD 已是 `3d9372e5`**——即"记录收口尾"本身已在 HEAD，计划把 HEAD 标成它的父提交，**差一个提交**（内容无影响：代码/文档主体均在 `c93241d5`，`3d9372e5` 只加记录 42 行）。
**推送状态**：origin/main 落后 HEAD 8 个提交，与计划 §0 "推送遇 GitHub 443 故障，后台重试中" 相容；远端是否已收到 `c93241d5`/`3d9372e5` 未核（UNVERIFIED，未 fetch）。

### 1.1 工作树构成（与批次 28 基线 §1.1 逐一相同）

- M 201 = feature **96**（tutor 37 / library 30 / capture 15 / review 11 / profile 3）+ core **38**（data 10 / ui 11 / database 8 / export 5 / domain 4）+ docs **36** + app/src **23** + tools **4** + 仓库根 **3**（`.gitignore`、`dynamic_gui_architecture_plan.md`、`settings.gradle.kts`）+ knowledge-production **1**。
- ?? 472 中含 **lockfile 13**（§9）。

---

## 2. 热区快照对照（工单第 2 项；计划 §0:9）

**计划全文无 `HOT_ZONES` 字面量**（`grep -n "HOT_ZONES\|热区"` 于计划仅命中 `:9` 的"热区："行与 §1 两处正文，无该标识符/表头）。仓库内 `HOT_ZONES` 仅出现在既往基线文档，无权威定义文件；故按计划 `:9` 清单逐项对照：

| 计划 §0:9 热区 | 当前实测 | 判定 |
|---|---|---|
| feature 五模块 | tutor **37 M**、library **30 M**、capture **15 M**、review **11 M**、profile **3 M** | ✓（与批次 28 基线逐一相同） |
| core/ui | **11 M** | ✓ |
| core/markdown | **整目录未跟踪**（`?? core/markdown/build.gradle.kts` 为该目录代表项；M=0） | ✓（他线迁移中，未变） |
| core/export（KD-75） | **5 M**（`build.gradle.kts` + `MistakeExportJobRunner.kt` + 3 test）＋ `?? core/export/gradle.lockfile` | ✓（见 §8） |
| core/domain 4M | **4 M**，全 src/main（§2.1） | ✓ |
| core/data 他线 M | 模块全口径 **10 M**（src/main 2 + androidTest 5 + test 3，§2.1） | ✓（计划未给数；visual 包**不在**脏列，开工即净） |
| settings.gradle.kts | **M** | ✓ |
| Root | `app/src/main/kotlin/com/tingyun/smartmistakebook/SmartMistakeBookRoot.kt` **M** | ✓ |
| SecondaryScreens | `app/src/main/kotlin/com/tingyun/smartmistakebook/SecondaryScreens.kt` **M** | ✓ |

清单外同脏（子集非穷举，与既往同型）：core/database **8 M**、app/src **23 M**、docs **36 M**、tools **4 M**、knowledge-production 1、`.gitignore`、`dynamic_gui_architecture_plan.md`。

### 2.1 core/domain、core/data 开工即脏 M 点名（工单第 2 项）

**core/domain（4，全 src/main）：**
1. `core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/BatchImportRepository.kt`
2. `core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/MistakeDetailRepository.kt`
3. `core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/SchedulingEvaluation.kt`
4. `core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/TutorJudgedReview.kt`

**core/data（10）：**
- src/main（2）：`mistake/RoomMistakeDetailRepository.kt`、`study/RoomTutorToolRunner.kt`
- androidTest（5）：`capture/BatchImportRepositoryInstrumentedTest.kt`、`capture/SplitImportCommitReceiptInstrumentedTest.kt`、`capture/SplitImportReceiptReconciliationInstrumentedTest.kt`、`model/ModelTaskRepositoryInstrumentedTest.kt`、`study/StudySubmissionWithoutFixtureInstrumentedTest.kt`
- test（3）：`knowledge/GoldenRetrievalJvmTest.kt`、`model/OpenAiNativeToolsProtocolTest.kt`、`model/TutorToolRequestDualParseTest.kt`

（前缀均为 `core/data/src/{main,androidTest,test}/kotlin/com/tingyun/smartmistakebook/core/data/`。批次 29 新增 `VisualDrawOpsPdfPainter` 落在 `visual/` 包——该包当前**干净**，新文件将是本批首个脏项。）

---

## 3. 批次 28 落点在树核对（工单第 3 项）

**落点全部已入库于 HEAD、git 干净。** 实测 `git status --porcelain -- <visual 三目录>` 输出为空；`git ls-files …/visual/` = **12**。

### 3.1 Painter 双文件的精确分配

| 文件 | 行数 | 内容分配 |
|---|---|---|
| `core/data/src/main/kotlin/.../visual/VisualDrawOpsPainterPure.kt` | **187** | **零 android 导入**（`:3-5` 仅 `FigureGenerationOutcome`/`MathBox`/`floor`）：异常类型 `:22-25`；**10 个码常量** `PAINTER_SCHEMA_VERSION:30`、`PAINTER_BALANCE:34`、`PAINTER_UNKNOWN_LABEL:38`、`PAINTER_STROKE_WITHOUT_FILL:43`、`PAINTER_DASH_ODD:50`、`PAINTER_LABEL_MATH:54`、`PAINTER_BOUNDS:58`、`PAINTER_COLOR:62`、`PAINTER_ALPHA:66`；工厂 `paintingFailure:69-73`；矩阵列序 `toAndroidValues:85-89`（`[a,c,e,b,d,f,0,0,1]`）；窄化 `drawOpsFloat:96-102`；严格色解析 `parseDrawOpsColor:112-132`（`#RRGGBB`/`#RRGGBBAA`，`:113` 长度门）；alpha floor `drawOpsAlpha255:140-145`（`:144` `floor(alpha*255.0).toInt()`）；label DTO `VisualLabelContent:153-181`、`VisualLabelLine:184-187` |
| `core/data/src/main/kotlin/.../visual/VisualDrawOpsPainter.kt` | **311** | android 应用层：`import android.graphics.{Canvas,DashPathEffect,Matrix,Paint,Path}` `:3-7`；常驻缓冲 `pathPaint/textPaint/path/scratchMatrix/rootMatrix/mirror/mirrorSnapshots` `:50-56`；入口 `draw:69-90`（schema 门 `:75-80`、`pxPerDu` 门 `:81-83`、根矩阵 `setMatrix` `:84-87`、background `:88`）；`play:92-135`（Save `:100-105`、Concat `:107-113`、Clip 根空间舞步 `:115-124`、Restore 配平门 `:128-133`）；`playDrawPath:137-170`；`playLabel:172-209`；`bindPathPaint:212-252`；`bindTextPaint:266-272`；`rebuildPath:275-294`；`fillTypeOf:296-299`；companion `DRAW_SCHEMA_VERSION=1`/`MATRIX_VALUES=9` `:301-310` |

> 契约 `docs/current/visual-generation-contract.md:115` 自报 "311 行 / 187 行"，与 `wc -l` 实测 **零漂移**。

### 3.2 形状门行号（KD-78 ①②的现回放侧兜底）

| 门 | 精确位置 | 原文要点 |
|---|---|---|
| **stroke-only**（`PAINTER_STROKE_WITHOUT_FILL`） | `VisualDrawOpsPainter.kt:138-143`（判据 `:138` `op.fill == null && op.stroke != null`，抛码 `:140`） | "stroke-only path has no color source in DrawOps V1 (contract gap, R1 §5-①)" |
| **dash 形状门**（偶数且 ≥2，`PAINTER_DASH_ODD`） | `VisualDrawOpsPainter.kt:144-154`（判据 `:149` `it.size % 2 != 0 \|\| it.size < 2`，抛码 `:151`，消息 `:152`） | 门**提前到任何相位之前**，防"fill 相位已上墨"的半相位 |
| dash 精确装配（复核修复） | `VisualDrawOpsPainter.kt:236-250`（`FloatArray(dash.size)` `:245`，逐项 `drawOpsFloat` `:246-248`，`DashPathEffect(buffer, 0f)` `:249`） | 消灭"常驻缓冲只扩不缩"残尾串状态 |
| 8 位色 alpha 覆盖（KD-78 ③ 现状，**本批待改**） | `VisualDrawOpsPainter.kt:214-215`（`pathPaint.color = colorArgb` → `pathPaint.alpha = alpha255`）、`:268-269`（textPaint 同形） | 先 `setColor`（含 AA 字节）后 `setAlpha` ⇒ 8 位色 alpha 字节失效；计划 §1.2 裁定改为 `floor(colorA/255 × op.alpha)`（**当前代码未实现，即本批 T5 落点**） |

### 3.3 painter 仪器化套件 @Test 计数 = **19**（判据 `OK (19 tests)`）

`grep -c "@Test"` 于 `core/data/src/androidTest/kotlin/.../visual/VisualDrawOpsPainterInstrumentedTest.kt` = **19**。用例（`@Test` 行号 / 方法名）：

`:133 theSameOpsPaintIdenticalPixelsWithinOneMethod` · `:145 …InAFreshTestMethod` · `:156 aShearMatrixPlacesInkByContractColumnOrder` · `:167 aNestedGroupClipCutsInRootSpace` · `:179 aGroupLabelLandsAtTheProjectedRootSpacePosition` · `:205 halfAlphaFillsFloorTheQuantizedAlpha` · `:216 backgroundColorsTheCanvasAndNullLeavesItTransparent` · `:226 stateIsRestoredAfterGroupClose` · `:238 aStrokePhasePaintsOutsideTheFillBand` · `:250 aShorterDashAfterALongerDashOnTheSamePainterKeepsItsOwnPattern` · `:272 aForeignSchemaVersionFailsClosed` · `:287 aRestoreWithoutSaveFailsClosedInsideTheWalk` · `:303 aStrokeOnlyPathFailsClosed` · `:312 anOddDashFailsClosedEvenThoughValidationAcceptsIt` · `:322 anEmptyDashFailsClosedEvenThoughValidationAcceptsIt` · `:340 aLabelWithoutResolvableContentFailsClosed` · `:348 aMathLabelFailsClosedThisBatch` · `:368 anUnusableReplayScaleFailsClosed` · `:379 aLegalSceneGoesFromCompileToColoredBitmap`

- 修复轮新增两例在列（`:250` 残尾 dash、`:322` 空 dash）——即计划 §0 遗留① "现树 19 例" **成立**；**这 19 例是否已上设备本轮未执行（UNVERIFIED，归 T1）**。
- 旁证：JVM 纯测 `core/data/src/test/kotlin/.../visual/VisualDrawOpsPainterPureTest.kt` = **10** `@Test`（被调 10 例，与契约 `:115` "JVM 纯测 10 例" 一致）。

### 3.4 编译器套件 dot alpha 断言行

- **设备（QuickJS 编译器套件）**：`core/data/src/androidTest/kotlin/.../visual/QuickJsSceneCompilerInstrumentedTest.kt:151`
  ```
  assertEquals(0.5, dotPath.alpha, 0.0)
  ```
  上下文 `:140-143`（`dotPath = document.ops[4]`，6 条命令、末条 `Close`），更正理由块 `:144-150`（原期望 1.0 与契约 `visual-generation-contract.md:98` "group alpha 乘入"、编译器 `visual-compiler.js:860/:884/:937`、Node 台 `run_tests.mjs:219` 三处权威矛盾）；标签同位断言 `:158 assertEquals(0.5, label.alpha, 0.0)`。**该套件 @Test 计数 = 6**。
- **Node 测试台**：`tools/visual_compiler/run_tests.mjs:219` `check('legal/full label alpha composed', label.alpha === 0.5)`；同场景 dot path 源 `:186`、发射 `:193`。
- 两个载体（`QuickJsSceneCompilerInstrumentedTest.kt`、`run_tests.mjs`）`git status` **干净**。

---

## 4. KD-78 全文位置与四缺口原文（工单第 4 项）

**位置**：`docs/known-defects.md:2869`（标题）— `:2882`（Reopen condition），下一节起于 `:2883` 空行后新条目。标题原文（`:2869`）：

> ## KD-78 (open, 2026-10-28 登记（批次 28 T2 移交收口 + 复核第 1 轮；中）) · DrawOps V1 生成门/回放语义四缺口：stroke-only 无颜色源、dash 形状门未钉、#RRGGBB(AA) 与 op.alpha 优先级未定义、measure/replay flags 不在环境指纹——生成侧至今放行，painter 逐次运行时拒（③为静默错画）

**四缺口原文行号与要点：**

- **① stroke-only 无颜色源** `:2873`：validator `:340-342` 只要求 fill/stroke 二选一即放行；`VisualStrokeSpec`（`VisualDrawOps.kt:54-60`）无 color 字段；compiler 白名单拒绝 stroke 带色 ⇒ stroke-only 文档合法出两道生成门，回放侧 fail-closed（`VisualDrawOpsPainter.kt:137-143`）。
- **② dash 形状门未钉** `:2874`：validator `:424-442` 与 compiler `requireStroke`（`visual-compiler.js:472-485`）只钉逐项值域与 ≤32 条数，**不钉偶数、不钉最小条数**；painter 前置门 `VisualDrawOpsPainter.kt:144-154` 兜底；并记 "painter 侧缓冲串状态已同步修复（…`VisualDrawOpsPainter.kt:240-250`）…**设备复跑 UNVERIFIED**…**生成侧两门仍不钉形状，缺口保留**"。
- **③ 8 位色 alpha 被 op.alpha 静默覆盖** `:2875`（**唯一静默错画项**）：三侧都接受 `#RRGGBBAA`（validator `COLOR_PATTERN:45`、compiler、painter `parseDrawOpsColor` `VisualDrawOpsPainterPure.kt:112-132`），但 `bindPathPaint`/`bindTextPaint` 先 `setColor` 后 `setAlpha`（`VisualDrawOpsPainter.kt:214-215/268-269`），AOSP `Paint.setAlpha` 只替换 alpha 通道 ⇒ 8 位色 alpha 永远失效；契约对该形态与 `op.alpha` 优先级**零定义**。
- **④ measure/replay flags 不在环境指纹** `:2876`：度量指纹只含 `typefaceId|sizeSp|density|fontScale|locale`（`VisualContentMeasuring.kt:150-152`），无 Paint flags 项；replay `textPaint` 严格复制默认 flags ⇒ 度量 Paint flags 改动时 digest 不变、静默失配。

**Fix direction（`:2880`）与 Reopen condition（`:2882`）** 与计划 §1 裁定 2–5 一一对应（③ 二选一：拒绝 8 位色 或 定义乘法语义——计划取"乘法"；② 两门各加"偶且 ≥2"；① 建议加 color 或钉"fill 必填"——计划取"生成侧拒绝";④ flags 进指纹）。文档 `git status` **干净**。

## 4.1 契约 painter 段现状行（`docs/current/visual-generation-contract.md`，干净）

| 行 | 现状要点 |
|---|---|
| `:102` | "RenderScene 是校验后的不可变显示列表…`core:model` 的协议与 compiler 不依赖 Compose；`core:visual` 的 AndroidScenePainter 共用于 Compose nativeCanvas 和 PDF page.canvas。" |
| `:110` | 表头时点："…2026-10-28 批次 28 T3 更新 painter 落层注记" |
| `:115` | `core:visual` 行：不存在该模块、`AndroidScenePainter` 零命中；"**painter 落层已裁定（批次 28 T3 同步）**：…落 `core:data` `visual` 包过渡态…不新建 `core:visual` 模块"；落码已入树 "`VisualDrawOpsPainter.kt`（311 行）/`VisualDrawOpsPainterPure.kt`（187 行）/JVM 纯测 10 例/仪器化套件 19 例，设备冒烟 **17/17 绿**"；"修复轮新增的两例（残尾 dash/空 dash）设备复跑 UNVERIFIED；**PDF 半边…留批次 29**" |
| `:119` | 归位条件段：painter 落点裁定注记 + KD-78 四缺口登记句（"生成侧维持放行、painter 侧 fail-closed（③ 8 位色为静默错画，V1.1 按拒绝或乘法语义收口）"） |
| `:190` | 兼容判断分层（重放 vs 重执行） |
| `:192` | "导出冻结 `{programHash,state,timeMs,seed,layoutEnvironment,RenderScene}`；PDF/SVG 不重新执行 JS…PDF 只按目标栏宽计算 ptPerDu，复用 AndroidScenePainter" |

> 计划 §1.2/§1.6 的裁定（alpha 乘法公式、PDF 落 `core:data`）**尚未写入契约**——`:119` 仍写"按拒绝或乘法语义收口"的未定式，`:192` 仍指向 `AndroidScenePainter`（不在树）。本批 T6 须同步。

---

## 5. 准入清单 v3 的 S-04 / S-05 / S-06 现状列原文（工单第 5 项）

文件：`docs/research/2026-10-26-batch-24-4e2-admission-checklist.md`（v3 + 补记 2；tracked，`git status` 干净）。原文摘录（现状列）：

- **S-04（`:144`）** —— "**部分落（v3 补记 2，批次 28 T3 按 R2 排序 §1.1 A2 刷新）**：DTO 语义已立——`VisualDrawOps.kt` sealed 六原语（Save/Concat/Clip/DrawPath/Label/Restore）+ `VisualDrawOpsValidator.kt`（词表/预算/有限性/配平/stats 复算，批次 27 落码）+ `Rendered` 增 `drawCommandsJson`/`drawSchemaVersion`（`core/domain/.../VisualExecutionEngine.kt:245-246`，本轮实测锚）；**序列化往返（drawCommandsJson → 结构化 → 再序列化一致）无测试**；无函数断言由 sealed 词表结构性保证、无专项用例行；painter 屏半边批次 28 T2 在飞（设计 `2026-10-28-painter-design.md`），本注记时点 painter 类名 grep 0"。缺口列："序列化往返测试、无函数专项断言、painter（DTO 已落）"。
- **S-05（`:145`）** —— "**部分落 + 一处口径差待裁（v3 补记 2…旧"未落：无 DTO"已过时）**：编译器已拒非空 `controls`（`visual-compiler.js:732` `SCENE_FIELD_CONTROLS`"must be an empty array in V1"）与整体拒 `form`（`:738` `SCENE_FIELD_UNAVAILABLE`…）；**hitRegions 按编译器现状允许非空**（`:950-975` 只做数组+预算校验、非空不拒，并经 `:1077-1088` 序列化进 DrawOps 输出）——与本表 §1.1 "`controls[]`、`hitRegions[]`、`form?` 必须为空/不存在"存在口径差（hit-region 命中测试本属交互面），S-05 第三条负向（hitRegions）待主会话对齐口径后落"。缺口列："hitRegions 口径裁定 + 对应负向"。
- **S-06（`:146`）** —— "**未落**：模块与类零命中（v3 补记 2 维持"未落"，补落点注记：painter 屏半边落点已裁定 `core:data` `visual` 包过渡态——`VisualDrawOpsPainter.kt` `internal`、纯计算/应用层分离，设计 `2026-10-28-painter-design.md` §3.1；不新建 `core:visual` 模块（`settings.gradle.kts` 热区），模块归位条件不变；落码批次 28 T2 在飞、本注记时点类名 grep 0）"。缺口列："模块、真实布局、painter"。

> **状态差（实质输入）**：三行现状均停在"批次 28 T2 在飞 / painter 类名 grep 0"的时点（`:494` 附录 E 范围句自述"批次 28 T1（设备复跑）与 T2（painter 落码）在飞未出数，本补记不预填设备/落码判定"）。当前树实测：painter 类**已在树**（`VisualDrawOpsPainter.kt:44`、`VisualDrawOpsPainterPure.kt`，设备 17/17 已出数），故 S-04/S-06 的"在飞/grep 0"表述**已过时**；S-05 的"hitRegions 待裁"与计划 §1.1 裁定一致，可直接落负向。

---

## 6. DrawOps V1 词表与校验器放行条件（工单第 6 项）

### 6.1 `VisualDrawOps.kt` 六原语（`core/data/src/main/kotlin/.../visual/VisualDrawOps.kt`，179 行）

| 原语 | 行 | 形态 |
|---|---|---|
| `Save` | `:98` | `data object`（压栈） |
| `Concat` | `:104` | `visualDrawOp.Concat(val matrix: VisualMatrix)` |
| `Clip` | `:110` | `Clip(path: List<VisualPathCommand>, rule: VisualFillRule)`（组合后坐标） |
| `DrawPath` | `:116-121` | `DrawPath(path, fill: String?, stroke: VisualStrokeSpec?, alpha: Double)`（局部坐标；fill 先画/stroke 后画） |
| `Label` | `:127-136` | `Label(contentId, x, y, rotationDeg, hAlign, vAlign, color: String, alpha: Double)` |
| `Restore` | `:139` | `data object`（出栈，严格配平） |

路径命令 `VisualPathCommand` `:21-34`（MoveTo/LineTo/CubicTo/Close，仅 M/L/C/Z）；`VisualStrokeSpec` `:55-61` = `width/cap/join/miterLimit/dash`，**无 color 字段**（KD-78 ① 的结构根因）。

### 6.2 校验器放行条件（`VisualDrawOpsValidator.kt`）

| 条件 | 行 | 原文/语义 |
|---|---|---|
| **8 位色放行** | `:45` | `private val COLOR_PATTERN = Regex("^#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?$")` — 末两位可选 ⇒ `#RRGGBBAA` **合法**；用于 fill `:325`、background `:104`、label color `:383` |
| **stroke-only 放行** | `:341` | `if (fill == null && stroke == null) return … DRAWOPS_NO_PAINT "$at: path needs a fill or a stroke"` — 只要有一个非空即过 ⇒ `fill=null, stroke!=null` **放行** |
| **dash 条数：仅上限** | `:427-428` | `if (field.size > MAX_DASH_ENTRIES)` → `DRAWOPS_DASH "dash has over 32 entries"`；`MAX_DASH_ENTRIES = 32`（`:37`）；逐项只验 `(0, 100000]`。**无偶数门、无最小条数门** ⇒ 奇数 / 空数组 `[]` **放行** |
| fill 类型门 | `:330` | `"fill must be a color or null"` |

> 结论：三道放行条件与 KD-78 ①②③ 的症状描述**逐字吻合**（生成侧至今放行）；本轮计划 §1.3/§1.4 的生成门（`PAINTER_DASH_ODD` 偶数≥2、`PAINTER_STROKE_WITHOUT_FILL`）需在 validator + compiler 两处新增。

---

## 7. `Rendered.drawCommandsJson` 字段与产处（工单第 7 项，读码引用）

- **字段定义**：`core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/VisualExecutionEngine.kt:241-245`
  ```kotlin
  data class Rendered(
      val sceneJson: String,
      val sceneSchemaVersion: Int,
      val contentIds: Set<String>,
      val drawCommandsJson: String? = null,
      val drawSchemaVersion: Int = 1,
  )
  ```
  KDoc `:236-239`："DrawOps V1 文本…宿主校验器已按与编译器同一套不变量复验后才会出现在这里…可空并默认 null：fake/旧形态构造点不承担编译语义；真实引擎的 Rendered 必携带非空值"；`init` 预算门 `:256-261`（`figureUsageUtf8Bytes(drawCommandsJson) <= MAX_TRANSACTION_JSON_BYTES`）。
- **产处（真实引擎）**：`core/data/src/main/kotlin/.../visual/QuickJsVisualExecutionEngine.kt`
  - `:399` `val drawCommandsJson = runCompilePhase(transaction, jsDispatcher, checkedSceneJson, metricsJsonText, request)`
  - `:406` `return commitRendered(checkedSceneJson, contentIds, drawCommandsJson, request.viewport)`
  - `:776-779` `commitRendered(...)`：`VisualDrawOpsValidator.validate(drawCommandsJson, contentIds, viewport)`（第二道门）
  - `:791` `VisualExecutionResult.Rendered(sceneJson = …, drawCommandsJson = drawCommandsJson, drawSchemaVersion = DRAW_SCHEMA_VERSION)`
- **跨进程 wire（AIDL 信封）**：`app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsProtocol.kt:97` `private const val KEY_DRAW_COMMANDS = "drawCommandsJson"`；写 `:217` `.put(KEY_DRAW_COMMANDS, result.drawCommandsJson ?: JSONObject.NULL)`；读 `:284` `drawCommandsJson = if (root.isNull(KEY_DRAW_COMMANDS)) {…}`。
- **消费现状**：`VisualDrawOpsValidator.validate(text, …)` 是目前唯一的"文本 → 类型化文档"入口（S-04 的 T2 任务即补一条可复用的 `Rendered.drawCommandsJson → VisualDrawOpsDocument` 解析 + 往返测试；当前**无往返测试**，与 S-04 现状一致）。

---

## 8. PDF 面现状（工单第 8 项）

**仓内已有 `PdfDocument` / `PdfRenderer` 使用点（主树，不含 `.worktrees/`）——15 个文件命中；但无 DrawOps 回放用 painter。**

| 位置 | 用法 |
|---|---|
| `core/export/src/main/kotlin/.../export/DeterministicMistakePdfRenderer.kt:10`（import）、`:62`（`PdfDocument()`）、`:65`（`PageInfo.Builder`） | **已有的 PDF 绘制器**：把行/公式/图形/图片/作答区映射成 `Paint` 画到 `PdfDocument` 的 Canvas（KDoc `:23`）——与计划 §1.6 的 `VisualDrawOpsPdfPainter` 同型但**非 DrawOps 驱动** |
| `core/export/src/main/kotlin/.../export/MistakePdfExporter.kt:4`（`PdfRenderer`）、`:128`（调 `DeterministicMistakePdfRenderer.render`）、`:167`（`PdfRenderer(descriptor)`） | 导出后回读校验 |
| `core/export/src/main/kotlin/.../export/MistakePdfDelivery.kt:9-10`、`:33`、`:54`、`:72`、`:226-227`（`PdfRenderer → PdfDocument`） | 预览/重渲染 |
| `core/data/src/main/kotlin/.../data/capture/PdfBatchImportPageRenderer.kt:6`（import `PdfRenderer`）、`:55`、`:69` | 导入侧 PDF 页面栅格化 ⇒ **`core:data` 模块已依赖 `android.graphics.pdf`** |
| 测试面 | `core/data/src/androidTest/.../capture/BatchImportRepositoryInstrumentedTest.kt:10/:996`、`BatchImportSourceRangeInstrumentedTest.kt:7/:241`、`core/export/src/androidTest/.../MistakePdfDeliveryRegressionInstrumentedTest.kt:5/:163`、`app/src/androidTest/.../SharedCaptureTestFiles.kt:7` |

- **无 `VisualDrawOpsPdfPainter`**：`grep -rn "VisualDrawOpsPdfPainter" --include=*.kt core/ app/ feature/`（排除 `/build/`）**0 文件命中**；全仓 `*.kt/*.js/*.md` 命中的 4 处均为本基线文件自身的引用（`docs/research/2026-10-28-batch-29-baseline.md:66/212/218/220`）。
- **`core/export` 脏**：**5 M**（`build.gradle.kts`、`src/main/.../MistakeExportJobRunner.kt`、`src/test/.../{MistakePdfEligibilityTest,MistakePdfPagePlannerTest,MistakePdfPlannerGoldenTest}.kt`）+ `?? core/export/gradle.lockfile` ⇒ **符合 KD-75 热区、契合计划 §1.6"不进 core/export"**。
- **落位可用性证据**：`core:data` 已有 `android.graphics.pdf` 使用（`PdfBatchImportPageRenderer.kt`），故计划 §1.6 把 `VisualDrawOpsPdfPainter` 放 `core:data` visual 包**无需新增模块依赖**（本轮未跑 Gradle，依赖闭包按现有使用点推断，非构建验证）。

---

## 9. 锁窗口再判定（工单第 9 项；计划 §2"锁文件生成（窗口判定驱动）"不做）

**6 个解析图 build 面 git 状态（`git status --porcelain -- <path>` 实测）：无一干净。**

| 文件 | 状态 |
|---|---|
| `settings.gradle.kts` | **M** |
| `core/ui/build.gradle.kts` | **M** |
| `feature/tutor/build.gradle.kts` | **M** |
| `core/export/build.gradle.kts` | **M** |
| `.gitignore` | **M** |
| `core/markdown/build.gradle.kts` | **??**（整目录未跟踪） |

- **暂存区整空**：`git diff --cached --name-only | wc -l` = **0** ⇒ **暂存区锁文件计数 = 0**。
- **未跟踪锁文件 = 13**：`app`、`core/data`、`core/database`、`core/domain`、`core/export`、`core/model`、`core/ui`、`feature/capture`、`feature/library`、`feature/profile`、`feature/review`、`feature/tutor` 的 `gradle.lockfile` + 根 `settings-gradle.lockfile`（另 `core/markdown/gradle.lockfile` 随整目录 ??，不单列）。与批次 28 基线"lockfile 13"一致，**无新增**。

**判定结论：锁窗口未开。** 干净树前置不满足（6/6 build 面不干净）、锁文件全部未跟踪、暂存区零锁文件；与计划 §2"锁文件生成（窗口判定驱动）"不做相容，批次 29 无需为此改树。

## 10. 设备在线状态（工单第 10 项，只读）

`C:\Android\Sdk\platform-tools\adb.exe devices -l` 实跑（本轮唯一设备命令）：

```
List of devices attached
emulator-5562          device product:sdk_gphone64_x86_64 model:sdk_gphone64_x86_64 device:emu64xa transport_id:17
emulator-5584          device product:sdk_gphone64_x86_64 model:sdk_gphone64_x86_64 device:emu64xa transport_id:7
```

- **2 台在线（均 `device` 态，无 offline/unauthorized）**：`emulator-5562`（emu64xa，新出现）、`emulator-5584`（emu64xa；批次 28 基线判定"属他线"）。
- **与批次 28 基线差异**：彼时 3 台（`-5554`/`-5556`/`-5584`）；本轮 **`-5554` 与 `-5556` 不在线**，`-5562` 新增。**ps16k（`sdk_gphone16k_x86_64` / 16 KB 页，批次 28 的 `-5556`）当前不在线**——如需 16 KB 页设备轨须先确认该 AVD 启动路径（本轮未启动，UNVERIFIED）。
- 本轮未安装/未执行/未改设备态。

---

## 11. 与计划 §0/§1 不符处与判定汇总

1. **HEAD 标注差一个提交**：计划 §0:5 写 "HEAD = `c93241d5`…其上 `3d9372e5` 为记录收口尾"；实测 **HEAD = `3d9372e5`**（收口尾自身已在 HEAD，`git show --stat` 证其为纯文档 +42 行）。内容层面无影响，但"HEAD"字面不符，且按计划书写会漏掉 `3d9372e5` 已在树的事实。
2. **推送状态**：origin/main = `8116ad98`，落后 HEAD **8** 提交 ⇒ 与 §0 "推送重试中"相容；远端实时是否已收到 `c93241d5` **UNVERIFIED**（未 fetch）。
3. **HOT_ZONES 无字面定义**：计划与仓库均无 `HOT_ZONES` 标识（仅既往基线引用）；本轮按计划 §0:9 逐项对照，**全部热区仍脏，与批次 28 基线逐一相同**（feature 96 / core 38 / docs 36 / app 23 / tools 4 / root 3 / kp 1 = 201）。
4. **批次 28 落点**：`VisualDrawOpsPainter.kt`(311)/`VisualDrawOpsPainterPure.kt`(187) 已入库且干净；仪器化 **19 @Test**（含修复轮 2 例）——计划 §0 遗留① 的"现树 19 例"成立，**判据 `OK (19 tests)` 未执行（UNVERIFIED，归 T1）**。
5. **KD-78 四缺口登记完整、契约 painter 段已同步到批次 28 时点**；但 §1 裁定 2（alpha 乘法公式）与裁定 6（PDF 落 `core:data`）**尚未进契约**（`:119` 仍是未定式、`:192` 仍指向不存在的 `AndroidScenePainter`）——T6 需改。
6. **S-04/S-05/S-06 现状列停在"批次 28 T2 在飞"时点**（painter 类名 grep 0），与当前树（painter 已落、17/17 已出数）不符；S-04 的"无序列化往返测试"仍成立，S-05 的 hitRegions 口径差与计划 §1.1 裁定一致。
7. **PDF 面**：主树已有 `PdfDocument`/`PdfRenderer` 使用点（尤其 `core/export/DeterministicMistakePdfRenderer.kt` 与 `core/data/capture/PdfBatchImportPageRenderer.kt`），但**无 DrawOps 回放 painter**；`core/export` 脏 5 M + ?? lockfile ⇒ 计划 §1.6"不进 core/export"既有热区依据，`core:data` 又已具 PDF API 依赖，落位可行（依赖闭包未构建验证）。
8. **锁窗口未开**（6/6 build 面脏、暂存区 0、未跟踪锁文件 13）——与计划 §2"不做"一致。
9. **设备**：当前 2 台（`-5562`、`-5584`）；批次 28 的 `-5554`/`-5556`（含 ps16k）不在线，设备轨需重新登记主机映射。

## 12. 本轮未验证项（如实）

- **UNVERIFIED**：origin/main 远端实时 hash（未 `git fetch`）。
- **UNVERIFIED**：painter 19 例、隔离 13 例、编译器 6 例、引擎 26 例的设备复跑（只读核对，不执行；即计划 T1 对象）。
- **UNVERIFIED**：R-04（R8 隔离往返）阻塞状态（计划 §0 遗留②；本轮未触及该轨）。
- **UNVERIFIED**：`VisualDrawOpsPdfPainter` 落 `core:data` 的依赖闭包（未跑 Gradle，仅按现有 `android.graphics.pdf` 使用点推断）。
- 本轮未运行 Gradle/编译/任何测试；未改任何工作树文件（本文件为唯一写盘产物）。

## 13. 复核命令清单（可重跑）

```bash
git rev-parse HEAD && git rev-parse origin/main
git rev-list --left-right --count origin/main...HEAD        # 0	8
git show --stat --oneline HEAD                              # 3d9372e5 纯文档 +42
git status --porcelain | wc -l                              # 673
git status --porcelain | awk '{print $1}' | sort | uniq -c  # 201 M / 472 ??
git diff --cached --name-only | wc -l                       # 0
grep -n "STUDY_DATABASE_VERSION" core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabase.kt  # :134 = 69
sed -n '540,552p' core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/ModelTasks.kt                         # 18
wc -l core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualDrawOpsPainter{,Pure}.kt                 # 311 / 187
grep -c "@Test" core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualDrawOpsPainterInstrumentedTest.kt  # 19
grep -n "dotPath.alpha\|label.alpha" core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/visual/QuickJsSceneCompilerInstrumentedTest.kt  # :151 / :158
grep -n "PAINTER_STROKE_WITHOUT_FILL\|PAINTER_DASH_ODD" core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualDrawOpsPainter.kt  # :140 / :151（门 :138-143 / :144-154）
grep -n "COLOR_PATTERN\|MAX_DASH_ENTRIES\|needs a fill or a stroke" core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualDrawOpsValidator.kt  # :45 / :37,:427 / :341
grep -n "drawCommandsJson" core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/VisualExecutionEngine.kt  # :245/:256
grep -n "KD-78" docs/known-defects.md                       # :2869
grep -n "S-04\|S-05\|S-06" docs/research/2026-10-26-batch-24-4e2-admission-checklist.md  # :144/:145/:146
grep -rln "PdfDocument\|PdfRenderer" --include=*.kt core/ app/ feature/ | grep -v /build/ # 主树 15 文件
git status --porcelain -- core/export/                      # 5 M + ?? lockfile
git status --porcelain | grep -c '^??.*lockfile'            # 13
"C:\Android\Sdk\platform-tools\adb.exe" devices -l          # -5562 / -5584 在线
```
