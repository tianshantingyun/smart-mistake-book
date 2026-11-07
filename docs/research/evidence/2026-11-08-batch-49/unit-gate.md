# 批次 49 单测门：12 模块 `testDebugUnitTest --continue`

| 字段 | 值 |
|---|---|
| evidence-id | batch49-unit-gate |
| batch | 2026-11-08-batch-49 |
| step | 计划 §P3-16「定向/全量/强门」中的 12 模块单测门；本 ask 只做单测门 |
| verdict | **FAIL**：exit=1；**9 红 / 4161 用例 / 2 skipped**；9 红全部为 **HEAD 既存的他线语义红**，0 条可归因于批次 49 工作树改动（逐条见 §3–§4） |
| executed-at | 主跑 2026-11-08T01:32:26–01:34:26 +0800；补跑 01:40:23–01:40:52 +0800 |
| head | `ba7b5624ea0c2bc3f09dcee247f5852e4f428122`（主跑日志头登记） |
| worktree | 主跑起点 `git status --porcelain | wc -l` = **642**（共享脏树，含他线在飞） |
| operator | 单测门子代理（无并发代理；主跑期间仅一个 Gradle 客户端，daemon 空闲健康检查） |
| device-serial | n/a（JVM） |
| raw-log | `build/batch49-unit.log`（343 行，含命令头/开始结束时间/`GRADLE_EXIT=1`）；补跑 `build/batch49-unit-rerun.log` |
| sanitized | 是（未摘录任何密钥；`local.properties` 的明文 provider key 只登记位置，见 §6） |

## 1. 命令与口径

12 模块 = `settings.gradle.kts` 中除 `:app`、`:benchmark` 外的全部模块：`core:model`、`core:domain`、`core:database`、`core:data`、`core:export`、`core:ui`、`core:markdown`、`feature:capture`、`feature:review`、`feature:tutor`、`feature:library`、`feature:profile`。依据：`core:model`/`core:domain` 用 `build.gradle.kts` 注册同名任务委托给 `test`（两模块 build 文件 KDoc「档1 门禁统一按 Android 风格任务名 `testDebugUnitTest` 点名各模块单测」）；`:app` 只有 `testLocalFirstDebugUnitTest`/`testStrictOfflineDebugUnitTest`（`app/build.gradle.kts:50-51` flavor），`:benchmark` 为 `com.android.test`，均不在 12 模块口径内。此口径与批次 46 门日志实际任务集一致（`build/batch46-unit.log` 恰含上述 12 个 `testDebugUnitTest`）。

主跑命令（日志头逐字记录）：

```text
./gradlew :core:model:testDebugUnitTest :core:domain:testDebugUnitTest \
  :core:database:testDebugUnitTest :core:data:testDebugUnitTest \
  :core:export:testDebugUnitTest :core:ui:testDebugUnitTest \
  :core:markdown:testDebugUnitTest :feature:capture:testDebugUnitTest \
  :feature:review:testDebugUnitTest :feature:tutor:testDebugUnitTest \
  :feature:library:testDebugUnitTest :feature:profile:testDebugUnitTest \
  --continue --console=plain
```

- 环境：`GRADLE_USER_HOME="D:/smart mistake book/.gradle"`；JDK 21.0.11（Temurin）；Gradle 9.6.1（wrapper）；`local.properties` → `sdk.dir=C:\Android\Sdk`。
- **未使用 `tools/run-gradle.ps1`**：该脚本固定调用 `S:\.toolchains\gradle\gradle-9.6.1\bin\gradle.bat`（`tools/android-env.ps1:187`），而本 checkout 无 `.toolchains/`（PowerShell `Test-Path` = `False`；该目录 gitignored，`.gitignore:10`）。按 `DEVELOPMENT.md:7`「Gradle wrapper is committed; run `gradlew`」改用 committed wrapper。
- 设备纪律：未运行任何 connected/设备 Gradle 任务；未运行 `connectedLocalFirstDebugAndroidTest`。

## 2. 结果总表（逐模块）

主跑 `BUILD FAILED in 1m 59s`；`158 actionable tasks: 15 executed, 143 up-to-date`；`FAILURE: Build completed with 3 failures`（3 个测试任务）。

| 模块 | 任务态 | tests | fail | err | skip | 结果 XML 时间戳（UTC） |
|---|---|---|---|---|---|---|
| `core:model` | 主跑 UP-TO-DATE → 补跑执行 | 655 | 0 | 0 | 0 | 17:40:50（补跑） |
| `core:domain` | 执行 | 740 | 0 | 0 | 1 | 17:32:32–17:33:00 |
| `core:database` | **FAILED** | 248 | **4** | 0 | 0 | 17:33:07–17:33:37 |
| `core:data` | **FAILED** | 1690 | **4** | 0 | 1 | 17:32:33–17:34:23 |
| `core:export` | 执行 | 174 | 0 | 0 | 0 | 17:32:52 |
| `core:ui` | 执行 | 56 | 0 | 0 | 0 | 17:32:46 |
| `core:markdown` | NO-SOURCE（0 个测试源） | 0 | 0 | 0 | 0 | — |
| `feature:capture` | 执行 | 148 | 0 | 0 | 0 | 17:32:50 |
| `feature:review` | 主跑 UP-TO-DATE → 补跑执行 | 101 | 0 | 0 | 0 | 17:40:27（补跑） |
| `feature:tutor` | **FAILED** | 261 | **1** | 0 | 0 | 17:32:32–17:32:34 |
| `feature:library` | 执行 | 82 | 0 | 0 | 0 | 17:32:49 |
| `feature:profile` | 执行 | 6 | 0 | 0 | 0 | 17:32:39 |
| **合计** | | **4161** | **9** | 0 | **2** | |

- 主跑中 `:core:model:testDebugUnitTest`、`:feature:review:testDebugUnitTest` 为 `UP-TO-DATE`（复用 23:03/00:44 的结果，非本次执行）。为满足「本轮执行」的证据标准，补跑：
  - `./gradlew :core:model:test :feature:review:testDebugUnitTest --rerun --console=plain` → `BUILD SUCCESSFUL in 4s`、`GRADLE_EXIT=0`；`feature:review` 101/0 为新鲜结果；`:core:model:test` 仍报 `UP-TO-DATE`（`--rerun` 未生效于该任务）。
  - 追加 `./gradlew :core:model:cleanTest :core:model:test --console=plain` → `BUILD SUCCESSFUL in 4s`、`GRADLE_EXIT_MODEL=0`；655/0，XML 时间戳 17:40:50 新鲜。
- 每个模块的 `tests/failures/errors/skipped` 均由本轮解析 `build/test-results/**/TEST-*.xml` 得出（529 份 XML）。

## 3. 9 条失败逐名（日志原文 + 断言消息）

日志行号指 `build/batch49-unit.log`。

**`feature:tutor`（1）**

| 用例 | 日志行 | 失败 |
|---|---|---|
| `TutorExportSheetTest.theWordFormatNoticeStatesItsRealLimitsAndPdfHasNone` | :223 | `java.lang.AssertionError at TutorExportSheetTest.kt:204`（`assertTrue(notice.contains("作答空白区"))`） |

**`core:database`（4）**

| 用例 | 日志行 | 失败消息（摘要） |
|---|---|---|
| `KernelWave0SchemaContractTest.the study database version matches the latest exported schema` | :264 | `expected:<73> but was:<75>`（测试期望字面量仍停在 73，库头已是 75） |
| `LibraryFtsCountPathQueryCopyContractTest.crossJoinSqlCopiesMatchTheDaoQueries` | :267 | `ComparisonFailure`：冻结副本比 live DAO 多 `(:sectionId = '__unclassified__' AND COALESCE(catalog.chapter_labels, '') = '')` |
| `LibraryFtsCountPathQueryCopyContractTest.scale50kCrossJoinSqlCopiesMatchTheDaoQueries` | :270 | 同上（50k 档副本） |
| `LibraryFtsRankingQueryContractTest.rankingAndFilterLiteralsMatchTheFrozenReferenceCopy` | :273 | `ComparisonFailure`：筛选片段少 `__unclassified__ AND COALESCE(catalog.chapter_labels, '') = ''` 一段 |

**`core:data`（4）**

| 用例 | 日志行 | 失败消息（摘要） |
|---|---|---|
| `OpenAiCompatibleModelGatewayTest.unverifiedEditCapabilityAndCancelledEditMakeZeroHttpCalls` | :282 | `ModelEgressAuthorizationException: 当前模型尚未验证这项能力，请到设置重新测试`（`OpenAiCompatibleModelGatewayTest.kt:166`） |
| `OpenAiCompatibleModelGatewayTest.changedEditSourceFingerprintIsRejectedBeforeHttp` | :285 | 同上（`:153`） |
| `OpenAiCompatibleModelGatewayTest.mistakeEditMockServerReceivesOriginalImageAndRejectsInvalidOrTruncatedOutput` | :288 | 同上（`:124`） |
| `RoomBackedStudyExperienceRepositoryTest.profileStrengthsDropForgottenAndPrerequisiteSuppressedNodes` | :291 | `expected:<[knowledge:m1:math.read-monotonicity-from-graph]> but was:<null>` |

## 4. 归因：9 红均为 HEAD 既存 / 他线语义，0 条可归批次 49

独立佐证：批次 48 记录 §9.2 已把同 4 组红登记为「他线语义变更与批次 41/42 断言的冲突，非本批引入」（`docs/research/2026-11-07-batch-48-record.md:515-522`：容量族 5（本批已修绿，见 §5）、`KernelWave0` 1、`LibraryFts` 3、`RoomBackedStudyExperience` 1、`Gateway` 3、`TutorExportSheet` 1）。本轮逐条核当前树：

| 组 | 当前树静态事实（本会话实读） | 结论 |
|---|---|---|
| `TutorExportSheetTest`（1） | 测试与实现 `git status` **均空**；最后改动 `ea4348f9 2026-11-06 23:31 feat(ui): 收口全局界面与精简文案…`。实现现文案 `TutorExportSheet.kt:242` =「Word 以文字说明代替图形，**不含作答区**。打印请用 Word 打开。」，不含测试断言要求的「作答空白区」（`TutorExportSheetTest.kt:204`） | HEAD 既存；他线文案精简（PR#13）未同步该断言 |
| `KernelWave0`（1） | 测试文件 `git status` 空；`74.json`/`75.json` 已入库，`git log` 指向 `ea4348f9`/`4d629d9d`（PR#13 合并线） | HEAD 既存；schema 73→74→75 的 bump 未同步测试字面量 |
| `LibraryFts`（3） | `LibraryFtsSearchDao.kt` `git status` **空**（最后 `ea4348f9`）；两份 androidTest 冻结副本 `git status` **空**（最后 `cf448a97`） | 漂移在 HEAD 固化；非本批（本批只改 `LearningDao.kt` 的 review_log 查询，与 LibraryFts 无关） |
| `Gateway`（3） | 抛点 `core/model/.../ModelEgress.kt:606`；`ModelEgress.kt` 与 `OpenAiCompatibleModelGatewayTest.kt` `git status` **均空**（最后 `4d629d9d`） | HEAD 既存；模型能力核验语义与他线测试夹具冲突 |
| `RoomBackedStudyExperience`（1） | 断言读 `profile.knowledgePrerequisites[...]`；`StudyExperienceMappers.kt:204` 构造 `StudyProfileOverview(` 但全文件 **0** 处 `knowledgePrerequisites` 赋值（`git show HEAD:…StudyExperienceMappers.kt | grep -c` = **0**）；测试函数在 HEAD 即存在（HEAD `:1423`）；工作树对该测试文件的 diff 仅 `:3472-3490` 的 fake 两处（`takeLast(limit)` + `plannedReason`），不涉及失败用例 | HEAD 既存；与本批「fake plannedReason」改动无关 |

因此本门的失败不改变「批次 49 改动是否引入回归」的判断：**本批工作树改动未新增测试红**。但计划 §验证要求「12 模块单测门+强门 exit=0」，该**门判据本身未达成**，verdict 记 **FAIL**，不因归因给他线而记 PASS。按批次 48 出口，这 4 组语义重锚属他线会话或获授权批次；本批不代改断言。

## 5. 正向证据：本批目标面在本门内的绿

- **5 条原红点名全绿**（基线 `docs/research/2026-11-08-batch-49-baseline.md:120-124` 列名）：
  - `StudyReviewPlannerExamWindowTest` 8/0（含 `in-window exam grows the daily budget and the queue`、`in-window exam flips the daily selection toward the exam subject`、`parallel exams take the steepest ramp for budget and rank by subject ramp`、`weak knowledge node pulls its bound fresh question ahead before the exam`）；
  - `StudyReviewPlannerServiceCapacityPreviewTest` 4/0（含 `service preview keeps the existing intake unschedulable count`）；
  - 且 P0-1 新增两例同套件绿：`settings store budget wins over the constructor fallback`、`null settings store falls back to the constructor budget`。
- **P0-3 校准口径**：`PlannedReasonCalibrationTest` 4/0。
- **P1 模拟**：`ReviewCalibrationExperimentTest` 1/0（`core:domain` 全场 740/0）。
- **P2 产品闭环**：`KnowledgeQuizItemStoreVerificationStatusTest` 2/0、`ProjectionConflictReprojectTest` 8/0、`KnowledgeConflictCardTest` 5/0、`KnowledgeConflictSessionStateTest` 6/0、`KnowledgeClarificationPresenterTest` 14/0（`feature:review` 全场 101/0）。
- **P0-2 review_log 最近窗口** 的「3 行 + limit=2」断言已在工作树落为**设备用例** `core/database/src/androidTest/.../MasterySchedulingMigrationInstrumentedTest.kt:126-145`；**本 JVM 门不覆盖，NOT RUN**。

## 6. 未验证 / 缺口（不得写成通过）

1. **`:app` 两个 flavor 单测未跑**：`testLocalFirstDebugUnitTest` / `testStrictOfflineDebugUnitTest` 不在 12 模块口径；本批 app 侧新增/改动（`SchedulingCalibrationCopyTest`、`StudyProjectionIssueCopyTest`、`ReviewReminderCoordinatorTest` 等）无本门证据。
2. **设备/仪器化未跑**：含 §5 的 `limit=2` 设备钉值、KD-67 确认卡、校准可见页、冲突处置、2h soak —— 本 ask 只做 JVM 单测门，且计划禁令 `connectedLocalFirstDebugAndroidTest`。
3. **强门未跑**（非本 ask）；`acceptance_report --require-complete` 未跑。
4. **`core:markdown` NO-SOURCE**：该模块 0 个 JVM 测试源，不能记为「12 模块全有测试执行」。
5. 主跑 `core:model`/`feature:review` 曾 UP-TO-DATE；已补跑（均 0 失败），但补跑命令与主跑不同（`--rerun` / `cleanTest+test`），不是同一条命令的 12 模块统一运行。
6. **秘密卫生**：`local.properties` 含明文 `SMB_REAL_PROVIDER_KEY`（gitignored，`.gitignore:14`）；本文件与日志均未摘录，建议轮换并确认从未入库。
7. 门禁通过判据未达成：**exit=1**。本文件不把 9 红「归因后」改记为通过。

## 7. 可重跑命令

```bash
cd "D:/smart mistake book"
export GRADLE_USER_HOME="D:/smart mistake book/.gradle"
./gradlew :core:model:testDebugUnitTest :core:domain:testDebugUnitTest \
  :core:database:testDebugUnitTest :core:data:testDebugUnitTest \
  :core:export:testDebugUnitTest :core:ui:testDebugUnitTest \
  :core:markdown:testDebugUnitTest :feature:capture:testDebugUnitTest \
  :feature:review:testDebugUnitTest :feature:tutor:testDebugUnitTest \
  :feature:library:testDebugUnitTest :feature:profile:testDebugUnitTest \
  --continue --console=plain > build/batch49-unit.log 2>&1; echo "GRADLE_EXIT=$?"
```

失败清单复算：解析 `build/test-results/{testDebugUnitTest,test}/TEST-*.xml` 中带 `<failure>`/`<error>` 的 `testcase`（本轮 529 份 XML，9 条）。
