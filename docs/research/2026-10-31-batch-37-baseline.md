# 批次 37 基线核对（2026-10-31）

> 核对人：基线核对工程师（子代理，只读核对：零代码改动、零 Gradle、零设备写）。
> 方法：全部结论来自本会话实读文件与实跑命令，命令与关键输出随条记录。
> 无当前证据的结论一律标 **UNVERIFIED**。
> 对照计划：`.zcode/plans/plan-2026-10-31-batch-37-v01-isolated-r04-k6.md` §0/§1（下称计划 §0/§1）。

## 0. 一句话结论

计划 §0 的 V-01（4 例落 core/data、注入进程内引擎、2/4 红归因夹具）、A-10（3 例 1 红归因 QUEUED 前置）、R-04（K1–K4 绿/K5 半边/K6 未执行）、KD-82/77/73 三锚点、DB schema 71 **主干事实与现树相符**。**已过时/不符有两处**：① **HEAD 已不是 §0 的 `1912180a`**——kb 线批次 26–29 四个提交（`a7d41f45`/`d1bacec5`/`b1d59757`/`5ee1f90f`）已叠加在其上，现 HEAD `5ee1f90f`（`1912180a` 是 HEAD 的祖先，非回退）；② **"清单 A-10/A-11/A-12/V-01 行未刷新"已失实**——v3 补记 10（批次 36 记录员收口，2026-10-31，随 `1912180a` 在树）已刷新 A-10/A-11/A-12/V-01/R-04 五行（行内注记追加、0 项转通过）。另有两处**计划内部口径差**登记：§1.5 "五件套"证据盘上实为 **6 文件**（`00–05`）；§1.4 只写 program 的 16384、未提 imagePrompt 半边（KD-82 处方是两字段 1024/16384）。

## 一、HEAD、origin/main、工作树、DB schema

| 项 | 结果 | 证据（本会话实跑） |
|---|---|---|
| HEAD | `5ee1f90f7e2411a6c1865890f6bf5da21f2b57aa`（`git rev-parse HEAD`）；`git log -1` = "feat(kb): 批次29——F6 导入路径优化…+ 文书"（2026-10-31 14:08:06 +0800） | 与计划 §0 "HEAD `1912180a`" **不符**：`git log --oneline -20` 实证 `1912180a`（批次 36 台账）之上叠了 kb 线四提交（`a7d41f45` 批次 26 → `5ee1f90f` 批次 29）；`git merge-base --is-ancestor 1912180a HEAD` exit=0——`1912180a` 是 HEAD 的祖先，历史无回退，只是 **kb 线在批次 36 收口后合入** |
| origin/main（本地引用） | `git rev-parse origin/main` = 同上逐字节相同；`git rev-list --left-right --count origin/main...HEAD` = `0 0` | 本地视角无差 |
| fetch 新鲜度 | `.git/FETCH_HEAD` mtime = **Oct 27 00:39**（`ls -l` 实读）——仍未 fetch；远端是否已有新提交 **UNVERIFIED**（本轨只读，未 fetch） | 沿批次 35/36 基线同款保留项 |
| 工作树计数 | **688 条**：`212 M` + `476 ??`（`git status --porcelain \| wc -l` = 688；awk 按状态列计数）。比批次 36 基线（686 = 212 M + 474 ??）多 2 条 ?? | 实跑。`git diff --stat` = 212 files changed, **5107 insertions(+), 2678 deletions(-)**——M 是真实内容改动非换行噪音（抽验 `ExportPdfWorker.kt` diff 为文案改写）。新增 ?? 之一已指认：`core/data/src/test/.../visual/FigureGenerationLocalBudgetBoundaryTest.kt`（未跟踪；A-10 的 JVM 边界 5 例，E-05 读值的载体文件**尚未入仓**）；另一条未逐条追认（UNVERIFIED） |
| DB schema | **71**：`core/database/.../StudyDatabase.kt:139` `internal const val STUDY_DATABASE_VERSION = 71`（grep 实读）；`core/database/schemas/...StudyDatabase/71.json` 存在（`ls` 实证，`69/70/71.json` 为最新三件） | ✅ 与计划 §3 "DB schema 71" 一致 |

工作树脏项按目录分布（`git status --porcelain` 分组计数，实跑）：`feature/*` 134（他线 UI 轨）、`core/ui` 24+、`docs/research` 16+、`tools/kb_coverage` 11、`app/` 28（其中 `app/src/main/kotlin` 10 条 M）、`knowledge-production/` 若干（kb 线 tmp 产物）。锁文件全部 untracked——符合"锁文件与 `tmp/` 不提交"。

## 二、热区快照与 app、core/data、core/model 开工态

热区口径沿批次 36 基线 §二：`RoomTutorToolRunner.kt`、`feature/*`、`core/ui`、`core/export`、`core/markdown`、`core/domain`、`settings.gradle.kts`、Root、`docs/current`。

| 热区/模块 | 开工态（`git status --porcelain` 实跑） |
|---|---|
| `RoomTutorToolRunner.kt` | **干净**（对路径单独 status 为空）——与批次 36 基线一致 |
| `settings.gradle.kts` | ` M`（他线模块注册改动延续） |
| Root（`SmartMistakeBookRoot.kt`/`SmartMistakeBookDestinations.kt`） | 两者均 ` M` |
| `app/` | 28 条脏；`app/src/main/kotlin` 10 条 M（ExportPdfWorker/ReviewReminder*/SecondaryScreens 等，他线 UI 轨）；**app androidTest 与 `app/src/main/.../visualjs/` 全干净**——V-01 迁移落点（T2）开工面干净 |
| `feature/*` | 134 条脏（tutor/library/capture/review/profile）——他线 UI 重建轨工作面 |
| `core/ui` 35 / `core/export` 6 | 脏（他线） |
| `core/domain` | **仍 4 个源文件 M**：`BatchImportRepository.kt`、`MistakeDetailRepository.kt`、`SchedulingEvaluation.kt`、`TutorJudgedReview.kt`（+ `?? gradle.lockfile`）——与批次 36 基线同 4 文件 |
| `core/model` | **src 干净**（整目录 status 仅 `?? gradle.lockfile`）——批次 36 基线里 M 的 `FigureGenerationPolicy.kt` 已随 `82f1c3c7` 入库，现 git 干净。计划 §2 T1 "源快照含 core/model" 的对象当前无未提交改动 |
| `core/data` | **16 条脏**（比批次 36 基线多 1）：`RoomMistakeDetailRepository.kt` + 12 个测试文件 M，`?? gradle.lockfile`，`?? src/test/.../visual/FigureGenerationLocalBudgetBoundaryTest.kt`（新）；**androidTest 全干净**（V-01/A-10 套件在 HEAD 状态） |
| `docs/current` | 7 条 M（README/architecture-contract/data-schema/implementation-status/model-task-contracts/product-contract/release-gates）；**`visual-generation-contract.md` 本身干净**——与批次 36 基线一致 |

**对计划 T5 的直接含义**：`docs/current` 整目录热区**未清空**（7 M 在飞）→ 计划 T5 "契约 :208 若热区清空否则登记"应走**登记**分支（`visual-generation-contract.md:208` 本身干净可改，是否动笔归主会话热区裁定，同批次 36 基线 §7.2 口径）。

## 三、V-01 套件现树形态与隔离引擎绑定先例

### 3.1 V01VisualChainInstrumentedTest 现树形态（`core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/visual/V01VisualChainInstrumentedTest.kt`，940 行，实读全文）

- **4 例落 core/data** ✅（与计划 §0 一致）：`@Test` 于 `:138`（aJsProposal…ZeroRemoteDispatchWitnesses，生成腿+四断言位）、`:238`（theProductionAssembly…KeepsJsUnavailableWithoutAuditRows，生产装配负控）、`:271`（theFrozenDrawCommandsReplay…，屏重放）、`:317`（theRestoredDocumentWritesAPdf…，PDF）。
- **引擎注入形态**：`FigureGenerationCoordinationFactory.create(..., visualEngine = engine, visualFrameInputs = …)` 于 `:147-148`（用例 A）与 `:418-419`（C/D 骨架）；engine = **进程内** `QuickJsVisualExecutionEngine(measurer)`（工厂方法 `:667-668`；engines 列表 `:118`）。文件对 `com.tingyun.smartmistakebook.visualjs` 的 import 为 0（批次 36 证据 step02 :82 同口径）→ 计划 §0 "不满足'真 VisualJsClientEngine（:visual_js 隔离进程）'语义底线"**属实**。
- **夹具缺陷①（度量器复用，2/4 红归因，与计划 §0 一致）**：`runChainToRestoredReplay()` 只建一个 `DeviceTextMeasurer`（`:411`），程序被执行两次——协调器 `generate`（`:421`）+ 测试直取 drawOps 的 `engine.execute`（`:432`）；`DeviceTextMeasurer.measure` 每次向 `measured` 追加（类 `:605-641`，追加点 `:636`）→ 2 条使 `singleOrNull()` 为 null。断言位：`:225`（用例 A，单次度量不受影响）、`:550`（`ChainArtifacts.labelResolver`）、`:582`（`hasLabelInk`）。红例=用例 C（`:271`）与 D（`:317`），归因原文：`docs/research/evidence/2026-10-30-batch-36/step02-v01-chain.md:79-80`（"失败点：…labelResolver() 的 requirePresent(measurer.measured.singleOrNull()…)（:549–:550）…归因：用例夹具/断言与'整链只度量一次'假设不一致"）+ 记录 `2026-10-30-batch-36-record.md` §4.4（`:203`）。
- **夹具缺陷②（"零派发见证不变量保持"，计划 §1.1 原文措辞）**：批次 36 证据/记录对 V-01 红例只归因了度量器一处，**"零派发见证"在既有文书中无独立红例归因**（record/step02 grep 该词组无命中）。现树对应观测面（实读）：四断言位 KDoc `:85-94`；断言位③ `:217-222`（`readParentDispatchCount(PARENT_REQUEST_ID)==0`）；预置夹具 `seedParentOperation` `:732-768`（`dispatch_count to 0` 在 `:742`）**仅在用例 A 被调用（`:140`），C/D 共用骨架（`:395-540`）不 seed 父操作行**——即 ③ 号见证在重放/PDF 两例上不活跃。该计划的"缺陷"读作"见证不变量在迁移时须保持/补齐"，其确切落点以实现轨按计划语义落码为准；本基线只登记现树事实（seed 仅在 A、见证③在 C/D 无观测对象），不代裁定。
- 其他既有降级登记未变：UI 卡片段=测试内最小渲染（KDoc `:96-99`）、度量器=测试内真机度量（`:100-106`）、`VisualFrameInputs` 为 `fun interface`（`core/data/src/main/.../visual/VisualFrameInputs.kt:19`，app 依赖 core/data → **app 模块可见**，计划 §1.1 该句成立）。

### 3.2 VisualJsClientEngine 构造与绑定形态（T2 迁移的注入对象）

- 构造：`class VisualJsClientEngine(private val context: Context) : VisualExecutionEngine`（`app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsClientEngine.kt:100`）；KDoc 明言 context 必须是 application context。
- 绑定：按需 `bindService`——`Intent(context, VisualJsService::class.java)` + `Context.BIND_AUTO_CREATE`（`:648-650`）；per-bind Link 状态、握手/ready 预算、死亡重建（KDoc `:69-87` 区段）。Manifest：`android:name=".visualjs.VisualJsService"` + `android:isolatedProcess="true"` + `android:process=":visual_js"`（`app/src/main/AndroidManifest.xml:69-72`）。
- **app androidTest 起隔离客户端的既有先例**（`app/src/androidTest/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsIsolationInstrumentedTest.kt`，1219 行，实读关键段）：
  - client 本体直构：`VisualJsClientEngine(context)`（`:119-120` helper，`:330`/`:707` 用例内同型）——**这就是 T2 要注入协调器的形态**。
  - AIDL 直连另有 `VisualJsServiceHarness`（`:1036-1219`）：真实 `bindService(Intent(context, VisualJsService::class.java), …, BIND_AUTO_CREATE)`（`:1086`）、`IVisualJsService.Stub.asInterface`（`:1069`）、death recipient（`:1051-1053`）、ready 轮询 `awaitReady`（`:1099-1108`，30 s 预算）、AIDL execute + reliable PFD 先 execute 后写信封（`:1112-1196`）、`close()` unbind（`:1198-1205`）。KDoc `:1031-1034` 明示 harness **刻意不复用 client**（直打 AIDL 表面）；client 自身由重建用例覆盖 → V-01 迁移应走 client 本体 + `VisualFrameInputs`，harness 只是隔离进程生命周期的先例参照。

## 四、A-10 红例夹具（与计划 §0/§1.2 一致）

套件：`core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/visual/FigureDispatchBudgetBoundaryInstrumentedTest.kt`（git 干净）。3 例：`aSeventhOperationDispatchIsDeniedWithTheRealCount` `:50`（绿）、`switchingMethodDoesNotResetTheOperationDispatchCount` `:84`（绿）、`theChatFinalAnswerSlotSurvivesFigureBudgetExhaustion` `:122`（**红**）。

- **夹具落库路径**：`seedModelTask` `:173-196`（三例调用点 `:56`/`:87`/`:125`），内部 `database.createModelTask(CreateModelTaskCommand(...))` `:183-186`；落库初态 = `ModelTaskTransactionDao.kt:204` `status = ModelTaskStatus.WAITING_FOR_MODEL.name`（实读）。
- **守卫行号**：`ModelTaskDatabasePort.kt:52-54`（`core/database/src/main/kotlin/.../database/ModelTaskDatabasePort.kt`，实读）：`require(expectedStatus == ModelTaskStatus.QUEUED) { "Only a queued model task may reserve a remote dispatch" }`；命令类 `ReserveModelTaskRemoteDispatchCommand` 声明在 `:42`。
- 红因链（批次 36 记录 §4.4 `:205` 原文转录，与本轨实读行号吻合）：夹具建行 WAITING_FOR_MODEL → reserve 前置守卫抛 `IllegalArgumentException` → "耗尽→终答位保留"子门无设备证据。计划 §1.2 修复方向（按前置校验落库为 QUEUED + 子门补证）与现树缺口吻合。

## 五、R-04 现状：keep 规则内容、K1–K5 证据、K6 判据

### 5.1 keep 规则现文（实读两文件全文）

- `app/r04-test-keep.pro`（**75 行**，批次 36 扩充后）：① 测试类构造器+`@Test/@Before/@After/@BeforeClass/@AfterClass`（`:22-29`）；② `@Test` 成员（`:32-34`）；③ `androidx.test.**`/`org.junit.**`/`junit.**` 全量（`:38-40`）；④ **批次 36 新增 `-keep class j$.** { *; }`（`:74`，注释 `:42-73` 记名 j$/util/g 两 R8 改名碰撞机理与 main/test mapping 双证）**。
- `app/proguard-rules.pro`：**批次 36 新增 `-keep class androidx.tracing.Trace { beginSection(String); endSection(); forceEnableAppTracing(); }`**（`:35-40`，注释 `:21-34` 记 monitor 1.8.0 按名引用 30 处、类被 AGP 排除在测试 APK 外+主包内联删除的机理）。
- 接线：`app/build.gradle.kts:98` `testProguardFiles("r04-test-keep.pro")`（grep 实证）。
- 门脚本 `tools/ci/check_r04_keep_rules.py` 在树；**本轨未复跑**（非本 ask 核对项）→ 现轮 PASS 状态转录自批次 36 证据（`build/device-track-batch36/r04-k4-keepgate.txt` 两行 OK，本轨实读）。

### 5.2 K1–K5 证据现状（实读 `docs/research/evidence/2026-10-30-batch-36/step04-r04-k1-k6.md` 判据表 :66-71 + 目录实证）

| 步 | 状态 | 证据 |
|---|---|---|
| K1 | PASS | `r04-step1-build.log:811` BUILD SUCCESSFUL；`r04-step1-red.log:2` Permission Denial 红基线（batch35 目录） |
| K2 | PASS | `r04-step2-build.log:543` BUILD SUCCESSFUL + `K2_BUILD_EXIT=0`（batch36 目录） |
| K3 | PASS | 主/测两包证书 SHA-256 相同（`r04-k3-main.txt:2`/`r04-k3-test.txt:2`，batch36 两份在树，本轨实读目录） |
| K4 | PASS | `r04-k4-keepgate.txt` 两行 OK（本轨实读原文："OK: …0 hits (rule lines contain no -dontobfuscate)" ×2 + 汇总行） |
| K5 | **未按判据执行**（so 双 ABI 半边有、dex 计数缺） | batch36 目录 `ls \| grep -i "k5\|k6"` **0 命中**（exit=1）；批次 35 的 `build/device-track-batch35/r04-k5b-count.txt` 内容单值 `275`（本轨实读）——是批次 35 轮的产物，不覆盖批次 36 重建 |
| K6 | **无执行证据** | batch36 目录无任何 k6 产物；最近一次实测仍是批次 35 红（`build/device-track-batch35/r04-k6-logcat.txt`，三类签名 `:86`/`:92`/`:110`） |

→ 与计划 §0 "K1–K4 绿（扩充后 keep 的 release 包）、K5 半边、K6 未执行 → 阻塞维持" **逐字相符** ✅。

### 5.3 K6 判据原文与命令（`docs/research/2026-10-30-r04-k6-keep-rules.md` §5.1 `:123-138`，实读转录）

- 前置门（改规则后全链从步 2 重跑）：① `python tools/ci/check_r04_keep_rules.py` 仍 OK/OK、EXIT=0；② 源快照门（release 前后 src-sha，含 core/model）；③ K2 重建 `-Psmbk.testBuildType=release` 两 APK；④ K3 证书逐字相同；⑤ K4 两 `.pro` dontobfuscate/dontshrink/dontoptimize=0 且 mapping 存在；⑥ K5a so 双 ABI、K5b 测试类原名存活 + **mapping 产物级预检**（主 mapping 仍含 `DesugarCollections$SynchronizedMap -> j$.util.g`；测试 mapping **不含** `-> j$.util.g:` 条目，等价命令 `grep -F ' -> j$.util.g:' <测试mapping>` 期望 0 命中；`grep -a -F -c 'androidx/tracing/Trace' <主包classes*.dex>` ≥ 1）。
- **K6 设备命令（判据原文照抄）**：`install -r -t` 两 APK → `logcat -c` → `am instrument -w -r -e class com.tingyun.smartmistakebook.visualjs.VisualJsIsolationInstrumentedTest#workerIsIsolatedAndAProgramRoundTripsWithoutBeingKilledLater,com.tingyun.smartmistakebook.visualjs.VisualJsIsolationInstrumentedTest#theWatchdogKillsAHungWorkerAndAFreshGenerationHasNoBusyState com.tingyun.smartmistakebook.offline.test/androidx.test.runner.AndroidJUnitRunner`。
- **通过**：摘要 `OK (2 tests)` 且 logcat `grep -cE "NoClassDefFoundError|ClassNotFoundException|NoSuchMethodError"` = **0**。**仍红**：按阻塞回报，不得改写判据、不得换 internal 变体、不得 `-dontobfuscate`。
- 注意：**批次 36 记录员产物级预检实测"预检②不过（j$ 碰撞仍在、j$ keep 仅部分生效）"**（record `:329`）→ K6 上设备前该预检是否复测由实现轨按 R1 §5.1-⑥ 执行，本轨只登记该未清项。

## 六、KD-82 / KD-77 / KD-73 三锚点

### 6.1 KD-82（`docs/known-defects.md:3143`，open/known limitation，实读全文）

- 锚点现树复核**未漂移**（grep 实证）：`OpenAiModelProtocol.kt:552`/`:583` 两处 `maxLength`（`:619`/`:652` 同族小边界）；`FigureGenerationPolicy.kt:32` `MAX_IMAGE_PROMPT_UTF8_BYTES = 4*1_024`、`:33` `MAX_PROGRAM_UTF8_BYTES = 64*1_024`、`:222`/`:225` 字节门与错误文本。`FigureGenerationPolicy.kt` 现在 **git 干净**（批次 36 的 +29 行 `FigureGenerationLocalJsBudget` 已随 `82f1c3c7` 入库）——台账 Progress 段"该文件为 M"的描述已过时（该段自述时点 2026-10-30，无妨）。
- **计划 §1.4 口径差（登记不代裁）**：计划只写 "schema `maxLength` 改 16384（= 65536B 的最坏 4 字节上界）"，即只覆盖 **program** 字段；KD-82 Fix direction ① 是**两字段**——`imagePrompt` 4096→**1024**、`program` 65536→**16384**（`⌊字节上限/4⌋`）。imagePrompt 半边计划未提，实现轨落码前宜与主会话确认范围。另 KD-82 关闭条件原文要求"广告解冻批（A-04 或同等）裁定 ①/②、落码并过 JVM 用例"，计划 §1.4 直接以"JVM 正/负例后 KD-82 关闭"收口——**跳过了"A-04 或同等广告解冻裁定"前提**，与台账关闭条件有口径差，处置归主会话。

### 6.2 KD-77（`docs/known-defects.md:2907`，open，实读全文）

- 台账最新 Progress（批次 36 T6）：API 23 最小冒烟闭环已跑（`emulator-5596`=api23_smoke64、修复后包 sha `1593b25a…`、冷启动 1611ms、四类签名 0），证据落 `tmp/kd77-api23-smoke/`（未入仓），"关闭裁决不代裁 + 跨账本分歧（kb-batch25:401 自称已关）待主会话裁定"。
- **tmp 证据现状（`ls tmp/kd77-api23-smoke/` 实跑）**：**6 个文件**——`00-prestate.txt`、`01-install.txt`、`02-cold-start.txt`、`03-logcat-full.txt`、`04-signature-scan.txt`、`05-post-state.txt`。计划 §1.5 与记录 §3.6 都写"五件套"/"00–05"——**"五件套"计数与盘上 6 文件不符**（或以 00 为前置不计），迁移（T5）应**整目录 6 文件迁入** `docs/research/evidence/2026-10-31-batch-37/`，勿按"五"漏件。

### 6.3 KD-73（`docs/known-defects.md:2796`，open，实读 KD-73 段全文并 grep）

- 脚本与日志均在树：`tools/ci/check_stdlib_compat.py`（649 行，随 `3ac0b770` 批次 36 提交）、`tmp/kd73-stdlib-scan/run-final.log`（30 行，本轨实读尾部："OK: 0 unresolved stdlib member references across **23847** unique kotlin/* refs (329 artifacts; 11 coordinates unscanned)" + `SCAN_EXIT=0`）。
- **台账缺回链实证**：对 KD-73 段（`:2796` 至 KD-77 前）grep `check_stdlib_compat|kd73-stdlib-scan|23,847` = **0 命中**——台账仍停留在批次 33 的"无可复跑脚本与结果落仓 → UNVERIFIED"状态，`3ac0b770` 之后的脚本化事实未回链。计划 §1.6 的回链缺口**属实** ✅。

## 七、设备在线状态（只读）

`"C:/Android/Sdk/platform-tools/adb.exe" devices -l`（本会话实跑）+ 逐台 `getprop ro.build.version.sdk`：

```
emulator-5562   device  sdk_gphone64_x86_64   API 34
emulator-5584   device  sdk_gphone64_x86_64   API 34
emulator-5596   device  generic_x86_64        API 23   ← api23_smoke64（KD-77 证据轨同机）
```

**3 台模拟器在线（全 x86_64；2×API 34 + 1×API 23）**，与批次 36 基线同三台。未做任何安装/推送；设备上已装 APK 是否对应当前源 **UNVERIFIED**（本轨未查包版本）。

## 与计划 §0/§1 的不符处汇总（核心交付）

1. **HEAD 过时**：§0 "批次 36 收口后，HEAD `1912180a`" → 现 HEAD `5ee1f90f`（kb 线批次 26–29 四提交叠加，`1912180a` 为其祖先）。批次 37 的 diff/文档基线应按 `5ee1f90f` 起算；kb 线提交不触及 4e.2 热区文件（§二 所列 M 文件集合与批次 36 基线同形），但 **HEAD hash 与"工作树计数"类证据引用需用新值**。
2. **"清单 A-10/A-11/A-12/V-01 行未刷新"失实**：v3 补记 10（2026-10-31，随 `1912180a` 在树）已刷新 A-10/A-11/A-12/V-01/R-04 五行（行内注记追加、0 项转通过；本轨对清单头部与四行 grep 实证）。计划 T5 的"清单行刷新"实际**已发生**，剩余动作只有契约 `:208` 回改（热区未清空 → 登记分支）与后续轮次的状态再刷新。
3. **"五件套"计数差**：`tmp/kd77-api23-smoke/` 盘上 **6 文件（00–05）**，计划/记录写"五件套"——迁移按整目录迁，勿漏件。
4. **KD-82 范围口径差**：计划 §1.4 只写 program→16384；KD-82 处方是两字段（imagePrompt→1024、program→16384），且台账关闭条件带"A-04 或同等广告解冻批裁定"前提——计划未提。范围与关闭路径归主会话裁定。
5. **其余主干相符**：DB schema 71 ✅；V-01 4 例落 core/data + 进程内引擎注入（`:667-668`）+ 2/4 红归因度量器夹具 ✅；A-10 3 例 1 红归因 QUEUED 前置（`ModelTaskDatabasePort.kt:52-54` + `ModelTaskTransactionDao.kt:204`）✅；R-04 K1–K4 绿/K5 半边/K6 未执行 ✅（且 K6 判据原文与命令在 R1 `:123-138` 完整可执行）；KD-73 回链缺口属实 ✅；`VisualFrameInputs` app 可见 ✅；设备 3 台（含 API 23 一台）✅。
6. **批次 36 遗留未清项（计划未列，登记防漏）**：① `FigureGenerationLocalBudgetBoundaryTest.kt`（A-10 JVM 边界 5 例）**未入仓**（`??`）——T5 若不迁它，E-05 读值的载体文件仍游离在版本控制外；② K6 mapping 产物级预检②（j$ 碰撞）批次 36 记录员实测"不过"，K6 上设备前须复测（§5.3-⑥）。

## 附：本会话实跑命令清单（可重跑）

```bash
git rev-parse HEAD origin/main; git log --oneline -20; git merge-base --is-ancestor 1912180a HEAD   # 5ee1f90f…; 4 kb commits on top; exit=0
git rev-list --left-right --count origin/main...HEAD   # 0 0
ls -l .git/FETCH_HEAD                                   # Oct 27 00:39
git status --porcelain | wc -l                          # 688（M=212、??=476）
git diff --stat | tail -3                               # 212 files, +5107/-2678
grep -n "STUDY_DATABASE_VERSION" core/database/.../StudyDatabase.kt   # :139 = 71
ls core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/   # …69/70/71.json
git status --porcelain -- <各热区路径>                   # §二 表（RoomTutorToolRunner 空；docs/current 7 M；core/model 仅 lockfile）
# V-01 / 隔离引擎
grep -n "@Test\|QuickJsVisualExecutionEngine\|singleOrNull\|DeviceTextMeasurer\|seedParentOperation" core/data/src/androidTest/.../visual/V01VisualChainInstrumentedTest.kt
grep -n "class VisualJsClientEngine" app/src/main/.../visualjs/VisualJsClientEngine.kt               # :100
grep -n "bindService\|VisualJsService::class" app/src/main/.../visualjs/VisualJsClientEngine.kt      # :648-650
grep -n "visual_js\|isolatedProcess" app/src/main/AndroidManifest.xml                                # :69-72
grep -rn "fun interface VisualFrameInputs" core/ --include=*.kt                                       # core/data/.../VisualFrameInputs.kt:19
# A-10
grep -n "seedModelTask\|createModelTask" core/data/src/androidTest/.../visual/FigureDispatchBudgetBoundaryInstrumentedTest.kt   # :56/:87/:125/:173/:183
sed -n '195,215p' core/database/.../dao/ModelTaskTransactionDao.kt    # :204 WAITING_FOR_MODEL
sed -n '45,60p' "core/database/.../ModelTaskDatabasePort.kt"          # :52-54 QUEUED require
# R-04
cat app/r04-test-keep.pro; sed -n '15,45p' app/proguard-rules.pro     # 4 组规则含 j$.** (:74)；tracing 三签名 (:35-40)
grep -n "testProguardFiles" app/build.gradle.kts                       # :98
ls build/device-track-batch36/ | grep -i "k5\|k6"                      # 0 命中（exit=1）
head build/device-track-batch36/r04-k4-keepgate.txt                    # OK ×2
cat build/device-track-batch35/r04-k5b-count.txt                       # 275
sed -n '118,145p' docs/research/2026-10-30-r04-k6-keep-rules.md        # K6 判据与命令
# KD 三条
grep -n "## KD-73\|## KD-77\|## KD-82" docs/known-defects.md           # :2796/:2907/:3143
grep -n "maxLength" core/data/.../OpenAiModelProtocol.kt               # :552/:583/:619/:652
grep -n "MAX_IMAGE_PROMPT_UTF8_BYTES\|MAX_PROGRAM_UTF8_BYTES" core/model/.../FigureGenerationPolicy.kt   # :32/:33
ls tmp/kd77-api23-smoke/                                               # 6 文件 00–05
ls tools/ci/check_stdlib_compat.py tmp/kd73-stdlib-scan/run-final.log  # 均在；log 尾 OK 0 unresolved / 23847 / SCAN_EXIT=0
python -I -c "…grep 'v3 补记 10'…四行"                                  # 清单五行已刷新实证
# 设备
"C:/Android/Sdk/platform-tools/adb.exe" devices -l                     # 3 emulators
adb -s emulator-{5562,5584,5596} shell getprop ro.build.version.sdk    # 34/34/23
```
