<!-- 体积自查：约 150 行；R4 上限 400 行 / 65536 B -->
# E-40-02b schema 73 迁移矩阵 + 备份清单 + 错因账本（设备）

| 字段 | 值 |
|---|---|
| evidence-id | E-40-02b |
| convention-version | 1 |
| batch | 2026-11-02-batch-40 |
| step | 步骤二B（WS2 schema 73；判据 §46） |
| gate-or-kd | 判据 §46 S73-1…S73-8；KD-80 同类回归 |
| verdict | **PARTIAL**：设备 APK 快照（10:13 建造，DB=73）上全绿；当前工作树的 `StudyDatabase.kt` 曾在 **10:33:32** 被 KB 32B 的文件级还原覆盖回 DB=71（`dee81ea7` 10:45:47 只是事后提交的两个 docs 文件，未触碰该源文件）。**复核轮已按主会话裁定 rebase 到 71（92 表）并接线；rebased `73.json` 已由 KSP 生成（92 表/110 FK），identityHash 已回填 `1d086050…`**；设备绿仍不可复现（绑定 10:13 APK） |
| executed-at | 2026-11-02T10:27–10:51+08:00 |
| operator | 设备验证轨 |
| head | 构建时 `a3f367df`；执行中 HEAD 变为 `dee81ea7`（10:45:47） |
| worktree | 构建时 653 条；执行后源漂移 169 行 / 60 文件 |
| device-serial | emulator-5584（API 34 / x86_64 / page-size 4096） |
| apk-sha256 | data test `34e3435d9fa5ef0b7f18dc0b02cd229d35c9913e2565174c5b619c77bf103179`；database test `f12ef06abfb523c79b40b7e2170f006ed9d462cbb2d3cbba0ff8f6d059668f92` |
| exit-code | 全部 `am instrument` adb-exit=0；见 §2 逐套件 |
| raw-log | `build/device-track-batch40/suites/suite-{errorcause,rowcounts,trimmer,c4,drill,drill-stageA,drill-stageB,backup,matrix}.log` + 同名 `-logcat.txt` |
| record-ref | `docs/research/2026-11-02-batch-40-record.md` |
| sanitized | 是 |

## 1. 设备结果（同一四包 APK / 同一设备，串行）

| 套件 | 包 | 期望（判据/源码） | 实测 | 判定 |
|---|---|---|---|---|
| `ErrorCauseLedgerInstrumentedTest` | DB | 1 | `OK (1 test)`，Time 0.442，code0=1 | PASS |
| `BackupRowCountsCoverageInstrumentedTest` | DATA | 2 | `OK (2 tests)`，Time 2.484，code0=2 | PASS |
| `BackupSnapshotTrimmerInstrumentedTest` | DATA | 9 | `OK (9 tests)`，Time 57.606，code0=9 | PASS |
| `BackupVisualReferenceInstrumentedTest` | DATA | 8 | `OK (8 tests)`，Time 8.458，code0=8 | PASS |
| `BackupRestoreInstrumentedTest` | DATA | 34 | `OK (34 tests)`，Time 25.199，code0=34 | PASS |
| `FullMigrationMatrixInstrumentedTest` | DB | 16（判据写 15；源码 `@Test`=16，+1 合法） | `OK (16 tests)`，Time 349.914，code0=16 | PASS |
| `DeviceMigrationDrillInstrumentedTest` | DATA | 6 | 见 §3：4 直跑 + 2 两阶段；两阶段均实测 | PASS（附条件） |

- 逐套件 `STATUS_CODE: -`（-2/-4）= 0；无 `FAILURES!!!`、无 `Skips`（drill 的两阶段见 §3）。
- 设备侧 APK sha 与宿主逐字节一致（E-40-01 §4）。

## 2. S73 判据逐条

> **口径**：本表“设备 APK 快照”列是 **10:13 建造时**的 93 表/`aa9f0313…` 口径；复核轮已裁定
> rebase 到 71（**92 表**、identityHash 已回填 `1d086050…`），当前口径以 §4 为准。本表保留快照事实，
> 不改写历史读数。

| 判据 | 设备 APK 快照 | 当前工作树（10:51 后） | 证据 |
|---|---|---|---|
| S73-1 常量/产物同源 | **PASS（构建时）**：`73.json` version=73、hash=`aa9f0313…0932`、entities=93、views=2；matrix 运行时 `readDatabaseVersion()==73` | **FAIL**：`StudyDatabase.kt:139` = **71**（KB 32B 在 10:33:32 的文件级还原覆盖，早于 `dee81ea7` 的 10:45:47 docs 提交）；KSP `build/generated/ksp/debug/.../StudyDatabase_Impl.kt:264` = `RoomOpenDelegate(71,"714c0ecb…")`（生成物 mtime 10:35:28） | `73.json` sha `27c5c6a4…c73`；`grep STUDY_DATABASE_VERSION` |
| S73-2 表集合双向零差集 | **PASS**：93 unique entities / 93 allTables / 93 rowCountKeys / 2 views；`BackupRowCountsCoverageInstrumentedTest` 逐名绿 | 同左（plan 仍 73） | `SmbkSelectionPlan.kt` `require(allTables.size == 93)`；rowcounts 设备绿 |
| S73-3 迁移注册 | **PASS（构建时）**：`ERROR_CAUSE_MIGRATION_72_73` 在 `StudyDatabaseFactory.open` 的 `.addMigrations(...)`；matrix 覆盖 1..73；无 `fallbackToDestructiveMigration` | **FAIL**：当前 `StudyDatabase.kt` 对 `ERROR_CAUSE_MIGRATION_72_73` / `ErrorCause*Entity` **零命中**；迁移文件仍在树但未注册 | `grep -n ERROR_CAUSE_MIGRATION_72_73` → 无输出；`grep fallback…` → 无输出 |
| S73-4 备份清单同步 | **PASS**：`SCHEMA_VERSION=73`、`IDENTITY_HASH=aa9f0313…`、`CLOSURE_VERSION=5`、`CLOSURE_DIGEST_PREFIX=smbk-selection-v5`；KDoc 93 表/85 裁剪；`SmbkSelectionPlanTest` 12/12（09:38 JVM 结果，本会话未重跑） | plan 未漂移 | `SmbkSelectionPlan.kt:49,56,59,62`；`Test XML tests=12 failures=0` |
| S73-5 测试资产 | **PASS**：data 测试包 `assets/.../73.json` 396419 B；`SCHEMA_ASSET` 显式 73；无 71/90 字面量；断言 93 与 73.json 实值一致 | 73.json 仍在树：`core/database/schemas/.../73.json`（396419 B，untracked）；`core/data/src/androidTest/assets` 内无副本，APK 资产为构建产物 | `unzip -l`；`ls -la`；`BackupRowCountsCoverageInstrumentedTest.kt:569` |
| S73-6 迁移矩阵/旧行 | **PASS**：matrix `OK (16 tests)`，含 `errorCauseTablesAreCreatedEmptyWhileV72RowsSurvive`（v72 种子行 → 73 后旧行仍在、两新表为空、`active_key` 唯一索引在） | 当前树不可复现（S73-1/3 红） | `suite-matrix.log` 该 test code 0 |
| S73-7 备份计数 | **PASS**：backup 34 / rowcounts 2 / trimmer 9 / c4 8 / drill 6（两阶段） | — | 上表 |
| S73-8 失败归因 | 设备侧无红例；09:38–09:41 单测快照唯一红 `MaterialFeatureScaleTest`（B：KB L2，65021 vs 64839） | **快照已过期**：该 XML 是 DB=73 树的 09:38–09:41 产物，源测试已随 KB 回滚删除；当前树另有 `KernelWave0SchemaContractTest` 静态红（`max(73.json)=73` vs `STUDY_DATABASE_VERSION=71`），不得再用“唯一红”描述当前红集 | 09:38 XML；`KernelWave0SchemaContractTest.kt:36-56`；`StudyDatabase.kt:139` |

## 3. drill 6 例的两阶段执行（不是跳过）

单次 `am instrument` 下 2 例按设计 `assumeTrue` 跳过（`killProcessHandoffStagesArchiveThenDies` 需 `-e smb_d1_kill_stage true`；`restoreFromHandoffAfterProcessDeath` 需前一阶段交接件）：

- 主跑：`suite-drill-optin.log` → `OK (6 tests)`，4 例 code0，2 例 `-4`（AssumptionViolated）。
- 阶段 A：`-e smb_allow_data_reset true -e smb_d1_kill_stage true -e class ...#killProcessHandoffStagesArchiveThenDies` → `INSTRUMENTATION_RESULT: shortMsg=Process crashed.`、`INSTRUMENTATION_CODE: 0`（预期进程死亡；交接件落盘）。
- 阶段 B：`-e smb_allow_data_reset true -e class ...#restoreFromHandoffAfterProcessDeath` → `OK (1 test)`，Time 2.161，code0=1。
- 结论：6 个场景均实际执行；S73-7 的“单次 `OK (6 tests)` 且 Skips=0”在单次调用下不成立，按本两阶段记录判 PASS（附条件），不得把 `-4` 当绿。

## 4. 与当前树的冲突（阻塞项）

- **实际机制（复核更正）**：KB 32B 的文件级回滚在 **10:33:32** 把 `StudyDatabase.kt` 还原为 DB=71（`src-sha-before.txt` `ab50bbd8…` → `src-sha-final.txt` `7210f974…`；KSP 生成物 10:35:28 已是 `RoomOpenDelegate(71,"714c0ecb…")`）。`dee81ea7`（10:45:47）只新增两个 docs 文件（`git show --stat`：2 files / 60 insertions），**没有触碰** `StudyDatabase.kt`；把它写成回滚提交会把机制、时间都记错。上述是 **10:33–10:51 的历史时点**；复核轮 rebase 后当前树为 **73 DB + 92 表清单**，已自洽。
- **复现前置（复核补充 + 裁定）**：仅恢复 WS2 的 73 注册不足。原 `73.json` 的 93 表 = 71 的 90 表 + L2 `knowledge_search_material_feature` + 错因两表；KB 32B 已删除 L2 的 `KnowledgeSearchMaterialFeatureMigration_71_72.kt` / 实体（`restored=27/deleted=5`），71→72 链已断。**主会话 2026-11-02 复核轮裁定：rebase 到 71，不恢复 L2**；73 = 90 + 2 = **92 表**，直连迁移 `MIGRATION_71_73`，`72.json` 作为 orphan 保留。代码侧已落（`STUDY_DATABASE_VERSION=73`、两实体/DAO 注册、`MIGRATION_71_73`）；rebased `73.json` 已由门禁 KSP 生成（2026-11-02 13:35：version 73 / entities 92 / views 2 / FK 110 / identityHash `1d086050204a0da1ed6b6e3299dd9733`），`SmbkSelectionPlan` 已回填同值。
- 本文件所有设备绿只绑定 **10:13 建造的 APK sha**（E-40-01 §4），不绑定当前 HEAD/工作树。
- 复现条件：按 rebase 裁定重跑 E-40-01 的构建→快照→安装门（KSP 已产出 92 表 `73.json` 并回填 `SmbkSelectionPlan`），再重跑 §46/§47；在设备重跑前，S73 仍不能按“当前树通过”关闭（设备绿只绑定 10:13 APK）。

## 5. 同号 73 冲突（复核新发现）

- 本仓曾存在两套互不兼容的 **version=73**：rebase 前本批未跟踪 `73.json` = 93 表 / `identityHash aa9f0313…`（含错因两表）；`codex/ui-green-pages-2026-10-27` 工作树的未跟踪 `73.json` = **97 表 / `identityHash 4d2c8e0f…`**（新增 `paper_review` / `paper_review_photo` / `paper_review_question`，6B-3 改卷面）。rebase 后本批 `73.json` = **92 表 / `1d086050…`**；UI 工作树的 97 表版本仍是冲突源。
- **裁定（主会话 2026-11-02 复核轮）**：本线先占 **73**（rebase 后 92 表）；UI 线（`codex/ui-green-pages` 工作树里未跟踪的 73.json=97 表/`4d2c8e0f…`，含 `paper_review*` 三表）在合并时必须**重编号到 74** 并重跑 KSP 生成。规则：先提交者占用版本号，后到者重编号；本裁定已写入批次 40 记录与决策台账，避免两套 73 共存造成 KD-80 类断裂。UI 的 73.json 不在本树，未触碰。

## 6. 用户可见后果：closureVersion 4→5 不向后兼容（复核补披露）

- 写侧与读侧都把 `closureVersion` 固定为 `SmbkSelectionPlan.CLOSURE_VERSION=5`（`SmbkArchiveCodec.kt:444-446` / `:1162-1166`）。任何 **v3/closure-4** 内容选择包在恢复前会被直接判 `SelectionRead.Invalid`，不是“按旧规则重算”。
- 设计文档的“旧包永远可读”（`docs/research/2026-10-17-6c2-content-selection-design.md:25,427`）只覆盖**没有 selection 的 v1/v2**（`MIN_READABLE_FORMAT_VERSION`），不覆盖本批提升的闭包版本。若 closure-4 的 v3 包曾发布给用户，本版会拒绝恢复；本批证据此前只写“备份 34/34 绿（S=73 快照）”，未披露该后果。
- 处置选项（需产品裁定）：① 接受并写进升级说明/恢复错误文案；② 为 closure-4 增加显式迁移或“按 v5 规则重校验后接受”的兼容读侧（不得静默接受未知闭包）。本轮不擅自实现兼容读侧。
