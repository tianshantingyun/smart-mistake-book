# AUD-11 判词/冷却/答案键 三节反例与分层测量

日期：2026-10-15 · 被测提交：`a15aebb7`（HEAD）+ 工作区未提交改动（见 §0.2 快照）
性质：**测量与报告，不改产品语义**。三个判词粒度/闸门分层/答案键的可疑点各自给出可重跑的实测证据与裁定建议。

---

## 0 口径、快照与运行事实

### 0.1 三句话结论

| # | 反例 | 实测结论 |
|---|---|---|
| ① | 判词粒度：会话级模型证据被读成整题判决（无节点过滤） | **成立**。前置 KC 的较新正向证据（半权 0.075）压过目标 KC 上的负向证据，整题被判「做对」，按 0.5 记账（放大 6.67×），判决快照不带任何知识归属 |
| ② | 冷却分层：12h 同 KC 冷却通道无关 | **成立**。模型写之后 1h 的独立小测写被 `SAME_KC_IN_COOLDOWN` 拒掉；一晚讲题后当晚小测客观证据落库 **0/3**，13h 后同样三题落库 3/3；反向（小测挡模型）与小测自锁同样成立 |
| ③ | 答案键：错键判错之后有没有纠正/撤销/重放的落点 | **三样都没有**。错判落 NEGATIVE 0.35 且账本里只有这一条 chat-evidence 事件（不是 attempt）；同身份重发被身份吞掉（连观察行都不落）、新身份在冷却内被拒、冷却过后只是**追加**一笔正向（错判不被替换/补偿）；重放确定性复现错判。附带测到身份粘性导致的**静默丢失**（写入口回报 Accepted，账本零事件） |

### 0.2 被测快照（为什么不是直接在工作区跑）

两棵树在测量时刻**都编译不过 `:core:data`**：

- **HEAD `a15aebb7` 编译不过**：`core/data/.../knowledge/ReviewedTeachingMaterialSidecarJsonCodec.kt:9,210` 引用
  `core.model.KnowledgeMaterialContentAttribute`，而该类型的定义只存在于**未提交**的工作区改动里
  （`core/model/.../ProblemCatalog.kt:243`，已 staged）。HEAD 上跑
  `./gradlew :core:data:compileDebugKotlin` 报 3 条 `Unresolved reference`。
- **工作区当时也编译不过**：`core/data/.../backup/AndroidBackupRepository.kt:653-654`（在非 suspend 函数里调
  `countMistakes()` / `readReferencedCanonicalAssets()`，AUD-05 半成品）与
  `core/data/.../model/OpenAiModelResponseParsers.kt:352,356`（`TutorKnowledgeEvidence` 的 KD-38 常量半成品）。

因此测量跑在一个**冻结快照** `D:\aud11-measure`（`git worktree add --detach a15aebb7`）上，只叠加两项：

1. `core/database/.../dao/ChatEvidenceDao.kt` = **工作区版本**（AUD-01：accepted 先落行、只对新行分配序列/写 outbox），
   md5 `4a148ddc074bdfb1ba1d305195ac3b3a`，与工作区**逐字节相同**；
2. 快照专有的 `core/model/.../KnowledgeMaterialContentAttribute.kt`：把工作区 `ProblemCatalog.kt:232-245` 的
   enum 逐字抄成独立文件（HEAD 缺它 → 编译不过；仅此一处新增，被测路径不引用该类型）。

其余全部为 HEAD。**被测代码与工作区代码等价**（已逐一核对）：

| 文件 | 快照 md5 | 与工作区关系 |
|---|---|---|
| `core/data/.../study/TutorJudgedReviewSettler.kt` | `9c991345…` | 去 CR 后**逐字节相同**（差异仅 CRLF） |
| `core/domain/.../MasteryWriteGate.kt` | `299eabde…` | 去 CR 后相同 |
| `core/database/.../dao/ChatEvidenceDao.kt` | `4a148ddc…` | 逐字节相同 |
| `core/data/.../study/KnowledgeEvidenceWriter.kt` | `38ff88cd…` | 逐字节相同 |
| `core/data/.../study/KnowledgeQuizFeedbackWriter.kt` | `48e44427…` | 逐字节相同 |
| `core/model/.../Assessment.kt` | `67852f8e…` | 逐字节相同 |

### 0.3 运行命令与结果

```
# ①（JVM）
cd /d/aud11-measure
./gradlew :core:data:testDebugUnitTest \
  --tests "com.tingyun.smartmistakebook.core.data.study.TutorJudgedReviewKcGranularityAuditTest"
→ BUILD SUCCESSFUL；XML: tests=3 failures=0 errors=0

# ②③（真库 instrumented，emulator-5554 / Android 14）
./gradlew :core:data:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=\
com.tingyun.smartmistakebook.core.data.study.KnowledgeQuizCooldownLayeringAuditTest,\
com.tingyun.smartmistakebook.core.data.study.KnowledgeQuizAnswerKeyAuditTest
→ BUILD SUCCESSFUL；"Tests 10/10 completed. (0 skipped) (0 failed)"；XML: tests=10 failures=0 errors=0
```

②③ 跑在**真 Room 库**上（`StudyDatabaseFactory.open`，走完整迁移），经**真写入口**
（`KnowledgeEvidenceWriter` / `KnowledgeQuizFeedbackWriter`）写入；知识点经 `importKnowledgeBase` 的
审校导入落到 `knowledge_nodes`（否则门会以 `KNOWLEDGE_NODE_NOT_ANCHORED` 先拒，冷却根本走不到）。

测试文件在快照里（**未**复制进工作区，见 §4 限制）：
`D:\aud11-measure\core\data\src\test\kotlin\com\tingyun\smartmistakebook\core\data\study\TutorJudgedReviewKcGranularityAuditTest.kt`、
`D:\aud11-measure\core\data\src\androidTest\kotlin\com\tingyun\smartmistakebook\core\data\study\KnowledgeQuizCooldownLayeringAuditTest.kt`、
`…\KnowledgeQuizAnswerKeyAuditTest.kt`。

---

## ① 判词粒度：会话级证据 → 整题判决（无节点过滤）

**被测站点**：`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/TutorJudgedReviewSettler.kt:252-255`

```kotlin
val evidence = database
    .readChatEvidenceByConversation(TutorConversationIds.captured(tutorSessionId))
    .filter { it.rejected_reason == null && it.weight > 0.0 }
    .maxByOrNull { it.created_at_epoch_millis }
    ?: return null
```

只按**会话**取数、按**时间**取最新一条；`knowledge_node_id` 既不参与筛选也不参与判决，
判决结果再被写成一笔**权重 0.5** 的题目级证据（`TutorJudgedReviewSettler.kt:282`），
快照归属刻意留空（同文件 `:138-139`「空归属：这条通道只驱动题目级排期」）。

### 1.1 反例构造

多 KC 题面（`MistakeRecord.knowledgeNodeIds = {kc-target, kc-prereq}`）、本轮**无检查题作答**（开放作答 →
走模型判词分支），模型在本会话里写两条真话：

- `kc-target`（本题要考的 KC）**NEGATIVE 0.35** @ t=2600 —— 学生在目标技能上没做出来（＝整题未做对）；
- `kc-prereq`（前置 KC）**POSITIVE** @ t=2700，权重 **0.075**（`0.15 × 0.5`，前置属
  `ANCHOR_CLASS_DISCLOSED`，见 `MasteryWriteGate.kt:197-198` 与 `:219-224` 的半权）——
  只是"这一步会了"，不是"这道题做对了"。

### 1.2 实测（3/3 通过，钉的是**当前行为**）

| 测量 | 结果 |
|---|---|
| 结算状态 / 整题正误 | `RECORDED` / **`isCorrect = true`** |
| 落库判定 | `MODEL_JUDGED_CORRECT`，weight **0.5**，`problemMemoryOutcome = ASSISTED_RECALL` |
| 判定快照归属 | `attributions` 为空 —— 事后无法反查是哪条节点的证据生效 |
| 两条证据行 | 都还在库里、都没被消费（判决只"挑了最新一条"） |
| 对照：把较新那条换成**无关节点**（另一科 KC） | 结果**逐位相同**（`isCorrect = true`、0.5） |
| 半权放大 | KC 账本 0.075 → 整题账本 0.5，**6.67×** |

一句话：**"前置 KC 的证据存在、但整题未做对"的场景，今天就判成整题正确**；而且因为判决不带归属，
这条误判在数据里看不出成因。锚在无关节点上的证据同样能翻转整题判决。
半权（前置/披露）本意是"这条证据不算强"，但在整题口径上它被换成 0.5 的固定先验，
**降权信息在跨粒度时被丢掉**。

### 1.3 建议（留裁定，不改语义）

1. 整题判决要求**至少一条 accepted 证据锚在题面自己的 KC 集**（`MistakeRecord.knowledgeNodeIds`）上；
   只有外部节点证据时按既有产品裁定走 `NO_VERDICT`（保持阻塞），而不是判对。
2. 或者整题权重不再用固定 0.5 先验，而由生效那条证据的 KC 级权重派生（避免 6.67× 的跨粒度放大）。
3. 无论选哪条，判决快照都应记下"是哪条节点/哪条证据驱动的"（现在 `attributions` 是空的），
   否则这类误判在审计面上不可见。

---

## ② 冷却分层：12h 同 KC 冷却与通道无关

**被测站点**：

- `core/domain/.../MasteryWriteGate.kt:42`（`SAME_KC_COOLDOWN_MILLIS = 12h`）与 `:360-363`（`lastWrite < 12h` → 拒）；
- `core/database/.../dao/ChatEvidenceDao.kt:104-109`（任务书所指跨度；KDoc 自 `:101` 起，`SELECT` 在
  `:104-106`，抽象方法声明在 `:109`）：

```sql
SELECT MAX(created_at_epoch_millis) FROM learner_chat_evidence
WHERE learner_id = :learnerId AND knowledge_node_id = :knowledgeNodeId
  AND rejected_reason IS NULL
```

只过滤 rejected，**没有** `source_kind` 过滤。
组合点在 `core/data/.../study/KnowledgeEvidenceWriter.kt:55-59`：两条通道都从这同一个查询取冷却年龄，
而 `MasteryWriteGate.GateInput`（`MasteryWriteGate.kt:262-307`）**没有通道/source_kind 这一维**——
门在结构上不可能区分来路。

### 2.1 与文档承诺的分叉

- `MasteryWriteGate.kt:33-41` 写的是"before the same KC accepts another **model** evidence write"，
  并把独立信号明确寄托在"real attempts on the attempt channel"上；
- 而知识点小测（`KNOWLEDGE_QUIZ`，本地机械判定、置信 1.0——`KnowledgeQuizFeedbackWriter.kt:17,46-47`）
  **正是**"真实作答"，走的却是 chat-evidence 通道，于是照样被模型的 12h 冷却挡住；
- `LearnerChatEvidenceEntity.kt:11-13,40-42` 把 `source_kind` 称作"审计与批量撤销的锚点"，
  冷却查询却不用它。

### 2.2 实测（5/5 通过，真库 + 真写入口）

| 场景（同一 KC，窗口 12h） | 实测 |
|---|---|
| 模型写（MODEL_CHAT，accepted）→ **1h 后小测写** | `evidenceRecorded=false`，`rejectedReason=SAME_KC_IN_COOLDOWN`；账本只剩模型那一笔；小测落一条 `source_kind=KNOWLEDGE_QUIZ`、weight 0、rejected 的观察行 |
| 站点探针：`lastAcceptedChatEvidenceAtForKc` | 库里唯一 accepted 行是 **MODEL_CHAT** 时，查询**把它当冷却起点返回**；之后落一条更晚的 rejected 行，查询仍返回模型那一笔 → 缺的那一维**恰好是** `source_kind` |
| 反向：小测写 accepted → 1h 后模型写 | `Rejected(SAME_KC_IN_COOLDOWN)`（学生自己的客观作答挡掉模型下一笔掌握度证据） |
| 小测自锁 | t0 accepted → t0+12h−1 拒 → **t0+12h 放行**（判定是严格 `<`） |
| 影响复算（一晚讲题 3 个 KC + **当晚**3 题全对） | 客观证据落库 **0/3**；账本 3 笔（全是模型）；同样 3 题放到 **13h 后** → 落库 **3/3**，账本 6 笔（3 模型 + 3 小测） |

用户可见面（读码，非本轮执行）：`feature/review/.../KnowledgeReviewSessionScreen.kt:300` 把该拒因映射成
**"这个知识点刚刚复习过，这次先只作为观察"**——当挡路的是**模型的写**、学生根本没复习过时，这句话是错的；
讲题通道另有 `core/model/.../TutorToolTrace.kt:371`（"这个知识点刚记过一次，没有重复计入"）。

### 2.3 建议（留裁定，不改语义）

1. 把 12h 冷却限定在 `source_kind = 'MODEL_CHAT'`（与文档意图一致），让客观判定通道按其自身配额
   （会话/滚动窗上限）放行；或给 `KNOWLEDGE_QUIZ` 单独一个更短的自锁窗（"同一会话同一题重复提交"的防重
   已由幂等身份承担）。
2. 若维持通道无关，则用户可见文案应按**真实拒因来源**改写（现在会把模型的冷却说成"你刚复习过"）。
3. 被拒体量已被现有校准面记录（`ChatEvidenceGateCalibration.kt:94` 按拒因分桶），
   收窄前后可用它复核"被挡掉的是哪一路"；但请注意该校准面**不区分通道**，维持现状时建议先把
   `source_kind` 加进分桶维度，再谈用数据校准这个常数。

---

## ③ 答案键：错键之后，三种落点都不存在

**被测站点**：`core/model/.../Assessment.kt:77-91`（`KnowledgeQuizOutput.toTutorAssessmentItem`：
模型输出的 `correctChoiceId` 被搬进内存里的 `TutorAssessmentItem`），此后对错只在
`KnowledgeQuizFeedbackWriter.kt:46` 做 `selectedChoiceId == correctChoiceId` 的机械比较。

### 3.1 反例构造与实测（5/5 通过，真库 + 真写入口）

题面：A 客观正确、B 错误；**模型把键指到 B**。学生选 A（客观正确）：

| 测量 | 结果 |
|---|---|
| 判答 | `isCorrect = false` → 落 **NEGATIVE 0.35**（标准 lapse 档，confidence 1.0，理由"知识点复习作答（答错）"） |
| 账本里错判的身份 | 只有一条 `ChatEvidenceSubmitted`；**没有 attempt/快照事件** → attempt 侧的纠正设施（`AttemptCorrection` 一族）**没有可纠正的对象** |
| 落库行里有键/题面/选项吗 | 没有：`evidence_id = knowledge-quiz:<requestId>:<node>`（只由 通道+命名空间+节点 派生），其余 11 列全是方向/权重/理由/置信/时间/锚定等级 |
| 纠正①：同 requestId + 纠正后的键（冷却内） | 被 `SAME_KC_IN_COOLDOWN` 拒；因与既有行同 id，**连观察行都落不下来**，原行仍是那条 accepted 的错判 |
| 纠正②：换新身份 + 纠正后的键（冷却内） | 同样拒（只多一条 rejected 观察行，不进账本） |
| 纠正③：**13h 后** + 纠正后的键 | 新的一笔 POSITIVE 落库，但错判**不被替换、不被补偿、不被抵消**：两行两事件并存（append-only） |
| 撤销 | 无落点：端口上的 chat-evidence 面只有 `recordChatEvidence` + 读 + 计数（`ModelTaskDatabasePort.kt:149-175`），全仓对 `learner_chat_evidence` 没有 UPDATE/DELETE 的写路径 |
| 重放 | `LearningProjector.replay` **确定性复现**错判：`failureWeight = 0.35`（对照"键一开始就对"为 `0.0`）、`masteryScore` 严格更低、两次重放逐位相同 → 重放不可能"洗掉"它，只会再积一次分 |
| 附带测到的结构缝：**身份粘性** | 一条先被拒的写占住 `evidence_id`；冷却过期后**同一身份**的合法写通过门、写入口回报 `Accepted`，但 Room 的 IGNORE 把它静默吞掉——账本事件数不变、库里那一行仍是被拒状态（**静默丢失**） |

### 3.2 与本次任务书前提的差异（重要）

任务书写"答案键只在内存（`Assessment.kt:77-91`），无持久化"。这**对 HEAD（已提交车道）成立**，但
**工作区已经不同**：工作区里有一份**未跟踪、在建**的 AUD-02 改动
（`core/data/.../study/KnowledgeQuizItemStore.kt`，经 `RoomBackedStudyExperienceRepository.kt:202,731-748` 与
`core/domain/.../StudyExperienceRepository.kt:358-382` 接线），它把题面**连同答案键**落进
`assessment_item_snapshot.answer_spec_snapshot`（`correctChoiceId`）。
**但即便有了它，纠正仍没有落点**：该 store 的契约是**首见即真**——同身份的第二笔写入（哪怕键不同）
撞 `ImmutablePayloadConflictException`，代码注释明说"本题面**不是**被覆盖，而是回读既有行"
（`KnowledgeQuizItemStore.kt:27-29,74-78`）。也就是说：**键会被持久化，但持久化的键按设计不可改**。

（该 store 未被执行——它不在快照里，见 §4；上面是读码结论。）

### 3.3 建议（留裁定，不改语义）

1. **纠正语义需要一次产品裁定**：错键被纠正后，那笔基于错键的错判应当
   (a) 由一笔补偿性账本事件抵消（需要证据载荷里带上"题面/键版本"才能定位受影响的行），或
   (b) 在写路径上按"键已改判"单列一类事件，或
   (c) 至少让审计面能把证据行与题面快照对起来（现在证据行里没有题面 id）。
   三者都改变语义，故本测量不动实现。
2. **身份粘性（静默丢失）值得单独登记**：写入口对"落库被 IGNORE"无感知即回报成功，
   调用方（UI）会以为证据已记。这与 AUD-01 修的"同身份重放"是同一身份机制的另一面——
   建议至少让 `insertAsLedgerEvents` 把"accepted 被 IGNORE 掉"变成可观察结果（回报/日志），
   再谈谁该吃这个冲突。
3. 若采纳 AUD-02 的题面持久化，则"键不可改"这条要与上面第 1 条的裁定对齐：
   要么允许改判并留版本，要么明确规定"恢复只读回原题面、改判走新会话/新身份"。

---

## 4 限制与未验证

- **快照而非工作区直跑**：测量在 `D:\aud11-measure` 冻结快照上执行（§0.2）。被测六文件的代码与工作区
  等价（逐字节或仅 CRLF 差异），但快照**不含**工作区里在建的 AUD-02/AUD-05/KD-38 改动，
  因此：§3.2 的 `KnowledgeQuizItemStore` 与 §2 提到的任何在建改动都**只是读码，未执行**。
- **并发编辑**：测量期间工作区正被其他改动推进（我复制过的 78 个文件里有 9 个在几分钟内发生变化，
  并出现新的未跟踪文件）。本报告引用的行号取自**读取时**的工作区文本，代码指纹见 §0.2 的 md5。
- **未跑端到端链路**：②③ 直接驱动写入口（模型通道请求形状照抄 `RoomTutorToolRunner.kt:866-899`），
  未跑完整讲题工具环 / ViewModel / UI；"小测被拒后复习队列项是否保持未掌握"是**推断**（无掌握度移动 →
  排队依据不变），未测。
- **测试文件位置**：三个测试类只存在于快照目录，**未**复制进工作区（本次写入工作区源码被本地安全 hook 拦截；
  且工作区 `:core:data` 当前编译不过，复制进去也无法运行）。待该模块恢复可编译后，从上述快照路径
  原样拷入工作区同名相对路径即可运行——本报告的数字就是这批文件在快照上的产物，未做任何转写。
- **未验证**：AUD-02 store 的运行时行为；`ChatEvidenceGateCalibration` 对通道维度的分桶是否已在别处补齐；
  工作区在建改动合入后本报告 ①②③ 的结论是否仍然成立（那是下一次测量的输入）。

---

## 附：本报告用到的可复跑命令

```bash
# 快照
git worktree add --detach /d/aud11-measure a15aebb7
# （叠加 §0.2 的两项：ChatEvidenceDao.kt 用工作区版本；补上 KnowledgeMaterialContentAttribute enum）

# ①
cd /d/aud11-measure && ./gradlew :core:data:testDebugUnitTest \
  --tests "com.tingyun.smartmistakebook.core.data.study.TutorJudgedReviewKcGranularityAuditTest"

# ②③
cd /d/aud11-measure && ./gradlew :core:data:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=\
com.tingyun.smartmistakebook.core.data.study.KnowledgeQuizCooldownLayeringAuditTest,\
com.tingyun.smartmistakebook.core.data.study.KnowledgeQuizAnswerKeyAuditTest

# 结果读取
# JVM:   core/data/build/test-results/testDebugUnitTest/*KcGranularity*.xml
# 真机:  core/data/build/outputs/androidTest-results/connected/debug/*.xml
```
