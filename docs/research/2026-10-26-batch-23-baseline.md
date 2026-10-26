# 批次 23 基线核对（2026-10-26，基线核对员）

只读核对；**未运行 Gradle、未编译、未执行任何测试或设备用例、未做任何 git 写操作**（本文件是唯一写盘产物）。所有行号、计数、状态均为本轮实读实测，命令见附录 A。对照计划：`.zcode/plans/plan-2026-10-26-batch-23-device-gates-lockfiles.md`（下称"计划"）§0。

"干净"定义：`git status --porcelain -- <path>` 无输出（不代表内容无缺陷）。行号 = 本工作树当前物理行号（HEAD `f0a145f8` + 193 个他线 M，他线再改同一文件时行号会漂）。

---

## 1. HEAD / 工作树 / DB schema / 请求 schema

| 项 | 实测 | 证据（命令/路径行号） |
|---|---|---|
| HEAD | `f0a145f850931887385c4bf2e1f798a0e8f2e391`（main） | `git rev-parse HEAD` |
| origin/main | `f0a145f850931887385c4bf2e1f798a0e8f2e391`（**与 HEAD 相同**） | `git rev-parse origin/main`；**本轮未 `git fetch`**，这是本地远端跟踪引用，远端实时 hash 未核（UNVERIFIED） |
| HEAD 构成 | `f0a145f8` 是**纯文档提交**（`docs/research/2026-10-26-batch-22-record.md` +10 行）；父提交 `69e5f591`（批次 22 主提交，2026-10-26 13:11 +0800） | `git log --oneline -3`、`git show --stat f0a145f8` |
| 工作树 | **193 M + 449 ??；staged = 0，`MM` = 0** | `git status --porcelain` → `grep -c '^ M'`=193、`grep -c '^??'`=449、`git diff --cached --name-only \| wc -l`=0 |
| DB schema | `STUDY_DATABASE_VERSION = 69`（`core/database/.../StudyDatabase.kt:134`；`version = STUDY_DATABASE_VERSION` 于 `:233`）；导出目录共 **69 个 schema 文件**，最高 **`69.json`**；**`70.json` 不存在**（`git ls-files '*70.json'` 空）；`StudyDatabase.kt` 与 `core/database/schemas/` 均 **git 干净** | `grep -n` + `ls` + `git status --porcelain -- core/database/schemas/`（空）+ `git ls-files '*70.json'`（空） |
| 请求 schema | `TUTOR_VISUAL_CONTEXT_SCHEMA_VERSION = 18`、`CURRENT_SCHEMA_VERSION = TUTOR_VISUAL_CONTEXT_SCHEMA_VERSION`（`core/model/.../ModelTasks.kt:546-547`）；`ModelTasks.kt` **git 干净** | `grep -n` + `git status --porcelain -- core/model/.../ModelTasks.kt`（空） |

工作树按顶层归组（实数）：

- **M**：feature 89（tutor 36 / library 26 / capture 13 / review 11 / profile 3）、core 38（core/data 10 / core/ui 11 / core/database 8 / core/export 5 / core/domain 4）、docs 37、app 21、tools 4、仓库根 2（`.gitignore`、`dynamic_gui_architecture_plan.md`）、`settings.gradle.kts` 1、knowledge-production 1。
- **??**：feature 27（tutor 20 / library 7）、core 24（core/ui 23 + `core/markdown/` 1）、docs 15（research 12 / superpowers 3）、tools 12（kb_coverage 11 / kb_build 1）、knowledge-production 11、app 4；其余约 356 条为仓库根审计/临时散点（`.agent_*`、`_tmp*`、`tmp*`、`out/`、`output/`、`nul` 等）。

对比批次 22 基线（208 M + 453 ??）：M 净减 15，?? 净减 4；`settings.gradle.kts`、`core/database`、`core/export`、`core/domain`、`docs/current` 的脏面与批次 22 基线同形。

---

## 2. 热区快照 vs HOT_ZONES（计划 §0 第 2 条）

计划全文无 `HOT_ZONES` 标题（与批次 22 相同）；按计划 §0 的"热区不变"清单逐项对照：

| 计划 §0 热区 | 当前实测（M / ??） | 判定 |
|---|---|---|
| feature 四模块 | tutor **36/20**、library **26/7**、capture **13/0**、review **11/0** | ✓ 全部在飞 |
| core/ui | **11 M + 23 ??** | ✓ |
| Root | `SmartMistakeBookRoot.kt` M、`SmartMistakeBookDestinations.kt` M | ✓ |
| SecondaryScreens | `SecondaryScreens.kt` M | ✓ |
| settings.gradle.kts | **M**（含 `:core:markdown` 新模块，见 §8） | ✓ |
| core/domain | **4 M** | ✓（全路径见 §2.1） |
| core/data | **10 M** | ✓（计划口径是"G5/study 文件"，模块本身脏；全路径见 §2.2） |

**计划热区未列、当前实脏（新增冲突面）**：

- **core/database 8 M**：`RoomBatchImportStore.kt`、`TutorConversationDatabaseContract.kt`、`dao/BatchImportDao.kt`、`dao/TutorConversationDao.kt`、`port/CleanRedrawTaskPort.kt`、`LibraryFtsCountPathInstrumentedTest.kt`、`LibraryFtsCountPathScale50kInstrumentedTest.kt`、`LibraryFtsRankingPathInstrumentedTest.kt`。
- **core/export 5 M**、**feature/profile 3 M**；docs 37 M + 18 ??；tools 4 M + 12 ??；`core/markdown/` 未跟踪（新 Gradle 模块，已经被 `settings.gradle.kts` include）。
- app 实为 **21 M**（计划只点名 Root/SecondaryScreens）：main 8（`LearningMasteryScreen.kt`、`ReviewReminderCoordinator.kt`、`ReviewReminderScreen.kt`、`SchedulingSettingsScreen.kt`、`SecondaryScreens.kt`、`SmartMistakeBookDestinations.kt`、`SmartMistakeBookRoot.kt`、`StartupStateBanner.kt`）+ androidTest 10 + app test 3。

### 2.1 core/domain 开工即脏 M 全路径（4 个，全量点名）

1. `core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/BatchImportRepository.kt`
2. `core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/MistakeDetailRepository.kt`
3. `core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/SchedulingEvaluation.kt`
4. `core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/TutorJudgedReview.kt`

core/domain 无其他 M、无未跟踪。批次 23 落点 `AdaptiveSelectionConsumption.kt`、`AnswerKeyVerificationUpgrade.kt`、`AdaptiveQuestionSelector.kt` **逐个 porcelain 实测干净**。

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

core/data 无未跟踪文件。批次 23 落点 `KnowledgeQuizItemVerificationUpgrader.kt`、`AdaptiveSelectionAssembler.kt`、`KnowledgeQuizItemStore.kt` **逐个 porcelain 实测干净**。

> 与批次 22 基线（core/data 23 M，含 `DenseRecallAssembly.kt` + 资源/夹具 11）不同：本日 core/data 只剩 10 M；`DenseRecallAssembly.kt` 现已为已跟踪且干净（`git ls-files` 命中、porcelain 空），资源/夹具不再出现在脏表。**排提交时仍须显式按路径排除上面 10 个文件。**

---

## 3. 批次 23 落点脏态逐个核对

`git status --porcelain -- <path>` 逐个实测，**全部空（干净）**：

| 落点 | 状态 | 本轮实读补充事实 |
|---|---|---|
| `build.gradle.kts`（根） | **干净** | 62 行；`dependencyLocking` 见 §5；stdlib 下压见 §5 |
| `gradle/libs.versions.toml` | **干净** | `kotlin = "2.3.21"` `:3`；`quickjs-kt-android = "1.0.15"` `:51`；`quickjs-kotlin-stdlib = "2.4.10"` `:61` |
| `app/build.gradle.kts` | **干净** | KD-69 修复已在 HEAD：benchmark `:58`（proguardFiles `:67`）、internal `:69`（`:75`）、release（proguardFiles `:84`）三处均有 `proguardFiles(...)`；minify 三个变体仍为 true |
| core/database `SeedAssessmentPort` | **干净** | 文件是 `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/port/LearningProjectionPort.kt:39-69`；详见 §6 |
| core/database DAO 面 | **干净** | `dao/LearningDao.kt`、`entity/LearningEntities.kt`、`RoomStudyDatabase.kt`、`DatabaseContract.kt`、`StudyDatabasePort.kt` 逐个 porcelain 空；详见 §6 |
| core/domain `AdaptiveSelectionConsumption.kt` | **干净** | 5424 B；`AnswerKeyVerificationUpgrade.kt` 8650 B、`AdaptiveQuestionSelector.kt` 15226 B 同样干净 |
| core/domain `AnswerKeyVerificationUpgrade.kt` | **干净** | 同上；批次 22 T4 已随 `69e5f591` 入库 |
| core/data `KnowledgeQuizItemVerificationUpgrader.kt` | **干净** | 单列写端口 `AssessmentItemVerificationStatusWriter` 在 `:33-43`（CAS 方法 `:38-42`） |
| `docs/known-defects.md` | **干净** | 2701 行；KD-66…KD-74 全在（§4） |
| `docs/current/visual-generation-contract.md` | **干净** | 203 行；章节 §1…§10（`grep '^## '`），§5 为"通用场景与显示列表"` :92` |

**模块级提醒**：`core/database` 整体仍有 8 M（§2 全路径），`core/domain` 4 M、`core/data` 10 M；"落点干净"只对上面点名的文件成立，不能读成"模块干净"。

**批次 22 收口声明 spot-check（只读）**：`app/src/main/AndroidManifest.xml:68-72` 有 `<service ... android:isolatedProcess="true" android:process=":visual_js" />`；AIDL 两文件在盘且为正确接口（`IVisualJsService.aidl` 1841 B、`IVisualJsCallback.aidl` 1847 B，后者是 `oneway` 回调）；服务端 `VisualJsService.kt:195-198` 与客户端 `VisualJsClientEngine.kt:439` 各有一处 `dup()`；隔离断言在 `VisualJsService.kt:112-114` 与仪器化用例 `VisualJsIsolationInstrumentedTest.kt:88`。**"AIDL 有效"（可编译）本轮未复跑，UNVERIFIED。**

---

## 4. KD-66…KD-74 条目原文与当前状态

文件：`docs/known-defects.md`（git 干净）。**九条全部 `open`**。

| 条目 | 行 | 标题（原文） | 当前状态 |
|---|---|---|---|
| KD-66 | `:2573-2590` | "KD-66 (open, 2026-10-26 登记（批次 21 T3/复核发现 #1/#13/#21；高）) · QuickJS issue #240 使引擎硬期限不能返回：`execute()` 永不返回、`close()` 卡死、`inFlight` 不复位" | open；Progress `:2579-2586`：T1 进程层代码已落、无设备证据、JS 不可用/不广告 |
| KD-67 | `:2592-2608` | "KD-67 (open, 2026-10-26 登记（批次 21 T2/复核发现 #15；中）) · G5-A 生产候选全部 `contentVerified=false`：即使接线也到不了 ASK，CLARIFY_OR_CONFIRM 无消费者" | open；Progress `:2598-2604`：核心侧三块已落；"仍未闭合"五点：生产调用点、core/database 单列 UPDATE adapter、feature 消费者+UI、设备/集成用例、source verifier 生产实现 |
| KD-68 | `:2610-2620` | "KD-68 (open, 2026-10-26 登记（批次 21 T1/复核发现 #16；中）) · 新题积压 UI 承诺不可调度项：用 `preview` 算术而非 `previewBacklog`，`unschedulableCount` 无生产消费者" | open；`ReviewRoute.kt:529`/`:558` 仍为旧口径 |
| KD-69 | `:2622-2634` | "KD-69 (open, 2026-10-26 登记（批次 21 设备轨步五/复核发现 #12；低）) · `internal`/`benchmark` buildType minify=true 但缺项目 proguardFiles：R8 strict full mode 剥掉生成类的无参构造器（WorkDatabase_Impl / ML Kit registrar），启动 FATAL" | open；修复已落（`:2630`），构建/设备判据未验证 |
| KD-70 | `:2636-2652` | "KD-70 (open, 2026-10-26 登记（批次 21 T3/复核发现 #6/#21；中）) · QuickJS 内存上限路径在 x86_64 未成立：设备实际 `INVALID_PROGRAM`，契约要求的 `MEMORY_LIMIT` 未达" | open；设备全量 20 例仅 P2 红，P2 诊断未落盘 |
| KD-71 | `:2654-2669` | "KD-71 (open, 2026-10-26 登记（批次 21 T3 复核发现 #22；低）) · `QuickJsVisualExecutionEngine` close/execute 竞态：closed 检查与 `currentTransaction.set` 之间的窗口" | open；修复已落（`:2662-2664`），设备未执行 |
| KD-72 | `:2671-2681` | "KD-72 (open, 2026-10-26 复核返修 #5/#10 登记；中) · dependency locking 已配置但仓库无任何锁文件：默认 LockMode 下锁门未生效" | open；见 §5 |
| KD-73 | `:2683-2691` | "KD-73 (open, 2026-10-26 复核返修 #11 登记；中) · 运行时 kotlin-stdlib 强制降回 2.3.21：字节码/API 兼容证据只覆盖 quickjs AAR，不是全解析图" | open；配置在盘（§5），全图证据未落 |
| KD-74 | `:2693-2701` | "KD-74 (open, 2026-10-26 复核返修 #6 登记；中) · 批次 22 设备结论（16KB 3/3、R8 三变体冒烟、备份/迁移）无日志落盘" | open；`build/device-track-batch22` 不存在（只有 `build/device-track-batch21`） |

### 4.1 关闭判据逐字抄录

**KD-72（`docs/known-defects.md:2681`）**：

> **Reopen condition.** 仓库内每个 subproject 都有 `gradle.lockfile`，且一次负控（人为抬高某个传递依赖版本）在解析期被锁状态判红后关闭。

其 Fix direction（`:2679`）：

> **Fix direction.** 由允许跑 Gradle 的门禁轨执行根目录注释里的 14 个 `--write-locks` 目标，把生成的 `gradle.lockfile` 入库；随后再加一个"锁文件存在"检查（或 `LockMode.STRICT` 防新模块漏锁）。在锁文件入库前，不得把 T3 写成"依赖锁定已完成"。

**KD-73（`:2691`）**：

> **Reopen condition.** 完整解析图扫描 unresolved=0 且设备/R8 冒烟无 stdlib 归因崩溃，或升级到 2.4.10 的构建/设备证据落盘后关闭。

**KD-74（`:2701`）**：

> **Reopen condition.** 上述四类结论各有可复跑命令与落盘结果，且 T1 修复后的设备复跑结果一并落盘后关闭。

KD-74 的"四类结论"在 Fix direction（`:2699`）逐项为：每条命令的 `am instrument` summary/`INSTRUMENTATION_CODE`、logcat 的 FATAL/ANR/NoSuchMethod/NoClassDef 全量 grep、16KB 的 `getprop ro.product.cpu.page.size` 与产物 `readelf -l` 页对齐、备份/迁移用例输出；另记 APK/mapping sha256。

### 4.2 其余九条关闭判据（原文，便于对照）

- KD-66 `:2590`："> **Reopen condition.** 4E.2 进程隔离落地或上游修复版升级后，以"issue #240 形状在 hard+500ms 内返回拒绝、且后续请求可用"的设备用例关闭；在此之前不得把 T3 标成运行时完成。（T1 的仪器化用例现以 ≤6 s 判死 worker，弱于本条件；关闭时仍以 hard+500 ms 的用例为准。）"
- KD-67 `:2608`："> **Reopen condition.** 出现真实生产候选可被标为 VERIFIED/USER_ASSERTED 的写路径且 feature/review 消费 CLARIFY 决策后，以"生产候选能进 ASK、未核验候选仍 CLARIFY"的设备/集成用例关闭。（批次 22 的 JVM 进展不改变本条件。）"
- KD-68 `:2620`："> **Reopen condition.** `ReviewIntakeBacklogPreviewInstrumentedTest` 覆盖"全超预算项显示为不可调度/专项会话"且真实计划不再承诺其日引入后关闭。"
- KD-69 `:2634`："> **Reopen condition.** `internal` minified 构建装机冷启动（含二次冷启动）无 `FATAL EXCEPTION`/`StartupException`/`NoSuchMethodException`/`ClassNotFoundException`/`NoClassDefFoundError`，且**一次中文 OCR** 无 `MlKitException`/`NoSuchMethodException` 后关闭；release 冒烟不得回退。"
- KD-70 `:2652`："> **Reopen condition.** 设备用例以 `MEMORY_LIMIT`（或经裁定的等价判据）转绿，且 `memoryUsage`/诊断证据落盘后关闭。"
- KD-71 `:2669`："> **Reopen condition.** 新增并发用例在设备上证明 close 后不再有成功执行、且 in-flight 事务被强弃后关闭（`closeDuringAnInFlightTransactionWaitsAndRejectsIt` + 压测 32 轮全绿，日志落盘）后关闭。"

**KD 正文行号漂移（本轮实测，重要）**：KD-72 Symptom 写"根 `build.gradle.kts:33-36` 对全部 subprojects 声明 `dependencyLocking`"（`:2673`），KD-73 写"`build.gradle.kts:47-57` 用 `eachDependency + useVersion`"（`:2685`）。当前树实际是 **`:37-40`**（`subprojects {` `:37`、`dependencyLocking {` `:38`、`lockAllConfigurations()` `:39`）与 **`:51-61`**（`configurations.configureEach` `:51`、`eachDependency` `:52`、`useVersion` `:54`）。批次 22 在文件头加注释导致 +4 行漂移；语义不变。

---

## 5. 锁文件现状 / dependencyLocking / pin gate

### 5.1 锁文件：**全仓为零**

三路只读实测，全部无输出（exit=0）：

1. `git ls-files '*gradle.lockfile*'` → 空（无已跟踪锁文件）。
2. `find . \( -name .git -o -name .worktrees -o -name .artifacts -o -name .jez -o -name build -o -name .gradle \) -prune -o -name '*gradle.lockfile*' -print` → **0 命中**（全仓，剪掉构建/历史副本）。
3. `find . -maxdepth 4 -name '*gradle.lockfile*' ...` → 0 命中；`git status --porcelain | grep -i lockfile` → 空（连未跟踪锁文件也没有）。

附带：`gradle/dependency-locks/` 不存在、`gradle/verification-metadata.xml` 不存在（`ls` 均 `No such file or directory`）。

结论：**KD-72 现状属实**——`dependencyLocking` 只有声明，没有任何锁状态，LENIENT 下锁门未生效。

### 5.2 根 `build.gradle.kts` 行号（62 行）

| 内容 | 行 |
|---|---|
| KD-72 复核状态注释（"仓库内仍没有任何 gradle.lockfile…LENIENT 模式下锁门未生效"） | `:21-23` |
| 14 个 `--write-locks` 目标命令（注释） | `:24-29`（`:app`、`:core:model`、`:core:domain`、`:core:database`、`:core:data`、`:core:export`、`:core:ui`、`:core:markdown`、`:feature:capture`、`:feature:review`、`:feature:tutor`、`:feature:library`、`:feature:profile`、`:benchmark`） |
| `val kotlinStdlibVersion = libs.versions.kotlin.get()` | `:35` |
| `subprojects {` / `dependencyLocking {` / `lockAllConfigurations()` / `}` | **`:37 / :38 / :39 / :40`** |
| stdlib 下压：`configurations.configureEach` / `resolutionStrategy.eachDependency` / `useVersion(kotlinStdlibVersion)` / `because(...)` | `:51 / :52 / :54 / :55-58` |

`settings.gradle.kts` 的 `include(...)` 块（`:19-34`）恰好 **14 个 subproject**（`:app`、`:core:model`、`:core:domain`、`:core:database`、`:core:data`、`:core:export`、`:core:ui`、`:core:markdown`、`:feature:capture`、`:feature:review`、`:feature:tutor`、`:feature:library`、`:feature:profile`、`:benchmark`），与根注释的 14 个目标**数量一致**。

### 5.3 pin gate 脚本 `tools/ci/check_dependency_pins.py`（277 行）校验项与行号

| 校验项 | 行 |
|---|---|
| quickjs 三构件 sha256 常量（AAR / POM / .module） | `:48-50` |
| 缓存内查找三构件 + 缺失即报错 | `:83-99`、`:214-225` |
| 三构件 sha256 比对 | `:226-234`（`checksum_errors` 定义 `:73-80`） |
| 上游事实：POM 声明 `kotlin-stdlib` 版本 == 目录 `quickjs-kotlin-stdlib` | `:172-178`（POM 解析 `:102-107`、`:236-238`） |
| 本项目决定 ①：解析出的 quickjs 版本必须包含目录钉版 | `:180-184` |
| 本项目决定 ②：解析出的 `kotlin-stdlib` 必须 == 编译期 `kotlin`（默认 2.3.21） | `:186-191` |
| XML 产物 DOCTYPE/ENTITY 拒绝（防被污染产物） | `:54`、`:133-138` |
| 解析图产物选择（最新且含 quickjs 的 `*artifact-dependencies.xml`） | `:110-130` |
| 负控开关 `--expect-kotlin-runtime` | `:198-204`、`:210` |

**本轮实跑**：`python tools/ci/check_dependency_pins.py` → **`PY_EXIT=0`**，末行 `OK: quickjs checksums, upstream POM requirement and resolved runtime pins match`；打印 `resolved: quickjs=['1.0.15'] kotlin-stdlib=['2.3.21']`。解析图来源仍是 `app/build/intermediates/incremental/lintVitalAnalyzeStrictOfflineRelease/strictOfflineRelease-artifact-dependencies.xml`（**AIDL 修复前的旧构建产物**，本轮未跑 Gradle、未刷新解析图）——这条只证明钉版门，不证明锁门（与 KD-72 Evidence 一致）。

---

## 6. SeedAssessmentPort 现状（core/database，读源码）

### 6.1 接口方法

文件：`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/port/LearningProjectionPort.kt`。

`interface SeedAssessmentPort`（`:39-69`）当前 **5 个方法**：

| 方法 | 行 | 语义 |
|---|---|---|
| `suspend fun saveAssessmentItemSnapshot(item: AssessmentItemSnapshotSeedRecord)` | `:40` | 写入呈现快照；不可变闸（首见即真） |
| `suspend fun findAssessmentItemSnapshot(assessmentItemSnapshotId: String): AssessmentItemSnapshotSeedRecord?` | `:48` | 按 id 读回；无行 = null |
| `suspend fun overwriteAssessmentItemSnapshot(item: AssessmentItemSnapshotSeedRecord)` | `:64` | **KD-41 损坏行修复口**，只对"既有行解不开"用 |
| `suspend fun saveAssessmentEvidenceSnapshot(snapshot: AssessmentEvidenceSnapshot)` | `:66` | 证据快照 |
| `suspend fun appendAssessmentEvent(event: AssessmentEventSeedRecord)` | `:68` | 追加事件 |

**没有任何"只改 verification_status"或 CAS 的方法**（`grep -rn "verificationStatus" core/database/.../port/` → **零命中**；`grep -rn "UPDATE assessment_item_snapshot" core/database/src/main` → 零命中）。

`StudyDatabasePort` 在 `StudyDatabasePort.kt:111` 直接组装 `SeedAssessmentPort`（与 `AttemptWritePort, OrganizationWritePort, LearningProjectionPort...` 同列）。

### 6.2 `assessment_item_snapshot.verification_status` 列

| 事实 | 位置 |
|---|---|
| 实体列 `@ColumnInfo(name = "verification_status") val verificationStatus: String` | `entity/LearningEntities.kt:45-46`（实体 `:27-61`，主键 `assessment_item_snapshot_id`） |
| 领域记录字段 `verificationStatus: String` | `StudyDatabaseRecords.kt:569`（`AssessmentItemSnapshotSeedRecord` `:560-577`） |
| 导出 schema `69.json`：`verification_status` **TEXT / notNull=true / 无 defaultValue** | `core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/69.json`（本轮用 Python 解析实体表逐列读出） |
| 允许值集合 `VERIFIED` / `USER_ASSERTED` / `UNKNOWN` | `DatabaseContract.kt:90-94` |
| 写入校验 `known(item.verificationStatus, "verificationStatus", verificationStatuses)` | `DatabaseContract.kt:602` |

**T2 落库半边不需要 DB 版本升级/迁移**：列在 v69 已存在且 NOT NULL，缺的只是单列 UPDATE 的 DAO/port 方法。

### 6.3 update 路径（现状 = 无单列更新）

| 路径 | 位置 | 行为 |
|---|---|---|
| 插入 | `dao/LearningDao.kt:137-138`（`@Insert(onConflict=IGNORE) insertAssessmentItem`） | 插入成功返回 true |
| 不可变闸 | `LearningDao.kt:164-169`（`saveAssessmentItem`） | 插入失败 → 读回同 id；**载荷完全相同** → false；否则抛 `ImmutablePayloadConflictException` |
| 整行覆盖 | `LearningDao.kt:171-181`（`@Update overwriteAssessmentItem`） | KD-41 损坏行修复专用；受影响行数 0 = 无行 |
| Room adapter | `RoomStudyDatabase.kt:1567-1570`（save）、`:1572-1576`（find）、`:1585-1588`（overwrite） | 覆盖口与 save 走同一道 `DatabaseContractValidator.validateAssessmentItem` |
| 目标契约（core/data 已定义） | `core/data/.../study/KnowledgeQuizItemVerificationUpgrader.kt:33-43`：`interface AssessmentItemVerificationStatusWriter { suspend fun compareAndSetVerificationStatus(id, expectedCurrentStatus, newVerificationStatus): Boolean }`（方法 `:38-42`） | 接口 KDoc `:14-32`（`:23-27` 明确要求"一条 `UPDATE assessment_item_snapshot SET verification_status = :new WHERE id = :id AND verification_status = :expected`；行不存在或状态已变 → false；不得新建行、不得整行重写"；`:29-31` 登记生产 adapter 未落） |

**结论（与 KD-67 一致）**：`AssessmentItemVerificationStatusWriter` 目前只有测试替身（`KnowledgeQuizItemVerificationUpgraderTest.kt:92`、`AdaptiveSelectionAssemblerTest.kt:710`），**生产 Room adapter 不存在**；T2 需要在 `SeedAssessmentPort` 增方法、在 DAO 增参数化单列 UPDATE（禁止字符串拼接）、在 `RoomStudyDatabase` 增 adapter，并把 writer 接到 `StudyDatabasePort`。`KnowledgeQuizItemVerificationUpgrader.kt` KDoc `:29-31` 自己写明"生产 adapter 未落、本轨只能改 core/domain 与 core/data"。

---

## 7. 设备状态（只读查询）

`adb` / `emulator` 不在 PATH，用绝对路径（`/c/Android/Sdk/...`）。

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
| emulator-5554 在线 | **是**，state=`device` | `adb devices` |
| emulator-5554 身份 | **`test_device`**，API 34，x86_64，**PAGE_SIZE=4096** | `adb -s emulator-5554 emu avd name` / `shell getconf PAGE_SIZE` / `getprop ro.build.version.sdk` / `getprop ro.product.cpu.abi` |
| emulator-5554 页大小属性 | `ro.product.cpu.page.size`、`ro.product.page_size`、`ro.boot.hardware.cpu.pagesize` **全为空** | `getprop` 三连（与 KD-74 要求的证据字段一致，当前取不到） |
| emulator-5562 | `audit_claude_coverage_20261024`（**不在** `-list-avds` 输出，陈旧名），4096，API 34，x86_64 | 同上 |
| emulator-5584 | `kb_test_device`，4096，API 34，x86_64 | 同上 |
| ps16k_x86_64 AVD 在盘 | **是**：`config.ini` → `image.sysdir.1 = system-images\android-36\google_apis_ps16k\x86_64\`、`tag.id = page_size_16kb`、`abi.type = x86_64` | `grep` `~/.android/avd/ps16k_x86_64.avd/config.ini` |
| ps16k 是否在线 | **否**：三台在线设备全部 4096（4KB），无 ps16k | 上表 |

本轮未启动/停止任何模拟器、未安装/运行任何 APK、未改任何设备设置。

---

## 8. 与计划 §0 不符处 / 需在开工前知道的偏差

1. **"干净落点"是文件级、不是模块级**。计划 §0 把 `core/database`、`core/domain`/`core/data` 的 G5 与 study 文件、`docs/**` 列为干净落点；实测这些**点名文件**确实干净，但 `core/database` 模块整体 8 M、`core/domain` 4 M、`core/data` 10 M、`docs` 37 M + 18 ??。提交/门禁必须按路径白名单，不能按模块 `git add`。
2. **`SeedAssessmentPort` 没有 T2 需要的单列 CAS 口**。计划 T2 措辞"落 core:database 的既有 SeedAssessmentPort 语义"容易被读成"复用既有方法"；实际必须**新增** port 方法 + 参数化单列 UPDATE + Room adapter（§6.3）。好消息：`verification_status` 列 v69 已存在，**无需 DB 版本升级/迁移**，`70.json` 未被占用。
3. **KD-72 / KD-73 的行号引用已漂移 4 行**：KD-72 写 `build.gradle.kts:33-36`，实际 `:37-40`；KD-73 写 `:47-57`，实际 `:51-61`。语义无误，引用时按当前行号。
4. **锁文件生成与 `:core:markdown` 他线正面相遇**：`settings.gradle.kts`（M）已 include `:core:markdown`，根注释的 14 个 `--write-locks` 目标也包含它，而 `core/markdown/` 仍是未跟踪目录。若本批生成并提交锁文件，会把未跟踪模块的锁文件一起带进来；KD-72 的"每个 subproject 都有锁文件"也会把 `:core:markdown` 计入。需先裁定该模块是否随本批入库。
5. **热区清单仍漏列实脏面**：core/database 8 M、core/export 5 M、feature/profile 3 M、docs 37 M/18 ??、tools 4 M/12 ??、`core/markdown/` 未跟踪；app 21 M（计划只点名 Root/SecondaryScreens）。
6. **`ps16k AVD 可用` ≠ 当前在线**：AVD 在盘（android-36 google_apis_ps16k），但在线三台全是 4KB；16KB 设备轨需先启动 ps16k。且三台设备上的 `ro.product.cpu.page.size` 等属性为空，KD-74 要求的 16KB 属性证据当前取不到。
7. **origin/main 未 fetch**：`git rev-parse origin/main` 与 HEAD 相同，但这是本地远端跟踪引用；远端实时 hash 本轮 **UNVERIFIED**。
8. **批次 22 的设备结论仍无落盘证据**：`build/device-track-batch22/` 不存在（只有 `build/device-track-batch21`）；计划 §0 的"T1 修复后复跑""20 例仅 P2 红""16KB 3/3""R8 三变体冒烟"均只能记为 UNVERIFIED（KD-74 未关闭）。
9. **计划 §0 其余断言逐项属实**：HEAD=f0a145f8（=69e5f591 + 纯文档尾，+10 行）；T1 隔离层代码在盘（manifest `isolatedProcess=true`、AIDL 两文件、`dup()`、Uid 断言）；无任何锁文件；stdlib 运行时钉 2.3.21 但全图证据未落；KD-66/67/68/69/71 open；热区（feature 四模块、core/ui、Root、SecondaryScreens、settings.gradle.kts）全部在飞；批次 23 点名的 9 个落点逐文件干净。

**UNVERIFIED / 本轮未做**：未跑 Gradle 任何任务（单测门/强门/assemble 均未跑）；未跑 JVM/androidTest/设备用例；未 `git fetch`；未编译 AIDL；未验证 KD-66 修复后的设备复跑、KD-70 P2 归因、16KB 装载、R8 冒烟、锁文件生成效果。**本轮唯一执行的行为检查是 pin gate 脚本**（`python tools/ci/check_dependency_pins.py`，exit=0）与只读 `adb`/`git` 查询。

---

## 附录 A：本轮实际执行的只读命令（可复跑）

```bash
git rev-parse HEAD; git rev-parse origin/main
git log --oneline -3; git show --stat f0a145f8
git status --porcelain | grep -c '^ M'        # 193
git status --porcelain | grep -c '^??'        # 449
git diff --cached --name-only | wc -l         # 0
git status --porcelain | grep '^ M' | sed 's/^ M //' | awk -F/ '{print $1"/"$2}' | sort | uniq -c
git status --porcelain | grep '^??' | sed 's/^?? //' | cut -d/ -f1 | sort | uniq -c
git status --porcelain -- <每个落点>
git ls-files '*gradle.lockfile*'
find . \( -name .git -o -name .worktrees -o -name .artifacts -o -name .jez -o -name build -o -name .gradle \) -prune -o -name '*gradle.lockfile*' -print
find . -maxdepth 4 -name '*gradle.lockfile*' -not -path './.git/*' -not -path './.worktrees/*'
ls -d gradle/dependency-locks gradle/verification-metadata.xml
grep -n "STUDY_DATABASE_VERSION" core/database/.../StudyDatabase.kt
ls core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/   # 69 个文件，最高 69.json
git ls-files '*70.json'
grep -rn "TUTOR_VISUAL_CONTEXT_SCHEMA_VERSION\|CURRENT_SCHEMA_VERSION =" core/model/.../ModelTasks.kt
grep -n "dependencyLocking\|lockAllConfigurations\|useVersion" build.gradle.kts
grep -n "^include" -A 15 settings.gradle.kts
python tools/ci/check_dependency_pins.py        # exit 0
grep -rn "SeedAssessment\|verification_status\|UPDATE assessment_item_snapshot" core/database/src/main --include=*.kt
python -c "<解析 69.json 的 assessment_item_snapshot 列定义>"
grep -rn "AssessmentItemVerificationStatusWriter\|compareAndSetVerificationStatus" core/data --include=*.kt
grep -n "AndroidManifest service/isolatedProcess"; ls app/src/main/aidl/.../visualjs/; grep -rn "dup()" app/src/main/kotlin/.../visualjs/
"/c/Android/Sdk/platform-tools/adb.exe" devices
"/c/Android/Sdk/emulator/emulator.exe" -list-avds
"/c/Android/Sdk/platform-tools/adb.exe" -s <serial> emu avd name
"/c/Android/Sdk/platform-tools/adb.exe" -s <serial> shell getconf PAGE_SIZE
"/c/Android/Sdk/platform-tools/adb.exe" -s <serial> shell getprop {ro.product.cpu.page.size,ro.product.page_size,ro.boot.hardware.cpu.pagesize,ro.build.version.sdk,ro.product.cpu.abi}
```
