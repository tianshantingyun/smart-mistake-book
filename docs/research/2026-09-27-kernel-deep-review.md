# 内核算法层深挖复核（2026-09-27）

- 日期：2026-09-27
- 状态：复核完成，四层病灶逐条落盘；修复排入重划后的阶段 3A/3B/3C
- 上游：`docs/research/2026-09-25-mastery-mechanism-review.md`（阶段 0 研究）
- 方法：三个对抗审计子代理（校准口径 / 坐标系污染 / 跨机制契约）+ 主会话亲手核实常数；
  所有结论带 `file:line`（主工作树）；未实测处标 UNVERIFIED。
- 证据等级沿用阶段 0 研究的定义（A/A−/B/C/D/未找到）。

## 1. 总判断

阶段 0 研究回答的是"算法选型对不对"（选型大体对，常数无据、9 项必须补）。
本轮复核回答的是更底层的"**已有的这套东西，真的在按它声称的方式运转吗**"。答案是：

> **通道在，但训不出来；账在，但两本不一致；绑了，但没有复核、没有监测、也没有自愈。**

四个病灶层：

- **A 层 · 校准通道结构性训不出来**：FSRS 个性化参数的拟合 loss 与在线投影各有一条腿不接线；
  门槛只有 8 条、无采纳门、每次启动静默覆盖；被污染的样本直接进拟合集。
- **B 层 · 证据契约链漂移**：投影期改写证据不回写账本，训练数据与真实施行不一致；
  "看答案后答对"当前被记为独立答对（w=1.0）。
- **C 层 · 坐标系污染链**：候选菜单→绑定→掌握→排程→材料注入→全权重写，全程零题面语义复核；
  绑定正确率零实测；K3 自动重拆是死门；错绑不可自愈。
- **D 层 · 时间与配额语义裂缝**：跨时区/DST 下"同日/跨日"判定漂移；新会话模型下配额语义与
  实现错位（隐性债务）。

## 2. A 层 · 校准通道结构性训不出来

路径缩写：`R`=`core/domain/.../SchedulingEvaluation.kt`、`M`=`FsrsScheduleMath.kt`、
`MM`=`MemoryUpdateModel.kt`、`SINK`=`core/data/.../ReviewLogSink.kt`、
`CAL`=`StudySchedulingCalibration.kt`、`REPO`=`RoomBackedStudyExperienceRepository.kt`、
`REVEAL`=`StudyAnswerRevealService.kt`、`APP`=`SmartMistakeBookApplication.kt`、
`ENT`=`core/database/.../entity/LearningEntities.kt`、`DAO`=`dao/LearningDao.kt`。

**训练会坏（结构性）**

| # | 病灶 | 证据 | 后果 |
|---|---|---|---|
| A1 | **w20（decay）梯度恒为零**：拟合重放 `R:148` 不传 parameters，`M:64` 默认 decay → loss 与 w20 无关，参数永远停在默认值；在线投影 `MM:86,93` 与 `M:83` 同样硬编码默认 decay | `R:430-434` 声称拟合含 w20 | 个性化参数即使写入，遗忘曲线这条腿永远不生效 |
| A2 | **w3/w16 结构性不可训练**：映射永不产 EASY（`M:210-241`），训练集恒空；w16 钉 1.0 不拟合 | `M:18-22`、`R:460-462` | 官方 4 值评级被 3 值合成映射取代，两参数成死参数 |
| A3 | **ANSWER_REVEALED 伪装 AGAIN 进拟合集**：`REVEAL:49-62` 落 ATTEMPT/rating=1/weight=0；重放只认 rating（`R:31`），weight 不进样本 | 研究 §8.2 明令禁止 | 看答案污染 S′_f 训练集 |
| A4 | **缺 state{0..3} 列**：`ENT:1032-1077` 无 state；同日分支靠 elapsed<1 天猜测（`R:141-147`） | 官方 schema 必填 | New/Learning/Review/Relearning 不可分，首条全按 New |
| A5 | **无"每日每卡首条"去重**：`DAO:179-186` 无按日分组 | 官方只计首条 | 同日重复行用从未拟合的 w17-19 推进状态再进次日预测，轨迹被扭曲 |
| A6 | **无 day_start**：日界固定本地午夜（`StudyWriteContext.kt:38`），拟合期无法换 day_start 重算 | 官方默认 4:00 | 凌晨复习系统性错派前一天（幅度未实测） |

**参数会静默不更新/劣化**

| # | 病灶 | 证据 |
|---|---|---|
| A7 | **无采纳门 + 每次启动无条件覆盖**：`CAL:91` 无条件写库；fit 每次从 DEFAULT 冷启动（`R:529`），不比较存量参数 log loss | 官方采纳门"新参数不劣于现参数"未实现 |
| A8 | **门槛 8/64 条**（`R:596-597`）vs 官方 400 硬门 + >300 health check | 8 条可预测样本就拟合并上线=拿噪声当参数 |
| A9 | **每次启动静默跑一次**（`APP:353-354` runCatching），注释自称"self-gated by fsrs-rs (>=64)"——64 是自定值非官方口径 | 无日志无 UI，诊断盲区；`R:413` 空样本异常被静默吞 |
| A10 | **MODEL_JUDGED 永久排除且无回归机制**：`R:184-185` 无条件过滤；UI 暗示"校准达标后放开"（`SourceCalibrationSection.kt:27-28,90-101`）但代码无开关 | 无工件题的学生（讲题判定主导）FSRS 永远训不出来 |
| A11 | **新复习栏交互信号链断**：`ReviewInteractionTracker`（`feature/review/...:12`）无生产调用者；scroll_up/away/interruption 恒 0（`REPO:602-608` 透传）→ `avoidancePracticeUnitIds`/`confidenceAtErrorByPracticeUnit`/注意力折扣对新复习栏失效 | 链在、信号断 |
| A12 | **scheduling_eligible 悬空**：无生产写入者写 false；`DAO:179-186` 不筛；映射时丢弃（`SINK:119-131`） | 将来冷却抑制落地后，观测行会无声进拟合集 |

**仅文档/审计问题**

| # | 病灶 | 证据 |
|---|---|---|
| A13 | go/no-go 评估门是死代码：`evaluateSchedulingModels`/`fsrsBeatsBaseline`（`R:105-112`）生产零消费 | 算出来没人看；FSRS 开关是手动 toggle |
| A14 | 拟合窗口 takeLast(20_000)（`CAL:88,141`）vs 官方全历史 | 与 A7 叠加使"重启重拟合"随机游走 |
| A15 | duration 语义漂移（MODEL_JUDGED 行=判词时间−上次活跃、reveal 行=0） | 暂不致病（拟合不消费 duration），但列口径失真 |

**HLR 顺带核实**：80% 完成=采集-回填-报表环；**模型本人是手调常数**
（`HalfLifeRegression.kt:20-21,133-134,149-166` 注释自认"NOT trained on real data"），
全库无 theta 拟合代码；**晋升到投影的门不存在**（类注释明言"never fed back into scheduling"）。

## 3. B 层 · 证据契约链漂移（训练数据与真实施行不一致）

| # | 病灶 | 证据 | 后果 |
|---|---|---|---|
| B1 | **投影期改写证据不回写账本**：`applyRevealCausality`（`LearningProjector.kt:683-708`）把看答案后答对改写为 `(NONE, 0.0)`，但 attempt_event 仍存 w=1.0、review_log 仍记 GOOD | 训练集=从未施行的 GOOD | FSRS 拟合与线上投影语义长期漂移 |
| B2 | **看答案后答对当前记为 INDEPENDENT w=1.0**：`StudySubmissionPreparer.kt:96` 硬编码 `revealedBeforeAnswer=false`；policy 的 ANSWER_WAS_REVEALED 分支从 Preparer 不可达（`:53-59` 从不传 persistedAssistance） | 掌握度虚增的正确性 bug（P0） | 学生看答案再答对=独立掌握 |
| B3 | **FSRS 模型完全不消费 weight**：`MM:62-101` 签名有 weight、函数体无引用；0.6/0.9/1.0 折叠成三档评级 | 连续权重唯一保留点是 `LOW_CONFIDENCE_CORRECT_CEILING=0.85`（`M:208-217`） | "证据质量"在记忆侧不存在 |
| B4 | **"看答案"三处语义分裂**：policy=EXCLUDED 不计证据；review_log=AGAIN 进拟合；投影=AGAIN 且 lapseCount++（`LearningProjector.kt:922-923`） | 同一行为三种含义 | "不计证据"与"记一次遗忘失败"并存 |
| B5 | **TutorJudgedReviewSettler 写死 weight=0.5 不走折扣**（`TutorJudgedReviewSettler.kt:97-109`） | 与 preparer 路径不同定价 | 判题路径与作答路径口径不一 |
| B6 | **KnowledgeQuizFeedbackWriter 走门档位（0.35/0.10/0.15/0.18）不经 evidence policy、不进 review_log** | 第三条定价路径 | 三套定价并存，只一套进拟合 |

## 4. C 层 · 坐标系污染链（静默错记，不可自愈）

**链条**：别名截断的索引 → 候选菜单缺正确节点 → 模型绑邻近节点（零题面复核）→ 错节点记账
→ 排程/材料/披露/写门全偏 → 自动自锁 → K3 死门无自愈 → 零监测。

| # | 病灶 | 证据 |
|---|---|---|
| C1 | **零题面语义复核**：`isAcceptableForPersistence`（`RoomMistakeOrganizationRepository.kt:1120-1135`）全部是"模型输出 vs 菜单"内部一致性（confidence≥0.72、节点在菜单、kind 一致、父名一致），**没有任何一步拿题目文本核对"这道题到底是不是这个点"** | 候选菜单错→绑定错的直接通道 |
| C2 | **绑定正确率零实测**：`docs/kb-stage4-report-2026-09-25.md:168` 明确不给绑定正确率；唯一同类量化 A-18=42%（材料-节点，`docs/kb-problem-register-2026-09-15.md:130-137`） | 静默错记的规模未知 |
| C3 | **别名截断直接伤绑定**：索引侧 61.6% 节点片段超限（`KnowledgeSearchFeatureExtractor.kt:18-22`）、prompt 侧 27.7% 别名被截（`RoomMistakeOrganizationRepository.kt:896`）→ 关键别名被截的节点进不了候选菜单 | 模型只能绑邻近节点（错绑）或发 grounding（不绑） |
| C4 | **兜底分支全量绑定 bug**：用户路径 atom 全拒时 `nodeIdsToBind = topicNodes.map{knowledgeNodeId}` 按 **strength=1.0 全绑**（`RoomMistakeOrganizationRepository.kt:1021-1025`） | 宁可全错绑也不落 pseudo |
| C5 | **错误自动绑定自锁**：`mergeAutomaticClassifications`（`:705-733`）把自动接受过的标签以 confidence=1.0 保留，后续自动轮改不掉 | 只有用户手动能改，而错误对用户不可见 |
| C6 | **错绑后果的放大链**：复习作答记账到错 KC（`AttemptTransactionDao.kt:658`）→ 投影错（`LearningProjector.kt:319-322`）→ 排程 masteryRisk 全偏（`ReviewPlannerV2.kt:441-542`）→ 材料注入错点"讲错知识点"（`RoomTutorTeachingReferenceRepository.kt:105`）→ **CONFIRMED 锚定全权重写错 KC**（`TutorKnowledgeCode.kt:47-53`、`RoomTutorToolRunner.kt:743-744`） | 放大系数逐级升高 |
| C7 | **K3 自动重拆是死门**：`ModelTaskKind` 12 种无重拆任务（`ModelTasks.kt:11-24`）、`TutorToolName` 5 工具无重拆（`TutorToolLoop.kt:12-19`）、全仓"重拆/RECLASSIFY"零命中；唯一重绑路径是人工 UI | 没有任何生产路径产生重拆提议 |
| C8 | **改绑不重放**：`RoomProblemOrganizationStore.confirm` 不产生 ledger 事件；`StudyProjectionDrainer` 全量重放唯一触发源是 CORRECTION 事件（`ProjectionTransactionDao.kt:477-482`）；历史证据 attribution 写死旧 bindingId（`AttemptTransactionDao.kt:658`） | **D-Q1G 第 3 条"按新绑定重放历史掌握度"在实现层不存在** |
| C9 | **零监测**：无任何生产绑定正确性监控 | 错记无法被漂移检测发现 |

## 5. D 层 · 时间与配额语义裂缝

| # | 病灶 | 证据 |
|---|---|---|
| D1 | **跨时区/DST 裂缝**：上一复习的本地日用**当前事件**的偏移换算（`LearningProjector.kt:884-888`，`localEpochDayOf` 用 eventUtcOffsetMinutes），上一复习自己的偏移未持久化 | 同日算两天/跨日算同日；影响短时分支与 cross-day streak |
| D2 | **两套日口径并存**：ReviewLogSink 用构造期固定 zoneId 算 delta_t（`SINK:69-74`），投影器用事件存储偏移；`ForgettingCurve.estimateAt` 的 FSRS 分支 wall-clock floor 到整天（`ForgettingCurve.kt:62`）；`MemoryUpdateModel.addDays` 用 86400000 整数倍（`MM:101-116`） | 同一次复习的 delta_t 在拟合与线上投影可能不同 |
| D3 | **回拨被 clamp**：`coerceAtLeast(0)`（`LearningProjector.kt:887`、`SINK:74-76`） | cross-day 不增、anomaly 只计数不修正 |
| D4 | **弃用列仍写仍读风险**：`last_reviewed_epoch_day`（`ENT:705-706`）已从投影与 sink 剥离但仍在写；model 默认值仍是 UTC 日序（`LearningState.kt:544`） | 任何第三读方拿到 UTC 口径 |
| D5 | **会话配额在新模型下语义漂移**：进栏一律新会话 → 50/会话（`MasteryWriteGate.kt:52`）退化为纯循环断路器；防刷只靠 12h 同 KC 冷却 + 100/时滚动窗 | 文档口径需同步改 |
| D6 | **null conversationId→0 配额洞**（`RoomTutorToolRunner.kt:680-689`）：当前被大厅无代号的结构性拒写遮住；大厅真实会话 id（`tutor-conv:UUID`，`TutorLobbyRoute.kt:485`）与工具环 conversationId=null 错位；落库 `conversationId.orEmpty()` 会把多条 null 会话并成空串桶（`:750,773`） | 隐性债务：未来大厅获配注册表即暴露 |

## 6. 修复优先级（进入重划后的阶段 3A）

1. **让训练真正发生（A 层 P0 三件）**：A1 w20 接线（一行：`R:148` 传 parameters + 在线投影两处）；
   A3 reveal 独立标记并排除出拟合集；A7+A8 采纳门（新参数 log loss 不劣于存量）+ 400 硬门。
2. **正确性 bug（B 层 P0）**：B2 看答案后答对定价（`StudySubmissionPreparer` 接 persistedAssistance）；
   B1 契约回写（投影改写同步回写 review_log，或拟合视图与施行视图统一）。
3. **表示重做（阶段 0 研究 §3.1）**：s/f 双计数 + β-二项后验 + Wilson/Jeffreys 区间；
   看答案语义统一 + 独立标记；删 legacy ×0.45；前置硬过滤；考试外生约束。
4. **绑定可信度（C 层）**：C4 兜底全量绑定 bug 修复（改落 pseudo）；C5 自锁打破；
   绑定正确性生产监测管线（抽样人工复核，参照 A-18 口径）；C7 K3 提议的产生路径（新任务/工具）；
   C8 改绑→重放触发路径（D-Q1G 第 3 条真正落地，放 3B）。
5. **口径修复（A/D 层）**：A4 state 列、A5 每日首条、A6 day_start、A10 MODEL_JUDGED 回归机制、
   A11 ReviewInteractionTracker 接线；D1/D2 时间口径统一（上一复习偏移持久化）、D6 配额绑定真会话行。
6. 其余（A13-A15、D3/D4）随对应阶段顺手修或标文档。

## 7. 与既有裁定的关系（修订记录）

1. **D1"非目标"条款修订**：原裁定"学习科学内核不作为重构对象"→ 修订为"**内核结构
   （账本→投影→排程架构）不动；算法表示与数值重做、并科学落实**"。依据：本复核证明最底层
   的表示与数值正是病灶所在。
2. **D-Q1G 第 3 条实现缺失登记**："换版时历史掌握度按新绑定重放"在实现层不存在
   （C8：attribution 写死旧 bindingId、改绑无重放触发）——是**承诺未实现**，不是设计错误，
   归入阶段 3B 必须交付。
3. **K3 登记**：自动重拆的 hook 判据零实现（C7），且无提议产生路径——阶段 3C 必须新增
   "重拆提议的产生路径"（模型任务或工具），否则 hook 永远是死门。
4. **看答案后答对定价 bug（B2）登记为 P0**：当前记为独立答对 w=1.0，掌握度虚增。

## 8. 未验证清单（不得当事实）

- 绑定正确率无任何实测（C2）；A-18=42% 是材料-节点口径，不可直接当题-点错误率。
- A6（day_start 偏移影响幅度）、D1（跨时区裂缝触发频率）未实测——判据是代码路径推导。
- 组织路径召回与金标 0.5444 是否同分布 UNVERIFIED。
- "大厅未来是否开放 MASTERY_UPDATE"未定，D6 的暴露时机取决于此。
