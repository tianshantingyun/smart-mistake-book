# 批次 49 环后门禁复跑（post-fix gate，2026-11-08）

> 角色：门禁执行员（本 ask 只复跑门禁并记录 exit；未改任何源码/测试/台账/阈值/金样）。
> 执行时点：2026-11-08 06:44–06:52 +0800。
> HEAD：`0515cfb087e0d12afbdff2c739c2993dce5adf99`（main）。共享脏树 `git status --porcelain -uall` = **4911**。
> 串行纪律：Gradle 单客户端逐条执行；未运行 `connected*`，未调用 adb（`adb devices` 无设备）。
> 说明：本文件是门禁复跑的新增证据，不在 `EVIDENCE.sha256` 冻结清单内（该清单是 06:40 台账冻结时点快照）。

## 0. 结论与 exit

| 门 | 命令 | exit | 摘要 |
|---|---|---:|---|
| 强门（post-fix） | `bash build/run-batch49-strong-postfix.sh` | **0** | 7/7 任务 exit=0；`GRADLE_EXIT=0`；编译错误 `^e: ` = 0 |
| 修复涉及模块定向单测 | 见 §2 命令 | **1** | 2970 例 / 10 红 / 2 skip；10 红全部可归因 HEAD 既存（§2.3），本批新增面全绿（§2.4） |
| `acceptance_report` | `python -I tools/ci/acceptance_report.py --out build/b49-acceptance/report-postfix.json` | **1** | `state=FAIL`；counts 900/22/278/0；`violations=[]`；900/900 PASS 行有效复核 |
| `acceptance_report --require-complete` | 同脚本 `--require-complete` | **1** | `NOT_ACCEPTANCE_COMPLETE (UNVERIFIED=0 FAIL=22 BLOCKED=278)` + `REVIEW_CHECKS_SHARED 18 组` |

**不得读成**：强门 exit=0 只证明编译/打包，不执行测试；单测 exit=1 与验收 exit=1 是真实未收口，不是本次复跑的机械故障。

## 1. 强门（post-fix）：PASS，exit=0

命令（逐字）：

```bash
cd "D:/smart mistake book"
bash build/run-batch49-strong-postfix.sh
# SCRIPT_EXIT=0
```

- 脚本：`build/run-batch49-strong-postfix.sh`；任务集与 `build/run-batch49-strong.sh:14-21` 逐字一致，仅日志落点改为 `build/batch49-strong-postfix.log`。
- 日志：`build/batch49-strong-postfix.log`（**995 行**，sha256 `dcefb021eba60e6aaa310ed5c0d4be4f19e2b3bb710f16291e4066d28cf6c043`），起止 `06:44:54 → 06:46:26 +0800`，HEAD/脏树数/jdk 21.0.11 见日志头 `:1-4`。
- 逐任务（日志行号为 `build/batch49-strong-postfix.log` 行号）：

| # | 任务 | exit | 任务态 | BUILD 行 |
|---|---|---:|---|---|
| 1 | `:core:data:compileDebugAndroidTestKotlin --rerun` | 0 | 执行（`:50`，无 UP-TO-DATE） | `:55` `BUILD SUCCESSFUL in 40s` |
| 2 | `:core:database:compileDebugAndroidTestKotlin --rerun` | 0 | 执行（`:106`） | `:152` `BUILD SUCCESSFUL in 15s` |
| 3 | `:core:ui:compileDebugAndroidTestKotlin --rerun` | 0 | `NO-SOURCE`（`:198`） | `:200` `BUILD SUCCESSFUL in 2s` |
| 4 | `:core:export:compileDebugAndroidTestKotlin --rerun` | 0 | 执行（`:236`） | `:239` `BUILD SUCCESSFUL in 3s` |
| 5 | `:app:compileLocalFirstDebugAndroidTestKotlin --rerun` | 0 | 执行（`:454`） | `:463` `BUILD SUCCESSFUL in 8s` |
| 6 | `:app:compileStrictOfflineDebugAndroidTestKotlin --rerun` | 0 | 执行（`:684`） | `:693` `BUILD SUCCESSFUL in 15s` |
| 7 | `:app:assembleLocalFirstDebug --continue` | 0 | 226/226 `UP-TO-DATE`（`:987`、`:990` `BUILD SUCCESSFUL in 2s`） | `:993` |

- `grep -c "^e: " build/batch49-strong-postfix.log` = **0**（无编译错误）。
- APK：`app/build/outputs/apk/localFirst/debug/app-localFirst-debug.apk`，190 593 845 B，mtime `11-08 06:39`，sha256 `e19f1b22c58299db2ffb3be0df7284288d91a0ce7e42f0e9eb95eb06fd5fce51`。本次 assemble 为 UP-TO-DATE，即该 APK 与 `device/12-batch49-repair-artifacts.device.json` 绑定的 `e19f1b22…` 为同一构建。
- **不证明**：不执行任何测试；`core:ui` = NO-SOURCE；不证明运行时/设备行为。

## 2. 修复涉及模块定向单测：exit=1

### 2.1 范围与命令

修复环/本批 P0–P2 的代码足迹（`git status` + mtime 06:18–06:38）落在 `core:domain`、`core:data`、`core:database`（P0-2 `LearningDao`）、`feature:review`、`app`。按此跑 5 个模块任务：

```bash
cd "D:/smart mistake book"
GRADLE_USER_HOME="$PWD/.gradle" ./gradlew \
  :core:domain:testDebugUnitTest :core:data:testDebugUnitTest \
  :feature:review:testDebugUnitTest :app:testLocalFirstDebugUnitTest \
  --continue --rerun --console=plain > build/batch49-postfix-unit.log 2>&1
# GRADLE_EXIT=1（仅 :core:data 与 :app 两个测试任务失败）

GRADLE_USER_HOME="$PWD/.gradle" ./gradlew :core:database:testDebugUnitTest \
  --continue --rerun --console=plain > build/batch49-postfix-unit-core-database.log 2>&1
# GRADLE_EXIT=1（:core:database 失败，见 §2.2）
```

- 日志：`build/batch49-postfix-unit.log`（328 行，sha256 `36ebf3034453cf7455c5a5d1878361bf3cbf3e5857421c2959a8e8649e99353b`）；`build/batch49-postfix-unit-core-database.log`（100 行，sha256 `ce6c346b0639c5cd6ae7008ebc5d22a495b6056f92c87c49a09abf30da4e9385`）。
- 两份日志 `grep -c "^e: "` 均为 **0**（测试源码编译通过，失败是断言而非编译）。
- 结果由本轮 `build/test-results/**/TEST-*.xml` 现算（XML mtime 06:47–06:52，非旧结果）。

### 2.2 逐模块

| 模块 | tests | fail | err | skip | 结论 |
|---|---:|---:|---:|---:|---|
| `core:domain` | 741 | 0 | 0 | 1 | 绿（含 P1 模拟与 P1-10 上线门） |
| `core:data` | 1688 | **4** | 0 | 1 | 红（4 例与 06:30 基线逐名相同） |
| `feature:review` | 106 | 0 | 0 | 0 | 绿（含修复环新增 5 例决策消费） |
| `app:testLocalFirstDebugUnitTest` | 187 | **2** | 0 | 0 | 红（2 例为 HEAD 既存，见 §2.3） |
| `core:database` | 248 | **4** | 0 | 0 | 红（FTS/schema 4 例与基线逐名相同） |
| **合计** | **2970** | **10** | 0 | **2** | |

### 2.3 10 红逐名与归因（均为 HEAD 既存，非本批引入）

| # | 模块 | 用例 | 断言/异常 | 归因证据 |
|---|---|---|---|---|
| 1-3 | `core:data` | `OpenAiCompatibleModelGatewayTest.unverifiedEditCapabilityAndCancelledEditMakeZeroHttpCalls` / `changedEditSourceFingerprintIsRejectedBeforeHttp` / `mistakeEditMockServerReceivesOriginalImageAndRejectsInvalidOrTruncatedOutput` | `ModelEgressAuthorizationException`（`:124/:153/:166`） | 与 `repair/unit-gate-after.log` 同一组；`unit-gate.md §4` 已归因模型能力核验他线语义 |
| 4 | `core:data` | `RoomBackedStudyExperienceRepositoryTest.profileStrengthsDropForgottenAndPrerequisiteSuppressedNodes` | `AssertionError at :1423` | 同上；`unit-gate.md:100`（`StudyExperienceMappers` HEAD 即无 `knowledgePrerequisites` 赋值） |
| 5 | `core:database` | `KernelWave0SchemaContractTest.the study database version matches the latest exported schema` | 期望 73 vs 库头 75 | `unit-gate.md §3` 同一组；HEAD 既存 |
| 6-8 | `core:database` | `LibraryFtsCountPathQueryCopyContractTest.crossJoinSqlCopiesMatchTheDaoQueries`、`scale50kCrossJoinSqlCopiesMatchTheDaoQueries`、`LibraryFtsRankingQueryContractTest.rankingAndFilterLiteralsMatchTheFrozenReferenceCopy` | FTS 冻结副本与 live DAO 漂移 | `unit-gate.md §3` 同一组；`LearningDao.kt` 的 review_log 改动与 LibraryFts 无关 |
| 9 | `app` | `StorageRestoreFeedbackTest.genericRestoreFailureDoesNotEchoTheRawExceptionMessage` | `AssertionError: 不得回显 content:// URI`（`StorageRestoreFeedbackTest.kt:38`） | 测试文件 `git status` 空；实现 `git show HEAD:app/.../SecondaryScreens.kt:1453-1460` 非域异常分支仍为 `"恢复失败，已尝试保留原数据。${failure.message.orEmpty()}"`；工作树 `SecondaryScreens.kt` 仅两个 hunk（`@@ -132,0 +133,6 @@`、`@@ -508,0 +515,12 @@`，均为本批校准页），不触及该函数 → HEAD 即红 |
| 10 | `app` | `TutorSettlementNoticeTest.aVerdictlessTutorVisitSaysThereIsNoVerdictAndOffersRetry` | `AssertionError`（`TutorSettlementNoticeTest.kt:37`，期望 `"还没有形成判定"`） | 测试文件 `git status` 空；实现 `git show HEAD:app/.../SmartMistakeBookDestinations.kt:552-566` 文案为 `"尚未形成判定…"`，工作树对该文件无改动 → HEAD 即红 |

- 红 #9/#10 是 app 单测首次进入本批门禁后**新暴露的 HEAD 既存红**（`unit-gate.md §6.1` 已注明 `:app` 两个 flavor 单测此前未跑）；不是本批修复环引入，本 ask 不代改他线语义、不放宽断言。
- 红 #1-8 与 `repair/unit-gate-and-kd67-blockers.md §1` 的 9 红同源（本 ask 另跑 app 后多出 2 例；`feature:tutor` 未在本 ask 范围内）。

### 2.4 本批目标面正向证据（同一次 XML 现算，0 失败）

| 类别 | 用例类 | tests |
|---|---|---:|
| P0 原红点名 | `StudyReviewPlannerExamWindowTest` / `StudyReviewPlannerServiceCapacityPreviewTest` | 8/0、4/0 |
| P0 校准口径 | `PlannedReasonCalibrationTest` | 4/0 |
| P1 模拟 | `SimulatedLearnerTest` / `ReviewCalibrationExperimentTest` / `SimulationDeterminismContractTest` / `SchedulingEvaluationOnSimulationTest` | 6/0、1/0（1 skip）、2/0、1/0 |
| 校准页/权重 | `SchedulingCalibrationViewBuilderTest` / `ReviewPlannerScaleBenchmarkTest` / `ReviewScoringWeightsSeamTest` | 7/0、2/0、3/0 |
| KD-67 | `KnowledgeClarificationPresenterTest` / `KnowledgeConflictCardTest` / `KnowledgeConflictSessionStateTest` / `KnowledgeQuizItemStoreVerificationStatusTest` / `KnowledgeQuizItemVerificationUpgraderTest` | 14/0、6/0、10/0、2/0、15/0 |
| 冲突重整理/校准 | `ProjectionConflictReprojectTest` / `StudySchedulingCalibrationTest` | 8/0、8/0 |
| app 修复面 | `SchedulingCalibrationCopyTest` / `StudyProjectionIssueCopyTest` / `SchedulingRetentionSliderValueTest` | 7/0、3/0、4/0 |

## 3. `acceptance_report`：exit=1（真实未收口）

命令与输出（逐字）：

```bash
python -I tools/ci/acceptance_report.py --out build/b49-acceptance/report-postfix.json
# REPORT_EXIT=1
python -I tools/ci/acceptance_report.py --require-complete \
  --out build/b49-acceptance/report-require-complete-postfix.json
# REQUIRE_COMPLETE_EXIT=1
```

| 项 | 值 |
|---|---|
| ledger | `docs/acceptance/ledger.md`，sha256 `264425f5796f0597e7f3ed8d9b7fe359f112d774eae1e0ab7256c1e9ea9f6ba8`（与 `00-index.md` 冻结值一致） |
| counts | PASS **900** / FAIL **22** / BLOCKED **278** / UNVERIFIED **0**（total 1200） |
| state / ok | `FAIL` / `false` |
| `violations`（标准报告） | **[]**（证据链本身无违规） |
| 复核 | `pass_with_review=900`、`pass_with_invalid_review=0`、`pass_without_review=0`、`pass_with_row_specific_review=731`；`review_validation_errors=[]` |
| `--require-complete` violations | `NOT_ACCEPTANCE_COMPLETE (UNVERIFIED=0 FAIL=22 BLOCKED=278)`；`REVIEW_CHECKS_SHARED`（18 组） |
| head / dirty | `0515cfb0…` / 4911 |

- 输出：`build/b49-acceptance/report-postfix.json`（sha256 `e4a3d574a63336f3035c9f1f25ef5f6963d7aa1e4e229f66361c6fbdec6b6432`）、`report-require-complete-postfix.json`（sha256 `1ee780b63d8db7db4da011d9a6e4247b9117f761ff7821cc7a382615ab6fc670`）、两份 stdout 同目录。
- exit=1 的构成与 `repair/acceptance-gate-explanation.md` 一致：22 FAIL（F-057、H-018/H-062、13 条 J 发布、7 条 GATE）、278 BLOCKED；不是本次复跑新增。

## 4. 未运行 / 残余（不得读成通过）

1. **设备与 2h soak 未在本 ask 复跑**：本 ask 未用 adb、未安装 APK、未跑任何 instrumented/soak；设备证据仍以 `device/` 既有记录为准（x86_64 模拟器 API 34）。
2. **真库样本**：P1 标定/per-reason/G6 因果、校准页真库读数仍 **BLOCKED**（零真库样本）；模拟结论仍标 **model-based**。
3. **单测门判据未达成**：exit=1；10 红均为 HEAD 既存（§2.3），本 ask 不放宽断言、不代改他线语义，故不记 PASS。
4. **`feature:tutor` / 其余 7 模块**未在本 ask 重跑（不在修复足迹）；其最新状态引用 06:30 `repair/unit-gate-after.log`，不是本次执行结果。
5. **强门不执行测试**：`core:ui` NO-SOURCE；APK assemble 为 UP-TO-DATE（输入在 06:39 构建后未变）。
6. **共享脏树**：4911 条（`-uall`，含未跟踪证据）；本 ask 只新增 `build/` 日志与本节证据文件，未改源码/台账/复核/哈希清单。

## 5. 复跑命令

```bash
cd "D:/smart mistake book"
bash build/run-batch49-strong-postfix.sh; echo "SCRIPT_EXIT=$?"

GRADLE_USER_HOME="$PWD/.gradle" ./gradlew \
  :core:domain:testDebugUnitTest :core:data:testDebugUnitTest \
  :feature:review:testDebugUnitTest :app:testLocalFirstDebugUnitTest \
  --continue --rerun --console=plain > build/batch49-postfix-unit.log 2>&1; echo "GRADLE_EXIT=$?"
GRADLE_USER_HOME="$PWD/.gradle" ./gradlew :core:database:testDebugUnitTest \
  --continue --rerun --console=plain > build/batch49-postfix-unit-core-database.log 2>&1; echo "GRADLE_EXIT=$?"

python -I tools/ci/acceptance_report.py --out build/b49-acceptance/report-postfix.json; echo "REPORT_EXIT=$?"
python -I tools/ci/acceptance_report.py --require-complete \
  --out build/b49-acceptance/report-require-complete-postfix.json; echo "REQUIRE_COMPLETE_EXIT=$?"
```
