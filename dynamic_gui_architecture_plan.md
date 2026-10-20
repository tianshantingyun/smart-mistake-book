# 通用动态 GUI 与统一生图架构计划

更新：2026-10-18。状态：提示词/方案已修订，正式统一路由与JS后端待实施。

## 1. 统一生图能力

JS与图像模型是生图的两种方法，所有涉及生图的地方使用同一判定和生成方式，包括题图转写、干净题面、重绘、去手写、讲解配图和插画。用途与方法正交，由模型结合实际能力和目标选择，不按入口或科目写死路线。

- [统一提示词正文与边界](docs/current/figure-generation-prompt-policy.md)
- [运行时/持久化/预算契约](docs/current/visual-generation-contract.md)
- [4E实施计划](docs/superpowers/plans/2026-10-18-flexible-js-drawing-implementation.md)
- [总路线](docs/REFACTOR-MASTER-LINE.md)

## 2. 选择原则

| 需要 | 通常优先 | 灵活处理 |
|---|---|---|
| 精确几何、比例、公式标签、参数交互、可编辑 | JS | 静态复杂视觉或能力/成本不同可选图像模型，只要满足精确/质量要求 |
| 照片纹理、自然形态、写实艺术、原像素编辑 | 图像模型 | 若目标只是规范语义图，可选JS重建，不能把重建说成恢复原像素 |
| 转写/干净题面/去手写 | 由目标判定 | JS与图像模型都可选，不按“来自照片”固定方法 |
| 讲解图 | 由教学表达判定 | 函数图常用JS，复杂视觉讲解可用图像模型；均走同一流程 |

模型可以在失败后说明理由并在同一操作/授权/预算内重新选方法，无需新增逐图确认。客户端不会暗换后端，不能以换方法降低保真/交互目标或绕过预算。表单、单选、文本输入和提交保持原生，不画成无法交互的图片。

## 3. 共用流程

所有入口构造FigureGenerationRequest→既有模型回合输出统一Proposal→必要时准备源证据→FigureGenerationCoordinator执行所选方法→共同质量门→FigureArtifact/引用/记录。problemKind仅为题面特征，不再作为“是否生图/使用哪种方法”的唯一开关。纯文字/公式转写无需强制生图。

GENERATE_FIGURE工具、CAPTURE_PARSE、IMAGE_PIPELINE_CLASSIFY、保存净化、库内重绘、显式重新生成共用同一policy；讲题工具名仍保留GENERATE_FIGURE。已有转写proposal不重复分类。历史打开和导出仅重放，不暗触发生图。

## 4. 两种后端

JS：固定QuickJS-KT源码→Android隔离Service→有界Scene/RenderScene→原生Canvas与PDF共享回放。函数/循环/采样自由组合group/path/label，不按题型增加renderer；无网络/文件/Android主机绑定；独立watchdog、stale generation防护、原生软期限、事件FIFO与Frame/checkpoint分离均按详细契约。

图像模型：复用已配置、实际验证的生成/编辑API；接具体imagePrompt、授权源图与约束，输出canonical像素资产。图像模型执行端不负责调用JS或规划工具。缺少实际能力时不广告。

两方法都要正确、清晰、标注完整，忠实转写不猜补未知；源图转写与讲解图使用宿主派生role，防止答案进入练习卷题目区。源图是否必须直接提供及所需格式按参与生成的模型实际需要校验，不能假装看过。

## 5. 同一记录与预算

operationId含宿主subject/basis/purpose/slot，不含method；切换不新开预算。两方法都记生图attempt/产物，外部图像调用和文本token/费用分开；缓存命中、JS动画、PNG预览和重复导出不重复算新生图。内部sourceType不定义业务生图次数。

所有Provider模型执行（含图像生成/编辑）补接同一持久ledger，逻辑操作上限6；讲题预留终答，独立净化本地终态不硬留聊天位。当前图片通道未接该ledger，实施必须补齐。JS本地执行不占外部派发。strictOffline只能复用兼容本地产物/程序，不能获取新的模型决策/图像。

公共operation/attempt与JS程序/快照/消息/题目引用非破坏迁移；Room当前67、目标下一空闲版本68，请求17→18或顺延。类型化artifact允许JS/vector与image结果统一消费，不把visualId塞进旧像素figureId，也不重复导出vector/PNG。

## 6. 交付与验证

4E.1所有入口先接共用管线，以现有图像方法验通，再纳入过门的JS静态后端；4E.2交互/原生表单；4E.3复杂来源与保真扩展。阶段不按用途限制方法。

已有桌面33帧/13检查、API34/x86_64/4KB探针6项仅证明JS可行性，不证明正式路由或真实模型质量。API23/arm64/16KB源码构建/R8、低端性能、两方法选择与双向切换、全部入口同policy、账本/迁移/备份、保真金标仍按计划验收。
