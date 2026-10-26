# 批次 22 基线核对（2026-10-26，基线核对员）

只读核对；**未运行 Gradle / 未编译 / 未执行任何测试或设备用例 / 未做 git 写操作**。所有行号、git 状态、计数均为本轮实读实测，命令见附录 A。对照计划：`.zcode/plans/plan-2026-10-26-batch-22-visualjs-process.md`（下称"计划"）。

排除面：工作树存在 `.worktrees/*`、`.artifacts/*`、`.jez/*`、若干 `build/`、`.gradle/` 历史副本；本报告的全仓 grep 均已 prune 这些目录（`find . \( -name .git -o -name .worktrees -o -name .artifacts -o -name .jez -o -name build -o -name .gradle \) -prune -o -name '*.kt' -print` → 1384 个 `.kt`），结论只基于主树。

---

## 1. HEAD / 工作树 / DB schema / 请求 schema / 70.json

| 项 | 实测 | 证据（命令/路径行号） |
|---|---|---|
| HEAD | `062e18f0b3e50303b96bd0f494caf63bf7091b1c`（分支 main） | `git rev-parse HEAD` |
| origin/main | `062e18f0b3e50303b96bd0f494caf63bf7091b1c`（**与 HEAD 相同**） | `git rev-parse origin/main` |
| 工作树 | **208 M + 453 ??；staged = 0** | `git status --porcelain \| cut -c1-2 \| sort \| uniq -c` → `208  M` / `453 ??` |
| DB schema | `STUDY_DATABASE_VERSION = 69`（`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabase.kt:134`；`version = STUDY_DATABASE_VERSION` 于 `:233`）；导出 schema 文件最高 **69.json**（目录内 67/68/69.json）；`StudyDatabase.kt` git 干净 | `grep -n` + `ls core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/` + `git status --porcelain -- <file>`（空） |
| 请求 schema | `TUTOR_VISUAL_CONTEXT_SCHEMA_VERSION = 18`、`CURRENT_SCHEMA_VERSION = TUTOR_VISUAL_CONTEXT_SCHEMA_VERSION`（`core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/ModelTasks.kt:546-547`）；`TutorVisualContextFingerprintTest.kt:65` 断言 18；**模块是 core/model**（不是 core/data）；文件与整个 core/model 零 M | `grep -rn TUTOR_VISUAL_CONTEXT_SCHEMA_VERSION`、`git status --porcelain -- core/model/.../ModelTasks.kt`（空） |
| 70.json 占用 | **未占用**（三路空输出）：`git ls-files '*70.json'` 空、`git status --porcelain -- core/database/schemas/` 空、`find . -name 70.json`（排除 .git）空 | 三路命令均 exit=0 且无输出 |

注意：DB 版本常量文件干净，但 **core/database 主源码另 5 文件 + 3 个 FTS androidTest 共 8 M**（§2 全路径）——70.json 的竞争风险载体是这些文件，不是 `StudyDatabase.kt` 本身。

---

## 2. 热区快照 vs 计划热区（计划 §0 第 2 条；计划未用 `HOT_ZONES` 标题）

### 2.1 模块级 M / ?? 计数（`git status --porcelain` 全表实数）

| 模块 | M | ?? | 备注 |
|---|---|---|---|
| feature/tutor | **36** | 20 | |
| feature/library | **26** | 7 | |
| feature/capture | **13** | 0 | |
| feature/review | **11** | 0 | 9 main + 2 test |
| feature/profile | 3 | 0 | 计划未列 |
| core/data | **23** | 0 | 见 §2.3 不符 |
| core/ui | **11** | 23 | build.gradle.kts + 9 main + 1 test；?? 含 `ClaudeMotion.kt` 等 |
| core/database | **8** | 0 | 计划未列 |
| core/export | **5** | 0 | |
| core/domain | **4** | 0 | |
| app | **21** | 4 | 10 androidTest + 8 main（含 Root 系列）+ 3 app test；?? 3 测试 + `app/src/main/assets/`（开源许可文本） |
| docs | 37 | 18 | current 7 / research 10 / superpowers 4 / history 3 / design 1 / specs 1 / docs 根 11 |
| tools | 6 | 12 | |
| 仓库根文件 | 3 | 0 | `.gitignore`、`dynamic_gui_architecture_plan.md`、`settings.gradle.kts` |
| knowledge-production | 1 | 12 | |

合计：89(feature)+51(core)+21(app)+37(docs)+6(tools)+3(root)+1(kp) = **208 M**；?? 453 条中 **97 条在模块/文档/工具目录下**，其余为仓库根散点（`.agent_audit_crops*`、`tmp*`、`nul`、`%TEMP%`、`out/` 等审计/临时产物）。

### 2.2 计划热区逐条对照

| 计划 §0 热区 | 当前实测 | 判定 |
|---|---|---|
| feature 四模块（review 仍多文件 M） | tutor 36 / library 26 / capture 13 / review 11 | ✓ 全部在飞；review 多文件 M 属实 |
| core/ui | 11 M + 23 ?? | ✓ |
| Root | `SmartMistakeBookRoot.kt`、`SmartMistakeBookDestinations.kt` M；app 共 21 M | ✓（但 app 脏面远大于 Root） |
| core/domain 他线 4 M | 4 | ✓ 精确 |
| core/data 两 M | **23 M** | **✗ 不符**（计划口径是批次 20/21 的 main 两文件；见 §2.3） |
| core/export 五 M | 5 | ✓ |
| settings.gradle.kts | M（内容见 §2.4） | ✓ |
| docs/current 其余 | 7 M | ✓ |
| （16KB）ps16k AVD 可用 | AVD 在盘但**当前未运行**（§8） | 需设备轨先启动 |

**计划热区未列、当前实脏（新增冲突面）**：

- **core/database：8 M**（70.json 竞争风险载体）
- **docs/research 10 M**、docs/superpowers 4 M、docs/history 3 M、docs 根散点 11 M、docs/design 1 M、docs/specs 1 M
- **tools 6 M**（`dense_build/gen_device_parity_fixture.py`、`dense_build/model-manifest.json`、`kb_build/merge_candidate_verdicts.py`、`kb_build/tables/alias_exceptions.csv`、`kb_build/tables/alias_map.csv`、`tests/test_kb_merge_candidate_verdicts.py`）
- **feature/profile 3 M**、**knowledge-production 1 M**、仓库根 `.gitignore` / `dynamic_gui_architecture_plan.md`

### 2.3 core/domain 开工即脏 M 全路径（4 个，全量点名）

1. `core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/BatchImportRepository.kt`
2. `core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/MistakeDetailRepository.kt`
3. `core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/SchedulingEvaluation.kt`
4. `core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/TutorJudgedReview.kt`

core/domain 无其他 M、无未跟踪；计划 §1 要动的 G5 落点 `AdaptiveQuestionSelector.kt`、`AdaptiveSelectionAssembler.kt`、`KnowledgeQuizItemStore.kt` 均 **git 干净**（逐个 porcelain 实测）。

**core/database 8 M**：`RoomBatchImportStore.kt`、`TutorConversationDatabaseContract.kt`、`dao/BatchImportDao.kt`、`dao/TutorConversationDao.kt`、`port/CleanRedrawTaskPort.kt`、`LibraryFtsCountPathInstrumentedTest.kt`、`LibraryFtsCountPathScale50kInstrumentedTest.kt`、`LibraryFtsRankingPathInstrumentedTest.kt`。

**core/export 5 M**：`build.gradle.kts`、`MistakeExportJobRunner.kt`、`MistakePdfEligibilityTest.kt`、`MistakePdfPagePlannerTest.kt`、`MistakePdfPlannerGoldenTest.kt`。

**core/data 23 M**：计划点名的 main 两文件仍在（`RoomMistakeDetailRepository.kt`、`RoomTutorToolRunner.kt`），另 **main 1**（`knowledge/dense/DenseRecallAssembly.kt`）+ **androidTest 5**（BatchImport/SplitImport×2/ModelTask/StudySubmission）+ **test 4**（`GoldenRetrievalJvmTest.kt`、`DenseRecallRerankerTest.kt`、`OpenAiNativeToolsProtocolTest.kt`、`TutorToolRequestDualParseTest.kt`）+ **资源/夹具 11**（`assets/dense/*` 3、`resources/knowledge/*` 4、`resources/dense/*` 4）。批次 21 基线记 core/data 测试面 8 M，本日扩到 23 M，且新增了 main `DenseRecallAssembly.kt` 与 4 个资源文件。

### 2.4 settings.gradle.kts 的实质：他线正在开新 Gradle 模块

`git diff -- settings.gradle.kts`（唯一 hunk）：在 include 列表 `":core:ui",` 后新增 **`":core:markdown",`**。对应未跟踪目录 `core/markdown/`（`build.gradle.kts`、`LICENSE`、`README.md`、`upstream-manifest.json`；`namespace = com.tingyun.smartmistakebook.core.markdown`、`compileSdk = 37`、compose、`richtext-ui:0.17.0`、`coil-compose:2.4.0`）。

这条与计划 §2「AIDL/进程层新增只在 app 模块（settings.gradle.kts 禁改；不开新 Gradle 模块）」**直接相邻冲突**：虽不碰批次 22 落点，但若该线先落 `:core:markdown`，后续任何 `settings.gradle.kts` 相关提交/门禁都会与批次 22 的"禁改"约束在同一文件上相遇。

---

## 3. 批次 22 落点脏态逐个核对

`git status --porcelain -- <path>` 逐个实测，全部**空（干净）**：

| 落点 | 状态 | 补充事实（本轮实读，非脏态） |
|---|---|---|
| `app/build.gradle.kts` | 干净 | 182 行；buildTypes 见 §7 |
| `app/src/main/AndroidManifest.xml` | 干净 | 65 行；当前**无 `<service>`、无 `android:process`/`isolatedProcess`**；全仓 `isolatedProcess` 只出现在引擎 KDoc `QuickJsVisualExecutionEngine.kt:84` |
| `app/src/main/kotlin/.../SmartMistakeBookApplication.kt` | 干净 | `class SmartMistakeBookApplication : Application()` 于 `:105`，`override fun onCreate()` 于 `:285`；当前无 `Process.myUid()` 隔离守卫（`grep` 零命中） |
| `core/data/.../visual/QuickJsVisualExecutionEngine.kt` | 干净 | 666 行（结构见 §5） |
| `core/data/.../study/KnowledgeQuizItemStore.kt` | 干净 | 289 行（UNKNOWN 写入点见 §6） |
| `gradle/libs.versions.toml` | 干净 | kotlin 2.3.21（`:3`）、coroutines 1.11.0（`:10`）、quickjs-kt-android 1.0.15（`:51`）、transitive stdlib pin 2.4.10（`:56`） |
| 根 `build.gradle.kts` | 干净 | — |
| `gradle.properties` | 干净 | — |

**额外（不在 8 个落点内，但计划明令禁改）**：`settings.gradle.kts` **M**（§2.4）；`app/src/main/assets/` **未跟踪**（3 个 open-source LICENSE/NOTICE 文本，他线产物）。

**供应链基线复核（计划 §0 最后一项，顺带核实）**：`gradle/verification-metadata.xml` 不存在、`gradle/dependency-locks` 不存在、`find -maxdepth 3 -name '*.lockfile'` 空——**确实没有 dependency locking / verification-metadata**；`tools/ci/check_dependency_pins.py` 存在（7970 字节，10-26 05:15）。计划 §0 该条现状属实。

---

## 4. 契约原文与批次 21 记录 §6 第 9–14 项

契约文件：`docs/current/visual-generation-contract.md`（201 行，**git 干净**——批次 21 的 RELRO 改写已在 `1a6bf486` 提交）。

### 4.1 §3.2 JS 后端协议（`:43-58`）关键原文

- `:47`：「代码声明 `const visual = { prepare(input), render(input, metrics), reduce(state, event) }`；SDK 在同一全局上下文提供纯函数图形帮助方法。模块加载器、主机对象和 import 均不开启。bootstrap 在用户源码前运行，封闭 Date、Math.random 等隐式时钟/随机源；随机必须使用 input.seed 的 SDK PRNG。用户源码永远视为不可信，bootstrap 限制不能替代进程隔离和输出验证。」
- `:49`：「`FrameInput` 包含 `{programId, revision, state, viewport, timeMs, seed, theme}`；只含本图数据，无密钥、凭据、文件路径或全库对象。一次布局事务冻结这些值：」
- `:51-54` 四步事务：新建上下文执行 prepare → 关闭；原生验证/度量；**新建上下文**重新执行 render；原生严格校验、共享 compiler 编译、原子提交 RenderScene；reduce 同样使用新上下文。
- `:56`：「每事务最多一次 prepare、一次 measure batch、一次 render。……前端不得在 draw 回调执行 JS 或解析公式。」
- `:58`：「阶段能力按4E.1统一管线/静态后端、4E.2交互、4E.3复杂源与保真扩展分别声明……未启用的控件/事件/模式在模型能力上下文中不广告，解析与执行也同时拒绝，不仅隐藏 UI。」

### 4.2 §4 隔离、IPC 与预算（`:60-70`）终止相关原文

- `:62`：「新增非导出 Service，`android:isolatedProcess="true"`、专用 `:visual_js` 进程。Application 必须在打开 Room、恢复备份、维护文件、创建 Provider/密钥工厂前，用公开 API `Process.myUid() != applicationInfo.uid` 短路；同时审计构造/字段与 companion 初始化、实际进入该进程的 ContentProvider/AndroidX Startup，不能只在 onCreate 最前面加 guard 就宣称已隔离全部启动路径。隔离进程不申请文件/网络主机绑定。」
- `:64`：「宿主通过 AIDL 发控制消息，源码和 JSON 经有界 ParcelFileDescriptor 管道传输；读写循环均计字节、可取消，不逐图元发 Binder。每次任务有 requestId、generation、phase；全局一个在飞绘制、最多两个待绘 program……消费者须验证服务调用身份、描述符方向与长度，不暴露宿主通用 RPC。」
- `:66`：「JS 在服务专用单线程 dispatcher 上执行，不能因 evaluate 是 suspend 就假定离开主线程。**引擎 timeout 是协作中断，close 可能等待引擎锁，均不是硬终止。**」
- `:68`：「**独立 watchdog 线程在每个 JS 阶段开始前启动；硬超时由服务自己 `killProcess(myPid())`。** AIDL `oneway terminate(generation)` 在 Binder 控制线程先原子核对当前 generation，再自杀，不排在 JS 执行队列；过期 terminate 必须 no-op。watchdog 捕获同一执行令牌，正常完成/取消先使令牌失效，迟到定时器不得杀下一任务。宿主先废弃 generation，再请求终止/解绑，通过 Binder death 确认旧实例终结后最多重建一次；不假定宿主能杀不同 UID，也不把 unbind 当同步终止。死亡未确认时展示 fallback，不能并发放大进程数。」
- `:70`：「重建只恢复运行环境，不自动重跑刚才超时/崩溃的源码。该 programHash 熔断至用户明确本地重试或源码改变；本地重试仍受相同预算。……绝不 kill 宿主或声称所有 native 调用都有硬期限。」
- `:78` 预算表：「单 JS 阶段协作期限 / 服务硬期限 / 宿主阶段期限 | 100 ms / 1500 ms / 2500 ms；期限不是实时调度保证」

**关于"三层"的原文事实**：契约正文**没有**出现"三层"字样（`grep 三层 docs/current/visual-generation-contract.md` 零命中）。三层编号来自二手文档，且**编号互相矛盾**：

- `docs/research/2026-10-25-js-engine-selection.md:94-100`「终止 API 三层（契约 §4，visual-generation-contract.md:66-70）」：① 协作（evaluationTimeoutMillis/interrupt/协程取消）；② **服务硬期限：`:visual_js` 内 watchdog + `killProcess(myPid())` + AIDL terminate**；③ 宿主（废弃 generation → unbind → Binder death → 最多重建一次 → 熔断）。
- `docs/agent-first-refactor-decisions-2026-09-23.md:3249`：「硬终止三层：库只提供协作层，进程级 watchdog+killProcess 按契约 §4 自建」。
- 批次 21 记录 `:128` 与 `:246`：把进程级 `isolatedProcess`+AIDL+`killProcess` 称作「契约 §4 **第三层**」「唯一进程级终止层」。
- 引擎 KDoc `QuickJsVisualExecutionEngine.kt:72-87` 自述「三层落两层」：协作层 / 宿主硬期限层（watchdog 强弃）/ **未做=进程级**。

即：计划 §0 与批次 21 记录用"第三层=进程级"；研究文档把进程级编为第 ② 层、宿主侧编为第 ③ 层。**含义一致（进程级是唯一能杀任意循环的层），编号不一致**，引用时需统一口径。

### 4.3 第 22 行 RELRO 新措辞（逐字）

`docs/current/visual-generation-contract.md:22`（整行为一个 bullet，节选判据句，逐字）：

> 发布 AAR（quickjs-kt-android 1.0.15）四 ABI 的 PT_LOAD 均为 16 KB 对齐；GNU_RELRO 终点非 16 KB 整倍数（arm64 余 4096、x86_64 余 12288）但 RELRO 独占 LOAD 段（余数页内无可写段），按 [Android 官方检查要求](https://developer.android.com/guide/practices/page-sizes#check-relro) 明文豁免判合规——"If the GNU_RELRO segment is in its own LOAD segment, the non-zero value returned is still compliant"——不构成阻断门；逐 ABI 实测与判据原文见 [16KB 研究](../research/2026-10-25-16kb-arm64-api23-build.md) §2.4/§1.5。源码重建前置改判为消费发布 AAR（主会话裁定 2，2026-10-25：钉版+构件 sha256 记录，自建 AAR 仅作运行时实测失败回退）；16 KB 真机加载、API 23/arm64 运行时、R8 后 JNI 面仍待设备轨验证，仍是启用前门。

### 4.4 批次 21 记录 §6 第 9–14 项（逐条原文，`docs/research/2026-10-25-batch-21-record.md:246-251`）

> 9. **issue #240**：钉版 OPEN；`execute()` 在自续 job 下永不返回，`close()` 卡死；两层终止不能给调用方上界；`inFlight` 不复位、后续 BUSY。4E.2 隔离进程 + AIDL + `killProcess` 是唯一进程级终止层（KD-66）。
> 10. **OOM 路径未验证**：x86_64 实际 INVALID_PROGRAM（string too long / prepare null），契约 `MEMORY_LIMIT` 未成立；`classifyJsException` 消息启发式（KD-70）。
> 11. **16KB 页大小断言未在设备执行**：新 `runtimePageSizeIs16KB` 在盘；step3 旧日志 `OK(2)` 无 `PAGE_SIZE=`；仓内无 `16384` 日志。设备轨数字以工单转录为准。
> 12. **设备轨 16KB 运行**：QuickJS 三方法 + 原生装载绿，但新 watchdog 形状未跑；`ro.product.page_size`/`ro.boot.hardware.cpu.pagesize` 本轮为空，与 R3 §1.4 不一致。
> 13. **回退自建 AAR 判据未裁决**：OOM 红更可能归因夹具/库异常消息；issue #240 是上游缺陷；未归因 AAR 构件。
> 14. **4E.2/4E.3 未做**：隔离进程、AIDL、共享 SceneV1 compiler、交互/保真升级均未落地；JS 仍 UNAVAILABLE、不广告。

补充（题面括号写"KD-66 到 KD-71"，但 9–14 项只覆盖 KD-66/70；KD-71 在第 15 项）：

> 15. **close/execute 竞态**（KD-71，低）。——`:252`

### 4.5 KD-66…KD-71 条目位置（`docs/known-defects.md`，git 干净）

| 条目 | 行 | 标题要点 |
|---|---|---|
| KD-66 | `:2573-2581` | QuickJS issue #240 使硬期限不能返回；高 |
| KD-67 | `:2583-2591` | G5-A 生产候选全部 contentVerified=false；中 |
| KD-68 | `:2593-2601` | 新题积压 UI 用 preview 而非 previewBacklog；中 |
| KD-69 | `:2603-2611` | internal/benchmark minify 无 proguardFiles（§7 全文抄录） |
| KD-70 | `:2613-2621` | QuickJS 内存上限在 x86_64 落 INVALID_PROGRAM；中 |
| KD-71 | `:2623-2631` | close/execute 竞态窗口；低 |

---

## 5. QuickJS 引擎现状（`QuickJsVisualExecutionEngine.kt`，666 行，git 干净）

### 5.1 execute / close / inFlight 结构行号

| 结构 | 行 |
|---|---|
| 类声明与三个可注入参数 | `:107-116` |
| `closed` / `inFlight` / `fusedProgramDigests` / `currentTransaction` | `:118` / `:119` / `:120-121` / `:122` |
| watchdog 单线程执行器（线程名 `quickjs-visual-watchdog`，daemon） | `:124-126` |
| **`execute()` 全函数** | **`:128-201`** |
| ├ closed 早检 | `:129-131`、`:153-155` |
| ├ 源码预算 → INPUT_OVER_BUDGET | `:132-138` |
| ├ state 预算 | `:139-145` |
| ├ digest 熔断 → PROGRAM_FUSED | `:146-152` |
| ├ **`inFlight.compareAndSet`** → BUSY | `:156-161` |
| ├ `Transaction` 构造 + **`currentTransaction.set`** | `:162-163` |
| ├ watchdog 定时（hardDeadlineMillis） | `:164-168` |
| ├ `withTimeout(hard + 500ms)` | `:173-175`（grace 常量 `HOST_AWAIT_GRACE_MILLIS = 500L` 于 `:641`） |
| ├ HARD_DEADLINE_EXCEEDED → 熔断返回 | `:176-180` |
| ├ `TimeoutCancellationException` → forceAbandon + 熔断 | `:182-192` |
| ├ `CancellationException` → forceAbandon 后重抛 | `:193-195` |
| └ `finally`：取消定时器 / 清 currentTransaction / **`inFlight.set(false)`** | `:196-200`（复位在 `:199`） |
| **`close()`**：`getAndSet` 幂等 → `currentTransaction.get()?.forceAbandon()` → `watchdog.shutdownNow()` | **`:203-207`** |
| `runTransaction()` | `:211` 起 |
| 每事务专用单线程 dispatcher（线程名 `quickjs-visual-js`，daemon） | `:216-219` |
| `Transaction` 内部类 / `onHardDeadline()` / `forceAbandon()` | `:595` / `:610` / `:616` |

### 5.2 issue #240 KDoc 与三层终止 KDoc

- 三层终止 KDoc：`:72-87`；其中**未做的第三层**明确写于 `:84-87`：「未做（登记为 4E.2 前置）：契约 §4 的第三层——`android:isolatedProcess="true"` 的 `:visual_js` 进程、AIDL 控制通道与 `killProcess(myPid())` 硬终止」。
- **issue #240 KDoc：`:89-96`**——`:89` 链接 `https://github.com/dokar3/quickjs-kt/issues/240`；`:90` 「OPEN/零评论/无修复 PR @ 1.0.15」；`:90-91` 机制（job 先 `Promise.resolve().then(f)` 排队下一个再自旋 → evaluate 永不返回、close 卡死）；`:92-96` 爆炸半径与 accept-the-leak。
- 错误面（`out of memory`/`not defined`/其余 → MEMORY_LIMIT/SANDBOX_VIOLATION/INVALID_PROGRAM）：`:98-102`；close 语义：`:104-105`。

### 5.3 watchdog 测试形状行号（`QuickJsVisualExecutionEngineInstrumentedTest.kt`，500 行）

- 文件 KDoc 用例映射（含 issue 240 一条）：`:33-49`；issue 240 行：`:44-49`。
- **watchdog/issue240 形状用例**：`@Test` `:305`，`fun issue240SelfQueuingJobIsKnownOpenAndLeaksOnlyDaemonThreads()` `:306-347`：
  - engine 配置 `cooperativeTimeoutMillis = 100L, hardDeadlineMillis = 600L`：`:308`
  - 观察形状注释（独立 daemon 线程 + join 超时，不在 Instr 线程直跑）：`:310-313`
  - `thread(isDaemon = true, name = "issue240-evaluate")` + `runBlocking { engine.execute(...) }`：`:315-324`
  - `done.await(5, TimeUnit.SECONDS)` + `assertFalse`（"returned within 5s ⇒ 上游已修，必须反转断言"）：`:328-333`
  - 第二次请求断言 `BUSY`：`:338-342`
  - 不调用原始 `QuickJs.close()`、engine.close 走守护强弃：`:344-346`
  - 夹具 `ISSUE_240_SOURCE`：`:447-448` 起
- JVM 测试仅校验无原生库面的描述（`QuickJsVisualExecutionEngineJvmTest.kt:23`：「完整执行语义（渲染/协作中断/内存/沙箱/熔断/issue 240）没有原生库无法验证」）。

### 5.4 `NativeLibraryPageSizeInstrumentedTest`（70 行，git 干净）新断言行号

- 类 KDoc（16KB 门只由本用例断言、4KB skip）：`:12-27`
- 旧两例装载：`:31-35`（quickjs）、`:37-42`（LiteRt + litert_jni）
- **新用例 `runtimePageSizeIs16KB()`：`:49-59`**；逐行：`fun` `:50`、`println("PAGE_SIZE=")` `:52`、`assumeTrue(pageSize == 16384L)` `:53`、`assertEquals(16384L, pageSize)` 与失败文案 `:54-58`
- 辅助 `assertPageSizeSane()`：`:61-69`

**UNVERIFIED**：本轮未运行任何设备用例，以上仅为源码形状。

---

## 6. G5 现状

| 事实 | 位置 | 说明 |
|---|---|---|
| `AdaptiveSelectionAssembler` 是 `internal` | `core/data/.../study/AdaptiveSelectionAssembler.kt:73`（`internal class AdaptiveSelectionAssembler(`） | 368 行，git 干净 |
| 接线状态 KDoc | `:54-60`：「本类是 `internal`，feature/review 是另一个 Gradle 模块，**无法直接实例化**；全仓除本文件 KDoc 与测试外零引用，生产调用点不存在。」 | 与实测一致 |
| 死端 KDoc | `:62-69`：「唯一的生产题面写入方 `KnowledgeQuizItemStore.record` 固定 `verificationStatus = UNKNOWN`……两个候选源都会落到 `CLARIFY_OR_CONFIRM`。」 | — |
| 零生产引用（实测） | 全仓 1384 个 `.kt` 扫描，`AdaptiveSelectionAssembler` 只出现在：自身 `:73`；测试 `AdaptiveSelectionAssemblerTest.kt:51/355/399/415/453`；注释 `EloCorrectnessPredictor.kt:191` | feature/app 零实例化 |
| `verificationStatus=UNKNOWN` 写入点 | `KnowledgeQuizItemStore.kt:176`（`verificationStatus = StudyDbValue.VerificationStatus.UNKNOWN,`），说明注释 `:173-175`；`record()` 于 `:142`；字段声明 `:259-260` | 唯一生产写方 |
| 选择器 `contentVerified` 门 | `AdaptiveQuestionSelector.kt:239-245`（`safeBaseCandidates`），判据 `candidate.contentVerified &&` 在 `:241` | 288 行 `hasUnverifiedTarget` |
| `CLARIFY_OR_CONFIRM` 分支 | `AdaptiveQuestionSelector.kt:283-296`（`noCandidateDecision`）：`:288` 判 `!candidate.contentVerified`，`:292` 返回 `CLARIFY_OR_CONFIRM`，reason `TARGET_CONTENT_NOT_VERIFIED`（`:293`） | 枚举常量 `:23`；`select()` `:145` |
| 回归钉 | `AdaptiveSelectionAssemblerTest.kt:355` `fun 'assembled production candidates stay fail-closed to clarify until verified'`；断言 `:385` | 注释 `:382-384` 明写"没有核验写方、feature/app 也没有消费者" |

**消费者搜索（feature/app 零命中复核）**：

1. `git grep -n "CLARIFY_OR_CONFIRM" -- '*.kt'` → 6 命中 / 3 文件：`AdaptiveSelectionAssembler.kt:63,131`、`AdaptiveSelectionAssemblerTest.kt:382,385`、`AdaptiveQuestionSelector.kt:23,292`。
2. 全仓非排除 .kt 扫描（1384 文件，含全部未跟踪 `.kt`）→ **同样只这 3 个文件**。
3. 结论：**feature/** 与 **app/**（含 androidTest、未跟踪文件）**零命中**；生产消费者不存在。

---

## 7. R8 现状（`app/build.gradle.kts`，182 行，git 干净）

| buildType | 行 | 关键配置 |
|---|---|---|
| `benchmark` | `:58-64` | `matchingFallbacks += listOf("release")` `:59`；`isDebuggable = false` `:60`；`isProfileable = true` `:61`；`signingConfig = debug` `:62`；**`isMinifyEnabled = true` `:63`；无 `proguardFiles`；无 `isShrinkResources`** |
| `internal` | `:65-70` | `matchingFallbacks += listOf("release")` `:66`；`isDebuggable = true` `:67`；`signingConfig = debug` `:68`；**`isMinifyEnabled = true` `:69`；无 `proguardFiles`；无 `isShrinkResources`** |
| `release` | `:71-79` | `signingConfig = release` `:75`；`isMinifyEnabled = true` `:76`；`isShrinkResources = true` `:77`；**`proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")` `:78`** |

三者差异一句话：**三者都 minify=true；只有 release 配了 proguardFiles + shrinkResources；benchmark 额外 `isProfileable=true` 且不可调试，internal 可调试**。`app/proguard-rules.pro` 存在（1334 字节，Sep 30）。

**KD-69 条目原文**（`docs/known-defects.md:2603-2611`）：

> ## KD-69 (open, 2026-10-26 登记（批次 21 设备轨步五/复核发现 #12；低）) · `internal`/`benchmark` buildType minify=true 但无 proguardFiles：R8 剥掉 WorkManager/Room 生成类，启动 FATAL
>
> **Symptom（设备实跑）.** `:app:assembleLocalFirstInternal`（minify=true、无 proguardFiles）装机启动崩溃：`java.lang.RuntimeException: Unable to get provider androidx.startup.InitializationProvider` ← `androidx.startup.StartupException: java.lang.NoSuchMethodException: androidx.work.impl.WorkDatabase_Impl.<init>` ← `Room.getGeneratedImplementation`（`build/device-track-batch21/step5-r8-logcat.txt:372-410`）。根因：`app/build.gradle.kts:58-70` 的 `benchmark`（`:58-64`）与 `internal`（`:65-70`）只设 `isMinifyEnabled = true`，没有 `proguardFiles(...)`；只有 release（`:71-80`）配了默认规则 + `proguard-rules.pro`。同批 release 冒烟：BUILD SUCCESSFUL 3m24s、装机启动 PID=13393、523 行 logcat 中 FATAL/ANR/ClassNotFound 等全 0（`step5-r8-release-logcat.txt`）。
>
> **Impact.** low（仅内部/基准构建；不进 release 包，但会让 minified debug 冒烟假红/无法用于验证）。
>
> **Fix direction.** 给 `internal`/`benchmark` 补 `proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")`（或关闭其 minify）；补 `assembleLocalFirstInternal` 启动冒烟。改构建配置属共享文件/他线风险，先与主会话确认再动。
>
> **Reopen condition.** `internal` minified 构建装机启动无 `WorkDatabase_Impl`/Room/WorkManager 生成类缺失后关闭；release 冒烟不得回退。

核对：KD-69 对 benchmark/internal 行号与 proguardFiles 缺失的记述与当前树**完全一致**；唯一小出入是 release 块写作 `:71-80`，实际内容 `:71-79`（`:80` 是 buildTypes 收尾大括号）。

---

## 8. 模拟器状态（只读查询）

环境事实：`adb` / `emulator` **不在 PATH**（`adb: command not found`、`emulator: command not found`，exit=127）；`ANDROID_HOME=C:\Android\Sdk`。使用绝对路径执行：

`/c/Android/Sdk/platform-tools/adb.exe devices`（exit=0）：

```
List of devices attached
emulator-5554	device
emulator-5562	device
emulator-5584	device
```

`/c/Android/Sdk/emulator/emulator.exe -list-avds`（exit=0）：`audit_user_visible_20261016`、`kb_test_device`、**`ps16k_x86_64`**、`test_device`。

| 项 | 实测 | 证据 |
|---|---|---|
| emulator-5554 是否存在 | **存在**，state=`device` | `adb devices` |
| emulator-5554 是哪个 AVD | **`test_device`**（`adb -s emulator-5554 emu avd name` → `test_device`） | — |
| emulator-5554 页大小 | **4096（4KB）**：`adb shell getconf PAGE_SIZE` → `4096`；`ro.product.page_size` / `ro.boot.hardware.cpu.pagesize` **空**；API 34、x86_64 | 只读 getprop/getconf |
| ps16k_x86_64 AVD 是否存在 | **存在**：`~/.android/avd/ps16k_x86_64.avd/config.ini` → `image.sysdir.1 = system-images\android-36\google_apis_ps16k\x86_64\`、`tag.id = page_size_16kb`、`abi.type = x86_64` | `ls ~/.android/avd/` + `grep config.ini` |
| ps16k 是否在跑 | **未运行**：三个在跑设备分别是 `test_device`、`audit_claude_coverage_20261024`、`kb_test_device`，均 4KB | `adb emu avd name` / `getconf PAGE_SIZE` |
| 其余 | emulator-5562 的 AVD 名 `audit_claude_coverage_20261024` **不在** `~/.android/avd/` 也不在 `-list-avds` 输出（陈旧名）；三个在跑设备 `ro.product.page_size`/`ro.boot.hardware.cpu.pagesize` 全空，与批次 21 记录 §6 第 12 项一致 | — |

本轮未启动/停止任何模拟器、未安装/运行任何 APK。

---

## 9. 与计划 §0 不符处汇总

1. **`core/data 两 M` → 实际 23 M**（本轮最实质的数字不符）。main 两文件仍在，但新增 main `DenseRecallAssembly.kt` + 测试面 9 + 资源/夹具 11；若按"core/data 只避两文件"排提交，会把 21 个他线文件卷进来。
2. **`settings.gradle.kts` 不干净且实质是"开新 Gradle 模块"**：diff 新增 `":core:markdown"`，配套未跟踪 `core/markdown/`（richtext 0.17.0 + coil）。计划 §2 明令 `settings.gradle.kts` 禁改、不开新模块——该线与本批约束正面相遇，需在提交/门禁时显式排除。
3. **热区清单漏列实脏面**：core/database 8 M（70.json 载体）、docs/research 10 M、docs/superpowers 4 M、docs/history 3 M、docs 根 11 M、docs/design 1 M、docs/specs 1 M、tools 6 M、feature/profile 3 M、knowledge-production 1 M、根 `.gitignore`/`dynamic_gui_architecture_plan.md`；app 实为 21 M（计划只点名 Root）。
4. **热区热度相对批次 21 基线继续上升**：tutor 28→36、library 19→26、capture 10→13、review 10→11、core/ui 10→11、app 13→21。计划"热区在飞"判断仍成立，但可用余地更小。
5. **"契约 §4 第三层"编号无契约原文支撑**：契约无"三层"字样；研究文档 `js-engine-selection.md:94-100` 把进程级 killProcess 编为第 ② 层、宿主侧为第 ③ 层，而计划 §0/批次 21 记录把进程级称"第三层"。含义一致、编号冲突，建议引用时注明"唯一进程级终止层"而非层号。
6. **`ps16k AVD 可用` ≠ 当前可用**：AVD 在盘（android-36 google_apis_ps16k），但当前只跑着三台 4KB x86_64 设备；emulator-5554 是 `test_device`（4096），不是 ps16k。设备轨需先启动 ps16k。
7. **KD-69 的 release 行号 `:71-80` 与实际 `:71-79` 有 1 行出入**（内容一致，不影响修复）。
8. 其余计划 §0 断言**逐项属实**：HEAD=批次 21 收口提交且与 origin/main 同点；8 个批次 22 落点全净；core/export 5 M；core/domain 4 M；docs/current 7 M；无 dependency locking / verification-metadata；AAR 拉 kotlin-stdlib 2.4.10 覆盖编译 2.3.21（`gradle/libs.versions.toml:3/51/53-56`）；KD-70 的 x86_64 INVALID_PROGRAM 与 KD 条目一致。

**UNVERIFIED / 本轮未做**：未跑 Gradle 任何任务（单测门/强门/assemble 均未跑）；未跑任何 JVM/androidTest/设备用例（§5、§6、§8 的设备数字全部引批次 21 记录或本轮 adb 只读查询）；未验证 R8 修复效果、未验证 16KB 真机装载、未验证 OOM 分类；未做 `git fetch`（origin/main 为本地远端跟踪引用，可能与远端实时不一致）。

---

## 附录 A：本轮实际执行的只读命令（可复跑）

```bash
git rev-parse HEAD; git rev-parse origin/main
git status --porcelain | cut -c1-2 | sort | uniq -c          # 208 M / 453 ??
git status --porcelain | grep '^ M' | sed 's/^ M //' | sort
git status --porcelain | grep '^ M' | sed 's/^ M //' | awk -F/ '{print $1"/"$2}' | sort | uniq -c
git status --porcelain | grep '^??' | sed 's/^?? //' | cut -d/ -f1 | sort | uniq -c
git status --porcelain -- <每个落点>
git diff -- settings.gradle.kts
git ls-files '*70.json'; git status --porcelain -- core/database/schemas/; find . -name 70.json -not -path './.git/*'
grep -n STUDY_DATABASE_VERSION= core/database/.../StudyDatabase.kt
ls core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/
grep -rn TUTOR_VISUAL_CONTEXT_SCHEMA_VERSION --include=*.kt .
find . \( -name .git -o -name .worktrees -o -name .artifacts -o -name .jez -o -name build -o -name .gradle \) -prune -o -name '*.kt' -print   # 1384
git grep -n "CLARIFY_OR_CONFIRM" -- '*.kt'
git grep -n "AdaptiveSelectionAssembler" -- '*.kt'
# 对 1384 个 .kt 逐个 grep（CLARIFY_OR_CONFIRM / AdaptiveSelectionAssembler）
grep -n "isolatedProcess\|android:process\|<service" app/src/main/AndroidManifest.xml
"/c/Android/Sdk/platform-tools/adb.exe" devices
"/c/Android/Sdk/emulator/emulator.exe" -list-avds
"/c/Android/Sdk/platform-tools/adb.exe" -s <serial> emu avd name
"/c/Android/Sdk/platform-tools/adb.exe" -s <serial> shell getconf PAGE_SIZE
"/c/Android/Sdk/platform-tools/adb.exe" -s <serial> shell getprop ro.product.page_size
grep -n "buildTypes\|create(\|isMinifyEnabled\|proguardFiles\|isShrinkResources" app/build.gradle.kts
```

## 附录 B：本报告使用的"干净"定义

"git 干净" = `git status --porcelain -- <path>` 无输出（不代表内容无缺陷）；"行号" = 本工作树当前物理行号（HEAD `062e18f0` + 208 个他线 M，故他线再改同一文件时行号会漂）。
