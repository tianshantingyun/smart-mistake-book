# G0 本会话改动清单（批次 50）

## 一、本会话实际编辑

| 路径 | 性质 | 摘要 |
|---|---|---|
| `docs/research/2026-10-17-6c2-content-selection-design.md` | 文档 | §9.11 残块冻结为 `6c2-plan-v5`（closure 5/schema 73）；§9.14 当前块统一为单一 `6c2-plan-v3`；补 `views`；note 升 75；§9.11 交叉引用改 §9.14 |
| `tools/ci/check_backup_plan_sync.py` | 门脚本 | 75.json / 99 表 / `emptyFullSelectionGolden` / views 三方 / v4 公式加 `interiorTables` |
| `tools/tests/test_backup_plan_sync.py` | 自测 | identity 漂移用例改用现行 `f9a3d1c9…`；新增 marker 不成对 exit 2 用例 |
| `core/data/src/main/kotlin/.../backup/SmbkSelectionPlan.kt` | **仅注释** | schema 75 / closure 6 / 99 表 / v3 标记；常量与逻辑零改动 |
| `core/data/src/test/kotlin/.../backup/SmbkSelectionPlanTest.kt` | 测试正则+注释 | v6→v3 标记；99/123 计数；golden 值未变 |
| `core/data/src/androidTest/kotlin/.../backup/BackupRowCountsCoverageInstrumentedTest.kt` | 测试断言目标 | 73.json→75.json、92→99；等值关系未变 |
| `core/data/src/androidTest/kotlin/.../backup/BackupSelectionRestoreInstrumentedTest.kt` | 测试断言目标 | 恢复后版本 73→`SmbkSelectionPlan.SCHEMA_VERSION` |
| `core/data/src/androidTest/kotlin/.../backup/BackupV4PackRestoreInstrumentedTest.kt` | 测试断言目标 | `readDatabaseVersion()`/`user_version` 73→当前 schema |
| `docs/acceptance/domain-index.md` | 台账导航 | B 行出口追加「75 需新证据」注记 |
| `docs/acceptance/b-domain-schema75-annotation.md` | 新增 | B 域 v73 历史标注（不改 ledger.md） |
| `docs/research/evidence/2026-11-09-batch-50/g0/*` | 新增 | 本目录证据 |

## 二、开工时已是 M、本会话**未触碰**的备份测试

- `BackupRestoreInstrumentedTest.kt`
- `BackupVisualReferenceInstrumentedTest.kt`
- `DeviceMigrationDrillInstrumentedTest.kt`

（三者 mtime 均为 2026-11-07 05:18，属既有未提交改动；本会话只读了它们，未写入。）

## 三、本会话未改

- `docs/acceptance/ledger.md`（sha256 `264425f5…` 被 922 份行级复核绑定）。
- `ClosureRuleTables.kt`、`StudyDatabase.kt`、`75.json`（KSP 导出物）。
- 任何 JS/UI 在飞文件。
