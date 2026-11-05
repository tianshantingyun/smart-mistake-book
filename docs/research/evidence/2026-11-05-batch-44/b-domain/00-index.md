# B 域证据索引（批次 44，2026-11-04 实跑）

> 本文件是导航与口径说明，**不是证据**；台账 B 行只引用同目录下的
> `*.xml` / `*.device.json` / `*.log`。所有设备证据绑定
> `serial + boot_id + apk sha`。

## 1. 身份

| 项 | 值 |
|---|---|
| 源快照 | HEAD `26f2327b0f88d3cad7e076b967b62874e36ca07d` + 工作树脏（共享树，未提交） |
| core:database androidTest APK | sha256 `32235e6323f7caa45bdbdb6a0c566f5f50c9fb4da26cef975163deb3a7501a61` |
| core:data androidTest APK | sha256 `c9943728ec7dfb699345f39af311c0c52e183fec70224a4952eae781000dc488` |
| 迁移设备 | `emulator-5562`，API 34，x86_64，boot_id `a702449b-0f25-4481-800f-ab0a475a37a6` |
| 备份设备 | `emulator-5566`，API 34，x86_64，boot_id `d8f39d06-ec64-4bd7-96f3-7846250d6fb9` |
| 记录时间 | 2026-11-04 19:11–20:46 UTC（本机 19:11–20:46） |

两个 APK 都是“自测 APK”（`pm list instrumentation` 的 target == 测试包本身），
因此 `apk_sha256` 与 `test_apk_sha256` 同值；没有独立 app-under-test APK。

## 2. 迁移 1..72 → 73

### 2.1 动态矩阵

- 命令入口：`python -I tools/ci/am_instrument_recorder.py --serial emulator-5562
  --package com.tingyun.smartmistakebook.core.database.test --class
  com.tingyun.smartmistakebook.core.database.FullMigrationMatrixInstrumentedTest#everyExportedSchemaVersionMigratesToCurrentWithoutDestructiveFallback`
- 用例 `everyExportedSchemaVersionMigratesToCurrentWithoutDestructiveFallback`
  对 `1..73` 每个版本都：用 `core/database/schemas/.../<v>.json` 建库 →
  `StudyDatabaseFactory.open` → 触发 Room 迁移 → 断言 `libraryCatalogCount == 0`。
  任何版本迁移失败或 Room 表结构校验失败都会让该用例失败。
- 结果：`tests=1 failures=0 errors=0 skipped=0`、`EXIT=0`（2026-11-04
  20:46:48 UTC，`emulator-5562`）。73 个源版本的 open/migrate 全部通过。
- 证据：`b-migration-matrix.xml`、`b-migration-matrix.device.json`、
  `b-migration-matrix.log`。
- **矩阵 XML 被 72 行共用**：一个 testcase 覆盖 72 个源版本；这是同一用例的
  72 次循环，不是 72 个独立用例。lint 的 `EVIDENCE_REUSED` 会告警，属预期。

### 2.2 逐版本静态探针

- 每个版本一份 `b-migration-vNN-static.log`，内容：`<v>.json` 是否存在、
  `database.version` 是否等于 v、实体/外键/索引计数、迁移链 v→73 是否完整、
  注册的迁移对数（72）。
- 迁移链事实源：`StudyDatabase.kt` 的 `addMigrations(...)` 与各
  `object : Migration(a, b)` 声明；静态探针要求 1..70 有 `a→a+1`、71 有
  `71→73`、72 有 `72→73`。
- **边界**：逐版本行只证明“该源版本的导出 schema 可被打开并迁移到 73，且迁移链
  注册完整”。**逐版本的数据行保持/外键级联/回滚**没有在 72 个版本上逐一实例化；
  带数据的非破坏性专测见 `FullMigrationMatrixInstrumentedTest` 的
  `reviewPlanFingerprintMergeKeepsExistingPlanAndQueueRows`、
  `m5DropKeepsMemoryRowsAndRemovesConsumptionTable`、
  `m1DropRemovesLegacyFixtureProjectionTablesAndKeepsRealRows`、
  `kf32CreatesBindingChangeEventTableWithoutTouchingExistingRows`、
  `libraryCatalogRebuildReadsRevisionTitleWithoutTouchingStoredRows`
  （这些方法在矩阵类里，但本轮只按需运行了矩阵方法；它们不在 72 行的证据内）。

## 3. 备份（18 行）

### 3.1 设备用例

| label | 类 | tests | fail/err/skip | verdict |
|---|---|---:|---|---|
| `b-backup-restore` | BackupRestoreInstrumentedTest + BackupSelectionRestoreInstrumentedTest + BackupV4PackRestoreInstrumentedTest | 48 | 0/0/0 | PASS |
| `b-backup-drill2` | DeviceMigrationDrillInstrumentedTest（4 个非杀进程用例，`-e smb_allow_data_reset true`） | 4 | 0/0/0 | PASS |
| `b-backup-create` | BackupSelectionCreateInstrumentedTest（同上参数） | 3 | 0/0/0 | PASS |

`b-backup-drill`（第一次，未传 `smb_allow_data_reset`）有 6 个 `-4` 假设跳过，
**不计通过**，只保留为“跳过门”的反例证据；台账不引用它。

### 3.2 静态门与自测

- `b-backup-plan-gate.log`：`python -I tools/ci/check_backup_plan_sync.py --json`
  → `ok=true`、`EXIT=0`。核对：closureVersion/schemaVersion/identityHash/92 表、
  三 mask `[9,13,15]`、v4 冻结规则表与夹具字节/摘要/行数。
- `b-backup-plan-sync.xml` + `.log`：`pytest tools/tests/test_backup_plan_sync.py`
  → 8 passed，`EXIT=0`。
- `b-adversarial-mask-policy.log`：源码不变量探针，`allowedMasks={9,13,15}`，
  `0` 与 `8` 不在允许集，`rejectedMasks` 等于 0..15 的补集，`EXIT=0`。

### 3.3 9 行 mask×state 的实例化边界

| mask | clean | partial | corrupt |
|---|---|---|---|
| 9 | 创建 trim 计数 + 恢复裁会话/派生索引 + 换机演练 | 声明 mask=9 但包内仍带未选会话 → 包内库实测拒绝 | 非法选择块/非法 mask（0/8/1/3）在创建或交换前拒绝 |
| 13 | 恢复保留会话/派生索引恒裁 + 换机演练 | **UNVERIFIED**：无 mask=13 的 partial 夹具 | **UNVERIFIED**：无 mask=13 的 corrupt 夹具 |
| 15 | 全选仍裁派生索引 + 每表换机演练 | 设置多键顺序写失败 → partial 回执、不回滚 | 全选包 rowCounts 与包内库不符 → 交换前拒绝 |

mask=13 的 partial/corrupt 两格没有专门实例化。共享完整性门（B-075/B-081）用
mask=9/15 包装载，不能替代“mask=13 包装载”的判词，因此记 `UNVERIFIED`，不记
PASS。

### 3.4 损坏类覆盖（至少 9 类）

| 类 | 用例 |
|---|---|
| 截断归档 | `truncatedArchiveIsRejectedByValidateAndRestore` |
| 摘要不符 | `checksumMismatchIsRejected` |
| zip 炸弹（压缩比） | `zipBombCompressionRatioIsRejected` |
| 条目数超限 | `tooManyEntriesAreRejected` |
| 单条目超限 | `singleEntryOversizeIsRejected` |
| 数据库条目预算 | `databaseEntryAboveItsOwnBudgetIsRejectedByBothPaths` / `databaseEntryUsesItsOwnBudgetOnBothValidateAndUnpack` |
| 总解压超限 | `totalDecompressionOversizeIsRejected` |
| 交换前库校验失败 | `preSwapValidationFailureRejectsCorruptDatabaseAndKeepsLiveData` |
| 引用资产缺失 | `restoreIsRejectedWhenTheArchivedDatabaseReferencesAMissingAsset` |
| 生成产物引用缺失 | `restoreIsRejectedWhenAGenerationAttemptOutputAssetIsMissing` |
| 重复恢复请求（并发/CAS） | `duplicateConcurrentRestoreRequestsAreRejectedIdempotently` |
| 交换中断启动恢复 | `startupRecoveryRollsBackGenerationAfterInterruptedSwap` / `startupRecoveryCleansPreSwapInterruptionWithoutTouchingLiveData` |

未直接实例化、因此不写进 PASS 行：ZIP “bad magic”、SQLite “bad magic”、
ZIP 重复条目（生产侧 `SmbkArchiveCodec.kt:797` 有拒绝，但没有夹具）、
“missing manifest”（JVM codec 测试有，本轮未跑 Gradle）。

## 4. v4 / v5 跨版本

- **v4**（closure 4 / schema 72）：`BackupV4PackRestoreInstrumentedTest` 三个用例
  全绿；冻结夹具字节先做 sha256 断言，再恢复、72→73 迁移、逐表行数对拍。
- **v5**（closure 5 规则表）：跨版本边由 `BackupRestoreInstrumentedTest` 的
  `oldSchemaArchiveMigratesOnRestore`（v30 包 → v73）与
  `schema68ArchiveRestoresAndMigratesToCurrentVersion`（v68 包 → v73）覆盖；
  同版本边由三 mask 恢复、legacy v2 全量语义、派生索引恒裁覆盖。v5 没有更高
  schema 可“跨”，所以“跨版本”指旧 schema 包升到当前 v73。

## 4.1 本轮 B 域判词与门禁

- 台账：**88 PASS / 2 UNVERIFIED / 0 FAIL / 0 BLOCKED = 90**。
  UNVERIFIED 为 B-077（mask=13 partial）与 B-078（mask=13 corrupt）。
- `python -I tools/ci/acceptance_init.py --check` → `EXIT=0`（1200 行无漂移）。
- `python -I tools/ci/acceptance_report.py --ledger docs/acceptance/ledger.md`
  → `EXIT=0`；**B 域 violations = 0**。
- `python -I tools/ci/acceptance_lint.py --ledger docs/acceptance/ledger.md`
  → `EXIT=0`；**B 域 violations = 0**。5 条 `EVIDENCE_REUSED` 警告全部来自
  B 域共用矩阵/恢复证据（预期，见 §2.1）。
- `python -I tools/ci/trace_requirements.py --ledger docs/acceptance/ledger.md`
  → 全局 `EXIT=1`（他域占位）；**B 域 gaps = 0**。
- 引用完整性自检：88 个 PASS 行共 110 个 `Class#method` 引用，全部能在
  对应 XML 中定位到 `testcase`，且无 `failure/error/skipped`。

## 4.2 对抗尝试

- **跳过门不得计通过**：`b-backup-drill`（未传 `smb_allow_data_reset`）产生
  6 个 `-4` 假设跳过，recorder 判 `EXIT=1`；这些文件保留，但不进任何 PASS 行。
- **非法 mask**：`nonCreatableSelectionsAreRejectedBeforeAnySnapshotOrArchiveByte`
  对 mask=0/8/1/3 断言创建前拒绝且零归档字节；`b-adversarial-mask-policy.log`
  独立复核 `allowedMasks={9,13,15}`。
- **篡改包**：digest/rowCounts/closureVersion/schemaVersion/mask=8/assetPolicy
  六类篡改均要求校验期与交换前拒绝，且原库可读、无 `.prev`、无待重建标记。
- **第三方拼接包**：塞入未选 `TUTOR_RESPOND` 任务行/无引用 figure operation，
  要求软引用重查在交换前拒绝。
- **重复恢复**：并发重复请求必须幂等拒绝。

## 5. 未验证与残余（不得写成通过）

- 迁移 72 行的“数据保持/回滚”未逐版本实例化（见 §2.2）。
- mask=13 的 partial/corrupt 两格（B-077/B-078）。
- ZIP/SQLite bad magic、ZIP 重复条目、missing manifest 的备份侧夹具。
- 真实杀进程交接（`killProcessHandoffStagesArchiveThenDies` 需要两阶段
  `am instrument` 且第二阶段依赖交接件；本轮未跑，按 runbook 留为手工步骤）。
- 设备证据绑定的是“自测 APK”；没有独立 app-under-test APK 可绑。

## 6. 复跑命令

```bash
# 迁移矩阵（约 50–60 分钟；API 34 x86_64）
python -I tools/ci/am_instrument_recorder.py --adb /c/Android/Sdk/platform-tools/adb.exe \
  --serial emulator-5562 --package com.tingyun.smartmistakebook.core.database.test \
  --class com.tingyun.smartmistakebook.core.database.FullMigrationMatrixInstrumentedTest#everyExportedSchemaVersionMigratesToCurrentWithoutDestructiveFallback \
  --apk-sha 32235e6323f7caa45bdbdb6a0c566f5f50c9fb4da26cef975163deb3a7501a61 \
  --test-apk-sha 32235e6323f7caa45bdbdb6a0c566f5f50c9fb4da26cef975163deb3a7501a61 \
  --label b-migration-matrix --out-dir docs/research/evidence/2026-11-05-batch-44/b-domain

# 备份恢复三件套
python -I tools/ci/am_instrument_recorder.py --adb /c/Android/Sdk/platform-tools/adb.exe \
  --serial emulator-5566 --package com.tingyun.smartmistakebook.core.data.test \
  --class "com.tingyun.smartmistakebook.core.data.backup.BackupRestoreInstrumentedTest,com.tingyun.smartmistakebook.core.data.backup.BackupSelectionRestoreInstrumentedTest,com.tingyun.smartmistakebook.core.data.backup.BackupV4PackRestoreInstrumentedTest" \
  --apk-sha c9943728ec7dfb699345f39af311c0c52e183fec70224a4952eae781000dc488 \
  --test-apk-sha c9943728ec7dfb699345f39af311c0c52e183fec70224a4952eae781000dc488 \
  --label b-backup-restore --out-dir docs/research/evidence/2026-11-05-batch-44/b-domain

# 静态门
python -I tools/ci/check_backup_plan_sync.py --json
python -I -m pytest tools/tests/test_backup_plan_sync.py -q \
  --junitxml=docs/research/evidence/2026-11-05-batch-44/b-domain/b-backup-plan-sync.xml
```
