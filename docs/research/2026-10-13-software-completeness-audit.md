# 智能错题本全软件完成度审计（2026-10-13）

> **2026-10-18 绘图路线更新：** 所有生图入口统一判定，由模型按共享规则选择JS或图像模型；两者均为生图方式，正式统一接线待实施。当前约束见[教学图形契约](../current/visual-generation-contract.md)。本文既有日期、提交、测试、缺陷与旧方案作为历史事实保留，不代表新路线已实现。

**结论：工程底座和学习内核已经很充实，软件处于“可信数据与跨流程闭环收口”阶段。尚未达到只剩界面打磨、文档整理即可完成的程度。** 最紧迫的工作是把录入、作答、证据、恢复、导出、备份之间的身份和状态接齐，再完成真实模型质量验收。

本报告是一次审计快照，不替代 [MASTER LINE](<D:/smart mistake book/docs/REFACTOR-MASTER-LINE.md>)，也不修改既有产品裁定。审计交付完成与软件发布门通过是两件事。

## 1. 范围与证据边界

- 基线：当前工作区，HEAD `00bdaced`。审计开始时已有 4 个受版本控制文件的未提交修改；按工作区现状检查，未改动这些文件。
- 覆盖：13 个 Gradle 模块；应用模块主源码（不含 benchmark）统计 538 个 Kotlin 文件、133,534 行。按产品流程、学习与模型内核、知识库三路追踪生产装配和调用，对照当前合同、总路线、10 月 11 日需求定稿及最新批报。
- 证据等级：**本轮执行**为当前命令和产物；**源码确认**为追完生产控制流；**历史实测**为仓库已有记录；**待复现**为尚未运行设备场景的风险。没有把静态分析说成真机复现，也没有逐行穷尽每个文件。
- 本轮没有配置真实模型、发送真实题图、重跑设备仪器化或签名 Release 验收。真实供应商教学质量、全量升级恢复、低版本 Android 运行和真机性能仍有验证边界。
- 本次新增审计报告与 `.artifacts/software-audit-2026-10-13/` 证据；未修改软件实现。

## 2. 软件是什么，以及已经做到哪一步

这是一套本机保存学习事实、外部模型处理题面和讲解、算法维护掌握状态与复习节奏的 Android 学习应用。产品主链是：

```text
照片/PDF → 规范原图与草稿 → 切题/结构化题面 → 错题本
                                               ↓
讲题/工具动作 ← 知识库与教学材料 ← 知识点归类/绑定
       ↓                                       ↓
作答与学习证据 → 账本/投影 → 掌握状态与FSRS → 复习队列
       ↓                                       ↓
备份恢复 ← 数据库与图资产             PDF练习卷/纸质重做
```

`core:model/domain` 定义事实、契约和算法；`core:database/data` 负责 Room、投影、模型调用、资产和知识包；各 `feature:*` 提供录入、讲题、错题库、复习、个人设置；`core:export` 处理 PDF；`app` 装配导航和后台任务；`benchmark` 提供性能测量框架。Room 是学习事实源，模型不直接写数据库，模型工具通过本地授权和写入门执行。

| 领域 | 实际成熟度 | 不能由此推导的完成结论 |
|---|---|---|
| 工程底座 | 多模块、双 flavor、Room schema 1–65、迁移与测试基础、规范资产管线、CI 已建立 | 当前完整门禁仍未全绿；历史迁移测试存在不等于本轮设备矩阵已跑 |
| 录入与错题本 | 相机/相册、整卷 PDF、草稿、题面修订、历史追溯、SQL 分页/筛选、归档恢复均有生产装配 | 整卷多题确认还没有正确完成全部入库；题级去重与新增入口仍有后续项 |
| 智能体与讲题 | 多协议、附件、工具环、派发预算、知识引用和本地写入门实际接线 | 真实跨科讲解质量、题目判定和图形保真还没有完整验收 |
| 学习内核 | β-二项掌握表示、记忆卡、FSRS/V2 排程、证据投影、改绑重放、校准采纳门均是生产能力 | 小测重复提交及进程恢复仍有缺陷；真实数值可信度还依赖数据校准 |
| 知识库 | 四科成品与 59,008 条材料实际消费，版本/来源/绑定/构建工具齐备 | 包声明 PARTIAL，候选未晋升、内容覆盖与最终召回未达成完整收口 |
| 生图与导出 | images edits 已接保存链，PDF 参数化排版、后台成果、保存/打开/分享已经存在 | 净化覆盖与故障续跑不足；docx/改卷及纸质答卷回流未完成 |
| 学情与设置 | 已有学习掌握页、保留率、考试日历、提醒、备份恢复入口 | 全知识库画像、点薄弱点直接复习、完整考纲冲刺、含设置的个性化备份仍欠缺 |

按总路线，0/1/2、3A/3B、读侧闭合、4A/4B 已有完成记录；3C/3D 和薄纵向切片仍在收口；3E、4C、4D、阶段 5/5B、6/6B/6C/7/X 尚有大量计划项。阶段完成标签主要表示原阶段约定的工程范围，不表示后续扩展需求或真实产品质量已经完成。

特别注意：最新批次 9 报告的绑定审计点估计已是 **95.15%，报告按点估计判过门**，不能继续拿旧 89.4% 当当前数字。但这仍不能把整个 3D 判绿：候选内容门、最终检索、晋升配套和其他子门还没有一起收口。

## 3. 看似做完、实际存在断点的核心问题

### A1 · P1 · 不同小测复用同一提交编号，后续作答会撞账本键

每次模型可生成新题，但 [KnowledgeReviewQuizLoader.kt:92](<D:/smart mistake book/feature/review/src/main/kotlin/com/tingyun/smartmistakebook/feature/review/KnowledgeReviewQuizLoader.kt:92>) 固定以知识点生成 `itemId`；[KnowledgeReviewSessionScreen.kt:260](<D:/smart mistake book/feature/review/src/main/kotlin/com/tingyun/smartmistakebook/feature/review/KnowledgeReviewSessionScreen.kt:260>) 又只用题 ID 与选项构造提交 ID。因此同一知识点隔日再次选择 B，仍生成第一次的证据 ID。

[ChatEvidenceDao.kt:68](<D:/smart mistake book/core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/dao/ChatEvidenceDao.kt:68>) 为 accepted 行生成同一 outbox ID；证据插入 `IGNORE`，outbox 插入 `ABORT`。冷却结束、第二次写入获准时会发生冲突，重试又沿用同键。**幂等规则把不同业务操作误认成同一次操作。** 本轮源码等价 SQLite 场景复演了唯一键冲突，见 [输出](<D:/smart mistake book/.artifacts/software-audit-2026-10-13/knowledge-quiz-idempotency-sqlite-output.txt>)；尚未在真实 Room/设备上复现。

验收应覆盖：同一次提交重试只写一笔；同知识点不同日、不同生成题、相同选项可以分别写入。现有 namespace 稳定性单测没有覆盖后一个条件。

### A2 · P1 · 小测恢复时换了题，却保留旧题答案选择

[KnowledgeReviewSessionViewModel.kt:71](<D:/smart mistake book/feature/review/src/main/kotlin/com/tingyun/smartmistakebook/feature/review/KnowledgeReviewSessionViewModel.kt:71>) 的题目只在内存，选择则进入 SavedState；恢复后将加载状态重置为 IDLE。界面自动取题，而 [KnowledgeReviewQuizLoader.kt:72](<D:/smart mistake book/feature/review/src/main/kotlin/com/tingyun/smartmistakebook/feature/review/KnowledgeReviewQuizLoader.kt:72>) 新建 UUID 并重新执行模型任务，没有观察原题任务。

**触发场景：原题选 B、未提交，进程重建且 SavedState 恢复；界面自动发起新的出题任务。若生成了不同题面且仍有 B 选项，新题仍预选 B，可能把旧题选择记成新题作答。** 即使题面碰巧相同，也产生新的模型调用。现恢复单测只验证重新打开加载路径，没有同时验证题目、选择、请求身份和出网次数。证据为源码链，尚未设备复现。

### A3 · P1 · 整卷切题提前把全部题标成“已录入”，实际只推进首题

[SplitImportReviewRoute.kt:192](<D:/smart mistake book/feature/library/src/main/kotlin/com/tingyun/smartmistakebook/feature/library/SplitImportReviewRoute.kt:192>) 对所选、仍待确认且有草稿 ID 的题先写 `SAVED`，随后只打开第一个草稿。页面把非 PENDING 显示为“已录入”，但账本更新没有提交错题。[SmartMistakeBookRoot.kt:729](<D:/smart mistake book/app/src/main/kotlin/com/tingyun/smartmistakebook/SmartMistakeBookRoot.kt:729>) 保存首题后跳详情，没有继续下一草稿。

再次点击“录入所选”时，待确认列表已空，可直接完成批次。**其余题仍为草稿，未即刻丢失，但批量入库承诺与状态失真。** 现设备用例覆盖入口/缩略图/选批，不覆盖多题真实提交、中途退出、返回继续。证据为源码完整导航链。

### A4 · P1 · dense 已装配，却被讲题主入口后续词面排序覆盖

[RoomTutorKnowledgeContextLoader.kt:67](<D:/smart mistake book/core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/RoomTutorKnowledgeContextLoader.kt:67>) 将题面交给带 dense 的候选读取，随后 [KnowledgeContextRetriever.kt:58](<D:/smart mistake book/core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/KnowledgeContextRetriever.kt:58>) 又对同一集合按自身词面分完整排序；同分按名称/ID，词面分为零的节点会被过滤。

词面 SQL 已先截取候选，dense 重排还强制保持 ID 集合与数量不变，没有从更大池重排后再截取。因此，在当前固定召回集合内，dense 的排序被后续步骤覆盖，最终最多 8 个候选不受它的排列影响。dense 也不能补回没有进入候选集的节点。**测 store 的向量排序通过，不能证明最终提示词受益。** 应把验收点移到 loader 最终注入的知识点与材料，并覆盖无同词的同义问法。证据为源码链。

### A5 · P1 · 备份可通过校验，却缺数据库引用的新图片

[AndroidBackupRepository.kt:103](<D:/smart mistake book/core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/backup/AndroidBackupRepository.kt:103>) 先枚举资产，再在第 113 行创建数据库快照；普通写入没有参与跨数据库与资产的一致性保护。两步之间如果新图落盘并提交引用，数据库快照会包含它，ZIP 资产列表却没有它。

ZIP 校验只核 manifest 文件列表、长度和哈希；数据库校验只核 SQLite/外键/Room，不核“DB 引用的文件都在包中”。所以两边分别完整，整体仍可能不完整。源码允许该交错；本轮 [内存模型输出](<D:/smart mistake book/.artifacts/software-audit-2026-10-13/backup-image-snapshot-interleaving-model-output.txt>) 得到 `quick_check=ok`、外键检查为空而新图缺失。**这是静态证明的竞态风险，尚未观察到实际换机损失。** 应补并发录入/生图时备份，以及解包后的 DB→文件完整性检查。

### A6 · P1 · 心得或净化图变化后，再导出可命中旧 PDF

[MistakePdfEligibility.kt:152](<D:/smart mistake book/core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfEligibility.kt:152>) 把心得与净化图 URI 写入渲染输入，但导出指纹只包括身份、题面指纹与版式；[MistakePdfExporter.kt:120](<D:/smart mistake book/core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfExporter.kt:120>) 同指纹直接返回缓存。

改心得不会创建新题面修订，提交后异步附净化图也不改变题面指纹。**在版式使用对应内容时，先导出、再改心得或等待净化图、再导出，可能仍拿到旧内容。** 现测试证明字段映射和版式改 key，没有证明所有渲染输入变化都会失效。证据为源码链，尚未设备复现。

## 4. 重大能力缺口与已知边界

| 缺口 | 当前事实与后果 | 计划归属 / 完成条件 |
|---|---|---|
| 图像净化覆盖、可靠性、保真 | 只在 `WITH_FIGURE` 时净化；纯文字题不会生成净化图，原图里的手写内容仍保留，不等于结构化文字导出必带手写。提交后 application scope 作业没有持久失败/续跑；杀进程或 edits 失败后不自动补做。可解码不等于图题等价 | R1 / 4D：分开验证题干、公式、图形标注；故障后能续做；明确哪些原题会净化 |
| 切分红线没有质量金标门 | usable split 只检查 2–12 矩形和正宽高，不能验证题干/选项/图是否切残 | R3 / 4C：跨科、跨页、含图/公式/手写样本；完整题边界验收及不合格出口 |
| 四科知识覆盖不完整 | 成品 3,741 点、59,008 材料，但声明 PARTIAL；2026-09-18 AI 审校台账登记 352 COVERED / 73 PARTIAL / 236 NOT_COVERED，分母 661。它是 2025 课标内容要求条目覆盖，不能当模型准确率，尚未随最新 staging 并点更新 | 3D/R10：课程覆盖与材料数量分开验；未覆盖范围明确呈现 |
| 候选与应用成品尚未闭合 | staging 为 3,356 点、912 retired；成品为 3,741 点、527 retired。候选 14 空材料点、覆盖映射 22 个目标已不存在；最新并点未晋升到 App | D-5：候选全门绿，映射消费迁移，contentVersion 与 dense 资产一起发布 |
| 前置内容稀疏 | 成品 401 边，仅 624/3,741 点参与任一边，83.32% 孤立；不能把前置算法存在当成普遍可用的补救能力，也不能假定每点都必须有前置 | R10 / D-4：按应有前置的知识点验覆盖和正确性，不以盲目加边数量验收 |
| 学习可信度边界 | 模型生成题干和正确答案键，本地比 ID 后按客观证据处理，未独立校验答案键；12h 同 KC 冷却又把模型判断和新测验作答一起限制 | R4/5、Wave X：题目/答案键抽验，纠错撤销与污染重放，按证据来源验证权重和冷却 |
| 复习与弱点到行动未兑现完整终态 | 全库弱点扫描、学生三键判定和撤销尚是阶段任务；现学情页只展示已有状态，未覆盖全库盲区，行不可点进直接复习 | 3E、5、6 / R4/R5/R8：扫描范围、预算、复习入口、反馈和撤销一整圈 |
| 离线复习限制 | 无教学工件的生产错题走讲题判定；无模型时无提交或零权重跳过，首题可阻住整场错题复习。源码明确登记为知情裁定 | 不作为隐蔽 bug；需明确 strictOffline/未配模型的可用范围，后续裁定才能改变 |
| 考前与自适应练习 | 考试日历已有，但考纲范围、爬升扩容、自适应选择器到出题工具，以及纸质答卷回收判定回灌仍未完整接齐 | 5B / R7：日期→范围→选择→出卷→真实作答→证据回流 |
| 可编辑文档 | PDF 保存/打开/系统分享已做；docx、App 内题块编辑、自然语言改卷没有完成 | 6B / R7：以文档模型、双渲染器和可审计编辑动作验收 |
| 个性化换机备份 | 现 `.smbk` 数据库+规范图片包和恢复入口已存在；选项强制含图片。DataStore 中的保持率、考试日历及提醒等设置未打包 | 6C / R9：可选内容、含设置换机往返、跨迁移链恢复、保密字段剔除 |
| 研究补库闭环 | grounding 缺口可落库，但研究 coordinator 无生产驱动，搜索 gateway 只有接口、没有生产实现，审校与发布链未装配，仍依赖开发期工具和重新打包 | 明确本地缺口→受控研究→审校→发布→缺口关闭的责任与终态，不以有接口当完成 |

图像覆盖与后台作业证据见 [RoomCaptureWorkflowRepository.kt:475](<D:/smart mistake book/core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/capture/RoomCaptureWorkflowRepository.kt:475>)；全库画像/行动缺口见 [LearningMasteryScreen.kt:59](<D:/smart mistake book/app/src/main/kotlin/com/tingyun/smartmistakebook/LearningMasteryScreen.kt:59>)；模型答案键与写入语义见 [KnowledgeQuizFeedbackWriter.kt:46](<D:/smart mistake book/core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/KnowledgeQuizFeedbackWriter.kt:46>)。

另有需要复现的语义风险：[TutorJudgedReviewSettler.kt:242](<D:/smart mistake book/core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/TutorJudgedReviewSettler.kt:242>) 优先使用最新有作答 cycle 的本地选择核对；结算当前队首项时，也要求判词时间不早于该复习项的 `lastActiveAt`。但在没有本地选项判定时，会把本会话最新 accepted 知识点证据转整题正误，未进一步限定为题绑定节点或当前教学 cycle。前置/工具发现 KC 是合法证据目标，仍需测试“本次复习期间前置懂了，但整题未做对”的多 KC 场景。尚未设备复现。

## 5. 计划本身还不完善的地方

**5.1 权威和状态文档已漂移，下一轮有重复开发风险。** `docs/current` 仍称 Lobby 只支持文本、模型只能建议掌握更新，而当前附件和门控工具写入已经落地；旧意图 spec 写无 tools/三次派发，实际是工具环/六次。需求表仍称 PDF 导入、备份入口、保持率/考试日历、学情页或系统分享不存在。它们已在生产，缺的是扩展范围和验收。阶段 5 行仍写 D-0 未做，其他行已记完成；批次 9 D-1 点估计过门也还未统一回总线。应先更新“已存在 / 部分完成 / 待验证 / 新需求”，而不是按旧 ❌ 重建。

**5.2 “完成”只有一个标签，混合了工程完成和产品可信。** 4A/4B 打勾附带导出/生图走查 UNVERIFIED；所谓薄纵向切片实际上只验到 41/155 张照片录入，模型未配时整理、讲题、复习、判定、掌握回写都不可达。见 [走查报告](<D:/smart mistake book/docs/research/2026-10-11-thin-vertical-slice-walkthrough.md:14>)。应分别记录“代码接线、模块验证、设备全链、真实模型质量”四项。

**5.3 图像与切题验收还只是方向。** 新阶段 spec 写准确率“立项自定”，没有最终数值、样本分层、金标判定规则或重大错题/切残的独立门。总体准确率很高仍可能稳定损坏某一类几何题。需要定义分母、错误严重度、失败路由，以及切分/识别/重绘各自的门，不能共用一个“模型成功率”。

**5.4 阶段 5 范围过大，依赖没有落实成可执行分批门。** 同一行含复习三键、判题限定、KC 卡片、错因自动化、讲题增强、备注、undo、设置。哪些只依赖稳定题级账本、哪些必须等高质量绑定，应拆成清晰子批并尊重现有硬门，避免整批无法验收。同时补真实样本如何积累到 400 个可预测长程样本，不能把 400 条原始记录视为达标：MODEL_JUDGED、REVEAL 被排除，首条及同日重复不贡献预测样本。还需明确样本来源、错误键/辅助作答污染清理、回放对照和不达标回退；不能只写 Wave X 等数据。

**5.5 基准与“绿门”没有统一测最终产物。** 本轮 [JVM Golden 量表](<D:/smart mistake book/build/golden-jvm-metrics.txt:6>) 明确不锁质量：A-route-rerank 68/129、Recall@5 52.71%；B-route-mirror 79/129、61.24%。它们不是最终真机融合召回，不能直接用来判 D3 失败或达标。设备基准的回归地板 Recall@5 ≥0.64、MRR ≥0.56 也不能证明现行 D3 主集 0.90、逐章 0.80。绑定 95% 是另一指标；批次 9 的 95.15% 点估计也不等于总体下界达到 95%（其近似 CI 为 92.34%–97.97%，抽样设计另有约束）。应明确每个门的入口、数据、统计口径和最终产品消费位置。

候选检查还存在对象错位：内容门默认查 staging，dense 门固定查成品资产。所以“候选内容检查 + dense 绿”并不证明同一候选包与向量匹配。发布对象需要同一版本、同一内容指纹。

**5.6 自动状态页尚不能作为当前提交的独立证据。** [generate_status.py:99](<D:/smart mistake book/tools/ci/generate_status.py:99>) 用残留 `.class/.jar` 存在判编译 OK，APK 存在判 Release OK；测试 XML 未绑定本次运行，`JOB_STATUS` 缺失默认 success，整体 PASS 不直接要求所有 JVM/Lint/设备门已测且绿。当前状态页还写 commit=`local`、branch=`unknown`，只展示部分模块。应绑定 HEAD/run ID、产物与测试时间，并将未测门保留为未通过完成门。

**5.7 内存修复之后仍需检查准确率和尾部路径。** [KD-32](<D:/smart mistake book/docs/known-defects.md:1210>) 已记录 import 采样修复，但旧大图 crop/净化仍可整幅解码；2048 采样后的公式/图形识别精度没有验证。历史记录全知识包安装约 1,133 秒，不能由“无 OOM”推出首装体验已达标。多协议长推理修复也主要落 Chat Completions，其他三协议的截断分流仍为已知缺口。当前 19 个主源码文件超过 1,000 行，单凭行数不判坏，但账本、图资产、任务、缓存身份跨层分散，应优先统一这些契约，减少继续增补局部分支。

## 6. 本轮实际验证

| 检查 | 命令 / 结果 | 证据 |
|---|---|---|
| 全量 JVM | `gradlew.bat testDebugUnitTest test :app:testLocalFirstDebugUnitTest :app:testStrictOfflineDebugUnitTest --rerun-tasks --console=plain`；BUILD SUCCESSFUL，243 任务执行，2,486 tests / 0 failures / 0 skipped | [日志](<D:/smart mistake book/.artifacts/software-audit-2026-10-13/jvm-verification.log>)、[逐模块 XML 汇总](<D:/smart mistake book/.artifacts/software-audit-2026-10-13/jvm-results.json>) |
| 双 Debug 组装 | `gradlew.bat :app:assembleLocalFirstDebug :app:assembleStrictOfflineDebug --console=plain`；BUILD SUCCESSFUL（组装任务 up-to-date，前一组合命令也完成两组装任务） | [日志](<D:/smart mistake book/.artifacts/software-audit-2026-10-13/assemble-verification.log>) |
| 完整 Lint | 两 app flavors + core:data/database/export/ui + 全部 feature 的 `lintDebug`，`--continue`；exit 1，library 5 个 NewApi、data 1 个 NewApi | [日志](<D:/smart mistake book/.artifacts/software-audit-2026-10-13/lint-complete-verification.log>) |
| Python 工具 | `PYTHONPATH=tools`、UTF-8 环境，`python -m unittest discover -s tools/tests`；691 tests，18 failures + 1 error，672 通过 | [日志](<D:/smart mistake book/.artifacts/software-audit-2026-10-13/python-verification-utf8.log>) |
| KB 候选门 | `python tools/ci/run_kb_checks.py --json`；exit 1，24 内容门中 23 绿，`unbound_points=14` 红；consistency/roundtrip/manifest 绿；dense 绿的对象为成品 | [JSON](<D:/smart mistake book/.artifacts/software-audit-2026-10-13/kb-checks.json>) |
| 幂等冲突模型 | `python .artifacts/software-audit-2026-10-13/knowledge-quiz-idempotency-sqlite-reproduction.py`；复演到 UNIQUE 冲突，脚本明确非 Room/设备运行 | [脚本](<D:/smart mistake book/.artifacts/software-audit-2026-10-13/knowledge-quiz-idempotency-sqlite-reproduction.py>) |

Python 首次运行有 2 个 GBK 输出编码错误，改为 UTF-8 重跑后消除；剩余 19 项不能归因于编码。主要涉及成品旧基线与 staging/权威表漂移、覆盖目标、晋升用例和缺失文档，不代表 19 个 App 功能均损坏。

Lint 具体为 [MistakeExportHubRoute.kt:415](<D:/smart mistake book/feature/library/src/main/kotlin/com/tingyun/smartmistakebook/feature/library/MistakeExportHubRoute.kt:415>) 的 `java.time` 与 [DenseTokenizer.kt:165](<D:/smart mistake book/core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/dense/DenseTokenizer.kt:165>) 的 `String.codePoints`。应用已有 desugaring，不能把全部 library Lint 报错直接说成低版本必崩；但当前要求的兼容性门确实为红，低版本设备路径未验证。

## 7. 建议的优先顺序

1. **先修事实与身份问题**：A1/A2 小测、A3 切分、A5 备份一致性、A6 导出缓存；每项补对应跨模块场景，避免错误学习事实与假成功。
2. **把可信坐标系真正晋升并由最终入口验收**：消掉候选 14 空点、迁移覆盖映射、对齐成品/侧车/dense，再修 A4 最终检索；同步已过/未过的 3D 子门，不削弱既有红门。
3. **补真实纵向切片**：用真实模型和有数据形态走完录入→讲题/作答→判定→掌握→第二次复习，并加入杀进程、改绑、导出、备份换机；将“可运行”与“讲得/判得/画得正确”分开验。
4. **细化图像与复习阶段契约后实施**：R1/R3 金标与错误红线，阶段 5 分批、全库扫描、undo、独立证据与冷却语义，学情到行动入口。
5. **按现总路线推进扩展能力和真实数据校准**：考前/自适应/纸质回流、docx/编辑、个性化换机备份、Wave X。先纠正文档中已经存在的入口，避免重复造一套。

本次审计：**Completion Gate PASSED**（报告与当前证据已交付）。软件全量完成/发布门：**Completion Gate FAILED**（Lint、Python 工具、候选内容门仍红，真实模型全链与上述缺口尚未完成）。
