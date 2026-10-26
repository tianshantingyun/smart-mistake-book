# 批次 24 基线核对（2026-10-26，基线核对员）

> **只读核对**：未运行 Gradle、未编译、未执行任何 JVM/仪器化/设备用例、未 `git fetch`、未做任何 git 写操作（本文件是唯一写盘产物）。设备面只执行了只读 `adb` 查询。对照计划：`.zcode/plans/plan-2026-10-26-batch-24-isolation-final-kd70.md`（下称"计划"）§0。
>
> **快照时点**：2026-10-26 19:34:43 +0800（`git status --porcelain` 冻结 669 行）。核对期间工作树有他线并发写入：首轮读数为 666 行（200 M + 466 ??），冻结时为 669 行（200 M + 469 ??）；已确证新增的两个未跟踪文件见 §1.2。**冻结后树仍在变**（19:42 复查为 677 行 / 206 M / 471 ??），下文所有行号/计数以冻结时点为准，不能当作稳定终值。
>
> "干净"定义：`git status --porcelain -- <path>` 无输出（不代表内容无缺陷）。

---

## 1. HEAD / 工作树 / DB schema / 请求 schema

| 项 | 实测 | 证据（命令/路径行号） |
|---|---|---|
| HEAD | `5bc8bdf868e1296049ab901cf1da69e136062006`（`main`） | `git rev-parse HEAD` |
| origin/main | `5bc8bdf868e1296049ab901cf1da69e136062006`（**与 HEAD 相同**） | `git rev-parse origin/main`；**本轮未 `git fetch`**，这是本地远端跟踪引用，远端实时 hash 未核（UNVERIFIED） |
| ahead/behind | `0 0`（`origin/main...HEAD`） | `git rev-list --left-right --count origin/main...HEAD` |
| HEAD 构成 | `5bc8bdf8` 是**纯文档提交**：`docs/research/2026-10-26-batch-23-record.md` +10 行；父提交 `f8a362ee`（批次 23 主提交） | `git show --stat --oneline 5bc8bdf8`；`git log --oneline -3` |
| 工作树（冻结 19:34:43） | **200 M + 469 ?? = 669 行；staged = 0，`MM` = 0** | `git status --porcelain \| wc -l`=669；`grep -c '^ M'`=200；`grep -c '^??'`=469；`git diff --cached --name-only \| wc -l`=0 |
| DB schema | `STUDY_DATABASE_VERSION = 69`（`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabase.kt:134`；`version = STUDY_DATABASE_VERSION` 于 `:233`）；导出目录共 **69 个 schema 文件**，最高 **`69.json`**；**`70.json`/`71.json` 不存在**（`git ls-files` 空）；`StudyDatabase.kt` 与 `core/database/schemas/` 均 **git 干净** | `grep -n` + `ls` + `git status --porcelain -- core/database/schemas/`（空）+ `git ls-files '*70.json' '*71.json'`（空） |
| 请求 schema | `TUTOR_VISUAL_CONTEXT_SCHEMA_VERSION = 18`、`CURRENT_SCHEMA_VERSION = TUTOR_VISUAL_CONTEXT_SCHEMA_VERSION`（`core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/ModelTasks.kt:546-547`）；`ModelTasks.kt` **git 干净**；`core/model` 唯一脏项是未跟踪 `core/model/gradle.lockfile` | `grep -n` + `git status --porcelain -- core/model/`（仅 `?? core/model/gradle.lockfile`） |
| VisualJS wire schema | `VisualJsWire.PROTOCOL_VERSION: Int = 1`（`app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsProtocol.kt:31`） | `grep -n` |

### 1.1 工作树按顶层归组（M，实数）

- **M（200）**：feature 95（tutor **37** / library **29** / capture **15** / review **11** / profile **3**）、core 38（core/data 10 / core/ui 11 / core/database 8 / core/export 5 / core/domain 4）、docs **36**、app/src **23**、tools 4、仓库根 3（`.gitignore`、`dynamic_gui_architecture_plan.md`、`settings.gradle.kts`）、knowledge-production 1。
- 与批次 23 基线（193 M：feature 89 / core 38 / docs 37 / app 21 / tools 4 / 根 2 / `settings.gradle.kts` 1 / knowledge-production 1）相比：M **+7**，其中 feature +6（tutor +1、library +3、capture +2）、app +2、docs −1。

### 1.2 未跟踪（??）概览与核对期间新增

- **??（469）** 主要块：core/ui 24（23 个 Claude*/UI 新文件 + `gradle.lockfile`）、feature/tutor 21（20 个新文件 + lock）、docs **18**、tools 12、knowledge-production 11、feature/library 11（10 文件 + lock）、app 5（`app/gradle.lockfile` + 3 个 androidTest + `app/src/main/assets/`）；其余 **367 条**为仓库根审计/临时散点（`.agent_*`、`_tmp*`、`tmp*`、`out/`、`output/`、`nul`、`x` 等）。
- **核对期间新增（首轮 666 → 冻结 669 的差异中已确证）**：
  1. `core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/study/UsageMaterialAllocationTest.kt`（`??`）
  2. `docs/research/2026-10-26-library-layout-refinement.md`（`??`）
- 这两个文件**不属于批次 24 任何落点**；排提交时须按路径白名单排除。`core/data` 的 ?? 因此从 1（lock）增至 2。

---

## 2. 热区快照 vs HOT_ZONES（计划 §0 第 2 条）

计划全文无 `HOT_ZONES` 标题（与批次 22/23 相同）；按计划 §0 的"热区不变"清单逐项对照（冻结时点）：

| 计划 §0 热区 | 当前实测（M / ??） | 判定 |
|---|---|---|
| feature 四模块 | tutor **37/21**、library **29/11**、capture **15/1**、review **11/1** | ✓ 全部在飞 |
| core/ui | **11 M + 24 ??**（23 新文件 + lock） | ✓ |
| Root | `SmartMistakeBookRoot.kt` M、`SmartMistakeBookDestinations.kt` M | ✓ |
| SecondaryScreens | `SecondaryScreens.kt` M | ✓ |
| settings.gradle.kts | **M**（`include(...)` 在 `:19`，`:core:markdown` 于 `:27` 仍 include；未跟踪模块仍在） | ✓ |
| core/export | **5 M**（与计划"五 M"逐数相符） | ✓ |

**计划 §0 未点名、本批基线条目要求点名（开工即脏）**：

| 对照项 | 当前实测（M / ??） | 判定 |
|---|---|---|
| core/domain | **4 M** + 1 lock | 脏；全路径见 §2.1（本批 R1/T1 不要求改） |
| core/data | **10 M + 2 ??** | 脏；全路径见 §2.2；visual 子目录干净（§3） |

**计划热区未列、当前实脏（与本批共存面）**：

- **core/database 8 M**：`RoomBatchImportStore.kt`、`TutorConversationDatabaseContract.kt`、`dao/BatchImportDao.kt`、`dao/TutorConversationDao.kt`、`port/CleanRedrawTaskPort.kt`、3 个 LibraryFts 仪器化测试。
- **feature/profile 3 M**；docs 36 M + 18 ??（本批 docs 落点虽干净，但 docs 面整体仍脏）；tools 4 M + 12 ??；knowledge-production 1 M + 11 ??；`core/markdown/` 整个目录未跟踪。
- **app 实为 23 M**（计划只点名 Root/SecondaryScreens）：main 10（`ExportPdfWorker.kt`、`LearningMasteryScreen.kt`、`ReviewReminderCoordinator.kt`、`ReviewReminderPlatform.kt`、`ReviewReminderScreen.kt`、`SchedulingSettingsScreen.kt`、`SecondaryScreens.kt`、`SmartMistakeBookDestinations.kt`、`SmartMistakeBookRoot.kt`、`StartupStateBanner.kt`）+ androidTest 10 + app test 3。

### 2.1 core/domain 开工即脏 M 全路径（4 个，全量点名）

1. `core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/BatchImportRepository.kt`
2. `core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/MistakeDetailRepository.kt`
3. `core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/SchedulingEvaluation.kt`
4. `core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/TutorJudgedReview.kt`

core/domain 无其他 M；另有一个未跟踪 `core/domain/gradle.lockfile`。批次 24 的 R1/T1 落点在 `app` visualjs（干净），**不要求改 core/domain**；如研究输出要改 `VisualExecutionBudget`（`core/domain/.../VisualExecutionEngine.kt:106-139`），该文件当前是干净的，但所在模块不是。

### 2.2 core/data 开工即脏 M 全路径（10 个，全量点名）

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

core/data 另有两个 ??：`core/data/gradle.lockfile`、`core/data/src/test/.../study/UsageMaterialAllocationTest.kt`（核对期间新增，**非 visual 落点**）。**T2 的 `core/data/.../visual/` 目录逐个实测干净**（§3），但排提交时必须显式按路径排除上面 10 个 M 与该新增测试。

---

## 3. 批次 24 落点脏态逐个核对

`git status --porcelain -- <path>` 逐个实测，**全部空（干净）**：

| 落点 | 状态 | 本轮实读补充事实 |
|---|---|---|
| `app/src/main/kotlin/.../visualjs/` 全部文件 | **干净** | 3 个已跟踪 .kt：`VisualJsClientEngine.kt`（811 行 / 35,791 B）、`VisualJsProtocol.kt`（406 行 / 19,083 B）、`VisualJsService.kt`（574 行 / 25,240 B）；均随 `69e5f591` 入库，之后未再改 |
| `app/src/main/aidl/.../visualjs/` | **干净** | `IVisualJsService.aidl` 37 行、`IVisualJsCallback.aidl` 32 行；两份都在 `git ls-files` 内（不是被覆盖后的游离态） |
| `app/src/main/AndroidManifest.xml` | **干净** | 隔离 service 声明所在文件未被他线改动 |
| `app/src/androidTest/.../visualjs/` | **干净** | `VisualJsIsolationInstrumentedTest.kt` 未改；abandon 断言在 `:203`（用例）、`:210`（first abandon）、`:215`（second abandon） |
| `core/data/src/main/.../visual/`（7 文件） | **干净** | `DefaultFigureGenerationCoordinator.kt`、`FigureGenerationAuditRecorder.kt`、`FigureGenerationReconciler.kt`、`QuickJsVisualExecutionEngine.kt`、`RoomFigureDispatchLedger.kt`、`RoomFigureGenerationAuditRecorder.kt`、`VisualJsFailureClassification.kt` |
| `core/data/src/test/.../visual/`（10 文件） | **干净** | 含 `QuickJsVisualExecutionEngineJvmTest.kt`、`VisualJsFailureClassificationTest.kt` 等 |
| `core/data/src/androidTest/.../visual/`（4 文件） | **干净** | 含 `QuickJsVisualExecutionEngineInstrumentedTest.kt`、`NativeLibraryPageSizeInstrumentedTest.kt` |
| `docs/known-defects.md` | **干净** | 2,767 行；KD-66…KD-75 全在（§4） |
| `docs/research/evidence/` | **干净** | 已跟踪 **15 个 `.md`**（全部在 `2026-10-26-batch-23/`）；另有 1 个 `.mimosa/hook-status/*.json` 在盘但被 `.gitignore:29`（`.mimosa/`）忽略，不是证据 |
| `docs/current/visual-generation-contract.md` | **干净** | 218 行；章节 `§1…§10`（`grep '^## '`），§4 隔离/IPC 在 `:60`，§5 通用场景在 `:92` |

**模块级提醒**：`core/data` 整体仍有 10 M + 2 ??；"落点干净"只对上面点名的文件/目录成立，不能读成"模块干净"。`core/ui`、`core/export`、feature 四模块同理。

**批次 23 收口的 spot-check（只读）**：`docs/research/2026-10-26-batch-23-record.md` 的 §8 收口块（`:406-414`）记录了提交 `f8a362ee`（50 文件）与推送 `f0a145f8..f8a362ee`；HEAD 现为 `5bc8bdf8`（其子，纯文档 +10 行），与 `git log` 一致。**提交/推送本身本轮未复核远端**（未 fetch，UNVERIFIED）。

---

## 4. KD 条目原文与关闭/剩余判据

`docs/known-defects.md`（git 干净，2,767 行）中与本批相关的五条**全部 `open`**：

| 条目 | 行范围 | 标题（原文节选） |
|---|---|---|
| KD-66 | `:2573-2598` | "KD-66 (open, 2026-10-26 登记（批次 21 T3/复核发现 #1/#13/#21；高）) · QuickJS issue #240 使引擎硬期限不能返回：`execute()` 永不返回、`close()` 卡死、`inFlight` 不复位" |
| KD-70 | `:2665-2681` | "KD-70 (open, 2026-10-26 登记（批次 21 T3/复核发现 #6/#21；中）) · QuickJS 内存上限路径在 x86_64 未成立：设备实际 `INVALID_PROGRAM`，契约要求的 `MEMORY_LIMIT` 未达" |
| KD-72 | `:2702-2722` | "KD-72 (open, 2026-10-26 复核返修 #5/#10 登记；中) · dependency locking 已配置但仓库无已跟踪锁文件：DEFAULT 模式下锁门未生效，且已生成的锁与 HEAD/提交面不一致" |
| KD-74 | `:2736-2746` | "KD-74 (open, 2026-10-26 复核返修 #6 登记；中) · 批次 22 设备结论（16KB 3/3、R8 三变体冒烟、备份/迁移）无日志落盘" |
| KD-75 | `:2748-2767` | "KD-75 (open, 2026-10-26 批次 23 T4 登记；高) · PDF 导出无文档级隐式标识：官方 PDF 载体是 Info 字典 `/AIGC`，当前只剩逐图显式图注；且"仅 AI 图"写入判据漏掉模型解析文本" |

### 4.1 KD-66（`:2573-2598`）原文要点

Impact（`:2577`）：

> **Impact.** high（调用方拿不到 1500 ms 硬上界；任何不可信图程序可拖死一个引擎实例。批次 22 T1 已落 L2 进程级硬终止代码；批次 23 设备轨已验证 4/5 路径（隔离往返、watchdog 硬杀、死亡后重建均绿），但关闭门与 abandon 路径均未达成，故本条维持 open）。

批次 23 Progress（`:2588-2594`）逐字：

> **Progress（2026-10-26 批次 23 设备轨；supersedes 上方"复跑 UNVERIFIED / 第 5 例未执行"的记录时点表述）.** 设备证据已按 R4 规范落盘（`docs/research/evidence/2026-10-26-batch-23/`）。dup 修复后的 `VisualJsIsolationInstrumentedTest` 在 emulator-5554（API 34，x86_64，APK sha256 `e5ec3a89…bb441`）真跑两轮：**5 例全部执行，4 绿 1 红**。
>
> - **绿**：`workerIsIsolatedAndAProgramRoundTripsWithoutBeingKilledLater`（隔离 UID 成立 + AIDL 往返成功）、`aStaleGenerationIsRejectedAndAStaleTerminateIsANoOp`（过期 generation/terminate no-op）、`theWatchdogKillsAHungWorkerAndAFreshGenerationHasNoBusyState`（1500 ms 硬期限 `killProcess` + 新 generation 无 BUSY）、`theClientRebuildsOnceAfterADeathAndTheNewWorkerIsNotBusy`（死亡确认后重建一次，新 worker 无 BUSY）。
> - **红**：`theClientRebuildsAtMostOnceAfterConfirmedAbandons`（`the first abandon did not confirm death`）——见上方新增用例段与 E-01c；这是 client `abandon()` 的结构性缺陷，不是 dup 修复回退。
> - **冷启动对照**：重启后立即跑 5/5 红（隔离进程 attach 4.6–7.5 s > 客户端 5 s 连接预算，E-01d）；沉降约 2 min 后回到 1/5 红（E-01e），证明加载态超时与结构性失败已分离。
> - **结论**：`libquickjs.so` 装载、`killProcess` 权限、watchdog 杀进程、死亡后重建均已有设备绿证据；**KD-66 仍 open**，原因只剩关闭门——现有 watchdog 用例以 ≤6 s 判死，弱于 Reopen condition 的 hard+500 ms，且 issue #240 形状本身仍泄漏 daemon 线程（E-02 的观察用例绿=已知形态，不是可终止）。abandon 缺陷建议另立 KD（本条只跟踪 #240 关闭门）。

关闭判据（`:2598`，原文）：

> **Reopen condition.** 4E.2 进程隔离落地或上游修复版升级后，以"issue #240 形状在 hard+500ms 内返回拒绝、且后续请求可用"的设备用例关闭；在此之前不得把 T3 标成运行时完成。（T1 的仪器化用例现以 ≤6 s 判死 worker，弱于本条件；关闭时仍以 hard+500 ms 的用例为准。）

**剩余**：关闭门（hard+500 ms 证据）未达；abandon 缺陷仍红且**尚无独立 KD**（当前 KD 列表止于 KD-75，无 KD-76）；JS 不广告。

### 4.2 KD-70（`:2665-2681`）原文要点

Progress（`:2671`）逐字：

> **Progress（2026-10-26 批次 22 T2 + 批次 23 设备轨；core/data + 仪器化用例）.** 根因与探针已按 R3 源码级研究落定，代码与用例已改。**设备轨已执行（2026-10-26，E-02）：全量 20 例 19/20 绿，唯一红 P2**——P2 `heapExhaustionWithLostErrorMessageIsAttributedByMemoryUsage` 实际输出 `INVALID_PROGRAM: [UNATTRIBUTED] phase prepare failed with a message-less engine error (raw=null, mallocSize=177216, mallocLimit=16777216)`（`docs/research/evidence/2026-10-26-batch-23/step02-quickjs-oom.md:112`）："消息丢失"分支已命中（`raw=null`），但读到的堆只有约 1.06% of 16 MiB，低于 90% 阈值，分类器按设计落 `UNATTRIBUTED`；**原因已明确为夹具没有稳定推满堆，不是分类器阈值问题**。P1 的 `MEMORY_LIMIT` 主路径已在设备成立（诊断含 `16777216`/`out of memory`），P3/P4/P5 绿。**KD-70 保持 open：P2 判据未达成**；下一步按 Fix direction ② 先裁定等价判据或换能稳定推满堆的夹具（不得下调 90% 阈值）。

修复方向（`:2679`）与关闭判据（`:2681`）原文：

> **Fix direction.** ① 用能真实触达 QuickJS 堆上限的夹具（分阶段分配并在 OOM 前读 `memoryUsage` 记录）重跑设备用例；② 若 AAR 在 x86_64 的 OOM 必然先报 `string too long`/`prepare null`，则把契约/用例改为"有界拒绝 + 诊断保留原因"的可证判据，需先裁定（不得直接放宽断言）；③ `classifyJsException` 增加结构化诊断或明确已知边界。**（批次 22 T2 已按 ①/③ 落分类器与 P1–P5 探针；①的设备执行已完成（E-02：P1 绿、P2 红且诊断落盘），②的等价判据裁定仍待主会话，见 Progress。）**
>
> **Reopen condition.** 设备用例以 `MEMORY_LIMIT`（或经裁定的等价判据）转绿，且 `memoryUsage`/诊断证据落盘后关闭。

**剩余**：P2 的"消息丢失 + 堆证据"分支未在设备成立；不得下调 90% 阈值；等价判据尚未裁定。

### 4.3 KD-72（`:2702-2722`）原文要点

Symptom 首句（`:2704`）：

> **Symptom（读码 + 只读命令，2026-10-26 批次 23 复核更新）.** 根 `build.gradle.kts` 的 `subprojects { dependencyLocking { lockAllConfigurations() } }` 已声明；工作树已生成 13 个**未跟踪**的 `gradle.lockfile`（app、core/model、core/domain、core/database、core/data、core/export、core/ui、core/markdown、feature×5；另有 `settings-gradle.lockfile`），`git ls-files '*gradle.lockfile'` 仍为 0，`:benchmark` 无锁。

未闭合四点（`:2716`）逐字：

> **仍未闭合（KD-72 保持 open）**：① 锁必须在**干净、待提交的树**上重新生成（当前锁与 HEAD 不一致，且 hot-zone build 改动不能随本批提交）；② `:benchmark` 缺锁、`:core:markdown` 模块与锁都不能单独入库（R3 §6 P5）；③ 一次负控（人为抬版/加依赖在解析期红）未执行；④ 现有 13 个锁不得原样 `git add`。**T1 不能写成已完成。**

关闭判据（`:2722`）原文：

> **Reopen condition.** 仓库内每个 subproject 都有 `gradle.lockfile`，且一次负控（人为抬高某个传递依赖版本）在解析期被锁状态判红后关闭。

**本轮实测补充（重要口径差）**：`git status --porcelain | grep -i lockfile` 显示 **13 个未跟踪锁条目**（`app`、`core/{data,database,domain,export,model,ui}`、`feature/{capture,library,profile,review,tutor}`、根 `settings-gradle.lockfile`）；但**盘上实际有 14 个**——`core/markdown/gradle.lockfile`（6,470 B，mtime 10-26 15:04）存在，只因整个 `core/markdown/` 目录未跟踪而被折叠成一条 `?? core/markdown/`，不进入 lockfile 计数。`benchmark/gradle.lockfile` 不存在。KD-72 的 Symptom 文案（含 core/markdown）与盘上 14 个一致；计划 §0 的"13 个"是 git 可见条目数。按 KD-72 的"每个 subproject 都要有锁"判据，`core/markdown` 的锁同样不能单独入库（模块未跟踪），`:benchmark` 仍缺。

### 4.4 KD-74（`:2736-2746`）原文要点

Progress 结论段（`:2744`）逐字：

> **Progress（2026-10-26 批次 23，设备证据落盘轨；只读 + 仓内 `.md`）.** 设备结论已按 R4 v1 落到 `docs/research/evidence/2026-10-26-batch-23/`（`00-index.md` + E-00…E-09）。……**C6 未满足且本条不能关闭**：记录虽已落盘，但另有未闭合子项——本机缺 `llvm-readelf` 别名二进制（E-09 用同发行版 `llvm-readobj`，`research/evidence/2026-10-26-batch-23/step09-elf-llvm-readobj.md:107`），等价性仍需主会话裁定；arm64 16KB 运行时未执行（ps16k AVD 为 x86_64）；T1 仍 1/5 红（abandon 结构性缺陷，E-01b/E-01c）、KD-70 P2 仍红（E-02）。按 R4 §6.3，设备红绿不影响本条，但"四类结论齐备 + 等价判据裁定"未齐，故 **KD-74 保持 open，不写 closed**。若主会话裁定 `llvm-readobj` 等价于 `llvm-readelf`，C1③ 即成立，届时只需补记录 "KD-74 closed" 即可关闭本条。

关闭判据（`:2746`）原文：

> **Reopen condition.** 上述四类结论各有可复跑命令与落盘结果，且 T1 修复后的设备复跑结果一并落盘后关闭。

**剩余（按 KD 当前文本）**：条目仍写 open；主会话已在批次 23 记录 §8（`:413`）裁定 `llvm-readobj --program-headers` 与 `llvm-readelf -l` 字段级等价——**该裁定尚未回写 KD-74 条目**；T1 4/5 红时的复跑证据已落盘，但 T1 本身待本批修复。这正是计划 T3 要同步的两件事。

### 4.5 KD-75（`:2748-2767`）原文要点

Symptom（`:2750`）与第二处缺口（`:2757`）逐字：

> **Symptom（读码 + 官方指南）.** 单份/批量导出的 PDF 由 `android.graphics.pdf.PdfDocument`（Skia）生成，文件内只有逐张 AI 图的显式图注"本图由 AI 生成"（`core/export/.../AiGeneratedImageCaption.kt:27`），**没有**文档级隐式标识。TC260-PG-20258A §6.3 明确规定 PDF 文本文件的隐式标识载体是 Document Information Dictionary，写法为 `/AIGC({"Label":..., ...7 字段...})`（R1 `docs/research/2026-10-26-pdf-implicit-labeling.md:65-69`；官方 PDF sha256 `6bf6c118…3b0e`）。图片指南 20259A 只覆盖图片文件、不含 PDF（R1 :71-86），因此"AI 图装进 PDF"是两份指南之间的空档，不是豁免；《标识办法》第五条要求文件元数据含隐式标识（R1 :29）。**当前状态：既无隐式标识，也不能声称"法规未要求"。**
>
> → 结果：**若题面来自 CAPTURE_PARSE，PDF 可能包含模型产出的文本块，但导出层无法区分**；按"仅 AI 图"判据写 `/AIGC` 会漏标这类 PDF。保守口径下不能把"文本块都是用户/OCR 内容"当成已验证事实。模型结构化转写是否属于"生成合成文本"是适用范围解释（同 R1 U2），但写入判据不能建立在"PDF 一定没有模型文本"这个未经代码支持的前提上。

Fix direction（`:2763`）与关闭判据（`:2767`）原文：

> **Fix direction（热区空出后，须整批落地）.** 不引第三方库；在 `core/export` 新增 `PdfAigcInfoInjector`（append-only 增量更新，重定义/新增 Info 对象，写 `/AIGC` 7 字段 JSON；值保持 ASCII；已存在则幂等替换；遇到 `/Encrypt`、`/Linearized`、坏 trailer 一律 fail-closed）。写入判据必须**同时**覆盖：(a) `isAiGeneratedExportImage` 为真的 AI 图（现行口径）；(b) 文档含 `MODEL_DOCUMENT_PARSE` 文本。……缓存必须失效：`MistakePdfEligibility.kt:186` 的 `"mistake-export-v3"` → `v4`……打印部分页 `MistakePdfDelivery.writeSubset` 重绘丢元数据，需缓冲后注入或明确登记。DOCX 的 `docProps/custom.xml` 对称缺口另立条目（R1 U9），不在本条。
>
> **Reopen condition.** 含 AI 图或含 `MODEL_DOCUMENT_PARSE` 文本的导出 PDF 能在设备上被独立读者读回正确 `/AIGC`，纯用户内容 PDF 读不到；旧缓存指纹已升版且无旧无标识产物；显式图注逐位不变（或按 R1 判据登记差异）；设备日志落盘（按 R4 规范）后关闭。**不得以"法规未要求 PDF 隐式标识"关单**——20258A §6.3 明文有 PDF 方法（R1 §1.3.1）。

**剩余**：零代码（仍无 `/AIGC`）；写入判据 (b) 需要 provenance 传递；Android PDFium 接受度（R1 U3）未验；`core/export` 当前 5 M 热区，计划明确本批**仅登记、不实现**。

### 4.6 批次 23 记录 §5 / §8 的复核结论（原文）

文件：`docs/research/2026-10-26-batch-23-record.md`。

**§5 口径说明（`:204`）逐字**：

> **口径说明**：本节清单来自任务材料"五、复核发现与处置"的 JSON（24 条，含 3 组重复：#1/#21、#3/#22、#9/#23），severity/status 按原复核；"处置"为记录员按当前树核对后的状态。修复环材料另称"11 条复核发现"，与 24 条清单不一致，按实际清单逐条登记，不合并成"11 条全部关闭"。`已修` = 当前树文本/代码已按发现改；`已登记` = 写入 KD/研究/构建注释但事项未闭合；`残留` = 当前树仍未改且未关闭。

**§5 高风险条 #12（`:219`）逐字（节选）**：

> | 12 | 13 个锁文件 | 锁是在他线未提交的 core/ui/core/export/settings 构建改动上生成的，与 HEAD 不一致；原样提交会使 CI 在锁解析期红 | **高** | 已独立复核 | **未修（KD-72 open）**：……`grep -rn richtext-commonmark core/ui/gradle.lockfile app/gradle.lockfile` 无命中……`git show HEAD:core/export/build.gradle.kts \| grep -c serialization` = 0，而 `grep -c kotlinx-serialization core/export/gradle.lockfile` = 5。**不得原样 `git add`**。 |

**§5 记录员追加核对（`:233-237`）**：

> 1. `--write-locks exit=0` 与 `:benchmark` 缺锁并存（§4）：`exit=0` 不能证明 14 模块全覆盖；且仓内无生成日志，生成命令的实际范围不可复核。
> 2. T5 summary 的"核对清单与发现见 evidence"在仓内无对应 E-file（§3.5）；T5 只能算文档完成，不能算设备/门禁证据。
> 3. `00-index.md` 之外的证据文件均未改；记录员只读，未把 `build/` 当证据。

**§8 第一块"主会话实跑与收口状态（工单给定）"（`:400-404`）逐字**：

> - **门禁（工单给定）**：单测门第 1 轮 `exit=0`；修复环复跑 `exit=0`；强门第 1 轮 `exit=0`；`--write-locks` `exit=0`（stdout 尾部 `BUILD SUCCESSFUL in 2m 55s` / `228 actionable tasks: 22 executed, 206 up-to-date` / `Configuration cache entry stored.`）。
> - **修复环（工单给定）**：材料称 11 条复核发现全部属实；其中设备/文档类已修，锁文件结构性条目已修到"生成面/门禁面"，但重生成与 Gradle/CI 实跑受本任务约束阻塞，KD-72 如实保留 open。**本节与 §5 的 24 条清单口径不一致，记录员按实际清单登记，未把 11 条直接写成"全部关闭"。**
> - **提交/推送**：截至本记录时点，**未提交、未推送**；建议清单与排除项见 §7。提交由主会话执行；非快进 merge，不 rebase。
> - **本批不声称全绿**：T1 1/5 红（abandon 结构性缺陷）、KD-70 P2 红、锁文件 13/14 且未入库（KD-72）、T4 零代码（KD-75）、KD-74 未关闭、KD-73 未证、真实 Provider UNVERIFIED、JS 不广告。

**§8 第二块"主会话收口（2026-10-26，实跑证据）"（`:408-413`）逐字**：

> - **单测门**（11 模块 `--continue`）：**exit=0**（BUILD SUCCESSFUL，1 executed / 227 up-to-date——批次 23 修复环后的树未再变）。
> - **强门**（六 androidTest 编译 + `:app:assembleLocalFirstDebug`）：**exit=0**（BUILD SUCCESSFUL，2 executed / 270 up-to-date）。
> - **提交**：`f8a362ee`（50 文件），按 §7 的 11 块分块 add；**13 个未跟踪 `gradle.lockfile` 与 `settings-gradle.lockfile` 均未纳入**（与 HEAD 不一致，KD-72 保持 open，待干净树重生成后单独提交）；热区文件未触碰。
> - **推送**：`f0a145f8..f8a362ee` 已上 origin/main（`ls-remote` 复核 = 本地 HEAD）。
> - **本批不声称全绿**：T1 隔离 4/5（client `abandon()` 死亡确认结构性失败，4 次复现）；KD-70 P2 仍红（mallocSize 177216≈1.06% 未达 90% 阈值）；KD-72/73/74/75 open；T4 只研究+登记未改导出链；真实 Provider UNVERIFIED；JS 仍不广告。
> - **主会话裁定（KD-74 证据等价性）**：`llvm-readobj --program-headers` 与官方判据所用 `llvm-readelf -l` 读取同一 ELF 程序头表；对"每 LOAD p_align≥2^14"与"RELRO 终点 mod 16K 及官方独占 LOAD 豁免"两项，字段级等价成立，接受为可复核证据（E-09 已附程序头原始字段）。KD-74 其余关闭项（记录回链）随本节成立。

> **注意**：§8 同一节号出现两次。第一块是"截至本记录时点未提交、未推送"的过程状态，第二块是主会话收口后的终态；引用时必须用第二块（提交/推送已发生）。`git log` 侧证实 `f8a362ee` 与 `5bc8bdf8` 存在，与第二块一致。

---

## 5. VisualJsClientEngine.kt 的 abandon 路径（`:549-601` 全文）

文件：`app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsClientEngine.kt`（811 行，git 干净）。以下为 **549-601 行原文（含行号）**：

```kotlin
549	        }
550	        if (closed.get()) {
551	            // close() 与握手竞态：close 先看到 link==null（死亡已清空）而不会再解绑这个
552	            // 新实例；这里补上，避免关闭后泄漏一条 binding/进程。
553	            candidate.state = LinkState.ABANDONED
554	            runCatching { candidate.service?.terminate(candidate.generation) }
555	            candidate.service = null
556	            runCatching { context.unbindService(candidate.connection) }
557	            unlinkDeath(candidate)
558	            synchronized(linkLock) { if (link === candidate) link = null }
559	            return null
560	        }
561	        return candidate
562	    }
563	
564	    private suspend fun abandon(link: Link) {
565	        if (link.state == LinkState.ABANDONED) return
566	        link.state = LinkState.ABANDONED
567	        runCatching { link.service?.terminate(link.generation) }
568	        link.service = null
569	        runCatching { context.unbindService(link.connection) }
570	        unlinkDeath(link)
571	        val confirmed = withTimeoutOrNull(DEATH_CONFIRM_TIMEOUT_MILLIS) { link.death.await() } != null
572	        if (!confirmed) {
573	            // 契约 §4：死亡未确认时展示 fallback，不能并发放大进程数。
574	            link.state = LinkState.DEATH_UNCONFIRMED
575	            unavailable = true
576	        }
577	        synchronized(linkLock) { if (this.link === link) this.link = null }
578	    }
579	
580	    private fun dispose(dead: Link) {
581	        dead.state = LinkState.ABANDONED
582	        runCatching { dead.service?.terminate(dead.generation) }
583	        dead.service = null
584	        runCatching { context.unbindService(dead.connection) }
585	        unlinkDeath(dead)
586	        synchronized(linkLock) { if (link === dead) link = null }
587	    }
588	
589	    private fun handleDeath(deadLink: Link, cause: String, unexpected: Boolean) {
590	        synchronized(linkLock) {
591	            if (link === deadLink) link = null
592	        }
593	        unlinkDeath(deadLink)
594	        // 关键：unbind 移除 AMS 的重启理由；不 unbind 时系统可能在宿主决定重建前又把
595	        // 服务拉起来（R1 §5.4，AndroidX PDF 同型注释）。
596	        runCatching { context.unbindService(deadLink.connection) }
597	
598	        val call = activeCall
599	        if (call != null && call.link === deadLink && !call.pending.isCompleted) {
600	            fusedDigests.add(call.digest)
601	            call.pending.complete(
```

### 5.1 `Link.death` / `markDead` / `unlinkDeath` 的关系（精确）

| 符号 | 行 | 行为 |
|---|---|---|
| `Link.death` | `:662` | 每个 `Link` 新建一个 `CompletableDeferred<Unit>()`；**per-bind**，不复用旧实例信号（KDoc `:502-504`） |
| `deathRecipient` | `:679-681` | `IBinder.DeathRecipient { markDead("binder died", unexpected = true) }` |
| `unlinkDeath(link)` | `:646-651` | `val binder = link.binder ?: return`；`link.binder = null`；`runCatching { binder.unlinkToDeath(link.deathRecipient, 0) }`——**解注册后 binderDied 不再回调** |
| `markDead(cause, unexpected)` | `:758-765` | CAS `dead` false→true；若 state ≠ ABANDONED 置 DEAD；`service = null`；**`if (!death.isCompleted) death.complete(Unit)`**；`connected.complete(false)`；调 `handleDeath` |
| `abandon(link)` | `:564-578` | `state=ABANDONED` → `terminate(generation)`（oneway）→ `service=null` → `unbindService` → **`:570 unlinkDeath`** → **`:571` 等 `link.death` ≤1 s** |

**因果链（本批要修的结构性缺陷）**：`abandon()` 在等待死亡前就 `unlinkDeath`（`:570`），同时已 `unbindService`（`:569`）。服务端被 `terminate` 的 watchdog 异步 SIGKILL 时，`binderDied` 已无注册者；`onServiceDisconnected` 只在进程意外死亡且 binding 仍在时回调，客户端已 unbind，故也不会来。`death` 的唯一完成点是 `markDead`（`:762`），而 `abandon` 之后没有路径再调它 → `withTimeoutOrNull(1000)` 超时 → `DEATH_UNCONFIRMED` + `unavailable = true`（`:572-575`）。这与设备证据完全一致：服务 16 ms 内被杀，客户端 1 s 收不到 death（KD-66 `:2585`；E-01c `:107`；测试断言 `VisualJsIsolationInstrumentedTest.kt:210`）。

> 补充：`abandon()` 中 `terminate()` 是 oneway Binder 调用；`unbindService` 也不是同步终止。`dispose()`（`:580-587`）同样先 `unlinkDeath` 但**不等 death**，用于替换未连接/陈旧 link，不承担死亡确认语义。

### 5.2 客户端 attach 超时常量与冷启动加载态处理现状

| 常量/行为 | 值/位置 | 说明 |
|---|---|---|
| `CONNECT_TIMEOUT_MILLIS` | **5_000 ms**（`:808`） | 包住 `candidate.connected.await()`（`:537-538`），即 **attach + 握手**的预算 |
| `DEATH_CONFIRM_TIMEOUT_MILLIS` | **1_000 ms**（`:809`） | `abandon()` `:571`；`close()` 诊断 `:233` |
| `HOST_PHASE_DEADLINE_MILLIS` | **2_500 ms**（`core/domain/.../VisualExecutionEngine.kt:129`） | 宿主阶段软期限；到点走 `abandon`（`:189-204`） |
| 连接超时后的状态 | `:542-548` | `candidate.state = ABANDONED` → `unbindService` → `unlinkDeath` → `link = null` → **`unavailable = true`** |
| 加载态处理 | **不存在** | 全文件 `grep "unavailable = false"` **零命中**；`unavailable` 一旦置真，`ensureConnected()` 直接返回 null（`:483`），`execute` 折 `ENGINE_CLOSED`（`:179-182`）。无 warm-up、无重试、无 loading UI、无延长首连预算 |
| 服务端配合 | `VisualJsService.kt:94-97` | `onUnbind` 立即 `killSelf("the host unbound the visual_js worker")`；客户端超时 unbind 会把 worker 一并杀掉，后续无法复用 |

**设备侧现状（证据原文）**：冷启动后立即跑，隔离进程 attach **4.6–7.5 s** > 5 s，5/5 红（`docs/research/evidence/2026-10-26-batch-23/step01d-visualjs-isolation-coldboot.md:61`、`:64`、`:72`）；沉降约 2 min 后 attach **0.6–0.9 s**，回到 1/5 红（`step01e-visualjs-isolation-postboot-settled.md:66`、`:76`）。结论：加载态与 abandon 结构性失败已被分离；但客户端没有"加载中"状态，首连失败即永久 `unavailable`。

---

## 6. quickjs-kt memoryLimit 现状

### 6.1 引擎设置值

- `js.memoryLimit = VisualExecutionBudget.MAX_HEAP_BYTES`（`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/QuickJsVisualExecutionEngine.kt:425`）。
- `MAX_HEAP_BYTES: Long = 16L * 1024 * 1024`（`core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/VisualExecutionEngine.kt:117`），即 **16 MiB**。
- 紧邻设置：`js.maxStackSize = MAX_STACK_BYTES`（256 KiB，`:426`）、`js.evaluationTimeoutMillis = cooperativeTimeoutMillis`（`:427`）。
- 每个 phase 新建 `QuickJs.create(jobDispatcher)`（`:411`），设置在 evaluate 之前（`:425`），`finally { js.close() }`（`:464-467`）。

### 6.2 `mallocSize` / `mallocLimit` 来源

| 事实 | 依据 |
|---|---|
| `js.memoryUsage` 由 quickjs-kt 暴露；引擎在"消息丢失哨兵 + 非中断类型"路径读取，发生在 `close()` 之前 | `QuickJsVisualExecutionEngine.kt:449-462`（`js.memoryUsage` 于 `:453`，`HeapSnapshot` 于 `:454-457`） |
| `MemoryUsage.mallocSize/mallocLimit` 直接来自 QuickJS 的 `JS_ComputeMemoryUsage`：该函数复制 `malloc_count`/`malloc_size`/`malloc_limit`（`quickjs.c:6928-6938`）；`JS_SetMemoryLimit` 只写 `malloc_state.malloc_limit`（`quickjs.c:2221-2224`） | R3 `docs/research/2026-10-26-quickjs-oom-semantics.md:160-166`（§2.1.4）、`:11`、`:91` |
| `mallocSize` 是运行时分配器字节计数（`usable_size + MALLOC_OVERHEAD` 记账），不是 JS 对象图大小；`js_def_malloc` 用 `malloc_size + size > malloc_limit` 拒绝 | R3 `:11`、`:67-72`、`:82-86` |
| 分类阈值：`mallocLimit > 0 && mallocSize * 100 >= mallocLimit * 90` | `core/data/.../VisualJsFailureClassification.kt:49`（语义注释 `:29`；字段 `:38-41`） |

### 6.3 KD-70 P2 探针源码行号

文件：`core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/visual/QuickJsVisualExecutionEngineInstrumentedTest.kt`。

| 项 | 行 |
|---|---|
| P2 用例 `heapExhaustionWithLostErrorMessageIsAttributedByMemoryUsage()` | `:270-293`（`engine.execute(request(HEAP_LIST_SOURCE))` 于 `:279`） |
| P2 断言：`MEMORY_LIMIT` / `attributed by memoryUsage` / `mallocLimit=16777216` / 诊断长度 | `:285-292` |
| P2 探针源码 `HEAP_LIST_SOURCE` | `:720-729`：`for (var i = 0; i < 500000; i++) { head = { next: head }; }`（有界循环、全可达、无长字符串/大 realloc，KDoc `:715-719`） |
| P1（主路径）用例 / 源码 | `:241-268`；`OOM_SOURCE` `:705-713`（单次 64 MiB `ArrayBuffer`） |
| P3/P4/P5 | `:295-317` / `:319-337` / `:339-356`；源码 `:736-745` / `:748-753` / `:756-761` |

### 6.4 R3 文档的推荐探针

R3 `docs/research/2026-10-26-quickjs-oom-semantics.md`：

- **P1（主路径，推荐）**：§5.2（`:345-361`）——`prepare` 内 `new ArrayBuffer(64 * 1024 * 1024)`；64 MiB > 16 MiB 且 < INT32_MAX，不涉字符串；失败时仍剩约 15 MiB，OOM Error 可构造（message 可读）。预期 `Rejected(MEMORY_LIMIT, "…InternalError: out of memory…")`。
- **P2（消息丢失分支，推荐）**：§5.3（`:371-394`）——50 万个小对象链表 `head = { next: head }`；每轮只分配 `sizeof(JSObject)` 量级，失败时余量连 OOM Error 都放不下 → message 为 `"null"`；预期 `memoryUsage.mallocSize` 接近 `mallocLimit`。R3 明确这是"确定性论证"（`:385-390`）。
- **P3/P4/P5 负控**：§5.4 rope 翻倍、§5.5 `throw null` 与伪造 `RangeError('out of memory')`（`:396-438`）。
- **阈值纪律**：§6.3（`:582`）要求若 P2 的 `mallocSize` 低于 14.4 MiB，先检查夹具是否意外大 realloc，**不得为变绿直接降低 90% 阈值**；§5.7（`:457`）要求 x86_64/4KB 与 arm64/16KB 各跑一次。

**与设备现状的差**：R3 的 P2 推导预期"接近上限"，但设备实测只有 `mallocSize=177216`（1.06%），说明该 AAR/ABI 上 P2 在推满堆前已因别的原因失败（E-02 `step02-quickjs-oom.md:101`、`:112`）。这正是本批 R2/T2 要查清或换夹具的点；阈值与断言未放宽。

---

## 7. 设备状态（只读查询）

`adb` / `emulator` 不在 PATH，用绝对路径（`/c/Android/Sdk/...`）。

`/c/Android/Sdk/platform-tools/adb.exe devices`（exit=0）：

```
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
| **emulator-5556** | **`ps16k_x86_64`** | **16384** | **36** | x86_64 | **16KB AVD 当前在线** |
| emulator-5562 | `audit_claude_coverage_20261024`（不在 `-list-avds`，陈旧名） | 4096 | 34 | x86_64 | 4KB 设备 |
| emulator-5584 | `kb_test_device` | 4096 | 34 | x86_64 | 4KB 设备 |

**ps16k 在线状态：是（emulator-5556）**，`sys.boot_completed=1`。页大小证据：`getconf PAGE_SIZE=16384`；`ro.boot.hardware.cpu.pagesize=16384`；`ro.product.cpu.page.size` 与 `ro.product.page_size` 仍为空（与批次 23 在 4KB 设备上的属性缺失同型）；`ro.build.version.release=16`。AVD 配置 `~/.android/avd/ps16k_x86_64.avd/config.ini`：`image.sysdir.1 = system-images\android-36\google_apis_ps16k\x86_64\`、`tag.id = page_size_16kb`、`abi.type = x86_64`。

> 与批次 23 基线（当时 ps16k **不在线**，在线三台全 4KB）相比，本轮 **ps16k 已在线**。这改善 16KB 设备轨的可执行性，但它仍是 **x86_64**，不能替代 arm64 16KB 运行时证据（KD-74 的 arm64 缺口仍在）。

本轮未启动/停止任何模拟器、未安装/运行任何 APK、未改任何设备设置。

---

## 8. 与计划 §0 不符处 / 开工前须知

1. **工作树在核对期间继续变脏（他线并发）**：首轮 666 行（200 M + 466 ??）→ 冻结 669 行（200 M + 469 ??）→ 19:42 已到 677 行（206 M + 471 ??）。冻结后新增的 6 个 M 全在 **core/database → KB/study 线**（`RoomKnowledgeBaseStore.kt`、`RoomStudyDatabase.kt`、`dao/KnowledgeTeachingMaterialDao.kt`、`port/KnowledgePort.kt`、`core/data/src/test/.../study/KnowledgeReadRecallBudgetTest.kt`、`RoomBackedStudyExperienceRepositoryTest.kt`），**未触碰本批 visualjs / core/data visual / docs 落点**（19:45 复查落点 porcelain 仍全空）。新增的未跟踪文件里，`core/data/src/test/.../study/UsageMaterialAllocationTest.kt` 位于 core/data（T2 所在模块），**不属于 visual 落点**；`docs/research/2026-10-26-library-layout-refinement.md` 是未跟踪研究文档。提交必须按路径白名单，禁止模块级 `git add`。
2. **ps16k AVD 已在线（emulator-5556，16384/API 36）**——计划 §0 未提；批次 23 基线记录当时其不在线。设备轨可直接用，但 **arm64 16KB 仍无**，KD-74 的 arm64 运行时缺口不因此关闭。
3. **abandon 缺陷没有独立 KD**：KD-66 `:2593` 明确"abandon 缺陷建议另立 KD"，但当前 KD 列表止于 KD-75，没有 KD-76。计划 T1 直接修 abandon，**追踪载体未定**（修完挂哪条 KD、是否补登记），需主会话裁定。
4. **KD-74 的关闭依据与条目文本不同步**：批次 23 记录 §8（`:413`）已有主会话"`llvm-readobj` 等价"裁定并称"其余关闭项随本节成立"，但 KD-74 条目 `:2744` 仍写"C6 未满足且本条不能关闭"。计划 T3 要做的正是回写；在回写完成前，不能把 KD-74 当已关闭。
5. **批次 23 记录 §8 有两个同名节**：第一块（`:398`）仍写"未提交、未推送"，第二块（`:406`）才是实跑收口（提交 `f8a362ee`、推送完成）。引用时以第二块为准。
6. **origin/main 未 fetch**：本地引用与 HEAD 相同（`5bc8bdf8`），远端实时 hash **UNVERIFIED**。
7. **"落点干净"是文件/目录级，不是模块级**：`core/data` 整体 10 M + 2 ??、`core/domain` 4 M + 1 lock、app 23 M；T2 只能改 `core/data/.../visual/` 与 `docs`，不得把模块内他线文件带入提交。
8. **热区清单仍漏列实脏面**：core/database 8 M（冻结时；19:42 已增至 12 M，KB 线在写）、feature/profile 3 M、docs 36 M/18 ??、tools 4 M/12 ??、`core/markdown/` 未跟踪、app 实为 23 M（计划只点名 Root/SecondaryScreens）。
9. **DB/请求 schema 无变化**：DB v69、请求 schema 18，两者权威文件均干净；本批 T1/T2 不需要 DB 升级或 schema 变更。
10. **计划 §0 其余断言逐项属实，但"13 个未跟踪锁"需加脚注**：git 可见 13 条，盘上实为 14 个（`core/markdown/gradle.lockfile` 被未跟踪目录折叠，§4.3）；HEAD=`5bc8bdf8`（= `f8a362ee` + 纯文档 10 行，父提交已 `git rev-parse 5bc8bdf8^` 复核）；T1 4/5 与 abandon `unlinkDeath` 位置（`:570-571`，位于 `:564-578`）属实；冷启动 4.6–7.5 s 与沉降 0.6–0.9 s 有 E-01d/E-01e 回链；KD-70 P2 `mallocSize=177216` 有 E-02 回链；`benchmark` 无锁；KD-74 证据 15 个 `.md` 属实；KD-75 与 core/export 5 M 属实；热区（feature 四模块、core/ui、Root、SecondaryScreens、settings.gradle.kts、core/export）全部在飞。

**UNVERIFIED / 本轮未做**：未跑 Gradle 任何任务（单测门/强门/assemble 均未跑）；未跑 JVM/androidTest/设备用例；未 `git fetch`；未编译 AIDL；未复跑隔离 5 例、KD-70 P2、R8 冒烟、16KB 抽验、备份抽验；未验证 KD-74 裁定的实际证据内容（只读条目与记录文本）。**本轮实际执行的检查**：`git` 只读查询/计数、`grep`/文件读取、`adb devices` 与 4 台设备的 `getconf`/`getprop`/`emu avd name` 只读查询。

---

## 附录 A：本轮实际执行的只读命令（可复跑）

```bash
git rev-parse HEAD; git rev-parse origin/main
git rev-list --left-right --count origin/main...HEAD     # 0 0
git log --oneline -3; git show --stat --oneline 5bc8bdf8
git status --porcelain | wc -l                            # 首轮 666 → 冻结 669（19:34:43）
git status --porcelain | grep -c '^ M'                    # 200
git status --porcelain | grep -c '^??'                    # 首轮 466 → 冻结 469
git diff --cached --name-only | wc -l                     # 0
git status --porcelain | grep '^ M' | sed 's/^ M //' | awk -F/ '{print $1"/"$2}' | sort | uniq -c
git status --porcelain | grep '^??' | sed 's/^?? //' | cut -d/ -f1 | sort | uniq -c
git status --porcelain -- <每个落点>                       # feature/core/docs/app/settings 等
git ls-files '*gradle.lockfile*'                          # 0
git status --porcelain | grep -i lockfile                 # 13 条（+ core/markdown/gradle.lockfile 被未跟踪目录折叠 → 盘上 14 个）
ls -la core/markdown/                                     # gradle.lockfile 6,470 B 在盘
for d in app core/model core/domain core/database core/data core/export core/ui core/markdown feature/{capture,review,tutor,library,profile} benchmark; do test -f "$d/gradle.lockfile" && echo "LOCK $d"; done
grep -n "STUDY_DATABASE_VERSION" core/database/.../StudyDatabase.kt   # :134 = 69
ls core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/  # 69 个，最高 69.json
git ls-files '*70.json' '*71.json'                        # 空
grep -n "TUTOR_VISUAL_CONTEXT_SCHEMA_VERSION\|CURRENT_SCHEMA_VERSION =" core/model/.../ModelTasks.kt  # :546-547 = 18
grep -n "PROTOCOL_VERSION" app/src/main/kotlin/.../visualjs/VisualJsProtocol.kt  # :31 = 1
grep -n "death\|markDead\|unlinkDeath\|abandon\|CONNECT_TIMEOUT\|DEATH_CONFIRM" app/src/main/kotlin/.../visualjs/VisualJsClientEngine.kt
wc -l app/src/main/kotlin/.../visualjs/*.kt app/src/main/aidl/.../visualjs/*.aidl
grep -n "unavailable = false" app/src/main/kotlin/.../visualjs/VisualJsClientEngine.kt   # 零命中
grep -n "memoryLimit\|memoryUsage\|mallocSize\|mallocLimit" core/data/.../QuickJsVisualExecutionEngine.kt core/data/.../VisualJsFailureClassification.kt
grep -n "HEAP_LIST_SOURCE\|OOM_SOURCE\|STRING_ROPE_SOURCE\|THROW_NULL_SOURCE\|FORGED_OOM_SOURCE" core/data/src/androidTest/.../QuickJsVisualExecutionEngineInstrumentedTest.kt
grep -n "malloc_limit\|malloc_size\|JS_ComputeMemoryUsage\|P2：\|P1：" docs/research/2026-10-26-quickjs-oom-semantics.md
grep -n "^## KD-6[6-9]\|^## KD-7[0-9]" docs/known-defects.md
grep -n "^## \|^## 8" docs/research/2026-10-26-batch-23-record.md
git ls-files docs/research/evidence/ | wc -l              # 15
git check-ignore -v docs/research/evidence/2026-10-26-batch-23/.mimosa/hook-status/*.json  # .gitignore:29
"/c/Android/Sdk/platform-tools/adb.exe" devices
"/c/Android/Sdk/emulator/emulator.exe" -list-avds
for s in emulator-5554 emulator-5556 emulator-5562 emulator-5584; do \
  adb -s $s emu avd name; adb -s $s shell getconf PAGE_SIZE; \
  adb -s $s shell getprop ro.build.version.sdk; adb -s $s shell getprop ro.product.cpu.abi; done
"/c/Android/Sdk/platform-tools/adb.exe" -s emulator-5556 shell getprop sys.boot_completed          # 1
"/c/Android/Sdk/platform-tools/adb.exe" -s emulator-5556 shell getprop ro.boot.hardware.cpu.pagesize  # 16384
grep -E "image.sysdir|tag.id|abi.type" ~/.android/avd/ps16k_x86_64.avd/config.ini
```
