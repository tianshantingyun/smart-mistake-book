# 自由 JS 教学绘图：研究结论与接入证据

> 最新用户规则：JS与IMAGE_MODEL均为生图方法，所有入口共用判定，由模型选择；本报告旧固定分流/JS不计生图的设计推断已被[统一提示词](../current/figure-generation-prompt-policy.md)取代。原实验与代码审查证据保留。

日期：2026-10-18。研究完成后才修改计划；正式 App 功能未实施。

## 1. 结论

采用现有讲题模型生成短 JS、Android 隔离 QuickJS 计算几何、原生校验/排版/绘制。无需按题型增加原生绘图组件；程序能使用循环、函数、数组、采样、状态计算。简单图不调用图像生成模型，表单与流程卡仍使用原生 UI。

照片去手写、像素修补、艺术/写实插画保留明确用途的图像通道。几何题原图的规范重建改为多模态语义约束→JS→图题核对，不能把“画得像”当正确。

最终契约：[visual-generation-contract](../current/visual-generation-contract.md)；分批计划：[4E](../superpowers/plans/2026-10-18-flexible-js-drawing-implementation.md)。

## 2. 为什么选择这条路线

| 方案 | 适配性 | 决定 |
|---|---|---|
| 每次让图像模型画图 | 文本/标注/比例难核验；静态、交互与导出重建成本高 | 只保留适合像素生成的用途 |
| 固定图表/题型 JSON 模板 | 安全可控，但每遇新表现形式都扩枚举，模型算法自由度低 | 旧协议兼容，不作为新方案上限 |
| 模型 HTML/SVG/JS→WebView | 浏览器生态丰富，但 DOM/页面/字体、资源限制及 PDF 原生一致性另成系统 | V1 不选；将来确有浏览器需求再单独立项 |
| JS→通用场景→原生 | 算法自由；可验证、可持久化，复用现有 Compose/PDF | 采用；承担真实字体布局、进程隔离、运行库维护 |

“通用原语”是图形数据的底层语法，不是固定题型：圆、轴、箭头、网络、波形等由 SDK 生成 path/group/label。模型不需要调用原生题型 renderer，也不直接控制 Android。

## 3. 开源项目评估

| 项目/核查版本 | 许可与环境 | 适合承担的职责 |
|---|---|---|
| [quickjs-kt 1.0.15](https://github.com/dokar3/quickjs-kt/tree/a081755bcdb18ee6afa7ffee25b8fa976f06d31f) | MIT，Android minSdk23，JNI/Bellard QuickJS | 固定源码执行层；补服务隔离、硬 watchdog、16 KB 构建 |
| [AndroidX JavaScriptEngine 1.1.1](https://developer.android.com/jetpack/androidx/releases/javascriptengine) | AndroidX，要求 API26 和相应实现/特性 | 不覆盖现项目 API23，故不作统一首选 |
| [Zipline](https://github.com/cashapp/zipline) | Apache-2.0；官方明确不作为不可信程序沙箱 | 不选作安全边界 |
| [D3](https://github.com/d3/d3) | ISC；scale/array/shape 有纯计算部分 | 可选择少量算法；DOM selection 与定时器模块不直接加载 |
| [JSXGraph 1.14.0](https://github.com/jsxgraph/jsxgraph) | MIT/LGPL 双许可；浏览器 Board | 数学交互参考，整库不进裸 QuickJS |
| [p5.js 2.3.4](https://github.com/processing/p5.js) | LGPL-2.1；浏览器 Canvas | 自由画图 API 参考，非 Android 无 DOM 运行库 |
| [Konva 10.9.0](https://github.com/konvajs/konva) | MIT；Canvas，Node 也需绘图后端 | 编辑交互参考，不假定能直接原生或无损 SVG 导出 |
| [Penrose 3.3.1](https://github.com/penrose/penrose) | MIT；优化器/Wasm/DOM 渲染链 | 约束布局研究，不引入 V1 运行链 |
| [Vega-Lite 6.5](https://github.com/vega/vega-lite) | BSD-3-Clause；统计图数据流 | 统计语法参考，非全学科自由绘图核心 |
| [tldraw](https://github.com/tldraw/tldraw) | SDK 需单独核许可，不能简单称作 MIT | 编辑器/Make Real 参考，不直接依赖 |

详版研究保存于仓库 [JS 绘图研究简报](../../.jez/artifacts/research-brief-flexible-js-drawing-2026-10-17.md)，包含具体源码、issue、版本与浏览器依赖证据。版本是研究快照，不是自动升级声明。

## 4. 桌面实验

QuickJS-emscripten 0.32.0 独立上下文执行三个手写程序：功面积、拔塞阶段、波叠加；11 状态/程序，共 33 场景。13 项检查通过：能力缺失、无限循环、内存、非法数/预算/字段、转义、上下文隔离、确定性和外部 watchdog。

大稀疏数组字符串转换没有及时响应协作中断，最终被外层进程 watchdog 杀死。这是需要操作系统级隔离和硬终止的实验证据，不能只写 evaluationTimeout。

预览以已计算帧展示；浏览器检查了三组滑块和 360px 宽度。没有测试真实 Provider 自动生成程序的成功率，也没有证明生产字体的自适应排版已完成。代码/结果见 [实验目录中的结果](../../.jez/artifacts/flexible-js-drawing-2026-10-17/output/results.json)。

## 5. Android 最小探针

独立研究包 `com.tingyun.visualprobe`，没有修改正式应用代码。AGP9.3.0、Kotlin2.3.21、coroutines1.11.0，直接消费 quickjs-kt-android1.0.15 后 assembleDebug 成功。首次 AGP 默认 Kotlin2.2 编译失败；对齐仓库2.3.21后通过，未使用 metadata 绕过或升级正式工程。

emulator-5556，API34、x86_64、4 KB 页面，6 项检查 PASS：

1. 原生库加载、中文/emoji、101 点正弦计算；独立 PID/UID、无 INTERNET、宿主文件不可读、UID guard 阻止业务初始化。
2. fetch/document/window/process/require 不存在。
3. 无限循环被协作中断。
4. 堆上限触发 OOM。
5. 长原生操作由独立进程期限限制。
6. 人为阻塞工作线程后服务自杀、重新绑定新 PID，宿主 PID 存活。

命令、完整结果和边界见 [Android 接入复核](../../.jez/artifacts/android-visual-runtime-probe-2026-10-17/research-findings.md)、[probe-result.json](../../.jez/artifacts/android-visual-runtime-probe-2026-10-17/probe-result.json)。

## 6. 运行库发布问题及解决方案

固定 quickjs-kt v1.0.15 commit `a081755bcdb18ee6afa7ffee25b8fa976f06d31f`。研究 AAR SHA256：`406c3efb80d5fa3495df4a7f2663be8cc30c974fe5c2368effa929de77cf0ed8`。

四 ABI 的 PT_LOAD 对齐为16KB，但 GNU_RELRO 结束存在非零余数。APK zipalign 通过也不能证明内部库正确，Android 官方指出 RELRO 问题会导致16KB运行崩溃。因此正式产物固定源码，用 [NDK r28c 28.2.13676358](https://github.com/android/ndk/releases/tag/r28c) 重建，逐 ABI 验 ELF/RELRO、APK 打包和16KB设备实际加载。[官方16KB检查方法](https://developer.android.com/guide/practices/page-sizes#check-relro)。

当前未验证：NDK重建、API23、arm64、16KB设备、R8、正式 App Application/provider 初始化矩阵。它们是第一实施批次的阻断门；失败时不启用 JS，不自动换更大架构或回生图。

## 7. 当前工程接入必须解决的差距

- GENERATE_FIGURE 现只有 kind/description，无源码；请求 schema17，研究初读 Room66、定稿时并行升至67，无 TUTOR_VISUAL_GENERATE/REVIEW 任务。新字段必须在两路工具 schema/parser 同步；绘图迁移目标顺延68。
- 当前模型工具循环的注释声称留终答，代码还未撤最后一轮工具；新方案必须实际修正两路广告及终答执行门，总派发仍≤6。
- 现有循环回喂摘要，不保留完整工具 transcript；需有界 TutorVisualContext 供下一预算内回合修复，不隐含调用另一个模型。
- 现有 Compose/PDF 的坐标和数学排版部分重复；MathBox 字形宽估计、上标位置不足以保证准确，需共享度量/lowering/painter。
- 图片资产表只保存像素，程序/state/RenderScene 需单独表；预览不能用付费 GENERATED_FIGURE 或把 visualId 冒充 figureId。消息与保存题引用、GC、备份须一起接。
- 历史 attachedImages 的 cache miss 可能外部生图，新兼容要求改 cache-only，不能因打开旧消息收费。

## 8. 实施范围与验收

五批依次为：运行库/隔离，场景/排版，模型工具，存储/兼容，UI/导出。验六类用户截图及一个新算法组合，360/600/840dp与大字体/TalkBack；力图必须按真实方向，不能复刻截图中的误导箭头。共享快照导出不重新执行 JS。

真实模型产码正确率、图题语义正确率、设备耗时/内存和所有发布兼容仍需专门验证。该研究决定了实现路线与工程门，没有把研究样例宣称为正式软件完成。
