# 阶段 3B · 掌握库结构 完成记录 · 2026-10-03

> 依据：`docs/REFACTOR-MASTER-LINE.md` 3B 行 + `docs/research/2026-10-02-stage3b-plan.md`（三步走计划）+ 台账
> D-M M1–M7 / KF-32 / 裁决 22 修订二 / 裁决 28 登记项。
> 实施方式：六批（B1–B6），每批"实施 → 协调方正式门 → **独立复核**（新 Agent、只读、对抗性）→ 修复轮 → 显式文件清单提交"。
> 本记录独立成文；台账（`docs/agent-first-refactor-decisions-2026-09-23.md`）他线在飞，回填待其静止（沿用既有纪律）。

## 1. 交付与提交（六批）

| 批 | 提交 | 内容 | 版本指纹面 |
|---|---|---|---|
| B1 | `a96bc8ce` | **D-M M5**：死面删除（`projection_consumption` 表 + `learner_problem_memory_state` 两个无读者的列） | `STUDY_DATABASE_VERSION` 56→57（非破坏） |
| B2 | `3d234d32` | **D-M M1**：fixture 播种系统整退场 + 提交/揭示路径去"fixture 目录门"（改由 `StudyPracticeUnitFacts` 从库内事实派生；无绑定走 pseudo 桶） | schema 57→58（DROP 两张 fixture 专表，不搬运） |
| B3 | `edac9f5f` | **D-M M4** 写通道并一（`KnowledgeEvidenceWriter`，quiz 通道补 rejected 观察行与 `anchor_class`）+ **D-M M6** V1 planner 退场与共享打分核（`KnowledgeNodeScorer`）+ 选择器先修接线（字段+消费+用例） | 零 bump（定格测试证明） |
| B4 | `7e72e408` | **KF-32**：改绑 → 新账本事件 `BINDING_CHANGED`（仅全量重放消费）→ 重放期归因按"当前绑定集合"重派生（upcast）；旧节点经差集删除归零；顺带修 `CHAT_EVIDENCE_SUBMITTED` 提交许可缺失 + `readBoundMaterialIds` 调用点 900 分块 | **projector-v12 / attribution-v4 / ledger-v3**（`learning-core-v12`）；schema 58→59（新表 `binding_change_event`） |
| B5 | `a7b87a0f` | **D-M M2** hint 接线（`hintCount>0` → HINT 协助条目，两档死分支可达；UI 采集移交阶段 5）+ **D-M M7** advisory 一等工具（`ADVISORY_READ`/`ADVISORY_WRITE`）+ **插眼 8** 未分类桶代号（`UNCLASSIFIED_BUCKET` → `pseudo:<SUBJECT>`） | 零 bump；提示词版本 tutor-plan-v14 / tutor-respond-v21 / tutor-lobby-v12 |
| B6 | `3529c7f8` | **回退工具化**：`projection_archive` 读回 + 专用恢复路径 `restoreArchivedProjection`（8 道判定全在写之前；覆盖前归档使回退可逆）+ 真机演练 9 例 + 演练记录 | 零 bump；零 schema/迁移变动 |

（先行提交：计划文档 `890d966f`；每批的版本台账行 §3.11–§3.15 随代码同笔。）

## 2. 验收门（对照计划 §3 三步走）

| 步 | 验收点 | 结论 | 证据 |
|---|---|---|---|
| 一 | M5 删面：`projection_consumption` 零 SELECT、两列零读者 | ✅ | B1 台账行 §3.11；`:core:database` 仪器化含 1→57 矩阵 |
| 一 | M1 功能内核：**无 fixture 的构建能完成「录入→提交→揭示」全链** | ✅ | `StudySubmissionWithoutFixtureInstrumentedTest` 2/0（真机）；release R8 冒烟（B2） |
| 一 | M1 不可留 test-only 生产接缝 | ✅ | 21 个 `seedFixture` 引用面改"直写 DAO / 库文件直写 / 真实写路径"；curated 内容以测试源集夹具留存（三份，KDoc 记录） |
| 二 | M4：两证据写通道并一；被拒一律落观察行；anchor/配额语义统一 | ✅ | `KnowledgeQuizFeedbackWriteTest` 5/0（rejected 行 weight 0 + reason + anchor_class）；讲题通道逐字段不变 |
| 二 | M6：V1 生产不可达 → 删；打分核共享；**不静默丢断言** | ✅ | `ReviewPlannerTest` 13 例逐条移交/退场登记（台账 §3.12）；定格测试"默认即 V2" |
| 二 | 选择器先修接线 | ✅（登记边界） | `AdaptiveQuestionSelectorTest` 19/0；**全仓无生产构造点**已登记 |
| 三 | **KF-32**：改绑→事件→重放→历史证据挂新节点→**旧节点归零**→幂等；合并×改绑；无改绑不重放 | ✅ | `BindingChangedReplayDrillInstrumentedTest` 3/0（5 连跑）；`ProblemOrganizationDatabaseInstrumentedTest` 15/0（含 touch A/B：停用即 `expected:<4000> but was:<2000>` 失败） |
| 三 | KF-32 "当前绑定集合"语义（E 复核） | ✅ | = 最近一次确认那一批（`accepted_at == MAX(receipt)`）；RESTRICT 保留的审计旧绑定不算当前；写时快照与重放**同一读口** |
| 三 | M2：六档全部可达 | ✅ | `MasteryEvidencePolicyTest` 六档各一例（含新补 `INCORRECT_ON_RETRY`）；端到端三条（有提示答对/答错/揭示支配） |
| 三 | M7：两枚一等工具（描述单源/任何轮次/每参数校验/限枚举/稳定键 upsert/payload 上限） | ✅ | `OpenAiNativeToolsProtocolTest`、`TutorToolRoundGateTest`、`TutorToolAuthorizationTest`、`TutorToolCallTest`、`ModelTaskFingerprintStabilityTest`（4 面）；`RoomModelTaskToolLoopInstrumentedTest` 9/0 + T6Mastery 3/0 |
| 三 | 插眼 8：桶代号披露、编造门不变、桶不进召回面、不新增强制 | ✅ | `PseudoUnclassifiedBucketInstrumentedTest` 1/0（CURATED 对照证明是过滤）；`advisoryWriteLandsOnTheUnclassifiedBucketCode` |
| 三 | **回退工具化 + 演练**（用户裁） | ✅ | `ProjectionRollbackDrillInstrumentedTest` 9/0（真 Room + 真 drainer：主链逐位一致、归档只增 0→1→2→3、4 拒绝 + 3 守卫 + 呈现态超前拒绝）；记录 `docs/research/2026-10-03-projection-rollback-drill.md`（runbook §4 逐步映射） |

## 3. 终门（全量，2026-10-03）

| 门 | 结果 |
|---|---|
| 全量 JVM（`./gradlew test --continue --rerun-tasks`） | **2174 / 0 / 0**（312 个结果文件；app 双 flavor 63、core:data 604、core:model 394、core:domain 550、core:database 121、feature:tutor 199、feature:capture 106、feature:review 39、core:ui 37、feature:library 35、core:export 14、feature:profile 6、tools/image-mcp-server 6） |
| core:database 仪器化全套（冷启 `-wipe-data` 模拟器，含性能门严格口径） | **200 / 0 / 0**（回退演练 9、性能门 6、迁移矩阵 1→59 6、问题整理 15、StudyDatabase 29） |
| app 三屏仪器化（`connectedLocalFirstDebugAndroidTest`，class 过滤） | **5 / 0 / 0**（LearningMastery 1 + SchedulingSettings 2 + SourceCalibration 2） |
| R8 冒烟（localFirst release，debug keystore 口径） | `assembleLocalFirstRelease` **5m14s**；装机 Success、启动 PID 6882、logcat 零 `FATAL EXCEPTION/AndroidRuntime/ClassNotFound/NoSuchMethod/NoClassDefFound`；首屏 = 复习根空态（无 fixture 的 release 如实行为） |

## 4. 独立复核（六批）

| 批 | 复核结论 | 处置 |
|---|---|---|
| B3 | approved（零阻断） | — |
| B4 | approve-with-notes，无 P0/P1 | P2 读侧分叉（`StudyPracticeUnitFacts.knowledgeNodeIds` 仍全量读）**提交前修**（与归因/重放同读口）；存量库 v58 缺口登记台账 §3.13 |
| B5 | approve-with-notes，无 P0/P1 | 5 项 P2 全处置：advisory PK 加 learner 前缀（与唯一索引同冲突面）/ `CancellationException` 重抛 / 孤儿行边界注释 / 六档 reason 钉死 / ADVISORY_WRITE 落桶用例 |
| B6 | approve-with-notes，1 P1 | P1「呈现态不随恢复回滚」：文档过度声明订正为"重派生仅全量重放路径"；增量路径 → **恢复前显式拒绝**（`PRESENTATION_AHEAD`）+ 三守卫（`CHECKPOINT_AHEAD`/`RESTORED_AT_IN_PAST`/`MALFORMED_ARCHIVE`）；runbook §4-4 与 drill §4-4/§4-5 订正 |

## 5. 登记与遗留（不属 3B / 明示边界）

- **存量库 v58 缺口**（台账 §3.13 注）：v58 期"有回执但无行落在 MAX(receipt)"的单元读当前集合为空集——无数据损坏、下次确认自愈、迁移不回填（应用未发布，真实存量仅开发库）。
- **KD-30**：native tool_calls 路由不解析 `extendedResult`（MASTERY_READ 扩展预算只在 json_object 路由生效）——既有缺陷，登记不修，最小修法在缺陷册。
- **选择器先修接线**：`AdaptiveQuestionSelector` 全仓无生产构造点（B3 登记；字段/跳过消费/效果用例已落，将来接线用既有 `KnowledgePrerequisiteReader`）。
- **UI `hintCount` 采集**移交阶段 5（B5 登记；`ReviewSessionScreen` 两处提交未传 hintCount → 默认 0）。
- **B6 UNVERIFIED**：边界 6（归档载荷引用已删 practice_unit/knowledge_node 的 RESTRICT 回滚）未构造用例（按事务语义推断整体回滚）；`MALFORMED_ARCHIVE` 只包 `IllegalArgumentException` 家族；P1 反向实证（无守卫时的楔死形态）未直接复现（依据 require 链 + 守卫在真实构造触发）。
- **回退 archive 保留 = 维持只增**（drill §3）：复看触发点 = 发布前置的存量压力观测；若密集全量重放按"每 learner 最近 N=2 份"评估。
- **计划原文两处口径差**（drill §4-4 为准）：恢复覆盖 **8 表**（计划写 9）——`presentation_projection_state` 不在归档 JSON 内；目标选择 = **最近一份 + 版本拒**（计划写 latest by learner/version）。
- **不做**（计划 §0 已裁）：KF-31 提议侧、KF-29/30（3C）；S3 滑动窗（无归属，另行登记）；金标数值校准（Wave X）。
- 台账回填待他线静止；主线段落已收口（见 `REFACTOR-MASTER-LINE.md` 3B 行）。

## 6. 剩余风险

- **真实数据压力未做**：改绑重放演练用测试内合成数据（用户裁定口径）；3D D-1 的真实 rebind 数据到后补一次真实压力测试（计划 §7）。
- **回退工具的运维边界**：跨版本恢复一律拒（真实灾难恢复需同时回滚应用版本）；恢复是维护操作（排空静止时执行，无跨进程互斥）；呈现态超前时显式拒绝（先处理呈现态或走全量重放路径）。
- **门常数数值校准**仍归 Wave X（3B 只交付"依据可引"）。
