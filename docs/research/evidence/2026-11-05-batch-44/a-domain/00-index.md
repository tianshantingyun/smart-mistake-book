# A 域证据索引（批次 44，2026-11-04 实跑）

> 本文件是导航与口径说明，**不是证据**；台账行只引用同目录下的
> `*.xml` / `*.device.json` / `*.log`。所有设备证据绑定
> `serial + boot_id + apk_sha256 + test_apk_sha256`。

## 1. 身份

| 项 | 值 |
|---|---|
| 源快照 | HEAD `26f2327b0f88d3cad7e076b967b62874e36ca07d` + 工作树脏（共享树，未提交） |
| app APK | `app-localFirst-debug.apk` sha256 `84cae864c42f7e26203d21f3901c06a4d0f02f2dd3b012bb63727ef32e5dab5b`（`./gradlew :app:assembleLocalFirstDebug --offline` EXIT=0） |
| app androidTest APK | 运行 r1–r4/r6–r10 时为 `9ede778d…`；r5 在权限拆分与注释补完后为 `aab6af01…`（只改了 `MistakeExportNotificationsInstrumentedTest`） |
| core:data androidTest APK | `4ef6575b017b200c06b436051f600334b217a9cc53718a1510136bcafa190056` |
| 设备 | `emulator-5584`，API 34，x86_64，PAGE 4096，boot_id `5a34f877-606b-4e74-b7b8-84f6398faf9d` |
| 记录时间 | 2026-11-04 08:39–10:29 UTC（本机 16:39–18:29） |

## 2. 设备用例（recorder 逐例 XML）

| label | tests | fail/err/skip | verdict | test APK |
|---|---:|---|---|---|
| r1-empty | 1 | 0/0/0 | PASS | 9ede778ded7b |
| r1-manual | 2 | 0/0/0 | PASS | 9ede778ded7b |
| r1-shared | 6 | 0/0/0 | PASS | 9ede778ded7b |
| r2-tutor | 7 | 0/0/0 | PASS | 9ede778ded7b |
| r3-empty | 3 | 0/0/0 | PASS | 9ede778ded7b |
| r3-selfreport | 8 | 0/0/0 | PASS | 9ede778ded7b |
| r4-detail | 2 | 0/0/0 | PASS | 9ede778ded7b |
| r4-revision-edit | 2 | 0/0/0 | PASS | 4ef6575b017b |
| r5-export | 4 | 0/0/0 | PASS | 50cc131a745b |
| r5-permission-denied | 1 | 0/0/0 | PASS | 50cc131a745b |
| r5-bg | 3 | 0/0/0 | PASS | 9ede778ded7b |
| r6-backup | 11 | 0/0/0 | PASS | 9ede778ded7b |
| r6-backup-scale | 1 | 0/0/0（evaluated=0） | FAIL（1800 s 超时，EXIT=1） | 4ef6575b017b |
| r7-knowledge | 4 | 0/0/0 | PASS | 9ede778ded7b |
| r8-mastery | 2 | 0/0/0 | PASS | 9ede778ded7b |
| r9-reminder | 2 | 0/0/0 | PASS | 9ede778ded7b |
| r9-reminder-red | 2 | 1/0/0 | FAIL（红相保留） | 00bae34128b2 |
| r10-settings | 10 | 0/0/0 | PASS | 9ede778ded7b |
| r10-recovery | 4 | 0/0/0 | PASS | 9ede778ded7b |
| r10-native | 2 | 0/0/0 | PASS | 9ede778ded7b |
| seed-detail-fixture | 1 | 0/0/0 | PASS | 50cc131a745b |

- `r9-reminder-red`：产品文案已统一为「点击开始」，测试停在基线「点这里开始」，
  设备验收第一次跑红；测试断言修正后 `r9-reminder` 2/2 绿。红相文件保留，不洗掉。
- `r6-backup-scale`：365 MiB 全量/子集备份恢复 1800 s 未完成（`TIMEOUT after
  1800s`、`EXIT=1`），因此 A-050 记 `UNVERIFIED`，不记“大库通过”。

## 3. JVM 用例（`jvm-*.xml`）

| 模块 | 类 | tests | 结果 |
|---|---|---:|---|
| app | ReviewReminderCoordinatorTest | 7 | 0/0/0 |
| app | ReviewReminderTimeCalculatorTest | 3 | 0/0/0 |
| app | ExportPdfWorkerTest | 14 | 0/0/0 |
| app | BatchImportDriverTest | 2 | 0/0/0 |
| app | LearningMasteryEmptyReasonTest | 4 | 0/0/0 |
| app | LearningMasteryRecentActivityTest | 1 | 0/0/0 |
| app | MasteryBandConsistencyTest | 5 | 0/0/0 |
| app | LibraryExportNoticeTest | 3 | 0/0/0 |
| core:data | BackupWriteGateRestoreLifecycleTest | 11 | 0/0/0 |
| core:data | BackupTempArtifactCleanupTest | 2 | 0/0/0 |
| core:data | RestoreSoftReferenceRecheckTest | 7 | 0/0/0 |
| core:data | RoomMistakeDetailRepositoryTest | 10 | 0/0/0 |
| core:data | SmbkSelectionPlanTest | 12 | 0/0/0 |
| core:data | V4PackFixtureTest | 8 | 0/0/0 |
| core:data | SixC2RulingInvariantTest | 3 | 0/0/0 |
| core:export | MistakePdfEligibilityTest | 17 | 0/0/0 |
| core:export | MistakePdfPaperEditTest | 19 | 0/0/0 |
| core:export | MistakePdfBatchEligibilityTest | 8 | 0/0/0 |
| core:export | MistakeExportJobRunnerTest | 21 | 0/0/0 |

命令原文见 `jvm-*.cmd.log`；Gradle 全量输出见 `jvm-*.log`（含 `SKIPPED` 任务行，
故不作 `cmd:` 证据，只作辅助）。

## 4. 设备状态探针（34 PASS）

- 10 旅程 × `rotate` / `home` / `kill`（J1–J10）；
- J1/J2/J3/J7 的 `clear` 空态；
- J9 的 `permission`（撤销 `POST_NOTIFICATIONS` 后页面仍可达，结束恢复授权）。

每个 `<label>.device.json` 记录 serial/boot_id/apk sha/observations/failures；
`<label>.log` 只有 `EXIT=0`。探针只判入口与状态切换，不替代语义断言。

## 5. 门禁输出（对正式台账）

| 门 | 命令 | 结果 |
|---|---|---|
| 计数 | `python -I tools/ci/acceptance_init.py --check` | exit 0（1200 行） |
| 报告 | `python -I tools/ci/acceptance_report.py --ledger docs/acceptance/ledger.md` | **exit 0**，violations 0 |
| lint | `python -I tools/ci/acceptance_lint.py --ledger docs/acceptance/ledger.md` | **exit 0**，violations 0 |
| trace | `python -I tools/ci/trace_requirements.py --ledger docs/acceptance/ledger.md` | 全局 exit 1（他域占位）；**A 域 gaps = 0** |

A 域计数：**66 PASS / 23 UNVERIFIED / 1 BLOCKED = 90**。

## 6. 未验证与阻塞（不得写成通过）

- **A-010 讲题主路径 BLOCKED**：无真实 Provider/凭据；已有注入模型 UI 用例。
- **A-050 大库备份恢复 UNVERIFIED**：365 MiB 1800 s 超时，未取得判词。
- **A-005/A-014/A-023/A-032/A-059/A-068/A-077/A-086**：A 域无大样本设备用例；
  E/F 域另有覆盖，但不得互相外推。
- **A-031/A-033/A-051/A-060/A-069/A-087**：该旅程无运行时权限可撤销，或空态
  不适用于该入口。
- **A-058/A-065/A-066**：知识点空态文案、掌握度失败/中断恢复无断言落点。
- 探针不证明滚动位置、草稿字节级一致或屏幕阅读器语感。

## 7. 工具与回归

- `tools/ci/am_instrument_recorder.py`：把 `am instrument` 逐例状态码转 JUnit XML；
  失败/错误/跳过一律 `EXIT=1`。单测 `tools/tests/test_am_instrument_recorder.py` 5 例。
- `tools/ci/a_journey_probe.py`：rotate/home/kill/clear/permission 探针；
  单测 `tools/tests/test_a_journey_probe.py` 4 例。
- 两工具单测本轮实跑：`python -I -m pytest tools/tests/test_am_instrument_recorder.py
  tools/tests/test_a_journey_probe.py -q` → **9 passed**。
- 对抗尝试见 `adversarial-attempts.md`。
