<!-- 体积自查：约 110 行；R4 上限 400 行 / 65536 B -->
# E-41-D02 schema 73 设备复现（迁移矩阵 1→73 / 备份 / 裁剪 / drill / 错因账本）

| 字段 | 值 |
|---|---|
| evidence-id | E-41-D02 |
| convention-version | 1 |
| batch | 2026-11-03-batch-41 |
| step | 设备轨步骤二（计划 ask 2；对应计划 §2 WS5 32–36） |
| gate-or-kd | schema 73 设备不回退；批次 40 记录 §9.1-4 |
| verdict | **PASS**：matrix 16/16、backup 34/34、rowCounts 2/2、trimmer 10/10、c4 8/8、错因账本 3/3、drill 4 直跑 + 2 两阶段；全部 `am instrument` exit=0 |
| executed-at | 2026-11-03T00:50–00:56+08:00（UTC 16:50–16:56Z） |
| operator | 设备轨 |
| head | `62522930`（源快照 `src-sha-after-fix.txt`，见 E-41-D01 §2） |
| device-serial | emulator-5584（API 34 / x86_64 / 4096） |
| apk-sha256 | data test `82a6f21e…fac02`；database test `6f55edbf…49291b`；均见 E-41-D01 §3 |
| exit-code | 7×`am instrument` exit=0；drill 阶段 A exit=0 且 `shortMsg=Process crashed.`（设计），阶段 B exit=0 |
| raw-log | `build/device-track-batch41/suites/suite-*.log` + `suite-*-logcat.txt`；`run-index.txt` |
| record-ref | 批次 40 记录 §8 #15/#16/#19、§9.1-4；证据 `evidence/2026-11-02-batch-40/step02b-schema73-migration-backup.md` |
| sanitized | 是 |

## 1. 逐套件结果（串行，同一设备/APK）

| 套件（FQCN 简写） | 包 | 源码 `@Test` | 实测 | Time | code0 / skip(-4) |
|---|---|---|---|---|---|
| `core.database.FullMigrationMatrixInstrumentedTest` | DB | 16 | `OK (16 tests)` | 221.36 s | 16 / 0 |
| `core.data.backup.BackupRestoreInstrumentedTest` | DATA | 34 | `OK (34 tests)` | 20.104 s | 34 / 0 |
| `core.data.backup.BackupRowCountsCoverageInstrumentedTest` | DATA | 2 | `OK (2 tests)` | 2.111 s | 2 / 0 |
| `core.data.backup.BackupSnapshotTrimmerInstrumentedTest` | DATA | 10 | `OK (10 tests)` | 36.223 s | 10 / 0 |
| `core.data.backup.BackupVisualReferenceInstrumentedTest`（c4） | DATA | 8 | `OK (8 tests)` | 7.618 s | 8 / 0 |
| `core.database.ErrorCauseLedgerInstrumentedTest` | DB | 3 | `OK (3 tests)` | 0.614 s | 3 / 0 |
| `core.data.backup.DeviceMigrationDrillInstrumentedTest` | DATA | 6 | `OK (6 tests)` | 7.283 s | 4 / 2（两阶段补跑） |

- matrix 首例 `everyExportedSchemaVersionMigratesToCurrentWithoutDestructiveFallback` 即 1→73 全量迁移；`INSTRUMENTATION_CODE: -1`（runner 正常结束）、无 `FAILURES!!!`。
- 与计划 ask 的计数差异（如实）：
  - trimmer 计划写 **9**，实测 **10**；多出的是批次 41 WS1 item 7 新增的 `schema73IdentityHashMismatchFailsClosedBeforeAnyTrim`（`BackupSnapshotTrimmerInstrumentedTest.kt:335`），不是复制/放宽。
  - 错因账本计划写 **1**，实测 **3**；批次 40 原有 `appendAcceptRetractAndSupersedeKeepOneActiveRow`，本批 WS6 增加 `candidateRejectedAndRetractedStatesLeaveNoActiveLabelBehind`（`:155`）与 `aFailingVoidRollsBackTheRetractionRowAndKeepsTheBindingActive`（`:268`）。

## 2. drill 6 例的两阶段（不是跳过）

- 主跑 `OK (6 tests)` 中 4 例 code0，2 例 `-4`（assumption，需显式参数/交接件）。
- 阶段 A：`-e smb_allow_data_reset true -e smb_d1_kill_stage true -e class ...#killProcessHandoffStagesArchiveThenDies` → `INSTRUMENTATION_RESULT: shortMsg=Process crashed.`；`INSTRUMENTATION_CODE: 0`；adb exit=0（进程自杀是设计产物）。
- 阶段 B：`-e smb_allow_data_reset true -e class ...#restoreFromHandoffAfterProcessDeath` → `OK (1 test)`，Time 2.034 s。
- 结论：6 个场景均实际执行；单次调用下 `OK (6 tests)` 且 skip=0 不成立，`-4` 不计绿。

## 3. schema 事实（本轮实读/实跑）

- `STUDY_DATABASE_VERSION=73`（`core/database/.../StudyDatabase.kt:142`），`MIGRATION_71_73` / `ERROR_CAUSE_MIGRATION_72_73` 已注册（`:423-424`）。
- `73.json` 73 份资产随 data 测试包发布；`73.json` = 92 表 / 2 view / identityHash `1d086050…`（E-41-D01 §4）。
- matrix 含 v72 种子行 → 73 后旧行仍在、两错因表为空、`active_key` 唯一索引存在的断言（批次 40 已登记，本轮随 16/16 复现）。

## 4. 未验证 / 边界

- matrix 的 16 例是否逐版本（1..73 每级）全部覆盖，本轮只按套件整体绿记录；未逐方法打印旧库版本号。
- 未做 16KB 页大小、真机、非 API 34 复现。
- 设备结论只绑定 E-41-D01 §3 的 data/database APK sha；01:22 后他线重建的 data test APK（`73ae4fb8…`）未安装、未用于本文件。
