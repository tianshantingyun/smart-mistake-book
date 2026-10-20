# 批次 16 基线（PRD-05 / 4E WG-04 / PRD-06 / 6B-2/3 / 小修）

> 登记时点：2026-10-20（工作树当前状态；计划批次名为 2026-10-21）。
> 只读登记：本文件是 T0 的唯一产出。未运行 Gradle、未安装/启动 App、未改动任何代码、未 push。
> 所有命令均在 `D:\smart mistake book` 执行；结论只覆盖本文件写下的命令输出与源码行，不代表设备或真实质量问题已通过。

## 1. 版本控制基线

| 项 | 值 | 来源 |
| --- | --- | --- |
| HEAD | `a200f27bf6feb19137d485329967b126952f0950`（短号 `a200f27b`） | `git rev-parse HEAD` |
| origin/main | `1d8bb937ebca78b376a155f2b55a074f7ff3a180`（短号 `1d8bb937`） | `git rev-parse origin/main` |
| 领先 / 落后 | `0	4`（领先 4，落后 0） | `git rev-list --left-right --count origin/main...HEAD` |
| 分支 | `main...origin/main`，`[ahead 4]` | `git status -sb` |
| 工作树 | 473 行 = 74 modified（` M`）+ 399 untracked（`??`）（本文件写入前的计数） | `git status --porcelain` 逐行计数 |

本地领先的 4 笔（`git log --format='%h %H %ci %s' origin/main..HEAD`）：

| 提交 | 时间 | 说明 |
| --- | --- | --- |
| `a200f27b` | 2026-10-20 20:51:10 +0800 | `fix(baseline): 纳入能力表单 VM 与数据库重启页（HEAD 编译所需，含他线稳定文件）` |
| `95b00272` | 2026-10-20 04:55:01 +0800 | `fix(kb): 批次13 晋升后收口——v16→v17 重晋升…`（KB 线第二笔） |
| `8a100b59` | 2026-10-20 04:28:16 +0800 | `feat(kb): 批次13——SKIP 全量重开判定并入…`（KB 线第一笔） |
| `8eb69460` | 2026-10-20 03:16:18 +0800 | `docs(research): 修正批次15 代码提交统计（21976 insertions）` |

补充事实：批次 15 的代码提交 `032fce0e`（`feat(batch-15): PRD A/B 批、KD-56 大库恢复与 4E WG-05`）已是 `origin/main` 的祖先（`git log --oneline -8 1d8bb937` 可见 `032fce0e`），不在本地 backlog 内。

### 1.1 推送可达性

同一轮内做了两次只读 `ls-remote`，结果不同：

1. `GIT_TERMINAL_PROMPT=0 timeout 25 git ls-remote --heads origin` → `fatal: unable to access 'https://github.com/tianshantingyun/smart-mistake-book.git/': Failed to connect to github.com:443 after 21065 ms: Could not connect to server`（约 21 秒后失败）。
2. 重试同命令 → 成功返回远端分支列表，其中 `1d8bb937ebca78b376a155f2b55a074f7ff3a180	refs/heads/main`，`exit=0`。

登记结论：远端当前**可读**，但连接不稳定（首次超时/拒绝）；远端 `main` 仍是 `1d8bb937`，4 笔本地提交尚未推送。**实际 push 未执行**（铁律禁止），推送权限与网络稳定性均未验证。

## 2. HEAD 独立编译状态

批次 15 基线登记的静态阻塞是：HEAD 树缺少 `CapabilityFormViewModel.kt` 与 `DatabaseRestartRequiredScreen.kt` 两个声明，而已提交代码引用它们。本批 HEAD 已由 `a200f27b` 修复该静态缺口：

- `git show --stat --oneline a200f27b` → 只改 2 个文件、74 insertions：
  - `app/src/main/kotlin/com/tingyun/smartmistakebook/CapabilityFormViewModel.kt`（+39）
  - `app/src/main/kotlin/com/tingyun/smartmistakebook/DatabaseRestartRequiredScreen.kt`（+35）
- `git ls-tree -r HEAD --name-only | grep -E "CapabilityFormViewModel|DatabaseRestartRequiredScreen"` → 两条路径都在 HEAD 树中（exit 0）。
- `git grep -n "class CapabilityFormViewModel\|fun DatabaseRestartRequiredScreen" HEAD -- 'app/*'` →
  - `HEAD:app/src/main/kotlin/com/tingyun/smartmistakebook/CapabilityFormViewModel.kt:14: internal class CapabilityFormViewModel : ViewModel()`
  - `HEAD:app/src/main/kotlin/com/tingyun/smartmistakebook/DatabaseRestartRequiredScreen.kt:17: internal fun DatabaseRestartRequiredScreen(message: String, onClose: () -> Unit)`
- 引用点仍在：
  - `HEAD:app/src/main/kotlin/com/tingyun/smartmistakebook/SecondaryScreens.kt:118`（`val form: CapabilityFormViewModel = ...viewModel()`）
  - `HEAD:app/src/main/kotlin/com/tingyun/smartmistakebook/SmartMistakeBookRoot.kt:202`（`DatabaseRestartRequiredScreen(message = message, ...)`）

结论：**“声明缺失”这一静态阻塞已闭合**；但 T0 被铁律第 2 条禁止运行 Gradle，`compileLocalFirstDebugKotlin`/`assembleLocalFirstDebug` 未执行，因此“HEAD 可独立编译”只能记为**静态符号闭合、实际编译 UNVERIFIED**，不得写成编译通过。另：`SmartMistakeBookRoot.kt` 与 `SmartMistakeBookApplication.kt` 由集成轨独占，本批其他轨不得引用这两个文件里的新符号。

## 3. Schema 与依赖

### 3.1 Room schema

- `STUDY_DATABASE_VERSION = 68` — `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabase.kt:130`；同文件 `:227` 传给数据库构建，`:385` 注册 `ANSWER_KEY_CORRECTION_MIGRATION_67_68`。
- 迁移实现：`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/AnswerKeyCorrectionMigration_67_68.kt:20`。
- 已落盘 schema：`core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/68.json` 存在；同一目录 `69.json` 不存在（`ls` 报 `No such file or directory`）。
- 本批计划：T1 优先用**无 schema 的 VOID 哨兵**做 attempt 级 undo；若证明不可表达才独占 **69**（届时需迁移 + 68/69.json + 矩阵测试）。T8 的设计件按 70 讨论。除 T1 外本批无 DB schema 写者。

### 3.2 模型请求 schema

- `CURRENT_SCHEMA_VERSION = IMAGE_CLASSIFY_SOURCE_SHA_SCHEMA_VERSION`，后者值为 **17** — `core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/ModelTasks.kt:535-536`。
- `MIN_SUPPORTED_SCHEMA_VERSION = 1` — 同文件 `:484`。
- 本批 T3 只把请求 schema bump 到 **18**，不写 DB schema。

### 3.3 关键依赖版本（`gradle/libs.versions.toml`）

| 组件 | 版本 | 行 |
| --- | --- | ---: |
| Android Gradle Plugin | 9.3.0 | 2 |
| Kotlin | 2.3.21 | 3 |
| Compose BOM | 2026.06.00 | 4 |
| androidx.core:core-ktx | 1.18.0 | 5 |
| activity-compose | 1.13.0 | 6 |
| lifecycle | 2.11.0 | 7 |
| navigation | 2.9.8 | 8 |
| datastore | 1.2.1 | 9 |
| kotlinx-coroutines | 1.11.0 | 10 |
| kotlinx-serialization | 1.9.0 | 11 |
| Room3 | 3.0.0 | 12 |
| paging | 3.4.2 | 13 |
| androidx.sqlite | 2.7.0 | 14 |
| sqlite-jdbc（测试） | 3.53.4.0 | 17 |
| KSP | 2.3.10 | 18 |
| JUnit | 4.13.2 | 24 |
| Espresso | 3.7.0 | 27 |
| LiteRT | 2.1.6 | 32 |
| OkHttp | 5.4.0 | 43 |
| WorkManager | 2.10.5 | 44 |

App 模块 SDK：`compileSdk = 37`、`minSdk = 23`、`targetSdk = 36`（`app/build.gradle.kts:9,21,22`）。Gradle 模块共 13 个，包含 `:app`、`:core:{model,domain,database,data,export,ui}`、`:feature:{capture,review,tutor,library,profile}`、`:benchmark`（`settings.gradle.kts:19-32`）。

### 3.4 工作树归属

本批 T0 未产生任何代码改动；登记时点（本文件写入前）的 473 行均非 T0 产出，主体是计划 §0 列出的他线在飞热区与历史审计裁图（`git status --porcelain` 抽样）。本批不触碰、不代提交任何一条。抽样可见：`app/` 的 `LearningMasteryScreen.kt`、`ReviewReminderCoordinator.kt`，`core/data` 的 `OpenAiModelProtocol.kt`、`RoomModelTaskRepository.kt`、`RoomCaptureWorkflowRepository.kt`，`core/database` 的 `RoomBatchImportStore.kt`、`BatchImportDao.kt`，以及大量 `.agent_audit_crops*` 未跟踪图片。其中计划点名的共享脏文件当前差异为：`OpenAiModelProtocol.kt` +1 行、`RoomModelTaskRepository.kt` 约 +26/−14（两文件合计 `27 insertions(+), 14 deletions(-)`，`git diff --stat`），由 T3（其后 T4/T5）严格串行整文件纳入并保留他线 hunk。

## 4. 四栏登记（本批基线时点）

四栏口径来自 PRD-01：`docs/superpowers/plans/2026-10-19-product-readiness-followup-plan.md:39`（“代码接线/模块检查/设备全链/真实质量”分别登记）。

| 栏 | 已具备（证据） | 缺口 / 未验证 |
| --- | --- | --- |
| **代码接线** | 13 个模块已 include；HEAD 树含 `CapabilityFormViewModel.kt` 与 `DatabaseRestartRequiredScreen.kt` 及其引用，批次 15 的静态声明缺口已闭合；批次 15 代码 `032fce0e` 已在 `origin/main` 历史；PRD-05 所需模型枚举 `SELF_REPORTED_RECALL`/`SELF_REPORTED_STUCK` 已存在（`core/model/.../LearningState.kt:15,21`），`AttemptCorrection`（`:662`）与 `EvidenceCorrectionKind.VOID/REPLACE`（`:551-552`）已存在；复习页文件已存在（`feature/review/.../ReviewRoute.kt`、`ReviewSessionScreen.kt`、`CapturedReviewSessionScreen.kt`）。 | 批次 16 的 T1~T8/I1 **尚未实现**；`feature/review` 内 `git grep "SELF_REPORTED_RECALL\|selfReported\|SelfReport"` **无命中**，即三键自评 UI 尚无接线；WG-04a/b、PRD-06 快照、6B-2 原生入口、6B-3 命令解析均未接线（见 §5.3）；未运行整包编译，接线是否可编译 UNVERIFIED。 |
| **模块检查** | 门禁命令集已在计划 §4 固定：11 个模块单测 + 6 个 androidTest 编译 + `:app:assembleLocalFirstDebug`；Room `68.json` 已落盘、迁移已注册。 | **T0 未运行任何 Gradle**（铁律第 2 条），本基线没有任何当前模块绿灯。上一批的官方结论仍为“单测门第 3 轮 `exit=1`、强门未通过、D1 阻塞”（`docs/research/2026-10-20-batch-15-prd-a-b-backup-wg05-record.md:243-245`），那是批次 15 时点，不能当作本批结果。已知红点名照录：`GoldenRetrievalInstrumentedTest`/`GoldenRetrievalJvmTest`、SECTION facet 边际门、`Stage1LexicalLabTest`/`Stage2LexicalScoresExportTest`（同记录 §3.3，`:269` 起）。 |
| **设备全链** | 设备协议已固定：`assembleDebugAndroidTest` + `adb install -r` + `am instrument`；禁止 `:app:connectedLocalFirstDebugAndroidTest`（计划 §4、D1）。历史设备证据只属于历史包。 | 本轮执行 `C:/Android/Sdk/platform-tools/adb.exe devices` → `List of devices attached`（空列表），当前无设备在线；本批未安装、未启动、未跑任何 instrumented/UI 用例，设备全链 **UNVERIFIED**。批次 15 的 D1 也是阻塞（同记录 `:276` 起）。 |
| **真实质量** | 无。 | 模型凭据未播种（计划 §6 最后一行），真实 Provider 的成功率、时延、成本、数学正确性整块 **UNVERIFIED**；PRD-09 质量门明确不在本批（计划 §1 `:18`）。模块绿、schema 通过、设备点击都不能替代真实质量结论。 |

## 5. 本批范围与入口

### 5.1 做（计划 §1 `:16`）

- PRD-05：本地三键（Good/Hard/Again，无 Easy）/ attempt 级 undo / 无模型复习闭环。
- 4E WG-04a：SSE 工具调用终态门（按 `index/id` 聚合 `delta.tool_calls`，完整结束后才解析，payload/解码上限）。
- 4E WG-04b：`method/program/imageOperation/imagePrompt` + 提议结构 + 请求 schema 18。
- PRD-06：单一能力快照；未接线动作（尤其 `ADD_TO_REVIEW_PLAN`）不广告。
- 6B-3：有界自然语言改卷（模型只输出白名单编辑命令）。
- 6B-2：原生“编辑卷面”入口。
- 小修：KD-43(b)、KD-47、KD-62。
- T8：WG-06/07/08 的 schema 69 设计件（只写文档）。
- 基线/收口。

### 5.2 明确不做（计划 §1 `:18`）

WG-06/07/08 实施（设计件 only）、WG-02/03/09/10、JS/QuickJS、PRD-09 质量门、PRD-10 的 5b/5c/5B/6C-2、PRD-11、UI-00~11、**PRD-05 真实数据启用**（等 3D 全门）、KD-61 完整关闭。

### 5.3 分轨入口与当前缺口（HEAD 源码证据）

| 轨 | 入口文件（HEAD 已存在） | 当前缺口（本轨要消灭的失败） |
| --- | --- | --- |
| **T1 PRD-05 三键/undo/无模型** | `feature/review/.../ReviewRoute.kt`、`ReviewSessionScreen.kt`、`CapturedReviewSessionScreen.kt`；`core/data/.../study/StudySubmissionPreparer.kt`、`ReviewLogSink.kt`、`RoomBackedStudyExperienceRepository.kt`、`StudyWriteContext.kt`；`core/model/.../LearningState.kt`（`SELF_REPORTED_RECALL:15`、`SELF_REPORTED_STUCK:21`、`VOID/REPLACE:551-552`、`AttemptCorrection:662`）；`core/database/.../AttemptTransactionDao.kt` | 复习 UI 无三键自评/undo 承接（`git grep` 无命中）；`ReviewLogSink` 当前只写“证据映射后实际采用的 rating”（`ReviewLogSink.kt:83-90`），尚无“原始档 / 折扣分列”落点；undo 需选择无 schema VOID 哨兵或占 69；无模型路径（已保存但无机器判分工件）未打通。 |
| **T2 WG-04a SSE 终态门** | `core/data/.../model/OpenAiSse.kt`、`OpenAiModelTransport.kt`、`OpenAiChatCompletionsProtocol.kt`、`OpenAiCompatibleModelGateway.kt` | `OpenAiSse.reconstructedChatCompletion` 只拼接 `delta.content`（`OpenAiSse.kt:127-164`），不聚合 `delta.tool_calls`，重建体只有 `message.content`；`deltaHasToolCalls` 只返回布尔（`:78-85`）；transport 对工具增量只回调 `onToolCallDelta` 布尔（`OpenAiModelTransport.kt:299-301`）；在 `core/data/src/main/kotlin` 内 grep `512*1024|524288|64*1024|65536` **无命中**，没有 512KiB/64KiB 上限；gateway 的流选择只看 `supportsStreaming && usesOpenAiSse()`（`OpenAiCompatibleModelGateway.kt:160-161`），没有“声明工具的回合强制 stream=false”。 |
| **T3 WG-04b method/proposal + schema 18** | `core/model/.../TutorToolLoop.kt`（`TutorToolCall:114`）、`TutorTasks.kt`、`TutorToolTrace.kt`、`ModelTasks.kt`；`core/data/.../model/OpenAiModelTaskAdapters.kt`、`OpenAiModelResponseParsers.kt`、`OpenAiModelProtocol.kt`、`RoomModelTaskRepository.kt`、`RoomTutorToolRunner.kt` | `TutorToolCall` 当前字段只有 `tool/rationale/terms/direction/understanding/difficultyTier` 等，未见 `method/program/imageOperation/imagePrompt`（`TutorToolLoop.kt:114-131`）；请求 schema 仍为 17（`ModelTasks.kt:535-536`）；`FigureGenerationProposal` 尚未复用到工具提议；JS 必须继续 UNAVAILABLE。 |
| **T4 PRD-06 能力快照** | `core/model/.../Capabilities.kt`（`AppCapabilitySnapshot:9-27`）、`TutorLocalAction.kt`、`TutorToolDescriptions.kt`；`core/domain/.../TutorLocalActionExecution.kt`、`TutorLocalActionAdmission.kt`；`feature/tutor/.../TutorPendingRequestCommands.kt`、`TutorPendingRequestCard.kt` | 能力快照尚未与 native schema / Route B / admission / 执行端同源；`ADD_TO_REVIEW_PLAN` 仍在 `TutorLocalAction.entries`（`TutorLocalAction.kt:57`）并被原生 schema 全量广告（`OpenAiModelProtocol.kt:293` 遍历 `entries`），但执行端映射 `NOT_WIRED_YET`（`TutorLocalActionExecution.kt:30,115`）——即“广告了没人接的东西”；`GENERATE_FIGURE` 尚无“图像后端就绪才广告”的统一判定。 |
| **T5 6B-3 有界自然语言改卷** | `feature/tutor/.../TutorExportSheet.kt`、`TutorConversationViewModel.kt`；`core/export/.../MistakePdfPaperEdit.kt`、`MistakePaperDraftStore.kt` | 现有 `TutorExportEditSheet.kt:49` 是结构化手动编辑 sheet（由 `TutorExportSheet.kt:290` 打开），不是模型白名单命令解析；无“增/删/调序/改文字/卷面备注”的命令模型与非法/越界拒绝路径。 |
| **T6 6B-2 原生编辑入口** | `feature/library/.../MistakeExportHubRoute.kt`；`core/export/.../MistakePdfPaperEdit.kt`；`feature/tutor/.../TutorExportEditSheet.kt` | `MistakeExportHubRoute.kt` 内 grep `编辑卷面|onEdit|paperEdit` **无命中**，原生导出成果页没有“编辑卷面”入口；编辑必须只改文档副本、不改原题。 |
| **T7 小修** | `core/data/.../study/KnowledgeQuizFeedbackWriter.kt`、`KnowledgeEvidenceWriter.kt`；`core/database/.../dao/ChatEvidenceDao.kt`；`feature/review/.../KnowledgeReviewSessionScreen.kt`；`app/.../SecondaryScreens.kt`；`core/data/.../backup/RestoreRecoveryCoordinator.kt`、`RoomBackupSupportStore.kt` | `docs/known-defects.md` 仍标 open：KD-43(b)（`:1933`，身份粘性/UNKNOWN 键 UI 改键未裁定）、KD-47（`:2051`，恢复中途失败文案仍称“保持原样”）、KD-62（`:2421`，checkpoint 结果不校验、成包临时库无启动清理、写门只覆盖两仓库）。 |
| **T8 WG-06/07/08 设计件** | 只写 `docs/research/2026-10-21-4e-wg060708-schema-design.md`（新文件） | 计划 §3 `:112` 口径：T1 优先 VOID 哨兵，只有不可表达才独占 69；T8 设计件按 70。设计件须写明 T1 的实际结果与下一可用号，不得双写。 |
| **I1 集成** | 只改 `SmartMistakeBookRoot.kt` / `SmartMistakeBookApplication.kt` | 本批 T1/T4/T6/T7 的 UI/路由尚未落地，I1 尚无对象；禁止引用未跟踪文件。 |
| **D1 设备** | 协议见计划 §4/D1 | 当前无设备在线；全部设备项 UNVERIFIED。 |
| **C1 收尾** | 记录/台账同步 | 只有在门禁与设备证据齐备后才可 `push`；网络当前不稳定（§1.1）。 |

## 6. 已知缺口与风险

1. **HEAD 编译**：静态符号缺口已由 `a200f27b` 闭合，但实际编译未跑（禁止 Gradle）→ `UNVERIFIED`。不得把“符号存在”写成“编译通过”。
2. **模块门禁**：上一批官方结论是单测门 `exit=1`、强门未过；本批尚未开始跑门禁。已知红点（KB 线）照录，失败需先归因本批 vs 他线，不声称全绿（计划 §4）。
3. **设备**：`adb devices` 空；本批没有任何设备全链结果。历史 PASS 只作历史证据。
4. **共享脏文件**：`OpenAiModelProtocol.kt`、`RoomModelTaskRepository.kt` 由他线在飞；T3 起严格串行整文件纳入并保留 hunk，T2 不得改。`TutorSessionPanel.kt`、`MistakePdfPagePlanner.kt`、`BatchImport*`、`KnowledgeReviewSessionViewModel*`、`docs/current/*`、`tools/kb_*`、`knowledge-production/*` 本批不碰。
5. **schema 争用**：T1 优先无 schema VOID；若不可表达才占 69。T8 设计件必须写明 T1 的结果与 4E 的下一可用号，不得双写。
6. **真实质量**：无凭据、无真实 Provider 数据；6B-3/PRD-09 的真实质量只能记 `UNVERIFIED`。
7. **网络**：`ls-remote` 首次连接失败、重试成功；推送仍可能失败，收口需记录并重试（计划 §6）。
8. **PRD 进度**：`docs/superpowers/plans/2026-10-19-product-readiness-followup-plan.md` 实测 `- [ ]` 50 条、`- [x]` 0 条；PRD-05 的勾选项尚未完成，本批不得预勾。

## 7. 登记边界

- 本文件只写 `docs/research/2026-10-21-batch-16-baseline.md`，不改代码、不写测试、不运行 Gradle、不执行 `git add/commit/stash/checkout/reset`、不 push。
- 他线在飞文件一律只读；本文件不代提交、不代修语义。
- 不输出、不落盘、不回显任何密钥或凭据；模型轨本批整块 `UNVERIFIED`。
- 本文所有“已具备/已存在”均为 HEAD 源码或命令输出的静态事实；“通过/完成”只在有当前执行证据时才可使用，本批 T0 没有这类证据。
