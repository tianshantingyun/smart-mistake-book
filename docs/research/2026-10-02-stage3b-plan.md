# 阶段 3B「掌握库结构」实施计划（已批准 · 2026-10-02）

> 依据：master line 3B 行、phasing §3B（`docs/superpowers/specs/2026-09-25-agent-first-refactor-phasing.md:31,49,57-58`）、
> 台账 D-M（`docs/agent-first-refactor-decisions-2026-09-23.md:796-811`，M1–M7 全已裁）、
> fix-plan KF-32（`docs/research/2026-09-28-algorithm-fix-plan.md:401-414`）、
> 裁决 22 修订二（插眼 8 最终口径，台账 `:2146-2156`）、roadmap（KF-32 先于 3C）。
> 用户裁定：回退=工具化+演练；KF-32 验收=测试内合成数据；选择器先修接线=纳入 3B。

## 0. 范围

**三步走**（照 phasing 既定顺序）：

| 步 | 内容 |
|---|---|
| 一 · 删与迁 | **D-M M1**（删 legacy 投影表 + fixture 播种系统）+ **D-M M5**（删 `projection_consumption` + 两个死列）→ **STUDY_DATABASE_VERSION 56→57** |
| 二 · 收敛 | **D-M M4**（两条证据写通道并一）+ **D-M M6**（删 V1、`scoreKnowledgeNode` 入共享打分核）+ **选择器先修接线** |
| 三 · 重放与工具面 | **KF-32**（改绑→账本事件→全量重放；attribution 重派生）+ **D-M M2**（hint/retry 非 UI 接线）+ **D-M M7**（advisory 一等工具）+ **插眼 8 落地**（未分类 pseudo 桶）+ **回退工具化 + 演练** |

**3B 实际剩余已勘定**：S5/S6/S8 与 Q2 的 archive 已随 3A Wave 4 交付；S2/S11 已被裁决 4 移出；P8 已完成。
**不做**：KF-31 提议侧、KF-29/30（3C）；S3 滑动窗（无归属，另行登记）；金标数值校准（Wave X）。

## 1. 关键事实基线（全部 file:line 已核）

- **M1 的实质是功能性的**：`StudyWriteContext.requireTeachingArtifact`（`core/data/.../study/StudyWriteContext.kt:63-66`）在无
  fixture 的构建（release）里对任何 practice unit 直接抛 `IllegalArgumentException("outside the verified M1 catalog")`——
  提交（`StudySubmissionPreparer.kt:42`）与揭示（`StudyAnswerRevealService.kt:27`）两条主路径都会炸（应用从未发布故未见）。
  删 fixture 必须**同时**把这两条路径的元数据来源改成库内事实派生（practice unit + 当前绑定 + 题目字段；无绑定走 pseudo）。
- legacy 两表（`problem_memory_state`/`knowledge_mastery_state`，`LearningEntities.kt:568-.../605-...`）确为 fixture-seed-only
  （唯一生产读者是 v29→30 历史迁移的视图 SQL，v35→36 已被真投影视图取代；`ProblemDao.kt:198-203` 注释已写明）；
  `seedFixture` 被 **21 个测试文件**引用（17 androidTest），是步骤一最大的机械迁移面；**不得保留 test-only 生产接缝**。
- M5 死面确认：`projection_consumption`（`LearningEntities.kt:534-566`，写点 `ProjectionTransactionDao.kt:977-988/512-516`）
  全仓**零 SELECT**；`learner_problem_memory_state.last_reviewed_epoch_day`/`last_attempt_id`（`:748-767`）写后无读者
  （时间戳现算，`ReviewLogSink.kt:65`/`LearningProjector.kt:1092` 注释明说）。`learner_problem_memory_state` 表本身是活体，勿删。
- M6：`useReviewPlannerV2` 默认 true 且**无任何生产/测试翻闸点**（`RoomBackedStudyExperienceRepository.kt:97,923-924`；
  分支 `StudyReviewPlannerService.kt:388-393`；版本取值 `:189-193`）；但 `KnowledgeReviewQueue` 的知识点队列**永远走 V1**
  （`KnowledgeReviewQueue.kt:137-193` → `ReviewPlanner.scoreKnowledgeNode`，`ReviewPlanner.kt:407-512`；V2 无该方法）——
  M6 的真正存量 = 把 `scoreKnowledgeNode`/`masteryRiskFor`（`:361-397`）/难度档（`:519-529`）/`DIFFICULTY_CYCLE`（`:608-612`）
  抽成共享打分核，队列改为消费；`KnowledgeReviewQueue.kt:207-209` 的 `0.3/0.2/1.5` 是唯一未收源的第三份同名常量
  （对应 `AlgorithmConstants.ReviewScoring.FAMILY/SOURCE/MAX_DIVERSITY`）。
- KF-32 现状：attribution 写时钉死（`AttemptTransactionDao.kt:643-667` 五元组校验 + RESTRICT；快照形状 `LearningState.kt:189-222`）；
  `RoomProblemOrganizationStore.confirm`（`core/database/.../RoomProblemOrganizationStore.kt:41-219`，离线纠正 UI 在
  `feature/library/.../MistakeOrganizationCorrectionEditor.kt:316`）**不落任何账本事件**；无 `BINDING_CHANGED` kind；
  全仓**无 upcaster 层**（手写 `when` + 指纹 schema 版本）；successors 链已存在（`KnowledgeNodeSuccessors.kt:28`，
  投影三处消费 `LearningProjector.kt:339,904,965`）；全库 `superseded_by` 目前皆 NULL。
  新增 kind 触点：`LearningState.kt`（新事件类）/`LearningLedgerFingerprint.kt:10-16`/`LearningDaoModels.kt:92-95`/
  `ProjectionTransactionDao.kt` 各 `when(row.eventKind)`/`DatabaseContract.kt:801-806,835-840`（**顺带核实
  `CHAT_EVIDENCE_SUBMITTED` 在两表缺失的既有不一致**）/`StudyProjectionDrainer.kt:269-290`/`LearningCoreVersions.kt`。
  "旧节点归零"= 重放从空表累加 + `applyProjectionTables` 差集删除（`ProjectionTransactionDao.kt:1073-1085`），无需显式清零。
- 插眼 8：pseudo 节点 `pseudo:<SUBJECT>`（`RoomKnowledgeBaseStore.kt:54-105`，displayName「未归类知识点」）已由题目兜底链路创建；
  模型当前**无法**命中它（MODEL_CANDIDATE 被召回过滤 `ProblemOrganizationDao.kt:145-153,166-190` → 永不披露 → 代号解析失败，
  `RoomTutorToolRunner.kt:798-823`）——落地 = 显式披露固定"未分类"桶代号（编造 id 门不变）。
- M7：`llm_teaching_advisory` 表/DAO/三 kind 齐备（`LearningEntities.kt:1157-1200`，唯一索引 `(learner,source,kind)` 天然去重；
  DAO `LearningDao.kt:212-244`）；模型侧完全无工具（`TutorToolName` 仅 5 值，`TutorToolLoop.kt:12-19`）；新工具触点=
  枚举/`TutorToolDescriptions.kt:10-57`/`OpenAiModelProtocol.kt:229-271,349-...`/`TutorPermissionPolicy.kt:68,83-95`/
  `RoomModelTaskRepository.kt:415-456`/`RoomModelTaskToolRounds.kt:35-75`/`RoomTutorToolRunner.kt:194-208`/
  模型输入指纹（`ModelTasks.kt:738`，新声明按守则 strip+指纹+4 用例）/约 11 处测试。
- 回退：archive 只写不读（`ProjectionArchiveEntity` `LearningEntities.kt:678-710`；写 `ProjectionTransactionDao.kt:1651-1668`；
  payload=`LearnerSnapshotJson.encode`，解码 `LearnerSnapshotJson.kt:39` 已有）；缺 = DAO 读回 + 恢复端口 + `schema_ddl`
  校验 + 9 表重建事务；`commitProjection` 防降级按设计拒旧版本，恢复须走专用路径。手工流程
  `docs/research/kernel-projection-rollback.md:59-95`（六步）。
- 在飞状态：工作树仅他线 5 个文件在飞（台账 +225 / specs / tools/kb_build ×2）——不 add、不碰；台账仍脏（记录沿用独立成文）。

## 2. 外部资料（落进设计）

1. **Azure《Event Sourcing Pattern》（官方）**：「you should never update the event data. The only way to update an entity or
   undo a change is to add a compensating event」；演进四策（宽容反序列化/事件版本号/**读时 upcasting**/就地迁移=最后手段）；
   幂等（记最后处理序号）；「Snapshots are an optimization, not a replacement for the eventstream」。
   → **KF-32 = 追加 BINDING_CHANGED 补偿事件 + 读时重派生（upcast），绝不改历史行**。
2. **Marten《Events Versioning》（官方）**：「not to change the past data but compensate our mishaps… appending the new event
   with correction」；加必填字段「consider if it wouldn't be better to add an explicit event type instead」；
   upcasting「on the fly each time the event is read」。→ **新 kind 而非复用 CORRECTION**。
3. **Anthropic《Memory tool》（官方）**：客户端执行、**作用域强校验**（"validate every path"）、大小上限与分页、去敏、
   JIT 检索、"keep content up-to-date, coherent and organized"、"Do not create new files unless necessary"。
   → M7/插眼 8：写入限本 learner + 合法节点 id、payload 上限、稳定键 upsert、读回最近 N 条。
4. **Practitioner：write-manage-read（Towards Data Science，单一来源，标注引用）**：manage 是"the hard part"；
   长期记忆须「curated. Not everything goes in」；带时间戳/版本。→ M7 工具描述写明"只写持久共识"。
   元数据：官方三源（Azure/Marten/Anthropic）逐条采纳；TDS 标注"实践类单源"。

## 3. 三步实施设计

### 步骤一 · M1/M5 删与迁（schema 57）

1. **M5**：迁移 56→57 删 `projection_consumption` 表 + 两死列（SQLite 表重建法，非破坏；历史迁移 SQL 不动）；
   同步 `LearningEntities`/`StudyDatabase`/`DatabaseContract` 列清单/`LearningDaoMappings`/投影写入点/`core:model` 两字段
   （`lastAttemptId` 是必填 model 字段，连带 `require` 与全部夹具）；矩阵用例改 1→57；导出 57.json；
   `KernelWave*SchemaContractTest`/`RoomStudyDatabaseMappingsTest` 相应更新。
2. **M1 删物**：两 legacy 实体/表 + `FixtureSeedDao` 播种读路径 + `seedFixture` 端口与全部调用 + `StudySeedBundle` 两字段 +
   `StudyFixtureSource`/`EmptyStudyFixtureSource`/`StudyFixtureRegistry` + debug `M1CuratedFixtureSource`/`M1FixtureInitProvider`；
   其余消费者（mappers/planner/answerReveal/snapshotBuilder/writeContext）逐点改签名。
3. **M1 功能内核（不可省）**：提交/揭示路径去 fixture 目录门——所需元数据改从 practice unit 记录 + 当前绑定派生；
   无绑定走 pseudo。**验收**：无 fixture 的构建（localFirst release 冒烟 + 单测）能完成「录入→提交→揭示」全链。
4. **测试迁移**：21 个 seedFixture 引用面改「直写 DAO / 走真实写路径」；`M1CuratedStudySeedTest` 随种子系统删除。
5. 门：全量 JVM + `core:database` 仪器化全套（矩阵 1→57）+ app 三屏 + 本步末 R8 冒烟；台账 §3.11。

### 步骤二 · M4/M6 收敛 + 选择器接线

1. **M4**：新建**唯一**证据写入口（core:data 内部，如 `KnowledgeEvidenceWriter`）：`RoomTutorToolRunner.masteryUpdate` 与
   `KnowledgeQuizFeedbackWriter.submit` 都经它——被拒一律落观察行；`anchor_class` 语义统一（quiz 通道补合法 anchor）；
   配额语义统一（D9 半权/配额成入口规则）；**取消 `evidenceConfidence=1.0` 硬编码**（值有理由可引）；幂等 id 规则单源。
2. **M6**：抽共享打分核（`KnowledgeNodeScorer`：`scoreKnowledgeNode`/`masteryRiskFor`/`knowledgeDifficultyBand`/
   `DIFFICULTY_CYCLE`）→ `KnowledgeReviewQueue` 改消费；删 `ReviewPlanner` 类/`VERSION`/`useReviewPlannerV2` 与分支/
   `ReviewPlannerTest`（独有覆盖逐条核对转移，不静默丢断言）；删 `LearningCoreVersions.REVIEW_PLANNER`/`REVIEW_COMPOSITE`；
   队列三常量收源。**零 bump 证明**：先补"默认即 V2 且 V1 串不再出现在计划行"定格测试。
3. **选择器先修接线**：`AdaptiveSelectionRequest` 加先修稳定度表字段 + 仓库调用点填；效果用例：先修弱 → 不再 SKIP。
4. 门：全量 JVM + app 三屏；台账零 bump 理由行。

### 步骤三 · 重放与工具面（一次合并 bump）

1. **KF-32 + M3**：新账本事件 `BINDING_CHANGED`（补偿事件；**仅全量重放**语义，与 CORRECTION 同类）：
   事件类 + 指纹 + 常量 + 解码/许可两表（含核实 CHAT_EVIDENCE 不一致）+ drainer 判定；写入点 `RoomProblemOrganizationStore.confirm`
   两条路径（自动接受 + 离线纠正）落事件+outbox；重派生：replay 期 attribution 由 `practiceUnitId → 当前
   practice_unit_knowledge_binding` 派生（写时快照仅增量路径用）；旧 bindingId 经 successors 链解析（已有）；
   版本 **PROJECTOR v12 + ATTRIBUTION v4 + LEDGER v3** 一次 bump + archive 先归档全量重放；台账 §3.12。
   验收（合成数据）：改绑→事件→重放→历史证据挂新节点→旧节点经差集删除归零→幂等；合并×改绑组合；无改绑不触发重放。
2. **M2（非 UI）**：核实 `MasteryEvidencePolicy` 六档消费点 → HINT 形态可达；缺 `hintCount→persistedAssistance` 转换补在提交路径；
   UI 采集归阶段 5（写明登记）。测试：六档各一例。
3. **M7 + 插眼 8**：`ADVISORY_READ`/`ADVISORY_WRITE` 两枚一等工具（描述单源；权限"任何轮次"；读=按节点/题/科目最近 N 条；
   写=三 kind 限枚举/稳定键 upsert/payload 上限/作用域校验；描述写明 curate 语义；模型输入 strip+指纹+4 用例）；
   插眼 8 = 会话代号表显式含本科「未分类」桶代号（解析到 `pseudo:<SUBJECT>`，缺则 ensure；编造 id 门不变；不新增触发）。
4. **回退工具化 + 演练**：archive 读回（latest by learner/version）+ `restoreArchivedProjection` 端口：`schema_ddl` 比对 →
   decode → 单事务重建 9 表（state_version 递增）→ **跨版本恢复拒**（版本陷阱写进 KDoc）；演练：真实重放 archive 做恢复测试 +
   走 `kernel-projection-rollback.md` §4 全流程落演练记录；archive 保留=维持只增（登记复看）。
5. 门：全量 JVM + `core:database` 仪器化全套 + app 三屏 + R8 冒烟；完成记录独立成文 + master line 3B 行（台账脏则待回填）。

## 4. 版本与迁移预算

- `STUDY_DATABASE_VERSION` 56→**57**（步骤一，M5 删表删列，非破坏）。
- 步骤三合并 bump：`PROJECTOR` v11→**v12**、`ATTRIBUTION` v3→**v4**、`LEDGER` v2→**v3**（KF-32）；archive 先归档。
- 零 bump：M1、M4/M6、M7/插眼 8、回退工具（各写明证明）。
- 台账 §3.11/§3.12 + §2 号段行；`KernelWave0SchemaContractTest` 版本字面量同步。

## 5. 冲突裁决（逐条落法）

① 3B 桶=§0 实际剩余；② 回退=工具化+演练（用户裁）；③ 插眼 8 按裁决 22 修订二，phasing 文字过期；④ 3B 只交桶结构，
不依赖 KF-31；⑤ KF-32=重放半（3B），K3 提议半留 3C；⑥ 门常数"依据可引"（插眼 6 研究），数值校准归 Wave X；
⑦ audit 阶段表过期→照 §0；⑧ 全程写「D-M Mx」防编号撞车；⑨ M2 非 UI，UI 归阶段 5；⑩ `CHAT_EVIDENCE` 契约不一致→顺带核实修。

## 6. 纪律与门

共享树：不碰他线在飞文件/tools/kb_*；显式文件列表提交；门口径同 3A 尾（性能观测以冷启模拟器为准，
`PerformanceGateTest` 环境档不声称绿）；R8 冒烟 debug keystore 口径。每步：定向测试先红后绿 + 全仓编译 + 门。

## 7. 风险与 UNVERIFIED

- M1 测试迁移面大（21 文件）——按"直写 DAO/真实路径"迁移，无可行 seam 者新造测试夹具（记录）；
- KF-32 改 attribution 语义 → 全量重放输出变化（bump 已预留）；合成演练证机制，真数据后补一次压力测试；
- M7 触模型输入指纹（守则已含）；回退"跨版本拒"是语义边界（KDoc 写明）；
- 不属本阶段（登记）：S3 滑动窗、每召回固定读数开销、候选池预筛、PerformanceGateTest 环境档。

---

## 附录 A · 3B 实施锚点（供工作流子代理）

- **M5**：`LearningEntities.kt:534-566`（ProjectionConsumptionEntity）/`:748-767`（两死列）；写点 `ProjectionTransactionDao.kt:977-988,512-516`；
  模型字段 `LearningState.kt:549,558`（+ `:592 require`）；映射 `LearningDaoMappings.kt:404/413/552/561`；迁移样板 `KernelWave3Migration_55_56.kt`；
  注册 `StudyDatabase.kt:124,219,303-303...`（addMigrations 至 `KERNEL_WAVE3_MIGRATION_55_56`）；矩阵 `FullMigrationMatrixInstrumentedTest`。
- **M1**：删物清单见 §1 基线第 2 条；测试引用面（seedFixture 21 文件）另列；`StudyWriteContext.kt:18-23,63-66`；
  `StudySubmissionPreparer.kt:42,56,82`；`StudyAnswerRevealService.kt:21,27,31`；debug 源集 `core/data/src/debug/...`（含 AndroidManifest init provider）。
- **M4**：`RoomTutorToolRunner.kt:777-950`（含 D9 半权 :897-903、拒绝观察行 :941+）；`KnowledgeQuizFeedbackWriter.kt:19-88`
  （`evidenceConfidence=1.0` 在 :44）；`MasteryEvidencePolicy.kt:16-23,100-153`；`StudySubmissionPreparer.kt:136 hintCount`。
- **M6**：§1 基线第 4 条全列；测试面 `ReviewPlannerTest`(13)/`KnowledgeReviewQueueTest`(15)/`KnowledgeReviewPlannerTest`(7)/
  `BlockingLearningCoreReviewTest`/`KnowledgeMasteryDropPropagationTest`/`ReviewPlannerScaleBenchmarkTest`。
- **KF-32**：§1 基线第 5 条全列；另 `LearningLedgerChunkReadTest`（假 DAO 需随新 kind 更新）、`StudyDatabaseInstrumentedTest:1938,1992-1995`（kind 映射复制点）。
- **M7/插眼 8**：§1 基线第 6/7 条全列；`OpenAiNativeToolsProtocolTest`/`TutorToolPromptInjectionTest`/`TutorToolRequestDualParseTest`/
  `TutorToolRoundGateTest`/`TutorPermissionPolicyTest`/`MasteryUpdateCodeWhitelistTest`/`RoomModelTaskT6MasteryInstrumentedTest`。
- **回退**：§1 基线第 8 条全列；`ProjectionArchiveDrainerTest`/`ProjectionVersionGuardTest`/`FullMigrationMatrixInstrumentedTest:137`。
