# 证据：澄清/确认卡与校准可见页信息架构研究（批次 49 / R2 产品闭环）

| 字段 | 值 |
|---|---|
| 落盘 | `docs/research/evidence/2026-11-08-batch-49/clarification-ux-sources.md` |
| 归属产物 | `docs/research/2026-11-08-clarification-ux.md` |
| 角色 | 产品/交互研究：**只读**源码 + 外部取件；未改任何主码、测试、台账、金样 |
| 未做 | **未跑任何 Gradle / 任何 `am instrument` / 未连设备 / 未 `git add`**；本文不含任何 PASS 判定 |
| 工作目录 | `D:/smart mistake book`（Git Bash，win32） |

---

## 1. 本地源码读点（命令 → 关键输出）

### 1.1 计划与基线

```text
$ wc -l .zcode/plans/plan-2026-11-08-mastery-review-quality.md
107 .zcode/plans/plan-2026-11-08-mastery-review-quality.md      ← 实读全文（第 25-31 行 = P2 产品闭环 11-14）
$ wc -l docs/research/2026-11-08-batch-49-baseline.md
223 docs/research/2026-11-08-batch-49-baseline.md               ← 实读全文（§5 热区、§5.5 KD-67 底座）
```

### 1.2 CLARIFY / KD-67 契约

```text
$ grep -rn "AdaptiveClarification" --include=*.kt . | head -40
core/domain/.../AdaptiveSelectionConsumption.kt:19   interface AdaptiveClarificationConsumer
core/domain/.../AdaptiveSelectionConsumption.kt:21   suspend fun onClarificationRequired(request)
core/domain/.../AdaptiveSelectionConsumption.kt:48   data class AdaptiveClarificationRequest
core/domain/.../AdaptiveSelectionConsumption.kt:81   fun AdaptiveDecision.clarificationRequest(...)
core/data/.../AdaptiveSelectionAssembler.kt:308      suspend fun selectAndConsume(...)
core/data/.../AdaptiveSelectionAssembler.kt:314      clarificationConsumer: AdaptiveClarificationConsumer,
```

- `AdaptiveClarificationRequest` 字段实读（`AdaptiveSelectionConsumption.kt:48-72`）：
  `learnerId / targetKnowledgeNodeIds / requestedTargetKind / candidates / reasonCodes / selectorVersion / decisionAtEpochMillis`；
  `AdaptiveClarificationCandidate`（`:28-40`）只有 `assessmentItemId / itemFamilyId / knowledgeNodeIds`
  → **载荷不含题面、选项、正确答案、知识点显示名**。
- 消费者契约「同一决策重放可能重复调用，实现方需幂等」（`:20-21`）。

### 1.3 选择器：CLARIFY 何时产生、reasonCodes 全集

```text
$ grep -n "AdaptiveDecisionKind\|reasonCodes\|setOf(" core/domain/src/main/kotlin/.../AdaptiveQuestionSelector.kt
:153  noCandidateDecision(request, setOf("QUESTION_NOT_REQUESTED"))
:162  setOf("PROJECTION_NOT_CURRENT")
:164  selectCalibration(request, setOf("CALIBRATION_REQUESTED"))
:173  setOf("CLEARLY_MASTERED_FOR_SKIP")
:199  setOf("LEARNING_EVIDENCE_NOT_CURRENT")
:210  setOf("PREDICTION_UNKNOWN_STALE_OR_OUTSIDE_CORRIDOR")
:299-315 noCandidateDecision(...) → CLARIFY_OR_CONFIRM **当且仅当**存在未核验目标，并附加 "TARGET_CONTENT_NOT_VERIFIED"
:242  !candidate.answerWouldBeRevealed &&      ← 揭示态是安全候选的唯一闸
```

- 结论（读码得出）：`CLARIFY_OR_CONFIRM` 的 `reasonCodes` 可能**不含** `TARGET_CONTENT_NOT_VERIFIED`
  （证据过期/预测越界/预测未请求等任一原因都会走同一出口），此时"确认答案"不是可行解法。

### 1.4 候选装配：两类候选，只有一类有键面

```text
$ sed -n 160,235p core/data/src/main/kotlin/.../AdaptiveSelectionAssembler.kt
:188  contentVerified = verified           ← 快照行按 verificationStatus（VERIFIED/USER_ASSERTED）现读
:209  contentVerified = false              ← 错题行恒 false（fail-closed，读面无可判定核验状态）
:210  answerWouldBeRevealed = false        ← 错题行按 false
```

### 1.5 揭示态从来没有非 HIDDEN 的生产写入

```text
$ grep -rn "answerRevealState\s*=" --include=*.kt core/ | grep -v /build/ | grep -v test
core/data/.../KnowledgeQuizItemStore.kt:183   answerRevealState = ANSWER_REVEAL_STATE_HIDDEN,
core/database/.../RoomStudyDatabaseMappings.kt:114/133  （映射透传）
$ sed -n 222,232p core/data/.../KnowledgeQuizItemStore.kt
private companion object {
    ...
    const val ANSWER_REVEAL_STATE_HIDDEN = "HIDDEN"
```

- 唯一生产写口恒写 `HIDDEN` → 今天"学生已看过答案"这件事**结构上不会被记录**。

### 1.6 核验升级：来源回执与生产 adapter

```text
$ grep -rn "AnswerKeyVerificationSourceVerifier" --include=*.kt . | grep -v /build/ | grep -v .worktrees
core/domain/.../AnswerKeyVerificationUpgrade.kt:112   fun interface AnswerKeyVerificationSourceVerifier
core/data/.../KnowledgeQuizItemVerificationUpgrader.kt:138/148  构造参数
core/data/src/test/.../KnowledgeQuizItemVerificationUpgraderTest.kt:87   （仅测试实现）
core/data/src/test/.../AdaptiveSelectionAssemblerTest.kt:445             （仅测试实现）
```

- **生产实现不存在**（只有测试夹具）；`confirmationId` 必须指向真实回执
  （`AnswerKeyVerificationUpgrade.kt:43-61`、`:112-118`；被拒因 `SOURCE_NOT_VERIFIED` 见 `:94-99`）。
- 回执形状：`AnswerKeyCorrectionReceipt(correctionId, status, …)`（`KnowledgeQuizFeedbackWriter.kt:551-564`）。

### 1.7 计划 §11 与代码不符的一处（记录）

```text
$ grep -n "suspend fun \|fun record" core/data/src/main/kotlin/.../KnowledgeQuizItemStore.kt
:42   suspend fun read(...)
:83   suspend fun readSnapshot(...)
:109  suspend fun save(...)
:142  private fun record(snapshotId, item, occurredAtEpochMillis): AssessmentItemSnapshotSeedRecord
```

- 计划 §P2-11 写「`KnowledgeQuizItemStore.record` 写核验状态」；实际上 `record` 是 **private 行构造器**，
  本类**没有**写核验状态的方法（该类 KDoc 明确"键信任写口 = 升级器"）。

### 1.8 复习会话现状（动作序列 / 状态机 / 已有卡片）

```text
$ grep -n "enum class KnowledgeQuizLoadStatus" -A 20 feature/review/.../KnowledgeReviewSessionViewModel.kt
:17-35  IDLE / LOADING / LOADED / FAILED / KNOWLEDGE_PREPARING / NO_MATERIAL
$ grep -n "testTag(" feature/review/.../KnowledgeReviewSessionScreen.kt
:144 knowledge_review_finish   :215 knowledge_review_loading   :367 knowledge_review_submit
:384/388 knowledge_review_report_issue_neutral / knowledge_review_report_issue
:471 knowledge_review_correct_key   :604 knowledge_review_verdict   :670 knowledge_review_skip
:281 knowledge_review_next
```

- 每个知识点的必点次数（读码）：选项行 1 次（`ReviewChoiceRow.kt:82-85` clickable）+ 提交 1 次（`Screen:353-375`）
  + 下一知识点 1 次（`Screen:277-285`）= **3 次**；「答案有问题」「更正答案」均为可选入口。
- **既有纪律**：KD-43 的「更正答案」只在已提交后出现（`Screen:455-479` 的 `viewModel.submittedChoice != null` 守卫）；
  其对话框文案「更正后，将按新答案重新判分…原作答记录会保留」（`Screen:496-497`）。

### 1.9 既有确认卡（可借语法，不可借链）

```text
$ wc -l feature/tutor/src/main/java/.../TutorPendingRequestCard.kt
171
$ sed -n 45,104p feature/tutor/.../TutorPendingRequestCard.kt
:57-64   Surface（PendingSurface + 20dp 圆角）:69-78 标题 + 一句 detail
:79-82  busy 行 liveRegion=Polite  :83-101 FlowRow：PrimaryActionButton(accept) + OutlineActionChip(暂不处理)
:84     maxItemsInEachRow = if (fontScale > 1.3f) 1 else 2
:42-44  KDoc：卡是「行」的视图，进程死亡后重建的是同一张卡
```

- 与澄清请求的**结构差异**：确认卡背后是 `agent_pending_request` **可写行**（有终态），
  澄清请求是**只读决策快照**且消费者可能被重复调用（`AdaptiveSelectionConsumption.kt:14-17/20-21`）。

### 1.10 三键自评（Again/Hard/Good 的本仓对位）

```text
$ sed -n 48,118p feature/review/.../ReviewSelfReportControls.kt
:51-56  "没有 Easy"；对=Good / 卡顿=Hard（费力但回忆成功）/ 错=Again
:113-118 selfReportKeyPresentation：("对","顺利回忆") ("卡顿","费力但想起来了") ("错","没想起来/答错")
:746-758 maxWidth < 80.dp*fontScale*3+16.dp → 三键改整行竖排
:794-799 OutlinedButton heightIn(min = 64.dp)
```

### 1.11 校准面（今天有 / 没有）

```text
$ grep -n "SettingsSectionTitle\|fun .*Screen" app/src/main/.../SecondaryScreens.kt
:125 CapabilityScreen   :416 "学习预测效果"（在 CapabilityScreen 内，仅当 calibrationReportProvider != null）
:54  SourceCalibrationSection 位于同一面板内
$ grep -n "PARAMETER_COUNT" core/domain/.../FsrsScheduleMath.kt
:48 const val PARAMETER_COUNT = 21
$ grep -n "optimizedParameters" core/data/.../DataStoreSchedulingSettingsStore.kt
:83 override val optimizedParameters: Flow<DoubleArray?>
:95 override suspend fun setOptimizedParameters(parameters: DoubleArray?)
   → 只持久化 21 个数字本身；**无**时间戳 / 样本数 / logLoss
$ sed -n 95,125p core/data/.../StudySchedulingCalibration.kt
:106 if (result.mode == FsrsParameterOptimizer.Mode.INSUFFICIENT_DATA) return null
:116 if (!shouldAdopt(candidateLoss, existing, existingLoss)) { … return null }
:134-143 shouldAdopt(...) 四分支
$ grep -n "data class Result" -A 8 core/domain/.../SchedulingEvaluation.kt
:463-470 Result(parameters, mode, trainLogLoss, validationLogLoss, sampleCount, optimizedParameterIndices)
:316-337 calibratePlannedReasons(...) → PlannedReasonCalibration（已算，无 UI）
$ grep -n "EXAM_CAPACITY_RAMP_MAX_EXTRA_FRACTION" core/domain/.../AlgorithmConstants.kt
:148 = 0.5（KDoc 标 PENDING_CALIBRATION，:140-147）
$ grep -n "const val VERSION" core/domain/.../ReviewPlannerV2.kt
:1055 "review-planner-v4"
$ grep -n "SELECTOR_COMPOSITE" core/domain/.../LearningCoreVersions.kt
:136 "learning-core-v14(selector-v7,…)"
$ wc -l app/src/main/.../SchedulingSettingsScreen.kt
237   ← 已有「复习安排」页（root_scheduling_settings）：每日时长 / 记忆目标 / 考试日历；:37-40 明写"改动在下次启动生效"
```

### 1.12 测试设施边界（决定判据能放哪层）

```text
$ sed -n 1,35p feature/review/build.gradle.kts
依赖只有 core:model / core:domain / core:ui / lifecycle；testImplementation junit + coroutines-test
   → **无 compose-ui-test、无 androidTest 源集**
$ grep -n "compose.ui.test" app/build.gradle.kts
:199 debugImplementation(libs.compose.ui.test.manifest)
:201 androidTestImplementation(libs.compose.ui.test.junit4)
$ ls feature/review/src/test/kotlin/.../review/
… ReviewSelfReportUiContractTest.kt   ← 既有"界面合同（不依赖 Compose 渲染）"范式
$ sed -n 195,282p tools/ci/a11y_audit.py
审计项：TOUCH_TARGET_TOO_SMALL / MISSING_LABEL / BOUNDS_UNREADABLE / CONTRAST_TOO_LOW；:51 DEFAULT_MIN_DP=48.0；缺颜色输入 → contrast UNVERIFIED
$ grep -n "MinimumTouchTarget" core/ui/src/main/java/.../Theme.kt
:80 val MinimumTouchTarget = 48.dp
```

---

## 2. 外部取件（URL → 逐字引文 → 采纳/拒绝）

> 取件方式：`WebFetch`（页面转 markdown 后按提问取原文）。**每条只取了一个页面**；下表中标「单一来源」者未做第二渠道复核。

### 2.1 Anki：四个评分键的官方定义
URL：`https://docs.ankiweb.net/studying.html`

- "Select this when your answer is incorrect or when you couldn't recall the answer."（Again）
- "if it counts as a fail in a real-life context outside of Anki, then it counts as a fail in Anki as well."
- "Select this button when your answer is correct, but you had doubts about it or it took a long time to recall."（Hard）
- "Select this when your answer is correct, but it took some mental effort to recall it."（Good，"should be the most commonly used button"）
- "Select this if your answer is correct and it took no mental effort to recall it."（Easy）

**采纳**：Again 承接"没想起来/答错"、Hard 承接"想起来但费力/有疑虑"——与本仓三键（对/卡顿/错）同构，
本仓无 Easy（`ReviewSelfReportControls.kt:51-56`）。
**拒绝**：四键 → 三键。理由是本仓 `ReviewSelfReportKey` 只有三档，`EASY` 在证据映射里结构性不存在
（`SchedulingEvaluation.kt` 优化器注释：EASY 的 w16 训练集为空）。

### 2.2 Anki：leech 阈值与处置
URL：`https://docs.ankiweb.net/leeches.html`

- "cards that you keep forgetting"，"take up a lot more of your time"
- "When this counter reaches 8, Anki tags the note as a leech and suspends the card."
- 继续失败时 "half the initial leech threshold" 再警告（8 → 12、16…）
- 推荐处置："Editing … the most efficient method"；其次是删除/"leave it suspended"；干扰类"Waiting"

**采纳**：① 「要决策的事」放在复习**之外**（tag + suspend，事后处理）而不是复习循环里；
② 默认动作是"先不动它"，而不是要求当场处置。
**拒绝**：默认 suspend 本仓无对应语义（会改计划输出），不在本轮引入。
> 另一页 `https://docs.ankiweb.net/deck-options.html` 的 Leeches 条目只写 "For details, see the leeches section."，未给数值——**不构成第二来源**。

### 2.3 SuperMemo：leech 定义
URL：`https://supermemo.guru/wiki/Leech`

- "Leeches in SuperMemo are questions that a student keeps forgetting."
- 多因 "disrespect for the 20 rules of knowledge formulation"；好题也可能需要"mnemonic techniques"或"coherence"

### 2.4 SuperMemo：Leech Alert 向导（打断形态）
URL：`https://help.supermemo.org/wiki/Leeches`

- "An element becomes a leech when the number of memory lapses passes a defined limit"
- "the following dialog will be displayed **each time** you encounter a leech during repetitions"（"Leech Alert! wizard"）
- 选项逐字：Continue (Esc) / Show answer / Postpone / Edit / Forget / Dismiss / Delete / Reschedule / Parameters / Auto-postpone / Auto-forget / Include semi-leeches / Great–Bad
- **默认阈值本页未给数值**（半 leech 示例为"five or more lapses and an interval up to 60 days"）
  → 「SuperMemo 默认阈值」**未取到，不作事实使用**。

**采纳**：① 每次遇到都弹 + 十余个选项 = 复习被决策淹没的反面样本（本设计明确不做）；
② "Continue (Esc)" 作为一等出口——任何卡都必须有"不改任何东西也能继续"的出口。
**拒绝**：leech 向导式多选项对话框。

### 2.5 未取到 / 单一来源声明

| 事实 | 状态 |
|---|---|
| Anki leech 默认 8 次、tag + suspend | 单一来源（`docs.ankiweb.net/leeches.html`） |
| Anki 四键语义逐字 | 单一来源（`docs.ankiweb.net/studying.html`） |
| SuperMemo leech 定义 | 单一来源（`supermemo.guru/wiki/Leech`） |
| SuperMemo Leech Alert 选项清单 | 单一来源（`help.supermemo.org/wiki/Leeches`） |
| SuperMemo 默认阈值数值 | **未取到**（页面未给） |
| Anki 各版本最大差异（如 FSRS 后的 Hard 语义） | **未取**（只读了当前稳定版手册页） |

---

## 3. 本会话未做（不得被读成已通过）

1. 未跑任何 Gradle 任务（`test` / `connected*` / 门禁）。
2. 未连设备、未 `uiautomator dump`、未截图、未跑 `tools/ci/a11y_audit.py`。
3. 未改任何主码/测试/台账/金样；未 `git add`/`commit`。
4. 本文所有 `path:line` 均来自本会话读取；行号以当前工作树（含他线未提交改动）为准，
   与 HEAD 可能有偏移。
