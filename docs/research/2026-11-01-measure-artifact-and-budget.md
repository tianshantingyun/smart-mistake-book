# R2 度量研究：完整布局 artifact（M-01）、排版预算（M-03）、数学 lowering（S-08）设计

日期：2026-11-01。任务来源：批次 38 计划（`.zcode/plans/plan-2026-11-01-batch-38-triple.md` §1 C.9–C.11），
驱动 M-01/M-03/S-08 三项实施轨。任务定义原文：清单 `docs/research/2026-10-26-batch-24-4e2-admission-checklist.md`
`:148/:155/:157`；现状基线：`docs/research/2026-11-01-batch-38-baseline.md` §行 60–62。

证据分级声明：标 **[引证]** 的内容来自本会话实际读码（附路径:行号）或本轮实际抓取的官方文档/GitHub 元数据
（附 URL 与命令）；标 **[待验证]** 的为单一来源或未取得逐字引文项，不当定论。本批纪律禁跑 Gradle，
全部仓库侧核验为静态读码；§5/§6 的测试判据是给实施轨的红绿标准，本批**未运行**。

---

## 0. 结论速览（推荐组合）

| 任务 | 缺口定性 | 推荐落点 | 关键裁定 |
|---|---|---|---|
| M-01 | **部分落**：端口五字段无 `inkBounds`、无类型化布局；internal 完整 artifact 已在树 | 分两步：① 类型化布局 DTO 落 core/data `visual` 包或 app visualjs（均非热区）；② 端口字段等 core/domain 热区解锁再提 | `inkBounds` = 墨迹边界（`Paint.getTextBounds` 口径），不进 digest；与布局盒**无包含不变式** |
| M-03 | **未落**：4MP/16MiB 常量 0 命中；12..48sp/fontScale 门已在树可复用 | 预算器落 core/data `visual` 包（core/domain `VisualExecutionBudget` 是热区）+ bitmap 分配点前置门 | 累计像素口径取 **du（dp）**；16 MiB 按实际 px×bytesPerPixel；两门独立判（4MP×4B=16MiB 数字自洽是巧合级对齐，不是同一条门） |
| S-08 | **未落**：`MathContentLowering` 全仓 0 命中；但复用对象与降级雏形全部在树 | `MathContentLowering` 落 core/model（非热区），收敛 `parseAndBuildBox`+`ReadableMathText` 既有链 | 不合并三处现有消费方（要动 core/ui、core/export 两个热区）；"不得第三套"的语义 = 不得新增第四处 parser/坐标绘制 |
| GitHub 参考 | — | 文本测量：androidx（Apache-2.0，唯一可引实现）；数学：**无任何可复用库**（jlatexmath 全家族 GPL，license 一票否决） | 自研 MathBudget/MathBox 链维持，外部仅作设计参考 |

---

## 1. 任务定义原文与现状

| 清单行 | 原文 | 现状（基线 `:60–62`，本会话读码复核一致） | 验收 |
|---|---|---|---|
| M-01 `:155` | 度量端口返回完整布局 artifact：宽/高/baseline/`inkBounds`/实际字号/摘要 | 端口有宽/高/baseline/字号/digest，无 `inkBounds`（`VisualExecutionEngine.kt:210` KDoc 自认"随批次二类型化布局落地"）；internal 完整 artifact `VisualMeasuredLayout` 已在树（`VisualContentMeasuring.kt:250`） | 端口/度量批次测试（新增 JVM） |
| M-03 `:157` | 真实字形度量/字体白名单/后台布局/12..48 sp/fontScale/累计 4 MP/单 bitmap 16 MiB | 12..48sp 门已落（`VisualContentMeasuring.kt:178-179`）；4MP/16MiB 常量 0 命中（基线 grep `4_194_304\|16_777_216\|MAX_TOTAL_PIXEL\|MAX_BITMAP` = 0，本会话未复跑该 grep，以基线为据） | `NativeContentLayoutInstrumentedTest`（新增，设备） |
| S-08 `:148` | 数学真实 lowering：复用 `MathBudget` parser/AST，未知 TeX 回可读文本，不增第三套公式/坐标逻辑 | `MathContentLowering` 零命中；降级雏形已在树（`measureMathBox` + `degraded` 字段） | `MathContentLoweringTest`（新增 JVM/设备） |

---

## 2. 仓库一手事实（本会话读码，全部 [引证]）

### 2.1 端口面（core/domain —— 热区，只读）

- `VisualContentMeasurer.measure(batch): VisualContentMeasureResult`：`core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/VisualExecutionEngine.kt:162-164`；KDoc 自述其消灭的失败 = "prepare 一次/度量一次/render 只引用本事务 contentId" 的纪律不可观测 + "实现可能无限追加测量"（`:155-161`）。
- 端口五字段 `VisualMeasuredContent(contentId, widthDu, heightDu, baselineDu, fontSizeSp, digest)`，全部 require 有限/非负：`VisualExecutionEngine.kt:212-228`；`:210` KDoc："inkBounds 的完整矩形随批次二类型化布局落地，本端口先携带标量度量"。
- 批预算在类型上强制：128 条 / 6000 字符（`:175-180`）；画布边长 4096 du（`:138`）；事务 JSON 256 KiB（`:114`）。
- 每事务恰一次 measure batch 由引擎保证（`:156`）；度量结果注入 JS render 时深冻结（`QuickJsVisualExecutionEngine.kt:411-423`，注释引契约 §3.2:58）。

### 2.2 度量实现（app/visualjs —— 非热区）

- internal 完整布局 artifact `VisualMeasuredLayout(kind, widthDu, heightDu, baselineDu, lines, degraded)`：`app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualContentMeasuring.kt:250-262`。KDoc："端口五字段之上的行结构……本批不进端口、不序列化"；sealed→data class 收敛理由已登记（草图两子类型成员集相同、降级 math 携带文本段打破边界）。
- TEXT 内蕴度量（`:270-302`）：显式 `\n` 分段（`explicitLineRanges` `:396-411`，段尾单个 `\r` 归一，尾 `\n` 产生空段参与行数）；宽 = 各段 advance 最大值（`Paint.measureText` 口径，经 `VisualTextMeasureBackend.advancePx` seam）；高 = 段数 × 行高（行 metrics 对整段载荷取一次）；baseline = 首行自顶偏移（`-ascent`）。**无自动换行**（R2 D3：换行需 entry 协议加宽度字段，属契约变更）。
- MATH 盒度量（`:314-339`）：`parseAndBuildBox(payload)` → `MathBoxBuilder.buildBox(node, MathMetrics.of(environment.fontSizePx()))` → 盒宽/高/baseline ÷ density。null = 解析/预算失败。
- 契约降级（`:347-362` `measureContentEntry`）：MATH 失败 → 用 text 路径度量**原文 payload**，`degraded=true`、kind 保留 MATH（摘要按 kind 区分）——即"未知 TeX 回可读文本"的既有雏形，不做占位框。
- 内容摘要（`:370-385`）：`sha256(visual-content-measure-v1|kind|degraded|环境指纹|payload)`；环境指纹 = `typefaceId|sizeSp|density|fontScale|locale|paintFlags`（`:172-174`）。
- 环境门（`:146-161`）：`sizeSp ∈ MIN_SIZE_SP(12f)..MAX_SIZE_SP(48f)`（`:177-179`）、`fontScale > 0`、`density > 0`，违反即 fail-closed；`CANONICAL_SIZE_SP = 16f`（`:202`）。单位换算单一规则（`:112-118`）：`px = sp × density × fontScale`；`du = px / density`；故 `du per sp = fontScale`；`fontSizePx()` `:164`。
- 度量 Paint 显式 `flags = 0` 并与指纹一致校验（`:463-481`）；平台 `Paint()` 默认 1283 的实测结论已被官方文档佐证（§6.3）。
- warm-up 探针（`:90-100`）把字体子系统首初始化移出 1500 ms 硬期限；环境异常族 `VisualMeasurementEnvironmentException`（`:110`）fail-closed。

### 2.3 消费面（core/data visual 包）

- 引擎注入 JS 的 metrics JSON 恰为端口五字段（`QuickJsVisualExecutionEngine.kt:641-652`）——JS 放置文本只需宽/高/baseline；行结构不进 wire。
- DrawOps 词表 `Label(contentId, x, y, rotationDeg, hAlign, vAlign, color, alpha)`：`core/data/.../visual/VisualDrawOps.kt:119-130`，注释引契约 §5"compiler 一次算出最终位置，不由 Compose/PDF 各自猜 baseline"。
- 回放侧 DTO `VisualLabelContent.Text(payload, sizeSp, fontScale, heightDu, lines)` / `.Math(box: MathBox, sizeSp, fontScale)`：`VisualDrawOpsPainterPure.kt:194-228`。KDoc 自定位："`VisualMeasuredLayout.lines` seam 的回放侧形态——painter 复用同一 seam、同一数字……app 侧未来装配器把度量产物折成本 DTO；本批由测试台/冒烟直接构造"。**该接口是 internal**——跨模块（app→core/data）不可直接构造，这是 M-01 落点裁定的关键约束（§3.4）。
- painter `playLabel`：math 回放 fail-closed（`PAINTER_LABEL_MATH`，`VisualDrawOpsPainter.kt:193-198`）；文本按 `heightDu / lines.size` 反解行高逐段 `drawText`（`:212-218`）——该反解不变量（heightDu = 段数 × 行高）由度量侧 `:297` 保证。
- 回放 Paint 显式 `flags = REPLAY_TEXT_PAINT_FLAGS(0)`（`VisualDrawOpsPainter.kt:54`）；度量/回放 flags 同值为 KD-78 ④ 的跨侧钉子。
- `drawRendered`（`VisualDrawOpsPainterRendered.kt:26-38`）internal，无生产装配点（painter 生产装配留批次 31 未达；preview canonical 资产 = 本批 D-04 轨）。

### 2.4 数学链（core/model —— 非热区）

- `MathBudget`（`core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/MathBudget.kt:11-117`）：输入 2000 字符、token 500、AST 节点 1000、深度 20、矩阵 50×20、分数嵌套 10、cases 20、aligned 50、盒宽 512 字符宽、盒高 256 行高；`checkBudget`/`checkLayoutSize` 违约返回错误字符串、"never throw"（`:7-9`）。
- `parseAndBuildBox(input): MathParseResult`（`:634-697`）：Tokenizer→Parser→AST→Budget→Box 单链集成，违约返回 error 结果（调用方降级）；`parseAndBuildBoxOrNull`（`:703-710`）。
- `MathMetrics.of(fontSize)`（`:232-241`）：charWidth=0.5×size、charHeight=size、lineSpacing=0.25×size；非有限/非正回退 16px。
- `MathBoxBuilder.buildBox`（`:351-620`）：树递归产出 `MathBox`（width/height/baseline 自顶口径，`MathBox` 定义 `:262-340`）；字符宽估算（0.5em/字符）与上下标简化是**已知精度边界**（P2），契约明令"必须补为真实字形度量"且**修正必须落在共享 lowering**、不能只在度量侧做（`VisualContentMeasuring.kt:46-48` 注释 + R2 前序文档 §1.5-6）。
- 可读文本降级 `ReadableMathText.formula`（`core/model/.../ReadableMathText.kt:13-20`）：`MathRenderer.render(MathParser.parse(value))`，catch 后**原文透传**；KDoc："There is exactly ONE math parser in the codebase"。
- 既有测试回归位：`MathBudgetEnforcementTest`/`MathBoxGoldenTest`/`MathPipelinePropertyFuzzTest`（core/model test，ls 实证）。
- UI/导出侧现有消费方：`core/ui/MathBoxComposeRenderer.kt`、`core/export/CanvasMathBoxRenderer.kt`（两模块均热区，本会话未读内容，仅确认存在）。

### 2.5 契约条款（docs/current —— 只读）

- §3.2 第 2 步（`visual-generation-contract.md:52`）："原生验证并度量 text/math，保留不可变布局 artifact，返回宽、高、baseline、inkBounds、实际字号与内容摘要。"
- §4（`:90`）："sizeSp 限 12..48，系统 fontScale 另计；单事务所有文字 artifact 与预览的累计像素≤4 MP，单 bitmap≤16 MiB，宿主只保留当前帧和一个候选帧，释放旧 artifact。原生解析、几何派生、字体布局在后台受输入复杂度、单在飞任务与软期限约束……超限不截断成'正确图'，返回有界诊断。"
- §5（`:99`）："label 引用已度量 contentId……compiler 一次算出最终位置，不由 Compose/PDF 各自猜 baseline。第一版无自动标签避让优化器，JS 根据真实 metrics 布局；原生检查越界并给诊断。"
- §5 持久化（`:129`）："内容文本/数学 lowering、冻结的行范围与 run 位置、baseline、字体身份/版本及摘要随显示操作保存……冷恢复在字体/renderer 精确匹配时从已冻结记录重建原生 artifact……需要新环境重新排版时明确生成新快照。"
- §5 公式段（`:131`）："现有 `MathBudget.kt` 的 parser/AST/budget 可复用；MathBox 的字符宽估算与上下标简化必须补为真实字形度量、明确 x/y 的共享 lowering。第一版支持范围须由公式金样限定，未知 TeX 回可读文本，不宣称完整 LaTeX。不得继续增加第三套公式/坐标绘制逻辑。"

### 2.6 bitmap 字节口径的仓内先例 [引证]

`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/capture/AndroidCanonicalAssetVault.kt:103-104,493`：
"3072×4096 的照片按整幅 ARGB_8888 解码是 50.3MB/份"——3072×4096×4 B = 50,331,648 B = 48 MiB ≈ 50.3 MB，
**×4 每像素字节（ARGB_8888）的会计口径是仓内既有约定**（KD-32 采样预算即按此）。官方逐字引文未取得，见 §9 未验证项。

---

## 3. M-01 设计：完整布局 artifact 的字段与语义、消费面

### 3.1 字段语义表（目标形态）

| 字段 | 单位 | 语义 | 度量口径 | 来源现状 |
|---|---|---|---|---|
| `widthDu` | du | 内蕴排版宽 | text = max 段 advance（`Paint.measureText`）；math = 盒宽（`MathBox.width`） | 已落（端口+artifact 双处） |
| `heightDu` | du | 高 | text = 段数 × 行高（文本感知 metrics）；math = 盒高 | 已落（双处） |
| `baselineDu` | du | baseline 自盒顶偏移 | text = `-ascent`（首行）；math = `MathBox.baseline` | 已落（双处） |
| `fontSizeSp` | sp | 实际使用的规范字号 | 环境单一规范字号 16（per-entry size 属契约扩展 R2 D3，未做） | 已落 |
| `digest` | hex | 内容摘要 | sha256(kind\|degraded\|环境指纹\|payload) | 已落 |
| `kind` | enum | TEXT/MATH | 条目 kind；降级 math 保留 MATH | artifact 已有、端口未有 |
| `lines` | 段列表 | 显式段 `[start, endExclusive)` + 段宽 | `\n` 切分 + `\r` 归一；不变式 heightDu = 段数 × 行高 | artifact 已有、端口未有 |
| `degraded` | bool | math 解析/预算失败降级为可读文本 | true 时 lines = 降级文本段列表 | artifact 已有、端口未有 |
| `inkBounds` | du 矩形 | 墨迹边界（新增） | §3.2 坐标约定 | **两端均未有，纯新增** |

### 3.2 `inkBounds` 坐标约定（设计裁定，实施轨照此落）

1. **原点** = 布局盒左上角；y 向下为正；`baselineDu` 独立给出基线位置。这与 `baselineDu` 的既有口径（"自盒顶"，`VisualContentMeasuring.kt:256`）同系。
2. **text 逐段求法**：`Paint.getTextBounds(segment)` 官方口径为"smallest rectangle that encloses all of the characters, **with an implied origin at (0,0)**"，且"styles are ignored"（§6.1 引文）——即返回矩形相对**绘制原点（左缘 + 基线）**，top 为负（基线之上）。折算到盒顶原点：`top_i = baselineDu + bounds.top + i × lineHeightDu`、`bottom_i = baselineDu + bounds.bottom + i × lineHeightDu`、`left_i = bounds.left`、`right_i = bounds.right`（LTR 假定与 `measureText` 的 advance 口径一致，painter 现行 `isRtl=false` 同款）。整段 `inkBounds` = 各段矩形联合。
3. **math**：`MathBox` 是布局盒树（自带 width/height/baseline），第一版 `inkBounds` = 盒自身（左上原点全盒）；墨迹级细化（分数线上墨、根号帽等）随"真实字形度量"共享 lowering 升级同批演进，不单独立项。
4. **无包含不变式（必须写进类型 KDoc）**：斜体悬挂、超出行 metrics 的字形（fallback 大字形在 API<33 被低估，§6.1）可使 ink 越出 `[0,widthDu]×[0,heightDu]`；消费面只读不裁，越界拒绝仍按布局盒口径判（契约 §5 `:99`"原生检查越界"现行实现基础）。若实施中发现某消费面必须假设包含，属新契约变更，回主会话。
5. **inkBounds 不进 digest**：digest 已锚定 (kind, payload, 环境指纹, degraded)；inkBounds 是同一环境、同一 Paint 配置下的**派生量**（同一 pass 产出，`VisualContentMeasuring.kt:284-293` 同批 advance/metrics）。把它塞进摘要只会让"同内容同环境"缓存失配。但它**必须随持久化 artifact 携带环境指纹**（契约 §5 `:129`"字体身份/版本及摘要随显示操作保存"），冷恢复时指纹不匹配 → 弃用重排（"需要新环境重新排版时明确生成新快照"）。

### 3.3 消费面映射（每个字段谁在吃）

| 消费方 | 消费的字段 | 说明 |
|---|---|---|
| JS 编译器（render 的 metrics 注入） | widthDu/heightDu/baselineDu/fontSizeSp/digest/contentId | 现行 `metricsJson` 五字段（`QuickJsVisualExecutionEngine.kt:641-652`）。**加字段 = wire 面变更**：metrics 是宿主→JS 深冻注入，加字段不破"JS 只可回显 contentId"门，但 `tools/visual_compiler` JS 测试台的 metrics 词表需同步（同值镜像纪律，同 `MAX_SCENE_NODES` 先例 `VisualExecutionEngine.kt:141-145`） |
| 原生越界检查 | widthDu/heightDu（现行） | inkBounds 到位后可升级为墨迹判据——但第一版不动，判据升级属契约变更 |
| painter / PDF 回放 | lines/heightDu（行高反解）、kind、degraded | `VisualLabelContent` 现行反解 `heightDu/lines.size`（`VisualDrawOpsPainter.kt:212-218`）；类型化布局落地后可直携 lineHeight（消除反解依赖），**但改动必须两端同改并保金样** |
| math 回放解锁 | MathBox（S-08 产出） | 现行 fail-closed `PAINTER_LABEL_MATH`；解锁路径见 §5.3 |
| 持久化（RenderScene DTO） | 全部 | 契约 §5 `:129`：行范围/run 位置/baseline/字体身份+摘要随显示操作保存；`VisualMeasuredLayout` KDoc 已预留"随 painter/PDF 回放批次经同一 seam 重导出"（`:243-244`） |
| 缓存命中判定 | digest + 环境指纹 | "缓存只在内容摘要和布局环境完全一致时命中"；官方同型先例：Compose `TextMeasurer` 对**影响布局的输入**做 LRU 缓存、仅绘制相关参数变化时复用（§6.2） |

### 3.4 推进路径（受热区硬约束的两步走）

**约束事实**：端口类型在 core/domain（禁改中）；`VisualLabelContent` 是 core/data 的 internal 接口（app 直接构造不可见）；core/model 不能依赖 core/domain（依赖方向 core/domain→core/model，反向成环）。

- **第 1 步（本批可落，零热区）**：类型化布局 DTO（§3.1 全字段形态）落 **core/data `visual` 包设为 public**（与 painter 同包、`VisualLabelContent` 的 natural home；`VisualDrawOpsPainterPure.kt:191` KDoc 本就预留"app 侧未来装配器把度量产物折成本 DTO"），或维持 app visualjs internal 现状仅补 inkBounds 计算。app 度量器产出它、`measureTextIntrinsic`/`measureMathBox` 补逐段 `getTextBounds`；端口五字段照旧映射。
  - 新增指认的失败：① painter 回放对行高只能反解（heightDu=段数×行高的隐式契约一旦被未来多行距打破即静默错位）——类型化布局直携行结构消除断言；② "原生检查越界"与未来交互命中区对墨迹不可见——inkBounds 消灭。
  - 注意：若选择 core/data public DTO，app 需依赖 core:data（已成立，`VisualJsService` 构造 `QuickJsVisualExecutionEngine`）；core/data `visual` 包现树无他线 M（基线 §二：core/data M 面 = dense/knowledge/RoomMistakeDetailRepository/RoomTutorToolRunner/SafeTruncation），**实施轨开工前须复核目标文件是否被卷脏**（目标文件变脏则整文件纳入并注明或停项登记）。
- **第 2 步（core/domain 解锁后）**：端口 `VisualMeasuredContent` 增补 `inkBounds`（nullable，旧构造点/契约 fake 不必承担）；`lines/degraded/kind` 是否提进端口由届时"类型化布局"批次裁（端口 KDoc `:210` 已承诺的正是 inkBounds 一项）。
- **明确不做**：不改 `metricsJson` wire 形状（本批无 JS 侧消费需求，改了就同时欠 JS 测试台同步债）；不把三处公式绘制合并（见 §5.4）；不做 run 级位置（`getTextRunAdvances`/shaping run 边界属 PDF 重放批）。

---

## 4. M-03 设计：排版预算与负向

### 4.1 预算清单与现状

| 契约 §4 要求 | 现状 | 缺口 |
|---|---|---|
| sizeSp 限 12..48 | **已落**：`MIN_SIZE_SP=12f`/`MAX_SIZE_SP=48f`，`VisualMeasureEnvironment.init` require fail-closed（`VisualContentMeasuring.kt:157-159,177-179`） | 无（回归保护） |
| 系统 fontScale 另计 | **已落**：`fontScale>0` 门（`:152-154`）；`fontSizePx() = sizeSp × density × fontScale`（`:164`）；`du per sp = fontScale`（`:112-118`） | 无。注意联动：fontScale 放大 → MathMetrics.of(px) 线性放大盒尺寸 → `MathBudget.checkLayoutSize` 字符宽口径随 metrics 缩放（`MathBudget.kt:107-114`）——数学半边的预算**天然随 fontScale 生效**，无需新门 |
| 累计像素 ≤ 4 MP | **未落**（常量 0 命中，基线 §行 61） | 纯新增，见 4.2 |
| 单 bitmap ≤ 16 MiB | **未落** | 纯新增，见 4.2 |
| 只保留当前帧 + 一个候选帧，释放旧 artifact | **未落** | 属宿主帧缓存装配（painter 生产装配/D-04 之后才有"帧"），登记约束不本批实现 |
| 真实字形度量 / 字体白名单 | 占位 `SYSTEM_DEFAULT_TYPEFACE_ID`（`:195`，KDoc 自认后续） | 与 S-08 的"共享 lowering 真实字形度量"同一后续工作（P2 边界），不本批 |
| 后台布局 | **已落**：度量在 `visual-js-executor` 单线程（`VisualJsService` 装配，R2 前序文档 §1.1 读码） | 无 |

### 4.2 两条新门的裁定

**累计像素 ≤ 4 MP（4,194,304 px）**

- **口径裁定：按 du（dp）计**，不按设备 px。理由：4 MP 是**内容预算**（"文字 artifact 与预览的累计像素"），同一内容在 1x/3x density 设备上不应被差别拒绝；契约 `:100` 明文"根变换只做一次 du→px"，布局期尚未做根变换。每条布局盒像素 = `ceil(widthDu) × ceil(heightDu)`；预览按其 du 尺寸计入。字节门（16 MiB）才用真实 px×bytesPerPixel——两口径各司其职并在常量 KDoc 写死。
- **单 bitmap ≤ 16 MiB（16,777,216 B）**：分配前检查 `width × height × bytesPerPixel ≤ 16_777_216`（ARGB_8888 = 4 B/px，§2.6 口径）。**在 `Bitmap.createBitmap` 之前拒绝**——消灭的失败：超尺寸位图在分配点抛 OOM/`OutOfResourcesError` 把"程序超限"变成进程死亡（fail-closed 应是有界拒绝，契约 `:90`"超限不截断成'正确图'，返回有界诊断"）。
- **数字自洽注记（登记，不作为合并依据）**：16 MiB ÷ 4 B = 4,194,304 px = 恰好 4 MP。即 ARGB_8888 下"单 bitmap 字节上限"与"累计像素上限"在数值上重合。**两门仍独立判**（RGB_565 等配置下单 bitmap 像素数可与累计不同；且语义一个管内容总量、一个管内存分配），这句写进常量 KDoc 防后人"发现等价"合并掉一条门。
- **参照数**：画布边长上限 4096 du（`VisualExecutionEngine.kt:138`）→ 满幅 4096² = 16,777,216 px = 16 MP = 4× 累计预算；2048² 恰 = 4,194,304 = 累计上限。即**满幅画布预览必拒**，预览必须缩采样——这正是负向测试要钉的形态（预览尺寸裁定归 D-04 轨，本设计给出硬上界）。

**落点（热区约束下）**

- 预算器（纯函数：`fun totalLayoutPixelsDu(entries): Long` + `requireWithinBudget`）落 **core/data `visual` 包**（与 §3.4 第 1 步同址；`VisualExecutionBudget` 在 core/domain 是热区，解锁后搬并归位）。度量结算处（measurer 汇总后）与预览分配点（D-04 轨的 createBitmap 前置门）都调它。
- JS 侧**不镜像**：场景几何预算（节点/路径命令）才需要 JS 镜像（`visual-compiler.js` 同值镜像先例）；像素/字节预算是宿主度量与分配事实，JS 不分配 bitmap、不算像素。
- 负向拒绝形态：新增有界拒绝码（建议 `LAYOUT_PIXEL_BUDGET` / `BITMAP_BYTE_BUDGET`，与 `PAINTER_STROKE_WITHOUT_FILL` 等生成门码族同风格），**不截断、不静默缩放**。

### 4.3 负向矩阵（实施轨红绿判据；本批未运行）

| # | 触发 | 预期 | 测试位 |
|---|---|---|---|
| N1 | env sizeSp = 11.9 / 48.1 / NaN | init require 失败（环境异常族） | JVM（既有环境测试扩展） |
| N2 | fontScale = 0 / 负 / NaN | 同上 | JVM |
| N3 | 批 129 条 / 6001 字符 | 端口 require（回归不放宽） | JVM 回归 |
| N4 | 多条宽文本布局盒 du 面积合计 > 4,194,304 | 有界拒绝 `LAYOUT_PIXEL_BUDGET`，digest/降级路径不受影响 | JVM 纯函数预算器 |
| N5 | 预览 4096×4096 du（16 MP） | 拒绝（满幅必拒参照数） | 设备（`NativeContentLayoutInstrumentedTest`） |
| N6 | createBitmap(2050, 2050, ARGB_8888) = 16,810,000 B > 16 MiB | 分配前拒绝 `BITMAP_BYTE_BUDGET`，无 OOM | 设备 |
| N7 | 大预览 + 多条文本 artifact 合计越界（4MP 的"文字与预览合计"语义） | 拒绝 | 设备 |
| N8 | math 输入 > 2000 字符 | `parseAndBuildBox` error → 降级 `degraded=true`（预算族既有语义回归） | JVM（既有 enforcement 测试） |
| N9 | 释放旧 artifact：第 3 帧到达时仅存当前帧+候选帧 | 宿主保留策略（约束登记，实现随 painter 装配批） | 设备（届时） |

---

## 5. S-08 设计：数学 lowering 的接缝

### 5.1 接缝现状：链已在树，缺的是"命名化的共享入口"

完整链已存在且纯 Kotlin（core/model，JVM 可测）：`Tokenizer → Parser → AST → MathBudget.checkBudget → MathBoxBuilder.buildBox → MathBudget.checkLayoutSize`，即 `parseAndBuildBox`（§2.4）。三个消费方各自调它：

1. **度量**（app）：`measureMathBox`（`VisualContentMeasuring.kt:314-339`）+ 降级（`:347-362`）；
2. **UI**（core/ui，热区）：`MathBoxComposeRenderer`（未读内容，存在性确认）；
3. **导出**（core/export，热区）：`CanvasMathBoxRenderer`（同上）。

契约"不得继续增加第三套公式/坐标绘制逻辑"（`:131`）的**语义裁定：不得新增第四处 parser/坐标逻辑**；把现有三处合并需要动 core/ui、core/export 两个热区，本批不可行也不必要——它们已经全部复用同一 core:model 链，没有第二套逻辑存在。

### 5.2 `MathContentLowering` 设计（清单点名的类，落 core/model）

```kotlin
// core/model —— 非热区；纯 Kotlin，零 android 依赖（与 MathBudget 同模块同约束）
object MathContentLowering {
    /**
     * 数学内容的共享 lowering：parse → AST → budget → box（+ 度量）与"未知 TeX 回可读文本"
     * 的唯一入口。成功 → MathBox（布局盒，baseline 自顶）；失败 → 可读文本 fallback
     * （ReadableMathText.formula 口径：原文透传，不重建）。
     */
    sealed interface Result {
        data class Box(val box: MathBox, val node: MathNode, val parse: MathParseResult) : Result
        data class FallbackText(val readable: String, val error: String) : Result
    }
    fun lower(input: String, fontSizePx: Float): Result
}
```

- `Box` 分支 = 现 `measureMathBox` 的成功路径原样收编；`FallbackText` 分支 = 现 `measureContentEntry` 降级路径原样收编（`degraded=true` 语义不变）。
- **降级语义必须完整**（两段式，缺一即漏）：
  1. **预算越界**（真实失败面）：`parseAndBuildBox` 对未知 TeX 极度宽容——未知命令降级为字面 atom，解析几乎不失败，失败主体是预算（输入>2000 字符、token/节点/深度/盒尺寸上限，`VisualContentMeasuring.kt:310-312` 注释自认）→ `FallbackText` 携带 budget error 字符串；
  2. **真解析异常**（连字面 atom 都不成立的输入）：`MathParser.parse` 抛异常 → `ReadableMathText.formula` 的 catch 原文透传（`ReadableMathText.kt:16-18`）。
  两段的 fallback 文本都走 text 度量路径量出宽/高/baseline，kind 保留 MATH、`degraded=true` 进摘要（`VisualContentMeasuring.kt:370-385` 既有）。
- **支持范围由公式金样限定**（契约 `:131`）：金样 = `MathBoxGoldenTest` 既有集合的扩集；不在金样内的输入不承诺盒形态、只承诺可读文本——这正是"不宣称完整 LaTeX"的落点。
- **新增指认的失败**：现行降级逻辑散在 `measureMathBox`/`measureContentEntry`（度量侧私有），未来 painter 回放（core/data）解锁 math 时**没有可跨模块调用的共享入口**——要么复制降级判断（造第二套降级逻辑 = 变相第三套），要么 internal 泄漏。`MathContentLowering` 作为 public 纯 Kotlin 对象消灭该失败；度量侧改为调用它（app 行为零变化，回归由 §5.4 判据钉住）。

### 5.3 回放解锁路径（S-08 的下游收益，实现归回放批）

`VisualLabelContent.Math` 现 fail-closed（`PAINTER_LABEL_MATH`）。lowering 到位后：app 装配器用 `MathContentLowering.lower` 的 `Box` 结果折成 `VisualLabelContent.Math(box, sizeSp, fontScale)` → painter 递归消费 `MathBox` 树绘制。**坐标语义（契约"明确 x/y 的共享 lowering"）**：`MathBox` 的 baseline 自顶、树内递归布局（`MathBoxBuilder.buildBox:351-620`）就是共享坐标事实——绘制侧**只消费 box 不重算几何**（"明确 x/y" = 几何唯一来源是 builder）。label 原点 (x,y) 仍是编译器一次算定的 DrawOps 值（`VisualDrawOps.kt:124-126`），与文本回放的舞步一致。

### 5.4 测试判据（`MathContentLoweringTest`；本批未运行）

1. 金样集 → 期望盒尺寸（扩 `MathBoxGoldenTest`，同字号口径 16px 基线对拍 `CANONICAL_SIZE_SP=16f`）；
2. 未知 TeX → `FallbackText` 且文本 = 原文透传（非重建）；
3. 预算越界（>2000 字符等）→ `FallbackText` 携带 budget error；
4. `lower` 与现行 `parseAndBuildBox`+`measureContentEntry` 组合逐字段等价（迁移回归，防降低降级保真度）；
5. digest 含 degraded 的既有断言回归（同 payload 不同降级态摘要不同）；
6. fontScale 联动：fontSizePx 放大 → 盒尺寸线性放大 → 触发 `checkLayoutSize`（M-03 N 家族共享）。

---

## 6. 官方文档证据（本轮实际抓取）

### 6.1 Paint（<https://developer.android.com/reference/android/graphics/Paint>，web_reader 全文抓取，2026-11-01）[引证]

- `getTextBounds(char[], int, int, Rect)`："Return in bounds (allocated by the caller) the **smallest rectangle that encloses all of the characters, with an implied origin at (0,0)**." CharSequence 变体（API 29）补注："**Note that styles are ignored** even if you pass `Spanned` instance. Use `StaticLayout` for measuring bounds of `Spanned`."——inkBounds 的官方语义与"忽略样式"精度边界。
- `measureText`："Return the width of the text."——advance 口径（端口 widthDu 采用），与 ink（getTextBounds）不同义，两者都进 artifact、各标口径。
- `ascent()`："Note that this is the ascent of the **main typeface**, and actual text rendered may need a larger ascent **because fallback fonts may get used**"；API 33+ `getFontMetricsInt(CharSequence,…)`："If the text is rendered with multiple font files, this function returns the **large ascent and descent that are enough for drawing all font files**."——API<33 混排行高低估边界（P1）的官方出处，与仓内实现一致（`VisualContentMeasuring.kt:437-452`）。
- `Paint()` 构造默认：Q+ 构造置 `FILTER_BITMAP_FLAG`、S+ 构造置 `ANTI_ALIAS_FLAG`——**官方确认平台默认 flags ≠ 0**，支撑仓内"API 34 实测 1283、必须显式置 0"的既有结论（`VisualContentMeasuring.kt:183-191,464-469`）。
- `equalsForTextMeasurement(Paint)`（API 28）："Returns true if the passed Paint will have the same effect on text measurement"——度量/回放两侧 Paint 配置一致性的官方等价判据（跨侧测试可用）。

### 6.2 Compose TextMeasurer（缓存先例）[引证-摘要级]

<https://developer.android.com/reference/kotlin/androidx/compose/ui/text/TextMeasurer>；android.googlesource.com 源（TextMeasurerHelper.kt）："Caches layout results internally using an LRU cache to optimize repeated measure calls. Reuses the cached layout when changing only **draw-affected parameters**"——官方对"按影响布局的输入键控缓存"的同型先例，佐证本仓 digest+环境指纹键控设计（§3.3 末行）。

### 6.3 Bitmap 字节口径 [待验证-仅字面引文层面]

ARGB_8888 = 4 B/px 的官方逐字引文本轮未取得（`Bitmap.Config` 参考页两次抓取均超时/仅导航壳）。字节口径由**仓内先例**支撑（`AndroidCanonicalAssetVault.kt:103-104,493`：3072×4096 ARGB_8888 = 50.3MB，即 ×4 精确成立，KD-32 采样预算依此）。按纪律标待验证；若实施轨需要逐字引文再取（Bitmap API reference）。

---

## 7. GitHub 参考评分（Android 文本测量/布局 + 数学渲染）

评分维度：可复用性 / license / 维护度 / 与当前技术栈兼容。命令：`gh search repos "latex render android"`、`gh repo view … --json licenseInfo,stargazerCount,pushedAt,latestRelease,isArchived`、`gh api repos/…/contents/LICENSE`（2026-11-01 实跑）。

| 项目 | stars | license（实读） | 最近 push / release | 评分 | 结论 |
|---|---|---|---|---|---|
| **androidx/androidx**（TextMeasurer / PrecomputedTextCompat / StaticLayout） | —（官方） | Apache-2.0 | 持续（androidx-main） | **可引** | 文本测量/布局/缓存唯一可直接引用的实现参考；其"布局影响输入键控缓存"与本仓 digest 设计同型。无 math 能力 |
| noties/Markwon | 3,421 | Apache-2.0 | 2024-04 / v4.6.2 (2021) | **设计参考** | markdown+math 集成质量高；但其 LaTeX 扩展捆绑 `jlatexmath-android`（GPL 衍生，`noties/jlatexmath-android` 实测 **archived**，2020 止更）→ **math 半边不可复制**；仅借鉴其异步 drawable/降级分层 |
| opencollab/jlatexmath | 574 | **GPL-2.0+**（LICENSE 实读前 10 行："GNU General Public License…version 2…or (at your option) any later version"） | 2022-02 / 1.0.7 (2018) | **否决** | license 一票否决 + 停更 3 年 |
| mksmbrtsh/jlatexmath-android | 200 | NOASSERTION（GPL 衍生） | **2018-07** | **否决** | license 否决 + 停更 7 年 |
| noties/jlatexmath-android | 79 | NOASSERTION（GPL 衍生） | **archived**（2020-04） | **否决** | license 否决 + 已归档 |
| rikkahub/jlatexmath-android | 2 | NOASSERTION（GPL 衍生） | 2026-07 / 1.5 (2026-07) | **否决** | 在维护但 license 家族否决 + 采用度近零（2★） |

**裁定**：数学 lowering 无任何 license 兼容的外部实现可复用——自研 `MathBudget/MathBoxBuilder` 链维持（契约本就明令复用它），外部项目仅作降级分层/异步绘制的设计参考。文本测量侧 androidx 是唯一引用来源（其 Apache-2.0 与本仓兼容）。检索轮次：关键词 3 轮内收敛（"latex render android" → jlatexmath 家族 license 核验 → Markwon 捆绑关系核验），未超纪律上限。

---

## 8. 来源清单

**本仓（静态读码，路径:行）**：`core/domain/.../VisualExecutionEngine.kt:114,131-151,155-228,241-259`；`app/.../visualjs/VisualContentMeasuring.kt:53-110,112-204,206-262,264-385,394-411,415-508`；`core/data/.../visual/QuickJsVisualExecutionEngine.kt:411-438,641-652`；`core/data/.../visual/VisualDrawOps.kt:119-153`；`core/data/.../visual/VisualDrawOpsPainterPure.kt:185-228`；`core/data/.../visual/VisualDrawOpsPainter.kt:50-54,72-92,183-220`；`core/data/.../visual/VisualDrawOpsPainterRendered.kt:23-38`；`core/data/.../visual/VisualSnapshotRestore.kt:27-71`；`core/model/.../MathBudget.kt:11-117,122-243,262-340,350-620,634-710`；`core/model/.../ReadableMathText.kt:12-34`；`core/data/.../capture/AndroidCanonicalAssetVault.kt:103-104,493`；`docs/current/visual-generation-contract.md:52,79,90,99-100,107,118-131,179,212`；`docs/research/2026-10-26-batch-24-4e2-admission-checklist.md:148,155,157`；`docs/research/2026-11-01-batch-38-baseline.md:60-62`；`docs/research/2026-10-27-content-measurement.md`（R2 前序，全文）；`.zcode/plans/plan-2026-11-01-batch-38-triple.md`。

**官方文档（本轮抓取）**：
- Paint API reference（getTextBounds/measureText/ascent/getFontMetricsInt/Paint() 默认 flags/equalsForTextMeasurement）：<https://developer.android.com/reference/android/graphics/Paint>
- Compose TextMeasurer：<https://developer.android.com/reference/kotlin/androidx/compose/ui/text/TextMeasurer>；源 <https://android.googlesource.com/platform/frameworks/support/+/HEAD/compose/ui/ui/src/commonMain/kotlin/androidx/compose/ui/text/TextMeasurerHelper.kt>
- （R2 前序文档已抓取并引证、本文沿用的：StaticLayout.Builder、PrecomputedTextCompat、android:isolatedProcess——URL 见 `2026-10-27-content-measurement.md` §7）

**GitHub（gh CLI 实跑元数据）**：<https://github.com/opencollab/jlatexmath>（LICENSE 实读）；<https://github.com/mksmbrtsh/jlatexmath-android>；<https://github.com/noties/jlatexmath-android>；<https://github.com/noties/Markwon>；<https://github.com/rikkahub/jlatexmath-android>；<https://github.com/androidx/androidx>。

---

## 9. 未验证项登记

| 项 | 状态 | 处置 |
|---|---|---|
| ARGB_8888 = 4 B/px 的官方逐字引文 | 未取得（Bitmap.Config 页两次抓取超时/导航壳） | 字节口径以仓内 KD-32 先例支撑并标 [待验证-引文层面]；实施轨如需引文再抓 Bitmap API reference |
| 基线"M-03 4MP/16MiB 常量 0 命中"的 grep | 本会话未复跑，以基线 `:61` 为据 | 实施轨开工时复跑确认（`4_194_304\|16_777_216\|MAX_TOTAL_PIXEL\|MAX_BITMAP`） |
| `MathBoxComposeRenderer` / `CanvasMathBoxRenderer` 内容 | 未读（两模块热区，只确认存在） | 不影响本设计（合并被裁定不做）；如需对拍绘制口径由实施轨读 |
| 隔离进程 A1/A2 假设（度量环境可用性、同度量逐位一致） | 沿用 R2 前序文档 [待验证] 标记不变 | 设备轨判定 |
| VisualLabelContent.Math 回放的设备像素级正确性 | 设计面，未实现未运行 | 归回放批 `MathContentLoweringTest` 设备半边 |
| 本设计的实施（三轨落码/测试） | 本批为研究文档，零代码改动、零 Gradle、零设备 | 实施轨按 §3.4/§4.2/§5.2 落点推进，开工前复核目标文件热区状态 |
