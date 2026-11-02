# KD-27 CI 红诊断（批次 40 / WS6 item 38，2026-11-02）

> 只读分析：`gh run view` / `gh run view --log-failed` + 仓库文件核对。**零 Gradle、零设备、零 git 写。**
> 结论：KD-27 的**原始红因**（Python 工具链 7 failures/27 errors、`core:export` 的 serialization 缺件、`core:database` 的 `RevisionImageSource` 缺件）已不再是当前红因；当前红因是 **HEAD 自身两处跨模块不一致 + `feature/{capture,tutor}` 设备断言漂移**。两处 HEAD 不一致的修复**已存在于工作树但未提交**，且都落在禁改热区（`core/export`、`core/database`），本轨不能落码或提交。

## 1. 当前 CI 事实（run `41351379815`，HEAD `a3f367df`）

```text
gh run list --workflow=android-check.yml --limit 8
-> 41351379815  a3f367df  failure  2026-11-01T21:19:01Z  push

gh run view 41351379815 --json jobs --jq '.jobs[] | {name,conclusion,...}'
-> check        failure
-> instrumented failure
```

### 1.1 `check` job：唯一红步 = `Unit tests`

```text
> Task :core:export:testDebugUnitTest FAILED
154 tests completed, 2 failed
MistakePdfPlannerGoldenTest > the default layout plan is pinned by explicit content and a registered digest FAILED
    java.lang.AssertionError at MistakePdfPlannerGoldenTest.kt:36
MistakePdfPlannerGoldenTest > the practice sheet plan is pinned by explicit content and a registered digest FAILED
    java.lang.AssertionError at MistakePdfPlannerGoldenTest.kt:64
BUILD FAILED in 5m 26s
```

`Knowledge build toolchain tests`、`Knowledge pack gate and table-pack consistency`、`Room schema drift check`、`Dependency lockfile manifest and coverage gate` 均 **success**（`Dependency lockfiles are up to date` 因无跟踪锁被 skip）。因此 KD-27 登记的三条历史红因（`chapter_map` 坏 slug / artifact 测试缺 manifest / `pypdf` 未装）在本轮**全部不再是红因**。

### 1.2 `instrumented` job：三类红

| 任务 | 结果 | 证据 |
|---|---|---|
| `:core:database:compileDebugKotlin` | **FAILED** | `RoomStudyDatabase.kt:2160:5 'readLatestLiveTutorVisualSnapshot' overrides nothing.` |
| `:feature:capture:connectedDebugAndroidTest` | **FAILED** | `CaptureModelTaskCardInstrumentedTest.incompleteQuestionOnlyOffersAppendWithoutDiscardingSavedPages`：`assertExists` 找不到文本 `已保存的页面不会丢失`（`:195`） |
| `:feature:tutor:connectedDebugAndroidTest` | **FAILED**（5 例） | `ClaudeMarkdownSlotInstrumentedTest` 两例（toolbar `first()` vs `null`；fenced code 字面量）；`LocalImageUiInstrumentedTest` 摘要不可见；`ReferenceEditorLayoutInstrumentedTest` `editor_source_reference` 不可见；`TutorKeyboardLayoutInstrumentedTest` 5 s 超时 |

## 2. 红因归因（先归因本批/他线/环境）

### 2.1 `core:export` golden 红：HEAD 测试与 HEAD 实现不一致（**他线/主线程提交面**）

- HEAD 的 `MistakePdfPagePlanner` 已不再把 `[已选]` 追加到题面选项行（`git show HEAD:core/export/src/main/.../MistakePdfPagePlanner.kt | grep -n '已选\|不可选'` 只命中 KDoc `:333`，无输出拼接）。
- HEAD 的 `MistakePdfPlannerGoldenTest` 仍期望 `line/CHOICE/18.00/false/0/B. 先减后增 [已选]`（`git show HEAD:...MistakePdfPlannerGoldenTest.kt` 第 48/77 行附近）与旧摘要。
- 工作树**已有** UV-10 重登记：`git diff -- core/export/src/test/.../MistakePdfPlannerGoldenTest.kt` 把两条期望改为无后缀，并把两个摘要改为 `2ba874cc…` / `d9f928e1…`；同时 `MistakePdfPagePlannerTest.kt` +41、`MistakePdfEligibilityTest.kt` +31。
- **结论**：CI 在跑 HEAD，而修复只在工作树；`core/export` 是热区，且本轨无 git 写权限 ⇒ **不可修**。需该线把工作树这三个测试文件提交（或主会话裁定整文件纳入）。

### 2.2 `core:database` 编译红：HEAD 实现覆盖了 HEAD 端口不存在的方法（**他线/主线程提交面**）

- HEAD `RoomStudyDatabase.kt:2160` 有 `override suspend fun readLatestLiveTutorVisualSnapshot(programId: String)`。
- HEAD `TutorVisualPort.kt` **没有**该方法（`git show HEAD:...TutorVisualPort.kt | grep -n readLatestLive` 无输出）。
- 工作树 `TutorVisualPort.kt` **+15 行**新增该 default 方法（`git diff -- core/database/.../port/TutorVisualPort.kt`），签名与实现一致；`RoomStudyDatabase.kt` 对 HEAD 零 diff。
- **结论**：HEAD 内部不一致，CI 必然 `overrides nothing`。修复已在工作树；`core/database` 他线 M（knowledge 面）且为热区 ⇒ **不可修**。需该线提交端口改动。

### 2.3 `feature/{capture,tutor}` 设备红：断言与当前 UI 语义漂移（**他线/UI 面**）

- 6 例都是**确定性语义断言**（找不到节点/文本、期望 `first()` 实得 `null`、5 s 内条件不满足），不是崩溃、不是安装失败、不是资源通胀；不是本批 WS6 引入。
- 失败集中在 `feature/capture` 与 `feature/tutor`——共享纪律列为热区，且 `feature/tutor` 有未提交 UI 续作。
- **结论**：归他线/UI 面；WS6 不改。需要该线按当前实现更新用例或修 UI；不得以放宽断言/删例方式转绿。

### 2.4 不是红因的项

- Python 工具链：`Knowledge build toolchain tests` 在本轮 success；日志中的 `--dry-run 与 --write 互斥`、`malformed gate input` 是**负向测试的预期输出**，不是失败。
- 锁：无跟踪锁，`Dependency lockfiles are up to date` 被 skip；KD-72 仍未落地，与本轮红无关。
- 环境：`instrumented` 的模拟器启动/KVM 步骤未报错；失败发生在测试执行阶段，非环境。

## 3. 可修项与不可修项

| 项 | 可修性 | 动作 |
|---|---|---|
| `core:export` golden 重登记 | ❌ 热区 + 需提交 | 工作树已有修复；由该线/主会话提交 |
| `core:database` 端口方法 | ❌ 热区 + 需提交 | 工作树已有修复；由该线/主会话提交 |
| `feature` 设备 6 例 | ❌ 热区/他线 | 由 UI 线按当前语义修用例或 UI |
| `docs/status.md` 冻结 | ◐ 可本地重生成，但非 CI 产物 | 不建议本轮做：本地 build 产物多数 `NOT_MEASURED`，重生成会给出误导性"绿"；等 CI 转绿后由 workflow 写回 |
| commit-back 条件 `if: success()` | ❌ 不应改 | 改成 `always()` 会把红构建的 status 写回 main，掩盖红；KD-27 的正解是让 `check` 转绿 |
| KD-27 台账回链 | ✅ | 本文 + `docs/known-defects.md` Progress 块 |

**本轨落码 = 0 行产品代码**：两处 HEAD 不一致的修复都在禁改热区且已存在于工作树，本轨只能诊断与登记；不把"工作树有修复"写成"CI 已修"。

## 4. 转绿的最小路径（按依赖排序）

1. 提交 `core/database/.../port/TutorVisualPort.kt` 的 15 行端口方法（与 `RoomStudyDatabase.kt:2160` 配对），使 `:core:database:compileDebugKotlin` 过。
2. 提交 `core/export` 三个测试文件（golden 重登记 + 两条 UV-10 断言），使 `:core:export:testDebugUnitTest` 过。
3. 由 UI 线处理 `feature/{capture,tutor}` 6 例：按当前 UI 语义更新用例或修 UI；不得放宽断言。
4. 上述三项都进 main 后，`check` job 应转绿；`Commit status report` 才会执行，`docs/status.md` 恢复为 CI 产物。
5. 若届时 `check` 仍红，按 `gh run view <id> --log-failed` 的**步骤级**结论重新归因，不猜（KD-27 Reopen condition 原文）。

## 5. 来源

| 类型 | 来源 | 用途 |
|---|---|---|
| CI | `gh run list --workflow=android-check.yml --limit 8`；`gh run view 41351379815 --json jobs`；`gh run view 41351379815 --log-failed`（行 9790-9840、19025-19060、19230-19270、19385-19435） | 红步、失败用例、编译错误 |
| 台账 | `docs/known-defects.md:1061-1098` | KD-27 历史红因与 Reopen condition |
| 工作流 | `.github/workflows/android-check.yml:39-180` | 步骤顺序、`if: success()` commit-back |
| 仓库 | `git show HEAD:core/export/.../MistakePdfPlannerGoldenTest.kt`；`git show HEAD:core/database/.../port/TutorVisualPort.kt`；`git diff -- core/export/src/test core/database/.../port/TutorVisualPort.kt` | HEAD 不一致与工作树修复 |
| 本轨实跑 | `git status --porcelain -- docs/status.md`；`git log -1 -- docs/status.md` | status.md 冻结于 2026-10-24 手工重生成 |
