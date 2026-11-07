# 证据：KD-67 确认卡 / 澄清消费者 + 核验写路径闭合（批次 49 · P2-11）

| 字段 | 值 |
|---|---|
| 落盘 | `docs/research/evidence/2026-11-08-batch-49/kd67-clarification-closure.md` |
| 计划条目 | `.zcode/plans/plan-2026-11-08-mastery-review-quality.md:27`（P2-11 KD-67）与本次 ask 1–4 |
| 角色 | feature/review 工程师（实现 + 定向验证）；**未连设备、未跑 connected 测试、未改台账/金样** |
| 工作目录 | `D:/smart mistake book`（Git Bash / win32），HEAD `ba7b5624` |
| 结论口径 | 下文每个「绿」都对应本会话实跑命令与退出结果；未跑项单列 §6 |

---

## 1. 开工前热区逐文件核对（计划硬约束）

```text
$ git status --porcelain -- feature/review app/src/main/kotlin/com/tingyun/smartmistakebook
 M feature/review/src/test/.../KnowledgeReviewSessionViewModelTest.kt     ← 他线（SavedState 序列化夹具）
?? feature/review/gradle.lockfile                                          ← 他线锁文件
 M app/src/main/kotlin/com/tingyun/smartmistakebook/ReviewReminderCoordinator.kt  ← 他线（提醒）
```

- 本项要改的 `KnowledgeReviewSessionScreen.kt`、`KnowledgeReviewSessionViewModel.kt`、
  `KnowledgeReviewSessionViewModelFactory.kt` 与 `app/.../SmartMistakeBookRoot.kt` **均干净**。
- 他线占用的 `KnowledgeReviewSessionViewModelTest.kt` **未改一个字符**；新增测试放在**新文件**
  `KnowledgeClarificationPresenterTest.kt`（与基线 §5.1「新确认卡测试建议新建独立文件」一致）。
- core/data 的 `KnowledgeQuizItemStore.kt` / `KnowledgeQuizItemVerificationUpgrader.kt` /
  `RoomBackedStudyExperienceRepository.kt` 与 core/domain 的 `StudyExperienceRepository.kt`
  开工前 `git status` 为空（基线 §5.5 同结论）。
- **未新增 DAO 方法**（`RoomStudyDatabase.kt`、`LearningDao.kt` 一行未改）。

## 2. 改动清单（路径 + 关键位置）

| 文件 | 变更 | 消灭的具体失败 |
|---|---|---|
| `feature/review/src/main/kotlin/.../KnowledgeClarificationPresenter.kt`（新） | `KnowledgeClarificationPresenter : AdaptiveClarificationConsumer`（StateFlow 状态机、请求键幂等、确认忙态/失败可重试）、`KnowledgeClarificationState`、`KnowledgeClarificationOutcome`、`knowledgeClarificationCardModel` / `knowledgeClarificationReasonLines` / `isAnswerKeyConfirmationAllowed` | ① `CLARIFY_OR_CONFIRM` 决策算出来就丢、全仓无实现方；② 同一决策重放重置卡片/冲掉忙态；③ 非"键未核验"原因下"确认答案"不是解法（选择器 `noCandidateDecision` 对所有无候选原因都返回 CLARIFY，见 `docs/research/2026-11-08-clarification-ux.md` §2.4） |
| `feature/review/src/main/kotlin/.../KnowledgeClarificationCard.kt`（新） | 确认卡 Composable：原因行（人话）、候选列表（显示名，不上屏内部 id）、确认 / 换一道题 / 取消、busy/error、`knowledge_clarification_*` testTag | 学生没有任何确认/换题/取消出口；确认只能落在**屏幕上这道题**（错题行候选无键面，点了只会 `SnapshotNotFound`） |
| `feature/review/src/main/kotlin/.../KnowledgeReviewSessionScreen.kt` | 新增可选参数 `clarificationState` / `onClarificationConfirm` / `onClarificationNewQuestion` / `onClarificationCancel`（默认 Hidden + no-op，未接线调用方行为不变）；卡片渲染在题面下、提交按钮上 | 会话内没有卡片挂点 |
| `core/data/src/main/kotlin/.../KnowledgeQuizItemStore.kt` | `record(...)` / `save(...)` 增加 `verificationStatus`（默认 `UNKNOWN`）；KD-41 损坏行重写**保留**库内既有核验状态；新增 `knowledgeQuizAnswerSpecOf`（键载荷解码单源） | 唯一生产写方永远只能写 `UNKNOWN`（调用方知道已核验也写不进去）；重试重写坏行会把已确认状态静默降回 `UNKNOWN`（学生刚确认过又被问一遍） |
| `core/data/src/main/kotlin/.../KnowledgeQuizItemVerificationUpgrader.kt` | 新增 `LedgerAnswerKeyVerificationSourceVerifier`（生产来源校验：账本里真实存在、断言键 = 题面当前键、未被撤销/替换；`IndependentVerification` 无生产记录源 → 恒 false） | `AnswerKeyVerificationSourceVerifier` 只有测试实现 → 生产确认要么自造 id 绕过、要么永远 `SOURCE_NOT_VERIFIED`（点了没反应） |
| `core/data/src/main/kotlin/.../RoomBackedStudyExperienceRepository.kt` | 实现 `selectKnowledgeReviewQuestion`（`AdaptiveSelectionAssembler.selectAndConsume`，快照 = 既有 `currentLearnerSnapshot()`） | 装配器没有任何生产调用点（基线 §5.5：grep 只命中自身与测试） |
| `core/domain/src/main/kotlin/.../StudyExperienceRepository.kt` | 新增默认实现 `selectKnowledgeReviewQuestion(...): AdaptiveDecision? = null` | 生产装配点需要一个既有读面可用的 seam；默认 null 让旧替身编译不破且不假装选择成功 |
| `app/src/main/kotlin/.../SmartMistakeBookRoot.kt` | 复习路由创建 presenter/upgrader；`restoreQuiz`/`loadQuiz` 落库后调用 `selectKnowledgeReviewQuestion`；确认回调走「账本用户断言记录 → 单列 CAS 升级 `USER_ASSERTED`」；换题/取消关卡片；拒绝/异常回卡片并 Toast | 确认流没有生产调用点（KD-67 ①③⑤）；CLARIFY 在生产里无人消费 |
| `feature/review/src/test/.../KnowledgeClarificationPresenterTest.kt`（新） | 13 例（见 §3） | 状态机无测试 |
| `core/data/src/test/.../KnowledgeQuizItemStoreVerificationStatusTest.kt`（新） | 2 例 | store 状态写入/保留无测试 |
| `core/data/src/test/.../KnowledgeQuizItemVerificationUpgraderTest.kt` | 追加 4 例（生产来源校验：真回执 / 伪造 / 断言别的键 / 被撤销 / 独立核验不可达） | 生产 verifier 无测试 |

**未改**：掌握阈值（21 天/0.9）、G6、选题权重、`ReviewPlannerV2.VERSION`、任何金样、台账、`RoomStudyDatabase.kt`/`LearningDao.kt`。→ 无需 VERSION bump。

## 3. 先红后绿（逐条命令 + 实跑输出）

### 3.1 feature/review 状态机（红 → 绿）

```text
# 红（实现前，只写了测试）
$ ./gradlew :feature:review:testDebugUnitTest --tests "…KnowledgeClarificationPresenterTest" --console=plain
e: …KnowledgeClarificationPresenterTest.kt:158:25 Unresolved reference 'KnowledgeClarificationPresenter'.
e: …:178:21 Unresolved reference 'knowledgeClarificationCardModel'.
… 39 actionable tasks: 1 executed, 36 up-to-date
BUILD FAILED in 5s
```

```text
# 绿（实现后，含后续三条新门控）
$ ./gradlew :feature:review:testDebugUnitTest --tests "…KnowledgeClarificationPresenterTest" --console=plain
BUILD SUCCESSFUL
→ XML: tests="14" skipped="0" failures="0" errors="0"
```

覆盖：打开卡片（候选+原因，2 例）、未知原因码不泄漏、已知原因逐条人话、已解决请求重放不复活、
忙态重放不冲掉、候选不在列表拒绝、**非键未核验原因拒绝确认**、键未核验可确认、失败回卡片可重试、
不同决策可再打开、取消不伪装成确认、**确认只落在屏幕上这道题**（旧候选/无题面/非键原因三种拒绝）、
selectorVersion 暴露、StateFlow 可观察。

### 3.2 feature/review 全模块（不回归）

```text
$ ./gradlew :feature:review:testDebugUnitTest --console=plain
BUILD SUCCESSFUL
→ 11 个测试类，90 tests，0 failures / 0 errors（本会话 python 逐 XML 汇总；newest 23:44:31）
```

### 3.3 core/data store 状态写入（红 → 绿）

```text
# 红（实现前）
$ ./gradlew :core:data:testDebugUnitTest --tests "…KnowledgeQuizItemStoreVerificationStatusTest" --console=plain
e: …KnowledgeQuizItemStoreVerificationStatusTest.kt:70:13 No parameter with name 'verificationStatus' found.
BUILD FAILED in 10s
```

```text
# 绿（实现后；连带既有 store 用例）
$ ./gradlew :core:data:testDebugUnitTest --tests "…KnowledgeQuizItemStore*" \
    --tests "…KnowledgeQuizItemVerificationUpgraderTest" --tests "…AdaptiveSelectionAssemblerTest" --console=plain
BUILD SUCCESSFUL in 13s
→ AdaptiveSelectionAssemblerTest 12/0、KnowledgeQuizItemStoreTest 8/0、
  KnowledgeQuizItemStoreVerificationStatusTest 2/0、KnowledgeQuizItemVerificationUpgraderTest 15/0（合计 37/0）
```

### 3.4 编译面（生产可编译）

```text
$ ./gradlew :app:compileLocalFirstDebugKotlin --console=plain     → BUILD SUCCESSFUL in 47s
$ ./gradlew :core:data:compileDebugKotlin --console=plain          → BUILD SUCCESSFUL in 14s
$ ./gradlew :core:domain:test --tests "…AnswerKeyVerificationUpgradePolicyTest" --console=plain
                                                                    → BUILD SUCCESSFUL in 5s（domain 测试源全编过）
```

注：`:app:compileDebugKotlin` 不存在（变体为 LocalFirst/StrictOffline），实际执行的是
`compileLocalFirstDebugKotlin`。

## 4. 设计决定与如实登记

1. **确认的写路径 = 先落账本用户断言，再单列 CAS。**
   确认时调用既有 `AnswerKeyCorrectionWriter` 的 UNKNOWN 键路径（`newCorrectChoiceId = 题面当前键`），
   得到**真实账本记录** `correctionId`，再交给 `KnowledgeQuizItemVerificationUpgrader.upgrade(USER_ASSERTED)`；
   `LedgerAnswerKeyVerificationSourceVerifier` 要求该记录断言的就是题面当前键且未被撤销。
   → 升级口要求的"来源回执"因此不是自造 id。
   **残余（如实）**：账本里这条记录的 `reason_markdown` 前缀是 `answer-key-correction:<itemId>:<key>:`
   （KD-43 的既有形状），审计面读到的是"用户断言过这个键"，与"更正成别的键"共用前缀——两者靠
   "断言键 == 当时题面键"区分。这与 `clarification-ux.md` §2.5(c) 的顾虑同源；未新增 evidence kind
   （那要改 DB contract，超出本 ask 授权面）。
2. **"换题"的语义 = 跳过这个知识点**（按钮文案写明"换一道题（跳过这个知识点）"）。
   同一会话的题面身份是 `(learner, 会话, 知识点)` 且"首见即真"（`KnowledgeQuizItemStore` 类 KDoc），
   会话内重出只会读回同一道题——把"换题"做成重出是假动作，所以给的是真出路。
3. **确认动作的三重门控**：① 原因集含 `TARGET_CONTENT_NOT_VERIFIED`；② 候选 id == 屏幕上这道题；
   ③ 学生已作答（`submittedChoice != null`）。任一不满足都不出现/不接受确认。
   与 `clarification-ux.md` §0.1 的"两段式"一致：答前确认按钮是**禁用**的（文案说明"先做完这道题"），
   答后可用；非键未核验原因直接不渲染确认按钮（`knowledge_clarification_no_confirm`）。
4. **卡片动作 3 个**（确认 / 换一道题 / 取消）：ask 明确要求"候选列表/原因/确认/换题/取消"；
   `clarification-ux.md` §0.2 的"卡级动作 ≤ 2"未采纳（换题与取消是两条不同出路，合并会让
   "换题"隐含"丢弃本节点"而学生看不出来）。
5. **打断预算未实现**：`clarification-ux.md` §0.3 建议"每会话最多 1 次要求动作的打断"；本实现
   每个知识点的 CLARIFY 都会拉起卡片（同一决策重放不会重复弹）。登记为残余，未擅自加预算
   （会改变产品语义，需产品裁定）。
6. **卡片不复制 `TutorPendingRequestCard` 链路**：无 `agent_pending_request` 行、无 ACCEPT/DECLINE
   终态、无跨回合恢复；状态随目的地存活，进程死亡后由会话恢复重新选择。视觉上沿用
   `WorkspacePanel` + 主/次按钮 + busy 文案。

## 5. KD-67 未闭合半边（本 ask 未覆盖，不得读成已关闭）

- 设备/集成用例"生产候选能进 ASK、未核验候选仍 CLARIFY"（KD-67 reopen condition）**未跑**。
- 确认链路的**真 Room 端到端**（账本写入 → CAS → 下次装配 `contentVerified=true`）**未跑**；
  JVM 侧只证明了各段（verifier 认账本记录、CAS adapter 由既有测试覆盖、presenter 状态机）。
- 错题行候选仍 `contentVerified=false`（装配器契约未变，本批不放宽）。
- `IndependentVerification`（`VERIFIED`）在生产仍不可达（没有独立核验记录库）。

## 6. 本会话未做（不得被读成已通过）

1. 未连设备、未跑任何 `connected*` / `am instrument` / 未跑 `connectedLocalFirstDebugAndroidTest`。
2. 未跑全仓强门、未跑 `acceptance_report`、未改台账/验收行（E/F/A 行属 P2-15）。
3. 未跑 `tools/ci/a11y_audit.py`（新卡的无障碍判据待设备/审计阶段）。
4. 未跑完整 `:core:data:testDebugUnitTest`（只跑 §3.3 点名的 4 个类）。
5. **app 单测源集当前编译失败（pre-existing，非本批引入）**：
   `./gradlew :app:compileLocalFirstDebugUnitTestKotlin` → `Unresolved reference 'retentionFromSlider'`
   （`app/src/test/kotlin/.../SchedulingRetentionSliderValueTest.kt:24-52`；该符号在 app main 全仓不存在，
   该测试文件 `git status` 为空、HEAD 提交 `587098f1` 引入）。→ 本批**没有**跑任何 app 单测，
   也不把 app 单测当成本批证据；未擅自修他线测试。
6. 未 `git add` / commit。
