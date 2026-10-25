# 后续实施计划：先完成可纠错、可复习、可交付的学生闭环

日期：2026-10-19。状态：研究与计划已形成，**下列新增验收目标尚未通过；已有在飞实现先复用核验，只补差距**。上游：[研究证据](../../research/2026-10-19-product-readiness-research.md)、[处理设计](../specs/2026-10-19-product-readiness-design.md)。总路线和当前完成状态仍以[MASTER LINE](../../REFACTOR-MASTER-LINE.md)为准。本文件的PRD编号是补缺任务身份，不重排既有阶段编号，不覆盖其他任务的在飞代码。

2026-10-19最终源码复核已发现手动建题、DOCX字节检查、文档编辑和统一生图协调器的在飞实现，详见[研究§1.1](../../research/2026-10-19-product-readiness-research.md#11-最终核验时的工作树增量)。勾选框表示本计划的完整退出门，不表示相关文件尚未存在。JS在当前协调器中仍返回UNAVAILABLE；不能据类型或测试文件存在就广告能力。

## 1. 实施次序与归属

| 优先批 | 任务 | 归属/依赖 | 可交付结果 |
|---|---|---|---|
| A：数据可纠错与可恢复 | PRD-01基线；PRD-02答案键；PRD-03原题修订；PRD-07备份 | 现有3B/4A/4C/6C维护；不等5B完整新业务 | 错题能改、错判能撤回重放、自建大库备份能恢复 |
| B：原生基础操作 | PRD-04导出；PRD-06空态/手动/能力；PRD-08草稿 | 各现有模块，原生导出不等3D/4E | 无Key也能录入手动题、整理已存内容、选PDF/DOCX保存分享 |
| C：复习闭环 | PRD-05三键/undo与无模型路径 | 契约确定后开发/隔离测试可先行；真实启用仍等3D全门与PRD-02/03 | 一次作答可完成、误点可恢复、不依赖模型判词 |
| D：统一静态生图 | 原WG-01～10 + PRD-09质量/提示词 | 既有4E顺序；需新图的分支才依赖它 | 同策略选择两方法，生成/保存/恢复/入卷端到端 |
| E：能力扩展 | PRD-10下游承接 | 5b另依3E；5c/5B原业务门；6B-2/3、6C-2按各自门 | 知识复习、出题回收、改卷、换机共用上述能力 |
| F：发布与长期校准 | PRD-11验收矩阵 | 7总收尾、X真实样本 | 新证据逐项验收；迁移重置最后；X不提前宣称 |

批次表示优先级和依赖，不要求独立任务全串行。共享源码/Room迁移/KB写入必须单写者；研究可以并行，不能让多条实现线同时占同一迁移号。原阶段5“不得开工”改为“受控研发可先行、真实数据启用挂起”，不降低3D数值门。完整5/5B依赖仍以总路线为准。

## 2. 实施位置索引

源码根统一为 `com/tingyun/smartmistakebook`，下表中的别名在本文件内展开；所有文件均为研究时实际存在的文件，新增文件或测试会明确标“拟新增”。

| 别名 | 仓库相对目录 |
|---|---|
| M | `core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model` |
| K | `core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain` |
| D | `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data` |
| DB | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database` |
| APP | `app/src/main/kotlin/com/tingyun/smartmistakebook` |
| T | `feature/tutor/src/main/java/com/tingyun/smartmistakebook/feature/tutor` |
| R | `feature/review/src/main/kotlin/com/tingyun/smartmistakebook/feature/review` |
| L | `feature/library/src/main/kotlin/com/tingyun/smartmistakebook/feature/library` |
| C | `feature/capture/src/main/java/com/tingyun/smartmistakebook/feature/capture` |

### PRD-01：冻结基线、影响面与启用状态

- [ ] 记录HEAD、工作树差异、当前Room/请求schema、依赖版本、现有入口与真实失败。研究时Room67/请求17仅为快照，执行时取下一未占用版本，不能与4E同时预占68/18。
- [ ] 将每项的“代码接线/模块检查/设备全链/真实质量”分别登记；模型调用失败不阻断无模型测试，真实Provider项如实UNVERIFIED。
- [ ] 为5a/b/c建立实际启用检查及测试库隔离：测试fixture不写用户库，状态页明确未开放。3D同包可信门和3E队列门有可追溯证据时才开放对应写入。

验收：新增任务无误勾完成，老完成记录保留；不因开发构建成功自动打开真实数据能力。关联AUD-15/16。

### PRD-02：答案键来源、争议、纠正与撤销（提前承接KD-43/AUD-11）

位置：`D/study/KnowledgeQuizItemStore.kt`、`D/study/KnowledgeQuizFeedbackWriter.kt`、`D/study/KnowledgeEvidenceWriter.kt`、`DB/dao/ChatEvidenceDao.kt`、`DB/dao/AttemptTransactionDao.kt`、`M/LearningState.kt`、`M/LearningLedgerFingerprint.kt`、`K/LearningProjector.kt`、`D/study/StudyProjectionDrainer.kt`。

- [ ] 固定小测题/键版本与学生选择、submission、evidence的显式关系；对可可靠还原的旧行回填来源，其他历史行标未知，不能猜配。schema迁移、导入/备份闭包、指纹/upcaster同批覆盖。
- [ ] 将“本地比较确定性”和“键本身已验证”拆开，删除自动比较便自动VERIFIED的推断。首批未核验/争议键保留会话反馈与真实选择，阻止新的数值掌握写入，不弹每题确认；有独立支持的键才走适用证据规则。
- [ ] 小测反馈/历史详情加入“答案有问题”：记录明确目标与理由，可暂停该键计分；更正键保留来源。未看到答案时使用中性入口，避免为了纠错提前泄露答案。
- [ ] 复用现有事务/outbox/projector，补chat evidence定向纠正/撤销，attempt使用已有AttemptCorrection。明确“撤销完全排除该有效证据”与“替代后重算”的区别；零权重事件不得继续推进记忆卡。
- [ ] 键版本/纠错回执/受影响目标及重放待办原子持久化，批量用稳定游标恢复；迟到旧判分校验键版本。被拒/未曾接受的旧记录不凭纠错生成新学习事实。
- [ ] 重放从原作答位置重算后续状态，不在当前日追加一笔正向；题级日程、KC状态、训练样本有效视图与统计一起对齐。修复完成前展示更新中，回执只在目标checkpoint追平后标完成。

拟新增验收组：`AnswerKeyCorrectionInstrumentedTest`、`ChatEvidenceCorrectionReplayTest`、`AnswerKeyTrustPolicyTest`。反例必须含错键判错→改键、正确→撤销、改两次/撤销更正、重复请求、后续已有新作答、同KC其他题不受误伤、改绑后重放、纠错中被杀、旧键迟到、旧包恢复。证明增量触发后的结果与从头重放一致；不能只断言多了一行。

退出门：KD-43的最低可用纠错链在5a/b新增真实证据入口启用前完成；5B只扩展到整卷/改编引用，不再承担首次纠错实现。数值/证据语义变更要更新相关算法版本与对照，不偷偷沿用旧指纹。

### PRD-03：修改已保存原题与revision失效

位置：`L/MistakeDetailRoute.kt`、`K/MistakeDetailRepository.kt`、`D/mistake/RoomMistakeDetailRepository.kt`、`D/capture/RoomCaptureWorkflowRepository.kt`、`DB/entity/ProblemEntities.kt`。

- [ ] 从详情/复习提供“修改这道题”，复用已有输入/公式展示及revision持久模型，增加受控的修改命令与CAS。保存前后显示必要差异；不要求每次录入都进编辑页。
- [ ] 留住原图、旧revision、来源与历史作答。编辑题干、选项、答案和图的动作分别标明影响；同题排版/备注更改不清空历史。
- [ ] 语义改变使旧basis未完成模型任务、生图、判分结果不能附着到新版；旧图/解析标旧版，新目标需图时走WG-06共享生成。另存新题时使用新身份。
- [ ] 新revision供下一次复习/导出；正在进行的旧题会话检测版本冲突，保留草稿并提示重新载入，不暗中把旧答案交给新题。修正已有错误评分调用PRD-02。

验收：编辑→杀进程→重开→复习→导出版本一致；A/B并发编辑只接受正确expectedRevision；旧生成晚到不污染新题；历史卷与历史作答仍可读。扩展既有 `MistakeRelationAndRevisionInstrumentedTest`，新编辑完整链另加定向测试。

### PRD-04：原生PDF/DOCX格式选择与可靠交付

位置：`APP/SmartMistakeBookRoot.kt`、`APP/SmartMistakeBookApplication.kt`、`T/TutorExportSheet.kt`、`T/TutorPendingRequestCommands.kt`；复用现有 `core:export` 渲染器和成果页。

- [ ] 把sheet的通用展示/状态抽到双方可访问的原生组件；原生单题/多选/筛选导出可直接选择格式和布局。模型建议只预填同一draft，候选仍须本轮NOTEBOOK_READ证明；宿主原生选择用另一种合法来源，不伪造模型回执。
- [ ] 固定导出题版本、可见字段、图引用与布局为不可变输入；格式纳入任务/缓存身份，旧任务缺format仍按既有PDF兼容，坏值拒绝。
- [ ] 沿现有WorkManager、SAF保存、URI授权分享/打印；用户取消不算失败或成功交付，目标应用不存在可返回保存入口，不因分享失败重新生成卷。
- [ ] 复用当前DocxImageSource的同字节长度/hash检查，并核验所有调用点传入冻结资产元数据；按资产上限及expectedByteSize有界读取，不能只靠stat预检后无界readBytes。必需题图变化/缺失须显式失败并保留上一成果，不能沿null降级静默漏图。只有明确非必需的装饰图可省略并说明。默认不含答案，答案附件与题目区角色分别验；KD-55按实际端到端证据关闭。

验收：无Key/断网/401/429下，两种格式均能从原生单题和多选入口交付；模型派发数为0。验证中文/公式/多图/分页、ZIP内实际media/hash、保存取消、分享后返回、进程中断恢复。仅弹出系统分享面板不等于接收端已拿到文件；至少一次用真实接收应用打开保存文件。

### PRD-05：本地三键与撤销先做成完整闭环

位置：`R/CapturedReviewSessionScreen.kt`、`R/ReviewRoute.kt`、`M/LearningState.kt`、`D/study/StudyReviewPlannerService.kt`及既有提交/会话推进事务。

- [ ] 先刻画旧四键潜伏合同与P6/P7基址，恢复已裁定Good/Hard/Again，写SELF_REPORTED，原档/折扣分列；不把它写成LOCAL_CHECKED或MODEL_JUDGED。
- [ ] 已保存题直接作答、自评、看已有解析、继续下一题；AI讲题/判词作为可选路径。保留既有新引入预算，跳过不当错答，不凭浏览/图形互动记掌握。
- [ ] 提交与会话推进使用稳定submission身份及持久回执；先答题后正常展开不罚，主动提前看答案沿既有reveal；无可信答案如实说明，不能伪称已客观核验。
- [ ] undo复用PRD-02撤销/替代与重放，并恢复正确会话位置；不能靠直接倒减统计。已看到答案的曝光仍保留，撤销后重评不恢复成独立未辅助作答。
- [ ] 独立测试库验证后才申请能力启用判定；真实用户数据写入仍须3D完整门+PRD-02/03/05，5a不额外依赖3E，5b仍需3E。无临时QUESTION_ONLY事件、无“空归属=不影响KC”捷径。

验收：三档区别、双击、旋转、提交中断、undo→重评、跨日/同日、题版本变更；无模型时仍能完成本地流程。逐条对拍全重放与投影/日程；测试不能在用户学习库制造记录。

### PRD-06：首装空态、手动题与真实能力声明

位置：`R/ReviewRoute.kt`、`APP/SmartMistakeBookRoot.kt`、`C/components/CaptureScreenComponents.kt`、`C/CaptureScreen.kt`、`D/model/OpenAiModelProtocol.kt`、`D/model/OpenAiModelTaskAdapters.kt`、`K/TutorLocalActionExecution.kt`、`T/TutorPendingRequestCard.kt`。

- [ ] 无题空态给录入CTA；有题无到期分开展示；失败待处理任务保留继续入口，不要求重新拍照。
- [ ] 复用已有startManualDraft与采集页入口，核验KD-52无源手动题创建、读取、提交、草稿恢复全链，来源USER_TRANSCRIPTION；真实无图不伪造canonical源文件。首装CTA需到达可用的LIBRARY手动入口，不能误入仍要求照片的讲题入口；不能把按钮或照片手动净化模式当A8完成。
- [ ] 从已实现处理器、配置/依赖就绪和本轮权限计算一份可调用能力；native schema、Route B说明、提示词与执行验证同源。ADD_TO_REVIEW_PLAN接线前不广告，旧卡可读拒绝，不声称加入成功。
- [ ] 权限三档按TutorPermissionPolicy对齐，AUTO_VISIBLE仍经过MasteryWriteGate，不额外新增确认。切分自动化仍受AUD-08门，不借手动入口绕过切残红线。

验收：首次安装/空库/仅历史题/有待处理任务四态；手动题created=true且来源正确；未知/未启用工具不能执行；两协议在相同能力快照下声明与执行一致。

### PRD-07：先修自建备份与并发写，再做内容勾选

位置：`DB/RoomBackupSupportStore.kt`、`D/backup/AndroidBackupRepository.kt`、`D/backup/SmbkArchiveCodec.kt`；关联KD-53/54/47、AUD-05、6C-2。

- [ ] 复用SmbkResourceLimits已分开的数据库1GiB/其他单条100MiB/累计2GiB预算，核验创建、预检、恢复同口径及磁盘空间预检；不能仅在解包端提高上限而让创建端产出不可恢复包。流式解压与hash，异常巨大条目/伪长度/数量超限仍拒绝。
- [ ] 首先用365MB及更大实际形态夹具量测独立快照连接：不占Room长写租约；与关闭/恢复协调；验证并发保存、WAL增长、取消和临时文件回收。连接选项和SQLite版本按实际驱动证据确定。
- [ ] 旧SQLite fallback加真正全写者屏障，检查checkpoint结果再复制；只允许有界等待/复制窗口，超时明确未生成有效备份。错误分类后再决定兼容回退，I/O/损坏/取消不得吞掉后无锁copy。
- [ ] 若独立连接/旧API路径不能满足性能和一致性门，提交有界的增量Backup API探针及驱动兼容证据后再替换；不假设Room已有该API，不先为备份换全库驱动。此项不过门则KD-54保持未完成。
- [ ] 快照开始前取得与资产GC删除互斥的备份保护，快照完成枚举引用并建立定向pin后才解除全局GC保护，复制/hash结束释放pin；复用已有保护机制，普通新增写入可继续。不得留下“快照已取、引用尚未保护”窗口；取消/超时也回收租约。先验证KD-53/54再实施6C-2；设置部分恢复如实显示，原DB交换失败可回滚。

验收：≥365MB自建包创建/校验/恢复；并发录入/生图/GC，缺资产门真实触发；低磁盘、错误长度、坏hash、杀进程、不同API SQLite版本。性能目标先冻结测量设备与负载：正常存档写p95相对无备份基线不超过2倍且无单次>5秒的备份占锁等待；不达标时保留未通过并报告，不以“无ANR”替代。目标是本轮工程标准，尚未实测。

### PRD-08：草稿恢复先复现、再做小修

位置：`T/TutorConversationViewModel.kt`、`T/TutorConversationSavedState.kt`及既有Room草稿持久化端口。

- [ ] 注入Room旧读延迟、写失败、先后写乱序，验证SavedState较新输入是否被覆盖；分别测旋转、系统回收、强停/重启，不能把三者当同一种恢复。
- [ ] 仅反例成立才加本地编辑序列/持久版本和串行CAS写；晚到旧读不覆盖新输入，提交成功清稿匹配版本。失败保留未保存状态/可重试输入，不用runCatching静默当成功。
- [ ] 保留已有采集侧防抖/后台flush与会话草稿实现，不重建通用离线引擎；文案不承诺所有未落盘按键在强杀后必恢复。

验收：新输入、旧读取、提交清稿并发顺序的确定性测试；真实进程重建不外发。反例不成立则留证关闭疑虑，不做无收益修改。

### PRD-09：统一生图的图义质量、提示词与开放条件

位置与工程任务沿[4E计划](2026-10-18-flexible-js-drawing-implementation.md)的WG-01～10；策略正文只维护[统一提示词](../../current/figure-generation-prompt-policy.md)。先映射并复用已有FigureGenerationPolicy/DefaultFigureGenerationCoordinator/派发账本接线，再补缺；当前只执行IMAGE_MODEL不等于双方法已完成。不新增第二个选择器/图像生成器。

- [ ] 在共享规则补源事实、真实方向/单位/标注、图题一致及未证实不补造；只有实际支持的方法/交互广告。模型允许例外选法与预算内换法，后端只执行验证过的proposal。
- [ ] 将六张示例落实为可核查题意夹具；原生问答表单另验。图下说明不能补救方向错误，TeX原文渲染失败单列；结构PASS不直接升级图义验证状态。
- [ ] 冻结120个盲测任务，开发集另设不混入分母，保存独立事实预期与评审来源。只用模型审评时明确证据限制，分歧未决不能计合格；解析可计算的图用独立预期核验，不能只读模型自己输出的断言。用盲测错例改prompt后须另换未见集作启用判定，旧集只作回归。
- [ ] 依设计§6执行首次≥90%/预算内≥95%/分组≥90%、关键错误接受=0门，记录比例/分母/区间。明确要求的图降级为文字或NO_FIGURE不算成功，失败样例不能删除；两后端配对/切换另验而不强制所有题都跑两遍。
- [ ] 固定模型/协议/设备/网络，记录首图p50/p95、总时长、token/费用、修复与切换原因；JS路径p95≤30秒、IMAGE_MODEL路径≤120秒为首批目标。运行前形成有上限的评测预算清单；未知费用记未知，六派发总门不变。
- [ ] 最先验静态图→持久化→重启→详情→PDF/DOCX，然后完成所有WG入口；过门后广告静态能力，再扩4E.2交互/4E.3保真。保存/历史/导出/滑块回放0模型派发；失败保留原产物。

> **（2026-10-25 批次 20：PRD-09 harness 可先行半边已落树，勾选框不动——真实质量门未过）**：120 盲测任务（6 组×20）+24 开发集已冻结为 SHA-256 manifest 防篡改夹具（`tools/figure_eval/`，review_source 必填、事实预期 288 条其中 critical 209、开发集不计入 120 分母）；评测预算清单落盘（费用未知记未知、六派发总门引用契约:138+ModelTasks.kt:61）；两后端配对/切换框架 + 指标字段（首图 p50/p95/总时长/token/费用）就位；fake 驱动端到端报告在盘（JSON+MD），18 项机制测试实跑全绿（pytest 18 passed）；JS 后端留桩（NOT_WIRED）待 T4 VisualExecutionEngine 端口接线。**真实 Provider 质量/费用 UNVERIFIED（无 key）**；盲测 120 例未跑；门常量是启用目标非已达。见 `docs/research/2026-10-25-batch-20-record.md` §3.5。

退出门：工程、语义、可读性、恢复和真实质量分别有证据。手机截图/API34探针、手写程序不能代替真实Provider成功率。上述百分比/时延是待实测的启用目标，不得写成已达到。

### PRD-10：后续阶段承接，避免再次断线

- [ ] 5b消费PRD-02的键来源/纠错与会话恢复，保留3E新引入/配额，验证批量导入积压；5c复用版本与实际能力，备注隐私和自动化许可照原裁定。
- [ ] 5B把答卷/改编/出卷版本及学生原始证据映射到PRD-02，重复回收不重复计分。文档副本改题不能修改原题答案；新版题图仍走WG-09，不把生成清图当学生答案。（2026-10-24 批次19：回收**域层半边**已落树——答卷身份幂等键（重传不重复计分、不同卷/不同日不撞键）、出卷答案键链模型、客观题本地判（键不可信→待核验、不写数值掌握）、LOCAL_CHECKED 回灌端口；"生成图/清图不冒充答卷"在域层锁死（仅 LocalChecked 档可提交证据）。**未完成**：导出侧键链接线（`MistakeExportRecord` 无 answerKey 字段族）、UI 入口、真实落库仓储、判词通道实现（只留接口）；37 用例无 Gradle 执行证据（单测门 exit=1）。不勾。见 `docs/research/2026-10-23-batch-19-record.md` §2.6）
- [ ] 6B-2先核对并复用在飞结构化编辑代码，完整交付验收衔接PRD-04原生格式入口；无需暂停独立开发，也不能重建已有模型。6B-3自然语言操作白名单和图引用共用该模型，保留失败前文档，不做DOCX往返解析。
- [ ] 6C-2加入新键/纠错/有效证据/版本/共享图/账本引用闭包；删会话保题或保学习记录时保其必要依赖。实际schema落地后重算机器清单，不更改旧schema66附录伪装已验。（2026-10-24 批次19：收口尾三项已落——设计件三裁定+机器数字回写（§9.9/§9.11，openMasks={0,8} 契约不变）、T2 六项加固（写侧拒负 rowCounts/assetPolicy 同口径/deleteAllData 清标记/恢复交换前 S3 软引用重查/I5 SQL 单源化/子集文案实示）、D1 换机演练设备门全过（mask 15/9/13+损坏包停交换前+真实杀进程两阶段）+备份回归 56/56。**本条的扩闭包本身未做**；工程门未过未提交；KD-63 新登记。不勾。见批次 19 记录 §2.2-§2.3）
- [ ] 6学情保持只读和四科支持说明；普通雷达/周报/进度用真实数据原生画，不触发生图模型。所有历史打开与导出不得重新计费。

### PRD-11：用学生任务验收，分开证据层级

- [ ] 本地任务线：首次空态→手动题→保存→修改→再次打开→原生PDF/DOCX→保存/分享→备份→换机。网络请求观测=0（除用户另行主动请求模型外）。（2026-10-24 批次19 设备实跑（API34 模拟器，localFirst）：首装空态 1/1、手动建题→保存→错题本重开可见+model_task=0 1/1、原生 DOCX 真渲染/MIME/MediaStore 1/1、PDF 保存/分享/打印 2/1、备份→清数据（参数闸）→换机恢复 D1 6/0；**网络观测=0 已证**（netstats 无 uid 行+/proc tcp socket 0 条+model_task=0）。**「修改」步骤未覆盖**；全链 OK (5 tests)。不勾，待补修改步骤。见批次 19 记录 §2.8）
- [ ] 学习任务线：有数据/已过门→三键→答案→撤销→重评→次日再练；错答案键→纠正→重放→日程与KC/统计一致。保留独立作答与曝光因果。
- [ ] 模型任务线：真实拍照转写/讲题/两方法生图→保存→出卷→回收；源版本变动、拒绝、401/429、取消和进程恢复逐条验；未到达的步骤记UNVERIFIED。（无凭据，UNVERIFIED 维持——批次 19 未动）
- [ ] 设备门覆盖API23、arm64、16KB、R8和现有双flavor；对应页面TalkBack/大字/48dp、真实存储规模/性能，最后做完整Lint/测试/Release及迁移链重置后旧包恢复。（2026-10-24 批次19 可行矩阵如实：宿主仅 android-34 镜像、abilist=x86_64,arm64-v8a（arm64 仅 nativebridge 翻译非真 arm64）、PAGESIZE=4096 → **API23/arm64/16KB 三项不可行+理由**，不伪称覆盖；flavor 四选一仅 localFirst 装/启/走查/卸全链已验，strictOffline/benchmark/internal 未跑；R8/TalkBack/大字/48dp/完整 Lint 未做。不勾）
- [ ] X只使用经过有效证据筛选的合格长程样本；撤销/未核验键/模型判词/曝光样本按实际契约排除，保留排除原因，不用总记录数凑400。

## 3. 命令与报告纪律

下面是**实施后待运行命令**，本轮文档修订未运行这些产品测试。开工时先核Gradle已注册任务与对应变体，新增测试名不得写成已经存在/通过。

```powershell
.\gradlew.bat :core:model:test :core:domain:test :core:data:testDebugUnitTest
.\gradlew.bat :feature:review:testDebugUnitTest :feature:library:testDebugUnitTest :feature:tutor:testDebugUnitTest :core:export:testDebugUnitTest
.\gradlew.bat :core:database:connectedDebugAndroidTest :core:data:connectedDebugAndroidTest
.\gradlew.bat :app:assembleLocalFirstRelease :app:assembleStrictOfflineRelease
```

每批只跑相关定向用例与必需门，相关变更稳定后再做最终全量门；不把全量重复运行当研究。实际设备入口、真实Provider、低端性能与迁移恢复需另列run/样本/结果。文档校验验证计划、链接、依赖及历史保留，并记录共享工作树的源码变化；本轮写入范围限文档/研究artifact。其他在飞源码只做已列核验点的静态复核，不能据文档检查通过声称产品通过。

## 4. 本计划完成定义

一项实现只有对应事实、投影/恢复、入口与失败路径证据都闭合才可勾选。实际验收失败回到原任务修，不能降低阈值、移除失败样本、把任务改名算完成。下一批首选PRD-02/03/07的契约与复现，PRD-04/06可按独立文件边界随后推进；4E继续原WG路线，不抢先广告JS能力。

## 5. UI改版承接（2026-10-19补充）

[UI计划](2026-10-19-ui-ux-redesign-plan.md)把现有不合理的操作路径、信息密度和视觉层级纳入正式工作；[设计](../specs/2026-10-19-ui-ux-redesign-design.md)规定每页主动作、内容顺序、返回、空/错/恢复态与适配。不是另加一套后端，也不是只作美化。

| 原任务 | UI责任 | 必须一起验收 |
|---|---|---|
| PRD-01/11 | UI-00/01/02/11 | 真实基线、四底部入口/五功能分区、设计令牌、任务与设备证据 |
| PRD-02/03 | UI-05/06 | 原题修改、键纠正、撤销评分与文档副本分别可辨，回执状态不误报 |
| PRD-04 | UI-03/09 | 列表选择→原生格式/布局→生成→保存/分享，无模型可完成 |
| PRD-05 | UI-06 | 卡片优先、三键自评、答案曝光、撤销与原位求助；真实启用门不变 |
| PRD-06 | UI-02/03/04/07 | 首装下一步、列表优先、紧凑录入、实际可用能力与诚实文案 |
| PRD-07/08 | UI-04/07/10 | 继续任务、草稿保留、空间/版本/部分恢复反馈，而非通用重试 |
| PRD-09/10 | UI-08/09/10 | 公式/双方法图/表单/导出/只读档案同源呈现，不强制用户选后端 |

布局和无模型入口可先行；不把全部UI推迟到3D或JS完成。涉及新事实写入/生成式互动/恢复的可用状态仍按原门，不提前放假按钮。执行时复用现有core:ui、richtext、图像和导出组件；模块共享文件与正在进行的PRD/WG任务单写者协调。

## 6. 2026-10-20 现有UI逻辑修复承接

按[UL-01～09审查](../../research/2026-10-20-existing-ui-logic-audit.md)与[UI计划第5节](2026-10-19-ui-ux-redesign-plan.md)执行：PRD-03承接编辑器重建/完成/在途返回，PRD-04承接答案模板有效状态与编辑稿读取身份，PRD-08复用竞态测试方法处理附件旧读；列表读取失败与现有归档/恢复回执沿UI-03/05维护，不新建PRD功能。

本批只修既有逻辑漏洞，范围不扩到新页面、业务规则、隐式自动保存、额外确认或新模型调用。原题编辑和原生PDF/DOCX选择已有在飞源码，更新为复用与核验；不据此关闭PRD-03/04的完整退出门。本轮无产品修复与设备验收，任务仍待执行。

## 7. 现有操作可用性细化（2026-10-20）

[UF-01～12](../../research/2026-10-20-ui-usability-friction-audit.md)与[UI计划第6节](2026-10-19-ui-ux-redesign-plan.md)细化既有入口：PRD-03/04接编辑字段核对、导出参数/目标文件反馈；PRD-06接选题搜索、批量取消；PRD-07接数据库占用与操作分组；配置/排程/只读档案和长页返回沿原UI维护。只调整表达、分组、布局和现有字段输入，不添加新业务或领域规则。

本地改进可先行，相关UL竞态/保存保护先过门；不能重新开发已经存在的原生编辑/格式选择，也不能把本轮界面观察记作所有页面或当前HEAD验收通过。

## 8. 编辑、续做和图片消费的一致性（2026-10-20）

[UF-13～19](../../research/2026-10-20-ui-flow-followup-audit.md)按[UI计划第7节](2026-10-19-ui-ux-redesign-plan.md)落入原任务：PRD-06承接题面编辑中间态与拆分复核的状态/数量/真实动作；PRD-08承接明确jobId不漂移、缺草稿不假完成及现有恢复；PRD-09沿UI-08/11承接图片身份切换和本地解码失败，复用共享组件。既有UL/UF不重复记完成量。

同文件先修对象/可执行条件/收尾，再改表达；输入缓冲不放宽正式内容校验，不增加保存步骤或自动持久化。图片消费与生成分开，JS/IMAGE_MODEL用同一产物合同，查看/展开不派发。七项待实施和设备验证，不新增PRD功能、业务规则或发布豁免。
