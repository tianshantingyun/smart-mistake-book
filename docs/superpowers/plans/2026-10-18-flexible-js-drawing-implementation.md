# 4E：统一生图与 JS 绘图后端实施计划

日期：2026-10-18。状态：方案/提示词已修订，正式工程待实施。原文件名保留作为既有计划入口。

目标：所有生图场景使用同一判定与生成管线，由模型选择JS或IMAGE_MODEL；用途不绑定方法。权威依据：[统一生图提示词](../../current/figure-generation-prompt-policy.md)、[图形契约](../../current/visual-generation-contract.md)、[MASTER LINE](../../REFACTOR-MASTER-LINE.md)。

## 0. 最新决定

1. JS与图像模型都是生图方式；转写、题图/干净题面重建、去手写、讲解图、插画统一构造request/proposal并进入同一coordinator，不各写一套规则。
2. 提示词提供偏好和例外，由模型按精确性、交互、像素保真、视觉复杂度、延迟/成本和实际能力选择；允许在同一操作预算内说明理由后换方法，不新增逐图确认，不由客户端暗换。
3. 原生表单仍用Compose。JS后端采用固定源码QuickJS隔离进程与通用场景，IMAGE_MODEL复用已有图像API；两后端只执行，不决定业务路由。
4. 统一生图operation/attempt、类型化artifact与质量/引用/计数；远程图像请求补接持久预算ledger。JS也算生图，仅本地执行不增加外部图像调用。
5. 不提前修改生产提示词宣称不存在的JS能力；完整正文已在权威提示词文件，接入时按真实availableMethods动态注入。

### 0.1 可交付次序

| 能力 | 交付边界 | 先决门 |
|---|---|---|
| 4E.1 统一生图与静态后端 | 所有入口先共用策略/请求/协调器；先复用图像方法验通，再使JS静态成为并列可选方法；打通源证据、引用、恢复、PDF | 两路工具/转写/净化适配、真实能力、公共ledger/状态/质量；JS运行库过门后才广告 |
| 4E.2 本地交互与表单 | reduce/原生控件/NativeTutorFormV1、动画与命中语义；附加SVG | 共用决策不变，按后端真实能力满足交互；事件/CAS/草稿/配额 |
| 4E.3 复杂源与保真扩展 | 扩展整页/复杂自然图/跨科质量覆盖，必要时声明组合能力 | 两方法都依统一源证据和质量规则，不给B7/A10另建路由 |

先取得“同一规则→模型选方法→一张图→保存重启→PDF”的完整链，再扩表现与复杂来源。B7/A10/讲解/转写不是分期指定后端的开关，所有入口在4E.1即接共用判定；尚不可用能力只由真实能力/授权/预算/质量决定。旧数学主链不全局重写。

研究证据：`.jez/artifacts/research-brief-flexible-js-drawing-2026-10-17.md`；Android 探针目录 `.jez/artifacts/android-visual-runtime-probe-2026-10-17/` 的 build.log、probe-result.json、research-findings.md。桌面 13 项检查、33 帧；API 34/x86_64/4 KB 隔离探针 6 项 PASS。API 23、arm64、16 KB、R8、真实模型产码率及正式 App 集成均未验证。

## 1. 当前源码基线与所有权

根目录为 `D:\smart mistake book`。下表省略统一包前缀 `com/tingyun/smartmistakebook`；路径均相对仓库。文件名是当前实际文件，不沿用旧文档里不存在的 VectorPlot.kt 或旧视觉任务。

| 位置 | 现状 | 本次职责 |
|---|---|---|
| core/model/src/main/kotlin/.../core/model/TutorToolLoop.kt | GENERATE_FIGURE仅kind/description | 适配统一method/proposal；kind只描述用途 |
| 同目录 ModelTasks.kt、TutorTasks.kt、TutorToolDescriptions.kt、TutorToolTrace.kt | 请求 schema 17；无视觉生成/审查任务 | 能力/上下文、两路说明、独立 trace 与兼容 |
| core/data/src/main/kotlin/.../core/data/model/OpenAiModelProtocol.kt、OpenAiModelResponseParsers.kt | native/兼容 JSON 两路解析 | 两路严格 schema、参数与预算一致 |
| 同目录 RoomModelTaskRepository.kt | 总派发 6，最后一轮尚未撤工具 | budget-aware declarations/context、终答拒工具 |
| 同目录 OpenAiSse.kt、OpenAiModelTransport.kt；wire/ 四协议适配器 | 原生 SSE 终态只重建 content；其他三协议走 Route B | 工具参数增量聚合、截断/取消与多协议原样保留 |
| feature/tutor/src/main/java/.../feature/tutor/TutorModelTaskPolicy.kt 内的 TutorQuestionContext | 原题图未进Plan，Respond只有本轮附图 | 共用source basis/实际发送门，按各方法必需证据准备 |
| core/data/src/main/kotlin/.../core/data/study/RoomTutorToolRunner.kt | generateFigure调用图像生成器 | 转调统一FigureGenerationCoordinator，不按kind选后端 |
| core/data/.../model/TutorFigureGenerator.kt、AttachedImageGeneratorFactory.kt | 工具图片链与历史 resolver | 保留像素能力；历史 resolver cache-only |
| core/ui/src/main/java/.../core/ui/AttachedImageCard.kt | resolver 与缓存交互入口 | 只读旧缓存；缺失显示显式生成动作 |
| core/model/.../StructuredContent.kt、MathBudget.kt | 旧图形有限；数学含字符宽估算 | 兼容旧协议；复用 AST，新增真实度量与共享 lowering |
| core/ui/.../StructuredContentRenderer.kt、MathBoxComposeRenderer.kt | UI 自己计算部分几何 | 新场景统一回放，旧入口逐步适配 |
| core/export/src/main/kotlin/.../core/export/DeterministicPdfFigureRenderer.kt、CanvasMathBoxRenderer.kt | PDF 另算几何 | 新快照使用共用 painter，不再复制算法 |
| core/database/.../StudyDatabase.kt、dao/TutorConversationDao.kt、dao/PendingCaptureDao.kt | 定稿时 schema 67；引用决定 GC/备份 | 新增图程序/快照/引用、迁移和资产枚举 |
| feature/tutor/src/main/java/.../feature/tutor/TutorTurnMessages.kt | 正文、trace、图片引用同事务 | 新 visual reference 同事务落库 |
| app/src/main/kotlin/.../SmartMistakeBookApplication.kt | 会初始化文件、Room、恢复、Provider | 隔离 UID guard；只装配宿主依赖 |

开工前以实际工作树重核包路径/版本；已有他线未提交改动不得覆盖。研究初读 Room66，定稿复核已为67；目标改为68/18，若再次被占用则顺延，不把研究快照当永久常量。

新增模块边界：

- `core:model`：VisualProgram/Scene/State/Event DTO、纯校验、SceneCompiler，不引用 Android/Compose。
- `core:visual`（Android library）：真实文本/公式布局、不可变内容 artifact、AndroidScenePainter、SVG writer；core:ui 与 core:export 均可依赖。
- `core:visual-js`（Android library）：隔离 Service、AIDL、QuickJS worker、SDK 资产与宿主客户端；只依赖必要协议，不依赖 core:data/Room/网络。
- `core:domain`：VisualExecutionEngine/VisualProgramRunner/VisualRepository 抽象端口；core:data 只编排来源、幂等、持久化与CAS，不能导入native layout/AIDL。app 的 AndroidVisualExecutionEngine 实现前一个端口，组合 core:visual-js 与 core:visual；feature 不依赖 core:data，core:ui/export 不依赖 JS 模块。
- `third_party/quickjs-kt`：固定源码快照及许可证，不带嵌套 .git；依赖锁与构建 SHA 进入版本清单。

## 1.1 必须先建立的统一生图契约

- [ ] core:model 新增 FigureGeneration.kt（request/proposal/decision、purpose与method正交、类型化artifact）、FigureGenerationPolicy.kt（policyVersion、capability/目标/预算合法性）；method只有JS/IMAGE_MODEL，不按入口建立映射表。公开字段与提示词文件一致；新policy的GENERATE缺method必须拒绝，旧无method载体只可作历史读取，不静默默认图像方法。
- [ ] core:domain 新增 FigureGenerationCoordinator/RemoteModelDispatchLedger端口；core:data 新增 DefaultFigureGenerationCoordinator.kt，公共状态机统一调JS执行器或既有ImageGenerationChannel，准备/验证/持久化/记录在公共层，不分两套业务流程。
- [ ] core:data/model 新增 FigurePromptAssembler.kt，接入统一提示词正文一次，按真实capabilities/source/requirements/budget注入；JS可用时同时注入已实现的schema、SDK签名和完整最小样例。TutorToolDescriptions长短描述、TUTOR_PLAN/RESPOND、CAPTURE_PARSE、IMAGE_PIPELINE_CLASSIFY共用同一规则，图像API只收具体imagePrompt/源图/约束。源图/OCR/附件内容作为数据，不覆盖宿主指令；proposal的purpose校验宿主范围，不能改题面/讲解角色。
- [ ] 明确调用点：RoomTutorToolRunner、RoomCaptureWorkflowRepository、CleanRedrawTaskRunner、ConfiguredCleanImageGenerator、TutorFigureGenerator、历史显式重新生成统一转调coordinator；旧图像类可作IMAGE_MODEL适配器，不为按场景固定路线重命名全仓。
- [ ] ModelTasks.kt的CaptureParseOutput/ImagePipelineClassifyOutput可携带统一proposal，problemKind只作题面特征；已有proposal不再重复分类，缺省才用既有IMAGE_PIPELINE_CLASSIFY。移除净化作业WITH_FIGURE才生图的唯一开关，TEXT_ONLY也按目标决定是否要干净题面。
- [ ] 图像生成/编辑补接RemoteModelDispatchLedger：复用并扩展现有持久派发预留，标记remoteKind而不新增ModelTaskKind；ImageGenerationChannel接收绑定operation/method/源证据/配置的已预留dispatch context，POST前验证，拒绝旁路调用。讲题与父操作共享最多6次模型派发及1次终答预留；独立净化/转写同上限、本地终态不预留聊天位。换方法/传输重发不复制操作身份或隐藏计费，缓存命中不再派发。
- [ ] 新增FigureGenerationPolicyTest、FigurePromptParityTest、FigureGenerationCoordinatorTest和CaptureFigureRouteInstrumentedTest：同一用途两方法都成功、同目标跨入口同规则、双向方法切换仍同operation、无新增确认、约束未被降级、不可用不假成功。偏好测试允许例外，禁止断言“重绘必选X”。

验证：core:model:test、core:domain:test、core:data:testDebugUnitTest及Capture/Tutor/净化定向仪器化；具体Gradle任务按已注册模块执行。公共路由先以实际已可用的图像方法验通，再纳入JS，不能先在提示词广告空能力。

## 2. 批次一：可发布运行库与进程边界

- [ ] 新增 `third_party/quickjs-kt/UPSTREAM.md`、许可证、源码/子模块 SHA 清单；构建脚本从固定源码离线可复建，禁止在设备下载运行库或字节码。保留 bellard QuickJS，不在本批换 NG。
- [ ] 在 settings.gradle.kts、gradle/libs.versions.toml 注册 core:visual、core:visual-js；源码构建固定 NDK 28.2.13676358。新增 ELF/RELRO/打包验证脚本并记录产物 SHA。若固定源码与 Kotlin 2.3.21 不兼容，先形成最小补丁与复验；本批未通过时后续能力不开启。
- [ ] core:visual-js 新增 VisualJsService.kt、VisualJsClient.kt、QuickJsWorker.kt、VisualWorkerProtocol.kt、AIDL IVisualJsService/IVisualJsCallback；Manifest 非导出 isolatedProcess，专用 :visual_js。
- [ ] SmartMistakeBookApplication 首个业务操作前加 UID guard；审计构造/字段/companion 的 eager 初始化与 merged manifest 中实际进入该进程的 provider/startup，不能只看 onCreate。确保不建 Room、不跑备份恢复、不读密钥。
- [ ] Worker 专用线程；100 ms 协作中断、1500 ms 独立自杀 watchdog、2500 ms 宿主阶段期限；terminate 直接在 Binder 控制线程 self-kill。源码/state/结果走有界 PFD 管道，EOF/取消/超长均关闭两端。

- [ ] watchdog/terminate 均核对原子 generation 令牌；完成后迟到 timeout/取消不能杀下一任务。崩溃/超时只重建环境，不重跑坏程序；programHash 熔断至用户本地重试/源码改变。宿主同步字体/编译只设软期限、单在飞与结果废弃，不将协程取消写成硬抢占。
- [ ] 新增 VisualWorkerIsolationInstrumentedTest：权限/私有文件/初始化、普通程序、中文 emoji、无限循环、堆耗尽、长原生操作、主动崩溃、宿主取消、Binder death、旧结果失效、只重建一次；验证宿主存活、FD 回收、服务无无界队列。

验证命令（模块新增后执行）：

~~~powershell
.\gradlew.bat :core:visual-js:assembleDebug :core:visual-js:testDebugUnitTest :core:visual-js:connectedDebugAndroidTest
.\gradlew.bat :app:assembleLocalFirstRelease :app:assembleStrictOfflineRelease
~~~

设备矩阵：API 23/4 KB、arm64/4 KB、API 35+/16 KB、R8 release；逐项登记加载/执行/终止。`llvm-readelf -Wl` 验 PT_LOAD≥0x4000 且 RELRO 结束对齐，zipalign 验 APK；不能只验 zipalign。研究包通过不替代正式进程和 release 实测。

## 3. 批次二：程序 SDK、场景校验与共享排版

- [ ] core:model 新增 VisualProgram.kt、VisualScene.kt、VisualSceneValidator.kt、VisualSceneCompiler.kt、VisualStateCodec.kt；ValidatedScene 构造受控，只能从 validator 产生。
- [ ] core:visual-js/assets/visual-sdk-v1.js 提供 prepare/render/reduce 驱动及 line/arrow/circle/arc/axes/polyline 等纯帮助函数；基础结果仅 group/path/label。用户可写循环、函数、采样，不按题型加 renderer。Date/random/timers/import/network/host object 不可用。
- [ ] 锁定坐标、矩阵顺序、clip、alpha、线型、填充、弧误差与 du/sp 规则；拒绝未知字段、重复 JSON 键、恶意原型键、重复 ID、非法引用、非有限数、越界派生 bounds、过深树及超字节预算。展开回放也计路径预算；字体、bitmap/累计像素与在飞 artifact 按契约设限。
- [ ] core:visual 新增 AndroidContentLayout.kt、MathContentLowering.kt、AndroidScenePainter.kt、SceneSvgWriter.kt；字体来自原生白名单，真实 glyph metrics，布局在后台。公式复用 parser/AST，修正明确的上/下标坐标与宽度，未知语法走文字替代。
- [ ] 一次 prepare 返回完整批量内容；render 只引用冻结 contentId。内容摘要和字体/排版版本进入缓存；Compose draw 不进行 JS/度量；相同 RenderScene 由 PDF/屏幕同 painter 回放。
- [ ] 新增 app/src/main/kotlin/.../visual/AndroidVisualExecutionEngine.kt 与 core:domain.VisualExecutionEngine 端口：平台编排负责 prepare/原生measure/render/compile/取消；core:data 仅注入该端口，不直接依赖 core:visual-js 或 core:visual。以 gradle 依赖与架构测试验证 core:export/core:ui 不导入 JS 执行器，避免跨层循环。

- [ ] V1 字体资源清单和可复用 artifact 有明确版本；未知系统 fallback/emoji 使用受像素预算限制的文字栅格或整图预览。字体名不当作版本指纹；12sp 最小字号随 fontScale 排版，未知 renderSchema 不交 painter。
- [ ] 新增 SceneValidatorTest、SceneCompilerTest、VisualStateCodecTest、VisualSdkContractTest、NativeContentLayoutInstrumentedTest、MathContentLoweringTest；覆盖 clipping、非均匀矩阵、虚线、分数/根号/上下标、中文宽度、大字体，不编写只照抄实现的断言。

验证：`gradlew.bat :core:model:test :core:visual:testDebugUnitTest :core:visual:connectedDebugAndroidTest :core:visual-js:testDebugUnitTest`。金样同时比较结构/位置与固定环境像素，跨系统字形差异单列，不承诺跨设备像素 SHA 一致。

## 4. 批次三：现有模型工具一次接通

- [ ] TutorToolCall适配统一method/proposal：JS带完整program，IMAGE_MODEL带EDIT/GENERATE与imagePrompt；旧executionMode仅兼容映射。description保留短用途，purpose不决定method。host绑定subject/basis/role；单回复最多主图+必要辅助图，全部尝试共用父操作预算。
- [ ] 同步 OpenAiModelProtocol strictFunctionSchema/native parser、OpenAiModelResponseParsers Route B 白名单/解析、TutorToolDescriptions 与载体测试；程序只能在独立工具字段，禁止放入 messageMarkdown/attachedImages。

- [ ] **先修终态门**：OpenAiSse 累积 delta.tool_calls（index/id/name/arguments），测试 content=null、分片换行/反斜杠/中文、截断、重复/冲突 index、取消和 payload 预算。未支持时对工具声明回合显式 stream=false，不追加探测/隐藏重试；普通文字回合保留流式策略。
- [ ] wire/OpenAiChatCompletionsProtocol.kt、OpenAiResponsesProtocol.kt、AnthropicMessagesProtocol.kt、GeminiGenerateContentProtocol.kt 全覆盖；后三者保持 supportsNativeTools=false，用各自 Route B 正规化测试源码字节不变。新增工具 payload≤512KiB、代码≤64KiB/state≤16KiB 的增量与解码校验，不截断；整个 HTTP 响应无总 cap 是既有独立风险，不声称已由 worker 预算解决。
- [ ] 新增 TutorVisualContext，限当前/选中一份程序源码、state、诊断和能力版本；ModelTaskRequest schema 17→18（或当前下一版），空载体不改变历史指纹。补 ModelTaskFingerprintStabilityTest 和 logical-operation 指纹旧 JSON 测试。
- [ ] RoomModelTaskRepository按共享ledger真实余额构建工具集并预留终答；IMAGE_MODEL执行另占远程派发，准备源图后预算按决策+所选方法执行+终答计算，不再一律假定两次够。失败/截断/重建计数不重置；余额不足不执行后端，修复与换方法仅用原余额，不新增TUTOR_VISUAL任务。
- [ ] RoomTutorToolRunner通过domain的FigureGenerationCoordinator执行显式method，返回类型化artifact/status/≤2KiB诊断；JS可附visual上下文，不回整张场景或本地URI。残缺源码、当前题变更、revision过期均不执行/不应用，两方法都走共同记录与失败回传。
- [ ] 统一FigureGenerationTrace：两方法都记生图attempt/产物；method和外部图像调用分项，JS帧/预览/缓存不重复计数。失败诊断回原预算内决策，允许模型修复或双向换方法；客户端不自动暗换，不把visualId当旧像素figureId。

- [ ] 4E.1即接共用源证据门：冻结VisualSourceBasis，缺源时NEED_SOURCE/PREPARE_SOURCE，再按所选后端实际必需证据发送或读取可追溯结构化源。JS产码模型与图像编辑执行分别取得其所需输入；不按REDRAW用途禁JS。测同批准备伪已发送、附加题错图、SHA变化、授权不足和剩余远程成本不足。

该项实际文件包括 TutorModelTaskPolicy.kt 内 TutorQuestionContext、core:model 的 TutorTasks.kt/ModelTasks.kt/ModelEgress.kt，以及 core:data 的 OpenAiCompatibleModelGateway.kt.requireImageRequestFits、OpenAiModelTaskAdapters.kt；请求构建、requestsImageBytes、披露集合、byte grants 和 wire 图片块必须同源。新增源码累计/尝试/墙钟预算见契约，不能用多枚本地工具调用绕过总工作量限制。
- [ ] 验证 native/Route B 同一输入同一结果、无图能力模型提前拒绝需源图的重建、总派发≤6、终答不执行工具、运行/滑块 0 网络、strictOffline 禁止新外发但允许兼容缓存程序的本地重新布局/交互、取消/重建不自动外发。

验证：`gradlew.bat :core:model:test :core:data:testDebugUnitTest :feature:tutor:testDebugUnitTest`，再运行 core:data 工具环仪器化（具体 variant 以模块注册任务为准，禁止把不存在命令记成通过）。真实 Provider 测试另记录模型名、参数、token、派发、源码成功率与修复耗费；无凭据时登记 UNVERIFIED，不拿手写程序冒充。

## 5. 批次四：Room、引用、恢复与旧图兼容

- [ ] core:database新增FigureGenerationEntities/Dao与JS的VisualEntities/Dao：公共figure_generation_operation/attempt及JS程序/快照/消息/保存题引用，六张新增表；旧clean_redraw_task非破坏关联operationId。Room67下一未占用版本（当前目标68），请求schema17下一版18；导出新schema并测全链，不覆盖并行迁移。

- [ ] 公共operationId含parent/subject/basis/purpose/slot、不含method；attemptId与产物缓存含method及真实参数。JS程序不可变。role由宿主按purpose/subject派生，与method无关；两种方法的图都不能冒充未核验题面。更新sourceType白名单，但业务生图计数不再只查GENERATED_FIGURE。
- [ ] core:data 新增 RoomVisualRepository.kt、LocalVisualProgramRunner.kt：源码原始字节/SHA、state 规范 JSON、scene/RenderScene/版本、time/seed、布局环境及题面来源落库。RenderScene 仅序列化路径和内容/冻结行与 run 位置、字体身份摘要；Path/Paint/StaticLayout 留在内存。冷恢复不执行 JS，字体/renderer 不匹配时使用原预览/文字，重新排版产生新快照。正文、trace、message visual link 同事务；旧请求/旧表无需补造程序。
- [ ] PNG 预览走 canonical vault，新增 LOCAL_VISUAL_PREVIEW 常量；snapshot→preview、保存题→visual 引用加入 PendingCaptureDao 清理/备份查询，删除会话不删除保存题图。保存题引用绑定该 revision，不能暗改新修订。

- [ ] Frame 只在内存，逐帧不写库/不写 PNG；仅初次成功、手势结束、暂停/后台及保存导出提交 checkpoint。program+basis+expectedRevision CAS，迟到任务不得回滚状态。每程序保留初始/最新两个未固定 checkpoint；可回收派生缓存每程序4MiB/全局32MiB，固定引用不得删，磁盘不足拒本次提交。
- [ ] assistant 正文/trace/visual link 首次提交同事务；程序/预览先 staging，失败/取消清未引用产物；文件先写并验 SHA，随后注册 DB 引用，失败孤儿交既有清理。测取消、存储满、CAS冲突、导出固定与备份并发；不能靠 message 未提交前先建永久孤儿行。
- [ ] 更新备份完整性/导入恢复/GC；程序与显示列表是 DB 数据，预览是资产，任何必需字节缺失拒绝完整包。未知程序版本只读已验证快照/文字，不自动执行或联网。
- [ ] 旧 attachedImages resolver cache-only；有明确绑定时补消息引用，缓存缺失提供显式重新生成入口。旧 raster、CLEAN_IMAGE、旧 FigureSchema 不批量重画、不因打开历史收费。
- [ ] 测全历史迁移矩阵、保存/删会话/GC、备份往返、快照引用、缺文件、重复提交、程序/状态/字体/时间改变指纹、进程重建恢复且 0 外发。

验证：`gradlew.bat :core:database:connectedDebugAndroidTest :core:data:testDebugUnitTest`；用当前仓库备份 suite 做真实创建→校验→恢复，不只测 DAO。新版本/迁移占用由实施当日重核。

## 6. 批次五：界面、导出与语义正确性

- [ ] core:ui 新增 VisualSceneCard.kt、VisualControls.kt、VisualSemantics.kt；feature:tutor 从持久 visual link 展示。Canvas/原生控件分离，range/transport 控件可聚焦，点击与拖动共享命中矩阵，过期事件拒绝。
- [ ] native clock 限 10 fps、仅前台播放、最新状态合并；系统禁动画保留步进。控件更新只改局部 state，答题提交走当前题既有讲题事件，不让 JS 写掌握库。

- [ ] 4E.2 新增 core:model/NativeTutorFormV1.kt、core:ui/NativeTutorForm.kt：≤4题 radioGroup、≤6选项/题、≤1000 UTF-16理由 textInput、submit；纳入总控件预算。原生 AnswerDraft 与 JS state 分离，form 摘要/会话/revision 匹配后以稳定 submissionId 经 TutorInteractionCommands/TutorTurnMessages 提交真实学生新回合。重复点击/重建去重，程序/模型预填不能伪造学生原话或自动提交。
- [ ] 原生表单草稿走独立 ui_state_json/formDigest/revision 与500ms输入合并/后台保存；旋转用有界 SavedStateHandle，重建不自动发送，换题不套用旧答案。reduce 离散事件有界FIFO、序号去重/CAS，只有绝对位置中间帧可合并；用连点两次按钮、drag-end紧接reset、满队列及迟到reduce结果验证不丢动作。
- [ ] 独立测试“动画1000帧→0个逐帧DB/资产写入”“拖动结束→一次checkpoint”“进程死亡→最近checkpoint”“strictOffline→本地图交互可用且0 Provider派发”。
- [ ] core:export 新增 VisualSnapshotPdfBlock.kt，并接 MistakePdfEligibility/MistakePdfPagePlanner/DeterministicMistakePdfRenderer；冻结状态和 RenderScene 后同 painter 输出。exportFingerprint 覆盖快照 SHA、字体/renderer/布局、time/seed，导出不执行 JS。

- [ ] 导出按宿主 role 过滤：题目区仅当前revision且核对通过的 QUESTION_DIAGRAM；TUTOR_EXPLANATION 只能按 includeSolution 放解析区。用含答案的过程图测试默认练习卷不泄题，显式解析区可包含，不把所有同题图混成 CLEAN_IMAGE。
- [ ] 4E.2 附加 SVG writer 输出受控路径/clip/变换；文字可用内嵌 PNG artifact，说明几何为矢量、字体全矢量未实现。不新增产品导出入口，不阻断 4E.1 的 PDF；文件不含外链/脚本，不承诺 tagged PDF。
- [ ] B7/A10/转写/讲解全部按共同源证据与质量要求判定并选择JS或图像模型；保留净化持久作业和A11确定性预处理。语义清图不伪称原像素修复，源内容未知不补造，未过门不替换原题；同一图的vector/PNG只消费一次。

JS后端与原生UI的冻结工程fixture（验证指定后端本身，不限制生产方法选择；共用路由另按第9节验证同用途两方法及例外。按能力阶段验收；图形需参数/布局变化与静态导出，表单验原生提交）：

| 样例 | 必须验证的行为 |
|---|---|
| 截图 1 比例条 | 3:1/1:1 精确比例、标签可读，非截图图片 |
| 截图 2 F-x 面积 | 人工渲染 fixture 定义 F(x)=F0(1-x/h)，x=h/2 时面积=3F0h/8；这是函数绘图 oracle，不假设真实拔塞题总拉力终点为0 |
| 截图 3 解题流程 | 原生卡片与公式，不把流程强制画进 Canvas |
| 截图 4 问答（4E.2） | NativeTutorFormV1 的原生 radioGroup/textInput/submit、禁用态与幂等；作答不由 JS 伪造，不自写掌握度 |
| 截图 5 受力图 | 方向遵循物理条件；原截图用横箭头“区分力”的误导不能照搬，摩擦/气压合力按实际向下标注 |
| 截图 6 拔塞阶段 | x=0、h/2、h 的相对位移、无裁切、公式不显示原始 TeX |
| 新组合：波叠加/轨迹采样 | 只新增模型程序与数据，不新增题型原生 renderer |

屏宽 360/600/840 dp，fontScale 1/1.3/2，明暗主题、TalkBack、横屏/旋转、后台恢复；检查标签重叠、公式基线、点击区、无水平溢出。同一快照 PDF/SVG 不改变曲线采样、标签或裁剪；布局宽度变化则明确生成新快照。

渲染 fixture 与学科正确性金标分开：截图缺少完整题面时不把其图线端点当物理公理。B7 必须按授权完整原题及独立核对集判断，不能以本表样图通过证明真实题目答案正确。

## 7. 退出门与回退

- [ ] 4E.1先统一全部生图入口并按真实能力开放方法；4E.2/3扩表现与保真，不按用途固化后端。整个4E仍需全部工程证据，正式源码/设备/真实Provider各自记录；未过方法/能力不广告，不把文档正确当软件已接通。
- [ ] 最终运行仓库对应 flavor 的 assemble/lint/unit、全历史 Room 迁移与核心设备 suite、release R8/16 KB 冒烟；不因研究探针通过跳过正式工程门。
- [ ] 校验 JS 首次生成、预算内修复、保存重启、交互、导出打印、历史兼容完整链。发布开关由能力门控制，不恢复旧 TUTOR_VISUAL 隔离死链。
- [ ] 灰度可关闭故障方法并保留旧成果；更新availableMethods，由模型在原预算/授权内选择其余可行方法，不能客户端暗换、复制新operation绕预算或静默降低目标。
- [ ] 回填 MASTER LINE 4E、新契约状态与完成记录。没有代码/设备证据时不得勾选本计划任务或把 4B 历史完成算作 4E 完成。

## 8. 文档一致性验收

本次规划维护需同步：dynamic_gui_architecture_plan、image-pipeline-decision/spec、4B 设计/计划的路线优先级、4D、主线/phasing、需求 B7/G15/A10、产品/场景说明、模型边界、current 契约入口与发布门。完成记录和历史审计保留日期事实，加新契约优先级说明；知识库原始教材配图/原图裁切不改成模型生成物。

文档完成的证据是：文件可读、相对链接可解析、git diff --check 通过、旧路线关键词逐项审阅、无误勾正式实施任务。它不等于本计划中的正式软件已实现。

本地审查的问题、代码证据和修订结果见[2026-10-18计划复核报告](../../research/2026-10-18-flexible-js-drawing-plan-review.md)。

## 9. 统一规则提示词与反例验收

完整提示词仅维护在[figure-generation-prompt-policy](../../current/figure-generation-prompt-policy.md)，接入时动态注入。必须验“转写选JS”“讲解选IMAGE_MODEL”“两种方法失败后可双向切换”“TEXT_ONLY需要时可生图/WITH_FIGURE不必重生”“只一种或无方法可用”“原像素保真与语义重建区分”“缓存/动画不重复计生图”。这次用户新规则取代此前按用途固定路线及JS不算生图的规划，历史研究/审计证据不改写。

## 10. 全入口接线任务与后续阶段承接

本节把第1.1节及各工程批次拆成可验收的接线编号`WG-01`～`WG-10`，不是另立架构或重复计功。当前全部未实施：接口、提示词、手写样图不能替代真实入口到产物的证据。

### 10.1 文件定位与范围

下列别名精确展开到目录，后文`别名/文件名`为仓库相对路径，不含省略号；标记“新增”的文件是实施目标，其余是当前工作树已有文件。

| 别名 | 目录 |
|---|---|
| M | core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model |
| DOM | core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain |
| D | core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data |
| DB | core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database |
| UI | core/ui/src/main/java/com/tingyun/smartmistakebook/core/ui |
| T | feature/tutor/src/main/java/com/tingyun/smartmistakebook/feature/tutor |
| L | feature/library/src/main/kotlin/com/tingyun/smartmistakebook/feature/library |
| E | core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export |
| A | app/src/main/kotlin/com/tingyun/smartmistakebook |

拍摄/导入、原图裁切、压缩与读取继续既有链路，仅供源证据；一旦需要创建、净化、重建、编辑或重新生成图形，就进入共享规则。选择题、输入、提交仍用原生控件。

### WG-01：公共装配、后端与派发账本

阶段：4E.1首先实施。新增`M/FigureGeneration.kt`、`M/FigureGenerationPolicy.kt`、`DOM/FigureGenerationCoordinator.kt`、`DOM/RemoteModelDispatchLedger.kt`、`D/visual/DefaultFigureGenerationCoordinator.kt`、`D/model/FigurePromptAssembler.kt`；修改`A/SmartMistakeBookApplication.kt`、`D/model/RoomModelTaskRepository.kt`、`D/model/ImageGenerationChannel.kt`、`D/model/OpenAiImageGenerationChannel.kt`、`D/model/TutorFigureGenerator.kt`、`D/capture/ConfiguredCleanImageGenerator.kt`。

- [ ] app装配共享coordinator/ledger并注入全部入口；原有Factory不各建业务路由。图像后端独立适配ImageGenerationChannel，不回调已改为coordinator入口的旧高层生成器，避免递归。
- [ ] 入口适配层在已有模型任务取得proposal，coordinator消费Request+Proposal并返回产物或诊断；修复/重选交回原任务环，独立净化用现有IMAGE_PIPELINE_CLASSIFY。ledger独立注入，coordinator不反向构造ModelTaskRepository，消除装配环。
- [ ] 图像POST前必须有绑定operation/source/config的持久派发预留；源码/决策沿原模型账本。按真实JS/EDIT/GENERATE能力组装同一提示词，缺失能力不广告。

验收：新增`FigureGenerationWiringTest`，通过fake后端实际执行讲题/录入请求，观测统一policyVersion、operation/attempt及预留入口；无有效预留的图像HTTP调用为0。不能仅测试构造器参数。

### WG-02：转写与源图证据

阶段：4E.1，依赖WG-01。修改`M/ModelTasks.kt`、`M/CapturedQuestionDocument.kt`、`D/model/OpenAiModelTaskAdapters.kt`、`D/model/OpenAiModelResponseParsers.kt`、`D/capture/RoomCaptureWorkflowRepository.kt`；讲题源图同步修改`M/TutorTasks.kt`、`M/ModelEgress.kt`、`T/TutorModelTaskPolicy.kt`、`D/model/OpenAiCompatibleModelGateway.kt`。

- [ ] CAPTURE_PARSE需要图时返回同型proposal，parser接受并严格校验，绑定源页/裁切范围/草稿修订；图形通过类型化引用进入题目文档，不把源码塞进正文/图片URI。
- [ ] 已有有效proposal直接执行，缺省且需要判定才调IMAGE_PIPELINE_CLASSIFY；TEXT_ONLY/WITH_FIGURE不决定生成或方法。源证据足够就复用，NEED_SOURCE先准备，换题/重裁/SHA变化使旧proposal失效。

验收：Capture转写/授权用例覆盖转写选JS、转写选IMAGE_MODEL、NO_FIGURE、缺源、源SHA变化；已有proposal从转写到提交不重复分类/执行，保存后引用仍在。

### WG-03：保存净化、去手写与后台续跑

阶段：4E.1，依赖WG-01/02。修改`D/capture/CleanRedrawTaskRunner.kt`、`D/capture/RoomCaptureWorkflowRepository.kt`、`D/capture/ConfiguredCleanImageGenerator.kt`、`DOM/CleanImageGenerator.kt`、`DB/entity/CleanRedrawTaskEntity.kt`、`DB/dao/CleanRedrawTaskDao.kt`；公共operation/attempt迁移沿第5节。

- [ ] 保留clean_redraw_task身份、claim、SUPERSEDED和启动对账，关联共享operation/proposal。移除TEXT_ONLY直接当生成完成的捷径；NO_FIGURE记无需生成的终态，不伪报已产图。
- [ ] 当前`ByteArray → CleanImageResult`回调与挂接升级为类型化FigureArtifactRef，支持JS快照与canonical图片；核对通过才绑定题目revision，sourceType/provenance按真实方法填写。
- [ ] 逐项测试拍照、连拍、文件/分享导入、批量切题和讲题草稿保存的既有提交分支；同题同目标复用成果，不同题冻结各自身份，恢复/重试不清空预算。
- [ ] 已发送但结果未知的图像请求保留待核实状态，不能盲目补发或保证外部调用天然恰好一次；Provider幂等/查询能力须实际验证后使用。

验收：扩展`CleanRedrawTaskRunnerTest`、`CleanRedrawTaskInstrumentedTest`和Capture提交测试，验证两方法成功、NO_FIGURE终态、切换同operation、重复提交不重复执行、迟到不挂新修订、无凭据仍可保存原题。

### WG-04：讲题工具、流式与消息呈现

阶段：4E.1，依赖WG-01/02；交互部分随4E.2。修改`M/TutorToolLoop.kt`、`M/TutorToolDescriptions.kt`、`M/TutorToolTrace.kt`、`D/model/OpenAiModelProtocol.kt`、`D/model/OpenAiSse.kt`、`D/model/RoomModelTaskRepository.kt`、`D/study/RoomTutorToolRunner.kt`、`T/TutorTurnMessages.kt`、`T/TutorChatConversation.kt`；新增UI文件沿第6节。

- [ ] TUTOR_PLAN/RESPOND、GENERATE_FIGURE、native与Route B一起接统一proposal/能力提示词，kind只表用途；完整工具参数聚合后才执行，工具结果回类型化artifact/status/诊断。
- [ ] 正文、trace、图引用同一事实链；JS显示原生图形，IMAGE_MODEL显示本地图资产。比例条、函数面积、受力图、阶段图可呈现，流程/表单保留原生控件，也允许不出图。
- [ ] 诊断回原回合让模型修复或双向换方法，共用六次预算与终答预留；4E.2接滑块/动画/表单事件，不将播放/预填作为作答。

验收：两路解析及`RoomModelTaskToolLoopInstrumentedTest`加真实消息卡片用例，覆盖同用途两方法、双向切换、残缺源码不执行、最后终答无工具、保存重启仍可见图。

### WG-05：旧attachedImages与显式重新生成

阶段：4E.1，依赖WG-01/04。修改`D/model/AttachedImageGenerator.kt`、`D/model/AttachedImageGeneratorFactory.kt`、`UI/AttachedImageCard.kt`、`T/TutorChatConversation.kt`、`T/CapturedTutorSessionRoute.kt`、`T/SavedMistakeTutorRoute.kt`、`A/SmartMistakeBookApplication.kt`。

- [ ] 旧resolver改为cache-only，打开消息/展开/旋转/恢复只读资产；缺图显示显式操作，旧attachedImages字段不作为新生图指令旁路。
- [ ] “重新生成/重试”绑定原消息subject/basis/purpose/slot，进入共享判定选任一可用方法；失败重试沿原operation/预算，不拿当前另一题作源图，不写死IMAGE_MODEL。
- [ ] 同回复的新工具产物与旧attachedImages有明确关联才去重；不能证明同图时不猜测合并，也不在渲染时另开发图请求。

验收：扩展`AttachedImageGeneratorTest`、`AttachedImageCardTest`与恢复用例：“历史打开/展开/旋转→0远程调用”“显式重生成→共享coordinator”“换题后重生成旧图→旧basis或明确缺源”。

### WG-06：保存入库、题目详情与重绘出口

阶段：4E.1，依赖WG-02/03/04。修改`DOM/SaveTutorDraftToLibraryUseCase.kt`、`DOM/CaptureWorkflowRepository.kt`、`D/capture/RoomCaptureWorkflowRepository.kt`、`DOM/MistakeDetailRepository.kt`、`D/mistake/RoomMistakeDetailRepository.kt`、`L/MistakeDetailRoute.kt`；引用沿第5节。

- [ ] 草稿保存、录入提交、题目修订保留对应已验证图引用/revision/role，删会话后保存题仍有图；仅保存/查看不重新生成。
- [ ] 题目详情的重绘/生成命令、进度与失败展示接同一coordinator；当前未有完整新出口的地方，在上述既有repository/route新增，不把拟新增按钮说成现存功能。
- [ ] 题面重建验证通过才替换，讲解图位于解析区；失败留原图/上一有效成果，晚到结果不覆盖新revision。

验收：扩展`SaveTutorDraftToLibraryUseCaseTest`、`RoomMistakeDetailRepositoryTest`及详情仪器化，覆盖两方法保存重启、删会话、修订/迟到、失败与题面/讲解角色隔离。

### WG-07：PDF、DOCX、打印与分享

阶段：4E.1，依赖WG-03/06；SVG随4E.2。修改`E/MistakePdfEligibility.kt`、`E/MistakePdfPagePlanner.kt`、`E/DeterministicMistakePdfRenderer.kt`、`E/MistakePdfExporter.kt`、`E/DocxMistakeRenderer.kt`、`E/MistakeDocxExporter.kt`；新增`E/VisualSnapshotPdfBlock.kt`。

G15既有题图入卷在本项收口，不等待阶段5B的新出题/改编能力。

- [ ] PDF回放已冻结JS RenderScene或读取canonical图片；DOCX从同一已验证快照取得有界PNG并嵌入，图像方法复用canonical字节，不能把新JS图退化成一行说明。PNG格式转换不新增生图attempt/模型调用。
- [ ] 共用role/revision/includeSolution过滤，同图vector/PNG只消费一次；分享/打印复用导出成果。缺必要快照/资产时报具体不可用项，不静默漏图或临时联网补图；导出期间固定引用。

验收：扩展`MistakePdfCleanImageExportInstrumentedTest`、`MistakePdfExporterInstrumentedTest`、`DocxMistakeRendererTest`，检查PDF图形及DOCX media/relationship实际存在、默认练习卷不泄答案、导出/打印/分享0模型派发、重复导出不重复计生图。

### WG-08：状态、预算、恢复与备份

阶段：4E.1，贯穿WG-01～07。修改第5节公共记录/引用、`DB/StudyDatabase.kt`、`DB/dao/TutorConversationDao.kt`、`DB/dao/PendingCaptureDao.kt`、`D/model/RoomModelTaskRepository.kt`、`A/SmartMistakeBookApplication.kt`及既有备份/恢复入口。

- [ ] 两方法统一generationAttempts/successfulArtifacts/method/purpose/质量/失败状态，外部图像调用与token/费用另记；预览/动画/缓存/格式转换不算新生图，切换不清账。
- [ ] 跨入口测试取消、进程死亡、磁盘满、备份恢复、GC和固定引用；未知版本用已验证预览/文字，不在恢复时执行不兼容源码或联网补图。

验收：第5节迁移/备份suite加入两方法混合数据，恢复后Capture/讲题/详情定位同一artifact，导出不丢图、ledger不回零；迁移版本按开工时下一空闲版本决定。

### WG-09：后续复习、出题与纸质闭环承接

阶段：5/5B在各自原有前置门满足后承接。涉及现有KNOWLEDGE_QUIZ的`M/ModelTasks.kt`、`D/model/KnowledgeQuizWire.kt`、`D/study/RoomBackedStudyExperienceRepository.kt`，阶段5讲题增强，以及5B的G5/G14出题/改编与G8回收。既有题图入卷由WG-07承担，本项只扩展这些后续业务的图形消费与生成。

- [ ] 需要新图时扩既有输出schema/parser及展示引用，携带同型proposal，接同一prompt/coordinator/ledger；不另造出题专用生图器，不把现有KNOWLEDGE_QUIZ纯文字协议说成已支持图片。
- [ ] 原题图直接引用，改编题新图绑定新题basis/revision，题干图与答案/解析图分开。回收答卷属于输入证据，需新清图/重建才走共享生图，不能把生成图冒充学生原始答卷。
- [ ] 有图分支依赖4E.1及本阶段原有门，无图分支保持原依赖；需交互才依赖4E.2。不得以此提前宣布阶段5/5B开工或解除3D/3E等原前置。

验收：在对应阶段新增“两方法新题图→作答→保存/出卷”用例，验证题目/答案不串位、回收来源可追溯、模型图不直接形成掌握证据。

### WG-10：旁路迁移、真实Provider与完成台账

- [ ] 迁移当前三处直接图像执行：`D/model/AttachedImageGeneratorFactory.kt`的channel.generate、`D/model/TutorFigureGenerator.kt`的channel.generate、`D/capture/ConfiguredCleanImageGenerator.kt`的channel.redrawClean。业务入口均经coordinator/ledger到图像后端；用HTTP transport观测验证，不能只凭文本搜索。
- [ ] fake后端先验全部入口，再以真实Provider验两方法、跨入口同policy、换方法与预算；无真实配置时保留UNVERIFIED，fake成功不等于模型质量通过。
- [ ] 每个WG分栏登记代码/装配位置、模块命令、设备全链、真实模型质量。4E.1收齐WG-01～08/10适用项；WG-09随5/5B收口，不反向要求提前实现未来业务；4E.2/3按交互/复杂保真门推进。

文档维护验证本节覆盖、文件与链接；正式实施执行第1.1/4/5/6节对应Gradle任务和以上定向用例。六类截图仅作工程样例，各入口接线与真实题目正确性分别留证。本节不改变正式工程尚未实施的状态。

### 10.2 后续计划文件同步索引

- [后续阶段执行清单](../../../.zcode/plans/plan-2026-10-18-followup-figure-wiring.md)：4C/4D/4E→5a/b/c→5B→6B/6C→7及未完成工作；以主线状态为准，不解除原有门。
- [新阶段spec](../../research/2026-10-11-new-stages-spec.md)：各阶段已增加接线任务与验收，6B-2/3接编辑/改图，6C接快照/程序/ledger闭包。
- [6C-2设计补充](../../research/2026-10-17-6c2-content-selection-design.md#7-统一生图接入后的闭包补充2026-10-18)：跨题目/会话共享图按保留根筛选，新表落地后再重算机器清单。
- [分期依赖](../specs/2026-09-25-agent-first-refactor-phasing.md)：有图/无图分支依赖分开，G15已有题图入卷仍在WG-07，不推迟到5B。

## 11. 2026-10-19 产品闭环与真实质量补充

承接[产品补缺实施计划PRD-09/10](2026-10-19-product-readiness-followup-plan.md)，保持WG-01～10为唯一接线编号：

- [ ] 源题编辑/答案纠正后，WG-02/03/04/06的basis/revision校验拒绝旧任务回写；历史产物按原版本保留，不因改稿自动重新生图。
- [ ] WG-07接PRD-04原生格式选择；已有图转PDF/DOCX不依赖模型入口，图字节/hash固定后嵌入，0模型派发。
- [ ] 两方法共同遵守新增图义提示词；校验结构、语义预期和可读性分别记证，错误受力方向/坐标/条件不算合格。
- [ ] 按[设计§6](../specs/2026-10-19-product-readiness-design.md#6-统一生图的质量与启用)冻结120任务与盲测；首次≥90%、预算内≥95%、分组≥90%，关键错误接受=0。记录真实方法自主选择、修复/切换、延迟/费用；手写程序和模型自评不替代独立预期。
- [ ] 先验静态图保存/重启/详情/原生PDF及DOCX纵向链，再完成4E.1所有入口和正式设备矩阵；4E.2/3继续后置。阈值与时延目标尚未实测，不据本节开启能力。

## 12. 2026-10-19 在飞实现承接

本文件前部2026-10-18“全部未实施”是当时计划状态。最终源码复核已出现FigureGenerationPolicy、DefaultFigureGenerationCoordinator和公共派发账本相关接线；协调器目前只执行IMAGE_MODEL，JS返回UNAVAILABLE。**本轮没有验证任何WG项全部完成**。

继续实施前先按WG-01～10逐项映射已存在的类型、提示词装配、proposal入口、账本、持久引用与导出消费，复用现有实现并只补缺口；不要再创建第二个协调器或账本。JS运行库工程、真实双方法选择/切换、全入口、恢复和PRD-09质量门仍需独立证据。详情见[研究§1.1](../../research/2026-10-19-product-readiness-research.md#11-最终核验时的工作树增量)。
