# smart mistake book 生产就绪度审计 · 完整记录

> **审计问题**：这套软件，尤其是内核，是否达到生产级质量标准？
> **审计对象**：`D:\smart mistake book` 的**已提交代码**（`main`）。工作树中另有并发会话的未提交改动（`docs/known-defects.md`、`ProblemDao.kt`、`ModelEgress.kt`、`MasteryWriteGate.kt`、`RoomTutorToolRunner.kt`、`RawConnectionAccess.kt` 等十余个文件），**不属于本次范围**，全程未被修改、暂存、提交或还原。
> **审计日期**：2026-09-12
> **改动量**：**零**。全程只读；未运行 Gradle、未执行测试、未在设备上验证。
> **仓库规模**：17 个 Gradle 模块 · 44 版 Room schema · FSRS-6 调度内核

---

## 0. 这份文档是什么

本次审计产出**三份材料**，用途不同，请配合使用：

| 材料 | 位置 | 用途 |
|---|---|---|
| **设计版报告**（HTML） | `C:\Users\听云\错题本内核缺口登记册.html` | 给人读的。判定矩阵、旗舰发现、遗忘曲线对比图、横切失效模式。适合快速建立整体判断。 |
| **本文件**（完整记录） | `docs/audit-2026-09-12-kernel-readiness.md` | 给施工用的。57 条原始 finding 的**全文** `failure` / `evidence`、19 条对抗复核的完整理由、全部 healthy 项、9 条未验证漏项。**引用任何结论请引这一份。** |
| **原始机器数据** | 会话临时目录 `audit-extract.json`（115 KB）· `wvuzb1nlz.output`（94 KB）· `journal.jsonl` | 逐 agent 的原始返回值。只在需要追溯某个数字的来源时读。 |

**读法建议**：先看第 1 节的判定矩阵决定要不要往下读；施工前**必读第 3 节**——那一节是复核阶段与施工阶段推翻了前述结论的九条修正（S-1～S-9），其中**五条**推翻的是我自己的判定（S-1、S-4、S-6、S-8、S-9）。跳过它会把已经作废的结论当成依据。**动手前还要读第 12.3 节**：并发会话的未提交工作树已经结清了在册条目中的一条、并改到了另外四条所在的文件（第八条修正 S-7 也记在那里）——那五条的当前状态以 12.3 为准。

### 已确认的决策（2026-09-12）

| 编号 | 议题 | 决策 |
|---|---|---|
| D-1 | 改投影输出的两条修复（同日聚合、`decay` 传递）的前置条件 | **先做批 0「重放可信化」**，四条验收全做（见 §11） |
| D-2 | 伪归因的历史数据 | **只修未来**（写入层加「本题已有非伪绑定则不落伪归因」），并把「归因是写入时的不可变事实」写进 spec |
| D-3 | 实施隔离 | 另开 worktree，**基线＝已提交 HEAD `dc12065`**（2026-09-12 确认，见 §12.2；不是 `origin/main`） |
| D-4 | 全量重放要不要分块 | **不分块**。删掉原方案里的「分块」，只留上界 ＋ 显式失败（ADR-0002；理由是修正追溯生效且结构上进不了增量路径，见 §12.4） |
| D-5 | 第 2 项「重放与增量等价」的口径 | **严格逐字段相等，不排除任何字段**——整条链路上没有 wall clock，时间戳本来就该相同（见 §12.4 的订正）。**定义域＝不含修正的账本**：修正结构上进不了增量路径，那不算不等价 |
| D-6 | 若第 2 项在不含修正的账本上仍不等价 | 登记为架构缺陷，**暂停批 3 的算法修复**，先把不等价的来源钉死；不缩小测试范围来变绿 |
| D-7 | 第 4 项不变量校验的落点 | 放在 drainer 的 `commitFullReplay`：校验通过才调用 `commitProjection`，不过则保留旧 checkpoint 不提交 |
| D-8 | 设计记录 | 建 `docs/adr/0001`（归因写入时冻结）与 `docs/adr/0002`（全量重放不可分块），并建 `CONTEXT.md` 词汇表 |
| D-9 | 第 2 项要不要同时钉住「修正必然触发全量重放」 | **要**。等价性的定义域靠两处机制保证（`AttemptCorrection` 不属于 `IncrementalLearningEvent`；批次加载器见修正行即返回 `FULL_REPLAY_REQUIRED`），两者都要有断言，否则定义域会静默扩大。DAO 那一半登记为**待补的仪器化测试**（本机不跑，标 `UNVERIFIED`） |
| D-10 | `LearningProjectorTest.kt:265-284` 那条弱测试 | **补强**成全字段相等，改名与注释使其声称的与断言一致；「分批累积」那一半由第 2 项在 drainer 层另测。两层都留，不删 |
| D-11 | `MAX_APPLIED_RECORDS = 4_096` 的跨批封顶 | **纳入第 2 项**（造 4_097 条事件比对两条路）。理由：账本规模远超 4096（§12.6），这条「逐次走过来的结果 vs 一次重放出来的结果」的一致性在真实安装上可达 |
| D-12 | N-01…N-05 的处置范围 | **N-02／N-04／N-05 作为一组「投影失败的出口」并入批 0 第 4 项**一起设计（它们同一个问题的三个面）；**N-03 进批 1**（与 `runOperation` 覆盖面一起修）；**N-01 登记为 `P3` 死代码** |
| D-13 | 超限异常的类型与可重试性 | 新建 `ProjectionReplayLimitExceededException`：**不继承** `ProjectionCasConflictException`、**不重试**，声明在 `core/database` 的端口文件（与 `LearningLedgerIntegrityException` 同处）。不复用账本损坏的异常——两者处置不同，合并会让它们都无法被单独识别 |
| D-14 | 上界的取值口径 | 按**防 OOM** 量级取（约 `10⁵` 条），并**明确接受其后果**：重度老用户会撞上，撞上即该次投影版本升级永久无法完成。不取「几乎不可能达到」的值——那只是让代码看起来有保护（一个在 OOM 之前不会触发的上界形同虚设） |
| D-15 | 「重放地平线」 | **现在立项**：写 `docs/adr/0003-replay-horizon.md` 记下方向与成立条件（剩余账本里修正的目标序列号不得早于基线），**实现排在批 0 之后**（批 2 或批 3）。不塞进批 0，也不只做口头登记 |
| D-16 | N-06（排空步数上界报成 CAS 冲突）的处置 | **批 1 第一条**，与 N-03 一起做。两者同属「失败出口说错原因」，而出口本身在批 0 第 4 项修；出口没修好之前再抛一个新异常，只会落到同一个说错原因的地方。**不**塞进批 0——批 0 的范围是"重放可信"，不是"把每一个错标的异常换掉" |
| D-17 | 知识包安装失败要不要阻止投影初始化 | **不阻止**。HEAD 上两句共用一个 `try`，`install()` 一抛就永远走不到 `initialize()`——**知识包失败会连带冻住学习进度**，而界面只说「自动分类会暂缓」，把严重性说反了。这是批 0 第 5 项的**唯一刻意行为改动**；代价（两段都失败时只显示投影那条）写在 `StartupInitialization` 的 KDoc 里 |
| D-18 | 确定性失败要不要提供「重试」 | **不提供**。`RecoverableFailure` 新增 `retryable`（默认 `true` ＝ 拆分前行为），账本有缺口与超出重放上界两类置 `false`：它们的输入在同一版本内不会变（账本只追加、从不裁剪），重试只会重放同一条错误，而文案正说「重试不会改变结果」。原因未知的那一类保留重试 |
| D-19 | 批 0 第 5 项带出的四条新发现 | **N-07**（`replay` 造没人读的预测列表，动 `core:domain` 公开类型）→ 批 4 删除；**N-08**（`errorCategory` 只写不读）→ **本批不加枚举值**，改用诊断编号前缀做面向人的归类载体，清理时在「删字段」与「接上横幅」之间选一个；**N-09**（`LearningLedgerIntegrityException` 被当通用异常用，26 处抛出点里只有 2 处关于账本）→ 归类挂调用点、不挂类型，清理归批 4；**N-10**（知识包安装三个独立事务、半装后永久卡死，`P2`）→ 根因归批 1，本项已降低其危害面 |

---

## 1. 判定摘要

判定标准是「**机制是否在生产路径上真正生效**」，不是「代码是否存在」。严重度沿对抗复核者的定义：
`P0`＝静默错误结果／崩溃／数据损坏　`P1`＝真实缺陷、影响有界　`P2`＝潜在风险或当前不可达　`P3`＝结构性。

| # | 维度 | 判定 | 承重理由 |
|---|---|---|---|
| 01 | 调度与记忆数学内核 | **有保留地就绪** | 公式逐项对齐 py-fsrs、不变量由类型强制、数值退化有兜底；但 `decay` 参数 `w20` 在四处被冻结（F-01），三种「已经过天数」口径并存（F-02） |
| 02 | 事件溯源与投影完整性 | **有保留地就绪** | 确定性重放、单事务 CAS、指纹双向绑定扎实；但版本触发重放的唯一判定无测试，修正账本无写入方 |
| 03 | 知识库联结与掌握度聚合 | **未就绪** | 评级／自评证据**无条件**落到 `pseudo:<科目>` 伪节点，有真绑定的题其真 KC 永远收不到；补救通道 release 恒空 |
| 04 | 模型边界与结构化输出嵌合 | **未就绪** | 配图通道出网不读用户的同意开关；手写 allowlist 漏两个 kind，使两条已建成的生产链被永久拒绝 |
| 05 | 测试严谨度与门禁 | **门禁层未就绪** | 性能门用空种子数据跑（三条断言恒真）；`core:ui` 的 3 个测试文件（11 个 `@Test`）从未被执行；~~两个 `Skeleton` 测试零断言~~（批 4 已换成三条有断言的启动恢复用例） |
| 06 | 架构边界、依赖方向与体量 | **就绪** | 依赖方向与 `ARCHITECTURE.md` 逐模块吻合，零 feature→feature、零 feature→core:data；`core:model` 与 `core:visual-runtime` 真正零 Android 依赖。仅一处小越界 |
| 07 | 持久化、迁移与并发状态 | **有保留地就绪** | 会话推进是真 CAS＋单事务、迁移 1→44 连续无破坏性回退；但存在 P1 幂等违约、恢复失败后进程级数据库不重开、切分任务崩溃卡死 |

**计数**：就绪 1 ／ 有保留地就绪 3 ／ 未就绪 2 ／ 门禁层未就绪 1。缺口 **56 条**（P1 ×9 ／ P2 ×26 ／ P3 ×21）。

### 直接回答「尤其是内核」

**如果把「内核」定义为数学与调度内核——达标。**
`FsrsScheduleMath` 是 py-fsrs `scheduler.py` 的逐项忠实移植：`R(0)=1`、`R(S,S)=0.9`、成功 `S'≥S`、`D∈[1,10]` 这些不变量不是「通常成立」，而是由构造函数与 `coerceIn` 强制；毕业、leech、数值溢出都有确定性实现与兜底；`review_log.rating` 记的是调度实际使用的评级，与训练分布一致。

**如果把「内核」定义为让学习算法真正起作用的整条链——未就绪。**
拖住它的不是公式，是接线。**56 条里有 11 条是同一种病**：机制被建成、被写进 spec、被测试覆盖，然后在生产路径上没有生产者或没有消费者——修正账本无写入方、影子预测算完即丢、前置补救通道的门其实是 debug 期的 fixture 目录、视觉权重算完不读、四个标定校准接口零调用点、`fsrsBeatsBaseline` 零读者。

**批 0 收尾不改变上面七维的判定。** 要说清楚原因，否则会被读成"修完了还是未就绪"的矛盾：
批 0「重放可信化」做的是**让已有的重放机制变得可信且失败可见**（触发判定有测试、两条路逐字段等价、
有上界、失败不再说错原因），它**不动**那 11 条接线缺陷、也不动 F-01／F-02 两条旗舰发现。
维度 02（事件溯源与投影完整性）的唯一承重理由「版本触发重放的唯一判定无测试」**已被批 0 结清**，
但该维还压着「修正账本无写入方」，所以判定仍是"有保留地就绪"，只是保留的那一半更窄了。
批 3 的算法修复（同日聚合、`decay` 传递）现在才具备 D-1 要求的前置条件。

---

## 2. 审计方法与执行记录

### 2.1 做了什么

7 个维度并行审计，每个维度再派一个**对抗复核者**（默认立场是怀疑，只有亲自读过源码、确认失败路径成立才判 CONFIRMED；并被要求反向检查「看着像缺陷其实被上游拦住」「看着接好了其实没人喂」「是否已被未提交的并发工作树修掉」）。

| 阶段 | agent 数 | 结果 |
|---|---|---|
| 维度审计 | 7（+1 重跑 = 8） | 7 维全部完成；持久化维度的首个 agent 因 API 流错误中断后重跑成功 |
| 对抗复核 | 7 | **3 个完成**（投影重放、知识联结、模型边界）；**4 个中断**（测试门禁、调度数学、持久化并发、架构边界） |
| 汇总 | 1 | 完成，但其第 7 维被截断（该维由独立工作流补跑） |
| **合计** | **14** | 10 完成 ／ 4 因基础设施故障失败 |

### 2.2 四个中断的对抗复核

中断原因是子智能体所用模型的 **API 流错误**（`API Error: Stream error: error decoding response body`），属基础设施故障而非逻辑问题——重试一次仍失败。受影响的维度是：

- **测试严谨度与门禁**（8 条，全部无对抗结论）
- **调度与数学内核**（11 条，全部无对抗结论）
- **持久化、迁移与并发**（7 条，全部无对抗结论；该维复核 agent 两次中断）
- **架构边界与体量**（8 条，全部无对抗结论）

**处置**：这四个维度的承重项由主循环回读一手源码补验，标注为 `◆ 一手核实`。**失败路径已确证，端到端复现未做**；且**没有第二个独立视角**——这是本记录最主要的可信度缺口。

### 2.3 证据等级口径

| 标记 | 含义 | 条数 |
|---|---|---|
| `✔ 对抗复核 CONFIRMED` | 复核者逐条核对行号、反查生产可达性、检查是否已被并发工作树修掉 | 19 |
| `◆ 一手核实` | 主循环读源码确证，未经独立 agent 复核 | 20 |
| `○ 未对抗复核` | 单遍审计笔记，未被打过反方。**不得据此单独宣称功能失效** | 15 |
| `△ UNCERTAIN` | 复核者判定事实成立但后果或可达性未证实 | 2 |
| `✕ 被推翻 REFUTED` | 不是缺陷，已从登记移除 | 2 |

---

## 3. 记录级修正（复核阶段与施工阶段新增，十一条）

> **这一节是本记录最重要的部分。** 其中 **S-1、S-4、S-6、S-8、S-9、S-10、S-11 修正的是审计报告本身**（含主循环自己的判定与**建议**）——
> 按「不把有明确记录的设计取舍当缺陷」和「先回读一手来源再推进」两条纪律发现的。
> 凡与本节冲突的表述，以本节为准。
> **S-1～S-8 出自复核阶段，S-9 出自批 1 第 1.4 项的施工后核实，S-10 出自批 3 第 4 项的施工前回读，
> S-11 出自批 3 第 3 项的施工前回读（推翻的是本记录作者自己给出、且已被用户采纳的建议）。**

### S-1　`ChatEvidenceDao` 的幂等修复方案必须改：原方案会造成永久账本 GAP

**这是一条对修复方案本身的证伪，不是对 finding 的证伪。**

原批 1 的方案是「把 `insertOutboxRows` 的 `OnConflictStrategy.ABORT` 改成 `IGNORE`」。读代码后确认**这会让问题变严重**：

- `ChatEvidenceDao.allocateSequence` 在插入**之前**就分配序列号（CAS 单调递增，`core/database/.../dao/ChatEvidenceDao.kt:153-162`）。
- 读侧的 GAP 检测是**严格**的：
  - `ProjectionTransactionDao.loadProjectionBatch` 从 `checkpoint+1` 逐行走，`row.outboxSequence != expected` 即返回 `ProjectionBatchStopReason.GAP`；
  - `ProjectionTransactionDao.loadLearningLedger` 在最后显式报 `"Sequence N was allocated but has no immutable ledger event"`。

所以重试时——序列号已消耗、证据行与 outbox 行都被 IGNORE——会留下一个**永久空号**，投影从此停摆、`StudyDataStatus` 被置 `ERROR`。
**即：把「重试抛异常」换成「账本永久损坏」。**

**正确修法**：在分配序列号**之前**先按 `evidence_id` 查已存在行，存在则直接返回 no-op（不分配序列号）。这才是 DAO 注释里「must silently no-op」的本意。

### S-2　同意门的精确形态：不是「没检查」，是「读错了开关」

原 finding `egress-bypass-image-channel` 判定成立，但**机制描述需精确化**：

- `capabilities.networkRequestsAllowed` 来自 `FlavorCapabilityFactory.create()`（`SmartMistakeBookApplication.kt:352-354`）——是**构建变体**级能力位（`localFirst` / `strictOffline`），**不是用户的同意开关**。
- 用户的同意开关是 `modelAgentConsentStore`（DataStore）。**批量导入路径确实读它**：`SmartMistakeBookApplication.kt:263` 注入 `consentEnabled = { modelConfigurationStore != null && runBlocking { modelAgentConsentStore?.current() ?: false } }`，注释写明「runs only under the global "model agent" consent」。
- **图片生成路径拿到的是变体位**：`SmartMistakeBookRoot.kt:758` 传 `networkRequestsAllowed = application.capabilities.networkRequestsAllowed`。

**结论**：用户在设置里关掉「模型智能体同意」，**配图与去手写仍然会把原图字节发出去**。这不是「没有同意机制」，而是「这条通道读了一个与用户同意无关的开关」。

**附带新发现（未在原 56 条中）**：

> **`agentConsentGranted` 有两处被硬编码为 `true`** —— `RoomBatchImportRepository.kt:510` 与 `RoomCaptureWorkflowRepository.kt:416`。
> 于是网关那句 `check(request.agentConsentGranted)`（`OpenAiCompatibleModelGateway.kt:601`）对这两条路径**形同虚设**。
> 今天靠调用点的 `consentEnabled()` 兜住，不构成现网漏洞；但那是「第二道闸假装在」，不是「第二道闸真的在」——
> 任何人后来绕过调用点直接构造请求，二次校验不会拦住。定级 `P2`。

> **⚠ 复核（2026-09-13，收 R-13 与 N-28 时回读主线）：这条轴在 `main` 上已被产品决定换了个答案，
> 本分支的修法不能原样合并。**
>
> 一手事实（全部读 `main` = `d0bc3b2e`）：
> - `e462f1ea`「删除全局模型同意开关，配置模型即唯一条件」**删掉了** `ModelAgentConsentStore` 与
>   `DataStoreModelAgentConsentStore`——本分支 S-2 修法所依赖的那个类型在主线上**不存在了**。
> - 主线的 `ImageCredentialGate.kt` 仍是**修前**形态（`networkRequestsAllowed: Boolean`），
>   `AttachedImageGeneratorFactory.create` 同样；即本分支批 1 第 1.1 项**从未进过 `main`**。
> - 主线把"同意"改成**推导值**：`SmartMistakeBookApplication.kt:259` 注入
>   `modelEgressAllowed = { modelConfigurationStore != null }`（＝**配置了模型就是同意**）；
>   但 `SmartMistakeBookRoot.kt:626/654/721` 三处把这个参数喂的是
>   `baseCapabilities.networkRequestsAllowed`（**仍是构建变体位**）。
> - 上面那条「两处硬编码 `true`」在主线**原样存在**（`RoomBatchImportRepository.kt:510`、
>   `RoomCaptureWorkflowRepository.kt:415`）。
>
> **含义**：S-2 与本分支的修法是这条轴上的**一种**答案（"读出用户开关"），产品在 `main` 上选了**另一种**
> （"配置模型即同意"）。两者不能同时成立，**合并时（若做合并）必须二选一**；而"主线那三处喂变体位、
> 两处喂常量 `true` 在新语义下是否本就正确"（`strictOffline` 是否仍必须拒绝一切出网？）是**产品问题**，
> 这里只登记事实，不判——主线上的代码属于另一条工作流，本审计不动它。

### S-3　归因**无法**历史修复而不破坏重放确定性（定论）

这是本次审计最深的一条架构结论，三个已核实的事实推出它：

1. **`attributions` 在指纹里**：`LearningLedgerFingerprint.kt:129-140` 逐字段哈希 `bindingId` / `knowledgeNodeId` / `weight` / `basisRevisionId` / `taxonomyVersion` / `role` / `certainty`；而每条读取路径都会重算指纹与 outbox 比对，不符即 `CONFLICT`、投影停摆。→ **改归因 = 破指纹 = 投影阻塞**，历史归因不能靠改写快照修。
2. **绑定表不是 append-only**：`ProblemOrganizationDao.kt:279` 有硬 `DELETE FROM practice_unit_knowledge_binding`。
3. 但该 DELETE 带守卫 `NOT EXISTS (引用它的 attribution)`——**已经吃到证据的绑定不可删**，能被删的只有从未被引用的。

推出的定理：

> **能修历史的方案，必然破坏重放确定性；保住重放确定性的方案，必然修不了历史。**

论证：「归因何时被解析」只有两个位置——写入时（烘焙进不可变快照）或投影时（读活的绑定表）。
投影时读活表意味着同一份事件日志在用户今天组织题目**之前**重放给出一种结果、**之后**重放给出另一种结果；
「重放」于是不再是真相的重建，而是调用时刻的函数——批 0 刚建立的可信性会立刻自相矛盾。
用「事件发生时点之前的绑定」做 as-of 语义可以恢复确定性，但新绑定的 `acceptedAt` 晚于事件，
as-of 就退化成与写入时判定**完全相同**——历史仍然修不了。

**唯一同时满足两者的路径**是把归因的变更本身变成事件（选项 C）。而 `AttemptCorrection` 只有「撤销刚才的结果」这一种语义，
没有「改归因」这种语义，且修正账本在生产上**连一个写入方都没有**（见 5.2.2 `correction-ledger-never-fed`）。

**决策 D-2**：走写入层限定 + 文档化。因此 **spec §9 描述的分摊公式（暗示读时解析）与实现是分叉的**，
这个分叉本身就是失效模式 D 的一个实例，应随本次修复一并订正。

### S-4　F-02 的爆炸半径远低于原判；`sameday-aggregation-missing` 需重新定性

**修正一：F-02（三种 elapsed-day 口径）的持久化影响被高估。**
三种口径的实际落点：

| 口径 | 位置 | 是否写持久状态 |
|---|---|---|
| learner 本地日历日差（**最正确**） | `LearningProjector.projectMemory` | **是**（`S`、`nextReview`） |
| `floor(elapsedMillis / 24h)` | `ForgettingCurve.estimateAt` 的 FSRS-6 分支 | **否**——调用点是 `StudyExperienceMappers.retentionAt`（显示用 `retrievability`）、`HLRPredictionAuditService.estimateAt`（影子审计）、`ReviewPlanner.estimateAt`（V1，不可达） |
| 未取整小数天 | `KnowledgeReviewQueue.knowledgeRecallRiskByNode` | **否**——调用点是 `StudyReviewPlannerService.kt:519` 的风险排序 |

**即：写持久状态的只有一个，而且它本来就是三者中最正确的。**
F-02 修复因此**不需要版本升级、不需要重放**，属低风险修复。
（`ForgettingCurve.reviewAtTargetRetention` 会被投影调用，但它算的是**间隔**而非 elapsed days，是另一个函数、另一回事，不在 F-02 口径集内。）

**修正二：`sameday-aggregation-missing` 的原定性不成立——它是一条已记录的取舍，而不是未实现的要求。**

spec 的 `§10 实现注记` 原文：

> **同日语义（§2.15）**：以 py-fsrs short-term 调度器同构方式实现——`t==0` 走短程分支、`t>=1` 才落长程更新，
> 满足"每卡每日至多一次长程更新"的 P0 不变量；learning_steps 保持禁用；review_log 保留全部原始证据（采集与调度解耦）。
> §2.15 的"当日 min(G) 聚合、日终落地"**由短程分支的保守性覆盖**（同日 Again 即时衰减，聚合无额外自由度），作为实现注记记录。

所以：(a) **P0 不变量（每卡每日至多一次长程更新）是满足的**——`t==0` 走短程分支，同日不会重复落长程更新；
(b) 聚合要求是 spec **明确记录为「由短程分支的保守性覆盖」** 的取舍，不是遗漏。

**但我实算后发现那条覆盖论证只对一半情形成立**（默认参数 `w17=0.5425 / w18=0.0912 / w19=0.0658`）：

- **同日重复失败**：实现比聚合**更保守**（每次 Again 都即时衰减）→ 论证成立。
- **同日重复成功**：`S' = S · e^{w17·w18} · S^{−w19}`。取 `S=0.212`、`G=GOOD`，单次乘数 **1.1636**：
  - 一次：`S = 0.2467`
  - 三次：`S = 0.3244`（连乘 1.1636 × 1.1521 × 1.1414）
  - **比聚合成一次高 31.5%** → 论证不成立。聚合本会把三次压成一次 `G_agg = min = GOOD`，实现却放了三次。

**重新定性**：本条从「spec §2.15 的 P0 要求未实现」改为
**「文档明确记录的取舍，其覆盖论证对『同日重复成功』不成立（`S` 被重复放大 ~31%），对『同日重复失败』成立」**。
严重度维持 `P2`，但**可达性未验证**：需要确认同一张卡在同一天被复习两次在生产上确实可达
（`ReviewPlannerV2.MAX_PER_KNOWLEDGE_NODE_PER_SESSION = 2` 限制的是**单次会话**内同一 KC 的题数，
而产品被描述为「日粒度会话」；同日多次会话、或重开会话是否可达未确证）。

### S-5　前置补救通道的**真实内容源是存在的**，原判需精确化

原 finding `remediation-gated-on-debug-fixture` 判定成立（`reTeachOpening` / `prerequisiteRemediation` 以 `teachingArtifact(...) ?: return null` 起手，而该值只来自 debug 期的 fixture 注册表）。
但需补充一个正面事实：**release 侧有真实的教学材料数据源，只是没被这条链路使用**——

- `knowledge_teaching_material` 表由 **`BundledKnowledgeBaseInstaller`** 填充，数据来自 **`BundledKnowledgePackResources.load()`**；
- 两者都在 `core/data/src/main`（**release 可见**），不是 debug-only；
- 读取侧 `KnowledgeTeachingMaterialDao.readForKnowledgeNodes` 与 `TutorTeachingReferenceSelector.reTeachPriority` 已存在、已有测试、且 SQL 的 `ORDER BY CASE` 与纯函数逐值同构。

**即**：修复不需要「新建内容源」，只需要把 `prerequisiteRemediation` 的供给从 `fixtureSource.teachingArtifactForPracticeUnit` 改到
`knowledge_teaching_material`（经 DAO + selector）。这条从「无内容源」降级为「接错了源」。
（`reTeachOpening` 是否也需要同样的替换，取决于它的返回类型是否基于材料而非 curated assessment items——实施时确认。）

> **实施时的三处订正（2026-09-13 批 2 第 3 项，一手回读后）——这条原判的"接错了源"不准确。**
> 1. **材料那一半从来就是真库**：`teachingReferences`（`RoomTutorTeachingReferenceRepository` →
>    `RoomKnowledgeBaseStore` → `KnowledgeTeachingMaterialDao.readForKnowledgeNodes`）只读
>    `knowledge_teaching_material`。所以本条不是"接错了内容源"，而是**「这道题绑在哪些 KC 上」这个范围
>    取自策展件**（`artifact.knowledgeNodeIds`），范围为空则整条通道返回 null。
> 2. **范围的真源是错题读侧**：`ProblemDao.observeActiveMistakes` 的 `knowledge_node_ids` 子查询
>    已经从 `practice_unit_knowledge_binding` 按「当前修订 ＋ 最近一次组织轮」算好了，
>    与排程、视觉归因读的是同一份结论——不必新开读取路径。
> 3. **修数据层还不够，而且这才是主要原因**：唯一渲染补救卡的是**策展屏** `ReviewSessionScreen`，
>    实拍题走的是 `CapturedReviewSessionScreen`，那一屏连这个参数都没有；装配侧又以
>    `artifact != null` 为门。**只按本条原文改数据层，生产里什么都不会变。**
>
> 因此本条的真实范围是**数据 ＋ 装配 ＋ 呈现**三层，见 §11 批 2 第 3 项。另：
> `reTeachOpening` 的确认结论是**需要**同样的替换（它用 `artifact.subject` ＋ `artifact.knowledgeNodeIds`
> 查材料），但**没有一并做**——它没有任何一屏渲染过它，而它按本仓自己的说法是"必经步骤"，
> 放哪里是产品可见的选择。登记为 **N-16**。

### S-6　全量重放没有上界、没有进度、失败不可见

原 P2 `version-replay-trigger-untested` 只讲「触发判定没有测试」。补一条运维面事实：

- `StudyProjectionDrainer.commitFullReplay`（`:141`）**没有条数上界、没有分块、没有进度上报**；
- 全仓 grep 无 `ReplayProgress` / `MAX_FULL_REPLAY` 一类上界常量；
- 增量路径有上界（`MAX_PROJECTION_DRAIN_STEPS = 64` × `PROJECTION_BATCH_SIZE = 100`），**全量重放没有**；
- 而全量重放是**同步**操作，且 `LearningCoreVersions.kt:8-21` 明文记载「v5→v6 / v6→v7 必须靠版本不匹配触发全量重放，否则修复对已投影事件静默无效」。

**意义**：`decay` 传递与同日聚合两条修复只要落在投影层，就会**强制所有已有安装**在下次启动走这条路径。
把三条算法修复押在一个「没测试、没上界、没进度、失败不可见」的机制上，风险不对称——
这正是决策 D-1（先做批 0）的事实依据。

> **S-7 不在本节。** 它是对**证据行号引用**的订正（`core-ui-tests-never-run` 引用的第 88 行来自并发工作树而非已提交状态），
> 成因是「工作树过期」而非「复核推翻结论」，因此与 §12.3 放在一起，便于同批处理；正文订正已就地写进 §5.5.1。

---

### S-8　§8 漏项 7（配图通道不遵守用户配置的 `modelId`）**不成立**：那是**已记录的设计**，而它的修复方案会打断正在工作的链路

**2026-09-12 批 1 施工前回读一手来源时发现。** §11 批 1 的 1.2 照抄了 §8 漏项 7，因此这一条同时推翻
**一条 finding 与一条修复方案**。

**原文两条主张，逐条核对**：

| 原文主张 | 一手核对 | 结论 |
|---|---|---|
| 「配图/去手写出网通道**不遵守用户配置的 `modelId`**」 | `docs/image-pipeline-spec.md:41-49` 明写「**Models are split (per decision)**」：*Read / classify / explain* 用「用户配置的任意图像输入多模态模型」；*Generate figures* 用 **GPT image 2**，「figure generation is **deliberately separated into MCP**」。§5 的路由表把两者分列（`multimodal (image input)` ／ `MCP gpt-image-2`）。该文件**已提交**（`0a2b03f`） | **误把已记录的设计读成了缺陷** |
| 「且**无注释/规格说明**该取舍」 | 规格三处明写（`:41-49`、`:51-58`、`:117-119`）；代码两处注释点名（`OpenAiImageGenerationChannel.kt:20`、`ConfiguredCleanImageGenerator.kt:19`） | **事实错误** |

**它的修复方案是错的，而且会造成新的故障。** 1.2 要求把 `configuration.modelId` 传给
`OpenAiImageGenerationChannel`。但 `supportsImageInput` 探测的是**视觉输入**——`ModelProbeSpec.kt:11-13`
写明该探针「要求模型读出合成图里的四字符码」，即它在问「这个**对话**模型能不能看图」，
**不是**「它能不能生成/编辑图」。于是照 1.2 改，就会往 `POST /v1/images/edits` 里塞一个对话模型 id
（OpenAI 上就是 `gpt-4o` 一类的值），**当前能用的 OpenAI 配置会被打断**。

**按本审计自己的标准处置。** §13.2 第二条（`attention-floor-unreachable`）已经立了规矩：
「事实成立，但 spec 明确记为设计取舍 → 属已记录的设计取舍，不是缺陷」。
S-8 与它同类，且更进一步——这里连「事实成立」都不成立，因为**模型是被有意拆开的**。

**因此 1.2 不实现，从批 1 移除。**

**剩下的真问题（换了一条，且更大）**：规格说 MCP server 是「the single figure path」，
但 `tools/image-mcp-server` **不在 `settings.gradle.kts` 里、没有任何模块引用它**，
生产实际走的是进程内的 `OpenAiImageGenerationChannel`。这不是「不遵守 modelId」，
而是**「配图模型是硬编码的 OpenAI 专用模型，第三方 provider 用户在规格内就用不了配图」**——
一个**产品/规格范围**问题，不是代码缺陷。要不要支持第三方配图模型（需要给配置加一个图像模型字段
＋ 设置页 ＋ schema），是**产品决策**，登记为 **N-11**（见 §12.5），不塞进批 1。

### S-9　「修 1.4 会连带关闭 finding 4 的主触发路径」**不成立**：1.4 只关掉其中较小的一条

**2026-09-12 批 1 第 1.4 项施工完成后核实。** §8.2 第二条的结论句原文是
「修复 finding 1 的『仅无绑定题才落伪归因』限定**会同时关闭 finding 4 的主触发路径**，单独修任一条都不完整」。
改完 1.4 之后回读 finding 4 自己的选择逻辑，发现这句**说反了**——它连带影响的是 §13.3 第 3 条给 1.4 记的
那笔红利（「连带关闭视觉证据被引走的两条路径」），也是错的。

**finding 4 有两条可达路径，1.4 只关掉支路径那一条：**

| 路径 | 时序 | 1.4 的作用 |
|---|---|---|
| **① 先伪后真｜主路径，关不掉** | 题无绑定 → `createReviewPlan` 落一行伪绑定（`taxonomyVersion="pseudo-plan-v1"`，`acceptedAt=T0`）→ 之后 organization 管道补上真绑定（`T1>T0`） | **正确地放行**：落伪绑定时该题**确实没有绑定**。而 `VisualInteractionIngestor.kt:74-79` 取的是 `bindings.first().taxonomyVersion`（按 `acceptedAtEpochMillis` 升序排序），于是**先落的伪绑定永久遮蔽后到的真绑定** |
| **② 先真后伪｜支路径，已关闭** | 真绑定已存在，但目录投影为空（该绑定不再匹配最新 `problem_organization_receipt` 与其 KNOWLEDGE classification，`ProblemDao.kt:107-146`）→ 旧代码仍再落一行伪绑定去遮蔽 | **关掉了**：`any { it.sourceType != PSEUDO_FALLBACK }` 为真 → 拒绝落伪绑定 |

路径 ① 才是主路径——它是**每道「先复习、后归类」的题的常规生命周期**：无绑定的题一定会先进一次复习计划，
然后才可能被 organization 管道归类。所以 1.4 **按定义不该拦它**：那时题就是未归类的，伪绑定是对当时事实的**正确**记录。
这条遮蔽因此必须在**组选择处**修，登记为批 1 第 **1.7** 项（见 §5.3.6）。

**教训（与 §13.2 第三条同源）**：`"修 A 会连带修好 B"` 这种断言，必须回到 **B 自己的判定逻辑**上验证，
不能由 A 的存在性推出来。§8.2 那三条本来就声明过「未经任何二次验证」——这一条正好说明**那句声明是有用的**。

### S-10　「优化器没有 log-loss 门」**不成立**：门在，只是它的基准是**出厂默认**、不是**当前生效的那组**（2026-09-13 批 3 动手前回读一手来源时发现）

§11 批 3 原写「参数优化器加 spec §2.11 要求的 log-loss 门（现在是 `optimize()` 后直接写回，无 `evaluate()`）」。
回读 `SchedulingEvaluation.kt` 与装配点后，这句话的**因果说反了**：门是有的，而且比"有一个门"更严——
缺的是**另一个基准**。

| 问题 | 现状（一手：`SchedulingEvaluation.kt:501-542`、`StudySchedulingCalibration.kt:79-90`） |
|---|---|
| `optimize()` 有没有对照 log-loss 择优？ | **有，而且每处都对**：`fit()` 用 `DEFAULT_PARAMETERS` 的验证损失做初始 `best`/`bestLoss`（`:507-511`），只有当某轮参数把验证损失改进了 `> 1e-9` 才替换（`:532-534`）。所以返回的参数**在验证尾段上不会比出厂默认差**；`INSUFFICIENT_DATA` 更是直接回默认（`:401-410`） |
| w15 解锁有没有增益门？ | **有**：`≥5000` 样本 ＋ HARD 桶 `≥100` ＋ 验证损失相对增益 `> 2%` 三条同时成立才解锁（`:444-457`） |
| **那缺的是什么？** | 门的**对照物是出厂默认**，不是**当前生效的那组参数**。`StudySchedulingCalibration.kt:88` 拿到结果后在 `mode != INSUFFICIENT_DATA` 时**无条件** `store.setOptimizedParameters(result.parameters)` |
| 后果 | 一次新拟合可以**把已经更好的现行参数换掉**：只要它还比出厂默认好（这是必然的，因为 `fit` 的基准就是出厂默认），就会写回。数据越攒越多，排期质量却可能**悄悄退回去**——而且没有任何地方记下这次退步 |
| spec §2.11 的字面要求 | `docs/specs/mastery-scheduling-spec.md:139`：「本地跑 BCE+BPTT 优化器 → 替换 w；**必须 `evaluate()`（log-loss 目标 0.35–0.45）** + 灰度 reschedule（逐日渐进）」。这是一条**绝对区间**判据，与"比谁好"是两件事——两者都没有实现在写回处 |

**处置**：本条**订正**上面的表述，批 3 该项的内容随之改成
「写回处加**相对现行参数**的 hold-out 门 ＋ 记下被拒的拟合」，而「比出厂默认好」这一半**已经实现、不重做**。
一条纯粹"更严"的判据不需要新机制也不需要产品决策：它在退化时**保留现行参数**，方向只可能是"不让排期变差"。

**取证等级**：源码级（读到 `fit` 的初始 `best` 与写回点），**未**构造变异、**未**在真实样本上跑过——
所以它是"**描述订正 + 待办项**"，不是"已知会在生产中发生的事故"。写回处那条门的验收条件已经在
§11 批 3 里逐条写出；实现前先补一条能红的用例（现行参数已优于默认时，一次更差的拟合不得生效）。

### S-11　「F-02 统一到分数日历天」是**我给的错误建议**：那会把 FSRS-6 的模型按 FSRS-7 的输入算（2026-09-13，实施前回读一手资料时自己推翻）

**这一条修正的是本记录作者的建议本身**，而且它**已经被用户采纳过**（"继续，F-02 按你推荐的分数日历天统一"）。
按"先回读一手来源再推进"的纪律去读工程里**已有的**一手研究后，发现那条建议站不住：

| 我当时的理由 | 一手资料说的 |
|---|---|
| "读侧估计该用连续时间，分数天更忠实" | 本项目实现的是 **FSRS-6**，而 FSRS-6 **按天**：fsrs-rs `main` 的 `model_v6.rs:243` `round_elapsed_days`（clamp≥0 后 +0.5 取整），注释原文 "FSRS-6 stays day-based: keep f32 transport, but round elapsed days to nearest day"（引文与逐版本核对见 `docs/research/deltat-convention-research.md` §2） |
| "py-fsrs 取整只是因为它的调度器按整天工作" | **分数天是 FSRS-7 的能力**：fsrs-rs `inference.rs:521-532` 的文档注释原文 "FSRS-6 rounds elapsed days to nearest whole day internally; **FSRS-7 keeps fractional elapsed days**"；已发布版 fsrs-rs 干脆收**整数** `days_elapsed: u32`（`inference.rs:357`）；py-fsrs 用 `max(0, (now - last).days)`（UTC 24 小时截断） |

**所以那条建议的真实后果是**：在 FSRS-6 的曲线上喂 FSRS-7 的输入，并且要把
**spec §2.1/§2.2（t ＝ 本地日历日差）改掉**来迁就它——那才是"实现与规格不一致"。

**实际做的是相反方向**：统一到**整天**的本地日历日差，也就是
**写入侧本来就在用、spec 本来就写着、Anki 的 rollover 口径也一致**的那一个。
于是 **spec 一个字都不用改**——需要改的是那两个漂移过去的读侧出口。

**教训（写给下一次的自己和任何人）**：**"用户批准了"不等于"这条建议被核实过"**。
批准是基于我的理由，而理由是错的；这时候照做才是真正违背用户意图（他要的是科学的算法）。
把它纠回来的动作很便宜——**动手前读一遍工程里已有的一手研究**，而那篇研究本来就在仓里
（`docs/research/deltat-convention-research.md`），是我先给建议、后读它，顺序反了。

---

## 4. 旗舰发现

三条最深的：改动收益最高、且**最容易被误判为已经好了**。

### F-01　拟合出的 `decay` 参数永远无法生效，且冻结在四个互不相干的地方
`P1` · `◆ 一手核实` · `core/domain/.../FsrsScheduleMath.kt:83` · `SchedulingEvaluation.kt:411` + `SchedulingReplay:140` · `MemoryUpdateModel.kt:86,93` · `OptimalRetention.kt:149`

FSRS-6 的记忆强度衰减由第 21 个参数 `w20` 决定（默认 `0.1542`）。参数优化器把它列进了要拟合的下标集合
（`(0..14).toList() + listOf(20)`），看起来拟合 15 个参数——**实际只有 14 个**。

原因是**梯度恒为零**：优化器用中心差分算 `∂loss/∂w20`，而损失函数经 `SchedulingReplay.predict` 调
`retention(elapsedDays, stability)`——**不传 decay**，函数就用默认参数里的 `w20`。于是 `w20` 怎么变、预测不变、
梯度恒 `0`、Adam 步 `parameters[20] -= lr * 0 / (0 + 1e-8)` 恰好等于不动。每轮还白白多算两次损失评估。

四处冻结，**任何一处单独修都不生效**：

| # | 位置 | 症状 |
|---|---|---|
| ① | `SchedulingEvaluation.kt:411` 拟合集合含下标 20，`SchedulingReplay.predict:140` 调 `retention(e, s)` 不传 decay | 梯度 ≡ 0，拟合无效 |
| ② | `FsrsScheduleMath.intervalDays:83` 硬编码 `val decay = -DEFAULT_PARAMETERS[20]` | 函数签名没有 decay 入口 |
| ③ | `MemoryUpdateModel.kt:86,93` 调 `retention(elapsedDays, previous.stabilityDays)` | 忽略自己持有的 `parameters` |
| ④ | `OptimalRetention.averageRetention:149` 硬编码 decay | 同文件的 `simulate()` 却正确转发了 `parameters` |

> **为什么这条最危险：两个错误正在互相抵消。**
> 现在**看不出问题**，因为 `w20` 确实从未离开默认值，所以「哪里都不读它」和「哪里都读它」结果一样。
> 真正危险的是**只修一半**：谁要是只修了 ①（让重放把 decay 传进损失函数），`w20` 立刻开始移动——
> 而 ②③④ 三处生产路径还在用默认 decay。结果是拟合用一条曲线、排期用另一条，
> 制造出真正的训练／服务不一致，**而且不会有任何测试变红**。
> 要修必须四处一起修，并且先补一个「拟合出的参数确实改变了预测」的断言来锁住它。

**量级参考**（实算，用于判断这条冻结掩盖了多少差异）：
`R=(1+0.98030·t/S)^(−0.1542)`（幂律，生产实际使用）与 `0.9^(t/S)`（指数，LEGACY 回滚开关）在定义点 `t/S=1` 处重合于 `0.9`，
但尾部相差巨大——`t/S=8` 时幂律给 **0.715**、指数给 **0.430**。这两条曲线是生产代码与回滚开关的真实差别，
也标出了 `w20` 本该移动的量级。

> **已修（2026-09-12 批 3）。** 四处一起改，**另加施工中枚举出来的第五处**
> （`LearningProjector` 的毕业分支与 `ForgettingCurve` 的 FSRS6 分支——它们**写持久状态**，
> 只改原四处会在本来一致的地方造出不一致）。断言按出口分条（新文件 `FsrsDecayThreadingTest`
> ＋ `FsrsProjectionBehaviorTest` 一条接线用例），7 条变异逐处点名命中的断言。
> 改动对现有安装**零行为变化**（`w20` 至今从未离开出厂值），因此**不需要投影版本升级、不需要重放**。
> 做法、范围边界（余下的只读估计登记为 **N-14**）与证据见 §11「批 3」。

### F-02　同一个工程里存在三种「已经过天数」的口径
`P1` · `◆ 一手核实` · `LearningProjector.projectMemory:831-952` · `ForgettingCurve.kt:52-54` · `KnowledgeReviewQueue.kt:118-123`

见 **S-4**：只有 `LearningProjector.projectMemory`（日历日，最正确）写持久状态；另两处是只读估算。
因此本条的准确表述是：**用户看到的可提取性 `R` 与排期所依据的 `S` 可能用不同口径算出，UI 会自相矛盾**
（典型示例：「掌握度说『较稳』、遗忘风险说『高』」），而**不改持久数据**。

另有一个更微妙的问题：**LEGACY 的 kill-switch 不只切换曲线形状，还顺带切换了时间口径**——
指数分支用未取整的小数天，幂律分支用取整的天。回滚开关同时改变两个变量，让「回滚后行为差异」无法归因。

> **已修（2026-09-13 批 3 第 3 项）。** 读侧两个出口（`ForgettingCurve.estimateAt` 的 FSRS-6 分支、
> `KnowledgeReviewQueue.knowledgeRecallRiskByNode`）统一到**写入侧本来就在用的**那个口径：
> learner 本地日历日差，整日。口径的定义收进新类型 `ReviewCalendar`，两个读侧出口都**委托**给它。
> **最后那个「kill-switch 顺带切换口径」的问题留在原样**——`LEGACY_EXPONENTIAL` 分支**故意不动**：
> 它是 spec §2.20 的回滚开关，语义由用例逐位冻结在精确毫秒上，改它等于改回滚语义本身。
> 于是"回滚后行为差异无法归因"这条**仍然成立**，但**登记为已知**（不是遗漏）：
> 回滚开关同时改变曲线形状与时间口径是**它自己的**语义，不是 F-02 的三种口径问题。
>
> **方向被 S-11 纠正过**：本记录作者原推荐"统一到分数天"（已被用户采纳），回读一手资料后
> 自己推翻——分数天是 **FSRS-7** 的能力，本项目实现的是 **FSRS-6**。最终统一到整天，
> **spec 一个字都没改**（它本来就写着 t = 本地日历日差）。做法、范围枚举、3 条变异与证据见 §11 批 3 第 3 项。

### F-03　重试一次 chat 证据写入会抛异常——而 DAO 自己的注释承诺它是静默 no-op
`P1` · `✔ 对抗复核 CONFIRMED` ＋ `◆ 一手核实` · `core/database/.../dao/ChatEvidenceDao.kt:26-35, 72, 83`

该 DAO 的 KDoc 白纸黑字写着：`evidence_id` 是确定性幂等键，**重试必须静默 no-op，而不是让整个事务在主键冲突上失败**。
同一文件往下 50 行，代码违反了自己写的契约：

- `:69` 先为 evidence 分配一个新序列 → `:82 insertAll(accepted)` 用 **IGNORE** 跳过重复证据行（符合承诺）
- → `:83 insertOutboxRows(outboxRows)` 用 **ABORT**，而 `outboxId` 由 `evidence_id` 确定性派生
  （`:72 "learning-outbox:${learner_id}:$EVENT_KIND:${evidence_id}"`），
  `projection_outbox` 另有 `(event_kind, event_id)` 唯一索引与 `outbox_id` 主键**双重冲突**
  → 抛 `SQLiteConstraintException` → 整个事务回滚。

**可达性已确证**：`RoomModelTaskRepository.kt:690-691` 的注释明确要求「同一 request 的重试／多轮共享同一 evidenceId 命名空间，
让 MASTERY_UPDATE 的 evidence_id 确定性派生（重试不重复落库）」，而 `KnowledgeQuizFeedbackWriter.kt:64` 用
`"knowledge-quiz:$requestId:$knowledgeNodeId"` 拼确定性 id。异常被工具循环的 catch 吞成通用 failed 结果，
模型看到的是「写失败」，而非文档承诺的静默 no-op。

**为什么它能活到今天**：现有集成测试**全部使用不同的 `evidence_id`**，从没有一条重复写入用例；
唯一「覆盖」重试的单测用的是 `FakeStudyDatabasePort`，并且断言里带 `.distinct()`——那个断言在结构上无法观察这个失败。

> **修复前必读 S-1**：把 ABORT 改成 IGNORE 会留下**永久账本 GAP**，比现状更糟。

---
## 5. 完整缺口登记

> 本节列出的是 57 条中的 55 条有效项；被推翻的 2 条（`mastered-anchor-unverified`、`attention-floor-unreachable`）
> 仍保留在下面 `5.4` 的原始列表中，但已在第 6 节标注 REFUTED 与理由。主循环新增的 F-01 见第 4 节。
**严重度分级**（沿对抗复核者使用的定义）：
`P0`＝静默错误结果／崩溃／数据损坏　`P1`＝真实缺陷、影响有界　`P2`＝潜在风险或当前不可达　`P3`＝结构性。

计数：审计者共提出 **57 条**，其中 **2 条**被对抗复审判为 REFUTED（不计入缺陷）→ **55 条有效**；
加上主循环在复核阶段自行确认的 **F-01**（`decay` 参数四处冻结，见第 4 节）→ **56 条登记在册**。

| 严重度 | 条数 |
|---|---|
| `P1` 真实缺陷、影响有界 | 9 |
| `P2` 潜在风险或当前不可达 | 26 |
| `P3` 结构性 | 21 |
| **合计** | **56** |

### 5.1 调度与记忆数学内核

合计 **11** 条（P1 ×1 ／ P2 ×4 ／ P3 ×6）

#### 5.1.1　`future-timestamp-never-clamped`

- **严重度**：`P1`　**类型**：`correctness`
- **标题**：未来时间戳事件不被钳回，FUTURE_TIMESTAMP_CLAMPED 永不可达，可把稳定性钉到上限并污染整库排程
- **位置**：`core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/LearningProjector.kt:239`

**失败路径**

> 设备时钟被前拨（手动改时间/NTP 跳变）后提交的任何 attempt/reveal 其 occurredAtEpochMillis 在未来。投影里 effectiveAt = maxOf(projectedAt, occurred) 只保证不早于事件本身，从不做上界钳制：该事件的 studyDay 在未来 → elapsedCalendarDays 巨大 → 走跨日成功分支，R≈0 → 稳定性被 clampStability 钉到 STABILITY_MAX=36500，nextReview = 未来时间 + 36500 天（约 100 年），该卡实质永久退出排程；同时 lastReviewedAtEpochMillis 变成未来，之后所有真实时间的复习都落到 atEpochMillis<lastReviewed 的 TIME_ROLLBACK 分支。更进一步，ProjectionCheckpoint.projectedAt/generatedAt 随之为未来值，StudyReviewPlannerService.planningContext(StudyReviewPlannerService.kt:451,456) 把 planningAtEpochMillis = maxOf(startOfDay, decisionWatermark) 抬到未来，于是之后每一份复习计划都在『未来的 now』上计算，全部卡片 elapsed 巨大、dueRisk 饱和。

**证据**

> LearningProjector.kt:239 `val effectiveAt = maxOf(projectedAt, event.occurredAtEpochMillis)`（replay 路径同式 434）；trust 赋值 LearningProjector.kt:985-992 `else -> EventTimeTrust.FUTURE_TIMESTAMP_CLAMPED` 的 else 分支在 effectiveAt>=occurred 恒成立下不可达，仅 LearningState.kt:594 定义处 + 此处 else 出现，全仓无其他生产点（grep FUTURE_TIMESTAMP_CLAMPED 仅命中枚举与这一处）。契约却写明该值语义为『clamped back to projection watermark』(LearningState.kt:592-596)。上游无钳制：StudyWriteContext.studyDayAt(StudyWriteContext.kt:32) 直接按事件毫秒换算；StudyRatingSubmissionService.kt:69-71 原样透传 occurredAtEpochMillis。

审计者自评信心：`verified`

#### 5.1.2　`assistance-channel-empty`

- **严重度**：`P2`　**类型**：`dead-mechanism`
- **标题**：hint/reveal 逐事件通道结构性恒空：MasteryEvidencePolicy 的 4 个分支不可达，评级映射的 HARD(提示) 分支是死代码
- **位置**：`core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/MasteryEvidencePolicy.kt:133`

**失败路径**

> AssessmentSubmissionContext.hintWasUsed / answerWasRevealed 只由 persistedAssistance 推导，而唯一生产调用点 StudySubmissionPreparer.prepareChoiceSubmission 构造该 context 时从不传 persistedAssistance（默认 emptyList），故两者恒 false。于是 evaluate() 中依赖它们的 4 个分支永不进入：:78(INCORRECT_AFTER_REVEAL)、:89(ANSWER_WAS_REVEALED)、:100(INCORRECT_AFTER_HINT)、:133(CORRECT_AFTER_HINT)。后果：reason=CORRECT_AFTER_HINT 在生产中永不产生，于是 FsrsScheduleMath.kt:232-234『提示后答对→HARD』这条专门为『提示后答对不等于独立答对』设置的保守评级分支永不被喂数据（同样 INCORRECT_AFTER_HINT 在 reportedRatingFor 的条目也空转）。整个 persistedAssistance 供给者 TutorAssistanceCoordinator 在生产中从未被构造（仅测试构造），说明该采集通道尚未接通。

**证据**

> MasteryEvidencePolicy.kt:100,133（依赖 context.hintWasUsed）与 :78,89（依赖 context.answerWasRevealed）；context 定义 Assessment.kt:158-164 由 persistedAssistance 推导；唯一生产调用 StudySubmissionPreparer.kt:197-206 未传该字段。消费不到的分支 FsrsScheduleMath.kt:232-234。供给者 TutorAssistanceCoordinator.kt:15 全仓无生产构造（grep 仅其自身与 TutorAssistanceCoordinatorTest）。

审计者自评信心：`verified`

#### 5.1.3　`fsrs-go-nogo-gate-dead`

- **严重度**：`P2`　**类型**：`dead-mechanism`
- **标题**：spec §2.20 的上线门 fsrsBeatsBaseline 全仓零消费者，而 FSRS 默认启用
- **位置**：`core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/SchedulingEvaluation.kt:97`

**失败路径**

> spec §2.20 规定『上线门：FSRS-6 log-loss < 指数基线，否则回退』，但 SchedulingEvaluationReport.fsrsBeatsBaseline 只被定义、没有任何读取点（连测试都不读它）；唯一能产出该值的 StudyExperienceRepository.evaluateSchedulingModels() 在生产中无人调用（grep 全仓仅接口声明、RoomBacked 转发、以及仪器化测试的空实现）。而 SchedulingOptions.useFsrsScheduling 默认 true（StudyExperienceRepository.kt:622），即 FSRS-6 默认启用、上线门从未被咨询过。结果：文档承诺的模型选择闸门是空转机制，FSRS 是否真的优于基线从不在运行链路里被判定。

**证据**

> 定义 SchedulingEvaluation.kt:97-100；grep `fsrsBeatsBaseline` 全仓仅命中这一处。调用链仅到 core/domain/StudyExperienceRepository.kt:494 与 core/data/.../StudySchedulingCalibration.kt:40，无 app/feature 调用点。默认开关 StudyExperienceRepository.kt:618-623 `useFsrsScheduling: Boolean = true`。

审计者自评信心：`verified`

#### 5.1.4　`optimizer-no-evaluate-gate`

- **严重度**：`P2`　**类型**：`spec-divergence`
- **标题**：参数优化器把结果直接写回设置，未经 spec §2.11 要求的 evaluate()/log-loss 门，且 8 条样本即可触发
- **位置**：`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/StudySchedulingCalibration.kt:88`

**失败路径**

> spec §2.11 要求『必须 evaluate()（log-loss 目标 0.35–0.45）』后才替换 w。实际 optimizeSchedulingParameters 只要 mode != INSUFFICIENT_DATA 就无条件 store.setOptimizedParameters(result.parameters)，既不调用 SchedulingEvaluationHarness.evaluate() 也不检查 0.35–0.45 或 fsrsBeatsBaseline。门槛低至 MIN_SAMPLES_FOR_FITTING=8 条可预测样本（mode=INITIAL_STABILITY_ONLY，拟合 w0..w5）。该结果在下次启动被读入并直接作为调度参数（Application.kt:167-173 → RoomBackedStudyExperienceRepository.kt:152-155 FsrsMemoryUpdateModel(parameters=...)），即用不足 64 条数据的拟合值替换全部 21 个参数中的 6 个并影响所有卡片间隔。退出条件仅靠 fit() 内部『验证 loss 未改善则回退默认』约束，无规格要求的绝对质量门。

**证据**

> StudySchedulingCalibration.kt:86-89 `val result = FsrsParameterOptimizer.optimize(samples); if (mode==INSUFFICIENT_DATA) return null; store.setOptimizedParameters(result.parameters)`；触发点 app/.../SmartMistakeBookApplication.kt:299-301（每次启动静默 runCatching 调用）；阈值 SchedulingEvaluation.kt:571-572；读回 RoomBackedStudyExperienceRepository.kt:108-109,152-155 与 Application.kt:167-173。

审计者自评信心：`verified`

#### 5.1.5　`sameday-aggregation-missing`

- **严重度**：`P2`　**类型**：`spec-divergence`
- **标题**：spec §2.15 P0 的『同卡同日多条证据聚合为一条当日评级(G_agg=min)』未实现，低稳定性卡的同日重复 Good 会复利抬升 S
- **位置**：`core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/MemoryUpdateModel.kt:80`

**失败路径**

> spec mastery-scheduling §2.15（标 P0）要求同卡同日多条证据聚合成一条当日评级 G_agg=min(G_raw)、日终落地。实现改为对每条同日证据立即各跑一次 shortTermStability，逐条相乘。对稳定性较大(S≳2.2)的卡，乘子被 coerceAtLeast(1.0) 压到 1，聚合与否等价——实现注记正是据此声称『聚合无额外自由度』；但该论证在 S<1 时不成立：S=0.212（AGAIN 初见）时同一次同日 Good 的乘子 = e^{w17·w18}·S^{−w19} = 1.0507×0.212^{−0.0658} ≈ 1.1637。连续 3 次同日 Good 得 S≈0.324，而按 G_agg=min=Good 只该得 S≈0.247，稳定性被抬高约 31%，且随同日证据条数继续复利。这条 P0 规则的保守聚合语义在当前实现里没有对应物。

**证据**

> spec docs/specs/mastery-scheduling-spec.md:125（聚合条款）、:306（实现注记声称『由短程分支的保守性覆盖，聚合无额外自由度』）。代码 MemoryUpdateModel.kt:80-81 对每条 elapsedDays<1 证据各自调用 shortTermStability；短程公式 FsrsScheduleMath.kt:108-115。

审计者自评信心：`verified`

> **已处理（2026-09-13 批 3 第 1 项）：按 S-4 的两步走完——先确证可达性，再用数字决定，最后"订正说明"而未改代码。**
> 三条结论与一个纠正：
> ① **可达性成立，但可达的那条路与原判以为的不同**——1h 冷却的视觉通道已被 2026-09-06 的
> 结构化场景渲染隔离关掉（`VisualInteractionEventSink` 无生产写入方），当前实际可达的是
> **主观评级那条 6h 冷却**；
> ② **放大有上界且与证据条数无关**：短程乘子的不动点 `S* = 2.1210577` 天，
> 反复同日成功最多把 `S` 推到 `max(S₀, S*)`；在调度上**最多值一天**（1 天 vs 2 天）；
> ③ spec 那条其实是**两句话**且**不唯一地合成一个算法**，实现按第二句（同日重复走 short_term）
> 照字面执行，真正的偏差只有"跨日更新用的评级不是前一日证据的 `min`"这一条。
> **代码零改动**，`docs/specs/mastery-scheduling-spec.md` §10 的实现注记整段重写；
> 验收是新增的 `FsrsSameDayBoundTest` 六条（走产线 `updateMemory`）＋ 2 条变异按集合相等验证。
> 完整推导、倍率表与"为什么不实现"见 §11 批 3 第 1 项。

#### 5.1.6　`hlr-shadow-manager-dead`

- **严重度**：`P3`　**类型**：`dead-mechanism`
- **标题**：HLRShadowModeManager 是并行影子实现，生产从未构造
- **位置**：`core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/HLRShadowMode.kt:13`

**失败路径**

> 生产实际用的是 HLRPredictionAuditService（RoomBackedStudyExperienceRepository.kt:167、StudyReviewPlannerService.kt:60）；HLRShadowModeManager 只在测试中被构造过，其 predictShadow/extractFeatures 无生产消费者。它还残留一个 0..1 尺度的难度默认值（extractFeatures 的 difficulty: Double = 0.5），而全工程已迁到 1..10——一旦被启用会让 HLRFeatures.init(difficulty in 0.0..1.0) 直接抛异常，是一枚埋雷。

**证据**

> grep `HLRShadowModeManager` 全仓仅 HLRShadowMode.kt:13 定义 + HLRPredictionAuditService.kt:29 的 KDoc 引用；无 `HLRShadowModeManager(` 生产构造（仅测试）。难度默认值 HLRShadowMode.kt:62；HLRFeatures 约束 HalfLifeRegression.kt:126。

审计者自评信心：`verified`

#### 5.1.7　`mastery-status-stale-unassigned`

- **严重度**：`P3`　**类型**：`dead-mechanism`
- **标题**：MasteryStatus.STALE 从不被赋值，所有 status==STALE 判定恒 false
- **位置**：`core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/LearningState.kt:581`

**失败路径**

> projectChatEvidence 与 projectMastery 只会产出 UNKNOWN/LEARNING/MASTERED/CONFLICTED，全仓没有 status = MasteryStatus.STALE 的赋值点（含持久化映射）。ReviewPlannerV2.kt:509 与 ReviewPlanner.kt:353 里的 `state.status == MasteryStatus.STALE` 子句永远为 false，真正生效的只是同句的 lastEvidenceAt 时间判断。删除该枚举值不会改变任何行为，若有新写入方误设 STALE 也无从校验。

**证据**

> grep `MasteryStatus.STALE` 全仓仅命中读取点 ReviewPlanner.kt:353、ReviewPlannerV2.kt:509、AdaptiveQuestionSelector.kt:158，无赋值点；枚举定义 LearningState.kt:581。

审计者自评信心：`verified`

#### 5.1.8　`projector-safeadd-unused`

- **严重度**：`P3`　**类型**：`structural`
- **标题**：LearningProjector.safeAdd 是未使用的私有函数
- **位置**：`core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/LearningProjector.kt:1133`

**失败路径**

> 定义了 Long 饱和加法 safeAdd，但全仓无调用点。时间加成实际由 addDays(MemoryUpdateModel.kt:113-116) 与 ForgettingCurve.reviewAtTargetRetention 里的溢出判断各自实现，safeAdd 不参与任何路径。

**证据**

> grep `safeAdd` 全仓仅命中 LearningProjector.kt:1133 的定义处。

审计者自评信心：`verified`

#### 5.1.9　`recall-risk-day-convention-split`

- **严重度**：`P3`　**类型**：`spec-divergence`
- **标题**：同一张卡在知识点队列与错题排程里用两套 t 口径算 R（分数天 vs 整日历日）
- **位置**：`core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/KnowledgeReviewQueue.kt:118`

**失败路径**

> spec §2.1/§2.2 定义 t 为 learner 本地日历日差（整日）。ForgettingCurve 的 FSRS6 分支按整日地板（ForgettingCurve.kt:62 floor(elapsedMillis/DAY)），错题排程的 dueRisk 读它；但知识点复习的 knowledgeRecallRiskByNode 用未取整的墙钟分数天（KnowledgeReviewQueue.kt:118-123）。同一张卡同一时刻，两个界面对 R 的估计不同：卡 last reviewed 12h 前、S=1.0 时，排程侧 elapsedDays=0→R=1.0，知识点侧 elapsedDays=0.5→R≈0.94。结果是知识点复习队列的到期风险与错题排程对『是否快到复习点』的判定系统性偏移。

**证据**

> KnowledgeReviewQueue.kt:118-123 `elapsedDays = (now - lastReviewedAt).coerceAtLeast(0)/DAY_MILLIS` 后直接 FsrsScheduleMath.retention；对照 ForgettingCurve.kt:59-64 的 `floor(elapsedMillis/DAY).coerceAtLeast(0)` 与 spec docs/specs/mastery-scheduling-spec.md:32（t=本地日历日差）。

审计者自评信心：`likely`

> **已修（2026-09-13 批 3 第 3 项）。** 两处读侧统一到 spec 写的那个口径（learner 本地日历日差，整日），
> 定义收进新类型 `ReviewCalendar`；`KnowledgeReviewQueue` 不再自己算天数，改为收一条配置好的曲线并
> 委托给它——**因此这条同时也收掉了 N-14 的队列那一半**（它原先读的是出厂衰减）。
> 本条的另一半（`ForgettingCurve` 的墙钟整日地板）与本条的失败路径不完全相同：地板口径的错法不是
> "与分数天不同"，而是"跨午夜时把本地日历日差算成 0"。三条变异的命中集合、以及"两条用例各守一侧"
> 的读数纪律见 §11 批 3 第 3 项。

#### 5.1.10　`shorttermreview-field-unread`

- **严重度**：`P3`　**类型**：`dead-mechanism`
- **标题**：MemoryUpdateResult.shortTermReview 全仓无读取点（恒 false）
- **位置**：`core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/MemoryUpdateModel.kt:16`

**失败路径**

> FsrsMemoryUpdateModel 恒返回 shortTermReview=false，LearningProjector 消费 update 时只取 stabilityDays/difficulty/nextReviewAtEpochMillis，从不读 shortTermReview；LegacyExponentialMemoryUpdateModel 认真计算了它（:161-163）也无消费者。这是一条『看着接好了』的返回字段，改动其语义不会有任何测试或行为变红。

**证据**

> grep `shortTermReview` 全仓仅命中 MemoryUpdateModel.kt:16(声明),109,161,163,177（全是写入/局部使用），无任何读取；LearningProjector.kt:876-899 只取三个字段。

审计者自评信心：`verified`

#### 5.1.11　`spec-interval-inverse-sign`

- **严重度**：`P3`　**类型**：`spec-divergence`
- **标题**：spec §2.3 间隔反解公式丢了负号（r*^(1/w20)），代码用的是正确的 r*^(-1/w20)
- **位置**：`docs/specs/mastery-scheduling-spec.md:38`

**失败路径**

> 权威 spec §2.3 写 I(r*,S) = (S/FACTOR)·(r*^(1/w20) − 1)，但由 R(t,S)=(1+FACTOR·t/S)^(−w20) 解出必须是 r*^(−1/w20)。按 spec 字面值算：r*=0.9、w20=0.1542 时 0.9^(1/0.1542)=0.505，减 1 得 −0.495 → 反解出负间隔（被 coerceIn(1,..) 兜成 1 天），任何照文档重写的人都会把调度间隔压成 1 天。实现 FsrsScheduleMath.kt:84 用 desiredRetention.pow(1.0/decay)（decay=−w20）即 r*^(−1/w20)，与推导及 py-fsrs _next_interval 一致，是正确的；需要改的是文档。

**证据**

> spec docs/specs/mastery-scheduling-spec.md:38（及 docs/research/mastery-math-modeling.md:55 同样写法）vs FsrsScheduleMath.kt:83-85 `val decay = -DEFAULT_PARAMETERS[20]; val raw = (stabilityDays/factor(decay))*(desiredRetention.pow(1.0/decay) - 1.0)`。

审计者自评信心：`verified`


### 5.2 事件溯源与投影完整性

合计 **5** 条（P1 ×1 ／ P2 ×3 ／ P3 ×1）

#### 5.2.1　`version-replay-trigger-untested`

- **严重度**：`P1`　**类型**：`missing-gate`
- **标题**：PROJECTOR/EVIDENCE 版本升级触发全量重放的唯一判定没有任何测试覆盖，改坏即静默不重放
- **位置**：`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/StudyProjectionDrainer.kt:75`

**失败路径**

> 把 StudyProjectionDrainer.kt:75 的 `previous.checkpoint.projectorVersion != LearningProjector.VERSION` 改成比较错误字段、或恒 false、或误写成 `==`，没有任何测试会变红。全仓 grep `StudyProjectionDrainer` 只在 main 命中（测试零引用）；唯一走 drainer 的 JVM 单测用 fake 把 commitProjection 直接 `error(...)`，且所有 seed 快照的 projectorVersion 都等于 LearningProjector.VERSION。后果：LearningCoreVersions.kt:8-21 明文记载『v5→v6 / v6→v7 必须靠版本不匹配触发全量重放，否则修复对已投影事件静默无效』这条唯一机制一旦回归，已有库会永久保留旧版本算出的 lastEvidenceAt/lastEvidenceDirection 与旧 delta_t 口径，且 UI 不报错。

**证据**

> StudyProjectionDrainer.kt:75 `val requiresReplay = previous.checkpoint.projectorVersion != LearningProjector.VERSION ||`；:83-90 仅在 requiresReplay 时 commitFullReplay。RoomBackedStudyExperienceRepositoryTest.kt:2536-2537 `override suspend fun commitProjection(commit) = error("commitProjection is not used by these focused tests")`；同文件 :1624/:1636/:1678 seed `projectorVersion = LearningProjector.VERSION`。测试树 grep `StudyProjectionDrainer|requiresReplay` 无命中（仅 LearningProjectorTest.kt:268 注释提及）。

审计者自评信心：`verified`

#### 5.2.2　`bounded-drain-throws-on-backlog`

- **严重度**：`P2`　**类型**：`correctness`
- **标题**：未消费账本超过 6400 条时 drain() 在 64 步后抛异常，当前快照发布进入 ERROR
- **位置**：`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/StudyProjectionDrainer.kt:138`

**失败路径**

> drain() 是 `repeat(MAX_PROJECTION_DRAIN_STEPS=64)`（:36），每步最多消费 PROJECTION_BATCH_SIZE=100（:189）条。当存在 >6400 条未消费 outbox 事件（例如恢复一份 checkpoint 远落后于账本头的备份，或批量导入产生大量事件后再首次启动）时，64 步内无法追平，走到 :138 `throw ProjectionCasConflictException("Projection did not drain within the bounded work limit")`。该异常经 RoomBackedStudyExperienceRepository.runOperation → publishFailure 把 StudyDataStatus 置 ERROR 并向调用方抛出。虽然下一次 drain 会从新 checkpoint 继续、可能自愈，但触发它的那次快照发布对用户是错误态；且正常使用时每次写入即 drain，不会积累到 6400，故属罕见但可达的埋雷。

**证据**

> StudyProjectionDrainer.kt:36 `repeat(MAX_PROJECTION_DRAIN_STEPS)`；:189 `PROJECTION_BATCH_SIZE = 100`；:191 `MAX_PROJECTION_DRAIN_STEPS = 64`；:138 `throw ProjectionCasConflictException("Projection did not drain within the bounded work limit")`；RoomBackedStudyExperienceRepository.kt:1002-1011 runOperation catch → publishFailure。

审计者自评信心：`likely`

#### 5.2.3　`correction-ledger-never-fed`

- **严重度**：`P2`　**类型**：`dead-mechanism`
- **标题**：AttemptCorrection 账本事件在生产没有任何写入方，修正→全量重放整条通道不可达
- **位置**：`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/RoomStudyDatabase.kt:1122`

**失败路径**

> port 方法 appendAttemptCorrection（LearningProjectionPort.kt:58，实现 RoomStudyDatabase.kt:1122）在全仓只被 instrumentation 测试（StudyDatabaseInstrumentedTest.kt:229/331/479）和 core/data 测试里的 error 覆写调用，app/feature 无任何调用点。于是下列机制在生产永不可达：ProjectionTransactionDao.kt:475-486 读到 correction 才返回的 FULL_REPLAY_REQUIRED；LearningProjector.kt:414-419/437-445/525-533 的修正应用与 replay 修正分支；以及 :547 写入的 correctionWatermarkEpochMillis 恒为 null。后者被 ReviewPlanner.kt:523 与 ReviewPlannerV2.kt:784 读入决策指纹，等于该输入恒定。与 system-blueprint.md:642-643『撤销刚才结果生成 AttemptCorrection，不删除原事件』及 :1736 的验证要求相悖。

**证据**

> LearningProjectionPort.kt:58 `suspend fun appendAttemptCorrection(...)`；RoomStudyDatabase.kt:1122-1132 实现；grep `appendAttemptCorrection` 仅命中 port 定义、RoomStudyDatabase 实现、及 StudyDatabaseInstrumentedTest.kt:229/230/331/332/357/479 与 RoomBackedStudyExperienceRepositoryTest.kt:2411-2413（`error("appendAttemptCorrection is not used by these focused tests")`）。ProjectionTransactionDao.kt:475-486 `if (row.eventKind == EVENT_KIND_CORRECTION) return batchStop(..., FULL_REPLAY_REQUIRED, ...)`。

审计者自评信心：`verified`

#### 5.2.4　`shadow-predictions-discarded`

- **严重度**：`P2`　**类型**：`dead-mechanism`
- **标题**：全量重放生成的影子预测被计算后直接丢弃，审计/标定回路从未收到数据
- **位置**：`core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/LearningProjector.kt:1140`

**失败路径**

> replay() 在 :557 调 `generatePredictions(snapshot, ordered, projectedAt)`（注释 :1136-1139 自称『shadow predictions that can be compared with actual outcomes later for calibration』），并在 :572 填入 LearningProjectionResult.predictions。但唯一的消费方 StudyProjectionDrainer.commitFullReplay 只取 result.snapshot 与 result.presentationProjectionStates（:158-170），predictions 不落库、不传 sink；core/app/feature 全仓 grep `.predictions` 除 LearningProjector 自身外无读取点。每次版本升级/陈旧自愈/修正触发的全量重放都会对每个 attempt×attribution 重算一批预测并立即丢弃，标定回路永远是空的。

**证据**

> LearningProjector.kt:557 `val predictions = generatePredictions(snapshot, ordered, projectedAt)`；:572 `predictions = predictions,`；:1140-1175 生成逻辑。StudyProjectionDrainer.kt:158-170 `database.commitProjection(ProjectionCommit(... snapshot = result.snapshot, presentationProjectionStates = result.presentationProjectionStates))`（无 predictions）。grep `LearningProjectionResult|\.predictions`：main 仅 LearningProjector.kt，测试仅 FsrsProjectionBehaviorTest.kt 构造用；app/feature 零命中。

审计者自评信心：`verified`

#### 5.2.5　`projection-consumption-write-only`

- **严重度**：`P3`　**类型**：`structural`
- **标题**：projection_consumption 表只写不读且无上限增长
- **位置**：`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/dao/ProjectionTransactionDao.kt:754`

**失败路径**

> commitProjection 为每个被消费事件插入一行 ProjectionConsumptionEntity（:754-763，含 projectorVersion 与 consumedAt），但全仓没有任何 SELECT/@Query 读取该表（仅 entity 定义 LearningEntities.kt:535、StudyDatabase 注册与 import 处出现），也无清理/截断。账本事件越多该表越大，纯存储无查询收益；若将来要靠它按投影版本判断重算，FULL_REPLAY 只对 `eventSequence > expectedCheckpoint` 的事件补写（commitFullReplay :155-157），存量事件在新版本下不会有消费记录，该表也不足以支撑。

**证据**

> ProjectionTransactionDao.kt:754-763 `rows.map { ProjectionConsumptionEntity(...) }.insertWhenNotEmpty(::insertConsumptions)`；grep `projection_consumption|ProjectionConsumptionEntity` 命中仅为 entity 定义（LearningEntities.kt:535）、StudyDatabase 注册、import 语句与 ProjectionTransactionDao 插入点，无任何查询。

审计者自评信心：`verified`


### 5.3 知识库联结与掌握度聚合

合计 **8** 条（P1 ×2 ／ P2 ×4 ／ P3 ×2）

#### 5.3.1　`pseudo-attribution-unconditional`

- **严重度**：`P1`　**类型**：`correctness`
- **标题**：评级/自评证据无条件落到 pseudo:<SUBJECT>，有真绑定的题其真 KC 永远收不到这些证据
- **位置**：`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/StudySubmissionPreparer.kt:70`

**失败路径**

> 一道已被 organization 管道接受真绑定（taxonomy_version=organization-v1 / user-corrected-v1）的题，学生在复习会话按四键评级或提交三档自评 → prepareRatingSubmission/prepareSelfReportSubmission 无条件调用 buildPseudoAttribution → 证据快照的 attributions 只有 `pseudo:<SUBJECT>` 一个节点 → LearningProjector 只更新 pseudo KC 的 masteryScore/evidenceMass，真 KC 的掌握度完全不增长。后果：真 KC 永远停在 UNKNOWN/无证据（weakness 恒为 1、KC_MASTERY_DROP 不传导、前置判定把真 KC 视为未知），而 pseudo:<SUBJECT> 累积全部证据成为假的主知识点。

**证据**

> StudySubmissionPreparer.kt:70-76 `prepareRatingSubmission` 开头即 `val pseudoAttributions = buildPseudoAttribution(...)`；:92 `attributions = pseudoAttributions`；:253-259 同样无条件的 `prepareSelfReportSubmission`；:154-179 `buildPseudoAttribution` 只调 ensurePseudoKnowledgeBinding，无“已有真绑定则跳过”分支。RoomKnowledgeBaseStore.kt:36-91 无条件构造 `val knowledgeNodeId = "pseudo:${subject.uppercase()}"` 并插入绑定（insertKnowledgeBindings 在 ProblemOrganizationDao.kt:196 为 IGNORE）。LearningProjector.kt:749-766 只对 attributions 记账、:965 `val weight = attempt.evidence.weight` 对每个 DIRECT attribution 全量入账。权威规格 mastery-scheduling-spec.md:318 原文限定“自评/评级快照对**无绑定题**自动携带伪归因”，代码未做该限定。

审计者自评信心：`verified`

**2026-09-12 已修（批 1 第 1.4 项，worktree `audit/kernel-readiness`，未提交）。**

修法**不在** `StudySubmissionPreparer` 加分支，而在**唯一的写入收口点**
`RoomKnowledgeBaseStore.ensurePseudoKnowledgeBinding` 加一条判定：该题已有
`sourceType != PSEUDO_FALLBACK` 的绑定时返回 `null`（`buildPseudoAttribution` 收到 `null` 即返回
`emptyList()`——这条路径本来就写好了，只是从来没有被触发过）。判定**必须放在伪知识点物化之后**，
理由与实测见 §11 批 1 第 1.4 项。

`DatabaseContract.kt:458` 的 `attributions.isNotEmpty() || LocalReviewSelfReportContract.matches(snapshot)`
表明「无归因的自评快照」正是**契约已经认可的状态**（`LocalReviewSelfReportContract.matches` 要求
`attributions.isEmpty()`，`LearningState.kt:83-91`），所以这不是新增语义，是把一个已定义的守卫补上缺失的谓词。

> **范围订正**：本条**不**连带关闭 §5.3.6。原因见 §3 **S-9**（finding 4 的主路径是「先伪后真」，
> 1.4 按定义放行），修法登记为批 1 第 **1.7** 项。

#### 5.3.2　`remediation-gated-on-debug-fixture`

- **严重度**：`P1`　**类型**：`dead-mechanism`
- **标题**：前置补救/重教通道在 release 恒为 null：门槛是仅 debug 存在的 fixture 目录
- **位置**：`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/RoomBackedStudyExperienceRepository.kt:499`

**失败路径**

> release（strictOffline/localFirst 任一 flavor 的 release 构建）中 StudyFixtureRegistry.source 保持 EmptyStudyFixtureSource → repository.teachingArtifact(pu) 对所有题返回 null → prerequisiteRemediation 第一行 `?: return null` 直接返回 null，reTeachOpening 同理。§2.9 前置补救与 §2.16 重教两条通道在任何真实设备上都不产出内容；UI 侧 SmartMistakeBookDestinations.kt:121-125 也因此恒走 `else null`。单元测试是绿的，只因为它们用 M1CuratedFixtureSource。

**证据**

> RoomBackedStudyExperienceRepository.kt:460-461 `override suspend fun teachingArtifact(...) = fixtureSource.teachingArtifactForPracticeUnit(practiceUnitId)`；:476 `val artifact = teachingArtifact(practiceUnitId) ?: return null`（reTeachOpening）；:499 同一句（prerequisiteRemediation）。StudyFixtureSource.kt:40-43 `object StudyFixtureRegistry { @Volatile var source: StudyFixtureSource = EmptyStudyFixtureSource }`，:31-33 EmptyTeachingArtifact 返回 null。M1FixtureInitProvider 只在 `core/data/src/debug/AndroidManifest.xml` 注册（app 各 flavor 的 manifest 无该 provider）。测试反证：RoomBackedStudyExperienceRepositoryTest.kt:1365 `fixtureSource = M1CuratedFixtureSource`，而依赖它的测试在 :1095 断言 remediation 非空。

审计者自评信心：`verified`

> **2026-09-13 处置：本条按「两条通道分开」结清——前置补救已修（批 2 第 3 项），开场重教登记为 N-16。**
> 本条把两条通道合成一条记，处置时必须拆开，因为它们**缺的不是同一个东西**：
> - **前置补救**：缺的**不是内容源**（材料一直只读 `knowledge_teaching_material`），
>   而是「这道题绑在哪些 KC 上」这个范围取自策展件。已改为读错题读侧的
>   `knowledge_node_ids`（当前修订 ＋ 最近一次组织轮），并把装配侧的门由 `artifact != null`
>   改成"题存在且数据就绪"、再由实拍屏真的把它渲染出来——**三层都动了才叫修好**：
>   原文只提到数据层，而**只改数据层在生产里什么都不会变**（渲染点只有策展屏，
>   实拍屏连参数都没有）。
> - **开场重教**：同样的范围问题，但**没有任何一屏渲染过它**，而它按本仓自己的说法是
>   "必经步骤"——放哪里是**产品可见**的选择。**登记为 §12.5 N-16，不擅自设计。**
>
> 本条"UI 侧 `SmartMistakeBookDestinations.kt:121-125` 也因此恒走 `else null`"这一句是对的，
> 但它只说了**加载**被挡住；**更深的一层是渲染**——即使加载出来，实拍屏也不会显示。

#### 5.3.3　`binding-strength-no-consumer`

- **严重度**：`P2`　**类型**：`spec-divergence`
- **标题**：binding.strength 写入三处、无任何聚合消费；研究文档仍称其为分摊权重
- **位置**：`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/RoomKnowledgeBaseStore.kt:76`

**失败路径**

> strength 只在写入端被赋值（organization 按 attributedSteps/stepAttributions.size 计算，伪绑定/grounding/seed 恒 1.0），除校验与视图曝光（LibraryCatalogView/LibraryKnowledgeQuestionLattice 的 binding_strength 列）外没有任何掌握度聚合读取它。于是“多 KC 题按 strength 分摊”不成立；更糟的是 docs/research 的两份规格仍把它写成权威分摊权重，任何据此实现/验收的人会得到与线上相反的心智模型。

**证据**

> 写入：RoomMistakeOrganizationRepository.kt:995-1001、:1008；RoomKnowledgeBaseStore.kt:76（伪绑定 1.0）、:744；KnowledgeGroundingDao.kt:346（1.0）。校验/展示：DatabaseContract.kt:888；LibraryCatalogView.kt:114/147；MasterySchedulingMigration.kt:174（lattice 视图）。唯一“消费”是 RoomBackedStudyExperienceRepository.kt:754 把它作为只读字段暴露。规格冲突：mastery-math-modeling.md:190-196 §9 `contribution_k = s·w_e·(strength_k/Σstrength_j)` 与 three-store-linkage-design.md:90-94 §3.2“改为 strength 归一”，而权威 mastery-scheduling-spec.md:171-172 §2.13 明写“binding.strength 降级为排序/展示用途”——代码遵从 §2.13，两份 research 文档过期未改。

审计者自评信心：`verified`

#### 5.3.4　`material-limit-starvation`

- **严重度**：`P2`　**类型**：`correctness`
- **标题**：DAO 的 LIMIT 先于字符预算截断，超大高优先级材料会吃掉补救卡名额
- **位置**：`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/dao/KnowledgeTeachingMaterialDao.kt:57`

**失败路径**

> referencesFor(limit=DEFAULT_LIMIT=4) 先让 DAO 按（role→type→title）ORDER BY 后 LIMIT 4 截断，再由 TutorTeachingReferenceSelector 在 20000 字符预算内二次筛选；selector 对放不下的材料是“跳过继续”。若某前置 KC 的前 4 份材料（按优先级）单份或累计都超过预算，selector 返回空列表 → PrerequisiteRemediationPolicy.offer 返回 null → 补救卡不出现；而排在第 5 位、能放进预算的小材料因 DAO 已截断永远看不到。这是“预算被错类/超大材料吃掉”的可达路径。

**证据**

> KnowledgeTeachingMaterialDao.kt:23-58：`LIMIT :limit` 在 ORDER BY（role rank, material_type CASE, title, material_id）之后；RoomTutorTeachingReferenceRepository.kt:134-141 `if (selected.size < limit && reference.markdownChars <= remainingChars) { selected += ... }`（skip 而非 break），候选集已被 DAO 限死为 limit 条；TutorTeachingReference.kt:67-71 markdownChars=summary+applicability+content+boundary，上限合计可达 52000（TutorTeachingReference.kt:75-78）而总预算仅 20000（TutorTasks.kt:257-258）。

审计者自评信心：`likely`

#### 5.3.5　`visual-attribution-weights-dead`

- **严重度**：`P2`　**类型**：`dead-mechanism`
- **标题**：视觉通道 PRIMARY 0.6 / SECONDARY 0.4 权重被算出但聚合从不消费
- **位置**：`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/VisualInteractionIngestor.kt:80`

**失败路径**

> 多 KC 题的视觉作答按 index 计算 0.6/0.4（并按 SECONDARY pool 均分）写进 KnowledgeEvidenceAttribution.weight；但 LearningProjector.projectMastery 只用 attempt.evidence.weight 给每个 KC 记全量证据，attribution.weight 唯一的读取点是 LearningLedgerFingerprint 做指纹哈希。因此“按 PRIMARY/SECONDARY 分摊证据”这一机制名义上接好了、实际对掌握度零影响；改动 0.6/0.4 只改指纹、不改结果。

**证据**

> VisualInteractionIngestor.kt:80-100 计算 `secondaryWeight` 与 `weight = if (index==0) PRIMARY_VISUAL_ATTRIBUTION_WEIGHT else secondaryWeight`；LearningProjector.kt:962-965 注释“it no longer splits evidence”、`val weight = attempt.evidence.weight`；全仓搜索 attribution.weight 的读取只有 core/model/.../LearningLedgerFingerprint.kt:136 `field("attribution[$index].weight", ...)`。规格 mastery-scheduling-spec.md:83 已声明“视觉通道 PRIMARY/SECONDARY 常数废除”，代码仍保留并计算。

审计者自评信心：`verified`

#### 5.3.6　`visual-taxonomy-group-shadowed-by-pseudo`

- **严重度**：`P2`　**类型**：`correctness`
- **标题**：视觉通道按“最早 accepted 的绑定”选 taxonomy 组，既存的伪绑定会遮蔽真绑定
- **位置**：`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/VisualInteractionIngestor.kt:74`

**失败路径**

> 某题先在无绑定状态下生成计划，createReviewPlan 以 taxonomyVersion="pseudo-plan-v1" 落下一行伪绑定（acceptedAt=T0）；之后 organization 管道补上真绑定（acceptedAt=T1>T0）。此后的视觉交互尝试：bindings 按 (acceptedAt,bindingId) 排序后 first() 是伪绑定 → taxonomyVersion 取到 pseudo-plan-v1 → attributed 只含伪绑定行 → 视觉证据全部记到 pseudo:<SUBJECT>，真 KC 被静默丢弃。同类行在任一更早的伪绑定存在时都会触发。

**证据**

> VisualInteractionIngestor.kt:74-81 `val bindings = ...sortedWith(compareBy(acceptedAtEpochMillis, bindingId))`；:78 `val taxonomyVersion = bindings.first().taxonomyVersion`；:79 `val attributed = bindings.filter { it.taxonomyVersion == taxonomyVersion }`。伪造绑定确会早于真绑定存在：StudyReviewPlannerService.kt:179-189 对无绑定题以 `taxonomyVersion = "pseudo-plan-v1"` 物化；StudySubmissionPreparer.kt:73 以 LocalReviewSelfReportContract.TAXONOMY_VERSION（LearningState.kt:81 = "local-review-self-report-v1"）另建一行。readKnowledgeBindingsForPracticeUnit（ProblemOrganizationDao.kt:221-230）返回该题全部绑定行、不过滤 source_type。

审计者自评信心：`likely`

**2026-09-12 已修（批 1 第 1.7 项，worktree `audit/kernel-readiness`，未提交）。**

修法就在本条自己的选择逻辑上，**两条规则**（各有各的失败要消灭，详见 §11 批 1 第 1.7 项）：

```kotlin
val classified = bindings.filterNot { it.isPseudoFallback }   // ① 已接受分类优先
val pool = classified.ifEmpty { bindings }                    //    只有占位绑定时才回落
val taxonomyVersion = pool.last().taxonomyVersion             // ② 取最新一组，不是最早一组
val attributed = pool.filter { it.taxonomyVersion == taxonomyVersion }
```

配套把伪/真之别**抬到读取面**：`PracticeUnitKnowledgeBindingRecord` 新增**必填**（无默认值）
`isPseudoFallback`，由 `RoomStudyDatabase` 的映射从 `source_type` 推出
（`row.sourceType == PSEUDO_BINDING_SOURCE_TYPE`）。**刻意不给默认值**——有默认值就等于让每个
现有与将来的构造点默默宣称「这是一条已接受分类」，正是本记录模式 E 那条「缺省值冒充真实信号」。

> **为什么这不是 1.4 的重复**：1.4 管**写入**（不再为已分类的题**新写**伪绑定），本条管**读取**
> （已有的那行伪绑定不得遮蔽真绑定）。两件事都必要——1.4 按定义放行「先伪后真」的常规时序，
> 而那正是本条规则 ① 的主路径。见 §3 **S-9**。
> **规则 ② 顺带结清 §8.2 第三条**（`USER_CORRECTED` 重组织后旧 KC 继续吃证据）：那条本轮已一手
> 核实为真，且它是**与权威 spec 直接冲突**的一条——`three-store-linkage-design.md:93` 与
> `mastery-scheduling-spec.md:203`（§2.13「旧 KC 停止新证据」）都要求取**最新**组，代码取的是最早组。

#### 5.3.7　`duplicate-dead-visual-constants`

- **严重度**：`P3`　**类型**：`dead-mechanism`
- **标题**：RoomBackedStudyExperienceRepository 内三个视觉权重常量与知识复习会话常量均无消费者
- **位置**：`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/RoomBackedStudyExperienceRepository.kt:1034`

**失败路径**

> PRIMARY_VISUAL_ATTRIBUTION_WEIGHT(0.6)/SECONDARY_VISUAL_ATTRIBUTION_WEIGHT_POOL(0.4)/VISUAL_VIOLATED_WEIGHT(0.5) 在该文件 companion 内定义，但真正生效的是 VisualInteractionIngestor.kt:180-183 的同名常量副本；改前者不会有任何行为或测试变化，且两份副本值一旦漂移会误导读者。KNOWLEDGE_QUIZ_CONVERSATION_ID 同样定义了却没人用（调用方 ViewModel 自拼 id）。

**证据**

> 全仓 grep 三个常量名只在 RoomBackedStudyExperienceRepository.kt:1033-1035（定义）与 VisualInteractionIngestor.kt:80/87/110/181-183（各自的 companion 定义+使用）出现；KNOWLEDGE_QUIZ_CONVERSATION_ID 只在 :1027 定义，实际 id 由 KnowledgeReviewSessionViewModel.kt:193-194 `"knowledge-quiz-review:$sessionStartedAtEpochMillis"` 生成。

审计者自评信心：`verified`

#### 5.3.8　`two-prerequisite-of-constants`

- **严重度**：`P3`　**类型**：`structural`
- **标题**：两个同值 PREREQUISITE_OF 常量分属题-题与 KC-KC 两表，关系查询不按类型过滤
- **位置**：`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDbValue.kt:99`

**失败路径**

> StudyDbValue 里 KnowledgeRelationType.PREREQUISITE_OF(:6) 与 RelationType.PREREQUISITE_OF(:99) 字符串值完全相同但语义/表不同。KnowledgeNodeRelationDao.readForDependents 不写 `relation_type = ...` 过滤，前置图完全依赖写入端 KnowledgeNodeRelationContract 只放行 PREREQUISITE_OF；一旦该表将来加入其它关系类型（如 RELATED_TO），前置读取会静默把非前置关系当作前置。

**证据**

> StudyDbValue.kt:5-7 与 :95-101 两个 `const val PREREQUISITE_OF = "PREREQUISITE_OF"`；KnowledgeNodeRelationDao.kt:26-37 readForDependents 只按 subject + dependent_knowledge_node_id 过滤；KnowledgeNodeRelationContract.kt:29 是唯一保证（require relationType == KnowledgeRelationType.PREREQUISITE_OF）。

审计者自评信心：`verified`


### 5.4 模型边界与结构化输出嵌合

合计 **10** 条（P1 ×1 ／ P2 ×6 ／ P3 ×3）

#### 5.4.1　`image-classify-kind-never-supported`

- **严重度**：`P1`　**类型**：`dead-mechanism`
- **标题**：生产网关 supportedTasks 从不声明 IMAGE_PIPELINE_CLASSIFY，去手写重绘 spine 在生产上恒被能力门拒
- **位置**：`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/model/OpenAiCompatibleModelGateway.kt:485`

**失败路径**

> 用户开启全局模型同意并配置了支持图像输入的模型后保存错题，RoomCaptureWorkflowRepository.decideAndRedraw 发起 IMAGE_PIPELINE_CLASSIFY -> RoomModelTaskRepository.execute 先跑 capabilityFailure -> provider.supports(kind) 恒为 false（该 kind 从不在 supportedTasks 中）-> PERMANENT_FAILURE；terminal.status != SUCCEEDED，shouldRedraw 恒 false，scheduleCleanRedraw 永不触发——去手写重绘整条链在生产上永不运行。

**证据**

> OpenAiCompatibleModelGateway.kt:485-499 的 buildSet 只 add TUTOR_PLAN/TUTOR_RESPOND/TUTOR_LOBBY/PROBLEM_CLASSIFY/KNOWLEDGE_QUIZ，图像分支只 add CAPTURE_ASSESS/CAPTURE_PARSE/TUTOR_VISUAL_GENERATE/TUTOR_VISUAL_REVIEW，无 IMAGE_PIPELINE_CLASSIFY；RoomModelTaskRepository.kt:115 调 capabilityFailure，605-610 用 provider.supports(request.input.kind) 判 PROVIDER_CAPABILITY_MISSING；RoomCaptureWorkflowRepository.kt:418-428；docs/image-pipeline-decision.md:8-10 声称该 spine 已在生产运行。对照 FakeModelGateway.kt:165-175 声明支持 IMAGE_PIPELINE_CLASSIFY，所以 instrumented 测试全绿而生产恒拒。

审计者自评信心：`verified`

#### 5.4.2　`debrief-focus-labels-dead-and-kind-unsupported`

- **严重度**：`P2`　**类型**：`dead-mechanism`
- **标题**：TutorDebriefOutput.teachingFocusLabels 必填但无消费方；且 LEARNING_SUMMARIZE 在任何 gateway 的 supportedTasks 中都缺席
- **位置**：`core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/TutorTasks.kt:869`

**失败路径**

> 1) teachingFocusLabels 被 parser 解析并要求 1..8 个非空，消费端 SavedMistakeTutorRoute.kt:281-288 只读 misconceptionMarkdown，focus 标签实际取自 TUTOR_PLAN 输出；2) LEARNING_SUMMARIZE 不在 OpenAiCompatibleModelGateway.kt:485-499 也不在 FakeModelGateway.kt:165-175 的 supportedTasks 中，而 debrief 请求只在 LOCAL_NO_EGRESS provider 下构造（TutorModelTaskPolicy.kt:246）——即唯一允许发 debrief 的配置也会在 capabilityFailure 被永久拒，静默 debrief 通道整体不可达。

**证据**

> TutorTasks.kt:869,879-884；OpenAiModelResponseParsers.kt:590-604；SavedMistakeTutorRoute.kt:277-289；TutorModelTaskPolicy.kt:246；OpenAiCompatibleModelGateway.kt:485-499；FakeModelGateway.kt:165-175；RoomModelTaskRepository.kt:605。

审计者自评信心：`verified`

#### 5.4.3　`egress-bypass-image-channel`

- **严重度**：`P2`　**类型**：`security`
- **标题**：配图/去手写图片出网通道绕过 ModelEgressPolicy 与全局同意开关
- **位置**：`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/model/AttachedImageGeneratorFactory.kt:31`

**失败路径**

> 用户在设置里关闭「模型代理」全局同意后：聊天出网被 agentConsentMatches/ModelEgressPolicy 挡住，但打开一条已持久化、带 attachedImages 的讲题回复时，UI 仍调用该 resolver，resolveImageCredential 只验凭证存在 + capabilityVerification.supportsImageInput，随后 OpenAiImageGenerationChannel 把当前题面原图（REDRAW_PROBLEM 走 readTutorSessionSheetBytes）POST 到配置的 provider——图片字节在同意关闭后仍然出网，且不走 egress manifest / RestrictedModelAssetSource 的逐资产授权。

**证据**

> AttachedImageGeneratorFactory.kt:31-46（只 resolveImageCredential，无 consent）；ImageCredentialGate.kt:13-23（无 consent 检查）；TutorChatConversation.kt:358 attachedImageResolver 调用点不检查 agentConsentEnabled；对照 ModelEgress.kt:447-455 agentConsentMatches 与 OpenAiCompatibleModelGateway.kt:541-552 isReadyForNetwork 才要求 consent。DataStoreModelAgentConsentStore.kt:34-40 允许用户关闭。

审计者自评信心：`likely`

#### 5.4.4　`image-classify-text-formulas-dead`

- **严重度**：`P2`　**类型**：`dead-mechanism`
- **标题**：ImagePipelineClassifyOutput.textMarkdown/formulas 解析后无任何消费者
- **位置**：`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/model/OpenAiModelResponseParsers.kt:283`

**失败路径**

> prompt 要求模型对 TEXT_ONLY 转写 textMarkdown 与 formulas（OpenAiModelTaskAdapters.kt:125），parser 忠实解析进输出，但唯一消费点 RoomCaptureWorkflowRepository.kt:420 只比较 problemKind == WITH_FIGURE；textMarkdown/formulas 从不被读取，模型这段转写是纯浪费出网 token。

**证据**

> ModelTasks.kt:434-439 定义；OpenAiModelResponseParsers.kt:281-292 解析（且未 requireOnlyKeys）；RoomCaptureWorkflowRepository.kt:419-421；全库 src/main 无 .textMarkdown/.formulas 对该类型的读取。

审计者自评信心：`verified`

#### 5.4.5　`mastered-anchor-unverified`

- **严重度**：`P2`　**类型**：`correctness`
- **标题**：MASTERED 证据锚门只数模型 rationale 里的引号，不核对引文是否真出现在会话文本（committed 版）
- **位置**：`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/RoomTutorToolRunner.kt:344`

**失败路径**

> 模型 MASTERY_UPDATE 的 rationale 只要写出 ≥2 对长度≥4 的引号（如 学生说"因为""所以"），MasteryWriteGate 就认为 POSITIVE+MASTERED 具备可核查支持，以 0.18 权重接受并落库；被引用的文本从不与会话原文比对，编造引文即可通过高置信档。

**证据**

> committed RoomTutorToolRunner.kt:344 evidenceAnchorCount = MasteryWriteGate.evidenceAnchorCount(call.rationale)；MasteryWriteGate.kt:103-117 仅正则计数，已注明"不核对引文真伪"；MasteryWriteGate.kt:286-296 该值决定 MASTERED 是否被拒。注：工作树未提交改动新增 verifiedEvidenceAnchorCount 修此问题，本 finding 只针对已提交代码。

审计者自评信心：`verified`

#### 5.4.6　`problem-classify-dead-output-fields`

- **严重度**：`P2`　**类型**：`dead-mechanism`
- **标题**：PROBLEM_CLASSIFY 输出 summaryMarkdown/reviewPriorityMarkdown 与原子知识 observableOutcomeMarkdown/boundaryMarkdown 全库无消费方
- **位置**：`core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/ProblemOrganizationTasks.kt:329`

**失败路径**

> prompt 明确要求这些字段（OpenAiProblemOrganizationProtocol.kt:100,126-131）、parser 用 requiredString 解析且 init 校验非空，但写入/展示路径只读 classifications/relations/atomicKnowledge/reasoning 匹配/stepAttributions/difficultyTier/groundingRequests，从不读 summaryMarkdown/reviewPriorityMarkdown/observableOutcomeMarkdown/boundaryMarkdown——模型为它们产出的内容永不可达。

**证据**

> ProblemOrganizationTasks.kt:328-329,223-224；OpenAiProblemOrganizationProtocol.kt:191-192,247-248；RoomMistakeOrganizationRepository.kt:343-377（acceptOrganizationLocally/buildConfirmationCommand 只用其余字段）、755-765；跨全库 src/main grep reviewPriorityMarkdown 仅命中定义处与 parser。

审计者自评信心：`verified`

#### 5.4.7　`tutor-difficulty-reason-dead`

- **严重度**：`P2`　**类型**：`dead-mechanism`
- **标题**：TutorTurnPlan.difficultyReasonMarkdown 必填但 UI/逻辑无消费方
- **位置**：`core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/TutorTasks.kt:508`

**失败路径**

> prompt 把 difficultyReasonMarkdown 列为必填、parser 用 requiredString、init 校验非空；feature/tutor 渲染与策略层只读 openingMarkdown/solutionMarkdown/alternateMethodMarkdown/diagnosticItem/visualRequest 等，从不读 difficultyReasonMarkdown，模型多写一段从不展示。

**证据**

> TutorTasks.kt:508,522；OpenAiModelResponseParsers.kt:418,542；OpenAiModelTaskAdapters.kt:215；全库 grep difficultyReason 仅命中定义/prompt/parser/测试。

审计者自评信心：`verified`

#### 5.4.8　`attention-floor-unreachable`

- **严重度**：`P3`　**类型**：`dead-mechanism`
- **标题**：MasteryWriteGate 的注意度拒写门在生产不可达（attentionFactor 恒 1.0）
- **位置**：`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/RoomTutorToolRunner.kt:355`

**失败路径**

> RoomTutorToolRunner.Context.attentionFactor 默认 1.0，而 RoomModelTaskRepository.toolContext 构造 Context 时从不传该参数 -> GateInput.attentionFactor 恒 1.0 -> MasteryWriteGate.evaluate 的 attentionFactor < MIN_ATTENTION_FACTOR(0.4) 分支永不触发，ATTENTION_BELOW_FLOOR 拒因成为死代码。

**证据**

> committed RoomTutorToolRunner.kt:86（默认 1.0）、355（attentionFactor = context.attentionFactor）；RoomModelTaskRepository.kt:674-692 toolContext 未赋值；MasteryWriteGate.kt:307-309；docs/specs/2026-09-02-tool-loop-wiring-design.md:124 承认讲题 UI 未接切屏/离开采集。

审计者自评信心：`verified`

#### 5.4.9　`contract-registry-test-only`

- **严重度**：`P3`　**类型**：`dead-mechanism`
- **标题**：ModelTaskContractRegistry 无生产调用方，契约表与运行时 egress 不变量实为两套
- **位置**：`core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/ModelTaskContractRegistry.kt:45`

**失败路径**

> 全库 src/main 无任何 import 或调用 ModelTaskContractRegistry/ModelTaskContract；运行时的 egress 不变量完全由 ModelEgressManifest 的硬编码常量与 requireAuthorizes 决定。改 registry（如翻转某 kind 的 assetPolicy/requiredDisclosures）不影响任何运行时行为，也不会有测试失败——契约表可与实际收口静默漂移。

**证据**

> grep ModelTaskContractRegistry 仅命中本文件定义与 core/model/src/test/.../ModelTaskContractRegistryTest.kt；运行时权威在 ModelEgress.kt:253-365 与 538-695。ARCHITECTURE.md:22 自认其为 parity 表而非 dispatch 路径调用。

审计者自评信心：`verified`

#### 5.4.10　`tool-confidence-default-0.8`

- **严重度**：`P3`　**类型**：`correctness`
- **标题**：工具环 confidence 缺省 0.8，使「模型没给置信度」被当成 0.8（高于 0.7 证据门）
- **位置**：`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/model/OpenAiModelResponseParsers.kt:494`

**失败路径**

> 模型 tool call 省略 confidence 字段时 parser 静默填 0.8；MasteryWriteGate.EVIDENCE_CONFIDENCE_THRESHOLD = 0.7，于是缺省值直接通过证据置信门，把「模型未表态置信度」当成「高置信」放行写掌握度（direction/understanding 仍是必填）。

**证据**

> OpenAiModelResponseParsers.kt:494 confidence = call.optionalDouble("confidence") ?: 0.8；Native 工具路径 OpenAiModelProtocol.kt:466 同样 ?: 0.8；TutorToolLoop.kt:83 构造默认 0.8；MasteryWriteGate.kt:30,280-282。

审计者自评信心：`verified`

**2026-09-12 已修（批 1 第 1.5 项，worktree `audit/kernel-readiness`，未提交）。**

三处缺口用**一个具名常量**收口：`core:model` 新增
`const val MISSING_TOOL_CONFIDENCE = 0.0`（放在 `TUTOR_TOOL_ROUTE_CONFIDENCE_THRESHOLD` 旁边），
三处全部改指它——

| 站点 | 原值 | 现 |
|---|---|---|
| `TutorToolLoop.kt:83`（data class 缺省） | `0.8` | `MISSING_TOOL_CONFIDENCE` |
| `OpenAiModelProtocol.kt:466`（原生 tool_call arguments） | `?: 0.8` | `?: MISSING_TOOL_CONFIDENCE` |
| `OpenAiModelResponseParsers.kt:494`（结构化 `toolRequests`） | `?: 0.8` | `?: MISSING_TOOL_CONFIDENCE` |

于是缺省值**永远过不了证据门**（`MasteryWriteGate.kt:246` 的
`evidenceConfidence < EVIDENCE_CONFIDENCE_THRESHOLD` → `RejectReason.EVIDENCE_BELOW_CONFIDENCE`），
而不是像 `0.8` 那样刚好越过 `0.7`。常量刻意用 `0.0` 而不是「0.7 以下的某个数」：
它表示**没有信号**，不是一个弱信号。

> **为什么常量不放在 `MasteryWriteGate` 旁边**：`core:model` **不能**依赖 `core:domain`
> （依赖方向），而三处缺口里有两处在 `core:data`、一处在 `core:model`；放进 `core:model`
> 是唯一能让三处共用一个值的位置。KDoc 里点名门的位置，但不成链接。

**三条测试**（都在 JVM 侧，不需要设备）：

| 测试 | 位置 | 钉住的性质 |
|---|---|---|
| `aMasteryUpdateThatStatesNoConfidenceIsRejectedByTheEvidenceGate` | `RoomTutorToolRunnerTest` | **端到端后果**：不传 confidence 的 MASTERY_UPDATE 被门拒（`rejected:EVIDENCE_BELOW_CONFIDENCE`），且仍落一条 weight=0、带拒因的观察行（被拒 ≠ 删除） |
| `nativeToolCallWithoutConfidenceParsesBelowTheEvidenceGate` | `OpenAiNativeToolsProtocolTest` | 原生 tool_call arguments **漏发** confidence → 解析结果低于门 |
| `masteryUpdateWithoutConfidenceParsesBelowTheEvidenceGate` | `TutorToolRequestDualParseTest` | 结构化 `toolRequests` 漏发 confidence → 解析结果低于门 |

三条都把 `MISSING_TOOL_CONFIDENCE < MasteryWriteGate.EVIDENCE_CONFIDENCE_THRESHOLD` 当作断言的一部分，
因此**以后有人把常量调高到门以上，测试会先红**——常量与门的关系被钉住，不再靠注释提醒。



合计 **8** 条（P1 ×2 ／ P2 ×4 ／ P3 ×2）

#### 5.5.1　`core-ui-tests-never-run`

- **严重度**：`P1`　**类型**：`missing-gate`
- **标题**：core:ui 的 11 个单测（含 markdown 净化安全断言）不在任何 CI 单测任务里，永不执行
- **位置**：`.github/workflows/android-check.yml:48`

**失败路径**

> CI 单测步骤（第 43-55 行）显式列出 15 个模块任务，唯独缺 :core:ui。core/ui 是 Android library，其测试任务 testDebugUnitTest 从未被调用，schema-export.yml 也不跑它。后果：core/ui/src/test 下 AiReplyRichMarkdownTest.kt 的 shouldUseRichTextMarkdown 安全断言（拒绝 javascript: 链接、远程图片）与 AttachedImageCardTest/ThinkingCollapsibleCardTest 共 11 个 @Test 在 CI 里既不编译也不运行。把 shouldUseRichTextMarkdown 改成允许 javascript:alert(1)，CI 不会变红。注意工作流自己的注释（第 44-46 行）明写「every other module's unit tests are listed explicitly or they never run in CI」，本条正是违例。

**证据**

> .github/workflows/android-check.yml:43-55（无 :core:ui）；core/ui/src/test/java/com/tingyun/smartmistakebook/core/ui/{AiReplyRichMarkdownTest,AttachedImageCardTest,ThinkingCollapsibleCardTest}.kt（3 个文件、11 个 @Test）；core/ui/build.gradle.kts 为 android.library，单测任务名为 testDebugUnitTest。

> **S-7 订正（见 §12.3）**：原文此处曾写「:core:ui 只出现在 lint 列表（第 88 行 :core:ui:lintDebug）」——那一行来自**并发会话的工作树**，不是已提交状态。
> 按 HEAD 重新核对：`:core:ui` 在整个 `android-check.yml`、`schema-export.yml`、`status-template.md` 里**一次都不出现**，
> 已提交的 lint 步骤只有 `./gradlew lintLocalFirstDebug lintStrictOfflineDebug`（第 79 行），无任何模块级 lint。
> 即已提交状态下 `core:ui` **既不在单测清单也不在 lint 清单**——本条比原记录更严重，且并发树的 lint 补丁并未触及单测那一半。

审计者自评信心：`verified`

#### 5.5.2　`perf-gate-empty-seed`

- **严重度**：`P1`　**类型**：`missing-gate`
- **标题**：性能门禁的 insertTestData 是空实现，六个性能测试全部在 0 行库上测量，500/200/1000ms 预算恒真
- **位置**：`core/database/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/database/PerformanceGateTest.kt:247`

**失败路径**

> PerformanceGateTest.insertTestData(10_000) 被 6 个 @Test 调用（第 57、89、117、157、201、226 行），但函数体只有注释、不写任何行（第 247-250 行 `// For now, this is a placeholder`）。所有查询都在空库上执行：searchPerformance10kItems/chineseSearchPerformance/concurrentSearchPerformance 的 P95 断言（SEARCH_P95_TARGET_MS=500ms、CONCURRENT=1000ms，第 284/293 行）与 facetQueryPerformance（200ms，第 290 行）几乎必然为 0ms 而通过；firstScreenPerformance 同理。把 insertTestData 改成真正写入 10k 行，或把生产查询改坏成 O(n²)，这两者都不会让这些测试变红。release-gates.md:28 宣称有「1k/10k/50k library performance gates」，实际一个都没有生效。

**证据**

> PerformanceGateTest.kt:247-250 `private suspend fun insertTestData(count: Int) { // This would insert test data into the library_catalog view // For now, this is a placeholder }`；调用点 57/89/117/157/201/226；目标常量 284/287/290/293。docs/current/release-gates.md:28。真实 50k 播种只在功能测试 LibraryCatalogPagingInstrumentedTest.kt:28-125，那里没有任何计时断言。

审计者自评信心：`verified`

> **已处理（2026-09-13 批 4）：按审计给的两条路**一起**走——真实播种 ＋ 预算标 `NOT_MEASURED`。**
> `insertTestData` 现在真的写 10k 行（走 `seedFixture`，与 `LibraryCatalogPagingInstrumentedTest`
> 同一个播种口）并调 `refreshLibrarySearchProjection()` 建 FTS 索引；三条计时用例各加
> **"这条查询命中过行"的前置断言**（缺了它，夹具与查询一旦脱节，计时断言会在**零行命中**上
> 稳定通过——这正是本条的错法换个地方复发）。
>
> **恢复测量后立刻红了三条，而红的原因分两层，必须分开记**：
> ① 第一层是**我的夹具/查询写错了**：`countSearch` 的入参是**已构造好的 MATCH 表达式**，
> 生产经 `CjkTextTokenizer.matchExpression`，用例直接传了用户输入 ⇒ 中文查询被当成单个词，
> 一行都命中不到（前置断言把它抓住，没有让它变成"新的空跑"）；
> ② 第二层才是**真的计量结果**：FTS 三条超预算 3.2–20.9 倍，而目录侧两条都在预算内——
> 详见 **§12.5 N-17**（含两次运行的数、这个不对称为什么指向 FTS 路径、以及要决定的两条路）。
>
> **不再断言的只有那三个数**；命中前置与 60 秒挂死上界保留。**没有挑一个能让它变绿的预算**——
> 那正是本条要防的错法。

#### 5.5.3　`backup-skeleton-vacuous-tests`

- **严重度**：`P2`　**类型**：`missing-gate`
- **标题**：BackupRestore 的三个 process-death「Skeleton」@Test 默认分支为空体，恒真且无替代断言
- **位置**：`core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/backup/BackupRestoreInstrumentedTest.kt:604`

**失败路径**

> processDeathBetweenDatabaseAndAssetSwapSkeleton（582-594）、processDeathAfterSwapBeforeVerifySkeleton（604-610）、firstLaunchFailureAfterCommittedSwapSkeleton（620-626）都把注入开关硬编码为 false（killForReal/simulateOpenFailure=false），默认路径下函数体什么都不做、不断言，永远绿色。第一条有真实替代 startupRecoveryRollsBackGenerationAfterInterruptedSwap（465）；第二条（swap 后、VERIFYING 前死亡）与第三条（提交后首次启动失败）在本文件内没有等价自动断言，只有注释描述——这两条恢复分支无覆盖。它们还会作为 passed 计入 status.md 的 core:data 用例数。

**证据**

> BackupRestoreInstrumentedTest.kt（HEAD dc12065）:582-594、604-610、620-626；替代覆盖 465/529；文件内 @Test 列表 51-626。

审计者自评信心：`likely`

> **已处理（2026-09-13 批 4）：三个空体 Skeleton 换成三条有断言的用例**，覆盖审计点名的
> 那两条无等价断言的恢复分支：
>
> | 审计说"无覆盖"的现场 | 新用例 | 断言 |
> |---|---|---|
> | 两次原子切换之间死亡（数据库已切、资产未切） | `startupRecoveryRollsBackDatabaseHalfSwapWithoutTouchingAssets` | 数据库回滚到上一代；**没切的资产那一半不被误删**；`.prev`/`.next`/暂存/日志全部清掉 |
> | 资产切换之后、VERIFYING 之前死亡 | `startupRecoveryRollsBackBothGenerationsAfterDeathBetweenAssetSwapAndVerify` | 数据库**与资产一起**回到上一代（只回滚数据库的实现会在这条红） |
> | 提交后首次启动失败 | `committedRestoreLeavesNoJournalSoTheNextStartupDoesNotRollBack` | 跑**真实**恢复，然后：盘上无日志、`recoverOnStartup` 返回 `NothingToRecover`、恢复进来的那一代还在 |
>
> 三点必须写清楚，否则这条会被读成"换了个名字"：
>
> 1. **真进程死亡在本测试宿主里无法自动化**——instrumentation 跑在要被杀的进程里，
>    `killProcess(myPid())` 会把 runner 一起杀掉，断言永远执行不到。原来那三个 Skeleton
>    的 `killForReal` 分支即使置 true 也不是可用的测试（进程死了就没有"下一次启动"的断言者）。
>    所以换成**仿真等价物**：摆出崩溃现场，跑真实的 `BackupRestoreStartupRecovery`。
>    仿真覆盖不到的唯一一环是"物理掉电后 fsync 是否真落盘"，那需要真机断电实验。
> 2. **现场用生产的日志写入器 `RestoreJournal` 留**，不再手写 JSON。手写 JSON 只能证明
>    "读取端认得这个格式"，证明不了"写入端写的就是这个格式"；两边一漂移，手写的照样绿。
> 3. **两代夹具内容刻意可分辨**（题面与资产文件名各不相同）——两代一样时，把回滚整个
>    跳过也能通过。
>
> **三次变异，预测的变红集合与实际逐次相等**：只删 `rollbackDatabase` 调用 → 红
> `{两次切换之间}`；只删 `rollbackAssets` 调用 → 红 `{资产切换之后}`（外加既有的
> `startupRecoveryRollsBackGenerationAfterInterruptedSwap`）；只删成功路径的
> `journal.clear()` → 红 `{提交后不回滚}`。用例数 16→16，`0 skipped 0 failed`。
> 那条"提交后不回滚"此前**完全没有覆盖**：日志漏清会让下一次启动把刚恢复进来的数据
> 当成"切换了一半"再回滚掉，用户看到的是"恢复成功、重启后数据回到恢复前"。

审计者自评信心：`likely`　**处理者自评信心：`verified`（JUnit XML ＋ 三次变异）**

#### 5.5.4　`migration-matrix-empty-assertion`

- **严重度**：`P2`　**类型**：`missing-gate`
- **标题**：迁移矩阵测试不播种数据，仅断言空库计数为 0，破坏性回退无法被检出
- **位置**：`core/database/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/database/FullMigrationMatrixInstrumentedTest.kt:23`

**失败路径**

> everyExportedSchemaVersionMigratesToCurrentWithoutDestructiveFallback 对 1..STUDY_DATABASE_VERSION 只做「按导出 schema 建空库 → 打开 → 断言 libraryCatalogCount(...)==0」（第 23 行）。空库天然返回 0，该断言不能区分无损迁移与 destructive fallback：在 StudyDatabaseFactory.open 上加 .fallbackToDestructiveMigration() 或在某条迁移里 DROP 掉所有数据，本测试仍然全绿——而测试名恰恰声称 WithoutDestructiveFallback。它实际只证明「每个版本的 schema 能建库且 open 不抛异常」。

**证据**

> FullMigrationMatrixInstrumentedTest.kt:16-24；createDatabaseFromExportedSchema 只建表/索引/视图、不插行（ExportedSchemaTestDatabase.kt:7-48）。数据保留另有逐版本测试补偿（LibrarySearchMigrationInstrumentedTest.kt:52 断言迁移后有 1 行、MasterySchedulingMigrationInstrumentedTest.kt:212 播种）。

审计者自评信心：`verified`

#### 5.5.5　`perf-telemetry-readers-dead`

- **严重度**：`P2`　**类型**：`dead-mechanism`
- **标题**：性能/内测指标读取器全无消费者且 benchmark 测试从不执行，宏基准与 Beta 门禁结构上不可能失败
- **位置**：`benchmark/src/androidTest/kotlin/com/tingyun/smartmistakebook/benchmark/PerformanceTargets.kt:78`

**失败路径**

> PerformanceMeasurementReader（PerformanceTargets.kt:78）与 TelemetryReader（BetaGateTargets.kt:55）全仓无任何调用点（grep 排除定义文件与 .worktrees 后零命中），即 PerformanceMeasurement.Measured / TelemetryState.Measured 永远不会被构造，也没有任何断言读它们。真实测量本应由 benchmark 模块产生，但 CI 对 benchmark 只做 `./gradlew :benchmark:assemble`（android-check.yml:75，仅编译），没有任何 connected 任务；benchmark 的 StartupBenchmark 有 6 个 @Test（StartupBenchmark.kt:30/44/59/78/97）。因此 status.md 的宏基准/内存行（66-69、75）恒为 NOT_MEASURED，release-gates.md:29 的「Macrobenchmark and Baseline Profile」与整个 BetaGateTargets 只能靠人肉，门禁无法因回归而变红。

**证据**

> PerformanceTargets.kt:64/78（sealed PerformanceMeasurement / reader）、BetaGateTargets.kt:43/55（TelemetryState / reader）；grep `PerformanceMeasurementReader|TelemetryReader|BetaGateTargets|TelemetryState` 除定义与 StartupBenchmark.kt:21 注释外无命中；android-check.yml:75；docs/status.md:66-69,75。

审计者自评信心：`verified`

#### 5.5.6　`status-migration-security-never-measured`

- **严重度**：`P2`　**类型**：`spec-divergence`
- **标题**：release-gates 声称「迁移矩阵通过 / 备份往返通过」，但 status.md 的迁移与安全检查恒为 NOT_MEASURED
- **位置**：`.github/workflows/status-template.md:89`

**失败路径**

> status 模板的 Migration Tests 表只有 {{MIGRATION_TEST_ROWS}}（第 89 行），Security Checks 的 {{SECURITY_DEBUG_SIGNING}}/{{SECURITY_HARDCODED_SECRETS}}/{{SECURITY_DEPENDENCY_VULNS}}（第 95-97 行）在 generate_status.py 的 values 字典里没有任何赋值来源，最终被通用空占位替换成 NOT_MEASURED（status.md:89、95-96）。于是 docs/current/release-gates.md:16-17 的「full Room migration matrix passes」「backup create/validate/restore round trip passes」在机器可验证状态报告里没有任何对应证据行，无法从 status.md 判断这些门是否通过。

**证据**

> status-template.md:85-97；generate_status.py values 赋值段无 MIGRATION_TEST_ROWS/SECURITY_* 键；docs/status.md:89 `NOT_MEASURED`、:95-96；docs/current/release-gates.md:16-17。

审计者自评信心：`verified`

#### 5.5.7　`pr-paths-skip-gate-scripts`

- **严重度**：`P3`　**类型**：`missing-gate`
- **标题**：PR 路径过滤不覆盖 tools/**、.github/**，改动门禁脚本或工作流本身不会触发 CI
- **位置**：`.github/workflows/android-check.yml:5`

**失败路径**

> on.pull_request.paths（第 5-14 行）只含 **.kt、**.kts、app/**、core/**、feature/**、gradle/**、build.gradle.kts、settings.gradle.kts、gradle.properties。tools/ci/*.py（check_file_size_gate.py、check_release_manifest.py、generate_status.py）与 .github/workflows/** 都不匹配。一个只改这些文件（例如改坏文件体量门或状态生成器）的 PR 不会触发 android-check；push 分支没有 paths 过滤（第 15-16 行），所以问题只会在合并进 main 后由 push 运行才暴露。

**证据**

> android-check.yml:3-16（pull_request.paths 列表、push.branches 无 paths）。

审计者自评信心：`verified`

#### 5.5.8　`status-report-module-undercoverage`

- **严重度**：`P3`　**类型**：`structural`
- **标题**：status.md 单测表只报 5 个模块，其余 10+ 个在 CI 运行的模块结果全部落空
- **位置**：`.github/workflows/status-template.md:31`

**失败路径**

> 状态模板的 Unit Tests 表硬编码只有 :core:database、:core:data、:feature:library、:feature:capture、:feature:tutor 五行（第 31-35 行）。CI 实际运行的 :core:domain、:core:model、:core:visual-runtime、:core:export、:core:visual-ui、:feature:profile、:feature:review、:quality:visual-benchmark、:knowledge-production 以及 app 两个 flavor 的单测结果都不会进入 status.md（generate_status.py 的 MODULES 也只有 6 项）。所以「机器可验证状态报告」并不覆盖大部分测试断言，回归只能靠人读 CI 日志。

**证据**

> status-template.md:31-35；docs/status.md 单测表仅 5 行；android-check.yml:48-55 实际列了 15 个单测任务；generate_status.py MODULES 6 项。

审计者自评信心：`verified`


### 5.6 架构边界、依赖方向与体量

合计 **8** 条（P1 ×1 ／ P2 ×4 ／ P3 ×3）

#### 5.6.1　`tutor-adaptive-decision-dead`

- **严重度**：`P1`　**类型**：`dead-mechanism`
- **标题**：生产代码从不构造非空 AdaptiveDecision，自适应讲题决策整条通道结构性空转
- **位置**：`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/StudySnapshotBuilder.kt:114`

**失败路径**

> 学生带一道已验证题进入讲题、且题里基础步骤已稳定掌握时，spec §13/§18.2 要求本地给出 SKIP_MASTERED_FOUNDATION 等 AdaptiveDecision 并渲染 TutorScreen 的 adaptationMessage；实际 StudyExperienceSnapshot.tutorDecision 在两处生产赋值处都写死 null，SmartMistakeBookRoot 的 takeIf 只是再包一层 null，TutorRoute.kt:148 因此永远只落到 `adaptiveDecision == null -> TutorAdaptivePauseScreen("先确认要讲的当前题")` 分支，`else -> adaptivePauseMessage(decision.kind)`（TutorRoute.kt:224）和 `adaptationMessage(adaptiveDecision, profile)`（TutorRoute.kt:185）都不可达。后果：§18.2「两题族独立正确后跳过基础」与 §13.10 的自适应行为在生产永不发生。

**证据**

> StudySnapshotBuilder.kt:114 `tutorDecision = null`；RoomBackedStudyExperienceRepository.kt:1019 `tutorDecision = null`；唯一构造 AdaptiveDecision 的类 AdaptiveQuestionSelector.kt:125 在全仓 git grep 只被 AdaptiveQuestionSelectorTest.kt:15 实例化（`private val selector = AdaptiveQuestionSelector()`）；消费点 SmartMistakeBookRoot.kt:420 与 498；TutorRoute.kt:104/142/148/185/224。androidTest 的 RootTutorFailClosedInstrumentedTest.kt:542 有私有 askDecision() 才构造过非空值——即只有仪器化测试能进入该分支。

审计者自评信心：`verified`

#### 5.6.2　`calibration-report-methods-no-caller`

- **严重度**：`P2`　**类型**：`dead-mechanism`
- **标题**：四个调度/证据校准接口方法无任何生产调用点，校准计算从不执行
- **位置**：`core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/StudyExperienceRepository.kt:494`

**失败路径**

> evaluateSchedulingModels()、sourceCalibrations()、plannedReasonCalibrations()、chatEvidenceGateCalibration() 在 core:data 有完整实现并各有测试，但 app/feature/core 全仓没有任何生产调用点，也没有界面展示结果。于是它们下游的计算——SchedulingEvaluationHarness.evaluate/calibratePlannedReasons、ChatEvidenceGateCalibration.calibrate、ReviewLogSink.sourceCalibrations——在生产运行时从不被触发，这些「已接好线」的分析通道是空转的。

**证据**

> 接口定义 StudyExperienceRepository.kt:494/501/504/512；实现 RoomBackedStudyExperienceRepository.kt:814/817/820/823（各自委托 StudySchedulingCalibration）；但 `.evaluateSchedulingModels(`/`.sourceCalibrations(`/`.plannedReasonCalibrations(`/`.chatEvidenceGateCalibration(` 在 app/feature 下 0 命中，唯一非实现命中是 androidTest 的空实现桩 RootTutorFailClosedInstrumentedTest.kt:442；对比 calibrationReport/optimizeSchedulingParameters/recommendedDesiredRetention/suggestedReminderMinute 均有 app 调用点，证明这不是接口整体未接。同类先例见 docs/known-defects.md「setOptions / declareExam / removeExam had no caller」。

审计者自评信心：`verified`

#### 5.6.3　`domain-exposes-androidx-paging`

- **严重度**：`P2`　**类型**：`spec-divergence`
- **标题**：core:domain 不再是 AndroidX-free：领域接口暴露 androidx.paging.PagingSource
- **位置**：`core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/LibraryCatalogRepository.kt:3`

**失败路径**

> 基线/依赖声明把 core:domain 当作纯 JVM 领域层，但它的公共领域接口直接 import androidx.paging.PagingSource。任何实现或消费该接口的模块都被迫依赖 AndroidX Paging 抽象，领域层与 UI 框架耦合；虽然 paging-common 是 KMP 构件、不会导致编译失败，但边界声明与实际不符（审计要求该 grep 为零）。

**证据**

> LibraryCatalogRepository.kt:3 `import androidx.paging.PagingSource`；core/domain/build.gradle.kts `implementation(libs.androidx.paging.common)`；对照 core/model 与 core/visual-runtime 的 `^import android|^import androidx` grep 为 0，说明只有 domain 这一处越界。

审计者自评信心：`verified`

#### 5.6.4　`hlr-shadow-manager-orphan`

- **严重度**：`P2`　**类型**：`dead-mechanism`
- **标题**：HLRShadowModeManager 全类无任何实例化，shadow 预测机制空转
- **位置**：`core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/HLRShadowMode.kt:13`

**失败路径**

> 该类声明的 predictShadow()/evaluatePrediction() 承载「影子预测 + 与真实结果比对校准」这一整条机制，但没有任何生产或测试代码 new 过它，因此永不产生影子预测，HLREvaluationResult 也永不生成。生产实际走的是另一套 HLRPredictionAuditService。

**证据**

> git grep HLRShadowModeManager 全仓仅两条：HLRShadowMode.kt:13 定义、HLRPredictionAuditService.kt:29 的 KDoc 链接 `/** ... shared with [HLRShadowModeManager] ... */`（仅注释，非调用）；predictShadow / evaluatePrediction / HLREvaluationResult 在 core/domain/HLRShadowMode.kt 内各自只有定义处。

审计者自评信心：`verified`

#### 5.6.5　`review-planner-v1-branch-dead`

- **严重度**：`P2`　**类型**：`dead-mechanism`
- **标题**：ReviewPlanner V1 的「回滚」分支不可达，rollback 开关是单向门
- **位置**：`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/StudyReviewPlannerService.kt:325`

**失败路径**

> 注释（audit §3.4）把这行称作 V1 greedy planner 的回滚路径，但 useReviewPlannerV2 默认常量 true，三个生产工厂都不传该参数，也没有任何测试传 false——因此 `reviewPlanner.plan(request)` 在生产和测试中都不会执行；一旦 V2 出问题，改这个开关回到 V1 的计划器分支没有任何可达入口（V1 类本身仍被 KnowledgeReviewQueue 的 scoreKnowledgeNode 使用，所以不是整类死，只有 plan 这条分支死）。

**证据**

> StudyReviewPlannerService.kt:322-326 `if (useReviewPlannerV2) reviewPlannerV2.plan(request) else reviewPlanner.plan(request)`；RoomBackedStudyExperienceRepository.kt:1031 `private const val DEFAULT_USE_REVIEW_PLANNER_V2 = true` 且所有 create() 均未覆盖（StudyExperienceRepositoryFactory.kt:15/24/36）；`useReviewPlannerV2` 全仓引用只有 Repository:100/244、Service:53/322，无一处传 false。

审计者自评信心：`verified`

#### 5.6.6　`knowledge-retrieval-benchmark-orphan`

- **严重度**：`P3`　**类型**：`dead-mechanism`
- **标题**：KnowledgeRetrievalBenchmark 无生产消费者，仅被单测引用
- **位置**：`core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/KnowledgeRetrievalBenchmark.kt:15`

**失败路径**

> 270 行的检索质量度量类（Recall@K/MRR/nDCG）放在 main 源集，却没有生产调用方；只有 core/data 的一个单测构造它，等于一段永不参与生产路径的度量机制。

**证据**

> git grep KnowledgeRetrievalBenchmark 全仓仅两条：core/domain/.../KnowledgeRetrievalBenchmark.kt:15 定义与 core/data/src/test/kotlin/.../KnowledgeRetrievalBenchmarkTest.kt（测试）；没有 quality 模块或生产类引用它。

审计者自评信心：`verified`

#### 5.6.7　`mastery-threshold-two-authorities`

- **严重度**：`P3`　**类型**：`structural`
- **标题**：掌握度 0.7/0.4 分界在 app UI 与 core:ui 各有一份常量，无机械联系
- **位置**：`app/src/main/kotlin/com/tingyun/smartmistakebook/LearningMasteryScreen.kt:398`

**失败路径**

> 同一个 conservativeMasteryScore 的档位判定被写了两遍：core/ui/MasteryBands.kt 的 masteryBandLabel 与 app 主源集的 forgettingRiskLabel 各自硬编码 0.7/0.4。改其中一处的阈值不会让另一处编译失败或测试变红，两个界面会对同一分数给出不同档位（例如把 MasteryBands 上调到 0.75 后，掌握页仍按 0.7 判档）。项目自己在 KnowledgeReadiness.kt:19-22 明确把「同值两常量、无机械联系」列为漂移温床，此处正属该模式。

**证据**

> core/ui/src/main/java/.../MasteryBands.kt:8-10（>=0.7→"较稳"，>=0.4→"一般"）；app/.../LearningMasteryScreen.kt:397-400（>=0.7→"低"，>=0.4→"中"，else "高"）；两处均无共享常量。此外 LearningMasteryScreen.kt:382 另用 evidenceMass>=1.0/正确答案>=2 做「较强」标签，与领域 ClearMasteredForSkipPolicy.EVIDENCE_MASS=2.0 又是两套阈值。

审计者自评信心：`verified`

#### 5.6.8　`megafile-backlog-grandfathered`

- **严重度**：`P3`　**类型**：`structural`
- **标题**：体量门只拦新增文件，11 个存量 >1000 行生产文件永不被收敛，其中 SecondaryScreens.kt 三屏杂处
- **位置**：`.github/workflows/android-check.yml:101`

**失败路径**

> CI 的 File-size gate 明确只检查本分支相对 origin/main 改动的文件（main 1000 行 / test 1500 行硬线），因此存量巨型文件不会在任何一次运行中变红，堆积只增不减。其中最典型的是 app/src/main/.../SecondaryScreens.kt：一个文件同时承载 CapabilityScreen（模型能力/配置，107-538 行）、DataPrivacyScreen（597）、StorageScreen（626）三个互不相关的二级界面。其余 >1000 行文件多为内聚型（记录 DTO / 事务 DAO / 投影算法 / 校验契约），此处不作为缺陷。

**证据**

> .github/workflows/android-check.yml:100-104 注释「only files changed on this branch are checked, so the existing large-file backlog … does not fail every run」+ tools/ci/check_file_size_gate.py；find+wc 得到 11 个 >1000 行生产文件：StudyDatabaseRecords.kt 1396、RoomStudyDatabase.kt 1301、LearningProjector.kt 1227、ProblemDraftTransactionDao.kt 1213、ProjectionTransactionDao.kt 1194、RoomMistakeOrganizationRepository.kt 1134、LearningEntities.kt 1119、TutorVisualDocument.kt 1076、MistakeDetailRoute.kt 1067、RoomBackedStudyExperienceRepository.kt 1040、DatabaseContract.kt 1027；SecondaryScreens.kt:107/597/626 三个独立 @Composable 界面。

审计者自评信心：`verified`


### 5.7 持久化、迁移与并发状态

维度声明：persistence-concurrency

合计 **7** 条（P1 ×1 ／ P2 ×3 ／ P3 ×3）

> **本维无对抗复核结论**：该维度的复核 agent 两次均因 API 流错误中断（基础设施故障）。承重项由主循环回读一手源码补验，见第 3 节与第 12 节。

#### 5.7.1　`chat-evidence-duplicate-outbox-pk`

- **严重度**：`P1`　**类型**：`correctness`
- **标题**：重复 chat evidence 写入在 outbox 主键冲突抛异常，而非文档承诺的幂等 no-op
- **位置**：`D:/smart mistake book/core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/dao/ChatEvidenceDao.kt:83`

**失败路径**

> 同一 evidence_id 第二次写入时，第69行先为它 allocateSequence（新序列），第82行 insertAll(accepted) 用 IGNORE 跳过重复证据行，但第72行 outboxId 由 evidence_id 确定性派生、第83行 insertOutboxRows 是 OnConflictStrategy.ABORT → 主键/唯一索引冲突抛 SQLiteConstraintException，整个事务回滚。可达输入：模型任务用同一 requestId 重试或同一 task 内多轮工具调用对同一 KC 再发 MASTERY_UPDATE（evidenceIdNamespace=requestId，见 RoomModelTaskRepository.kt:690-691 注释明确要求“重试不重复落库”），或 KnowledgeQuizFeedbackWriter.kt:64 用确定性 id "knowledge-quiz:$requestId:$knowledgeNodeId" 重复提交同一题。异常被 RoomTutorToolRunner.run 的 catch（RoomTutorToolRunner.kt:358 附近）吞成通用 failed 工具结果，模型看到写失败而不是文档承诺的静默 no-op；现有 ChatEvidenceLedgerIntegrationTest 全部用不同 evidence_id，无重复写入用例。

**证据**

> 第35行 `@Insert(onConflict = OnConflictStrategy.ABORT) protected abstract suspend fun insertOutboxRows(...)`；第72行 `outboxId = "learning-outbox:${entry.learner_id}:$EVENT_KIND_CHAT_EVIDENCE:${entry.evidence_id}"`；第83行 `insertOutboxRows(outboxRows)`；第26-28行注释「the evidence_id is a deterministic idempotency key — a retried write ... must silently no-op instead of failing the whole transaction on a PK clash」；RoomModelTaskRepository.kt:690-691 注释「同一 model-task request 的重试/多轮共享同一 evidenceId 命名空间，让 MASTERY_UPDATE 的 evidence_id 确定性派生（重试不重复落库）」。projection_outbox 另有 event_kind_event_id 唯一索引同样会冲突。

审计者自评信心：`verified`

#### 5.7.2　`catalog-retrievability-always-null`

- **严重度**：`P2`　**类型**：`dead-mechanism`
- **标题**：library_catalog.retrievability 恒为 NULL，LEAST_MASTERED 排序静默退化为按更新时间
- **位置**：`D:/smart mistake book/core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/entity/LibraryCatalogView.kt:20`

**失败路径**

> 视图把 retrievability 硬编码为 NULL（44.json 的 library_catalog createSql、以及迁移 35→36 的 LIBRARY_CATALOG_VIEW_SQL_V36 同样是 `NULL AS retrievability`）。LibraryQueryDao.kt:50 与 :101 的 `CASE :sort WHEN 'LEAST_MASTERED' THEN catalog.retrievability END ASC`、RoomLibrarySearchStore.kt:166 的 `"LEAST_MASTERED" -> "catalog.retrievability ASC,\n    "` 都排在一个常量上 → “最不熟练”排序实际等价于默认的 updated_at DESC，静默给出错误顺序而非报错；LibraryCatalogItem.retrievability 也永远为 null。触发输入：LibraryQuery(sort=LEAST_MASTERED)；当前 UI 未暴露该枚举（LibraryViewModel 用默认值），故属当前不可达但机制已死的隐患。

**证据**

> LibraryCatalogView.kt:20 `NULL AS retrievability`；LibraryQueryDao.kt:50 与 :101；RoomLibrarySearchStore.kt:166；LibraryCatalogRepository.kt:9 定义 LEAST_MASTERED，全库仅此一处出现（grep 无任何调用点设置 sort）。

审计者自评信心：`verified`

#### 5.7.3　`restore-failure-closes-db-no-reopen`

- **严重度**：`P2`　**类型**：`correctness`
- **标题**：恢复失败（swap 之后）关闭进程级数据库且不重开，失败提示也不要求重启
- **位置**：`D:/smart mistake book/core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/backup/AndroidBackupRepository.kt:242`

**失败路径**

> doRestore 在第242行 database.close()（传入的就是 Application 持有的同一个 RoomStudyDatabase，见 SmartMistakeBookApplication.kt:268 BackupRepositoryFactory.create(this, database)）之后才做 rename/verifyRestoredDatabase；若第589行 `PRAGMA integrity_check` 或其他后置步骤抛错，catch 分支(320-345行)回滚文件并 rethrow，但数据库对象保持已关闭。SecondaryScreens.kt:731 的失败提示只有「恢复失败，已尝试保留原数据」，没有成功路径(724行)那句「请完全退出并重新打开应用」——用户被告知原数据保住了、继续操作，但后续任何 DAO 调用都会因连接池已关闭抛 IllegalStateException，持久层在进程重启前完全不可用。全库没有任何路径重新 StudyDatabaseFactory.open（唯一会新建库的 StudyExperienceRepositoryFactory.create(context) 重载无调用点）。

**证据**

> AndroidBackupRepository.kt:242 `database.close()`；catch 分支 320-345 只做 rollback/journal.clear 后 `throw failure`，不重开数据库；SecondaryScreens.kt:724 成功提示含 restartNote、:731 失败提示不含；StudyExperienceRepositoryFactory.kt:11-17 的 create(context) 重载全库无调用（App 用的是 :19 起传入 database 的重载，closeDatabaseOnClose=false）。

审计者自评信心：`verified`

#### 5.7.4　`split-import-preparing-stuck-crash`

- **严重度**：`P2`　**类型**：`correctness`
- **标题**：切分任务卡在 PREPARING 时被当作可编辑列出，勾选触发未捕获的 require 崩溃
- **位置**：`D:/smart mistake book/core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/RoomSplitImportStore.kt:67`

**失败路径**

> BatchSplitRecognizer 先 createSplitJob（提交 PREPARING 事务，RoomSplitImportStore.kt:42）再 markReady（另一个事务），两步之间进程死亡 → 任务永久停在 PREPARING（无任何回收/重试路径，BatchSplitRecognizer.kt:99 还丢弃了 markReady 的 Boolean，调用方无法发现失败）。SplitImportDao.observeActiveJobs 第54行把 PREPARING 也列为 active，SplitImportReviewRoute.kt:79-81 显式允许渲染 PREPARING（页头显示“切分完成，勾选要录入的题目”）。用户勾选时 SplitImportReviewRoute.kt:140 repository.updateSelection → RoomSplitImportStore.updateSelected 第67行 `require(job.status == READY)` 抛 IllegalArgumentException，且该调用位于第138行 `scope.launch` 内无 try/catch → 未捕获异常导致界面崩溃。

**证据**

> RoomSplitImportStore.kt:67 `require(job.status == StudyDbValue.SplitImportStatus.READY) { "Split-import selection requires a ready job" }`；SplitImportDao.kt:53-54 `WHERE status IN ('PREPARING', 'READY')`；BatchSplitRecognizer.kt:99 `splitImports.markReady(job.jobId, job.questions.size, occurrenceTime)`（返回值丢弃，第100行无条件 return SplitReady）；SplitImportReviewRoute.kt:79、:138-146。

审计者自评信心：`likely`

#### 5.7.5　`attempt-error-type-dead-columns`

- **严重度**：`P3`　**类型**：`dead-mechanism`
- **标题**：attempt_event.error_type/error_type_confidence/low_confidence_correct 无任何生产写入方
- **位置**：`D:/smart mistake book/core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/entity/LearningEntities.kt:355`

**失败路径**

> 迁移 35→36 给 attempt_event 加了 error_type、error_type_confidence、low_confidence_correct 三列，实体也映射，但全库无生产写入：唯一构造 attempt_event 的地方 AttemptTransactionDao.kt:356-362 只写 hintCount 与 revealedBeforeAnswer；StudySubmissionPreparer.kt:242 恒传 revealedBeforeAnswer = false；error_type/error_type_confidence/low_confidence_correct 既不被写也不被读（Attempt 模型 LearningState.kt:261-273 无对应字段）。这些列永远为 NULL/0，属迁移注释自称的 “error-type channel placeholders”。任何把它当已接通信号来读的代码都会拿到恒空值。

**证据**

> MasterySchedulingMigration.kt:44-47 三条 ADD COLUMN；LearningEntities.kt:355-360 映射；AttemptTransactionDao.kt:356-362 构造处只含 hintCount/revealedBeforeAnswer；StudySubmissionPreparer.kt:241-242 `hintCount = submission.hintCount, revealedBeforeAnswer = false`；全库 grep errorType/lowConfidenceCorrect 仅在实体与迁移命中。

审计者自评信心：`verified`

#### 5.7.6　`calendar-day-backfill-untested`

- **严重度**：`P3`　**类型**：`missing-gate`
- **标题**：41→42 日历日回填表达式无测试覆盖（改坏不会有测试变红）
- **位置**：`D:/smart mistake book/core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/CalendarDayMigration.kt:26`

**失败路径**

> 回填语句 `UPDATE learner_problem_memory_state SET last_reviewed_epoch_day = last_reviewed_at_epoch_millis / 86400000 WHERE last_reviewed_at_epoch_millis > 0` 没有任何测试断言其值：core/database 的 androidTest/test 与 core/data 的 androidTest 全库 grep `last_reviewed_epoch_day` / `CalendarDayMigration` 零命中。FullMigrationMatrixInstrumentedTest.kt:16-24 只对每个版本建库→迁移→调一次 libraryCatalogCount，依赖 Room 的结构校验，不校验回填数据。把除数改成 86400/8640000、去掉 WHERE、或改成写入错误列，都不会让任何测试失败；后果是迁移后所有卡片首个复习日的 delta_t 分日基准错乱。

**证据**

> CalendarDayMigration.kt:25-27 的 UPDATE 语句；grep -rn "last_reviewed_epoch_day|CalendarDayMigration" core/database/src/{androidTest,test} core/data/src/androidTest → 0 命中；FullMigrationMatrixInstrumentedTest.kt:18-23 只断言 `libraryCatalogCount(...) == 0`。

审计者自评信心：`verified`

#### 5.7.7　`cleardata-fk-off-leak`

- **严重度**：`P3`　**类型**：`correctness`
- **标题**：clearAllData 中途异常会永久关闭该连接的外键约束
- **位置**：`D:/smart mistake book/core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/RoomBackupSupportStore.kt:109`

**失败路径**

> clearAllData 先 `PRAGMA foreign_keys = OFF`（第109行），随后按 sqlite_master 遍历所有表逐条 `DELETE FROM \`$table\``（第125行），最后才 `PRAGMA foreign_keys = ON`（第129行）。若任一 DELETE 抛异常（例如删到 FTS4 外部内容表/其 shadow 表），ON 永不执行；Room 只在自己生成代码的 onOpen 里设一次 ON（StudyDatabase_Impl.kt:702），且这是单连接共享，因此该连接剩余生命周期的所有外键约束静默失效（后续 DELETE/INSERT 不再做引用完整性检查）。当前 clearAllData 仅被 androidTest 调用（RootTutorFailClosedInstrumentedTest.kt:75），生产不可达，故为潜在风险而非现网缺陷。

**证据**

> RoomBackupSupportStore.kt:107-131（HEAD 已提交逻辑；工作树的唯一改动是 useConnection→withRawConnection，不影响该缺陷）；StudyDatabase_Impl.kt:701-703 onOpen 只执行一次 `PRAGMA foreign_keys = ON`；调用点仅 app/src/androidTest/.../RootTutorFailClosedInstrumentedTest.kt:75。

审计者自评信心：`likely`

缺口登记（有效项）合计：**55 条**；加主循环新增的 F-01 = **56 条在册**。

---

## 6. 对抗复核逐条裁决

复核者立场：默认怀疑，只有亲自读过源码、确认失败路径成立才判 CONFIRMED；并被要求反向检查两件事——
「看着像缺陷其实被上游拦住」与「看着接好了其实没人喂」，以及「是否已被未提交的并发工作树修掉」。

结论分布：**CONFIRMED 19 · UNCERTAIN 2 · REFUTED 2**。

> 本节只有三个维度，因为只有这三个维度的对抗复核**成功运行**。另四个维度（测试门禁、调度数学、持久化并发、架构边界）的复核 agent 因 API 流错误中断，**没有任何独立复核结论**——其承重项由主循环一手补验，见第 3 节与第 12.1 节。

### 6.1　事件溯源与投影完整性

- **`version-replay-trigger-untested`** → **CONFIRMED**
  - 严重度修正：`P2`
  - 逐条属实。StudyProjectionDrainer.kt:75 原文 `val requiresReplay = previous.checkpoint.projectorVersion != LearningProjector.VERSION ||`，:83-90 仅 requiresReplay 时走 commitFullReplay。主树 grep `StudyProjectionDrainer`：main 仅定义(:28)与 RoomBackedStudyExperienceRepository.kt:162 构造点，测试树唯一命中是 LearningProjectorTest.kt:268 的注释；`requiresReplay` 在测试树零命中。所有走 drainer 的 JVM 测试（RoomBackedStudyExperienceRepository 的 fake）在 RoomBackedStudyExperienceRepositoryTest.kt:1624/1636/1678/1697 seed `projectorVersion = LearningProjector.VERSION`，且 :2536-2537 `commitProjection` 直接 `error(...)`，:2513-2521 loadProjectionBatch 恒返回空事件 END_OF_LEDGER；androidTest 也只用当前版本（ChatEvidenceLedgerIntegrationTest.kt:122）或独立 knowledge 投影（KnowledgeContextRetrievalInstrumentedTest.kt:272/455）。LearningCoreVersions 注释（:7-21）确将版本不匹配全量重放列为 v5→v6/v6→v7 修复生效的唯一机制，故该分支零覆盖属实。注意 LearningProjector.VERSION 实为 PROJECTION_COMPOSITE（LearningProjector.kt:1205），结论不变。严重度应下调：这是面向未来回归的缺门，当前无任何生产错误结果，按定义属 P2 而非 P1。

- **`correction-ledger-never-fed`** → **CONFIRMED**
  - 严重度修正：`P2`
  - 写侧确实无生产调用方：`appendAttemptCorrection` 仅见 LearningProjectionPort.kt:58 定义、RoomStudyDatabase.kt:1122 实现（内部转 AttemptTransactionDao.appendCorrection）、StudyDatabaseInstrumentedTest.kt:229/331/479、以及 RoomBackedStudyExperienceRepositoryTest.kt:2411-2413 的 `error(...)` 覆写；`appendCorrection` 在 main 仅 AttemptTransactionDao.kt:446 定义并被 RoomStudyDatabase.kt:1125 调用，再无上层。app/feature 无任何修正入口（grep Undo/撤销 在 feature 仅命中组织页的本地 relation 撤销测试，非 Attempt 撤销）。ProjectionTransactionDao.kt:475-486 的 correction→FULL_REPLAY_REQUIRED、LearningProjector.kt:414-419/437-445/525-533 的修正应用分支、:547 的 correctionWatermark（进而 ReviewPlanner.kt:523 / ReviewPlannerV2.kt:784 的决策指纹输入恒定）因此生产不可达，属实。但需注明：docs/scenario-registry.md:102 已明文记录『Correction 尚无完整产品入口』，即这是已登记的功能缺口，而非审计新发现；它与 system-blueprint.md:643/1736 及 product-information-architecture.md:307/323 的撤销要求相悖，故仍成立。目前无用户可达的运行时失败，P2（当前不可达）合适。

- **`shadow-predictions-discarded`** → **UNCERTAIN**
  - 严重度修正：`P3`
  - 『被计算后直接丢弃』属实：generatePredictions 仅被 replay() 调用（LearningProjector.kt:557），结果填入 LearningProjectionResult.predictions(:572, :52)，而唯一消费方 StudyProjectionDrainer.commitFullReplay(:150-170) 只取 snapshot 与 presentationProjectionStates，不落库；main 中 `LearningProjectionResult.predictions` 除 LearningProjector 自身外无读取点。但 finding 的后果判断『审计/标定回路从未收到数据 / 标定回路永远是空的』被证伪：另一条生产链路在喂标定回路——StudyReviewPlannerService.kt:82-86 调 HLRPredictionAuditService.planPredictions 后 predictionAuditSink.record(audit)，RoomBackedStudyExperienceRepository.kt:191 注入 RoomPredictionAuditSink（写入 student_model_prediction 表），:936 backfillPredictionOutcome → predictionAuditSink.resolveOutcome 回填结果，StudySchedulingCalibration.kt:114 再 readResolvedStudentModelPredictions 做标定。所以该字段是『无人消费的重复死代码』（P3 结构性），而非 P2 缺陷；且这些影子预测用 replay 末尾快照的最终 mastery 为每个历史 attempt 赋值（LearningProjector.kt:1155），本身带前视偏差，幸而未被使用。严重度夸大且后果不成立，故不判 CONFIRMED。

- **`bounded-drain-throws-on-backlog`** → **UNCERTAIN**
  - 严重度修正：`P2`
  - 代码路径本身属实：drain() 用 repeat(MAX_PROJECTION_DRAIN_STEPS=64)(:36)，每步 loadProjectionBatch limit=PROJECTION_BATCH_SIZE=100(:41,:189)，64 步后 :138 抛 ProjectionCasConflictException；RoomBackedStudyExperienceRepository.kt:1002-1011 runOperation 捕获→publishFailure(:1013-1021) 将 StudyDataStatus 置 ERROR。但『>6400 条未消费 outbox』在生产的可达性未能证实，且 finding 举的两例都不成立：(1) 批量导入走的是 BatchImportRepository/MAX_BATCH_IMPORT_PAGES（problem 页面），不产生 learning ledger 事件；(2) 正常每次 oper 前都先 currentLearnerSnapshot()→drain()（如 submitChoice RoomBackedStudyExperienceRepository.kt:527），单个用户动作只追加 1 条事件，不会累积到 6400。恢复备份是否落后取决于归档是否含 learner_projection_snapshot（RoomBackupSupportStore.kt:114 只展示清库逻辑，未证实恢复语义），无证据说明 checkpoint 会远落后账本头。可证的批量生产者只有 v41 ChatEvidenceMigration 的一次性 outbox 回填（ChatEvidenceMigration.kt:77-108），量级也难达 6400。因此判 UNCERTAIN：潜在埋雷成立但触发条件未证实，维持 P2（潜在风险/当前不可达），不升至 P1。

- **`projection-consumption-write-only`** → **CONFIRMED**
  - 严重度修正：`P3`
  - 属实。ProjectionTransactionDao.kt:754-763 为每个被消费事件插入 ProjectionConsumptionEntity（含 projectorVersion=commit.snapshot.checkpoint.projectorVersion 与 consumedAt=commit.snapshot.checkpoint.projectedAtEpochMillis），实体定义 LearningEntities.kt:534-566。主树 grep `ProjectionConsumptionEntity|projection_consumption` 仅命中实体定义、StudyDatabase.kt:170 注册、相关文件的 import、ProjectionTransactionDao 的 :411-412 抽象插入方法与 :755 插入点；无任何 `SELECT ... FROM projection_consumption` 或 @Query。亦无删除/截断：grep `DELETE FROM projection_outbox|prune` 零命中，outbox 行永存，故 FK ON DELETE CASCADE 永不触发，该表随账本单调增长。commitFullReplay(:155-157) 只补写 eventSequence > expectedCheckpoint 的回执，新版本下存量事件确无消费记录，故该表确不足以支撑按 projectorVersion 重算。作为只写不读、无上限的结构性冗余，P3 恰当。

### 6.2　知识库联结与掌握度聚合

- **`pseudo-attribution-unconditional`** → **CONFIRMED**
  - 严重度修正：无（维持原判）
  - 亲自读到：StudySubmissionPreparer.kt:70-76 与 :253-259 起手无条件调 buildPseudoAttribution，:92/:275 把结果整体赋给 attributions；buildPseudoAttribution(:154-179) 只走 database.ensurePseudoKnowledgeBinding，无“已有真绑定则跳过”分支。RoomKnowledgeBaseStore.kt:44 恒构造 knowledgeNodeId="pseudo:${subject.uppercase()}"，:81 insertKnowledgeBindings（ProblemOrganizationDao.kt:196 为 OnConflictStrategy.IGNORE，只会挡住重复行、不会退回真绑定）。LearningProjector.kt:749-766 只遍历 assessmentSnapshot.attributions，:965 每个 DIRECT attribution 取 attempt.evidence.weight 全量入账——没有任何按 practiceUnitId 重映射到真绑定的路径（domain 层 grep 无 pseudo 处理）。规格 mastery-scheduling-spec.md:318 原文确为“自评/评级快照对无绑定题自动携带伪归因”，代码未做该限定；StudySubmissionPreparer.kt:142-145 的 KDoc 也自称“when a saved question carries no accepted knowledge bindings”，与实际代码矛盾。生产可达性成立：ReviewSessionScreen 的四键评级经 SmartMistakeBookDestinations.kt:231 submitReviewRating → StudyRatingSubmissionService.kt:94 prepareRatingSubmission，而捕获题（正是可能有真绑定的题）走的就是这条；真绑定的题其 queueItem.knowledgeNodeIds 为真 KC（StudyReviewPlannerService.kt:205-219 的 ifEmpty 只在空时回落），证据却只落伪节点。未修改（git status 确认 StudySubmissionPreparer.kt 不在改动列表，非工作树已修）。唯一需要修正的是后果措辞：真 KC 并非“永远 UNKNOWN”——KnowledgeQuizFeedbackWriter.kt:62-77 以真实 knowledgeNodeId 写 learner_chat_evidence，经 ProjectionTransactionDao.kt:1124 / ChatEvidenceDao.kt:165 转成 ChatEvidenceSubmitted，LearningProjector.kt:576-630 projectChatEvidence 会更新该真 KC；视觉通道（VisualInteractionIngestor）在真绑定组更早时也给真 KC。因此缺陷是“评级/自评这一主通道完全跳过真 KC”，而非“真 KC 全无证据”，P1 仍成立（评级的 weight 远大于 chat 的 0.35 上限且不受门控配额限制）。

- **`remediation-gated-on-debug-fixture`** → **CONFIRMED**
  - 严重度修正：无（维持原判）
  - 亲自读到：RoomBackedStudyExperienceRepository.kt:460-461 teachingArtifact 直接 = fixtureSource.teachingArtifactForPracticeUnit；:476 reTeachOpening 与 :499 prerequisiteRemediation 都以 `teachingArtifact(...) ?: return null` 起手。StudyFixtureSource.kt:40-43 StudyFixtureRegistry.source 默认 EmptyStudyFixtureSource，:31-33 返回 null；全仓 StudyFixtureRegistry.source 赋值只有 core/data/src/debug/.../M1FixtureInitProvider.kt:16，且 M1FixtureInitProvider 只注册在 core/data/src/debug/AndroidManifest.xml。app 的 localFirst/strictOffline 两个 flavor manifest 只有权限/网络配置，app/src/debug 只有两个 activity，都不含该 provider——所以任一 release 变体 registry 保持 Empty，teachingArtifact 对所有题 null。UI 侧 SmartMistakeBookDestinations.kt 里 loadedArtifact = repository.teachingArtifact(...)，随后 reTeachOpening/prerequisiteRemediation 都在 `if (loadedArtifact != null)` 的 else 分支恒为 null。测试反证成立：RoomBackedStudyExperienceRepositoryTest.kt:1365 用 M1CuratedFixtureSource。另注：docs/scenario-registry.md:101 把 teachingArtifact 记为只覆盖 curated M1 的 IMPLEMENTED-SLICE，并称“真实已保存错题进入原题复习不依赖 teaching artifact”——但 reTeachOpening/prerequisiteRemediation 恰恰依赖它且对真实错题在所有构建里都拿不到 artifact，该记录并不构成对“release 通道恒空”的豁免。未修改（目标文件不在 git 改动列表）。

- **`visual-attribution-weights-dead`** → **CONFIRMED**
  - 严重度修正：无（维持原判）
  - 亲自读到：VisualInteractionIngestor.kt:80-100 计算 secondaryWeight=0.4/(size-1)、index 0 取 0.6 并分 PRIMARY/SECONDARY 写进 KnowledgeEvidenceAttribution.weight。LearningProjector.kt:954-965 projectMastery 注释明写“it no longer splits evidence”，`val weight = attempt.evidence.weight` 对每个 DIRECT KC 全量入账，完全不读 attribution.weight。全仓 attribution.weight 的读取只有 core/model/.../LearningLedgerFingerprint.kt:136（指纹哈希）与 LearningState.kt:206（上界校验）——即改 0.6/0.4 只改指纹不改结果。规格 mastery-scheduling-spec.md:83 已宣布“视觉通道 PRIMARY/SECONDARY 常数废除，改 PRIMARY=最大 strength 绑定”，代码仍保留并计算。P2 dead-mechanism 定级合适。

- **`visual-taxonomy-group-shadowed-by-pseudo`** → **CONFIRMED**
  - 严重度修正：无（维持原判）
  - 亲自读到：VisualInteractionIngestor.kt:74-81 bindings 按 (acceptedAt,bindingId) 排序后 taxonomyVersion=bindings.first().taxonomyVersion，attributed=同 taxonomy 子集；readPracticeUnitKnowledgeBindings → ProblemOrganizationDao.kt:222-230 返回该题全部绑定行、不按 source_type 过滤。伪绑定确会以更早 acceptedAt 存在并存活：StudyReviewPlannerService.kt:179-189 对无绑定题以 taxonomyVersion="pseudo-plan-v1" 物化（acceptedAt=计划时刻）；StudySubmissionPreparer.kt:73 又以 "local-review-self-report-v1" 建行。RoomProblemOrganizationStore.kt:94 在组织落库时调 deleteUnreferencedKnowledgeBindings，而该 SQL（ProblemOrganizationDao.kt:277-291）只删“无 attribution 引用”的绑定——评级已为该伪绑定写下 attribution，故伪绑定在组织补真绑定后仍留下，且 acceptedAt 早于真绑定。至此排序 first() 取到伪组，真 KC 被静默丢弃。可达性成立但依赖“组织晚于首次评级/计划”的时序（组织由 feature/library/MistakeOrganizationSection.kt:366 的用户动作触发，非捕获同步），P2 定级恰当。注意同一机制不止伪绑定：任何更晚的 USER_CORRECTED 重组织新 taxonomy 组同样会被较早组遮蔽。

- **`binding-strength-no-consumer`** → **CONFIRMED**
  - 严重度修正：`P3`
  - 事实全部核对属实：strength 读取点只有 RoomMistakeOrganizationRepository.kt:1043（指纹）、DatabaseContract.kt:888、LibraryCatalogView.kt:114、MasterySchedulingMigration.kt:174（lattice 视图）与 RoomProblemOrganizationStore.kt:277-278（上界校验），无任何掌握度聚合消费；写入端 RoomMistakeOrganizationRepository.kt:995-1011、RoomKnowledgeBaseStore.kt:76、KnowledgeGroundingDao.kt:346。代码确实遵从权威 mastery-scheduling-spec.md:172“binding.strength 降级为排序/展示用途”，而 docs/research/mastery-math-modeling.md §9（contribution_k=s·w_e·strength_k/Σstrength）与 docs/research/three-store-linkage-design.md §3.2 仍写成分摊权重——这两份是过期研究稿。故这不是代码缺陷，而是文档漂移（研究稿未随 §2.13 修订同步）。定级从 P2 下调至 P3：无运行时后果，只误导据研究稿实现/验收的人。

- **`material-limit-starvation`** → **CONFIRMED**
  - 严重度修正：无（维持原判）
  - 亲自读到：KnowledgeTeachingMaterialDao.kt:23-58 的 `LIMIT :limit` 位于 ORDER BY（best_role_rank → material_type CASE → title → material_id）之后；RoomTutorTeachingReferenceRepository.kt:25-29 referencesFor(limit=DEFAULT_LIMIT) 把该 limit 原样传给 DAO，:134-141 选择器在 20000 字符预算内 `if (selected.size < limit && reference.markdownChars <= remainingChars)` 是 skip 而非 break，候选集已被 DAO 限死为 limit 条。常量核对：TutorTeachingReferenceRepository.kt:18 DEFAULT_LIMIT=MAX_TEACHING_REFERENCES=4（TutorTasks.kt:257），预算 20000（TutorTasks.kt:258），单份 markdownChars 上界 4000+8000+32000+8000=52000（TutorTeachingReference.kt:74-78），材料合同同样允许 content 32000（KnowledgeTeachingMaterialContract.kt:83-86），单材料可超预算。故“前 4 份按优先级的材料都超预算 → 返回空 → PrerequisiteRemediationPolicy.offer 无材料”的路径成立，第 5 份小材料不可见。可达性依赖数据（需同一 KC 有 ≥4 份 >20000 字符的高优先材料；内置材料包按选择器注释只含 METHOD_MODEL/CONCEPT_EXPLANATION），属潜在风险，P2 可接受。

- **`duplicate-dead-visual-constants`** → **CONFIRMED**
  - 严重度修正：无（维持原判）
  - 亲自 grep 全仓（排除 .worktrees/build）：VISUAL_VIOLATED_WEIGHT / PRIMARY_VISUAL_ATTRIBUTION_WEIGHT / SECONDARY_VISUAL_ATTRIBUTION_WEIGHT_POOL 在主树的定义只出现在 RoomBackedStudyExperienceRepository.kt:1033-1035 与 VisualInteractionIngestor.kt:181-183；前者是 private const，且这两个文件中除 VisualInteractionIngestor 自己的 companion 外无任何读取（可见性上也不可能被外部引用）。KNOWLEDGE_QUIZ_CONVERSATION_ID 只在 RoomBackedStudyExperienceRepository.kt:1027 定义（同样 private），实际会话 id 由 KnowledgeReviewSessionViewModel.kt:193-194 自拼 "knowledge-quiz-review:$sessionStartedAtEpochMillis"（:169 调用）。P3 死常量定级合适。

- **`two-prerequisite-of-constants`** → **CONFIRMED**
  - 严重度修正：无（维持原判）
  - 亲自读到：StudyDbValue.kt:5-7 KnowledgeRelationType.PREREQUISITE_OF="PREREQUISITE_OF"，:95-101 RelationType.PREREQUISITE_OF 同值——两表语义不同、字符串相同。KnowledgeNodeRelationDao.kt:26-37 readForDependents 只按 subject + dependent_knowledge_node_id IN (...) 过滤，无 relation_type 条件。唯一保证来自 KnowledgeNodeRelationContract.kt:29 requireValid(relationType == StudyDbValue.KnowledgeRelationType.PREREQUISITE_OF) 的导入校验。当前仅有 PREREQUISITE_OF 会入表，故属结构性埋雷、当前不可达，P3 定级恰当。

### 6.3　模型边界与结构化输出嵌合

- **`image-classify-kind-never-supported`** → **CONFIRMED**
  - 严重度修正：`P1`
  - OpenAiCompatibleModelGateway.kt:485-499 的 buildSet 只 add TUTOR_PLAN/RESPOND/LOBBY/PROBLEM_CLASSIFY/KNOWLEDGE_QUIZ，图像分支 492-497 只 add CAPTURE_ASSESS/CAPTURE_PARSE/TUTOR_VISUAL_GENERATE/TUTOR_VISUAL_REVIEW，确无 IMAGE_PIPELINE_CLASSIFY。生产 DI 在 SmartMistakeBookApplication.kt:223-232/248 用该 gateway 构造的 modelTaskRepository；capabilities()=toCapabilities()（156-157）。RoomModelTaskRepository.capabilityFailure(600-610) 在 providerId!=unconfigured 时以 provider.supports(kind) 判 PROVIDER_CAPABILITY_MISSING→PERMANENT_FAILURE。RoomCaptureWorkflowRepository.decideAndRedraw(395-428) 构造 ImagePipelineClassifyInput（ModelTasks.kt:228-252）并 execute，status!=SUCCEEDED→shouldRedraw 恒 false，scheduleCleanRedraw 唯一调用点(568/428)永不触发。对照 FakeModelGateway 在 core/data/src/debug(166-170 支持该 kind)故 androidTest 全绿。docs/image-pipeline-decision.md:8-10 确称已在生产运行。工作树未改这些文件。

- **`egress-bypass-image-channel`** → **CONFIRMED**
  - 严重度修正：`P2`
  - AttachedImageGeneratorFactory.kt:29-59 只经 resolveImageCredential（ImageCredentialGate.kt:13-22 仅查 networkRequestsAllowed+凭证+supportsImageInput，无 consent），OpenAiImageGenerationChannel.generateViaEdits(56-89) 直接 POST /v1/images/edits 原图字节，不经 ModelEgressPolicy/agentConsentMatches（对照 ModelEgress.kt:447-455）。SmartMistakeBookRoot.kt:755-762 无条件创建 resolver，TutorChatConversation.kt:358-367 渲染时调用且无 consentEnabled 判断。consent 开关（SecondaryScreens.kt:400-424，DataStoreModelAgentConsentStore.kt:35-46）不改 networkRequestsAllowed（Capabilities.kt:21-22 只看 flavor），localFirst 生产 flavor 下可达。工作树未改这些文件。

- **`mastered-anchor-unverified`** → **REFUTED**
  - 严重度修正：无（维持原判）
  - 已提交 HEAD 确如所述：RoomTutorToolRunner.kt:344 传 MasteryWriteGate.evidenceAnchorCount(call.rationale)，HEAD MasteryWriteGate 仅正则计数、拒门在 253-261 用该值。但未提交工作树已修：git diff 显示 RoomTutorToolRunner.kt:359-368 改为 POSITIVE+MASTERED 时用 verifiedEvidenceAnchorCount(rationale, verifiableSessionText(context))，MasteryWriteGate.kt:136-147 新增真实子串核对，OpenAiModelTaskAdapters.kt:465 亦加 prompt 约束。按复核规则判 REFUTED。（附注：finding 引的 MasteryWriteGate.kt:286-296 是工作树行号，HEAD 对应 253-261。）

- **`problem-classify-dead-output-fields`** → **CONFIRMED**
  - 严重度修正：`P2`
  - 全库 src/main grep：reviewPriorityMarkdown 仅命中定义(ProblemOrganizationTasks.kt:329,348,353)与 parser(OpenAiProblemOrganizationProtocol.kt:100,126,248)，无读取方；summaryMarkdown 对该类型同理；observableOutcomeMarkdown(ProblemOrganizationTasks.kt:224,250,261; parser 191)与 AtomicKnowledgeSuggestion.boundaryMarkdown(225,254,262)无消费方。写路径 buildConfirmationCommand(890-1067)/isAcceptableForPersistence(1087-1102)/acceptOrganizationLocally(720-765) 只消费 classifications/relations/原子 id 匹配/stepAttributions/difficultyTier/groundingRequests。init 校验确要求这些字段(250-262,347-353)。这些字段被 requiredString 解析，模型必须产出却从不被读。工作树未改。

- **`image-classify-text-formulas-dead`** → **CONFIRMED**
  - 严重度修正：`P2`
  - ModelTasks.kt:436-437 定义 textMarkdown/formulas；OpenAiModelResponseParsers.kt:281-292 忠实解析；prompt OpenAiModelTaskAdapters.kt:125,127 明确要求；唯一消费点 RoomCaptureWorkflowRepository.kt:418-421 只比较 problemKind==WITH_FIGURE。全库 main 无对该类型 .textMarkdown/.formulas 的读取。工作树未改。

- **`tutor-difficulty-reason-dead`** → **CONFIRMED**
  - 严重度修正：`P2`
  - TutorTasks.kt:508 定义、522 requireTutorMarkdown 必填；parser OpenAiModelResponseParsers.kt:418 requiredString、542 在 wire-key 集；prompt 列于 OpenAiModelTaskAdapters.kt:215。全库 main 无 difficultyReasonMarkdown 读取（仅定义/prompt/parser/测试）。工作树未改。

- **`debrief-focus-labels-dead-and-kind-unsupported`** → **CONFIRMED**
  - 严重度修正：`P2`
  - (1) TutorTasks.kt:869/879-884 要求 teachingFocusLabels 1..8 非空，OpenAiModelResponseParsers.kt:590-604 解析，但消费点 SavedMistakeTutorRoute.kt:277-289 只读 misconceptionMarkdown；全库 main 无 teachingFocusLabels 读取。(2) LEARNING_SUMMARIZE 不在 OpenAiCompatibleModelGateway.kt:485-499，也不在 FakeModelGateway.kt:166-170。(3) 唯一构造点 buildTutorDebriefRequest(TutorModelTaskPolicy.kt:246) 仅在 executionLocation==LOCAL_NO_EGRESS 时返回非空，而 main 中只有 debug FakeModelGateway 是 LOCAL_NO_EGRESS（UnavailableModelGateway 为 UNAVAILABLE/unconfigured），该 provider 又不支持该 kind→RoomModelTaskRepository.capabilityFailure(605) 恒拒，debrief 通道整体不可达。工作树未改。

- **`contract-registry-test-only`** → **CONFIRMED**
  - 严重度修正：`P3`
  - 全库 grep：ModelTaskContractRegistry/ModelTaskContract 仅出现在本文件与 core/model/src/test/.../ModelTaskContractRegistryTest.kt；运行时收口在 ModelEgress.requireAuthorizes(538) 与硬编码常量，registry 不在 dispatch 路径（ARCHITECTURE.md:22 自认 parity 表）。修正 finding 一处夸大：该测试会断言 TUTOR_LOBBY/CAPTURE_ASSESS/PARSE/IMAGE_PIPELINE_CLASSIFY 的 assetPolicy/requiredDisclosures(20-65)并用 init 约束(13-39)，改动这些确会失败，故并非『任何改动都不会有测试失败』；但『无生产调用方、契约表与运行时两套』这一核心成立。工作树未改。

- **`attention-floor-unreachable`** → **REFUTED**
  - 严重度修正：无（维持原判）
  - 事实成立：RoomTutorToolRunner.kt:86 attentionFactor 默认 1.0，RoomModelTaskRepository.toolContext(674-692) 不传；唯一另一 GateInput 构造点 KnowledgeQuizFeedbackWriter.kt:43-58 也传 1.0→MasteryWriteGate.kt:307-309 门不可达。但 docs/specs/2026-09-02-tool-loop-wiring-design.md:~124 明确记为有意延后：『真实信号接线未做…P2 默认 1.0 不误伤，拒写线只在未来 UI 采集接入后触发…标为后续 UI 接线』。属已记录的设计取舍，非缺陷，按判据 5 判 REFUTED。

- **`tool-confidence-default-0.8`** → **CONFIRMED**
  - 严重度修正：`P3`
  - OpenAiModelResponseParsers.kt:494 confidence = call.optionalDouble(\"confidence\") ?: 0.8；OpenAiModelProtocol.kt:466 同；TutorToolLoop.kt:83 默认 0.8；MasteryWriteGate.kt:30 EVIDENCE_CONFIDENCE_THRESHOLD=0.7、280-282 据此拒门，0.8>0.7 故缺省即过门。direction/understanding 缺失在 RoomTutorToolRunner 更早被拒(310)，故 confidence 是唯一『以默认值过证据门』的字段，路径可达（模型可省略该可选字段）。spec 2026-09-02:117 只记录默认值 0.8、未说明与 0.7 门的关系，仍计为轻微 fail-open；P3。

---

## 7. 已核实无误项（healthy）

审计者与复核者共同确认「机制真实存在且在生产路径上生效」的部分。列出它们是必要的：
这份记录如果只有缺陷，读起来会比实际状态更糟，也不足以定位修复的边界。

### 7.1　调度与记忆数学内核（8 条）

- FSRS-6 公式移植逐项忠实：retention/FACTOR 推导与 R(0)=1、R(S,S)=0.9（FsrsScheduleMath.kt:63-71）与 py-fsrs 一致；同日分支 shortTermStability 的 G≥2 地板 1.0 与 AGAIN 豁免（:108-115）、跨日成功 nextRecallStability 的 HP/EB（:120-139）、跨日遗忘 min(longTerm, S/e^{w17·w18})（:142-154）、难度均值回归锚点 D0(4)=w4−e^{w5·3}+1（:160-167）均与 spec §2.4 和文档伪码逐项对上，且已把 0-based 枚举与 1-based 评级换算注释清楚（rating.ordinal-2）。
- 全局不变量由类型强制而非『通常成立』：ProblemMemoryState.init 强制 stabilityDays 有限且>0、difficulty∈[1,10]、nextReview>=lastReviewed（LearningState.kt:522-551）；每次写入都过 clampStability/clampDifficulty（FsrsScheduleMath.kt:100-102，MemoryUpdateModel.kt:74-99）；retention 结果 coerceIn(0,1)。成功 S'≥S 因 (exp((1−R)·w10)−1)≥0 且 (11−D)>0 在构造上成立，失败 S'≤S/e^{w17w18}<S 且 ≥STABILITY_MIN。
- 日历日 delta_t 从时间戳现算、不读派生字段，重放确定性有保证：LearningProjector.kt:862-865 与 1201-1202 (localEpochDayOf)，ReviewLogSink 用同一口径（ReviewLogSink.kt:60-75），并有注释锁定 AUDIT §3.7 的失败类别；review_log.rating 记的是调度实际使用的评级（ReviewLogSink.kt:82-88 schedulingRatingFor），与训练分布一致。
- 前置门（曾整条空转）确实已接通：StudyReviewPlannerService.kt:318 把 KnowledgePrerequisiteReader 解析的图喂入 ReviewPlanningRequest；Reader 按 KC 自己的 subject 分区并 256 分块（KnowledgePrerequisiteReader.kt:45-56），判定唯一权威是 KnowledgeReadiness（gapOf/weakestBlockingPrerequisite），排程与会话两侧共用同一阈值常量。这是本工程第三次『定义在、赋值不在』检查后仍闭合的通道。
- 毕业/maintenance 与 leech 都是确定性实现且与 spec 一致：毕业连胜 §2.10（LearningProjector.kt:899-921，I(r*,S)≥90 天则改用 I(0.8,S)，ReviewPlannerV2 打 GRADUATED_MAINTENANCE），leech §2.16（lapse≥6 且连续 2 次跨日 Again，LearningState.kt:558-559；难度冻结 LearningProjector.kt:895-898；×0.15 重罚保持恢复路径可达 ReviewPlannerV2.kt:439,886）。
- 参数优化器的『写回—被调度读取』链路是通的（非空转）：StudySchedulingCalibration.kt:88 写入 DataStoreSchedulingSettingsStore（:83-101 带长度校验）→ 下次启动 Application.kt:167-173 同步读取 → RoomBackedStudyExperienceRepository.kt:152-155 注入 FsrsMemoryUpdateModel(parameters=...)。w16 按研究 §7 钉为 1.0 且不参与拟合、w15 增 HARD 样本门槛（SchedulingEvaluation.kt:433-454,574-582），与 algorithm-changes-2026-09-09 §1.4 一致。
- 数值退化路径基本被处理：intervalDays 的 raw 若溢出/NaN 由 round().toInt()+coerceIn(1,36500) 兜底（FsrsScheduleMath.kt:88-91），addDays 有 Long 溢出保护（MemoryUpdateModel.kt:113-116），LogDurationModel 的 GLOBAL_PRIOR 用 ln 后 exp 回取而非 exp(60)（LogDurationModel.kt:152-154），OptimalRetention 的闭式积分在 DECAY+1≠0 下有效并 coerceIn(0,1)。
- 模型只交语义、数值在本地：chat 证据 weight 是本地常量封顶（LearningState.kt:424-428 且 init require weight≤0.35），难度档 TutorDifficultyTier→秒数由本地常数表出（IntakeDurationBaseline.kt:30-42），HLR 影子特征把 1..10 难度归一化后再喂 0..1 约束的 HLRFeatures（HLRPredictionAuditService.kt:149）。


### 7.2　事件溯源与投影完整性（7 条）

- 每条写入路径都有确定性幂等键与指纹复核：Attempt 用 submissionId（AttemptTransactionDao.kt:310-311,556-593），AnswerReveal 用 assessmentEventId 且复算 LearningLedgerFingerprint.answerReveal 比对（:376-394,523-554），TutorAnswerExposure 用 exposureId 派生 outcomeId 并 verifyMaterialized 复算指纹（TutorExposureDao.kt:291-330），视觉互动用 stableId 生成 submissionId/attemptId（VisualInteractionIngestor.kt:132-136），因此重复 sweep 不会重复计分（:161-176 明确 created 才计入）。
- outbox 行与事件实体用 canonicalFingerprint 双向绑定：readAttempt/readAnswerReveal/readTutorAnswerExposure/readChatEvidence 每次都从实体重算指纹并与 outbox 比对，任一不一致即返回 null → ProjectionBatchStopReason.CONFLICT（ProjectionTransactionDao.kt:1046-1127,517-526）。
- 提交是单事务 CAS，不存在半途快照：commitProjection 标 @Transaction（ProjectionTransactionDao.kt:635-771），header 的 checkpoint/state_version CAS、呈现态转移、全表 delete+insert、consumption 写入全在同一事务；崩溃只会保留旧 checkpoint，下次从该 checkpoint 重放（对应 system-blueprint.md:1737 的要求）。CURRENT 快照还被强制要求 checkpoint==ledgerHead（:654-658），杜绝把落后快照标成 new。
- 顺序键唯一且连续、重放确定性：outbox_sequence 由 learning_sequence 的 CAS 单调分配（AttemptTransactionDao.kt:130-145、ChatEvidenceDao.kt:153-162、TutorExposureDao.kt:332-341），读侧一律 ORDER BY outbox_sequence（ProjectionTransactionDao.kt:106-142）并做连续性 GAP 检测（:459-473,569-615）；replay 按 eventSequence 排序且 require 1..n 无洞无重复（LearningProjector.kt:386-392）。同一时间戳用 effectiveAt=maxOf(projectedAt, occurredAt) 单调推进（:239,:434），重放逐位可复现。
- 版本不兼容不会静默混算：requireCompatibleSnapshot 在 checkpoint.projectorVersion 与当前 VERSION 不符且快照非空时直接抛错（LearningProjector.kt:625-638），配合 drainer 的 requiresReplay + commitFullReplay 形成『要么全量重放、要么拒绝』，而非旧值新值混用。
- 时间口径的确定性有测试且实现正确：设备时钟回拨被记录并禁止把复习时间前移（LearningProjector.kt:847-851；LearningProjectorTest.kt:112），上一条本地日由 lastReviewedAtEpochMillis+事件自身 UTC 偏移现算而非读可被某条通道按 UTC 污染的持久字段（:1201-1202,:862-864；测试 :287,:342），chat 证据对 lastEvidenceAt/lastEvidenceDirection 的写入与全量重放等价（:576-623；测试 :203,:221,:244,:266）。
- 呈现权威（response ordinal / terminal reveal）在 DB 与投影两侧都有闸：transitionPresentationAuthorityForAttempt/ForReveal 做 state_version CAS 与 ordinal 连续性校验（ProjectionTransactionDao.kt:810-925），replay 侧也 require 每 presentation 的 ordinal 从 1 连续、且至多一个 terminal reveal（LearningProjector.kt:402-411），且所有呈现态在提交后被 verifyCommittedPresentationStates 与重放结果逐项比对（:927-969）。


### 7.3　知识库联结与掌握度聚合（6 条）

- conservativeMasteryScore 确实是 masteryScore 的严格下界，不存在被违反的输入：masteryLowerBound(LearningProjector.kt:1096-1099) = (p − 1.2·sqrt(p(1−p)/(m+1))).coerceIn(0.0, p)，上界被 coerce 钉死为 p；两处调用点（projectMastery:973、projectChatEvidence:590）都用同一个 updatedProbability 推出下界与点估计，且 KnowledgeMasteryState 的 init(LearningState.kt:649-652) 再次断言 `in 0.0..masteryScore`。negative/positive 学习率仅影响 p 本身，无法让下界越界（UNCERTAINTY_SCALE=1.2 为正，LearningProjector.kt:1210）。
- 前置图解析方向与分区正确且补齐了图外节点：KnowledgePrerequisiteReader.graphFor 用每个 KC 自己的 subject 分组查询（KnowledgePrerequisiteReader.kt:45-56，规避关系表按 subject 分区导致的静默空集），按 256 分块对齐 RoomKnowledgeBaseStore 的硬上限（:48、:75-76），并单独取回不在查询集内的前置节点行以拿到其 subject/displayName（:59-71）——这三步正是补救通道能按“前置 KC 自己的科目”检索材料的前提。
- 前置判定规则单一权威且被排程侧真实消费：KnowledgeReadiness.READY_THRESHOLD(0.6) 同时被 KnowledgeReadiness.weakestBlockingPrerequisite(:58,:66) 与 ReviewPlannerV2 的同 KC 配额豁免(:729) 使用；StudyReviewPlannerService.createReviewPlan 在生产调用点把当日候选的图填进 ReviewPlanningRequest(:318-320)，ReviewPlannerV2 据此产出 PREREQ_GAP 理由与 `−PREREQ_GAP_WEIGHT×prereqGap` 打分项(:448-453,:618)——文档声称的 L4 空转确已修复，“无掌握度证据的前置不算缺失”的过滤器(:55-58)也避免了新绑定关系把题目一次性判成缺前置的噪声。
- 教学材料排序有可测的单一权威且与 SQL 对齐：TutorTeachingReferenceSelector.reTeachPriority 是纯函数权威(RoomTutorTeachingReferenceRepository.kt:73-81)，选择器按 role rank→type priority→materialId 排序并施加 20000 字符预算(:96-142)，DAO 的 ORDER BY CASE(:45-54) 与 bindingRoleRank(:146-150) 逐值同构；role 被当作序（PRIMARY→SUPPORTING→其他）而非数值权重，与 §3.6 的修正说明一致。
- 伪 KC 物化是幂等的：ensurePseudoKnowledgeBinding 以 (practiceUnit, revision, taxonomyVersion, nodeId) 拼 bindingId、insert 用 OnConflictStrategy.IGNORE，并在插入后按 id 回读校验(RoomKnowledgeBaseStore.kt:44-90；ProblemOrganizationDao.kt:196-202)，重复调用不会产生同身份多行（不同 taxonomyVersion 会各留一行，属预期版本锚）。
- 知识点 quiz 通道有真实 UI 调用点且带防刷与锚定：KnowledgeReviewSessionViewModel.submitAnswer(:142-186) 经 SmartMistakeBookRoot.kt:619 注入 repository::submitKnowledgeQuizFeedback，KnowledgeQuizFeedbackWriter 先校验节点真实存在(:31)、经 MasteryWriteGate 施加 per-conversation/per-learner 配额(:42-59) 再写 learner_chat_evidence，最终由 LearningProjector.projectChatEvidence(:576-622) 落到 KC 层——这是三条 KC 级客观证据通道中唯一不经 binding、结构完整的通道。


### 7.4　模型边界与结构化输出嵌合（10 条）

- 出网授权是两点校验、逐字节可核：ModelEgressPolicy.authorize + requireCurrentExternalAuthorization + gateway 在 enqueue 前的 requireCurrentAuthorizationBeforeEnqueue（ModelEgress.kt:457-536；OpenAiCompatibleModelGateway.kt:248-267,311-341），并且资产大小在读取时二次核对（OpenAiCompatibleModelGateway.kt:355-368,612-630）。
- 上传预算在不分配 Base64 的前提下先算后发：ModelRequestPayloadBudget.requirePreparedRequestFits + base64EncodedBytes 溢出保护（ModelEgress.kt:401-438），两条 permit 分支都调用（OpenAiCompatibleModelGateway.kt:370-380,585-593）。
- 模型输出的信任边界集中在 ModelTaskCompletionValidator，且仓库在写 SUCCEEDED 前强制 requireValid：adapter 可反序列化，但 USER_CONFIRMED/LOCAL_POLICY_ACCEPTED 只能本地产生（KnowledgeQuizTasks.kt:122-200,255-305；RoomModelTaskRepository.kt:435-441）；ModelTaskSnapshot.init 也再次强制（ModelTasks.kt:510-519）。
- 写掌握度是服务端（本地执行器）权限门，模型只交语义：tutorToolAuthorization 的 intent×confidence×declaredTools 交集 + writeAnchoredToCurrentQuestion 锚定 Respond（TutorToolLoop.kt:211-263；RoomModelTaskRepository.kt:274-293），数值权重/冷却/配额/注意度本地常量（MasteryWriteGate.kt:23-176,266-316）。
- 写证据幂等且有被拒审计行：evidence_id 由 requestId+tool+KC 确定性派生，Room IGNORE 兜底重试；被拒也落 rejected 观察行而非静默丢弃（RoomTutorToolRunner.kt:28-34,301-305,402-425）。
- 「模型只交语义枚举，一切数值在本地」在难度上前进到位：模型只给 EASY/MEDIUM/HARD（OpenAiProblemOrganizationProtocol.kt:260-261），秒数由 IntakeDurationBaseline.secondsForTier / StudyReviewPlannerService 本地表给出，consult 层按 practiceUnit 落库（RoomMistakeOrganizationRepository.kt:357-363）。
- 密钥处理达标：凭证以 ModelCredentialReadResult.apiKey.use 租借并在用后 Arrays.fill 清零，未进日志/异常文本；图片通道同样（OpenAiCompatibleModelGateway.kt:163-171,305；ConfiguredCleanImageGenerator.kt:52-88；AttachedImageGeneratorFactory.kt:33-45）。
- 图片出的网也过 SSRF 收口：resolveGuardedEdits/guardedModelClient 拒绝指向本机/局域/保留地址（OpenAiModelTransport.kt:131-192），聊天与图片通道共用。
- 知识点复习（KNOWLEDGE_QUIZ）输出契约自洽：correctChoiceId 必须属于 choices、choiceId 唯一（KnowledgeQuizTasks.kt:66-81；KnowledgeQuizWire.kt:23-31），下游 toTutorAssessmentItem 复用本地判答 evaluateChoice（Assessment.kt:61-91）。
- 请求指纹对 schema 演进有显式平移处理：v7→v8 consent 改名兼容解码、空工具载体/空页对比键在指纹中抹平（ModelTasks.kt:571-591,708-750），并有 LogicalOperationFingerprint 区分「同一语义操作」与「重试包络」。


### 7.5　测试严谨度与门禁（9 条）

- KnowledgeReadinessTest 的阈值边界用字面量锁定，改常量会被测出：第 41 行以 masteryScoreOf={0.6} 断言不阻塞、第 31 行断言 gap==0.6-0.25、第 117/138 行断言 0.5/0.35。把 KnowledgeReadiness.READY_THRESHOLD 从 0.6 改成 0.7 会让多条断言变红，属于有效抗变异（core/domain/src/test/kotlin/.../KnowledgeReadinessTest.kt）。
- ReviewPlannerV2Test 有竞争性前置降权测试（第 483-499 行）：预算只够一题、两题除前置外参数全同，断言缺前置的被排后并带 PREREQ_GAP、达标的不带。改 PREREQ_GAP_WEIGHT（ReviewPlannerV2.kt:877=2.0）或把 reason 分支反过来都会变红——历史上恒空的通道现在有真实断言。
- KnowledgePrerequisiteReaderTest 直接测生产读取器 KnowledgePrerequisiteReader 的 graphFor，覆盖按 subject 分区、256 分块、集合外前置节点回取等分支（core/data/src/test/kotlin/.../knowledge/KnowledgePrerequisiteReaderTest.kt:16-104），弥补了 FakeStudyDatabasePort 单测不经过真实读取路径的缺口。
- LibraryCatalogPagingInstrumentedTest 真播种 50_000 行并断言计数、末页 offset、检索命中数与 subjectFacets 求和（core/database/src/androidTest/.../LibraryCatalogPagingInstrumentedTest.kt:28-125）——即确实存在一个真实的大数据量功能门，只是它没有计时断言且被 PerformanceGateTest 的错误命名混淆。
- 备份恢复测试的校验覆盖扎实：截断/校验和不匹配/zip 炸弹压缩比/条目数超限/单条与总解压超限/预交换校验失败都各有独立负向用例（BackupRestoreInstrumentedTest.kt:244-412），并有两条真实的中断恢复断言（startupRecoveryRollsBackGenerationAfterInterruptedSwap:465、startupRecoveryCleansPreSwapInterruptionWithoutTouchingLiveData:529）。
- 逐版本迁移测试会播种真实旧版行并断言迁移后仍在（LibrarySearchMigrationInstrumentedTest.kt:52 断言 1 行存活；MasterySchedulingMigrationInstrumentedTest.kt:212 播种 v35 memory 行），部分弥补迁移矩阵断言过弱的问题。
- CI 单测/连接测试的模块枚举纪律本身是对的且有注释说明原因（android-check.yml:43-55 显式列出并注明「不列出就永不运行」）；仪器化任务列了 9 个 connected 任务并与实际 androidTest 目录一一对应（app/core:data/core:database/core:export/core:visual-ui/feature:capture/feature:tutor/feature:library），并用 --continue 让单个模块失败不阻断后续。
- generate_status.py 对无测量来源的值一律渲染 NOT_MEASURED 而非编造数字（避免假绿），文件体量门是按 base diff 的、只拦新增巨文件，release manifest 门会拒绝 path="."/"/" 的 FileProvider 根暴露（tools/ci/check_release_manifest.py）——这些工具本身的意图与实现一致。
- 覆盖率路径与生成器对齐且可解释：core/data、core/database 的 JacocoReport 输出 build/reports/coverage/unit-test.xml（core/data/build.gradle.kts:69、core/database/build.gradle.kts:61），generate_status.py 正读该路径；core:database 5.2% 的低值已由 KD-5 说明为「DAO default method 逻辑在仪器化测试里、单测覆盖本就低」，属于已披露的真实缺口而非测量伪影。


### 7.6　架构边界、依赖方向与体量（7 条）

- 模块依赖方向整体与 ARCHITECTURE.md / docs/current/architecture-contract.md 一致：逐模块念 build.gradle.kts 后，feature 之间零互相依赖，feature 只依赖 core:model/core:domain/core:ui（library 额外依赖 core:export、tutor 额外依赖 core:visual-runtime），没有任何 feature 依赖 core:data；只有 app 作为组合根依赖 core:data 与 core:database（architecture-contract.md 的 prohibited 列表只禁 feature→core:data，未被违反）。
- core:model 真正零 Android 依赖：grep `^import android|^import androidx` 在 core/model/src 下 0 命中，build.gradle.kts 仅 kotlin.jvm + kotlinx-serialization，是干净的纯 JVM 模型层。core:visual-runtime 同样 0 命中。
- core:database 的端口按关注点拆成 14 个文件（port/BackupPort、BatchImportPort、KnowledgePort、LibraryPort、TutorPort 等，合计 1223 行），StudyDatabasePort 只组合它们（12 个方法）——数据库边界是细分而非单一大接口，属良好分层。
- 前置知识判定有唯一权威且双侧共用：KnowledgeReadiness.READY_THRESHOLD（KnowledgeReadiness.kt:23）同时被排程侧 ReviewPlannerV2.kt:729 与会话侧 RoomBackedStudyExperienceRepository.kt:503 消费，KnowledgeReadiness.kt:18-22 明确写了「禁止同值两常量」的理由，是本工程反重复机制的正面样本（对比缺陷 mastery-threshold-two-authorities）。
- 此前审计指出的 ReviewPlanningRequest.knowledgePrerequisites 空通道确已修复：生产唯一构造点 StudyReviewPlannerService.kt:318 用 knowledgePrerequisites.graphFor(...) 赋值，消费方 ReviewPlannerV2.kt:46 使用；KnowledgePrerequisiteReader（core:data）按 KC 科目分区解析，非空转。
- 两个 PREREQUISITE_OF 枚举是语义不同且已文档化的两条关系：StudyDbValue.RelationType.PREREQUISITE_OF 是题→题（DatabaseContract.kt:57 校验、M1CuratedStudySeed.kt:77 使用），StudyDbValue.KnowledgeRelationType.PREREQUISITE_OF 是知识点→知识点（KnowledgeNodeRelationContract.kt:29、BundledKnowledgePackResources.kt:438），docs/specs/mastery-scheduling-spec.md:102 明确二者是两回事，不属重复机制。
- 存在真实的新增体量门（android-check.yml 的 File-size gate，硬线 main 1000/test 1500），配合 tools/ci/check_file_size_gate.py，至少能阻止新的千行文件进入；core:domain 与 core:data/database 还有 Kover/Jacoco 覆盖率报告接入 CI 状态报告。


### 7.7　持久化、迁移与并发状态（6 条）

- 复习会话推进是真正的 CAS + 单事务组合，无法构造双推进/丢更新：RoomStudyDatabase.kt:1065-1096 recordReviewAttempt 用 withWriteTransaction 把 attempt 写入与 advanceSession 包在同一事务；ReviewDao.kt:403 advanceSession 为 @Transaction，第469行 compareAndSetSessionState 以 WHERE state_version/current_ordinal/active_session_key/status 做 CAS；ReviewEntities.kt:209 active_session_key 唯一索引 + ReviewDao.kt:436 要求 activeLearnerSessions.size==1，共同强制同一 plan 只可能有一个 IN_PROGRESS 会话；重放由 attempt_id 作主键的 receipt 与 readAdvanceReplay（ReviewDao.kt:508-570）逐字段重算校验，幂等。
- 账本 outbox 的幂等键有唯一约束且写入前重放校验：projection_outbox 的 learner_id_outbox_sequence 与 event_kind_event_id 均为唯一索引（44.json）；attempt/correction/answer-reveal 在写入前先查 replay 并重算 canonical fingerprint 比对（AttemptTransactionDao.kt:556-593、610-641、523-554）；序列分配 allocateSequence 在事务内做 CAS（AttemptTransactionDao.kt:790-799）。
- 迁移链完整且逐版有结构校验，关键回填有数据断言：StudyDatabaseFactory.open 注册 1→44 全部 43 个 Migration 且无 fallbackToDestructiveMigration（StudyDatabase.kt:298-347）；FullMigrationMatrixInstrumentedTest.kt:16-24 对 1..44 每个导出 schema 建库再迁移到当前；MasterySchedulingMigrationInstrumentedTest.kt:48 对 difficulty 0..1→1..10 转换写真实种子行断言；CI 的 instrumented job 明确运行 :core:database:connectedDebugAndroidTest（.github/workflows/android-check.yml）。我按 schema JSON 逐版（1→44）比对列/索引/表增量与迁移 SQL 文本，未发现“加了列/表/索引而迁移漏了”或反向遗漏。
- 备份恢复的原子切换 + 启动回滚闭环：staged 库在 swap 前用生产 Room builder 跑迁移并用 quick_check/foreign_key_check 预验证（AndroidBackupRepository.kt:500-562）；swap 前后对文件和目录 fsync；journal 记录 phase 与各代具体路径，BackupRestoreStartupRecovery 在打开数据库之前执行 clean/rollback/quarantine（RestoreRecoveryCoordinator.kt，由 SmartMistakeBookApplication.kt:160 在 StudyDatabaseFactory.open 之前调用）；deleteAllData 覆盖数据库、题图、SharedPreferences、DataStore、缓存、密钥库与 Keystore 条目。
- chat evidence 写闸的三个精确计数查询都有 v43 新增的对应索引：learner_id_created_at_epoch_millis、conversation_id、learner_id_knowledge_node_id_created_at_epoch_millis 与 ChatEvidenceDao.kt:130-165 的 lastAcceptedAtForKc/countAcceptedSince/countAcceptedInConversation 的 WHERE 完全对齐，避免了全表扫描。
- 批量导入的中断回收幂等且被真实调用：BatchImportDao.kt:157/216 的 requeueInterruptedBoundaries/requeueInterruptedPages 把 CHECKING→FAILED、IMPORTING→QUEUED；RoomBatchImportRepository.kt:307 在 process 开始时、:217 在 organizeBatch 开始时各调用一次，回收路径不会重复执行已完成页（claim 用 status 守卫）。

---

## 8. 复核者发现的独立漏项（未经对抗复核，按未验证处理）

这些是复核 agent 在尝试推翻既有 finding 时**顺带发现、原审计没有提出的**问题。
它们没有经过任何第二方验证，**不得据此单独宣称功能失效或可用**。

### 8.1　事件溯源与投影完整性（3 条）

- generatePredictions 用**终局快照**给每条历史 attempt 造预测：LearningProjector.kt:1155 从 `snapshot.knowledgeMasteryStates` 取 mastery，而该 snapshot 是整段 replay 结束后的最终状态。也就是说每条历史事件的『预测』实际写的是它之后的终局掌握度（前视/回看偏差），若将来真接入标定会系统性失真。当前因 predictions 被丢弃而未爆——这是审计 finding 3 未揭示的隐患。
- projection_consumption.consumedAtEpochMillis 取的是 commit.snapshot.checkpoint.projectedAtEpochMillis（ProjectionTransactionDao.kt:761），对 FULL_REPLAY 而言该值是 replayAt（账本最大事件时间，无事件时为 0），并非真实消费墙钟时间。即便将来读这张表，其审计时间戳本身不可信（与 finding 5 同表，属独立缺陷）。
- drain() 达到 64 步上限时抛的是 ProjectionCasConflictException（StudyProjectionDrainer.kt:138），但该状态与 CAS 冲突无关（是账本积压/未追平）；调用方 runOperation 无法据此区分真实并发冲突与工作量上限，失败消息『did not drain within the bounded work limit』与异常类型自相矛盾，误导排障（finding 4 只讲触发，未指出异常类型本身错误）。

### 8.2　知识库联结与掌握度聚合（3 条）

- VisualInteractionIngestor.kt:161-171 记账时把 priorMemory 传成 null（其它通道 submitChoice/评级/自评都传 currentLearnerSnapshot 的 priorMemory）。ReviewLogSink.kt:58-77 用 priorMemory.lastReviewedAtEpochMillis 现算 deltaDays，null 直接落 0.0——于是所有视觉交互在 review_log 里的 delta_t 恒为“同日”，而 review_log 正是 FSRS 参数优化器的训练数据，会污染离线拟合。这是该维度证据聚合链上的真实漏项（虽更贴近 delta_t 口径，但发生在知识库证据入账处）。
- findings 1 与 4 的因果被拆成两条：真正让 finding 4 可达的是 finding 1 无条件创建的伪绑定——评级/自评路径为每题落一行 PSEUDO_FALLBACK 绑定，且其 attribution 使该绑定在组织期 deleteUnreferencedKnowledgeBindings（ProblemOrganizationDao.kt:277-291）中免于清理，从而长期留在 readPracticeUnitKnowledgeBindings 里、并把视觉证据从真 KC 引走。修复 finding 1 的”仅无绑定题才落伪归因”限定会同时关闭 finding 4 的主触发路径，单独修任一条都不完整。
  > **2026-09-12 订正，见 §3 S-9**：本段**最后一句说反了**。1.4 的限定**不会**关闭 finding 4 的**主**触发路径——
  > 主路径是「题先无绑定（此刻落伪绑定是**正确**的，1.4 按定义放行）→ 之后才被 organization 归类」，
  > 而 `VisualInteractionIngestor.kt:74-79` 取最早 accepted 的组，于是先落的伪绑定永久遮蔽后到的真绑定。
  > 1.4 关掉的只是支路径——「真绑定已在、但目录投影为空」时再补落一行伪绑定。
  > finding 4 仍需在 `VisualInteractionIngestor` 的**组选择处**修，已登记为批 1 第 **1.7** 项。
- 视觉通道的 taxonomy 组选择（VisualInteractionIngestor.kt:74-81）不只被伪绑定遮蔽：用户对已在库题做 USER_CORRECTED 重组织会生成更新的 taxonomy 组（RoomMistakeOrganizationRepository.kt:910-913 的 USER_CORRECTION_TAXONOMY_VERSION），但排序取最早 acceptedAt 的组意味着更正后的真 KC 仍收不到视觉证据。审计只写了伪绑定遮蔽，漏了“更正被忽略”这一同源可达路径。
  > **2026-09-12 一手核实（批 1 第 1.7 项）：本条成立，且是 §2 之外唯一一条与权威 spec 直接冲突的**
  > （因此从「未经二次验证」升为**已核实**）。逐条证据：
  > - 更正会**新写**一行绑定，不是改写旧行：绑定 id 由
  >   `sha256(practiceUnitId|knowledgeNodeId|problemRevisionId|taxonomyVersion)` 派生
  >   （`RoomMistakeOrganizationRepository.kt:1002`），而 `taxonomyVersion` 在非 `LOCAL_POLICY_ACCEPTED`
  >   时取 `"user-corrected-v1"`（`:921-925`）⇒ 新 id、`accepted_at` 更晚。
  > - 旧行**不会被清理**：`deleteUnreferencedKnowledgeBindings`（`ProblemOrganizationDao.kt:275-295`）的
  >   `NOT EXISTS(assessment_evidence_attribution …)` 正是把「被历史归因引用过的绑定」排除在删除之外，
  >   且 store 的注释写明「Evidence attributions are immutable historical facts. Retain bindings they reference」
  >   （`RoomProblemOrganizationStore.kt:92-94`）。**所以被复习过的题必然两行并存**——而没被复习过的题
  >   旧行被删、只剩新行、反而不受影响。**受害面恰好是「已经产生过证据的题」。**
  > - **权威 spec 站「最新」，代码站「最早」**：`docs/research/three-store-linkage-design.md:93`
  >   原文是「同 binding 多 revision … 取 **accepted_at 最新**的 taxonomy_version 组」；
  >   `docs/specs/mastery-scheduling-spec.md:203`（§2.13）也写「换绑迁移简化：**旧 KC 停止新证据**即可」。
  >   代码的 `pool.first()` 与这两处**方向相反**。

### 8.3　模型边界与结构化输出嵌合（3 条）

- ~~配图/去手写出网通道不遵守用户配置的 modelId~~ —— **本条已作废，见 S-8**。原判把
  `docs/image-pipeline-spec.md:41-49`「Models are split (per decision)」这一**已记录的设计**
  （对话用多模态模型、配图用 gpt-image-2）读成了缺陷，并据此提出一个会打断 OpenAI 配图的修复。
  真正剩下的问题（配图模型硬编码、第三方 provider 在规格内用不了配图）登记为 **N-11**，属产品决策。
- 缺少『真实网关 supportedTasks 覆盖契约表』的 parity 测试：全库无任何测试断言 OpenAiCompatibleModelGateway.toCapabilities().supportedTasks 覆盖 ModelTaskContractRegistry.all()（或 currentFor(kind)!=null 的 kind）。registry 测试(10-76)只自检 registry 自身。这正是 finding 1（IMAGE_PIPELINE_CLASSIFY）与 finding 7（LEARNING_SUMMARIZE）这类『手写 allowlist 漏 kind』能静默漏过的结构性根因。
- 配图通道重复出网：AttachedImageGenerator.resolveOne 每次都重新 generate+persist 新资产、从不复用已生成结果（AttachedImageGenerator.kt:46-57），AttachedImagesSection 以 LaunchedEffect(images) 触发（AttachedImageCard.kt:137-140）——每次进入组合（如滚动离开再回来）都会重发图片请求，放大 finding 2 的出网面。

## 9. 横切失效模式

56 条里 **11 条是同一种病**。逐条修是必要的，但如果只逐条修，下一批同形状的问题还会长出来——
这一节比任何单条 finding 都更能回答「离生产级还差什么」。

| # | 失效模式 | 实例数 | 代表性实例与根因 |
|---|---|---|---|
| **A** | **机制建成但无生产者／消费者**（死机制） | **11** | 修正账本无写入方 · 影子预测算完即丢 · 补救通道门在 debug fixture · 视觉权重算完不读 · `binding.strength` 无聚合消费 · 分类输出字段无人读 · 图像分类文本字段无人读 · 讲题难度理由无人读 · 复盘焦点标签无人读 · 契约注册表无生产调用方 · 自适应决策硬编码<br>**根因**：契约／schema／prompt 先行定义，但「定义 → 生产消费」之间没有门禁。本工程史上已**三次**出现「定义在、赋值不在」。 |
| **B** | **allowlist 与契约表双轨** | 2 | 手写 `supportedTasks` 与 `ModelTaskContractRegistry` 是两套、无 parity 测试 → 漏掉 `IMAGE_PIPELINE_CLASSIFY` 与 `LEARNING_SUMMARIZE`。是模式 A 在门禁层的具体出口。 |
| **C** | **同意／授权逐通道手写** | 3 | 文本通道查 `agentConsentGranted`（值来自用户开关）、图片通道只查**构建变体**开关（见 S-2）；工具 `confidence` 缺省恰好过 `0.7` 门；另有 2 处把 `agentConsentGranted` 硬编码 `true`。缺「任何字节出网必经单一收口点」的结构强制。 |
| **D** | **口径／常量双定义 ＋ 文档漂移** | 5（**＋ F-02，已修**） | 死视觉常量双份 · 两个 `PREREQUISITE_OF` · `binding.strength` 的 spec 与研究文档冲突 · 掌握度 `0.7/0.4` 双权威 · spec §2.3 公式符号与实现不符（实现对、文档错）。缺单一权威，代码与文档双轨演进且无漂移检测。<br>**F-02（§3，主循环新增，2026-09-13 已修）是本模式的极端形态**：不是双定义而是**三定义**（写入侧本地日历日 · 读侧墙钟整日地板 · 读侧未取整分数天），修法也印证了本模式的结论——**把口径收进单一定义（`ReviewCalendar`），双轨就不再存在**，而不是去逐个调用点对齐。 |
| **E** | **缺省值冒充真实信号，污染统计链** | 3（**其中 1 条已修**） | ~~视觉通道 `priorMemory=null` 使 `delta_t` 恒 0~~（**2026-09-13 已修**，见 §11 批 3 第 2 项；另发现同一形态的余留：同一次排空的第 2..n 行）· 工具 `confidence` 缺省 0.8 · 影子预测用终局快照造成前视偏差。**缺省语义 ≠ 真实语义**，喂进拟合链路前无有效性校验。 |
| **F** | **测试覆盖函数，不覆盖接线 seam** | 3（**＋ 1 种变体**） | drainer 版本触发无测试 · 标定消费无测试 · 缺 gateway↔registry parity 测试。测试围绕组件写，而非围绕「机制是否在生产接线处生效」写——**这是模式 A 之所以不能被及时发现的原因**。<br>**变体（2026-09-13 批 3 第 3 项带出）：测试覆盖了函数，却只覆盖它上面"两种口径恰好一致"的那些格子。** F-02 修复时 `core:domain` **407 条既有用例零变红**——因为既有夹具的间隔全是整日（或整 24 小时），墙钟地板、分数天、本地日历日差在那些格上**逐位相同**；同理 N-15 的 `LearningMasteryRecentActivityTest` 四条断言全落在整日格上。**判别格（跨午夜不足 24 小时）一格都没有**，所以同一张卡显示两个 `R` 这件事，覆盖率的数字再高也照不出来。修法：新增用例时**先问"这一格里几种可能的口径会不会给出不同的值"**，不一致才是有判别力的夹具。 |

**模式 A 是压倒性主导**（≥11 实例）。B 与 C 是它在门禁层的两个具体出口，E 与 F 是它**为什么长期没被发现**的原因：
缺省值让失效看起来像正常数据，组件级测试让失效看起来像已覆盖。

> **如果只允许改一件事**，改「新增机制必须带一条证明它在生产路径上被消费的测试」比修任何单条 finding 回报都高。

### 9.1 一处必须说清的表层一致性

维 01、03、06 都把「前置门已接通」列为 healthy 且都能核到行——但维 03 的伪归因表明**通道通了、输入被污染了**：
真 KC 拿不到证据，于是前置判定仍把真 KC 视为未知。维 06 的 healthy 因此只是维 03 缺陷的表层。
同理，维 01 把「优化器写回—调度读取链路非空转」列为 healthy，而视觉通道的 `delta_t` 恒 0 意味着
**该链路正跑在被污染的训练数据上**。（**2026-09-13 已修**：`delta_t` 现在取真实的"距上一次复习
几天"，含同一次排空内部的推进，见 §11 批 3 第 2 项。维 01 的 healthy 那句话因此到这一天才有资格成立。）

**这些不是两维互相矛盾，而是「表层 healthy 恰是另一维的 finding」。** 这个区别很重要：它决定了要不要当成缺陷改，
也决定了为什么「所有维度都通过」这种汇总方式会漏掉真问题。

---

## 10. 重构与收敛清单

不改线上行为，只改结构。排序按「收益 ÷ 成本」从高到低。凡涉及删除的，均先确认了没有引用点。

| 编号 | 重构项 | 理由与落点 |
|---|---|---|
| **R-01** | **拆分 11 个超线生产文件**，首推 `SecondaryScreens.kt` | 体量门只拦新增，存量被永久豁免。最重的 `SecondaryScreens.kt`（1810 行级）把备份／恢复／设置三个不相关屏幕堆在一个文件里——按屏拆分是这个文件天然的边界，不需要新建抽象。其余超线文件逐个判断「是否还能一眼看清职责」，能拆才拆，不为行数而拆。 |
| **R-02** | 把 `PagingSource` 移出 `core:domain` | 领域层是唯一一个 androidx import 的持有者。两个选项：接口下沉到 `core:ui`，或在领域层定义自有的分页契约、由 `core:data` 适配。修完 `core:domain` 才是真正的纯 JVM 层，「纯 JVM」这个声明才有机械保证。 |
| **R-03** | 掌握度分界收敛为单一权威 | 把 `0.7／0.4` 提到一处常量，让 `core/ui/MasteryBands.kt` 与 `LearningMasteryScreen.kt:398` 都引用它。参照 `KnowledgeReadiness.kt:18-22` 的写法——同一工程里已有正面样本，照抄即可。**✅ 2026-09-13 完成并验证**：`core/ui/MasteryBands.kt` 增 `MASTERY_STRONG_THRESHOLD = 0.7` / `MASTERY_FAIR_THRESHOLD = 0.4`，三个使用点（`masteryBandLabel`、`retentionBandLabel`、app 的 `forgettingRiskLabel`）全部改引它，`forgettingRiskLabel` 由 `private` 改 `internal` 以便跨模块断言。**新增 `MasteryBandConsistencyTest` 4 条**，判别格是**恰好等于切点**（`>=` 与 `>` 只在边界上不同）。**2 条变异**：M1 把风险档退回自己的 `0.75` 字面量（＝修复前的形状）→ **仅** `theStrongBoundaryAgreesBetweenMasteryAndForgettingRisk` 抓住（`expected:<低> but was:<中>`）；M2 把常量本身改成 `0.9` → **仅** `theCutPointsAreTheDocumentedOnes` 抓住。**M2 那条分工是刻意的**：边界用例的输入**取自常量**，所以常量一动、夹具跟着动、内部仍自洽——与 `FsrsSameDayBoundTest` 的 M2 同一种分工（关系用例守"两处一致"，数值用例守"常量是这两个数"）。**追补（同日晚）**：补第 5 条 `theTwoLabelsAgreeAtEveryScoreOnTheGrid`（扫 `[0,1]` 上 101 点，断言"掌握档说较稳 ⇒ 风险档必须说低"），因为它抓得住四个探测点抓不住的形状——**只在区间中部插一条分支**。追补时踩了一个坑，记在这里因为它极易被误当成结论：把风险档改回**与常量同值**的 `0.7/0.4` 字面量是什么都测不出来的**等价变异**，全绿并不说明用例有盲区；真正的一侧漂移（`0.75/0.45`）实测红三条 `{强档边界、中档边界、网格}`，边界用例是抓得住的 |
| **R-04** | 消除 `ForgettingCurve` 里重复的算法判定 | 同一个「用哪条曲线」的判定在同一文件里写了两遍（`:123-129` 与 `:140-146`）。抽成私有函数，顺带让 F-02 的时间口径切换只发生在一个地方。**2026-09-13 部分了结（随 F-02 一并处理，取"收口口径"那一半）**：R-04 的**目的**（口径切换只发生在一处）已达成——`t` 的算法现在只存在于 `ReviewCalendar`，两个 `when(algorithm)` 分支里只剩"调用它"；R-04 的**手段**（把 `when` 抽成私有函数）**没做**，理由是它现在是**编译器穷尽性检查**的 `when`，抽成策略对象／私有函数只是把同一份分派换个位置，**指认不出它消灭的具体失败**（§12.2）。详见 §11 批 3 第 3 项 |
| **R-05** | 删除三个死视觉常量 | `RoomBackedStudyExperienceRepository.kt:1027,1033-1035` 的 `private const` 与 `VisualInteractionIngestor` 重复且无读取（`KNOWLEDGE_QUIZ_CONVERSATION_ID` 同病）。直接删——留着会让人以为权重是从这里配的。**2026-09-13 复核：真实数量是 7 个，不是 3 个**（逐名在**本文件内**计数，`private` ⇒ 只可能被本文件读，计数为 1 即"只有声明"）：`KNOWLEDGE_QUIZ_CONVERSATION_ID`、`VISUAL_VIOLATED_WEIGHT`、`PRIMARY_VISUAL_ATTRIBUTION_WEIGHT`、`SECONDARY_VISUAL_ATTRIBUTION_WEIGHT_POOL`、`VISUAL_ASSESSMENT_ITEM_ID_PREFIX`、`VISUAL_ANSWER_SPEC_ID`、`VISUAL_ITEM_FAMILY_ID`。**这一整块是视觉入账搬去 `VisualInteractionIngestor` 时留下的旧居**——所以按"一块"删，不是按"三个"删。同一处还有**两条错的注释**：一条 KDoc（「知识点复习范围材料读取上限」）**下面没有常量**（它注释的常量已被删），另一条 KDoc 写着「Difficulty mid-point on the FSRS 1..10 domain」却挂在 `VISUAL_VIOLATED_WEIGHT = 0.5` 上（难度中点是 5.5，这条注释说的是别的东西）。**并到 R-06 的清理里一起做**。**✅ 常量那一半 2026-09-13 完成（`d18346e1`）**：按"一块"删掉七个零读者常量 ＋ 三条错的／孤儿注释（其中两条注释下面根本没有常量，一条把"难度中点"挂在`VISUAL_VIOLATED_WEIGHT = 0.5` 上）。证据是审计自己那套：这七个名字在**本文件内各只出现一次**（＝只有声明；`private` ⇒ 别处读不到），而 `VisualInteractionIngestor` 里各有一份**真的被读**的副本；编译通过 ＋ `core:data` JVM **415/0**，因此不重跑仪器化（本次不含可达路径变化）。**R-06 的其余死代码仍未做** |
| **R-06** | **死代码清理清单** | `LearningProjector.safeAdd`（未用私有函数）· `MemoryUpdateResult.shortTermReview`（恒 false 无读取）· `MasteryStatus.STALE`（从不赋值，连带 `status==STALE` 判断与视图里的 `'STALE'` 检查）· `HLRShadowModeManager`（无实例化）· `KnowledgeRetrievalBenchmark`（无调用）· `ReviewPlanner` V1 的 `plan()`（不可达）· `AdaptiveQuestionSelector`（或接线）· `projection_consumption` 表（只写不读且无上限增长）。**2026-09-13 全仓计数复核（`\b名字\b` 逐处）：** `safeAdd` **1**（只有定义）· `HLRShadowModeManager` **2**（定义 ＋ 一处 KDoc 引用）· `KnowledgeRetrievalBenchmark` **1**（只有定义）· `AdaptiveQuestionSelector` **2**（定义 ＋ 它自己的测试——**生产无构造**）· `shortTermReview` **6 处全在 `MemoryUpdateModel.kt` 内**（声明与写入，**无读取**）· `MasteryStatus.STALE` **13 处但从不被赋值**（比较点全是死分支——删它要连带删各 `when`/`==` 分支，是本清单里唯一有行为面的一项，**单独做**）。前四项可以一次删干净 |

> **2026-09-13 执行（`22c10988`）＋ 一条对自己的订正。**
> **已删四项**：`LearningProjector.safeAdd`（全仓 1 处＝只有定义）· `MemoryUpdateResult.shortTermReview`
> **字段**（声明＋两处写入、零读取；同名的**局部**变量在 `MemoryUpdateModel:170/172` 真被读，只删字段那一半——
> 顺带消掉一个误导：字段注释说"同日复习落到十分钟 legacy 节奏"，而写入处写死 `false`）·
> `HLRShadowModeManager` ＋ `HLREvaluationResult`（2 处：定义＋一处 KDoc 引用；删后修掉那条链接，
> 它用到的 `HLRFeatures`／`HalfLifeRegressionPredictor` 由 `HLRPredictionAuditService` 在用，不动）·
> `KnowledgeRetrievalBenchmark` ＋ `KnowledgeRetriever`（1 处；检索质量的机器门已在
> `tools/retrieval_quality_gate.py`，Kotlin 这份是重复能力）。回归：`:core:domain` **414 / 0**、`:core:data` **418 / 0**，
> 无测试被删。
>
> **⚠ 订正一条（本行原文说错了）**：清单里的 **`ReviewPlanner` V1 的 `plan()` 不是死代码**——
> `StudyReviewPlannerService:330-334` 是 `if (useReviewPlannerV2) V2 else reviewPlanner.plan(request)`，
> 而那个开关是**有文档的回滚闸门**（`RoomBackedStudyExperienceRepository:1079` 与 `:93-100` 的 KDoc：
> "flipping it back to false restores the audited V1 greedy planner **without any other code change**"）。
> 按 R-06 原文删掉它，等于**把回滚路径删掉**——清理清单里最不该照做的一条。
>
> **仍未做**：`MasteryStatus.STALE`（13 处但从不被赋值，要连带删各 `when`/`==` 分支——清单里唯一有行为面的一项）·
> `AdaptiveQuestionSelector`（有测试、生产无构造 ⇒ 接线或删除是**取舍**，与 R-07 同族）·
> `projection_consumption` 表（只写不读且无上限增长 ⇒ 要动 schema，与 R-09 那类一起判）。
| **R-07** | 四个校准接口：接线或删除，**二选一** | `evaluateSchedulingModels` · `sourceCalibrations` · `plannedReasonCalibrations` · `chatEvidenceGateCalibration` 无调用方，加上 `fsrsBeatsBaseline` 零读者。要么把标定回路接上（这是算法科学性的关键一环），要么明确下线并写进文档。**保持现状是最差选项**——它让「我们有标定」成为一句无法验证的话。**2026-09-13 一手核实并完成「证据」那一半**（`aecb8468` 之前的那个提交）：五个出口**在生产里零读者**（前四个只有 androidTest 替身在覆写；`fsrsBeatsBaseline` 是算得出来但没人据它决定，而它是 spec §2.20 的 go/no-go 门——**今天不会响**，唯一的 kill switch 是人手动的 `useFsrsScheduling`，与 status.md 那些恒为 NOT_MEASURED 的门同类）。处置：在每个声明处写明「尚未接线 ＋ 接线的最低成本落在哪」（呈现／导出报告、设置页的来源校准提示、门槛常量的复核清单、启动路径读一次 go/no-go 门），于是它不再能被读成「标定回路在跑」；**「接线还是下线」仍留给你拍板**——接线是产品功能、下线是能力回收，两者都不该由施工顺手定。 |
| **R-08** | 网关能力集与契约注册表收敛为单一权威 ＋ parity 测试 | 一次消灭模式 B，并防止将来再漏 kind。测试断言「注册表里 `currentFor(kind) != null` 的每个 kind 都被 `supportedTasks` 覆盖，或显式声明为不支持」。**2026-09-13：测试那一半已落地，单一权威那一半登记为 N-23。** 落地的形态与本条原文一致——`theConfiguredGatewayAdvertisesEveryKindItsProtocolCanServe` 逐条钉住宣告集、`everyTaskKindIsEitherAdvertisedOrExplicitlyOutOfScopeForThisGateway` 逼每个枚举值显式归类，本轮再补 `theImageGatedKindsMatchTheRegistrysAssetPolicy` 把"图片已验证才宣告"的那一组与注册表 `assetPolicy ≠ FORBIDDEN` 钉成同一件事。**四路审查建议的"把五条 `add(...)` 直接换成注册表派生"被否**：那会把"这个 kind 带附件"当成"这个网关服务它"，将来新注册一个本网关服务不了的带附件 kind 会被**静默宣告**（fail-open），而本条原文要的正是"或显式声明为不支持"这条 fail-closed 的路。**未做的**：让注册表在生产路径上真的被读（现在只有测试读它）——见 **N-23**，属产品级取舍 |
| **R-09** | `attempt_event` 三个错误类型列：接线或删列 | 迁移注释自称 placeholder。若近期无意采集，**删列比留着好**——留着的列会被后来者当成可用信号。**2026-09-13 一手核实并处置**：三列（`error_type`／`error_type_confidence`／`low_confidence_correct`）在 core/app/feature **既无写入方也无读取方**；处置取「说明白 ＋ 定触发条件」而非删列——**便宜的删法是陷阱**：`ALTER TABLE … DROP COLUMN` 要 SQLite ≥ 3.35，而本工程 `minSdk 23`（SQLite 3.8）会直接崩，**迁移矩阵用例跑在新设备上、抓不到**；只剩核心表重建一条路，为三列空占位不划算（`V10→14`／`V15→16` 有先例，真要做时照它来）。R-09 点名的失败是「被后来者当成可用信号」——那一半已消灭：字段注释从「populated from stage C onward」改成明确的「尚未接线 ＋ 删列触发条件」（见实体文件里那三个字段的注释）。零行为变化，`:core:database` JVM **67 / 0**。 |
| **R-10** | 两个 `PREREQUISITE_OF`：重命名，或给 `readForDependents` 加 `relation_type` 过滤 | **✅ 2026-09-13 完成（取"加过滤"）**：SQL 加 `AND relation_type = :relationType`、DAO 加形参、调用方传 `PREREQUISITE_OF`。**带仪器化用例**（绕过契约往表里写一行异类关系，要求查询看不见它）＋ 1 条变异还原三点、实测"两种类型同时出现在前置图里"。两条夹具记账（指纹须大写十六进制、每个导入节点都要有绑定）见 §11 批 2 执行状态 |
| **R-11** | 出网同意的两条路径归一 | 见批 1 的 S-2 修法：**不加新抽象**，改为「所有出网构造点必须经过同意门」的机械门（测试／契约），比引入 `EgressPermit` 一类抽象更符合「新增必先指认它消灭的具体失败」。 |
| **R-12** | 体量门加存量收敛机制 | 现在只拦新增。加一条「存量超线文件不得再增长」的检查——比一次性大重构温和，且能防止 R-01 做完之后重新长回去。**2026-09-13 做了没有取舍的那一半（`b79238cf`）**：告警里带上**移动量**（`+49 vs base (3096)` / `new file`），评审因此分得清"一直这么大"与"这个分支弄大了"；`--all` 模式顺带修好——它此前没定义 `base`，树里只要有超线文件就 `NameError`（全树 65 个，即必崩）。两个模式都实跑，错误路径用一次性探针实测（1103 行新文件 → `1 error` ＋ exit 1，探针提交已完全清除）。**硬门那一半没做，也不该由施工顺手定**："存量超线文件一个字节都不许涨"会连正当改动一起拦——本分支的批 2 第 3 项就往一个超线文件里加过必需的方法。这是策略取舍，留给用户决定。 |
| **R-13** | 文档与实现对齐 | spec §2.3 间隔反解公式符号（实现对、文档错）· spec §9 归因分摊公式（与实现分叉，见 S-3）· status 模板（声称「迁移矩阵通过／安全检查通过」而 `status.md` 标 `NOT_MEASURED`）· 研究文档 §9 的 `strength` 分摊公式。**这类漂移的危险在于它指导后来者把对的改错。** **2026-09-13：四项中三项已订正**——① spec §2.3 的指数（`4865b43e`，`−1/w20`；并已把这段订正从**主树**误落的改动搬回本分支）② spec §2.8 的归因分摊 ＋ 研究 §9 的 `strength`（`e87e429a`，含 ADR-0001「归因在写入时冻结」）③ 第 3 项（status 模板）**拆成两半**：报告"没有测量来源也给结论"的那一半已修（`6957f1d0`，另修 AAB 体积写错列），余下"PASS 该覆盖什么、恒空的行由谁产"是取舍，登记为 **N-31**——**作用域那一半已按建议做掉（`8e6337aa`：标题写明只覆盖本次真跑过的门）**，余下"恒空的行由谁产／FAIL 是否提交／要不要引第三个词"三条仍等拍板。 |

---

## 11. 修复批次

排序理由：先让历史重放这条路可信（批 0），再断安全与正确性的根因（批 1），然后恢复被误报「已运行」的功能链（批 2），
再做算法与数据质量（批 3），最后锁门禁与清理（批 4）。

### 批 0　重放可信化【决策 D-1，前置】

在动任何会改投影输出的修复**之前**完成。原定四条验收全做；施工中第 4 项被推翻并改落到
**第五项「投影失败的出口」**（D-12），四项验收的实质内容都在，因此按**五项**记录。

1. **触发测试**：把 `StudyProjectionDrainer.kt:75` 的 `requiresReplay` 判定改坏（比错字段／恒 false／误写 `==`）会让测试变红。
   （现状：全仓测试树对 `StudyProjectionDrainer` 与 `requiresReplay` **零命中**。）
2. **确定性测试**：同一版本下，「一次性全量重放」与「逐条增量投影」对同一批事件给出**逐字段相同**的结果。
   （这条是「重放结果可信」的证明，也是当前架构缺的那条强不变量。）
3. **上界与失败可见**：`commitFullReplay` 加条数上界与分块；超限时明确失败并给出可恢复路径；
   不再复用语义错误的 `ProjectionCasConflictException`（见 §8 漏项 3）。
   > **2026-09-12 更正**：「**分块**」这半条不成立，已作废——见 §12.4 与 ADR-0002。
   > 「给可恢复路径」这半条也改了归属：它不是一个可以随手上界的选项，而是 ADR-0003 的重放地平线，
   > 实现排在批 0 之后。批 0 里剩下的是**上界本身**：到点显式失败、保留旧检查点、用属于自己的异常。
   > 另外，同一族的 §8 漏项 3 之外还有一个**新的**误用（N-06：排空步数上界也复用同一个异常），见 §12.5。
4. **重放后不变量校验**：`S` 有限且 `>0`、`mastery ∈ [0,1]`、`nextReview` 不回退、账本无 GAP；
   任一不过则**保留旧 checkpoint**，不提交。
   > **2026-09-12 更正（动手前发现）**：这一项的原方案**被推翻**。四条不变量**全已在计算点守住**
   > （`require` / `coerceIn` / `coerceAtLeast(1)` / `replay` 自己的 `require`），事后校验器在现有代码上
   > **没有可守的失败**，按 §12.2 属于多余机制。该做的事反过来——不校验结果，而是**给失败一个名字**，
   > 即 D-12，落成第五项。详见下「第 4 项」与 §13.2 第三条。

> **本批状态：五项全部落地并验证（2026-09-12）**——第 4 项的原方案被推翻后改落到第 5 项（D-12）。
> 每项都有变异校验（共 24 条，逐条点名命中的断言）；第 3 项的「分块」半条作废（§12.4／ADR-0002）。
> 明细见下面的执行状态，回归数字见 §13.1。

#### 批 0 执行状态（2026-09-12）

分支 `audit/kernel-readiness`，worktree `D:/smart mistake book/.worktrees/kernel-readiness`，基线 `dc12065`（决策 D-3：从已提交 HEAD 开）。**改动尚未提交。**

| 验收项 | 状态 | 证据 |
|---|---|---|
| 1 触发测试 | **已完成并验证** | 见下「第 1 项」 |
| 2 确定性测试 | **已完成并验证** | 见下「第 2 项」；含 6 条精确命中的变异 |
| 3 上界与失败可见 | **已完成并验证** | 见下「第 3 项」；4 条变异命中、1 条无法构造（语言层保证） |
| 4 重放后不变量校验 | **原方案被推翻**，并入第 5 项 | 四条不变量全已在计算点守住，事后校验器无可守的失败（§12.2）——见下「第 4 项」 |
| 5 投影失败的出口（D-12） | **已完成并验证** | 见下「第 5 项」；10 条变异逐条命中，含把原缺陷改回去的那条 |

**批 0 收尾状态**：第 1～5 项全部落地。验收项 4 的**原方案**不成立，D-12 已批准的重构方向（「让重放路径自己的失败落到一个有名字的出口」）由第 5 项承担，因此四项验收的实质内容都在，只是第 4 项换了下落点。

**本批产出的文件**（worktree `audit/kernel-readiness`，均未提交）

| 文件 | 性质 |
|---|---|
| `core/data/.../study/StudyProjectionDrainer.kt` | 产线：构造参数收窄为 `LearningProjectionPort`（第 1 项，行为不变）；全量重放上界 + `MAX_FULL_REPLAY_EVENTS`（第 3 项） |
| `core/database/.../StudyDatabasePort.kt` | 产线：新增 `ProjectionReplayLimitExceededException`（第 3 项，D-13） |
| `app/.../StartupFailureMessages.kt` | 产线：新增。启动期失败的文案与编号前缀（第 5 项，N-02／N-04） |
| `app/.../StartupInitialization.kt` | 产线：新增。两段初始化的**归属**（第 5 项，N-02） |
| `app/.../StartupState.kt` | 产线：`RecoverableFailure` 新增 `retryable`（第 5 项，§12.1 影响面闭合） |
| `app/.../SmartMistakeBookApplication.kt` | 产线：启动期后台初始化改为经 `runStartupInitialization`；删掉一处空操作赋值（第 5 项） |
| `core/data/src/test/.../study/LedgerProjectionFixture.kt` | 新增：端口替身 + 事件夹具，三个测试文件共用 |
| `core/data/src/test/.../study/StudyProjectionDrainerReplayTriggerTest.kt` | 新增：5 条触发判定用例 |
| `core/data/src/test/.../study/StudyProjectionDrainerEquivalenceTest.kt` | 新增：5 条等价性／升级／封顶／上界用例 |
| `core/domain/src/test/.../LearningProjectorTest.kt` | 修改：等价性用例强化为整份快照 + 呈现状态表 |
| `app/src/test/.../StartupFailureMessagesTest.kt` | 新增：6 条文案／编号／重试可达性用例 |
| `app/src/test/.../StartupInitializationTest.kt` | 新增：5 条归属用例（含把原缺陷改回去的那条） |
| `docs/adr/0001`、`0002`、`0003`、`CONTEXT.md`、`docs/plans/2026-09-12-batch-0-replay-trustworthiness.md` | 新增：决策记录、词汇与施工契约 |

**第 1 项的做法与证据。**

为了让这条判定可测，先把 `StudyProjectionDrainer` 的构造参数从 `StudyDatabasePort` 收窄为
`LearningProjectionPort`——drain 只用其中四个方法（`readCurrentLearnerSnapshot` /
`loadProjectionBatch` / `loadLearningLedger` / `commitProjection`），收窄后替身只需实现这四个，
而不必为另外 28 个端口写桩。影响面已闭合：唯一调用点
`RoomBackedStudyExperienceRepository.kt:162` 传的是 `StudyDatabasePort`，是其子类型，调用点无需改动；
`StudyDatabasePort` 仍继承 `LearningProjectionPort`。**这是本次唯一的产线改动，行为不变。**

新增 `core/data/src/test/.../study/StudyProjectionDrainerReplayTriggerTest.kt`（5 条用例，
替身镜像 `ProjectionTransactionDao.loadProjectionBatch` 的分页语义，因此 drain 的循环真的会收敛）：

| 用例 | 钉住的性质 |
|---|---|
| `projectorVersionMismatchReplaysEvenWhenNewEventsExist` | 版本不匹配必须重放，**且优先于增量**（即使有待投影事件） |
| `staleFreshnessWithNoNewEventsReplays` | 陈旧快照即使无新事件也必须重放 |
| `nonCurrentProjectionStatusWithNoNewEventsReplays` | 非 CURRENT 状态即使无新事件也必须重放 |
| `caughtUpCurrentSnapshotIsReturnedUntouched` | 已追平且版本一致时**零提交**、原样返回 |
| `newEventsOnACurrentProjectionCommitIncrementally` | 版本一致且有新事件时走增量，不得借道全量重放 |

**变异验证**（逐条改坏产线判定，跑同一套用例）：

| 变异 | 结果 |
|---|---|
| `requiresReplay` 恒 `false` | **红**，3 条失败 |
| 版本比较 `!=` → `==` | **红**，5 条全失败 |
| 比错字段（版本比较换成 `lastSequence != ledgerHeadSequence`） | **红**，1 条失败——恰好是唯一「版本一致且有待投影事件」的用例，正是该写法的判别点 |
| `batch.events.isEmpty() &&` → `\|\|` | **红**，5 条全失败 |
| 还原 | **绿**，5 条全过（`tests="5" failures="0" errors="0"`） |

验收项 1 因此不只是「有测试」，而是**被测变异证明过它真的在守那条判定**。

> **2026-09-12 复核（夹具重构之后）**：第 2 项把内嵌替身抽成了共用夹具，因此上表这轮证据
> 对**新文件**重新取了一遍——版本子句恒 `false` → 红 1 条（恰是版本不匹配那条）；
> 版本比较写成 `==` → 红 5 条；`&&` 写成 `||` → 红 5 条。鉴别力未被重构削弱。

**第 2 项的做法与证据。**

先修**夹具失真**，否则测不出真东西：原先内嵌的端口替身自造
`"fingerprint:${id}"`，而真实 DAO 写的是 `LearningLedgerFingerprint.event(...)`
（`AttemptTransactionDao.kt:350`、`ChatEvidenceDao.kt:77`）。指纹不一致会被投影器判成冲突，
于是"两条路是否等价"这个问题的答案会被夹具本身污染。抽出 `LedgerProjectionFixture.kt` 时一并修正：
指纹走同一个规范化函数、呈现状态表被真的维护（读取时按 `PresentationProjectionStateEntity.toModel(checkpoint)`
的口径把 `asOfLedgerSequence` 归到当前检查点，缺失给默认态）、修正行按 DAO 的语义返回
`FULL_REPLAY_REQUIRED`、页满给 `LIMIT_REACHED`。替身偏离生产语义的每一处都在它的 KDoc 里点名。

三条用例（`StudyProjectionDrainerEquivalenceTest`）：

| 用例 | 钉住的性质 |
|---|---|
| `fullReplayEqualsStepByStepIncrementalAccumulation` | 12 条事件的账本（走满四种事件、两条相反的曝光因果顺序）在四条路上——版本不匹配全量重放、每批 1 条、每批 7 条、每批 100 条——落到**逐字段相同**的 `LearnerSnapshot` |
| `replayedAndIncrementalAgreeOnTheDerivedCheckpointFields` | 两条路由**不同代码**算出的 `checkpoint` / `knownLedgerHeadSequence` / 时间线 / 修正字段必须一致；点明这里**没有墙钟**（时间线是事件时间戳的最大值） |
| `aCorrectionInThePageEscalatesToFullReplay` | D-9：页里出现修正 ⇒ 全量重放，绝不增量；修正**追溯生效**（被修正的作答改成负向证据、`appliedCorrectionRecords` 落库） |
| `theAppliedRecordCapRetainsTheSameWindowUnderAnyBatchSize` | D-11：4_097 条事件下，一次排空与切 41 批保留**同一个** 4_096 条窗口，且被丢掉的是最旧那条 |

**domain 层那一半**（D-10）：`LearningProjectorTest` 里原名
`full replay produces the same chat-evidence clock as incremental projection` 的用例
只比了 `knowledgeMasteryStates`——它的名字与注释声称的是"两条路等价"，断言却只覆盖一个字段，
于是 `problemMemoryStates`、`checkpoint`、`knownLedgerHeadSequence`、`generatedAtEpochMillis`、
四张记录表以及 `presentationProjectionStates` 上的任何分叉都逃得过去。已替换为
`full replay and incremental projection agree on the whole snapshot`：整份快照 + 呈现状态表 +
五个被点名的派生字段。呈现状态表那一项尤其重要——它是提交时 CAS 校验的对象
（`ProjectionTransactionDao.kt:964`），两条路一旦不一致，升级后的**第一次增量提交就会被判 CAS 冲突**。

**变异验证**（每条断言都必须能在它保护的生产逻辑被拿掉时变红）：

| 变异 | 结果 |
|---|---|
| `replay` 忽略曝光因果（`answerRevealSequence` 恒 `null`） | **红**，**只**由等价性用例捕获 |
| `replay` 不施加修正（拿掉 `event.copy(...)`） | **红**，只由修正升级用例捕获 |
| 记录表封顶保留最旧而非最新 | **红**，只由封顶用例捕获 |
| 上界常量差一（`4_096` → `4_095`） | **红**，只由封顶用例捕获 |
| `replay` 把新鲜度写成 `STALE` | **红**，**只**由强化后的 domain 用例捕获 |
| `replay` 丢掉讲题曝光记录表 | **红**，**只**由强化后的 domain 用例捕获 |

最后一节是本项最有价值的一点：**六条变异全都只打红它们各自守护的那条断言**，
其中两条只由强化后的 domain 用例捕获——这正是 D-10「声称的与断言的要一致」的可验证形式。
（第一版试的两个变异把整个 domain 测试类打红，说明它盖过了要证明的东西，因此换掉了；
这条经验值得记：**变异必须精确到"只有这条断言能抓"才算证据**。）

**回归证据**（产线改动「行为不变」的证明）：`:core:data:testDebugUnitTest` 全量 ——
**50 个测试类、394 条用例、0 跳过、0 失败、0 错误**；`:core:domain:test` 全量 ——
**58 个测试类、395 条用例、0 跳过、0 失败、0 错误**（均 `BUILD SUCCESSFUL`）。
另有 `:core:data:compileDebugKotlin` 编译通过（无新增告警）。

**未验证项**：`:core:data:lintDebug` 未跑；本项的仪器化测试在写下这段时未跑（见下）。
> **2026-09-13 收尾更新（一条从 UNVERIFIED 变成已验证，一条仍然未验证）：**
> 那条"特别记一条待补"**其实已经有测试了，只是当时没跑**——
> `StudyDatabaseInstrumentedTest.kt:333-344` 就是它：先 `appendAttemptCorrection` 两次
> （断言第二次 `created == false` 且返回同一 `correction`），再 `loadProjectionBatch`，
> 断言 `stopReason == FULL_REPLAY_REQUIRED`、`events` 恰为**修正之前**的那一条、
> `blockedAtSequence == 修正事件的 sequence`。本轮把整个
> **`:core:database:connectedDebugAndroidTest` 跑完：150 / 0 失败**——
> 所以「DAO 兑现了承诺」这半条**现在是真机验证过的**，第 2 项与它合起来才是完整结论。
> **仍然未验证的是 lint**（`:core:data:lintDebug` 本轮仍未跑），以及主树/worktree 的 CI 本轮未触发。
> 教训与 §12.1 同源：**"只能在那里验证"当时被写成了"没人验证"**——写下 UNVERIFIED 时
> 应当顺手确认"那里到底有没有这条用例"，否则一个已经写好的测试会被当成缺口记上两个月。

**第 3 项的做法与证据。**

全量重放的上界（决策 D-13／D-14）。

- **检查点的位置**：`loadLearningLedger` 之后、`replay` **之前**。`replay` 一进去就把整份账本持在
  内存里，之后没有提前失败的机会。
- **常量是顶层 `internal const val MAX_FULL_REPLAY_EVENTS = 100_000`**，不是 companion 私有成员：
  取值口径必须能被测试钉住，而仓里已有同形先例（`StudyDatabasePort.kt:32-33` 的两个容量常量）。
- **构造器多了 `maxFullReplayEvents: Int = MAX_FULL_REPLAY_EVENTS`。** 它消灭的具体失败：上界的
  **边界行为**（超限即失败、旧检查点原样保留）不开口子就要造十万条事件的夹具才测得到，
  于是这条上界会带着未验证的边界行为上线。生产永远走默认值。
- **新异常不继承 `ProjectionCasConflictException`**，因此 `drain` 的重试分支不会接住它。

| 变异 | 结果 |
|---|---|
| 上界检查整个删掉 | **红**，只由上界用例捕获 |
| 上界差一（`>` → `>=`） | **红**，只由上界用例捕获 |
| 产线上界降到同族的 `4_096` | **红**，只由取值口径用例捕获 |
| 产线上界抬到 `100_000_000`（OOM 之后才触发） | **红**，只由取值口径用例捕获 |
| 让新异常继承 `ProjectionCasConflictException` | **无法构造**：`This type is final, so it cannot be extended.` |

最后一行是本次最值得记的结果：**D-13 的「不继承 CAS」目前由语言本身保证**——想继承它根本编译不过。
因此那条 `assertFalse(isAssignableFrom(...))` 不是因为"现在安全"才多余，它守的是**将来**：
N-06 要改的正是同一处类型层级，谁把它做成 `open`，这行会立刻红。

---

**第 4 项：原方案被推翻——四条不变量全已在计算点守住。**

动手前逐条回读源码，结论是**清单上的每一条都已经是投影器的现成保证**，一个"重放后校验器"在现有代码上
没有可守的失败。按 §12.2，那就是多余机制，不实现。

| 原清单 | 一手核对结果 |
|---|---|
| `S` 有限且 `> 0` | `ForgettingCurve.kt:78-80` 的 `require(stabilityDays.isFinite() && stabilityDays > 0.0)` 在**每一次**用 S 算间隔时把关；`projectMemory` / `projectAnswerReveal` / `projectTutorAnswerExposure` 三条路都必经它。生产里不可能出现非正 S 的状态。 |
| 掌握度 `∈ [0,1]` | `LearningProjector.kt:588`、`:971` 更新后 `.coerceIn(0.0, 1.0)`；`:1099` 下界 `.coerceIn(0.0, probability)`。同样是计算点保证——而且 `coerceIn` 是**静默**的，事后校验连看都看不到（值已经被夹过了）。 |
| `nextReview` 不回退 | 对 FSRS 主干：`MemoryUpdateModel.kt:101-102` 先 `coerceAtLeast(1)` 天再加到 `effectiveAttemptAtEpochMillis`；毕业分支与曝光分支走 `reviewAtTargetRetention`（上表第一条已保证 S>0）。**每条路都至少 +1 天**，所以"回退"不可达。另外这条**对 `previous` 比较本身不成立**：版本升级或修正都会合法地把 `nextReview` 往前挪（那正是修复的目的），拿旧快照当基准会误报。 |
| 账本无 GAP | `replay` 自己 `require`「序列必须是 1..n 的连续唯一序列」（`:388`、`:391`、`:411`），而入口那侧 `commitFullReplay` 已经在 `status != COMPLETE` 时抛 `LearningLedgerIntegrityException`。 |

上面这张表同时暴露了**真正的问题**，而且它不在"重放之后"，而在"重放之前"：投影器的三条 `require`
由 DAO 的写入侧保证（作答 id 幂等、呈现响应序号 CAS、一次终结性曝光），正常数据永远走不到；
可一旦落到（部分恢复、导入了别的库、手工改过的库——本审计已登记过 `restore-failure-closes-db-no-reopen`
与 `cleardata-fk-off-leak` 两条同类缺陷），抛出的是一个裸 `IllegalArgumentException`，全仓无人指名捕获。
这正是 **N-04 的同一族**，处置属于第 5 项，不属于一个输出校验器。

**因此第 4 项没有产出代码，改为「让重放路径自己的失败落到一个有名字的出口」并入第 5 项（D-12）。**
这不是"没做完"，是 §12.2 那道门在起作用：按原样做就是错的。

顺带带出 **N-07**（`replay` 在内存里造一份没人读的预测列表，见 §12.5）。

---

**第 5 项的做法与证据（D-12：N-02／N-04／N-05）。**

三个出口都有毛病，因此先修出口——否则第 3、4 项抛出的异常只会落到一个**说错原因**的地方。

产物两个新文件，职责分开：

- `StartupFailureMessages.kt`——四类失败 → 文案 + 诊断编号前缀（`startup:knowledge:` /
  `startup:ledger:` / `startup:replay-limit:` / `startup:projection:`）。
  **（2026-09-12 批 1 N-06 订正：现在是**五**类——新增 `startup:drain-budget:`，
  见 §11「N-06」；本节其余内容描述的是批 0 当时的四类。）**
- `StartupInitialization.kt`——两段初始化的**归属**：哪一段失败，就说哪一段的话。

**为什么归属要单独抽成一个函数。** 文案断言钉不住归属：把两个 catch 合回去（也就是**原缺陷本身**）
时，文案一个字都没变，只写文案断言的测试会**全绿**。归属正是 N-02 的实质，所以它必须自己可测。
`runStartupInitialization` 接收两个 `suspend` lambda 与一个 `logFailure`，返回要发布的失败态。

**唯一的刻意行为改动（必须记在这里）。** HEAD 上两句共用一个 `try`，`install()` 一抛就永远走不到
`initialize()`——**知识包安装失败会连带冻住学习进度**，而界面只说「自动分类会暂缓」，把严重性说反了。
这正是 N-02 要消除的假耦合。现在知识包失败后仍继续尝试投影；代价是两段都失败时只有投影那条会显示
（横幅一次只显示一条）。行为清单写在 `StartupInitialization` 的 KDoc 里，并由
`aKnowledgeInstallFailureSaysSoAndDoesNotStopProjection` 钉住。

**连带发现 **N-10**（见 §12.5）**：`BundledKnowledgeBaseInstaller.installPack` 是**三个独立事务**
（`importKnowledgeBase` 是 `@Transaction`，`importKnowledgeNodeRelations` 与
`importKnowledgeTeachingMaterials` 不是），中途失败会留下半装状态，下次启动走 `else` 分支的
`require` 再抛一次 → 永久卡死。本次改动**降低了它的危害面**：知识包卡死不再冻住学习进度。

**影响面闭合（§12.1）。** 改这一处牵动的他处逐条核对：

| 被牵动 | 处置 |
|---|---|
| `StartupStateBanner` 按 `isRetryable` 决定是否渲染「重试」按钮 | **改了**：`RecoverableFailure` 新增 `retryable`（默认 `true` = 拆分前行为）。两类**确定性**失败（账本有缺口、超出重放上界）置 `false`——它们的输入在同一版本内不会变（账本只追加、从不裁剪），重试只会把同一条错误再显示一次，而文案正说「重试不会改变结果」 |
| `SmartMistakeBookRoot.kt:319` 读 `isRetryable` | 无需改动：语义不变，只是取值不再恒 `true` |
| `errorCategory` | **没有新增枚举值**。它全仓没有读取方（2 处写入、1 处默认、0 处读取），加值不会改变用户看到的东西；能让用户区分原因的是**编号前缀**（横幅会显示它）。登记为 **N-08** |
| `StartupState.kt` 里一处 `if (startupState.value is StartupState.Ready) { startupState.value = StartupState.Ready }` | **删掉**：`Ready` 是 `data object`，StateFlow 对相等值不发射，因此这行什么也没做。它读起来像个守卫，实际是空操作 |
| `modelAgentConsentStore` / 网关构造等 `onCreate` 其余部分 | 不在本项范围，未触碰 |

**变异验证（10 条，逐条点名命中的用例）**

| 变异 | 命中 |
|---|---|
| **W1 把两个 catch 合回去**（原缺陷本身） | **红**：`aProjectionFailureIsNeverBlamedOnTheKnowledgePack`、`bothFailuresAreLoggedSeparatelyAndProjectionIsWhatTheUserSees` |
| W2 取消被吞成失败态 | **红，只由** `cancellationIsNeverConvertedIntoAFailureState` |
| W3 知识包失败后跳过投影 | **红**：`aKnowledgeInstallFailureSaysSoAndDoesNotStopProjection`、`bothFailuresAreLoggedSeparately…` |
| W4 两段都失败时以知识包为准 | **红，只由** `bothFailuresAreLoggedSeparately…` |
| W5 两次失败塌成一条日志 | **红，只由** `bothFailuresAreLoggedSeparately…` |
| M1 投影文案重新加上「可以继续使用」 | **红，只由** `unknownProjectionFailureStillBlamesTheProjectionNotTheKnowledgePack` |
| M2 超出上界的文案不再说「重试不会改变结果」 | **红，只由** `replayLimitFailureDoesNotPromiseARetryWillHelp` |
| M3 账本与超出上界共用一个编号前缀 | **红**：`everyFailureClassCarriesItsOwnDiagnosticPrefix`、`ledgerIntegrityFailureNamesTheLedger…` |
| M4 账本损坏也提供重试 | **红，只由** `onlyTheUnknownProjectionFailureOffersARetry` |
| M5 `isRetryable` 忽略新字段（回到改动前行为） | **红，只由** `onlyTheUnknownProjectionFailureOffersARetry` |

> **用例改名（2026-09-12 批 1 N-06）**：上表 M4／M5 点名的
> `onlyTheUnknownProjectionFailureOffersARetry` 现名 `onlyFailuresThatCanActuallyChangeOfferARetry`。
> 改名的理由是这个旧名字在 N-06 之后**不再成立**——可重试的投影失败从一类变成两类
> （新增的排空预算那一条**可重试**，见 §11「N-06」）。**两条 `assertFalse` 与一条 `assertTrue` 逐字未动**，
> 只是加了第四条断言（排空预算可重试）并改了名字与 KDoc；故上表两行仍然有效。

W1 是这一项的核心证据：**只有归属断言能抓住原缺陷**。M5 是第二重要的：它证明 `retryable` 这个字段
真的被消费，而不只是一个没人读的装饰。基线（不打变异）全程绿。

**回归证据**

| 命令 | 结果 |
|---|---|
| `:app:testLocalFirstDebugUnitTest` | **7 个测试类、26 条用例、0 失败、0 错误** |
| `:app:testStrictOfflineDebugUnitTest` | **8 个测试类、27 条用例、0 失败、0 错误** |
| `:app:assembleLocalFirstDebug` | `BUILD SUCCESSFUL`，产出 `app/build/outputs/apk/localFirst/debug/app-localFirst-debug.apk` |
| `:core:domain:test` | **58 个测试类、395 条用例、0 失败、0 错误** |
| `:core:data:testDebugUnitTest` | **50 个测试类、396 条用例、0 失败、0 错误** |

**未验证项（本项）**

- **横幅的渲染本身没有任何自动化测试**：全仓对 `startup_state_banner` / `startup_retry_button`
  两个 testTag **零引用**，`StartupStateBanner` 也没有测试调用方。因此「`retryable = false`
  确实让按钮消失」只由 `isRetryable` 这一层断言支撑，**从状态到界面那一步是 `UNVERIFIED`**。
  seam 已经在了（testTag 现成）。
  > **2026-09-13 更新（理由被订正，结论不变）**：原写「本机不跑 `:app:connectedDebugAndroidTest`」——
  > 这一句现在是**假的**：该套件本轮跑过（两个变体各 40 条，0 失败）。
  > 但**这一条仍然是 UNVERIFIED，而且理由更清楚了**：跑得动之后我去那里找这条用例，
  > **那里没有**（`app/src/androidTest` 对这两个 testTag 仍然零命中）。
  > 所以缺口不是"跑不动"，而是"**没人写**"——这比"跑不动"更值得记，因为它不会因为
  > 环境变好而自动消失。同一条纪律见 §11 批 0 第 2 项那条更新。
- **知识包失败 + 投影成功这一组合未在真机实测**：它是本次新增的行为，只有单元测试的替身覆盖。
- 本机 Gradle 环境的一次性发现记录在 §13.4。

---

### 批 1　安全与正确性根因【低本，高承重】

| 项 | 内容 | 注意 |
|---|---|---|
| 1.1 | **图片通道同意门** | 按 **S-2**：把 `resolveImageCredential` 的输入从 `capabilities.networkRequestsAllowed` 换成用户的 `modelAgentConsentStore`；同时把 `RoomBatchImportRepository.kt:510` 与 `RoomCaptureWorkflowRepository.kt:416` 的 `agentConsentGranted = true` 改成真值 |
| 1.2 | ~~**配图通道遵守用户配置的 `modelId`**~~ | **已作废（S-8）**：§8 漏项 7 把「已记录的模型拆分设计」读成了缺陷，且它的修复方案（把对话模型 id 传给 `/v1/images/edits`）会**打断当前能用的 OpenAI 配置**。真正的缺口是「配图模型硬编码为 OpenAI 专用」，属产品决策 → **N-11**。**本项不实现** |
| 1.3 | **`ChatEvidenceDao` 幂等修复** | 按 **S-1**：**先按 `evidence_id` 查已存在行，存在则 no-op，不分配序列号**。**不要**只把 ABORT 改成 IGNORE。并补一条真实重复写入测试（现在全用不同 id，无法观察该失败） |
| 1.4 | **伪归因写入层限定** | 按 **D-2／S-3**：本题已存在 `sourceType != PSEUDO_FALLBACK` 的绑定时不落伪归因。**零版本升级**（原写「一行判定」，施工后改为一条判定＋一段理由注释）。判定放在唯一收口点 `RoomKnowledgeBaseStore.ensurePseudoKnowledgeBinding`，且**必须在伪知识点物化之后**——见下「第 1.4 项」 |
| 1.5 | **工具 `confidence` 缺省改为不达门** | `OpenAiModelResponseParsers.kt:494` 的缺省 `0.8` 恰好越过 `MasteryWriteGate.EVIDENCE_CONFIDENCE_THRESHOLD = 0.7`。缺省应当是「不达门」，不是「刚好达门」 |
| 1.6 | **把 S-3 的约束写进文档** | 「归因是写入时的不可变事实」+ 订正 spec §9 的分摊公式（R-13 的一部分） |
| **1.7** | **视觉组选择去伪遮蔽**（§5.3.6，`P2`） | 由 §3 **S-9** 带出：1.4 关不掉 finding 4 的**主**路径。让 `VisualInteractionIngestor` 选 taxonomy 组时优先非 `PSEUDO_FALLBACK` 的绑定；需要 `PracticeUnitKnowledgeBindingRecord` 带 `sourceType`（现在没有）。**已完成**，且施工中把第二半一并修了：组选择还要取**最新**一组而不是最早一组（§8.2 第三条） |

#### 批 1 执行状态（2026-09-12）

分支 `audit/kernel-readiness`，worktree `D:/smart mistake book/.worktrees/kernel-readiness`，基线 `dc12065`。**改动尚未提交。**

| 项 | 状态 | 证据 |
|---|---|---|
| 1.1 图片通道同意门 | **已实现并验证** | 见下「第 1.1 项」 |
| 1.2 ~~配图通道遵守用户配置的 `modelId`~~ | **已作废，不实现**（S-8） | 残留缺口登记为 **N-11** |
| 1.3 `ChatEvidenceDao` 幂等 | **已完成并验证** | 见下「第 1.3 项」；先红后绿，4 条变异逐条命中 |
| 1.4 伪归因写入层限定 | **已完成并验证** | 见下「第 1.4 项」；3 条变异逐条命中 |
| 1.5 工具 `confidence` 缺省 | **已完成并验证** | 见下「第 1.5 项」。原写「等并发会话落地后再定」的**前提已满足**（那 12 个提交已进 `main`，且 1.5 的三个目标文件与它无交集）。4 条变异按「命中集合相等」验证 |
| 1.6 把 S-3 的约束写进文档 | **已完成**（纯文档） | `docs/specs/mastery-scheduling-spec.md` 新增 **§2.8.1**「归因是**写入时**的不可变事实」＋ §2.8 加作废块；`docs/research/mastery-math-modeling.md` §9、`docs/research/three-store-linkage-design.md` §3.1/§3.2 各加订正块 |
| **1.7** 视觉组选择去伪遮蔽 | **已完成并验证** | 见下「第 1.7 项」；4 条变异按「命中集合相等」逐条验证。**顺带结清 §8.2 第三条**（更正被忽略） |
| **N-03**　可选卡片异常不得带走会话 | **已完成并验证** | 见下「N-03」；`loadOptionalSessionCard` ＋ 4 条 JVM 断言（含「取消必须继续上抛」） |
| **N-06**　排空步数上界不得报成 CAS 冲突 | **已完成并验证** | 见下「N-06」；新异常 ＋ 第五个文案分支 ＋ 可注入步数上界；5 条变异按「命中集合相等」验证（含一条必须先把 CAS 改成 `open` 才构造得出的） |
| **N-10**　知识包半装后必须能自愈 | **已完成并验证** | 见下「N-10」；判定条件由「一条都没有」改成「没装齐」＋去掉提前 `return`＋装完重读；真机仪器化用例 ＋ 2 条变异按「命中集合相等」验证 |

**本批的回归基线（2026-09-12 **中段**实测，含 1.3＋1.4＋1.1 的全部改动）**

| 套件 | 结果 |
|---|---|
| `:core:database:connectedDebugAndroidTest` | **149 / 0 失败**（30 个测试类） |
| `:core:data:connectedDebugAndroidTest` | **99 / 0 失败**（17 个测试类） |
| `:core:database:testDebugUnitTest` | 67 / 0 失败 |
| `:core:data:testDebugUnitTest` | 402 / 0 失败 |
| `:core:domain:test` | 395 / 0 失败（本轮 UP-TO-DATE，输入未变） |
| `:app:testLocalFirstDebugUnitTest` | 26 / 0 失败 |

> 本轮实测同时确认：**1.3 与 1.4 的新测试都在这一次运行里**——
> `ChatEvidenceLedgerIntegrationTest`（6 条，含 `retryingTheSameEvidenceIdIsANoOpThatDoesNotConsumeALedgerSequence`
> 与 `aBatchContainingAnAlreadyRecordedEvidenceOnlyAllocatesForTheNewOnes`）与
> `MasterySchedulingMigrationInstrumentedTest`（4 条，含 `pseudoBindingIsWrittenOnlyForQuestionsWithNoAcceptedBinding`）。
> 仪器化测试可跑这个前提本身也是本轮确立的（见 §12.1 与 §13.4）。

**本批收尾的冻结树基线（2026-09-12/13 实测，含 1.1／1.3／1.4／1.5／1.7／N-03／N-06 的全部改动）**

> **这两张表是中间态，已被 §13.1 的「冻结树最终基线」取代。** 保留它们的理由：它们记录了
> 当时**只跑了 8 个套件**这件事——而全树实际有 **16 个 JVM 套件与 9 个仪器化套件**。
> 清单式枚举漏掉的 8 个 JVM 套件里，装着的正是 `feature:capture`（99 条）、`feature:tutor`（89 条）、
> `feature:review`（33 条）这些**体量不小的**套件。见 §13.1 与 §13.4。

| 套件 | 结果 | 相对上表 |
|---|---|---|
| `:core:model:test` | **299 / 0 失败**（33 个类，`--rerun-tasks` 强制重跑） | 中段未记 |
| `:core:domain:test` | **395 / 0 失败**（58 个类，同上强制重跑） | 中段那次是 UP-TO-DATE，这次是真跑 |
| `:core:database:testDebugUnitTest` | **67 / 0 失败**（14 个类） | 不变 |
| `:core:data:testDebugUnitTest` | **409 / 0 失败**（51 个类） | ＋7 ＝ 1.7 三条 ＋ 1.5 三条 ＋ N-06 一条 |
| `:core:ui:testDebugUnitTest` | **11 / 0 失败**（3 个类） | 中段未记 |
| `:app:testLocalFirstDebugUnitTest` | **31 / 0 失败**（8 个类） | ＋5 ＝ N-03 四条 ＋ N-06 一条 |
| `:app:testStrictOfflineDebugUnitTest` | **32 / 0 失败**（9 个类） | 中段未记 |
| `:core:database:connectedDebugAndroidTest` | **149 / 0 失败** | 不变（同一次运行里跑完） |
| `:core:data:connectedDebugAndroidTest` | **99 / 0 失败** | 第一次跑在 54/99 处**进程崩溃**，`adb reboot` 后重跑全绿，见 §13.4。**此后 N-10 又加了 1 条用例（该类 2 条，单独跑 2/2 绿），故该套件现在是 100 条**；收尾的整轮重跑见本节末尾 |

> **上表取数时的树状态（必须说清楚，否则"冻结树"三个字是假的）**：九行里
> `:core:model`／`:core:domain`／`:core:database`／`:core:data`／`:core:ui`／两个 `:app`
> 与 `:core:database` 仪器化都是 **N-10 之前**的状态；`:core:data` 仪器化的 99/0 也是。
> **N-10 之后**只单独重跑了它的那个类（2/2）与全部 JVM 套件（`:core:data` 409、`:app` 31）。
> 收尾会在**所有源码改动都停下之后**再整轮重跑一次，届时把这两行换成终值。
> **2026-09-13 已执行**：终值见 §13.1「冻结树最终基线（2026-09-13）」——全树 16 个 JVM 套件
> 与 9 个仪器化套件，**2026 条、0 失败、6 跳过**（其中 6 个仪器化套件取自改动前那一轮，
> 该表已逐行注明）。

> 两条纪律记在这次取数上：**不带 `--tests` 过滤**（§13.4：同一条过滤在两次调用里表现不同，
> 成因未查明）；**跑仪器化之前先换一个干净的重启**（同一次里 `:core:database` 149 条先跑完、
> 之后 `:core:data` 才崩，说明设备是用着用着变坏的，不是一开始就坏）。

**第 1.1 项的做法与证据。** 按 **S-2**，`resolveImageCredential` 的第二个参数由
`networkRequestsAllowed: Boolean` 换成 `modelAgentConsentStore: ModelAgentConsentStore?`，
于是「读错开关」这个错误**在类型上不可表达**：布尔值再也传不进来。两条图形通道
（`AttachedImageGeneratorFactory` 的配图、`ConfiguredCleanImageGenerator` 的去手写）都走这一个闸门，
不可能对同一件事做出不同判断。离线变体不需要额外检查——`SmartMistakeBookApplication` 只在
`capabilities.networkRequestsAllowed` 为真时构造同意存储，`null` 已同时表示「该变体不许联网」与「没有同意渠道」，
**传 null 一律拒绝**。两处硬编码 `agentConsentGranted = true`
（`RoomBatchImportRepository.kt:509`、`RoomCaptureWorkflowRepository.kt:416`）改为传用户同意的真值。
新增 `ImageCredentialGateTest.kt`＋两个替身（`FakeModelAgentConsentStore`／`FakeModelConfigurationStore`），
含一条**Switch 型漏检**的断言（拿「存储存在与否」当同意必须被拒）。
> **未验证项**：那两处 `agentConsentGranted` 的新值只做了**源码级**核对——消费方是
> `OpenAiCompatibleModelGateway.kt:601` 的 `check(request.agentConsentGranted)`。没有仪器化测试覆盖
> 「同意关闭时该请求被网关拒绝」这条端到端路径。

**第 1.3 项的做法与证据。** 按 **S-1**，修法**不是**把 `insertOutboxRows` 的 `ABORT` 改成 `IGNORE`
（那会白白消耗一个 `learning_sequence` 而没有不可变事件行 → 永久 GAP → 读侧判定账本损坏）。
真正的修法是**在分配序列号之前**按 `evidence_id` 查已存在行：新增
`ChatEvidenceDao.existingEvidenceIds`（`RoomKnowledgeBaseStore` 所在的同一模块），
`insertAsLedgerEvents` 先算出 `fresh = accepted - existing`，为空即返回；`insertAll` 保持 `IGNORE` 作最后兜底，
`insertOutboxRows` 保持 `ABORT`（序列号与 outbox 行的连续性必须硬失败）。

- **先红后绿**：两条新测试写在未修代码上先跑 → **2 条红**，抛
  `SQLiteConstraintException: UNIQUE constraint failed: projection_outbox.event_kind, projection_outbox.event_id`
  于 `ChatEvidenceDao.insertAsLedgerEvents(ChatEvidenceDao.kt:83)`。可达性来自
  `RoomModelTaskRepository.kt:691` 的 `evidenceIdNamespace = requestId`——**重试**与**同一请求的多轮**都会复用同一个 `evidence_id`；
  而工具环把该异常吞成通用 `errorKind = "failed"`，于是模型被告知「写失败」，证据其实已经进账本。
- **变异**（4 条，逐条被点名的断言捕获）：M1 ＝ 恢复原方案（不查已存在行 ＋ outbox 改 `IGNORE`）→ 两条新测试；
  M2 ＝ 完全不查（＝ 未修的 HEAD）→ 两条新测试；M3 ＝ 查了但**仍然为重复项分配序列号** → 两条新测试；
  M4 ＝ 只对单条路径查、批量路径不查 → **仅**批量那条测试。M4 正是第二条测试存在的理由。
- **同族缺陷闭合**：全仓 5 个 `allocateSequence` 站点里另外 4 个（`AttemptTransactionDao` 三处、`TutorExposureDao` 一处）
  **本来就先查再分**。所以这不是「某个 DAO 有 bug」，而是「5 个分配站点里 1 个违反了另外 4 个都遵守的惯例」。

**第 1.4 项的做法与证据。**

判定**放在唯一收口点** `RoomKnowledgeBaseStore.ensurePseudoKnowledgeBinding`（`StudySubmissionPreparer.buildPseudoAttribution`
与 `StudyReviewPlannerService.createReviewPlan` 两条路径都经过它）：

```kotlin
if (organizationDao.readKnowledgeBindingsForPracticeUnit(practiceUnitId)
        .any { it.sourceType != PSEUDO_BINDING_SOURCE_TYPE }) {
    return null
}
```

配一条 `PSEUDO_BINDING_SOURCE_TYPE = "PSEUDO_FALLBACK"` 常量，写入端与判定端共用同一个，**不会漂移**。
`buildPseudoAttribution` 收到 `null` 即返回 `emptyList()`——**这条路径本来就写好了**（`?: return emptyList()`），
只是从来没有被触发过；所以这是**补上一个已定义守卫的缺失谓词**，不是新增机制（§12.2）。

**三个必须放在一起看的一手事实**（都是施工中读代码确立的，任一条读错都会把修复改错）：

1. **判定必须在伪知识点物化之后，不能提前。** `createReviewPlan` 对「目录投影为空」的题会把候选的 KC 回落成
   `"pseudo:${subject.uppercase()}"`（`StudyReviewPlannerService.kt:215-219`，末尾是 `?:` 兜底，**不是空集**），
   而 `review_queue_knowledge_node` 外键指向 `knowledge_node`、`DatabaseContract.kt:640` 又要求每个队列入队项
   **至少一个** KC。若把早退提到物化之前，「投影为空但有真绑定」的题就会让建计划抛 `SQLiteConstraintException`
   ——正是 2026-08-30 修过的那个 bug 的翻版。**实测钉住**：变异 M2 就是把这个早退提到物化之前，测试的
   `assertTrue("the refused call must still materialize pseudo:MATH", …)` 立刻变红。
2. **目录投影可以在绑定行存在时为空**，路径是**组织回执一致性**而非修订版本：
   `ProblemDao.kt:107-146` 只保留「没有回执，或该绑定等于最新回执且其 KNOWLEDGE 分类与同
   `taxonomy_version`／`accepted_at` 匹配」的绑定。用户做过一次纠正后，旧绑定就落在这之外。
3. **按修订号收窄判定是空操作**：`practice_unit_knowledge_binding` 外键指向
   `practice_unit(practice_unit_id, problem_revision_id)`（`ProblemEntities.kt:416-441`，父表该列对为唯一索引
   `ProblemEntities.kt:88`），因此 `basis_revision_id` **恒等于**该题当前的 revision。所以判定按 practiceUnit 收窄
   与按 revision 收窄等价——**不要**为此再加一层参数。

**「无归因的自评快照」是契约已经认可的状态，不是新语义。**
`DatabaseContract.kt:458` 的判据是 `attributions.isNotEmpty() || LocalReviewSelfReportContract.matches(snapshot)`，
而 `LocalReviewSelfReportContract.matches`（`LearningState.kt:83-91`）**要求** `attributions.isEmpty()`，
且两条自评路径构造的快照正好满足其余每个条件。也就是说：**「题已归类 ⇒ 自评证据不挂伪 KC」
这个表示法在模型层和 DB 契约层都已经定义好了**，缺的只是让它可达的那一个谓词。

- **测试**（`MasterySchedulingMigrationInstrumentedTest.pseudoBindingIsWrittenOnlyForQuestionsWithNoAcceptedBinding`，
  真机 Room、外键开启）：三个题**共用一套夹具**，只在「有没有已接受绑定」上不同，且各用一个**其他状态不碰**的科目，
  于是每道题的伪知识点只可能来自它自己那次调用。三个方向：
  ① 未归类（PHYSICS）→ 落伪绑定、节点物化；② 已归类（MATH）→ 拒绝、**节点仍物化**（这一段就是事实 1 的钉子）；
  ③ 已归类但带一行历史伪绑定（CHEMISTRY，**真实的迁移顺序**：计划先落伪绑定、分类后到）→ 拒绝且**不新增第三行**。
- **变异**（3 条，逐条被点名的断言捕获）：M1 ＝ 去掉判定（＝ 修复前）→ 方向② 的 `assertNull` 失败，
  报 `expected null, but was:<PracticeUnitKnowledgeBindingRecord(bindingId=pseudo-binding:unit-bound:…)>`；
  M2 ＝ 判定提到物化之前 → 方向② 的节点物化 `assertTrue` 失败；M3 ＝ `any` 改 `all` →
  方向③（多出一行）与方向①（空列表上 `all` 恒真，导致连未归类的题也拒绝）双双失败，
  后者还被**既有**的 `pseudoKnowledgeBindingSatisfiesAttributionForeignKeysOnDevice` 独立捕获
  ——即未归类方向本来就有测试守着。
- **范围订正**：本条**不**关闭 §5.3.6，理由与证据见 §3 **S-9**（新增第 1.7 项接手）。
- **施工教训**（已记 §13.4）：变异脚本第一版报「三条变异全部命中」，实际是**假阳性**——
  它把「结果目录不存在」当成「有红测试」。真实原因是脚本用 `cmd /c gradlew.bat` 起子进程，
  而本会话的 shell 带 `NoDefaultCurrentDirectoryInExePath=1`，`cmd` 根本找不到 `gradlew.bat`，
  三次都在编译前就退出。修成绝对路径＋「目录不存在即失败」后重跑，才拿到上表的真结果。

**第 1.7 项的做法与证据（§5.3.6，由 S-9 带出）。**

这一项落在 `VisualInteractionIngestor` 的**组选择**上，两条规则各有各的失败要消灭（§12.2），
且都写在同一个 pool 上：

| # | 规则 | 它消灭的具体失败 | 权威依据 |
|---|---|---|---|
| 1 | **已接受分类优先**：`bindings.filterNot { it.isPseudoFallback }`，为空才回落到含占位绑定的全量 | 先复习后归类的题（占位绑定 `accepted_at` 最早）把此后**全部**视觉证据引到 `pseudo:<SUBJECT>`，真 KC 一条都收不到（§5.3.6 的 finding 4，§3 S-9 证明 1.4 关不掉它） | `mastery-scheduling-spec.md` §3.4（伪 KC 是**未归类题**的回落） |
| 2 | **取最新一组**：`pool.last()`（pool 已按 `(acceptedAt, bindingId)` 升序） | 用户做过 `USER_CORRECTED` 重组织后，证据继续流向**被更正掉的那个** KC，而更正后的 KC 永远收不到（§8.2 第三条，本轮升为**已核实**） | `three-store-linkage-design.md:93`「取 **accepted_at 最新**的 taxonomy_version 组」＋ `mastery-scheduling-spec.md:203`「换绑迁移简化：**旧 KC 停止新证据**」 |

**配套的读取面改动**：`PracticeUnitKnowledgeBindingRecord` 新增**必填**（无默认值）`isPseudoFallback`，
由 `RoomStudyDatabase` 的映射从 `source_type` 推出（`row.sourceType == PSEUDO_BINDING_SOURCE_TYPE`），
占位绑定返回的那条记录置 `true`。**刻意不给默认值**——有默认值就等于让每个现有与将来的构造点
默默宣称「这是一条已接受分类」，正是本记录模式 E 那条「缺省值冒充真实信号」。全仓只有 4 个构造点
（2 个产线、2 个测试替身），每个都必须显式表态。

**为什么规则 2 属于本项而不另立条目**：它与规则 1 是**同一个选择语句的同一个 pool**，
拆开会让两条规则互相看不见（规则 2 的测试若不同时固定规则 1，就无法证明是 `last()` 在起作用）。
两者也共用一个反例家族：「最早的 accepted 行永久获胜」。

**测试**（三条，`RoomBackedStudyExperienceRepositoryTest`，JVM 单测；三条题各自只差一个变量）：

| 用例 | 夹具 | 钉住的性质 |
|---|---|---|
| `visualEvidenceFollowsTheAcceptedBindingWhenAnOlderPlaceholderBindingExists` | 占位绑定 `accepted_at=100` ＋ 真绑定 `=900` | 占位不得遮蔽真绑定（规则 1 主路径） |
| `visualEvidenceStaysOnThePlaceholderWhileItIsTheOnlyBinding` | **只有**占位绑定 | 未归类题的证据仍落在占位上——规则 1 的回落分支是承重的，不是装饰 |
| `visualEvidenceFollowsTheCorrectedBindingNotTheSupersededGroup` | 旧真绑定 `=800` ＋ 更正后真绑定 `=900` | 更正后证据改流到新组（规则 2） |

**变异**（4 条，**断言"被哪几条测试捕获"的集合相等**，不是"有没有变红"）：

| 变异 | 命中 | 失败消息（原文） |
|---|---|---|
| M1 两条规则全关（＝ 修复前） | 遮蔽 ＋ 更正两条 | `expected:<binding-[correct]ed> but was:<binding-[supersed]ed>`；`expected:<binding-[v]> but was:<binding-[pseudo]>` |
| M2 偏好取反（只留占位） | **仅**遮蔽那条 | `expected:<binding-[v]> but was:<binding-[pseudo]>` |
| M3 去掉 `ifEmpty` 回落 | **仅**只有占位那条 | `IllegalArgumentException: Required value was null.`（pool 为空 → 根本没写快照） |
| M4 `last()` 改回 `first()` | **仅**更正那条 | `expected:<binding-[correct]ed> but was:<binding-[supersed]ed>` |

> M4 单独只由「更正」那条捕获，M2 单独只由「遮蔽」那条捕获，M3 单独只由「只有占位」那条捕获——
> **三条测试各有一条只有它能抓的变异**，没有一条是多余的；M1 的两条命中则同时给出了修复前
> 两个失败路径的**原文对照**（证据 → 被更正掉的 KC、证据 → 伪 KC）。

**第 1.5 项的做法与证据（§5.4.10）。** 修法与三条测试见 §5.4.10 的订正块；
**变异 4 条**（同样断言「命中集合相等」）：

| 变异 | 命中 | 失败消息（原文） |
|---|---|---|
| M1 `TutorToolCall` 缺省改回 `0.8` | **仅** runner 那条 | `expected:<false> but was:<true>`（门放行了一条没人判过的证据） |
| M2 两个解析站点改回 `?: 0.8` | **仅** 两条解析用例 | `expected:<0.0> but was:<0.8>`（两处都还原到了原始缺口） |
| M3 常量取 `0.7`（**正好**压在门上） | 三条 | `缺省置信度必须低于证据门，实得 0.7` |
| M4 常量取 `0.75`（门上） | 三条 | `缺省置信度必须低于证据门，实得 0.75` |

> M1 与 M2 **互不重叠**：数据类缺省只管"构造时没给"的场景，两个解析站点只管"provider 没发"的场景，
> 所以测试也必须分成这两类——只写一条端到端用例，会在 M2 下变绿（解析补了 0.8 后，
> 调用方传的就是 0.8，而那条用例根本没经过解析）。M3 是边界变异（`0.7 < 0.7` 为假 ⇒ 放行），
> 它证明三条用例钉的是「**严格低于**门」，不是「不等于 0.8」。

**N-03 的做法与证据（两条可选卡片不得带走整个会话）。**

前提先回读一手源码核实（不是沿用 N-03 登记时的推断）：`reTeachOpening`（`RoomBackedStudyExperienceRepository.kt:474`）
与 `prerequisiteRemediation`（`:501`）都经 `currentLearnerSnapshot()`（`:996-1000`）调用
`projectionDrainer.drain()`，而**这两个方法都不走 `runOperation`**——`runOperation`（`:1002-1011`）
才装 `publishFailure` 兜底。所以 drain 的任何异常（含 N-06 新增的那条）会原样穿过它们，
进入 `SmartMistakeBookDestinations.kt` 的 `produceState` 块，而该块内没有 `try/catch`。
**崩溃这一后果仍是推断**（该作用域未装 `CoroutineExceptionHandler`），未在设备上复现；
本次修的是**可达的那一段**——异常确实会从这两个调用点逃出去。

修法是一条 `loadOptionalSessionCard`（`app/src/main/kotlin/com/tingyun/smartmistakebook/OptionalSessionCard.kt`），
只有 11 行，三个性质缺一不可：

| 性质 | 它消灭的具体失败 |
|---|---|
| 失败 → 返回 `null`（这张卡就是没有） | 会话被一张**可选**卡带走：§2.9 补救与 §2.16 重讲都不是复习能否继续的前提 |
| 失败**有记录**（`onFailure`，产线写 logcat） | 静默吞掉只是把模式 E 抬高一层——「缺省冒充真实信号」在这里表现为「读失败冒充没有这张卡」 |
| **取消照旧上抛** | 吞掉 `CancellationException` 会让协程在页面消失后继续跑；所以 catch 写成两段而不是一个 `catch (Throwable)` |

**四条 JVM 断言**（`app/src/test/.../OptionalSessionCardTest.kt`，实测 4/0）：
`aFailingCardDegradesToNoCardAndIsReported`（降级 ＋ 有记录）、`aLoadedCardPassesThrough`（正常路径不误报）、
`aMissingCardIsNotAFailure`（`null` 不是失败）、`cancellationStillPropagates`（取消传播且**不**走 `onFailure`）。

- **范围边界**：同一个 `produceState` 块里的第三个调用 `repository.teachingArtifact(it)`（`:107`）**没有**包裹。
  它是**正确的**——那条失败的含义不是「少一张卡」，而是「这道题读不出来」，把它降级成"没有卡"会让界面
  永远停在「正在读取题目…」（`artifactLoad.isLoaded` 永远是 `false`）。但它今天之所以不可达，是因为
  `teachingArtifact`（`:460-461`）读的是**夹具**；批 2 第 3 项正要把它换成 `knowledge_teaching_material`——
  那一步就是让它变可达的那一步，因此一并登记为 **N-12**（见 §12.5）。
- **没修的**：没有仪器化/端到端测试覆盖「复习页打开着、drain 抛异常」这条真实路径。

**N-06 的做法与证据（排空预算用尽要说自己的话，D-16）。**

三处改动，每一处都对应一个具体的失败（§12.2）：

| # | 改动 | 它消灭的具体失败 |
|---|---|---|
| 1 | `core:database` 新增 `ProjectionDrainBudgetExhaustedException` | **失败原因与事实相反**：原实现走完 64 步后抛 CAS 冲突，而那个检查点**在本次调用里根本没被别的写者动过**。拆成独立类型的实际价值有两处：用户看到的标题与编号不同、日志里不再出现一个不存在的并发现场 |
| 2 | `StudyProjectionDrainer` 的步数上界改成可注入（`maxDrainSteps = MAX_PROJECTION_DRAIN_STEPS`） | 不开口子就要造 **6_400 条**事件（64 页 × 100）的夹具才测得到「预算用尽时已推进的部分仍在库里」，这条失败出口会带着未验证的边界行为上线。**产线仍走默认值**，与 `maxFullReplayEvents` 同一个理由与同一种写法 |
| 3 | `projectionFailure` 新增**第五个**分支（`startup:drain-budget:`） | 把这条失败混进「原因未知的投影失败」，用户就失去了唯一能据以决定「要不要再点一次重试」的信息 |

**为什么这一条可重试，而它上面两条不可**——这是本项最容易写错的地方：

| 失败 | 输入是什么 | 重试会发生什么 | `retryable` |
|---|---|---|---|
| `ProjectionReplayLimitExceededException` | 账本**总条数** | 同一份账本得到同一个结果 | `false` |
| `LearningLedgerIntegrityException` | 账本**内容**（只追加、从不裁剪） | 同一条错误再显示一次 | `false` |
| **`ProjectionDrainBudgetExhaustedException`** | **这一次走了多少步** | **从已提交的检查点接着走** | **`true`** |

五类前缀互不相同（`knowledge` / `ledger` / `replay-limit` / `drain-budget` / `projection`），
这条不变量由 `everyFailureClassCarriesItsOwnDiagnosticPrefix` 钉住（清单已扩到五条）。

**变异（5 条，断言「命中集合相等」）**：

| 变异 | 命中 | 失败消息（原文，节选） |
|---|---|---|
| M1 抛回 CAS 冲突（= 修复前 HEAD） | **仅** drainer 那条 | `unexpected exception type thrown; expected:<…ProjectionDrainBudgetExhaustedException> but was:<…ProjectionCasConflictException>` |
| M2 忽略预算（`repeat(maxDrainSteps)` → `repeat(Int.MAX_VALUE)`） | **仅** drainer 那条 | `expected …ProjectionDrainBudgetExhaustedException to be thrown, but nothing was thrown` |
| M3 删掉 app 侧分支（落到通用文案） | `drainBudgetExhaustionSaysTheRetryIsTheWayForward`、`everyFailureClassCarriesItsOwnDiagnosticPrefix` | `expected:<5> but was:<4>`；「必须点名是积压没排完」 |
| M4 app 侧分支改成 `retryable = false` | **仅** `onlyFailuresThatCanActuallyChangeOfferARetry` | 「排空预算用尽与上面两条相反：已完成的推进已落库，重试就是通路（审计 N-06）」 |
| M5 新异常继承 CAS 冲突 | **仅** drainer 那条（`assertFalse(isAssignableFrom)`） | 「预算用尽不得复用 CAS 冲突的语义（N-06）……实际类型：…ProjectionDrainBudgetExhaustedException」 |

- **M4 单独只由那条新断言捕获**——其余四条变异都抓不到它。这就是「`retryable = true` 显式写出来」
  这条改动的全部意义：不写断言，把默认值改回去不会有人发现。
- **M5 是唯一需要改两个文件才构造得出的变异**：`ProjectionCasConflictException` 是 final，
  让新异常直接继承它**编译不过**（`This type is final, so it cannot be extended.`）——
  正是批 0 第 3 项记下的那条「目前由语言本身保证」。脚本先把 CAS 改成 `open` 才构造出这个变异，
  于是它证明那条 `assertFalse` 守的是**将来**，不是现在。
- 变异脚本每轮结束把三个源文件写回，并在 `finally` 里断言**逐字节相同**后再继续；脚本自身
  从**活源码里切出锚点**（不再手抄中文字符串），因此锚点失配会直接报「anchor matched 0 times」而不是静默跳过。

**N-10 的做法与证据（知识包半装必须能自愈）。**

三处改动，都在 `BundledKnowledgeBaseInstaller.installTeachingMaterials`：

| # | 改动 | 它消灭的具体失败 |
|---|---|---|
| 1 | 判定 `existingMaterials.isEmpty()` → `existingMaterials.size < expectedMaterials.size` | 「非空但不全」（2 000 / 10 356）不再落到 `require` 上抛「incomplete or conflicting」，改为重跑分批导入补齐——**半装不再永久卡死** |
| 2 | 去掉导入分支末尾的 `return` | 全新安装那一路**也开始校验自己刚写的东西**：原先「导入完就 return」与「校验」互斥，导入出问题没有任何断言会说话 |
| 3 | 导入后**重读** `existingMaterials`（改用 `var`） | `require` 原本比的是导入**之前**的读数，于是刚补齐的这一次会被自己判成「不完整」——把「卡死」改成「卡死得更隐蔽」 |

**第 3 处是施工中自己踩出来的，拦住它的是测试不是人眼。** 写完第 1、2 处之后回读控制流才发现
`require` 用的是同一份数据的**陈旧**读数；把那台真机用例先跑一次（**红**，报 `require` 失败）。
这正是「改一处要闭合影响面」在现场的样子：**只改判定条件、不动下游对同一份数据的第二次使用，等于没改。**
它同时说明为什么这条修复必须带**真机**用例——JVM 侧没有替身能证明"读回来的就是刚写进去的"。

**一条前置事实（本轮实测）**：6 个 sidecar 里**每个来源都至少被一条材料引用**
（逐份 python 核对：`sources − 被引用的 sources = ∅`）→ 所以去掉 `return` 之后那条更强的校验
对 bundled 包**恒成立**；否则它会把一次成功的全新安装变成失败。这条是先证后改，不是改完试试。

**测试**（`BundledKnowledgeBaseInstallerInstrumentedTest.aHalfInstalledTeachingSidecarIsHealedByTheNextInstall`，
真机 Room）：按真实顺序 `importKnowledgeBase` 装知识点，再**只导入第一个批次**（2 000 条）
——现场就是"第二个事务还没跑"。五条断言：①夹具确实是**半装**（恰好 2 000，而不是 0）；
②`install()` 之后补齐到 10 356 条；③**逐条比载荷**（数量对得上但内容换掉也算失败）；
④来自**最后一个批次**的那条存在（证伪"补到一半又停"）；⑤再装一次逐条相等（补齐动作自身幂等）。
**基线 2/2 通过**（该类共 2 条用例，另一条是既有的全科安装用例）。

**变异（2 条，真机运行，命中集合按实测记录）**：

| 变异 | 命中 | 说明 |
|---|---|---|
| M1 判定退回 `isEmpty()`（＝ 修复前） | **仅** 半装那条 | 半装用例是唯一能抓住它的；既有用例在 M1 下仍绿——因为重读还在，全新安装那一路照常通过 |
| M2 去掉导入后的重读 | 半装那条 **＋** `installsEverySupportedSubjectWithGroundedAtomsAndIsIdempotent` | 比预期多一条，而多出来的这条**是对的**：少了重读，全新安装那一路比的是空 map，连原本就有的幂等用例也一起红——重读对「全新安装」同样承重 |

> M2 的"多一条"没有当成噪声抹掉：先解释它（重读对两条路径都承重），再把解释写进脚本的
> `EXPECTED` 与它的注释里。**把观察到的集合改成与自己预期相符，才是这一节要防的错。**

### 批 2　接线补全【中本，高承重】

- **网关能力集补两个 kind** ＋ 新增 **gateway↔registry parity 测试**（一次消灭模式 B，并防将来）；
  `IMAGE_PIPELINE_CLASSIFY` 的唯一生产构造点已存在（`RoomCaptureWorkflowRepository.kt:409`），debug 假网关甚至已经声明了它——真网关漏了。
  > **2026-09-12 动手前核查：这一项要拆成两半，只有一半能动手，另一半是产品决策。**
  >
  > **① `IMAGE_PIPELINE_CLASSIFY`——真实缺陷，本批实现。** 生产路径确实在跑它：
  > `RoomCaptureWorkflowRepository.decideAndRedraw`（`:391-425`）构造 `ImagePipelineClassifyInput` 并
  > `tasks.execute(...)`。而配置出来的真网关（`OpenAiCompatibleModelGateway.kt:485-499`）的
  > `supportedTasks` **不含**它 → `RoomModelTaskRepository.capabilityFailure`（`:601-608`）
  > 返回 `PROVIDER_CAPABILITY_MISSING`「当前模型不支持这项任务」（`retryable = false`）→
  > `shouldRedraw` 恒假 → **只有"图里有几何图形才重绘"这一步静默不发生**（不崩、不提示）。
  > 修法是把该 kind 加进 `supportsImageInput` 那一组（它发图，与 CAPTURE_* 同类）。
  >
  > **② `LEARNING_SUMMARIZE`——不是"漏了一个 kind"，是整条通道不可达。** 见 §12.5 **N-13**：
  > 往生产网关加它反而是**新的假声明**（它的调用方只在 `LOCAL_NO_EGRESS` 下发这条请求）。
  > 这一半**不动手**，等产品决定。
  > **✅ ① 已完成（2026-09-12，`IMAGE_PIPELINE_CLASSIFY` ＋ 反向穷尽的 parity 测试）；② 仍待产品决定（N-13）。**
- **修正账本接线或显式下线并对齐文档**（`appendAttemptCorrection` 无生产写入方；相关机制：`FULL_REPLAY_REQUIRED`、`correctionWatermarkEpochMillis` 恒为 null）。
  **⏳ 仍是待办**（本批第 2 项）。
- **前置补救通道换真实内容源**：按 **S-5**，从 `fixtureSource.teachingArtifactForPracticeUnit` 改到
  `knowledge_teaching_material`（`BundledKnowledgeBaseInstaller` 已填充，release 可见，DAO 与 selector 已有测试）。
  **必须同时处理 N-12**：`teachingArtifact` 与前置补救读的是同一条内容源，换上真库正是让
  「题干读取失败」从几乎不可达变成可达的那一步；不给它一个自己的失败态，界面就会把读失败说成「这题不存在」。
  **✅ 2026-09-13 完成并验证**（本批第 3 项）——**但动手前纠正了三处定性，范围因此变成三层**：
  ① 材料那一半**早就是真库**（`teachingReferences` 只读 `knowledge_teaching_material`），
  卡在夹具上的是**「这道题绑在哪些 KC 上」**，不是内容源本身；
  ② N-12 的绑定理由**不成立**——`teachingArtifact` 与 `knowledge_teaching_material` 不是同一种东西，换不过去；
  ③ **最关键**：唯一渲染补救卡的是**策展屏**，实拍题走的 `CapturedReviewSessionScreen` 连这个参数都没有，
  所以只改数据层等于没改。实际改动落在数据（范围改读题库）＋装配（两张卡的门由 `artifact != null` 改成
  `readyToRead`）＋呈现（实拍屏渲染同一张卡）。N-12 按"加一个失败维度"处理，不是加 try/catch。
  **`reTeachOpening` 是同病但不在本项范围内**（需先决定它出现在哪个界面，属产品可见选择）——登记为 **N-16**。详见 §11 批 2 第 3 项
- **`readForDependents` 加 `relation_type` 过滤**（R-10）。
  **✅ 2026-09-13 完成并验证**（含仪器化用例与 1 条变异），见下面的执行状态。

#### 批 2 执行状态（第 1 项：网关能力集 ＋ parity 测试，2026-09-12）

**第 1 项（网关能力集 ＋ parity 测试）：只做能确证的那一半。**

动手前先把「到底漏了几个 kind」**枚举干净**，而不是只修审计点到的那一个（规则 §12.1）：
把每个「生产代码里真的会构造请求」的位置逐个映射到它的输入类型，得到 11 个 kind，
再与真网关宣告的集合对照：

| 生产构造点 | 输入类型 | kind | 真网关是否宣告 |
|---|---|---|---|
| `RoomCaptureWorkflowRepository.kt:407` | `ImagePipelineClassifyInput` | `IMAGE_PIPELINE_CLASSIFY` | **否——本项要修的** |
| `RoomBatchImportRepository.kt:501`、`BatchSplitRecognizer.kt:57`、`CaptureModelTaskRequests.kt:19` | `CaptureAssessmentInput` | `CAPTURE_ASSESS` | 是（图片组） |
| `CaptureModelTaskRequests.kt:41` | `CaptureParseInput` | `CAPTURE_PARSE` | 是（图片组） |
| `RoomMistakeOrganizationRepository.kt:261`、`MistakeOrganizationSection.kt:601` | `ProblemOrganizationInput` | `PROBLEM_CLASSIFY` | 是 |
| `KnowledgeReviewRequests.kt:67` | `KnowledgeQuizInput` | `KNOWLEDGE_QUIZ` | 是 |
| `TutorLobbyModelTaskPolicy.kt:75` | `TutorLobbyInput` | `TUTOR_LOBBY` | 是 |
| `TutorModelTaskPolicy.kt:255/319/413/493` | `TutorPlanInput`／`TutorRespondInput`／`TutorVisualGenerateInput`／`TutorVisualReviewInput` | `TUTOR_PLAN`／`TUTOR_RESPOND`／`TUTOR_VISUAL_GENERATE`／`TUTOR_VISUAL_REVIEW` | 是（后两者在图片组） |
| `TutorModelTaskPolicy.kt:567` | `TutorDebriefInput` | `LEARNING_SUMMARIZE` | 否——**但这一条不该加**，见 **N-13**（产品决策） |

**结论：`IMAGE_PIPELINE_CLASSIFY` 是唯一一个"生产在构造、真网关不宣告、且加进去是对的"的 kind。**
这条枚举本身就是本项的一半价值——它把"漏了一个"从印象变成**清单上只剩一个**。

**改动**：`OpenAiCompatibleModelGateway.toCapabilities()` 把 `IMAGE_PIPELINE_CLASSIFY` 加进
`supportsImageInput` 那一组（它发图，与 `CAPTURE_*` 同门），并留注释说明漏掉它的后果是
**静默失效**而不是崩溃。

**测试（`OpenAiCompatibleModelGatewayTest`）**：三条，各钉一侧——
① `theConfiguredGatewayAdvertisesEveryKindItsProtocolCanServe`：**清单式断言**（十个 kind 逐个列出）
与"宣告的每一条都能路由"（`ModelTaskContractRegistry.require` ＋ `ModelPromptPolicyVersions.currentFor` 非空）；
② `everyTaskKindIsEitherAdvertisedOrExplicitlyOutOfScopeForThisGateway`：**反向穷尽**——
`ModelTaskKind` 的 14 个取值必须被分成"本网关宣告"与"本网关不管"两组，后者逐条写理由，
于是**下一个新增的 kind 会让它立刻变红**；
③ 既有的 `onlyCapabilitiesThatPassedTheExactTestAreAdvertised` 加一条
`assertFalse(supports(IMAGE_PIPELINE_CLASSIFY))`（图片探测未过时不得宣告），把它钉在同一门上。

**结果**：`:core:data:testDebugUnitTest` **411 / 0**（＋2 条新用例），网关测试类 56 / 0，三条都真跑到了。

**变异（1 条，JVM）**：

| 变异 | 命中 | 失败消息（原文，节选） |
|---|---|---|
| M1 把 `add(IMAGE_PIPELINE_CLASSIFY)` 删掉（＝ 修复前的现场） | **仅** `theConfiguredGatewayAdvertisesEveryKindItsProtocolCanServe` | `expected:<[…IMAGE_PIPELINE_CLASSIFY…]> but was:<[…无它…]>` |

> M1 被**单独一条**抓住，正是「清单式断言」要的性质：列出的十条里少一条只有它能看见。
> 这也解释了为什么另两条抓不到——反向穷尽那条带的是它自己的字面集合，门控那条只管图片探测未过的那一侧。

#### 批 2 执行状态（R-10，2026-09-13）

**R-10（前置图不再依赖别处维护的严格性）：已完成并验证。**

原查询只按 `subject` ＋ `dependent_knowledge_node_id` 取行，**语义里隐含了"这张表只有一种关系
类型"**——而那条不变量维护在**另一个文件**里（`KnowledgeNodeRelationContract` 的
`require(relationType == PREREQUISITE_OF)`）。契约将来放宽一格，前置图就会静默多出几条
"其实不是前置"的边：不崩、不报错，只是多喂给调度器几条错误的先修关系。

改动三点：SQL 里加 `AND relation_type = :relationType`、DAO 加形参、调用方
（`RoomKnowledgeBaseStore.readKnowledgeNodeRelationsForDependents`）传
`StudyDbValue.KnowledgeRelationType.PREREQUISITE_OF`。

**测试（新增 `KnowledgeNodeRelationScopeInstrumentedTest`，仪器化）**：
把那次"将来"**提前造出来**——**绕过契约，直接往表里写一行另一种类型的关系**，再要求查询看不见它。
之所以绕得过去，正是这条缺陷的成因：`relation_type` 列上**没有 CHECK 约束**，
`knowledge_node_relation` 上也没有触发器，**契约是 Kotlin 层的、不是 schema 层的**。
用例同时钉三件事，缺一条就会变成"因为插不进去所以看不见"的假绿：

| 断言 | 它在防什么 |
|---|---|
| raw SQL 读回那行的类型 == 植入的类型 | 防"夹具没生效"——否则用例会因为**查不到**而假绿 |
| 同一对节点上真实存在的那条 `PREREQUISITE_OF` **照常返回** | 防"查询坏了"被当成"过滤生效了" |
| 返回值里**没有**植入那一行 | 这才是被测的性质 |

**变异（1 条，仪器化）**：把改动三点一起还原成修复前的形态（SQL 去掉条件、DAO 去掉形参、
调用方去掉实参）。

| 变异 | 命中（**集合相等**） | 失败消息（原文，节选） |
|---|---|---|
| M1 还原 R-10 前的查询 | `{aRelationTypeOutsideTheCurrentContractStaysOutOfThePrerequisiteGraph}` | 返回值里**同时**出现 `relationType=PLANTED_SECOND_RELATION_TYPE` 的那一行与真实的前置关系——**这就是"静默串味"的实物** |

**两条记账订正（都要写下来，因为它们各花掉一次设备运行）**：

1. **我先前记的"这条没有测试接缝，只能记为 UNVERIFIED"是错的。** 接缝一直存在：
   相邻的仪器化用例（`KnowledgeContextRetrievalInstrumentedTest`）早就在用
   `SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, …)` 直接读底层文件。
   那次判断的教训与 §12.1 同源——**"找不到接缝"要先枚举过所有既有用例再下结论**，
   否则会把"我没找到"写成"不存在"。
2. **两次设备运行各撞掉一条契约规则**，两条都是既有契约的既有要求，只是没写在我要读的那一段里：
   来源的 `contentFingerprint` 必须是 **大写十六进制**（`Regex("[A-F0-9]{64}")` 全串匹配——
   `"B"`／`"C"` 恰好合法，`"R"` 不合法，所以照抄邻居的写法也可能踩空）；
   以及**每一个**被导入的节点都要有已核验的来源绑定，**主题节点也不例外**。
   两条都不是缺陷，属于"夹具必须服从契约"。

**基线**：`:core:database:connectedDebugAndroidTest` 全量 **150 条**——修夹具前的那次全量跑里
**149 条通过、唯一失败的就是本条**（失败原因是夹具的指纹用了非十六进制的 `"R"`），
把夹具修好之后本类 1/1 通过。
**收尾的整轮全量重跑已完成（2026-09-13）：150 / 0 失败**（`core\database\build\outputs\androidTest-results\connected\debug` 的 XML，写于本轮窗口内）。
这一行同时结清了本条留下的那句"全量重跑待收尾"——**修好的夹具在全量运行里也是绿的**，
而不是只在单跑一个类时绿。

#### 批 2 执行状态（第 3 项：前置补救换真实内容源 ＋ N-12，2026-09-13）

**本项动手前先纠正了审计自己对本项的定性——三条，其中两条改变了动手范围。**

| 审计原文 | 一手回读后的事实 |
|---|---|
| S-5：「把 `prerequisiteRemediation` 的供给从夹具改到 `knowledge_teaching_material`（经 DAO + selector）」 | **材料那一半早就是真库了**：`teachingReferences`（`RoomTutorTeachingReferenceRepository` → `RoomKnowledgeBaseStore` → `KnowledgeTeachingMaterialDao.readForKnowledgeNodes`）本来就只读 `knowledge_teaching_material`。真正卡在夹具上的是**这道题绑在哪些 KC 上**——它取自 `artifact.knowledgeNodeIds`，而 `teachingArtifact` 在 release 里恒为 null |
| 同上：「这条从『无内容源』降级为『接错了源』」 | 更准确：**内容源一直是对的，错的是「范围从哪来」** |
| N-12：「批 2 第 3 项要把内容源从夹具换成 `knowledge_teaching_material`。**那一步就是让它变可达的那一步**」 | **不成立**：`teachingArtifact` 返回的是 `VerifiedTeachingArtifact`（题干／讲解／**策展**评测项），`knowledge_teaching_material` 是**知识点讲解材料**，两者不是同一种东西，`teachingArtifact` 换不过去也不该换。所以本项并**没有**让那条读取变可达，N-12 的理由要重新落 |

**更重要的第四条纠正：即使数据层修好，这条通道在生产里仍然不会出现。**

`prerequisiteRemediation` 的消费点是 `SmartMistakeBookDestinations` 的 `produceState`，而它
**以 `loadedArtifact != null` 为门**；策展件在 release 里恒为 null ⇒ 两张可选卡连**加载**都不会发生。
再往下：唯一渲染补救卡的是**策展屏** `ReviewSessionScreen`，而实拍题走的是
`CapturedReviewSessionScreen`——那一屏**根本没有 `prerequisiteRemediation` 这个参数**。
所以本项的真实范围是**三层**，只改数据层等于什么都没发生：

| 层 | 改动 |
|---|---|
| **数据**（core:data） | `prerequisiteRemediation` 改读 `currentKnowledgeScopeOf(practiceUnitId)`：错题读侧的 `knowledge_node_ids`（当前修订 ＋ 最近一次组织轮，与排程、视觉归因同一份结论），空集即返回 null（不拿猜的范围去查） |
| **装配**（app） | 两张卡的门由 `artifact != null` 改成 `readyToRead`（题存在且数据就绪）；**`reTeachOpening` 仍留在旧门上**（见下） |
| **呈现**（feature:review） | `CapturedReviewSessionScreen` 新增 `prerequisiteRemediation` 参数并在题干上方渲染 `PrerequisiteRemediationCard`（该卡由 `private` 改 `internal`，与策展屏共用同一个组件、同一条"不拦作答"规则） |

**N-12 一并处理（维度而不是 try/catch）**：`TeachingArtifactLoad` 增 `loadFailureDiagnosticId`，
`repository.teachingArtifact(...)` 的抛出被捕获（取消照常上抛）并落成**失败态**；
界面在「正在读取题目…」**之前**判掉失败态，显示 `teachingArtifactFailureMessage(...)`：
一句自己的话（读不出来 ＋ 没有记录作答 ＋ 怎么重试）＋ 自己的诊断编号前缀 `review:artifact:`。
消灭的失败很具体：**原来抛一次异常就让 `isLoaded` 永远停在 false，界面永远停在「正在读取题目…」**，
用户既看不到原因，也没有任何出路。

> **⚠️ 同日晚撤回：上面这一段的机制已被删除，N-12 判定为"不可达"而不是"已修"。**
> 复核发现那条读取在生产里**根本抛不出异常**（只有一种实现、委托给纯查表的夹具源、工厂无装饰层），
> 所以整套兜底**生下来就不可能触发**——机制建成、没有能触发它的失败。
> 保留这段话是为了记录**当时的推理**与它错在哪：错误在于"这次改动让那个 `produceState` 块对每张卡
> 都真的干活"推出"那次读取成了唯一能带走整块的调用"——**"没被包住"不等于"会抛"**。
> 完整复核与删除清单见 **§12.5 N-12 条内**。**不要**把 N-12 读成已修。

**新增登记：`reTeachOpening` 是同病，但不在本项范围内**（见 **§12.5 N-16**）。
它的范围与科目仍取自策展件（`artifact.subject` ＋ `artifact.knowledgeNodeIds`），而且**没有任何一屏
渲染过它**——本项只把门留给它，是因为放开那道门只会多一次必然返回 null 的查询。
把它一并做掉需要先决定"开场重教卡出现在实拍屏的什么地方"，那是**产品可见**的选择，
与补救卡不同：**spec §2.9 明说了补救要注入，§2.16 没有说开场重教要出现在哪个界面。**

**验收（新增 3 条 ＋ 变异 2 条）**

| 用例 | 层 | 钉住的性质 |
|---|---|---|
| `aCapturedQuestionGetsRemediationFromTheLibraryAloneWithNoFixtureSource` | core:data | **空夹具源 ＋ 不播种策展包**（release 的形状）下，实拍题也拿得到补救 |
| `theFailureSaysItFailedRatherThanThatItIsStillLoading` 等 3 条 | app（JVM） | 失败文案与「正在读取题目…」逐字不同、说清没有记录作答、给出下一步、编号前缀可辨识；另有一条钉住编号是**按实例**而不是按类（归类看前缀）——**这 3 条随当晚的撤回一并删除**（它们只能断言一段生产里不可能出现的文字） |
| `theCapturedScreenShowsTheRemediationBesideTheQuestionAndStillLetsTheStudentAnswer` | app（仪器化） | 实拍屏真的渲染出这张卡，且**题干与自评入口同时可用**（不拦作答） |

**变异（2 条，命中集合都是单元素）**

| 变异 | 命中 | 说明 |
|---|---|---|
| M1 把 `prerequisiteRemediation` 退回「以 artifact 范围为源」（修复前的形状） | **仅** `aCapturedQuestionGetsRemediationFromTheLibraryAloneWithNoFixtureSource` | **五条既有补救用例在变异下全部仍绿**——这就是 S-5 说的"既有覆盖看不见这条缺陷"的实测：它们全都显式注入 `M1CuratedFixtureSource` |
| M2 把补救卡从实拍屏撤掉（修复前的形状） | **仅** `theCapturedScreenShowsTheRemediationBesideTheQuestionAndStillLetsTheStudentAnswer` | 策展屏那条既有仪器化用例仍绿——**它测的是另一屏**，这正是这条缺陷能在两屏之间活下来的原因 |

> 两条变异的命中集合都是**单元素**，而且各自都是"新增的那一条"——这不是恰巧，是本项的性质决定的：
> **旧覆盖测的是策展路径，而缺陷整个长在实拍路径上**。夹具层要多加一条"
> `seedFixture` 按 `bundle.knowledgeBindings` 现算 KC 范围"的改动，否则该用例在旧夹具下恒为空范围、
> 变成一条永远为红的假用例；这条改动本身也是把夹具拉回生产形状（生产的
> `observeActiveMistakes` 就是这么算的）。

### 批 3　算法与数据质量【中本 · 依赖批 0】

- **`decay` 传递四处一起修**（F-01），并先补「拟合出的参数确实改变预测」的断言锁住它。
  **✅ 2026-09-12 完成并验证**（四处 ＋ 施工中枚举出的第五处），见下面的执行状态。
- **视觉通道传真实 `priorMemory`**（修 `delta_t` 恒 0）——这是 §8 漏项中**对算法科学性影响最直接**的一条，因为 `review_log` 正是 FSRS 优化器的训练数据。
  **✅ 2026-09-13 完成并验证**（本项第 2 项）。
- **同日聚合**：按 **S-4** 重新处理——先确证「同日重复」在生产可达，再决定是实现聚合，还是把 spec 的覆盖论证订正为
  「仅对同日重复失败成立」并显式接受同日重复成功的放大。
  **✅ 2026-09-13 完成并验证**（本项第 1 项）——**取"订正说明"**：可达性证实（但可达的是主观 6h 那条，
  视觉 1h 那条已被产品隔离关掉）；放大**有上界**（不动点 2.1210577 天，调度上最多值一天）；
  spec 那条实为两句、不唯一地合成一个算法。**代码零改动**，spec §10 注记重写，新增 6 条用例 ＋ 2 条变异。
- **统一 `elapsed` 口径**（F-02）：只有 `LearningProjector` 写持久状态且它已正确；统一另两处**不需要版本升级**。
  **✅ 2026-09-13 完成并验证**（本项第 3 项）——**但方向与原文的建议相反**：不是统一到分数天，而是统一到
  **整天**的本地日历日差（见 §3 **S-11**）。原文那句「不需要版本升级」经施工核实**成立**（写入侧一字未动）。
- **参数优化器加 spec §2.11 要求的 log-loss 门**（现在是 `optimize()` 后直接写回，无 `evaluate()`）。
  **✅ 2026-09-13 完成并验证**（本项第 4 项）——**但前提表述被 S-10 订正**：门其实**在**（`SchedulingEvaluation.evaluate`），
  缺的是「对照物是**当前生效的那组**」而不是出厂默认。已按订正后的判据实现；spec 的 log-loss **绝对区间**未实现（归属 **R-07**）。

#### 批 3 执行状态（2026-09-12 起）

**F-01（衰减参数四处冻结）：四处一起修 ＋ 一族按出口分条的断言。**

先说清它为什么**不只是"少拟合一个参数"**：研究文档 `docs/research/mastery-math-modeling.md`
把 `w20` 写成**可训练、区间 `[0.1, 0.8]`**（`:39-41`），并在同一节给出带它的闭式间隔公式
`I(r*, S) = (S/FACTOR)·(r*^(1/w20) − 1)`（`:55`）——也就是说**规格说它是可训练的，代码把它冻住了**，
这是实现与规格不一致，而不是"优化空间"。

四处的位置与症状（都在 `core/domain`）：

| # | 位置 | 症状 |
|---|---|---|
| ① | `SchedulingEvaluation.kt:140`（`SchedulingReplay.predict`）`retention(elapsedDays, stability)` | 损失与衰减无关 ⇒ **梯度恒为零**，拟合空转 |
| ② | `FsrsScheduleMath.intervalDays` 里 `val decay = -DEFAULT_PARAMETERS[20]` | 函数签名原本没有 decay 入口 |
| ③ | `MemoryUpdateModel.kt:86,93` 的 `retention(elapsedDays, previous.stabilityDays)` | 忽略自己持有的 `parameters` |
| ④ | `OptimalRetention.averageRetention` 里同样写死 | 同文件的 `simulate` 却已正确转发 `parameters` |

**修法**：给 ② 的 `intervalDays` 加 `decay` 形参（带默认值，只为"手上没有参数"的场景），
给 ④ 的 `averageRetention` 加**必填** `decay`（留默认值只会让下一个调用方再犯一次同样的错），
①③④ 各自的调用点改成传 `-parameters[20]`。

> **这次的改动对现有安装是零行为变化**：`parameters[20]` 至今从未离开默认值
> （正是因为这条缺陷），所以 `-parameters[20]` 与原先写死的默认值逐位相同。
> **不需要 `LearningProjector.VERSION` 升级，也不需要重放。**
> 这也解释了为什么这条修复是安全的，以及为什么它必须在**同一批**里四处一起做——
> 一旦只有 ① 生效，`w20` 会开始移动，而其余三处仍读默认值，那才是真正的训练／服务不一致。

**断言（新文件 `FsrsDecayThreadingTest`，四条按出口分）**：夹具只把 `parameters[20]` 从
`0.1542` 挪到 `0.4`，其余 20 位逐位相同，于是断言出来的差异只可能来自衰减。
一条必须知道的性质写进了类注释，否则用例会写成"看起来对但恒真"：
**目标保持率恰好是 `0.9` 时 `intervalDays` 与衰减无关**（`FACTOR` 就是按 `R(S,S)=0.9` 定义的），
所以间隔那一条用例把目标保持率设成 `0.8`（产品里真会出现的取值，`0.7..0.97`）。

**施工中发现审计点名的四处不是全部：还差第五处，而且它比其中两处更承重。**
按 §12.1 把**所有**读衰减的调用点枚举干净（而不是只看点名的四处），多出两处：

| # | 位置 | 为什么它必须一起改 |
|---|---|---|
| ⑤ | `LearningProjector` 的**毕业分支**：`intervalDays(…, desiredRetention)` 与实际写进 `nextReviewAt` 的 `ForgettingCurve.reviewAtTargetRetention` | 它**写持久状态**。只改审计那四处的话，常规分支用拟合衰减、毕业分支用出厂衰减——**在本来一致的地方造出不一致**，正是这批要消灭的那种失败 |
| ⑥ | `ForgettingCurve.estimateAt` 的 FSRS6 分支 | 同一族；它喂的是"读出的 R"，与⑤共用同一条曲线 |

因此改动落成：`ForgettingCurve` 新增 `decay`（默认出厂值），⑤⑥ 两处改用它；
`FsrsMemoryUpdateModel` 新增 `internal val decay`（与它已经公开的 `desiredRetention` 同级），
毕业分支用它；core:data 的装配点把**同一组参数**同时交给模型与两条曲线
（原先 `optimizedFsrsParameters ?: DEFAULT_PARAMETERS` 写了两遍——那正是两边各走一条曲线的机会）。

**范围边界（§12.2：多改的每一处都要说清，没改的每一处也要）**：F-01 收口的是
**「写持久调度的路径」＋「拟合自己的损失」**。仍有一批**只读估计**用出厂衰减
（`KnowledgeReviewQueue` 的到期风险、V1 规划器与 app 侧的各 `ForgettingCurve()` 默认实例）——
它们**登记为 N-14**（见 §12.5），理由是它们与 **F-02** 落在同一片代码上，
应当一起设计免得来回改；而且在 F-01 落地前它们与写侧**逐位一致**，是惰性的。

**这条改动对现有安装是零行为变化**，这一点是它能安全落地的全部原因：
`parameters[20]` 至今从未离开默认值（正是这条缺陷），所以每一处 `-parameters[20]`
与原先写死的默认值逐位相同。**不需要 `LearningProjector.VERSION` 升级，也不需要重放。**
实测佐证：`core:data` 的 411 条既有测试在改动前后**逐条不变**（见下）。

**施工中带出的第二个发现：第 ⑤ 处的两半，在默认设置下都**不可观测**——而且阈值是
两个具体的数（2026-09-13，由一条**没被抓住的变异**带出）。**

第一版变异里有一条「把毕业**闸门**的衰减换回出厂默认」，它一条都没打红。查下去不是脚本的问题，
也不是哪条用例写松了，而是这一格在默认配置下**本来就分辨不出衰减**：

| 目标保持率 r* | 闸门能否分辨衰减 | 「毕业 / 不毕业」两种结果是否不同 | 说明 |
|---|---|---|---|
| **0.9**（出厂默认） | **否** | 是 | `intervalDays(S, 0.9, 任意衰减) == S`——`FACTOR` 就是按 `R(S,S)=0.9` 定义的，两种衰减给出**逐位相同**的间隔（实测 89 天 / 93 天） |
| **0.8**（`GRADUATION_TARGET_RETENTION`） | 是 | **否** | 闸门过与不过都写 `intervalDays(S, 0.8)`，同一天 |
| 其它（0.7…0.97，滑杆区间） | 是 | 是 | 只有落在这一格，闸门的衰减才既**可分辨**又**有后果** |

所以第 ⑤ 处的闸门衰减**不是死代码，但它的生效条件是"用户把目标保持率设成既不是 0.9 也不是
0.8 的值"**。这不是假想配置：设置页把它做成 `0.7f..0.97f` 的滑杆
（`SchedulingSettingsScreen.kt:76`），`RoomBackedStudyExperienceRepository` 把它原样交给模型
（`:163`）。用户只要拖动过那根滑杆，闸门读哪条曲线就开始决定"这次复习算不算毕业"。

**因此补了一条只挑这一格的用例**（`FsrsProjectionBehaviorTest.graduation gate follows the
fitted decay of its model`）：目标保持率取 **0.75**（避开 0.9 与 0.8 两个"看不出差别"的取值，
且仍在滑杆区间内），再从"差一次跨日成功就满毕业连胜"的状态出发，扫描播种的记忆强度，
**只取两种衰减对 90 天闸门判断相反**的那一格——拟合衰减说"还不够"（实测 57 天）、
出厂衰减会误判成"够了"（实测 91 天），断言此时必须**不毕业**、写在 `nextReviewAt` 上的
是常规间隔。夹具自带两条反向断言（找到了这一格；这一格真能分辨两种结果），
任一条不成立就红——用例不会因为"恰好没踩到那一格"而静默变成恒真。

> 这一条同时是 §12.2 的一次实测：**"多改一处就多一个可能不生效的位置"**。第 ⑤ 处是施工中
> 从审计点名的四处之外**加出来**的（见上表 ⑤⑥），而加出来的两处里，闸门这一半在默认
> 配置下完全没有可观测后果。加它的理由仍然成立（不能"拿一条曲线判够不够 90 天、再拿另一条
> 曲线算下一次复习日"），但它提醒：**加一处的代价不只是那一行代码，还包括一条能证明它
> 真的生效的用例**——否则永远不知道它是接线还是摆设。

**变异（8 条，JVM；每条都逐处点名命中的断言，不比"有没有变红"更松）**

| 变异 | 命中（**集合相等**） | 失败消息（原文，节选） |
|---|---|---|
| M1 第 ① 处：`SchedulingReplay.predict` 读回默认衰减（＝修复前的现场） | `{theReplayPredictionsFollowTheFittedDecay}` | 「重放出的（预测可提取率, 实际结果）必须随衰减变化……不变化就意味着 ∂loss/∂w20 ≡ 0」 |
| M2 第 ② 处：模型的 `intervalDays` 丢掉 `decay` | `{theScheduledIntervalFollowsTheFittedDecay, graduation gate follows the fitted decay of its model}` | `expected:<1907> but was:<2551>` |
| M3 第 ③ 处：模型的 `retrievability` 读回默认衰减 | `{theMemoryUpdateFollowsTheFittedDecay}` | `Actual: 703.9107336322717` |
| M4 第 ④ 处（**文件层**）：`simulate` 的局部 `decay` 换回出厂值 | `{theRetentionRecommendationFollowsTheFittedDecay}` | 「CMRR 曲线上的『记住量』必须随衰减变化……实测最大差 0.0」 |
| **M4b 第 ④ 处（只把 `averageRetention` 的实参换回出厂值）** | **∅ —— 记录在案的缺口** | **未被抓住（如文档所述）**：同一函数里另外两处 `decay` 仍在拟合值上，把这一处的差异盖住了 |
| M5 第 ⑥ 处：`ForgettingCurve.estimateAt` 读回默认衰减 | `{theForgettingCurveHonoursTheDecayItIsGiven}` | `Actual: 0.9727695594804078` |
| M6 第 ⑤ 处：`ForgettingCurve.reviewAtTargetRetention` 读回默认衰减 | `{theForgettingCurveHonoursTheDecayItIsGiven, graduation maintenance follows the fitted decay of its model}` | `expected:<403> but was:<539>` |
| M7 第 ⑤ 处（**接线**）：投影器把出厂衰减交给毕业闸门 | `{graduation gate follows the fitted decay of its model}` | `expected:<57> but was:<40>` |

三条读数纪律记在这里：

- **M4b 是"跑得完"和"抓得住"的区别**。M4 最初写成"只把 `averageRetention` 的实参换回默认"，
  结果是绿的；那不是脚本坏了，而是**这个变异在那一格上不可观测**（同一个 `simulate` 里
  `intervalDays` 与 `retention` 仍用拟合衰减，曲线整体照样与默认不同，整条曲线断言恒真）。
  要抓住它需要"只走一步就停下"的接缝——**记录为缺口（预期集合 ∅）**，而不是把红调绿、
  也不是把断言放宽到能过。真正的**文件层**变异（M4）随后被一条断言精确抓住。
- **M6 的第一版预期集合写错了**，是**用例抓得比预期更严**：`reviewAtTargetRetention` 不只被
  遗忘曲线用例读，**毕业维护的写路径**也读它，所以那条毕业用例同样会红。集合改成两条，
  改动的是**记录**，不是代码。
- **M7 的"没被抓住"曾一度像脚本的问题**，查下去是用例夹具的**观测窗口**问题（见上面那张
  阈值表）：原毕业用例挑的记忆强度使两种衰减都远超 90 天，闸门怎么判都通过。补一条只挑
  判断相反那一格的用例之后，M7 落在它身上——**判据没变，是被观测的那一格变了**。

**这一批的回归基线（冻结树，`--no-daemon`、不带 `--tests` 过滤）**：
`:core:domain:test` **402 / 0**（F-01 前 395；新增 `FsrsDecayThreadingTest` **5** 条、
`FsrsProjectionBehaviorTest` 毕业接线 1 条 ＝ 401，再加本轮补的毕业闸门 1 条 ＝ 402）。

**批 3 第 2 项：视觉通道的 `delta_t` 不再恒 0（2026-09-13 完成并验证）**

原文：`VisualInteractionIngestor.kt` 给 `ReviewLogSink.record` 传的 `priorMemory` 是 `null`，
而 sink 把 `null` 映射成 `deltaDays = 0.0`（`ReviewLogSink.kt:58`）。**这条不是"少一个字段"**：
`review_log` 正是 FSRS 参数优化器的训练数据，`delta_t ≡ 0` 的那批样本让拟合看到一个"所有复习
都挤在同一天"的世界，而它们与真实的同日重复在特征空间里**混在一起**、事后分不开。

改法分两半，**第二半是施工中才发现的**：

| 半 | 内容 | 为什么它单独不够 |
|---|---|---|
| ① 根源 | 把"上一次复习的时间戳"从当前投影传进排空（`LearnerSnapshot.problemMemoryStates`） | 修好"完全不传" |
| ② 一轮内部 | 一次排空里写下第 2..n 行时，以其**紧邻的前一行**为界推进 | ① 取的是**排空开始前**的投影：同一次排空补录两条相隔超过冷却的视觉交互时，第二行会跨过第一行去读上一次投影，把"隔了两天"读成"隔了五天"。DAO 按 `attempted_at_epoch_millis ASC` 返回，所以顺序推进是对的 |

**施工中撞上的一堵墙，以及它为什么会改到签名上（§12.1 的一次实测）**：第 ② 半最初写成
"把携带的 `ProblemMemoryState` 的 `lastReviewedAtEpochMillis` 推进到刚写下的那一行"，
`ProblemMemoryState` 的不变量立刻拒绝——`Next review must not precede the latest review`
（`LearningState.kt:535`）。要让它过，就得给 `nextReviewAt` **编一个值**，而那正是我在同一段
注释里反对的事（编出来的数字除了骗过校验没有用处）。于是改成**签名只收时间戳**：

```kotlin
suspend fun record(…, previousReviewedAtEpochMillis: Long?, …)   // 原：priorMemory: ProblemMemoryState?
```

sink 里**唯一**用到那个对象的地方就是它的 `lastReviewedAtEpochMillis`（`:58` 的判空与 `:80` 的
换算），所以这次收窄对既有 6 个调用点是**逐位等价**的：原来传 `priorMemory`，现在传
`priorMemory?.lastReviewedAtEpochMillis`，判空分支与 `≤ 0` 分支一一对应。
**这正是"改一行"的修复牵出 7 个调用点 + 一次签名收窄**的例子——不是范围失控，
是那一行本来就没有诚实的最小改法。

**发现方式值得单记一条（与 §9 的横切模式同源，但方向相反）**：第 ② 半的错误**没有表现为断言失败**。
`initialize()` 的视觉排空是**静默降级**的（`catch (failure: Throwable)`，见
`RoomBackedStudyExperienceRepository.kt:386-389`），账本里只是**少了一行**，
delta 断言于是拿到一个单元素列表。把用例改走 `ingestVisualInteractionAttempts()`
（不吞异常的那条入口）之后，异常原文立刻现形。
**"静默降级"是这次排查里唯一让缺陷不可见的东西**——让失败可见比让失败不发生更重要。

**回归基线**：`:core:data:testDebugUnitTest` **413 / 0**（改动前 411；新增
`visualEvidenceRecordsTheRealGapSinceItsLastReview` 与
`aSweepMeasuresEachRowFromTheRowJustBeforeIt` 两条）。

**变异（2 条，JVM；各钉一半，命中集合相等）**

| 变异 | 命中（**集合相等**） | 失败消息（原文，节选） |
|---|---|---|
| V1 把 `previousReviewedAtEpochMillis` 换回 `null`（＝修复前的现场） | `{visualEvidenceRecordsTheRealGapSinceItsLastReview, aSweepMeasuresEachRowFromTheRowJustBeforeIt}` | `expected:<[3.0, 2.0]> but was:<[0.0, 0.0]>` ／ `expected:<3.0> but was:<0.0>` |
| V2 去掉第 ② 半的推进（不再以紧邻的前一行为界） | **仅** `{aSweepMeasuresEachRowFromTheRowJustBeforeIt}` | `expected:<[3.0, 2.0]> but was:<[3.0, 5.0]>` |

V2 的读数**就是第 ② 半要消灭的那个失败的实物**：第二行没有以第一行为界，跨过去读了
排空前的投影（"上一次复习"在第 0 天），于是把真实的两天读成五天。而 V1 只被两条用例中的
**两条**同时抓住、V2 只被其中一条抓住——**集合不等**正是这两半互不替代的证据。

**批 3 第 4 项：写回处加「相对现行参数」的 hold-out 门（审计 S-10，2026-09-13 完成并验证）**

先把表述订正清楚（**S-10 已改掉了原来那句**）：原写「优化器没有 log-loss 门、无 `evaluate()`」，
读完一手来源才发现门是有的、而且更严——`fit` 用**出厂默认**做择优基准，返回的参数在留出尾段上
从不比默认差。**缺的是另一个基准**：装配点只判 `mode != INSUFFICIENT_DATA` 就无条件写回，
于是"比出厂默认好"（必然成立）就够写回，**一次新拟合可以把已经更好的现行参数换掉**。

改动落在两处：

| 位置 | 改动 |
|---|---|
| `FsrsParameterOptimizer.optimize` | 新增 `incumbent: DoubleArray = DEFAULT_PARAMETERS` 形参（默认值让"还没拟合过"的首次调用**逐位等价于旧行为**）；拟合结束后用**同一段留出尾段**比一次：候选必须优于 incumbent 超过 `ADOPTION_MARGIN`（`1e-9`，与 `fit` 内部判"更好"的阈值同值——两处不一致会在两道门之间留下一条谁都不认的缝） |
| `StudySchedulingCalibration.optimizeSchedulingParameters` | 把 `store.optimizedParameters.first() ?: DEFAULT_PARAMETERS` 作为 incumbent 传进去；**只在 `result.adopted` 时写回**（被拒时返回的就是 incumbent，照写一遍会白产生一次 DataStore 写入与一次流发射） |

**一次差点走错的设计弯路，值得记**：第一版把"被拒"做成 `Mode` 的一个新取值
（`REJECTED_NO_IMPROVEMENT`）。跑起来立刻发现它把**两个正交的维度塞进了一个枚举**——
`Mode` 回答"拟合了多少"，被拒回答"采不采纳"；合在一起会让"窄带只拟合了 w0..w5"这类事实
**在恰好被拒时凭空消失**（实测：`optimizer fits initial stability only in the narrow band`
立刻红在 `expected:<[0, 1, 2, 3, 4, 5]> but was:<[]>`）。
于是改成**正交的一个布尔位 `adopted`**，并把口径划干净：

> **`adopted` 是唯一描述"结果"的字段**；`mode`／`trainLogLoss`／`validationLogLoss`／
> `optimizedParameterIndices` 一律描述**这次尝试**（拟合了多少、损失是多少）。`adopted = false`
> 时 `parameters` 就是 incumbent 本身。

这样改完，**既有的 6 条优化器用例一条都不用改**（它们断言的是"尝试的范围"，语义没变），
`REJECTED` 那个枚举值也不需要存在。教训与 S-9 同源：**新概念先问它和既有维度是否正交**，
塞进既有枚举之前先试一次"能不能只是一个新字段"。

**验收（新用例 `a refit on the same data never replaces the parameters it started from`）**：
夹具先拟一次得到 `fitted`，再**以 `fitted` 为 incumbent 拟第二次**——同一批样本、同一套过程、
同样的迭代次数 ⇒ 第二次不可能更好，因此必须 `adopted = false`、参数逐位等于 `fitted`。
这是"写回只会让留出损失变小"最锋利的一格，而且**不依赖真实数据里恰好出现退步**。

**变异（2 条，JVM；命中集合相等）**

| 变异 | 命中 | 失败消息（原文，节选） |
|---|---|---|
| M1 闸门条件改成恒假（＝候选永远被采纳，即修复前的行为） | `{a refit on the same data never replaces the parameters it started from}` | 「第二次拟合没有在留出尾段上更优，就不该被采纳」 |
| M2 把对照物换回**出厂默认**（＝修复前的**基准**） | 同上 | 同上 |

**回归基线**：`:core:domain:test` **403 / 0**（＋1 条新用例）；
`:core:data:testDebugUnitTest` **413 / 0**（装配点改动的回归）。

**范围边界（§12.2）**：这次只加"不许变差"的门，**没有**实现 spec §2.11 那句 `evaluate()` 的
log-loss **绝对区间**（0.35–0.45）。理由：绝对区间是**上线门**（"这套模型值不值得用"），
不是**采纳门**（"这次拟合比现在这组好吗"）；把它当采纳门，会让"用户数据本身偏难、
log-loss 天然偏高"的情形**永久拒绝任何改进**。它的归属是 **R-07**（标定回路接线或下线），
那时才需要决定这个区间是硬门还是监控指标。

**批 3 第 3 项：读侧「已经过天数」收口到一个函数（F-02 ＋ R-04 ＋ N-14 的队列那一半，2026-09-13 完成并验证）**

原文（F-02）：同一个工程里并存**三种**「已经过天数」口径，于是**同一张卡、同一时刻，
两个界面对 R 的估计不同**——一张卡 23:00 复习过，次日 01:00 看，"墙钟整日地板"说过了 0 天
（R = 1），"未取整墙钟分数天"说过了 0.083 天，而**本地日历日差**（写入侧用的那个）说过了 1 天。

**方向先被 S-11 纠正过**：本记录作者原本建议统一到**分数天**（且已被用户采纳），
回读一手资料后自己推翻——**分数天是 FSRS-7 的能力，本项目实现的是 FSRS-6**。
于是统一到**整天**的本地日历日差，也就是 spec 本来就写着、写入侧本来就在用的那一个。
**spec 一个字都没改**。

**先按 §12.1 把"算天数"的位置枚举干净**（而不是只改审计点名的那两处），逐条判在不在范围内：

| 位置 | 判 | 理由 |
|---|---|---|
| `ForgettingCurve.estimateAt` 的 FSRS-6 分支（墙钟整日地板） | **改** | F-02 的两处之一；错题详情/影子审计读它 |
| `KnowledgeReviewQueue.knowledgeRecallRiskByNode`（分数墙钟天） | **改** | F-02 的另一处；知识点队列读它 |
| `ReviewSample.elapsedDaysSince` 的**回退分支** | **改** | 收集到的 `delta_t` 是整天，回退却是分数天——同一列特征混两种刻度；而且**代码与它自己的文档不符**（文档写 "wall-clock day floor"） |
| `LearningProjector` 的写入侧 | **不改** | 本来就是本地日历日差，正确；不改 ⇒ **不需要投影版本升级、不需要重放** |
| `ForgettingCurve` 的 `LEGACY_EXPONENTIAL` 分支 | **不改** | spec §2.20 的 kill switch，行为由用例逐位冻结，用精确毫秒 |
| `HLRPredictionAuditService.extractHlrFeaturesForShadow` 的 `daysSinceFirstSeen` | **不改** | 那是 **HLR 影子模型自己的特征**，不是 FSRS 的 `t` |
| `ReviewPlanner*` 的 `waitingDays`/`overdueDays`（排队节奏） | **不改** | 是「等了多久」的排序量，不是 R(t,S) 的 t |
| `MasterySmoothing` / `SleepWindowSignals` 的天数 | **不改** | 分别是 EMA 时间常数与睡眠窗几何，与 t 无关 |
| app 的 `recentActivityLabel`（"今天/昨天/N天前"） | **不改，但登记** | 同一物种的另一处（墙钟整日地板 vs 学习者日历），**用户可见**。它是标签不是估计，且改它需要把时区带进 UI 层——登记为 §12.5 **N-15**，不塞进本条 |

> **上面这张表第一版漏了两处，是收尾前的第二次枚举把它们找出来的——按 §12.1 记一笔。**
> 第一次枚举按"谁在算**已经过天数**"去找，于是"谁在**换算某个时刻属于哪一天**"这一类被漏掉了。
> 第二次换成按**表达式**去找（全仓 `toEpochDay()`／`DAY_MILLIS` 扫描），立刻命中两处**与
> `ReviewCalendar.localEpochDayOf(millis, zoneId)` 逐字符等价的内联副本**：
>
> | 位置 | 内容 | 判 |
> |---|---|---|
> | `core/data/.../study/ReviewLogSink.kt:78-85` | `Instant.ofEpochMilli(previous).atZone(studyZoneId).toLocalDate().toEpochDay()`——算 `review_log.delta_t` 的起点 | **改：委托 `ReviewCalendar`** |
> | `app/.../ReviewReminderCoordinator.kt:55-58` | 同一表达式，算"这条提醒算哪一天"的当日去重键 | **改：委托 `ReviewCalendar`** |
>
> 两处**都不是缺陷**（它们用的就是学习者时区，口径本来就对），所以"按失败找"永远找不到它们。
> 但它们是**定义的副本**：`ReviewCalendar` 存在的理由就是让日界规则只写一遍，留着两份内联副本
> 等于把这个理由作废一半——下一个改日界规则的人（例如引入 Anki 那样的可配置 rollover）
> 会在 `ReviewCalendar` 里改，而这两处**静默地保持旧规则**，症状与 F-02 同族。
> **教训**："枚举影响面"要按**要被消灭的那个概念的每一种表达形式**去找，而不是按"出错的那个症状"去找——
> 按症状找，只能找到已经出错的地方；按概念找，才能找到将来会出错的地方。
> 另有第三处 `StudyWriteContext.studyDayAt:32-42`（`local.toLocalDate().toEpochDay()`）**不改**：
> 它手里本来就有那个 `ZonedDateTime`（要用它的 `offset` 生成 `StudyDayContext` 的另两个字段），
> 把同一行换成函数调用会让同一个时刻被换算两遍，**换不到任何确定性收益**——它是日界规则的
> **产地**，不是它的副本。

**改动**：

| 落点 | 内容 |
|---|---|
| **新增 `ReviewCalendar`（core:domain）** | 「这个学习者的一天从哪到哪」的**唯一**定义：`localEpochDayOf(millis, utcOffsetMinutes)`（写入/重放用的、事件自带偏移那一支，**实现原样搬出** `LearningProjector` 的私有副本）、`localEpochDayOf(millis, zoneId)`（读侧用）、`elapsedCalendarDays(previous, at, zoneId)`。类注释里写明为什么是整天而不是分数天，并指向一手引文 |
| `ForgettingCurve.estimateAt/retentionAt/retentionNow` | 增加**必填** `zoneId`（无默认——默认一个 UTC 就是让下一个调用点静默算错日界）；FSRS-6 分支改调 `ReviewCalendar.elapsedCalendarDays`。R-04 要的"口径切换只发生在一处"由此达成 |
| `knowledgeRecallRiskByNode` | 改收 **`forgettingCurve` ＋ `zoneId`**，不再自己算天数、也不再自己取默认衰减。一次收掉两件事：F-02 的第二处，以及 **N-14 的队列那一半**（它原先读的是出厂衰减，与排期用的不是同一条曲线） |
| `ReviewPlanningRequest` | 新增派生属性 `zoneId`（由已有的 `timeZoneId` 解析，**只解析这一次**），两个规划器的 `scoreCandidate` 带上它 |
| `EvidenceProjectionInput` | 新增**必填** `timeZoneId`。这条通道**目前没有生产写入方**（接口在 `StudentModelContracts`，全仓只有声明与实现各一处，正是 R-07 记的"四个校准接口无调用方"），所以这个字段不会破坏任何调用点 |
| `StudyExperienceMappers.toCatalogEntry` / `StudySnapshotBuilder` / `StudyReviewPlannerService` | 逐处把装配点的 `studyZoneId` 与**产线那条曲线**传下去（映射器本来就是"每个依赖都是显式参数"的写法，顺着它的既有形状加） |
| `ReviewLogSink.record` | 第二次枚举补上（见上）：算 `review_log.delta_t` 起点的那段内联换算改为 `ReviewCalendar.localEpochDayOf(previous, studyZoneId)`。**逐位等价**——委托前后的函数体是同一个表达式 |
| `ReviewReminderCoordinator.handleReminderFired` | 第二次枚举补上：提醒的"当日去重键"同样改为委托。**逐位等价**，且无测试变红（该类的 6 条 `ReviewReminderCoordinatorTest` 用例全绿） |

**验收（新增 `ReviewCalendarTest`，4 条）**：判别性的那一格是**跨午夜、不足 24 小时**
（本地 23:00 → 次日 01:00）——三种口径在这一格给出三个**不同**的数，所以只有它能分开它们。
四条分别钉：日历日差本身（1.0，且**不是** 0）、曲线喂进去的**恰好是一天**（等于
`retention(1.0, S)`，既不等于 0 天的 1.0、也不等于 0.083 天的另一个数）、**两个读侧出口
给出同一个 R**（F-02 的原始症状）、以及"事件自带偏移"与"学习者时区"两个换算对固定偏移时区逐位相同。

**变异（3 条，JVM；命中集合相等）**

| 变异 | 命中 | 失败消息（原文，节选） |
|---|---|---|
| M1 共享函数自己退回墙钟整日地板 | `{two hours across local midnight…, the fsrs read path feeds the curve…}` | `expected:<1.0> but was:<0.0>` ／ `expected:<0.9573…> but was:<1.0>` |
| M2 **只**把曲线退回它自己的墙钟地板 | **仅** `{the fsrs read path feeds the curve…}` | `expected:<0.9573…> but was:<1.0>` |
| M3 **只**把队列退回内联的分数天 | **仅** `{the knowledge risk read path agrees…}` | `expected:<0.9573…> but was:<0.9958…>`——**同一张卡两个 R，这就是 F-02 的实物** |

> **M2 的预期集合第一版写错了，是脚本把它纠回来的**：我原以为"只改曲线"会让两个出口不一致，
> 于是预期 `{数值, 一致性}`。实际只命中数值那条——因为**队列现在委托给曲线**，
> 曲线口径一改，两个出口一起改、仍然一致。**所以两条用例各守一侧、谁也不包含谁**：
> 数值那条守"口径对不对"，一致性那条守"队列有没有停止委托"。
> 这与 F-01 的 M6/M7 是同一类读数纪律：**把观察到的集合改成与预期相符才是要防的错**。

**回归基线**：`:core:domain:test` **407 / 0**（＋4 条用例）、`:core:data:testDebugUnitTest` **413 / 0**、
`:core:model` 299 / 0、`:core:ui` 11 / 0、`:app` 31 / 0 与 32 / 0。
**值得记一笔**：这一批**没有任何既有用例变红**——因为既有夹具的间隔都恰好是整天（或整 24 小时），
在两种口径下逐位相同。这正是这条缺陷能长期活着的原因：**测试覆盖了函数，却没有一格覆盖
"两种口径分道扬镳的那一格"**（§9 模式 F）。

**两处补委托的验证**（第二次枚举带出的 `ReviewLogSink` 与 `ReviewReminderCoordinator`）：
这两行改动**逐位等价**（被替换掉的表达式与新调用的函数体相同），所以它们的证据不是"新用例变红"，
而是**覆盖它们的既有套件在改动后仍全绿**，且这两套件**在本轮的最终整轮运行里是真跑过的**：
`:core:data:testDebugUnitTest` 413 / 0 ＋ `:core:data:connectedDebugAndroidTest` **100 / 0**
（`review_log` 的写入路径）；`:app:testLocalFirstDebugUnitTest` 31 / 0 ＋
`:app:testStrictOfflineDebugUnitTest` 32 / 0 ＋ 两个 app 仪器化套件各 40 / 0（提醒协调器）。
**说清这条证据的限度**：它证明"没有改坏"，**不**证明"新的日界规则在跨午夜时也对"——
后者由 `ReviewCalendarTest` 的跨午夜那一格负责，而那两条既有用例一条都没落在那一格上（同上，模式 F）。

**N-14 的剩余一半**（登记不变）：domain 内部那些**默认** `ForgettingCurve()` 实例
（`HLRPredictionAuditService` 的默认曲线、`LearningProjector`/两个规划器的默认参数）仍读出厂衰减。
它们**不写持久状态**，且触发条件是"优化器真的开始移动 `w20`"；队列那一半已经在本条收掉。

**批 3 第 1 项：同日聚合——先确证可达性，再按数字决定"实现"还是"订正说明"（2026-09-13 完成并验证）**

原文（§5.1.5 `sameday-aggregation-missing`，已被 **S-4** 重新定性）要求本项：**先确证「同日重复」
在生产可达，再决定是实现聚合，还是把 spec 的覆盖论证订正为「仅对同日重复失败成立」并显式接受
同日重复成功的放大。** 两步都做了。

**第一步：可达性——可达，但可达的那条路与原判以为的不是同一条。**

| 事实 | 证据 |
|---|---|
| **没有任何不变量**禁止同卡在同一天发生第二次记忆更新 | 唯一的闸门是 §2.7 的防刷冷却：主观 6h、视觉 1h；ATTEMPT 通道无冷却（`RoomBackedStudyExperienceRepository.submitChoice` 只判 `writeResult.created`） |
| 计划器**不排除未到期卡** | `ReviewPlannerV2.kt:488-496` 给未到期卡 `dueRisk = 1 − R`（非零），短队列下可被选中 |
| 视觉通道**确实会写记忆** | `VisualInteractionIngestor` 构造完整的 `AttemptWriteCommand`（含 `problemMemoryOutcome`），走与作答同一条账本 |
| **但视觉通道目前不在产品面上** | `docs/model-first-product-boundaries.md` 的 2026-09-06 结构化场景渲染隔离：`TUTOR_VISUAL_GENERATE`/`TUTOR_VISUAL_REVIEW` 不再触发、scene 不再渲染 ⇒ `VisualInteractionEventSink` **没有生产写入方** |
| ⇒ 当前实际可达的生产者是**主观评级那条 6h 冷却** | 同一天内间隔 ≥6h 的两次自评（如 09:00 与 16:00）都 `schedulingEligible`，两次 `elapsedCalendarDays` 都是 0 |

**这一步的价值在于它推翻了一个默认印象**：1h 冷却那条最宽松的通道**恰好**是被产品隔离关掉的那一条，
所以"同日重复很容易发生"这个直觉在当前产品配置下**不成立**；可达性成立，但靠的是 6h 那条。

**第二步：把放大算死——它有上界，而且上界与证据条数无关。**

短程乘子 `m(S) = e^{w17·(G−3+w18)}·S^{−w19}`（`G≥2` 时钳制 `≥1`）的**不动点**
`S* = (e^{w17·w18})^{1/w19} = 2.1210577` 天。`S < S*` 被推高、`S > S*` 乘子 <1 被钳回 1.0，
所以**任意多次同日成功最多把 S 推到 `max(S₀, S*)`**——实测 200 次收敛到 2.1210517（与解析值差 6e-6）。
相对"聚合成一次"的倍率：`S₀=0.212` → 8.60×、`0.5` → 3.86×、`1.0` → 2.02×、`2.0` → 1.06×、
`≥2.121` → **1.00×**。

**换成用户看得见的**：`r*=0.9` 时 `I(r*,S)=S`（§2.3 已证），间隔取整、下限 1 天，所以
`S₀=0.212` 时"聚合成一次"给 **1 天**、反复同日成功最多给 **2 天**。**这条偏差在调度上只值一天。**

**验收（新增 `FsrsSameDayBoundTest`，6 条）**：全部走**产线路径** `FsrsMemoryUpdateModel.updateMemory`
（`elapsedCalendarDays = 0.0`），不是直接调 `FsrsScheduleMath`——这样连"哪些天数被当成同一天"也一并钉住。
六条分别钉：不动点处乘子**恰好为 1**、200 次同日成功**收敛到不动点且从不越过**（同时钉单调性）、
成熟卡同日再答对**一点不动**、三次 vs 一次的**倍率 1.3149878**、**调度后果恰好 1 天**、
同日失败**比聚合更保守**（0.0154 < 0.0834，即 spec 原注记成立的正是这半句）。

> **一个先验数值的独立复核**：上面所有数先是本记录作者用 Python 按公式独立算出来的，
> 再写进 Kotlin 用例**逐位断言到 1e-12**，两边**全部一致**——这等于用两种实现互证了同一组数，
> 而不是"跑过了就算对"。这也是为什么用例里那些常数是硬编码的字面量而不是现算的：
> **它们的作用就是把数值钉住**。

**变异（2 条，JVM）**

| 变异 | 命中（**集合相等**） | 失败消息（原文，节选） |
|---|---|---|
| M1 去掉 `G≥2` 的 `coerceAtLeast(1.0)` 钳制 | `{a card already above the fixed point…, FsrsScheduleMathTest.same day success never lowers stability, FsrsScheduleMathTest.short term stability matches py-fsrs composition}` | 成熟卡同日答对后 `S` 从 20 掉到 **17.25**——**这正是钳制在防的失败** |
| M2 把 `w19` 从 0.0658 改成 0.6（不动点移动） | `{three same day successes overshoot…, the scheduling consequence…, same day failures are handled more conservatively…, FsrsScheduleMathTest.short term stability matches py-fsrs composition}` | `expected:<0.24668918777567272> but was:<0.5649649406408761>` 等 |

> **M2 的预期集合第一版写错了，又是脚本把它纠回来的。** 我原以为参数一变，
> `same day successes cannot push stability past the fixed point` 会红。**它不会**——
> 那条用例的不动点是**从当前参数现算**的（`exp(w17·w18)^(1/w19)`），所以它验证的始终是
> "反复同日成功收敛到那个不动点、且不超过它"这条**关系**，而不是某个具体数值。
> **这是刻意的**：F-01／S-10 之后参数会真的开始移动，一条钉死 2.1211 的用例会在第一次
> 成功拟合时变红，从而**挡住一次正当的自适应**。分工因此是：
> **那一条守"有上界"，另三条数值用例守"上界是这个数"**，两条各守一侧、谁也不替代谁。
> 与 F-01 的 M6/M7、F-02 的 M2 是同一类读数纪律——**把观察到的集合改成与预期相符才是要防的错**。

**顺带纠正了原判对 spec 的读法（这才是本条真正的结论）。** §2.15 的那一条由**两句话**组成：
①"同卡同日多条证据聚合成一条当日评级（G_agg=min），日终/次日首开时落 FSRS 更新"；
②"当日首学与**同日重复**走 short_term 分支"。**若②照字面执行，①里"日终落地的那一次更新"
就没有独立事件可落**——两句话不唯一地合成一个算法。实现照字面执行②，并按①的**时点**
（长程更新落在次日首开）而非①的**评级来源**（用的是那次复习自己的评级，不是前一日全部证据的 `min`）。
所以**真实的偏差只有一条：跨日更新的评级来源**；同日重复本身按②是规格要求的，不是偏差。

**为什么不按①实现"日终聚合"**：①的"日终落地"需要**日界**这个概念，而事件溯源账本里
**没有日界事件**——投影由事件驱动、确定性重放，没有"今天结束了"这样一条输入。照字面实现要引入
新的日界事件类型（或改成读侧聚合），并随之做**投影版本升级＋全量重放**，代价落在每一次现网安装上，
换来的是"首次间隔 1 天而不是 2 天"。**故本项按订正说明结清、保留现有行为**；成本与影响面
已由上面的数字界定，将来若要改成规格字面语义，可据此直接评估。

**改动落点**：`docs/specs/mastery-scheduling-spec.md` §10 的「同日语义」实现注记整段重写
（含不动点推导、倍率表、可达性核实、以及"为什么不实现"）。**产线代码零改动**——
本项**没有**改变任何调度行为，这正是"先算再决定"的结果。**不需要投影版本升级、不需要重放。**

### 批 4　门禁与清理【低本】

- 性能门填真实播种，或改成明确的 `NOT_MEASURED`（**现状是空种子下的三条恒真断言**）。
- `:core:ui:testDebugUnitTest` 补进 CI 单测清单（**注意 `core:domain` 的任务名是 `:core:domain:test`，不是 `testDebugUnitTest`**——枚举式清单的陷阱）。
  **✅ 2026-09-13 已完成**：`.github/workflows/android-check.yml` 的 `Unit tests` 步加上了
  `:core:ui:testDebugUnitTest`，并在注释里写明这份清单**两个方向都会出错**、以及核对办法。
  验证：**该步的完整命令逐字重跑 → `BUILD SUCCESSFUL`**（17 个任务全部解析并通过）；
  YAML 用 `yaml.safe_load` 复核过折叠标量（`>`）拼出来的就是那一行命令。
  **2026-09-13 实跑核实：这一项是真的，且漏掉的不是空任务**——`:core:ui` 有 **11 条**用例，
  CI 的清单里此前没有它。
  > **同一次核对还带出一条新的**：CI 清单里的 **`:knowledge-production:test`** 在该模块
  > `src/test` 下**0 个测试文件**——**它是一条什么都不跑的空任务**。
  > 所以那张清单的问题不是"漏了一个名字"，而是**两个方向同时存在**：漏了有测试的
  > （`:core:ui`），也列了没测试的（`:knowledge-production`）。
  > **这一条本次不动**：留着一个无害的空任务，比删掉它更不容易误伤"以后要在这里加测试"的意图；
  > 真正的修法是让清单由**实际存在的测试源集**生成或校验——枚举式清单的两种失效方向它都防不住。
  > 这与 §7.5 记的同一条纪律是同一件事，登记为**仍未做**。
- 两个 `Skeleton` 测试加断言，或标记为待实现。
  **✅ 已完成（2026-09-13）**：三个空体 `@Test` 换成三条有断言的启动恢复用例，
  覆盖审计点名的两条无等价断言的恢复分支；现场用**生产**的 `RestoreJournal` 留、
  两代夹具内容刻意可分辨。**三次变异，预测的变红集合与实际逐次相等**；
  `:core:data:connectedDebugAndroidTest` 该类 **16/16 通过、0 skipped**。
  完整记录见 **§5.5.3**（真进程死亡在本测试宿主里无法自动化，理由与残余缺口都写在里面）。
- 迁移矩阵加数据断言（现在对每个版本只断言空库计数为 0）。
  **✅ 已完成（2026-09-13）**：按**每一版自己的列形状**播种一行最小错题，迁移后断言
  目录仍是那一行、题面仍是那一句；44 个版本实跑 215 秒通过。
  **一次变异**（把 31→32 改成 `DELETE FROM problem_revision`）如期变红并点名版本——
  **同一句话对旧的 `assertEquals(0, …)` 永远成立**，这就是本条要修的东西。
  **并带出一条新发现 N-18（升级上来的库永久搜不到，已修）**——见 §12.5。
- 补 drainer 版本触发重放测试（若批 0 已完成，此项并入）。
  **✅ 已并入批 0 第 1 项**（批 0 五项全部落地，见上）。
- 执行 §10 的 R-01～R-13。
  **部分已随前置批次落地**：**R-04 部分了结**（随批 3 第 3 项）、**R-10 完成**（随批 2）、
  **R-03 完成**（本轮，含追补的网格用例）。
  其余（R-01／R-02／R-05／R-06／R-07／R-08／R-09／R-11／R-12／R-13）**仍未开始**。

> **本批状态：部分开始（2026-09-13）**——**CI 单测清单那一项已完成并验证**（见上），
> 另两条由前置批次结清（drainer 版本触发测试并入批 0；R-04 部分、R-10 完成）。其余待办。
> 两处合并建议（**是"同一类决定"，不是同一片代码**）：
> **R-07**（四个校准接口）与**批 2 第 2 项**（修正账本）是同一种二选一——「接线，或明确下线并写进文档」——
> 值得用同一套判据一次裁定，免得一边接了线、另一边还挂着"我们有标定"这句无法验证的话；
> **R-06／R-05／R-09**（死代码、死常量、占位列）宜合并成一次清理动作，避免同一片代码改三遍。

### 一条贯穿五批的纪律

> **新增的每条机制都要带一条证明它在生产路径上被消费的测试。**

模式 A 的 11 个实例、B 的 2 个、F 的 3 个，全部可以在这一条纪律下被提前拦住。
这比任何单条修复都更接近「生产级」这个词的实际含义。

---

## 12. 覆盖缺口与未做项

### 12.1 审计本身的缺口

| 缺口 | 说明 |
|---|---|
| **对抗复核有 4 个维度缺口** | 测试门禁 · 调度数学 · 持久化并发 · 架构边界。原因是子智能体所用模型的 API 流错误（基础设施故障，非逻辑问题），重试仍失败。承重项已由主循环一手补验，但**没有第二个独立视角**。 |
| **未运行任何构建或测试** | 本记录不含「跑过之后通过」的证据。所有「已有测试覆盖」的判断来自**阅读**测试源码，不是执行结果。 |
| **未在真实设备或仪器化环境验证** | 涉及时间、并发、进程死亡的结论（F-02、恢复路径、切分崩溃）均为源码级推证。**（2026-09-12 批 1 订正：这台机器上其实跑得动仪器化测试——SDK 在 `C:\Android\Sdk`，AVD `test_device` 已启动，`adb devices` 报 `emulator-5580 device`。批 1 的 1.1／1.3／1.4 因此都拿到了真机 Room 证据，见 §11 批 1 执行状态。原文那句只对**审计当期**成立：当时没有尝试 `connectedAndroidTest`。）** |
| **未提交的工作树改动只做了部分比对** | 已逐文件 `git diff` 比对过与在册条目相关的那些改动（结果见 §12.3）；但并发会话其余改动只做了**是否触及缺陷本身**的判断，未逐条审计其正确性。`.worktrees/` 与 `smb-toolloop/` 下的同名副本也未审计（同名副本会让 grep 命中残留路径——读源码前需确认路径在主树）。 |
| **未做端到端产品验证** | 本记录回答「代码层是否达到生产级」，**不回答「学习效果是否真的提升」**——后者需要真实用户数据与对照实验。 |
| **2 条 UNCERTAIN 不得当结论用** | `shadow-predictions-discarded`（后果被证伪，降为 P3 死代码）与 `bounded-drain-throws-on-backlog`（触发条件 `>6400` 条未证实）。既不能据此宣称功能失效，也不能据此宣称可用。 |
| **9 条复核者漏项未经任何二次验证** | 见 §8。方向可信，细节动手前请自己过一遍。**其中「视觉通道 `delta_t` 恒 0」最值得优先复核。** |

### 12.2 实施隔离：D-3 已定（2026-09-12）

**决策 D-3 ＝ 从已提交 HEAD 开 worktree。** 已执行：

- worktree：`D:/smart mistake book/.worktrees/kernel-readiness`（`.worktrees/` 已在 `.git/info/exclude` 里，不污染主树状态）
- 分支：`audit/kernel-readiness`，基线 `dc12065`（＝当日 `main` 的 HEAD；注意它比 `origin/main` **领先 6 个提交**，
  所以必须按本地 HEAD 分支，不能按默认的 `origin/<default>`）
- 基线工作树**干净**（0 个改动），并发会话的未提交改动**不在**这条分支上

**代价已接受**：并发会话对 `MasteryWriteGate`（新增 `verifiedEvidenceAnchorCount` 真实子串核对）与
`RoomTutorToolRunner` 的改动不在基线内，而批 1.5 恰好要动同一条证据门——两个会话的工作最终需要一次合并决定，
合并顺序会影响批 1.5 的写法。

**因此仍维持的分工**：批 0 与批 1 的 1.1/1.2/1.3/1.4 与并发会话的文件无交集，可以先做；
**批 1.5（工具 `confidence` 缺省）与批 3 需要等那个会话落地后再定**。

### 12.3 并发工作树对本记录的影响（逐条核对，2026-09-12）

写成本记录之后，把并发会话**未提交工作树**的每个改动逐一与在册条目比对了一遍。
比对方法：对每个受影响文件跑 `git diff`（工作树 vs 已提交 HEAD），读完整 diff 文本，判断它是
「结清了某条在册条目」「改到了同一文件但没碰缺陷」还是「与本记录无交集」。
**已提交 HEAD 是判据**；只有当工作树的改动确实落在缺陷本身上时，才把该条移出待修清单。

> ### ⚠ 2026-09-12 晚订正：那份「未提交工作树」**已经落地成提交**，`main` 前进了 12 个提交
>
> 本节的前提（并发会话的工作**未提交**）在批 1 施工期间已经失效。复核时重新查了分支状态（第一手）：
>
> ```
> $ git -C "D:\smart mistake book" rev-parse HEAD
>   4c76125781d25c12d34745d6b4b638d0aa193f53        branch main
> $ git -C ".worktrees\kernel-readiness" rev-parse HEAD
>   dc120651faab4abab1a6b320031ad3bb3e8811c7        branch audit/kernel-readiness
> $ git merge-base --is-ancestor dc12065 4c76125  → 是（本记录的基线是 main 的祖先，未分叉）
> $ git rev-list --count dc12065..4c76125          → 12
> ```
>
> 关键提交：`d49296d feat(mastery): 证据锚必须真实出现在会话文本，MASTERED 不再能靠编造引号通过`
> （**这正是 §13.2 记的 `mastered-anchor-unverified`**，所以那条的「未提交工作树里已修好」应改读为
> **已提交**）；`c96cc4d`(KD-7)、`16fdbba`(KD-12+KD-10)、`4b08f64`(KD-8+KD-9+KD-11)、`4904fe2`(KD-11 余下)、
> `cd70d1a`(KD-13 库模块 lint 入 CI)、`4c76125 docs(defects): KD-1 与 KD-4 收口，**登记簿清零**`
> （`docs/known-defects.md` 的自有登记簿声称清零；本记录的条目**不**随之清零，两者口径不同）。
>
> **对本记录的逐条影响**：
>
> 1. **判据不变、时态要改**：本记录全部行号与结论、以及批 0／批 1 的施工，都基于 `dc12065`。
>    凡本节或 §13.2 写「并发树**未提交**工作树已修」的，现在是「**已提交**（见上列提交）」。
> 2. **本批改动与这 12 个提交**逐一核对过（`git status --short` 的每个文件 × `git diff --name-only dc12065..4c76125`）：
>    当时**只有 2 个文件重叠**。**（2026-09-12 批 1 收尾时重算：现在是 4 个——1.6 与 1.7 各引入一个新的，见下。）**
>    - `app/src/main/kotlin/.../SmartMistakeBookApplication.kt`——main 改了 54 行（`BackupRestoreStartupRecovery`
>      的启动恢复接线），本记录批 0 第 5 项也改了它（`runStartupInitialization` 的接线）。
>    - `core/data/.../study/RoomTutorToolRunner.kt`——main 改了 58 行（`verifiedEvidenceAnchorCount` +
>      `verifiableSessionText`），本记录批 1 第 1.3 项只改了 `masteryUpdate` 里的一处注释。
>    - **`core/database/.../RoomStudyDatabase.kt`（1.7 新增的重叠）**——main 在这 12 个提交里改过它
>      （备份支撑与库检索相关），1.7 改的是 `readPracticeUnitKnowledgeBindings` 的映射一处（加 `isPseudoFallback`）。
>    - **`docs/specs/mastery-scheduling-spec.md`（1.6 新增的重叠）**——main 改过这份 spec，1.6 在同一份文件里
>      新增 §2.8.1 并给 §2.8 加作废块。**这是唯一一处文档重叠**，且两份改动都在「归因/分摊」议题附近，
>      **合并时必须逐字读，不能假定自动合并无害**。
>    **四处都不同段**，但合并时必须人工确认合并结果。
>    复算命令（在本 worktree 里跑，`main` 的提交区间用绝对区间而不是 `HEAD`）：
>    `git diff --name-only dc12065 4c76125 | sort > /tmp/peer.txt && { git diff --name-only dc12065; git ls-files --others --exclude-standard; } | sort -u > /tmp/mine.txt && comm -12 /tmp/peer.txt /tmp/mine.txt`
> 3. **验证基线要说清**：本批全部验证（`:core:domain:test`、`:core:data:testDebugUnitTest`、
>    `:core:data:connectedDebugAndroidTest` 的 6 例、`:app:assembleLocalFirstDebug`）跑在
>    **`dc12065` + 本批改动**上。**合并到 `main` 之后必须在新基线上重跑**——这 12 个提交里有
>    `MasteryWriteGate` 与 `RoomTutorToolRunner` 的行为改动，属于会与写闸路径相交的那一类。
> 4. **`main` 自带一处轻微编辑残留**：`d49296d` 在 `RoomTutorToolRunner.kt` 把同一段注释写了两遍
>    （「只数**引文真出现在本会话文本里**的锚」下面 4 行，两份措辞略有不同）。**无行为影响**，
>    记在这里以免后来者以为是本记录的笔误。
> 5. **建议（需用户决定，不在本批范围内）**：批 1 收尾后把 `audit/kernel-readiness` **变基到 `4c76125`**
>    再合并，并在变基后的基线上重跑第 3 条列出的命令。
>

#### 已被并发树结清（从待修清单移出，计 1 条）

| 在册条目 | 承重位置 | 并发树的处置 |
|---|---|---|
| `catalog-retrievability-always-null`（维 07 `P2`，见 §5.7.2） | `LibraryCatalogView.kt:20` 的 `NULL AS retrievability` 使 `LEAST_MASTERED` 排序静默退化为按 `updated_at DESC` | **已结清**，即并发会话的 `KD-10 (resolved 2026-09-12)`。新增 `core/database/.../LibraryCatalogSorts.kt` 的 `LEAST_MASTERED_MASTERY_SQL`，对 `catalog` 别名做关联子查询取该行绑定知识点的最小 `lower_bound_independent_correct`（**无需 schema 变更**）；因 KSP 拒绝跨文件常量，`LibraryQueryDao.kt` 内另有一份同名的**同文件** `private const`；新增 `LibraryLeastMasteredSortInstrumentedTest`（3 例，且做了变异校验：把 FTS 侧改回 `catalog.retrievability` 只让 FTS 那一例变红）。 |

> 这是本记录中**第二条**因并发工作树而过期的条目（第一条是 §13.2 的 `mastered-anchor-unverified`）。
> 教训在 §13.2 已写明，此处再次实证：**未提交的并发工作树会让已提交代码的结论过期**，边界必须声明。

#### 维持成立，但需订正或补充（计 5 条）

**（1）`core-ui-tests-never-run`（维 05 `P1`，§5.5.1）——维持，且比原记录更严重；同时订正一处行号引用（记作 S-7）**

原记录的证据段写「`:core:ui` 在整个工作流里只出现在 lint 列表（第 88 行 `:core:ui:lintDebug`）」。
**那一行来自工作树，不是已提交状态**——审计 agent 读的是磁盘上的文件，而并发会话已经把那一行加了进去。
按已提交 HEAD 重新核对的结果是：

| 检查 | 已提交 HEAD | 并发工作树 |
|---|---|---|
| 单测步骤是否含 `:core:ui` | 否 | **否**（`git diff` 对 `testDebugUnitTest` 零命中，该步骤未被改动） |
| lint 步骤 | 只有 `./gradlew lintLocalFirstDebug lintStrictOfflineDebug`（第 79 行），**无任何模块级 lint** | 补了 10 个模块的 `lintDebug`（含 `:core:ui:lintDebug`） |
| 全仓 workflow 是否有 `:core:ui` | **一次都不出现**（`android-check.yml`、`schema-export.yml`、`status-template.md` 全部零命中） | 只在 lint 步骤出现一次 |

所以本条的准确表述是：**已提交状态下 `core:ui` 既不在单测清单、也不在 lint 清单**——
`core/ui/src/test` 下的 3 个测试文件（`AiReplyRichMarkdownTest` 4 个 ＋ `AttachedImageCardTest` 4 个 ＋
`ThinkingCollapsibleCardTest` 3 个，共 **11 个 `@Test`**）既不编译也不运行，其 lint 发现同样无人看。
§11 批 4 的处置（补 `:core:ui:testDebugUnitTest`）**不变，且仍未完成**。

> **这条同时是 §9 模式 F 的第二个实例，而且是最干净的一个。**
> 并发会话当天修掉的 `KD-13`，与本条是**同一个失败类**——那份工作流自己的注释写得很清楚：
> 「same reason the unit-test step lists them: **a module that is not named is never checked**」。
> 它按同一份清单补了 lint 那一半，却没补单测那一半——**同一天、同一份文件、同一句注释所描述的同一类失败**。
> 这反过来强化了 §11 那条贯穿纪律，也说明「枚举式清单」这种形状本身就应当被一条 parity 测试锁住，而不是靠人记得两边都加。

订正明细（S-7）：§5.5.1 证据段的「第 88 行 `:core:ui:lintDebug`」应改为
「已提交 HEAD 版**完全不出现** `:core:ui`；第 88 行那句是并发工作树新增的 lint 清单」；
判定矩阵（第 44 行）的「11 个测试文件」已订正为「3 个测试文件（11 个 `@Test`）」。

**（2）`perf-gate-empty-seed`（维 05 `P1`，§5.5.2）——维持**

`PerformanceGateTest.kt` 的工作树改动**只有** `useConnection` → `withRawConnection`（即并发会话的 `KD-12` 重命名）。
`insertTestData` 仍是占位，空种子下的三条恒真断言逐字未动。

**（3）`cleardata-fk-off-leak`（维 07 `P3`，§5.7.7）——维持**

`RoomBackupSupportStore.kt` 的工作树改动共 4 处，**全部**是 `useConnection` → `withRawConnection`（同样是 `KD-12`）。
`clearAllData` 里「`PRAGMA foreign_keys = OFF` 之后中途抛异常则 `ON` 永不执行」的逻辑逐字未动。

**（4）`restore-failure-closes-db-no-reopen`（维 07 `P2`，§5.7.3）——维持，且与并发会话的新改动是两条不同的路径**

并发会话的 `KD-9 (resolved 2026-09-12)` 确实动了恢复失败的用户可见性，但落在**另一条路径**上：

| | 并发会话 `KD-9` 修的路径 | 本条讲的路径 |
|---|---|---|
| 入口 | `BackupRestoreStartupRecovery.recoverOnStartup()`（**启动**时自动跑） | `AndroidBackupRepository.doRestore()`（**用户主动**恢复） |
| 新机制 | `RestoreRecoveryCoordinator.kt` 新增 `RestoreRecoveryAttention` 与 `attentionRequired()`；`SmartMistakeBookApplication.kt:269-277` 在 `Ready` 之后把状态覆盖为 `RecoverableFailure` | 无改动 |
| 缺陷是否被触及 | 否 | `doRestore` 的 catch 分支仍只做回滚与 `throw failure`，**不重开数据库**；`SecondaryScreens.kt:731` 的失败提示仍不含重启指引 |

所以两条**并存**，不是同一条。§12.2 里「批 1.5 与批 3 需等并发会话落地」的判断也不受影响。

**（5）配图通道不遵守用户配置的 `modelId`（§8 漏项 7）——⛔ 本条已作废，见 §3 S-8（2026-09-12 订正）**

此处原先判「维持」，只核对了「并发树对 `OpenAiImageGenerationChannel.kt` 的改动与 `modelId` 传递无关」这一层。
**但那条 finding 本身就不成立**——回读 `docs/image-pipeline-spec.md:41-47` 后确认「配图模型与对话模型拆开」
是**已记录的设计决定**，而原修复方案（把对话模型 id 传给 `/v1/images/edits`）会打断当前能用的 OpenAI 配置。
作废依据与残留缺口（**N-11**）见 §3 **S-8**。这一条**不再属于待修清单**。

`OpenAiImageGenerationChannel.kt` 的工作树改动只有 `java.util.Base64.getDecoder().decode(b64)` →
okio 的 `b64.decodeBase64()?.toByteArray()`（并发会话的 `KD-11`，理由是 `java.util.Base64` 需 API 26 而 `minSdk` 是 23），
与 `modelId` 的传递无关。

#### 与本记录无交集（3 项，仅备案）

- `ModelEgress.kt`：唯一改动是 `TUTOR_RESPOND` 的 prompt 版本号
  `tutor-respond-v10-reteach-material-priority` → `tutor-respond-v11-verifiable-anchor`。
- `AndroidBackupRepository.kt`：`KD-8`（Keystore 别名改为引用 vault 的 `MODEL_SECRET_KEY_ALIAS`、不再按前缀扫）
  ＋ `ContextCompat.getDataDir` 替换 `context.dataDir`（API 24 起才存在）。本条不在 §3.1 的 56 条内。
- `feature/common/src/main/kotlin/.../accessibility/AccessibilityExtensions.kt`：**被删除**。
  已核实该文件是**孤儿**——无任何 Gradle 模块 `include` 它、无任何 `srcDir` 指向它、
  `AccessibilityExtensions` 在主树内零引用，从未参与编译。（顺带说明：`settings.gradle.kts` 实际是
  **17 个模块**，`feature/common` 不在其中；本记录各处的「17 模块」无误。）

#### 处置纪律（自本次比对起生效）

1. **已被并发树结清的条目** → 标注「已被并发树结清、已从待修清单移出」，并把并发会话的缺陷编号（如 `KD-10`）记在旁边，便于回溯。
2. **文件被并发树改过但缺陷本身未变** → 明确写「文件被并发树改动，缺陷未变」并给出该改动的真实内容，
   免得后来者看到 diff 就以为已修。
3. **任何在并发树落地后仍要动手的修复** → **先重读一次工作树的当前内容再改**，不要沿用本记录里的行号；
   本记录的行号全部对应当日已提交 HEAD。

### 12.4 批 0 第 3 项的原方案不成立：**全量重放不能分块**（2026-09-12，动手前发现）

§11 批 0 第 3 项原文是「`commitFullReplay` 加条数上界**与分块**；超限时明确失败并给出可恢复路径」。
按第 3 条（工程事实闭包）读 `LearningProjector.replay` 之后确认：**「分块」这一半是错的，做下去会产生静默错误结果。**

**为什么。** `replay` 对整份账本走**两遍**：

1. 第一遍（`LearningProjector.kt:398`）只做收集与校验——建立 `attemptsById`、`presentationOrdinals`、
   `answerRevealSequences`，并把**每一条** `AttemptCorrection` 收进 `corrections[attemptId]`（`:414-418`）。
2. 第二遍（`:433`）才真正投影，而在处理某个 `Attempt` 时取的是
   `corrections[event.attemptId]`（`:437`，用于替换 `evidence` 与 `problemMemoryOutcome`，`:442`）——
   即**用整份账本里后出现的修正，去改写早先那条作答的证据与记忆结果**。

所以修正是**追溯生效**的：账本 `[attempt@1, correction@2]` 里，序列 1 的那次作答是按被修正后的证据投影的。
若把重放切成前缀块、逐块提交中间 checkpoint，则「先重放 `[attempt@1]`、再增量吃 `correction@2`」得到的
**不是**同一个结果——前者的 attempt@1 已经带着未修正的证据落库了。**前缀重放 ≠ 单次全量重放。**

**还有第二个、更硬的原因**（2026-09-12 补验）：修正**根本进不了增量路径**。
`AttemptCorrection` 不是 `IncrementalLearningEvent`（`LearningState.kt:241-242` 的 sealed 层级里它只实现
`LearningLedgerEvent`），而批次加载器一旦读到修正行就**直接返回 `FULL_REPLAY_REQUIRED`**、
不把那批事件交出去（`ProjectionTransactionDao.kt:475-486`，附文
`"Correction ${row.eventId} requires a full ledger replay"`）；drainer 拿到这个停止原因也直接走全量重放
（`StudyProjectionDrainer.kt:58-66`），从不调用增量投影。

于是分块重放在修正处不是「结果不同」，而是**根本推不过那个切分点**——它会反复拿到同一条
「要求全量重放」的停止原因。**分块在这里既不正确、也不可行。**

（**订正，2026-09-12 稍后**：我曾在此推断「修正行必然触发全量重放，所以每一次修正都会引发一次无界的全量重放，
这使第 3 项眼下就生效」。**这条推断在前提上不成立**——已核实 `appendAttemptCorrection` 在生产里
**没有任何调用者**（全仓只有端口声明 `LearningProjectionPort.kt:58`、实现 `RoomStudyDatabase.kt:1122`
与测试），即修正根本不会被写进生产账本。所以全量重放眼下**仍然只有投影版本变化这一个触发源**，
与 D-1 的原判一致。本节的分块结论不受影响（它讲的是机制的正确性，不是眼下的触发频率）；
第 3 项的真实紧迫性来自另一件事——账本无界且永不裁剪，见 §12.6。）

这正好落在第 12.1 条要防的那类错误上（与 S-1 同源）：**修复方案本身没先闭合影响面**。
若照原方案实现，它会通过所有现有测试，并在「账本里存在修正」的库上给出与单次重放不同的投影。

**重新定形（建议，待确认）：**

- **保留上界，删掉分块。** 超限即**显式失败**，不复用语义错误的 `ProjectionCasConflictException`
  （§8 漏项 3），另立一个能自述的类型（如 `ProjectionReplayLimitExceededException`），
  携带账本实际条数与上界值。
- **失败后的处置**：**保留旧 checkpoint 不提交**，把库置为可诊断的失败态（与第 4 项的不变量校验同一出口），
  不做「部分重放」。用户数据不动、行为与「这次重放没发生」等价。
- **上界取值**依据：需要先量出真实安装的账本规模（本记录未测）。在量出来之前，
  上界只能取一个明显宽松的常数，其作用是**把无界同步操作变成一个可见的失败**，不是做性能调优。
- **顺序依赖**：本项**必须排在第 2 项（确定性测试）之后**，因为「前缀重放 + 增量续算是否等于单次全量重放」
  正是第 2 项那条测试要回答的问题。若那条测试证明两者逐字段相同（即修正可被增量路径等价吸收），
  分块才重新成为一个可选项；在它给出结论之前，分块**不做**。

### 12.5 追加发现（2026-09-12，批 0 的事实核查带出）

以下 **11 条**（N-01…N-11）**不在** §5 的 56 条登记内——它们是在为批 0 第 3、4、5 项定位「失败该从
哪里冒出来」时读出来的。N-01…N-05 的证据是一手读源码；**N-06 是一手探针实测**（见下）；
N-07…N-10 是批次施工过程中带出的一手核查；**N-11 是批 1 第 1.2 项核查时把一条旧判据推翻后剩下的真实缺口**
（见 §3 **S-8**）。每条都注明了带出它的那一项。

#### N-01　投影失败的详情**写了没人读**（`P3` · `dead-mechanism`）

`StudyExperienceSnapshot.failureMessage` 定义在 `core/domain/.../StudyExperienceRepository.kt:178`、
写入在 `RoomBackedStudyExperienceRepository.kt:1020`，而**全仓 `src/main` 里没有任何读取方**。
于是投影失败的原因被算出来、存进内存快照、然后丢掉——UI 只能拿到一个布尔。
这是 §9 模式 A 的又一实例（机制建成但无消费者）。

> **未实现（D-12，`P3`）。** 批 0 第 5 项只登记不修：删字段与接上界面是两条路，都要先决定
> 「投影失败的原因该给谁看」。本次改动走的是另一条载体（启动横幅的标题＋文案＋编号），
> 没有碰 `failureMessage`。

#### N-02　启动期的投影失败被告诉用户是「知识包没准备好」（`P2` · `correctness`）

`initialize()`（`RoomBackedStudyExperienceRepository.kt:344-378`）末尾会经 `publishReadySnapshot` →
`StudySnapshotBuilder` → `currentLearnerSnapshot()` → `drain()`；`runOperation` 捕到后**重抛**
（`:1009`），抛到 `SmartMistakeBookApplication.kt:304` 的 `catch (failure: Throwable)`，
在那里被写成 `StartupState.RecoverableFailure(..., errorCategory = KNOWLEDGE_BASE,
title = "本地知识包尚未准备好", message = "错题和复习可以继续使用，自动分类会暂缓。")`。

**即：账本/投影出了问题，用户被告知的是知识包的问题，并且被告知「可以继续使用」。**
这条比 N-01 严重：它不是信息缺失，是**信息错误**——它会让学生和排查者都看向错的方向。

> **已修（2026-09-12，批 0 第 5 项，D-17）。** 归类与文案抽成 `StartupFailureMessages.kt` 的纯函数，
> 归属抽成 `StartupInitialization.kt` 的 `runStartupInitialization`（10 条变异验证，其中把两个 catch
> 合回去的那条只被归属用例抓住）。连带消掉一处同源的**假耦合**：HEAD 上 `install()` 抛了就永远走不到
> `initialize()`，知识包失败会冻住学习进度。

#### N-03　两条未包裹的入口可能让投影异常直接崩掉界面（`P2` · `correctness`）

`reTeachOpening()`（`:474`）与 `prerequisiteRemediation()`（`:501`）**没有**走 `runOperation`，
而 `SmartMistakeBookDestinations.kt:98-133` 的 `produceState` 块在 `:115`、`:122` 调它们，**块内无 try/catch**。
drain 的异常由此逃进 composition 的协程。*推断*：该作用域未安装 `CoroutineExceptionHandler`，
因此会落到主线程处理器并崩溃（对比：`applicationScope` 装了日志兜底 `SmartMistakeBookApplication.kt:73-77`，
所以后台循环上的同类异常只记日志）。**崩溃后果是推断的，未在设备上复现。**

> **已修（2026-09-12，批 1 与 N-06 同批）。** 两个调用点改走 `loadOptionalSessionCard`：
> 失败降级为「本题没有这张卡」，同时经 `onFailure` **留下记录**，**取消照旧上抛**。
> 四条 JVM 断言（3 个方向 ＋ 取消），做法与证据见 §11「N-03」。**登记时的推断部分保持不变**——
> 「异常会逃出这两个调用点」已由一手源码核实（两条都经 `currentLearnerSnapshot()` 调 `drain()`，
> 且都不走 `runOperation`），但「逃出去即崩溃」仍未在设备上复现，也没有端到端测试。

#### N-04　`LearningLedgerIntegrityException` 全仓无人捕获（`P2` · `missing-handling`）

只被抛出（`StudyProjectionDrainer.kt:54,146` 等），**没有任何 `catch` 子句指名它**。
于是「账本损坏」这件事没有任何专用处置路径，只能走通用 `Throwable` 通道，
其效果与下面 N-05 描述的通用兜底相同。**这也直接决定了批 0 第 3 项的新异常不能只是「再抛一个」**——
它落到的出口与账本损坏完全相同，而那条出口现在既没有正确的用户话术、也没有持久化的健康状态。

> **已修（2026-09-12，批 0 第 5 项）。** `projectionFailure` **指名**捕获它，给出专属标题／文案／
> 编号前缀（`startup:ledger:`），并且**不提供重试**（D-18：它的输入在同一版本内不会变）。
> 但要注意类型本身不够精确——26 处抛出点里只有 2 处关于学习账本，见 **N-09**。

#### N-05　没有任何**持久化**的投影健康状态，也没有诊断面（`P3` · `missing-observability`）

- `StudyDataStatus`（`StudyExperienceRepository.kt:14-18`）只有 `LOADING/READY/ERROR`，**只在内存里**
  （`MutableStateFlow`，`RoomBackedStudyExperienceRepository.kt:115`），写点只有两处。
- `ProjectionStatus` **是**持久化的（随快照落库），但它记的是投影器成功提交后的状态，**从不记 drain 的异常**。
- 全仓不存在 `*Diagnostic*` 文件；最接近的诊断面是启动横幅，它只显示 `diagnosticId`。
- 用户能看到的最具体的话是 `StudyDataStatusLine`（`SmartMistakeBookRoot.kt:919-935`）在 `ERROR` 时的
  「本机学习数据暂时无法更新；不会用空白结果替代已有记录。」——**没有原因、没有编号**。

**对批 0 第 4 项的含义**：`D-7` 定了「不过就保留旧 checkpoint 不提交」，但**「然后呢」目前没有诚实的落点**。
现状可用的三个出口都有毛病：启动期的 `RecoverableFailure` 会**说错原因**（N-02），
通用 `ERROR` 只说一句没有信息的套话（N-05），而未包裹入口可能直接崩（N-03）。
所以第 4 项的「失败可见」这一半，不只是一个接线问题。

> **部分已修（2026-09-12，批 0 第 5 项）。** 三个出口里**启动期那一个**现在说对了话，
> 并且带一个**四类互不相同**的诊断编号前缀（用户能看到它）。剩下两条仍在：
> ①`StudyDataStatus.ERROR` 那条路径仍然只说套话（没有原因、没有编号）；
> ②**没有任何持久化的投影健康状态**——本次改动只在启动失败时说话，投影在运行中失败
> （`refreshStudyExperience` 那条路）仍然只落到套话上。这两条**保持登记**。
> **（2026-09-12 批 1 N-06：前缀为**五**类，新增 `startup:drain-budget:`。①②两条不变。）**

#### N-06　排空步数上界被报成「CAS 冲突」（`P2` · `correctness`，2026-09-12 批 0 第 2 项带出）

**取证方式**（第一手，临时探针跑完即删；账本 6_401 条、生产页大小 100）：

```
PROBE: ledger events = 6401, production page size = 100, drain steps = 64
PROBE: threw com.tingyun.smartmistakebook.core.database.ProjectionCasConflictException:
       Projection did not drain within the bounded work limit
PROBE: commits before the throw = 64
PROBE: checkpoint after the throw = 6400
```

- `StudyProjectionDrainer.drain` 用 `repeat(MAX_PROJECTION_DRAIN_STEPS = 64)` 包住提交循环，
  循环走完仍未追平就抛 `ProjectionCasConflictException`。**64 页 × 100 条 = 6_400 条的积压**即触发。
- 触发时 64 次提交**已经落库**（检查点已到 6_400），抛出的却是一个断言「检查点在排空途中被别的写者改过」
  的异常——而它在本次调用里根本没变过。
- 后果：`runOperation` 捕获它、发一次失败事件、再抛给调用方。用户看到一次「投影更新失败」，
  而 6_400 条其实已经投影成功；下一次调用从 6_400 接着走。**工作没丢，但原因是假的**，
  而且它对**正常使用的重度用户**可达（每天 20 题 ≈ 一年 7_300 条 > 6_400）。
- 与 §8 漏项 3 是**同一族但不同处**：那处是"账本太大要做全量重放"，这处是"一次排空的预算用完"。
  两者都不该借用 CAS 冲突的语义。

**处置**：登记为**批 1 第一条**，与 N-03 一起做——两者都属于「失败出口说错原因」，
而批 0 第 4 项正在修出口本身；出口修好之前再抛一个新异常，只会落到同一个说错原因的地方。
> **2026-09-12 更新**：出口**已经修好**（§11 批 0 第 5 项：`StartupFailureMessages` +
> `StartupInitialization`，10 条变异验证）。N-06 的前提因此具备，仍排**批 1 第一条**——
> 但要注意它不能照抄第 3 项的做法：第 3 项的上界是「这次升级处理不了这么多条」，
> N-06 是「一次排空的预算用完、下次接着走」，后者**是可重试的**，不该被说成永久失败。
>
> **已修（2026-09-12，批 1，与 N-03 同批）。** 三处改动：
> ① `core:database` 新增 `ProjectionDrainBudgetExhaustedException`（**不继承** CAS 冲突那一族）；
> ② `StudyProjectionDrainer` 的步数上界改成可注入（产线值仍是 64，注入只为让边界行为可测，
> 与 `maxFullReplayEvents` 同一个理由）；
> ③ `projectionFailure` 新增**第五个**分支，`startup:drain-budget:` 前缀、**`retryable = true`**（显式写出，
> 因为它与上面两条投影分支的判断相反）。做法、5 条变异与用法见 §11「N-06」。
> **本条不关闭 N-05 的②**：运行中（`refreshStudyExperience`）的排空预算用尽仍只落到 `StudyDataStatus.ERROR` 的套话上。

---

#### N-07　`replay` 在内存里造一份没人读的预测列表（`P2` · `dead-mechanism`，2026-09-12 批 0 第 4 项带出）

**取证方式**（第一手）：全仓搜索 `LearningProjectionResult.predictions` 字段的读取方——**零命中**。

```
$ grep -rn "\.predictions" --include=*.kt core app feature | grep -v /build/     → 空
```

- `LearningProjector.replay` 在 `:557` 调 `generatePredictions(snapshot, ordered, projectedAt)`
  （`:1140`），对**每一条 Attempt × 每一个归因**构造一个 `StudentModelPrediction`，
  连同 `computeFeatureFingerprint` 的字符串拼接。
- 这些对象**只被塞进 `LearningProjectionResult.predictions`，没有任何读取方**；
  `commitProjection` 也没有承接它的参数。真在用的预测审计是另一条链
  （`RoomPredictionAuditSink` → `recordStudentModelPredictions`），与它无关。
- **后果**：它是在**重放**这条路上分配内存（O(作答数 × 归因数)，且随账本增长），
  而重放正是刚刚设了上界的那条路——等于让上界要挡的内存峰值里多背了一块纯浪费。
  它同时让 `replay` 与 `project` 的结果在 `predictions` 上必然不同（一个非空、一个空），
  任何将来比较 `LearningProjectionResult` 的人都会看到一个假的差异。

**处置**：`P2`（不是 `P3`：它在被设上界的路径上分配内存）。**推荐在批 4 删除整个
`generatePredictions` + `computeFeatureFingerprint` + 结果里那个字段**。删它是安全的
（零读取方），但动的是 `core:domain` 的公开类型，不属于批 0 的范围，因此只登记不动手。

---

#### N-08　`StartupErrorCategory` 是只写不读的字段（`P3` · `dead-mechanism`，2026-09-12 批 0 第 5 项带出）

**取证方式**（第一手）：全仓搜索 `errorCategory` 的读取方。

- 写入方 2 处（`FatalFailure` 的 `DATABASE`、本批新增的四类 `RecoverableFailure`）、
  默认值 1 处（`StartupErrorCategory.UNKNOWN`），**读取方 0 处**。
- `StartupStateBanner` 只渲染 `title` / `message` / `diagnosticId` 三样（`StartupStateBanner.kt:33-49`）。
- 因此**不能**靠新增一个 `PROJECTION` 枚举值来承载"哪一类数据出了问题"——加了也不会改变
  用户或排查者看到的任何东西，属于 §12.2 说的多余机制。

**处置**：`P3`。**本批不加枚举值**；批 0 第 5 项改用**诊断编号前缀**做面向人的归类载体
（横幅会显示它，因此四类前缀互不相同这一条是可断言、可观察的）。清理这一项时应当在
「删掉字段」与「把横幅接上它」之间选一个，不要留着只写不读。
> **2026-09-12 批 1 N-06 更新**：前缀从四类变五类（新增 `startup:drain-budget:`，写法与
> 「前缀互不相同」那条断言的清单同步扩充）。**这一项本身没有变化**——`errorCategory`
> 仍然只写不读，新分支照旧按既有的两个取值写。N-06 新增的 `retryable = true` 也不是新机制：
> 它显式写出的是**默认值**，为的是让三条投影分支的差别（前两条 `false`、这条 `true`）
> 在同一处可读，且不被将来改动默认值的重构悄悄改掉。

---

#### N-09　`LearningLedgerIntegrityException` 被当作通用「行内字段矛盾」异常使用（`P3` · `boundary`，2026-09-12 批 0 第 5 项带出）

**取证方式**（第一手）：枚举该异常的全部抛出点。

- **26 处产出抛点的调用**，其中**只有 2 处与学习账本有关**：
  `StudyProjectionDrainer.kt:84`（`commitFullReplay` 的状态检查）与 `:176`（排空上界）。
  **（2026-09-12 批 1 N-06 注：本节写于批 0，此后该文件加了导入、类注释与一个构造器参数，
  同一个文件里两处现在的行号是 `:93`（GAP／CONFLICT 分支）与 `:191`（全量重放被账本状态挡住）。
  指向的内容未变。）**
- 其余 24 处分布在 `RoomPendingCaptureStore.kt`（12 处，待处理草稿/会话行）、
  `ModelTaskTransactionDao.kt`（11 处，模型任务行）、`LearningDaoMappings.kt:211`（1 处）。
  例：`RoomPendingCaptureStore.kt:192`「Pending draft $draftId has no current revision」——
  与学习账本毫无关系。
  （计数口径：`grep -rn "LearningLedgerIntegrityException(" --include=*.kt core app feature`
  去掉 `/build/` 与 `app/src/test` 后 27 行命中，减去 `StudyDatabasePort.kt` 里那一行**类声明**。）
- **后果**：仅凭类型推不出「学习账本坏了」。谁在别的调用点上按类型给它配文案，
  就会说错原因——也就是 N-02 那个错法的另一种形态。

**处置**：`P3`。批 0 第 5 项的归类**挂在调用点上而不是类型上**（`projectionFailure` 只在
投影初始化的 catch 里被调用），并在 KDoc 里写明这个前提。真正的清理属于批 4：要么拆出
`LearningLedgerIntegrityException` 与「行内矛盾」两个类型，要么把名字改成不暗示账本的名字。

---

#### N-10　知识包安装是三个独立事务，中途失败会留下永久卡死的半装状态（`P2` · `persistence`，2026-09-12 批 0 第 5 项带出）

**取证方式**（第一手，读源码）。

- `BundledKnowledgeBaseInstaller.installPack`（`BundledKnowledgeBaseInstaller.kt:28-51`）
  依次调用 `importKnowledgeBase` → `importKnowledgeNodeRelations` → `importKnowledgeTeachingMaterials`，
  最后一步是 `installTeachingMaterials`。
- `importKnowledgeBase` 有 `@Transaction`（`ProblemOrganizationDao.kt:81-82`）；
  另外两个是 `RoomKnowledgeBaseStore` 上的普通 `suspend fun`（`:224`、`:250`），**没有跨三步的事务**。
- 于是**两个方向都能卡死**：①第二/三步失败 → 节点已入库、关系或材料缺失；
  ②`installPack` 的 `require(existingById == expectedById && …)`（`:41-47`）在**节点非空**时要求
  源、节点、绑定**逐字段完全一致**——半装状态必然不等，于是**此后每次启动都在这里再抛一次**，
  安装永远不会自愈。
- 唯一的出路是用户主动清数据或重装。这是本审计登记的第二条「需要清数据才能恢复」的缺陷
  （第一条是 `restore-failure-closes-db-no-reopen`）。

**处置**：`P2`。批 0 第 5 项**降低了它的危害面**（知识包卡死不再连带冻住学习进度，
见 §11 批 0 第 5 项），但**根因未修**：修法是给这三步一个事务，或让 `require` 在
「不缺但不同」时走重建而不是抛。归入**批 1**（与 1.3 `ChatEvidenceDao` 幂等修复同族：都是
「写入侧的半成品状态在下次启动变成永久故障」）。

> **2026-09-12 施工前核对（批 1，第一手）——两处订正＋一条被证实的修法前提。**
>
> 1. **「另外两个是普通 suspend fun、没有事务」不准确**：`importKnowledgeNodeRelations`
>    （`RoomKnowledgeBaseStore.kt:262`）与 `importKnowledgeTeachingMaterials`（`:292`）
>    **各自都在 `database.withWriteTransaction { }` 里**。真正缺的是**跨这三步**的事务，
>    所以本条的结论（半装后永久卡死）仍成立，但成因要说对：**每一步各自是原子的，
>    所以"半装"不是"一行只写了一半"，而是"整批里只落了前几批"**。
> 2. **卡死的具体路径只有一条：教学材料的分批导入。** 知识库那一步（节点/来源/绑定）
>    是**一次** `@Transaction`，要么全落要么不落，因此 `installPack` 里那个
>    「节点非空 ⇒ 要求逐字段一致」的 `require` **不会被自己人触发**（写进去的就是 pack 的内容）。
>    真正会长期停摆的是 `installTeachingMaterials`：`moe-2025` 那个包有 **10 356 条材料**
>    （6 个 sidecar：2048×5 ＋ 116），安装按 **2 000/批** 分批，**批与批之间崩溃就留下
>    "非空但不全"**，而旧写法只认「一条都没有」才导入，于是走到 `require` 失败，
>    **此后每次启动都再抛一次**。
> 3. **修法前提已从源码证实**：教材导入是**幂等**的——`KnowledgeTeachingMaterialDao.insertMaterials`
>    与 `insertBindings` 都是 `@Insert(onConflict = OnConflictStrategy.IGNORE)`，
>    且 `importAll` 随后**逐条回读校验**、不一致才抛 `ImmutablePayloadConflictException`
>    （`KnowledgeTeachingMaterialDao.kt:79-118`）。所以「重跑一次安装以补齐缺口」
>    **只会补齐缺失的行，不会覆盖已落库的行**，也不会把"真冲突"悄悄吃掉。

> **已修（2026-09-12 批 1 收尾）。** 判定条件由 `existingMaterials.isEmpty()` 改成
> `existingMaterials.size < expectedMaterials.size`，**并去掉那条 `return`**，让控制流落到既有的
> `require` 上。前半句让"非空但不全"改为**重跑全部分批导入**（幂等，见上面第 3 条）；
> 后半句让**全新安装那一路也开始校验自己刚写的东西**——原先"导入完就 return"与"校验"互斥，
> 导入出问题没有任何断言会说话。
> 做法、前置事实（6 个 sidecar 里每个来源都被至少一条材料引用，所以更强的校验路径恒成立）
> 与仪器化测试见 §11「N-10」。


#### N-11　配图模型硬编码为 OpenAI 专用，与规格里「MCP 是配图路径」的表述对不上（`P2` · `product-boundary`，2026-09-12 批 1 第 1.2 项核查带出）

**取证方式**（第一手，2026-09-12 复核）。

- **硬编码**：`OpenAiImageGenerationChannel.kt:27` `private val modelId: String = DEFAULT_MODEL_ID`，
  `:146` `const val DEFAULT_MODEL_ID = "gpt-image-2"`。
- **两个生产构造点都不传 `modelId`**：`AttachedImageGeneratorFactory.kt:41`、`ConfiguredCleanImageGenerator.kt:108`。
  （全仓 `OpenAiImageGenerationChannel(` 三处命中，第三处在 `OpenAiImageGenerationChannelTest.kt:22`。）
- **规格确实要求把模型拆开**：`docs/image-pipeline-spec.md:41-47`「**Models are split (per decision)**」，
  `:117`／`:119` 的路由表把「multimodal (image input)」与「MCP gpt-image-2」列成两列。
  ⇒ 所以「配图不读用户的 `modelId`」**不是**缺陷（这正是 §3 **S-8** 推翻旧判据的依据）。
- **但规格同时把 MCP 写成配图路径**：`CleanImageGenerator.kt:3-9` 的 KDoc——「may call an
  image-to-image provider (e.g. the tools/image-mcp-server, or OpenAI `/v1/images/edits`)」；
  规格 §2 的标题就是「Direction A — image-to-image generation (**MCP** + gpt-image-2)」。
- **而 `tools/image-mcp-server` 不在构建里**：`settings.gradle.kts:19-37` 的 17 个模块没有它；
  该目录自带一份独立的 `settings.gradle.kts`（`tools/image-mcp-server/settings.gradle.kts`），
  **全仓对它的唯一引用就是这个 KDoc**。

**后果**（两层，分开说）：

1. **第三方 provider 用户用不了配图。** `core:data` 里已经实现了 Anthropic Messages、
   Gemini GenerateContent、OpenAI Chat Completions 三套协议（见 §6），用户完全可以配好对话模型；
   但配图这条链**固定打 `gpt-image-2`**，而不是走用户配的 provider。也就是说
   「配图」这个功能对非 OpenAI 用户是**存在但不可用**的。
2. **规格与实现对不上。** 规格把 MCP 写成配图路径，实现里没有 MCP，只有直连 OpenAI 的
   `/v1/images/edits`。谁照规格排查，都会去找一个不在构建里的模块。

**处置**：`P2`，**但这是产品决策，不是可以直接动手的代码缺陷**——与 §13.2 `attention-floor-unreachable`
同一判据（改动牵动数据模型／设置界面／schema）。三条路都要用户定：

| 走法 | 内容 | 代价 |
|---|---|---|
| ① 承认现状 | 把规格改成「配图固定用 OpenAI gpt-image-2（需单独配 OpenAI key）」，删掉 MCP 相关表述 | 改文档 + 设置页文案；第三方用户仍无配图 |
| ② 加配图模型设置 | 给配图加独立的模型／凭据字段（新字段＋设置页＋schema 迁移） | 最大；要动 schema 与配置存储 |
| ③ 真接 MCP | 把 `tools/image-mcp-server` 接进构建并接线 | 要养一个独立进程／构建目标 |

**本批不动。** 与 S-8 的分工：**S-8 作废的是「配图通道读不到 `modelId` ⇒ 缺陷」这条判据本身**
（它的修复方案会打断当前能用的配置）；**N-11 是那条判据被推翻之后剩下的真实缺口**。

#### N-12　题干读取失败没有自己的状态，只能停在「正在读取题目…」（`P3` · `missing-handling`，2026-09-12 批 1 N-03 带出）

**取证方式**（一手读源码）：`produceState` 块（`SmartMistakeBookDestinations.kt:98-137`）先写
`value = TeachingArtifactLoad(practiceUnitId = requestedId)`（`:104`），再在**块末**写
`isLoaded = true`（`:130-136`）。于是 `:107` 的 `repository.teachingArtifact(...)` 只要抛异常，
`isLoaded` 就**永远停在 `false`**，界面停在 `:181` 的「正在读取题目…」。

- **它不能被 N-03 那个助手包住**（这一点是刻意的，不是遗漏）：那条失败的含义是「**这道题读不出来**」，
  不是「少一张可选卡」。用同一个助手降级成 `artifact = null`，非捕获题会落到 `:257` 的
  「当前题目暂时不可用，未记录本次作答。」——把一次**读取失败**说成「这题不存在」，
  正是模式 E 的同一个错法（缺省值冒充真实信号），比崩溃更坏：用户会以为题库里没这道题。
- **今天之所以几乎不可达**：`teachingArtifact`（`RoomBackedStudyExperienceRepository.kt:460-461`）
  读的是**夹具**（`fixtureSource.teachingArtifactForPracticeUnit`），不碰库、不调 drain。
- **使它与批 2 第 3 项绑定的那个事实**：批 2 第 3 项（**S-5**）要把内容源从夹具换成
  `knowledge_teaching_material`。**那一步就是让它变可达的那一步**——换上真库之后，
  读失败从"几乎不可能"变成"迁移、损坏、外键、查询超时都可能"。
- **诚实的修法不是加 try/catch**，而是给 `TeachingArtifactLoad`（`SmartMistakeBookRoot.kt:178-186`）
  加一个失败维度（`isLoaded` / `artifact` / `loadFailure` 三态），并给失败态一句自己的话。
  **本批不实现，但批 2 第 3 项动手时必须一并处理**——两项在同一行代码上，分开做等于
  把一个已知的假话留给下一次改动。
- **✅ 已处理（2026-09-13 批 2 第 3 项）**，但**理由被订正**：上面「批 2 第 3 项要把内容源从夹具换成
  `knowledge_teaching_material`，那一步就是让它变可达的那一步」**不成立**——`teachingArtifact`
  返回的是 `VerifiedTeachingArtifact`（题干／讲解／策展评测项），`knowledge_teaching_material`
  是知识点讲解材料，**两者不是同一种东西**，`teachingArtifact` 既换不过去也不该换。
  所以它今天**仍然**不抛（夹具源恒返回 null 而不抛），这条失败在功能上依旧是惰性的。
  **修它的理由是另一条，而且更强**：这次改动让那个 `produceState` 块**对每一张卡都真的干活**
  （原先对实拍题它读完 artifact 就结束了），于是块里**唯一没有包裹**的那一次读取
  （`repository.teachingArtifact(...)`，另两张卡都走 `loadOptionalSessionCard`）成了整块里
  唯一能把它带走的调用。落地形态：`TeachingArtifactLoad` 增 `loadFailureDiagnosticId`，
  读取抛出被捕获（取消照常上抛）并落成失败态；界面在「正在读取题目…」**之前**判掉它，
  显示 `teachingArtifactFailureMessage(...)`（读不出来 ＋ 没有记录作答 ＋ 怎么重试 ＋ 诊断编号）。
  验收：app 层 3 条 JVM 断言 ＋ 1 条钉住"编号按实例、归类看前缀"的断言。
  **消灭的失败因此表述为**：抛一次异常就让 `isLoaded` 永远停在 false、界面永远停在
  「正在读取题目…」——用户既看不到原因，也没有任何出路。

> **⚠️ 2026-09-13 复核后撤回：这条处置作废，N-12 的处置从"已修"改为"不可达 → 撤回"。**
>
> 上面那条"修它的理由"经一手回读**不成立**。`StudyExperienceRepository.teachingArtifact`
> 在生产里**只有一种实现**：`RoomBackedStudyExperienceRepository` 直接委托
> `fixtureSource.teachingArtifactForPracticeUnit`；`StudyFixtureSource` 的两种实现都是
> **纯查表**——release 侧 `EmptyStudyFixtureSource` 的函数体就是 `= null`，debug 侧是
> `curatedQuestions().firstOrNull { … }?.teachingArtifact()`；工厂
> （`StudyExperienceRepositoryFactory`）**没有任何装饰层**。这条读取**不可能抛**，
> 而取消异常又被原样上抛。
>
> 于是那套兜底是**生下来就不可能触发**的：try/catch 分支、`TeachingArtifactLoad`
> 的 `loadFailureDiagnosticId`、`TeachingArtifactFailureMessages.kt`（27 行）与它的
> 3 条断言一起，构成"机制建成、却没有能触发它的失败"——正是这份审计反复点名的病。
> 更坏的是它会把 N-12 记成"已修"：**一个假的"已修"比一个未结项有害得多**，
> 因为它会让下一个人以为这条通道已经被验证过。
>
> **处置：删除**（app 三个位置：目的地的 try/catch 与失败分支、`TeachingArtifactLoad`
> 的字段；两个文件整体删除）。`SmartMistakeBookRoot` 里另一处同名 `produceState`
> 从来就没设过这个字段，删掉之后两个生产者才真正同形。
> **将来工件若改从库里读**（那时才真的会失败），再按**届时的真实失败**设计失败态，
> 而不是现在摆一个空壳。
>
> 复核来源：子代理 `review-app` 的第一条发现（本轮四路审查之一）；结论由我一手读
> 上面四个文件核实后才采信。**同一轮里 review-tests 也独立指出**：那条失败分支
> 没有任何测试能走到（它只能被纯函数用例"测"文字，而生产里没有任何输入能让它出现）。
>
> **删除后的验证**：app 两个 JVM 变体 `36/0` 与 `37/0`（各比之前少 3 条，正是被删的那三条）；
> app 两个仪器化变体各 **42 条、0 失败 0 错误**（strictOffline 另有 6 条跳过）——
> 撤销动的是复习会话的 gate，所以这两个变体是必须跑的那条线。

#### N-13　「静默学习小结」（`LEARNING_SUMMARIZE`）在**任何**构建里都不可达（`P2` · `dead-channel`，2026-09-12 批 2 第 1 项核查带出）

**取证方式**（第一手，逐段回读调用链）：这条通道**每一环都写好了**，唯独没有一个会回答它的提供方。

| 环节 | 位置 | 状态 |
|---|---|---|
| 任务契约 | `ModelTaskContractRegistry.kt:99` | 有 |
| prompt 策略版本 | `ModelEgress.kt:48`（`LEARNING_SUMMARIZE`）/`:62` | 有 |
| 输入／输出类型 | `ModelTasks.kt:23`、`TutorTasks.kt:824` | 有 |
| 应用侧发起 | `SmartMistakeBookDestinations.kt:309-333`（会话结束时静默跑一次） | 有 |
| 读取与展示 | `SavedMistakeTutorRoute.kt:277` `observeRecentBySubject(…, LEARNING_SUMMARIZE, limit = 4)` | 有 |
| **会回答它的提供方** | —— | **没有** |

两处各自独立地把它挡死，任何一处单独看都像是"有意的"：

- **唯一调用方要求本机提供方**：`buildTutorDebriefRequest`（`feature/tutor/TutorModelTaskPolicy.kt:246`）
  `if (capabilities.executionLocation != LOCAL_NO_EGRESS) return null`——外部提供方**故意**不跑
  （避免未经批准就把对话记录发出去，这条本身是对的）。
- **本机提供方只有 debug 夹具，而它明确拒绝**：全仓 `LOCAL_NO_EGRESS` 的产线提供方只有
  `core/data/src/debug/.../FakeModelGateway.kt:175`（debug 源集）。它既不宣告这个 kind
  （`CAPABILITIES.supportedTasks`，`:167-170`），处理分支还写着
  `is TutorDebriefInput -> error("The capture-only demo provider cannot summarize tutor debriefs")`（`:147`）
  ——而且因为 `:53` 的能力检查先跑，那一行**连都到不了**。

**后果不是崩溃，是"功能静默不存在"**：那个「最近小结」列表永远是空的，学生从来没见过一条学习小结，
也没有任何一处会报错。这正是模式 A（接线了但没人消费）从"某个 DAO"上升成"整条通道"的形态。

**为什么不直接"把 kind 补上"**：`ProviderCapabilitySnapshot.supportedTasks` 是**广告**，
生产网关（`OpenAiCompatibleModelGateway.kt:485-499`）宣告它等于宣称"发给我我能答"——
而它的唯一调用方只在 `LOCAL_NO_EGRESS` 下发这条请求，所以往生产网关里加它就是**一次新的假声明**
（模式 E）。让通道活过来只有一条路：某个 `LOCAL_NO_EGRESS` 提供方**真的会回答**这个输入。

**处置**：`P2`，**产品决策**（与 **N-11** 同一判据——两条路都改变产品行为，不擅自实现）：

| 走法 | 内容 | 代价 |
|---|---|---|
| ① 让 demo 提供方也会做小结 | 给 `FakeModelGateway` 加 `TutorDebriefInput` 分支并宣告该 kind | 只动 **debug 源集**，release 零影响；但把「capture-only demo」的定位改成多用途 |
| ② 承认它不在产品范围内 | 删掉整条链（请求构造、契约、prompt 策略、读取与 UI） | 删得干净，但若将来要做"会话小结"要重建 |

**本批不动**；批 2 第 1 项只做**能确证的那一半**：`IMAGE_PIPELINE_CLASSIFY`（它在**生产**路径上被真实调用，
见下）。

#### N-14　读侧的保持率估计仍然用**出厂**衰减（`P3` · `inconsistent-read-path`，2026-09-12 批 3 F-01 施工带出）

**取证方式**（第一手：枚举 `FsrsScheduleMath.retention(` 与 `intervalDays(` 的**全部**生产调用点，
而不是只看审计点名的那四处）：

| 调用点 | 是否写持久状态 | F-01 后是否随拟合衰减 |
|---|---|---|
| `SchedulingReplay.predict`（拟合的损失） | 否（但决定拟合） | **是**（第 ① 处） |
| `MemoryUpdateModel` 的两处 `retention` ＋ `intervalDays` | **是** | **是**（第 ③② 处） |
| `LearningProjector` 毕业分支（`intervalDays` ＋ `reviewAtTargetRetention`） | **是** | **是**（第 ⑤ 处，见 §11 F-01；**生效条件见下面的注**） |
| `OptimalRetention.averageRetention` | 否（推荐读out） | **是**（第 ④ 处） |
| `KnowledgeReviewQueue.kt:120`（知识点到期风险） | 否 | **✅ 是（2026-09-13 批 3 第 3 项）**——它改收一条配置好的曲线，不再自己取默认衰减 |
| `ForgettingCurve` 的**默认实例**（`HLRPredictionAuditService`、`LegacyExponentialMemoryUpdateModel`、`ReviewPlanner`、以及 app/feature 里的 `ForgettingCurve()`） | 否 | **否**（本半仍在） |
| `ReviewPlanner()`（V1 规划器，core:data 装配点上用的是默认实例） | 否 | **否**（本半仍在） |

- **今天全部不可观测**：`parameters[20]` 从未离开出厂值（正是 F-01 的成因），所以这些读路径
  与写路径目前**逐位一致**。它们只会在 **F-01 之后**才分叉——一旦优化器真的开始移动 `w20`，
  读侧的 R 与写侧的 R 就会用两条曲线，而表现是"排期说还早、风险说快忘光了"。
- **注（2026-09-13 补）**：上表最后那个「**是**」要带上条件。毕业分支的两半在**默认设置下都不可观测**：
  目标保持率 **0.9** 时 `intervalDays` 与衰减恒等无关（`FACTOR` 就是按 `R(S,S)=0.9` 定义的），
  而 **0.8** 时闸门过与不过会写出**同一天**（那是 `GRADUATION_TARGET_RETENTION`）。
  只有目标保持率 ∈ 滑杆区间的其它取值时，"闸门读哪条曲线"才既**可分辨**又**有后果**。
  阈值表与随之补上的用例见 §11 批 3「施工中带出的第二个发现」。
- **为什么不在本批一起改**：这些路径**不写持久状态**，改动落在"哪些只读路径该拿到拟合参数"
  这个装配决定上（V1 规划器的实例、`knowledgeRecallRiskByNode` 的函数签名、app/feature 里每个
  `ForgettingCurve()`）。这与 **F-02**（三种 elapsed 口径）**是同一片代码**——F-02 的修法
  （把口径收口到一个函数）必然要顺带决定这些实例怎么拿到参数。两项一起设计才不会来回改。
  **2026-09-13 复核**：这个"一起设计"的判断**只兑现了一半**，而且是**对的那一半**——
  队列那一处（`knowledgeRecallRiskByNode`）与 F-02 **落在同一个函数签名上**，所以它**必须**
  同批改（拆成两批就是同一个签名连着改两次）；剩下那些默认实例**不在 F-02 的改动面上**，
  它们的触发条件（"优化器第一次移动 `w20`"）也还没有到来，因此继续登记。
- **处置**：`P3`，**部分了结（2026-09-13 批 3 第 3 项）**：**队列那一半已修**（表格首行），
  余下的**默认曲线实例**仍登记。触发条件是"优化器第一次真的移动了 `w20`"，在那之前它们是惰性的。
  **验收条件已经能写出来**（现在写会立刻红，所以它属于 N-14 而不是 F-01）：
  `parameters[20] != DEFAULT_PARAMETERS[20]` 时，读侧与写侧对**同一个** `ProblemMemoryState`
  必须给出相同的可提取率 `R`。

#### N-15　「今天／昨天／N 天前」用的是墙钟整日地板，不是学习者日历日（`P3` · `display-convention-split`，2026-09-13 批 3 第 3 项枚举 F-02 影响面时带出）

**位置**：`app/src/main/kotlin/com/tingyun/smartmistakebook/LearningMasteryScreen.kt:364-375`（`recentActivityLabel`），
两个调用点在上面的「最近变化」列表里（`:183` 无障碍描述、`:203` 可见标签）。

**取证方式**（第一手）：函数体是
`val elapsedDays = (now - occurredAt).coerceAtLeast(0) / DAY_MILLIS`，再 `when (elapsedDays) { 0L -> "今天"; 1L -> "昨天"; else -> "${elapsedDays}天前" }`。

**失败路径**：与 **F-02** 同一物种——`now` 与 `occurredAt` 都是 UTC 毫秒，除以一天的**墙钟**时长。
于是**本地 23:00 复习过、次日 01:00 打开**时，标签是「**今天**」，而按学习者日历那是**昨天**。
同理，本地 00:30 的活动在当天 23:00 看是「今天」（正确），但**任何**跨午夜不足 24 小时的间隔都会被压成「今天」。

**为什么它是 N-15 而不是 F-02 的一部分**：F-02 统一的是**保持率估计**（`R` 的输入 `t`），
这一处是**展示标签**——它不喂给任何计算，错了不会让排期偏移，只会让用户看到一句与事实不符的话。
两者**该用同一个口径**（学习者本地日历日），但改法与风险面不同：这里要动的是一段 UI 代码，
而 `recentActivityLabel` **只有两个 `Long` 入参**，拿不到学习者时区。

**改动会带来的连带项（已核对，不是猜测）**：

| 连带项 | 说明 |
|---|---|
| 需要把时区带进 UI 层 | `LearningMasteryScreen` 的入参里没有 zone；唯一的产线调用点是 `SmartMistakeBookRoot.kt:839`，在那里把 `studyZoneId` 一路传下来即可（F-02 已经在装配点上暴露了这个值）。另有一个仪器化测试直接构造该屏幕（`LearningMasteryScreenInstrumentedTest.kt:33`），签名一改那里要跟着给值 |
| 会改一条既有用例的语义 | `app/src/test/.../LearningMasteryRecentActivityTest.kt` 钉住了 `recentActivityLabel(now + DAY_MILLIS, now) == "今天"`——那是**"未来时钟一律算今天"**的独立规则（`coerceAtLeast(0)`），与日历日口径**不冲突**，改口径时**必须保留它**，两种口径下的 0 都映射到「今天」 |
| 但**没有**用例覆盖真正的判别格 | 现有 4 条断言全部落在**整日整 24 小时**的间隔上（`now`、`now - 1d`、`now - 5d`、`now + 1d`），在这些格上墙钟地板与日历日差**逐位相同**——**这正是这类缺陷能长期活着的原因**（§9 模式 F），与 F-02 的既有夹具同病 |

**处置**：`P3`，**登记**（不塞进批 3 第 3 项）。理由：批 3 第 3 项的范围是"**读侧估计**的口径"，
把一段 UI 标签一起改会让那一条的可验证边界（"两个读侧出口给出同一个 R"）变成两件事。
**验收条件已经能写出来**：本地 23:00 复习、次日 01:00 打开时，标签必须是「昨天」；
且现有的「未来时钟算今天」四条断言**一条都不许改**。

#### N-16　开场重教（spec §2.16）与前置补救同病：**范围与门都取自策展件**，于是只在演示内容上可达（`P2` · `fixture-gated`，2026-09-13 批 2 第 3 项带出，**2026-09-14 已修并订正**）

**取证方式**（一手，与批 2 第 3 项同一次回读）：

| 事实 | 位置 |
|---|---|
| 范围与科目取自策展件 | `RoomBackedStudyExperienceRepository.reTeachOpening` 以 `teachingArtifact(practiceUnitId) ?: return null` 起手，再用 `artifact.subject` ＋ `artifact.knowledgeNodeIds` 查材料 |
| 装配侧仍以策展件为门 | `SmartMistakeBookDestinations` 的 `reTeachOpening` 加载点判 `loadedArtifact != null`——**批 2 第 3 项只放开了补救那一张卡，没有放开它** |
| **渲染点只有策展屏** | 全仓 `reTeachOpening` 的 UI 消费点只有 `ReviewSessionScreen`；`CapturedReviewSessionScreen`（实拍题走的那一屏）**没有这个参数** |
| ⇒ 生产里它**从不加载、也从不显示** | 上两条相加；release 的 `EmptyStudyFixtureSource` 让 `teachingArtifact` 恒为 null |
| 既有覆盖同样看不见 | `ReviewReTeachOpeningInstrumentedTest` 直接驱动 `ReviewSessionScreen` 并手工构造 artifact——与补救那条同一种盲区 |

**为什么它比 N-15 严重（`P2` 而不是 `P3`）**：补救卡只是题干旁的上下文，而 §2.16 的开场重教
按本仓自己的说法是**必经步骤**——`ReviewPrerequisiteRemediationInstrumentedTest` 的类注释原文：
「§2.16 的开场重教是必经步骤（学员须先确认），而前置补救只是题干旁的上下文——两条通道在界面上
共用材料卡，行为却必须相反」。也就是说：**这条通道缺失不是"少了一张卡"，是"leech 卡的恢复路径
少了一步"**，而 leech 状态本身会让卡被降权（`LEECH_RANK_FACTOR`）。

**为什么不与批 2 第 3 项一起做**：补救卡的位置由 spec 定（§2.9「gap>0 时注入补救项」，
且明说"不拦作答"），照做即可；**§2.16 没有说开场重教出现在哪个界面**，
而它是**必经步骤**——放在实拍屏的什么位置、要不要在作答前拦一下、与自评流程怎么排序，
都是**产品可见**的选择，不是可以照抄补救卡的地方。这一条要按 §12.2 先指名"它消灭的是哪个具体失败"
再动手。**本批只按最小形态登记**，不擅自设计。

**修复的形状（已确定，实施时照此）**：数据层与补救完全相同——改成读
`currentKnowledgeScopeOf(practiceUnitId)`，科目改从错题行取（`MistakeRecord.subject` 已在手边）；
装配侧放开 `artifact != null` 那道门；呈现侧需要一次产品决定。


> **⚠️ 2026-09-14 订正：本条原文写的「**没有任何一屏渲染过它**」是错的。** 我在按这条去删代码之前
> 先读了目标（删除是不可逆的），发现 `ReviewSessionScreen` **确实渲染它**——`ReTeachOpeningCard`
> ＋ `return@RootPageColumn` 扣住题干，学员确认后才露出题目（spec §2.16 的"必经步骤"），
> 而且有两条用例钉着：`ReviewReTeachOpeningInstrumentedTest`（专测"先重教、再露题"这个顺序）
> 与 `ReviewSessionViewModelTest.reTeachOpeningStaysAcknowledgedAcrossRecreation`。
> 准确的事实是：**只有策展屏渲染它，实拍屏 `CapturedReviewSessionScreen` 连参数都没有**，
> 而装配侧的门是 `artifact != null`（夹具背书 ⇒ release 恒 null）。所以真机上看不到开场重教，
> 原因是"**只有演示内容能走到渲染**"，不是"没人渲染"。`12877056` 的提交信息自己就记着这个区别：
> 它当时把**前置补救**卡修好（改读题库 ＋ 实拍屏真正渲染），而 `reTeachOpening` 是**有意留在旧门上**、
> 并注明"未在本批改动内"。**这条订正改变了处置**：按错的前提去删，会删掉一条 spec 明文要求、
> 已在渲染、且有两条用例的功能；正确动作是把范围与门都改成读题库。
>
> **✅ 2026-09-14 已修（`7e611d2b`）**，两层同时放开——缺一层则"数据层修好、生产里什么都不会变"：
> ① `core:data` 的 `reTeachOpening` 范围改读 `currentKnowledgeScopeOf`（题库的
> `knowledge_node_ids`，与排程/视觉归因同一份结论），科目也取自这些 KC 自己的行；材料按科目分区
> 检索，跨科目绑定时逐个科目查再按选择器顺序合并，而不是拿"第一个"科目静默漏掉另一半。
> 这与 `prerequisiteRemediation` 在 `12877056` 拿到的修法同一条，**也正是 A3 要的"C 与 D 收敛"**
> ——C 消失后，取材料那一半不再需要新缝。② `feature:review` 的实拍屏新增该参数与确认门
> （确认状态进 `SavedStateHandle`，过不了进程死亡就会把学员按回材料页或反之绕过重教），
> `ReTeachOpeningCard` 由 `private` 放宽为 `internal`；`app` 的门改成 `readableUnitId` 并传进实拍屏。
> 新增 `aCapturedQuestionGetsItsReTeachOpeningFromTheLibraryAloneWithNoFixtureSource`
> （空夹具 ＋ 不播种策展包，只留题库真实行——release 的形状）。变异：把范围退回策展件，
> **事前预测红集 = {该用例}、另两条 leech 用例保持绿**，实测只它一条红，md5 逐字节还原。
> 回归：`:core:data` ＋ `:feature:review` ＋ `:app` **1595 条 0 败**。
>
> **教训（写在这里供后来者）**：这条错误断言的传染面比它自己大——我把它抄进了问询用户的决策页，
> 于是**下一个动作就是"按错前提批准删除"**。拦住它的是"删除前先读目标"这条纪律，不是复核断言。
> 凡是"没有 X 渲染过它"这类**存在性否定**，写下时必须附上枚举证据（`grep` 的形状与命中），
> 否则它会在下游被当成事实使用。

#### N-17　搜索延迟的书面预算**不可能被验证**：恢复真实测量后，FTS 三条计时超预算 3.2–20.9 倍（两条 3.2–4 倍、并发那条 17.7–20.9 倍），而目录侧两条都在预算内（`P1` · `unverified-gate`，2026-09-13 批 4 施工中带出）

**取证方式**：把 `PerformanceGateTest.insertTestData` 从空实现改成真实播种（10k 行 ＋
`refreshLibrarySearchProjection()` 建 FTS 索引），并给三条计时用例各加一条**前置断言**
（"这条查询确实命中过行"，否则它就是零行命中，而零行最快）。改完立刻红了三条——
**第一次**红是被测查询与索引脱节（`countSearch` 的入参是**已构造好的 MATCH 表达式**，
生产经 `CjkTextTokenizer.matchExpression`，而用例直接传了用户输入，于是中文查询被当成
单个词、一行都命中不到）；修掉这一层之后，**第二次**红才是真的计量结果：

| 用例 | 书面预算 | 第 1 次实测 | 第 2 次实测 |
|---|---|---|---|
| `searchPerformance10kItems`（10k 行，100 次 P95） | 500ms | **1678ms** | **1619ms** |
| `chineseSearchPerformance`（10k 行，50 次 P95） | 500ms | **2018ms** | **2013ms** |
| `concurrentSearchPerformance`（10 并发均值） | 1000ms | **17730ms** | **20903ms** |
| `firstScreenPerformance`（20 行分页） | 500ms | 通过 | 通过 |
| `facetQueryPerformance`（全表 facet P95） | 200ms | 通过 | 通过 |
| `explainQueryPlanNoFullTableScan` | 无全表扫 | 通过 | 通过 |

**这个不对称是关键证据**：目录侧的两条（**首屏 20 行分页**、**全表 facet 聚合**）都在预算内，
而 **FTS 搜索**这三条超预算：`searchPerformance10kItems` 3.2–3.4 倍、`chineseSearchPerformance` 约 4 倍、**`concurrentSearchPerformance` 17.7–20.9 倍**——上面那两处旧写法（"3–42 倍"与"3–18 倍"）与本节的表**都对不上**，以本表为准（一次低估、一次高估，同一条实测写成了两个数，属 R-13 同类漂移，已一并订正）。"设备慢"解释不了这个差别——慢设备会一起慢；
**指向 FTS 搜索路径本身**（`library_search_fts MATCH` ⨝ `library_search_content` ⨝ `library_catalog`）。

**处置：预算标 `NOT_MEASURED`，不挑一个能让它变绿的数。**
审计给本项的两条路是"填真实播种"或"改成明确的 `NOT_MEASURED`"——**两条一起用了**：
播种是真的（所以计量真的发生、前置真的会拦），而**三个数不断言**，只 `println` 记下
（证据在 `core/database/build/outputs/androidTest-results/**/testlog/` 里，两次运行的数都在）。
保留的断言是：夹具让查询命中过行、以及一个 60 秒的**挂死上界**（粗到不可能因设备慢误报）。
**为什么不重定预算**：本机只有一台软件渲染的 AVD，"它是否代表真机"没有证据；
按这台机器的数字写进预算，等于把断言改成与观察相符。

**要决定的是**（这是产品/工程取舍，不擅自定）：
1. 若真机也不达标 → 这是**产品问题**，FTS 搜索需要优化（已有一条可用的诊断起点：
   目录侧快、FTS 侧慢，所以问题在 MATCH ⨝ content ⨝ catalog 这条链，不在目录视图本身）；
2. 若真机达标、只有模拟器不达标 → **按设备档位重定预算并在文件里写明档位**，
   同时给 CI 一个真机/加速器档位，而不是继续用"CI_MULTIPLIER ×4"这种无档位依据的系数
   （现有系数在这三条上即使 ×4 也仍然超）。

#### N-18　从 ≤v31 升级上来的用户，升级前就存在的题**永久搜不到**：`countIndexed()` 读的不是索引（`P1` · `wrong-guard`，2026-09-13 批 4 给迁移矩阵补数据断言时带出，**已修**）

**取证路径**：给迁移矩阵补"数据没被迁移丢掉"的断言时，顺手问了一句"**升级上来的库搜得到吗**"，
新增 `upgradedLibraryIsSearchableThroughTheProductionReadPathAlone`（v31 导出 schema ＋ 一行题
→ 只按生产路径 `open()` 跑完迁移链 → 直接搜）。**它红了。**

**现场**（当时的断言信息把三个读数直接打了出来）：

```
迁移后 content=1 indexed=1 outbox=0；显式刷新后 content=1 indexed=1 outbox=0   → 检索命中 0 条
```

**根因**：`library_search_fts` 是 **external-content** 表（`content=library_search_content`），
对它做**不带 MATCH 的全表扫描**时 SQLite 会回落到内容表取值——于是
`SELECT COUNT(*) FROM library_search_fts` 数的是**内容行**，不是**索引行**。
而 `refreshProjection()` 的首次引导分支判的条件正是 `countIndexed() == 0`：

- 31→32 的迁移只能回填 `library_search_content`（**未分词**——分词要在 Kotlin 里做，
  SQL 迁移建不了索引），所以那一刻的真实状态是"内容 1 行、索引 0 条"；
- 但 `countIndexed()` 报 **1** ⇒ 走**增量**分支 ⇒ 只去啃那个空的 outbox ⇒
  内容永远不再分词、索引永远是空的；
- **显式刷新也修不好**（这一点是实测出来的，不是推的）：显式刷新走的是同一条被误判的分支。

**影响**：从 ≤v31 升级上来的用户，错题库的**文本搜索对升级前就存在的每一道题都返回空**，
而且**任何刷新都修不好**。数据没坏，但用户会读成"我的题没被记住"——这正是它危险的地方。
从 v32 及以后升级的库不受影响（它们的索引是触发器增量维护的，内容行本来就是分过词的）。

**为什么此前全绿**：本文件其余三条用例**全都显式调了 `refreshLibrarySearchProjection()`**，
而它们的夹具是在 v31 **空库**上 `open` 之后才 `seedFixture`（`seedFixture` 会逐条 reindex）——
**根本没有经过"迁移时库里已经有数据"这条路**。空库那一刻 `content=0 indexed=0`，
引导分支判对了，所以它们绿得完全合理，也就完全掩盖了这条缺陷。

**处置（已修）**：`countIndexed()` 改为读 FTS4 的影子表
`SELECT COUNT(*) FROM library_search_fts_docsize`——`_docsize` 每个**已索引**文档一行，
是这里唯一能真正回答"索引里有几条"的读数。迁移后的库里
`library_search_fts_docsize`／`_segdir`／`_segments`／`_stat` 四个影子表都在（已实测列出来）。
方法名与语义至此才对齐。**没有改迁移、没有改 schema、没有升版本。**

**验证**：新用例连同本文件其余三条 **4/4 绿**。两次变异，预测的变红集合与实际逐次相等：

| 变异 | 预测 | 实际 |
|---|---|---|
| M-A 把 `countIndexed()` 改回内容族查询（＝修复前） | 红 `{升级后可搜}` | 红 `{升级后可搜}`，且失败信息是**根因的直接读数**：`但索引里一条都不该有 expected:<0> but was:<1>` |
| M-B 把 `refreshProjection()` 从 `searchCount` 读入口删掉 | 红 `{升级后可搜}`，其余三条保持绿 | 一致 |

两次变异源都已按 md5 逐字节还原，`git status` 只剩这一处改动。
**完整 `:core:database:connectedDebugAndroidTest` 重跑：151 条，0 失败 0 错误 0 跳过**
（该套件基线 150 条，本次 ＋1 ＝ 新增的升级检索用例；去重后只有一份结果 XML，写入时间 14:11:47）。

**顺带记一条纪律**（已写进 §13.4）：本条的取证路上我先按端口名
`refreshLibrarySearchProjection` grep 出"生产零调用点"，**差点写成"索引从没被建过"**——
实际四个读入口都在调它的内部名 `refreshProjection`。**否定性结论必须换检索式再核一遍。**
真正的缺陷比"没接线"小得多、也隐蔽得多，而那条假发现会把人带去改本来正确的代码。

#### N-19　前置补救按**每张卡**整读一遍错题目录（`O(N²)` 相关子查询／一场复习）（`P1` · `quadratic-read`，2026-09-13 四路审查 `review-data` 带出，**未修，已登记**）

**一手核实**：`RoomBackedStudyExperienceRepository.currentKnowledgeScopeOf`（`:564-568`）读的是
`database.observeMistakes().first()`，然后 `firstOrNull { it.practiceUnitId == practiceUnitId }` ——
为了一行的范围，把**整个目录**读出来。

代价（审查者给的量化，我按代码复核过算法形状）：`observeActiveMistakes` 每行带
**5 个相关子查询**（`knowledge_node_ids` 自身还嵌套两层），而调用点是
`ReviewSessionDestination` 的 `produceState`，**每张卡加载一次** ⇒ 一场复习
`O(N)` 次整目录读 × `O(N)` 行 ⇒ **`O(N²)` 次子查询**。库到数千题时，
每次翻卡都要付一次全目录扫描的代价，只为一行数据。

**为什么没在本轮直接修**：正确的修法不是"再写一份谓词"——`knowledge_node_ids` 那段
40 行 SQL 已经出现在 `observeActiveMistakes` 里，复制一份就是**第五个口径**（见 N-20），
而且它一旦与热查询漂移，同一屏上的"掌握度"与"前置补救"会基于不同的 KC 范围，**没有测试会红**。
另一个修法（给热查询加可选参数）会改到目录读的执行计划。正确解法是**先把 N-20 的口径统一**，
再让这一个调用点走统一后的定向查询——两件事应当一起做，所以登记而不是先糊上一层。

> **✅ 2026-09-13 已修并验证**（`28935d40`）。做法绕开了上面那条"必须先统一口径"的顾虑：
> **不重写谓词**——整段目录 SQL 提成 `ProblemDao.MISTAKE_CATALOG_SQL`，定向查询包成
> `SELECT catalog.knowledge_node_ids FROM (<同一份文本>) AS catalog WHERE catalog.practice_unit_id = :id`。
> 于是"第五个口径"在**文本层面不可能出现**，N-20 的口径统一仍然可以单独做（它现在是纯粹的语义取舍，
> 不再被性能问题绑住）。端口加 `knowledgeNodeIdsForPracticeUnit`（两个实现：`RoomStudyDatabase` 与测试替身）。
>
> **"省不省"不靠推理，靠 SQLite 自己的说法**：新增仪器化用例读 `EXPLAIN QUERY PLAN`，断言
> **没有** `SCAN error_book_entry` 且**确有** `SEARCH`——等值谓词若不下推，派生表就是把整份目录
> （每行 5 个相关子查询）先物化再筛，比原来更慢，那条用例正是拦这个的。另加两条防空转断言
> （计划不能为空、必须出现 SEARCH），否则"没扫全表"可能只是因为什么都没读到。实测 **1 / 0**。
>
> **防回退**：测试替身记 `catalogueReads`，补救取一道题的范围前后该计数必须不变。
> 变异（把调用点改回 `observeMistakes().first()` 整读目录）实测 **恰好 1 条红**，
> `expected:<2> but was:<3>`——那条断言落在 `thePrerequisiteMaterialIsLookedUpUnderThePrerequisiteNodeSubject`，
> **不是**我先以为的 S-5 那条（记账订正，与 §13.4 的读数纪律同类）。
> 回归：`:core:data` **418 / 0**、`:core:database` **67 / 0**、`:app` localFirst **73 / 0**。
> **未做**：`knowledge_node_ids` 那 5 个相关子查询本身的形状、以及目录读在数千题规模下的绝对耗时——
> 本条只消除"每张卡整读一遍"这个乘数。

#### N-20　「这道题绑在哪些 KC 上」在 `core:data` 里有**四个答案、三种优先级**（`P2` · `four-precedences`，2026-09-13 四路审查 `review-data` 带出，**未独立核实**）

审查者报告（其报告尾部被截断，只收到前三条）：`currentKnowledgeScopeOf`（题库范围，
空集返回 `null`，无夹具回退）、`reTeachOpening`（`teachingArtifact(...) ?: return null`，
即**夹具优先**）、以及另外两处，对同一个问题给出不同优先级的答案；本批只统一了其中一处。

**登记理由**：这与 N-19 是同一个设计项的两面（先统一口径，再谈性能），而且它属于
"同一个事实有多个来源"这一类缺陷——本仓的其它 P1 里有 11 条是这一类的镜像。
**待取回的完整清单**（审查者的第 3 条及其后）会补上另外两处的 `文件:行`。

> **✅ 2026-09-13 清单已由我自己枚举补齐（一手读源码），并给出统一建议——但统一本身是取舍，未擅自施行。**
>
> 「这道题绑在哪些 KC 上」在 `core:data` 里确实有**四个答案**（都在 `StudyExperienceMappers`／
> `StudyReviewPlannerService`／`RoomBackedStudyExperienceRepository` 三条链上）：
>
> | # | 位置 | 优先级 | 空集时 |
> |---|---|---|---|
> | A | `StudyExperienceMappers.toCatalogEntry:45` | 库内绑定 → **策展件** | 空集（`masteryStatus` 落到 UNKNOWN） |
> | B | `StudyReviewPlannerService:213-227` | 库内绑定 → 策展件证据归因 → **按科目的伪 KC** | **恒有值**（伪节点 `pseudo:<SUBJECT>`） |
> | C | `RoomBackedStudyExperienceRepository.reTeachOpening:488-489` | **只看策展件**（不读库内绑定） | 无开场 |
> | D | `RoomBackedStudyExperienceRepository.currentKnowledgeScopeOf:560-564` | **只看库内绑定** | `null`＝不提供补救 |
>
> 另有第五处是**消费者**而非口径：`StudyReviewPlannerService:185` 用 `knowledgeNodeIds.isEmpty()`
> 决定要不要给未归类题materialize 伪节点（§3.4 的 FK 前置条件）。
>
> **三种优先级**＝A/B 的差别只在最后一层回退（B 有伪 KC，A 没有）；C 与 D 各只取一端。
> **其中两处不只是"答案不同"，而是方向相反的错**：C 与 A 会去读**策展件**，而 S-5 已经判定
> 「范围应取自题库自身，不取自策展夹具」（release 里 `teachingArtifact` 恒 null）——
> C 的这半正是 S-5 的同病，已登记为 **N-16**（且那条通道无 UI，属产品决定）。
>
> **我的建议（待你定）**：把问题**按用途拆成两个**，而不是硬凑成一个答案——
> ①**调度/展示用**（A、B）＝「库内绑定为准，空则按 §3.4 用伪 KC」，其中**策展件回退应当删除**
> （release 恒 null，留着只在 debug 造成"两种行为"）；②**取材料用**（C、D）＝「只认库内绑定，
> 空即不提供」，**不得**用伪 KC 冒充真实 KC 范围（伪 KC 没有材料，只会让检索空转）。
> 这样 C 与 D 收敛到同一条规则，A 与 B 的差别只剩"伪 KC 该不该进目录"，而那是个可单独判的展示问题。

> **2026-09-13 施工前核实（一条会改变做法的发现）**：原打算先做"取材料那一半"（把 C 收敛到 D 的规则），
> 但一手读代码后它**不是一行改动**：`reTeachOpening` 的**科目**也来自那个策展件
> （`teachingArtifact(practiceUnitId)` → `fixtureSource.teachingArtifactForPracticeUnit(...)`，一手核实为
> fixture-backed），而材料检索 `referencesFor(subject, knowledgeNodeIds, …)` 两个入参都需要。
> 想只认库内绑定，就得**再加一个缝**（按 practice_unit 取"科目 ＋ 范围"，与 N-19 那条定向查询同源）。
> 而且它落在 **N-16 的产品决定**里（那条通道没有任何一屏渲染过它）——先加缝、后决定是否渲染，
> 顺序反了会白做一遍。**结论：C 与 N-16 一起做，不做前置的半截。**

#### N-21　「重试」按钮不重跑失败的那一步，失败横幅也永不消解（`P1` · `inert-retry`，2026-09-13 四路审查 `review-app` 带出，**已一手核实**）

**取证**（我自己回读四个文件，不是转述）：

- `SmartMistakeBookRoot.kt:319-323`：`onRetry = if (startupState.isRetryable) { { application.refreshStudyExperience() } } else null`；
- `SmartMistakeBookApplication.kt:328-338`：`refreshStudyExperience()` 只调 `studyRepository.refresh()`，
  而 `StudyExperienceRepository.refresh()` 的默认实现就是 `= initialize()`（`:393`）
  ——**它不会重跑 `BundledKnowledgeBaseInstaller.install`**，那一步只在
  `onCreate` 的那次 `runStartupInitialization` 里跑（`:287-291`）；
- `startupState` 的写入点只有 `:76`（初值）／`:269`（初始化成功置 `Ready`）／`:273`／`:294`（失败），
  **没有任何一条路径把失败态清回 `Ready`**；
- `StartupState.isRetryable`（`StartupState.kt:38-46`）对 `RecoverableFailure` 默认为 `true`
  ——**包括 `KNOWLEDGE_BASE` 类别**。

**具体失败**：知识包安装失败 → 横幅说「本地知识包尚未准备好」并给出**重试**按钮 →
用户按下去 → 只重跑了投影，知识包还是没装；而且**无论这次重试成功与否，横幅都不会消失**
（成功时 `initialize()` 不写 `startupState`；`SmartMistakeBookApplication.kt:296-297` 的注释
甚至明说"投影成功也绝不能抹掉先前那条知识包失败"——那条规则对**启动路径**是对的，
但它与"给用户一个只重跑投影的重试按钮"合起来，就成了一句做不到的承诺）。

**修法方向（未实施）**：让 `refreshStudyExperience` 走**与启动同一条** `runStartupInitialization`
（重跑它该重跑的那一步），并给 `startupState` 一条回到 `Ready` 的路径；
两条都要有断言，否则"重试真的重跑了那一步"与"横幅真的会消失"都还是没被钉住。

> **✅ 2026-09-13 已修并验证**（`f12e8b73`）。做法沿用 N-02 已经建好的那条缝：新增
> `runStartupRetry`，与启动走**同一条** `runStartupInitialization`，只在**一处**不同——
> 这一次的结论**无论如何都发布**（失败态或 `Ready`）。启动路径那条"成功不写状态"保持原样，
> 它是对的（那时启动态本来就是 `Ready`，且投影成功不得抹掉知识包失败）；差别写进了两者的 KDoc。
> 应用侧新增 `retryStartupInitialization()`，与 `refreshStudyExperience()` **分开**：后者只是让仓储
> 重发快照（`MainActivity.onStart` 用它），既不重装知识包也不该动启动态——两件事混在一条路上，
> 正是这条缺陷的成因。横幅的 `onRetry` 改指新函数。
>
> 断言 4 条（`StartupInitializationTest`）：重试**必须重跑两段**、成功**必须发布 `Ready`**、
> 失败发布的是**这一次**的失败（`startup:knowledge:` 前缀）、**取消不发布**。
> 变异各自还原后实测：把安装那一步换成空操作 ⇒ **3 条红**（重跑／失败归类／取消；
> 后两条红是因为夹具用安装那一步注入失败，属夹具耦合，如实记下）；把
> `publish(failure ?: Ready)` 换成只在失败时发布 ⇒ **恰好 1 条红**（成功那条）。均与事前预测一致。
> 回归：`:app` localFirst **77 / 0**（＋4）。
>
> **未做的部分要说清**：这是**策略层**的证据（重试会重跑、会收回横幅）。"横幅在界面上真的
> 换了文案／真的消失"仍无端到端断言——§13.4 里那条缺口（启动横幅"从状态到界面"没人写）**依旧在**，
> 本条没有把它关掉。

#### N-22　S-10 的**正向**方向没有测试：`adopted == true` 与"只在采纳时才写回"都无人断言（`P2` · `missing-direction`，2026-09-13 四路审查 `review-tests` 带出，**未独立核实**）

审查者报告（尾部被截断）：`SchedulingEvaluationTest` 新增的那条只断言 `second.adopted == false`
与 `second.parameters == incumbent`；**没有任何用例断言 `adopted == true`**，也没有用例断言
参数**只在采纳时**写回（`StudySchedulingCalibration` 整个类没有测试）。
因此把比较反向（`<` 改成 `<` ＋ 余量）或把成功返回硬写成 `adopted = false`，
现有用例全绿而**参数永久冻结**——与 F-01 是同一个失败类的另一面。

**登记理由**：这是"反向用例有了、正向没有"的典型形态；修法是给新用例补一句
`assertTrue(fitted.adopted)`（顺带证明夹具是有判别力的），再给 `StudySchedulingCalibration`
补一条带假设置存储的用例。**未实施**，因为要与 N-20/N-19 排期。

> **✅ 2026-09-13 两条都已落地，且与 N-27 ①② 是同两件事**（两条登记来自不同审查者，重复）：
> `eb8c48e9` 补了判别力前置（`assertTrue(fitted.adopted)`，变异实测"永不被采纳"只红这一条）；
> `5be9a9f5` 补了写回闸门（`optimizeSchedulingParametersWritesBackOnlyWhenTheCandidateIsAdopted`，
> 变异实测"无条件写回"只红这一条，`expected:<1> but was:<2>`）。
> **一处与登记原文不同、需要说清**：`StudySchedulingCalibration` **没有**单独建测试类，
> 而是走**真实装配**覆盖——那个类是 `internal` 且只在 `RoomBackedStudyExperienceRepository.kt:238`
> 被构造一次（7 个协作者），直接单测会把装配点排除在外，正好丢掉要证的那一环。
> 证据：`:core:data:testDebugUnitTest` **417 / 0**（含该用例）。逐条变异记录见 **N-27** 的 ✅ 块。

#### N-23　任务契约注册表在生产里**一个读者都没有**（`P2` · `test-only-contract`，2026-09-13 四路审查 `review-data` 带出，**已一手核实**）

`ModelTaskContractRegistry`（`core/model`）逐 kind 声明了 `egressPurpose`／`assetPolicy`／
`requiredDisclosures`／`prohibitedDisclosures`／派发预算，但 `grep` 全仓（排除 `build/`）
显示：**主源码里没有一处读它**，读它的只有 `core:model` 自己的测试与
`core:data` 的网关测试。也就是说这些声明目前**没有运行时效果**——它们是文档，
不是强制。

**为什么值得单独登记**：本批修掉的那条静默失效（`IMAGE_PIPELINE_CLASSIFY` 没被宣告）
恰好就是"同一个事实有两个来源、其中一个没人读"；注册表是那个"没人读"的来源。
**修法方向（未实施）**：要么让签发路径真的按契约校验（`prohibitedDisclosures` 尤其值得），
要么把这份声明降级成纯文档并去掉"注册表"这个名字所暗示的强制性。
**不擅自选**：这属于"要把模型出网边界做成强制还是自证"的产品级取舍。

> **2026-09-14 复核：我原打算做的"让它承重"那一半，其实已经存在。** 动手前先读现有用例，
> 发现 `ModelTaskContractRegistryTest.everyKindWithAPromptPolicyHasAContract` **已经**逐条钉住
> "凡是有现行 prompt policy（＝生产真的会发送）的 kind，都必须声明出网契约"，并且注释里
> 明确点名了三个未建占位（`PROBLEM_RELATE`／`TUTOR_EVALUATE`／`REVIEW_RERANK`）；
> `R-08` 那一行也记着"测试那一半已落地"。
> **因此本条现在只剩纯产品问题**：`prohibitedDisclosures` 这类声明要不要在**签发路径上强制**
> （运行时有牙齿），还是把"注册表"降级为纯文档并去掉那个名字暗示的强制性。
> 两条都改变产品语义，按 §13.3 **不擅自选**。若选"强制"，最低成本落点是网关构造请求时按
> `contract.prohibitedDisclosures` 校验信封——那是一个有明确输入/输出的定点改动，可以单独立项。

#### N-24　未投影过的单元，同一轮排空里**第二条**视觉证据的 `delta_t` 仍是 0（`P1` · `wrong-delta-t`，2026-09-13 四路审查 `review-data` 带出，**已修并验证**）

**一手核实**：`VisualInteractionIngestor.ingestOne` 写下账本行后推进"上次复习"，但带一个
`containsKey` 守卫，注释给的理由是"map 里没有它 ⇒ 这个单元还没被投影过 ⇒ `delta_t = 0`
是首次复习的约定"。那个理由**对第一条成立、对第二条不成立**：第一条刚刚创建了账本行，
此刻它就已经是该单元最近的一次复习；守卫让第二条也读到 null，于是跨两天的第二条被记成
"又是首次复习"，**把间隔 0 写进 FSRS 优化器的训练集**——与 F-01 同一类，只是换了个通道。

**修法**：删掉守卫、无条件推进；顺序本身保证第一条仍拿到 null（`record` 在上、写在下面）。
**为什么既有用例盖不住**：`aSweepMeasuresEachRowFromTheRowJustBeforeIt` 的夹具调了
`seedPriorMemoryForVisualIngest`，那个 map 里本来就有这个单元 ⇒ 两种情况恰好同值——
又一次"两种口径恰好一致"的夹具。新增 `aSweepOnANeverProjectedUnitMeasuresTheSecondRowFromTheFirst`
补上未覆盖的那一格，并同时断言第一条仍是 `0.0`（首次复习的约定不许被一起改掉）。
**验证**：`core:data` JVM 416/0；变异（把守卫放回去）**只有新用例变红**，
信息是数值级的 `expected:<[0.0, 2.0]> but was:<[0.0, 0.0]>`，而既有那条保持绿。

#### N-25　F-01 只改了**症状**没拆机制：`decay` 仍是三个公开入口的默认参数，`[20]` 手写六处（`P2` · `regression-surface`，2026-09-13 四路审查 `review-domain` 带出，**未独立核实**）

审查者给的位置：`core/domain/.../FsrsScheduleMath.kt:64,71,89`。判据是"下一个调用点照样能
静默退回出厂衰减"——F-01 修的是**当时那几个**调用点，而不是让"用错衰减"这件事**写不出来**。
**登记理由**：本仓反复出现的模式是"修了实例、留着形状"，而这条正是形状。
修法方向（审查者给）：删掉三个默认值 ＋ 加统一的 `decayOf(parameters)` 收敛下标 20。

> **✅ 2026-09-13 已修并验证**（`214fdd36` ＋ `675bf81e`）：新增 `FsrsScheduleMath.decayOf(parameters)` 作为下标 20 的**唯一**出处
> （只做 O(1) 的长度检查，不调会遍历数组的 `requireValid`——它会被逐条记忆调用），五处手写改为调用它；**三个默认值删掉**，新增 `DEFAULT_DECAY` 给"手上确实没有拟合参数"的场景，必须显式写出。
> **编译器逐条列出了所有依赖默认值的调用点：16 处，全在测试里，production 一处不差**（production 那 8 个调用点本来就把 decay 显式传了）。
> 追补一条**值钉**：`decayOf(DEFAULT_PARAMETERS) == -0.1542`（FSRS-6 的 `w20`，出厂参数最后一位），**刻意用字面量**——用 `decayOf` 算期望值是关系断言，下标写错时两边一起动。
> **变异**（把 `decayOf` 的 `[20]` 改成 `[19]`）红 6 条：`{值钉, FsrsDecayThreadingTest 四条, 毕业闸门一条}`。
> **如实记一笔**：`FsrsDecayThreadingTest` 那四条**也**抓得住它（期望值手写了 `-parameters[20]`），所以值钉不是唯一守卫，但它是唯一在孤立语境下说得出"这个数不对"的那条。
> 验证：全树 JVM **16 套件 1585 条 0 失败 0 跳过**（`--rerun-tasks`），此后仅补那条值钉用例（production 未变），`core:domain` ＋ `core:data` 复跑 **830/0**。

#### N-26　日界／「已经过天数」仍有**四处**游离在 `ReviewCalendar` 之外（`P2` · `fourth-convention`，2026-09-13 四路审查 `review-domain`/`review-data` 带出，**未独立核实**）

四处的来源分属两份报告（互不重复）：
`SchedulingEvaluation.kt:46-48` 的 `elapsedDaysSince` 回退分支（墙钟整日地板，被审者称为"第四种口径"）·
`StudyWriteContext.kt:38` 的 `studyDay.epochDay`（delta_t 的减数）·
`StudyReviewPlannerService.kt:460` 规划用的 `LocalDate` ·
`LearningProjector.kt:1204-1205`／`:862-866`（`localEpochDayOf` 直通包装 ＋ 内联的减法与 clamp）。
**为什么登记而不是顺手改**：F-02 的结论是"日界的定义只有一处"，而这里证明那句话**今天还不成立**；
把它们逐个收口是一次有明确验收条件的收尾工作（每收一处，那条"唯一定义"的声明才多一分真），
但**不能**把四处混在别的改动里改——那正是本轮已经踩过的坑（"改了字面、没改内里"的镜像）。

> **✅ 2026-09-13 第 2 处已收口**（`4cf541e8`）：`StudyReviewPlannerService.planningContext` 原先自己
> `atZone(studyZoneId).toLocalDate()` 再 `atStartOfDay(studyZoneId)`；现在问 `ReviewCalendar`
> 新增的 `localDayStartEpochMillis(localEpochDay, zoneId)`（**入参是本地日序号**，与 `localEpochDayOf`
> 合成一件事的两半；不是 `epochDay × 86400000`，那是 UTC 午夜）。判别格就是这两者之差：
> 变异（改成 UTC 午夜）**恰好 1 条红**，即新用例。`:core:domain` **415 / 0**、`:core:data` **418 / 0**。
>
> **第 1 处（`SchedulingEvaluation.elapsedDaysSince` 的回退分支）不能当"免费收口"做**：我原以为它只是
> 读侧展示口径，一手读代码后发现**它喂的是损失与参数拟合**（`SchedulingReplay` 用它算预测对），
> 动它会改拟合结果 ⇒ 必须先定"这个回退该不该与读侧同口径"，属**取舍**。
> 第 3、4 处（`StudyWriteContext`／`LearningProjector`）仍是写侧：改它们会改持久 `delta_t`，
> 要升 `LearningProjector.VERSION` ＋ 重放，与 F-02「不升版本、不重放」的边界冲突，**单独立项**。

#### N-30　「这一族参数」在生产里有**第三个来源**：保持率建议读设置存储，曲线读构造时那一组（`P2` · `third-parameter-source`，2026-09-13 我在核 N-27 第 ③ 条时一手发现，**未修——它是个取舍**）

**一手取证**（读代码，不靠推断）：

- 曲线与模型用的是**构造时解析出来的那一组**：`RoomBackedStudyExperienceRepository.fsrsParameters
  = optimizedFsrsParameters ?: DEFAULT_PARAMETERS`，`forgettingCurve`／`reviewPlannerV2`／
  `memoryUpdateModel` 都用它。这个字段的 KDoc 自己写着"**模型与曲线共用**（F-01）……
  分开写两次就是让两边有各走一条曲线的机会"。
- 而 `StudySchedulingCalibration.recommendedDesiredRetention()` 读的是
  `schedulingSettingsStore.optimizedParameters.first() ?: DEFAULT_PARAMETERS`——**另一个来源**。

**为什么它们会分叉**：`SmartMistakeBookApplication:303` 在**启动时**跑一次
`optimizeSchedulingParameters()`，而 repository 是**更早**构造的（构造时把设置存储当时的值
拷了进去）。于是一次优化写回之后、到下次启动之前，同一进程里：排期用旧那组、保持率建议用新那组。
`SmartMistakeBookRoot:872` 就是在这一窗口里取 `retentionHint` 的。

**影响有界**：分叉的只是那条**建议**（用户据此设目标保持率），不是排期本身；影响面也只在
"本次启动内"（下次启动两边自动对齐）。

**为什么登记而不是改**：两种收法都说得通，而它们给出**不同的用户可见数值**——
① 让建议用**排期实际在用的那一组**（与"模型与曲线共用"这条自家不变量一致，代价是建议会滞后一次拟合）；
② 承认"建议应当反映最新拟合"，那就要让排期也跟着换（与"读在构造时、一次会话内模型稳定"冲突）。
**这是取舍，按 §13.3 的规矩不擅自定。** 无论选哪边，都该有一条断言把它钉住。

#### N-27　标定回路的**消费者**零覆盖：S-10 正向方向、`StudySchedulingCalibration`、F-01 装配点（`P2` · `missing-direction`，2026-09-13 四路审查 `review-tests` 带出，**未独立核实**）

三条同源：① `SchedulingEvaluationTest:139-161` 只断言**被拒**方向，没有任何断言要求夹具真的
产生过一次采纳（`adopted == true`），于是"永不被采纳"的变异全绿；② `StudySchedulingCalibration`
**整个类没有测试**，`if (result.adopted)` 这道写回闸门与正向采纳都无人覆盖
（需要假 settings store：被拒时不得调用 `setOptimizedParameters`，采纳时必须调用）；
③ F-01 的装配点（两条曲线各自的 `decay = -fsrsParameters[20]`、把 `forgettingCurve` 交给
`StudyReviewPlannerService`）无测试，测试替身甚至不暴露 `optimizedFsrsParameters`。
**为什么这三条要一起做**：它们共同构成"标定真的改变了输出"的证据链；缺任何一环，
R-07（四个校准接口接线或删除）就仍然只能靠读代码判断。

> **已做两条（2026-09-13）**：
> ① `eb8c48e9`：给那条拒绝用例补**判别力前置**（`assertTrue(fitted.adopted)`）。变异实测：把成功路径改成永不采纳 → **只有它变红**，且失败信息就是新加的这句——`RED count 1` 同时说明，在这条前置之前，"永不被采纳"在整个 `core:domain` ＋ `core:data` 里没有任何断言会红（审查者那句话是真的）。
> ② `5be9a9f5`：新增走**真实装配**的用例，同一批历史拟两次——第一次必然采纳（写 1 次），第二次必然被拒（**不得再写**）。变异实测：把 `if (result.adopted)` 换成无条件写回 → 只有它变红，`expected:<1> but was:<2>`。夹具 40 张卡 × 4 次复习 ＝ 120 条可预测样本，并带一条前置断言证明它真的走得到采纳那一支。
> **还差第 ③ 条（F-01 的装配点）**，登记时要带一个实测到的路标：审查者建议断言"某条目录项的 `retrievability`"，而 `library_catalog` 这个视图里那一列**是 `NULL AS retrievability`**（一手读视图 SQL）；真正由曲线算出来的是**错题读侧**的 `problem_memory_state.retrievability`（`observeActiveMistakes` 直接选 `memory.retrievability`），所以判别观测点应取后者或取计划出的下次复习日，而不是目录行。

> **✅ 2026-09-13 第 ③ 条也已结清，做法与登记建议不同、理由如下**（`d9001921` ＋ `6ab72e36`）：
> **① 读侧那一半用测试钉住。** 观测点最终取 `StudyExperienceMappers.toCatalogEntry` 现算的
> `retrievability`（它直接用 repository 自己的 `forgettingCurve`），夹具是一张卡 ＋ 一条 30 天前的记忆态，
> 两次构造只有 `optimizedFsrsParameters` 不同 ⇒ 期望值由**独立算出的闭式**钉成字面量
> （`t/S=2.5`、`w20=0.1542 → 0.826135877`；`w20=0.4 → 0.798822641`）。
> 夹具取值写进注释：曲线在 `t = S` 处与参数无关（都恰好 0.9），那种夹具会让几条断言一起退化成同一个常量。
> 变异（把装配点这条曲线的衰减冻回出厂值）**恰好 1 条红**，就是新用例，其余 417 条不动 ⇒ 判别力是真的。
> **② 规划那一条不用测试，改成一实例。** 原先 `forgettingCurve` 构造两遍、实参逐字相同；
> 参数查重上一轮已收口，两份构造调用却仍给"排期一条曲线、保持率另一条"留路。曲线是不可变类，
> 于是直接共用同一个实例——**分叉在装配点不再可表达**，比补一条只能看见一个出口的测试更彻底
> （审查者给的两个观测点里，"计划出的下次复习日"依赖的是间隔公式那条线，与曲线实例无关，
> 照它写只会造出一条与要说的事无关的断言）。零行为变化：`:core:data` **418 / 0**。

#### N-28　S-2 的第二条通道（配图）没有测试证明它走了共享闸门（`P2` · `untested-gate`，2026-09-13 四路审查 `review-tests` 带出，**未独立核实**）

`core/data/.../AttachedImageGeneratorFactory.kt:25-36`：S-2 的修法是"所有出网构造点必须经过
同意门"，而去手写那条有测试，**配图这条没有**——`AttachedImageGeneratorTest` 直接构造
generator，绕开了工厂与闸门。**登记理由**：这是"机制建成、没有证据它在生产路径上生效"的
又一个实例，而它恰好是**出网**这条线上的一环。

> **✅ 2026-09-13 已修并验证**（`4e0e93f7`）。先把"为什么此前证不了"说清楚：`resolve` 把任何异常都吞成
> "少一张图"（`AttachedImageGenerator.kt:55`），所以**"闸门拒绝"与"放行后出网失败"从返回值上完全一样**——
> 唯一能分开的观测点是"channel 有没有被构造"（构造 channel 是这条链上第一件会出网的事），
> 而配图这条**没有可注入的缝**（去手写那条有：`ConfiguredCleanImageGenerator.ChannelFactory`）。
> 于是修法是把缝**收成两条通道共用的一个**（`ImageChannelFactory` ＋ `GuardedEditsChannelFactory`，落在 `model/`
> 与 `resolveImageCredential` 同一个包），配图工厂加一个 internal 重载带缝（**公开 API 不变**，生产路径逐位不变：
> 原来内联的 `resolveGuardedEdits` ＋ 构造 channel 原样搬进共享工厂）。两个 `ModelStore` 替身由 `src/test`
> 移到 `src/debug`——与 `FakeModelGateway` 同一机制，仪器化侧才拿得到（不写第二份）。
>
> 证据：新增**仪器化**用例 7 条（工厂第一步就要真 Context，所以只能仪器化），
> 6 条各钉一个拒绝理由（同意撤销／无同意渠道／无凭据／能力未测／能力不含图像输入／无配置存储）
> 且都断言**channel 一次都没构造**；第 7 条是**反面地基**：同意与凭据齐备时 channel **必须**被构造
> （没有它，"没有 channel"与"代码根本不构造 channel"分不开）。跑法：
> `:core:data:connectedDebugAndroidTest -P...class=...AttachedImageGeneratorFactoryInstrumentedTest` ⇒ **7 / 0**。
> 变异（把闸门那行换成直接 `readCredential()`，即 S-2 的修前形状）⇒ **恰好 4 条红**
> （同意撤销、无同意渠道、能力未测、能力不含图像输入），与事前预测集合逐条一致；
> 另 3 条（无凭据、无配置存储、反面地基）保持绿——正是它们不该管的事。夹具记账：
> `:core:data:testDebugUnitTest` **417 / 0**（替身搬家后 JVM 侧未变）。
>
> **⚠ 这次修复带着一个合并前提**：它建立在"图片通道该读用户同意开关"这一判断上，而那条轴在 `main` 上
> 已被产品决定换成了"配置模型即同意"（见 §3 **S-2** 的复核块，含逐条事实与影响）。
> 所以**这条提交不能原样合并**：合并时要么保留本分支的同意存储，要么按主线语义重述同一条断言。

#### N-29　批大小在 installer 与"半装"夹具里各写一份（`P3` · `fixture-boundary`，2026-09-13 四路审查 `review-data` 带出，**未独立核实**）

`BundledKnowledgeBaseInstaller.kt:78` 的 2_000 是局部 `val`，
`BundledKnowledgeBaseInstallerInstrumentedTest.kt:184` 又声明成 `HALF_INSTALL_BATCH = 2_000`，
两者无耦合：将来改分批会让"半装"夹具不再落在批边界上，而用例**依旧通过**
（它测的是"半装后可恢复"，不是"恰好停在批边界"）。修法：提到 installer 的 `internal const val`，
生产与夹具都引用它。

> **✅ 2026-09-13 已修**（`c753efa0`）：`BundledKnowledgeBaseInstaller.TEACHING_MATERIAL_BATCH_SIZE`，夹具的 `HALF_INSTALL_BATCH` 直接引用它。验证：两个源集编译通过，`core:data` JVM 417/0，被改到的仪器化类实跑 2/2。

#### N-31　状态报告的总体判定**只有一个可能取值**，且它把"没测"与"没过"读成同一件事（`P2` · `over-reporting`，2026-09-13 我在收 R-13 第 3 条时一手核实，**第 3 条与作用域声明均已修；余下三条仍是取舍**）

`docs/status.md` 的 `## Overall Status` 渲染 **PASS**，而同页的迁移矩阵、安全检查、宏基准/内存、
覆盖率、AAB 体积等行全是 `NOT_MEASURED`。三件事各自成立、互相独立：

1. **判定能拿到的取值只有 PASS 一个**（一手读 `.github/workflows/android-check.yml:104-123`）。
   生成那一步是 `if: always()` ＋ `env: JOB_STATUS: ${{ job.status }}`，所以失败时**会**渲染 FAIL——
   但紧接着的提交那一步是 `if: success() && github.event_name == 'push' && ref == 'refs/heads/main'`，
   **只有绿的那次才提交**。于是 main 上的 `status.md` 是一条永远绿的铭牌：FAIL 只进 artifact，从不落地成文件。
2. **有整行永远填不上**。`{{MIGRATION_TEST_ROWS}}`、`{{SECURITY_DEBUG_SIGNING}}` / `{{SECURITY_HARDCODED_SECRETS}}` /
   `{{SECURITY_DEPENDENCY_VULNS}}`、以及 `COLD_START_MS` / `WARM_START_MS` / `SEARCH_P95_MS` /
   `FIRST_SCREEN_MS` / `PEAK_MEMORY_MB` 连同它们各自的 `_STATUS`，在 `generate_status.py` 的 `values` 里
   **没有任何赋值来源**，被兜底扫描写成 `NOT_MEASURED`；而它们需要的证据（仪器化那 150 条、真机 benchmark）
   在**另一个 job**（`android-check.yml:127` 的 `instrumented`）里，两个 job 之间没有工件传递。
   这与 **§5.5.6**（release-gates 声称"迁移矩阵通过／备份往返通过"，而报告里没有对应证据行）是同一件事的两面。
3. **判定本身曾是唯一一个"没有测量来源却照样给结论"的值**。旧代码是
   `os.environ.get("JOB_STATUS", "success")`——任何**不经过那个 workflow 的调用**（本地手跑、将来换 runner）
   都会在一份整页 `NOT_MEASURED` 的报告上写下 **PASS**。这与本文件给这个生成器的正面评价
   （"对无测量来源的值一律渲染 NOT_MEASURED 而非编造数字"）**直接冲突**——即这条例外连它自己的契约都没守住。

> **✅ 2026-09-13 已修第 3 条**（`6957f1d0`）：新增 `overall_status(job_status)`，把"没有来源"与
> "来源说成功"分开——无 `JOB_STATUS` ⇒ `NOT_MEASURED`，有才映射 PASS/FAIL，**CI 行为逐位不变**。
> 同一提交顺手修掉同物种的第二处：AAB 体积**已经读到**却写进了模板从不引用的键
> （`AAB_SIZE_PLACEHOLDER`），于是两个 AAB 列对着一份量过的工件恒报 `NOT_MEASURED`；
> 现在按 `RELEASE_<FLAVOR>_AAB_SIZE` 落列（`artifact_outputs` 一族同时收掉了 APK／AAB 两份重复的探测）。
> 新增 `tools/tests/test_generate_status.py` **6 条**（其中 3 条走**真模板**渲染）。反向验证：
> 把生成器还原成修复前，**恰好 4 条红**——两条 `ERROR`（`overall_status` 不存在）＋ 两条 `FAIL`
> （判定渲染成 `**PASS**`、AAB 行渲染成 `NOT_MEASURED`），另 2 条 CI 路径（`failure`→FAIL、`success`→PASS）
> **保持绿**，与事前预测的集合逐条一致。**但要说清楚：CI 不跑 `tools/tests`**（全仓无 pytest、
> workflow 无该步骤），所以这 6 条目前只是"可手跑的契约"，回归时不会自己响；要它生效得先把 tools 测试接进 CI
> （与 §5.5.7 的 paths 过滤是同一片）。本机跑法：`cd tools && python -m unittest tests.test_generate_status`。

> **未做的那一半是取舍，不该由施工顺手定**：PASS 该覆盖什么（本 job **真跑过**的步骤，还是"每一行门都测过且绿"）·
> 哪些行算门 · 要不要引入第三个词（`PARTIAL`／未测）· 要不要让 FAIL 也能提交到 main ·
> 以及那批恒空的行由谁产（跨 job 传工件，还是删列并在 release-gates 里改口径）。
> 我的建议：**保持 PASS ＝「本次 check job 通过」**，但把标题写成 `## Overall Status (this run)`
> 并加一行"此判定只覆盖本 job 实际执行的门"，让"没测"与"没过"在读者眼里分开；恒空的行要么指定 owner ＋
> 补工件传递，要么从模板删掉。**留着不动是最差选项**——它让每一份报告都读起来像"全绿"。

> **✅ 2026-09-13 作用域那一半已按上面的建议做掉**（`8e6337aa`）：模板标题改为
> `## Overall Status (this run)`，并在结论下加一块引用写明——**这一位只覆盖本次 CI 真跑过的门**
> （compilation / unit tests / lint / assemble / gates），**不覆盖**同页仍为 `NOT_MEASURED` 的行
> （迁移矩阵、安全检查、宏基准与内存、AAB 体积等），那些行**既不算通过、也不算不通过**。
> 选这一条而不是引第三个词：**读者拿到的信息量一样，"没测"与"没过"在眼里分开**，
> 而判定位的取值集合与 CI 行为**逐位不变**——新增一个 `PARTIAL` 词会同时动到告警口径与历史可比性，
> 那些不在这一半的范围内（见下面仍开着的三条）。
>
> **连带修的是测试自己的一处脆性**（这条比模板改动更值得记）：`verdict_of` 原先是
> 「`## Overall Status` 之后第一个非空行就是结论」，于是**标题一加限定词，它就取到 `(this run)`**
> ——三条渲染用例同时变红。改成取标题后缀里第一个 `**...**` 后结论不再依赖标题措辞。
> 这里最容易犯的错是「测试红了就改断言让它变绿」，所以改完补了变异：把 `overall_status`
> 固定成 `"PASS"`，**事前预测红集 = {无 JOB_STATUS, failure}、绿集 = {success, AAB}**，
> 实测 `FAILED (failures=2)`，红在预测的两格上；`generate_status.py` 以 md5 逐字节还原
> （`b03a0b49…` → 同值）。也就是说这次不是把断言放松，而是把它从「依赖标题字面」换成
> 「依赖结论位置」，判别力未减。
>
> 验证命令与结果：`cd tools && python -m unittest tests.test_generate_status` → `Ran 6 tests ... OK`。
> **仍未变的一条前提照旧成立**：CI 不跑 `tools/tests`（全仓无 pytest、workflow 无该步骤），
> 这 6 条目前仍是"可手跑的契约"，回归时不会自己响。

> **仍未做的三条（真取舍，未擅自定）**：① 那批**恒空的行由谁产**——跨 job 传工件，还是在 release-gates
> 里改口径／从模板删列（§5.5.6 是同一件事的另一面）；② 要不要让 **FAIL 也提交到 main**
> （现在只有绿的那次提交，于是 main 上是一条永远绿的铭牌，FAIL 只进 artifact）；
> ③ 要不要引第三个词（`PARTIAL`／未测），以及 `PASS` 到底该覆盖"本 job 真跑过的步骤"
> 还是"每一行门都测过且绿"。**作用域写明之后，①仍是最该先定的一个**：它决定报告里
> 那六类行的去留，②③都建立在它之上。

### 13.7　R-01「拆超线文件」的收口：两次实测否掉了计划里假设的那条缝（2026-09-13）

B6 的计划里写着「② 带调用点的聚合」「③ DAO 聚合」。真去拆之前先量了一遍，结论是
**这两类的「族」不是我原以为的模块，而是同一张策略表的若干行**。三条证据，都是本机实测：

**（一）`DatabaseContract.kt`（1027 行）的引用图：分不开，因为公用底座太宽。**
按**配平花括号**切块（不是按行区间——这正是上次失败的第二个拦路石）拿到 **74 个成员**：
37 个 `validate*`／`construct*` 公开入口 ＋ 22 个词表常量 ＋ 15 个字段级私有辅助
（`id`／`text`／`known`／`finiteRange`／`nonNegative` ×2／`positive` ×2／`stableToken` ×2／
`isKnownZone`／`validateStudyDay`／`validateSourceAsset`／`validateDraftRevision`…）。建成员间引用图后：

| 量 | 值 |
|---|---|
| 公开入口中被解析出的**连通分量** | **19** |
| 其中**完全孤立**（零交叉引用）的入口 | **11** |
| 最大的两个分量 | 各 6 个成员（capture/confirm 一族；review/seed 一族） |
| `id` 被**多少个不同分量**使用 | **15 / 19** |
| `nonNegative` ／ `positive` ／ `sha256` ／ `text` ／ `known` | 12 / 7 / 4 / 4 / 4 |

也就是说：**按族切，每一族都要引用同一批私有底座**——这正是上次编译报
`Unresolved reference 'id' / 'requireContract' / MAX_PROJECTION_BATCH_SIZE` 的**原因**，
不是运气差。而且真正的跨族边是**有语义的**（`validateSeedBundle` → `validateReviewBundle`、
`validateConfirmTutorSession` → `validateReviseProblemDraft`、`validateCommitTutorSession` →
`validateCommitProblemDraft`），切开就会在新文件之间**造出互相 import 的小网**。

**（二）真正的缝在"字段规则 vs 命令策略"，不在"族 vs 族"。** 那 15 个字段辅助 ＋ 词表常量
≈ **230 行**，是**纯函数、零命令知识**的一层——把它提成同包 `DatabaseFieldRules`（Kotlin 允许
`import Object.member`，调用点可**一字不改**）才是这文件里唯一存在的分层。收益：1027 → ≈797 行，
**回到线内且是真分层而不是搬行数**。**本轮不做**，理由写在下面（三）之后的取舍里。

**（三）DAO 的公用底座是「事务边界」，拆开就是拆掉原子性——这条最硬。**
`ProblemDraftTransactionDao`（1213 行，`internal abstract class`，非 interface）有 **14 个
`@Transaction` 方法**：`create`／`appendSourceAsset`／`revise`／`replace`／`split`／`mergeSourceBundle`／
`read`／`confirmTutorSession`／`readTutorSession`／`commitTutorSession`／`attachCleanRedrawAsset`／
`endTutorSession`／`commit`／`replayCommit`。它们**逐个是"一条命令一个事务脚本"**，靠一批
`protected abstract` 的插入原语（`insertDraft`／`insertDraftRevision`／`insertProblem`／
`insertProblemRevision`／`insertPracticeUnit`／`insertSourceAsset`…）跨 **9 张表**
（`problem_draft`／`problem_draft_revision`／`problem_draft_source_asset`／`problem_draft_commit_receipt`／
`problem`／`problem_revision`／`practice_unit`／`canonical_source_asset`／`tutor_session` ＋ `error_book_entry`）落地。
**按聚合根拆成多个 DAO，要么让一个脚本跨 DAO 提交 ⇒ 失去原子性，要么把 14 个脚本全部改写成
`database.withTransaction { }` ⇒ 动持久化层语义。** 一个有迁移矩阵的 app 不该为了行数做后者。
同样的形状也在 `ProjectionTransactionDao`（1194 行）里。**这条不是"懒得拆"，是拆之前先量：
量完发现这个动作会改行为，而 R-01 的定义是"不改行为的可读性改动"。**

**（四）本轮改动对这五个文件的真实贡献，也一并量了（防止把"我改大的"和"本来就大"混为一谈）：**

| 文件 | 基线 `dc12065` | 现在 | 本分支提交数 | 判 |
|---|---|---|---|---|
| `DatabaseContract.kt` | 1027 | 1027 | **0** | 未碰；超线是继承的 |
| `ProblemDraftTransactionDao.kt` | 1213 | 1213 | **0** | 未碰 |
| `ProjectionTransactionDao.kt` | 1194 | 1194 | **0** | 未碰 |
| `MistakeDetailRoute.kt` | 1067 | 1067 | **0** | 未碰 |
| `RoomStudyDatabase.kt` | 1301 | **1309** | 2 | ＋8 行，全是 R-02 的**端口方法本体** |
| `SmartMistakeBookRoot.kt` | 996 | **1003** | 5 | ＋9 行，其中 4 行是解释 S-2／N-21 的注释 |

净效果：**五个文件里只有两个是本分支碰过的，各加不到 10 行，且每一行都是取证过的修复**
（S-2 同意门、N-21 重试、R-02 分页缝）。super-review 的"把非组件文件推过 1000 行"那条，
落点是"**the PR** pushes…**without a strong structural reason**"——这里 996 → 1003 的 3 行净值
全是"必须修的东西"，那 7～9 行里没有藏着一处可以判司的结构问题。

**取舍（未擅自定）**：`DatabaseFieldRules` 那一刀是**唯一真实可用**的一刀，约 230 行、
零调用点改动、收益是 1027 → 797。但它动的是**本分支一行都没碰过的文件**，在一个以"内核就绪"
为目标的审计分支上做纯可读性重构，收益（行数回到线内）与风险（一次 230 行的搬动）不对称。
**建议单独开工单**，与 R-01 剩余项一起做；若用户要求现在做，做法已经量好在（一）（二）里。
**"不拆"要有证据支撑，这一节就是**——R-01 的原始前提（这些文件有干净的族缝）经测量**不成立**。

### 13.8　「可执行项已用尽，余下是一个决策队列」——B3／B4／B7 与 C 组的实测收口（2026-09-13）

做完 B6 之后回头逐条量剩下的项，结论是：**它们不是"还没排到"，而是每一条都卡在一个只有你能拍的**
**决定上。**把这个结论本身记下来，因为它决定了剩下这段时间该怎么花——继续"施工"会变成替你定产品口径。

**B3 余下的 `elapsedDaysSince` 回退：不是缺陷，是已被文档固定的近似，且回退分支在产线上基本不走。**
`SchedulingEvaluation.kt:46` 的 `deltaTDays ?: floor(...)` 取的是**墙钟整日地板**——正是 F-02 否掉的那个口径。
但它**已经**是整日（KDoc 写明"回退分支必须也是整天…不是新口径"），与 `delta_t` 的整数刻度一致；
剩下的差别只在"跨本地午夜但不足 24 小时"那一格。**实测它有多活**：`deltaTDays` 由
`ReviewLogSink.kt:102` 在**每一次写入**时落下 ⇒ 回退只对**迁移前的旧行与夹具**生效。
要再往前一步得给 `ReviewSample` 引入 `zoneId`——而那个 data class 是**有意不带时区**的
（`deltaTDays` 的 KDoc 写着它"captured at collection time from the learner-local study day"）。
**所以这是"要不要为一个旧行才走的支路，把一个域模型改成带时区"的判断，不是一行修。**

**B4 的 R-09 三列：注释已落地（`e9516c17`），而"接线 or 删列"两头都不是工程动作。**
`attempt_event` 的 `hint_count`／`revealed_before_answer`（及同族第三列）**既无写入方也无读取方**
（一手逐处 grep）。**接线＝发明一个消费者**（产品决定）；**删列＝建新表→拷数据→删旧表→改名**
（`ALTER TABLE … DROP COLUMN` 要 SQLite ≥ 3.35，`minSdk 23` 上是 SQLite 3.8，**会崩**，
且迁移矩阵跑在新设备上抓不到）。这条的注释正是为了拦住"把占位列当可用信号"，那件事**已经做完**。

**B7 四个校准接口：两个方向都是产品可见。** 接线是大功能，删除会让"我们有标定"这句话消失。
**C 组（N-17／N-23／N-30／N-31 余三条／R-12）与 C′（R-11）**：同理，逐条都是口径而非判断。

**因此剩下这部分的完成顺序＝决策顺序**（每定一条，其下的链条立刻可执行）：
**① N-16**（开场重教那条通道要不要有 UI）——它同时解锁 A3 与 N-20 的第二半；
**② `PASS` 覆盖什么 ＋ 恒空行由谁产**（N-31 余三条）——解锁报告语义与 §5.5.6；
**③ 四个校准接口的取舍**（B7）——解锁 R-07 与 N-27 的正向覆盖；
**④ 三条搜索／注册表／参数口径**（N-17／N-23／N-30）；
**⑤ 变基决定**（R-11／S-2 的 `main` 分歧）。
详见随后给出的决策页（每条附选项、成本与我的建议）。

### 13.9　你拍板的六条与它们的落地状态（2026-09-14）

把待决项整理成决策页之后，你逐条给了口径。这一节是**决定 → 做了什么 → 还差什么**的对照，
每一条都能追到提交。

| # | 你的裁定 | 状态 | 证据 |
|---|---|---|---|
| ① | 开场重教**修好**（读题库 ＋ 实拍屏渲染） | ✅ **已做** | `7e611d2b`；`:core:data`＋`:feature:review`＋`:app` 1595/0；新用例 ＋ 变异按预测红集 |
| ② | 状态页恒空行：**跨 job 传工件让它们真被测到** | ⏸ **未做（量完发现是工作流重构）** | 见下 |
| ③ | 四个校准接口：**全部接线，做成功能** | ⏸ **未做（特性级；且有一条前置缺陷）** | 见下 |
| ④ | N-17 搜索预算：**先在真机上测一轮再定** | ✅ **按此执行**（预算维持 `NOT_MEASURED`，数已记档） | §5.1 的 N-17 表 |
| ⑤ | S-2 口径：**采纳 main 的「配置模型即同意」** | ✅ **已做** | `b8e7f005`（cherry-pick `e462f1ea` ＋ 补本分支特有部分） |
| ⑥ | 状态页附带：**FAIL 也提交 ＋ tools/tests 接进 CI** | ✅ **已做** | `071bf759` |

**②为什么没做，以及它的正解（量过，不是推的）。** 本机跑一次生成器：**20 行／36 格仍是
`NOT_MEASURED`**，分属**六类生产者**——宏基准（冷/热启动、搜索 P95、首屏、峰值内存）、迁移矩阵、
安全检查、覆盖率、lint、release APK/AAB。其中宏基准那几个数只 `println` 进 testlog，
**不是 JUnit XML 里可解析的字段**。正解不是「在 check 里加个下载步骤」，而是**新增一个 `status` job
（`needs: [check, instrumented]`）**：两个 job 的工件都下来，报告在那里生成与提交。
这会把报告生成从 `check` 里搬走（连带 `JOB_STATUS` 与提交步骤的归属），是一次工作流重构 ＋
一个解析器 ＋ 它的用例。**按「做一半会留下更坏的状态」没动手。** 顺带一条已做的：`tools/tests`
接进 CI 之后，它立刻暴露出一处从未生效的守卫（见 §13.10）——那正是「接 CI」这条决定的价值。

**③为什么没做，以及它撞上的那条缺陷。** 「接线」要四个落点（呈现／导出报告、设置页的来源校准提示、
门槛常量复核清单、启动路径读一次 go/no-go 门），**前三个涉及还没有设计定案的界面**——在没有界面口径
的情况下动手等于替你发明产品。更要紧的是第四条：`fsrsBeatsBaseline` 是 spec §2.20 的
**go/no-go 门**，而**它今天的判据本身有已知缺陷**（§8 已记：log-loss 绝对区间会「永久拒绝任何改进」，
今天靠人手动的 `useFsrsScheduling` 兜着）。**在这个判据修好之前把它接到启动路径上，
等于让排期按一条会永久否决 FSRS 的规则自动切换**——那不是接线，是引入一个新缺陷。
所以这一条的正解是**两步**：先修判据（算法问题，属你的「科学算法都支持改」范围），再接线。

### 13.10　接 CI 时立刻掉出来的那处「存在但从不运行」（2026-09-14，新发现）

把 `tools/tests` 接进 CI 的第一步是**先在本机把它跑一遍**——结果 41 条里 **4 条红**。
根因不在用例，在被测代码：`tools/teaching_sources/epub_audit.py` 两处写
`ElementTree.XMLParser(resolve_entities=False)`，而**实测** `resolve_entities` 在
Python 3.13 的 stdlib 里**根本不是 `XMLParser` 的参数**（`TypeError: unexpected keyword argument`）。
于是那两行一执行就炸，**调用它们的四条用例一直红着没人知道**，因为 `tools/tests` 从来不进 CI。

**威胁是真的**：实测默认的 `ElementTree.fromstring` **会展开**内部实体
（`<!ENTITY b "&a;&a;&a;">` → 16 个字符），嵌套声明可以放大成 billion-laughs。

**修法不依赖版本相关的关键字名**：新增 `_parse_untrusted_xml`，**拒绝带 DTD／实体声明的文档**
（EPUB 的 container.xml 与 OPF 都不需要 DOCTYPE，需要放行的情形不存在，所以失败朝关闭一侧倒），
两处调用点原有的错误文案保留。新增用例把守卫**真的会拒绝**钉住；变异（删掉守卫那一行）按
**事前预测的红集 = 它一条**实测。`tools/tests` 由 41 → **42 条全绿**，并已进 CI。

**这条与 N-31／R-13 同源**：一份"看起来有门"的报告与一份"看起来有守卫"的代码，
只有真的去跑它才会分开。这也是「把 tools 测试接进 CI」这条决定**当场就赚回来**的一次。

### 13.11　合并面实测：能合，但不是「合一下」——两处语义冲突要你先定（2026-09-14）

用 `git merge-tree --write-tree` 干跑（不碰 refs／index／工作区）：**11 处冲突**，
其中 **6 处落在 `main` 这轮「拆除复习自评通道」的同一片区域**。

**`main` 这轮做了什么（`d0bc3b2e`，17 文件 −1253 行）**：复习自评／评级通道整体拆除，
复习作答改由**讲题判定**。它删掉了 `StudyRatingSubmissionService`(−146)、
`CapturedReviewLoopInstrumentedTest`(−202)、`RoomBackedReviewRatingTest`(−244)，
并把 `StudySubmissionPreparer`(−265)、`RoomBackedStudyExperienceRepository`(−99)、
`StudyExperienceRepository`(−133) 大幅削掉。**实拍屏的契约被换掉了**：现在的签名是
`(onBack, entry, queuePosition, queueSize, tutorJudgedAvailable, onOpenTutorJudge, modifier)`
——**不再收 `prerequisiteRemediation`，也不收 `reTeachOpening`**；而 `main` 的
`SmartMistakeBookDestinations` 仍然算出这两个值，只喂给**策展屏**。

**于是两处冲突是语义的，不是机械的：**

1. **`CapturedReviewSessionViewModelTest.kt` —— `main` 删除、本分支修改，而本分支的那次修改
   就是我这轮为 N-16 新加的 `reTeachOpeningStaysAcknowledgedAcrossRecreation`。**
   同一文件在 `main` 上已不存在（它的 VM 随自评通道一起没了）。
2. **`StudyRatingSubmissionService.kt` —— `main` 删除、本分支修改**（本分支只有 `059af83c`
   那次审计改动碰过它）。两处的正解是**接受 `main` 的删除**，但那意味着我这轮的 UI 用例要
   **重新表达或撤销**。

**最要紧的一条（必须你定，我不擅自选）**：`main` 的实拍屏把「实拍题的唯一作答面是讲题判定」
写成了产品判断，并在重写时**去掉了前置补救参数**——那正是本分支 `12877056`（批 2 第 3 项）
给实拍屏加上的东西。也就是说：

- **本分支的主张**：§2.9 补救与 §2.16 开场重教**不区分题的来源**，实拍题也该有；
- **`main` 的主张**：实拍题的作答走讲题判定，那条路上由讲题侧处理。

两者不是同一件事的两个写法，是**两个产品判断**。合并时必须选一个：
① 若「讲题判定已涵盖」→ **撤销本分支 N-16 在实拍屏那一半**，只保留数据层那一半
（范围改读题库，那一半在两种架构下都成立），并把这条结论写进 N-16；
② 若你认为实拍题仍该有重教／补救 → 那是在合并时**回退 `main` 的一个产品决定**，
不能由施工顺手做。

**落盘还有第二个前置**：`main` 的工作区现在有**并行会话的 46 个在途改动**，且 `main` 自己的
体量门正因为其中一个文件（`SmartMistakeBookRoot.kt` 工作树 1011 行）**报 1 error**——
`d0bc3b2e` 的提交信息里也自己注明了那是"并行会话的在途编辑，本提交未触碰"。
所以即使在工作区里把冲突解完，**把这棵树推上 `main` 这一步也不该越过他们**。

**结论**：技术上无死结，值得合；但它是一次**需要先定架构口径的集成**，不是一条命令。
在那之前，本分支的价值（44 个提交的取证与修复）以现状保存是安全的。

### 13.12　合并的完整解法（已解出，尚未落地）与一个卡住它的发现（2026-09-14）

11 处冲突**全部解过一遍**并逐个确认了正确解，然后**中止**了那次合并。中止不是没解出来，
而是第二件事：**`main` 提交的那棵树自己编不过**。

**卡住它的发现（决定性）**：`main` 提交的 `OpenAiModelTaskAdapters.kt:104` 用了
`input.userHint`，而 `userHint` 这个字段在 `main` 的**提交**里**根本不存在**——
`git grep userHint main` 全树只有 4 处命中，**全在那一个适配器文件里**（用法 ＋ 私有 helper），
`main` 的 `ModelTasks.kt` 命中 **0**。它的声明在**主树的未提交改动**里
（`core/model/.../ModelTasks.kt:184`，属并行会话在途工作）。
也就是说：**`main` 的 HEAD 依赖它自己的工作区才编得过**；把它的提交合进来，
得到的一棵树**无法编译、无法验证**。**不提交一个验证不了的合并**——这是中止的理由，
与"解得对不对"无关。

**11 处冲突的正确解（已验过，可直接复用）**：

| 文件 | 正解 | 理由 |
|---|---|---|
| `app/.../SmartMistakeBookDestinations.kt` | main 的 `tutorJudgedAvailable`/`onOpenTutorJudge` **＋** 我方的 `prerequisiteRemediation`/`reTeachOpening` 两个参数 | 自评通道已被 main 拆除；补救/重教与它正交（见下） |
| `app/.../SmartMistakeBookRoot.kt` | 取 main | main 在函数更早处（223 行）已声明 `startupState`；我方那行是重复声明 |
| `core/data/androidTest/.../BackupRestoreInstrumentedTest.kt` | 两边都留 | 各插各的（我方 `newGenerationSeed()`／main 的 `companion object`），互不相干 |
| `core/data/.../capture/RoomBatchImportRepository.kt` | 留我方 | 只是一个注释 |
| `core/data/.../model/OpenAiModelResponseParsers.kt` | **只一行** `confidence`（用我方 `MISSING_TOOL_CONFIDENCE`）＋ main 的 `extendedResult` | 两边声明的是**同一个属性**，拼接会重复（我第一次就踩了这个） |
| `core/model/.../TutorToolLoop.kt` | 同上 | 同上 |
| `core/data/.../study/RoomBackedStudyExperienceRepository.kt` | 取 main | 那段是自评提交方法本体，main 已删 |
| `core/database/.../dao/ProblemDao.kt` | 保留我方的常量抽取形式，**常量内容换成 main 的 SQL** | 见下面的 KD-7 |
| `feature/review/.../CapturedReviewSessionScreen.kt` | 取 main 的新结构，再把两个参数 ＋ 重教门 ＋ 补救卡补回去；**确认状态用 `rememberSaveable`** | main 之后这个屏是**无状态**的（没有 ViewModel），所以不能再走 `SavedStateHandle` |
| `.../StudyRatingSubmissionService.kt` | `git rm`（接受 main 的删除） | main 有意拆除自评/评级通道 |
| `.../CapturedReviewSessionViewModelTest.kt` | `git rm`（同上）；我方为 N-16 新加的那条用例**随之作废**（VM 已不存在） | 同上 |

**顺带一个真实收获（合并在数据层带来 main 的 KD-7 修复）**：`c96cc4d4 fix(database):
错题目录的记忆字段改读真实投影表（KD-7）`。它消灭的失败是——`observeActiveMistakes` join 的
`problem_memory_state` 只有 `FixtureSeedDao` 会写，**设备测试因为走 seedFixture 而看到真值、
全绿，生产里没有 fixture seed，于是每一行的 `next_review_at_epochminis` 都是 NULL**；
`library_catalog` 视图早在 v35→36 就改读了 `learner_problem_memory_state`，这两条查询被漏掉。
**我方那份 `MISTAKE_CATALOG_SQL` 正是被漏掉的旧版本** ⇒ 合并会把这条生产修复带进来
（解冲突时必须把常量内容换成 main 的版本，否则会静默把它改回去）。

**关于那条我以为要你定的架构取舍：它不存在。** main 的 `reTeachOpening` 与
`prerequisiteRemediation` 实现**都仍以 `teachingArtifact(...) ?: return null` 起手**——
`main` 是同一起点上的**另一条线，从来没有过我这批 S-5/N-16 修复**。它这轮拆的是
「复习自评/评级通道」（提交原话：复习作答与评级不由学生决定），而 spec 里被标「已废止」的
只有自评的**权重表**，§2.9/§2.16 未动。两者正交：一个拆「学生自己判对错」，
一个补「缺前置/leech 时给材料」。所以合并的正解是**保住双方各自的工作**，
不需要回退任何一方的产品决定。

**可执行的前置顺序**：①等并行会话把它们那批在途改动落地（`main` 才自洽可编译）；
②在本隔离树里合 `main`、按上表解 11 处；③跑全套（JVM ＋ 仪器化源编译 ＋ Room schema 校验）；
④**落地到 `main` 这一步留给并行会话**——他们的工作区有 46＋ 个在途改动，且体量门正红。

### 13.13　合并已完成（在分支上），落盘卡在一个文件上（2026-09-14）

`main` 自洽之后重做了合并。这一次做完了：**18 处冲突逐个解、两处语义冲突按"保住双方"合并、
整棵树验证通过**，并已提交为 `1395c606`（合入 main 的 25 个提交）；随后 main 又前进一个提交
（`e754bf2e` 图片链上界核对），也干净合入（`de1ff0c7`）。**分支现在包含 main 的全部提交**
（`git rev-list --count HEAD..main` = 0）。

**验证（合并后的整棵树，都是当前证据）**：JVM 全套 **1662 条 0 败**（`:core:model`／`domain`／
`data`／`database`、`feature:library`／`review`／`capture`／`tutor`、`:app` 的 localFirst 与
strictOffline 两个 flavor）；`tools/tests` **42/42**；`:core:data`／`:core:database`／`:app`／
`:feature:capture`／`:feature:library`／`:feature:review` 的**仪器化测试源全部编译通过**；
`:app:assembleStrictOfflineDebug` 通过。

**这一轮新看清的三件事**（都是解冲突时才显形的，值得留下）：

1. **KD-7 必须带进来**：main 的 `c96cc4d4` 修的正是我方 `MISTAKE_CATALOG_SQL` 的旧版本——
   `problem_memory_state` 只有 `FixtureSeedDao` 会写，于是**生产里每行的
   `next_review_at_epoch_millis` 都是 NULL，而设备测试走 seedFixture 看到真值、全绿**。
   解冲突时把常量内容换成 main 的 SQL（含 `learner_problem_memory_state` 与投影过滤）。
2. **`isRetryable` 是两条线各自裁定的合取**：main 要按**类别**排掉恢复回滚/隔离（`DATABASE`，
   那两处 `retryable` 是默认值，只读标志排不掉）；我方要读**归类处写下的标志**
   （投影三类各有各的实话，"排空预算用尽重试就是通路"，只按类别会把这一类一起杀掉）。
   最终写成 `errorCategory != DATABASE && retryable`——两条意图都在。
3. **main 那 179 行"新增"其实是我 R-01 拆走的块**：12 个类型**全部**已在我拆出的三个文件里，
   逐个核对后唯一的新东西是**一个字段** `SplitImportQuestionSeed.splitDraftId`。
   整体取 main 会**重复 11 个类型**。

**落盘为什么还没做（实测，一个文件）**：把本分支与 main 的差异（129 个文件）与主树的
**在途改动**取交集，命中**恰好一个**——`app/src/main/kotlin/com/tingyun/smartmistakebook/
SmartMistakeBookRoot.kt`：本分支改过它，而并行会话**当前仍未提交**对它的修改。
git 的快进在这种情况下会**拒绝**（"local changes would be overwritten"），这是对的。
**没有用 `update-ref` 绕过**：那会把 main 的 ref 换掉却不碰工作区，于是他们那份
基于旧 main 的在途编辑会静默变成"相对新 HEAD 的改动"，提交时可能把合并进来的改动一起退回去
——等于在别人脚下换地板。

**落盘的正确时机**：并行会话提交或 stash 掉那一处（18 个在途条目里已跟踪的只剩它），
之后 `git merge --ff-only` 就是一条命令的事。**在那之前，分支已具备全部条件**，
并在隔离树里保持可验证状态。

### 12.6 账本规模：无界、永不裁剪、单学习者（2026-09-12 核实）

这一节决定批 0 第 3 项的上界该怎么取——结论是**它取不出一个舒服的值**，原因在最后。

#### 事实

| 事实 | 证据 |
|---|---|
| 账本＝`projection_outbox`（身份/序列/指纹）＋ 5 张载荷表 | `LearningEntities.kt:507-532`；`eventKind` 五种，列于 `StudyProjectionDrainer.kt:192-196` |
| **账本行永不被删除** | 对 5 张载荷表与 `projection_outbox` 的 `DELETE FROM` 全仓零命中；无 prune/trim/archive/retention 代码；唯一整库删除是用户主动的 `deleteAllData()` |
| **每次安装只有一个学习者** | `learnerId: String = DEFAULT_LEARNER_ID`（`RoomBackedStudyExperienceRepository.kt:89`），三个工厂重载都不传该参数 |
| ⇒ **账本总量随安装生命周期单调增长，无上界** | 由上两条推出；仓库与 `docs/specs/`、`docs/current/` 中**没有任何**关于账本规模、增长率、重放成本、裁剪或归档的陈述（已对相关关键词全量 grep） |

**每单位活动产生几条事件**（全部一手核对）：

| 活动 | 事件数 |
|---|---|
| 答一道复习题 | 1 `ATTEMPT`（`AttemptTransactionDao.kt:363-366`，每次恰好一行） |
| 拍一道错题 | **0** —— 采集流程不写任何账本事件 |
| 一次讲题回合 | 回合本身 0；被接受的 `MASTERY_UPDATE` 各 +1 `CHAT_EVIDENCE`（被拒的写 0，`ChatEvidenceDao.kt:63-67` 先把 rejected 分出去）；每个变可见的解答目标 +1 `TUTOR_ANSWER_EXPOSURE_OUTCOME`，而 `bindAnchor` 会把该会话**所有**待定曝光一次性物化（`TutorExposureDao.kt:180-182`），故一次提交可产生多条 |
| 一次揭晓答案 | 1 `ANSWER_REVEAL_OUTCOME` |
| 一次迁移 v41 | **一次性批量**：为每条尚无 outbox 行的历史 `learner_chat_evidence` 补一行（`ChatEvidenceMigration.kt:135-149`） |

所以量级由「累计答过多少题」主导：每天几十条是常态，多年安装到 **10⁴～10⁵ 量级**是可达的。
（`MAX_WRITES_PER_LEARNER_WINDOW = 100`/小时 之类只封住讲题证据那一支，不是账本总量。）

#### 真正的问题：重放本身是 O(账本) 内存

`commitFullReplay` 先 `ledger.validPrefix.map { it.event }` —— 把**整份账本**物化成一个 List；
而 `LearningProjector.replay` 无论怎么切读，都必须持有 `attemptsById`（全部作答）、
`memoryStates`、`masteryStates` 以及四张封顶 4096 的记录表。**所以重放的内存占用与账本规模同阶，
且这不是读法问题，是 `replay` 两遍结构的固有性质。**

配合 ADR-0002（不可分块），推出：

> **一份大到装不进内存的账本，在现有设计下是不可恢复的。**
> 上界因此**不是可调的旋钮，而是对一条硬限制的承认**。

#### 因此第 3 项的上界只能这样取

- 取值必须**明显高于任何真实安装能达到的规模**，它的作用是抓住病态增长（例如某次迁移把账本放大一个数量级），
  **不是**做容量规划。参照系：同族常量是 `MAX_PROJECTION_BATCH_SIZE = 4_096`、
  `MAX_APPLIED_ATTEMPT_RECORDS = 4_096`、增量路径上界 64 × 100 = 6,400——
  而账本在几年内轻易超过后三者，所以**不能照抄这些值**。
- 更根本的方向（不在批 0 范围，但应记下）：现有设计没有任何「重放地平线」概念，
  而 `requireCompatibleSnapshot` 要求重放**必须从空快照开始**。
  要想让大盘账本可恢复，需要一条按投影版本标注的**基线快照**，让重放从基线而不是从序列 1 开始
  ——其成立条件可机械检查（剩余账本里若有修正，其目标的序列号不得早于基线）。
  这既解决内存，又不违反 ADR-0002（因为基线本身是一次完整重放的产物）。**是否要做，是独立的架构决定。**

---

---

## 13. 附录

### 13.1 统计

| 项 | 值 |
|---|---|
| 缺口在册 | **56**（P1 ×9 ／ P2 ×26 ／ P3 ×21） |
| 审计者提出 | 57 条（含 2 条被推翻）→ 55 条有效 ＋ 主循环新增 F-01 |
| 对抗复核结论 | CONFIRMED 19 · UNCERTAIN 2 · REFUTED 2 |
| 证据等级 | `✔` 19 · `◆` 20 · `○` 15 · `△` 2（＋ `✕` 2 不计） |
| 复核者独立漏项 | 9 条（未计入 56，按未验证处理） |
| 本会话新增修正 | **11 条**（S-1～S-11）。S-10 出自批 3 第 4 项的**施工前回读**（订正"优化器没有 log-loss 门"这一表述）；**S-11 出自批 3 第 3 项的施工前回读**——推翻的是**本记录作者自己给出、且已被用户采纳的建议**（"F-02 统一到分数日历天"），一手资料（fsrs-rs `model_v6.rs` / `inference.rs`、已发布 `days_elapsed: u32`、py-fsrs、Anki rollover）显示分数天是 **FSRS-7** 的能力而本项目实现 FSRS-6，故改为统一到**整天**的本地日历日差。其中 4 条是对修复方案／findings／**本记录自身断言**的证伪（S-1、S-2 的机制描述、**S-8**、**S-9**）、1 条（S-7）是对证据行号引用的订正。**S-9 与 S-11 是在施工阶段（而非复核阶段）推翻本记录断言的**——它们来自「动手前回读一手来源／改完之后回读受影响逻辑」这一步，而不是来自复核。**S-11 的教训已写进 §3：「用户批准了」不等于「这条建议被核实过」** |
| 新发现未入册项 | 1 条（`agentConsentGranted` 两处硬编码 `true`，P2，见 S-2） |
| §12.5／§12.6 追加发现 | **31 条**（N-01…N-31）。其中 **N-06**（排空步数上界报成 CAS 冲突）由批 0 第 2 项带出并取证（`6_401` 条积压 → 64 次提交落库后抛 `ProjectionCasConflictException`）；**N-07**（`replay` 造一份没人读的预测）、**N-08**（`errorCategory` 只写不读）、**N-09**（`LearningLedgerIntegrityException` 被当通用异常用）、**N-10**（知识包安装半装后永久卡死）由批 0 第 4、5 项带出；**N-11**（配图模型硬编码为 OpenAI 专用，`P2`·`product-boundary`）由批 1 第 1.2 项推翻旧判据后带出（见 §3 **S-8**）；**N-12**（题干读取失败没有自己的状态，`P3`·`missing-handling`）由批 1 **N-03 的范围边界核查**带出，并**绑定到批 2 第 3 项**——绑定理由先被订正、**随后整条被判定不可达并撤回**（那条读取在生产里根本抛不出异常，见 N-12 条内两次复核记录；不要把 N-12 记成"已修"）；**N-13**（`LEARNING_SUMMARIZE` 静默学习小结在**任何**构建里都不可达，`P2`·`dead-channel`）由批 2 第 1 项的动手前核查带出，**属产品决策，不擅自实现**；**N-14**（读侧保持率估计仍用出厂衰减，`P3`·`inconsistent-read-path`）由批 3 **F-01 的调用点枚举**带出——**其"队列那一半"已随批 3 第 3 项修掉**、余下的默认曲线实例仍登记；**N-15**（「今天/昨天/N 天前」用墙钟整日地板而非学习者日历日，`P3`·`display-convention-split`）由批 3 第 3 项**枚举 F-02 影响面**时带出，同物种但**不喂任何计算**（是展示标签），登记待与 UI 时区传递一起做；**N-16**（开场重教 spec §2.16 的范围取自策展件、且**没有任何一屏渲染过它**，`P2`·`dead-channel`）由批 2 第 3 项带出——它与该批的补救同病，但按本仓自己的说法是"必经步骤"，放哪里属**产品可见**决定，故只登记不擅自设计；**N-17**（搜索延迟的书面预算不可能被验证：恢复真实测量后 FTS 三条计时超预算 3.2–20.9 倍，而目录侧两条都在预算内，`P1`·`unverified-gate`）由批 4 **性能门施工**带出，**预算判定权在用户**；**N-18**（从 ≤v31 升级上来的库，**升级前就存在的题永久搜不到**：`countIndexed()` 读的是内容行、首次引导分支永不触发，`P1`·`wrong-guard`）由批 4 **迁移矩阵补数据断言**时带出——**已修并验证**（改读 FTS 影子表 `_docsize`，两次变异按集合相等）；**N-19…N-29** 由 **2026-09-13 四路并行审查**带出（补救通道每卡整读目录、KC 口径四处不一致、重试按钮不重跑失败步骤、契约注册表生产零读者、视觉通道第二条证据的 delta_t 仍为 0（**已修**）、F-01 的机制未拆、日界仍有四处游离、标定回路消费者零覆盖、配图通道未证明走闸门、批大小两处各写一份），**逐条处置见 §13.5**。**N-31**（状态报告的总体判定只有一个可能取值，且把"没测"与"没过"读成同一件事，`P2`·`over-reporting`）由我收 **R-13 第 3 条**时一手核实——"没有测量来源就给结论"那一半已修（连带 AAB 体积写错列），余下"PASS 覆盖什么、恒空的行由谁产"是取舍。**N-03／N-06／N-10 已在批 1 修完并验证**（见 §11） |
| 被并发工作树结清 | 1 条（`catalog-retrievability-always-null` ＝ 并发会话 `KD-10`，见 §12.3）· 另有 4 条经比对**维持成立** |
| 顶点规模 | 17 模块 · 44 版 Room schema · 7 审计维度 |
| 批 0 进度（2026-09-12 收尾） | **5 项全部落地**：1 触发测试、2 确定性测试、3 重放上界＋显式失败、5 失败出口；第 4 项的原方案被推翻并改落到第 5 项（D-12）。**每项都有变异校验**，共 **24 条**变异逐条点名命中的断言（第 1 项 4、第 2 项 6、第 3 项 4、第 5 项 10），另 1 条因语言限制**无法构造**；第 1 项在夹具重构后另有 3 条复验。回归：`:core:domain:test` 395 条 · `:core:data:testDebugUnitTest` 396 条 · `:app:testLocalFirstDebugUnitTest` 26 条 · `:app:testStrictOfflineDebugUnitTest` 27 条，全绿；`:app:assembleLocalFirstDebug` 通过 |

| 批 1 进度（2026-09-12 收尾，**本批全部完成**） | **1.1 ／ 1.3 ／ 1.4 ／ 1.5 ／ 1.7 ／ N-03 ／ N-06 已完成并验证；1.6 完成（纯文档）；1.2 作废（S-8 → N-11）**。原定「1.5 等并发会话落地」的**前提已满足**（那 12 个提交已进 `main`，三个目标文件与它无交集）；**变基到 `4c76125` 仍待用户决定**。**⚠ 2026-09-13 补一条会影响变基决定的事实**：批 1 第 1.1 项（S-2）所在的同意轴，在 `main` 上已被产品决定换成了另一种答案（`e462f1ea` 删掉了 `ModelAgentConsentStore`，改成"配置模型即同意"），本分支那半从未进过 `main`，两边**不能同时成立**——逐条事实与影响见 §3 **S-2** 的复核块。变异校验 **20 条**（1.3 四条、1.4 三条、1.7 四条、1.5 四条、N-06 五条），逐条点名命中的断言；其中 **1.4／1.5／1.7／N-06 断言的是「命中集合相等」**，不是「有没有变红」。**N-03 没有变异**——它的改动是「失败降级 ＋ 留记录 ＋ 取消上抛」三个性质，证据是四条 JVM 断言（含一条取消路径），没有可构造的生产变异。施工中另有一条**无法构造**的变异（语言限制：`ProjectionCasConflictException` 是 final）。详见 §11 批 1 执行状态 |

| 批 2 进度（2026-09-13） | **第 1 项的 `IMAGE_PIPELINE_CLASSIFY` 已完成并验证**（＋3 条用例、1 条变异）；第 1 项的 `LEARNING_SUMMARIZE` **仍是待办**（产品决策 N-13）；第 2 项（修正账本）**仍是待办**。**R-10 已完成并验证**：`readForDependents` 加 `relation_type` 过滤 ＋ **新增仪器化用例** ＋ 1 条变异还原改动三点。`:core:database:connectedDebugAndroidTest` 全量 **150 / 0**。**第 3 项（前置补救 ＋ N-12）已完成并验证，且动手前纠正了本项三处定性**：材料那半早就是真库、N-12 的绑定理由不成立、**唯一渲染补救卡的策展屏根本不是实拍题走的那一屏**——真实范围是数据＋装配＋呈现三层。用例外 3 条、变异 2 条（命中集合各为单元素，且都是新增的那一条：旧覆盖测的是策展路径）。**N-12 那一半当晚撤回**：四路审查里的 `review-app` 指出那条读取抛不出异常，复核属实，兜底机制已删除、N-12 改判"不可达"（见 §12.5 N-12）。**新登记 N-16**（开场重教同病且无处渲染，属产品可见决定）。详见 §11 批 2 执行状态 |
| 批 3 进度（2026-09-13 订正） | **F-01（衰减四处冻结＋施工中枚举出的第五处）已完成并验证**；断言 **6** 条（`FsrsDecayThreadingTest` **5** 条 ＋ `FsrsProjectionBehaviorTest` 接线 1 条 ＋ **2026-09-13 补的毕业闸门 1 条**），**8** 条变异逐处点名命中的断言（含 1 条**记录在案的缺口** M4b）。**零行为变化**（`w20` 至今未离开出厂值），故不升投影版本、不需重放。`:core:domain:test` **402 / 0**。**同日订正两处记账**：原写「`FsrsDecayThreadingTest` ４条」实为 5 条；原写「闸门的衰减已接线」为真但**在默认 0.9 下不可观测**，生效条件是目标保持率 ≠ 0.9 且 ≠ 0.8（见 §11 批 3 的阈值表）。余下的只读估计登记为 **N-14**（与 F-02 同片，一起设计）。详见 §11 批 3 |
| 批 3 第 4 项进度（2026-09-13） | **写回处加"相对现行参数"的 hold-out 门：已完成并验证**（审计 **S-10**——原判"没有 log-loss 门"被回读一手来源订正为"门在，但对照物是**出厂默认**"）。`optimize` 新增 `incumbent` 形参（默认值 ⇒ 首次调用逐位等价旧行为）＋ `adopted` 位（**唯一的结果口径字段**，其余字段一律描述"这次尝试"）；装配点只在 `adopted` 时写回。**2 条变异**（闸门恒假／对照物换回默认）逐条被同一条新用例抓住。`:core:domain:test` **403 / 0**、`:core:data:testDebugUnitTest` **413 / 0**。spec §2.11 的 log-loss **绝对区间**未实现（归属 R-07，理由见 §11）。详见 §11 批 3 第 4 项 |
| 批 3 第 2 项进度（2026-09-13） | **视觉通道 `delta_t` 不再恒 0：已完成并验证**。两半——①根源（从当前投影取"上一次复习的时间戳"）②一轮内部（同一次排空里以**紧邻的前一行**为界推进）。施工中撞到 `ProblemMemoryState` 的不变量（`nextReviewAt ≥ lastReviewedAt`），**没有靠给无关字段编值绕过**，而是把 `ReviewLogSink.record` 的入参收窄成 `previousReviewedAtEpochMillis: Long?`（对既有 6 个调用点逐位等价），排查过程见 §11 批 3 第 2 项。`:core:data:testDebugUnitTest` **413 / 0**（＋2 条用例） |
| 批 3 第 3 项进度（2026-09-13） | **读侧「已经过天数」收口到一个函数：已完成并验证**（＝审计 **F-02** 的全部 ＋ **R-04** 的目的那一半 ＋ **N-14 的队列那一半**）。新增 `ReviewCalendar` 作为「学习者本地日历日」的**唯一定义**，两个读侧出口（`ForgettingCurve.estimateAt` 的 FSRS 分支、`KnowledgeReviewQueue.knowledgeRecallRiskByNode`）改为**委托**它；写入侧与 LEGACY 分支**一个字节没动** ⇒ 不升投影版本、不需重放。**3 条变异**，命中集合**逐条不同**（M1 两条／M2 一条数值／M3 一条一致性），且 M2 的预期集合第一版写错、**由脚本纠回**。新增 4 条用例（`ReviewCalendarTest`），判别格是**跨午夜不足 24 小时**。`:core:domain:test` **407 / 0**（＋4）、`:core:data:testDebugUnitTest` **413 / 0**——**既有用例零变红**，因为它们全部落在"两种口径逐位相同"的格子上（§9 模式 F）。**方向由 §3 S-11 纠正**（原建议"统一到分数天"被自己推翻：分数天是 FSRS-7 的能力，本项目实现 FSRS-6）。同批带出 **N-15**（UI 的「今天/昨天」标签同一物种）。详见 §11 批 3 第 3 项 |
| 批 3 第 1 项进度（2026-09-13） | **同日聚合按 S-4 的两步结清：取「订正说明」而非「改代码」**。① **可达性**：无任何不变量禁止同卡同日第二次记忆更新（闸门只有 §2.7 冷却：主观 6h／视觉 1h；计划器不排除未到期卡）；**但** 1h 冷却那条视觉通道已被 2026-09-06 的结构化场景渲染隔离关掉（`VisualInteractionEventSink` 无生产写入方），**当前实际可达的是主观 6h 那条**。② **放大算死**：短程乘子不动点 `S* = 2.1210577` 天，反复同日成功最多把 `S` 推到 `max(S₀, S*)`，**与证据条数无关**；换算成调度**最多值一天**（`S₀=0.212` 时 1 天 → 2 天）。③ **spec 读法纠正**：§2.15 那条是两句、不唯一地合成一个算法；实现按第二句照字面执行，真正偏差只有"跨日更新的评级来源"一条。**产线代码零改动**、不升投影版本、不需重放；`docs/specs/mastery-scheduling-spec.md` §10 注记整段重写；新增 `FsrsSameDayBoundTest` **6 条**（走产线 `updateMemory`）＋ **2 条变异**按集合相等验证，其中 M2 的预期集合**第一版写错、由脚本纠回**。先验数值用 Python 独立算过一遍，再逐位断言到 1e-12，两边一致。详见 §11 批 3 第 1 项 |
| 批 4 进度（2026-09-13） | **大部完成**：**CI 单测清单补 `:core:ui:testDebugUnitTest` 已完成**（11 条此前 CI 从不运行的用例；该步的完整命令逐字重跑通过），并带出一条新发现——清单里的 `:knowledge-production:test` 是**空任务**（该模块 `src/test` 下 0 个测试文件）。drainer 版本触发测试已并入批 0；R-04 部分、R-10 完成。**性能门已完成**（真实播种 ＋ 预算标 `NOT_MEASURED`；带出 N-17：FTS 三条计时超书面预算 3.2–20.9 倍，判定权在用户）。**三个 `Skeleton` 已完成**（三条有断言的启动恢复用例，16/16）。**R-03 完成**（含追补的网格用例与一处等价变异的教训）。**迁移矩阵数据断言完成**（44 版实跑 ＋ 一次破坏性迁移变异）。**带出并修好 N-18**：从 ≤v31 升级的用户，升级前就存在的题永久搜不到（`countIndexed()` 读的是内容行，引导分支永不触发）；修法是一行 SQL 改读 FTS 影子表 `_docsize`，**两次变异**按集合相等验证。**四路并行审查（2026-09-13）**：7 条发现里 **R1／R2／R3 已修并验证**、R3 的派生方案经技术性反驳后只采纳一半、**R4～R7 登记为 N-19…N-22**（R5／R7 的报告被截断，待补发），详见 **§13.5**。**其余待办**：R-01／R-02／R-05～R-09／R-11～R-13 与 N-19～N-23 |

**冻结树最终基线（2026-09-13，所有源码改动停下之后）**

取数方式：直接读 Gradle 写下的 JUnit XML（**不读控制台摘要**，见 §13.4 的读数纪律），
每个 XML 只计一次（Gradle 会在 `connected/`、`connected/debug/`、`connected/debug/flavors/`
下**重复**写同一份结果，按路径去重）。JVM 套件用 `--rerun-tasks` 强制真跑。
**本轮取数时间：JVM 2026-09-13 12:03–12:04；仪器化 03:16–03:35（另 §11 批 2 第 3 项那一批
12:53 前跑完 app 两个变体与 core:data）。**

> **窗口不齐这件事要说清楚——分两层。**
> **① 最后一处源码改动是批 2 第 3 项**（core:data 主源 ＋ feature:review 主源 ＋ app 主源与测试）。
> 除它之外，JVM 各套件与 03:53 那轮的差异只有批 3 第 1 项新增的 `FsrsSameDayBoundTest`（＋6，
> core:domain 因此 407 → 413）。
> **② 仪器化只能分两段报**：`app`（两个变体）与 `core:data` 在批 2 第 3 项之后**重跑过**
> （12:02 前，见各套件一行）；其余 6 个仪器化套件（`core:database` 150、`core:export` 12、
> `core:visual-ui` 6、`feature:capture` 23、`feature:library` 26、`feature:tutor` 46）的结果
> 取自 03:16–03:35 那一轮。**它们为什么不必重跑**：批 2 第 3 项改的三个主源集分别是
> `core:data`、`feature:review`、`app`——前两者是那 6 个套件所在模块的**依赖**，
> 所以严格说它们的输入**变了**（`core:data` 的实现改了）。**这一条的诚实结论是：
> 那 6 个套件的结果是"改动前的绿"，不是"改动后的绿"。** 若要全部对齐，需再跑一轮
> 约 15 分钟；本轮**没有**跑，因此**不声称**它们验证了批 2 第 3 项。
> 把它们列入本表是为了记录**当时的证据**，不是为了声称它们覆盖了之后的改动。

> **第三段窗口（2026-09-13 13:26–13:28 与 15:37–15:38，批 4 的后半段之后）**：批 4 又落了
> 九个提交（`7c63dfcd` 备份恢复三条用例、`9522545d`＋`298065a4` R-03、`96441d5a` 性能门、
> `463cb0dc` 会话目的地结构整理、`6c1f7a1b` 迁移矩阵数据断言、`529161e3` N-18、
> `d1ef314c` N-12 撤回、`f0e49f99` 契约注册表等价断言、`1d5f44d3` 实拍屏接线用例）。
> **JVM 半边已按这一轮的树重取两次**（16 个套件全部 `--rerun-tasks` 强制重跑；
> 第三次的 XML 写入时间落在 **15:55:41–15:56:56** 这一个窗口内，终值见下表：
> **1585 条、0 失败 0 跳过**；此后只给 `core:domain` 补了一条值钉用例（production 未变），`core:domain` ＋ `core:data` 复跑 830/0）。相对上一段的变化逐项对得上：两个 app 套件各 **−3**
> （`TeachingArtifactFailureMessagesTest` 随 N-12 一起删除，它测的是一段生产里不可能出现的文字）、
> `:core:data` **＋1**（契约注册表等价性断言）。
> **仪器化半边**：这三个提交碰到的套件都已按新树重跑——`core:database` **151/0**、
> `app` 两个变体各 **43/0**（strictOffline 7 条跳过）、`core:data` 的备份恢复类 **16/16**。
> 其余仪器化套件的结果**早于**这一批提交，**不声称**它们验证了批 4。

**JVM 单测：16 个套件，1586 条，0 失败**

| 套件 | 条数 |
|---|---|
| `:core:model:test` | 299 |
| `:core:domain:test` | 414 |
| `:core:database:testDebugUnitTest` | 67 |
| `:core:data:testDebugUnitTest` | 415 |
| `:core:ui:testDebugUnitTest` | 11 |
| `:core:export:testDebugUnitTest` | 14 |
| `:core:visual-runtime:test` | 11 |
| `:core:visual-ui:testDebugUnitTest` | 12 |
| `:feature:capture:testDebugUnitTest` | 99 |
| `:feature:review:testDebugUnitTest` | 33 |
| `:feature:tutor:testDebugUnitTest` | 89 |
| `:feature:library:testDebugUnitTest` | 33 |
| `:feature:profile:testDebugUnitTest` | 6 |
| `:quality:visual-benchmark:test` | 9 |
| `:app:testLocalFirstDebugUnitTest` | 36 |
| `:app:testStrictOfflineDebugUnitTest` | 37 |

**仪器化：9 个套件，443 条，0 失败，6 跳过**

| 套件 | 条数 | 备注 |
|---|---|---|
| `:core:database:connectedDebugAndroidTest` | 150 | R-10 的整轮全量重跑（见 §11 批 2）；**取自 03:16 那一轮** |
| `:core:data:connectedDebugAndroidTest` | 100 | **12:02 重跑过**（批 2 第 3 项改了 `core:data` 主源） |
| `:core:export:connectedDebugAndroidTest` | 12 | 本审计首次运行；**取自 03:32 那一轮** |
| `:core:visual-ui:connectedDebugAndroidTest` | 6 | 本审计首次运行；**取自 03:32 那一轮** |
| `:feature:capture:connectedDebugAndroidTest` | 23 | 本审计首次运行；**取自 03:35 那一轮** |
| `:feature:library:connectedDebugAndroidTest` | 26 | 本审计首次运行；**取自 03:34 那一轮** |
| `:feature:tutor:connectedDebugAndroidTest` | 46 | 本审计首次运行；**取自 03:34 那一轮** |
| `:app:connectedLocalFirstDebugAndroidTest` | 42 | **11:53 重跑过**（＋2 ＝ 批 2 第 3 项的新用例） |
| `:app:connectedStrictOfflineDebugAndroidTest` | 42 | **11:56 重跑过**；**6 条跳过** |

**仪器化合计 447 条、0 失败、6 跳过。**

**总计 2026 条、0 失败、6 跳过**（JVM 1579 ＋ 仪器化 447）。

三点必须与数字一起读：

1. **"0 失败"不等于"全都被验证过"**：6 条跳过集中在 `RootTutorFailClosedInstrumentedTest`
   与 `RootExperienceInstrumentedTest`，跳过就是**没有验证**，不能计入覆盖。
2. **这次的覆盖面与 CI 的清单对不齐，两个方向都有缺口——而两个方向都各有发现。**
   逐条核对 `.github/workflows/android-check.yml`：
   - **CI 的单测清单是 16 个任务，但里面没有 `:core:ui:testDebugUnitTest`。** 本次实跑把它补上，
     拿到 **11 条**——**也就是说批 4 记的「`:core:ui` 没进 CI 清单」是真的，而且不是一条空任务**。
   - 反过来，CI 清单里的 **`:knowledge-production:test` 本次没跑**：该模块 `src/test` 下
     **0 个测试文件**，那条任务在 CI 里是一条**什么都不跑的空任务**（新发现，登记为批 4 的补充）。
   - **仪器化那一侧对得上**：CI 列了 **9 个** connected 任务，与本次实跑的 9 个**完全一致**。
     所以上表里 7 行标「本轮首次运行」指的是**这个审计**首次跑，**不是** CI 首次跑——
     这两件事不能混为一谈，否则会把"我们没跑过"写成"没人跑过"。
3. **两条明确未跑**：
   - `:benchmark` 的仪器化套件（macrobenchmark，需要独立的、可 profile 的构建变体与应用安装，
     与常规 `connectedAndroidTest` 不是同一条路；CI 里的对应步骤只做
     `:benchmark:assemble`，**不跑它的测试**）。
   - `tools/image-mcp-server` 的 1 个测试文件——`tools/` **不在** `settings.gradle.kts`
     的模块清单里，**它不是本工程的 Gradle 模块**，不参与本基线。

### 13.2 被推翻的 5 条（不在这里当缺陷）

- **`mastered-anchor-unverified`**（MASTERED 锚点未核对会话证据）：复核者核对后发现**并发工作树里已经修好**
  （`RoomTutorToolRunner` 改用 `verifiedEvidenceAnchorCount`，`MasteryWriteGate` 新增真实子串核对）。
  → 教训：**并发工作树会让已提交代码的结论过期**，边界必须声明。
  （同因过期而已结清的第二条是 `catalog-retrievability-always-null`，见 §12.3。）
  > **2026-09-12 晚订正**：写这条时那份工作树还是**未提交**的；现在它已落地为提交
  > `d49296d`（`main` = `4c76125`）。结论不变，理由的时态更新——**"未提交"这个限定已经不存在**，
  > 见 §12.3 顶部的基线订正。
- **`attention-floor-unreachable`**（注意力系数门不可达）：事实成立，但
  `docs/specs/2026-09-02-tool-loop-wiring-design.md` 明确记为「有意延后，等 UI 采集接入再触发」。
  → 属已记录的设计取舍，不是缺陷。
- **批 0 第 4 项的原方案（重放后不变量校验器）**：它推翻的是**本审计自己给出的修复方案**，不是某条发现。
  动手前逐条回读源码后发现四条不变量**全已在计算点守住**（`ForgettingCurve.kt:78-80` 的 `require`、
  `LearningProjector.kt:588/971/1099` 的 `coerceIn`、`MemoryUpdateModel.kt:101-102` 的 `coerceAtLeast(1)`、
  `replay` 自己的三条 `require`），一个输出校验器**在现有代码上没有可守的失败**。
  → 教训：**"事后校验"在计算点已经保证时不是加固，是死代码**；而且 `coerceIn` 是静默的，
  事后校验连看都看不到它想抓的东西。该做的事因此反过来——不校验结果，而是**给失败一个名字**
  （D-12，落成批 0 第 5 项）。这条与 §13.2 前两条同源：都要求**动手前先回读一手来源**。
- **配图通道不遵守用户配置的 `modelId`**（§8 漏项 7）：回读 `docs/image-pipeline-spec.md:41-47`
  「**Models are split (per decision)**」与 `:117`／`:119` 的路由表后确认，**配图用 `gpt-image-2`、对话用用户的多模态模型，
  是一项已记录的（且被独立复核过的）设计决定**，不是缺陷。而原修复方案要把对话模型 id 传给
  `/v1/images/edits`——那会让**当前能用的 OpenAI 配图直接失败**（`supportsImageInput` 探的是视觉**输入**，
  见 `ModelProbeSpec.kt:11-13`，与图像**生成**是两回事）。→ 见 §3 **S-8**。
  教训：这条与 `attention-floor-unreachable` 同源（已记录取舍 ≠ 缺陷），但更进一层——
  它的**修复方案本身就是新的失败源**。那条判据被推翻后剩下的真实缺口（配图模型硬编码为 OpenAI 专用、
  `tools/image-mcp-server` 不在构建里）登记为 **N-11**，属产品决策。
- **§8.2 的因果断言「修 1.4 会连带关闭 finding 4 的主触发路径」**（2026-09-12 施工后核实，见 §3 **S-9**）：
  它推翻的是**本记录自己的一条推理**，不是某条 finding——1.4 关掉的只是支路径，主路径
  （「先伪后真」，即每道先复习后归类的题的常规生命周期）**按定义**不受 1.4 影响，仍需在组选择处修（第 1.7 项）。
  → 教训：`"修 A 会连带修好 B"` 必须回到 **B 自己的判定逻辑**上验证，不能由 A 的存在性推出来。
  这是九条修正里**第一条在施工阶段（而非复核阶段）**推翻本记录断言的——它来自「改完之后回读受影响逻辑」这一步，
  和 §13.2 前四条一样，都属于「动手前／动手后都要回读一手来源」这条纪律。

### 13.3 五个最值得先动手的点（按「收益 ÷ 成本」）

1. **`ChatEvidenceDao` 幂等**——`P1`，跨维度重复命中，且失败会让模型看到假的「写失败」。**但先读 S-1，别照原方案改。**
2. **网关能力集漏两个 kind**——`P1`，一条 parity 测试同时消灭模式 B，且两条已建成的生产链立刻复活。
3. **伪归因写入层限定**——`P1`，零版本升级，让知识层开始积累。**已于 2026-09-12 完成**（批 1 第 1.4 项，见 §11）。
   ~~且它连带关闭视觉证据被引走的两条路径~~ —— **这笔红利不存在**（见 §3 **S-9**）：视觉证据被引走有两条路径，
   1.4 只关掉较小的那条「先真后伪」，**主**路径「先伪后真」是常规生命周期、1.4 按定义放行它，
   要在组选择处修，即新登记的第 **1.7** 项。
4. **图片通道同意门**——安全红线，按 S-2 换开关。
5. **批 0 的收尾 ＋ 出口之后的下一组**——原先占这条的是「触发测试」，它**已于 2026-09-12 完成**
   （连同等价性测试，见 §11 批 0 执行状态）；接棒的「重放上界」与「失败出口」也**已完成**。
   批 3 的全部算法修复因此不再赌在未验证机制上，也不再落到一个说错原因的出口上。
   **下一组按成本排序**：**N-10**（知识包半装后永久卡死，`P2`，修法明确：给三步一个事务或让
   `require` 走重建）、**N-06**（排空步数上界报成 CAS 冲突，`P2`，已取证）、
   **N-03**（两条未包裹入口可能崩界面，`P2`）——三者都属于「写入侧的半成品状态在下次启动
   变成永久故障」或「失败原因说错」这两族，与批 1 的 **1.3 `ChatEvidenceDao` 幂等**同源，
   放在一批里做能共用同一套故障注入测试。
   注意 N-06 **不能照抄第 3 项的做法**：第 3 项的上界是「这次升级处理不了这么多条」，
   N-06 是「一次排空的预算用完、下次接着走」——后者是**可重试**的，不该被说成永久失败。

### 13.4 本机 Gradle 环境的实测记录（2026-09-12 起，2026-09-13 补读数纪律；为"证据可重跑"而记）

这一节不记录产品缺陷，只记录**这一轮验证是怎么跑出来的**，否则别人无法复现、也无法判断
哪些结论只是"这台机器上跑不了"。

**项目的正规入口跑不动。** 仓里的正规入口是 `tools/run-gradle.ps1` → 它 source
`tools/android-env.ps1`，后者做四件事：要求工作区在 `D:`、`subst S:` 指向本树根
（注释写明「QEMU's Windows launcher corrupts non-ASCII executable paths」）、
把 `GRADLE_USER_HOME` 钉到 `<树根>/.gradle`、并从 **`<树根>/.toolchains/`** 取
`gradle-9.6.1` 与 `android-sdk`。**`.toolchains/` 在主树与本 worktree 都不存在**，
所以 `run-gradle.ps1` 现在无法执行。

**实际用的替代跑法**（本轮所有 Gradle 证据都出自它）：

```
$env:GRADLE_USER_HOME = "<worktree>\.gradle"      # 本 worktree 自己的缓存（约 1 GB，已填充）
gradlew.bat <task> --console=plain --no-daemon
```

两点都必要，且都不是「随便试出来的」：

- **`GRADLE_USER_HOME` 必须指向本 worktree 的 `.gradle`。** 默认的 `~/.gradle/caches`
  **没有** `com.halilibo.compose-richtext`（`core:ui` 依赖）也没有
  `androidx.profileinstaller:1.4.0` 的描述符，因此 `:app:*` 的一切任务在那里都解析不了
  （`No cached version … available for offline mode` / 联网解析同样失败）。本 worktree 的
  `.gradle` 里两者齐全。
- **必须 `--no-daemon`。** 用常驻 daemon 时依赖解析失败，报
  `Remote host terminated the handshake` ＋
  `The server may not support the client's requested TLS protocol versions: (TLSv1.2, TLSv1.3)`，
  对 `dl.google.com` 与 `repo.maven.apache.org` **都是**如此。**同一个 JDK 的裸 JVM
  访问这两个地址是通的**（独立探针：`HttpClient` GET 两个 URL 均 `200`）。
  因此这不是网络或沙箱拦截，而是**那个 daemon 自己的问题**——具体成因本轮**没有查明**，
  只确证「换一个新建的 JVM 就好」。这一条按**未查明**记录，不要当成"网络被墙"的结论。

**顺带两条与 §12.3 有关的事实。**

- `subst S:` 是**机器全局**的，而 `android-env.ps1` 会校验 `S:` 指向自己那棵树。
  因此**两个 worktree 不能同时构建**：先映射的那棵树占住 `S:`，另一棵会直接抛
  「S: is already in use and does not point to this workspace」。并发会话之间"同时跑测试"
  在这台机器上结构性地不可能。
- 本轮**没有碰主树的 `.gradle` 缓存**（主树缓存 3 GB，属于并发会话的构建状态）。
  替代跑法只写本 worktree 的缓存目录；`gradle.properties` 里 `kotlin.incremental=false`，
  也不存在"混用两种根导致 Kotlin 增量缓存损坏"的风险。

**一个会静默让命令失败的 shell 事实（2026-09-12 批 1 第 1.4 项施工中实测）。**
本轮的 Bash 会话（Git Bash → Windows 进程）带着 `NoDefaultCurrentDirectoryInExePath=1`，
于是 `cmd /c gradlew.bat` 直接报 `'gradlew.bat' 不是内部或外部命令`——`cmd` 不再搜索当前目录，
即使子进程的 cwd 已经是 worktree。改用**绝对路径**（或在 PowerShell 里跑）即可，与构建无关。

这条值得单记，是因为它**曾经制造过一次假阴性**：第 1.4 项的变异脚本用
`cmd /c gradlew.bat` 起子进程，三次变异全部退出码 1 而结果 XML 里红测试为空，
脚本却把"结果目录不存在"的哨兵值当成"有红测试"而**报告成功**。
两条教训都写进纪律：
（1）**靠结果文件判定成败的脚本，必须把"文件不存在"当作失败**，不能当作空；
（2）退出码非 0 有两种来源（编译失败 vs 测试失败），**必须看输出**而不是只看码。

**读数纪律（2026-09-13 收尾取基线时新增三条，都是从这次实跑里学到的）。**

1. **只读 JUnit XML，不读控制台摘要。** 两个原因，本轮都实际踩到：
   ① 控制台的 `Starting 12 tests on test_device` 这类行**不带模块名**，要与模块配对只能靠
   "按声明的任务顺序推"——而那个推断**是错的**（本轮把 `:core:export` 的 12 条与
   `:core:visual-ui` 的 6 条**配反了**，两个数都真实存在，只是属于对方）；
   ② `Tests 45/40 completed` 这类计数**本身就不自洽**（跳过被计入分子，于是分子能超过分母），
   多套件并发时上一套件的进度行还会与下一套件的 `Starting` 交错打印。
2. **同一份结果会被写在多个嵌套目录下。** `connected`／`connected/debug`／
   `connected/debug/flavors` 三层都能 glob 到**同一批** `TEST-*.xml`；
   不去重就会把仪器化总数算成两三倍。按**文件真实路径**去重。
3. **`--rerun-tasks` 是"这一轮真的跑了"的唯一凭据。** Gradle 的 UP-TO-DATE 是**有效**证据
   （输入未变则结果仍成立），但它与"这一轮跑过"不是同一句话；收尾取基线时对 JVM 套件
   统一加了 `--rerun-tasks`，并**核对每个 XML 的写入时间都落在同一个窗口内**
   （本轮窗口 2026-09-13 03:16–03:38）——否则"冻结树"三个字里会混着几小时前的旧结果。
修好这两点后重跑，三条变异逐条命中（见 §11 批 1 第 1.4 项）。

**变异纪律（2026-09-13 批 4 施工中新增，来自一次真实的自我误判）。**

这一轮做 R-03 的追补用例时，我把**先推断、后实测**当成了实测：看到"改回字面量全绿"就记下
"探测点有盲区"，而那条变异是**等价变异**——它把共享常量换成**它自己的值**（`0.7`→`0.7`），
改完行为一模一样，全绿是必然的，什么也证明不了。真正的一侧漂移（`0.75/0.45`）实测红三条。
两条纪律因此写下来：

1. **变异先自问"它真的改变了行为吗"。** 等价变异（改完语义相同）不算证据，它只能造出
   "我的用例没抓住" 这种**假发现**，把人带去改本来没错的断言。
2. **预测的变红集合必须在看结果之前写下来。** 这一轮的每一次（备份恢复三条、R-03 两条）
   我都先写了预测，逐次相等才收；上面那次误判正是**没写预测、先看结果再解释**的产物。
   写下来才区分得开"用例没抓住"与"这条变异本来就不该被抓住"。
3. **变异要核对靶点，不能只确认"替换成功"。** 这一轮做"实拍屏有没有接到前置补救"的变异时，
   脚本按字符串找**第一个**同名参数删——而那个文件里有两处
   `prerequisiteRemediation = artifactLoad.prerequisiteRemediation`（策展屏一处、实拍屏一处），
   删掉的是策展屏那条。结果：新用例全绿，而它本该变红。**差一点被读成"用例抓不住"**，
   实际是变异没打在该打的地方。改对靶点后一次性复现。
   **纪律**：变异脚本必须打印**它改动的那一行的行号与上下文**，与预测一起写下来；
   只打印"applied"的脚本不算证据。

**否定性结论的检索纪律（同日，同一轮里差点造成一次假发现）。**

"某个方法在生产里**没有调用点**"这类结论，**不能只按接口名 grep 一次就下**。这一轮我按
`refreshLibrarySearchProjection`（端口上的名字）检索，得到"生产零调用点"，据此几乎要
写下一整条"库检索的索引从没被建过"的发现——而实际上四个读入口都在调它的**内部实现名**
`refreshProjection`（分页那条还是以函数引用 `::refreshProjection` 传给
`RefreshingPagingSource`），内部名不在我的检索式里。**真正的缺陷比"没接线"小得多也隐蔽得多**
（见 §12.5 N-18：`countIndexed()` 读错了表，引导分支永不触发）。

规矩：**否定性结论（"没有调用点／不存在／全库无此用法"）必须换一次检索式再核一遍**——
按实现方法名、按函数引用、或直接读实现文件；三者至少做一项，才允许写进报告。
这与 §11.3 的"零命中不等于不存在"是同一条纪律，只是在**本仓库内部**的版本。

**第三种"看起来像代码坏了"的状态：陈旧的 instrumentation 进程占住窗口焦点（2026-09-13 实测）。**

compose 用例整批报 `java.lang.IllegalStateException: No compose hierarchies found in the app.
Possible reasons include: (1) the Activity that calls setContent did not launch…`，
或干脆在**第一句等待**上超时（`Timed out waiting for text '1'`）。两者都与"活动没起来/代码坏了"
完全同形。真因是**上一个模块的 instrumented 测试进程还活着并占着前台窗口**——
`adb shell dumpsys window | grep mCurrentFocus` 直接给出证据，本次看到的是
`com.tingyun.smartmistakebook.feature.library.test/androidx.activity.ComponentActivity`
（还有一次是 `feature.capture.test`）。真正的应用活动因此拿不到前台，compose 找不到自己的层级。

**处置顺序**（本次四步都要，前两步单独做不够）：

```
adb shell am kill-all
adb shell pm list packages | sed 's/package://' | grep tingyun | xargs -n1 adb shell am force-stop
adb shell dumpsys window | grep mCurrentFocus      # 必须回到 launcher 才算干净
```

代价记录在案：这一轮在它上面**浪费了四次运行、两次误判**——一次以为自己的改动弄坏了
同类的其它用例，一次以为变异没被用例抓住（其实是变异打错了靶，见上）。所以这条纪律是：
**先看 `mCurrentFocus`，再解释任何 compose 层的红。**
`adb reboot` 与 `adb uninstall` 在这里**都不管用**（后者稳定报 `DELETE_FAILED_INTERNAL_ERROR`，
与 §13.4 上面那段记的同一种包管理器故障）。

**模拟器会进入两种"看起来像代码坏了"的状态（2026-09-12 批 1 第 1.7 项实测，两次都要靠
"同一份 APK 换个环境重跑"才能分清）。** 记在这里，因为这两种假阴性/假阳性都会让人
**改错地方**——以为自己的改动破坏了迁移测试。

1. **包管理器坏掉**：`core:database:connectedDebugAndroidTest` 在一次 149/149 全绿之后，
   下一次同一份 APK（任务 `dexBuilderDebugAndroidTest UP-TO-DATE`，**没有重编译**）在第 1 条
   测试上就报
   `Instrumentation run failed due to Process crashed` ＋
   `Device emulator-5580 failed to uninstall test APK … [DELETE_FAILED_INTERNAL_ERROR]`，
   结果 XML 里 `<failure></failure>` **是空的**（`time="0.085"`，测试其实没跑起来）。
   `adb uninstall` 两个包同样 `DELETE_FAILED_INTERNAL_ERROR`，而 `pm list packages` 连列都列不出来。
   **`adb reboot` 之后、源码一字未改，同一个类 2/2 通过。**
2. **判定这是环境问题而不是自己改坏的，靠的是一条结构事实**：
   `:core:database` 的 `build.gradle.kts` 只依赖 `:core:model`（`:31`），**不依赖 `:core:data`**。
   所以当时正在改的 `:core:data/VisualInteractionIngestor.kt` **根本进不了那个 APK 的输入**——
   先证"我的改动到不了这里"，再去找环境原因。（对照：`core/database/build/outputs/...` 的
   结果 XML 若 `<failure>` 有内容，就必须当逻辑问题查。）

**这两次假故障的完整时间线（2026-09-12 约 23:20–23:41，第一手）**，因为它的形状值得记住：
**每一次失败都停在不同的重量级用例上，而每次都不是我改的那块。**

| 时刻 | 现象 | 停在哪条用例 |
|---|---|---|
| ① | 149/149 全绿（同一份 APK） | — |
| ② | 同一 APK（`dexBuilderDebugAndroidTest UP-TO-DATE`，未重编译）第 1 条就 `Process crashed` | `AttemptResponseMigrationInstrumentedTest.versionFourteenAttemptRemainsV2…` |
| ③ | `adb reboot` 后、源码一字未改，**单类 2/2 通过** | — |
| ④ | 再跑全集，两个套件同时崩 | `KnowledgeGroundingMigrationInstrumentedTest.versionEighteen…`（core:database，第 17 条）／`BatchImportRepositoryInstrumentedTest.splitRecognitionDegrades…`（core:data，第 23 条） |
| ⑤ | 清干净包记录后单跑 core:database，跑到第 11 条再崩 | `FullMigrationMatrixInstrumentedTest.everyExportedSchemaVersionMigrates…` |
| ⑥ | 下一次直接装不上：`Failed to install-write all apks` ＋ `- waiting for device -` | —（没跑到用例） |

**⑥ 给出了真因。** `adb devices` 那一步的主机名从 `emulator-5580` 变成了 **`emulator-5554`**，
`uptime` 只有 1 分钟——**这台模拟器在 23:38:51 被外部以 `-wipe-data` 重启了**
（进程命令行变成 `emulator.exe -avd test_device -wipe-data -no-snapshot-save -no-boot-anim -gpu swiftshader_indirect`，
与原先那台 `-port 5580 -no-window -cores 1 -no-snapshot` 不是同一个进程）。②④⑤ 的"每次停在不同用例"
因此是**设备在被拆掉、重建**期间跑测试的必然结果，不是任何一条代码路径的问题。

**但真正的根因是"两个会话在抢同一台模拟器"，wipe 只是它的症状（2026-09-12 23:47 查实）。**
重启后的干净设备上，全集仍然在第 1 条崩。这次抓到了完整的因果链（logcat 原文）：

```
15:44:15.9  abb: StartCommandInProcess(package.install-…)
15:44:16.021 ActivityManager: Force stopping com.tingyun.smartmistakebook.core.database.test user=-1: installPackageLI
15:44:16.022 ActivityManager: Killing 5682:…core.database.test (adj 0): … due to installPackageLI
15:44:16.023 ActivityManager: Crash of app … running instrumentation AndroidJUnitRunner
15:44:16.024 PackageManager: Update package …core.database.test code path from … to …
```

也就是说：**我的仪器化进程是被"另一次 `adb install` 同一个测试 APK"杀掉的**，不是自己崩的。
而"另一次"来自哪里，有一条直接证据——同一时刻**主树**的结果目录在动：

```
D:\smart mistake book\core\database\build\outputs\androidTest-results\connected\debug\
  test_device(AVD) - 14    2026/9/12 23:47:09   ← 我这边跑到一半时，主树正在写结果
```

**结论：并发会话也在这台模拟器上跑 `:core:database:connectedAndroidTest`。**
一台设备上两个 AGP 运行会互相在对方仪器化进行中重装同一个测试 APK，
于是双方都在**随机的用例**上看到 `Process crashed`——谁先装谁就把对方杀掉。
这与 §13.4 上面那条 `subst S:` 是同一类结构限制（那一条挡的是**构建**，这一条挡的是**设备**）：
**两个 worktree 不能同时跑仪器化测试**，而且失败的样子极具误导性——它看起来像被测代码坏了。

**反证（同一天 23:55，第一手）**：对方那次跑完时**一次绿到底**——
主树 `core/database/build/outputs/androidTest-results/connected/debug/` 里
`test-result-exit-code.txt` = `0`，XML 头为 `tests="161" failures="0" errors="0"`。
**同一台设备、同一段时间，单独跑就全绿**——这把「设备本身坏了」和「并发互杀」彻底分开了：
坏的是**并发**，不是设备，也不是任何一方的代码。

**因此本轮对仪器化证据的处置**（诚实标注，不含糊）：

- 我**主动停掉了自己那次运行**（23:47），因为它同时在破坏对方的运行。
- 本记录的仪器化绿灯只采信**在无争用窗口内**跑出来的那两次：
  `:core:database` 149/149、`:core:data` 99/99（同日 22:43／23:19 前后，当时主树没有并发运行）。
- 争用期间的所有 `Process crashed` **一律不作数**——既不当作我的缺陷，也不当作"通过"。
- **1.7 的仪器化复验状态：UNVERIFIED**（争用所致，非代码原因）。可接受的替代证据是
  ①`:core:database` 在 1.7 的接口改动（端口记录新增 `isPseudoFallback`）之后仍是 149/149 全绿
  （22:43 那次就带着该改动），且 1.7b 只改了 `:core:data`、`:core:database` 的输入未变；
  ②1.7 改动的代码路径**没有任何仪器化测试覆盖**（`core/data` 与 `app` 的 androidTest 里
  `VisualInteraction` 零命中），所以仪器化套件对它只是回归网，不是覆盖率网。

**两条可复用的处置办法（都实测过）**：


- **包记录损坏**：`pm list packages` **列不出**该包，而 `adb uninstall <pkg>` 报
  `[DELETE_FAILED_INTERNAL_ERROR]`。`adb shell pm uninstall --user 0 <pkg>` **返回 `Success`**，
  之后重新安装即可正常跑（实测：清掉后 core:database 能跑到第 11 条，而不是第 1 条就崩）。
- **别在设备刚启动时跑仪器化测试。** 被 wipe 后的首启要重扫包并 dexopt：实测 1 分钟 load average
  从 8.5 降到 2.5 花了约 **2 分 15 秒**，在那之前跑必然超时或崩。等到 `cat /proc/loadavg` 的
  1 分钟均值低于 ~2.5 再开跑。

**`--tests` 过滤器的行为在一轮里出现过两种（2026-09-12 批 1 N-06 施工中实测）。**

同一个 `:core:data:testDebugUnitTest`：

| 命令形态 | 观察到的结果 |
|---|---|
| `:core:data:testDebugUnitTest :app:testLocalFirstDebugUnitTest --tests "*StartupFailureMessages*" --tests "*StudyProjectionDrainer*" --tests "*OptionalSessionCard*"` | core:data 的**全量** 51 个测试类全部重跑（51 个结果 XML 同一分钟写入）；app 侧**确实**只跑了命中的 2 个类 |
| `:core:data:testDebugUnitTest --tests "*StudyProjectionDrainerEquivalenceTest"` | **只跑 1 个类、7 条**（过滤器生效） |

**成因未查明**，因此不作因果结论。**处置是纪律，不是解释**：本记录的所有回归数字**一律按全量任务取数**
（不加 `--tests`），需要"只跑一个类"时用**单任务单模式**的命令形态；靠过滤器省时间的跑法不用于取证。

**变异脚本的两条纪律（N-06 施工中定下来，取代此前"手抄中文字符串"的做法）。**

- **锚点从活源码里切出来，不手抄。** 旧脚本把要替换的中文代码块逐字写进 Python 字面量
  （还要写成 `\x` 转义），一旦源码有一个字的改动，锚点就会失配——而**失配的表现是替换 0 次**，
  脚本若不检查就会"跑完全程、报告全部命中"。新脚本用起止标记从当前文件里 `index` 出锚点，
  并断言 `count(old) == 1`，失配直接 `SystemExit`。这与第 1.4 项那条教训是同一族：
  **判定成败的脚本必须把"没找到"当失败，不能当空。**
- **每轮结束把源文件写回，并在 `finally` 里断言逐字节相同。** 变异会改产线源码，
  恢复不彻底会让后续验证跑在被污染的文件上（假绿）或直接改坏工作树。

**控制台是 GBK：带 `∂`／`≤`／`−` 的断言消息会让变异脚本在打印那一步崩掉（2026-09-12 批 3 F-01 实测）。**
`print(f"RED {name}: {message}")` 抛 `UnicodeEncodeError: 'gbk' codec can't encode character '∂'`，
而它发生在**断言检查之前**——于是脚本的退出码仍是 0（`| tail` 之后更看不出来），
一次崩溃与"跑完了"在终端里长得一样。处置：脚本里
`sys.stdout.reconfigure(encoding="utf-8", errors="replace")` ＋ 运行时 `PYTHONIOENCODING=utf-8`。
**这是"判定成败的脚本必须把'没走到检查'当失败"的第三个变种**（前两个：结果目录不存在、退出码非 0 撞上编译失败）。

**同一个 GBK 事实还有**读取**那一侧，而且更隐蔽（2026-09-13 批 2 R-10 变异实测）。**
`subprocess.run(..., capture_output=True, text=True)` **不带 `encoding`** 时按本地编码（GBK）解码
子进程输出，而 Gradle 的输出里有 UTF-8 字节 → **在读取线程里抛 `UnicodeDecodeError`**
（`threading.py` → `subprocess.py` 的 `_readerthread`）。它的可怕之处在于：
**主线程不受影响**——退出码照拿、结果 XML 照读、判定照做，只有 `proc.stdout` 是被截断的。
本次它的唯一可见症状是终端里飘出两段 Python traceback，而脚本仍然正确地报了"变异被抓住"。
**如果那次判定依赖 `proc.stdout`（比如"编译失败"与"测试失败"靠输出区分），结论就会是错的。**
处置：四处变异脚本统一加 `encoding="utf-8", errors="replace"`。
**与第三条合起来看，纪律是：脚本对自己读进来的每一个字节也要声明编码，不能只声明自己写出去的。**

**"进程崩了"与"测试红了"必须分开判：判据在 `test-results.log`，不在 Gradle 输出里
（2026-09-12 批 1 收尾实测）。** 冻结树的 `:core:data:connectedDebugAndroidTest` 在 54/99 处
被 Gradle 报成

```
com.tingyun...BundledKnowledgeBaseInstallerInstrumentedTest > installsEverySupportedSubjectWithGroundedAtomsAndIsIdempotent FAILED
Tests on test_device(AVD) - 14 failed: There was 1 failure(s).
Test run failed to complete. Instrumentation run failed due to Process crashed.
```

看起来像一条红测试（Gradle 把它算进 `failure(s)`，HTML 报告也给它一页）。但同一目录下
`testlog/test-results.log` 的记录是**决定性的**：

```
INSTRUMENTATION_STATUS: current=54
INSTRUMENTATION_STATUS: test=installsEverySupportedSubjectWithGroundedAtomsAndIsIdempotent
INSTRUMENTATION_STATUS_CODE: 1          ← 1 = 测试开始
INSTRUMENTATION_RESULT: shortMsg=Process crashed.
INSTRUMENTATION_CODE: 0
```

`STATUS_CODE: 1`（开始）之后**没有** `-2`（失败）——测试**根本没跑完**，是进程在它执行期间死了，
runner 于是把在飞的那条记成 FAILED。三条配套旁证：类报告里 `failed (13.117s)` **不带任何失败消息**、
`<failure>` 为空、以及那一次运行整体**异常地快**（4 分 34 秒跑完 `:core:database` 149 条后就崩，
此前完整跑两套要 9 分 28 秒）。

**判定办法（30 秒，比重跑整轮便宜）**：单独重跑那个类。

```
gradlew.bat :core:data:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.tingyun.smartmistakebook.core.data.knowledge.BundledKnowledgeBaseInstallerInstrumentedTest
```

实测 **1/1 通过**（`BUILD SUCCESSFUL in 1m 24s`），而这一次源码一字未改——所以它是环境。
**结论按"环境"记，不当作缺陷，也不当作通过**：崩溃那一次的 54/99 记 `UNVERIFIED`，
干净的全量重跑另行记录。

**收尾的整轮运行（2026-09-13 03:16–03:38，同日无争用窗口）——争用那一节的三条待办在这里结清。**

| 项 | 结果 |
|---|---|
| 设备 | `emulator-5554`（`adb devices` 报 `device`），全程无第二个会话写入结果目录 |
| 仪器化 9 个套件 | **443 条，0 失败，6 跳过**，逐套件见 §13.1「冻结树最终基线」 |
| 其中 `:core:data`（"崩在 54/99" 的那一个） | **100 / 0**——干净重跑**一次跑完**，没有复现 54/99，也没有用"单独重跑一个类"绕过去 |
| 其中 `:core:database`（R-10 的整轮复跑） | **150 / 0** |
| JVM 16 个套件 | **1566 条，0 失败**（统一 `--rerun-tasks` 强制真跑） |
| 结果文件写入时间 | 全部落在 03:16–03:38 一个窗口内（用来证明"没有混进旧结果"） |

**这一轮同时结清了几条先前的 UNVERIFIED**，逐条说明（不合并成一句"都绿了"）：

- **1.7 的仪器化复验状态**：先前记 `UNVERIFIED`（争用所致）。现在两个 app 变体的仪器化套件
  各 **40 / 0** 跑完。但**覆盖面的结论不变**——`core/data` 与 `app` 的 androidTest 里
  `VisualInteraction` 仍然零命中，所以这两套对 1.7 的改动只是**回归网，不是覆盖率网**。
  两句话必须同时说：**"跑绿了"和"这条路径被覆盖了"是两件事。**
- **`loadProjectionBatch` 读到修正行返回 `FULL_REPLAY_REQUIRED`**：先前记 `UNVERIFIED`
  （"只能在 `:core:database` 仪器化里验证，本机不跑"）。**已经验证**——那条用例一直存在
  （`StudyDatabaseInstrumentedTest.kt:333-344`），本轮 150/0 里包含它。见 §11 批 0 第 2 项的更新。
- **启动横幅"从状态到界面"**：先前记 `UNVERIFIED`，理由写的是"本机不跑 app 的仪器化"。
  **理由被订正、结论不变**：套件现在会跑，但**那里没有这条用例**。缺口是"没人写"，
  不会因为环境变好而消失。见 §11 批 0 第 5 项那条更新。

- **仪器化测试的方法名不能带空格**（2026-09-13，写 N-28 的用例时撞到）：`fun \`a capability test …\`()` 内的
  lambda 会生成以方法名命名的匿名类（`…Test$a capability test …$1`），而 D8 直接拒绝——
  `Space characters in SimpleName … are not allowed prior to DEX version 040`。本工程 `minSdk 23`，
  于是**整包 dex 失败**（不是警告、不是那一条用例失败，是整个 `:core:data:connectedDebugAndroidTest` 构建失败）。
  JVM 侧没有这个限制，所以 `src/test` 里满眼反引号方法名、`src/androidTest` 里一个都没有——
  **这不是风格差异，是平台约束**；写仪器化用例时照 `src/androidTest` 既有命名走（camelCase）。

- **机械拆文件的三条规则**（2026-09-13 拆 R-01 时两次踩到，后两条是第一条的深一层）：（i）**注解必须与它的声明一起搬**——按行切块若把 `data class` 行当块起点，`@Entity(...)` 会留下、声明被搬走（KSP 报错）；（ii）**块边界不能落在「注解串」与它的声明之间**——块起点若写成「任何 `@` 行」，会把 `@Serializable` 与紧跟的声明切开，报 `This annotation is not repeatable`（`@Entity` 那次恰好没暴露它）；正确规则是「块只在 KDoc 处、或在上一个块**已含声明**时的注解处切开」；（iii）搬走的声明若用了**同文件的 private 顶层辅助函数**，拆完立刻编译不过——文件私有是一种**不出现在 import 里的耦合**，按名字切的方案看不见它。

- **机械拆文件时，注解必须与它的声明一起搬**（2026-09-13，拆 R-01 的实体文件时撞到）：按行切块时若把
  `data class` 行当作块起点，`@Entity(...)`／`@ColumnInfo` 这类注解会留在原文件、声明被搬走——
  KSP 报 `Entity declaration must be annotated with @Entity`。规则要写成"块只在 KDoc 或注解处切开"。
  **只在带注解的声明上显形**：同一套脚本拆纯记录聚合（无注解）时全绿，所以别拿一类文件的结果
  去推断另一类。

**仍然未验证的（这一轮没能结清，明确列出）**：`:core:data:lintDebug` 与两个 `lint*Debug`
未跑；`:benchmark` 的 macrobenchmark 套件未跑（需独立变体，CI 也只 `assemble` 不跑它）；
主树与 worktree 的 **CI 本身**本轮未触发（本机不触发，所以"CI 绿"这句话本轮没有证据，
上面所有绿灯都是**本机**跑出来的）。

---

### 13.5 四路并行审查的处置（2026-09-13）

**这是什么**：批 4 施工期间，四个子代理并行审了这一分支的改动（`review-domain` /
`review-data` / `review-app` / `review-tests`）。这一节逐条记录**我的一手核实结论**与**处置**。
"核实"一栏是我回读源码后的判定，**不是转述**——四份报告里有两份在传回时被截断，
截断的那部分一律按"审查者报告，未经独立核实"标注，并要求补发。

| # | 来源 | 发现（压缩） | 我的一手核实 | 处置 |
|---|---|---|---|---|
| R1 | `review-app` #1（`review-tests` #1 独立同指） | N-12 那套"读取失败"兜底**不可达** | **属实**：`teachingArtifact` 生产里只有一种实现、委托给纯查表的夹具源、工厂无装饰层 | **已删除**（`d1ef314c`）；N-12 改判 **"不可达 → 撤回"**，不再记"已修" |
| R2 | `review-tests` #2 | 实拍屏的前置补救**接线**从未被断言 | **属实**：既有用例直接调 `CapturedReviewSessionScreen` | **已补**（`1d5f44d3`）：走真实根导航的用例 ＋ 变异确认 |
| R3 | `review-data` #1 | 带图 kind 是手写清单，与契约注册表可能漂移 | **属实**（漂移可发生，且今天没有断言能抓） | **部分采纳**（`f0e49f99`）：加"等价性"断言；**派生方案被否**（fail-open）；另一半 → **N-23** |
| R4 | `review-data` #2 | 补救通道**每张卡**整读一遍错题目录 | **属实**（读代码：`observeMistakes().first()`） | **未修 → N-19**（修法要与 N-20 一起做） |
| R5 | `review-data` #3 | 「哪些 KC」四个答案、三种优先级 | 报告被截断，**未独立核实** | **登记 N-20**，已要求补发完整清单 |
| R6 | `review-app` #2 | 重试按钮不重跑失败步骤；失败横幅永不消解 | **属实**（回读四个文件，见 N-21） | **登记 N-21** |
| R7 | `review-tests` #3 | S-10 正向方向（`adopted == true`）无测试 | 报告被截断，**未独立核实** | **登记 N-22**，已要求补发完整清单 |

**R3 为什么只采纳一半**（这是一次**技术性反驳**，不是折中）：审查者的修法是把五条
`add(...)` 换成 `all().filter { it.assetPolicy != FORBIDDEN }`。那条推导把
"这个 kind 带附件"当成"这个网关服务它"，两者不等价：今天恰好一致，但将来若注册一个
本网关服务不了的带附件 kind，推导会**静默宣告**它（请求真发到提供商才失败，还先烧一次派发
——fail-open），而手写清单是不宣告（请求被 `capabilityFailure` 挡住，零出网——fail-closed）。
所以生产侧保持显式，改用断言把两者钉成同一件事；`PROBLEM_CLASSIFY` 的 `assetPolicy`
由 FORBIDDEN 改成 REQUIRED 的变异**只有新用例变红**，证明它确实补上了原来没人守的那一格。

**这一轮新学到的两条纪律**（都已写进 §13.4）：变异的**靶点**要核对（同名参数有两处，
我第一次打错地方，用例全绿）；以及"先看 `mCurrentFocus`，再解释任何 compose 层的红"
（陈旧的 `*.test` 进程占住前台窗口会让整批用例报 `No compose hierarchies found`）。

**三份被截断的报告补发之后的完整清单**（`review-domain` 7 条、`review-data` 5 条、
`review-tests` 6 条）。**级别与位置沿用审查者原话**；"核实"一栏写的是我做到哪一步：

| 来源 | 位置（审查者给） | 级别 | 缺陷（压缩） | 处置 |
|---|---|---|---|---|
| data | `VisualInteractionIngestor.kt:216-218` | major | 未投影单元的**第二条**视觉证据 `delta_t` 仍为 0 | **已修 ＋ 用例 ＋ 变异**（`1ec94110`）→ **N-24** |
| domain | `FsrsScheduleMath.kt:64,71,89` | major | F-01 只改症状：`decay` 仍是三个入口的默认值、`[20]` 手写六处 | **已修 ＋ 值钉 ＋ 变异**（`214fdd36`／`675bf81e`）→ **N-25** |
| domain | `SchedulingEvaluation.kt:46-48` | minor | `elapsedDaysSince` 回退分支是**第四种**日口径 | **并入 N-26** |
| domain | `StudentModelContracts.kt:64-77` | minor | 新增必填 `timeZoneId` 落在一个"全仓无处构造、唯一实现的曲线忽略时区"的契约上，且非法 id 在使用点才抛 | **并入 R-07**（2026-09-13）：该契约在生产里**一个构造点都没有**（`grep EvidenceProjectionInput` 只有声明、接口与 `HLRPredictionAuditService` 的实现），所以它的类型边界该跟"接线或删除"一起定——单独把它改成 `ZoneId` 是给一份没人用的契约打磨 |
| domain | `LearningProjector.kt:1204-1205`（及 `:862-866`） | minor | `localEpochDayOf` 是一行直通包装；写入侧因缺 offset 重载而内联重写了减法与 clamp | **并入 N-26** |
| domain | `SchedulingEvaluation.kt:586` | minor | 新注释声称 `ADOPTION_MARGIN` 与 `fit` 内部阈值同源，而 `fit` 写的是字面量 `1e-9` | **待办（minor）**——**这条是"注释说谎"，改动其一即悄悄分叉** |
| domain | `SchedulingEvaluation.kt:386-445,499-525` | minor | `Result.adopted` 一次性布尔承载不了不变量、默认值还是宽松的 `true`，两个出口各手抄一遍 | **✅ 2026-09-13**（`d3e6aaa1`）：`sealed interface Decision` ＝ `Adopted(parameters)` / `Rejected(incumbent)`，`Result.parameters` 与 `Result.adopted` 都改为**推导**——"什么都没换"是构造出来的；默认值 `true` 也一并消失（新出口必须指明结论）。变异（闸门谎报 `Adopted`）**恰好 2 条红**（domain 的 refit 用例 ＋ data 的写回闸门），即类型化没有把行为断言弄丢 |
| domain | `ReviewPlanner.kt:236,252,313`（`ReviewPlannerV2.kt:471,486,592` 同形） | minor | 同一个 `(memory, now, zoneId)` 在一张卡上算三遍 | **不修，理由记录**（2026-09-13 核实后否掉）：三处确实同在 `scoreCandidate`，但 236 与 252 在**互斥的 `when` 分支**里、313 在一个 `\|\|` 短路表达式里——一张卡最多算两次，而"`when` 之前算一次"会让不需要它的分支也算，并在两个分支里引入一次 null 处理。单次是 `pow` 级的纯计算，代价不成立；V1/V2 同形，同样处理 |
| domain | `LearningProjector.kt` 1230 行 / `ReviewPlannerV2.kt` 920 行 | — | 越过 1000 行信号线 | **审查者自己建议本次不拆**，我同意：投影器受重放等价性约束、是单一确定性单元，且刚被强化后的用例钉住 |
| data | `StudyWriteContext.kt:38`、`StudyReviewPlannerService.kt:460` | major | `ReviewCalendar` 号称"日界的唯一定义"，但 `studyDay.epochDay` 与规划用的 `LocalDate` 各有一份内联副本 | **并入 N-26** |
| data | `RoomBackedStudyExperienceRepository.kt:129-157` | minor | `ForgettingCurve(...)` 仍写了两遍（字段一份、planner 内联一份） | **待办（minor）**：`ForgettingCurve` 无状态，直接传字段 |
| data | 同上 `:383-384` 与 `:1010-1011` | minor | `currentLearnerSnapshot().problemMemoryStates.mapValues{…}` 写了两遍，且每次为一个小查询物化全部记忆态 | **待办（minor）** |
| data | `BundledKnowledgeBaseInstaller.kt:78`、对应仪器化用例 `:184` | minor | 批大小生产是局部 `val`、夹具另写 `HALF_INSTALL_BATCH = 2_000`，无耦合 | **登记 N-29** |
| data | `ChatEvidenceLedgerIntegrationTest.kt:52-267` | minor | 六个用例各重复约 30 行临时库脚手架，漏一处清理不会被发现 | **✅ 2026-09-13**（`6507749f`）：抽成 `withTemporaryDatabase { }`，清理只剩一处；**事实佐证**——抽之前六个里有一个（`chatEvidenceEmptyLearnerReturnsEmptyLedger`）只 `close()` 不 `deleteDatabase`，无人发现。设备实跑 **6 / 0** |
| tests | `SchedulingEvaluationTest.kt:139-161` | major | 只断言被拒方向，没有断言夹具真的产生过一次采纳 | **已修 ＋ 变异**（`eb8c48e9`）→ **N-27** |
| tests | `StudySchedulingCalibration.kt:93-96` | major | 整个类无测试，`if (result.adopted)` 写回闸门与正向采纳无人覆盖 | **已修 ＋ 变异**（`5be9a9f5`）→ **N-27** |
| tests | `RoomBackedStudyExperienceRepository.kt:128-160,254` | major | F-01 装配点无覆盖，测试替身不暴露 `optimizedFsrsParameters` | **并入 N-27（第 ③ 条仍未做，路标已记）** |
| tests | `AttachedImageGeneratorFactory.kt:25-36` | minor | S-2 第二通道（配图）无测试证明它走共享闸门 | **登记 N-28** |
| tests | `ReviewCalendarTest.kt:48-52` | minor | `assertNotEquals(0.0, …)` 被上一行蕴含，是装饰性断言 | **待办（minor）**：删掉那一行 |
| tests | `ReviewCalendarTest.kt:103-112` | minor | 只断言两个口径在固定偏移时区一致，KDoc 声明的唯一分歧点（夏令时切换那一小时）无覆盖 | **待办（minor）**：补 `America/New_York` 跨切换用例 |
| tests | `StudyProjectionDrainerEquivalenceTest.kt:126-129` | minor | KDoc 声称 DAO 那一半"本机不跑、UNVERIFIED"，而已有仪器化用例断言了同一条 | **待办（minor）**：改成交叉引用，保留"替身镜像 DAO"的限定语 |

> **这张表里"待办（minor）"的十条本轮有意不做**：它们各自都是小改动，但混在算法/接线类改动里
> 一起提交，正是本审计反复点名的"改了字面、没改内里"的镜像（改到哪一步无法单独验收）。
> 它们已逐条记在案，随时可以按条取用。**N-25／N-26／N-27／N-28／N-29 是其中需要设计的五条。**

---

*本记录由 14 个子智能体（7 维审计 ＋ 7 维对抗复核，其中 4 个因 API 流错误中断）与主循环的一手补验共同产出。
所有行号对应当前工作树的**已提交状态**；工作树改动影响的个别行号已在条目内注明。
完整原始数据见 `audit-extract.json`、`wvuzb1nlz.output`、`journal.jsonl`。*

---

## 14. 剩余项的完成顺序（2026-09-13 定）

排序依据只有三条：**能指出它消灭的具体失败** ＞ **验证成本低** ＞ **不牵动持久状态**。
凡会改到重放结果或产品可见口径的，一律往后排并单独说明；凡只差用户拍板的，列在最后。

| 序 | 项 | 为什么在这个位置 | 验收（做到什么算完） |
|---|---|---|---|
| A1 | **N-21** 重试按钮不重跑、失败横幅永不消解（`P1`） | 唯一一条**用户直接看得见**的失效，且已一手核实 | 点重试必须重跑失败的那一步；恢复后横幅有路回 Ready；用例钉住两条 |
| A2 | **§13.5 的五个小项**（装饰性断言、注释说谎、三处重复计算、时区契约、`mapValues` 重复） | 每条都是"改动其一即悄悄分叉"的种子，成本极低 | 逐条改 ＋ 注释与代码同源；不新增抽象 |
| ~~A3~~ | ✅ **已随 N-16 的修复解决**（`7e611d2b`：C 那条通道的范围改读题库之后，取材料那半自然只剩「只认库内绑定」这一条，不再需要新缝） | ~~**N-20 的取材料那一半**（C 与 D 收敛：只认库内绑定）~~ | ~~S-5 已定"范围取自题库自身"，属**收尾**不是新取舍~~ **← 这一格的前提经实测已否，见 N-20 的「施工前核实」块**：`reTeachOpening` 的**科目**也取自策展件，而材料检索两个入参都要 ⇒ 想只认库内绑定得**再加一个缝**（按 practice_unit 取"科目 ＋ 范围"）；且它落在 **N-16 的产品决定**里（那条通道没有任何一屏渲染过）⇒ **先加缝后决定是否渲染＝白做一遍**。**实际状态：等 N-16 拍板，不做前置的半截。** | （随 N-16 一起）C 不再读策展件；A/B 的差别仍只登记 |
| A4 | **`adopted` → sealed `Adopted`/`Rejected`** | 用类型承载不变量，替掉"一次性布尔 ＋ 宽松默认 `true`" | 两个出口不再手抄；既有 S-10 用例不变绿为条件 |
| B1 | **R-06 死代码清理** | 减少面；每删一处用 grep ＋ 编译举证 | 清单逐项有"无人引用"证据或保留理由 |
| B2 | **§13.5 第六条**（六个用例重复 30 行临时库脚手架） | 测试质量；抽 helper 后漏清理不再静默 | `withTemporaryDatabase { }` 一处定义 |
| B3 | **N-26 读侧两处**（`elapsedDaysSince` 回退、规划用的 `LocalDate`） | **写侧那两处不动**：它们改的是持久 `delta_t` ⇒ 要升 `LearningProjector.VERSION` ＋ 重放，属另一件事 | 读侧不再有第二份日界；写侧单独登记 |
| B4 | **R-09 `attempt_event` 三列** | 删列＝迁移，要连带矩阵用例 | 或接线或删列，不留 placeholder 列 |
| B5 | **R-02 `PagingSource` 移出 `core:domain`** | ✅ **2026-09-13 完成**（`R-02` 达成：该模块零命中 Paging）。做法取「①的变体」——域接口只留页形状的读，分页源由装配点交进 feature：数据层新增 `LibraryPagingSourceProvider`（`RoomLibraryCatalogRepository` 实现它，**保留 Room 自己的 PagingSource**，因为它带表失效与计数语义，手写 offset/limit 版本会丢掉这两样）；feature 改收函数类型参数 `catalogPagingSource`（**feature 不必依赖 core:data**）；app 装配点传`application.libraryPagingSources::pagingSource`（为此加了编译期 paging 依赖——它本就随 core:data 进包）。零行为变化：`:core:domain` 415/0、`:core:data` 418/0、`:feature:library` 33/0、`:app` 77/0。侦察中发现的原方案缺陷（在 feature 里现造 PagingSource 会丢 Room 失效）已避开。 | `core:domain` 不再依赖 Paging，且 feature 的 Pager 仍能拿到分页源 |
| B6 | **R-01 拆超线文件** | ✅ **第一个已完成**（2026-09-13）：`StudyDatabaseRecords.kt` 1396 → **844 行**，按 port 子接口拆出 `SplitImportRecords`／`KnowledgeRecords`／`StudyStateRecords`，**同包 ⇒ 零 import 改动**，是清单里唯一不牵动调用点的形态（`:core:database` 67/0、`:core:data` 418/0 为证）。该文件原有的 KDoc「一个文件好让漏接线是编译错误」已改写成保留原意（那是**类型的性质**，与文件个数无关）。**其余超线生产文件按类排队**：① 纯声明聚合：`LearningEntities.kt` **✅ 已拆**（1132 → **821 行**，`4af043cf`，`assessment_*` 家族 6 实体进同包 `AssessmentEntities.kt`，schema 导出未动）· `DatabaseContract.kt`（1027）**不是同一类**——一手看过后它是**单个 object**（`DatabaseContractValidator`），拆它＝把一个校验器拆成几个并改调用点，属②类而不是①类（我此前把它归进①是错的，已订正）；① 附：`TutorVisualDocument.kt`（1076）**✅ 已拆**（`aecb8466`，1076 → **727 行**，`TutorVisualSceneTypes.kt` 319 行）。两次试错的路标留在 §13.4 的三条规则里：缝要取**文档种类**（2D/3D 家族只有 5 个声明，切不动）；拆之前必须先把 7 个同文件 `private` 顶层辅助函数放宽为 `internal`——**文件私有是不出现在 import 里的耦合**，按名字切的方案看不见它，拆完才会以编译错误的形式暴露。回归 `:core:model` 299/0、`:core:data` 418/0、`:app` 77/0。 **② 带调用点的聚合：`DatabaseContractValidator` 试拆失败，两个拦路石已定位**（`DatabaseContract.kt` 1027／单个 object／50 成员／48 处调用）。试把投影一族（`validateProjectionRequest`／`validateLedgerRequest`／`validateProjectionCommit`／`verifyCanonicalFingerprint`）搬成同包的 `ProjectionContractValidator` 并改 3 个文件的调用点后，编译报两类错：**（a）被搬走的成员依赖原 object 自己的成员**——两个辅助函数（`id`／`requireContract`）与一个常量（`MAX_PROJECTION_BATCH_SIZE`）：这是「文件私有耦合」的 **object 版且更难**，共用件被多个族引用，拆哪一族都会引出它们，正解是先把共用件提到第三处（或让新 object 用限定名调用）；**（b）按行提取不保证花括号配平**（成员体里有嵌套 `{}`），原文件留成语法错误——正解是按**配平的花括号**切块。已整体 `git checkout` 还原，无残留。**③ DAO 聚合**（`ProblemDraftTransactionDao` 1213、`ProjectionTransactionDao` 1194、`RoomStudyDatabase` 1309）**已按实测判为「不拆」，理由见 §13.7**——不是没做，是量完之后`拆`这个动作在这里会**破坏原子性**。**B6 到此收口**：①②③ 全部处置完，其中①拆了两个、②③各自给出实测结论并说明为何不动。 |
| B7 | **R-07 四个校准接口：接线或删除** | 接线是大功能、删除是产品可见；先取可辩护的一半 | 每个接口二选一落地，不留"我们有标定"的空话 |
| C | **N-17 / N-23 / N-30 / N-31（余三条）/ R-12 硬门** | 这几条要的是**产品口径**，不是工程判断：重定档延迟预算＝发明指标；注册表强制读＝取舍；保持率建议读哪组参数＝呈现口径；`PASS` 覆盖什么＝报告语义（**作用域那一半 2026-09-13 已按建议做掉**，见 N-31；余下"恒空的行由谁产／FAIL 是否提交／要不要引第三个词"仍等拍板）；存量文件零增长会拦住正当改动 | 只登记 ＋ 给建议，等拍板 |
| — | **进度（2026-09-13，十三）** | **试拆第二类失败并定位两个拦路石**：object 内共用件（`id`／`requireContract`／`MAX_PROJECTION_BATCH_SIZE`）与「按行提取不保花括号配平」；已整体还原、无残留。**顺带修好 B6 行被早先一次替换吃掉的收尾竖线**。下一步＝先提共用件、再按配平花括号切块。 | — |
| — | **进度（2026-09-13，十二）** | **B7 的「证据」那一半 ✅**（五个出口的零读者状态写在声明处；其中 `fsrsBeatsBaseline` 是「不会响的门」）；**「接线还是下线」待你拍板**。B6 余下的四个文件不再同形（要动调用点／要仪器化）。 | — |
| — | **进度（2026-09-13，十一）** | **B6 三件 ✅**（`StudyDatabaseRecords` 1396→844、`LearningEntities` 1132→821、`TutorVisualDocument` 1076→727）：三处都是同包拆分 ⇒ 零 import 改动、行为零变化。**余下的都不再是同形**：`DatabaseContract`／`RoomStudyDatabase`／两个 DAO 是"要动调用点"，三个界面聚合各要仪器化。**B7 仍待做**（四个校准接口）。 | — |
| — | **进度（2026-09-13，十）** | B6 已完成两件（记录聚合、实体家族）；**试拆第三件时发现我自己的分类错了**：`DatabaseContract.kt` 是单个 object（②类），`TutorVisualDocument.kt` 按我假定的家族切只切出 5 个声明（真正的缝在文档种类上）——两处都已订正进 B6 行，脚本在断言处停下、未写文件。 | — |
| — | **进度（2026-09-13，九）** | **B6 第二件 ✅**（`LearningEntities.kt` 1132→821；**踩到并修掉一个真坑**：机械切块时"注解必须随它的类一起搬"，第一版规则把 `@Entity` 留在原文件、KSP 立刻报错——只在**带注解的实体**上显形，记录聚合那类看不见）。§13.4 同类教训 +1。 | — |
| — | **进度（2026-09-13，八）** | **B6 第一个文件 ✅**（1396→844 行，同包三分，两处套件绿）· 其余超线文件已按三类排队（见该行）· **B7 待做**（四个校准接口：接线或删除，属产品可见取舍）。 | — |
| — | **进度（2026-09-13，七）** | **B5 ✅**（R-02 达成，四处套件全绿）· 下一步 **B6（拆 11 个超线文件）**，其后 B7。 | — |
| — | **进度（2026-09-13，六）** | **B5 侦察完成、待定方案**：暴露面只有一处，但消费方是 feature 的 `Pager`，要先回答「谁来构造 PagingSource」（两条路与倾向已写进该行）；要动的 `feature/library` 与并发会话的工作区相邻。**B6／B7 同理属「要动边界／大范围」，宜在方案定下后成批做。** | — |
| — | **进度（2026-09-13，五）** | **B4 ✅**（R-09 三列：一手核实为死列，处置取「说明白＋触发条件」，并记下"便宜的 DROP COLUMN 在新设备上测不出、在 minSdk 23 上会崩"这个陷阱）。下一步按 §14 是 **B5（`PagingSource` 移出 `core:domain`）**。 | — |
| — | **进度（2026-09-13，四）** | **B3 的第 2 处 ✅** `4cf541e8`（规划侧日界收口，变异恰好 1 条红）；第 1 处经核实**不是免费收口**（它喂损失与拟合）、第 3／4 处是写侧要升版本——三者都写进了 N-26。下一步按 §14 是 **B4（`attempt_event` 三列：接线或删列）**。 | — |
| — | **进度（2026-09-13，三）** | **B2 ✅**（六份临时库脚手架收口；顺带证实其中一份漏了清理）· **B3 待做，且要比原计划更小心**：读侧两处里的 `SchedulingEvaluation.elapsedDaysSince` 回退分支**喂的是损失与参数拟合**（不只是展示），动它会改拟合结果，不是免费的口径收口——要先定它该不该动。 | — |
| — | **进度（2026-09-13，续）** | A4 ✅ `d3e6aaa1` · B1 **部分完成**（`22c10988` 删了 4 项；`ReviewPlanner.plan()` 经核实**否掉**——它是回滚闸门），余下三项见 R-06 行。 | — |
| — | **进度（2026-09-13）** | A1 ✅ `f12e8b73` · A2 ✅ `509f69e3`（其中 `StudentModelContracts` 一条**并入 R-07**、`ReviewPlanner` 那条**核实后否掉**，理由见 §13.5 对应行的处置）· A3 **改期**：核实后它不是一行改动，与 N-16 一起做 | — |
| C′ | **R-11 出网同意归一** | **不能在本分支做**：那条轴在 `main` 上已被产品决定换掉（见 §3 S-2 复核块） | 等变基决定 |

---

## 15. 同名副本清点与保留决定（2026-09-15）

### 15.1 目的与结论

用户要求：把不活跃的分支合并进去、清点清楚、不要误删；随后追加「再确认一次，确认 worktree 里面没有主干不涉及的模块以及改动」。
**两项都测完了，答案都是"没有"**——但清出一个**主干确实没有、且从未进过任何 git** 的小集合（见 15.3）。
用户先定「先不要删，内容需要保留」，随后追加「**非常确定的部分可以把它删掉了**」。据此：确定档 6 项已删，含裸草稿的 2 项保留——**处置与执行见 15.5／15.6**。

### 15.2 模块层：确认无主干不涉及的模块

六份副本（`smb-toolloop`、`bisect-root`、`bisect-root2`、`bisect-split`、`image-subsystem`、`split-large-files`）的 `settings.gradle.kts` include 表与主干**逐字相同、各 17 个**：主干没有的模块 **无**，缺主干任何一个 **无**。

### 15.3 文件层：唯一一类"主干确实没有"的东西

判据用的是最硬的一层——**算文件的 blob 哈希去对象库查**（`git hash-object` ＋ `git cat-file -e`）。这条判据能测出"曾被 `git add` 过"这一档，比"是否出现在某个提交的树里"更强。结果：下列 7 个 blob **在对象库里全部不存在** ⇒ 从未被 `git add`、从未属于任何仓库的任何分支。全库 refs 按完整路径与按文件名双向查询同样无命中；主干对这些符号命中数全为 0。

| 所在副本 | 文件 | 行数 | 是什么 |
|---|---|---|---|
| `split-large-files` | `core/ui/src/main/java/…/core/ui/VectorPlot.kt` | 113 | `VectorPathSpec` 结构化向量绘图渲染器（模型出 SVG path → 画成可缩放图）；**唯一落在 `src/main` 生产源集的一个** |
| `split-large-files` | `knowledge-production/math-atomic-split-plan.md` | 152 | 高中数学知识点拆到 `ATOMIC` 粒度的方案 |
| `split-large-files` | `knowledge-production/sample-function-monotonicity.md` | 190 | 函数·单调性 树形知识库样板书 |
| `split-large-files` | `math-structure-draft.md` | 379 | 24 个高中数学模块结构清单（源自 `knowledge-coverage-ledger`） |
| `image-subsystem` | `core/export/src/androidTest/…/ApparatusSemanticRenderTest.kt` | 109 | 仪器图渲染实验（androidTest） |
| `image-subsystem` | `core/export/src/androidTest/…/PhysicsForceDiagramReproTest.kt` | 123 | 受力图渲染实验（androidTest） |
| `image-subsystem` | `core/export/src/androidTest/…/VectorRichnessExperimentTest.kt` | 121 | 向量丰富度实验（androidTest） |

同批扫出的纯垃圾（`local.properties` ×3、两个 worktree 的 `.git` 文件、`tools/ci/__pycache__/*.pyc`、`.mimosa/hook-state/*.json`）本身无工程价值：所在目录已删的，随之消失；`.mimosa` 那一份在删 `smb-toolloop` 前已留档（见 15.5）。

**这两个副本就是那两个孤儿 worktree**：`.git` 内容为 `gitdir: D:/智能错题本/.git/worktrees/…`，而仓库早已改名、该目录不存在 ⇒ **git 完全读不了它们**，所以这些草稿连"未提交改动"都不是，只是目录里的裸文件。这也是它们必须在**别处**留一份记录的原因（记在这里）。

### 15.4 其余副本的方向：只落后，不领先

除 15.3 那 7 个外，各副本的独有文件**全部**是"主干曾跟踪、后被主干自己删掉"的东西（同意开关 `e462f1ea`、自评通道 `d0bc3b2e`、`TutorProviderMatrix` `3b94b313` 等）。另 `origin/refactor/split-large-files`（`a1b35b6a`）**已是 main 祖先**，其已提交内容早已并入主干。

### 15.5 确定性判据：每个文件都过对象库

只比"副本有、主干 tracked 里没有"会漏掉一类：**两边路径相同、但副本里是另一份从未提交的内容**。所以最终判据是对**副本内每个文件**（仅排除 `build/`、`.gradle/`、`.idea/`、`.kotlin/`、`__pycache__`）算 blob 哈希、逐个查对象库——只要有一个 blob 不在库里，这份副本就**不确定**，不删。

做法与结果（`git hash-object --stdin-paths` ＋ `git cat-file --batch-check`，全程不写对象库）：

| 副本 | 扫描文件数 | blob 不在对象库 | 判定 |
|---|---|---|---|
| `.worktrees/bisect-root` | 1009 | 1（`local.properties`） | **确定**——除 gitignore 的本地件外全部可从历史重构 |
| `.worktrees/bisect-root2` | 1009 | 1（同上，三份 md5 相同 `c70bfda37d06`） | **确定** |
| `.worktrees/bisect-split` | 1027 | 1（同上） | **确定** |
| `/d/smb-toolloop-wt` | 0 | 0 | **确定**——679M 全是 `.gradle/` 缓存，零源码 |
| `smb-toolloop/` | 855 | 2 | 见下 |
| `.worktrees/image-subsystem` | — | 3（三个实验测试） | **不确定** |
| `.worktrees/split-large-files` | — | 4（`VectorPlot.kt` ＋ 三份 `.md`） | **不确定** |

`local.properties` 已核实为 `.gitignore:13` 忽略的本地件，内容只有一行 `sdk.dir=<路径>`，无密钥、无工程信息。

**这次判据抓到了按路径比会漏掉的东西**：`smb-toolloop` 里的
`core/data/src/main/kotlin/…/core/data/model/OpenAiModelTaskAdapters.kt` —— 路径在主干里**存在**，所以"独有文件"差集看不见它；但它这一版是 495 行（2026-08-30 冻结时的未提交态），blob 不在对象库里，而主干历史只有 562 → 571 → 581 行三版。即**主干从未有过这一版**。同类还有 `.mimosa/hook-state/sess_*.json`（工具会话态）。这两个已逐字节留档到 `scratch/from-smb-toolloop-2026-09-15/`（md5 `293dd51462cf8714f0b41aff45e4f8bc`，与源一致，`.gitignore:37` 的 `scratch/` 之下）。

### 15.6 决定与实际执行（2026-09-15）

用户先定"先不要删，内容需要保留"，随后追加"**非常确定的部分可以把它删掉了**"。据此分两档执行：

**已删（确定档，6 项，约 9.5 GB）**——删前均已满足 15.5 的判据，删除后逐项核验不存在：
`.worktrees/bisect-root`(2.4G) · `.worktrees/bisect-root2`(2.5G) · `.worktrees/bisect-split`(2.9G) · `/d/smb-toolloop-wt`(679M) · `smb-toolloop`(1.1G，唯一未进 git 的文件已先留档)。

**保留（不确定档，2 项，3.3 GB）**——含主干确实没有、且从未进过任何 git 的裸草稿，**未动**：
- `.worktrees/split-large-files`（1.3G 前的 2.0G）：`VectorPlot.kt`(113 行)、`knowledge-production/math-atomic-split-plan.md`(152)、`knowledge-production/sample-function-monotonicity.md`(190)、`math-structure-draft.md`(379)。md5 前 12 位依序为 `0ff3d1f544a0` / `2a788e98136a` / `3baec374be4b` / `07db61d94981`。
- `.worktrees/image-subsystem`（1.3G）：`ApparatusSemanticRenderTest.kt`(109)、`PhysicsForceDiagramReproTest.kt`(123)、`VectorRichnessExperimentTest.kt`(121)。md5 前 12 位依序为 `81eae41742b5` / `9f47e30eee08` / `bbce567212c2`。

**未删（非清单项）**：`/d/smb-build` 是符号链接指向主树，本就不在处置范围。
`/d/智能错题本` 是 0 字节空壳，`rmdir` 报 `Device or resource busy`——**有进程持有该目录句柄**，故未删；它不占空间，无害，句柄释放后可随手删。

### 15.7 对后来者的约束

- 保留的那 2 个目录**不在 git 视野内**（`.git` 指向已不存在的 `D:/智能错题本/.git/worktrees/…`），删除不可逆、无历史可恢复。要动之前先读本节。
- 已在 15.6 记下这 7 个草稿的 md5，可用作"是否已被改动/误删"的比对基准。
- 要重新做"能不能删"的判断，用 15.5 的对象库判据，**不要**用"独有文件差集"——本节已举证那会漏。
