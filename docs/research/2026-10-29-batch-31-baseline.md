# 批次 31 基线核对（2026-10-29，基线核对员）

> **只读核对**：本轮未运行 Gradle、未编译、未执行任何 JVM/仪器化/设备用例、未 `git fetch`、未做任何 git 写操作（本文件是唯一写盘产物）。设备面只执行只读 `adb devices -l` / `adb -s <serial> emu avd name` / `adb -s <serial> shell getprop` / `getconf PAGE_SIZE`。对照对象：`.zcode/plans/plan-2026-10-29-batch-31-persistence-hardening.md` §0（`:5-10`，下称"计划"）。
>
> **快照时点**：2026-10-29，`git status --porcelain` 冻结 **690 行（218 ` M` + 472 `??`）**；**暂存区 0 条**（`git diff --cached --name-only | wc -l` = 0）。
>
> "干净"定义：`git status --porcelain -- <path>` 无输出（不代表内容无缺陷）。

---

## 1. HEAD / 引用 / 工作树 / DB schema / 请求 schema（工单第 1 项）

| 项 | 实测 | 证据（命令/路径行号） |
|---|---|---|
| HEAD | `59d4a03f92ab27314d25384a777f41829b3421a2`（`main`） | `git rev-parse HEAD` |
| origin/main | `59d4a03f92ab27314d25384a777f41829b3421a2`（**与 HEAD 相同**） | `git rev-parse origin/main`；**本轮未 `git fetch`**，为本地远端跟踪引用，远端实时 hash 未核（UNVERIFIED） |
| 分支跟踪 | `* main 59d4a03f [origin/main]`（无 ahead/behind 标记） | `git branch -vv` |
| HEAD 构成 | `59d4a03f` 台账/总路线/决策；父 `31146e44` 证据后半；再父 `7090bd9c` 设备证据前半、`e1a8331e` 研究、`c2a17d29` 用例 | `git log --oneline -5` 实读 |
| **与计划 §0:5 对照** | 计划写"HEAD `59d4a03f`"；**一致，无偏差** | 同上 |
| 工作树（冻结） | **218 ` M` + 472 `??` = 690 行；staged = 0** | `git status --porcelain \| wc -l`=690；`awk` 计数 ` M`=218、`??`=472；`git diff --cached --name-only \| wc -l`=0 |
| M 按顶层分组 | feature **96**、docs **43**、core **43**、app **23**、tools **9**、`settings.gradle.kts` 1、`knowledge-production` 1、`dynamic_gui_architecture_plan.md` 1、`.gitignore` 1 = **218** | `git status --porcelain \| grep '^ M' \| awk … \| cut -d/ -f1 \| sort \| uniq -c` |
| ?? 按顶层分组（前 5） | feature 38、core 30、docs 19、tools 12、knowledge-production 11（余为 app 5、临时文件/输出/x 等）= **472** | `git status --porcelain \| grep '^??' …` |
| DB schema | `internal const val STUDY_DATABASE_VERSION = 70`（`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabase.kt:139`）；导出目录 **70 个 json（1..70，最高 70.json）**，且 `git ls-files core/database/schemas \| wc -l` = **70**（无缺失版本） | `grep -n STUDY_DATABASE_VERSION`；`python` 解析 70.json 得 `database.version=70`；`git ls-files \| wc -l` |
| 请求 schema | `TUTOR_VISUAL_CONTEXT_SCHEMA_VERSION = 18`、`CURRENT_SCHEMA_VERSION = TUTOR_VISUAL_CONTEXT_SCHEMA_VERSION`（`core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/ModelTasks.kt:546-547`）；`core/model` 仅 `?? core/model/gradle.lockfile`，`ModelTasks.kt` 自身干净 | `sed -n '544,548p'` + `git status --porcelain core/model` |
| 规划 vals | 计划 §0:7 "schema 70 已落并推送"、§0 未提请求 schema；实测 DB 70 / 请求 18 | 同上 |
| 与批次 30 基线的增量 | 批次 30 基线（`docs/research/2026-10-29-batch-30-baseline.md:7`）为 673 行（201 M + 472 ??）；本批 **+17 M**，`??` 数不变 | 两文件对照 |

**结论**：HEAD 与本地 `origin/main` 一致；"DB schema 70"与"请求 schema 18"均与实测相符。

---

## 2. 热区快照 + core/data、core/database 开工即脏清单（工单第 2 项）

**前言（口径）**：批次 31 计划**没有枚举热区清单**（`grep -n "热区\|HOT_ZONES" .zcode/plans/plan-2026-10-29-batch-31-persistence-hardening.md` 仅命中 `:37` 的"热区禁改"一句，无列表）。因此本节沿用批次 30 基线 §2 所用清单（`docs/research/2026-10-29-batch-30-baseline.md:35-47`，其来源是批次 30 计划 §0:10 清单），**这是沿用口径，不是本批计划原文**。

| 热区项 | 当前实测（total = M + ??） | 批次 30 基线值 | 判定 |
|---|---|---|---|
| feature/tutor | **59**（37 M + 22 ??） | 37 M | ✓ 脏 |
| feature/library | **43**（30 M + 13 ??） | 30 M | ✓ 脏 |
| feature/capture | **16**（15 M + 1 ??） | 15 M | ✓ 脏 |
| feature/review | **12**（11 M + 1 ??） | 11 M | ✓ 脏 |
| feature/profile | **4**（3 M + 1 ??） | 3 M | ✓ 脏 |
| core/ui | **35**（11 M + 24 ??） | 11 M + 24 ?? | ✓ 脏 |
| core/markdown | **1**（`?? core/markdown/` 整目录未跟踪） | 整目录未跟踪 | ✓ |
| core/export | **6**（5 M + 1 ??） | 5 M | ✓ |
| core/domain | **5**（4 M + 1 ??） | 4 M | ✓ |
| settings.gradle.kts | **M**（1） | M | ✓ |
| `SmartMistakeBookRoot.kt` | **M** | M | ✓ |
| `SecondaryScreens.kt` | **M** | M | ✓ |

命令：`for p in …; do git status --porcelain -- "$p" | wc -l; done`（逐项 total/M/??，见上表）。

计划清单外同脏：core/data **14**、core/database **11**、app/src **23 M + 5 ??**、docs **43 M + 19 ??**、tools **9 M + 12 ??**。

### 2.1 core/data 开工即脏（14 条 = 13 M + 1 ??）

命令：`git status --porcelain -- core/data core/database`（原样输出 25 行）。

**M（13）：**
1. `core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/backup/BackupRestoreInstrumentedTest.kt`
2. `core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/backup/DeviceMigrationDrillInstrumentedTest.kt`
3. `core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/capture/BatchImportRepositoryInstrumentedTest.kt`
4. `core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/capture/SplitImportCommitReceiptInstrumentedTest.kt`
5. `core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/capture/SplitImportReceiptReconciliationInstrumentedTest.kt`
6. `core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/model/ModelTaskRepositoryInstrumentedTest.kt`
7. `core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/study/StudySubmissionWithoutFixtureInstrumentedTest.kt`
8. `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/mistake/RoomMistakeDetailRepository.kt`
9. `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/RoomTutorToolRunner.kt`
10. `core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/GoldenRetrievalJvmTest.kt`
11. `core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/model/OpenAiNativeToolsProtocolTest.kt`
12. `core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/model/TutorToolRequestDualParseTest.kt`
13. `core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualDrawOpsParserTest.kt`

**??（1）：** `core/data/gradle.lockfile`（未跟踪锁，**不得入库**）。

### 2.2 core/database 开工即脏（11 条 = 10 M + 1 ??）

**M（10）：**
1. `core/database/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/database/LibraryFtsCountPathInstrumentedTest.kt`
2. `core/database/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/database/LibraryFtsCountPathScale50kInstrumentedTest.kt`
3. `core/database/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/database/LibraryFtsRankingPathInstrumentedTest.kt`
4. `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/RoomBatchImportStore.kt`
5. `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/TutorConversationDatabaseContract.kt`
6. `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/dao/BatchImportDao.kt`
7. `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/dao/PendingCaptureDao.kt`
8. `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/dao/TutorConversationDao.kt`
9. `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/port/CleanRedrawTaskPort.kt`
10. `core/database/src/test/kotlin/com/tingyun/smartmistakebook/core/database/KernelWave0SchemaContractTest.kt`

**??（1）：** `core/database/gradle.lockfile`（未跟踪锁，不得入库）。

### 2.3 关键：批次 30 落点文件本身全部干净

`git status --porcelain --` 对下列路径**全部无输出**（即在 HEAD 内，非工作树改动）：
`core/database/schemas/`、`StudyDatabase.kt`、`VisualSnapshotMigration_69_70.kt`、`TutorVisualIdentity.kt`、`TutorVisualCommit.kt`、`entity/TutorVisualEntities.kt`、`dao/TutorVisualDao.kt`、`RoomStudyDatabase.kt`、`core/data/src/main/.../visual/`（整包）、`core/data/src/main/.../backup/`（SmbkSelectionPlan/BackupSnapshotTrimmer）、`TutorVisualSchemaContractTest.kt`、`VisualDrawOpsParityMetrics.kt`、`VisualDrawOpsParityInstrumentedTest.kt`、`TutorVisualSnapshotMigration_69_70InstrumentedTest.kt`、`core/data/src/androidTest/.../visual/`、`app/src/androidTest/.../visualjs/`。

**唯二例外（且都属"批次 30 修复未提交"）**：`BackupRestoreInstrumentedTest.kt`（M，5 增 5 删，见 §3.7）与 `KernelWave0SchemaContractTest.kt`（M，版本字面量 69→70，见 §6.3）。

**对计划 §2:37 的含义**：schema/migration 单写者（任务 6 轨）的目标文件 `StudyDatabase.kt`、`schemas/70.json` 等**开工即干净**，无预存脏冲突；但它们当前是 70，任务 6 要升 71 必须自己改（本批唯一 schema 写者）。

---

## 3. 批次 30 落点在树核对（工单第 3 项）

### 3.1 四表与迁移 69→70 —— **在树成立**

| 载体 | 证据 |
|---|---|
| 实体（4 张） | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/entity/TutorVisualEntities.kt`：`:37` `tableName="tutor_visual_program"`、`:105` `tutor_visual_snapshot`、`:208` `tutor_message_visual`、`:274` `problem_revision_visual` |
| @Database 注册 | `StudyDatabase.kt:237-240`（`TutorVisualProgramEntity` / `TutorVisualSnapshotEntity` / `TutorMessageVisualEntity` / `ProblemRevisionVisualEntity` 四行） |
| 迁移对象 | `VisualSnapshotMigration_69_70.kt:35-54`：`object : Migration(69, 70)`，顺序执行 **15 条 DDL（4 表 + 11 索引）**（`:38-52`） |
| 迁移注册 | `StudyDatabase.kt:409` `VISUAL_SNAPSHOT_MIGRATION_69_70,` |
| 导出 | `core/database/schemas/…/70.json`：`database.version=70`、`identityHash=11a8a701bae767a8a3c5bc810e4743a4`、`entities=90`；四表索引数 **3/4/2/2 = 11**（python 实读），与迁移 DDL 数一致 |
| 69→70 差异 | python 对照：69 有 **86** 实体、70 有 **90**；新增恰为四表，无删除；69 `identityHash=6633b7c2fe223041c4a2927203853f4f` |

**注（文档陈旧）**：`VisualSnapshotMigration_69_70.kt:26-33` 仍写"`schemas/70.json` 是构建产物，本轮没有生成…占位说明见 `70.json.NOT_EXPORTED.md`"，`TutorVisualSchemaContractTest.kt:33-35` 同句。实测 `ls … | grep -i NOT_EXPORTED` **无此文件**（exit=1），`70.json` 已跟踪（HEAD blob `7deb6300`）。两处 KDoc 未回改（不影响代码，但会误导"尚未导出"）。

### 3.2 `SmbkSelectionPlan` 的 70/90 常量 —— **成立**

- `SCHEMA_VERSION: Int = 70`（`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/backup/SmbkSelectionPlan.kt:40`）
- `IDENTITY_HASH = "11a8a701bae767a8a3c5bc810e4743a4"`（`:43`）—— 与 70.json `identityHash` **逐字相同**
- `require(allTables.size == 90)`（`:538`）；`require(softReferences.size == 21)`（`:554`）；`CLOSURE_VERSION = 3`（`:46`）
- 表归属实数（解析 Kotlin 列表并比对 70.json，命令为 python 脚本 + 70.json 实读）：类别 **74**（31+35+7+1）+ ALWAYS **7** + DERIVED **2** + SPLIT **7** = **90**，**无重复、无缺失**；与 70.json 的 90 个 `tableName` **双向差集皆空**
- 视图 2 项（`library_catalog`、`knowledge_question_lattice`）单列 `views`，与 70.json `database.views` 同名

### 3.3 `BackupSnapshotTrimmer` 版本门 —— **成立（跟随常量）**

`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/backup/BackupSnapshotTrimmer.kt`：
- `:268-271`：`if (database.version != SmbkSelectionPlan.SCHEMA_VERSION) fail("快照 schema=… 裁剪器只支持 schema=…")` —— **不再写死 69**
- `:273-283`：`SELECT identity_hash FROM room_master_table WHERE id = 42` 与 `SmbkSelectionPlan.IDENTITY_HASH` 比对
- `:248` 写入 `schemaVersion = database.version`；`:109` 走 `SmbkSelectionPlan.requireCreatableMask(mask)`
- 文件本身**干净**（HEAD 内）

### 3.4 69→70 测试的 DDL 断言行 —— **修复已在树**

`core/database/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/database/TutorVisualSnapshotMigration_69_70InstrumentedTest.kt:206-225`：
> "SQLite 存进 sqlite_master 的 DDL 已去掉 `IF NOT EXISTS`（设备实测），所以不能用 `substringBefore(" IF NOT EXISTS")` 当前缀比——那会把整条 DDL 留下…（批次 30 设备红因）"

现断言 = 索引存在（`:211-214`）+ `startsWith("CREATE UNIQUE INDEX")`（`:215-218`）+ `contains("(`program_id`, `state_sha256`, `draw_commands_sha256`)")`（`:219-225`）。**断言语义未放宽**（仍是存在 + UNIQUE + 三列）。

套件共 **2 例**（`grep -c "@Test"` = 2）：`visualTablesAreCreatedEmptyWithoutTouchingV69RowsAndIdempotent`（`:38`，此前红例）与 `onlyLiveSnapshotsCanBeLinkedAndCasUsesChangedRowCounts`（`:74`）。批次 30 设备实测该套件 `Tests run: 2, Failures: 1`（`docs/research/evidence/2026-10-29-batch-30/step07-persistence-migration.md:42`），**另一例在红轮中是过的**，红的就是 `:38` 那例、首项目帧 `:206`（同文件 `:59-65`）。红例之后的 `assertV70ForeignKeyBehaviour`（`:55` 调用、`:307` 定义）与旧行保持断言（`:258-299`）现在**在代码路径上可达**——但修复后的"执行到"属运行证据，本基线**未跑**（UNVERIFIED）。

### 3.5 checkpoint 保留策略的生产调用者 —— **仓库层已接线，生产调用点仍为 0**

- 纯策略：`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualCheckpointRetention.kt`（`nextCheckpointKind:123`、`plan:137`）
- 生产接线（代码在 HEAD）：`TutorVisualCommitRepository.kt:327-373` `planCheckpoint` → 拒绝映射为 `TutorVisualCommitRejectedException`（`:361-371`）；`stageSnapshot` 用它（`:191-198`）、`commitAssistantTurnWithVisual` 用它并传 demote/evict（`:233-252`）
- 事务内落点：`RoomStudyDatabase.kt:2023-2039`（`demoteLatestCheckpoints` / `evictUnpinnedCheckpoints` 在 `withWriteTransaction` 内）
- SQL：`dao/TutorVisualDao.kt:138-151`（demote 只改 `checkpoint_kind`、WHERE 写死 `LATEST`）、`:158-176`（evict 重查 pin + kind）
- **调用者 grep**：`grep -rn "TutorVisualCommitRepository(" --include=*.kt .` → 仅 `TutorVisualCommitRepository.kt:152`（定义）与 `TutorVisualCommitRepositoryInstrumentedTest.kt:39/80/120`（**androidTest**）；`grep -rn "stageSnapshot(\|commitAssistantTurnWithVisual(\|repointMessageVisual(" app feature core/data/src/main core/database/src/main` → 仅定义与 `RoomStudyDatabase`/DAO 实现，**app/feature 零调用点**
- 同理 `TutorVisualPort` 只被 `TutorVisualCommitRepository` 消费（grep 结果）

→ 计划 §0:10 "checkpoint 保留策略零生产调用" **在"app/feature 无调用者"意义上成立**；但 **"策略未接线"不成立**（仓库层与事务 SQL 都已接线）。

### 3.6 link CAS SQL / 题面 link 插入 —— **计划所称的两处漏洞在树已被修**

- 指针 CAS：`dao/TutorVisualDao.kt:328-351`
  - `AND revision = :expectedRevision`
  - `AND EXISTS (SELECT 1 FROM tutor_visual_snapshot AS snapshot WHERE snapshot.snapshot_id = :nextSnapshotId AND snapshot.status = :liveStatus AND snapshot.program_id = tutor_message_visual.program_id)` —— **同 program 校验已存在**（`:336-341`），KDoc `:318-327` 明写"批次 30 复核修复"
- 题面 link 插入：`dao/TutorVisualDao.kt:407-428` `INSERT … SELECT … WHERE EXISTS (… status = :liveStatus AND snapshot.program_id = :programId)` —— **STAGING 已写不进**（`:412-417`），KDoc `:399-406` 明写"批次 30 复核修复前这里是普通 @Insert(ABORT)"
- 两条都经 `@Transaction` 读回判定（`linkMessageVisual:359-397`、`pinRevisionVisual:449-482`）

### 3.7 snapshotId 派生调用点 —— **仓库层派生，DB 层未重派生/未比对**

- 派生函数：`TutorVisualIdentity.kt:71` `deriveSnapshotId(programId, stateSha256, drawCommandsSha256)`（哈希只含三者）
- 调用点（`grep -rn "deriveSnapshotId\|snapshotIdFor"`，排除定义）：
  - **生产**：`TutorVisualCommitRepository.kt:169-176`（`snapshotIdFor`）、`:399-403`（`toWrite` 内派生）
  - 测试：`TutorVisualCommitRepositoryInstrumentedTest.kt:48`、`TutorVisualIdentityTest.kt:76-87`
- DB 层：`RoomStudyDatabase.kt:2118-2130` `ensureTutorVisualProgram` **重新派生 programId 并 `require(write.programId == derivedProgramId)`**；但 `:2151-2171` `ensureTutorVisualSnapshot` **只按内容三元组查重/插入，未重派生 `write.snapshotId` 比对**（`insertSnapshot(write.toEntity(...))` 直接用传入 id）
→ 计划 §0:10 "snapshotId 不重新派生"在 **DB 层成立**；仓库层已派生（任务 5 只需补 DB 层门）。

### 3.8 内容唯一键 vs 渲染版本 —— **与计划前提相反：批次 30 复核修复已把版本移出身份语义**

- 唯一索引：`entity/TutorVisualEntities.kt:121` `Index(value = ["program_id", "state_sha256", "draw_commands_sha256"], unique = true)`；迁移 DDL 同列（`VisualSnapshotMigration_69_70.kt:111-114`）
- 载荷比对：`TutorVisualCommit.kt:331-337` 明写"**环境版本列不参与比较**（批次 30 复核修复）：`runtime_version`/`compiler_version`/`renderer_version`/`font_manifest_version` …此前把它们并入载荷比对，导致同一 program + state + draw 在升级后再次提交时命中既有行却因版本不同抛 `ImmutablePayloadConflictException`…两套口径只能留一套：身份 = 唯一键 = 内容三列"
→ 计划 §0:10 "painter 升级后**无法**持久化同内容" **在当前树上不成立**（该失败已被消除）。计划任务 6 要把版本并入身份并生成**新快照**，是**反向改写** HEAD 内刚落地的裁定（见 §8-D5）。

### 3.9 LIVE LATEST 进淘汰候选 —— **成立（未修）**

`dao/TutorVisualDao.kt:158-176` `evictUnpinnedCheckpoints` 的 WHERE 只有 `snapshot_id IN (…)` + `(checkpoint_kind IS NULL OR = :latestKind)` + 两个 `NOT EXISTS`（消息/题面引用），**没有 `status` 过滤**；候选来源 `checkpointSummaries`（`:100-120`）同样不过滤 status。→ LIVE 且未被固定、kind 为 LATEST 的行可被淘汰。

### 3.10 备份绿轮前提（KD-80 修复）在树但**未提交**

`BackupRestoreInstrumentedTest.kt` 工作树 diff（`git diff`，5 增 5 删）：用例改名为 `schema68ArchiveRestoresAndMigratesToCurrentVersion`（`:488`），期望值 `69` → `SmbkSelectionPlan.SCHEMA_VERSION`（`:590`）。该文件**仍是 ` M`**（未提交）。`BackupRestoreInstrumentedTest` 共 **34 例**（`grep -c "@Test"` = 34），与计划 34/34 口径一致。

---

## 4. 一致门阈值与 P07 归因（工单第 4 项）

### 4.1 常量（现树原值，**未改动**）

`core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualDrawOpsParityMetrics.kt`：

| 项 | 值 | 行 |
|---|---|---|
| `MAX_TEXT_BBOX_DEVIATION_PX` | `1` | `:847` |
| `MAX_TEXT_CENTROID_DELTA_PX` | `0.5` | `:848` |
| `MIN_TEXT_MASS_RATIO` / `MAX_TEXT_MASS_RATIO` | `0.95` / `1.05` | `:849-850` |
| `MAX_TEXT_PROFILE_TV` | `0.08` | `:851` |
| `THRESHOLD_VERSION` | `"R2-2026-10-28/§4.2-initial-uncalibrated"` | `:185` |

计划任务 7 要求改记 `calibrated-r1` —— **当前树仍是 `initial-uncalibrated`，未改**。

### 4.2 判决优先级（任务 7 要求"F2/F3 先于 F4"——**未改**）

- 文本判决 `rawTextVerdict`（`:549-580`）：先 bbox/centroid 越界 → `F4_GEOMETRY`（`:556-563`），之后才是 mass/TV → `F1_FONT`（`:564-572`）、AA → `F2_AA`（`:573-578`）。**几何在 AA 之前**，与计划"改为 F2/F3 先于 F4"相反（即计划要改的那一版尚未落码）。
- 非文本判决 `:520-546` 顺序为 F4_COLOR → F2_AA → F3_ALPHA_ROUNDING → F4_SEMANTIC。

### 4.3 设备读数出处（一手）

- 证据文档：`docs/research/evidence/2026-10-29-batch-30/step05-parity.md:43`（`P07 must not be red: F4_GEOMETRY reasons=[text bboxDev=1px centroid=0.5181329158118464px]`）、`:51`（复跑同值、`maskS=1224 maskP=1431 union=1442`）、`:88-91`（批次 29 AA 开 vs 批次 30 AA 关对照）
- **一手 logcat**（本轮实读）：`build/device-track-batch30/step05-parity-logcat.txt:121-122`
  - `:121` P07 `iou=0.8412 … maskS=1224 maskP=1431 union=1442 alphaMismatch=0`
  - `:122` P07 `text=bboxDev=1 centroid=0.52 mass=0.957 tvColumn=0.032 tvRow=0.042 verdict=F4_GEOMETRY`
  - 同文件 P06 `text=bboxDev=1 centroid=0.32 … verdict=F2_AA`
- 掩膜数（mask）与质心（centroid）均出自该 logcat 行；`0.5181` 是 `0.52` 的未截断值（证据文档给全精度）
- 计划 §0:8 "CJK 质心 0.518 > 阈值 0.5" —— **与实测一致**

**注意**：该 logcat 在 `build/`（`.gitignore` 忽略），属本机原始日志，**不是仓内证据**；仓内证据是 `docs/research/evidence/2026-10-29-batch-30/step05-parity.md`。

---

## 5. KD-71/72/73/77/80 原文与当前状态（工单第 5 项）

| KD | 位置 | 标题状态 | 当前实质状态（按原文最后一块） |
|---|---|---|---|
| **KD-71** | `docs/known-defects.md:2727` | `open`（低）QuickJs close/execute 竞态 | 修复已落码（单锁状态机 + 有界等待 + 测试缝 `beforeRegistrationHookForTest`）；**本批新增 JVM 用例未执行**，设备证据仍是批次 23/28 旧轮 → **open** |
| **KD-72** | `docs/known-defects.md:2750` | `open`（中）dependency locking 无已跟踪锁 | 批次 30 T7 复核"仍不可行、零动作"：W1 六文件 6/6 不干净、W2 树未收口、W3 staged=0、W4 跟踪锁 0 / 盘上 14、W5 `:benchmark` 无锁、W6 负控未做、W7 Gradle 未跑 → **open** |
| **KD-73** | `docs/known-defects.md:2776` | `open`（中）kotlin-stdlib 降回 2.3.21 | 批次 30 T7 自述做了 10.3 万 class 静态全图扫描（138,225 条引用 2.3.21 全可解析），但**扫描器/日志/结果未落仓 → UNVERIFIED**；设备/R8 冒烟未做 → **open** |
| **KD-77** | `docs/known-defects.md:2875` | `open`（中）API 23 未验证 | 批次 30 T7 自述已装 `platforms;android-23` + 镜像并跑旧 APK 冒烟（冷启动 2.857 s、四类崩溃 0），但**冒烟 logcat 未落仓 → 未复核**；关闭需**修复后 APK** 重跑并落盘 → **open** |
| **KD-80** | `docs/known-defects.md:2955` | `open`（高）schema 70 后备份清单停在 69 | 修复已落树（SmbkSelectionPlan 70/90/21/CLOSURE 3、trimmer 跟随常量、测试改名+常量期望），但**34/34 设备绿轮未跑** → **open** |
| 旁证 | KD-78 `:2889`（open，④ 已关）；KD-79 `:2939`（**closed** 2026-10-29） | — | 与计划 §0:9 "KD-78④/KD-79 已按设备条件关闭" 一致 |

**核准**：计划 §0:9 "KD-71/72/73/77 open" 与 "KD-80 已登记" 全部与台账原文相符。

---

## 6. `70.json` 跟踪 / identityHash / 实体数 / 迁移矩阵（工单第 6 项）

### 6.1 70.json

| 项 | 实测 | 命令 |
|---|---|---|
| 是否跟踪 | **是**：`git ls-tree HEAD core/database/schemas/…/70.json` → `100644 blob 7deb6300487c0fb4fa537893b7e334405031af79`（`git ls-files --error-unmatch` 亦成功） | 实跑 |
| `database.version` | **70** | python `json.load` |
| `identityHash` | **`11a8a701bae767a8a3c5bc810e4743a4`** —— 与 `SmbkSelectionPlan.IDENTITY_HASH`（`:43`）逐字相同 | python |
| 实体数 | **90**（`database.entities` 长度） | python |
| 视图 | `knowledge_question_lattice`、`library_catalog` | python |
| 文件大小/mtime | 378,885 B / 2026-10-29 02:05:31 | `ls -l` |

### 6.2 迁移矩阵覆盖 1..70 —— **成立**

`core/database/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/database/FullMigrationMatrixInstrumentedTest.kt:38`：
```kotlin
for (version in 1..STUDY_DATABASE_VERSION) {
```
`STUDY_DATABASE_VERSION = 70` ⇒ **1..70 全覆盖**（含 70 自身：从 70.json 建库后打开为 no-op）。该文件共 **15 个 `@Test`**（1 个矩阵循环 + 14 个带数据的专项迁移用例）。

### 6.3 HEAD 上的 JVM 契约测试字面量仍是 69（重要）

- HEAD 版 `KernelWave0SchemaContractTest.kt`（`git show HEAD:…`）`:52` 仍是 `69,`，断言 `assertEquals("…下一次 bump 请同步本字面量", 69, STUDY_DATABASE_VERSION)`
- 而同 HEAD 的 `STUDY_DATABASE_VERSION` 已是 **70**（`StudyDatabase.kt:139`，该文件干净）
- ⇒ **在 HEAD 原样构建时，该 JVM 用例必红**；工作树 diff（`:47-53`）已把字面量与文案改成 70（**未提交**）
- 另一处断言 `assertEquals(latestExported, STUDY_DATABASE_VERSION)`（`:38-42`）因 70.json 已跟踪而满足

---

## 7. 锁窗口与设备在线状态（只读，工单第 7 项）

### 7.1 锁窗口（KD-72 W1–W7 现状）

| 项 | 实测 | 命令 |
|---|---|---|
| 树是否收口 | **否**：690 行（218 M + 472 ??） | `git status --porcelain \| wc -l` |
| 暂存区 | **0** | `git diff --cached --name-only \| wc -l` |
| 跟踪锁 | **0** | `git ls-files '*lockfile' \| wc -l` |
| 盘上 `*.lockfile` | **14** | `find . -maxdepth 3 -name "*.lockfile" -not -path "./.worktrees/*" \| wc -l` |
| git 可见未跟踪锁条目 | **13**（`core/markdown/gradle.lockfile` 被折叠进 `?? core/markdown/`） | `git status --porcelain \| grep -i lockfile` |
| W1 六文件 | `settings.gradle.kts` **M**、`core/ui/build.gradle.kts` **M**、`feature/tutor/build.gradle.kts` **M**、`core/export/build.gradle.kts` **M**、`.gitignore` **M**、`core/markdown/build.gradle.kts` **??** → **6/6 不干净** | 逐文件 `git status --porcelain --` |
| `:benchmark` 锁 | 不存在（不在 find 结果） | 同上 |

**判定：KD-72 的"干净树重生成锁"窗口仍不满足，零动作**（与批次 30 T7 结论一致）。

### 7.2 设备在线（只读）

`/c/Android/Sdk/platform-tools/adb.exe devices -l`（`adb` 不在 Git Bash PATH，用 SDK 绝对路径）：

| serial | 状态 | AVD 名 | API | ABI | PAGE_SIZE |
|---|---|---|---|---|---|
| emulator-5562 | `device` | `test_device` | 34 | x86_64 | 4096 |
| emulator-5566 | `device` | `audit_full_ui_forest_20261028` | 34 | x86_64 | 4096 |
| emulator-5584 | `device` | `kb_test_device` | 34 | x86_64 | 4096 |

命令：`adb -s <serial> emu avd name`、`shell getprop ro.build.version.sdk`、`shell getprop ro.product.cpu.abi`、`shell getconf PAGE_SIZE`。**三台均在线**；批次 30 设备证据用的 `emulator-5562/test_device` 仍在线且规格一致（API 34 / x86_64 / 4096）。

---

## 8. 与计划 §0 不符处（本工单要的差异清单）

> 口径：**"不符"= 计划 §0 的断言与当前树/实测不一致**。§0 是对批次 30 收口的描述，部分描述已被批次 30 复核修复轮的提交（HEAD `59d4a03f` 及其父提交）超越。

| # | 计划原文 | 实测 | 影响 |
|---|---|---|---|
| **D1** | §0:10 "link CAS 不校验同 program"（列为未修漏洞） | **已修**：`TutorVisualDao.kt:336-341` CAS 已带 `snapshot.program_id = tutor_message_visual.program_id`，KDoc `:318-327` 标注"批次 30 复核修复" | 任务 5 若照计划再做一遍会重复实现/重复改同一 SQL；应改为"复核该门 + 补负向用例" |
| **D2** | §0:10 "题面 link 可 pin STAGING" | **已修**：`TutorVisualDao.kt:407-428` 已改为 `INSERT … SELECT … WHERE status='LIVE' AND program 匹配`，KDoc `:399-406` 标注"批次 30 复核修复" | 同 D1 |
| **D3** | §0:10 "checkpoint 保留策略零生产调用" | **半对**：策略已在仓库层接线（`TutorVisualCommitRepository.kt:191/233/327-373`）且在提交事务内执行 demote/evict（`RoomStudyDatabase.kt:2023-2039`）；但 `TutorVisualCommitRepository` **无 app/feature 调用点**（仅 androidTest） | 任务 4 若理解为"接线"会重复落地；真正缺的是"从产品路径调用仓库"（属装配/热区，可能超出本批） |
| **D4** | §0:10 "snapshotId 不重新派生" | **部分**：仓库层派生（`TutorVisualCommitRepository.kt:399-403`）；DB 层只重派生 programId（`RoomStudyDatabase.kt:2118-2130`），`ensureTutorVisualSnapshot`（`:2151-2171`）不校验 snapshotId | 任务 5 只需在 DB 层补门；计划未写这层区分 |
| **D5** | §0:10 "内容唯一键不含 renderer/字体版本（painter 升级后**无法**持久化同内容）" + 任务 6 "schema 71：把 renderer/font 版本并入快照身份…使 painter 升级后同内容**产生新快照**" | **前提已被消除**：`TutorVisualCommit.kt:331-337` 已把四个版本列移出载荷比对，明写"身份 = 唯一键 = 内容三列；版本差异由恢复侧重放/像素门判定…而不是制造同内容第二行"。即"升级后同内容再次提交不再冲突"，计划所称失败不存在 | 任务 6 是**反向改已落裁定**（并要 schema 71），必须先由主会话裁定是否推翻；否则本批不该动 schema |
| **D6** | §0:10 "LIVE LATEST 可能进淘汰候选" | **成立**：`TutorVisualDao.kt:158-176` 无 status 过滤 | 与计划一致，无偏差（仅登记） |
| **D7** | §0:7 "schema 70 已落并推送（四表 + 迁移 + 70.json 跟踪）" | 四表/迁移/70.json **确在 HEAD**；但 **KD-80 的修复（`BackupRestoreInstrumentedTest` 改名+常量）与 `KernelWave0SchemaContractTest` 的字面量 70 都还是工作树 ` M`**（未提交） | "已推送"只覆盖 schema 本身；测试侧修复未推。HEAD 上 JVM 契约测试字面量 69 ≠ 常量 70（§6.3），即 **HEAD 原样构建该 JVM 用例必红** |
| **D8** | §0:8 "69→70 套件 1 红（DDL 断言缺陷）" | 修复**已在树**（`:206-225`）；套件共 2 例，设备红轮 `Tests run: 2, Failures: 1`，红的是 `visualTablesAreCreatedEmpty…`（`:38`），另一例过（`step07-persistence-migration.md:42`）；同批**迁移矩阵 1→70 已 PASS 15/15**（同文件 `:10`） | 与计划一致；但"修后必须真的执行到外键负向与旧行断言"仍未执行（UNVERIFIED） |
| **D9** | §2:37 "热区禁改" | 计划**未枚举热区**；本基线沿用批次 30 清单，测得**全部仍脏**（§2） | 计划缺清单，执行轨需以本表为口径，否则"禁改"无边界 |
| **D10** | §0:7 "备份清单已同步到 90 表/软引用 21/CLOSURE 3" | **成立**（`:40` / `:554` / `:46` / `:538`，且与 70.json 90 实体逐名一致） | 无偏差 |

**另外两处文档陈旧（非计划断言，但会误导执行轨）**：
- `VisualSnapshotMigration_69_70.kt:26-33` 与 `TutorVisualSchemaContractTest.kt:33-35` 仍称"70.json 本轮未生成 / 见 `70.json.NOT_EXPORTED.md`"，而该占位文件**不存在**、70.json 已在 HEAD。
- 计划 §3 验收写"迁移 1→71 全过"，但当前树 `STUDY_DATABASE_VERSION=70`；71 只在任务 6 执行后才存在——**验收前提取决于 D5 的裁定**。

---

## 9. 本轮未执行 / UNVERIFIED

- **未运行 Gradle**（编译、JVM 测试、Lint、`lockAllDependencies`、`check_lockfiles.py` 均未跑）；文中所有"用例数"来自 `grep -c "@Test"`，**不是执行结果**。
- **未执行任何设备/仪器化命令**（只跑 `adb devices -l` / `emu avd name` / `getprop` / `getconf`，均为只读查询）。
- 备份 34/34、69→70 两例、迁移 1→70、painter 24、隔离 13、flags 2、PDF 16、一致门 11 的**绿轮均 UNVERIFIED**（本批基线只做静态计数与在树核对）。
- 远端 `origin/main` 实时 hash **未核**（未 `git fetch`），等式仅对本地跟踪引用成立。
- §3.4 的"DDL 断言修复后能否真正执行到外键负向/旧行断言"是**代码路径可达性**判断，非运行证据。
- KD-73 的 10.3 万 class 扫描、KD-77 的 API 23 冒烟：均只有台账自述，**无仓内可复跑脚本/日志**，本基线不采信为已验证。
- `StudyDatabase.openInMemory`（`StudyDatabase.kt:416-428`）只注册 `SPLIT_IMPORT_MIGRATION_34_35` —— 因内存库按当前版本新建、无需迁移，**本轮未判为缺陷**，仅记录。

---

## 10. 十秒摘要（给主会话）

1. HEAD = `origin/main` = `59d4a03f`；工作树 **690（218 M + 472 ??）**、staged 0；DB schema **70**、请求 schema **18**。
2. 批次 30 四表/迁移 69→70/70.json（90 实体、identityHash `11a8a7…`、11 索引）/SmbkSelectionPlan 70·90·21·CLOSURE3/trimmer 跟随常量 —— **全部在树且干净**。
3. **计划 §0 的六条"未修漏洞"里，两条已修（同 program CAS、题面拒 STAGING）、一条半修（checkpoint 已接线但无产品调用者）、一条部分（snapshotId DB 层未校验）、一条前提被推翻（渲染版本已移出身份、不再冲突）、一条成立（LIVE LATEST 可被淘汰）**。
4. 一致门仍是 `initial-uncalibrated`，P07 判决仍是 F4 先于 F2 —— 任务 7 未做；设备读数出处 `step05-parity.md` + `build/device-track-batch30/step05-parity-logcat.txt:121-122`（maskS 1224 / maskP 1431 / centroid 0.5181）。
5. **HEAD 原样构建时 `KernelWave0SchemaContractTest` 必红**（字面量 69 ≠ 常量 70，修复仅在工作树）。
6. 锁窗口不可行（6/6 构建文件脏、跟踪锁 0、盘上 14）；三台 API 34/x86_64/4096 模拟器在线（含 `emulator-5562/test_device`）。
