# 4E.2 准入门（2026-10-26）：隔离门全绿后，JS 距离“可作为方法被广告”还差什么

> 性质：**只研究、不实现**。本报告没有运行 Gradle、没有跑设备/仪器化用例、没有 git 写操作、没有改生产代码；结论来自当前工作树的计划、契约、批次记录与源码只读核对。凡“设备绿/红”均引用已有 E-file，未在本轮复跑。
>
> 前提提醒：**隔离门现在并非全绿**。批次 23 设备证据是 5 例 4 绿 1 红（`theClientRebuildsAtMostOnceAfterConfirmedAbandons` 红，E-01b/E-01e）；批次 24 T1 已在工作树改 `abandon()` 与冷启动首连预算，但**设备复跑 UNVERIFIED**（`docs/known-defects.md:2598-2604`）。本报告的“隔离门全绿后”是条件句，不是现状陈述。
>
> 基线：计划给定 HEAD `5bc8bdf8`；本报告只读核对时 `git rev-parse --short HEAD` = `5bc8bdf8`，工作树 690 项改动（217 M + 473 ??，含他线在飞）。本批落点（`app/.../visualjs`、`core/data/visual`）开工即脏：`VisualJsClientEngine.kt`、`VisualJsIsolationInstrumentedTest.kt`、`QuickJsVisualExecutionEngine.kt`、`VisualJsFailureClassification.kt` 有批次 24 在飞改动。
>
> **在飞口径差异（低）**：KD-66 Progress 写“重建保持 5_000 ms / 死亡确认 1 s”（`docs/known-defects.md:2601`），当前工作树是 `RECONNECT_TIMEOUT_MILLIS=10_000`、`DEATH_CONFIRM_TIMEOUT_MILLIS=2_000`（`app/.../visualjs/VisualJsClientEngine.kt:883`、`:890`）。这是批次 24 在飞记录与代码的漂移，不影响本清单的准入门判据；提交前应统一文本与常量。

## 0. 结论摘要

1. **隔离门全绿只是“能跑”的入口，不是“能广告”的证明。** 当前唯一跑通的是隔离进程里的**纯几何程序**（`prepare` 返回空批次、`render` 返回空路径），文本/公式/标签一律被拒；场景 JSON 也没有类型化校验、没有回放、没有持久化、没有导出。
2. **六项必需项里只有“策略/类型骨架”部分落**：`FigureGenerationCapabilities`/`FigureGenerationPolicy`/`FigureArtifact.Program` 已存在；SceneV1 编译器、真实内容度量、交互/表单、保真验证、双后端执行均为 **0 落或仅硬关闭的占位**。
3. **六项之外还有五个横切缺口**：生产装配（无 `AndroidVisualExecutionEngine`、无 `core:visual-js`）、JS 持久化（无 program/snapshot/message 表）、回放/导出（无 painter、无 `VisualSnapshotPdfBlock`）、源证据（`TutorQuestionContext` 无原题资产）、发布矩阵/真实 Provider（API 23/arm64 运行时、arm64 16 KB、release R8、真实质量均未验）。
4. **最小可广告子集建议：只广告“静态 SceneV1”**——无 `reduce`、无原生控件/滑块、无表单、无动画、无 SVG；交互/表单整体后置到 4E.2，复杂源/保真扩展后置到 4E.3。但**静态子集也必须先补齐编译器、度量、painter、持久化、导出、双后端与质量门**；以当前代码，静态子集同样**不具备广告条件**。
5. **不要广告的“半步”**：纯几何程序能跑、或 `VisualExecutionEngine` 端口契约用例绿、或手写 JS 能渲染出一张图，都不能作为“JS 可用”的证据。契约原文：未过方法/能力不广告，不能把文档正确当软件已接通（`docs/superpowers/plans/2026-10-18-flexible-js-drawing-implementation.md:172-176`）。

---

## 一、必需项与当前状态

### 1.0 总表

| # | 必需项 | 当前状态 | 对“广告 JS”的判定 | 关键证据 |
|---|---|---|---|---|
| 1 | SceneV1 编译器（含 SceneV1/RenderScene DTO、validator、compiler、state codec、SDK、painter） | **未落（0）** | **硬阻塞** | 计划 `:93-101`；契约 `:94-104`；源码零命中（附录 A #1/#9） |
| 2 | 内容度量（真实 text/math 字形/公式布局） | **部分落**：端口/预算/假度量已有；真实度量 0 | **硬阻塞** | `VisualExecutionEngine.kt:149-215`；`VisualJsProtocol.kt:394-406`；`VisualJsService.kt:310` |
| 3 | 交互图/滑块/表单（reduce、原生控件、NativeTutorFormV1、动画/命中、SVG） | **未落（0）** | MVA 可后置；全量 4E.2 硬阻塞 | 计划 `:20`、`:142-147`、`:151`、`:161`；契约 `:125-141` |
| 4 | 保真门（fidelity 目标、像素保真拒绝、语义/结构质量验证） | **部分落**：档位/不降级校验已有；验证/质量门 0；JS 像素保真拒绝缺失 | **硬阻塞** | `FigureGeneration.kt:97-126`；`FigureGenerationPolicy.kt:170-178`、`:185-219`；设计 `:91` |
| 5 | 能力快照/提示词广告（method 级能力、工具 schema、description、prompt 正文） | **部分落（硬关闭）**：骨架已有，JS 在所有广告面被拒 | **硬阻塞** | `Capabilities.kt:47-132`；`FigurePromptAssembler.kt:40-45`、`:70-73`；`OpenAiModelProtocol.kt:443-459` |
| 6 | 双后端配对（同一 operation、模型选方法、JS 执行、切换/预算/审计） | **部分落**：类型/policy 骨架已有；执行 0 | **硬阻塞** | `DefaultFigureGenerationCoordinator.kt:125-134`、`:182-190`；`TutorToolLoop.kt:343-349`；`TutorFigureMethodDualParseTest.kt:208-222` |

> 状态口径：“已落”= 代码在当前树且有可执行的测试/证据；“部分落”= 类型/骨架/单侧实现存在，但广告所依赖的真实路径不可用；“未落”= 源码零命中或只有注释/计划文字。

### 1.1 SceneV1 编译器（含回放）

**契约要求。** 计划把这一整块放在 4E 批次二：

- `core:model` 新增 `VisualProgram.kt`、`VisualScene.kt`、`VisualSceneValidator.kt`、`VisualSceneCompiler.kt`、`VisualStateCodec.kt`；`ValidatedScene` 只能由 validator 产生（计划 `:93`）。
- `core:visual-js/assets/visual-sdk-v1.js` 提供 `prepare/render/reduce` 驱动及 `line/arrow/circle/arc/axes/polyline` 等纯帮助函数；基础结果仅 `group/path/label`（计划 `:94`）。
- 严格拒绝未知字段、重复 JSON 键/ID、原型污染键、非法引用、NaN/Infinity、越界、过深树、超字节预算；展开回放也计路径预算（计划 `:95`；契约 `:90`）。
- `core:visual` 新增 `AndroidContentLayout.kt`、`MathContentLowering.kt`、`AndroidScenePainter.kt`、`SceneSvgWriter.kt`；一次 prepare 返回完整批量内容，render 只引用冻结 `contentId`；屏幕与 PDF 用同一 painter 回放（计划 `:96-97`；契约 `:102`）。
- `RenderScene` 是校验后的不可变显示列表，不能含函数/脚本/回调（契约 `:102`）。

**当前状态：未落（0）。**

- 上述五个 `core:model` 文件全部不存在；`grep -rn "class VisualSceneCompiler\|class VisualSceneValidator\|class VisualStateCodec\|class RenderScene\|class ValidatedScene"` → **0**（附录 A #1）。
- 不存在 `core:visual` 或 `core:visual-js` Gradle 模块：`settings.gradle.kts:19-33` 只 include app、core 六模块、feature 五模块与 benchmark；契约 §5.1 也把“不存在 `core:visual`/`core:visual-js`/`AndroidScenePainter`”列为现状（`docs/current/visual-generation-contract.md:110-119`）。
- `AndroidContentLayout`、`MathContentLowering`、`AndroidScenePainter`、`SceneSvgWriter` 类全部零命中（附录 A #9）。
- 无 SDK 资产：`app/src/main/assets` 只有 3 个开源许可文本，无 `.js`；`find ... -name "visual-sdk*"` → 0（附录 A #4）。引擎的 bootstrap 只做两件事——封闭 `Date`、用种子 PRNG 替换 `Math.random`（`core/data/.../visual/QuickJsVisualExecutionEngine.kt:809-814`），没有提供任何绘图帮助函数。
- 引擎只做**保守输出校验**：字节预算 → JSON 可解析 → 扫描 `contentId` 是否属于本事务冻结集合（`QuickJsVisualExecutionEngine.kt:647-684`）。KDoc 明说“完整 SceneV1 严格校验归 4E.2 共享 compiler，这里只做可观测的保守面”（`:69`、`:643-645`）。
- 因此 `render` 返回 `{}` 也会被当作 `Rendered`（`VisualExecutionResult.Rendered` 只要求 `sceneJson` 非空、版本 ≥1，见 `core/domain/.../VisualExecutionEngine.kt:218-235`）。**“引擎接受”不等于“场景可绘制”**。
- 没有任何显示列表消费者：`core/ui` 无 `VisualSceneCard`/`VisualControls`/`VisualSemantics`（附录 A #3），`core/export` 无 `VisualSnapshotPdfBlock`（附录 A #6），feature/tutor 对 `VisualScene`/`visualId` 零引用。

**对广告的影响：硬阻塞。** 模型即使产出一段合法 JSON，也没有类型化校验保证它能被 painter 画出来；没有 painter/显示列表，UI 与 PDF 都无处消费；没有 SDK，模型只能手写路径命令，和“通用绘图方法”的产品承诺不一致。

### 1.2 内容度量（真实 text/math）

**契约要求。** 每次事务：新上下文 `prepare(input)` → 原生验证并度量 text/math（一次批量）→ 保留不可变布局 artifact（宽、高、baseline、inkBounds、实际字号、内容摘要）→ 新上下文 `render(input, metrics)`，且只能引用本事务 `contentId`（契约 `:47-58`）。预算：内容请求 ≤128、总字符 ≤6000（契约 `:82`；`VisualExecutionEngine.kt:131-135`）。

**当前状态：端口/预算已落；真实度量未落。**

- 端口层已落：`VisualContentMeasurer`、`VisualContentMeasureBatch`、`VisualContentMeasureEntry`、`VisualContentMeasureResult`、`VisualMeasuredContent` 都在 `core/domain/.../VisualExecutionEngine.kt:149-215`；批次/条目构造器就地强制 128 条 / 6000 字符（`:154-179`）。
- 但 `VisualMeasuredContent` **没有 `inkBounds`**，KDoc 也承认“inkBounds 的完整矩形随批次二类型化布局落地，本端口先携带标量度量”（`VisualExecutionEngine.kt:196-197`）。契约要求的是完整矩形（`:53`）。
- 隔离服务注入的是 **`UnwiredVisualContentMeasurer`**：`measure()` 对空批次返回空，对任何非空内容请求抛 `VisualMeasurementUnavailableException`（`app/src/main/kotlin/.../visualjs/VisualJsProtocol.kt:394-406`）；服务构造引擎时直接传它（`VisualJsService.kt:310`）。
- 引擎把度量异常折成 `INVALID_PROGRAM`（`QuickJsVisualExecutionEngine.kt:336-345`）。也就是说：**隔离进程里任何带文字的图都会失败**，只有 `prepare` 返回 `[]` 的纯几何程序能过。
- 批次 22 复核已把这一点登记为低风险：`UnwiredVisualContentMeasurer` 把“服务未接线”折成 `INVALID_PROGRAM`，4E.2 接线前会误导诊断（`docs/research/2026-10-26-batch-22-record.md:232`、`:251`）。广告 JS 后，这类诊断会直接回喂模型，模型可能反复修 program 而永远修不好。
- 仪器化引擎用例用的是 `CapturingMeasurer`/`GatedMeasurer` 假度量（`core/data/src/androidTest/.../QuickJsVisualExecutionEngineInstrumentedTest.kt:84-110`），只证明“度量被调用一次、结果被注入 render”，**没有证明真实字形/公式路径**。
- 公式 parser/AST 已有可复用资产（`core/model/.../MathBudget.kt`，契约 `:123` 明确“parser/AST/budget 可复用”），但没有 `MathContentLowering`，也没有真实字形度量。

**对广告的影响：硬阻塞。** 教学图的核心价值是标签、公式、单位、比例标注；没有真实度量，静态图只能画“无字几何”，不足以支撑任何教学用途。技术上可以今天广告“纯几何图”，但那是**不可接受的产品子集**，不应作为 MVA。

### 1.3 交互图/滑块/表单

**契约要求。** 4E.2 的交付边界是：`reduce`、原生控件、`NativeTutorFormV1`、动画与命中语义；附加 SVG（计划 `:20`）。具体：

- `core:ui` 新增 `VisualSceneCard.kt`、`VisualControls.kt`、`VisualSemantics.kt`；Canvas/原生控件分离，range/transport 控件可聚焦，点击与拖动共享命中矩阵，过期事件拒绝（计划 `:142`）。
- native clock 限 10 fps、仅前台播放、最新状态合并；系统禁动画保留步进；控件更新只改局部 state，答题提交走当前题既有讲题事件（计划 `:143`）。
- `core:model/NativeTutorFormV1.kt`、`core:ui/NativeTutorForm.kt`：≤4 题 radioGroup、≤6 选项/题、≤1000 UTF-16 理由 textInput、submit；原生 `AnswerDraft` 与 JS state 分离；form 摘要/会话/revision 匹配后以稳定 `submissionId` 经 `TutorInteractionCommands`/`TutorTurnMessages` 提交真实学生新回合；重复点击/重建去重，程序/模型预填不能伪造学生原话或自动提交（计划 `:145`；契约 `:131-135`）。
- 事件与渲染是不同队列：reduce 单在飞、离散事件有界 FIFO（≤16）、activate/reset/drag-start/drag-end 不合并；只有同一手势的绝对 range 值/drag-move 可合并；按 expectedStateRevision CAS；重复 sequence 不重复应用（契约 `:129`）。
- 草稿独立 `ui_state_json`/`formDigest`/`revision`，500 ms 输入合并/后台保存；旋转用有界 `SavedStateHandle`，重建不自动发送，换题不套用旧答案（计划 `:146`；契约 `:135`）。
- 独立测试：动画 1000 帧 → 0 个逐帧 DB/资产写入；拖动结束 → 一次 checkpoint；进程死亡 → 最近 checkpoint；strictOffline → 本地图交互可用且 0 Provider 派发（计划 `:147`）。

**当前状态：未落（0）。**

- 端口/引擎没有 `reduce`：`grep -c "reduce" QuickJsVisualExecutionEngine.kt` → **0**（附录 A #7）；`VisualExecutionEngine` 端口只有 `execute/close`（`VisualExecutionEngine.kt:40-46`）。
- 没有任何 `SceneV1` DTO，自然也没有 `controls[]`、`hitRegions[]`、`form?` 字段；`grep "NativeTutorForm"` → **0**（附录 A #2）。
- `core/ui` 无 `VisualSceneCard`/`VisualControls`/`VisualSemantics`（附录 A #3）。
- 没有 `tutor_message_visual.ui_state_json`/`formDigest`/`revision` 列（`grep "tutor_visual"` → 0；附录 A #5）。
- `TutorInteractionCommands` 确实存在，但它是**讲题计划选项提交**的命令层（`feature/tutor/.../TutorInteractionCommands.kt:26-60`），不是 4E.2 的原生表单协议，也没有 form 摘要/idempotent submission 语义。
- 没有任何 feature/tutor 对 `VisualScene`/`visualId` 的消费（零命中）。

**对广告的影响：MVA 可后置，但必须显式关闭。** 契约要求“未启用的控件/事件/模式在模型能力上下文中不广告，解析与执行也同时拒绝，不仅隐藏 UI”（契约 `:58`）；`form` 仅在 4E.2 能力开启时允许（`:94`、`:131`）。因此静态 MVA 必须：`supportsParametricInteraction=false`、prompt 不含 reduce/controls/form、SceneV1 validator 对 `controls`/`hitRegions`/`form` 非空直接拒绝。若做不到“解析与执行同时拒绝”，就会变成“广告了静态，模型却塞交互字段”的假成功。

### 1.4 保真门

**契约要求。** 保真不是一句“fidelity 字段”：

- `FigureFidelity` 三档 + `FigureGenerationTarget` 的 `requiresParametricInteraction`/`requiresPixelFidelity`/`requiresCompleteLabels`；pixel-fidelity 目标不能是 `ILLUSTRATIVE`（`FigureGeneration.kt:97-126`）。
- proposal 的 fidelity 不得低于宿主目标（`FigureGenerationPolicy.kt:170-178`）。
- 必需原像素保真时只能走支持的 EDIT 能力（`FigureGeneration.kt:114-115`）；IMAGE_MODEL EDIT 也要求源图字节与 SHA 一致（`FigureGenerationPolicy.kt:240-270`）。
- 质量门：冻结 120 个盲测任务（6 组各 20），首次合格 ≥90%、原预算内最终 ≥95%、每组最终 ≥90%、关键题意错误接受 = 0、越权/超预算/恢复重复外发 = 0；报分母/置信区间/评审来源（`docs/superpowers/specs/2026-10-19-product-readiness-design.md:91`；计划 `:324`；PRD-09 `:133`）。
- 六类截图 fixture + 一个新算法组合样例；结构/语义预期和可读性分别记证，错误受力方向/坐标/条件不算合格（计划 `:154-168`、`:323`）。
- 4E.3 负责“整页/复杂自然图/跨科质量覆盖，必要时声明组合能力”，两方法都依统一源证据和质量规则（计划 `:21`）；按“交互/复杂保真门”推进（`:306`）。

**当前状态：部分落，且有一条真实的 policy 漏洞。**

- 已落：fidelity 枚举、target 位、proposal 不降级校验（`FigureGeneration.kt:97-126`；`FigureGenerationPolicy.kt:170-178`）；IMAGE_MODEL EDIT 的源图/SHA/能力校验（`:240-270`）。
- **未落：JS 分支不检查 `requiresPixelFidelity`。** `validateJsProposal`（`FigureGenerationPolicy.kt:185-219`）只检查“不得带图像载荷 / program 必填非空 / 64 KiB 预算 / 参数交互能力”，没有检查 `request.target.requiresPixelFidelity`；而 IMAGE_MODEL 分支有这条检查（`FigureGenerationPolicy.kt:290-300`）。这意味着一旦广告 JS，模型可以选 JS 去满足一个“要求原像素保真”的目标，而 policy 会放行。当前生产不构造 JS proposal，所以不是现行可利用缺陷；但它是**广告前必须补的负向门**（并加测试）。
- **同一问题的入口侧证据**：`ConfiguredCleanImageGenerator` 的 `PIXEL_CLEANUP` 请求把 `requiresPixelFidelity=false`（`core/data/.../capture/ConfiguredCleanImageGenerator.kt:103-107`）。如果 `PIXEL_CLEANUP` 的业务语义确实是“保留原像素的编辑/去污”（`FigureGeneration.kt:55-56`），这个 target 就应该固定为 true；否则即使补上 JS 分支的 pixel-fidelity 拒绝，这个入口仍不会挡住 JS 语义重建。**广告前必须同时核对 purpose↔target 映射，不能只补 policy 一处。**
- 未落：没有任何质量/保真 evaluator、oracle、120 题集合、盲测记录；`figure_generation_attempt` 只有 method/status/diagnostics 等列，**没有 quality 列**（`core/database/.../entity/FigureGenerationEntities.kt:120-153`）。契约要求的“质量目标/质量”在持久层没有载体（契约 `:161-164`）。
- 未落：六类 fixture 的工程/语义测试零命中（`3F0h/8` 等零命中）；没有任何“独立预期”或“受力方向/坐标核对”。
- **4E.3 在计划里只有一行边界**（`:21`），没有像 4E.2 那样的任务/验收节；`grep "4E.3"` 只命中 `:21`，其余只有“4E.2/3”的合并表述（`:172`、`:306`）。因此“保真扩展”目前没有可执行判据，只有 `:323-325` 的质量门数值和契约 `:202` 的完整门清单。**这不是本报告的推断，是计划文本本身的缺口。**
- 真实 Provider 无凭据：模型质量 UNVERIFIED（`docs/research/2026-10-26-batch-23-record.md:198`；计划 `:305` 明确“无真实配置时保留 UNVERIFIED，fake 成功不等于模型质量通过”）。

**对广告的影响：硬阻塞。** 广告意味着“模型可以选它并相信结果经过了与 IMAGE_MODEL 相同的质量要求”。当前只有声明式 fidelity，没有验证；且 JS 对 pixel-fidelity 目标没有拒绝。要先补 policy 负向门 + 至少静态子集的独立 oracle + 真实 Provider 质量记录，才谈得上“可作为方法”。

### 1.5 能力快照/提示词广告

**契约要求。** 能力上下文只能报告真实已接通的方法；JS 可用时，组装器必须同时提供已实现的 VisualProgram/Scene schema、SDK 函数签名和完整最小样例；只给“支持 JS”不算接入（`docs/current/figure-generation-prompt-policy.md:5`、`:80-84`；契约 `:29-31`、`:58`）。工具 schema、prompt 正文、能力快照、执行端必须同源（`Capabilities.kt:29-45`）。

**当前状态：骨架已落，JS 在所有广告面被硬关闭。**

- **工具级能力快照已落**：`TutorCapabilitySnapshot` 是唯一广告源，工具集只含 `IMPLEMENTED_TOOLS`，`GENERATE_FIGURE` 由 `imageBackendReady` 裁剪（`core/model/.../Capabilities.kt:47-132`；`core/data/.../RoomModelTaskRepository.kt:959`）。
- **但 `imageBackendReady` 是图像后端专用**：`figureGenerator != null && declaredProvider.supportsImageInput`（`RoomModelTaskRepository.kt:959`）。如果只有 JS 运行库可用、图像后端不可用，`GENERATE_FIGURE` 会被整体隐藏——**JS 本地方法无法单独广告**。这是 method 级能力快照缺失的直接后果。
- **method 级能力是硬编码的 `IMAGE_MODEL_ONLY`**，至少 4 处生产/默认值：
  - `core/data/.../model/AttachedImageGenerator.kt:144`
  - `core/data/.../model/TutorFigureGenerator.kt:372`
  - `core/data/.../capture/ConfiguredCleanImageGenerator.kt:95`
  - `core/data/.../model/FigurePromptAssembler.kt:102`（默认参数）
- **提示词组装器对 JS fail-closed**：`FigurePromptAssembler.assemble`/`assembleForTutorTool` 都 `require(FigureGenerationMethod.JS !in capabilities.availableMethods)`（`:40-45`、`:108-113`）；正文还写死“本波次只有图像模型方法；生成/编辑各占一次远程派发”（`:70-73`）。
- **工具 schema 只广告 IMAGE_MODEL**：`OpenAiModelProtocol.kt:443-459` 的 `method.enum` 只有 `IMAGE_MODEL`，没有 `program` 字段；测试 `theNativeSchemaAdvertisesOnlyImageModelAndTheNewPayloadFields` 明确断言 `figureBlock` 不含 `"JS"`（`TutorFigureMethodDualParseTest.kt:255-276`）。
- **工具描述仍是 kind/图像模型口径**：长描述说“本地未配置可用的图像模型时也不会生成”（`TutorToolDescriptions.kt:56-68`）；短描述仍是 `kind=REDRAW_PROBLEM/GENERATE_PROCESS`，没有 method/program（`:89-93`）。
- **`TutorVisualContext` 载体已落但无生产构造**：类型/指纹/预算在 `core/model/.../TutorVisualContext.kt:23-78`；生产零构造点，只有测试（`grep` 只命中测试）。
- **能力快照的广告-执行一致性测试目前只覆盖 IMAGE_MODEL**：`FigureToolPromptInjectionTest.kt:142` 断言 prompt 正文不含 "JS"；`FigurePromptParityTest.kt:48` 断言 `availableMethods == [IMAGE_MODEL]`。

**对广告的影响：硬阻塞。** 广告 JS 不是改一句提示词，而是一次**跨面同步改造**：method 级能力源（JS runtime ready + image backend ready）、`FigurePromptAssembler`、`OpenAiModelProtocol` schema、`TutorToolDescriptions`、`RoomModelTaskRepository` 的 `imageBackendReady` 语义、以及 native/Route B 两路解析的接受分支。任何一面漏改，都会出现“广告了但执行端拒绝”或“没广告但模型能伪造”的裂缝。

### 1.6 双后端配对

**契约要求。** JS 与 IMAGE_MODEL 是同一生图能力的两种方法：所有入口同一 request/proposal/coordinator/ledger；模型按真实能力选择；同一 operationId 不含 method；切换方法不换 operation、不清预算、不加确认、不静默降级；客户端不暗换；JS 本地执行不占远程派发（契约 `:9-13`、`:29-31`、`:143-157`；计划 `:61-67`、`:113-115`；提示词策略 `:99-115`）。验收必须覆盖“同用途两方法都成功”“JS 失败转 IMAGE_MODEL”“图像不佳转 JS”“操作 ID/源题/预算不变”（计划 `:67`；提示词策略 `:110-115`）。

**当前状态：类型/policy 骨架已落；执行 0；广告面全拒。**

- 已落：
  - `FigureGenerationMethod` 枚举（`FigureGeneration.kt:32-36`）。
  - JS proposal 的形状校验：不得带 `imageOperation/imagePrompt`、必须带非空 program、≤64 KiB、参数交互能力不足时拒绝（`FigureGenerationPolicy.kt:185-219`）。
  - 类型化 `FigureArtifact.Program`（programId/sourceJs/snapshotId，method 固定 JS）（`FigureGeneration.kt:499-514`）。
  - `operationId` 不含 method 的语义已写进 `FigureDispatchParent`/`FigureGenerationRequest`（`:260-335`）。
- 未落/硬关闭：
  - **协调器没有 JS 分支，也没有 `VisualExecutionEngine` 注入点**：`DefaultFigureGenerationCoordinator` 构造参数只有 `ledger`、`openBackend`、`now`、`audit`（`DefaultFigureGenerationCoordinator.kt:125-134`）；`generateImage` 在 `method != IMAGE_MODEL` 时直接返回 `UNAVAILABLE`，诊断“本波次没有可用的 JS 运行库，未执行”（`:182-190`）。
  - 测试 `aJavascriptProposalIsNotExecutedInThisWave` 把这一现状钉死：JS proposal + JS capability → `UNAVAILABLE`、`ledger.requests` 空、`channel.generateCalls==0`（`FigureGenerationCoordinatorTest.kt:197-221`）。JS 接通时这条测试必须**改成正向执行断言**，不能删掉或放宽。
  - **工具调用构造器拒绝 JS/program**：`require(figureProgram == null)`、`require(figureMethod == null || figureMethod == IMAGE_MODEL)`（`TutorToolLoop.kt:343-349`）；`toFigureGenerationProposal()` 永远填 `IMAGE_MODEL`（`:394-405`）。
  - **解析器解析 program 但构造器拒**：Route B/原生都读 `program`（`OpenAiModelResponseParsers.kt:615-623`），然后被 `TutorToolCall` 构造器拒；测试 `jsMethodIsRejectedOnBothRoutes` 断言两路都抛 `IllegalArgumentException`（`TutorFigureMethodDualParseTest.kt:208-222`）。
  - **原生 schema 没有 program 字段**（`OpenAiModelProtocol.kt:443-459`）；即使 schema 有 method，也没有 JS 载荷位。
  - **basis 指纹不包含 program**：`figureBasisFingerprint(operationId, sourceSha256, imagePrompt)` 只拼 operation/source sha/imagePrompt（`FigureGeneration.kt:615-630`）；协调器旧调用回退也用它（`DefaultFigureGenerationCoordinator.kt:519`）。对 JS proposal，`imagePrompt` 为 null，两个不同 program 会得到同一个 basis 指纹——一旦接通 JS，这会让“输入身份”失去区分度。**广告前必须改成按方法携带真实载荷摘要（例如 program SHA），或要求入口显式提供 basis。**
  - **没有生产装配**：`VisualJsClientEngine` 只在仪器化用例构造（`app/src/androidTest/.../VisualJsIsolationInstrumentedTest.kt:96`、`:251`）；`core/data` 的协调器也不引用 `VisualExecutionEngine`。三层（coordinator → client engine → isolated service）没有一条生产链。
  - **没有双方法成功/切换测试**：`TutorFigureProposalExecutionTest.kt` 只覆盖 IMAGE_MODEL EDIT/GENERATE（`:124-228`）；没有 JS 成功、JS 失败转 IMAGE_MODEL、IMAGE_MODEL 不佳转 JS、同 operation/预算不变、local JS 0 远程派发的用例。

**对广告的影响：硬阻塞。** “双后端配对”目前是**单后端执行 + 另一后端的类型占位**。广告 JS 后，模型会真的选它；当前 coordinator 会回 `UNAVAILABLE`，等于广告一个必然失败的方法，违反“未过方法不广告”。

---

## 二、六项之外的横切前置（同样不可省略）

### 2.1 生产装配与模块边界

- 契约 §5.1 的目标架构对照表明确：不存在 `AndroidVisualExecutionEngine`；不存在 `core:visual-js`；Service/AIDL/client 全在 app；直接引擎 `QuickJsVisualExecutionEngine` 落 `core:data`；`core:visual` 不存在（契约 `:110-119`）。
- 归位条件写死：**4E.2 真正开放 JS 前**，把 Service/client/AIDL 迁入 `core:visual-js`（需改 `settings.gradle.kts`），并组合 `AndroidVisualExecutionEngine`；端口与 wire 协议不变（契约 `:119`）。
- 这不是“架构洁癖”：当前 `core:data` 同时承担“业务数据层”和“QuickJS 执行引擎”，与契约给 core:data 划的“只经端口做来源/幂等/持久化/CAS”边界不一致（契约 `:104`）。广告 JS 意味着这条边界要长期保留，必须在开放前归位，否则后续每加一个入口都会继续往 core:data 堆执行逻辑。

### 2.2 持久化、回放与导出

契约 §8 要求四类 JS 记录（`tutor_visual_program`、`tutor_visual_snapshot`、`tutor_message_visual`、`problem_revision_visual` 引用）与统一 operation/attempt 同事务（契约 `:161-173`）。当前：

- DB v69（`core/database/.../StudyDatabase.kt:134`）；69.json 中只有两张图相关表：`figure_generation_operation`、`figure_generation_attempt`（`FigureGenerationEntities.kt:39-40`、`:106-107`；`FigureGenerationSchemaContractTest` 断言“只多这两张表”）。
- `grep "tutor_visual"` 在 `core`/`app` 零命中（附录 A #5）；没有 `LOCAL_VISUAL_PREVIEW` 常量、没有 `ui_state_json/formDigest/revision`、没有 `problem_revision_visual` 引用。
- 没有 `VisualSnapshotPdfBlock`（附录 A #6）；`core/export` 仍是旧图像口径（`CLEAN_IMAGE`/`QUESTION_DIAGRAM`/`cleanImageLocalUri`，见 `core/export/.../AiGeneratedImageCaption.kt:10`、`DocxMistakeRenderer.kt:24-31`），不能消费 JS 快照。PDF/DOCX 的 role/revision 过滤、同快照 screen/PDF 一致、导出 0 JS 执行都没有 JS 载体（计划 `:148-150`；契约 `:192-196`）。
- 没有 UI 显示链：`core/ui` 无 `VisualSceneCard`；feature/tutor 对 `visualId` 零引用。即使引擎返回了场景，也没有任何“消息里的图”可渲染。
- 没有备份/迁移/GC 的 JS 行：契约要求程序/快照是 DB 数据、预览是资产，缺必需字节拒绝完整包；当前备份 suite 的 `figure_generation_operation` 计数是审计表，不是 JS 程序/快照（`BackupSnapshotTrimmerInstrumentedTest.kt:68` 等）。

**结论：没有持久化就没有“保存重启仍可见图”，没有回放就没有“展示”，没有导出就没有 4E.1 要求的 PDF 纵向链。这三条都是广告的硬前置，不是后续优化。**

### 2.3 源证据

契约 §3.1 明确：当前 `TutorQuestionContext` 没有原题资产；Plan 不发原题图，Respond 仅发 `studentImageAssetRefs`；这些仍是接入差距。JS 产码模型需实际看到原图或足够且可追溯的结构化源事实，不能把“本地有图”当成“参与生成的模型已看图”（契约 `:37`）。

- 代码侧：`TutorQuestionContext` 字段只有 session/revision/subject/title/questionDocument/learningMemory/...（`feature/tutor/.../TutorModelTaskPolicy.kt:115-138`）；没有 `VisualSourceBasis`、没有 `sourceSeenDispatchId`、没有“本轮实际发送了哪份源证据”的记录（`grep "VisualSourceBasis\|sourceSeenDispatchId"` 在 core/model、core/data、feature/tutor 零命中）。
- `FigureGenerationRequest.source: FigureSourceEvidence?` 可以携带 JPEG/PNG 字节与 SHA（`FigureGeneration.kt:224-251`），但讲题入口没有把它接到原题资产；契约要求的“同批准备不冒充已发送”也没有实现。

**结论：JS 广告后，模型可能在没看到源图/结构化事实的情况下产码；这会把“图不对”归因成模型质量问题，而实际是源证据门没接。**

### 2.4 发布矩阵与真实 Provider

- 契约 §9 的完整门包括：源码固定与许可证；API 23/arm64/API 35+ 16 KB/R8；隔离启动、无网络/文件能力、引擎崩溃和 watchdog 恢复；字节/几何/字体/像素预算；两种工具协议和六次总预算；旧请求指纹、全历史迁移、备份/GC；六类截图样例与一个新算法组合样例；360/600/840 dp、fontScale 1/1.3/2、TalkBack；同快照屏幕/PDF/SVG 一致；真实 Provider 生成与修复率另测（契约 `:202`）。
- 已有证据：API 34 x86_64 4 KB（E-01b/E-01e、E-02）、API 36 x86_64 16 KB 运行时（E-03，`step03-page-size-16k.md:113`）、arm64 ELF 程序头（E-09）。
- 缺口：**API 23 运行时未测；arm64 运行时未测**（批次 23 记录 `:164` 明确“arm64 运行时未测”）；**arm64 16 KB 运行时未测**；**release R8 本批未重跑**（KD-69 仍 open，`docs/known-defects.md:2671`）；`usage.txt` 未全量穷举、`ExportPdfWorker` 未单独触发。
- 真实 Provider 无凭据：模型质量 UNVERIFIED（批次 23 记录 `:198`）；PRD-09 的 120 题盲测/90/95/90/关键错误 0 没有任何执行记录（设计 `:91`；PRD-09 `:133`）。

**结论：即使功能链全绿，“在哪些设备/ABI/构建形态上可广告”仍没有闭环；真实 Provider 质量更没有任何证据。**

---

## 三、最小可广告子集建议（MVA-JS-STATIC）

### 3.1 建议范围

**只广告“静态 SceneV1”**，即：

- `FigureGenerationMethod.JS` 可用，但能力位固定为：`supportsParametricInteraction=false`、`supportsLayerComposition=false`、`supportsPixelEditing=false`；JS 不提供图像编辑/生成。
- SceneV1 只允许 `group/path/label`；`controls[]`、`hitRegions[]`、`form?` 必须为空/不存在；`reduce`、动画/transport、事件、草稿、SVG 全部不广告、不解析、不执行。
- `FigureGenerationTarget.requiresParametricInteraction=true` 的目标对 JS 拒绝（现有 policy 已做，`FigureGenerationPolicy.kt:212-217`）；`requiresPixelFidelity=true` 的目标对 JS 拒绝（**当前缺失，广告前必须补**）。
- 模型仍可按真实能力为任意 `purpose` 选 JS 或 IMAGE_MODEL；**不得**引入“某用途只准 JS/只准图像模型”的路由表（契约 `:9-13`、`:35`；计划 `:60-61`）。
- 同 operation 内允许 JS 失败后修 program，或说明理由后切 IMAGE_MODEL；客户端不暗换；切换不换 operation、不清预算（契约 `:11`、`:143-157`）。

### 3.2 静态 MVA 仍必须补齐的六项

1. **SceneV1 编译器 + SDK + RenderScene/painter**：`VisualProgram/VisualScene/Validator/Compiler/StateCodec`；`visual-sdk-v1.js`（静态子集：`prepare/render` + `line/arrow/circle/arc/axes/polyline`，不含 `reduce`）；`AndroidScenePainter` 与 PDF 同 painter。
2. **真实内容度量**：`AndroidContentLayout`/`MathContentLowering`，真实 glyph metrics、inkBounds、字体白名单、后台布局；替换 `UnwiredVisualContentMeasurer`。
3. **持久化与显示**：`tutor_visual_program/snapshot/message_visual`（或等价）、`LOCAL_VISUAL_PREVIEW`、消息同事务引用、删除会话不删保存题图、CAS/checkpoint；`VisualSceneCard` 从持久 link 显示。
4. **导出**：`VisualSnapshotPdfBlock` 接 PDF/DOCX；冻结 `{programHash,state,timeMs,seed,layoutEnvironment,RenderScene}`；导出不执行 JS；role/revision 过滤；默认练习卷不泄答案。
5. **双后端执行与广告面**：coordinator JS 分支（注入 `VisualExecutionEngine`，JS 本地不占 remote dispatch，仍记 attempt）；`TutorToolCall` 接受 JS/program；native/Route B schema；`FigurePromptAssembler` 动态 method 能力 + 静态 schema/SDK/样例；`TutorCapabilitySnapshot` method 级就绪；tool description 更新；`TutorVisualContext` 生产注入（当前 program/state/诊断给修复回合）；basis 指纹纳入 program 摘要。
6. **保真/质量门（静态子集）**：补 JS pixel-fidelity 拒绝，并核对 `PIXEL_CLEANUP` 入口的 `requiresPixelFidelity` 映射；至少用截图 1/2/5/6 + 新组合（波叠加/轨迹采样）做独立 oracle；若按现行契约，还要 120 题盲测的 90/95/90/关键错误 0（或由主会话正式裁定缩小 MVA 质量门，不能静默跳过）。

### 3.3 明确后置

- **4E.2 交互/表单**：`reduce`、原生控件/滑块、`NativeTutorFormV1`、动画/transport、命中语义、草稿、`strictOffline` 交互、SVG writer。全部后置，但在能力快照/prompt/解析/执行四处同时关闭（契约 `:58`、`:131`）。
- **4E.3 复杂源与保真扩展**：整页/复杂自然图/跨科质量覆盖、组合能力（layer composition）、复杂保真 oracle。当前计划只有一行边界（`:21`），建议单独立项/细化后再谈广告范围扩大。
- **IMAGE_MODEL 的既有能力不变**：它仍是像素编辑/生成、自然纹理、复杂写实图的主方法；JS 静态子集的广告不改变它的能力位。

### 3.4 依赖顺序（建议）

1. **SceneV1 类型/validator/compiler/state codec + SDK 静态子集**（`core:model` + assets）。
2. **真实度量 + painter + screen/PDF 回放**（`core:visual` + `core:export`）。
3. **持久化 + 消息 link + UI 卡片 + 备份/迁移/GC**。
4. **coordinator JS 分支 + tool/parser/schema/prompt/capability/description**。
5. **双方法成功/切换/0 远程派发测试 + 设备端到端 + 质量门**。
6. **发布矩阵（API 23/arm64/16KB/R8 release）与真实 Provider**。

这个顺序的理由：1-2 决定“能不能画对”，3 决定“能不能留住”，4 决定“模型能不能选到并执行”，5-6 决定“敢不敢对用户说可用”。任何一步提前广告都会产生不可归因的失败。

### 3.5 不建议的“半步广告”

- **纯几何图**：技术上今天可在隔离进程跑通（`prepare` 返回空），但没有标签/公式，教学价值不足；且仍缺编译器/painter/持久化。不要把它包装成“JS 可用”。
- **只改提示词/工具 schema**：会广告一个 coordinator 必然回 `UNAVAILABLE` 的方法（`DefaultFigureGenerationCoordinator.kt:182-190`）。
- **只接通引擎、不做 compiler/painter**：场景 JSON 无法显示，等于把内部冒烟当产品能力。
- **让 IMAGE_MODEL 代打“JS 已支持交互”**：契约明确“只有静态图能力的后端不能假装完成必需的连续参数交互”（提示词 `:18`；policy `:212-217`）。

---

## 四、判定门：哪些用例必须设备绿

> 记号：**设备** = 必须仪器化/真机或模拟器执行；**JVM** = JVM 测试（必要但不充分）；**新增** = 当前仓内不存在，需实施时新增；**已绿/已红** = 有批次 23 E-file；**未验** = 批次 24 在飞或从未执行。
>
> **一句话答案（必须设备绿的用例）**：`VisualJsIsolationInstrumentedTest` 6/6；`QuickJsVisualExecutionEngineInstrumentedTest` 20/20（或裁定的等价 P2 判据）；真实 `VisualContentMeasurer` 的 text/math 设备用例；Scene compiler/painter/PDF 同快照 + 六类 fixture 静态子集；JS 静态端到端（tool→coordinator→isolated→compiler→UI→save/restart→PDF/DOCX）；迁移/备份/导出含 JS 行；广告-执行一致；发布矩阵（API 23/arm64/arm64 16 KB/release R8）。交互/表单设备用例属 4E.2 全量，MVA 阶段不纳入。

### 4.1 隔离门（前提，必须全绿）

| 用例 | 类型 | 现状 | 证据/说明 |
|---|---|---|---|
| `workerIsIsolatedAndAProgramRoundTripsWithoutBeingKilledLater` | 设备 | **已绿** | 隔离 UID + AIDL 往返（E-01b/E-01e） |
| `aStaleGenerationIsRejectedAndAStaleTerminateIsANoOp` | 设备 | **已绿** | E-01b |
| `theWatchdogKillsAHungWorkerAndAFreshGenerationHasNoBusyState` | 设备 | **已绿** | 1500 ms SIGKILL + 新 generation 无 BUSY（E-01b） |
| `theClientRebuildsOnceAfterADeathAndTheNewWorkerIsNotBusy` | 设备 | **已绿** | 死亡后重建一次（E-01b） |
| `theClientRebuildsAtMostOnceAfterConfirmedAbandons` | 设备 | **已红→批次 24 改后未验** | 批次 23 红 `the first abandon did not confirm death`（E-01c:107）；批次 24 改 `abandon()`/`awaitDeathConfirmation()`（`VisualJsClientEngine.kt:575-650`），**未跑设备** |
| `aColdStartAttachBeyondTheOldConnectBudgetStillRendersTheFirstProgram` | 设备 | **新增，未验** | `VisualJsIsolationInstrumentedTest.kt:246-260`；批次 24 新增，旧 5 s 预算下必红，新 15 s 首连预算待设备验证 |

> 广告判定：**6/6 必须设备绿**。若批次 24 的修复在设备上仍红，隔离门未全绿，JS 继续不广告（本批纪律，计划 `:15`、`:17`）。
>
> KD-66 的 `hard+500ms` 关闭门（`docs/known-defects.md:2608`）是**单独**的关闭条件：现有 watchdog 用例以 ≤6 s 判死，弱于该门。广告文案/能力说明**不得**声称“1.5 s 硬上界”；若要以“进程级硬终止”作为安全卖点，必须先有 hard+500ms 设备证据。

### 4.2 引擎/沙箱门

| 用例 | 类型 | 现状 | 说明 |
|---|---|---|---|
| `QuickJsVisualExecutionEngineInstrumentedTest` 全 20 例 | 设备 | **19/20，P2 红** | E-02 `step02-quickjs-oom.md:112`：`mallocSize=177216`，未达 90% 阈值 → `INVALID_PROGRAM [UNATTRIBUTED]` |
| `memoryOverTheHeapBudgetIsRejectedAsAMemoryLimit`（P1） | 设备 | **已绿** | 单次 64 MiB ArrayBuffer → `MEMORY_LIMIT` |
| `heapExhaustionWithLostErrorMessageIsAttributedByMemoryUsage`（P2） | 设备 | **红→批次 24 换 GC 锚定夹具，未验** | 批次 24 改 `HEAP_LIST_SOURCE`（`QuickJsVisualExecutionEngineInstrumentedTest.kt:761` 夹具），设备复跑归设备轨；KD-70 仍 open |
| P3/P4/P5（string cap / null throw / forged OOM） | 设备 | **已绿** | E-02 |
| 真实 `VisualContentMeasurer` 路径 | 设备 | **新增** | 现有仪器化用 `CapturingMeasurer`（`:84-110`）；隔离服务用 `UnwiredVisualContentMeasurer`（`VisualJsService.kt:310`），从未验证真实 text/math |
| `VisualExecutionEngineContractTest` 对真实引擎复用 | JVM/设备 | **仅对 fake** | 契约测试头明确“真实 QuickJS 实现落地时复用本文件作为同一契约的验收”（`VisualExecutionEngineContractTest.kt:34-40`），当前只有 fake 实例 |

> 广告判定：引擎全 20 例（或主会话裁定的等价判据）+ 真实度量路径 **设备绿**。KD-70 P2 未绿之前，“内存预算可证”不成立；**不得下调 90% 阈值**（KD-70:2691、`:2702`）。

### 4.3 编译器/度量/回放门（新增）

计划 `:101` 已给出必须新增的测试类：

- `SceneValidatorTest`、`SceneCompilerTest`、`VisualStateCodecTest`、`VisualSdkContractTest`、`NativeContentLayoutInstrumentedTest`、`MathContentLoweringTest`（JVM/设备按类）。
- 覆盖 clipping、非均匀矩阵、虚线、分数/根号/上下标、中文宽度、大字体；金样同时比较结构/位置与固定环境像素；跨系统字形差异单列，不承诺跨设备像素 SHA 一致（计划 `:101-103`）。
- 新增 `AndroidScenePainter` 的 Compose/PDF 同 painter 回放测试；同一 `RenderScene` 在屏幕与 PDF 的结构/位置一致（设备/导出）。
- 六类截图 fixture：1 比例条、2 F-x 面积、3 解题流程、5 受力图、6 拔塞阶段；加“波叠加/轨迹采样”新组合（计划 `:154-168`）。静态 MVA 的必测子集是 1/2/5/6 + 新组合；截图 3 主要验原生卡片/公式；截图 4 属 4E.2 表单，后置。
- 学科正确性 oracle 与渲染 fixture 分开：截图缺完整题面时不能把图线端点当物理公理；B7 必须按授权完整原题及独立核对集判断（计划 `:168`）。

> 广告判定：以上 **设备绿**（至少 JVM + 设备金样/仪器化）。没有它们，“场景能编译”只是类型存在，不是可显示/可导出。

### 4.4 端到端纵向链门（新增）

必须新增一条 JS 静态纵向链设备用例（建议名 `VisualGenerationStaticEndToEndInstrumentedTest`）：

- 模型工具 proposal（method=JS、program）→ `FigureGenerationCoordinator` → 隔离引擎 → 真实度量 → compiler → `VisualSceneCard` 显示 → 保存/重启 → PDF/DOCX 导出；全程 **0 次 Provider 派发**（JS 本地执行）。
- 同一 operation 内 JS 失败 → 诊断回原回合 → 切换 IMAGE_MODEL（fail-closed/预算不变）；或 IMAGE_MODEL 效果不佳 → 转 JS；断言 operationId 不变、无新增确认、无双重载荷、预算不重置。
- 保存题/删除会话/GC：删除会话不删保存题图；固定引用不被回收；进程重建读最近 checkpoint；strictOffline 下已持久程序可重放、0 Provider 派发（契约 `:141`、`:180-184`）。
- 消息正文/trace/visual link 同事务；失败/取消清 staging；迟到结果不覆盖新 revision（契约 `:184`）。

> 广告判定：**必须设备绿**。这是“同一规则→模型选方法→一张图→保存重启→PDF”的完整链（计划 `:23`），缺一段就不能广告。

### 4.5 持久化/迁移/备份/导出门

| 门 | 必须覆盖 | 现状 |
|---|---|---|
| Room 迁移 | 全历史版本 → 新增 JS 表/列；旧行一行不动；再开一次不漂 | 新增；当前只有 figure audit 表（v69） |
| 备份/恢复 | 程序/快照/预览/引用进备份；缺字节拒绝完整包；恢复后不执行 JS、不联网 | 新增；现有备份 34/34 是旧资产口径（E-05） |
| GC/固定引用 | 初始/最新两个 checkpoint；每 program 4 MiB/全局 32 MiB；固定引用不删；磁盘不足拒提交 | 新增 |
| 导出 role/revision | QUESTION_DIAGRAM 仅当前 revision 且核对通过；TUTOR_EXPLANATION 仅 includeSolution 解析区；默认练习卷不泄答案 | 新增（旧图 role 测试存在，JS 无载体） |
| 导出 0 派发 | PDF/DOCX/打印/分享不触发模型/JS 执行 | 新增 |
| 导出指纹 | 快照 SHA/字体/renderer/布局/time/seed 进入 exportFingerprint | 新增 |

> 广告判定：**设备/JVM 均需绿**（迁移/备份/导出至少设备或真实 Room 仪器化）。没有持久化，“保存重启仍可见图”不成立；没有导出，4E.1 的 PDF 纵向链不成立。

### 4.6 能力/工具广告门

| 用例 | 类型 | 现状 |
|---|---|---|
| `TutorFigureMethodDualParseTest`：JS/program 在 native + Route B 都被接受（两路同果） | JVM | **当前相反**：`jsMethodIsRejectedOnBothRoutes` 断言拒绝（`:208-222`）；广告前必须改成接受并保留负向用例（缺 program、超 64 KiB、program 放正文） |
| `theNativeSchemaAdvertisesOnlyImageModel...` → 改为“广告 IMAGE_MODEL 与 JS，且 JS 只出现在独立 program 字段” | JVM | **当前相反**（`:255-276`） |
| `FigurePromptAssembler` 组装 JS 静态能力：注入 schema/SDK 签名/最小样例；不含 reduce/form/controls | JVM | **当前 fail-closed**（`:40-45`、`:108-113`） |
| `TutorCapabilitySnapshot` method 级就绪：JS runtime ready 时 `GENERATE_FIGURE` 可广告，不依赖 image backend | JVM/设备 | **未落**：当前 `imageBackendReady` 只看图像后端（`Capabilities.kt:70-78`；`RoomModelTaskRepository.kt:959`） |
| `TutorToolDescriptions` 动态描述：method/program，而非“未配置图像模型就不会生成” | JVM | **未落**（`:56-68`、`:89-93`） |
| 能力快照广告-执行一致：广告 JS 后执行端不返回 `UNAVAILABLE` | 设备 | **未落**；当前 `FigureGenerationCoordinatorTest` 钉的是 UNAVAILABLE（`:197-221`） |

> 广告判定：全部 **JVM 绿 + 至少一条设备广告-执行一致用例绿**。广告面与执行面必须同源，不能只改 schema。

### 4.7 质量/保真门

| 门 | 判据 | 现状 |
|---|---|---|
| 六类 fixture 静态子集 | 结构/位置/标签/比例/方向；独立预期；屏幕/PDF 一致 | 新增 |
| 新组合样例 | 波叠加/轨迹采样只新增程序与数据，不新增题型 renderer | 新增 |
| 120 题盲测 | 首次 ≥90%、预算内 ≥95%、每组 ≥90%、关键错误接受 = 0；记录分母/区间/评审来源 | 新增；真实 Provider UNVERIFIED |
| 布局/无障碍 | 360/600/840 dp、fontScale 1/1.3/2、明暗主题、TalkBack、横屏/旋转、后台恢复；标签重叠/公式基线/点击区/无水平溢出 | 新增 |
| 延迟 | JS 首张合格图 p95 ≤30 s（待验证目标，不是保证） | 未测（设计 `:95`） |
| 负向 | JS pixel-fidelity 目标拒绝；参数交互不足拒绝；伪造 OOM 分类；超预算不截断 | 部分 JVM；pixel-fidelity 对 JS 缺失（`FigureGenerationPolicy.kt:185-219`） |

> 广告判定：至少静态子集的 fixture + 布局/无障碍 **设备绿**；质量门按现行契约需 120 题记录。若主会话要缩小 MVA 质量门，必须**正式改写门值**（记录在案），不得用“先广告再补”绕过（契约 `:202-204`；计划 `:324`）。

### 4.8 发布矩阵门

契约 `:202` 要求：API 23 / arm64 / API 35+ 16 KB / R8。

| 项 | 现状 | 证据/缺口 |
|---|---|---|
| API 34 x86_64 4 KB | 已跑 | E-01b/E-01e、E-02 |
| API 36 x86_64 16 KB 运行时 | 已跑 3/3 | E-03 `step03-page-size-16k.md:113` |
| arm64 ELF 程序头 | 已跑 | E-09 `step09-elf-llvm-readobj.md:119` |
| **API 23 运行时** | **未测** | 批次 23 记录 `:164` 未列；无 E-file |
| **arm64 运行时** | **未测** | 批次 23 记录 `:164` 明确“arm64 运行时未测” |
| **arm64 16 KB 运行时** | **未测** | 只有 x86_64 16 KB |
| **release R8 冒烟** | **本批未重跑** | KD-69 仍 open；internal R8 E-04 绿不等于 release（`known-defects.md:2671`） |

> 广告判定：上述四项 **设备绿**。矩阵未闭环前，广告只能限定在已验构建/ABI，且必须在能力/文案中如实说明；不能默认“全设备可用”。

### 4.9 交互/表单门（MVA 后置，不纳入本次广告门）

如果未来要开 4E.2 全量，以下也必须设备绿：

- `NativeTutorFormV1` 原生 radioGroup/textInput/submit、禁用态、幂等（计划 `:161`）。
- reduce 队列：连点两次按钮、drag-end 紧接 reset、满队列、迟到 reduce 结果不丢动作（计划 `:146`）。
- 动画 1000 帧 → 0 个逐帧 DB/资产写入；拖动结束 → 一次 checkpoint；进程死亡 → 最近 checkpoint；strictOffline → 本地交互可用且 0 Provider 派发（计划 `:147`）。
- 360/600/840 dp、fontScale、TalkBack、横竖屏、后台恢复下的控件/命中区/阅读顺序（契约 `:137`）。
- 表单草稿旋转/重建不自动发送、换题不套用旧答案（契约 `:135`）。

> 在 MVA 静态子集阶段，这些全部**不广告、不解析、不执行**；若模型硬塞 `controls`/`form`/`reduce` 字段，validator 必须拒绝（契约 `:58`、`:131`）。

### 4.10 当前必须先转红的用例（按优先级）

1. `theClientRebuildsAtMostOnceAfterConfirmedAbandons`（隔离门唯一红；批次 24 修复未验）。
2. `aColdStartAttachBeyondTheOldConnectBudgetStillRendersTheFirstProgram`（新增；冷启动首连）。
3. `heapExhaustionWithLostErrorMessageIsAttributedByMemoryUsage`（KD-70 P2；或主会话裁定等价判据）。
4. `QuickJsVisualExecutionEngineInstrumentedTest` 其余 19 例保持绿（不得因改夹具回退）。
5. P3/P4/P5 与 `VisualJsFailureClassificationTest` 保持绿（不得放宽 90% 阈值）。
6. 接入真实 `VisualContentMeasurer` 后，新增的 text/math 设备用例必须绿（当前完全没有）。
7. KD-66 `hard+500ms` 门（若广告文案要声称硬上界；否则至少写明“最坏调用方等待约 18.5 s，不是 1.5/2.5 s 硬上界”）。
8. 发布矩阵四项（API 23/arm64/arm64 16KB/release R8）补齐。

---

## 五、建议顺序、待裁事项与风险

### 5.1 建议顺序（从“隔离门全绿”之后开始）

1. **先把隔离门 6/6 设备跑绿**（批次 24 T1 修复 + 设备证据按 R4 落盘）；KD-70 P2 同步用 GC 锚定夹具复跑或走等价判据裁定。
2. **SceneV1 类型/validator/compiler/state codec + SDK 静态子集**（先不接 UI，用 JVM 金样钉结构/位置）。
3. **真实度量 + painter + PDF 回放**（`core:visual`/`core:export`），用截图 1/2/5/6 + 新组合做独立 oracle。
4. **持久化 + 消息 link + `VisualSceneCard` + 备份/迁移/GC**。
5. **coordinator JS 分支 + tool/parser/schema/prompt/capability/description + 双方法测试**；同时补 JS pixel-fidelity 拒绝、`PIXEL_CLEANUP` target 映射核对与 basis 指纹 program 摘要。
6. **端到端设备链 + 发布矩阵 + 真实 Provider 质量门**。
7. 全部绿后，才把 `availableMethods` 加入 JS，并在工具/提示词/能力快照三处同步广告；先静态，后交互。

### 5.2 需要主会话裁定的事项（本报告不自裁）

1. **MVA 质量门是否缩小**：现行契约是 120 题盲测 + 90/95/90/关键错误 0（设计 `:91`、计划 `:324`）。若只广告静态子集，是否仍要求完整 120 题？缩小必须正式改写门值并留记录，不能默认跳过。
2. **KD-66 `hard+500ms` 是否作为广告硬门**：现有隔离 suite 的 watchdog 用例以 ≤6 s 判死；若广告不声称硬上界，是否可先不关闭 KD-66？本报告建议：**广告可以不以 KD-66 关闭为前提，但能力/文案不得声称硬上界；若要以“进程级硬终止”作为卖点，则必须先关闭。**
3. **4E.3 是否单独立项**：计划只有一行边界（`:21`），没有任务/验收节。建议在广告范围从“静态”扩到“复杂源/保真扩展”之前，先补一份可执行计划，否则“保真门”没有可核对的判据。
4. **架构归位时机**：契约要求 4E.2 开放 JS 前迁入 `core:visual-js` + `AndroidVisualExecutionEngine`（`:119`）。是否允许“先在 app 模块广告静态、后归位”？本报告建议不允许：归位是已登记的开放条件，先广告会让偏差长期化。

### 5.3 风险提示

- **把“隔离绿”当“JS 绿”**：隔离只证明进程/UID/终止/往返；它不证明场景可编译、可显示、可保存、可导出。
- **把“纯几何跑通”当“教学图可用”**：文本/公式是教学图的主体；`UnwiredVisualContentMeasurer` 仍会把任何带字图拒成 `INVALID_PROGRAM`。
- **把“policy 接受 JS”当“双后端已配对”**：coordinator/parser/tool/schema 四处仍硬拒；policy 只是形状校验。
- **把“fidelity 字段存在”当“保真已验”**：没有 oracle/质量记录；且 JS 分支未拒 pixel-fidelity。
- **把“internal R8 绿”当“release 可发”**：KD-69 仍要求 release 不回退；API 23/arm64 运行时也未测。

---

## 附录 A：本报告实跑核对（只读；未跑 Gradle/设备）

```bash
cd "D:/smart mistake book"
git rev-parse --short HEAD                 # 5bc8bdf8
git status --porcelain | wc -l             # 690
git status --porcelain | grep -c '^ M'     # 217
git status --porcelain | grep -c '^??'     # 473

# 1) SceneV1 类型/编译器：0
grep -rn "class VisualSceneCompiler\|class VisualSceneValidator\|class VisualStateCodec\|class RenderScene\|class ValidatedScene" \
  --include=*.kt core app feature | grep -v "/build/" | wc -l          # 0
# 2) NativeTutorFormV1/NativeTutorForm：0
grep -rn "NativeTutorForm" --include=*.kt core app feature | grep -v "/build/" | wc -l  # 0
# 3) VisualSceneCard/VisualControls/VisualSemantics：0
grep -rn "VisualSceneCard\|VisualControls\|VisualSemantics" \
  --include=*.kt core app feature | grep -v "/build/" | wc -l          # 0
# 4) visual-sdk 资产：0
find app core feature -type f -name "visual-sdk*" | wc -l               # 0
# 5) tutor_visual 表/常量：0
grep -rn "tutor_visual" --include=*.kt --include=*.sql core app | grep -v "/build/" | wc -l  # 0
# 6) VisualSnapshotPdfBlock：0
find core/export -name "VisualSnapshotPdfBlock.kt" | wc -l               # 0
# 7) 引擎 reduce：0
grep -c "reduce" core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/QuickJsVisualExecutionEngine.kt  # 0
# 8) 生产构造 VisualJsClientEngine：0（仅 androidTest 96/251 与类声明 81）
grep -rn "= VisualJsClientEngine(\|VisualJsClientEngine(context)\|VisualJsClientEngine(delayed)" \
  --include=*.kt app core feature | grep -v "/build/" | grep -v androidTest | wc -l  # 0
# 9) AndroidScenePainter/AndroidContentLayout/MathContentLowering/SceneSvgWriter 类：0
grep -rn "class AndroidScenePainter\|class AndroidContentLayout\|class MathContentLowering\|class SceneSvgWriter" \
  --include=*.kt core app feature | grep -v "/build/" | wc -l             # 0
# 10) v69 中 figure_generation 表：2
grep -o '"tableName": *"[^"]*"' core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/69.json \
  | grep -c "figure_generation"                                           # 2
```

**未执行**：任何 `./gradlew`、`adb`、`am instrument`、`connectedAndroidTest`；任何 git 写命令。设备绿/红均引用批次 23 E-file，不是本轮实跑。

## 附录 B：来源清单（本轮全部为仓内材料；未使用外部网络来源，故无外部 URL）

- 实施计划：`docs/superpowers/plans/2026-10-18-flexible-js-drawing-implementation.md`（4E.2 `:20`、4E.3 `:21`、批次二 `:93-101`、界面/导出 `:142-151`、fixture `:154-168`、退出门 `:172-176`、质量门 `:323-325`）。
- 图形契约：`docs/current/visual-generation-contract.md`（方法选择 `:9-13`、源证据 `:37`、JS 事务 `:47-58`、隔离 `:62-72`、SceneV1/RenderScene `:94-104`、目标架构现状 `:110-119`、交互/表单 `:125-141`、工具/预算 `:143-157`、持久化 `:159-176`、导出 `:192-196`、启用门 `:200-204`）。
- 提示词策略：`docs/current/figure-generation-prompt-policy.md`（`:5`、`:80-84`、`:99-115`）。
- 产品就绪设计/PRD：`docs/superpowers/specs/2026-10-19-product-readiness-design.md:91`、`:95`；`docs/superpowers/plans/2026-10-19-product-readiness-followup-plan.md:133`。
- 批次记录：`docs/research/2026-10-26-batch-23-record.md`（`:7`、`:161`、`:164`、`:198`、`:251-254`、`:398-414`）；`docs/research/2026-10-26-batch-22-record.md`（T1 隔离/Unwired 度量）。
- 缺陷台账：`docs/known-defects.md` KD-66 `:2573-2608`、KD-69 `:2659-2675`、KD-70 `:2677-2702`、KD-71 `:2704`。
- 设备证据：`docs/research/evidence/2026-10-26-batch-23/step01b-visualjs-isolation-rerun.md:94`、`step01c-abandon-confirmed.md:107`、`step01e-visualjs-isolation-postboot-settled.md:85`、`step02-quickjs-oom.md:112`、`step03-page-size-16k.md:113`、`step04-r8-smoke.md:142`、`step05-backup-restore.md:70`、`step06-migration.md:153`。
- 源码锚点：`core/model/.../FigureGeneration.kt:32-36`、`:97-126`、`:135-160`、`:224-251`、`:260-335`、`:499-514`、`:615-630`；`core/model/.../FigureGenerationPolicy.kt:170-178`、`:185-219`、`:240-270`；`core/model/.../Capabilities.kt:47-132`；`core/model/.../TutorToolLoop.kt:343-349`、`:394-405`；`core/model/.../TutorToolDescriptions.kt:56-68`、`:89-93`；`core/model/.../TutorVisualContext.kt:23-78`；`core/domain/.../VisualExecutionEngine.kt:40-46`、`:149-215`、`:218-288`；`core/data/.../visual/QuickJsVisualExecutionEngine.kt:336-345`、`:584-645`、`:647-684`、`:809-814`；`core/data/.../visual/DefaultFigureGenerationCoordinator.kt:125-134`、`:182-190`、`:519`；`core/data/.../model/FigurePromptAssembler.kt:35-45`、`:70-73`、`:100-113`；`core/data/.../model/OpenAiModelProtocol.kt:443-459`；`core/data/.../model/OpenAiModelResponseParsers.kt:615-623`；`core/data/.../model/RoomModelTaskRepository.kt:959`；`app/.../visualjs/VisualJsService.kt:310`；`app/.../visualjs/VisualJsProtocol.kt:394-406`；`app/.../visualjs/VisualJsClientEngine.kt:575-650`、`:877-890`；`settings.gradle.kts:19-33`；`core/database/.../StudyDatabase.kt:134`；`core/database/.../entity/FigureGenerationEntities.kt:39-40`、`:106-153`。
