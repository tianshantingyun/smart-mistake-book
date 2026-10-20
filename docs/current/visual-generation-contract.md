# 教学图形生成契约

决策日期：2026-10-18；本地对抗性复核修订：同日。状态：**方案确定；2026-10-19已有统一policy/coordinator在飞接线，目前仅执行IMAGE_MODEL，JS仍不可用，正式全链未验收**。本契约取代历史文档中“重绘一律使用生图模型”“禁止所有模型 JS”“只允许固定题型组件”的未来实施要求；历史提交、测试与完成记录仍保留原意。实施批次见 [4E 计划](../superpowers/plans/2026-10-18-flexible-js-drawing-implementation.md)。

## 1. 统一生图与方法选择

JS 和 IMAGE_MODEL 是同一生图能力的两种方法。所有涉及生图的入口，包括题图转写、干净题面、规范重绘、保存净化、讲解图和插画，统一使用“目标/源证据→模型判定→选择方法→生成→共同验收→产物/引用/记录”管线。用途不决定后端。

权威选择规则与可直接接入的提示词正文见[统一生图提示词](figure-generation-prompt-policy.md)。精确关系、公式标注、可编辑和交互通常优先JS；纹理、自然形态、写实或像素编辑通常优先IMAGE_MODEL。这是模型参考偏好，不是类型黑名单。题图转写可选择JS，讲解图可选择图像模型；客户端只验证真实能力/授权/预算和目标，不偷偷改选。

允许模型在同一操作内根据失败诊断修复或重选方法，无需新增逐图确认；切换仍受原目标、授权、质量和预算约束。没有可行方案时保留原成果/原图并说明，不以换方法掩盖失败。

JS后端为模型源码→隔离运行→校验场景→原生回放；IMAGE_MODEL后端复用已配置的图像生成/编辑通道，结果走canonical资产。IMAGE_MODEL的EDIT/GENERATE是内部操作，源图准备不是第三种方法。流程卡、单选、输入和提交继续原生控件，不强制画为图片。

JS仍可自由使用函数/循环/采样/状态组合通用图元，不为题型加页面。安全限制只约束执行边界：无任意HTML/CSS/SVG输入、DOM、WebView、Kotlin或Android调用。SVG只从已验证数据导出。

## 2. 研究证据与选型

- 桌面 QuickJS 实验：3 个程序、33 个场景、13 项检查通过；另有移动宽度预览。它证明程序绘图及资源控制可行，不代表真实模型生成成功率。
- Android 独立探针：AGP 9.3.0、Kotlin 2.3.21、coroutines 1.11.0，消费 quickjs-kt-android 1.0.15 编译成功；API 34/x86_64/4 KB 页设备 6 项检查通过，包含隔离权限、中文、死循环、内存、硬 watchdog、重建。
- 正式采用 [quickjs-kt v1.0.15 固定源码](https://github.com/dokar3/quickjs-kt/tree/a081755bcdb18ee6afa7ffee25b8fa976f06d31f)，commit `a081755bcdb18ee6afa7ffee25b8fa976f06d31f`，保留 MIT/QuickJS 许可证。用 NDK r28c `28.2.13676358` 重建，构建依赖及子模块也记录 SHA，不动态追最新版本。
- 发布 AAR 的 PT_LOAD 为 16 KB 对齐，但 GNU_RELRO 结束未对齐；APK zipalign 通过不能解除该问题。源码重建、API 23、arm64、16 KB 真正加载、R8 尚未验证，全部是启用前阻断门。[Android 官方检查要求](https://developer.android.com/guide/practices/page-sizes#check-relro)。
- AndroidX JavaScriptEngine 1.1.1 的 minSdk 26 不覆盖本项目 23，故不作为第一版统一运行库；Zipline 不作为不可信代码安全边界；D3 的纯算法可按需移植，p5.js、JSXGraph、Konva、Penrose、Vega 的浏览器/DOM/Canvas/Wasm 依赖不直接塞入 QuickJS。各库版本、许可和取舍见研究报告。

研究与可复跑证据：仓库 `.jez/artifacts/research-brief-flexible-js-drawing-2026-10-17.md`，`.jez/artifacts/android-visual-runtime-probe-2026-10-17/research-findings.md`、`probe-result.json`。研究产物不得当作正式功能完成记录。

## 3. 程序与调用协议

保留 GENERATE_FIGURE 作为讲题工具适配器；其 kind 只映射用途，不映射方法。统一 FigureGenerationRequest/Proposal 的 method 只有 JS/IMAGE_MODEL，decision 为 GENERATE/NO_FIGURE/NEED_SOURCE/UNAVAILABLE。JS带完整program；IMAGE_MODEL带imageOperation=EDIT/GENERATE与imagePrompt。现有executionMode仅为兼容实现映射：JS→LOCAL_JS，图像方法→IMAGE_EDIT/IMAGE_GENERATE，PREPARE_SOURCE只对应准备步骤，不能当业务方法。

请求由宿主绑定subject/basis、purpose、源资产及质量要求。subject可为讲题当前题、Capture草稿、保存修订或授权源页；模型不指定文件/URL/数据库对象。业务角色按用途和subject派生，不按method判断。programV1仍为apiVersion/sourceJs/initialState/purpose/description/fallbackMarkdown，原始UTF-8保存，禁止塞正文或旧attachedImages。

### 3.1 统一入口与源证据

所有调用点构造同一种 FigureGenerationRequest，经 core:domain.FigureGenerationCoordinator 端口统一编排。现有TUTOR_PLAN/RESPOND承载工具proposal；CAPTURE_PARSE可附同型proposal；缺少proposal时现有IMAGE_PIPELINE_CLASSIFY用同一提示词判定，不新增视觉任务。保存净化/讲解图/重绘不能各维护一套方法路由。problemKind只描述题面，不再硬编码TEXT_ONLY不生图或WITH_FIGURE必走图像模型。

当前TutorQuestionContext没有原题资产，Plan不发原题图，Respond仅发studentImageAssetRefs；这些仍是接入差距。先冻结VisualSourceBasis（subject/revision、canonical引用/SHA、允许披露范围），各后端取得其必需证据：JS产码模型需实际看到原图或足够且可追溯的结构化源事实；IMAGE_MODEL编辑执行需收到已授权原图。不能把本地有图当参与生成的模型已看图，也不为每张图强制重复分类/上传。

NEED_SOURCE/PREPARE_SOURCE统一准备缺失证据，下一预算内回合实际发送后记录sourceSeenDispatchId/字节摘要；同批准备不冒充已发送。本轮已具足证据可直接判定生成。规划模型只需决定将已授权图交给图像编辑执行时，按执行模型的真实资产披露校验，不强制另发一次主模型请求。

操作指纹保留冻结source basis，每轮实际请求/授权覆盖真实图像/context。准备/生成前按所选可行方法的剩余远程成本与终答预留计算预算；不能统一写死“后面两次一定够”：JS本地执行不需远程，图像生成/编辑另占一次模型派发。证据不足、题目变更、SHA改变或预算不足均停止，不猜补原题事实，不复制新操作身份绕过预算。

### 3.2 JS后端协议

以下JS执行/场景约束适用于JS方法，不是其他业务入口的另一套判定规则。

代码声明 `const visual = { prepare(input), render(input, metrics), reduce(state, event) }`；SDK 在同一全局上下文提供纯函数图形帮助方法。模块加载器、主机对象和 import 均不开启。bootstrap 在用户源码前运行，封闭 Date、Math.random 等隐式时钟/随机源；随机必须使用 input.seed 的 SDK PRNG。用户源码永远视为不可信，bootstrap 限制不能替代进程隔离和输出验证。

`FrameInput` 包含 `{programId, revision, state, viewport, timeMs, seed, theme}`；只含本图数据，无密钥、凭据、文件路径或全库对象。一次布局事务冻结这些值：

1. 新建 QuickJS 上下文执行源码和 `prepare(input)`，返回批量内容度量请求，随后关闭上下文。
2. 原生验证并度量 text/math，保留不可变布局 artifact，返回宽、高、baseline、inkBounds、实际字号与内容摘要。
3. 新建上下文重新执行同一源码和 `render(input, metrics)`；必须只引用本事务 contentId。拒绝新内容、暗改字体、宽度或无限追加测量。
4. 原生严格校验、共享 compiler 编译，原子提交 RenderScene。reduce 同样使用新上下文；程序不能依赖跨调用全局变量。

每事务最多一次 prepare、一次 measure batch、一次 render。坐标/颜色动画复用已布局内容；内容变化再走有界事务。缓存只在内容摘要和布局环境完全一致时命中。前端不得在 draw 回调执行 JS 或解析公式。

阶段能力按4E.1统一管线/静态后端、4E.2交互、4E.3复杂源与保真扩展分别声明；所有生图入口从4E.1接同一判定，不能按阶段把某用途硬绑后端。静态程序可省略 reduce；每次调用的 input 与 metrics 深冻结，reduce 仅能返回符合 StateV1 的局部状态。未启用的控件/事件/模式在模型能力上下文中不广告，解析与执行也同时拒绝，不仅隐藏 UI。

## 4. 隔离、IPC 与预算

新增非导出 Service，`android:isolatedProcess="true"`、专用 `:visual_js` 进程。Application 必须在打开 Room、恢复备份、维护文件、创建 Provider/密钥工厂前，用公开 API `Process.myUid() != applicationInfo.uid` 短路；同时审计构造/字段与 companion 初始化、实际进入该进程的 ContentProvider/AndroidX Startup，不能只在 onCreate 最前面加 guard 就宣称已隔离全部启动路径。隔离进程不申请文件/网络主机绑定。

宿主通过 AIDL 发控制消息，源码和 JSON 经有界 ParcelFileDescriptor 管道传输；读写循环均计字节、可取消，不逐图元发 Binder。每次任务有 requestId、generation、phase；全局一个在飞绘制、最多两个待绘 program（各保留一个最新状态），只合并相同 program 的旧状态，跨 program 按序处理。满队列回 BUSY 并保留原帧，由可见/交互生命周期限次重新调度，不把另一张图误当旧帧丢掉；初次工具生成按已有工具序列执行。旧帧与不再匹配场景的事件拒绝。消费者须验证服务调用身份、描述符方向与长度，不暴露宿主通用 RPC。

JS 在服务专用单线程 dispatcher 上执行，不能因 evaluate 是 suspend 就假定离开主线程。引擎 timeout 是协作中断，close 可能等待引擎锁，均不是硬终止。

独立 watchdog 线程在每个 JS 阶段开始前启动；硬超时由服务自己 `killProcess(myPid())`。AIDL `oneway terminate(generation)` 在 Binder 控制线程先原子核对当前 generation，再自杀，不排在 JS 执行队列；过期 terminate 必须 no-op。watchdog 捕获同一执行令牌，正常完成/取消先使令牌失效，迟到定时器不得杀下一任务。宿主先废弃 generation，再请求终止/解绑，通过 Binder death 确认旧实例终结后最多重建一次；不假定宿主能杀不同 UID，也不把 unbind 当同步终止。死亡未确认时展示 fallback，不能并发放大进程数。

重建只恢复运行环境，不自动重跑刚才超时/崩溃的源码。该 programHash 熔断至用户明确本地重试或源码改变；本地重试仍受相同预算。服务关闭超时不得通过无限新建服务/线程绕过。宿主字体/路径编译的期限是结果作废与背压的软期限，协程取消不能硬中止同步 StaticLayout/Canvas；超时保持原帧，并待单个后台任务结束再接新任务。绝不 kill 宿主或声称所有 native 调用都有硬期限。

初始 V1 上限如下；属于待设备性能验收的产品预算，探针通过不等于这些全组合已达标。

| 资源 | 硬上限 |
|---|---|
| 单程序源码 / state / 单向 JSON | 64 KiB UTF-8 / 16 KiB / 256 KiB |
| QuickJS 堆 / 栈 | 16 MiB / 256 KiB；宿主 JSON、字体、像素另设预算 |
| 单 JS 阶段协作期限 / 服务硬期限 / 宿主阶段期限 | 100 ms / 1500 ms / 2500 ms；期限不是实时调度保证 |
| 场景节点 / 树深 / 路径命令 | 256 / 8 / 全场景 4096 |
| 内容请求 / 总字符 / 控件 / 命中区 | 128 / 6000 / 16 / 128 |
| 画布边长 / 坐标绝对值 | 4096 du / 100000；矩阵及变换后边界也须有限且合规 |
| 自动播放 | 最多 10 fps，后台暂停，丢帧合并；低端设备不达预算降为逐步播放 |

新增程序建议≤12 KiB，64 KiB 是拒绝阈值而非每轮目标；单逻辑操作最多提交两份最终图，累计接收源码（含失败尝试）≤128 KiB、新源码布局尝试≤6次、累计 JS 阶段墙钟≤10秒。任一预算耗尽即文字降级并使用原派发余额终答，不另建任务；正常本地交互不重复消耗远程逻辑操作次数，但仍受单事务/熔断限制。单份模型工具 payload 的解码后 JSON≤512 KiB，sourceJs 再按实际 UTF-8≤64 KiB 检查，不截断源码、转义或代理对；工具回喂摘要仍遵守原有字符预算，源码只进独立有界 context。

本表的 JSON 上限针对 worker/视觉 DTO，不等于整个 Provider HTTP 响应已有总量上限。当前 transport.readSse/readFully 明确没有响应体总量 cap，属于既有独立风险；本次不暗改所有模型任务的响应政策。新增工具参数增量须独立有界累积、超限中止本轮，不能等整段巨量源码存入通用 history 后才检查。

严格拒绝未知字段/节点/路径命令、重复 JSON 键/ID、原型污染键、悬空引用、NaN/Infinity、非规范 state、非法颜色、任意 URL/字体/纹理/图片。路径命令预算同时计定义总数与展开后的实际回放次数，不能借重复引用放大。sizeSp 限 12..48，系统 fontScale 另计；单事务所有文字 artifact 与预览的累计像素≤4 MP，单 bitmap≤16 MiB，宿主只保留当前帧和一个候选帧，释放旧 artifact。原生解析、几何派生、字体布局在后台受输入复杂度、单在飞任务与软期限约束；线程中已开始的同步工作可能晚于期限结束，只能废弃结果。StateV1 深度≤8、标量≤1024；场景 JSON 解析深度≤64，均在流式读取/解码时检查，不能先无界建树后再拒绝。超限不截断成“正确图”，返回有界诊断；交互失败保留上一有效帧，首次失败显示文字/公式说明。

## 5. 通用场景与显示列表

SceneV1：`schemaVersion, sceneId, revision, viewBox, background, paths[], root, controls[], hitRegions[], form?, accessibility, fallbackMarkdown`。只有 group/path/label 三类绘图节点，原生控件与可选表单另列；form 仅在 4E.2 能力开启时允许。

- path 定义为 `M/L/Q/C/Z` 数值段数组，不接收 SVG d 字符串。必须 M 开始；Q 在共享 compiler 转 C；圆、弧、箭头、轴由 SDK 生成基础路径，弧拆为每段不超过 90° 的三次曲线，误差算法随 SDK 版本固定。
- group 含本地矩阵、opacity、visible、可选 clip path、children；路径含 fill、stroke（width/cap/join/miterLimit/dash），只支持 srcOver。clip 只相交，fillRule 为 nonzero/evenodd，不支持 inverse clip、滤镜或隐式大离屏层。
- 矩阵约定 `x'=a*x+c*y+e, y'=b*x+d*y+f`，组合 parent*local。按 children 顺序绘制；group alpha 乘到子节点，不承诺 CSS 隔离透明组语义。stroke 随变换缩放。
- label 引用已度量 contentId，包含 anchor、offset、水平/垂直对齐、rotation、颜色、朗读顺序。compiler 一次算出最终位置，不由 Compose/PDF 各自猜 baseline。第一版无自动标签避让优化器，JS 根据真实 metrics 布局；原生检查越界并给诊断。
- `1 du=1 dp`，屏宽变化和 fontScale 变化重新布局；字号使用允许字体的 sizeSp，原生解析成 du。学科量纲留在 state，与绘图坐标分离。根变换只做一次 du→px。数学 y 轴朝上由 JS 显式变换，标签投影后放置以免倒置。

RenderScene 是校验后的不可变显示列表：固定路径、矩阵/裁剪、已放置 glyph/line/path 或文本 artifact、语义与命中区。不可包含函数、脚本或回调代码。`core:model` 的协议与 compiler 不依赖 Compose；`core:visual` 的 AndroidScenePainter 共用于 Compose nativeCanvas 和 PDF page.canvas。

原生执行编排由 app 的 AndroidVisualExecutionEngine 实现 core:domain.VisualExecutionEngine 端口，组合 core:visual-js 客户端、core:visual 内容布局和纯 compiler。core:data 只通过该端口做来源/幂等/持久化/CAS，不直接引用 AIDL/原生排版。core:ui/export 只依赖 core:visual 回放，不依赖 JS worker。依赖图见[架构契约](architecture-contract.md)，避免把布局插件硬塞入数据层或让导出需要脚本执行器。

持久化的 RenderScene 必须是可序列化 DTO：内容文本/数学 lowering、冻结的行范围与 run 位置、baseline、字体身份/版本及摘要随显示操作保存。Android Path/Paint/StaticLayout 仅为进程内缓存，不序列化进 Room。冷恢复在字体/renderer 精确匹配时从已冻结记录重建原生 artifact，不再执行 JS；资源不匹配时读取原预览或文字说明，不能换系统字体后仍声称同快照排版一致。需要新环境重新排版时明确生成新快照。

现有 `MathBudget.kt` 的 parser/AST/budget 可复用；MathBox 的字符宽估算与上下标简化必须补为真实字形度量、明确 x/y 的共享 lowering。第一版支持范围须由公式金样限定，未知 TeX 回可读文本，不宣称完整 LaTeX。不得继续增加第三套公式/坐标绘制逻辑。

## 6. 交互与无障碍

原生控件为 button/toggle/range/select/transport；数据只含局部 ID、label、value、范围/选项、enabled、语义。事件为 activate/change/drag/transport，带 programId、sceneId、sceneRevision、sequence。事件值经本地校验，再调用纯 reduce；native clock 提供 timeMs。过期 revision 不写回。

事件与渲染是不同队列：reduce 单在飞、离散事件按宿主 sequence 的有界 FIFO（最多16项）执行，activate/reset/drag-start/drag-end 不合并；队列满时暂时禁用对应动作并提示，不静默丢点击。只有同一手势的绝对 range 值/drag-move 可合并，增量事件不可合并。reduce 返回值按 expectedStateRevision CAS 应用，再触发 latest-wins 绘制；绘制丢帧不得丢状态更新。重复 sequence 不重复应用。表单 submit 走下文原生幂等提交，不进入可合并绘图队列。

4E.2 另补原生表单协议 NativeTutorFormV1，作为 SceneV1 的可选 form（不是画布路径）：formId、题项 radioGroup、理由 textInput 与 submit。每表单≤4 题、每题≤6 选项、理由≤1000 UTF-16 code units，总预算仍计入控件/内容上限。formId 由宿主按程序、当前题 basis 和表单内容摘要重建；不得由模型绑定其他题/路由/工具。4E.1 不广告 form。

原生 AnswerDraft 独立持有真实选择/输入，JS reduce 不得修改作答或伪造提交。学生点击 submit 后，宿主验证会话/revision/form 摘要并通过现有 TutorInteractionCommands/TutorTurnMessages 提交一次真实学生消息（使用稳定 submissionId 去重）。选择、键入、模型预填来源分开，模型预填不能当学生原话；自动播放或 JS activate 不能触发提交。提交产生正常的学生新回合，不偷用已经结束的模型工具循环预算，也不直接写掌握度。

未提交 AnswerDraft 使用独立 uiState（不放进 sourceJs/initialState）：旋转用 SavedStateHandle 的有界临时值，停止输入500ms、选择改变或进入后台时保存到 tutor_message_visual.ui_state_json，字段同时带 formDigest/revision。只保存本表单实际输入，不生成 PNG；提交成功后同事务标记已提交/清草稿。进程重建不自动发送，表单/basis 已变则保留原草稿供查看而不套到新题。

命中测试使用 compiler 的同一变换、clip 和逆变换；最小触达区、键盘/辅助操作、阅读顺序和逐步控制必须可达。系统禁动画时停自动播放，保留 seek/逐步查看。Canvas 图不能仅有一条 contentDescription；可交互对象须有可聚焦语义节点与原生动作。

场景 purpose、关系说明、当前状态与公式可读替代必填。协议不提供 correct/mastery/score/learned 写入能力。浏览、播放、拖动只改变本图状态；答题提交由既有讲题事件绑定当前题和证据策略，JS 自报答对不得写掌握库。

strictOffline 禁止外部派发和获取新模型源码，但允许已持久程序在 API/SDK/运行库兼容且本地执行未关闭时重新布局、拖动和播放；静态冷恢复优先读快照。版本未知或执行熔断时读兼容快照/预览/文字。禁止把“离线可交互”宣传成“离线能生成新讲解”。

## 7. 模型工具环与费用

不新增TUTOR_VISUAL_GENERATE/REVIEW任务。统一决策由已有讲题/转写/IMAGE_PIPELINE_CLASSIFY任务承载，使用同一policyVersion与proposal校验；JS源码由该既有任务产生，图像方法的具体imagePrompt传给图像API。native/兼容JSON/转写parser/净化作业同步，流式残缺源码不能执行。

### 7.1 终态与多协议先决门

当前 OpenAiSse.reconstructedChatCompletion 仅重建 content，delta.tool_calls 参数不会进入终态。必须先补 OpenAI Chat Completions 原生增量聚合：按 index/id 合并 name/arguments，完整结束才解析执行，检测 arguments 超限、截断、取消、重复/冲突 index。未通过该门时，工具声明回合显式使用非流式请求，不追加一次请求探测，也不将半段参数当可执行程序；普通文本回合的流式政策保持原意。

Responses、Anthropic、Gemini 当前 supportsNativeTools=false，只走 Route B；各自 normalize/reconstructedBody 都要测试 sourceJs 原始换行、反斜杠、中文/emoji、空 content、截断和取消。不能只测 OpenAI 的两个 parser 后声称所有 Provider 已支持。strict schema 的本地校验始终最终兜底。

新增有界 TutorVisualContext，向下一次**已有预算内**模型回合携带最近一份程序源码、当前 state、截断诊断与能力版本；只提供当前题相关对象，不把历史全部源码累积回喂。上下文预算：源码 64 KiB、state 16 KiB、诊断 2 KiB，且受原请求总 token/字节预算约束；放不下时不自动重试。

统一管线每个逻辑操作最多6次Provider模型执行派发，文本决策/带图修复与图像生成/编辑共用持久ledger；JS本地执行不占远程派发。讲题预留1次终答，故余额1时撤两路工具；独立转写/净化终态本地保存，不硬留聊天位。旧“5工具轮+1终答”仅是没有额外图像执行时的上界。当前图像通道尚未接ledger，必须补接，不能只改提示词就声称已统一预算。失败/截断/重建前派发均计数，换方法不换operationId，不隐藏重试/审查请求。

JS和IMAGE_MODEL都记生图尝试/成功产物，统一FigureGenerationTrace标记method/purpose/来源/质量/状态；本地动画、预览和缓存命中不重复算新生图。外部图像调用、文本token和费用另记，JS本地执行不产生外部图像调用。不能用旧GENERATED_FIGURE资产行数定义所有生图，也不能把visualId冒充旧像素figureId；使用类型化FigureArtifactRef。所有外发仍受Provider/资产授权和strictOffline约束。

## 8. 持久化、兼容与导出

统一生图需具备公共operation/attempt记录，以及JS后端的程序/快照/引用（已有在飞账本须复用核验；下列完整合同尚未验收）：

- `figure_generation_operation`：稳定operationId、parent逻辑操作/subject/basis/purpose/slot、policyVersion、状态、质量目标、类型化最终产物；身份不包含method。
- `figure_generation_attempt`：operation FK、序号、模型所选method/理由、具体操作/输入指纹、结果/诊断/质量、远程reservation与实际费用计数。方法切换仍在同一operation，后台clean_redraw_task关联该操作，历史行非破坏兼容。

programId 与对外 visualId 在 V1 为同一个宿主分配的不可变身份；源码/初始状态/basis 改变即新 program，禁止原位覆盖已被消息或题目引用的源码。sceneRevision、generation 与 snapshotId 由宿主分配，JS 输出只可回显，不能重置或跨 program 引用。

每份图的role由宿主依据purpose/subject派生：源题重建为QUESTION_DIAGRAM，讲解为TUTOR_EXPLANATION，必要的整页清图单独标明来源范围。method不决定role；JS图或图像模型图都不能冒充未核对的原题事实，源验证状态另存。

- `tutor_visual_program`：visualId/programId、宿主 role、原始 sourceJs/SHA、initialState、API/SDK 版本、题面 revision/sourceBasis 摘要、原模型 request/provenance。
- `tutor_visual_snapshot`：program FK、规范 state/SHA、sceneSchema/renderSchema、验证后的场景与 RenderScene/SHA、题面图核对状态、timeMs/seed、runtime/compiler/font 版本、viewport/fontScale/theme、可空预览 canonical 资产 FK。
- `tutor_message_visual`：messageId+ordinal 唯一、program FK、当前 snapshot FK、可空有界 ui_state_json/formDigest/revision（4E.2 草稿）；与正文/trace 同事务写入。题目保存需另加明确的 `problem_revision_visual` 引用，禁止靠会话还活着维持已保存题的图。

研究初读为 Room 66；定稿复核时并行工作已升至 Room 67，ModelTaskRequest 仍为 17。实施在无人占用版本时迁 68/18，否则顺延并同步测试，不覆盖并行迁移。新表外键、索引、引用回收及 backup 资产枚举同时更新；会话删除不删除保存题引用。源码/state/场景各自版本从 1 起，未知版本只读缓存/fallback，不盲目执行旧源码。

JS源码独立存，PNG预览可用LOCAL_VISUAL_PREVIEW技术标签；图像模型结果走canonical像素资产。标签只说明存储来源，不定义“是否生图”或费用；统一operation/attempt负责业务计数。产物缓存键包含method及真实参数/版本/源证据；operationId不含method，切换不能重置预算。旧图像资产与JS预览通过类型化artifact引用消费，避免同图同时取vector和CLEAN_IMAGE重复导出。JS的program/state/snapshot确定性指纹规则继续适用。

### 8.1 帧、持久快照与事务

动画 Frame 是最多两份内存对象，逐帧 render 不写 Room、不生成 canonical PNG、不触发备份或 Provider。只在初次成功、range/drag 结束、播放暂停/进入后台、明确保存/导出时提交 checkpoint；连续输入合并，未完成的一次手势在进程死亡后可回到最近已提交 checkpoint，不能承诺每一帧恢复。

每 program 仅保留初始与最新两个未被其他对象固定的 checkpoint；消息/保存题/导出固定的历史快照不得回收。每 program 的可回收派生缓存≤4 MiB，全局可回收视觉缓存≤32 MiB（原始程序和被固定事实不计作可删缓存）。超限只淘汰未固定快照/预览；全部固定或磁盘不足则本次不提交并说明，不通过删用户事实腾空间。状态与 snapshot pointer 使用 program+basis+expectedRevision CAS，迟到 checkpoint 不覆盖新状态。

首张可见图随 assistant message/trace/visual link 在同一事务提交，事务前准备程序/快照或资产仅作为 staging；记录 ownerRequestId 与 STAGING/LIVE 生命周期，活动 owner 的预备产物不得被 GC。失败、取消或进程租约失效时先持久化 owner 状态并对账，确认未被消息/题目/导出引用后才清理；恢复中的操作不能因进程死亡被直接当无主。预览文件先安全写入并校验 SHA，再在 DB 事务注册 canonical/snapshot 引用；失败遗留文件由既有孤儿清理处理。保存题只增加精确 revision/role 引用；删除会话不删保存题/导出固定的快照。

请求 codec 当前 encodeDefaults/explicitNulls 均开启；新增可空上下文必须 EncodeDefault.NEVER 或精确旧 schema 投影，并扩真实旧请求/逻辑操作指纹测试，不能认为“默认 null”天然兼容。

旧 raster 资产、旧 FigureSchema、旧 attachedImages 仍可读，不批量重画。历史 attachedImages resolver 改为 cache-only，确定映射后补真实消息引用；缺失显示显式重新生成动作，打开旧消息不能悄悄出网收费。

兼容判断分层：programApi/sdk/runtime 决定能否重新执行；sceneSchema/renderSchema/font 版本决定能否重放矢量。源程序未知但 RenderScene 版本已知可只重放；RenderScene 也未知时只能用受预算校验的 canonical 预览/可读说明，不能把未知 JSON 强行交 painter。备份恢复、历史读取均重新校验 DTO/摘要/引用，不信任磁盘上自称 validated 的标记。

导出冻结 `{programHash,state,timeMs,seed,layoutEnvironment,RenderScene}`；PDF/SVG 不重新执行 JS，不重新采样曲线或移动标签。PDF 只按目标栏宽计算 ptPerDu，复用 AndroidScenePainter；SVG 序列化同一显示列表。第一版 SVG 文字可内嵌局部 PNG artifact，几何仍为矢量；不承诺字体全矢量或跨机字体一致。PDF 附可见说明，不声称已有 tagged PDF 无障碍。

普通练习卷题目区只使用绑定当前题目 revision、通过核对的 QUESTION_DIAGRAM；TUTOR_EXPLANATION 只在用户明确启用解析区时加入该区，不能因“同题有图”把含解题过程/答案的讲解图塞进题目区。4E.1 必须接 PDF；SVG writer 是 4E.2 的附加交付，不因此新增产品导出入口。

字体身份必须来自可验证资源清单。V1 对无法稳定识别的系统 fallback/emoji，不承诺冻结 text runs 可跨系统无损重建，须保存所需文字局部栅格或整图预览并使用相同源 artifact 回放/导出；受累计像素预算约束。不能只存 Typeface 名称便称字体版本精确匹配。SVG 外部规范画布标签可用这些局部 PNG。

图题核对未通过时不替换原题/原图，不编造缺失标注。语义重建、照片净化、文字识别、切分各有独立冻结数据和质量门。普通生图的“成功”不是数学正确证据。

## 9. 启用与退出门

所有生图入口先接4E.1统一决策/协调器，可先以已有图像方法验通，再在JS静态后端过门后并列广告两方法；不能把某用途永久留在旧专线。4E.2扩交互/表单，4E.3扩复杂来源/保真质量。每个方法/特性仍须真实可用才广告，未过门返回不可用，不伪造能力。完整门包括：源码固定与许可证；API 23/arm64/API 35+16 KB/R8；隔离启动、无网络/文件能力、引擎崩溃和 watchdog 恢复；字节/几何/字体/像素预算；两种工具协议和六次总预算；旧请求指纹、全历史迁移、备份/GC；六类截图样例与一个新算法组合样例；360/600/840 dp、fontScale 1/1.3/2、TalkBack；同快照屏幕/PDF/SVG 一致；真实 Provider 生成与修复率另测。

任何门未过保持文字/公式及已有图片读取可用，明确该能力未就绪。方案、研究探针、正式功能的完成状态分开登记。

本轮审查发现、代码证据与修订范围见[计划复核报告](../research/2026-10-18-flexible-js-drawing-plan-review.md)。

同日最新方法规则以[统一生图提示词](figure-generation-prompt-policy.md)为准；此前研究/审查中的“固定JS路线、照片专走edits、不计JS生图”等旧方案仅保留历史证据。

2026-10-19产品质量补充：工程门之外，必须按[闭环设计§6](../superpowers/specs/2026-10-19-product-readiness-design.md#6-统一生图的质量与启用)和[PRD-09](../superpowers/plans/2026-10-19-product-readiness-followup-plan.md)执行冻结任务集、独立图义预期、首次/预算内成功率、延迟与成本验收。方向错误不能由文字免责声明抵消；原生表单另验。先交付静态纵向闭环再完成全部WG，不新增按用途固定后端规则。相关数值为待验证启用目标，非已有测量结论。

2026-10-19源码进度以[研究§1.1](../research/2026-10-19-product-readiness-research.md#11-最终核验时的工作树增量)为准。类型/policy与IMAGE_MODEL协调器存在不代表JS运行库或全入口接线已完成；实际能力广告仍由运行库与工程/质量门决定。

## 10. UI呈现承接（2026-10-19）

[UI设计§4.6](../superpowers/specs/2026-10-19-ui-ux-redesign-design.md#46-讲解图公式和原生交互)及[UI-08](../superpowers/plans/2026-10-19-ui-ux-redesign-plan.md)统一图卡的加载/失败/旧版/静态回退、公式缓冲、放大/文本替代与原生表单。仅统一容器和操作边界，保留模型自由绘图，不新增固定题型组件或按用途选择后端。恢复、历史和导出只消费已有产物；显式重生成仍走原公共策略，授权预算内换法不增加确认。

已有文字/公式/原生控件可独立修UI；新静态图按4E.1、生成式互动/表单按4E.2开放。图像放大不能替代必要的可读说明；布局合格不能替代PRD-09图义门。
