# 主干整合记录（2026-11-12）：工作树/分支合并尝试与结论

> 执行：主会话，隔离 clone `D:/smbk-ui-merge`（不触碰共享主工作树）。
> 目标（用户指令）：把不同工作树里的全部合到主干。
> 结论先行：**机械合并不可行**。工作树分支都是旧世代（落后 main 44–837 提交），
> 而 main 真正最新的代码有 **105 个 Kotlin 文件未提交**（83 M + 22 ??）。合并分支产出的树
> 反复因「分支引用了 main 未提交的类型/字段」而编译失败。正确路径是先提交 main 工作树的
> 最新世代，再由各分支 owner rebase；本分支只作为进度与证据保留。

## 1. 工作树/分支盘点（相对 main ef6a9d97）

| 工作树 | ref | 独有提交 | 工作树脏 | 处置 |
|---|---|---:|---:|---|
| plans-publication | codex/plans-2026-10-20 | 1 | 0 | 已并入（7 处文档冲突保留 main 较新版） |
| ui-green-pages | codex/ui-green-pages-2026-10-27（基准 387e8009） | 8 | 0 | 已并入（4 冲突），并补入 main 未提交的 KD-67/AIGC/model 半边 |
| verify-visible-fixes | detached a15aebb7 | 0 | 33（已暂存） | 其内容已在 main（StorageOperationsViewModel/MappingPagingSource 等，f094b9ad）；3-way 应用 18 冲突，未采用 |
| aud11-measure | detached a15aebb7 | 0 | 288（1 code + 287 审计截图/脚本） | 代码改动为旧世代；287 个审计产物留在原工作树，未入产品树 |
| kb-bench | detached 0486a532 | 0 | 4 | 基准文件已在 main；未重复合入 |
| kernel-readiness | audit/kernel-readiness | 44 | 0 | **尝试后回退**：模型层是旧世代（缺 main 的 TutorRoundQuestionDeclaration、引用 main 已删除的 TutorVisualScene），58 冲突按主干优先仍无法编译 |
| ui-rebuild-continuous-response | 分支（无工作树） | 110 | — | 落后 1187 提交，非工作树范围，未合 |

## 2. UI 分支合并（已落在本分支）

- 冲突 4 处：`FigurePromptAssembler`（取 UI 完整教样例——真实 visual-sdk/compiler 探针 compiles=true/labels=9——叠加 main 硬约束）、`RoomStudyDatabase`/`StudyDatabasePort`（并集，paperReviews 单份）、`MistakePdfExporterAigcJvmTest`（GENERATION=3 × v5-math-layout 指纹）。
- 为让 UI 世代与 main 功能共存，补入 **main 工作树未提交** 的件：
  - `KnowledgeCalibrationDecision.kt`（KD-67 UI 判定类型）
  - `feature/review` + `Root/Application` 的 KD-67 参数接线（3-way cleanly）
  - `core/ui/AiContentBadge.kt` + `AttachedImageCard` 的 AI 角标
  - `TutorPlan/RespondInput.figureGenerationCapabilities`、`TutorPlan/RespondOutput.teachingFigureOmission/deliveryToolTraceJson`（均默认 null，EncodeDefault NEVER）
- 已登记的丢弃：复习会话屏/VM 的 UI 视觉 delta（功能世代优先）；`GeneratedFigureCard` 的 AI 角标（UI 版无卡头行，需布局工作）。

## 3. 构建结果（13 次，串行、无并发）

- 依次越过：AGP 依赖校验（补 lifecycle 2.8.3 共 10 个模块校验和，来源 Google Maven，单来源未交叉核验）→ `core:model`（TutorTasks/SceneTypes 世代冲突）→ `core:data`（输出字段并集）→ `core:ui`（AttachedImageCard 签名）→ `:app`。
- 最新失败（`build/b51-trunk13.log`）：`:app SmartMistakeBookRoot.kt:1769/1787`，`errorCausePanel` 参数与 `entryId` 不存在——对应 `feature/library/MistakeDetailRoute.kt` 的 **main 未提交版本**（5c 错因面板 UI 半边）。
- 这不是最后一处：main 还有 105 个未提交 Kotlin 文件（83 M + 22 ??），包括 5b 容量、6B-3 改卷、5c 面板等 UI 半边的剩余部分。

## 4. 结论与建议

1. **“主干”目前不是任何分支，而是 main 工作树**。要整合工作树，第一步必须先在 main 上把 105 个未提交 Kotlin 文件按批次归属提交（或明确丢弃），否则任何从 HEAD 出发的构建都缺半边。
2. 工作树分支（UI 44 behind / kernel 837 behind）不适合机械 merge：它们的模型/数据层与 main 已分叉，逐文件并集会产生持续的类型/字段缺口（本次已补 6 组，仍有剩余）。
3. 建议顺序：
   a. 主会话/owner 提交 main 工作树的 5b/5c/6B-3/KD-67 等 UI 半边（本分支已给出所需文件清单与补丁）；
   b. UI 分支 rebase 到该提交，仅保留视觉 delta；
   c. kernel 分支按审计项 rebase（其 44 提交的模型层重构不能整包带入）；
   d. 再跑一次本记录 §3 的 release 构建作为整合验收。
4. 本分支 `codex/trunk-integration-2026-11-12` 保留全部中间提交与构建日志，供继续；**未推送 main、未改共享主工作树**。

## 5. 残余风险

- lifecycle 2.8.3 校验和为单来源记录（未第二来源交叉核验）。
- UI 合并丢弃了两处视觉 delta（复习会话屏/VM、GeneratedFigureCard 角标），需 UI owner 后续补。
- kernel 分支未合入，其 44 个审计提交仍在原分支。
- 本记录不改变四门与发版终裁：3D/3E/MVA/AUD-08 均未真实通过，发版不成立。
