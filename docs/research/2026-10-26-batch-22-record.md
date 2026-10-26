# 批次 22 记录（2026-10-26）：4E.2 前置——隔离 `:visual_js` 进程层 + KD-66~71 按证据核对 + 供应链加固

> 记录时点：2026-10-26（+08:00）。本文件由记录员在共享工作树 `D:\smart mistake book` 只读核对计划、基线、四研究文档、当前源码/测试、`git status`、`build/` 产物与批次材料后写入。**记录员未运行 Gradle、未运行 adb/am instrument、未执行任何 git 写操作、未提交/推送、未改生产代码。**
> 计划：`.zcode/plans/plan-2026-10-26-batch-22-visualjs-process.md`（下称"计划"）。
> 基线：`docs/research/2026-10-26-batch-22-baseline.md`（下称"基线"）。
> **门禁状态（工单给定）**：单测门第 1 轮 `exit=0`；修复环复跑 `exit=0`；强门第 2 轮 `exit=0`；修复环第 1 轮收尾"单测门复跑 `exit=0`"。记录员未复跑 Gradle，且仓内无本批门禁日志（§4），故不把"门禁全绿"写成记录员实证。
> **本记录不声称"批次全绿"**：T1 设备功能门 4/4 红（修复前 APK）、修复后未复跑；T2 设备 20 例仅 P2 红、KD-70 保持 open；T3 无任何锁文件、locking 实际未生效（KD-72），stdlib 降级只有 AAR 局部字节码证据（KD-73）；T4 只有核心半边、生产未接线；T5 设备冒烟数字无日志（KD-74）；T6 设备材料称两例通过但无日志、KD-71 保持 open；T7 只改文档；JS 仍 UNAVAILABLE、不广告；真实 Provider UNVERIFIED。

## 1. 范围与基线

### 1.1 计划范围（计划 §1）

- **做**：R1 隔离进程/AIDL/killProcess 工程化研究；R2 依赖锁定与 kotlin-stdlib 兼容判定；R3 QuickJS memoryLimit/OOM 语义与确定性探针；R4 debug R8 WorkManager/Room 根因与官方 keep 规则；T1 **4E.2 前置隔离进程层**（`:visual_js` 进程 Service + AIDL + generation 核对 + watchdog + killProcess + 主进程 client 实现 `VisualExecutionEngine` + death recipient 恢复；JS 仍不广告）；T2 KD-70 OOM 探针与分类修复；T3 供应链加固（dependency locking 或 verification-metadata + kotlin-stdlib 决策落地 + CI 门加强）；T4 KD-67 G5 生产半边（核验写方 API + CLARIFY 消费契约 + assembler 可注入 seam；feature UI 留）；T5 KD-69 debug R8 修复（T1 后，共享 `app/build.gradle.kts` 串行）；T6 KD-71 竞态修复（T2 后，共享引擎串行）；T8 条件轨（开工核热区：feature/library 干净→AWAITING_VERIFICATION UI 态；feature/tutor+core/ui 干净→G1/G2；SecondaryScreens 干净→G7 嵌入；否则登记）；设备轨（ps16k + QuickJS watchdog/OOM + XMP 仪器化 + R8 debug/release + 备份迁移抽验）；门禁/双复核/记录/提交/自动推送。
- **不做**（计划 §1/§2）：真实 Provider；4E.2 交互图/4E.3（本批只落进程级终止前置）；G5 选项 C；Wave X；AUD-08；5b/5c 与阶段 6 UI（热区）。

### 1.2 已裁边界（本批执行，不得改）

1. **JS 方法不广告**：工具面、提示词、能力快照零改动；JS 仍 `UNAVAILABLE` 直到 4E.2 全门。
2. **隔离进程层是 4E.2 前置**：本批只落 `:visual_js` 进程 Service + AIDL + generation 核对 + 强制终止；交互图与保真升级（4E.2/4E.3）不做。
3. **`settings.gradle.kts` 是热区**：不得新增 Gradle 模块；AIDL 与 Service 都落 app 模块。
4. **依赖决策由 R2 证据定**：先试约束 kotlin-stdlib 到 2.3.21 再加全量测试与设备；不通过再按 R2 升级路径评估，不擅自全项目升 Kotlin。
5. **真实 Provider 无凭据**：一切模型质量 UNVERIFIED。
6. **契约 `docs/current/visual-generation-contract.md` 已被批次 21 授权改过 RELRO 行**；本批只做 T7 的措辞与状态一致性修正。

### 1.3 基线摘要（引自基线 + 记录时点复核）

- **HEAD**：基线实测 `062e18f0`（= origin/main）；记录时点实测 **`41421c0d` = origin/main**（`git rev-parse --short HEAD` 与 `origin/main` 同值）。两者之间是 KB 他线的 docs-only 批次 22 提交（`062e18f0..41421c0d`，`git log --oneline -2` 可见 `41421c0d`/`062e18f0`），无代码/schema 变化；本批代码基线仍按 `062e18f0` 引用。
- **工作树**：基线 **208 M + 453 ??**；记录时点 **673 项 = 208 M + 465 ??**（`git status --porcelain` 行数实数）。他线继续在飞。
- **schema**：`STUDY_DATABASE_VERSION = 69`（基线 `StudyDatabase.kt:134`）；请求 schema `TUTOR_VISUAL_CONTEXT_SCHEMA_VERSION = 18`（`ModelTasks.kt:546-547`）。记录时点 `git status --porcelain -- core/database/schemas .../StudyDatabase.kt core/model/.../ModelTasks.kt` **无输出**；本批零 DB schema、零请求 schema、零版本串 bump（§2.5、算法台账 §3.32）。
- **批次 22 落点**：基线逐个实测 8 个落点全干净；记录时点均按本批改动变脏/新增（`git status --porcelain --` 逐个核对，§3/§7）。
- **热区漂移**：基线 feature/tutor 36、feature/library 26、feature/capture 13、feature/review 11、core/ui 11、app 21（含 Root 系列）、core/database 8、core/export 5、core/domain 4、core/data **23**（计划写"两 M"，基线 §9.1 指出不符）、docs/current 7、tools 6、settings.gradle.kts M。
  - 记录时点复核：**feature/library 33、feature/tutor 56、core/ui 34**（`git status --porcelain -- <dir> | wc -l`），`app/src/main/kotlin/com/tingyun/smartmistakebook/SecondaryScreens.kt` 仍 `M`。T8 因此走**分支 D**（只报告不落 UI）。
  - `core/domain` 他线 4 M 仍在（BatchImportRepository/MistakeDetailRepository/SchedulingEvaluation/TutorJudgedReview）；本批 T4 另改 `AdaptiveQuestionSelector.kt`（M）与新增 3 文件。
  - `core/data` 他线面仍远大于"两 M"：本批只纳入 visual/ 与 study/ 下的 T2/T4/T6 文件，其余 20+ 文件排除（§7）。
- **`settings.gradle.kts` 实质**：他线 diff 新增 `":core:markdown"`（基线 §2.4），与本批"禁改/不开新模块"约束正面相邻；本批未改该文件。
- **文档热区矛盾**：共享纪律写"docs/current 其余 M"为热区；计划/工单同时授权 T7 修正 `docs/current/visual-generation-contract.md`（批次 21 已授权过 RELRO 行）。本批该文件仅按 T7 改 4 行（§3.7），整文件纳入并注明。
- **契约"三层"编号**：契约正文无"三层"字样（基线 §4.2）；本记录统一用 **L1 协作中断 / L2 服务硬终止（唯一进程级）/ L3 宿主阶段期限**，不再单独使用"第三层"。

## 2. 四研究线与复核结论

### 2.1 R1 隔离进程 / AIDL / 进程级硬终止（`docs/research/2026-10-26-visualjs-isolated-process.md`）

- **选型**：`android:isolatedProcess="true"` + `android:process=":visual_js"` 的**非导出绑定 Service**；硬终止只由服务进程自己执行 `Process.killProcess(Process.myPid())`（watchdog 到点或 AIDL `oneway terminate(generation)` 核对通过）。宿主只做"废弃 generation → 请求终止 → unbind → Binder death 确认 → 最多重建一次"，**不假设宿主能杀不同 UID**。
- **理由**：隔离进程 UID 与宿主不同、无自己的权限、不挂载应用数据目录；宿主用 `killProcess(servicePid)`/`ActivityManager.killBackgroundProcesses` 杀不到它；能杀任意循环（含 issue #240 自续 job）的只有服务进程内 SIGKILL。普通 `:visual_js`（同 UID 独立进程）虽可由宿主 kill，但保留全部应用权限/文件/网络，违反契约 §4 隔离前提，列为被拒候选。
- **证据等级**：官方文档经 `developer.android.google.cn` 官方镜像取文，AOSP/AndroidX/Chromium 源码用 `gh api` 按 pin commit 实读，开源样例（nextvol Apache-2.0、TinaIDE GPL-3.0 仅参考思路、Firefox MPL-2.0、duckduckgo Apache-2.0）逐项标 license；本地 `javap` 只核签名存在性。**Gradle/设备未运行**，设备行为均 `UNVERIFIED`。
- **AIDL 配方**：`IVisualJsService`（`protocolVersion`/`workerUid`/`currentGeneration`/`initialize`/`execute`/`oneway terminate`）+ `IVisualJsCallback`（`oneway onResult`/`onFatal`）；源码与 JSON 走 `ParcelFileDescriptor` 有界管道（256 KiB 上界），不逐图元走 Binder。
- **未验证**：隔离进程内 `libquickjs.so` 装载、`killProcess` 权限、watchdog 实际杀进程、ContentProvider/AndroidX Startup 在隔离进程的装配面；AOSP mirror 非实时 HEAD，行为可能已漂移。

### 2.2 R2 依赖治理（`docs/research/2026-10-26-dependency-governance.md`）

- **Q1 选型**：**全项目 dependency locking（`lockAllConfigurations()`）为主 + 保留既有 quickjs 定向 sha256 门**；全量 `verification-metadata` 作为条件项，**本批默认不启用**。两者不是替代关系：locking 钉"解析出的版本"，verification 钉"下载到的字节"。本仓库"AAR 的 POM 把 kotlin-stdlib 从 2.3.21 静默抬到 2.4.10、仓库内零 diff"正是 locking 的目标失败模式。
- **Q2 兼容判定**：AAR 字节码 106 个 class 的 88 条 `kotlin/*` 成员引用在 2.3.21/2.4.10 两个 stdlib **全部可解析（unresolved=0）**；唯一"2.4 才有"的 `kotlin.concurrent.atomics.AtomicBoolean` 只出现在 Kotlin 元数据，JVM 字段类型实为 Java `AtomicBoolean`。POM 的 `kotlin-stdlib 2.4.10` 是库作者构建声明，不是字节码硬要求；但元数据 `mv=[2,4,0]` 只能靠"向前兼容"读取，官方口径是 "preferably ... mostly"（不保证）——**是"能用"，不是"合规"**。
- **三条路径**：**A（约束回 2.3.21）先行**，须同步改写 `tools/ci/check_dependency_pins.py:165-168` 的语义（旧脚本会把 A 判红，本线负控已实证）；**B（全项目升 2.4.10）当前被 KSP 阻断**（Maven Central 最新 KSP=2.3.12，官方声明支持 KGP 2.2.10–2.3.x；Kotlin 2.4 官方声明 Gradle 上限 9.5.0，本仓库 wrapper 9.6.1 超界）；**C（维持现状 + 锁定）是 A 失败时的回退**。
- **Q3 牵动面**：只动 stdlib 版本（A/C）不牵动 Compose 编译器插件与 KSP；只有升 Kotlin（B）才牵动。R8 侧批次 21 的 release 已在含 2.4.10 + 该 AAR 的 APK 上跑通并启动无 FATAL。
- **记录时点复核**：仓库仍**无任何 `gradle.lockfile`**（`find app core feature benchmark gradle build-logic -maxdepth 2 -name gradle.lockfile` 无输出；根目录也无），`gradle/verification-metadata.xml` 不存在；默认 `LockMode.LENIENT` 下 locking **实际未生效** → **KD-72**。stdlib 降级只有 AAR 局部扫描与缓存 POM 样本，不是完整解析图证明 → **KD-73**。

### 2.3 R3 QuickJS OOM 语义（`docs/research/2026-10-26-quickjs-oom-semantics.md`）

- **memoryLimit 语义**：`JS_SetMemoryLimit` 只写 `malloc_state.malloc_limit`；`js_def_malloc`/`js_def_realloc` 按 `malloc_size + size` 拒绝（bellard `quickjs.c:2153-2209`、`:2221-2224`，commit `04be246`）。成功分配按 `usable_size + MALLOC_OVERHEAD` 记账，16 MiB 是近似硬上限。
- **超限抛什么**：分配器返回 NULL → `JS_ThrowOutOfMemory` → `JS_ThrowInternalError("out of memory")` → 普通 `InternalError` Error 对象（可被 JS `try/catch` 捕获，`current_exception_is_uncatchable=FALSE`）。
- **关键反例**：连 Error 对象本身都分配不出来时，`JS_ThrowError2` 改抛 `JS_NULL`（源码注释 "out of memory: throw JS_NULL to avoid recursing"，`quickjs.c:7638-7655`）；quickjs-kt 1.0.15 把 `JS_NULL` 映射成 Java `null`，Kotlin 侧 `reason.toString()` 折成字符串 `"null"`。**KD-70 的 `program failed in phase prepare: null` 由此产生，不是"AAR 丢消息"**。
- **字符串上限不是 1 MiB**：`JS_STRING_LEN_MAX = 2^30−1`；rope 阈值 512/8192/depth 60，`s+s` 可在极小堆下把长度推到 2^30 并抛 `InternalError: string too long`——批次 21 旧夹具红例的机制。
- **确定性探针**：**P1** 单次 64 MiB `ArrayBuffer`（message 可读，验 `MEMORY_LIMIT` 主路径）；**P2** 50 万小对象链表（无 headroom，确定性复现 `"null"`）；**P3** rope 翻倍（`string too long` 负控）；**P4** 低堆 `throw null`（`INVALID_PROGRAM` + `[UNATTRIBUTED]` 负控，T2 裁定不新增 UNKNOWN）；**P5** `RangeError('out of memory')`（伪造文案负控）。
- **分类规格**：**类型优先**（quickjs-kt 只有 `QuickJsInterruptedException` 可区分）→ **消息严格前缀** `InternalError: out of memory`（不再宽泛 `contains`）→ 仅对丢失哨兵（`null`/`"null"`/`Invalid evaluation handle: -1`）读 `memoryUsage`，`mallocSize ≥ 90% mallocLimit` 归 `MEMORY_LIMIT` → 都不确定时 `UNATTRIBUTED`（对外折 `INVALID_PROGRAM` + 前缀）兜底。取证点在 phase 内、`close()` 前。
- **记录时点复核**：分类器与 P1–P5 在树（`VisualJsFailureClassification.kt`、测试 `:242/:271/:296/:320/:340`）；JVM 分类矩阵 XML `tests="14" failures="0" errors="0"`（12:33，记录员只读，非本轮实跑）；设备 20 例仅 P2 红（工单材料），KD-70 保持 open。

### 2.4 R4 R8 / WorkManager / Room（`docs/research/2026-10-26-r8-workmanager-room.md`）

- **官方 consumer rules**：`androidx.room:room-runtime:2.6.1` 只带一条**无成员限定**的 `-keep class * extends androidx.room.RoomDatabase`；`androidx.work:work-runtime:2.10.5` 只保 Worker/InputMerger/WorkerParameters；Room **2.7.0 起**才改成 `{ void <init>(); }`，Room3 3.0.0 自带该成员规则。
- **根因链**：AGP 9.3.0 的 `android.r8.strictFullModeForKeepRules` 默认 **true**（`BooleanOption` 字节码 `iconst_1` 实读）；R8 9.3.16 在此模式下**不再为被 keep 的类隐式保留无参构造器**。Room 2.6.1 的无成员规则只保住类/类名，`WorkDatabase_Impl.<init>()` 无人引用 → 被裁（批次 21 `usage.txt:43130`）→ `androidx.startup` 启动期 `Room.getGeneratedImplementation` 反射实例化失败 → FATAL。
- **修法**：给 `benchmark`/`internal` 各补一行 `proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")`，把已存在的项目规则带进变体；**不要**关 minify、**不要**关 strict full mode（AGP 10 弃用/11 移除）、**不要**改 Room 版本。release 同批 A/B（`configuration.txt:101` 项目 Room keep + `release usage.txt:45476` 无 `<init>`）已证明有效；`matchingFallbacks` 不继承 `proguardFiles`。
- **第二个未爆缺口**：ML Kit 三个 registrar 的惰性反射构造在 internal 同样被裁，是最可能的下一个崩溃点；debug minified 冒烟必须覆盖冷启动/应用 Room3 DB 首开/中文 OCR/一次导出 Worker。
- **记录时点复核**：`app/build.gradle.kts:67`（benchmark）、`:75`（internal）、`:84`（release）三处 proguardFiles 对齐；`strictOfflineInternal/usage.txt:43093` 起 `WorkDatabase_Impl:` 段已无 `public void <init>()`，`strictOfflineRelease/usage.txt:45476` 同样无；`localFirstInternal` 旧产物仍列（修前对照）。设备冒烟数字无日志（KD-74）。

### 2.5 研究复核结论

- **总体**：四条研究均有落盘文档、来源分级与未验证清单；本批代码与 R1 的 AIDL/Service/client 形状、R2 的路径 A、R3 的 P1–P5 与分类、R4 的补丁逐项对齐（记录员实读 §3）。未发现需要推翻研究结论的新事实；发现的是**实现/文档与研究的边界漂移**，已处置或登记（§5）。
- **R1**：选型与 AIDL 控制面可实现；但"宿主不能杀不同 UID"只在 API 28+ 有 `Process.isIsolated()` 兜底，API 23–27 依赖 `applicationInfo.uid` 未设备验证（§5 #20）；隔离进程内 libquickjs/Startup/ContentProvider 仍未设备审计。
- **R2**：Q1 的推荐没有落地为锁状态（KD-72）；Q2 的路径 A 已落码且钉版门拆成两条不变量（记录员实跑 `exit=0`、14/14 单测），但**完整解析图的 API 兼容无证明**（KD-73）。全量 verification-metadata 未启用是研究裁定，不是遗漏。
- **R3**：源码级语义与设备诊断一致（`"null"` 的确定性来源）；设备主路径 P1 未单独留日志，P2 红（KD-70）；分类器是"类型优先 + 严格前缀 + 丢失哨兵堆证据"的启发式，P4 只覆盖低堆 `throw null`，高堆反例缺失（§5 #19）。
- **R4**：根因由本机构建产物实证（usage/configuration），修复后 R8 产物静态对照成立；设备冒烟只有工单数字、无日志（KD-74）。KD-69 保持 open 是"缺设备证据"，不是修复未落码。

## 3. 八实施轨 + 集成 + 设备轨

> 判定口径：**代码级完成** = 改动在当前树且记录员/复核静态核对属实；执行证据分列（设备实跑 / JVM 工件 / 独立编译执行 / 无证据=UNVERIFIED）。文件清单按工单 JSON 与 `git status` 交叉核对（§7）。
>
> 状态总表（工单状态原样保留，末列为记录员判定）：
>
> | 轨 | 工单状态 | 本记录判定 | 差异理由 |
> |---|---|---|---|
> | T1 隔离进程层 | 部分 | **代码落层 / 设备功能门红（partial）** | 设备修复前 4/4 红；pipe 根因已修但修复后未复跑；新增第 5 例未设备执行 |
> | T2 OOM 修复 | 完成 | **代码完成 / 设备中央分支未达（partial）** | 设备 20 例仅 P2 红；KD-70 保持 open |
> | T3 供应链加固 | 部分 | **部分** | 无任何锁文件（KD-72）；stdlib 降级完整图未证（KD-73） |
> | T4 G5 生产半边 | 完成 | **核心半边完成 / 产品未接线** | Room adapter、feature 消费者、生产调用点仍无；KD-67 open |
> | T5 R8 修复 | 完成 | **代码+构建产物完成 / 设备日志缺失** | usage/configuration 静态对照成立；冒烟无日志（KD-74） |
> | T6 竞态修复 | 完成 | **代码完成 / 设备证据无日志** | 设备材料称 KD-71 两例通过，但无日志、T6 summary 仍称待设备 |
> | T7 文档一致性 | 完成 | **完成（文档）** | 四项修正；KD 行号遗留见 §5 #8 |
> | T8 条件轨（分支 D） | 部分 | **部分（只报告）** | 三处热区仍脏，按指派不落 UI |

### 3.1 T1 隔离进程层 —— **代码落层 / 设备功能门红（partial）**

- 文件（9）：`app/build.gradle.kts`（M，`buildFeatures.aidl = true`）、`app/src/main/AndroidManifest.xml`（M，`:68-72` Service 三属性）、`app/src/main/kotlin/com/tingyun/smartmistakebook/SmartMistakeBookApplication.kt`（M，隔离 guard `:294-302`）、`app/src/main/aidl/com/tingyun/smartmistakebook/visualjs/IVisualJsCallback.aidl`（新）、`.../IVisualJsService.aidl`（新）、`app/src/main/kotlin/.../visualjs/VisualJsProtocol.kt`（新）、`.../VisualJsService.kt`（新）、`.../VisualJsClientEngine.kt`（新）、`app/src/androidTest/kotlin/.../visualjs/VisualJsIsolationInstrumentedTest.kt`（新）。
- 交付：非导出 `VisualJsService`（`exported=false`、`isolatedProcess=true`、`process=:visual_js`、无 intent-filter）；AIDL 控制面（`initialize`/`execute`/`terminate`/`protocolVersion`/`workerUid`/`currentGeneration`，source/JSON 走 `ParcelFileDescriptor` 有界管道）；服务内独立 watchdog（`ScheduledThreadPoolExecutor` + 令牌 CAS + `Process.killProcess(myPid())`，`:69-119`、`:413-427`）；`terminate(generation)` 在 Binder 线程核对后走同一出口（`:216-221`）；正常完成先失效令牌（`:368-380`）；宿主 Binder 死亡观察（`:540`）；L3 client 实现 `core:domain.VisualExecutionEngine`，per-bind generation、Binder death 恢复、最多重建一次、programHash 熔断；Application 在打开 Room/备份/Provider 前用 `Process.myUid() != applicationInfo.uid`（API 28+ 叠加 `Process.isIsolated()`）短路。
- **记录员实跑（非 Gradle/非设备）**：`aidl.exe --lang=java` 对两份 AIDL **分别单文件**编译（repo 路径，PowerShell 引号）→ `SERVICE_REPO_EXIT=0`、`CALLBACK_REPO_EXIT=0`，生成 `IVisualJsCallback.java`/`IVisualJsService.java`（`/c/tmp/aidlout_repo`）；`python tmp/verify_batch22_visualjs_compile.py` → `COMPILE_OK`、exit 0；`python tmp/verify_batch22_visualjs_test_compile.py` → `COMPILE_OK`、exit 0。
- **设备轨（工单转录，修复前 APK）**：类级 `VisualJsIsolationInstrumentedTest` → `Tests run: 4, Failures: 4`（Time 10.101）。4 例全部 `AssertionError: ... request pipe ended before a complete envelope arrived`（`RESULT_REJECTED=1`）；断言点 `:101`（首执行）、`:146`（stale terminate 后活执行）、`:168`（watchdog 6s 内未杀）、`:210`（client 首次执行未映射 HARD_DEADLINE）。单例复跑同诊断。
- **隔离证据（成立）**：测试运行中 `adb shell ps -A | grep visual_js` 采样到 `u0_i9000`…`u0_i9003`（最终复跑 `u0_i9007`）的 `...offline:visual_js:...VisualJsService`；宿主 `u0a202`；用例内 `workerUid() != Process.myUid()`（`:89-94`）通过。manifest 声明见 `AndroidManifest.xml:68-72`。
- **pipe 根因（工单证词 + 当前代码）**：`:visual_js` 进程 strace（PID 16445/16603）显示 `read(73, "{\"programJson\":...", 8192) = 840`、随后 `read(73, "", 8192) = 0`，信封已完整读入；服务仍写 `{"reason":"ENGINE_CLOSED","diagnostics":"request pipe ended before a complete envelope arrived"}`。根因是 `ParcelFileDescriptor.createReliablePipe()` 的 read end 带状态 comm 通道：宿主在 `execute()` 返回后关闭自己那份 read end，服务端到 EOF 时 `AutoCloseInputStream.checkError()` 抛 `IOException: Remote side is dead`，被 `readBoundedRequest` 的 catch 折成 `RequestRead.Failed`（旧 `VisualJsService.kt:241-255`、`:425-437`）。**当前树已修**：服务端在 Binder 线程先 `request.dup()`（`VisualJsService.kt:194-198`、`:254`、`:440-462`），宿主/测试读结果前同样先 `dup()`（`:514-515`）。
- **当前测试集**：`VisualJsIsolationInstrumentedTest` 现 **5 例**（`:87`、`:132`、`:157`、`:202`、`:223`），新增 `theClientRebuildsAtMostOnceAfterConfirmedAbandons`（`abandonForTest` 走生产 `abandon` 路径）；**4/4 红是修复前 APK 的 4 例，新增第 5 例与修复后的 5 例都未设备执行**。
- **AIDL 事故（记录时点）**：复核发现 #13 已记录 `IVisualJsCallback.aidl` 曾被 `aidl.exe` 多输入调用覆盖成 `IVisualJsService` 的生成 Java stub。**记录员本轮复跑时再次触发同一覆盖**：`aidl.exe ... file1.aidl file2.aidl` 的第二个位置参数被当作 OUTPUT（help：`aidl [OPTION]... INPUT [OUTPUT]`），第一份输入生成的 Java 写到了第二份输入的路径。已按 R1 §4.2 配方与 12:30 生成的 `IVisualJsCallback.java` 签名恢复（1340 B，UTF-8，未跟踪），并用**单文件** `--lang=java` 编译两份 AIDL 均 `exit=0`。恢复文本的接口签名与生成 Java 一致；非语义注释可能与修复轮原文有差异。**后续禁止对 aidl.exe 使用双位置参数调用。**
- **未达/未验**：修复后设备复跑、watchdog 实际杀进程、`killProcess` 在隔离进程的权限、隔离进程内 `libquickjs.so` 装载、ContentProvider/AndroidX Startup 隔离装配面 dumpsys 审计、L3 调用方上界（最坏约 8.5 s）与并发排队无上界。**KD-66 保持 open**。

### 3.2 T2 OOM 修复 —— **代码完成 / 设备中央分支未达（partial）**

- 文件（5）：`core/data/src/main/kotlin/.../visual/VisualJsFailureClassification.kt`（新）、`.../visual/QuickJsVisualExecutionEngine.kt`（M）、`core/data/src/test/kotlin/.../visual/VisualJsFailureClassificationTest.kt`（新）、`core/data/src/androidTest/kotlin/.../visual/QuickJsVisualExecutionEngineInstrumentedTest.kt`（M）、`docs/known-defects.md`（M，KD-70）。
- 交付：仪器化夹具换成 **P1**（64 MiB ArrayBuffer）并新增 **P2/P3/P4/P5** 确定性探针；引擎分类改为**类型优先 / 严格前缀 `InternalError: out of memory` / 丢失哨兵 + `memoryUsage ≥ 90%` 归 MEMORY_LIMIT / `UNATTRIBUTED` 兜底**（`VisualJsFailureClassification.kt:23-34`、`:123-157`）；新增 JVM 纯函数分类矩阵（14 例）。
- **记录员只读证据**：`core/data/build/test-results/testDebugUnitTest/TEST-...VisualJsFailureClassificationTest.xml` → `tests="14" skipped="0" failures="0" errors="0"`（mtime 12:33:35；记录员未运行，该 XML 属他人执行产物）。
- **设备轨（工单转录）**：`am instrument ... -e class ...QuickJsVisualExecutionEngineInstrumentedTest` 无 log-only 参数 → `Tests run: 20, Failures: 1`（Time 7.803）。唯一红例 `heapExhaustionWithLostErrorMessageIsAttributedByMemoryUsage`（测试 `:271`）期望 `MEMORY_LIMIT`，实际 `INVALID_PROGRAM: [UNATTRIBUTED] ... raw=null, mallocSize=177216, mallocLimit=16777216`。其余 19 项通过，含 P1 `memoryOverTheHeapBudgetIsRejectedAsAMemoryLimit`（`:242`）、P3/P4/P5、issue240（`done.await(5s)` 未返回，符合 1.0.15 已知形态）、KD-71 两例（close 确定性 + 32 轮并发）。
- **未达**：P2 的"丢失哨兵 + 堆证据"中央分支未在设备成立；P1 的 `MEMORY_LIMIT` 无逐例日志。**KD-70 保持 open**；不得用其它四例绿替代。
- 说明：`P4` 已更名 `nullThrownAtLowMemoryIsUnattributedInvalidProgram`（原复核指出的 `nullThrownAtLowMemoryIsUnknown` 名实不符已修）；`VisualJsProtocol.kt:48-54`、`:320-321` 已改为"T2 最终裁定不新增 UNKNOWN"。

### 3.3 T3 供应链加固 —— **部分**

- 文件（5）：`build.gradle.kts`（M，锁定 + 路径 A 约束 + 回退判据）、`gradle/libs.versions.toml`（M，上游 POM 声明 2.4.10 与项目编译 2.3.21 分列）、`tools/ci/check_dependency_pins.py`（M，两条不变量）、`tools/tests/test_check_dependency_pins.py`（新，14 例）、`.github/workflows/android-check.yml`（M，`**/gradle.lockfile` 触发路径 + pin gate 注释）。
- 交付：根 `build.gradle.kts:37-40` 对 14 个子项目 `dependencyLocking { lockAllConfigurations() }`；`:51-60` 用 `eachDependency + useVersion` 把运行时 `kotlin-stdlib` 约束回 `kotlin = 2.3.21`，并写明两条回退判据（AAR 升级后 unresolved>0；设备 `NoSuchMethodError/NoClassDefFoundError` 归因 AAR）；钉版门拆成"上游 POM 声明 == catalog `quickjs-kotlin-stdlib` (2.4.10)"与"解析出的运行时 stdlib == catalog `kotlin` (2.3.21)"两条不变量（`check_dependency_pins.py:157-178`），AAR/POM/.module 三 sha256 继续校验；CI `:117-125` 调用脚本。
- **记录员实跑**：`python tools/ci/check_dependency_pins.py` → `OK: quickjs checksums, upstream POM requirement and resolved runtime pins match`，`PIN_EXIT=0`；输出 `resolved: quickjs=['1.0.15'] kotlin-stdlib=['2.3.21']`；`PYTHONPATH=tools python -m unittest discover -s tools/tests -p test_check_dependency_pins.py -v` → `Ran 14 tests ... OK`。
- **未达**：**仓库无任何 `gradle.lockfile`**（`find app core feature benchmark gradle build-logic -maxdepth 2 -name gradle.lockfile` 无输出；根目录也无）；`gradle/verification-metadata.xml` 不存在；默认 `LockMode.LENIENT` 下锁门**不生效**（KD-72）。stdlib 降级的完整解析图 API 兼容**无证明**（只扫了 AAR 的 88 条成员引用；KD-73）。pin gate 仍依赖 `app/build/intermediates/incremental/lintVitalAnalyzeStrictOfflineRelease/strictOfflineRelease-artifact-dependencies.xml`（当前树可读，但旧构建产物在 AIDL 红时会失效）。
- 结论：T3 只能记**部分**；"已配置 locking"不等于"锁门生效"。

### 3.4 T4 G5 生产半边 —— **核心半边完成 / 产品未接线**

- 文件（9）：`core/domain/.../AnswerKeyVerificationUpgrade.kt`（新）、`core/domain/.../AdaptiveSelectionConsumption.kt`（新）、`core/domain/.../AdaptiveQuestionSelector.kt`（M，抽出 `unverifiedTargetCandidates` 单一来源）、`core/domain/src/test/.../AnswerKeyVerificationUpgradePolicyTest.kt`（新）、`core/data/.../study/KnowledgeQuizItemVerificationUpgrader.kt`（新）、`core/data/.../study/AdaptiveSelectionAssembler.kt`（M，公开可注入 seam + `selectAndConsume` + CLARIFY 转发）、`core/data/src/test/.../study/KnowledgeQuizItemVerificationUpgraderTest.kt`（新）、`AdaptiveSelectionAssemblerTest.kt`（M）、`docs/known-defects.md`（M，KD-67）。
- 交付：核验升级领域判据（`UNKNOWN→VERIFIED/USER_ASSERTED`，来源形状 + 确认者归属 + 时间非负）、写方 API（单列 CAS `compareAndSetVerificationStatus`）、`sourceVerifier.verify(...)` 对回执/核验记录的真实性校验（`:171-176`）、`AlreadyUpgraded` 幂等路径也先验来源（`:140-149`）、CLARIFY 消费契约（`AdaptiveClarificationConsumer`）、`AdaptiveSelectionAssembler` 公开注入 seam 与 `selectAndConsume`。
- **记录员实跑（非 Gradle）**：`python tmp/verify_batch22_t4.py` → `EXIT=0`；`AnswerKeyVerificationUpgradePolicyTest` `OK (4)`、`KnowledgeQuizItemVerificationUpgraderTest` `OK (9)`、`AdaptiveSelectionAssemblerTest` `OK (12)`、`AdaptiveQuestionSelectorTest` `OK (21)`、`EloCorrectnessPredictorTest` `OK (14)` → **合计 60/60**（工单 T4 summary 写"58 例"是新增两例前的旧数；当前树为 60）。
- **未达（如实）**：Room adapter、feature 消费者、生产调用点仍不存在（`grep` 生产零引用；KD-67 open）。删除端所需"写方 API/消费契约/可注入 seam"已具备，但**不是产品可用**。`AnswerKeyVerificationUpgrade` 的 KDoc 已如实写明"仅形状校验不够、`confirmationId` 任意非空串 + 当前学习者 id 可伪造 USER_ASSERTED"的边界（`:103-108`），生产接线前必须由 `sourceVerifier` 绑定真实回执。
- 版本口径：`LearningCoreVersions` 未 bump；`AdaptiveQuestionSelector` 只做等价抽取（`unverifiedTargetCandidates`），公式/决策不变（§2.5、算法台账 §3.32）。

### 3.5 T5 R8 修复 —— **代码+构建产物完成 / 设备日志缺失**

- 文件（2）：`app/build.gradle.kts`（M，benchmark/internal 各补一行 proguardFiles；共 6 行：4 注释 + 2 规则）、`docs/known-defects.md`（M，KD-69 根因/Progress/待设备轨验收命令）。
- 交付：`benchmark` `:67`、`internal` `:75`、`release` `:84` 三处规则集对齐（`proguard-android-optimize.txt` + `proguard-rules.pro`）；未关 minify、未关 strict full mode、未改依赖版本。
- **静态对照（工单材料）**：`strictOfflineInternal/usage.txt:43093` 起 `androidx.work.impl.WorkDatabase_Impl:` 段已无 `public void <init>()`；三个 ML Kit registrar `<init>` 也不在移除列表；`configuration.txt:101` 含项目 Room keep；`strictOfflineRelease/usage.txt:45476` 同样无 `<init>()`；修前 `localFirstInternal` 旧产物仍列。
- **设备冒烟（工单转录）**：`./gradlew.bat --console=plain :app:assembleStrictOfflineInternal :app:assembleStrictOfflineBenchmark` → BUILD SUCCESSFUL 4m3s；internal 两次启动（WARM/COLD）均 `Status: ok`（PID 16887/17025）；benchmark 两次启动均 ok（PID 17122/17241）；四份 logcat（581/553/375/294 行）对 `FATAL EXCEPTION|StartupException|NoSuchMethodException|ClassNotFoundException|NoClassDefFoundError|ANR in|MlKitException` 全零命中。release：临时 CI dry-run keystore（构建后删除、未输出口令）→ BUILD SUCCESSFUL 3m53s，`app-strictOffline-release.apk` 155,682,706 B；uninstall 后两次 COLD 启动 ok（PID 17874/17961）；两份 logcat（501/344 行）同样零命中。
- **未达**：设备日志未落盘（KD-74）；KD-69 保持 open（缺设备证据，不是补丁缺失）。

### 3.6 T6 竞态修复 —— **代码完成 / 设备证据无日志**

- 文件（3）：`core/data/.../visual/QuickJsVisualExecutionEngine.kt`（M）、`QuickJsVisualExecutionEngineInstrumentedTest.kt`（M，新增 2 例）、`docs/known-defects.md`（M，KD-71）。
- 交付：`closed` 与在飞事务登记改为同一把 `stateLock` 单临界区（登记 `:198-203`、close `:269-286`）；close 强弃后用 completion latch 有界等待（`hardDeadline+500ms`）；阶段在创建上下文前与登记活引擎后双查 `abandoned`；最终结果与 `closed` 同临界区线性化判定（`:245-256`）；对外错误枚举零改动。
- 测试：`closeDuringAnInFlightTransactionWaitsAndRejectsIt`（当前 `:545`）、`concurrentCloseAndExecuteRoundsNeverReportAnUnexpectedOutcome`（当前 `:617`）；工单设备材料称这两例在 20 例运行中通过（仅 P2 红）。
- **但**：无设备日志；T6 summary 仍写"新用例未设备执行，保持 open"。**记录按保守口径保持 KD-71 open**，待日志落盘后再关。

### 3.7 T7 文档一致性 —— **完成（文档）**

- 文件（3）：`docs/current/visual-generation-contract.md`（M，4 行）、`docs/known-defects.md`（M）、`docs/agent-first-refactor-decisions-2026-09-23.md`（M）。
- 四项修正：① 契约 RELRO 行按批次 21 复核 #23 改为内存/文件偏移双视角的准确表述；② 契约 §4 按 T1 真实现状补 L1/L2/L3 落地状态（含调用方上界如实化并引用 KD-66）；③ KD-66/67/68/69/70/71 六条状态按时点证据统一核对（新增 KD-66/68 Progress、KD-69 标注修复前；六条全部仍 open，无关闭证据）；④ decisions 追加批次 22 T1 架构偏差登记节。
- 未跑 Gradle、未做 git 写、未改热区代码。记录时点复核：契约 §4 的 L2 段已如实写"设备修复前 4/4 红、修复后 UNVERIFIED"；KD-67 的旧行号问题仍遗留（§5 #8，本记录已修）。

### 3.8 T8 条件轨（分支 D） —— **部分（只报告）**

- 开工复核三处热区仍脏：`feature/library` **33**、`feature/tutor` **56**、`core/ui` **34**（`git status --porcelain -- <dir> | wc -l`），`app/src/main/kotlin/.../SecondaryScreens.kt` **M**。
- 按指派走分支 D：**不做 UI**；三项遗留（AWAITING_VERIFICATION UI 态、G1/G2、G7 嵌入）均已在决策台账有现成条目，无需补登记。
- 本轨未改任何文件、未运行 Gradle。**T8 不声称任何产品面完成。**

### 3.9 集成

- **无生产接线**：T1 client 只被仪器化用例构造（生产零引用）；T2 分类只在引擎内部；T4 装配器有公开 seam 但无生产调用点；T5 只改构建配置；T7 只改文档。JS 仍 `UNAVAILABLE`、不广告；`FigurePromptAssembler.kt:41,109` 仍写"do not advertise JS"（复核材料实读）。
- **模块边界**：Service/AIDL/client 全落 app 模块，未新增 Gradle 模块（`settings.gradle.kts` 未改）；`core/data`、`core/domain` 源码不引用 AIDL。
- **CI/供应链**：`.github/workflows/android-check.yml:17` 新增 `**/gradle.lockfile` 触发；`:117-125` pin gate 两条不变量。
- **契约**：`docs/current/visual-generation-contract.md` §4 已把 L1/L2/L3 现状、设备红、修复未复跑、调用方上界（最坏约 8.5 s）与"JS 不广告"写清；L2 代码落地不等于 KD-66 关闭。

### 3.10 设备轨（工单转录；记录员未复跑、无设备）

> 环境：`emulator-5554`（API 34，x86_64，4KB）用于 T1/T2/16KB 外的设备动作；`ps16k_x86_64`（AVD `ps16k_x86_64`，API 36，x86_64，16KB）用于 16KB。协议 = `adb install -r -t` + `am instrument -w -r -e class ...`，**全程未跑 connectedAndroidTest**。以下数字均来自工单设备轨材料；**仓内无本批设备日志**（`build/device-track-batch22` 不存在；`build/` 只有 KB 他线 `agent-batch22` 与旧 `device-track-batch21`），故一律标 **UNVERIFIED（无日志）**。

1. **构建**：`./gradlew.bat --console=plain :app:assembleStrictOfflineDebug :app:assembleStrictOfflineDebugAndroidTest :core:data:assembleDebugAndroidTest :core:database:assembleDebugAndroidTest` → BUILD SUCCESSFUL 1m13s；产物 `app-strictOffline-debug.apk`、`app-strictOffline-debug-androidTest.apk`、`data-debug-androidTest.apk`、`database-debug-androidTest.apk`。前置 `:app:assembleDebugAndroidTest` 因 flavor 二义失败（候选 `assembleLocalFirstDebugAndroidTest`/`assembleStrictOfflineDebugAndroidTest`），改用显式 strictOffline 任务。记录员只读确认产物存在（`app/build/outputs/apk/strictOffline/debug/app-strictOffline-debug.apk` 183,032,608 B，09:13）。
2. **T1 安装与执行**：`adb -s emulator-5554 install -r -t` 两 APK 均 Success；`am instrument -w -r -e class ...VisualJsIsolationInstrumentedTest ...AndroidJUnitRunner` → `Tests run: 4, Failures: 4`，Time 10.101。恢复 debug 后单例复跑首例 → `Tests run: 1, Failures: 1`，同诊断。
3. **T1 红因**：4 例全部 `AssertionError: ... request pipe ended before a complete envelope arrived`（`RESULT_REJECTED=1`）。断言点 `:101`/`:146`/`:168`/`:210`。
4. **T1 隔离证据（成立）**：`adb shell ps -A | grep visual_js` 采样到 `u0_i9000`、`u0_i9001`、`u0_i9002`、`u0_i9003`（最终复跑 `u0_i9007`）的 `com.tingyun.smartmistakebook.offline:visual_js:...VisualJsService`；最终复跑 logcat `Start proc 18328:.../u0i7 for service`，宿主 `Start proc 18256:.../u0a202`；用例内 `workerUid()!=Process.myUid()`（`:89-94`）通过。
5. **T1 请求管道反证**：对 `:visual_js` 进程 strace（PID 16445/16603）得到 `read(73, "{\"programJson\":...", 8192) = 840`、`read(73, "", 8192) = 0`；随后服务仍写 `{"reason":"ENGINE_CLOSED","diagnostics":"request pipe ended before a complete envelope arrived"}`（96 B）。该诊断唯一来源是旧 `VisualJsService.kt:241-255`；`readBoundedRequest` 以 `AutoCloseInputStream(request).use` 包住读取（旧 `:425-437`），catch 吞掉 IOException；客户端同样 `createReliablePipe` + 先 execute 后写信封（`VisualJsClientEngine.kt:252`、`:273`、`:349`）。
6. **T2**：安装新 `data-debug-androidTest.apk` 后 `am instrument ... -e class ...QuickJsVisualExecutionEngineInstrumentedTest`（无 log-only）→ `Tests run: 20, Failures: 1`，Time 7.803；唯一红例 `heapExhaustionWithLostErrorMessageIsAttributedByMemoryUsage`（测试 `:271`）期望 `MEMORY_LIMIT`，实际 `INVALID_PROGRAM: [UNATTRIBUTED] ... raw=null, mallocSize=177216, mallocLimit=16777216`。其余 19 项通过。
7. **16KB**：`ps16k_x86_64` AVD 启动后 `getconf PAGESIZE; getconf PAGE_SIZE` → 两行均 `16384`；AVD `config.ini` `image.sysdir.1=system-images\android-36\google_apis_ps16k\x86_64\`、`tag.id=page_size_16kb`。安装 `data-debug-androidTest.apk` 后 `am instrument ... -e class ...NativeLibraryPageSizeInstrumentedTest` → `OK (3 tests)`，Time 0.029；logcat `PAGE_SIZE=16384`、`Load .../lib/x86_64/libquickjs.so ... ok`、`Load .../lib/x86_64/libLiteRt.so ... ok`（断言 `NativeLibraryPageSizeInstrumentedTest.kt:50-58`，quickjs 装载 `:32-35`，LiteRT 装载 `:37-42`）。
8. **R8 debug minified**：`:app:assembleStrictOfflineInternal :app:assembleStrictOfflineBenchmark` → BUILD SUCCESSFUL 4m3s；internal 两次启动（WARM/COLD）均 `Status: ok`（PID 16887/17025）；benchmark 两次均 ok（PID 17122/17241）；四份 logcat（581/553/375/294 行）对七类崩溃签名全零命中。
9. **R8 release**：用 `.github/workflows/android-check.yml:86-96` 的公开 CI dry-run 值临时生成 keystore（`build/` 下，构建后删除；未输出口令），`:app:assembleStrictOfflineRelease` → BUILD SUCCESSFUL 3m53s，`app-strictOffline-release.apk` 155,682,706 B（09:43）；apksigner 显示 CN=CI Dry Run。因签名不同先 `adb uninstall` 再 `install -r -t`，两次 COLD 启动均 ok（PID 17874/17961），versionName=0.2.0-offline；两份 logcat（501/344 行）对七类崩溃签名零命中。
10. **R8 keep 规则静态对照**：`strictOfflineInternal/usage.txt:43093` 起 `WorkDatabase_Impl:` 段已无 `public void <init>()`；三个 ML Kit registrar（修前映射 `:77348/:77357/:77360`）的 `<init>` 也不在移除列表；`configuration.txt:101` 含 `-keepclassmembers class * extends androidx.room.RoomDatabase {`；release `usage.txt:45476` 同样无 `<init>()`。对应修复 `app/build.gradle.kts:58-76`/`:77-85`。
11. **备份抽验**：`am instrument ... -e class ...BackupRestoreInstrumentedTest` → `OK (34 tests)`，Time 37.132。
12. **迁移抽验**：`FullMigrationMatrixInstrumentedTest` → `OK (15 tests)`，Time 727.819；`DeviceMigrationDrillInstrumentedTest` → `OK (6 tests)`（4 通过 + 2 跳过，跳过者为 kill-stage 两段）。随后显式破坏性闸：stage A `-e smb_d1_kill_stage true -e class ...#killProcessHandoffStagesArchiveThenDies` → `INSTRUMENTATION_RESULT: shortMsg=Process crashed.`（用例设计如此）；stage B `-e smb_allow_data_reset true -e class ...#restoreFromHandoffAfterProcessDeath` → `OK (1 test)`，Time 2.812。
13. **纪律**：未编辑任何源码/测试/文档（记录员只读核对 `git status --porcelain -- app/src/main/kotlin/.../visualjs app/src/main/aidl` 仍只有本批新增文件）；未运行 connectedAndroidTest，所有设备执行均为 `am instrument`；未运行 git 写操作；临时 dry-run keystore 已删除；ps16k 模拟器已停止，emulator-5554 已 unroot 并恢复 strictOffline debug APK。

## 4. 门禁与设备证据

| 门 | 状态 | 证据/说明 |
| --- | --- | --- |
| 单测门（11 模块） | **第 1 轮 `exit=0`**；修复环复跑 `exit=0`（工单材料） | 记录员未复跑 Gradle；**仓内无本批门禁日志**（`build/` 下无 batch22 单测门产物）→ 工单转录，不冒充记录员实证。 |
| 强门（六 androidTest 编译 + assemble） | **第 2 轮 `exit=0`**（工单材料） | 同上；设备轨的 `assembleStrictOffline*` 成功（§3.10 步一）是旁证，不等于完整强门。 |
| 修复环第 1 轮 | **13 条处置**（工单仅转录前 2 条细节：T1 设备红补记录、`readBoundedRequest` 根因修复；其余 11 条未提供逐条文本） | 修复环收尾"单测门复跑 `exit=0`"；未运行 Gradle；设备动作用既有 APK/自建 `app_process` 探针完成，未重装应用。 |
| 依赖钉版门 | **记录员实跑 `exit=0`** | `python tools/ci/check_dependency_pins.py` → `OK: quickjs checksums, upstream POM requirement and resolved runtime pins match`；`resolved: quickjs=['1.0.15'] kotlin-stdlib=['2.3.21']`。 |
| 钉版门单测 | **记录员实跑 14/14 OK** | `PYTHONPATH=tools python -m unittest discover -s tools/tests -p test_check_dependency_pins.py -v` → `Ran 14 tests ... OK`。 |
| AIDL 编译 | **记录员实跑两份单文件 `exit=0`** | `aidl.exe --lang=java`（repo 路径，分别单文件）→ `SERVICE_REPO_EXIT=0`、`CALLBACK_REPO_EXIT=0`；生成两个接口 Java。**双输入调用会把第二份输入当输出覆盖，禁止使用。** |
| T1 源码/测试编译 | **记录员实跑 `COMPILE_OK`** | `python tmp/verify_batch22_visualjs_compile.py`、`python tmp/verify_batch22_visualjs_test_compile.py` → 均 `COMPILE_OK`、exit 0（缓存 Kotlin 2.3.21 直编，非 Gradle）。 |
| T4 JVM | **记录员实跑 60/60** | `python tmp/verify_batch22_t4.py` → `EXIT=0`；`AnswerKeyVerificationUpgradePolicyTest` 4、`KnowledgeQuizItemVerificationUpgraderTest` 9、`AdaptiveSelectionAssemblerTest` 12、`AdaptiveQuestionSelectorTest` 21、`EloCorrectnessPredictorTest` 14。 |
| T2 JVM 分类矩阵 | **只读 XML：14/0/0** | `core/data/build/test-results/.../TEST-...VisualJsFailureClassificationTest.xml`（mtime 12:33:35）；记录员未运行该轮。 |
| 设备 | **工单转录，无日志** | §3.10 共 13 组；`am instrument` 裸跑，不产 XML；本批日志缺失 → KD-74；T1 4/4 红、T2 20/1 红。 |
| 真实 Provider | **UNVERIFIED** | 无凭据、未调用；JS 不广告；PRD-09 真实质量/费用未验。 |

**记录员对门禁的保留**：单测门/强门的 `exit=0` 没有本批日志落在仓内；设备数字均来自工单转录，`build/` 中只有 APK/mapping 产物（`app-strictOffline-{debug,internal,benchmark,release}.apk` 与 `app/build/outputs/mapping/strictOffline{Internal,Benchmark,Release}`），没有 `am instrument` 结果或 logcat。故本记录不把"门禁全绿/设备全绿"写成可审计结论。

## 5. 复核发现与处置（25 条；severity/status 按原复核，处置为记录员按当前树核对）

| # | 位置 | 发现 | 级别 | 复核状态 | 本批处置（当前树 / 记录时点） |
| --- | --- | --- | --- | --- | --- |
| 1 | `VisualJsIsolationInstrumentedTest.kt:85,130,155,200` | 设备轨 T1 隔离进程功能门 4/4 红，4E.2 前置未通过；T1 轨 summary 未记录本批设备轨已跑且全红 | 高 | 未复核 | **已补记录**：KD-66 Progress 与契约 §4 已写 4/4 红与首例断言；本记录 §3.1/§3.10 转录。设备材料来自工单，无日志。 |
| 2 | `VisualJsService.kt:241-256,425-445` | 服务端 `readBoundedRequest` 把请求读取期间的任何 IOException 归为 `RequestRead.Failed`，回 `ENGINE_CLOSED` + "request pipe ended..."，代码仍在树中 | 高 | 已独立复核 | **已修（复核后）**：服务端 Binder 线程先 `request.dup()`（`:194-198`、`:254`、`:440-462`），宿主/测试读结果前先 `dup()`（`:514-515`）；修复后设备复跑 UNVERIFIED。 |
| 3 | `QuickJsVisualExecutionEngineInstrumentedTest.kt:270-291`；`known-defects.md:2641,2647` | 设备轨 T2 20 例仅 P2 红；KD-70 未记录 P2 红，中央分支未在设备成立 | 中 | 未复核 | **已补记录**：KD-70 Progress 已写"全量 20 例仅 P2 红"（`:2642/:2648`）；本记录 §3.2/§3.10 转录；KD-70 保持 open。 |
| 4 | `visual-generation-contract.md:72`；`known-defects.md:2584` | T7 写"4 例在盘未执行""尚无设备证据"，与本批设备轨 4/4 红冲突 | 中 | 已独立复核 | **已修**：契约 §4 与 KD-66 Progress 已改为"设备轨已执行（修复前）→ 4/4 红；修复后复跑 UNVERIFIED"。 |
| 5 | `build.gradle.kts:33-36`；仓库无 `gradle.lockfile` | dependency locking 已配置但没有锁文件，默认 LockMode 下不生效；T3=部分 | 中 | 已独立复核 | **未修（登记 KD-72）**：记录员复跑 `find ... -name gradle.lockfile` 无输出；`verification-metadata.xml` 不存在；钉版门本身生效。 |
| 6 | `build/` 与 `app/build/outputs/` | 16KB 3/3、R8 三变体冒烟零崩溃、备份/迁移抽验全绿均无日志落盘；设备结论 UNVERIFIED | 中 | 未复核 | **登记 KD-74**：本记录 §3.10/§4 全部标"工单转录、无日志"；仅确认 APK/mapping 产物存在。 |
| 7 | `VisualJsProtocol.kt:49-56,318-329`；`VisualJsClientEngine.kt:67-68`；`VisualExecutionEngine.kt:258-288`；`VisualJsFailureClassification.kt:33-35,80-90` | T1 注释仍写 T2 会新增 `UNKNOWN`、跨进程必须改成 UNKNOWN；T2 明确不新增（UNATTRIBUTED→INVALID_PROGRAM）；P4 测试名名实不符 | 中 | 已独立复核 | **已修**：`VisualJsProtocol.kt:48-54`、`:320-321` 已写"T2 最终裁定不新增"；P4 已更名 `nullThrownAtLowMemoryIsUnattributedInvalidProgram`（`:320`）。 |
| 8 | `known-defects.md:2653+`；`QuickJs...InstrumentedTest.kt:544,616` | KD-71 Progress 行号 `:543/:615` 与实际差 1；KD-67 Symptom 仍用 T4 改前旧行号且未标"历史行号" | 低 | 已独立复核 | **本记录已修**：KD-71 改为当前 `:545/:617`；KD-67 的 assembler/selector 行号改为"批次 21 实现时 + 当前值"。 |
| 9 | `git rev-parse HEAD` / `git status` | HEAD=origin/main=`41421c0d` 是 KB 批次 22 提交；4E 批次 22 改动仍在工作树，计划 §1 提交/推送未完成 | 低 | 已独立复核 | **如实记录**：HEAD `41421c0d`、工作树 208 M + 465 ??；本批提交清单见 §7，提交由主会话执行。 |
| 10 | `AndroidManifest.xml:68-72`；AIDL；`VisualJsService.kt:104-120,202-208,398-414`；`VisualJsClientEngine.kt:186-218,529-602`；`SmartMistakeBookApplication.kt:300-304` | 已核实（无问题）：T1 三层/进程层代码落层与打包证据齐全；Application guard 在 Room/备份之前；JS 未广告 | 低 | 已独立复核 | **维持**：记录员复跑 AIDL 单文件编译 exit=0；manifest 三属性、Application guard 行号复核一致。 |
| 11 | `KnowledgeQuizItemVerificationUpgrader.kt:32-42,158-168`；`AdaptiveSelectionConsumption.kt:81-107`；`AdaptiveSelectionAssembler.kt:308-331` | 已核实（无问题）：升级写口单列 CAS、CLARIFY 在 core/data 可达、58 例 JVM 全绿；但生产 Room adapter/feature consumer/调用点仍无，"生产半边"易被读成产品可用 | 低 | 已独立复核 | **维持并更新**：记录员实跑 60/60（新增两例）；生产仍零接线；KD-67 open；本记录 §3.4 如实标"核心半边"。 |
| 12 | `app/build.gradle.kts:64-67,74-75`；mapping；`visual-generation-contract.md:22`；热区 | 已核实（无问题）：T5 proguardFiles 在 R8 产物层生效；T7 RELRO 与 APK 内 libquickjs.so ELF 程序头一致；T8 计数 33/56/34 一致 | 低 | 已独立复核 | **维持**：记录时点复核 `usage.txt:43093`/`configuration.txt:101`、proguard 行 `:67/:75/:84`、热区计数 33/56/34 + `SecondaryScreens.kt` M。 |
| 13 | `IVisualJsCallback.aidl:1-10`（配合 `app/build.gradle.kts:95 aidl=true`） | 回调 AIDL 被写成 `IVisualJsService` 的生成 Java stub（第 10 行 `public interface IVisualJsService`，无 callback 接口），`aidl.exe` 双输入调用 exit=1，T1 整块不可构建 | 高 | 已独立复核 | **已修 + 记录员复现事故**：修复轮已恢复正确 `oneway` 回调源；记录员本轮误用双输入调用再次覆盖，已按 R1 §4.2 与生成 Java 签名恢复（1340 B，UTF-8），单文件编译两份 AIDL 均 exit=0。**双输入形式是 aidl.exe 的 `INPUT [OUTPUT]` 用法，禁止再用。** |
| 14 | `visual-generation-contract.md:72`；`known-defects.md:2581`；decisions `:3367` | 文档把 `IVisualJsCallback.aidl`/AIDL 控制面写成"均已实现"，实际源文件是 service stub；属把未构建/未验证代码写成已实现 | 中 | 已独立复核 | **已修（措辞）**：三处均补"源文件存在且可编译 ≠ 设备/构建门通过"；契约 §4 已写设备 4/4 红、修复未复跑。 |
| 15 | `build.gradle.kts:31-52`；CI；全仓 lockfile | locking 已声明但无锁文件，锁门未生效；buildscript/pluginManagement 未锁；pin gate 依赖旧 `artifact-dependencies.xml` | 中 | 已独立复核 | **登记 KD-72**：记录员复跑无锁文件、无 verification-metadata；pin gate `exit=0`。 |
| 16 | `build.gradle.kts:48-56`；catalog `:52-60`；R2 文档 | 把运行时 stdlib 从 AAR 软要求 2.4.10 压回 2.3.21；扫描只覆盖 AAR 88 条引用，不是完整解析图；无本批新构建/设备/R8 证据；软覆盖可被 strict 约束赢 | 中 | 未复核 | **登记 KD-73**；记录员复跑 pin gate 只校版本、不校 API；本记录 §2.2/§3.3 如实标"完整图未证"。 |
| 17 | `VisualJsClientEngine.kt:467-477,484-527,529-543,578-601`；契约 `:68` | "最多重建一次"只在死亡通知路径扣预算；`abandon` 不读也不置 `rebuildConsumed`，重复超时可反复新建 | 中 | 已独立复核 | **已修（复核 #12）**：预算改在 `connectLocked` 统一扣（`:512-519`），`ensureConnected` 先查预算（`:490`），`abandon` 后再次连接也受同一预算；测试 `theClientRebuildsAtMostOnceAfterConfirmedAbandons`（`:203`）在盘未设备执行。 |
| 18 | `AnswerKeyVerificationUpgrade.kt:26-61,120-158`；`KnowledgeQuizItemVerificationUpgrader.kt:135-140,142-168`；`AdaptiveSelectionConsumption.kt:19-22` | 公开核验升级 API 只校验来源形状，`confirmationId` 任意非空 + 当前学习者 id 即可把 UNKNOWN 写成 USER_ASSERTED；`AlreadyUpgraded` 不校验本次 source | 中 | 已独立复核 | **已修（复核 #13）**：写方新增 `sourceVerifier.verify(source, learnerId, snapshotId)`（`:171-176`），`AlreadyUpgraded` 前也走 `sourceOnlyRejection`（`:140-149`）；新增"无回执 id 拒绝且不写库""已升级行伪造回执不得回报 AlreadyUpgraded"两例。KD-67 仍 open（生产未接线）。 |
| 19 | `VisualJsFailureClassification.kt:101-104,124-135`；测试 `:319-335` | 类型优先正确；风险在"丢失哨兵 + 90% 堆证据"是启发式：接近上限的 `throw null` 会被标 MEMORY_LIMIT（P4 只测低堆），真实 OOM 若无前缀/读堆失败会落 INVALID_PROGRAM([UNATTRIBUTED]) | 低 | 已独立复核 | **未修**：分类器仍为启发式；P1–P5 设备未全部成立（P2 红）；KD-70 open。 |
| 20 | `SmartMistakeBookApplication.kt:294-302`；`VisualJsService.kt:112-119,127-131`；`app/build.gradle.kts:21 minSdk=23` | 隔离判定依赖"隔离进程 applicationInfo.uid == 应用 UID"；API 28+ 有 `Process.isIsolated` 兜底，23–27 没有；代码注释自标未设备验证；设备矩阵无 23–27 | 低 | 未复核 | **未修**：代码仍以 `Process.myUid() != applicationInfo.uid` 为主、API 28+ 叠加 `isIsolated`；无 23–27 设备证据。 |
| 21 | `VisualJsService.kt:82,202-208`；`VisualJsClientEngine.kt:604-609` | `activeGeneration` 初值 0，`terminate` 不检查 `initialized`，未初始化 worker 收到 `terminate(0)` 会自杀；生产 client 不发 0 | 低 | 已独立复核 | **未修**：`terminate` 仍只比较 generation（`:216-221`），无 `initialized` 前置；属 AIDL 边界。 |
| 22 | `VisualJsProtocol.kt:398-408`；`QuickJsVisualExecutionEngine.kt:336-345`；`VisualJsService.kt:296` | 服务内 `UnwiredVisualContentMeasurer` 对任何 text/math 条目抛异常，引擎折成 INVALID_PROGRAM，把"服务未接线"报成"程序无效" | 低 | 已独立复核 | **未修**：`UnwiredVisualContentMeasurer` 仍在（`:394`）；JS 未广告，当前不是产品故障；4E.2 接线前会误导诊断。 |
| 23 | `known-defects.md:2602`；`tmp/verify_batch22_t4.py`；`tmp/` | KD-67 以 4/4、7/7、12/12、21/21、14/14 作为证据并引用"批次 22 T4 交付记录"；该记录不在仓库，输出目录不存在 | 低 | 未复核 | **已补**：本记录 §3.4/§4 记录记录员实跑 60/60（4/9/12/21/14）与命令；`tmp/verify-batch22-t4` 目录仍在，脚本可复跑；记录文件即本文件。 |
| 24 | `VisualJsProtocol.kt:47-56,322-327`；`known-defects.md:2644` | Wire 注释仍写 T2 会新增 UNKNOWN/必须改成 UNKNOWN，与 T2 裁定冲突；`REASON_UNKNOWN` 无发送点 | 低 | 已独立复核 | **已修**：注释已改为"T2 最终裁定不新增"（`:48-54`、`:320-321`）；`REASON_UNKNOWN` 仍保留为 wire 字面量，无发送点。 |
| 25 | `VisualJsClientEngine.kt:164,186-203`；契约 `:64,72` | 契约 `:64` 要求满队列回 BUSY/最多两个待绘 program；client 用无超时 `executeMutex` 串行化，第二个并发调用无界等待；契约 `:72` 已披露，二者不一致 | 低 | 已独立复核 | **未修**：`executeMutex.withLock` 仍在（`:164`），`withTimeout` 在锁内（`:188`）；契约 `:72` 已如实披露"调用方上界不是 2500 ms/排队无上界"，但 `:64` 未修订。 |

## 6. 遗留 / UNVERIFIED / 待裁清单

**工程门**
1. **单测门/强门的 `exit=0` 无本批日志产物**（工单转录）；记录员未运行 Gradle。提交前建议主会话把 11 模块单测门 + 强门各复跑一次并留日志到 `build/device-track-batch22/`。
2. **本批设备结论无日志**（KD-74）：16KB 3/3、R8 三变体冒烟、备份 34/34、迁移 15/15 + 6/6、T2 20/1、T1 4/4 红均只有工单数字；`build/` 只有 APK/mapping。
3. **AIDL 工具用法**：`aidl.exe` 双位置参数 = `INPUT [OUTPUT]`，已两次造成 `IVisualJsCallback.aidl` 被覆盖；后续只允许 `--lang=java` + 单文件（或构建系统调用）。
4. **锁文件缺失**：T3 的 dependency locking 没有锁状态（KD-72）；`verification-metadata.xml` 不存在；全量 verification 按 R2 裁定不默认启用。

**T1 / 4E.2 前置**
5. **修复后设备复跑 UNVERIFIED**：pipe `dup()` 修复未装机验证；5 例（含新增的重建预算例）未跑；`killProcess` 权限、watchdog 实际杀进程、隔离进程内 libquickjs 装载均无绿证据。
6. **ContentProvider/AndroidX Startup 隔离装配面未 dumpsys 审计**；Application guard 只在 `onCreate` 前段，不能声称"隔离全部启动路径"。
7. **API 23–27** 的隔离 UID 判定无兜底、无设备矩阵；最坏是隔离进程完整初始化/无法自杀（不误杀宿主）。
8. **L3 调用方上界不是 2500 ms**：连接预算 5000 ms + 放弃死亡确认 ≤1000 ms，最坏约 8.5 s；并发调用在 `executeMutex` 无界排队。契约 `:64` 与 `:72` 仍不一致。
9. **`terminate(0)` 边界**：未初始化 worker 可被 `terminate(0)` 自杀；建议 `terminate` 前要求 `initialized`。
10. **`UnwiredVisualContentMeasurer`**：服务内内容度量未接线，text/math 条目被折成 INVALID_PROGRAM；JS 不广告，但 4E.2 接线前会误导诊断。
11. **4E.2 交互图 / 4E.3 保真升级未做**；JS 广告门未开；L3 无生产装配点。

**T2 / KD-70**
12. **P2 红**：丢失哨兵 + 堆证据的中央分支未在设备成立；P1 的 `MEMORY_LIMIT` 无逐例日志。KD-70 保持 open。
13. **分类器是启发式**：高堆 `throw null` 反例缺失；真实 OOM 消息丢失时仍可能落 `[UNATTRIBUTED]`。不得为变绿放宽断言。

**T3 / KD-72/73**
14. **stdlib 降级完整图未证**：88 条 AAR 成员引用扫描不是全解析图；运行期若用到 2.4 API 会以 `NoSuchMethodError/NoClassDefFoundError` 暴露；pin gate 只校版本。
15. **路径 B 被阻断**：KSP 无 2.4 版本、wrapper 9.6.1 超出 Kotlin 2.4 声明的 Gradle 上限 9.5.0；路径 C 是回退。
16. **锁门未生效**：需在可跑 Gradle 的环境执行 `./gradlew :app:dependencies ... --write-locks`（14 个子项目）并入库锁文件；本批不能声称"依赖已锁定"。

**T4 / KD-67**
17. **生产未接线**：Room adapter、feature 消费者、生产调用点均无；装配器虽已公开，但 `feature/review` 热区未空。
18. **回执绑定只到 `sourceVerifier` 端口**：生产确认流尚未实现该端口；UI 接线前必须补真实回执/确认记录，不能只传非空 `confirmationId`。
19. **G5 选项 C / 探索额度**仍待后续裁定；Elo 超参 `PENDING_CALIBRATION`。

**T5 / T6 / T7**
20. **KD-69 保持 open**：补丁已落、静态对照成立，但设备冷启动/OCR/Worker 冒烟无日志；ML Kit registrar 是下一个反射风险点。
21. **KD-71 保持 open**：设备材料称两例通过，但无日志；且测试行号在记录时点漂移到 `:545/:617`。
22. **KD-68 未动**：`feature/review` 仍用 `preview` + 固定 900s，`unschedulableCount` 无生产消费者。
23. **T7 文档**：KD-66/67/68/69/70/71 全部仍 open（无关闭证据）；契约 `:64` 与 `:72` 的 BUSY/排队不一致未修。

**热区 / 他线 / 待裁**
24. **G1/G2、5b/5c、阶段 6 UI、6B-3 注入、AUD-08** 均因热区未空出，本批不做（计划 §1）。
25. **AWAITING_VERIFICATION 独立 UI 态 + "重新核实/放弃"路径**（feature/library + core/domain 热区）仍遗留（批次 20 起）。
26. **T8 分支 D**：feature/library 33、feature/tutor 56、core/ui 34、SecondaryScreens M；本批只报告。
27. **真实 Provider / Wave X / 5B 出题回收接线**不在本批；G5 选项 C 与探索额度待后续裁定。
28. **32 位设备（armv7/x86）不可安装**为既采决策，本批不回退；minSdk 名义 23 与有效机型面（64 位 only）继续显式化。

## 7. 提交清单建议（记录员只读核对 git status 后列出；**提交由主会话执行**）

**前置条件**：主会话在提交前复跑一次单测门/强门并留 `exit=0` 日志；设备红例（T1 4/4、T2 P2）作为已知项随记录入册，不阻塞提交但不得写成全绿；AIDL 只允许单文件编译。

本批文件实测 **37 路径**（代码/测试/工具 26 + 文档 11），按 ≤6 路径/块分块：

- **块 1（T1 AIDL + manifest/guard）**：`app/src/main/aidl/com/tingyun/smartmistakebook/visualjs/IVisualJsService.aidl`、`app/src/main/aidl/com/tingyun/smartmistakebook/visualjs/IVisualJsCallback.aidl`、`app/src/main/AndroidManifest.xml`、`app/src/main/kotlin/com/tingyun/smartmistakebook/SmartMistakeBookApplication.kt`、`app/build.gradle.kts`
- **块 2（T1 Service/protocol）**：`app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsProtocol.kt`、`.../VisualJsService.kt`
- **块 3（T1 client + 设备用例）**：`app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsClientEngine.kt`、`app/src/androidTest/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsIsolationInstrumentedTest.kt`
- **块 4（T2 分类器 + 测试）**：`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualJsFailureClassification.kt`、`core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualJsFailureClassificationTest.kt`
- **块 5（T2/T6 引擎 + 设备用例）**：`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/QuickJsVisualExecutionEngine.kt`、`core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/visual/QuickJsVisualExecutionEngineInstrumentedTest.kt`
- **块 6（T3 构建/钉版）**：`build.gradle.kts`、`gradle/libs.versions.toml`、`tools/ci/check_dependency_pins.py`、`tools/tests/test_check_dependency_pins.py`、`.github/workflows/android-check.yml`
- **块 7（T4 域层）**：`core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/AnswerKeyVerificationUpgrade.kt`、`.../AdaptiveSelectionConsumption.kt`、`.../AdaptiveQuestionSelector.kt`、`core/domain/src/test/kotlin/com/tingyun/smartmistakebook/core/domain/AnswerKeyVerificationUpgradePolicyTest.kt`
- **块 8（T4 数据层）**：`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/KnowledgeQuizItemVerificationUpgrader.kt`、`.../study/AdaptiveSelectionAssembler.kt`、`core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/study/KnowledgeQuizItemVerificationUpgraderTest.kt`、`.../study/AdaptiveSelectionAssemblerTest.kt`
- **块 9（研究 R1–R4 + 基线）**：`docs/research/2026-10-26-visualjs-isolated-process.md`、`docs/research/2026-10-26-dependency-governance.md`、`docs/research/2026-10-26-quickjs-oom-semantics.md`、`docs/research/2026-10-26-r8-workmanager-room.md`、`docs/research/2026-10-26-batch-22-baseline.md`
- **块 10（契约 + KD + 决策台账）**：`docs/current/visual-generation-contract.md`（**热区重叠，计划/工单授权；整文件纳入并注明**）、`docs/known-defects.md`（**开工即脏 M：T2/T4/T5/T6/T7 + 本记录；整文件纳入**）、`docs/agent-first-refactor-decisions-2026-09-23.md`（**开工即脏 M：T7 + 本记录 T4 语义；整文件纳入**）
- **块 11（主线 + 算法台账 + 本记录）**：`docs/REFACTOR-MASTER-LINE.md`（**开工干净；本记录同步顶部/4E/补修/5B 四行**）、`docs/research/algorithm-version-ledger.md`（**开工干净；本记录追加 §3.32 零 bump**）、`docs/research/2026-10-26-batch-22-record.md`（本文件）

**明确排除**（他线在飞，一律不动）：`app/src/main/kotlin/com/tingyun/smartmistakebook/SmartMistakeBookRoot.kt`（他线动画引用未跟踪 `core/ui/ClaudeMotion.kt`）；app 其余开工即脏 M（LearningMasteryScreen/ReviewReminderCoordinator/ReviewReminderScreen/SchedulingSettingsScreen/StartupStateBanner/SmartMistakeBookDestinations 等）；`SecondaryScreens.kt`；feature/tutor|library|review|capture 全部；core/ui 全部；core/domain 他线 4 M（BatchImportRepository/MistakeDetailRepository/SchedulingEvaluation/TutorJudgedReview）；core/data 他线 M（`RoomMistakeDetailRepository.kt`、`RoomTutorToolRunner.kt`、`knowledge/dense/DenseRecallAssembly.kt` 与 8 个测试面 M、资源/夹具 11）；core/database 8 M；core/export 5 M；`settings.gradle.kts`；`.gitignore`；docs/current 其余 M；docs/research 他线未跟踪记录（`2026-10-26-kb-*`、`2026-10-26-dense-union-fusion-precedents.md`、`2026-10-26-ui-copy-refinement.md` 等）；docs/superpowers 他线 M；`tools/kb*`、`tools/tests/test_kb_merge_candidate_verdicts.py`、`knowledge-production/**`；`build/` 设备/门禁日志（无本批日志）与 `tmp/verify_batch22_*.py`（临时复核脚本，不入提交；复跑命令见 §4）。

**同步文档说明**：`docs/superpowers/plans/2026-10-19-product-readiness-followup-plan.md` 本批**未涉**（PRD-09 harness 无改动，T4 不触 PRD-09），故不纳入提交；`docs/REFACTOR-MASTER-LINE.md` 与 `docs/research/algorithm-version-ledger.md` 由本记录按"顶部/4E/补修/5B"与"§3.32 零 bump"同步。

**安全边界**：本文件不含任何密钥/令牌/凭据；Mimosa 无完整扫描结论，不宣称项目安全；提交分块建议由主会话执行，非快进 merge 不 rebase。

## 附录：本记录员实际执行的只读核对（可复跑；未运行 Gradle/设备/任何 git 写命令）

```bash
# 计划/基线/批21记录/四研究文档全文或头部读取（Read/sed/grep）
git rev-parse --short HEAD                 # 41421c0d
git rev-parse --short origin/main          # 41421c0d
git status --porcelain | wc -l             # 673
git status --porcelain | grep -c '^ M'     # 208
git status --porcelain | grep -c '^??'     # 465

# 热区计数（记录时点）
for d in feature/library feature/tutor core/ui; do printf "%s " "$d"; git status --porcelain -- "$d" | wc -l; done
#   → 33 / 56 / 34
git status --porcelain -- app/src/main/kotlin/com/tingyun/smartmistakebook/SecondaryScreens.kt
#   → " M .../SecondaryScreens.kt"

# schema / 版本串干净性
git status --porcelain -- core/database/schemas \
  core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabase.kt \
  core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/ModelTasks.kt
#   → 无输出（零 DB/请求 schema 变化）

# 锁文件 / verification-metadata
find app core feature benchmark gradle build-logic -maxdepth 2 -name "gradle.lockfile"
#   → 无输出
ls gradle/verification-metadata.xml
#   → No such file or directory

# AIDL（单文件编译，repo 路径；PowerShell 引号）
#   aidl.exe --lang=java -p<framework> -o<out> -I<repo>/app/src/main/aidl \
#     <repo>/app/src/main/aidl/.../IVisualJsService.aidl
#   → SERVICE_REPO_EXIT=0
#   .../IVisualJsCallback.aidl → CALLBACK_REPO_EXIT=0
#   （双输入形式 aidl.exe A.aidl B.aidl 会把 B 当 OUTPUT 覆盖；已复现，禁止）

# 钉版门（实跑；非 Gradle）
python tools/ci/check_dependency_pins.py
#   → OK: quickjs checksums, upstream POM requirement and resolved runtime pins match
#   → resolved: quickjs=['1.0.15'] kotlin-stdlib=['2.3.21']; PIN_EXIT=0
PYTHONPATH=tools python -m unittest discover -s tools/tests -p test_check_dependency_pins.py -v
#   → Ran 14 tests ... OK

# T1 源码/测试编译（实跑；缓存 Kotlin 2.3.21，非 Gradle）
python tmp/verify_batch22_visualjs_compile.py       # → COMPILE_OK, exit 0
python tmp/verify_batch22_visualjs_test_compile.py  # → COMPILE_OK, exit 0

# T4 JVM（实跑；非 Gradle）
python tmp/verify_batch22_t4.py
#   → AnswerKeyVerificationUpgradePolicyTest OK (4)
#   → KnowledgeQuizItemVerificationUpgraderTest OK (9)
#   → AdaptiveSelectionAssemblerTest OK (12)
#   → AdaptiveQuestionSelectorTest OK (21)
#   → EloCorrectnessPredictorTest OK (14)
#   → OK: batch-22 T4 JVM tests passed; EXIT=0

# T2 JVM 分类矩阵（只读 XML，非本轮运行）
grep -o 'tests="[0-9]*" skipped="[0-9]*" failures="[0-9]*" errors="[0-9]*"' \
  core/data/build/test-results/testDebugUnitTest/TEST-...VisualJsFailureClassificationTest.xml
#   → tests="14" skipped="0" failures="0" errors="0"（mtime 12:33:35）

# 关键源码锚点
grep -n "isolatedProcess\|android:process\|<service" app/src/main/AndroidManifest.xml
#   → :68-72（exported=false / isolatedProcess=true / process=:visual_js）
grep -n "dup\|readBoundedRequest" app/src/main/kotlin/.../visualjs/VisualJsService.kt
#   → :194-198 / :254 / :440-462
grep -n "rebuildConsumed\|connectLocked" app/src/main/kotlin/.../visualjs/VisualJsClientEngine.kt
#   → :98 / :482-519 / :589-633
grep -n "stateLock\|inFlight" core/data/src/main/kotlin/.../visual/QuickJsVisualExecutionEngine.kt
#   → :157 / :198-203 / :245-256 / :269-286
grep -n "proguardFiles" app/build.gradle.kts
#   → :67 / :75 / :84
grep -n "@Test" app/src/androidTest/kotlin/.../visualjs/VisualJsIsolationInstrumentedTest.kt
#   → :87 / :132 / :157 / :202 / :223（5 例）
grep -n "fun closeDuringAnInFlight\|fun concurrentCloseAndExecute" \
  core/data/src/androidTest/kotlin/.../visual/QuickJsVisualExecutionEngineInstrumentedTest.kt
#   → :545 / :617

# 构建产物存在性（只读）
ls -la app/build/outputs/apk/strictOffline/{debug,internal,benchmark,release}/
#   → app-strictOffline-{debug,internal,benchmark,release}.apk（09:13/09:34/09:35/09:43）
ls -d build/device-track-batch21 build/device-track-batch22
#   → 只有 batch21；batch22 不存在（本批设备结论无日志）
```

> 未执行：任何 `./gradlew`、`adb`、`am instrument`、`connectedAndroidTest`；任何 git 写命令。门禁/设备/研究结论均标注来源（工单材料/设备轨 evidence/修复环材料/研究文档），不冒充本记录员实跑。

## 8. 主会话收口（2026-10-26，实跑证据）

- **单测门**（11 模块 `--continue`，主会话实跑）：**exit=0**（BUILD SUCCESSFUL，6 executed / 222 up-to-date）。
- **AIDL 权威判据**：`:app:compileLocalFirstDebugAidl` 随单测门真实执行通过——重建的 `IVisualJsCallback.aidl` 语法与 import 均有效（记录员与主会话均曾误用 aidl.exe 双输入形式覆盖源文件；主会话已按 09:02 生成 Java 签名重建并验证，源文件 sha256 前后不变。**双输入形式属 aidl 的 INPUT+OUTPUT 用法，以后禁用，AIDL 一律以 Gradle 任务验证**）。
- **强门**（六 androidTest 编译 + `:app:assembleLocalFirstDebug`）：**exit=0**（BUILD SUCCESSFUL，12 executed / 260 up-to-date）。
- **提交**：`69e5f591`（37 文件，8,429 插入/210 删除），按 §7 的 11 块分块 add；排除项未触碰。
- **推送**：GitHub 443 时段性不可达（连接重置 + 连接超时），重试后远端出现 `Everything up-to-date`——包大概率已在某次重试中上传成功；后台循环以 `ls-remote` 复核，最终以远端 hash 为准。
- **本批不声称全绿**：T1 隔离进程设备功能门修复前 4/4 红（dup 修复后复跑 UNVERIFIED）；T2 设备 20 例仅 P2 红（KD-70 open）；T3 锁文件未生成（KD-72）；stdlib 下压未证（KD-73）；设备日志未落盘（KD-74）；KD-66/67/68/69/71 保持 open。
- Mimosa 提交仍无完整扫描结论（scanner_enobfs）——不宣称项目安全。
