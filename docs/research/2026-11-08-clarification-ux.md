# 复习会话中的「澄清/确认卡」与「校准可见页」：信息架构与可测试 UI 判据

> 落盘：`docs/research/2026-11-08-clarification-ux.md`（批次 49 · P2 产品闭环 · R2 产品/交互研究）
>
> 本文是**研究/设计件**，不是验收裁定：给结构、给动作预算、给判据与测法，**不签任何 PASS**。
> 本会话未跑 Gradle、未连设备、未改任何代码；证据见
> `docs/research/evidence/2026-11-08-batch-49/clarification-ux-sources.md`（下称「证据件」）。

---

## 0. 结论摘要（先给推荐，再给被否掉的方案）

1. **确认卡不能放在"出题之前"。** 三条读码事实否掉了这个形态（§2.5）：① 出题前展示正确答案 = 把即将用来计分的题提前揭示，而"已揭示"这件事今天**结构上不会被记录**（唯一生产写口恒写 `HIDDEN`，`KnowledgeQuizItemStore.kt:183`）；② "确认"要落成 `USER_ASSERTED` 需要一个**真实回执**，而回执校验端口的生产实现不存在（`AnswerKeyVerificationUpgrade.kt:112`，全仓仅测试实现）；③ 候选里还有一类来自错题行，它们**恒未核验且没有可展示的键面**（`AdaptiveSelectionAssembler.kt:209`），给学生一个"确认"按钮等于给一个点不动的按钮。
   → **推荐：两段式。** 出题前只给「换一道题 / 跳过这个知识点」（**不显示任何键**）；「确认答案对不对」只在**学生答完之后**给（复用 KD-43 已存在的"答后才出现"纪律，`KnowledgeReviewSessionScreen.kt:455-479`）。
2. **动作预算：卡级动作 ≤ 2，必修动作 = 0。** 复习必须能一路走到会话末，不因任何卡停下来（判据 CX-E01/E02、CX-A01/A03）。
3. **打断预算：每会话最多 1 次"要求动作"的打断，其余降级为会话末汇总。** 因为 CLARIFY 是**每个知识点**都可能触发的决策（选择器对任一"无候选"原因 + 存在未核验目标都返回它，`AdaptiveQuestionSelector.kt:299-315`），不设预算时最坏情况是每个节点弹一次。
4. **复用 `TutorPendingRequestCard` 的视觉语法，不复用它的链。** 借：卡片底/标题+一句细节/主次 2 按钮/busy 行 polite 播报/`testTag` 前缀/大字号折行（`TutorPendingRequestCard.kt:57-101`）。不借：它背后的 `agent_pending_request` 可写行与 ACCEPT/DECLINE 终态——澄清是**只读决策快照**且消费者可能被重复调用（`AdaptiveSelectionConsumption.kt:14-21`），写成行会把"一次性决策"变成"待办清单"。
5. **校准可见页应落在「复习安排」页（`SchedulingSettingsScreen`），不要挂在「模型服务」。** 它展示的是排程/记忆参数（21 维 FSRS 参数、per-reason 召回、G6 幅度、规划器版本），不是 provider 配置；而现在的"学习预测效果"面板挂在**模型服务**页（`Routes.Capability` → `CapabilityScreen`，`SecondaryScreens.kt:416-420`），与 provider 配置同屏——学生要查"排程准不准"得先想到去模型服务页找。
6. **校准页有两处数据诚实性硬约束**：① 今天只持久化了 21 个数字本身，**没有**"最近拟合时间/样本/logLoss"（`DataStoreSchedulingSettingsStore.kt:83-101`）→ 页面必须写"尚未拟合/无记录"，不能编数字；② `optimizeSchedulingParameters()` 对"数据不足"和"采纳门拒绝"**都返回 null**（`StudySchedulingCalibration.kt:106`、`:116-122`）→ 页面在扩返回类型之前**不许**声称任何一种原因。
7. **冲突 KC 默认继续复习**（学生可选「先校准」），不做强制拦截——与计划硬约束一致（冲突 KC 默认仍可选）。

---

## 1. 输入、范围与边界

| 项 | 内容 |
|---|---|
| 计划条目 | `.zcode/plans/plan-2026-11-08-mastery-review-quality.md:27-31`（P2-11 KD-67 确认卡 / P2-12 校准可见页 / P2-13 投影冲突 / P2-14 知识点冲突 / P2-15 验收行） |
| 硬约束 | 同文件 `:41-44`：热区 `feature/review`+`app` 授权但需逐文件 `git status`；掌握阈值不改；冲突 KC 默认仍可选；模拟结论标 model-based；真库样本不足标 BLOCKED；`RoomStudyDatabase.kt` 不新增 DAO |
| 本会话实读 | `AdaptiveSelectionConsumption.kt`、`AdaptiveQuestionSelector.kt`、`AdaptiveSelectionAssembler.kt`、`AnswerKeyVerificationUpgrade.kt`、`KnowledgeQuizItemStore.kt`、`KnowledgeQuizFeedbackWriter.kt`（相关段）、`KnowledgeReviewSessionScreen.kt`、`KnowledgeReviewSessionViewModel.kt`、`TutorPendingRequestCard.kt`、`ReviewSelfReportControls.kt`、`ReviewChoiceRow.kt`、`SchedulingEvaluation.kt`（相关段）、`StudySchedulingCalibration.kt`、`DataStoreSchedulingSettingsStore.kt`、`SchedulingSettingsScreen.kt`、`SecondaryScreens.kt`（相关段）、`SourceCalibrationSection.kt`、`WorkspaceSurfaces.kt`、`tools/ci/a11y_audit.py`、`feature/review/build.gradle.kts`、`app/build.gradle.kts`（相关段）。逐条命令与行号见证据件 §1 |
| 外部材料 | Anki 手册 2 页 + SuperMemo 2 页（逐字引文与"单一来源"标注见 §3 与证据件 §2） |
| 本会话**未做** | 未跑 Gradle / 未连设备 / 未跑 `a11y_audit` / 未改代码。本文所有判据**均为待建立**，不是"已通过" |

---

## 2. 现状事实（每条都可回指源码）

### 2.1 知识点复习会话今天的样子

- 状态机：`IDLE / LOADING / LOADED / FAILED / KNOWLEDGE_PREPARING / NO_MATERIAL`（`KnowledgeReviewSessionViewModel.kt:17-35`）。**没有**"等待学生决定"这个状态——任何新卡都会新增一个状态或一个并行表面。
- 每个知识点的**必点次数 = 3**：选选项（`ReviewChoiceRow.kt:82-85` clickable）→ 提交（`Screen:353-375` `knowledge_review_submit`）→ 下一知识点/完成（`Screen:277-285` `knowledge_review_next`）。
- 已有的"卡"形态有三种，全都**在失败/异常路径**上：加载失败卡（重试/跳过/返回，最多 3 动作，`Screen:643-686`）、判决卡（`Screen:598-636`）、结束卡（`Screen:182-197`）。
- 已有的"问答对话框"两个，都是**学生主动点开**（不阻断流程）：`答案有问题？`（`Screen:379-400`）与 `更正答案`（`Screen:455-479`）；后者有明确纪律：**只在已提交后出现**。

> 结论：新卡要挤进的是一个"每节点 3 次点击、0 次弹窗"的流程。任何新增的强制点击都直接抬高完成成本。

### 2.2 既有确认卡（`TutorPendingRequestCard`）：借什么、不借什么

| 维度 | `TutorPendingRequestCard` 现状 | 本设计是否沿用 |
|---|---|---|
| 视觉 | `Surface`（`PendingSurface`，20dp 圆角）+ 标题 + 一句 detail（`:57-78`） | **沿用**（同一套"提示卡"语法，学生不用学第二套） |
| 动作 | 主按钮 `accept` + 次按钮「暂不处理」（`:83-101`） | **沿用「2 个动作」上限**，但文案不同（见 §4） |
| 忙碌态 | 一行「正在处理这次请求…」+ `liveRegion = Polite`（`:79-82`） | **沿用** |
| 大字号 | `fontScale > 1.3f` 时每行 1 个按钮（`:84`） | **沿用**（判据 CX-I03） |
| 测试锚 | `testTagPrefix` + `_accept` / `_decline` / `_busy`（`:53`、`:81`、`:91`、`:99`） | **沿用命名法**（新前缀，不复用旧前缀） |
| 背后实体 | `agent_pending_request` **可写行**，裁决后落终态，进程死亡重建同一张卡（`:42-44`） | **不沿用**：澄清是只读快照（`AdaptiveSelectionConsumption.kt:14-17`），且消费者"同一决策重放可能重复调用"（`:20-21`）。写行会让重复调用造重复行，并把"当场决定"变质成"回大厅处理" |
| 决策枚举 | `ACCEPT` / `DECLINE` | **不沿用**：澄清卡的动作不是"同意/拒绝一个外部提议"，而是"继续/换一条路" |

### 2.3 三键自评（Again / Hard / Good 的本仓对位）

- 三键语义与文案单源：`对`=Good（顺利回忆）/`卡顿`=Hard（**费力但想起来了**）/`错`=Again（没想起来/答错），**无 Easy**（`ReviewSelfReportControls.kt:51-56`、`:113-118`）。
- 三键在窄屏/大字号下改竖排（`:746-758`），按钮最小高 64dp（`:798`）。
- **它不能当确认卡的模板**：自评是"答完之后的一次评分"（一个维度的取值），确认卡是"要不要现在处理一件事"（一次分支）。把分支塞进评分键会让学生把"我没想清楚"按成某个评分档。

### 2.4 CLARIFY 载荷里到底有什么（决定卡能显示什么）

`AdaptiveClarificationRequest`（`AdaptiveSelectionConsumption.kt:48-72`）只带：

| 字段 | 学生可读？ | 说明 |
|---|---|---|
| `targetKnowledgeNodeIds` | 间接 | 只有 id；显示名要另读队列项（`KnowledgeReviewSessionScreen.kt:199` 已有 `entry.displayName`） |
| `requestedTargetKind` | 否 | 内部目标类型（FOUNDATION/…，`AdaptiveQuestionSelector.kt:14-20`） |
| `candidates[{assessmentItemId,itemFamilyId,knowledgeNodeIds}]` | 否 | **没有题面、没有选项、没有正确答案** |
| `reasonCodes` | 需翻译 | 见 §4.6 文案表 |
| `selectorVersion` / `decisionAtEpochMillis` | 否 | 只进诊断，不进学生界面 |

→ **卡片要显示"这是哪道题"，必须另读题面**（`KnowledgeQuizItemStore.readSnapshot`，该类已有读口 `:83`），而这一步正是 §2.5 的风险源。

`reasonCodes` 全集（选择器产出处，`AdaptiveQuestionSelector.kt:153/162/164/173/199/210/299-315`）：
`QUESTION_NOT_REQUESTED`、`PROJECTION_NOT_CURRENT`、`CALIBRATION_REQUESTED`、`CLEARLY_MASTERED_FOR_SKIP`、`LEARNING_EVIDENCE_NOT_CURRENT`、`PREDICTION_UNKNOWN_STALE_OR_OUTSIDE_CORRIDOR`、`TARGET_MATCH`、`SAFE_VERIFIED_CANDIDATE`、`HARD_CHALLENGE_CORRIDOR`、`TARGET_CONTENT_NOT_VERIFIED`、`NO_SAFE_CANDIDATE`、`NO_SAFE_CALIBRATION_CANDIDATE`、`CALIBRATION_ONLY`。

**关键结构性事实**：`CLARIFY_OR_CONFIRM` 的判据只是"存在未核验目标"（`:299-315`），所以它的原因集**可能不含** `TARGET_CONTENT_NOT_VERIFIED`（例如证据过期、预测越界）。此时"确认答案"不是解法——卡不能承诺"确认后就能出题"。

### 2.5 三条硬约束（本设计的根据，全部来自读码）

**(a) 候选只有两类，只有一类可确认。**

| 来源 | `contentVerified` | 读面是否有键 | 能否在复习里"确认" |
|---|---|---|---|
| 本会话题面快照（`assembleCandidates` 首段） | 按 `verificationStatus` 现读（`:184-189`） | 有（`answer_spec_snapshot` 解出 `correctChoiceId`，`KnowledgeQuizItemStore.kt:203-222`） | 可以，但**只在答后**（见 (b)） |
| 错题行 | **恒 false**（`:209`） | **没有**（该读面只有 `practiceUnitId` / `knowledgeNodeIds`） | **不可以**——列进"待确认"就是死按钮 |

**(b) "已看过答案"从不被记录。**
唯一的生产写口 `KnowledgeQuizItemStore.save` 恒写 `ANSWER_REVEAL_STATE_HIDDEN`（`:183`），常量为 private（`:228`）；全仓 `answerRevealState =` 的非测试写入只有这一处（证据件 §1.5）。而选择器唯一的"别出已揭示的题"闸就是 `!candidate.answerWouldBeRevealed`（`AdaptiveQuestionSelector.kt:242`）。
→ 任何**答前**展示正确选项的确认卡，都会造出"学生刚看过答案、系统仍认为未揭示"的题。答后展示没有这个问题：题面身份含会话分量（`knowledgeQuizItemSnapshotId(learner, session, node)`，`KnowledgeQuizItemStore.kt` 类 KDoc 与 `:109-114`），同一会话内一节点一题、不会二次出给同一学生。

**(c) "确认"今天写不进去。**
`USER_ASSERTED` 要求 `UserAssertion(confirmationId, confirmedByLearnerId, confirmedAt)` 且 `confirmationId` 指向**真实回执**（`AnswerKeyVerificationUpgrade.kt:43-61`、`:112-118`，拒因 `SOURCE_NOT_VERIFIED` `:94-99`）。生产侧：
- 回执校验端口 `AnswerKeyVerificationSourceVerifier` **没有生产实现**（仅测试，证据件 §1.6）；
- 计划 §P2-11 写的 `KnowledgeQuizItemStore.record` 实际是 **private 行构造器**（`:142`），本类没有"写核验状态"的方法；
- 用现有的 REPLACE 更正去表达"我确认这个键"会在账本里追加一条 `KEY_CORRECTION` 事件并把 `keyVersion` 推到新值（回执字段语义 `KnowledgeQuizFeedbackWriter.kt:561-562`）——**审计面会把"确认"读成"改键"**，与"来源如实保留"（`AnswerKeyVerificationUpgrade.kt:14-19`）相悖。

→ 结论：**在 verifier 生产 adapter 落地之前，卡里不出现"确认"主按钮**。第一期只能给"换一道 / 跳过"与"这条我先记下"（无写）。

### 2.6 校准面：今天有什么、缺什么

| 项 | 现状 | 位置 |
|---|---|---|
| 预测质量（Brier / logLoss / ECE / 最大偏差） | 有，但挂在「模型服务」页的"学习预测效果"面板（`if (calibrationReportProvider != null)` 才渲染；生产恒传入，`SmartMistakeBookRoot.kt:1440`） | `SecondaryScreens.kt:416-497`（面板）；调用点 `SmartMistakeBookRoot.kt:1437-1443` |
| 来源校准（讲题判定等主观来源） | 有，嵌在同一面板内 | `SourceCalibrationSection.kt:25-28`、`:54` |
| 21 维 FSRS 参数 | 只持久化**数值本身** | `FsrsScheduleMath.kt:48`（`PARAMETER_COUNT = 21`）；`DataStoreSchedulingSettingsStore.kt:83-101` |
| 最近拟合（时间/样本/验证 logLoss vs 基线） | **不存在**：`FsrsParameterOptimizer.Result` 有这些字段（`SchedulingEvaluation.kt:463-470`），但 `optimizeSchedulingParameters()` 只在内存里用一次就丢 | `StudySchedulingCalibration.kt:95-125` |
| 重拟合结果的**原因** | **不可区分**：数据不足（`:106`）与采纳门拒绝（`:116-122`）都 `return null` | 同上 |
| per-reason 召回表 | 已实现计算（`SchedulingEvaluation.kt:316-337`），**无 UI** | 同上 |
| G6 考前扩容幅度 | `0.5`，KDoc 明确标 `PENDING_CALIBRATION` | `AlgorithmConstants.kt:140-148` |
| 规划器 / 选择器版本 | `review-planner-v4`、`learning-core-v14(selector-v7,…)` | `ReviewPlannerV2.kt:1055`、`LearningCoreVersions.kt:136` |

---

## 3. 外部参考：Anki 与 SuperMemo 各给了什么、我们取哪一半

> 逐字引文、URL、"单一来源/未取到"标注见证据件 §2。本节只写**采纳/拒绝**与理由。

### 3.1 Anki：四键语义 → 我们只取三键的对位

Anki 当前手册把 **Again** 定义为"答错或没想起来"，**Hard** 为"答对了但有疑虑/花了很久"，**Good** 为"答对但费了点心力"，**Easy** 为"毫不费力"。
**采纳**：Again=没想起来、Hard=想起来了但费力——与本仓 `错/卡顿` 完全同构，且本仓已经在 KDoc 里用这条裁定（`ReviewSelfReportControls.kt:51-56`）。
**拒绝**："Easy"第四键。本仓 `ReviewSelfReportKey` 只有三档，且拟合侧 EASY 结构性不可辨识（`SchedulingEvaluation.kt:499-501` 注释：w3/w16 剔除）。
**对本卡的直接含义**：确认卡**不是**第四个评分键。它不能长得像评分键（并排、同尺寸、同色调），否则学生会把"我不知道这题答案对不对"当成绩来按。

### 3.2 Anki：leech 处置放在复习**之外**

"当计数达到 **8**，Anki 给笔记打 leech 标签并**暂停**该卡"；继续失败按"半个阈值"再提醒（12、16…）。推荐处置是**事后**编辑（"the most efficient method"）、删除、或先搁置。
**采纳**：
1. **"要决策的事"移出复习循环**——对应本设计的 K3（会话末汇总）与 K4（设置页校准）。
2. **默认动作是"先不动"**——对应本设计"必修动作 = 0"。
**拒绝**：默认 suspend/暂停某题。本仓没有"暂停单个题面"的语义；新增它会改计划候选集（推断，未验证），属产品裁定，超出本轮。

### 3.3 SuperMemo：leech 向导 = 反面教材

SuperMemo 的 leech 定义是"学生总是忘的题"；其 `Leech Alert` 向导**每次遇到 leech 都弹**，选项十余个（Continue / Postpone / Edit / Forget / Dismiss / Delete / Reschedule / Parameters / Auto-postpone / Auto-forget / Include semi-leeches / Great–Bad）。
**拒绝**：这种"每次遇到都弹 + 多选项"的形态——它把复习变成决策流水线，正是我们要避免的打断。
**采纳**：`Continue (Esc)` 是一等出口——任何卡都必须能"什么都不改，继续"。
**未取到**：SuperMemo 的**默认阈值数值**（该页未给），本文不使用。

### 3.4 三系统对照（本卡设计的位置）

| 系统 | 每个"决策点"的动作数 | 打断时机 | 处置归属 |
|---|---|---|---|
| Anki 评分 | 1（四键之一） | 答后、每题 | 当场（评分即调度） |
| Anki leech | 0（自动 tag+suspend） | 事后 | 复习之外（编辑/删除/搁置） |
| SuperMemo leech | 1–2（向导选一项） | **每次遇到** | 当场（向导） |
| **本设计 K1（出题受阻）** | **2**（换一道/跳过），且每会话 ≤1 次 | 出题前、且仅当"这个点根本出不了题" | 当场（只影响本节点） |
| **本设计 K2（答后确认条）** | **0 必选 / 2 可选** | 答后、内联、不遮"下一题" | 当场（可选）或会话末 |
| **本设计 K4（校准页）** | 0（只读）；2 个危险动作需二次确认 | 学生主动进入"复习安排" | 设置页 |

---

## 4. 信息架构（推荐方案）

### 4.1 五条通道的分工

| 通道 | 出现条件 | 学生必须做什么 | 动作数 |
|---|---|---|---|
| **K1 出题受阻卡**（会话内） | 该知识点的题**确实出不了**（CLARIFY 且无可用候选），且本会话未用过打断预算 | 二选一：换一道题 / 跳过这个知识点 | 1（二选一） |
| **K2 答后确认条**（会话内，内联） | 学生已提交，且该题面 `verificationStatus == UNKNOWN` | **什么都不用做**；可选「确认这个答案」/「不对，更正」 | 0 必选 / 1 可选 |
| **K3 会话末汇总**（会话内，末屏） | 本次会话累计 ≥1 条未处理的待确认 | 可展开看列表 | 0 必选 / 1 可选 |
| **K4 校准可见页**（`复习安排` 页内） | 学生主动进入 | 只读浏览 | 0 |
| **K5 冲突 KC 卡**（会话内，节点级） | 该节点 `effectiveStatus == CONFLICTED` | 默认继续复习；可选「先做校准题」 | 0 必选 / 1 可选 |

> 关键取舍：计划 §P2-11 列的五件事（候选/原因/确认/换题/取消）**不能压进一张卡**——5 个动作违反 ≤2 预算。拆法：**"确认"归 K2**（答后）、**"换题/取消"归 K1**（答前）、**"候选/原因"是两张卡共用的展示块**（不是动作）。

### 4.2 K1 出题受阻卡（唯一允许"要求动作"的卡）

```
┌──────────────────────────────────────────────┐
│ 这个知识点的题还没准备好                      │  ← 标题：说事实，不说机制
│ 这道题的答案还没有独立核对，先换一道题试试。   │  ← 一句原因（由 reasonCodes 翻译）
│                                              │  ← 不列 assessmentItemId
│  [ 换一道题 ]        [ 跳过这个知识点 ]       │  ← 2 个动作，主/次
│  跳过不记录结果，这个知识点之后仍会复习。      │  ← 后果句（与现有失败卡同口径）
└──────────────────────────────────────────────┘
```

硬规则：
1. **不显示正确选项、不显示"这是第几题"**（无键面，且答前揭示有 (b) 风险）。
2. 只出现**一次**/会话（打断预算，§4.5）。
3. **「跳过」永不禁用**——即使"换一道题"正在请求中。反过来不成立：请求中时「换一道题」置忙（沿用 `busy` 行 + polite 播报）。
4. 若原因是"证据过期/预测越界"（无 `TARGET_CONTENT_NOT_VERIFIED`），文案**不得**说"答案没核对"，改为"这次判断的依据过期了，先换一道"（§4.6 表）。

### 4.3 K2 答后确认条（内联，0 必选）

```
[判决卡：回答正确 / 这次选择还差一步]
这次用到的答案还没有独立核对；你确认它是对的吗？   ← 一行，bodySmall，InkSecondary
   确认这个答案        不对，更正                 ← 两个 labelMedium 文本按钮（不是大按钮）
[ 下一知识点 ]                                     ← 永远在最下方、永远可点
```

硬规则：
1. **位置在判决卡之下、"下一知识点"之上**，且**不得**用 `AlertDialog`（现有 `答案有问题？`/`更正答案` 是学生主动点开的对话框；本卡是系统主动出现，形态必须更轻）。
2. **「不对，更正」复用 KD-43 现有对话框**（`Screen:480-565`），不新造第二个更正流程。
3. 「确认」在 §2.5(c) 的 verifier 落地前**不渲染**（宁可不给，也不给一个必然失败的按钮）；渲染时必须有三态：处理中 / 已记录 / 失败（失败必须说"没有记录成功"，不得假成功——与 `SmartMistakeBookRoot.kt:976-978` 的既有纪律同源）。
4. 若 `submittedChoice == null`，K2 **完全不存在**（判据 CX-E05）。

### 4.4 动作预算（可量化）

| 场景 | 今天必点 | 加卡后必点 | 可选动作 |
|---|---|---|---|
| 一个知识点（正常出题） | 3 | **3**（+0） | +2（答案有问题/更正） |
| 一个知识点（K1 卡住） | 0（卡死） | **2** | — |
| 一个知识点（K2 出现） | 3 | **3**（+0） | +2（确认/更正） |
| 会话结束（K3） | 1（完成） | **1** | +1（展开汇总） |
| 冲突节点（K5） | 3 | **3**（+0） | +1（先做校准题） |
| 校准页（K4） | — | **0** | +2（立即重拟合 / 重置参数） |

→ 设计不变式：**任何通道都不得让"完成本次复习"的必点次数上升**（唯一例外 K1：那条路径今天是死的）。

### 4.5 打断预算、去重与恢复

| 规则 | 值 | 为什么 |
|---|---|---|
| 每会话"要求动作"的打断 | **≤ 1 次** | CLARIFY 可按节点触发（`AdaptiveQuestionSelector.kt:299-315`），不设限即等于每节点一卡 |
| 第 2 次及以后 | 降级为 K3 汇总行（0 动作） | 保住"不打断"这一条硬要求 |
| 去重键 | `assessmentItemId` | 载荷保证候选 id 唯一（`AdaptiveSelectionConsumption.kt:65`），仍要防"两次决策列出同一题" |
| 重复投递 | 幂等：不叠卡、不叠行 | 消费者契约明写"可能重复调用"（`:20-21`） |
| 进程死亡/旋转 | 已处理/已忽略的 `assessmentItemId` 集合持久化到 `SavedStateHandle`，重建后**不重弹** | 与 `KnowledgeReviewSessionViewModel` 既有持久化纪律一致（`:479-496`） |
| 会话结束 | 未处理的项清零（不跨会话堆积） | 决策快照有时效（`decisionAtEpochMillis`），旧快照不该在新会话复现 |

### 4.6 文案表（`reasonCodes` → 学生句子；含禁词）

| 原因码 | 学生看到的一句 | 备注 |
|---|---|---|
| `TARGET_CONTENT_NOT_VERIFIED` | 「这道题的答案还没有独立核对，先换一道题试试。」 | 唯一允许暗示"换题能解决"的原因 |
| `LEARNING_EVIDENCE_NOT_CURRENT` | 「这个知识点之前的记录有点久了，先复习到这里。」 | 不得说"答案有问题" |
| `PREDICTION_UNKNOWN_STALE_OR_OUTSIDE_CORRIDOR` | 「这次判断的依据过期了，先换一道。」 | 同上 |
| `PROJECTION_NOT_CURRENT` | 「学习记录正在更新，请返回后重新进入。」 | 系统状态，不提供"换题/确认"，只提供"返回"（本会话没有自动重试） |
| `QUESTION_NOT_REQUESTED` | （不出现卡） | 这是调用方契约错误，不该进学生界面 |
| `NO_SAFE_CANDIDATE` / `NO_SAFE_CALIBRATION_CANDIDATE` | 「这个话题暂时没有合适的题，先跳过。」 | 不提供"确认" |
| 其他/未知码 | 「这道题暂时不能出，先跳过这个知识点。」 | 兜底句，不解释机制 |

**禁词表**（JVM 判据 CX-E07 直接正则断言）：`CLARIFY`、`TARGET_CONTENT_NOT_VERIFIED`、`selector`、`projection`、`核验`、`verificationStatus`、`MODEL_JUDGED`、`assessmentItemId`、`reasonCode`、`UserAsserted`、`USER_ASSERTED`。
> 依据：现有学生文案把内部枚举翻成人话且不出现机制词（`TutorPendingRequestCard.kt:115-121`、`KnowledgeReviewSessionScreen.kt:578-596`）。

### 4.7 K4 校准可见页（落点：`SchedulingSettingsScreen`，新增第 4 个分区「学习算法校准」）

| 分区 | 内容 | 数据来源 | 空/缺省态 |
|---|---|---|---|
| A 记忆参数 | 21 个参数（默认折叠），每项 `w[n]` + 数值 + "未拟合/已拟合"标记 | `optimizedParameters`（`DataStoreSchedulingSettingsStore.kt:83`）+ `DEFAULT_PARAMETERS` | 无值 → 「用默认参数」 |
| B 最近一次拟合 | 时间 / 参与样本 / 验证 logLoss / 是否被采纳 | **今天不存在** → 必须先加持久化（见 §6 前置缺件） | 「还没有记录」（**不许**写 0 或猜测） |
| C 按原因校准 | 每行：原因 / 样本数 / 实际回忆率 / 总体基线 | `calibratePlannedReasons`（`SchedulingEvaluation.kt:316-337`） | 空 → 「样本还不够」；单行 <20 样本 → 标"仅供参考" |
| D 来源校准 | 沿用 `SourceCalibrationSection`（讲题判定等） | `SourceCalibrationSection.kt:54-97` | 沿用其既有空态 |
| E 规划器 | `review-planner-v4`、`learning-core-v14(selector-v7,…)`、G6 幅度 `0.5`（标"起步占位，未校准"） | `ReviewPlannerV2.kt:1055`、`LearningCoreVersions.kt:136`、`AlgorithmConstants.kt:148` | 恒定显示 |
| F 动作 | 「立即重拟合」/「重置参数」 | `optimizeSchedulingParameters()` / `setOptimizedParameters(null)` | 动作前二次确认；结果必须落三态（§6） |

硬规则：
1. **不许把"下次启动生效"藏起来**：该页既有的"改动在下次启动生效"（`SchedulingSettingsScreen.kt:42-43`）必须同样适用于"立即重拟合"的文案。
2. **"立即重拟合"的四种结果必须可区分**：采纳 / 保留原参数（候选更差）/ 数据不足 / 失败。当前 API 只给 `null`（`StudySchedulingCalibration.kt:106`、`:116-122`）→ 在扩返回类型之前，页面**只能**说"本次没有更新参数"，不得说原因（判据 CX-E13）。
3. **G6 不是"推荐值"**：显示 `0.5` 时必须带"起步占位/未校准"标注（`AlgorithmConstants.kt:140-147`），否则学生会把占位常量当标定值——这正是本轮 P1 要测的东西。

### 4.8 K5 冲突 KC 卡（默认继续）

- 触发：节点 `effectiveStatus == CONFLICTED`（`ClearlyMasteredForSkipPolicy.kt:127`、`:146`）。
- 版面：一句「这个知识点之前掌握过，最近又错了，建议先做一道校准题」+ 两动作「先做校准题」/「仍然复习」。
- **默认 = 仍然复习**：卡不阻断，直接进入正常出题流程；「先做校准题」只是把本次的 `requestedTargetKind` 换成 `CALIBRATION`（选择器已有 `ASK_CALIBRATION`，`AdaptiveQuestionSelector.kt:164`、`:222-232`）。
- 与硬约束一致：冲突 KC 默认仍可选，不强制。

---

## 5. 可测试的 UI 判据

### 5.0 分层前提（决定判据放哪）

| 层 | 能测什么 | 设施现状 |
|---|---|---|
| **E（JVM 合同）** | 动作数、状态机、文案、去重、幂等、默认值 | `feature/review` 只有 `test` 源集、无 `compose-ui-test`（`feature/review/build.gradle.kts:17-26`）→ 沿用既有范式 `ReviewSelfReportUiContractTest`（纯函数断言，不渲染） |
| **A（设备旅程）** | 真点击、真语义树、进程重建 | `app` 有 `compose.ui.test.junit4`（`app/build.gradle.kts:201`）；`feature/review` **没有 androidTest 源集** → 新 UI 用例必须落 `app/src/androidTest` |
| **I（无障碍/版式）** | 触摸目标、标签、对比度、大字号几何 | `tools/ci/a11y_audit.py dump/audit/theme`（`:51` 默认 48dp；缺颜色则 contrast=UNVERIFIED） |

> **不可判定声明**：以下三条**无法**自动化，任何"PASS"都不成立——
> ① 学生是否真的理解"答案还没有独立核对"这句话；② 0 必选动作是否真的让学生"不觉得被打断"（只能测代理指标：每会话动作数、modal 次数）；③ 校准数值是否"准"（需真库样本，样本不足按硬约束标 BLOCKED）。

### 5.1 E 域：JVM 合同判据（新建 `feature/review/src/test/.../ClarificationCardContractTest.kt` 等）

| ID | 判据（可观测断言） | 测法（待建测试 → 断言） | 若失败，学生看到什么 |
|---|---|---|---|
| CX-E01 | 任一卡的可点动作 ≤ 2 | `clarificationCardModel(...).actions.size <= 2` 对 K1/K2/K3/K5 四种输入各断言 | 一张卡出现 3+ 个按钮，学生不知道主次 |
| CX-E02 | 必修动作为 0（K1 除外：它是唯一有二选一的通道） | `clarificationRequiredTaps(model, state = IDLE) == 0`（K2/K3/K5）；K1 为 1（二选一） | 复习被卡住，必须处理才能继续 |
| CX-E03 | 每会话打断 ≤ 1 | 依次喂 3 个 CLARIFY 决策 → 第 1 个得 `K1`，第 2/3 个得 `SUMMARY_ONLY` | 每个知识点弹一次，复习变问卷 |
| CX-E04 | 重复投递幂等 | 同一 `AdaptiveClarificationRequest` 调两次 → 卡片数仍为 1，`candidates.map(id).distinct().size == candidates.size` | 同一道题在卡里出现两遍 |
| CX-E05 | 未答不显示答案 | `clarificationKeyVisible(submitted = false) == false`，且渲染文本不含 `item.choices` 里正确项的文字 | 出题前泄题，且系统不记录（§2.5b） |
| CX-E06 | 原因不可越权承诺 | 对 `reasonCodes` 不含 `TARGET_CONTENT_NOT_VERIFIED` 的输入，`copy.detail` 不得匹配 `(确认|核对).*(就能|即可|后就能)` | 学生确认了却发现还是出不了题 |
| CX-E07 | 禁词表 | 对 §4.6 全部可见文案做正则：`(CLARIFY|TARGET_CONTENT_NOT_VERIFIED|selector|projection|核验|verificationStatus|MODEL_JUDGED|assessmentItemId|reasonCode|USER_ASSERTED)` 必须 0 命中 | 界面出现内部机制词 |
| CX-E08 | 错题候选不可确认 | 构造 1 快照 + 1 错题候选 → `confirmableCandidates.size == 1`；另一组进 `unconfirmable` 且该组 `actions.isEmpty()` | 点了"确认"永远没反应（死按钮） |
| CX-E09 | 汇总计数正确 | 3 条待确认（含 1 条重复 id）→ 汇总文本含数字 `2` | 数字与实际不符 |
| CX-E10 | 重建不重弹 | 处理后把 id 集合写 `SavedStateHandle` 再重建 → `clarificationSurface == NONE` | 转屏/杀进程后同一张卡反复出现 |
| CX-E11 | 冲突默认继续 | `conflictCard(default).blocked == false` 且主路径动作 == `CONTINUE_REVIEW` | 冲突 KC 被强制拦截（违反硬约束） |
| CX-E12 | 校准页"未拟合"诚实 | `calibrationParameterRows(null, defaults).all { it.state == USING_DEFAULT }`，行数 == `FsrsScheduleMath.PARAMETER_COUNT`(21) | 页面把默认值画成"已校准" |
| CX-E13 | 重拟合结果可区分 | 页面模型必须消费 4 值 outcome（`ADOPTED/KEPT_EXISTING/INSUFFICIENT_DATA/FAILED`）各一条文案；**当前 API 只有 `null` → 本判据前置是扩返回类型**（§6） | 页面把"没通过采纳门"说成"数据不足" |
| CX-E14 | per-reason 表诚实 | 每个 `PlannedReasonCalibration` 一行；`sampleCount < 20` → `row.advisoryOnly == true`；空列表 → `EmptyState` | 3 个样本的召回率被当结论 |

### 5.2 A 域：设备旅程判据（`app/src/androidTest/.../`，用 `am_instrument_recorder.py` 跑）

| ID | 判据 | 测法 | 失败后果 |
|---|---|---|---|
| CX-A01 | 复习全链无 modal | 进入知识点复习 → 选答 → 提交 → 下一 → 完成；全程 `onNode(isDialog()).assertDoesNotExist()` | 复习被对话框打断 |
| CX-A02 | K1 恰 2 个可点动作 | 语义树里卡内 `clickable && enabled` 节点数 == 2 | 多出"稍后"等第三动作 |
| CX-A03 | K2 不夺走"下一知识点" | K2 出现时 `onNodeWithTag("knowledge_review_next").assertIsEnabled()` | 学生被迫先处理确认 |
| CX-A04 | K3 可展开 | 末屏点击汇总行 → 列表出现且条数 == 会话内去重后待确认数 | 汇总空开/数字错 |
| CX-A05 | 校准页离线可达 | 不配置任何 provider，从"复习安排"进入校准分区并可读到 A/E 两区 | 校准入口仍绑在模型服务页（学生没有理由去那里找排程校准） |
| CX-A06 | 冲突节点默认继续 | 冲突 KC 节点进入 → 不点任何"先校准" → 仍能作答并提交 | 冲突 KC 被拦死 |

### 5.3 I 域：无障碍与版式判据（工具现成）

| ID | 判据 | 命令（逐字） | 判定 |
|---|---|---|---|
| CX-I01 | 卡内可点节点 ≥ 48dp | `python -I tools/ci/a11y_audit.py dump --serial <s> --out build/b49/ux-dump.xml` 然后 `python -I tools/ci/a11y_audit.py audit --dump build/b49/ux-dump.xml --density-dpi <dpi> --out build/b49/ux-audit.json` | `violation_codes` 不含 `TOUCH_TARGET_TOO_SMALL`；`structural_ok == true` |
| CX-I02 | 每个可点节点有可朗读文本 | 同上 | `violation_codes` 不含 `MISSING_LABEL` |
| CX-I03 | 200% 字号不截断/不重叠 | `adb shell settings put system font_scale 2.0` → 重新 dump；比较相邻可点节点 bounds 无相交；截图人工复核一次 | bounds 无相交 **且** 人工确认无截断（人工那半不可自动判） |
| CX-I04 | 状态变化有 polite 播报 | `app` Compose 用例断言 `SemanticsProperties.LiveRegion == Polite`（同 `TutorPendingRequestCard.kt:79-82` 的写法） | 读屏用户听不到"正在处理/已记录" |
| CX-I05 | 对比度 | `python -I tools/ci/a11y_audit.py audit --dump … --colors <主题色对>` | `contrast.status == "PASS"`；**缺颜色输入时必须报 UNVERIFIED，不得计 PASS**（`a11y_audit.py:195-282`） |

> §5.3 的命令是**本轮未执行**的（未连设备）；参数 `--serial/--density-dpi/--colors` 需按当次设备填。工具本身已在仓内（`tools/ci/a11y_audit.py:503-529`）。

### 5.4 不可判定项（写死在报告里，防止被"顺手标绿"）

| 项 | 为什么不可判 | 出口 |
|---|---|---|
| "学生理解了'未核对'这句话" | 语义理解，ATF 官方亦承认 "require human judgment"（`docs/research/2026-11-06-a11y-acceptance.md:51` 逐字引 ATF README） | 人工走查一次，标 UNVERIFIED；不得因"文案已写"记 PASS |
| "卡不打断复习"（主观） | 无客观阈值 | 只用代理指标：每会话 modal 次数 = 0、必修动作 = 0、打断 ≤ 1（CX-E01/E02/E03、CX-A01） |
| 校准数值准不准 | 需真库样本；样本不足按硬约束 BLOCKED | 页面照实显示样本数；<阈值不呈现为结论（CX-E14） |
| K2 的"确认"能否成功 | 依赖 verifier 生产 adapter（不存在） | 在 adapter 落地前，K2 不渲染"确认"（§2.5c），该动作标 BLOCKED |

---

## 6. 与批次 49 计划条目的映射、以及必须先认账的前置缺件

| 计划条目 | 本设计落点 | 前置缺件（**不是 UI 能补的**） |
|---|---|---|
| P2-11 KD-67 确认卡 | K1（答前换题/跳过）+ K2（答后确认条）+ K3（会话末汇总）；装配点在 `SmartMistakeBookRoot.kt:939` 的 `KnowledgeReviewSessionScreen(...)` 调用处 | ① `AnswerKeyVerificationSourceVerifier` **生产 adapter**（否则任何确认都 `SOURCE_NOT_VERIFIED`）；② `KnowledgeQuizItemStore` **没有**写核验状态的方法（计划写的 `record` 是 private 构造器，`:142`）；③ 若要走"确认"专用回执，需一条**不与 REPLACE 混用**的确认记录（否则审计面把"确认"读成"改键"，§2.5c） |
| P2-12 校准可见页 | K4，落在 `SchedulingSettingsScreen`（新分区），**不**复用 CapabilityScreen 的"学习预测效果" | ① 最近拟合元数据需**新持久化**（时间/样本/验证 logLoss/是否采纳）：今天只存 21 个数字（`DataStoreSchedulingSettingsStore.kt:83-101`）；② `optimizeSchedulingParameters()` 返回类型需扩（否则"数据不足"与"采纳门拒绝"不可区分，`StudySchedulingCalibration.kt:106` vs `:116-122`） |
| P2-13 投影冲突处置 | 不在本卡体系内（属错题/投影页）；本设计只要求「重新整理」类动作**不进**复习循环 | `StudyExperienceRepository.reprojectAll()` 的进度/失败可见面（本会话未读该实现，未验证） |
| P2-14 知识点冲突 | K5（2 动作、默认继续） | 无（`ASK_CALIBRATION` 已存在：`AdaptiveQuestionSelector.kt:164`、`:222-232`） |
| P2-15 验收行 | §5 各判据按 E/A/I 分域挂行 | `feature/review` 无 androidTest 源集 → A 域用例需落 `app`，或在 `feature/review/build.gradle.kts` 显式新增测试依赖（属构建改动，需授权） |

---

## 7. 未验证、风险与交接

1. **本文全部判据均未执行**。未跑 Gradle、未连设备、未跑 `a11y_audit`。所有"判据"是待建测试的规格，不是结果。
2. **行号会漂**：引用的 `path:line` 取自当前工作树（基线登记 595 个未提交路径）。他线改动落在 `feature/review` 测试文件与 `app` 时，本文件的行号可能失效；以符号名为准。
3. **单一来源声明**：Anki 的"leech=8/tag+suspend"与四键语义、SuperMemo 的 leech 定义与向导选项，均**只取了一个页面**（证据件 §2.5 已逐条标注）。SuperMemo 默认阈值**未取到**，本文未使用。Anki 在 FSRS 之后各键的调度差异未取（只读当前稳定版手册）。
4. **"每会话 ≤1 次打断"是设计预算，不是实测**：没有真实学生会话频次数据；建议在实现后先用离线模拟（计划 P1 的 `SimulatedLearner`，尚未落地）统计"每会话 CLARIFY 命中节点数"再定 1 还是 2。
5. **K2 的"确认"存在产品风险**：即使 verifier 落地，"学生确认自己刚看过的答案"提供的证据强度很弱（`USER_ASSERTED` 的语义是"用户明确断言"，不是"独立核验"）。建议把它定位成"允许继续出题的最小信任"，不要让它在任何界面被读成"这题已核对"（对应文案必须写"你确认过"，不写"已核对"）。
6. **冲突面**：`feature/review` 的测试文件与 `app` 正被他线修改（基线 §5.1/§5.2）；本设计不要求整文件改写，落地时应按最小 hunk。
7. **交接**：若要把本文变成实现，建议顺序为 `E 域合同判据（纯函数，零依赖）` → `K1/K3（不需要写路径）` → `K4（先补持久化）` → `K2（等 verifier）`。这个顺序的每一步都能独立验收，且不会出现"UI 先上线、写路径永远缺席"的半接线。
