# 批次 49 · P0 收尾证据（2026-11-08）

> 执行角色：core:data 测试工程师（本 ask 只做四件事：review_log 最近窗口用例、4 个定向
> 测试类、acceptance_lint 前缀判定、5 条原红复核）。**未改**掌握阈值 / G6 / 权重 /
> VERSION / 金样 / 台账 verdict。所有命令均为本会话实跑，证据原文件在同目录。

## 1. review_log 最近窗口（3 行、limit=2、保留最新且升序）

### 1.1 落点

- 用例：`core/database/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/database/MasterySchedulingMigrationInstrumentedTest.kt`
  新增 `recentReviewLogWindowKeepsTheNewestRowsInAscendingOrder`（+51 行，含
  `reviewLogRow(...)` 夹具）；该文件在本 ask 开工时 `git status` 为**空**（无他线占用）。
- 走真实 DAO：`StudyDatabaseFactory.openInMemory` → `recordReviewLogEntries` →
  `readReviewLogSamples(limit = 2)`（Room + 真 SQLite，非 fake）。

### 1.2 红 → 绿（同一用例，同一设备 emulator-5566）

| 轮 | 被测 SQL | 命令 | 结果 |
|---|---|---|---|
| 红（临时回退） | 修复前 `ORDER BY reviewed_at_utc ASC, review_log_id ASC LIMIT :limit` | `:core:database:assembleDebugAndroidTest` → `python -I tools/ci/am_instrument_recorder.py --serial emulator-5566 --package com.tingyun.smartmistakebook.core.database.test --class "…MasterySchedulingMigrationInstrumentedTest#recentReviewLogWindowKeepsTheNewestRowsInAscendingOrder"` | **EXIT=1**，`failures=1`；`AssertionError: expected:<[2000, 3000]> but was:<[1000, 2000]>`（`b49-review-window-red-old-sql.xml`） |
| 绿（恢复修复） | 内层 `DESC` 取窗 + 外层 `ASC`（树内现态） | 同上，全类过滤 | **EXIT=0**，`tests=4 evaluated=4 failures=0 errors=0 skipped=0`（`b49-review-window-green-final.xml`） |

- 临时回退后已恢复：`git diff --stat LearningDao.kt` = `13 insertions(+), 4 deletions(-)`，
  与恢复前一致。
- 设备绑定：`b49-review-window-green-final.device.json`（serial `emulator-5566`，
  boot_id `2b49b0ad-…`，test APK sha256 `5a8f778e…`）。
- 构建日志：`00-assemble-green.log` / `01-assemble-red-reverted.log` / `02-assemble-green-restored.log`（均 `BUILD SUCCESSFUL`）。

## 2. 四个定向 JVM 测试类（逐条 exit）

| # | 类 | 命令（本会话实跑） | exit | XML 计数 |
|---|---|---|---|---|
| 1 | `StudyReviewPlannerExamWindowTest` | `./gradlew --console=plain :core:data:testDebugUnitTest --tests "com.tingyun.smartmistakebook.core.data.study.StudyReviewPlannerExamWindowTest"` | **0** | tests=8 skipped=0 failures=0 errors=0 |
| 2 | `StudyReviewPlannerServiceCapacityPreviewTest` | 同上换类名 | **0** | tests=4 skipped=0 failures=0 errors=0 |
| 3 | `LongRangeSampleLedgerReadTest` | 同上换类名 | **0** | tests=2 skipped=0 failures=0 errors=0 |
| 4 | `PlannedReasonCalibrationTest` | `./gradlew --console=plain :core:domain:test --tests "com.tingyun.smartmistakebook.core.domain.PlannedReasonCalibrationTest"` | **0** | tests=4 skipped=0 failures=0 errors=0 |

- 计数口径说明：`git show HEAD:…ExamWindowTest.kt | grep -c @Test` = **6**，工作树
  = **8**（+2 新例）。ask 写的「7 例」与实际不符；实际 8 例全绿。
- `:core:domain:testDebugUnitTest` 是 `tasks.register` 的委托任务（`core/domain/build.gradle.kts:26-28`），
  **没有 `--tests` 选项**；首次按 ask 字面跑 `:core:domain:testDebugUnitTest --tests …`
  得到 `BUILD FAILED: Unknown command-line option '--tests'`（exit 1），改用真实 `test`
  任务后 4/4 绿。
- 每条 XML 原文已复制到本目录（`01-exam-window.xml` … `04-planned-reason-calibration.xml`）；
  每轮前删除旧结果文件以强制实际执行（日志分别含 `> Task :core:data:testDebugUnitTest`
  或 `> Task :core:domain:test`，且 `actionable tasks` 行显示 `1 executed`）。

## 3. acceptance_lint 检查列前缀判定（临时样例，不写台账）

- 样例：`lint-prefix/sample-ledger.md`（4 行，**不是** ledger.md）；证据文件 `fake-cmd.txt`。
- 命令：`python -I tools/ci/acceptance_lint.py --ledger docs/research/evidence/2026-11-08-batch-49/lint-prefix/sample-ledger.md --partial --warn-only --out …/prefix-sample.lint.json`
  → **EXIT=1**，`total=4`，`violations=3`：
  - `E-999` `UNVERIFIED:` 前缀 → `UNVERIFIED_AS_PASS` ✓
  - `E-997` `未验证：` 前缀 → `UNVERIFIED_AS_PASS` ✓
  - `E-996` 方法列含 `UNVERIFIED` → `UNVERIFIED_AS_PASS` ✓
  - `E-998` 检查列**正文**提及「UNVERIFIED 不得标 PASS」但非前缀 → **不报** ✓
  （规则位置 `tools/ci/acceptance_lint.py:110-116`。）
- 真台账：`python -I tools/ci/acceptance_lint.py --out …/real-ledger.lint.json` → EXIT=1，
  `total=1200`，violations 仅 `EVIDENCE_REUSED` **144**（与批次 48 基线同值），
  `UNVERIFIED_AS_PASS` = **0**。
- E-061：直接解析 `acceptance_model.parse_ledger` → verdict `PASS`，check 开头
  `错因落账并发。JVM 用例用假端口…`，前缀判定 = `False`；lint 违规里 **无 E-061**。
  全台账 PASS 行中「检查列以 UNVERIFIED/未验证 前缀开头」= **0**。

## 4. 5 条原红复核（点名见 `docs/research/2026-11-08-batch-49-baseline.md` §4）

| # | 用例 | 所在类 / 本轮结果 |
|---|---|---|
| 1 | `in-window exam grows the daily budget and the queue` | ExamWindow 8/8 绿 |
| 2 | `in-window exam flips the daily selection toward the exam subject` | ExamWindow 8/8 绿 |
| 3 | `parallel exams take the steepest ramp for budget and rank by subject ramp` | ExamWindow 8/8 绿 |
| 4 | `weak knowledge node pulls its bound fresh question ahead before the exam` | ExamWindow 8/8 绿 |
| 5 | `service preview keeps the existing intake unschedulable count` | CapacityPreview 4/4 绿 |

**5/5 当前绿。** 注意：本 ask 只实测「当前绿」，没有回退 PR#13 语义去重演 5 例的
「先红」；红态仍只有基线 §4 的静态推断（夹具预算 < 1200s 默认值），未在本会话复现。

## 5. 未做 / 未验证

- 未跑 12 模块单测门 / 强门 / 全量回归 / 2h soak（本 ask 范围外；Gradle 已按串行使用）。
- fake DB 的 `takeLast(limit)` 无独立 JVM 断言；本 ask 用设备真 DAO 覆盖了生产路径。
- P1 模拟标定、P2 产品闭环、P3 台账冻结与 25 份复核均未开工（计划后续项）。
