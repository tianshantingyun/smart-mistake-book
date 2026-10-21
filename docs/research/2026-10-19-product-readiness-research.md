# 实际产品闭环研究：纠错、复习、导出、恢复与生图

研究跨 2026-10-18 至 2026-10-19。范围：解决上一轮产品审查发现的问题，并修订后续计划。本文是源码与官方资料研究，不是新版本验收报告；本轮未运行真实模型、完整设备流程或产品测试。

结论：保留现有本地学习内核、原生界面、单一文档模型和统一生图路线。先补可纠错的数据与可直接操作的入口，再扩大自动化。把开发、测试和真实数据启用分开；不能用隐藏 UI 的办法绕过知识点证据门。

## 1. 证据与适用范围

| 证据 | 能支持什么 | 不能支持什么 |
|---|---|---|
| 当前工作树源码、既有测试源码 | 真实调用、接口、持久化与未接线位置 | 没有执行的测试通过、设备行为已复现 |
| 2026-10-18 已安装包的录入页截图 | 当时可见的拍照、相册、PDF入口与待处理任务 | 安装包等于当前未提交源码、其他页面全流程已验证 |
| 官方产品文档、Android/SQLite文档 | 已公开工作流、平台 API 的保证与限制 | 本项目采用相同方法便自然正确、竞品内部实现 |
| 既有 AUD/KD 报告 | 当时提交与设备的缺陷证据 | 缺陷在当前提交仍必现、旧红门已经修复 |

工作树含其他任务改动，本轮仅修文档。并行研究助手中途遇到用量限制；采用的关键事实由主线继续核对，未完成的助手报告不列为独立审查证据。基线与验证记录位于 `.jez/artifacts/product-plan-research-2026-10-18/`。

### 1.1 最终核验时的工作树增量

2026-10-19最终核验发现，工作树在研究期间继续变化，不能把研究起点的“尚无实现”用作最新状态。以下是源码静态复核，**不是产品测试通过**；§3保留问题与方案，遇到当前进度以本表及MASTER最新状态为准。

| 能力 | 当前直接看到的实现 | 后续如何承接 |
|---|---|---|
| 无图手动建题 | `CaptureViewModel.startManualDraft`已有LIBRARY入口、无照片命令及重试；采集页已有按钮/调用 | PRD-06复用并核验空源读写、提交、恢复和首装入口；不能再从零写生产者，也不能据按钮关闭KD-52 |
| DOCX与文档副本编辑 | Tutor侧已有格式/布局及编辑后导出回调；列表/详情原生入口仍调用默认格式导出 | PRD-04补原生格式选择和交付；6B-2先对照在飞编辑代码补差距，不能把Tutor回调等同于原生入口已完成 |
| KD-55字节一致性 | `DocxImageSource.read`已对将嵌入的同一份字节检查长度/hash，但异常返回null并允许省略 | 复用检查；验证调用点都传入预期值，必需题图缺失/不匹配须显式失败，非必需图省略也应说明，不能静默交付残题 |
| KD-53大库预算 | `SmbkResourceLimits`已分数据库1GiB、其他单条100MiB、累计2GiB；validate/unpack共用类型预算 | 复用预算改动并验证创建端同口径、空间预检及≥365MB实际往返；不能用参数放宽代替恢复证据 |
| 统一生图 | 已有`FigureGenerationPolicy`、`DefaultFigureGenerationCoordinator`及派发账本相关代码；协调器明确仅执行IMAGE_MODEL，对JS返回UNAVAILABLE | 先映射WG已接部分，补真实proposal/全入口/持久化验收；继续JS运行库工程，不广告JS、不重新创建协调器 |
| 备份快照与草稿 | `RoomBackupSupportStore`仍占Room写连接做VACUUM；`reloadDraftFromConversationRow`仍无版本比较直接覆盖draft | PRD-07仍需快照探针；PRD-08仍先复现后修，不因相邻文件改动就关闭问题 |

源码差异与核验点记录在本轮artifact中的`concurrent-source-drift.json`；它记录共享工作区变化，不证明其他改动全部正确。本轮没有修改产品源码，也没有替这些在飞实现运行测试或关闭缺陷。

## 2. 对照成熟产品后真正应该借鉴的行为

| 产品/资料 | 已核实做法 | 对本项目的处理决定 |
|---|---|---|
| [Anki 学习流程](https://docs.ankiweb.net/manual/studying) | 区分新卡、学习中、到期；提供自评、编辑与临时跳过等操作 | 基础复习不能依赖模型在线。沿用本项目已裁定三键与曝光规则，不照搬四键、10秒建议或其权重 |
| [AnkiDroid 手册](https://docs.ankidroid.org/manual.html) | 原生添加、编辑、导出、撤销、备份恢复入口 | 误点后的恢复要作用于事实与日程；文档导出和题面纠错应能直接进入 |
| [RemNote 新卡管理](https://help.remnote.com/en/articles/16213222-managing-new-cards) | 批量/AI新增卡可先放待学习集合，首次练习后进入间隔复习 | 本项目已有新引入预算，不另建队列；用大量导入与积压场景验证预算、计数和继续学习出口 |
| [Photomath 扫描与编辑](https://support.google.com/photomath/answer/14333327?hl=en) | 可调扫描范围、手动输入、修改识别后的题目 | 提供可发现的纠错入口，保持自动录入主流程；不增加每题强制人工核对 |
| [Khanmigo 反馈](https://support.khanacademy.org/hc/en-us/articles/13983335341069-How-do-I-leave-feedback-about-Khanmigo) | 可反馈不正确、不帮助思考或直接给答案等问题 | 反馈应绑定具体题版本/答案版本/回合，能进入处理链；反馈按钮本身不等于错误学习记录已修复 |
| [试卷宝 iOS 开发者介绍](https://apps.apple.com/cn/app/%E8%AF%95%E5%8D%B7%E5%AE%9D/id1624848373) | 扫描、去手写、框题、整理和导出为主要使用任务 | 优先把已有资产变成可保存/分享的卷子。此处仅参考公开介绍，不声称实测其 Android 版本或内部算法 |

这些产品支持“任务可完成、错误可恢复”的方向，不构成本项目掌握算法有效性的证明。账号、云同步、社交、家长平台不因竞品存在就加入当前范围。

## 3. 源码发现与处理方案

以下路径相对仓库；`D` 为 `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data`，`M` 为 `core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model`，`K` 为 `core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain`，`DB` 为 `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database`。

### 3.1 保存后的题面纠错必须独立于改卷

保存前已有 `CaptureCorrectionForm.kt`；保存后详情主要是阅读、讲题、导出、备注、归档、归类。`K/MistakeDetailRepository.kt` 没有对应题面修订命令。现有 revision 与不可变快照可复用，不能据此声称完整编辑入口已存在。

采用两个明确动作：“修改这道题”产生原题的新 revision；“修改本次试卷”仅改文档副本。原图与原文保留，修改使用 expectedRevision/CAS；异步识别、生图、判词须核对其冻结 basis，不能回写到更新后的题。文字样式调整不重排学习；条件/答案改变需检查相关判分与图的有效性。旧作答仍指向旧 revision，不自动当成新题作答，也不因文字修改直接清空历史。

### 3.2 答案键错误是事实纠错问题

`D/study/KnowledgeQuizItemStore.kt` 对模型提供的键写 VERIFIED/AUTO_VERIFIED；`D/study/KnowledgeQuizFeedbackWriter.kt` 以本地选项 ID 比较确定方向并传 `OBJECTIVE_ANSWER_CONFIDENCE=1.0`。比较过程确定，不证明模型答案键正确。

更重要的是，小测当前写 `learner_chat_evidence` / `ChatEvidenceSubmitted`，不是 attempt。`DB/dao/AttemptTransactionDao.kt` 已有 AttemptCorrection 的事务/outbox，`K/LearningProjector.kt` 已有按原作答顺序替换证据的全重放；它们不能直接修复 chat evidence。最小方案是扩展既有账本的**定向纠正/撤销事件**及源引用，复用现有 projector/outbox，不另建评分系统。

答案键增加独立的来源和验证状态。自动比较仍在本地；机器可验证的来源按已验证规则计证据。只有模型建议且没有独立支持的键，首批只给本轮反馈，保留学生答案，不自动写成高置信知识掌握；不强制用户逐题确认。若以后要让此类键参与定量投影，应单独校准证据定价，不能在本轮凭空给一个折扣系数。已存在的旧记录不批量删除，按可追溯身份处理被质疑项；未知历史来源如实标记。

“答案有问题”先让对应键处于争议状态，停止新错误扩散；确认修正后按受影响 submission/evidence 清单补偿与重放。已绑定历史 attempt 的纠正复用 AttemptCorrection，chat evidence 必须有自己的显式目标。把新答案再记一条正向证据不能抵消旧错误。

### 3.3 不采用“只隐藏掌握页就提前开放复习”

`M/LearningState.kt` 的 LocalReviewSelfReportContract 目前仍是旧历史兼容形状；`K/LearningProjector.kt::attributionsForAttempt` 会在重放时从当前绑定重派生归属，`D/study/StudyProjectionDrainer.kt` 还会补 pseudo 绑定。空 attributions 或只隐藏 UI 无法隔离知识点影响。

比较三种实施方式：

| 方式 | 收益 | 代价/判断 |
|---|---|---|
| 继续等全部门过后才开始开发阶段5 | 最少排期调整 | 用户基础流程缺口持续暴露，开发与验收过晚 |
| **开发/合成验收先行，真实数据启用保留门** | 先完成三键、撤销、无模型路径；不改证据算法 | **本轮采用**。启用与研发状态分列；5b另等3E队列门 |
| 新增 QUESTION_ONLY 作用域、以后自动晋升知识证据 | 可提前放出有限题级日程 | 须改增量/全量投影、迁移、恢复与回放；容易重复计分，当前收益不足以支撑另起过渡系统 |

因此修订的是“不得开发”的过宽排程，**不是降低3D门或允许真实数据提前写入**。SELF_REPORTED 仍依裁定32/36属于作答类、可准入记忆卡，不冒充 LOCAL_CHECKED，也不永久排除于掌握投影。作答前曝光和判定后展开继续分开。

### 3.4 导出、空态、手动录入应直接可达

原生列表/详情已有 PDF 导出，6B-1 已有 DOCX 渲染器和格式 sheet；缺口在格式 sheet 主要由模型建议打开。复用同一文档模型、参数校验、WorkManager 和交付链，把展示层变成可由原生/智能体两入口调用。原生候选来自宿主选中题/筛选，模型候选继续受 NOTEBOOK_READ 与本轮范围校验，不能为了复用而放开任意 ID。

首装复习空态给“录入第一道错题”；有题但无到期题时说明完成情况，链接已有题库，不能误导为没有内容。KD-52 的无图手动题生产者在最终核验时已有在飞实现；按§1.1复用，继续验空 source 的读取、提交、恢复与入口，不只验按钮。

ADD_TO_REVIEW_PLAN 当前执行返回 NOT_WIRED_YET，卡片也如实说明不会修改计划，不是伪成功；问题在仍对模型广告。统一计算实际可执行能力供 native tools、Route B、提示词与执行端使用，历史卡保留诚实说明，未实现操作不进入本轮可调用集合。

### 3.5 数据恢复优先于内容勾选扩展

KD-53 要统一导出/校验/恢复的大小与空间预算，并流式校验；不能只取消100MB限制，仍要有条目、累计解压、数量和磁盘余量边界。

`DB/RoomBackupSupportStore.kt` 把整个 VACUUM INTO 放在 `isReadOnly=false` 的 Room 连接内；旧 SQLite fallback 还依赖“App空闲”注释保证复制期间无写入。后者不是实际互斥保证。SQLite 官方说明 VACUUM INTO 可生成一致快照，Online Backup API 可分段复制；但 Android/Room 当前是否暴露可直接使用的 native backup 接口，必须由实际驱动证明。[SQLite 备份](https://www.sqlite.org/backup.html)、[VACUUM](https://www.sqlite.org/lang_vacuum.html)

首选把支持 VACUUM INTO 的快照操作移到独立、受生命周期管理的连接，避免长期占 Room 唯一写连接；记录并验证 WAL 增长、取消与并发写。旧版本采用真正的短时全写者屏障、检查 checkpoint 结果后复制；无法在预算内安全完成时显式拒绝/重试，不能静默复制活库。增量 Backup API 是前述方案不能达门时的备选，不为计划假设不存在的 Room API，也不先换数据库驱动。

`TutorConversationViewModel` 的 SavedState 恢复后又读 Room 草稿，存在较旧持久内容覆盖较新恢复输入的静态风险，尚未复现。先做可控延迟/失败写入试验；成立后加版本与单写序列，提交成功清稿也用 CAS。Android 明确 SavedState 不替代长期存储，不能承诺未落盘按键在强杀后永不丢。[Android 状态保存](https://developer.android.com/topic/libraries/architecture/saving-states)

### 3.6 生图质量不能止于能执行、能显示

维持 JS/IMAGE_MODEL 同一策略和模型自主选择。另补“图义”验收：受力箭头必须表示真实方向；坐标标注/阴影范围必须与公式一致；阶段图位置与条件一致；不能靠图下注释解释画错的箭头。原生表单单独验输入/提交，不能被栅格图替代。

区分结构可执行、与题意一致、产物可读三个判断。模型自述“通过”或渲染成功只证明部分条件；验证器可检查规则，不能声称通用语义正确。代表任务采用预先冻结的源事实/解析预期，真实模型评测保留盲测集、失败样例、方法切换、总派发和延迟/成本；首批目标见设计与计划。未达到目标的能力不广告，不能按用途永久绑后端。

## 4. 开源与平台复用取舍

| 参考 | 用法 | 不采用的扩大范围 |
|---|---|---|
| [AnkiDroid](https://github.com/ankidroid/Anki-Android) | 阅读原生学习/编辑/撤销/离线工作流与测试组织；本轮不移植源码 | 不引入其整套集合格式、同步与调度器；代码复用另核许可证 |
| [Anki](https://github.com/ankitects/anki) | 对照学习事实与可恢复操作的边界 | 不以项目知名度替代本项目算法对照试验 |
| SQLite 内建能力 | 复用现有数据库、快照、事务和 outbox | 不为备份立即换驱动、引入第二事件总线 |
| [Android offline-first](https://developer.android.com/topic/architecture/data-layer/offline-first) | 持久本地数据支撑核心读取/任务，网络作为需要时的能力 | 不把所有任务都改成自动联网队列；进程重建继续禁止自动外发 |
| [Android SAF](https://developer.android.com/training/data-storage/shared/documents-files) | 系统选址保存、已有URI分享/权限，避免新增存储体系 | 不为导出请求广泛文件权限、不声称打开分享面板等于对方收到文件 |
| 既定 QuickJS/原生 Canvas/OOXML | 继续现有选型与运行库门，先交付静态图闭环 | 不再叠加WebView、通用HTML执行器或完整Word编辑内核 |

以上 GitHub 项目用作工作流/实现研究参考；本轮未以星数或下载量作质量判断，也未把未查验的版本数字写入计划。

## 5. 研究落点

采用[处理设计](../superpowers/specs/2026-10-19-product-readiness-design.md)与[后续实施计划](../superpowers/plans/2026-10-19-product-readiness-followup-plan.md)。现有 MASTER LINE 仍是唯一状态入口；新计划的 PRD 编号只是跨阶段补缺任务身份，不建立第二套阶段或第二套生图管线。

剩余不确定性均有可停止的验证任务：老API备份可中断性、草稿竞态复现、原始答案来源可追溯程度、真实Provider生图质量及性能。本轮没有把这些写成已解决的运行结果。
