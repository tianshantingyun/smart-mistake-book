# 批次 26 基线核对（2026-10-27，基线核对员）

> **只读核对**：本轮未运行 Gradle、未编译、未执行任何 JVM/仪器化/设备用例、未 `git fetch`、未做任何 git 写操作（本文件是唯一写盘产物）。设备面只执行了只读 `adb`/`emulator -list-avds` 查询。对照计划：`.zcode/plans/plan-2026-10-27-batch-26-kd66-gate-lockfiles.md` §0（`:3-10`，下称"计划"）。
>
> **快照时点**：2026-10-27 05:42:23 +0800，`git status --porcelain` 冻结 **673 行（201 M + 472 ??）**；**暂存区 0 条**（`git diff --cached --name-only | wc -l` = 0）。冻结时本批落点（§3）porcelain 全为 0。核对结束落盘本文后复查为 **674 行（201 M + 473 ??）**：新增 1 个 ?? 是本文档自身；M 计数不变。
>
> "干净"定义：`git status --porcelain -- <path>` 无输出（不代表内容无缺陷）。

---

## 1. HEAD / 工作树 / DB schema / 请求 schema

| 项 | 实测 | 证据（命令/路径行号） |
|---|---|---|
| HEAD | `405c83739cfa5385aa7652ef61f3488f953c66ce`（`main`） | `git rev-parse HEAD` |
| origin/main | `405c8373…`（**与 HEAD 相同**） | `git rev-parse origin/main`；`git rev-list --left-right --count origin/main…HEAD` = `0 0`；**本轮未 `git fetch`**，这是本地远端跟踪引用，远端实时 hash 未核（UNVERIFIED） |
| HEAD 构成 | `405c8373` 是**纯文档提交**：`docs/research/2026-10-27-batch-25-record.md` +12 行（主会话收口 §9）；父提交 `961c6a0e`（批次 25 主提交，31 文件 +4,979/−128：服务 warm-up/ready、`VisualJsFrameQueue.kt` 新增、客户端 LOADING/有界重试、协议 v2+`isReady()`、握手竞态用例、批次 25 基线/记录/证据 E-file、契约审计文档） | `git show --stat --oneline 961c6a0e`、`git show --stat --oneline 405c8373` |
| 工作树（冻结 05:42:23） | **201 M + 472 ?? = 673 行；staged = 0** | `git status --porcelain \| wc -l`=673；`awk` 按 X 列分组：`M` 201、`??` 472；`git diff --cached --name-only \| wc -l`=0 |
| 与批次 25 基线冻结值（202 M + 471 ??）差 | M −1（docs 37→36），?? +1（docs 18→19）；期间 `961c6a0e`/`405c8373` 两提交入库了批次 25 工作及其文档；?? +1 的具体身份未逐一追认（他线活动区） | `git status --porcelain` 分组计数；批次 25 基线 `:5`、`:19` |
| DB schema | `STUDY_DATABASE_VERSION = 69`（`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabase.kt:134`；`version = STUDY_DATABASE_VERSION` 于 `:233`）；导出目录 **69 个 schema 文件**，最高 `69.json`（`67/68/69` 为最高三档）；`StudyDatabase.kt` 与 `core/database/schemas/` **git 干净** | `grep -n` + `ls …/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/ \| wc -l`=69 + `git status --porcelain`（空） |
| 请求 schema | `TUTOR_VISUAL_CONTEXT_SCHEMA_VERSION = 18`、`CURRENT_SCHEMA_VERSION = TUTOR_VISUAL_CONTEXT_SCHEMA_VERSION`（`core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/ModelTasks.kt:546-547`）；`ModelTasks.kt` **git 干净** | `grep -n` + `git status --porcelain`（空） |
| VisualJS wire schema | `VisualJsWire.PROTOCOL_VERSION: Int = 2`（`app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsProtocol.kt:35`）；AIDL `boolean isReady();`（`IVisualJsService.aidl:38`，46 行） | `grep -n`。**较批次 25 基线（`:31` = 1）由 1→2**，与计划 §0:6 "协议 v2+isReady" 一致 |

### 1.1 工作树 M 分组（实数，201）

- feature **96**（tutor **37** / library **30** / capture **15** / review **11** / profile **3**）
- core **38**（data **10** / ui **11** / database **8** / export **5** / domain **4**）
- docs **36**；app/src **23**（androidTest 10 / main kotlin 10 / test 3）；tools **4**；仓库根 **3**（`.gitignore`、`dynamic_gui_architecture_plan.md`、`settings.gradle.kts`）；knowledge-production **1**。合计 96+38+36+23+4+3+1 = 201。
- 与批次 25 基线 §1.1 唯一差：docs 37→36。

---

## 2. 热区快照对照（计划 §0:9）

**计划全文无 `HOT_ZONES` 标题/标识**：`grep -c "HOT_ZONES" .zcode/plans/plan-2026-10-27-batch-26-kd66-gate-lockfiles.md` = 0（exit=1），与批次 22/23/24/25 同型。按计划 §0:9 清单逐项对照（冻结时点）：

| 计划 §0 热区 | 当前实测（M） | 判定 |
|---|---|---|
| feature 四模块 | tutor **37**、library **30**、capture **15**、review **11** 全脏（另 profile **3** 亦脏，计划未列） | ✓ 全部在飞 |
| core/ui | **11 M** | ✓ |
| Root | `SmartMistakeBookRoot.kt` M、`SmartMistakeBookDestinations.kt` M | ✓ |
| SecondaryScreens | `SecondaryScreens.kt` M | ✓ |
| core/export | **5 M**（`build.gradle.kts` + 4 源/测试；构成见 §5 表前注） | ✓ |
| core/markdown 整目录 | **整个目录未跟踪**（`?? core/markdown/`，含 `build.gradle.kts`、`gradle.lockfile`）；工作树 `settings.gradle.kts:27` 有 `":core:markdown"`，**HEAD 无**（`git show HEAD:settings.gradle.kts \| grep markdown` exit=1） | ✓（他线迁移中） |
| （计划本行未列）settings.gradle.kts | **M**（`include(` 于 `:19`） | 实脏；锁窗口判定涉及（§5） |

### 2.1 core/domain 开工即脏 M 全路径（4 个，与批次 25 基线逐一相同）

1. `core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/BatchImportRepository.kt`
2. `core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/MistakeDetailRepository.kt`
3. `core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/SchedulingEvaluation.kt`
4. `core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/TutorJudgedReview.kt`

另有未跟踪 `core/domain/gradle.lockfile`。**T1（KD-66 用例）与 T4（split_golden）落点不在此模块**。

### 2.2 core/data 开工即脏 M 全路径（10 个，与批次 25 基线逐一相同）

main（2）：`mistake/RoomMistakeDetailRepository.kt`、`study/RoomTutorToolRunner.kt`。
androidTest（5）：`capture/BatchImportRepositoryInstrumentedTest.kt`、`capture/SplitImportCommitReceiptInstrumentedTest.kt`、`capture/SplitImportReceiptReconciliationInstrumentedTest.kt`、`model/ModelTaskRepositoryInstrumentedTest.kt`、`study/StudySubmissionWithoutFixtureInstrumentedTest.kt`。
test（3）：`knowledge/GoldenRetrievalJvmTest.kt`、`model/OpenAiNativeToolsProtocolTest.kt`、`model/TutorToolRequestDualParseTest.kt`。

另有 `?? core/data/gradle.lockfile`。**`core/data/src/{main,test,androidTest}/.../visual/` 目录均干净**（§3）。

### 2.3 core/database 开工即脏 M 全路径（8 个，与批次 25 基线逐一相同）

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

## 3. 批次 26 落点脏态逐个核对

`git status --porcelain -- <path>` 逐个实测，**全部空（干净）**：

| 落点 | porcelain | 本轮实读补充事实 |
|---|---|---|
| `app/src/main/kotlin/.../visualjs/` 全部 | **空** | 4 个已跟踪 `.kt`：`VisualJsClientEngine.kt`（**1,262 行**；批次 25 基线 938）、`VisualJsService.kt`（**646 行**；原 574）、`VisualJsProtocol.kt`（410 行）、`VisualJsFrameQueue.kt`（216 行，`961c6a0e` 新增） |
| `app/src/main/aidl/.../visualjs/` | **空** | `IVisualJsService.aidl` **46 行**（`isReady()` 于 `:38`）、`IVisualJsCallback.aidl` 32 行 |
| `app/src/androidTest/.../visualjs/` | **空** | `VisualJsIsolationInstrumentedTest.kt` **1,001 行、12 个 `@Test`**；`VisualJsHandshakeRaceInstrumentedTest.kt` 300 行（握手竞态 2 例，fake-binder，不作隔离门证据） |
| `tools/split_golden/` | **空** | 4 文件：`validate_split_golden.py`（317 行）、`measure_split_accuracy.py`（410 行）、`tables/split_golden_v1.json` + `tables/split_golden_v1.json.sha256`（§7） |
| `docs/known-defects.md` | **空** | **2,850 行**；KD-66 `:2573`、KD-72 `:2740`、KD-76 `:2813`、KD-77 `:2842`（§4） |
| `docs/current/visual-generation-contract.md` | **空** | **218 行** |
| `core/data/src/{main,test,androidTest}/.../visual/` | **空** | 批次 25 基线 §3 同表仍成立（7 main + 10 test + 4 androidTest 文件） |

**模块级提醒**：落点干净 ≠ 模块干净——app 23 M、core/data 10 M + 1 ??、core/domain 4 M + 1 ??、core/database 8 M + 1 ??；排提交必须按路径白名单，禁止模块级 `git add`。

---

## 4. 原文抄录

> 以下均为按行区间从当前文件**逐字提取**（非转述）；行号为本次冻结时点。

### 4.1 KD-66（`docs/known-defects.md:2573-2617`；`:2619` 起为 KD-67）

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

**（2026-10-27 批次 25 T1 补充；代码层，本轨未跑 Gradle/设备）.** 上段"竞态交错无确定性测试"的缺口已由新用例补上：`app/src/androidTest/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsHandshakeRaceInstrumentedTest.kt`（新增，未跟踪）钉住 `markConnected`/`markDead`/`isConnected` 共用 `stateLock`、`markDead` 先 CAS `dead` 再取锁的交错不变量（markDead 赢得交错时连接发布让位、已死 link 不得被发布成 CONNECTED）。共 2 例（fake-binder 夹具，不启动真实 `:visual_js`，不作隔离门证据）：①首连握手停在发布点前最后一个握手调用（workerUid 停车窗），markDead 在窗口内完整落地后放行 → `connectForTest=false`、`connectionCount=0`、bind 恰好 1 次；②已连接实例死亡后恰好一次重建，重建握手再输给死亡 → `connectionCount` 仍 1、bind 恰好 2 次、之后 fail-closed 不再 bind（重建预算只扣一次）。同批按批次 24 §5 #11 改正"`abandon()` 未确认分支"的"迟到的 death 会被丢弃"措辞（注释与 logcat 文本；行为不变：迟到死亡仍走 markDead/handleDeath 补完等待者，重建被 fail-closed 门挡下）。非 Gradle 直编 `tmp/verify_batch25_t1_race_compile.py`：主源（Protocol+**真实** FrameQueue+`aidl.exe` 直出的 isReady AIDL 垫片+Engine；不含 `VisualJsService.kt`——T2 在飞引 core/domain 未落地符号，用批次 24 构建中间件旧类解析）→ `MAIN_COMPILE_OK`；`--race-only` 只编本批新用例 → `TEST_COMPILE_OK`（均 exit=0；`aidl.exe` 垫片不是 AIDL 门，AIDL 门仍归 Gradle 任务）。**整测类编译当前红**：`VisualJsIsolationInstrumentedTest.kt:883/:889` 的 `READY_WAIT_MILLIS`/`READY_POLL_MILLIS` 未解析——T2 在飞新增断言引用尚未落地的常量，非 T1 改动；终态编译由门禁脚本统一执行。用例的执行证据待本批设备/门禁轨；**这不改变本条关闭门**——关闭仍以 hard+500 ms 设备用例（#240 形状）为准，该交错测试不覆盖 #240。

**Progress（2026-10-27 批次 25 设备轨；记录员回填）.** 设备证据 `docs/research/evidence/2026-10-27-batch-25/`：① hard+500 ms 专属用例**仍未写**（E-02 `step02-kd66-hard-500ms.md:48` NOT-WRITTEN；全 androidTest 源核查无该断言，现有 watchdog 以 `withTimeoutOrNull(6_000L)` 判死（`VisualJsIsolationInstrumentedTest.kt:198`），弱于关闭门）；② 本条 T1 交错用例 2/2 设备绿（E-01 `step01-visualjs-isolation.md`，fake-binder，不覆盖 #240 形状）。**关闭门不变，本条保持 open**（判据对照见该索引 §9.2）。

**Fix direction.** ①（批次 22 T1 已落代码，未关闭）`:visual_js` `isolatedProcess` + AIDL 控制通道 + `killProcess(myPid())` 进程级硬终止——唯一能终止任意循环的层；设备门见 Progress。② 上游发 >1.0.15 修复后升级并把观察型用例反转为"必须协作中断"断言。③ 引擎层维持 accept-the-leak：每事务独立 daemon dispatcher、强弃 close 放专用守护线程、失败即 fuse/BUSY，如实现 KDoc 所述。

**Reopen condition.** 4E.2 进程隔离落地或上游修复版升级后，以"issue #240 形状在 hard+500ms 内返回拒绝、且后续请求可用"的设备用例关闭；在此之前不得把 T3 标成运行时完成。（T1 的仪器化用例现以 ≤6 s 判死 worker，弱于本条件；关闭时仍以 hard+500 ms 的用例为准。）
```

### 4.2 KD-76（`docs/known-defects.md:2813-2840`；`:2842` 起为 KD-77）

```markdown
## KD-76 (open, 2026-10-26 批次 24 T5 登记（研究复核高危项）；高) · 隔离门 6/6 不含 warm-up/ready：冷启动首个 execute 仍可能被 1500 ms watchdog 误杀，按现判据可在首个用户请求必败时放行 JS 广告

**Symptom（读码 + 批次 23 设备证据）.** `docs/research/2026-10-26-4e2-entry-checklist.md:256` 把必须设备绿的隔离门定义为 `VisualJsIsolationInstrumentedTest` **6/6**，`:269` 进一步写"广告判定：**6/6 必须设备绿**"。但 6 例中没有任何一例模拟"冷启动首个 `execute` 在 CPU 竞争下被 1500 ms 服务硬期限杀掉"：

- 批次 24 新增的冷启动用例 `aColdStartAttachBeyondTheOldConnectBudgetStillRendersTheFirstProgram`（`app/src/androidTest/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsIsolationInstrumentedTest.kt:249-260`）只把 `onServiceConnected` 推迟 7.6 s（`COLD_START_ATTACH_DELAY_MILLIS=7_600L`，同文件 `:383`），断言首连预算与"首次 execute 成功"；其夹具 KDoc 明说"只推迟 attach 回调、不推迟 `bindService`"（同文件 `:379-381`），**没有制造首个 execute 内的 QuickJS 原生装载竞争**。
- 服务端 watchdog 在请求开始即 arm 1500 ms（`app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsService.kt:413-427`；常量 `SERVICE_HARD_DEADLINE_MILLIS=1500L`，`core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/VisualExecutionEngine.kt:126`）。批次 23 冷启动设备日志在同一时刻打出 `hard deadline (1500 ms) for request live-after-stale-terminate; killing the worker`（`docs/research/evidence/2026-10-26-batch-23/step01d-visualjs-isolation-coldboot.md:65`），同文件 `:72` 把它归因为冷启动负载（重启后负载 5.25、system_server/GMS 正在启动）。这与"首个用户请求在冷启动被误杀"是同一失败形状，不是 #240 自续 job。
- 契约已如实登记该缺口：`docs/current/visual-generation-contract.md:72` 写"冷启动首个 execute 的 1500 ms watchdog 误杀（E-01d）仍需 warm-up/ready，本批未修"；批次 24 T1 Progress 也写"冷启动用例只证明首连 15 s 预算（若设备跑绿），不能证明 1500 ms 误杀已消失"（`docs/known-defects.md:2602`）。

**Impact.** 高（广告门完整性 + 用户首个请求可能必败）：隔离门可以在**沉降态**跑绿（批次 23 沉降后 4/5 绿，E-01b/E-01e），而真实用户冷启动后的第一个请求仍可能因原生装载超过 1500 ms 被判 `HARD_DEADLINE_EXCEEDED` / 进程被杀。按 R3 的 6/6 判据，此时可以放行 JS 广告，广告承诺与实际失败直接冲突；该失败形状已被设备日志在冷启动负载条件下实际捕获（批次 23 E-01d：重启后立即执行 5/5 红，隔离进程 attach 4.6–7.5 s 超旧 5 s 预算，重启负载 5.25、system_server/GMS 正在启动，`docs/research/evidence/2026-10-26-batch-23/step01d-visualjs-isolation-coldboot.md:61-72`）——证据只是**一台**冷启动模拟器上的一次实跑，"仍可能被误杀"有证据，"发生在每个新装/重启用户的首个请求"是外推（批次 24 复核 §5 #8）：是否触发取决于设备负载与原生装载耗时，不得当作每例必现，也不得因沉降态复绿而排除。

**Fix direction（实现形态需裁定；不得放宽 1500 ms 程序期限）.** 在服务端引入明确的 warm-up/ready 语义（二选一，需主会话裁定）：① 服务在 `onCreate`/首个请求前完成 `libquickjs.so` 装载与一次空程序 warm-up，ready 之前不 arm 程序 watchdog；② 首个请求的一次性原生装载单独计时并排除在 1500 ms 程序期限之外，装载完成后再 arm 同一 watchdog。无论哪种，**已装载后的真实程序执行仍必须 1500 ms 硬杀**，不得用"首请求放行"掩盖 #240 挂死。客户端只允许在 ready 后发首个用户请求；ready 超时/失败必须 fail-closed 并如实显示加载态，不得把加载失败写成程序错误。广告门前必须把 R3 的 6/6 升级为包含以下设备用例的判据（数量以主会话裁定为准）：③ 冷启动/CPU 竞争下首个 `execute` 返回 `Rendered`，且日志无该请求的 watchdog hard deadline；④ 负控——同一 warm 服务上挂死程序仍在 1500 ms 内 SIGKILL（不得因 warm-up 分支放宽）；⑤ 首连失败/ready 超时走明确加载态，不永久 `unavailable`（与 T1 已登记的 LOADING 缺口一致）。

**验收判据（未运行）.** 设备证据按 R4 规范落盘：设备冷启动（最好含低内存/高负载条件）→ 首个真实请求成功；同一设备 warm 后挂死程序仍 1500 ms 硬杀；`onFatal`/logcat 证明没有"首个请求被 hard deadline 杀掉"的签名。R3 广告门的 6/6 文本必须同步改为包含 warm-up/ready 用例，否则不得以 6/6 绿作为 JS 广告依据。JVM 可覆盖 ready 状态机与超时映射；设备用例不可由 JVM 代替。

**Reopen condition.** 设备证据证明冷启动（含 CPU 竞争）首个 `execute` 成功且不是靠放宽 1500 ms 程序期限获得；warm 服务的挂死负控仍在 1500 ms 内被杀；R3/契约的广告门文本已包含该用例并回链证据。在此之前 JS 继续不广告（本批纪律，计划 `.zcode/plans/plan-2026-10-26-batch-24-isolation-final-kd70.md:15`、`:17`）。

**Progress（2026-10-27 批次 25 T2/T3；代码层，本轨未跑 Gradle/设备）.** Fix direction ① 方向的 warm-up/ready 已落代码层：`VisualJsService.onCreate` 在任何请求前经单线程执行器完成一次性装载 warm-up（真实 `QuickJs.create` + 一次空程序 evaluate，`QuickJsVisualExecutionEngine.warmUpNativeRuntime`）后置单调 `ready`；ready 前 `execute` 拒绝（"ready 之前不 arm 程序 watchdog"由该门结构性保证），warm-up 失败 killSelf 走既有死亡→重建→fail-closed 路径（不新增第三条恢复路径）；AIDL `IVisualJsService.isReady()` 新增（`PROTOCOL_VERSION` 1→2，`app/src/main/aidl/com/tingyun/smartmistakebook/visualjs/IVisualJsService.aidl`），宿主 ready 等待独立预算。**验收判据的设备部分未动**：③ 冷启动首请求绿且无该请求 hard-deadline 签名、④ warm 服务挂死负控仍 1500 ms 硬杀均待设备轨；⑤ ready 失败/首连失败走明确加载态（LOADING/有界重试）仍未落；隔离进程内装载实测成本 UNVERIFIED；R3/准入清单广告门文本的同步修订未做——**本条保持 open，设备验证前 JS 不广告**（沿用批次 24 计划纪律）。另：批次 25 T3 已把契约 §64 队列/背压落为 `VisualJsFrameQueue`（1 在飞 + ≤2 待绘 + latest-wins + BUSY 保帧），与本条互补、不构成本条关闭条件。

**Progress（2026-10-27 批次 25 T2 续：客户端 loading 形状 + warm-up 落点修正 + 用例；代码层，本轨未跑 Gradle/设备；上一块中「⑤ 仍未落」就代码层而言被本块取代，设备判据不变）.**

- **客户端 loading 语义（⑤ 的代码层）**：`VisualJsClientEngine.connectLocked` 在握手成功后轮询 `isReady()`（间隔 100 ms、预算 `READY_TIMEOUT_MILLIS=10_000`——R1' §2.3-4 的待设备校准起点；独立于 attach 预算与 2500 ms 阶段期限；ready 单调，已连接 link 复用不复查）。attach/ready 预算超时 = **loading 形状**：如实折 `ENGINE_CLOSED`（新增 `lastConnectFailure` 诊断通道，文本区分 loading/not-ready 与结构失败），**不写 `unavailable`、不扣重建预算**（`lastBindFailedOnLoading` 旗标豁免 `rebuildConsumed` 检查；死亡/结构失败/握手成功均清零旗标），下一次 execute 重新 bind + 等 ready——批次 24 登记的「连接超时一次失败即永久 unavailable」在 loading 形状上解除；死亡与结构性失败（bind 失败/协议不匹配/同 UID/死亡未确认）仍按既有语义锁存；**死亡路径上的重建照旧记账**（`rebuildAfterDeath` 失败即锁存，契约「最多重建一次」不变）。loading 超时收尾 = terminate + unbind（服务 onDestroy → killSelf，不留孤儿进程）+ 复用 `abandon` 的死亡确认 ≤2 s；loading 放弃后的晚到 attach 经 `Link.abandonedForLoading` 按 markDead(unexpected=true) 收尾——handleDeath 在 ABANDONED 上短路，不锁存、不重建（否则首连超时的重试会被 1 s 后的晚到 attach 锁存）。close() 在 attach/ready 等待中立即终结（批次 24 修复 + ready 轮询的 closed/death 检查），不无限等。
- **warm-up 落点修正（研究复核高危项的保守分支）**：R1' §2.3-1 草图「构造 `QuickJsVisualExecutionEngine` 即触发 `QuickJs.create`」读码复核**不成立**——构造函数（`core/data/.../visual/QuickJsVisualExecutionEngine.kt` 类头）只初始化 measurer/期限、stateLock/fused 集合与 watchdog 线程池，`QuickJs.create` 全文件唯一调用点在事务路径内（`runPhase` 内）。按草图实现会得到 ready=true 但 libquickjs.so 根本未装载——恰是本条 ③ 要抓的失败形状。修正 = **直接触达装载点**：`warmUpNativeRuntime()` 内 `QuickJs.create(Dispatchers.Default)` → 一次 `void 0;` evaluate（①「装载 + 一次空程序」原文口径；上游 `evaluate` 是 suspend，函数内 `runBlocking` 在调用线程完成，无用户 job 不卡排水循环）→ close。修正登记见 R1' §8。
- **用例（本类共 12 例：原 6 + T3 新增 3 + 本轨新增 3）**：harness 新增 `awaitReady`（预算 30 s），既有用例适配新协议（真实 execute 前等 ready；断言零放宽——ready 门在 generation/BUSY/有界读检查之前，不等 ready 会让 STALE/BUSY/超预算断言误见 SHUTTING_DOWN）。本轨新增：`anExecuteAcquiredDuringWarmUpIsRefusedBoundedlyAndNeverWatchdogKilled`（连接后不等 ready 直接 acquire：SHUTTING_DOWN|OK|REJECTED 有界返回、无 fatal/死亡、ready 后同一 worker 可用）、`aFirstConnectTimeoutFailsHonestlyAndTheNextExecuteRetriesWithoutLatching`（attach 推迟 bind+16 s > 15 s 预算：两次 execute 均如实 FAILED、诊断含 loading、`unavailableForTest()==false`、connectionCount==0）、`aReadyTimeoutFailsHonestlyWithoutLatchingAndTheNextExecuteRebinds`（注入恒 not-ready 的本地 fake binder + 测试 seam `readyTimeoutOverrideForTest=250`：ENGINE_CLOSED + not-ready 诊断、不锁存、connectionCount 1→2 证明重新握手）。**seam 消灭的失败**：ready 超时分支在真服务上不可确定性触达（真实 warm-up 总会完成）。fake-binder 平台前提已独立取证：AOSP `android-14.0.0_r1` `Binder.java`（gh api 实读）本地 `linkToDeath`/`unlinkToDeath` 是 no-op 不抛、`isBinderAlive()` 非 final 可覆写。T1 轨 `VisualJsHandshakeRaceInstrumentedTest.FakeWorkerService` 补 `isReady()=true`（协议变更强制：否则其握手在 ready 轮询处按 loading 失败转红）。
- **验证边界（如实）**：非 Gradle 检查已执行：`python tmp/verify_batch25_t2_compile.py` 五阶段全 COMPILE_OK（exit=0：aidl.exe 单输入 ×2 + javac；core:data 引擎；app main 4 文件（含 T3 队列联合编译）；androidTest 2 文件）；既有 `QuickJsVisualExecutionEngineJvmTest` **2/2 绿**（`python tmp/verify_batch25_t2_jvm_regression.py`，JUnitCore，非 Gradle）。「红→绿」为按代码路径的确定性推演（批次 24 同型先例），不是执行证据。③④⑤ 设备判据、READY_TIMEOUT=10 s 的设备校准、warm-up 实测收益全部 UNVERIFIED。

**Progress（2026-10-27 批次 25 设备轨；记录员回填）.** 设备证据 `docs/research/evidence/2026-10-27-batch-25/`：本条 T2 三例设备首验绿（E-01 `step01-visualjs-isolation.md:170-181`）是 **⑤（loading 不锁存、有界重试）的首份（部分）设备证据**——但为沉降态 + 注入形状（attach 推迟 / 恒 not-ready fake binder），非真实冷启动 attach 分布；③（冷启动首请求绿且无该请求 hard deadline）未达，④ 部分（warm 服务挂死负控绿但计时 ≤6 s 弱口径），⑤ 的 UI/文案面与 R3/准入清单广告门文本同步未做（判据对照见该索引 §9.3）；契约 §4 worst-case 29.5 s 启动面未设备实测。**本条保持 open，设备验证前 JS 不广告**。

**交叉引用.** KD-66 跟踪 issue #240 的 hard+500 ms 关闭门；本条只跟踪"冷启动首个 execute 不被一次性装载误杀"的 warm-up/ready 缺口，不代替 KD-66，也不改变其关闭门。批次 24 T1 的 `abandon()` 修复与首连预算属另一失败形状，设备复跑见批次 24 E-01/E-01b（6/6），与本条的 warm-up/ready 门无关。
```

### 4.3 KD-72（`docs/known-defects.md:2740-2762`；`:2764` 起为 KD-73）

```markdown
## KD-72 (open, 2026-10-26 复核返修 #5/#10 登记；中) · dependency locking 已配置但仓库无已跟踪锁文件：DEFAULT 模式下锁门未生效，且已生成的锁与 HEAD/提交面不一致

**Symptom（读码 + 只读命令，2026-10-26 批次 23 复核更新）.** 根 `build.gradle.kts` 的 `subprojects { dependencyLocking { lockAllConfigurations() } }` 已声明；工作树已生成 13 个**未跟踪**的 `gradle.lockfile`（app、core/model、core/domain、core/database、core/data、core/export、core/ui、core/markdown、feature×5；另有 `settings-gradle.lockfile`），`git ls-files '*gradle.lockfile'` 仍为 0，`:benchmark` 无锁。锁生成于他线未提交的 core/ui/core/export/feature/tutor/settings 构建改动之上，且按 `dependencies` 报告任务生成的锁只有 `debugCompileClasspath` + 工具链配置，没有 test/androidTest/runtime。`DEFAULT`（不是 `LENIENT`；默认模式由 Gradle 9.6.1 字节码确认）对"完全没有锁状态"放行，但对"有锁状态却不匹配"在解析期判红——因此这批锁既不能原样提交（core/ui/app 锁缺 HEAD 仍用的 `richtext-commonmark`；core/export 锁多出 HEAD 没有的 `kotlinx-serialization-json`），也不能算作锁门已生效。`buildscript`/`pluginManagement` 未锁；当前真正生效的仍只有 `tools/ci/check_dependency_pins.py` 的 quickjs 三构件 sha256 与两条版本事实。

**Evidence（本轮回跑）.** `python tools/ci/check_dependency_pins.py` → exit 0，`resolved: quickjs=['1.0.15'] kotlin-stdlib=['2.3.21']`；`python -m unittest discover -s tools/tests -p "test_check_dependency_pins.py" -t tools -v` → 14 tests OK。两者都只证明"钉版门"，不证明锁门。**注意解析图来源**：脚本读的是 `app/build/intermediates/incremental/lintVitalAnalyzeStrictOfflineRelease/strictOfflineRelease-artifact-dependencies.xml`（AIDL 修复前的旧构建产物）；本轮禁跑 Gradle，解析图未刷新，因此"resolved 2.3.21"是旧图事实，不是修复后重解析的证据。批次 22 T3 的"dependency locking 已启用"只能记为**部分属实**。

**Evidence（2026-10-26 批次 23 只读复核）.** `git status --porcelain -- '*.lockfile'` → 13 个 `?? <module>/gradle.lockfile` + `?? settings-gradle.lockfile`；`git ls-files '*gradle.lockfile'` → 0；`ls benchmark/gradle.lockfile` → 不存在。逐模块配置集合（从锁文件右侧提取）：core/data/database/export/ui/markdown 与 feature×5 只有 `debugCompileClasspath`（及 annotationProcessor/KSP/kotlinCompilerPluginClasspath 等工具配置）；core/domain/model 只有 `compileClasspath`；app 只有 `localFirstDebugCompileClasspath`/`localFirstDebugRuntimeClasspath`/`localFirstDebugUnitTestCompileClasspath`，无 androidTest/unitTestRuntime。`git show HEAD:core/ui/build.gradle.kts` 仍为 `implementation(libs.richtext.commonmark)`，而 core/ui 与 app 锁均无 `com.halilibo.compose-richtext:richtext-commonmark`；`git show HEAD:core/export/build.gradle.kts` 无 serialization 依赖，而 core/export 锁含 `kotlinx-serialization-*:1.9.0=debugCompileClasspath`。**这些是静态事实；本轮未运行 Gradle/CI，解析期失败行为依据 Gradle 9.6.1 官方文档（`DEFAULT`："Existing entries in the lock state must be matched in the build"、"A version mismatch or missing resolved module causes a build failure"、"Resolution result must not contain extra dependencies compared to the lock state"）。**

**Progress（2026-10-26 批次 23 T1 复核修复；只读 + 构建/CI 文本，未运行 Gradle）.** 已落三件：

- `build.gradle.kts` 的注释改为实际状态（13 个未跟踪锁、DEFAULT、不能原样提交），并新增聚合任务 `lockAllDependencies`（每个子项目的 `dependencies` + `resolveLockableClasspaths`）；后者解析所有 `*CompileClasspath`/`*RuntimeClasspath`，补上 test/androidTest/runtime 覆盖。**该任务未运行 Gradle 验证；首次门禁轨执行时以实际解析结果为准。** 这同时收窄复核发现 #8 的不对称：此后新增/升级 androidTest/testRuntimeOnly 依赖也会进入锁状态（前提是锁按新任务重生成并入库）；`buildscript`/`pluginManagement`、file AAR 与 detached 配置仍不锁（R3 §2.7 的已知残余，不在本条修复范围）。
- 新增 `tools/ci/check_lockfiles.py` + `tools/tests/test_check_lockfiles.py`（9/9 通过：`python -m unittest discover -s tools/tests -p "test_check_lockfiles.py" -t tools -v`）。语义：锁尚未落地时只提示；一旦有第一个跟踪锁，强制每个 included project 都有锁、Android application/library 有 unitTest/androidTest 配置、JVM 有 test 配置，并拒绝未 include 模块的锁。实跑当前仓库 → exit 0，提示 13 个未跟踪锁不能原样提交，并点名 `:benchmark` 缺锁（DEFAULT 下不会报错）。
- CI 增加两个 step：`Dependency lockfile manifest and coverage gate`（始终跑静态脚本）与 `Dependency lockfiles are up to date`（仅当有跟踪锁时跑 `lockAllDependencies --write-locks` + `git status --porcelain -- '*.lockfile'` 判红）。**CI 未运行；Gradle step 的实际行为 UNVERIFIED。**

**Progress（2026-10-26 批次 24 T3，只读核对；无 Gradle/git 写）.** 复核确认"仓库无任何锁文件 / 默认 `LENIENT`"的旧表述作废：`git status --porcelain | grep -i lockfile` → 13 条 git 可见的未跟踪锁条目（含 `settings-gradle.lockfile`）；盘上实为 **14 个** `gradle.lockfile`（`core/markdown/gradle.lockfile` 因整个目录未跟踪被折叠成 `?? core/markdown/`）；`git ls-files '*gradle.lockfile'` → 0；`benchmark/gradle.lockfile` 不存在；根 `build.gradle.kts:19-33` 写明默认 `LockMode.DEFAULT`（不是 `LENIENT`）且这批锁不能原样提交；`python tools/ci/check_lockfiles.py` → exit 0，输出"13 个未跟踪锁文件…不能原样提交；缺锁 project：：benchmark（DEFAULT 模式下不会报错）"。这批锁生成于他线未提交的构建改动、与 HEAD 不一致，**不得原样 `git add`**；等干净树用 `lockAllDependencies --write-locks` 重生成后再单独提交。**KD-72 保持 open**，剩余条件见下。

**仍未闭合（KD-72 保持 open）**：① 锁必须在**干净、待提交的树**上重新生成（当前锁与 HEAD 不一致，且 hot-zone build 改动不能随本批提交）；② `:benchmark` 缺锁、`:core:markdown` 模块与锁都不能单独入库（R3 §6 P5）；③ 一次负控（人为抬版/加依赖在解析期红）未执行；④ 现有 13 条 git 可见锁条目（盘上 14 个文件）不得原样 `git add`。**T1 不能写成已完成。**

**Impact.** medium（供应链：POM 静默抬版/新增传递依赖在解析期不会被拦；当前唯一的漂移被 sha256/版本门覆盖，但覆盖范围是定向的，不是全图；且现有锁与提交面不一致，误提交会红 CI）。

**Fix direction.** 主会话/门禁轨在他线 build 改动落定后，于干净树执行 `./gradlew --no-configuration-cache --no-parallel --console=plain lockAllDependencies --write-locks`，审 diff 后把 14 个 included project 的锁与对应源码一起提交；`:core:markdown` 未入库时先按 R3 P5 排除，等模块落地再补。随后跑 `python3 tools/ci/check_lockfiles.py` 与 CI 的写锁-diff step，并按 R3 §5.5 做负控。在锁文件入库前，不得把 T3 写成"依赖锁定已完成"。

**Reopen condition.** 仓库内每个 subproject 都有 `gradle.lockfile`，且一次负控（人为抬高某个传递依赖版本）在解析期被锁状态判红后关闭。
```

### 4.4 批次 25 记录 §9 主会话收口（`docs/research/2026-10-27-batch-25-record.md:361-371`；本记录有两个 `## 9`，`:354` 起是记录员验证结果，`:361` 起才是主会话收口）

```markdown
## 9. 主会话收口（2026-10-27，实跑证据与裁定）

- **单测门**（11 模块 `--continue`）：**exit=0**（4 executed / 224 up-to-date，R15 修复后的树）。
- **强门**（六 androidTest 编译 + `:app:assembleLocalFirstDebug`）：**exit=0**（4 executed / 268 up-to-date）。
- **主会话两项收口修复**（复核 R15/发现#13 的处置）：
  1. **R15 旗标恢复竞态（裁定+修复）**：`handleDeath` 顶部无条件清零 `lastBindFailedOnLoading` 移到 ABANDONED 短路**之后**——loading 放弃的死亡（我们主动 terminate）不再清豁免，真实非预期死亡仍清；竞态确定性消除（不再依赖窄窗写序），`VisualJsClientEngine.kt` handleDeath 头注释已写明语义。
  2. **A-14 夹具确认**：修复轮已把第三负向调用改为新开 pipe（树内在位），设备红例归因成立；复跑归批次 26 设备轨。
- **提交**：`961c6a0e`（31 文件，4,979 插入/128 删除，含块 11 的 4 个 tmp 复算脚本与两个 00-index 勘误），按 §7 分块 add；13+1 个未跟踪锁未纳入。
- **推送**：远端基线已被他线推进到 `25ddb602`（KB 批次 24），本批 `961c6a0e` 快进推送成功并 `ls-remote` 复核一致。
- **本批不声称全绿**：设备 12 例隔离 11 绿 1 红（红=A-14 夹具缺陷，已修待复跑）；KD-66 hard+500ms 用例未写（E-02 如实 NOT-WRITTEN）；KD-76 ③④⑤ 设备判据未执行；锁文件未生成（他线已把批 23 的 13+1 陈旧锁 **git add 进暂存区**——原样提交将判红，见 R3 §0，跨线协调项）；arm64/16KB UNVERIFIED；真实 Provider UNVERIFIED；JS 仍不广告。
- Mimosa 提交仍无完整扫描结论（scanner_enobufs）——不宣称项目安全。
```

---

## 5. 锁窗口判定（关键，对照计划 §0:8）

### 5.1 实测事实（本轮命令，全部只读）

| # | 检查 | 命令 | 结果 |
|---|---|---|---|
| F1 | 暂存区总量 | `git diff --cached --name-only \| wc -l` | **0**（暂存区完全为空） |
| F2 | 暂存区锁文件 | `git diff --cached --name-only \| grep -i lockfile` | **exit=1，0 条** |
| F3 | 锁文件提交史 | `git log --all --oneline -- '*gradle.lockfile' 'settings-gradle.lockfile'` | **空**（任何分支从未提交过锁文件） |
| F4 | 已跟踪 *lockfile* 文件 | `git ls-files '*lockfile*'` | 4 个，**均非锁文件**：`docs/research/2026-10-26-lockfile-generation.md`、`docs/research/2026-10-27-lockfile-clean-tree-check.md`、`tools/ci/check_lockfiles.py`、`tools/tests/test_check_lockfiles.py` |
| F5 | 盘上锁文件 | `test -f` 循环 | **14 个**：13 个 `gradle.lockfile`（app、core/{model,domain,database,data,export,ui,markdown}、feature×5）+ `settings-gradle.lockfile`；`benchmark/` 无锁 |
| F6 | git 可见锁条目 | `git status --porcelain \| grep -i lockfile` | **13 条 `??`**（`core/markdown/gradle.lockfile` 被整目录未跟踪折叠） |
| F7 | 锁门脚本实跑 | `python tools/ci/check_lockfiles.py` | **exit=0**，输出：`OK: dependency locking 尚未落地：没有任何已跟踪的 gradle.lockfile，KD-72 保持 open，本门跳过（CI 的 --write-locks step 同样跳过）；工作区存在 13 个未跟踪锁文件（例如 app/gradle.lockfile），不能原样提交；缺锁 project：:benchmark（DEFAULT 模式下不会报错）` |
| F8 | `.gitignore` 对锁无规则 | `grep -n lockfile .gitignore` | exit=1（锁未被 ignore，也未被去 ignore） |

### 5.2 六个解析图 build 文件的暂存/未暂存状态（`git status --porcelain=v1 -- <paths>`）

| 文件 | porcelain | 说明 |
|---|---|---|
| `settings.gradle.kts` | ` M`（**未暂存**） | worktree 在 `:27` 加 `":core:markdown"`；**HEAD 无 markdown**（`git show HEAD:settings.gradle.kts \| grep markdown` exit=1） |
| `core/ui/build.gradle.kts` | ` M`（**未暂存**） | |
| `feature/tutor/build.gradle.kts` | ` M`（**未暂存**） | |
| `core/markdown/build.gradle.kts` | `??`（**未跟踪**，整目录） | |
| `core/export/build.gradle.kts` | ` M`（**未暂存**） | |
| `.gitignore` | ` M`（**未暂存**） | 未暂存 diff 仅 +3 行：`# Superdesign temporary templates` + `.superdesign/tmp/`（`git diff .gitignore` 实读）——**与锁无关** |

**六个全部未入库，且 0 个在暂存区。**

### 5.3 锁-HEAD 陈旧性的静态复核（本轮独立验证 KD-72 Symptom 的两处不一致）

- `grep -o "richtext[a-z-]*" core/ui/gradle.lockfile` → `richtext-ui`、`richtext-ui-android`、`richtext-ui-material`（**无 `richtext-commonmark`**）；`git show HEAD:core/ui/build.gradle.kts` → 有 `richtext.commonmark`、`richtext.ui.material`。→ 锁缺 HEAD 仍在用的依赖，与 KD-72 `:2742` 记录一致。
- `grep -o "kotlinx-serialization[a-z-]*" core/export/gradle.lockfile` → `kotlinx-serialization-{bom,core,core-jvm,json,json-jvm}`；`git show HEAD:core/export/build.gradle.kts \| grep -c serialization` → **0**（exit=1）。→ 锁含 HEAD 没有的依赖，同上一致。
- 口径边界：以上是静态字节比对，**未运行 Gradle 解析**，"解析期判红"行为仍以 Gradle 9.6.1 文档口径引用（KD-72 `:2746`），本轮未重新证明。

### 5.4 判定：**"干净树重生成"当前不可行 → 计划 T0 走"否则只登记"分支**

依据（三条独立成立）：

1. **树不干净**：KD-72 Fix direction（`:2760`）要求"他线 build 改动落定后，于干净树执行 `lockAllDependencies --write-locks`"。当前树 201 M + 472 ??，含全部热区模块与他线在飞改动。
2. **build 文件未入库、HEAD 与 worktree 解析图不同**：重生成锁依赖 worktree 的 6 个 build 改动（尤其 `:core:markdown` include 仅存在于 worktree），而 HEAD 的 `settings.gradle.kts` 无 markdown。现在重生成 = 锁再次生成于**未入库构建改动**之上，精确重演 KD-72 Symptom（`:2742` "锁生成于他线未提交的构建改动之上…不能原样提交"）——5.3 的静态比对证实锁-HEAD 不一致至今成立。
3. **core/markdown 不能单独入库**（KD-72 `:2756` ②引 R3 §6 P5；F7 的门语义也"拒绝未 include 模块的锁"——HEAD 未 include markdown，其锁现在入库会被门拒绝）。

**与计划 §0:8 的两处状态失配**（以本轮实测为准）：

| 计划 §0:8 断言 | 本轮实测 |
|---|---|
| "13+1 个陈旧锁已被他线 **git add 进暂存区**（原样提交判红）" | **暂存区为 0 条**（F1/F2）；锁文件无任何提交史（F3）。"暂存跨线危险"当前不存在——或已被他线撤销，或为计划撰写时的瞬态；本文如实登记现态，无法追溯中间过程（UNVERIFIED：暂存态何时消失） |
| "重生成前置=6 个 build 文件入库（**4 已暂存**、core/export/build.gradle.kts 与 .gitignore 未暂存）" | **0 个已暂存**；6 个 = 5 ` M` 未暂存 + 1 `??`（§5.2）。"4 已暂存"的暂存态同样已不存在 |

**给主会话的操作含义**：T0 不触发 R3 §4 重生成流程；本批 T3（锁文件重生成，world.run）按计划 §1 的窗口判定落在"只登记"分支——登记本次窗口判定事实（暂存态消失、6 build 文件仍未入库、树不干净），不得执行 `git add` 锁文件、不得跑 `--write-locks`。

---

## 6. KD-66 hard+500ms 用例设计输入（计划 §1 R1/T1）

### 6.1 现 watchdog 用例计时结构（`VisualJsIsolationInstrumentedTest.kt`，1,001 行，12 @Test，git 干净）

`theWatchdogKillsAHungWorkerAndAFreshGenerationHasNoBusyState`（`:186-232`）本轮实读：

- `:188-193` `harness()` → `connect()` → `assertTrue(first.awaitReady())`（批次 25 起先等 ready，KD-76 ④ 注释在 `:190`）→ `initialize(303L)`。
- `:197` `val hung = async { first.execute(generation, "issue-240", issue240Request()) }`（issue #240 自续 job 形状，`issue240Request()` 定义 `:610`）。
- `:198` **`val died = withTimeoutOrNull(6_000L) { first.deathSignal().await() } != null`** ← 现行弱口径计时点（批次 25 基线后行号不变，KD-66 `:2613` 引用一致）。
- `:202-206` `assertTrue(died, "the L2 watchdog did not kill :visual_js within 6 s …")`；`:199-201` 注释保留"钉版升级后反转为协作中断断言"的方向。
- `:207-212` `hung.await()` 断言 `outcome.status != VisualJsWire.RESULT_OK`（挂死不得 Rendered）。
- `:214-228` 新 harness `connect` → `awaitReady` → `initialize(304L)` → `execute` 断言 `RESULT_OK`（新进程/新 generation 不继承 BUSY）。
- 类内另一处 `withTimeoutOrNull(6_000L)` 在 `:456`（`aSecondConcurrentRequestIsRejectedBusyWhileTheFirstIsInFlight` 的死亡观察，同弱口径）。
- harness ready 面板：`awaitReady(timeoutMillis = READY_WAIT_MILLIS)` `:881-887`，常量 `READY_WAIT_MILLIS = 30_000L`（`:993`）、`READY_POLL_MILLIS = 100L`（`:996`）——批次 25 T1 时 `:883/:889` 未解析的常量已由 T2 落地（KD-66 `:2611` 所记"整测类编译当前红"为中间态，现树常量在位）。

### 6.2 服务端 killSelf 路径行号（`VisualJsService.kt`，646 行，git 干净；本轮 grep -n 实测）

| 行 | 路径 |
|---|---|
| `:75` | `private val ready = AtomicBoolean(false)`（单调，KD-76 ①） |
| `:113` / `:127-135` | `onCreate` → `warmUpOnJsExecutor()`；`QuickJsVisualExecutionEngine.warmUpNativeRuntime()` → `ready.set(true)`；失败分支 `killSelf("warm-up failed: …")`（`:135`） |
| `:145` / `:151` | `onUnbind` → `killSelf("the host unbound the visual_js worker")`；`onDestroy` → `killSelf(…)` |
| `:154` | `private fun killSelf(reason: String)` 定义 |
| `:205-207` | AIDL `override fun isReady(): Boolean = ready.get()` |
| `:242-246` | `execute` 的 ready 门：ready 前拒绝且**不排队**（"ready 之前不 arm 程序 watchdog"的结构性保证） |
| `:377` | `armWatchdog(execution)`——请求已过 ready 门、在 `runRequest` 内 arm（一次）；`:375-376` 注释说明装载必已完成 |
| `:481` | fatal → `killSelf("fatal: $diagnostics")` |
| `:484-496` | `armWatchdog`：schedule `killSelf("watchdog hard deadline")`（`:494`）于 `SERVICE_HARD_DEADLINE_MILLIS`（`:496`） |
| `:291` | `terminate(generation)` 接受 → `killSelf("terminate(generation=$generation) accepted")` |
| `:583` / `:606` | 宿主回调失败 / 结果管道写失败 → killSelf |
| `:612` / `:617` | 宿主死亡观察：`DeathRecipient { killSelf("the host process died") }`；已死立即杀 |

常量：`SERVICE_HARD_DEADLINE_MILLIS = 1500L`（`core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/VisualExecutionEngine.kt:126`）。

### 6.3 宿主"按时返回"可测断言面（`VisualJsClientEngine.kt`，1,262 行，git 干净）

| 项 | 位置 | 值/事实 |
|---|---|---|
| 宿主阶段期限 | `withTimeout(VisualExecutionBudget.HOST_PHASE_DEADLINE_MILLIS)` 于 `:269`（会话体 `:227` 起含 2500 ms 期限，注释 `:72`/`:83`/`:315`） | 2500 ms；`execute()` 的有界返回由它保证——**`hung.await()` 在 #240 形状下不会无限挂**，这就是"按时返回"的既有可测面 |
| 死亡确认 | `DEATH_CONFIRM_TIMEOUT_MILLIS = 2_000L`（`:1219`）、轮询 50 ms（`:1222`） | 死亡事实 + `isBinderAlive` 兜底，2 s 上界 |
| 连接预算 | `FIRST_CONNECT_TIMEOUT_MILLIS = 15_000L`（`:1206`）、`RECONNECT_TIMEOUT_MILLIS = 10_000L`（`:1212`）、attach 超时分类 loading `:690-710` | 在宿主 2500 ms 期限**之外**（KD-66 `:2583` 口径） |
| ready 轮询 | `READY_TIMEOUT_MILLIS = 10_000L`（`:1230`）、间隔 100 ms（`:1233`）、`readyBudgetMillis()` `:809`（测试 seam `readyTimeoutOverrideForTest`） | 握手后、发请求前 |
| loading 不锁存 | `lastConnectFailure`（`:143`）；`:597`/`:638-639`/`:690-767` loading 豁免重建预算与 `unavailable` 锁存 | 批次 25 T2/R15 修复后语义 |

**hard+500 ms 判据的落点建议（按 KD-66 Reopen condition `:2617` 原文，本轮只归纳可测面，不写用例）**：

1. **计时基准**：围绕 `execute(generation, "issue-240", issue240Request())` 的提交→有界返回测墙钟（`SystemClock.uptimeMillis()`/`System.nanoTime()`），断言 **≤ SERVICE_HARD_DEADLINE_MILLIS + 500 = 2000 ms**；对照面是把 `:198` 的 `withTimeoutOrNull(6_000L)` 换成 2_000L 量级口径（death 观察与返回判据都收敛到 2 s 内）。
2. **返回必须是拒绝**：`hung.await()` 的 `outcome.status != RESULT_OK`（现 `:207-212` 面保留），且诊断可归因 hard-deadline/引擎关闭系（不放宽为"任何非 OK"以外的强断言需主会话裁定）。
3. **后续请求可用**：死亡确认后新 generation `execute` 返回 `RESULT_OK`（现 `:214-228` 面保留；或经同 client 死亡重建路径，注意重建预算记账语义）。
4. **负控边界**：ready 之后才发请求（`awaitReady`），避免把 warm-up 装载耗时混入 1500 ms 计时——这是 KD-76 ④ 与 KD-66 关闭门在夹具上的分界。

### 6.4 设备基线现状（计划 §0:7 的文本层核对）

计划 §0:7 "隔离 12 例 11 绿（1 红=A-14 夹具缺陷，已修待复跑）+ Race 2/2；KD-70 P2 于 ps16k 绿 20/20；R8 release 绿；备份 34/34"——与批次 25 记录 §9 `:370` 及 E-file 索引文本**逐项一致**（文本层 ✓）。**本轮未复跑任何设备用例，设备层数字 UNVERIFIED**（基线核对员职责边界）。

---

## 7. tools/split_golden 现状与 AUD-08 "工具半边"缺口（计划 §0:10、§1 T4）

### 7.1 现状（git 干净；4 文件）

| 文件 | 规模 | 职责（脚本自述口径，本轮实读头部） |
|---|---|---|
| `tables/split_golden_v1.json` + `.sha256` | — | 冻结金标 v1：7 页题框标注 + sha256 旁车；JSON 顶层键 `evidenceGrade/generatedAt/pages/sampleRule/schemaVersion`，页级键 `boxes/file/layout/questionCount/subject`（`python -c json.load` 实测） |
| `validate_split_golden.py` | 317 行 | 机械校验 C1-C6：C1 冻结字节（sha 旁车=磁盘字节、canonical 逐字节、CRLF=0）、C2 schema+evidenceGrade 冻结常量、C3 每页 questionCount==boxes、C4 框合法性、C5 file 去重、C6 pages≥7 |
| `measure_split_accuracy.py` | 410 行 | 三指标度量（v2 口径）：① 题数完全一致率（单题页不拆分记一致）② 边界 IoU≥0.5 题占比（只度量不设门）③ 切残率（含 0.3-0.6 阈值敏感性）；门式 ①≥0.9 且 ③≤0.05；**"本脚本只报数，exit 0 表示度量完成（不等于门通过）"**；应用导出输入 `build/split-eval/app-split.json`（设备 DB 快照，构建产物不在 git 内） |

### 7.2 对照 new-stages-spec AUD-08 原文（`docs/research/2026-10-11-new-stages-spec.md` §4C `:162-169`，逐字引关键条）

```markdown
### 4C：AUD-03前置与AUD-08切分金标

- [ ] 先交付切分门说明和冻结数据集：记录科目、题型、公式/图、手写、分页/跨页、题块边界、完整选项、附图归属；分训练/开发/验收，不能用同一批调门又验门。
- [ ] 标注来源和复核分别登记；专家/用户/模型单源不能混称。未获得人工金标或独立复核时如实写代理金标/UNVERIFIED，不能用模型自检成功充当人工正确率。
- [ ] 门说明分别给完整题命中、切残、漏题、错误合并、重复率的分母/阈值/样本量/统计区间和严重度。**切残不得自动入库**沿用既有红线；数值须按既有"立项自定/科学落实"流程形成设计产出，不在本次维护中编造。
- [ ] 修改/验证定位：feature/library的SplitImportReviewRoute、core/database的RoomSplitImportStore、app的CaptureResume导航，core/data的BatchSplitRecognizer/RoomBatchImportRepository。
- [ ] 回归至少包括：选3题保存第1题后返回继续第2/3题；第2题失败/取消/杀进程仍可恢复；切残题干/丢选项/附图错归被挡；既有PDF导入和单张录入不退化。
```

### 7.3 "工具半边"缺口清单（现状 vs 规范，逐项）

1. **指标覆盖 3/5**：现只有 ①完整题命中（题数一致率）③切残率 ②IoU；规范要求**五类**——"完整题命中、切残、**漏题、错误合并、重复率**"。漏题仅以"未配对金标题按未达标计/只体现在①与②"间接体现（脚本自述），**错误合并、重复率无指标实现**。
2. **门说明无独立载体**：分母/阈值有（脚本头部 + `docs/research/2026-10-14-4c-stage1-record.md:19-22`），但**样本量/统计区间/严重度**无处落（金标仅 7 页 16 框；stage1 记录明说门值"标'立项自定'"建议值、"尚未落盘为独立仓内产物"——该句指当时；现脚本已入库，门说明文档仍缺）。规范原文："门说明**分别**给……分母/阈值/样本量/统计区间和严重度"。
3. **无 train/dev/acceptance 切分**：单一冻结集 `split_golden_v1`；规范"分训练/开发/验收，不能用同一批调门又验门"未满足。
4. **标注来源/复核登记面窄**：JSON 仅 `evidenceGrade` 冻结常量（validate C2 防"模型标注被改写成人工专家"）+ `sampleRule`；**无标注员/复核人字段**；stage1 记录 `:89` 如实登记"金标为模型标注、无人工专家"（代理金标口径 ✓，但"标注来源和复核**分别登记**"的结构化登记未见）。
5. **门未接线**：`grep -rn "split_golden" tools/ci/ .github/` → 无输出（`.github/workflows/` 存在：`android-check.yml`、`schema-export.yml`）——度量脚本无 CI/门禁/准入门引用；"exit 0 = 度量完成 ≠ 门通过"的判门步骤（红/绿判定器）不存在；"切残不得自动入库"红线与工具无自动化挂接。
6. **度量输入依赖 git 外产物**：`build/split-eval/app-split.json` 为设备 DB 快照导出，重跑度量需先有导出流程；本轮未验证其存在性。

批次 25 基线 §0:10 "tools/split_golden 干净（批次 24/25 两次条件轨均未指派）" ✓ 与本轮一致——上述缺口全部留给本批 T4。

---

## 8. 设备状态（只读查询）

`adb`/`emulator` 不在 PATH，用绝对路径（`/c/Android/Sdk/...`）。

`/c/Android/Sdk/platform-tools/adb.exe devices`（exit=0）：

```text
List of devices attached
emulator-5554	device
emulator-5556	device
emulator-5562	device
emulator-5584	device
```

`/c/Android/Sdk/emulator/emulator.exe -list-avds`（exit=0）：`audit_user_visible_20261016`、`kb_test_device`、**`ps16k_arm64`（新增）**、`ps16k_x86_64`、`test_device`。

| 序列 | AVD 名（`emu avd name`） | PAGE_SIZE（getconf） | SDK | boot_completed | 判定 |
|---|---|---|---|---|---|
| emulator-5554 | `test_device` | 4096 | 34 | 1 | 4KB 设备 |
| **emulator-5556** | **`ps16k_x86_64`** | **16384** | **36** | **1** | **ps16k 16KB AVD 在线** |
| emulator-5562 | `audit_claude_coverage_20261024`（不在 `-list-avds`，陈旧名） | 4096 | 34 | 1 | 4KB 设备 |
| emulator-5584 | `kb_test_device` | 4096 | 34 | 1 | 4KB 设备 |

**新事实（较批次 25 基线 §9）**：`-list-avds` 现含 **`ps16k_arm64`** AVD（批次 25 时无），但**未在线**（不在 `adb devices`）；KD-77 `:2844` 记录的 SDK 镜像清单含 `system-images;android-36;google_apis_ps16k;arm64-v8a`，说明他线已建 arm64 16KB AVD。arm64 16KB 运行时证据（P-02/R-03 缺口）仍 **UNVERIFIED**——AVD 存在 ≠ 已跑。本轮未启动/停止任何模拟器、未安装/运行任何 APK。

---

## 9. 摘要：与计划 §0 不符处与开工须知

### 9.1 逐条对照

| 计划 §0（行） | 本轮实测 | 判定 |
|---|---|---|
| `:5` HEAD = `405c8373` = origin/main | 两侧同 hash；ahead/behind `0 0` | ✓（**本地引用**；未 fetch，远端 UNVERIFIED） |
| `:6` 批次 25 交付清单 | `961c6a0e` 提交构成（协议 v2+isReady、FrameQueue、race 用例、契约审计文档）与清单一致 | ✓ 文本层 |
| `:7` 设备基线（12 例 11 绿 + Race 2/2 等） | 与批次 25 记录 §9 `:370` 文本一致 | ✓ 文本层；**设备层 UNVERIFIED（未复跑）** |
| `:7` KD-66 hard+500ms 用例未写 → KD-66 仍 open | E-02 `step02-kd66-hard-500ms.md` 引用 NOT-WRITTEN（KD-66 `:2613`）；台账 KD-66 `:2573` = open；androidTest 现有 12 例无 hard+500ms 断言（`grep withTimeoutOrNull` 仅 6 s 口径两处） | ✓ |
| `:8` "13+1 锁已被 git add 进暂存区" | **暂存区 0 条**；锁无提交史 | ✗ **状态已迁移**（§5.4） |
| `:8` "6 build 文件入库（4 已暂存…）" | **0 已暂存**（5 ` M` + 1 `??`）；HEAD settings 无 markdown | ✗ **状态已迁移**（§5.2） |
| `:9` 热区清单 | 六项全部在飞 + settings.gradle.kts M | ✓ |
| `:10` tools/split_golden 干净 | porcelain 空 | ✓ |

### 9.2 开工前须知（必须带进批次 26）

1. **锁窗口判定 = "只登记"分支**（§5.4）：暂存态已消失、6 build 文件未入库、树 201 M + 472 ?? 不干净。T3 不得 `git add` 锁文件、不得跑 `--write-locks`；登记本文 §5 事实即可。原"暂存跨线警示"（记录 §9 `:370`）的紧急处置对象已不在暂存区。
2. **KD-72 的盘面事实未变**：14 个陈旧锁在盘（F5）、与 HEAD 静态不一致复核成立（§5.3）、`check_lockfiles.py` exit=0 处于跳过模式（F7）、`:benchmark` 仍缺锁。
3. **visualjs 行号全面漂移**：Engine 938→1,262 行、Service 574→646 行、隔离测试 6→12 例（1,001 行）；批次 25 基线 §5/§6 的行号表**不可直接引用**，T1/T2 落码前按本文 §6 现值重锚。AIDL `isReady()` 在 `IVisualJsService.aidl:38`，协议 v2（`VisualJsProtocol.kt:35`）。
4. **KD-66 关闭门口径不变**：hard+500 ms 设备用例（Reopen condition `:2617`）；现 `:198` 的 6 s 弱口径与 `:456` 的第二处 6 s 是 T1 的替换点。夹具必须先 `awaitReady`（ready 门在 arm 之前是 KD-76 ①的结构性保证，warm-up 耗时不得混入 1500 ms 计时）。
5. **core/domain、core/data、core/database 开工即脏**（§2.1-2.3，与批次 25 逐一相同）：排提交按路径白名单，禁止模块级 `git add`；visual 落点本身干净（§3）。
6. **AUD-08 工具半边缺口为 6 项**（§7.3）：五类指标缺二、门说明无独立载体、无 train/dev/accept 切分、来源/复核登记面窄、门未接线、度量输入依赖 git 外导出。T4 扩展范围以本清单对照规范原文，不编造阈值（规范明说"数值……不在本次维护中编造"）。
7. **ps16k_arm64 AVD 已存在但未在线**：16KB x86_64 轨可直接用 emulator-5556；arm64 16KB 运行时证据仍缺（P-02/R-03），AVD 存在不改变该缺口状态。
8. **DB/请求 schema 未变，VisualJS wire 已 v2**：DB v69、请求 schema 18 不需要升级；wire 协议 1→2 是批次 25 已提交事实（§1），本批 T1/T2 沿用 v2，不得回退。

**UNVERIFIED / 本轮未做**：未跑任何 Gradle 任务（单测门/强门/assemble 均未跑）；未跑 JVM/androidTest/设备用例；未 `git fetch`；未编译 AIDL；未复跑隔离 12 例、KD-76 ③④⑤、KD-70 P2、R8 冒烟、备份抽验；未运行 Gradle 解析验证锁-HEAD"解析期判红"行为（静态字节比对已做，§5.3）；未追溯"暂存态 13+1 锁"何时/如何消失（无 reflog/暂存史取证）；未验证 `build/split-eval/app-split.json` 存在性；批次 25 设备 E-file 内容未重读（只引用记录/索引文本）。**本轮实际执行**：`git` 只读查询/计数、`grep`/`sed`/`wc`/`test -f`/`ls` 文件读取、`python tools/ci/check_lockfiles.py`（只读脚本，无写盘）、金标 JSON 只读解析、`adb devices`/`emu avd name`/`getconf`/`getprop` 只读查询、`emulator -list-avds`。

---

## 附录 A：本轮实际执行的只读命令（可复跑）

```bash
git rev-parse HEAD; git rev-parse origin/main
git rev-list --left-right --count origin/main...HEAD          # 0 0
git log --oneline -3; git show --stat --oneline 961c6a0e; git show --stat --oneline 405c8373
git status --porcelain | wc -l                                # 673
git status --porcelain | awk '{print $1}' | sort | uniq -c    # 201 M / 472 ??
git diff --cached --name-only | wc -l                         # 0
git diff --cached --name-only | grep -i lockfile              # exit=1
git ls-files '*lockfile*'                                     # 4 个非锁文件
git log --all --oneline -- '*gradle.lockfile' 'settings-gradle.lockfile'   # 空
git status --porcelain -- <每个批次26落点>                     # 全空
git status --porcelain=v1 -- settings.gradle.kts core/ui/build.gradle.kts feature/tutor/build.gradle.kts core/markdown/build.gradle.kts core/export/build.gradle.kts .gitignore
git show HEAD:settings.gradle.kts | grep markdown             # exit=1
git diff .gitignore; git diff settings.gradle.kts
grep -o "richtext[a-z-]*" core/ui/gradle.lockfile; git show HEAD:core/ui/build.gradle.kts | grep -o "richtext[a-z.]*" | sort -u
grep -o "kotlinx-serialization[a-z-]*" core/export/gradle.lockfile; git show HEAD:core/export/build.gradle.kts | grep -c serialization   # 0
for d in app core/{model,domain,database,data,export,ui,markdown} feature/{capture,review,tutor,library,profile} benchmark; do test -f "$d/gradle.lockfile" && echo "ONDISK $d"; done; test -f settings-gradle.lockfile && echo ONDISK
python tools/ci/check_lockfiles.py                            # exit=0（跳过模式）
grep -n STUDY_DATABASE_VERSION core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabase.kt   # :134 = 69
ls core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/ | wc -l                                # 69
grep -n 'TUTOR_VISUAL_CONTEXT_SCHEMA_VERSION\|CURRENT_SCHEMA_VERSION =' core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/ModelTasks.kt   # :546-547 = 18
grep -n PROTOCOL_VERSION app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsProtocol.kt                    # :35 = 2
grep -n "HOT_ZONES" .zcode/plans/plan-2026-10-27-batch-26-kd66-gate-lockfiles.md                                          # 0 命中
grep -n "withTimeoutOrNull\|@Test" app/src/androidTest/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsIsolationInstrumentedTest.kt
grep -n "killSelf\|armWatchdog\|isReady\|ready" app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsService.kt
grep -n "const val\|withTimeout" app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsClientEngine.kt
grep -n "SERVICE_HARD_DEADLINE_MILLIS" core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/VisualExecutionEngine.kt   # :126 = 1500L
wc -l app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/*.kt app/src/androidTest/kotlin/com/tingyun/smartmistakebook/visualjs/*.kt docs/known-defects.md docs/current/visual-generation-contract.md
grep -n "^## KD-66\|^## KD-72\|^## KD-76" docs/known-defects.md    # :2573 / :2740 / :2813
grep -n "^## 9" docs/research/2026-10-27-batch-25-record.md        # :354 / :361
sed -n '155,200p' docs/research/2026-10-11-new-stages-spec.md      # §4C AUD-08
python -c "import json;d=json.load(open('tools/split_golden/tables/split_golden_v1.json',encoding='utf-8'));print(sorted(d.keys()))"
grep -rn "split_golden" tools/ci/ .github/ 2>/dev/null            # 无输出（门未接线）
grep -rln "切残率\|题数一致率\|split_golden" docs/ 2>/dev/null
"/c/Android/Sdk/platform-tools/adb.exe" devices
"/c/Android/Sdk/emulator/emulator.exe" -list-avds
for s in emulator-5554 emulator-5556 emulator-5562 emulator-5584; do adb -s $s emu avd name; adb -s $s shell getconf PAGE_SIZE; adb -s $s shell getprop ro.build.version.sdk; adb -s $s shell getprop sys.boot_completed; done
```
