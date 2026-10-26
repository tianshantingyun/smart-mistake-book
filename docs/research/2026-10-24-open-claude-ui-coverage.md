# Open Claude UI 全量覆盖复核


2026-10-25 全量收尾已恢复：新增窄屏大字三键、选项圆标、中文学科朗读和单卡多页可见解码修复。当前构建、逐页证据、长图内存、输入法与剩余门统一见[全量收尾核验](2026-10-25-ui-completion-audit.md)；下文59项定向结果是前一批范围，不能替代本轮结果。
参考固定在 `Caho1/open-claude-ui@c1f923ae0051a871941b98a0e2e3183a545e6a85`。本表覆盖 36 个源码样例、14 章独立文档和本应用的实际消费者；“接入”与“设备通过”分别记录。完整发布验收按用户要求暂缓，不能据此把未验证项写成通过。

本次继续使用已批准的 Superdesign 青绿方向。保留已有业务、四底栏和 Android 原生能力；按钮、版面、输入、反馈和动画按照上游源码移植。不复制样例中的假成功、模型能力和用量。

2026-10-25 更新：本轮适用UI移植与源码对照已闭合，找到的实际接线缺口已补齐，最新设备定向回归59/59通过，当前四根页触屏截图已查看。此结论覆盖下表列明的适用裁定、消费者与方法，不等于原计划的完整业务、性能、真机输入法及无障碍发布验收。

## 1. 源码到消费者

路径前缀与执行计划一致：U=`core/ui/.../core/ui/`，T=`feature/tutor/.../feature/tutor/`，L=`feature/library/.../feature/library/`，C=`feature/capture/.../feature/capture/`，A=`app/.../smartmistakebook/`。精确路径、摘要和调用位置另存本轮 `coverage-inventory.json`。

| ID | 上游源码 | 已接入的原生实现与实际消费者 | 本轮核验 / 边界 |
| --- | --- | --- | --- |
| S01 | ArtifactsSplitPaneResizer.tsx | U/ClaudeSplitPane、ReferenceEditorPage → L/MistakePaperEditRoute、MistakeRevisionEditRoute | 拖动、钳制、同一草稿、读屏调整；现有设备用例复验 |
| S02 | AttachmentCard.tsx | T/TutorMessageAttachments → 大厅与会话；导出记录行、U/AttachedImageCard | 补真实本机文件名、来源、编号与查看器标题；读取失败保持编号，不猜文件大小 |
| S03 | cds-pure.css | U/Theme、ClaudeLayout 与共享控件 → feature/app 消费者 | 使用原生令牌，CSS 不作为运行时依赖 |
| S04 | cds-theme.css | U/Theme、TutorMarkdownTokens、ClaudeMotion | 保留 Jade；阅读、控件、边框和状态令牌独立；普通字色对比度有 JVM 检查 |
| S05 | CdsButton.tsx | U/ClaudeButtons、ClaudeInteractiveSurface、ClaudeFocusIndicator、Components → 全局控件 | 主次动作、悬停/焦点/按压与 48dp 触区；补选择 chip 语义；修复青绿实心按钮内侧焦点不可辨，按实际底色选择至少 3:1 边框，真实像素设备检查通过 |
| S06 | CdsMotionEngine.tsx | U/ClaudeMotion、ClaudeSelection、ClaudeStatus → 真实 UI 状态 | 只迁移有消费者的动画，演示按钮不进入业务 |
| S07 | ChatComposer.tsx | T/TutorConversationSurface 中的 TutorChatComposer、TutorSurfaceComposer → 大厅/拍照/错题讲题 | 既有 IME、附件、纯图发送与同事件提交保持；输入在上、工具在下 |
| S08 | ChatComposerChin.tsx | 统一输入工具栏 → 已有模式、附件与错误恢复 | 只显示实际已接通的能力 |
| S09 | ChatMessageBubbles.tsx | T/TutorSurfaceMessages、TutorSurfaceStudentBubble/AssistantReply | 用户自然宽度、85% 上限、助手无框正文与阅读版心 |
| S10 | ClaudeAudioPlayer.ts | 当前无对应音频功能 | 明确不新增语音/提示音；不适用项，不冒充已移植 |
| S11 | ClaudeDesktopHeader.tsx | A/SmartMistakeBookRoot 与 Tutor/编辑页面 inset | 状态栏、键盘与导航栏由原生负责；不增加桌面窗口按钮 |
| S12 | ClaudeMotionPresets.ts | U/ClaudeMotion → 按钮、折叠、弹窗、导航、分段控件 | 系统动画关闭与运行时切换；无业务事件依赖动画完成 |
| S13 | CodeBlock.tsx | U/ClaudeCodeBlock、ClaudeCopyButton → AiReplyRichMarkdown → 助手终稿 | 本轮接语言栏、深色代码块、逐块原文复制；列表/引用共用同一解析树，四项设备测试通过 |
| S14 | ConfirmationDialog.tsx | U/ClaudeAlertDialog → 原有确认/设置弹窗 | 520dp/16dp/24dp、正文滚动、操作区、取消与真实授权回调；长正文设备检查通过 |
| S15 | ContainerResponsiveBar.tsx | 页头/消息动作 FlowRow；T/TutorExportSheet | 栏数、字号、附加内容自动换行与可增长步进器；窄屏/大字可达及准备期间锁定的定向设备检查通过 |
| S16 | CoworkAgentCanvas.tsx | 现有任务卡的真实身份/结果反馈 | 无现成协作工作台，不引入新任务系统 |
| S17 | createClaudeMainWindow.ts | Android Activity/Compose 宿主 | Electron 与 macOS 窗口能力不适用 |
| S18 | DraggableSessionList.tsx | U/ClaudeList → T/TutorHistoryRoute；原有排序/删除 | 接紧凑会话行与独立动作；不新增固定、拖拽重排数据模型 |
| S19 | EpitaxyIframeBridge.tsx | 既有视觉 JS 隔离与宿主白名单 | 边界原则适用；不复制宽权限 iframe/RPC；生图 JS/IMAGE_MODEL 判定不变 |
| S20 | LiveMicActivityBars.tsx | 当前无录音功能 | 不新增麦克风权限或假波形 |
| S21 | McpToolDecisionCard.tsx | T/TutorPendingRequestCard/Commands/VM → 大厅、TutorSessionAttachmentArea | 每卡防连点、真实执行忙态、禁用两种决定与准确目标文案；命令并发 JVM 和设备忙态通过，沿原执行/落库顺序 |
| S22 | MediaLightboxViewer.tsx | U/LocalImageViewer/BoundedLocalImage → 原图、附图、生成图、来源引用；L/LibraryInlineImages、SplitImportReviewRoute；R/CapturedReviewQuestionContent；T/CapturedTutorSessionRoute | 补齐四处真实遗漏入口；原图多页顺序、返回位置与来源身份保持。列表题图点击仍进详情，另设放大动作；框选画布和拆题选择缩略图保留编辑/选择手势。对应真实消费者设备测试通过 |
| S23 | ModelSelectorEffort.tsx | 既有 ModelSettings/能力控件 → 原输入工具与设置 | 不新增硬编码五档或虚构支持矩阵 |
| S24 | ModelSwitcherWithTokenGauge.tsx | 实际模型配置、协议分段与验证反馈 | 没有真实 usage 不显示仪表；保留真实启用门 |
| S25 | ReasoningAmbientGlow.tsx | U/ClaudeQuietDots、ThinkingCollapsibleCard → 当前运行状态 | 保留低噪声反馈；正文不闪烁/模糊，不移植大面积循环光效 |
| S26 | RichComposerWithMentions.tsx | T/TutorConversationSurface、TutorComposerTextPolicy 的原生 TextFieldValue/IME 与已有附题入口 | 不新增斜杠/提及协议；已有真实输入连接回归 |
| S27 | ScrollFadeContainer.tsx | T/PendingMessageImagesRow、MessageImagesRow | 本轮按真实横向溢出显示数量/滑动提示；不遮住正文、公式或移除按钮 |
| S28 | SegmentedControl.tsx | U/ClaudeSegmentedControl → L/LibraryRoute、A/SecondaryScreens | 原轨道/滑块/120ms、单一 Tab 入口、方向键/Home/End/RTL；修复实际四种协议标签换行后触区高度不一致及选中项焦点不可辨。双向 Tab、RTL、大字溢出、整格点击与焦点像素检查通过 |
| S29 | StatusIndicators.tsx | U/PageStateFrame、ClaudeStatus → 现有页面与任务 | 真实加载骨架、局部运行提示、完成/失败分离；骨架原失败来自合并语义树，改用真实未合并子节点定位后通过；未改产品去迎合测试 |
| S30 | StreamingDiffViewer.tsx | U/ClaudeRevisionDiff → L/MistakeRevisionEditRoute | 原题面修订字段、增删对照、复制和宽窄布局；不新增 Git diff 解析器或自动提交修订 |
| S31 | tailwind.config.js | 原生令牌定义与消费者 | 不导入 Tailwind 依赖 |
| S32 | ThinkingAccordion.tsx | U/ThinkingCollapsibleCard/ClaudeDisclosure → 实际思考区域 | 生成/完成、摘要/正文分层；减少动画分支设备检查已通过 |
| S33 | useClaudeRovingFocus.ts | U/ClaudeSelection、ClaudeModalKeyboard、ClaudeFocusIndicator 与原生模态 | 单一 Tab/方向键/退出；Escape 沿原取消策略，不能绕过不可取消确认。图片关闭后焦点回到真实触发控件的设备测试通过 |
| S34 | useClaudeScrollPinning.ts | T/TutorTailFollowPolicy、TutorConversationSurface | 用户滚动优先、真实尾部几何、回到最新按钮避开实际输入高度；长回复、键盘/附件/多行、同事件提交与纯图发送回归通过 |
| S35 | useClaudeSharedElementTransition.ts | LocalImageViewer、claudeModalEntrance | 原生淡入/缩放降级、来源身份独立；不宣称跨运行时逐帧一致 |
| S36 | useRateLimitBackoff.ts | 既有任务恢复/失败呈现 | 不在 Compose 增加重试器，不改等待下界、幂等键、重试预算 |

## 2. 十四章逐项落点

| 独立章节 | 对应实现/裁定 |
| --- | --- |
| v1/01 设计哲学与令牌 | S03/04/31：中性色、青绿、字色与角色尺寸 |
| v1/02 原子组件 | S02/05/14/23/24/28：原生共享控件与真实选项 |
| v1/03 会话与输入 | S07/08/09/21/26/32/34：统一 TutorSurface 的真实消费者 |
| v1/04 动效与光影 | S06/12/25/29：动效令牌与系统关闭动画 |
| v1/05 多媒体与产物 | S01/13/19/22/30/35：代码、分栏、图像和修订；音频不适用 |
| v1/06 桌面与协作 | S11/16/17/18/33：原生宿主、列表/焦点；不新增协作/桌面专有能力 |
| v1/07 工程配置与资产 | 固定提交、MIT 署名/许可证、源码来源清单；不替换现有 Android 构建链 |
| v1/08 Agent 开发指南 | 可追溯参数、覆盖矩阵和真实消费者；文档内容不作为项目指令 |
| v2/01 版本边界 | 固定 c1f923ae；上游作者声明不等同验证过官方生产客户端 |
| v2/02 新特性 | 草稿/恢复、触控、真实能力；不增加企业策略/语音/自动发送 |
| v2/03 架构对比 | 状态仍归现有 VM/仓库；UI 只发既有动作，不复制第二套业务状态机 |
| v2/04 底座参数 | 按角色/单位核验原生动画；不混合不同官方版本声明 |
| v2/05 参考实现 | 原样借鉴适用布局/反馈，替换模拟数据和模拟成功为本机真实事实 |
| v2/06 总结 | 按消费者与证据判断覆盖，不按新增文件数判断完成 |

## 3. 本轮补齐的现有页面漏洞

| 实际页面 | 问题与修正 | 验证状态 |
| --- | --- | --- |
| 大厅/讲题确认卡 | 同一真实动作可在终态落库前被重复触发 → 每卡 single-flight；真实执行期间两种决定都禁用 | 命令并发 JVM、设备忙态通过 |
| 存题确认卡 | 一律声称附图录入 → 按草稿/附图/已入库题的真实目标分别表述，缺失上下文不声称已存题 | JVM 通过 |
| 导出设置 | 一行塞五个选项/两个步进器 → 按容器换行，文本可增长 | 窄屏/大字设备通过 |
| 导出卷面准备 | 正在读 A 题时仍可勾成 B 题 → 准备期间冻结选题/入口，迟到结果再核对选题和版式 | VM 挂起回归通过 |
| 分类修正 | 正在保存旧选择时还能修改建议 → 保存期间禁用建议，整行单一 checkbox 语义 | 真实保存门挂起时设备断言通过 |
| 拆题复核 | 不可点击的整行带前进箭头 → 整行切换选择、保留真实回执图标 | 点击真实文本只派发一次、勾选与计数同步的设备检查通过 |
| 拍照拆题 | 46dp 固定高按钮 → 至少 48dp、字号增长时增高 | 320dp/2 倍字、真实选择状态的触区与回调设备检查通过 |
| 筛选/模板 chip | 颜色表示选中但读屏无状态 → 选择调用方有 selected 语义，普通动作保持按钮 | 设备语义检查通过；不冒充 TalkBack 人工验收 |
| 原图与题图 | 复习多页、拍题讲解旧弹窗、列表题图、拆题整页未统一放大 → 接同一只读查看器 | 多页第二页缩放/返回、列表进详情/收藏独立、重绘身份、拆题选择不变均有设备证据 |
| 青绿按钮/分段 | 焦点边框画在同色内部不可辨；协议长短标签触区高度不同 → 按真实背景调整焦点色、分段格撑满轨道 | 先复现 1.0/1.26 焦点对比度及不等高红例，再以真实组件像素/触区检查通过 |
| 复习/题目详情 | 遗留 26/28sp 页面标题、拆题预览暖色面板与全局令牌冲突 → 页题 20sp、状态分节 16sp，使用共享中性表面 | 相关模块 JVM/构建通过；数值统计的大字号保留其数据强调角色 |

## 4. Markdown 槽的维护边界

`core:markdown` 替换已发布的 `richtext-commonmark:0.17.0`，使用该版本 Android 源码。保留原解析器、AST、内联、表格、列表、引用路径，只新增可选代码块显示槽。13 个原始文件逐项核验，9 个原样保留，4 个为 Android expect/actual 合并或槽适配；没有 internal 可见性压制、反射或第二套解析器。

Apache-2.0 原许可、修改说明与逐文件摘要在模块和应用资产中保留；已核验依赖树排除旧 Maven commonmark 模块，localFirst 和 strictOffline 两种 APK 均包含完整 MIT/Apache 许可及修改说明。原有拒绝 HTML/远程图/链接的安全门继续先于渲染器，代码块工具栏不能绕过它。

## 5. 证据与尚未关闭的门

本轮证据目录：`.jez/artifacts/open-claude-ui-coverage-2026-10-24/`。

- `verified-current-build-2026-10-25.log`：共享 JVM/lint、Tutor/Library/Capture/Review/Profile JVM、localFirst/strictOffline 主包及相关测试包构建成功，2m49s。601 项 JVM 零失败/错误/跳过（52/258/65/148/72/6）；core:ui lint 0 错误、1 个原有 ModifierParameter 警告。
- `verified-device-summary.json`、`verified-{tutor,library,capture,app}.log`：任务独立 AVD 上 59/59 通过（37/16/1/5）。按源码测试方法集合核对实际结果，无缺失/跳过；安装后设备 base.apk 与本地 APK 的 SHA256 一致。测试不等于所有业务流程均已验收。
- `coverage-inventory.json`：36 个源码样例、14 章、174 个现有原生源文件的来源和调用候选；七类共享控件没有遗漏的原始 Material 导入/别名/全限定调用。此索引是源码追踪，不是运行时可达证明。
- `image-consumer-audit.json`：14 处有界图片调用逐项归类，区分只读预览、查看器内部、框选画布和拆题选择缩略图，后两者保留原手势。
- `source-manifest.json`、`markdown-source-verification.json`、`apk-manifest.json`：原始快照、当前摘要、13 项上游源码受控差分、两种主 APK 打包许可证核验。共享文件其他会话的变化不能整体归入或暂存。
- `app-screenshots-current-2026-10-25/`、`native-screenshot-receipt.json`：已查看当前APK四根页截图。由实际层级定位底栏、系统触屏点击、等待唯一目标根节点后采集，核验导航无键盘焦点及APK/截图摘要；未用固定延时或测试语义点击冒充默认触屏状态。模型设置页本来使用FLAG_SECURE，黑图不作为视觉证据，也未为截图关闭保护。该页仅记录控件、协议和保存入口可达的设备证据。
- 首轮骨架定位、图片 Escape、焦点低对比、协议格不等高等红例日志保留；以最新方法集合与绿例结果为当前结论，不抹去失败过程。
- AVD 为 `audit_claude_coverage_20261024` / `emulator-5562`；未使用其他工作的 5554。一次独立复核代理调用因额度限制没有执行，不计作审查通过；以上源码复核由当前实施者本地完成。

仍未关闭的是原计划独立保留的完整业务路径/发布门、性能和大图内存实测、真实中文输入法、多真机/系统组合、TalkBack 人工与受保护设置页的人工视觉核对。完整共享元素逐帧过渡采用已批准的原生淡入/缩放降级，不宣称与 Web 宿主逐帧相同。没有用本表、编译或自动化语义检查替代这些门。未提交推送。
