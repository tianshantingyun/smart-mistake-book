# 统一生图实施规格

更新：2026-10-18。状态：统一提示词与方案已定，生产接线待实施。以[统一提示词](current/figure-generation-prompt-policy.md)、[运行契约](current/visual-generation-contract.md)、[实施计划](superpowers/plans/2026-10-18-flexible-js-drawing-implementation.md)为准。

## 1. 业务协议

method仅JS/IMAGE_MODEL，purpose与method正交。所有转写、净化、重绘、讲解/插画请求使用FigureGenerationRequest/Proposal；decision=GENERATE/NO_FIGURE/NEED_SOURCE/UNAVAILABLE。JS带完整program；IMAGE_MODEL带EDIT/GENERATE与imagePrompt。兼容executionMode映射至LOCAL_JS/IMAGE_EDIT/IMAGE_GENERATE，PREPARE_SOURCE是准备步骤，不是方法。

GENERATE_FIGURE工具、CAPTURE_PARSE、IMAGE_PIPELINE_CLASSIFY、保存净化和显式重新生成共同调用FigureGenerationCoordinator；讲题工具名仍是GENERATE_FIGURE。problemKind不再硬定生成或后端，已有proposal不重复分类。角色与subject绑定由宿主派生，模型不提供数据库对象、文件或URL。

## 2. 判定与提示词

提示词正文只维护一份，各入口动态注入相同policyVersion、availableMethods、sourceAccess、requirements、budget、diagnostics。精确/交互通常JS优先，像素质感通常图像模型优先，均允许例外。模型可在原预算/授权内修复或换方法，不新增确认；客户端不得暗换。生产只广告实际已接通的方法。

native/兼容JSON/转写parser都验证同型proposal；原生SSE先补完整参数聚合，Responses/Anthropic/Gemini继续各自RouteB。选择模型与图像执行API提示分离，后者只接具体内容、源图与约束。

## 3. 执行与验收

JS后端按固定QuickJS隔离/预算/场景/共享排版契约；图像后端复用已验证API并写canonical资产。两者统一源证据、业务质量、状态、缓存和引用；JS导出PNG仍记JS。忠实转写不能造事实，像素编辑和语义重建不能偷换目标。

公共operation/attempt+JS程序/快照/引用共六张拟新增表，旧clean_redraw_task关联共用操作并保留续跑/去重/SUPERSEDED。operationId不含method，缓存/attempt含method。Room67→下一空闲版68、请求17→18或顺延，备份/GC/旧指纹同步。

两方法都计生图attempt/产物，外部图像调用/token/费用另记；缓存/动画/预览不重复计数。图像生成/编辑补接最多6次Provider模型执行的持久ledger；讲题留终答，独立净化终态本地保存不留聊天位。方法切换不能绕过父预算或另建同图新操作。

## 4. 消费与交付

FigureArtifact类型化引用vector/program快照或raster资产；旧图片继续可读。题面与讲解role隔离，PDF默认题目区不能含答案图。新导出优先对应规范产物，vector/PNG不得重复画；导出/历史打开不触发生成。

4E.1先统一所有入口，再按实际能力启用JS静态；4E.2交互/表单，4E.3复杂源/保真。未过能力门不广告，不因用途写死后端。共同验“转写可JS”“讲解可图像模型”“双向换方法同operation”“TEXT_ONLY也按需生成”“两方法都记生图”“所有入口同prompt”。设备/真实Provider/质量门不得由文档测试替代。
