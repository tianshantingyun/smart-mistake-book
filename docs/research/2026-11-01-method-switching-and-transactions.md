# 批次 38 V-02/V-03/V-04 研究：方法切换与事务/竞态设计（2026-11-01）

> 作者：R1 切换研究（子代理，只读研究轨：零代码改动、零 Gradle、零设备写、零 git 写）。
> 证据标准：全部结论来自本会话实读文件（行号为实读时行号，实施时按符号重锚）与实跑 grep/gh/检索；
> 外部来源逐条给 URL；无法证实的写 **UNVERIFIED/待验证**。
> 对照：计划 `.zcode/plans/plan-2026-11-01-batch-38-triple.md` §1 A（下称计划）；
> 清单 `docs/research/2026-10-26-batch-24-4e2-admission-checklist.md` v3 `:221-:223`（下称清单）；
> 契约 `docs/current/visual-generation-contract.md` §1/§7/§8/§8.1（下称契约）；
> 基线 `docs/research/2026-11-01-batch-38-baseline.md` §三（下称基线）。

## 0. 结论摘要

1. **V-02（方法切换）最小实现推荐"调用侧编排 + 现有审计/账本零改动"**：切换点放在协调器调用方
   （core/data 入口适配层），一次切换 = 同 `operationId` 的第二次 `coordinator.generate(...)`。
   现树审计面已天然支持：`insertOperation` 为 `OnConflictStrategy.IGNORE`（同 operationId 重放不覆盖，
   `FigureGenerationDao.kt:18`/`:27-28`），attempt 行每次新建（`ABORT`，`:103-104`），操作终态由
   最后一次尝试覆写（`finishOperation`，`FigureGenerationDao.kt:87-101`）；持久账本 per-operation
   计数跨方法累计（`attemptOrdinal = result.logicalDispatchCount`，`RoomFigureDispatchLedger.kt:107`；
   `MAX_DISPATCHES = 6`，`ModelTasks.kt:61`）——"同 operationId / 预算不重置 / 不新增确认"三语义
   无需新表新列。**方向不对称是结构性的**：JS→IMAGE 可环内机械回退（IMAGE 载荷 imageOperation+
   imagePrompt 入口已有）；IMAGE→JS（"图像不佳"）的 program 只能来自模型，最小实现=失败诊断经
   有界 `TutorVisualContext` 回喂下一次已有预算内模型回合（契约 §7 已立此上下文），不是机械回退。
2. **V-03 剩余半边**：checkpoint/GC/恢复门已落码（`VisualCheckpointRetention` 15 例、
   `VisualSnapshotRestore` 12 例在树，本会话 grep `fun \`` 实读）；**未见**的是三组跨用例：
   删会话不删保存题图（现树 FK 已是 CASCADE/RESTRICT 分离，`TutorVisualEntities.kt:225-227`）、
   固定引用不回收的**端到端**（pin→evict 不删）、进程重建读最近 checkpoint + strictOffline
   重放 0 派发（恢复门有、0 派发观测专项无）。测试设计见 §3.2。
3. **V-04 竞态面**：同事务提交/STAGING 租约/指针 CAS/事务内预算重判全部已落码
   （`RoomStudyDatabase.commitTutorTurnWithVisual` `:2030-2125` 等）；**未见**的是
   "失败/取消清 staging"与"迟到结果不覆盖新 revision"的专项用例。竞态面枚举与断言见 §4.2。
4. **外部参考**：resilience4j（Apache-2.0，10.8k★，pushed 2026-10-30，`gh api` 实证）、
   Temporal activity retry（官方文档）、transactional outbox（microservices.io/AWS）——
   三个可借鉴点与本项目已有语义的映射见 §5；**无一个可直接引入**（均为外部依赖或模式参照，
   本项目语义已有更强约束），只取其判据形状。

---

## 一、输入：清单行原文与现树锚点（本会话实读）

| 项 | 清单行原文（`:221-:223`，Read 实读） | 验收用例（行内） | 现树已落/未落（基线 §三 + 本会话复核） |
|---|---|---|---|
| V-02 | "方法切换：JS 失败 → IMAGE_MODEL、图像不佳 → JS；同 operationId、不新增确认、预算不重置、无双重载荷" | 双向切换 + 同 operation 断言（设备/JVM） | 未落。A-10 有"同 operationId 换 method 计数不重置"的**设备断言位**（清单 `:198`），但 JS↔IMAGE 切换语义用例不存在（基线：`core/data/.../visual/` 测试目录 grep `switch/fallsBack` 0 命中，本会话 ls 测试目录复核一致，§一文件清单无切换测试） |
| V-03 | "保存题/删除会话/GC：删会话不删保存题图；固定引用不回收；进程重建读最近 checkpoint；strictOffline 可重放已持久程序且 0 派发" | 三组仪器化（设备） | 半边落：retention/restore 纯策略 + 测试在树；跨用例（删会话/重建/strictOffline 0 派发）未见 |
| V-04 | "正文/trace/visual link 同事务；失败/取消清 staging；迟到结果不覆盖新 revision" | 取消/迟到/staging 仪器化（设备） | 半边落：同事务提交载体 + 租约 + CAS 在树；清 staging 与迟到结果的专项用例未见 |

**契约关键句**（实读，V-02 设计的规范依据）：
- §1（`:11`）："允许模型在同一操作内根据失败诊断修复或重选方法，无需新增逐图确认；切换仍受原目标、授权、质量和预算约束。没有可行方案时保留原成果/原图并说明，不以换方法掩盖失败。"
- §7（预算段）："统一管线每个逻辑操作最多6次Provider模型执行派发……JS本地执行不占远程派发。……换方法不换operationId，不隐藏重试/审查请求。"
- §8（attempt 表）："figure_generation_attempt：operation FK、序号、模型所选method/理由……方法切换仍在同一operation。"
- §8.1："请求由宿主绑定……迟到 checkpoint 不覆盖新状态""失败、取消或进程租约失效时先持久化 owner 状态并对账……""删除会话不删保存题/导出固定的快照。"

---

## 二、V-02：方法切换最小实现设计

### 2.1 现树事实链（切换设计要贴合的既有结构）

| 事实 | 锚点（本会话实读） | 对切换设计的含义 |
|---|---|---|
| `operationId` 派生不含 method | `TutorFigureGenerator.kt:330` `val operationId = "figure-op:${request.kind.name}:$figureId"`；`TutorFigureResult.operationId` KDoc `:141-145` "不含 method；换方法/重试不复制身份" | 切换沿用同一 operationId **零改动** |
| 协调器一次调用 = 一次尝试，方法分派在 `generateImage` | `DefaultFigureGenerationCoordinator.kt:211-234`（`admitFinalFigure` 门在分派**前** `:217`；IMAGE→`:236`，JS→`:271`） | "切换=再调一次 generate"与既有语义正交；最终图门天然跨方法合计（`MAX_FINAL_FIGURES_PER_OPERATION = 2`，KDoc 明言"两种方法合计计数"，`FigureGenerationPolicy.kt:445`） |
| 操作行幂等、尝试行唯一、终态覆写 | `FigureGenerationDao.kt:27-28`（IGNORE）/`:103-104`（ABORT）/`:87-101`（finishOperation UPDATE 覆写，KDoc "晚到尝试不会共用同一 operation 行"——指并发晚到，串行切换不冲突） | 同 operationId 第二次 generate 的审计语义已正确：行不重复、终态=最后一次事实 |
| JS attempt 不占远程派发 | `DefaultFigureGenerationCoordinator.kt:268-269`（不调 `ledger.reserve`）、`:657-673`（`beginLocalAttempt`，`reservation_id=NULL`，负序号由持久层分配） | JS→IMAGE 切换后 IMAGE 首次进账本，`attemptOrdinal` 从账本 logicalDispatchCount 起——远程计数不被 JS 污染，也不因切换重置 |
| 远程预算真实门在账本 | `ModelTasks.kt:61` `MAX_DISPATCHES = 6`；`RoomFigureDispatchLedger.kt:73-78`（终答位扣减）、`:107`（序号=logicalDispatchCount） | 切换不换 id ⇒ 6 次上限跨方法共用，"预算不重置"由持久结构保证 |
| JS 失败分类（可归属 vs 设施类） | `VisualJsFailureClassification.kt`（`UNATTRIBUTED` 哨兵/堆兜底 `:42-46`；`UNATTRIBUTED_DIAGNOSTIC_PREFIX = "[UNATTRIBUTED]"` `:141`）；协调器折类 `:397-408`（ENGINE_CLOSED→UNAVAILABLE；[UNATTRIBUTED]→UNAVAILABLE；其余→FAILED） | 切换触发判据可直接消费 outcome.status+诊断前缀，无需新分类器 |
| policy 载荷互斥 | `FigureGenerationPolicy.kt:205-210`（JS 不得带 imagePrompt/imageOperation）、`:252-257`（IMAGE 不得带 program）；IMAGE 远程预算门 `:150-159`；`imageModelProposal` 工厂 `:74-91` | 切换后新 proposal 由 policy 重新全量校验——"切换受原目标/授权/质量/预算约束"已有静态门 |
| JS 提案的生产来源 | `TutorToolLoop.kt:428-447` `toFigureGenerationProposal()`（模型工具调用→proposal，两方法同型）；**main 代码暂无调用方**（grep 仅测试命中） | 切换编排落在工具环/入口适配层后，两方向 proposal 来源齐备；JS 生产接线未归位（X-01 裁定）是 V-02 的**前置依赖**，不是本设计缺口 |
| 显式重试位 | `DefaultFigureGenerationCoordinator.kt:349-353`：`explicitUserRetry` 只在用户显式重试时 true；"JS 熔断的恒等物是 program 摘要" | 机械切换**不是**用户重试：切换产生的 attempt 不得置该位（避免借道解除 JS 熔断） |

### 2.2 切换点：三个候选位置与裁定

| 候选 | 位置 | JS→IMAGE | IMAGE→JS | 裁定 |
|---|---|---|---|---|
| A. 协调器内部 | `generate()` 内一次失败后自动换法再试 | 可行 | **不可行**：program 只能来自模型（契约 §3 "模型不指定文件/URL"；协调器结构上无 program 来源，伪造即违约） | 否决：把"只对一半方向成立"的逻辑放进公共状态机，会让另一半变成假实现 |
| B. 调用侧编排（**推荐**） | 工具环执行 GENERATE_FIGURE 的调用方（core/data 入口适配层，如 `TutorFigureGenerator` 之后的统一 runner；JS 生产接线归位后即自然落点） | 一次 JS attempt FAILED（可归属）→ 同 operationId 合成 IMAGE proposal（载荷来自同一工具调用的 description/imagePrompt，`FigureGenerationPolicy.imageModelProposal` 现成工厂）再调一次 generate | 一次 IMAGE attempt"图像不佳"→ 本轮**不做机械回退**：把有界失败诊断写入 `TutorVisualContext`（契约 §7 已立：向下一次已有预算内模型回合携带"最近一份程序源码、当前 state、截断诊断与能力版本"），由模型在下一轮改选 JS+program，operationId 不变 | **采用**。理由：①两方向各自的载荷来源只有这里同时齐备；②协调器保持"一次调用=一次尝试"的既有语义与 A-10/A-01 全部既有用例零改动；③审计/账本/预算门零改动（§2.1 表）；④"图像不佳"是质量判断，本地无确定性判据，机械回退会变成"以换方法掩盖失败"（契约 §1 禁止） |
| C. 双向模型驱动 | 每次切换都回模型回合 | 可行但白烧一次 Provider 模型派发（JS→IMAGE 的载荷本地已有） | 可行 | 否决为默认路径：违反"最小实现"；JS→IMAGE 留作模型显式改选的合法形状之一 |

**最小改动面清单**（全部在 core/data，非热区；热区核对见 §6）：
1. 新增调用侧编排（一个小类/函数，如 `generateWithMethodFallback(request, primary, fallbackFactory)`），
   规则：primary FAILED 且诊断可归属（§2.4 判据表）且 `admitFinalFigure`/账本门未拒 → 用
   `imageModelProposal(...)` 构造 fallback（同 request=同 operationId）再调一次协调器；
   reason 字段登记切换来源（前一 attempt 有界诊断摘要）——契约 §8 "模型所选method/理由" 与 §7
   "不隐藏重试/审查请求" 的落点。
2. 协调器/policy/审计/账本/DAO：**零改动**（§2.1 每行已实证）。
3. 一个登记项：同 operationId 第二次 attempt 的 `auditOperation` 会给出**按新方法派生的
   basisFingerprint**（`DefaultFigureGenerationCoordinator.kt:764-778`：JS 用 program、IMAGE 用
   imagePrompt），但操作行 INSERT IGNORE 保留**首次**指纹——per-attempt 的 `input_fingerprint`
   才是载荷身份。实施时在编排类 KDoc 登记此口径，不改库。
4. 一个边界（如实登记）：两次调用之间操作行会经 `FAILED(终态) → markOperationRunning → 新终态`
   的短暂"复活"；进程恰死在中间 → 启动对账把 RUNNING 行按 `findStaleOperations` 判 FAILED
   （`FigureGenerationDao.kt:61-75`），attempt 行各自带真实终态——不产生假成功，但对账语义
   "操作终态=最后一次尝试"要进用例（§2.5 用例 7）。

### 2.3 attempt/预算语义（逐条对验收行）

- **同 operationId**：不新增任何 id 派生；编排类原样复用 request（§2.1 行 1）。
- **不新增确认**：切换发生在同一次工具调用执行内（JS→IMAGE）或同一模型回合链内（IMAGE→JS，
  经 TutorVisualContext）；无新 UI 确认、无新授权面——契约 §1 明文。
- **预算不重置**：
  - 远程：账本 per-operation 持久计数（6 上限），切换不换 id ⇒ 天然不重置；切换后 IMAGE 预留被拒
    ⇒ 终态 FAILED + 有界诊断（KD-60 口径真实计数），**不得**因"换过方法"绕行。
  - 本地：`FigureOperationLocalBudgets` 按 operationId 累计（JS 布局尝试 6 / 源码 128 KiB /
    墙钟 10 s），JS 超限后切换 IMAGE **语义上允许**——这些是 JS 专面预算，IMAGE 由账本门管；
    但"最终图 2 份跨方法合计"的门在方法分派前（`:217`），切换实现**必须保持它在前**，
    第 3 份成品拒一切方法。
  - `explicitUserRetry`：切换 attempt 不携带（§2.1 末行）。
- **attempt 行**：每次尝试一行；JS 行 `reservation_id=NULL`+负序号、IMAGE 行有预留序号；
  操作行终态/计数=最后一次尝试的事实；对账判据 `method='JS' AND reservation_id IS NULL`
  （`FigureGenerationReconcileDao.kt:134`/`:184`，"不可伪造判据"）在切换后不变——
  用例须覆盖"JS FAILED + IMAGE SUCCEEDED"终态不被对账改判。

### 2.4 切换触发判据（fail-closed 优先）

| 前一 attempt 结果 | 是否触发 JS→IMAGE 回退 | 依据 |
|---|---|---|
| `VisualExecutionResult.Rejected` 且可归属程序错（INVALID_PROGRAM 无 `[UNATTRIBUTED]` 前缀、硬期限等）→ 协调器 FAILED | **触发** | 程序错=该输入下 JS 兑现不了目标，IMAGE 是合法另解（契约 §1） |
| ENGINE_CLOSED / 无引擎 / 无帧 → UNAVAILABLE | **不触发**（如实回 UNAVAILABLE） | 设施类；IMAGE 走同一凭证门大概率同败，回退会把"本机没接通"掩盖成"方法不行"（A-09 口径：能力位说话） |
| `[UNATTRIBUTED]` → UNAVAILABLE | **不触发** | P-11：原因不可归属，不回喂 |
| policy REJECTED / 本地门 REJECTED（墙钟/布局/源码） | **不触发（该方向）**；但允许转 IMAGE 一次（JS 专面预算不迁移） | REJECTED 不是"JS 失败"而是"该操作超限"；IMAGE 另受账本门。注意与上行的区别：`admitFinalFigure` 拒绝时**两方向都拒**（门在分派前） |
| 账本 Denied（6 次用尽）→ FAILED | **不触发** | 预算事实，切换不得绕行（§2.3） |
| CancellationException | **不触发** | 取消路径（`:449-454` NonCancellable 收尾）如实落 CANCELLED |

"图像不佳 → JS"（IMAGE 方向）：本轮无本地确定性质量门 ⇒ 不做机械触发；登记为模型回合驱动
（TutorVisualContext 携带诊断）。若未来引入确定性门（如 `requiresCompleteLabels` 的本地核对失败），
须先回契约/policy 修订，不在本批（新增必须指认消灭的失败：当前"图像不佳"没有可执行判据，
凭感觉回退=把主观判断伪装成策略）。

### 2.5 用例设计（JVM 6 例 + 设备 1 组，与 A-10 测试同装配）

JVM（`core/data/src/test/.../visual/`，fake 引擎 + CountingLedger 同 `FigureGenerationLocalBudgetBoundaryTest.kt:281-325` 的替身形状）：
1. `jsFailureFallsBackToImageWithinTheSameOperationId`：JS attempt 真实进引擎失败（可归属）→
   编排层发起 IMAGE attempt：断言 `ledger.reserve` 恰 1 次、attemptOrdinal=1、操作行终态 SUCCEEDED、
   两行 attempt（JS 负序号 `reservation_id=NULL` + IMAGE 序号 1）、两次 outcome 的 operationId 相等。
2. `switchDoesNotResetTheRemoteBudget`：账本预置已用 5 次 → JS 失败 → IMAGE 预留被 6 上限拒 →
   终态 FAILED、used=5 不变、无第 7 次派发、诊断含真实计数。
3. `switchDoesNotResetLocalBudgets`：JS 累计墙钟恰达 10 s → 再一次 JS attempt REJECTED（不建行）→
   允许转 IMAGE（账本另管）；反向：`finalFigures=2` 时编排层在分派前即被拒（断言 fallback 未发起、
   `openBackend` 未被调）。
4. `facilityFailureDoesNotFallBack`：ENGINE_CLOSED 与 `[UNATTRIBUTED]` 两向量 → UNAVAILABLE、
   `ledger.reserve` 0 次、无 IMAGE attempt 行。
5. `noDoublePayloadAfterSwitch`：JS FAILED + IMAGE GENERATED → 消费链只挂一张
   `tutor_message_visual` link（(message_id, ordinal) 唯一）；终态 artifact 类型=ImageAsset，
   无 Program 残留。
6. `operationRowKeepsFirstBasisFingerprintAndFinalFact`：切换后操作行 `basis_fingerprint`=首尝试值、
   终态/计数=第二次尝试事实（§2.2 登记项 3/4 的回归钉）。
7. `reconcileKeepsJsFailedImageSucceededOperation`：操作含 JS(FAILED,local)+IMAGE(SUCCEEDED)
   → 跑 `FigureGenerationReconciler` → 不改判（对账判据回归）。

设备（app/androidTest，沿批次 37 V-01 app 版夹具与真隔离引擎装配）：
8. 同 operationId 双向切换断言组：JS 腿 0 Provider 派发 + IMAGE 腿恰 1 次出网（对齐 V-01 四断言位
   与清单 `:198` 的"同 operationId 换 method 计数不重置"位）；`admitFinalFigure` 前置门先跑。

---

## 三、V-03：保存题/删会话/GC/重建 checkpoint 语义边界与测试设计

> 清单行首词是"保存题"（基线转录一致）；本节按"保存题（含其引用/额度面）"理解。
> 若"保存额"另指保存数量配额（save quota），现树未见任何保存数量上限的实现或契约条款
> （grep `保存额/saveQuota` 于 core/data main = 0 命中，本会话实跑）——**该读法 UNVERIFIED**，
> 不据此设计。

### 3.1 语义边界（已落码半边逐条 + 缺口半边）

**已落码（本会话实读锚点）**：
- 引用分离：`tutor_message_visual.message_id` 对消息 **CASCADE**（link 随消息走），
  `program_id`/`current_snapshot_id` 对 program/snapshot **RESTRICT**（不许删被引用事实）——
  `TutorVisualEntities.kt:225-227` KDoc 明言"会话删除只带走 link，不带走 program/snapshot
  （与'删会话不删保存题引用'同一纪律）"。
- 保存题 pin：`pinRevisionVisual`/`insertRevisionVisualIfLive` 只认 LIVE 快照 + 同 program
  （`TutorVisualDao.kt:469-546`），幂等读回/换图冲突（`ImmutablePayloadConflictException`）。
- pin 计算：`checkpointSummaries` 的 `pinned` = 消息当前指针 `OR problem_revision_visual`
  （`TutorVisualDao.kt:118-140`）；淘汰 SQL 事务内**重查** pin/kind/status/租约
  （`:208-235`），INITIAL/LIVE/未知 kind/status fail-closed 保留（`VisualCheckpointRetention.kt:187-192`）。
- 删会话路径：`TutorConversationDao.deleteConversation`（`:586-596`，先删 tutor_message 再删会话行）
  → 触发 link CASCADE，program/snapshot 无级联。
- 重建恢复：`findLatestLiveSnapshot`（`TutorVisualDao.kt:264-275`，按 `promoted_at DESC`）→
  `VisualSnapshotRestore.restore` 六门判定（`VisualSnapshotRestore.kt:193-251`：非 LIVE/未知
  scene/render schema/renderer 不符/字体清单未验证/DrawOps 解析拒 → 有码只读降级）；
  "a staging snapshot is never replayed"（`VisualSnapshotRestoreTest.kt:241`）在树。
- 配额拒提交：4 MiB/32 MiB 事务前预检 + 事务内重判（`VisualCheckpointRetention.plan`
  `:202-214`、`RoomStudyDatabase` `enforceVisualCheckpointBudget` `:2108-2118`）。

**缺口半边（V-03 要补的测试）**：删会话→保存题图存活的端到端；pin 行为对淘汰 SQL 的**实删**
（纯策略测试只断言 plan 列表）；进程重建→最近 checkpoint→0 派发重放的观测闭环；
strictOffline 语义下的重放（X-03 面：`core/data/src/main` grep `strictOffline` 0 命中，基线 §五
一致——JS 持久程序重布局载体未立，本批 V-03 的 strictOffline 例只能落在"已持久快照重放"半边，
"重布局"半边归属 X-03 登记不混同）。

### 3.2 三组仪器化用例设计（设备，语义对应清单验收"三组仪器化"）

**组 1：删会话不删保存题图 / 固定引用不回收**
1. `deletingConversationKeepsSavedProblemVisual`：提交一assistant轮（消息+link+program+snapshot 全 LIVE）→
   `pinRevisionVisual` 到一个保存题 revision → `deleteTutorConversation` → 断言：
   `tutor_message_visual` 行没了（CASCADE）；`tutor_visual_program`/`tutor_visual_snapshot` 行在；
   `problem_revision_visual` 行在；`findLatestLiveSnapshot` 仍取到；restore 仍 Replay。
2. `pinnedSnapshotIsNeverEvictedEvenWhenStaleAndStaging`：pin 一张 LIVE 后，构造同 program 的新
   checkpoint 提交（plan 会 demote 旧 LATEST）→ 断言被 pin 行不进 `evictUnpinnedSnapshotIds`；
   直接对 DAO 跑 `evictUnpinnedCheckpoints`（候选含被 pin 行）→ 断言命中 0。
3. `evictionUnderBudgetPressureRejectsInsteadOfDeletingPinnedFacts`：全部快照被固定 + 注入
   `freeSpaceBytes=0`/预算=极小 → `commitAssistantTurnWithVisual` 抛
   `TutorVisualCommitRejectedException(PER_PROGRAM_BUDGET/GLOBAL_BUDGET/DISK_SPACE_INSUFFICIENT)`
   且**没有任何行被删**（行计数前后一致）——契约 §8.1 "不通过删用户事实腾空间" 的设备正验。

**组 2：进程重建读最近 checkpoint**
4. `processRebuildReplaysTheLatestLiveCheckpoint`：同 program 提交两次 checkpoint（INITIAL→LATEST，
   两次 commit 之间消息已 pin 旧 LATEST 与否两向量）→ 重建（新 repository 实例+同库）→
   `findLatestLiveSnapshot` 返回新 LATEST → restore Replay 且 `snapshotId`=新行。
5. `rebuildFallsBackToReadOnlyWithCodedReasons`：对四门各造一行（未知 scene schema / renderer 不符 /
   字体清单未验证 / 非 LIVE）→ restore 各回对应 `VisualRestoreFallbackReason` 码，
   `previewAssetId` 仅在 `previewVerified=true` 时暴露（沿 `VisualSnapshotRestoreTest` 既有断言形状，
   但走**端到端行→记录映射**（T3 读侧）而非手工构造 record——这是与既有 12 例的差异点）。

**组 3：strictOffline 可重放已持久程序且 0 派发**
6. `offlineReplayCausesZeroProviderDispatch`：飞行模式/`networkRequestsAllowed=false` 装配下
   走恢复→Replay→painter；断言位：`RemoteModelDispatchLedger.reserve` 0 次 + 图像通道工厂 0 次
   （观测口径沿 `FigureHttpTransportBypassObservationTest` 的 ledger 拒绝/通道计数替身）+
   操作行 `used_remote_dispatches` 不变。恢复路径结构上无出网点（restore 不 import 任何引擎类型，
   `VisualSnapshotRestore.kt:18-19` grep 门在树），本例把它钉成**观测证据**。
7. （登记不实现）"已持久程序**重布局**（重跑源码）"半边归 X-03/strictOffline 载体；本组不含。

---

## 四、V-04：同事务/清 staging/迟到结果的竞态面与断言设计

### 4.1 已落码面（本会话实读）

- **同事务**：`RoomStudyDatabase.commitTutorTurnWithVisual`（`:2030-2125`）单
  `withWriteTransaction`：appendAssistantMessage（正文+thinking+tool trace 同一条消息行，`:2039-2040`）
  → promote CAS → link（`INSERT … SELECT … WHERE status='LIVE' AND program_id=…`，`TutorVisualDao.kt:345-367`）
  → demote/evict → 事务内预算重判；任一步抛 → 全体回滚（KDoc `:2108-2110` "Room 回滚消息/link/program/snapshot"）。
- **失败/取消清 staging**：STAGING 租约三选一（无主/本 owner/租约过期，`TutorVisualDao.kt:208-235`）；
  `reconcileStaleStaging` 只删过期+无引用 STAGING（`:243-261`、`RoomStudyDatabase.kt:2138-2148`）；
  契约 §8.1 "失败、取消或进程租约失效时先持久化 owner 状态并对账……恢复中的操作不能因进程死亡被
  直接当无主"。
- **迟到结果不覆盖新 revision**：`repointMessageVisual` CAS（expectedRevision + LIVE + 同 program
  三条件，`TutorVisualDao.kt:397-419`）；`promoteSnapshot` 单向 CAS（STAGING→LIVE，无反向，
  `:294-309`）；`reassignStagingOwner` CAS（`:315-329`）；重复提交幂等读回、指向另一快照即冲突
  （`linkMessageVisual` `:423-458`）。

### 4.2 竞态面枚举与断言（每面：触达方式 → 断言）

| # | 竞态 | 触达方式（测试注入点） | 断言 |
|---|---|---|---|
| 1 | 事务中途失败 → 正文/link/图全体不成立 | 真库 + `freeSpaceBytes=0`（或预算极小）使事务内重判抛 | 消息行 0、link 行 0、snapshot 仍 STAGING（或不存在）、program 不残留；再以合法参数重试同一轮 → 全部成立（无半行残留） |
| 2 | 失败/取消遗留 STAGING → 启动对账回收 | `stageSnapshot(ownerRequestId=A)` 后不 commit；注入 `stagingLeaseMillis=极小` 或 `reconcileStaleStaging(now)` | 对账后 A 的 STAGING 行没了；同 owner 的 LIVE 行、别 owner 未过期 STAGING 行保留（租约三选一逐向量） |
| 3 | 活动 owner 的预备产物被并发 GC 误删 | B 提交的 `evictCheckpointSnapshotIds` 含 A 刚 stage 的行（A 未过期、A≠B） | 删除 SQL 命中 0（租约条件）；A 随后 `commitAssistantTurnWithVisual` 仍成功（A 手里 `(programId, snapshotId)` 未失效）——消灭 `DEFAULT_VISUAL_STAGING_LEASE_MILLIS` KDoc（`TutorVisualCommitRepository.kt:29-36`）登记的那个失败 |
| 4 | 迟到 checkpoint 覆盖新 revision | link revision=1 → `repointMessageVisual(expected=1, 新图)` 成功（revision→2）→ 再发迟到 `repointMessageVisual(expected=1, 旧图)` | 第二次返回 false；指针/revision 不变；旧图若未 LIVE 由 promote CAS 兜住（`promoteSnapshot` 命中 0 = 已非 STAGING） |
| 5 | 迟到结果指向另一 program 快照 | `repointMessageVisual` 传入他 program 的 LIVE 快照 | 命中 0（同 program 条件，`:405-410`）——批次 30 复核修复的结构门回归钉 |
| 6 | 并发双提交预算双过 | 两个协程并发 `commitAssistantTurnWithVisual`（不同 program、合计超全局预算） | 恰一个成功、一个抛 `TutorVisualCommitRejectedException(GLOBAL_BUDGET)`；事务内重判读的是提交后字节（`enforceVisualCheckpointBudget`，`:2108-2118`） |
| 7 | 重复提交同轮（幂等回放） | 同 `(message, program, snapshot)` 二次 commit | 幂等读回（`linkMessageVisual` `:430-438`），无第二行、无冲突；已 LIVE 快照不重跑保留策略（`planCheckpoint` LIVE 短路，`TutorVisualCommitRepository.kt:429-437`） |
| 8 | 提交路径静默换图 | 同 `(message_id, ordinal)` 指向另一张快照的 commit | `ImmutablePayloadConflictException`（换图只能走指针 CAS）——D-02 KDoc 失败语义回归钉 |
| 9 | 进程死亡在两次调用之间（§2.2 边界 4 的 V-04 面） | 操作行 RUNNING + attempt 各带真实终态时启动对账 | RUNNING 行判 FAILED 待核实（`findStaleOperations`）；"JS 成功不可伪造判据"（`method='JS' AND reservation_id IS NULL`）不被切换形状绕过 |

### 4.3 断言纪律（沿用共享纪律）

- 先红后绿：每个用例先在当前树跑（#2/#4/#6/#8 的载体在树，正确实现下应直接绿——它们是**回归钉**，
  不是新功能测试；若哪个红了即是对账/CAS 实现缺陷，先归因再修）；#1/#3 需要的注入点若不存在，
  先立 seam（`freeSpaceBytes`/`stagingLeaseMillis` 均已是构造参数，`TutorVisualCommitRepository.kt:187-192`，
  零新增注入面）。
- 断言不放宽：冲突一律断言**具体异常类型/返回值**（false / 命中 0 / 特定 reason 枚举），
  不用"没崩"代替。
- 设备面：清单验收类型为"仪器化（设备）"；#6 的真并发与 #9 的进程死亡语义只有设备/仪器化可钉，
  JVM 用 Room 真库可先行的（#1/#4/#5/#7/#8 载体纯 SQL）落 JVM，#2/#3/#6/#9 落设备轨。

---

## 五、外部参考评分（策略回退/任务重试语义）

> 检索通道：WebSearch + `gh api repos/...`（本会话实跑）；判别标准按共享纪律：license/维护度/与
> 当前栈兼容/可借鉴性，不唯 star。

| 参考 | 健康度（实证） | 语义要点 | 与本项目的映射 | 评分/采纳度 |
|---|---|---|---|---|
| [resilience4j](https://github.com/resilience4j/resilience4j) | Apache-2.0、10,798★、pushed 2026-10-30、未归档（`gh api` 实跑） | Retry 重执行**同一操作**；Fallback 在最终尝试失败后运行；Retry×Fallback 组合的豁免语义有 [issue #759](https://github.com/resilience4j/resilience4j/issues/759)/[#1318](https://github.com/resilience4j/resilience4j/issues/1318) 讨论 | 与 V-02 编排同构：**attempt 内重试≠换法回退**；回退在"最终尝试失败后"恰好一次。判据形状（按异常分类决定是否 fallback）= §2.4 判据表 | ★★★★☆ 借判据形状；**不引依赖**（Kotlin 协程挂起语义与其 VitrualThread 假设不合，且账本/本地预算已是更强门） |
| [Temporal activity retry](https://docs.temporal.io/activities)（官方） | 官方文档（WebFetch 实读） | attempt 失败按 Retry Policy 自动重试；**每次 attempt 从初始状态开始，除非用 Heartbeat detail 做 checkpoint**——"last recorded Heartbeat details are made available to the Activity function on the next attempt"；幂等是开发者责任 | 两个映射：①"attempt 序号独立于业务身份"（Activity ID 恒定、attempt 递增）= 本项目 operationId/attemptOrdinal 同构；②"重试续 checkpoint 靠最近一次心跳快照"= V-03"进程重建读最近 checkpoint"的同型（我们用 tutor_visual_snapshot 承担 Heartbeat detail 角色） | ★★★★☆ 借语义对照；引入 workflow 引擎属过度机制（消灭不了本项目任何现有失败） |
| [Transactional outbox](https://microservices.io/patterns/data/transactional-outbox.html)（microservices.io）+ [AWS 版](https://docs.aws.amazon.com/prescriptive-guidance/latest/cloud-design-patterns/transactional-outbox.html) | 模式权威页（多源一致） | 状态变更与消息落**同一本地事务**；中继后续发布，**至少一次**；对账/清理由中继+租约认领承担 | = V-04 同事务提交（消息+link+snapshot 一事务）+ 对账回收 STAGING（reconcile）的同型。差异：本项目是单库单写者，无"发布"半边，outbox 的中继/认领半边不需要 | ★★★☆☆ 已有实现的模式背书；无新增落点 |
| （对照项）failsafe-lib/failsafe | Apache-2.0、4,319★、pushed 2025-12-28（`gh api` 实跑） | Fallback/Retry/CircuitBreaker 组合策略 | 同 resilience4j；无额外增量 | ★★☆☆☆ 仅记录 |

**红线对照**：三个参考都**不**提供"切换不新增确认/预算不重置"的业务语义——那是本仓账本+本地预算
的结构属性；外部参考仅提供"回退判定按失败分类、恰一次、在最终尝试后"的判据形状（resilience4j）与
"重试续 checkpoint"的语义对照（Temporal）。

---

## 六、热区核对与未验证项

**本轨改动面（实施轨时）**：全部落 core/data（`visual/` 测试目录 + 入口适配层新编排类）与
app/androidTest（设备夹具沿 V-01 app 版）。核对：core/data 不在计划 §2 热区清单
（core/domain、settings.gradle.kts、core/export、core/ui、feature/*）；
`DefaultFigureGenerationCoordinator.kt`/`TutorFigureGenerator.kt`/`TutorVisualCommitRepository.kt`
等本会话未做任何写操作（只读）。core/data 有他线在飞 M（dense/knowledge/…面），实施轨开工时按
"目标文件变脏则整文件纳入并注明"纪律先查 `git status --porcelain -- <目标文件>`。
**本轨实际产出仅本文件**（docs/research 未跟踪目录，属本轨记录位）。

**未验证/边界（逐条如实）**：
1. 本会话未运行任何 Gradle/JVM 测试——§2.5/§3.2/§4.3 全部用例是设计，未实跑，红绿状态 UNVERIFIED。
2. "保存额"若非"保存题"的别读法（保存数量配额）：现树与契约均未见该面，UNVERIFIED；未据此设计。
3. IMAGE→JS 的"图像不佳"触发：本地无确定性质量判据（§2.4），登记为模型回合驱动；是否存在
   可先行的本地核对门（requiresCompleteLabels 等）属 policy/契约修订，未验证可行性。
4. `TutorVisualContext`（契约 §7）的载体在树的落码状态未逐行核（本会话只读其契约条款与
   A-11/A-12 行注记）；切换的 IMAGE→JS 半边依赖它，实施前须先核载体。
5. JS proposal 生产接线（工具环→协调器的 runner）main 代码暂无调用方（grep 实证，X-01 归位裁定
   待主会话）——V-02 双向用例的**设备半边**前置依赖该归位；JVM 半边可用 seam 先行。
6. 操作行"短暂复活"（FAILED→RUNNING→新终态）与对账交互（§2.2 边界 4）只做了代码路径推演，
   未有实跑证据；用例 7（§2.5）落地时先验证。
7. 外部参考的版本兼容（resilience4j 对 Kotlin 2.3.21/coroutines 1.11.0）：未验证——但本轨结论是
   不引依赖，该验证不阻塞。

## 七、来源

- 仓内（实读）：计划 `.zcode/plans/plan-2026-11-01-batch-38-triple.md`；清单
  `docs/research/2026-10-26-batch-24-4e2-admission-checklist.md:220-223`；契约
  `docs/current/visual-generation-contract.md`（§1/§7/§8/§8.1）；基线
  `docs/research/2026-11-01-batch-38-baseline.md`；
  `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/`
  （`DefaultFigureGenerationCoordinator.kt`、`FigureOperationLocalBudgets.kt`、
  `TutorVisualCommitRepository.kt`、`VisualCheckpointRetention.kt`、`VisualSnapshotRestore.kt`、
  `RoomFigureDispatchLedger.kt`、`VisualJsFailureClassification.kt`）；
  `core/data/.../model/TutorFigureGenerator.kt`；`core/model/.../FigureGenerationPolicy.kt`、
  `ModelTasks.kt`、`TutorToolLoop.kt`；`core/database/.../dao/FigureGenerationDao.kt`、
  `FigureGenerationReconcileDao.kt`、`TutorVisualDao.kt`、`TutorConversationDao.kt`、
  `entity/TutorVisualEntities.kt`、`RoomStudyDatabase.kt`；
  测试目录 `core/data/src/test/.../visual/`（18 文件 ls 实证）。
- 外部（检索/实跑）：
  - https://github.com/resilience4j/resilience4j （gh api 实证：Apache-2.0/10798★/pushed 2026-10-30）
  - https://github.com/resilience4j/resilience4j/issues/759 、 https://github.com/resilience4j/resilience4j/issues/1318 （Retry×Fallback 语义）
  - https://docs.temporal.io/activities （WebFetch 实读：retry/attempt/checkpoint 语义）
  - https://microservices.io/patterns/data/transactional-outbox.html 、
    https://docs.aws.amazon.com/prescriptive-guidance/latest/cloud-design-patterns/transactional-outbox.html （同事务+对账模式）
  - https://github.com/failsafe-lib/failsafe （gh api 实证：Apache-2.0/4319★/pushed 2025-12-28；对照项）
