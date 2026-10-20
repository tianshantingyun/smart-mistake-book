# 批次 16 记录（2026-10-21）：PRD-05 三键/undo + 4E WG-04 + PRD-06 能力快照 + 6B-2/3

> 记录时点：2026-10-21。本文件由记录员只读核对源码、计划与门禁产物后写入；**未运行 Gradle、未执行 git 写操作、未安装/运行设备用例**。
> “已落地/已写”只表示当前工作树存在该源码或测试文件；“通过”只在给出当前门禁产物或本记录实际执行的命令时使用，否则一律写 UNVERIFIED。
> 计划：`.zcode/plans/plan-2026-10-21-batch-16-prd05-wg04-capabilities.md`；基线：`docs/research/2026-10-21-batch-16-baseline.md`。

## 1. 范围与基线

### 1.1 依据

- 计划 §1 范围（`:16-18`）：PRD-05 本地三键/undo/无模型复习；4E WG-04a SSE 终态门；WG-04b method/proposal + 请求 schema 18；PRD-06 能力快照与未接线不广告；6B-3 有界自然语言改卷；6B-2 原生编辑入口；KD-43(b)/47/62 小修；WG-06/07/08 schema 69 设计件。
- 明确不做：WG-06/07/08 实施、WG-02/03/09/10、JS/QuickJS、PRD-09 质量门、PRD-10 的 5b/5c/5B/6C-2、PRD-11、UI-00~11、PRD-05 真实数据启用（等 3D 全门）、KD-61 完整关闭。
- 文件所有权：T1~T7 按计划分轨；`SmartMistakeBookRoot.kt` / `SmartMistakeBookApplication.kt` 由 I1 独占；`OpenAiModelProtocol.kt` / `RoomModelTaskRepository.kt` 由 T3 起整文件纳入并保留他线 hunk。本记录员未改任何代码。

### 1.2 基线事实与记录时点的变化

| 项 | 基线（T0 文档） | 记录时点 |
| --- | --- | --- |
| HEAD | `a200f27b` | 未运行 git；按工单仍 `a200f27b` |
| origin/main | `1d8bb937` | 未运行 git；按基线仍 `1d8bb937` |
| 本地领先 | 4 笔（`a200f27b`、`95b00272`、`8a100b59`、`8eb69460`） | 未推送；本批未提交 |
| DB schema | 68 | **68**（`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabase.kt:130`） |
| 请求 schema | 17 | **18**（`core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/ModelTasks.kt:546-547`） |
| PRD 勾选 | 50 个未勾 / 0 已勾 | 仍 50 / 0（`docs/superpowers/plans/2026-10-19-product-readiness-followup-plan.md`） |
| 设备 | 基线时 `adb devices` 空 | 记录时点 `emulator-5554	device` 在线（本记录执行 `C:/Android/Sdk/platform-tools/adb.exe devices`），但 D1 未执行任何用例 |

记录时点工作树仍含大量他线在飞改动（基线 473 行）；本批新增文件/改动未提交。本记录**不代提交、不代修他线语义**，也**未运行 git 状态命令**（按本轨铁律“不运行 git”）。

### 1.3 轨与最终状态

| 轨 | 自报状态 | 记录员判定（记录时点） | schema |
| --- | --- | --- | --- |
| T1a PRD-05 数据契约 | 部分 | 代码级落地；新增测试已写；门禁/设备未验 → **部分** | 68（无 schema 变更） |
| T1b PRD-05 UI | 部分 | 代码级落地；纯单测已写未跑；app 接线见 §2.10 → **部分** | — |
| T2 WG-04a SSE | 部分 | 代码级落地；32 个用例已写（聚合 24 + 流 4 + 选择 4）；未跑 → **部分** | — |
| T3 WG-04b + schema 18 | 部分 | 代码级落地；测试已写；未跑 → **部分** | 请求 18 |
| T4 PRD-06 能力快照 | 部分 | 代码级落地；测试已写；未跑 → **部分** | — |
| T5 6B-3 改卷 | 部分 | 代码级落地；测试已写；真实模型端口未接 → **部分** | — |
| T6 6B-2 原生入口 | 部分 | 代码级落地；测试已写；设备未跑 → **部分** | — |
| T7 小修 | 完成（自报） | 代码级完成；KD 的关闭证据见 §7 / known-defects | — |
| T8 WG-06/07/08 设计件 | — | 文档已落（主会话代写，见文首注） | 设计 69 |
| I1 集成 | 部分 | Root 已接 T6/T7；记录时点树内另有 T1 接线，来源未核实 → **部分** | — |
| D1 设备 | 阻塞 | **阻塞**：强门未绿，未执行 | — |
| C1 收尾 | — | 记录完成；门禁/复核未放行（见 §3/§6/§8） | — |

## 2. 逐轨交付

### 2.1 T1a PRD-05 数据契约（三键自评 + VOID undo + 无模型答案）

**文件。** `core/model/.../LearningState.kt`；`core/domain/.../LearningProjector.kt`、`StudyExperienceRepository.kt`；`core/database/.../entity/LearningEntities.kt`、`StudyDatabaseRecords.kt`、`RoomStudyDatabase.kt`、`RoomStudyDatabaseMappings.kt`、`DatabaseContract.kt`、`dao/LearningDao.kt`、`dao/ReviewDao.kt`、`port/LearningProjectionPort.kt`；`core/data/.../study/ReviewLogSink.kt`、`RoomBackedStudyExperienceRepository.kt`、`StudyPracticeUnitFacts.kt`、`StudySubmissionPreparer.kt`；测试 `RoomBackedStudyExperienceRepositoryTest.kt`、`StudySubmissionWithoutFixtureInstrumentedTest.kt`、`ReviewSelfReportContractTest.kt`、`ReviewUndoReplayTest.kt`、`ReviewUndoTransactionInstrumentedTest.kt`、`SelfReportedReviewInstrumentedTest.kt`。

**交付。**
- 三键映射：`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/StudySubmissionPreparer.kt:194-311`——对=`Good` 原档 0.8、卡顿=`Hard` 原档 0.7（两者均 `SELF_REPORTED_RECALL` 且 `ASSISTED_RECALL`，永不是 `INDEPENDENT_CORRECT`）、错=`Again` 原档 1.0（`SELF_REPORTED_STUCK`）；三键不走注意力/时长折扣，原档落 `review_log.evidence_weight`。
- 自评提交/撤销/无模型读口：`core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/StudyExperienceRepository.kt:464-485`；实现 `core/data/.../RoomBackedStudyExperienceRepository.kt:669`（提交）、`:745`（撤销）、`:807`（已存答案）；`StudyPracticeUnitFacts.kt:142`（`selfReportFacts` 不要求机判工件）、`:186`（`storedAnswerFor` 显式 `isTrusted=false`）。
- VOID 哨兵（无 schema）：`core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/LearningState.kt:715-767`——`[void-attempt:v1]` 前缀 + `NONE/0/ANSWER_REVEALED` 的 schema 合法零证据形状；构造期 `require` 禁止带前缀却携带真实证据（`:715-724`）；`isVoid` `:728-729`，`voidOf` `:748`。
- 投影排除：`core/domain/.../LearningProjector.kt:586` 在重放路径进入 `projectMemory` 前分支；`voidedAttemptIds` 在 `:810`。保留 attempt 行、`responseOrdinal`、曝光/审计；REPLACE 仍在原账本位置/时间重算。增量投影遇更正走 `FULL_REPLAY_REQUIRED`（`ProjectionTransactionDao` 既有分支），故 VOID 只在全量重放消费。
- DB 原子 undo：`core/database/.../RoomStudyDatabase.kt:1502-1537` 在同一 `withWriteTransaction` 内追加更正、回退会话、`markReviewLogSchedulingIneligible`（`LearningDao.kt:234`）；`ReviewDao.kt:539-627` 的 `rewindSession` 以 advance receipt 证明“只撤销最后一次推进”，写新 revision 且版本继续递增。
- 版本/序号解耦：`DatabaseContract.kt:724-726` 将不变量从 `stateVersion == currentOrdinal` 放宽为 `stateVersion >= currentOrdinal`（撤销回退一格但不倒退版本）。
- 有效证据口径：`ReviewLogSink.kt:144-163`；`LearningDao.kt:205/215-221/322` 带出 `scheduling_eligible`、过滤撤销行；schema 68 已含该列（`68.json:8729`；67.json 同列已在），无需迁移/69。

**测试（已写）。** `ReviewSelfReportContractTest.kt`、`ReviewUndoReplayTest.kt`、`ReviewUndoTransactionInstrumentedTest.kt`、`SelfReportedReviewInstrumentedTest.kt`（记录时点已存在，含 `submitReviewSelfReport` 真仓库用例）、`RoomBackedStudyExperienceRepositoryTest.kt` 增量、`StudySubmissionWithoutFixtureInstrumentedTest.kt` 两例。
**未达/未验证。** 未运行 Gradle（铁律）；instrumented 未上设备；`LearningProjector.VERSION` 未 bump 的判据见 ledger §3.26；`TutorJudgedReviewSettler.kt:101-112` 仍把 `expectedStateVersion` 当队列 ordinal，undo 后走“请求讲题”路径可能 fail-closed（未改他线文件）；`reviewLogSink.record` 与并发 undo 的窄窗口未测。

### 2.2 T1b PRD-05 会话 UI

**文件。** `feature/review/.../ReviewSelfReportControls.kt`、`ReviewSessionScreen.kt`、`CapturedReviewSessionScreen.kt`；测试 `ReviewSelfReportViewModelTest.kt`、`ReviewSelfReportUiContractTest.kt`。

**交付。**
- 三键单一文案 `selfReportKeyPresentation`（`feature/review/.../ReviewSelfReportControls.kt:108`），控件遍历 `ReviewSelfReportKey.values()`（`:725`）；无 Easy。
- 通道完整性 `selfReportChannelReady`（`:119-128`），两处会话入口同判定（`ReviewSessionScreen.kt:145-150`、`CapturedReviewSessionScreen.kt:129-134`）。
- 提交中断锁键：`hasPendingSubmit`（`:228-229`），pending 时拒绝换键（`:241`），只放行原判定键（`:134-139`）。
- undo 持久回执：`ReviewUndoReceipt`（`:71-75`），成功后持久化 `correctionId/created/next`（`:343-354`），跨旋转恢复（`:581-593`）；`created=false` 重放不再回退。
- undo→重评：`attemptOrdinal` 成功后 +1（`:350-352`），下一次 `pendingCommand` 取最新会话版本与新 ordinal（`:428-446`）。
- 题版本/会话身份：`CapturedReviewSessionScreen.kt:97-101/362-378` 默认并入 `problemRevisionId + practiceUnitId + 会话身份`。
- 无模型路径：`storedAnswerAvailability` 区分 TRUSTED/UNTRUSTED/UNKNOWN（`:180-192`）；两屏如实提示并提供跳过/请求讲题；录后展示已存答案/解析（`ReviewSessionScreen.kt:618`）。
- 记录后写曝光（记录时点新增）：`ReviewSessionScreen.kt:470-486` 在自评已记录且有可信答案时调用 `onRevealAnswer`，失败提示在 `:619`——消灭“撤销后重评被当成未看过答案”的失败。

**测试（已写）。** `ReviewSelfReportViewModelTest.kt`（9 例）、`ReviewSelfReportUiContractTest.kt`（7 例，含三键无 Easy、半接线不接管、题版本/会话身份）。
**未达/未验证。** 未跑 `:feature:review:testDebugUnitTest`；无 Compose/设备渲染；端到端本地闭环依赖 app 接线（§2.10）。

### 2.3 T2 4E WG-04a SSE 终态门

**文件。** `core/data/.../model/OpenAiSse.kt`、`OpenAiModelTransport.kt`、`wire/OpenAiChatCompletionsProtocol.kt`、`OpenAiCompatibleModelGateway.kt`；测试 `OpenAiSseToolCallAggregationTest.kt`、`OpenAiSseToolCallStreamTest.kt`、`ModelStreamSelectionTest.kt`。

**交付。**
- 聚合与终态门：`core/data/.../model/OpenAiSse.kt:156` `reconstructedChatCompletion` 按 `index` 合并 `delta.tool_calls`；`:200-207` 拒绝“声明 tool_calls 却无调用”；`toWire`（`:411`）要求 id/type/name 完整、arguments 非空且严格解析为单个 JSON object；`toWireCalls`（`:499-522`）要求 `finish_reason=tool_calls`，`length/content_filter`/缺失 → `IncompleteModelOutputException`，`stop` 等 → `InvalidModelResponseException`，index 必须 `0..n-1` 连续。
- 预算：`:563` `MAX_TOOL_CALL_PAYLOAD_UTF8_BYTES = 512*1024`（整轮全部 tool arguments 解码后 UTF-8 总和）；`:566` `MAX_DECODED_TOOL_SOURCE_UTF8_BYTES = 64*1024`（sourceJs）；`:572` `MAX_STREAMED_TOOL_CALLS = 128`。`:250`/`:485`/`:535` 在读取/累积期增量计费，超限立即抛错；`:237` 对非流式完整响应同样生效。
- 传输层：`OpenAiModelTransport.kt:300-302` 仅对 2xx 且支持聚合的协议创建读取期预算观察器；`:410` `Utf8SseAccumulator` 用有状态 `CharsetDecoder` 跨 read 解码。
- 协议/网关：`wire/OpenAiChatCompletionsProtocol.kt:221` 仅该协议 `supportsStreamedToolCallAggregation=true`；`OpenAiCompatibleModelGateway.kt:172-183` 计算 `nativeToolRound` 并调用 `shouldStreamModelResponse`，`:723-730` 在不支持聚合且声明工具时返回 false（强制 `stream=false`）。
- 文本流终态（记录时点新增）：`OpenAiSse.kt:221-229` 对“既无 `finish_reason` 又无 `[DONE]`”的非工具文本流抛 `IncompleteModelOutputException`，不再把半段正文当完整答复。

**测试（已写）。** `OpenAiSseToolCallAggregationTest.kt`（24 例）、`OpenAiSseToolCallStreamTest.kt`（4 例）、`ModelStreamSelectionTest.kt`（4 例）。
**未达/未验证。** 未跑 `:core:data:testDebugUnitTest`；真实 Provider 偏差（`stop`+tool_calls、缺 id、name 逐字符、坏 frame）未实测；SSE 分帧仍只按 `\n\n`（CRLF 不触发读取期早停）；预算观察器对普通文本流也逐帧严格解析（见 §6 低危登记）。

### 2.4 T3 4E WG-04b method/proposal + 请求 schema 18

**文件。** `core/model/.../ModelTasks.kt`、`TutorTasks.kt`、`TutorToolLoop.kt`、`TutorToolTrace.kt`、`TutorVisualContext.kt`、`FigureGeneration.kt`；`core/data/.../model/FigurePromptAssembler.kt`、`OpenAiModelTaskAdapters.kt`、`OpenAiModelResponseParsers.kt`、`OpenAiModelProtocol.kt`、`TutorFigureGenerator.kt`、`study/RoomTutorToolRunner.kt`、`RoomModelTaskRepository.kt`；测试 `FigureToolPromptInjectionTest.kt`、`TutorFigureMethodDualParseTest.kt`、`TutorFigureProposalExecutionTest.kt`、`TutorFigureProposalCallTest.kt`、`TutorToolFigureTraceFieldsTest.kt`、`TutorVisualContextFingerprintTest.kt`。

**交付。**
- `TutorToolCall` 新载荷：`core/model/.../TutorToolLoop.kt:205-218`（`figureMethod`/`figureProgram`/`figureImageOperation`/`figureImagePrompt`）；`:343-372` JS/program 一律拒、IMAGE_MODEL 必须成对带 operation+prompt；`:394-406` `toFigureGenerationProposal`。
- 双路由同一解析点：`core/data/.../model/OpenAiModelResponseParsers.kt:454-539`（Route B）；原生 `OpenAiModelProtocol.kt:752` 改为 `arguments.toTutorToolCall(...)`；原生 schema `:458-510` 只广告 `method=IMAGE_MODEL`、`imageOperation=EDIT/GENERATE`、有界 `imagePrompt`，schema 内不含 JS。
- 请求 schema 18：`ModelTasks.kt:546-547`；`TutorVisualContext.kt:17` 用 `@EncodeDefault(NEVER)`，`TutorTasks.kt` 字段声明在 `:259-260/488-490`。
- 提示词注入：`OpenAiModelTaskAdapters.kt:283/379` 调 `figureRuleBodyFor`，`:619-622` 用 `FigurePromptAssembler.assembleForTutorTool` + `sharedOperationFigureBudget(reserveChatFinalAnswer=true)`；Lobby 不注入。
- 生成器/执行：`TutorFigureGenerator.kt` 增 `imageOperation/imagePrompt`；`RoomTutorToolRunner.kt` 把 method/purpose/operationId/status 写入痕迹。

**测试（已写）。** `TutorFigureMethodDualParseTest.kt`（7 例）、`FigureToolPromptInjectionTest.kt`（6 例）、`TutorFigureProposalExecutionTest.kt`（4 例）、`TutorFigureProposalCallTest.kt`、`TutorToolFigureTraceFieldsTest.kt`、`TutorVisualContextFingerprintTest.kt`。
**未达/未验证。** 未跑 `:core:model:test`/`:core:data:testDebugUnitTest`；`visualContext` 只进入请求编码/指纹，尚未进入 Plan/Respond 提示词或工具 schema（§6 低危）；真实 Provider IMAGE_MODEL EDIT/GENERATE 未验证；`FigureGeneration.kt` 的 `@Serializable` 超出计划点名清单，需集成轨知悉。

### 2.5 T4 PRD-06 能力快照 + 未接线不广告

**文件。** `core/model/.../Capabilities.kt`、`TutorLocalAction.kt`；`core/domain/.../TutorLocalActionAdmission.kt`、`TutorLocalActionExecution.kt`；`core/data/.../model/OpenAiModelProtocol.kt`、`OpenAiModelTaskAdapters.kt`、`RoomModelTaskRepository.kt`；`feature/tutor/.../TutorPendingRequestCommands.kt`、`TutorPendingRequestCard.kt`；测试 `TutorCapabilitySnapshotTest.kt`、`TutorLocalActionAdmissionTest.kt`、`TutorLocalActionExecutionTest.kt`、`TutorToolGateTest.kt`、`TutorLocalActionAdvertisementParityTest.kt`、`RoomAgentPendingRequestRepositoryTest.kt`、`RoomModelTaskToolLoopInstrumentedTest.kt`、`TutorPendingRequestCommandsTest.kt`。

**交付。**
- `core/model/.../Capabilities.kt:47` `TutorCapabilitySnapshot`；`:70` `withDependencies(imageBackendReady, knowledgeMaterialReady)`；`:88` `IMPLEMENTED_TOOLS`（八枚）；`:104` `IMPLEMENTED_LOCAL_ACTIONS` 仅 `OPEN_PROBLEM/SAVE_TO_NOTEBOOK/START_EXPORT`；`:115` `forRound` 只取本轮声明集且本地动作仅大厅轮。
- 准入：`core/domain/.../TutorLocalActionAdmission.kt:56/95/163-177`，未实现动作不进广告也不准入；`ADD_TO_REVIEW_PLAN` 执行端仍 `NOT_WIRED_YET`（`TutorLocalActionExecution.kt:116`）。
- 两路由同源：`OpenAiModelProtocol.kt:233/262-283`；`OpenAiModelTaskAdapters.kt:179/288/447/461/516-565`；`RoomModelTaskRepository.kt:281-284/524-527/931-958`。
- 旧卡文案：`TutorPendingRequestCard.kt:144-148` 明说“这一步现在还不能自动做”。

**测试（已写）。** `TutorCapabilitySnapshotTest.kt`（4 例）、`TutorLocalActionAdmissionTest.kt`（13 例）、`TutorLocalActionExecutionTest.kt`（9 例）、`TutorToolGateTest.kt`（4 例）、`TutorLocalActionAdvertisementParityTest.kt`（6 例）、`TutorPendingRequestCommandsTest.kt`、`RoomModelTaskToolLoopInstrumentedTest.kt` 增量。
**未达/未验证。** 未跑 Gradle；真实 Provider/设备上的“图像后端就绪”判定未实测；快照之外仍存在工具/动作名单（`TutorToolGate.kt`、`TutorPermissionPolicy.kt`、feature 层），靠测试与人工同步（§6 低危）。

### 2.6 T5 6B-3 有界自然语言改卷

**文件。** `core/model/.../PaperEditCommand.kt`；`feature/tutor/.../TutorPaperEditApplier.kt`、`TutorPaperEditAssistant.kt`、`TutorConversationViewModel.kt`、`TutorConversationViewModelFactory.kt`、`TutorExportSheet.kt`、`TutorExportEditSheet.kt`、`TutorLobbyRoute.kt`、`TutorRoute.kt`；测试 `PaperEditCommandParserTest.kt`、`TutorPaperEditApplierTest.kt`、`TutorExportModelEditTest.kt`。

**交付。**
- `core/model/.../PaperEditCommand.kt:26-98/138-346` 白名单七命令 + 严格形状（键集/类型/长度/offset）。
- `feature/tutor/.../TutorPaperEditApplier.kt:42-74/78-180` 原子应用、整批拒绝、只改 `MistakePdfPaperEdit` 副本；应用后 `toDraft()` 重算 `inputSha256`（`:65-72`）。
- `TutorPaperEditAssistant.kt:35-130` 提示词只暴露卷面副本的既有 ID/文字；`TutorConversationViewModel.kt:599-690` 接线（busy/拒绝保留/迟到结果不覆盖/端口缺失不广告）；`TutorExportEditSheet.kt:116-151` 入口；`TutorExportSheet.kt:294-318` 透传；`TutorRoute.kt:106/132`、`TutorLobbyRoute.kt:105-127/448-450` 注入点。

**测试（已写）。** `PaperEditCommandParserTest.kt`（10 例）、`TutorPaperEditApplierTest.kt`（13 例）、`TutorExportModelEditTest.kt`（5 例）。
**未达/未验证。** 未跑 Gradle；`TutorRoute.paperEditAssistant` 默认 null，记录时点 **app 中没有 `paperEditAssistant` 注入**（`grep -rn paperEditAssistant app/src/main/kotlin` 无命中），所以生产入口隐藏、真实模型改卷 UNVERIFIED。

### 2.7 T6 6B-2 原生编辑入口

**文件。** `feature/library/.../MistakePaperEditRoute.kt`、`MistakeExportHubRoute.kt`、`MistakeDetailRoute.kt`；测试 `NativePaperEditEntryTest.kt`、`NativePaperEditRouteInstrumentedTest.kt`。

**交付。**
- `feature/library/.../MistakePaperEditRoute.kt:72` 定义 `entryIds/loadPaper/onBack/onExport`；`:279` `confirmNativePaperEdit` 对空白必填文字 fail-closed；`:235` 导出按钮。
- `MistakeExportHubRoute.kt:104` 新增 `onEditPaper`，`:176` 渲染入口；`MistakeDetailRoute.kt` 新增 `onEditPaper`（仅当前版本）。
- 未编辑沿用批量导出同一快照与指纹：`core/export/.../MistakePdfPaperEdit.kt:203-251`（`toDraft()`）；编辑后重算 `inputSha256`、保留来源题面文档指纹。

**测试（已写）。** `NativePaperEditEntryTest.kt`（5 例）、`NativePaperEditRouteInstrumentedTest.kt`（8 例）。
**未达/未验证。** 未跑 Gradle/androidTest；设备链未跑；I1 的 Root 路由接线已落（`SmartMistakeBookRoot.kt` 新增 `mistake/paper-edit/{entryIds}` 与 composable），但原生入口在安装包内可达性未验证；编辑中间态不持久化（重进重建卷面）。

### 2.8 T7 小修（KD-43(b) / KD-47 / KD-62）

**文件。** `core/data/.../study/KnowledgeEvidenceWriter.kt`、`KnowledgeQuizFeedbackWriter.kt`；`feature/review/.../KnowledgeReviewSessionScreen.kt`；`app/.../SecondaryScreens.kt`；`core/data/.../backup/RestoreRecoveryCoordinator.kt`；`core/database/.../RoomBackupSupportStore.kt`；测试 `KnowledgeEvidenceLedgerStatusTest.kt`、`KnowledgeQuizFeedbackWriteTest.kt`、`LatestCorrectedKeyOrderTest.kt`、`BackupTempArtifactCleanupTest.kt`、`RoomBackupCheckpointValidationTest.kt`、`StorageRestoreFeedbackTest.kt`、`AnswerKeyCorrectionInstrumentedTest.kt`、`KnowledgeQuizCooldownChannelScopeInstrumentedTest.kt`。

**交付。**
- KD-43(b) 自动补偿：`core/data/.../study/KnowledgeEvidenceWriter.kt:117` 调 `writeAcceptedWithStickyIdentityCompensation`；`:159-192` 撞被拒观察行后换确定性新身份再写；新回执档位 `LEDGERED_AFTER_REJECTED_IDENTITY`/`REPLAYED_AFTER_REJECTED_IDENTITY`（`:371-380`）；补偿身份也被占住时仍报 `BLOCKED_BY_REJECTED_ROW`（反例）。上层 `KnowledgeQuizFeedbackWriter.kt:250-272` 对新档位返回 `evidenceRecorded=true`。
- UNKNOWN 键 REPLACE：`KnowledgeQuizFeedbackWriter.kt:595-665` 无账本目标但存在 `ANSWER_KEY_UNVERIFIED` 观察行时走 `recordUserAssertedKeyCorrection`（`:804-853`）；保留观察行、按新键重判成新身份证据；`expectedKeyVersion` 为题面级 CAS（`:856-874`）；`latestCorrectedKey` 支持账本序（`:379-445`）。
- UI：`feature/review/.../KnowledgeReviewSessionScreen.kt:455-560`“改正答案键”对话框（testTag `knowledge_review_correct_key`）；`:479-560` 打开时按 `item.id` 现读键版本，提交时带 `loadedKeyVersion`，读不到由 Root 兜底。
- I1 接线：`app/.../SmartMistakeBookRoot.kt:872-950` 稳定 `requestId`、本会话 `conversationId`、`expectedKeyVersion`（`loadAnswerKeyVersion` + 提交前兜底）、按回执状态给如实 Toast。
- KD-47：`app/.../SecondaryScreens.kt:954-962` 新增 `storageRestoreCompletionMessage`，`WRITE_FAILED` 文案改为“设置未能完整写入，可能有部分设置已变。”；调用点在 `:727-732`。
- KD-62①：`core/database/.../RoomBackupSupportStore.kt:15-37/155-184` 读取并校验 `PRAGMA wal_checkpoint(TRUNCATE)` 三列；②：`core/data/.../backup/RestoreRecoveryCoordinator.kt:370-373/400-416` 启动清理 `cache/backup-snapshots/` 与 `databases/backup-snapshot-*.db`。③未动（归 KD-61）。
- 读侧同源修复（记录时点新增）：`KnowledgeQuizItemStore.kt:41-70` 传 `sessionId` 与 `correctionLedgerOrder`，只接受本会话的键替代（否则旧会话常量 `itemId` 会把旧键套到新题面）。

**测试（已写）。** `KnowledgeEvidenceLedgerStatusTest.kt`、`KnowledgeQuizFeedbackWriteTest.kt`（6 例）、`LatestCorrectedKeyOrderTest.kt`（4 例）、`RoomBackupCheckpointValidationTest.kt`（5 例）、`BackupTempArtifactCleanupTest.kt`（2 例）、`StorageRestoreFeedbackTest.kt`（4 例）、`AnswerKeyCorrectionInstrumentedTest.kt` 增量、设备 `KnowledgeQuizCooldownChannelScopeInstrumentedTest.kt` 增量。
**未达/未验证。** 未跑 Gradle；instrumented 未上设备；KD-43 仍 open（见 §7 与 known-defects）；KD-62③/KD-61 未动。

### 2.9 T8 WG-06/07/08 设计件

**文件。** `docs/research/2026-10-21-4e-wg060708-schema-design.md`（新文件，87 行）。文首注明：T8 子代理因提供方错误未完成，由主会话据 4E 研究结论落盘；本文只设计不实现。其 §1 明确：批次 16 的 PRD-05 用 schema 68（无 schema VOID），**schema 69 留给批次 17 的 WG-08**；§7 写明与批次 16/17 的边界。

### 2.10 I1 集成与记录时点的工作树观察

**文件。** `app/.../SmartMistakeBookRoot.kt`（`SmartMistakeBookApplication.kt` 记录时点未改）。

**I1 自报交付。**
- Root 接通 T6 原生卷面编辑：`SmartMistakeBookRoot.kt` 新增 `mistake/paper-edit/{entryIds}` 编解码与 composable、导出成果/错题详情入口、单题/批量导出来源登记。
- Root 接通 T7“改正答案键”写入口与逐状态回执（`:872-950`）。
- T4 经核对无需 Root 侧装配。
- I1 自报 blocker：T1 的唯一调用点 `SmartMistakeBookDestinations.kt` 不在其所有权，故 T1 未接线；T6 新文件当时未跟踪。

**记录时点树内观察（与上述 blocker 不一致，来源未核实，不得算作 I1 交付）。**
- `app/.../SmartMistakeBookDestinations.kt:307-330`（`ReviewSessionScreen`）与 `:374-387`（`CapturedReviewSessionScreen`）已传 `onSubmitSelfReport`/`onUndoReview`/`storedAnswer`/`reviewSessionId`/`reviewStateVersion`/`onSessionRewound`/`onSkipQuestion`；`storedAnswer` 由 `produceState` 读（`:161-170`）；`stageReviewRewound` 只更新版本/序号/队列、不换题（`:206-214`）。
- `presentationId` 已不含 `sessionVersion`（`:306`，`presentation:review:<sessionId>:<practiceUnitId>`），与 T1b KDoc 一致。
- `SelfReportedReviewInstrumentedTest.kt` 已存在（1 个 `@Test`，走 `repository.submitReviewSelfReport`）。
- 这些文件不在任何轨的自报文件清单内，无法归因；未编译、未测试，故只能记为“树内已出现、验证状态 UNVERIFIED”。

### 2.11 D1 设备轨

**阻塞。** 强门未绿、单测门工单 `exit=1`，设备轨未执行。计划 D1 的全部项目（PRD-05 三键/undo/无模型、PRD-06 广告一致、6B-3 fake 命令、6B-2 编辑→导出、KD-47 文案、KD-62①②、WG-04 parser/tool-loop、批次 15 回归抽验）均 **UNVERIFIED**。记录时点 `emulator-5554` 在线，但本批没有 `assembleDebugAndroidTest`/`adb install -r`/`am instrument` 产物（`find . -path "*androidTest-results*" -name "*.xml" -newermt "2026-10-21 00:00"` → 0 条）。

## 3. 门禁

### 3.1 工单结论（本记录采用）

- **单测门第 3 轮 `exit=1`**：`:core:data:compileDebugUnitTestKotlin` 失败，导致 11 模块单测门不绿。
- 强门（六个 androidTest 编译 + `:app:assembleLocalFirstDebug`）**未执行/未通过**。
- 本记录员**未运行 Gradle**（铁律第 2 条），因此不能给出任何新的门禁结论。

### 3.2 第 3 轮失败点与树内修复（独立核对转述）

独立核对给出的 5 条编译错误（分布在 3 个文件）与记录时点树内状态：

| 位置 | 第 3 轮错误 | 记录时点树内状态 |
| --- | --- | --- |
| `core/data/src/test/.../OpenAiSseToolCallStreamTest.kt:172/205` | `SseDeliveringCall` 缺 `tag(Class<T>, computeIfAbsent)` / 旧 tag 覆写不存在 | `:202-205` 已补两个 `computeIfAbsent` 重载 |
| `core/data/src/test/.../TutorFigureMethodDualParseTest.kt:247/250` | `Map<String,JsonElement>` 不是 `JsonObject` | `:244` 已用 `JsonObject(...)` 包装 |
| `core/data/src/test/.../KnowledgeEvidenceLedgerStatusTest.kt:78` | `Unresolved reference 'assertTrue'` | `:10` 已补 import |

这些修复**只在源码层**；记录时点没有修复后的整轮门禁记录，故门禁不能算绿。

### 3.3 记录时点只读观察（旁证，不代替门禁）

记录员只读聚合 `build/test-results`（未执行 Gradle）：

- 2026-10-21 的 `TEST-*.xml` 共 **402** 个；`failures`/`errors` 聚合均为 **0**（脚本按 mtime 过滤 + ElementTree 解析）。
- 批次 16 新增/修改测试的 XML 明细（类名、tests/failures/errors、mtime）：
  - `ReviewSelfReportContractTest` 7/0/0（core:data 05:07）、`ReviewUndoReplayTest` 6/0/0（core:domain 02:19）；`ReviewSelfReportViewModelTest` 9/0/0、`ReviewSelfReportUiContractTest` 7/0/0（feature:review 04:42）。
  - `OpenAiSseToolCallAggregationTest` 24/0/0、`OpenAiSseToolCallStreamTest` 4/0/0、`ModelStreamSelectionTest` 4/0/0（core:data 05:07）。
  - `TutorFigureMethodDualParseTest` 7/0/0、`FigureToolPromptInjectionTest` 6/0/0、`TutorFigureProposalExecutionTest` 4/0/0、`TutorLocalActionAdvertisementParityTest` 6/0/0（core:data 05:07）。
  - `TutorCapabilitySnapshotTest` 4/0/0、`TutorLocalActionAdmissionTest` 13/0/0、`TutorLocalActionExecutionTest` 9/0/0、`TutorToolGateTest` 4/0/0（core:model/core:domain 02:19）。
  - `PaperEditCommandParserTest` 10/0/0、`TutorPaperEditApplierTest` 13/0/0、`TutorExportModelEditTest` 5/0/0、`TutorPendingRequestCommandsTest` 16/0/0、`NativePaperEditEntryTest` 5/0/0（core:model/feature:tutor/feature:library 02:19）。
  - `KnowledgeEvidenceLedgerStatusTest` 5/0/0、`KnowledgeQuizFeedbackWriteTest` 6/0/0、`LatestCorrectedKeyOrderTest` 4/0/0、`BackupTempArtifactCleanupTest` 2/0/0、`RoomBackupCheckpointValidationTest` 5/0/0、`StorageRestoreFeedbackTest` 4/0/0（05:07/02:19/05:05）。
- **限制**：这些 XML 只证明“这些类在某次运行中通过”，不能证明计划 §4 的 11 模块单测门整轮 `exit=0`（Gradle 可能只跑了变更模块/定向类，且 UP-TO-DATE 会保留旧 XML）；与工单第 3 轮 `exit=1` 时点不同。故 §3.1 结论不变。

## 4. 设备

- 记录时点 `adb devices` → `emulator-5554	device`（本记录实际执行）。
- **未执行**任何 `assembleDebugAndroidTest`、`adb install -r`、`am instrument`；2026-10-21 无 androidTest 结果 XML（`find` 计数 0）。
- 因此设备全链、真实接收应用打开、PRD-05 真库自评/undo、6B-2 编辑→导出、KD-47 文案可见性、KD-62 清理/checkpoint 真实场景，全部 **UNVERIFIED**。
- D1 自报状态：**阻塞**（强门未绿）。

## 5. 外部研究

### 5.1 R1：事件溯源 void/replacement、FSRS/Anki 三键、自评防伪

- 事件溯源没有“原地删除事件”的正当路径；撤销已提交 attempt 应追加补偿事件。VOID（从有效证据集合排除）与 REPLACE（按新载荷在原位置/时间重算）是两种补偿语义。Microsoft Event Sourcing/Compensating Transaction 页面与仓库 `LearningState.kt` 的 `EvidenceCorrection` KDoc 同向。
- 本仓 `AttemptCorrection` 原本是 **replacement-only**（无 `kind`，`replacement_*` 全 NOT NULL）；零权重会被 `projectMemory` 解释为 `ANSWER_REVEALED`/AGAIN 并 lapse。**因此本批没有把 VOID 写成普通零权重，而是用显式前缀哨兵 + 构造期约束**，投影器在 `projectMemory` 之前分支（`LearningProjector.kt:586`）；这与 R1 的“显式增加事件语义”建议一致，但采用无 schema 方案。
- Anki 官方是四键 Again/Hard/Good/Easy；官方只给二键回退，没有官方三键模式。FSRS 评分 1=Again、2=Hard、3=Good、4=Easy；“卡顿”必须是成功回忆（Hard），失败必须 Again。本批三键“对/卡顿/错”符合该映射，且自评正性档封顶 Good（不发放 Easy 奖励）。
- 自评防伪：`SELF_REPORTED_RECALL/STUCK` 独立 reason，`isIndependent` 只由 reason 决定，自评永不是 `INDEPENDENT_CORRECT`；`review_log.rating` 与 `evidence_weight` 分列；UI 明写“证据口径：自评”，客观选择只作本机反馈。
- 证据等级：Dunlosky & Rawson 2012 标题/期刊/DOI 经 Crossref 核实；“86% 自评过自信”为仓库转述、未读全文，标【单一来源待验证】。Anki undo 源码/测试断言为读码级，未运行 Anki 测试。

### 5.2 R2：OpenAI-compatible SSE tool_calls 聚合与终态

- `delta.tool_calls` 每项只有 `index` 必填；id/type/name/arguments 可选。必须按 `index` 合并，不能按到达顺序 append；id/name 应“取首个非空、后续相同忽略、冲突 fail-closed”。arguments 是 JSON 文本分片，任何中间时刻都不保证合法 JSON；唯一 wire-level 完成信号是终帧 `finish_reason=tool_calls`，且仍须 schema 校验。
- fail-closed：拒绝 `length`/`content_filter`、缺失 finish_reason 的 EOF、malformed frame、index 重复/越界/冲突、非法 JSON/未知字段/空 arguments（除非显式无参）；任一调用不合法则整轮不执行。
- 对声明工具的回合强制 `stream=false` 合法：`stream` 默认 false，`tools` 是独立字段，非流式响应返回 `message.tool_calls` 且 `finish_reason=tool_calls` 是正式支持模式；代价是失去逐字流式/首 token 时延，但工具能力、token/费用不增加。本批按契约 `visual-generation-contract.md:132` 采用该降级。
- 512KiB/64KiB **没有外部标准依据**：OpenAI OpenAPI/WHATWG SSE/RFC 8259 均不定义具体字节上限；本仓可追溯来源是 `docs/current/visual-generation-contract.md:84` 的产品预算（“单份工具 payload 解码后 JSON≤512KiB、sourceJs UTF-8≤64KiB、待设备性能验收”）。实现按此作用域使用，未套到整条 SSE/reasoning 流。
- 官方 Node SDK 的 128 tool calls、16MiB partial JSON、65,536 片段等是工程锚点；本仓只用 128 作调用数上限，不冒充外部标准。
- 未验证：platform.openai.com 正文直连 403，改用 OpenAPI 仓库与 Python/Node SDK 源码（一手官方制品）+ Microsoft Learn 旁证；真实 Provider 偏差未实测。

## 6. 复核发现与处置

> 独立核对与对抗复核发生在门禁第 3 轮前后；记录员在写记录前对每条做了当前源码复核。凡“树内已修”只表示记录时点源码存在对应修复，**不等于测试通过**。

| # | 发现（来源） | 记录时点状态 | 证据 |
| --- | --- | --- | --- |
| 1 | 单测门第 3 轮 `exit=1`，`core:data` 测试编译失败（独立核对，高） | **未关闭**；树内已补 import/重载/JsonObject，但无整轮重跑 | §3.2/§3.3 |
| 2 | PRD-05 三键/undo 在 app 唯一调用点未传参（独立核对 + 对抗复核，高） | **树内已出现接线**（非任何轨自报）；未编译/未测试 | `SmartMistakeBookDestinations.kt:307-330/374-387` |
| 3 | 自评后展示已存答案未写 `AnswerRevealOutcome`（对抗复核，高） | **树内已修**：记录后对可信答案写 reveal，失败有可见提示 | `ReviewSessionScreen.kt:470-486/619` |
| 4 | key-fix 幂等身份只看 itemId+选项，跨会话吞掉合法作答（对抗复核，高） | **树内已修**：观察行查找加 `conversationId`/本会话过滤 | `KnowledgeQuizFeedbackWriter.kt:145/158/490-495` |
| 5 | 改正键按常量 itemId 跨会话套到新题面（对抗复核，高） | **树内已修**：`latestCorrectedKey` 加 `sessionId`，且只在本会话选项集内应用 | `KnowledgeQuizItemStore.kt:41-70` |
| 6 | 改正答案键 CAS 生产传 `null`（独立核对 + 对抗复核，中） | **树内已修**：对话框按 item.id 现读版本，Root 提交前兜底取版本 | `KnowledgeReviewSessionScreen.kt:479-560`；`SmartMistakeBookRoot.kt:889-925` |
| 7 | `presentationId` 含 `sessionVersion`，undo 后换 presentation（对抗复核，中） | **树内已修**：identity 只由 `(sessionId, practiceUnitId)` 决定 | `SmartMistakeBookDestinations.kt:303-306` |
| 8 | 非工具文本流无 finish_reason 时把截断内容当完整答复（对抗复核，中） | **树内已修**：[DONE] 或 finish_reason 至少一个 | `OpenAiSse.kt:161-164/221-229` |
| 9 | `KnowledgeQuizItemStore` 未传 `ledgerOrder`，读侧时间序降级（独立核对 + 对抗复核，中） | **树内已修** | `KnowledgeQuizItemStore.kt:47-56` |
| 10 | PRD-05 无真库自评 instrumented 覆盖（独立核对，中） | **树内已补** `SelfReportedReviewInstrumentedTest.kt`（1 例），**未运行** | 同文件 `:42-131` |
| 11 | T5 `paperEditAssistant` 未注入（轨道自报 + 记录员 grep） | **仍开放** | `grep -rn paperEditAssistant app/src/main/kotlin` 无命中 |
| 12 | `visualContext` 只进请求编码/指纹，未进提示词/schema（独立核对，低） | **仍开放** | `grep -rn visualContext core/data/.../model/` 无命中 |
| 13 | 预算观察器对普通文本流也逐帧严格解析，坏 frame 会中止文本答复（对抗复核，低） | **仍开放** | `OpenAiModelTransport.kt:299-307`；`OpenAiSse.kt:313-325` |
| 14 | 旧 SavedState 缺键名时 `SELF_REPORTED_RECALL` 回退成 GOOD，Hard 旧回执显示为“对”（对抗复核，低） | **仍开放** | `ReviewSelfReportControls.kt:542-547` |
| 15 | 能力快照之外仍有多份人工工具/动作名单（对抗复核，低） | **仍开放（设计边界）** | `TutorToolGate.kt`、`TutorPermissionPolicy.kt`、`TutorModelTaskPolicy.kt` |
| 16 | 所有新测试、11 模块强门、设备轨未执行（对抗复核，高） | **未关闭** | §3.1/§4 |

**处置结论。** 高优先的“未接线、未写 reveal、跨会话键污染、CAS 关闭”在记录时点的树内已有对应源码修复；但这批修复没有经过本批门禁/设备验证，且部分不在任何轨的自报清单内。因此本批不能宣称验收完成，必须由下一轮门禁与设备段复核。

## 7. 遗留与下一批

1. **门禁 P0**：重跑 11 模块单测门并取得 `exit=0`；跑强门（六个 androidTest 编译 + `:app:assembleLocalFirstDebug`）。当前工单仍是第 3 轮 `exit=1`。
2. **设备 P0**：D1 全部项目未执行；`emulator-5554` 在线时优先跑 PRD-05 隔离库自评/undo/重放、6B-2 编辑→导出、KD-47/62 与批次 15 回归；真实 Provider 仍无凭据。
3. **PRD-05**：真实数据启用仍挂 3D 全门；`TutorJudgedReviewSettler.expectedStateVersion` 与撤销后版本解耦的问题留给 I1/T5 收口；`review_log` 中“答题前已 reveal 的自评”规范化行为未改（可能进入拟合样本），需后续裁定。
4. **4E WG-04**：真实 Provider 的 SSE/工具调用偏差、CRLF 分帧、文本流预算严格解析、`visualContext` 是否进入提示词，均待下一批；WG-02/06/07/08 未实施，schema 69 留给批次 17 的 WG-08（T8 设计件 §1/§7）。
5. **6B-3**：`paperEditAssistant` 尚未在 app 注入；真实模型质量 UNVERIFIED。
6. **KD 状态**：KD-43 保持 open（代码级进展，但门禁/设备未跑；关闭条件仍是两半各有用例执行证据）；KD-47 按源码文案改动关闭（测试已写，记录员未重跑）；KD-62 ①+② 代码级关闭、③ 归 KD-61；KD-61 未动。
7. **PRD 勾选**：`docs/superpowers/plans/2026-10-19-product-readiness-followup-plan.md` 仍 50 个未勾 / 0 已勾，本批不预勾。
8. **未归属修复**：§2.10 的 app 接线、reveal、跨会话键、CAS 修复未出现在任何轨的自报清单，提交时需主会话确认来源与归属，避免冒领或漏提。

## 8. 提交 / 推送

- **基线提交**：`a200f27b`（`fix(baseline): 纳入能力表单 VM 与数据库重启页`）已在本地 HEAD，含 2 文件 +74；`origin/main` 仍 `1d8bb937`，本地领先 4 笔（`a200f27b`、`95b00272`、`8a100b59`、`8eb69460`），**尚未推送**（基线 §1；网络首次连接失败、重试 `ls-remote` 成功，实际 push 未执行）。
- **批次 16**：本批代码/文档**未提交**（记录员未运行 git 写命令）。提交时需按计划 §5 分笔、显式文件列表、分块 add；共享脏文件（`OpenAiModelProtocol.kt`、`RoomModelTaskRepository.kt`、`TutorSessionPanel.kt` 如必须、`MistakeDetailRoute.kt`、`SecondaryScreens.kt`、`docs/known-defects.md`、主线、台账、决策台账）整文件纳入并保留他线 hunk。
- **未跟踪/新增文件**：T1 的 `ReviewSelfReportControls.kt`、`SelfReportedReviewInstrumentedTest.kt`、`ReviewUndoReplayTest.kt`、`ReviewUndoTransactionInstrumentedTest.kt`，T5 的 `PaperEditCommand.kt`/`TutorPaperEdit*`，T6 的 `MistakePaperEditRoute.kt` 与两个测试，T8 设计件等，必须显式纳入；不得提交他线未跟踪的 `BatchImport*`、`docs/current/*`、`tools/kb_*`、`knowledge-production/*`。
- **推送**：按 2026-10-19 长期授权由主会话收口自动 push；非快进先 merge，不 rebase；网络失败记录并重试。**本记录不宣称已推送/已提交。**
- **安全**：Mimosa 扫描无完整结论，本批不宣称任何安全结论；未输出/落盘任何密钥或凭据。

## 9. 主会话收尾实跑（2026-10-21，覆盖 §3/§4 的旧门禁结论）

> 工作流记录按工单旧结论写成“单测门第 3 轮 exit=1、强门/设备未跑”。主会话在同一工作树重跑并补修后，结果如下；以本节为准。

### 9.1 门禁

- **单测门（11 模块，`--continue`）exit=0**：core:model 494/0、core:domain 607/0、core:data 974/0、core:database 203/0、core:export 132/0、core:ui 41/0、feature:review 72/0、feature:library 52/0、feature:capture 148/0、feature:tutor 246/0、app 60/0；合计 **3,029 tests / 0 failures**。
- **强门 exit=0**：六模块 androidTest 编译 + `:app:assembleLocalFirstDebug`。修掉的测试侧缺口：
  1. `ReviewUndoTransactionInstrumentedTest` 的 `attempt.attempt.attemptId` 少一层解引用；
  2. `LearningLedgerChunkReadTest` 的 fake `LedgerOnlyProjectionTransactionDao` 未实现新增的 `voidedPresentationIds`；
  3. `ReviewUndoTransactionInstrumentedTest` 用 `activeSession` 读已完成会话，改为 `latestSession`（测试夹具）。
- 本批无新增 DB schema（schema 68）；请求 schema 18 由 T3 落树。

### 9.2 设备（`assembleDebugAndroidTest` + `adb install -r` + `am instrument`；未用 connectedAndroidTest）

| 用例 | 结果 | 关键证据 |
| --- | --- | --- |
| `SelfReportedReviewInstrumentedTest` | 1/1 | 三键自评写 Room、推进、VOID undo、undo→重评；首跑触发 `ProjectionCasConflictException`，修复见 §9.3 |
| core:data 四类（`SelfReported` + `StudySubmissionWithoutFixture` + `AnswerKeyCorrection` + `KnowledgeQuizCooldownChannelScope`） | 24/0 | 自评/无夹具/答案键纠正与撤销/身份粘性自动补偿与重放 |
| `ReviewUndoTransactionInstrumentedTest` | 4/4 | VOID 回退会话、review_log 失效、幂等、只撤最后推进、全重放排除 |
| `RoomModelTaskToolLoopInstrumentedTest#aLobbyRoundSecondDispatchDeclaresEveryReadyTool` | 1/1 | 无图像能力 provider 下第二轮声明 7 枚（去 GENERATE_FIGURE）；首跑只有 1 次派发，修复见 §9.3 |
| `NativePaperEditRouteInstrumentedTest` | 8/8 | 原生编辑入口、未编辑快照一致、改标题换指纹、空白 fail-closed、导出成果/详情入口 |
| `BackupRestoreInstrumentedTest`（批次 15 回归） | 27/0 | 备份删除/恢复、旧 schema 迁移、设置往返、AUD-05 缺资产拒恢复 |

设备合计 **64 tests / 0 failures**。

### 9.3 收尾修复（生产 2 处、测试 3 处）

1. **VOID 呈现态权威**（生产）：`ProjectionTransactionDao.verifyCommittedPresentationStates` 在 FULL_REPLAY 时，对“确有 VOID 更正”的呈现 id 只放开 `memoryProjectionApplied` 一位，其余字段仍逐位必对；新增 `voidedPresentationIds` 查询（`attempt_correction` join `attempt_event`）。撤销前持久态是 true、全量重放后合法变为 false；不放开会抛 `ProjectionCasConflictException`。`SelfReportedReviewInstrumentedTest` 由红转绿。
2. **首轮工具声明默认值**（生产）：`RoomModelTaskRepository.toolDeclarationsFor` 对空 `toolDeclarations` 先补 `TUTOR_TOOL_DECLARATIONS` 再按就绪位裁剪；空列表被当成“没有任何工具”会让模型申请的工具被工具环当未声明丢弃（设备用例只有 1 次派发）。修复后能力快照用例 1/1。
3. 测试侧：上述 `attempt` 解引用、`latestSession`、fake DAO `voidedPresentationIds` 三处；均在设备/单测门复跑通过。

### 9.4 状态更新

- **KD-43 关闭**：单元 3,029/0 + 设备 24/0（含身份粘性自动补偿、UNKNOWN 键 REPLACE、跨会话键隔离、纠错/撤销/重放）。
- **KD-47 关闭**：`StorageRestoreFeedbackTest` 4/0（单元门）。
- **KD-62 ①+② 关闭**：`RoomBackupCheckpointValidationTest` 5/0、`BackupTempArtifactCleanupTest` 2/0；③ 归 KD-61。
- **KD-61** 保持 open（关库前 750ms 启发式/非门控写入口）。
- **PRD-05**：数据层设备 24/0、UI 合同 JVM 通过、app 接线已编译；无设备 UI 点击，PRD 勾选仍不预勾。
- **PRD-06**：能力快照广告-执行一致设备 1/1；空态半边上批已过，能力半边界落地。
- **6B-2**：设备 8/8；Root 路由与详情/导出入口已接。
- **6B-3**：代码 + JVM，`paperEditAssistant` 未在 app 注入（无真实 Provider），真实模型质量 UNVERIFIED。
- **4E WG-04**：SSE/方法/schema 18 代码 + JVM；真实 Provider 未验。
- **T8 设计件**由主会话代写，仅设计交付。

### 9.5 提交与推送

- **代码/测试提交 `f3ea18b8`（104 文件）**；文档提交待本文件；推送结果待网络恢复后补记。
- 代码/文档将按显式文件清单分笔提交；`SmartMistakeBookRoot.kt`、`SmartMistakeBookDestinations.kt`、`SecondaryScreens.kt`、`OpenAiModelProtocol.kt`、`RoomModelTaskRepository.kt`、`MistakeDetailRoute.kt`、`docs/known-defects.md`、主线、台账、决策台账整文件纳入并保留他线 hunk。
- 推送仍受 GitHub 连接影响：基线 4 笔 backlog + 本批提交需在收口时重试；非快进先 merge，不 rebase。
