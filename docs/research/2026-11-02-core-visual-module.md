# `core:visual` 模块化研究（批次 40 R7；S-06 / X-01 草案）

> 日期：2026-11-02。范围：把屏/PDF painter 与真实内容度量从 `core:data`/`app` 抽到 `core:visual`，给出依赖图、可见性、test seam、`settings.gradle.kts` 热区下的落地顺序与回滚，并给出 GitHub 参考评分。
>
> 方法：本会话只读实读源码、构建文件、契约、准入清单、V-01 seam 设计与 CI 配置；GitHub 元数据用 `gh api` 实取；**未运行 Gradle、未跑任何测试/设备、未做 git 写操作**。所有构建/测试/设备结论均为 **UNVERIFIED**，文中只给可复跑命令与判据。

---

## 0. 结论摘要

**推荐方案 M2：新建一个 `core:visual` Android library，承接完整的 DrawOps 协议 + 屏/PDF 回放 + 真实内容度量；`core:data` 只保留引擎、持久化、恢复与预览编排。**

关键决定：

1. **移动范围**：除屏/PDF painter 与度量外，一并移动 DrawOps DTO/parser/validator/duplicate-keys。理由不是"顺手"，而是 S-06 的验收形态要求 `core:ui`/`core:export` **只依赖 `core:visual` 回放**（契约 §5 `:112`）；若 parser 留在 `core:data`，`core:export` 仍必须依赖 `core:data` 才能把持久化 JSON 变回文档，模块边界无法闭合。
2. **依赖方向**：`core:visual` 只依赖 `:core:domain`（`api`）+ `:core:model`（`implementation`）+ 既有 catalog 库；**禁止**依赖 `:core:data`、`:core:database`、`:core:ui`、`:core:export`、`:app`、`:feature:*`。`core:data` 依赖 `core:visual`（单向；不是 visual 依赖 data；无环）。
3. **可见性**：跨模块消费面提升为 `public`（Document/Op/enums/LabelContent/Validation/Parser/Validator/Painter/PDF/异常与码/Artifact/Measurer 入口）；纯计算 helper、环境/后端、PDF 纯层、`PdfWriteIntegrity` 保持 `internal`。同模块 `src/test` 与 `src/androidTest` 是 friend path，继续直接测 `internal`，这是主要 test seam。
4. **包名**：第一阶段**保留** `com.tingyun.smartmistakebook.core.data.visual`（模块 namespace 用 `com.tingyun.smartmistakebook.core.visual`），把改动压到最小；仓库有 **95 个 Kotlin 文件**引用该包名（本会话 `grep -rl` 实测），热区在飞时改包名会把机械 diff 混进语义 diff。包名重命名单列为后续静默窗口的机械批，不作为 S-06 关闭条件。
5. **V-01 seam**：`VisualChainTestSeams` 原样保留。painter/PDF 半边变成 `core:visual` 公共 API 后，seam 的 PDF 函数可以退化为薄转调；`restoreDecision` 半边仍因 `core:data` internal restore 而必须存在。**不删 seam、不改 V-01 用例签名**。
6. **`settings.gradle.kts`**：只加一条**独立** `include(":core:visual")`（不碰现有 `include(...)` 块的逗号/排序），把冲突面压到一行；四段补丁（P0–P4）串行落地，每段可独立回滚。
7. **X-01 不混同**：X-01 是 `core:visual-js`（Service/client/AIDL），不是渲染模块。`core:visual` 先落；`core:visual-js` 另开同一热区窗口，两者互不依赖，回滚独立。
8. **GitHub 参考**：`android/nowinandroid` 25/25（模块类型、`api`/`implementation`、独立 `-test` 模块的教科书样例）；`autonomousapps/dependency-analysis-gradle-plugin` 22/25（依赖图/配置错误检查，作为边界门清单，不引入插件）；`android/gradle-recipes` testFixtures 21/25（只示范 unit test，且 repo 级 license 未声明）；`vmadalin/android-modular-architecture` 12/25（2021 停更，拒绝作模板）。

**S-06 状态**：本文件只交付模块化设计与落地判据；模块真正存在、构建/设备绿、`core:ui`/`core:export` 接线完成前，S-06 仍为"部分落"，不得写成"通过"。**X-01 仍阻塞**（app/AIDL/Service 热区），本文件只定义边界与顺序。

---

## 1. 现状、约束与本次实测

### 1.1 任务定义（准入清单原文）

- **S-06**：`core:visual` 模块 + `AndroidScenePainter` 共用 Compose/PDF。现状"部分落"：模块不存在、`AndroidScenePainter` 零命中；过渡落点三件已在树（真实度量、屏 painter、PDF painter）。证据位置 `docs/research/2026-10-26-batch-24-4e2-admission-checklist.md:149`。
- **X-01**：开放 JS 前归位——Service/client/AIDL → `core:visual-js`；app 的 `AndroidVisualExecutionEngine` 组合三层；端口/wire 不变。现状"未落"。证据位置同上 `:242`。
- 契约 §5：`core:visual` 的 `AndroidScenePainter` 共用于 Compose `nativeCanvas` 和 PDF `page.canvas`；`core:ui`/`core:export` 只依赖 `core:visual` 回放，不依赖 JS worker。`docs/current/visual-generation-contract.md:110-112`。
- 契约 §5.1 现状表明确记：`core:visual` 不存在、`AndroidScenePainter` 零命中、painter 落 `core:data` 过渡态；归位条件 = 热区空出。`docs/current/visual-generation-contract.md:123-127`。

### 1.2 本会话实测（只读）

| 项 | 实测 | 命令/证据 |
|---|---|---|
| HEAD | `a3f367df` | `git rev-parse --short HEAD` |
| `settings.gradle.kts` | **干净**；sha256 `46b3c37c49733f2566772dba3c04dc45fb9c69fd3a7bd96aca96fede31e067f7` | `git status --porcelain -- settings.gradle.kts`（无输出）；`sha256sum` |
| 模块清单 | 无 `:core:visual`；现有 `:core:model/domain/database/data/export/ui/markdown` + 5 feature + app + benchmark | `settings.gradle.kts:18-34` |
| 相关代码是否脏 | `core/data/.../visual`、`app/.../visualjs`、`core/data/build.gradle.kts`、`app/build.gradle.kts` 在 targeted `git status` 中无输出 | `git status --porcelain -- <those paths>` |
| 视觉 seam 门 | **PASS**：0 main/test 引用、1 个 androidTest 引用 | `python tools/ci/check_visual_test_seams.py` → `OK…`，`EXIT=0` |
| 锁文件门 | **PASS（尚未落地语义）**：无已跟踪锁；工作区 13 个未跟踪锁；缺锁 project 仅 `:benchmark` | `python tools/ci/check_lockfiles.py` → `OK…`，`EXIT=0` |
| 构建/测试 | **未运行** | 本轨纪律（零 Gradle/零设备） |

> 与批次 40 基线 `docs/research/2026-11-02-batch-40-baseline.md` §2 的差异：基线记 core/data 47 M、core/database 21 M；本会话没有重跑全量 `git status`，只对上面列出的本任务相关路径做了 targeted 检查。热区仍按"在飞"处理，不因 targeted 干净就认为可以随意改。

### 1.3 当前代码分布（实读行数）

| 文件 | 行数 | 现状声明 | 角色 |
|---|---:|---|---|
| `core/data/.../visual/VisualDrawOps.kt` | 192 | `internal` 顶层 DTO；`:13-14` KDoc 明说"4E.2 落 `core:visual` 回放时按需提升可见性" | 协议类型 |
| `core/data/.../visual/VisualDrawOpsParser.kt` | 969 | `internal object`（`:50`） | JSON→DTO + 复验 |
| `core/data/.../visual/VisualDrawOpsValidator.kt` | 41 | `internal object`（`:29`），委托 parser | 引擎提交门 |
| `core/data/.../visual/VisualDrawOpsDuplicateKeys.kt` | 229 | 无 Android import | 重复键检测 |
| `core/data/.../visual/VisualDrawOpsPainter.kt` | 334 | `internal class`（`:44`） | 屏 Canvas 回放 |
| `core/data/.../visual/VisualDrawOpsPainterPure.kt` | 228 | 零 Android import；`:87` `REPLAY_TEXT_PAINT_FLAGS`、`:194` `VisualLabelContent` | 纯计算层 |
| `core/data/.../visual/VisualDrawOpsPainterRendered.kt` | 38 | `internal` 扩展 + `PAINTER_DRAW_COMMANDS` | `Rendered`→painter 衔接 |
| `core/data/.../visual/VisualDrawOpsPdfPainter.kt` | 258 | `internal class`（`:55`），`:57` 复用同一 painter | PDF 页画布 |
| `core/data/.../visual/VisualDrawOpsPdfPainterPure.kt` | 92 | 零 Android import | 页几何纯层 |
| `core/data/.../visual/PdfWriteIntegrity.kt` | 214 | 纯 JVM/IO | 写成功 D1/D2/D3 |
| `core/data/.../visual/VisualMeasuredLayoutArtifact.kt` | 130 | `data class` public（`:39`） | 度量 artifact |
| `app/.../visualjs/VisualContentMeasuring.kt` | 661 | `internal class AndroidVisualContentMeasurer`（`:54`）；`:643` 服务装配入口 | 真实度量 |

合计主源 **3,386 行**（本会话 `wc -l` 相加）。测试规模见 §3.3。

### 1.4 为什么不能只搬 painter

1. **契约方向**：`core:ui`/`core:export` 只依赖 `core:visual`。`core:export` 若要在 PDF 上复用同一 painter，必须自己能 `parse` 持久化的 `drawCommandsJson`；parser 留在 `core:data` 就会把 `core:export → core:data` 变成硬依赖，违反契约。
2. **类型方向**：painter 的输入是 `VisualDrawOpsDocument`。若 DTO 留在 `core:data`，`core:visual` 必须依赖 `core:data`，形成反向依赖，且 `core:data` 又需要 painter（预览/恢复），必然成环。
3. **S-06 验收**：清单要求的是"模块 + `AndroidScenePainter` 共用 Compose/PDF"，不是"painter 换个目录"。协议、回放、度量同属一个视觉层，才有无环边界。

---

## 2. 模块划分

### 2.1 方案比较

| 方案 | 内容 | 主源/测试 | 优点 | 缺点 | 结论 |
|---|---|---:|---|---|---|
| **M1 最小搬** | DTO + painter/PDF + artifact + 度量；parser/validator 留 `core:data` | ~2,109 行 / 74 JVM + 58 设备 | 改动小、先解 painter 跨模块可达 | `core:export` 仍依赖 `core:data`；S-06 无法闭合；`core:data` 仍持协议解析 | 不推荐 |
| **M2 完整视觉层（推荐）** | M1 + parser/validator/duplicate-keys/`drawRendered` 一并下沉 | ~3,386 行 / 135 JVM + 58 设备 | 无环；`core:ui/export` 只依赖 visual；S-06 的模块边界可闭合 | 一次 diff 大；parser 969 行接近 1000 行硬门 | **推荐，分段落地** |
| **M3 拆纯/Android 两模块** | `core:visual-protocol`（JVM）+ `core:visual`（Android） | 同 M2 + 一条 settings | 纯层可 JVM-only 消费 | 当前没有 JVM-only 消费者；多一个模块/锁/CI 条目，未消灭已发生失败 | 拒绝（触发条件后再议） |

**M2 的"新增"指认**：它消灭的具体失败是——
- `core:export`/`core:ui` 为拿 painter 被迫依赖 `core:data`（契约禁止的边）；
- app androidTest 为测 painter/PDF 只能经 `VisualChainTestSeams`，而 seam 的 PDF 半边本质是在绕过"internal 跨模块不可达"（V-01 设计 `docs/research/2026-11-01-v01-pdf-leg-exposure-design.md:9`）；
- `core:data` 同时承担"数据/持久化"与"Android 绘制协议"，任何一侧改动都要在同一个大模块里回归。

### 2.2 移动/保留清单

**移入 `core:visual`（主源）**

- `VisualDrawOps.kt`（DTO/Op/enums/Validation）
- `VisualDrawOpsParser.kt`、`VisualDrawOpsValidator.kt`、`VisualDrawOpsDuplicateKeys.kt`
- `VisualDrawOpsPainter.kt`、`VisualDrawOpsPainterPure.kt`、`VisualDrawOpsPainterRendered.kt`
- `VisualDrawOpsPdfPainter.kt`、`VisualDrawOpsPdfPainterPure.kt`、`PdfWriteIntegrity.kt`
- `VisualMeasuredLayoutArtifact.kt`
- `app/.../visualjs/VisualContentMeasuring.kt`（移动到 `core:visual`；包名从 `...visualjs` 改为 `...core.data.visual`，app 侧只多一行 import）

**留在 `core:data`（并说明为什么）**

| 文件/面 | 留的理由 |
|---|---|
| `QuickJsVisualExecutionEngine.kt` | JS 引擎/进程边界，属执行编排；契约 §5.1 已登记"引擎落 core:data"的既有偏差，本批不改 |
| `VisualSnapshotRestore*`、`VisualCheckpointRetention.kt` | 依赖 DB 行/恢复策略；属持久化层 |
| `TutorVisualPreviewRenderer*`、`TutorVisualPreviewAssetPersister*` | 预览编排 + 资产行/DB；只调用 `core:visual` 公共 painter |
| `VisualChainTestSeams.kt` | restore 半边依赖 `core:data` internal；V-01 兼容面 |
| `VisualLayoutBudget.kt`、`VisualFrameInputs.kt`、`VisualRendererVersion.kt` | 引擎/预算/版本协商，非绘制实现；移动会扩大本批而不增加边界收益 |
| `VisualJsFailureClassification.kt` | 引擎失败分类，属执行层 |

**测试移入 `core:visual`**：见 §3.3。`VisualFigureFixtureOracleTest`、`QuickJs*`、`V01VisualChainInstrumentedTest`（core:data 降级变体）、`TutorVisual*` 保留在 `core:data`；app 的 `V01VisualChainInstrumentedTest` 保留在 app（seam 消费方）。

### 2.3 目标依赖图

```text
:app
 ├─ implementation :core:visual          (度量入口 / painter 公共 API)
 ├─ implementation :core:data            (引擎/持久化/协调器)
 ├─ implementation :core:ui              (现有；未来加 :core:visual)
 └─ implementation :core:export          (现有；未来加 :core:visual)

:core:data
 ├─ api            :core:visual          (公开的 TutorVisualPreviewRequest 暴露 VisualMeasuredLayoutArtifact)
 ├─ implementation :core:database
 ├─ implementation :core:domain
 └─ implementation :core:model

:core:visual
 ├─ api            :core:domain          (VisualContentMeasurer / VisualViewport / VisualExecutionResult / VisualContentKind)
 ├─ implementation :core:model           (MathBox / FigureGenerationOutcome / figureUsageUtf8Bytes)
 ├─ implementation androidx.core.ktx     (ConfigurationCompat)
 └─ implementation kotlinx.serialization.json (parser)

:core:ui / :core:export  ---X-->  :core:data     (契约禁止；现状本就 0 引用)
:core:visual             ---X-->  :core:data / :core:database / :core:ui / :core:export / :app / :feature:*
```

无环论证：`core:visual → core:domain → core:model`；`core:data → core:visual`；`core:database` 不依赖 visual；`core:ui`/`core:export` 未来只依赖 visual。`core:visual` 没有任何指回 `core:data` 的边。

> 既有不一致（不在本批扩大）：`core:data` 现在用 `implementation(project(":core:domain"))`，但公开的 `TutorVisualPreviewRequest` 已暴露 `VisualExecutionResult.Rendered` / `VisualMeasuredContent` 等 domain 类型。按 `api`/`implementation` 语义这本应是 `api`；app 目前直接依赖 domain 掩盖了它。本批只把新增的 visual 边声明为 `api`，不顺手改 domain 边（避免把无关修复混进抽取批）；若未来启用 DAGP，这条会先被报出来。

### 2.4 `core:visual/build.gradle.kts` 草案

> 全部使用既有 catalog；不新增外部坐标、不新增 Gradle 插件版本。`kotlin.serialization` 与 `core:data` 同款接线。

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.tingyun.smartmistakebook.core.visual"
    compileSdk = 37

    defaultConfig {
        minSdk = 23
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    // 公共签名暴露 core:domain 类型（VisualContentMeasurer / VisualViewport / Rendered），必须 api。
    api(project(":core:domain"))
    implementation(project(":core:model"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    // catalog 无 coroutines-core alias；与 core:domain 同版本字面（1.11.0）。
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.core.ktx)
    androidTestImplementation(libs.kotlinx.coroutines.android)
}
```

注意：
- **不要**在 `core:visual` 里加 `coreLibraryDesugaring`、LiteRT、QuickJS、Room、OkHttp；这些属 `core:data`/app。
- `VisualContentMeasuring.kt` 的 `Paint`/`Resources` 只在 Android 目标；JVM 单测用 fake backend，不需要 Robolectric（沿用现有 seam 理由）。
- `testOptions.unitTests.isReturnDefaultValues = true` 与 `core:data` 同款，防止未来 JVM 测试误触 android 默认实现；当前纯层测试不依赖它。

### 2.5 可见性矩阵

Kotlin `internal` 是**模块级**，移动文件后 `core:data`/app 立即失去访问。逐符号：

| 符号 | 现在 | 目标 | 消费方 / 理由 |
|---|---|---|---|
| `VisualDrawOpsDocument` / `VisualDrawOp` / path/enums / `VisualDrawHitRegion` / `VisualDrawOpsStats` / `VisualDrawOpsViewBox` | `internal` | **public** | parser/painter 的公共载荷；`core:data`、`core:export`、测试夹具 |
| `VisualDrawOpsValidation`（Valid/Invalid） | `internal` | **public** | parser 返回类型；引擎/导出按码分流 |
| `VisualDrawOpsParser` | `internal` | **public** | `core:data` 引擎/restore、`core:export` 重校验 |
| `VisualDrawOpsValidator` | `internal` | **public** | 引擎提交门 |
| `VisualDrawOpsPaintingException` + `paintingFailure` | `internal` | **public** | 跨模块 catch/构造（`TutorVisualPreviewRenderer`、`VisualChainTestSeams`） |
| `PAINTER_*` 码常量（含 `PAINTER_DRAW_COMMANDS`） | `internal` | **public** | 调用方按码分类；测试断言 |
| `REPLAY_TEXT_PAINT_FLAGS` | `internal` | **public** | 度量/回放跨侧钉（KD-78 ④） |
| `VisualDrawOpsPainter` | `internal` | **public**，建议改名 `AndroidScenePainter` | S-06 名实对齐；Compose/PDF 共用入口 |
| `VisualDrawOpsPdfPainter` | `internal` | **public** | `core:export` PDF 腿；V-01 seam 薄转调 |
| `drawRendered` / `PAINTER_DRAW_COMMANDS` | `internal` | **public** | `core:data` V-01 降级变体、预览复检 |
| `VisualMeasuredLayoutArtifact` | public | public（不变） | core:data 预览请求/回放 |
| `AndroidVisualContentMeasurer` | `internal` | **public** | app 服务构造 |
| `visualContentMeasurerForService(resources)` | `internal` | **public** | app 唯一装配入口 |
| `VisualMeasureEnvironment` / `VisualTextMeasureBackend` / `PaintVisualTextMeasureBackend` | `internal` | **internal** | 同模块 JVM 测试 seam；外部不需要注入 |
| `VisualMeasurementEnvironmentException` | `internal` | **internal** | 外部按 `Throwable`/有界诊断处理；不扩类型面 |
| `VisualDrawOpsDuplicateKeys` / pure helpers / PDF pure / `PdfWriteIntegrity` / `VisualMeasuredLayout` | `internal` | **internal** | 同模块测试直接测；跨模块无消费 |

**改名决定**：S-06 的验收字面是 `AndroidScenePainter`。建议在同一次移动中把 `VisualDrawOpsPainter` 改名为 `AndroidScenePainter`（行为零变化），`VisualDrawOpsPdfPainter` 内部继续持有它。若主会话判定"名称非验收项"，可保留原名；但 `grep "class AndroidScenePainter"` 仍为 0，S-06 的名实对齐会继续欠账。**不建 wrapper**：wrapper 会多一层无独立失败要消灭的抽象。

### 2.6 包名：第一阶段保留，后续机械重命名

- 第一阶段：协议/painter/PDF/artifact 文件的 `package` 保持 `com.tingyun.smartmistakebook.core.data.visual`；模块 `namespace` 用 `com.tingyun.smartmistakebook.core.visual`。Kotlin 包可跨模块，`internal` 仍按模块隔离；好处是 **95 个引用文件零 import 改动**，把热区冲突面压到最小。唯一例外是 `VisualContentMeasuring.kt`：它现在在 app 的 `com.tingyun.smartmistakebook.visualjs` 包，移入后改为 `com.tingyun.smartmistakebook.core.data.visual`（app 只多一行 import）。
- 第二阶段（静默窗口）：`core.data.visual` → `core.visual` 的机械重命名，单独提交，不混语义改动。触发条件：`settings` 无他线在飞、`core/data` visual 文件干净、构建/设备绿。**不把包名重命名作为 S-06 关闭条件**。
- 若有人担心"包名说 data 但模块是 visual"：在 `core/visual` 源根放一个 `README.md`/包级 KDoc 注明过渡态；不要为此在第一批改动 95 个文件。

### 2.7 S-06 验收映射（本批之后仍缺什么）

| S-06 要素 | 本设计覆盖 | 仍需 |
|---|---|---|
| `core:visual` 模块存在 | 模块 + settings 一行 | 实际落地/构建绿 |
| `AndroidScenePainter` | 改名后的公共 painter | `grep "class AndroidScenePainter"` 非 0 |
| Compose 共用 | painter 接收 `Canvas`；Compose `nativeCanvas` 可直接传 | `core:ui` 接线（热区，另批） |
| PDF 共用 | `VisualDrawOpsPdfPainter` 复用同一 painter `draw` | `core:export` 接线（热区，另批） |
| 屏/PDF 结构一致 | 现有 parity 原型门随测试移入 visual | 设备绿轮重跑；不把原型门当产品门 |

---

## 3. Test seam 设计

### 3.1 现有 V-01 seam（必须保留）

`VisualChainTestSeams`（`core/data/.../visual/VisualChainTestSeams.kt:37`）是 V-01 ④ PDF/restore 腿的公开测试缝：
- `restoreDecision(...)`（`:51`）：入参 `TutorVisualSnapshotRecord`（core:database 公共读侧），转调 `core:data` internal `VisualSnapshotRestore`；
- `writeSnapshotPdf(...)`（`:94`）：解析 `drawCommandsJson` → `VisualDrawOpsPdfPainter.writeFile`，D1∧D2∧D3 完整性由 painter 强制；
- 三条硬约束（KDoc `:18-35`）：仅 androidTest 引用、R-04 release 需 keep、不透出 internal 类型。
- 门：`tools/ci/check_visual_test_seams.py` 断言 **0 main/test 引用 + ≥1 androidTest 引用**；本会话基线实跑 `OK`（见 §1.2）。

### 3.2 模块化后的三层 seam

| 层 | 机制 | 用途 | 是否新增 |
|---|---|---|---|
| **同模块 test seam** | `src/test` / `src/androidTest` 对 main `internal` 的 friend path | 纯层、painter、PDF、度量后端测试（现有 193 例的主体） | 否（随文件移动） |
| **跨模块生产 API** | `core:visual` public 的 `AndroidScenePainter` / `VisualDrawOpsPdfPainter` / parser / measurer | `core:data`、`core:ui`、`core:export`、app 正常消费 | 是（可见性提升，不是新机制） |
| **跨模块测试缝** | `VisualChainTestSeams` 保留；PDF 半边可薄转调 public painter | V-01 ④ restore→PDF 全腿；生产旁路门 | 否（原样保留） |

**不采用 AGP `testFixtures`**：
- 官方 `android/gradle-recipes` 的 testFixtures recipe 只示范 `testImplementation(testFixtures(project(":lib")))`，未示范 `androidTest` 消费；本仓 V-01 设计已把它标为 **UNVERIFIED**（`docs/research/2026-11-01-v01-pdf-leg-exposure-design.md:36`，U1 `:103`）。
- 本仓当前构建文件零 `testFixtures`、零 `@VisibleForTesting`（同文档 `:25` 实读结论）。
- 采用它会同时改 `core/visual` 与 app 的 build 文件，并引入 AGP/Kotlin friend-path 不确定性；对当前失败没有增量收益。

**不建 `core:visual-test` 模块（现阶段）**：NIA 的 `core:data-test`（`api(projects.core.data)`）是可选先例，但本仓跨模块测试夹具目前只有 V-01 seam 一处；新增模块不消灭已发生的失败。触发条件（满足其一再建）：
1. `core:data`、`core:ui`、`app` 三个以上模块需要同一套 DrawOps/度量夹具；
2. 手建文档在 ≥2 个模块重复且已出现漂移；
3. 需要把夹具作为独立 AAR 发布/复用。

### 3.3 测试迁移矩阵（本会话 `grep -c '@Test'` 实测）

**JVM（移入 `core/visual/src/test`，共 135）**

| 源文件 | @Test |
|---|---:|
| `core/data/.../visual/VisualDrawOpsParserTest.kt` | 26 |
| `core/data/.../visual/VisualDrawOpsValidatorTest.kt` | 35 |
| `core/data/.../visual/VisualDrawOpsPainterPureTest.kt` | 13 |
| `core/data/.../visual/VisualDrawOpsPdfPainterPureTest.kt` | 6 |
| `core/data/.../visual/PdfWriteIntegrityTest.kt` | 16 |
| `core/data/.../visual/VisualMeasuredLayoutArtifactTest.kt` | 9 |
| `app/.../visualjs/VisualContentMeasuringTest.kt` | 16 |
| `app/.../visualjs/VisualMeasuredLayoutInkBoundsTest.kt` | 9 |
| `app/.../visualjs/VisualMeasureEnvironmentBudgetTest.kt` | 5 |

**设备（移入 `core/visual/src/androidTest`，共 58）**

| 源文件 | @Test |
|---|---:|
| `core/data/.../visual/VisualDrawOpsPainterInstrumentedTest.kt` | 24 |
| `core/data/.../visual/VisualDrawOpsPdfPainterInstrumentedTest.kt` | 16 |
| `core/data/.../visual/VisualDrawOpsParityInstrumentedTest.kt` | 16 |
| `core/data/.../visual/VisualDrawOpsParityMetrics.kt` | 0（helper，随套件移动） |
| `app/.../visualjs/VisualMeasureReplayPaintFlagsInstrumentedTest.kt` | 2 |

**保留原位**：`core:data` 的 `V01VisualChainInstrumentedTest`、`TutorVisualRebuildRestoreInstrumentedTest`、`TutorVisualPreviewRendererInstrumentedTest`、`VisualFigureFixtureOracleTest`、`QuickJs*`；app 的 `V01VisualChainInstrumentedTest`、`VisualJs*`。

> 设备绿绑定源快照（含 core/model）必须重跑；新增模块的 58 例不是"旧套件换目录"，而是新的 connected 任务，旧绿不能替代。

### 3.4 新增边界门（建议 `tools/ci/check_visual_module_boundaries.py`）

**它消灭的具体失败**：模块看似抽出来了，但 `core:visual` 悄悄 `implementation(project(":core:data"))`，或源码 import `com.tingyun.smartmistakebook.core.data`——依赖图反向，`core:ui/export` 仍无法独立，回滚时也无法判断哪条边是新的。

最小判据（只读、零 Gradle）：
1. `core/visual/build.gradle.kts` 不得出现 `:core:data`、`:core:database`、`:core:ui`、`:core:export`、`:app`、`:feature:`;
2. `core/visual/src/main` 不得 import `com.tingyun.smartmistakebook.core.data`、`core.database`、`core.ui`、`core.export`、`visualjs`;
3. `core/ui`、`core/export` 不得出现 `project(":core:data")`；
4. `settings.gradle.kts` 必须有且仅有一个 `:core:visual` include。
5. 退出码 0/1，仿 `check_visual_test_seams.py` 的负控位置参数（替换扫描根）。

**不引入 DAGP 插件**：`autonomousapps/dependency-analysis-gradle-plugin` 能更全面地查 unused/wrong-configuration，但引入它会改根 `build.gradle.kts`/`settings.gradle.kts`、新增外部坐标与锁状态，正好落在热区；当前一个模块的方向检查用 60 行 Python 就够。DAGP 作为"检查清单"参考（见 §5），等模块稳定后再议。

### 3.5 与 V-01 seam 的关系（逐条）

| 问题 | 结论 |
|---|---|
| seam 需要删吗？ | **不删**。`restoreDecision` 仍依赖 `core:data` internal `VisualSnapshotRestore`；删了 V-01 ④ 立刻失去 restore 腿。 |
| `writeSnapshotPdf` 还留吗？ | **留**，签名不变；实现改为解析（public parser）+ `VisualDrawOpsPdfPainter.writeFile`（public）。V-01 用例零改动。 |
| painter 公共化后，app 测试能否绕过 seam？ | 可以，但**只对纯 painter/PDF 测试**。V-01 的"restore→PDF 全腿"仍应走 seam，确保冷恢复判定序/解析复检/完整性不被测试旁路。 |
| seam 门要改吗？ | **不改** `check_visual_test_seams.py` 的声明路径与 0-main 引用规则；新增边界门单独跑。 |
| R-04 keep 要改吗？ | 包名/类名不变则不用；若改名 `AndroidScenePainter`，keep 规则（当前 `app/r04-test-keep.pro`/`proguard-rules.pro` 对 painter 零命中）仍不用改。若未来 V-01 进 release 轨，再按 V-01 设计 §2.2 补 keep。 |
| V-01 状态会因此变成 4/4 吗？ | **不会自动**。seam 的 PDF 半边变得可薄转调，但 V-01 ④ 的设备证据仍需重跑；且 restore 半边未变。状态仍按主会话/台账裁决。 |

**方案选择**：
- A（推荐）：seam 原样 + PDF 半边薄转调；零 V-01 测试 churn。
- B（后续可选）：删 `writeSnapshotPdf`，app 测试直接用 `core:visual` 公共 PDF API；少一个 seam 函数，但 V-01 用例要改，且 restore→PDF 全腿变成测试自拼两步。**本批不做**。

---

## 4. `settings.gradle.kts` 热区下的落地顺序与回滚

### 4.1 前置条件（窗口开启前）

1. `settings.gradle.kts` 仍为基线 sha `46b3c37c…` 且 `git status --porcelain -- settings.gradle.kts` 无输出；若他线已改，**停项登记**，不要 rebase 别人的改动。
2. 移动清单内文件 targeted `git status` 干净；任一文件变脏 → 按"整文件纳入并注明"或停项登记（共享纪律），不要部分搬。
3. 记录基线：
   - `wc -l` 与 sha256（§1.3 表）；
   - `grep -c '@Test'` 计数（§3.3 表）；
   - `python tools/ci/check_visual_test_seams.py`、`python tools/ci/check_lockfiles.py` 的当前输出（本会话已跑：均 `EXIT=0`）。
4. 准备 `core/visual/build.gradle.kts` 与移动清单；**不**在窗口外提交"模块已 include 但代码半搬"的状态。

### 4.2 补丁序列（P0–P5）

> 每段都必须是一个可构建的树；P2/P3 是语义分段，不是"先破坏后修"。

**P0 — 建立空模块（只碰 settings 一行 + 新 build 文件）**

```diff
     ":benchmark",
 )
+
+// 批次 40 R7 / S-06：visual 渲染协议与度量独立模块（热区单行；勿在此改其他内容）
+include(":core:visual")
```

验证（窗口内第一道）：
```bash
./gradlew :core:visual:assembleDebug --console=plain
./gradlew projects --console=plain
```
判据：空模块可配置/编译；`settings.gradle.kts` 只多这一行。若失败，回滚 = 删这两行 + 删 `core/visual/`，零源码移动。

**P1 — 移协议层（DTO/parser/validator/duplicate-keys）**

- `git mv` 到 `core/visual/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/`（保留包名）。
- 可见性按 §2.5 提升；`core/data/build.gradle.kts` 增加 `api(project(":core:visual"))`。
- 移 `VisualDrawOpsParserTest`（26）、`VisualDrawOpsValidatorTest`（35）到 `core/visual/src/test`。

验证：
```bash
./gradlew :core:visual:testDebugUnitTest :core:data:testDebugUnitTest --console=plain
```
判据：61 例 JVM 绿；`core:data` 引擎/恢复编译通过（parser 公共化无签名变化）。

**P2 — 移回放/PDF/度量**

- 移 §2.2 的 painter/PDF/artifact/PdfWriteIntegrity/`VisualDrawOpsPainterRendered`/`VisualContentMeasuring`。
- 移 JVM 74 例 + 设备 58 例（§3.3）。
- `core/data` 的 `TutorVisualPreviewRenderer`/`VisualChainTestSeams`/V01 降级变体只改 import（包名不变时多数零改动）；`VisualChainTestSeams.writeSnapshotPdf` 可改为薄转调。
- `app/build.gradle.kts` 加 `implementation(project(":core:visual"))`；`VisualJsService` 加 `visualContentMeasurerForService` 的 import。
- `AndroidScenePainter` 改名（若采纳）同步 `VisualDrawOpsPdfPainter` 与测试。

验证：
```bash
./gradlew :core:visual:testDebugUnitTest :core:visual:assembleDebug --console=plain
./gradlew :core:data:testDebugUnitTest :app:testLocalFirstDebugUnitTest --console=plain
```

**P3 — 设备轨**

```bash
./gradlew :core:visual:connectedDebugAndroidTest --console=plain
./gradlew :core:data:connectedDebugAndroidTest --console=plain
./gradlew :app:connectedLocalFirstDebugAndroidTest --console=plain
```
判据：`core:visual` 58 例全绿（先红后绿的新模块任务）；`core:data`/app 不回退。**旧设备绿不能替代**。

**P4 — CI/状态/锁/门**

- `.github/workflows/android-check.yml`：
  - Unit tests（`:99-104`）加 `:core:visual:testDebugUnitTest`；
  - Lint（`:134-135`）加 `:core:visual:lintDebug`；
  - Instrumented（`:233`）加 `:core:visual:connectedDebugAndroidTest`；
  - 如需 coverage，再加 `:core:visual:unitTestCoverageXmlReport`（先在 build 文件注册 Jacoco；不注册则明确"新模块不在覆盖率门"）。
- `tools/ci/generate_status.py`：`MODULES`（`:41-47`）与 `LINT_LIBRARY_MODULES`（`:54-56`）加 `:core:visual`；coverage 映射（`:566-570`）按是否注册报告决定。
- 锁：`./gradlew --no-configuration-cache --no-parallel --console=plain lockAllDependencies --write-locks`；确认 `core/visual/gradle.lockfile` 生成。当前 `check_lockfiles.py` 是"尚未落地"语义（0 已跟踪锁），不要因为门绿就认为锁已覆盖新模块。
- 跑门：
```bash
python tools/ci/check_visual_test_seams.py
python tools/ci/check_visual_module_boundaries.py
python tools/ci/check_lockfiles.py
python tools/ci/check_file_size_gate.py --base origin/main
```

**P5 — 文档/台账**

- S-06 行由主会话按实际构建/设备证据更新；本文件只给设计与判据。
- 契约 §5.1 的"painter 落 core:data 过渡态"改为"core:visual"（`docs/current` 热区，另批/主会话应用，本批不写）。
- `docs/REFACTOR-MASTER-LINE.md` 记 S-06 进展（只登记，不宣称通过）。

### 4.3 热区协议

- `settings.gradle.kts` 只允许 P0 的一行；**不要**在同批整理 include 顺序/逗号/注释。
- 独立 `include(":core:visual")` 放在现有 `include(...)` 块之后：`check_lockfiles.py` 的 `INCLUDE_BLOCK_RE`（`:40`）会扫描全文的每个 `include(...)`，独立行同样被解析；这样避免改现有块的尾逗号。
- 若他线在窗口内改了 settings：先 `git diff settings.gradle.kts` 看是否只是自己的行；若是别人的改动，**停项**，把补丁重放到最新文件后再继续；不要 `git checkout` 别人的改动。
- 不在窗口内改 root `build.gradle.kts`、`gradle/libs.versions.toml`、`.github` 以外的共享文件；CI 清单改动放 P4，与模块代码分开提交。

### 4.4 回滚矩阵

| 失败点 | 回滚动作 | 树的状态 |
|---|---|---|
| P0 空模块构建失败 | 删 `include(":core:visual")` + 删 `core/visual/` | 回到基线，零源码移动 |
| P1 JVM 红（parser/validator） | 反向 `git mv` 回 `core:data`；还原可见性；删 `api(project(":core:visual"))` | 回到基线；seam/设备不受影响 |
| P2 编译/单测红 | 反向移动 painter/PDF/measurer 与测试；还原 app/core:data build/import；保留 P1 协议层（仍可构建）或一并回滚 | 中间态：`core:visual` 只有协议层仍合法；S-06 仍部分落 |
| P3 设备红 | 不回滚源码；按"本批/他线/环境"归因；修复环后复跑同一命令 | 源码保留，设备证据未达 |
| P4 CI/锁/门红 | 先修门/清单；不要放宽门。锁问题用 `--write-locks` 重生成；CI 清单漏项补项 | 模块代码保留 |
| settings 被他线改动/冲突 | 停项登记；不强行 rebase 覆盖；补丁重放到最新 settings | 不产生半合并状态 |

**整体回滚**：按 P4→P0 逆序 `git revert`/反向移动；因为全程 `git mv`（不复制），回滚后不会留下重复类。**不要**只回滚用例不回滚 seam/门（`check_visual_test_seams.py` 会红提醒，V-01 设计 `:88-90` 同型纪律）。

**停止条件（任一触发即停项登记，不硬做）**：
- `settings.gradle.kts` 在窗口内出现非本补丁的改动；
- 移动清单内文件变脏且无法整文件纳入；
- `core:visual` 空模块构建失败且原因不在本补丁（AGP/Kotlin/JDK）；
- P1/P2 出现 >5 个不可归因红例；
- R8 keep / R-04 门因新模块出现未知红；
- 需要新增外部依赖/插件才能继续。

### 4.5 牵动面清单（改一处先枚举）

| 牵动面 | 动作 | 说明 |
|---|---|---|
| `settings.gradle.kts` | +1 独立 include | 热区；只此一行 |
| `core/visual/build.gradle.kts` | 新增 | 见 §2.4 |
| `core/data/build.gradle.kts` | +`api(project(":core:visual"))` | 因 `TutorVisualPreviewRequest` 公开暴露 artifact |
| `app/build.gradle.kts` | +`implementation(project(":core:visual"))` | 度量入口 |
| `VisualJsService.kt` | +import 度量工厂 | 唯一 app main 消费者（本会话 grep 实证） |
| `core:data` visual 代码 | 包名不变 → 多数零 import；可见性提升 | `TutorVisualPreviewRenderer`/seam/V01 降级变体 |
| 测试（193 例） | 移动/保留见 §3.3 | 设备绿必须重跑 |
| `.github/workflows/android-check.yml` | 三处清单 | 不点名 = 永不运行（文件内注释原话） |
| `tools/ci/generate_status.py` | MODULES/LINT 清单 | 否则状态报告看不见新模块 |
| 锁文件 | 生成 `core/visual/gradle.lockfile` | 当前门是"尚未落地"语义 |
| `tools/ci/check_visual_test_seams.py` | 不改 | 声明路径不变 |
| 新增边界门 | 新增文件 + CI 调用 | 见 §3.4 |
| `core/ui`/`core/export` | 本批不改 | 热区；未来只加 `implementation(project(":core:visual"))` |
| 契约 §5.1 / S-06 台账 | 只登记 | docs/current 热区，另批应用 |

---

## 5. GitHub 参考评分

评分口径（每项 1–5，总分 25）：

- **License**：5 = 仓库级 Apache-2.0/MIT 明确；3 = 仅文件头声明/仓库级未声明；1 = 未知/不兼容。
- **维护度**：5 = 近 90 天有 push；4 = 近 180 天；2 = 近 1 年；1 = 超过 1 年。
- **栈匹配**：5 = AGP 9.3.x + Gradle 9.x + Kotlin 2.3.x；4 = 接近但版本有差；2 = 旧 AGP；1 = 不可参考。
- **相关度**：5 = 模块抽取 + test seam/ painter/PDF 直接相关；4 = 模块布局/依赖规则；3 = 依赖卫生；2 = 泛化示例；1 = 无关。
- **证据质量**：5 = 官方/一手维护；4 = 广泛使用且有发布记录；3 = 个人样例。

| 仓库 | License | 维护 | 栈 | 相关 | 证据 | 总分 | 结论 |
|---|---|---:|---:|---:|---:|---:|---|
| `android/nowinandroid` | 5（Apache-2.0） | 5（2026-11-01 push） | 5（AGP 9.3.2 / Kotlin 2.3.0） | 5 | 5（Google 官方） | **25** | **主参考**：模块类型、`api`/`implementation`、独立 `-test` 模块 |
| `autonomousapps/dependency-analysis-gradle-plugin` | 5（Apache-2.0） | 5（2026-11-01 push；v3.20.1 2026-10-31） | 4（changelog 测 AGP 9.3.1 / Gradle 9.7.1 / KGP 2.4.0；本仓 9.3.0/9.6.1/2.3.21） | 4（依赖图/config 检查） | 4（广泛使用） | **22** | **只借检查清单，不引插件**（避免根 build/settings 热区 + 新依赖） |
| `android/gradle-recipes`（`agp-9.0` 分支） | 3（仓库级 license API 404；recipe 文件 Apache-2.0 头） | 4（2026-10-22 push；分支只读） | 5（AGP 9.0 分支；本仓 9.3） | 4（testFixtures，非模块抽取） | 5（AOSP 官方 recipe） | **21** | **仅作 testFixtures 事实源**；本设计不采用 testFixtures（androidTest 消费未示范） |
| `androidx/androidx` | 5（Apache-2.0） | 5（2026-11-01 push） | 5 | 2（巨型 monorepo，不是抽取模板） | 5 | **22** | 只作 API/可见性纪律的远参考，不作模板 |
| `vmadalin/android-modular-architecture` | 5（Apache-2.0） | 1（2021-08-23 push） | 1（旧 AGP） | 2 | 3 | **12** | **拒绝**：停更、栈不兼容，不能作 2026 模板 |

**证据摘录（本会话 `gh api` 实读）**

- NIA `settings.gradle.kts`：`include(":core:ui")`、`:core:designsystem`、`:core:data`、`:core:data-test`、`:core:testing` 等；`core/ui/build.gradle.kts` 用 `api(projects.core.designsystem)` / `api(projects.core.model)`；`core/data/build.gradle.kts` 用 `api(projects.core.database)` 等、`implementation(projects.core.analytics)`；`core/data-test/build.gradle.kts` 只有 `api(projects.core.data)` + `implementation(libs.hilt.android.testing)`。→ **暴露给消费者的类型用 `api`，内部实现用 `implementation`；测试夹具单独模块，不把测试代码塞进 main。**
- NIA `docs/ModularizationLearningJourney.md`：core modules "can depend on other core modules, but they shouldn't depend on feature nor app modules"；模块 README 有依赖图；`core:designsystem` 与 `core:ui` 分责（设计系统 vs 依赖数据层的组合 UI）。→ 与本文 `core:visual`（渲染/度量）vs `core:ui`（Compose 组合）的分责同型。
- DAGP `README.asciidoc` Features：unused dependencies、used transitive、wrong configuration (`api` vs `implementation`)、unused annotation processors；CHANGELOG 记 "update (and test against) AGP 9.3.1" 与 "Gradle 9.7.1 and KGP 2.4.0"。→ 本文 §3.4 的边界门只借它的检查项，不引入插件。
- gradle-recipes `testFixtures/lib/build.gradle.kts`：`testFixtures { enable = true }` + `testFixturesImplementation(libs.truth)`；`testFixtures/app/build.gradle.kts`：`testImplementation(testFixtures(project(":lib")))`。README 只写 `app/test -> lib/testFixtures`。→ **只证明 unit test 消费；androidTest 消费未示范**（与 V-01 设计 U1 一致）。
- `vmadalin/android-modular-architecture`：`pushed_at=2021-08-23`，`license=Apache-2.0`；只能作历史模块化样例，不能作当前栈模板。

**采用决定**：
- 采用 NIA 的**结构模式**（模块类型/`api` vs `implementation`/测试模块独立）；不复制其代码、不引入 Hilt/Compose 约定插件（本仓没有 build-logic，新增会改根构建）。
- 不引入 DAGP、testFixtures、Keeper 等新机制；理由见 §3.2/§3.4，均为"新增不消灭当前失败/落热区"。

---

## 6. 风险、开放问题与未验证项

### 6.1 风险

| # | 风险 | 级别 | 处置 |
|---|---|---|---|
| R1 | 移动 parser 969 行，接近 1000 行硬门；后续小改可能触发 file-size gate | 中 | 本批不拆；若后续增长，按 parser 子阶段拆文件（单独批），不在抽取批混 |
| R2 | `core:data` 公开 API 经 `TutorVisualPreviewRequest` 暴露 `VisualMeasuredLayoutArtifact`，必须 `api(project(":core:visual"))`；若用 `implementation`，app 编译会缺类型 | 中 | 用 `api`；边界门检查 `core:data` 不因此依赖 `core:ui/export` |
| R3 | 包名过渡态（`core.data.visual` in `core:visual`）让后来者困惑 | 低 | 模块 README/KDoc 注明；后续机械重命名 |
| R4 | 改名 `AndroidScenePainter` 触及大量 KDoc/测试名 | 低 | 机械改名，行为零变化；若不改名则 S-06 名实欠账 |
| R5 | `settings.gradle.kts` 冲突 | 高 | 独立一行、P0 单段、停项条件见 §4.5 |
| R6 | 新模块未进 CI/status → 测试永不运行 | 高 | P4 三处清单 + status generator；否则不算落地 |
| R7 | 锁文件未跟踪/新模块无锁 | 中 | `--write-locks` 生成；`check_lockfiles.py` 当前"尚未落地"语义不等于覆盖 |
| R8 | R8/release keep：painter 公共化后仍可能被收缩 | 中 | 当前 keep 文件对 painter 零命中；若 V-01 进 release 轨，按 V-01 设计补 keep；不因 debug 绿就宣称 release 绿 |
| R9 | 设备绿是旧绑定快照 | 高 | 新模块 58 例必须新跑；`core:data`/app 回归同轮 |

### 6.2 开放问题（需主会话/实施批裁定）

1. **是否改名 `AndroidScenePainter`**：若 S-06 验收按字面 grep，必须改；若按语义（共用 painter），可不改。本文建议改。
2. **包名重命名时机**：本文建议单独机械批；若主会话要求一次到位，需把 95 文件 import 改动纳入同一窗口，冲突风险显著上升。
3. **`core:visual-js` 是否同窗口**：X-01 依赖 app/AIDL/Service，建议后置；若同窗口，先 `core:visual` 再 `core:visual-js`，各自独立回滚。
4. **`core:export`/`core:ui` 接线批**：S-06 的"共用"最终要靠它们消费 `core:visual`；本批只到模块与公共 API。
5. **覆盖率/Jacoco**：新模块是否注册 `unitTestCoverageXmlReport`；不注册则需在 status 报告显式标"未测"。

### 6.3 未验证项（明确列出，不当作已过）

| 面 | 状态 | 原因 |
|---|---|---|
| `core:visual` 空模块/完整模块构建 | **UNVERIFIED** | 本会话零 Gradle；build 文件为草案 |
| 135 JVM + 58 设备例迁移后绿 | **UNVERIFIED** | 未运行；计数来自 `grep -c '@Test'`，不是执行结果 |
| `AndroidScenePainter` 改名后的编译/设备 | **UNVERIFIED** | 未实施 |
| `core:data`/app 在新依赖图下编译 | **UNVERIFIED** | 未运行 |
| AGP 9.3.0 对 `core:visual` 新模块的配置 | **UNVERIFIED** | 未运行 |
| R8/release keep 行为 | **UNVERIFIED** | 未运行 |
| CI 三处清单 + status generator 改动效果 | **UNVERIFIED** | 未运行 CI |
| 锁文件生成 | **UNVERIFIED** | 未运行 Gradle |
| 边界门脚本 | **未编写/未运行** | 本文件只给判据 |
| `testFixtures` androidTest 消费 | **未验证/不采用** | 官方 recipe 未示范；V-01 设计 U1 |
| DAGP 对 AGP 9.3.0 的精确兼容 | **待验证** | changelog 测 9.3.1；本仓 9.3.0，未运行 |
| GitHub 星数/维护度 | 2026-11-02 快照 | 会变化；不作为永久事实 |
| `core:export`/`core:ui` 共用 painter | **未接线** | 热区另批 |

---

## 7. 来源

### 7.1 仓内（本会话实读）

| 主题 | 路径:行 |
|---|---|
| 模块清单 | `settings.gradle.kts:18-34` |
| core:data 依赖 | `core/data/build.gradle.kts:169-171` |
| app 依赖 | `app/build.gradle.kts:171-182` |
| DrawOps DTO/可见性 | `core/data/.../visual/VisualDrawOps.kt:13-14`、`:165` |
| parser/validator | `core/data/.../visual/VisualDrawOpsParser.kt:50`、`VisualDrawOpsValidator.kt:29` |
| 屏 painter | `core/data/.../visual/VisualDrawOpsPainter.kt:44`、`:273` |
| 纯层/flags/label | `core/data/.../visual/VisualDrawOpsPainterPure.kt:87`、`:194` |
| PDF painter | `core/data/.../visual/VisualDrawOpsPdfPainter.kt:55-57` |
| Rendered 衔接 | `core/data/.../visual/VisualDrawOpsPainterRendered.kt:23-35` |
| 度量入口 | `app/.../visualjs/VisualContentMeasuring.kt:54`、`:122`、`:131`、`:616`、`:643` |
| 服务消费 | `app/.../visualjs/VisualJsService.kt:150` |
| 度量端口 | `core/domain/.../VisualExecutionEngine.kt:162` |
| V-01 seam | `core/data/.../visual/VisualChainTestSeams.kt:18-35`、`:37`、`:51`、`:94` |
| seam 门 | `tools/ci/check_visual_test_seams.py:36-42`、`:128-135` |
| 锁门 | `tools/ci/check_lockfiles.py:40-51`；根 `build.gradle.kts:37-38` |
| CI 清单 | `.github/workflows/android-check.yml:99-104`、`:134-135`、`:233` |
| 状态生成器 | `tools/ci/generate_status.py:41-56`、`:566-570` |
| 契约 | `docs/current/visual-generation-contract.md:110-112`、`:123-127`、`:202` |
| 准入清单 S-06/X-01 | `docs/research/2026-10-26-batch-24-4e2-admission-checklist.md:149`、`:242` |
| V-01 设计 | `docs/research/2026-11-01-v01-pdf-leg-exposure-design.md:9`、`:35-37`、`:66`、`:86-90`、`:103` |
| painter 设计（分层先例） | `docs/research/2026-10-28-painter-design.md:12`、`:74-76` |
| 批次 40 基线 | `docs/research/2026-11-02-batch-40-baseline.md:44-48` |
| 版本 | `gradle/libs.versions.toml:2-3`；`gradle/wrapper/gradle-wrapper.properties:3` |

### 7.2 外部（`gh api` / 官方仓库，2026-11-02 取）

| 来源 | 用途 |
|---|---|
| https://github.com/android/nowinandroid | 模块类型、`api`/`implementation`、`-test` 模块（Apache-2.0，21,993★，2026-11-01 push） |
| https://github.com/android/nowinandroid/blob/main/settings.gradle.kts | 17 个 core 模块、6 个 feature 目录 |
| https://github.com/android/nowinandroid/blob/main/docs/ModularizationLearningJourney.md | core 模块不得依赖 feature/app；模块依赖图 |
| https://github.com/android/nowinandroid/blob/main/core/ui/build.gradle.kts | `api(projects.core.designsystem)` 等 |
| https://github.com/android/nowinandroid/blob/main/core/data-test/build.gradle.kts | `api(projects.core.data)` 的测试模块模式 |
| https://github.com/android/gradle-recipes/tree/agp-9.0/testFixtures | 官方 testFixtures recipe（只示范 unit test） |
| https://github.com/android/gradle-recipes/blob/agp-9.0/testFixtures/lib/build.gradle.kts | `testFixtures { enable = true }` |
| https://github.com/autonomousapps/dependency-analysis-gradle-plugin | 依赖卫生检查项（Apache-2.0，2,190★） |
| https://github.com/autonomousapps/dependency-analysis-gradle-plugin/blob/main/CHANGELOG.md | 测 AGP 9.3.1 / Gradle 9.7.1 / KGP 2.4.0 |
| https://github.com/vmadalin/android-modular-architecture | 旧样例（2021-08-23 停更；拒绝作模板） |

---

## 附录 A：`settings.gradle.kts` 补丁（唯一允许的行）

```diff
     ":benchmark",
 )
+
+// 批次 40 R7 / S-06：visual 渲染协议与度量独立模块（热区单行；勿在此改其他内容）
+include(":core:visual")
```

## 附录 B：落地判据清单（逐项可勾）

- [ ] `settings.gradle.kts` 仅多一行，sha 变化可解释；
- [ ] `core/visual/build.gradle.kts` 编译通过；
- [ ] `core:visual` 无 `:core:data`/`:core:database`/`:core:ui`/`:core:export`/`:app`/`:feature` 依赖；
- [ ] `core:visual/src/main` 无 `core.data`/`core.database`/`core.ui`/`core.export`/`visualjs` import；
- [ ] `core:data` 仅 `api(project(":core:visual"))` 一条新边；
- [ ] 135 JVM + 58 设备例迁移后绿；
- [ ] `core:data`/app 设备套件不回退；
- [ ] `check_visual_test_seams.py` 仍 `OK`；
- [ ] 新边界门 `EXIT=0`；
- [ ] CI unit/lint/instrumented 三处清单含 `:core:visual`；
- [ ] `generate_status.py` 的 MODULES/LINT 含 `:core:visual`（或显式登记未覆盖）；
- [ ] `core/visual/gradle.lockfile` 生成（或登记"锁未落地"）；
- [ ] S-06 台账只记"部分落/进展"，不写"通过"；
- [ ] X-01 单独登记，未与 `core:visual` 混同。

## 附录 C：与 X-01 的边界草案

- X-01 目标模块：`core:visual-js`，承接 `VisualJsService`、`VisualJsClientEngine`、AIDL、wire 协议；端口 `core:domain.VisualExecutionEngine` 与 wire 字节不变。
- `core:visual` 与 `core:visual-js` **互不依赖**：`core:visual` 是渲染/度量；`core:visual-js` 是执行/进程通信。app 作为组合根把二者与 `core:data` 装配起来。
- 顺序：`core:visual` 先（叶子模块，低风险）；`core:visual-js` 后（app manifest/AIDL/进程边界，需设备复跑）。两者各自一条 settings include、各自回滚。
- 不在本批移动 `VisualJsService`/AIDL/`VisualJsClientEngine`；X-01 仍由 app 热区与设备窗口阻塞。
