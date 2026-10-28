# E-06 备份 34 例：`BackupRestoreInstrumentedTest`（**FAIL 8/34**：schema 70 与备份裁剪器 69 未同步）

| 字段 | 值 |
|---|---|
| evidence-id | E-06 |
| convention-version | 1 |
| batch | 2026-10-29-batch-30 |
| step | 六（备份 34 例不回退；计划 §3 验收） |
| gate-or-kd | 计划 `.zcode/plans/plan-2026-10-29-batch-30-device-persist-fixes.md:45`「备份 34 例不回退」；D-01「备份资产枚举同步」（`:19`） |
| verdict | **FAIL**：`Tests run: 34, Failures: 8`。7 红同一根因 = `BackupSnapshotTrimException: 快照 schema=70，裁剪器只支持 schema=69`（C 类：D-01 schema 70 落码未同步备份计划/裁剪器）；1 红 = 版本断言仍写 69（B 类：测试期望漂移） |
| executed-at | 2026-10-29T02:33:30+0800 前后（设备 UTC 18:33） |
| operator | 设备验证轨（subagent，批次 30） |
| head | `a806525e` |
| worktree | dirty（D-01/D-03 面在飞：`StudyDatabase.kt` version=70、`SmbkSelectionPlan.kt` 仍 69、`BackupSnapshotTrimmer.kt` M；本轨零写入） |
| device-serial | `emulator-5562`（API 34 / x86_64 / PAGE_SIZE 4096） |
| apk | `data-debug-androidTest.apk` sha256 `cb88851b…4b1951`（设备侧一致） |
| apk-version | 0.2.0-offline |
| exit-code | 宿主 shell `EXIT=0`（非判据） |
| raw-log | `build/device-track-batch30/step06-backup.log` / `step06-backup-logcat.txt`（381 行） |
| record-ref | 批次 30 记录（后写） |
| sanitized | 是 |

## 1. 命令

```bash
export MSYS_NO_PATHCONV=1
A=C:/Android/Sdk/platform-tools/adb.exe; S=emulator-5562
$A -s $S logcat -c
$A -s $S shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.data.backup.BackupRestoreInstrumentedTest \
  com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner \
  > build/device-track-batch30/step06-backup.log 2>&1; echo "EXIT=$?"
$A -s $S logcat -v threadtime -d > build/device-track-batch30/step06-backup-logcat.txt
```

## 2. 结果（原样）

```text
1) fullRestoreLeavesDerivedSearchIndexEmptyAndMarksItForRebuild       BackupSnapshotTrimException: 快照 schema=70，裁剪器只支持 schema=69
2) backupClosureIncludesGenerationAttemptOutputs                     （同）
3) settingsSurviveBackupAndRestoreRoundTrip                          （同）
4) duplicateConcurrentRestoreRequestsAreRejectedIdempotently          （同）
5) schema68ArchiveRestoresAndMigratesTo69    java.lang.AssertionError: 68 旧包恢复后必须由 Room 迁移到当前版本 expected:<69> but was:<70>
6) backupCreatedWhileACaptureIsRegisteredKeepsTheArchiveReferenceClosed  BackupSnapshotTrimException: 快照 schema=70，裁剪器只支持 schema=69
7) backupDeleteAndRestoreRoundTripPreservesCatalog                    （同）
8) settingsWriteFailureIsReportedAsPartialWithoutRollingBackTheRestoredDatabase （同）

FAILURES!!!
Tests run: 34,  Failures: 8
```

## 3. 归因（判据 §7.2/§7.3/§7.5）

### 3.1 七红：schema 70 与备份裁剪器 69 未同步（**C，产品/集成缺口**）

- **第一项目帧**：`BackupSnapshotTrimmer.validateSchemaIdentity`（`BackupSnapshotTrimmer.kt:267-271`），
  由 `BackupRestoreInstrumentedTest` 的备份/恢复路径触发。
- **代码事实**：
  - DB 当前版本 = **70**：`core/database/src/main/kotlin/.../StudyDatabase.kt:139`
    `internal const val STUDY_DATABASE_VERSION = 70`（D-01 本批落码，`schema 69→70`）。
  - 裁剪器只接受 **69**：`core/data/src/main/kotlin/.../backup/SmbkSelectionPlan.kt:39`
    `const val SCHEMA_VERSION: Int = 69`（注释仍写 `69.json`）；
    `BackupSnapshotTrimmer.kt:267` 要求 `database.version == SmbkSelectionPlan.SCHEMA_VERSION`，
    不等即抛 `快照 schema=${...}，裁剪器只支持 schema=${...}`（`:269-271`）。
  - 该文件自己的 KDoc 已登记该缺口："在那之前真子集遇到 v70 库会在身份校验处 fail-closed
    （未知表拒绝），不会静默丢预览"（`BackupSnapshotTrimmer.kt:1243-1247`）。
- **权威源**：计划 §1 任务 3 明确 D-01 必须"**备份资产枚举同步**"
  （`.zcode/plans/plan-2026-10-29-batch-30-device-persist-fixes.md:19`）；
  计划 §3 验收要求"备份 34 例不回退"（`:45`）。基准行为是批次 29 的 `OK (34 tests)`
  （`docs/research/evidence/2026-10-28-batch-29/step05-backup.md`）。
- **分类 = C（产品/集成缺口）**：schema 70 已进 DB 主源，但备份计划/裁剪器的版本与表集合未同步，
  导致 schema-70 库的备份/恢复全路径 fail-closed。**非 A/B/D**：不是夹具问题（同一 fixture 在批次 29
  绿）、不是期望漂移（期望"当前版本库可备份"正确）、不是环境/旧包（同设备、新 APK、sha256 已核）。
- **归属/处置**：D-01（T2）拥有者；需把 `SmbkSelectionPlan` 对齐 v70（`schemaVersion`/`identityHash`/
  86→90 表集合与 `tutor_visual_*` 资产臂），并重跑 34 例。**本轨未改任何文件**（该面为他线在飞、
  计划 §2 单一写者纪律）。

### 3.2 一红：`schema68ArchiveRestoresAndMigratesTo69` 期望 69（**B，测试期望漂移**）

- **第一项目帧**：`BackupRestoreInstrumentedTest.kt:588-591`（`assertEquals(69, restored.readDatabaseVersion())`）。
- **权威源**：`STUDY_DATABASE_VERSION = 70`（`StudyDatabase.kt:139`）；测试名与注释仍写"迁移到 69"。
- **分类 = B**：实际值 70 是权威值；期望 69 过期。正确处置是把期望改为**权威常量**
  （`STUDY_DATABASE_VERSION`），不是"改成实测值"（二者恰好同为 70），delta 0，不属放宽。
- **归属/处置**：同一 D-01 拥有者（`BackupSnapshotTrimmer` 与备份测试同一落点）；本轨不代改。

## 4. 对验收的影响

- 计划 §3「备份 34 例不回退」**不成立**（26/34 绿）。这是**他线在飞期间的可预期中间态**，
  但在 T2 完成"备份资产枚举同步"并复跑前，本批不得宣称备份面回归通过。
- 本红例**不**由设备/旧包/夹具引起，重建重装不会变绿（已排除 D）。

## 5. 未验证项

- **UNVERIFIED**：T2 同步后的备份 34 例绿轮。
- **UNVERIFIED**：`BackupSelectionRestoreInstrumentedTest`（10）/`BackupSnapshotTrimmerInstrumentedTest`（9）
  等其它备份面套件本批未跑（不属于"34 例"口径），其 v70 行为未知。

## 脱敏与去噪（R4 §4.4）

- 扫描：本批目录四条正则各 **0 命中**（命令与结果见 `00-index.md` §8）；本文件无密钥/凭据/PII。
- 去噪：保留 8 条失败行原文与第一项目帧；未删除 `FATAL`/`ANR`/`chatty` 行（本文件无此类行）。
- 二进制/截图：无。全量见头部 `raw-log`（`build/`，不入仓、不作关闭依据）。
