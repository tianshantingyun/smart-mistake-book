# 2026-11-10 · KD-46 每日全库扫描配额（3E）研究：零 schema 最小改法

> 性质：研究/设计件（批次 51 · R2）。本轨**不改代码、不跑 Gradle**（批次纪律：Gradle 只在主会话串行）。
> 所有代码事实均为本轮在当前工作树实读；行号即当前工作树行号。实跑项见 §8。
>
> **结论一句话**：零 schema **可以**把「每次排计划 ≤10」收紧为「**每日实际复习**（有 `KNOWLEDGE_QUIZ` 落账）的全库扫描点 ≤10」，做法是从既有作答账本 `learner_chat_evidence` 派生当日已花费名额；但它**不能**保证「每日**下发/计划** ≤10」——那需要一个当日账本（推荐 DataStore，仍零 Room schema）。schema 76 新表代价见 §6（不建议）。

---

## 0. 决策表

| 口径 | A. 零 schema 证据派生（推荐做最小改法） | B. DataStore 日账本（零 Room schema） | C. schema 76 新表 |
|---|---|---|---|
| 每日**实际复习**的扫描点 ≤10 | ✅ 无重排时严格；有重排时 `≤10+R`（§3.4） | ✅ 严格 | ✅ 严格 |
| 每日**下发/计划**的扫描点 ≤10 | ❌（学生会看到、但没答的不计） | ✅（首次预览即冻结当天 10 个） | ✅ |
| 跨日重置 | ✅ 天然：学习日窗口（04:00 日界） | ✅ 存 `local_day_epoch_day` 比较 | ✅ 行主键含日键 |
| 并发 / 幂等 | ✅ 只读 + 纯函数稳定排序（同输入同输出） | ✅ DataStore `edit` 原子读改写 | ✅ 主键 + IGNORE/REPLACE |
| 迁移 / 备份清单 / 机器块 | **零** | **零**（新 DataStore 键；`deleteAllData` 已整目录清） | **全链**（§6，≥15 文件 + 4 门 + 2 设备链） |
| app 热区接线 | **零** | 有（DI 一处；`SmartMistakeBookApplication.kt` 等 M 文件，冲突风险） | 零（走 DB 端口） |
| 本批建议 | ✅ 先做 | 门要求"下发 ≤10"时改用 | ❌ 不选 |

**为什么不是"把当日已扫集合塞进计划表"**：`review_plan` 的列里没有节点集合（`ReviewEntities.kt:10-59`），`active_review_plan_slot` 只有指针 + 时间戳（`:60-104`），`review_queue_knowledge_node` 只能挂在**错题** queue item 下面（`:156-179`）。`plan_fingerprint` 是计划身份、有唯一索引（`:24-27`），往里追加任何东西都会破坏"同指纹=同输入同算法"的语义与唯一性。计划表能提供的是**日键**（`local_day_epoch_day`）与**时点**（`created_at_epoch_millis`），提供不了"集合"。

---

## 1. 现状（本轮实读）

### 1.1 常量与两档范围

- `FULL_LIBRARY_SCAN_DAILY_CAP = 10`，KDoc 明写「这是『每次排计划 ≤10』，**不是严格的每日累计总量**」——`core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/KnowledgeReviewFullLibraryScan.kt:6-30`。
- 第二档范围两条件（有绑定题、有可出题材料）在 `fullLibraryScanCandidate`：`KnowledgeReviewFullLibraryScan.kt:51-73`。
- 取队：第二档先按分取前 cap 个（平局按 node id），再与第一档并集，最后时间预算 + 多样性取队——`KnowledgeReviewQueue.kt:191-241`；`.take(fullLibraryScanDailyCap)` 在 `:200`，并集在 `:204`。
- 队列里 KD-46 口径引用：`KnowledgeReviewQueue.kt:152-154`、`:184-185`。

### 1.2 生产装配与两处调用

- `StudyReviewPlannerService.currentKnowledgeReviewPlan`：`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/StudyReviewPlannerService.kt:805-960`
  - 今日计划：`:812-817`；第一档范围：`:831-838`；退役过滤 `:842`；
  - `todayCandidateIds`（第一档 ∩ 可出题材料）：`:858-859`；
  - 第二档读行并减去第一档：`:879-880`；候选：`:889-898`；
  - 取队调用：`:912-929`（`fullLibraryScanDailyCap = FULL_LIBRARY_SCAN_DAILY_CAP` 在 `:928`，时间预算取已落库计划 `:925`）。
- 生产调用**两处**，各自带新 UUID：
  - 首页计数预览：`app/src/main/kotlin/com/tingyun/smartmistakebook/SmartMistakeBookRoot.kt:647-655`（`requestId = "knowledge-review-count:<uuid>"`；该目的地进入 RESUMED 组合、且 `status`/`planId`/`profile`/模型可用性 key 变化时重算一次）；
  - 会话进屏：`SmartMistakeBookRoot.kt:963-966`（`"knowledge-review-plan:<uuid>"`）。
- `requestId` 只做非空校验（`StudyReviewPlannerService.kt:810`），**不落库、不参与去重**；所谓幂等只能来自"只读 + 确定性"，不能写成"按 requestId 幂等"。
- 知识点队列不落库：`KnowledgeReviewSessionPlan` 是纯数据类（`KnowledgeReviewQueue.kt:48-60`），全仓无持久化落点；前份 3E 研究同结论（`docs/research/2026-11-09-3e-verification.md:71-73`）。

### 1.3 现有口径被两处测试固化（改造时必须处理）

- JVM：`core/domain/src/test/kotlin/com/tingyun/smartmistakebook/core/domain/KnowledgeReviewQueueTest.kt:527-570`——断言"纯函数无状态、同输入逐位相同、同日两批不重叠、累计到 20"（`:549-569`）。
- 设备：`core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/study/StudyReviewPlanner3EScanInstrumentedTest.kt:323-390`——第一次 10、第二次**仍然 10 且不相交**，末断言 "当日累计 20 > 10"（`:386-390`）。**这就是要改写的那条**（收紧，不是放宽）。

---

## 2. 既有持久面盘点：能不能承载"当日已扫描集合"

| 表 | 主键/关键列 | 时间戳 | 可否当"当日已扫/已复习集合" | 结论 |
|---|---|---|---|---|
| `review_plan` | learner, `local_day_epoch_day`, tz, `plan_fingerprint`(unique) | `created_at_epoch_millis` | ❌ 无节点列；指纹是身份不可追加 | 只能当日键 |
| `active_review_plan_slot` | learner, day, tz → plan_id | `updated_at_epoch_millis` | ❌ 单指针 | 只能当日键 |
| `review_queue_item` / `review_queue_knowledge_node` | plan_id + practice_unit / node | — | ❌ 只能在**错题**队列项下挂节点 | 不可挪用 |
| `assessment_item_snapshot` | id 编 `(learner, session, node)`；`created_at` | ✅ | ⚠️ 可派生"已下发"；但 `created_at` **无索引**（实体索引列见 `LearningEntities.kt:20-25`），且要解析 id、表义是"呈现内容" | 备选，不推荐 |
| `learner_chat_evidence` | learner, node, `source_kind`, `created_at`（**有索引**） | ✅ | ✅ 唯一"节点 × 通道 × 时间"的既有账本；学生一作答就落（含被拒观察行） | **选它** |
| `learner_knowledge_mastery_state` | learner, node | `last_evidence_at` | ⚠️ 不分通道（讲题/知识点测验混在一起），会把讲题与第一档也算进来 | 不可用 |
| `knowledge_search_index_state` / `content_install_state` | 安装/索引状态 | — | ❌ 与学习证据无关 | 不可用 |

### 2.1 为什么 `learner_chat_evidence` 是正确的那张表

- 索引：`(learner_id, created_at_epoch_millis)` 与 `(learner_id, knowledge_node_id, created_at_epoch_millis)`——`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/entity/LearnerChatEvidenceEntity.kt:20-25`；列 `source_kind` / `created_at_epoch_millis` / `rejected_reason` 在 `:40-60`。
- 写入口：知识点测验作答走 `KnowledgeQuizFeedbackWriter`——accepted 通道 `KNOWLEDGE_QUIZ` 在 `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/KnowledgeQuizFeedbackWriter.kt:88` / `:249`；题面读不回/键版本不符 → 写 **rejected 观察行**（`:201-227`）；键未独立核验 → 同样写 rejected 观察行（`:228-244`），行内 `created_at = occurredAtEpochMillis`、`source_kind = KNOWLEDGE_QUIZ`（`:300-328`）。
- 门控拒写也落行：`KnowledgeEvidenceWriter.kt:123-138`（`GateResult.Rejected` → `recordChatEvidence` 一条 rejected 行）——"被拒 ≠ 删除"。
- 读口先例：`ModelTaskDatabasePort.kt:221`（全量 `readChatEvidenceByLearner`）、`ChatEvidenceDao.kt:159`；按时窗 + 通道的**带索引**查询已有先例（`lastAcceptedAtForKc`、滚动窗 COUNT，`ChatEvidenceDao.kt:180-207`；端口注释 "Indexed gate queries (batch-scale: O(log n), no full-table pull)" 在 `ModelTaskDatabasePort.kt:230-233`）。

### 2.2 一个必须知道的例外（进入残留风险）

并非每次作答都 100% 落新行：
- 若这笔证据已被一条定向更正/撤销指向（`KnowledgeQuizFeedbackWriter.kt:95-101`）——直接早退，不写新行；
- 证据身份被先前的 rejected 行占住且补偿也失败（`:271-281`，`EVIDENCE_IDENTITY_STICKY`）——不写新行（但更早那行已在库，通常是往日）。

两者都只会**少计**，不会多计；进 §3.4 的 `R2`。

---

## 3. 方案 A（推荐，零 schema）：当日已复习节点派生 + 剩余名额

### 3.1 数据流

```
今日计划（review_plan / active_review_plan_slot）── 只取"日键 + 学习日起点"
        │
        ▼
learner_chat_evidence
  WHERE learner_id=? AND source_kind='KNOWLEDGE_QUIZ'
    AND created_at ∈ [学习日 04:00, +24h)
  → DISTINCT knowledge_node_id  =  quizzedToday
        │
        ├── 减去 todayCandidateIds（今日第一档 ∩ 可出题）→ spentToday（当日已花费名额）
        │
        └── 与 scanCandidates 求交 → spentInScan（当日已复习的扫描点，回放用）
remaining = max(0, FULL_LIBRARY_SCAN_DAILY_CAP − |spentToday|)
第二档 = spentInScan（回放，不再计名额） ∪ (scanCandidates − spentToday) 取前 remaining（新增）
```

### 3.2 "利用既有计划状态/指纹/时间戳"具体指什么

- **日键**：`planningContext()` 用 `StudyDayMath.localEpochDayOf` + 04:00 日界算 `localDate`，并用同一个 `studyDayEpochDay` 算 `startOfDay`——`StudyReviewPlannerService.kt:780-796`（公式 `:790-791`）；日界单源 `StudyDayMath.kt:49-52`、`:65-69`。
- **与计划同一日键**：`createReviewPlan` 写 `localDayEpochDay = planningContext.localDate.toEpochDay()`（`:665`）；`currentReviewPlan` 也按它查（`:430-436`）——不会出现"知识点按昨天、错题按今天"的分叉。
- **计划时间戳**：`PlanningContext` 当前只有 `localDate` 与 `planningAtEpochMillis`（`:799-802`），构造点唯一（`:792-795`，那里**已经算好** `startOfDay`）——把 `studyDayStartEpochMillis` 加进这个 data class 即可，零风险（全仓无第二处构造）。
- **指纹**：`plan_fingerprint` 只作计划身份，**不参与**配额的读写，也不因本改动 bump（知识点队列不进计划指纹，见 `KnowledgeReviewFullLibraryScan.kt:26-28` 与 `docs/research/algorithm-version-ledger.md` §3.12 的零 bump 口径）。

### 3.3 最小改动（文件级）

1. `core/database`（**零 schema，只加查询**）
   - `ChatEvidenceDao` 加一条只读查询（带 `sourceKind` 参数，值取 `StudyDbValue.ChatEvidenceSourceKind.KNOWLEDGE_QUIZ`，避免字面量漂移）：
     ```sql
     SELECT DISTINCT knowledge_node_id FROM learner_chat_evidence
     WHERE learner_id = :learnerId AND source_kind = :sourceKind
       AND created_at_epoch_millis >= :sinceInclusive
       AND created_at_epoch_millis <  :untilExclusive
     ```
   - `ModelTaskDatabasePort`（挨着 `:221`）加端口方法；`RoomStudyDatabase`（声明 `:67 : StudyDatabasePort`，`readChatEvidenceByLearner` 实现处 `:1880`）实现；测试 `FakeStudyDatabasePort`（`core/data/src/test/.../RoomBackedStudyExperienceRepositoryTest.kt:1965`，证据列表 `:2013`、写 `:2350`、读 `:2376`）实现。**生产具体实现只有 `RoomStudyDatabase`、测试具体实现只有 `FakeStudyDatabasePort`**（其余全是 `by delegate` 或继承 fake），爆炸半径 2 个文件。
2. `core/domain/.../KnowledgeReviewQueue.kt`
   - 加参数 `fullLibraryScanReplayCandidates: List<KnowledgeReviewCandidate> = emptyList()`（默认空 = 既有调用逐位不变）；
   - 并集改一行：`val pool = (candidates + replayPicks + scannedPicks).distinctBy(KnowledgeReviewCandidate::knowledgeNodeId)`；
   - `replayPicks` = 回放候选按同一打分核过滤后原样进池（不 `take`，不占 `remaining`）；
   - KDoc 把 `fullLibraryScanDailyCap` 的语义写清："**本次调用可新增**的名额（调用方已扣当日已复习）"。
3. `core/data/.../StudyReviewPlannerService.kt`
   - `PlanningContext` 加 `studyDayStartEpochMillis`；`planningContext()` 构造处传 `startOfDay`；
   - 新私有方法：
     ```kotlin
     private suspend fun knowledgeQuizNodesReviewedToday(ctx: PlanningContext): Set<String> =
         database.readKnowledgeQuizNodesReviewedBetween(
             learnerId = learnerId,
             sinceEpochMillisInclusive = ctx.studyDayStartEpochMillis,
             untilEpochMillisExclusive = ctx.studyDayStartEpochMillis + AlgorithmConstants.DAY_MILLIS,
         ).toSet()
     ```
   - `currentKnowledgeReviewPlan` 在 `todayCandidateIds`（`:859`）之后：
     ```kotlin
     val quizzedToday = try {
         knowledgeQuizNodesReviewedToday(planningContext)
     } catch (cancelled: CancellationException) {
         throw cancelled
     } catch (failure: Throwable) {
         android.util.Log.w("StudyReviewPlannerService", "knowledge quiz quota read failed", failure)
         null                                   // 未知花费 ≠ 0 花费：fail-closed
     }
     val spentToday = quizzedToday?.minus(todayCandidateIds)
     val remaining = spentToday
         ?.let { (FULL_LIBRARY_SCAN_DAILY_CAP - it.size).coerceAtLeast(0) }
         ?: 0
     val spentInScan = scanCandidates.filter { it.knowledgeNodeId in spentToday.orEmpty() }
     val newScan = scanCandidates.filterNot { it.knowledgeNodeId in spentToday.orEmpty() }
     ```
     取队调用改成：`fullLibraryScanCandidates = newScan`、`fullLibraryScanReplayCandidates = spentInScan`、`fullLibraryScanDailyCap = remaining`。
   - 注释里两处 KD-46 口径（`:821-830`、`:931-933`）同批改写成"每日累计（实际复习）"。
4. `core/domain/.../KnowledgeReviewFullLibraryScan.kt`：常量 KDoc 从"每次排计划 ≤10，非严格每日"改成"每日累计 ≤10（按当日 `KNOWLEDGE_QUIZ` 落账去重；无重排时严格；残留 R/R2 见研究件）"；**值 10 不变**。
5. 不改：schema/迁移/备份清单/机器块/DataStore/app 接线/计划指纹/排程算法版本。

### 3.4 不变量（写进 KDoc 与证据里的原话）

- `spent` = 当日有 `KNOWLEDGE_QUIZ` 落账、且不在**今日**第一档可出题集合（`todayCandidateIds`）里的节点。
- **无重排**（知识队列当天第一次构建后，第一档集合 `todayCandidateIds` 不变：计划不重排、材料不增删）：不在第一档的作答节点只可能来自扫描档 ⇒ `spent` 就是"当日扫描实际复习集合"⇒ 并集 = `|spent| + (10 − |spent|) = 10`，**严格**。
- **有重排**（当天计划被重排，某扫描点被拉进第一档）：该点被 `minus(todayCandidateIds)` 减掉 ⇒ 上界 `10 + R`，`R` = 当日"先被扫描下发、后被重排进第一档"的节点数（`R ≤ |第一档|`，且只在当日重排后才可能 >0）。
- 反向（第一档节点被移出）：只会**多算**已花费 ⇒ 少取（保守，不会超）。
- `R2` = 当日作答但没有落任何证据行的节点（§2.2 两种例外）⇒ 只会少计，上界再加 `R2`。
- 时钟回拨 / 备份恢复 / 清库：日窗口按当前时钟算，回拨可能把当日行移出窗口、换库会丢掉当日行 ⇒ 最多再多一批（低频，登记）。
- **一句话**：A 承诺"每日**实际复习**（有落账）的扫描点 ≤10（无重排时严格）"，**不**承诺"每日下发 ≤10"。

### 3.5 跨日重置

- 没有任何计数器要清：窗口 = `[studyDayStart, studyDayStart + DAY_MILLIS)`，`studyDayStart` 由当天 `localDate` 反推（`:790-791`）。
- 本地 03:59:59.999 与 04:00:00.000 分属两个学习日（`StudyDayMath.kt:44-52`）；测试见 §5-C2。
- 与错题计划同一天键 ⇒ 两个队列在午夜/04:00 一起翻页，不会互相错位。

### 3.6 并发

- A 方案**只读不写**；选择是纯函数 + 稳定排序（score 降序、node id 升序：`KnowledgeReviewQueue.kt:195-199`）⇒ 同输入并发两次得到**同一批** 10 个（不是两批 10 个），并集仍 10。
- 两个并发调用只有在"输入快照不同且没有作答证据"时才可能选出不同批次——这正好落入 `R` 上界（§3.4），不需要 mutex（没有写入可串行，mutex 也替代不了账本）。
- 多进程：应用单进程（Room 单实例）；跨进程不适用。

### 3.7 幂等

- 同输入重复调用：输出逐位相同、不写任何表（除既有计划读取）⇒ 天然幂等；`requestId` 不参与（`StudyReviewPlannerService.kt:810` 只校验非空）。
- 因此**不要**在文档/证据里写"按 requestId 幂等"；要写"只读 + 确定性 ⇒ 重放同结果"。

### 3.8 失败路径

| 失败 | 处理 | 测试 |
|---|---|---|
| 证据查询抛错/超时 | `remaining = 0`：本次**不取新增**扫描点，第一档照常；`Log.w`（同包先例 `ReviewLogSink.kt:142`）。**不得**把未知花费当 0（会超发） | C6 |
| 协程取消 | 原样抛 `CancellationException`（仓内惯例，参考 `StudyReviewPlannerService.kt:104-106`） | 既有 |
| 今日无计划 | 既有 `requireNotNull` 抛（`:813-817`）；与配额无关 | 既有 |
| 证据行节点已退役/无材料 | 仍计入已花费（保守，不少取） | C3 覆盖"rejected 也计" |
| 回放节点已不可出题 | `scoreKnowledgeNode` 返回 null 自动剔除；名额不补 | 既有打分口径 |
| 时钟回拨 / 恢复 / 清库 | 当日可再用一批；登记为残留 | §3.4 |

---

## 4. 方案 B（备选，仍零 Room schema）：DataStore 日账本（严格"下发 ≤10"）

只在门要求「**下发** ≤10」或判定 `R/R2` 不可接受时采用。

- **形态**：domain 新接口 `KnowledgeScanQuotaStore`（`consumed(day): Set<String>` / `consume(day, ids): Set<String>`）+ DataStore 实现（新键 `scan_quota_day` / `scan_quota_nodes`，一个 `edit {}` 原子读-改-写；day 不符先清空；结果集恒 ≤10）。
- **语义**：当天**第一次**调用冻结当天的 10 个；首页计数预览与进屏读同一份日集合（数字与内容一致，不存在"预览烧额度"）；当天不补捞——已复习/已掌握腾出的名额**不再补新的**（这是"每日累计 ≤10"的严格解释，产品上要接受"当天最多就这 10 个"）。
- **跨日**：比较 `local_day_epoch_day`；**并发**：DataStore `edit` 串行；**幂等**：同请求重复调用返回同一集合；**失败**：写失败 ⇒ 本次不返回扫描档（fail-closed），第一档照常。
- **代价**：新接口/实现；DI 一处（`StudyExperienceRepositoryFactory` / `SmartMistakeBookApplication`——**app 热区在飞文件**，本批他线在开，冲突风险要按"最小 hook"处理）；`StudyReviewPlannerService` 构造加参数（JVM/设备两个 harness + 所有替身）；`deleteAllData` 已整目录清 `files/datastore`（`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/backup/AndroidBackupRepository.kt:928-930`，删除在 `:967`）⇒ 不用额外清；备份 `settings.json` **不要**加这个键（跨设备恢复会带回过期日账本，反而锁死当天）。
- **为什么仍优于 schema 76**：不动 Room 版本/备份清单/机器块/历史包兼容；数据本来就是设备本地临时状态。

---

## 5. 先红后绿用例

**前置步骤（避免"红=编译不过"）**：先把 §3.3-1 的 DAO/端口/Room/fake 实现落下来（生产行为不变），再写用例 → 红（服务还没接线）→ 再落 §3.3-3 的服务逻辑 → 绿。**不得**用改断言、放宽 cap、改 `required` 变绿。

| # | 用例（层） | 夹具/动作 | 断言 | 今天为什么红 |
|---|---|---|---|---|
| C1 | `scanQuotaIsDailyCumulativeAcrossPlanCalls`（JVM，`FakeStudyDatabasePort`） | 第一次 `currentKnowledgeReviewPlan` 取 10 个扫描点；用 `recordChatEvidence` 给这 10 个节点各写一条当日 `KNOWLEDGE_QUIZ` 行（生产行形状）；第二次调用（快照不变或 `snapshotAfterMastering` 均可） | 第二次扫描档为空**或**回放同一批 10；`first ∪ second` distinct ≤ 10 | 现在第二次再取 10 个、累计 20 |
| C2 | `scanQuotaResetsAtStudyDayBoundary`（JVM） | 10 条证据时间 = `studyDayStart − 1ms`；另 10 条 = `studyDayStart`（同一批节点可复用） | 前者不计（remaining=10，新增 10）；后者计（新增 0，可回放同批） | 现状根本不读证据 ⇒ 后半红 |
| C3 | `scanQuotaCountsRejectedObservationRows`（JVM） | 写 `rejected_reason != null`、`source_kind=KNOWLEDGE_QUIZ`、当日时间的行 | 计入已花费 | 现状不计 |
| C4 | `scanQuotaDoesNotChargeTodaysFirstTierReviews`（JVM，**守卫**） | 只给 `todayCandidateIds` 的节点写当日证据 | 扫描档仍能取满 10（"扫描不被今日队列挤掉"） | 现状也绿——守住反向回归 |
| C5 | `scanQuotaNeverExceedsWhenTimeBudgetBindsFirst`（设备） | 1200 s 预算第一次只取 4（`TIGHT_BUDGET_SECONDS`）→ 写 4 条证据 → 2700 s 第二次 | 第二次新增 ≤6，两批并集 ≤10 | 现状第二次取 10，累计 14 |
| C6 | `scanQuotaFailsClosedWhenEvidenceReadFails`（JVM） | fake 的新查询抛 `IllegalStateException` | 返回的队列无扫描档、不抛；第一档与既有断言不变 | 现状不调用 ⇒ 有 10 个扫描档 ⇒ 红 |
| C7 | `planCallsWithIdenticalInputsReturnIdenticalScanPicks`（JVM，**守卫**） | 连调两次、不写任何证据 | 两次扫描 id 集合逐位相同；证据表行数不变 | 现状也绿——守"只读/确定性" |
| C8 | `dailyQuotaDeviceScenario`（设备，**改写** `StudyReviewPlanner3EScanInstrumentedTest#scanQuotaIsPerPlanNotADailyCumulativeTotal`） | 第一次 10 → **经真实写路径**（`recordChatEvidence` 或 `submitKnowledgeQuizFeedback`）写 10 条当日证据 → 掌握态更新 → 第二次 | 第二次扫描档 0（或同一批回放）；当日 distinct = 10；**删掉**旧的 "> FULL_LIBRARY_SCAN_DAILY_CAP" 断言 | 旧断言正好相反 |
| C9 | `pendingBudgetIsNotDoubleSpent`（JVM） | 同日两次调用；第一次只写 4 条证据 | 第二次 `remaining = 6`（不是 10） | 现状 10 |

**旧用例处置**：
- `KnowledgeReviewQueueTest#scanQuotaIsPerPlanNotADailyAccumulator`（JVM，`:527-570`）**保留**——它测的是纯函数"每次调用 ≤cap/同输入一致"；只把 KDoc 与末条断言文案改成"纯函数无状态；每日口径由调用方（服务）扣减，见 C1"。不要删、不要把它读成"反对 KD-46 修复"。
- `StudyReviewPlanner3EScanInstrumentedTest#scanQuotaIsPerPlanNotADailyCumulativeTotal`（`:323-390`）**必须改写**（C8）：它模拟"复习完"用的是 `snapshotAfterMastering`（只改快照、不落证据）。在新口径下"复习完"= 有 `KNOWLEDGE_QUIZ` 落账；只改快照是一个产品上不可达的状态，继续用它测"每日累计"会要求实现去猜一个不存在的信号。改写后它在修复前红、修复后绿。

---

## 6. 若坚持落库：schema 76 完整代价清单（**不建议**）

> 先说结论：这是一个"设备本地的当日临时配额"，既不是用户内容，也不是可重建的派生索引；6C-2 的四类里**没有正确归属**。落库的代价远大于收益。

### 6.1 迁移 / schema

- `STUDY_DATABASE_VERSION 75→76`（`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabase.kt:142`）；新 `StudyMigrationsV75To76`；注册进 `addMigrations`（`:357-432`，现末项 `UI_INTEGRATION_MIGRATION_74_75`）；实体 + `@Database(entities=…)`；KSP 重新导出 `76.json`（**新 `identityHash` 只能取构建产物，不得手写**——同 `SmbkSelectionPlan.kt:43-47` 的纪律）。
- 建议表形：`knowledge_scan_quota_day(learner_id, local_day_epoch_day, knowledge_node_id, study_day_utc_offset_minutes, picked_at_epoch_millis, PK(learner_id, local_day_epoch_day, knowledge_node_id), FK→knowledge_node)`；日键必须带偏移（`StudyDayMath.kt:12-17` 的"时间戳+偏移"账本纪律）。
- `deleteAllData` 删库文件（`AndroidBackupRepository.kt:961`）⇒ 无需额外清；`DeviceMigrationDrillInstrumentedTest` 1..76 加一条。

### 6.2 备份 / 清单（6C-2）

- 99 → **100 表**；四类归属没有正确答案：
  - 用户四类（错题/掌握/会话/图片）都不是；
  - `ALWAYS`（`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/backup/SmbkSelectionPlan.kt:172-182`）会随**每个** mask 入包 → 换机恢复带回别的设备的当日行，会**锁死当天扫描**；
  - `DERIVED`（`:183-188`）语义是"派生索引、恢复后重建"，这张表**不可重建**；SPLIT 更不成立。
- 牵动：`trimOrder`（`:213`，现脚本实测 91 项）、FK 裁剪顺序、软引用（若不用 FK）、`rowCountKeys` → 100（`:207`）、`SCHEMA_VERSION` / `IDENTITY_HASH` / `CLOSURE_VERSION`（`:41` / `:46` / `:51`）。
- **历史包兼容坑（本轮实读，最贵的一处）**：
  - `ClosureRuleTables.v4.rowCountKeys` 由"当前 99 键 − 2 错因 − 7 内部 + 1 material_feature"**算术派生**，且 init `check(size == 91)`（`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/backup/ClosureRuleTables.kt:77-101`）——加一张表 ⇒ v4 变 92，**类初始化直接崩**；
  - `v5`/`v5Ui` 同样由当前键派生（`:67-70`），`SmbkArchiveCodecTest.kt:48-49` 钉死 91/92 ⇒ v5 变 93，测试炸，且真实 closure-5 旧包会被判"摘要键集不符"；
  - 因此必须：新表加入 v4 的 `addedTables`、v5/v5Ui 改成**显式冻结集**、`v6` 不能再用 `CLOSURE_VERSION` 动态生成（`:57-64`）——要新增冻结 v6 + 现行 v7，`of()` 接受 7（`:104-109`），`rowCountKeysForRead` 加 6（`SmbkSelectionPlan.kt:698-702`）。

### 6.3 清单 / 机器块（四方一致门）

- `tools/ci/check_backup_plan_sync.py` 的 `SCHEMA_PATH`（`:56`）与 `SmbkSelectionPlanTest` 的 `CURRENT_SCHEMA_PATH`（`core/data/src/test/.../SmbkSelectionPlanTest.kt:640-641`）都硬编码 `75.json` → 改 76。
- 同批再生四处：`SmbkSelectionPlan.kt` 常量/清单、设计件 §9.14 `6c2-plan-v3` 机器块、`SmbkSelectionPlanTest` 空库全选 golden（`:648-649`，现 `8567faae…`，99 表）、`76.json`；脚本的 v4 复算也要把新表加进 `v4_added`（`check_backup_plan_sync.py:316-324`），否则 91 键断言失败。
- `BackupRowCountsCoverageInstrumentedTest.kt:83` 的 `99` 与用例名（"NinetyThree"）同批改 100。
- `CLOSURE_VERSION 6→7` ⇒ 新前缀 `smbk-selection-v7` + 新 golden；`allowedMasks 9/13/15` 与 6C-2 三项裁定**不得动**（`SmbkSelectionPlan.kt:28-34`）。

### 6.4 备份 / 恢复设备面

- 全要重跑：`BackupRestoreInstrumentedTest`、`BackupSelectionRestoreInstrumentedTest`（mask 9/13/15 行数对拍）、`BackupV4PackRestoreInstrumentedTest`、`BackupRowCountsCoverageInstrumentedTest`、`DeviceMigrationDrillInstrumentedTest`。
- 恢复侧要不要"按当天过滤账本行"是**新的产品裁定**：不滤 = 换机当天可能 0 名额；滤 = 恢复路径新增逻辑 + 新测试。

### 6.5 文档 / 验收

- `docs/known-defects.md` KD-46、`docs/acceptance/adjudication.md` 3E 行、`docs/research/2026-10-11-new-stages-spec.md` §3E、算法版本台账（本表不进计划指纹 ⇒ 仍零 bump，但知识队列输出变化要记）、行级 review JSON / 台账机器块。
- 合计：**≥15 个文件 + 4 处清单/门 + 2 条设备链**，与"一个临时日配额"的收益不成比例。

---

## 7. 验证命令（主会话串行执行；本轨未跑）

```bash
# 改前/改后协作检查
git status --porcelain -- core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain \
  core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study \
  core/database/src/main/kotlin

# 域层纯函数
./gradlew :core:domain:test --tests "com.tingyun.smartmistakebook.core.domain.KnowledgeReviewQueueTest" --console=plain

# JVM 服务层（C1…C4、C6、C7、C9；若放新测试类，同命令换 --tests 类名）
./gradlew :core:data:testDebugUnitTest --tests "com.tingyun.smartmistakebook.core.data.study.RoomBackedStudyExperienceRepositoryTest" --console=plain

# 设备真库（C5、C8；改写的用例）
./gradlew :core:data:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.tingyun.smartmistakebook.core.data.study.StudyReviewPlanner3EScanInstrumentedTest \
  --console=plain

# 只有走 B/C、或动了备份清单时才需要
python -I tools/ci/check_backup_plan_sync.py
```

---

## 8. 本轮实跑与未跑（如实）

**实跑（只读，不涉及 Gradle）**：

| 命令 | 输出（摘要） |
|---|---|
| `python -I tools/ci/check_backup_plan_sync.py` | `PASS 四方一致（code / §9.14 机器块 / golden / 75.json）`，`EXIT=0`；其中 `trimOrder code=91`、`v4: 91 keys`、`digest … 8567faae0e90…`（与 §6.2 的"加表会破 91"判断同源） |
| `python -I -c "import json;d=json.load(open('core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/75.json',encoding='utf-8'));print(d['database']['version'],len(d['database']['entities']),d['database']['identityHash'])"` | `75 99 f9a3d1c9ea8ef83d7222721ad1015a51`（version / entities / identityHash） |
| `git status --porcelain -- core/domain core/data docs/research` | 目标域文件为 `M`（他线/前批既有改动，本轨**未改**）；`docs/research/2026-11-10-daily-scan-quota.md` 不存在（本文件新建） |

**未跑（如实）**：任何 Gradle 任务、任何 JVM 测试、任何设备/androidTest、任何 adb。原因：本轨是研究件，批次纪律要求 Gradle 只在主会话串行执行（与其他 agent 并发会撞锁）。因此 §5 的用例**目前只是设计**，红/绿状态未在本轮实测——"今天为什么红"是**读码推断**，不是实跑结果。

---

## 9. 待裁 / 残留风险

- **Q1（最关键）**：3E「无条件 PASS」的口径是"每日**实际复习** ≤10"还是"每日**下发/计划** ≤10"？
  - 前者 → 选 A（本文件推荐的最小改法）；
  - 后者 → 选 B（DataStore，仍零 schema）；只有"必须进 DB/进备份"时才选 C。
- **Q2**：`R`（当日重排把扫描点拉进第一档）与 `R2`（作答未落行）是否可接受？两者都低频，但要让 3E 证据如实写"当日 R=…"。
- **Q3**：`R` 的实际频率需要设备/日志量化（建议在 C8 证据里记一次"当日是否重排"）。
- 残留风险（A）：重排 `R`、`R2`、时钟回拨、恢复/清库、证据表增长后的查询成本（有索引，仍建议设备量一次 P95；首页预览每次 RESUMED 都会读一次）。
- 若选 B：DI 接线落在 app 热区（他线在飞文件），必须按"最小 hook"做，冲突即停项登记。

---

## 10. 引用索引

- 常量/范围：`KnowledgeReviewFullLibraryScan.kt:6-30`、`:51-73`
- 取队：`KnowledgeReviewQueue.kt:48-60`、`:152-154`、`:171-241`（`.take` `:200`，并集 `:204`，稳定排序 `:195-199`）
- 装配：`StudyReviewPlannerService.kt:665`、`:780-802`、`:805-960`（`todayCandidateIds` `:858-859`，scanRows `:879-880`，scanCandidates `:889-898`，取队 `:912-929`）
- 调用点：`SmartMistakeBookRoot.kt:647-655`、`:963-966`
- 接口口径：`core/domain/.../StudyExperienceRepository.kt:565-568`
- 计划表：`ReviewEntities.kt:10-59`、`:60-104`、`:107-153`、`:156-179`
- 证据表：`LearnerChatEvidenceEntity.kt:20-60`；写口 `KnowledgeQuizFeedbackWriter.kt:88`、`:201-244`、`:249`、`:300-328`；门 `KnowledgeEvidenceWriter.kt:123-138`
- 端口/DAO：`ModelTaskDatabasePort.kt:217-233`、`ChatEvidenceDao.kt:159`、`:180-207`、`RoomStudyDatabase.kt:1880`
- 测试：`KnowledgeReviewQueueTest.kt:527-570`、`StudyReviewPlanner3EScanInstrumentedTest.kt:323-390`
- 备份/清单：`SmbkSelectionPlan.kt:41-51`、`:172-207`、`:213`；`ClosureRuleTables.kt:56-109`；`SmbkArchiveCodecTest.kt:48-49`；`SmbkSelectionPlanTest.kt:640-649`；`BackupRowCountsCoverageInstrumentedTest.kt:83`；`tools/ci/check_backup_plan_sync.py:53-57`、`:316-324`
- 版本/迁移：`StudyDatabase.kt:142`、`:357-432`
- 清库：`AndroidBackupRepository.kt:916-967`
- 前情：`docs/known-defects.md:2070-2085`（KD-46）、`docs/research/2026-11-09-3e-verification.md:71-73`、`:147`、`:534`；`docs/research/2026-11-02-fsrs-capacity.md:404`；需求原文 `docs/requirements-fused-final-2026-10-11.md:114-118`、`:228`
