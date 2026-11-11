# 主干统一最终版记录（2026-11-12）

> 目标（用户指令）：把工作树里的全部改动提交、合并清楚，形成一个统一最终版本。
> 执行：主会话；共享主工作树用于提交源改动，合并/构建在隔离 clone（`D:/smbk-ui-merge` /
> `D:/smbk-clean-b51-8dd29e96/src`）完成。

## 0. 结论

**统一最终版 = 新 main（主工作树最新世代）+ UI 视觉基准 + plans**，已通过干净 clone 的
`:app:assembleLocalFirstRelease`（BUILD SUCCESSFUL）。kernel-readiness 的 44 个审计提交
**未能机械并入**（依赖锁状态与模型世代冲突），已回退并保留原分支，见 §3。

## 1. 主工作树全部源码提交（新 main，4 笔）

| 提交 | 内容 |
|---|---|
| `feat(core)` | core/model、core/data、core/database、core/domain、core/ui、core/export 的 96 个文件（含 5b/5c/6B-3/KD-67/生图契约/AIGC 半边与 lockfile、备份 v4 夹具） |
| `feat(app/feature)` | app + feature 的 34 个文件（KD-67 接线、5c 面板、6B-3 改卷、容量区块、tutor 标签；排除 Windows 保留名 `nul`） |
| `chore(tools/kb)` | tools/ci、kb_build、kb_coverage、dense_build、kb_perf、tests 与 `knowledge-production/wusan/materials.jsonl`（60 个文件；排除 `_tmp_*`/`__pycache__`） |
| `docs` | docs 67 个文件 + `settings-gradle.lockfile` + 动态 GUI 架构计划 |

- 新 main HEAD：`a7f4ad4f`。
- **干净 clone 全量 release 构建：BUILD SUCCESSFUL in 6m 25s**（APK 156 MB）。
- 未提交的仅剩临时产物（根目录 `x/ tmp/ out/ output/ growth/ crops/ tmp_judge_8/`、
  `tmp_slice*.txt`、`_tmp_*`、`nul`），已登记为不入库。

## 2. UI + plans 并入统一最终版

- UI 分支 `codex/ui-green-pages-2026-10-27`（基准 `387e8009`）合并到新 main：**15 处冲突**，
  按「新 main 优先」；`AttachedImageCard` 取 UI 版（图号/图注）并补回 AI 显式角标。
- plans 分支 `codex/plans-2026-10-20`：**9 处文档冲突**保留 main 较新版。
- 为消除 UI 旧世代与新 main 的签名级联，以下文件/目录取新 main（UI 视觉 delta 未保留，已登记）：
  `feature/tutor` 整目录、`app/.../SchedulingSettingsScreen.kt`、
  `feature/review/{KnowledgeReviewSessionScreen,KnowledgeReviewSessionViewModel}.kt`。
- **统一分支全量 release 构建：BUILD SUCCESSFUL in 2m 57s**。

## 3. kernel-readiness：尝试两次，回退

- 合并 44 个审计提交 → 59 处冲突，按统一主线优先（add/add 取分支新文件）。
- 构建失败：`:core:domain` 的 **dependency lock state** 仍指向旧世代版本
  （`androidx.annotation:1.9.1`、`paging-common:3.4.2` 等无法解析），不是代码语义问题而是
  旧 lockfile + 模型世代双缺口。
- 处置：`git revert -m 1` 回退该 merge；原分支 `audit/kernel-readiness` 未动。
  其 44 个审计提交需 owner 在新 main 上 rebase（或按审计项逐条移植），不能整包带入。

## 4. 其余工作树

| 工作树 | 结论 |
|---|---|
| verify-visible-fixes（a15aebb7，33 个暂存） | 内容已在 main（StorageOperationsViewModel/MappingPagingSource 等，f094b9ad）；3-way 18 冲突，未采用 |
| kb-bench（0486a532，4 个改动） | 基准文件已在 main；未重复合入 |
| ui-rebuild-continuous-response（无工作树） | 落后 1187 提交，超出范围 |

## 5. 残余风险（引用结论时必须带）

1. UI 视觉 delta 在 `feature/tutor`、复习会话屏/VM、设置页未保留（功能世代优先）。
2. kernel 分支未合入；其审计项仍待 rebase。
3. `GeneratedFigureCard` 的 AI 角标未加（UI 版无卡头行）。
4. 主工作树仍有临时产物未入库（不属于产品源码）。
5. 四门与发版终裁不变：3D/3E/MVA/AUD-08 均未真实通过，发版不成立。
6. lifecycle 2.8.3 依赖校验和为单来源（Google Maven 下载产物），未第二来源交叉核验。

## 6. 与远端 main 的最终合并（2026-11-12 收口）

- 推送时远端已前进 2 个提交（`1bc59d68 merge: 合并 UI 基准与主分支收口`、`f1c087fc feat(settings): 接入独立生图模型配置`）。按纪律 **merge，不 rebase**。
- 合并冲突 4 处（FigurePromptAssembler / RoomStudyDatabase / StudyDatabasePort / AIGC 测试），全部保留本地统一版（最新世代且已通过构建）。
- 自动合并后在 `KnowledgeReviewSessionViewModel` 出现重复声明（Git 未报冲突）；该文件取合并前统一版 `110fe428`，其余远端设置功能保留。
- **最终 main `aeb004fe` 干净 clone 全量 release 构建：BUILD SUCCESSFUL in 2m 57s。**
- 推送结果：`f1c087fc..aeb004fe  main -> main`，远端 = 本地。
- 最终版本构成：新 main 最新世代（主工作树 105 个 Kotlin 文件 + 工具/文档）+ UI 视觉基准（未冲突页面/主题）+ plans；kernel 分支未并入。
- 工作树仅余临时产物未跟踪；tracked 工作树 0 脏行（`.gitignore` 已提交）。

## 7. kernel-readiness 最终处置（2026-11-12，main 89fb2ebd）

- 采用「撤销 revert」恢复该 merge 的祖先关系，再把**代码层整体回到 main**（61 个仍取分支内容的
  文件全部 checkout 回 `2cddcf42`，含模型/排程/UI/构建脚本/依赖校验和）。
- 保留分支的 **docs/ADR/审计报告/计划**（0001–0003 ADR、replay 可信度计划、状态页工具文档等）。
- 干净 clone 全量 release 构建：**BUILD SUCCESSFUL in 3m 7s**；`89fb2ebd` 已推送
  （`2cddcf42..89fb2ebd  main -> main`），tracked 工作树 0 脏行。
- **该分支的 44 个审计提交的代码改动未应用**（模型/排程/UI 世代与统一版不兼容），
  需 owner 在新 main 上按审计项 rebase；文档层已入主干。
- 至此工作树分支（plans/UI/kernel）在 main 历史中均有合并祖先；`ui-rebuild`（无工作树、
  落后 1187 提交）不在本次范围。
