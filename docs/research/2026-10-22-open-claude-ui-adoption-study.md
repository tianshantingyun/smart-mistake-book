# Open Claude UI 全量参考研究与适配清单

日期：2026-10-22。本文保留原研究与设计输入；适用呈现和交互已进入全应用原生实施。**当前实现、消费者和核验状态以 [全量覆盖复核](2026-10-24-open-claude-ui-coverage.md) 为准**，完整业务/性能发布门继续独立保留。

用户目标：系统吸收 Open Claude UI 的设计与交互经验，用于智能错题本。用户已确认：**保留错题本青绿主色，采用 Claude 式布局与质感**。

关联：[长期参考索引](2026-10-22-open-claude-ui-reference.md)、[本轮设计草案](../superpowers/specs/2026-10-22-open-claude-ui-adoption-design.md)、[既有 UI 计划](../superpowers/plans/2026-10-19-ui-ux-redesign-plan.md)。

## 1. 证据范围

- 上游：[Caho1/open-claude-ui](https://github.com/Caho1/open-claude-ui/tree/c1f923ae0051a871941b98a0e2e3183a545e6a85)，固定提交 `c1f923ae0051a871941b98a0e2e3183a545e6a85`，本地 `D:\references\open-claude-ui`。
- 逐文件盘点 `code-examples/` 的 **36 个文件**、v1/v2 的 **14 个独立章节**；完整合订本与分卷内容重复，不重复计数。
- 深入核对重点：主题/动效预设、输入与会话呈现、贴底滚动、图片缩放、工具决策、弹窗、沙箱通信、限流恢复。其余模块完成入口与适用性归类，未逐个运行。
- 当前应用基线 HEAD：`f2adf825`；研究读取包含现有未提交工作。源码发现与视觉候选均不冒充真机复现结果。
- 文件摘要与章节/导出索引：`.jez/artifacts/open-claude-ui-adoption-2026-10-22/source-inventory.json`。此目录为本地研究证据，不依赖它阅读本报告。
- 作者所称的“官方参数”“生产级”“完全还原”属于上游声明。本次没有取得或验证 Claude 官方生产包，不能把作者分析当作官方产品规范。

## 2. 36 个源码文件的处理决定

以下源码路径均相对上游 `code-examples/`。状态表示设计取舍，**不表示已经在错题本实现或验收**。

| ID | 上游文件 | 可吸收的经验 | 错题本落点与处理 |
| --- | --- | --- | --- |
| S01 | `ArtifactsSplitPaneResizer.tsx` | 分栏最小宽、指针捕获、取消回滚、边界钳制 | 已接入原图/编辑双栏：拖动、最小宽、双击等宽、键盘/读屏调整与 Escape 回滚；同一编辑状态跨宽窄保留。列表/详情不新增路由，手机维持单页 |
| S02 | `AttachmentCard.tsx` | 缩略图、类型、文件名、移除动作各有位置 | 输入区待发附图、导出文件行与源图引用；保持扩展名和来源可辨，触区按 Android 规格处理 |
| S03 | `cds-pure.css` | 表现层可与组件运行时分开 | 采纳分层方式；原生 UI 使用主题令牌，不导入 CSS 或 React 运行时 |
| S04 | `cds-theme.css` | 表面、文字、边框、圆角、间距、动效集中定义 | `core:ui/Theme.kt` 等共享层；保留青绿，按语义建立必要层级，不复制整套灰阶制造闲置令牌 |
| S05 | `CdsButton.tsx` | 主/次/行内/危险动作层级、按压反馈 | 已移植 ClaudeButtons 的主/次/幽灵/图标与危险确认样式；48dp 触区、0.95/150ms 按压、悬停/焦点，39 文件控件消费者直接迁移，卡片内次级动作不重复描边 |
| S06 | `CdsMotionEngine.tsx` | 状态切换、分段控件和骨架屏的视觉例子 | 仅参考有意义的状态过渡；其演示状态与夸张形变不进入产品任务逻辑 |
| S07 | `ChatComposer.tsx` | 输入、附件、发送与局部工具组成稳定输入区 | 共用 `TutorSurfaceComposer` / `TutorChatComposer`；调整布局与焦点状态，保留纯图发送、草稿和阻断原因 |
| S08 | `ChatComposerChin.tsx` | 底部工具与错误恢复就地呈现 | 仅呈现已有可用设置/恢复动作；不新建离线发送队列或额外设置模型 |
| S09 | `ChatMessageBubbles.tsx` | 用户消息有轻容器，助手讲解使用阅读版心 | 复用 `TutorSurfaceStudentBubble` / `TutorSurfaceAssistantReply`；助手正文已具此方向，继续统一排版与间距 |
| S10 | `ClaudeAudioPlayer.ts` | 音频资源的替代方案、初始化和生命周期 | 当前范围不新增声音或语音模式；保留为已有/未来音频功能的参考，避免把提示音当任务状态 |
| S11 | `ClaudeDesktopHeader.tsx` | 系统窗口区域与内容区域职责分离 | 转化为 Android 状态栏、导航栏、键盘 inset 的单一消费规则；不移植 macOS 红绿灯占位 |
| S12 | `ClaudeMotionPresets.ts` | 可中断弹簧、参数来源、减少动画分支 | 原生统一动效策略；核对参数并换算，按用途命名，不照抄有冲突的预设名称 |
| S13 | `CodeBlock.tsx` | 内容块头部、可见复制反馈、等宽排版 | 已通过固定 0.17.0 源码的可选显示槽接入 ClaudeCodeBlock：语言栏、12dp 深底、13sp 等宽、逐块原文复制和 2.5 秒反馈；列表/引用也走原解析树。来源、Apache 许可与依赖检查见 2026-10-24 覆盖记录，不绕过 internal、不另造解析器 |
| S14 | `ConfirmationDialog.tsx` | 目的、影响、确认/取消动作集中表达 | 已接入 ClaudeAlertDialog：原生 Dialog、520dp/16dp/24dp、独立正文滚动、操作区、关闭与入场；长内容按钮可达已定向检查。原授权/草稿/securePolicy 不变，完整焦点验收保留 |
| S15 | `ContainerResponsiveBar.tsx` | 按容器而非设备名称调整工具栏 | 页头、筛选条、消息动作、输入附件条按实际剩余宽度与字号换行/收敛；高频动作保持可达 |
| S16 | `CoworkAgentCanvas.tsx` | 并行状态的区分、任务归属与结果关联 | 仅参考现有后台任务的身份与状态呈现；不增加多智能体工作台、协作空间或新的任务系统 |
| S17 | `createClaudeMainWindow.ts` | 宿主能力与表现层分离 | 沿 Android 原生宿主与现有模块依赖实现；不引入 Electron，也不复制其窗口权限设置 |
| S18 | `DraggableSessionList.tsx` | 稳定身份、拖动死区、落点反馈、取消 | 历史已采用紧凑图标/标题/时间/独立动作行；我的/四栏使用同一选中表面。历史仍按现有真实排序，未新增固定/拖拽排序模型 |
| S19 | `EpitaxyIframeBridge.tsx` | 不可信内容与宿主隔离、限定通信 | 参考边界原则，继续执行现有视觉 JS 隔离契约；不复制浏览器桥或开放任意宿主 RPC |
| S20 | `LiveMicActivityBars.tsx` | 状态可视化的噪声门限与平滑 | 当前范围不新增麦克风/录音；已有数据图的平滑不得改写科学数值或掩盖误差 |
| S21 | `McpToolDecisionCard.tsx` | 等待决定、执行与终态有不同呈现 | 有副作用申请沿 `TutorPendingRequestCard`；只读查阅保持 `TutorToolTraceLine` 灰色文字明细；结果由真实任务驱动 |
| S22 | `MediaLightboxViewer.tsx` | 锚点缩放、平移钳制、切图复位、焦点返回 | 原图/附图/生成图统一只读查看体验；手势使用触点中心，图身份与来源保持独立 |
| S23 | `ModelSelectorEffort.tsx` | 选项由能力集合提供、代价就地说明 | 只呈现当前真实能力；不复制五档枚举或固定推荐值，不假设任意服务都支持它 |
| S24 | `ModelSwitcherWithTokenGauge.tsx` | 名称、能力与真实使用量分层 | 复用既有模型配置和验证结果；没有真实 usage 就不显示百分比/精确仪表，不添加猜测配额 |
| S25 | `ReasoningAmbientGlow.tsx` | 活跃状态与静止状态区分 | 保留简洁文字/单一进度反馈；正文不闪烁、不模糊，不使用大面积持续光效分散阅读注意力 |
| S26 | `RichComposerWithMentions.tsx` | 输入法合成保护、上下文身份、输入高度控制 | 原生输入正确处理组合文本/IME；保留既有附题方式，本次不增加 `/` 或 `@` 命令体系 |
| S27 | `ScrollFadeContainer.tsx` | 边缘提示、触顶触底消隐、避免侵占正文 | 仅在确有溢出的横向附件/工具区域评估使用；题干、公式、正文和操作不靠遮罩隐藏 |
| S28 | `SegmentedControl.tsx` | 选择状态、轨道与焦点一致 | ClaudeSegmentedControl 已接错题筛选及真实模型协议：1dp 轨道、120ms 滑块、方向键/Home/End/RTL、选择语义；窄屏大字滚动且保留全部名称 |
| S29 | `StatusIndicators.tsx` | 加载占位、运行、就绪、失败可区分 | 复用 `PageStateFrame` 和现有任务状态；有内容时局部提示，不伪造生成百分比或同时堆多个加载动画 |
| S30 | `StreamingDiffViewer.tsx` | 变更对照、稳定行对齐、流式增量思想 | ClaudeRevisionDiff 已消费原题面字段 diff：修改前后、增删底色、工具栏、复制和宽窄布局；没有引入 Git diff 解析/代码审查器，视觉操作不提交修订 |
| S31 | `tailwind.config.js` | 组件统一消费语义配置 | 转化为原生令牌消费与检查；不把 Tailwind 配置作为 Android 依赖 |
| S32 | `ThinkingAccordion.tsx` | 摘要/详情、生成状态和完成状态分层 | 已移植思考外壳/分隔/内距、真实三点运行反馈、完成点与复制；保留自动展开/完成收起，未编造耗时，动画不改变答案曝光 |
| S33 | `useClaudeRovingFocus.ts` | 键盘方向导航、退出、RTL、焦点恢复 | 原生 focus/semantics 与外接键盘、TalkBack 验收；不复制 DOM tabIndex 实现 |
| S34 | `useClaudeScrollPinning.ts` | 用户滚动优先、靠近底部跟随、回到最新 | 修复当前 `TutorConversationFrame` 的贴底判据与悬浮按钮定位；按真实几何和输入来源判定 |
| S35 | `useClaudeSharedElementTransition.ts` | 缩略图与大图身份连续、退出恢复、能力降级 | 图片查看采用原生过渡；按当前 Compose 能力选择共享元素或淡入，系统关闭动画时直接完成 |
| S36 | `useRateLimitBackoff.ts` | 等待原因、下一次尝试与手动恢复可见 | UI 读取已有任务层的恢复状态；不在 Compose 再建定时器调模型，不改变既有幂等与派发预算 |

## 3. 14 章文档如何落到当前产品

| 文档 | 采用内容 | 限制 |
| --- | --- | --- |
| v1/01 设计哲学与设计令牌 | 表面分层、可读排版、边框与圆角职责 | 色值与字号以本应用青绿和阅读需求为准 |
| v1/02 基础原子控件 | 动作层级、选择反馈、附件结构、确认语义 | 桌面紧凑尺寸不作为手机触区 |
| v1/03 会话与输入 | 讲解阅读、输入法、工具痕迹和输入区 | 保留本应用会话/答案/授权契约，不抄模型品牌或支持矩阵 |
| v1/04 动效与光影 | 令牌化、减少动画、按场景区分 | 阅读内容不扫光模糊；原生渲染优先 |
| v1/05 多媒体与产物 | 图像坐标变换、分栏约束、隔离、对照 | 图像查看和生图独立；不新增 Web 工件宿主 |
| v1/06 桌面架构与协作 | 状态归属、滚动、自适应、焦点与恢复 | macOS、多代理、桌面历史重排无现成业务对应项 |
| v1/07 工程与资产 | 表现与宿主分离、资源可用性、规范入口 | 不替换本项目编译器、公式解析器或渲染架构 |
| v1/08 Agent 开发指南 | 可追溯令牌、边缘情况、自检思路 | 其提示词不是本项目指令；参数必须回到源码核对 |
| v2/01 版本边界 | 按版本解释规格，避免混用 | 上游版本声明不等于本次验证了官方客户端 |
| v2/02 新特性 | 草稿价值、触控区域、离线资源 | 不引入企业策略、语音或后台自动发送等新业务 |
| v2/03 架构对比 | 适度密度、状态隔离、轻量动效 | 性能提升、缓存成本等数字没有本应用证据，不写成验收结论 |
| v2/04 底座与参数 | 动效角色、参数来源与单位换算 | 同章包含不同客户端版本；不称为一份统一官方常量表 |
| v2/05 参考实现 | 模型能力配置、输入工具布局 | 示例与真实后端执行严格区分 |
| v2/06 总结 | 结构、反馈、性能一起考虑 | 不按“模块全部出现”判断完成，而按适用项与实测结果判断 |

## 4. 上游不能原样搬用的具体问题

| 证据 | 研究结论 | 采用方式 |
| --- | --- | --- |
| v1/08 的弹簧决策树与 `ClaudeMotionPresets.ts` | 名称与参数不一致；源码 `GENTLE=380/18/1` 明确标为作者自建，`HEAVY_SNAP` 含 `mass=0.1` | 以用途和方程为依据，重新定义原生预设，保留来源说明 |
| `McpToolDecisionCard.tsx` 的 `handleAction` | 批准后使用 1200ms 定时器直接显示 success，是模拟执行 | 禁止移植模拟状态；批准回调后持续观察现有持久化任务 |
| `ConfirmationDialog.tsx` | 注释声称焦点锁定；本文件只有 Escape 监听、对话框语义等，没有对应完整焦点锁定逻辑 | 使用原生模态组件并实际验证焦点进入、返回、返回键与大字可达性 |
| `EpitaxyIframeBridge.tsx` | 把 `hasBeenActive` 作为一次高危操作用户激活依据不足；还开放了 forms/popups/modals 等能力 | 只采用隔离与限定消息思想；执行现有受限 JS 协议和原生操作权限，不复制该桥 |
| `useRateLimitBackoff.ts` | 注释把 ±15% 抖动称为 Full Jitter；实际公式也可能缩短 Retry-After 指示的等待时间 | 不引入第二重试机；未来修改重试层时单独核验服务器等待下界、幂等与现有预算 |
| `docs/claude-components-storybook.html` | 声称离线预览，实际外链 Tailwind、React/ReactDOM/Babel 与 Google Fonts | 保存源码参考；不能据此宣称离线演示已交付 |
| `ModelSwitcherWithTokenGauge.tsx` 等示例 | 带硬编码模型与展示数据 | 仅参考信息组织；所有能力、用量从应用实际数据读取 |
| v2/04 §4.4 与 v2 标题 | 分别提到 2.42304.2 和 2.37337.1 | 记录版本边界，不把其常量组合直接用于“精确复刻” |

`hasBeenActive` 表示页面过去发生过交互，不代表当前消息获得了一次操作授权，见 [HTML 用户激活规范](https://html.spec.whatwg.org/dev/interaction.html#the-useractivation-interface)。这是一项从 API 语义推导的适配限制，不是对上游部署环境做出的完整安全审计。

## 5. 当前错题本的具体改进依据

下列路径以应用仓库为根；尚未做本轮设备复现，行为风险需在实施时先做定向重现。

| ID | 已读源码与事实 | 要解决的问题 |
| --- | --- | --- |
| A01 | `core/ui/.../Theme.kt`：共享横向边距 26dp；错题本新工具栏/题卡使用更紧凑布局 | 相同窗口下不同页面的内容密度与页头层级不统一，采用按角色迁移的边距规范 |
| A02 | `ThinkingCollapsibleCard.kt`：详情使用 `bodySmall` 与 `InkMuted` | 正常说明字色需要变深；InkMuted/Paper 的静态对比度约 3.010:1，不宜承担普通正文 |
| A03 | `Theme.kt`：`ErrorWarm=#E36F27`；`TutorSurfaceStartError` 等将它用作普通小字 | 将错误底色/装饰与错误文字分开；ErrorWarm/Paper 约 3.003:1 |
| A04 | `TutorConversationSurface.kt`：跟随状态按末条索引可见判尾，滚动助手 `isAtTail` 却使用末条底边几何 | 同一列表使用不同判据；长末条只露出一部分时仍可能被当作贴底，需覆盖用户向上阅读时的流式更新 |
| A05 | 同文件：回到最新按钮固定 `bottom=80.dp`，输入框最多四行且附图区可增长 | 按实测输入区/附件高度定位按钮，避免覆盖正文或输入动作 |
| A06 | 同文件：发送图标显式 `tint=JadeActive`，外层 `IconButton(enabled=canSend)` | 禁用语义与显式图标颜色可能不一致，需实测并采用统一状态色；不改变发送条件 |
| A07 | `TutorSurfaceMessages.kt`：消息动作使用不换行 Row；`OutlineActionChip` 自带描边 | 大字/长标签要能排下；复制、停止、重试按现有状态出现，减少套框 |
| A08 | `TutorSurfaceStudentBubble` 总是占 86% 宽；助手正文已经沿统一 `TutorPrompt` 渲染 | 用户短消息可按内容自然收窄；保持助手现有宽版心，不为改样式重写消息存储与渲染体系 |
| A09 | `CaptureSourceImagePreview.kt`：点击切换折叠/展开；`BoundedLocalImage` 是有界采样预览 | 将查看细节完善为有界、只读的缩放体验；不能把放大采样图宣传成已经读取原始分辨率 |
| A10 | `ReviewRoute.kt`：问候与分节之间多段 24/30dp 间距，开始按钮固定 58dp | 压缩重复标题留白，按钮采用可增长最小高度；保留队列、恢复与自评语义 |
| A11 | `ProfileRoute.kt`：32sp 页题、多个绿竖条分节和分隔线 | 页题与错题本的 24sp 统一角色；学习情况与设置分组，减少重复视觉标记 |
| A12 | `TutorToolTraceLine.kt` 已实现可展开灰色文字；`PageStateFrame` 已有统一状态形状 | 这是已有基础，继续复用；不把只读工具调用改成上游的大型审批卡，也不新增第二套空态框架 |

旧 `core:ui/MessageBubble.kt` 包含 280dp 上限，但当前已追到的会话主路径是 `TutorSurface*`。不能仅改旧组件就声称所有会话完成改版；实施证据必须来自真实入口。

以上颜色计算为不透明 sRGB 色对计算，不能代表透明叠加、所有控件状态或图片内文字已经通过无障碍检查。候选错误文字 `#A23E24` 在 Paper 上约 6.068:1，只是待视觉验收候选值。

## 6. Android 参数与实现依据

原生适配采用 Android 官方的 [Compose 动画说明](https://developer.android.com/develop/ui/compose/animation/quick-guide)、[触区及语义](https://developer.android.com/develop/ui/compose/accessibility/api-defaults)、[窗口尺寸分类](https://developer.android.com/develop/adaptive-apps/guides/use-window-size-classes)、[inset 消费](https://developer.android.com/develop/ui/compose/system/insets-ui)、[多点触控](https://developer.android.com/develop/ui/compose/touch-input/pointer-input/multi-touch)。

Compose 弹簧使用 stiffness 和 dampingRatio。根据二阶方程与 [AndroidX SpringSimulation 源码](https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/animation/animation-core/src/commonMain/kotlin/androidx/compose/animation/core/SpringSimulation.kt)，在同一时间单位和位移标度下，从 `k,c,m` 转换时须用 `stiffness=k/m`、`dampingRatio=c/(2√(mk))`；只换算阻尼而漏掉质量会改变自然频率。

| 上游源码预设 | k / c / m | 归一化 stiffness | dampingRatio |
| --- | --- | --- | --- |
| BOUNCY | 600 / 30 / 1 | 600 | 0.6124 |
| SNAPPY | 320 / 28 / 1 | 320 | 0.7826 |
| GENTLE（作者自建） | 380 / 18 / 1 | 380 | 0.4617 |
| HEAVY_SNAP | 1200 / 80 / 0.1 | 12000 | 3.6515 |

此表是计算结果，不是最终产品动效配置。可见性阈值、首帧、像素密度、初始速度与动画关闭策略仍影响实际表现；不能据此宣称两个运行时逐帧一致。

## 7. 与当前计划、在飞工作衔接

统一入口、题图外显、真实队列、四底栏、会话隔离、图像生成统一判定等现有裁定继续生效。对应 UI-00～11 的拟补充项见设计草案；不重排业务 PRD/WG/4E 启用门。

当前 `LearningMasteryScreen.kt`、`ReviewReminderCoordinator.kt`、`BatchImportRoute.kt`、`TutorSessionPanel.kt` 等存在其他未提交工作。落地前须重新核对占用与 diff，先从干净共享呈现层及明确独立的页面切片开始；同文件只设一个写入者，不把现有改动整体提交或恢复。

本轮研究没有修改应用源码、数据、模型配置或运行权限；没有执行应用测试、调用模型、安装上游依赖、提交或推送。最终交付须按每项适用性的真实实现与设备证据逐项关闭，不能用本报告的文件数量证明实现完成。
