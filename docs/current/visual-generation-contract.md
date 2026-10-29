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
- 发布 AAR（quickjs-kt-android 1.0.15）四 ABI 的 PT_LOAD 均为 16 KB 对齐；GNU_RELRO 终点非 16 KB 整倍数（arm64 余 4096、x86_64 余 12288）但 GNU_RELRO 与承载它的第二个可写 PT_LOAD 完全同段（官方所称 "in its own LOAD segment"）：按内存/虚拟地址视角，该 16 KB 余数页内没有可写映射——第三个可写 LOAD 从下一页才开始（arm64 vaddr `0xc8788`、x86_64 `0xd2d78`）；"第三个可写 LOAD 与 RELRO 同页"只在文件偏移视角成立（其起点恰为第二个 LOAD 的文件终点：arm64 `0xc0788`、x86_64 `0xcad78`，与 RELRO 的文件字节同处一个 16 KB 文件页）。故按 [Android 官方检查要求](https://developer.android.com/guide/practices/page-sizes#check-relro) 明文豁免判合规——"If the GNU_RELRO segment is in its own LOAD segment, the non-zero value returned is still compliant"——不构成阻断门；逐 ABI 实测与判据原文见 [16KB 研究](../research/2026-10-25-16kb-arm64-api23-build.md) §2.4/§1.5（该文脚注沿用旧缩写"余数页内无可写段"，以本句的内存视角为准）。本行按批次 21 复核发现 #23 修正原措辞（依据见 `../research/2026-10-25-batch-21-record.md` §5 #23；T7 复读 APK 内 `libquickjs.so` 的 ELF 程序头核对了上述两组地址）。源码重建前置改判为消费发布 AAR（主会话裁定 2，2026-10-25：钉版+构件 sha256 记录，自建 AAR 仅作运行时实测失败回退）；16 KB 真机加载、API 23/arm64 运行时、R8 后 JNI 面仍待设备轨验证，仍是启用前门。
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

**终止层落地状态（2026-10-26 批次 22 T1 落层；批次 23/24 设备复跑回填；批次 25 T2 warm-up/ready 与 T3 队列/背压代码落层（设备验证前不改变广告门）；按动作分 L1/L2/L3，旧文献的"第三层"一律指 L2，不再用层号混称）。** L1 协作中断（100 ms）已在 core:data 引擎落地（`evaluationTimeoutMillis` + 协程取消 → `PHASE_TIMEOUT`），但对 issue #240 的自续 job 不构成硬终止。L2 服务硬终止（1500 ms）**代码已落层，设备用例已验证 watchdog/重建路径（仍未生产装配）**：app 的 `visualjs.VisualJsService` 以 `android:isolatedProcess="true"` + `android:process=":visual_js"` 注册，AIDL `IVisualJsService`/`IVisualJsCallback`、watchdog 令牌 CAS、`killProcess(myPid())`、过期 `terminate(generation)` no-op 均已实现（`app/src/main/AndroidManifest.xml`、`app/src/main/aidl/com/tingyun/smartmistakebook/visualjs/`）；2026-10-26 复核曾用 `aidl.exe` 单文件编译两份 AIDL（均 exit=0；此前 `IVisualJsCallback.aidl` 被误覆盖成 `IVisualJsService` 的生成 Java stub，已恢复，见 KD-66 Progress）；**批次 23 起 AIDL 门一律以 Gradle 任务验证**，`aidl.exe` 单文件结果只作历史证据、不再作为本批通过判据；watchdog 在引擎 `execute` 前 arm 一次、请求收尾 disarm（契约原文"每个 JS 阶段开始前"在实现里取整个请求的 JS 工作起点，R1 `../research/2026-10-26-visualjs-isolated-process.md` §5.1）；`SmartMistakeBookApplication.onCreate` 在打开 Room/备份/Provider 前用 `Process.myUid() != applicationInfo.uid`（API 28+ 叠加 `Process.isIsolated()`）短路。**设备轨已执行（2026-10-26 批次 23；E-01b/E-01e）**：`VisualJsIsolationInstrumentedTest` **5 例全部执行，4 绿 1 红**（批次 23 时点）。绿：`workerIsIsolatedAndAProgramRoundTripsWithoutBeingKilledLater`（隔离 UID + AIDL 往返成功）、`aStaleGenerationIsRejectedAndAStaleTerminateIsANoOp`、`theWatchdogKillsAHungWorkerAndAFreshGenerationHasNoBusyState`（1500 ms 硬期限 SIGKILL + 新 generation 无 BUSY）、`theClientRebuildsOnceAfterADeathAndTheNewWorkerIsNotBusy`（死亡后重建一次，新 worker 无 BUSY）。红：`theClientRebuildsAtMostOnceAfterConfirmedAbandons`（`the first abandon did not confirm death`）——`VisualJsClientEngine.abandon()` 先 `unlinkDeath` 再等 `link.death`（`VisualJsClientEngine.kt:570-571`），服务已 SIGKILL 而客户端 1 s 内收不到 death；E-01c 单方法稳定复现。加载态对照：冷启动后立即跑 5/5 红（attach 4.6–7.5 s > 5 s，E-01d），沉降约 2 min 后回到 1/5 红（E-01e）。**批次 24 E-01/E-01b 已在两代 debug APK 上各跑一遍，均 6/6（`OK (6 tests)`；`Time: 23.945` / `22.208`）**：abandon 用例转绿（重建后真实 `execute` 返回 `Rendered`，第二次确认后不再 bind）、7.6 s 冷启动 attach 用例通过、`death was not confirmed` 告警 0；证据 `../research/evidence/2026-10-26-batch-24/step01-visualjs-isolation.md:124`、`step01b-visualjs-isolation-rerun.md:102`、`00-index.md:43-44`。**因此"复跑 UNVERIFIED / 第 5 例未执行 / watchdog 与重建无绿证据"的旧表述作废**；隔离进程内 `libquickjs.so` 装载与 `killProcess` 权限已由 watchdog/往返用例绿证明。ContentProvider/AndroidX Startup 的隔离进程装配面仍未 `dumpsys` 审计（该缺口已登记为 4E.2 准入门 P-08，见 [`../research/2026-10-26-batch-24-4e2-admission-checklist.md`](../research/2026-10-26-batch-24-4e2-admission-checklist.md)）。L3 宿主阶段期限（2500 ms）**仅代码落层且无生产装配**：app 的 `VisualJsClientEngine` 在期限到点后 `abandon()`（ABANDONED → `terminate(generation)` oneway → `unbindService` → **保留 death 注册**并以 binder death 事实 + 轮询 `isBinderAlive` 组合确认 ≤2 s），确认死亡后清空 link、由下一次执行扣唯一一次重建预算（复核 #12 后预算在 `connectLocked` 统一扣；未确认才 fail-closed，不并发放大进程数），再死即熔断；**调用方上界不是 2500 ms**——`ensureConnected()` 的连接预算在期限之外：首连 15000 ms（批次 24 由 5000 ms 延长；设备实测冷启动 attach 4.6–7.5 s，旧 5 s 会把加载态误判成结构性失败，E-01d），重建 10000 ms；期限到点后的 `NonCancellable` 放弃还要等死亡确认 ≤2000 ms，最坏约 29.5 s（首连 15 s + warm-up ready 10 s + 宿主阶段 2.5 s + 死亡确认 2 s，另加 Binder 开销；ready 预算只在每次新进程 bind 后等待一次，已 ready 的连接复用不重付）；冷启动首个 execute 的 1500 ms watchdog 误杀（E-01d）缺口已登记 KD-76（**隔离门 6/6 绿不得作为 JS 广告依据**）；warm-up/ready 已按 KD-76 ①方向落服务端代码层（批次 25 T2，无生产装配；批次 25 设备轨注入形状首验绿——warm-up 中 acquire 有界拒绝、attach/ready 超时 loading 不锁存两类均绿（E-01 `step01-visualjs-isolation.md`），KD-76 ③ 真实冷启动首请求未达、④ 负控计时弱口径（≤6 s）、⑤ UI/文案面未做，设备判据归批次 26 T2；批次 26 设备轨已出数（E-01/E-02/E-03，批次 27 T5 补记）：③ 冷启动轮（boot 后不沉降，loadavg 0.75）13/13 全绿、零意外杀/零首请求杀，但负载轻于批次 23 E-01d、调用方为测试 APK 内生产 client 引擎、29.5 s 链与低内存 UNVERIFIED——不判定达成；④ 强证据——warm 服务挂死被 1500 ms 硬期限 SIGKILL（kill 行实测提交后 ~1.52–1.55 s）+ 调用方 hard+500 ms 有界返回三轮绿（弱口径解除）；⑤ 行为层加载态三用例沉降/冷启动两轮全绿，UI/文案面与 R3 广告门文本升级仍未做（`../research/evidence/2026-10-27-batch-26/step03-kd76-criteria.md` §5/§7；warm-up 实测装载成本已由批次 27 设备轨出数（E-04 [`../research/evidence/2026-10-27-batch-27/step04-warmup-cost.md`](../research/evidence/2026-10-27-batch-27/step04-warmup-cost.md)：控制组 n=10 p50=686/p95=725/max 738 ms、套件 churn 组 n=16 中位 675.5 ms、26/26 worker 全部 ready，口径=AMS `Start proc`→`warm-up finished`；x86_64/API 34 模拟器口径，arm64/真机/低内存/收益半边 UNVERIFIED——`step04` §6；KD-76 本体维持 open））：`VisualJsService.onCreate` 在任何请求前经单线程执行器完成一次性 `QuickJs.create` 装载 + 空程序 evaluate 后置单调 `ready`，ready 前 `execute` 拒绝（进不了请求路径即无 watchdog arm 点），AIDL 新增 `isReady()`（`PROTOCOL_VERSION` 1→2，两端同 APK 发布、旧新组合仍在握手 fail-closed 解绑），宿主在握手后轮询 ready（间隔 100 ms、预算 10 s——待设备校准起点，独立于 attach 预算与 2500 ms 阶段期限），attach/ready 预算超时按 **loading** 如实失败（`ENGINE_CLOSED` + loading/not-ready 诊断），不锁存 `unavailable`、不扣死亡重建预算，下一次 execute 重新 bind + 等 ready（KD-76 ⑤；生命周期限次属 P-04；死亡路径上的重建仍按既有"最多重建一次"记账）；KD-76 的设备判据部分验证（④ 强证据、⑤ 行为层 ×2 轮绿；③ 冷启动轮全绿零意外杀但不判定达成——产品进程/UI 首请求与 29.5 s worst-case 链 UNVERIFIED），**KD-76 保持 open、设备验证前 JS 不广告**；并发调用的队列/背压已按 §64 落客户端层（批次 25 T3，`VisualJsFrameQueue` + `VisualJsClientEngine`，无生产装配；设备侧仅服务端层 BUSY/有界读两例首验绿（E-01），经 `engine.execute` 的客户端队列负向批次 26 T2 亦未执行（批次 26 记录 §6 #13），仍缺）：全局 1 在飞 + 最多 2 待绘、同 program latest-wins（被顶掉的旧等待者与满队列的第 3 个调用方均回 `BUSY` 背压拒绝、在飞与待绘帧保留）、跨 program FIFO；会话在常驻消费者侧执行，排队等待上界由"1+2 帧 × 会话预算"约束（不再是旧 `executeMutex` 的无上界挂起）；worker 死亡时排队帧清空回 `ENGINE_CLOSED`（R1 §3.2 保守分支，待主会话确认）；调用方取消只出队自己的等待帧（排队帧出队不执行）、取消在飞帧不再 abandon worker、不打断在飞执行（会话按自身预算跑完、结果丢弃；主会话已裁定确认采纳——批次 25 记录 §3.3 ②，批次 27 T4 定稿；死亡清队仍待主会话确认，见前句）；服务端单在飞 `STATUS_BUSY` 保留为纵深防御；"满队列回 BUSY 后由可见/交互生命周期限次重新调度"的后半句属调用方生命周期层（P-04），尚未落地。§64 另有两个开口项**列主会话待裁**（批次 27 T4 登记，防随批次收口被无声丢弃——R23/批次 25 记录 §6 #9；R1' [`../research/2026-10-27-visualjs-warmup-queue.md`](../research/2026-10-27-visualjs-warmup-queue.md) §4 A-4/§6 #8 记两读法并存、需定谳后才能写断言，R2 审计无 phase 内容）：① `:64` 的 `phase` 字段语义——读法一"初生成 vs 重绘帧/帧序"需再裁定映射 `sceneRevision`（已在信封内）或新增字段（`PROTOCOL_VERSION` 再升，断言=队列内同 program 新帧 revision 单调递增）；读法二 §3.2 事务阶段（prepare/measure/render）则是服务端内部事实、无需 wire 传输；wire 请求信封现无 phase 字段（`VisualJsProtocol.kt` 请求信封仅 programJson{programId, sourceJs, revision, stateJson|null, viewport, timeMs, seed, theme, explicitUserRetry}+limits），定谳前不写断言、不加字段；② `:70` 软期限句（宿主字体/路径编译的期限是结果作废与背压的软期限）在准入清单无对应行、KD 无条目，是否/如何落门待主会话定谳。JS 仍 UNAVAILABLE、不广告：L3 客户端没有生产装配点，服务内内容度量已接线（2026-10-27 批次 27 T2：`AndroidVisualContentMeasurer`——text=Paint 内蕴度量（显式 `\n` 分段，无自动换行）、math=core:model 盒布局链 + 契约降级为可读文本，warm-up 探针把字体子系统首初始化移出 1500 ms 窗口；隔离进程字体/资源面（R2 `2026-10-27-content-measurement.md` 假设 A1）与设备轨度量往返仍未验证），4E.2 交互/表单未做。**KD-66 已于 2026-10-27 批次 26 关闭**（关闭门原文不变：“issue #240 形状在 hard+500 ms 内返回拒绝且后续请求可用”的设备证据，见 [`../known-defects.md`](../known-defects.md) KD-66）：批次 26 T1 落码的专属计时用例 `aHungWorkerReleasesTheCallerWithinHardPlus500ms` 三次独立运行全绿（批次 26 E-01 套件轮 + E-02 聚焦轮 ×2，断言 A1–A7 无放宽，[`../research/evidence/2026-10-27-batch-26/step02-kd66-hard-500ms.md`](../research/evidence/2026-10-27-batch-26/step02-kd66-hard-500ms.md)），记录回链 [`../research/2026-10-27-batch-26-record.md`](../research/2026-10-27-batch-26-record.md) §3.5；此前批次 23/24 的 watchdog 证据按 R4 [设备验证证据落盘规范](../research/2026-10-26-device-evidence-convention.md)落盘但以 ≤6 s 判死、弱于 hard+500 ms（不构成关闭证据）；issue #240 上游仍 OPEN、daemon 线程泄漏仍是登记形态（accept-the-leak 不变），JS 不广告不变（KD-76 open）；`abandon()` 的死亡确认缺陷已由批次 24 修复代码并补用例（**批次 24 E-01/E-01b 设备复跑 6/6，旧 UNVERIFIED 作废**），因此本节仍不得读成“进程级硬终止已验证”。

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

**DrawOps V1.1 生成门与回放语义（2026-10-28 批次 29 T5 落码、T6 台账同步，[KD-78](../known-defects.md)）.** 以下五条把"两道生成门（`visual-compiler.js` 与原生 `VisualDrawOpsParser`/`VisualDrawOpsValidator`）与回放 painter"钉成同词；回放侧对应门保留为绕过生成门的兜底，不自愈：

- **8 位色 alpha**：`#RRGGBBAA` 的末两位是色内 alpha，**可**与 op/组 alpha 相乘——回放有效 alpha = `floor(colorA × op.alpha)`（`colorA ∈ [0,255]`；6 位色取 255，等价 `floor((colorA/255) × op.alpha × 255)`）；painter 在 `setColor` 之后以该值 `setAlpha`。因此 `#RRGGBBAA` 与组 alpha 是乘法，不是"组 alpha 覆盖色内 alpha"。
- **dash 形状**：`dash` 必须为**偶数项且 ≥2**；奇数或 <2（含空数组 `[]`）在两道生成门即拒（码 `PAINTER_DASH_ODD`），回放侧同码兜底（AOSP `DashPathEffect` 构造器对 `length<2` 抛无码 `ArrayIndexOutOfBoundsException`，奇数进 native 行为无规约）。
- **stroke-only**：`stroke` 无 color 字段，路径**必须有 `fill`**（stroke 色取 fill 色）；`fill=null && stroke!=null` 在两道生成门即拒（码 `PAINTER_STROKE_WITHOUT_FILL`），回放侧同码兜底。
- **度量/回放 Paint flags**：度量环境指纹包含 `paintFlags`（`typefaceId|sizeSp|density|fontScale|locale|paintFlags`），**固定值 0**（AA/locale/subpixel 全关的 MVA 取舍）——它**不是**平台 `Paint()` 的构造默认：API 34 实测默认为 **1283**（`ANTI_ALIAS|FILTER_BITMAP|DEV_KERN|EMBEDDED_BITMAP`，批次 29 设备证据 R1）。度量侧 `forEnvironment` 与回放侧 `textPaint` 都必须在构造后**显式** `flags = 0`（`DEFAULT_PAINT_FLAGS` / `REPLAY_TEXT_PAINT_FLAGS`）；装配时校验实际度量 Paint 的 flags 与指纹一致（不一致 fail-closed），回放侧同值并与指纹同值——改任一侧必须同时改另一侧与指纹。
- **hitRegions**：`hitRegions[]` **可非空**——编译器/解析器只做数组 + 预算校验并透传进 DrawOps 文档；屏与 PDF 的 painter 都**不绘制**命中区，命中测试由交互层按同一变换/clip/逆变换消费（批次 29 §1.1 裁定 1）。空数组仍合法，非空不是错误，也不得在回放侧被当作绘图 op。此前"hitRegions 必须为空"的口径作废。

RenderScene 是校验后的不可变显示列表：固定路径、矩阵/裁剪、已放置 glyph/line/path 或文本 artifact、语义与命中区。不可包含函数、脚本或回调代码。`core:model` 的协议与 compiler 不依赖 Compose；`core:visual` 的 AndroidScenePainter 共用于 Compose nativeCanvas 和 PDF page.canvas。

原生执行编排由 app 的 AndroidVisualExecutionEngine 实现 core:domain.VisualExecutionEngine 端口，组合 core:visual-js 客户端、core:visual 内容布局和纯 compiler。core:data 只通过该端口做来源/幂等/持久化/CAS，不直接引用 AIDL/原生排版。core:ui/export 只依赖 core:visual 回放，不依赖 JS worker。依赖图见[架构契约](architecture-contract.md)，避免把布局插件硬塞入数据层或让导出需要脚本执行器。

### 5.1 目标架构 vs 现状（批次 22 登记，批次 23 只读复核）

> 本小节只做对照，不改上文处方、端口边界或归位条件，也不新裁架构。批次 22 的偏差登记原文见[决策台账](../agent-first-refactor-decisions-2026-09-23.md)的“批次 22 T1 架构偏差登记”节；其中“直接引擎落 `core:data`”是批次 21 已登记、批次 22 继续有效的偏差（同节）。架构契约已把视觉运行时的目标边界写成“target boundary, not a claim that the new modules exist”（[架构契约](architecture-contract.md) §Planned visual runtime）。

| 契约 §5 处方（目标） | 现状（2026-10-26 批次 23 只读核对；2026-10-27 批次 27 T2 更新内容度量相关两行；2026-10-28 批次 28 T3 更新 painter 落层注记；2026-10-28 批次 29 T6 更新 PDF 半场与一致门原型；2026-10-29 批次 30 T12 回改 painter/parity/PureTest 例数与 PDF 行数；2026-10-29 批次 31 T5 回改现树行数与 parity 例数） | 证据 |
|---|---|---|
| app 的 `AndroidVisualExecutionEngine` 实现 `core:domain.VisualExecutionEngine` 端口，组合客户端、内容布局与纯 compiler | 不存在 `AndroidVisualExecutionEngine` 类；app 现有 `VisualJsClientEngine`（实现端口）与 `VisualJsService`（L2 服务），没有把三层组合成一个 app 端编排器 | `grep -rn "class AndroidVisualExecutionEngine"` → 0；`app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsClientEngine.kt:79`；`app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsService.kt:310` |
| 客户端落 `core:visual-js` 模块 | 不存在 `core:visual-js`；Service、AIDL、宿主 client 全在 app 模块，未新增 Gradle 模块 | `settings.gradle.kts:19-34`（无 visual 模块）；`app/src/main/aidl/com/tingyun/smartmistakebook/visualjs/`、`app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/` |
| 直接引擎由 app 侧编排；`core:data` 只经端口做来源/幂等/持久化/CAS | 直接引擎 `QuickJsVisualExecutionEngine` 落 `core:data`，由 app 的 Service 直接构造（`measurer = AndroidVisualContentMeasurer`，批次 27 T2 起为真实度量；此前为 `UnwiredVisualContentMeasurer` fail-closed 桩）；它不引用 AIDL/原生排版，契约给 core:data 的两条红线未破 | `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/QuickJsVisualExecutionEngine.kt:141-150`；`app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsService.kt:405-416`；core/data 与 core/domain 对 `IVisualJs`/`visualjs.` 零命中 |
| `core:visual` 承担内容布局与回放（`AndroidScenePainter` 共用 Compose/PDF） | 不存在 `core:visual` 模块；`AndroidScenePainter` 零命中；内容度量已接线（批次 27 T2 `AndroidVisualContentMeasurer`：text/math 真实度量；断行 artifact 留在 app visualjs 内部 seam（`VisualMeasuredLayout`），未抽 `core:visual`）；**painter 落层已裁定（批次 28 T3 同步）**：DrawOps V1→Canvas 映射 painter 落 `core:data` `visual` 包过渡态（设计研究 `../research/2026-10-28-painter-design.md` §3.1：`VisualDrawOpsPainter.kt` `internal`、纯计算层与 android.graphics 应用层分离、矩阵镜像 + clip/label 根空间 setMatrix 舞步），不新建 `core:visual` 模块（`settings.gradle.kts` 热区）；落码（批次 28 T2）已入树：`VisualDrawOpsPainter.kt`（批次 28 T2 落码 311 行；批次 31 T5 `wc -l` 现树 **334 行**）/`VisualDrawOpsPainterPure.kt`（187 行；现树 **228 行**）/JVM 纯测 10 例（批次 29 T5 后 **13 例**）/仪器化套件 19 例（批次 29 T5 后 21 例：3 例因 KD-78 ①② 生成门改 hand-built 兜底夹具、+2 例 8 位色 alpha 与回放 flags；复核修复轮再 +3 例：门序组合负例 1 + `drawRendered` 2，现树 24 例），设备冒烟 **17/17 绿**（2026-10-28 批次 28 E-03，先红后绿为夹具建位图顺序缺陷、产品源零改动；`../research/evidence/2026-10-28-batch-28/step03-painter-smoke.md:45`）——原"落码与设备冒烟未出数/类名全仓零命中"是本表 T3 注记时点（批次 28 记录步补记，2026-10-28）；修复轮新增的两例（残尾 dash/空 dash）设备复跑 UNVERIFIED；**PDF 半边（批次 29 T3）已落码**：`VisualDrawOpsPdfPainter.kt`（**258 行**，批次 31 T5 `wc -l` 实测；批次 30 T12 的 154 行为 T5 `writeFile`/`PdfWriteIntegrity` 接入前时点，批次 29 注记的 104 行为 KD-79 跟踪流修复前时点）/`VisualDrawOpsPdfPainterPure.kt`（92 行）复用同一 `VisualDrawOpsPainter.draw`，`ptPerDu` 只替换 `pxPerDu`（由目标栏宽计算），JVM 纯测 6 例 + 仪器化 **16 例**（批次 30 设备复跑 `OK (16 tests)`）；**屏/PDF 一致性原型门（批次 29 T4）** `VisualDrawOpsParityInstrumentedTest` 10 例（批次 31 T5 现树 **15 例**：+N08 与 `calibrated-r1` 新增 N09–N12 负控；设备已实跑 `Tests run: 15, Failures: 1`——失败为 N09-P06 旧的 `centroid=1.000` 等式（A 类，门级已 `red=true` 且理由 `bboxDev=2px > 1px`），`PARITY_CONTROL` 14/14 红、N09-P07 中止未执行，绿轮未出数），类头自定位"原型门，不接产品/CI/发版门"（`../research/2026-10-28-screen-pdf-parity-gate.md`；阈值标定 `../research/2026-10-29-parity-calibration.md`：TEXT 质心 0.5→**0.75 px**、墨量比 0.95/1.05→**0.93/1.07**、TEXT 的 F5 归零，`THRESHOLD_VERSION = calibrated-r1`；**落码在树；设备实跑 FAIL 14/15（E-08/E-08b，A 类归因：N09 旧等式；`calibrated-r1` 绿轮 UNVERIFIED）**，仍是原型门）；**批次 29 设备证据已落盘**（`../research/evidence/2026-10-28-batch-29/00-index.md` §3/§9）：painter 21 例 20 绿 1 红（④ flags 观测面 `expected 0 / actual 1283`，`../research/evidence/2026-10-28-batch-29/step01-painter.md:50`）、PDF 13 例 12 绿 1 红（`pdfWriteFailureIsCoded`，`../research/evidence/2026-10-28-batch-29/step04-pdf-parity.md:49-52`）、一致门原型 10/10 绿（**原型门，非产品/发版门**，`../research/evidence/2026-10-28-batch-29/step04-pdf-parity.md:96`）、app flags 2 例 1 红（`../research/evidence/2026-10-28-batch-29/step03-parser-t5-device.md:72-80`）；红因与修复建议见该索引 §4/§9（R2 排序 `../research/2026-10-28-mva-remaining-order.md` §0 序 1） | `settings.gradle.kts:19-34`；`grep -rn "class AndroidScenePainter"` → 0；`app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualContentMeasuring.kt`；`VisualJsService.kt:89,149-151`；painter 设计 `../research/2026-10-28-painter-design.md`（批次 28 T2 设计研究，落码未发生时点） |
| `core:ui`/`core:export` 只依赖 `core:visual` 回放，不依赖 JS worker | 对 `QuickJs`/`visualjs`/`VisualExecutionEngine` 零引用（“不依赖 worker”成立）；但 `core:visual` 尚不存在，回放依赖尚未建立 | `grep -rn "QuickJs\|visualjs\|VisualExecutionEngine" core/ui/src core/export/src` → 0 |
| JS 未广告、无生产装配 | `VisualJsClientEngine` 无生产构造点（仅仪器化用例构造），因此 `VisualJsService` 也无生产 bind 入口；JS 仍 UNAVAILABLE | `VisualJsClientEngine.kt:71`；`app/src/androidTest/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsIsolationInstrumentedTest.kt:84-85`；[KD-66](../known-defects.md) |

归位条件不变（沿用批次 22 登记）：4E.2 真正开放 JS 前，把 Service/client/AIDL 迁入 `core:visual-js`（需改 `settings.gradle.kts`，须热区空出）；端口与 wire 协议不变。本批没有新增模块、没有改 `settings.gradle.kts`；`core:visual`/`AndroidScenePainter` 的缺口不是本批新裁定，按 §9 启用门继续跟踪。（2026-10-28 批次 28 T3 注记：painter 落点已裁定 `core:data` `visual` 包过渡态——见上表行；`core:visual` 模块归位随 Service/client/AIDL 迁入同一热区窗口，本条件不变。DrawOps V1 生成门/回放语义四缺口——stroke-only 无颜色源、dash 形状门未钉（偶数/最小条数）、`#RRGGBB(AA)` 与 `op.alpha` 优先级未定义、measure/replay flags 不在环境指纹——已登记 [KD-78](../known-defects.md)。**批次 29 T5 已按 §5 的 DrawOps 条文收口**：① 生成侧两道门拒 stroke-only（`PAINTER_STROKE_WITHOUT_FILL`）、② 拒奇数/`<2` dash（`PAINTER_DASH_ODD`）、③ 8 位色 alpha 取 `floor(colorA × op.alpha)` 乘法语义、④ 度量环境指纹加 `paintFlags`，两侧 Paint 显式置 0（平台默认 1283，不是 0）；回放侧对应门保留为兜底。KD-78 仍 open 的唯一缺项是 ③ 的设备像素级正/负例（已写入 `VisualDrawOpsPainterInstrumentedTest`，未上设备）——本注记不改上列处方。**（2026-10-28 设备轨后更正）**：③ 的设备像素正/负例已上设备并绿，唯一红移为 ④ 回放 flags 观测面（`expected 0 / actual 1283`，平台 `Paint()` 默认 flags 非 0），见 `../research/evidence/2026-10-28-batch-29/00-index.md` §9.1。**批次 29 T6 同步**：hitRegions 口径按计划 §1.1 裁定 1 写入 §5（可非空、painter 不绘制、交互层消费，旧"必须为空"口径作废）；PDF 半边与一致门原型已落码（见上表行）；设备面（painter 21 例 / PDF 13 例 / 一致门 10 例 / app flags 2 例）**已上设备并落盘**（`../research/evidence/2026-10-28-batch-29/00-index.md` §3：painter / PDF / app flags 各 1 红，一致门原型 10/10 绿），KD-78 维持 open（④ 设备面红，见该索引 §4 R1）。**（2026-10-28 复核修复轮）**：④ flags 已改为两侧构造后**显式**置 `REPLAY_TEXT_PAINT_FLAGS`/`DEFAULT_PAINT_FLAGS`（0），painter 门序（dash 先于 stroke-only）与两道生成门对齐，PDF 写侧新增 `PdfWriteFailureTrackingStream` 关闭 fail-open（[KD-79](../known-defects.md)），`drawRendered` 补 2 例设备判据；painter 现树 24 例、app flags 2 例、PDF 13 例、隔离 13 例的修复后绿轮均 **UNVERIFIED**（本轮零 Gradle/零设备）。**（2026-10-29 批次 30 设备轨）**：修复后绿轮已出数——painter 24/24、app flags 2/2、隔离 13/13、PDF **16/16** 全绿（`../research/evidence/2026-10-29-batch-30/00-index.md` §2），KD-78 ④ 与 KD-79 台账最低口径由该轨判定满足；parity 11 例仍 1 红（A 类：原型门初始阈值未标定，未放宽），备份 34 例 8 红（D-01 schema 70 未同步备份计划/裁剪器，已在复核修复轮同步）。`VisualDrawOpsPdfPainterInstrumentedTest` 现树 16 例。）

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

每 program 仅保留初始与最新两个未被其他对象固定的 checkpoint；消息/保存题/导出固定的历史快照不得回收。每 program 的可回收派生缓存≤4 MiB，全局可回收视觉缓存≤32 MiB（原始程序和被固定事实不计作可删缓存）。超限只淘汰**未被固定、kind ∈ {NULL, LATEST}、且仍为 STAGING 的中间态**快照/预览；**LIVE（用户已经看到的图）永不淘汰**——它不是"没人引用的缓存"，`checkpoint_kind` 只表达角色，与是否可见是两轴（批次 31 裁定，`TutorVisualDao.evictUnpinnedCheckpoints` / `VisualCheckpointRetention` 的 `evictable` 均以 `status = STAGING` 为正向条件，未知 status/kind fail-closed 保留）。全部固定或磁盘不足则本次不提交并说明，不通过删用户事实腾空间。状态与 snapshot pointer 使用 program+basis+expectedRevision CAS，迟到 checkpoint 不覆盖新状态。

首张可见图随 assistant message/trace/visual link 在同一事务提交，事务前准备程序/快照或资产仅作为 staging；记录 ownerRequestId 与 STAGING/LIVE 生命周期，活动 owner 的预备产物不得被 GC。失败、取消或进程租约失效时先持久化 owner 状态并对账，确认未被消息/题目/导出引用后才清理；恢复中的操作不能因进程死亡被直接当无主。预览文件先安全写入并校验 SHA，再在 DB 事务注册 canonical/snapshot 引用；失败遗留文件由既有孤儿清理处理。保存题只增加精确 revision/role 引用；删除会话不删保存题/导出固定的快照。

请求 codec 当前 encodeDefaults/explicitNulls 均开启；新增可空上下文必须 EncodeDefault.NEVER 或精确旧 schema 投影，并扩真实旧请求/逻辑操作指纹测试，不能认为“默认 null”天然兼容。

旧 raster 资产、旧 FigureSchema、旧 attachedImages 仍可读，不批量重画。历史 attachedImages resolver 改为 cache-only，确定映射后补真实消息引用；缺失显示显式重新生成动作，打开旧消息不能悄悄出网收费。

兼容判断分层：programApi/sdk/runtime 决定能否重新执行；sceneSchema/renderSchema/font 版本决定能否重放矢量。源程序未知但 RenderScene 版本已知可只重放；RenderScene 也未知时只能用受预算校验的 canonical 预览/可读说明，不能把未知 JSON 强行交 painter。备份恢复、历史读取均重新校验 DTO/摘要/引用，不信任磁盘上自称 validated 的标记。

导出冻结 `{programHash,state,timeMs,seed,layoutEnvironment,RenderScene}`；PDF/SVG 不重新执行 JS，不重新采样曲线或移动标签。PDF 只按目标栏宽计算 ptPerDu，复用 `core:data` `visual` 包的共享 `VisualDrawOpsPainter`（屏 `Canvas` 与 `PdfDocument` page.canvas 是同一入口，批次 29 T3 落码 `VisualDrawOpsPdfPainter`）；SVG 序列化同一显示列表。第一版 SVG 文字可内嵌局部 PNG artifact，几何仍为矢量；不承诺字体全矢量或跨机字体一致。PDF 附可见说明，不声称已有 tagged PDF 无障碍。

普通练习卷题目区只使用绑定当前题目 revision、通过核对的 QUESTION_DIAGRAM；TUTOR_EXPLANATION 只在用户明确启用解析区时加入该区，不能因“同题有图”把含解题过程/答案的讲解图塞进题目区。4E.1 必须接 PDF；SVG writer 是 4E.2 的附加交付，不因此新增产品导出入口。

字体身份必须来自可验证资源清单。V1 对无法稳定识别的系统 fallback/emoji，不承诺冻结 text runs 可跨系统无损重建，须保存所需文字局部栅格或整图预览并使用相同源 artifact 回放/导出；受累计像素预算约束。不能只存 Typeface 名称便称字体版本精确匹配。SVG 外部规范画布标签可用这些局部 PNG。

图题核对未通过时不替换原题/原图，不编造缺失标注。语义重建、照片净化、文字识别、切分各有独立冻结数据和质量门。普通生图的“成功”不是数学正确证据。

切分门（AUD-08）2026-10-27 登记工具面闭环：冻结金标 v1（7 页/16 框，**模型标注代理金标，无人工专家**）与可复跑判门（`tools/split_golden/`：validate / export / measure / check_split_gate + 门说明 GATE.md）。门值 ①≥0.9 且 ③≤0.05（“立项自定”建议值）；当前实测门 **FAIL**（① 0.5714，4C 存量设备导出复算）→ **门后自动化不启用**，切残不得自动入库红线不变。错误合并/重复率指标未立项未实现，训练/开发/验收切分未做，设备与真实模型重测 UNVERIFIED——逐项如实登记于 GATE.md，不以工具面交付触发任何门后自动化。

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
