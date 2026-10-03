# 阶段 4B「智能体生图与文档导出」实施计划（2026-10-03）

> 依据：实施契约 `docs/research/2026-09-28-imagegen-and-export-design.md`（A1–A6 + B1–B6；四条前置约束已由用户 2026-09-28 逐条确认：重绘只限图形题 / 生图费用只记账不确认 / 导出形态=练习卷（含作答空白区，答案解析可选默认关）/ 先参数化现有模板、Typst 二期）；
> 主线 4B 行（退出门："生图→导出→打印真机走查、A/B 用例绿"）；phasing 4B（依赖 2、4A）；4A 已收口（`docs/research/2026-10-03-stage4a-completion-record.md`，tip `2dccc574`）。
> **用户 2026-10-03 四项裁定**：① B3=模型只提议版式参数、**可按用户要求给出候选题**、**学生在候选中再勾选**（payload 不含题 id）；② AI 标识**只做隐式元数据**（显式角标不做，登记）；③ 分发=**系统分享面板**（不引微信/QQ SDK）；④ **不新增应用内预览**（打印=系统打印对话框，自带预览 + "另存为 PDF"，零外部设备依赖）。

## 0. 基线勘误（本轮勘察实测；设计稿已过时半代）

1. 工具面现为 **7 枚**（D-M M7 之后）→ `GENERATE_FIGURE` 实为**第 8 枚**：`MAX_TOOL_DECLARATIONS` 7→8、Respond 硬编码名单、3+ 处测试硬断言 7 同步。
2. 4A 已把渲染器/分页抽成 `MistakePdfPagePlanner.kt` / `DeterministicMistakePdfRenderer.kt` → B1/B2 锚点以此为准（设计稿 `MistakePdfExporter.kt:454-785` 失效）。
3. 4A 已删生产预览面（`MistakePdfPreview` 生产零消费者）。
4. `START_EXPORT` 现状与 B3 冲突：空 payload + `NOT_WIRED_YET`，KDoc 明写"模型不该也不能指定"。

## 1. 关键事实基线（file:line 已核）

- **A1 触点 6 层**：`core/model/.../TutorToolLoop.kt:12-27`（枚举）/`:96-260`（契约与豁免 `:174-182`、方向位互斥 `:193-197`）/`:511-576`（授权矩阵）/`:587`（`MAX_TOOL_DECLARATIONS=7`）；`TutorToolDescriptions.kt:11-76`（两处文案穷举）；`TutorToolTrace.kt:207-215`（学生可见名）；`core/domain/.../TutorToolGate.kt:22-45`（声明集/写工具集）；`TutorPermissionPolicy.kt:87-137`（档位/确认卡）；`core/data/.../study/RoomTutorToolRunner.kt:180-201`（执行分派；**现无资产库/生成器依赖，需 app→`RoomModelTaskRepository`→runner 三层注入**）；协议 `OpenAiModelProtocol.kt:229-270/:354-549/:602-667` + `OpenAiModelResponseParsers.kt:437-489`（两路由 schema/解析）；`feature/tutor/TutorModelTaskPolicy.kt:491-500`（Respond 硬编码 7 枚）；硬断言 7 的测试点：`TutorModelTaskPolicyTest:928/:1148`、`RoomModelTaskToolLoopInstrumentedTest:590`、`TutorToolCarrierValidationTest:64-65`。
- **图片链路**：生成通道**已生产可用**（`OpenAiImageGenerationChannel.kt:26-141`：`/v1/images/edits`（有源图）/`/v1/images/generations`（无源图），默认 `gpt-image-2`），但只接采集重绘与讲题附图（`RoomCaptureWorkflowRepository.kt:509-542`、`SmartMistakeBookDestinations.kt:366-373`），**工具环零调用**（A1 缺口）；**A2 缺口**：`AndroidCanonicalAssetVault.persistCleanImageBytes:133-171` 只写文件不写 DB（canonical 行/引用全缺 → GC 看不见、URI 只活 Compose `remember`）；`SourceAssetType` 3 值（`StudyDbValue.kt:40-44`，TEXT 常量，**加值零 schema**）；引用表 `problem_revision_source_asset`（role=CLEAN_IMAGE）与 `tutor_message_source_asset` 已在；导出取图**只取第一张 CLEAN_IMAGE**（`MistakePdfEligibility.kt:152-162`）、批量不合并 cleanImage（`MistakePdfBatchEligibility.kt:54-98`）。
- **导出管线（4A 后）**：`MistakePdfEligibility.kt:185-201`（输入）/`:249-260`（指纹）；`MistakePdfPagePlanner.kt:20-27`（版式常数）/`:214-226`（字号表）/`:243-273`（计划项）；`DeterministicMistakePdfRenderer.kt`；`MistakePdfDelivery.kt`（预览/打印/SAF/分享同源）；hub + `ExportPdfWorker`（4A，schema 61）。
- **B3 现状**：`TutorLocalAction.kt:90-124`（休眠参数设施）、`TutorLocalActionExecution.kt:87-107`（NOT_WIRED_YET）/`:113-128`（payload=空集）、`AgentPendingRequest.kt:197-248`（校验）、Route A 广告 `OpenAiModelProtocol.kt:285-336`。
- **合规（隐式元数据字段）**：属性（AI 生成）+ 提供者 + 编号；办法第 4 条显式标识本轮不做（登记）。

## 2. 外部资料（本轮收集）

1. **分享**：Android 官方《Send simple data》+ FileProvider 参考 → `ACTION_SEND` + 最具体 MIME + `createChooser`，接收方经 content URI 取文件；微信开放平台（`WXFileObject` 支持一般文件；Android 7+ 建议 FileProvider）→ **系统面板即主流，不引 SDK**。**单一来源风险（登记）**：微信作为系统面板 target 对 `application/pdf` 的接收行为未文档化 → 真机走查实测。
2. **生图 API**:OpenAI（`/v1/images/generations|edits`，b64）/ Gemini（内联 base64，SynthID）/ DashScope（异步任务 + URL；OpenAI 兼容层无 images）/ 智谱（同步 URL，`watermark_enabled` 默认 true）→ **复用现有自研通道**；MIT 级客户端库（openai-kotlin 等）均不适配 BYO-key 多 provider → 不引。
3. **Typst**：`typst/typst` Apache-2.0、无官方 Android 产物；WASM 路线（java-typst，Chicory，**minSdk 26 与项目 23 冲突**，20★ 单作者）/ NDK 交叉编译（无先例，预计 +15–25MB）→ **B5 只出研究**。
4. **合规**：CAC《人工智能生成合成内容标识办法》（2025-09-01 施行）第 4 条（显式标识、"下载/复制/导出功能应确保文件中含有显式标识"）+ 第 5 条（隐式元数据：属性/提供者/编号）；GB 45438-2025 强制国标（发布日单一来源标注）。
5. **端侧生图不可行**（MediaPipe Image Generator 疑似下线；模型数 GB、需 Android 12+/OpenCL）→ 云端 BYO-key 是唯一务实路线。

## 3. 四批设计与验收

### 批 1 · 生图工具可达（A1 + A3 + A5 + A6；零 schema）
- **A1**：`TutorToolName` 第 8 枚 `GENERATE_FIGURE`；参数 `{kind: REDRAW_PROBLEM|GENERATE_PROCESS, description ≤200}`，REDRAW 源图自动取自当前题规范资产（**绝不接受模型供图**）；结果只回 id+状态；8 处触点全改 + `MAX_TOOL_DECLARATIONS` 7→8 + Respond 名单；执行器把生成器/clean-redraw 穿三层注入；两路由 schema/解析**同源**；测试：3 处硬断言 7→8、非法 kind/超长 description 拒、8 工具面可达。
- **A3**：门单源（`ConfiguredCleanImageGenerator` 删内联门→`resolveImageCredential`；`savedMistakeSheetBytes`→`vault.resolve`）。
- **A5**：`tools/image-mcp-server/README.md` 首行标注"仅开发用；无 SSRF/凭证防护；已被 App 内 GENERATE_FIGURE 取代"（不删目录）。
- **A6**：删大厅 `attachedImages` 解析（wire key + 指纹 strip）；Respond/Plan 降级为兼容字段（仍解析仍渲染）。
- **门**：全量 JVM + `core:data`/`app` 定向仪器化（工具环/工具卡）+ app 三屏；独立复核；显式清单提交。

### 批 2 · 图持久化、幂等与生图 UX（A2 + A4 + 隐式元数据；零 schema）
- **A2（最重）**：`SourceAssetType.GENERATED_FIGURE`（代码值）；生成结果 canonical + 引用注册（题重绘→`problem_revision_source_asset` role=CLEAN_IMAGE；会话图→`tutor_message_source_asset`）；幂等键 `sha256(源图sha+prompt+size+quality+format)`；状态机 `PENDING→GENERATED→REFERENCED`；UI 从**持久引用**重建（旋转/进程重建不重复出网）；"一次生成一次付费"调用计数用例钉死。
- **A4**：三态 `GENERATING/FAILED(重试走幂等键)/READY`；重绘静默失败→工具卡灰色小字"图重绘未完成，已保留原图"；成功→"本次生成已计入额度"；**记账=次数/时间/模型/用途**（不猜金额），查看入口登记阶段 6。
- **隐式元数据**：生成图文件写 属性+提供者+编号（PNG tEXt / EXIF / XMP 视格式）；随导出/分享保留；显式角标不做（缺陷册登记"显式标识未落（办法第 4 条），对外分发前重开"）。
- **门**：全量 JVM + DB 仪器化 + app 定向仪器化（幂等/三态/元数据读回）；复核；提交。

### 批 3 · 排版参数化与练习卷（B1 + B2 + B4 + B5；零 schema）
- **B1**：`MistakePdfLayout`（`templateId: practice_sheet|compact|with_answers`、`marginPt 24..72`、`fontScale 1..3`、`columnCount 1|2`、`blockOrder` 白名单、`imageScale 0.5..1.0`、`includeAnswer/Solution/Note` 默认 false + `validate()` + `DEFAULT`）；进 `MistakePdfExportInput` 与 `exportFingerprint`（全字段）。
- **B2**：planner/renderer 参数化（常数注入、`PdfLineStyle` 缩放）；练习卷**作答空白区**（选择 1 行高×≤6；段落 `max(3, 字数/25)` 行高细线框；跨页 greedy + `keepWithNext`）；**答案/解析独立成区**（用户 2026-10-01 裁定：题目区在前、答案/解析区在后）；`includeSolution` 无数据源→`UNSUPPORTED_LAYOUT_FEATURE` fail-closed；`columnCount=2` 且图超半栏→缩半栏。
- **B4**：三链路回归（预览第 0 页尺寸/aspect 不变、**打印字节=prepared 文件字节**、部分页重渲染）+ 新建金样对拍机制（确定性渲染 sha256 摘要；不稳定则退化"结构断言 + 单机摘要登记"并标注）。
- **B5**：`docs/research/typst-android-feasibility.md`（两路线/体积/许可/minSdk 26 冲突；结论门 ≤15MB 且许可允许→二期立项）。**不进本批门禁**，随批交付。
- **门**：全量 JVM + `core:export` 仪器化 + hub/app 导出类仪器化；复核；提交。

### 批 4 · 智能体触发导出与 4B 综合门（B3 + 走查 + 收尾）
- **B3（按裁定口径）**：`START_EXPORT` payload 白名单首次落地 `{templateId, layout}`（枚举 + `validate()` + 缺字段拒）+ 确认卡展示模板名与关键参数；**执行从 `NOT_WIRED_YET` 改真接线**：确认后打开**导出 sheet**——① 版式参数表单（预填模型提议值，可改）；② **候选题目 = 本轮检索/工具结果在本地留痕的题**（模型"选出"的过程即其检索并展示的过程）默认列出，学生**在其中勾选**（也可库内再搜）；③ 确认 → 后台导出（`ExportPdfWorker` 带 layout）→「导出成果」hub。**payload 不含题 id**（无编造面）；同步改 `TutorLocalActionExecution.kt` KDoc 与空 payload 口径。
- **4B 综合门**：全量 JVM + DB 全套 + app 全套 + R8 冒烟；**真机走查**：生成一张图 → 图持久化（重启后仍在）→ "导出成两栏练习卷，不含答案" → 确认卡 → 候选勾选 → 参数化渲染 → 系统打印对话框预览 + **「另存为 PDF」** + 分享面板（**微信是否接收 PDF**：实测记录）+ 承接 4A 遗留（真实通知点击/SAF 保存）。
- **收尾**：完成记录独立成文 + 主线 4B 行 + 台账 §3.18（零算法 bump + 提示词版本 bump 自记）+ push。

## 4. 版本与迁移预算

- **零 schema**（A2 的 sourceType 是 TEXT 常量值；若实现中发现契约白名单需改=纯代码）。
- **ModelEgress 提示词版本 bump**（新工具 + 导出参数声明；既有惯例自记）；`LearningCoreVersions` 不动；台账 §3.18 零算法 bump 行。

## 5. 冲突裁决与登记

① 设计稿"第 6 工具"→第 8 枚（勘误）；② B1/B2 锚点改为 4A 抽取后的文件；③ START_EXPORT 口径按本轮裁定合并（模型提版式+候选、学生勾选；payload 不含题 id）；④ 微信接收 PDF 行为未文档化 → 真机走查记录；⑤ 生图不扩 provider（登记）；⑥ `includeSolution` fail-closed；⑦ A5 标注不删；⑧ 显式 AI 标识未落 → 缺陷册登记；⑨ 预览面不恢复（系统打印对话框=预览 + 另存为 PDF）；⑩ provider 水印（智谱默认 true / 不得去水印）写进登记。

## 6. 纪律与门

共享工作树：不碰他线（KB docs/tools）；显式文件清单提交；每批"实施 → 正式门 → 独立复核（新 Agent、只读、对抗性）→ 修复轮 → 提交"；门口径同 4A（全量 JVM、DB 仪器化冷启严格口径、app 仪器化、R8 冒烟）；不宣称项目安全（Mimosa 边界）。

## 7. 风险与 UNVERIFIED

- **A2 最重**（持久化+幂等+UI 重建）；"一次生成一次付费"用例必须钉死；幂等键含全部生成参数。
- **A1 穿层注入**（app→repository→runner）是结构性改动，不得破坏既有 7 工具用例。
- **金样对拍**是新机制；CI 字体/系统差异导致不稳定时按退化方案并如实标注。
- **隐式元数据**需真机验证可读回且导出/分享后仍在。
- **打印**：真实打印机路径依赖系统打印服务（与 App 无关）；本轮走查用"另存为 PDF"闭环。
- **微信接收 PDF**、真实通知点击、SAF 保存：真机走查项（承接 4A 遗留）。
- B5 立项结论以研究报告为准（体积 ≤15MB + 许可双门）。
