# 批次 33 设备判据（T1：结构门 + KD-80 收口）

> **性质**：本轨只做**判据与命令清单**；设备执行归设备轨。本轨**未运行 Gradle、未 assemble、未 install、
> 未执行任何 `am instrument`**，也**未做任何 git 写操作**。文中所有 `OK (N tests)` 都是**通过判据**，
> 不是本轮结果。本轮唯一实跑的门级检查是 §1.4 的 `S=71` 只读比对脚本（`RESULT=PASS` / `EXIT=0`）；
> 其余为只读 `grep` / `git` / `python` 事实核对。
>
> 计划来源：`.zcode/plans/plan-2026-10-29-batch-33-structure-gates-kd80.md` §1 任务 1/2、§2、§3。
> 基线来源：`docs/research/2026-10-29-batch-33-baseline.md`（下称"基线"）。
> KD-80 关闭条件原文：`docs/known-defects.md:2969-3006`（标题与 `:2987` 的 `80-C1 ∧ 80-C2 ∧ 80-C3 ∧ 80-C4`）。
> 80-C4 不变量与用例设计：`docs/research/2026-10-29-backup-visual-reference-checks.md`（下称"C4 研究"）§2–§5。

---

## 0. 结论速览

> **时点说明（2026-10-29 复核修复轮）**：下表"设备结果"列先给 **T1 快照**，`→ 最终` 后是同批
> 14:44–15:13 设备轨的读数（完整证据见 `docs/research/evidence/2026-10-29-batch-33/00-index.md`）。
> **判据正文不改写**（它是执行前口径）；现树以 `→ 最终` 与 00-index 为准，不得再把 T1 的
> "UNVERIFIED / 尚不存在"读成现树事实。

| 判据 | 目标 | 现树状态（T1 实读） | 设备结果 |
|---|---|---|---|
| **结构门** | `OK (5 tests)`，0 failures/errors/ignored | 5 个 `@Test`；`:221` 红因已修（读 `snap-staging-keeper`）；pin 分辨力断言仍在（`:218-225`、`:306-313`）；文件对 HEAD 零 diff | **UNVERIFIED**（T1）**→ 最终 PASS：`OK (5 tests)`**（`step02-structure-gates.log`） |
| **80-C1** | `BackupRestoreInstrumentedTest` `OK (34 tests)` | 34 个 `@Test`；`:590` 期望读 `SmbkSelectionPlan.SCHEMA_VERSION` | **UNVERIFIED**（T1）**→ 最终 PASS：`OK (34 tests)`**（`step01-backup-final.log`） |
| **80-C2** | 表清单逐名 `RESULT=PASS` / `EXIT=0`；设备侧 `rowCounts` 90 键逐名 | **静态半边本轮实跑 PASS**：90 表 / 90 唯一 / 90 JSON / 双向差集空 / 2 视图 / version 71 / hash 逐字同 | 设备半边随 80-C1 的 34/34；**UNVERIFIED**（T1）**→ 最终静态 PASS、设备侧未逐名 → PARTIAL**（E-03 §4 自认不打印 90 行） |
| **80-C3** | 设备 `OK (9 tests)` + JVM `SmbkSelectionPlanTest` 12/12、`VisualCheckpointRetentionTest` 17/17 | 文件计数 9 / 12 / 17；JVM 本轮未执行 | **UNVERIFIED**（T1）**→ 最终设备 9/9 PASS（`step03-trimmer-final.log`）、JVM 未执行 → PARTIAL** |
| **80-C4** | 新增视觉四表引用用例 `OK (5 tests)`（或声明等价的 N） | **T1 时点不存在**；现树已建 `BackupVisualReferenceInstrumentedTest.kt`（**1181 行 / 5 例**） | **UNVERIFIED（零断言）**（T1）**→ 最终 PASS：`OK (5 tests)`**（`step04-c4-visual.log`） |

**KD-80 判定**：`80-C1 ∧ 80-C2 ∧ 80-C3 ∧ 80-C4` 未全部满足 → **保持 open**。本批任何一步绿轮都不得写成
"KD-80 closed"；只有四条在**同一份重建源 + 同一 APK sha** 上同时成立，才允许按 KD 原文关闭。

---

## 1. 现树事实（T1 实读，2026-10-29）

### 1.1 结构门文件与修复位置

`core/database/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/database/TutorVisualStructureGatesInstrumentedTest.kt`

- 类声明 `:54`，5 个 `@Test`：
  | # | 方法 | 行 |
  |---|---|---|
  | 1 | `messageLinkRejectsForeignKeyProgramAndStagingSnapshots` | `:59` |
  | 2 | `revisionPinRejectsStagingAndForeignKeyProgramButAcceptsSameProgramLive` | `:134` |
  | 3 | `evictionDeletesOnlyUnpinnedStagingIntermediates` | `:169` |
  | 4 | `commitTransactionExecutesDemoteAndEvict` | `:235` |
  | 5 | `repositoryPlanRetainsAStagingIntermediateOnlyBecauseItIsPinned` | `:280` |
- 批次 32 红因（`NoSuchElementException: Key snap-staging-middle is missing in the map`，第一项目帧 `:221`）
  的修复已在 HEAD：读侧改为 `snap-staging-keeper`（种子 `:187`，读 `:230`），`snap-staging-middle`
  仍在 `:213-214` 被断言删除。修复提交 `9d91e6b0` 是 HEAD 祖先（`git merge-base --is-ancestor 9d91e6b0 HEAD`
  = true）；该文件 `git status --porcelain` 输出为空（对 HEAD 零 diff）。
- **pin 分辨力断言（不得删/不得放宽）**：
  - DAO 层：`snap-staging-middle` 被删（`:213-214`）；`snap-pinned-staging` 因题面 pin 保留（`:218-221`）；
    `snap-linked-staging` 因消息指针保留（`:222-225`）；读侧 `pinned` 分别为 1 / 1 / 0（`:228-230`）。
  - 仓储 plan→command→SQL 层：两条同形状候选（STAGING + LATEST + 同 owner + 租约过期，`:285-296`），
    pinned 行必须保留（`:306-309`），**未被引用的同形状行必须被淘汰**（`:310-313`，证明 pin 谓词不是恒真）。
- 文件 KDoc `:49-51` 在 T1 时写"修复后的绿轮仍需设备轨重跑，重跑前本套件不得写成 5/5"。
  **该条件已满足**：批次 33 设备轨 `OK (5 tests)`（`step02-structure-gates.log`），复核修复轮已把
  该 KDoc 改成实际读数；本轨未改任何断言。

### 1.2 schema 与备份常量（三处同值）

| 点 | 实测 | 依据 |
|---|---|---|
| `71.json` | 存在、已跟踪、`version=71`、`identityHash=714c0ecb85ee5edf74f0013740148138`、90 entities / 2 views | `git ls-files --error-unmatch …/71.json` exit=0；python 实读 |
| `SmbkSelectionPlan` | `SCHEMA_VERSION = 71`（`:44`）、`IDENTITY_HASH = "714c0ecb85ee5edf74f0013740148138"`（`:51`）、`CLOSURE_VERSION = 3`（`:54`）、`allTables.size == 90`（`:546`）、`softReferences.size == 21`（`:562`） | 逐行实读 |
| 裁剪器门 | 版本门 `database.version != SmbkSelectionPlan.SCHEMA_VERSION`（`BackupSnapshotTrimmer.kt:268-271`）；身份门读 `room_master_table` 并比 `IDENTITY_HASH`（`:276-283`） | 逐行实读 |
| 迁移注册 | `VISUAL_SNAPSHOT_IDENTITY_MIGRATION_70_71` 已注册（`StudyDatabase.kt:411`） | grep 实读 |

### 1.3 套件与当前 `@Test` 计数（构建前必须复读）

| 套件 | 文件 | 当前计数 | 设备目标 |
|---|---|---:|---|
| 结构门 | `core/database/.../TutorVisualStructureGatesInstrumentedTest.kt` | **5** | `OK (5 tests)` |
| 备份/恢复 | `core/data/.../backup/BackupRestoreInstrumentedTest.kt` | **34** | `OK (34 tests)` |
| 裁剪器 | `core/data/.../backup/BackupSnapshotTrimmerInstrumentedTest.kt` | **9** | `OK (9 tests)` |
| 视觉四表引用（C4，现树已建） | `core/data/.../backup/BackupVisualReferenceInstrumentedTest.kt` | **5**（1181 行；T1 时为"不存在"） | `OK (5 tests)`（或声明等价 N） |
| 迁移矩阵 1→71 | `core/database/.../FullMigrationMatrixInstrumentedTest.kt` | **15** | `OK (15 tests)` |
| 69→70（v69→71） | `core/database/.../TutorVisualSnapshotMigration_69_70InstrumentedTest.kt` | **4** | `OK (4 tests)` |
| 70→71 专用 | `core/database/.../TutorVisualSnapshotIdentityMigration_70_71InstrumentedTest.kt` | **3** | `OK (3 tests)` |
| 提交 | `core/database/.../TutorVisualCommitInstrumentedTest.kt` | **12** | `OK (12 tests)` |
| 仓库 | `core/data/.../visual/TutorVisualCommitRepositoryInstrumentedTest.kt` | **12**（T1 为 11；复核修复轮新增第 12 例，**UNVERIFIED**） | `OK (12 tests)` |
| 一致门（原型） | `core/data/.../visual/VisualDrawOpsParityInstrumentedTest.kt` | **16** | `OK (16 tests)` |
| painter | `core/data/.../visual/VisualDrawOpsPainterInstrumentedTest.kt` | **24** | `OK (24 tests)` |
| PDF | `core/data/.../visual/VisualDrawOpsPdfPainterInstrumentedTest.kt` | **16** | `OK (16 tests)` |
| 隔离 | `app/.../visualjs/VisualJsIsolationInstrumentedTest.kt` | **13** | `OK (13 tests)` |
| flags | `app/.../visualjs/VisualMeasureReplayPaintFlagsInstrumentedTest.kt` | **2** | `OK (2 tests)` |

JVM 半边（仓内命令日志，不是 `build/` XML 旁证）：

| 套件 | 模块 | 当前计数 | 目标 |
|---|---|---:|---|
| `SmbkSelectionPlanTest` | `:core:data` | **12** | 12/12 |
| `VisualCheckpointRetentionTest` | `:core:data` | **17** | 17/17 |
| `KernelWave0SchemaContractTest` | `:core:database` | **8** | 8/8（支撑门） |
| `TutorVisualIdentityTest` | `:core:database` | **6** | 6/6（支撑门） |
| `TutorVisualSchemaContractTest` | `:core:database` | **9** | 9/9（支撑门） |

> **计数漂移警告**：计划 §0 写"提交 11 / 仓库 8"，基线（更早时点）写"11 / 9"，现树是 **12 / 11**——
> 本批 T3/T4/T5 仍在往这两个文件加例。设备轨**必须在构建前**重新 `grep -c '@Test'` 并把实际计数写进
> 证据头；设备报告的数**小于**构建前源计数 = 旧包（D），不得当成"少跑几例也算绿"。
> 另：`TutorVisualCommitInstrumentedTest.kt`、`TutorVisualCommitRepositoryInstrumentedTest.kt`、
> `RoomStudyDatabase.kt` 当前均为 ` M`（实现轨在飞）。设备轨必须等这些文件稳定后再构建；
> 按批次纪律，改到的目标文件**整文件纳入并注明**，不得只提交片段。
>
> 另：`BackupRestoreInstrumentedTest.kt:482` 的 KDoc 仍写"`SmbkSelectionPlan.SCHEMA_VERSION`，现为 70"，
> 而常量已是 71。该行是文档残留（批次 33 任务 7 的回改项），**不是**运行时权威；判据以常量与 `71.json` 为准。

### 1.4 本轮实跑的唯一检查（80-C2 静态半边）

命令（仓库根，只读；与批次 32 判据 §6.3 同一脚本，`S=71`）：

```bash
S=71 python - <<'PY'
import json, os, re, sys
v = os.environ.get("S", "71")
plan_path = "core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/backup/SmbkSelectionPlan.kt"
schema_path = f"core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/{v}.json"
if not os.path.exists(schema_path):
    print(f"SCHEMA={v} MISSING {schema_path}"); sys.exit(2)
src = open(plan_path, encoding="utf-8").read()
def block(a, b): return src[src.index(a):src.index(b)]
views = re.findall(r'"([a-z0-9_]+)"', block("val views", "val categoryTables"))
tables = []
for a, b in [("val categoryTables", "val alwaysTables"), ("val alwaysTables", "val derivedTables"),
             ("val derivedTables", "val splitTables"), ("val splitTables", "val rebuildTables")]:
    tables += re.findall(r'"([a-z0-9_]+)"', block(a, b))
db = json.load(open(schema_path, encoding="utf-8"))["database"]
json_tables = [e["tableName"] for e in db["entities"]]
json_views = [x["viewName"] for x in db["views"]]
plan_ver = int(re.search(r'const val SCHEMA_VERSION: Int = (\d+)', src).group(1))
plan_hash = re.search(r'const val IDENTITY_HASH: String = "([0-9a-f]+)"', src).group(1)
only_plan = sorted(set(tables) - set(json_tables)); only_json = sorted(set(json_tables) - set(tables))
dups = sorted({t for t in tables if tables.count(t) > 1})
print(f"SCHEMA={v} PLAN_TABLES={len(tables)} UNIQUE={len(set(tables))} JSON_TABLES={len(json_tables)}")
print(f"ONLY_PLAN={only_plan}"); print(f"ONLY_JSON={only_json}"); print(f"DUP={dups}")
print(f"VIEWS_MATCH={set(views) == set(json_views)}")
print(f"PLAN_VERSION={plan_ver} JSON_VERSION={db['version']}")
print(f"PLAN_HASH={plan_hash} JSON_HASH={db['identityHash']}")
ok = (len(tables) == len(set(tables)) == len(json_tables) and not only_plan and not only_json
      and set(views) == set(json_views) and plan_ver == db["version"] == int(v) and plan_hash == db["identityHash"])
print("RESULT=" + ("PASS" if ok else "FAIL")); sys.exit(0 if ok else 1)
PY
echo "EXIT=$?"
```

实跑输出：

```text
SCHEMA=71 PLAN_TABLES=90 UNIQUE=90 JSON_TABLES=90
ONLY_PLAN=[]
ONLY_JSON=[]
DUP=[]
VIEWS_MATCH=True
PLAN_VERSION=71 JSON_VERSION=71
PLAN_HASH=714c0ecb85ee5edf74f0013740148138 JSON_HASH=714c0ecb85ee5edf74f0013740148138
RESULT=PASS
EXIT=0
```

结论：**80-C2 的静态半边在现树成立**；这**不是**设备结果，不能替代 80-C1 的 34/34 或 80-C2 的设备侧
`rowCounts` 逐名对拍。

---

## 2. 判据一：结构门 `OK (5 tests)`

### 2.1 通过条件（全部满足才算绿）

1. `am instrument` 摘要为 **`OK (5 tests)`**，且 `Tests run: 5, Failures: 0, Errors: 0`；无 `Skipped` / `Ignored`。
2. 5 个方法逐一执行（`-r` 原始输出中每个方法都有 `INSTRUMENTATION_STATUS: test=` 记录），不得用
   `-e class ...#method` 只跑子集。
3. **pin 分辨力断言仍有效**（本节 2.2），不得以"改成读 keeper 行"为由删掉任何一条。
4. logcat（套件运行窗口）满足：
   - `grep -c "NoSuchElementException"` = **0**；
   - `grep -c "SQLiteException"` = **0**；
   - `grep -c "FOREIGN KEY constraint failed"` = **0**；
   - `grep -cE "FATAL EXCEPTION|ANR in "` = **0**。
5. 该绿轮必须来自 §4 的**重建重装** APK（dex 门含 `snap-staging-keeper` 与第 5 例方法名）。

### 2.2 "pin 分辨力断言仍有效"的可执行判据

源码侧（构建前，任一缺失即停）：

```bash
F=core/database/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/database/TutorVisualStructureGatesInstrumentedTest.kt
grep -q 'rawSnapshot(context, databaseName, "snap-staging-keeper"' "$F"        # 读侧必须读仍存在的行
grep -q 'summaries.getValue("snap-staging-keeper").pinned' "$F"                 # 无引用 = 0
grep -q 'summaries.getValue("snap-pinned-staging").pinned' "$F"                 # 题面 pin = 1
grep -q 'summaries.getValue("snap-linked-staging").pinned' "$F"                 # 消息指针 = 1
grep -q 'snap-abandoned-staging' "$F"                                           # 仓储层"同形状未引用必须删"的负向
grep -q 'repositoryPlanRetainsAStagingIntermediateOnlyBecauseItIsPinned' "$F"
```

语义侧（必须同时成立，缺一即判该判据不成立）：

- `snap-pinned-staging`（STAGING + 题面 pin）**仍在**；
- `snap-linked-staging`（STAGING + 消息指针）**仍在**；
- `snap-staging-middle`（STAGING + 未引用 + 在淘汰候选集）**已删**；
- `snap-staging-keeper`（STAGING + 未引用 + **不在**淘汰候选集）**仍在**，读侧 `pinned == 0`；
- 仓储层：pinned 行**仍在**，同形状未引用的 `snap-abandoned-staging` **已删**。

dex 侧（构建后，见 §5.2）：`snap-staging-keeper`、`snap-pinned-staging`、`snap-linked-staging`、
`snap-abandoned-staging`、`repositoryPlanRetainsAStagingIntermediateOnlyBecauseItIsPinned` 五个符号必须都在
`database-debug-androidTest.apk` 里。缺任一 = 旧包/漏装（D），停。

### 2.3 允许的修复范围（红线）

- 只允许改"读哪一行"；**不允许**删除 `:218-225`、`:306-313` 的断言，**不允许**把 `assertEquals(..., 1, ...)`
  改成 `assertTrue(... >= 0)`，**不允许**把 `snap-abandoned-staging` 的 `assertNull` 改成 `assertNotNull`。
- 不允许用 `@Ignore` / `Assume` / 缩小候选集 / 改 `deleted` 期望来"变绿"。
- 若设备红在 `snap-pinned-staging` 或 `snap-linked-staging` 被删，按 §6 归因，**不得**先改测试。

---

## 3. 判据二：KD-80 四条关闭条件

### 3.1 80-C1：备份/恢复 34/34（S=71）

**命令**（设备轨，§5.4 第 1 步）：

```bash
$A -s "$S" shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.data.backup.BackupRestoreInstrumentedTest \
  com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner
```

**通过**：

- `OK (34 tests)`；0 failures / 0 errors / 0 ignored；
- 套件日志四个 grep 全 0：
  ```bash
  grep -c "BackupSnapshotTrimException" step01-backup.log        # 0
  grep -c "expected:<70>"               step01-backup.log        # 0
  grep -c "expected:<71>"               step01-backup.log        # 0
  grep -c "裁剪器只支持 schema="         step01-backup-logcat.txt # 0
  ```
- `schema68ArchiveRestoresAndMigratesToCurrentVersion`（`:488`）绿：恢复后版本断言读
  `SmbkSelectionPlan.SCHEMA_VERSION`（`:590`），当前常量 71；回执版本仍是包自己的 68（不得改成 71）。
- 该轮 APK 必须由当前树重建（§4）；批次 32 的 34/34 对应 11:14–11:17 APK，**不可引用**。

### 3.2 80-C2：表清单逐名（静态 + 设备两侧）

**静态侧（本轮已 PASS）**：`S=71` 脚本 `RESULT=PASS` / `EXIT=0`（§1.4）。通过口径：

- `PLAN_TABLES = UNIQUE = JSON_TABLES = 90`；
- `ONLY_PLAN = ONLY_JSON = DUP = []`；
- `VIEWS_MATCH=True`；
- `PLAN_VERSION = JSON_VERSION = 71`；
- `PLAN_HASH == JSON_HASH == 714c0ecb85ee5edf74f0013740148138`。

`EXIT=2`（`71.json` 缺失）或任何一项不等 = **不通过**；**不得**拿 `S=70` 的结果关闭 71。

**设备侧（随 80-C1 同轮）**：

- 成包：v3 manifest 的 `selection.rowCounts` 必须**恰好覆盖 90 张表**，missing/extra 逐名报出
  （`SmbkArchiveCodec.kt:464-468`；`SmbkSelectionPlan.rowCountKeys = allTables.toSet()`）。
- 恢复：manifest `rowCounts` 的 90 个键与包内库 `COUNT(*)` **逐表相等**
  （`AndroidBackupRepository.kt:1224-1226`、`:1269`）。
- 通过：34/34 中上述两条路径无 `rowCounts` / `missing` / `extra` 失败；logcat 无
  `SmbkArchiveException`。34/34 单独已包含这两条，**但仍需**把「90 键逐名」写进证据结论，
  不得只写"34/34 绿"。

### 3.3 80-C3：设备半边 + JVM 半边

**设备半边**：`BackupSnapshotTrimmerInstrumentedTest` **`OK (9 tests)`**（§5.4 第 3 步）。

**JVM 半边**（必须由串行门禁脚本执行，并保留**仓内命令日志**；`build/` 下的 XML 只能作旁证）：

```bash
./gradlew --console=plain :core:data:testDebugUnitTest \
  --tests "com.tingyun.smartmistakebook.core.data.backup.SmbkSelectionPlanTest" \
  --tests "com.tingyun.smartmistakebook.core.data.visual.VisualCheckpointRetentionTest"
```

**通过**：

- exit=0；
- 两套件 `tests="12"/"17"`、`failures="0"`、`errors="0"`、`skipped="0"`；
- 命令、时间、模块、XML 路径进证据（否则仍记 PARTIAL——批次 32 的口径是"仓内命令日志"，
  不是 `build/` 旁证）。

支撑门（不单独构成 C3，但同轮建议一起跑，红了先按 §6 归因）：

```bash
./gradlew --console=plain :core:database:testDebugUnitTest \
  --tests "com.tingyun.smartmistakebook.core.database.KernelWave0SchemaContractTest" \
  --tests "com.tingyun.smartmistakebook.core.database.TutorVisualIdentityTest" \
  --tests "com.tingyun.smartmistakebook.core.database.TutorVisualSchemaContractTest"
```

### 3.4 80-C4：备份/恢复后视觉四表引用关系保持

**现状（T1 实读）**：`BackupVisualReferenceInstrumentedTest.kt` **当时不存在**；
`grep -c "tutor_visual" core/data/src/androidTest/.../backup/BackupRestoreInstrumentedTest.kt` = **0**。
即 T1 时 80-C4 仍是**零断言**，`BackupRestoreInstrumentedTest` 的 34/34 不能替代。

**现树（2026-10-29 复核修复轮）**：该文件已存在（**1181 行 / 5 例**，`grep -c "@Test"` = 5），
设备实跑 **`OK (5 tests)`**（E-04，`build/device-track-batch33/step04-c4-visual.log`）→ 80-C4 已从
"零断言"变为 PASS；34 例基线未改（`BackupRestoreInstrumentedTest` 仍 34 例，另跑）。

**落点**（C4 研究 §5.1/§5.3，推荐）：新增
`core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/backup/BackupVisualReferenceInstrumentedTest.kt`，
**5 例**，不改 34 例基线。若实现轨选择把断言追加进既有 `schema68ArchiveRestoresAndMigratesToCurrentVersion`
（该文件当前 ` M`，按批次纪律整文件纳入并注明），则必须**显式声明新基线**（例如 34 → 35），
并在证据头记录；不得让设备轨在不知情的情况下把"34 变 35"判成红。

**必须覆盖的不变量**（C4 研究 §2；每条失败信息带不变量 ID + 样例主键）：

| 组 | 不变量 | 断言 |
|---|---|---|
| 行数/集合 | V-R1/R2/R3 | mask 15：源→归档→恢复三次快照相等；mask 9/13：集合等于 C4 研究 §1.3 保留集公式（Kotlin 计算，不复制 SQL） |
| 引用 | V-R5–R11 | `foreign_key_check` 0 行；link/pin/snapshot→program 孤儿查询 0 行；复合 `program_id` 一致；`basis_revision_id` no-new-dangling |
| 状态/角色 | V-R12–R16 | `status ∈ {STAGING,LIVE}`；LIVE⇔`promoted_at` 非空；被引用快照必须 LIVE；`checkpoint_kind` 域；`revision >= 1` |
| 身份/内容 | V-R17–R21 | `program_id`/`snapshot_id` 重派生一致；state/draw 摘要 = `sha256(json)`；五列唯一键无重复行 |

> V-R20（`scene_sha256 == sha256(scene_json)`）在本批任务 4 落码后已可纳入：现树
> `RoomStudyDatabase.kt:2277` 已有该 `require`，`TutorVisualCommitInstrumentedTest` 新增
> `sceneDigestThatDoesNotMatchItsContentIsRejectedBeforeAnyWrite`（`:234`）。设备绿轮前仍按未执行登记。
> 基线 §5.1 的"`scene_sha256` 无内容校验仍成立"是**更早时点**（任务 4 落码前）；以现树 `:2277` 为准。

**5 例通过判据**（C4 研究 §5.3）：

1. `fullSelectionRoundTripPreservesAllVisualRowsAndReferences`（mask 15）：三次快照相等，S2–S10 全空。
2. `subsetNineKeepsRevisionPinsAndInitialBaselinesOnly`（mask 9）：`M=0`、`R=1`、`S={S1,S3}`、`P={P1,P2}`。
3. `subsetThirteenKeepsMessageLinksButTrimsUnreferencedStaging`（mask 13）：`M=2`、`S={S1,S2,S3}`、`P={P1,P2}`、S4 不在、被引用快照均 LIVE。
4. `archiveAndRestoredDatabaseAgreeOnVisualRowIdentity`（mask 15）：归档 vs 恢复按主键排序的内容列逐行相等；两库 `integrity_check=ok`。
5. `repackagedArchiveWhoseLinkPointsAtAMissingSnapshotIsRejectedBeforeSwap`（第三方重裁）：抛 `BackupRestoreException`，消息含 `foreign_key_check`，`countMistakes()` 不变。

**设备命令**（C4 研究 §5.5；类名/方法名以落码后源码为准，不得预写占位名）：

```bash
$A -s "$S" logcat -c
$A -s "$S" shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.data.backup.BackupVisualReferenceInstrumentedTest \
  com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner \
  | tee build/device-track-batch33/step-c4-visual.log
$A -s "$S" logcat -v threadtime -d > build/device-track-batch33/step-c4-visual-logcat.txt
```

**通过**：

- `OK (5 tests)`（或实现轨声明并记录的 N），0 failures / 0 errors / 0 ignored；
- logcat 无 `BackupSnapshotTrimException`、无 `SQLiteException`、无未捕获的 `foreign_key_check` 异常
  （第 5 例的拒绝是**预期**，应被测试捕获并断言）；
- **同轮** `BackupRestoreInstrumentedTest` 仍为 `OK (34 tests)`（基线不变；若实现轨改为 35，以声明的 N 为准）；
- 该轮 APK 与 80-C1 是**同一份重建 APK**（同一 sha256）。

**不得**：用"34/34 绿 + 表清单 PASS"代替 C4；用"表在 90 张清单里"代替"引用关系保持"；
把 C4 用例的绿写成 80-C1 的绿。

### 3.5 关闭规则

```text
KD-80 closed ⇔ 80-C1 ∧ 80-C2 ∧ 80-C3 ∧ 80-C4
             且四条的设备证据来自同一份「构建前源快照」与同一个 APK sha256
```

- 任一条件缺失、来自旧 APK、或来自 `S=70` 口径 → **open**，可写"某条已核销"，不得写 closed。
- 当前树：C1/C2 有 S=71 设备绿轮但对应批次 32 的 11:14 APK（现树已变）；C2 静态 PASS；
  C3 JVM 半边未在仓内执行；C4 零断言 → **open**。

---

## 4. 判据三之前置：必须先重建重装（硬门）

### 4.1 为什么不能引用旧绿轮

- 批次 32 的设备 APK 构建于 **11:14–11:17**；结构门修复落在 **13:04:22 / 13:05:17** 两笔提交
  （基线 §3），HEAD 已含，但那个 APK **不含**。
- 批次 32 记录另登记现树在 12:26–12:28 有 **11 个源文件**改动（含 `RoomStudyDatabase` /
  `TutorVisualCommitRepository` 的 stage 淘汰集修复与仓库新增例）。
- 本批 T3/T4/T5 仍在加例（提交 12 / 仓库 11）。**任何"批次 32 曾 34/34、16/16、13/13 绿"都不能
  替代当前树/当前 APK 的证据。**

### 4.2 构建与源绑定

1. 构建前取源快照：
   ```bash
   mkdir -p build/device-track-batch33
   find app/src core/data/src core/database/src -type f -print0 | sort -z \
     | xargs -0 sha256sum > build/device-track-batch33/src-sha-before.txt
   ```
2. 构建（EXIT=0）：
   ```bash
   ./gradlew --console=plain \
     :app:assembleStrictOfflineDebug \
     :app:assembleStrictOfflineDebugAndroidTest \
     :core:data:assembleDebugAndroidTest \
     :core:database:assembleDebugAndroidTest
   ```
3. 构建后复取源快照并 `diff`：
   ```bash
   find app/src core/data/src core/database/src -type f -print0 | sort -z \
     | xargs -0 sha256sum > build/device-track-batch33/src-sha-after.txt
   diff -u build/device-track-batch33/src-sha-before.txt \
           build/device-track-batch33/src-sha-after.txt \
     && echo "SOURCE STABLE" || { echo "SOURCE CHANGED DURING BUILD — REBUILD"; exit 1; }
   ```
   **不一致 = 构建早于源改动（D）**：必须等所有在飞轨稳定后**重建 + 重装**，前一轮日志作废。
4. 记录四包 sha256（进证据头）：
   ```bash
   sha256sum app/build/outputs/apk/strictOffline/debug/app-strictOffline-debug.apk
   sha256sum app/build/outputs/apk/androidTest/strictOffline/debug/app-strictOffline-debug-androidTest.apk
   sha256sum core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk
   sha256sum core/database/build/outputs/apk/androidTest/debug/database-debug-androidTest.apk
   ```

### 4.3 安装与字节核对

```bash
export MSYS_NO_PATHCONV=1
A=C:/Android/Sdk/platform-tools/adb.exe
S=emulator-5562   # -5566/-5584 属他线，禁碰

$A -s "$S" install -r -t app/build/outputs/apk/strictOffline/debug/app-strictOffline-debug.apk
$A -s "$S" install -r -t app/build/outputs/apk/androidTest/strictOffline/debug/app-strictOffline-debug-androidTest.apk
$A -s "$S" install -r -t core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk
$A -s "$S" install -r -t core/database/build/outputs/apk/androidTest/debug/database-debug-androidTest.apk

for p in com.tingyun.smartmistakebook.offline \
         com.tingyun.smartmistakebook.offline.test \
         com.tingyun.smartmistakebook.core.data.test \
         com.tingyun.smartmistakebook.core.database.test; do
  path=$($A -s "$S" shell pm path "$p" | tr -d '\r' | sed 's/^package://')
  $A -s "$S" shell sha256sum "$path"
done
```

- 四条均 `Success`；`pm path` 的 sha256 与宿主 APK 逐字节一致。
- `INSTALL_FAILED_UPDATE_INCOMPATIBLE` → 先 `adb uninstall <精确包名>` 再装，舞步记入证据。
- **只有安装成功且 sha 对上，才允许跑 §5.4**。

### 4.4 构建前一致性门（schema 中间态禁止构建）

```bash
V=$(sed -n 's/.*STUDY_DATABASE_VERSION = \([0-9][0-9]*\).*/\1/p' \
  core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabase.kt | head -1)
P=$(sed -n 's/.*const val SCHEMA_VERSION: Int = \([0-9][0-9]*\).*/\1/p' \
  core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/backup/SmbkSelectionPlan.kt | head -1)
echo "V=$V P=$P"                       # 必须 71 / 71
test -f core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/71.json \
  || { echo "STOP: 71.json missing"; exit 1; }
git ls-files --error-unmatch \
  core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/71.json
grep -q "VISUAL_SNAPSHOT_IDENTITY_MIGRATION_70_71" \
  core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabase.kt
```

任一不等/缺失 → **停**，不得用中间态构建。

---

## 5. 判据三：设备轨串行命令与 dex 符号门

> 严格串行，一条完成再下一条。`export MSYS_NO_PATHCONV=1` 全程生效。Gradle 只允许 `assemble*`；
> **禁 `connectedAndroidTest`**。每条套件前清 logcat，跑完落 raw log + logcat。

### 5.0 步 0：设备再登记

```bash
# A / S 见 §4.3（A=adb 绝对路径，S=emulator-5562）
$A devices -l
$A -s "$S" shell getprop ro.build.version.sdk
$A -s "$S" shell getprop ro.product.cpu.abi
$A -s "$S" shell getconf PAGE_SIZE
$A -s "$S" shell getprop ro.build.fingerprint
```

通过：`device` 态、非 offline/unauthorized；serial/API/ABI/PAGE_SIZE/fingerprint 五值进证据头。
优先 API 34 / x86_64 / 4 KB；换设备则注明**不能跨设备比较像素**（一致门）。

### 5.1 步 1–3：构建前门 → 构建 → 安装

见 §4.4 → §4.2 → §4.3。**顺序不可颠倒**。

### 5.2 步 4：dex 符号门（防旧包跑新门）

```bash
set -e
need() {
  apk="$1"; shift
  for s in "$@"; do
    unzip -p "$apk" 'classes*.dex' | grep -a -q "$s" \
      || { echo "MISSING [$s] in $apk"; return 1; }
  done
  echo "DEX OK: $apk"
}
```

**app 测试包**（隔离 13 + flags 2）：

```bash
need app/build/outputs/apk/androidTest/strictOffline/debug/app-strictOffline-debug-androidTest.apk \
  VisualJsIsolationInstrumentedTest \
  VisualMeasureReplayPaintFlagsInstrumentedTest \
  theMeasuringPaintAndTheReplayTextPaintShareTheFingerprintedFlags \
  aFingerprintThatDisagreesWithTheActualPaintFailsClosed
```

**core:data 测试包**（备份 34 + 裁剪器 9 + painter 24 + PDF 16 + parity 16 + 仓库 11）：

```bash
need core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk \
  BackupRestoreInstrumentedTest \
  schema68ArchiveRestoresAndMigratesToCurrentVersion \
  BackupSnapshotTrimmerInstrumentedTest \
  VisualDrawOpsPainterInstrumentedTest \
  VisualDrawOpsPdfPainterInstrumentedTest \
  VisualDrawOpsParityInstrumentedTest \
  n09OnePixelTextShiftIsCaught \
  n10WholePageMisdrawIsCaught \
  n11ShapeWrongButPositionAndMassRightIsCaught \
  n12WholeBlockShiftBeyondToleranceIsCaught \
  n13CentroidBlindSpotIsCoveredByBboxOrShift \
  pdfWriteFailureMidStreamKeepsTheFirstFailureText \
  pdfFileWriteRoundTripsThroughReadback \
  pdfReadOnlyTargetFailsClosedBeforeAnyWrite \
  PdfWriteFailureTrackingStream PdfWriteTelemetry \
  TutorVisualCommitRepositoryInstrumentedTest \
  snapshotOverSourceBudgetIsRejectedBeforeAnyWrite \
  commitEvictsAnUnreferencedStagingIntermediateThroughPlanToCommand \
  stageThenCommitStillDemotesThePreviousLatestCheckpoint \
  stagingIntoAnOverBudgetProgramEvictsAnExpiredUnpinnedStagingIntermediate \
  stagingWhenEvictionIsRefusedByAnInFlightLeaseStillRejectsWithACode
```

**C4 条件门**（实现轨落码后才跑；类缺失时**跳过 C4、继续其余套件**并把 C4 记 UNVERIFIED）：

```bash
C4=core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/backup/BackupVisualReferenceInstrumentedTest.kt
if [ -f "$C4" ]; then
  need core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk \
    BackupVisualReferenceInstrumentedTest \
    fullSelectionRoundTripPreservesAllVisualRowsAndReferences \
    subsetNineKeepsRevisionPinsAndInitialBaselinesOnly \
    subsetThirteenKeepsMessageLinksButTrimsUnreferencedStaging \
    archiveAndRestoredDatabaseAgreeOnVisualRowIdentity \
    repackagedArchiveWhoseLinkPointsAtAMissingSnapshotIsRejectedBeforeSwap
else
  echo "C4 CLASS ABSENT — 80-C4 UNVERIFIED；继续跑其余套件，KD-80 保持 open"
fi
```

> C4 的 5 个方法名以**落码后源码**为准；实现轨若改名，同步改本节。**不得**把不存在的名字塞进 dex 门
> （批次 32 对 70→71 的规则同此）。

**core:database 测试包**（迁移矩阵 15 + 69→70 4 + 70→71 3 + 结构门 5 + 提交 12）：

```bash
need core/database/build/outputs/apk/androidTest/debug/database-debug-androidTest.apk \
  FullMigrationMatrixInstrumentedTest \
  everyExportedSchemaVersionMigratesToCurrentWithoutDestructiveFallback \
  TutorVisualSnapshotMigration_69_70InstrumentedTest \
  visualTablesAreCreatedEmptyWithoutTouchingV69RowsAndIdempotent \
  onlyLiveSnapshotsCanBeLinkedAndCasUsesChangedRowCounts \
  indexExpectationsStayInSyncWithTheMigrationDdlConstants \
  indexShapeAssertionsCanFailOnWrongColumnOrderAndNonUniqueIndex \
  TutorVisualSnapshotIdentityMigration_70_71InstrumentedTest \
  TutorVisualStructureGatesInstrumentedTest \
  messageLinkRejectsForeignKeyProgramAndStagingSnapshots \
  revisionPinRejectsStagingAndForeignKeyProgramButAcceptsSameProgramLive \
  evictionDeletesOnlyUnpinnedStagingIntermediates \
  commitTransactionExecutesDemoteAndEvict \
  repositoryPlanRetainsAStagingIntermediateOnlyBecauseItIsPinned \
  snap-staging-keeper \
  snap-pinned-staging \
  snap-linked-staging \
  snap-abandoned-staging \
  TutorVisualCommitInstrumentedTest \
  snapshotIdThatIsNotDerivedFromContentIsRejectedBeforeAnyWrite \
  snapshotDigestThatDoesNotMatchItsContentIsRejectedBeforeAnyWrite \
  drawCommandsDigestThatDoesNotMatchItsContentIsRejectedBeforeAnyWrite \
  sceneDigestThatDoesNotMatchItsContentIsRejectedBeforeAnyWrite
```

**schema 资产门**：

```bash
V=$(sed -n 's/.*STUDY_DATABASE_VERSION = \([0-9][0-9]*\).*/\1/p' \
  core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabase.kt | head -1)
unzip -l core/database/build/outputs/apk/androidTest/debug/database-debug-androidTest.apk \
  | grep -E 'StudyDatabase/[0-9]+\.json$' | wc -l   # == V（71）
unzip -l core/database/build/outputs/apk/androidTest/debug/database-debug-androidTest.apk \
  | grep -q "StudyDatabase/$V.json" || { echo "MISSING $V.json"; exit 1; }
```

通过：三个基础 `DEX OK`（C4 类存在时再加一个 `DEX OK`）；任一 `MISSING` = 旧包/漏装，**停**，重建重装，不得继续跑。

### 5.3 步 5：构建前计数复读（写进证据头）

```bash
for f in \
  core/database/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/database/TutorVisualStructureGatesInstrumentedTest.kt \
  core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/backup/BackupRestoreInstrumentedTest.kt \
  core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/backup/BackupSnapshotTrimmerInstrumentedTest.kt \
  core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/visual/TutorVisualCommitRepositoryInstrumentedTest.kt \
  core/database/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/database/TutorVisualCommitInstrumentedTest.kt; do
  printf "%3d %s\n" "$(grep -c '@Test' "$f")" "$f"
done
```

设备报告数 < 构建前计数 = **D（旧包）**；不得继续。

### 5.4 步 6–N：套件串行

```bash
# 沿用 §4.3 的 A / S
D=build/device-track-batch33
mkdir -p "$D"
run() {  # run <step> <pkg> <class>
  step="$1"; pkg="$2"; cls="$3"
  $A -s "$S" logcat -c
  $A -s "$S" shell am instrument -w -r -e class "$cls" \
    "$pkg/androidx.test.runner.AndroidJUnitRunner" | tee "$D/$step.log"
  $A -s "$S" logcat -v threadtime -d > "$D/$step-logcat.txt"
}

# 1) 备份 34（80-C1 + 80-C2 设备侧）
run step01-backup com.tingyun.smartmistakebook.core.data.test \
  com.tingyun.smartmistakebook.core.data.backup.BackupRestoreInstrumentedTest

# 2) 结构门 5（判据一）
run step02-structure-gates com.tingyun.smartmistakebook.core.database.test \
  com.tingyun.smartmistakebook.core.database.TutorVisualStructureGatesInstrumentedTest

# 3) 裁剪器 9（80-C3 设备半边）
run step03-trimmer com.tingyun.smartmistakebook.core.data.test \
  com.tingyun.smartmistakebook.core.data.backup.BackupSnapshotTrimmerInstrumentedTest

# 4) 视觉四表引用 5（80-C4；类存在才跑；缺失则记 UNVERIFIED，继续其余套件，KD-80 保持 open）
run step04-c4-visual com.tingyun.smartmistakebook.core.data.test \
  com.tingyun.smartmistakebook.core.data.backup.BackupVisualReferenceInstrumentedTest

# 5) 迁移矩阵 1→71（15）
run step05-migration-matrix com.tingyun.smartmistakebook.core.database.test \
  com.tingyun.smartmistakebook.core.database.FullMigrationMatrixInstrumentedTest

# 6) 69→70（v69→71，4）
run step06-migration-69-70 com.tingyun.smartmistakebook.core.database.test \
  com.tingyun.smartmistakebook.core.database.TutorVisualSnapshotMigration_69_70InstrumentedTest

# 7) 70→71 专用（3）
run step07-migration-70-71 com.tingyun.smartmistakebook.core.database.test \
  com.tingyun.smartmistakebook.core.database.TutorVisualSnapshotIdentityMigration_70_71InstrumentedTest

# 8) 提交（现树 12）
run step08-visual-commit com.tingyun.smartmistakebook.core.database.test \
  com.tingyun.smartmistakebook.core.database.TutorVisualCommitInstrumentedTest

# 9) 仓库（现树 11）
run step09-visual-repo com.tingyun.smartmistakebook.core.data.test \
  com.tingyun.smartmistakebook.core.data.visual.TutorVisualCommitRepositoryInstrumentedTest

# 10) 一致门 16（原型门，不得写成产品门）
run step10-parity com.tingyun.smartmistakebook.core.data.test \
  com.tingyun.smartmistakebook.core.data.visual.VisualDrawOpsParityInstrumentedTest

# 11) painter 24
run step11-painter com.tingyun.smartmistakebook.core.data.test \
  com.tingyun.smartmistakebook.core.data.visual.VisualDrawOpsPainterInstrumentedTest

# 12) PDF 16
run step12-pdf com.tingyun.smartmistakebook.core.data.test \
  com.tingyun.smartmistakebook.core.data.visual.VisualDrawOpsPdfPainterInstrumentedTest

# 13) 隔离 13
run step13-isolation com.tingyun.smartmistakebook.offline.test \
  com.tingyun.smartmistakebook.visualjs.VisualJsIsolationInstrumentedTest

# 14) flags 2
run step14-flags com.tingyun.smartmistakebook.offline.test \
  com.tingyun.smartmistakebook.visualjs.VisualMeasureReplayPaintFlagsInstrumentedTest
```

逐步汇总（任一非目标字符串即停在该步）：

```bash
for f in "$D"/step*.log; do
  echo "== $(basename "$f")"
  grep -E "OK \(|Tests run:|Failures:|Error|Skipped|Time:" "$f" | tail -4
done
```

目标字符串：

| step | 目标 |
|---|---|
| step01-backup | `OK (34 tests)` |
| step02-structure-gates | `OK (5 tests)` |
| step03-trimmer | `OK (9 tests)` |
| step04-c4-visual | `OK (5 tests)`（或声明 N） |
| step05-migration-matrix | `OK (15 tests)` |
| step06-migration-69-70 | `OK (4 tests)` |
| step07-migration-70-71 | `OK (3 tests)` |
| step08-visual-commit | `OK (12 tests)`（以构建前复读为准） |
| step09-visual-repo | `OK (11 tests)`（以构建前复读为准） |
| step10-parity | `OK (16 tests)` |
| step11-painter | `OK (24 tests)` |
| step12-pdf | `OK (16 tests)` |
| step13-isolation | `OK (13 tests)` |
| step14-flags | `OK (2 tests)` |

### 5.5 步 N+1：JVM 半边（串行门禁脚本，不是设备）

见 §3.3。必须保留命令日志；`build/` XML 只能作旁证。

### 5.6 步 N+2：签名扫描（全部 logcat）

```bash
for f in "$D"/*-logcat.txt; do
  printf "%s: " "$(basename "$f")"
  grep -cE "FATAL EXCEPTION|ANR in |NoSuchMethodError|NoClassDefFoundError|UnsatisfiedLinkError|SIGSEGV|CANNOT LINK|failed to map segment|death was not confirmed" "$f"
done   # 期望全 0
```

### 5.7 证据头最低字段

serial / API / ABI / PAGE_SIZE / fingerprint / HEAD / 工作树状态 / 四 APK sha256 /
`src-sha-before` 与 `src-sha-after` 的 diff 结果 / 构建前各套件 `@Test` 计数 / 每步目标字符串与实际 summary /
红例归因（§6.4 字段）。缺任一 = 证据不成立。

---

## 6. 判据四：红例归因规则（先测试层，后产品层）

### 6.1 绝对禁止

- 删除/弱化断言、放大 delta/容差、缩小扫描范围、`@Ignore`/`Assume` 跳过、用 `-e class` 过滤红例、
  把 `OK (N tests)` 的 N 改小、把失败改写成"已知问题"后仍声称通过。
- **先改测试让它变绿、再回头找原因。**
- 以"批次 32 曾 34/34、16/16、13/13 绿"或"另一台设备绿"替代当前树/当前 APK 的证据。
- 把一致门（prototype）的红/绿写成产品门或发版结论。
- 用"34/34 绿"代替 80-C4；用"表在 90 张清单里"代替"引用关系保持"。
- 用"JVM `build/` XML 有 12/0"代替"仓内命令日志"（80-C3 仍是 PARTIAL）。

### 6.2 强制顺序

1. **抓原样**：完整失败行、断言消息、stack（项目帧 + 框架帧首行）、`Tests run:` 行；归因前不删 log。
2. **定位第一项目帧**：哪个文件哪一行。
3. **先测试层**：
   - **A（夹具/门逻辑缺陷）**：失败在测试辅助代码、夹具、或原型门自身判定逻辑；产品路径未被
     执行到，或门的分辨力断言写错。处置 = 改夹具/门逻辑，产品源零改动；**不得借机放宽阈值**。
   - **B（测试期望漂移）**：实际值与权威源（常量/契约/编译器/探针）一致，测试期望写错。
     处置 = **改成权威值**（不是改成实测值），保留 delta 0；补权威源 `path:line`。
4. **再环境层 D（设备/旧包）**：设备 offline、装错旧包、API/ABI/PAGE_SIZE 不符、字体差异导致
   跨设备像素比较、构建早于源改动。处置 = 重登记/重建重装/核 sha256 与 dex 门；
   **跨设备像素差异不作产品缺陷**，只在单设备内比较像素。
5. **最后产品层 C**：测试期望符合权威源，产品输出/行为错。处置 = 修产品；保留/加强测试；
   若为已知缺口登记 KD，**不得删测试或放宽**。
6. **先红后绿闭环**：红 log → 归因证据 → 改动清单（精确到文件/行/牵动面）→ 重建重装（新 sha256）
   → 同命令复跑绿 log。未复跑 = UNVERIFIED。

> 口诀：**A/B 先行，D 排除，最后才 C**。

### 6.3 批次 33 专用陷阱

| 现象 | 先判 | 处置 |
|---|---|---|
| 结构门红 `NoSuchElementException: Key snap-staging-middle is missing in the map` | 先看 dex：缺 `snap-staging-keeper` = **D**（旧 APK）；dex 有该符号仍红 = **A/C**（测试读侧回归或产品删除语义变化） | 缺符号 → 重建重装；有符号 → 查 `:187/:230` 与 `TutorVisualDao.kt` 读侧 |
| 结构门红 `snap-pinned-staging` / `snap-linked-staging` 被删 | 先 A（夹具形状）→ **C**（pin/link 谓词失效） | 查 `TutorVisualDao.kt` 的两条 `NOT EXISTS`；不得删断言 |
| 结构门第 5 例红：pinned 行消失 | **C**（plan 未排除 pinned） | 查 `VisualCheckpointRetention.kt` 的 `!pinned` 过滤 |
| 结构门第 5 例红：`snap-abandoned-staging` 仍在 | **C**（SQL pin 谓词恒真/未生效） | 查 `TutorVisualDao.kt` 的 pin `NOT EXISTS`；不得把 `assertNull` 改成 `assertNotNull` |
| 备份红 `快照 schema=71，裁剪器只支持 schema=70` | 先 D（旧包）→ **C**（常量回填未完成） | 查 `SmbkSelectionPlan.kt:44` 与 `BackupSnapshotTrimmer.kt:268-283`；新包仍 70 = C |
| 备份红 `expected:<70> but was:<71>` | **D**（旧测试包） | dex 门查 `schema68ArchiveRestoresAndMigratesToCurrentVersion`；重装 |
| 备份 34 中 `schema68ArchiveRestoresAndMigratesToCurrentVersion` 红 | **C**（迁移未注册/恢复未落到实际库） | 查 `StudyDatabase.kt:411` 与恢复路径 |
| 备份报告 35/36 等非 34 | 先看实现轨是否**声明**把 C4 断言并入该文件 | 已声明 = 新基线（记录）；未声明 = **D**（装错包）或 **C**（基线被静默改动） |
| C4 用例 1 红、其余绿 | **A**（全选路径被裁，`mask != FULL` 守卫被破坏） | 查 `BackupSnapshotTrimmer.kt` 的视觉跳过守卫 |
| C4 用例 2/3 红，但归档与恢复一致 | **A**（期望公式/裁剪规则） | 先核对 C4 研究 §1.3 与实现；不得改期望去迁就实测 |
| C4 归档绿、恢复红 | **B**（恢复 B 类：复制/迁移/交换） | 查恢复 Phase 2b/2c/3 |
| C4 用例 5 未被拒绝 | **B**（恢复侧 FK 门失效，KD-80 复现） | 查 `AndroidBackupRepository.kt:1121-1135`；不得删用例 |
| 裁剪器不是 9 | **D**（旧包） | 重建；dex 门查 `BackupSnapshotTrimmerInstrumentedTest` |
| 提交不是 12 / 仓库不是 11（T1 快照） | 先看构建前复读 | 源计数也变了 = 正常漂移（记录）；设备数 < 源数 = **D** |
| 迁移矩阵在 v71 红、`assets.open` 抛 FileNotFound | **D**（assets 缺 71）→ C | 查 schema 资产门；重建 |
| 迁移矩阵红 `Migration didn't properly handle` | **C**（迁移 DDL 与实体索引不一致） | 对照 71.json 的 `createSql`；先排除 D |
| 打开库报 `Room cannot verify the data integrity` | **C**（71.json 哈希 ≠ Impl 哈希）或 D（旧 asset） | 按 §4.4 重取；**不得改 JSON** |
| JVM `SmbkSelectionPlanTest` 红在 71.json/hash | **C**（schema/常量漂移）或 D（stale build） | 先跑 §4.4 门；不得改期望 |
| `NoClassDefFoundError` / `UnsatisfiedLinkError` / `FATAL EXCEPTION` | **D** | 重建重装；查签名扫描 |
| 跨设备像素差 | **D** | 只在同设备内比较；不得判产品缺陷 |

### 6.4 归因记录最低字段

红例名 + 行号 + 失败消息 + 第一项目帧 + 四源核对结果（逐源 `path:line`）+ 类别（A/B/C/D）+
处置（改哪个文件哪几行/或不改的理由）+ 重建 sha256 + 绿轮 summary。缺任一字段 = 归因不成立。

---

## 7. 未验证项（本轨，T1 时点）

> **时点说明**：本节是 T1 执行前的缺口清单，**不代表现树**。批次 33 设备轨（14:44–15:13）已把
> 结构门/80-C1/80-C3 设备半边/80-C4 跑绿；仍成立的是 80-C2 设备侧逐名与 80-C3 JVM 半边。
> 实际读数见 §0 `→ 最终` 与 `docs/research/evidence/2026-10-29-batch-33/00-index.md` §3–§4。

1. **结构门 5/5**：只验证了源码/行锚与修复已在 HEAD；**未构建、未上设备** → UNVERIFIED。
2. **80-C1 34/34**：未跑；批次 32 的绿轮对应 11:14 APK，当前树已变 → 当前树 UNVERIFIED。
3. **80-C2 设备侧**：未跑；静态脚本本轮 PASS（§1.4），但设备 `rowCounts` 逐名随 34/34 → UNVERIFIED。
4. **80-C3**：设备裁剪器 9、JVM 12/17 均未执行 → UNVERIFIED（JVM 半边仍是 PARTIAL 口径）。
5. **80-C4**：T1 时用例类不存在、零断言 → 当时 UNVERIFIED；**现树已建 5 例并设备 `OK (5 tests)`**
   （E-04）。不得把 34/34 写成已覆盖 C4（两者是不同套件）。
6. **设备在线状态**：本轨未跑 `adb devices`；§4.3 的 `emulator-5562` 取自基线 §8（只读实测）与批次 32 判据；
   执行前必须重新登记。
7. **计数漂移**：§1.3 先给 T1 快照，复核修复轮后仓库由 11 → 12（新增 stage 侧"净值也超"负例），
   提交仍 12、C4 5；设备轨必须按 §5.3 复读。新增/补强的 12 例复跑 **UNVERIFIED**（本会话不运行 Gradle）。
8. **`71.json` 生成新鲜度**：本轨只读既有产物，未跑 `kspDebugKotlin`；生成物与现树实体的一致性由
   `SmbkSelectionPlanTest` / schema 契约门在设备/串行门禁中复核。

---

## 8. 来源

### 8.1 仓内（本会话实读/实跑）

- 计划：`.zcode/plans/plan-2026-10-29-batch-33-structure-gates-kd80.md:1-32`
- 基线：`docs/research/2026-10-29-batch-33-baseline.md`（§0-A/§3/§7.1/§8）
- KD-80：`docs/known-defects.md:2969-3006`（关闭条件 `:2987`；C4 原文 `:2977`）
- 结构门：`core/database/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/database/TutorVisualStructureGatesInstrumentedTest.kt:54,59,134,169,187,213-230,235,280-315`
- 淘汰 SQL：`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/dao/TutorVisualDao.kt:180-235`（pin/kind/status/租约）
- 读侧：`TutorVisualDao.kt:118-141`（`checkpointSummaries` 只返回仍存在的行）
- 备份：`core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/backup/BackupRestoreInstrumentedTest.kt:85,488,590`（34 例；`:482` 的 "现为 70" 是文档残留）
- 裁剪器：`core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/backup/BackupSnapshotTrimmerInstrumentedTest.kt:33`（9 例）
- 常量：`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/backup/SmbkSelectionPlan.kt:44,51,54,546,562`
- 裁剪器门：`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/backup/BackupSnapshotTrimmer.kt:268-283`
- 清单逐名：`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/backup/SmbkArchiveCodec.kt:464-468`；
  `AndroidBackupRepository.kt:1224-1226,1269`
- 迁移注册：`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabase.kt:411`
- `71.json`：`core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/71.json`（version 71 / hash `714c…8138` / 90 entities / 2 views；`git ls-files` exit=0）
- C4 设计：`docs/research/2026-10-29-backup-visual-reference-checks.md:125-180`（V-R1–R21）、`:225-356`（S1–S10）、`:358-423`（期望集合与归因）、`:494-597`（5 例与命令）
- 批次 32 判据模板：`docs/research/2026-10-29-batch-32-device-criteria.md:520-847`（串行命令、dex 门、归因）
- 批次 32 红例：`docs/research/evidence/2026-10-29-batch-32/step13-red-attribution.md:1-70`（A 类，第一项目帧 `:221`）

### 8.2 本轮实跑命令

```bash
# 80-C2 静态半边（S=71；RESULT=PASS / EXIT=0）
S=71 python - <<'PY' … PY

# 计数与事实（只读）
grep -c '@Test' <各套件文件>
grep -n "SmbkSelectionPlan.SCHEMA_VERSION" core/data/src/androidTest/.../BackupRestoreInstrumentedTest.kt
grep -c "tutor_visual" core/data/src/androidTest/.../BackupRestoreInstrumentedTest.kt   # 0
git ls-files --error-unmatch core/database/schemas/.../71.json
git merge-base --is-ancestor 9d91e6b0 HEAD
git status --porcelain -- core/database/src/androidTest/.../TutorVisualStructureGatesInstrumentedTest.kt
python - <<'PY'  # 71.json: version/hash/entities/views
PY
```

**未运行**：任何 `gradlew`、任何 `adb install` / `am instrument`、任何 `git add/commit/push`、任何文件删除。
