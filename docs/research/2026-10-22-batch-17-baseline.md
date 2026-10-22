# 批次 17 基线（4E WG-08 / WG-06 / WG-07 + PRD/6C-2 余项）

> 登记时点：2026-10-21（工作树当前状态；文件名按任务指定使用 2026-10-22）。
> 只读登记：本文件是 T0 的唯一产出。未运行 Gradle，未安装/启动 App，未改动任何代码，未执行 `git add/commit/stash/checkout/reset/push`。
> 所有命令均在 `D:\smart mistake book` 执行；结论只覆盖下列命令输出与源码行，不代表编译、设备或真实质量已经通过。
> 依据计划：`.zcode/plans/plan-2026-10-21-batch-17-wg08-wg06-wg07.md`（下称“计划”）。

## 1. 版本控制基线

### 1.1 HEAD、远端与分支关系

| 项 | 值 | 命令 / 来源 |
| --- | --- | --- |
| HEAD | `5bc8313628f8b22dfe888fb795ae5b1bdf1fb413`（短号 `5bc83136`） | `git rev-parse HEAD` |
| 本地 `origin/main` | `5bc8313628f8b22dfe888fb795ae5b1bdf1fb413` | `git rev-parse origin/main` |
| 本地跟踪关系 | `main` 上游为 `origin/main`，`## main...origin/main`，无 ahead/behind 标记 | `git branch -vv`、`git status -sb` |
| 领先 / 落后 | `0	0` | `git rev-list --left-right --count HEAD...origin/main` |
| 真实远端 `main` | `5bc83136...	refs/heads/main`，`ls_remote_exit=0` | `GIT_TERMINAL_PROMPT=0 timeout 30 git ls-remote --heads origin main` |
| 最近提交 | `5bc83136` 记录补推送结果；`6e5c7342` 收尾记录；`f3ea18b8` 批次 16 代码；`74876108` 批次 14 | `git log --oneline -5` |

结论：本地 `main` 与 `origin/main`、真实远端三者当前一致（0/0，无待推送提交）。本轮只读 `ls-remote` 成功；实际 push 未执行，推送权限未验证。

### 1.2 工作树计数与主要归属

写本文件之前执行 `git status --porcelain=v1`：

- 总计 **482** 项 = **75** modified（` M`）+ **407** untracked（`??`）。
- 计划 §0（计划 `:9`）登记的是 **473** 项 = 72 M + 401 ??；当前比计划多 **9** 项（+3 M、+6 ??），差值来自计划落盘后其他线继续写入。基线以本次实测 482 为准。
- 本文件写入后实测：总计 **483** = 75 M + 408 ??；多出的 1 项未跟踪即本文件，属 T0 自身产物，不属于他线在飞。

modified（75）按顶层目录：

| 目录 | 数量 | 说明 |
| --- | ---: | --- |
| `app/` | 4 | `LearningMasteryScreen.kt`、`ReviewReminderCoordinator.kt`、其测试、`KnowledgeReviewSessionInstrumentedTest.kt` |
| `core/` | 20 | `core/data` 8、`core/database` 5、`core/domain` 1、`core/export` 4、`core/ui` 2 |
| `docs/` | 34 | 含 `docs/current/*` 7 个、`docs/research/*`、`docs/superpowers/*` 等 |
| `feature/` | 10 | Tutor 会话面板、复习 ViewModel、批量导入路由等 |
| `knowledge-production/` | 1 | `wusan/materials.jsonl` |
| `tools/` | 5 | `tools/kb_build`、`tools/dense_build`、`tools/tests` |
| 根目录 | 1 | `dynamic_gui_architecture_plan.md` |

untracked（407）按顶层归属：

| 归属 | 数量 | 说明 |
| --- | ---: | --- |
| 根目录 | 355 | 266 个以 `.agent*` 开头的根级条目 + 89 个其他根级临时文件或目录（`tmp/`、`out/`、`crops/` 等） |
| `docs/` | 20 | 含 `docs/current/visual-generation-contract.md`、`docs/current/figure-generation-prompt-policy.md`、多份研究/计划文档 |
| `tools/` | 12 | 主要为 `tools/kb_coverage/*`、`tools/kb_build/*` |
| `knowledge-production/` | 11 | 知识生产临时数据/脚本 |
| `feature/` | 8 | `feature/tutor` 的 androidTest（含 `LocalImageUiInstrumentedTest` 等）与 `feature/library` 批量导入测试 |
| `app/` | 1 | `UserVisibleRecoveryInstrumentedTest.kt` |

归属判断：这些改动属于计划 §0（`:9`）列出的其他线在飞（批量导入/复习提醒/Tutor UI/导出规划/KB 工具/文档整合），**不是 T0 产出**。另以批次 16 三个提交（`f3ea18b8`、`6e5c7342`、`5bc83136`）的文件清单与当前 `git diff --name-only` + `git ls-files --others --exclude-standard` 求交集，结果为 **0**，与计划“与批次 16 提交零重叠”一致。

### 1.3 计划进度计数的当前值（供参考）

- PRD 计划 `docs/superpowers/plans/2026-10-19-product-readiness-followup-plan.md`：`^- \[ \]` **50** 条、`^- \[x\]` **0** 条（与计划 `:11` 一致）。
- UI 计划 `docs/superpowers/plans/2026-10-19-ui-ux-redesign-plan.md`：`^- \[ \]` **64** 条、`^- \[x\]` **3** 条；计划 `:11` 写的是“67 未勾 / 0 已勾”，已因其他线推进而变化。

## 2. Schema 基线

### 2.1 Room 数据库 schema

| 项 | 当前值 | 证据 |
| --- | --- | --- |
| DB 版本 | **68** | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabase.kt:130`：`internal const val STUDY_DATABASE_VERSION = 68` |
| `@Database` 声明 | 文件内 `:139` 起 | 同上 |
| 版本传入 | `:227`：`version = STUDY_DATABASE_VERSION` | 同上 |
| 迁移注册尾部 | `:385`：`ANSWER_KEY_CORRECTION_MIGRATION_67_68` | 同上 `:375-385` |
| `68.json` | 存在，358,658 字节（2026-10-20 01:39） | `ls -l core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/68.json` |
| `69.json` | **不存在** | 同目录 `ls` 报 `No such file or directory`；目录内共 68 个 schema 文件 |
| schema 69 两张新表 | 当前源码 **0 处**命中 | `grep -rn "figure_generation_operation\|figure_generation_attempt" --include=*.kt core app feature` 计数为 0；`68.json` 内同样 0 命中 |

当前实体列状态（schema 69 要改的四列）：

| 表 / 实体 | 当前列 | 缺口 | 证据 |
| --- | --- | --- | --- |
| `tutor_message_source_asset` / `TutorMessageSourceAssetEntity` | `message_id`、`source_asset_id`、`ordinal` | 无 `role`、无 `basis_revision_id` | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/entity/TutorConversationEntities.kt:254-260` |
| `clean_redraw_task` / `CleanRedrawTaskEntity` | `task_id`、`revision_id`、`source_asset_id`、`source_image_sha256`、`request_fingerprint`、`subject_id`、`status`、`attempt_count`、`last_failure_code`、created/updated | 无 `operation_id` | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/entity/CleanRedrawTaskEntity.kt:43-66` |
| `problem_revision_source_asset` / `ProblemRevisionSourceAssetEntity` | `problem_revision_id`、`source_asset_id`、`role` | 无 `ordinal` | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/entity/CaptureEntities.kt:207-213` |

计划 §2（`:21-35`）对 schema 69 的收窄口径：

- 新建 `figure_generation_operation`（`operation_id` TEXT PK，复用现有 `figure-op:` / `clean-redraw-op:` / `attached-image-op:` 字符串；含 `source_kind/source_id/basis_fingerprint/purpose/policy_version/status` 与三个审计镜像计数；索引 `(source_kind, source_id)`、`(status, updated_at)`）。
- 新建 `figure_generation_attempt`（`attempt_id` TEXT PK、`operation_id` FK RESTRICT、`attempt_ordinal`、`method` 当前只 IMAGE_MODEL、`status/provider_snapshot/model_snapshot/source_sha256/input_fingerprint/output_asset_id FK RESTRICT/reservation_id/diagnostics`、started/ended；唯一 `(operation_id, attempt_ordinal)`；索引 `output_asset_id`）。
- **不新建重复 link 表、不建 JS 表**（计划 `:23`）。
- 迁移 `FigureGenerationMigration_68_69`，版本 69，`schemas/69.json` 由 KSP 生成；契约测试 + 1→69 矩阵 + 68→69 保行（计划 `:35`）。
- 预算权威仍是 `model_task_operation.dispatch_count` / `clean_redraw_task.attempt_count`；新表计数只是审计镜像（计划 `:26`、`:35`、`:116`）。

**T1a 开工前需确认的一处口径**：计划 `:32` 把 `basis_revision_id` 与 `tutor_message_source_asset.role` 写在同一句可空列清单里，但未显式写它属于哪张表；设计件 `docs/research/2026-10-21-4e-wg060708-schema-design.md:41-44` 原先把它放在拟议的新 link 表 `tutor_message_visual` / `problem_revision_visual`，而计划 `:23` 已明确“不新建重复 link 表”。本基线不替 T1a 决定；T1a 需在写 DDL 前确认落点，避免建出计划禁止的重复表。

### 2.2 模型请求 schema

| 项 | 当前值 | 证据 |
| --- | --- | --- |
| 当前请求 schema | **18** | `core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/ModelTasks.kt:546`：`TUTOR_VISUAL_CONTEXT_SCHEMA_VERSION = 18`；`:547`：`CURRENT_SCHEMA_VERSION = TUTOR_VISUAL_CONTEXT_SCHEMA_VERSION` |
| 最低支持版本 | 1 | 同文件 `:488` |
| 本批是否改请求 schema | **否** | 计划 `:19`：6B-3 新模型任务类型/请求 schema 不做，“保持 null 注入” |
| 当前 `ModelTaskKind` | 12 个既有枚举值，无新增类型 | `ModelTasks.kt:12-25` |

## 3. 他线热区清单（本批禁改，特别标注除外）

以下路径当前均为 dirty（除特别注明），由其他线在飞；本批只读，不得修改、不得代提交。

| 热区 | 当前状态 | 说明 |
| --- | --- | --- |
| `feature/tutor/src/main/java/com/tingyun/smartmistakebook/feature/tutor/components/TutorSessionPanel.kt` | `M` | `git diff --stat` 222 行变更 |
| `BatchImport*` / `SplitImport*` | `M` / `??` | `RoomBatchImportStore.kt`、`BatchImportDao.kt`、`BatchImportRepository.kt`、`BatchImportRoute.kt` 均为 `M`；`feature/library/.../BatchImportOutcomeInstrumentedTest.kt` 为 `??`；`BatchImportRepositoryInstrumentedTest.kt`、`SplitImportCommitReceiptInstrumentedTest.kt`、`SplitImportReceiptReconciliationInstrumentedTest.kt` 为 `M` |
| `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/capture/RoomCaptureWorkflowRepository.kt` | `M` | `git diff --stat` 1 行变更；计划 `:53`、`:64` 明确 T2a/T3 不改此文件 |
| `KnowledgeReviewSessionViewModel.kt` 及 Factory/Test | `M` | `feature/review/.../KnowledgeReviewSessionViewModel.kt` +94 行左右；Factory 与 Test 同为 `M`；计划 `:80` 明确 T6 不改 |
| `core/ui/AttachedImageCard.kt`、`core/ui/BoundedLocalImage.kt` | `M` | 分别约 +48、+50 行 |
| `core/export/MistakePdfPagePlanner.kt` + 两个测试 | `M` | **UV-10 hunk 所在**：当前工作树 `:180-191` 的 `ChoiceGroup` 渲染只输出 `choiceLabel(index) + choice.text`（`:184`），相对 HEAD 删除了题面里的 `已选` / `不可选` 状态后缀。UV-10 定义见 `docs/research/2026-10-16-user-visible-followup-audit.md:20`、`:103`。T4 可整文件纳入，但必须保留此 hunk（计划 `:67`、`:115`）；若开工时该 hunk 仍在变，先停/报告 |
| `ReviewReminderCoordinator.kt`、`LearningMasteryScreen.kt` | `M` | 计划 `:13` 列为禁改 |
| `OpenAiModelProtocol.kt`、`RoomModelTaskRepository.kt` | **当前干净** | 本轮 `git status --porcelain` 对该两文件无输出；但计划 `:13` 仍将其列为“本批只读”，不得改 |
| `docs/current/*` | `M` 7 / `??` 2 | `README.md`、`architecture-contract.md`、`data-schema.md`、`implementation-status.md`、`model-task-contracts.md`、`product-contract.md`、`release-gates.md` 为 `M`；`figure-generation-prompt-policy.md`、`visual-generation-contract.md` 为 `??` |
| `tools/kb_*`、`knowledge-production/*` | `M` / `??` | 知识生产与 KB 工具线在飞，禁改 |

`SmartMistakeBookRoot.kt` 与 `SmartMistakeBookApplication.kt` 由集成轨（I1）独占，本批其他轨不得引用其新符号。

## 4. 本批入口

### 4.1 WG-08：schema 69 最小集（T1a，单写者）

- 文件：新 `FigureGenerationEntities.kt`、`FigureGenerationDao.kt`、`FigureGenerationMigration_68_69.kt`；改 `StudyDatabase.kt`、`TutorConversationEntities.kt`、`CleanRedrawTaskEntity.kt`、`CaptureEntities.kt`；契约/迁移矩阵/schema 测试（计划 `:40`）。
- 内容：两表 + 四个可空列 + 迁移 + 实体/DAO；`schemas/69.json`；版本 69；不做 recorder/role 业务（计划 `:41`）。
- 验收：1→69、68→69；旧行保行；DDL/索引/unique；预算不双计（计划 `:42`）。
- 后续：T1b operation/attempt 状态机与角色单源（计划 `:44-47`）；T5 GC/备份/僵尸对账（计划 `:71-74`）。

### 4.2 WG-06：保存 / 详情 / 显式重绘（T2a → T2b → T3）

- **T2a 保存角色**（计划 `:49-53`）：`ProblemDraftTransactionDao.commitTutorSession`、`TutorTurnMessages.kt`、`TutorConversationDao.kt`、`RoomTutorToolRunner.attachGeneratedRedrawToRevision`、角色 helper。trace kind→role（REDRAW→CLEAN_IMAGE，GENERATE_PROCESS→TUTOR_EXPLANATION）；保存同事务复制本会话助手消息的 visual link 到 revision，不复制 QUESTION_SOURCE 重复项；旧 NULL 跳过/按 trace 回退并标 legacy；文本修订复制旧图只允许 QUESTION_SOURCE/SOURCE_EVIDENCE；role-aware 挂接 + current revision CAS。
- **T2b 详情角色**（计划 `:55-58`）：`MistakeDetailDao.kt`、`RoomMistakeDetailRepository.kt`、`MistakeDetailRoute.kt`、角色 helper；详情拆题目资产/讲解资产，讲解图折叠，题目区不显示 TUTOR_EXPLANATION，打开/展开/旋转 0 派发。
- **T3 显式重绘**（计划 `:60-64`）：新 `ExplicitRedrawService.kt`；`CleanRedrawTaskRunner.kt`、`CleanRedrawTaskEntity.kt`、`CleanImageGenerator`/coordinator、`MistakeDetailRepository.kt`、`RoomMistakeDetailRepository.kt`、`MistakeDetailRoute.kt`。命令含 `problemRevisionId/expectedRevisionId/role/reason`；复用 `clean_redraw_task` 作持久身份/尝试上限/预算父操作；forced 路径跳过 TEXT_ONLY；current-revision CAS；晚到 SUPERSEDED；失败码；详情按钮/进行中/失败/重试；启动对账续跑。
- 已验证入口文件在 HEAD 索引中存在：`ProblemDraftTransactionDao.kt`、`TutorTurnMessages.kt`、`TutorConversationDao.kt`、`RoomTutorToolRunner.kt`、`MistakeDetailDao.kt`、`RoomMistakeDetailRepository.kt`、`MistakeDetailRoute.kt`、`CleanRedrawTaskRunner.kt`、`CleanImageGenerator.kt`、`MistakeDetailRepository.kt`（`git ls-files` 逐一核对）。

### 4.3 WG-07：导出角色（T4）

- 文件：`MistakePdfEligibility.kt`、`MistakePdfExportInput`（类型定义在 `MistakePdfEligibility.kt:214`，非独立文件）、`MistakePdfPagePlanner.kt`（保留 UV-10 hunk，整文件）、`MistakePdfPaperEdit.kt`、`MistakePaperDraftStore.kt`、`DocxMistakeRenderer.kt`、`DocxImageSource.kt`、`TutorExportSheet.kt`（计划 `:67`）。
- 当前基线：`MistakePdfEligibility.kt:16` 只有 `CLEAN_IMAGE_SOURCE_ROLE = "CLEAN_IMAGE"`，`:187` 的 eligibility 仍按 `asset.role == CLEAN_IMAGE_SOURCE_ROLE` 过滤，即当前只认 `CLEAN_IMAGE`，尚无角色分区。
- 做（计划 `:68`）：题目区 `CLEAN_IMAGE` 优先、其次 `QUESTION_DIAGRAM`；解析区仅 `TUTOR_EXPLANATION` 且 `includeSolution=true`；legacy 单 `CLEAN_IMAGE` 输出逐位一致；`inputSha256` 含全部 role+asset+sha；DOCX 同过滤、缺必图 fail-closed；卷面副本保留全部视觉引用；`includeSolution` 文字解析仍不可用则只做解析图区、UI 不虚假承诺。
- 验收（计划 `:69`）：JVM role/指纹/legacy golden；DOCX media 实际存在；PDF 像素层题目区不含讲解图；导出 0 派发。

### 4.4 PRD-05 / PRD-06 / PRD-08 设备用例（T6 / T7）

- **PRD-05**（计划 `:76-80`）：修 `TutorJudgedReviewSettler.kt` 的 `expectedStateVersion` 同时当 `ordinal/currentOrdinal` 的口径，使 undo 后请求讲题不 fail-closed；设备 UI 覆盖三键/双击/旋转/中断/undo→重评/无模型已存答案；隔离库；不勾真实启用。需求侧验收见 `docs/superpowers/plans/2026-10-19-product-readiness-followup-plan.md:91`（三档区别、双击、旋转、提交中断、undo→重评、跨日/同日、题版本变更、无模型本地流程）。
- **PRD-06**（计划 `:82-85`）：空库 CTA 点击→手动建题可见的设备用例。需求侧见同文件 `:93-104`：首次安装/空库/仅历史题/有待处理任务四态；手动题 `created=true` 且来源正确（`USER_TRANSCRIPTION`）；首装 CTA 必须到达可用的 LIBRARY 手动入口。
- **PRD-08**（计划 `:82-85`）：`ActivityScenario.recreate` 恢复草稿、不自动外发/不回写；force-stop 仍人工。需求侧见同文件 `:116-122`：分别测旋转、系统回收、强停/重启，不能把三者当同一种恢复。
- 文件边界：`ReviewSessionScreen.kt`（含其 controls）只在测试暴露缺口时最小修；`ReviewRoute`/`CaptureScreen` 仅最小修；`SmartMistakeBookRoot.kt` 由 I1 接（计划 `:77`、`:83`）。当前 `ReviewRoute.kt`、`CapturedReviewSessionScreen.kt`、`ReviewSessionScreen.kt`、`CaptureScreen.kt` 均在 `git ls-files` 中；索引内没有独立的 `ReviewSessionControls.kt`。

### 4.5 6C-2 机器清单 / 闭包重算（T8，只写文档）

- 文件：`docs/research/2026-10-17-6c2-content-selection-design.md` 或新附录（计划 `:88`）。
- 做（计划 `:89`）：按 schema 69 重算类别/FK/软引用/裁剪顺序/`closureVersion`/`digest`；共享 operation/attempt 按宿主与引用分行；列 §7 用例；明确三项待裁定（仅图片组合/空选择/派生索引恒裁）；不实现。
- 现有设计件 §7 从 `:547` 开始（“统一生图接入后的闭包补充（2026-10-18）”），其中 `:551` 要求实际 4E 迁移落地后从最新 Room 导出重算，不写死版本 68 或旧 84 表数量。
- 精确对照：设计件 §6.1（`:518-529`）实际列了 8 条待裁定；计划点名的三项对应其中 ①「仅图片」组合 mask=8（`:522`）、② 空选择 mask=0（`:523`）、③ 派生索引恒裁（`:524`）。T8 只需按计划明确这三项，不得把其余条目擅自代签。

## 5. 明确不做（计划 `:19`）

- WG-02 / WG-03 / WG-09 / WG-10。
- JS / QuickJS（协调器继续只执行 IMAGE_MODEL，JS 返回 UNAVAILABLE 且不广告）。
- 4E.2 / 4E.3。
- PRD-09 真实质量门。
- 5B / 5b / 5c 实现。
- 6C-2 产品实现（三项待裁定只做设计/清单，不落地）。
- 6B-3 新模型任务类型 / 请求 schema（保持 null 注入，请求 schema 仍为 18）。
- PRD-05 真实数据启用（仍等 3D 完整门）。
- UI-00~11。
- KD-61 关闭（只做反证/收窄）。

此外，T0 本轨不做：Gradle、设备安装/启动、`connectedAndroidTest`、真实 Provider 调用、任何代码或测试改动、git 暂存/提交/推送。

## 6. 已知风险与未验证项

1. **schema 69 未落地**：`69.json` 不存在，`figure_generation_*` 表在源码与 68.json 中均为 0 命中；迁移/契约/矩阵测试全部未运行。T1a 是 `StudyDatabase.kt` 的唯一写者；T0 不写。
2. **`basis_revision_id` 落点未定**：计划 `:32` 未指明表名，设计件 `:41-44` 的拟议新 link 表又被计划 `:23` 排除；T1a 开工前必须确认，否则会与“不新建重复 link 表”冲突。
3. **UV-10 hunk 在飞**：`MistakePdfPagePlanner.kt:180-191` 的未提交改动正是 UV-10 修复（删除题面 `已选/不可选`）。T4 整文件纳入时必须保留；若开工后仍在变，按计划 `:115` 先停/报告。
4. **编译与测试 UNVERIFIED**：铁律禁止 Gradle，本基线没有任何当前编译、单测、androidTest 编译或强门结果；不得把“文件存在”写成“编译通过”。
5. **设备 UNVERIFIED**：未执行 `adb devices`、未安装 App、未跑 PRD-05/06/08 或迁移/导出设备用例；历史设备证据只属于历史批次。
6. **真实质量 UNVERIFIED**：无凭据、无真实 Provider 数据；PRD-09 明确不在本批。
7. **工作树计数漂移**：计划 `:9` 的 473 已被其他线推进到 482；提交/收口时需重新取数，不得沿用本文件或计划的旧计数。
8. **热区当前干净不等于可改**：`OpenAiModelProtocol.kt`、`RoomModelTaskRepository.kt` 当前无 diff，但计划 `:13` 仍将其保留为本批只读；不得因“看起来干净”而改。
9. **`RoomCaptureWorkflowRepository.kt` 禁改**：T2a/T3 的保存/重绘链必须绕开该文件（计划 `:53`、`:64`），当前它本身有 1 行他线改动。
10. **6C-2 待裁定数量口径**：计划写“三项”，设计件 §6.1 实际列 8 条；T8 只按计划点名的三项处理，其余保持未决。

## 7. 登记边界

- 本文件只写 `docs/research/2026-10-22-batch-17-baseline.md`；不改代码、不写测试、不运行 Gradle、不执行 git 写操作、不 push。
- 他线在飞文件一律只读；本文件不代提交、不代修语义、不替他线决定 schema 设计。
- 不输出、不落盘、不回显任何密钥或凭据；本批真实模型轨整块 UNVERIFIED。
- 文中“存在/具备/命中为 0”均为当前命令输出或源码行的静态事实；“通过/完成”只在有当前执行证据时才可使用，本 T0 没有这类证据。

## 附录：本基线使用的只读命令（可复跑）

```bash
git rev-parse HEAD
git rev-parse origin/main
git rev-list --left-right --count HEAD...origin/main
git branch -vv
git status -sb
git status --porcelain=v1 | wc -l
git status --porcelain=v1 | awk '{print $1}' | sort | uniq -c
GIT_TERMINAL_PROMPT=0 timeout 30 git ls-remote --heads origin main
git log --oneline -5
git diff --stat -- <热区文件...>
grep -n "STUDY_DATABASE_VERSION" core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabase.kt
ls -l core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/68.json
ls -l core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/69.json   # 期望：No such file
grep -rn "figure_generation_operation\|figure_generation_attempt" --include=*.kt core app feature
grep -n "CURRENT_SCHEMA_VERSION\|TUTOR_VISUAL_CONTEXT_SCHEMA_VERSION" core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/ModelTasks.kt
grep -n "class TutorMessageSourceAssetEntity" -A 10 core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/entity/TutorConversationEntities.kt
grep -n "class CleanRedrawTaskEntity" -A 25 core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/entity/CleanRedrawTaskEntity.kt
grep -n "class ProblemRevisionSourceAssetEntity" -A 10 core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/entity/CaptureEntities.kt
git diff -- core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfPagePlanner.kt
grep -c "^- \[ \]" docs/superpowers/plans/2026-10-19-product-readiness-followup-plan.md
grep -c "^- \[x\]" docs/superpowers/plans/2026-10-19-product-readiness-followup-plan.md
```
