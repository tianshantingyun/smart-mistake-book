# 批次 31 T1 设备判据：八套件计数 / KD-80 关闭门 / 串行命令与 dex 门 / 红例归因

> **只读核验**：本轮未运行 Gradle、未编译、未安装或执行任何仪器化/设备用例、未做 git 写操作。
> 只读命令：`grep -c "@Test"`、`Read`/`sed` 源码、`git status --porcelain`、`git rev-parse HEAD`、
> `git ls-files`、`unzip -l`、`python` 比对脚本。设备执行归设备轨（计划
> `.zcode/plans/plan-2026-10-29-batch-31-persistence-hardening.md:14-16`、`:39-40`）。
>
> **判据输入**：计划 §1 A/B（`:14-22`）、批次 31 基线（`docs/research/2026-10-29-batch-31-baseline.md:11-28`、
> `:103-124`、`:237-315`）、KD-80 台账（`docs/known-defects.md:2955-2963`）、批次 30 设备判据
> （`docs/research/2026-10-29-batch-30-device-criteria.md`）、P07 标定研究
> （`docs/research/2026-10-29-parity-calibration.md:13-31`、`:73-76`）与现树源码/测试实读。
>
> **快照时点**：2026-10-29 05:30 +0800。HEAD `59d4a03f92ab27314d25384a777f41829b3421a2`；
> DB schema **70**（`StudyDatabase.kt:139`），`core/database/schemas/.../` 最高 **70.json**，无 71.json。
> **工作树正在被 T3/T4 改写**（69→70 套件、parity 测试/阈值均为 ` M`），本文的套件计数是**该时点复读值**；
> 设备轨必须以"构建前最后一次复读"的计数为准（§1.3），不得把本文件的数字当成冻结常量。
>
> **2026-10-29 10:10 现树漂移（批次 32 T2，本轨复读）**：parity 套件 **16 例**（+`n13CentroidBlindSpotIsCoveredByBboxOrShift`）、
> 阈值版本名改为 **`calibrated-r2`（数值一个不动）**、N09 改为**跨栅格模型断言**（P06 `1.2625` / P07 `0.608282`，
> 容差 1e-6/0.02）；`TutorVisualSnapshotMigration_69_70InstrumentedTest` 的行锚随落码移动（`onlyLive…` 现 `:102`）。
> 下表与 §2 的行锚已按 10:00 复读；并行轨仍在改，**使用前必须重跑 §1.2 的 `grep -c`/`grep -n`**。

---

## 0. 结论速览

| 套件（文件） | 计划/基线口径 | 本快照复读 | 设备目标（冻结规则见 §1.3） |
|---|---|---|---|
| 备份 `BackupRestoreInstrumentedTest`（`core/data`） | 34 | **34**（` M`） | `OK (34 tests)` |
| 69→70 `TutorVisualSnapshotMigration_69_70InstrumentedTest`（`core/database`） | 2（批次 30） | **4**（` M`；本批新增 2 个元用例，归属以批次记录为准——早期注记写 T3，复核轨称 T2，二者均无提交作者可验） | `OK (4 tests)`（以最终落码为准） |
| 迁移矩阵 `FullMigrationMatrixInstrumentedTest`（`core/database`） | 15；循环 1..70 | **15**；循环 1..70 | `OK (15 tests)`；若 schema 71 则覆盖 1..71 |
| painter `VisualDrawOpsPainterInstrumentedTest`（`core/data`） | 24 | **24** | `OK (24 tests)` |
| 隔离 `VisualJsIsolationInstrumentedTest`（`app`） | 13 | **13** | `OK (13 tests)` |
| flags `VisualMeasureReplayPaintFlagsInstrumentedTest`（`app`） | 2 | **2** | `OK (2 tests)` |
| PDF `VisualDrawOpsPdfPainterInstrumentedTest`（`core/data`） | 16 | **16** | `OK (16 tests)` |
| 一致性门 `VisualDrawOpsParityInstrumentedTest`（`core/data`） | 11（批次 30） | **15**（` M`，T4 +N09–N12；10:10 现树 **16**，批次 32 T2 +N13） | 批次 31 为 `OK (15 tests)`；现树 16 例以构建前复读为准 |

**结论**：

1. **计划/工单里的"一致门 11"和"69→70 套件 2"已被在飞落码超越**：本快照分别是 **15** 与 **4**
   （10:10 现树 parity 已 **16**，+N13）。设备轨若一致性门跑出 `OK (11 tests)`、或 69→70 套件跑出
   `OK (2 tests)`，判 **D（旧包）**，不是"少几例也算过"（flags 的 `OK (2 tests)` 是正常目标，不在此列）。
2. **KD-80 的修复代码在树但未提交**（`BackupRestoreInstrumentedTest.kt` ` M`，5 增 5 删；
   `SmbkSelectionPlan` 已是 70/90/21/CLOSURE 3）。**设备绿轮已跑：`OK (34 tests)`**（E-01，
   2026-10-29 06:31），批次 30 的 8 条红例逐条转绿；KD-80 仍 **open**，但原因已不是"34/34 未跑"，
   而是 80-C3 的 JVM 半边 UNVERIFIED 与 80-C4 零断言（E-12）。
3. **KD-80 台账的第四项（备份/恢复后视觉四表引用关系保持）当前 34 例并不覆盖**：
   `grep -rln "tutor_visual" core/data/src/androidTest/.../backup/` = 0 命中（§3.4）。34/34 + 表清单
   两项不足以按台账原文关闭；需补断言或如实记 UNVERIFIED。
4. **P07 阈值已改为 `calibrated-r1`（批次 31）；现树改名为 `calibrated-r2`（批次 32 T2，数值一个不动）**：
   质心 0.5→0.75、墨量比 0.95–1.05→0.93–1.07，依据是**同设备同夹具的 AA 关读数 0.518…**，不是"为了变绿"。
   设备绿轮必须同时证明 N01–N08、N09–N13 仍全红（§2.8、§5.5；现树 16 例）。**设备实跑结果（E-08/E-08b，当时 r1 / 15 例）**：
   `Tests run: 15, Failures: 1`——N01–N08、N10–N12 全红；唯一红是 N09-P06 的旧等式
   `centroid=1.000`（实测 1.2625，门级已 `red=true`、理由 `bboxDev=2px > 1px`），归因
   **A（负控期望值算错）**，N09-P07 中止未执行；`calibrated-r1` 绿轮仍 **UNVERIFIED**。
   **`1.000` 是旧源期望**：批次 31 设备轨后先撤成"只断言必须红 + `F4_GEOMETRY`"，批次 32 T2 现树
   又改为**跨栅格模型断言**（P06 `1.2625` / P07 `0.608282`，容差 1e-6 / 0.02，
   `VisualDrawOpsParityInstrumentedTest.kt:309-365/:902-905`），并新增 N13。原期望错在把
   `centroidDeltaPx` 当同侧平移量，实际是屏↔PDF 跨栅格欧氏距离（`VisualDrawOpsParityMetrics.kt:830-831`
   的 `hypot`），P06 +1 px 读数 = |v+(1,0)| = 1.2625，不是 1.000。

---

## 1. 快照事实与计数冻结

### 1.1 现树事实（本轮实跑）

| 项 | 实测 | 证据 |
|---|---|---|
| HEAD | `59d4a03f92ab27314d25384a777f41829b3421a2` | `git rev-parse HEAD` |
| DB schema | `STUDY_DATABASE_VERSION = 70` | `StudyDatabase.kt:139` |
| 导出 schema | 目录 70 个 json，最高 `70.json`；`git ls-files core/database/schemas \| wc -l` = 70 | 实跑 |
| 70.json | `database.version=70`、`identityHash=11a8a701bae767a8a3c5bc810e4743a4`、90 实体、2 视图 | §3.2 比对脚本实读 |
| 迁移注册 | `VISUAL_SNAPSHOT_MIGRATION_69_70`（`StudyDatabase.kt:409`）；`object : Migration(69, 70)`（`VisualSnapshotMigration_69_70.kt:36`） | 源码 |
| 备份常量 | `SCHEMA_VERSION = 70`（`:40`）、`IDENTITY_HASH = 11a8a701…`（`:43`）、`CLOSURE_VERSION = 3`（`:46`）、`allTables.size == 90`（`:538`）、`softReferences.size == 21`（`:554`） | `SmbkSelectionPlan.kt` 实读 |
| 71.json | **不存在**；`S=71` 比对脚本输出 `MISSING …/71.json`，exit=2 | 实跑 |

### 1.2 套件计数（`grep -c '@Test'`，本轮实跑）

```bash
grep -c '@Test' core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/backup/BackupRestoreInstrumentedTest.kt          # 34  (M)
grep -c '@Test' core/database/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/database/TutorVisualSnapshotMigration_69_70InstrumentedTest.kt # 4 (M；批次 30 为 2)
grep -c '@Test' core/database/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/database/FullMigrationMatrixInstrumentedTest.kt   # 15
grep -c '@Test' core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualDrawOpsPainterInstrumentedTest.kt   # 24
grep -c '@Test' app/src/androidTest/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsIsolationInstrumentedTest.kt                  # 13
grep -c '@Test' app/src/androidTest/kotlin/com/tingyun/smartmistakebook/visualjs/VisualMeasureReplayPaintFlagsInstrumentedTest.kt      # 2
grep -c '@Test' core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualDrawOpsPdfPainterInstrumentedTest.kt # 16
grep -c '@Test' core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualDrawOpsParityInstrumentedTest.kt  # 15 (M；批次 30 为 11；10:10 现树 16)
```

**计数来源的差异（必须登记）**：

- 69→70 套件：` M`（在飞）；新增
  `indexExpectationsStayInSyncWithTheMigrationDdlConstants`（批次 31 `:228`；10:10 现树 `:238`）与
  `indexShapeAssertionsCanFailOnWrongColumnOrderAndNonUniqueIndex`（批次 31 `:265`；10:10 现树 `:275`），用于证明 DDL 断言
  修复不是"把断言改松"，而是"期望与迁移常量同源 + 反例能红"。
- parity 套件：` M`（在飞）；新增 `n09OnePixelTextShiftIsCaught`（10:10 现树 `:309`）、
  `n13CentroidBlindSpotIsCoveredByBboxOrShift`（`:368`，批次 32 T2）、`n10WholePageMisdrawIsCaught`（`:450`）、
  `n11ShapeWrongButPositionAndMassRightIsCaught`（`:466`）、`n12WholeBlockShiftBeyondToleranceIsCaught`（`:500`）。
  这是 `calibrated-r1/r2` 的"放宽许可证"；现树阈值版本名 `calibrated-r2`（数值不动）。
  **行锚随落码漂移，使用前重跑 `grep -n "fun n"`**（旧锚 `:294/:323/:339/:373`、批次 31 的 `:298/:338/:354/:388` 均已作废）。

### 1.3 计数冻结规则（设备轨强制）

1. 构建前对八套件再跑一次 §1.2 的 `grep -c '@Test'`，把结果写进证据头（`counts-before-build`）。
2. `am instrument` 的 summary 目标 = **最后一次复读的 N**，例如 `OK (N tests)`；不得预先写死
   11/2 等旧值。
3. `N_run < N_frozen` → **D（旧包/装错包）**：停，查 sha256 与 dex 门，重装重跑，不得把少例读成通过。
4. `N_run > N_frozen` → 说明构建后仍有落码：停，重新复读并重建；不得"多跑的几例不管"。
5. 若 T3/T4 在设备轨执行期间继续改测试文件，`git status --porcelain` 与源文件 sha256 快照
   （构建前/后）必须一致；不一致 = 构建早于源改动，判 D。

---

## 2. 八套件套件级硬判据

所有套件共用：

1. summary 必须是目标字符串 `OK (N tests)`，**0 failures / 0 errors / 0 skipped**；
2. `INSTRUMENTATION_CODE: -1` **不是判据**（AndroidJUnitRunner 恒返回 RESULT_OK）；
3. 宿主 shell 退出码**不是判据**（断言失败时 `am instrument` 仍可能 exit=0）；
4. 每套件独立 `logcat -c` → 执行 → `logcat -d` 落 raw log；
5. 9 类崩溃签名全 0（清单见 §5.1）；
6. **禁用 `connectedAndroidTest`**（计划 `:39`）；只允许 `assemble*` + `install -r -t` + `am instrument`。

### 2.1 备份 34 例

文件：`core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/backup/BackupRestoreInstrumentedTest.kt`。
类：`com.tingyun.smartmistakebook.core.data.backup.BackupRestoreInstrumentedTest`。

**目标**：`OK (34 tests)`（最终复读为准）。

**批次 30 的 8 条红例必须在本轮全部转绿**（`step06-backup.md:39-46`）：

| # | 用例 | 本轮必须成立 |
|---|---|---|
| 1 | `fullRestoreLeavesDerivedSearchIndexEmptyAndMarksItForRebuild` | 恢复后派生索引为空、重建标记在 |
| 2 | `backupClosureIncludesGenerationAttemptOutputs` | 生成 attempt 输出被备份闭包收进 |
| 3 | `settingsSurviveBackupAndRestoreRoundTrip` | 设置往返一致（生产 DataStore 路径） |
| 4 | `duplicateConcurrentRestoreRequestsAreRejectedIdempotently` | 并发重复恢复幂等拒绝 |
| 5 | `schema68ArchiveRestoresAndMigratesToCurrentVersion`（`:488`） | 68 旧包恢复后由 Room 迁到**当前版本**；期望读 `SmbkSelectionPlan.SCHEMA_VERSION`（`:590`），不是字面量 69 |
| 6 | `backupCreatedWhileACaptureIsRegisteredKeepsTheArchiveReferenceClosed` | 采集在飞时备份仍闭合 |
| 7 | `backupDeleteAndRestoreRoundTripPreservesCatalog` | 备份/删除/恢复后目录保留 |
| 8 | `settingsWriteFailureIsReportedAsPartialWithoutRollingBackTheRestoredDatabase` | 设置写失败报部分成功、不回滚恢复库 |

**套件级附加判据**：

```bash
# 两处都不许出现（批次 30 的 7 红根因与 1 红期望漂移）
grep -c "BackupSnapshotTrimException" build/device-track-batch31/step01-backup.log        # 0
grep -c "expected:<69>"               build/device-track-batch31/step01-backup.log        # 0
grep -c "裁剪器只支持 schema="         build/device-track-batch31/step01-backup-logcat.txt # 0
```

- 若仍出现 `快照 schema=70，裁剪器只支持 schema=69`：先查 `SmbkSelectionPlan.SCHEMA_VERSION`
  （`:40`）与 `BackupSnapshotTrimmer` 的版本门（`:268-271`）；新包仍 69 → **C（产品/集成缺口）**；
  旧包 → **D**。
- 若出现 `expected:<69> but was:<70>`：说明装的是批次 30 修复前测试包，判 **D**；修复后测试读常量，
  不会打印 69。

### 2.2 69→70 套件（现树 4 例）

文件：`core/database/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/database/TutorVisualSnapshotMigration_69_70InstrumentedTest.kt`。
类：`com.tingyun.smartmistakebook.core.database.TutorVisualSnapshotMigration_69_70InstrumentedTest`。

**目标**：`OK (4 tests)`（批次 30 为 2；T3 已加 2 条元用例，最终复读为准）。

| # | 用例（行，批次 31 时点） | 本轮必须成立（批次 31 语义） |
|---|---|---|
| 1 | `visualTablesAreCreatedEmptyWithoutTouchingV69RowsAndIdempotent`（`:58`） | 从 69 库打开后迁到 `STUDY_DATABASE_VERSION`；**执行顺序**是数据保全（`:76` → `inspectV70DataPreservation` `:320`）→ 外键负向（`:77` → `assertV70ForeignKeyBehaviour` `:446`）→ 形状（`:78` → `inspectV70Shape` `:338`）；四表建出且为空；旧消息/题面/资产行一行不丢；再次打开幂等 |
| 2 | `onlyLiveSnapshotsCanBeLinkedAndCasUsesChangedRowCounts`（`:102`，10:10 现树） | STAGING 快照写不进消息引用；`STAGING→LIVE` 以命中行数判赢；过期 revision 的 CAS 命中 0 行 |
| 3 | `indexExpectationsStayInSyncWithTheMigrationDdlConstants`（`:228`；10:10 现树 `:238`） | 迁移恰好 11 条索引 DDL；索引名集合、键列序、UNIQUE 关键字与测试期望逐位一致 |
| 4 | `indexShapeAssertionsCanFailOnWrongColumnOrderAndNonUniqueIndex`（`:265`；10:10 现树 `:275`） | 负控：错列序 / 非唯一 / 不存在索引 / 多一条少一条都必须判不等；证明形状断言仍有分辨力 |

> **10:10 现树漂移（批次 32，必须读）**：现树第 1 例只调用 `inspectV70DataPreservation`（`:80`）
> 与 `assertV70ForeignKeyBehaviour`（`:81`），**`inspectV70Shape` 已不在文件中**；行锚现为
> `inspectV70DataPreservation :330`、`assertV70ForeignKeyBehaviour :462`。上表与下段 DDL 语义
> **只对批次 31 快照成立**，现树用例结构已由批次 32 轨改写——不得把旧顺序/旧 `:338-369` 断言
> 当现树判据；重跑前请按符号复读源码。

**DDL 断言的语义（不得放宽；批次 31 时点）**：本批已把形状断言从 `sqlite_master.sql` 文本比较改为
**PRAGMA 结构读数**——表列序读 `PRAGMA table_info`，索引名集合读 `PRAGMA index_list`，键列序读
`PRAGMA index_info`，唯一性读 `index_list.unique`（批次 31 时点 `inspectV70Shape` `:338-369`；10:10 现树该函数已不在）。这样既避开
设备侧 `sqlite_master` 去掉 `IF NOT EXISTS` 的文本差异（批次 30 的 A 类红因），又没有放宽
"存在 + 唯一 + 三列"的语义。第 3 例把迁移 DDL 常量（含 `CREATE UNIQUE INDEX IF NOT EXISTS …`，
`:246`）与测试期望对齐；第 4 例用临时库证明读取器能抓住错列序/非唯一。**两例都必须绿**；
删掉负控或把 `assertFalse` 改成恒真 = 放宽，不得据此关闭。

**schema 71 分支**：若 T3 把 `STUDY_DATABASE_VERSION` 升到 71，本套件必须把 v69 库迁到**当前 71**
（即注册 70→71 迁移）；只改 `assertEquals` 的期望值而没注册迁移 = **C（产品缺口）**，不得用改名
或跳过隐藏。若新增 70→71 专属套件，必须同时纳入 dex 门与本节计数。

### 2.3 迁移矩阵 1→当前版本

文件：`core/database/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/database/FullMigrationMatrixInstrumentedTest.kt`。
类：`com.tingyun.smartmistakebook.core.database.FullMigrationMatrixInstrumentedTest`。

**目标**：`OK (15 tests)`。第 1 例是矩阵循环：

```kotlin
for (version in 1..STUDY_DATABASE_VERSION) { ... }   // FullMigrationMatrixInstrumentedTest.kt:38
```

- 当前 `STUDY_DATABASE_VERSION = 70` ⇒ 覆盖 **1..70**（70 个版本点）。
- 若 schema 升到 **71** ⇒ 同一循环自动覆盖 **1..71**；套件数仍是 15（1 个矩阵 + 14 个专项；
  若 T3 另加 70→71 专项，按 §1.3 复读计数）。
- **证明覆盖范围的三条硬门**（缺一不可）：

```bash
# 1) 版本常量
V=$(sed -n 's/.*STUDY_DATABASE_VERSION = \([0-9][0-9]*\).*/\1/p' \
  core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabase.kt | head -1)
echo "V=$V"                                      # 期望 70；若升版则 71

# 2) 测试 APK 里必须真的带 1..V 的导出 schema（矩阵按 assets 建库）
unzip -l core/database/build/outputs/apk/androidTest/debug/database-debug-androidTest.apk \
  | grep -E 'StudyDatabase/[0-9]+\.json$' | wc -l   # 必须 == V
unzip -l core/database/build/outputs/apk/androidTest/debug/database-debug-androidTest.apk \
  | grep -q "StudyDatabase/$V.json" || echo "MISSING $V.json"

# 3) 若 V=71，71.json 必须已跟踪
git ls-files --error-unmatch \
  "core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/$V.json"
```

任一版本缺 schema/迁移，矩阵会在该版本中止 → **C（产品/迁移缺口）**，不得删该版本或缩小循环。

### 2.4 painter 24 例

文件：`core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualDrawOpsPainterInstrumentedTest.kt`。
类：`com.tingyun.smartmistakebook.core.data.visual.VisualDrawOpsPainterInstrumentedTest`。

**目标**：`OK (24 tests)`。关键设备判据（批次 30 判据 §2.1 逐例表仍适用，本批新增关注）：

| 用例（行） | 必须成立 |
|---|---|
| `anEightDigitColorAlphaMultipliesWithTheOpAlphaInsteadOfBeingOverwritten`（`:253`） | `#ff000080` 中心 `0xFFFF7F7F`（KD-78 ③ 像素正例） |
| `theReplayTextPaintKeepsTheExplicitlyPinnedFlagsMirroredByTheMeasureFingerprint`（`:264`） | 回放 `textPaintFlags == REPLAY_TEXT_PAINT_FLAGS == 0`（KD-78 ④） |
| `aStrokeOnlyPathWithAnOddDashReportsTheSameFirstGateAsTheGenerators`（`:428`） | 门序与生成侧同码同序（`PAINTER_DASH_ODD`） |
| `drawRenderedReplaysTheFrozenCommandSequence`（`:489`） | `validatedDocument` 与 `drawRendered` 像素逐位相等 |
| `drawRenderedFailsClosedOnMissingOrInvalidCommands`（`:511`） | `null`/截断 → `PAINTER_DRAW_COMMANDS` 且内嵌 `DRAWOPS_*` |

套件级：`measure paint flags .* do not match the environment fingerprint` = 0；9 类签名 0。

### 2.5 隔离 13 例

文件：`app/src/androidTest/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsIsolationInstrumentedTest.kt`。
类：`com.tingyun.smartmistakebook.visualjs.VisualJsIsolationInstrumentedTest`。

**目标**：`OK (13 tests)`，且以下全部成立：

1. `Start proc .*:visual_js` 的 pid 集合 = `visual_js warm-up finished` 的 pid 集合（按 pid 1:1）；
2. `visual_js warm-up failed` = **0**；
3. kill 四类之和 = spawn 数：`hard deadline (1500 ms) for request` + `terminate(generation` +
   `the host unbound the visual_js worker` + `due to start instr`；出现无法归类的杀因 = 失败；
4. 事后 `ps -A -o PID,NAME | grep visual_js` 为空；
5. 9 类签名 0；无 `death was not confirmed`。

> 批次 29 的失败形态（供归因）：spawn 14 / ready 0 / warm-up failed 12
> （`docs/known-defects.md:2902`）。若修复后仍 0 ready，按 §6 先排 D（旧包/环境）、再判 C，
> 不得只跑一遍就下结论。

### 2.6 flags 跨侧 2 例

文件：`app/src/androidTest/kotlin/com/tingyun/smartmistakebook/visualjs/VisualMeasureReplayPaintFlagsInstrumentedTest.kt`。
类：`com.tingyun.smartmistakebook.visualjs.VisualMeasureReplayPaintFlagsInstrumentedTest`。

**目标**：`OK (2 tests)`。

| # | 用例 | 必须成立 |
|---|---|---|
| 1 | `theMeasuringPaintAndTheReplayTextPaintShareTheFingerprintedFlags` | 度量 Paint flags=0；`environment.paintFlags=0`；指纹以 `|paintFlags=0` 结尾；`Paint().flags != 0`（API 34 默认 1283） |
| 2 | `aFingerprintThatDisagreesWithTheActualPaintFailsClosed` | 坏指纹 `paintFlags=1` 被拒且消息含 `paintFlags=1` |

第 2 例是负例：它绿 = 坏指纹被拒；它红 = KD-78 ④ 关闭不成立。

### 2.7 PDF 16 例

文件：`core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualDrawOpsPdfPainterInstrumentedTest.kt`。
类：`com.tingyun.smartmistakebook.core.data.visual.VisualDrawOpsPdfPainterInstrumentedTest`。

**目标**：`OK (16 tests)`。关键设备判据：

| 用例（行） | 必须成立 |
|---|---|
| `pdfWriteFailureIsCoded`（`:448`） | 失败流 → 有码 `PAINTER_PDF_IO`（不是裸 `IOException`，也不是无异常） |
| `pdfWriteFailureMidStreamKeepsTheFirstFailureText`（`:458`） | 首块后抛 → `PAINTER_PDF_IO`，消息含 `MID_STREAM_WRITE_FAILURE`，`acceptedBytes > 0` |
| `pdfFileWriteRoundTripsThroughReadback`（`:477`） | 目标存在/非空/含 `%%EOF`；`PdfRenderer` 回读 48×32；无 `.pending-` 残留 |
| `pdfReadOnlyTargetFailsClosedBeforeAnyWrite`（`:499`） | 目录 0555 → `PAINTER_PDF_IO` 且含 `not writable`；目标未发布 |
| `pdfReplayFailuresKeepTheScreenPainterCodeFamily`（`:527`） | stroke-only/奇数 dash → 同屏侧错误码，含 `ops[0]` |

套件级：`grep -c "java.io.IOException: simulated write failure"` **> 0**（证明 native 吞异常路径
确实被走到）；9 类签名 0。**仍无设备用例**：ENOSPC、静默短写、页数回读不符、`renameTo` 失败
（`docs/known-defects.md:2953`）——16 绿不得写成 D3 全路径完成。

### 2.8 一致性门 15 例（原型门，基线 11）

文件：`core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualDrawOpsParityInstrumentedTest.kt`。
类：`com.tingyun.smartmistakebook.core.data.visual.VisualDrawOpsParityInstrumentedTest`。

**定位**：`PARITY_GATE scope=PROTOTYPE product-gate=no ci-gate=no release-gate=no`
（`VisualDrawOpsParityInstrumentedTest.kt:100`，10:10 现树）。**不得**引用为产品门/发版结论。

**目标**：批次 31 设备轨为 `OK (15 tests)`（批次 30 为 11；T4 加 N09–N12 四例）；**10:10 现树 16 例**
（批次 32 T2 加 `n13CentroidBlindSpotIsCoveredByBboxOrShift`），最终以构建前复读为准。

**当前阈值（批次 31 设备轨为 `calibrated-r1`；10:10 现树改名 `calibrated-r2`，数值一个不动，
`VisualDrawOpsParityMetrics.kt`）**：

| 项 | 值 | 行（10:10 现树） |
|---|---|---|
| `THRESHOLD_VERSION` | `"calibrated-r2"`（批次 31 设备轨为 `"calibrated-r1"`） | `:262` |
| `maxBboxDeviationPx` | 1 | `:270` |
| `maxCentroidDeltaPx` | **0.75**（原 0.5） | `:271` |
| `minMassRatio` / `maxMassRatio` | **0.93 / 1.07**（原 0.95/1.05） | `:272-273` |
| `maxProfileTv` | 0.08 | `:274` |
| TEXT 判决顺序 | 硬几何护栏（缺墨 / bbox>1 / 质心>0.75 / bestShift）→ F4_GEOMETRY；再 F1_FONT；再 F2_AA | `:687-742` |

**阈值调整的合法性（本批红线）**：

1. 依据必须是**同设备、同夹具、AA 关**的读数，不是"改到能过"：
   P07 `centroid=0.5181329158118464`、`bboxDev=1`、`mass=0.957`、`edgeMean=28.11`
   （`docs/research/2026-10-29-parity-calibration.md:73-76`；
   `docs/research/evidence/2026-10-29-batch-30/step05-parity.md:43`；
   一手 logcat `build/device-track-batch30/step05-parity-logcat.txt:121-122`）。
2. 归因必须是 **AA/字体（S1+S2）而非几何**：屏侧 AA 关 + hinting + 整像素吸附；PDF 侧 AA 开 +
   hinting 关 + 浮点位置（`parity-calibration.md:13-14`、`:21-31`、`:44-49`）。
3. 放宽必须同步收紧：TEXT 的 F5 隔离带删除，F5 恒 0（`VisualDrawOpsParityMetrics.kt:749/:759`）；
   **盲区文案已回改**：旧 `blindWindow=text-centroid(0,0.76)px` 建立在"1 px 平移 = 1.000"
   的错误模型上，现树改为方向相关表述（`VisualDrawOpsParityInstrumentedTest.kt:95-105`，
   `blindSet=text-centroid B(-v,tau_c) per-fixture (direction-dependent)`）；不得再引用 `(0,0.76)` 标量窗口。
4. **放宽许可证 = N09–N13 必须全红**（10:10 现树函数：`:309/:368/:450/:466/:500`）：
   - N09：PDF 整块 +1 px → **必须红**。设备实测 P06 由 `bboxDev=2 > 1` 抓（理由
     `text bboxDev=2px > 1px`），同轮 `bestShift=(1,0)`、gain≈0.195；**质心读 1.2625 而非 1.000**
     （跨栅格欧氏距离，|v|=0.32）；P07 反解应约 0.608 < 0.75、**未执行**。旧"质心必须抓 / bbox 抓不住"作废；
     批次 32 T2 现树把这两组读数写成模型断言（P06 1e-6 容差、P07 0.02 容差）。
   - N10：整页错画 → 带内无墨 → F4_GEOMETRY（设备实测 `text ink missing on one side`）；
   - N11：180° 旋转，bbox/墨量不变，设备实测质心 1.1665 / TV 0.371 / IoU 0.233 → 必须红；
   - N12：整块 +4 px，设备实测 bboxDev=5、质心 3.56 → 必须红；
   - N13（批次 32 T2 新增）：合成栅格让质心/墨量都不变、只有 bbox/bestShift 可见 → 必须红且
     **不得**退成 F1_FONT；删几何护栏即失败。
   任一条绿 = `PARITY_GATE_DESENSITIZED`，判 **A（门失去分辨力）**，本轮标定作废，不得调阈值让它绿。

**套件级 logcat 判据（全部成立才算原型门当轮有效）**：

```bash
grep -c "PARITY_GATE scope=PROTOTYPE product-gate=no ci-gate=no release-gate=no" \
  build/device-track-batch31/step08-parity-logcat.txt          # == 测试数（每例 @Before 一次；批次 31 时点 15；现树 16）
grep -c "thresholds=calibrated-r1" \
  build/device-track-batch31/step08-parity-logcat.txt          # == 测试数（批次 31 证据 15；现树 r2，新跑改 calibrated-r2）
grep -c "PARITY_CONTROL " build/device-track-batch31/step08-parity-logcat.txt
# 批次 31 时点 15：N01×4 + N02–N07×6 + N09×2 + N10 + N11 + N12；N08 不打印。现树 16（+N13）
# 注意尾随空格：N11 另打 PARITY_CONTROL_DETAIL，不能用无空格的 "PARITY_CONTROL" 计数
grep "PARITY_CONTROL " build/device-track-batch31/step08-parity-logcat.txt \
  | grep -vc "red=true"                                        # 0
grep -c "PARITY_GATE_DESENSITIZED" build/device-track-batch31/step08-parity.log  # 0（fail() 消息，查 .log）
grep -c "PARITY_SYSTEMATIC_DRIFT"  build/device-track-batch31/step08-parity.log  # 0（fail() 消息，查 .log）
grep -c "PARITY_DIAGNOSTIC_WRITTEN" build/device-track-batch31/step08-parity-logcat.txt # 绿轮 0（Log.e）
grep "PARITY_ENV" build/device-track-batch31/step08-parity-logcat.txt | tail -1         # 含 replayTextPaintFlags=0
grep "PARITY_F5"  build/device-track-batch31/step08-parity-logcat.txt | grep -c "class=TEXT"  # 0
```

- `PARITY_F5` 总数 ≤2（非 TEXT 类）；TEXT 类 F5 必须为 0（批次 31 `calibrated-r1`；现树 `calibrated-r2` 同义）。
- **15/15 绿不等于产品门通过**；它只证明"PDF 路径相对屏路径未引入新偏差"。现树 16 例同理。

---

## 3. KD-80 关闭判据

KD-80 台账原文（`docs/known-defects.md:2977`）有四项：34/34 绿、表清单与 schema 逐名一致、
trimmer/选择计划单测绿、备份/恢复后旧行与视觉四表引用关系保持。工单口径只点名前两项；本判据
把四项全列出，**不拿前两项冒充全关闭**。

### 3.1 80-C1：备份 34/34（设备）

```bash
$A -s "$S" logcat -c
$A -s "$S" shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.data.backup.BackupRestoreInstrumentedTest \
  com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner \
  | tee build/device-track-batch31/step01-backup.log
$A -s "$S" logcat -v threadtime -d > build/device-track-batch31/step01-backup-logcat.txt
```

通过：`OK (34 tests)`；0 失败/错误/跳过；§2.1 的 8 条红例全绿；log 中无
`BackupSnapshotTrimException`、无 `expected:<69>`。

### 3.2 80-C2：表清单与 schema 逐名一致（可复跑，无需 Gradle）

在仓库根执行（`S` 取当前 `STUDY_DATABASE_VERSION`；当前 70，若升 71 改 `S=71`）：

```bash
S=70 python - <<'PY'
import json, os, re, sys
v = os.environ.get("S", "70")
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

**本轮实跑（S=70）**：

```text
SCHEMA=70 PLAN_TABLES=90 UNIQUE=90 JSON_TABLES=90
ONLY_PLAN=[]
ONLY_JSON=[]
DUP=[]
VIEWS_MATCH=True
PLAN_VERSION=70 JSON_VERSION=70
PLAN_HASH=11a8a701bae767a8a3c5bc810e4743a4 JSON_HASH=11a8a701bae767a8a3c5bc810e4743a4
RESULT=PASS
EXIT=0
```

**通过**：`RESULT=PASS` 且 `EXIT=0`；当前预期 90 表 + 2 视图、双向差集空、无重复。
若 `S=71`：`71.json` 不存在时脚本 `EXIT=2`，**不得**拿 70 的结果关闭 71 的 KD-80；
`SmbkSelectionPlan.SCHEMA_VERSION`/`IDENTITY_HASH` 必须同步到 71/新哈希。

**辅助（JVM 同源契约，可由门禁轨执行）**：

```bash
./gradlew --console=plain :core:data:testDebugUnitTest \
  --tests "com.tingyun.smartmistakebook.core.data.backup.SmbkSelectionPlanTest"
# 期望 12/12 绿；当前 12 例（grep -c '@Test' 实读）
```

注意：`SmbkSelectionPlanTest` 现硬编码 `SCHEMA_70_PATH`
（`core/data/src/test/.../SmbkSelectionPlanTest.kt:610-611`）。若 schema 升 71，该路径与 `70` 字面量
必须由 schema 写者同步；测试红不能靠放宽路径/断言解决。

### 3.3 80-C3：trimmer / 选择计划单测

- 设备：`BackupSnapshotTrimmerInstrumentedTest` **9 例**（`grep -c '@Test'` 实读），包
  `com.tingyun.smartmistakebook.core.data.test`；期望 `OK (9 tests)`。
- JVM：`SmbkSelectionPlanTest` **12 例**（同上）。
- 两者任一红：KD-80 保持 open；如果是 schema 版本/表集合同步问题，按 **C** 归因到 schema/备份
  同步面，不是设备问题。

### 3.4 80-C4：旧行与视觉四表引用关系（**当前缺口**）

- 台账要求"备份/恢复后旧行与视觉四表引用关系保持"（`docs/known-defects.md:2977`）。
- 实读：`grep -rln "tutor_visual" core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/backup/`
  = **0 命中**；`BackupRestoreInstrumentedTest.kt` 只有 `insertFigureGenerationAuditFixture` 的 import
  （`:22`），没有对 `tutor_visual_program/snapshot/message_visual/revision_visual` 的行或引用断言。
- 因此 **34/34 + 表清单 PASS 仍不足以证明 80-C4**。处置二选一：
  1. 在备份/恢复 34 例内补一条显式断言（v70 库含视觉四表行 → 备份 → 恢复 → 行数/外键引用保持）；
  2. 如实登记 `KD-80 80-C4 UNVERIFIED`，KD-80 不按全关闭写。
- 不得用"表在 90 张清单里"替代"引用关系保持"；前者是枚举，后者是数据语义。

### 3.5 关闭规则

```text
80-C1 ∧ 80-C2 ∧ 80-C3 ∧ 80-C4 → KD-80 可关闭
缺任一项 → 保持 open（或按项写成部分关闭，理由逐项列）
```

特别地：

- 34/34 绿但 `SmbkSelectionPlan` 仍是 69/86/20 → **C，open**（批次 30 的 8 红形态复现）。
- 表清单 PASS 但备份 34 有红 → **不可关闭**：表清单只证明静态枚举，不证明归档/恢复路径。
- schema 升 71 但备份计划未同步 → **C，open**，且属于本批 schema 写者与备份面的接缝缺陷。

---

## 4. 设备轨串行命令与 dex 符号门

> 以下命令由设备轨执行；**严格串行**，一条完成再下一条。`$S` 用 §4.0 登记的 serial。
> `export MSYS_NO_PATHCONV=1` 全程生效。Gradle 只允许 `assemble*`；**禁 `connectedAndroidTest`**。

### 4.0 步 0：设备再登记

批次 30 基线只读实测在线：`emulator-5562`（`test_device`）、`emulator-5566`（审计他线）、
`emulator-5584`（`kb_test_device`，他线，**禁碰**）
（`docs/research/2026-10-29-batch-30-baseline.md:283-293`）。本批执行前重新确认：

```bash
A=C:/Android/Sdk/platform-tools/adb.exe
$A devices -l
# 选定 $S=emulator-5562（不使用 -5566/-5584）
$A -s "$S" shell getprop ro.build.version.sdk
$A -s "$S" shell getprop ro.product.cpu.abi
$A -s "$S" shell getconf PAGE_SIZE
$A -s "$S" shell getprop ro.build.fingerprint
$A -s "$S" shell cat /proc/uptime /proc/loadavg
```

**通过**：`device` 态、非 offline/unauthorized；serial/API/ABI/PAGE_SIZE/fingerprint 五值进证据头。
优先 API 34 / x86_64 / 4 KB；换设备则注明**不能跨设备比较像素**。

### 4.1 步 1：构建（EXIT=0）

```bash
./gradlew --console=plain \
  :app:assembleStrictOfflineDebug \
  :app:assembleStrictOfflineDebugAndroidTest \
  :core:data:assembleDebugAndroidTest \
  :core:database:assembleDebugAndroidTest
```

**通过**：`EXIT=0` 且 `BUILD SUCCESSFUL`。构建后立即记录四包字节/sha256：

```bash
sha256sum app/build/outputs/apk/strictOffline/debug/app-strictOffline-debug.apk
sha256sum app/build/outputs/apk/androidTest/strictOffline/debug/app-strictOffline-debug-androidTest.apk
sha256sum core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk
sha256sum core/database/build/outputs/apk/androidTest/debug/database-debug-androidTest.apk
```

**构建早于源改动的陷阱**：构建前后对 `app/src`、`core/data/src`、`core/database/src` 做 sha256
快照并 `diff`；不一致 = **D**，重新构建。批次 30 用此法排除（`step00-environment.md:60-61`）。

### 4.2 步 2：安装（`adb install -r -t`）

包名（已由构建配置/合并清单/输出元数据实测）：

| APK | 包名 | 证据 |
|---|---|---|
| app | `com.tingyun.smartmistakebook.offline` | `app/build.gradle.kts:45-49` |
| app test | `com.tingyun.smartmistakebook.offline.test` | 合并清单 `package=` |
| core:data test | `com.tingyun.smartmistakebook.core.data.test` | `core/data/build/outputs/apk/androidTest/debug/output-metadata.json` |
| core:database test | `com.tingyun.smartmistakebook.core.database.test` | `core/database/.../output-metadata.json` |

```bash
$A -s "$S" install -r -t app/build/outputs/apk/strictOffline/debug/app-strictOffline-debug.apk
$A -s "$S" install -r -t app/build/outputs/apk/androidTest/strictOffline/debug/app-strictOffline-debug-androidTest.apk
$A -s "$S" install -r -t core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk
$A -s "$S" install -r -t core/database/build/outputs/apk/androidTest/debug/database-debug-androidTest.apk
```

**通过**：四条均 `Success`；设备侧 `pm path` + `sha256sum` 与宿主逐字节一致：

```bash
for p in com.tingyun.smartmistakebook.offline \
         com.tingyun.smartmistakebook.offline.test \
         com.tingyun.smartmistakebook.core.data.test \
         com.tingyun.smartmistakebook.core.database.test; do
  path=$($A -s "$S" shell pm path "$p" | tr -d '\r' | sed 's/^package://')
  $A -s "$S" shell sha256sum "$path"
done
```

`INSTALL_FAILED_UPDATE_INCOMPATIBLE` → 先 `adb uninstall <精确包名>` 再装，舞步记入证据。

### 4.3 步 3：dex 符号门（防旧包跑新门）

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

# app 测试包：隔离 13 + flags 2
need app/build/outputs/apk/androidTest/strictOffline/debug/app-strictOffline-debug-androidTest.apk \
  VisualJsIsolationInstrumentedTest \
  VisualMeasureReplayPaintFlagsInstrumentedTest \
  theMeasuringPaintAndTheReplayTextPaintShareTheFingerprintedFlags \
  aFingerprintThatDisagreesWithTheActualPaintFailsClosed \
  anExecuteAcquiredDuringWarmUpIsRefusedBoundedlyAndNeverWatchdogKilled

# core:data 测试包：备份 34 + painter 24 + PDF 16 + parity 16（现树）+ 仓库 8（现树）
need core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk \
  BackupRestoreInstrumentedTest \
  schema68ArchiveRestoresAndMigratesToCurrentVersion \
  VisualDrawOpsPainterInstrumentedTest \
  VisualDrawOpsPdfPainterInstrumentedTest \
  VisualDrawOpsParityInstrumentedTest \
  drawRenderedReplaysTheFrozenCommandSequence \
  drawRenderedFailsClosedOnMissingOrInvalidCommands \
  n09OnePixelTextShiftIsCaught \
  n13CentroidBlindSpotIsCoveredByBboxOrShift \
  n10WholePageMisdrawIsCaught \
  n11ShapeWrongButPositionAndMassRightIsCaught \
  n12WholeBlockShiftBeyondToleranceIsCaught \
  theReplayTextPaintKeepsTheExplicitlyPinnedFlagsMirroredByTheMeasureFingerprint \
  pdfWriteFailureMidStreamKeepsTheFirstFailureText \
  pdfFileWriteRoundTripsThroughReadback \
  pdfReadOnlyTargetFailsClosedBeforeAnyWrite \
  PdfWriteFailureTrackingStream PdfWriteTelemetry \
  TutorVisualCommitRepositoryInstrumentedTest \
  stageThenCommitStillDemotesThePreviousLatestCheckpoint \
  commitEvictsAnUnreferencedStagingIntermediateThroughPlanToCommand \
  freshForeignStagingLeaseIsNotEvictedByAnotherRequest \
  perProgramBudgetIsReenforcedInsideTheTransactionAfterAStaleRead \
  globalBudgetIsReenforcedInsideTheTransactionAfterAStaleRead \
  snapshotOverSourceBudgetIsRejectedBeforeAnyWrite

# core:database 测试包：69→70 4 + 迁移矩阵 15 + 结构门 5（10:07 现树）+ 提交 11（10:07 现树）
need core/database/build/outputs/apk/androidTest/debug/database-debug-androidTest.apk \
  FullMigrationMatrixInstrumentedTest \
  everyExportedSchemaVersionMigratesToCurrentWithoutDestructiveFallback \
  TutorVisualSnapshotMigration_69_70InstrumentedTest \
  visualTablesAreCreatedEmptyWithoutTouchingV69RowsAndIdempotent \
  onlyLiveSnapshotsCanBeLinkedAndCasUsesChangedRowCounts \
  indexExpectationsStayInSyncWithTheMigrationDdlConstants \
  indexShapeAssertionsCanFailOnWrongColumnOrderAndNonUniqueIndex \
  TutorVisualStructureGatesInstrumentedTest \
  messageLinkRejectsForeignKeyProgramAndStagingSnapshots \
  revisionPinRejectsStagingAndForeignKeyProgramButAcceptsSameProgramLive \
  evictionDeletesOnlyUnpinnedStagingIntermediates \
  commitTransactionExecutesDemoteAndEvict \
  repositoryPlanRetainsAStagingIntermediateOnlyBecauseItIsPinned \
  TutorVisualCommitInstrumentedTest \
  snapshotIdThatIsNotDerivedFromContentIsRejectedBeforeAnyWrite \
  snapshotDigestThatDoesNotMatchItsContentIsRejectedBeforeAnyWrite \
  drawCommandsDigestThatDoesNotMatchItsContentIsRejectedBeforeAnyWrite
```

**通过**：三个 `DEX OK`；任一 `MISSING` = 旧包/漏装，**停**，重建重装，不得继续跑。

**schema 资产门**（矩阵按 assets 建库，必须与版本常量同数）：

```bash
V=$(sed -n 's/.*STUDY_DATABASE_VERSION = \([0-9][0-9]*\).*/\1/p' \
  core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabase.kt | head -1)
echo "V=$V"
unzip -l core/database/build/outputs/apk/androidTest/debug/database-debug-androidTest.apk \
  | grep -E 'StudyDatabase/[0-9]+\.json$' | wc -l   # == V
unzip -l core/database/build/outputs/apk/androidTest/debug/database-debug-androidTest.apk \
  | grep -q "StudyDatabase/$V.json" || { echo "MISSING $V.json"; exit 1; }
```

- 现树 `V=70`；若 T3 升 71，`71.json` 必须同时进 assets 且被 `git ls-files` 跟踪。
- 方法名以源码为准；本节的 `pdfFileWriteRoundTripsThroughReadback` 与
  `VisualDrawOpsPdfPainterInstrumentedTest.kt:477` 一致。若门报 `MISSING`，先核对大小写与落码版本，
  不得直接把该符号删掉绕过。

### 4.4 步 4–11：八套件（每套件前清 logcat、后落 raw log）

```bash
mkdir -p build/device-track-batch31

# 1) 备份 34
$A -s "$S" logcat -c
$A -s "$S" shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.data.backup.BackupRestoreInstrumentedTest \
  com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner \
  | tee build/device-track-batch31/step01-backup.log
$A -s "$S" logcat -v threadtime -d > build/device-track-batch31/step01-backup-logcat.txt

# 2) 69→70（当前 4 例）
$A -s "$S" logcat -c
$A -s "$S" shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.database.TutorVisualSnapshotMigration_69_70InstrumentedTest \
  com.tingyun.smartmistakebook.core.database.test/androidx.test.runner.AndroidJUnitRunner \
  | tee build/device-track-batch31/step02-migration-69-70.log
$A -s "$S" logcat -v threadtime -d > build/device-track-batch31/step02-migration-69-70-logcat.txt

# 3) 迁移矩阵 1→V（当前 15 例）
$A -s "$S" logcat -c
$A -s "$S" shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.database.FullMigrationMatrixInstrumentedTest \
  com.tingyun.smartmistakebook.core.database.test/androidx.test.runner.AndroidJUnitRunner \
  | tee build/device-track-batch31/step03-migration-matrix.log
$A -s "$S" logcat -v threadtime -d > build/device-track-batch31/step03-migration-matrix-logcat.txt

# 4) painter 24
$A -s "$S" logcat -c
$A -s "$S" shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.data.visual.VisualDrawOpsPainterInstrumentedTest \
  com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner \
  | tee build/device-track-batch31/step04-painter.log
$A -s "$S" logcat -v threadtime -d > build/device-track-batch31/step04-painter-logcat.txt

# 5) 隔离 13
$A -s "$S" logcat -c
$A -s "$S" shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.visualjs.VisualJsIsolationInstrumentedTest \
  com.tingyun.smartmistakebook.offline.test/androidx.test.runner.AndroidJUnitRunner \
  | tee build/device-track-batch31/step05-isolation.log
$A -s "$S" logcat -v threadtime -d > build/device-track-batch31/step05-isolation-logcat.txt

# 6) flags 2
$A -s "$S" logcat -c
$A -s "$S" shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.visualjs.VisualMeasureReplayPaintFlagsInstrumentedTest \
  com.tingyun.smartmistakebook.offline.test/androidx.test.runner.AndroidJUnitRunner \
  | tee build/device-track-batch31/step06-flags.log
$A -s "$S" logcat -v threadtime -d > build/device-track-batch31/step06-flags-logcat.txt

# 7) PDF 16
$A -s "$S" logcat -c
$A -s "$S" shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.data.visual.VisualDrawOpsPdfPainterInstrumentedTest \
  com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner \
  | tee build/device-track-batch31/step07-pdf.log
$A -s "$S" logcat -v threadtime -d > build/device-track-batch31/step07-pdf-logcat.txt

# 8) 一致性门 15（原型门）
$A -s "$S" logcat -c
$A -s "$S" shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.data.visual.VisualDrawOpsParityInstrumentedTest \
  com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner \
  | tee build/device-track-batch31/step08-parity.log
$A -s "$S" logcat -v threadtime -d > build/device-track-batch31/step08-parity-logcat.txt
```

逐步汇总：

```bash
for f in step01-backup step02-migration-69-70 step03-migration-matrix step04-painter \
         step05-isolation step06-flags step07-pdf step08-parity; do
  echo "== $f"; grep -E "OK \(|Tests run:|Failures:|Error|Skipped|Time:" \
    "build/device-track-batch31/$f.log" | tail -4
done
# 期望（按本快照；构建前须复读）：OK (34 tests) / OK (4 tests) / OK (15 tests) / OK (24 tests)
#                                  / OK (13 tests) / OK (2 tests) / OK (16 tests) / OK (15 tests)
```

任一 `Failures: [1-9]`、`Error`、`Skipped`、或 summary 非目标字符串 → **停在该步**，按 §6 归因，
不得"先跑完再说"。

### 4.5 补充套件（不计入八套件基线，但本批持久化收口必须跑）

计划任务 3–6 触碰 `TutorVisualCommitRepository`、checkpoint 保留、身份门与 schema；下列套件是
这些落点的设备面。批次 30 曾跑：`TutorVisualCommitInstrumentedTest` **8/8**、
`TutorVisualCommitRepositoryInstrumentedTest` **3/3**（含磁盘满拒提交）
（`step07-persistence-migration.md:43-44`）。**批次 31 现树计数已复读**（集成接线，2026-10-29；10:05 现树刷新）：
`TutorVisualCommitInstrumentedTest` **11**（批次 31 为 9；+`snapshotDigest…`、`drawCommandsDigest…`）、
`TutorVisualCommitRepositoryInstrumentedTest` **8**（批次 31 为 4；+`commitEvictsAnUnreferencedStagingIntermediateThroughPlanToCommand`、租约保留、per-program/global 事务内重判、超源预算 4 例），
并新增 `TutorVisualStructureGatesInstrumentedTest` **5 例（10:07 现树；批次 31 为 4）**（消息 link 跨 program/STAGING、题面 pin STAGING/跨 program、
跨 program CAS、demote/evict 在提交事务内真的执行）。三套件都必须跑；类名与方法名已同步进 §4.3 的 data/database dex 门：

```bash
# 身份/提交（core:database.test）
$A -s "$S" shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.database.TutorVisualCommitInstrumentedTest \
  com.tingyun.smartmistakebook.core.database.test/androidx.test.runner.AndroidJUnitRunner

# 仓库/checkpoint（core:data.test）
$A -s "$S" shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.data.visual.TutorVisualCommitRepositoryInstrumentedTest \
  com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner

# 结构门（core:database.test）
$A -s "$S" shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.database.TutorVisualStructureGatesInstrumentedTest \
  com.tingyun.smartmistakebook.core.database.test/androidx.test.runner.AndroidJUnitRunner
```

若 checkpoint 保留/身份门新增仪器化用例，**必须**把新方法名加入 §4.3 的 data/database dex 门；
否则绿轮不覆盖本批修复。

---

## 5. 签名扫描与专项 logcat 判据

### 5.1 九类崩溃签名（八份 logcat）

```bash
for f in step01-backup-logcat.txt step02-migration-69-70-logcat.txt \
         step03-migration-matrix-logcat.txt step04-painter-logcat.txt \
         step05-isolation-logcat.txt step06-flags-logcat.txt \
         step07-pdf-logcat.txt step08-parity-logcat.txt; do
  printf "%s: " "$f"
  grep -cE "FATAL EXCEPTION|ANR in |NoSuchMethodError|NoClassDefFoundError|UnsatisfiedLinkError|SIGSEGV|CANNOT LINK|failed to map segment|death was not confirmed" \
    "build/device-track-batch31/$f"
done   # 期望全 0
```

### 5.2 备份 / 迁移专项

```bash
grep -c "BackupSnapshotTrimException" build/device-track-batch31/step01-backup.log   # 0
grep -c "expected:<69>"               build/device-track-batch31/step01-backup.log   # 0
grep -c "ComparisonFailure"           build/device-track-batch31/step02-migration-69-70.log # 0
```

- 69→70 绿 = 第 1 例按 `:76 → :77 → :78` 的顺序先跑数据保全、再跑外键负向、最后跑形状；
  批次 30"形状先红导致后面一条没跑到"的形态已被执行顺序消除。若只看 summary 不放心，可加
  `-e class Class#method` 单方法复跑并保留 log。
- 套件必须是 **4 例**；只跑 2 例 = 旧包（D），说明两个元用例（批次 31 `:228`、`:265`；10:10 现树 `:238`、`:275`）没进 APK。
- 迁移矩阵绿 = 循环内每个 1..V 都建库并升到当前；任一版本异常会使该例红。

### 5.3 隔离 worker 生命周期

```bash
grep -c "Start proc .*:visual_js"              build/device-track-batch31/step05-isolation-logcat.txt
grep -c "visual_js warm-up finished"           build/device-track-batch31/step05-isolation-logcat.txt
grep -c "visual_js warm-up failed"             build/device-track-batch31/step05-isolation-logcat.txt  # 0
grep -c "hard deadline (1500 ms) for request"  build/device-track-batch31/step05-isolation-logcat.txt
grep -c "terminate(generation"                 build/device-track-batch31/step05-isolation-logcat.txt
grep -c "the host unbound the visual_js worker" build/device-track-batch31/step05-isolation-logcat.txt
grep -c "due to start instr"                   build/device-track-batch31/step05-isolation-logcat.txt
# 前两数按 pid 1:1 相等；后四数之和 = spawn；无未分类杀因
$A -s "$S" shell ps -A -o PID,NAME | grep visual_js   # 期望空
```

### 5.4 flags / PDF

```bash
for f in step05-isolation-logcat.txt step06-flags-logcat.txt step04-painter-logcat.txt; do
  printf "%s: " "$f"
  grep -c "measure paint flags .* do not match the environment fingerprint" \
    "build/device-track-batch31/$f"
done   # 期望 0 0 0

grep -c "java.io.IOException: simulated write failure" \
  build/device-track-batch31/step07-pdf-logcat.txt   # 期望 > 0
```

### 5.5 一致性门

见 §2.8 的命令块。额外要求：

- `PARITY_CONTROL `（带尾随空格）行数必须等于当轮 `assertRed` 执行数；批次 31 快照 15 例 → **15**（现树 16 → 16）。
  `PARITY_CONTROL_DETAIL` 是附加明细（N09/N11/N12），不得计进控制数。
- 每个 `PARITY_CONTROL ` 必须 `red=true`；`PARITY_GATE_DESENSITIZED`=0、`PARITY_SYSTEMATIC_DRIFT`=0
  （两者是 `fail()` 消息，查 `step08-parity.log`，不是 logcat）。
- `PARITY_GATE` 行数 = 测试数（每例 `@Before` 一次；批次 31 时点 15，现树 16）；每行都必须 `product-gate=no` 且
  `thresholds=calibrated-r1`（批次 31 证据；现树 r2，新跑改 `calibrated-r2`）。
- `PARITY_F5` 中 `class=TEXT` 必须为 0；非 TEXT ≤2。
- `PARITY_DIAGNOSTIC_WRITTEN` 绿轮为 0（PNG 只在失败时写）。
- **原型门**：`product-gate=no` 行必须在；任何"PDF 可用/发版通过"的结论都不得引用本门。

---

## 6. 红例归因规则（先测试层，后产品层）

### 6.1 绝对禁止

- 删除/弱化断言、放大 delta/容差、缩小扫描范围、`@Ignore`/`Assume` 跳过、用 `-e class` 过滤红例、
  把 `OK (N tests)` 的 N 改小、把失败改写成"已知问题"后仍声称通过。
- **先改测试让它变绿、再回头找原因**。
- 以"批次 30 曾 painter 24/24、PDF 16/16、隔离 13/13 绿"或"另一台设备绿"替代当前树/当前 APK 的证据。
- 把一致性门（prototype）的红/绿写成产品门或发版结论。
- 为 P07 变绿直接调阈值而不引用设备读数与归因证据。

### 6.2 强制顺序

1. **抓原样**：完整失败行、断言消息、stack（项目帧 + 框架帧首行）、`Tests run:` 行；归因前不删 log。
2. **定位第一项目帧**：哪个文件哪一行。
3. **先测试层**：
   - **A（夹具/门逻辑缺陷）**：失败在测试辅助代码、夹具、或原型门自身判定逻辑；产品路径未被
     执行到，或门的分辨力断言写错。例：批次 30 的 `inspectV70Shape` 曾用
     `substringBefore(" IF NOT EXISTS")` 比 DDL（A 类；本批已改为 PRAGMA 结构读数）。
     处置 = 改夹具/门逻辑，产品源零改动；**不得借机放宽阈值**。
   - **B（测试期望漂移）**：实际值与权威源（常量/契约/编译器/Node 台/探针）一致，测试期望写错。
     处置 = **改成权威值**（不是改成实测值），保留 delta 0；补权威源 `path:line`。
4. **再环境层 D（设备/旧包）**：设备 offline、装错旧包、API/ABI/PAGE_SIZE 不符、字体差异导致
   跨设备像素比较、构建早于源改动。处置 = 重登记/重建重装/核 sha256 与 dex 门；
   **跨设备像素差异不作产品缺陷**，只在单设备内比较像素。
5. **最后产品层 C**：测试期望符合权威源，产品输出/行为错。处置 = 修产品；保留/加强测试；
   若为已知缺口登记 KD，**不得删测试或放宽**。
6. **先红后绿闭环**：红 log → 归因证据 → 改动清单（精确到文件/行/牵动面）→ 重建重装（新 sha256）
   → 同命令复跑绿 log。未复跑 = UNVERIFIED。

> 口诀：**A/B 先行，D 排除，最后才 C**。顺序颠倒（先认定产品错）最容易把测试缺陷修成产品缺陷。

### 6.3 批次 31 专用陷阱

| 现象 | 先判 | 处置 |
|---|---|---|
| 备份仍 7 红 `快照 schema=70，裁剪器只支持 schema=69` | 先 D（旧包/旧测试）→ 再 C | 新包仍 69 → 查 `SmbkSelectionPlan.SCHEMA_VERSION`/`BackupSnapshotTrimmer`；这是 KD-80 复现，open |
| 备份 1 红 `expected:<69> but was:<70>` | **D**（旧测试包） | dex 门查 `schema68ArchiveRestoresAndMigratesToCurrentVersion`；重装重跑 |
| 69→70 红 `expected:<CREATE UNIQUE INDEX[]> but was:<…>` | **D**（旧包/旧断言） | 当前断言已是 PRAGMA 结构读数（§2.2），不会打印该消息；按旧包重建重装，不得改产品 |
| 69→70 只跑 2 例 | **D** | 旧包；现树 4 例（T3 元用例），重建 |
| 迁移矩阵在某版本红 | C（迁移/schema 缺失）或 D（assets 缺失） | 先查 §4.3 资产门与 `V`；再查该版本迁移注册 |
| parity 11/15 绿但现树 16 | **D** | 旧包；N09–N13 未跑不能作为 `calibrated-r2` 证据 |
| parity 正例红 / N09–N13 任一绿 | **A**（门失去分辨力） | 不得调阈值；`PARITY_GATE_DESENSITIZED` 即证据；标定作废 |
| P07 仍红 `F4_GEOMETRY centroid≈0.518` 且 N09 红 | 先 A/B（阈值标定）后 C | 检查 `calibrated-r2` 是否进 APK（dex/`thresholds=calibrated-r2`）；阈值调整必须有设备读数与 AA/字体归因 |
| PDF `PAINTER_PDF_IO` 不出现 | 先 D（旧包）→ C | dex 门查 `PdfWriteFailureTrackingStream`/新方法；logcat native IOException 必须 >0 |
| 隔离 12 红且 `paintFlags 1283` | 先 D → C | 新包仍 1283 = 修复未生效；旧包 = D |
| 跨设备像素差 | **D** | 只在同设备内比较；不得判产品缺陷 |

### 6.4 归因记录最低字段

红例名 + 行号 + 失败消息 + 第一项目帧 + 四源核对结果（逐源 `path:line`）+ 类别（A/B/C/D）+
处置（改哪个文件哪几行/或不改的理由）+ 重建 sha256 + 绿轮 summary。缺任一字段 = 归因不成立。

---

## 7. 本文件边界与未验证项

- **UNVERIFIED**：八套件均**未**在本轮执行；本文件只给判据与命令，`OK (…)` 是目标字符串，
  不是已通过结果。
- **UNVERIFIED**：备份 34/34、KD-80 关闭、P07 `calibrated-r1`/`calibrated-r2` 绿轮、N09–N13 负控设备面。
- **UNVERIFIED**：KD-80 80-C4（视觉四表引用关系）——现 34 例无 `tutor_visual` 断言（§3.4）。
- **UNVERIFIED**：schema 71 分支——**10:10 现树** `STUDY_DATABASE_VERSION = 71` 且 `VisualSnapshotIdentityMigration_70_71.kt:53`
  已注册 `Migration(70, 71)`，但 **`71.json` 缺失**、`SmbkSelectionPlan` 仍 70；1→71 矩阵、备份 34/34（S=71）、
  KD-80 S=71 分支均未跑。
- **在飞计数**：69→70 套件 4（T3）、parity **15**（批次 31 快照；10:10 现树 **16**，批次 32 T2 +N13）；本文数字为快照，构建前必须复读（§1.3）。
- **已核（只读）**：八套件 `@Test` 计数 34/4/15/24/13/2/16/15（批次 31 快照；10:10 现树 parity 16）；
  schema 70（批次 31 快照；10:10 现树 71、`71.json` 缺失）；`SmbkSelectionPlan` 70/90/21/CLOSURE 3 与 70.json
  逐名一致（§3.2 脚本 `RESULT=PASS`）；备份测试期望读 `SmbkSelectionPlan.SCHEMA_VERSION`（`:590`）；
  69→70 形状断言已改为 PRAGMA 结构读数（批次 31 时点 `:338-369`；10:10 现树 `inspectV70Shape` 已不在）
  + 两个元用例（批次 31 `:228`、`:265`；10:10 现树 `:238`、`:275`）；parity 阈值 `calibrated-r1`
  （批次 31）→ 现树 `calibrated-r2`（`VisualDrawOpsParityMetrics.kt:262-275`，数值不动）与设备读数出处（`parity-calibration.md:73-76`）。
- **不在本判据面**：编译器/引擎/Node 台、R8 往返、arm64/真机/16 KB 页、生产签名、
  真实 Provider（无凭据 → 模型质量 UNVERIFIED）；一致性门仍是原型门。
- **设备依赖**：`emulator-5562` 在线；若掉线，先按 §4.0 重新登记。
- **本文件自身是新增 `??` 产物**；未做 git 写操作，未改任何热区文件。

### 7.1 设备轨回填（2026-10-29 复核修复轮；只读，零 Gradle/零设备/零 git 写）

上文 §7 的"八套件均未执行 / 备份与 N09–N12 未出数"只对 05:30 快照成立。设备轨 06:20–06:54 已把
`docs/research/evidence/2026-10-29-batch-31/` 的 15 份证据落盘，实测结果：

- 备份 **`OK (34 tests)`**（E-01）、69→70 **`OK (4 tests)`**（E-02）、迁移矩阵 **`OK (15 tests)`**（E-03，循环 1..70）、
  painter **`OK (24 tests)`**（E-04）、隔离 **`OK (13 tests)`**（E-05）、flags **`OK (2 tests)`**（E-06）、
  PDF **`OK (16 tests)`**（E-07）、结构门三套 **17/17**（E-09）。
- 一致性门 **FAIL 14/15**（E-08/E-08b）：唯一失败是 N09-P06 的旧 `centroid=1.000` 等式（实测
  1.2625；**旧源期望，现树已改为跨栅格模型断言，见 §0-4**）；门级已 `red=true`（`bboxDev=2px > 1px`），归因 A，
  N09-P07 中止未执行，**`calibrated-r1` 绿轮仍 UNVERIFIED**（现树 `calibrated-r2` 同样 UNVERIFIED）。
- **仍 UNVERIFIED**：KD-80 80-C3 的 JVM 仓内执行（E-12 记未跑；本机 XML 12/0 与 17/0 属 `build/`
  本地产物，命令归属未验证）、KD-80 80-C4（零断言）、schema 71 分支、跨 API/真机。
