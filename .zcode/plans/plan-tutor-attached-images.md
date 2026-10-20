# 讲题对话附加 MCP 生成图（重绘题面 + 解析过程图）

> 2026-10-18后续接线更新：本文件保留历史任务/裁定与执行范围，不作为重跑旧MCP、固定图像后端或旧schema的指令。后续转写/净化/讲解/编辑/备份中的新图接线统一遵循[共享规则](../../docs/current/figure-generation-prompt-policy.md)、[4E的WG清单](../../docs/superpowers/plans/2026-10-18-flexible-js-drawing-implementation.md#10-全入口接线任务与后续阶段承接)及[后续阶段计划](plan-2026-10-18-followup-figure-wiring.md)；完成/未完成状态以[主线](../../docs/REFACTOR-MASTER-LINE.md)最新记录为准。

> Date: 2026-09-06 · Owner: this session · Status: IMPLEMENTATION
> 用户决策已确认：①=③ 都做；同 MCP（gpt-image-2）；图字段不按 kind 分类（共享语义）；可复用资产由模型决定入题板、不在表面平铺、点击指定错题单独看；安全边界=独立字段+本地 URI。

## 0. 目标

讲题对话的输出能附带两类**位图**（不是结构化场景）：
- **重绘题面**（链路①）：`ImageGenerationChannel.redrawClean` → gpt-image-2 `/v1/images/edits`。
- **解析过程图**（链路③）：`ImageGenerationChannel.generate` → gpt-image-2 `/v1/images/generations`（文生图/图生图）。

两者都**持久化为本地 canonical asset**，对话输出引用 asset 的本地 URI，渲染走 `BoundedLocalImage`（只解码本地 file/content URI，绝不联网）。现有 `SafeInlineMarkdown` / `shouldUseRichTextMarkdown` 三层 fail-closed **完全不动**——图片走独立字段，不混入 markdown。

## 0.1 关键设计决策（已确认）

| 决策 | 结论 |
|---|---|
| `AttachedImage` 形态 | 模型输出的**意图**（描述要什么图），非持久化引用；`sourceAssetId` **已删除** |
| `REDRAW_PROBLEM` 源图 | **自动拿当前题面**（从 `TutorVisualSourceAssetScope` 自动解析），模型不传 |
| `GENERATE_PROCESS` 源图 | 无，纯文生图（描述即 prompt） |
| 位图管线 | **独立轻量管线**（用户确认），不塞进 `TutorVisualPipeline`(scene) 的 review/cache/risk 闭环 |
| 安全 | 独立字段 + 本地 URI + `BoundedLocalImage`；不动 markdown fail-closed |
| 复用的架构思想 | anchor 定位 + 异步生成 + 补渲染；`ImageGenerationChannel` + asset vault + `BoundedLocalImage` |

## 1. 已核实的关键事实

| 事实 | 证据 |
|---|---|
| Android **不走 MCP 协议**，直接打 OpenAI | `OpenAiImageGenerationChannel` 调 `/v1/images/edits`；Android 搜不到任何 McpClient 实现 |
| MCP server 是**独立进程**，给外部 MCP 主机用 | `tools/image-mcp-server`，`StdioServerTransport`，含 `main()` |
| gpt-image-2 **同时支持** edits + generations | OpenAI 文档：`v1/images/generations`(生成) 与 `v1/images/edits`(编辑) 均 supported，input modalities: text,image |
| `BoundedLocalImage` 只解码本地 URI，绝不联网 | `BoundedLocalImage.kt`：`file`/`content` 分支，else null |
| asset 有持久化入口 | `AndroidCanonicalAssetVault.persistCleanImageBytes` → `CanonicalSourceAssetRecord` |
| Android 经 SSRF 守卫调 OpenAI | `ConfiguredCleanImageGenerator` → `resolveGuardedEdits` |
| 图字段 `AttachedImage` 不存在，全新 | grep 全库无 `AttachedImage` |

## 2. 复用 vs 新建

### 复用（不重写）
- `BoundedLocalImage`（渲染本地 URI）。
- `AndroidCanonicalAssetVault.persistCleanImageBytes` / CLean asset（存图）。
- `resolveGuardedEdits` / `OpenAiImageGenerationChannel` 的 SSRF + 认证模式。
- `CaptureSourceAssetRef` 的 asset 引用的字段形态（assetId/sha256/width/height）。

### 新建（真正新增）
1. **`AttachedImageRef`（core/model）**：对话输出的共享图字段。字段：`assetId`、`localUri`、`mimeType`、`width`、`height`、`role`（REDRAW_PROBLEM / SOLUTION_PROCESS / GENERIC）+ `sha256`。校验：uri 必须 `file://` 或 `content://`（禁 http）。加入 `ModelTaskOutput`（各对话 kind 作为 `attachedImages: List<AttachedImageRef> = emptyList()`）。
2. **`ImageGenerationChannel.generateImage(prompt/instruction, optional sourceImage) : ImageRedrawResult`**：新增文生图方法，调 `/v1/images/generations`。复用现有 SSRF + auth。若带源图则走 edits，否则走 generations。
3. **parser WIRE_KEYS**：`TUTOR_RESPOND/PLAN/LOBBY_WIRE_KEYS` 加 `"attachedImages"`；`toTutorRespond/Plan/Lobby` 接字段。
4. **对话输出渲染**：SUCCEEDED 回复正文下方，若有 `attachedImages` 非空 → 渲染缩略锚点（点击展开 `BoundedLocalImage`），不在表面平铺。
5. **MCP server（可选，给外部主机用）**：加 `generate_process_figure` 工具（文生图/图生图）。Android 不依赖它，仅供外部调用。

## 3. 范围与边界

- **不做/搁置**：`TutorVisualScene` / `TutorVisualDocumentScene` 2D/3D 场景渲染（`TutorVisualSceneRenderer`）——完全不动、不删。
- **安全**：`attachedImages` 的 `localUri` 只允许 `file://`/`content://`，禁 `http(s)://`；`BoundedLocalImage` 天然只解本地。不改 markdown 图片拦截。
- **成本风险（用户已知，标注不阻塞）**：gpt-image-2 生成式会字符漂移（`docs/image-pipeline-spec.md` §6 已标 open risk），需 pilot。

## 4. 实施顺序

1. **✅ `AttachedImage`（core/model）+ 校验 + 单测**：`AttachedImageKind`（REDRAW_PROBLEM/GENERATE_PROCESS）+ `AttachedImage`（imageId/kind/description/accessibilityText/sourceAssetId，校验：desc 走 requireTutorMarkdown 弱白名单、aid 走 requireSafeModelText、accessibility 空则跳过）。加入 `TutorRespondOutput`/`TutorPlanOutput`/`TutorLobbyOutput` 的 `attachedImages: List<AttachedImage> = emptyList()`，init 校验上限 `AttachedImage.MAX_ATTACHED_IMAGES`(=6)。
   - 测试：round-trip（含两种 kind）、拒 active/超大 desc、拒超 budget、legacy 无键解空。`TutorTasksTest` 38 例全绿。
2. **✅ 生成通道（core/data）+ 测试**：`ImageGenerationChannel` 接口加 `generate(request: ImageGenerationRequest): ImageRedrawResult`；`ImageGenerationRequest(prompt, sourceImageBytes?, sourceImageMimeType?, outputFormat, maxDimension)`。`OpenAiImageGenerationChannel` 实现：无源图 → `POST /v1/images/generations`（文生图，JSON body）；有源图 → `POST /v1/images/edits`（图生图，multipart）。`redrawClean` 重构复用 `execute()`（DRY）。fake 实现补 `generate`。`OpenAiImageGenerationChannelTest` 加 generations + edits 两测试，`core:data` 单测全绿。
3. **✅ 渲染 UI 组件（core/ui）+ 单测**：`AttachedImageCard` 折叠锚点（图标 + kind 标题），点击展开 `BoundedLocalImage`（本地 URI，绝不联网），无 URI 时占位"图尚未生成"。纯函数 `attachedImageCollapsedTitle(image)` 按 kind 给图题（重绘图/过程图）。`AttachedImageCardTest` 2 例全绿。
4. **✅ 位图管线·数据层编排（core/data + core/database）+ 单测**：
   - `AttachedImageGenerator`（core/data）：函数注入（generate/persist/resolveCurrentSheetBytes/uriFor），把 `List<AttachedImage>` 意图解析成 `Map<imageId, fileUri>`。`GENERATE_PROCESS` → 描述当 prompt 文生图；`REDRAW_PROBLEM` → 自动拿当前题面字节 + 固定重绘指令（去手写）图生图。生成 → `persistCleanImageBytes` → `relativePath→fileUri`。单图失败降级为该图缺席，cancellation 传播。
   - `StudyDbValue.SourceAssetType` 加 `TUTOR_ATTACHED`。
   - 测试：两 kind 转 URI、REDRAW 无题面跳过、generate 失败降级、cancellation 传播、prompt 按 kind 塑形。`AttachedImageGeneratorTest` 全绿。
5. **✅ UI 异步补渲染接线（core/ui + feature/tutor）+ 单测**：
   - `AttachedImagesSection`（core/ui）：`LaunchedEffect` 逐图调 `resolve`，state 存 `Map<imageId, String?>`，逐个 `AttachedImageCard`（含"图未生成"占位）。纯 suspend `resolveAttachedImages(images, resolve)` 可测。`AttachedImageCardTest` 加 2 例全绿。
   - `attachedImageResolver: (suspend (AttachedImage) -> String?)?` 从 `TutorModelPanel` → `TutorChatExchange` → `TutorAssistantReplyBubble` 一路注入，SUCCEEDED 正文后插 `AttachedImagesSection`（在 `resolvedVisualScene` 之前）。默认 null=不显示图。
   - **余下**：生产装配（app 层把真实 `AttachedImageGenerator` 接成 resolver）。
6. **✅ 模型 prompt 规则（respond/plan）+ parser WIRE_KEYS + 测试**：
   - parser：`TUTOR_RESPOND_WIRE_KEYS`/`TUTOR_PLAN_WIRE_KEYS`/`TUTOR_LOBBY_WIRE_KEYS` 加 `"attachedImages"`；`ATTACHED_IMAGE_WIRE_KEYS = {imageId,kind,description,accessibilityText}`；`JsonElement.toAttachedImage()`；`toTutorRespond/Plan/Lobby` 接 `attachedImages = optionalArray(...).map(...)`。
   - prompt（respond 规则 4/7a/11、plan 规则 5a/返回JSON）：允许0到6个 `attachedImages`（本地生成图请求，非图片文件）；`kind` 只能 REDRAW_PROBLEM（自动重绘当前题面去手写）/GENERATE_PROCESS（文生图）；description/accessibilityText 禁 URL/HTML/SVG/代码/像素/颜色；messageMarkdown/solutionMarkdown 始终独立讲清。
   - 测试：`OpenAiCompatibleModelGatewayTest` 加 attachedImages 从 wire 解析、缺省为空、未知子键被拒。core:data 全绿。
   - **范围**：lobby prompt 未加 attachedImages（轻量列表对话，图价值低；WIRE_KEYS 已允许，但模型不被提示即不返回，无副作用）。
7. **✅ A 数据路径 + 生产装配 + app 接线**：
   - `AndroidCanonicalAssetVault.readUriBytes(uri)`：读本地 content/file URI 字节（限额，本地绝不联网）。
   - `CaptureWorkflowRepository.readTutorSessionSheetBytes(sessionId)`（domain 接口，默认 null）+ `RoomCaptureWorkflowRepository` 实现（读 confirmed session → sourceImageUri → vault.readUriBytes）。
   - `AttachedImageGeneratorFactory.create(context, configurationStore?, networkRequestsAllowed, resolveCurrentSheetBytes)`——store 为 null 时返回恒 null resolver（不显示图）；否则共享凭证门 + SSRF 守卫 + asset vault 装配。
   - `SmartMistakeBookRoot` 向 `CapturedTutorSessionRoute` 传 `attachedImageResolver = AttachedImageGeneratorFactory.create(..., resolveCurrentSheetBytes = { captureRepository.readTutorSessionSheetBytes(sessionId) })`。
   - **并发断点**：core:data 编译曾被 peer 未提交的 `MistakeOrganizationRepository`(domain) 加 abstract 方法而 `RoomMistakeOrganizationRepository` 未实现所阻（与 attachedImages 无关），已收敛（peer 补上）。
   - 验证：`:core:model/test` + `:core:ui/testDebugUnitTest` + `:core:data/testDebugUnitTest` + `:feature:tutor/testDebugUnitTest` + `:app:compileLocalFirstDebugKotlin` 全绿。
8. **✅ MCP server `generate_process_figure` 工具**：
   - `OpenAiEditsClient.generate(instruction, sourceImage?, sourceMimeType?)`：无源图 → `POST /v1/images/generations`（文生图 JSON body）；有源图 → `/v1/images/edits`（图生图 multipart）。`redrawClean` 重构复用共享 `execute(http)`（DRY）。加 `ImageGenerationRequestBody` 数据类。
   - `ImageMcpServer` 注册 `generate_process_figure` 工具：输入 `instruction`(必填) + 可选 `source_image_b64`/`source_mime_type`，输出 status/image_b64/mime_type/model。复用 `redraw_clean_problem` 的 errorResult 模式。
   - 测试：`OpenAiEditsClientTest` 加 文生图(generations)、图生图(edits)、源图/mime 必须成对 3 例。MCP 模块 `test --rerun-tasks` 全绿（6 例）。
   - 注：Android app 不经 MCP 协议（直接打 OpenAI）；此工具仅给外部 MCP 主机（Cursor/Claude）用。
9. **—— TODO ——** 端到端（模型返回 attachedImages → 本地生成 → 对话显示）未上真机/真模型验证（缺 gpt-image-2 凭证/pilot）；`image-pipeline-spec.md` §6 已标 gpt-image-2 生成式漂移 risk。
10. **总结**：模型契约、生成通道、渲染组件、数据编排、UI 接线、工厂、parser/prompt、A 数据路径、app 装配、MCP server 10 块完成。剩余：真机 pilot（需 gpt-image-2 凭证）。

## ★ OpenMAIC 借鉴移植（2026-09-06）：AI 教师标注层·聚光灯

> **⚠️ 已被隔离取代（同日稍后）：** 本小节的神馆灯移植，以及整个 2D/3D 结构化场景渲染，随后（2026-09-06）被用户决定**从产品隔离、暂停开发**。权威声明见 `docs/model-first-product-boundaries.md` "2026-09-06 结构化场景渲染隔离"。`TutorVisualSpotlightOverlay`/`spotlightMaskRects` 等代码保留但被隔离不触发；`attachedImages`（位图）是另一功能不受影响。

**参考来源**：`THU-MAIC/OpenMAIC`（清华，Web/React，AGPL-3.0 但源码获用户授权套用交互机制）。clone 到本机 `AppData\Local\Temp\openmaic`。核心借鉴点：**SpotlightOverlay 聚光灯**（SVG mask 挖空 + 圆角白框 + 弹性动画）+ `Action` 动作契约（spotlight/laser/wb_draw_*）。

**兼容性判断**：OpenMAIC 是 Web(React/SVG/Three.js)，本项目 Android Compose/Filament——**源码不能 copy，移植交互机制**。

**已移植：聚光灯（教师聚焦强调）**
- `core/visual-runtime/.../TutorVisualLayoutEngine.kt`：加**纯函数** `spotlightMaskRects(panelWidth, panelHeight, target, inflate)` —— 返回目标四周 4 个遮罩矩形（annulus 拼接，不碰 BlendMode，稳）。`TutorVisualDocumentRuntimeTest` 加 2 例（中央 tile 不盖目标、贴边 degenerate slice 过滤）。
- `core/visual-ui/.../TutorVisualSpotlightOverlay.kt`（新）：`@Composable` 标注层——读 `frame.step.primaryRelationElementId` + `layout.nodes/connectors.bounds`，画 4 个遮罩矩形(Ink alpha 0.38) + JadeSoft 光晕 + JadeActive 圆角框(弹性收缩 320ms)。颜色走 `MaterialTheme.colorScheme`(app 层映射 SmartColors，贴合 UI 风格)。
- `core/visual-ui/.../TutorVisual2DPanel.kt`：BoxWithConstraints 内、Content Canvas 之后叠 `TutorVisualSpotlightOverlay(matchParentSize())`——**不拦截学生拖拽/点选**(只画不消费输入)。
- **验证**：runtime+ui JVM 测试全绿；**instrumented 6 例全绿**(模拟器真渲染)——聚光灯不破坏面板切换/全屏/状态恢复/3D 内存检查/无效场景 fallback。

**待人工确认(诚实)**：instrumented 只验证"没破坏"，**未断言聚光灯视觉正确**。遮罩/框的视觉需人看渲染截图确认（尤其目标 bounds 与 layout px 坐标系对齐、focused 元素是节点还是连线时的框形态）。

**下一步建议**：① 加激光笔(LaserPointer)——同理，红点指向目标；② 若需要，把 `TutorVisual2DPanel` 的选中/拖拽 `frame.step.focusElementIds` 多个焦点也纳入；③ 人工在模拟器截图确认聚光灯视觉效果。

## 5. 剩余工作（真实工作量 + 决策点）

**🔴 生产装配（core:data 需再暴露一个公开工厂）**：`attachedImageResolver` 的装配需要 `AndroidCanonicalAssetVault`(internal,需 context) + `OpenAiImageGenerationChannel`(经 `resolveGuardedEdits` SSRF 守卫) + 当前题面 resolver + `relativePath→file://` URI resolver——这些大多是 core:data 的 `internal` 类，app 层跨模块不可见。所以要在 core:data 加一个公开 `AttachedImageGeneratorFactory.create(...)`（类似 `ConfiguredCleanImageGeneratorFactory`），封装 internal 细节，app 层调它接成 `attachedImageResolver`。
**🟠 模型 prompt 规则**：`OpenAiModelTaskAdapters` 给模型写能返回 `attachedImages` 的系统规则（描述什么图、禁 URL/pixels/颜色指令、REDRAW 与 GENERATE_PROCESS 的语义）。安全敏感，贴合此前 T4 的强白名单。
**🟢 MCP server**：`generate_process_figure` 工具（图生图/文生图），可选，独立给外部主机用。

> Blocked-until-known：concurrent `split-large-files` 切占影响 `core:database`/`core:data` 编译；本 plan 只涉 core:model/core:ui/feature:tutor + tools/image-mcp-server（独立），尽量避开。
