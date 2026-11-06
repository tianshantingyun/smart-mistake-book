# WS7 / D-04 预览生产装配：默认关闭的注入点（批次 42，2026-11-04）

> 计划：`.zcode/plans/plan-2026-11-04-batch-42.md` §2 WS7 项 56–60。
> 依据：`docs/research/2026-11-01-preview-wiring.md`（R1，下称 R1）§2.1–§2.7、§3.4；批次 39 决策台账 `docs/agent-first-refactor-decisions-2026-09-23.md:3723-3738`。
> 本文件只登记**落码、判据、执行状态与阻塞**；不代替门禁日志或设备 E-file。

| 字段 | 值 |
|---|---|
| evidence-id | E-42-WS7-01 |
| convention-version | 1 |
| batch | 2026-11-04-batch-42 |
| step | 计划 §2 WS7 items 56–60（`.zcode/plans/plan-2026-11-04-batch-42.md:92-97`） |
| gate-or-kd | D-04 预览生产装配（MVA 前置） |
| verdict | **PARTIAL / BLOCKED**：3 个新文件在树、默认关闭；JVM/设备用例全部 **UNVERIFIED**（本轨零 Gradle/零 adb）；生产 commit 调用点接线 **BLOCKED**（热区 + 调用方不存在 + 广告面红线） |
| executed-at | 2026-11-03（本机时间；文档名按计划批次 42 的 2026-11-04） |
| operator | WS7 装配轨 |
| head | `faab17f5`（索引整理时 `git rev-parse HEAD`；本文件成文时未自记） |
| device-serial | **N/A**（本轨零设备） |
| apk-sha256 | **N/A**（本轨零构建） |
| exit-code | 无（零 Gradle/零 adb；仅静态 `git diff --stat` 核对为空，未记录 exit code） |
| raw-log | 无（未产生执行日志） |
| record-ref | 计划 WS7 56–60；`docs/research/2026-11-01-preview-wiring.md`；批次 39 决策台账 `docs/agent-first-refactor-decisions-2026-09-23.md:3723-3738` |
| sanitized | 是（无密钥/账号/Cookie） |

## 1. 落点（3 个新文件，零既有文件编辑）

| 文件 | 作用 |
|---|---|
| `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/TutorVisualPreviewAssembly.kt` | 生产注入点：`TutorVisualPreviewInjection`（默认 `DISABLED`）、`TutorVisualPreviewAssembly.inject(request, snapshot)`、`TutorVisualPreviewInjectionResult`（快照 + 有界诊断） |
| `core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/visual/TutorVisualPreviewAssemblyTest.kt` | JVM：默认关闭零渲染、注入、降级/取消、冻结帧校验、广告面锁、JVM 端到端（真实仓储命令） |
| `core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/visual/TutorVisualPreviewAssemblyInstrumentedTest.kt` | 设备：真实渲染链 + 真实仓储的 ON/OFF/拒绝三态 |

`availableMethods` / 能力 / 提示词文件**零改动**（`git diff --stat` 对 `FigureGeneration.kt`、`FigurePromptAssembler.kt`、`RoomModelTaskRepository.kt`、`ConfiguredCleanImageGenerator.kt`、`AttachedImageGenerator.kt`、`TutorFigureGenerator.kt` 为空）。

## 2. 判据逐项

| 项 | 要求 | 落码/用例 | 状态 |
|---|---|---|---|
| 56 | 注入点默认关闭（现有默认不变）+ 显式开关 | `TutorVisualPreviewInjection.DISABLED` 为构造默认值；关闭时 `inject` 立即返回同一 `snapshot`（`previewAssetId` 原样）、零渲染器调用、零 vault 写。用例 `the injection point is off by default and never touches the renderer`；设备 `switchOffCommitsWithoutAPreviewOrAnyPreviewAssetWrite` | 落码；执行 UNVERIFIED |
| 57 | 开关打开端到端（JVM/设备各一） | JVM 成功腿：`the injected preview id reaches the real commit command`（装配 → 真实 `TutorVisualCommitRepository` → 端口收到 `CommitTutorTurnWithVisualCommand.snapshot.previewAssetId`；渲染端口用假件，JVM 无 Canvas）。JVM 降级腿：`an enabled real renderer degrades end to end at the commit command`（**真实渲染器**准入拒绝 → 真实仓储命令 `previewAssetId=null`、vault 写入口零触碰）。设备：`switchOnRendersThroughTheProductionChainAndCommitsThePreviewId`（真 Canvas/Bitmap → PNG → canonical vault → 资产行 → 真仓储 → 读回快照行） | 落码；执行 UNVERIFIED |
| 58 | 失败降级 `previewAssetId=null` 断言 | 拒绝分支显式 `copy(previewAssetId = null)`（不继承旧 id）；端口抛异常/OOM 同样置 null + `PREVIEW_INJECTION_FAILED` 有界诊断；`CancellationException` 重抛。JVM 用例四条（拒绝 / 抛异常 / OOM 兜底 / 取消重抛）+ 设备拒绝例（4096² 真实准入拒绝、提交仍 LIVE、零预览资产行） | 落码；执行 UNVERIFIED |
| 59 | 开关不放广告面（`availableMethods` 不变） | 开关是**显式实参**、不接触能力/提示词；用例 `the preview switch never advertises JS or changes available methods` 钉 `IMAGE_MODEL_ONLY.availableMethods == {IMAGE_MODEL}` 且提示词不出现 `QuickJS`/`sourceJs`；能力文件 `git diff` 为空 | 落码 + 静态核对；执行 UNVERIFIED |
| 60 | 登记"MVA 未过前默认关闭" | 准入清单 D-04 行追加 **批次 42 WS7 注记**（`docs/research/2026-10-26-batch-24-4e2-admission-checklist.md` D-04 行），写明 MVA（§1.4）通过前生产必须保持默认关闭、本批不打开任何生产调用点 | 已登记 |

## 3. 关键语义（与 R1 对齐）

1. **默认态 = 批次 39 之前的逐位行为**：`previewAssetId` 原样保留（生产恒 null），不分配位图、不写 vault、不登记资产行。
2. **失败统一出口**：装配边界一个 catch 点；拒绝/异常/OOM → `previewAssetId = null` + 有界诊断（≤ `FigureGenerationOutcome.MAX_DIAGNOSTICS_CHARS`）；取消重抛。快照提交**永不**因预览失败而失败（R1 §2.3）。
3. **冻结帧一致**：开关打开时 `request` 的视口宽高与 `fontScaleMilli` 必须与 `snapshot` 逐值相等，否则 `IllegalArgumentException`（先于渲染；R1 §2.1"同帧"）。
4. **写入点**：只允许在 `commitAssistantTurnWithVisual` 路径调用，不在 `stageSnapshot`（R1 §2.2）。
5. **R-04**：生产 commit 接通前本类只被测试引用；release 轨 keep 义务与 `VisualChainTestSeams` 一并在 `app/r04-test-keep.pro` 管理（R1 §2.7），涉及本类的 release 轨设备例不得进 R-04 清单。

## 4. 执行状态（本轮如实）

- 本轮**未运行 Gradle、未执行 adb/设备**（批次纪律：Gradle 只在门禁阶段串行运行）。上面所有 JVM/设备用例均为 **UNVERIFIED**；不得把"用例在树"读成通过。
- 门禁阶段应执行：
  - JVM（最小相关）：`:core:data:testDebugUnitTest --tests "com.tingyun.smartmistakebook.core.data.visual.TutorVisualPreviewAssemblyTest"`
  - 设备：`assembleLocalFirstDebugAndroidTest` → `adb install -r -t` → `am instrument -e class com.tingyun.smartmistakebook.core.data.visual.TutorVisualPreviewAssemblyInstrumentedTest`（**禁用** `:app:connectedLocalFirstDebugAndroidTest`）。
- 静态核对（本轮实跑）：能力/提示词文件 `git diff --stat` 为空；工作树仅新增 3 个文件。

## 5. 阻塞：Application / 生产调用点接线（BLOCKED）

**结论：本批不能打开生产注入点，只出 patch 草案。**

阻塞理由（逐条可核）：

1. **热区禁改**：批次 42 纪律明列 `Application/Root` 禁改；`SmartMistakeBookApplication.kt` 当前虽非他线 M，仍属禁改面。
2. **生产调用点不存在**：`commitAssistantTurnWithVisual` 在 `app/src/main`、`feature/*`、`core/*/src/main` 排除定义/KDoc 后**零调用方**（本轮 grep 实测）；视觉链生产装配仍是三参（`SmartMistakeBookApplication.kt:363-369`，`visualEngine` 未注入），`previewAssetId` 生产恒 null。
3. **打开会翻转广告面**：批次 39 决策台账 `docs/agent-first-refactor-decisions-2026-09-23.md:3729` 明确"接 `visualEngine` 会翻转 JS 能力广告"；MVA 未过前 `availableMethods` 必须零改动（本批红线）。

### 5.1 patch 草案（未应用，不可直接 `git apply`）

草案分两处，必须**同批**落地（只加装配字段而无 commit 调用点会成为被 R8 收缩的死代码）：

```diff
--- a/app/src/main/kotlin/com/tingyun/smartmistakebook/SmartMistakeBookApplication.kt
+++ b/app/src/main/kotlin/com/tingyun/smartmistakebook/SmartMistakeBookApplication.kt
@@
+import com.tingyun.smartmistakebook.core.data.visual.TutorVisualPreviewAssembly
+import com.tingyun.smartmistakebook.core.data.visual.TutorVisualPreviewRendererFactory
@@
+    /**
+     * D-04 生产注入点。**MVA（准入清单 §1.4）通过前不得传 ENABLED**；默认实参即关闭态，
+     * 与批次 39 之前逐位相同（previewAssetId 恒 null、零渲染/零写）。
+     */
+    private val tutorVisualPreviewAssembly: TutorVisualPreviewAssembly by lazy {
+        TutorVisualPreviewAssembly(
+            renderer = TutorVisualPreviewRendererFactory.forProduction(this, database),
+            // injection = TutorVisualPreviewInjection.ENABLED 仅由主会话在 MVA 判据满足后开启
+        )
+    }
```

未来 commit 调用点（当前不存在；`feature/tutor` 热区，归装配批）：

```kotlin
val prepared = visualPreviewAssembly.inject(
    request = TutorVisualPreviewRequest(
        rendered = executed,                       // 引擎本次执行产物
        viewportWidthDu = frame.viewport.widthDu,  // 与快照同一冻结帧
        viewportHeightDu = frame.viewport.heightDu,
        fontScaleMilli = frame.fontScaleMilli,
        createdAtEpochMillis = now,                // 与仓储 clock() 同源（R1 §2.2-3）
        labels = previewLabels,
        // measuredEntries 通道甲未接线前保持 null（合并语义如实登记，R1 §2.5）
    ),
    snapshot = snapshot,
)
repository.commitAssistantTurnWithVisual(
    message = message,
    program = program,
    snapshot = prepared.snapshot,                  // 唯一被提交的快照输入
    ownerRequestId = ownerRequestId,
)
prepared.diagnostic?.let { diagnostics.record(it) } // 有界诊断进诊断面，不进显示
```

### 5.2 解除阻塞的 runbook

1. 主会话裁定：视觉链生产装配批开工（`visualEngine` 进 `FigureGenerationCoordinationFactory`）且 MVA 判据逐条满足（准入清单 §1.4 + MVA 设备用例）。
2. 落地 §5.1 两处（装配字段 + commit 调用点），`injection` 仍保持默认 `DISABLED`；门禁跑 JVM + 设备两套。
3. 单独一次提交把 `injection = ENABLED` 打开（显式、可回滚、与 `availableMethods` 零改动同批判据）；打开前后各跑一遍设备 ON/OFF 用例，绑定 APK sha 与源快照。
4. 回滚：把该实参改回默认（删一行）即回到批次 39 行为；无需迁移、无 schema 影响（引用列与 FK 已在）。

## 6. 未验证项

| # | 项 | 状态 |
|---|---|---|
| U1 | JVM 用例实际编译/执行 | **UNVERIFIED**（本轮零 Gradle） |
| U2 | 设备用例实际编译/执行（真 Canvas/vault/FK） | **UNVERIFIED**（本轮零 adb） |
| U3 | 生产 commit 调用点接线与 `ENABLED` 打开 | **BLOCKED**（§5） |
| U4 | `measuredEntries` 通道甲合并语义 | 未接线（R1 §2.5 登记；当前 `null` = 预览独判） |
| U5 | release 轨 R8 keep 形状（预览装配 + 缝） | 未核（随 R-04 批） |
