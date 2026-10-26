# 契约准入对照审计（批次 25 R2，2026-10-27）

> **性质**：只读审计 + 单文件落盘。零代码、零 Gradle、零设备执行、零 git 写操作。目标文件仅本文档（`docs/research/2026-10-27-contract-admission-gap.md`）；未触碰任何热区与他线记录。
>
> **输入**：契约 `docs/current/visual-generation-contract.md`（全文通读，219 行）；清单 `docs/research/2026-10-26-batch-24-4e2-admission-checklist.md`（全文通读，337 行，HEAD `d783ae84` 已提交于 `aabae781`）；计划 `.zcode/plans/plan-2026-10-27-batch-25-race-warmup-queue.md`（全文通读）。佐证用只读 grep/sed/find/git（附录 A 全部实跑）。
>
> **本表不是门**：本文档只产出"漏项 + 可执行判据 + 增量表"，任何一行都不构成"通过"；设备/Gradle 门仍由脚本串行执行与 R4 证据规范落盘。

---

## 0. 对账：计划所称"58 项漏六处"与现清单 63 行

计划 §0（2026-10-27 主会话）写"准入检查表 58 项落盘但复核判 MVA 不成立且漏：§4 启动面审计、§64 队列/背压、§86/§155/§202 ledger、§58 深冻结、§186 指纹、§202 API35+ 口径"。本轮实跑核对：

```bash
git -C "D:/smart mistake book" show HEAD:docs/research/2026-10-26-batch-24-4e2-admission-checklist.md | grep -cE "^\| [PSMDEAQVRX]-[0-9]+ \|"   # → 63
grep -cE "^\| [PSMDEAQVRX]-[0-9]+ \|" <工作树同文件>                                                                                          # → 63
```

63 = 58 + 5：**P-08（启动面，清单 :128）、P-09（队列/背压，:129）、P-10（死亡未确认，:130）、P-11（`[UNATTRIBUTED]` 前缀，:131）、A-10（ledger 预算，:190）已在批次 24 收口提交 `aabae781` 中补入**。因此任务点名的六处缺口里：

- **三项已有行**（启动面 P-08、队列 P-09、ledger A-10）——不再整行缺，但判据粒度不足/现状行过时，归入**第三部分**与增量表"改判据"补丁；T4 合并时**不得重复建行**。
- **三项真缺**（§3.2:58 深冻结、§8:186 旧请求/逻辑操作指纹、§9:202 API35+ 16KB 口径）——归入**第一部分真漏项**，另加本轮自行发现的五处。

---

## 一、漏项清单

### 1A. 任务点名六项的逐项裁定

| # | 任务点名 | 裁定 | 依据 |
|---|---|---|---|
| 1 | §4:62 隔离启动面审计 | **清单已有 P-08（:128），判据缺可执行命令**（dumpsys/负向 harness 未给出）→ 移入第三部分 | 契约 §4:62；清单 :128 |
| 2 | §4:64 队列/背压 | **清单已有 P-09（:129），"伪造身份/描述符方向"无 harness 定义** → 移入第三部分 | 契约 §4:64；清单 :129 |
| 3 | §4:86/§7:155/§9:202 六派发 ledger | **清单已有 A-10（:190）**，且其现状行"图像通道未接 ledger"**已过时**（见第五部分）；判据另缺"两份最终图/文字降级"两条 → 改判据补丁 | 契约 §4:86、§7:155；清单 :190 |
| 4 | §3.2:58 input/metrics 深冻结 | **真漏**：清单 63 行无任何行覆盖 | 契约 §3.2:58；`QuickJsVisualExecutionEngine.kt:813` bootstrap 仅 `Object.freeze(Math)`，`Date=undefined`/`Math.random` 替换之外无 input/metrics 冻结；:676 的 contentId 检查是事务引用白名单，不是对象深冻结 |
| 5 | §8:186 旧请求 codec/逻辑操作指纹兼容 | **真漏**：清单无行。基础件已存在（`ModelTasks.kt:736-742` 的 `encodeDefaults=true/explicitNulls=true`、`:832-842` `logicalOperationJson`/`legacyFingerprintJson`、`:1106` 旧 v9 注释、`ModelTaskFingerprintStabilityTest` ≥15 个 `@Test`），但**没有一行**要求"新增可空上下文（首个即 TutorVisualContext 进请求）必须 `EncodeDefault.NEVER` 或精确旧 schema 投影，并扩真实旧请求/逻辑操作指纹 golden" | 契约 §8:186；§9:202"旧请求指纹" |
| 6 | §9:202 API 35+ 16KB 口径 | **真漏（口径级）**：R-03（:218）只写"arm64 16 KB AVD/真机"，无 API≥35 口径、无套件集定义、未引用批 24 E-04 装载证据（`ps16k_x86_64`/API 36/3/3，仅 `runtimePageSizeIs16KB` 装载断言）。现行隔离 6/6 与 KD-70 20/20 全部跑在 API 34/x86_64/4KB（`step02-quickjs-oom.md:16-19`），**隔离与 KD-70 套件从未在任何 16KB 设备上执行** | 契约 §2:22、§9:202；`evidence/2026-10-26-batch-24/step04-page-size-16k.md` 头表 |

### 1B. 本轮自行发现的漏项（清单 63 行均无覆盖）

| # | 契约锚点 | 漏什么 | 现状证据 |
|---|---|---|---|
| L-1 | §4:70 | **programHash 熔断与本地重试预算承继**："重建只恢复运行环境，不自动重跑刚才超时/崩溃的源码。该 programHash 熔断至用户明确本地重试或源码改变；本地重试仍受相同预算"。清单 P-01/P-04 只覆盖重建次数/预算，X-03 的"执行熔断"是 §6:141 strictOffline 语境（未知版本/熔断时读快照），均非本门 | `grep "熔断" 清单` 仅命中 X-03 语境；无 watchdog-kill 后"不自动重跑/显式重试预算承继"用例 |
| L-2 | §7:151 | **Route B 三协议 sourceJs 保真负向套件**："各自 normalize/reconstructedBody 都要测试 sourceJs 原始换行、反斜杠、中文/emoji、空 content、截断和取消。不能只测 OpenAI 的两个 parser"。A-02 只写"native/Route B 两路同果"，无逐协议保真向量 | 协议在 `core/data/.../model/wire/AnthropicMessagesProtocol.kt`、`GeminiGenerateContentProtocol.kt`、`OpenAiResponsesProtocol.kt`（grep `supportsNativeTools = false` 实证）；清单无对应行 |
| L-3 | §8:188 | **旧 attachedImages cache-only resolver 与"打开旧消息不出网"**：清单无行。部分基础已落（WG-05）：`AttachedImageRegenerationWiringTest.kt:24`"旧 attachedImages 的显式重新生成必须走真实生产协调器"，再生成入口 `AttachedImageGenerator.kt:155`；但"resolver 改为 cache-only、缺失显示显式再生成动作、打开旧消息 0 派发"无门 | 契约 §8:188；grep 实证如上 |
| L-4 | §3.1:35 + §1:7 | **全入口同管线（不各维护一套方法路由）**：清单 §1.2 只列六处广告面，V-01 只走讲题 tool 链；`FigureGenerationRequest(` 在 core/data/main 的三个构造点——净化 `ConfiguredCleanImageGenerator.kt:100`、attachedImages 再生成 `AttachedImageGenerator.kt:155`、讲题 `TutorFigureGenerator.kt:377`——无逐入口"同一 policyVersion/proposal 校验、JS 不被入口私有路由"用例 | 契约 §1:7（六类入口）、§3.1:35 |
| L-5 | §4:86 | A-10 判据缺两条子门：**"单逻辑操作最多提交两份最终图"** 与 **"任一预算耗尽即文字降级并使用原派发余额终答"**。A-10 现文只有 6 派发/128 KiB/6 布局/10 s/512 KiB | 契约 §4:86；清单 :190 |
| L-6 | §7:153 | A-07 判据缺 TutorVisualContext 三预算的负向边界（源码 64 KiB、state 16 KiB、诊断 2 KiB、"放不下不自动重试"）。构造器已 enforce（`TutorVisualContext.kt:50-62` require 实证），缺的是钉住它们的测试行 | 契约 §7:153；清单 :187 |
| L-7 | §9:202 | **"源码固定与许可证"门在 R-05 无许可证判据**：R-05（:220）只写 AAR 钉版/stdlib/16KB ELF。且本轮实查 `app/src/main/assets`（3 个文件，均 compose-richtext/open-claude-ui 许可）与 `core/data/src/main/assets` **均无 quickjs-kt/QuickJS 许可文本**（grep 0 命中）——契约 §2:22"保留 MIT/QuickJS 许可证"目前无仓库载体 | 契约 §2:22、§9:202；附录 A find/grep 实证 |
| L-8 | §2:22 + §9:202 | **R8 后 JNI 面**：R-04 判据是"release 变体冷启动/Worker/OCR"；批 24 E-03 R8 冒烟仅 `am start` 冷启动 ×2（API 34/x86_64）查 FATAL/StartupException/NoSuchMethod，**无 QuickJS/visualjs 执行面**。契约 §2:22 明写"R8 后 JNI 面仍待设备轨验证" | `evidence/2026-10-26-batch-24/step03-r8-release-smoke.md` 头表与 §1；契约 §2:22 |

---

## 二、检查方法与通过判据（可执行）

> 全部为供设备轨/JVM 轨执行的**可复跑命令与用例定义**；本审计未运行其中任何一条设备/Gradle 命令（附录 B）。"通过"判据一律落在可观测输出上。

### G-1（P-08 判据补强｜契约 §4:62）

- **静态（主机，已核）**：合并清单 6 个 Provider（`BatchImportFileProvider`、`androidx.core.content.FileProvider`、`MistakePdfFileProvider`、`MistakeDocxFileProvider`、`androidx.startup.InitializationProvider`、`MlKitInitProvider`）均无 `android:process=":visual_js"`；Service `exported="false"` 且无 intent-filter（`AndroidManifest.xml:69-73`）。复核命令：`grep -A3 "<provider" app/build/intermediates/merged_manifests/<变体>/process*/AndroidManifest.xml`。
- **设备（新增仪器化用例，targetProcess `:visual_js`）**：
  1. `adb shell pidof com.tingyun.smartmistakebook:visual_js` 非空；
  2. `adb shell dumpsys activity providers | grep "visual_js"` → **0 命中**（无 Provider 托管于隔离进程）；
  3. 进程内断言 `Process.isIsolated() == true` 且 `Process.myUid() != applicationInfo.uid`，`queryContentProviders(processName=":visual_js")` 返回空；
  4. 负向：隔离进程内 `Socket(...).connect()` → 连接失败（IOException 系）；`context.filesDir` 写文件 → 失败（隔离 UID 无应用数据目录）；
  5. `adb logcat -d --pid=<visual_js pid> | grep -i "sqlite\|room"` → 0 命中（Room 未在隔离进程打开）。
- **通过判据**：2/3/4/5 全部成立，且 Application 构造期审计记录落盘（`SmartMistakeBookApplication.kt:287-304` 的 guard 与"构造期只分配对象/lazy 不触达"注释逐条核对为代码事实）。

### G-2（P-09 判据补强｜契约 §4:64）

- **JVM（新增 `VisualJsClientEngineQueueTest`，fake binder）**：并发提交 4 个 program → 1 在飞 + ≤2 待绘、第 4 个 BUSY；同 program 连续提交 → 仅保留最新 state；跨 program 完成顺序 = 提交顺序；满队列时旧帧引用不变（BUSY 保留原帧）。
- **设备（扩展隔离 suite）**：服务端单在飞/BUSY 已在（`VisualJsService.kt:85/:186-189`），新增用例：第一个 execute 用挂起源码占住单在飞槽 → 第二个 execute 收 `STATUS_BUSY` 且已渲染帧保留。
- **伪造身份/描述符方向（当前"判据不可执行"的根源是实现缺失，实现后按此测）**：第三方 UID 客户端 `bindService` → 失败/`SecurityException`（静态前提：`exported="false"`）；JVM fake callback 传错方向 `ParcelFileDescriptor` 与超长 payload → 服务端拒绝且回有界诊断、在飞状态不变（`VisualJsService.kt:200/:210/:235-236` 的槽位释放路径不受影响）。
- **通过判据**：客户端队列四行为断言绿 + 设备 BUSY/原帧用例绿 + 伪造身份/方向拒绝用例绿。

### G-3（A-10 现状改写 + 判据补｜契约 §4:86/§7:155/§9:202）

- **现状改写**：图像通道**已接** ledger——`DefaultFigureGenerationCoordinator.kt:126` 注入 `RemoteModelDispatchLedger`、`:253-281` 先 `reserve` 后建通道；ledger 侧"同一逻辑操作最多 6 次、讲题留 1 次终答"与"预算耗尽拒绝"已实现（`RemoteModelDispatchLedger.kt:17/:114`）；既有 ledger 测试载体：`ConfiguredTutorFigureGeneratorTest`、`AttachedImageRegenerationWiringTest` 等（grep 实证 5 个测试文件引用）。
- **剩余判据（JVM 为主，注入 fake 引擎/时钟）**：累计接收源码 128 KiB+1 拒绝；新源码布局第 7 次拒绝；累计 JS 阶段墙钟 >10 s 拒绝；单份工具 payload 解码后 JSON 513 KiB 拒绝；同 operationId 换 method 计数不重置；**提交第 3 份最终图拒绝（L-5）**；**预算耗尽返回文字降级而非新任务（L-5）**。第 7 次派发拒绝若 ledger 测试已覆盖则引用既有用例名，不重复建。
- **通过判据**：每条阈值边界用例绿 + `FigureGenerationTrace`/attempt 行随计数落库（既有 `figure_generation_attempt` 表）。

### G-4（S-09 新增｜契约 §3.2:58 深冻结）

- **检查方法**：
  1. bootstrap 在把 `input`/`metrics` 交给用户源码前对对象图执行深 `Object.freeze`（`QuickJsVisualExecutionEngine.kt:813` 现仅冻结 `Math`）；
  2. JVM 隔离引擎用例：JS 程序在 prepare/render 内执行 `input.viewport.w = 1` 或向 `metrics` 数组 push（严格模式）→ 抛 `TypeError`、该请求按裁定分类拒绝，且渲染输出不含被改值；
  3. Kotlin 侧：度量端口返回类型无 mutable 访问器（`core/domain/.../VisualExecutionEngine.kt:196-213` 接口审计 + 编译期证明）。
- **通过判据**：2 的负向用例绿（程序内改写被拒/无效），3 类型审计记录落盘。

### G-5（A-12 新增｜契约 §8:186 旧请求/逻辑操作指纹）

- **检查方法**：新增可空上下文字段（首个：TutorVisualContext 进请求/逻辑操作）时——① 编码侧 `EncodeDefault.NEVER` 或精确旧 schema 投影；② `ModelTaskFingerprintStabilityTest` 扩**真实旧请求 golden**（旧版本 JSON 落 test resources）+ 逻辑操作指纹向量，断言：旧 payload 解码语义等价、当前代码对旧输入的编码输出与 golden **逐字节一致**（新可空字段为 null 时不出现新键）；③ `ModelTasks.kt:1106` 的旧 v9 语义回归保留。
- **命令**：`./gradlew :core:model:test --tests "*ModelTaskFingerprintStability*"` + 相关 `:core:data:test`（由脚本串行执行，本审计不跑）。
- **通过判据**：新增字段合入后既有 golden 全绿且 golden 文件 diff 为零；新字段仅在新值非 null 时出现于编码输出。

### G-6（R-03 口径修订｜契约 §9:202 API 35+ 16KB）

- **检查方法**：AVD/真机满足 **API ≥ 35 且 arm64 且页大小 16384**（先 `adb shell getconf PAGE_SIZE` 确认），依次跑：① `VisualJsIsolationInstrumentedTest` 6/6；② `QuickJsVisualExecutionEngineInstrumentedTest` 20/20（含 P2）；③ 沿用批 24 E-04 的 `runtimePageSizeIs16KB` 装载断言。证据按 R4 规范落 E-file。
- **通过判据**：③ 在该设备返回 16384 且 ①② 全绿；同时 R-03 行补引批 24 E-04（x86_64/API 36 装载 3/3）作为既有部分证据。

### G-7（P-12 新增｜契约 §4:70 熔断与重试预算承继）

- **检查方法**：JVM 状态机测试 + 设备用例：watchdog 硬杀/引擎崩溃后——① 同 programHash 的自动重发被拒（ledger/attempt 计数不变、日志无第二次 execute）；② 用户显式本地重试入口可用且**预算承继**（不开新逻辑操作）；③ 源码 SHA 变更后熔断解除、可重新生成。
- **通过判据**：①②③ 三断言绿；与既有 `theClientRebuildsOnceAfterADeathAndTheNewWorkerIsNotBusy`（重建后不自动执行）区分开——该用例不覆盖 programHash 熔断与显式重试路径。

### G-8（A-11 新增｜契约 §7:151 Route B 保真）

- **检查方法**：新增参数化 JVM 套件，对 `wire/AnthropicMessagesProtocol.kt`、`GeminiGenerateContentProtocol.kt`、`OpenAiResponsesProtocol.kt` 三协议的 normalize/reconstructedBody 各喂 7 类向量：原始换行、反斜杠、中文、emoji、空 content、截断流、取消。
- **通过判据**：前 5 类 sourceJs **UTF-8 逐字节相等**；截断/取消显式拒绝、绝不进入执行；21 个向量全绿后才可声称"Provider 已支持"。

### G-9（D-06 新增｜契约 §8:188 attachedImages cache-only）

- **检查方法**：JVM/设备：打开含旧 attachedImages 的历史消息 → resolver 仅查本地缓存（复用 `FigureHttpTransportBypassObservationTest` 的 0 派发观测模式）；缺失映射 → UI 呈现显式重新生成动作（既有 `AttachedImageRegenerationWiringTest` 锁定再生成走真实协调器），断言打开动作 0 派发、0 计费。
- **通过判据**：离线环境打开旧消息成功且 0 网络派发；缺失时显式动作存在。

### G-10（A-13 新增｜契约 §3.1:35/§1:7 全入口同管线）

- **检查方法**：① 架构 grep 门：`grep -rn "FigureGenerationRequest(" core/data/src/main` → 仅三个构造点（净化 :100、attachedImages :155、讲题 :377），出现第四处即失败；② JVM：三个入口 + IMAGE_PIPELINE_CLASSIFY 各构造 JS proposal，断言全部经同一 `FigureGenerationCoordinator` 端口与同一 policyVersion/proposal 校验（fake 引擎），无入口私有 method 路由分支。
- **通过判据**：① 命中数 = 3；② 各入口 JS 提案同果。

### G-11（R-04 判据补｜契约 §2:22 R8 后 JNI 面）

- **检查方法**：release（R8）变体 APK 上跑 `VisualJsIsolationInstrumentedTest` 至少"往返 + watchdog"两例；`unzip -l <release.apk> | grep libquickjs.so` 非空。
- **通过判据**：release 下 AIDL 往返返回 `Rendered`；watchdog 硬杀路径可用。
- **注意**：batch 24 E-03 release 冒烟仅冷启动（无 QuickJS 面），不能当作本门证据。

### G-12（R-05 判据补｜契约 §9:202/§2:22 许可证）

- **检查方法**：`find app/src/main/assets core/*/src/main/assets -type f | grep -i licen` 应含 quickjs-kt（MIT）与 QuickJS 许可条目——**当前 0 命中（真缺载体）**；钉版 commit/sha256 以契约 §2:22 记录为准回归核对。
- **通过判据**：许可文本文件存在于资产（或 NOTICE 汇总文件）且含 MIT/QuickJS 条目；`2026-10-25-16kb-arm64-api23-build.md` 的 sha 记录未漂移。

### G-13（A-07 判据补｜契约 §7:153）

- **检查方法**：`TutorVisualContextTest`（新增/扩展）：源码 65536+1 B、state 16384+1 B、诊断 2048+1 字符各一负向（构造器 require 已在 `TutorVisualContext.kt:50-62`）；集成侧断言"放不下时**不自动重试**"。
- **通过判据**：三条边界负向绿 + 不自动重试断言绿。

---

## 三、清单已有但判据不可执行的条目

| 清单行 | 不可执行点 | 可执行化（指向第二部分） |
|---|---|---|
| P-08（:128） | "dumpsys 审计""filesDir/网络绑定失败"无命令与测试名；Application"审计记录"无载体定义 | G-1 |
| P-09（:129） | "伪造身份或描述符方向"无 harness；客户端队列行为无测试名 | G-2 |
| P-04（:124） | "真实冷启动/沉降双态"——"沉降"无定义（E-01e 用约 2 min 等待，不可复现） | 定义：冷启动 = `am force-stop` 后立即触发；沉降 = force-stop 后 ≥2 min 再跑同用例（写明窗口并落 E-file） |
| P-05（:125） | 分支 2"文案不承诺硬上界"无机械判据 | `grep -rn "1500 ms\|硬上界\|硬终止" FigurePromptAssembler.kt TutorToolDescriptions.kt`（`core/data/model/`、`core/model/`）→ 0 命中或命中处均否定句（后者需人工确认并记录） |
| P-02（:122） | "或主会话书面等价判据"依赖裁定，非可执行（主判据"两 ABI 设备绿"本身可执行） | 保留主判据；等价判据分支须先有书面裁定才可用 |
| Q-05（:200） | 120 题盲测依赖真实 Provider 凭据（当前无凭据） | 维持 UNVERIFIED 登记；判据在真实 Provider 轨执行（沿用裁定） |
| R-01（:216）/R-02（:217） | "AVD/真机（设备）"未定义套件集 | 补口径：隔离 6/6 + KD-70 20/20 构成"运行时加载/执行/终止"判据；API 23 另需 `adb shell getprop ro.build.version.sdk` 记录 |
| R-03（:218） | 无 API≥35 口径、未引用批 24 E-04、未定义套件集 | G-6 |
| R-04（:219） | "冷启动/Worker/OCR"不含 §2:22 的 QuickJS JNI 面 | G-11 |
| R-05（:220） | 无许可证判据；且许可证载体当前真缺（L-7） | G-12 |
| S-06（:142）/E-05（:173） | "结构/位置一致"无比较机制 | 定义：同一 `RenderScene` 输入经 Compose 与 PDF 两 painter，比较画布指令序列序列化相等（或金样像素 diff，阈值先金样固定）；执行时写死 |
| Q-04（:199） | "独立预期"载体未定义 | 定义：fixture + expected 结构 JSON 断言（非人工目测截图） |
| A-10（:190） | 现状行过时（"图像通道未接 ledger"）；缺"两份最终图/文字降级"两条 | G-3 |
| A-07（:187） | 缺三预算负向与"不自动重试"断言 | G-13 |
| S-01..S-08、M-01..M-04、D-01..D-05、E-01..E-04、A-01..A-06/A-08/A-09、Q-01..Q-03/Q-06、V-01..V-04、X-01..X-03 | 验收用例列多为"新增 JVM/设备"未命名用例——可实现但无固定判据名；不在本文逐行展开，由 T4 按本表口径补名 | — |

---

## 四、增量表（供 T4 合并；不得与既有行重复）

| 建议行 | 动作 | 契约锚点 | 检查项 | 现状 | 验收判据（可执行） | 责任轨 |
|---|---|---|---|---|---|---|
| S-09 | **新增** | §3.2:58 | input/metrics 深冻结（JS 侧 freeze + Kotlin 侧不可变端口） | 未落；bootstrap 仅 `Object.freeze(Math)`（`QuickJsVisualExecutionEngine.kt:813`） | G-4 三断言 | `后续-C` + `24-T1` |
| P-12 | **新增** | §4:70 | programHash 熔断：不自动重跑、显式重试预算承继、源码变更解除 | 未落（无行） | G-7 三断言 | `24-T1` + `后续-W` |
| A-11 | **新增** | §7:151 | Route B 三协议 sourceJs 保真 7 向量 ×3 | 未落（协议文件在 `core/data/.../model/wire/`） | G-8 | `后续-W` |
| A-12 | **新增** | §8:186、§9:202 | 旧请求/逻辑操作指纹兼容门（EncodeDefault.NEVER/投影 + golden 扩测） | 部分基础已有（`ModelTasks.kt:736-742/:832-842/:1106`、`ModelTaskFingerprintStabilityTest`）；缺 visual 上下文扩测行 | G-5 | `后续-W` |
| A-13 | **新增** | §3.1:35、§1:7 | 全入口同管线：三个 `FigureGenerationRequest(` 构造点无私有路由 | 部分落（三入口均走 coordinator 建链，无逐入口断言行） | G-10 | `后续-W` |
| D-06 | **新增** | §8:188 | 旧 attachedImages cache-only resolver + 显式再生成 + 打开旧消息 0 派发 | 部分落（WG-05 再生成接线已测）；cache-only/0 派发无行 | G-9 | `后续-P` + `后续-W` |
| P-08 | **改判据** | §4:62 | 补 dumpsys 命令、queryContentProviders 断言、网络/filesDir 负向、logcat 佐证 | 行已有，判据无命令 | G-1 | `24-T1` + `24-设备` |
| P-09 | **改判据** | §4:64 | 补客户端队列 JVM 用例名、设备 BUSY/原帧用例、伪造身份/方向 harness | 行已有，判据无 harness | G-2 | `24-T1` + `24-设备` |
| A-10 | **改现状+判据** | §4:86/§7:155/§9:202 | 现状改"图像通道已接 ledger（coordinator :126/:253-281）"；补 128 KiB/6 布局/10 s/512 KiB 边界与"两份最终图/文字降级" | 行已有；现状过时、判据缺两条 | G-3 | `后续-W` |
| A-07 | **改判据** | §7:153 | 补 64/16/2 KiB 边界负向与不自动重试断言 | 行已有 | G-13 | `后续-W` |
| R-03 | **改判据** | §9:202/§2:22 | 补 API≥35 口径、套件集（隔离 6/6 + KD-70 20/20 + 页大小断言）、补引批 24 E-04 | 行已有 | G-6 | `后续-R` + `24-设备` |
| R-04 | **改判据** | §2:22 | 补 release R8 下 QuickJS JNI 面（隔离往返 + watchdog 两例） | 行已有 | G-11 | `后续-R` + `24-设备` |
| R-05 | **改判据** | §9:202/§2:22 | 补 quickjs-kt MIT/QuickJS 许可文本载体与核对命令（当前 assets 0 命中） | 行已有 | G-12 | `后续-R` |
| R-01/R-02 | **改判据** | §9:202 | 补套件集口径 | 行已有 | 见第三部分 | `后续-R` |
| P-04 | **改判据** | §4:72 | 定义冷启动/沉降窗口 | 行已有 | 见第三部分 | `24-T1` |
| P-05 | **改判据** | §4:72 | 补文案禁词 grep | 行已有 | 见第三部分 | `主会话` |
| S-06/E-05/Q-04 | **改判据** | §5/§8 | 定义一致性比较机制与 oracle 载体 | 行已有 | 见第三部分 | `后续-L`/`后续-Q` |

**合并红线**：不新增 P-08/P-09/P-10/P-11/A-10 重复行；不动 §0 勾选规则与 X-04 纪律；所有新行的"现状"以本文行号 + 附录 A 命令为准（行号漂移时按符号重定位）。

---

## 五、状态漂移登记（转交 T5，本文不改契约/台账）

1. **契约 §7:155 "当前图像通道尚未接ledger，必须补接"已过时**：`DefaultFigureGenerationCoordinator.kt:126/:253-281` 已注入并预留 ledger，`RemoteModelDispatchLedger.kt:17` 明写"同一逻辑操作最多 6 次；讲题留 1 次终答"、`:114` 有预算耗尽拒绝分支。是否改写契约该句由 T5/主会话裁定（契约文件不在本审计落盘范围）。
2. **清单 A-10 现状行同因过时**（增量表已给改写文案）。
3. **契约 §2:22"保留 MIT/QuickJS 许可证"无仓库载体**（app/core assets grep 0 命中）——按 L-7 处理或由主会话裁定载体位置（如 NOTICE 汇总）。
4. KD-76 仍 open（`docs/known-defects.md:2805`，本轮 grep 复核）——清单 P-07 引用有效，无漂移。

---

## 附录 A：本轮实跑命令与关键输出（全部只读；未跑 Gradle/adb/dumpsys）

```bash
git -C "D:/smart mistake book" log --oneline -5 -- docs/research/2026-10-26-batch-24-4e2-admission-checklist.md
# → aabae781（清单于批次 24 收口提交，非未跟踪）
git -C "D:/smart mistake book" show HEAD:docs/research/2026-10-26-batch-24-4e2-admission-checklist.md | grep -cE "^\| [PSMDEAQVRX]-[0-9]+ \|"   # → 63（HEAD 与工作树均 63）
grep -n "executeMutex" app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsClientEngine.kt      # → :84/:167
grep -n "inFlight\|BUSY" app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsService.kt         # → :85/:186-189/:200/:210/:235-236
sed -n '280,310p' app/src/main/kotlin/com/tingyun/smartmistakebook/SmartMistakeBookApplication.kt             # → guard（myUid/isIsolated）+ 自述"dumpsys 审计 UNVERIFIED"
grep -n "provider\|isolatedProcess\|visual_js" app/src/main/AndroidManifest.xml                               # → :64-72 仅 service；app 自身 manifest 无 <provider>
sed -n '60,75p' app/src/main/AndroidManifest.xml                                                              # → service exported="false"、isolatedProcess、:visual_js
grep -A3 "<provider" app/build/intermediates/merged_manifests/localFirstDebug/processLocalFirstDebugManifest/AndroidManifest.xml | grep "android:name"
# → BatchImportFileProvider / androidx FileProvider / MistakePdfFileProvider / MistakeDocxFileProvider / androidx.startup.InitializationProvider / MlKitInitProvider（均无 :visual_js process）
sed -n '795,830p' core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/QuickJsVisualExecutionEngine.kt
# → BOOTSTRAP_JS 全文：Date=undefined、Math.random 替换、Object.freeze(Math)、void 0——无 input/metrics 冻结（grep 冻结仅 :676 contentId 文案与 :813 Object.freeze(Math)）
grep -rln "RemoteModelDispatchLedger" core app | grep -v build   # → RoomFigureDispatchLedger、DefaultFigureGenerationCoordinator、ImageGenerationChannel、TutorFigureGenerator 等
grep -n "ledger\|Ledger\|预留" core/data/.../DefaultFigureGenerationCoordinator.kt                            # → :126 注入、:253-281 reserve→Granted
grep -n "6\b\|余额\|预留\|终答" core/domain/.../RemoteModelDispatchLedger.kt                                  # → :17（6 次上限+终答位）、:114（预算耗尽拒绝）
grep -n "explicitNulls\|encodeDefaults" core/model/.../ModelTasks.kt                                          # → :736-742、:832-842、:839-842（logicalOperationJson/legacyFingerprintJson）、:1106 旧 v9 注释
grep -n "@Test" core/model/src/test/.../ModelTaskFingerprintStabilityTest.kt                                  # → :52 起至少 15 个
grep -n "64\|KiB\|MAX" core/model/.../TutorVisualContext.kt                                                   # → KDoc :12（64/16/2 KiB）、:50-62 require
grep -rn "FigureGenerationRequest(" core/data/src/main | grep -v build                                        # → 恰 3 处：ConfiguredCleanImageGenerator.kt:100、AttachedImageGenerator.kt:155、TutorFigureGenerator.kt:377
grep -rln "supportsNativeTools = false" core/data/src/main                                                    # → wire/AnthropicMessagesProtocol.kt、GeminiGenerateContentProtocol.kt、OpenAiResponsesProtocol.kt
grep -n "cache\|重新生成" core/data/src/test/.../AttachedImageRegenerationWiringTest.kt                        # → :24（WG-05 显式再生成走真实生产协调器）
head -40 docs/research/evidence/2026-10-26-batch-24/step04-page-size-16k.md                                   # → E-04：ps16k_x86_64、API 36、16384 页、3/3 PASS（仅装载断言）
head -35 docs/research/evidence/2026-10-26-batch-24/step03-r8-release-smoke.md                                # → E-03：release 冷启动 ×2、API 34/x86_64、查 FATAL/StartupException/NoSuchMethod（无 QuickJS 面）
grep -n "device-api\|device-abi\|device-page-size" docs/research/evidence/2026-10-26-batch-24/step02-quickjs-oom.md  # → API 34 / x86_64 / 4096
find app/src/main/assets -type f                                   # → 仅 compose-richtext 与 open-claude-ui 许可 3 个文件
grep -rln "quickjs-kt\|QuickJS" app/src/main/assets core/data/src/main/assets                                # → 0 命中
grep -n "^## KD-76" docs/known-defects.md                          # → :2805（open）
find core -name "FigurePromptAssembler.kt" -not -path "*build*"    # → core/data/src/main/kotlin/.../core/data/model/FigurePromptAssembler.kt
```

## 附录 B：未验证项与限制

- **未运行任何设备命令**（adb/dumpsys/am instrument）、**未运行任何 Gradle 任务**：第二部分全部判据是供设备轨/JVM 轨执行的可复跑定义，本审计 0 项"通过"。dumpsys 佐证由 G-1 在设备轨产出。
- `FigurePromptAssembler.kt:40-43/:108-111`（A-04 引用）本轮只核实文件路径（`core/data/model/`），**未复跑行号**（T1/T2 在飞文件行号漂移风险，清单附录 A 已有同类提醒）。
- `ModelTaskFingerprintStabilityTest` 的 15+ 用例具体覆盖面未逐个阅读，仅确认存在与规模；A-12 落地前需先读该文件确定 golden 载体与扩测点。
- "契约 §7:155 过时"为**读码裁定**（证据见第五部分 #1），非主会话裁定；契约改写与否不在本审计权限内。
- 单一来源提示：本审计对契约语义的映射基于契约原文 + 清单原文 + 上述只读命令，无外部检索；与批次 24 R2/R3 研究的交叉点均引自清单行号，未重读 R3 全文。
