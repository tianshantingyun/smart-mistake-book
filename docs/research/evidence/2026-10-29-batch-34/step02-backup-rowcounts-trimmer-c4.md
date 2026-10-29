# E-02 备份 34 / rowCounts 逐名 2 / 裁剪器 9 / 视觉四表引用 5

| 字段 | 值 |
|---|---|
| evidence-id | E-02 |
| convention-version | 1 |
| batch | 2026-10-29-batch-34 |
| step | 二（KD-80 逐名用例、备份 34、裁剪器 9；另 C4 5） |
| gate-or-kd | KD-80 80-C1 / 80-C2（设备侧）/ 80-C3（设备半边）/ 80-C4 |
| verdict | PASS |
| executed-at | 2026-10-29T18:45:03+08:00 → 18:48:29+08:00 |
| operator | 设备验证轨（子代理） |
| head | `db792fa873f4bc150f415b67d1c2e3b1bab6c995` |
| worktree | 721 条（241 `M` / 480 `??`） |
| device-serial | `emulator-5562` |
| device-api | 34 |
| device-abi | x86_64 |
| device-page-size | 4096 |
| apk | `core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk` |
| apk-sha256 | `53e69f485a4bb4fe3d5c7bdff53ebbfb50008011376df1cbc133e4a946397caf` |
| apk-version | （空） |
| exit-code | 0（四条 `am instrument`） |
| raw-log | `build/device-track-batch34/step01-backup.log`（485 行）、`step02-rowcounts.log`（37 行）、`step04-trimmer.log`（135 行）、`step05-c4-visual.log`（79 行）（本机，不入仓） |
| record-ref | `docs/research/2026-10-29-batch-34-record.md` §4 |
| sanitized | 是（§6） |

## 1. 目的与判据

- **80-C1**：`BackupRestoreInstrumentedTest` = `OK (34 tests)`，0 失败/错误/跳过，log 无
  `BackupSnapshotTrimException`、无 `expected:<70>`/`expected:<69>`。
- **80-C2 设备侧（本批新增）**：`BackupRowCountsCoverageInstrumentedTest` = `OK (2 tests)`；
  逐名断言 manifest `rowCounts` 覆盖测试 APK 内 `71.json` 的 90 张表（含原始文本重复键检测），
  且包内库/恢复库 `sqlite_master` 表集合逐名双向比对、逐表 `rowCounts == COUNT(*)`。
- **80-C3 设备半边**：`BackupSnapshotTrimmerInstrumentedTest` = `OK (9 tests)`。
- **80-C4**：`BackupVisualReferenceInstrumentedTest` = `OK (5 tests)`。

## 2. 命令（可复跑）

```bash
cd "D:/smart mistake book"; export MSYS_NO_PATHCONV=1
A=C:/Android/Sdk/platform-tools/adb.exe
S=emulator-5562
D=build/device-track-batch34
run() { step="$1"; pkg="$2"; cls="$3"
  $A -s "$S" logcat -c
  $A -s "$S" shell am instrument -w -r -e class "$cls" \
    "$pkg/androidx.test.runner.AndroidJUnitRunner" > "$D/$step.log" 2>&1
  $A -s "$S" logcat -v threadtime -d > "$D/$step-logcat.txt" 2>&1; }

run step01-backup   com.tingyun.smartmistakebook.core.data.test \
  com.tingyun.smartmistakebook.core.data.backup.BackupRestoreInstrumentedTest
run step02-rowcounts com.tingyun.smartmistakebook.core.data.test \
  com.tingyun.smartmistakebook.core.data.backup.BackupRowCountsCoverageInstrumentedTest
run step04-trimmer  com.tingyun.smartmistakebook.core.data.test \
  com.tingyun.smartmistakebook.core.data.backup.BackupSnapshotTrimmerInstrumentedTest
run step05-c4-visual com.tingyun.smartmistakebook.core.data.test \
  com.tingyun.smartmistakebook.core.data.backup.BackupVisualReferenceInstrumentedTest
```

- 工作目录：仓库根
- 退出码：四条均 `0`

## 3. 结果摘要

| 套件 | 目标 | 实际 summary | failures/errors/skipped | 判据 |
|---|---|---:|---:|---|
| 备份 | `OK (34 tests)` | `OK (34 tests)`（Time 38.746） | 0 / 0 / 0 | PASS |
| rowCounts | `OK (2 tests)` | `OK (2 tests)`（Time 3.317） | 0 / 0 / 0 | PASS |
| 裁剪器 | `OK (9 tests)` | `OK (9 tests)`（Time 114.141） | 0 / 0 / 0 | PASS |
| C4 视觉四表 | `OK (5 tests)` | `OK (5 tests)`（Time 8.78） | 0 / 0 / 0 | PASS |

四条 `INSTRUMENTATION_CODE: -1`（runner 恒 RESULT_OK，**不作判据**）。

## 4. 关键输出（有界）

rowCounts 两个方法名均在原始 `-r` 流中（未用 `-e class ...#method` 过滤）：

```text
INSTRUMENTATION_STATUS: numtests=2
INSTRUMENTATION_STATUS: test=everyRowCountMatchesThePackagedAndRestoredDatabaseByName
INSTRUMENTATION_STATUS: test=manifestRowCountsEnumerateTheNinetySchemaTablesByName
INSTRUMENTATION_STATUS_CODE: 0
Time: 3.317
OK (2 tests)
INSTRUMENTATION_CODE: -1
```

rowCounts logcat 异常签名：`SQLiteException|FileNotFoundException|AssertionError|FATAL EXCEPTION` = **0**。

### 4.1 80-C2 设备侧 90 键清单（逐名）

来源：**同一次设备运行所用的 `data-debug-androidTest.apk` 内**
`assets/com.tingyun.smartmistakebook.core.database.StudyDatabase/71.json`，
asset sha256 = `4a13d9d0b5ee561f088142267afe0a97890745ff66d57ae2b1191bef47881d94`
（与仓库源 `core/database/schemas/.../71.json` 逐字节相同，见 E-01 §4）。
`entities` 计数 = 90、去重后仍 90（设备用例内断言）；下列为按字典序的 90 个表名：

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

设备用例对这 90 个名字**逐个**执行了三类断言（不是集合相等一次过）：manifest `rowCounts` 键、
包内库 `sqlite_master` 实际表、恢复库 `sqlite_master` 实际表；并对每个表断言
`rowCounts[table] == 包内库 COUNT(*) == 恢复库 COUNT(*)`（全选下仅两张派生索引恒为 0）。
绿轮即这 90×3 个逐名断言全部成立；任一缺失/多余/重复/计数不等都会在设备上以带表名的
`AssertionError` 判红。

## 5. 失败 / 跳过 / 异常

无。四条套件 0 失败 / 0 错误 / 0 跳过。

## 6. 脱敏与去噪

- 删除：`am instrument` 的逐用例 `.` 进度点（保留 numtests/方法名/Time/OK 行）。
- 保留：两条方法名、Time、OK 行、INSTRUMENTATION_CODE、asset sha、90 行表名。
- 扫描：§4.4 命令见 `00-index.md` §6 → 0 命中。

## 7. 判定

**PASS**：80-C1 `OK (34 tests)`；80-C2 设备侧 `OK (2 tests)` 且证据含同源 asset 的 90 键逐名清单；
80-C3 设备半边 `OK (9 tests)`；80-C4 `OK (5 tests)`。四者均来自 E-01 的同一 APK `53e69f48…`。
未覆盖：80-C3 的 JVM 半边（`SmbkSelectionPlanTest` 12 / `VisualCheckpointRetentionTest` 17）
不在本轨权限内（只允许 `assemble*`），由串行门禁脚本另行记录。
