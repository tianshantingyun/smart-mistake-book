# D-04 预览 PNG 生产接线 + M-03 分配前拒绝 + N5/N6/N7 设备判据 + V-01④ seam 收口（2026-11-01，批次 39 R1）

> 研究轨（驱动计划 `.zcode/plans/plan-2026-11-01-batch-39-wiring-calibration.md` §1.A 任务 1/2/4 = T2/T3）。
> **只读/设计**：零 Gradle、零设备命令、零生产代码改动、零 git 写、不动热区。产出 = 本文件。
> 方法：本会话实读 commit 仓储/Persister/预算器/vault/恢复侧/六站点谓词/两套 V-01 套件等源码（行号均为本会话实读），
> 外部参考经 `gh api`（仓库元数据）与官方文档实取。未实测项一律标 **UNVERIFIED**；零 Gradle 环境，编译/测试执行归门禁与设备轨。

## 0. 一句话结论

1. **D-04 接线**（任务一）：预览渲染器落 core:data `visual` 包**新文件**（internal + 公共工厂，沿
   `FigureGenerationCoordinationFactory` 先例），输入 = 引擎 `Rendered.drawCommandsJson`（引擎已过校验门）
   + `VisualMeasuredLayoutArtifact` 折 label DTO；时点 = `commitAssistantTurnWithVisual` 调用路径（**不**含
   `stageSnapshot`）；失败语义 = **有界降级**（previewAssetId=null + 有界诊断 + 快照照常提交），16 MiB 门
   `requireBitmapBytesWithinBudget` 在 `createBitmap` **前**执行、拒绝后不缩放不重试；降级自愈——已登记未引用
   的资产行被六站点谓词 GC 回收。
2. **N5/N6/N7 判据**（任务二）：设备向量落 core:data androidTest（同模块可达 internal，**无需 seam**；N7 合计
   语义用公共类型 `VisualMeasuredContent` 构造合成条目）；"真引擎+真度量器+真提交"的全程设备例归 app V-01
   链套件（与 T3 的 seam 增量共乘）。
3. **V-01④**（任务三）：批次 38 T10 方案 A（`VisualChainTestSeams` 单新文件公开测试缝）的**事实前提本会话全部
   复核仍成立**（7 类 internal 符号未变、缝不存在、testFixtures 仍缺席、app 3 例/变体 4 例）；维持推荐 A，
   硬前提 = R-04 release 轨 keep（`app/r04-test-keep.pro` 在树）或 Keeper；回滚 = 删 1 新文件 + 还原用例增量。
4. **外部参考**（任务四）：Coil（Apache-2.0，11,956★，2026-10-31 仍活跃）的"请求有界尺寸、ORIGINAL 需显式"
   是预览定界策略的最佳同形；androidx `workmanager-testing` 的公共 `TestDriver` 是"公开测试缝"的权威先例
   （但其独立 artifact 形态需新模块 → settings.gradle.kts 热区，反向支持方案 A）；官方 `Bitmap.Config` 逐字
   引文本会话实取（ARGB_8888 = 4 B/px），**关闭 R2 §6 的待验证项**。

---

## 1. 失败事实闭包（本会话实读；接线的"内里"）

### 1.1 写侧已备、生产零调用方

| 事实 | 锚（实读） |
|---|---|
| `previewAssetId` 生产恒 null：main 源集零调用方，唯一构造点在 app androidTest | `grep TutorVisualSnapshotInput( main = 0 命中`；`app/src/androidTest/.../visualjs/V01VisualChainInstrumentedTest.kt:426` |
| 唯一预览写口已落（批次 38）：落字节→登记行→返回记录，source_type 走共享常量 | `core/data/.../visual/TutorVisualPreviewAssetPersister.kt:31-61`；常量 `core/database/.../StudyDbValue.kt:58` |
| `forProduction` 装配（vault 统一预算面 + DB 端口）零调用方 | `TutorVisualPreviewAssetPersister.kt:68-85`（grep main 零命中） |
| commit 仓储零生产调用方：`TutorVisualCommitRepository(` 仅测试构造 | grep `app/src/main` = 0 命中；`V01VisualChainInstrumentedTest.kt:410-446`（app 测试手工提交） |
| 生产视觉链未通引擎：`FigureGenerationCoordinationFactory.create` 装配**无** `visualEngine` 入参 | `app/src/main/.../SmartMistakeBookApplication.kt:363-369`；缺省回退 `DefaultFigureGenerationCoordinator.kt:276`（`visualEngine ?: return` → UNAVAILABLE） |
| 第六类站点谓词（RESTRICT 引用 + 孤儿 GC 判定单源） | `core/database/.../dao/CanonicalAssetReferenceSites.kt:14,51-54` |
| vault 接受门（预览 PNG 的下游闸） | `AndroidCanonicalAssetVault.kt:204-219`（输入 ≤ 24 MiB）、`:477-479`（MAX_DIMENSION=8192、MAX_PIXELS=16,000,000、MAX_CANONICAL_BYTES=24 MiB） |

**结论**：D-04 的"生产接线"不是一个孤立写点，而是**未来视觉链生产装配**（引擎进 coordinator + commit 进
讲题入口）的一个组成缝。本设计把预览缝定义成**独立可接线单元**：它不依赖引擎何时进生产，只要 commit
装配点出现，预览缝按 §2 插入即可；在此之前它与 Persister 同状态（工具类备好、零调用方），不虚报"已接线"。

### 1.2 恢复侧为什么要预览（失败语义的根据）

- `VisualRestorePolicy.CURRENT` 的 `verifiedFontManifests` 为**空集**（`VisualSnapshotRestore.kt:100-125`，
  `:125` `VERIFIED_FONT_MANIFESTS = emptySet()`）→ 默认判定序必然走到 `ReadOnly`（③档）——**今天每次冷恢复
  都是只读档**。
- ③档显示 = 预览 PNG（`previewVerified=true` 时）或**只剩文字说明**（`VisualRestoreOutcome.ReadOnly`，
  `VisualSnapshotRestore.kt:147-152`）。previewAssetId 恒 null ⇒ 冷恢复永远只剩文字——这正是 D-04 要消灭的
  失败（批次 38 R3 研究原话："未知版本降级时只剩文字说明"）。
- 因此预览的定位是**③档显示缓存**，不是回放输入（②档回放唯一输入是 `drawCommandsJson`，`VisualSnapshotRestore`
  判定序不读预览字节）→ 它的失败**不应**拖垮快照提交的完整性（§2.3）。此定位决定 §2.3 全部失败语义。

### 1.3 预算器与执行点（M-03 已落、缺的只是分配点）

- `VisualLayoutBudget.requireBitmapBytesWithinBudget(widthPx, heightPx, bytesPerPixel)`：超 16 MiB 即抛
  IllegalArgumentException（携带 `BITMAP_BYTE_BUDGET` 码），**分配不发生**；恰达不拒（`VisualLayoutBudget.kt:108-128`）。
- 4 MP 累计门已在引擎度量结算处接线（`QuickJsVisualExecutionEngine.kt:418-421`，measurer 汇总后、render 前，
  越界折 INVALID_PROGRAM + `LAYOUT_PIXEL_BUDGET`）。
- 引擎 `:417` 注记"预览尺寸并入同一累计是 D-04 轨的接线点"——注意口径：**该并入只能在预览生成侧判**
  （§2.4 裁定为什么不能在引擎结算处判）。
- `bytesPerPixel` 参数化的根据（本会话官方文档实取，关闭 R2 §6 待验证）：`Bitmap.Config` 官方页逐字
  —— **ARGB_8888 "Each pixel is stored on 4 bytes"**；RGB_565 = 2 B/px、RGBA_F16 = 8 B/px、ALPHA_8 = 1 B/px
  （developer.android.com/reference/android/graphics/Bitmap.Config，Last updated 2026-08-03）。

### 1.4 渲染链各环节在树上的现状

| 环节 | 现状 | 锚 |
|---|---|---|
| DrawOps 文本 → document | `VisualDrawOpsParser.parse`（internal），恢复判定序同一入口 | `VisualDrawOpsParser.kt:50`；`VisualDrawOpsPainterRendered.kt:26-38`（`drawRendered` 范式：解析拒绝折 `PAINTER_DRAW_COMMANDS`） |
| document → Canvas | `VisualDrawOpsPainter.draw(document, canvas, pxPerDu, labelContent)`（internal）；`pxPerDu` 非有限/非正即拒；label 解析 null = 内容不可达 | `VisualDrawOpsPainter.kt:72-92` |
| label DTO | `VisualLabelContent.Text/Math`（internal）；生产来源 = 度量 artifact 折算（"app 侧装配器把度量产物折成本 DTO"为登记的接线） | `VisualDrawOpsPainterPure.kt:194-228`；`VisualMeasuredLayoutArtifact.kt`（public，携 lines/heightDu/kind/degraded） |
| 位图分配先例 | `PdfBatchImportPageRenderer.kt:62` `Bitmap.createBitmap(width, height, ARGB_8888)`——页尺寸由 PDF 界定故无门；预览尺寸由 du×density 推导**无界**，故必须有门 | `core/data/.../capture/PdfBatchImportPageRenderer.kt:62` |
| 画布上界 | `VisualExecutionBudget.MAX_CANVAS_EDGE_DU = 4096`（快照输入 require 同口径） | `TutorVisualSnapshotInput.kt:112-117`（`TutorVisualCommitRepository.kt` 内） |

---

## 2. 任务一：预览 PNG 生产接线设计（T2）

### 2.1 渲染输入（全部来自"提交时已在手"的数据，零新增查询）

```
Rendered.drawCommandsJson（引擎产出，VisualDrawOpsValidator 已复验过——core/domain VisualExecutionEngine.kt:236-239 KDoc）
  → VisualDrawOpsParser.parse        （恢复级复检：不信任"自称 validated"，painter 拒绝折 PAINTER_DRAW_COMMANDS 有界码）
  → VisualDrawOpsPainter.draw(document, previewCanvas, pxPerDu_preview, labelContent)
      pxPerDu_preview = min(1f, PREVIEW_EDGE_PX / max(canvasWidthDu, canvasHeightDu))   ← 定界策略，见 2.4
      labelContent    = contentId → VisualLabelContent（由 VisualMeasuredLayoutArtifact 折算，同一次执行的度量产物）
  → Bitmap.compress(PNG, 100) → ByteArray
```

- **labelContent 的生产来源**：`VisualMeasuredLayoutArtifact` 是 public 类型且正是"度量侧产物"
  （`VisualMeasuredLayoutArtifact.kt` KDoc 消费表第 3 行：painter 回放经 `VisualLabelContent` 消费 lines/heightDu）。
  artifact→DTO 的折算 helper 是登记过的公共接线（M-01/replay 批），预览与屏/PDF 回放**复用同一折算**——
  不为预览造第二套行/段规则（契约 `:131`"不得继续增加第三套公式/坐标绘制逻辑"的同族纪律）。
- **同名帧输入**：预览必须用与快照行相同的冻结帧（viewport/theme/fontScale/seed/timeMs）——预览是"这张快照
  的样子"的显示缓存；输入漂移 = 显示缓存与回放语义说谎。
- **不画 math 盒**：`VisualLabelContent.Math` 现行 fail-closed（`PAINTER_LABEL_MATH`）——预览继承该边界，
  math 标签位置按既有降级语义处理，不做预览特例。

### 2.2 时点（写入点）

1. **只在 `commitAssistantTurnWithVisual` 路径**（LIVE 首张可见图）渲染预览；**不在 `stageSnapshot`**——
   STAGING 行是临时中间态（60 分钟租约、`reconcileStaleStaging` 回滚回收，`TutorVisualCommitRepository.kt:27-38,359-360`），
   给临时行产预览 = 白烧一次位图+编码+落盘，且被回收后必成孤儿。
2. **顺序即语义**（Persister KDoc 已钉）：渲染 → `Persister.persist`（字节→资产行，原子两步）→
   `record.sourceAssetId` 填进 `TutorVisualSnapshotInput.previewAssetId` → 仓储提交。引用建立时资产行必然已在，
   RESTRICT FK 不会被拒。**不在仓储内部**做预览（DB 层保持纯事务语义，不掺 Canvas/Bitmap 设施）；
   **不在引擎内部**做预览（JS 事务边界内做宿主位图工作会把显示缓存耦合进执行结果，且越界折型会污染
   INVALID_PROGRAM 语义，见 2.4）。
3. **createdAt = 提交装配所用的同一 `now` 读数**（仓储 `clock()` 同源），避免资产行与快照行时间戳各说各话。
4. 幂等：内容寻址（Persister KDoc `:27-29`）——同内容重复提交 → 同字节 → 同一行（`INSERT OR IGNORE`），
   不产生第二份文件。**前提**：渲染确定性（同 drawCommands + 同帧输入 + 同 pxPerDu 策略 + 同 renderer 版本）；
   pxPerDu 策略必须**不依赖设备 density**（预览自定界，见 2.4），否则同内容跨设备不同字节——幂等破。这是
   策略取 `min(1f, …)` 且基于 du 而非 density 的根本原因（登记为实施批的防漂移要求）。

### 2.3 失败语义（有界降级；每类指认）

| 失败类 | 处置 | 依据/自愈 |
|---|---|---|
| 16 MiB 字节门拒（`BITMAP_BYTE_BUDGET`） | 有界拒绝，**不缩放重试、不静默缩**；降级为无预览提交 + 有界诊断 | 契约 §4"超限不截断成'正确图'，返回有界诊断"；策略已保证不触发（2.4），触发即策略缺陷，如实暴露 |
| 4 MP 累计（含预览）拒（`LAYOUT_PIXEL_BUDGET`） | 同上：拒预览不拒快照 | 预览是③档显示缓存（1.2）；文字侧已在引擎结算处独立判过 |
| 解析/绘制拒绝（`PAINTER_DRAW_COMMANDS` 族）、label 内容不可达 | 降级为无预览提交 + 有界诊断 | 快照回放唯一输入是 drawCommands（已由引擎门保证非空合法）；预览失败不动摇它 |
| `Persister.persist` 失败（vault 预算/解码拒绝、registerRow DB 故障） | 原样穿透至装配边界 → 降级为无预览提交 + 有界诊断 | JVM 测试已钉"登记失败穿透、半截预览不可提交"（`TutorVisualPreviewAssetPersisterTest.kt:81-98`）；降级发生在**persist+register 两步整体之外**，不会把半截状态传给快照 |
| persist 成功后、快照提交前失败 | 已登记的资产行成为**未引用**行 → 六站点谓词判定孤儿 → 既有 GC 回收 | `CanonicalAssetReferenceSites.kt:31-54`（第六分支）——降级路径**自愈，无泄漏** |
| `CancellationException` | **重抛**，不折降级 | 取消不是超限，吞掉会把取消伪装成"预览不可用" |
| 位图分配 OOM（门漏网时） | 兜底 catch `OutOfMemoryError` → 折为有界拒绝（同 vault `:238-242` 的处置形态） | 门在前（2.4）+ 兜底在后，两层都不让 OOM 变进程死亡 |

**统一降级出口**（装配边界一个 catch 点）：`previewAssetId = null` + 有界诊断（进诊断面、不进显示，码沿
`BITMAP_BYTE_BUDGET`/`LAYOUT_PIXEL_BUDGET`/`PAINTER_*` 族）。快照提交本身**永不因预览失败而失败**——
反例推演：若改为"预览失败 → 提交失败"，则一个显示缓存的字节预算问题能阻断用户拿到完整图（消息/program/
快照一起不成立），把增强物提升成了完整性依赖，方向性错误。

### 2.4 16 MiB 门的分配前拒绝（接线形状与定界策略）

**执行点**（新预览渲染器内，唯一 createBitmap 前）：

```kotlin
val widthPx = ceil(canvasWidthDu * pxPerDuPreview).toInt()
val heightPx = ceil(canvasHeightDu * pxPerDuPreview).toInt()
// 先门后分配——VisualLayoutBudget.kt:108-128 的约定："分配不发生"
VisualLayoutBudget.requireBitmapBytesWithinBudget(widthPx, heightPx, VisualLayoutBudget.ARGB_8888_BYTES_PER_PIXEL)
val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
```

**定界策略（预览 pxPerDu 不取设备 density）**：`pxPerDu_preview = min(1f, PREVIEW_EDGE_PX / max(wDu, hDu))`。

- **硬上界 2048 px**：2048²×4 = 16,777,216 B = 恰达 16 MiB（≤ 门放行）。2049 px 即越——策略上限任何写法
  都不得越过 2048，且**运行时真数仍过门**（策略是第一道、门是第二道；改策略忘了门，门兜底）。
- **建议策略值 1600 px**（沿仓内消费方既有分辨率上限先例 `BoundedLocalImage.MAX_PREVIEW_EDGE = 1_600`，
  `core/ui/.../BoundedLocalImage.kt:72`；同族先例 PDF 重绘 2_000）：1600²×4 = 10.24 MB，对 16 MiB 门留
  36% 余量；③档显示在手机屏上 1600 px 长边已超屏宽。1600² du = 2.56 MP ≤ 4 MP，给 §2.5 的"文字+预览
  合计"留余量。**裁定倾向 1600**，实施批可调常量，但 ≤ 2048 是不可越线（越线 = 策略缺陷，门会兜住但每
  次触发都是回归）。
- 与 vault 门自洽（预览 PNG 的下游闸）：2048² = 4.19 MP ≤ vault `MAX_PIXELS = 16,000,000`（`AndroidCanonicalAssetVault.kt:479`）、
  边长 ≤ `MAX_DIMENSION = 8_192`、PNG 字节（线条图远小于原始位图）≤ `MAX_CLEAN_INPUT_BYTES = 24 MiB`。
  满幅 4096² 未缩采样预览 = 16.7 MP 会**同时**越 16 MiB 门（64 MB）与 vault 像素门——两闸互为印证。
- **JS 侧不镜像**（`VisualLayoutBudget` KDoc 已裁定）：预览是宿主分配事实，JS 不分配位图。

### 2.5 "文字 artifact 与预览的累计 ≤ 4 MP"的并入机制（N7 的生产形态）

预览 du 面积 = `ceil(viewportWidthDu) × ceil(viewportHeightDu)`（预览覆盖整幅画布，du 域不因 px 缩采样而变）。

- **为什么不能在引擎结算处并入**：引擎结算（`QuickJsVisualExecutionEngine.kt:418-421`）越界折
  INVALID_PROGRAM = **整张图作废**。若把预览面积并入那里，4096² 合法画布（快照输入 require 允许
  `TutorVisualCommitRepository.kt:112-117`）会因"预览放不下"被连坐杀掉整张图——显示缓存的存在性问题
  折成了程序无效，与 2.3 的降级语义直接矛盾。**裁定：并入判在预览生成侧，拒绝只作用于预览。**
- **度量总数怎么到预览侧**（`Rendered` 不携带、core/domain 热区禁改）：两条通道，实施批按接线深度二选一：
  - **通道甲（零 core/domain 改动，先落）**：预览渲染缝接受 `measuredEntries: List<VisualMeasuredContent>`（public 类型，
    `VisualLayoutBudgetTest.kt:54-61` 同构构造）。装配点有度量器在手（度量器是装配注入引擎的，`VisualContentMeasuring.kt`
    在 app/src/main）——生产接线落地时装配自然能传；当前传不了就传 `null`，累计退化为"预览独判 ≤ 4 MP"
    （必要条件仍强制），**合并语义如实登记为待接线**。
  - **通道乙（生产全量）**：core:data 侧给度量器套 recording 装饰器（同模块、不改 core/domain 端口签名），
    装配读最后一次批次的 du 总数传给预览缝。**并发注意**：同一引擎并发事务会交错——装饰器须按批次键
    （programId+sceneRevision）或仅在事务串行的装配下使用；实施批若采用乙，先证引擎执行串行性
    （**UNVERIFIED**，本会话未读 jsDispatcher 并发模型）。
- 预览侧判定函数建议复用 `transactionBudgetDiagnostic(measured + previewBox)` 的同族形状（新纯函数
  `previewAdmissionDiagnostic(viewportDu, measuredEntries)`，返回 null=准入 / 码=拒绝），常量与口径同源
  `VisualLayoutBudget`，不另立数字。

### 2.6 触面枚举（改一处先闭合；热区现状为本会话 `git status` 实读）

| 触面 | 现状 | 动作 |
|---|---|---|
| `core/data/.../visual/TutorVisualPreviewRenderer.kt`（建议名） | 不存在 | **新增**：internal 渲染器 + 公共工厂函数（沿 `FigureGenerationCoordinationFactory` public-缝/internal-机 先例）。**消灭的具体失败**：调点自拼"解析→画→门→压缩→persist→登记"六步，漏门=OOM、漏登记=FK 拒；收成一次调用 |
| `QuickJsVisualExecutionEngine.kt` | **M（他线）** | 本设计**零编辑**（2.5 已裁定不并入引擎结算）；如实施批触碰须整文件纳入并登记 |
| `DefaultFigureGenerationCoordinator.kt` | **M（他线）** | 生产装配点（引擎/coordinator 接线）在他线轨道；本批不动，预览缝在装配落地时插入 |
| `core/domain`（Rendered 增字段） | 热区开工即脏 | **禁改**；通道甲规避 |
| `VisualSnapshotRestoreReadSide.kt` | ??（他线新文件） | 只读消费，零编辑 |
| 设备绿源快照 | — | core:data main 新文件 → 快照变化 → 绑定套件按纪律复跑；新增用例必须重跑 |

### 2.7 预览渲染器自己的 R-04 暴露面（提前写死）

生产 commit 路径接通**之前**，预览渲染器与 `VisualChainTestSeams` 同处境：只被 androidTest 引用 → 主包 R8
可收缩 → release 轨设备例悬空 CNFE（批次 35 K6 机制②同型，见批次 38 T10 文档 §2.3）。处置沿该节二选一：
显式 keep（`app/r04-test-keep.pro` 在树）或 Keeper 自动推断。**在该前提落地前，涉及预览渲染器的 release 轨
设备例不得进 R-04 清单**——登记项，不是可选项。debug 轨 `minifyEnabled=false` 不受影响。

---

## 3. 任务二：D-04 设备向量 N5/N6/N7 判据（R2 §4.3 设备半边落地）

**测试位裁定**：core:data androidTest **同模块可达 internal**（painter/解析器/渲染器直接引用，零 seam）；
"真引擎+真度量器+真提交"的全程形态归 app V-01 链套件（§3.4，与 T3 seam 共乘）。R2 提的
`NativeContentLayoutInstrumentedTest` 名义测试位属于 app 度量器（`VisualContentMeasuring.kt` 在 app/src/main），
N5/N6/N7 是预览分配向量、主体在 core:data——两个测试位并存不冲突，登记清楚分工。

**先红后绿**：渲染器未落时三个向量编译不过（引用缺失）即红；本环境零 Gradle，执行状态一律 **UNVERIFIED**
（归门禁串行 + 设备轨）。

### 3.1 N5——满幅预览拒绝（du 累计口径）

| 项 | 判据 |
|---|---|
| 触发 | 快照 viewport = 4096×4096 du（合法画布上界，`MAX_CANVAS_EDGE_DU`）；预览按 2.4 策略产生 → 预览 du 面积 = 16,777,216 du² = 4× 预算（参照数：2048² 恰 = 4,194,304，R2 §4.2） |
| 预期 | 预览**有界拒绝**，诊断前缀 `LAYOUT_PIXEL_BUDGET`（2.5 同族）；**零位图分配**（拒绝发生在 createBitmap 前）；快照照常提交：`previewAssetId = null`、status=LIVE；恢复判定走 ③档 ReadOnly（空清单默认），显示退文字 |
| 断言位 | ① 拒绝码前缀；② 快照行 LIVE 且 preview_asset_id IS NULL；③ 过程无 `OutOfMemoryError`；④（可选加强）预览函数返回有界诊断对象供装配写诊断面 |
| 防漂移 | 若有人"修"成静默缩采样满幅预览：断言②（previewAssetId 仍 null）与码断言同时红——不截断成"正确图"的契约钉子 |

### 3.2 N6——createBitmap 分配前拒绝（字节口径）

| 项 | 判据 |
|---|---|
| 触发 | 驱动预览缝产生 2050×2050 px ARGB_8888 请求（= 16,810,000 B > 16 MiB，参照数同 JVM 例 `VisualLayoutBudgetTest.kt:138-147`）。因生产策略上限 ≤ 2048 不可能自然产生，设备向量**显式以越界入参调门（或以注入 pxPerDu 越过策略）**——向量对象是**门**，不是策略 |
| 预期 | `IllegalArgumentException`、消息前缀 `BITMAP_BYTE_BUDGET`、**分配不发生**（无 OOM——门的全部意义）；无任何位图句柄存活 |
| 断言位 | ① 异常类型+码前缀（逐字对齐 JVM 半边）；② 无 `OutOfMemoryError`；③（真机加强）向量后进程可继续执行一次小分配（证明没有内存被打爆的隐性污染） |
| 与 N5 分界 | N5 是 du 域**累计**门（内容预算语义），N6 是 px×B/px **单次分配**门（内存事实语义）——两门独立判，向量分开写、不得合并（`VisualLayoutBudgetTest.kt:42-50` 的"防发现等价"断言在设备半边的镜像） |

### 3.3 N7——大预览 + 多条文本合计越界（"文字与预览合计"语义）

| 项 | 判据 |
|---|---|
| 触发（最小形） | viewport = 2048×2048 du：预览独判 = 4,194,304 du² **恰达不拒**（≤ 语义）→ 追加 ≥1 条真实度量文本盒（合成 `VisualMeasuredContent` 条目，public 构造）合计 > 4 MP → 拒 |
| 触发（全程形，app 链） | 真引擎第二次执行得 Rendered + 真度量器条目 → 预览缝收真实 entries → 合计越界拒（该形随 §4 seam 的链套件增量落地） |
| 预期 | 诊断前缀 `LAYOUT_PIXEL_BUDGET`；拒绝只作用于预览；快照照常提交（同 N5 断言②） |
| 断言位 | ① 码前缀；② 恰达（仅预览、无文本）不拒的对照半例（"≤"语义的设备钉）；③ 快照完整 |
| 度量真值来源 | 通道甲（2.5）：app androidTest 用真度量器；core:data androidTest 用合成条目（`VisualMeasuredContent` 为 core/domain public 类型，core:data 测试可构造） |

### 3.4 向量与套件归属汇总

| 向量 | 测试位 | 可达性依赖 |
|---|---|---|
| N5 / N6 / N7（最小形） | core/data androidTest 新套件（预览分配向量） | 同模块 internal 直达，**零 seam**；随预览渲染器落码同批 |
| 全程形（真引擎→真提交→预览降级→重开→③档文字回退） | app `V01VisualChainInstrumentedTest`（3→4 例） | 需 §4 的 seam（或生产装配先行）；与 V-01④ 增量**同一批落地最经济** |

---

## 4. 任务三：V-01④ PDF 腿合规暴露 seam——复核、收口与回滚（T3）

### 4.1 事实复核（批次 38 T10 前提逐条本会话重验，全部仍成立）

| 前提 | 本会话复核 |
|---|---|
| 7 类 core:data internal 符号不可达 app androidTest | grep 实跑：`VisualDrawOpsPdfPainter.kt:55`、`VisualSnapshotRestore.kt:181`、`VisualDrawOpsParser.kt:50`、`VisualDrawOps.kt:165`、`VisualDrawOpsPainterPure.kt:87,194`、`VisualRendererVersion.kt:20` 全部仍 `internal` |
| `VisualChainTestSeams` 不存在 | grep = 0 命中 |
| `testFixtures` 构建面缺席 | `core/data/build.gradle.kts` / `app/build.gradle.kts` / `settings.gradle.kts` grep `testFixtures` = ABSENT（本会话实跑） |
| app 套件 3 例 / core:data 变体 4 例 | `grep -c "@Test"` 实跑：3 / 4 |
| keep 先例文件在树 | `app/r04-test-keep.pro` 存在（`ls app/*.pro` 实跑） |

### 4.2 取舍收口（公开测试缝 vs 模块调整）

维持批次 38 推荐 **方案 A（单新文件公开测试缝）**，本轮补充三条决策输入（其中一条反向排除 B 的变体）：

1. **androidx 权威先例反向支持 A 的"薄"**：androidx 对"把内部机制交给测试"的答案不是翻转可见性，而是
   **独立 testing artifact + 公共 seam 接口**（`androidx.work.testing.TestDriver`，`androidx/androidx` 仓
   `work/work-testing/src/main/java/androidx/work/testing/TestDriver.java`，本会话 gh 实查）。但其"独立模块"
   形态在本仓需要 settings.gradle.kts 增模块——**热区**（R-04/Keeper 推迟中）→ B 的模块级变体在本批结构性
   不可行；A 是"同模块公开缝"里触面最小的一档（+1 新文件、零既有文件编辑），与 androidx 的语义同构
   （公开缝收窄为显式枚举的少量 opaque 操作，不透出 internal 类型）。
2. **本批 T2 向量把 A 的必要性再收窄**：N5/N6/N7 最小形**不需要** seam（core:data androidTest 同模块可达）。
   A 缝的不可替代面只剩 app 链的 ④ 腿真值（真隔离引擎产出 DrawOps → restore → PDF 全腿 + 全程预览形）。
   实施批若排序紧张，④ 缝可让位于 T2 最小形向量，不阻塞 N 判据落地。
3. **预览渲染器与缝的 keep 义务合并管理**：预览渲染器在生产 commit 接通前同样 test-only（§2.7）——两处
   release 轨暴露面在 `r04-test-keep.pro`/K4 门里**一并登记**，避免两个批各自加行造成门脚本漂移。

**方案 B（testFixtures）仍 UNVERIFIED**（AGP 9.3 × Kotlin 2.3.21 friend-path，U1 未变）；**方案 C（可见性
翻转）维持不推荐**（永久扩 7-10 个领域类型公开面，且与"新增必须指认消灭的失败"反着来——C 消灭的失败
与 A 相同，但公开面代价大 7-10 倍）。

### 4.3 缝签名（沿批次 38 §2.1，本会话仅补两处实施约束）

- 缝内转调点即本会话实读的三个入口：`VisualSnapshotRestore.restore(...)`（判定序 `:181` object）、
  `VisualDrawOpsParser.parse` + `VisualDrawOpsPdfPainter.writeFile`（`VisualDrawOpsPdfPainter.kt:55`）。
- **约束补丁 1**：缝 KDoc 必须写死"仅 androidTest 引用"grep 门（批次 38 建议的 `tools/ci/check_visual_test_seams.py`
  同型）——它同时是 §3 的回滚净度机检。
- **约束补丁 2**：④ 例断言全部只用缝公共返回值 + `PdfRenderer` 公共 API + 像素数组，不得经由缝把
  `VisualDrawOpsDocument` 等 internal 类型透出（Kotlin public 签名不得暴露 internal 类型的语言规则，
  批次 38 已核；本会话未另行取官方引文）。

### 4.4 回滚（三段式，与批次 38 §3 一致，本会话按当前树再核）

| 单元 | 动作 | 净度 |
|---|---|---|
| 主回滚 | 删 `VisualChainTestSeams.kt`（1 新文件）+ 还原 app 套件增量（3→4 例回 3）+ 删可选 keep 行/门脚本行 | 生产代码零触碰（缝无 src/main 引用——grep 门即机检）；零 schema/契约/DB 影响 |
| 触发（失败归因） | app ④ 设备红且两轮修复无法归因夹具/环境 → 归因缝语义 → 阻塞登记回主会话，变体继续承担，V-01 维持 PARTIAL 3/4 | 不删变体、不改判据 |
| 中间态 | 只回滚 keep 行（K4 门红）不影响缝与用例；只回滚用例则缝成孤儿（grep 门红提醒一并处理） | 实施批二选一登记 |

---

## 5. 任务四：GitHub/官方参考检索与评分

> 检索执行：`gh api repos/{...}`（元数据：license/stars/pushed，本会话实跑）；官方文档经 web 渠道实取
> （Bitmap.Config 页整页到手）。检索通道故障记录：`developer.android.com` 首选 WebFetch 通道连接超时 →
> 换 web-reader 通道成功（纪律"单通道失败换通道"执行）；`gh search repos "android bitmap downsample preview"`
> 与 `gh search code "TestDriver workmanager-testing"` 零命中 → 换 `gh api` 直查与 `gh api search/code` 成功。
> 每缺口 ≤ 3 轮关键词，未超。

| # | 参考 | 元数据（gh 实查） | 对本设计的复用点 | 评分 | 采纳裁定 |
|---|---|---|---|---|---|
| R1 | **coil-kt/coil** | Apache-2.0；11,956★；pushed 2026-10-31；未归档 | "请求有界尺寸、`Size.ORIGINAL` 需显式 opt-in"的定界策略形态——与 2.4"预览自定界 + 门兜底"同构；全 Kotlin、与仓技术栈同族 | **4.5/5** | **策略参考采纳**（思想级，零代码搬运；本仓常量/门全部仓内同源） |
| R2 | **bumptech/glide** | license NOASSERTION（BSD 式/AOSP 系）；35,008★；pushed 2026-10-31 | `DownsampleStrategy`/`SIZE_ORIGINAL` 警示的同族先例；位图字节会计与重采样成熟度 | 4/5 | 同 R1 备选；license spdx 标注含糊（NOASSERTION）故排 R1 后 |
| R3 | **androidx/androidx `work-testing` `TestDriver`** | Google/Apache-2.0（androidx 仓内 `work/work-testing/.../TestDriver.java`，gh contents 实查到路径） | "公开测试缝"权威形态：显式枚举的公共 seam 接口、独立 artifact、不透内部类型——方案 A 的语义模板 | **5/5（形态权威）** | 采纳其**形态**；其**独立模块**落地形态需 settings.gradle.kts（热区）→ 不搬 |
| R4 | **davemorrissey/subsampling-scale-image-view** | Apache-2.0；8,009★；**pushed 2024-04-22（维护趋冷）** | 大图分块渲染（tile）——"满幅预览放不下"的另一解 | 2/5 | **考虑后不采纳**：预览是 ≤1600px 有界小栅格不是可缩放大图；维护度不达标；引入依赖与仓红线（零新增依赖取向）冲突 |
| R5 | **官方 Bitmap.Config API 参考** | developer.android.com，Last updated 2026-08-03（本会话整页实取） | **ARGB_8888 "Each pixel is stored on 4 bytes"**（逐字）；RGB_565=2B/px、RGBA_F16=8B/px、ALPHA_8=1B/px | 权威 | **采纳**——关闭 R2 §6 待验证项（`VisualLayoutBudget.ARGB_8888_BYTES_PER_PIXEL=4` 的官方根据） |
| R6 | Kotlin `internal` 可见性规则（跨编译单元不可见/friend path） | 语言规则 | 缝机制的根据 | — | 批次 38 已核 + 仓内编译事实（app 套件镜像常量）双重支撑；本轮未另取官方引文（如实登记） |

**GitHub 优先纪律自检**：三个候选均核 license/维护度/兼容性；无一处"自己造别人已解决的东西"——本设计的
门/常量/预算器**仓内已落**（批次 38），外部参考只用于策略形态与先例佐证，零代码搬运、零新增依赖。

---

## 6. 未验证项与登记

| # | 项 | 状态 |
|---|---|---|
| U1 | N5/N6/N7 设备执行结果（门真机触发、无 OOM 实测） | **UNVERIFIED**——零 Gradle/零设备纪律；归门禁 + 设备轨 |
| U2 | 预览渲染器缝签名编译可行性（label DTO 折算 helper 与 replay 批共享形状） | 设计稿，未编译 |
| U3 | 通道乙（度量器 recording 装饰器）的引擎并发模型（jsDispatcher 串行性） | **UNVERIFIED**，本会话未读；采乙前必证 |
| U4 | R8 对预览渲染器 + 缝 + painter 家族的收缩边界（keep 行精确形状） | **UNVERIFIED**（需 K2 产物核，随 R-04 批） |
| U5 | 1600 px vs 2048 px 策略值的③档显示观感裁定 | 设计倾向 1600（先例+余量），显示裁定归主会话 |
| U6 | `MobileDebug` 面 `BoundedLocalImage.MAX_PREVIEW_EDGE = 1_600` 行号在 core/ui（热区）——本会话 grep 命中于 `.worktrees` 镜像路径，主树行号未复核 | 先例引用标注**待实施批在主树复核** |

## 7. 来源（本会话实读/实跑/实取）

**仓内（行号均为本会话实读）**：计划 `.zcode/plans/plan-2026-11-01-batch-39-wiring-calibration.md`；
`TutorVisualPreviewAssetPersister.kt:31-85`；`TutorVisualCommitRepository.kt:80-130,298-339,112-117`；
`VisualLayoutBudget.kt:45-133`；`VisualLayoutBudgetTest.kt:11-24,42-50,132-158`；
`TutorVisualPreviewAssetPersisterTest.kt:12-24,59-98`；`QuickJsVisualExecutionEngine.kt:360-470`；
`VisualDrawOpsPainter.kt:67-92`；`VisualDrawOpsPainterRendered.kt:23-38`；`VisualDrawOpsPainterPure.kt:185-228`；
`VisualMeasuredLayoutArtifact.kt`（KDoc 消费表）；`VisualSnapshotRestore.kt:25-180`；
`VisualSnapshotRestoreReadSide.kt:1-60`；`CanonicalAssetReferenceSites.kt:1-74`；`StudyDbValue.kt:58`；
`DatabaseContract.kt:135`；`AndroidCanonicalAssetVault.kt:181-246,470-500`；`PdfBatchImportPageRenderer.kt:62`；
`SmartMistakeBookApplication.kt:330-389`；`DefaultFigureGenerationCoordinator.kt:140-276`；
`core/domain/VisualExecutionEngine.kt:225-284`；`app/.../visualjs/V01VisualChainInstrumentedTest.kt:280-470`；
`ExportPdfWorker.kt:1-40`（仅辨热区归属，与本设计无关）；
`docs/research/2026-11-01-v01-pdf-leg-exposure-design.md`（全文）；`docs/research/2026-11-01-measure-artifact-and-budget.md`
§4（全文）；`docs/research/2026-11-01-batch-38-record.md`（D-04/M-03/登记 #17 段）；
`docs/research/2026-11-01-preview-source-evidence.md`（D-04 缺口三段）。

**命令实跑**：`git status --short`（热区脏面，§2.6）；grep 组（internal 符号面/testFixtures/@Test 计数/main 零调用方）；
`gh api repos/coil-kt/coil`、`repos/bumptech/glide`、`repos/davemorrissey/subsampling-scale-image-view`、
`repos/androidx/androidx/contents/...`（404 后换 `gh api search/code` 命中 `work/work-testing/.../TestDriver.java`）。

**外部（URL）**：
- https://developer.android.com/reference/android/graphics/Bitmap.Config （ARGB_8888=4 B/px 逐字引文，2026-08-03 版）
- https://github.com/coil-kt/coil
- https://github.com/bumptech/glide
- https://github.com/androidx/androidx （`work/work-testing/src/main/java/androidx/work/testing/TestDriver.java`）
- https://github.com/davemorrissey/subsampling-scale-image-view

**共享纪律执行记录**：零 git 写、零 Gradle、零设备命令、零热区编辑；本文为 docs/research 下本线新文件；
失败未遇（无需归因）；新增指认——预览渲染器消灭"调点六步自拼漏步"（§2.6）、N 向量消灭"分配前门无设备执行面"
（§3）、缝消灭"④ 腿跨模块不可达"（§4，沿批次 38 指认）。
