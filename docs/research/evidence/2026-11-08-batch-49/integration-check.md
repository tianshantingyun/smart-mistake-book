# 批次 49 · 集成静态检查与提交清单（2026-11-08）

> 角色：集成工程师（只读检查；**未改任何主码/测试/台账/金样**，只新增本文件与
> `commit-manifest-add.txt`）。范围 = 本批改动枚举（`git status` 只读）、包名/import/
> 依赖/资源核对、提交清单、`feature/review` 与 `app` 的他线 hunk 归属确认。
>
> 明确未跑：**任何 Gradle**（含 `:app:compile*`、`:core:*:test*`、门禁/强门）、任何设备/
> `adb` 命令、`acceptance_report`、`acceptance_lint` 全量复跑。计划纪律「Gradle 只在门禁/
> 实验阶段串行运行；设备用 adb」在本 ask 未触发。下文中「绿/成功」一律标注为**证据文件
> 自述**，不是本会话实测。

## 0. 结论摘要

| 项 | 结果 |
|---|---|
| 批次 49 改动枚举 | **可枚举且闭合**：新增 27 个代码/文档文件 + 证据树 104 个文件；修改 28 个既有文件（逐文件 `git status` 命中）。提交面 126 路径（`git add --dry-run` 实测）；证据树 22 个 `.log` 被仓库 `*.log` 规则忽略、不可入库（§4/§5-F10） |
| 包名 / 项目内 import | **静态解析通过**：新文件 78 条 + 修改文件新增 22 条 `com.tingyun.*` import，全部能定位到声明（0 未解析） |
| 依赖 | **满足**：`feature/review`↔`core:{model,domain,ui}`、`app`↔`core:{data,database,domain,model,ui}`、`core:domain` 测试仅需 JUnit（模拟器只用 JDK/JUnit） |
| 资源 | **无新增资源需求**：新 UI/测试文件 0 处 `R.*` 引用；无 manifest/资产变更 |
| 热区归属 | **未覆盖他线 hunk**：`feature/review` 唯一被占的测试文件 diff 仍是他线 SavedState 改动；`app` 本批只碰了开工时干净的 3 个文件；他线 6 个 app 文件无本批标记 |
| `RoomStudyDatabase.kt` | **无新增 DAO**：现有 diff 16 行全为 PR#13（T8）手工并集；`LearningDao` 仅改既有 SQL，方法签名未变 |
| 硬约束 | 掌握阈值 21 天/0.9、G6 0.5、`VERSION=review-planner-v4`、金样文件均未变（hash 复核） |
| 阻塞提交的发现 | **1 个应剔除文件**（GBK 旧副本 JSON）+ **1 处证据↔树 hash 漂移**（登记即可）；`.mimosa`/`.log` 已被 `.gitignore` 覆盖、不会误入（§3.6/§5-F3/F10）。均可按 §4 清单处理，不需要改主码 |

## 1. 工作树与批次改动枚举

### 1.1 工作树（只读）

```text
$ git status --porcelain=v1 | wc -l
642
$ git rev-parse HEAD
ba7b5624ea0c2bc3f09dcee247f5852e4f428122
```

与基线（`docs/research/2026-11-08-batch-49-baseline.md:23`，595 条）之差 = 本批 49 的新增/
修改 + 其他线继续在飞。**642 不是本批commit scope**；本批 scope 见 §1.2/§1.3。

### 1.2 本批新增（27 个代码/文档 + 证据树）

| 组 | 文件 | 证据 |
|---|---|---|
| domain 读模型 | `core/domain/src/main/.../SchedulingCalibrationView.kt` | 计划 §P2-12；`calibration-page/README.md` §1 |
| domain 测试 | `.../SchedulingCalibrationViewBuilderTest.kt`、`ReviewScoringWeightsSeamTest.kt` | `calibration-page/README.md` §1；`p1-9-keep-current-values.md` |
| 模拟器 | `.../simulation/{SimulatedLearner,SimulatedLearnerTest,SimulationDeterminismContractTest,ReviewCalibrationExperimentTest}.kt` | 计划 §P1-6；`p1-6-simulated-learner.md` |
| core/data 测试 | `.../KnowledgeQuizItemStoreVerificationStatusTest.kt`、`.../ProjectionConflictReprojectTest.kt` | `kd67-clarification-closure.md:44`；`p2-conflict-handling.md` §3 |
| core/data 设备测试 | `core/data/src/androidTest/.../settings/SchedulingSettingsStoreInstrumentedTest.kt` | `calibration-page/README.md` §1 |
| feature/review 生产 | `.../KnowledgeClarificationCard.kt`、`KnowledgeClarificationPresenter.kt`、`KnowledgeConflictCard.kt` | `kd67-clarification-closure.md:35-36`；`p2-conflict-handling.md` §2.2 |
| feature/review 测试 | `.../KnowledgeClarificationPresenterTest.kt`、`KnowledgeConflictCardTest.kt`、`KnowledgeConflictSessionStateTest.kt` | 同上 |
| app 生产 | `app/src/main/.../SchedulingCalibrationSection.kt`、`StudyProjectionIssueBanner.kt` | `calibration-page/README.md` §1；`p2-conflict-handling.md` §1 |
| app 测试 | `app/src/test/.../{SchedulingCalibrationCopyTest,StudyProjectionIssueCopyTest}.kt`、`app/src/androidTest/.../SchedulingCalibrationSectionInstrumentedTest.kt` | 同上 |
| research 文档 | `docs/research/2026-11-08-{batch-49-baseline,review-calibration-prereg,review-calibration-report,calibration-method,clarification-ux}.md`、`2026-11-08-review-calibration-report.json` | 计划 §P1-7/8；本文件同目录证据 |
| 证据树 | `docs/research/evidence/2026-11-08-batch-49/**`（104 个文件；另有本 ask 新增的 `integration-check.md` 与 `commit-manifest-add.txt`） | 计划 §P3；本 ask 指定的证据落点 |

### 1.3 本批修改（28 个）

```text
core/database/.../{dao/LearningDao.kt, androidTest/.../MasterySchedulingMigrationInstrumentedTest.kt}
core/data/.../study/{StudyReviewPlannerService,StudySchedulingCalibration,RoomBackedStudyExperienceRepository,
                     KnowledgeQuizItemStore,KnowledgeQuizItemVerificationUpgrader,StudyProjectionDrainer,
                     StudySnapshotBuilder}.kt
core/data/.../settings/DataStoreSchedulingSettingsStore.kt
core/data/src/test/.../study/{StudyReviewPlannerExamWindowTest,StudyReviewPlannerServiceCapacityPreviewTest,
                     RoomBackedStudyExperienceRepositoryTest,StudySchedulingCalibrationTest,
                     KnowledgeQuizItemVerificationUpgraderTest}.kt
core/domain/.../{KnowledgeReviewQueue,ReviewPlanner,ReviewPlannerV2,SchedulingEvaluation,StudyExperienceRepository}.kt
core/domain/src/test/.../PlannedReasonCalibrationTest.kt
feature/review/.../{KnowledgeReviewSessionScreen,KnowledgeReviewSessionViewModel}.kt
app/.../{SmartMistakeBookRoot,SecondaryScreens,SchedulingSettingsScreen}.kt
tools/ci/acceptance_lint.py
docs/acceptance/ledger.md
```

逐文件核验方法：路径 × 证据文档登记的落点 × `git diff` 内容主题三方对照；28 条在
`git status --porcelain` 中全部命中（`??` 目录内的 4 个模拟器文件通过
`core/domain/src/test/.../simulation/` 整目录 `??` 命中）。

## 2. 热区 hunk 归属（计划硬约束）

### 2.1 `feature/review`

| 文件 | 开工时 | 本批 | 本会话实测 diff | 结论 |
|---|---|---|---|---|
| `KnowledgeReviewSessionScreen.kt` | 干净（基线 §5.1） | 插入 KD-67/冲突卡参数与槽 | +78/−0，全部含 `KD-67`/`P2-14`/`K5` 标记 | 本批独占，无覆盖 |
| `KnowledgeReviewSessionViewModel.kt` | 干净 | 队列 v2 + 冲突状态机 | +77/−2，全部含 `P2-14`/冲突/校准 | 本批独占 |
| `KnowledgeReviewSessionViewModelTest.kt` | **他线在飞**（Java 序列化→Map 深拷贝） | 本批**未改** | +5/−12，内容仅 `Object*Stream` → `mapValues`（他线语义）；`git diff` 中 0 处本批标记 | 他线 hunk **仍完整存在**，未被覆盖 |

本批新增的 3 个测试是**独立新文件**，没有整文件改写被占测试（符合基线 §5.1 建议）。

### 2.2 `app`

| 文件 | 开工时 | 本批 | 实测 |
|---|---|---|---|
| `SchedulingSettingsScreen.kt` | 干净 | 恢复 `retentionFromSlider` + 1 行调用 | +22/−1，含 `P2-12`/`批次 49` |
| `SecondaryScreens.kt` | 干净 | `CapabilityScreen` 3 个可选参数 + 面板 | +18/−0，含 `P2-12` |
| `SmartMistakeBookRoot.kt` | 干净（基线 §5.2 点名 `:939`） | KD-67 装配 + 投影状态卡 + 校准页接线 | +338/−12；删除行仅为同一函数的改写（`StudyDataStatusLine`、`restoreQuiz`/`loadQuiz` 局部） |
| `ReviewReminderCoordinator.kt` + 2 个提醒测试 + 3 个 visualjs 测试 | **他线在飞** | 本批**未改** | `git diff --numstat` 仍为基线值 `4/2`（该文件）；其余文件 diff 中 0 处 `批次 49`/`P2-1x`/`KD-67` 标记 |

结论：`app` 本批只落在开工时**干净**的 3 个文件，他线在飞的 6 个文件未被整文件改写或覆盖。

### 2.3 共享数据库约束

```text
$ git diff --numstat -- core/database/.../RoomStudyDatabase.kt
16  0  core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/RoomStudyDatabase.kt
$ git diff --numstat -- core/database/.../dao/LearningDao.kt
13  4  core/database/.../dao/LearningDao.kt
```

- `RoomStudyDatabase.kt` 16 行全部是 `PR#13（T8）` 手工并集（类头注释 + `paperReviews`/详细解答入口），
  **无本批内容、无新 DAO 方法**（`git diff -U0 | grep '^+'` 逐行核对）。
- `LearningDao.kt` 只改既有 `readReviewLogSamples` 的 `@Query`（`:227-242`），
  **方法签名未变**（`:242`），满足「`RoomStudyDatabase.kt` 不新增 DAO 方法」。

### 2.4 计划硬约束的常量面（未变）

| 项 | 现值 | 位置 | 本批 |
|---|---|---|---|
| 掌握稳定度阈值 | `21.0` | `AlgorithmConstants.kt:63` | 未改（`git status` 空） |
| 掌握召回阈值 | `0.9` | `AlgorithmConstants.kt:66` | 未改 |
| G6 幅度 | `0.5` | `AlgorithmConstants.kt:148` | 未改 |
| 规划器版本 | `review-planner-v4` | `ReviewPlannerV2.kt:1097` | 未 bump |
| 金样文件 | `ReviewPlannerScaleBenchmarkTest.kt` | — | `git status` 空；sha256 与 manifest 同值（§3.5） |

权重只做了**注入 seam**（`ReviewScoringWeights`，默认值逐位等于原常量，`ReviewPlannerV2.kt:15-55`），
`KC_DROP_WEIGHT` 迁入 `DEFAULT_KC_DROP_WEIGHT = 2.5`（`:53`），`ReviewPlanner.kt:22-25` 仅剩注释。
默认值不变 → 不需要 VERSION bump/金样重生成（与预注册 §11 一致）。

## 3. 静态一致性检查（本会话实跑）

### 3.1 包名 / import

- 新文件 `package` 行与目录一致（21 个新增代码文件全部核对：`feature.review`、`com.tingyun.smartmistakebook`、
  `core.domain`、`core.data.settings`、`core.domain.simulation`）。
- 项目内 import 解析（Python 单遍扫描 `core/feature/app` 源树）：
  - 新文件：`imports: 78 syms: 78 / missing: []`
  - 修改文件新增 import：`new imports: 22 / missing: []`
  - 唯一一度告警的 `toTutorAssessmentItem` 是扩展函数（`core/model/.../Assessment.kt:77`），正则未覆盖，非缺失。

### 3.2 依赖

| 模块 | 本批用到的依赖 | 结论 |
|---|---|---|
| `feature/review` | `core:model`（`ReviewReason`）、`core:domain`（`Adaptive*`）、`core:ui`（卡片组件）、`kotlinx-coroutines-test`（新测试） | 均在 `feature/review/build.gradle.kts` |
| `app` | `core:data`（upgrader/verifier）、`core:database`（`StudyDatabasePort`）、`core:domain`、`core:model`、`core:ui`、`lifecycle.runtime.compose` | 均在 `app/build.gradle.kts` |
| `core:domain` 测试 | 仅 JUnit + JDK（`SimulatedLearner*` 只用 `java.security.Random/MessageDigest` 等） | `core/domain/build.gradle.kts` 只有 `testImplementation(libs.junit)`，够用 |
| `core:data` 测试 | `runBlocking`/`CoroutineScope`（coroutines-core，`implementation`） | 既有测试同用法 |

`SchedulingCalibrationSectionInstrumentedTest` 复用既有 `CapabilityScreen`（`internal`）——
`app` 的 androidTest 已有相同先例（`CapabilityScreenInstrumentedTest.kt:57`），非新风险。

### 3.3 资源

新 UI/测试文件 0 处 `R.string/R.drawable/R.id` 引用；不新增 Activity/Destination，无需
manifest、strings、drawable 变更。

### 3.4 JSON / 证据自洽

```text
$ python -I（逐文件 json.loads，UTF-8）
json files: 22   bad: 1
('docs/2026-11-08-review-calibration-report.json', "'utf-8' codec can't decode byte 0xb8 …", 37435)
```

- 21 个 JSON（含 `calibration/*.json`、`cross-process-rerun/*.json`、`docs/research/2026-11-08-*.json`）全部合法。
- 预注册 hash 三方一致：文件 `042776e8…96b843` == `run-manifest.json.prereg_sha256` == report `meta.prereg_sha256`。

### 3.5 证据 manifest ↔ 当前树

逐个重算 `sha256sum` 对照 `calibration/run-manifest.json::file_sha256`：

| 文件 | manifest | 当前树 | 结论 |
|---|---|---|---|
| `ReviewPlannerV2.kt` | `ed6ae4e5…` | `ed6ae4e5…` | ✅ |
| `AlgorithmConstants.kt` | `e695418f…` | `e695418f…` | ✅ |
| `ReviewPlanner.kt` | `62143174…` | `62143174…` | ✅ |
| `ClearlyMasteredForSkipPolicy.kt` | `3cb0af7c…` | `3cb0af7c…` | ✅ |
| `SchedulingEvaluation.kt` | `e0f3f26a…` | `e0f3f26a…` | ✅ |
| `ReviewPlannerScaleBenchmarkTest.kt` | `d72a2ed9…` | `d72a2ed9…` | ✅（金样未重生成） |
| `SimulatedLearner.kt` | `62d1eeac…` | `62d1eeac…` | ✅ |
| `StudyReviewPlannerService.kt` | `2bd8c40b…` | **`1cedc668…`** | ⚠️ 见 §5-F2 |

### 3.6 diff 卫生

```text
$ git diff --check -- <本批 28 个修改文件>   → 无输出，exit=0
```

行尾：`StudyProjectionDrainer.kt`、`StudySchedulingCalibration.kt` 为 CRLF（提交时 git 会
告警并归一为 LF，不阻塞）；新增代码文件（21 个）全部 LF、无行尾空白（逐文件二进制扫描：
`files 21 crlf [] trailing_ws []`）。新增 6 个 research 文档未做行尾扫描。

## 4. 提交清单

> 共享树，**禁止 `git add -A` / `git add .`**。按下列分组显式 `git add`。
> 机器可读 A 组：`docs/research/evidence/2026-11-08-batch-49/commit-manifest-add.txt`
> （含 `.mimosa` 排除 pathspec）。本会话实测：
>
> ```text
> $ git add --dry-run --pathspec-from-file=…/commit-manifest-add.txt
> exit=0   added=126   mimosa_hits=0
> ```
>
> 126 = 55 个显式代码/文档路径 + 71 个证据文件。证据树其余 22 个 `.log` 被 `.gitignore:31`
> 的 `*.log` 规则忽略（`git check-ignore` 命中），**不入库**；仓库当前 tracked `.log` = 0，
> 属既有惯例（见 §5-F10），不是本批新问题。

### A. 本批应提交（126 = 25 主代码 + 22 测试 + 2 工具/台账 + 6 research 文档 + 71 证据文件）

- **主代码（25）**：§1.3 中除测试、`tools/ci`、`docs/acceptance/ledger.md` 的全部路径；
  加上 §1.2 的 `SchedulingCalibrationView.kt`、feature/review 三个新生产文件、app 两个新生产文件。
- **测试（22）**：§1.2/§1.3 中全部 `src/test`、`src/androidTest` 路径（含模拟器 4 个）。
- **工具/台账（2）**：`tools/ci/acceptance_lint.py`、`docs/acceptance/ledger.md`（仅 E-061 一行）。
- **research 文档（6）**：`docs/research/2026-11-08-batch-49-baseline.md`、
  `...-prereg.md`、`...-report.md`、`...-report.json`、`...-calibration-method.md`、
  `...-clarification-ux.md`。
- **证据（71，可入库）**：`docs/research/evidence/2026-11-08-batch-49/` 下除 `.mimosa/**`
  （13）与被忽略的 `.log`（22）外的全部文件。

### B. 需本批负责人裁定（不要默认并入）

| 路径 | 说明 |
|---|---|
| `docs/2026-11-08-review-calibration-report.json` | **GBK 编码旧副本**（37435B，缺 `data_quality`），与 `docs/research/...-report.json`（39205B，UTF-8）双胞胎；报告正文只引用后者（`...-report.md:388,408`）。建议删除或重编码后再入库 |
| `docs/research/evidence/2026-11-08-batch-49/calibration/.mimosa/**`（13 个） | 已在 `.gitignore:29` 的 `.mimosa/` 规则内（`git check-ignore -v` 命中），`git add` 不会带入；manifest 仍保留 `:(exclude)` 作双保险。无需处理 |
| `tools/tests/test_acceptance_lint.py` | 唯一改动是 `subprocess` 的 `encoding="utf-8"`（3/3 行）；**本批 lint 前缀规则仍无自动化回归用例**（见 §5-F4）。归属未在证据中登记 |
| `docs/acceptance/evidence/2026-11-07-batch-48/`（整目录） | 批次 48 证据，非本批；是否随本批补提交需批次 48 owner 决定 |
| `docs/research/evidence/2026-11-08-batch-49/` 缺 `DIR-SHA256.txt`/`00-index.md` | 与批次 44/48 证据惯例不一致（见 §5-F7）；是否补由验收 owner 定 |

### C. 不得加入（他线在飞 / 垃圾）

- 他线修改：`app` 的 `ReviewReminderCoordinator.kt`、`ReviewReminderCoordinatorTest.kt`、
  `ReviewReminderPlatformInstrumentedTest.kt`、`MistakeExportNotificationsInstrumentedTest.kt`、
  `SavedMistakeSheetBytesTest.kt`、`visualjs/VisualContentMeasuringTest.kt`；
  `feature/review/.../KnowledgeReviewSessionViewModelTest.kt`；
  `feature/library/build.gradle.kts`；`core/database/{RoomStudyDatabase.kt,StudyDatabasePort.kt}`（PR#13）；
  B35/knowledge 线（`RoomTutorToolRunner.kt`、`KnowledgeContextRetriever.kt`、
  `BundledKnowledge*`、`Reviewed*`、`KnowledgeSearchIndex*`、`RoomKnowledge*` 等）；
  `.gitignore`、`docs/acceptance/blocked-runbook.md`（批次 48）、`docs/knowledge*`、
  `knowledge-production/**`、`feature/tutor/.../TutorSurfacePolicyTest.kt` 等。
- 他线新增：`app/src/{test,androidTest}/.../visualjs/*`、`core/data/src/main/.../telemetry/KnowledgeTelemetry.kt`、
  `core/data/src/test/.../{KnowledgeTelemetryWiringTest,ModelCallFailureRecordLogDisciplineTest}.kt`、
  `core/data/src/androidTest/.../{ops,telemetry,assets/backup}/`、`KnowledgeFirstQueryProbeInstrumentedTest.kt`、
  `tools/tests/fixtures/`、若干 `tools/ci/*.py` 等。
- 锁文件（14）与构建产物：`app/gradle.lockfile`、`core/{data,database,domain,export,markdown,model,ui}/gradle.lockfile`、
  `feature/{capture,library,profile,review,tutor}/gradle.lockfile`、`settings-gradle.lockfile`。
  `git ls-files '*gradle.lockfile'` = 0 → `check_lockfiles.py` 语义「锁尚未落地 → 放行」；
- 但 KD-72 结论（`docs/known-defects.md:2798`）要求：这批锁生成于他线未提交构建改动，
  **不得原样入库**。
- 垃圾/临时：`%TEMP%/`、`x`、`growth`、`out/`、`output/`、`tmp/`、`.v2c/`、`.agent_*`、`.tmp_*`、
  `audit_tmp/`、`crops/`、`Screenshot_*.png`、`feature/tutor/src/main/java/.../nul`（0 字节，
  Windows 保留名产物）等。

## 5. 发现（按严重度）

| # | 级别 | 发现 | 证据 |
|---|---|---|---|
| F1 | **中（提交前必处理）** | `docs/2026-11-08-review-calibration-report.json` 是 GBK 旧副本（解码失败），且与 `docs/research` 版本内容不同（少 `data_quality`）；直接提交会把一个非法 UTF-8 JSON 带进仓库 | §3.4；`sha256sum` 两文件不同（`ab505e00…` vs `86f137c3…`）；`python` 逐键对比 |
| F2 | 中（证据↔树漂移） | `StudyReviewPlannerService.kt` 在 P1 实验后又被 P2-14 改了 1 行（`reasons = scored.reasons`），当前 sha256 与 `run-manifest.json` / report `meta.file_sha256` 不符；而 report 里 `file_sha256_verified_in_report_session=true` 仍成立（写报告时点） | §3.5；`git diff` 该文件末 hunk；`p1-9-keep-current-values.md:82` 仍声称「同值」 |
| F3 | 信息（已缓解） | 证据树夹带 13 个 `.mimosa/**` hook 状态文件，但 `.mimosa/` 已在 `.gitignore:29` 忽略，`git add` 不会带入（`without_exclude=0` 实测）；manifest 的 `:(exclude)` 只是双保险 | `git check-ignore -v` 命中 `.gitignore:29`；`git add --dry-run -- <证据目录> \| grep -ci mimosa` → 0 |
| F4 | 低 | `acceptance_lint` 新前缀规则仍**无自动化回归测试**：`tools/tests/test_acceptance_lint.py` 只改了 3 行编码参数；规则只有一次样例探针（`lint-prefix/prefix-sample.lint.json`） | `git diff --stat tools/tests/test_acceptance_lint.py` → `3 insertions(+), 3 deletions(-)`；`tools/ci/acceptance_lint.py:112` |
| F5 | 低（跨线观察） | KD-67 生产调用 `selectKnowledgeReviewQuestion` 不传 `userRequestedQuestion=true`，选择器先返回 `QUESTION_NOT_REQUESTED`；因 `noCandidateDecision` 在存在未核验候选时仍升级为 `CLARIFY_OR_CONFIRM`，确认卡仍能出现，但决策原因里带了一个伪原因，且 ASK 分支在该调用上永不执行。P2 证据已登记此观察，未修 | `core/domain/.../AdaptiveQuestionSelector.kt:146-147`、`:283-296`；`p2-conflict-handling.md:72` |
| F6 | 低（证据过时） | `kd67-clarification-closure.md:155` 记录「app 单测源编译失败：Unresolved reference 'retentionFromSlider'」；当前树已由 P2-12 恢复该函数（`SchedulingSettingsScreen.kt:54`），静态看该失败已不成立（**编译未重跑**） | `kd67-clarification-closure.md:155`；`SchedulingSettingsScreen.kt:54`；`SchedulingRetentionSliderValueTest.kt` 4 例 |
| F7 | 低 | 证据树缺 `DIR-SHA256.txt`/`00-index.md`（批次 44 有 `DIR-SHA256.txt`、批次 48 有 `E-FILES-SHA256.txt`），104 个文件无整体完整性清单 | `ls` 对比 |
| F8 | 信息（证据过时） | `p1-9-keep-current-values.md:163` 称全树构建因他线 `RoutingEvalDeviceExportInstrumentedTest.kt:73` 语法错误 FAIL；该文件当前全仓不存在（定向 `find`/`grep` 只命中该证据文档）→ 该结论已不可复现 | 同上 |
| F9 | 信息 | `core:data` 全量 JVM 在 P2 证据里 `1690 tests / 4 failed`（P2 声称 4 条非本 ask）；本批「定向 0 红」不覆盖全量，门禁阶段需重跑并分类，不能把 4 红算成已解决 | `p2-conflict-handling.md:95` |
| F10 | 信息（既有惯例，影响证据链） | 证据树 22 个 `.log` 被 `.gitignore:31` 的 `*.log` 忽略，`git add` 静默跳过；仓库 tracked `.log` = 0（批次 44 台账同样引用未 tracked 的 `.cmd.log`）。→ 证据文档里的 `cmd:…/*.log` 目标不入库，复核者拿不到原始日志。本批沿用该惯例，未改策略 | `git check-ignore -v`（命中 `.gitignore:31`）；`git ls-files '*.log' \| wc -l` → `0`；`git add --dry-run` 126 vs 证据树 non-mimosa 93+显式 55 |

**正面确认（本会话实测）**

1. 本批 55 个显式提交路径（25 主代码 + 22 测试 + 2 工具/台账 + 6 文档）全部在 `git status` 命中；
   无「证据声称改了、树里没有」项。
2. `feature/review` 被占测试文件的他线 hunk（`Object*Stream` → `mapValues`）完整保留；
   本批未整文件改写它。
3. `app` 本批只触碰开工时干净的 3 个文件；他线提醒/visualjs 6 个文件 diff 无本批标记。
4. `RoomStudyDatabase.kt` 无本批内容、无新 DAO；`LearningDao` 方法签名未变。
5. 阈值/G6/VERSION/金样均未变；权重只加默认等值 seam。
6. 21/22 JSON 合法；预注册 hash 三方一致。

## 6. 未验证 / 未做（不得读成已通过）

1. **未跑 Gradle**：本批任何编译、单测、仪器化、门禁、强门、`acceptance_report` 均未由本 ask 执行；
   文中引用的 BUILD SUCCESSFUL / 测试计数均来自证据文件自述，未经复核。
2. **未连设备/adb**：确认卡、校准页、冲突卡、重整理、soak 的设备证据未复核。
3. **未验证批次单独提交后的可编译性**：本 ask 只做静态检查；「HEAD + 本批 A 组」是否独立编译
   需门禁阶段实跑（本批代码不依赖 C 组他线改动，但这是静态推断）。
4. **A 组隐藏文件**：已用 `git add --dry-run` 验证证据目录实际入库 71 个文件，`.log`（22）与
   `.mimosa`（13）都被忽略规则挡下；但若后续有人改 `.gitignore`，同一 manifest 的 dir 展开面
   会变化（本 ask 未锁 `.gitignore`）。
5. **未验证 F4 的规则在真实台账上的误报/漏报**（基线曾实跑 144 不变；本 ask 未复跑 lint）。
6. **未验证证据 `.log` 在提交后是否可获取**：22 个 `.log` 默认被 `*.log` 忽略，不会随
   `commit-manifest-add.txt` 入库；若验收要求原始日志入库，需 owner 决定 `git add -f` 或
   改 `.gitignore`（本 ask 不改仓库策略）。
7. **未对新增 research 文档做事后校对以外的检查**（只验证了引用路径与 JSON 合法性）。
