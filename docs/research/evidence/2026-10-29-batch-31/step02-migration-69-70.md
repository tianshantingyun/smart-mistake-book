# E-02 69→70 迁移套件 4 例：`TutorVisualSnapshotMigration_69_70InstrumentedTest`（PASS 4/4）

| 字段 | 值 |
|---|---|
| evidence-id | E-02 |
| convention-version | 1 |
| batch | 2026-10-29-batch-31 |
| step | 二（八套件第 2；判据 §2.2） |
| gate-or-kd | 批次 30 "DDL 断言缺陷" A 类修复后的绿轮；schema 71 分支未触发（当前 70） |
| verdict | **PASS**：`OK (4 tests)`；4 例含数据保全 → 外键负向 → PRAGMA 形状顺序 |
| executed-at | 2026-10-29T06:32:53–06:32:58+0800 |
| operator | 设备验证轨（subagent，批次 31） |
| head | `59d4a03f` |
| worktree | dirty（本轨零写入） |
| device-serial | `emulator-5562`（API 34 / x86_64 / 4096） |
| apk | `core/database/build/outputs/apk/androidTest/debug/database-debug-androidTest.apk` |
| apk-sha256 | `4e54f0de476712add5bc839dec16a419b3927a4fdfc27bf41beca4e290c7da2d`（设备侧一致） |
| apk-version | （测试包 versionName 空） |
| exit-code | 宿主 shell `EXIT=0`（非判据） |
| raw-log | `build/device-track-batch31/step02-migration-69-70.log`（65 行 / 3372 B）/ `-logcat.txt`（106 行 / 15963 B） |
| record-ref | `docs/research/2026-10-29-batch-31-record.md`（后写） |
| sanitized | 是（§6；扫描 0 命中） |

## 1. 目的与判据

判据 §2.2：现树 4 例（批次 30 为 2；T3 新增两个元用例）；必须绿，且：
第 1 例按"数据保全（`inspectV70DataPreservation`）→ 外键负向（`assertV70ForeignKeyBehaviour`）→ 形状
（`inspectV70Shape`，PRAGMA 结构读数）"顺序执行；第 3 例把迁移 DDL 常量与期望同源；第 4 例是负控，
证明形状断言仍有分辨力。**只跑 2 例 = 旧包（D）**。

## 2. 命令（可复跑）

```bash
export MSYS_NO_PATHCONV=1
A=C:/Android/Sdk/platform-tools/adb.exe; S=emulator-5562
$A -s $S logcat -c
$A -s $S shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.database.TutorVisualSnapshotMigration_69_70InstrumentedTest \
  com.tingyun.smartmistakebook.core.database.test/androidx.test.runner.AndroidJUnitRunner \
  | tee build/device-track-batch31/step02-migration-69-70.log
$A -s $S logcat -v threadtime -d > build/device-track-batch31/step02-migration-69-70-logcat.txt
```

## 3. 结果摘要

| 指标 | 值 | 判据 |
|---|---|---|
| tests run | 4 | 期望 4（构建前复读 4） |
| failures / errors / skipped | 0 / 0 / 0 | 期望 0 |
| `STATUS_CODE: 0` | 4 | 每例一条通过状态 |
| `ComparisonFailure` | 0 | 期望 0（批次 30 A 类红形态未复现） |
| `INSTRUMENTATION_STATUS: stack=` | 0 | — |
| 9 类签名 | 0（E-11） | 期望 0 |

## 4. 关键输出（有界）

```text
Time: 5.365

OK (4 tests)

INSTRUMENTATION_CODE: -1
```

逐例（`grep INSTRUMENTATION_STATUS: test=` + 其后状态码）：

```text
visualTablesAreCreatedEmptyWithoutTouchingV69RowsAndIdempotent        codes=['1','0']
onlyLiveSnapshotsCanBeLinkedAndCasUsesChangedRowCounts                codes=['1','0']
indexExpectationsStayInSyncWithTheMigrationDdlConstants               codes=['1','0']
indexShapeAssertionsCanFailOnWrongColumnOrderAndNonUniqueIndex        codes=['1','0']
```

第 4 例绿 = 负控（错列序 / 非唯一 / 缺失 / 多一条少一条都必须判不等）确实执行且通过，
即形状断言没有被改松。

## 5. 失败 / 跳过 / 异常

无。schema 71 分支未触发：`STUDY_DATABASE_VERSION = 70`、`schemas/…/` 最高 `70.json`、APK assets
70 个 json（E-00 §4）。**UNVERIFIED**：70→71 迁移与 `71.json`（本批不升 schema）。

## 6. 脱敏与去噪

- 删除：无。
- 保留：summary、逐例状态码、零命中的 `ComparisonFailure` 计数。
- 扫描：R4 §4.4 → 0 命中。
- 二进制/截图：无。

## 7. 判定

PASS。判据 §2.2 的"4 例"与"负控必须绿"均满足；批次 30 的 `expected:<CREATE UNIQUE INDEX[]>` A 类
红形态未复现（本 log `ComparisonFailure`=0，且断言已改 PRAGMA 结构读数）。

## 8. 原始全量（不入仓）

- 路径：`build/device-track-batch31/step02-migration-69-70.log` / `-logcat.txt`
- 行数/大小：65 行 / 3372 B；logcat 106 行 / 15963 B
- 说明：`build/` 被 `.gitignore:15` 忽略。
