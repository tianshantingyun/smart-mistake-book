# 6B-3 改卷 UI（自然语言改卷入口 / 预览 / 采用）— 批次 50 证据

> 计划：`.zcode/plans/plan-2026-11-09-batch-50-release.md` 产品面项 30（`:62`）；
> 直接要求：卷面/详情加自然语言改卷入口——输入命令 → `PaperEditModelPort` → 预览 diff →
> 采用/取消；失败保留原文；不伪造模型回执。
> **本轨不改任何门值；不碰 `core:data` 的派发预算 / `PaperEditModelPort` 契约。**

## 1. 终态

| 要求 | 结论 | 证据 |
|---|---|---|
| 卷面页有自然语言入口 | **成立**：`MistakePaperEditRoute` 顶部 `AI 改卷面（一句话）` chip → 展开输入框 | `feature/library/src/main/kotlin/com/tingyun/smartmistakebook/feature/library/NativePaperEditModelSection.kt:37`；`MistakePaperEditRoute.kt:281`（接入位置）；设备用例 `theAiSectionIsNotAdvertisedWithoutAModelPort`（对照）与 `aModelPreviewIsShownBeforeAdoptionAndOnlyAdoptionChangesThePaper` |
| 详情页有自然语言入口 | **成立**：当前版本显示 `AI 改卷面` chip，历史版本不显示；点击进入卷面页并展开 AI 区 | `MistakeDetailRoute.kt:502`（chip）、`:129`（`onModelEditPaper`）；`SmartMistakeBookRoot.kt:287`（`?ai={ai}` 路由）、`:1758`（导航）；设备用例 `theMistakeDetailOffersTheAiPaperEntryForTheCurrentRevision` / `aHistoricalRevisionDoesNotOfferTheAiPaperEntry` |
| 输入 → `PaperEditModelPort` | **成立**：`NativePaperEditModel.buildInput` 生成有界 `PaperEditInput`，`request()` 调端口；app 装配 `RepositoryPaperEditModelPort(application.modelTaskRepository)` | `feature/library/src/main/kotlin/com/tingyun/smartmistakebook/feature/library/NativePaperEditModel.kt:114`（`buildInput`）、`:153`（`request`）；`app/src/main/kotlin/com/tingyun/smartmistakebook/SmartMistakeBookRoot.kt:1624`（装配）；JVM `the app consent value is forwarded as given...` 钉住同意位透传 |
| 预览 diff | **成立**：白名单命令在**副本**上逐条应用，产出 `NativePaperEditDiff`（题目标签 + 变化 + 改前/改后）；卷面实例在预览阶段不变 | JVM `whitelisted commands produce a preview without touching the source paper`；设备同名前缀用例（预览阶段标题仍为原文） |
| 采用 / 取消 | **成立**：采用才有 CAS（必须仍是生成预览的同一卷面实例）；取消清空预览、卷面不动 | JVM `a preview can only be adopted on the exact paper...`；设备 `…OnlyAdoptionChangesThePaper` |
| 失败保留原文 | **成立**：端口失败/取消/未落终态、命令非法/越界、空命令、无实际变化、改完不可导出全部 `Rejected`；卷面与输入框要求都保留 | JVM `a model failure keeps the paper...`、`a cancelled task...`、`an illegal or out-of-range command...`；设备 `aRejectedModelReplyKeepsThePaperAndTheStudentsInstruction` |
| 不伪造模型回执 | **成立**：失败只说本地可判定事实（失败/取消/没有可执行修改），不回显供应商正文、不显示"已改好"；预览只在解析+应用全部成功后出现 | JVM 失败用例断言 reason 不含 `provider said`；`previewFor` 对空命令/无变化直接 `Rejected` |
| 等待期间学生手改不被覆盖 | **成立**：结果到达时 `edit !== paper` → 丢弃并说明 | 设备 `aManualEditWhileWaitingPreventsTheStalePreviewFromBeingApplied` |
| 未启用模型不广告"能跑" | **成立**：AI 区仍在，但说明"尚未启用模型"+ 设置入口，生成按钮禁用、0 次端口调用 | 设备 `withoutConsentTheSectionExplainsWhyItCannotRun` |

**结论：6B-3 改卷 UI 代码级完成 + JVM/设备（fake 端口）验收通过；真实 Provider 输出质量 UNVERIFIED。**

## 2. 改动文件

新增（feature:library）：

- `src/main/kotlin/.../NativePaperEditModel.kt`：`MistakePdfPaperEdit → PaperEditInput` 映射、
  `paperIdFor`/`revisionFor`、白名单命令应用 + diff、CAS 采用、端口失败分类。
- `src/main/kotlin/.../NativePaperEditModelSection.kt`：AI 区 UI（chip / 输入框 / 生成 / 预览 diff /
  采用 / 取消 / 未启用说明）。
- `src/test/kotlin/.../NativePaperEditModelTest.kt`：17 例（映射/块类型、预算、预览、整批拒绝、失败保留、
  同意位、CAS、revision/paperId 稳定性、不可表达输入）。
- `src/androidTest/kotlin/.../NativePaperEditModelInstrumentedTest.kt`：7 例设备 UI（无端口不广告 /
  预览→采用 / 取消不动卷面 / 改要求作废旧预览 / 拒绝保留 / 等待手改丢弃 / 未同意禁用）。

修改（最小 hook）：

- `feature/library/.../MistakePaperEditRoute.kt`：`modelPort` / `modelConsentGranted` /
  `initialModelEdit` / `onOpenModelSettings` 参数；AI 区状态与请求/采用/取消；预览期间手动编辑
  时 stale 提示。
- `feature/library/.../MistakeDetailRoute.kt`：`onModelEditPaper` 参数 + 当前版本 `AI 改卷面`
  chip（历史版本不显示）。
- `feature/library/src/androidTest/.../NativePaperEditRouteInstrumentedTest.kt`：+2 详情入口用例。
- `app/.../SmartMistakeBookRoot.kt`：卷面路由 `?ai={ai}`、`RepositoryPaperEditModelPort` 装配、
  详情 `onModelEditPaper` 导航、`modelConsentGranted = modelConfiguration.isConfigured`。

## 3. 红 → 绿

1. **红（先写测试）**：`./gradlew :feature:library:testDebugUnitTest --tests
   "...NativePaperEditModelTest"` 首跑失败于 `:feature:library:compileDebugUnitTestKotlin`：
   `Unresolved reference 'NativePaperEditModel'` / `'NativePaperEditPreview'` /
   `'NativePaperEditAdoption'`（30+ 条，本会话 2026-11-08 首跑）。这是预期的红。
2. **绿（JVM）**：实现后同命令 `BUILD SUCCESSFUL`；XML
   `tests="17" skipped="0" failures="0" errors="0"`（`TEST-com.…NativePaperEditModelTest.xml`）。
3. **模块回归**：`:feature:library:testDebugUnitTest` 全量 17 个 XML、**108 tests / 0 failures /
   0 errors / 0 skipped**（`module-unit-suite.log`）。
4. **设备**：`:feature:library:connectedDebugAndroidTest` 过滤两类 →
   `NativePaperEditModelInstrumentedTest` 7/7 + `NativePaperEditRouteInstrumentedTest` 10/10
   （`instrumented-results.xml`：`tests="17" failures="0" errors="0"`）。
5. **编译**：`:app:compileLocalFirstDebugKotlin :app:compileStrictOfflineDebugKotlin`
   `BUILD SUCCESSFUL`（`app-compile.log`；末次为 UP-TO-DATE：Gradle 增量判定 feature 模块 ABI
   未变；feature 模块本身已在设备构建中重编译）。

设备：`emulator-5590`（`ui_motion_20261107(AVD) - 14`），x86_64 / API 34 / Android 14，
**模拟器，不是真机**。

## 4. 复跑命令

```bash
./gradlew :feature:library:testDebugUnitTest \
  --tests "com.tingyun.smartmistakebook.feature.library.NativePaperEditModelTest" --console=plain

./gradlew :feature:library:testDebugUnitTest --console=plain

./gradlew :feature:library:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.tingyun.smartmistakebook.feature.library.NativePaperEditModelInstrumentedTest,com.tingyun.smartmistakebook.feature.library.NativePaperEditRouteInstrumentedTest \
  --console=plain

./gradlew :app:compileLocalFirstDebugKotlin :app:compileStrictOfflineDebugKotlin --console=plain
```

## 5. 未验证 / 风险

- **真实 Provider 的改卷回执质量 UNVERIFIED**：设备用例全部用 fake `PaperEditModelPort`；
  没有凭据，也没有发起真实外发。`RepositoryPaperEditModelPort` 的真实链路只由既有
  `core/data` 用例（JVM `PaperEditModelPortTest` + 设备 `PaperEditLedgerInstrumentedTest`）覆盖，
  本轨未重跑它们。
- **app 级端到端导航 UNVERIFIED**：详情 chip 的 `?ai=1` 路由参数没有在真机/模拟器上走完整
  NavHost；模块级路由与详情回调已绿。区域模式与既有 `Routes.ExportResults?exportId=…` 相同。
- **共享树冲突风险**：`MistakeDetailRoute.kt`、`SmartMistakeBookRoot.kt` 是 5c 线在飞文件，
  本轨只加最小 hook（参数 + chip + 导航）。若 5c 以整文件回写，需重新应用本轨的 4 处小改；
  当前工作树编译绿且 5c 的 `errorCausePanel` 改动仍在。
- **未跑 lint / detekt / 独立复核**：本轨只跑 JVM、模块单测、instrumented 与两 flavor 编译。
- **模型端口重复实现登记**：`NativePaperEditModel.previewFor` 与 `feature:tutor` 的
  `TutorPaperEditApplier` 是同一套白名单/卷面方法的两份实现（后者 `internal` 且不产出 diff）。
  合并路径见新文件 KDoc；本批不跨模块重构，避免碰 tutor 热区。
- **`local.properties` 含明文 `SMB_REAL_PROVIDER_KEY`（gitignored）**：本会话读取该文件时看到该行
  （未在结果/证据中输出、未复制、未提交）；建议轮换并改为环境变量注入。

## 6. 证据清单

- `jvm-green.log`、`TEST-com.tingyun.smartmistakebook.feature.library.NativePaperEditModelTest.xml`
- `module-unit-suite.log`
- `instrumented-green.log`、`instrumented-results.xml`
- `app-compile.log`
