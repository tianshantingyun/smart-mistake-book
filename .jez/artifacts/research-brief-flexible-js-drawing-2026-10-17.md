# 模型自由绘图与 JavaScript 运行时：深度研究

日期：2026-10-17，Asia/Shanghai。研究深度：deep。<br>
对象：Android / Kotlin / Jetpack Compose 智能错题本，minSdk 23。<br>
范围：官方文档、开源仓库源码与发布记录、三个独立只读研究方向，以及本地 QuickJS 桌面实验。没有修改 Android 业务代码，没有调用图像生成服务，没有进行 Android 真机性能或完整安全审计。

## 1. 决策结论

应该支持模型现场构造新图形。上一轮围绕截图拆解组件只能说明六类页面可以实现，尚不足以回答“遇到新图形时是否需要增加客户端题型代码”。产品目标应升级为：模型能够编写受限权限下的绘图程序，自由组合形状、路径、文字、数学关系和交互状态。

推荐优先验证：

```text
模型生成 JavaScript 绘图程序
  → 可中断、可重建的隔离计算环境
  → 通用场景数据 / 绘图命令
  → 严格校验
  → 可信 Compose Canvas + 原生公式渲染
```

JS 在这里是构图和计算语言。它可以有循环、函数、条件、数组和数值采样；实际能够影响的宿主能力限定为绘图和受控交互。表达能力与宿主权限是两个独立设计问题。

WebView + SVG/Canvas 是另一条真实可用路线，尤其适合复用 JSXGraph 等浏览器库。它的优势是视觉和交互能力现成；代价是 Web 宿主、隔离、状态恢复、移动端资源和导出链路都需要管理。两条路线值得用同一题集实测后确定适用边界。

简单几何、坐标图、比例条、受力图、卡片关系图和参数演示默认采用确定性程序绘图。原题已有复杂图时可继续使用已入库的原图；艺术插画、真实材质等需求再另行选择图像生成或素材，不让所有视觉内容都走同一技术通道。

## 2. 用户所说的“灵活”应该如何验收

目标不是无限权限，也不是无限复杂度，而是：新增题目和新组合图时，只增加一段程序和输入数据，不修改渲染器源码。

应允许的能力包括：任意路径和贝塞尔曲线、组合形状、分组和变换、裁剪、坐标系、曲线采样、箭头、公式、标签、命中区域、多面板、按状态重绘。库里可以有辅助方法，但不能要求每种教学图都先有专用组件。

典型迁移验收：客户端没有“波形叠加组件”，模型仍能用循环采样和折线画出两列波及其叠加；客户端没有“拔塞页面”，模型仍能让矩形、瓶口路径和位移参数构成完整演示。

自由绘图不能自动保证物理、化学或几何内容正确。坐标轴单位、受力方向、函数定义域、对象比例、图与题意的一致性仍需内容验收。

## 3. 三条架构路线比较

| 项目 | A：直接生成 HTML / SVG / JS 页面 | B：JS → 绘图命令 → 可信渲染 | C：声明式图形 / 约束 DSL |
| --- | --- | --- | --- |
| 绘图自由度 | 浏览器能力基本开放，适合新交互与布局 | 图元、路径、变换足够通用时很高；可以临时写算法 | 取决于语言，通用图元、数据流和约束也能很灵活 |
| 循环、函数、采样 | 自然支持 | 自然支持，需执行资源预算 | 常见计算可用 sequence/map/表达式；复杂算法可能冗长 |
| 图形库复用 | 最直接，可用 DOM/Canvas 库 | 适合纯计算模块；浏览器绘图库不能原样搬入 | 通常使用该 DSL 对应运行时 |
| 原生项目集成 | 需嵌入 Web 宿主和消息桥 | 可复用现有 Compose/数学渲染 | 可复用现有模型层，但要维护解释器 |
| 查看历史静态图 | 若只保存源码，需要再执行 | 可直接重放已验证命令，无须执行原程序 | 保存规范、状态和求解结果可重放 |
| 交互状态 | 容易散落在 DOM、闭包和计时器中 | 显式 state → render，便于恢复 | signals / 绑定 / 约束，依赖 DSL 能力 |
| 主要成本 | 隔离、依赖、页面资源、恢复、测试面 | SDK、协议、执行器、校验器和 renderer | 语言设计、诊断、迁移、表达力扩展 |
| 建议用途 | 自由度上限的对照组；复杂 Web 图形分流 | 当前 Android 项目的默认候选 | B 的语义/约束层与直接数据降级通道 |

“声明式”不等于几个预设模板。Vega 的 signals、表达式和数据流可以驱动互动图；Penrose 能从对象与约束求布局。区别在于写程序的方法和运行边界，不宜简单判断 JS 一定比所有 DSL 更灵活。[Vega signals](https://vega.github.io/vega/docs/signals/)、[Penrose 语言](https://penrose.cs.cmu.edu/docs/ref)。

## 4. 为何优先验证 B

模型需要的常常是“算出怎么画”，不必同时拥有 DOM、网络、文件、Android 对象或数据库能力。B 允许模型临时写几何算法，同时把真正跨越信任边界的产物收敛为有限图形数据。

示意接口如下。它是建议的 SDK 形态，不是当前 App 已存在的 API：

```javascript
function render({ state, viewport, data }, g) {
  const points = [];
  for (let i = 0; i <= 100; i++) {
    const x = i / 100;
    points.push([x, Math.sin(2 * Math.PI * x + state.phase)]);
  }
  g.polyline(points, { stroke: "primary" });
  g.math("wave", "y=\\sin(2\\pi x+\\varphi)", { anchor: "top" });
}
```

这里没有“正弦波题型页面”。`polyline`、`math` 和坐标变换就是通用能力。标准绘图能力也不能只停留在圆、矩形和直线；需要逐步包含 path、变换、分组、裁剪和锚点，否则 B 会被 SDK 自身限制。

应提供少量可靠便利方法，如坐标映射、箭头、曲线采样、标注锚点和面板布局，以减少模型重复编写低价值代码；底层保留任意路径入口，让模型可以表达未预设的图。

### JS 引擎与绘图宿主不能混为一谈

QuickJS 提供 ECMAScript，不提供 DOM、Canvas 或完整浏览器。D3 的部分计算模块可抽取；完整 p5.js、Konva、JSXGraph 的绘制对象依赖浏览器宿主或其他图形后端。库能在 Node 运行，也不能据此证明能在裸 QuickJS 运行。

对于 B，不建议把每一条 `line()` 都作为一次 Binder/桥接调用。应在隔离侧收集一批命令后传输，避免逐图元通信开销；命令量与宿主端分配也要计量。

## 5. Android 执行器的实际选择

| 候选 | 已核实事实 | 对当前 minSdk 23 项目的判断 |
| --- | --- | --- |
| [QuickJS-KT](https://github.com/dokar3/quickjs-kt) | v1.0.15 发布于 2026-09-03，固定 tag 的 minSdk=23；基于 Bellard QuickJS；提供内存、栈、求值超时和中断接口 | 优先做 Android 原型；部署在隔离计算 Service 的可行性及重启流程仍需实测 |
| [AndroidX JavaScriptEngine](https://developer.android.com/jetpack/androidx/releases/javascriptengine) | 稳定版 1.1.1，2026-09-23；API26+ 且设备 WebView 实现支持；库本身 minSdk26 | 平台隔离能力有价值，但不能说常规接入即可覆盖 API23；需兼容及依赖策略 |
| [QuickJS-NG](https://github.com/quickjs-ng/quickjs) | C 引擎，v0.17.0 发布于 2026-09-18；MIT | 核心候选；Android 绑定/NDK/ABI/打包维护是额外工作，不等于现成 Kotlin 接入 |
| [Cash App Zipline](https://github.com/cashapp/zipline) | 当前 1.28.0，最低 API23；官方明确不提供 sandbox/process isolation，不应用于不可信代码 | 不作为模型代码默认执行器；不要因它使用 QuickJS 就误判信任模型 |
| WebView | 现成 SVG/Canvas/DOM 和触控环境；版本/renderer 能力随设备变化 | JSXGraph、p5 等分流路线；缺少通用每段代码 CPU/heap 配额，需要独立恢复设计 |

关键来源：[QuickJS-KT 固定版本配置](https://raw.githubusercontent.com/dokar3/quickjs-kt/v1.0.15/gradle/libs.versions.toml)、[QuickJS-KT JNI 实现](https://raw.githubusercontent.com/dokar3/quickjs-kt/v1.0.15/quickjs/src/jniMain/kotlin/com/dokar/quickjs/QuickJs.jni.kt)、[Android JavaScriptEngine 指南](https://developer.android.com/develop/ui/views/layout/webapps/jsengine)、[AndroidX 库配置](https://raw.githubusercontent.com/androidx/androidx/androidx-main/javascriptengine/javascriptengine/build.gradle)、[Zipline 信任模型](https://github.com/cashapp/zipline#secure)。

### 首选 Android 原型的边界

建议研究 `android:isolatedProcess="true"` 且不导出的绑定 Service，在其中加载 QuickJS-KT；用专用后台执行线程，主进程只接收已校验场景。普通 `android:process=":drawing"` 只改变进程，并不会自动去除 App 权限。[Service 官方定义](https://developer.android.com/guide/topics/manifest/service-element)。

QuickJS-KT 默认内存限制为 -1、默认超时关闭，需要主动配置。`evaluate` 是 suspend 函数也不代表求值自动转移后台线程；读取固定版本 JNI 调用路径后，执行调度和独立 watchdog 应由应用显式安排。隔离 Service 的 native 加载、OOM、超时退出与重新绑定都尚未在本项目验证。

AndroidX 连接到独立沙箱进程，但多个 isolate 共享这个进程。超时等待不等于终止执行；应按特性检测决定关闭 isolate 或整个 sandbox。API23–25 不能仅凭运行时 `if` 就解决依赖 minSdk26 的合并问题。[JavaScriptSandbox API](https://developer.android.com/reference/androidx/javascriptengine/JavaScriptSandbox)、[JavaScriptIsolate API](https://developer.android.com/reference/androidx/javascriptengine/JavaScriptIsolate)。

## 6. 开源绘图库筛选

| 项目 | 版本/许可核查 | 最适合的角色 | 真实限制与取舍 |
| --- | --- | --- | --- |
| [D3](https://github.com/d3/d3) | v7.9.0；ISC；仓库仍有后续维护 | 选取 scale、array、shape、布局等计算模块，作为绘图 SDK 的工具箱 | 不把整个 DOM 操作层搬进 QuickJS；force 默认有计时器，需显式停止/步进并适配 |
| [Observable Plot](https://github.com/observablehq/plot) | 官方 marks API 已核查 | 借鉴 mark 组合、accessor、条件构图和通用图形 API | 自身不是代码沙箱，也不会判断图的教学含义；完整图形输出依赖宿主 |
| [JSXGraph](https://github.com/jsxgraph/jsxgraph) | v1.14.0，2026-10-05；MIT 或 LGPL 双许可 | 交互几何、函数、积分区域、滑块、动态点 | 完整 Board 依赖浏览器；MathJax 公式和截图链要组合验证 |
| [p5.js](https://github.com/processing/p5.js) | v2.3.4，2026-09-25；LGPL-2.1 | 最接近自由 sketch 的浏览器绘图/动画接口；也可借鉴 API 风格 | `createCanvas` 创建 HTML Canvas；核心导出 PNG/WebP/JPG，SVG 需另评估扩展；没有通用教学布局求解 |
| [Konva](https://github.com/konvajs/konva) | 10.9.0，2026-10-15；MIT | 需要对象点选、拖动、分组、变换的图形编辑交互 | 当前缺少原生 SVG 导出；Node 使用也需要 canvas/skia 后端，不是裸 JS 即可绘图 |
| [Penrose](https://github.com/penrose/penrose) | v3.3.1，2026-08-30；MIT | 复杂集合、几何和关系图的约束布局；作为后续独立能力 | core 依赖 Rose、MathJax 和 Wasm；SVG API 返回 DOM 元素，不能直接塞进裸 QuickJS；局部优化不保证所有关系成立 |
| [Vega / Vega-Lite](https://github.com/vega/vega-lite) | Vega-Lite v6.5.0，2026-10-08；BSD-3-Clause | 实验统计图、多系列、轴/图例与交互选择 | 不适合作为任意物体/器官/受力图唯一语言；组合图 autosize 有限制；完整 View 的宿主要求需适配 |

版本与许可来源：[D3 release](https://github.com/d3/d3/releases/tag/v7.9.0)、[p5 release](https://github.com/processing/p5.js/releases/tag/v2.3.4)、[Konva release](https://github.com/konvajs/konva/releases/tag/10.9.0)、[JSXGraph release](https://github.com/jsxgraph/jsxgraph/releases/tag/v1.14.0)、[Penrose release](https://github.com/penrose/penrose/releases/tag/v3.3.1)、[Vega-Lite release](https://github.com/vega/vega-lite/releases/tag/v6.5.0)。许可证以对应项目 LICENSE 为准，不能仅依赖 GitHub API 自动识别。

D3 官方明确指出 scale、array、interpolate、format 不操作 DOM；shape 可以输出 path。它很适合支撑 B，但与原生 path 命令的转换、曲线截断、数值域仍需实现。[D3 入门](https://d3js.org/getting-started)、[面积生成](https://d3js.org/d3-shape/area)、[force 步进](https://d3js.org/d3-force/simulation)。

Two.js 与现有能力及 Konva 重叠，暂不进入优先栈。Sandpack/WebContainers 解决代码运行和浏览器预览，能力范围远大于简单绘图，不应仅为几幅教学图默认引入完整应用开发环境。[Sandpack bundler](https://sandpack.codesandbox.io/docs/advanced-usage/bundlers)、[WebContainers](https://webcontainers.io/guides/introduction)。

## 7. 社区问题与版本信号

这些是官方仓库记录，不是对所有版本都成立的普遍结论。

- [Konva #2072](https://github.com/konvajs/konva/issues/2072)：提出原生 SVG 导出缺失。若要打印、放大和重排矢量图，这比 Stars 更影响选型。
- [Vega-Lite #9927](https://github.com/vega/vega-lite/issues/9927)：隐藏容器与 `width:container` 导致最终宽度为零。聊天卡片、折叠区域和复用宿主均需测试。[尺寸文档](https://vega.github.io/vega-lite/docs/size.html)也列出组合图 autosize 限制。
- [Vega-Lite #9761](https://github.com/vega/vega-lite/issues/9761)：组合图轴标签重叠报告，说明自动布局不自动保证可读。
- [JSXGraph Board 文档](https://jsxgraph.org/docs/JXG.Board_.html)：screenshot 对 text/foreignObject 中再嵌 SVG 有限制，涉及 MathJax 输出时需验证。
- [Penrose #1976](https://github.com/penrose/penrose/pull/1976)：复用 MathJax pipeline 修复反复编译时的泄漏，v3.3.1 包含该修复。这是已修复历史问题，不应说最新版仍有同一缺陷。
- [QuickJS-Emscripten #219](https://github.com/justjake/quickjs-emscripten/issues/219)：大型数组转字符串可能长期不进入 interrupt 回调。本轮也对相似输入执行了外层硬截止实验，见下文。

## 8. 真实产品和系统的可借鉴证据

Claude 官方证实聊天中可生成临时、可调整的自定义图形；OpenAI 官方证实交互式数学/科学模块。这些证明产品形态存在，不能证明用户截图的内部实现，也不能证明其所有内容都由任意 JS 现场生成。[Claude 官方介绍](https://claude.com/resources/articles/claude-builds-visuals)、[OpenAI 官方介绍](https://openai.com/index/new-ways-to-learn-math-and-science-in-chatgpt/)。

Figma 官方工程文章介绍过从插件代码到受控图形 API 的设计，并在后续安全更新中说明迁移到 C JS VM 编译为 Wasm 的方案。这是“开放程序表达力，同时控制宿主能力”的相关先例；文章是历史实现记录，不能直接当作当前 Android 绘图方案。[插件系统](https://www.figma.com/blog/how-we-built-the-figma-plugin-system/)、[后续安全更新](https://www.figma.com/blog/an-update-on-plugin-security/)。

[tldraw Make Real](https://github.com/tldraw/make-real) 的固定 commit `928c4c4176fbe84fef4e4c3573e325d12cddbe4d` 展示了选中图像、旧 HTML、再次生成和截图回传机制。其流式预览代码不是可直接照搬的隔离样板；tldraw SDK 当前属于 source available，不能笼统列为 MIT 开源生产依赖。[预览源码](https://github.com/tldraw/make-real/blob/928c4c4176fbe84fef4e4c3573e325d12cddbe4d/app/PreviewShape/PreviewShape.tsx)、[许可说明](https://tldraw.dev/community/license)。

## 9. 运行边界：哪些措施实际解决什么

| 措施 | 能解决的事 | 不能据此推导的结论 |
| --- | --- | --- |
| 独立 JS runtime、未挂载宿主接口 | 限制脚本可调用的宿主能力 | 不能保证引擎无漏洞或不会耗尽资源 |
| guest heap / stack / interrupt | 限制部分计算和内存开销 | 不覆盖所有 native 操作、宿主分配和最终绘制成本 |
| 独立可终止进程 | 主界面可在执行器失败后恢复 | 不证明数学正确，也不替代输出校验 |
| JSON/display-list 校验 | 禁止未知图元、非法属性、非有限数、过量数据和资源入口 | 不知道箭头方向是否符合题意 |
| iframe sandbox / CSP | 限制浏览器 origin 和特定能力通道 | 不提供 CPU/heap 配额，不是数学校验器 |
| Web Worker | 把计算移离页面线程，并提供 terminate | 默认仍有网络等 API；不是独立 OS 权限沙箱 |

不要使用 `node:vm` 作为不可信代码安全边界，Node 官方明确否定这个用途。[Node VM](https://nodejs.org/api/vm.html)。同源 iframe 同时拥有 `allow-scripts` 与 `allow-same-origin` 时还存在移除 sandbox 的风险。[HTML 标准](https://html.spec.whatwg.org/multipage/iframe-embed-object.html#attr-iframe-sandbox)。

WebView 路线需要使用可信外壳、受控消息、来源与长度校验；不把模型页面接到通用 `addJavascriptInterface` 或反射桥。缺失资源、网络、导航、弹窗、文件/content URI 都需要明确策略；仅写 CSP 一项不是完整实现。[Android 桥接风险](https://developer.android.com/privacy-and-security/risks/insecure-webview-native-bridges)、[本地内容加载](https://developer.android.com/develop/ui/views/layout/webapps/load-local-content)。

## 10. 本地实验及实际结果

实验目录：`.jez/artifacts/flexible-js-drawing-2026-10-17/`。依赖锁定 `quickjs-emscripten@0.32.0`，通过 `npm install --ignore-scripts` 安装在研究目录，没有改动产品依赖。

运行命令：

```powershell
node --check experiment.mjs
node experiment.mjs
```

执行模型：每个任务进入独立 Node 子进程；子进程内部用 QuickJS/Wasm 执行 JS。不给 guest 安装文件、网络、DOM、模块加载或原生对象桥。guest 输出 JSON 后由宿主校验，再由可信 SVG exporter 生成图。它证明的是路线 B 的桌面可行性，不是 Android 引擎、隔离 Service 或 Compose renderer 的实测。

实验包含三段本轮手写的示例程序：变力做功、拔塞位移、两列波叠加。每段输入 11 个参数值，产生 33 个场景。第三个场景只增加 JS 程序，没有修改通用 SVG 渲染器。

| 检查 | 实测结果 |
| --- | --- |
| 同一渲染器承接三个程序 / 33 状态 | 通过 |
| `fetch/document/window/process/require/WebAssembly` | guest 中均为 undefined |
| 普通 `while(true){}` | 被引擎 interrupt 中断 |
| 过量 heap 分配 | 产生 out of memory，宿主仍运行 |
| NaN / 超范围坐标 | 在宿主解析后的校验中拒绝 |
| 超图元预算 | 拒绝 |
| 标签中夹带 HTML | 当文字转义，未执行 |
| URL 样式 / 未知事件属性 | 拒绝 |
| 新 context 之间的全局变量 | 未继承 |
| 外层 watchdog | 实际终止挂起子进程 |
| 大数组转字符串压力样例 | 触发约 2 秒外层截止；没有在 100ms guest deadline 内正常返回 |
| 同源码/输入重复运行 | 规范化场景一致 |

总计 13 项检查通过。实验配置为 guest heap 8MiB、求值 deadline 100ms、普通外层截止 3 秒，特殊压力样例外层截止 2 秒；这些是本轮实验参数，不是产品推荐性能指标。

最重要的负面结果：不能把“配置了 100ms interrupt”写成所有 JS 都能在 100ms 内停止。特殊操作需要独立执行边界的硬截止。样例通过也不能称为完整安全审计。

输出文件：`output/results.json`、`output/programs.json`、`output/preview.html`、三份 SVG。预览页面只切换提前算出的 11 个状态，没有在浏览器里重新执行示例程序；没有测量真实模型自动生成代码的成功率。

浏览器验证：实际操作三个滑块到边界，核对做功读数 0.500、木塞完全拔出、叠加振幅 2.000；360px 视口无页面横向溢出，并输出桌面及窄屏截图。图内文字仍随固定 viewBox 缩小，尚未实现面向手机的图形重排与独立字号策略；浏览器验证不代表 Android 可用性验收。

## 11. 与现有项目的连接方式

本轮读取的 `dynamic_gui_architecture_plan.md` 已禁止任意 JS 和循环，这是旧方案的取舍，不是技术上的必然限制。用户现在要求重新研究更自由方案，应重新评估这些条款，而非以旧计划阻止分析。本轮没有修改这份计划。

若验证后采用 B，建议将以下内容写入下一版设计：

- 模型产物增加可选绘图程序；已有 `QuestionDocument` 和文字/公式路径继续作为内容层与降级，不另立第二套学习事实模型。
- 内部图形场景由纯数据定义，程序只是场景的生成方式之一；直接数据和 JS 生成结果共用 validator/renderer。
- 既有 MathBox 承担公式排版。增加 `math` 图元时处理基线、锚点、边界与替代文本，不把复杂公式变成普通 SVG text。
- 图形布局需要文字/公式尺寸。可以采用批量度量、预提供字体指标或两阶段布局；不能假设裸 QuickJS 有浏览器 `measureText`。
- 选择题、理由输入和提交继续使用原生业务控件；图内热点、滑块和播放属于受控交互事件。
- 模型程序不能写 Room、掌握度或任意业务字段。浏览图形、拖动滑块也不自动变成学习证据。

## 12. 参数、交互、动画与重放

推荐程序契约：`init(data)`、`update(state,event)`、`render(data,state,viewport,time)`。输入/输出必须可序列化且受限，事件只允许预定义类型。它是设计方向，尚未在 Android 实现。

随机性使用显式 seed；时间由宿主传入，避免依赖壁钟；保存程序 hash、SDK/runtime/renderer/schema 版本、输入、状态、seed、字体和已验证静态结果。只保存源码并不能保证以后还能显示相同图形。像素级一致性还依赖字体和渲染环境。

历史静态查看可直接消费缓存场景。交互时根据新 state 在隔离侧生成结果；高频动画要评估 IPC、图元更新量和电量，优先考虑关键帧、变换绑定或原生插值。不能未经真机测试就承诺每帧重新运行 JS 的帧率。

纯函数式契约也不是靠提示词就能保证；隐藏全局状态、计时器和未锁定依赖会破坏重放。实验使用新 context；生产究竟采用每次重建、持久上下文还是检查点，需要按延迟与可重现性实测取舍。

## 13. 正确性与纠错闭环

需要两条独立验收线：

1. 视觉检查：越界、遮挡、字号、标签、曲线断裂、坐标比例和面板重排。可以把截图和诊断交给模型做有限次修正。
2. 内容检查：数值、单位、几何关系、受力方向、题设条件、函数定义域。图像看起来合理不能替代数值与关系断言。

代表性反例：`1/x` 跨零点不能被一条直线连接；圆的坐标系不能因非等比缩放变成错误的椭圆；箭头不能为了避让文字而改变力方向；位移参数不等于真实时间。

模型自检也可能重复同一错误。验证来源应尽量独立，包括确定性计算、关系断言和必要的内容复核。截图纠错的外发仍须遵守当前授权策略与逻辑操作 dispatch 预算，不能设计无界“截图—再问模型”的循环。

## 14. 下一阶段应做的比较实验

不建议立即重写主渲染系统。先做一个 Android 隔离原型，同时比较 A 与 B，保留 C 作为直接数据基线。

建议题集 30～50 个样例，覆盖原六图、非标准组合图、分段/渐近线、参数曲线、向量场、几何依赖、光学、电路、化学/生物结构关系。测试中记录是否因新题修改 renderer；若修改，说明尚未达到目标自由度。

固定模型、输入、token 预算、纠错次数和输出目标，至少比较：首次可渲染率、首次内容正确率、修正次数、生成时延、执行/首帧、长消息滚动、内存峰值、恢复成功率、导出可用性。不能根据模型熟悉 JS 的直觉直接声称生成质量更高。

Android 矩阵至少覆盖 API23/25、26、较新 API 和不同 WebView；对 QuickJS-KT 的隔离 Service 做 native 加载、死循环、OOM、异常退出、重新绑定、旋转和进程重建测试。测试大字体、窄屏、TalkBack、禁动画与旧会话。

按结果分流：普通教学图默认 B；复杂交互几何研究 WebView + JSXGraph；大量自由 sketch 动画研究 p5；约束静态图研究 Penrose；实验统计数据可走 Vega-Lite。图片素材与图像生成保留为不同需求的工具。

## 15. 未验证项与证据限制

已验证：官方版本/配置/API、部分真实源码调用路径、桌面 JS→JSON→SVG 的运行结果、失败隔离和输出校验样例。未验证：Android APK 依赖合并、隔离 Service、各设备 WebView 特性、原生 heap/帧率/功耗、真实模型生成成功率和跨学科内容正确率。

GitHub 公共 API 调研途中触发限流，后续维护信息改从官方仓库页面、发布记录和固定 tag 获取。搜索中出现的 `anthropics/generative-ui` 地址实际 404，未将第三方 README 的说法当作官方开源证据。

研究预览工具禁止 `file:` URL，浏览器检查通过仅绑定 127.0.0.1 的临时静态服务进行；文件本身没有远程依赖。所有实验文件都位于研究产物目录，Android 产品代码未改动。
