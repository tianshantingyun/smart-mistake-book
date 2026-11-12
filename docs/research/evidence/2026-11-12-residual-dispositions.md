# 残余项处置结论（2026-11-12，最终 HEAD 6e3afd12）

> 对应目标「不再留尾巴、边界放到最宽、全量整合」。本文件给出三项内容层残余的明确处置，
> 并提供可追溯指针；分支/工作树/未跟踪文件层面的闭环见
> `docs/research/2026-11-12-trunk-integration-record.md` §8 与
> `docs/research/evidence/2026-11-12-workspace-archive.md`。

## 0. 最终构建证据（当前 HEAD）

- HEAD `6e3afd12` = `origin/main`。
- 干净 clone（`D:/smbk-clean-b51-8dd29e96/src`，0 脏行）执行：
  `./gradlew --offline :app:assembleLocalFirstRelease -Psmbk.versionCode=101194`
  → **BUILD SUCCESSFUL in 1m 58s**，exit 0（日志 `build/b51-head6e3a.log`，APK 156 MB）。
- 此前同代码树 `31e3c601` 亦通过（2m49s）；`6e3afd12` 仅追加本文档。

## 1. kernel-readiness 代码层：结论 = 已被当前世代取代，分支保留可追溯

- 该分支（`audit/kernel-readiness`，tip `de1ff0c7`，44 个审计提交）已作为祖先合并进 main
  （merge `a3b578c7`，后经 revert-of-revert 恢复祖先关系）。
- **代码层不应用**：其模型/排程/UI 世代与统一版不兼容（`requireTutorScene*`、
  `estimateAt(zoneId)`、`MasteryBands` 重载、旧 dependency lock state 等持续级联）。
  合并时 59 处冲突按「统一主线优先」裁定，61 个仍取分支内容的文件整体回到 main。
- **文档层已入主干**：ADR 0001–0003、replay 可信度计划、状态页/CI 文档等。
- **可追溯指针**：分支本体在远端保留（`origin/audit/kernel-readiness` = `de1ff0c7`），
  其完整差异可从该 ref 取得；不另存巨型补丁（含 KB/二进制，198MB，已弃）。
- **后续动作（owner）**：在新 main 上按审计项逐条 rebase，一次一项、带测试，不整包带入。

## 2. UI 视觉 delta：结论 = 已归档为补丁，待 UI owner 叠加

- UI 基准 `387e8009` 已并入；以下文件的视觉 delta 在冲突中按「功能世代优先」被取代：
  `feature/tutor/`、`feature/review/KnowledgeReviewSessionScreen.kt`、
  `KnowledgeReviewSessionViewModel.kt`、`app/.../SchedulingSettingsScreen.kt`、
  `core/ui/.../AttachedImageCard.kt`（仅图号/图注部分；AI 角标已保留）。
- **归档补丁**：`D:/smbk-workspace-archive-2026-11-12/_worktree-patches/ui-dropped-visual-deltas.patch`
  （216 KB，`a7f4ad4f...387e8009` 的对应文件差异）。
- 叠加建议：在新 main 上按文件逐个 `git apply --3way`，先跑该模块单测再进设备；
  `AttachedImageCard` 需与 AI 角标并存。

## 3. Dependabot 分支：结论 = 不并入，属外部自动化升级提案

远端 9 个分支，均为 Dependabot 生成的依赖/动作升级提案，不是工作树里的人工改动：

```
dependabot/github_actions/actions/checkout-7
dependabot/github_actions/actions/setup-java-5
dependabot/github_actions/actions/upload-artifact-7
dependabot/github_actions/android-actions/setup-android-4
dependabot/gradle/agp-9.3.1
dependabot/gradle/com.patrykandpatrick.vico-compose-3.3.0
dependabot/gradle/filament-1.75.0
dependabot/gradle/kotlin-2.4.10
dependabot/gradle/okhttp-5.5.0
```

不并入的理由：

1. 会改变构建工具链/运行时依赖（AGP、Kotlin、okhttp、filament 等），必须走**独立升级批次**：
   依赖校验和、lockfile、R8/mapping、16KB/ABI、迁移与全量回归都要重跑；
2. 与本次「整合既有工作树」的目标不同源，盲合会绕过依赖验证与发布门；
3. 分支由 Dependabot 自动维护，保留即为其正常形态，不构成主干未整合的「尾巴」。

处置：**保持现状（不合并、不删除）**；需要升级时按批次逐条验证后合入。

## 4. 结论

- 工作树/分支/未跟踪文件三个层面已闭环；当前 HEAD 已用干净 clone 构建留证。
- 内容层三项均有明确结论与可追溯指针：kernel 代码保留在分支 ref、UI delta 归档补丁、
  dependabot 留作独立升级批次。
- 四门与发版终裁不变：3D/3E/MVA/AUD-08 均未真实通过，发版不成立。
