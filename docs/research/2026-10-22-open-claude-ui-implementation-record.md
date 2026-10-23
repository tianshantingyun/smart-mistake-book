# Open Claude UI 原生移植实施记录

更新：2026-10-23。用户已批准七批实施，并追加要求“排版布局、交互形式、使用手感，直接照搬”。当前采用源码结构和量度移植；本记录区分实际实现、定向验证和仍开放的整体验收。

执行入口：[实施计划](../superpowers/plans/2026-10-22-open-claude-ui-adoption-implementation.md)。参考源码固定在 `D:/references/open-claude-ui`，revision `c1f923ae0051a871941b98a0e2e3183a545e6a85`。这是Caho1的开源参考实现，不将其中“官方”注释当作独立取证结论。上游MIT许可证已随应用保存在 `app/src/main/assets/open-source/open-claude-ui-LICENSE.txt`。

## 1. 实际改动

| 层次 | 落点与结果 |
| --- | --- |
| 全应用呈现 | `Theme.kt`、`Components.kt`及既有页面消费者。青绿主色保留，中性画布/白色浮层/墨色说明沿上游；页题20sp、分节16sp、输入15sp、紧凑控制12sp按角色消费，题卡18/15/14sp保留。正常错误文字独立用ErrorText，不把装饰色用作小字 |
| 输入与手感 | `ClaudeLayout.kt`直接登记来源量度；`TutorChatComposer`采用20dp外壳、14dp内边距、15/21sp整行输入、下方工具与32dp发送视觉、48dp触区、0.95按压缩放/150ms过渡。原生组合文本与长度边界保留，发送在事件时检查最新文本 |
| 欢迎与会话 | 新会话为欢迎标题→输入→实际建议入口；同一个输入实例在开始页与消息底部之间移动。已有会话按真实几何贴尾，向上看长回复时不抢读，点击回到最新抵达实际尾部 |
| 消息 | 用户气泡采用上游85%最大宽、18dp圆角、16/12dp内边距；助手身份在正文上方，正文保留阅读宽度和4dp缩进。去掉侧边头像挤占和正文边框，动作可换行 |
| 工具与选择 | 已有回答方式移到输入工具栏，弹出原有两种选项；选择当前项不写入，选择另一项调用原切换回调。思考详情和只读工具明细保持真实状态、清晰文字及48dp展开触区 |
| 图片 | `LocalImageViewer`只读既有本地资产，有界缩放/拖动/双击/方向按钮/复位/关闭、真实预览尺寸与缺图提示。原图、重绘、历史重绘、讲解图共用来源标题。原图/附图/生成图消费者接线；打开、缩放、返回不派发生成 |
| 编辑与其他页面 | 详情题面前置，维护项后置；840dp且高度足够时原图和同一编辑组件并排，窄屏单列。录入、复习、我的、设置、导出沿原回调迁移可增长控件、行内动作和可读说明；四底栏及来源返回保持 |
| inset | 导航壳消费一次底部inset；会话外框处理IME，使悬浮按钮也避开键盘。输入可见行数按剩余空间1～4行调整，完整草稿不变、不缩系统字号 |

## 2. S01–S36实际处理

逐文件研究仍见[参考清单](2026-10-22-open-claude-ui-adoption-study.md)。以下说明实现决策，不能由“参考过”推断新增能力。

| 编号 | 采用/保留/不适用的实际决定 |
| --- | --- |
| S01 | 原图/编辑双栏与同一编辑状态；未新增拖动分栏器或列表详情双路由 |
| S02 | 本地附件卡、独立移除、查看入口和资产身份保持 |
| S03/S04/S31 | React/CSS配置转成Compose语义主题与实际消费者；未加入Web运行时 |
| S05 | 主/次/行内动作职责；普通动作避免重复强边框，实际触区48dp |
| S06/S12/S25/S35 | 保留有意义的原生反馈，发送按压采用上游量度；未宣称交付全部弹簧、光效或共享元素动画 |
| S07/S08/S26 | 上下分层输入、紧凑工具栏、原生IME合成、草稿、同事件提交与发送；无第二发送队列 |
| S09 | 用户气泡量度、助手上方身份与无框正文、自然宽度和动作条 |
| S10/S20 | 本轮无音频/麦克风业务，不新增入口或权限 |
| S11/S17 | Android系统栏/IME/inset职责分离；不搬Electron/macOS窗口代码 |
| S13 | 继续现有Markdown/数学引擎，不另起解析器 |
| S14 | 继续原生模态和未保存规则；没有照搬上游未证实的完整DOM焦点锁定 |
| S15 | 工具/页头按可用宽度与字号换行，输入随实际高度收敛 |
| S16 | 没有对应的多代理工作台业务，不添加桌面工作区 |
| S18 | 会话历史沿既有排序和身份；不新增历史拖动排序 |
| S19 | 视觉JS仍服从现有隔离契约；未复制开放浏览器宿主桥 |
| S21 | 只读工具沿文字明细，有副作用沿已有申请卡和真实回执；不复制模拟成功计时器 |
| S22 | 本地图片锚点缩放、平移边界、复位与关闭，含非手势方向操作 |
| S23/S24 | 模型设置只用实际能力和验证结果；没有硬编码模型菜单、假usage或配额仪表 |
| S27 | 横向附件沿LazyRow；不使用渐隐遮罩盖住题干、公式或操作 |
| S28 | 保留原有选择语义；回答方式用真实选项弹层，不伪装不可用选择 |
| S29 | 局部真实加载/失败；不以假百分比或打开页面制造完成记录 |
| S30 | 保留既有版本/对照/CAS，不让视觉差异提交修订 |
| S32 | 思考摘要/详情可读、48dp展开头；曝光事实不由动画决定 |
| S33 | 原生按钮/标签、选择与非手势图片操作；完整TalkBack和外接键盘人工巡检仍未验证 |
| S34 | 同一尾部几何判据；长回复阅读优先，欢迎从头开始 |
| S36 | 沿已有恢复协调器，不在UI新增模型重试定时器 |

## 3. 构建与测试证据

证据根：`.jez/artifacts/open-claude-ui-implementation-2026-10-22/`。设备仅使用本任务 `emulator-5562` / API34，不清用户设备数据。安装包明确选 `localFirstDebug`，另检查 `strictOfflineDebug` 组装；本项目通用 `assembleDebugAndroidTest` 存在多flavor歧义。

- 前一集成构建 `integrated-verification.log` 成功；`compatibility-and-lint.log` 包含strictOffline组装、四模块lint及ExplicitRedrawService定向JVM成功。后续源码移植结果单列，不能由旧APK代替。
- 最新六模块JVM：core:ui 48、tutor 255、library 65、capture 148、review 72、profile 6，共594例、0失败/错误。日志 `claude-fidelity-green-build.log` 中组装及core:ui/tutor lint通过；同一次app lint在core:data报ZipException，重查结果另记，不将该命令写成全绿。
- `claude-fidelity-tutor-device.log`：24/24通过，覆盖输入结构与选择器、组合文本/纯图、真实IME提交到会话、真实键盘+附图+2倍字、长回复/欢迎、图片操作与有界加载、共享组件及大厅滚动。
- `claude-fidelity-default-device.log`：真实主应用四根页与设置可达，1/1通过；945×2100px/density420，即360×800dp，fontScale1.0。`claude-fidelity-default/`保存四根页截图，已查看智能体和我的。最初引起反馈的图是320dp/fontScale2.0压力图，不将它冒充默认画面。
- `claude-fidelity-library-green-device.log`：27/27通过，涵盖列表/多页/损坏图、独立收藏、原生文档编辑、导出范围、大字动作、图片角色与显式重绘。上一条命令误写了一个不存在的测试类名，出现初始化失败；已改用真实类名完整重跑，没有删产品断言。
- 最新主应用矩阵各1项通过：360dp/font1.0、320dp/font2.0、412dp/font1.3、600dp/font1.0、840dp/font1.0及横屏800×360dp。日志为`claude-fidelity-{default,320-font2,412-font13,600-font1,wide,landscape}-device.log`，对应目录保存四根页截图。
- `claude-fidelity-wide-editor-device.log`：实际840dp窗口中编辑从双栏切到320dp单栏再切回，内部草稿仍在，1/1通过。截图使用缺图夹具，验证参考区/编辑状态，不作为真实题目内容截图。
- `claude-fidelity-animation-off-device.log`：三项系统动画比例设为0后，选择器/输入结构和查看器7/7通过；完成后恢复原比例、360dp窗口与fontScale1.0。
- 最后按可移动方向禁用查看器按钮，避免整张图在该方向仍完整可见时出现无效操作；`viewer-axis-final-device.log`中5/5通过，包含横图可左右移动而上下按钮禁用的真实像素/操作断言。
- `claude-fidelity-assemble-final.log`：独立新进程串行组装`strictOfflineDebug`、`localFirstDebug`及其测试APK成功。app lint两次出现`zip END header not found`（第二次在Tutor既有曝光测试分析中）；未跳过检查或删除缓存。`claude-fidelity-lint-fresh-process.log`中独立新进程重查成功，SARIF为0错误/44警告，不能称为零警告。
- 最后方向按钮调整后，`final-current-ui-build.log`中core:ui JVM/lint与localFirst整包成功（1m25s）。期间一次KSP增量缓存路径缺失，另一次遇到并行备份代码的字段初始化编译错误；最新备份文件已自行更新初始化顺序，本轮未编辑该文件。最终使用带引号的`'-Pksp.incremental=false'`进行串行新进程构建，没有删除缓存、跳过检查或覆盖在飞源码。此处不对缓存异常原因作已证实归因。
- 最终安装上述当前APK，`final-current-app-device.log`中四根页/设置1/1通过；最新真实主应用截图为`final-current-app-screenshots/`。`final-verification-summary.json`保存设备日志计数、源码/文档摘要及APK指纹；MIT许可证已直接从APK内读取核对，文档相对链接检查无缺失。

## 4. 审查、失败定位与开放门

独立最终审查给出With fixes：图片缺非手势平移，重绘在查看器被误称原图。已实现方向按钮和统一标题；方向栏初次出现引起缩放复位，由设备失败定位为视口高度变化，改为稳定占位后最新24项设备回归通过。IME旧快照的疑点由真实提交正文证据撤回；没有靠定时器延迟发送。

真实附件/键盘测试发现阅读区不足，已将输入可见行数按可用高度调整；后续附件按上游改为紧凑横卡，仍使用真实组件复验。草稿正文、字号与动作语义保持。

`app-final-device.log` 的14项主应用业务/恢复回归中11项通过、3项自评/撤销失败。诊断出现 `ImmutablePayloadConflictException: assessment_presentation ... already exists with a different payload`，路径为 `AttemptTransactionDao.loadOrCreatePresentation` / `materializeAnswerReveal` / `recordAnswerReveal`，证据 `reveal-exception.log`。没有放宽事务不变性或删失败断言；临时诊断修改已按原始字节还原。该问题沿PRD-05处理，UI改造不关闭业务门。

仍开放：实际中文输入法候选界面与多真机；TalkBack/外接键盘完整巡检；同URI原地改写字节的版本失效；极长/多页大量图片和长列表峰值内存/帧耗；完整真实录入→保存→讲题、导出→成果、进程恢复等端到端矩阵。1600px采样边界与小样本回归不等于峰值性能证明。JS与IMAGE_MODEL统一生成合同、4E/WG/PRD门均不因本记录关闭。

## 5. 共享工作区与所有权

本任务开始HEAD为f2adf825；实施期间其他工作继续提交，不能把当前HEAD差分当作本任务独占改动。原始字节保存在`source-baseline/`，用户纠偏前的状态另存`claude-fidelity-before/`。更新后的`owned-files.json`共72个源码/测试/许可证文件，`owned-changes.diff`是基线到快照的审阅包；ExplicitRedrawService、MistakeDetailRoute和CapturedReviewQuestionContent包含其他并行到达的业务hunk，不将全部差分归为本轮UI实现。最终指纹核对又发现`SecondaryScreens.kt`到达6C-2备份分类选择的新代码，晚于上述APK/lint快照；保留该工作并单列兼容构建，备份业务本身不冒记为本轮已验。文档状态另以本记录和计划为准。

ExplicitRedrawService仅为解除编译阻塞将既有值64移到文件私有作用域，未改生成策略。其余数据库/导出/知识库/模型在飞工作未reset、stash、覆盖或整体暂存。本轮未执行提交或推送。

后到的6C-2设置代码已在`shared-backup-ui-compatibility-build.log`中兼容组装通过（1m14s），`shared-backup-ui-compatibility-device.log`中四根页/设置1/1通过，新默认截图位于`claude-fidelity-default-latest/`。其备份选择/闭包/恢复语义由并行工作验收；本轮只核对其未覆盖已移植呈现。完整源差分包可能包含该新增业务hunk，不得整包当作独占UI改动提交。

## 6. 2026-10-23 全前端扩展批

用户明确暂缓全面验收后，继续按上游源码移植共享按钮、常驻标签输入、确认弹窗、分段/菜单、紧凑列表/页头/底栏、思考状态、复制、修订 diff 与可调整宽屏分栏。58 个源码/测试文件变化，10 个新共享实现，39 个文件直接迁移控件调用；保留所有既有业务回调和并行备份工作。详情、来源映射、默认主应用截图与 50 个 JVM/11 次设备定向通过证据见 [扩展移植记录](2026-10-23-open-claude-ui-expansion.md)。代码块工具栏的 internal API 接入尝试已完整撤回，未绕过封装或新增解析器。原全面业务/性能/真机门继续保留，不提交推送。
