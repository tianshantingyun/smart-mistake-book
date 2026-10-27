# SceneV1 编译器 + SDK 设计（批次 27 T1 驱动研究 R1）

日期：2026-10-27。任务来源：批次 27 计划 §1 T1（`.zcode/plans/plan-2026-10-27-batch-27-4e2-mva-static.md`："SceneV1 编译器+SDK 落码（core:data visual 侧，JS sandbox 内 `compile(sceneJson)→drawCommands` 纯计算，契约 §3.2 纯计算边界）"）。性质：**研究设计，未落任何产品代码**；本报告未跑 Gradle、未做 git 写操作、未触碰任何热区文件；新增文件仅本报告。

一手输入（本会话逐字读过）：契约 `docs/current/visual-generation-contract.md` §3.2/§4/§5/§6/§8；引擎 `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/QuickJsVisualExecutionEngine.kt` 全文；端口 `core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/VisualExecutionEngine.kt` 全文；wire 协议 `app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsProtocol.kt`（结果信封段）；桌面实验 `.jez/artifacts/flexible-js-drawing-2026-10-17/`（experiment.mjs + output/results.json）与其研究简报、Android 探针 `.jez/artifacts/android-visual-runtime-probe-2026-10-17/research-findings.md`、`2026-10-25-js-engine-selection.md`、`2026-10-27-mva-reeval.md`。

## 0. 结论

1. **没有一个现成开源库可以在裸 QuickJS 里直接承担"严格 JSON 场景 → 有界绘制指令"**：候选库（Vega/zrender/lottie/canvg/Excalidraw/leafer 等）全部依赖 DOM/Canvas 宿主，或体积/信任模型不符（§1.3）。采纳形态 = **自研小编译器 + SDK（JS 资产，估 15–25 KiB 源码）+ 算法级移植**：弧→三次曲线公式与 Q→C 提升为标准数学（本会话数值复核），指令词表以 zrender PathProxy 的 M/L/C/Q/Z 数值流为独立旁证，显示列表架构借鉴 vello/typst 的扁平编码流，宿主 Canvas 映射点参考 lottie-android（JVM/Android 原生）。
2. **编译器放独立第三上下文**（prepare/render 之外）：把"编译器完整性"从防御性（值捕获/防重绑）升级为结构性（上下文里根本没有用户代码）；输入是场景 JSON 文本，正好用严格解析器在文本边界落实契约 §4 的"重复 JSON 键/ID、原型污染键"拒绝；输出仍受宿主原生严格校验（契约 §3.2 第 4 步"原生严格校验"不动）。
3. **DrawOps V1 = save/concat/restore + clip/path/label 六种 op**：绘制路径带 `concat`（保住契约"stroke 随变换缩放"的非均匀缩放语义），裁剪/命中区带组合后坐标（点内判定无需逆矩阵），标签位置由编译器一次算定（契约 §5"compiler 一次算出最终位置"）。
4. **预算可行（桌面证据支持，设备实测未做）**：桌面 QuickJS(Wasm) 单帧全管线 7.5–35 ms（13/13 检查、8 MiB 堆），编译新增工作量同级；上限场景（256 节点/4096 路径命令）在 100 ms 协作期限内的设备实测列为 T1 证据项，超限即 `PHASE_TIMEOUT` fail-closed，无挂死形态。

## 1. 参考调研（GitHub）

### 1.1 检索与核验通道

- 主通道：`gh` CLI（已认证）。元数据命令（2026-10-27 实跑）：`gh repo view OWNER/REPO --json licenseInfo,stargazerCount,pushedAt,isArchived,latestRelease`；源码核对 `gh api repos/.../contents/<path>`（Base64 解出后 grep）。
- 两处仓库名猜错（`rough-studio/roughjs`、`mattdesl/points-on-curve`）经 `gh search repos` 纠正为 `pshihn/rough`；`points-on-curve`（曲线拟合）与本设计无关（采样曲线直接取点，不做拟合），弃用。
- 辅助计算：`node -e` 数值复核 κ 常数与 Q→C 精度（命令与输出记录于 §5.3）。
- WebSearch/WebFetch 本轮未动用（gh 单通道全部命中，无需换道）。

### 1.2 license / 维护度 / Kotlin 互操作评分表

stars/推送/发布均为 2026-10-27 当日 `gh` 实测。**license 以仓库 LICENSE 文件为最终权威**；本表仅 zrender 已读文件内容复核（"BSD 3-Clause License / Copyright (c) 2017, Baidu Inc."），其余为 GitHub API `licenseInfo`（如未来真要引入依赖，须按 `2026-10-25-js-engine-selection.md` §8 的方法读 LICENSE 文件复核后方可引用）。

| 仓库 | license | ★ | 最近推送 | 最新发布 | 角色（对本设计的用途） | Kotlin 互操作 |
|---|---|---|---|---|---|---|
| [vega/vega](https://github.com/vega/vega) | BSD-3-Clause | 12,027 | 2026-10-20 | v6.4.0 | "声明式 JSON → scenegraph → canvas 渲染器"两段式架构参考（其 scenegraph items → CanvasRenderer 遍历发射 ctx 调用） | 无（浏览器 DOM） |
| [ecomfe/zrender](https://github.com/ecomfe/zrender) | BSD-3-Clause（LICENSE 文件已复核） | 6,312 | 2026-10-16 | 6.1.0 | **指令词表旁证**：PathProxy 为扁平数值命令流（`src/core/PathProxy.ts:225-339`，CMD.M/L/C/Q/Z）；`src/canvas/dashStyle.ts` 印证 painter 层处理 dash | 无（DOM Canvas） |
| [airbnb/lottie-web](https://github.com/airbnb/lottie-web) | MIT | 32,285 | 2025-09-01（**滞后约 13 个月，维护度存疑**） | 无 tag | JSON 动画 → canvas/SVG 双后端的"一份描述两种 painter"参考 | 无 |
| [airbnb/lottie-android](https://github.com/airbnb/lottie-android) | Apache-2.0 | 35,757 | 2026-02-15 | v6.7.1 | **§4 Canvas 映射点参考**（JVM/Android 原生：路径/描边/dash/文本→Canvas） | 原生 JVM/Android |
| [canvg/canvg](https://github.com/canvg/canvg) | MIT | 3,838 | 2026-10-26 | v4.0.3 | SVG→canvas 指令 lowering 参考（架构性：解析→中间形态→ctx 指令） | 无 |
| [excalidraw/excalidraw](https://github.com/excalidraw/excalidraw) | MIT | 134,654 | 2026-10-27 | v0.18.1 | 受限扁平元素集（线/箭头/矩形/椭圆/文本）→canvas 渲染的最小词表参考 | 无（React 应用） |
| [antvis/g](https://github.com/antvis/g) | **GitHub 未检出**（仓库无根级 LICENSE 文件；包级 MIT 说法未核到一手文件）— **license 歧义，仅作架构阅读** | 1,217 | 2026-10-16 | @antv/g-lite@2.7.2 | 场景图→多后端渲染参考候选 | 无 |
| [d3/d3-shape](https://github.com/d3/d3-shape) | ISC | 2,532 | 2023-10-06（d3 模块化设计下属稳定而非弃维护，**单一来源判断，待验证**） | v3.2.0 | arc/line/curve 生成器算法（纯函数）；契约 §2 已裁"D3 的纯算法可按需移植" | 纯 JS，可入 QuickJS |
| [d3/d3-path](https://github.com/d3/d3-path) | ISC | 197 | 2023-10-06 | v3.1.0 | Canvas path API ↔ M/L/C/Q/Z 路径数据的词表对照 | 同上 |
| [linebender/vello](https://github.com/linebender/vello) | Apache-2.0 | 4,448 | 2026-10-27 | v0.11.0 | **扁平编码指令流**架构参考（scene = 编码后的 op 流，painter 线性回放） | 无（Rust，需 FFI） |
| [linebender/kurbo](https://github.com/linebender/kurbo) | Apache-2.0 | 1,023 | 2026-10-08 | v0.13.1 | **弧→三次曲线公式一手复核**（`kurbo/src/arc.rs:77-86`：`arm_len=(4/3)·\|tan(θ/4)\|`，且每整椭圆 subdivisions≥4，即每段≤90°） | 无（Rust） |
| [typst/typst](https://github.com/typst/typst) | Apache-2.0 | 56,854 | 2026-10-27 | v0.15.1 | Content→Layout→Frame（Group/Text/Shape/Link 扁平显示列表）→painter 架构参考（in-repo 另有 `docs/research/typst-android-feasibility.md`，本轮未读其内容） | 无（Rust） |
| [penrose/penrose](https://github.com/penrose/penrose) | MIT | 8,000 | 2026-10-04 | v3.3.1 | "程序→编译器→场景图→渲染"管线参考；R1 已裁不进 V1 运行链（`2026-10-18-flexible-js-drawing-research.md`） | 无 |
| [leaferjs/leafer-ui](https://github.com/leaferjs/leafer-ui) | MIT | 4,466 | 2026-10-27 | v2.3.2 | JSON 配置→canvas 的参考候选（中文生态；依赖面未审） | 无 |
| [colinmeinke/svg-arc-to-cubic-bezier](https://github.com/colinmeinke/svg-arc-to-cubic-bezier) | GitHub 报 "Other" | 76 | 2022-12-09（停滞近 4 年） | v3.2.0 | **弃用**：弧→C 数学已由 kurbo 源码复核 + 本会话数值复核钉住，不引入低维护 npm 单点 | — |

### 1.3 采纳结论：借鉴什么、移植什么、不引什么

- **不整库引入**（全部否决为依赖）：候选库的绘制面均假定 DOM/Canvas 宿主——契约 §2 原裁（"p5.js、JSXGraph、Konva、Penrose、Vega 的浏览器/DOM/Canvas/Wasm 依赖不直接塞入 QuickJS"）对上表新增候选同样成立；且没有任何一家提供契约要求的"有界 fail-closed 编译 + 预算拒绝"面。
- **算法级移植（数学事实，不复制代码体）**：弧→三次曲线用 κ=4/3·tan(θ/4) 分段公式（kurbo `arc.rs:86` 源码旁证；本会话数值复核 90° 段最大径向误差 2.7253e-4·r，§5.3）；Q→C 用标准次数提升（本会话数值复核为精确变换，误差 1.8e-15 即 float64 舍入级，§5.3）。这两个公式是数学事实，无 license 义务；**若未来整段移植 d3-shape 函数体（ISC），须在资产头保留版权与许可声明**——本设计不移植函数体，只采用公式与行为约定。
- **架构级借鉴（不受 license 约束的设计事实）**：zrender PathProxy 证明 M/L/C/Q/Z 数值命令流是工业最小词表；vello/typst 证明"编译一次 → 扁平指令流 → 多 painter 线性回放"是成熟形态；lottie-android 证明同一显示列表在 Canvas 上以 save/concat/clip/draw 回放可行。

## 2. 管线定位与命名对齐

**本报告按任务条目二 `compile(sceneJson)→绘制指令序列` 设计**：输入 = 程序 `visual.render(input, metrics)` 产出、引擎已取回的 **SceneV1 JSON 文本**；输出 = **DrawOps V1**——它就是契约 §5"RenderScene 是校验后的不可变显示列表"的序列化内容。任务书序言写"输入 RenderScene JSON"与计划 T1 写 `compile(sceneJson)` 存在措辞差：若主会话原意是"SceneV1 与 RenderScene 两段式（宿主先产出 RenderScene、编译器再 lowering）"，则 §3.2 的 DrawOps 词表与错误面原样可用，只把 strict-parse 从编译器挪到宿主——已在 §6.1 登记，**不擅自改裁定，留主会话对齐契约文本**。

事务管线（批次 27 目标态，改动点加粗；既有行为引现行源码）：

```
prepare 上下文（既有）：bootstrap + 用户源码 → visual.prepare(input) → JSON → 关闭
        ↓                                  QuickJsVisualExecutionEngine.kt:63-66
原生度量（既有）：measurer.measure(batch)           VisualExecutionEngine.kt:149-151
render 上下文（既有）：bootstrap + 用户源码 → visual.render(input, metrics) → JSON.stringify
        ↓                                  QuickJsVisualExecutionEngine.kt:68-69,452-456
**compile 上下文（新增）：bootstrap + 编译器资产（无用户代码）
   → __visualCompile(sceneJson, metricsJson, expect) → DrawOps JSON 文本 → 关闭**
        ↓
原生严格校验（既有面扩展）：字节预算 + contentId 冻结（:648-686）+ **DrawOps 词表/预算/有限性复验**
        ↓
原子提交：VisualExecutionResult.Rendered（**+drawCommandsJson/drawSchemaVersion**，sceneJson 保留）
```

每事务 3 个上下文、每上下文各受一次 100 ms 协作期限与事务级 1500 ms 硬期限约束——上下文随建随弃、全局不跨调用，是引擎既有结构性纪律（"每事务新上下文"，`QuickJsVisualExecutionEngine.kt:72-74`），compile 只是复用它。

## 3. JS 侧结构设计

### 3.1 compile API（编译器资产，唯一暴露面）

```js
// 上下文内只求值 bootstrap（既有）+ visual-compiler.js；无用户源码、无 SDK 原语。
// 返回 DrawOps JSON 字符串；任何违规抛 SceneCompileError（code + 有界 message）。
__visualCompile(sceneJsonText, metricsJsonText, expect)
//   sceneJsonText   程序 render 的输出文本（引擎已按 256 KiB 预算检查过，:655-662）
//   metricsJsonText 本事务冻结的度量表（contentId/width/height/baseline/fontSizeSp/digest）
//   expect          { sceneRevision: int }  —— 编译器核对场景回显（契约 §8"JS 输出只可回显"）
```

结构决定与它各自消灭的失败：

| 决定 | 消灭的具体失败 |
|---|---|
| 独立第三上下文（不放 render 同上下文） | 用户代码在 render 返回前重绑 `globalThis` 上任何编译器入口（现有 bootstrap 对 `Math.random` 的值捕获 `:847-852` 只能防一个名字）；独立上下文里编译器**无被篡改面** |
| 输入为 JSON 文本 + 自带严格解析器 | `JSON.parse` 对重复键静默取后者、对 `__proto__` 键照单全收——契约 §4 明令拒绝"重复 JSON 键/ID、原型污染键"，文本边界的严格解析器是唯一能看见重复键的位置 |
| 编译器输出仍受原生校验 | "编译器是可信 SDK"与"它跑在被敌意输入喂过的进程里"是两回事——输入场景来自不可信程序，输出必须原生复验（契约 §3.2 第 4 步"原生严格校验"）；消灭"编译器缺陷/被污染原语把越界值带出沙箱" |
| compile 完全无随机/无时钟 | 确定性可复算：同 (sceneJson, metricsJson, expect) → 字节相同输出（桌面实验"同源码/输入重复运行→规范化场景一致"检查，results.json tests） |

### 3.2 DrawOps V1 词表（输出协议草案）

```jsonc
{
  "drawSchemaVersion": 1,
  "viewBox": {"x":0,"y":0,"w":360,"h":240},     // V1 MVA 要求等于 viewport，否则 SCENE_FIELD 拒绝
  "background": null,                            // 或 "#RRGGBBAA"
  "ops": [
    {"op":"save"},
    {"op":"concat","m":[a,b,c,d,e,f]},           // 组合后矩阵（parent*local，契约 §5）
    {"op":"clip","path":[["M",x,y],["L",x,y],["C",x1,y1,x2,y2,x,y],["Z"]],"rule":"nonzero"},
                                                 // clip 路径为组合后坐标；只相交（契约 §5）
    {"op":"path","path":[["M",x,y],["C",...],["Z"]],
     "fill":"#RRGGBBAA",                          // fill/stroke 至少一个非 null
     "stroke":{"width":2,"cap":"round","join":"miter","miterLimit":4,"dash":null},
     "alpha":1},                                  // group 链预乘后的最终 alpha
    {"op":"label","contentId":"c7","x":120.5,"y":64,"rotationDeg":0,
     "hAlign":"center","vAlign":"middle","color":"#26354a","alpha":1},
    {"op":"restore"}
  ],
  "hitRegions": [{"id":"zone-1","path":[["M",..]],"rule":"nonzero"}],  // 组合后坐标，≤128
  "stats": {"nodes":0,"pathCommands":0,"labels":0,"ops":0}
}
```

不变量（编译器生成、宿主复验同一套）：

1. 路径命令仅 `["M",x,y]` / `["L",x,y]` / `["C",x1,y1,x2,y2,x,y]` / `["Z"]`——**Q 已在编译器提升为 C**（精确变换，§5.3），**无 A 弧命令**（弧在 SDK 侧已拆为 ≤90° 的 C 段）；zrender PathProxy 的同款词表是独立旁证（§1.2）。
2. 绘制路径带 `concat` 矩阵而非烘焙坐标——非均匀缩放下"stroke 随变换缩放"（契约 §5）只能由 painter 在变换空间里描边保真；clip/hit 为填充语义，带组合后坐标使点内判定**无需逆矩阵**（契约 §6"命中测试使用 compiler 的同一变换、clip 和逆变换"在此落为"命中路径已被同一组合变换烘焙"）。
3. `save/restore` 严格配平；alpha 由 group 链预乘（契约 §5"group alpha 乘到子节点"），painter 每绘制恰一次 setAlpha；srcOver 之外无混合模式（契约 §5"只支持 srcOver"）。
4. 一切数字有限且组合后 |v| ≤ 100000（契约 §4"坐标绝对值 100000；矩阵及变换后边界也须有限且合规"）；矩阵有限且 |det| ≥ 1e-12（不可逆矩阵破坏命中逆变换语义，V1 直接拒绝——严格性登记，非契约原文）。
5. 预算计数进 `stats`：节点 ≤256、树深 ≤8、路径命令（展开后）≤4096、控件 ≤16、命中区 ≤128、输出文本 ≤256 KiB——全部对齐契约 §4 表与 `VisualExecutionBudget`（`VisualExecutionEngine.kt:106-139`）。
6. `controls[]`、accessibility、fallbackMarkdown 不进 DrawOps：控件由原生层从提交的场景渲染（契约 §5"原生控件与可选表单另列"），朗读顺序随场景 DTO 保存（§8 快照）。
7. label 位置 = 编译器把 anchor 经组合矩阵投影后，按 offset/对齐/rotation 与度量宽高 baseline 一次算定（契约 §5"compiler 一次算出最终位置，不由 Compose/PDF 各自猜 baseline"；"标签投影后放置以免倒置"）。

### 3.3 SDK 绘制原语集（prepare/render 上下文，用户源码前求值）

契约 §3.2："SDK 在同一全局上下文提供纯函数图形帮助方法"；§5："圆、弧、箭头、轴由 SDK 生成基础路径，弧拆为每段不超过 90° 的三次曲线，误差算法随 SDK 版本固定"。命名 `visualSdk`，命名空间深冻结；每个原语对非有限输入 fail-closed 抛 `SceneSdkError`。

```js
visualSdk.circle(cx, cy, r)            // 4 段 C；κ = 0.552284749831（4/3·tan(π/8)，本会话复核）
visualSdk.arc(cx, cy, r, a0, a1)       // n = ceil(|sweep|/90°) 段，每段 κ=4/3·tan(θ/4)；
                                       // sweep 归一化到 (−2π, 2π]；段数公式随 SDK 版本固定
visualSdk.arrow(x1, y1, x2, y2, o?)    // 杆线 + 头两段；头长/半角固定默认（随 SDK 版本写死）
visualSdk.axis(o)                      // 轴线 + 刻度 + 端箭头；刻度数字仍由程序发 label
visualSdk.polyline(points)             // 采样曲线折线（桌面实验最常用原语，13/13 检查载体）
visualSdk.random()                     // 捕获宿主注入 PRNG（java.util.Random(seed)，引擎 :441-442；
                                       // 捕获方式同 __ktRandom :847-852，prepare/render 各自从 seed 重放）
```

V1 明确**不设** ellipse（group 矩阵缩放 circle 即得）/rect（M+3L+Z 共 5 条命令，程序内联即可）、不做主题色 token、不做避让（契约 §5"第一版无自动标签避让优化器"）、不做文本度量糖（协议本身只有 `{kind,payload}`）。每项缺席都是按"新增必须指认消灭的失败"的逆向裁剪：没有它们也没有已发生的失败。

### 3.4 错误面（fail-closed：一切违规 = 有界拒绝，无部分输出）

编译器/SDK 只有一种失败形态：抛错 → 引擎折 `VisualExecutionRejection.INVALID_SCENE`（程序侧、回喂模型诊断；`VisualExecutionEngine.kt:287`）→ 诊断 ≤ 512 字符（`FigureGeneration.kt:586` MAX_DIAGNOSTICS_CHARS）带节点路径/索引上下文。错误码枚举（词表即测试矩阵）：

| 码族 | 覆盖 |
|---|---|
| `SCENE_PARSE_*` | 非 JSON / 对象或数组层重复键 / `__proto__`·`constructor`·`prototype` 键 / 解析深度 >64（契约 §4"场景 JSON 解析深度≤64，在流式读取时检查"）/ 非有限数（`1e999` 等）/ 尾随内容 |
| `SCENE_FIELD_*` | 未知字段 / 缺必填 / 类型错 / schemaVersion≠1 / viewBox≠viewport / revision 不回显 |
| `SCENE_NODE_*` | 未知节点类型（只有 group/path/label）/ 节点 >256 / 树深 >8 / 重复 id |
| `SCENE_PATH_*` | 未知命令 / 非 M 起始 / 展开 >4096 / 坐标越界或非有限 |
| `SCENE_STYLE_*` | 非法颜色（非 `#RRGGBB(AA)`）/ 未知 cap/join / miterLimit<1 / dash 非法 / fill、stroke 双 null |
| `SCENE_REF_*` | label 的 contentId 不在本事务冻结度量集（引擎既有冻结检查 `:671-680` 的编译器侧前置） |
| `SCENE_MATRIX_*` | 矩阵非有限 / 不可逆 |
| `SCENE_BUDGET_*` | ops 输出 >256 KiB / 命中区 >128 |
| `SceneSdkError` | SDK 原语收到非有限/越界参数 |

严格解析器（~120 行 JS，IIFE 内捕获自身所需内建函数，杜绝污染面）产出 null-prototype 纯对象——原型污染在解析产物上结构性不可表达。未知字段拒绝即契约 §4"严格拒绝未知字段/节点/路径命令"；**未启用的能力在解析层拒绝，不是隐藏 UI**（契约 §3.2 能力门原文）。

### 3.5 与 bootstrap / 深冻结（计划 T3）的接口

- bootstrap 资产扩展：既有 `BOOTSTRAP_JS`（封 Date、捕获 PRNG、freeze Math，`QuickJsVisualExecutionEngine.kt:847-852`）不变；SDK 原语与编译器各为独立 JS 资产（core:data resources，UTF-8 随包），求值顺序 bootstrap → [SDK（仅 prepare/render 上下文）| 编译器（仅 compile 上下文）] → 用户源码（仅 prepare/render）。
- 深冻结（T3，§58）：FrameInput 与 metrics 图在 bootstrap 内递归 freeze（带环防护）；compile 上下文收到的 metricsJsonText 由宿主直接序列化冻结后的度量结果，编译器解析后同样深冻结再使用——input/metrics 在 prepare/render/compile 三个上下文里都不可被程序暗改（契约 §3.2"每次调用的 input 与 metrics 深冻结"）。
- 引擎终止语义、AAR/stdlib、队列、watchdog、AIDL、广告门：**零改动**（共享纪律"客户端/协议不动引擎终止语义"）。

## 4. 宿主侧执行：DrawOps → Android Canvas 映射点（painter 归 4E.2 后段，本批只到指令序列）

| DrawOp | Android Canvas 映射点 | 注 |
|---|---|---|
| `save`/`restore` | `Canvas.save()` / `restore()` | 配平由校验器保证，painter 不自愈 |
| `concat` | `Canvas.concat(Matrix)`；契约 `(a,b,c,d,e,f)`（x'=ax+cy+e）→ Android `Matrix.setValues` 列序为 `[MSCALE_X, MSKEW_X, MTRANS_X, MSKEW_Y, MSCALE_Y, MTRANS_Y]`，即 `[a,c,e,b,d,f]` | **skew 列序错位是此映射的头号隐性错误**（lottie-android 的矩阵桥接为参考实现） |
| `clip` | Path 重建 → `Canvas.clipPath(path, Region.Op.INTERSECT)` | 契约"clip 只相交"；API 23+ 安全 |
| `path` | Path：moveTo/lineTo/cubicTo/close；fillType = Path.FillType.NONZERO/EVEN_ODD；Paint：fill 先画（style=FILL）、stroke 后画（style=STROKE，width/cap/join/miterLimit 直接映射，`dash` → `DashPathEffect`）；alpha → `Paint.setAlpha(floor(alpha*255))`（取整规则随编译器版本固定，屏/PDF 同规则） | 一条路径带 fill+stroke 时画两遍（fill→stroke 叠序），与 Canvas/SVG 惯例一致 |
| `label` | 冻结文本/数学 artifact（度量阶段的 StaticLayout/MathBudget lowering）在已算定 (x,y,rotation) 绘制：save → rotate(anchor) → draw → restore | baseline 已在编译期解析；du→px 由根变换一次完成（契约 §5"根变换只做一次 du→px"） |
| `background` | `Canvas.drawColor` / Compose 容器背景 | — |
| `hitRegions` | 原生点内判定（同 fill rule）；4E.2 交互段消费 | 组合后坐标，无需逆矩阵 |
| 整表 | PDF：同一 op 开关打在 `PdfDocument` page.canvas（core:visual painter 共用，契约 §5"AndroidScenePainter 共用于 Compose nativeCanvas 和 PDF page.canvas"）；SVG writer 为 4E.2 附加交付 | 契约 §8："PDF/SVG 不重新执行 JS，不重新采样曲线或移动标签"——弧/曲线已在 SDK/编译期定形，painter 只回放 |

## 5. 预算可行性（16 MiB 堆 / 100 ms 协作期限）

### 5.1 内存

- 场景解析树 ≈ 文本的 3–5 倍：256 KiB 上限 → ≤1.3 MiB 瞬时；DrawOps 对象 ≤4096 命令 ≈ ≤1 MiB；输出串 ≤256 KiB。峰值 ≪ 16 MiB（`VisualExecutionBudget.MAX_HEAP_BYTES`，`VisualExecutionEngine.kt:117`）。
- 桌面证据：实验全程 8 MiB 客体堆完成 3 程序 33 场景 13/13 检查（results.json `guestHeapMiB: 8`）；Android 探针在 API 34/x86_64 上 8 MiB 堆上限触发干净 OOM、宿主无恙（`android-visual-runtime-probe-2026-10-17/research-findings.md:16`）。
- 编译上下文无用户源码（≤64 KiB 求值面不存在），堆压力比 render 上下文更小。

### 5.2 时间

- 桌面实测（quickjs-emscripten@0.32.0，Wasm，**慢于**原生 JNI）：11 帧全管线（新建上下文+bootstrap+源码求值+prepare/render+stringify+校验）wall 82/353/382 ms → **每帧 7.5/32/35 ms**（results.json `scenes[].wallMs`）。编译阶段无源码求值、工作量同级或更小；原生 QuickJS（Android 探针已证可跑）通常显著快于 Wasm。
- 失败形态有界：超 100 ms 即 `evaluationTimeoutMillis` 协作中断 → `PHASE_TIMEOUT`（`:437`、分类器类型优先），不会挂死；编译器/SDK 为纯同步代码、无 promise/job——上游 issue #240 的"job 自续队列绕过协作中断"形状**在编译阶段不可达**（同 `2026-10-25-js-engine-selection.md` §5 对该缺陷的爆炸半径分析）。
- 事务墙钟：3 个上下文 × ≤100 ms + 度量，仍被 1500 ms 服务硬期限覆盖；预算上限场景（256 节点/4096 命令）的设备实测 **UNVERIFIED**，列 T1/设备轨证据项（连同 KD-76 的 warm-up 实测口径，互不混用）。

### 5.3 本会话数值复核（可复跑命令）

```bash
node -e "
const k = 4/3*Math.tan(Math.PI/8);
let maxErr = 0, atT = 0;
for (let i = 0; i <= 2000000; i++) {
  const t = i/2000000; const mt = 1-t;
  const x = mt**3*1 + 3*mt*mt*t*1 + 3*mt*t*t*k + t**3*0;
  const y = mt**3*0 + 3*mt*mt*t*k + 3*mt*t*t*1 + t**3*1;
  const err = Math.abs(Math.hypot(x,y) - 1);
  if (err > maxErr) { maxErr = err; atT = t; }
}
console.log('kappa =', k.toFixed(12));
console.log('max radial error for 90deg unit arc =', maxErr.toExponential(6), 'at t =', atT.toFixed(4));
"
→ kappa = 0.552284749831
→ max radial error for 90deg unit arc = 2.725300e-4 at t = 0.2113

node -e "
const q=(t,p0,q1,p2)=>{const m=1-t;return m*m*p0+2*m*t*q1+t*t*p2;};
const c=(t,p0,c1,c2,p2)=>{const m=1-t;return m*m*m*p0+3*m*m*t*c1+3*m*t*t*c2+t**3*p2;};
const p0=2,q1=7,p2=3; const c1=(p0+2*q1)/3, c2=(p2+2*q1)/3;
let maxd=0; for(let i=0;i<=10000;i++){const t=i/10000; maxd=Math.max(maxd, Math.abs(q(t,p0,q1,p2)-c(t,p0,c1,c2,p2)));}
console.log('Q->C max deviation:', maxd);
"
→ Q->C max deviation: 1.7763568394002505e-15   # float64 舍入级，精确变换
```

弧误差登记：半径 r du 的 90° 段最大径向偏差 = 2.7253e-4·r du（r=200 → 0.05 du 不可见；r=2000 → 0.54 du 接近 1 dp）。契约已把该算法钉进 SDK 版本，此数为版本化文档值，不是缺陷。

## 6. 备选方案与已否决项

1. **Kotlin 原生编译器**（诚实备选）：native 单一信任点、省一个上下文与 JS 堆、输出即类型化 DTO；缺点是与 SDK 分居两语言（几何公式/词表双维护）、且 Kotlin 侧同样要自建严格 JSON 解析（kotlinx `parseToJsonElement` 对重复键同样静默取后）。计划 T1 已裁 JS sandbox 内纯计算——**沿裁定**，此备选与"两段式"改裁路径一并登记（§2），DrawOps 词表与错误面在任一裁法下原样可用。
2. **整库复用**（vega/zrender/lottie/canvg/excalidraw/leafer/antvis.g）：全部否决为依赖（§1.3）；antvis/g 另有 license 歧义。
3. **SVG d 字符串输入**（canvg 路线）：契约 §5 明拒（"path 定义为 M/L/Q/C/Z 数值段数组，不接收 SVG d 字符串"）。
4. **全烘焙坐标（无 concat op）**：丢非均匀缩放下的描边保真（§3.2 不变量 2），否决。
5. **compile 与 render 同上下文**：编译器暴露被篡改面（§3.1 表第 1 行），否决。
6. **冻结全部 JS 内建（SES 式 lockdown）**：会改变用户程序可见语义（原型扩展静默失效/抛错），V1 不做；SDK/编译器用值捕获 + null-prototype 产物达到同等防护目标，内建冻结列为 4E.3 加固候选。

## 7. 对 T1 落码与测试的建议（先红后绿）

1. **JVM 纯函数面**：DrawOps 原生校验器（词表/预算/有限性/配平/引用冻结）先写负向矩阵测试（红），再落实现——校验器是纯函数，与 `VisualJsFailureClassification` 同型可全 JVM 覆盖。
2. **编译器金样**：`tools/`（非 kb，落点允许）放 Node 夹具生成器：固定 (scene, metrics, expect) → 期望 DrawOps 字节；仪器化用例经真实引擎对同输入断言字节一致（确定性即回归）。夹具至少覆盖：纯几何、全节点类型、Q 提升、弧拆分（含 >90° sweep 的多段）、标签放置（对齐×rotation）、§3.4 每个错误码一例。
3. **引擎层**：编译失败 → INVALID_SCENE 且诊断 ≤512；预算内构造超限场景 → PHASE_TIMEOUT 不挂死；compile 输出被宿主校验拒绝的双向负向（编译器可信但不免检）。
4. **类型面**：`VisualExecutionResult.Rendered` 增 `drawCommandsJson`/`drawSchemaVersion`（core/domain 落点允许）；wire 信封 `VisualJsProtocol.kt:81,95-97,212-215,245-279` 同步加字段——快照两者都存（契约 §8"验证后的场景与 RenderScene/SHA"）。
5. **设备轨**：预算上限场景 100 ms 实测 + 隔离回归（P-08/A-11/R-02 批次已列，不重复建夹具）。

## 8. 未验证项（如实）

- 预算上限场景（256 节点/4096 命令）在**设备** QuickJS 上 strict-parse + lowering 的实测耗时：UNVERIFIED（只有 Wasm 桌面 7.5–35 ms/帧外推；命令在 §5.2/§5.3 可复跑）。
- 第三个上下文每事务的创建成本（设备）：UNVERIFIED；受 1500 ms 硬期限兜底。
- 真实 Provider 生成 SceneV1 程序的质量/成功率：UNVERIFIED（无凭据，纪律沿用）。
- d3-shape "模块稳定而非弃维护"的定性：单一来源判断，待验证。
- antvis/g 包级 license（无根 LICENSE 文件）：未核到一手文件，歧义登记。
- 表内其余仓库 license 为 GitHub API 检测：若未来真引入依赖，须按 js-engine-selection §8 方法读 LICENSE 文件复核（本设计只做算法级移植，无引入动作）。
- 本报告为研究设计：**未落任何产品代码、未跑 Gradle、未做 git 写操作**；§7 是建议而非已执行项。

## 9. 来源

- 契约与计划：`docs/current/visual-generation-contract.md`（§3.2 JS 协议 :43-58、§4 预算表 :74-90、§5 场景与显示列表 :92-104、§6 交互 :125-141、§8 持久化 :159-198）；`.zcode/plans/plan-2026-10-27-batch-27-4e2-mva-static.md`（§1 T1/T3）
- 代码（本会话通读）：`core/data/.../visual/QuickJsVisualExecutionEngine.kt`（:63-74 事务编排、:435-442 沙箱配置与 PRNG、:648-686 场景校验、:825-852 warm-up 与 bootstrap）；`core/domain/.../VisualExecutionEngine.kt`（:106-139 预算单源、:223-235 Rendered、:258-288 拒绝枚举）；`app/.../visualjs/VisualJsProtocol.kt`（:81,95-97,212-215 结果信封）
- 既有研究：`docs/research/2026-10-25-js-engine-selection.md`（引擎钉版/license 方法/issue #240）；`docs/research/2026-10-27-mva-reeval.md`（MVA 缺口：编译器/校验器/SDK 资产 0 命中）；`.jez/artifacts/research-brief-flexible-js-drawing-2026-10-17.md`（路线 B 与 SDK 草图）；`.jez/artifacts/flexible-js-drawing-2026-10-17/experiment.mjs` + `output/results.json`（13/13、8 MiB、wallMs）；`.jez/artifacts/android-visual-runtime-probe-2026-10-17/research-findings.md`
- GitHub（2026-10-27 `gh` 实测）：§1.2 表内 15 个仓库及其 LICENSE/源码路径（zrender `src/core/PathProxy.ts:225-339`、`src/canvas/dashStyle.ts`；kurbo `kurbo/src/arc.rs:77-86`）；URL 见表内链接
- 本会话数值计算：§5.3（κ 常数、90° 弧误差、Q→C 精度），命令与输出随文可复跑
