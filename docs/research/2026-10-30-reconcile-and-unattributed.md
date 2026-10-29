# 批次 35 研究驱动（T2/T6）：对账 JS 区分复核设计 + [UNATTRIBUTED] 前缀通道 + explicitUserRetry 生产调用点 + M-04/Q-01 分类分离

> 研究员：对账与审计研究（R2 子代理）。本文件是**研究/设计文档，零代码改动、零 Gradle、零设备写、零 git 写**。
> 方法：全部结论来自本会话实读源码（行号为 2026-10-30 时点工作树）与实跑命令（清单见附录 A）；
> 外部参考经 gh CLI / WebFetch 取证并标注来源 URL；无当前证据的结论一律标 **UNVERIFIED**。
> 驱动对象：计划 `.zcode/plans/plan-2026-10-30-batch-35-dual-backend-closeout.md` §1 任务 1（对账复核设计）、
> 13（P-11）、14（P-12②③/M-04/Q-01）；判据源 `docs/research/2026-10-26-batch-24-4e2-admission-checklist.md`
> （v3，下称**清单**）行 P-11（:134）、P-12（:135）、M-04（:158）、Q-01（:209）。
> 基线事实沿用 `docs/research/2026-10-30-batch-35-baseline.md`（下称**基线**），凡引用处注明。

---

## 0. 一句话结论

1. **对账 JS 区分已在批次 34 落树且方向正确**（`FigureGenerationReconcileDao.kt:128` / `:173`），但现行"确认成功"谓词只认 `method = 'JS'` 标签，**没有 `reservation_id IS NULL` 连词**——标签与"本地无预留"这一结构事实之间存在一道可被"JS 名义预留行"穿透的缝；本文给出不可伪造判据的完整论证与一道最小谓词收紧（§1）。
2. **P-11 的链路五站全部在树且逐字保真**，真正的缺口是两端：无跨进程（AIDL）保留断言、无宿主消费方（前缀常量 `internal`，下游无人读）；wire 往返门受"app 模块 JVM 测试无 org.json"约束必须落设备侧（§2）。
3. **P-12② 的生产调用点设计为 `FigureGenerationRequest` 增宿主冻结字段 + 协调器透传**，UI 半边按批次 34 第二道门的先例作热区登记；预算承继断言落在协调器 JVM 用例（同 operationId → 1 操作行 / attempt −1、−2 / 账本零触碰 / 0 远程派发）（§3）。
4. **M-04 的折点唯一且已定位**（`QuickJsVisualExecutionEngine.kt:381-390` 把宿主度量后端失败折成 `INVALID_PROGRAM`），与 :429-435（程序侧预算越界，**应**折 `INVALID_PROGRAM`）的分界就是"异常来源在程序还是宿主设施"；推荐新增端口枚举值 `MEASUREMENT_UNAVAILABLE`，wire 两侧同构建强制相等所以无漂移面（§4.1）。**Q-01 是真实 policy 洞**：JS 分支（`FigureGenerationPolicy.kt:192-226`）确实不查 `requiresPixelFidelity`，补一道 `TARGET_UNSATISFIABLE` 拒绝 + JVM 负向（§4.2）。
5. **GitHub 参考评分**：模式级印证采纳（同事务审计行=outbox、stale 截止对账=Temporal、篡改证据=Merkle/Trillian），**不引入任何外部依赖**——每个"新增"都指认了它消灭的失败，未找到需要"照搬实现"的场景（§5）。

---

## 一、ReconcileDao JS 区分实现复核：不可伪造的"本地已确认成功"判据

### 1.1 现状实现（批次 34 已落，提交 39dbe6d4；基线 §三.1 复核一致）

两处谓词（`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/dao/FigureGenerationReconcileDao.kt`）：

| 位置 | 谓词 | 语义 |
|---|---|---|
| `:122-136` 第二段候选 | `status='SUCCEEDED' AND output_asset_id IS NULL AND method <> 'JS' AND ended_at ≤ cutoff` | 假成功候选**排除 JS**：JS 的产物是内存 `FigureArtifact.Program`，按设计没有 `canonical_source_asset` 行可回填（KDoc `:112-121`） |
| `:162-185` 操作级确认 | `NOT EXISTS (… confirmed.status='SUCCEEDED' AND (confirmed.output_asset_id IS NOT NULL OR confirmed.method = 'JS'))`，且无 RUNNING/PENDING 在途（`:175-179`） | "有任一确认成功的尝试"则**不**把父操作判死 |

改判本体 `failUnconfirmedSucceededAttempt`（`:138-153`）只改 `status/diagnostics/ended_at`、带 `output_asset_id IS NULL` 复核条件，产物列绝不清除——与 KDoc `:18-19`"对账只改状态、诊断与结束时刻，绝不删 output_asset_id"一致。

设备用例已覆盖两条主分支（`core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/visual/FigureGenerationReconcilerInstrumentedTest.kt`，实读）：
- `:225-271` 本地 JS 成功（`output_asset_id` 恒 NULL）对账后保持 SUCCEEDED（操作/尝试计数皆 0）；
- `:285-352` 同操作"IMAGE_MODEL 成功无产物 + JS 成功"混合：只纠 IMAGE_MODEL 那条尝试，父操作因 JS 真实成功保持 SUCCEEDED。

### 1.2 判据设计：为什么 `method = 'JS'` 单独**不是**不可伪造判据

"不可伪造"在本仓的语义不是对抗攻击者（设备本地 SQLite 无敌手模型），而是：**谓词必须只由"真实发生过的本地成功"这一事件的写入径产生，任何其他代码路径（含上游 bug/回归）都无法拼出同一行组合**。逐层枚举写入径（全部实读）：

1. **谁能写 `method='JS'` 的尝试行？** 只有两个写口：
   - `FigureGenerationDao.insertLocalAttempt`（`FigureGenerationDao.kt:125-148`）：`reservationId = null` **硬编码**（`:143`），序号取负数区间（`:134` `MIN(attempt_ordinal)-1`），`attempt_id` 与序号同源；
   - 通用 `insertAttempt`（`ABORT`，`:103-104`），被远程路径 `beginAttempt` 调用时写 `method = reservation.method`（`DefaultFigureGenerationCoordinator.kt:567-579`）。**账本本身不校验 method**：`RoomFigureDispatchLedger.kt:52` 原样写 `request.method.name`。
   - 上游现有两道护栏：协调器远程路径硬编码 `method = FigureGenerationMethod.IMAGE_MODEL`（`DefaultFigureGenerationCoordinator.kt:409`）；`FigureImageDispatchBinding` 构造器 `require(method == IMAGE_MODEL)`（`core/domain/.../FigureGenerationCoordinator.kt:58-59`）。但这两道都在**协调器/绑定层**，不在**台账层**——若未来账本 API 或新入口回归，出现"JS 名义预留"行，其尝试行就是 `method='JS'` **且** `reservation_id 非空`。
2. **谁能把行写成 `SUCCEEDED`？** `finishAttempt`（`FigureGenerationDao.kt:166-182`）与对账三段（只写 FAILED）。端口层把尝试行建为 `RUNNING` 是硬编码（`RoomFigureGenerationAuditRecorder.kt:95` `status = ATTEMPT_RUNNING`，常量 `:166`），`succeed()` 只在引擎 `Rendered` 之后调用（`DefaultFigureGenerationCoordinator.kt:320-324`）。所以 `SUCCEEDED ∧ method='JS' ∧ reservation_id IS NULL` 只能出自"本地引擎真的返回了 Rendered"这条链。
3. **`reservation_id IS NULL` 是判别子的原因**：本地执行按契约不占远程派发（协调器 KDoc `:245-246`"不调用 ledger.reserve……本地执行占远程派发正是契约禁止的错配"）；远程路径的预留 id 恒非空（账本授予，`:573`）。于是 `method='JS' ∧ reservation_id 非空` 是**结构上不可能由合法本地路径产生**的行——现行谓词 `confirmed.method = 'JS'`（`:173`）会把这种异常行当"本地已确认成功"，且若它 `SUCCEEDED + output IS NULL`，第二段候选的 `method <> 'JS'`（`:128`）又把它**永久排除在对账之外**：既虚报成功，又逃过纠察，还占着一笔无从追查的派发预算。
4. **旁证（不加进谓词）**：`attempt_ordinal < 0`（负区间本地专用，实体 KDoc `FigureGenerationEntities.kt:90-91`"两条序列互不相交"）与 `attempt_id` 前缀同源结构；它们与 `reservation_id IS NULL` 同源（同一写口产生），任选其一即可，不叠加（避免同一事实的两份副本漂移——12.2 纪律）。

**结论判据（本设计的核心句）**：

> 「本地已确认成功」≡ 存在尝试行 `status='SUCCEEDED' ∧ method='JS' ∧ reservation_id IS NULL`，且该行由 `insertLocalAttempt`（唯一的 `JS ∧ reservation_id IS NULL` 写口）在**成功之前**建立（`RUNNING` 先行，`finishAttempt` 落终态）。单靠 method 标签可伪造（标签未经台账校验），单靠行存在可伪造（FAILED 行也有行），单靠 SUCCEEDED 可伪造（正是第二段要纠的假成功类）；**三元合取**才是写径唯一的行形状。

### 1.3 与既有"SUCCEEDED+无产物"改判如何并存（并存不变式）

两类的本质差异是**成功之后还有没有可失败的落盘步**：

- **IMAGE_MODEL**：协调器在入口落盘**之前**写 SUCCEEDED（`output` 留空），`attachAttemptOutputAsset`（`FigureGenerationDao.kt:191-201`）是第二次独立写。persist/attach 失败（T3 ATTACH_FAILED、进程被杀）会留下"成功但没产物"→ 这正是第二段改判存在的理由（DAO KDoc `:21-24`）。
- **JS**：`succeed()` 是引擎 `Rendered` 之后**同一协程的最后一步**，之后没有资产形状的落盘（产物=program，本体已在 proposal/trace 里）——"SUCCEEDED"本身就是终态证据，不存在"成功后掉产物"的窗口。所以第二段对 JS 整类豁免是对的；豁免的**精确边界**应当与 1.2 的判据对齐。

并存不变式（改判继续成立的前提，全部已由现实现满足或由下述收紧补足）：
1. 第二段只吃"**非**本地已确认成功"的 `SUCCEEDED+NULL` 行，改判不动产物列（`:146` SQL 复核条件）；
2. 操作级确认条件认"有产物成功"或"本地已确认成功"任一（`:169-174`）；
3. 在途（RUNNING/PENDING）优先——重试可能马上回填产物，不先判死（`:175-179`）；
4. 对账不重派（`FigureGenerationReconciler.kt:22-25` 三条边界），重试只能由用户动作触发——这也是 §3 P-12 的入口合法性来源。

### 1.4 谓词收紧建议（最小改动，消灭 §1.2 第 3 条的具体失败）

**新增必须指认的失败**：若未来出现"JS 名义预留"行（账本/适配层回归），现行实现会让该行永久虚报成功且逃过对账。收紧把这类行重新纳入第二段纠察，操作级确认也只认真本地行。

- 候选查询（`:128`）：`AND method <> 'JS'` → `AND NOT (method = 'JS' AND reservation_id IS NULL)`
- 操作级确认（`:173`）：`OR confirmed.method = 'JS'` → `OR (confirmed.method = 'JS' AND confirmed.reservation_id IS NULL)`

语义对现行两条合法类**逐位不变**（本地 JS 行 reservation_id 恒 NULL；远程 IMAGE_MODEL 行不受影响），只有异常行（JS∧有预留）的行为改变：从"两处都豁免"变为"两处都按假成功纠察"。实体层 `reservation_id` 有指向账本预留表的 RESTRICT 外键（`FigureGenerationEntities.kt:117-121`），异常行必须真有一笔预留存在，不是拼写笔误能造出的。

**用例设计（设备侧，真库；JVM `FigureGenerationReconcilerTest` 是 fake port 不测 SQL——`:212` `override suspend fun reconcileStaleFigureGenerationRuns`）**：
- 正向（已有）：`FigureGenerationReconcilerInstrumentedTest.kt:225-271`、`:285-352` 两例不回退；
- 新负向 A：插入 `method='JS' ∧ reservation_id=<真实预留> ∧ SUCCEEDED ∧ output NULL` → 对账后该尝试 FAILED（`RECONCILED_UNCONFIRMED_SUCCESS`）、父操作 FAILED；
- 新负向 B：同一操作里"负向 A 行 + 本地 JS 行（reservation_id NULL, SUCCEEDED）"→ 本地行保持 SUCCEEDED，父操作因真本地行保持 SUCCEEDED（确认谓词只认真本地行，不认异常行）。

**判定**：计划 §1 任务 1 的"区分"半边已在树；本设计的增量 = ①谓词收紧（1.4）、②两个异常负向用例。两者都属"消灭已指认失败"的最小形态；若主会话裁定上游两道护栏（coordinator `:409` + binding `:58`）足够、拒收紧，则必须把"JS∧预留=未定义行为"写进 DAO KDoc 作为登记，不可静默。

---

## 二、P-11：`[UNATTRIBUTED]` 前缀通道设计（AIDL → 宿主保留并消费）

### 2.1 链路五站现状（全部实读，逐字保真已成立）

| 站 | 文件:行 | 行为 |
|---|---|---|
| ① 分类器 | `core/data/.../visual/VisualJsFailureClassification.kt:141`（`UNATTRIBUTED_DIAGNOSTIC_PREFIX = "[UNATTRIBUTED]"`）、`:192`（丢失哨兵 → `UNATTRIBUTED`）、`:227-231`（诊断以前缀开头，附 raw/mallocSize/mallocLimit） | 纯函数；`reason` 折 `INVALID_PROGRAM`（`:135`），**对外枚举不新增 UNKNOWN**（T2 裁定，`:45-47`） |
| ② 引擎/服务 | `QuickJsVisualExecutionEngine.kt`（runPhase 吞 `PhaseRejection`）→ `VisualJsService.kt:586-590` `encodeRejection(reason, diagnostics)` 进 payload 信封；`:605-612` `callback.onResult(..., readEnd, summary)`——payload（PFD）与 AIDL `diagnostics` String 双路都带原文 | 信封 `{reason:"INVALID_PROGRAM", diagnostics:"[UNATTRIBUTED] …"}` |
| ③ AIDL | `app/src/main/aidl/.../IVisualJsCallback.aidl` `oneway void onResult(long, String, int, in ParcelFileDescriptor, String)` | diagnostics 是普通 String Binder 参数 + payload JSON 双通道 |
| ④ 客户端 | `VisualJsClientEngine.kt:483` `decodeResult(status, bytes, diagnostics)` → `VisualJsProtocol.kt:308-341` `decodeRejection`：reason 按名映射回枚举（`:330`），诊断**逐字**带回（`:340` `boundedDiagnostics(detail)`，截断上限 `FigureGenerationOutcome.MAX_DIAGNOSTICS_CHARS`）；未映射 reason 显式登记不静默（`:334-338`） | 前缀在传输后仍居诊断首位（`bounded` 截断从头部保留） |
| ⑤ 宿主协调器 | `DefaultFigureGenerationCoordinator.kt:335-348`：`boundedRejection`（`:744-746`）拼 `"${reason.name}: ${diagnostics}"` 落 `FigureGenerationOutcome.diagnostics` + attempt 审计诊断（`:337`） | `INVALID_PROGRAM: [UNATTRIBUTED] …` 原样进台账/trace |

### 2.2 两个真实缺口（清单 P-11 行"未落"的现时精确化）

- **缺口 1（保留无断言）**：没有任何测试证明前缀**跨进程**活着。全仓 `[UNATTRIBUTED]` 断言只存在于分类器 JVM 测试（`VisualJsFailureClassificationTest.kt:165/:191`）；跨进程套件 `VisualJsIsolationInstrumentedTest.kt` grep `UNATTRIBUTED` = **0 命中**（本会话实跑）。且 **wire 往返门落不了 JVM**：`VisualJsProtocol` 用 `org.json`，而 `app/build.gradle.kts:206` 测试依赖仅 `libs.junit`，`grep -rl "org.json" app/src/test/` = **0 命中**（实跑）——org.json 在本地单测的 mockable android.jar 下不可用，协议往返断言必须设备侧（androidTest 有真 org.json + 真 Binder），与计划 §2"AIDL 只以 Gradle 任务验证"一致。
- **缺口 2（无消费方）**：前缀常量是 `internal`（`VisualJsFailureClassification.kt:141`，core:data 内部），链路末端**没有任何代码读它**——`boundedRejection` 只拼串。后果即清单 P-11 行所引 R3 §6.6 的退化口径失效：回喂模型时 `INVALID_PROGRAM: [UNATTRIBUTED] …` 与真程序错不可区分，模型会去"修"一个没有错的 program（与 M-04 同型的回喂误导，见 §4.1）。清单附录警示原话："把'[UNATTRIBUTED] 有前缀'当'宿主已消费'"（清单 `:301`）。

### 2.3 通道设计

1. **前缀晋升为宿主可见的单一来源**：把前缀常量从 core:data `internal` 晋升到 core/domain 端口层（与 `VisualExecutionRejection` 同文件或伴生对象，KDoc 写死字面量 `[UNATTRIBUTED]` 不许改——跨进程映射与设备断言都只认这个值，现有 core:data 常量改为引用它，删副本）。**指认的失败**：常量在 core:data internal，宿主（协调器/trace 写口）无法引用 → 消费不可能发生；副本常量会漂移。
2. **消费点 = 协调器把"不可归属"折成与程序错不同的出口语义**：现成载体是 tool round 的类型化 `errorKind` 通道（先例：`FIGURE_METHOD_UNAVAILABLE`，`RoomModelTaskToolRounds.kt:124` + `RoomTutorToolRunner.kt:959`）。协调器在 `Rejected` 分支（`DefaultFigureGenerationCoordinator.kt:335-348`）识别 `reason == INVALID_PROGRAM && diagnostics.startsWith(<前缀>)` → errorKind 记**不可归属**类（如 `figure_engine_unattributed`），不落"程序错"类。**指认的失败**：回喂模型把引擎不可归属失败当程序错反复修 program（R3 §6.6 的退化口径从"登记"变"生效"）。UI 渲染半边（`TutorToolTraceLine.kt`，core/ui 热区）不落，同 `figure_method_unavailable` 痕迹渲染一起按热区登记（基线 §四已登记该项仍未落）。
3. **三层门**：
   - **JVM（已有，不回退）**：低堆注入负向——丢哨兵 + 低堆 → `UNATTRIBUTED` 且诊断以前缀开头（`VisualJsFailureClassificationTest.kt:159-165`）；批次 23 证人 1.06% 水位同判（`:173-179`）；无堆快照同判（`:186-191`）。这条负向的语义是**防伪造 OOM**：程序 `throw null` 造不出 `MEMORY_LIMIT`（哨兵单独不构成堆证据，`:190-192` 两条件合取）。
   - **设备 wire 往返（新增）**：`VisualJsIsolationInstrumentedTest` 注入"丢失哨兵 + 低堆"程序 → 宿主侧断言收到的 `Rejected` 满足 `reason == INVALID_PROGRAM && diagnostics.startsWith("[UNATTRIBUTED]")`（跨 真 Binder + 真 PFD + 真 org.json 信封）；并断言 AIDL `diagnostics` 参数与 payload 信封双路一致（`VisualJsService.kt:610-612` 传的是同一 `summary`）。
   - **审计保留（新增，JVM 可测）**：协调器用 fake 引擎返回 `INVALID_PROGRAM + [UNATTRIBUTED]` 诊断 → 断言 attempt 审计诊断与 outcome 诊断都以前缀存活（`boundedRejection` 不吃掉它），且 errorKind 走不可归属类。
4. **边界记录（清单判据的第三句）**：三类已知边界如实登记进消费点 KDoc——栈根分配型真 OOM（堆快照被 GC 回收致比值不成立，PENDING_CALIBRATION，`VisualJsFailureClassification.kt:39-41`）、丢失哨兵+高堆、非严格 OOM 前缀（`RangeError: out of memory` 是程序伪造，`VisualJsFailureClassificationTest.kt:78-84` 已钉）。

**判定**：本设计给清单 P-11"前缀保留/消费门 + 边界记录"的落点；`availableMethods` 不动（广告开关零改动纪律不变）。

---

## 三、P-12②③：explicitUserRetry 生产调用点（非 UI 半边）与预算承继断言

### 3.1 现状（全部实读/实跑）

- 端口字段：`VisualExecutionRequest.explicitUserRetry`（`core/domain/.../VisualExecutionEngine.kt:72`，KDoc `:34`"不自动重跑坏源码（契约 §4）；用户明确本地重试"）。
- 熔断三处：引擎内 `QuickJsVisualExecutionEngine.kt:224`（`!explicitUserRetry && digest in fusedProgramDigests` → 拒）；客户端入队预检 `VisualJsClientEngine.kt:189-192`、出队复查 `:243-247`；**成功的显式重试解除熔断** `:294-297`（`fusedDigests.remove`）。digest = 源码 SHA-256（结构事实，清单 P-12 行）。
- **生产 `explicitUserRetry=true` 调用点：不存在**（清单 P-12 行 G-7② 原话"生产侧无调用点——全仓仅测试构造 4 处"；本会话实跑 grep 复核一致：命中全部为测试 `QuickJsVisualExecutionEngineInstrumentedTest.kt:175/186`、`VisualJsIsolationInstrumentedTest.kt:323/327/338/682/706`、契约 `VisualExecutionEngineContractTest.kt:62/72/425`、fake `FakeVisualExecutionEngine.kt:196`，外加 wire 解码 `VisualJsProtocol.kt:136/:198`——解码不是调用点）。
- 协调器构造 `VisualExecutionRequest`（`DefaultFigureGenerationCoordinator.kt:292-304`）不传该字段 → 默认 false；`FigureGenerationCoordinatorTest.kt:306` `assertEquals(false, sent.explicitUserRetry)` 现行为钉死。

### 3.2 设计：生产调用点落协调器（非 UI 半边），UI 半边热区登记

1. **`FigureGenerationRequest` 增宿主冻结字段** `explicitUserRetry: Boolean = false`（core/model，KDoc 语义：仅当用户对**同一逻辑操作**显式重试时为 true，宿主必须复用同一 `operationId`）。默认 false 对三个既有生产构造点（`ConfiguredCleanImageGenerator.kt:100`、`AttachedImageGenerator.kt:155`、`TutorFigureGenerator.kt:377`）逐位零变化。注意"请求 schema 只由对应轨写"约束针对 OpenAiModelProtocol 的 wire schema（`core/data/.../OpenAiModelProtocol.kt:378-379`，基线 §一核对）——本字段是 Kotlin 模型字段，**不进**模型请求 schema，两轨不冲突。
2. **协调器透传**：`generateLocalJs` 的 `VisualExecutionRequest(...)`（`:294-303`）增 `explicitUserRetry = request.explicitUserRetry`。**远程路径不消费**（在 `dispatchWithReservation` 不读它）：熔断的恒等物是 programHash，只存在于 JS 引擎；IMAGE_MODEL 的重试预算已由账本管理（`RemoteModelDispatchLedger`），二者不混。**指认的失败**：没有生产 true 调用点时，看门狗硬杀/引擎崩溃后的同 programHash 重发永远 `PROGRAM_FUSED`，用户侧"重试"无法生效——熔断从"保护"变"死锁"。
3. **UI 半边=热区登记**：用户重试按钮（feature/*）→ 入口构造 `explicitUserRetry=true` 的请求，属他线热区，本批不落；与批次 34 第二道门"落点在热区文件本体则整文件纳入并注明"同模式登记（基线 §三.2 先例）。当前 JS 未广告（`availableMethods` 未含 JS），用户可见的重试面尚不存在，代码层先行不接通是安全的。
4. **预算承继断言（JVM，`FigureGenerationCoordinatorTest` 增例）**：
   - 正向：同 `operationId` 第二次 generate（flag=true，同 program）→ 断言 `ledger.requests` 仍空（JS 不预留，`:245-246`）；审计操作仍 1 行（同 id `insertOperation` IGNORE，`FigureGenerationDao.kt:27-28`）；attempt 两条（-1、−2，`insertLocalAttempt` 负序号链 `:134`）；`operationFinishes` 的 `reservedRemoteDispatches/usedRemoteDispatches` 恒 0/0（`FigureGenerationCoordinatorTest.kt:318-319` 既有断言形状）；`engine.requests.last().explicitUserRetry == true`。
   - 负向（承继的反面）：**不同** operationId 的重试是新逻辑操作——审计两行、承继不成立；断言协调器不因 flag=true 跳过任何 policy/预算门（policy 对 JS 的预算门本就不生效 `:147-149`，但对 IMAGE_MODEL 的 true 不产生任何豁免——远程路径不读该字段）。
   - ③"解除熔断直接断言 + 客户端熔断 JVM 状态机专项"：客户端熔断集合的真实现（`VisualJsClientEngine`）依赖 Android 运行面，JVM 只有端口契约层 fake（清单 P-12 行证据列原话）；**专项落设备**（隔离套件已有同 hash 自动重发 `PROGRAM_FUSED` 断言 `VisualJsIsolationInstrumentedTest.kt:318` 附近，补"成功显式重试后同 hash `Rendered` 且熔断解除"直接断言），JVM 半边只做端口契约（已有 `VisualExecutionEngineContractTest.kt:422-425`）。此为现状约束下的落点，不是放宽。

**判定**：P-12② 生产调用点 = 模型字段 + 协调器透传 + JVM 预算承继例；③ 解除断言归设备、JVM 专项按现状边界落端口层。清单"预算承继依赖 A-10 ledger × 引擎装配"的装配半边：JS 路径结构性不触账本，承继证据落在审计形状（1 操作行/0 派发）——这是 JS 半边能给出的最强承继证据，如实写明。

---

## 四、M-04 失败分类分离 与 Q-01 requiresPixelFidelity 拒绝

### 4.1 M-04：度量/后端失败从 `INVALID_PROGRAM` 分离

**折点定位（唯一）**：`QuickJsVisualExecutionEngine.kt:381-390`——

```kotlin
val measured = try { measurer.measure(batch) }
  catch (cancel: CancellationException) { throw cancel }
  catch (failure: Throwable) {
    return rejected(VisualExecutionRejection.INVALID_PROGRAM,
      "content measurement failed: ${failure.javaClass.simpleName}") }
```

`measurer` 是**宿主侧**原生度量设施（服务级度量器，批次 27 T2 已替换掉批次 22 的 `UnwiredVisualContentMeasurer`——`VisualJsService.kt:83` 注释；批次 22 记录行 22 与遗留 #10 是本条目源起）。它运行时抛（设施 bug/宿主资源失败）= 不是程序的错，但折成 `INVALID_PROGRAM` 后按协调器语义（`DefaultFigureGenerationCoordinator.kt:741-746`："宿主据此区分'该改输入'（INVALID_PROGRAM 等）与'该稍后重排'"）会回喂模型"程序无效"→ 模型反复修 program，永不收敛（清单 M-04 行原话"回喂模型会反复修 program"）。

**分界线（设计判据）**：异常来源在程序还是在宿主设施。
- 程序侧**应**折 `INVALID_PROGRAM` 的近邻（不动）：`:352-360`（stateJson 非 JSON，state 来自程序产物）、`:429-435`（度量批次/条目构造器预算强制——条目来自程序 prepare 输出，越界是程序侧）。
- 宿主设施侧**必须分离**：`:381-390` 的 measurer 失败。

**方案（两案，推荐 A）**：
- **A（推荐）**：端口枚举新增 `MEASUREMENT_UNAVAILABLE`（`VisualExecutionEngine.kt:285` 枚举处），引擎 `:386-389` 改投它，诊断带稳定标记（与 P-11 前缀同一模式）；协调器 `:341-343` 把它与 `ENGINE_CLOSED` 同样归 `FigureGenerationStatus.UNAVAILABLE`（"本机设施不可用、非程序错、不回喂修 program"）。wire 安全性：`decodeRejection` 按名映射、未映射显式拒绝不静默（`VisualJsProtocol.kt:330-339`）；两端同构建由 limits 逐项相等强制（`:343-371`）——无版本漂移面。**指认的失败**：设施失败被回喂成程序错。清单验收句"负向用例断言 `measurement unavailable` 单独分类"逐字满足。
- **B（零枚举面替代）**：维持 `ENGINE_CLOSED` + 稳定诊断标记。缺点：`ENGINE_CLOSED` 语义是"引擎已关闭"（`:310-311`），设施失败混入后审计无法仅凭 reason 区分两类设施故障；清单"单独分类"的字面满足度弱。列为备选不推荐。

**负向用例设计（JVM）**：注入恒抛的 measurer → 断言 `reason == MEASUREMENT_UNAVAILABLE`（或案 B 的标记）且协调器 outcome 状态 `UNAVAILABLE`、审计诊断带标记；对照例（既有语义不回退）：度量条目超预算仍 `INVALID_PROGRAM`（`:429-435` 路径）。

**相邻观察（不扩 scope，登记不动）**：`VisualJsProtocol.decodeRendered`（`:291-305`）把"完整但畸形的结果信封"折 `INVALID_PROGRAM`，其 KDoc `:239-241` 自认"那是协议 bug"——同型的"宿主侧折程序错"。M-04 行义只覆盖度量；此项登记为后续候选，不并入本批。

### 4.2 Q-01：JS 分支补 `requiresPixelFidelity=true` 拒绝

**洞确认（实读）**：`FigureGenerationPolicy.kt:192-226` `validateJsProposal` 只查 `requiresParametricInteraction`（`:219-224`），不查 `requiresPixelFidelity`；pixel 查只在 IMAGE 分支（`:305-313`"目标要求原像素保真，只能用支持该能力的图像编辑"）。而 `FigureGenerationTarget` 的语义（`FigureGeneration.kt:113-124`）要求"必需原像素保真时只能走支持的 EDIT 能力"——JS 参数化重绘从 canvas 指令重建，**不触原像素**，不可能兑现 pixel 保真承诺；现行 JS 分支会放行一个 `requiresPixelFidelity=true` 目标的 JS proposal，正是清单 Q-01 行"真实 policy 漏洞"。

**可达性现状**：三个生产入口构造 `FigureGenerationTarget` 恒 `requiresPixelFidelity = false`（`ConfiguredCleanImageGenerator.kt:105`、`AttachedImageGenerator.kt:161/:167`、`TutorFigureGenerator.kt:360/:366`，实跑 grep）——洞当前不可达，但 policy 是纯校验门，自洽性必须不依赖调用方善意（4E.2 双后端收口后 entry 面会扩大）。

**设计**：`validateJsProposal` 增——

```kotlin
if (request.target.requiresPixelFidelity) {
    return reject(FigureGenerationRejectionCode.TARGET_UNSATISFIABLE,
        "目标要求原像素保真，JS 参数化重绘不能冒充完成")
}
```

与 `requiresParametricInteraction` 恰成镜像：交互承诺 JS 能给（查能力 `supportsParametricInteraction`）、像素保真 JS 结构上给不了（无条件拒）；IMAGE 分支反向（交互给不了 `:299-304`、像素保真看 EDIT 能力 `:305-313`）。**负向用例**：`FigureGenerationPolicyTest` 增 JS pixel 负向（现有 IMAGE 侧例 `pixelFidelityRequiresAnEditCapableMethod` `:317-334` 作形状参照）；对照例：`requiresPixelFidelity=false` 的 JS proposal 照常 Accepted（防过拒）。

---

## 五、GitHub 参考（任务对账 / 审计不可伪造）评分

检索：`gh search repos`（"transactional outbox" / "tamper-evident audit log" / "audit log hash chain" / "transparency log merkle"）+ `gh repo view` 元数据（star/license/pushAt/主语言，2026-10-29/30 实跑）；关键主张用 WebFetch 对 README/官方文档二次取证（单来源已标）。按"更新度、维护状态、license、技术栈兼容、相关性"评分（5 分制）。

| 仓库 | 元数据（gh 实跑） | 可迁移点 | 对本仓裁定 | 分（语言/维护/license/相关） |
|---|---|---|---|---|
| **namastack/namastack-outbox** | Kotlin、Apache-2.0、399★、pushed 2026-10-29（当日） | 事务性 outbox："Business data and its outbox record commit together"（README 实fetch）；at-least-once、消费方需自行幂等 | 与 §1 的同事务判据同构：审计行与状态迁移一个写事务（`insertLocalAttempt` 的 `@Transaction`）、`insertOperation` IGNORE=幂等重放。**模式印证采纳，不引依赖**（Spring Boot 库，Android 不可用也不该引） | 5 / 5 / 5 / 5 |
| **temporalio/temporal** | Go、MIT、24,064★、pushed 2026-10-29 | durable execution：Activity 失败/超时按 Retry Policy 自动重试、Heartbeat 断点续跑（docs.temporal.io/activities 实fetch） | **反例参照**：我们的对账刻意**不自动重派**（`FigureGenerationReconciler.kt:22-25`）、不自动重跑坏源码（熔断）——"不自动付钱/不自动重跑"是本仓更严的裁定，Temporal 证明自动重试可行但不适用本预算模型。stale 截止判据（进程启动时刻 vs 超时窗口取 max，`FigureGenerationReconciler.kt:75-76`）与其"超时即重投"同题异答 | 2 / 5 / 5 / 4 |
| **google/trillian** | Go、Apache-2.0、3,760★、pushed 2026-10-02 | Merkle 树透明日志：追加只写、密码学可验证 | 审计不可篡改的权威参照。**裁定不引入**：本仓防的是误报/虚报（流程回归），不是日志攻击者；行完整性已由 KD-81 备份内容身份（db+wal 进 sha）承载。若未来台账需要跨设备可验证性，Merkle 化是正解（登记为远期选项） | 2 / 4 / 5 / 3 |
| **jobrunr/jobrunr** | Java、LGPL-3.0（README badge 实fetch；gh licenseInfo 记 Other）、3,102★、pushed 2026-10-29 | SQL 持久化任务状态机 + 乐观锁（README："guarantees execution by single scheduler instance using optimistic locking"） | license 重（LGPL）；README **未**证 zombie/孤儿任务 requeue（实fetch 原话"doesn't explicitly document zombie/orphan job requeue"）——恰好是我们需要的部分 | 3 / 4 / 2 / 3 |
| 检索弃用项 | "tamper-evident audit log" 命中多为 <200★ 单人项目（wirken 193★ MIT、aigis 53★ Apache 等） | — | 星数与维护度不足，且与 Android/Room 场景错配；不采纳（零命中≠不存在，但两轮关键词未见更优，停止检索——11.4 三轮上限纪律） | — |

**综合结论**：任务对账与审计不可伪造在本仓的形态（同事务审计行 + stale 截止 + 状态 CAS 改判 + 结构判据而非标签判据）与业界高质量实现（outbox / durable execution / transparency log）**模式一致**；未找到需要引入的代码级依赖，全部收益以"模式印证 + 三个 KDoc 引用"落地。三个新增（§1.4 谓词收紧、§2.3 前缀晋升、§4.1 枚举值）各自指认的失败见各节，无"觉得好就加"。

---

## 六、验收映射与未验证项

| 计划 §1 任务 | 本文交付 | 状态 |
|---|---|---|
| 1（对账 JS 区分复核） | §1 现状复核 + 不可伪造判据论证 + 谓词收紧 SQL + 2 个负向用例设计 | 设计完成；谓词/用例**未落码**（属实现轨） |
| 13（P-11） | §2 链路五站现状 + 两缺口 + 前缀晋升/消费点/三层门设计 | 设计完成；落码属实现轨 |
| 14（P-12②③/M-04/Q-01） | §3 生产调用点+承继断言设计；§4.1 折点+两案（推荐 A）；§4.2 洞确认+拒绝设计 | 设计完成；落码属实现轨 |
| GitHub 参考 | §5 五项评分（元数据 gh 实跑、关键主张 README/docs 二次取证） | 完成 |

**UNVERIFIED / 边界**：
- 本轨零 Gradle、零设备、零 git 写——所有"测试已绿/套件计数"引用均转引自基线与清单原文，本会话未复跑任何套件；
- 工作树为开工即脏（685 条，基线 §一）；本文引用行号以 2026-10-30 实读为准，他线在飞可能使行号漂移；
- §1.4 谓词收紧、§2.3、§3.2、§4 均为**设计建议**，是否采纳由主会话裁定（§1.4 的"拒收紧须登记"路径已给出）；
- 批次 34 已提交文件与当前脏项交集为空（基线 §二），本文未改动任何文件。

## 附录 A：本会话实跑命令（可重跑）

```bash
# 热区/文件定位与实读（Read 工具）：FigureGenerationReconcileDao.kt 全文、FigureGenerationDao.kt 全文、
# VisualJsFailureClassification.kt 全文、FigureGenerationPolicy.kt 全文、FigureGenerationReconciler.kt 全文、
# VisualJsProtocol.kt 全文、DefaultFigureGenerationCoordinator.kt:1-42/240-420/560-748、
# QuickJsVisualExecutionEngine.kt:330-470、RoomFigureGenerationAuditRecorder.kt:70-100、VisualExecutionEngine.kt:1-90/285-315

grep -rn "insertLocalAttempt\|reconcileStaleRunning" --include=*.kt core/ app/ | grep -v dao/FigureGeneration
grep -rn "explicitUserRetry" --include=*.kt core/ feature/ app/ | grep -v build/      # 生产 true 调用点=0
grep -rn "requiresPixelFidelity" --include=*.kt core/ app/ | grep -v build/
grep -rn "FigureGenerationReconciler(" --include=*.kt core/ app/ feature/ | grep -v Test
grep -n "UNATTRIBUTED|throw null|unattributed" app/src/androidTest/.../VisualJsIsolationInstrumentedTest.kt  # 0 命中
grep -rl "org.json" app/src/test/                                                     # 0 命中
grep -n "method|IMAGE_MODEL|JS|require" core/data/.../RoomFigureDispatchLedger.kt     # 账本不校验 method
grep -rn "outcome.diagnostics|\.diagnostics" <三个生图入口>                             # 诊断流向
gh search repos "transactional outbox" --limit 6 --json fullName,stargazersCount,license,updatedAt,description
gh search repos "tamper-evident audit log" --limit 5 --json …
gh search repos "audit log hash chain" --limit 5 --json …
gh repo view temporalio/temporal|google/trillian|namastack/namastack-outbox|jobrunr/jobrunr --json stargazerCount,licenseInfo,pushedAt,primaryLanguage
# WebFetch：namastack-outbox README（"Business data and its outbox record commit together"）、
#   jobrunr README（LGPLv3 badge；无 zombie requeue 表述）、docs.temporal.io/activities（Retry Policy/Heartbeat）
```

## 附录 B：来源 URL

- https://github.com/namastack/namastack-outbox （Kotlin 事务性 outbox；Apache-2.0；README 引句经 WebFetch 核）
- https://github.com/temporalio/temporal （durable execution；MIT）
- https://docs.temporal.io/activities （Activity Retry Policy / Heartbeat 语义引句）
- https://github.com/google/trillian （Merkle 透明日志；Apache-2.0）
- https://github.com/jobrunr/jobrunr （SQL 任务状态机；LGPL-3.0；README 经 WebFetch 核）
- 本仓内：`.zcode/plans/plan-2026-10-30-batch-35-dual-backend-closeout.md`；`docs/research/2026-10-30-batch-35-baseline.md`；`docs/research/2026-10-26-batch-24-4e2-admission-checklist.md`（:134/:135/:158/:209/:301）；`docs/research/2026-10-26-batch-22-record.md`（§5 行 22、遗留 #10）
