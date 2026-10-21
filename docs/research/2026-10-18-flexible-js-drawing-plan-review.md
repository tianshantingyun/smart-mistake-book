# 自由 JS 绘图计划复核

> 最新用户规则：JS与IMAGE_MODEL均为生图方法，所有入口共用判定，由模型选择；本报告旧固定分流/JS不计生图的设计推断已被[统一提示词](../current/figure-generation-prompt-policy.md)取代。原实验与代码审查证据保留。

日期：2026-10-18。方式：由主代理在本地逐项复核，没有启动审查子代理。对象是计划的可执行性、边界和一致性，不是宣称正式 JS 功能已经测试通过。

结论：**原生 JS 绘图路线可以保留，但原计划还不能直接照单开工。发现 12 项需要修订的问题，其中 6 项为 P1 接入/一致性阻断。已直接修订计划和契约；下表“已修订”仅指规划，生产代码仍未实施。**

入口：[修订后的实施计划](../superpowers/plans/2026-10-18-flexible-js-drawing-implementation.md)、[权威契约](../current/visual-generation-contract.md)、[主计划](../../dynamic_gui_architecture_plan.md)。审查前主计划及契约保存在仓库 `.jez/artifacts/visual-plan-review-2026-10-18/before/`，未用 Git HEAD 覆盖已有工作。

## 1. 问题与处理

| ID | 级别 | 原计划问题及后果 | 直接修订 |
|---|---|---|---|
| VR-01 | P1 | 只安排 tool schema/parser，没有安排原生 SSE 参数重建。当前 SSE 终态只有 content，content=null 的工具调用及分片 JS 会丢失，无法完成执行链 | 把 delta.tool_calls 聚合列为模型接入先决门，按 index/id 合并 name/arguments，完整结束才执行；未过门的工具声明回合显式非流式，不隐藏追加请求 |
| VR-02 | P1 | 把“当前题有原图”当作“讲题模型看过原图”。当前 Plan 无原题图片，Respond 仅发学生本轮附图；B7 仅改 JS 参数无法得到规范重建依据 | 4E.3 新增冻结 VisualSourceBasis、PREPARE_SOURCE 和下一预算内回合真实带图门；请求/资产授权/指纹一起改，4E.1/2 不广告 REDRAW LOCAL_JS |
| VR-03 | P1 | 所有图都能关联保存题、进导出，却没区分题面图与教学解析图。含答案的过程图可能进入默认练习卷题目区 | 宿主派生 QUESTION_DIAGRAM/TUTOR_EXPLANATION，核对状态另存；题目区仅用精确修订的已核对题面图，讲解图只按 includeSolution 进解析区 |
| VR-04 | P1 | 要验截图中的单选、理由输入和提交，但 controls 只有 button/toggle/range/select/transport，缺表单协议、原生作答事实和幂等提交 | 4E.2 定义 NativeTutorFormV1、radioGroup/textInput/submit、独立 AnswerDraft 与草稿恢复；经既有交互命令提交学生新回合，JS 不能伪造答案/提交 |
| VR-05 | P1 | 把状态、帧、快照笼统持久化，未规定写入频率。10fps 可能触发逐帧 Room/PNG、备份/GC 压力和无界快照增长 | Frame 只留内存；初次成功、手势结束、暂停/后台及保存导出才 checkpoint；加 CAS、初始/最新未固定保留、每程序4MiB/全局32MiB可回收缓存配额 |
| VR-06 | P1 | terminate/watchdog 未明确原子核对执行代号；迟到取消可能杀下一任务。“只留最新”也可能合并掉按钮/重置；重建后坏源码可能再次卡死 | 终止令牌核对、过期 no-op、定时器失效；坏 programHash 熔断，不自动重跑；绘制 latest-wins 与离散 reduce FIFO 分离，序号/CAS去重，只有绝对位置中间帧可合并 |
| VR-07 | P2 | 写 strictOffline“仅重放缓存”，与“交互无需联网”冲突，阻止兼容缓存程序离线拖动/播放 | 禁止离线外部派发和获取新源码，允许兼容缓存程序本地执行；未知版本/熔断才退快照/预览，冷恢复不自动发请求 |
| VR-08 | P2 | 宿主字体/几何阶段“期限”容易被实现成协程超时即硬停止；同步 StaticLayout/Canvas 不因取消而保证中止 | 服务 JS 用独立进程硬终止；宿主只做输入复杂度限制、单在飞、软期限/结果废弃，无无界补线程或 kill 宿主 |
| VR-09 | P2 | 静态图必须等动画、表单、SVG、B7 和字体大改；原生编排接口也未落清，可能让core:data导入布局/Service或让export依赖JS | 划分4E.1/2/3，先单图保存/PDF闭环；SVG非首期门，不全局改旧数学。app实现VisualExecutionEngine平台编排，data只依赖domain端口，UI/export只依赖原生回放 |
| VR-10 | P2 | “未知版本读快照”和“字体精确匹配”不够可执行：RenderScene本身也可能未知，系统fallback/emoji字体名不能证明资源一致 | execution与render版本分层；未知显示协议只读安全预览/说明；冻结真实资源摘要，无法识别的字体保存受预算文字栅格/整图预览，不承诺跨系统无损文字重建 |
| VR-11 | P2 | program可变身份、首次消息事务、取消后的预备产物和固定历史快照回收规则不够明确，易留孤儿或覆盖已保存题图 | visualId=不可变programId，换源码/basis即新对象；首图随消息/trace/link同事务，预备文件先写SHA后注册，失败清未引用产物；固定事实不可当缓存删除 |
| VR-12 | P2 | 只有单图预算，没有累计尝试/墙钟及模型载体预算；也没有区分worker有界与既有HTTP响应无总cap。8sp标签偏小，测试样图可能被误当物理公理 | 限累计源码128KiB/6次新源码尝试/10s JS墙钟，payload512KiB并不截断源码；State/JSON深度解析时限制；标签≥12sp；渲染函数fixture与真实学科金标分开 |

## 2. 当前代码核对证据

以下属于本轮读取的当前源码，不是外部项目类比：

- [OpenAiSse.kt:127](../../core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/model/OpenAiSse.kt:127) 的 reconstructedChatCompletion 以 deltaContent 拼 content；重建 message 只放 content。它能判断工具帧，但没有把增量 arguments 复原为终态 tool_calls。
- [OpenAiCompatibleModelGateway.kt:607](../../core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/model/OpenAiCompatibleModelGateway.kt:607) 的 requireImageRequestFits：TUTOR_RESPOND 只取 studentImageAssetRefs，其注释明确本题题图不在其中；Plan 没有原题图分支。[TutorModelTaskPolicy.kt:115](../../feature/tutor/src/main/java/com/tingyun/smartmistakebook/feature/tutor/TutorModelTaskPolicy.kt:115) 内定义 TutorQuestionContext，不能把它误写为已有 TutorQuestionContext.kt 文件。
- [OpenAiResponsesProtocol.kt:41](../../core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/model/wire/OpenAiResponsesProtocol.kt:41)、[AnthropicMessagesProtocol.kt:45](../../core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/model/wire/AnthropicMessagesProtocol.kt:45)、[GeminiGenerateContentProtocol.kt:44](../../core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/model/wire/GeminiGenerateContentProtocol.kt:44) 均明确 supportsNativeTools=false；不应为此规划擅自开启原生工具，需测各自 Route B 正规化。
- [OpenAiModelTransport.kt:357](../../core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/model/OpenAiModelTransport.kt:357) 的 readSse 与 readFully 都没有响应总量cap。视觉进程的256KiB不能外推为整个端到端网络内存已受限；保留既有非绘图响应政策，新增绘图工具载体单独限量。
- 本轮源码仍为 Room67、请求schema17；68/18仅为无人占用时的实施目标。不存在已完成的4E模块或正式生成成功率证据。

## 3. 这次没有改变的取舍

模型写JS→隔离计算→验证场景→原生回放的架构继续采用。无JS失败转生图、无隐藏审查模型、无浏览行为自动写掌握度；像素净化继续现有images-edits。当前题/来源/角色/身份均由宿主绑定。

不因本轮审查顺带重写所有旧公式、开放浏览器页面、扩新Provider工具协议、建设全局响应限流或新增SVG产品入口。相关风险如实记录，避免把一个绘图方案变成全App基础设施重建。

## 4. 仍必须通过实验的门

这些不是“已修好”的代码，也不能以文档检查代替：

1. 固定QuickJS源码/NDKr28c重建，API23、arm64、16KB RELRO与实际加载、R8及正式App隔离初始化。
2. 两阶段布局/真实字体在低端设备的延迟、内存与10fps上限；宿主软超时后的实际背压行为。
3. 真实Provider的短JS成功率、原生SSE/三种RouteB、预算内修复，以及B7独立金标的几何/标注保真。

此前API34研究探针6项、桌面33帧/13项检查只作为可行性证据；本轮没有重新宣称它们覆盖以上门。

## 5. 本轮完成证据

本轮验证范围为：修改文档可读/新增相对链接、12项问题在契约与任务中的对应、三阶段广告/执行门、离线交互与表单/Frame/checkpoint一致性、旧冲突文本排除、实际源码路径、Room/请求版本与git diff空白检查。原计划所有正式工程任务仍为未勾选。

可复跑命令：`python .jez/artifacts/verify-visual-plan-review-2026-10-18.py`、`git diff --check -- '*.md'`。结果写入 `.jez/artifacts/visual-plan-review-2026-10-18/verification.json`。验证通过只说明本次计划修订完成，不是生产运行时已经发布。
