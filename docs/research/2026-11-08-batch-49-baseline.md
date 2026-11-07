# 批次 49 基线核对（2026-11-08）：掌握度 + 复习选题质量（A+B+C）

| 字段 | 值 |
|---|---|
| 角色 | 基线核对员（只读；未改任何主码/台账/金标/测试资产） |
| 计划 | `.zcode/plans/plan-2026-11-08-mastery-review-quality.md`（已完整读取，42 行；P0 5 项 + P1 5 项 + P2 5 项 + P3 3 项） |
| 旁证计划 | `.zcode/plans/plan-sess_972ed30a-b136-40b0-8362-e7e2a8be3065.md`（同题早期版本，比权威计划多出「4 例 + 1 例」夹具细节；**只作旁证，不作为判据**） |
| 上一批终裁 | `docs/research/2026-11-07-batch-48-record.md` §0/§5（NOT ACCEPTED 维持；台账 PASS 898 / FAIL 23 / BLOCKED 279）——已读 |
| 核对轮 | 2026-11-08（HEAD 提交时间为 2026-11-07 06:09:06 +0800；本文件按计划命名批次 49） |
| 本会话实跑范围 | 只读 `git`/`grep`/`sed`/`python -I`；**未跑任何 Gradle**（纪律：Gradle 只在门禁阶段串行）；未跑 `connectedLocalFirstDebugAndroidTest` |
| verdict | **可开工，但有 4 处必须先认账的缺口**：① P0-2 计划声称的「3 行 limit=2 断言」在工作树中**不存在**；② 5 条原红测试**未运行验证**（本 ask 不跑 Gradle）；③ 热区 `feature/review` 与 `app` 有**他线在飞改动**；④ 工作树 **14 个未跟踪 gradle.lockfile** + `feature/library/build.gradle.kts` 他线依赖改动，直接提交会污染 lockfile 门 |

---

## 1. HEAD / 远端 / 工作树

| 项 | 实测（本会话命令与输出） |
|---|---|
| HEAD | `git rev-parse HEAD` → `ba7b5624ea0c2bc3f09dcee247f5852e4f428122`（`git log -1` → `2026-11-07 06:09:06 +0800 docs: 批次48 主会话收口…`） |
| 分支 / 上游 | `git rev-parse --abbrev-ref HEAD` → `main`；`git status -sb` → `## main...origin/main` |
| 远端同步 | `git rev-list --left-right --count origin/main...HEAD` → `0	0`（无领先/落后） |
| 远端地址 | `git remote -v` → `origin https://github.com/tianshantingyun/smart-mistake-book.git`（fetch/push 同址） |
| 工作树计数 | `git status --porcelain=v1 \| wc -l` → **595** 个未提交路径；按状态码：`144 M` + `451 ??` |
| 脏路径分布（首层） | `core` 91、`docs` 63、`tools` 49、`knowledge-production` 14、`app` 11、`feature` 9，其余为 `tmp*`/`output`/`out`/`growth` 等散件 |

- 与批次 48 收口值（`dirty 5061`，见 `2026-11-07-batch-48-record.md:38`）不可直接比较：该值是 `acceptance_report` 的另一统计口径（含证据树逐文件），此处是 `git status` 路径数。
- **工作树不干净 → 按计划 §硬约束，热区改动只能最小 hunk / patch，不整文件抢占。**

### 1.1 已被占用的 git worktree（他线工作副本，非本批对象）

`git worktree list` → 主树 `D:/smart mistake book` `ba7b5624 [main]`，另有 **6 个**：`.codex/worktrees/plans-publication`（`codex/plans-2026-10-20`）、`.codex/worktrees/ui-green-pages`（`codex/ui-green-pages-2026-10-27`）、`.codex/worktrees/verify-visible-fixes`（detached `a15aebb7`）、`D:/aud11-measure`（detached `a15aebb7`）、`.worktrees/kb-bench`（detached `0486a532`）、`.worktrees/kernel-readiness`（`audit/kernel-readiness` `de1ff0c7`）。
→ 这些是**独立 checkout**，不占主树文件；但证明同一仓库有他线并行。主树脏路径的主题聚集（knowledge/visual 等）与这些线的主题相符，**但本基线未逐文件做归属**（只对 §5 列出的本批目标/约束文件逐一核了归属）。

## 2. 台账计数与门（本会话实跑，非引用）

```text
$ awk -F'|' 'NF>6 {gsub(/ /,"",$(NF-1)); print $(NF-1)}' docs/acceptance/ledger.md | sort | uniq -c
    279 BLOCKED
     23 FAIL
    898 PASS
      2 verdict     ← 表头
      1 ---         ← 分隔行
```

- **PASS 898 / FAIL 23 / BLOCKED 279**，与批次 48 终裁值一致（`2026-11-07-batch-48-record.md:29`）。本基线未改任何 verdict。
- 台账仅 **1 行**被本轮改动：`docs/acceptance/ledger.md`（`git diff --stat` → `1 file changed, 1 insertion(+), 1 deletion(-)`），即 E-061 行（见 §3.6）。

```text
$ python -I tools/ci/acceptance_lint.py --out build/b49-baseline/lint.json   （重定向后取真实退出码）
TRUE_EXIT=1
violations=144，Counter({'EVIDENCE_REUSED': 144})，ok=false, exit_code=1
```

- 与批次 48 的 `144 EVIDENCE_REUSED`（`2026-11-07-batch-48-record.md:38`）**逐值一致** → 本轮 `acceptance_lint.py` 的检查列改动对当前台账**零新增违规**。
- 补充机检（本会话 python 逐行解析 `ledger.md`）：`PASS` 行中「检查列以 `UNVERIFIED:`/`未验证：` 开头」的条数 = **0** → 新增规则在当前台账上不会误报，也不会因 E-061 改写而漏报其余行。

```text
$ python -I -m pytest tools/tests/test_acceptance_lint.py -q
..........                                                               [100%]
10 passed in 1.40s
```

- ⚠️ 该测试文件 `git status` 为**空（未改动）**：新增的「检查列前缀」规则**没有配套回归用例**，目前只有计划所称的「自测过」。属本批应补项（见 §8）。

## 3. 本轮已落 P0 逐项核对（工作树未提交，逐文件读 diff）

### 3.1 预算来源统一 —— ✅ 已落（未提交）

`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/StudyReviewPlannerService.kt`（`git diff` → `23 ++++++++++++++++----`）：

- `:60` 新增 `internal enum class BudgetResolution { SETTINGS_THEN_CONSTRUCTOR, CONSTRUCTOR_ONLY }`，注释明确「生产 = 设置存储优先；`CONSTRUCTOR_ONLY` 只给夹具」。
- `:117` `dailyReviewBudgetSeconds()` 改为 `when (budgetResolution)` 单点解析；构造参数 `budgetResolution` 默认 `SETTINGS_THEN_CONSTRUCTOR`（`:80`）。
- `:383` `reviewCapacityPreview` 新增 `val baseBudgetSeconds = dailyReviewBudgetSeconds()`，替换原 `reviewTimeBudgetSeconds`；`:393` 爬坡上限改按 `baseBudgetSeconds` 比较 → **预览/计划/引入三处同源**。
- 测试夹具：`StudyReviewPlannerExamWindowTest.kt` 的 `plannerService(...)` 增加 `budgetResolution` / `settingsStore` 参数（`:344` 附近），`CONSTRUCTOR_ONLY` 为默认；`StudyReviewPlannerServiceCapacityPreviewTest.kt` 夹具显式 `budgetResolution = BudgetResolution.CONSTRUCTOR_ONLY`（`:192`）。
- 新增两例：`settings store budget wins over the constructor fallback`（`:467`，断言 10 分钟=600s 压过构造 120s）、`null settings store falls back to the constructor budget`（`:486`，断言 120s）。

### 3.2 review_log 最近窗口 —— ⚠️ DAO 与 fake 已落，「3 行 limit=2 断言」**未找到**

- `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/dao/LearningDao.kt:227-242`：`@Query` 已改为子查询——内层 `ORDER BY reviewed_at_utc DESC, review_log_id DESC LIMIT :limit`，外层 `ORDER BY reviewed_at_utc ASC, review_log_id ASC`（`readReviewLogSamples` 签名未变，**未新增 DAO 方法**，符合 `RoomStudyDatabase.kt` 约束）。
- fake 同口径：`core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/study/RoomBackedStudyExperienceRepositoryTest.kt:3476` 由 `.take(limit)` 改 `.takeLast(limit)`（排序仍 `sortedBy { reviewedAtEpochMillis }` 升序）。
- 附带结论：`core/data/src/main/kotlin/.../study/ReviewLogSink.kt:164-166` 的 `eligibleRows()` **没有**做「反转回升序」（旁证计划 §P0-2 要求的一步）——因为 SQL 外层已 `ASC`，不需要反转；此处与最终实现一致，**不构成缺陷**，但旁证计划的措辞已过时。
- ❌ **缺口**：计划 §P0-2 写「新增 3 行 limit=2 断言」。本会话检索 `core/data/src/test`、`core/database/src/test`、`core/database/src/androidTest` 全树：`readReviewLogSamples` 的调用点里**没有任何 `limit = 2` 用例**；唯一 `limit = 2` 命中 `core/database/src/androidTest/.../StudyDatabaseInstrumentedTest.kt:1124` 属 `observeCompletedReviewLocalDays`，且该文件 `git status` 为**空（未改动）**。→ **该断言未落盘**，不能算作已完成的先红后绿证据。

### 3.3 校准口径过滤 —— ✅ 已落（未提交）

- `core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/SchedulingEvaluation.kt:316-319`：`calibratePlannedReasons` 的取样本行改为 `fittableReviewSamples(samples).mapNotNull { … }`，注释说明与 FSRS 拟合/logLoss 同口径（排除 `MODEL_JUDGED` / `REVEAL`）。
- 新增用例：`core/domain/src/test/.../PlannedReasonCalibrationTest.kt` `model judged and reveal samples do not enter per reason calibration`（文件现 4 个 `@Test`），断言 `sampleCount == 1`、`realizedRecallRate == 1.0`；夹具 `sample(...)` 增加 `sourceKind` 参数默认 `ATTEMPT`。

### 3.4 fake plannedReason —— ✅ 已落（未提交）

- `RoomBackedStudyExperienceRepositoryTest.kt:3489` 新增 `plannedReason = entry.plannedReason`（同一处 fake 的 `readReviewLogSamples`），此前 fake 丢字段导致「真库有、JVM 用例看不见」。

### 3.5 acceptance_lint 检查列 —— ✅ 已落（未提交），但无配套回归用例

- `tools/ci/acceptance_lint.py:110-116`：`import re`（`:33`）+ `re.match(r"^\s*(?:UNVERIFIED|未验证)\s*[:：]", row.check)`，把「检查列**状态前缀**形态」纳入 `UNVERIFIED_AS_PASS`，正文里描述规则不再误报。
- 自测：§2 的 `pytest` 10/10 + `acceptance_lint` 144 不变（本会话实跑）。
- ⚠️ `tools/tests/test_acceptance_lint.py` **零改动** → 该规则无钉值用例（见 §8）。

### 3.6 E-061 文本 —— ✅ 已落（未提交，仅台账 1 行）

`git diff -U0 -- docs/acceptance/ledger.md`：

- 旧检查列以 `UNVERIFIED: …本轮未跑设备` 开头 → 新文本 `错因落账并发。JVM 用例用假端口，不模拟 SQL 事务；真 SQLite 事务由设备用例 \`ErrorCauseLedgerInstrumentedTest\` 覆盖（3 例 0 失败，E-061 证据）`。
- 与 `docs/acceptance/verdicts/rows/E-061.json` 的既有证据自洽（该 JSON `git status` 未变）：`xml_aggregate` → `tests=3; failures=0; errors=0; skipped=0`，`device_binding` → `counts={"errors":0,"evaluated":3,"failures":0,"skipped":0,"tests":3}; verdict=PASS`。
- 该行 verdict 仍为 `PASS`，本基线未改判。

### 3.7 计划 §P0-5（25 份失效复核）—— 未开工（无批次 49 证据/记录）

`ls docs/research | grep 2026-11-08` → 空；`docs/research/evidence/2026-11-08-batch-49` → **不存在**（`ls: cannot access … No such file or directory`）。属 P3 最终台账冻结后执行件，本基线只登记「未开始」。

## 4. 5 条原红测试：点名、成因链与当前状态

**状态：全部 NOT RUN（本 ask 只做基线，未跑 Gradle；无本会话证据可称绿/红）。** 下面「红因链」是**静态推断**，依据是夹具预算值 < `SchedulingOptions` 默认值，非运行日志：

- 默认值来源：`core/domain/src/main/kotlin/.../StudyExperienceRepository.kt:737` `dailyReviewMinutes: Int = 20` → `:739` `dailyReviewBudgetSeconds = dailyReviewMinutes * 60` = **1200s**。
- 机制：**PR#13 之后、本批夹具修复（注入 `CONSTRUCTOR_ONLY`）之前**——`reviewCapacityPreview` 已改读 `dailyReviewBudgetSeconds()`，设置存储默认 1200s 优先；而下列 5 例仍按构造参数 120/60/300s 断言 → 预览出数变 1200s 后红。

| # | 测试（文件:行） | 夹具预算 | 断言（会被 1200s 推翻的部分） | 当前状态 |
|---|---|---|---|---|
| 1 | `StudyReviewPlannerExamWindowTest.in-window exam grows the daily budget and the queue`（`:120`） | 120 | `timeBudgetSeconds == 120`、`== 180`、队列 2/3 题 | **NOT RUN** |
| 2 | `…in-window exam flips the daily selection toward the exam subject`（`:153`） | 60 | `queue == [unit-a]` / `[unit-b]` / `[unit-a]`（预算变 1200 会纳入两题） | **NOT RUN** |
| 3 | `…parallel exams take the steepest ramp for budget and rank by subject ramp`（`:199`） | 60 | 预算/排序断言 | **NOT RUN** |
| 4 | `…weak knowledge node pulls its bound fresh question ahead before the exam`（`:224`） | 300 | 队列/顺序断言 | **NOT RUN** |
| 5 | `StudyReviewPlannerServiceCapacityPreviewTest.service preview keeps the existing intake unschedulable count`（`:81`） | 120 | `intakeUnschedulableCount == 1`（注释：「300s 的真实时长超过 120s 整日预算」） | **NOT RUN** |

点名依据：① 旁证计划 §P0-1 原文「`StudyReviewPlannerExamWindowTest`（4 例）、`StudyReviewPlannerServiceCapacityPreviewTest`（1 例）」；② 上表 5 例是这两个文件中**唯一**预算 < 1200s 的用例（其余用 1200s，与默认值同值不受影响）。两文件现分别含 8 / 4 个 `@Test`（`grep -c "@Test"`）。
→ **门禁阶段必须逐条点名复跑**（`core:data:testDebugUnitTest --tests …`），本条基线不替它背书。

## 5. 热区他线在飞清单（逐文件 `git status`）

### 5.1 `feature/review`（计划授权修改，但有他线在飞）

```text
 M feature/review/src/test/kotlin/com/tingyun/smartmistakebook/feature/review/KnowledgeReviewSessionViewModelTest.kt
?? feature/review/gradle.lockfile
```

- 那 1 个 `M` **不是本批改动**：diff 把 `processRestoredHandle` 的 Java 序列化（`ObjectOutputStream/ObjectInputStream`）换成 Map 深拷贝（`state.mapValues`），属**他线**（SavedState 语义修正）。
- 本批 KD-67 的目标文件现状：`KnowledgeReviewSessionViewModel.kt`、`KnowledgeReviewSessionViewModelFactory.kt`、`KnowledgeReviewSessionScreen.kt` 均**干净**（`git status` 空）→ 可做最小 hunk；但同模块测试文件被占，**新确认卡测试建议新建独立文件**，勿整文件改写该测试。

### 5.2 `app`（计划授权修改，但 6 M + 5 ?? 全为他线）

```text
 M app/src/main/kotlin/com/tingyun/smartmistakebook/ReviewReminderCoordinator.kt        (4 +, 2 -)
 M app/src/test/kotlin/com/tingyun/smartmistakebook/ReviewReminderCoordinatorTest.kt
 M app/src/androidTest/kotlin/com/tingyun/smartmistakebook/ReviewReminderPlatformInstrumentedTest.kt
 M app/src/androidTest/kotlin/com/tingyun/smartmistakebook/MistakeExportNotificationsInstrumentedTest.kt
 M app/src/test/kotlin/com/tingyun/smartmistakebook/SavedMistakeSheetBytesTest.kt
 M app/src/test/kotlin/com/tingyun/smartmistakebook/visualjs/VisualContentMeasuringTest.kt
?? app/gradle.lockfile
?? app/src/androidTest/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsClientFrameQueueDeviceInstrumentedTest.kt
?? app/src/androidTest/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsDeathUnconfirmedFailClosedInstrumentedTest.kt
?? app/src/androidTest/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsReconnectBudgetInstrumentedTest.kt
?? app/src/test/kotlin/com/tingyun/smartmistakebook/visualjs/VisualMeasureEnvironmentBudgetTest.kt
```

- 主题聚集在**提醒（ReviewReminder）**与**视觉 JS（visualjs）**两条他线，与本批掌握度/选题无交集。
- 本批 KD-67 的 app 侧装配点 `app/src/main/kotlin/com/tingyun/smartmistakebook/SmartMistakeBookRoot.kt:939`（`KnowledgeReviewSessionScreen(` 调用）**干净** → 可改。
- 提醒：`app` 里 `ReviewReminder*` 正在被改，若本批 UI 用例跑 `app` 全量 androidTest，需先与那条线对齐，否则会把他线红算到本批头上。

### 5.3 共享约束文件（`RoomStudyDatabase.kt`）已被他线占用，但不违反本批约束

- `git status` → ` M core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/RoomStudyDatabase.kt`；`git diff --stat` → `25 insertions(+)`，内容为 **PR#13（T8 冻结卷面/逐题答案）桥接的手工并集**（类头注释「PR#13（T8）… 这里手工并集」、`paperReviews` 入口），非本批改动。
- 它**未新增 LearningDao 方法**（`LearningDao.kt` 只改既有 `readReviewLogSamples` 的 SQL）→ 计划硬约束「`RoomStudyDatabase.kt` 不新增 DAO 方法」**仍可守**。风险在于该文件已被他线 25 行占用，本批**不得再整文件改**。

### 5.4 依赖/lockfile 他线信号（会污染门禁）

```text
 M feature/library/build.gradle.kts        (+ testImplementation(libs.kotlinx.coroutines.test))
?? settings-gradle.lockfile
?? app/gradle.lockfile, core/{data,database,domain,export,markdown,model,ui}/gradle.lockfile,
   feature/{capture,library,profile,review,tutor}/gradle.lockfile        ← 共 14 个
```

- `tools/ci/check_lockfiles.py` 是 CI 实跑步骤（`.github/workflows/android-check.yml:74`），不属「候选」。KD-72 既有结论仍有效：这批锁「生成于他线未提交的构建改动、与 HEAD 不一致，**不得原样 `git add`**」（`docs/known-defects.md:2798`），且盘上锁数 14 与当时一致 → **本批提交前必须先裁定归属**（不整文件抢占、不顺手 `git add`）。

### 5.5 KD-67 底座现状（重要事实）

- `AdaptiveClarificationConsumer` 定义在 `core/domain/src/main/kotlin/.../AdaptiveSelectionConsumption.kt`；`selectAndConsume` 在 `core/data/src/main/kotlin/.../AdaptiveSelectionAssembler.kt:308`。
- **生产调用点不存在**：`grep -rn "AdaptiveSelectionAssembler(" --include=*.kt app feature core` 只命中 `AdaptiveSelectionAssembler.kt` 自身（`:62` 文档、`:88` 类声明）与 `AdaptiveSelectionAssemblerTest.kt`；源码注释 `:79` 亦自述「**仍未接通**：确认流的生产调用点…」。
- 相关目标文件全部干净：`AdaptiveSelectionConsumption.kt`、`core/data/.../KnowledgeQuizItemStore.kt`、`.../KnowledgeQuizItemVerificationUpgrader.kt`、`core/domain/.../StudyExperienceRepository.kt`（`git status` 均空）。
→ P2-11 需要**从零新建生产装配点**（不是在既有调用点加参数），工作量按「新增 + 接线 + 测试」估，不能按「改一行」估。

## 6. 权重 / G6 / 版本 / 掌握阈值基线（供 P1 对照，均未改）

| 项 | 现值 | 位置 |
|---|---|---|
| G6 考试爬坡幅度 | `EXAM_CAPACITY_RAMP_MAX_EXTRA_FRACTION = 0.5` | `core/domain/src/main/kotlin/.../AlgorithmConstants.kt:148` |
| 选题权重（KC 掉落） | `KC_DROP_WEIGHT = 2.5`（仍是裸常量，未单源） | `core/domain/src/main/kotlin/.../ReviewPlanner.kt:22`；消费 `ReviewPlannerV2.kt:665` |
| 规划器版本 | `ReviewPlannerV2.VERSION = "review-planner-v4"` | `core/domain/src/main/kotlin/.../ReviewPlannerV2.kt:1055` |
| 掌握阈值（**本批不改**） | `MIN_STABILITY_DAYS = 21.0`、`MIN_CURRENT_RETENTION = 0.9` | `core/domain/src/main/kotlin/.../AlgorithmConstants.kt:63`、`:66`；语义见 `ClearlyMasteredForSkipPolicy.kt:13` |
| 金样 | `ReviewPlannerScaleBenchmarkTest` 三组夹具「bit identical」（文件头注释：金样**不含版本串**，但含选择序列 → 权重/G6 变更会破） | `core/domain/src/test/kotlin/.../ReviewPlannerScaleBenchmarkTest.kt:24-30`、`:63-74` |

→ 计划 §P1-9「一次性 VERSION bump + 三组金样重生成 + 集成回归」的前置确认成立：现状 v4 + 0.5 + 2.5 单源缺失，均可作为变更前快照。

## 7. 设备与模拟器（本会话实测）

```text
$ C:/Android/Sdk/platform-tools/adb.exe devices -l
emulator-5566  device  product:sdk_gphone64_x86_64 model:sdk_gphone64_x86_64 device:emu64xa transport_id:1
emulator-5590  device  product:sdk_gphone64_x86_64 model:sdk_gphone64_x86_64 device:emu64xa transport_id:2
```

| 设备 | Android | SDK | ABI | 已装 tingyun 包 |
|---|---|---|---|---|
| emulator-5566 | 14 | 34 | x86_64 | 10 个：`localfirst`、`offline` + `core.database.test`、`core.data.test`、`core.export.test`、`feature.tutor.test`、`feature.capture.test`、`feature.library.test`、`localfirst.test`、`offline.test` |
| emulator-5590 | 14 | 34 | x86_64 | 2 个：`com.tingyun.smartmistakebook.localfirst` + `.localfirst.test` |

- AVD 清单（`C:/Android/Sdk/emulator/emulator.exe -list-avds`）：`api23_smoke`、`api23_smoke32`、`api23_smoke64`、`audit_user_visible_20261016`、`kb_test_device`、`kb_test_device_iso`、`ps16k_arm64`、`ps16k_x86_64`、`test_device`、`ui_motion_20261107`。
- `adb`/`emulator` **不在 Git Bash PATH**，须用绝对路径 `C:/Android/Sdk/platform-tools/adb.exe`、`C:/Android/Sdk/emulator/emulator.exe`（`local.properties` → `sdk.dir=C:\Android\Sdk`；`ANDROID_HOME=C:\Android\Sdk`）。
- **无真机**；两台在线模拟器均为 **x86_64 / API 34**。P2 的无障碍/截图设备证据与 P3 的 2h soak（F-055/F-057）可用这两台；`connectedLocalFirstDebugAndroidTest` 按计划禁令**不跑**。
- 本基线**未安装/未启动/未写入**任何设备状态（只 `pm list packages` / `getprop` 只读查询）。

## 8. 未验证 / 缺口 / 交接

1. **NOT RUN（本 ask 明确排除）**：① 5 条原红测试的当前红/绿（未跑 Gradle）；② `core:domain:test`、`core:data:testDebugUnitTest`、`core:database:testDebugUnitTest`；③ 12 模块单测门 + 强门（口径同 `docs/research/2026-11-05-batch-43-baseline.md:418`）；④ 任何设备用例（soak/确认卡/校准页/冲突页）。→ 批次 49 的「定向 0 红」「5 条点名绿」目前**一条都还没有证据**。
2. **P0 缺件**：计划 §P0-2 的「3 行 limit=2 断言」在工作树中**不存在**（§3.2）。这是 P0 唯一的「声称已改但没找到」项，开工第一件事应补上（先红：3 行数据 + `limit=2` 断言保留最新两条且升序）。
3. **P0 缺件（计划未列）**：`acceptance_lint` 检查列规则无回归用例（`tools/tests/test_acceptance_lint.py` 未改）。建议补 1 例：检查列正文含「UNVERIFIED」不得报、前缀 `UNVERIFIED:` 必报、前缀 `未验证：` 必报。
4. **热区冲突面**：`feature/review` 测试文件与 `app`（提醒/视觉两线）正在被改；`RoomStudyDatabase.kt` 他线 25 行。本批任何改动前须再跑一次 `git status -- <目标文件>`，若已被占则停项/出 patch 并登记（计划硬约束）。
5. **lockfile 风险**：14 个未跟踪 `gradle.lockfile` + `feature/library/build.gradle.kts` 他线依赖改动；提交/门禁前须裁定归属，否则 `check_lockfiles.py` 结果不可信。
6. **路径/命名**：本批证据落点 `docs/research/evidence/2026-11-08-batch-49/` **尚不存在**（需由执行件创建）；本文件是本批唯一已落盘产物。
7. **文档口径**：旁证计划 `.zcode/plans/plan-sess_972ed30a-….md` 与权威计划在「`ReviewLogSink.eligibleRows()` 反转」一处措辞不一致（实现不需要反转，见 §3.2）；以权威计划为准时该步按「已由 SQL 外层 ASC 达成」处理，不重复实现。
