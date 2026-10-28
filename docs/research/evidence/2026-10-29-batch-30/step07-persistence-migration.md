# E-07 持久化设备面：迁移矩阵 1→70（PASS 15/15）、69→70（FAIL 1/2，测试断言）、提交/CAS（PASS 11/11）、冷恢复（UNVERIFIED）

| 字段 | 值 |
|---|---|
| evidence-id | E-07 |
| convention-version | 1 |
| batch | 2026-10-29-batch-30 |
| step | 七（计划 §1 B：D-01 迁移矩阵 / D-02 身份与 CAS / D-05 冷恢复） |
| gate-or-kd | 计划 `:19-21`；T2（D-01）已落码 `STUDY_DATABASE_VERSION=70` |
| verdict | **部分**：迁移矩阵 **PASS 15/15**；69→70 **FAIL 1/2**（A：测试断言把索引 DDL 当前缀比）；`TutorVisualCommitInstrumentedTest` **PASS 8/8**、`TutorVisualCommitRepositoryInstrumentedTest` **PASS 3/3**（含磁盘满拒提交）；**冷恢复设备用例不存在 → UNVERIFIED** |
| executed-at | 2026-10-29T02:35–02:40+0800（设备 UTC 18:35–18:40） |
| operator | 设备验证轨（subagent，批次 30） |
| head | `a806525e` |
| worktree | dirty（T2/T4 面在飞；本轨零写入） |
| device-serial | `emulator-5562`（API 34 / x86_64 / PAGE_SIZE 4096） |
| apk | `database-debug-androidTest.apk` sha256 `c0766963…38d49b`；`data-debug-androidTest.apk` sha256 `cb88851b…4b1951`（均设备侧一致） |
| apk-version | 0.2.0-offline |
| exit-code | 宿主 shell 均 `EXIT=0`（非判据） |
| raw-log | `build/device-track-batch30/step07-migration-matrix*.{log,txt}`、`step08-migration-69-70*.{log,txt}`、`step09-visual-commit-db*.{log,txt}`、`step10-visual-repo*.{log,txt}` |
| record-ref | 批次 30 记录（后写） |
| sanitized | 是 |

## 1. 命令

```bash
export MSYS_NO_PATHCONV=1
A=C:/Android/Sdk/platform-tools/adb.exe; S=emulator-5562
./gradlew --console=plain :core:database:assembleDebugAndroidTest        # BUILD SUCCESSFUL in 20s；EXIT=0
$A -s $S install -r -t core/database/build/outputs/apk/androidTest/debug/database-debug-androidTest.apk   # Success
$A -s $S logcat -c
$A -s $S shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.database.FullMigrationMatrixInstrumentedTest \
  com.tingyun.smartmistakebook.core.database.test/androidx.test.runner.AndroidJUnitRunner
# 其余三个套件同形，见 §2
```

## 2. 结果

| 套件 | 包 | 结果 |
|---|---|---|
| `FullMigrationMatrixInstrumentedTest`（15） | `com.tingyun.smartmistakebook.core.database.test` | **`OK (15 tests)`**（Time 266.473） |
| `TutorVisualSnapshotMigration_69_70InstrumentedTest`（2） | 同上 | **`Tests run: 2, Failures: 1`** |
| `TutorVisualCommitInstrumentedTest`（8） | 同上 | **`OK (8 tests)`**（Time 1.8） |
| `TutorVisualCommitRepositoryInstrumentedTest`（3） | `com.tingyun.smartmistakebook.core.data.test` | **`OK (3 tests)`**（Time 0.861；含 `diskFullRejectsBeforeAnyVisualRowIsWritten`） |

### 2.1 迁移矩阵 1→70

`FullMigrationMatrixInstrumentedTest.kt:35-39`：

```kotlin
@Test
fun everyExportedSchemaVersionMigratesToCurrentWithoutDestructiveFallback() = runBlocking {
    for (version in 1..STUDY_DATABASE_VERSION) { ... }
```

`STUDY_DATABASE_VERSION = 70`（`StudyDatabase.kt:139`），故循环覆盖 **1..70**；15 例（含 53–68 各版本
行级不丢、v69→v70 表形状）全绿。**迁移矩阵 1→70 成立。**

### 2.2 69→70：`visualTablesAreCreatedEmptyWithoutTouchingV69RowsAndIdempotent`（FAIL）

```text
org.junit.ComparisonFailure: (program_id, state_sha256, draw_commands_sha256) 必须 UNIQUE（同内容只存一份）
  expected:<CREATE UNIQUE INDEX[]> but was:<CREATE UNIQUE INDEX[ `index_tutor_visual_snapshot_program_id_state_sha256_draw_commands_sha256` ON `tutor_visual_snapshot` (`program_id`, `state_sha256`, `draw_commands_sha256`)]>
	at ...TutorVisualSnapshotMigration_69_70InstrumentedTest.inspectV70Shape(TutorVisualSnapshotMigration_69_70InstrumentedTest.kt:206)
	at ...visualTablesAreCreatedEmptyWithoutTouchingV69RowsAndIdempotent(TutorVisualSnapshotMigration_69_70InstrumentedTest.kt:38)
```

- **第一项目帧**：`TutorVisualSnapshotMigration_69_70InstrumentedTest.kt:206`（`inspectV70Shape`）。
- **代码事实**：断言写
  `assertEquals("...UNIQUE...", "CREATE UNIQUE INDEX", indexSql(...)?.substringBefore(" IF NOT EXISTS"))`；
  `indexSql` 读 `sqlite_master.sql`（同文件 `:726-736`）。迁移 DDL 是
  `CREATE UNIQUE INDEX IF NOT EXISTS \`index_tutor_visual_snapshot_program_id_state_sha256_draw_commands_sha256\` ON ...`
  （`VisualSnapshotMigration_69_70.kt:112-115`）；**设备侧 `sqlite_master` 实际返回的 SQL 已不含
  `IF NOT EXISTS`**（见上式 `but was:`），因此 `substringBefore(" IF NOT EXISTS")` 不裁剪，
  拿到整条 DDL，与裸前缀比较失败。
- **实际对象正确**：`but was:` 明示 UNIQUE 索引存在，键为
  `(program_id, state_sha256, draw_commands_sha256)`——正是断言标题要的语义；同一类的另一例与迁移矩阵
  全绿。
- **分类 = A（测试断言/夹具缺陷）**：期望写成裸前缀，未对照 `sqlite_master` 的存储形态。
  处置应为断言索引存在 + 键列 + UNIQUE（或对前缀做 `startsWith`），**不是放宽**、不改产品。
- **归属**：T2（该文件为 `??` 未跟踪新文件，2026-10-29 01:23 落码）。本轨未改。

### 2.3 D-02 身份与 CAS 设备用例（PASS）

- `TutorVisualCommitInstrumentedTest` 8/8 绿（含同 `program_id` 不同非哈希载荷判冲突等）。
- `TutorVisualCommitRepositoryInstrumentedTest` 3/3 绿，其中 `diskFullRejectsBeforeAnyVisualRowIsWritten`
  在设备面覆盖"存储满拒提交"。

### 2.4 D-05 冷恢复（**UNVERIFIED**）

- `VisualSnapshotRestore.kt` 与 `VisualCheckpointRetention.kt` 已在树（`core/data` 主源），
  但 **`core/data/src/androidTest`、`core/database/src/androidTest`、`app/src/androidTest` 中
  `grep -rn "VisualSnapshotRestore"` = 0 命中**（本轨实跑）。JVM 侧 `VisualSnapshotRestoreTest.kt` 有 12 例，
  但本轨不跑 JVM 单测（纪律：只允许 assemble / assembleDebugAndroidTest）。
- 结论：**T4 冷恢复的设备用例未落码 → 设备面 UNVERIFIED**，不得以 JVM 绿替代。

## 3. 未验证项

- **UNVERIFIED**：冷恢复（重启不执行 JS、字体/renderer 匹配、未知版本只读、checkpoint 两份、配额拒提交）
  的设备面——无用例。
- **UNVERIFIED**：`renameTo` / D3 失败面（见 `step04-pdf.md`）。
- 69→70 的测试断言修复与重跑归 T2；本轨只给归因。

## 脱敏与去噪（R4 §4.4）

- 扫描：本批目录四条正则各 **0 命中**（命令与结果见 `00-index.md` §8）；本文件无密钥/凭据/PII。
- 去噪：保留 4 个套件 summary、失败原文与第一项目帧；未删除 `FATAL`/`ANR`/`chatty` 行（本文件无此类行）。
- 二进制/截图：无。全量见头部 `raw-log`（`build/`，不入仓、不作关闭依据）。
