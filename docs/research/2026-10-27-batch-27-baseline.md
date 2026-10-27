# 批次 27 基线核对（2026-10-27，基线核对员）

> **只读核对**：本轮未运行 Gradle、未编译、未执行任何 JVM/仪器化/设备用例、未 `git fetch`、未做任何 git 写操作（本文件是唯一写盘产物）。设备面只执行了只读 `adb`/`emulator -list-avds`/`emu avd name`/`getconf`/`getprop` 查询。对照计划：`.zcode/plans/plan-2026-10-27-batch-27-4e2-mva-static.md` §0（`:3-8`，下称"计划"）。
>
> **快照时点**：2026-10-27 23:42:45 +0800，`git status --porcelain` 冻结 **673 行（201 M + 472 ??）**；**暂存区 0 条**（`git diff --cached --name-only | wc -l` = 0）。M 分组与批次 26 基线 §1.1 **逐一相同**（201）；?? 472 与批次 26 基线冻结值一致（其收尾时的第 473 个 ?? 即批次 26 基线文档自身，已随 `7b2d6905` 入库）。
>
> "干净"定义：`git status --porcelain -- <path>` 无输出（不代表内容无缺陷）。

---

## 1. HEAD / 工作树 / DB schema / 请求 schema

| 项 | 实测 | 证据（命令/路径行号） |
|---|---|---|
| HEAD | `caa0726a02f1862371a3b2770c8c29663ba7fa02`（`main`） | `git rev-parse HEAD` |
| origin/main | `caa0726a…`（**与 HEAD 相同**） | `git rev-parse origin/main`；**本轮未 `git fetch`**，这是本地远端跟踪引用，远端实时 hash 未核（UNVERIFIED） |
| HEAD 构成 | `caa0726a` 是**纯文档提交**：`docs/research/2026-10-27-batch-26-record.md` +9 行（批次 26 主会话收口）；父提交 `7b2d6905`（批次 26 主提交，**26 文件 +4,742/−100**：KD-66 hard+500 ms 用例与关闭、隔离门 13+2、AUD-08 切分金标门工具面、MVA 复判与清单 v3、批次 26 基线/记录/证据） | `git show --stat --oneline caa0726a`、`git show --stat --oneline 7b2d6905` |
| **与计划 §0:5 的偏差** | 计划写 "HEAD = `7b2d6905`"；**实测 HEAD = `caa0726a`（`7b2d6905` 为 HEAD~1）**。差异 = 批次 26 记录补收口的 1 文档提交，非内容冲突；"= origin/main" 仍成立 | `git log -1 --format='%h %s'` → `caa0726a docs(4e,aud08): 批次26 记录补主会话收口…` |
| 工作树（冻结 23:42:45） | **201 M + 472 ?? = 673 行；staged = 0** | `git status --porcelain \| wc -l`=673；`grep -c '^ M'`=201、`grep -c '^??'`=472；`git diff --cached --name-only \| wc -l`=0 |
| DB schema | `STUDY_DATABASE_VERSION = 69`（`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabase.kt:134`；`version = STUDY_DATABASE_VERSION` 于 `:233`）；导出目录 **69 个 schema 文件**，最高 `69.json`；`StudyDatabase.kt` 与 `core/database/schemas/` **git 干净** | `grep -n` + `ls …/schemas/…StudyDatabase/ \| wc -l`=69 + `git status --porcelain`（空） |
| 请求 schema | `TUTOR_VISUAL_CONTEXT_SCHEMA_VERSION = 18`、`CURRENT_SCHEMA_VERSION = TUTOR_VISUAL_CONTEXT_SCHEMA_VERSION`（`core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/ModelTasks.kt:546-547`）；`ModelTasks.kt` **git 干净**（core/model 仅 `?? gradle.lockfile`） | `grep -n` + `git status --porcelain core/model/` |
| VisualJS wire schema | `VisualJsWire.PROTOCOL_VERSION: Int = 2`（`app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsProtocol.kt:35`）；与批次 26 基线 `:22`（=2）一致，无漂移 | `grep -n PROTOCOL_VERSION` |

### 1.1 工作树 M 分组（实数，201；与批次 26 基线 §1.1 逐一相同）

- feature **96**（tutor **37** / library **30** / capture **15** / review **11** / profile **3**）
- core **38**（data **10** / ui **11** / database **8** / export **5** / domain **4**）
- docs **36**；app/src **23**（androidTest 10 / main kotlin 10 / test 3）；tools **4**（`dense_build/gen_device_parity_fixture.py`、`dense_build/model-manifest.json`、`kb_build/merge_candidate_verdicts.py`、`tests/test_kb_merge_candidate_verdicts.py`——均属他线 KB/审计面）；仓库根 **3**（`.gitignore`、`dynamic_gui_architecture_plan.md`、`settings.gradle.kts`）；knowledge-production **1**。合计 96+38+36+23+4+3+1 = 201。
- ?? 472 关键子计数（`grep -c`）：lockfile **13**、`tmp*` 顶层 **4**、`docs/` **19**、core|feature|app 源码区 **73**（他线活动区，含 core/ui 24、feature/tutor 22、feature/library 13、tools/kb_coverage 11 等）。

---

## 2. 热区快照对照（计划 §0:7）

**计划全文无 `HOT_ZONES` 标题/标识**：`grep -c "HOT_ZONES" .zcode/plans/plan-2026-10-27-batch-27-4e2-mva-static.md` = **0（exit=1）**，与批次 22–26 同型。按计划 §0:7 清单逐项对照（冻结时点）：

| 计划 §0:7 热区 | 当前实测（M） | 判定 |
|---|---|---|
| feature 五模块（含 profile） | tutor **37**、library **30**、capture **15**、review **11**、profile **3** 全脏 | ✓（与批次 26 基线逐一相同） |
| core/ui | **11 M**（`build.gradle.kts` + 9 main + 1 test） | ✓ |
| core/markdown | **整个目录未跟踪**（`?? core/markdown/`）；工作树 `settings.gradle.kts:27` 有 `":core:markdown"`，**HEAD 无**（`git show HEAD:settings.gradle.kts \| grep markdown` exit=1） | ✓（他线迁移中，未变） |
| core/export | **5 M** | ✓ |
| core/domain 4M | **4 M**（§2.1） | ✓ 计数与构成均符 |
| core/data 两 M | **共 10 M**（main **2** + androidTest 5 + test 3，§2.2）；"两 M"仅在"main 源码口径"下成立——若按总 M 计数读，计划低报 8 | **✓/口径歧义**（见 §10.1 #2） |
| settings.gradle.kts | **M**（`include(` 于 `:27`） | ✓ |
| Root | `SmartMistakeBookRoot.kt` M、`SmartMistakeBookDestinations.kt` M | ✓ |
| SecondaryScreens | `SecondaryScreens.kt` M | ✓ |

### 2.1 core/domain 开工即脏 M 全路径（4 个，与批次 25/26 基线逐一相同）

1. `core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/BatchImportRepository.kt`
2. `core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/MistakeDetailRepository.kt`
3. `core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/SchedulingEvaluation.kt`
4. `core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/TutorJudgedReview.kt`

**`VisualExecutionEngine.kt` 不在脏列**（git 干净，§3）。另有未跟踪 `core/domain/gradle.lockfile`（KD-72 面不变）。

### 2.2 core/data 开工即脏 M 全路径（10 个，与批次 25/26 基线逐一相同）

main（2）：`mistake/RoomMistakeDetailRepository.kt`、`study/RoomTutorToolRunner.kt`。
androidTest（5）：`capture/BatchImportRepositoryInstrumentedTest.kt`、`capture/SplitImportCommitReceiptInstrumentedTest.kt`、`capture/SplitImportReceiptReconciliationInstrumentedTest.kt`、`model/ModelTaskRepositoryInstrumentedTest.kt`、`study/StudySubmissionWithoutFixtureInstrumentedTest.kt`。
test（3）：`knowledge/GoldenRetrievalJvmTest.kt`、`model/OpenAiNativeToolsProtocolTest.kt`、`model/TutorToolRequestDualParseTest.kt`。

**`core/data/src/{main,test,androidTest}/.../visual/` 三目录全部干净**（§3）。另有 `?? core/data/gradle.lockfile`。

### 2.3 core/database 开工即脏 M 全路径（8 个，与批次 25/26 基线逐一相同；计划 §0:7 未列该模块，实脏）

1. `androidTest/.../LibraryFtsCountPathInstrumentedTest.kt`
2. `androidTest/.../LibraryFtsCountPathScale50kInstrumentedTest.kt`
3. `androidTest/.../LibraryFtsRankingPathInstrumentedTest.kt`
4. `main/.../RoomBatchImportStore.kt`
5. `main/.../TutorConversationDatabaseContract.kt`
6. `main/.../dao/BatchImportDao.kt`
7. `main/.../dao/TutorConversationDao.kt`
8. `main/.../port/CleanRedrawTaskPort.kt`

另有 `?? core/database/gradle.lockfile`；`StudyDatabase.kt` 与 `schemas/` 本身干净。

---

## 3. 批次 27 落点脏态逐个核对

`git status --porcelain -- <path>` 逐个实测，**全部空（干净）**：

| 落点 | porcelain | 本轮实读补充事实 |
|---|---|---|
| `core/data/src/main/.../visual/` 全部（7 文件） | **空** | `DefaultFigureGenerationCoordinator.kt`（586 行）、`FigureGenerationAuditRecorder.kt`（150）、`FigureGenerationReconciler.kt`（117）、**`QuickJsVisualExecutionEngine.kt`（854）**、`RoomFigureDispatchLedger.kt`（161）、`RoomFigureGenerationAuditRecorder.kt`（131）、`VisualJsFailureClassification.kt`（207）；合计 2,206 行。test/ 10 文件、androidTest/ 4 文件亦干净 |
| `app/src/main/kotlin/.../visualjs/` 全部（4 文件） | **空** | `VisualJsClientEngine.kt`（**1,262 行**）、`VisualJsService.kt`（646）、`VisualJsProtocol.kt`（410）、`VisualJsFrameQueue.kt`（216）；AIDL 2 文件（46/32 行）干净；androidTest：`VisualJsIsolationInstrumentedTest.kt`（**1,105 行、13 个 `@Test`**——较批次 26 基线 1,001 行/12 例，因批次 26 T1 hard+500 ms 用例入类；记录 `2026-10-27-batch-26-record.md:112` "实为 13 例" 一致）、`VisualJsHandshakeRaceInstrumentedTest.kt`（300）均干净 |
| `core/domain/.../VisualExecutionEngine.kt` | **空** | **288 行**（批次 26 基线未单列行数；本轮实读全文）。`grep -i visual` 在 core/domain 仅此一个 visual 相关源文件，无其他 `VisualExecutionEngine*` |
| `docs/known-defects.md` | **空** | **2,861 行**（批次 26 基线时 2,850，+11 行随 `7b2d6905`）；锚点：KD-66 `:2573`（**closed 2026-10-27 批次 26**）、KD-72 `:2746`（open）、KD-76 `:2819`（open）、KD-77 `:2853`（open，API 23） |
| `docs/current/visual-generation-contract.md` | **空** | **220 行**（批次 26 基线时 218，+2 行 = 批次 26 T4 `:200` 门登记 + T5 `:72` 刷新，随 `7b2d6905` 入库） |

**模块级提醒**：落点干净 ≠ 模块干净——app 23 M、core/data 10 M + 1 ??、core/domain 4 M + 1 ??、core/database 8 M + 1 ??、tools 4 M、docs 36 M；排提交必须按路径白名单，禁止模块级 `git add`。计划 §0:8 把 `tools/` 列为干净落点，但 tools/ 现有 **4 个 M**（他线 dense_build/kb_build 面）——批次 27 若在 tools/ 落新文件，新增路径与这 4 个 M 互不重叠，仍须白名单提交。

---

## 4. SceneV1 现状（计划 §0:6 缺口 + §1 T1 设计输入）

### 4.1 契约 §3.2 JS后端协议原文（`docs/current/visual-generation-contract.md:43-58`，逐字）

> 以下JS执行/场景约束适用于JS方法，不是其他业务入口的另一套判定规则。
>
> 代码声明 `const visual = { prepare(input), render(input, metrics), reduce(state, event) }`；SDK 在同一全局上下文提供纯函数图形帮助方法。模块加载器、主机对象和 import 均不开启。bootstrap 在用户源码前运行，封闭 Date、Math.random 等隐式时钟/随机源；随机必须使用 input.seed 的 SDK PRNG。用户源码永远视为不可信，bootstrap 限制不能替代进程隔离和输出验证。
>
> `FrameInput` 包含 `{programId, revision, state, viewport, timeMs, seed, theme}`；只含本图数据，无密钥、凭据、文件路径或全库对象。一次布局事务冻结这些值：
>
> 1. 新建 QuickJS 上下文执行源码和 `prepare(input)`，返回批量内容度量请求，随后关闭上下文。
> 2. 原生验证并度量 text/math，保留不可变布局 artifact，返回宽、高、baseline、inkBounds、实际字号与内容摘要。
> 3. 新建上下文重新执行同一源码和 `render(input, metrics)`；必须只引用本事务 contentId。拒绝新内容、暗改字体、宽度或无限追加测量。
> 4. 原生严格校验、共享 compiler 编译，原子提交 RenderScene。reduce 同样使用新上下文；程序不能依赖跨调用全局变量。
>
> 每事务最多一次 prepare、一次 measure batch、一次 render。……
>
> 阶段能力按4E.1统一管线/静态后端、4E.2交互、4E.3复杂源与保真扩展分别声明；……静态程序可省略 reduce；**每次调用的 input 与 metrics 深冻结**，reduce 仅能返回符合 StateV1 的局部状态。未启用的控件/事件/模式在模型能力上下文中不广告，解析与执行也同时拒绝，不仅隐藏 UI。

### 4.2 契约 §5 场景/编译/SDK 关键原文（`:92-104`，逐字摘录；行号为冻结时点）

- `:94` SceneV1 形状：`SceneV1：schemaVersion, sceneId, revision, viewBox, background, paths[], root, controls[], hitRegions[], form?, accessibility, fallbackMarkdown。只有 group/path/label 三类绘图节点，原生控件与可选表单另列；form 仅在 4E.2 能力开启时允许。`
- `:96` path：`path 定义为 M/L/Q/C/Z 数值段数组，不接收 SVG d 字符串。必须 M 开始；Q 在共享 compiler 转 C；圆、弧、箭头、轴由 SDK 生成基础路径，弧拆为每段不超过 90° 的三次曲线，误差算法随 SDK 版本固定。`
- `:97-98` group/矩阵：`group 含本地矩阵、opacity、visible、可选 clip path、children；…只支持 srcOver。clip 只相交，fillRule 为 nonzero/evenodd…`；`矩阵约定 x'=a*x+c*y+e, y'=b*x+d*y+f，组合 parent*local。…stroke 随变换缩放。`
- `:99` label：`label 引用已度量 contentId，…compiler 一次算出最终位置，不由 Compose/PDF 各自猜 baseline。第一版无自动标签避让优化器…`
- `:102` RenderScene：`RenderScene 是校验后的不可变显示列表：固定路径、矩阵/裁剪、已放置 glyph/line/path 或文本 artifact、语义与命中区。不可包含函数、脚本或回调代码。core:model 的协议与 compiler 不依赖 Compose；core:visual 的 AndroidScenePainter 共用于 Compose nativeCanvas 和 PDF page.canvas。`
- `:104` 编排处方：`原生执行编排由 app 的 AndroidVisualExecutionEngine 实现 core:domain.VisualExecutionEngine 端口，组合 core:visual-js 客户端、core:visual 内容布局和纯 compiler。core:data 只通过该端口做来源/幂等/持久化/CAS，不直接引用 AIDL/原生排版。…`
- §5.1（`:106-119`）目标 vs 现状表（批次 22 登记/批次 23 复核）：`AndroidVisualExecutionEngine`、`core:visual-js`、`core:visual`、`AndroidScenePainter` **均不存在**——本轮未重跑该表逐项 grep，仅实证 `core/data` 引擎（§3）与 `app visualjs`（§3）落位未变、`settings.gradle.kts` 无 visual 模块（HEAD 无 markdown 亦无 visual，`git show HEAD:settings.gradle.kts` 实读）。

### 4.3 契约 §6 交互与无障碍关键原文（`:125-141`，逐字摘录）

- `:127` `原生控件为 button/toggle/range/select/transport；…事件为 activate/change/drag/transport，带 programId、sceneId、sceneRevision、sequence。事件值经本地校验，再调用纯 reduce；native clock 提供 timeMs。过期 revision 不写回。`
- `:129` `事件与渲染是不同队列：reduce 单在飞、离散事件按宿主 sequence 的有界 FIFO（最多16项）执行，…重复 sequence 不重复应用。`
- `:131` `4E.2 另补原生表单协议 NativeTutorFormV1，作为 SceneV1 的可选 form…：formId、题项 radioGroup、理由 textInput 与 submit。每表单≤4 题、每题≤6 选项、理由≤1000 UTF-16 code units…。4E.1 不广告 form。`

### 4.4 端口 prepare/render 阶段定义（本轮实读全文）

`core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/VisualExecutionEngine.kt`（288 行，git 干净）：

- 端口 KDoc `:21-23`（阶段定义原文）：`每次执行 = 一个有界事务：新上下文执行源码 → prepare(input) → 原生度量（经 [VisualContentMeasurer]，一次批量）→ 新上下文重执行 + render(input, metrics) → 输出校验。程序不能依赖跨调用全局变量（契约 §3.2）。`
- 端口面：`interface VisualExecutionEngine { suspend fun execute(request): VisualExecutionResult; fun close() }`（`:40-46`）；`VisualExecutionRequest`（`:56-84`，字段含 `explicitUserRetry` :72）；预算单源 `VisualExecutionBudget`（`:106-139`：源码 64 KiB `:108`、state 16 KiB `:111`、事务 JSON 256 KiB `:114`、堆 16 MiB `:117`、栈 256 KiB `:120`、协作期限 100 ms `:123`、**服务硬期限 1500 ms `:126`**、宿主期限 2500 ms `:129`、内容 128 条/6000 字符 `:132/:135`、画布 4096 du `:138`）。
- 度量 seam：`interface VisualContentMeasurer`（`:149-151`）、`VisualContentMeasureBatch`（`:154-169`，构造器强制 128 条/6000 字符）、`VisualContentMeasureEntry`（`:172-179`，`kind` 只 TEXT/MATH `:181-184`）、`VisualMeasuredContent`（`:199-215`：contentId/widthDu/heightDu/baselineDu/fontSizeSp/digest；`:188-189` 允许空批次：`纯几何场景（无 text/math 内容）的 prepare 返回空批次是合法事务，空结果不构成失败`）。
- 结果面：`Rendered(sceneJson, sceneSchemaVersion, contentIds)`（`:223-235`）/ `Rejected(reason, diagnostics)`（`:238-247`）；拒绝分类 10 值（`:258-288`）。

### 4.5 引擎侧阶段实现与"快照/程序哈希/熔断"既有结构（`QuickJsVisualExecutionEngine.kt`，854 行，git 干净；本轮实读全文）

- **阶段实现**：`runPhase(transaction, jsDispatcher, phaseName, inputJson, metricsJsonText=null)`（`:395-516`）；常量 `PHASE_PREPARE = "prepare"`（`:794`）、`PHASE_RENDER = "render"`（`:795`）；prepare 调用点 `js.evaluate("JSON.stringify(visual.prepare($inputJson))", PREPARE_FILENAME)`（`:446-450`）、render 调用点 `js.evaluate("JSON.stringify(visual.render($inputJson, $metricsJsonText))", RENDER_FILENAME)`（`:451-456`）；程序协议门 `requireVisualShape`（`:520-535`：`visual.prepare`/`visual.render` 必须为 function）。prepare 返回协议解析 `parseContentRequests`（`:584-642`：数组、`{kind,payload}`、kind 只 text/math）；render 输出校验 `validateScene`（`:645-686`）：字节预算→JSON 可解析→**contentId 引用冻结检查**（`:671-680`，保守面）；`:646` 明写 `完整 SceneV1 严格校验归 4E.2 共享 compiler`。
- **SceneV1/compiler/SDK 落码现状 = 零**：`grep -rln "SceneV1\|VisualSceneCompiler\|visual-sdk" core/{model,data,domain}/src/main app/src/main` 仅命中两处 **KDoc 注释**（`QuickJsVisualExecutionEngine.kt:70/:646`、`VisualExecutionEngine.kt:220`）；core:model 无 `VisualScene`/`VisualSceneValidator`/`VisualSceneCompiler`/`VisualStateCodec` 类型；无 `core:visual-js/assets/visual-sdk-v1.js`（`core:visual-js`/`core:visual` 模块不存在）。引擎侧唯一 schema 常量 `SCENE_SCHEMA_VERSION: Int = 1`（`:793`）。
- **快照**：① JS 快照**持久化结构不存在**——`grep -rn "tutor_visual_program\|tutor_visual_snapshot\|tutor_message_visual" core/database/src/main/kotlin/` → **0 命中**（契约 §8 `:170-172` 的三表合同未落；已落的是批次 17 审计镜像两表 `figure_generation_operation`/`figure_generation_attempt`，`core/database/.../FigureGenerationMigration_68_69.kt:53` 起定义）；② 引擎内唯一 "snapshot" 是 **OOM 诊断 `HeapSnapshot(mallocSize, mallocLimit)`**（`:464-467`，仅在消息丢失哨兵路径、`close()` 之前读取，`:384-393` 快照可信度注释）。
- **程序哈希**：`digest = figureSha256Hex(request.sourceJs.toByteArray(UTF_8))`（`:193`）。
- **熔断**：引擎 `fusedProgramDigests: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap())`（`:168-169`）；命中即拒 `PROGRAM_FUSED`（`:194-199`，`explicitUserRetry` 豁免）；`HARD_DEADLINE_EXCEEDED` 后加入（`:243-247`）。客户端 `VisualJsClientEngine.fusedDigests`（`VisualJsClientEngine.kt:112`；execute 面 `:188`、队列出队复查 `:242` 读 `frame.digest`、加入 `:277/:440/:516/:915`、**显式重试成功后解除 `:296`**）——客户端侧行号本轮以 grep 实测，与准入清单 v3 P-12 行（`:135`）所引锚点一致。

---

## 5. 内容度量现状（计划 §0:6 缺口 + §1 T2 设计输入）

### 5.1 UnwiredVisualContentMeasurer（本轮实读）

- **位置**：`app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsProtocol.kt:398-408`（`internal object`）；异常类 `VisualMeasurementUnavailableException`（同文件 `:410`）。接线点：`VisualJsService.kt:382` `QuickJsVisualExecutionEngine(measurer = UnwiredVisualContentMeasurer)`；KDoc 登记 `VisualJsService.kt:62` `文本/数学内容度量未接线（[UnwiredVisualContentMeasurer]），只有纯几何程序可通`。
- **契约原文（`:387-397`，逐字）**：`服务进程内的内容度量实现（批次 22 T1 保守分支）。…在隔离进程里凭空返回常量会把错误排版伪装成"正确图"（契约 §4"超限不截断成正确图"）。T1 只落进程层骨架，不接 4E.2 的原生字形/公式 lowering：纯几何程序（prepare 返回空批次）可以跑通，任何内容度量请求 fail-closed 成有界拒绝。`
- **行为（`:398-408` 逐字）**：`override suspend fun measure(batch)` —— `if (batch.entries.isEmpty()) { return VisualContentMeasureResult(emptyList()) }`；否则 `throw VisualMeasurementUnavailableException("native text/math measurement is not wired into :visual_js yet (4E.2); ${batch.entries.size} content request(s) were rejected")`。**即：只收空批次；text/math 一律 fail-closed**（引擎侧把该异常折为 `INVALID_PROGRAM`，`QuickJsVisualExecutionEngine.kt:339-348` `content measurement failed: …`）。

### 5.2 4E 计划对"内容度量"的验收原文（`docs/superpowers/plans/2026-10-18-flexible-js-drawing-implementation.md`，331 行；计划全文无"内容度量"四字连用，验收条款落在"度量/measure"关键词）

- `:96`（批次二 §3）：`core:visual 新增 AndroidContentLayout.kt、MathContentLowering.kt、AndroidScenePainter.kt、SceneSvgWriter.kt；字体来自原生白名单，真实 glyph metrics，布局在后台。公式复用 parser/AST，修正明确的上/下标坐标与宽度，未知语法走文字替代。`
- `:97`：`一次 prepare 返回完整批量内容；render 只引用冻结 contentId。内容摘要和字体/排版版本进入缓存；Compose draw 不进行 JS/度量；相同 RenderScene 由 PDF/屏幕同 painter 回放。`
- `:101`：`新增 SceneValidatorTest、…、NativeContentLayoutInstrumentedTest、MathContentLoweringTest；覆盖 clipping、非均匀矩阵、虚线、分数/根号/上下标、中文宽度、大字体，不编写只照抄实现的断言。`
- `:42`（现状表）：`core/model/.../StructuredContent.kt、MathBudget.kt | 旧图形有限；数学含字符宽估算 | 兼容旧协议；复用 AST，新增真实度量与共享 lowering`。
- 批次 27 计划 §1 R2 的设计口径：`R2 无 UI 线程内容度量设计（StaticLayout/Paint.measureText 在 :visual_js 服务进程的可行性与精度）`（`.zcode/plans/plan-2026-10-27-batch-27-4e2-mva-static.md:12`）。

---

## 6. 深冻结现状（计划 §1 T3 设计输入）

- **bootstrap 原文**（`QuickJsVisualExecutionEngine.kt:847-852`，逐字）：

```kotlin
const val BOOTSTRAP_JS: String =
    "globalThis.Date = undefined;\n" +
        "const __ktRandom = __ktRandomNext;\n" +
        "Math.random = function () { return __ktRandom(); };\n" +
        "Object.freeze(Math);\n" +
        "void 0;\n"
```

- **`Object.freeze(Math)` 行号 = `QuickJsVisualExecutionEngine.kt:851`**。全 visual 相关源码 freeze 扫描：`grep -rn "Object.freeze\|freeze(" app/.../visualjs/ core/data/.../visual/ core/domain/.../VisualExecutionEngine.kt` → **唯一命中 :851**。
- **input/metrics 传入路径（均无冻结）**：
  1. 宿主 Kotlin 侧构造 `buildFrameInput(request)`（`:555-569`：`{programId, revision, state?, viewport{widthDu,heightDu}, timeMs, seed, theme}` JSON 字符串；state 原文内嵌 `Json.parseToJsonElement(stateJson)` `:558-561`）；
  2. `metricsJson(entries)`（`:571-582`：`[{contentId,widthDu,heightDu,baselineDu,fontSizeSp,digest}]`）；
  3. 两串以 **JS 字面量内嵌**进 evaluate 表达式（`:448` prepare / `:453` render）——`visual.prepare($inputJson)` / `visual.render($inputJson, $metricsJsonText)`，**没有 `Object.freeze`，也没有 structuredClone/不可变化处理**。
- **结论（T3 缺口实证）**：契约 §3.2 `:58` 要求 `每次调用的 input 与 metrics 深冻结`；现状唯一冻结面是 `Math`（:851）+ `Date = undefined`（:848）+ PRNG 闭包（:849-850）。深冻结 JSON 图在现树**零落码**——批次 27 T3 的落点即 `BOOTSTRAP_JS` 常量或 evaluate 注入点（`:443-456`），两种注入时机（bootstrap 内对全局无对象可冻 → 需在 `visual.prepare/render` 调用点包冻结层）的裁定属实施轨，本文只登记现状。

---

## 7. 准入清单与 KD-76 现状（计划 §0:6 + §1 T4/T5 设计输入）

### 7.1 准入清单 v3 五行现状（`docs/research/2026-10-26-batch-24-4e2-admission-checklist.md`，**v3，69 行**，git 干净未在脏列；行号为冻结时点）

| 行 | 位置 | 判据（逐字开头） | 现状（摘录） |
|---|---|---|---|
| **P-07** | `:130` | `warm-up/ready 门（KD-76）：冷启动首个 execute 不被一次性原生装载误杀；客户端只在 ready 后发首个请求；ready 超时 fail-closed 且有加载态` | `部分落（代码层已落 + ⑤ 首份（部分）设备证据；v3 刷新，本门未过）`；③ `未执行`、④ `弱口径`（负控绿但计时 `withTimeoutOrNull(6_000L)`，无"1500 ms 内"断言）、⑤ `收尾未做`（加载态 UI/文案面与 R3 原文 6/6 门文本，归主会话）。缺口列：`③④⑤ 设备判据…、READY_TIMEOUT=10 s 设备校准、warm-up 实测装载收益、29.5 s worst-case 启动面实测，均 UNVERIFIED` |
| **P-08** | `:131` | `隔离启动面审计（契约 §4:62；启用门 §9:202）…实际进入隔离进程的 ContentProvider/AndroidX Startup 用 dumpsys 审计；隔离进程不申请文件/网络主机绑定` | `部分落`：guard 在 `SmartMistakeBookApplication.onCreate` 最前；`ContentProvider/Startup 仍未 dumpsys（契约 §4:72 自认）；"隔离进程不申请文件/网络主机绑定"无独立证据`。验收列含设备判据 ①`pidof …:visual_js` 非空 ②`dumpsys activity providers \| grep visual_js` → 0 ③进程内断言 ④**负向：隔离进程内 `Socket(...).connect()` 连接失败、`context.filesDir` 写文件失败** ⑤Room 未在隔离进程打开 |
| **P-12** | `:135` | `programHash 熔断与本地重试预算承继（契约 §4:70）…用户显式本地重试可用且预算承继…源码 SHA 变更后熔断解除` | `部分落（v3 勘误…）`：熔断代码自批次 20/21/22 已存在（端口字段/引擎/客户端/设备断言，G-7 ① 设备绿已有）；`G-7 ② 显式重试预算承继无任何证据`（生产无 `explicitUserRetry=true` 调用点）；③ 无直接断言；客户端熔断 JVM 专项未建 |
| **A-11** | `:197` | `Route B 三协议 sourceJs 保真负向套件（契约 §7:151）：AnthropicMessagesProtocol/GeminiGenerateContentProtocol/OpenAiResponsesProtocol 各自 normalize/reconstructedBody 对 sourceJs 的原始换行、反斜杠、中文/emoji、空 content、截断、取消逐协议测试` | `未落`：三协议文件在 `core/data/.../model/wire/`；A-02 只写"native/Route B 两路同果"，无逐协议保真向量。验收=JVM 参数化 21 向量 |
| **R-02** | `:226` | `arm64 运行时加载/执行/终止` | `未测`；验收=arm64 设备；v2 套件集口径同 R-01（隔离 6/6 + KD-70 20/20） |

**与计划 §0:6 的两处标签失配（见 §10.1 #3/#4）**：计划把"隔离进程网络/filesDir 负向"标为 **A-11**——清单 v3 中那是 **P-08 验收列 ④**；计划把"R8 下隔离往返"标为 **R-02**——清单 v3 的 **R-02 = arm64 运行时**，R8 release 冒烟+QuickJS JNI 面是 **R-04**（`:228`）。

### 7.2 KD-76 ③④⑤ 与"装载成本实测"缺口原文（`docs/known-defects.md:2819` 起，open）

- **③④⑤ 定义（`:2829` Fix direction，逐字）**：`广告门前必须把 R3 的 6/6 升级为包含以下设备用例的判据（数量以主会话裁定为准）：③ 冷启动/CPU 竞争下首个 execute 返回 Rendered，且日志无该请求的 watchdog hard deadline；④ 负控——同一 warm 服务上挂死程序仍在 1500 ms 内 SIGKILL（不得因 warm-up 分支放宽）；⑤ 首连失败/ready 超时走明确加载态，不永久 unavailable（与 T1 已登记的 LOADING 缺口一致）。`
- **装载成本/收益实测缺口（`:2842` 验证边界，逐字）**：`③④⑤ 设备判据、READY_TIMEOUT=10 s 的设备校准、warm-up 实测收益全部 UNVERIFIED（设备侧首验结果见下方设备轨块）。`（同义缺口在准入清单 P-07 缺口列写为"warm-up 实测装载收益"——计划 §0:6 表述为"warm-up 实测装载成本缺"，三处同一事实。）
- **设备判据口径（`:2843`，逐字摘录）**：`③（冷启动首请求绿且无该请求 hard-deadline 签名）未达；④（warm 服务挂死负控仍 1500 ms 硬杀）弱口径——负控绿但计时 withTimeoutOrNull(6_000L)…无"1500 ms 内"断言；⑤ 代码层已落，行为层首份（部分）设备证据由下方设备轨块登记（沉降态 + 注入形状）…本条保持 open，设备验证前 JS 不广告`。
- 批次 26 记录头部（`docs/research/2026-10-27-batch-26-record.md:7`）：`KD-76 保持 open（③ 冷启动判据只完成"测试 APK 内生产 client 引擎"形状，产品进程/UI 首请求与 29.5 s worst-case 链 UNVERIFIED、负载轻于批次 23 E-01d；⑤ UI/文案面与 R3 广告门文本升级未做）`。

---

## 8. 队列"取消在飞帧不再 abandon worker"待确认标注位置（计划 §0:6 + §1 R3 落法输入）

主会话裁定（计划 §1:12）：`取消不 abandon 已裁定确认`。落法前需回填的三处"待确认"标注（本轮逐一实读）：

1. **契约**：`docs/current/visual-generation-contract.md:72`（§4 终止层落地状态段内，逐字）：`worker 死亡时排队帧清空回 ENGINE_CLOSED（R1 §3.2 保守分支，待主会话确认）；调用方取消排队帧即出队、取消在飞帧不再 abandon worker（会话按自身预算跑完、结果丢弃；与死亡清队同列待主会话确认，批次 25 记录 §3.3）`——**两处"待主会话确认"并列**（死亡清队 + 取消不 abandon），T4 同步时两处都要处置。
2. **记录**：`docs/research/2026-10-27-batch-25-record.md:121`（§3.3 `:113` 起，逐字）：`两项语义决策待主会话确认：① worker 死亡/abandon 时清空待绘队列为 ENGINE_CLOSED（R1' §3.2 保守推荐，代码已按此落、KDoc/契约双标"待确认"；备选"留队待重建"未采用）；② 调用方取消在飞帧不再 abandon worker（旧实现取消即 terminate 会连坐顶掉全部排队帧；会话按自身 1500/2500 ms 预算跑完、结果丢弃）。`（同文 `:90` T3 行"两项语义决策待主会话确认"、`:239` #10；`:220` 说明该两项目前**未落裁定账** `agent-first-refactor-decisions`——"本批两项 T3 语义决策与 T2 保守分支均待主会话确认，尚未构成已裁定事项"。）
3. **代码**：
   - `app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsFrameQueue.kt:40-42`（KDoc，逐字）：`取消已排队帧 = 出队不执行；取消在飞帧 = 会话照常在自身预算内跑完、结果被丢弃（不 abandon）——旧实现"调用方取消即 terminate worker"的行为随会话所有权移交消费者而废止，否则一个被取消的调用方会连坐顶掉全部排队帧。`；实现 `:112` `调用方取消：排队帧出队（不执行）；在飞帧由会话跑完`；`:120`（死亡/abandon 清队 KDoc）`（R1' §3.2 保守推荐，待主会话确认）`。
   - `app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsClientEngine.kt:66`（KDoc）：`（R1' §3.2 保守分支，待主会话确认）；调用方取消排队帧即出队，取消在飞帧不 abandon`；实现 `:212`：`（不再像旧实现那样 abandon worker——那会连坐顶掉全部排队帧，见 VisualJsFrameQueue KDoc）`。

**落法输入**：裁定确认后，T4 需把上述 1（契约 :72 两处）/2（记录 :121 为历史登记，一般不改写、由新记录收口）/3（`VisualJsFrameQueue.kt:120` 与 `VisualJsClientEngine.kt:66` 两处 KDoc 的"待主会话确认"字样）同步为已裁定；是否回写 `agent-first-refactor-decisions` 裁定账由主会话决定（记录 `:220` 现口径为"尚未构成已裁定事项，不落裁定账"）。

---

## 9. 设备状态（只读查询）

`adb`/`emulator` 不在 PATH，用绝对路径（`/c/Android/Sdk/...`）。

`/c/Android/Sdk/platform-tools/adb.exe devices`（exit=0）：

```text
List of devices attached
emulator-5554	device
emulator-5556	device
emulator-5562	device
emulator-5584	device
```

`/c/Android/Sdk/emulator/emulator.exe -list-avds`（exit=0）：`audit_user_visible_20261016`、`kb_test_device`、`ps16k_arm64`、`ps16k_x86_64`、`test_device`。

| 序列 | AVD 名（`emu avd name`） | PAGE_SIZE | SDK | boot_completed | 判定 |
|---|---|---|---|---|---|
| emulator-5554 | `test_device` | 4096 | 34 | 1 | 4KB 设备 |
| **emulator-5556** | **`ps16k_x86_64`** | **16384** | **36** | **1** | **ps16k 16KB AVD 在线** |
| emulator-5562 | `audit_claude_coverage_20261024` | 4096 | 34 | 1 | 4KB 设备 |
| emulator-5584 | `kb_test_device` | 4096 | 34 | 1 | 4KB 设备 |

`ps16k_arm64` AVD 存在但**未在线**（不在 `adb devices`）——批次 25 R4/记录 `:69` 已定论：本机 x86_64 宿主对 arm64 镜像 FATAL 硬拒载（`Avd's CPU Architecture 'arm64' is not supported…`），**是能力缺失定论，不是耗时问题**；arm64 16KB 运行时证据（R-02/R-03）在本机仍不可得。本轮未启动/停止任何模拟器、未安装/运行任何 APK。

---

## 10. 摘要：与计划 §0 不符处与开工须知

### 10.1 逐条对照（与计划 §0 不符/需限定处）

| 计划 §0（行） | 本轮实测 | 判定 |
|---|---|---|
| `:5` HEAD = `7b2d6905` = origin/main | **HEAD = `caa0726a`**（= origin/main；`7b2d6905` 为 HEAD~1）。差异 = 批次 26 记录补收口 docs 提交（1 文件 +9 行） | **hash 过时**（时点滞后，非内容冲突）；"= origin/main" ✓（本地引用，未 fetch，远端 UNVERIFIED） |
| `:5` "KD-66 已关闭" | 台账 `:2573` = **closed 2026-10-27 批次 26** | ✓ |
| `:6` "KD-70 P2 于 16KB 绿" | 批次 25 E-03 PASS（20/20 + 3/3，`2026-10-27-batch-25-record.md:151`）：**16KB × x86_64 PROVED**（ps16k），`arm64 轴/真机子句/精确 mallocSize UNVERIFIED` | ✓ 但**须限定 ABI**——不得读成"arm64/16KB 已绿"（R-02/R-03 仍未测） |
| `:6` "A-11（隔离进程网络/filesDir 负向）设备证据缺" | 清单 v3 **A-11 = Route B 三协议 sourceJs 保真负向套件**（`:197`，未落）；"隔离进程网络/filesDir 负向"是 **P-08 验收列 ④**（`:131`） | ✗ **标签失配**（缺口本身存在，归属行错） |
| `:6` "R-02（R8 下隔离往返）缺" | 清单 v3 **R-02 = arm64 运行时**（`:226`，未测）；R8 release 冒烟 + QuickJS JNI 面是 **R-04**（`:228`） | ✗ **标签失配**（缺口本身存在，归属行错） |
| `:6` 队列"取消在飞帧不再 abandon worker"待主会话确认（本批裁定确认） | 三处标注定位见 §8（契约 :72、记录 §3.3 :121、FrameQueue/ClientEngine KDoc） | ✓ |
| `:6` 其余缺口（SceneV1 编译器+SDK、真实内容度量、深冻结、painter/PDF 回放、持久化/导出、双后端、质量门） | SceneV1/compiler/SDK **零落码**（§4.5）、Unwired 只收空批次（§5.1）、深冻结仅 `Object.freeze(Math)`（§6）、tutor_visual_* 三表不存在（§4.5）、`AndroidScenePainter`/`core:visual` 不存在（§4.2） | ✓ 与计划一致 |
| `:7` 热区清单 | 六项全部在飞 + settings.gradle.kts M（§2）；M 分组与批次 26 逐一相同 | ✓ |
| `:7` "core/data 两 M" | core/data 实为 **10 M**（main 2 + androidTest 5 + test 3） | **口径歧义**：仅当"两 M"读作 main 源码计数才成立（§2.2） |
| `:8` 本批干净落点 | visualjs/、core/data visual 三目录、VisualExecutionEngine.kt、known-defects.md、visual-generation-contract.md **全部干净**（§3）；**tools/ 有 4 个他线 M** | ✓（落点本身）/ **tools/ 需白名单** |

### 10.2 开工前须知（必须带进批次 27）

1. **落点全干净是基线冻结时点事实**：树 201 M + 472 ??，他线在飞；排提交按路径白名单，禁止模块级 `git add`（app 23 M、core/data 10 M、core/domain 4 M、core/database 8 M、tools 4 M、docs 36 M 均为他线/热区）。
2. **行号锚点（冻结时点，实施前按符号重锚）**：`VisualExecutionEngine.kt` 288 行（`SERVICE_HARD_DEADLINE_MILLIS=1500L` :126）；`QuickJsVisualExecutionEngine.kt` 854 行（BOOTSTRAP_JS :847-852、runPhase :395、validateScene :645、warmUpNativeRuntime :825）；`VisualJsClientEngine.kt` 1,262 行（fusedDigests :112、abandon :811）；`VisualJsFrameQueue.kt` 216 行；`VisualJsService.kt` 646 行（Unwired 接线 :382）；`VisualJsProtocol.kt` 410 行（Unwired :398）；隔离测试 **1,105 行 / 13 @Test**（较批次 26 基线 1,001/12 漂移——批次 26 T1 用例入类）；`known-defects.md` 2,861 行（KD-66 :2573 closed、KD-72 :2746、KD-76 :2819、KD-77 :2853）；契约 220 行。
3. **T3 深冻结现状唯一实证锚**：全 visual 源码 freeze 扫描仅 `QuickJsVisualExecutionEngine.kt:851` 一处 `Object.freeze(Math)`；input/metrics 走 `:448`/`:453` 的 JS 字面量内嵌、无任何冻结——T3 是绿地，契约 §3.2:58 "每次调用的 input 与 metrics 深冻结" 是验收原文。
4. **T1 SceneV1 是绿地**：类型化 DTO/compiler/SDK/`core:visual`/`core:visual-js` 全部不存在；引擎只有保守面校验（:645-686）与 `SCENE_SCHEMA_VERSION=1`（:793）；契约 §5:104 的编排处方（app 的 `AndroidVisualExecutionEngine`）与 §5.1 归位条件（迁 `core:visual-js` 需热区空出）仍是目标态。
5. **T2 内容度量替换对象**：`UnwiredVisualContentMeasurer`（VisualJsProtocol.kt:398-408，只收空批次）；4E 计划验收原文 :96/:97/:101；度量预算类型上就地强制（128 条/6000 字符，VisualExecutionEngine.kt:162-167）。
6. **T4 清单同步的行号**：P-07 :130、P-08 :131、P-12 :135、A-11 :197、R-02 :226（清单 v3 共 69 行）；注意 §10.1 的两处标签失配——按清单行义同步，不按计划 §0:6 的括注。
7. **T4 队列裁定收口面**：契约 :72 两处"待主会话确认"（死亡清队 + 取消不 abandon 并列）+ 两处 KDoc（FrameQueue :120、ClientEngine :66）；裁定账（`agent-first-refactor-decisions`）现未落（记录 :220）。
8. **DB v69 / 请求 schema 18 / wire v2 未变**；本批不得回退 wire v2。
9. **设备**：ps16k_x86_64 在线（emulator-5556，16KB×API 36）；ps16k_arm64 AVD 存在但本机**原理性不可启动**（批次 25 R4 定论）——arm64/16KB 轴（R-02/R-03）在本机不可采证，设备轨设计时勿再排该轴。

**UNVERIFIED / 本轮未做**：未跑任何 Gradle 任务；未跑 JVM/androidTest/设备用例（隔离 13+2、KD-76 ③④⑤、KD-70、R8 均未复跑，设备数字一律转录批次 25/26 记录与 E-file 文本，未重读原始日志）；未 `git fetch`；未重跑契约 §5.1 现状表的逐项 grep（仅实证落位文件与 settings.gradle.kts）；`VisualJsClientEngine.fusedDigests` 客户端锚点为 grep 实测、未逐路径读码复核 P-12 全部断言；`RoomFigureDispatchLedger` 表名/DAO 细节未展开（批次 14 结构，本批不触碰）。**本轮实际执行**：`git` 只读查询/计数、`grep`/`sed`/`wc`/`ls` 文件读取、四个源文件全文实读（VisualExecutionEngine.kt 288 行、QuickJsVisualExecutionEngine.kt 854 行、visual-generation-contract.md 220 行、Unwired 定义段）、`adb devices`/`emu avd name`/`getconf PAGE_SIZE`/`getprop` 只读查询、`emulator -list-avds`。

---

## 附录 A：本轮实际执行的只读命令（可复跑）

```bash
git rev-parse HEAD; git rev-parse origin/main; git log -1 --format='%h %s'
git show --stat --oneline caa0726a; git show --stat --oneline 7b2d6905
git status --porcelain | wc -l; git status --porcelain | grep -c '^ M'; git status --porcelain | grep -c '^??'
git diff --cached --name-only | wc -l
git status --porcelain | grep '^ M' | sed 's|^ M ||' > /tmp/mfiles.txt   # 分组计数见 §1.1
git status --porcelain core/domain core/data core/database core/ui core/markdown core/export core/model
git status --porcelain -- '*visual*' '*Visual*'
git show HEAD:settings.gradle.kts | grep markdown    # exit=1
git ls-files docs/research/2026-10-27-batch-26-baseline.md   # 已随 7b2d6905 入库
grep -c "HOT_ZONES" .zcode/plans/plan-2026-10-27-batch-27-4e2-mva-static.md   # 0（exit=1）
grep -n STUDY_DATABASE_VERSION core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabase.kt   # :134 = 69
ls core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/ | wc -l   # 69
grep -n 'TUTOR_VISUAL_CONTEXT_SCHEMA_VERSION\|CURRENT_SCHEMA_VERSION' core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/ModelTasks.kt   # :546-547 = 18
grep -n PROTOCOL_VERSION app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsProtocol.kt   # :35 = 2
git status --porcelain core/data/src/{main,test,androidTest}/kotlin/com/tingyun/smartmistakebook/core/data/visual/   # 空
git status --porcelain app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/ app/src/main/aidl/com/tingyun/smartmistakebook/visualjs/ app/src/androidTest/kotlin/com/tingyun/smartmistakebook/visualjs/   # 空
git status --porcelain core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/VisualExecutionEngine.kt docs/known-defects.md docs/current/visual-generation-contract.md   # 空
wc -l <上述全部文件>
grep -n "^## KD-66\|^## KD-72\|^## KD-76\|^## KD-77" docs/known-defects.md   # :2573/:2746/:2819/:2853
grep -c "@Test" app/src/androidTest/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsIsolationInstrumentedTest.kt   # 13
grep -rn --include="*.kt" "SceneV1\|VisualSceneCompiler\|visual-sdk" core/{model,data,domain}/src/main/kotlin/ app/src/main/kotlin/   # 仅 KDoc 命中
grep -rn --include="*.kt" "tutor_visual_program\|tutor_visual_snapshot\|tutor_message_visual" core/database/src/main/kotlin/   # 0
grep -rn "Object.freeze\|freeze(" app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/ core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/ core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/VisualExecutionEngine.kt   # 仅 :851
grep -rn "UnwiredVisualContentMeasurer" --include="*.kt" core/ app/
grep -n "度量\|measure\|Measure" docs/superpowers/plans/2026-10-18-flexible-js-drawing-implementation.md
grep -n "P-07\|P-12\|P-08\|A-11\|R-02\|^| R-0" docs/research/2026-10-26-batch-24-4e2-admission-checklist.md
grep -n "③ 冷启动\|装载成本\|warm-up 实测收益全部 UNVERIFIED\|待主会话确认" docs/known-defects.md docs/current/visual-generation-contract.md
grep -n "取消在飞帧\|不再 abandon\|待主会话确认" docs/research/2026-10-27-batch-25-record.md
grep -n "abandon\|取消" app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsFrameQueue.kt app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsClientEngine.kt
grep -n "fusedDigests" app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsClientEngine.kt
grep -rn "ps16k" docs/research/2026-10-27-batch-25-record.md docs/research/2026-10-27-batch-26-record.md docs/research/evidence/2026-10-27-batch-26/00-index.md
"/c/Android/Sdk/platform-tools/adb.exe" devices
"/c/Android/Sdk/emulator/emulator.exe" -list-avds
for s in emulator-5554 emulator-5556 emulator-5562 emulator-5584; do adb -s $s emu avd name; adb -s $s shell getconf PAGE_SIZE; adb -s $s shell getprop ro.build.version.sdk; adb -s $s shell getprop sys.boot_completed; done
```
