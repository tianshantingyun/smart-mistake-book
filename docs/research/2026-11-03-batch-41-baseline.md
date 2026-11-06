# 批次 41 基线核对（只读，2026-11-03）

| 字段 | 值 |
|---|---|
| evidence-id | B41-BASELINE-01 |
| executed-at | 2026-11-02T07:05Z（本机时间 2026-11-02，文档命名按计划批次 41） |
| head | `62522930bc8bae51be1523c954f39449e054b6af` |
| origin/main | 同值（`git ls-remote` 实测） |
| 执行者 | 基线核对员（子代理） |
| 工具 | git 2.5x、Python 3.13.14、adb/emulator（SDK `C:\Android\Sdk`） |
| 未执行 | 任何 Gradle 任务、任何 git 写操作、任何设备测试 |
| verdict | 基线成立，但计划 §0 有 2 条“开批未闭合”与当前树冲突（见 §六） |

核对口径：本文件只记录本轮**实读/实跑**的结果。计划 §0 与批次 40 记录 §12 提供的结果（单测门、强门）不在本轮重跑范围内，按“转录”标注，不按通过计。

---

## 结论摘要

| # | 核对项 | 结果 | 判据 |
|---|---|---|---|
| 一 | HEAD/远端/工作树/DB 73/请求 19 | ✅ 一致 | `git rev-parse`、`git ls-remote`、源码常量 |
| 二 | 71/72/73.json 与 `SmbkSelectionPlan` | ✅ 一致（73） | JSON 实读 + 常量/表名集合比对 |
| 三 | 热区 M 快照 | ✅ 5 M（均为他线） | `git status --porcelain` |
| 四 | 本批落点存在性 | ✅ 全部存在（batch-41 证据目录未建，属正常） | `ls` / `git ls-files` |
| 五 | 设备与 AVD | ✅ 4 台在线、8 个 AVD | `adb devices`、`emulator -list-avds` |
| 六 | 与计划 §0 不符 | ⚠️ 2 条硬冲突 + 3 条口径补充 | 源码实读 |

---

## 一、HEAD、远端、工作树、schema 常量

### 1.1 HEAD 与远端（实测同值）

```text
$ git rev-parse HEAD
62522930bc8bae51be1523c954f39449e054b6af
$ git ls-remote origin main
62522930bc8bae51be1523c954f39449e054b6af	refs/heads/main
$ git log -1 --format="%H %ci %s"
62522930... 2026-11-02 14:51:30 +0800 docs: 批次40 主会话收口（门禁实跑/树态/schema73 复核/22 笔提交/纪律偏差）（1 文件）
```

分支 `main`，upstream `origin/main`。计划 §0 的“HEAD = `62522930` = `origin/main`”成立。

### 1.2 工作树计数

```text
$ git status --porcelain | wc -l
508
$ git status --porcelain | grep -c "^??"        # 未跟踪
417
$ git status --porcelain | grep -v "^??" | cut -c1-2 | sort | uniq -c
     91  M                                      # 全部为未暂存修改
$ git diff --cached --name-only | wc -l
0                                             # 索引干净，无暂存
```

- 91 个修改文件按目录：core 34（`core/data` 22、`core/database` 5、`core/export` 3、`core/model` 2、`core/ui` 1、`core/domain` 1）、docs 40、tools 10、app 4（均在 `app/src`）、knowledge-production 1、仓库根 2（`.gitignore`、`dynamic_gui_architecture_plan.md`）。
- 417 个未跟踪里绝大部分是临时产物（`.agent_*`、`.tmp_*`、`_slice*`、crops 图片等）+ **14 个 `gradle.lockfile`** + `feature/tutor/src/main/java/com/tingyun/smartmistakebook/feature/tutor/nul`（批次 40 §10 已列为“不得提交”）。
- 有提交意义但未跟踪的少数几件：`app/src/androidTest/.../visualjs/VisualJs{ClientFrameQueueDevice,DeathUnconfirmedFailClosed,ReconnectBudget}InstrumentedTest.kt`、`app/src/test/.../VisualMeasureEnvironmentBudgetTest.kt`、`core/database/src/androidTest/.../TutorVisualSavedProblemRetentionInstrumentedTest.kt`、`docs/research/2026-10-*`/`2026-11-01-*` 若干研究件。

### 1.3 schema 常量

| 常量 | 值 | 位置 |
|---|---|---|
| `STUDY_DATABASE_VERSION` | **73** | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabase.kt:142` |
| `PAPER_EDIT_SCHEMA_VERSION` | **19** | `core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/ModelTasks.kt:566` |
| `ModelTaskRequest.CURRENT_SCHEMA_VERSION` | **19**（= `PAPER_EDIT_SCHEMA_VERSION`） | `ModelTasks.kt:567` |

schema 接线复核（与批次 40 §12.2 同值）：`errorCauseDao()` 在 `StudyDatabase.kt:309`，`MIGRATION_71_73` 与 `ERROR_CAUSE_MIGRATION_72_73` 在 `StudyDatabase.kt:423-424` 注册；`72→73` 亦登记（`StudyDatabase.kt:422` 注释说明 72.json 为 orphan 但保留迁移）。

`schema` 目录最高版本为 `73.json`，**无 `74.json`**（`ls` 返回 No such file）。

---

## 二、71/72/73.json 与 `SmbkSelectionPlan` 常量

### 2.1 三个 JSON 的实读值

```text
$ python - <<'PY'（读取 database.version / identityHash / entities 数）
71  version=71  entities=90  hash=714c0ecb85ee5edf74f0013740148138
72  version=72  entities=91  hash=aa5e8e8e6536e0c1f3adcda6867814cb
73  version=73  entities=92  hash=1d086050204a0da1ed6b6e3299dd9733
```

`73.json` 另有 `views` 2 个：`library_catalog`、`knowledge_question_lattice`。

### 2.2 与 `SmbkSelectionPlan` 常量比对

| 计划常量 | 值 | 与 73.json | 位置 |
|---|---|---|---|
| `SCHEMA_VERSION` | 73 | ✅ = `database.version` | `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/backup/SmbkSelectionPlan.kt:54` |
| `IDENTITY_HASH` | `1d086050204a0da1ed6b6e3299dd9733` | ✅ = `database.identityHash` | `SmbkSelectionPlan.kt:64` |
| `CLOSURE_VERSION` | 5 | 无 JSON 对应（包格式版本，写侧常量） | `SmbkSelectionPlan.kt:70` |
| `allTables` | 92 项（76+7+2+7） | ✅ 集合相等 | `SmbkSelectionPlan.kt:213-215` |
| `trimOrder` | 84 项 | ✅ 运行期 `require(trimOrder.size == 84)` | `SmbkSelectionPlan.kt:221-224`、`:581` |
| `views` 断言 | 2 | ✅ = `73.json` 的 2 个 view | `SmbkSelectionPlan.kt:573` |

表名集合的程序化比对（脚本从 `SmbkSelectionPlan.kt` 抽字符串字面量）：

```text
json-not-in-plan = []      # 73.json 的 92 张表全部出现在计划里
plan-not-in-json = [列名若干 + knowledge_question_lattice + library_catalog  # 后者为 view]
```

### 2.3 71/72 的定位

`SmbkSelectionPlan` 只声明 73 一个版本常量（`SCHEMA_VERSION = 73`），71/72 无对应常量，属历史导出物：

- `72.json`（91 表）在代码里只以 **orphan** 身份出现：`SmbkSelectionPlan.kt:18`、`ErrorCauseMigration_72_73.kt:8,15`、`StudyDatabase.kt:422`。与计划 §0“72.json 作 orphan”一致。
- 71/72/73 三份均被 git 跟踪（`git ls-files | grep schemas/.../7[123].json` 三条命中）。

---

## 三、热区快照（feature/*、core/ui、core/export、core/domain、settings.gradle.kts、Application/Root）

命令：`git status --porcelain | grep -v "^??" | grep -E "(^|/)feature/|(^|/)core/ui/|(^|/)core/export/|(^|/)core/domain/|settings\.gradle\.kts|Application\.kt|Root"`

| 文件 | 状态 | 归属 |
|---|---|---|
| `core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/TutorJudgedReview.kt` | M | 他线（批次 40 未提交面） |
| `core/export/src/test/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfEligibilityTest.kt` | M | 他线 |
| `core/export/src/test/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfPagePlannerTest.kt` | M | 他线 |
| `core/export/src/test/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfPlannerGoldenTest.kt` | M | 他线 |
| `core/ui/src/main/java/com/tingyun/smartmistakebook/core/ui/Theme.kt` | M | 他线（UI 会话） |

- 热区 **5 个 M**（2 个源码 + 3 个 core/export 测试），与批次 40 §8 发现 11 的“热区 6 M→5 M”一致。
- `feature/*`：**0 个 M**（只有未跟踪的 `feature/tutor/.../nul` 与 5 个 `gradle.lockfile`）。
- `settings.gradle.kts`：**clean**（`git status --porcelain -- settings.gradle.kts` 空输出）。
- `Application.kt` / `Root*.kt`：**无命中**。
- 索引中无暂存（`git diff --cached --name-only` = 0），本批基线阶段未做 git 写。

---

## 四、本批落点存在性

| 落点 | 期望 | 实读 | 结果 |
|---|---|---|---|
| PDF 包 | `core/data/.../pdf/` | 7 件：`PdfAigcContentPolicy.kt`、`PdfAigcErrorCode.kt`、`PdfAigcInjector.kt`、`PdfAigcReader.kt`、`PdfAigcSchema.kt`、`PdfCrossReference.kt`、`PdfSyntax.kt` | ✅ |
| ErrorCause 三件 | extractor/gate/retractor | `ErrorCauseExtractor.kt`、`ErrorCauseGate.kt`、`ErrorCauseRetractor.kt`（+ model 侧 `ErrorCause.kt`、`ErrorCauseEvidence.kt`） | ✅ |
| ReviewCapacity 五件 | 5 | `ReviewCapacityPolicy.kt`、`ReviewCapacityPreviewReader.kt`、`ReviewCapacityScheduler.kt`、`ExamQuotaPlanner.kt`、`MissedDayRebalancePolicy.kt` | ✅ |
| PaperEdit 三件 | 3 | `core/model/.../PaperEditCommand.kt`、`PaperEditTasks.kt`、`core/data/.../model/PaperEditPrompt.kt` | ✅ |
| tools/split_golden 六件 | 6 个 .py | `check_split_gate.py`、`export_split_eval.py`、`measure_split_accuracy.py`、`rulings.py`、`validate_rulings.py`、`validate_split_golden.py` | ✅ |
| 证据目录 | batch-40 存在 / batch-41 待建 | `docs/research/evidence/2026-11-02-batch-40/`：13 个 md + `rulings/`；`2026-11-03-batch-41/` **不存在** | ✅（基线阶段尚未建，属正常） |

对应的测试件亦在树：`ErrorCauseGateTest.kt`、`ReviewCapacitySchedulerTest.kt`、`SmbkSelectionPlanTest.kt`、`core/database/src/test/.../ErrorCauseSchemaContractTest.kt`。

---

## 五、设备在线状态与可用 AVD

`adb`/`emulator` 不在 PATH，实测走 SDK 绝对路径（`C:\Android\Sdk`，`local.properties` 亦为 `sdk.dir=C:\\Android\\Sdk`）。

### 5.1 在线设备（`adb devices`）

| serial | sdk | release | abi | loadavg(1/5/15) | uptime |
|---|---|---|---|---|---|
| emulator-5562 | 34 | 14 | x86_64 | 0.38/0.50/0.57 | 4d16h |
| emulator-5566 | 34 | 14 | x86_64 | 0.46/0.38/0.36 | 16h39m |
| emulator-5584 | 34 | 14 | x86_64 | 0.03/0.02/0.00 | 14h01m |
| emulator-5596 | 23 | 6.0 | x86_64 | 0.02/0.04/0.05 | 3d13h（`api23_smoke`） |

四台均为 `device` 状态（非 offline/unauthorized），当前负载都低。**本文件未做任何安装/启动/测试**，上述只是静态读数，不构成任何设备门证据。

### 5.2 可用 AVD（`emulator -list-avds`，8 个）

```text
api23_smoke
api23_smoke32
api23_smoke64
audit_user_visible_20261016
kb_test_device
ps16k_arm64
ps16k_x86_64
test_device
```

- `ps16k_arm64` / `ps16k_x86_64` 存在 → WS9 item 52（16KB ps16k）/ item 53（arm64 可用性）具备**尝试入口**；能否真跑未验证（未启动）。
- 在线 4 台里没有 ps16k 的实例（`emulator-5596` 是 `api23_smoke`），如需 16KB 需新起 AVD。

---

## 六、与计划 §0 不符处（逐条）

计划 §0 原文：

> 开批未闭合：KD-75（无设备用例/core:export 零接线）、KD-76（无单轮入口/主口径无 M 值）、isolation 单臂 15/15、closure 4→5 旧包硬拒、`ReviewCapacityScheduler` 总量过载、`ErrorCauseGate` 未查 SELF_REPORT。

### D1（硬冲突）`ReviewCapacityScheduler` 总量过载 —— 当前树已修

计划 §0 与 WS1 item 1 把它当作待修缺陷，但 HEAD 里已实现“单日容量只有一份”：

- `ReviewCapacityScheduler.kt:46-47`：`当日容量只有一份：多科目考试通道与再平衡通道都从这份容量里取，绝不允许各拿整份。`
- `ReviewCapacityScheduler.kt:68-75`：各科应取数之和超容量时用 `ReviewCapacityPolicy.allocateByDayWeights` 按比例分同一份 `dailyCapacity`。
- `ReviewCapacityScheduler.kt:98-117`：再平衡只吃 `dailyCapacity - examPicked.size` 的剩余名额（`itemCap = remainingSlots`）。
- 用例已断总量：`core/data/src/test/.../study/ReviewCapacitySchedulerTest.kt:127` `assertTrue(plan.totalPickedCount <= plan.dailyCapacity)`；`:148` 断无考试时的 `maxDailyCapacity` 上限。
- 提交已在 HEAD：`git log -1 -- <file>` → `1d53cb4d feat(study): WS3 FSRS 容量/考试配额/欠账追赶/预览（6 文件；批次40）`；`git show HEAD:<file> | grep` 直接命中上述行。

结论：WS1 item 1 在 HEAD 上已是绿态。若按计划再改一遍，属于重复实现；本批应先判定“是否只补回归锁”。

### D2（硬冲突）`ErrorCauseGate` 未查 SELF_REPORT —— 当前树已修

- `core/data/src/main/kotlin/.../study/ErrorCauseGate.kt:42-43`：
  `if (candidate.evidence.channel == ErrorCauseChannel.SELF_REPORT) return false`
- 用例：`core/data/src/test/.../study/ErrorCauseGateTest.kt:103-105`（“SELF_REPORT 只能调置信、不能单独定类（R2 §3.6）”）。
- 同样在 HEAD：`git log -1 -- <file>` → `e89476ea feat(study): WS2 错因候选提取/D-1 落账门/AUD-11 撤销（6 文件；批次40）`。

结论：WS1 item 4 亦为已修。批次 40 §8 发现 14（“门缺口保留 open”）是**复核时点读数**，已被同批后续提交覆盖；§12.4 的“下一批首要”清单因此滞后。

### D3（口径补充）WS1 item 2 的机制已具备

`ExamQuotaPlanner` 已有 `quotaCap`（`ExamQuotaPlanner.kt:24,37,45,81`），`MissedDayRebalancePolicy` 已有 `itemCap`（由 `ReviewCapacityScheduler.kt:113` 传入）。是否满足“合并去重后总量口径”需看实现方补充断言，但**不是从零新建**。

### D4（与 §0 一致，登记为真缺口）WS1 item 3 仍开放

`ReviewCapacityPreview` 字段只有 `examSubjectsInWindow / examDeficitCount / examUnmetCount / rebalanceBacklogCount / rebalanceIneligibleCount / deduplicatedCount / intakeUnschedulableCount`（`ReviewCapacityPreviewReader.kt:150-158`），**没有逐科分解、也没有总量题数**。计划 item 3 的落点是真实缺口。

### D5（转录口径）计划 §0 的“单测门 3 红”是 HEAD 时点值，当前工作树已不是那个状态

计划 §0 的 3 例红为 `ProductionLexicalLegExportTest` / `Stage1LexicalLabTest` / `Stage2LexicalScoresExportTest`（`core/data/src/test/.../knowledge/`）。这 3 个文件**当前都在工作树 M 集里**，由 KB 线重钉了期望值（例：`Stage1LexicalLabTest.kt` 把 `0.15930232558139526` 改为 `0.16072351421188627`，容差 `1e-4` 未动，`git diff` 可见）。因此：

- “3 红”描述的是 **HEAD**，不是当前工作树；本批跑单测门会得到与计划不同的红/绿集合（结果未知，本轮未跑）。
- 这属于他线的未提交改动，不在本批落点内；**不得**据此改断言或宣称变绿。

### D6（转录口径）单测门/强门结果本轮未复现

计划 §0 的“单测门 exit=1（仅 3 例 KB 词面红）”与“强门 exit=0”来自批次 40 主会话（记录 §12.1）。本轮按纪律不跑 Gradle，**未验证**，不得当作本批基线事实引用。

### 与 §0 一致的项（已实测）

| §0 声称 | 实测 | 证据 |
|---|---|---|
| HEAD = origin/main = `62522930` | ✅ | `git rev-parse` / `git ls-remote` |
| schema 73、92 表、hash `1d086050…` | ✅ | `73.json` 实读 |
| 备份清单 73/92/84/CLOSURE 5 | ✅ | `SmbkSelectionPlan.kt:54,64,70,215,224` |
| KD-75：无设备用例、core:export 零接线 | ✅ | `grep -rln "PdfAigc\|AIGC" app/src/androidTest/` → 空；`grep -rln PdfAigc core/export/src/` → 空 |
| KD-76：无单轮入口 | ✅ | 全仓 `grep "smb.kd76"` → 空；`VisualJsConnectStageCalibrationInstrumentedTest.kt:133` 单个 `@Test`、`:912` `CALIBRATION_ROUNDS = 20` 进程内 20 轮 |
| isolation 单臂 15/15 | ✅ | `VisualJsIsolationInstrumentedTest.kt` `@Test` 计数 = 15 |
| closure 4→5 旧包硬拒 | ✅ | `SmbkArchiveCodec.kt:444-446`（写侧 require）、`:1162-1166`（读侧拒绝） |

---

## 七、UNVERIFIED（本轮未做，不得写通过）

1. 任何 Gradle 编译 / 单测门 / 强门 / androidTest 编译（本轮零 Gradle）。
2. 任何设备安装、启动、instrumentation 运行；四台在线设备只做了 `getprop` / `loadavg` 静态读。
3. `ps16k_arm64` / `ps16k_x86_64` 的实际可启动性与 16KB 页行为。
4. 计划 §0 的“3 例 KB 红”在当前工作树上的真实红集（文件已被他线修改）。
5. 备份包读写、closure 兼容读侧行为（只读了判定代码，未执行）。
6. `SmbkSelectionPlanTest` / `ReviewCapacitySchedulerTest` 等用例的实跑结果（未跑 JVM）。
7. 417 个未跟踪文件里是否有本批需要的落点（本轮只按 §四清单逐一 `ls`，未全量归类）。

---

## 八、给实施阶段的落点提示（不改代码，仅登记）

1. WS1 item 1 / item 4 的目标行为在 HEAD 已成立（D1/D2）。先确认是否只补“回归锁”或新增边界用例，避免重复实现与重复计件。
2. WS1 item 3（`ReviewCapacityPreview` 逐科 + 总量）是唯一被证实的容量侧缺口（D4）。
3. 热区当前 5 M 全为他线，`feature/*`、`settings.gradle.kts`、`Application/Root` 干净；本批若要动 `core/export` 只能出 patch 草案（KD-75 item 17 也是这个口径）。
4. 设备侧：`emulator-5584`（loadavg 0.03）最适合做低负载窗候选；`ps16k_*` 需另起 AVD，且需先确认 arm64 镜像可用（未验证）。
5. 工作树里 14 个 `gradle.lockfile` 与 `feature/tutor/.../nul` 属禁止提交项，提交前逐路径核对。

---

## 九、本轮执行的命令清单（可重跑）

```bash
git rev-parse HEAD
git ls-remote origin main
git status --porcelain | wc -l
git status --porcelain | grep -c "^??"
git diff --cached --name-only | wc -l
git status --porcelain | grep -v "^??" | cut -c1-2 | sort | uniq -c
git status --porcelain | grep -v "^??" | grep -E "(^|/)feature/|(^|/)core/ui/|(^|/)core/export/|(^|/)core/domain/|settings\.gradle\.kts|Application\.kt|Root"
git ls-files | grep -E "schemas/.*/(7[123])\.json"
python -c "读 71/72/73.json 的 version/identityHash/entities"
python -c "抽取 SmbkSelectionPlan.kt 表名字面量并与 73.json 实体集合比对"
grep -rn "SELF_REPORT" core/data/src/main/kotlin/.../study/ErrorCauseGate.kt
grep -n "quotaCap" core/data/src/main/kotlin/.../study/ExamQuotaPlanner.kt
grep -n "closureVersion" core/data/src/main/kotlin/.../backup/SmbkArchiveCodec.kt
grep -rln "PdfAigc\|AIGC" app/src/androidTest/ ; grep -rln PdfAigc core/export/src/
grep -rn "smb.kd76" --include=*.kt .
"C:/Android/Sdk/platform-tools/adb.exe" devices
"C:/Android/Sdk/emulator/emulator.exe" -list-avds
```

（`git log -1 -- <path>`、`git show HEAD:<path> | grep` 用于确认修复已在 HEAD 而非仅工作树。）
