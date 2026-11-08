# 证据：KD-67 第二道校准题（候选池 / 单槽限制）+ 真 Room 端到端（批次 50）

| 字段 | 值 |
|---|---|
| 计划条目 | `.zcode/plans/plan-2026-11-09-batch-50-release.md:65`（产品面 32「KD-67 第二道校准题」） |
| 前置登记 | `docs/acceptance/adjudication.md` §B49-6（第二道校准题未实现：候选池只有本会话 FOUNDATION 题、`(会话,知识点)` 单槽）；`docs/research/evidence/2026-11-08-batch-49/repair/unit-gate-and-kd67-blockers.md` §3（真 Room e2e 未跑） |
| 角色 | feature/review 工程师（实现 + 定向验证）；**未改门阈值、未放宽断言** |
| 工作目录 | `D:/smart mistake book`（Git Bash / win32） |
| 证据根 | `docs/research/evidence/2026-11-09-batch-50/kd67/` |
| 设备 | `emulator-5590`（x86_64 / API 34 模拟器，**非真机**） |

---

## 1. 落地了什么（两条被点名的阻塞逐条闭合）

| 阻塞（原文） | 闭合方式 | 文件 |
|---|---|---|
| **候选池只有本会话已落库的 FOUNDATION 题** | 装配器对每个目标节点读**两个槽位**：会话题面（`REVIEW`，仍标 `FOUNDATION`，既有行为逐字不变）与第二道题（`CALIBRATION`，标 `AdaptiveTargetKind.CALIBRATION`）→ 选择器既有的 `selectCalibration` 首次有可选题面。未核验的第二道题仍 `contentVerified=false`（**不因"学生点过按钮"放行**） | `core/data/.../study/AdaptiveSelectionAssembler.kt` |
| **`(会话, 知识点)` 单槽不允许第二道** | 题面行身份增加**槽位分量**：`knowledge-quiz-item-calibration:<learner>:<会话>:<节点>`（会话题面身份 `knowledge-quiz-item:...` 逐字不变）。第二道题题面 id = `knowledge-quiz:calibration:<节点>`（**与会话题面 id 不同**：选择器要求候选 id 唯一；答案键更正前缀按题面 id 分流）。store 增加槽位读/写、按题面 id 读；upgrader 的 CAS 增加槽位（默认 `REVIEW`，既有确认流不变）；repository 增加第二槽 persist/read/submit | `KnowledgeQuizItemStore.kt`、`KnowledgeQuizItemVerificationUpgrader.kt`、`KnowledgeQuizFeedbackWriter.kt`、`RoomBackedStudyExperienceRepository.kt`、`core/domain/.../StudyExperienceRepository.kt` |

新增接口都是**默认实现/默认参数**（`REVIEW`、`null`、`UnsupportedOperationException`），
既有实现方/替身不需要改签名、既有行身份与既有读口行为逐字不变。

## 2. 先红后绿（本会话实跑）

### 2.1 红（行为级，先写测试）

```text
$ ./gradlew :core:data:testDebugUnitTest --tests "…KnowledgeCalibrationQuestionTest" --console=plain
KnowledgeCalibrationQuestionTest > the calibration corridor selects the second question only after its own row is verified FAILED
    java.lang.AssertionError: 确认后的第二道校准题必须能被校准走廊选中 expected:<ASK_CALIBRATION> but was:<CLARIFY_OR_CONFIRM>
KnowledgeCalibrationQuestionTest > assembled candidates expose the persisted second question as a calibration candidate FAILED
    java.lang.AssertionError: 同一知识点必须装配出两道题（会话题面 + 第二道校准题）
      expected:<[knowledge-quiz:item:kc-calib, knowledge-quiz:calibration:kc-calib]>
        but was:<[knowledge-quiz:item:kc-calib]>
2 tests completed, 2 failed
```

> 如实边界：本次红是**行为断言失败**（不是编译红）。为了让"身份格式"这一契约先可表达，
> 红跑前已落两个纯命名函数（`knowledgeQuizCalibrationItemId` / 槽位 id），行为实现为零；
> 其余 3 例（槽位独立落库、逐槽 CAS、按题面 id 判分）在契约引入前不可表达，按**编译红**
> 计入，未伪称行为红。红跑输出为本会话终端抄录（未 tee 到文件）。

### 2.2 绿（JVM，实跑）

```text
$ ./gradlew :core:data:testDebugUnitTest --tests "…KnowledgeCalibrationQuestionTest" --console=plain
BUILD SUCCESSFUL
→ XML: tests=5 skipped=0 failures=0 errors=0   （jvm-KnowledgeCalibrationQuestionTest.xml）
```

5 例覆盖：两槽各自落库且互不覆盖 / 按题面 id 定位槽位（对不上 = null，不拿另一道题顶替）/
第二道题标 `CALIBRATION` 且未核验 fail-closed / 校准走廊只在**自己那一行**核验后选中它 /
逐槽 CAS（升级第二槽不改第一槽）/ 作答第二道题按**自己的键**判分（不是会话题的键）。

### 2.3 相关类回归（实跑）

```text
$ ./gradlew :core:data:testDebugUnitTest --tests …AdaptiveSelectionAssemblerTest
    --tests …KnowledgeQuizFeedbackWriteTest --tests …KnowledgeQuizEvidenceAuditTest
    --tests …KnowledgeQuizItemStoreTest --tests …KnowledgeQuizItemStoreVerificationStatusTest
    --tests …KnowledgeQuizItemVerificationUpgraderTest --tests …KnowledgeCalibrationQuestionTest
BUILD SUCCESSFUL → 55 tests / 0 failures / 0 errors
```

### 2.4 全模块回归（实跑，如实保留既有红）

```text
$ ./gradlew :core:data:testDebugUnitTest --console=plain
BUILD FAILED（既有红，非本项引入）→ 1707 tests / 4 failed / 2 skipped
4 红逐名（与批次 49 基线同一组）：
- OpenAiCompatibleModelGatewayTest ×3（ModelEgressAuthorizationException：模型能力未验证）
- RoomBackedStudyExperienceRepositoryTest（expected:<[knowledge:m1:math.read-monotonicity-from-graph]> but was:<null>）
```

> 计数基线：批次 49 记录为 1690/4/1；本次 1707 的 +12 来自他线在本工作树新增的测试
> （3E/5c/6B-3 等），+5 为本项新增。**失败集合与基线逐字相同**，无新增红。

### 2.5 编译面（实跑）

```text
$ ./gradlew :app:compileLocalFirstDebugKotlin :feature:review:testDebugUnitTest --console=plain
BUILD SUCCESSFUL → feature:review 116 tests / 0 failures / 0 errors（XML 聚合）
```

## 3. 真 Room 端到端（本会话设备实跑）

```text
$ ./gradlew :core:data:assembleDebugAndroidTest --console=plain          → BUILD SUCCESSFUL（build-androidtest.txt / build-final.txt）
$ sha256sum core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk
ba005aa706d5ad039be5e7ab58ff268c551946b1d522195d64a534c8a080629b
$ adb -s emulator-5590 install -r -t core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk
Success
$ adb -s emulator-5590 shell am instrument -w -e class \
    com.tingyun.smartmistakebook.core.data.study.KnowledgeCalibrationQuestionRoomE2eInstrumentedTest \
    com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner
OK (3 tests)   Time: 1.203
```

> 上表是**最终源码快照**（含最后一处 KDoc/未用 import 清理）的构建与设备读数。更早一轮
> 同用例 `OK (3 tests)`（APK `86496f1a…`，Time 1.19）只用于确认设备路径可达；两者
> 失败/通过集合一致，证据以本表为准。

原始输出：`e2e-device.txt`。3 例逐条覆盖（**只走生产写路径**，不手改 `verification_status` 造绿）：

1. **会话题面链**：`submitKnowledgeQuizFeedback`（键未核验 → 观察行）→ `AnswerKeyCorrectionWriter`
   用户断言（真实账本事件）→ `KnowledgeQuizItemVerificationUpgrader` 单列 CAS →
   **下次装配 `contentVerified=true`**；第二槽仍 `UNKNOWN`（逐槽）。
2. **第二道校准题链**：持久化到独立槽位（两槽各自读回）→ 确认前
   `selectKnowledgeReviewQuestion(CALIBRATION, userRequestedQuestion=true)` 必须停在
   `CLARIFY_OR_CONFIRM` → `submitKnowledgeCalibrationQuizFeedback(itemId=第二道题)` 按**自己的键**
   判分 → 账本断言 → CAS（`slot=CALIBRATION`）→ 下次装配 `contentVerified=true` 且
   `targetKind=CALIBRATION` → 校准走廊返回 **`ASK_CALIBRATION` 且选中第二道题**；
   决策的 `projectionCheckpointSequence ≥ 1`（证明控制面来自真投影而非空快照）。
3. **反例（同一真库）**：不带题面 id 的旧读口把第二道题的作答拿会话题的键判 →
   `ANSWER_KEY_VERSION_STALE`（误报）；带题面 id 的新读口 → `ANSWER_KEY_UNVERIFIED`
   （只落观察行）。这钉住"按题面 id 定位槽位"不是可选优化。

> 构建设障（如实）：androidTest 方法名初版用反引号含空格，D8 报
> `Space characters in SimpleName ... are not allowed prior to DEX version 040`；
> 改成 camelCase 后通过（与既有仪器化用例命名一致）。

## 4. 未改的东西（硬约束核对）

- 掌握阈值（21 天 / 0.9）、G6 幅度、选题权重、`ReviewPlannerV2.VERSION`：**零改动**。
- `contentVerified` / `safeBaseCandidates` / 走廊判据：**零放宽**；未核验的第二道题进不了任何 ASK。
- 既有会话题面的行身份、读口、CAS 默认行为：逐字不变（默认参数 `REVIEW`）。
- 金样 / 台账 / 验收行：未改（本项只落代码 + 证据）。

## 5. 未完成 / 残余（不签"第二道校准题已产品闭环"）

1. **第二道题的"生成 + 上屏 + 作答"还没有生产入口**：本项闭合的是**数据/选择面**
   （第二槽、候选池、逐槽核验、按题面 id 判分与确认）。学生点「先做校准题」后，屏幕仍只显示
   选择器决策文本（`knowledgeCalibrationStatusText`），不会真的生成/展示第二道题。缺的是：
   - 生成：`KnowledgeReviewQuizLoader` 需要一个第二题入口（题面 id 用 `knowledgeQuizCalibrationItemId`，
     现有 `loadQuiz` 固定 `knowledge-quiz:item:<节点>`）；
   - 上屏/作答：`KnowledgeReviewSessionViewModel` / `KnowledgeReviewSessionScreen` /
     `SmartMistakeBookRoot` 的 K5 动作需要"取第二题 → 作为 currentItem → 作答提交 →
     确认 → CAS(slot=CALIBRATION)"状态流；`submitKnowledgeCalibrationQuizFeedback` 已有生产实现，
     但 UI 尚未调用。
   这些文件在批次 49/他线改动面上（`git status` 为 M/未跟踪），本项按协作规则**不整文件抢占**。
2. **Elo 选项数解析仍读会话题面槽**：`AdaptiveSelectionAssembler.effectiveQuizStream` 按
   `(会话, 节点)` 读选项数；第二道题的作答事件若选项数与会话题面不同，预测输入的 guess 修正
   会略偏。不影响任何 fail-closed 判据与 CALIBRATION 选择（校准走廊不看预测区间），登记为精度残余。
3. **证据审计面**：`KnowledgeQuizEvidenceAudit` 仍按 `(会话, 节点)` 映射到会话题面槽；
   第二道题的证据行会显示成"配到第一道题"。只影响审计展示，不影响判分/核验。
4. **更正写口的兼容回退**：`AnswerKeyCorrectionWriter` 在"命令里的题面 id 两槽都解析不到"时
   仍回退读会话题面槽（保持既有行为）。正常路径（第二道题存在且已上屏）不会走到；
   若将来要求更强 fail-closed，可改为 `ANSWER_KEY_ITEM_UNREADABLE` 硬拒（会改变既有边界）。
5. **未跑**：设备 UI 旅程（第二道题的上屏/作答）、`acceptance` 行、2h soak、真机（仅模拟器）。
