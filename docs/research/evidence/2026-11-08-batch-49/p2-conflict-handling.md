# 批次 49 · P2-13/14 冲突处置证据（2026-11-08）

> 执行角色：core:data 工程师（本 ask 只做四件事：`reprojectAll()`、投影 `CONFLICTED` 专用
> 状态/原因/「重新整理」、冲突 KC 卡两动作、三类测试）。**未改**掌握阈值（21 天/0.9）、
> G6/权重/VERSION/金样/台账 verdict；**未编辑** `core/database` 下任何文件（`RoomStudyDatabase.kt`
> 零改动，不新增 DAO 方法）。
>
> 本文件所有命令均为本会话实跑；结果计数取自 `build/test-results/**/TEST-*.xml` 原文。

## 1. 热区登记（开工前逐文件 `git status`）

| 文件 | 开工时状态 | 本 ask 的改动 | 处理 |
|---|---|---|---|
| `core/domain/.../StudyExperienceRepository.kt` | **M（他线 P2-11/12 在飞）** | 新增 `StudyProjectionIssue`、快照 `projectionIssue` 字段、`reprojectAll()` 默认抛错、`selectKnowledgeReviewQuestion` 两个默认参数 | 只做**追加**，未改他线新增的 KD-67/校准页方法 |
| `core/data/.../RoomBackedStudyExperienceRepository.kt` | **M（他线 KD-67/校准页在飞）** | `reprojectAll()`、`publishFailure` 投影级映射、`selectKnowledgeReviewQuestion` 透传 corridor | 只做追加/局部替换，未动他线新方法实现 |
| `core/data/.../StudyReviewPlannerService.kt` | **M（他线 P0 预算在飞）** | 队列项多传 `reasons = scored.reasons`（1 行） | 未动预算解析 |
| `feature/review/.../KnowledgeReviewSessionScreen.kt` | **M（他线 KD-67 在飞）** | 新增参数 `onRequestCalibrationQuiz`、节点标题下插入冲突卡槽与"本次按校准题处理"标记 | 只做插入，未动 KD-67 确认卡块 |
| `app/.../SmartMistakeBookRoot.kt` | **M（他线校准页在飞）** | 新增 import、`StudyDataStatusLine` 增加 `suppressGenericError`、插入投影状态卡、KD-67 屏调用追加校准回调 | 只做局部插入，未动他线校准页/其余路由 |
| `core/domain/.../KnowledgeReviewQueue.kt` | 干净 | 队列项新增 `reasons`/`isConflicted` | 本 ask 独占 |
| `core/data/.../StudyProjectionDrainer.kt` | 干净 | 投影级异常类型、GAP/CONFLICT/增量拒绝分类、`reproject()` | 本 ask 独占 |
| `core/data/.../StudySnapshotBuilder.kt` | 干净 | `check` → 带状态的投影级异常 | 本 ask 独占 |
| `feature/review/.../KnowledgeReviewSessionViewModel.kt` | 干净 | 队列序列化 v2（+reasons）、冲突卡状态机 | 本 ask 独占 |
| `feature/review/.../KnowledgeConflictCard.kt` | 新建 | K5 卡模型 + Composable | 本 ask |
| `app/.../StudyProjectionIssueBanner.kt` | 新建 | 投影状态卡文案 + Composable | 本 ask |

## 2. 落点与"消灭的具体失败"

### 2.1 投影级（P2-13）

1. `StudyProjectionIssue(status)`（`core/domain/StudyExperienceRepository.kt`）+ 快照
   `projectionIssue`：**消灭**"投影撞上账本冲突/缺口时读面只剩通用 ERROR + 英文
   `failureMessage`，学生分不清是投影对不上还是数据库打不开，也没有任何修复出路"。
2. `StudyProjectionUnavailableException(status, message)` + 分类
   （`StudyProjectionDrainer.kt`）：
   - 增量批次 `GAP` → `WAITING_FOR_GAP`；`CONFLICT` → `CONFLICTED`（原先两者共用
     `LearningLedgerIntegrityException`，读面无法区分）；
   - `commitFullReplay` 的账本读状态 `GAP/CONFLICT` 分别映射（原先一律同一异常）；
   - 增量投影拒绝"数据库校验过的前缀"：`snapshot.projectionStatus == CONFLICTED` /
     conflicted ids → `CONFLICTED`，`missingSequence`/deferred → `WAITING_FOR_GAP`
     （原先 `check(...)` 抛通用 `IllegalStateException`）；
   - `StudySnapshotBuilder` 的 stale/incomplete 门同样带状态（CURRENT 但 STALE 归
     `CATCHING_UP`，不冒充冲突）。
3. `RoomBackedStudyExperienceRepository.publishFailure`：只对上述投影级异常与
   `ProjectionCasConflictException`（CAS 重试耗尽 = 投影无法收敛）设置专用状态；
   **保留**上一次的专用状态（后续无关写失败不能抹掉唯一修复出口）；普通 IO 失败
   **不**冒充冲突（有用例钉住）。
4. `reprojectAll()`：`StudyExperienceRepository` 默认实现抛
   `UnsupportedOperationException`（旧替身不得静默假成功）；RoomBacked 委托
   `StudyProjectionDrainer.reproject()`——**强制**从不可变账本全量重放，不看增量停止
   原因，CAS 冲突重读重试；成功后照常装配并发布 READY 快照。**零新增 DAO 方法**。
5. `StudyProjectionIssueBanner.kt`：按状态给**不同**学生原因句（冲突=矛盾记录、
   缺口=缺一段、追赶=还没追上）、唯一动作「重新整理」、后果句"不会删除任何作答"，
   失败态必须说"没有改变"。Root 在投影专用状态存在时压制通用错误行。

### 2.2 知识点级（P2-14）

1. `KnowledgeReviewQueueEntry.reasons`（默认空集）+ `isConflicted`：触发判据与排程侧
   加分判据同源（`KnowledgeNodeScorer.masteryRiskFor` 的 `CONFLICTED_KNOWLEDGE`）。
   **消灭**"冲突知识点在会话里与普通点完全一样，卡片只能靠 masteryScore 阈值另猜"。
2. `KnowledgeReviewPlannerService` 把 `scored.reasons` 带进队列项（1 行）。
3. `KnowledgeConflictCard.kt`：K5 纯函数模型（2 动作、必选 0、默认「仍然复习」）+
   Composable；`CALIBRATION_CHECK` 单独出现**不**触发（防止每节点弹卡）。
4. ViewModel：`showConflictCard` / `calibrationRequested` / `startCalibration()` /
   `dismissConflictCard()`；队列序列化 v2（第 6 列 reasons）+ 两个持久化集合
   （已处理节点、校准意图节点）。卡**不参与**取题/作答/前进判据（0 必选）。
5. 「先做校准题」→ `selectKnowledgeReviewQuestion(requestedTargetKind = CALIBRATION,
   userRequestedQuestion = true)`：走选择器已有的 CALIBRATION 走廊（`ASK_CALIBRATION`），
   不新建出题路径、不硬排除节点；屏幕显示"本次按校准题处理"让选择可见。

> 观察登记（不属本 ask，未改）：KD-67 生产调用 `selectKnowledgeReviewQuestion(...)`
> 目前不传 `userRequestedQuestion`（默认 false），选择器会对它一律返回
> `QUESTION_NOT_REQUESTED`。本 ask 仅给"先做校准题"显式传 true；KD-67 那条线的
> 诊断/确认路径是否需要 true，由该线裁定。

## 3. 验证（本会话实跑）

### 3.1 定向测试

| 命令 | 结果 |
|---|---|
| `./gradlew :core:data:testDebugUnitTest --tests "com.tingyun.smartmistakebook.core.data.study.ProjectionConflictReprojectTest" --console=plain` | **BUILD SUCCESSFUL**；XML `tests=8 failures=0 errors=0 skipped=0` |
| `./gradlew :feature:review:testDebugUnitTest --tests "...KnowledgeConflictCardTest" --tests "...KnowledgeConflictSessionStateTest" --tests "...KnowledgeReviewSessionViewModelTest" --console=plain` | **BUILD SUCCESSFUL**；卡 `tests=5`、状态机 `tests=6`、既有 VM `tests=23`，全部 `failures=0 errors=0` |
| `./gradlew :app:testLocalFirstDebugUnitTest --tests "com.tingyun.smartmistakebook.StudyProjectionIssueCopyTest" --console=plain` | **BUILD SUCCESSFUL**；`tests=3 failures=0 errors=0`（同时验证 Root/屏/banner 编译） |

上述 5 份 XML 原文已复制到本目录 `p2-conflict/`：
`01-projection-conflict-reproject.xml`（8）、`02-conflict-card-contract.xml`（5）、
`03-conflict-session-state.xml`（6）、`04-existing-session-vm-regression.xml`（23）、
`05-projection-issue-copy.xml`（3）。

### 3.2 模块套件（回归）

| 命令 | 结果 |
|---|---|
| `./gradlew :core:domain:testDebugUnitTest :feature:review:testDebugUnitTest --console=plain` | **BUILD SUCCESSFUL**；`core:domain` 740 tests / 0 failures（1 skipped）、`feature:review` 101 tests / 0 failures |
| `./gradlew :core:data:testDebugUnitTest --console=plain`（全量） | **BUILD FAILED**：`1690 tests completed, 4 failed, 1 skipped`。4 条均**非本 ask 改动**（见 §5） |

### 3.3 红 → 绿记录（如实）

- 测试文件先写；首次可执行状态里 `calibrationIntentRoutesThroughTheCalibrationCorridor`
  **红**（7 例中 1 失败：选择器在 `userRequestedQuestion=false` 时提前返回
  `QUESTION_NOT_REQUESTED`，CALIBRATION 意图未进走廊）→ 显式传 `userRequestedQuestion=true`
  后 **7/7 绿**。
- 后续新增增量冲突用例后 **8/8 绿**。
- `StudyProjectionIssueCopyTest` 首跑 **1 红**（缺口状态的后果句缺"不会删除作答"）→
  改产品文案（不放宽断言）→ **3/3 绿**。
- **未捕获首红**：`KnowledgeConflictCardTest` / `KnowledgeConflictSessionStateTest`
  在首跑前实现已随设计文档落地（首跑即绿）。这两组测试的"红先于绿"只有静态意义上的
  先后（先写测试文件），没有失败日志。**如实登记为证据缺口**，不以编译红冒充行为红。

## 4. 未验证 / 未做

1. **设备/仪器化用例未跑**：冲突卡真点击、投影状态卡"重新整理"端到端、无障碍语义树、
   截图、2h soak 均 **NOT RUN**（本 ask 未接设备；计划 §P3 归验收阶段）。
2. **app 全量单测未跑**（只跑了本 ask 定向类）；`:app:compileStrictOffline*` 未单独编译
   （`compileLocalFirstDebugKotlin` 已绿，源码同一份）。
3. `acceptance_report` / E/F/A 行 / 25 份失效复核 / 台账 verdict **未动**（计划 P3 项）。
4. `core:data` 全量套件的 4 条失败未修（见 §5），本 ask 不改判据让其变绿。

## 5. 全量套件既有失败（非本 ask 改动，如实登记）

`./gradlew :core:data:testDebugUnitTest` → `1690 tests completed, 4 failed, 1 skipped`：

1. `OpenAiCompatibleModelGatewayTest` 3 例（`unverifiedEditCapability…` /
   `changedEditSourceFingerprint…` / `mistakeEditMockServer…`，均在
   `ModelEgressAuthorizationException` 转换处失败）。本 ask 的 diff **不含** `core/model`、
   `core/data/model` 任何文件；失败点与投影/知识点冲突无关。
2. `RoomBackedStudyExperienceRepositoryTest.profileStrengthsDropForgottenAndPrerequisiteSuppressedNodes`
   （`RoomBackedStudyExperienceRepositoryTest.kt:1472`，expected `{PREREQ_NODE_ID}`，actual null）。
   **静态证明非本 ask 引入**：`git show HEAD:core/data/.../StudyExperienceMappers.kt |
   grep -c knowledgePrerequisites` = **0**，即 HEAD 的 `toProfileOverview` 从不给
   `StudyProfileOverview.knowledgePrerequisites` 赋值；该测试也在 HEAD 存在。本 ask 未改
   `StudyExperienceMappers.kt`（`git diff --stat` 为空），也未改该测试。

## 6. 残余风险

- 「先做校准题」在**当前产品形态**下切换选择走廊，并由会话状态机**消费选择器决策**：
  `ASK_CALIBRATION` 显示"已选中校准题面（id）"，未选中显示"选择器未给出校准题（原因）"，
  失败显示"校准选择没有完成"（修复环补，JVM 5 例 + 设备 2 例）。知识点复习题仍由
  `KnowledgeReviewQuizLoader` 现场生成，**决策不驱动换成另一道题**；卡片旧文案
  「校准题只多问一道」已删除。若要"校准题=真正多出一道题"，现有候选池没有 CALIBRATION
  候选题面，且 `(会话, 知识点)` 单槽持久化不允许第二道题——需要 KD-67 候选池/持久化改动，
  超出本批热区，登记为阻塞（`repair/README.md §2`）。
- 投影专用状态只在"投影/账本无法收敛"时出现；**账本完整**的自报 `CONFLICTED` 会被
  `drain()` 自动全量重放**直接修好**（不弹卡）。这是刻意行为：能自愈的冲突不该打扰学生。
- 冲突卡/投影卡的文案为学生可读句；修复环已补 `a11y_audit` 结构检查（STRUCTURAL=PASS，
  可点击节点 ≥48dp 且有朗读文本），**对比度仍 UNVERIFIED**（无颜色输入，不冒充通过）；
  大字号几何未测。
