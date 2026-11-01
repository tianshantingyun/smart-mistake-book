# 批次 25 基线核对（2026-10-27，基线核对员）

> **只读核对**：本轮未运行 Gradle、未编译、未执行任何 JVM/仪器化/设备用例、未 `git fetch`、未做任何 git 写操作（本文件是唯一写盘产物）。设备面只执行了只读 `adb` 查询。对照计划：`.zcode/plans/plan-2026-10-27-batch-25-race-warmup-queue.md` §0（`:3-9`，下称"计划"）。
>
> **快照时点**：2026-10-27 00:30:38 +0800（`2026-10-26T16:30:38Z`），`git status --porcelain` 冻结 **673 行（202 M + 471 ??）**；冻结时本批落点 porcelain 全为 0。核对结束落盘本文后复查（00:38）为 **675 行（202 M + 473 ??）**：新增的 2 个 ?? 分别是本文档自身与**他线在核对期间新建的 `core/data/src/androidTest/.../core/data/knowledge/TeachingMaterialAllocationInstrumentedTest.kt`**（非本批 visual 落点，排提交须排除）；M 计数不变（202），落点仍全部干净。
>
> "干净"定义：`git status --porcelain -- <path>` 无输出（不代表内容无缺陷）。

---

## 1. HEAD / 工作树 / DB schema / 请求 schema

| 项 | 实测 | 证据（命令/路径行号） |
|---|---|---|
| HEAD | `d783ae846526b2da3fbe0a041a3695bf23188820`（`main`） | `git rev-parse HEAD` |
| origin/main | `d783ae846526b2da3fbe0a041a3695bf23188820`（**与 HEAD 相同**） | `git rev-parse origin/main`；**本轮未 `git fetch`**，这是本地远端跟踪引用，远端实时 hash 未核（UNVERIFIED） |
| ahead/behind | `0 0`（`origin/main...HEAD`） | `git rev-list --left-right --count origin/main...HEAD` |
| HEAD 构成 | `d783ae84` 是**纯文档提交**：`docs/research/2026-10-26-batch-24-record.md` +14 行；父提交 `aabae781`（批次 24 主提交） | `git show --stat --oneline d783ae84`；`git log --oneline -4` |
| 工作树（冻结 00:30:38） | **202 M + 471 ?? = 673 行；staged = 0** | `git status --porcelain \| wc -l`=673；`grep -c '^ M'`=202；`grep -c '^??'`=471；`git diff --cached --name-only \| wc -l`=0 |
| DB schema | `STUDY_DATABASE_VERSION = 69`（`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabase.kt:134`；`version = STUDY_DATABASE_VERSION` 于 `:233`）；导出目录共 **69 个 schema 文件**，最高 **`69.json`**；`70.json`/`71.json` 不存在；`StudyDatabase.kt` 与 `core/database/schemas/` 均 **git 干净** | `grep -n` + `ls` + `git ls-files '*70.json' '*71.json'`（空）+ `git status --porcelain -- StudyDatabase.kt schemas/`（空） |
| 请求 schema | `TUTOR_VISUAL_CONTEXT_SCHEMA_VERSION = 18`、`CURRENT_SCHEMA_VERSION = TUTOR_VISUAL_CONTEXT_SCHEMA_VERSION`（`core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/ModelTasks.kt:546-547`）；`ModelTasks.kt` **git 干净**；`core/model` 唯一脏项是未跟踪 `core/model/gradle.lockfile` | `grep -n` + `git status --porcelain -- core/model/`（仅 `?? core/model/gradle.lockfile`） |
| VisualJS wire schema | `VisualJsWire.PROTOCOL_VERSION: Int = 1`（`app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsProtocol.kt:31`） | `grep -n` |

### 1.1 工作树按顶层归组（M，实数）

- **M（202）**：feature **96**（tutor **37** / library **30** / capture **15** / review **11** / profile **3**）、core **38**（core/data **10** / core/ui **11** / core/database **8** / core/export **5** / core/domain **4**）、docs **37**、app/src **23**、tools **4**、仓库根 **3**（`.gitignore`、`dynamic_gui_architecture_plan.md`、`settings.gradle.kts`）、knowledge-production **1**。合计 96+38+37+23+4+3+1 = 202。
- 与批次 24 基线（200 M）相比 **+2**：feature/library 29→30、docs 36→37；其余分组计数不变。

### 1.2 未跟踪（??）概览

- **??（471）**：feature **38**、core **30**、docs **18**、tools **12**、knowledge-production **11**、app **5**（`app/gradle.lockfile` + 3 个 androidTest + `app/src/main/assets/`）、**仓库根级单段条目 245**，其余 **112** 条为单层散点目录（`.agent_*`、`audit_tmp`、`out`、`output`、`tmp` 等）。
- `core/data` 的 ?? 构成变化：批次 24 基线时的 `core/data/src/test/.../study/UsageMaterialAllocationTest.kt` 已被 git 跟踪（`git ls-files` 命中 1，盘上仍在），该目录 ?? 曾降为 1；**核对期间（00:36:45）他线又新建** `core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/TeachingMaterialAllocationInstrumentedTest.kt`（KB/study 线，非本批 visual 落点）。故 core/data ?? 现为 **2**：`gradle.lockfile` + 该新测试。

---

## 2. 热区快照 vs HOT_ZONES（计划 §0 第 4 条）

**计划全文无 `HOT_ZONES` 标题/标识**：`grep -n "HOT_ZONES" .zcode/plans/plan-2026-10-27-batch-25-race-warmup-queue.md` 零命中（exit=1），与批次 22/23/24 同型。以下按计划 §0 的"热区不变"清单逐项对照（冻结时点）：

| 计划 §0 热区 | 当前实测（M / ??） | 判定 |
|---|---|---|
| feature 四模块 | tutor **37/22**、library **30/13**、capture **15/1**、review **11/1** | ✓ 全部在飞 |
| core/ui | **11 M + 24 ??**（23 新文件 + `gradle.lockfile`） | ✓ |
| Root | `SmartMistakeBookRoot.kt` M、`SmartMistakeBookDestinations.kt` M | ✓ |
| SecondaryScreens | `SecondaryScreens.kt` M | ✓ |
| settings.gradle.kts | **M**（`include(` 在 `:19`，`:core:markdown` 于 `:27` 仍 include；未跟踪模块仍在） | ✓ |
| core/export | **5 M**（与计划"五 M"逐数相符：`build.gradle.kts`、`MistakeExportJobRunner.kt`、3 个 test） | ✓ |

**本批基线条目要求点名（开工即脏）**：

| 对照项 | 当前实测 | 判定 |
|---|---|---|
| core/domain | **4 M + 1 ??**（lock） | **开工即脏**；全路径见 §2.1 |
| core/data | **10 M + 1 ??**（lock） | **开工即脏**；全路径见 §2.2；visual 子目录干净（§3） |
| core/database | **8 M + 1 ??**（lock） | **开工即脏**；计划热区未列，全路径见 §2.3 |

**计划热区未列、当前实脏（共存面）**：feature/profile **3 M/1 ??**；docs **37 M/18 ??**；tools **4 M/12 ??**；knowledge-production **1 M/11 ??**；app 整体 **23 M/5 ??**（计划只点名 Root/SecondaryScreens）；`core/markdown/` 整个目录未跟踪（含 `build.gradle.kts` 与 `gradle.lockfile`）。

### 2.1 core/domain 开工即脏 M 全路径（4 个）

1. `core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/BatchImportRepository.kt`
2. `core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/MistakeDetailRepository.kt`
3. `core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/SchedulingEvaluation.kt`
4. `core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/TutorJudgedReview.kt`

core/domain 无其他 M；另有未跟踪 `core/domain/gradle.lockfile`。**T1/T2 的落点不在此模块**；若 warm-up/ready 研究要动 `VisualExecutionBudget`（`core/domain/.../VisualExecutionEngine.kt`），该文件当前干净，但所在模块不是。

### 2.2 core/data 开工即脏 M 全路径（10 个）

main（2）：

1. `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/mistake/RoomMistakeDetailRepository.kt`
2. `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/RoomTutorToolRunner.kt`

androidTest（5）：

3. `core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/capture/BatchImportRepositoryInstrumentedTest.kt`
4. `core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/capture/SplitImportCommitReceiptInstrumentedTest.kt`
5. `core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/capture/SplitImportReceiptReconciliationInstrumentedTest.kt`
6. `core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/model/ModelTaskRepositoryInstrumentedTest.kt`
7. `core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/study/StudySubmissionWithoutFixtureInstrumentedTest.kt`

test（3）：

8. `core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/GoldenRetrievalJvmTest.kt`
9. `core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/model/OpenAiNativeToolsProtocolTest.kt`
10. `core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/model/TutorToolRequestDualParseTest.kt`

`core/data` 另有 `?? core/data/gradle.lockfile`。**T3 的 `core/data/.../visual/` 目录逐个实测干净**（§3），排提交时必须显式按路径排除上面 10 个 M 与 lockfile。

### 2.3 core/database 开工即脏 M 全路径（8 个）

1. `core/database/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/database/LibraryFtsCountPathInstrumentedTest.kt`
2. `core/database/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/database/LibraryFtsCountPathScale50kInstrumentedTest.kt`
3. `core/database/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/database/LibraryFtsRankingPathInstrumentedTest.kt`
4. `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/RoomBatchImportStore.kt`
5. `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/TutorConversationDatabaseContract.kt`
6. `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/dao/BatchImportDao.kt`
7. `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/dao/TutorConversationDao.kt`
8. `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/port/CleanRedrawTaskPort.kt`

`core/database` 另有 `?? core/database/gradle.lockfile`；`StudyDatabase.kt` 与 `schemas/` 本身干净。

---

## 3. 批次 25 落点脏态逐个核对

`git status --porcelain -- <path>` 逐个实测，**全部空（干净）**：

| 落点 | porcelain | 本轮实读补充事实 |
|---|---|---|
| `app/src/main/kotlin/.../visualjs/` 全部文件 | **空** | 3 个已跟踪 `.kt`：`VisualJsClientEngine.kt`（**938 行 / 43,770 B**）、`VisualJsProtocol.kt`（406 行 / 19,083 B）、`VisualJsService.kt`（574 行 / 25,240 B）；`git log -- <engine>` 最后改动为 `aabae781` |
| `app/src/main/aidl/.../visualjs/` | **空** | `IVisualJsService.aidl` 37 行、`IVisualJsCallback.aidl` 32 行，均在 `git ls-files` 内 |
| `app/src/androidTest/.../visualjs/` | **空** | `VisualJsIsolationInstrumentedTest.kt` 26,794 B，**6 个 `@Test`**（`:101/:146/:171/:216/:251/:266`）；`aabae781` 改过（`git show --stat` 列出 120 行变更） |
| `app/src/main/AndroidManifest.xml`（相邻面） | **空** | 服务声明 `:69-72`（`isolatedProcess=true`、`:visual_js`） |
| `core/data/src/main/.../visual/`（7 文件） | **空** | `DefaultFigureGenerationCoordinator.kt`、`FigureGenerationAuditRecorder.kt`、`FigureGenerationReconciler.kt`、`QuickJsVisualExecutionEngine.kt`、`RoomFigureDispatchLedger.kt`、`RoomFigureGenerationAuditRecorder.kt`、`VisualJsFailureClassification.kt` |
| `core/data/src/test/.../visual/`（10 文件） | **空** | 含 `QuickJsVisualExecutionEngineJvmTest.kt`、`VisualJsFailureClassificationTest.kt`、`FigureGenerationCoordinatorTest.kt` |
| `core/data/src/androidTest/.../visual/`（4 文件） | **空** | `FigureGenerationAuditFixtures.kt`、`FigureGenerationReconcilerInstrumentedTest.kt`、`NativeLibraryPageSizeInstrumentedTest.kt`、`QuickJsVisualExecutionEngineInstrumentedTest.kt` |
| `docs/known-defects.md` | **空** | **2,821 行**；KD-66/KD-76 原文见 §4 |
| `docs/current/visual-generation-contract.md` | **空** | **218 行**；章节 §1…§10（`grep '^## '`） |

**模块级提醒**：`core/data` 整体仍有 10 M + 1 ??、`core/domain` 4 M + 1 ??、`core/database` 8 M + 1 ??、app 23 M + 5 ??；"落点干净"只对上面点名的文件/目录成立，不能读成"模块干净"。

---

## 4. 原文抄录

> 以下均为按行区间从当前文件**逐字提取**（非转述）；行号为本次冻结时点。

### 4.1 KD-66（`docs/known-defects.md:2573-2613`；`:2614` 为条目间空行）

```markdown
## KD-66 (open, 2026-10-26 登记（批次 21 T3/复核发现 #1/#13/#21；高）) · QuickJS issue #240 使引擎硬期限不能返回：`execute()` 永不返回、`close()` 卡死、`inFlight` 不复位

**Symptom（设备实跑 + 源码/上游源码核对）.** quickjs-kt-android 1.0.15（钉 commit `a081755`）的 issue #240：脚本用一个 job 先 `Promise.resolve().then(f)` 排队下一个再自旋时，`evaluate()` 在 `awaitEvaluateResult` 的排水循环内持 `jsMutex` 永不返回，另一线程的 `close()` 也抢同一把锁卡死；`evaluationTimeoutMillis`/`interruptEvaluation()` 都不能让队列排空。批次 21 设备轨真跑：单独 `issue240...` 用例 `timeout 60` → `Terminated`、`AM_EXIT=124`（`build/device-track-batch21/step2-quickjs-issue240-real.log`）；类级运行 7 个点后无 summary，线程栈显示 `quickjs-visual-js` 卡在 `executePendingJob←awaitEvaluateResult←evalAndAwait`、`quickjs-visual-force-close` 卡在 `QuickJs.close→yieldThread`（`step2-thread-dump.txt:458-560`）。`QuickJsVisualExecutionEngine` 的 `withTimeout(hard+500ms)` 无法抢占 `withContext(jsDispatcher)` 内的同步 JNI；`finally` 不执行 → `inFlight=true` 永久，后续 `execute()` 只回 BUSY（T6 已把 `inFlight` 改为在飞事务指针；#240 下 `finally` 仍不执行，指针同样不复位，行为不变）。

**Impact.** high（调用方拿不到 1500 ms 硬上界；任何不可信图程序可拖死一个引擎实例。批次 22 T1 已落 L2 进程级硬终止代码；批次 23 设备轨已验证 4/5 路径（隔离往返、watchdog 硬杀、死亡后重建均绿）。批次 24 已修 `abandon()` 死亡确认与冷启动首连预算的代码缺陷，设备轨 E-01/E-01b 在 emulator-5554 上 **6/6**（两代 debug APK，见下方批次 24 设备轨 Progress）；关闭门（hard+500 ms）仍未达成，故本条维持 open）。

**Progress（2026-10-26 批次 22 T1，app 模块 + Application guard；非 Gradle、非设备）.** L2（唯一进程级硬终止层）代码已落，**KD-66 保持 open：关闭门未达成，JS 仍不可用/不广告**（设备执行见下方批次 23 Progress）。

- **落点**：`app/src/main/AndroidManifest.xml` 注册非导出 `VisualJsService`（`android:isolatedProcess="true"`、`android:process=":visual_js"`，无 intent-filter）；`app/src/main/aidl/.../visualjs/IVisualJsService.aidl`（`initialize`/`execute`/`terminate`/`protocolVersion`/`workerUid`/`currentGeneration`，源码与 JSON 走 `ParcelFileDescriptor` 有界管道）与 `IVisualJsCallback.aidl`（`oneway` 结果/致命回调）；`app/src/main/kotlin/.../visualjs/VisualJsService.kt` 的独立 watchdog（`ScheduledThreadPoolExecutor` + 令牌 CAS + `Process.killProcess(Process.myPid())`）、`terminate(generation)` 的 generation 核对与过期 no-op、正常完成先失效令牌、宿主 Binder 死亡观察；`VisualJsClientEngine.kt`（L3）实现 `VisualExecutionEngine` 端口。
- **启动隔离**：`SmartMistakeBookApplication.onCreate` 在 `super.onCreate()` 后、任何初始化副作用之前用 `Process.myUid() != applicationInfo.uid`（API 28+ 叠加 `Process.isIsolated()`）短路，位置先于 Room/备份/维护/Provider；构造期字段审计结论（`by lazy` 不触达、协程作用域只分配）写在代码注释；**ContentProvider/AndroidX Startup 实际装配面未 `dumpsys` 审计**。
- **调用方上界（如实，批次 22 时点；已被批次 24 Progress 取代）**：`VisualJsService` 的 watchdog 在 ~1500 ms SIGKILL；宿主 `VisualJsClientEngine` 的 2500 ms 只包住一次会话，`ensureConnected()` 的 5000 ms 连接预算在期限之外，期限到点后的死亡确认再等 ≤1000 ms → 当时口径为**最坏约 8.5 s，不是 1500/2500 ms 硬上界**。批次 24 已把首连改为 15_000 ms、重建 10_000 ms、死亡确认 2_000 ms，最坏约 19.5 s（见下方批次 24 T1/设备轨 Progress）；**8.5 s 不是当前值**。并发调用还会在 client 的 `executeMutex` 上无超时排队，排队方等待不设上界（与引擎端口的 BUSY 背压语义不同）。死亡未确认时不重建、后续请求回 ENGINE_CLOSED（防并发放大进程数）。
- **验证状态（2026-10-26 复核更新）**：设备轨已执行（修复前）→ **4/4 红**。首例断言原文：`first execution: request pipe ended before a complete envelope arrived expected:<0> but was:<1>`（emulator-5554，`am instrument` 直跑 `workerIsIsolatedAndAProgramRoundTripsWithoutBeingKilledLater`，pre-fix APK）。根因经设备探针定位：`ParcelFileDescriptor.createReliablePipe()` 的 read end 带一条状态 comm 通道；宿主在 `execute()` 返回后立刻关闭自己那份 read end，服务端稍后读到 EOF 时 `AutoCloseInputStream.checkError()` 把这次正常关闭读成 `IOException: Remote side is dead`，**64 KiB 数据已完整缓冲仍抛**；服务端把该 IOException 折成 `RequestRead.Failed` → `ENGINE_CLOSED`。修复（本批复核 #2）：服务端在 Binder 线程先 `request.dup()`（dup 出的数据 fd 不带 comm 通道）再投递执行线程；宿主 client 与测试 harness 读结果 payload 前同样先 `dup()`。**修复后的设备复跑已执行（批次 23，见下方 Progress 与 E-01b/E-01e）**：`libquickjs.so` 装载、`killProcess` 权限、watchdog 杀进程与重建后无 BUSY 已有绿证据（4/5），仅 `abandon` 路径红。AIDL 源文件在复核中发现 `IVisualJsCallback.aidl` 被误覆盖为 `IVisualJsService` 的生成 Java stub（`aidl.exe` 双输入调用的输出覆盖了第二份输入）；已恢复为正确 `oneway` 回调接口，并用 `aidl.exe` 对两份 AIDL 分别编译（exit=0）验证。**2026-10-26 记录时点复核**：当时测试集为 5 例（第 5 例 `theClientRebuildsAtMostOnceAfterConfirmedAbandons` 已在批次 23 设备执行，结果为红，见下方 Progress）；批次 24 起测试集为 6 例（新增冷启动用例），设备结果见下方批次 24 设备轨。记录员复跑 `aidl.exe` 时误用双位置参数（`INPUT [OUTPUT]`）再次覆盖该文件，已按 R1 §4.2 与生成 Java 签名恢复（1340 B，UTF-8），单文件 `--lang=java` 编译两份 AIDL 均 exit=0；后续禁止双输入调用。其 watchdog 用例以 ≤6 s 判死，**弱于本条 Reopen condition 的 hard+500 ms**，不能作为关闭证据。
- **本轮新增用例（设备已执行，红）**：`theClientRebuildsAtMostOnceAfterConfirmedAbandons`（首次 `abandon` 确认死亡后允许一次重建；第二次确认死亡后不得再 bind），用新增 `VisualJsClientEngine.abandonForTest()` 走生产 `abandon` 路径。批次 23 设备轨真跑结果：`AssertionError: the first abandon did not confirm death`（`VisualJsIsolationInstrumentedTest.kt:210`）——服务进程在 attach 后 16 ms 内被 `terminate(generation)` 的 SIGKILL 杀死，但客户端 1 s 内没有收到 binder death；根因是 `VisualJsClientEngine.abandon()` 先 `unlinkDeath` 再等 `link.death`（`VisualJsClientEngine.kt:570-571`）。E-01c 单方法稳定复现，E-01b/E-01d/E-01e 四次独立运行同形；修复方向是死亡确认顺序/判定，不是放宽断言。主源码与测试源码此前已用缓存 Kotlin 2.3.21 直接编译通过（`tmp/verify_batch22_visualjs_compile.py` / `..._test_compile.py` → COMPILE_OK）。
- **未做**：生产装配点（JS 仍 UNAVAILABLE、工具面/提示词/能力快照零改动）、服务内内容度量（`UnwiredVisualContentMeasurer`，只有纯几何程序可通）、4E.2 交互图/4E.3、上游 issue #240 修复（仍 OPEN）。

**Progress（2026-10-26 批次 23 设备轨；supersedes 上方"复跑 UNVERIFIED / 第 5 例未执行"的记录时点表述）.** 设备证据已按 R4 规范落盘（`docs/research/evidence/2026-10-26-batch-23/`）。dup 修复后的 `VisualJsIsolationInstrumentedTest` 在 emulator-5554（API 34，x86_64，APK sha256 `e5ec3a89…bb441`）真跑两轮：**5 例全部执行，4 绿 1 红**。

- **绿**：`workerIsIsolatedAndAProgramRoundTripsWithoutBeingKilledLater`（隔离 UID 成立 + AIDL 往返成功）、`aStaleGenerationIsRejectedAndAStaleTerminateIsANoOp`（过期 generation/terminate no-op）、`theWatchdogKillsAHungWorkerAndAFreshGenerationHasNoBusyState`（1500 ms 硬期限 `killProcess` + 新 generation 无 BUSY）、`theClientRebuildsOnceAfterADeathAndTheNewWorkerIsNotBusy`（死亡确认后重建一次，新 worker 无 BUSY）。
- **红**：`theClientRebuildsAtMostOnceAfterConfirmedAbandons`（`the first abandon did not confirm death`）——见上方新增用例段与 E-01c；这是 client `abandon()` 的结构性缺陷，不是 dup 修复回退。
- **冷启动对照**：重启后立即跑 5/5 红（隔离进程 attach 4.6–7.5 s > 客户端 5 s 连接预算，E-01d）；沉降约 2 min 后回到 1/5 红（E-01e），证明加载态超时与结构性失败已分离。
- **结论**：`libquickjs.so` 装载、`killProcess` 权限、watchdog 杀进程、死亡后重建均已有设备绿证据；**KD-66 仍 open**，原因只剩关闭门——现有 watchdog 用例以 ≤6 s 判死，弱于 Reopen condition 的 hard+500 ms，且 issue #240 形状本身仍泄漏 daemon 线程（批次 23 E-02 的观察用例绿=已知形态，不是可终止）。abandon 缺陷建议另立 KD（本条只跟踪 #240 关闭门）。
- **证据**：`research/evidence/2026-10-26-batch-23/step01b-visualjs-isolation-rerun.md:94`、`step01e-visualjs-isolation-postboot-settled.md:85`、`step01c-abandon-confirmed.md:107`、`00-index.md` §3/§3.1；原始日志 `build/device-track-batch23/step01b-visualjs-isolation-rerun.log`（`Tests run: 5, Failures: 1`）、`step01e-visualjs-isolation-postboot-settled.log`（同）。

**（2026-10-26 批次 24 T3 核对）** 批次 23 的设备执行状态以**批次 23 E-01b/E-01e** 为准（dup 修复后 5 例全部执行、4 绿 1 红）；"复跑 UNVERIFIED / 第 5 例未执行"旧表述作废。**批次 24 设备轨 E-01/E-01b 已 6/6（两代 debug APK，见下方 Progress）**。**保持 open**：剩余关闭门只有 hard+500 ms 设备用例（现有 watchdog 以 ≤6 s 判死，不算），abandon 路径红已在批次 24 设备轨消失。

**Progress（2026-10-26 批次 24 T1，app `visualjs` client + 仪器化用例；非 Gradle、非设备）.** `abandon()` 的结构性缺陷与冷启动误判已在代码层修复；**设备复跑已由下方批次 24 设备轨 E-01/E-01b 完成（6/6）；本条仍 open：hard+500 ms 关闭门未动**。

- **abandon 死亡确认（修复 E-01c 的结构性红）**：`VisualJsClientEngine.abandon()` 不再先 `unlinkDeath` 再等 `link.death`。现在保留 death 注册，用"死亡事实（`markDead` 完成 `CompletableDeferred`）+ 轮询 `IBinder.isBinderAlive` + `DEATH_CONFIRM_TIMEOUT_MILLIS`（2 s）兜底"组合确认旧实例终结；超时分支若最后一刻发现 `isBinderAlive=false` 也先 `markDead`，保证 `death`/在飞请求有界完成（R1 §4.4 待收口点 1）；确认后才清空 link、由下一次 `execute` 在 `connectLocked` 扣**唯一一次**重建预算，只有未确认才置 `unavailable=true`（fail-closed）。`handleDeath` 在 `ABANDONED` 状态短路，不会为 abandon 自动 bind。**它消灭的失败**：批次 23 E-01b/E-01c 的 `the first abandon did not confirm death`（服务 16 ms 内已 SIGKILL，客户端 1 s 内收不到本应到达的 binder death）。
- **冷启动首连预算**：首连 5_000 → 15_000 ms，重建 5_000 → 10_000 ms（R1 §6.2：E-01b 的重建 0.6–0.9 s 只覆盖沉降态，重负载重建无实测）。设备实测冷启动 attach 4.6–7.5 s（E-01d），旧 5 s 会把加载态误判成结构性失败并永久 `unavailable`；15 s 覆盖实测上界 + 握手。**它消灭的失败**：设备重启后立即跑 5/5 红的首连误判。调用方最坏等待相应从约 8.5 s 变为约 19.5 s（首连 15 s + 宿主阶段 2.5 s + 死亡确认 2 s，另加 Binder 开销），仍不是 1500/2500 ms 硬上界。
- **仍未覆盖（如实）**：连接超时仍是一次失败即永久 `unavailable`（未实现 R1 §5.3 的 LOADING + 500 ms 退避重试），也未实现服务端 warm-up/ready；E-01d 的"首个 execute 时 QuickJS 原生装载超过 1500 ms 被 watchdog 误杀"（R1 §5.1/§5.4）本批未修。冷启动用例只证明首连 15 s 预算（E-01 已设备绿），不能证明 1500 ms 误杀已消失。
- **用例（5 例形状 + 1 例冷启动）**：`theClientRebuildsAtMostOnceAfterConfirmedAbandons` 在首次 abandon 重建后新增一次真实 `execute`，断言返回 `Rendered`（BUSY 会使其红），覆盖"重建后无 BUSY"；新增 `aColdStartAttachBeyondTheOldConnectBudgetStillRendersTheFirstProgram`（attach 回调推迟 7.6 s = 实测上界 + 100 ms，旧 5 s 预算下必红），断言首次 `execute` 直接成功且只连接一次。仪器化类共 6 例。**本批禁 Gradle，未执行任何 JVM/仪器化/设备用例**；"红→绿"是按旧代码路径的确定性推演，不是执行证据。主源码与测试源码已用缓存 Kotlin 2.3.21 非 Gradle 直编：`python tmp/verify_batch22_visualjs_compile.py` / `..._test_compile.py` → `COMPILE_OK`（exit=0）。
- **issue #240 真挂死仍按 watchdog 覆盖（如实）**：本批没有改引擎终止语义。`theWatchdogKillsAHungWorkerAndAFreshGenerationHasNoBusyState` 仍以 ≤6 s 观察 L2 SIGKILL，`theClientRebuildsOnceAfterADeathAndTheNewWorkerIsNotBusy` 仍以死亡后重建 + 无 BUSY 覆盖；两者都弱于 Reopen condition 的 hard+500 ms，不能作为关闭证据。#240 的自续 job 仍会泄漏 daemon 线程（批次 23 E-02 观察型用例绿是已知形态，不是可终止）。
- **证据/未落盘**：代码 `app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsClientEngine.kt:575-642`、用例 `app/src/androidTest/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsIsolationInstrumentedTest.kt:205-260`；历史红证据 `research/evidence/2026-10-26-batch-23/step01b-visualjs-isolation-rerun.md:94`、`step01c-abandon-confirmed.md:107`、`step01b-visualjs-isolation-rerun.md` 同批冷启动 `step01d-visualjs-isolation-coldboot.md:61-72`。**本批（T1）未执行设备命令；设备日志见下一节批次 24 设备轨**。

**Progress（2026-10-26 批次 24 设备轨，E-01/E-01b；supersedes 上方"设备复跑 UNVERIFIED / 5 例 4 绿 1 红"的记录时点表述）.** `VisualJsIsolationInstrumentedTest` 在两代 debug APK 上各跑一遍，**均 `OK (6 tests)`**：E-01 用 21:31 构建件（app sha256 `53c3a453…6eba`，`Time: 23.945`），E-01b 用 21:37 他线重建后的当前安装件（app sha256 `782cebd6…73c6`，`Time: 22.208`）；测试 APK 两轮同为 `343b637a…08c0e`。两轮均为 6 个用例逐个 `INSTRUMENTATION_STATUS_CODE` 1→0，崩溃签名 8 类全 0，`death was not confirmed` 告警 0。批次 23 红的 `theClientRebuildsAtMostOnceAfterConfirmedAbandons` 转绿（重建后真实 `execute` 返回 `Rendered`，第二次确认后不再 bind），冷启动 7.6 s 延迟 attach 用例也绿（首连 15 s 预算）。**这不关闭 KD-66**：类内 watchdog 用例仍以 ≤6 s 判死，弱于 hard+500 ms 关闭门；#240 的 daemon 线程泄漏不变；JS 仍不广告。证据：`docs/research/evidence/2026-10-26-batch-24/step01-visualjs-isolation.md:124`、`step01b-visualjs-isolation-rerun.md:102`、`00-index.md:43-44`/`:53-57`；原始日志 `build/device-track-batch24/step01-isolation.log`、`step01b-isolation-rerun.log`（均 `OK (6 tests)`）。

**（2026-10-26 复核修复 #3/#4；代码层，未跑 Gradle/设备）.** ① **death-before-registration 竞态**：`Link.onServiceConnected` 原先在 `dead`/`ABANDONED` 检查通过后无条件写 `state = CONNECTED`；若 `markDead` 在两步之间赢，`handleDeath` 已清 link 并写 DEAD，随后仍会被覆盖。现在 `markConnected` 与 `markDead` 共用 `stateLock`（`markDead` 先 CAS `dead` 再取锁），`connectLocked` 用 `isConnected()` 在锁内复查 `dead`；已死 link 不会被当连接返回。② **首连中 `close()` 的 15 s 等待**：`close()` 现在对当前 link 调 `markDead("the client was closed", unexpected=false)`，完成 `connected`/`death`、终结 activeCall 并解注册/解绑；首连或重建中的 `connectLocked` 立即返回 null，不再等满预算。**未修/仍如实登记**：连接超时仍一次失败即永久 `unavailable`（无 LOADING/500 ms 退避，P-04）、`ensureConnected()` 仍在宿主 2500 ms 期限之外、最坏等待约 19.5 s、`executeMutex` 排队无上界；这些是契约 §4:72 已写明的已知限制，不是本轮回归。**验证边界**：本轮未运行 Gradle/设备，竞态交错无确定性测试；只做了非 Gradle 源码编译检查（`tmp/verify_batch22_visualjs_compile.py` / `..._test_compile.py`，见结果）。

**Fix direction.** ①（批次 22 T1 已落代码，未关闭）`:visual_js` `isolatedProcess` + AIDL 控制通道 + `killProcess(myPid())` 进程级硬终止——唯一能终止任意循环的层；设备门见 Progress。② 上游发 >1.0.15 修复后升级并把观察型用例反转为"必须协作中断"断言。③ 引擎层维持 accept-the-leak：每事务独立 daemon dispatcher、强弃 close 放专用守护线程、失败即 fuse/BUSY，如实现 KDoc 所述。

**Reopen condition.** 4E.2 进程隔离落地或上游修复版升级后，以"issue #240 形状在 hard+500ms 内返回拒绝、且后续请求可用"的设备用例关闭；在此之前不得把 T3 标成运行时完成。（T1 的仪器化用例现以 ≤6 s 判死 worker，弱于本条件；关闭时仍以 hard+500 ms 的用例为准。）
```

### 4.2 KD-76（`docs/known-defects.md:2805-2821`）

```markdown
## KD-76 (open, 2026-10-26 批次 24 T5 登记（研究复核高危项）；高) · 隔离门 6/6 不含 warm-up/ready：冷启动首个 execute 仍可能被 1500 ms watchdog 误杀，按现判据可在首个用户请求必败时放行 JS 广告

**Symptom（读码 + 批次 23 设备证据）.** `docs/research/2026-10-26-4e2-entry-checklist.md:256` 把必须设备绿的隔离门定义为 `VisualJsIsolationInstrumentedTest` **6/6**，`:269` 进一步写"广告判定：**6/6 必须设备绿**"。但 6 例中没有任何一例模拟"冷启动首个 `execute` 在 CPU 竞争下被 1500 ms 服务硬期限杀掉"：

- 批次 24 新增的冷启动用例 `aColdStartAttachBeyondTheOldConnectBudgetStillRendersTheFirstProgram`（`app/src/androidTest/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsIsolationInstrumentedTest.kt:249-260`）只把 `onServiceConnected` 推迟 7.6 s（`COLD_START_ATTACH_DELAY_MILLIS=7_600L`，同文件 `:383`），断言首连预算与"首次 execute 成功"；其夹具 KDoc 明说"只推迟 attach 回调、不推迟 `bindService`"（同文件 `:379-381`），**没有制造首个 execute 内的 QuickJS 原生装载竞争**。
- 服务端 watchdog 在请求开始即 arm 1500 ms（`app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsService.kt:413-427`；常量 `SERVICE_HARD_DEADLINE_MILLIS=1500L`，`core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/VisualExecutionEngine.kt:126`）。批次 23 冷启动设备日志在同一时刻打出 `hard deadline (1500 ms) for request live-after-stale-terminate; killing the worker`（`docs/research/evidence/2026-10-26-batch-23/step01d-visualjs-isolation-coldboot.md:65`），同文件 `:72` 把它归因为冷启动负载（重启后负载 5.25、system_server/GMS 正在启动）。这与"首个用户请求在冷启动被误杀"是同一失败形状，不是 #240 自续 job。
- 契约已如实登记该缺口：`docs/current/visual-generation-contract.md:72` 写"冷启动首个 execute 的 1500 ms watchdog 误杀（E-01d）仍需 warm-up/ready，本批未修"；批次 24 T1 Progress 也写"冷启动用例只证明首连 15 s 预算（若设备跑绿），不能证明 1500 ms 误杀已消失"（`docs/known-defects.md:2602`）。

**Impact.** 高（广告门完整性 + 用户首个请求必败）：隔离门可以在**沉降态**跑绿（批次 23 沉降后 4/5 绿，E-01b/E-01e），而真实用户冷启动后的第一个请求仍可能因原生装载超过 1500 ms 被判 `HARD_DEADLINE_EXCEEDED` / 进程被杀。按 R3 的 6/6 判据，此时可以放行 JS 广告，广告承诺与实际失败直接冲突；且该失败发生在每个新装/重启用户的首个请求，不是边缘场景。

**Fix direction（实现形态需裁定；不得放宽 1500 ms 程序期限）.** 在服务端引入明确的 warm-up/ready 语义（二选一，需主会话裁定）：① 服务在 `onCreate`/首个请求前完成 `libquickjs.so` 装载与一次空程序 warm-up，ready 之前不 arm 程序 watchdog；② 首个请求的一次性原生装载单独计时并排除在 1500 ms 程序期限之外，装载完成后再 arm 同一 watchdog。无论哪种，**已装载后的真实程序执行仍必须 1500 ms 硬杀**，不得用"首请求放行"掩盖 #240 挂死。客户端只允许在 ready 后发首个用户请求；ready 超时/失败必须 fail-closed 并如实显示加载态，不得把加载失败写成程序错误。广告门前必须把 R3 的 6/6 升级为包含以下设备用例的判据（数量以主会话裁定为准）：③ 冷启动/CPU 竞争下首个 `execute` 返回 `Rendered`，且日志无该请求的 watchdog hard deadline；④ 负控——同一 warm 服务上挂死程序仍在 1500 ms 内 SIGKILL（不得因 warm-up 分支放宽）；⑤ 首连失败/ready 超时走明确加载态，不永久 `unavailable`（与 T1 已登记的 LOADING 缺口一致）。

**验收判据（未运行）.** 设备证据按 R4 规范落盘：设备冷启动（最好含低内存/高负载条件）→ 首个真实请求成功；同一设备 warm 后挂死程序仍 1500 ms 硬杀；`onFatal`/logcat 证明没有"首个请求被 hard deadline 杀掉"的签名。R3 广告门的 6/6 文本必须同步改为包含 warm-up/ready 用例，否则不得以 6/6 绿作为 JS 广告依据。JVM 可覆盖 ready 状态机与超时映射；设备用例不可由 JVM 代替。

**Reopen condition.** 设备证据证明冷启动（含 CPU 竞争）首个 `execute` 成功且不是靠放宽 1500 ms 程序期限获得；warm 服务的挂死负控仍在 1500 ms 内被杀；R3/契约的广告门文本已包含该用例并回链证据。在此之前 JS 继续不广告（本批纪律，计划 `.zcode/plans/plan-2026-10-26-batch-24-isolation-final-kd70.md:15`、`:17`）。

**交叉引用.** KD-66 跟踪 issue #240 的 hard+500 ms 关闭门；本条只跟踪"冷启动首个 execute 不被一次性装载误杀"的 warm-up/ready 缺口，不代替 KD-66，也不改变其关闭门。批次 24 T1 的 `abandon()` 修复与首连预算属另一失败形状，设备复跑见批次 24 E-01/E-01b（6/6），与本条的 warm-up/ready 门无关。
```

### 4.3 批次 24 记录 §5 相关原文（`docs/research/2026-10-26-batch-24-record.md`）

**§5 口径说明（`:197-198`）逐字**：

```markdown
> **口径说明**：任务材料包含两组复核：①"修复环第 1 轮"称 **11 条**全部处置，其中 #3/#4 是代码修复（`VisualJsClientEngine.kt`）；但随附 11 行表格的 #3/#4 分别是 KD-74/KD-69 与锁计数，均非代码修复，**口径无法与表格对齐**。②"复核发现与处置"列出 **25 条**，本节按 25 条逐条核对当前树；11 条中的文档项已被 25 条覆盖（KD-70/66/74/69、锁计数、行号漂移、准入清单、R2 数字、KD-76 措辞、Gradle 偏离、T5 归因、迟到 death 措辞）。
> `已修` = 当前树文本/代码已按发现改；`已登记` = 已写入 KD/研究/准入清单但事项未闭合；`残留` = 当前树仍未改且未关闭；`已披露` = 属于过程事实，记录如实说明、不做代码修复。
```

**§5 表 #12–#15（`:213-216`）逐字**（#12 = `:788-797` 竞态；#13 = 19.5 s 加载态；#14 = `isBinderAlive` 措辞；#15 = `close()` unlinkDeath）：

```markdown
| 12 | `VisualJsClientEngine.kt:557/:794-799/:837` 起 | 仍有 death-before-registration 竞态：`onServiceConnected` 检查后无条件置 `CONNECTED`，可能覆盖已写入的 DEAD | 中 | **已修（代码）**：`Link.markConnected` 与 `markDead` 共用 `stateLock`；`connectLocked` 用 `isConnected()` 在锁内复查 `dead`；已死 link 不会被当连接返回。**无确定性交错测试**（KD-66 `:2609` 自认）。 |
| 13 | `VisualJsClientEngine.kt:486-503/:167/:920-936`；契约 `:72` | 加载态没有无限等待，但 `ensureConnected` 在宿主 `withTimeout` 之外：首连 15 s、重建 10 s、宿主阶段 2.5 s、死亡确认 2 s，最坏约 19.5 s；首连超时永久关闭，无加载/重试态；`executeMutex` 排队无上界 | 中 | **部分修**：`close()` 已用 `markDead` 完成等待（§5 #15）；LOADING/重试与 `executeMutex` 上界仍未做，契约 §4:72 已如实写 19.5 s 与无上界排队——**已登记，未消除**。 |
| 14 | `VisualJsClientEngine.kt:609-621`；R1 `:232-248` | KDoc 称 death 事实与 `isBinderAlive` 是"两条独立事实互为兜底"；研究指出 `isBinderAlive` 只读 `mAlive`，若通知丢失且无失败事务，两者可同时为 true；该分支无测试 | 低 | **残留（措辞）+ 已登记**：KDoc 已含"未注册 death 且无失败事务则不清零"的 caveat，但"互为兜底"仍强；6/6 设备绿只证明 death 路径（logcat `death was not confirmed`=0）。 |
| 15 | `VisualJsClientEngine.kt:224-244` | `close()` 仍先 `unlinkDeath` 再 `death.await()`（诊断用），与 abandon 修复方向相反 | 低 | **已修**：`close()` 现在对当前 link 调 `markDead("the client was closed", unexpected=false)`，完成 `connected`/`death`、终结 activeCall、解注册/解绑，首连/重建立即返回 null。 |
```

### 4.4 批次 24 记录 §9 主会话 KD 裁定原文（`:402-406`）

```markdown
- **主会话裁定（KD 状态）**：
  - **KD-70 关闭**：E-02 设备 20/20、P2 转绿（reason=MEMORY_LIMIT + diagnostics 含 attributed by memoryUsage + PENDING_CALIBRATION），满足 reopen 条件；精确 mallocSize 未打印（断言已证 ≥15,099,495 = 90%×16MiB），超参仍 PENDING_CALIBRATION。剩余 ABI 覆盖（arm64/16KB）另立，不阻断本条关闭。
  - **KD-69 关闭**：E-03 strictOffline release 冷启动 ×2 全绿、9 类崩溃签名 0，满足"release 冒烟不得回退"；生产签名另属发布链验证（一次性本地 key 已如实记录）。
  - **KD-66 保持 open**：hard+500ms 设备用例仍未覆盖；隔离 6/6 与 watchdog 硬杀不等于该关闭门。
  - **KD-74 已由 T3 关闭**（三条件齐备，记录回链 §8）。
```

### 4.5 契约原文（`docs/current/visual-generation-contract.md`）

**`:58`**：

```markdown
阶段能力按4E.1统一管线/静态后端、4E.2交互、4E.3复杂源与保真扩展分别声明；所有生图入口从4E.1接同一判定，不能按阶段把某用途硬绑后端。静态程序可省略 reduce；每次调用的 input 与 metrics 深冻结，reduce 仅能返回符合 StateV1 的局部状态。未启用的控件/事件/模式在模型能力上下文中不广告，解析与执行也同时拒绝，不仅隐藏 UI。
```

**`:62`**：

```markdown
新增非导出 Service，`android:isolatedProcess="true"`、专用 `:visual_js` 进程。Application 必须在打开 Room、恢复备份、维护文件、创建 Provider/密钥工厂前，用公开 API `Process.myUid() != applicationInfo.uid` 短路；同时审计构造/字段与 companion 初始化、实际进入该进程的 ContentProvider/AndroidX Startup，不能只在 onCreate 最前面加 guard 就宣称已隔离全部启动路径。隔离进程不申请文件/网络主机绑定。
```

**`:64`**：

```markdown
宿主通过 AIDL 发控制消息，源码和 JSON 经有界 ParcelFileDescriptor 管道传输；读写循环均计字节、可取消，不逐图元发 Binder。每次任务有 requestId、generation、phase；全局一个在飞绘制、最多两个待绘 program（各保留一个最新状态），只合并相同 program 的旧状态，跨 program 按序处理。满队列回 BUSY 并保留原帧，由可见/交互生命周期限次重新调度，不把另一张图误当旧帧丢掉；初次工具生成按已有工具序列执行。旧帧与不再匹配场景的事件拒绝。消费者须验证服务调用身份、描述符方向与长度，不暴露宿主通用 RPC。
```

**`:72`**：

```markdown
**终止层落地状态（2026-10-26 批次 22 T1 落层；批次 23/24 设备复跑回填；按动作分 L1/L2/L3，旧文献的"第三层"一律指 L2，不再用层号混称）。** L1 协作中断（100 ms）已在 core:data 引擎落地（`evaluationTimeoutMillis` + 协程取消 → `PHASE_TIMEOUT`），但对 issue #240 的自续 job 不构成硬终止。L2 服务硬终止（1500 ms）**代码已落层，设备用例已验证 watchdog/重建路径（仍未生产装配）**：app 的 `visualjs.VisualJsService` 以 `android:isolatedProcess="true"` + `android:process=":visual_js"` 注册，AIDL `IVisualJsService`/`IVisualJsCallback`、watchdog 令牌 CAS、`killProcess(myPid())`、过期 `terminate(generation)` no-op 均已实现（`app/src/main/AndroidManifest.xml`、`app/src/main/aidl/com/tingyun/smartmistakebook/visualjs/`）；2026-10-26 复核曾用 `aidl.exe` 单文件编译两份 AIDL（均 exit=0；此前 `IVisualJsCallback.aidl` 被误覆盖成 `IVisualJsService` 的生成 Java stub，已恢复，见 KD-66 Progress）；**批次 23 起 AIDL 门一律以 Gradle 任务验证**，`aidl.exe` 单文件结果只作历史证据、不再作为本批通过判据；watchdog 在引擎 `execute` 前 arm 一次、请求收尾 disarm（契约原文"每个 JS 阶段开始前"在实现里取整个请求的 JS 工作起点，R1 `../research/2026-10-26-visualjs-isolated-process.md` §5.1）；`SmartMistakeBookApplication.onCreate` 在打开 Room/备份/Provider 前用 `Process.myUid() != applicationInfo.uid`（API 28+ 叠加 `Process.isIsolated()`）短路。**设备轨已执行（2026-10-26 批次 23；E-01b/E-01e）**：`VisualJsIsolationInstrumentedTest` **5 例全部执行，4 绿 1 红**（批次 23 时点）。绿：`workerIsIsolatedAndAProgramRoundTripsWithoutBeingKilledLater`（隔离 UID + AIDL 往返成功）、`aStaleGenerationIsRejectedAndAStaleTerminateIsANoOp`、`theWatchdogKillsAHungWorkerAndAFreshGenerationHasNoBusyState`（1500 ms 硬期限 SIGKILL + 新 generation 无 BUSY）、`theClientRebuildsOnceAfterADeathAndTheNewWorkerIsNotBusy`（死亡后重建一次，新 worker 无 BUSY）。红：`theClientRebuildsAtMostOnceAfterConfirmedAbandons`（`the first abandon did not confirm death`）——`VisualJsClientEngine.abandon()` 先 `unlinkDeath` 再等 `link.death`（`VisualJsClientEngine.kt:570-571`），服务已 SIGKILL 而客户端 1 s 内收不到 death；E-01c 单方法稳定复现。加载态对照：冷启动后立即跑 5/5 红（attach 4.6–7.5 s > 5 s，E-01d），沉降约 2 min 后回到 1/5 红（E-01e）。**批次 24 E-01/E-01b 已在两代 debug APK 上各跑一遍，均 6/6（`OK (6 tests)`；`Time: 23.945` / `22.208`）**：abandon 用例转绿（重建后真实 `execute` 返回 `Rendered`，第二次确认后不再 bind）、7.6 s 冷启动 attach 用例通过、`death was not confirmed` 告警 0；证据 `../research/evidence/2026-10-26-batch-24/step01-visualjs-isolation.md:124`、`step01b-visualjs-isolation-rerun.md:102`、`00-index.md:43-44`。**因此"复跑 UNVERIFIED / 第 5 例未执行 / watchdog 与重建无绿证据"的旧表述作废**；隔离进程内 `libquickjs.so` 装载与 `killProcess` 权限已由 watchdog/往返用例绿证明。ContentProvider/AndroidX Startup 的隔离进程装配面仍未 `dumpsys` 审计（该缺口已登记为 4E.2 准入门 P-08，见 [`../research/2026-10-26-batch-24-4e2-admission-checklist.md`](../research/2026-10-26-batch-24-4e2-admission-checklist.md)）。L3 宿主阶段期限（2500 ms）**仅代码落层且无生产装配**：app 的 `VisualJsClientEngine` 在期限到点后 `abandon()`（ABANDONED → `terminate(generation)` oneway → `unbindService` → **保留 death 注册**并以 binder death 事实 + 轮询 `isBinderAlive` 组合确认 ≤2 s），确认死亡后清空 link、由下一次执行扣唯一一次重建预算（复核 #12 后预算在 `connectLocked` 统一扣；未确认才 fail-closed，不并发放大进程数），再死即熔断；**调用方上界不是 2500 ms**——`ensureConnected()` 的连接预算在期限之外：首连 15000 ms（批次 24 由 5000 ms 延长；设备实测冷启动 attach 4.6–7.5 s，旧 5 s 会把加载态误判成结构性失败，E-01d），重建 10000 ms；期限到点后的 `NonCancellable` 放弃还要等死亡确认 ≤2000 ms，最坏约 19.5 s（首连 15 s + 宿主阶段 2.5 s + 死亡确认 2 s，另加 Binder 开销）；冷启动首个 execute 的 1500 ms watchdog 误杀（E-01d）仍需 warm-up/ready，本批未修（缺口已登记 KD-76：**隔离门 6/6 绿不得作为 JS 广告依据**，须补 warm-up/ready 设备用例）；并发调用在 `executeMutex` 上无超时排队，排队方的等待不设上界（与引擎端口的 BUSY 背压语义不同）。JS 仍 UNAVAILABLE、不广告：L3 客户端没有生产装配点，服务内内容度量仍是 `UnwiredVisualContentMeasurer`（只有纯几何程序可通），4E.2 交互/表单未做。**KD-66 保持 open**：L2 代码落地与 6/6 设备绿不等于关闭，关闭门值不变（“issue #240 形状在 hard+500 ms 内返回拒绝且后续请求可用”的设备证据，见 [`../known-defects.md`](../known-defects.md) KD-66）；该证据已按 R4 [设备验证证据落盘规范](../research/2026-10-26-device-evidence-convention.md)落盘（批次 23 E-01b/E-01e；批次 24 E-01/E-01b），但现有 watchdog 用例以 ≤6 s 判死、弱于 hard+500 ms；`abandon()` 的死亡确认缺陷已由批次 24 修复代码并补用例（**批次 24 E-01/E-01b 设备复跑 6/6，旧 UNVERIFIED 作废**），因此本节仍不得读成“进程级硬终止已验证”。
```

**`:86`**：

```markdown
新增程序建议≤12 KiB，64 KiB 是拒绝阈值而非每轮目标；单逻辑操作最多提交两份最终图，累计接收源码（含失败尝试）≤128 KiB、新源码布局尝试≤6次、累计 JS 阶段墙钟≤10秒。任一预算耗尽即文字降级并使用原派发余额终答，不另建任务；正常本地交互不重复消耗远程逻辑操作次数，但仍受单事务/熔断限制。单份模型工具 payload 的解码后 JSON≤512 KiB，sourceJs 再按实际 UTF-8≤64 KiB 检查，不截断源码、转义或代理对；工具回喂摘要仍遵守原有字符预算，源码只进独立有界 context。
```

**`:155`**：

```markdown
统一管线每个逻辑操作最多6次Provider模型执行派发，文本决策/带图修复与图像生成/编辑共用持久ledger；JS本地执行不占远程派发。讲题预留1次终答，故余额1时撤两路工具；独立转写/净化终态本地保存，不硬留聊天位。旧“5工具轮+1终答”仅是没有额外图像执行时的上界。当前图像通道尚未接ledger，必须补接，不能只改提示词就声称已统一预算。失败/截断/重建前派发均计数，换方法不换operationId，不隐藏重试/审查请求。
```

**`:186`**：

```markdown
请求 codec 当前 encodeDefaults/explicitNulls 均开启；新增可空上下文必须 EncodeDefault.NEVER 或精确旧 schema 投影，并扩真实旧请求/逻辑操作指纹测试，不能认为“默认 null”天然兼容。
```

**`:202`**：

```markdown
所有生图入口先接4E.1统一决策/协调器，可先以已有图像方法验通，再在JS静态后端过门后并列广告两方法；不能把某用途永久留在旧专线。4E.2扩交互/表单，4E.3扩复杂来源/保真质量。每个方法/特性仍须真实可用才广告，未过门返回不可用，不伪造能力。完整门包括：源码固定与许可证；API 23/arm64/API 35+16 KB/R8；隔离启动、无网络/文件能力、引擎崩溃和 watchdog 恢复；字节/几何/字体/像素预算；两种工具协议和六次总预算；旧请求指纹、全历史迁移、备份/GC；六类截图样例与一个新算法组合样例；360/600/840 dp、fontScale 1/1.3/2、TalkBack；同快照屏幕/PDF/SVG 一致；真实 Provider 生成与修复率另测。
```

---

## 5. VisualJsClientEngine.kt 现状（938 行，git 干净）

| 项 | 当前位置 | 事实（本轮实读） |
|---|---|---|
| `executeMutex` 队列 | 声明 `:84`；加锁 `:167` | `Mutex.withLock` 串行整个会话（含 `ensureConnected`、`execute`、等结果）；代码中**无 `tryLock`/`withTimeout`/队列容量**，排队方等待无上界。**不是**契约 §4:64 的"1 在飞 + ≤2 待绘/latest-wins/BUSY 保帧"队列 |
| 计划所指 `:788-797` 交错 | `:786-799` | 现内容是 `uid == Process.myUid()` fail-closed（`:786-793`，其中 `:788-792` 为 `markDead` 调用）+ `if (!markConnected(candidate, uid))`（`:794-799`）。**death-before-registration 竞态已在 `aabae781` 修复**：`markConnected` 与 `markDead` 共用 `stateLock`（`markConnected` `:837-849`；`markDead` 先 CAS `dead` 再取锁 `:861-872`），`connectLocked` 用 `isConnected()` 锁内复查（`:557`）。**当前无确定性交错用例**：visual 测试目录 `grep -rn "interleav\|race\|markConnected"` 零命中 |
| `close()` | **`:224-241`**（计划写 `:224-239`，实际多 2 行） | `:228 markAbandoned` → `:229 terminate` → `:230 service=null` → `:231 unbindService` → `:238 markDead("the client was closed", unexpected=false)`（完成 `connected`/`death`、终结 activeCall、解注册/解绑）→ `:240 link=null`。**没有"先 unlinkDeath 再 await death"**；解注册发生在 `markDead→handleDeath` 内（`:662`） |
| 未确认路径 | `abandon()` `:580-607`；fail-closed `:590-601`；`awaitDeathConfirmation` `:622-647` | `:586 terminate` → `:588 unbind` → `:589` 组合确认（`link.death` 事实 + `isBinderAlive` 每 50 ms 轮询 + 2 s 上界）→ `:592 DEATH_UNCONFIRMED`、`:593 unavailable=true`、`:596-601 Log.w` → `:605 unlinkDeath` → `:606` 清 link。KDoc `:617` 仍写"两条独立事实互为兜底"；`:594-595` 仍写"迟到的 death 会被丢弃…永不据此重建"（行为保证成立、措辞过强，批次 24 §5 #11/#14 已登记） |
| 首连/重建/确认预算 | `:920` = **15_000 ms**；`:926` = **10_000 ms**；`:933` = **2_000 ms**；`:936` = 50 ms 轮询 | 连接预算在 `connectLocked` `:545-551` 选择；`ensureConnected()` 在 `:180` 调用，**位于宿主 `withTimeout(2500)`（`:191`）之外** → 文档口径最坏 15 + 2.5 + 2 = **19.5 s**（契约 `:72`，另加 Binder 开销） |
| 首连超时后的状态 | `:557-564` | 超时/未连上 → `markAbandoned` → `service=null` → `unbindService` → `unlinkDeath` → `link=null` → **`unavailable = true`（`:563`）**；`grep "unavailable = false"` **零命中**（exit=1）→ 一旦置真永不恢复；`ensureConnected()` `:487` 直接返回 null，`execute` 折 `ENGINE_CLOSED`（`:180-184`） |
| `unavailable` 全部写入点 | `:290`、`:326`、`:336`、`:346`、`:496`、`:520`、`:538`、`:563`、`:593`、`:679`、`:687`、`:698`、`:703` | 13 处，全部为置真；**无复位点** |
| warm-up/ready | **不存在** | `grep -rniE "warm.?up\|isready\|\bready\b\|loading"` 于 `app/src/main/kotlin/.../visualjs/` 与 `app/src/main/aidl/.../visualjs/` **零命中**（exit=1）；AIDL 方法只有 `protocolVersion/workerUid/currentGeneration/initialize/execute/terminate`（`IVisualJsService.aidl:20-36`） |

---

## 6. VisualJsService.kt 现状（574 行，git 干净）

| 项 | 行号 | 事实 |
|---|---|---|
| watchdog 字段/布局 | `:69-80` | `ScheduledThreadPoolExecutor(1)`，daemon 线程 `visual-js-watchdog`；`removeOnCancelPolicy=true`、`executeExistingDelayedTasksAfterShutdownPolicy=false`；与 JS 线程分离 |
| `armWatchdog` | `:412-428` | `watchdog.schedule({...killSelf(...)}, SERVICE_HARD_DEADLINE_MILLIS, MILLISECONDS)`；常量 `SERVICE_HARD_DEADLINE_MILLIS = 1500L`（`core/domain/.../VisualExecutionEngine.kt:126`） |
| watchdog 启动点 | `:305` | `armWatchdog(execution)` 在请求解码后、**创建引擎（`:309-310`）之前**执行；首个 `execute` 与 warm 请求同等待遇，无一次性装载豁免 |
| ready 状态 | **不存在** | 无 ready/warm-up 标志；`initialize(generation)`（`:150-165`）只做 generation 记账，不装载 QuickJS；AIDL 无 ready 方法 |
| `inFlight` / BUSY | 声明 `:85`；CAS `:186`；BUSY 返回 `:186-190`；复位 `:201`/`:210`/`:236`/`:381` | 全局单在飞；忙时 `STATUS_BUSY` 并关请求 fd；契约 §4:64 的"满队列 BUSY 保留原帧"在服务端只有单槽语义 |
| 单线程 JS 执行器 | `:65-67`，投递 `:207` | `Executors.newSingleThreadExecutor`；每请求一个任务、每请求新建 `QuickJsVisualExecutionEngine`（`:309-310`） |
| 宿主死亡观察 | `:538-556` | `linkToDeath` → `killSelf("the host process died")` |
| `onUnbind` 自杀 | `:94-97` | 宿主 unbind 即 `killSelf`，客户端无法复用同一 worker |

---

## 7. 准入清单计数（`docs/research/2026-10-26-batch-24-4e2-admission-checklist.md`，336 行）

**实测 63 行条目**（`grep -cE '^\| [A-Z]-[0-9]+'`）：

| 组 | 条目数 | ID 范围 |
|---|---|---|
| P | **11** | P-01 … P-11 |
| S | **8** | S-01 … S-08 |
| M | **4** | M-01 … M-04 |
| D | **5** | D-01 … D-05 |
| E | **5** | E-01 … E-05 |
| A | **10** | A-01 … A-10 |
| Q | **6** | Q-01 … Q-06 |
| V | **4** | V-01 … V-04 |
| R | **5** | R-01 … R-05 |
| X | **5** | X-01 … X-05 |
| **合计** | **63** | — |

**P-01 … P-07 条目要点（现状列原文摘要，非本轮执行）**：

- **P-01**（`:121`）：隔离 suite 6/6 设备绿（下限；完整门还需 P-07/P-10）；批次 24 E-01/E-01b 两代 APK 均 6/6；仍不覆盖 hard+500 ms、warm-up/ready、死亡未确认。
- **P-02**（`:122`）：KD-70 P2 转绿（批次 24 E-02 20/20，`MEMORY_LIMIT` + `attributed by memoryUsage` + `PENDING_CALIBRATION`）；**口径窄于 R2 §5**：只测 x86_64/API 34/4 KB，arm64/16 KB 未测，精确 `mallocSize` 未打印。
- **P-03**（`:123`）：引擎其余 19 例不回退（批次 24 E-02 20/20）；`VisualJsFailureClassificationTest` JVM 本轮未重跑。
- **P-04**（`:124`）：首连 15 s / 重建 10 s / 死亡确认 2 s 常量已改；`close()` 已改为完成 `connected`/`death`；**客户端无 LOADING/退避重试**，最坏等待仍约 19.5 s。
- **P-05**（`:125`）：KD-66 `hard+500 ms` 门未达；现有 watchdog 以 ≤6 s 判死。
- **P-06**（`:126`）：文档常量与实现一致（已回填）；KD-66 旧 5 s/1 s/8.5 s 已标为批次 22 时点。
- **P-07**（`:127`）：warm-up/ready 门（KD-76）**未落**：服务在请求开始即 arm 1500 ms watchdog，无 ready 语义；E-01d 已在冷启动记录同一失败形状。

**批次 24 复核后追加的行仍在**（本批需映射）：**P-08** 隔离启动面审计（部分落）、**P-09** 队列/背压与消费者校验（未落）、**P-10** 死亡未确认路径（部分落，无 fallback UI/负向设备用例）、**P-11** `[UNATTRIBUTED]` 诊断跨进程保留并消费（未落）。因此 P 组是 11 项，不是 7 项。

---

## 8. 锁文件现状与构建文件脏态

### 8.1 锁文件

- `git ls-files '*gradle.lockfile*'` = **0**（没有任何锁文件入库）。
- `git status --porcelain | grep -i lockfile` = **13 条未跟踪**：`app/gradle.lockfile`、`core/{data,database,domain,export,model,ui}/gradle.lockfile`、`feature/{capture,library,profile,review,tutor}/gradle.lockfile`、根 `settings-gradle.lockfile`。
- **盘上实为 14 个锁文件**：13 个模块 `gradle.lockfile` + 1 个 `settings-gradle.lockfile`；其中 `core/markdown/gradle.lockfile`（6,470 B）因整个 `core/markdown/` 目录未跟踪而被折叠成一条 `?? core/markdown/`，不进入 lockfile 计数。`benchmark/gradle.lockfile` **不存在**。
- 根 `build.gradle.kts:37-38` 已声明 `dependencyLocking { lockAllConfigurations() }`（文件本身干净）；`:26`/`:33` 注释说明当前默认 `LockMode.DEFAULT`、STRICT 待锁文件齐全后再评估。
- 尺寸：app 27,861 B；core/data 8,735；core/database 6,748；core/domain 2,910；core/export 3,111；core/markdown 6,470；core/model 2,383；core/ui 6,960；feature/capture 7,510；feature/library 7,900；feature/profile 6,173；feature/review 6,897；feature/tutor 7,648；settings 267。

### 8.2 构建文件

| 文件 | 状态 |
|---|---|
| 根 `build.gradle.kts` | 干净 |
| `settings.gradle.kts` | **M** |
| `app/build.gradle.kts` | 干净 |
| `core/model/build.gradle.kts` | 干净 |
| `core/domain/build.gradle.kts` | 干净 |
| `core/database/build.gradle.kts` | 干净 |
| `core/data/build.gradle.kts` | 干净 |
| `core/export/build.gradle.kts` | **M** |
| `core/ui/build.gradle.kts` | **M** |
| `core/markdown/build.gradle.kts` | **??（整个目录未跟踪）** |
| `feature/capture/build.gradle.kts` | 干净 |
| `feature/review/build.gradle.kts` | 干净 |
| `feature/tutor/build.gradle.kts` | **M** |
| `feature/library/build.gradle.kts` | 干净 |
| `feature/profile/build.gradle.kts` | 干净 |
| `benchmark/build.gradle.kts` | 干净（但无锁） |

**结论**：锁文件在盘上已生成、但 0 个入库；构建面脏的是 `core/export`、`core/ui`、`feature/tutor`、`settings.gradle.kts`（+ 未跟踪的 `core/markdown`）。这与计划 §0 "锁文件仍不生成（他线构建改动未入库）" 的措辞存在口径差（见 §10）。

---

## 9. 设备状态（只读查询）

`adb` / `emulator` 不在 PATH，用绝对路径（`/c/Android/Sdk/...`）。

`/c/Android/Sdk/platform-tools/adb.exe devices`（exit=0）：

```text
List of devices attached
emulator-5554	device
emulator-5556	device
emulator-5562	device
emulator-5584	device
```

`/c/Android/Sdk/emulator/emulator.exe -list-avds`（exit=0）：`audit_user_visible_20261016`、`kb_test_device`、**`ps16k_x86_64`**、`test_device`。

| 序列 | AVD 名（`emu avd name`） | PAGE_SIZE | SDK | ABI | 判定 |
|---|---|---|---|---|---|
| emulator-5554 | `test_device` | 4096 | 34 | x86_64 | 4KB 设备 |
| **emulator-5556** | **`ps16k_x86_64`** | **16384** | **36** | **x86_64** | **16KB AVD 在线** |
| emulator-5562 | `audit_claude_coverage_20261024`（不在 `-list-avds`，陈旧名） | 4096 | 34 | x86_64 | 4KB 设备 |
| emulator-5584 | `kb_test_device` | 4096 | 34 | x86_64 | 4KB 设备 |

**ps16k 在线状态：是（emulator-5556）**：`sys.boot_completed=1`；`getconf PAGE_SIZE=16384`；`ro.boot.hardware.cpu.pagesize=16384`；`ro.build.version.release=16`（SDK 36）；`ro.product.cpu.page.size` / `ro.product.page_size` 仍为空。AVD 配置 `~/.android/avd/ps16k_x86_64.avd/config.ini`：`image.sysdir.1 = system-images\android-36\google_apis_ps16k\x86_64\`、`tag.id = page_size_16kb`、`abi.type = x86_64`。

> ps16k 仍是 **x86_64**，不能替代 arm64 16KB 运行时证据；KD-74 已关闭，但 arm64 覆盖缺口继续由 KD-70/P-02 与 R-03 跟踪。

本轮未启动/停止任何模拟器、未安装/运行任何 APK、未改任何设备设置。

---

## 10. 摘要与与计划 §0 的不符处

### 10.1 与计划 §0 逐条对照

| 计划 §0 断言（行） | 本轮实测 | 判定 |
|---|---|---|
| `:5` HEAD = `d783ae84` = origin/main | `git rev-parse` 两侧同 hash；ahead/behind `0 0` | ✓（**本地引用**；未 fetch，远端 UNVERIFIED） |
| `:5` KD-74 已关、KD-66 open | KD-74 标题 `closed`（`:2768`）；KD-66 标题 `open`（`:2573`） | ✓ |
| `:5` KD-69/70 已关 | **台账仍写 open**：KD-69 `:2664`、KD-70 `:2682`；批次 24 记录 §9 `:403-404` 有"关闭"裁定 | ✗ **台账未回写**（见 10.2-1） |
| `:6` 4E.2 准入检查表 58 项 | **实测 63 行**（P11/S8/M4/D5/E5/A10/Q6/V4/R5/X5） | ✗ **数字不符**（批次 24 记录 §5 #6 已称 63） |
| `:6` 清单漏 §4 启动面审计 / 队列背压 / 6 派发 ledger | 清单**已含** P-08 / P-09 / A-10（均标"未落/部分落"） | △ **"漏"只对更早版本成立**；当前是"已登记未实现" |
| `:6` 漏 input/metrics 深冻结（§58）、旧请求指纹（§186） | 清单**无对应门**（批次 24 §5 #24 明确"仍未登记为门"） | ✓ 仍成立 |
| `:6` 漏 API35+ 16KB 口径（§202） | R-03（arm64 16 KB 未测）、R-05（AAR/ELF）覆盖 16KB；**无独立"API 35+"行** | △ 部分覆盖（见 10.2-3） |
| `:7` `:788-797` death-before-registration 竞态 | **已修**：`markConnected`/`markDead` 共用 `stateLock`（`:794-799`、`:837-872`）；批次 24 §5 #12 标"已修" | ✗ **计划把已修项列为残留**；真正的残留是"无确定性交错用例" |
| `:7` 加载态最坏 19.5 s、首连超时永久关闭、无 LOADING/有界重试 | 15+2.5+2 = 19.5 s（契约 `:72`）；`unavailable=false` 零命中；warm/ready/loading 零命中 | ✓ 属实 |
| `:7` `close()` 先 unlinkDeath | **已修**：`close()` `:224-241` 走 `markDead`（`:238`），不再先解注册；批次 24 §5 #15 标"已修" | ✗ **计划把已修项列为残留** |
| `:7` KDoc "两条独立事实互为兜底"措辞不实 | `:617` 原文仍在（批次 24 §5 #14 标"残留（措辞）"） | ✓ 属实 |
| `:7` P2 仅 x86_64/4KB 单 ABI | 台账/清单文本一致（P-02、KD-70）；**本轮未跑设备** | ✓ 文本层；设备层 UNVERIFIED |
| `:8` 热区 6 项不变 | 6 项全部在飞（§2） | ✓ |
| `:8` 锁文件仍不生成 | 盘上 **14 个锁文件**、**0 入库**、13 条 git 可见 | △ 若指"未入库"成立；若指"未生成"不实 |
| `:9` 队列顺序 | 本轮不涉及执行 | 未核（无需核） |

### 10.2 开工前须知（必须带进批次 25）

1. **KD-69/KD-70 状态漂移**：`docs/known-defects.md` 仍写 `open`，而批次 24 记录 §9（`:402-406`）已裁定关闭。计划 §0 采用裁定口径。**T5 台账同步时必须先决定以哪边为准并回写条目**，否则本批结束时台账仍自相矛盾。
2. **T1 的实际剩余范围小于计划字面**：death-before-registration 竞态与 `close()` 顺序都已在 `aabae781` 修好（批次 24 §5 #12/#15）。T1 真正缺的是 **确定性交错用例**（`markConnected` vs `markDead`）与死亡未确认负向用例（P-10）。若按计划字面再"修"竞态，会重复改已修代码。
3. **准入清单的"漏项"表述需更新**：P-08/P-09/A-10 已在清单内（未实现），深冻结/旧请求指纹仍未登记为门。计划 §1 的 T4"补齐"应针对后者与"API 35+ 16KB"明列，而不是重复添加 P-08/P-09/A-10。
4. **core/domain、core/data、core/database 开工即脏**：core/domain 4 M + lock；core/data 10 M + lock + 核对期间他线新增的未跟踪 `core/data/src/androidTest/.../core/data/knowledge/TeachingMaterialAllocationInstrumentedTest.kt`（visual 落点本身干净）；core/database 8 M + lock（计划热区未列）。排提交必须按路径白名单，禁止模块级 `git add`。
5. **热区 6 项全部在飞**：feature 四模块、core/ui、Root/SecondaryScreens、settings.gradle.kts、core/export；另 `core/markdown/` 整个目录未跟踪。任何触碰热区的实现都必须等主会话授权。
6. **锁文件：0 入库、14 个在盘、13 条可见、benchmark 缺锁**。计划 §0 的"仍不生成"应读成"未入库"；本批 R3 的前置检查若要求"干净树重生成"，当前树（202 M + 471 ??，含 core/export/core/ui/feature/tutor/settings 构建改动）不满足。
7. **ps16k AVD 在线（emulator-5556，16384/API 36/x86_64）**：16KB 设备轨可直接跑，但 **arm64 16KB 仍无**；不能据此关闭 arm64 覆盖缺口。
8. **DB/请求 schema 未变**：DB v69、请求 schema 18、VisualJS wire 1；三处权威文件均干净，本批 T1/T2/T3 不需要 schema 升级。
9. **批次 24 记录有两个 `## 9`**（`:389` 与 `:396`）：前者是记录员验证结果，后者才是主会话收口/裁定；引用 KD 状态时必须用 `:396` 起的第二块。
10. **`origin/main` 未 fetch**：本地引用与 HEAD 相同，远端实时 hash **UNVERIFIED**。

**UNVERIFIED / 本轮未做**：未跑任何 Gradle 任务（单测门/强门/assemble 均未跑）；未跑 JVM/androidTest/设备用例；未 `git fetch`；未编译 AIDL；未复跑隔离 6 例、KD-70 P2、R8 release 冒烟、16KB 抽验、备份/迁移抽验；未验证批次 24 设备 E-file 的实际内容（只读了记录/清单文本）；未验证 KD-74 裁定的证据内容。**本轮实际执行**：`git` 只读查询/计数、`grep`/`sed`/文件读取、`adb devices` 与 4 台设备的 `getconf`/`getprop`/`emu avd name` 只读查询。

---

## 附录 A：本轮实际执行的只读命令（可复跑）

```bash
git rev-parse HEAD; git rev-parse origin/main
git rev-list --left-right --count origin/main...HEAD                 # 0 0
git log --oneline -4; git show --stat --oneline d783ae84            # +14 lines, 1 file
git status --porcelain | wc -l                                      # 673
git status --porcelain | grep -c '^ M'                              # 202
git status --porcelain | grep -c '^??'                              # 471
git diff --cached --name-only | wc -l                               # 0
git status --porcelain -- <每个落点>                                 # 全空
grep -n STUDY_DATABASE_VERSION core/database/.../StudyDatabase.kt   # :134 = 69
ls core/database/schemas/.../StudyDatabase/ | wc -l                 # 69; max 69.json
git ls-files '*70.json' '*71.json'                                  # 空
grep -n 'TUTOR_VISUAL_CONTEXT_SCHEMA_VERSION\|CURRENT_SCHEMA_VERSION =' core/model/.../ModelTasks.kt  # :546-547 = 18
grep -n PROTOCOL_VERSION app/src/main/kotlin/.../visualjs/VisualJsProtocol.kt                       # :31 = 1
grep -n 'HOT_ZONES' .zcode/plans/plan-2026-10-27-batch-25-race-warmup-queue.md                     # 零命中
grep -n 'unavailable = false' app/src/main/kotlin/.../visualjs/VisualJsClientEngine.kt               # 零命中 (exit 1)
grep -rniE 'warm.?up|isready|\bready\b|loading' app/src/main/kotlin/.../visualjs/ app/src/main/aidl/.../visualjs/  # 零命中
grep -c '@Test' app/src/androidTest/.../visualjs/VisualJsIsolationInstrumentedTest.kt               # 6
grep -cE '^\| [A-Z]-[0-9]+' docs/research/2026-10-26-batch-24-4e2-admission-checklist.md           # 63
git ls-files '*gradle.lockfile*' | wc -l                            # 0
git status --porcelain | grep -i lockfile                           # 13 条
for d in app core/{model,domain,database,data,export,ui,markdown} feature/{capture,review,tutor,library,profile} benchmark; do test -f "$d/gradle.lockfile" && echo LOCK $d; done  # 13; benchmark 无
git status --porcelain -- '*.gradle.kts' gradle.properties gradle/libs.versions.toml
"/c/Android/Sdk/platform-tools/adb.exe" devices
"/c/Android/Sdk/emulator/emulator.exe" -list-avds
for s in emulator-5554 emulator-5556 emulator-5562 emulator-5584; do adb -s $s emu avd name; adb -s $s shell getconf PAGE_SIZE; adb -s $s shell getprop ro.build.version.sdk; adb -s $s shell getprop ro.product.cpu.abi; done
"/c/Android/Sdk/platform-tools/adb.exe" -s emulator-5556 shell getprop sys.boot_completed          # 1
"/c/Android/Sdk/platform-tools/adb.exe" -s emulator-5556 shell getprop ro.boot.hardware.cpu.pagesize # 16384
grep -E 'image.sysdir|tag.id|abi.type' ~/.android/avd/ps16k_x86_64.avd/config.ini
```
