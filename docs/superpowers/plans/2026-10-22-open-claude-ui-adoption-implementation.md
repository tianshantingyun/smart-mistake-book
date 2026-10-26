# Open Claude UI 全量原生适配 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将已批准的青绿主题设计、阅读布局、输入/滚动/图片查看与恢复细节落实到智能错题本现有页面，保持全部业务与生图合同。

**Architecture:** 复用现有 Compose、core:ui、统一 TutorSurface 和各 feature 的状态/回调。先交付共享呈现与会话，再接图片、页面布局及完整应用路径；每批独立验证，不复制第二套 UI/任务/渲染系统。

**Tech Stack:** Kotlin 2.3.21、现有 Compose BOM 2026.06.00 / Material3、Android minSdk 23、compileSdk 37、JUnit4、Compose instrumentation。保留现有依赖版本，不引入 React/Electron/Tailwind。

**Spec:** [2026-10-22-open-claude-ui-adoption-design.md](../specs/2026-10-22-open-claude-ui-adoption-design.md)

状态：设计已由用户 2026-10-22“你开始全量改造”批准；**执行计划已由用户回复“确认计划，开始执行”批准，实施与集成验证中**。建议执行方法：由当前会话原生串行实施，各批检查点留证；共享工作区存在大量在飞工作，不默认并行写同一批源码。用户已选择本会话原生串行实施；检查点与裁定记录在本计划专属 ledger。

2026-10-25 最新指令：用户追问完成范围后要求“继续全量完成吧”，恢复下文尚未关闭的完整路径、逐页、性能和可访问性核验；先前“暂缓全面验收”只作为历史状态保留，不再作为本轮停止依据。当前事实与剩余项见 [全量收尾核验](../../research/2026-10-25-ui-completion-audit.md)。不得将上一轮适用项移植的完成结论扩展为整份计划已经完成。

## Global Constraints

- 2026-10-23 用户再次明确：排版布局、交互形式和使用手感直接照搬参考项目。以源码移植为标准，不能仅做通用 Material 美化。Jade `#23704A` 保留，浅色画布改用上游 `#FCFCFB`、文字 `#151515` / `#525252`、白色浮层；此裁定覆盖上一版纸色与页题尺寸。
- 普通文字至少 4.5:1，实际触区至少 48dp；上游页题20sp、紧凑顶栏14sp、输入15/21sp、控制文字12/18sp按角色采用；既有错题卡18/15/14sp保留，系统大字号继续有效。
- 手机内容边距目标 16dp，阅读列最大约 600dp；320/360/412dp、600/840dp 与 fontScale 1.0/1.3/2.0 按批覆盖，不能通过缩字解决溢出。
- 保留四底栏、来源返回、题图外显、多页来源顺序、收藏独立点击、当前筛选全量导出、草稿/版本/回执/曝光事实、学习启用门。
- JS 和 IMAGE_MODEL 沿统一生图判定；查看、排版、动画和本地交互不触发远程生成。没有真实模型能力/用量/任务结果就不显示假选项、仪表或成功状态。
- 不安装上游依赖，不移植其模拟工具执行、浏览器宿主桥、桌面专用窗口、多代理和语音功能。
- 同文件单写者；每批先看最新 diff，保存基线，发现文件在读后变化先重新核对。不得全仓 add、reset、stash 或覆盖他人改动。提交/推送另依用户指令，本计划不自动执行。

## 2026-10-24 全量覆盖复核（持续执行）

完整对照表见 [36 源码、14 章与实际消费者覆盖记录](../../research/2026-10-24-open-claude-ui-coverage.md)。文件存在、导入迁移、编译通过与真实交互通过分开记录；不把这一批补齐当成整个目标完成。

- [x] S13：固定 0.17.0 原 CommonMark Android 源码，只增加可选代码块显示槽，接语言栏、逐块原文复制、2.5 秒勾选淡退；嵌套列表/引用仍用原解析树。四项设备回归通过，原许可/摘要/旧依赖排除/打包许可已核对。
- [x] S02/S27：本机附件真实文件名、来源与序号、查看器标题；真实溢出时提示数量与横向滑动，不遮挡移除操作。
- [x] S21：确认卡按真实目标描述草稿保存、附图录入或打开既有题；同一卡执行期间防重复决定、显示忙态、禁用两种决定。命令并发 JVM 通过。
- [x] S15：导出设置按容器换行，步进文本可增长；准备卷面期间冻结选题，迟到结果同时核对选题和版式。输入区阻断说明在窄容器分行，恢复动作不挤压说明。
- [x] 现有页面漏洞：分类保存期间锁定建议；拆题整行切换选择并去除误导前进箭头；拍照拆题按钮至少 48dp；选择 chip 补 selected 语义而普通动作不伪装成选择控件。
- [x] S28/S33：分段控件单一 Tab 入口、方向键与正反向退出；设备在真实 Keyboard 输入模式下通过，保留原生 TalkBack 选择语义。
- [x] S14/S22/S33：设备先复现图片弹窗 Escape 不关闭，再接共享键盘关闭；图片、确认、导出、框选、复习原图弹窗遵守原不可取消策略。图片焦点返回真实触发点、确认可取消/不可取消设备回归通过。
- [x] S22：复习多页原图列表接同一只读缩放，保留第二页身份、顺序和返回位置。确认拍题讲解旧 SourceImageFullscreenDialog 仍有真实调用后替换；补列表题图独立放大动作与拆题整页查看。题卡/图片进详情、收藏独立和选择缩略图原合同保留；对应设备回归通过。
- [x] 分类保存、拆题整行、拍照拆题实际状态的设备证据已补齐：真实保存挂起、文本点击一次更新选择与计数、320dp/2倍字48dp触区；并非用共享组件测试替代。
- [x] 最终核对源码摘要、实际主应用四根页截图、14章覆盖和原生模态消费者；受保护设置页只记录可达证据。性能、中文输入法、TalkBack人工与业务发布门仍独立保留。

维护裁定：新增 `core:markdown` 是原依赖的固定源码适配，不新增另一套解析器；升级 richtext 时必须同步复核槽、上游差分、Apache 许可和 fail-closed 设备用例。图像生成统一 JS/IMAGE_MODEL 判定及服务合同不变。

## 2026-10-25 当前消费者补齐

- [x] 修复绿色实心按钮内侧焦点与底色重合、选中分段键盘焦点不可辨；先留真实像素红例，再按实际底色选择可辨焦点边框，JVM/设备检查通过。
- [x] 用设置页实际四种协议标签核验混合换行：分段格撑满轨道，整格底部点击可选；单一 Tab、正反退出、RTL、Home/End 和重复选择不重复派发的设备检查通过。
- [x] 复习/详情遗留26/28sp页题按角色收敛到20sp、状态分节16sp；拆题预览改用共享中性表面，统计数值保留其数据角色。
- [x] 最新共享/五个feature JVM合计601项零失败/错误/跳过；core:ui lint零错误、1个原有警告；localFirst/strictOffline及相关测试包组装成功。当前设备37/16/1/5合计59项通过，已核对实际安装APK摘要及源码方法集合。
- [x] 已查看当前四根页的默认触屏截图，核对实际安装APK、唯一目标根节点、导航无键盘焦点及截图摘要；原生触屏输入用于清除前组测试的系统键盘模式，未改产品焦点行为。模型设置FLAG_SECURE不为截图关闭，其人工视觉核对仍开放。

本节记录适用UI实现和定向证据，不关闭Task4大图内存、Task5/6逐页业务对照、Task7全流程、性能/真机/输入法/TalkBack发布门；这是该批历史状态；用户已于2026-10-25恢复全面验收，当前进展以全量收尾核验记录为准。完整共享元素采用原生入场降级的裁定仍有效，不宣称逐帧一致。没有自动提交推送。

本轮适用呈现与交互的源码对照已闭合：36个样例和14章逐项裁定、174个原生源文件的消费者索引、14处图片调用分类、当前安装包定向检查与四根页截图均已复核。本结论只关闭本轮UI移植/覆盖检查，原七批未勾选的完整业务/人工/性能验收保持开放，不能据此宣称整个软件已完成发布验收。

## Review Focus

1. 末条回复高度超过视口，用户滚回其上半段：流式追加不能抢回到底；Task 2 设备回归覆盖。
2. 附图加四行输入、键盘与 2.0 大字同时出现：回到最新/停止/发送可达且互不覆盖；Task 2/3 覆盖。
3. 正在组合中文或输入代理对时达到长度上限：不截坏组合文本、不误发送；Task 3 覆盖。
4. 同 URI 的区域/版本切换或旧解码延迟返回：查看器不能显示上一图/上一区域；Task 4 覆盖。
5. 全局视觉迁移触及已有在飞修复：业务副作用、revision、恢复、评分/导出范围和返回位置不变；Task 5/6/7 复用原有定向回归。

## 文件位置约定

为避免重复长路径，下表为后续路径的精确前缀。不存在的文件在任务中明确标为新增。

| 前缀 | 路径 |
| --- | --- |
| U | `core/ui/src/main/java/com/tingyun/smartmistakebook/core/ui/` |
| UT | `core/ui/src/test/java/com/tingyun/smartmistakebook/core/ui/` |
| T | `feature/tutor/src/main/java/com/tingyun/smartmistakebook/feature/tutor/` |
| TT | `feature/tutor/src/test/java/com/tingyun/smartmistakebook/feature/tutor/` |
| TI | `feature/tutor/src/androidTest/java/com/tingyun/smartmistakebook/feature/tutor/` |
| L | `feature/library/src/main/kotlin/com/tingyun/smartmistakebook/feature/library/` |
| LI | `feature/library/src/androidTest/kotlin/com/tingyun/smartmistakebook/feature/library/` |
| C | `feature/capture/src/main/java/com/tingyun/smartmistakebook/feature/capture/` |
| R | `feature/review/src/main/kotlin/com/tingyun/smartmistakebook/feature/review/` |
| P | `feature/profile/src/main/java/com/tingyun/smartmistakebook/feature/profile/` |
| A | `app/src/main/kotlin/com/tingyun/smartmistakebook/` |
| AI | `app/src/androidTest/kotlin/com/tingyun/smartmistakebook/` |

所有 Gradle 命令在仓库根运行，使用仓库现有 `GRADLE_USER_HOME` 配置。设备验证使用本任务独立模拟器/测试包，不清用户应用数据，不占用其他工作正在使用的 emulator-5554。截图、日志、指纹记录在 `.jez/artifacts/open-claude-ui-implementation-2026-10-22/`。

### Task 1：共享表面、排版、动作与清晰文字

**Files:** 修改 `U/Theme.kt`、`U/Components.kt`、`U/PageStateFrame.kt`、`U/ThinkingCollapsibleCard.kt`、`U/TutorToolTraceLine.kt`、`U/TutorMarkdownTokens.kt`。新增 `UT/SmartSemanticContrastTest.kt`、`TI/SharedUiLayoutInstrumentedTest.kt`。

**Interfaces:** 保留 `SmartMistakeBookTheme(content)`、`SectionHeader(title, modifier, action)`、`PrimaryActionButton`、`OutlineActionChip` 和现有页面容器签名；在 SmartColors 增加被实际消费的 `ContentSurface`、`SubtleSurface`、`ErrorText`；新增 `InlineActionButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, contentDescription: String = text)`，仅用于无选中状态的行内动作。旧 OutlineActionChip 的选择/描边合同保持。

- [x] 记录本批文件内容摘要和共享组件基线截图；检查新增测试不会写业务库。
- [x] 添加 `normalSemanticTextHasAtLeastFourPointFiveContrast()`，按实际语义文字/底色计算对比度，不比较硬编码预期色值；添加 `largeTextKeepsSectionActionsReachable()`，用 320dp/fontScale2.0 验证标题和动作均可见、触区不重叠。
- [x] 先运行新增失败检查，确认发现正常说明/错误文字或布局缺口；纯外观调整使用同内容截图比较，避免为每个常量写镜像测试。
- [x] 实施语义色、紧凑分节、行内动作、可增长控件与说明文字；将思考详情移出 InkMuted，小字展开头保留48dp真实触区。先迁移明确消费者，不直接全仓替换所有26dp。
- [x] 运行 `./gradlew.bat :core:ui:testDebugUnitTest :core:ui:lintDebug :feature:tutor:assembleDebugAndroidTest --console=plain`；要求相关测试通过、无新增 lint Error/Fatal；设备运行 SharedUiLayoutInstrumentedTest，检查前后图。
- [x] 本地审查改动职责、无闲置令牌与无新业务副作用，登记本批证据；不自动提交。

### Task 2：真实贴底、长回复和消息动作布局

**Files:** 修改 `T/TutorConversationSurface.kt`、`T/TutorSurfaceMessages.kt`；新增 `T/TutorTailFollowPolicy.kt`、`TT/TutorTailFollowPolicyTest.kt`、`TI/TutorLongReplyScrollInstrumentedTest.kt`；复用 `TI/TutorLobbyScrollInstrumentedTest.kt`。

**Interfaces:** `internal fun isTutorTailVisible(lastIndex: Int?, totalItems: Int, lastBottom: Int?, viewportEnd: Int): Boolean` 统一尾部几何判据；现有 `forceFollowToken` / `blockAutoFollowToken` 的参数与调用者语义不变。输入区域实测高度只作为布局状态，不进入数据库。

- [x] 添加几何用例：空列表在尾部；末项不是最后一项为false；最后一项可见但底边大于视口为false；底边等于视口为true。
- [x] 添加 `streamingDoesNotStealReadingWithinOneTallReply()`：仅末条长回复超出视口，真实手势向上后追加正文，断言阅读位置不被拉回；点击回到最新才到尾部。
- [x] 先运行定向检查并确认失败来自现有末条索引判据，再以同一几何判据替换跟随状态与滚动助手。区分程序滚动和用户滚动，不引入强制150ms重新锁定。
- [x] 回到最新按钮改用实际输入/附图高度加安全间隔定位；复制/停止/重试改用可换行行内动作，保留原回调和testTag；用户短消息自然收窄，助手正文保持现有全阅读宽度。
- [x] 运行 `./gradlew.bat :feature:tutor:testDebugUnitTest :feature:tutor:assembleDebugAndroidTest :feature:tutor:lintDebug --console=plain`；设备运行新增长回复、已有大厅滚动与会话屏幕定向用例。保留滚回历史不抢读和新回复可到达两种行为。
- [x] 登记长回复、键盘、附图四行输入和大字截图，审查没有改变会话身份/模型订阅/答案曝光。

### Task 3：原生输入、常驻输入区与真实状态反馈

**Files:** 修改 `T/TutorConversationSurface.kt`、`T/TutorSurfaceComposer.kt`；新增 `T/TutorComposerTextPolicy.kt`、`TT/TutorComposerTextPolicyTest.kt`、`TI/TutorComposerInteractionInstrumentedTest.kt`；复用 `TT/TutorSurfacePolicyTest.kt`。

**Interfaces:** 保留 `TutorChatComposer(value: String, onValueChange: (String) -> Unit, onSend: () -> Unit, ...)` 对外字符串草稿接口与现有 MAX_STUDENT_MESSAGE_CHARS。组合文本仅在组件内部持有 TextFieldValue；新增 `internal fun boundedTutorInput(value: TextFieldValue, maxChars: Int): TextFieldValue`，保证合法选择/组合范围且不切断代理对。

- [x] 先添加 `aComposingCandidateIsNotSentAsACommand()`、`anEmojiAtTheLengthBoundaryIsNotSplit()`、`imageOnlyMessageStillUsesTheExistingSendCallback()`，断言一次真实提交才调用发送，组合确认不额外调用；上限与草稿正文按已有合同。
- [x] 用缺配置/正在生成/已结束/计划失败和纯图场景复用已有 block 策略，验证输入区始终在、阻断原因与发送状态同源。
- [x] 实施内部组合文本处理、focus/disabled状态色、输入高度与附图工具布局；从已有回调保存草稿，不新增发送队列或自动恢复发送。
- [x] 运行 `./gradlew.bat :feature:tutor:testDebugUnitTest :feature:tutor:assembleDebugAndroidTest :feature:tutor:lintDebug --console=plain` 及新增设备用例；中文IME需真实输入法另验，不能仅凭字符串注入测试宣称通过。
- [x] 审查参数兼容与草稿恢复，无新增延迟发送；记录无法覆盖的输入法/设备组合。

### Task 4：有界图片查看与来源连续性

**Files:** 修改 `U/BoundedLocalImage.kt`、`U/AttachedImageCard.kt`、`C/CaptureSourceImagePreview.kt`、`T/TutorMessageAttachments.kt`；新增 `U/LocalImageViewer.kt`、`U/ImageViewportTransform.kt`、`UT/ImageViewportTransformTest.kt`、`TI/LocalImageViewerInstrumentedTest.kt`；复用 `TI/LocalImageUiInstrumentedTest.kt`。

**Interfaces:** `LocalImageViewer(imageUri: String, contentDescription: String, onDismiss: () -> Unit, sourceRegion: NormalizedSourceRegion? = null)` 为只读原生模态查看器。`ImageViewportTransform` 保留纯数值缩放/平移边界；其状态按 URI/区域/来源版本重置。BoundedLocalImage 保留现有参数默认值、采样和加载回调，按需要补可选尺寸回调而不暴露 Bitmap 所有权。

- [x] 为缩放锚点不动、内容小于窗口时居中、缩小后平移钳制、无效尺寸不产生NaN写几何行为测试；先验证缺失行为。
- [x] 添加设备用例：拖动/双击/复位、关闭返回、区域切换、延迟旧解码、缺图可读提示；复用 ControlledImageProvider 真实阻塞源与像素断言。
- [x] 实施以触点为锚的有界变换、原生关闭/复位操作与系统返回；保留既有折叠/展开/本地重读行为，新增大图入口不绑定远程生成。
- [x] 接入原图、附图与已有生成图查看；错题列表仍整卡进详情，收藏独立，不能劫持原点击合同。
- [x] 保留1600px解码上限并如实说明预览分辨率；不宣称高清区域解码。运行 `./gradlew.bat :core:ui:testDebugUnitTest :feature:capture:testDebugUnitTest :feature:tutor:assembleDebugAndroidTest --console=plain` 与图片设备用例，记录大图/多页内存和截图。
- [x] 审查 URI/区域/版本身份、资源释放、TalkBack替代操作与多页顺序；不引入网络取图或新图片生成。

2026-10-25 补证：三张96MP PNG连续打开/关闭12次，实际预览1000×1500；记录PSS基线106723KB、逐页观测最大182900KB、关闭并GC后120560KB。另以12页真实文件读取红例修复单卡离屏全部解码问题，10项图片设备回归通过。数据是模拟器观测，不是实体机性能达标结论。

### Task 5：错题本、详情编辑、录入与导出页面迁移

**Files:** 修改 `L/LibraryRoute.kt`、`L/LibraryToolbar.kt`、`L/LibraryQuestionCard.kt`、`L/MistakeDetailRoute.kt`、`L/MistakeRevisionEditRoute.kt`、`L/MistakePaperEditRoute.kt`、`L/BatchImportRoute.kt`、`L/SplitImportReviewRoute.kt`、`L/MistakeExportHubRoute.kt`、`C/CaptureScreen.kt`、`C/components/CaptureScreenComponents.kt`。已有相关文件含在飞逻辑，逐个保存最新基线后仅改呈现hunk。测试复用 `LI/LibraryLayoutInstrumentedTest.kt`、`LI/LibraryInlineImagesInstrumentedTest.kt`、`LI/NativePaperEditRouteInstrumentedTest.kt`、`LI/LibraryBatchExportEntryInstrumentedTest.kt`；新增 `LI/LibraryAdaptiveActionsInstrumentedTest.kt`。

**Interfaces:** 所有 route 的业务参数/回调保持；用 Task1 的语义与行内动作，Task4查看器只接已有本地URI。适配只派生布局，不增加持久字段。

- [ ] 各页截图确认现状，核对编辑/批量录入/导出在飞hunk，记录可修改部分。已有失败恢复修复先合并阅读，不能用旧版文件覆盖。
- [x] 在320dp/fontScale2.0添加动作换行与完整可达用例，保持题卡图片、标题、收藏、成果入口；多页/无图/损坏图沿原测试。
- [x] 逐页迁移页头、说明/错误文字、卡片层次与自适应动作，消除固定高度截断和重复强边框；不再重做已合格题卡。
- [x] 原图参考/编辑在840dp且高度足够时并排，栏位各至少320dp；窄屏维持原单页任务；利用现有来源与编辑状态，不新建编辑模型/路由。
- [x] 运行 `./gradlew.bat :feature:library:testDebugUnitTest :feature:capture:testDebugUnitTest :feature:library:assembleDebugAndroidTest :feature:library:lintDebug :feature:capture:lintDebug --console=plain`，设备运行相关布局、图像、编辑与导出入口定向用例。
- [x] 核对来源返回、筛选范围、修订CAS、待处理与已保存分离，拍摄同内容对照并登记未完成业务门。

### Task 6：复习、我的、设置与导航壳迁移

**Files:** 修改 `R/ReviewRoute.kt`、`R/ReviewSessionScreen.kt`、`R/KnowledgeReviewSessionScreen.kt`、`R/CapturedReviewSessionScreen.kt`、`P/ProfileRoute.kt`、`A/SecondaryScreens.kt`、`A/ReviewReminderScreen.kt`、`A/SchedulingSettingsScreen.kt`、`A/SmartMistakeBookRoot.kt` 的呈现片段；`A/LearningMasteryScreen.kt` 有在飞工作，只做经核对的排版hunk。新增 `AI/NativeUiAdoptionInstrumentedTest.kt`，复用 `AI/ReviewContinuityInstrumentedTest.kt`、`AI/ReviewSelfReportDeviceUiInstrumentedTest.kt`、`AI/UserVisibleRecoveryInstrumentedTest.kt`。

**Interfaces:** 根路由/导航目的地、模型配置写回、评分与恢复仓库接口不变；所有页面迁移到Task1语义。只有消费者已逐页迁移后才将共享默认边距收敛到16dp，避免未迁移页被暗改。

- [x] 固定基线数据和截图，区分真实队列、无证据、未加载与完成态；核对提醒/学习掌握在飞变更。
- [x] 添加空库→录入、两个复习入口、2.0大字三键与设置表单可达、配置返回保留草稿的定向回归。未加载不能显示假0，打开页面不产生评分或发送。
- [x] 按2026-10-23上游量度修订为页题20sp、分节16sp；收紧问候与重复留白，按钮使用最小高度；三键保持同权重；设置/我的按现有功能分组，真实值与错误就地展示。
- [x] 导航壳只消费一次inset；键盘、系统返回、二级页来源栈沿原路由处理；宽屏仅增加布局区域，不改变四底栏的目的地语义。
- [x] 运行 `./gradlew.bat :feature:review:testDebugUnitTest :feature:profile:testDebugUnitTest :app:assembleLocalFirstDebug :app:assembleLocalFirstDebugAndroidTest :app:lintLocalFirstDebug --console=plain`，运行受影响的主应用导航/复习/恢复定向用例。业务库/证据迁移失败必须定位来源，不能修改UI测试规避。
- [x] 与Task1共同确认所有正常说明色、角色字号、触区和边距消费点，登记前后图与未验证的权限/窗口组合。

### Task 7：集成验收、兼容检查与计划同步

**Files:** 已批准设计、研究清单、`docs/superpowers/plans/2026-10-19-ui-ux-redesign-plan.md`、`docs/superpowers/specs/2026-10-19-ui-ux-redesign-design.md`；新增 `docs/research/2026-10-22-open-claude-ui-implementation-record.md`。应用源文件只在发现本轮回归且定位后修复，不做顺带重构。

**Interfaces:** 每批证据记录源文件摘要、设备/字体/窗口、命令、退出码、截图、已验与未验范围；适用项由S01–S36映射到Task1–6，不适用项保留明确原因。

- [ ] 按Review Focus逐项复核最新集成状态，核对输入/截图不是来自旧APK；测试数据不写入用户库。
- [x] 合并受影响模块的JVM/lint/组装结果，设备覆盖320/360/412与600/840窗口、字体1.0/1.3/2.0的代表性页面，另验动画关闭/横屏/键盘/返回。每批已覆盖且无新变化的检查不重复跑。
- [ ] 实际走四根页→题目→讲题→返回、录入→复核→已保存、复习→帮助→返回、导出→成果、设置→原草稿等现有路径；只记录真实完成回执。
- [x] 本地完整diff审查：无模拟成功、无新后端队列、无视觉副作用、无上游指令覆盖、无不必要依赖；无关失败和未达性能指标明确保留，不宣称全量通过。
- [x] 更新旧设计中“默认无自动滚动”为已批准的阅读优先规则，按真实证据勾选既有UI任务；未完成的PRD/WG/4E门不因UI改造关闭。
- [ ] 输出改动、验证与未验范围。提交/推送若本轮未另获指令则保持工作区，供用户审阅。

## 2026-10-25 收尾状态

- [x] 窄屏大字三键、选项圆标、科目中文描述与单卡多页可见解码已修复；238项受影响JVM、52项相关设备回归分批逐项通过，另有长图、宽窗和实际TalkBack各1项证据。安装包摘要逐批保存，原失败日志未删除。
- [x] 96MP长图12轮实测与150题真实时钟滚动采样已记录；后者用例通过不等于帧率合格。
- [ ] 性能门：debug模拟器243帧中88帧jank，p95=61ms；需release/profileable实体机Perfetto归因和同场景复验，不能标成流畅度通过。
- [ ] UI-11人工与剩余矩阵：目标学生走查、人耳朗读、其他中文输入法、受保护设置视觉，以及缺少逐状态截图的编辑/导出路径仍开放。具体证据和下一步见[全量收尾核验](../../research/2026-10-25-ui-completion-audit.md)。

## 计划自检

- 设计§2/3由Task1、§5.1由Task2、§5.2由Task3、§5.3由Task4、§4页面由Task5/6、§6/8集成由Task7承接。
- 五项Review Focus均有归属测试；没有把颜色常量、文件数或编译通过当成全应用体验验收。
- 跨任务新接口只集中于Task1行内动作、Task2尾部几何、Task3组合文本、Task4图片查看；业务接口不重定义。
- 本计划已获确认并进入实施；完成状态以实现记录、命令日志和设备回归为准。

## 2026-10-23 直接移植修订批

用户指出第一张320dp/2倍字号测试截图未达到Claude布局要求，明确要求排版、交互和手感直接照搬。新增本批以纠正实现偏差，不扩展业务功能。

- [x] 逐项对照 `ChatComposer.tsx`、`ChatMessageBubbles.tsx`、`ClaudeDesktopHeader.tsx`、选择器与主题源码；保存第二次改造前的字节基线。
- [x] 添加 `U/ClaudeLayout.kt`：20dp输入外壳、14dp内边距、15/21sp输入、14sp顶栏、18dp用户气泡、16/12dp消息内边距、85%气泡宽度与4dp助手正文缩进，均有实际消费者。
- [x] 输入区改为上方整行输入、下方工具栏和独立32dp发送视觉；保留48dp真实触区。新会话用欢迎标题→输入框→建议入口的顺序，已有消息后输入区固定在底部，同一输入实例跨布局移动。
- [x] 附件改为横向紧凑卡；回答方式移入工具栏，弹出已有两种选择；保留原会话写入回调。发送按压采用上游0.95缩放与150ms过渡，系统动画关闭可直接完成。
- [x] 助手身份移到正文上方，去掉侧边头像挤占和正文边框；用户气泡采用上游圆角与量度。能力目录改为实际建议入口，不再铺成长说明目录。
- [x] MIT版权与完整许可证随应用assets保留；未引入上游模拟任务、硬编码模型和React运行时。
- [x] 用最新APK验证输入法提交、真实键盘/大字/附件、长回复、选择器和大图操作；查看默认字号真实应用截图，极端字号截图另标用途。
- [x] 同步旧UI设计、执行状态和真实未达验收项；不将源码移植、组件截图或构建成功等同于全应用业务验收完成。

## 2026-10-23 全前端扩展移植批

用户明确“先不用全面验收”，继续把上游能够移植的前端内容落到现有软件。以下属于已经批准的改造范围；完整业务/性能验收另留，不能用它反复阻断实现。

- [x] 按 CdsButton.tsx 移植主/次/幽灵/图标按钮的圆角、填充、禁用、焦点与按压，沿现有 API 回调迁移全应用消费者，保留 Android 48dp 触区。
- [x] 按主题层级与 ConfirmationDialog.tsx 统一表单、弹窗、操作区与浮层，避免嵌套边框；滚动/键盘/关闭沿原合同。
- [x] 按 SegmentedControl.tsx 移植现有筛选分段；窄屏/大字允许滚动，不隐藏无图标选项名称。
- [x] 按 DraggableSessionList.tsx 的紧凑行、操作区和分组层次整理真实历史/设置列表；不新增固定/拖拽排序持久模型。
- [x] 按 ThinkingAccordion.tsx 与 StatusIndicators.tsx 移植现有思考折叠、真实运行状态和复制动作；不编造时间/进度。
- [x] 将 ArtifactsSplitPaneResizer.tsx 的分割线、宽度约束与可访问调整移到已有宽屏参考编辑布局，保持同一编辑状态。
- [x] 运行共享组件和受影响模块编译/定向检查，登记实际覆盖与尚未移植内容；不执行提交推送，不标记全应用验收完成。该批历史结果见 `docs/research/2026-10-23-open-claude-ui-expansion.md`：共享组件50例JVM、11次设备定向用例通过；当时撤回internal API尝试。2026-10-25已用固定原源码的可选显示槽补齐逐块工具栏，最新状态见上方覆盖复核，原解析与安全门保留。

## 2026-10-24 全局交互与动效补齐

用户再次要求按同一开源标准继续全面改造并覆盖未点名的体验细节；沿已批准的青绿与原生实施方向推进，不重新发起品牌或风格审批。Superdesign 用于当前界面复现和规范记录，真实软件的业务接线继续保留。

- [x] 建立统一曲线/时序、实时系统减少动画分支；覆盖按钮、发送、分段、字段与弹窗。
- [x] 四栏和二级页导航、返回转场沿原 NavHost 接入，统一折叠、真实加载/运行反馈。
- [x] 补齐历史/设置/选题/回答方式/快捷建议/附件菜单/思考标题的悬停、按压、焦点与触区，保留纯只读行的紧凑呈现。
- [x] 原图/生成图的统一查看器接入弹窗入场；错题卡仍外显题图；回复复制接共享实际反馈，不动画化正文/科学数值。
- [x] Superdesign 同一项目/单一方向创建和校正为真实首页，保存可恢复上下文与来源记录。
- [x] 必要编译、共享 JVM/lint 和主包/测试包构造；详情 `docs/research/2026-10-24-open-claude-ui-motion.md`。
- [x] 独立模拟器恢复可用后，已在2026-10-25最新包运行三项动效及图片/导航定向用例，包含实时系统动画偏好；当前证据见全量覆盖复核，不用前次结果替代。
- [ ] 全面业务、性能、真实输入法与无障碍门按2026-10-25最新指令继续执行；实体机、人工朗读和受保护页面视觉核对单独保留，不以模拟器替代。本轮不提交推送。


## 2026-10-26 界面文案清晰化（已实施，后续批次补齐整包验证）

- [x] 主要页面使用明确的动作、状态和下一步；统一错题本、模型设置、复习安排、学习进度与讲题记录等称呼。
- [x] 清理答案键、曝光、响应、入库、替换语义和本批未接等内部表达；保留网络发送、费用、覆盖/清空、看过答案及自评等重要后果。
- [x] 33个生产文件的250处字符串调整，非文案代码与测试锚点保持；当前6个模块601项JVM结果通过，最后命令缓存命中。未改变流程、写入和生图判定。
- [x] 用户授权后，仅补齐Elo区间输出缺失的舍入函数；未改预测公式、参数或测试金样。
- [x] 最终整包重建与最后4处Profile文案设备复验已由本日后续批次补齐：两种主包构建、实际MainActivity导航及最终截图见[状态打磨记录](../../research/2026-10-26-ui-state-polish.md)。之前246处文案快照的702项JVM和115个设备方法仍只作为历史证据。
- [ ] 上一批domain的2项点预测金样失败仍待该模块复验；本轮UI检查不关闭此项。
- 原性能、实体机、学生走查与其他未完成项继续保留。具体改写与证据见[文案记录](../../research/2026-10-26-ui-copy-refinement.md)。未提交推送。

## 2026-10-26 后续文案与状态打磨（本轮已验证）

- [x] 正式、简短的现有文案；省略重复标题的摘要，保留保存不确定性和数据后果。
- [x] 错题本未知数量不显示为0；分页加载/失败/重试和题图加载/不可用分别呈现，保留已读内容与离屏解码限制。
- [x] 掌握状态使用图标、文字和底色，兼容旧筛选值；大字时分类路径与标签分行。
- [x] 收藏与状态颜色、展开箭头沿现有时长过渡；减少动态效果时直接呈现最终值，动画不提交业务动作。
- [x] 最终702项相关JVM、两种主包及测试包构建、library lint零错误；6类24项设备检查同次全绿。详细范围及保留的初次失败见[记录](../../research/2026-10-26-ui-state-polish.md)。
- 原滚动性能、真机和人工验收门保持开放。未提交推送。


## 2026-10-26 错题页布局与视觉模块（实施后定向核验）

用户要求页面更紧凑、成熟，并明确加强错题页视觉模块性。沿已批准的青绿与Claude原生移植方向修正现有呈现，不增加业务流程。

- [x] 列表统一“信息头→题图”：题名、分类/来源、掌握状态和收藏/放大位于浅底信息区，细线分隔完整等比题图。正常图片不重复摘要，手动题/加载/失败保留文字；减少视觉图标尺寸，保留48dp触区。
- [x] 详情题面、原图、讲解图、错因备注使用同一模块外壳与标题规则，操作归属清楚，大字/窄屏自适应。用户后续明确不要缩略，已将原图/讲解图改为直接全宽、按原宽高比显示，移除模块展开和单图缩略步骤；角色、历史只读与备注保存保持。新验证单独见[详情直接显示](../../research/2026-10-26-detail-images-direct-display.md)，下列49项属于本批首次模块版历史结果。
- [x] 0结果不提供导出，无筛选空库不提示清除筛选；讲题输入阻断原因和已有设置动作同区呈现，保留原可用性策略。
- [x] 432项相关JVM、library/tutor lint零错误及两种主包/相关测试包构建；13个本轮源码/测试文件有字节基线与摘要，图片选择、备注处理和原动作策略静态核验通过。
- [x] 最新APK在独立emulator-5562运行12类49项设备回归全部通过，四个安装包摘要一致，8张原始截图及来源单独登记；已查看默认列表/详情、大字号和实际主应用空态/讲题页面。不用前批31项结果替代，也不以夹具图冒充用户数据。
- 原性能、真机、人工和完整发布门继续开放；无生图、业务规则或依赖变更，未提交推送。详见[布局与模块记录](../../research/2026-10-26-library-layout-refinement.md)。

2026-10-26 后续确认：详情图片直接显示批已通过73项library单测、lint零错误、两种主包/测试包构建及19项详情设备回归；全宽自然比例、大图返回、12页可见解码和原文件摘要不变均有新证据。原默认收起与缩略高度规则由用户最新要求替代，其他发布门未关闭，未提交推送。见[直接显示记录](../../research/2026-10-26-detail-images-direct-display.md)。


## 2026-10-27 青绿书页视觉与独立交付

用户要求继续优化并提交推送，随后强调视觉突破。本批实施者沿已批准青绿与Claude交互选择纸面阅读方向，不增加业务入口。

- [x] 温暖纸色、白色题卡、淡青绿信息头和章节边线；28sp页题/身份、18sp题名强化层级，保留正常信息对比度和原触区。
- [x] 同名题名去重、不同题面标题保留；单图重复图题去除，多页与重绘/历史身份保留。详情继续完整展示图片。
- [x] 原共享工作区出现其他任务暂存，UI交付改到独立分支`codex/ui-green-pages-2026-10-27`；其他工作与原暂存内容保留。
- [x] 本批源码快照独立构建通过，756项单测、19类73项设备回归、两模块lint零错误及两种主包/测试包完成；8张截图和安装摘要已核对。既有依赖锁推进、真机性能和完整发布门仍开放。

本批代码、范围与结果见[视觉与交付记录](../../research/2026-10-27-ui-visual-polish-and-delivery.md)。
