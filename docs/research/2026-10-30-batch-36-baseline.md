# 批次 36 基线核对（2026-10-30）

> 核对人：基线核对工程师（子代理，只读核对：零代码改动、零 Gradle、零设备写）。
> 方法：全部结论来自本会话实读文件与实跑命令，命令与关键输出随条记录。
> 无当前证据的结论一律标 **UNVERIFIED**。
> 对照计划：`.zcode/plans/plan-2026-10-30-batch-36-v01-r04-k6.md` §0（下称计划 §0）。

## 0. 一句话结论

计划 §0 的 HEAD、R-04 K6 崩溃签名、M-04 案 B/P-11 保守分支、A-10~A-12 缺口、设备计数等**主干事实全部与现树相符**；**已过时的有两处**——①"准入清单的 A-04~A-06 行仍写'未落'"已失真（批次 35 v3 补记 8 已把 A-04/A-05/A-06 等**九行**刷新为"已落码/门已脚本化"，计划任务 8 的刷新半边已落）；②"`r04-test-keep.pro` 注释声称的门不存在"已随批次 35 T3 脚本化（`tools/ci/check_r04_keep_rules.py` 在树）。R-04 K6 的 keep 规则缺口**仍成立**：现 keep 规则不含 `androidx.tracing` 与 desugared `j$/util` 任何 keep，K6 证据签名逐条在案。工作树 686 条脏项，绝大多数属他线 UI 轨，批次 36 不得裹挟提交。

## 一、HEAD、origin/main、工作树、DB schema

| 项 | 结果 | 证据（本会话实跑） |
|---|---|---|
| HEAD | `4f11cd5432e6c259038fc92bd2e975aee8d304b4`（`git rev-parse HEAD`），`git log -1` = "chore: 移除误入暂存区的他线 knowledge-production 两文件（工作树保留原样，归属 KB 线）" | 与计划 §0 "HEAD `4f11cd54`" 一致 ✅ |
| origin/main（本地引用） | 同上逐字节相同（`git rev-parse origin/main`）；`git rev-list --left-right --count origin/main...HEAD` = `0 0` | 本地视角无差 |
| **fetch 新鲜度** | `.git/FETCH_HEAD` mtime = **Oct 27 00:39**（`ls -l` 实读）——仍未 fetch，"HEAD==origin/main"只是本地引用视角；**远端是否已有新提交 UNVERIFIED**（本轨只读，未 fetch） | 同批次 35 基线 §一 的保留项 |
| 工作树计数 | **686 条**：`212 M` + `474 ??`（`git status --porcelain \| wc -l` + `awk` 按状态列计数）。比批次 35 基线（685 = 211 M + 474 ??）多 1 条 M | 实跑；未逐条追认新增的 1 条 M 是哪个文件（UNVERIFIED） |
| DB schema | **71**：`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabase.kt:139` `internal const val STUDY_DATABASE_VERSION = 71`（实读）；`core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/71.json` 存在（`find` 实证） | ✅ 与计划 §2 "DB schema 71" 一致 |
| 请求 schema | 本轨未重核（批次 35 基线 §一 已核：`figureMethods` 默认 `IMAGE_MODEL`，`OpenAiModelProtocol.kt:378-379`）；本轮只读 grep 未见 `availableMethods` 接通迹象（A-05 行 `RoomModelTaskRepository.kt:958-963` 透传 `false/false`，转引清单 v3 补记 8 文本，未独立实读该文件） | 上轮证据沿用，本轮未复跑 → 沿用部分标转引 |

工作树脏项按目录分布（`git status --porcelain` 分组计数，实跑）：`feature/tutor` 59、`feature/library` 43、`core/ui` 35、`docs/research` 32、`app/src` 27、`feature/capture` 16、`core/data` 15、`feature/review` 12、`tools/kb_coverage` 11、`core/database` 10、`docs/superpowers` 7、`docs/current` 7、`core/export` 6、`core/domain` 5、`feature/profile` 4、`docs/history` 3。锁文件仍全部 untracked（各模块 `gradle.lockfile` `??`）——符合"锁文件与 `tmp/` 不提交"。

## 二、热区快照与 core/domain、core/data、core/database、app 开工态

热区口径沿批次 35 基线 §二：`RoomTutorToolRunner.kt`、`feature/*`、`core/ui`、`core/export`、`core/markdown`、`core/domain`、`settings.gradle.kts`、Root。

| 热区/模块 | 开工态（`git status --porcelain` 实跑） |
|---|---|
| `RoomTutorToolRunner.kt`（实际路径 `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/RoomTutorToolRunner.kt`） | **干净**（对该路径 `git status --porcelain` 输出为空）。计划任务 6 的"若已清空则落类型化 errorKind"前置（git 视角）成立；注意该文件此前的第二道门已随 `559145cf` 整文件提交（批次 35 基线 §三.2） |
| `settings.gradle.kts` | ` M`（他线模块注册改动延续） |
| Root（`SmartMistakeBookRoot.kt` / `SmartMistakeBookDestinations.kt`） | 两者均 ` M`；`app/src/main/kotlin/.../smartmistakebook/` 下共 **10 条脏**（`git status --porcelain` 实跑） |
| `feature/*` | **134 条脏**（tutor 59 / library 43 / capture 16 / review 12 / profile 4）——他线 UI 重建轨工作面 |
| `core/ui` | **35 条脏** |
| `core/export` | 6 条脏 |
| `core/domain` | **4 个源文件 M**：`BatchImportRepository.kt`、`MistakeDetailRepository.kt`、`SchedulingEvaluation.kt`、`TutorJudgedReview.kt`（+ `?? gradle.lockfile`）——**M-04 案 A 的"热区清空"前提不满足**（见 §六） |
| `core/model` | src 干净（本轮 grep 触及的 `FigureGenerationPolicy.kt`/`ModelTasks.kt`/`FigureGeneration.kt` 均不在脏清单；整目录状态沿批次 35 基线"仅 lockfile untracked"，未重跑全目录计数 → 该行半沿引） |
| `core/data` | 15 条脏（`RoomMistakeDetailRepository.kt` + 多个测试文件 M 等）+ `?? gradle.lockfile` |
| `core/database` | 10 条脏（`RoomBatchImportStore`、`TutorConversationDatabaseContract`、DAO、`CleanRedrawTaskPort` 等）+ `?? gradle.lockfile` |
| `docs/current` | 7 条 M（README/architecture-contract/data-schema/implementation-status/model-task-contracts/product-contract/release-gates）；**`visual-generation-contract.md` 本身干净**（见 §七） |

批次 35 收口提交（`492b349e`/`ea461f60`/`1c0af2dc` 等）后无二次改动的复核：本轨未做全量 `comm` 交集复核（批次 35 基线做过一次），**UNVERIFIED**；但从 §三/§四 所引文件均为干净状态看，无返工迹象。

## 三、R-04：K6 崩溃证据、现 keep 规则、tracing/j$ 引用点

### 3.1 K6 崩溃证据（`build/device-track-batch35/r04-k6-logcat.txt`，291 行，实读全文）

时点 2026-10-29 19:03，进程 `com.tingyun.smartmistakebook.offline`（release 主包，`AndroidJUnitRunner` 启动即崩，`Process crashed before connection to orchestrator`）。计划 §0 的三类签名**逐条在案**：

| 签名 | 出现处（行号） |
|---|---|
| `ClassNotFoundException: androidx.tracing.Trace` | `:110`（crash 段 Caused by） |
| `java.lang.NoClassDefFoundError: Failed resolution of: Landroidx/tracing/Trace;` | `:92`（`AndroidJUnitRunner.sendStatus` 报崩溃时自身也炸） |
| `NoSuchMethodError: No direct method <init>(Ljava/util/Map;)V in class Lj$/util/g;`（声明在 test dex `classes2.dex`） | **恰 4 次**：`:86`/`:141`/`:240`/`:274` ✅ 与计划 §0 "4×" 一致 |

崩溃链：`androidx.startup.InitializationProvider.onCreate` → `WorkManagerInitializer` → `androidx.work.impl.WorkDatabase.<init>` → `j$.util.DesugarCollections.synchronizedMap` → `NoSuchMethodError`；随后 runner 自身报崩溃时又撞 `NoClassDefFoundError androidx/tracing/Trace`。伴生 hidden-API 拒绝 3 条（`:57-59`，`Collections$SynchronizedCollection;->mutex` 等反射 denied）——与 desugar `Collections` 的 hidden-API 依赖一致，扩 keep 时应一并入"为什么需要"清单。

同目录佐证文件在树（`ls` 实证）：`r04-step1-red-logcat.txt`、`r04-two-apk-sha.txt`、`r04-k3-main.txt`/`r04-k3-test.txt`、`r04-k5b-count.txt`、`src-sha-r04-*.txt` 五件（K2/K3/K5 证据）。

### 3.2 现 keep 规则（`app/r04-test-keep.pro`，41 行，实读全文）

现有 keep 仅四组：① 测试类构造器 + `@Test/@Before/@After/@BeforeClass/@AfterClass`（`:22-29`）；② `@Test` 成员（`:32-34`）；③ `androidx.test.**`/`org.junit.**`/`junit.**` 全量（`:38-40`）。**不含任何 `androidx.tracing` 或 `j$/util`（desugared java.util）keep** ——K6 阻塞的 keep 缺口在规则文本层确认成立。头部注释（`:12-18`）已改为引用真实门脚本（见 3.4），并保留 "NEVER disable obfuscation"。

### 3.3 `androidx.tracing` 与 `j$/util` 引用点（grep 实跑）

- **`androidx.tracing` 在源码/构建脚本中 0 命中**（`grep -rn "androidx.tracing" --include=*.kt/*.kts/*.toml/*.pro app/ core/ feature/ gradle/`，排除 build 目录）。它是**传递依赖**：`app/gradle.lockfile:162-163` `androidx.tracing:tracing(-ktx):1.2.0`（`localFirstDebugRuntimeClasspath`）；`core/data/gradle.lockfile` 无 tracing 行（仅 app 轨道拉入）——与崩溃链来自 app 主包 WorkManager 的路径吻合。
- **desugaring 配置 5 处**（`grep -rn "desugar" --include=*.kts/*.toml`）：`app/build.gradle.kts:170`、`core/data/...:168`、`core/database/...:30`、`feature/library/...:25`、`feature/tutor/...:28` 均为 `coreLibraryDesugaring(libs.desugar.jdk.libs)`；`gradle/libs.versions.toml:62` `desugar-jdk-libs = "2.0.3"`。`j$/util/g` 即 desugar_jdk_libs 改名后的 `java.util` 实现类。
- **任何 `.pro` 文件无 `j$` keep**（`grep -rn "j\$" app/*.pro` = 0 命中）。
- 扩 keep 的实现注意点（供实现轨引用，非本轨裁定）：`NoSuchMethodError` 的两个当事类（`j$.util.DesugarCollections.synchronizedMap` 调用的 `j$/util/g.<init>(Ljava/util/Map;)V`）与 `androidx.tracing.Trace` 都在**主包 dex**被 R8 收缩/改名后与 test dex 的引用错位；keep 需覆盖"test dex 按名引用主包类"的两侧（类名 + 构造器签名）。

### 3.4 keep 门脚本化（批次 35 T3，幽灵门已修）

- `tools/ci/check_r04_keep_rules.py` 在树（实读头部 60 行）：只匹配规则文本中的 `-dontobfuscate`（词边界、大小写不敏感、注释排除），默认检查 `app/proguard-rules.pro` + `app/r04-test-keep.pro`，缺文件即 FAIL；含负控旋钮（位置参数替换目标）。
- `tools/tests/test_r04_keep_rules.py` 在树（`ls` 实证）。两者随 `1c0af2dc`（"批次35 A-13 恰 3 处门与 R-04 keep 门脚本化（含负控自测；本轮实跑 PASS/0 hits/55 passed）"）提交。
- **本轨未复跑该脚本与 pytest**（只读口径下运行 Python 只读脚本属安全操作但非本 ask 所列核对项）→ 本轮执行结果 UNVERIFIED；上轮（批次 35 记录）"PASS/0 hits/55 passed" 为转录非本轮实测。

### 3.5 K1–K6 执行状态

K6 仍为阻塞态：仓内无 K6 绿的任何证据；计划任务 2 的"重跑 K1–K6 全链"未发生（本轮零 Gradle，符合本轨只读口径）。K6 两例在 release 轨从未执行过 → R-04 不得判通过（沿批次 35 关键限定，本轮无反证）。

## 四、A-10/A-11/A-12：清单原文与现树常量

### 4.1 清单行原文（`docs/research/2026-10-26-batch-24-4e2-admission-checklist.md`，实读）

- **A-10（`:198`）**：检查项=「每逻辑操作的派发与 JS 预算 ledger：单操作 ≤6 次 Provider 模型执行派发、累计接收源码（含失败尝试）≤128 KiB、新源码布局尝试 ≤6、累计 JS 阶段墙钟 ≤10 s、单份工具 payload 解码后 JSON ≤512 KiB；operation/attempt ledger 与 `FigureGenerationTrace` 不得因换方法重置；**单逻辑操作最多提交两份最终图**；**任一预算耗尽即文字降级并使用原派发余额终答**」。现状=**部分落**（图像通道已接 ledger；`RemoteModelDispatchLedger.kt:17`/`:114`）；缺口=「逐阈值边界证据 + '两份最终图/文字降级'两条子门」。
- **A-11（`:199`）**：Route B 三协议 sourceJs 保真负向套件（`AnthropicMessagesProtocol`/`GeminiGenerateContentProtocol`/`OpenAiResponsesProtocol` × 7 向量），现状=**未落**；21 向量全绿才可声称 Provider 已支持。
- **A-12（`:200`）**：旧请求/逻辑操作指纹兼容门（EncodeDefault.NEVER/精确投影 + golden 逐字节），现状=**部分落**（基础件在：`ModelTasks.kt:738-739`、`:832-842`、`ModelTaskFingerprintStabilityTest` 42 个 `@Test`）；无"新增可空上下文 golden 扩测"行。

### 4.2 现树常量核对（grep/实读）

| 阈值（A-10 行义） | 现树落点 | 状态 |
|---|---|---|
| ≤6 次 Provider 派发 | `core/model/.../ModelTasks.kt:61` `MAX_DISPATCHES: Int = 6`；`core/data/.../visual/RoomFigureDispatchLedger.kt:73` `usableLimit = ModelTaskRemoteDispatchPolicy.MAX_DISPATCHES - …`（讲题留终答位） | ✅ 在树 |
| 计数不重置（换 method 不重置） | `core/domain/.../RemoteModelDispatchLedger.kt:19` KDoc：「operationId 不含 method：换方法/失败重试仍是同一操作身份，不复制新预算」（实读） | ✅ 端口语义在树（KDoc 层） |
| 单份工具 payload ≤512 KiB | `core/data/.../model/OpenAiSse.kt:563` `MAX_TOOL_CALL_PAYLOAD_UTF8_BYTES = 512 * 1024`（实读） | ✅ 在树 |
| program 单份 ≤64 KiB（相关常量） | `core/model/.../FigureGenerationPolicy.kt:33` `MAX_PROGRAM_UTF8_BYTES = 64 * 1_024`；`:32` `MAX_IMAGE_PROMPT_UTF8_BYTES = 4 * 1_024`；`core/domain/.../VisualExecutionEngine.kt:108` `MAX_SOURCE_JS_BYTES = 64 * 1024`（单程序） | ✅ 在树（注意：**64 KiB 是单份**，与 A-10 的"累计 128 KiB"不同口径） |
| 单事务硬期限 | `VisualExecutionEngine.kt:126` `SERVICE_HARD_DEADLINE_MILLIS = 1500L`（实读；KDoc 自述"V1 初始上限单源"） | ✅ 在树（1500 ms 是**单事务**期限，非"累计 JS 墙钟 10 s"） |
| **累计接收源码 ≤128 KiB**（含失败尝试） | grep `128`/`MAX_SOURCE`/`cumulative`/`累计` 于 `VisualExecutionEngine.kt`/引擎/协调器：仅命中 `MAX_CONTENT_REQUESTS=128`、`MAX_HIT_REGIONS=128`（内容条数，非字节预算） | ❌ **未找到对应常量/实现**——A-10 缺口属实 |
| **新源码布局尝试 ≤6** | grep 无命中 | ❌ 未找到 |
| **累计 JS 阶段墙钟 ≤10 s** | grep `10_000`/`wallClock` 无命中（core/data/visual 全包） | ❌ 未找到 |
| **最多两份最终图 / 耗尽→文字降级** | 本轨未逐文件追认到对应实现（grep 未定位） | ❌ 未找到（与清单缺口"两条子门"一致） |

三协议文件在树（A-11 的对象存在）：`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/model/wire/` 下 `AnthropicMessagesProtocol.kt`、`GeminiGenerateContentProtocol.kt`、`OpenAiResponsesProtocol.kt`（`ls` 实证；`supportsNativeTools=false` 的实证沿清单 v2 附录 B，本轮未复跑）。

## 五、V-01 全链现成组件清单与缺的 seam

组件（全部实存，路径为本轮 find/grep 实证）：

| 环节 | 组件 | 落点 |
|---|---|---|
| tool proposal 入口 | `TutorToolLoop.kt`（`toFigureGenerationProposal`，JS 分支要求非空 program ≤64 KiB 且不带图像载荷 `:363`/`:398` 实读）+ 三生产入口构造点 | `core/model/.../TutorToolLoop.kt`；`core/data/.../ConfiguredCleanImageGenerator.kt:100`、`AttachedImageGenerator.kt:155`、`TutorFigureGenerator.kt:377`（A-13 门白名单，转引清单 v3 补记 8） |
| coordinator | `DefaultFigureGenerationCoordinator.kt`（含 `FigureGenerationCoordinationFactory` `:805-831`） | `core/data/.../visual/DefaultFigureGenerationCoordinator.kt`（实读工厂段） |
| 隔离引擎（进程级） | `VisualJsService`（isolatedProcess Service + AIDL + watchdog）+ `VisualJsClientEngine`（客户端队列/熔断/重建） | `app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsService.kt`、`VisualJsClientEngine.kt`（find 实证） |
| 事务内引擎（in-process） | `QuickJsVisualExecutionEngine` | `core/data/.../visual/QuickJsVisualExecutionEngine.kt` |
| 度量 | `AndroidVisualContentMeasurer`（internal，服务侧） | `app/.../visualjs/VisualContentMeasuring.kt:53`（grep 实证） |
| compiler | `visual-compiler.js` + `visual-sdk.js` | `core/data/src/main/resources/visual/`（`ls` 实证两文件均在） |
| painter | 屏 `VisualDrawOpsPainter(.kt)`+`PainterPure`+`PainterRendered`；PDF `VisualDrawOpsPdfPainter`+`PdfPainterPure`+`PdfWriteIntegrity`；序列化 `VisualDrawOpsParser`/`Validator`/`DuplicateKeys` | `core/data/.../visual/`（`ls` 实证 23 文件） |
| 保存/持久化 | `TutorVisualCommitRepository`、`VisualCheckpointRetention`、`VisualSnapshotRestore`、`FigureGenerationDao`（schema 71 四表） | `core/data/.../visual/` + `core/database/.../dao/FigureGenerationDao.kt` |
| PDF（导出侧） | `VisualDrawOpsPdfPainter` + `PdfWriteIntegrity`；app 导出 worker | `ExportPdfWorker.kt` 存在（工作树 ` M`），但 **`grep VisualDrawOpsPdfPainter app/.../ExportPdfWorker.kt` = 0 命中**——PDF 导出链对 painter 的生产调用点本轨未逐文件追认到，**该环节接线点 UNVERIFIED** |

**缺的 seam（本轮实证）**：

1. **生产引擎装配为 null**：`FigureGenerationCoordinationFactory.create` 的 `visualEngine`/`visualFrameInputs` 默认 `null`（`DefaultFigureGenerationCoordinator.kt:817-819` 实读，KDoc 明言"生产继续走 UNAVAILABLE"）；全仓唯一生产调用点 `SmartMistakeBookApplication.kt:364-370` 只传 `database/configurationStore/networkRequestsAllowed` 三参（实读）→ JS 恒 `UNAVAILABLE`。
2. **`availableMethods` 未接通**：清单 A-05 行 `RoomModelTaskRepository.kt:958-963` 显式透传 `false/false`（转引 v3 补记 8，未独立实读）。
3. **L3 客户端无生产装配**：P-12 行「生产 `VisualExecutionRequest` 唯一构造在 wire 层 `VisualJsProtocol.kt:185`」（转引清单；本轮 grep `VisualJsProtocol.kt` 确认文件在 app/visualjs）。
4. **UI 消费零命中**：`grep -rln VisualSceneCard core feature app` = **空**（实跑）——S-07 未落，V-01 设备用例若涉 UI 卡片段需按计划 §1 任务 5 用"测试内最小渲染替代并标注降级"。
5. P-11 errorKind 输出通道（见 §六）。

## 六、P-11 errorKind 半边与 M-04 案 A 的落点热区状态

- **P-11 errorKind 半边——已登记未落**：清单 `:134` P-11 行「**保守分支登记**：…tool-round 类型化 errorKind 半边未落（协调器无 errorKind 输出通道，runner 侧写入需动 `RoomTutorToolRunner.kt` 整文件协议且 JS 路径未接工具环——以既有 status 通道承载，`FigureGenerationStatus.UNAVAILABLE` KDoc 已写明设施类语义）」（实读原文）。P-11 的消费点半边已落（协调器 Rejected 分支 `DefaultFigureGenerationCoordinator.kt:358-366` 折 `UNAVAILABLE`）。
- **`RoomTutorToolRunner.kt` 现为 git 干净**（§二）→ 计划任务 6 的条件分支「若已清空则按批次 35 设计落类型化 errorKind」的前置（以 git 视角）成立；"热区"的另一半含义（他线是否仍认领该文件）仓库状态无法证明，**登记口径由主会话裁定**。
- **M-04 案 A——前提不满足**：清单 `:158` M-04 行「案 B 已落码（批次 35 T6）：折点唯一 `QuickJsVisualExecutionEngine.kt:396-410`…**保守分支登记：案 A（core/domain 新增 `MEASUREMENT_UNAVAILABLE` 枚举）不执行——热区**」（实读原文）。现树 `core/domain` 仍有 4 个源文件 M（§二）→ 计划任务 7 的「热区清空才做；否则维持案 B 并登记」应走**维持案 B** 分支。

## 七、准入清单 A-04~A-06 行、契约 :208、KD-82/72/73/77

### 7.1 准入清单（v3 补记 8 已刷新九行——计划 §0 相关表述过时）

`git log` 实证清单最后两次文档提交：`492b349e`（批次 35 台账，含"准入清单 v3 补记 7/8 与九行刷新"）、`78caacb2`（批次 34）。清单头部 `:5` v3 补记 8 原文（实读）：「按批次 35 落码与设备证据刷新 **A-04/A-05/A-06/A-08/A-13/P-11/P-12/M-04/Q-01** 九行现状——全部为'已落码/门已脚本化'，0 项转'通过'…不新增行（仍 69 行）」。

四行现状原文（实读转录）：

| 行 | 现状原文（转录） | 与计划 §0 的比对 |
|---|---|---|
| A-04（`:192`） | 「**已落码（批次 35 T4，v3 补记 8）**：装配期两处 `require(JS !in …)` fail-closed 已删（现树 `JS !in` 0 命中；保留空方法集拒绝）…生产三入口恒 `IMAGE_MODEL_ONLY` → JS 不广告」 | 计划 §0 说"仍写'未落'" → **过时**；计划任务 8 前半（A-04~A-06 行刷新）**已在批次 35 完成** |
| A-05（`:193`） | 「**已落码（批次 35 T4，v3 补记 8）**：`withDependencies` 增 `jsEngineReady`/`jsMeasurementReady` 双就绪位…`GENERATE_FIGURE` 保留条件改 `imageBackendReady \|\| (jsEngineReady && jsMeasurementReady)`（`Capabilities.kt:92`）…`RoomModelTaskRepository.kt:958-963` 显式透传 `false/false`」 | 同上 |
| A-06（`:194`） | 「**已落码（批次 35 T4，v3 补记 8）**：两描述函数增 `figureMethods` 参数…生产消费点不传参 → 广告文本零改动」 | 同上 |
| A-13（`:201`） | 「**门已脚本化（批次 35 T5，v3 补记 8）**：`tools/ci/check_figure_entry_gates.py`…本会话实跑 PASS/EXIT=0，命中 `ConfiguredCleanImageGenerator.kt:100`/`AttachedImageGenerator.kt:155`/`TutorFigureGenerator.kt:377`」 | 门脚本在树（find 实证 `tools/ci/check_figure_entry_gates.py`）；本轮未复跑 → PASS 为转录 |

### 7.2 契约 `docs/current/visual-generation-contract.md:208`（原文，实读）

`:208` 全文为：「切分门（AUD-08）2026-10-27 登记工具面闭环：冻结金标 v1（7 页/16 框，**模型标注代理金标，无人工专家**）与可复跑判门（`tools/split_golden/`：validate / export / measure / check_split_gate + 门说明 GATE.md）。门值 ①≥0.9 且 ③≤0.05…当前实测门 **FAIL**（① 0.5714…）→ **门后自动化不启用**…。**快照身份边界（2026-10-29 批次 32 T6 登记 KD-81）**：`export_split_eval.py` 的 `source.db.sha256` 只覆盖主库文件字节（`_sha256` 在 `:72-73`、manifest 在 `:221-222`），**不含 `-wal`/`-shm`**；设备库默认 WAL 模式，同一主库 + 不同 WAL 会得到不同导出却报同一 sha…未覆盖前，该 sha 只能读作'主库字节摘要'，不得读作完整快照身份。」

**过时点确认**：KD-81 已于批次 35 按裁定**关闭**（db+wal 进身份、`-shm` 排除；工具侧 `export_split_eval.py:78`/`:101-135` 已实现，批次 35 基线 §四 实读），`:208` 仍写"不含 -wal/-shm、不得读作完整快照身份" → 与关闭裁定矛盾，计划 §0 "快照身份句过时"成立。**补充事实**：该文件本身当前 **git 干净**（`git status --porcelain -- docs/current/` 显示 7 个 M 文件中无此文件）——计划任务 8 的"若热区清空则回改"从 git 视角看该文件可改；但 `docs/current` 整目录是热区（v3 补记 4 口径"契约正文（docs/current）为热区（开工即 M）"），同目录他线 7 文件在飞，是否动笔归主会话裁定。

### 7.3 KD 台账（`docs/known-defects.md`，标题行实读）

| KD | 现状态（标题行原文） |
|---|---|
| KD-82（`:3137`） | **open（known limitation，不排期；低）**——`maxLength` 按 Unicode 码点 vs 本地门按 UTF-8 字节（非 ASCII 下广告预算可达执行门 3-4×） |
| KD-72（`:2758`） | **open**——dependency locking 已配置但仓库无已跟踪锁文件（与现树"锁文件全部 untracked"一致，§一） |
| KD-73（`:2794`） | **open**——kotlin-stdlib 强制 2.3.21，兼容证据未覆盖全解析图 |
| KD-77（`:2903`） | **open**（批次 32 T6 标题回改）——android-23 镜像已装盘，修复后 APK 未在 API 23 完成装机/冷启动/最小冒烟 |

与计划 §1 任务 10 的对应：KD-72 锁窗口复核（W1–W7）、KD-73 兼容证据脚本化、KD-77 API 23 冒烟——三项均为 open，无前置已完成的新证据。

## 八、设备在线状态（只读）

`"C:/Android/Sdk/platform-tools/adb.exe" devices -l`（本会话实跑）：

```
emulator-5562   device  product:sdk_gphone64_x86_64  transport_id:18
emulator-5584   device  product:sdk_gphone64_x86_64  transport_id:7
emulator-5596   device  product:sdk_phone_x86_64 (generic_x86_64)  transport_id:27
```

**3 台模拟器在线（全 x86_64）**。与批次 35 设备轨（emulator-5562）相比：5562 仍在线；批次 35 基线里的 5566 已不在、新增 5596（generic 镜像）。未做任何安装/推送/查询扩展；设备上已装 APK 是否对应当前源 **UNVERIFIED**（本轨未查包版本）。

## 与计划 §0 的不符处汇总（核心交付）

1. **"准入清单的 A-04~A-06 行仍写'未落'（欠报，需刷新）"→ 过时**：v3 补记 8（批次 35，提交 `492b349e`）已把 A-04/A-05/A-06（连同 A-08/A-13/P-11/P-12/M-04/Q-01）九行刷新为"已落码/门已脚本化"（§7.1 原文转录）。计划任务 8 的"清单行刷新"半边已落，剩余只有契约 `:208` 回改（且该文件 git 干净，是否动笔属热区裁定）。
2. **"幽灵门/`.pro` 注释声称不存在的门"→ 已修**（批次 35 T3）：`tools/ci/check_r04_keep_rules.py` + `tools/tests/test_r04_keep_rules.py` 在树，`.pro` 头注释已改指真脚本（§3.4）。本轮未复跑脚本，PASS 为转录。
3. **其余主干相符**：HEAD `4f11cd54`、K6 三类签名（含 4×`j$/util/g` 逐条在案）、M-04 案 B + 案 A 热区登记、P-11 errorKind 半边登记、A-10~A-12 缺口、DB schema 71、设备 3 台 x86_64——全部与现树一致。
4. **R-04 keep 缺口仍成立且已定位到规则文本层**：现 keep 规则零 `androidx.tracing`/`j$` 条目；`androidx.tracing` 0 源码引用（仅 app 轨道传递依赖 tracing 1.2.0）、desugaring 配置 5 模块（§3.2/§3.3）。
5. **A-10 逐阈值常量断层**：6 次派发/512 KiB payload/64 KiB 单份 program 在树；128 KiB 累计源码、≤6 布局尝试、10 s 累计墙钟、两份最终图、文字降级**未找到**（§4.2 表）——计划任务 3 的边界用例需先补实现侧常量或引用既有实现。
6. **M-04 案 A 走"维持案 B"分支**：core/domain 仍 4 文件脏（§二/§六）。
7. **RoomTutorToolRunner.kt git 干净** → 计划任务 6 可走"已清空则落 errorKind"分支（热区认领的另一半裁定留主会话）。
8. **推送状态 UNVERIFIED**：HEAD==本地 origin/main（0/0），FETCH_HEAD 停在 10-27；如需定谳需 `git fetch`（本轨只读未做）。
9. **V-01 的关键缺 seam 实证**：生产装配恒 null（`SmartMistakeBookApplication.kt:364` 三参调用）、`VisualSceneCard` 0 命中、L3 无生产装配（§五）——计划任务 5 的设备用例在"全程 0 Provider 派发"外还需处理"生产装配未接"的现实（注入 fake/测试内装配，或按计划降级标注）。

## 附：本会话实跑命令清单（可重跑）

```bash
git rev-parse HEAD origin/main; git log -1 --format='%H %s'
git rev-list --left-right --count origin/main...HEAD        # 0 0
git status --porcelain | wc -l                               # 686（M=212、??=474）
grep -n "STUDY_DATABASE_VERSION" core/database/.../StudyDatabase.kt   # :139 = 71
find core/database/schemas -name "71.json"                   # 存在
git status --porcelain -- core/domain/                       # 4 M + lockfile
git status --porcelain -- "core/data/.../RoomTutorToolRunner.kt"      # 空
git status --porcelain -- docs/current/                      # 7 M（契约文件不在其中）
# K6 证据
#   实读 build/device-track-batch35/r04-k6-logcat.txt（:86/:92/:110/:141/:240/:274）
#   实读 app/r04-test-keep.pro 全文
grep -rn "androidx.tracing" --include=*.kt --include=*.kts --include=*.toml app/ core/ feature/ gradle/   # 0 源码命中
grep -n "tracing" app/gradle.lockfile                        # :162-163 tracing 1.2.0
grep -rn "desugar" --include=*.kts --include=*.toml app/ core/ feature/ gradle/   # 5 处 coreLibraryDesugaring + toml 2.0.3
# 常量
grep -n "MAX_DISPATCHES" core/model/.../ModelTasks.kt        # :61 = 6
grep -n "MAX_TOOL_CALL_PAYLOAD_UTF8_BYTES" core/data/.../OpenAiSse.kt   # :563 = 512*1024
grep -n "MAX_PROGRAM_UTF8_BYTES\|MAX_IMAGE_PROMPT_UTF8_BYTES" core/model/.../FigureGenerationPolicy.kt   # :32/:33
grep -n "SERVICE_HARD_DEADLINE_MILLIS\|MAX_SOURCE_JS_BYTES" core/domain/.../VisualExecutionEngine.kt     # :126=1500L / :108=64KiB
# 清单与契约
sed -n '200,215p' docs/current/visual-generation-contract.md  # :208 KD-81 快照身份句
git log --oneline -5 -- docs/research/2026-10-26-batch-24-4e2-admission-checklist.md   # 492b349e 最新
grep -n "## KD-82\|## KD-72\|## KD-73\|## KD-77" docs/known-defects.md
# 组件
ls core/data/src/main/resources/visual/                      # visual-compiler.js + visual-sdk.js
ls core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/
grep -rn "FigureGenerationCoordinationFactory.create" core app feature   # 唯一生产调用点 app/...:364
grep -rln "VisualSceneCard" core feature app                 # 0 命中
# 设备
C:/Android/Sdk/platform-tools/adb.exe devices -l             # 3 emulators
```
