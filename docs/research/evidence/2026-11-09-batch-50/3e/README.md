# 3E 门（全库薄弱点扫描 / 队列 / 预算）— 批次 50 设备与 JVM 证据

> 计划：`.zcode/plans/plan-2026-11-09-batch-50-release.md` §G2（第 17–18 行）。
> 需求行使：`docs/research/2026-10-11-new-stages-spec.md` §3E（「验收门：弱掌握点离开今日错题
> 并集仍可被捞回；F8/F17 封顶生效」）；`docs/requirements-fused-final-2026-10-11.md:114,118,228`
> （F7 / F8 / F17）。
> **本轨不改任何门值、不改 `FULL_LIBRARY_SCAN_DAILY_CAP`（=10），不改产品语义。**

## 1. 终态

| 门判据 | 结论 | 证据 |
|---|---|---|
| 弱掌握点离开今日错题并集仍可被捞回 | **成立（设备）**：45 分钟预算下，队外 118 个薄弱点里恰好捞回 10 个，且 10 个**全部**不在今日计划的知识点并集里 | `logs/08-instrument-3e-green-final.log` → `OK (4 tests)`；用例 `#fullLibraryScanRecallsWeakNodesOutsideTodaysQueueUpToTheDailyCap` |
| F17 配额封顶生效 | **成立**：第二档每次排计划恰好 10 个（= 常量），队外候选 118 个 → 封顶真的在封 | 同上 + `core/domain/.../KnowledgeReviewFullLibraryScan.kt:30` |
| F8 时间预算封顶生效 | **成立**：1200s 预算下知识点队列 6 项（6×180=1080 ≤ 1200 < 7×180），其中仅 4 项来自第二档（名额还剩 6 个没用 → 预算先绑住） | 用例 `#knowledgeQueueStopsAtTheTimeBudgetWhenItBindsBeforeTheDailyCap` |
| 每日 / 单计划口径 | **实现口径 = 每次排计划 ≤10，非严格每日累计（KD-46）**：同日两次排计划各取 10 个、累计 20 > 10；纯函数无"今日已扫"状态 | 设备 `#scanQuotaIsPerPlanNotADailyCumulativeTotal`；JVM `KnowledgeReviewQueueTest#scanQuotaIsPerPlanNotADailyAccumulator` |

**结论：3E 门通过（条件性，见 §5 风险 R1）。** 所有读数都在真实 Room 库 + 真实材料写口 + 真实
排程服务上取得，不是 mock；设备为 **emulator-5590（x86_64 / API 34）**，**不是真机**。

## 2. 判据 → 代码位置

| 判据 | 落点 |
|---|---|
| 第二档先按分取前 N（F17） | `core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/KnowledgeReviewQueue.kt:191-205`（`.take(fullLibraryScanDailyCap)` 在 200 行，并集在 204 行） |
| 并集后统一按分取队（F8） | 同文件 `:210-240`（`remainingSeconds` 起于 `timeBudgetSeconds`，`while (pool.isNotEmpty())` 逐项扣时） |
| 名额常量（=10，未改） | `core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/KnowledgeReviewFullLibraryScan.kt:30` |
| 知识点队列预算来源 = 当日计划预算 | `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/StudyReviewPlannerService.kt:918`；当日计划读取 `:807` |
| 当日预算解析（设置优先，构造参数仅回退） | 同上 `:117-121`；默认 20 分钟、范围 5–120 分钟见 `core/domain/.../StudyExperienceRepository.kt:829-834` |
| 知识点每个 180s、错题个性化桶 | `core/domain/.../LogDurationModel.kt:109`（180）、`:100`（60）；错题估时被 `ReviewPlannerV2` 换成桶预测：`core/domain/.../ReviewPlannerV2.kt:89`、`:942-952` |
| 全库扫描 SQL（一条查询） | `core/database/.../dao/ProblemOrganizationDao.kt:398`（SQL 见 `:360-395`）；行→记录映射 `RoomKnowledgeBaseStore.kt:467-475` |
| 范围规则（有绑定题 ∧ 材料可出题） | `core/domain/.../KnowledgeReviewFullLibraryScan.kt:51-73` |

## 3. 关键发现（本轨最重要的两条）

### 3.1 既有设备用例早已是红的（3/3），红因是夹具过期，不是实现缺陷

批次 50 首跑 `StudyReviewPlanner3EScanInstrumentedTest`：**`Tests run: 3, Failures: 3`**
（`logs/01-instrument-3e-scan-existing.log`）。逐条归因（均已回读代码确认）：

1. `noExamDayPlanIsFingerprintIdenticalToAnOutOfWindowExamPlan`：`预算逐位不变（无考试日）
   expected:<120> but was:<1200>`。`BudgetResolution`（批次 49，提交 `b6712a42`）之后，构造参数
   预算只在 `CONSTRUCTOR_ONLY` 下生效；默认 `SETTINGS_THEN_CONSTRUCTOR` 先读设置存储，真 DataStore
   给默认 1200s（`StudyReviewPlannerService.kt:117-121` + `StudyExperienceRepository.kt:829-831`）。
2. `fullLibraryScanRecallsWeakNodesOutsideTodaysQueueUpToTheDailyCap`：今日队列实际装了 15 题
   （预算实为 1200 而非 120），"弱点在今日并集之外"的夹具前提失效。
3. `knowledgeQueueStopsAtTheTimeBudgetWhenItBindsBeforeTheDailyCap`：期望 2 项、实得 6 项
   （1200/180 = 6，同一个预算口径问题）。

同一处的第二处过期假设是**知识点队列的预算来源**：提交 `ea4348f9`（2026-11-06）把
`timeBudgetSeconds = reviewTimeBudgetSeconds` 改成 `currentPlan.plan.timeBudgetSeconds`
（`git show ea4348f9 -- …/StudyReviewPlannerService.kt` 的 hunk：`- reviewTimeBudgetSeconds`
`+ currentPlan.plan.timeBudgetSeconds`）。此后"今日计划 120s + 知识排程实例 2700s"这种写法
不可能成立——预算只有一个来源。

**处置**：只改夹具，不改生产码；断言意图一条没删（节点 15→126、新增两条负向）。
`plannerService()` 显式用 `BudgetResolution.CONSTRUCTOR_ONLY`（该类 KDoc 自述只给"按显式预算
验证 G6/配额数学"的夹具用），并用 `LogDurationModel.record` 给三科各灌 600s/题的个性化桶
（10 分钟大题量级），使 45 分钟预算只装 4 道错题——118 个薄弱点因此真的落在今日并集之外。
**未放宽任何断言**：新增的"捞回 = 队外最老的 10 个"比原用例的"平局按 id 定序"更强。

### 3.2 F17 的"10"是**每计划**口径，不是每日累计（KD-46 仍成立）

- 设备：同一天连续两次排计划（第一次的 10 个复习为 MASTERED 后重排）→ 第二批是**另外** 10 个，
  两批不重叠，当日累计 20 > 10。
- JVM：`selectKnowledgeReviewQueue` 同输入重算逐位一致（无隐藏"今日已扫集合"），首批移除后
  再算得下一批 10 个。

这条口径已在 `docs/known-defects.md:2055`（KD-46，fixed 2026-10-18，口径=每次排计划 ≤10）登记；
本轨把**实际**语义钉进用例，避免被误当"每日累计封顶"。若要改成严格每日累计，需要新增
"当日已扫描集合"的持久面——那是产品变更，本轨不做。

## 4. 夹具与可复现性

| 维度 | 取值 |
|---|---|
| 规模 | 127 个知识点 / 126 道在册错题（120 弱 + 2 今日 + 3 已掌握 + 1 无材料）+ 1 个"只有材料"节点 |
| 弱掌握点 | `kc-lib-001..120`，绑定题 5–124 天前复习、稳定度 1 天、无知识点掌握态；主科按 MATH/PHYSICS/CHEMISTRY 轮转 |
| 今日题 | `kc-today-math` / `kc-today-phys`：400 天前复习（当日计划里分数最高，保证第一档非空） |
| 已掌握负向 | `kc-strong-01..03`：2 天前复习、稳定度 60 天、下次复习 +30 天（今日排不到、扫描也不捞） |
| 范围负向 | `kc-neg-no-material`（有在册题、无材料 → 扫描行 material 为 NULL、不进队列）；`kc-only-material`（有材料、无在册题 → 不在扫描行集） |
| 时钟 | `Clock.fixed(2026-01-02T08:00:00Z)`，`ZoneId=Asia/Shanghai`（本地 16:00 > 04:00 日界） |
| 落库方式 | 事实行走 `seedStudyFacts`（既有 androidTest 夹具：库文件直写、INSERT OR IGNORE，见 `StudySeedAndroidTestSupport.kt:27-61`）；材料走**真写口** `importKnowledgeTeachingMaterials`（契约 + 指纹 + Room 事务） |
| 数据性质 | **真实 SQLite/Room schema + 真实读路径 + 真实排程服务；种子是确定性合成内容**，不是生产用户库导出 |

## 5. 风险与未验证（如实）

**R1（口径性风险，需产品判断）**：知识点队列的预算 = 当日计划预算，且"第二档 10 个"只是
**上界不是预留**——并集按分取队（`KnowledgeReviewQueue.kt:204-240`）。因此第二档能进队多少，
取决于"知识点槽位数 − 今日档节点数"：默认 20 分钟 = 6 个槽（1200/180），若今日计划的题绑定
的节点已占满 6 个且分数都高于队外弱点，第二档贡献可以是 **0**。本轨 45 分钟夹具（4 个今日档
+ 10 个第二档）证明"能捞回"成立；但"每个默认配置都能捞回"不成立，除非给第二档预留份额
（那会改产品语义/门值，本轨未做，留给主会话裁定）。

**R2**：设备只有 **emulator-5590**（x86_64 / API 34），**未上真机**；未跑 2h soak；未跑
`connectedLocalFirstDebugAndroidTest`（计划禁令）。

**R3**：`9-jvm-data-3e-classes.log` 里 `RoomBackedStudyExperienceRepositoryTest` = 33 tests /
**1 failed**，失败项是 `profileStrengthsDropForgottenAndPrerequisiteSuppressedNodes`
（`expected:<[knowledge:m1:math.read-monotonicity-from-graph]> but was:<null>`），属"展示面强项 /
先修压制"，与 3E 两档/预算/名额无交集；`docs/research/2026-11-09-batch-50-record.md:60-61`
已把同一条记为批次 49 的既有失败。**本轨未修、也未把它记为通过**——3 个 3E 相关方法在该类中
均通过（`currentKnowledgeReviewPlanCoversTodaysQueuePlusTheFullLibraryScan`、
`currentKnowledgeReviewPlanScansTheLibraryEvenWhenTodaysQueueIsEmpty` 等）。
另：工作树里 `core/data/.../RoomBackedStudyExperienceRepository.kt` 有他线在飞改动（`git diff`
= 新增 KD-67 装配方法 69 行），与本失败无直接对应，**未归因于本轨**。

**R4**：`KnowledgeReviewQueueTest` 里的 5000 候选规模门（`< 1500ms`，实测该用例 44 ms）是 JVM
纯函数门，不覆盖真实 SQLite 扫描的 I/O 成本；设备侧 126 行的端到端耗时未单独计时（3 个用例
合计 `Time: 3.668`，含 3 次建库/播种，不是扫描微基准）。

**R5**：本轨未重复验证 KD-46 之外的"第二档与今日档同 id 以今日档为准"等既有 JVM 断言——它们
在 `KnowledgeReviewQueueTest` 23/23 绿里一并复跑通过。

## 6. 命令与读数（逐条）

```text
# 1) 旧用例红相（证据：logs/01-instrument-3e-scan-existing.log）
$ ./gradlew :core:data:assembleDebugAndroidTest --console=plain        # BUILD SUCCESSFUL
$ adb -s emulator-5590 install -r -t core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk
$ adb -s emulator-5590 shell am instrument -w -e class \
    com.tingyun.smartmistakebook.core.data.study.StudyReviewPlanner3EScanInstrumentedTest \
    com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner
Tests run: 3,  Failures: 3        Time: 1.54

# 2) 设备绿相（证据：logs/08-instrument-3e-green-final.log）
$ ./gradlew :core:data:assembleDebugAndroidTest --console=plain        # BUILD SUCCESSFUL
$ adb -s emulator-5590 install -r -t core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk
$ adb -s emulator-5590 shell am instrument -w -e class \
    com.tingyun.smartmistakebook.core.data.study.StudyReviewPlanner3EScanInstrumentedTest \
    com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner
OK (4 tests)        Time: 3.668
# test APK sha256 = dc0abe9dc8b39c6eab52ed1e5e2882db7c9868a33a126f2941c549eaf0147a82
# （上一轮 05-instrument-3e-green-attempt2.log 亦为 OK (4 tests)；08 是最终源树的复跑）

# 3) JVM：知识点队列（证据：logs/06-jvm-domain-…log + jvm/TEST-…KnowledgeReviewQueueTest.xml）
$ ./gradlew :core:domain:test --tests \
    "com.tingyun.smartmistakebook.core.domain.KnowledgeReviewQueueTest" --console=plain
BUILD SUCCESSFUL
tests=23 failures=0 errors=0 skipped=0 time=0.357
  scanQuotaIsPerPlanNotADailyAccumulator 0.005
  fiveThousandFullLibraryScanCandidatesStayWithinTheDailyCapAndScaleGate 0.044
  capsTheFullLibraryScanAtTheDailyCap 0.000
  aZeroDailyCapDisablesTheSecondTierEntirely 0.021

# 4) JVM：服务级 3E（证据：logs/09-jvm-data-3e-classes.log + jvm/TEST-…xml）
$ ./gradlew :core:data:testDebugUnitTest \
    --tests "…RoomBackedStudyExperienceRepositoryTest" \
    --tests "…StudyReviewPlannerExamWindowTest" \
    --tests "…StudyReviewPlannerServiceCapacityPreviewTest" --console=plain
RoomBackedStudyExperienceRepositoryTest: 33 tests / 1 failed（非 3E，见 R3）
StudyReviewPlannerExamWindowTest:         8 tests / 0 failed
StudyReviewPlannerServiceCapacityPreviewTest: 4 tests / 0 failed
```

`--tests` 在 `:core:domain:testDebugUnitTest` 上不可用（该任务只是 `test` 的委托包装，见
`core/domain/build.gradle.kts:22-28`），故 JVM 侧用 `:core:domain:test`。

## 7. 本轨改动文件

- `core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/study/StudyReviewPlanner3EScanInstrumentedTest.kt`
  ——夹具修正 + 规模化 + 新增 `#scanQuotaIsPerPlanNotADailyCumulativeTotal`（既有 4 个方法名保留）。
- `core/domain/src/test/kotlin/com/tingyun/smartmistakebook/core/domain/KnowledgeReviewQueueTest.kt`
  ——新增 `#scanQuotaIsPerPlanNotADailyAccumulator`（纯函数口径）。
- 证据：本目录（`README.md`、`summary.json`、`00-snapshot.txt`、`logs/`、`jvm/`）。

**生产代码零改动**；KB 包/内容/门值零改动；未 `git add`、未提交。

证据口径说明：设备绿相绑定的 test APK（sha `dc0abe9d…`）构建自**当前工作树**——HEAD
`a87fc70d` + 他线在飞改动（`KnowledgeReviewQueue.kt` +21 行 = 批次 49 的 `reasons` 字段、
`ReviewPlannerV2.kt` +79/-22、`ProblemOrganizationDao.kt` 的 D-3 父节点读口等）。本轨**没有**
改这些生产文件；3E 的两档取队/预算/名额代码路径逐行未变（`git diff` 仅命中本轨两个测试文件）。
