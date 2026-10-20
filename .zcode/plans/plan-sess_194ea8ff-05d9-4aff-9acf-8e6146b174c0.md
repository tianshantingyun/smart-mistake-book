# 生图与导出文档：单开阶段 + 实施级设计文档（无歧义、可照做）

> 2026-10-18后续接线更新：本文件保留历史任务/裁定与执行范围，不作为重跑旧MCP、固定图像后端或旧schema的指令。后续转写/净化/讲解/编辑/备份中的新图接线统一遵循[共享规则](../../docs/current/figure-generation-prompt-policy.md)、[4E的WG清单](../../docs/superpowers/plans/2026-10-18-flexible-js-drawing-implementation.md#10-全入口接线任务与后续阶段承接)及[后续阶段计划](plan-2026-10-18-followup-figure-wiring.md)；完成/未完成状态以[主线](../../docs/REFACTOR-MASTER-LINE.md)最新记录为准。

## 阶段划分（并入现有分阶段，单开一阶段）

在 `phasing` 文档插入 **阶段 4B · 智能体生图与文档导出**：
- 依赖：阶段 2（工具面泛化，第 6 工具挂在统一工具环上）+ 阶段 4A（错题本/录入的导出链路）。
- 内容：A 生图工具面（6 项）+ B 导出排版（6 项，一期参数化；二期 Typst 只做研究任务定义，不实施）。
- 退出门：A/B 各项各自用例绿 + 真机走查一条"生图→入导出→打印"链路。

## 设计文档（`docs/research/2026-09-28-imagegen-and-export-design.md`，实施级）

**每条目固定八段**（与路线图同模板，杜绝歧义）：现状证据（file:line）→ 目标行为（可验收表述）→
具体改法（逐文件逐函数，含字段/参数/DDL/形状定义写全）→ 迁移/指纹影响 → 测试（先红后绿+边界）
→ 依赖 → 完成判据。

### A 生图工具面（6 项，已裁定的四条先落为前置约束）

- **A1 第 6 工具**：`GENERATE_FIGURE` 加入 `TUTOR_TOOL_DECLARATIONS`（`TutorToolGate.kt:22-28`）；
  参数形状定义写全：`{kind: REDRAW_PROBLEM|GENERATE_PROCESS, description: String(≤200 字符),
  target: 当前题图形|无}`；授权矩阵（`TutorToolLoop.kt:373-425`）的意图×声明集×置信阈值条目；
  轮预算与结果预算挂接；工具卡呈现复用 K2c 灰色小字。约束写入：**只重绘 WITH_FIGURE 的图形部分**。
- **A2 持久化与幂等缓存**：生成结果注册 `canonical_source_asset` 行 + 写 `tutor_message_source_asset`
  或题引用（落哪张表按消息/题两种来源分别写清）；幂等键 = sha256(源图sha + prompt + size/quality/format)；
  命中缓存直接复用文件；GC 判据对照（`PendingCaptureDao.kt:30-61` 引用表清单）逐条核对。
- **A3 门统一**：`ConfiguredCleanImageGenerator` 改调 `resolveImageCredential`（`ImageCredentialGate.kt:13-23`）
  单源；`savedMistakeSheetBytes`（`SmartMistakeBookDestinations.kt:459-472`）改走 `vault.resolve(record)`。
- **A4 失败 UX 与费用记账**：占位图"生成中/重试"两态（`AttachedImageCard.kt:97-126` 改造点写明）；
  重绘静默失败改为工具卡可见一条状态；费用**只记账**（记入 D2 后台额度，无确认卡），工具卡显示
  "本次生成已计入额度"。
- **A5 MCP server 处置**：`tools/image-mcp-server` 停用（README 标注"仅开发用、无 SSRF/凭证防护、
  已被 App 内工具面取代"）或删除——二选一，给删除清单（文件列表）。
- **A6 死分支清理**：`TutorLobbyOutput.attachedImages` 解析保留或删除的取舍（倾向删）；大厅
  `TutorLobbyRoute` 零引用处补渲染或删解析，写清二选一。

### B 导出排版（6 项，一期参数化 / 二期研究）

- **B1 布局参数模型**：`MistakePdfExportInput` 增加 `layout: MistakePdfLayout`（字段：templateId、
  marginPt、fontScale 档位、columnCount 1|2、blockOrder、imageScale、includeAnswer/includeSolution/
  includeNote 布尔）；**全部字段进 `exportFingerprint`**（`MistakePdfEligibility.kt:139-147,249-260`
  改造点）。
- **B2 渲染器参数化**：`DeterministicMistakePdfRenderer`（`MistakePdfExporter.kt:454-785`）的
  `private const` 改为从 `MistakePdfLayout` 注入；**"练习卷"默认模板新增作答空白区**（每小问后
  留白高度估算规则写清）；含答案/解析/备注开关的渲染分支。
- **B3 动作 payload 形状**：`START_EXPORT` 的 `AgentPendingRequest.payload_json` 固定形状
  `{templateId, layout}`，白名单校验（模板 id 枚举 + 参数域校验）；模型只能选不能造；
  学生口头要求 → 模型转参数的链路（prompt 侧说明）。
- **B4 链路复用验证**：Typst 不在本期——预览/打印/保存继续同源（`MistakePdfDelivery.kt`），
  只验证参数化后三链路字节一致 + 部分页打印仍正确。
- **B5 二期研究任务（只出研究交付，不实施）**：java-typyst 许可证核验、WASM 体积、
  minSdk 26 兼容、编译性能基准；产出 `docs/research/typst-android-feasibility.md`；
  结论门：体积/许可证达标才立项 Typst 模板集。
- **B6 干净图块进导出**：重绘图持久化（A2）后作为导出图块来源接入 `MistakePdfEligibility`
  的 cleanImage 逻辑；无重绘图时回退现状（结构化图块/原图不导出）。

## 执行（批准后）

1. 写设计文档（上述结构，预计 400+ 行，全部带 file:line 与八段模板）。
2. `phasing` 插入阶段 4B 行与依赖说明；台账追加两条裁定（生图进工具面 + 练习卷参数化/Typst 二期）。
3. 显式文件列表提交 + 推送 main。不改产品代码。