# 智能体生图与文档导出：实施级设计（2026-09-28）

- 定位：阶段 **4B** 的实施契约。每条目八段（证据→目标→改法→迁移/指纹→测试→依赖→完成判据），
  未来模型照做即可，无歧义。
- 已裁定前置约束（2026-09-28，用户逐条确认）：
  1. 重绘范围 = **只重绘图形题**（`WITH_FIGURE`，现状维持；题干/选项/公式仍是模型转写的结构化文本）。
  2. 生图费用 = **只记账不确认**（静默生成，记入 D2 后台可见额度，无确认卡）。
  3. 导出形态 = **练习卷**（题目+作答空白区；含答案/解析/备注为可选项，默认关）。
  4. 引擎节奏 = **先参数化现有模板；Typst 为二期**（二期只出研究交付，不实施）。
- 上游证据：两个只读审计（生图子系统 / 导出管线）已核实全部 file:line。

---

## A · 生图进工具面（A1–A6）

### A1 · 第 6 工具 GENERATE_FIGURE

- **证据**：`TUTOR_TOOL_DECLARATIONS` 仅 5 工具（`core/domain/.../TutorToolGate.kt:22-28`）；模型只能
  经 `TutorPlanOutput/RespondOutput.attachedImages` 字段"申请附件"（`core/model/.../TutorTasks.kt:875,914`），
  不能在工具环调用生图。
- **目标**：模型在任何轮次可调用生图工具；只重绘 `WITH_FIGURE` 题的图形部分。
- **改法**：
  1. `TutorToolName` 增 `GENERATE_FIGURE`；`TUTOR_TOOL_DECLARATIONS` 增一条（6 工具面）。
  2. 参数形状（JSON schema，严格）：`{ "kind": "REDRAW_PROBLEM" | "GENERATE_PROCESS",
     "description": String ≤200 字符 }`——REDRAW_PROBLEM 的源图**自动取自当前题规范资产**，
     绝不接受模型供图；GENERATE_PROCESS 为纯文生图（提示词含 description + 当前题上下文）。
  3. 授权矩阵（`TutorToolLoop.kt:373-425`）：GENERATE_FIGURE 进入声明集×意图矩阵；门槛沿用具名规则。
  4. 轮预算：生图结果计入结果预算（与 5 工具同上限口径，2000/6000 字符体系；图结果只回 id+状态，
     不回字节）。
  5. 执行器：`RoomTutorToolRunner` 增 `generateFigure` 分支——REDRAW_PROBLEM 复用
     `redrawAndAttachClean` 链（`RoomCaptureWorkflowRepository.kt:471-485`）；GENERATE_PROCESS 复用
     `AttachedImageGenerator.resolve`（`core/ui/.../AttachedImageCard.kt:46-57` 背后的生成器）。
  6. 结果回填：下一轮 prompt 回填 `已生成图 id/状态`；工具卡灰色小字呈现（K2c 纪律）。
- **迁移/指纹**：工具声明变化 → prompt 策略版本 bump（`ModelPromptPolicyVersions`）+ 指纹纪律
  （strip 空载体 + 4 用例）。
- **测试**：① 先红：模型调 GENERATE_FIGURE 在 5 工具面下被拒 → 接 6 工具面后可达；
  ② REDRAW_PROBLEM 无 WITH_FIGURE 题时返回"无图可重绘"不报错；③ 参数超限（description>200）
  解码层拒；④ 轮预算超限行为与 5 工具一致。
- **依赖**：阶段 2（工具面泛化）完成。
- **完成判据**：工具环能调用、用例全绿。

### A2 · 持久化、引用与幂等缓存（最重缺口）

- **证据**：配图结果 `vault.persistCleanImageBytes` 后**不注册 canonical 行、不写引用**
  （`AttachedImageGeneratorFactory.kt:48-55`）→ orphan GC 永远看不见（`PendingCaptureDao.kt:30-61`
  只按行判孤儿）；图 URI 只活在 Compose `remember`（`AttachedImageCard.kt:138-140`）→ 旋转/进程
  重建**重新生成、重复付费出网**。
- **目标**：生成结果可被 GC 保护、可跨进程复用、同请求只生成一次。
- **改法**：
  1. 落库：生成成功后注册 `canonical_source_asset` 行（sourceType 新值 `GENERATED_FIGURE`，
     记录源图 sha 与 prompt sha）+ 按来源写引用——消息内配图写 `tutor_message_source_asset`，
     题重绘写 `problem_revision_source_asset`（复用 `attachCleanRedrawAsset` 路径）。
  2. 幂等键：`figureFingerprint = sha256(源图sha + prompt + size + quality + format)`；生成前
     查该键的既有资产，命中直接返回其 id（不再出网）。
  3. 图状态机（内存 + 持久引用）：`PENDING → GENERATED → REFERENCED`；UI 由持久引用重建
     （不再依赖 remember 里的 URI）。
- **迁移**：`canonical_source_asset.source_type` 新枚举值 + 非破坏迁移；GC 判据无需改
  （引用表清单已含两张表）。
- **测试**：① 旋转/进程重建后不重复出网（生成器调用计数）；② 同指纹二次请求命中缓存；
  ③ orphan GC 不删已引用生成图；④ 生成图被删除时 GC 能清。
- **依赖**：阶段 1（资产引用模型）。
- **完成判据**：四条用例绿 + 生成器调用计数测试钉死"一次生成一次付费"。

### A3 · 门统一为一份实现

- **证据**：三道门两处镜像（`ImageCredentialGate.kt:13-23` KDoc 自认"Mirrors the gating in the
  clean-redraw generator"）；`savedMistakeSheetBytes`（`SmartMistakeBookDestinations.kt:459-472`）
  用 app 层 sha256 比对替代 vault.resolve（`SavedMistakeSheetBytesTest` 存在但机制重复）。
- **改法**：`ConfiguredCleanImageGenerator`（`core/data/.../capture/ConfiguredCleanImageGenerator.kt:35-103`）
  删内联门，改调 `resolveImageCredential`；`savedMistakeSheetBytes` 改 `vault.resolve(record)`
  （删除本地 readBytes+比对）。
- **测试**：现有 `ConfiguredCleanImageGeneratorTest`（6 例）与 `SavedMistakeSheetBytesTest` 迁移后全绿；
  新增"门逻辑只存在一处"的变异测试（改 Gate 一处，两链都变）。
- **完成判据**：门单源 + 全部门测试绿。

### A4 · 失败 UX 与费用记账

- **证据**：配图占位只有"图尚未生成"文案（`AttachedImageCard.kt:97-104`），无"生成中/重试"；
  重绘失败静默保留原图，用户无感知。
- **改法**：
  1. `AttachedImageCard` 三态：`GENERATING`（转圈+文案）/ `FAILED`（重试按钮，重试走幂等键，
     不重复付费）/ `READY`。
  2. 重绘静默失败 → 工具卡灰色小字一条："图重绘未完成，已保留原图"。
  3. 费用：生成成功后在工具卡加"本次生成已计入额度"（**只记账不确认**，写入 D2 后台额度账）。
- **测试**：三态渲染；重试幂等；额度记账行存在。
- **完成判据**：三态 + 重试幂等用例绿。

### A5 · MCP server 处置

- **证据**：`tools/image-mcp-server` 是独立进程（不在 Android 构建，`settings.gradle.kts:19-33` 未
  include），`OpenAiEditsClient` 无 SSRF/凭证门（README 未标注"仅开发"）。
- **改法（二选一，推荐 B）**：
  - A：删除整个 `tools/image-mcp-server/`（文件清单：`ImageMcpServer.kt`/`OpenAiEditsClient.kt`/
    README 等，实施时 `git rm -r`）。
  - B：保留但 README 首行标注"仅开发用、无 SSRF/凭证防护、已被 App 内 GENERATE_FIGURE 工具取代"。
- **完成判据**：选 A 则模块消失；选 B 则标注存在。

### A6 · attachedImages 死分支清理

- **证据**：`TutorLobbyOutput.attachedImages` 被解析（`OpenAiModelResponseParsers.kt:489`）但
  `TutorLobbyRoute.kt` 对 AttachedImage 零引用（解析不渲染的死分支）。
- **改法（推荐）：删除大厅输出的 attachedImages 解析**（wire key 从 `TUTOR_LOBBY_WIRE_KEYS` 移除 +
  指纹 strip）；Respond/Plan 的 attachedImages 在新工具面（A1）落地后**降级为兼容字段**（仍解析、
  仍渲染，作为无工具端点 fallback）。
- **测试**：大厅输出含 attachedImages 时按未知键拒（fail-closed 不变）；旧行读取不抛异常。
- **完成判据**：死分支消除 + 旧行兼容用例绿。

---

## B · 导出排版（B1–B6，一期参数化 / 二期研究）

### B1 · 布局参数模型

- **证据**：`MistakePdfExportInput`（`MistakePdfEligibility.kt:185-201`）无布局字段；所有排版常数
  是 `DeterministicMistakePdfRenderer` 的 `private const`（`MistakePdfExporter.kt:454-785`）。
- **目标**：布局可参数化且进入缓存指纹。
- **改法**：
  1. 新类型 `MistakePdfLayout`：
     ```kotlin
     data class MistakePdfLayout(
       val templateId: String,          // "practice_sheet" | "compact" | "with_answers"
       val marginPt: Int,               // 24..72，默认 48
       val fontScale: Int,              // 1..3 档，默认 2
       val columnCount: Int,            // 1 | 2，默认 1
       val blockOrder: List<String>?,   // 空=默认顺序；非空=块类型顺序白名单
       val imageScale: Float,           // 0.5..1.0，默认 1.0
       val includeAnswer: Boolean,      // 默认 false
       val includeSolution: Boolean,    // 默认 false
       val includeNote: Boolean,        // 默认 false（备注）
     )
     ```
     含 `validate()`（域校验）+ `DEFAULT`。
  2. `MistakePdfExportInput` 增 `layout: MistakePdfLayout = DEFAULT`。
  3. `exportFingerprint`（`MistakePdfEligibility.kt:139-147,249-260`）纳入 layout 全字段
     （含 templateId）——换模板/参数必换指纹。
- **迁移/指纹**：无 schema；指纹变化自然使旧缓存失效（缓存键=指纹）。
- **测试**：同内容不同 layout → 指纹不同；非法 layout（margin=100）被 validate 拒；
  默认值序列化稳定。
- **依赖**：无。
- **完成判据**：指纹用例绿。

### B2 · 渲染器参数化 + 练习卷作答空白区

- **证据**：`DeterministicMistakePdfRenderer`（`MistakePdfExporter.kt:454-785`）单一固定模板，
  无作答区概念。
- **改法**：
  1. `private const`（页边距/字体表/页脚高度）改为从 `MistakePdfLayout` 注入；`LineStyle` 按
     fontScale 档位缩放。
  2. **练习卷模板（templateId="practice_sheet"）新增作答空白区**：每 `ChoiceGroup` 与每 `Paragraph`
     块后按规则留白——选择题留 1 行高×选项数上限 6、小问/段落留 `max(3, 字数/25)` 行高的空白框
     （细线框）；留白跨页时 greedy 分页（复用现有 `keepWithNext` 机制）。
  3. `includeAnswer`：在题尾附"答案：…"（ChoiceGroup 的 selectedChoiceId → 选项文本）；`includeSolution`
     尚无解析数据源——一期先渲染"解析待接入"占位？**不**：一期若数据不可得，该项渲染为
     `UNSUPPORTED_LAYOUT_FEATURE` 并计数跳过（fail-closed，不留占位脏字）；`includeNote` 渲染
     `error_book_entry.user_note`（数据已在 `MistakeDetailRepository`）。
- **测试**：① 三模板各生成 PDF 且字节互异；② 作答空白区高度断言（金样对比）；③ includeNote
  渲染备注、默认不含；④ 非法组合（columnCount=2 且 imageScale=1.0 且图片宽超半栏）行为写清
  （缩到半栏或拒——**选缩到半栏**，写进测试）。
- **依赖**：B1。
- **完成判据**：三模板渲染 + 空白区金样绿。

### B3 · START_EXPORT 动作 payload 形状与白名单校验

- **证据**：`AgentPendingRequest.kt:200-210` 注释自认 payload 形状未定义；模型对排版零输入。
- **改法**：
  1. `START_EXPORT` payload 固定形状：`{ "templateId": String, "layout": MistakePdfLayout JSON }`；
     本地 `requireAgentPendingRequestPayload` 增该形状校验（模板 id 枚举 + `MistakePdfLayout.validate()`）。
  2. 模型侧 prompt：学生口头排版要求（"两栏""字大点""不要答案"）由模型映射到白名单参数；
     **模型不得输出任意排版标记**（守住 `StructuredContent.kt:7-10` 边界）。
  3. 学生侧：确认卡文案展示将用的模板名与关键参数（"两栏·含解析"）。
- **测试**：合法 payload 通过；非法 templateId/越界参数被拒；缺字段被拒。
- **依赖**：阶段 2（本地动作通道）+ B1。
- **完成判据**：payload 校验用例绿。

### B4 · 预览/打印/保存链路复用验证

- **证据**：三条链路同源（`MistakePdfDelivery.kt:81-316`），参数化后不应破坏。
- **改法**：无功能改动；补齐测试：参数化布局下 ① 预览第 0 页 bitmap 尺寸与 aspect 不变；
  ② 打印全页请求流式复制字节 = prepared 文件字节；③ 部分页打印位图重渲染仍正确。
- **完成判据**：三链路字节一致用例绿。

### B5 · 二期研究任务定义（只出研究，不实施）

- **目标**：产出 `docs/research/typst-android-feasibility.md`，回答四问：
  ① java-typyst 许可证（是否可商用嵌入）；② WASM 二进制体积（APK 增量）；③ minSdk 26 兼容与
  编译性能基准（单题文档编译 ms 级？）；④ 与现有 MathBox 公式渲染的取舍（Typst 原生数学 vs
  保留 Canvas MathBox）。
- **结论门**：体积增量 ≤ 15MB 且许可证允许 → 立项 Typst 模板集（二期实施）；否则维持参数化
  模板路线。
- **完成判据**：研究报告落盘 + 立项/否结论。

### B6 · 干净图块进导出

- **证据**：`MistakePdfEligibility` 的 cleanImage 取自 `role == "CLEAN_IMAGE"` 的 source asset
  （`MistakePdfEligibility.kt:12,152-162`）；重绘图经 A2 持久化后成为正规引用。
- **改法**：A2 落地的重绘资产（`problem_revision_source_asset` 中 role=CLEAN_IMAGE）自然进入现有
  cleanImage 逻辑——**核实引用表与 role 一致，无代码改动则仅补测试**；无重绘图时回退现状
  （结构化图块渲染，原图不导出）。
- **测试**：有重绘图的题导出含图；无重绘图的题按现状。
- **完成判据**：两用例绿。

---

## 阶段 4B 退出门

A1–A6、B1–B4、B6 全部用例绿 + 真机走查一条完整链路：**智能体生成一张图 → 图持久化 → 学生说
"导出成两栏练习卷，不含答案" → START_EXPORT 确认卡 → 参数化渲染 → 预览/打印字节一致**。
B5 为研究交付，不进本阶段门禁。
