# 批次 34 设备判据（T1：KD-80 收尾 + 新测试源重跑 + 重建重装门）

> **性质**：本轨只做**判据与命令清单**；设备执行归设备轨。本轨**未运行 Gradle、未 assemble、未 install、
> 未执行任何 `am instrument`、未做任何 git 写操作**。文中所有 `OK (N tests)` 都是**通过判据**，不是本轮结果。
> 本轮唯一实跑的命令是 §1.4 的 `S=71` 只读比对脚本（`RESULT=PASS` / `EXIT=0`）与只读 `grep/stat/python`。
>
> 计划来源：`.zcode/plans/plan-2026-10-29-batch-34-dual-backend-kd80.md` §1 任务 1/2、§2、§3。
> 80-C2 原文：`docs/known-defects.md:3035`（设备侧 `rowCounts` 90 键逐名）与
> `docs/research/2026-10-29-batch-33-device-criteria.md:255-263`。
> 批次 33 未上设备的源差：`docs/research/2026-10-29-batch-33-record.md:225-249`。

---

## 0. 结论速览（执行前口径）

| 项 | 目标 | 现树事实 | 设备结果（待设备轨） |
|---|---|---|---|
| **80-C2 设备侧** | `rowCounts` 90 键**逐名**与 71.json 表集合比对；每表行数 = 包内库/恢复库 `COUNT(*)` | 新增 `BackupRowCountsCoverageInstrumentedTest.kt`（2 例，读测试 APK 内 `assets/…/71.json`）；静态半边本轮 `RESULT=PASS`/`EXIT=0` | 待跑：`OK (2 tests)` |
| **仓库 12** | 现树 12 例全部上设备 | `grep -c '@Test'` = **12**；设备绿轮只跑过 11 例 | 待跑：`OK (12 tests)` |
| **提交 12** | 现树 12 例全部上设备 | `grep -c '@Test'` = **12**（含 scene digest 第 12 例） | 待跑：`OK (12 tests)` |
| **结构门 5** | 现树 5 例全部上设备 | `grep -c '@Test'` = **5** | 待跑：`OK (5 tests)` |
| 其余 11 套件 | 不回退 | 计数见 §3.2 | 待跑（目标 §4.5） |
| **KD-80** | `80-C1 ∧ 80-C2 ∧ 80-C3 ∧ 80-C4` 且同一源快照 + 同一 APK sha | C2 设备侧此前 PARTIAL；C1/C3/C4 的绿轮来自旧源 | 四条全部在**本轮重建 APK** 上重取，缺一不关闭 |

**硬门（先于一切设备命令）**：**必须先重建重装**。批次 33 的 14 套件绿轮对应 14:44–15:13 的构建源，
现树已有 4 个测试文件不同（`docs/research/2026-10-29-batch-33-record.md:225-249`），**不得引用**。

---

## 1. KD-80 收尾判据（80-C2 设备侧）

### 1.1 此前为什么不算数

- 设备测试对 `rowCounts` 只有集合相等：`BackupSnapshotTrimmerInstrumentedTest.kt:45`、
  `BackupSelectionCreateInstrumentedTest.kt:96`、`:164`、`BackupSelectionScaleInstrumentedTest.kt:167`
  均为 `assertEquals(SmbkSelectionPlan.rowCountKeys, …keys)`。
- `grep -c "rowCounts" build/device-track-batch33/step01-backup-final.log` = **0**、
  `grep -c "tutor_visual_snapshot"` = **0**（`docs/known-defects.md:3035` 实读）。
- 集合相等还允许"两边同时错成同一份集合"：`71.json` 与 `SmbkSelectionPlan.allTables` 若一起漂移，
  集合断言仍绿。因此 80-C2 设备侧需要**逐名 + 权威源**两条同时成立。

### 1.2 新增用例（本轨落码，UNVERIFIED）

文件：`core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/backup/BackupRowCountsCoverageInstrumentedTest.kt`
（新建，**2 个 `@Test`**，不改 34 例基线）。

| # | 方法 | 行 | 钉住什么 |
|---|---|---:|---|
| 1 | `manifestRowCountsEnumerateTheNinetySchemaTablesByName` | `:74` | 从**测试 APK 的 assets** 读 `com.tingyun.smartmistakebook.core.database.StudyDatabase/71.json`（`:343`）；断言 `version=71`、`identityHash=714c…`、90 entities / 2 views；对 `entities[].tableName` **逐名** `assertTrue("rowCounts 缺少 71.json 表 $table")`（`:144`）；另从**原始 manifest 文本**解析 `rowCounts` 键序列（`:395`）判缺失/多余/**重复键**；断言 `rowCounts` 恰好 90 键、2 个 view 不进 `rowCounts`；同时断言 `SmbkSelectionPlan.allTables` / `rowCountKeys` 与 71.json **逐名一致** |
| 2 | `everyRowCountMatchesThePackagedAndRestoredDatabaseByName` | `:156` | 全选（mask 15）成包后，先从包内库/恢复库的 `sqlite_master` **逐名**比对实际表集合（`:174`、`:187`；`assertActualTableSetMatches` `:501`，缺失/多余/重复均判红），再对 71.json 的每个表名断言 `rowCounts[table] == 包内库 COUNT(*)`（`:176`）与 `== 恢复库 COUNT(*)`（`:189`）；并断言两张派生索引在全选下仍为 0 |

设计约束（实现已遵守，设备轨不得改）：

- **断言而非打印**：90 个键走逐名 `assertTrue/assertEquals` 循环，失败消息带表名；不靠 `println` 造证据。
- **权威源是设备 APK 内的 `71.json`**，不是源码常量自证。测试 APK 的 assets 来自
  `core/data/build.gradle.kts:34-38`（`assets.srcDir(rootProject.file("core/database/schemas"))`）；
  现有 `data-debug-androidTest.apk` 实测包含 `assets/…/StudyDatabase/71.json`（378,961 B）。
- 逐名计数用 `SELECT COUNT(*) FROM "<table>"`（`:545-566`）；表缺失时抛 `AssertionError("数据库缺少 71.json 表 $table")`。
- 实际表集合从 `sqlite_master` 读（`:473-494`）；Room/SQLite 簿记表（`android_metadata`/`room_master_table`/`sqlite_sequence`）
  与由虚拟表推导出的 FTS4 影子表放行，**不硬编码业务表名**；视图集合也要与 71.json 的两个视图逐名一致（`:533-542`）。
- 表集合**不硬编码**在测试里：开发机与设备读同一份 `71.json`，两边不会各自过期。

### 1.3 设备侧必须逐名写进证据的 90 键（71.json 实读）

```text
active_review_plan_slot
agent_pending_request
answer_reveal_outcome
applied_answer_reveal_record
applied_attempt_record
applied_correction_record
applied_tutor_answer_exposure_record
assessment_answer_reveal_event
assessment_event
assessment_evidence_attribution
assessment_evidence_snapshot
assessment_item_snapshot
assessment_presentation
attempt_correction
attempt_event
attempt_submission
batch_import_job
batch_import_page
binding_audit_sample
binding_change_event
canonical_source_asset
clean_redraw_task
content_install_state
error_book_entry
figure_generation_attempt
figure_generation_operation
independent_correct_observation
knowledge_grounding_request
knowledge_grounding_resolution
knowledge_node
knowledge_node_relation
knowledge_node_source_binding
knowledge_research_review_bundle
knowledge_research_review_source
knowledge_search_feature
knowledge_search_index_state
knowledge_source
knowledge_teaching_material
knowledge_teaching_material_node_binding
learner_chat_evidence
learner_knowledge_mastery_state
learner_problem_memory_state
learner_projection_snapshot
learning_sequence
library_search_content
library_search_fts
library_search_outbox
llm_teaching_advisory
mistake_export_record
model_task
model_task_event
model_task_operation
practice_unit
practice_unit_knowledge_binding
prediction_outcome
presentation_projection_state
problem
problem_classification_binding
problem_classification_candidate
problem_draft
problem_draft_commit_receipt
problem_draft_edit_snapshot
problem_draft_revision
problem_draft_source_asset
problem_organization_receipt
problem_relation
problem_revision
problem_revision_source_asset
problem_revision_visual
projection_archive
projection_outbox
review_log
review_plan
review_queue_item
review_queue_knowledge_node
review_queue_reason
review_session
review_session_advance_receipt
review_session_revision
split_import_job
split_import_question
student_model_prediction
tutor_answer_exposure
tutor_answer_exposure_outcome
tutor_conversation
tutor_message
tutor_message_source_asset
tutor_message_visual
tutor_visual_program
tutor_visual_snapshot
```

证据结论必须贴出这份 90 行清单（或等价的 `表名 = COUNT(*)` 90 行），并注明它取自**同一次设备运行所用的
`data-debug-androidTest.apk` 内 `assets/…/71.json`**（记录该 asset 的 sha256）。**不得**只写
"`OK (2 tests)` / 90 键已覆盖"而不列名——这正是批次 33 判 PARTIAL 的原因。

### 1.4 80-C2 静态半边（本轮已实跑）

命令与批次 33 判据 §1.4 相同（仓库根，只读）：

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

本轮实跑输出：

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

设备轨**必须重跑一次**并落盘（源可能在本轮之后继续变）；`RESULT=FAIL`/`EXIT≠0` 时不得进入设备步骤。

### 1.5 80-C2 设备侧通过条件

1. `am instrument` 摘要 **`OK (2 tests)`**；`Tests run: 2, Failures: 0, Errors: 0`；无 skipped/ignored。
2. 原始 `-r` 输出含两个方法名（不得用 `-e class ...#method` 只跑子集）。
3. logcat 窗口无 `FATAL EXCEPTION` / `SQLiteException` / `FileNotFoundException` / `AssertionError`。
4. 证据结论贴出 §1.3 的 90 行清单 + asset sha256 + `OK (2 tests)` + 两个方法名。
5. 该轮 APK 来自 §2 的**重建重装**，dex 门含 `BackupRowCountsCoverageInstrumentedTest` 与两个方法名（§4.4）。

### 1.6 KD-80 关闭规则（四条缺一不可）

```text
KD-80 closed ⇔ 80-C1 ∧ 80-C2 ∧ 80-C3 ∧ 80-C4
             且四条的设备证据来自同一份「构建前源快照」与同一个 APK sha256
```

本批设备轨必须在**同一轮重建 APK** 上重取：

| 判据 | 设备命令 | 目标 | 备注 |
|---|---|---|---|
| 80-C1 | `BackupRestoreInstrumentedTest` | `OK (34 tests)` | 日志 `BackupSnapshotTrimException` / `expected:<71>` / `expected:<70>` 均 0 |
| 80-C2 | `BackupRowCountsCoverageInstrumentedTest` | `OK (2 tests)` | §1.5；静态半边 §1.4 同轮重跑 |
| 80-C3 设备 | `BackupSnapshotTrimmerInstrumentedTest` | `OK (9 tests)` | — |
| 80-C3 JVM | `:core:data:testDebugUnitTest`（§3.3） | 12/12 + 17/17，exit=0 | 仓内命令日志，不是 `build/` XML 旁证 |
| 80-C4 | `BackupVisualReferenceInstrumentedTest` | `OK (5 tests)` | 与 80-C1 同一 APK |

只满足其中几条 → **KD-80 保持 open**，可写"某条已核销"。

---

## 2. 硬门：必须先重建重装

### 2.1 为什么不能引用批次 33 的绿轮

- 批次 33 的 14 套件绿轮构建于 **14:44–15:13**；`src-sha-before-c4c.txt` 对现树实测
  **858/862 通过、4 FAILED**（`docs/research/2026-10-29-batch-33-record.md:225-249`）。
- 具体缺口：仓库套件设备 **11 例**、现树 **12 例**；第 12 例
  `stagingWhenRetainedBytesStillExceedTheBudgetRejectsBeforeAnyWrite`（`:605`）与第 10 例补强的
  "preflight 淘汰集必须含在飞行行"断言**从未上设备**；提交、结构门、retention 三个文件也不同。
- 本轨又新增 `BackupRowCountsCoverageInstrumentedTest.kt`。旧 APK 里没有它，跑旧包只会得到
  "class not found" 或 0 例——**旧包的绿不能替代现树**。
- 本轨对现存的批次 33 `core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk`
  （14:58）实跑只读 dex 检索：`BackupRowCountsCoverageInstrumentedTest` = **0**、
  `stagingWhenRetainedBytesStillExceedTheBudgetRejectsBeforeAnyWrite` = **0**。
  即旧包**物理上不含**本批新增/补强的两个符号；不重建重装就一定跑不到它们。

### 2.2 构建前一致性门

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

### 2.3 构建前源快照

```bash
D=build/device-track-batch34
mkdir -p "$D"
find app/src core/model/src core/data/src core/database/src -type f -print0 | sort -z \
  | xargs -0 sha256sum > "$D/src-sha-before.txt"
```

### 2.4 构建（只允许 assemble*，禁 `connectedAndroidTest`）

```bash
./gradlew --console=plain \
  :app:assembleStrictOfflineDebug \
  :app:assembleStrictOfflineDebugAndroidTest \
  :core:data:assembleDebugAndroidTest \
  :core:database:assembleDebugAndroidTest
echo "BUILD_EXIT=$?"          # 必须 0
```

构建后再取源快照并比对：

```bash
find app/src core/model/src core/data/src core/database/src -type f -print0 | sort -z \
  | xargs -0 sha256sum > "$D/src-sha-after.txt"
diff -u "$D/src-sha-before.txt" "$D/src-sha-after.txt" \
  && echo "SOURCE STABLE" || { echo "SOURCE CHANGED DURING BUILD — REBUILD"; exit 1; }
```

**不一致 = 构建早于源改动（D 类）**：前一轮日志作废，重建重装。

**集合口径（2026-10-29 复核修复轮更正）**：`core/model/src` 必须在内——A-02/A-03 与
`FigureGenerationPolicy` 的改动都在 model 模块，且会编进 app APK / data 测试 APK。批次 34 实际
执行的命令只含 `app/src core/data/src core/database/src`（864 文件），因此那次 `SOURCE STABLE`
不覆盖 model；事后 `find core/model/src -type f -newermt "2026-10-29 18:43:40"` 为空（恰好未漂移），
但那是事后核对，不是构建时门。扩集合后为 973 文件。

### 2.5 四包 sha256 与安装字节核对

```bash
sha256sum app/build/outputs/apk/strictOffline/debug/app-strictOffline-debug.apk
sha256sum app/build/outputs/apk/androidTest/strictOffline/debug/app-strictOffline-debug-androidTest.apk
sha256sum core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk
sha256sum core/database/build/outputs/apk/androidTest/debug/database-debug-androidTest.apk

export MSYS_NO_PATHCONV=1
A=C:/Android/Sdk/platform-tools/adb.exe
S=emulator-5562                 # 5566/5584 属他线，禁碰；执行前重新登记

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

- 四条均 `Success`；设备侧 sha256 与宿主 APK 逐字节一致。
- `INSTALL_FAILED_UPDATE_INCOMPATIBLE` → 先 `adb uninstall <精确包名>` 再装，舞步记入证据。
- **只有安装成功且 sha 对上，才允许跑 §4.5**。

---

## 3. 现树新测试源必须重跑（仓库 12 / 提交 12 / 结构门 5）

### 3.1 三份源的设备轮缺口（本批必须消除）

| 套件 | 文件 | 现树计数 | 设备旧读数 | 本批目标 | 缺口 |
|---|---|---:|---:|---:|---|
| 仓库 | `core/data/src/androidTest/.../visual/TutorVisualCommitRepositoryInstrumentedTest.kt` | **12**（`:60,101,141,204,278,320,370,412,442,508,605,692`） | 11 | `OK (12 tests)` | 第 12 例 `stagingWhenRetainedBytesStillExceedTheBudgetRejectsBeforeAnyWrite`（`:605`）与第 10 例 `:508` 的"preflight 淘汰集含在飞行行"断言从未上设备 |
| 提交 | `core/database/src/androidTest/.../TutorVisualCommitInstrumentedTest.kt` | **12**（含 `sceneDigestThatDoesNotMatchItsContentIsRejectedBeforeAnyWrite`，`:235`） | 12（旧源） | `OK (12 tests)` | 旧绿轮对应旧源；scene digest 例需在新 APK 上复跑 |
| 结构门 | `core/database/src/androidTest/.../TutorVisualStructureGatesInstrumentedTest.kt` | **5**（`:64,139,174,240,292`） | 5（旧源） | `OK (5 tests)` | 旧绿轮对应旧源；KDoc 已在 HEAD 改过，需重建后复跑 |

> 规则：**设备报告数 < 构建前源计数 = D（旧包）**，停在该步，重建重装。
> 不得把"少跑一例也算绿"写成通过，也不得为了对齐而删/改测试。

### 3.2 构建前计数复读（写进证据头）

```bash
for f in \
  core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/backup/BackupRowCountsCoverageInstrumentedTest.kt \
  core/database/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/database/TutorVisualStructureGatesInstrumentedTest.kt \
  core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/backup/BackupRestoreInstrumentedTest.kt \
  core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/backup/BackupSnapshotTrimmerInstrumentedTest.kt \
  core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/backup/BackupVisualReferenceInstrumentedTest.kt \
  core/database/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/database/TutorVisualCommitInstrumentedTest.kt \
  core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/visual/TutorVisualCommitRepositoryInstrumentedTest.kt \
  core/database/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/database/FullMigrationMatrixInstrumentedTest.kt \
  core/database/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/database/TutorVisualSnapshotMigration_69_70InstrumentedTest.kt \
  core/database/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/database/TutorVisualSnapshotIdentityMigration_70_71InstrumentedTest.kt \
  core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualDrawOpsParityInstrumentedTest.kt \
  core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualDrawOpsPainterInstrumentedTest.kt \
  core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualDrawOpsPdfPainterInstrumentedTest.kt \
  app/src/androidTest/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsIsolationInstrumentedTest.kt \
  app/src/androidTest/kotlin/com/tingyun/smartmistakebook/visualjs/VisualMeasureReplayPaintFlagsInstrumentedTest.kt; do
  printf "%3d %s\n" "$(grep -c '@Test' "$f")" "$f"
done
```

本轨实读结果（以构建前复读为准）：

```text
  2 .../BackupRowCountsCoverageInstrumentedTest.kt
  5 .../TutorVisualStructureGatesInstrumentedTest.kt
 34 .../BackupRestoreInstrumentedTest.kt
  9 .../BackupSnapshotTrimmerInstrumentedTest.kt
  5 .../BackupVisualReferenceInstrumentedTest.kt
 12 .../TutorVisualCommitInstrumentedTest.kt
 12 .../TutorVisualCommitRepositoryInstrumentedTest.kt
 15 .../FullMigrationMatrixInstrumentedTest.kt
  4 .../TutorVisualSnapshotMigration_69_70InstrumentedTest.kt
  3 .../TutorVisualSnapshotIdentityMigration_70_71InstrumentedTest.kt
 16 .../VisualDrawOpsParityInstrumentedTest.kt
 24 .../VisualDrawOpsPainterInstrumentedTest.kt
 16 .../VisualDrawOpsPdfPainterInstrumentedTest.kt
 13 .../VisualJsIsolationInstrumentedTest.kt
  2 .../VisualMeasureReplayPaintFlagsInstrumentedTest.kt
```

### 3.3 80-C3 的 JVM 半边（串行门禁脚本，不是设备）

```bash
./gradlew --console=plain :core:data:testDebugUnitTest \
  --tests "com.tingyun.smartmistakebook.core.data.backup.SmbkSelectionPlanTest" \
  --tests "com.tingyun.smartmistakebook.core.data.visual.VisualCheckpointRetentionTest" \
  --rerun-tasks
```

通过：exit=0；`tests="12"` / `tests="17"`，`failures="0"`、`errors="0"`、`skipped="0"`；命令、时间与 XML 路径进证据。
`build/` 下的 XML 只能作旁证，不能单独把 80-C3 记 PASS。

---

## 4. 设备轨串行命令与 dex 符号门

> 严格串行，一条完成再下一条。`export MSYS_NO_PATHCONV=1` 全程生效。Gradle 只允许 `assemble*`；
> **禁 `connectedAndroidTest`**。每条套件前清 logcat，跑完落 raw log + logcat。

### 4.0 步 0：设备再登记

```bash
$A devices -l
$A -s "$S" shell getprop ro.build.version.sdk
$A -s "$S" shell getprop ro.product.cpu.abi
$A -s "$S" shell getconf PAGE_SIZE
$A -s "$S" shell getprop ro.build.fingerprint
```

通过：`device` 态、非 offline/unauthorized；serial/API/ABI/PAGE_SIZE/fingerprint 五值进证据头。
优先 API 34 / x86_64 / 4 KB；换设备则注明**不能跨设备比较像素**（一致门）。

### 4.1 构建前门 → 构建 → 安装

见 §2.2 → §2.3 → §2.4 → §2.5。**顺序不可颠倒**。

### 4.2 schema asset 门（新增：rowCounts 用例的权威源）

```bash
APK=core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk
unzip -l "$APK" | grep -q "assets/com.tingyun.smartmistakebook.core.database.StudyDatabase/71.json" \
  || { echo "MISSING 71.json in $APK"; exit 1; }
unzip -p "$APK" "assets/com.tingyun.smartmistakebook.core.database.StudyDatabase/71.json" \
  | sha256sum           # 进证据头
```

通过：存在且 sha256 进证据。缺失 = 测试 APK 的 assets 未合并（D 类），不得继续跑 80-C2 用例。

### 4.3 dex 符号门（防旧包跑新门）

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

**core:data 测试包**（备份 34 + rowCounts 2 + 裁剪器 9 + C4 5 + painter 24 + PDF 16 + parity 16 + 仓库 12）：

```bash
need core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk \
  BackupRestoreInstrumentedTest \
  schema68ArchiveRestoresAndMigratesToCurrentVersion \
  BackupRowCountsCoverageInstrumentedTest \
  manifestRowCountsEnumerateTheNinetySchemaTablesByName \
  everyRowCountMatchesThePackagedAndRestoredDatabaseByName \
  BackupSnapshotTrimmerInstrumentedTest \
  BackupVisualReferenceInstrumentedTest \
  fullSelectionRoundTripPreservesAllVisualRowsAndReferences \
  subsetNineKeepsRevisionPinsAndInitialBaselinesOnly \
  subsetThirteenKeepsMessageLinksButTrimsUnreferencedStaging \
  archiveAndRestoredDatabaseAgreeOnVisualRowIdentity \
  repackagedArchiveWhoseLinkPointsAtAMissingSnapshotIsRejectedBeforeSwap \
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
  stagingWhenEvictionIsRefusedByAnInFlightLeaseStillRejectsWithACode \
  stagingWhenRetainedBytesStillExceedTheBudgetRejectsBeforeAnyWrite \
  commitIntoAnOverBudgetProgramEvictsAnExpiredUnpinnedStagingIntermediate \
  "预检淘汰集必须包含在飞行 owner 的行"
```

**core:database 测试包**（迁移矩阵 15 + 69→70 4 + 70→71 3 + 结构门 5 + 提交 12）：

```bash
need core/database/build/outputs/apk/androidTest/debug/database-debug-androidTest.apk \
  FullMigrationMatrixInstrumentedTest \
  everyExportedSchemaVersionMigratesToCurrentWithoutDestructiveFallback \
  TutorVisualSnapshotMigration_69_70InstrumentedTest \
  visualTablesAreCreatedEmptyWithoutTouchingV69RowsAndIdempotent \
  onlyLiveSnapshotsCanBeLinkedAndCasUseChangedRowCounts \
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

通过：四个 `DEX OK`；任一 `MISSING` = 旧包/漏装（D），**停**，重建重装，不得继续跑。

### 4.4 套件串行（15 套件）

```bash
# 沿用 §2.5 的 A / S
D=build/device-track-batch34
mkdir -p "$D"
run() {  # run <step> <pkg> <class>
  step="$1"; pkg="$2"; cls="$3"
  $A -s "$S" logcat -c
  $A -s "$S" shell am instrument -w -r -e class "$cls" \
    "$pkg/androidx.test.runner.AndroidJUnitRunner" | tee "$D/$step.log"
  $A -s "$S" logcat -v threadtime -d > "$D/$step-logcat.txt"
}

# 1) 备份 34（80-C1 + 80-C2 的恢复路径基线）
run step01-backup com.tingyun.smartmistakebook.core.data.test \
  com.tingyun.smartmistakebook.core.data.backup.BackupRestoreInstrumentedTest

# 2) rowCounts 2（80-C2 设备侧逐名）
run step02-rowcounts com.tingyun.smartmistakebook.core.data.test \
  com.tingyun.smartmistakebook.core.data.backup.BackupRowCountsCoverageInstrumentedTest

# 3) 结构门 5
run step03-structure-gates com.tingyun.smartmistakebook.core.database.test \
  com.tingyun.smartmistakebook.core.database.TutorVisualStructureGatesInstrumentedTest

# 4) 裁剪器 9（80-C3 设备半边）
run step04-trimmer com.tingyun.smartmistakebook.core.data.test \
  com.tingyun.smartmistakebook.core.data.backup.BackupSnapshotTrimmerInstrumentedTest

# 5) 视觉四表引用 5（80-C4）
run step05-c4-visual com.tingyun.smartmistakebook.core.data.test \
  com.tingyun.smartmistakebook.core.data.backup.BackupVisualReferenceInstrumentedTest

# 6) 迁移矩阵 1→71（15）
run step06-migration-matrix com.tingyun.smartmistakebook.core.database.test \
  com.tingyun.smartmistakebook.core.database.FullMigrationMatrixInstrumentedTest

# 7) 69→70（v69→71，4）
run step07-migration-69-70 com.tingyun.smartmistakebook.core.database.test \
  com.tingyun.smartmistakebook.core.database.TutorVisualSnapshotMigration_69_70InstrumentedTest

# 8) 70→71 专用（3）
run step08-migration-70-71 com.tingyun.smartmistakebook.core.database.test \
  com.tingyun.smartmistakebook.core.database.TutorVisualSnapshotIdentityMigration_70_71InstrumentedTest

# 9) 提交（12）
run step09-visual-commit com.tingyun.smartmistakebook.core.database.test \
  com.tingyun.smartmistakebook.core.database.TutorVisualCommitInstrumentedTest

# 10) 仓库（12）
run step10-visual-repo com.tingyun.smartmistakebook.core.data.test \
  com.tingyun.smartmistakebook.core.data.visual.TutorVisualCommitRepositoryInstrumentedTest

# 11) 一致门 16（原型门，不得写成产品门）
run step11-parity com.tingyun.smartmistakebook.core.data.test \
  com.tingyun.smartmistakebook.core.data.visual.VisualDrawOpsParityInstrumentedTest

# 12) painter 24
run step12-painter com.tingyun.smartmistakebook.core.data.test \
  com.tingyun.smartmistakebook.core.data.visual.VisualDrawOpsPainterInstrumentedTest

# 13) PDF 16
run step13-pdf com.tingyun.smartmistakebook.core.data.test \
  com.tingyun.smartmistakebook.core.data.visual.VisualDrawOpsPdfPainterInstrumentedTest

# 14) 隔离 13
run step14-isolation com.tingyun.smartmistakebook.offline.test \
  com.tingyun.smartmistakebook.visualjs.VisualJsIsolationInstrumentedTest

# 15) flags 2
run step15-flags com.tingyun.smartmistakebook.offline.test \
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

| step | 目标 | 备注 |
|---|---|---|
| step01-backup | `OK (34 tests)` | 80-C1 |
| step02-rowcounts | `OK (2 tests)` | 80-C2 设备侧；证据需 90 行清单 |
| step03-structure-gates | `OK (5 tests)` | 现树 5 例 |
| step04-trimmer | `OK (9 tests)` | 80-C3 设备 |
| step05-c4-visual | `OK (5 tests)` | 80-C4 |
| step06-migration-matrix | `OK (15 tests)` | — |
| step07-migration-69-70 | `OK (4 tests)` | — |
| step08-migration-70-71 | `OK (3 tests)` | — |
| step09-visual-commit | `OK (12 tests)` | 现树 12 例 |
| step10-visual-repo | `OK (12 tests)` | 现树 12 例；< 12 = D |
| step11-parity | `OK (16 tests)` | 原型门 |
| step12-painter | `OK (24 tests)` | — |
| step13-pdf | `OK (16 tests)` | — |
| step14-isolation | `OK (13 tests)` | — |
| step15-flags | `OK (2 tests)` | — |

### 4.5 签名扫描（全部 logcat）

```bash
for f in "$D"/*-logcat.txt; do
  printf "%s: " "$(basename "$f")"
  grep -cE "FATAL EXCEPTION|ANR in |NoSuchMethodError|NoClassDefFoundError|UnsatisfiedLinkError|SIGSEGV|CANNOT LINK|failed to map segment|death was not confirmed" "$f"
done   # 期望全 0
```

### 4.6 证据头最低字段

serial / API / ABI / PAGE_SIZE / fingerprint / HEAD / 工作树状态 / 四 APK sha256 /
测试 APK 内 `71.json` asset sha256 / `src-sha-before` 与 `src-sha-after` 的 diff 结果 /
构建前各套件 `@Test` 计数 / 每步目标字符串与实际 summary / 90 键清单 / 红例归因（§5.4 字段）。
缺任一 = 证据不成立。

---

## 5. 红例归因规则（先 A/B/D，最后 C）

### 5.1 绝对禁止

- 删除/弱化断言、放宽 `assertEquals`、`@Ignore`/`Assume` 跳过、用 `-e class ...#method` 过滤红例、
  把 `OK (N tests)` 的 N 改小、把失败改写成"已知问题"后仍声称通过。
- **先改测试让它变绿、再回头找原因。**
- 以"批次 33 曾 14 套件全绿 / 另一台设备绿"替代当前树/当前 APK 的证据。
- 把一致门（prototype）的红/绿写成产品门或发版结论。
- 用"`OK (2 tests)`"代替 90 键逐名证据；用"表在 90 张清单里"代替每表行数相等。

### 5.2 分类与处置

| 类 | 含义 | 处置 |
|---|---|---|
| **A（夹具/门逻辑缺陷）** | 失败在测试辅助代码、夹具形状、或门自身的分辨力断言；产品路径未被有效执行 | 改夹具/门逻辑；产品源零改动；**不得借机放宽阈值** |
| **B（测试期望漂移）** | 实际值与权威源（`71.json`/常量/契约/编译器/探针）一致，期望写错 | **改成权威值**（不是改成实测值），保留 delta 0，补权威源 `path:line` |
| **D（设备/旧包/旧源）** | 设备 offline、装错旧包、API/ABI/PAGE_SIZE 不符、资产未合并、构建早于源改动 | 重登记/重建重装/核 sha256 与 dex/asset 门；**跨设备像素差异不作产品缺陷** |
| **C（产品缺陷）** | 期望符合权威源，产品输出/行为错 | 修产品；保留/加强测试；已知缺口登记 KD，**不得删测试或放宽** |

**先 A/B/D，再 C**；闭环要求：红 log → 归因证据（逐源 `path:line`）→ 改动清单 → **重建重装（新 sha256）**
→ 同命令复跑绿 log。未复跑 = UNVERIFIED。

### 5.3 批次 34 专用陷阱

| 现象 | 先判 | 处置 |
|---|---|---|
| `BackupRowCountsCoverageInstrumentedTest` 红 `FileNotFoundException … StudyDatabase/71.json` | **D**（测试 APK assets 未合并/旧包） | 查 §4.2 asset 门；重建重装；不得在测试里改成读仓库绝对路径 |
| rowCounts 红 `缺少 71.json 表 X` / `数量不是 90` | 先 **D**（旧 asset）→ **B**（`71.json` 与 `SmbkSelectionPlan` 漂移）→ **C**（成包漏表） | 先跑 §1.4 静态脚本；漂移按权威源修常量/清单，**不得改测试期望** |
| rowCounts 红 `包内库 COUNT(*) 与 manifest rowCounts 不符：X` | 先 **B**（全选裁剪语义写错）→ **C**（manifest 与载荷漂开） | 只有两表派生索引允许为 0；其余不等即产品错误，查裁剪/成包写回路径 |
| rowCounts 红 `恢复库 COUNT(*) … 不符：X` | **B**（恢复阶段少复制/多复制）→ **C** | 查 Phase 2b/2c；不得删该表断言 |
| rowCounts 红 `数据库缺少 71.json 表 X` | **D**（旧 asset）→ **C**（schema/迁移未建表） | 先核 `71.json` 与 `StudyDatabase_Impl` 同值；不得在测试里跳过缺表 |
| 仓库不是 12 / 提交不是 12 / 结构门不是 5 | 先看构建前复读：源计数也变了 = 正常漂移（记录）；设备数 < 源数 = **D** | 重建；dex 门查新方法名 |
| 仓库第 12 例红（净值仍超仍写库） | **A**（夹具净值算错）→ **C**（事务内重判失效） | 查 plan 淘汰集与端口命令；不得把 `assertNull` 改 `assertNotNull` |
| 提交 scene digest 例红 | **B**（摘要口径）→ **C**（`RoomStudyDatabase` 缺第三条 `require`） | 查 `scene_sha256 == sha256(scene_json)`；不得删用例 |
| 结构门红 `NoSuchElementException: Key snap-staging-middle` | 先 **D**（dex 缺 `snap-staging-keeper`）→ **A/C** | 缺符号 = 旧包；有符号仍红查读侧与淘汰 SQL |
| 备份红 `快照 schema=71，裁剪器只支持 schema=70` | 先 **D**（旧包）→ **C**（常量回填未完成） | 查 `SmbkSelectionPlan.kt:44` 与 `BackupSnapshotTrimmer.kt:268-283` |
| 备份 `expected:<70> but was:<71>` | **D**（旧测试包） | dex 门查 `schema68ArchiveRestoresAndMigratesToCurrentVersion`；重装 |
| `NoClassDefFoundError` / `UnsatisfiedLinkError` / `FATAL EXCEPTION` | **D** | 重建重装；查签名扫描 |
| 跨设备像素差 | **D** | 只在同设备内比较；不得判产品缺陷 |

### 5.4 归因记录最低字段

红例名 + 行号 + 失败消息 + 第一项目帧 + 四源核对结果（逐源 `path:line`）+ 类别（A/B/C/D）+
处置（改哪个文件哪几行/或不改的理由）+ 重建 sha256 + 绿轮 summary。缺任一字段 = 归因不成立。

---

## 6. 本轨未验证项

1. 新增 `BackupRowCountsCoverageInstrumentedTest.kt` **未编译、未上设备**（本轨不运行 Gradle/adb）→
   `OK (2 tests)` UNVERIFIED。编译风险点：`InstrumentationRegistry.getInstrumentation().context.assets`
   的 asset 路径、`StudySeedBundle` 构造参数、`seedStudyFacts` 的 internal 可见性（同模块 androidTest）。
2. 仓库 12 / 提交 12 / 结构门 5 的设备复跑 UNVERIFIED；文中计数是 `grep -c '@Test'` 静态读数。
3. 80-C1/C3/C4 的重跑 UNVERIFIED；批次 33 的绿轮对应旧源，不得引用。
4. 80-C3 的 JVM 半边 UNVERIFIED（本轨不运行 Gradle）。
5. 设备在线状态未重新登记（§4.0 由设备轨执行）；`emulator-5562` 只是示例 serial。
6. 90 键清单取自 `71.json` 实读（version 71 / hash `714c…` / 90 entities / 2 views），
   设备运行时必须从**同一次构建的测试 APK** 内 asset 重新取 sha256；两者不一致按 D 处理。

---

## 7. 来源

- 计划：`.zcode/plans/plan-2026-10-29-batch-34-dual-backend-kd80.md:13-14,24-33`
- 基线：`docs/research/2026-10-29-batch-34-baseline.md:88-121`（KD-80 现状态与四条判据）
- KD-80：`docs/known-defects.md:2985`、`:3034-3045`
- 批次 33 判据：`docs/research/2026-10-29-batch-33-device-criteria.md:219-263`（80-C1/C2）、`:455-604`（串行/dex/归因）
- 批次 33 记录：`docs/research/2026-10-29-batch-33-record.md:225-249`（设备源与现树 4 文件差、仓库 11 vs 12）
- 新用例：`core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/backup/BackupRowCountsCoverageInstrumentedTest.kt:56,74,144,156,174,187,343,501,574`
- 仓库 12：`core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/visual/TutorVisualCommitRepositoryInstrumentedTest.kt:508,605,692`
- 提交 12：`core/database/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/database/TutorVisualCommitInstrumentedTest.kt:235`
- 结构门 5：`core/database/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/database/TutorVisualStructureGatesInstrumentedTest.kt:64,139,174,240,292`
- schema asset 合并：`core/data/build.gradle.kts:34-38`
- 71.json：`core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/71.json`
- 本轮实跑：§1.4 的 `S=71 python` 脚本 → `RESULT=PASS` / `EXIT=0`；其余为只读 `grep/stat/python`。

---

## 8. 设备执行结果回填（2026-10-29 18:42–19:02，设备验证轨）

> 本节由设备验证轨在**执行后**追加，不改上文判据。完整证据：
> `docs/research/evidence/2026-10-29-batch-34/00-index.md`（E-00…E-08）。
> 设备 `emulator-5562`（API 34 / x86_64 / 4 KB）；HEAD `db792fa8`。

| 项 | 目标 | 实际 | 判定 |
|---|---|---|---|
| 硬门：重建重装 | 构建 exit=0 + 源快照稳定 + 四包 install Success + 设备 sha 一致 | `BUILD SUCCESSFUL in 42s`；864 文件源快照前后 diff 空；4/4 `Success`；4/4 sha 一致 | PASS |
| schema asset / dex 门 | APK 内 `71.json` + 新符号 | asset sha `4a13d9d0…`（= 仓库源）；3 个测试 APK 全 `DEX OK` | PASS |
| 80-C1 备份 | `OK (34 tests)` | `OK (34 tests)` | PASS |
| 80-C2 设备侧 | `OK (2 tests)` + 90 键逐名 | `OK (2 tests)`；90 键清单见 E-02 §4.1 | PASS |
| 80-C3 设备 | `OK (9 tests)` | `OK (9 tests)` | PASS |
| 80-C3 JVM | 12/17，exit=0 | 本轨只允许 `assemble*`，未执行 | UNVERIFIED（单测门） |
| 80-C4 | `OK (5 tests)` | `OK (5 tests)` | PASS |
| 结构门 | `OK (5 tests)` | `OK (5 tests)` | PASS |
| 提交 / 仓库 | `OK (12 tests)` / `OK (12 tests)` | 12 / 12 | PASS |
| 迁移 15 / 4 / 3 | 各自 `OK` | 15 / 4 / 3 | PASS |
| parity / painter / PDF | 16 / 24 / 16 | 16 / 24 / 16 | PASS |
| 隔离 / flags | 13 / 2 | 13 / 2 | PASS |
| 12 类崩溃签名（15 份 logcat） | 全 0 | 全 0 | PASS |
| worker 生命周期 | spawn↔ready 1:1、杀因全归因、无存活 | 17/17；服务杀因 15 + AMS 系统回收 2 = 17；无未归类；无存活 | PASS |
| A-01/A-02/A-03 | 0 远程派发、广告未动 | 静态核对 PASS；无新增 androidTest 用例 | PASS（静态） |

**红例**：0。KD-80 的关闭仍取决于 80-C3 JVM 半边与记录员对"同一源快照 + 同一 APK"的裁定
（本轨只提供设备事实，不代写 KD 关闭结论）。

