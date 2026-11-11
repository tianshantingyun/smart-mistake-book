# 组卷与聊天图文阅读实现计划

> 后续 UI 变更以用户于2026-11-11确认的 `387e80097f5ad4800ee313022a4c57ca665ee7b9` 为视觉基准。新增与修改页面、触发弹层遵循[设计第十五节](../specs/2026-11-08-paper-assembly-and-chat-reading-design.md#十五后续-ui-视觉基准2026-11-11用户确认)，继续按本计划记录验收范围。

> 面向 AI 代理的工作者，使用 subagent-driven-development 或 executing-plans 逐项实施。只在已批准的工作树与路径内工作。

目标为错题本独立组卷、Claude 式聊天图文排版、个性化讲解与通用答案分离，并使两种内容都具备有意义的必需图解。

架构沿用 LibraryCatalogRepository 查询、现有卷面编辑器、Markdown 与公式解析、统一 FigureGenerationCoordinator、消息资产及视觉快照。图片和 JS 是共享生图体系的两种方法。页面按资产和能力事实呈现状态。

技术栈为 Kotlin、Jetpack Compose、Paging、SavedStateHandle、现有 Markdown AST、Room 及隔离 VisualJs 引擎。

对应设计位于 docs/superpowers/specs/2026-11-08-paper-assembly-and-chat-reading-design.md，尤其第七至九节记录用户最新补充，并作为验收要求。

## 全局约束

- 新增路径仅限已批准的七项，其他新路径先弹窗。构建生成物按已有构建规则处理，人工证据进入已授权证据目录。
- 主工作树仍有其他线收口，本轮只修改当前 UI 工作树。主分支整合继续等待用户通知。
- 聊天回复采用学生当前上下文，通用答案仅基于冻结题面和通用学科规范，缓存相互独立。
- 每条模型讲解回复与每题通用详细答案必须穿插可解析、归属正确且有教学作用的图。简单图由 JS 完成。仅当复杂图确实必须使用生图模型且该模型真实不可用时，允许带明确原因的无图结果，其他缺图是未完成状态。
- 原题、答案揭示、权限、预算、资产校验和幂等规则继续生效。图片和图注遵循正文的答案揭示权限。
- 图的语义正确性与文件完整性分别核验，模型自检不能写成已独立验证。

## 文件职责与批准范围

新增

1. docs/superpowers/specs/2026-11-08-paper-assembly-and-chat-reading-design.md
2. docs/superpowers/plans/2026-11-08-paper-assembly-and-chat-reading-implementation.md
3. feature/library/src/main/kotlin/com/tingyun/smartmistakebook/feature/library/PaperAssemblyRoute.kt
4. feature/library/src/main/kotlin/com/tingyun/smartmistakebook/feature/library/PaperAssemblyViewModel.kt
5. feature/library/src/test/kotlin/com/tingyun/smartmistakebook/feature/library/PaperAssemblyViewModelTest.kt
6. core/ui/src/main/java/com/tingyun/smartmistakebook/core/ui/TutorMessageDocument.kt
7. core/ui/src/test/java/com/tingyun/smartmistakebook/core/ui/TutorMessageDocumentTest.kt

已有文件按任务修改，避免为每个小步骤新建工具类。设计与计划负责约束和进度，组卷 ViewModel 只管理有序选择与页面状态，共享消息组件负责受控文档块与插图呈现。

## 任务 1 — 独立组卷选题与已有编辑器接通

修改 LibraryRoute.kt、LibraryToolbar.kt、SmartMistakeBookRoot.kt，新增已批准的 PaperAssemblyRoute.kt、PaperAssemblyViewModel.kt 和对应测试。

- [x] 在测试中构造 SavedStateHandle，验证选择切换、跨查询保留、有序移动、移除、限额和重建。

```kotlin
val state = SavedStateHandle()
val model = PaperAssemblyViewModel(state)
model.toggle("entry-a")
model.toggle("entry-b")
model.move("entry-b", -1)
assertEquals(listOf("entry-b", "entry-a"), model.selectedEntryIds)
assertEquals(model.selectedEntryIds, PaperAssemblyViewModel(state).selectedEntryIds)
```

- [x] 从 LibraryViewModel 复用候选查询、筛选、分页与时间排序，组卷选择只保存 entryId 顺序，保持状态边界清楚。
- [x] 组卷 Route 展示“选题 / 已选”工作区、直接题图、独立选择操作、空态、分页失败重试和固定操作栏。
- [x] 在 SmartMistakeBookRoot 增加组卷导航，错题本“组卷”入口传入当前筛选基准，选中题目的有序列表进入既有 MistakePaperEditRoute。返回保留状态。
- [x] 复核已选题删除与版本变更，接续既有载入和冻结校验，失败题目可移除后继续。
- [x] 运行定向测试与模块编译，记录实际通过情况。

```powershell
./gradlew.bat :feature:library:testDebugUnitTest --tests '*PaperAssemblyViewModelTest' :feature:library:compileDebugKotlin :app:compileLocalFirstDebugKotlin --console=plain
```

## 任务 2 — Markdown 块层级和流式阅读

修改 core/ui 中 AiReplyRichMarkdown.kt、TutorMarkdownTokens.kt、TutorMathProse.kt、TutorStreamingReply.kt 及既有测试；按需使用现有 core/markdown 的 AST 与渲染槽。

- [x] 补充标题、段落、步骤编号、嵌套列表、引用、表格与数学比较式测试。原 HTML、活动 scheme 和远程图片拒绝测试继续通过。
- [x] 将标题和段落间距设为显式主题令牌。正文 16sp、约 26sp 行高，段落间距 12dp，标题前后区分节奏并支持字体缩放。
- [x] 公式分割尊重代码与转义上下文，长公式局部滚动。保留 CommonMark 列表和代码块复制行为。
- [x] 流式尾块与完整块使用同一正文基线，以段落位置建立稳定身份。未闭合语法可读，完成时避免整篇动画和阅读位置跳动。
- [x] 核对现有滚动跟随逻辑，上滑阅读时保留位置，回到底部只由明确操作触发。

```powershell
./gradlew.bat :core:ui:testDebugUnitTest :feature:tutor:compileDebugKotlin --console=plain
```

## 任务 3 — 图片和 JS 快照进入消息文档

新增批准的 TutorMessageDocument.kt 与测试，修改 AttachedImageCard.kt、TutorMathProse.kt、TutorRoute.kt、TutorGeneratedTurn.kt、TutorChatConversation.kt、TutorSurfaceMessages.kt、components/TutorSessionPanel.kt 和 components/TutorSessionComponents.kt 中实际调用。

- [x] 定义普通 Markdown 块和受控 Figure 引用块，单独段落中的 `[[figure:本地引用]]` 才能成为插图位置。
- [x] 以当前消息已提交资产和视觉快照建立允许引用集合。代码或数学中的相同字符按原文呈现，非法引用产生局部不可用状态。
- [x] 添加测试，覆盖正常穿插、重复引用、未知 ID、代码中的示例、无标记历史图和空正文。

```kotlin
val blocks = parseTutorMessageDocument("先判断方向。\n\n[[figure:figure-a]]\n\n再求大小。")
assertEquals(3, blocks.size)
```

- [x] 插图默认展开、完整适配宽度、图注跟随图，复用大图查看器。重复引用显示图号定位，历史图追加一次。
- [x] 发送前图片继续允许预览移除，已发送图片保留原顺序。解码失败的重新加载与重新生成使用不同动作。
- [x] 模型提示词与工具返回提供本地位置引用规则，保存原正文，复制和回喂使用可读图号，避免把内部标记当成教学正文。
- [x] 核对 JS 的 program/snapshot/preview 读取、冻结 drawOps 重放及新执行的真实装配。已有快照显示与新 JS 生成分别记录结果。只有支持实际交互的产物显示控制项。
- [x] 使用现有事务维护消息、工具痕迹与视觉引用的一致性，取消与重试复用原有幂等操作。

```powershell
./gradlew.bat :core:ui:testDebugUnitTest --tests '*TutorMessageDocumentTest' :feature:tutor:compileDebugKotlin :app:compileLocalFirstDebugKotlin --console=plain
```

## 任务 4 — 个性化回复与通用答案的生成约束

修改 core/data/model/OpenAiModelTaskAdapters.kt、OpenAiDetailedSolutionProtocol.kt、FigurePromptAssembler.kt，core/model/TutorTasks.kt、DetailedSolution.kt，core/data/export/RoomDetailedSolutionRepository.kt 及实际聊天提交链路。同步 docs/current/figure-generation-prompt-policy.md。

- [x] 在既有 TutorConversationPromptTest 和 DetailedSolutionProtocolTest 中加入用户要求的断言，分别验证个性化与通用输入边界、正文组织和必需图解。
- [x] 通用答案提示词明确独立阅读、规范步骤、必要公式、单位与结论。学生称呼、学习记录、聊天对话不进入其生成请求或复用键。
- [x] 两类内容均要求具体教学目标与图文对应，每条讲解回复、每题答案至少一图。流程图与关系图必须承载解释，原文截图和装饰图不计入。
- [x] 生图描述包含对象、关系、方向、条件、标签与单位。模型自检覆盖学科正确性、图文一致性、无答案提前泄露、手机阅读和灰度打印可辨认。
- [x] 详细答案解析器验证必要 figure 步骤，宿主在准备与导出边界验证已落盘的图与题面身份。协议合法和图可读分别验证。
- [x] 聊天在最终提交时校验有效图引用。配图未完成保留文本和恢复操作，展示未完成状态，处理同一请求重复提交。
- [x] 历史无图答案保持可读，再准备时复用已有有效文本并补图，已有有效图保持复用。只完成一部分图片时恢复缺失项，避免重复生成已完成内容。
- [x] 明确记录模型调用失败、配图失败、能力不可用和语义待人工核对的差别。阻止未完成答案进入预览确认导出。

```powershell
./gradlew.bat :core:model:test :core:data:testDebugUnitTest --tests '*DetailedSolutionProtocolTest' --tests '*TutorConversationPromptTest' :core:export:testDebugUnitTest --tests '*DetailedAnswerExportTest' --console=plain
```

## 任务 5 — 提问、反馈和答案预览统一

修改 TutorGeneratedTurn.kt、components/TutorSessionComponents.kt、DetailedAnswerPreviewSheet.kt、DetailedAnswerExport.kt 及现有测试。

- [x] 题干、选项说明、提示、正确与错误反馈和完整解法统一使用同一正文与图文组件。
- [x] 选择或填写与提交分离，每张卡一个问题并独立提交。选中只更新草稿，点击“提交答案”才写入作答，提交中防重复，失败保留选择，反馈使用对应语义色。
- [ ] 自由作答输入与提交操作相邻，键盘打开时保持主要动作可达。
- [x] 通用答案预览和 PDF/DOCX 在相同步骤位置显示图，使用有效图注，图题与最终答案对应。打印检查图中文字和灰度辨识度。
- [x] 检查每题配图完整性，缺图、坏图或版本失配时禁用确认并提供对应恢复入口。

```powershell
./gradlew.bat :feature:tutor:testDebugUnitTest :feature:library:testDebugUnitTest :core:export:testDebugUnitTest :app:assembleLocalFirstDebug --console=plain
```

## 任务 6 — 实际页面与全链路核验

- [x] 使用已批准的独立模拟器 emulator-5592（ui_teaching_20261108），更新既有实时可交互预览。原 emulator-5590 已被其他构建使用。
- [ ] 验证组卷从入口到候选筛选、累计选择、已选排序、卷面预览、题目卷与详细答案导出。
- [ ] 验证聊天长文、短文、步骤、公式、图片穿插、JS 已存快照、提问反馈、流式生成和返回位置。
- [ ] 对函数图、受力图和关系图分别核对标签、方向、单位、正文对应与灰度打印效果。不能调用真实模型或打印机时如实记录未验证项。
- [ ] 检查大字体、键盘、快速点击、配图失败重试、无生成能力和减少动画。
- [ ] 自审 diff，重新读取改动，更新本计划的完成状态与证据。只有实际结果与设计一致的项才能勾选。

## 执行记录

- 2026-11-08 已核对现有生成与导出协议。OpenAiDetailedSolutionProtocol 当前存在“配图不是必需装饰”和无能力时纯文字完成的规则，与用户新增要求冲突，任务 4 明确替换。
- 2026-11-08 已核对 DetailedSolutionInput 使用冻结题面，保留其通用答案输入边界。不得把个性化回复直接转换为详细答案。
- 2026-11-08 本计划编写时，JS 隔离引擎尚未在生产 coordinator 中装配，任务 3 必须以真实接线结果验收。

- 2026-11-08 用户进一步限定省略条件。JS 接入后承担简单绘图，只有确需生图模型的复杂图在该模型不可用时允许省略。规划与验收必须验证具体理由和实际能力，普通绘图失败进入恢复流程。


## JS 生产接线的新增批准范围

2026-11-08 用户已通过弹窗批准下面三条路径。具体接线补充见已授权证据目录的 js-chat-production-wiring-proposal-2026-11-08.md。

- app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsFigureHost.kt
- core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/JsFigureArtifactAdapter.kt
- app/src/androidTest/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsFigureHostInstrumentedTest.kt

任务 3 增加真实引擎与冻结帧装配、JS 执行结果到快照/预览/消息引用适配、无图像模型时的简单图验证。使用已有视觉表和提交事务，保留 JS 方法身份。

当前轮验证记录为 chat-paper-1108-build3.log。共享 UI 测试、错题本测试、讲题模块和应用编译通过。本结果尚不覆盖最新的 DAO 补图重试、全部生成协议回归和新增 JS 生产接线，完成后重新验证。

## 参考截图确认后的收口任务

2026-11-08 用户确认助手讲解通栏展开、学生消息右侧紧凑气泡、每卡单题以及先选择或填写再提交。以设计第十节为准实施。

- [x] 核对所有讲解入口的消息容器，正文、公式、插图和提问卡使用同一内容宽度。
- [x] 精简插图标题与重复操作，图文按步骤连续呈现，默认展开并支持放大。
- [x] 修复公式上下标、分数、括号及长公式阅读，核对手机与导出结果。
- [x] 将选择草稿与正式提交分离，覆盖调整选择、未选禁用、重复点击、失败重试和历史已提交状态。
- [x] 完成产物引用和提交状态的进程恢复接线，再运行最终构建及相关验证。
- [x] 将实际结果与待验证项写回计划，提供当前构建的可交互预览。

新增独立模拟器的两条路径已获得用户批准。

- C:/Users/听云/.android/avd/ui_teaching_20261108.ini
- C:/Users/听云/.android/avd/ui_teaching_20261108.avd/

目前工作仍在实施中。此前通过的构建与原生案例不能代替本次全部改动后的最终验证。


## 本轮实施与核验记录

### 已落地

- 独立组卷复用错题本查询与筛选，有序选择、移除、版本校验和已有卷面编辑器接通。Library 单元测试通过，先前原生走查已确认两题选择和排序进入编辑器。
- 助手正文使用完整阅读宽度，学生消息沿用右侧紧凑气泡。标题、段落、公式和插图共用阅读组件。插图默认展开、图注在下、点击放大，重复引用定位原图。历史图片也默认展开。
- 单题选择卡保留草稿，点击提交后才调用写入。未选禁用、重复提交、失败保留、状态恢复和下一题重置已做原生验证。开放式输入继续使用已有对话输入区，其交互边界未改为新的评分协议。
- App 已装配隔离 JS 引擎和规范预览适配，三类教学程序实际执行、保存、重放，讲解图能够进入消息及 PDF/DOCX 详细答案。
- 修复手机和打印共用的上下标、分数位置和相邻字符间距，忽略 LaTeX 排版指令，独立公式居中，长公式局部滚动。导出缓存版本同步更新。
- 工具交付痕迹随成功输出持久化，进程恢复可读回配图完整性说明。

### 当前可复核证据

证据目录为 .jez/artifacts/library-interior-implementation-2026-10-31/。

- reading-design-1108-full-build.log，App、测试 APK、模型、UI、Tutor、Library 和 Export 的构建及相关测试通过。
- reading-design-1108-final-build2.log，模型提示词、通用答案协议、统一协调器及配置生图器的定向测试通过。
- reading-design-1108-question-native.log，改选、显式提交和失败后重试通过 1 项原生测试。
- reading-design-1108-choices-native.log，重复提交、恢复、等待数据库响应、答案揭示与新题重置通过 5 项原生测试。
- reading-design-1108-submit-recovery-native.log，真实写入失败、自由回复恢复、提示失败重试通过 3 项原生测试。
- reading-design-1108-figures-native.log，三类教学程序、消息图引用和长公式阅读通过 5 项原生测试。
- reading-design-1108-typesetting-final2.log，数学排版修正后的 App 构建与 Math、UI、Export 测试通过。

### 尚需区分的验收

- 教学图样例来自人工编写程序，尚不能据此声称真实模型已达到图义正确率、首次成功率或修复率目标。
- PDF 已实际生成并渲染检查，DOCX 已核对内嵌图片摘要与图注，实体打印机输出仍未验证。
- 新模拟器和真实 Provider 的组卷到答案生成全过程、跨设备尺寸矩阵及完整无障碍验收仍需后续执行。
- 转写、来源重建与照片净化的历史 JS 挂接门仍需按原 4E 计划单独验收，本轮讲解图接通不能替代这些入口的完成证明。


### 最终追加核验

- reading-design-1108-native-metrics-build.log，使用实际字形 advance 的手机与 PDF 排版构建通过，Math、UI、Export 测试通过。度量回调为纯接口，业务模型保留框架无关性，PDF 计划器继续使用已有度量端口。
- reading-design-1108-native-metrics-final.log，实际字宽版本的公式阅读、JS 函数图及 PDF/DOCX 导出通过 2 项原生测试，重新渲染检查无字母挤压。
- reading-design-1108-largefont-native.log，fontScale 1.3、关闭动画时的插图、放大、定位和长公式阅读通过 2 项原生测试，原系统设置已恢复。
- reading-design-1108-streaming-final.log，流式配图引用显示准备状态，代码示例与尾部空白保持原样，App 与 UI 测试通过。
- reading-design-1108-js-sdk.log，JS 编译器及 SDK 96 项测试通过。
- 实时预览已切换到 emulator-5592，并验证页面 HTTP 200、设备连接正常。这是新建的独立模拟器，当前题库为空。通过生产组件渲染的讲解与提问样例另存截图，未将样例伪装为真实模型会话。

关键截图为 reading-single-question-1108.png、reference-reading-formulas.png、reference-reading-formulas-largefont.png 和 visual-host-teaching.png，打印产物为 teaching-detailed-answer.pdf/.docx，PDF 实际渲染页为 teaching-detailed-answer.png。

本轮实现与工程验证已落地，全量验收仍保留前节明确的真实 Provider、实体打印、来源重建及设备矩阵事项，不能标为全部完成。

最终实际字宽版本的 fontScale 1.3 阅读回归通过 1 项原生测试，见 reading-design-1108-native-metrics-largefont.log；设置已恢复，实时预览已回到正常应用页面。git diff --check 通过。


## 2026-11-09 用户反馈修正

- [x] 单题卡通过受控正文位置插入讲解段落之间，提交仍只处理当前问题。模型提示词、流式占位、复制和历史回喂同时处理位置标记。
- [x] 手机预览、PDF 与 Word 的答案图片采用有界尺寸、保持比例和居中显示，导出缓存升版。
- [x] inline-question-1109-final-build.log 记录 App、测试 APK、UI、Export 及定向提示词测试通过。
- [x] 原生核验单题卡前后段落位置、选择与失败重试，实际生成 PDF/DOCX 并检查图片尺寸。

本轮直接修改此前批准的源码、测试、设计和计划文件，证据继续写入已授权目录。

原生证据为 inline-question-1109-native.log 与 answer-figure-sizing-1109-native.log，各通过 1 项测试。已检查实际截图 inline-question-1109.png 和 answer-figure-sizing-1109.png，单题卡位于前后讲解段落之间，打印图等比缩小并居中且标签清楚。Word XML 同时校验居中及宽高上限，产物为 answer-figure-sizing-1109.pdf/.docx。以上使用人工测试样例，不替代真实模型语义质量验收。


## 2026-11-09 详细答案最终规范修正

- [x] 题目标识仅保留组卷题号，删除来源、版本与核对附加行，同步手机预览。
- [x] PDF 和 Word 删除图片 AI 声明及公式“读作”，精简重复图题和图号，生成提示词同步规范。
- [x] 依据用户最新要求，图片宽高各缩至第十一节版面的 50%，面积为其四分之一，覆盖 PDF、Word 与手机预览。
- [x] 完成最终原生导出、实际页面检查与面积对比，登记证据。

构建和定向测试日志为 concise-answers-quarter-1109-build.log。

最终验证通过。concise-answers-quarter-1109-final-build.log 记录 App 构建及 Export 测试通过，concise-answers-quarter-1109-native.log 记录实际 JS 图到 PDF/DOCX 导出通过 1 项原生测试。Word 内嵌图片尺寸与上一版产物相比，面积比为 0.2499999175，已确认来源、版本、AI 提示和“读作”文字缺席。检查结果为 concise-answers-quarter-1109-check.json，实际渲染页及文件为 concise-answers-quarter-1109.png/.pdf/.docx。


## 2026-11-09 聊天动作、版本与玻璃栏实施计划（待整体确认及新增路径批准）

设计依据为设计文档第十三节。用户已逐项确认最后回复可重生成、旧版保留、每版正文与题卡整体切换、当前选定版本用于追问、历史版本只读，以及阅读自动收紧上下栏。

### 预计新增路径，共 7 条

1. C:/Users/听云/.codex/worktrees/ui-green-pages/smart mistake book/core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/entity/TutorReplyVersionEntities.kt
2. C:/Users/听云/.codex/worktrees/ui-green-pages/smart mistake book/core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/dao/TutorReplyVersionDao.kt
3. C:/Users/听云/.codex/worktrees/ui-green-pages/smart mistake book/core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/TutorReplyVersionMigration_75_76.kt
4. C:/Users/听云/.codex/worktrees/ui-green-pages/smart mistake book/core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/76.json
5. C:/Users/听云/.codex/worktrees/ui-green-pages/smart mistake book/core/ui/src/main/java/com/tingyun/smartmistakebook/core/ui/ClaudeGlassSurface.kt
6. C:/Users/听云/.codex/worktrees/ui-green-pages/smart mistake book/feature/tutor/src/test/java/com/tingyun/smartmistakebook/feature/tutor/TutorReplyVersionSelectionTest.kt
7. C:/Users/听云/.codex/worktrees/ui-green-pages/smart mistake book/core/database/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/database/TutorReplyVersionMigrationInstrumentedTest.kt

其余实现修改现有文件，证据写入已授权的 library-interior-implementation-2026-10-31 目录。

### 阶段 A，版本数据与旧数据兼容

- [x] 新增版本组、回复版本及版本作答实体和 DAO，注册 75→76 迁移，生成并核对 76.json。旧消息按单一版本读取，首次重生成事务冻结原记录。
- [x] 在现有 TutorConversationRepository、TutorInteractionRepository、数据库合同及 Room 仓储增加版本身份和选择投影。版本号、题卡、答题状态和图片引用以同一版本 ID 关联。
- [x] 将版本记录纳入现有备份恢复、删除与资产引用清理。运行针对性迁移和旧数据读取验证。

### 阶段 B，真实重生成与上下文

- [x] 在 TutorLobbySendCommands、TutorRespondCommands、TutorPlanCommands 和任务输入/指纹中接入独立重生成身份，覆盖智能体大厅、拍照讲题、已存错题讲题。
- [x] 生成成功后原子发布新版本，失败、停止和恢复沿用本次操作身份。提交前复核最新轮次，处理快速重复点击和并发发消息。
- [x] 模型上下文、讲题时间线和学习事实读侧使用同一已选版本。追问时固定本轮版本，历史只读查看。
- [x] 每版题卡独立提交，旧请求的默认字段保持既有编码与指纹兼容。

### 阶段 C，消息动作与版本切换

- [x] ClaudeCopyButton 添加常驻复制图标，复制成功展示勾选反馈。
- [x] TutorSurfaceMessages 和 TutorGeneratedTurn 接入带图标的重新生成、停止与失败重试。按最新可编辑回复判定入口。
- [x] 提供左右版本切换和版本页码，切换正文、配图、题卡、草稿及反馈，恢复页面后保持选中版本。

### 阶段 D，玻璃态与阅读面积

- [x] 核对 Haze 1.7.2 依赖与当前 Compose 的实际编译，锁定依赖校验信息，添加共享 ClaudeGlassSurface。
- [x] 重排 TutorConversationFrame 的正文采样层和上下浮层，统一首尾避让及安全区。顶栏、输入栏依据滚动意图收紧，回顶部和输入焦点驱动展开。
- [ ] 输入中文组合、草稿、附件、交互模式及键盘状态随收展保留。大字体适配与减少动态效果纳入同一状态机。
- [ ] 对比改造前后同尺寸截图，检查正文可见面积、模糊范围、按钮可读性、最后一行与提问提交动作可达。

### 阶段 E，针对性收口

- [x] 版本选择与上下文投影单元验证，75→76 数据升级验证，App 与相关模块编译。
- [ ] 独立模拟器实走两次重生成、版本切换、各版本答题、追问锁定、失败与取消、重启恢复、键盘和阅读收展。
- [x] 保存实际截图及执行结果，区分示例模型响应、真实 Provider 和设备渲染的验证结果。


### 2026-11-10 实施与验证记录

统一会话层完成三类回复的重生成，保留原消息为兼容底本。补齐独立版本的模型请求、输出、Provider 快照、配图引用、题卡作答和浏览草稿，支持任务台账清理后的重建。后续追问使用已选投影，历史浏览与固定上下文分离。待确认本地操作完成后恢复重新生成入口。已锁定版本仅允许真实答案曝光单调追加解答展示状态。

独立审阅发现的 6 项重要问题已修复，覆盖历史题卡反馈、追问揭示、确认卡收尾、流式停止、复制完整内容和玻璃遮挡区的曝光判定。

本轮实际结果

- App、数据库和讲题原生测试包构建成功。
- 聊天定向单测 56/56，图文复制与解析单测 10/10。
- 备份结构与历史归档兼容用例 50/50。
- emulator-5592 数据库原生回归 3/3，包括 75→76、独立作答、重开恢复、追问锁定及锁定后的单调展示记录。
- 原生界面回归 3/3，包括失败保留原文、成功版本切换、单题显式提交、大字体与减少动画条件下玻璃栏收展、聚焦恢复和无遮挡阅读视口。

界面回归使用示例模型响应与真实生产组件。真实外部 Provider、完整应用重启和完整聊天流程的中文输入尚待集成验证。当前专用模拟器已有非调试应用，版本号 101177 高于本次 APK 的 2，系统拒绝覆盖安装，既有应用和数据保留。

截图与日志位于已授权证据目录，主记录为 chat-glass-versions-1109-ledger.md。

模型任务入口追加核验已完成。重新生成采用独立操作指纹和调用预算，Respond 的新版不占用原回复槽位，常规重试的指纹规则保持兼容。真实数据库创建原回复与两个重生成任务的回归通过，覆盖槽位校验和完整性检查。最终日志为 chat-regeneration-1110-operation-build3.log 与 chat-versions-1110-db-final4.log。


## 全局精致书页与轻盈现代实施（用户已批准）

在当前隔离工作树内联执行，修改已有文件。

### V1 共享视觉标准

- [x] 修改 core/ui 的 Theme.kt、ClaudeLayout.kt、ClaudeButtons.kt、ClaudeInteractiveSurface.kt、WorkspaceSurfaces.kt、ClaudeSelection.kt，复用现有 Components.kt 的共享控件。暖白主底、墨色文字、青绿操作、暖金强调，建立 InputOutline、QuestionSurface、FigureSurface 三个有实际消费者的语义色。面板降阴影，浮层保留接触与环境阴影，48dp点击区域保留。
- [x] 扩展现有 SmartSemanticContrastTest，验证正文及语义强调的4.5对比度、输入边线与文字的层级。

### V2 讲题阅读与操作区

- [x] 修改 TutorGeneratedTurn.kt、TutorConversationSurface.kt、TutorSurfaceMessages.kt、TutorMarkdownTokens.kt、AttachedImageCard.kt、ClaudeGlassSurface.kt。题卡题干/选项/提交区通过间距和底色区分，输入区压低常态描边，玻璃使用适度透光，图解采用浅白轻容器。流式及完成正文共用16sp、26sp排版。
- [x] 运行已有 TutorMessageDocumentTest、TutorReplyVersionSelectionTest；原生检查显式提交、版本切换、大字体收展与无遮挡阅读视口。

### V3 错题本与阅读详情

- [x] 修改 LibraryQuestionCard.kt、MistakeSectionCard.kt。题图与标题处于列表首层，统一14—16dp圆角与细边，题目列表采用低阴影密度，详情模块保持阅读间距。

### V4 复习、我的及触发页

- [x] 修改 ReviewRoute.kt、ProfileRoute.kt 及共享 ClaudeDialog.kt、WorkspaceSheet.kt、ClaudeList.kt 等现有组件。复习入口、设置行、标题与按钮遵循同一层级，分组和表单保留清楚的操作重心。
- [x] 扫描各 feature 内直接 Material 弹层及自定义容器，记录使用共享主题覆盖及需单独调整的入口。按入口修正相关圆角、边框和阴影。

### V5 验证与交付

- [x] 执行 :core:ui:testDebugUnitTest、讲题定向测试与 :app:assembleLocalFirstDebug，阅读真实命令输出。
- [x] emulator-5592 原生组件截图检查讲题、题卡和玻璃栏，并检查错题本与设置入口样例。记录受设备高版本限制的完整安装验证范围。
- [x] 更新本节进度与已有执行记录，运行 git diff --check。


### 2026-11-11 实施结果与覆盖边界

共享配色、字阶、行高、圆角、卡片、设置行、选择行和浮层已接入生产代码。讲题采用16sp/26sp正文、暖金题卡标识、淡底选项与轻玻璃输入区。错题列表取消常态阴影，详情模块使用16dp细边容器。“组卷”入口改用文档集合图标。智能体修改范围和纸质复习选择行同步统一。

源码扫描确认四个主要 feature 的普通对话框使用 ClaudeAlertDialog，编辑、分类、组卷、详细答案、复习和设置面板沿用 WorkspaceSheet 或共享面板。独立 Dialog 保留在图片查看、题面图片处理和裁剪等图像工作区。这些工作区沿用共享主题与操作控件，图像画布的遮罩与坐标处理维持各自用途。共享主题同时覆盖主页面和触发页面。

本轮在 SharedUiLayoutInstrumentedTest 的已有文件增加设置行与复习安排弹层样例，用于检查生产共享组件的选择、提交和返回，样例不连接真实设置仓储。讲题原生回归使用示例模型响应，错题本回归使用示例题库数据。

当前验证结果

- :core:ui:testDebugUnitTest 通过72项，含正文与强调色4.5对比度、透明控件在明暗背景中的焦点对比度。
- :feature:tutor:testDebugUnitTest 定向通过28项，包括 TutorReplyVersionSelectionTest 与 TutorConversationTimelineTest。
- :app:assembleLocalFirstDebug、:feature:tutor:assembleDebugAndroidTest、:feature:library:assembleDebugAndroidTest 构建成功。
- emulator-5592 原生界面检查通过19项，讲题与共享组件10项、错题本与导出面板9项。覆盖大字体、减少动态效果、长弹层按钮可达、题图外显、收藏独立、筛选恢复、版本切换与题卡显式提交。
- 实际截图保存在既有证据目录。editorial-chat-1111.png、editorial-library-1111.png、editorial-library-large-type-1111.png、editorial-settings-1111.png、editorial-settings-sheet-1111.png 均来自设备渲染。

完整应用覆盖安装仍未完成。当前模拟器安装了非调试101177版，本次APK版本为2，系统拒绝降级覆盖。独立测试包已安装并验证生产UI组件。完整应用导航、真实设置保存、真实Provider及所有业务状态的整机视觉验收继续保留为待验收项。

最终构建日志为 editorial-global-final-build5.log，原生日志为 editorial-native-tutor-final.log 与 editorial-native-library-final.log，均位于既有证据目录。


大字体截图追加修正已实施。ClaudeSegmentedControl 使用文字测量值计算最小单元宽度，标签保持单行，溢出可横向滚动。选中面改为12dp小圆角，移除常态阴影。既有大字体原生用例增加四字标签单行高度检查，避免“掌握程度”再次拆行撑高。


## UI 基准合入主分支

本次整合 UI 分支 d6917dc7 与主分支 ef6a9d97。解决绘图提示词、RoomStudyDatabase、StudyDatabasePort 与导出策略测试的显式冲突，修复自动合并产生的重复参数和重复属性。绘图保留教学样例及完整约束，PDF缓存使用主分支第3代内容策略和本版第5版布局身份。

主分支已提交的 App 入口依赖尚未提交的知识复习确认与校准回调。使用主工作树的对应实现补齐已有 KnowledgeReviewSessionScreen、KnowledgeReviewSessionViewModel 及其现有测试。校准决策类型与状态文案放入现有 ViewModel 文件，保留本版题面面板视觉。整合仅涉及这些必要接线，主工作树仍有615项未提交改动待后续收口。

本轮验证通过

- :app:assembleLocalFirstDebug 构建成功。
- 共享UI单测72项、导出定向单测28项、绘图提示词单测7项、知识复习ViewModel单测23项，共130项、0失败。
- JS生产编译器检查96项通过，绘图提示词样例生产编译检查1项通过。
- 相对 origin/main 的本次增量差异检查通过。主分支原有验收证据包含空白格式告警，证据内容沿用原提交。

证据位于已授权的 library-interior-implementation-2026-10-31 目录，主日志为 ui-merge-1111-build5.log，补充 ui-merge-1111-visual.log 与 ui-merge-1111-prompt.log。完整应用设备验收继续按前文记录跟进。
