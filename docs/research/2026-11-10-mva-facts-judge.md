# 把 120 盲测的 fact_expectations 变成可判定检查：RenderScene/DrawOps 事实提取、像素不可判项处理、VLM 判官协议与 C 通道校准件（批次 51 / R3）

> **性质**：研究 + 可执行协议设计。**零产品代码改动、零 Gradle、零设备写入、零真实 Provider 调用、零门阈值改动**。写盘产物：本文件 + `docs/research/evidence/2026-11-10-batch-51/mva-facts-probe/`（3 个探针脚本 + 3 份 JSON + stdout）。其余文件未触碰（§9 有 `git status` 前后读数）。
>
> **时点**：HEAD `8dd29e96`（开工前 `git status` 读数见 §9）；`git status --porcelain -- tools/figure_eval` 开工前 = ` M figure_eval_gate.py` + `?? mva_gate.py` + `?? judge_channel_map.json` + `?? test_mva_gate.py`（4 行，均为他线/前批遗留，本次未改）。
>
> **口径**：`已实证` = 本会话跑过并留下命令与输出；`读码` = 只读了源码未运行；`未跑` = 明确没做。**不改任何门阈值**：90/95/90/0、p95 30 s/120 s 全部按 `tools/figure_eval/figure_eval_gate.py:85-141` 原值引用；`check_fact_expectations` 的比对语义（subset/disjoint/相等）一字未改。
>
> **本文件不声称 MVA 会通过**。它只回答"每条事实怎么判、判不了怎么写、判官怎么不被宽松偏差骗、校准件怎么最小落地"。

---

## 0. 结论摘要（先给可执行结论）

1. **"RenderScene" 在本仓的落地形态是两个结构件，不是一个**：SceneV1（模型产出的场景，`core/data/src/main/resources/visual/visual-compiler.js:10-25` 的键面）→ 编译器 → **DrawOps V1 显示列表**（`core/data/.../visual/VisualDrawOps.kt:165-172`；引擎 `VisualExecutionResult.Rendered.drawCommandsJson`，`core/domain/.../VisualExecutionEngine.kt:240-262`）。事实提取应以 **DrawOps 为准**（它是 painter 真正回放的东西），SceneV1 只在"角色绑定"时需要（§2.3）。
2. **三个信息缺口必须认账，不能猜**：
   - **label 文本不在显示列表里**：DrawOps `label` 只有 `contentId`（`VisualDrawOps.kt:127-136`），文本在 prepare 批的 `contentId→payload`（`VisualExecutionEngine.kt:166-230`）。提取器必须同时拿"度量批"，否则 label_set 全不可判。
   - **节点 id 在编译时被丢弃**：`path`/`label` op 的键面没有 `id`（`VisualDrawOpsParser.kt:375-506`），所以"作者用节点 id 声明角色"这条**不能从 DrawOps 读到**；只有 `hitRegions[].id` 保留（`VisualDrawOps.kt:143-147`）。
   - **像素臂缺的是信息，不是分辨率**：81 条 critical `numeric` 要求 1e-9/1e-6 精确值（另有 6 条 0.01 可 OCR；§3.1 与 §3.7 复核），这些值在栅格上**不存在**。任何"VLM 判官证明像素臂 critical 错误=0"的表述都是假声明。
3. **可判性分类（本轮实测计数）**：288 条事实 = **A1 204（crit 129）/ A2 70（crit 66）/ D3 14（crit 14）**。A1 = 只看显示列表可判；A2 = 需作者角色声明（数值仍由代码算）；D3 = 显示列表不可判（须 C 或 UNVERIFIED）。任务级：**全部 A1 的题 64/120；含 A2 的题 42；含 D3 的题 14**（全部落在 `handwriting_mark_removal` 7 + `answer_stroke_erase` 7）。
4. **14 题 D3 ⇒ 门在算术上过不了，这不是"覆盖率打折"**：即使其余 106 题全过，`106/120 = 0.8833 < 0.90`（首次门）且 `< 0.95`（最终门）；`handwriting_removal` 组最多 `6/20 = 0.30 < 0.90`。所以 **C 通道对 `label_absent` 不是可选项，是 MVA 过门的必要条件**；不实现 C 就只能如实写 `NOT_EVALUABLE/partial`，不得写 PASS。
5. **B 通道对 `label_absent` 无效——本轮实证**：夹具的禁写值是**类名**（"红笔勾/对号/波浪线"），不是字面串。文本级 `isdisjoint` 在图上残留 `x=5` 时仍返回 `accepted=true`（`probe_result.json#per_fact…residue_present`）。→ `mva_gate.CHANNEL_RULES` 的 `label_absent → A|B` 规则应改为 **A|C**（派生文件规则变更，非阈值变更；影响 14 条，totals `A|B 116→102`、`A|C 40→54`，`test_mva_gate.py:102` 的期望值须同步）。
6. **提取与判定分离已端到端跑通**：Node 用**生产同源编译器**把 8 个最小场景编成 DrawOps，Python 提取器对 10 个 (case, task) 判定**全部符合预期**（`probe_extract.py` EXIT=0；`all_matches_expectation=true`）。其中"几何对、标签错"时 numeric 通过而 label 失败；"不回放 concat"时 `step_order` 读成 `["移项","移项","移项"]` 被拒（提取器反向对照）。
7. **计划点名的 MVA 入口命令在 `-I` 下跑不起来**：`python -I tools/figure_eval/mva_gate.py` → `ModuleNotFoundError: No module named 'figure_eval_gate'`（`-I` 隐含 `-P`，脚本目录不进 `sys.path`）。最小修 2 行（§7.4），否则"计划 §验证命令第 5 条"是不可复跑的命令。
8. **判官只做两件事**：B 通道**转写**（只报图上写了什么）+ C 通道**闭式类判定**（方向/顺序/存在性，枚举作答）。**永不问"这张图对不对"**，永不让 C 翻 A 的失败。宽松偏差用**校准件**度量成数（JLI = 注入已知错误上的接受率），逐 kind 设准入。单 provider 家族时不得声称"双家族一致"（证据限制如实写）。
9. **C 通道校准件最小形态**：`core:data` androidTest 里手写 6 个校准件（4 个 C 相关 kind，各含正确/注入错）DrawOps → `VisualDrawOpsParser.parse` → `VisualDrawOpsPainter` 画到 Bitmap → PNG + manifest；Python runner 用与盲测**同一提示词模板**问、按 kind 算 `false_accept/false_reject`。准入：某 kind `false_accept > 0` ⇒ 该 kind 的 C 判定作废（回 UNVERIFIED），**不许**拿"宽松"当"通过"。

---

## 1. 输入与口径（本轮实读/实跑）

| 项 | 内容 |
|---|---|
| 计划 | `.zcode/plans/plan-2026-11-10-batch-51-final.md`（全文 117 行；R3 = `:35`，G4 = `:73-78`） |
| 既有门 | `tools/figure_eval/figure_eval_gate.py`（`GATES:85-141`、`produce_output_facts:273-285`、`_corrupt:288-303`、`check_fact_expectations:642-672`、`first_pass=GENERATED_OK:892`、`critical_error_accepted=False:983`、`JsPort:376-631`） |
| 读数器 | `tools/figure_eval/mva_gate.py`（`CHANNEL_RULES:55-72`、`fact_channel:154-166`、`gate_reading:298-448`、`records_from_harness_report:451-471`、`preflight:474-517`） |
| 夹具 | `tools/figure_eval/tasks_blind_120.json`（120 题 / 288 事实 / 209 critical）、`tasks_dev_24.json`、`freeze_manifest.json`、`judge_channel_map.json` |
| 显示列表 | `core/data/.../visual/VisualDrawOps.kt`（词表）、`VisualDrawOpsParser.kt`（严格键面/序列化）、`VisualDrawOpsPainter.kt`（回放语义） |
| 场景/编译 | `core/data/src/main/resources/visual/visual-compiler.js`（SceneV1→DrawOps，label 投影 `:934-952`）、`core/domain/.../VisualExecutionEngine.kt`（`Rendered` / 度量端口 / 预算） |
| 引擎/app | `core/data/.../QuickJsVisualExecutionEngine.kt`（measurer 注入 `:168-169`、`measure:395`）、`app/.../visualjs/VisualContentMeasuring.kt:54-79`（app 侧 measurer）、`VisualJsProtocol.kt`（信封键面） |
| 前序研究 | `docs/research/2026-11-09-mva-judge.md`（R5 通道设计、盲法、taxonomy）；`docs/research/2026-11-09-batch-50-record.md`（MVA 0 可评） |
| 外部 | VisionQ（arXiv 2610.00666）、ChartJudgeBench（arXiv 2609.24210）、GGBench（arXiv 2511.11134）——沿用 R5 §1 的【V】级摘录，**本轮未重读、未新增外部结论** |

---

## 2. 判定对象：从 RenderScene 能读出什么、读不出什么

### 2.1 DrawOps V1 的证据面（读码）

| op | 携带 | 对事实判定的价值 |
|---|---|---|
| `path` | `path[]`（M/L/C/Z，局部坐标）+ `fill` + `stroke` + `alpha` | 几何事实的**唯一**来源；需按 `concat` 栈变换 |
| `label` | `contentId` + `x/y`（编译器一次算定的**根空间**左缘+baseline）+ 对齐回显 + `color/alpha` | 位置可判；**文本要另取** |
| `concat`/`save`/`restore` | 6 元矩阵 | 回放必需；漏了就把局部坐标当根坐标 |
| `clip` | 已烘焙的组合坐标路径 | **可见性掩码**：被裁掉的元素不能计入计数/文本 |
| `hitRegions[]` | `id` + 根空间路径 | 唯一保留 id 的元素通道（可作角色载体，§2.3） |

**label 坐标不套栈**：编译器在组内先投影再发射（`visual-compiler.js:934-952`），painter 也以 `canvas.setMatrix(rootMatrix)` 处理 label（`VisualDrawOpsPainter.kt:213-225`）。提取器若对 label 再乘一次组矩阵就是双重变换——这与 painter 注释里点名的头号回放错误同型。

### 2.2 显示列表**没有**的三样东西

1. **文本**：`label` 只有 `contentId`。真实事务里 `contentId→payload` 只存在于 prepare 批（`VisualContentMeasureBatch.entries`，`VisualExecutionEngine.kt:166-183`）；`VisualMeasuredContent` 只回 `digest`（`:212-228`）。→ **提取器必须与"记录型 measurer"同批采集**：在设备适配器里包一层 `VisualContentMeasurer`（`app/.../VisualContentMeasuring.kt:54-79` 的实例），记录 `payload + widthDu/heightDu/baselineDu`。
2. **节点 id**：`VisualDrawOp` 的六个原语没有任何 id 字段（`VisualDrawOps.kt:96-140`），解析器键面同样没有（`VisualDrawOpsParser.kt:375-506`）。SceneV1 的 `node.id` 在编译后**消失**。
3. **可见性/遮挡语义**：`clip` 只影响绘制；`visible:false` 的组整树不发射（`visual-compiler.js:815-818`），而 `alpha=0` 的元素**仍会发射**（op 的 alpha 为 0）但画不出来。提取器必须把 `clip` 当掩码、把 `visible:false` 当不存在、把 `alpha=0` 当不可见，否则计数会虚高。

### 2.3 角色绑定的三条可选载体（A2 的前提）

| 载体 | 可行性 | 代价 | 判定 |
|---|---|---|---|
| **SceneV1 `id` + 场景→ops 对齐** | `Rendered.sceneJson` 仍在（`VisualJsProtocol` 信封有 `sceneJson`）；编译器按遍历序发射（每组 save/clip/concat/children/restore，`visual-compiler.js:855-872`），可按"每个 path 节点恰 1 个 path op、每个 label 节点恰 1 个 label op"对齐，并用 `stats.nodes/pathCommands/labels/ops` 交叉校验 | 无 schema/协议变更；需一组"对齐正确性"测试 | **推荐** |
| `hitRegions[].id` 作角色标签 | id 与根空间路径都保留（`VisualDrawOps.kt:143-147`） | 语义挪用（命中区本为交互）；作者要为每个角色重复一条路径 | 备选 |
| 扩 DrawOps schema（op 加 `id`） | 直接可读 | V1→V2：compiler/parser/painter/validator/PDF/快照全链改；批次 50 刚冻结 V1 | **不推荐**（超出本批最小改动） |

Role 只回答"这是哪个元素"；**数值仍由代码从 ops 算**。它不能回答"它等于多少"，所以角色绑定不会变成自报。

---

## 3.（a）fact_expectations → 可判定映射与容差

### 3.0 总规则（对所有 kind 生效）

| # | 规则 | 依据 |
|---|---|---|
| R1 | **判定只走 `check_fact_expectations`**（`figure_eval_gate.py:642-672`），不另写比较器；subset/disjoint/相等语义**不改** | 改语义就是改门 |
| R2 | **数值容差只取夹具的 `tolerance`**（1e-9 ×80 / 1e-6 ×5 / 0.01 ×6，R5 §6.2）。提取器**不得**自加 epsilon；聚类/对齐用的 0.5 du 是**几何容差**，与事实容差分离，且必须写进报告 | 容差是 oracle 的一部分 |
| R3 | **提取器不读 `fact_expectations`**（签名只收：显示列表 + 度量批 + 角色表 + viewport）。测试用"改预期值不改事实"钉住（同 `test_driver_outcome_independent_of_expectation_values` 形状） | PRD-09 :132 |
| R4 | **未知 key / 未知 kind 一律报错**（不默认"可判"），与 `mva_gate.fact_channel:166` 同纪律 | 防止新 key 静默落进"视觉能判" |
| R5 | **坐标一律先回放到根空间**（path 套栈、label 不套栈、clip 当掩码）；`Double.toString` 往返保证数值可精确复现（`VisualDrawOpsParser.kt:704-706`） | 见 §2.1 |
| R6 | **文本取 prepare 批 payload**，规范化只做：去首尾空白、行分隔符→单空格、U+2212/U+FF0D→`-`。**不做** NFKC/大小写/下标折叠（`F/F₀` 的下标 0 与 ASCII 0 是不同字符串） | `axis_min` 等负号、`axis_labels` 的下标 |

### 3.1 numeric（17 个 key，91 条）

| key | 提取规则（代码做） | 容差 | 类 |
|---|---|---|---|
| `segment_length_ratio` | 共线边的**路径结构**分段（相邻边/子路径边界，不看几何间隙）→ 取主游程恰 2 段 → `len[0]/len[1]`（沿方向序） | 夹具 1e-9 | **A1**（探针实证） |
| `arm_length_ratio` | 角色：支点 + 两条力线；臂长 = 支点到力线垂距；比值 | 1e-9 | A2 |
| `point_x` / `point_y` | 由刻度标签锚点拟合轴标定（值↔位置线性映射）；角色：点标记；反解坐标 | 1e-9 | A2 |
| `axis_min` / `axis_max` | 轴端刻度文本 → 数值（R6 负号归一） | 1e-9 | A1（印刷值） |
| `converted_ms` | 换算表单元格文本 → 数值 | 1e-9 | A1（印刷值） |
| `discriminant` | 公式结果文本（`Δ=…`）→ 数值 | 1e-9 | A1（印刷值） |
| `real_distance_cm` | 题面要求图上**标注**实际距离（如 "150000 厘米"）→ 文本解析 | 1e-9 | A1（印刷值） |
| `row_count` / `column_count` | 网格线唯一 y/x 坐标数 − 1（几何计数，返回 float） | 1e-9 | A1 |
| `value_of_1_2` / `value_of_1_3` | 数轴 0/1 刻度锚点拟合 → 标记点位置反解 | 1e-9 | A2 |
| `u_at_half_height` | 角色：曲线 + 容器；取 y=半高处曲线 x，除以参考宽 | 1e-9 | A2 |
| `curve_value_at_x_equals_h` | 角色：曲线 + 右边界；取 x=h 处曲线 y（轴标定后） | 1e-9 | A2 |
| `area_W_over_F0h_at_half` | 角色：阴影区域；路径积分（C 段按 16 分展平）→ 面积 /（轴宽×轴高） | 1e-9 | A2 |
| `resultant_amplitude` | 角色：合成曲线 + 基线；max\|y−baseline\| 换算到轴单位 | 1e-9 | A2 |

**"印刷值"类的边界（必须写进报告）**：`axis_min/max`、`converted_ms`、`discriminant`、`real_distance_cm` 核验的是**图上印了什么**，不是"图与所印数值几何一致"。例如比例尺题可印 "150000" 而线段长度不按比例——本类判不出。这类事实的合格措辞只能是"标注正确"，不得写成"图示正确"。`real_distance_cm` 的 du↔cm 换算在契约里不存在（1 du=1 dp，`VisualExecutionEngine.kt:86`），所以几何一致性**明确不做**。

### 3.2 label_set（16 个 key，96 条）

| 项 | 规则 |
|---|---|
| 提取 | 所有可见 `label` op 的 payload（去重、保序），**减去**被 clip 掉的元素 |
| 判定 | 既有 subset 语义（`expected ⊆ extracted`）；**多报不失败**，但报告必须写 `verified_with_extras`（R5 §5 #6 的已知宽松） |
| 容差 | 精确字符串（R6 规范化后） |
| 类 | **A1**（探针实证 `ratio_label`、`step_order` 的文本侧） |

### 3.3 count（8 个 key，47 条）

| key | 规则 | 类 |
|---|---|---|
| `segment_count` | 主游程的分段数（路径结构） | A1（探针） |
| `step_count` | 闭合且面积 > 100 du² 的子路径数 | A1（探针） |
| `cell_count` | 闭合格子数 = (行线−1)×(列线−1) | A1 |
| `tick_count` | 与轴共线族**垂直**的短线段数（长度 ≤ 0.15×轴长） | A1 |
| `marked_points` | 小闭合形状（bbox ≤ 12 du）或实心点路径数 | A1 |
| `label_count` / `kept_label_count` | 可见文本条数 | A1 |
| `wave_count` | 曲线子路径聚类数（按 C 控制点拟合的极值段分组） | A1 |

### 3.4 ordering（3 个 key，16 条）

| key | 规则 | 类 |
|---|---|---|
| `step_order` | 卡片（闭合形状）主散布轴排序 → 每卡内恰 1 个标签 → 按卡序输出文本 | A1（探针；反向顺序实证失败） |
| `ascending_order` | 数轴标签按锚点 x 排序；文本按分数解析后 **只用于** 核对升序（预期列表本身是字符串序列，比较仍走相等） | A1 |
| `phase_order` | 角色：阶段标签组；按锚点主轴排序 | A2 |

**严格性提醒**：`ordering` 的比较是**整表相等**（`:661-662`），所以提取结果不能带标题/单位等额外标签。提取器若判定"序列成员不确定"（同带内 0 或 >1 个标签），必须**不输出**该事实（→ mismatch），不许猜。

### 3.5 direction（5 个 key，24 条）

| 项 | 规则 | 类 |
|---|---|---|
| 提取 | 角色：箭头组；箭头 = 两条短边在尖端相接（或填充三角形，尖端离对边最远）；方向 = 尖端−底边中点的主方向，量化到夹具枚举 `{down, along_incline_up, up}`（`mva_gate.fixture_sanity:177` 只认这三个值） | A2 |
| 判定 | 精确枚举相等（`:659-660`） | — |
| 备注 | 方向在夹具里全 critical（24/24）；VisionQ 实测判官在 Relation 轴最弱（45.2%，R5 §1.1），所以 A2 能算就不要交给 C | — |

### 3.6 label_absent（2 个 key，14 条）

| 项 | 规则 | 类 |
|---|---|---|
| 夹具值 | `["红笔勾", "对号", "波浪线"]` / `["x=5", "答：x=5"]`——**类名与字面串混用** | — |
| 文本级判定 | 对 `["x=5","答：x=5"]` 这类字面串有效；对**类名**无效：图上画了一个红勾，可见文本里没有"红笔勾"三个字，`isdisjoint` 仍为真 | **D3** |
| 本轮实证 | 残留 `x=5` 的图 → `removed_handwriting` 判定 **accepted=true**（`probe_result.json#per_fact_channel_independence.residue_present__removed_handwriting_text_level`）。这是**假接受**，不是"判对了" | — |
| 结论 | 必须 C（视觉，类名→视觉类判定）并过校准；否则该事实 UNVERIFIED。**不许**把"文本里没有这三个字"当作"图上没有红勾" | — |

### 3.7 集合级：可判性分类与计数（本轮实跑）

```text
$ python -I -X utf8 docs/research/evidence/2026-11-10-batch-51/mva-facts-probe/probe_decodability.py
{"facts": 288,
 "by_class": {"A1": 204, "A2": 70, "D3": 14},
 "critical_by_class": {"A1": 129, "A2": 66, "D3": 14},
 "tasks": 120, "tasks_all_A": 106, "tasks_all_A1": 64, "tasks_with_A2": 42, "tasks_with_D3": 14,
 "channel_x_class": {"A x A1": 53, "A x A2": 41, "A|B x A1": 102, "A|B x D3": 14,
                     "A|B|C x A1": 38, "A|C x A1": 11, "A|C x A2": 29}}
```

| 读数 | 含义 |
|---|---|
| `A1 204 / crit 129` | 纯结构可判的事实；A 通道的"硬底" |
| `A2 70 / crit 66` | 依赖作者角色声明；**不做角色绑定，A 通道就少 70 条** |
| `D3 14 / crit 14` | 全部 `label_absent`，全部在 `handwriting_removal` 组的两个图型里 |
| `tasks_all_A1 64/120` | 不做角色绑定、不接 C 时，最多 64 题可完整判 |
| `tasks_all_A 106/120` | 做角色绑定后 106 题可完整判 |
| `A x A2 = 41` | **"A 只"的 94 条里有 41 条其实要角色绑定**——R5 把 A 只理解为"纯结构"是高估 |

### 3.8 探针实证：真实编译器 → 提取器 → 既有判定器

```text
$ node docs/research/evidence/2026-11-10-batch-51/mva-facts-probe/probe_compile.mjs
（8 个场景经 core/data/src/main/resources/visual/visual-compiler.js 编译，全部成功）

$ python -I -X utf8 docs/research/evidence/2026-11-10-batch-51/mva-facts-probe/probe_extract.py
EXIT=0   all_matches_expectation=true
```

| case | 题 | 期望 | 实得 | 事实读数 |
|---|---|---|---|---|
| ratio-1to1-correct | BLD-PROPCOORD-001 | 通过 | 通过 | ratio 1.0 / label ["1:1"] / count 2 |
| ratio-2to3-correct | BLD-PROPCOORD-005 | 通过 | 通过 | ratio **0.6666666666666666**（与夹具浮点字面量逐位相等）/ label ["2:3"] |
| ratio-1to1-rotated | BLD-PROPCOORD-001 | 通过 | 通过 | 组矩阵 `[0,1,-1,0,180,120]` 旋转 90° 后仍按根空间判为 1.0（游程方向 `(0,1)`，长 90/90） |
| ratio-1to1-correct | BLD-PROPCOORD-002 | 失败 | 失败 | ratio + label 双 mismatch |
| ratio-2to3-correct | BLD-PROPCOORD-001 | 失败 | 失败 | ratio + label 双 mismatch |
| ratio-label-mismatch | BLD-PROPCOORD-001 | 失败 | 失败 | **只有 label mismatch**（numeric 通过；逐事实见下） |
| flow-order-correct | BLD-FLOWFORM-001 | 通过 | 通过 | count 3 / order ["移项","合并同类项","系数化为1"] |
| flow-order-reversed | BLD-FLOWFORM-001 | 失败 | 失败 | 只有 ordering mismatch，count 仍 3 |
| handwriting-residue-present | BLD-DEHANDWR-001 | 失败 | 失败 | 仅因 `kept_printed_labels` 失败；`removed_handwriting` **通过**（见 §3.6） |
| handwriting-residue-absent | BLD-DEHANDWR-001 | 失败 | 失败 | 只有 1 个印刷标签 → subset 如实拒绝（探针场景不完整） |

逐事实（`probe_result.json#per_fact_channel_independence`）：

```json
{"geometry_ok_label_wrong__segment_length_ratio": {"value": 1.0, "accepted": true},
 "geometry_ok_label_wrong__ratio_label":          {"value": ["3:1"], "accepted": false},
 "residue_present__removed_handwriting_text_level":{"value": ["x=5"], "accepted": true},
 "residue_absent__removed_handwriting_text_level": {"value": ["题干第(1)问"], "accepted": true},
 "rotated__segment_length_ratio":                  {"value": 1.0, "accepted": true}}
```

**提取器反向对照**（A 通道版的"注入一处错必须报错"）：关掉 `concat` 回放后，`flow-order-correct` 的 `step_order` 读成 `["移项","移项","移项"]` → 判错 ✓。但 `ratio-1to1-rotated` 的比值在相似变换下不变，**仍为 1.0 通过**——所以这个对照只对"位置相关的 key"有判别力，不能被当成通用的"栈漏了检测器"。这条要写进测试说明，避免下次有人拿它证明"栈一定回放了"。

---

## 4.（b）像素不可判事实（A 只）如何处理：fail-closed 但不虚假

### 4.1 三个状态，不许混用

| 状态 | 定义 | 计数口径 |
|---|---|---|
| `DECODED` | 该事实由 A1/A2 从显示列表算出，或由**已准入**的 C 判定 | 进"合格/不合格" |
| `UNDECODABLE` | 该 kind/key 在当前产物形态下**没有判定信息**（如 IMG 臂的 1e-9 numeric；JS 臂的角色未声明） | **不进分母的合格数**，也**不记 0**；任务标 `NOT_EVALUABLE` |
| `JUDGE_UNAVAILABLE` | 该 kind 需要 C，但校准未过/无视觉 provider/判官返回不可用 | 同上 |

三条硬纪律：

1. **不可评 ≠ 失败**：`UNDECODABLE` 不得写进 `verify` 的 mismatch（否则 `0.88` 会被读成"质量差"），也不得算合格。`mva_gate._rate:282-295` 已经用 `value=None` 表达"无样本"，这里沿用。
2. **不可评 ≠ 通过**：任务只要有一条 critical 事实 `UNDECODABLE/JUDGE_UNAVAILABLE`，该任务整题不判合格（PRD-09 :132"分歧未决不计合格"的同向纪律）。
3. **覆盖率必须一起报**：报告写 `coverage = evaluable_tasks / 120`；`partial = coverage < 1`。**`partial=true` 时不得声明门 PASS**——不是阈值问题，是分母不同（106 题的 90% 与 120 题的 90% 不是同一个命题）。

### 4.2 两个臂的读数会是什么（算清账，不写"预计"）

| 臂 | A1 | A2 | D3（label_absent） | 可完整判的题 | 门能不能读 |
|---|---|---|---|---|---|
| **JS（无角色绑定、无 C）** | 204 | 0 | 0 | **64** | 不能（64/120 的任一比率都过不了 90/95） |
| **JS（有角色绑定、无 C）** | 204 | 70 | 0 | **106** | 不能：`106/120=0.8833`；`handwriting_removal` 组 `6/20=0.30` |
| **JS（角色绑定 + C 过校准）** | 204 | 70 | 14 | **120** | 可以读（读数是多少由实跑决定，本文件不预测） |
> 表内 A1/A2/D3 是**A 通道视角**的列；IMG 臂的非 A 事实由 B（OCR 转写）/C 承担，不填进 A1/A2。

| **IMG（有 provider、无结构化 sidecar）** | 0（栅格上没有结构化事实） | 0（无角色可读） | 14（C；其余靠 B 转写，不计入 A1/A2） | **≤63**（=120 − 57 题含 critical A 只事实） | 不能：`63/120=0.525`；A 只事实在栅格上不存在（R5 §6.2 的 87 条 critical 不变） |

**IMG 臂的上限推导**：`mva_gate` 预检本轮实测 `tasks_with_critical_a_only = 57`、`tasks_with_all_critical_a_only = 27`（§9 第 3 条）。57 题含至少一条 critical A 只事实，这些事实的期望值（1e-9/1e-6 精确值、>6 的计数）在栅格上不可读，所以 IMG 臂最多 63 题可评；即使 OCR 与 C 全部理想，`63/120 = 0.525 < 0.90`。**这不是 provider 问题**，有 provider 也过不了。

**对计划的直接含义**：G4 任务 29 的"120 盲测跑分"在 JS 臂上**必须**包含"角色绑定 + C 校准"两件；缺任一件，正确写法是 `NOT_EVALUABLE/partial`，不是"接近 90%"。IMG 臂即使拿到 provider 也仍应保持 **BLOCKED**（缺的是结构化 sidecar，不是图像后端）——这与计划 `:12` 一致，但理由要写对。

### 4.3 `mva_gate` 的最小改动（不改阈值）

| 改动 | 位置 | 说明 |
|---|---|---|
| 记录里带 `fact_channels` | `gate_reading` 输入形状 | 每条事实一个状态：`status ∈ {DECODED, UNDECODABLE, JUDGE_UNAVAILABLE}`、`class ∈ {A1, A2, C}`、`value` |
| 任务可评 = 全部 critical 事实 DECODED | `gate_reading:334-341` | 现在只要求"有 facts"；改为"critical 全覆盖" |
| 报 `coverage` / `partial` | 返回值 | `coverage=samples/120`；`partial = samples < 120` |
| `met` 在 partial 时必须为 false | `_rate` 调用点 | 只改"何时可读"，不改阈值数字 |
| `label_absent` 规则 A\|B → A\|C | `CHANNEL_RULES:62-63`、`fact_channel:160-161` | 派生文件规则；影响 totals 与 `test_mva_gate.py:102` 期望值（同步改测试，属规则变更登记） |

**不改**：`GATES` 任何数值、`check_fact_expectations` 任何比较、120/24 集任何字节。

### 4.4 像素不可判的具体写法（报告模板）

```
arm=JS  coverage=106/120  partial=true  evaluable=false
undecodable: [{task:"BLD-DEHANDWR-001", key:"removed_handwriting", reason:"class-name fact; C judge not calibrated"}]
gate: NOT_EVALUABLE（分母不足 120；不得写 0.8833 为"质量"）
descriptive_only: {decoded_tasks:106, accepted:..., rejected:...}   # 描述统计，非门
```

---

## 5.（c）VLM 判官盲测协议与宽松偏差对冲

### 5.1 判官的职责边界（先压到最小）

| 通道 | 判官做什么 | 判官**不**做什么 |
|---|---|---|
| A（A1/A2） | 无 | 不参与 |
| B（转写） | 只报"图上出现的字符串/数值/标记"（结构化答案表） | 不比较、不下结论 |
| C（闭式类判定） | 只回答枚举问题（方向四选一、顺序读出、类名存在性三选一） | 不问"图对不对"、不问"是否合格"、不打分 |

**优先级 A > B > C**：A 失败不能被 B/C 翻案；C 只能判"不合格/未决"，不能把 A 的失败改判为合格。

### 5.2 盲法（可复核的实现口径）

| 项 | 规则 | 落点 |
|---|---|---|
| 匿名 id | `anon_id = "T"+sha256(salt‖task_id)[:10]`（`mva_gate.anon_id:271-273`）；salt = run seed，映射表只进报告 | 提示词里**只出现** `anon_id` |
| 不可见字段 | `task_id`、`group/group_name`、`figure_type`、`sample_category_4e`、`fact_expectations`、`review_source`、程序源码 | 单测：提示词字符串扫描，命中即失败 |
| 可见内容 | 渲染 PNG + 该题的**判据问题清单**（不含预期值）+ 选项枚举 | §5.3 |
| 呈现顺序 | 每题两次：选项正序 / 反序（seed 记录）；两次不一致 = 未决 | 位置偏差对冲 |
| 判官与作者不同源 | 判官不得是写程序的模型；同家族时必须在报告写"单家族"限制 | 证据限制（PRD-09 :132） |
| 记录 | provider / model id / temperature / max tokens / prompt sha256 / 图片 sha256 / 渲染器版本 / run seed | 报告 `judge` 块 |

### 5.3 问题构造（每 kind 一个模板，绝不问总分）

| kind | 问题形态（示例） | 答案枚举 | 代码怎么用 |
|---|---|---|---|
| `label_absent`（类名） | "图中是否出现下列元素？逐项回答：红笔勾 / 对号 / 波浪线" | `present / absent / unreadable` | 任一 `present` → 不合格；全 `absent` 且两次一致 → 合格；`unreadable` → 未决 |
| `direction`（仅当 A2 不可用时的兜底） | "标有'摩擦力 f'的箭头指向？" | `沿斜面向上 / 沿斜面向下 / 竖直向下 / 无法判断` | 与 `along_incline_up/down/...` 映射后相等 |
| `ordering`（仅兜底） | "从上到下依次读出三个步骤的名称" | 文本序列 | 与预期整表相等 |
| `label_set`（B，IMG 臂用） | "读出图中所有可见文字，逐条列出" | 字符串数组 | subset 比较（代码做） |

**禁止问法**："这张图正确吗？""是否满足题目要求？""质量如何？"——ChartJudgeBench 的系统性 over-Accept 正是被这类问法喂出来的（R5 §1.2【V】）。

### 5.4 宽松偏差的度量与准入（不是口号）

定义 **JLI（judge leniency index）** = 在**已知注入错误**的产物上判官回答"正确/合格"的比例，逐 kind 计算。

| 量 | 定义 | 用途 |
|---|---|---|
| `false_accept` | 注入错 → 判官仍判对 | **JLI**；>0 即该 kind 判官不合格 |
| `false_reject` | 未注入（正确图）→ 判官判错 | 可用性 |
| `order_inconsistency` | 正序/反序答案不一致率 | 位置偏差 |
| `unreadable_rate` | 答案 `unreadable` 比例 | 可用性 |

**准入规则（fail-closed）**：某 kind `false_accept > 0` ⇒ 该 kind 的 C 判定**不可引用**，该 kind 只能走 A；无 A 可用 ⇒ 该事实 `UNVERIFIED`，该题不计合格。校准样本量、注入清单、逐 kind 三率全部入报告。

**必须说清的一条限制**：C 只能用于"错误在视觉上**可辨**"的事实。1e-9 的 numeric 注入一个 1.02 倍偏差，人眼与 VLM 都看不出来——这类事实**永远不进 C**（这也是 R5 §4.2 把 1e-6 以下锁死 A 的原因）。校准集因此只用**类别型**注入（反向、换序、出现/消失、粗计数 ±2）。

### 5.5 对冲清单（逐项 → 本协议的落点）

| # | 偏差 | 对冲 | 落点 |
|---|---|---|---|
| 1 | 系统性 over-Accept | 不问结论、只问测量/转写；比较由代码做 | §5.3 + `check_fact_expectations` |
| 2 | 位置/last-option 偏差 | 双序 + 选项洗牌 + 不一致率上报 | §5.2 |
| 3 | 选对图给错理由 | 判据写进问题、按 criterion 分开报；报告 `evidence_text`（判官指出依据的图上文字/标记） | §5.3 |
| 4 | RL 模型更宽松 | 校准 JLI 准入；单家族时如实写限制 | §5.4 |
| 5 | 判官弃答被当成通过 | `unreadable` 合法但**计不合格/未决** | §5.3 |
| 6 | 分歧折成合格 | 双序不一致 = `unresolved_disagreement` +1 | `mva_gate` register |
| 7 | 像素指标假安慰 | SSIM/LPIPS 一律不进任何门 | 明确不用 |
| 8 | 小切片当结论 | 任何 n<20 的切片标 `unreliable`，只描述 | 报告字段 |
| 9 | 判官看到预期值 | 提示词扫描单测 + 只给问题清单 | §7.2 测试 |

### 5.6 判官不可用的处理

`JUDGE_UNAVAILABLE` 的三种真实来源：无视觉 provider / 模型拒图 / 校准未过。三者都**不是** 0 分，也都**不是** PASS：对应任务 `NOT_EVALUABLE`，`coverage` 下降，门不可读。**禁止**用"文本模型假装看图"或"让作者模型自评"填空。

---

## 6.（d）C 通道校准件最小实现

### 6.1 校准件的形态（为什么必须走设备 painter）

C 判官看的是**像素**；校准图必须由**产品同一条回放链**产出，否则校准的是另一条渲染器。仓内可用链：

```
手写 DrawOps V1（夹具）
  → VisualDrawOpsParser.parse(text, frozenContentIds)     # 与引擎同严格度（VisualDrawOpsParser.kt:94-128）
  → VisualDrawOpsPainter.draw(document, Canvas(bitmap), pxPerDu, labelContent)   # :72-95
  → Bitmap.compress(PNG)                                  # 同仓既有用例风格（BackupVisualReferenceInstrumentedTest.kt:1355）
  → PNG + manifest.json → adb pull → Python judge runner
```

`VisualDrawOpsParser`/`VisualDrawOpsPainter`/`VisualLabelContent` 都是 `internal`（`VisualDrawOps.kt:14-18` 注释、`VisualDrawOpsPainter.kt:44`），**所以校准测例必须落在 `core/data/src/androidTest/.../visual/`**（同模块可达），不能放 app 模块（app 需走 `VisualChainTestSeams`，而该缝只出 PDF，不出 PNG）。

### 6.2 校准集（6 个 case / 4 个 C 相关 kind，正确/注入各一，全部类别型）

| case | kind | 正确图 | 注入（判官必须判"错"） | 判官问题 |
|---|---|---|---|---|
| `cal-direction-down` | direction | 箭头竖直向下 | 箭头竖直向上 | "箭头指向？上/下/无法判断" |
| `cal-direction-incline` | direction | 沿斜面向上 | 沿斜面向下 | 四选一 |
| `cal-order-abc` | ordering | 卡片 A→B→C（自上而下） | C→B→A | "自上而下依次读出" |
| `cal-absent-clean` | label_absent | 只有印刷标签 | 叠加红色对号 + 波浪线 | "是否有红笔勾/对号/波浪线？逐项" |
| `cal-presence-text` | label_absent | 无 "x=5" | 叠加 "x=5" 文本 | "图中是否出现 x=5？" |
| `cal-count-3` | count | 3 张卡片 | 5 张卡片 | "数出卡片数量（整数）" |

> 6 个 case × 2 变体 × 2 次（正序/反序）= 24 次调用；远低于 `budget.json` 的 864 生成派发（判官调用单列为**新预算行**，不占生成派发，见 R5 §4.6）。

### 6.3 最小 Kotlin 骨架（`core/data/src/androidTest/.../visual/MvaCalibrationRenderInstrumentedTest.kt`）

```kotlin
@RunWith(AndroidJUnit4::class)
class MvaCalibrationRenderInstrumentedTest {
    @Test fun renderCalibrationSet() {
        val outDir = InstrumentationRegistry.getInstrumentation()
            .targetContext.getExternalFilesDir("mva-calibration")!!.apply { mkdirs() }
        val manifest = JSONArray()
        for (case in calibrationCases()) {                    // 手写 DrawOps + 期望答案
            val doc = (VisualDrawOpsParser.parse(case.drawOps, case.contentIds)
                as VisualDrawOpsValidation.Valid).document
            val bitmap = Bitmap.createBitmap(
                ceil(case.viewportWidthDu * PX_PER_DU).toInt(),
                ceil(case.viewportHeightDu * PX_PER_DU).toInt(),
                Bitmap.Config.ARGB_8888,
            )
            VisualDrawOpsPainter().draw(doc, Canvas(bitmap), PX_PER_DU) { contentId ->
                case.labels[contentId]?.let { VisualLabelContent.Text(
                    payload = it, sizeSp = 14f, fontScale = 1f,
                    heightDu = 14f, lines = listOf(VisualLabelLine(0, it.length))) }
            }
            val png = File(outDir, "${case.id}.png")
            png.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            manifest.put(JSONObject()
                .put("id", case.id).put("kind", case.kind)
                .put("variant", case.variant).put("expected_answer", case.expectedAnswer)
                .put("png", png.name).put("png_sha256", sha256(png.readBytes())))
        }
        File(outDir, "manifest.json").writeText(manifest.toString())
    }
}
```

要点：`pxPerDu = 2f`（≥2 保证细线不丢）；viewport 与盲测一致（360×240，`JsPort` 默认 `:406`）；每张 PNG 自带 sha，manifest 与图片一一绑定。

### 6.4 最小 Python runner（`tools/figure_eval/judge_runner.py --mode calibration`）

```
1) 读 manifest.json；校验每张 PNG 的 sha256。
2) 对每张图按 kind 生成问题（与盲测**同一模板**，§5.3）。
3) 调 provider（凭据只从环境变量/既有 provider 配置读取；**任何日志不打印 key**）。
4) 解析 JSON 答案；校验枚举合法（非法答案 = 未决，不猜）。
5) 逐 kind 算 false_accept / false_reject / order_inconsistency / unreadable_rate。
6) 写 calibration.json：{"model": ..., "prompt_sha256": ..., "by_kind": {...}, "admissible": {...}}
7) 任一 kind false_accept > 0 ⇒ admissible=false。
```

**先红后绿**：先跑两个 stub 判官——`AlwaysAccept`（必须被校准判为 `false_accept=1`、inadmissible）与 `AlwaysReject`（`false_reject=1`）——再跑真判官。校准 runner 的测试（§7.2）用 stub，不需要网络。

### 6.5 与盲测的接线

- 盲测的 C 事实只在 `admissible[kind]=true` 时进判定；否则 `JUDGE_UNAVAILABLE`。
- 盲测与校准用**同一** `prompt_sha256`；改提示词即重校准（R5 §4.4 第 5 条）。
- 判官调用耗时**不计入** p95（p95 是生成侧首张合格图，设计 :95）。

---

## 7. 落地计划（Python / Kotlin，按先红后绿）

### 7.1 新增/修改文件

| 文件 | 动作 | 消灭的具体失败 |
|---|---|---|
| `tools/figure_eval/fact_extract.py` | 新增：回放（仿射栈/clip 掩码）+ A1/A2 解码器注册表（key→decoder，未知 key 报错）+ 角色表读取（SceneV1） | JS 臂 `final_accepted` 永假、无事实提取器（`README.md:82-84`） |
| `tools/figure_eval/test_fact_extract.py` | 新增：每个 key 一组 good/bad；"改预期值不改事实"；"漏回放栈必须红"；未知 key 报错 | 提取器与预期值耦合、漏栈、静默兜底 |
| `tools/figure_eval/judge_runner.py` | 新增：匿名化、双序、JSON 答案解析、calibration/blind 两模式、sha 绑定 | 判官被问结论、位置偏差、无校准 |
| `tools/figure_eval/test_judge_runner.py` | 新增：stub 判官三态（always-accept/reject/order-biased）、提示词禁字段扫描 | 宽松/位置偏差无法度量 |
| `tools/figure_eval/mva_gate.py` | 改：`sys.path` 修（§7.4）；`label_absent → A\|C`；`decode` 状态 + `coverage/partial` | "计划命令跑不起来"；partial 被读成质量；类名事实被文本判过 |
| `tools/figure_eval/test_mva_gate.py` | 改：通道 totals 期望（116→102 / 40→54）；新增 partial/undecodable 用例 | 规则变更无测试 |
| `core/data/src/androidTest/.../visual/MvaJsArmAdapterInstrumentedTest.kt` | 新增（G4 T25/T26 的落点）：记录型 measurer + 引擎执行 + PNG + 四件产物/题 | 设备轨没有"程序→图→证据"的记录面 |
| `core/data/src/androidTest/.../visual/MvaCalibrationRenderInstrumentedTest.kt` | 新增（§6.3） | C 通道无校准件 |

**不改**：`figure_eval_gate.py` 的 `JsPort`——它对每题跑同一个探针程序（`program_source` 默认 `RENDER_SCENE_PROGRAM_JS`，`:404`），且 `_map_engine_result` 只留 `scene_sha256/scene_bytes/content_ids`（`:596-600`），**不回传 sceneJson/drawCommandsJson/度量批**，所以它不能当事实提取的输入；设备适配器必须自己记录这四件。产品主代码、任何 schema、任何 GATES 数值、120/24 集同样不改。

### 7.2 测试映射（验收条件 → 测试）

| 验收 | 测试（先红后绿） |
|---|---|
| 提取器不读预期值 | 同一 DrawOps 配两份不同 `fact_expectations`，事实输出逐字节相同 |
| 每个可判 key 有真值 | 每 key 至少 1 组 good/bad；bad 必须 mismatch |
| 漏回放栈必红 | `flow-order-correct` 关栈 → `step_order` mismatch（本轮实证） |
| 旋转不变 | 90° 旋转的 1:1 bar 仍判 1.0（本轮实证；缩放未测） |
| 未知 key 不兜底 | 新 key → `ValueError` |
| 提示词不泄题 | 字符串扫描 `task_id`/组名/图型/预期值，命中即失败 |
| 双序不一致 = 未决 | stub 判官正序答 A、反序答 B → `unresolved_disagreement` +1 |
| 校准准入 | `AlwaysAccept` stub → `false_accept=1` → `admissible=false` |
| partial 不可读成 PASS | `coverage=106/120` → `evaluable=false`、`met=false` |
| `label_absent` 路由 | `fact_channel({"kind":"label_absent",...}) == "A\|C"` |

### 7.3 执行顺序（避免返工）

1. `fact_extract.py` + 测试（用本文件的探针产物当黄金输入）→ 先红后绿。
2. `mva_gate.py` 的 decode/coverage + 路由修正 + 测试。
3. 设备适配器（T25/T26）产出 1 题真样本 → 过提取器 → 1 条 gate record。
4. 校准件（§6）→ 6 个 case × 2 变体 PNG → 判官 runner → `calibration.json`。
5. 全量 120（作者链 + 提取 + C）→ 读数；**acceptance 一次性纪律不适用于 MVA**（MVA 是盲测集，允许在 dev 24 上定型提示词；见 §8 风险 4）。

### 7.4 `mva_gate.py` 的导入修（最小 2 行）

现状（本轮实测）：

```text
$ python -I tools/figure_eval/mva_gate.py
Traceback (most recent call last):
  File "D:\smart mistake book\tools\figure_eval\mva_gate.py", line 42, in <module>
    import figure_eval_gate as fem
ModuleNotFoundError: No module named 'figure_eval_gate'
```

原因：`-I` 隐含 `-P`（不把脚本目录加入 `sys.path`）。最小修（在 `import figure_eval_gate` 之前）：

```python
import sys
sys.path.insert(0, str(Path(__file__).resolve().parent))
```

修完后计划 §验证命令的 `python -I tools/figure_eval/mva_gate.py` 才成立（本轮用等价的 `sys.path.insert` 包装跑通，见 §9）。

---

## 8. 风险、未验证与边界（如实）

1. **A2 的角色绑定尚未实现**，且 DrawOps 不保留节点 id（§2.2）。本文件推荐"SceneV1 角色 + DrawOps 事实"双源，但**scene→ops 对齐**没有实测过；它的正确性必须用一组"每节点 op 数/stat 复算"测试钉住，否则角色可能绑到错的元素上（→ 假接受）。这是 G4 最大的技术风险，工作量估计高于提取器本体。
2. **C 通道一行未跑**：无图像**生成** provider（基线 §4：只有 1 组文本/对话 provider 键）；判官要用的 chat provider 视觉能力**本轮未验证**、也无真机渲染（本会话零 Gradle/零设备）。§6 是设计 + 骨架，**不是已实现**。判官模型是否支持图像：仓内唯一证据是 `OpenAiSseTransportTest.kt:140` 的注释（一次实测调用 `deepseek/deepseek-v4.1-flash`（image + JSON mode）），**本轮未调用、未验证**；运行时必须记录真实 model id，若拒图则 C = `JUDGE_UNAVAILABLE`。
3. **`label_absent` 的类名语义没有权威定义**：夹具只给三个类名，没给"什么算对号"的判据。校准件必须自己定义视觉类（红/蓝笔迹、勾形、波浪形），并把定义写进提示词与 `calibration.json`；不同定义会得到不同 JLI。这条必须在报告里作为"评审来源=模型标注"的一部分披露。
4. **盲测纪律与 MVA 的关系**：120 集 `policy` 写"不得用于调 prompt"（`tasks_blind_120.json#policy`）。本协议把"判官提示词/通道规则"的定型放在 **dev 24 + 手写校准集** 上；若有人用 120 的结果改提示词，该集立即降级为回归集（须换未见集）。G3 的 AUD-08 "acceptance 只跑一次"是切分门的纪律，**不自动适用**于 MVA，但 MVA 自己的一次性纪律要写进记录。
5. **`verified_with_extras` 仍是宽松点**：`label_set` 的 subset 语义允许模型多印标签（`:656`）。本文件不改语义（改则改门），只要求报告计数。
6. **"印刷值"类 numeric 的边界**（§3.1）：`axis_min/max`、`converted_ms`、`discriminant`、`real_distance_cm` 只能证明"印对了"，不能证明"画对了"。任何把它们写成"图示正确"的结论都是过度声明。
7. **统计**：120 题的点估计门不能当下界门（R5 §7.1：114/120 的 Wilson 下界 0.8952 < 0.95；20/20 的下界 0.8389 < 0.90）。本文件不改这条解释纪律。
8. **本轮零门运行**：没有跑 `figure_eval_gate.py`（会重写 `reports/`）、没有跑 `check_split_gate.py`、没有跑任何设备/Gradle。§9 列出的命令是全部实跑项。

---

## 9. 本轮实跑记录（可复跑）

```bash
# 0) 开工前后热区（证明未抢占他线）
git status --porcelain -- tools/figure_eval
#     M tools/figure_eval/figure_eval_gate.py
#    ?? tools/figure_eval/judge_channel_map.json
#    ?? tools/figure_eval/mva_gate.py
#    ?? tools/figure_eval/test_mva_gate.py        （4 行，前后一致）

# 1) 既有机制测试（不是质量评测）
python -m pytest tools/figure_eval/test_mva_gate.py tools/figure_eval/test_figure_eval_gate.py -q
#    46 passed（本轮两次：0.21 s / 0.15 s）

# 2) 计划点名的命令（失败 → §7.4）
python -I tools/figure_eval/mva_gate.py
#    ModuleNotFoundError: No module named 'figure_eval_gate'   （exit 1）

# 3) 读数器预检（带 sys.path 包装）
python -I -X utf8 -c "import sys; sys.path.insert(0,'tools/figure_eval'); import mva_gate as mg; ..."
#   fixture_ok=true；blind 120 / dev 24 / overlap [] / groups_complete true
#   channel_totals: A 94, A|B 116, A|C 40, A|B|C 38（crit: A 87, A|B 82, A|C 40）
#   tasks_with_critical_a_only 57 / tasks_with_all_critical_a_only 27
#   两臂 evaluable=false / samples=0

# 4) A 通道端到端探针（生产同源编译器 → DrawOps → 提取 → 既有判定器）
node docs/research/evidence/2026-11-10-batch-51/mva-facts-probe/probe_compile.mjs
#   8 个场景编译成功（含旋转组、concat 平移组）
python -I -X utf8 docs/research/evidence/2026-11-10-batch-51/mva-facts-probe/probe_extract.py
#   EXIT=0；all_matches_expectation=true；10/10 (case,task) 符合预期
#   per_fact：几何对/标签错 → numeric true / label false；残留 x=5 → removed_handwriting true（假接受）

# 5) 可判性分类（51 个 key 全覆盖，未知 key 报错）
python -I -X utf8 docs/research/evidence/2026-11-10-batch-51/mva-facts-probe/probe_decodability.py
#   A1 204（crit 129）/ A2 70（crit 66）/ D3 14（crit 14）
#   tasks_all_A1 64 / tasks_all_A 106 / tasks_with_A2 42 / tasks_with_D3 14
```

证据目录 `docs/research/evidence/2026-11-10-batch-51/mva-facts-probe/`：

| 文件 | sha256（本轮实测，`sha256sum`） |
|---|---|
| `probe_compile.mjs` | `33dd3092bd3fe38699ed9d52b25f4aebac098af23e05de420cbbbf55798c9c81` |
| `probe_extract.py` | `c68b1333e67cfd394efcc1c9bd0c928d2d32286d96f4c46e8b47a82da262cf26` |
| `probe_decodability.py` | `3ddc94777fbe9fd254860558a25f3c7002d0b81d4b7b6f0b0ebbef0d28b19eac` |
| `probe_outputs.json` | `b441f962e5f5e607378079cec8d8ff550959412c33059c92d1095c0bbf830f63` |
| `probe_result.json` | `f230168d679417eb1be728fb5197627cf1e829a2aa89f66c2a6840291f1ccb2e` |
| `fact_decodability.json` | `a339e304dc6ae9a0800be336e1377d80fa158f5a3c311f793fa9006a7e930852` |
| `probe_stdout.txt` | `11bffd42b4038daa433cc1b07c71f323e6bdca827f744c5c1ca6a8e34a33b50a` |

**未跑（不得读成通过）**：真实 Provider 调用、VLM 判官、设备渲染、PNG 校准件、`adb`、任何 Gradle 任务、`figure_eval_gate.py` 本体、`check_split_gate.py`、MVA/AUD-08/3D/3E 任何门。

---

## 10. 引用

- 设计 §6：`docs/superpowers/specs/2026-10-19-product-readiness-design.md:81-97`（质量门原文；**本轮未直读**，经 `figure_eval_gate.py:85-141` 的 `source.quote` 转引，阈值一字未改）
- PRD-09：`docs/superpowers/plans/2026-10-19-product-readiness-followup-plan.md:126-140`（独立事实核验/证据限制；同上转引）
- 生图契约：`docs/current/visual-generation-contract.md:54`（四步事务）、`:111`（RenderScene 定义）、`:130`（快照可序列化）、`:180`（快照行）、`:199-201`（兼容/导出）
- 前序研究：`docs/research/2026-11-09-mva-judge.md`（R5：通道/盲法/taxonomy/统计纪律）；本文件在其上只做三件增量：**可判性分类与计数、A2 载体缺口、C 校准件最小实现**
- 代码：`core/data/.../visual/{VisualDrawOps.kt,VisualDrawOpsParser.kt,VisualDrawOpsPainter.kt,VisualChainTestSeams.kt}`、`core/domain/.../VisualExecutionEngine.kt`、`core/data/src/main/resources/visual/visual-compiler.js`、`app/.../visualjs/{VisualJsProtocol.kt,VisualContentMeasuring.kt}`、`tools/figure_eval/{figure_eval_gate.py,mva_gate.py}`
