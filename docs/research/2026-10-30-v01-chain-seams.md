# 批次 36 研究：V-01 全链缺 seam 与最小仪器化设计（T3/T4 驱动）

> 研究人：R2 V01研究（子代理，只读研究；零代码改动、零 Gradle、零设备写）。
> 方法：全部结论来自本会话实读文件与实跑命令（`git status`、`gh api`、WebSearch ×1），命令与关键输出随条记录。
> 对照：`.zcode/plans/plan-2026-10-30-batch-36-v01-r04-k6.md`（下称计划）任务 3/4/5；
> `docs/research/2026-10-30-batch-36-baseline.md`（下称基线）§四/§五；
> 准入清单 `docs/research/2026-10-26-batch-24-4e2-admission-checklist.md:198-200`（A-10/A-11/A-12 原文）与 `:135`（P-12）。
> 本文件是**设计建议**，不是实现裁定；落码归 T3/T4 实现轨，逐条"新增指认消灭的失败"已随条给出。

## 0. 一句话结论

V-01 全链的组件全部在树，缺的不是组件而是**生产装配**：`visualEngine/visualFrameInputs` 生产恒 null（`SmartMistakeBookApplication.kt:363-369` 三参调用）、屏/PDF 两个 painter 零生产调用点（仅 androidTest 引用）、`VisualSnapshotRestore` 无生产构造方——因此 V-01 设备用例走**测试内装配**（工厂四参/五参注入是既有公共缝，`FigureGenerationCoordinationFactory.create:810-827`）即可闭环，不需要任何生产装配改动；"0 Provider 派发"有 4 个独立断言位（审计 0/0、attempt `reservation_id IS NULL`、账本零触碰、凭证门恒 null）；A-10 的 10 个阈值中 5 个在树、5 个（128 KiB 累计源码/≤6 布局尝试/10 s 墙钟/两份最终图/文字降级）**常量不在树**，边界用例须先补常量（先红后绿）；P-12③ 经机制再评估**可确定性构造**，建议**状态注入 seam（方案 B）**而非时钟注入，理由见 §三。

---

## 一、任务一：V-01 全链组件与缺 seam + 最小仪器化用例设计

### 1.1 组件清单（全部本会话实读；路径为现树实证）

| 环节 | 组件 | 本轮实读落点 | 生产态 |
|---|---|---|---|
| tool proposal | `TutorToolLoop.toFigureGenerationProposal`（JS 分支：非空 program ≤64 KiB 且不带图像载荷） | `core/model/.../TutorToolLoop.kt:428`、校验 `:344-401`（`MAX_PROGRAM_UTF8_BYTES` 引 `FigureGenerationPolicy.kt:33`） | 纯 JVM，可用 |
| 协调器 | `DefaultFigureGenerationCoordinator`；JS 分支 `generateLocalJs` **不调 `ledger.reserve`** | `core/data/.../visual/DefaultFigureGenerationCoordinator.kt:135-158`（构造缝）、`:250-375`（JS 路径）、`:240-249`（不预留 KDoc） | 主类可用；JS 引擎生产未注 |
| 装配口 | `FigureGenerationCoordinationFactory.create`（第 4/5 参 `visualEngine`/`visualFrameInputs` 默认 null） | 同文件 `:805-827` | 生产调用点唯一且只传三参（`SmartMistakeBookApplication.kt:363-369`，实读）→ JS 恒 UNAVAILABLE |
| 隔离引擎 | `VisualJsClientEngine`（AIDL + 队列/熔断/重建）+ `VisualJsService`（`:visual_js` 进程，watchdog 1500 ms） | `app/.../visualjs/VisualJsClientEngine.kt`（1263 行全读）、`VisualJsService.kt:318-471`/`:518-534` | 组件可用；14 例设备套件现绿（`VisualJsIsolationInstrumentedTest`，`grep -c @Test` = 14） |
| in-process 引擎 | `QuickJsVisualExecutionEngine` | `core/data/.../visual/QuickJsVisualExecutionEngine.kt`；JVM 套件钉住"本地单测不执行任何 QuickJs 路径"（`QuickJsVisualExecutionEngineJvmTest.kt:15-25`：桌面 JVM 无 `libquickjs.so`） | 真执行仅设备 |
| 度量 | `AndroidVisualContentMeasurer`（服务侧，warm-up 构造） | `VisualJsService.kt:141-158`/`:405-414`；端口 seam `VisualExecutionEngine.kt:162-164` | 服务内自装配，测试无需注 |
| compiler | `visual-compiler.js` + `visual-sdk.js` | `core/data/src/main/resources/visual/`（ls 实证，沿基线） | 可用 |
| painter（屏） | `VisualDrawOpsPainter(.draw)`；解析 `VisualDrawOpsParser.parse` | `VisualDrawOpsPainterRendered.kt:32`（parse 入口）；**生产调用点 0**（grep 全仓：仅 `core/data/src/androidTest` 引用） | 未接线 |
| painter（PDF） | `VisualDrawOpsPdfPainter(.write/.writeFile)` + `PdfWriteIntegrity` | `grep -rn VisualDrawOpsPdfPainter app core feature` → 仅 androidTest 两文件命中（`VisualDrawOpsPdfPainterInstrumentedTest.kt:80/:452/:482`、`VisualDrawOpsParityInstrumentedTest.kt:638`） | 未接线（基线 §五"接线点 UNVERIFIED"→本轮判定为**确认未接线**） |
| 保存/持久化 | `TutorVisualCommitRepository`（`stageSnapshot`/`commitAssistantTurnWithVisual`/`repointMessageVisual`）；`TutorVisualPort` 读侧已有 `readTutorVisualSnapshot`/`readTutorMessageVisual` | `TutorVisualCommitRepository.kt:253/:298/:342`；`core/database/.../port/TutorVisualPort.kt:85/:90` | 仓储可用；设备套件 `TutorVisualCommitRepositoryInstrumentedTest` 12 例（`:26` KDoc 自述） |
| 重启恢复 | `VisualSnapshotRestore.restore`（internal，校验 schema/renderer/font manifest 四门 + `VisualDrawOpsParser.parse`） | `VisualSnapshotRestore.kt:181-240`（全读） | **无生产构造方**：`VisualSnapshotRestoreRecord(` 全仓唯一构造点在测试（`VisualSnapshotRestoreTest.kt:65`，grep 实证） |
| PDF 导出（app 侧） | `ExportPdfWorker`（CoroutineWorker） | `app/.../ExportPdfWorker.kt:89-96`；`grep VisualDrawOpsPdfPainter` 0 命中 | 导出链对 painter **未接线** |
| UI 消费 | `VisualSceneCard` | `grep -rln VisualSceneCard core feature app` = 空（本轮复跑，与基线 §五.4 一致） | S-07 未落 |

### 1.2 缺 seam 清单（逐条指认，全部本轮实证）

1. **生产引擎装配为 null**：`SmartMistakeBookApplication.kt:363-369` 只传 `database/configurationStore/networkRequestsAllowed` 三参（实读）；`create` 的 4/5 参默认 null（`DefaultFigureGenerationCoordinator.kt:814-815`）→ JS 恒 UNAVAILABLE。
2. **屏/PDF painter 无生产调用点**（§1.1 表）→ painter 环节的仪器化只能在测试内调用（与计划任务 5"测试内最小渲染替代"一致）。
3. **`VisualSnapshotRestore` 无生产读链**：记录构造只在测试 → 重启腿的行读取经 `TutorVisualPort.readTutorMessageVisual`/`readTutorVisualSnapshot`（`:85/:90`）+ 测试内映射为 `VisualSnapshotRestoreRecord`。
4. **UI 卡片段缺失**（`VisualSceneCard` 0 命中）→ 按计划任务 5 原文：测试内最小渲染替代并**标注降级**。
5. P-11 errorKind 输出通道半边（沿基线 §六，与本文件无新增结论）。

### 1.3 最小仪器化用例设计（建议 5 段链、4 个设备用例，全部测试内装配）

装配原则：**不接生产装配点**。用例内 `FigureGenerationCoordinationFactory.create(database, configurationStore, networkRequestsAllowed = false, visualEngine = VisualJsClientEngine(context), visualFrameInputs = { testFrame })`——4/5 参注入是工厂既有的公共缝（`:810-827`），JVM 侧已有同形先例（`FigureGenerationCoordinatorTest.kt:245/:276-277/:329-330/:360-361` 实读）。`networkRequestsAllowed=false` 同时是"0 派发"的第 4 道保险（凭证门 `openGuardedFigureImageBackend:74-78` 无凭证恒 null）。DB 用设备既有惯例 `StudyDatabaseFactory.open(context, 随机名)`（`TutorVisualCommitRepositoryInstrumentedTest.kt:785-797` 实读）。

**用例 A（V01 生成腿 + 0 Provider 派发观测点）**——真实 `VisualJsClientEngine` → `:visual_js` 真进程真 QuickJS：

1. 输入：经 `TutorToolLoop` 工具调用形状构造 JS proposal（`toFigureGenerationProposal:428`），program 取一个纯几何可渲染源（沿用隔离套件 `pureGeometryRequest` 同族，≤64 KiB）；
2. `coordinator.generate(request, proposal)` → 断言 `GENERATED` + `FigureArtifact.Program`；
3. **0 派发观测点（四个独立断言位，任一为真即可单独证伪）**：
   - ① 审计 operation 行：`figure_generation_operation` 对该 `operationId` 落 `reserved_remote_dispatches=0 AND used_remote_dispatches=0`（写入点 `finishOperation:682-694`；JS 路径 `beginLocalAttempt` 保持 reserved=0，`:602-624` KDoc 自述"0 远程派发的正向证据"）；
   - ② 审计 attempt 行：`figure_generation_attempt.method='JS' AND reservation_id IS NULL`（本地写径唯一形状，对账豁免依赖它，`:636-639` KDoc）；
   - ③ 账本零触碰：JS 路径不调 `ledger.reserve`（`:240-249` KDoc）→ 断言父操作行 `dispatch_count` 写前写后不变（`RoomFigureDispatchLedger` 消费 `model_task_operation.dispatch_count`，`RoomFigureDispatchLedger.kt:73` 实读）；
   - ④ 无 `ImageAsset` 产物：`artifact is FigureArtifact.Program`（`generateLocalJs:327-339` 唯一成功形态）；
   - MockWebServer 载体先例（JVM 侧）：`FigureHttpTransportBypassObservationTest.kt`（实读头）——设备侧以 ①-④ 为准，不引网络桩。
4. 降级登记：本用例不覆盖"生产装配接线"，断言对象是**协调器注入缝 + 隔离引擎真进程**；生产装配（Application 四参）归后续批次。

**用例 B（屏渲染腿）**：取用例 A 的 `Rendered.drawCommandsJson`（注意：coordinator 的 `FigureArtifact.Program` **不携带** drawOps——`generateLocalJs:333-337` 只回 sourceJs；DrawOps 从 `VisualExecutionResult.Rendered.drawCommandsJson` 直接取，即用例内同时持有引擎返回值，或对同 program 再 `engine.execute` 一次取 Rendered——同 digest 重发非熔断态，合法）→ `VisualDrawOpsParser.parse(drawCommandsJson, contentIds)` → `VisualDrawOpsPainter().draw(document, Canvas(bitmap), pxPerDu, resolver)` → 断言位图像素非全空白（复用 `VisualDrawOpsPainterInstrumentedTest` 约定，`:66` 实读）。

**用例 C（保存/重启构造）**：
1. 保存：`TutorVisualCommitRepository.commitAssistantTurnWithVisual(message, program, snapshot, ownerRequestId)`（`:298-341`）；`TutorVisualSnapshotInput` 的 `sceneJson/drawCommandsJson/contentIds/schema 版本` 全部取自 `Rendered`，`viewport/theme/seed/timeMs` 取自帧快照，`rendererVersion`/`fontManifestVersion` 填 `VisualRendererVersion` 常量（`TutorVisualSnapshotInput:80-131` 字段实读）；
2. 重启：`store.close()` → **同名** `StudyDatabaseFactory.open(context, 同名)` 重建端口与仓储（真实进程内重启构造，沿用 `:785-797` 惯例）→ `readTutorMessageVisual(messageId, ordinal)` → `readTutorVisualSnapshot(snapshotId)` → 测试内映射 `VisualSnapshotRestoreRecord` → `VisualSnapshotRestore.restore(record, previewVerified = true)`（`:193-240`）→ 断言 `Replay`（非 `ReadOnly` 回退）；
3. 重绘一致性：恢复出的 `document` 重走用例 B 的 painter → 与重启前栅格逐位比较（`Bitmap.sameAs`）。

**用例 D（PDF 断言）**：`VisualDrawOpsPdfPainter().writeFile(document, target, ptPerDu, resolver)` → 双断言：
1. `PdfWriteIntegrity` 文件级不变量（D1-D3，`PdfWriteIntegrity.kt:12-42` 实读头）；
2. `PdfRenderer` 回读：页数 = 1、页尺寸与画布 du×ptPerDu 一致、栅格非空白、与用例 B 屏渲染同源比对（**仓内既有约定**：`VisualDrawOpsPdfPainterInstrumentedTest.kt:8/:36/:44/:95/:106` 与 parity 套件 `VisualDrawOpsParityInstrumentedTest.kt:57-58/:624/:638` 已以"同文档两次独立构建 → PdfRenderer 回读逐位相等"为确定性口径——优先复用仓内约定，不引第三方 PDF 库，见 §四）。

**降级登记（计划任务 5 原文允许）**：UI 卡片段以用例 B 的测试内最小渲染替代；`VisualSceneCard` 消费面归 S-07，不入本批判据。

**R-04 关联门（实现轨必须先答）**：新增用例若进 release 轨（R-04 K1-K6 口径），测试 dex 按 name 引用的 app/core 类 internal 成员在 R8 后会错位——现 `app/r04-test-keep.pro`（41 行全读）只有测试类构造器/@Test 注解/androidx.test·junit 三组 keep，**没有任何**既有 test seam（`connectForTest` 等）的成员 keep。要么确认隔离套件不进 release 轨套件单，要么为新增 seam 成员逐条补 keep 并给"为什么需要"（R-04 纪律）。此项 UNVERIFIED：K1-K6 的套件单未在树内核实。

### 1.4 改动面与热区

设计落码触点（建议）：新增 `app/src/androidTest/.../visualjs/`（或 `core/data/src/androidTest/.../visual/`）一个套件文件 + 可能的 `VisualJsClientEngine` 测试 seam（§三方案 B，1 个 internal fun）。本会话 `git status --porcelain` 实证：`app/src/main/.../visualjs/`、`SmartMistakeBookApplication.kt`、`app/src/androidTest/.../visualjs/`、`core/data/src/main/.../visual/`、`core/data/src/main/.../model/wire/` **全部干净**；`core/data/src/test` 有 6 个他线 M 文件（`GoldenRetrievalJvmTest`、`DenseTestFixtures`、`OpenAiNativeToolsProtocolTest`、`TutorFigureProposalExecutionTest`、`FigureGenerationAuditRecorderTest`、`VisualDrawOpsParserTest`）——**模块级 core/data 他线在飞**：任何新增/改动按纪律整文件纳入并注明，不触碰上述 6 文件。

---

## 二、任务二：A-10/A-11/A-12 逐阈值边界清单落位

### 2.1 常量行号总表（A-10 行义 10 项；全部本会话实读）

| # | 阈值（清单 `:198` 行义） | 现树落点 | 状态 |
|---|---|---|---|
| 1 | 单操作 ≤6 次 Provider 派发 | `core/model/.../ModelTasks.kt:61` `MAX_DISPATCHES: Int = 6`（实读）；消费点 `core/data/.../visual/RoomFigureDispatchLedger.kt:73` `usableLimit = MAX_DISPATCHES - 保留终答位`（实读） | ✅ 在树 |
| 2 | 累计接收源码（含失败尝试）≤128 KiB | grep 无命中（沿基线 §4.2；本轮未复跑同词 grep——基线当日实跑在案） | ❌ 缺常量 |
| 3 | 新源码布局尝试 ≤6 | 同上 | ❌ 缺常量 |
| 4 | 累计 JS 阶段墙钟 ≤10 s | 同上（引擎单事务期限是 `VisualExecutionEngine.kt:126` `SERVICE_HARD_DEADLINE_MILLIS=1500L` 与 `:129` 宿主 2500 ms，**不同口径**） | ❌ 缺常量 |
| 5 | 单份工具 payload 解码后 JSON ≤512 KiB | `core/data/.../model/OpenAiSse.kt:563` `MAX_TOOL_CALL_PAYLOAD_UTF8_BYTES = 512 * 1024`；伴生 `:566` `MAX_DECODED_TOOL_SOURCE_UTF8_BYTES = 64 * 1024`（单份 sourceJs） | ✅ 在树 |
| 6 | operation/attempt ledger 换 method 不重置 | `core/domain/.../RemoteModelDispatchLedger.kt:19` KDoc「operationId 不含 method：换方法/失败重试仍是同一操作身份，不复制新预算」（实读） | ✅ 语义在树（KDoc+实现） |
| 7 | `FigureGenerationTrace` 不重置 | grep 全仓 main 代码 0 命中（本轮实跑 `grep -rn FigureGenerationTrace --include=*.kt core` 排除 test = 空）——**契约概念未实现** | ❌ 未落 |
| 8 | 单逻辑操作最多两份最终图 | 未找到 | ❌ 缺常量 |
| 9 | 任一预算耗尽 → 文字降级 + 原派发余额终答 | 终答保留位已落（`reserveChatFinalAnswer`，`RoomFigureDispatchLedger.kt:73-77` 诊断文本与 `ModelTasks` 政策配套）；"降级"编排未找到 | ⚠️ 半落 |
| 10 | 单份 program ≤64 KiB（相关既有） | `FigureGenerationPolicy.kt:33` `MAX_PROGRAM_UTF8_BYTES=64*1_024`；`VisualExecutionEngine.kt:108` `MAX_SOURCE_JS_BYTES=64*1024` | ✅ 在树（注意与 #2"累计 128 KiB"不同口径） |

### 2.2 缺口常量的落位建议（每条指认消灭的失败；标注"设计建议"）

| 缺口 | 建议落位 | 消灭的具体失败 |
|---|---|---|
| 累计源码 ≤128 KiB（含失败尝试） | JS 工具环的 per-operation 累计器：语义归属 tool round 侧（收 `sourceJs` 的解析点已按单份 64 KiB 在 `OpenAiSse.kt:566` 拒），累计量与 operationId 绑定落审计/trace；常量建议放 `core/model`（与 `MAX_DISPATCHES` 同文件同风格，`ModelTasks.kt:57-67` 政策对象） | 失败尝试可无限重试，每次 ≤64 KiB 但总量无界——账本侧"≤6 次派发"挡不住 6×64 KiB |
| 新布局尝试 ≤6 | 同上累计器（"布局尝试"即失败后的重试分支计数） | 同上：无限重试面 |
| 累计 JS 墙钟 ≤10 s | 宿主侧 per-operation 累计：唯一能测"每次执行耗时"的位置是协调器 JS 分支（`engine.execute` 调用点 `DefaultFigureGenerationCoordinator.kt:294-311`）或 tool round 编排；常量建议与 `VisualExecutionBudget` 并列或 `ModelTasks` 政策对象 | 引擎只有单事务 1500 ms 期限，无跨尝试累计——挂死-重建循环可无限烧墙钟 |
| 两份最终图 | 最终图提交点：`TutorVisualCommitRepository`（`commitAssistantTurnWithVisual:298`/`repointMessageVisual:342` 是仅有的两条"图生效"路径）；常量建议 core/model 政策对象 | 同一逻辑操作可反复 repoint 出第 3 份最终图，无门 |
| 文字降级 | tool round 编排（runner 协议层；`RoomTutorToolRunner.kt` git 干净——基线 §二实跑在案，热区认领归主会话裁定） | 预算耗尽后无兜底形态，失败直接裸露 |

以上 5 条都属**实现先落、测试随后**（先红后绿）：测试先写边界向量（2.3），在常量落树前编译不过或断言红，是预期而非放宽理由。

### 2.3 JVM 参数化设计（计划任务 3 的 7 向量 → 载体矩阵）

| 向量 | 载体（JVM，core/data/core/model test） | 设计 |
|---|---|---|
| 第 7 次派发拒 | 账本既有测试载体族（清单 `:198` 原文："若既有 ledger 用例已覆盖则引用用例名，不重复建"）：`FigureGenerationBudgetDeclarationTest`（KD-60 预算声明，`:1-30` 实读头）、`ConfiguredTutorFigureGeneratorTest`、`ConfiguredCleanImageGeneratorTest` | 对同一 `operationId` 连续 `ledger.reserve` 7 次：第 7 次 `Denied(BUDGET_EXHAUSTED)` 且诊断含真实计数（KD-60 可读诊断）。**既有用例是否已含第 7 次向量 UNVERIFIED**（未逐用例读断言）——实现轨先 grep 引用名，缺则补 |
| 128 KiB+1B 拒 | §2.2 累计器（未落） | `@Test` 断言第 128 KiB+1 字节被拒、恰 128 KiB 通过；负向 + 边界通过双例 |
| JS 墙钟 >10s 拒 | §2.2 累计器（未落） | 用注入 `now`/耗时 fake 引擎（`VisualExecutionEngine` fake 先例：`VisualExecutionEngineContractTest`，清单 `:135` 转引）累计跨 3 次执行超 10 s → 拒 |
| 513 KiB payload 拒 | `OpenAiSse.kt:563` 常量（在树） | wire 解码层单测：构造 513 KiB 工具参数 JSON → 解码拒绝有界诊断；512 KiB 恰好通过 |
| 第 3 份最终图拒 | §2.2 提交点（未落） | 同 operationId 两份成功后第三份 `commitAssistantTurnWithVisual`/repoint 拒（有码） |
| 换 method 计数不重置 | `RoomFigureDispatchLedger` + 真库/in-memory（既有套件惯例） | 同 `operationId` 先 IMAGE_MODEL 派发 n 次再 JS→IMAGE_MODEL 交替：`dispatch_count` 单调累计不归零（端口语义 `RemoteModelDispatchLedger.kt:19`）；审计 operation 行 `updated_at` 单行演进 |
| 耗尽→文字降级 | runner/tool round（未落，热区待裁定） | 预算耗尽后请求终答走 `reserveChatFinalAnswer` 余额，返回文字形态而非新任务 |

参数化形态：仓内 JVM 测试为 JUnit4（`org.junit.Test`），参数化建议用表驱动循环（每向量一个 `@Test` 或一组 `(阈值, 输入, 期望)` 表循环断言），与 `VisualJsResultFrameFilter` 纯函数负向套件的仓内风格一致（`VisualJsClientEngine.kt:1251-1263` 实读）；不引 JUnit5/第三方参数化引擎（零新增依赖）。

### 2.4 A-11：三协议 × 7 向量 sourceJs 保真参数化套件

- API 面（实读 `ModelWireProtocol.kt` 全文）：`requestBody(modelId, input, images, stream, enableNativeTools)` 出请求体、`parseCompletion` 收响应、`reconstructedBody(rawSse)` 重建终态体；三实现 `AnthropicMessagesProtocol`/`GeminiGenerateContentProtocol`/`OpenAiResponsesProtocol` 均 `supportsNativeTools = false`（`:45/:44/:41` 实读）——Route B 恒定，sourceJs 只在 prompt 信封文本里走。
- 7 类向量（清单 `:199` 原文口径）：原始换行 / 反斜杠 / 中文 / emoji / 空 content / 截断 / 取消。
- 断言口径（清单原文）：前 5 类 sourceJs **UTF-8 逐字节相等**（构造含 `program` 的 `ModelTaskInput` → `requestBody` → 从请求体文本提取信封内 sourceJs 字段 → 与原串 `toByteArray(UTF8)` 逐位比较；反方向 `parseCompletion`/`reconstructedBody` 同断言）；截断/取消两向量显式拒绝、**绝不进入执行**。
- 形态：3 协议 × 7 向量 = 21 例，建议单表（`protocol × vector`）驱动，落 `core/data/src/test/.../model/wire/`（该目录现 git 干净；既有四协议测试同目录，风格可循）。**21 向量全绿才可声称 Provider 已支持**（清单原文）。

### 2.5 A-12：golden 扩测形态

- 现状（实读）：`ModelTaskFingerprintStabilityTest`（42 `@Test`，`grep -c` 实跑 = 42）是 **inline fixture 对**风格（v5/v6 输入对 + 指纹相等断言，`:53-76` 实读）；`ModelTaskCodec` 的 JSON 配置 `encodeDefaults=true, explicitNulls=true`（`ModelTasks.kt:735-742`）；逻辑操作投影 `logicalOperationJson` + `withoutEmpty*` 精确投影链（`:832-842` 实读）。**无 golden 资源文件**（core/model test resources 不存在；`core/data/src/test/resources` 下只有 dense/ 参考文件——属他线在飞面，勿引）。
- 扩测形态建议（不新增 golden 文件基建，沿 inline 风格）：
  1. **新键缺席 golden**：`TutorVisualContext` 类新可空上下文字段为 null 时，`encodeRequest`/`logicalOperationJson` 输出**逐字节**等于不含该键的 golden 字符串（inline 期望串，`assertEquals`）——钉住"新字段 null 不出现新键"；
  2. **旧 payload 往返**：v 现行 schema 的真实旧请求 JSON 串（inline）decode → 语义等价断言 + re-encode 逐字节 = 原串；
  3. **指纹稳定**：同一逻辑输入加 null 上下文前后 `ModelTaskLogicalOperationFingerprint.of` 相等；非 null 真值 → 指纹改变（防"加了字段指纹不感知"的假稳定）；
  4. 既有 42 例不得动；执行命令按清单 `:200` 原文：`./gradlew :core:model:test --tests "*ModelTaskFingerprintStability*"` + 相关 `:core:data:test`（由门禁脚本串行执行，本轨未跑）。

---

## 三、任务三：P-12③ 可构造性再评估（二选一建议）

### 3.1 机制解剖（全部实读 `VisualJsClientEngine.kt`）

熔断集合 `fusedDigests`（`:113-114`，private，ConcurrentHashMap 背书）。**4 个写入点**（全部真实故障路径）：宿主阶段超时 `:273-285`（`TimeoutCancellationException` 且结果未到）、结果层硬期限 `:437-443`、worker `onFatal` `:517`、worker 死亡 `:916`。**1 个解除点** `:296-298`：`frame.explicitUserRetry && outcome is Rendered` → `fusedDigests.remove(frame.digest)`——**解除的恒等物是"被重试程序自身的 digest"**，重试必须渲染成功才解除。检查位两处：入队前 `:188-194`、出队二次 `:243-250`。

批次 35 难构造判定（清单 `:135` 批次 35 增量原文，实读）："客户端熔断只在死亡/硬期限路径置位，重试挂死程序只会再挂"——即**触发熔断的程序必然是挂死/崩溃程序，而解除要求同一程序渲染成功**，两条件矛盾。绕过预检半边已由既有设备用例钉住：`VisualJsIsolationInstrumentedTest.kt:367`（`PROGRAM_FUSED` 基线，本轮实读核实行号）、`:372/:376/:387`（`explicitUserRetry = true` 三调用点）。

### 3.2 方案 A：时钟/预算注入

机制：给宿主阶段期限加测试覆盖位（先例就在同文件：`readyTimeoutOverrideForTest: Long?`，`:1182-1183`，KDoc 自述消灭"ready 超时分支无法确定性触达"）；`executeFrame:270` 的 `withTimeout(VisualExecutionBudget.HOST_PHASE_DEADLINE_MILLIS)` 改读覆盖位（1 行热路径改动）。

构造序列：① 覆盖位=300 ms，执行"烧时 ~800 ms 后渲染"的标定程序 → 宿主超时 → `:278` 熔断 + abandon（进程终止）；② 同程序无重试 → `PROGRAM_FUSED`（检查位真路径）；③ 撤覆盖位，同程序 `explicitUserRetry=true` → 重建进程（真进程 warm-up）→ 程序 800 ms 内渲染 → `:296-298` 解除；④ 同程序无重试 → Rendered（解除持久）。

**不推荐**的量化理由：
- 标定窗口只有 ~2 倍余量：烧时程序必须可靠 >300 ms（覆盖位）且 <1500 ms（服务 watchdog `VisualJsService.kt:530`，不可覆盖）且 <2500 ms（生产宿主期限）。JS 沙箱内无时钟（`VisualExecutionEngine.kt:66` 契约原文"引擎不自带时钟"），只能按迭代次数标定——共享 x86_64 模拟器负载波动下，>1500 ms 就触发服务 watchdog 二次熔断，用例脆且**慢**（每次熔断→重建都要走 10 s 重连 + 10 s ready 预算，`:1213/:1231`）；
- 它同时消耗重建预算并杀死进程 2 次，把 P-12③ 断言耦合在死亡/重建时序上——断言目标（解除位）与构造手段（进程死亡）无关，耦合只添噪声；
- 批次 35 "重试挂死只会再挂"的判断在**固定预算**下依然成立，方案 A 只是用"预算可变"绕开矛盾——绕开的正是要测的稳态。

### 3.3 方案 B：状态注入 seam（建议采纳）

机制：`VisualJsClientEngine` 增 1 个 internal 测试函数（约 3 行 + KDoc）：

```kotlin
/** 测试 seam：预置一条熔断记录。它消灭的具体失败：P-12③"成功显式重试解除熔断"
 *  （:296-298）在真实触发路径下不可确定性构造（触发程序必然挂死，重试同一程序只会
 *  再挂——清单 :135 批次 35 判定）；预置 digest 让解除位与检查位在无时序依赖下直测。 */
internal fun fuseDigestForTest(digest: String) { fusedDigests.add(digest) }
```

构造序列（全确定性、零进程死亡、零标定）：
1. 取可渲染快程序 P，`digest = figureSha256Hex(P)`（与 `:188` 同一派生）；
2. `engine.fuseDigestForTest(digest)` → 执行 P 无重试 → 断言 `PROGRAM_FUSED`（**检查位 `:189` 对注入态的真路径响应**）；
3. 执行 P `explicitUserRetry=true` → 真渲染（真隔离进程）→ `:296-298` 解除；
4. 再执行 P 无重试 → `Rendered`（解除持久化证明）。

覆盖面核算：P-12③ 的断言对象是**解除路径**（`fusedDigests.remove`）+ 检查位语义；熔断**触发**半边已由既有设备绿钉住（`:367` 基线 = 真死亡路径置位；issue240 挂死例覆盖真超时置位）。方案 B 恰好补上缺的那半，不多测一寸。
成本：1 个 internal fun（非热区文件；`app/.../visualjs/` 本轮 `git status` 干净）；先例一致（同文件已有 7 个 internal 测试 seam，`:1154-1198`）；负控可选：`fusedDigestsForTest()` 只读快照便于失败诊断（**可选**，不指认额外失败则不加——12.2 纪律）。

**建议：方案 B。** 方案 A 若被选中，必须先给 300/1500/2500 ms 标定数据，且接受用例时长与共享模拟器脆性——两案在判据上等价（同一解除位断言），风险与成本不等价。

### 3.4 两案共同纪律

- 基线不回退：`VisualJsIsolationInstrumentedTest.kt:367`（`PROGRAM_FUSED`）与 `:372/:376/:387` 必须仍绿（批次 35 判据 §1.4 原文）；
- `VisualJsIsolationInstrumentedTest` 现为 14 例（`grep -c @Test` 本轮实跑），新增例计入隔离套件（判据预期 14→15）；
- R-04 keep 关联门同 §1.3 末段（若进 release 轨需成员 keep + 逐条理由）。

---

## 四、任务四：GitHub 参考（端到端仪器化）评分

评分维度：License / 维护度（pushed_at 与 release）/ 与本链兼容性（Android + Kotlin + JUnit4）/ 可复用性。元数据全部本会话 `gh api repos/<name>` 实跑（2026-10-30）。

| 仓库/来源 | License | 维护度 | 适用面 | 评分与结论 |
|---|---|---|---|---|
| [android/testing-samples](https://github.com/android/testing-samples)（9.3k★） | Apache-2.0 | pushed 2025-07-18（~15 个月，官方样例库节奏正常） | 仪器化测试组织范式；`integration/ServiceTestRuleSample` 是唯一服务类样例 | **B**。结构参考；`ServiceTestRule` 不覆盖隔离进程 bind（我们的 `VisualJsClientEngine` 自带测试 seam 更贴题）——引用不引码 |
| [dokar3/quickjs-kt](https://github.com/dokar3/quickjs-kt)（155★） | Apache-2.0 | pushed 2026-10-29（**前一日**，高活跃） | 上游 JS 引擎的 integration-test 套件（`integration-test/general/src/commonTest/QuickJsIntegrationTest.kt`，git tree API 实查）——真执行集成测试的组织方式（内存上限/超时） | **B+**。沙箱行为测试设计参考；**quickjs-kt-android 1.0.15 与本仓的发布关系 UNVERIFIED**（仓内 KDoc 提及版本号，Maven 发布者关系未核）——单一来源待验证，只作设计参考不作依赖依据 |
| [TomRoush/PdfBox-Android](https://github.com/TomRoush/PdfBox-Android)（1.2k★） | Apache-2.0 | **红牌**：最后 release v2.0.27.0 = 2023-01-02，最后 push 2024-03-18（约 2.5 年无 release） | PDF 文本提取断言 | **不采纳**。维护度一票否决（11.3 纪律）；且本仓 PDF 断言已有仓内 PdfRenderer 约定（§1.3 用例 D），无需文本提取 |
| [takahirom/roborazzi](https://github.com/takahirom/roborazzi)（1.1k★） | Apache-2.0 | pushed 2026-10-22（活跃） | JVM 截图 golden 测试 | **C**。本链 painter 断言在真设备（QuickJS 仅设备可执行，`QuickJsVisualExecutionEngineJvmTest.kt:15-25` 实读），JVM 截图方案不适配 |
| [cashapp/paparazzi](https://github.com/cashapp/paparazzi)（2.6k★） | Apache-2.0 | pushed 2026-10-28（活跃） | JVM 布局渲染快照 | **C**。同上不适配 |
| [android/nowinandroid](https://github.com/android/nowinandroid)（22.0k★） | Apache-2.0 | pushed 2026-10-29（活跃） | 全应用 instrumented 测试组织/CI 门参考 | **B**。组织范式参考 |
| [PdfRenderer 官方文档](https://developer.android.com/reference/android/graphics/pdf/PdfRenderer)（developer.android.com） | — | 平台 API | 设备侧 PDF 页数/栅格断言 | **采纳**（零依赖）；配套样例 [android-PdfRendererBasic](https://github.com/googlesamples/android-PdfRendererBasic) 为 **googlearchive 归档仓**（`gh api` 实证 archived=true, push 2019-09）——只作 pattern 引证，不作活依赖 |

**总规则（GitHub 优先纪律的落地）**：本链每一环仓内已有实现（隔离引擎 14 例设备套件、painter parity/PdfRenderer 回读、commit 仓储 12 例设备套件、指纹 42 例 inline golden）——**项目内已有实现优先于外部方案**，外部参考全部只作"测试组织范式"引用，本设计不引入任何新第三方测试依赖。

---

## 附 A：本会话实读/实跑清单（可重跑）

```bash
# git 状态（热区/改动面判定）
git status --porcelain -- app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/ \
  app/src/main/kotlin/com/tingyun/smartmistakebook/SmartMistakeBookApplication.kt \
  app/src/androidTest/kotlin/com/tingyun/smartmistakebook/visualjs/ \
  core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/ \
  core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/model/wire/ \
  core/model/src/test/kotlin/com/tingyun/smartmistakebook/core/model/ModelTaskFingerprintStabilityTest.kt \
  core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/ModelTasks.kt core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/
# → visualjs/visual/wire/main 两个 core 文件干净；core/data/src/test 6 个他线 M

# 计数与定位
grep -c "@Test" app/src/androidTest/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsIsolationInstrumentedTest.kt   # 14
grep -c "@Test" core/model/src/test/kotlin/com/tingyun/smartmistakebook/core/model/ModelTaskFingerprintStabilityTest.kt # 42
grep -n "PROGRAM_FUSED\|explicitUserRetry = true" app/src/androidTest/.../VisualJsIsolationInstrumentedTest.kt  # :367/:372/:376/:387
grep -rn "VisualDrawOpsPdfPainter" --include=*.kt app core feature   # 仅 androidTest 命中
grep -rn "VisualSnapshotRestoreRecord(" --include=*.kt core app      # 仅 VisualSnapshotRestoreTest.kt:65
grep -rn "FigureGenerationTrace" --include=*.kt core | grep -v test  # 空

# GitHub 元数据（gh api，2026-10-30 实跑）
gh api repos/TomRoush/PdfBox-Android --jq '{...}'   # Apache-2.0, push 2024-03-18, release v2.0.27.0 2023-01
gh api repos/android/testing-samples ...            # Apache-2.0, 9292★, push 2025-07-18
gh api repos/dokar3/quickjs-kt ...                  # Apache-2.0, 155★, push 2026-10-29
gh api repos/takahirom/Roborazzi ...                # Apache-2.0, 1073★, push 2026-10-22
gh api repos/cashapp/paparazzi ...                  # Apache-2.0, 2638★, push 2026-10-28
gh api repos/android/nowinandroid ...               # Apache-2.0, 21983★, push 2026-10-29
gh api repos/googlesamples/android-PdfRendererBasic # googlearchive, archived=true, push 2019-09
# WebSearch ×1：PdfRenderer 设备断言模式（官方 API 文档为采纳源）
```

实读文件（全部本会话 Read/sed 全文或关键段）：`DefaultFigureGenerationCoordinator.kt`（828 行全读）、`VisualJsClientEngine.kt`（1263 行全读）、`VisualJsService.kt`（680 行全读）、`VisualExecutionEngine.kt`（315 行全读）、`TutorVisualCommitRepository.kt:1-360`、`VisualSnapshotRestore.kt:181-240`、`VisualFrameInputs.kt:1-60`、`ModelWireProtocol.kt` 全文、`ModelTasks.kt:40-75/:730-850`、`OpenAiSse.kt:555-575`、`RemoteModelDispatchLedger.kt:1-60`、`RoomFigureDispatchLedger.kt:60-80`、`TutorToolLoop.kt:340-428`、`TutorVisualPort.kt`（函数清单）、`SmartMistakeBookApplication.kt:330-409`、`VisualJsIsolationInstrumentedTest.kt:300-400`、`TutorVisualCommitRepositoryInstrumentedTest.kt:25-70/:785-797`、`QuickJsVisualExecutionEngineJvmTest.kt:1-35`、`FigureHttpTransportBypassObservationTest.kt:1-40`、`FigureGenerationBudgetDeclarationTest.kt:1-30`、`ModelTaskFingerprintStabilityTest.kt:1-70`+函数清单、批次 36 基线全文、批次 35 判据 §1.4/§3.1、清单 `:130-140/:195-202`、计划全文。

## 附 B：未验证项（如实登记）

1. **零 Gradle/零设备**：全部测试设计与判据未实跑（本轨纪律）；"第 7 次派发拒"等向量是否已被既有账本用例覆盖，未逐用例读断言——实现轨先引用用例名（清单原文口径）。
2. R-04 K1-K6 的 release 套件单未核实（§1.3 末段 keep 关联门的前提）。
3. `FETCH_HEAD`/远端新鲜度沿基线（未 fetch，本轨只读）。
4. quickjs-kt-android 与 dokar3/quickjs-kt 的发布关系（§四）——单一来源待验证。
5. "128 KiB/≤6 布局/10 s 墙钟/两份最终图/文字降级"的 grep 复跑未做（沿基线 §4.2 当日实跑在案）；`FigureGenerationTrace` 0 命中为本轮实跑。
6. GitHub 搜索通道：`gh api search/repositories?q=isolatedProcess...` 一次返回空（单通道失败），按纪律换通道（直接核对具体仓 + 官方文档），未再耗轮次；isolatedProcess 专项测试仓的更广检索未做（3 轮上限内主动收敛——仓内自建 seam 已是事实标准）。
