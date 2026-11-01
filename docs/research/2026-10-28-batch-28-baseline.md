# 批次 28 基线核对（2026-10-28，基线核对员）

> **只读核对**：本轮未运行 Gradle、未编译、未执行任何 JVM/仪器化/设备用例、未 `git fetch`、未做任何 git 写操作（本文件是唯一写盘产物）。设备面只执行只读 `adb devices -l`。对照计划：`.zcode/plans/plan-2026-10-28-batch-28-scenev1-device-painter.md` §0（`:3-10`，下称"计划"）。工单第 1–8 项逐项落盘。
>
> **快照时点**：2026-10-28 05:24:16 +0800，`git status --porcelain` 冻结 **673 行（201 M + 472 ??）**；**暂存区 0 条**（`git diff --cached --name-only | wc -l` = 0）。M 分组与批次 27 基线 §1.1 **逐一相同**（201）；?? 472 与批次 27 基线冻结值一致。
>
> "干净"定义：`git status --porcelain -- <path>` 无输出（不代表内容无缺陷）。

---

## 1. HEAD / 工作树 / DB schema / 请求 schema（工单第 1 项）

| 项 | 实测 | 证据（命令/路径行号） |
|---|---|---|
| HEAD | `8116ad98f6d0a7b6ef820a4a2fd1bdaa4d842b4c`（`main`） | `git rev-parse HEAD` |
| origin/main | `8116ad98…`（**与 HEAD 相同**） | `git rev-parse origin/main`；**本轮未 `git fetch`**，为本地远端跟踪引用，远端实时 hash 未核（UNVERIFIED） |
| HEAD 构成 | `8116ad98` = 批次 27 记录补收口（纯文档）；父 `a7b28a47` = 批次 27 主提交（SceneV1 编译器/SDK、内容度量、深冻结、队列裁定、设备审计）——与计划 §0:5-6 一致 | `git log --oneline -3` 实读 |
| **与计划 §0:5 对照** | 计划写 "HEAD = `8116ad98` = origin/main"；**实测一致，无偏差**（批次 27 基线时曾录得 `caa0726a` 偏差，本批无） | 同上 |
| 工作树（冻结 05:24:16） | **201 M + 472 ?? = 673 行；staged = 0** | `git status --porcelain \| wc -l`=673；`grep -c '^ M'`=201、`grep -c '^??'`=472；`git diff --cached --name-only \| wc -l`=0 |
| DB schema | `STUDY_DATABASE_VERSION = 69`（`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabase.kt:134`；`version = STUDY_DATABASE_VERSION` 于 `:233`）；导出目录最高 `69.json`（66/67/68/69 在库）；`StudyDatabase.kt` 与 `core/database/schemas/` **git 干净** | `grep -n` + `ls core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/` + `git status --porcelain`（空） |
| 请求 schema | `TUTOR_VISUAL_CONTEXT_SCHEMA_VERSION = 18`、`CURRENT_SCHEMA_VERSION = TUTOR_VISUAL_CONTEXT_SCHEMA_VERSION`（`core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/ModelTasks.kt:546-547`）；`ModelTasks.kt` **git 干净**（core/model 仅 `?? gradle.lockfile`） | `sed -n '540,550p'` + `git status --porcelain core/model/` |
| VisualJS wire schema | `VisualJsWire.PROTOCOL_VERSION: Int = 2`（`app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsProtocol.kt:32`）；值与批次 27 基线 `:22`（=2）一致，**行号漂移**（:35→:32），无值漂移 | `grep -n PROTOCOL_VERSION` |
| SceneV1 场景请求键面 | `schemaVersion, sceneId, revision, viewBox, background, paths[], root, controls[], hitRegions[], form?, accessibility, fallbackMarkdown`（契约 `docs/current/visual-generation-contract.md:94`；同键面见编译器头注 `visual-compiler.js:9`） | `grep -n schemaVersion` 实读 |

**文档–源码漂移发现（实质输入，归批次 28 T3）**：脏文档 `docs/current/data-schema.md:26` 写 "final source check is **Room 67, request schema 17** … target **68/18** only if those versions remain free at implementation time"。当前源码实测 **DB=69、请求 schema=18**——68 与 18 均已被占用；visual 落表时需取 **70+ / 19+**。`data-schema.md` 属 201 M 之一（开工即脏），其"Room 67/17"是写作时点的源码快照句，非当前事实；本基线以源码常量为权威。

### 1.1 工作树 M 分组（实数，201；与批次 27 基线 §1.1 逐一相同）

- feature **96**（tutor **37** / library **30** / capture **15** / review **11** / profile **3**）
- core **38**（data **10** / ui **11** / database **8** / export **5** / domain **4**）
- docs **36**；app/src **23**（androidTest 10 / main kotlin 10 / test 3）；tools **4**；仓库根 **3**（`.gitignore`、`dynamic_gui_architecture_plan.md`、`settings.gradle.kts`）；knowledge-production **1**。合计 96+38+36+23+4+3+1 = 201。
- ?? 472 关键子计数：**lockfile 13**、core|feature|app 源码区 73（含 core/ui 24、feature/tutor 22、feature/library 13）、docs/ 19、其余为审计/临时产物。

---

## 2. 热区快照对照（工单第 2 项；计划 §0:10）

**计划全文无 `HOT_ZONES` 标题/标识**（与批次 22–27 同型；批次 27 基线 §2 已录 `grep -c "HOT_ZONES" …plan-2026-10-27…` = 0，本批计划 `plan-2026-10-28-…md` 亦无该字面量）。按计划 §0:10 清单逐项对照（冻结时点）：

| 计划 §0:10 热区 | 当前实测（M） | 判定 |
|---|---|---|
| feature 五模块 | tutor **37**、library **30**、capture **15**、review **11**、profile **3** 全脏 | ✓（与批次 27 基线逐一相同） |
| core/ui | **11 M**（`build.gradle.kts` + 9 main + 1 test） | ✓ |
| core/markdown | **整个目录未跟踪**（`?? core/markdown/`，内含 `build.gradle.kts`/`gradle.lockfile`/`src`/`LICENSE`/`README.md`/`upstream-manifest.json`/`build`） | ✓（他线迁移中，未变） |
| core/export | **5 M**（`build.gradle.kts` + 1 main + 3 test） | ✓ |
| core/domain 4M | **4 M**，全部 src/main（§2.1 点名） | ✓ 计数与构成均符 |
| core/data 两 M | src/main 恰 **2**（§2.1 点名）；模块全口径 **10 M**（main 2 + androidTest 5 + test 3） | **✓/口径歧义**（见 §9 #1） |
| settings.gradle.kts | **M**（diff +1 行 `include ":core:markdown"`） | ✓ |
| Root | `app/src/main/kotlin/com/tingyun/smartmistakebook/SmartMistakeBookRoot.kt` **M** | ✓ |
| SecondaryScreens | `app/src/main/kotlin/com/tingyun/smartmistakebook/SecondaryScreens.kt` **M** | ✓ |

计划清单外同脏（热区清单为子集、非穷举，与既往批次同型）：core/database **8 M**、app/src **23 M**、docs **36 M**、tools **4 M**、knowledge-production 1、`.gitignore`、`dynamic_gui_architecture_plan.md`。

### 2.1 core/domain、core/data 开工即脏 M 点名（工单第 2 项）

**core/domain（4，全 src/main，与计划"4M"一致）：**
1. `core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/BatchImportRepository.kt`
2. `core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/MistakeDetailRepository.kt`
3. `core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/SchedulingEvaluation.kt`
4. `core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/TutorJudgedReview.kt`

**core/data（模块共 10；其中 src/main 2 = 计划"两 M"口径）：**
- main（2）：`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/mistake/RoomMistakeDetailRepository.kt`、`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/RoomTutorToolRunner.kt`
- androidTest（5）：`capture/BatchImportRepositoryInstrumentedTest.kt`、`capture/SplitImportCommitReceiptInstrumentedTest.kt`、`capture/SplitImportReceiptReconciliationInstrumentedTest.kt`、`model/ModelTaskRepositoryInstrumentedTest.kt`、`study/StudySubmissionWithoutFixtureInstrumentedTest.kt`（前缀 `core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/`）
- test（3）：`knowledge/GoldenRetrievalJvmTest.kt`、`model/OpenAiNativeToolsProtocolTest.kt`、`model/TutorToolRequestDualParseTest.kt`（前缀 `core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/`）

注：core/data 的 visual 面（§3）**不在**脏列——开工即净。

---

## 3. 批次 28 落点脏态（工单第 3 项）

**结论：四个落点全部干净（开工即净），全部已入库于 HEAD（代码面 `a7b28a47`）。** 实测 `git status --porcelain -- <四个路径>` 输出为空：

| 落点 | 文件数 | git 状态 |
|---|---|---|
| `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/` | 10，全 tracked（`git ls-files \| wc -l`=10） | **干净** |
| `app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/` | 5，全 tracked | **干净** |
| `docs/known-defects.md` | 1，tracked | **干净** |
| `docs/current/visual-generation-contract.md` | 1，tracked | **干净** |

visual/ 全 10 文件点名（`ls` 实读）：`DefaultFigureGenerationCoordinator.kt`、`FigureGenerationAuditRecorder.kt`、`FigureGenerationReconciler.kt`、`QuickJsVisualExecutionEngine.kt`、`RoomFigureDispatchLedger.kt`、`RoomFigureGenerationAuditRecorder.kt`、`VisualDrawOps.kt`、`VisualDrawOpsValidator.kt`、`VisualJsAssets.kt`、`VisualJsFailureClassification.kt`。
visualjs/ 全 5 文件：`VisualContentMeasuring.kt`、`VisualJsClientEngine.kt`、`VisualJsFrameQueue.kt`、`VisualJsProtocol.kt`、`VisualJsService.kt`。

附带核（painter 输入面）：`core/data/src/main/resources/visual/visual-compiler.js`（44,718 B）与 `visual-sdk.js`（11,429 B）、Node 台 `tools/visual_compiler/run_tests.mjs` 均 tracked 且**干净**。注意 `?? app/src/main/assets/` 整目录未跟踪（内仅 `open-source/`），与 visual 无关——JS 资产装载走 core:data resources classloader（`VisualJsAssets.kt:19-22`，`visual/visual-compiler.js`/`visual/visual-sdk.js`），非 app assets。

---

## 4. 批次 27 复核修复在树核验（工单第 4 项；读码引用，全部实读）

| 修复 | 位置（实读） | 在树证据 |
|---|---|---|
| `jsStringLiteral` 调用点 | `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/QuickJsVisualExecutionEngine.kt:756` | `"__visualCompile(" + "${jsStringLiteral(sceneJson)}, ${jsStringLiteral(metricsJsonText)}, ${jsStringLiteral(expectJson)})"` 三参全部经编码；KDoc `:750-753` 指认消灭的失败（`$` 直插 → 实参成 JS 对象值 → SCENE_FIELD_TYPE，批次 27 设备轨隔离 10 红/引擎 9 红） |
| `jsStringLiteral` 函数 | 同文件 `:1034` | 转义集：反斜杠/双引号/C0 具名（`\b\f\n\r\t`）+ 其余 `<0x20` 走 `\u00XX`；KDoc `:1025-1032` 注明调用面仅 runCompilePhase，prepare/render 的对象表达式注入不属同型 |
| 危险键拒绝 | `core/data/src/main/resources/visual/visual-compiler.js:270-272` | `if (key === '__proto__' || key === 'constructor' || key === 'prototype') fail('SCENE_PARSE_POISONED_KEY', …)` |
| `nullProtoTable` + 自有键查表 | 同文件 `:438-444`（函数）、`:446-450`（FILL_RULES/CAPS/JOINS/H_ALIGNS/V_ALIGNS 五表全 null-proto）、KDoc `:432-437`（指认 stroke.cap='constructor'/join='toString' 等继承键绕过失败）、首个使用 `checkKeys(stroke, …)` `:454` | 工单区间 ":434-455" 覆盖 KDoc+函数+表+首用，实读吻合；批次 27 记录 `docs/research/2026-10-27-batch-27-record.md:80/:153/:309` 自报现值 :438-450/:270 一致 |

与批次 27 记录自报值（:756/:1034/:438-450/:270）**零漂移**。三个载体文件（引擎/编译器 JS/记录文档）git 全干净（§3），即修复固化于 HEAD。

---

## 5. DrawOps V1 词表与 R1 §4 宿主映射点原文（工单第 5 项）

**编译器输出形状**（`visual-compiler.js` 头注 `:18-27` 实读）：

```
输出 DrawOps V1（R1 §3.2 词表；宿主 VisualDrawOpsValidator 以同一套不变量复验）：
  { drawSchemaVersion:1, viewBox:{x,y,w,h}, background, ops[], hitRegions[],
    stats:{nodes,pathCommands,labels,ops} }
  - 路径命令仅 ["M",x,y]/["L",x,y]/["C",…]/["Z"]（Q 在此提升为 C；无 A——弧由 SDK 拆 ≤90° C 段）
  - 组发射顺序：save → [clip(烘焙组合坐标)] → [concat(本地矩阵)] → children → restore
  - save/restore 严格配平；group alpha 乘入子节点（预乘）
```

**R1 §3.2 词表与七不变量**：`docs/research/2026-10-27-scenev1-compiler-design.md:95-128`（该文档 tracked 且干净）。
**R1 §4 宿主映射点原文**（`:171-182` 实读，painter 落码的设计输入）：

> | DrawOp | Android Canvas 映射点 | 注 |
> |---|---|---|
> | `save`/`restore` | `Canvas.save()` / `restore()` | 配平由校验器保证，painter 不自愈 |
> | `concat` | `Canvas.concat(Matrix)`；契约 `(a,b,c,d,e,f)`（x'=ax+cy+e）→ Android `Matrix.setValues` 列序为 `[MSCALE_X, MSKEW_X, MTRANS_X, MSKEW_Y, MSCALE_Y, MTRANS_Y]`，即 `[a,c,e,b,d,f]` | **skew 列序错位是此映射的头号隐性错误**（lottie-android 的矩阵桥接为参考实现） |
> | `clip` | Path 重建 → `Canvas.clipPath(path, Region.Op.INTERSECT)` | 契约"clip 只相交"；API 23+ 安全 |
> | `path` | Path：moveTo/lineTo/cubicTo/close；fillType = Path.FillType.NONZERO/EVEN_ODD；fill 先画（style=FILL）、stroke 后画（style=STROKE，dash → `DashPathEffect`）；alpha → `Paint.setAlpha(floor(alpha*255))` | 一条路径带 fill+stroke 时画两遍（fill→stroke 叠序） |
> | `label` | 冻结文本/数学 artifact 在已算定 (x,y,rotation) 绘制：save → rotate(anchor) → draw → restore | baseline 已在编译期解析；du→px 由根变换一次完成 |
> | `background` | `Canvas.drawColor` / Compose 容器背景 | — |
> | `hitRegions` | 原生点内判定（同 fill rule） | 组合后坐标，无需逆矩阵 |
> | 整表 | PDF：同一 op 开关打在 `PdfDocument` page.canvas（painter 共用）；SVG writer 为 4E.2 附加交付 | "PDF/SVG 不重新执行 JS，不重新采样曲线或移动标签" |

**VisualDrawOps.kt 六原语**（`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualDrawOps.kt:95-140`，sealed interface `VisualDrawOp`）：**Save**（:98）/ **Concat**（:104）/ **Clip**（:110）/ **DrawPath**（:116）/ **Label**（:127）/ **Restore**（:139）。路径命令四原语 `VisualPathCommand`（MoveTo/LineTo/CubicTo/Close，:20-34，KDoc："仅 M/L/C/Z；Q 已在编译器提升为 C"）。

**矩阵列序警示在 Kotlin 侧同样在树**（`VisualDrawOps.kt:76-82` KDoc）：

> 矩阵 `[a,b,c,d,e,f]`：`x' = a·x + c·y + e; y' = b·x + d·y + f`（契约 §5）。
> **painter 映射警示（R1 §4）**：Android `Matrix.setValues` 列序为 `[MSCALE_X, MSKEW_X, MTRANS_X, MSKEW_Y, MSCALE_Y, MTRANS_Y]`，即本矩阵须以 `[a, c, e, b, d, f]` 装载——skew 列序错位是该映射的头号隐性错误。

---

## 6. 锁窗口再判定（工单第 6 项；计划 §1"锁文件（窗口判定驱动）不做"）

**6 个解析图 build 文件 git 状态（`git status --porcelain -- <path>` 实测）：无一干净。**

| 文件 | 状态 | 差异内容（`git diff` 实读） |
|---|---|---|
| `settings.gradle.kts` | M | +1 行：`include 列表加 ":core:markdown"` |
| `core/ui/build.gradle.kts` | M | ±1 行：`implementation(libs.richtext.commonmark)` → `implementation(project(":core:markdown"))` |
| `feature/tutor/build.gradle.kts` | M | +2 行：`implementation(project(":core:export"))`（"6B-2：卷面编辑模型与草稿类型"注） |
| `core/export/build.gradle.kts` | M | +3 行：kotlin.serialization 插件 + `kotlinx.serialization.json`（"6B-2 卷面草稿落盘"注） |
| `.gitignore` | M | +3 行：`# Superdesign temporary templates` + `.superdesign/tmp/` |
| core/markdown（build.gradle.kts） | **整目录 ??** | `core/markdown/` 未跟踪，内含 `build.gradle.kts`、`gradle.lockfile`、`src/` 等 7 项 |

**差异定征**：五个 M 文件的差异全部是 6B-2/`core:markdown` 接线与 superdesign 忽略项，**无一是依赖锁启用差异**；即这批脏是既有他线接线态，不是锁窗口工作留下的。

**暂存区锁文件计数 = 0**（`git diff --cached --name-only | wc -l` = 0，暂存区整空）。
**未跟踪锁文件 = 13**：12 个模块 `gradle.lockfile`（app、core/data、core/database、core/domain、core/export、core/model、core/ui、feature/capture、feature/library、feature/profile、feature/review、feature/tutor）+ 根 `settings-gradle.lockfile`；另 `core/markdown/gradle.lockfile` 随整目录未跟踪（目录级 ?? 不单独计数）——与批次 27 基线 §1.1 "lockfile 13" 一致，无新增。

**判定结论：锁窗口未开。** 干净树前置不满足（6/6 build 面不干净）、锁文件全部未跟踪、暂存区零锁文件；与计划 §1 "锁文件（窗口判定驱动）、不做" 相容，批次 28 无需为此改树。

---

## 7. KD-76 剩余原文与准入清单 P-07/P-12 现状（工单第 7 项）

**KD-76 open**：`docs/known-defects.md:2819`（标题）——"隔离门 6/6 不含 warm-up/ready：冷启动首个 execute 仍可能被 1500 ms watchdog 误杀，按现判据可在首个用户请求必败时放行 JS 广告"。块域 `:2819-2853`（下一 KD-77 `:2855`）。两文档均 tracked 且干净（§3）。

**剩余缺口原文摘录**（最新 Progress 块 `:2851`，批次 27 设备轨记录员回填）：

> **本条保持 open，设备验证前 JS 不广告**（③ 产品进程/UI 首请求、29.5 s worst-case 链、低内存、R3 广告门文本升级等既有缺口不随本块消失）。

同块另录：warm-up 装载成本已出数（E-04：控制组 p50=686/p95=725/max 738 ms；26/26 ready；arm64/真机/低内存/高负载 UNVERIFIED）；**同轮隔离回归 13 例 10 红（E-05）= T1 接线缺陷，修复已在树且单测门复跑 exit=0，修复后设备复验 UNVERIFIED（隔离套件未再上设备）**——即计划 §0:7"批次 28 第一要务=修复后设备全绿复跑"的台账面依据。判据口径块 `:2843` 另录剩余：`READY_TIMEOUT=10 s` 设备校准、29.5 s worst-case 链与低内存条件 UNVERIFIED；③ 残余是否随批次 28 设备轨复验由主会话/设备轨编排定。

**准入清单**（`docs/research/2026-10-26-batch-24-4e2-admission-checklist.md`，v3，69 行；tracked 且干净）：

- **P-07**（`:130`，warm-up/ready 门（KD-76））：**部分落，本门未过**——代码层已落（服务 onCreate warm-up→单调 ready、客户端轮询 isReady 10 s 预算、loading 不锁存）+ ④ 强证据（warm 挂死 1500 ms 硬杀 + hard+500 ms 三轮绿）+ ⑤ 行为层 ×2 轮绿 + ③ PARTIAL **不判定达成**；缺口列 = ③ 产品进程/UI 首请求、29.5 s worst-case、低内存条件、READY_TIMEOUT 校准，均 UNVERIFIED。该行"warm-up 实测装载成本归批次 27 设备轨"标注已被 KD-76 `:2851` E-04 块取代（原文自述"就该项被本块取代"）。
- **P-12**（`:135`，programHash 熔断与本地重试预算承继）：**部分落（v3 勘误）**——熔断代码自批次 20/21/22 存在（端口字段 `explicitUserRetry`、引擎 `fusedProgramDigests`、客户端 `fusedDigests`）、设备断言 `PROGRAM_FUSED` 自批次 22 存在 → G-7① 设备绿已有；**G-7② 显式重试预算承继无任何证据**（生产侧无 `explicitUserRetry=true` 调用点，仅测试 4 处）；③ 无直接断言；客户端熔断 JVM 状态机专项测试未建；归属"待主会话排期"。

---

## 8. 设备在线状态（工单第 8 项）

`C:\Android\Sdk\platform-tools\adb.exe devices -l` 实跑（`adb` 不在 Git Bash PATH；`ANDROID_HOME=C:\Android\Sdk`）：

```
emulator-5554          device product:sdk_gphone64_x86_64    model:sdk_gphone64_x86_64    device:emu64xa      transport_id:15
emulator-5556          device product:sdk_gphone16k_x86_64   model:sdk_gphone16k_x86_64   device:emu64xa16k   transport_id:11
emulator-5584          device product:sdk_gphone64_x86_64    model:sdk_gphone64_x86_64    device:emu64xa      transport_id:7
```

- **3 台全部在线（`device` 态，无 offline/unauthorized）**。
- **ps16k = `ps16k_x86_64` AVD（API 36，页大小 16384）= emulator-5556**（术语与映射依据批次 27 记录 `docs/research/2026-10-27-batch-27-record.md:115`——"emulator-5556（ps16k_x86_64，API 36，16384）只读核"；`:206` "ps16k/KD-70/16KB 面本批未执行"）；本批该 AVD 在线，`emu64xa16k`/`sdk_gphone16k_x86_64` 产品串与 16 KB 页形态吻合。
- emulator-5554 = test_device（API 34 x86_64，4096 页，批次 27 设备轨五步主机，记录 `:115`）；emulator-5584 属他线（记录 `:115` "5584 属他线全程未触碰"）——批次 28 设备轨若复用需沿用该分工登记。
- 本轮仅只读 `adb devices`，未安装/未执行/未改设备态。

---

## 9. 与计划 §0 不符处（汇总）

1. **"core/data 两 M"为口径歧义**（计划 §0:10）：src/main 口径下成立（恰 2：`RoomMistakeDetailRepository.kt`、`RoomTutorToolRunner.kt`）；按模块全 M 计数实为 **10**（另有 androidTest 5 + test 3，§2.1）。批次 27 基线 §2 同款判定（"✓/口径歧义"），非新增偏差。
2. **脏文档 data-schema.md 与源码漂移**（§1 表）：`docs/current/data-schema.md:26`（M 态）写 "Room 67, request schema 17 / target 68/18"；源码实测 **DB=69、请求 schema=18**，68/18 均已被占用——visual 落表版本选择与 T3 台账同步的实质输入。"request schema 17" 在源码中未定位到对应常量（`grep -rni "requestSchema\|REQUEST_SCHEMA"` 于 core/、app/src/main/ 仅命中迁移测试 helper，无 17 值常量）——按文档孤证处理，当前请求 schema 以 `ModelTasks.kt:546-547`（=18）为权威。
3. 其余逐项（HEAD=origin/main=`8116ad98`、批次 27 交付物与两处修复在树、DrawOps/R1 §4 契合、KD-76 open、热区脏态）**与计划 §0 一致，无偏差**。

## 10. 本轮未验证项（如实）

- **UNVERIFIED**：origin/main 远端实时 hash（未 `git fetch`，对照为本地跟踪引用）。
- **UNVERIFIED**：修复后设备复跑（隔离 13 + 编译器套件 + 引擎 26）——本基线只读核对，不执行；即计划 T1 的对象，批次 28 设备轨待办。
- **UNVERIFIED**："request schema 17" 的源码常量（未定位，§9 #2）。
- 本轮未运行 Gradle/编译/任何测试；未改任何工作树文件（本文件为唯一写盘产物，落盘后自身成为第 473 个 ??）。

## 11. 复核命令清单（可重跑）

```bash
git rev-parse HEAD && git rev-parse origin/main
git status --porcelain | sort                 # 673 行 = 201 M + 472 ??
git status --porcelain | grep -c '^ M'        # 201
git diff --cached --name-only | wc -l          # 0（暂存区整空 → 暂存区锁文件 0）
git status --porcelain -- core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/ \
  app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/ docs/known-defects.md \
  docs/current/visual-generation-contract.md   # 空 = 四落点全干净
grep -n "jsStringLiteral" core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/QuickJsVisualExecutionEngine.kt   # :751/:756/:1034
grep -n "nullProtoTable" core/data/src/main/resources/visual/visual-compiler.js   # :438 函数 + :446-450 五表
grep -n "STUDY_DATABASE_VERSION" core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabase.kt  # :134 = 69
sed -n '540,550p' core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/ModelTasks.kt   # TUTOR_VISUAL_CONTEXT_SCHEMA_VERSION = 18
git diff -- settings.gradle.kts core/ui/build.gradle.kts feature/tutor/build.gradle.kts core/export/build.gradle.kts .gitignore
git status --porcelain | grep -i lockfile      # 13（目录级 core/markdown/ 另含其一）
"C:\Android\Sdk\platform-tools\adb.exe" devices -l   # 3 台 device 态；5556 = ps16k
```
