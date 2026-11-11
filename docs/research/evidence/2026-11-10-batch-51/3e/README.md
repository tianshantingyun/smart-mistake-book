# 3E · KD-46 每日累计配额（批次 51 · 零 schema 方案 A）证据

> 轨：批次 51 · G2（第 17–18 项）。性质：实现 + JVM 证据；**设备复验（第 19 项）未跑**。
> 本文件只写本轨在当前工作树实读/实跑的事实。行号 = 改动后当前工作树行号。

## 0. 一句话

把 3E 第二档（全库薄弱点扫描）的配额口径从「**每次排计划** ≤10」修为「**每个学习日累计**
≤10 个新增」：当日已花费名额从既有作答账本 `learner_chat_evidence`（`source_kind='KNOWLEDGE_QUIZ'`
且 `created_at ∈ [学习日 04:00, +24h)`）去重派生，**零 schema**（无新表/新列/迁移/备份清单/机器块），
当日已复习的扫描点回放但不占名额，账本读失败 fail-closed（remaining=0）。

**没有**声明 3E 无条件 PASS：本轨交付时设备侧实跑未运行，P95 查询成本未量。后续主会话
已跑 `device/26-instrument-3e-rerun.log` → **OK (4 tests)**（覆盖全库捞回 / F17 每日名额 /
F8 预算先绑 / 同日累计 / 无考试日指纹），但台账 1200 行没有 3E 门行，**批次级无条件 PASS
仍未签字**（行级投影为 E-075 PASS + A-023 BLOCKED）。见 §6。

## 1. 语义（实现即此，不是设计稿）

| 项 | 取值 / 规则 | 代码 |
|---|---|---|
| 日键 | 学习日（本地 04:00 日界，`StudyDayMath` 单源），与今日错题计划同一天键 | `StudyReviewPlannerService.kt:790-798`（`startOfDay`）、`:806`（`PlanningContext.studyDayStartEpochMillis`） |
| 窗口 | `[studyDayStart, studyDayStart + DAY_MILLIS)`，半开 | `StudyReviewPlannerService.kt:819-827` |
| 已作答集合 | `DISTINCT knowledge_node_id` where `learner_id=? AND source_kind='KNOWLEDGE_QUIZ' AND created_at ∈ 窗口`；**不过滤** `rejected_reason`（被拒观察行也算"这次作答发生了"） | `ChatEvidenceDao.kt:190-215`、`ModelTaskDatabasePort.kt:221-237`、`RoomStudyDatabase.kt:1883-1892` |
| 已花费 | `quizzedToday − todayCandidateIds`（今日第一档可出题节点不占扫描名额） | `StudyReviewPlannerService.kt:944` |
| 剩余名额 | `max(0, FULL_LIBRARY_SCAN_DAILY_CAP − |spentToday|)`；读失败 → 0 | `StudyReviewPlannerService.kt:945-948` |
| 新增 | `scanCandidates − spentToday`，只受剩余名额约束（`.take(cap)` 语义不变） | `StudyReviewPlannerService.kt:952`、`KnowledgeReviewQueue.kt:213-224` |
| 回放 | `scanCandidates ∩ spentToday`，进并集但不占名额 | `StudyReviewPlannerService.kt:951`、`KnowledgeReviewQueue.kt:201`、`:219` |
| 失败关死 | 账本读抛错 → `Log.w` + `remaining=0`（本次不取新增），今日第一档照常；`CancellationException` 原样上抛 | `StudyReviewPlannerService.kt:933-948` |
| 并发/幂等 | 只读 + 纯函数稳定排序（score 降序、node id 升序）；`requestId` 仍只校验非空、不参与去重 | `KnowledgeReviewQueue.kt:213-221`；`StudyReviewPlannerService.kt:832` |
| 版本 | 队列仍不落库、不进 `plan_fingerprint`、无 bump | `KnowledgeReviewFullLibraryScan.kt:56` 起 KDoc |

**不变量（如实）**：承诺"每日**实际复习**（有 `KNOWLEDGE_QUIZ` 落账）的扫描点 ≤10（无重排时严格）"，
**不**承诺"每日下发/计划 ≤10"。残留见 §5。

## 2. 零 schema 证明

- 新增的是一条第 **只读** SQL 查询（`ChatEvidenceDao.kt:190-215`）+ 端口方法 + Room 实现；
  **没有**新表、新列、migration、`76.json`、备份清单、§9.14 机器块、`trimOrder`、`CLOSURE_VERSION`。
- `git status --porcelain -- core/database/schemas` → 空（75.json 未动）。
- 6C-2 备份 mask 9/13/15 与 `SmbkSelectionPlan` 的既有裁定**未触碰**（本轨对该文件无 Write/Edit；
  工作树里它的 `M` 是他线/既有改动）。
- `plan_fingerprint` / `plannerVersion` 未改（无算法版本 bump；队列本就非持久化）。

## 3. 红 → 绿（先红后绿，未放宽断言）

### 3.1 红（改前实跑）

命令（与 §4-01 相同）：

```
./gradlew :core:data:testDebugUnitTest --tests "com.tingyun.smartmistakebook.core.data.study.RoomBackedStudyExperienceRepositoryTest.scanQuota*" --console=plain
EXIT=1
```

7 例中 **5 红 / 2 绿**（2 绿是守卫：今日第一档不占名额、只读确定性——修复前也应为绿）。红的断言原文
（当时从 JUnit XML 逐字取出；当次日志文件未持久化，可用本表最后一行的方法复现）：

| 用例 | 断言原文（截断） |
|---|---|
| `scanQuotaIsDailyCumulativeAcrossPlanCallsInTheSameStudyDay` | `同日第二次排计划不得再取满 10：first=[scan-01…scan-10] second=[…]` |
| `scanQuotaResetsAtTheStudyDayBoundary` | `今日窗口起点（含）的作答必须计入已花费：[scan-01…scan-10]` |
| `scanQuotaSpendsOnlyTheRemainingSlotsWithinTheSameDay` | `已花费 4 → 本次只取 6 个新增 expected:<6> but was:<10>` |
| `scanQuotaCountsRejectedObservationRowsAsSpent` | `被拒观察行也占名额：已花费 4 → 只取 6 个新增，实际 [scan-01…]` |
| `scanQuotaFailsClosedWhenTheEvidenceReadFails` | `账本读失败 → 未知花费按不可再取处理，今日第一档照常 expected:<[knowledge:today-tier]> but was:<[scan-01…]>` |

复现方法：把 `StudyReviewPlannerService.kt:979-981` 三行改回
`fullLibraryScanCandidates = scanCandidates` / `fullLibraryScanDailyCap = FULL_LIBRARY_SCAN_DAILY_CAP` /
（删 `fullLibraryScanReplayCandidates`）即可回到"每次排计划 ≤10"的旧行为。

### 3.2 绿（改后实跑，见 §4）

- 定向 7 例全绿（`logs/05-test-summary.txt`；JUnit XML：`logs/01b-roombacked-class-from-full-run.junit.xml`）。
- 域层新增 1 例全绿：`KnowledgeReviewQueueTest#replayCandidatesJoinThePoolWithoutConsumingTheRemainingCap`
  （`logs/02-jvm-domain.junit.xml`）。

## 4. 门检查实跑表（全部本轨实跑）

| # | 命令 | exit | 结果 |
|---|---|---|---|
| 01 | `./gradlew :core:data:testDebugUnitTest --tests "...RoomBackedStudyExperienceRepositoryTest.scanQuota*" --console=plain` | **0** | BUILD SUCCESSFUL；7 例 PASS（见 summary） |
| 02 | `./gradlew :core:domain:test --rerun --console=plain` | **0** | BUILD SUCCESSFUL；全模块域层测试通过（含新增回放用例） |
| 03 | `./gradlew :core:data:testDebugUnitTest --rerun --console=plain` | **1** | `1709 tests completed, 4 failed, 1 skipped`；4 红 = 批次 49/50 既有基线（§4.1），**无新增红**；本轨 7 例在 1709 内全绿 |
| 04 | `./gradlew :core:data:compileDebugAndroidTestKotlin --rerun-tasks --console=plain` | **0** | 设备用例源码**全量重编译**通过（28 tasks executed）；**只编译、未上设备** |

日志：`logs/01-jvm-quota-targeted.log`、`logs/02-jvm-domain-full.log`、`logs/03-jvm-data-full.log`、
`logs/04-androidtest-compile.log`；逐例结果 `logs/05-test-summary.txt`；diff `code-diff.patch`。

### 4.1 03 的 4 红 = 既有基线（逐名同组）

| 失败 | 本轨证据 |
|---|---|
| `OpenAiCompatibleModelGatewayTest` ×3（`ModelEgressAuthorizationException`） | 批次 49 `docs/research/evidence/2026-11-08-batch-49/unit-gate.md:85-87`；批次 50 `docs/research/evidence/2026-11-09-batch-50/postfix-gate.md:118`、`docs/research/2026-11-09-batch-50-record.md:593`（"4 红与批次 49 基线逐字同组…**无新增红**"） |
| `RoomBackedStudyExperienceRepositoryTest.profileStrengthsDropForgottenAndPrerequisiteSuppressedNodes`（`expected:<[knowledge:m1:math.read-monotonicity-from-graph]> but was:<null>`） | 批次 49 `unit-gate.md:88` 逐字同断言；批次 50 `postfix-gate.md:119`、`evidence/2026-11-09-batch-50/3e/summary.json:94`（"既有失败…attributed_to_3e: false"） |

本轨未修这 4 条、也未记它们通过（属于他线/既有缺陷，超出本阶段范围）。

## 5. 残留风险（不粉饰）

- **R（重排）**：当日先被扫描下发、后被重排拉进第一档的点会被 `− todayCandidateIds` 减掉 ⇒ 上界
  `10 + R`（`R ≤ |第一档|`，仅当日重排后可能 >0）。
- **R2（作答未落行）**：`KnowledgeQuizFeedbackWriter` 两条早退例外（既有定向更正指向、证据身份粘性）
  不写新行 ⇒ 只**少计**（更保守），日上界再加 `R2`。
- **R3（非扫描来源的当日测验）**：当日作答但不在本次扫描候选里的点同样占名额并被回放 ⇒ 当日
  **计划过的**去重节点可超过 10；**实际复习**仍以名额为上界。
- 时钟回拨 / 备份恢复 / 清库会丢当日行 ⇒ 最多再多一批（低频）。
- 证据表增长后的查询成本：查询走 `(learner_id, created_at_epoch_millis)` 索引，但**未在设备量 P95**
  （首页预览每次 RESUMED 都会调一次 `currentKnowledgeReviewPlan`）。

## 6. 未跑 / 未验证（如实）

| 项 | 状态 |
|---|---|
| 设备真库 `:core:data:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=...StudyReviewPlanner3EScanInstrumentedTest` | **NOT RUN**（本轨无设备；批次纪律要求主会话串行跑） |
| 设备用例改写（C8） | 已改写并**全量重编译通过**（§4-04）；运行结果未验证 |
| C5（时间预算先绑住、预算变更场景） | **未实现**（需要两个不同预算的已落库计划；留设备阶段） |
| 证据查询 P95（设备量） | **NOT MEASURED** |
| 3E 门判定「无条件 PASS」 | **NOT CLAIMED / NOT SIGNED**：设备 OK(4) 已由主会话补跑（`device/26-instrument-3e-rerun.log`），但台账没有 3E 门行；行级投影是 E-075 PASS、A-023 仍 BLOCKED（大队列耗时/内存未测）。批次级签字需验收线另立门行，修复环不代签。 |
| `docs/known-defects.md` KD-46 状态、`docs/acceptance/adjudication.md` 3E 行、行级 review JSON、台账机器块 | **未改**（属验收/台账阶段，避免与他线台账抢占） |

## 7. 改动文件

| 文件 | 改动 |
|---|---|
| `core/database/.../dao/ChatEvidenceDao.kt` | 新增只读查询 `readKnowledgeNodeIdsBySourceKindBetween`（`:190-215`） |
| `core/database/.../ModelTaskDatabasePort.kt` | 新增端口方法 `readChatEvidenceKnowledgeNodeIds`（`:221-237`） |
| `core/database/.../RoomStudyDatabase.kt` | 实现委托到 DAO（`:1883-1892`） |
| `core/domain/.../KnowledgeReviewQueue.kt` | 新增 `fullLibraryScanReplayCandidates`（traling 默认空，`:201`）；并集 `:219`；KDoc 改每日累计 |
| `core/domain/.../KnowledgeReviewFullLibraryScan.kt` | 常量 KDoc 改写（值仍 10，`:56`）；不变量/残留写实 |
| `core/domain/.../StudyExperienceRepository.kt` | 接口 KDoc 改每日累计 |
| `core/data/.../study/StudyReviewPlannerService.kt` | `PlanningContext` 加 `studyDayStartEpochMillis`；账本读口 + 扣减/回放/fail-closed（`:819-827`、`:927-952`、`:979-981`）；注释改写 |
| `core/data/src/test/.../RoomBackedStudyExperienceRepositoryTest.kt` | 新增 7 例 + 夹具；fake 加读口实现与失败探针；`repository()` 加 `reviewTimeBudgetSeconds` 参数 |
| `core/domain/src/test/.../KnowledgeReviewQueueTest.kt` | 新增回放/名额用例；旧用例 KDoc 与断言文案改为"纯函数分层"（断言本身不变） |
| `core/data/src/androidTest/.../StudyReviewPlanner3EScanInstrumentedTest.kt` | 旧 C8 用例改写为每日累计口径（删 `snapshotAfterMastering`、经真实写口落证据、断言回放/≤10）；类 KDoc 第 2/4 条改写 |

> `code-diff.patch` 里有 5 个**阶段开始前已是 `M`** 的文件（`RoomStudyDatabase.kt`、
> `StudyReviewPlannerService.kt`、`KnowledgeReviewQueue.kt`、`KnowledgeReviewQueueTest.kt`、
> `StudyReviewPlanner3EScanInstrumentedTest.kt`），因此 diff 包含他线/既有 hunk；本轨的 hunk
> 以 KD-46/3E 每日累计标记可辨。

## 8. 协作前置（阶段开始时实跑）

```
git status --porcelain -- core/domain/.../core/domain core/data/.../study core/data/.../androidTest/study
```

输出显示目标文件多为 `M`（前批/他线既有改动）。本轨只做**增量 hunk**（新增方法/参数/用例/注释），
未整文件覆盖；`knowledge-production/wusan/materials.jsonl`、`tools/kb_build/*`、`app/feature` 未触碰。
