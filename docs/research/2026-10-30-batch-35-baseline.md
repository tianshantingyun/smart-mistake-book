# 批次 35 基线核对（2026-10-30）

> 核对人：基线核对工程师（子代理，只读核对：零代码改动、零 Gradle、零设备写）。
> 方法：全部结论来自本会话实读文件与实跑命令，命令与关键输出随条记录。
> 无当前证据的结论一律标 **UNVERIFIED**。
> 对照计划：`.zcode/plans/plan-2026-10-30-batch-35-dual-backend-closeout.md` §0（下称计划 §0）。

## 0. 一句话结论

计划 §0 的 8 条缺陷中，**6 条已在批次 34 复核修复轮/收尾提交中落树或回填**（对账 JS 区分、Runner 第二道门、T5b、源快照判据补 core/model、KD-80 回填关闭、A-08 回填），计划 §0 相关表述已过时；**仍然成立的**是：本地 attempt 真库测试（文件在树但未提交/未上设备）、`.pro` 幽灵门（无脚本）、`figure_method_unavailable` 无痕迹渲染、parsers 注释过时、R-04 K1–K6 未执行。批次 34 已提交的 53 个文件全部干净；工作树另有 685 条脏项，其中大量热区改动属他线 UI 轨遗留，批次 35 不得裹挟提交。

## 一、HEAD、origin/main、工作树、DB schema、请求 schema

| 项 | 结果 | 证据（本会话实跑） |
|---|---|---|
| HEAD | `902d7acce117c3e33905b1ec5ba9ea0d61a21f9c`（`git rev-parse HEAD`） | 902d7acc = "docs(evidence): 批次34 设备证据…" |
| origin/main（本地引用） | 同上，逐字节相同（`git rev-parse origin/main`）；`git rev-list --left-right --count origin/main...HEAD` = `0 0` | 本地视角无差 |
| **fetch 新鲜度** | `.git/FETCH_HEAD` mtime = **2026-10-27 00:39:45 +0800**（`stat` 实读）——近三天未 fetch，"HEAD==origin/main"只是本地引用视角；**远端是否已有新提交 UNVERIFIED**（本轨只读，未 fetch） | `ls -l .git/FETCH_HEAD` |
| 工作树计数 | **685 条**：`211 M` + `474 ??`（`git status --porcelain \| wc -l`；按状态列 `awk` 计数）。真实内容改动非纯行尾：`git diff --stat` = **211 files, 5138 insertions(+), 2683 deletions(-)**，`--ignore-cr-at-eol` 仅差 1 行 | 命令见左 |
| DB schema | **71**：`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabase.kt:139` `internal const val STUDY_DATABASE_VERSION = 71`（实读）；`core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/71.json` 存在（`ls` 最高 69/70/71 中的 71） | ✅ 与计划 §2"DB schema 保持 71"一致 |
| 请求 schema | **figureMethods 默认 IMAGE_MODEL**：`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/model/OpenAiModelProtocol.kt:378-379` `figureMethods: Set<…> = setOf(…IMAGE_MODEL)`；`jsAdvertised` 仅当集合含 JS（`:388-389`）；生产默认路径与 A-03 之前逐字节相同（`:371-373` KDoc，实读） | ✅ 与计划 §0 A-03 表述一致 |

工作树脏项按目录分布（`git status --porcelain` 计数，实跑）：`feature/tutor` 59、`feature/library` 43、`core/ui` 35、`docs/research` 32、`app/src` 27、`feature/capture` 16、`core/data` 15、`feature/review` 12、`tools/kb_coverage` 11、`core/database` 10、其余零散。**锁文件全部 untracked**（各模块 `gradle.lockfile` `??`）——符合"锁文件不提交"；`tmp/` 为 untracked——注意"不得 `git add -A`"。另发现一个可疑残留文件 `feature/tutor/src/main/java/com/tingyun/smartmistakebook/feature/tutor/nul`（untracked，Windows NUL 设备名残留，需处置但本轨不动）。

## 二、热区快照与核心模块开工态

热区口径按计划 §2：`RoomTutorToolRunner.kt`、`feature/*`、`core/ui`、`core/export`、`core/markdown`、`core/domain`、`settings.gradle.kts`、Root。

| 热区/模块 | 开工态（`git status --porcelain` 实跑） |
|---|---|
| `RoomTutorToolRunner.kt` | **干净**（`git status --porcelain <path>` 空） |
| `settings.gradle.kts` | ` M`：+1 行 `":core:markdown"`（`git diff` 实读）——新增模块注册，属他线改动 |
| Root（`SmartMistakeBookRoot.kt`） | ` M` |
| `feature/*` | **134 条脏**（tutor 59 / library 43 / capture 16 / review 12 / profile 4），含大量 untracked 新测试（Claude* 套件等）——他线 UI 重建轨的工作面，非批次 34 内容 |
| `core/ui` | **35 条脏**（ClaudeButtons/ClaudeCodeBlock 等 untracked + 既有文件 M） |
| `core/export` | 6 条脏（build.gradle.kts、JobRunner、3 个测试） |
| `core/markdown/` | **整目录 untracked**（`??`）——与 settings.gradle.kts 的新模块配对 |
| `core/domain` | 5 条脏 |
| `core/model` | **src 干净**；仅 `?? core/model/gradle.lockfile` |
| `core/data` | 15 条脏（androidTest 夹具、`RoomMistakeDetailRepository.kt`、测试等）+ untracked：`gradle.lockfile`、**`FigureGenerationLocalAttemptOrdinalInstrumentedTest.kt`**（见三.3）、`VisualFrameInputs.kt` |
| `core/database` | 10 条脏（`RoomBatchImportStore`、`TutorConversationDatabaseContract`、DAO 等）+ `?? gradle.lockfile` |

**批次 34 已提交文件应干净——✅ 成立**：批次 34 提交范围为 `71a7ae66^..902d7acc`（9 个提交：71a7ae66 模型层、39dbe6d4 attempt 写口+对账 JS 区分、559145cf 协调器注入+请求 schema+第二道门、1a87937a JVM 用例、ef6cd67b rowCounts 用例、1c3f4ed8 R-04 配置、93cb099f KD-81 快照身份、78caacb2 台账、902d7acc 设备证据），`git diff --name-only` 共 **53 文件**；与当前脏项清单求交集（`comm -12`）= **空**。即批次 34 落的所有文件在提交后无二次改动。

注意：计划 §0 说"批次 34 完成后，未提交"——现树**批次 34 交付物本身已全部提交**（含设备证据 902d7acc）；"未提交"的 685 条脏项主要是他线（UI 重建）遗留 + 少量批次 35 已开工文件（见三.3），与"推送待做"合并读：**批次 34 的 9 个提交是否已推到远端 UNVERIFIED**（本地 origin/main 引用三天未更新）。

## 三、批次 34 修复环六落点在树核对

| # | 计划 §0 缺陷 | 现树事实 | 判定 |
|---|---|---|---|
| 1 | JS 成功被对账虚报（`FigureGenerationReconcileDao.kt:128-137`） | **已修复且已提交**。`FigureGenerationReconcileDao.kt:128` `AND method <> 'JS'`（假成功候选排除 JS）；`:173` 操作级确认条件认 `confirmed.method = 'JS'`；KDoc `:115-118` 明说"本地方法不在候选内"。落点提交 `39dbe6d4`（"批次34 本地 attempt 写口（负序号 @Transaction）+ 对账 JS 区分（修复环）"，2026-10-29 23:20），文件干净（`git status` 空） | **计划 §0 过时**；§1 任务 1 的"区分"半边已在树，"补正/负向用例"见 #3 行与批次 34 台账（A-01 行 v3 补记 6 亦已登记此修复） |
| 2 | Runner 只有一道门（`RoomTutorToolRunner.kt:946-958`） | **第二道门已落且已提交**。`RoomTutorToolRunner.kt:949-962`：`if (call.figureMethod == FigureGenerationMethod.JS)` → `ok=false` + `JS_FIGURE_UNAVAILABLE_SUMMARY` + `FIGURE_METHOD_UNAVAILABLE`；`:972-973` 注释"能走到这里的调用方法恒为 IMAGE_MODEL"。落点提交 `559145cf`（注明"RoomTutorToolRunner 整文件纳入：含他线改动与本批修复环的第二道门"） | **计划 §0 过时**；且落点**不是**计划 §1 任务 2 建议的"非热 seam"，而是直接落进热区文件本体（随他线 diff 整文件提交并注明，台账 A-02 行 v3 补记 6 已登记）。批次 35 无需再落此门 |
| 3 | 本地 attempt `@Transaction` 无真库测试 | `insertLocalAttempt` 在树（`FigureGenerationDao.kt:125-148`，`@Transaction`、`MIN(attempt_ordinal)-1` 负序号、`reservation_id=null`，提交 39dbe6d4）。**真库测试文件已存在于工作树但 untracked**：`core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/visual/FigureGenerationLocalAttemptOrdinalInstrumentedTest.kt`（204 行，实读）——真库 `StudyDatabaseFactory.open`，断言 -1/-2 与远程 1/2 共存、远程 1 重复写入 ABORT、`reservation_id` NULL、method 逐行核对 | **部分推进**：用例已写（内容覆盖计划 §1 任务 3 全部要求），但**未提交、未上设备 → 执行结果 UNVERIFIED**。计划 §0"无真库测试"的表述已过时，但"无设备执行证据"仍成立 |
| 4 | 源快照门漏 `core/model/src` | **判据文档已改写**：`docs/research/2026-10-29-batch-34-device-criteria.md:284/:302` 的 find 集合已含 `core/model/src`，`:310` 注明"2026-10-29 复核修复轮更正"；批次 34 证据 `step01-build-install-dex.md` §8 复核更正同口径（864→973 文件口径）。**但该"门"是流程性 find 命令（证据步骤），仓内无脚本门**（`grep -rln "src-sha-before" tools/` = 0） | **文档层已补**；计划 §1 任务 4 若指"脚本化门"则仍未落，若指"判据含 core/model"则已完成。后续设备轨必须按新 find 集合执行（当前无新设备证据） |
| 5 | WAL T5b 缺用例 | **T5b 已存在**：`tools/tests/test_split_gate.py:829` `test_export_rejects_wal_change_inside_transaction`（实读：事务内写入后断言 `exit 2` + "快照一致性" + 不落盘）。**本会话实跑** `python -m pytest tools/tests/test_split_gate.py -q` → **37 passed in 4.30s**（exit 0） | **计划 §0 过时**；KD-81 关闭所需的 T5b 已补且绿 |
| 6 | `.pro` 注释声称一个不存在的门 | **缺陷仍成立**。`app/r04-test-keep.pro:13-14` 声称 "The R-04 gate greps the proguard rule files for that directive and requires 0 hits in each"；但 `grep -rn "dontobfuscate" tools/ --include=*.py/*.ps1/*.sh` = **0 命中**，`tools/ci/` 无任何 proguard/r04 脚本；`dontobfuscate` 全仓只出现在 docs（含批次 34 证据 `00-index.md:144` 的一次性手工 grep `grep -cE "dontobfuscate|dontshrink|dontoptimize"` = 0） | **幽灵门确认**：该门只以一次性手工 grep 存在于证据文档，无 tools/ci 脚本。计划 §1 任务 15 的二选一（实现 tools/ci 脚本或改注释）仍待做 |

## 四、KD-80/81 现文本与四条判据；准入清单现状

### KD-80（`docs/known-defects.md:2991` 起，实读全文）

- **现状态：已关闭**。标题行：`## KD-80 (closed 2026-10-29 批次 34；…)`，块内含批次 34 设备轨回填块与记录员"确认关闭"块。
- **四条判据在批次 34 设备证据上的落点（台账原文转录）**：
  - **80-C1 = PASS**：`BackupRestoreInstrumentedTest` 设备 `OK (34 tests)`（E-02，`step02-backup-rowcounts-trimmer-c4.md` §3）。
  - **80-C2 = PASS（静态 + 设备侧逐名）**：静态 `RESULT=PASS`/`EXIT=0`（90/90/90、PLAN_HASH=JSON_HASH=714c0ecb…）；设备 `BackupRowCountsCoverageInstrumentedTest` `OK (2 tests)`（raw `build/device-track-batch34/step02-rowcounts.log`），90 键逐名、`missing=[] extra=[]`。
  - **80-C3 = PASS（设备本批 + JVM 沿用批次 33 仓内命令日志）**：设备裁剪器 `OK (9 tests)`；JVM `SmbkSelectionPlanTest` 12/0 + `VisualCheckpointRetentionTest` 17/0（`docs/research/evidence/2026-10-29-batch-33/step-jvm-80c3.md`）。台账保留口径：本批未重跑 JVM。
  - **80-C4 = PASS**：`BackupVisualReferenceInstrumentedTest` `OK (5 tests)`（E-02）。
  - 记录员复核：15 套件计数逐项与源码 `@Test` 一致（34/2/5/9/5/15/4/3/12/12/16/24/16/13/2）；已知边界=设备源快照 find 集合当时不含 `core/model/src`（不影响 KD-80 的 data/database 源）。
- **与计划 §0 的不符**：计划 §0 说"→ 本批裁决关闭"（即批次 35 任务 6），但**台账已在批次 34 内写 closed 并含裁决文本**——计划 §0 缺陷⑦的"KD-80 未回填"半边已过时。批次 35 只需复核裁决文本无需再落。

### KD-81（`docs/known-defects.md:3073` 起，实读全文）

- **现状态：open**。标题："工具侧 `source.snapshot` 已覆盖 db+wal 且 **37** 项回归绿（含 T5b），设备复跑与 `-shm` 口径未决"。
- 块内事实：工具侧修复（`export_split_eval.py:78` `IDENTITY_KIND="inputs-v1"`、`:101-135` db+wal 进摘要、`-shm` 显式排除、`:290-294` 导出前后比对先于写盘）；T5b 已补（`:829`）；37 项回归（**本会话实跑复现：37 passed in 4.30s**）。
- 与关闭条件残差（台账原文）：② `-shm` 字面覆盖 vs 实现按设计排除——**需主会话裁定**（计划 §0 已给出裁定方向："按 db+wal 进身份、-shm 排除裁定为可接受"）；③ AUD-08 设备库复跑未做。
- **批次 35 待做收敛为**：把 `-shm` 裁定写进台账 + 关闭（T5b 不再是缺口）。计划 §0"补 T5b 后关闭"中"补 T5b"已过时。

### 准入清单（`docs/research/2026-10-26-batch-24-4e2-admission-checklist.md`，v3+补记 1–6，实读相关行）

| 行 | 现状原文（转录/摘要） | 批次 35 相关 |
|---|---|---|
| A-04 | **未落**："装配期直接 `require(JS !in availableMethods)`"（`FigurePromptAssembler.kt:40-43`） | 任务 9 |
| A-05 | **未落**："`imageBackendReady` 只看图像后端"（`Capabilities.kt:67-78`） | 任务 10 |
| A-06 | **未落**："长短描述仍是 kind 口径"（`TutorToolDescriptions.kt:56-68`） | 任务 11 |
| A-07 | 部分落（类型在、生产零构造、预算 require 在） | 本批清单外 |
| A-08 | **已落（批次 34，v3 补记 5）**：`figureJsBasisFingerprint` 把 program 字节并入规范化串（`FigureGeneration.kt:646`）、coordinator 按 method 分支（`DefaultFigureGenerationCoordinator.kt:691-705`）、用例 `FigureGenerationCoordinatorTest.kt:463`；JVM 本轮未跑。**计划任务 12 的增量是"入口侧约束"（宿主显式传 basis 时以 program 摘要复核），该半边清单行内未见落点** | 任务 12 前半已落、入口侧约束待落 |
| A-13 | **部分落**："三入口均走 coordinator 建链，但无逐入口断言行，架构 grep 门未立"（恰 3 处构造：`ConfiguredCleanImageGenerator.kt:100`、`AttachedImageGenerator.kt:155`、`TutorFigureGenerator.kt:377`，v2 附录 B 复核） | 任务 12 的 grep 门脚本化 |
| P-11 | **未落**："协调器无 `VisualExecutionEngine` 注入、`UNATTRIBUTED` 命中 0（A-01）；无 AIDL/宿主侧断言"（注：A-01 注入点批次 34 已落，该行现状尚未随 A-01 刷新） | 任务 13 |
| P-12 | **部分落**：G-7 ① 设备绿已有；**② 显式重试预算承继无任何证据**（生产侧无 `explicitUserRetry=true` 调用点，全仓仅测试构造 4 处）；③ 无直接断言、客户端熔断 JVM 状态机专项测试未建 | 任务 14 |
| M-04 | **未落**："当前折 `INVALID_PROGRAM`" | 任务 14 |
| Q-01 | **缺失（真实 policy 漏洞）**："JS 分支只查参数交互，IMAGE 分支才查 pixel"（`FigureGenerationPolicy.kt:185-218` vs `:298-306`） | 任务 14 |

清单总体判定句（v3）：**MVA 不成立**；69 行、0 项转"通过"。A-01/A-02/A-03 三行已由 v3 补记 4/6 刷新为"已落（批次 34）…JVM 待跑/设备 UNVERIFIED"。

### 计划 §0 低级缺陷两处（批次 35 任务 8 的增量，实读核对）

- `figure_method_unavailable` 痕迹渲染：`core/ui/.../TutorToolTraceLine.kt` grep `figure_method_unavailable|FIGURE_METHOD_UNAVAILABLE` = **0 命中** → **仍未落**。
- `OpenAiModelResponseParsers.kt:619-620` 注释过时：实读仍在——"TutorToolCall 的构造器拒绝 JS 与 program（本构建没有运行库）"，与 A-02 落地后的实际行为（接受 JS+program）矛盾 → **仍未更正**。
- 另一处低级项 `maxLength` 按字符/本地门按字节：本轨未核对（计划未列为批次 35 任务），**UNVERIFIED**。

## 五、R-04

- **`app/build.gradle.kts` 配置在树**（实读）：
  - `:12` `val r04TestBuildType = providers.gradleProperty("smbk.testBuildType").orNull ?: "debug"`（属性门）；
  - `:65` `testBuildType = r04TestBuildType`（默认 debug，日常设备轨零变化）；
  - `:98` release 块内 `testProguardFiles("r04-test-keep.pro")`。
- **`app/r04-test-keep.pro`**（36 行，实读全文）：只 keep 测试类构造器/`@Test`/`@Before`/`@After`/`@BeforeClass`/`@AfterClass` 方法（`:18-25`）、`@Test` 成员（`:28-30`）、androidx.test/junit 运行时（`:34-36`）；注释明确"NEVER disable obfuscation"并声称 R-04 gate 会 grep 规则文件（见三.6：**该门不存在于 tools/**）。
- **K1–K6 执行状态**：仓内**无**批次 34/35 的 release 往返证据（批次 27 有 `docs/research/evidence/2026-10-27-batch-27/step03-r04-release-roundtrip.md`，属旧配置时点）；批次 34 记录自记 "R-04 release UNVERIFIED" → **K1–K6 未执行，open**。本轨未跑 Gradle（受限于只读口径），不代跑。

## 六、设备在线状态（只读）

`"C:/Android/Sdk/platform-tools/adb.exe" devices -l`（本会话实跑）：

```
emulator-5562  device  sdk_gphone64_x86_64
emulator-5566  device  sdk_gphone64_x86_64
emulator-5584  device  sdk_gphone64_x86_64
```

**3 台模拟器在线**（x86_64）。未做任何安装/推送/查询扩展。设备上已装 APK 是否对应当前源 UNVERIFIED（本轨未查包版本）。

## 七、与计划 §0 的不符处汇总（核心交付）

1. **缺陷①（对账 JS 虚报）已修复并提交**（39dbe6d4）——计划 §0 仍列为"本批第一优先"待修；§1 任务 1 剩余工作收敛为"确认用例覆盖"（reconciler 设备用例 `FigureGenerationReconcilerInstrumentedTest.kt` 已随批次 34 提交；其是否覆盖 JS 非虚报分支，本轨未逐例核对，UNVERIFIED）。
2. **缺陷②（第二道门）已落且已提交**（559145cf），落点在热区文件本体而非"非热 seam"——计划 §1 任务 2 已无需执行，但需在台账确认"热区登记"口径（A-02 行 v3 补记 6 已登记，核对文本一致）。
3. **缺陷③**：真库测试文件已写好在工作树（untracked、未提交、未上设备）——计划 §0"无真库测试"过时，"无执行证据"仍准。
4. **缺陷④**：源快照判据文档已改写含 `core/model/src`（复核修复轮）；仓内仍无脚本门——"补"的层级与计划任务 4 的落点需对齐（文档已完成，脚本未做）。
5. **缺陷⑤（T5b 缺用例）过时**：T5b 在树（`test_split_gate.py:829`）且 37 项回归本会话实跑全绿。KD-81 关闭只差"-shm 裁定落台账"与（可选）设备复跑。
6. **缺陷⑦（KD-80/A-08 未回填）过时**：KD-80 已 closed（含四条裁决与记录员确认）、A-08 行已由 v3 补记 5 回填。
7. **仍成立的缺陷**：③ 的执行证据缺口、⑥ 幽灵门（tools/ci 无脚本）、低级两项（痕迹渲染 0 命中、parsers 注释过时）、R-04 K1–K6 未执行。
8. **工作树背景**：685 条脏项中绝大多数属他线 UI 重建（Claude* / core:markdown / feature/* 大面积），批次 35 提交必须点名精确文件，严禁 `git add -A`；`feature/tutor/.../feature/tutor/nul` 为 NUL 残留文件需处置。
9. **推送状态**：HEAD==本地 origin/main（0/0），但 FETCH_HEAD 停在 2026-10-27——"批次 34 已推送"与"推送待做"均无当前证据，**UNVERIFIED**；如需定谳需 `git fetch`（本轨只读未做）。

## 附：本会话实跑命令清单（可重跑）

```bash
git rev-parse HEAD; git rev-parse origin/main; git rev-list --left-right --count origin/main...HEAD
git status --porcelain | wc -l            # 685；M=211、??=474
git diff --stat | tail -1                 # 211 files, 5138 insertions(+), 2683 deletions(-)
git diff --name-only 71a7ae66^..902d7acc | sort > /tmp/b34_files.txt   # 53 文件
git status --porcelain | awk '{print $NF}' | sort > /tmp/dirty_files.txt
comm -12 /tmp/b34_files.txt /tmp/dirty_files.txt                       # 空
git log -S "method <> 'JS'" -- core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/dao/FigureGenerationReconcileDao.kt   # 39dbe6d4
python -m pytest tools/tests/test_split_gate.py -q                      # 37 passed in 4.30s
grep -rn "dontobfuscate" tools/ --include=*.py --include=*.ps1 --include=*.sh   # 0 命中
grep -n "figure_method_unavailable" core/ui/src/main/java/com/tingyun/smartmistakebook/core/ui/TutorToolTraceLine.kt   # 0 命中
C:/Android/Sdk/platform-tools/adb.exe devices -l                        # 3 emulators
```
