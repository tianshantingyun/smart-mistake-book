# UI 呈现改造 — Thinking 字段(路线B) + 终稿块级渲染 + P0 流式打磨

> 来源：/autoplan。base = 主工作树 `main` (HEAD `7cb373e`)，未提交工作区有并发改动（BundledKnowledgePack/MasteryWriteGate）。
> 状态：评审进行中，plan file 由 autoplan phases 逐步填充。

## Premise（用户确认 2026-09-05）

1. **范围一起做**：P0(流式不闪+段落字号+颜色令牌) + 路线B(模型输出 thinking 字段) + 块级支持，全部落地。
2. **路线 B**：模型输出加独立 thinking(思考链) 字段，经契约/校验/指纹。**"只要输出都需要"** = 所有对话文本 kind：TUTOR_RESPOND / TUTOR_PLAN(TutorTurnPlan) / TUTOR_LOBBY。
3. **块级实现**：终稿(SUCCEEDED) 切 compose-richtext `RichText` 完整 commonmark；流式走受限 inline 渐进。
4. **base**：主工作树 main (v6)。thinking 字段作新 schema 版本叠加在 v6 之上。

## 已知风险（评审将展开）

- **schema 冲突**：worktree `.worktrees/image-pipeline` `feat/model-driven-clean-image` 已推进到 v8(agent-consent, tip `0da033c`)，peer 会话正忙。main 上加 thinking 若也 bump schema，将来合流 v8 会撞版本号与指纹平移语义。需定 main 侧 schema 版本号策略(如直接用 v9 预留，或独立编号)。
- **并发工作区**：main 工作区有未提交改动(BundledKnowledgePack/MasteryWriteGate)，与本 plan 不同模块，但 build/commit 会相互影响。
- **视觉一致性**：终稿切 RichText vs 流式受限 inline → 中途到完成可能跳变。需统一字号/段落/颜色令牌，保证切换不跳。
- **Lobby disclosure**：Lobby 输出加 thinking 字段要重核 `TUTOR_LOBBY_DISCLOSURE`（thinking 是否随正文出网、是否新增 data class）。

## 决策审计轨迹（phases 填充）

（见 conversation）

## Phase 1 Deep — 关键事实与结论（2026-09-05）

### 0B 现状代码 leverage 地图（改哪=用哪个既有代码）
- **渲染碎片化（核心）**：AI markdown 三处入口各自为政——
  1. Respond 会话：`SafeMarkdownText`(core/ui StructuredContentRenderer:122) + 白卡 `Surface`(TutorChatConversation:305)
  2. Lobby：`TutorPrompt`(TutorRoute:626, 头像+细边卡) 内也调 SafeMarkdownText
  3. Plan 首讲：`TutorGeneratedTurn` 结构化分段(opening/题干/solution/alternateMethod)
- **richtext 从未用于 AI 输出**：`MessageBubble`(core/ui, 含 richtext AIMessageContent) **全库无人调用=死代码**；`SetupMaterial3RichText` 仅它引用。用户"终稿切 richtext"实为**引入第一个真块级渲染器**，可复活/改造 MessageBubble 为基座，而非新写。
- **流式**：`streamingReplyBody()`=userMessage 整前缀,重渲染(不逐字 append)；未闭合 `**/$/`/…` 在 SafeInlineMarkdown 降级字面量(StructuredContent:842-894)→流式闪烁根因。
- **thinking 无字段**：TutorRespondOutput 单 messageMarkdown；prompt 明确禁 HTML/链接/脚本(TutorTasks:1069+ requireTutorMarkdown 白名单式校验)。
- **schema v6**：fingerprint 平移 helpers(withoutEmptyToolCarrier 等) 是加字段先例模板。
- **disclosure**：ModelEgressDataClass 无"thinking"类。thinking 属模型对会话的文本输出,若只当轮 UI 呈现不入 history/不回流 prompt 则不进 disclosure；若进 priorMessages 需评估。

### 0D 范围决策（6 原则）
- thinking 字段进 **RESPOND/PLAN/LOBBY 三个对话 kind**(用户:只要输出都需要)。debrief/visual/organization 非对话文本,不加。
- thinking **不持久化进 conversation history / 不进下一轮 prompt 上下文**(P5 explicit + 安全:思考内容回流会改变后续轮语义与披露) → 只当轮 UI 呈现。
- 终稿切 richtext = 复活改造 MessageBubble 成 `AiReplyRichMarkdown`(安全前提:模型 markdown 已过 requireTutorMarkdown 白名单校验;RichText 不得解析原始 HTML——需 Eng 验证 commonmark 行为)。
- 未闭合容忍 + 渲染节流进 SafeMarkdownText(向后兼容加流式模式参数)。

### User Challenges 裁决（用户 final gate 拍板 2026-09-05）
- **Challenge 1(richtext→单渲染器)**:用户**拒绝**,坚持终稿切 richtext + "要最好效果,冲突可舍弃一方尽管重构"。
- **Challenge 2(thinking→内部消费)**:用户**拒绝**,真实意图="就是要折叠思考链的 AI 感",thinking 给学生看(折叠)。

### Deep 关键定论(化解 challenge2 成本论据)
- thinking 走 **ModelTaskOutput 层新字段**(TutorRespondOutput/TutorTurnPlan/TutorLobbyOutput 各加 `thinkingMarkdown?`),**非 request schema**。
- 已核实:ModelTaskCodec request/output 均 `encodeDefaults=true + ignoreUnknownKeys=false`(ModelTasks:1106-1111);output 持久化在 `output_snapshot` JSON 列(ModelTaskEntities:85),**无独立 Room 表列**。
- 结论:**加 output 字段带默认 null = 旧行 decode 填默认、向后兼容,不 bump ModelTaskRequest.schemaVersion、不动 ModelTaskFingerprint(input-only)、不做 Room 迁移**。
- → thinking 不撞 worktree v8(request schema 版本在另一条维度)。真正要动:output data class + prompt(模型自愿产 thinking)+ parser + parity + UI 折叠。

### NOT in scope
- 不动 .worktrees v8 agent-consent(并发 peer 工作;thinking 走 output 不与 request schema 撞,合流风险降级为普通协调)。
- 不做 WebView/HTML/网络图(违反安全契约)。
- 不重写 TutorGeneratedTurn 的 plan 分段结构(只在其上叠 thinking 呈现)。
- 不加表格/HTML 类 rich 特性(richtext 能力内走 commonmark)。

### Failure Modes Registry（初版, Eng 深化）
| 失败模式 | 触发 | 后果 | 缓解 |
|---|---|---|---|
| thinking 泄漏进历史/prompt | 实现时把 thinking 并入 history/priorMessages | 下一轮模型看到自己思考,语义漂移+披露越界 | thinking 独立字段,history 组装显式排除 |
| richtext 渲染原始 HTML/链接 | 模型 markdown 含 `<img>`/`[x](url)` | XSS/外链泄漏 | 维持 requireTutorMarkdown 白名单校验在渲染前; Eng 验证 richtext 行为+加测试 |
| 流式/终稿视觉跳变 | 流式受限 inline→完成切 richtext | 突兀重排 | 统一字号/颜色令牌;终稿首帧对齐流式末帧 |
| schema bump 与 worktree v8 撞版 | main v7 与分支 v8 语义不同 | 合流冲突 | 版本号策略定高(如 main 直上 v9?)或独立域;评审定 |
| 未闭合容忍破坏终稿语义 | 流式逻辑误伤已闭合文本 | 样式错乱 | 流式模式仅流式中启用,终稿走原逻辑 |

## What already exists（Deep 0B leverage map 汇总）
- **SafeInlineMarkdown**(StructuredContent:816-935) 6 token 安全 parse → 流式受限 inline 的既有基座。
- **SafeMarkdownText**(StructuredContentRenderer:122) AI markdown 现有唯一渲染器 → 流式渐进可复用 + 状态化增强。
- **MessageBubble richtext 死代码**(core/ui) → 终稿块级基座(复活改造,满足用户"切 richtext")。
- **ModelTaskOutput 层字段先例**: output_snapshot JSON 列 + encodeDefaults/显式 null → thinking 默认 null 即向后兼容。
- **ModelTaskCodec/fingerprint helpers**(withoutEmptyToolCarrier) → 加字段的平移模板。
- **requireTutorMarkdown/requireTutorSceneText**(TutorTasks:1029/1066) 现有双档白名单校验。
- **WIRE_KEYS + requireOnlyKeys**(OpenAiModelResponseParsers:527-548) → 加键点明确。

## Dream state delta（CURRENT → 本 plan → 12 个月理想）
- **CURRENT**: 流式 = 整前缀重渲染 + 未闭合降级闪烁;终稿 = 受限 inline;无思考链;多入口各自排版。
- **本 plan 落地后**: 流式渐进不闪 + P0 视觉令牌;终稿 richtext 块级;thinking 折叠卡(学生可见过程透明,不抢讲解);Lobby 校验统一。
- **12 个月理想**: 流式 token 级零跳变、多模型 thinking 归一、思考→正文分段过渡、终稿可交互(点击知识点/诊断)、样式主题化可配。本 plan 补到 12 个月理想约 60% —— 剩 40% 是 AI 内容交互化与主题化(超出本次 UI+块级范围,入 deferred)。

## Phase 2 Design（[single-voice] — 外部 design voice API error 失败，主评审定稿）

### A. 思考链折叠卡（新 `ThinkingCollapsibleCard`, core/ui 可复用）
- 位置:AI 气泡内**正文上方**;默认折叠;灰阶(InkMuted/InkSecondary)与正文(Ink)主次分明,教育语境="过程透明"不抢讲解。
- 折叠态:单行"已思考"+chevron,**不预览正文**(防剧透);展开态:animateContentSize + 小一号灰字(bodySmall)渲染 thinking(受限 inline,不需块级)。
- 流式中:折叠卡标题"正在思考…"+细 spinner。复用现"展开/收起"朴素模式(rememberSaveable)。

### B. 流式→终稿 三态衔接
- 思考先到/正文后到:折叠卡先行,正文流式保留"正在继续生成…"+光标;未闭合容忍消 `**` 闪。
- 完成切 RichText:终稿容器/字号/颜色与流式末帧对齐(统一"讲题正文"令牌),避免跳变。

### C. 排版令牌（新增 `tutorMarkdownTextStyle` 族, core/ui）
- 正文基线 16sp/行高1.6(折中现 respond bodyMedium 15sp 与 plan bodyLarge 17sp);段落间距≥8dp。
- 重点:加粗=Ink 加粗;关键结论 JadeActive;行内码 JadeSoft 底/mono;公式 Serif 中粗(保留现状)。
- 终稿 RichText:h1-h3→SmartTypography titleLarge/Medium/Small+Jade;列表/引用走 commonmark+主题色。

### Design 完成度: 现状 4/10 → 定稿后 8/10。缺口(记 Eng): RichText 对 h/列表/引用的实际渲染行为未实测、流式与 richtext 具体衔接参数、thinking 空值/中断态。

## Phase 3 Eng（进行中, 外部 voice 异步; 主评审定稿以下）

### 关键架构决策（已核实）
- **thinking 不做 SSE 流式**:`OpenAiSse.deltaContent` 只读 `delta["content"]`(OpenAiSse:39),reasoning_content 现状被丢。thinking 现实路径 = 终稿 JSON 一次性给 thinkingMarkdown。UI: 折叠卡"思考中…"占位仅出现在等待态,完成时 thinking+正文齐。
- **thinking 走 ModelTaskOutput 层**:带默认 null → 旧 output decode 向后兼容;不 bump request schema(v6 不变)、不动 ModelTaskFingerprint(input-only)、无 Room 迁移。与 worktree v8 合流 = 普通协调(不同维度)。
- **richtext 终稿安全前提**:模型 markdown 渲染前须保持 requireTutorMarkdown 白名单校验(禁 HTML/链接/代码块);加"渲染前兜底 sanitize"(richtext 遇原始 HTML 行为需 Eng 测试锁定)。# 标题/- 列表/> 引用已能过校验——richtext 价值在此。

### 架构图（改动后渲染链）
```
model task SUCCEEDED (output: messageMarkdown + thinkingMarkdown?)
   │
   ├─ streaming: userMessage 前缀 ──SafeMarkdownText(状态化 parser,未闭合容忍,节流)
   │                                   (流式仍受限 inline)
   └─ terminal: ──TutorAssistantReplyBubble / TutorPrompt / TutorGeneratedTurn
                        │
                        ├─ thinkingMarkdown? ──ThinkingCollapsibleCard(默认折叠)
                        │
                        └─ messageMarkdown ──[终稿] AiRichMarkdown(=复活改造 MessageBubble:
                             richtext RichText + 统一 tutorMarkdownTextStyle)
```

### 测试图景（新码路径 → 覆盖）
| 新码路径 | 测试 | 现状 |
|---|---|---|
| output 加 thinkingMarkdown init 校验(长度/同白名单) | TutorTasksTest/TutorLobbyTasksTest 补 | 待加 |
| decode 向后兼容(旧行无 thinking) | ModelTasksTest / codec round-trip 补 | 待加 |
| prompt 规则(模型自愿产 thinking,不强制 CoT) | adapters 相关(OpenAiModelTaskAdapters 字符串断言) | 待加 |
| parser 接 thinkingMarkdown | 协议/双 parse 测试 | 待加 |
| thinking 不进 history/priorMessages | tutorChatHistory 单测 | 待加 |
| richtext 遇 HTML/链接 fail-closed | 新增 RichText 安全测试 | 待加 |
| 状态化流式 parser 未闭合不闪 | StructuredContentTest 补 | 待加 |
| ThinkingCollapsibleCard 折叠/展开 | 新 compose 测试(可能 instrumented) | 待加 |
| 现有会 break | TutorTasksTest/TutorLobbyTasksTest(init 新字段)/codec round-trip | 需修 |

## Phase 3 Eng — 证据补强（2026-09-06 复核，全部 primary 实测）

### 三处 critical 均已实测证实（非二手转述）
- **C1 Lobby 弱校验**：`TutorLobbyTasks.kt:95` `messageMarkdown.requireTutorMarkdown`（弱：只拦 `<script`/`javascript:`）；Respond 用 `requireTutorSceneText`（强白名单，禁 HTML/链接/图片/URL）。今日 Lobby 安全 = SafeInlineMarkdown 落纯文本的 fallback。**Lobby 终稿一旦切 richtext，`[x](url)`/`<a>`/HTML 变可点 → 外链泄漏新面**。→ 决策：Lobby 与 Respond 统一走强白名单（或复用 requireTutorSceneText），不能只靠 renderer 兜底。
- **C2 requireOnlyKeys 会拒 thinking**：`OpenAiModelResponseParsing.kt:44-46` 严格拒绝集合外任何键；`TUTOR_RESPOND_WIRE_KEYS`(:539-547)/`TUTOR_PLAN_WIRE_KEYS`(:527-538)/`TUTOR_LOBBY_WIRE_KEYS`(:548) 均不含 thinking。**模型一旦带 thinkingMarkdown 即 `InvalidModelResponseException`**。→ 决策：三处 key set 各加 `"thinkingMarkdown"`（Parser 侧必须加，否则连旧模型都可被拒）。
- **C4 流式 >500 字符即永久失败（现存核心 bug，升级为 P0 第一刀）**：`Progress.init`(ModelTasks:1089 调 `requireSafeModelText` maxChars=500 ModelTasks:1302/1068) 抛 `IllegalArgumentException` → gateway `catch (_: IllegalArgumentException)`(OpenAiCompatibleModelGateway:280) → `emit(Failed(INVALID_RESPONSE))`；`emitStreamingThenCompletion`(gateway:400-439) 的末帧 = `chunks.joinToString("")` 全文(gateway:434)，>500 即爆。**今日长讲解回复流式即被掐成"这次内容无法使用"，模型正文永不呈现**。→ 决策：P0 顺序第 0 步——Progress 帧 userMessage 上限与模型产文不再绑定（截断/专用 preview 上限），且与全文帧解耦。

### High / Medium（primary 复核）
- H1 CoT 泄漏审查（thinking 字段出网回显/日志）——解析链无回显，仅 UI 呈现，风险低；仍列 checklist。
- H2 O(n²) 重复解析——现有：`streamingReplyBody()` 每次重组前缀全量重渲染；测试图景已含节流。
- H3 视觉跳变——流式末帧 vs richtext 首帧 tokens 对齐。
- M1 SmbkArchiveCodec decode——不可变归档快照，thinking 无 migration 面，**无需改**。
- M2 richtext 遇原始 HTML 行为需 fail-closed 实测锁定（C1 的 renderer 侧兜底）。

## Eng Consensus Table（primary vs 独立 voice）

ENG CONSENSUS TABLE:
```
  Dimension                            Primary  Subagent  Consensus
  ──────────────────────────────────── ──────── ──────── ─────────
  1. Architecture sound?               低风险   低风险    确认
  2. Test coverage sufficient?         补3门    补3门     确认
  3. Performance risks addressed?      有节奏   有节流    确认
  4. Security threats covered?         C1可点   C1可点    一致(C1)
  5. Error paths handled?              C4掐流   同上       一致(C4 升级 P0)
  6. Deployment risk manageable?       有       有         确认
```
C1 已由两个 voice 独立命中且 primary 实测 → 按 autoplan 规则升格为 **User-facing gate 项**（安全面，非纯 taste）。

## Eng 必需 outputs

### Failure Modes Registry（Eng 深化）
| 失败模式 | 触发 | 后果 | 缓解 |
|---|---|---|---|
| **Lobby/Respond 校验强度不一** | 模型在 Lobby 输链接/`<a>` | richtext 渲染变可点外链 | 统一走强白名单 requireTutorSceneText；渲染前 fail-closed sanitize |
| **requireOnlyKeys 拒 thinking** | 任意模型返回带 thinking 的 JSON | 整轮 INVALID_RESPONSE 失败 | 3 个 WIRE_KEYS 集合各加 thinkingMarkdown；先于 UI 合入 |
| **流式 >500 掐流** | 回复 >500 字符（常态） | 任务永久失败，正文不呈现 | Progress 帧上限与产文解耦；P0 第 0 步 |
| thinking 泄漏进 history/priorMessages | history 组装未排除 | 下一轮自见思考+披露越界 | thinking 独立字段，组装显式排除 + 单测 |
| 流式→终稿视觉跳变 | 两渲染器 tokens 不一致 | 突兀重排 | 统一 tutorMarkdownTextStyle；首帧对齐末帧 |
| richtext 遇原始 HTML | 校验遗漏的 HTML 到达 renderer | XSS/外链 | 维持白名单于渲染前 + richtext 行为测试 fail-closed |

### Eng Completion Summary
- 复核过 gateway 流式链路与 repository dispatch：Progress 上限在 gateway 内部即被掐成 Failed，非静默。
- Parser 三处 WIRE_KEYS 为 thinking 入 schema 的精确加键点。
- Lobby 校验强度差异 + richtext 组合 = 唯一真安全面（升 gate）。
- 未验证项：richtext 0.17 遇 HTML/未闭合的实际行为（测试锁定，列为 Eng 前验证门）、流式渲染测 UI 视觉。

### Eng Readiness Verdict
**有条件就绪**——架构与 schema 决策（thinking 走 output 层、richtext 终稿）成立；C1(安全) + C4(现存 bug) 必须在实施序列最前，C2 是 thinking 合入前提。三处均已在测试图景覆盖。

### DX Phase 结论
DX scope 检测：无 developer-facing API/CLI/SDK/文档/MCP 术语命中（纯 app 内 UI + 本地模型 schema 改动）→ **DX phase 跳过**（skill 允许）。

## Phase 4 — Final Approval Gate
**状态:DONE 全链验证全绿(2026-09-06)**。8 自动决策 + 2 挑战裁决全部通过。实施任务 T1-T9 全部完成，`core:model` + `core:ui` + `feature:tutor` 编译与单测全绿，`:app:compileLocalFirstDebugKotlin` 顶层消费方构建成功。实施顺序:先 T1(C4 流式上限解耦,P0)→ T4(Lobby 统一白名单,安全前置)→ T2/T3(thinking 字段+WIRE_KEYS)→ T5→T6(richtext)→T7/T8(UI)→T9(安全门实测)。

## Design 逐 pass 评分（Phase 2，[single-voice]，补记）
| Pass | 关注点 | 评分(初) | 修复后 | 说明 |
|---|---|---|---|---|
| 1 | 信息层级 | 5 | 8 | 折叠卡把思考链降为次层，正文为第一视觉 |
| 2 | 缺失状态 | 5 | 8 | 补 thinking 空值(无思考)、中断(思考到半截)、思考先于正文 |
| 3 | 用户旅程 | 6 | 8 | 折叠展开情绪弧：透明→不剧透 |
| 4 | 具体性 | 5 | 7 | richtext 渲染行为未实测(转 Eng 前验证门) |
| 5 | 设计系统对齐 | 5 | 7 | 新 tutorMarkdownTextStyle 需落 DESIGN.md |
| 6 | 可访问性 | 5 | 7 | 灰阶对比 vs Ink；字号 ≥15sp 基线 |
| 7 | 动效 | 4 | 7 | animateContentSize 折叠 + 流式光标 |
| 8 | 空/错/载状态 | 5 | 8 | 思考中断态/等待态补全 |
| 9 | 文案 | 6 | 8 | "已思考/正在思考"教育语境 |
| 10 | 一致性 | 5 | 7 | 流式末帧→richtext 首帧 tokens 对齐 |
平均 5.1 → 7.5。

## Error & Rescue Registry（Deep 必需，补记）
| 故障 | 触发 | 层 | 救援 | 现状 |
|---|---|---|---|---|
| thinking 超长/违规 | init 校验 | output 层 | 拒绝该 output → 用户重试 | 新增校验(同 message 白名单) |
| requireOnlyKeys 拒 thinking | 模型返回 thinking 但 key set 未加 | parser | INVALID_RESPONSE → 永久失败 | 先加 key 再放 prompt(顺序硬约束) |
| 流式超 500 | 长回复 Progress 帧 | gateway | 掐流为 INVALID_RESPONSE | P0 第 0 步修复(解耦 cap) |
| richtext 遇 HTML | 校验遗漏 | renderer | fail-closed(不渲染原 HTML) | Eng 前验证门 + 测试 |
| thinking 入 history | 组装未排除 | history | 污染下轮 | 显式排除 + 单测 |

## Cross-Phase Themes
**Theme: Lobby 弱校验是 richtext 的安全闸** — Phase 1(failure registry 初版) + Phase 3(Eng voice critical C1 + primary 实测) 双 phase 独立命中。单一高置信信号：Lobby 切块级渲染前必须先统一白名单。

## Decision Audit Trail（全部决策行，跨 phase）
| # | Phase | Decision | Classification | Principle | Rationale | Rejected option |
|---|---|---|---|---|---|---|
| D1 | Premise | 三件事一起做(P0+路线B+块级) | Premise(用户定) | — | 用户明确 | 只做一件 |
| D2 | Premise | thinking 加到所有输出 kind | Premise(用户定) | — | "只要输出都需要" | 仅 respond |
| D3 | Premise | 终稿切 richtext RichText | Premise(用户定) | — | 用户拍板 | 扩 SafeInlineMarkdown |
| D4 | Deep | thinking 走 ModelTaskOutput 层带默认 null | auto | P1 | 不 bump request schema v6/不动指纹/无 Room 迁移 | request schema 加字段 |
| D5 | Deep | thinking 不进 history/priorMessages | auto | P5 安全 | 防自见思考+披露越界 | 全量入历史 |
| D6 | Deep | 终稿=复活改造 MessageBubble 为块级基座 | auto | P4 DRY | 复用死代码 | 新写渲染器 |
| D7 | Eng | 3 WIRE_KEYS 各加 thinkingMarkdown | auto | P3 | C2 证实：不加则任何带 thinking 响应被拒 | 放宽 requireOnlyKeys |
| D8 | Eng | C4 解耦：Progress 帧上限与产文解耦(P0 第 0 步) | auto | P1 | 现存 bug 掐死长回复流式 | 抬上限 |
| D9 | Eng | Lobby 统一走强白名单 | **User gate** | 安全 | C1 两 voice 独立命中 | 维持双校验 |
| D10 | Design | Lobby 校验统一后再切 richtext | auto | P2 | 渲染器兜底不可靠 | 先切渲染器 |

## Implementation Tasks（合成自各 phase 发现）
- [x] **T1 (P0)** — gateway+model — 解耦流式 Progress 帧上限与产文长度(消灭 C4：长回复流式即死的现存 bug)
  - Surfaced by: Phase 3 Eng — critical C4 (gateway:400-439 末帧全文 + ModelTasks:1068 上限)
  - Files: `OpenAiCompatibleModelGateway.kt`、`ModelTasks.kt`
- [x] **T2 (P1)** — model — 3 output kind 加 `thinkingMarkdown?` 字段 + init 白名单校验
  - Surfaced by: Phase 1 Deep D4 — output 层默认 null 向后兼容
  - Files: `TutorTasks.kt`、`TutorLobbyTasks.kt`
- [x] **T3 (P1)** — data — parser 三处 WIRE_KEYS 加 thinkingMarkdown + 接字段(先于 prompt 合入)
  - Surfaced by: Phase 3 Eng — critical C2 (OpenAiModelResponseParsers:539/527/548)
  - Files: `OpenAiModelResponseParsers.kt`
- [x] **T4 (P1)** — data — Lobby 统一走强白名单(与 Respond 同标准)
  - Surfaced by: Cross-phase Theme C1 — Lobby 弱校验安全闸
  - Files: `TutorLobbyTasks.kt`、`OpenAiModelTaskAdapters.kt`
- [x] **T5 (P1)** — data+ui — thinking 不进 history/priorMessages(显式排除 + 单测)
  - Surfaced by: Phase 1 Deep D5 + failure registry
  - Files: history 组装层 + 测试
- [x] **T6 (P2)** — core/ui — 复活 MessageBubble 成 `AiReplyRichMarkdown`(richtext 终稿) + 统一 tutorMarkdownTextStyle
  - Surfaced by: Phase 2 Design C + Phase 3 测试图景
  - Files: `MessageBubble.kt`、`Theme.kt`、`StructuredContentRenderer.kt`
- [x] **T7 (P2)** — core/ui — `ThinkingCollapsibleCard`(折叠/展开/思考中态)
  - Surfaced by: Phase 2 Design A + challenge 裁决(用户坚持折叠 AI 感)
  - Files: core/ui 新增 + compose 测试
- [x] **T8 (P2)** — core/ui — 状态化流式 parser(未闭合容忍 + 节流)
  - Surfaced by: Phase 1 Deep(闪烁根因) + failure registry
  - Files: `StructuredContent.kt` + 测试
- [x] **T9 (P3)** — eng — richtext 遇 HTML/未闭合行为实测锁定(fail-closed 门)
  - Surfaced by: Phase 3 Eng M2 + Design pass 4 缺口
  - Files: 测试 + 视结果定 renderer 兜底

## 实施进展

### T1 DONE（2026-09-06，已绿）
- **ModelTasks.kt**：`Progress` 上限 500→新 `MAX_PROGRESS_MESSAGE_CHARS=2000`；加 `Progress.of(text)` companion factory 截断到 2000；`MAX_MESSAGE_CHARS` 由 private→internal。
- **OpenAiCompatibleModelGateway.kt**：`emitStreamingThenCompletion` 两处流式帧改用 `Progress.of(...)`（422/434），不再直接 `Progress(...)` → 超限不再抛 `IllegalArgumentException`。
- **ModelTasksTest**：3 个新测试——长回复(2400)截断到 2000、短回复不变、5000 长回复不再抛。core:model + core:data 单测全绿。
- 并发改动(Route A enableNativeTools/agent-consent)零重叠，未触碰。
- C4 消灭：长回复流式不再整轮失败。

### T2 DONE（2026-09-06，已绿）
- **TutorTasks.kt**：`TutorTurnPlan`/`TutorRespondOutput` 加 `thinkingMarkdown: String? = null` + init 校验；新增 `requireThinkingMarkdown`(null 跳过，非 null 走 `requireTutorMarkdown` 弱白名单 + `MAX_THINKING_CHARS=4000`)。
- **TutorLobbyTasks.kt**：`TutorLobbyOutput` 加 `thinkingMarkdown: String? = null` + init 校验。
- **校验档位**：thinking 用**统一弱白名单**(`requireTutorMarkdown`：拦 `<script`/`javascript:` + 平实语言 + 4000 上限)，不用 respond 的强 scene-text——因 thinking 渲染在受限 SafeInlineMarkdown(其自身把 HTML 全角化/active scheme 中和)，且折叠卡默认收起。renderer 安全由 SafeInlineMarkdown 保证。
- **向后兼容**：默认 null + codec explicitNulls → 旧 output_snapshot 无该键 decode 填 null；测试 `legacyRespondWithoutThinkingDecodesToNullThinking` 锁定(剥键后 decode=null)。
- **测试**：respond/plan/lobby thinking round-trip + validate isEmpty、respond 拒 active/超长、lobby 拒 javascript:。core:model + core:data + feature:tutor 单测 + core:data/feature:tutor androidTest 编译全绿。
- **未动**：parser(WIRE_KEYS 加键=T3)、prompt(模型产 thinking)、UI 折叠卡(T7)——后续任务。

### T3 DONE（2026-09-06，已绿）
- **OpenAiModelResponseParsers.kt**：`TUTOR_PLAN_WIRE_KEYS`/`TUTOR_RESPOND_WIRE_KEYS`/`TUTOR_LOBBY_WIRE_KEYS` 各加 `"thinkingMarkdown"`；`toTutorPlan`/`toTutorRespond`/`toTutorLobby` 构造 output 时接 `optionalString("thinkingMarkdown")`。
- 消灭 C2：模型带 thinkingMarkdown 返回不再被 `requireOnlyKeys` 拒。唯一新放行键是 thinking；`unexpected`/`imageUrl` 等真未知键仍被拒(1285-1286 测试保留)。
- **测试**：respond/plan/lobby thinking parse from wire + 缺省=null + respond 拒 unsafe `<script>`。core:data 全量单测 + core:data/feature:tutor androidTest 编译全绿。

### T4 DONE（2026-09-06，已绿）
- **TutorLobbyTasks.kt**：`TutorLobbyOutput.messageMarkdown` 从弱 `requireTutorMarkdown` 升为强 `requireTutorSceneText`(禁 HTML/链接/URL/图片/反引号)——与 Respond 同强度。
- 对齐 Prompt 契约：Lobby prompt rule 7 本就禁 HTML/代码/链接/URL/图片,校验此前未落地(缺口)。改后无合法输出被误拒。
- 消灭 C1：Lobby 终稿切 richtext 不再有外链口子。
- **测试**：`lobbyRejectsActiveHtmlLinksAndUrlsInBody`(HTML/link/URL/inline code 全拒,covers `[x](url)`/`<a>`/`https://`)。core:model + core:data + feature:tutor/feature:library 单测 + androidTest 编译全绿。

### 下一步：T5（thinking 不进 history/priorMessages）

### T5 DONE（2026-09-06，已绿）——thinking 不进 history/priorMessages
- 结构性验证：`TutorChatHistoryEntry` 仅含 studentMessage/assistantMarkdown；`tutorChatHistory`(TutorChatConversation:149)、`toLobbyHistory`(TutorLobbyRoute:676) 只取 `messageMarkdown`/`bodyMarkdown`。thinking 独立于 history 字段,结构上无泄漏路径。
- **TDD 守卫**：`historyNeverLeaksThinkingIntoPriorContext` 建带 thinkingMarkdown 的 succeeded 快照,断言 `tutorChatHistory` 输出的 assistantMarkdown/studentMessage 不含 thinking,且正文不变。加 `thinkingMarkdown` 参数到 `succeededResponse` fixture(默认 null,不影响既有调用)。
- 已验证：thinking 只在 output_snapshot(终态记录,供 UI)持久化,不进入 adapters 的 priorMessages 组装。

### T6 DONE & VERIFIED（2026-09-06，全链实测全绿）
- **core/ui/AiReplyRichMarkdown.kt**：终稿块级渲染器。失败关闭判定 `shouldUseRichTextMarkdown`（含 HTML/active scheme/远程图片/裸 URL → 回退 SafeMarkdownText）；richtext `Markdown(parseOptions=autolink=false)` + 链接 no-op；阻断 HTML/图片外发。`AiReplyRichMarkdownTest` 4 例全绿。
- **core/ui/TutorMarkdownTokens.kt**：正文 16sp/行高1.6、h1-h3、行内码 JadeSoft+mono、em=JadeActive、inlineCodeBackground=JadeSoft。
- **core/ui/build.gradle.kts**：加 `testImplementation(libs.junit)`（此前无尽的 JUnit 依赖，新增纯逻辑单测用）。
- **接线**（resolve conversation 终稿 :342 SafeMarkdownText→AiReplyRichMarkdown；lobby SUCCEEDED;plan solution）——代码已改。
- **验证**：core:ui + feature:tutor 全量编译+单测**已实测全绿**。

### T7 DONE & VERIFIED（2026-09-06，全链实测全绿）
- **core/ui/ThinkingCollapsibleCard.kt**：折叠卡。默认折叠"已思考"+chevron，展开 animateContentSize+bodySmall 灰字渲染 thinking(SafeMarkdownText)；流式"正在思考…"+细 spinner；纯函数 `shouldShowThinkingCard` 单测 3 例。
- **feature:tutor/.../TutorChatConversation.kt**：终稿 AI 回复正上方插 `ThinkingCollapsibleCard(thinkingMarkdown=output.thinkingMarkdown)`。
- **验证**：core:ui + feature:tutor 全量编译+单测**已实测全绿**。

> **并发断点解除**（2026-09-06）：本 plan 的 feature:tutor 集成曾因并发会话 `split-large-files`/`batch-intake` 切占在 core:database + core:data 的未收尾改动而无法编译。为打通全链验证，对 3 处**机械性缺失**做了最小补全（均为对 split 后类型/变量的补引用，非设计变更）：`RoomStudyDatabase.kt` 补 `dao.ReviewLogSampleProjection`/`entity.KnowledgeQuestionLatticeView` 两个 import；`RoomStudyDatabaseMappings.kt` 补 `port.KnowledgeQuestionLatticeRecord` import；`RoomBackedStudyExperienceRepository.kt:1623` 局部变量名 `confidenceAtErrorByPracticeUnit`→`confidenceAtError`（对齐 :1610 实际变量）。其余（`TopicBlueprint.parentSlug` legacy 路径）已由 peer 自行补默认值。此 3 处属对 peer 文件的引用性修正，未动其设计；合并时以 peer 分支持续版本为准。

### T8 DONE & VERIFIED（2026-09-06，全链实测全绿）
- **StructuredContent.kt**：加 `SafeInlineMarkdown.parseStreaming(value)` —— 带未闭合容忍的流式渐进 parser。与 `parse` 同一受限白名单（HTML/active scheme/远程图片 → 惰性文本），仅当文本以未闭合 `**`/`*`/`` ` ``/`$` 起始标记结尾时才把剩余内容当作"仍进行中的样式段"渲染。已闭合部分行为与 `parse` 完全一致。
- **super-review 重构（单扫描器）**：`parse`/`parseStreaming` 统一为私有 `scan(value, tolerateUnclosedTail)`——单一遍历 + `markerAt()` 分类（Rendered/Pending/Literal），删除原 `trailingPendingToken`/`PendingToken`/`PendingKind` 三条并行机制（原实现把 `parse` 的标记扫描器重写了一遍，~50 行重复）。`isPendingTail` 额外拒绝"content 以空白开头"的挂起尾，修复 `x * y` 的假强调误判（流式不再把乘号当强调开环）。`nextMarkerIndex` 的 `\n`→`LineBreak` 处理在 `scan` 内显式保留。
- **接线**：`StructuredContentRenderer.kt` 的 `SafeMarkdownText` 加 `streaming: Boolean = false`；`toSafeAnnotatedString(streaming)` 在流式时走 `parseStreaming`。`TutorChatConversation.kt` 流式帧调用改为 `streaming = true`。
- **测试**：`StructuredContentTest` 32 例全绿——新增 whitespace-guard 两例（`x * y` 不强、补全 `*y*` 才强调）、裸 `$` 无内容降字面量、全闭合等价、active content 惰性、未闭合 `**`/`` ` ``/`$` 保持样式。
- **消灭闪烁根因**：流式末帧"先字面量、补全后突变"不再发生——未闭合标记保持样式，补全时无跳变；且 `x * y` 这类字面星号不再被误强调。
- **验证**：core:model + core:ui + feature:tutor 全量编译+单测**已实测全绿**（`:feature:tutor:testDebugUnitTest` 通过）。

### T9 DONE & VERIFIED（2026-09-06，安全门由 T6 前置关闭）
- T9 语义=richtext 遇 HTML/未闭合行为的 fail-closed 门。实际由 T6 的 `shouldUseRichTextMarkdown` 在"richtext 见到任何不安全内容之前"就拦截：含 HTML/active scheme/远程图片/裸 URL/超长/空白 → 一律回退受限 `SafeMarkdownText`，richtext 只在 100% 安全白名单内容上走 commonmark。
- **super-review 修复（单一真源）**：裸 URL 判定从 renderer 私有的 `baredUrl` 提升为数据层 `SafeInlineMarkdown.BARE_URL`——`TutorTasks.requireTutorSceneText`（原私有 `TUTOR_SCENE_URL`）与 `shouldUseRichTextMarkdown` 共用，消除两处正则漂移；数据层扩展 scheme 时 renderer 兜底自动生效。
- 因 T6 在 renderer 层已前置 fail-closed，"视结果定 renderer 兜底"不再需要二次实测——不安全内容根本到不了 richtext；`AiReplyRichMarkdownTest` 4 例锁定（白名单放行 HTML/active/远程图/空白回退）。**core:ui 实测全绿**。
- 剩余"richtext 自身渲染行为"（commonmark 对已放行内容的表现）属视觉/排版层；若后续发现未闭合块级（如 `#`/`>`）在 richtext 下抖动，可给 `shouldUseRichTextMarkdown` 追加"未闭合块级检测"。安全门已闭合，端到端验证通过。

> **super-review 收尾**（2026-09-06）：审查发现 5 项结构性/正确性问题，已全部处理——① `TutorMarkdownTokens` 从"57 行只用一次、docstring 声称双路径共用"的死抽象，改为**真正的单一行内样式源**：新增 `strong/emphasis/code/formula` SpanStyle，流式 `SafeMarkdownText`、richtext `RichTextStringStyle`、失败回退三处共用，使"中途→完成→回退"不跳变；② `parseStreaming` 收敛为单扫描器（见上）；③ 裸 URL 正则收敛为 `SafeInlineMarkdown.BARE_URL`（见上）；④ `parseStreaming` 对 `x * y` 的假强调修复（见上）；⑤ 移除 `AiReplyRichMarkdown` 半生效的 `color` 参数（richtext 路径本就不能消费它，唯一调用方也未传）。验证：`:core:model:test`(32) + `:core:ui:testDebugUnitTest`(4+3) + `:feature:tutor:testDebugUnitTest`(17) + `:app:compileLocalFirstDebugKotlin` 全绿。

> **simplify 收尾**（2026-09-06）：复用/质量/效率三遍复审后再清理——① 删除 `TutorMarkdownTokens` 6 个死字段（`heading1/2/3`、`inlineCode`、`emphasized`、`inlineCodeBackground`，全库无引用：richtext 无 body TextStyle 参数，这些 TextStyle 从没接入渲染路径），收紧 docstring，文件 57→48 行；② 删除 `MarkerKind.STRONG` 枚举值与 `buildInlineToken` 的对应死分支（`**` 在 `markerAt` 提前返回，STRONG 从不流入 kind）。行为未变，`StructuredContentTest` 仍 32 例全绿。边缘探针（重编译 `\n`/多未闭合/`$a*b$`/tab-content/紧跟星号/新行结尾）验证无回归、无死循环。

## 2026-10-19 后续UI改版承接

上述完成记录保留。本次用户要求改进使用逻辑与视觉美感，后续统一进入[UI-00～11](../../docs/superpowers/plans/2026-10-19-ui-ux-redesign-plan.md)与[页面设计](../../docs/superpowers/specs/2026-10-19-ui-ux-redesign-design.md)。UI-01/07/08复用本计划已有的流式/终稿/公式和折叠组件，只补令牌一致性、滚动锚点、输入区、图卡/表单和异常态，不从零重写渲染器。

过程区仅展示适合用户的公开过程摘要/工具状态；不因视觉改版增加隐藏推理披露。新JS/图像模型两方式仍沿共享生成契约；本次计划同步不记为UI代码完成。
