# Elo 难度估计数学精读与 G5-A 实现规格（R4，驱动 T2）

- 日期：2026-10-25（批次 21 研究轨 R4）
- 状态：**研究产出 + T2 实现规格**。主会话裁定 G5 = 选项 A 先行（R5 提案 §3-A，`docs/research/2026-10-25-g5-adaptive-selection-proposal.md`），本文把该裁定的"proso Elo 数学"落到公式级与签名级。
- 范围纪律：本文只研究+规格，不改任何代码；所有 Gradle 门禁由脚本串行执行（本文未跑、也无需跑——纯数学/读码产出）。
- 检索通道：`gh api`（已认证）拉取 proso-apps 源码全文（本会话实读）；WebSearch 一轮定位背景论文（PDF 两通道均不可解析，见 §1.7）；项目内源码全部本会话实读。

---

## 0. 一页结论（供 T2 直接执行）

1. **移植对象 = `adaptive-learning/proso-apps` 的 `PriorCurrentPredictiveModel`**（`proso/models/prediction.py:164-236`，MIT），即其生产配置实际启用的模型（`testproject/proso_config.yaml:4-5`，默认超参零覆盖）。
2. **0.8 与 0.05 的语义**（裁定引用的"更新步长"）：它们不是两个独立的固定步长，而是**动态步长 α(n) = 0.8/(1+0.05·n) 的分子与阻尼常数**（`prediction.py:166` `elo_alpha=0.8, elo_dynamic_alpha=0.05`；`:228` 组装；`:229-236` 分别作用于全局能力 prior_skill 与题目难度 difficulty，且**只在 (用户，题) 的首答**更新）。常量步长的旧形态（K_A=0.8 / K_D=0.05 各自固定）在被引用的旧仓 `anti-boring-framework` 上已不可达（404，§1.8）——**按裁定采 R5 提案 §2.4 描述的动态 α(n) 形态移植**。
3. **猜测修正/机会水平**：预测公式 `p = guess + (1−guess)·sigmoid(skill)`，`guess = 1/选项数`（`prediction.py:353-360`）——多选项题的预测从随机基线（机会水平 1/n）而非 0 起步。选项数未知时 proso 自身的降级路径是 `guess = 0`（关闭修正，`:356-359`），照搬。
4. **"缺时间字段"降级实际不存在缺口**：`PriorCurrent` 数学**不消费单题作答时长**（`update_phase` 全程不读 `answer_time`，`prediction.py:216-236`）；它唯一需要的时间量是**两次作答事件间的墙钟间隔** `seconds_ago`（`:198`），由台账 `occurredAtEpochMillis` 相邻差值现算即可。本项目知识点测验通道（`learner_chat_evidence`）确实没有时长列（`LearnerChatEvidenceEntity.kt:30-73`），但这不构成缺口；时长只在 practice-unit 通道存在（`attempt_event.duration_seconds`，`LearningEntities.kt:341`），不属本流。
5. **Elo 状态 = 台账前缀的纯函数**：有效作答流（`ChatEvidenceSubmitted` 中 `knowledge-quiz:` 通道 + `EvidenceCorrection` VOID/REPLACE 解析）按 `eventSequence` 折叠；每事件 O(1)（1 次 exp + 常数次 HashMap 操作），1 万事件 ≈ 单位数毫秒 ART；按 `(账本状态, 最大 eventSequence)` 水位缓存。GAP/CONFLICT → fail-closed 不出预测（不猜）。
6. **全部超参数值标 `PENDING_CALIBRATION`**（proso 生产值仅作初值；真实效果 UNVERIFIED，Wave X 校准线）。

---

## 1. proso-apps Elo 数学精读（逐公式，file:line）

### 1.1 仓库与许可核验（本会话 `gh api` 实测）

- `gh api repos/adaptive-learning/proso-apps` → `license.spdx_id = "MIT"`，`pushed_at = 2022-11-22`，stars = 2。与 R5 提案 §2.4 记录一致（社区维护度低，按"参考实现+论文背书"对待，不作依赖——本移植是**约 60 行纯函数重写**，不引 Python 依赖）。
- `gh api orgs/adaptive-learning/repos` 全列（20 仓）无 anti-boring-framework（§1.8）。
- 原文获取命令：`gh api repos/adaptive-learning/proso-apps/contents/proso/models/prediction.py -H "Accept: application/vnd.github.raw"`（master 分支，全文 371 行本会话实读，下文行号以此为准）。

### 1.2 三相位端口（`prediction.py:6-108`）

`PredictiveModel` 把模型生命周期钉成 `prepare`（读环境，`:33-54`）→ `predict`（答前预测，`:60-84`，返回 [0,1] 正确率）→ `update`（写回，`:90-108`）三相；`predict_and_update`（`:26-31`）保证**预测永远先于更新**——这是"重放时每事件的历史预测可精确重建"的结构基础。

### 1.3 状态与初始化（`PriorCurrentPredictiveModel.prepare_phase`，`:173-184`）

三状态，键控如下：

| 状态 | 键 | 初值 | 行号 |
|---|---|---|---|
| `prior_skill` | 用户（全局能力） | `default=0` | `:175` |
| `difficulty` | 题（item） | `default=0` | `:176` |
| `current_skill` | (用户， 题) | 无值 = None | `:177` |

`use_prior = current_skill is None`（`:178`）——(用户，题) 无历史时走先验分支。首答计数：`user_first_answers` / `item_first_answers`（`:180-181`，即该用户/该题的"首答次数"——对 (用户，题) 的第一次作答会让它们各 +1，用于动态步长）。

### 1.4 预测公式（`predict_phase` `:194-203` + `predict_simple` `:353-364`）

```
首答（current_skill 缺）：skill = prior_skill − difficulty                     # :195-196
重答：seconds_ago = 提问时刻 − 该(用户,题)上次作答时刻                         # :198, :367-371
      skill = current_skill + time_shift / max(seconds_ago, 0.001)            # :199
p = guess + (1 − guess) · sigmoid(skill)                                      # :360
sigmoid(x) = 1 / (1 + e^(−x))                                                 # :363-364
guess = 1 / number_of_options   （选项数缺失或为 0 时 guess = 0，关闭修正）    # :356-359
```

- **机会水平调整**即 `guess = 1/n`：预测以"全靠猜的正确率 1/n"为下锚，sigmoid 值在 [0,1] 内插值映射到 [1/n, 1]。n=2 时下锚 0.5，n=4 时 0.25。
- `time_shift=80.0`（秒，`:166`）：current_skill 随时间向"该(用户,题)作答后的水平 + 小幅回升"收敛——80 秒前 +1.0，8000 秒前 +0.01，量纲为 skill 单位（logit，**无 400/ln10 比例尺**）。
- 调用点把 `number_of_options = len(kwargs['options'])` 传入（`:202`、`:266`）——即 UI 选项个数。

### 1.5 更新公式（`update_phase` `:216-236`）

记 `result = correct ? 1 : 0`，`prediction` 为答前三相位算出的 p：

```
# (用户,题) 局部技能：每次作答都更新，步长非对称（PFAE 族参数，:222-225）
答对：current_skill += pfae_good · (result − prediction)      # pfae_good = 3.4
答错：current_skill += pfae_bad  · (result − prediction)      # pfae_bad  = 0.3（负向小幅拉低）

# 全局能力与题目难度：仅当 use_prior（该(用户,题)首答）才更新（:227）
α(n) = elo_alpha / (1 + elo_dynamic_alpha · n)                 # :228，n = 首答计数
prior_skill += α(user_first_answers) · (result − prediction)   # :231-233
difficulty  −= α(item_first_answers) · (result − prediction)   # :234-236
```

- **0.8 的语义**：`elo_alpha = 0.8`（`:166`）——动态步长的**分子**（首答时的全量步长）。
- **0.05 的语义**：`elo_dynamic_alpha = 0.05`（`:166`）——分母里的**阻尼**：第 n 次首答时步长衰减为 0.8/(1+0.05n)。新用户/新题收敛快、老用户/老题趋稳——论文语境里的"新题快速收敛、冷启动友好"性质（R5 提案 §2.4 引）。
- **非对称步长**（3.4 vs 0.3）：答对一次性大幅上调局部技能、答错只小幅下调——防"一次答错抹掉多次答对"的过反应。两个值都在 `__init__` 默认参数里（`:166`）。
- **难度的推进方向**：`difficulty −= α·(result−prediction)`——答对比预测好（残差>0）→ 难度下降（题变"容易"）；答对比预测差（残差<0）→ 难度上升。能力侧同号相加。
- **难度只在首答更新**（`use_prior` 门，`:227`）：重答的难度信息全部进 (用户,题) 的 current_skill，不再污染全局难度——这是"难度估计不被同一学生的重复作答漂移"的关键设计。

### 1.6 生产配置（本会话实拉）

`testproject/proso_config.yaml:4-5`：`predictive_model.class = proso.models.prediction.PriorCurrentPredictiveModel`，**无 parameters 覆盖**——即上述默认值（0.8/0.05/3.4/0.3/80.0）就是该仓生产运行值。这是"别家生产值"的出处，落码标 `PENDING_CALIBRATION`。

### 1.7 与论文的关系（背景印证，非公式来源）

- 论文 Pelánek (2016)《Applications of the Elo Rating System in Adaptive Educational Systems》存在（WebSearch 定位 `https://www.fi.muni.cz/~xpelanek/publications/CAE-elo.pdf`，2016，cited by 281），并确认代码中 `pfae_*` 命名对应 Papoušek et al. (2014) 的 PFAE 扩展族。
- **PDF 原文公式 UNVERIFIED**：WebFetch 返回 "Unsupported content type: application/pdf"；web_reader 返回 -500（两通道，本会话实跑）。**本文全部公式以仓库源码为唯一权威**（对移植而言源码本就是权威源），论文只作族背景，未作为任何公式出处引用。

### 1.8 anti-boring-framework 不可达 + 0.8/0.05 两义性澄清

- `gh api repos/proso-apps/anti-boring-framework` → 404；`gh search repos "anti-boring-framework"` → 空；`adaptive-learning` org 全列无此仓（约 2 轮检索后停止，按"缺口最多 3 轮"纪律）。结论：**该旧仓已删除或转私有，无法当前取证**；R5 提案 §2.4 当时的拉取记录（`proso/models/prediction.py`）指向的就是本节所精读的 proso-apps，两处一致。
- 含义：若按旧仓语义把 0.8/0.05 理解为"能力步长 0.8、难度步长 0.05 的两个固定 K"，**与当前可取证的 proso-apps 源码不符**（那里 0.8/0.05 是动态 α(n) 的分子/阻尼）。裁定文本"proso 超参（0.8/0.05/猜测修正）"以 R5 提案 §2.4 的描述为准，而 §2.4 与本节源码精读一致（α(n)=0.8/(1+0.05n)）。**T2 按动态 α(n) 形态实现**；若日后旧仓恢复可达且形态不同，属新裁定项，不在本批。

### 1.9 明确不移植的变体

`AlwaysLearningPredictiveModel`（`:239-292`）：层级父题传播（`level_decay = 1/3^level`，`:282`），非生产配置所启用；本项目知识点无同类父子权重面，不移植。`ShiftedPredictiveModel`（`:321-350`）为预测偏移装饰器，无需求，不移植。

---

## 2. 映射到本项目证据流

### 2.1 作答事件台账读取面（全部本会话实读）

Elo 的作答流**不是**新建采集，而是既有学习台账的一个投影切面：

| 环节 | 来源 | 位置 |
|---|---|---|
| 台账读口 | `StudyDatabasePort.loadLearningLedger(learnerId): LearningLedgerRead` → `validPrefix` + `status`（COMPLETE/GAP/CONFLICT） | `core/database/.../dao/ProjectionTransactionDao.kt:744-785` |
| 作答事件 | `ChatEvidenceSubmitted(evidenceId, conversationId, knowledgeNodeId, direction, weight, reasonMarkdown, confidence, occurredAtEpochMillis, eventSequence)` | `core/model/.../LearningState.kt:498-541` |
| 定向修正 | `EvidenceCorrection(correctionId, targetEvidenceId, kind=VOID/REPLACE, replacementDirection, replacementWeight, …)`；VOID=从有效集排除，REPLACE=按新载荷在**原作答位置与时间**重算；遇它必须全量重放 | `core/model/.../LearningState.kt:556-627` |
| 通道识别 | 证据 id 前缀 `knowledge-quiz:`（`KnowledgeEvidenceChannel.KNOWLEDGE_QUIZ.idPrefix`）；`chat-ev:` = MODEL_CHAT | `core/data/.../study/KnowledgeEvidenceWriter.kt:258-270` |
| 写入链（参考，不改） | `KnowledgeQuizFeedbackWriter.submit` 本地机械判分 `isCorrect = selectedChoiceId == correctChoiceId` → `KnowledgeEvidenceWriter.write` 入账 | `core/data/.../study/KnowledgeQuizFeedbackWriter.kt:75-76, 232-256` |
| 会话解析 | `knowledgeQuizSessionIdOf("knowledge-quiz-review:<sessionId>") → sessionId` | `core/data/.../study/KnowledgeQuizEvidenceAudit.kt:126-131` |
| 题面快照读口 | `KnowledgeQuizItemStore.readSnapshot(session, node)` → `item.choices`（`options_snapshot` 列）、`correctChoiceId`、`verificationStatus`；快照行身份 `(learner, 会话, 知识点)` | `core/data/.../study/KnowledgeQuizItemStore.kt:83-100, 273-277` |

### 2.2 三输入（对错 / 选项数 / 时间）的来源与降级矩阵

| 输入 | 从哪读 | 降级（读不到时） | 依据 |
|---|---|---|---|
| **对错** | 台账 `ChatEvidenceSubmitted.direction`（POSITIVE=答对 / NEGATIVE=答错），只取 `knowledge-quiz:` 通道的**已接受**事件 | 无需降级：台账只含已接受事件；被拒观察行天然不在 `validPrefix` 里 | `LearningState.kt:498-541`；`KnowledgeQuizFeedbackWriter.kt:30`（confidence=1.0，本地机械判定） |
| **选项数 n** | 该事件 `conversation_id` 解出会话（§2.1）→ `assessment_item_snapshot` 该 `(learner, 会话, 节点)` 行的 `options_snapshot` → `item.choices.size`（快照 init 保证 ≥2） | 快照行缺失/损坏/解不开 → **该事件按 n 未知处理：guess=0，猜测修正关闭**（proso 自身路径，`prediction.py:356-359`）——逐事件降级，不全局失效 | `KnowledgeQuizItemStore.kt:42-100, 212-213`；`KnowledgeQuizEvidenceAudit.kt:109-124`（对不上的出口都留 null，不猜） |
| **答题时间** | 见下 | 见下 | — |

**"缺时间字段"的降级（问题的正面回答）**：

1. proso `PriorCurrent` 数学**不消费单题作答时长**——`update_phase`（`prediction.py:216-236`）不读 `answer_time` 参数；时长只在 `PredictiveModel.predict_and_update` 签名上存在而未被该模型使用。因此知识点测验通道没有时长列（`LearnerChatEvidenceEntity.kt:30-73` 逐列核对无 duration）**不构成缺口**。
2. 数学唯一需要的时间量是**重答分支的 `seconds_ago`**（`prediction.py:198`）= 本次提问时刻 − 该(用户，节点) 上次作答时刻。在本项目里两者都取台账 `occurredAtEpochMillis`（事件自身携带），相邻差值现算——**台账原生满足，零新采集**。
3. 首答（该(用户，节点) 无前序事件）走 `use_prior` 分支，数学上不存在时间项（`prediction.py:195-196`）——这是 proso 自己的"缺上次时间"降级，照搬。
4. 若某事件时间戳异常（≤0 或倒挂）：按"无有效 last_time"处理，即视同首答分支（不猜插值），并在规格测试里钉住。
5. 未来若 Wave X 校准需要"快答/慢答"扩展（论文族的另一分支）：需先在 quiz 通道新增时长采集（新列+迁移），**属后续批裁定项，本批不做**——此处如实登记，不预设。

### 2.3 修正语义在重放中的处理（确定性关键）

台账前缀里 `EvidenceCorrection` 可能出现在目标之后（它只追加不改写）。重放前先做一次**有效性解析**（与掌握度投影同语义，`LearningState.kt:556-627` KDoc）：

- `VOID(target)`：目标从有效作答流**整条排除**；
- `REPLACE(target)`：目标保持**原 eventSequence 位置与原 occurredAt**，方向/对错换成 `replacementDirection`（POSITIVE=对 / NEGATIVE=错；LOAD 规则要求 REPLACE 必带非 NONE 方向）；
- 链式（更正的更正）按台账序逐条应用——后一条作用于前一条的结果；
- 解析后的有效流按 `eventSequence` 升序折叠。**同一前缀 → 同一有效流 → 同一状态，逐位不变**（与 `correctionLedgerOrder` 的"只接受完整前缀"口径一致，`KnowledgeQuizFeedbackWriter.kt:441-457`）。

### 2.4 范围排除（每项一次指认）

| 排除项 | 理由 |
|---|---|
| `MODEL_CHAT`（`chat-ev:`）通道事件 | 模型判词方向，非客观对错；Elo 需要机械判定的 result。计入会把判断置信混进对错流 |
| `Attempt` / `attempt_event`（practice-unit 作答，含 `duration_seconds`，`LearningEntities.kt:307-342`；账本事件 `LearningState.kt:365`） | 题目身份（problemRevisionId/practiceUnitId）与 quiz 的 (会话，节点) 不同构；并入是语义扩展，留给校准批裁定。已在 §2.2.5 登记时长采集差异 |
| `review_log`（`LearningEntities.kt:1000+`，duration_ms 在场） | 该表注释明言"feeds the evaluation harness and the local parameter optimizer, **never the projector**"——非台账，不属重放源 |
| G8 回灌 LOCAL_CHECKED 证据 | 落 `review_log` 侧（同上），不在 chat 台账；同上排除 |

---

## 3. 惰性重放派生的确定性设计

### 3.1 纯函数定义

```
EloState(learner) = fold( effectiveStream(loadLearningLedger(learner).validPrefix) , EloState.INITIAL )
```

- `effectiveStream`：过滤 `knowledge-quiz:` 通道 → 解析 VOID/REPLACE（§2.3）→ 按 `eventSequence` 排序；
- `fold` 的每步 = §1.4 预测 + §1.5 更新（预测用**当时**的状态，即答前三相位的 p）；
- **零新表、零写入**：状态只活在读取方的内存里；台账追加后水位变化 → 下次预测重新折叠。这正是"固定账本重放逐位不变"纪律的实例：状态是台账前缀的纯函数，无隐藏写入通道。

### 3.2 重放复杂度 O(n) 量化

| 量 | 值 | 依据/推算 |
|---|---|---|
| 每事件常数 | 1 次 `exp`（历史预测 sigmoid）+ ~4 次 HashMap get/put + 1 次 `startsWith` 前缀判 + 少量算术 | `prediction.py:360,363-364` 折叠展开；无排序（读口已按序）、无二次扫描 |
| 时间 | O(E)，E = 有效 quiz 作答事件数 | 单遍折叠 |
| 空间 | O(U + N)，U=学习者数（单学习者=1）、N=触及的节点数 | 每节点 difficulty/current_skill/last_time/首答计数四张小表 |
| 量级预期 | 台账设计量级"万条/年"（`LearnerChatEvidenceEntity.kt:15-17` 索引注释）；E=10⁴ → **≈10⁴ 次 exp+哈希 ≈ 单位数毫秒（ART）** | 每事件 ~0.5–2 µs 估值；一次出题决策只做一次折叠 + 每候选一次 O(1) 预测 |
| 调用频度 | 每次进入出题决策面一次（非渲染帧、非作答热路径） | T2 接线点在选题决策，不在列表项 |

### 3.3 缓存策略（透明，不影响确定性）

- 缓存键 = `(ledgerStatus == COMPLETE, max eventSequence of validPrefix)`；命中且相等 → 直接用缓存状态；键变化 → 重折叠覆盖。
- 依据：台账 append-only，且任何 `EvidenceCorrection` 本就触发既有机制的全量重放（`LearningState.kt:556-627`）——缓存不可能藏住修正。
- 归属：缓存实例放在装配器（core:data，会话/仓库作用域），core/domain 的纯折叠器**无状态无缓存**（保持纯函数可测）。

### 3.4 超预算登记线（计划 §5 风险预登记的执行口径）

E > 50 000（或实测单次折叠 > 50ms，以设备实测为准）→ **不在本批落表**，登记"增量 Elo 状态表"方案呈后续批（裁定要点：何时落、写口、与全量重放的互证）。计划原文："超预算则登记增量表方案呈后续批，不在本批擅自落表"（plan §5）。

### 3.5 确定性边界（如实）

- **同引擎 + 同前缀 → 逐位相同**：折叠是单线程、固定顺序、无并发聚合的标量 double 运算，Kotlin/JVM 与 ART 对标量浮点均 IEEE-754 严格求值。
- **跨引擎残差**：`exp` 的 libm 实现在桌面 JVM 与 ART/bionic 间可能差 ≤1 ulp，跨设备重放状态可在末位抖动。影响面 = 预测点值末位 → 走廊比较在**恰好在边界值上**时理论上可能翻转。缓解（T2 落地项，§4.4）：构造 `PredictedCorrectnessInterval` 前把点值钉到小数 6 位（`roundTo(6)`）——消灭"跨引擎末位抖动翻转走廊判定"这一具体失败；折叠内部保持全精度，不引入额外机制。
- GAP/CONFLICT：`loadLearningLedger` 非 COMPLETE → **不出预测**（候选 `predictedCorrectness = null` → 选择器既有 fail-closed `PREDICTION_UNKNOWN_STALE_OR_OUTSIDE_CORRIDOR`，`AdaptiveQuestionSelector.kt:195`）。与 `correctionLedgerOrder`"读不出完整前缀不猜"同口径（`KnowledgeQuizFeedbackWriter.kt:445-449`）。

---

## 4. 实现规格（T2 照做；签名级）

### 4.1 core/domain 新文件 `EloCorrectnessPredictor.kt`

```kotlin
package com.tingyun.smartmistakebook.core.domain

/**
 * proso-apps PriorCurrentPredictiveModel 的 Kotlin 移植（MIT，出处：
 * https://github.com/adaptive-learning/proso-apps proso/models/prediction.py:164-236,353-364；
 * 移植口径见 docs/research/2026-10-25-elo-predictor-spec.md §1）。
 * 纯函数：无 IO、无时钟读取、无全局态。状态由 [replay] 从作答流折叠派生。
 */
class EloCorrectnessPredictor(
    private val hyper: EloHyperparameters = EloHyperparameters.PROSO_PRODUCTION_DEFAULTS,
) {
    /** LearningCoreVersions 新常量，见 §4.5。 */
    val modelVersion: String   // = LearningCoreVersions.PREDICTOR_ELO

    /** 台账前缀的纯折叠（§3.1）。events 必须已按 eventSequence 升序；列表可为空。 */
    fun replay(events: List<EloAnswerRecord>): EloLearnerState

    /** 点预测 p = guess + (1−guess)·sigmoid(skill)；guess = 1/optionsCount（null/0 → 0）。 */
    fun predictCorrectness(
        state: EloLearnerState,
        knowledgeNodeId: String,
        optionsCount: Int?,
        atEpochMillis: Long,          // 决策时刻，用于 seconds_ago
    ): Double

    /** 选择器消费的区间：中点 = predictCorrectness，半宽 = h(该节点已计作答数)（§4.4）。 */
    fun predictInterval(
        state: EloLearnerState,
        knowledgeNodeId: String,
        optionsCount: Int?,
        decisionAtEpochMillis: Long,
    ): PredictedCorrectnessInterval   // 既有类型 AdaptiveQuestionSelector.kt:27-52
}
```

```kotlin
/** 全部数值为 proso 生产默认值（prediction.py:166 + proso_config.yaml:4-5）——PENDING_CALIBRATION。 */
data class EloHyperparameters(
    val eloAlpha: Double,            // 0.8  α(n) 分子           PENDING_CALIBRATION
    val eloDynamicAlpha: Double,     // 0.05 α(n) 阻尼           PENDING_CALIBRATION
    val pfaeGood: Double,            // 3.4  答对局部步长        PENDING_CALIBRATION
    val pfaeBad: Double,             // 0.3  答错局部步长        PENDING_CALIBRATION
    val timeShiftSeconds: Double,    // 80.0 重答回升项分子（秒） PENDING_CALIBRATION
    val intervalHalfWidthInitial: Double,   // 0.5  h(0)      PENDING_CALIBRATION（新判据，§4.4）
    val predictionValidityMillis: Long,     // 24h  validUntil = 决策时刻 + 此值  PENDING_CALIBRATION
) {
    companion object {
        /** proso 生产默认值快照；别家生产值，未在本项目数据上验证（Wave X 校准）。 */
        val PROSO_PRODUCTION_DEFAULTS = EloHyperparameters(/* 如上 */)
    }
}

/** 一条有效作答（core/data 装配器从台账解析产出；core/domain 不碰台账类型）。 */
data class EloAnswerRecord(
    val evidenceId: String,
    val knowledgeNodeId: String,
    val isCorrect: Boolean,           // direction==POSITIVE（REPLACE 已在装配侧解析）
    val optionsCount: Int?,           // null = 题面快照读不回 → 该事件 guess=0
    val occurredAtEpochMillis: Long,  // 台账事件时间（REPLACE 保持原时间）
    val eventSequence: Long,          // 台账序：重放唯一序
)

/** 折叠产物；对 (学习者, 节点) 两级键——单学习者端口内 user 维度退化为标量。 */
data class EloLearnerState(
    val priorSkill: Double,                              // 初值 0.0（prediction.py:175）
    val difficultyByNode: Map<String, Double>,           // 初值 0.0（:176）
    val currentSkillByNode: Map<String, Double>,         // 缺键 = use_prior 分支（:177-178）
    val lastAnswerAtByNode: Map<String, Long>,           // seconds_ago 用（:183, :198）
    val answeredCountByNode: Map<String, Int>,           // 区间半宽 h(n) 与诊断用
    val firstAnswerCountByUser: Int,                     // user_first_answers（:180）
    val firstAnswerCountByNode: Map<String, Int>,        // item_first_answers（:181）
) {
    companion object { val INITIAL = EloLearnerState(/* 全空/0 */) }
}
```

折叠规则（`replay` 内部，逐条对齐 §1 公式）：

1. `prediction_i = guess_i + (1−guess_i)·sigmoid(skill_i)`，`skill_i` 按 §1.4 两分支（`currentSkillByNode` 缺键 → `priorSkill − difficultyByNode[node]`；有键 → `currentSkill + timeShift/max(seconds_ago, 0.001)`，`seconds_ago = (record.occurredAt − lastAnswerAt)/1000.0`，缺 lastAnswerAt → 视同缺 currentSkill 走先验分支——§2.2.4 的显式降级）。
2. `currentSkillByNode[node] += (if (isCorrect) pfaeGood else pfaeBad) * (result − prediction_i)`（result = 1.0/0.0），每次都更新。
3. 若该 (learner, node) 此事件为**首答**（`currentSkillByNode` 更新前缺键）：`a = eloAlpha/(1 + eloDynamicAlpha·firstAnswerCount)`；`priorSkill += a·(result−prediction_i)`；`difficultyByNode[node] −= a·(result−prediction_i)`；随后计数 +1。对齐 `prediction.py:227-236`（proso 的 user/item 两个首答计数在本端口合并为节点级一个 n，因为学习者单例——**这是移植的显式简化**：单学习者设备上 α 对 user 与 item 同 n，数学等价）。
4. `lastAnswerAtByNode[node] = record.occurredAtEpochMillis`；`answeredCountByNode[node] += 1`。
5. 输出不做非负/上限截断（proso 亦不截断；sigmoid 自然限幅）。

### 4.2 core/data 新文件 `AdaptiveSelectionAssembler.kt`

```kotlin
package com.tingyun.smartmistakebook.core.data.study

/**
 * G5-A 读侧装配（R5 提案 §3-A）：候选装配 + 预测填充两个未绑定输入端口的生产实现。
 * 只读：loadLearningLedger / readChatEvidenceByLearner / findAssessmentItemSnapshot。
 * 零新表、零写路径。
 */
internal class AdaptiveSelectionAssembler(
    private val database: StudyDatabasePort,
    private val learnerId: String,
    private val predictor: EloCorrectnessPredictor,
) {
    /**
     * 台账 → [EloAnswerRecord] 有效流（§2.2/§2.3）：
     * 1) loadLearningLedger；status != COMPLETE → 返回 null（调用方对全部候选不出预测）；
     * 2) 过滤 ChatEvidenceSubmitted 中 evidenceId 以 "knowledge-quiz:" 开头者；
     * 3) EvidenceCorrection 解析 VOID/REPLACE（链式按台账序）；
     * 4) 每事件解析会话（knowledgeQuizSessionIdOf）→ findAssessmentItemSnapshot → choices.size；
     *    快照读不回 → optionsCount = null；
     * 5) 按 eventSequence 升序返回。
     */
    suspend fun loadEloAnswerStream(): List<EloAnswerRecord>?

    /** 复用折叠态 + 水位缓存（§3.3）；装配器实例作用域内 memoize。 */
    suspend fun currentEloState(): EloLearnerState?

    /**
     * 候选装配：题面快照池 → AdaptiveQuestionCandidate（含 predictedCorrectness 填充）。
     * 候选池来源（快照池语义 = "跨会话复用旧题面"是否允许，是 R5 提案 §5 登记的未裁项，
     * **本批只装配当次会话池**：readSnapshot(当前会话, 节点) 可读即候选；跨会话复用留裁定）。
     */
    suspend fun assembleCandidates(
        sessionId: Long,
        targetKnowledgeNodeIds: Set<String>,
        decisionAtEpochMillis: Long,
    ): List<AdaptiveQuestionCandidate>
    // 失败语义：currentEloState() == null → 全部候选 predictedCorrectness = null（fail-closed）
}
```

装配器与选择器的衔接（T2 生产调用点）：`feature/review` 的出题决策处（既有 seam `KnowledgeReviewSessionViewModel.knowledgeQuizConversationId()`，`feature/review/.../KnowledgeReviewSessionViewModel.kt:283,307`——**该文件是他线热区，本批不写**；接线按计划"生产调用点优先干净 seam，必要时登记一行接线遗留"）。装配器产出的候选列表填入 `AdaptiveSelectionRequest.candidates`，选择器本体一行不改（裁定硬边界①）。

### 4.3 版本登记（T2 必做，缺一算未完成）

1. `LearningCoreVersions` 新增 `const val PREDICTOR_ELO = "predictor-elo-v1"`（既有 `PREDICTION_INTERVAL = "prediction-interval-v1"` 在 `LearningCoreVersions.kt:108` 同级；若 `SELECTOR_COMPOSITE`（`:124-125`）语义上应含新因子，则同批 bump 并触发既有全量重放机制——**注意**：Elo 状态是读侧派生、不进投影快照，是否构成 SELECTOR_COMPOSITE 分量由 T2 按其 bump 规则判定并留痕；判定不清则先只加常量、不动 COMPOSITE）。
2. `docs/research/algorithm-version-ledger.md` 追加条目（算法台账既有纪律）。
3. 决策台账登记：proso 超参 PENDING_CALIBRATION + §2.2.5 时长扩展登记项。

### 4.4 区间半宽规则（新判据——指认它消灭的失败）

`PredictedCorrectnessInterval` 是既有契约（`AdaptiveQuestionSelector.kt:27-52`），Elo 点预测必须变成区间才能进走廊判据。proso 原始输出是点值，**没有区间**——本规则是移植的必要补全，不是多余机制：

- **它消灭的失败**：① 点值无法构造 `PredictedCorrectnessInterval`（类型不可能实例化）；② 冷启动即进走廊：新节点 n=0 时点预测 = 1/n + (1−1/n)·0.5（如 4 选项 = 0.625，恰落在 FOUNDATION 走廊 0.55–0.85 内，`AdaptiveQuestionSelector.kt:244-252`）——没有宽区间拦住，一道从未答过的题会被直接选中，违反"证据不足 fail-closed"的产品纪律。
- **规则**：`h(n) = clamp(intervalHalfWidthInitial / sqrt(1 + n), 0.05, 0.5)`，n = 该节点已计作答数（`answeredCountByNode`）。区间 = `[roundTo6(p−h), roundTo6(p+h)]` 夹到 [0,1]；`generatedAt = decisionAtEpochMillis`，`validUntil = generatedAt + predictionValidityMillis`，`modelVersion = PREDICTOR_ELO`。
- **效果**：h(0)=0.5（全宽 → 走廊必拒 → fail-closed）；h≈0.15 时 FOUNDATION 走廊可过（约 n≥11 次作答）；TRANSFER 走廊（0.35–0.75）约 n≥6。**走廊打开节奏 = 校准手柄**，两个常数都标 PENDING_CALIBRATION。
- **roundTo6**：§3.5 的跨引擎 ulp 钉；只钉区间构造，不钉折叠内部。

### 4.5 fail-closed 矩阵（T2 测试断言的直接来源）

| 情形 | 行为 |
|---|---|
| 台账 GAP/CONFLICT/读失败 | 全候选 `predictedCorrectness = null` → 选择器既有 `PREDICTION_UNKNOWN…` |
| 节点零作答 | 点预测 = 机会锚定值（§4.4 效果段），h=0.5 → 走廊拒 |
| 事件题面快照读不回 | 该事件 guess=0（修正关闭），其余事件不受影响 |
| 候选当次快照读不回 | 该候选 `optionsCount=null` → 点预测无修正；走廊照常评估 |
| 时间戳异常（≤0/倒挂） | 该(节点)视同无 last_time → 先验分支 |
| 所有超参 | 取 `PROSO_PRODUCTION_DEFAULTS`，逐字段标 PENDING_CALIBRATION |

### 4.6 测试计划（先红后绿；映射验收条件）

1. **公式金样**：手工小序列（2–3 事件）+ 手算期望值（对 §1.4/§1.5 每条公式各至少一断言：首答先验分支、重答 seconds_ago 分支、α(n) 首答门、pfae 非对称、guess=1/n、guess=0 降级）。
2. **纯函数性**：同一 events 列表 replay 两次 → 状态逐字段相等；replay 不改输入列表。
3. **修正语义**：VOID 后目标不贡献；REPLACE 后按新对错在**原序列位置**生效（交换 REPLACE 与后续事件的输入顺序不影响结果——顺序由 eventSequence 唯一决定）。
4. **降级**：optionsCount=null；GAP → 装配器返回 null → 候选无预测；时间戳异常。
5. **确定性/缓存透明**：水位不变命中缓存与强制重折叠结果逐位一致。
6. **冷启动 fail-closed**：n=0 区间必被 FOUNDATION 与 TRANSFER 走廊拒绝（与 `AdaptiveQuestionSelector` 既有单测同文件族）。

### 4.7 新增物 → 被消灭失败的对照（新增必先指认）

| 新增 | 消灭的具体失败 |
|---|---|
| `EloCorrectnessPredictor` + 超参数据类 | 预测生产端口缺失 → `predictedCorrectness` 永无来源 → 走廊判据恒拒 → 选择器空转（R5 提案 §1.2②/§3-A③）；G5 验收门"G5 有生产调用点"无实现路径（new-stages-spec.md:91） |
| `AdaptiveSelectionAssembler` | 候选装配端口缺失（R5 提案 §1.2①） |
| 区间半宽规则 h(n) | 点值无法实例化既有区间契约 + 冷启动裸点值误入走廊（§4.4） |
| roundTo6 钉值 | 跨引擎 exp 末位差在走廊边界翻转判定的可能（§3.5） |
| 水位缓存 | 每决策全量重放的重复耗时（同前缀重复折叠）——透明优化，不影响任何输出 |
| （不加的东西） | 无新表、无 ModelTaskKind、无提示词、无请求 schema、无 UI、无写路径——选项 A 的裁定范围 |

---

## 5. 待验证清单（如实）

1. **proso 超参在本项目数据分布上的真实效果 UNVERIFIED**（0.8/0.05/3.4/0.3/80.0 是别家生产值；Wave X 校准线的既有登记项）。本批落码值全部标 PENDING_CALIBRATION。
2. **Pelánek (2016) 论文原文公式 UNVERIFIED**：PDF 两通道（WebFetch 内容类型不支持 / web_reader -500）本会话均不可解析；公式权威 = 仓库源码（对移植而言即足够）；论文只作族背景（PFAE 命名对应 Papoušek et al. 2014，来自搜索摘要，**单一来源待验证**）。
3. **anti-boring-framework（固定 K 形态的旧实现）当前不可达**（404+搜索空，2 轮检索）；"0.8/0.05 = 固定双 K"的另一种理解**无法取证也无法证伪**，按 §1.8 以可取证的动态 α(n) 形态实现，已留裁定注记。
4. **跨会话复用旧题面的候选池语义**：R5 提案 §5 既有登记项，本规格未代裁（装配器本批只装当次会话池）。
5. **折叠性能的设备实测**（§3.2 是推算量级，非实测）；§3.4 的 50ms/50k 阈值在设备轨验证时回填实测值。
6. **`SELECTOR_COMPOSITE` 是否应含 PREDICTOR_ELO 分量**：按 §4.3.1 由 T2 判定留痕（其 bump 规则的本会话未全读，不代判）。
7. **单学习者 α 合并简化**（§4.1.3）：数学上单设备单学习者等价，但若未来多学习者共享节点难度（当前架构 learner_id 分库，不存在），需回看该简化。

## 6. 来源清单

项目内（本会话实读，行号为当前工作树）：

- `core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/AdaptiveQuestionSelector.kt`（预测区间契约 :27-52、走廊 :238-253、fail-closed :195、版本 :300）
- `core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/LearningCoreVersions.kt`（:108 PREDICTION_INTERVAL、:124-125 SELECTOR_COMPOSITE）
- `core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/HLRPredictionAuditService.kt`（shadow 预测+审计 sink 既有形态，对齐参考）
- `core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/LearningState.kt`（:339 台账事件接口、:365 Attempt、:498 ChatEvidenceSubmitted、:556 EvidenceCorrection）
- `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/dao/ProjectionTransactionDao.kt`（:744-785 loadLearningLedger）
- `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/entity/LearnerChatEvidenceEntity.kt`（:30-73 无时长列）
- `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/entity/LearningEntities.kt`（:307-342 attempt_event 含 duration_seconds :341；review_log duration_ms）
- `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/KnowledgeQuizFeedbackWriter.kt`（:30 置信常量、:75-76 机械判分、:232-256 写入、:441-457 账本序读法）
- `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/KnowledgeQuizItemStore.kt`（:42-100 快照读口、:273-277 身份）
- `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/KnowledgeQuizEvidenceAudit.kt`（:126-131 会话解析、:137-148 请求 id 逆解析）
- `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/KnowledgeEvidenceWriter.kt`（:258-270 通道前缀单源）
- `feature/review/src/main/kotlin/com/tingyun/smartmistakebook/feature/review/KnowledgeReviewSessionViewModel.kt`（:283,307 会话 id 生成——只读引用，热区不写）
- `docs/research/2026-10-25-g5-adaptive-selection-proposal.md`（裁定来源：§2.4/§3-A/§5）
- `.zcode/plans/plan-2026-10-25-batch-21-kd64-g5a-quickjs.md`（范围与风险预登记）

外部（本会话实拉/实查）：

- proso-apps 源码：https://github.com/adaptive-learning/proso-apps/blob/master/proso/models/prediction.py （`gh api ... -H "Accept: application/vnd.github.raw"` 实拉全文；MIT 许可经 `gh api repos/adaptive-learning/proso-apps` 核验）
- 生产配置：https://github.com/adaptive-learning/proso-apps/blob/master/testproject/proso_config.yaml （同上实拉）
- 背景论文（存在性经 WebSearch 定位，原文未解析）：https://www.fi.muni.cz/~xpelanek/publications/CAE-elo.pdf
