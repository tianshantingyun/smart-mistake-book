# checkpoint 预算口径会计：stage / commit 的"净值 vs 全量"

- 日期：2026-10-29（批次 33，驱动 T3；计划 `.zcode/plans/plan-2026-10-29-batch-33-structure-gates-kd80.md` §1 任务 3）
- 裁定对象：契约 §8.1 的"超限只淘汰中间态，淘汰后仍超才拒"（`docs/current/visual-generation-contract.md:186-190`）
- 证据口径：本会话只做**只读源码 + `gh api` + 官方文档**；**零 Gradle、零设备、零 git 写**。下文所有"当前行为"均为源码实读，不是执行结果（执行判据见 §7）。
- 代码基线：HEAD `6abd807f`（工作区干净的相关文件：`TutorVisualCommitRepository.kt`、`RoomStudyDatabase.kt`、`TutorVisualDao.kt`、`TutorVisualCommit.kt`；`git status --porcelain -- <these>` 本会话实跑为空）。

## 0. 结论先行

1. **现树两路径已经是同一口径**：事务前 plan 按"淘汰后净值"预检，事务内**先执行 demote/evict、再按实测总量重判**。stage 已把 plan 的淘汰集带进事务（`TutorVisualCommitRepository.kt:276-283`）并在事务内执行（`RoomStudyDatabase.kt:1959-1971`）；commit 同样（`:329-330`、`:2056-2071`）。修复来自 `3087c8ae`；对应的仪器化用例第 9 例 `stagingIntoAnOverBudgetProgramEvictsAnExpiredUnpinnedStagingIntermediate`（`TutorVisualCommitRepositoryInstrumentedTest.kt:423-487`）已提交（`9d91e6b0`），但本会话未执行（§7）。
2. **计划 §0 的"复核遗留"对现树已不成立**，且对上一版也不完全准确：`3087c8ae^` 的 stage 事务**既没有执行淘汰、也没有预算重判**（`git show 3087c8ae^:.../RoomStudyDatabase.kt` 中 `enforceVisualCheckpointBudget` 零命中），不是"事务按全量"，而是"stage 没有第二道门"。这一点必须在批次 33 的台账回改里如实写，不能照抄计划措辞。
3. **"净值"单独不是安全门**：plan 扣除的 `E` 是乐观集合，事务内 SQL 会再查 pin/kind/status/owner 租约，实际删除集 `E' ⊆ E`。当 `E' ⊊ E` 时，净值不超也可能落成实际超限；所以**事务内"淘汰后再量"不是可选项，是正确性的一环**。
4. **"全量"两种读法都必须改语义**：不先淘汰就按全量拒 → 直接违反 §8.1 的"超限只淘汰中间态"；先淘汰再按全量 → 等价于现口径，但多一个非原子 GC 步骤（被拒的提交仍留下已提交的删除副作用），不推荐。
5. **真正的剩余问题不在口径，而在三处**（见 §5）：
   - plan 的两次读不原子，可在并发删除窗口抛**无界** `IllegalArgumentException`（`VisualCheckpointRetention.kt:183-185`）；
   - `reconcileStaleStaging` **没有生产调用点**（全仓 grep 仅定义与测试），孤儿 STAGING 只能等下一次同 program 提交顺手淘汰；跨 program 的孤儿可把全局 32 MiB 永久卡死；
   - 首个 stage 留下了 `kind=INITIAL` 的 STAGING 行：它既不被 evict SQL 删（kind 过滤），也不被备份裁剪删（INITIAL 保留），reconcile 又没接线 → **永久占预算，并让该 program 永远补不出可见的 INITIAL 基线**。
6. **当前口径对 §8.1 的符合度：符合**（逐条见 §4.3）。唯一需要裁定的解释是"受保护字节是否计入预算"——现行计入，且有 §8.1"全部固定…则本次不提交"作为支撑，但原文"可回收派生缓存≤4 MiB"也可读成只计可回收部分；建议在契约里补一句定谳，而不是在代码里悄悄改口径。

## 1. 记号与集合（全部落到现有代码）

| 记号 | 含义 | 代码依据 |
|---|---|---|
| `P_p` | 某 program `p` 的全部快照行 | `TutorVisualDao.checkpointSummaries`：`WHERE program_id = :programId`（`TutorVisualDao.kt:118-140`） |
| `G` | 全库快照行 | `totalCheckpointStoredBytes`（`TutorVisualDao.kt:142-152`） |
| `b(r)` | 一行派生字节 = UTF-8(state)+UTF-8(scene)+UTF-8(draw) | SQL `length(CAST(x AS BLOB))`（`TutorVisualDao.kt:132-134`）；Kotlin 同口径（`VisualCheckpointRetention.kt:238-245`） |
| `x` | 本次 incoming 快照的 `b` | `visualCheckpointStoredBytes(...)`（`TutorVisualCommitRepository.kt:438-442`） |
| `pinned(r)` | 被 `tutor_message_visual.current_snapshot_id` 或 `problem_revision_visual.snapshot_id` 引用 | `TutorVisualDao.kt:123-131` |
| `E_p` | plan 认为可淘汰：`status=STAGING ∧ ¬pinned ∧ kind∈{NULL,LATEST}` | `VisualCheckpointRetention.kt:187-192` |
| `E'_p` | 事务内 SQL 实际删掉的行，`E' ⊆ E` | `TutorVisualDao.kt:208-235`（再查 kind/status/pin/**owner 租约**） |
| `B_p`, `B_g` | 4 MiB / 32 MiB | `TutorVisualCommit.kt:35-38` |
| 边界口径 | 等于预算允许（不是"小于才允许"） | Kotlin 用 `>` 判超（`VisualCheckpointRetention.kt:202,210`），SQL 用 `>`（`RoomStudyDatabase.kt:2182,2190`），两侧一致 |

四个量：

```
net_p      = b(P_p) − b(E_p) + x                 // plan 的"淘汰后净值"
net_g      = b(G)   − b(E_p) + x                 // 只扣本 program 的淘汰量
gross_p    = b(P_p) + x                          // "全量"
actual_p   = b(P_p) − b(E'_p) + x                // 事务内"实测净值"（= 淘汰后全量）
actual_g   = b(G)   − b(E'_p) + x
```

> 关键：`net_p` 用的是**计划**集合 `E_p`；`actual_p` 用的是**实际**集合 `E'_p`。二者只有在"SQL 一条候选都没拒绝"时才相等。租约（`owner_request_id IS NULL / = 本次 owner / created_at ≤ leaseDeadline`）是造成 `E' ⊊ E` 的常态来源，不是异常。

## 2. 两条路径的时序（当前 HEAD）

### 2.1 stage（S3，不可见预备）

```
[进程内 Mutex]                                  // 只串行同一 repository 实例，不是正确性依据
S0  requireS0(...)                              // 源码/state/事务 JSON/磁盘
plan  planCheckpoint(p, snap)  → net 预检
      读 P_p 汇总 + 读 G 总量（两次独立查询）
      计算 E_p、demote 集（stage 丢弃 demote）
[写事务 begin IMMEDIATE]
      ensureTutorVisualProgram                   // INSERT OR IGNORE + 回读比对
      ensureTutorVisualSnapshot                  // INSERT OR IGNORE / 转租约 / 回读比对
      evictUnpinnedCheckpoints(E_p, lease)       // 实际 E'_p
      if 行不是 LIVE: enforceVisualCheckpointBudget(actual_p, actual_g)
[commit / 任一 throw → 整体 ROLLBACK]
```

依据：`TutorVisualCommitRepository.kt:257-286`；`RoomStudyDatabase.kt:1953-1984`；计划值 `VisualCheckpointRetention.plan`（`VisualCheckpointRetention.kt:165-224`）。

### 2.2 commit（S4，首张可见图）

```
[同一把 Mutex]
S0 + plan（同 stage，但保留 demote 集）
[写事务 begin IMMEDIATE]
      ensure program + snapshot
      snapshotWasAlreadyLive = (status == LIVE)  // 在提升前取
      appendAssistantMessage
      promoteSnapshot STAGING→LIVE
      linkMessageVisual（INSERT…SELECT WHERE LIVE，同事务）
      demoteLatestCheckpoints(old LATEST → NULL)
      evictUnpinnedCheckpoints(E_p, lease)       // 实际 E'_p
      if !snapshotWasAlreadyLive: enforceVisualCheckpointBudget(...)
[commit / 任一 throw → 整体 ROLLBACK（消息/link/program/snapshot/demote/evict 全回滚）]
```

依据：`TutorVisualCommitRepository.kt:298-339`；`RoomStudyDatabase.kt:1995-2090`；`enforceVisualCheckpointBudget` 在 `:2175-2197`。

### 2.3 两路径的差异（预算相关 / 非预算相关）

| 维度 | stage | commit | 是否影响预算 |
|---|---|---|---|
| plan 读取集合 | `P_p \ {本次行}`，`G` 减本次行字节 | 同 | 否（同式） |
| 事务内写入 | program + snapshot(STAGING) | + 消息 + promote(LIVE) + link | 否（`b` 只含三列 JSON） |
| demote | **不做**（只算不用） | 做（旧 LATEST→NULL） | **否**：`kind` 不进 `b`；NULL 与 LATEST 都在 `E` 允许集内 |
| evict | 做 | 做 | 是（`E'`） |
| 事务内重判 | `status != LIVE` 时 | `!snapshotWasAlreadyLive` 时 | 等价：都只跳过"本来就是 LIVE 的幂等回放" |
| 回滚范围 | program/snapshot/evict | 消息/link/program/snapshot/demote/evict | 预算相关部分同 |

**结论**：在"预算记账"这个投影上，stage 与 commit **没有集合或时机差异**；唯一结构性差异（demote 与 LIVE 提升）都不改变 `b`，也不改变 `E` 的成员资格（旧 LATEST 降为 NULL 后仍可淘汰）。所以"stage 用净值、commit 用全量"这种二分在现树不成立。

## 3. 四种组合的行为差异（形式化）

设 plan 的判定函数与事务的判定函数如下（两侧都要同时过 per-program 与 global；先 per-program，后 global，`RoomStudyDatabase.kt:2181-2196`）：

| 变体 | plan 判定 | 事务判定 | 放行时的界 | 失败方向 | 对 §8.1 |
|---|---|---|---|---|---|
| **A（现树）** | `net_p ≤ B_p ∧ net_g ≤ B_g` | 先执行 `E'`，再 `actual_p ≤ B_p ∧ actual_g ≤ B_g` | `actual` | 安全；`E'⊊E` 且超界时**有界拒绝**（liveness 损失，方向正确） | **符合** |
| **B（净值 plan + 全量事务，不先淘汰）** | `net ≤ B` | `gross ≤ B` | `gross` | 安全；`b(E)>0` 且 `gross>B≥net` 时**误拒**——本可淘汰后提交 | **不符合**（拒之前没有淘汰） |
| **C（全量 plan）** | `gross ≤ B` | 任意（plan 已拒） | 不会到达 | 只要 `b(E)>0` 且 `gross>B≥net` 就误拒，且**根本没尝试淘汰** | **不符合**（"超限只淘汰中间态"落空） |
| **D（净值 plan + 事务不重判）** | `net ≤ B` | 无 | `actual` | **不安全**：`E'⊊E` 时落成 `actual>B`；并发双过同样 | **不符合**（淘汰后仍超也提交了） |

把同一张表投影到 stage/commit 两条路径（这就是"两路径的行为差异"的完整答案）：

| 口径组合 | stage 路径 | commit 路径 |
|---|---|---|
| plan 净值 + 事务"淘汰后实测"（现树） | `E'` 在事务内删，再量；`E'⊊E` 且超界 → `PER_PROGRAM_BUDGET`/`GLOBAL_BUDGET`，行与淘汰全回滚 | 同一判定；额外把旧 LATEST demote（不影响 `b`）；回滚范围更大（含消息/link） |
| plan 净值 + 事务"淘汰前全量"（B） | `gross>B` 即拒，即使 `E'` 本可释放空间 → 与 §8.1 方向相反 | 同 stage；且会把"本可提交的首张可见图"整轮拒掉 |
| plan 全量（C） | plan 阶段就拒，事务不发生；`E` 永远不被尝试 | 同 stage；首张可见图在写消息前就失败 |
| plan 净值 + 事务不重判（D） | 落成 `actual>B`；`E'=∅` 时等于按全量放行 | 同 stage；并发双过同样无门 |

**一句话**：两条路径在"预算记账"上没有自己的口径，它们共用同一对 plan/事务判定；真正的差异只在"是否 demote / 是否提升 LIVE / 回滚里有没有消息与 link"，而这三者都不改变预算的集合或数值。

### 3.1 反例（说明 D/B 都不是等价的"口径选择"）

取 `B_p = 4 MiB`，program 内有：

- `r1`：INITIAL、LIVE、3 MiB（受保护）；
- `r2`：LATEST、STAGING、3 MiB、`owner_request_id` 是**另一个仍在飞的请求**、未过 60 min 租约；
- incoming `x = 1 MiB`。

则 `E_p = {r2}`（plan 看不到租约），`net_p = 3 + 1 = 4 ≤ B_p`，`gross_p = 7 > B_p`。事务里 SQL 因租约拒绝删 `r2`，`E'_p = ∅`，`actual_p = 7 > B_p`：

- **A**：plan 放行，事务拒绝 → 有界 `PER_PROGRAM_BUDGET`，DB 零新增行。方向正确（不能删别人在飞的预备行）。
- **B**：在本例中 plan 放行、事务按全量拒绝，结果与 A 相同，但拒绝理由是"全量超"，不是"淘汰后仍超"；把 `r2` 的租约换成**已过期**后差别才暴露——A 会删 `r2` 并提交（`net = 4 ≤ B`），B 仍按 `gross = 7 > B` 拒绝 → **误拒一个本可提交的请求**。
- **C**：plan 直接拒绝，连"租约过期就能删"的情形也不尝试淘汰 → 误拒面更大。
- **D**：plan 放行、事务不重判 → `actual = 7 > B_p` 落库，**预算门失效**。

这个反例同时说明：**"净值"不是口径，而是"乐观预检"；正确性必须由"实际淘汰后重判"提供**。所谓"两处都用净值"应精确表述为"plan 用净值预检 + 事务用实测净值判决"，而不是"两层都相信同一个净值"。

### 3.2 `E' ⊊ E` 的三个来源

1. **租约**：别人刚 stage、未过 60 min 的行（`TutorVisualDao.kt:214-218`）；
2. **pin/status 并发变化**：plan 之后被消息/题面引用，或已被提升为 LIVE（`TutorVisualDao.kt:212-226`）；
3. **kind 变化**：plan 后被 demote 成 NULL 不影响（仍在允许集），但被改成 INITIAL 则会被排除（当前无此路径）。

只要来源 1/2 存在，"计划净值"就不是可达状态；**必须**保留事务内重判。

## 4. 统一方案

### 4.1 方案 A（推荐）：两处都按"净值预检 + 实测淘汰后判决"

不是"stage 也要 demote"；**demote 与预算无关**。需要成立的是四条：

- **A-1 同一份 plan 消费方式**：stage 与 commit 都把 `plan.evictUnpinnedSnapshotIds` 传进事务（现树已满足：`TutorVisualCommitRepository.kt:279`、`:330`；`StageTutorVisualSnapshotCommand.evictCheckpointSnapshotIds` 在 `TutorVisualCommit.kt:186-198`）。
- **A-2 事务内先淘汰、后量**：现树已满足（`RoomStudyDatabase.kt:1963-1982`、`:2056-2083`）。
- **A-3 量的是全量（含受保护行）**：现树已满足（`programCheckpointStoredBytes` / `totalCheckpointStoredBytes`）。
- **A-4 plan 只是快速预检**：不得把 `net ≤ B` 当最终结论（KDoc 已写明：`VisualCheckpointRetention.kt:137-140`）。

stage **不需要** demote：新行还是 STAGING，若把旧 LATEST 提前摘掉而在提交前被放弃，program 会进入"没有 LATEST 标记"的角色空洞；且 demote 对 `b` 零影响。现树的选择（stage 不带 demote）是正确的，不应为了"两处完全一样"而加。

### 4.2 方案 B（不推荐）：两处都按"全量"

"全量"要成为**可提交**的口径，必须先淘汰再判定；于是有两种落法：

- **B1 先 GC 再 plan（独立事务）**：GC 必须幂等、崩溃可续、跨进程单飞（参考 §6 的 Bazel 排他锁 / restic 两段式）。plan 读到的"全量"= GC 后的剩余，等于净值的另一种写法；事务内仍要再判一次（否则并发窗口不安全）。
  - 代价：被拒绝的"本次提交"已经留下了**已提交的删除副作用**（缓存被清、提交没成）；stage/commit 的事务原子性覆盖不到这次 GC。
  - 对 §8.1：方向符合，但"本次不提交"不再意味着"无副作用"（虽然删的是可回收缓存，不是用户事实）。
- **B2 只把 plan 改成全量、不预淘汰**：直接违反"超限只淘汰中间态"，属于 C 变体，**不可采纳**。

### 4.3 对 §8.1 的逐条符合度

契约原文（`docs/current/visual-generation-contract.md:190`、`:192`）：

| §8.1 子句 | A（现树） | B1 | B2/C | 依据 |
|---|---|---|---|---|
| 超限只淘汰"未被固定、kind∈{NULL,LATEST}、仍为 STAGING"的中间态 | 符合 | 符合（GC 同一谓词） | **不符合**（不淘汰） | `VisualCheckpointRetention.kt:187-192`；`TutorVisualDao.kt:210-227` |
| 淘汰后仍超才拒 | 符合（实测淘汰后量） | 符合 | **不符合**（未淘汰就拒） | `RoomStudyDatabase.kt:2073-2083` |
| LIVE 永不淘汰 | 符合 | 符合 | 符合 | SQL `status = :stagingStatus`（`TutorVisualDao.kt:213`） |
| 全部固定或磁盘不足则本次不提交并说明 | 符合（有界异常） | 符合 | 符合 | `TutorVisualCommitRejectedException`（`TutorVisualCommitRepository.kt:154-158`）；磁盘 S0 `:386-395`；`SQLiteFullException` 翻译 `:544-564` |
| 不通过删用户事实腾空间 | 符合 | 符合 | 符合 | `evictUnpinnedCheckpoints` 的 `NOT EXISTS` pin 双门（`:219-226`） |
| 活动 owner 的预备产物不得被 GC | 符合（租约 + 事务内重查） | 符合 | 符合 | `TutorVisualDao.kt:214-218`；契约 `:192` |
| 首张可见图与消息/trace/link 同事务 | 符合 | 符合 | 符合 | `RoomStudyDatabase.kt:1995-2090` |

**待裁定的解释**：预算分母是否包含受保护行（pinned / LIVE / INITIAL）。现行计入（`retained = b(P) − b(E) + x`），因此"全部固定"会拒——这正是 §8.1 末句"全部固定…则本次不提交"能成立的前提。若改成"只计可回收部分"，末句将永远不触发（`retained` 恒 ≤ `b(E)`，除非 incoming 本身超 4 MiB）。**建议维持现行**，并在契约里补一句"受保护行计入分母、但永不作为淘汰候选"，消除两种读法。

### 4.4 给 T3 的最小动作清单（按"消灭哪个失败"排序）

1. **接线 reconcile**（消灭：孤儿 STAGING 永久占预算 + `INITIAL` STAGING 永不可淘汰 + 全局预算被休眠 program 卡死）。`reconcileStaleStaging` 当前零生产调用点；`deleteStaleStagingSnapshots` 只看 status/age/引用（`TutorVisualDao.kt:243-261`），能删 STAGING INITIAL，但没人调用。阈值必须与 `DEFAULT_VISUAL_STAGING_LEASE_MILLIS`（60 min，`TutorVisualCommitRepository.kt:38`）同源或更宽，否则会把仍在飞的 stage 当孤儿删掉。
2. **把 plan 的两次读变成一次原子读**（消灭：`require(globalStoredBytes >= existingBytes)` 的**无界** `IllegalArgumentException`）。现读法：先 `readVisualCheckpointSummaries(programId)`，再 `readGlobalVisualCheckpointStoredBytes()`（`TutorVisualCommitRepository.kt:420,445`），中间可被别的 evict/reconcile 删行；`VisualCheckpointRetention.kt:183-185` 会抛 `IllegalArgumentException`，把并发窗口变成调用方无法处理的"参数错误"，破坏"预检失败=有界拒绝"的对外契约。落法：一个 DAO 查询同时返回本 program 汇总与全局总量，或把两次读包进同一读事务；无法保证时把该 `require` 映射成有界拒绝/重试。
3. **补 stage 的负向用例**（消灭："淘汰后仍超"在 stage 路径无设备证据）：在全新 program 上让租约内候选造成 `E'⊊E`，断言 `PER_PROGRAM_BUDGET` 且 `countSnapshots == 0`、`countPrograms == 0`。现有第 9 例只覆盖正向（`TutorVisualCommitRepositoryInstrumentedTest.kt:423-487`）。
4. **补一对"净 vs 全量"分辨例**（消灭：口径回归无人发现）：固定 `E≠∅`、`gross>B≥net`，断言 A 变体成功、且若把事务重判挪到淘汰之前则失败。这类用例能把"先淘汰后量"的顺序钉死，而不是只钉结果。
5. **写清 stage 独立事务的 GC 副作用**（消灭：把"本次不提交"误读成"零副作用"）：stage 已提交的 evict 不会因后续 commit 被拒而回滚；只能删缓存，不能删用户事实，但文档必须说明。
6. **评估全局候选选择**（消灭：跨 program 的全局卡死）：`plan` 只读 incoming program 的行（`TutorVisualCommitRepository.kt:420`），`globalAfter` 只扣本 program 的 `E`（`VisualCheckpointRetention.kt:209`）。若 §8.1 的"全局≤32 MiB"要求"超限先淘汰中间态"，则需要在全局轴上有跨 program 的候选选择（仍受租约/pin 约束）；否则应在契约里明确"全局轴只允许本地淘汰，跨 program 的孤儿由 reconcile 处理"。

> 第 1、2 项是**真实缺陷修复**；第 3、4 项是**证据补强**；第 5、6 项是**语义登记/裁定**。不要把它们混成"为了更完备"的加戏——每一项都对应一个具体失败（见括号）。

## 5. 并发与崩溃点语义

### 5.1 事务内重读为什么是权威的

- Room3 的 `withWriteTransaction` 明确是 `useWriterConnection { it.withTransaction(IMMEDIATE) }`（`androidx/androidx` 的 `room3/room3-runtime/src/commonMain/kotlin/androidx/room3/RoomDatabase.kt:691-701`，本会话 `gh api` 实读）。
- SQLite 文档对 `BEGIN IMMEDIATE` 的定义：立即开始写事务，不等第一条写语句；若另一连接已有写事务，会返回 `SQLITE_BUSY`（`https://www.sqlite.org/lang_transaction.html`，本会话 WebFetch 实读）。
- 因此：同一时刻只有一个写事务；后开始者要么在拿到写锁后看到前者已提交的行，要么在锁竞争处失败。事务内 `programCheckpointStoredBytes` / `totalCheckpointStoredBytes`（`TutorVisualDao.kt:142-171`）读到的是**本事务自己的写入 + 已提交的世界**，所以"淘汰后实测"是可信的最终判决。
- 进程内 `checkpointPlanLock`（`TutorVisualCommitRepository.kt:219`）只把**同一 repository 实例**的 plan+事务串起来，减少无谓回滚；它不覆盖第二个实例/第二个进程，KDoc 已如实标注（`:207-218`）。删掉它只会多一次事务内拒绝，不会造成超额写入。

### 5.2 计划层（事务外）的读不是原子的

- `planCheckpoint` 先读本 program 汇总，再读全局总量，**两次独立查询**（`TutorVisualCommitRepository.kt:420,445`），中间没有读事务。
- 可发生的坏结果：两次读之间，另一个提交/reconcile 删掉了本 program 的行 → 全局总量 < 先前汇总的和 → `VisualCheckpointRetention.kt:183-185` 的 `require` 抛 `IllegalArgumentException`。这不是有界拒绝，而是"预检契约之外"的异常类型。
- 反向（并发新增）不会触发该 `require`，只会让预检更保守/更宽松，均由事务内重判兜底。
- 触发条件：至少两个 repository 实例、绕过 repository 的直接端口写者、或 reconcile 与写并发（reconcile 当前未接线，接线后即成立）。**这是 §5 中最容易被"看起来没问题"掩盖的并发缺陷。**

### 5.3 崩溃点

| 时点 | 已持久化 | 预算后果 | 恢复 | 证据 |
|---|---|---|---|---|
| plan 前/中 | 无 | 无 | 无 | 只读 |
| stage 事务内崩溃 | 无（事务回滚） | 无 | 无 | `withWriteTransaction` 原子性 |
| stage 提交后、commit 前 | STAGING 行 + 已提交的 evict + program | 孤儿 STAGING 占预算；`E'` 已生效 | 依赖 reconcile（**未接线**）或同 program 的下一次 commit（且租约已过） | §5.4 |
| commit 事务内崩溃 | 无（消息/link/promote/demote/evict 全回滚） | 无 | 无 | `RoomStudyDatabase.kt:1995-2090`；`withWriteTransaction` 原子性 |
| commit 提交后 | 消息/link/LIVE/evict 全部生效 | 最终 `actual ≤ B`（若不满足则事务已回滚） | 无需恢复 | 同上 |
| 备份/恢复 | 视觉四表按行裁剪/保留 | 恢复后可能已接近/超过预算；下一次写入会先淘汰再判 | 无专门恢复后预算检查（未观察到） | `SmbkSelectionPlan.kt:501-509` |

### 5.4 孤儿 STAGING 与 `INITIAL` 陷阱（当前最重的崩溃语义问题）

1. `stageSnapshot` 在插入时就把 `checkpointPlan.incomingKind` 写进行（`TutorVisualCommitRepository.kt:267-269`；`TutorVisualSnapshotWrite.checkpointKind`）。若这是 program 的首个 stage，kind = `INITIAL`（`VisualCheckpointRetention.kt:151-156`）。
2. 该 stage 被放弃时，行留下：`status=STAGING`、`kind=INITIAL`。
3. `evictUnpinnedCheckpoints` 的 `WHERE` 只允许 `kind IS NULL OR kind = LATEST`（`TutorVisualDao.kt:212`）→ **INITIAL 永远删不掉**；`plan` 的 `evictable` 同样排除 INITIAL（`VisualCheckpointRetention.kt:191`）。
4. `reconcileStaleStaging` 能删（不带 kind 条件，`TutorVisualDao.kt:243-261`），但**全仓没有生产调用点**（本会话 `grep -rn "reconcileStaleTutorVisualStaging"` 仅命中定义与测试）。
5. 备份裁剪也保留 INITIAL（`BackupSnapshotTrimmer.kt:745-777` 的保留 SQL：`checkpoint_kind='INITIAL'` 无条件保留，不看 status），不会顺手清掉。
6. 后果一：这行永久占 4 MiB/32 MiB 分母；后果二：后续 commit 的 `nextCheckpointKind` 看到"已有 INITIAL"，于是把真正首个可见 checkpoint 定为 `LATEST` → **该 program 永远没有可见的 INITIAL 基线**（契约"初始+最新两份"落空）。
7. 同型但较轻的：孤儿 STAGING `LATEST` 只能在**同一 program**的下一次提交时被顺手淘汰（`E` 只来自 incoming program），且要等 60 min 租约；休眠 program 的孤儿会让全局 32 MiB **永久**逼近/卡死，任何 program 的提交都无权删别的 program 的行（`VisualCheckpointRetention.kt:209` 的 `globalAfter` 只扣本 program 的 `evictableBytes`）。

**结论**：§8.1 的"超限只淘汰中间态"在**全局轴**上目前只实现了"本地淘汰 + 拒绝"，没有"跨 program 回收"；契约要么补一条"跨 program 孤儿由启动对账处理"，要么要求全局淘汰候选跨 program 选取。这是一条需要主会话裁定的口径，不应由实现自行决定。

### 5.5 其他并发/错误面

- **`SQLiteFullException`** 已被翻译成有界拒绝（`TutorVisualCommitRepository.kt:544-553`）；`TutorVisualCheckpointBudgetExceededException` 同样（`:553-564`）。
- **`SQLITE_BUSY`** 没有专门翻译：单进程单 writer connection 下不应出现；若出现第二进程/第二实例竞争，会以 `android.database.sqlite.SQLiteException` 形态穿透，而不是有界拒绝。生产是否只允许一个 DB 实例，本会话未验证（§7）。
- **性能**：`totalCheckpointStoredBytes()` 是对全表的 `SUM(length(CAST(...)))`，每次写事务都在持写锁期间跑一遍；32 MiB 量级（数千行）未必是问题，但**未测量**。若将来放宽预算或行数上升，需要改成增量记账或加缓存（并处理回滚）。
- **journal mode**：仓内未见显式设置；备份路径按 WAL 处理（`RoomBackupSupportStore.kt:23` 的 `PRAGMA wal_checkpoint(TRUNCATE)`）。本节的锁论证只依赖 IMMEDIATE 语义，不依赖 WAL/rollback journal 的具体选择。

## 6. GitHub 参考（配额/GC 预算实现）

通道：`gh api`（本会话实跑，2026-10-29）。license/stars/last push 为实测元数据；"借哪一点/不借哪一点"是读源码后的判断；评分为主观（license / 维护度 / 可复用，1–5）。

| 仓 | license（实测） | stars | last push | 读到的实现 | 对本仓的映射 | 评分 |
|---|---|---|---|---|---|---|
| [kubernetes/kubernetes](https://github.com/kubernetes/kubernetes) | Apache-2.0 | 128,597 | 2026-10-29 | 镜像 GC：高/低水位（`image_gc_manager.go:393-404`），"在用/太新"的行跳过（`:492-510`），释放不足则显式失败（`:404-416`） | 最接近"先淘汰、淘汰后仍超才拒"；lease/minAge ≈ 我们的 60 min 租约；"释放不足→报错"≈ `E'⊊E` 的有界拒绝 | 5 / 5 / 4 |
| [bazelbuild/bazel](https://github.com/bazelbuild/bazel) | Apache-2.0 | 26,011 | 2026-10-29 | 磁盘缓存 GC：按 mtime 升序、超限即删最旧（`DiskCacheGarbageCollector.java:73-99`），AC 在 CAS 之前删以保引用完整性（`:70-76`），**跨进程排他锁**（`:187`），idle 后后台跑（`docs/remote/caching.mdx:311-320`） | 对应我们的 reconcile 应"单飞 + 幂等"；"先删 AC 再删 CAS"≈ 先删 link/引用再删快照的秩序；后台 GC vs 我们的事务内同步 GC 是取舍 | 5 / 5 / 4 |
| [etcd-io/etcd](https://github.com/etcd-io/etcd) | Apache-2.0 | 52,432 | 2026-10-28 | backend 配额：`Size()+cost < max`（`server/storage/quota.go:126-133`），超限返回 `ErrNoSpace`（`apply/quota.go:36-41`），并有 `NOSPACE` alarm（`api/v3rpc/quota.go:40-47`） | "有码拒绝 + 明确配额参数"可借；**不借**"先 apply 再返回错误"的顺序（`:37-41`）——那正是我们事务回滚要消灭的"客户端以为失败、数据已写" | 5 / 5 / 3 |
| [prometheus/prometheus](https://github.com/prometheus/prometheus) | Apache-2.0 | 66,849 | 2026-10-28 | 按时间降序排块（`tsdb/db.go:2245-2266`），累计超 `maxBytes` 后把**其后所有块**标记删除（`:2341-2347`），保留最新块；崩溃后靠 parent 标记续删（`:2121-2135`） | "保留最新、粗粒度删除"≈ 保留 LATEST；"块粒度"提醒我们：按行删除是细粒度优势，但也更容易碎片化；崩溃可续删 ≈ reconcile 要幂等 | 5 / 5 / 3 |
| [restic/restic](https://github.com/restic/restic) | BSD-2-Clause | 36,868 | 2026-10-18 | `forget`（按 `--keep-last` 等策略摘引用）与 `prune`（只删仅被已删快照引用的数据）两段（`doc/060_forget.rst:17-23,155-173`） | 与我们的 demote/evict（摘角色、删未被引用行）+ 未来的 reconcile 两段同构；"策略只决定保留集，不直接删数据"值得照搬 | 5 / 5 / 3 |
| [meta/rocksdb](https://github.com/meta/rocksdb)（`facebook/rocksdb` 重定向） | GPL-2.0（`COPYING`）+ Apache-2.0（`LICENSE.Apache`） | 32,232 | 2026-10-29 | `WriteBufferManager`：超 `buffer_size` 触发 flush/stall，不做"删受保护数据"（`include/rocksdb/write_buffer_manager.h:144-184`） | "超限→背压/拒绝，而不是删事实"与 §8.1 同向；**license 需注意**，只借形态不抄代码 | 3 / 5 / 2 |
| [ben-manes/caffeine](https://github.com/ben-manes/caffeine) | Apache-2.0 | 17,917 | 2026-10-25 | `maximumWeight` 明确"**可能提前淘汰，也可能在淘汰期间暂时超限**"（`Caffeine.java:458-483`） | **反例**：加权缓存是近似配额，不能拿来当"硬预算门"；我们的 fail-closed 必须自己实现精确 SUM + 事务内重判 | 5 / 5 / 2 |

**采纳结论**：只借形态，不引依赖。最值得抄的两点是：① Kubernetes 的"高/低水位 + 在用保护 + 释放不足显式失败"；② Bazel/restic 的"GC 单飞、两段式、崩溃可续"。最需要避免的是 etcd 的"先写后报错"和 Caffeine 的"近似配额"。

## 7. 未验证项（如实）

1. **本会话零 Gradle、零 adb/am instrument、零 git 写**。第 9 例、per-program/global 事务内重判例、`VisualCheckpointRetentionTest` 全部**未在本会话执行**；它们的绿/红只能由脚本门与设备轨裁决。
2. **视觉提交路径没有生产调用点**：`TutorVisualCommitRepository(` 仅在 `core/data/src/androidTest/.../TutorVisualCommitRepositoryInstrumentedTest.kt` 出现；`commitAssistantTurnWithVisual`、`stageSnapshot` 在 `app`/`feature`/`core` 的 main 源集中只有定义、没有调用（本会话 grep）。因此本文的"行为"目前是**测试/直接端口可达**的行为，不是产品运行路径。
3. **`reconcileStaleStaging` 未接线**由 grep 证明（仅定义与测试），但"将来由谁在何时调用"是 T3/主会话的决定，本文不替它选。
4. **跨进程并发未验证**：生产是否只开一个 DB 实例、是否会出现 `SQLITE_BUSY`、以及 Room3 对该异常的重试策略，本文只按官方 source 的 IMMEDIATE 语义论证，没有实测。
5. **性能未测**：事务内全表 `SUM` 的耗时、持锁时长、对其它写者的影响均无测量。
6. **备份/恢复后的预算行为未验证**：恢复可能带入接近/超过 32 MiB 的视觉行；下一次写入会走 plan/事务，但没有"恢复后立即对账"的代码，本文不对其正确性下结论。
7. **契约解释待裁定**：受保护字节是否计入预算分母（§4.3 末段）。本文倾向"计入"，理由是 §8.1 末句，但这是解释而非原文逐字结论。
8. **GitHub 评分是主观判断**；只有 license/stars/last push 是 `gh api` 实测值。RocksDB 的 license 为 GitHub 检测的 GPL-2.0（仓内同时有 `LICENSE.Apache`），引代码前必须单独做法务判断；本文未引任何代码。
9. **计划 §0 的"复核遗留"与现树不符**（§0 第 2 条）。这不影响本文交付，但台账/判据文件若照抄该句会形成反向虚报；本会话未改台账（非本任务范围）。

## 8. 来源

**仓内（本会话实读）**
- 契约：`docs/current/visual-generation-contract.md:186-192`（§8.1 正文与批次 32 注记）
- 计划：`.zcode/plans/plan-2026-10-29-batch-33-structure-gates-kd80.md:8,14`（"复核遗留"原文与任务 3）
- 仓储：`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/TutorVisualCommitRepository.kt:207-219,253-286,298-339,364-396,415-473,544-564`
- 策略：`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualCheckpointRetention.kt:29-41,50-64,74-102,104-144,151-156,165-224,238-245`
- DB：`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/RoomStudyDatabase.kt:1953-1984,1995-2090,2103-2113,2139-2197,2254-2318`
- DAO：`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/dao/TutorVisualDao.kt:118-171,177-190,208-235,243-261,294-309,345-367`
- 命令/DTO：`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/TutorVisualCommit.kt:27-62,181-223,231-286,387-396`
- 测试：`core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualCheckpointRetentionTest.kt:258-369`；`core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/visual/TutorVisualCommitRepositoryInstrumentedTest.kt:301-391,423-487`
- 备份：`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/backup/SmbkSelectionPlan.kt:501-509`；`BackupSnapshotTrimmer.kt:745-777`（INITIAL 无条件保留）
- Git 证据：`git log --oneline -6 -- <visual files>`；`git show 3087c8ae -- core/data/.../TutorVisualCommitRepository.kt core/database/.../RoomStudyDatabase.kt`；`git show 3087c8ae^:.../RoomStudyDatabase.kt | grep -n enforceVisualCheckpointBudget`（零命中）；`git log -S stagingIntoAnOverBudgetProgram -- <test>`（`9d91e6b0`）
- 入口/装配事实：`grep -rn "TutorVisualCommitRepository(" app feature core --include=*.kt`（仅测试）；`grep -rn "reconcileStaleTutorVisualStaging"`（仅定义/测试）

**外部（`gh api` / WebFetch 实跑，均未引依赖）**
- Room3：`https://github.com/androidx/androidx/blob/androidx-main/room3/room3-runtime/src/commonMain/kotlin/androidx/room3/RoomDatabase.kt#L691-L701`（`withWriteTransaction` = writer connection + IMMEDIATE）
- SQLite：`https://www.sqlite.org/lang_transaction.html`（DEFERRED / IMMEDIATE / EXCLUSIVE 与 `SQLITE_BUSY`）
- Kubernetes：`https://github.com/kubernetes/kubernetes/blob/master/pkg/kubelet/images/image_gc_manager.go`（`:393-416`、`:483-510`）
- Bazel：`https://github.com/bazelbuild/bazel/blob/master/src/main/java/com/google/devtools/build/lib/remote/disk/DiskCacheGarbageCollector.java`（`:73-99`、`:187`）；`docs/remote/caching.mdx`（`:311-320`）
- etcd：`https://github.com/etcd-io/etcd/blob/main/server/storage/quota.go`（`:126-133`）；`server/etcdserver/apply/quota.go`（`:36-41`）；`server/etcdserver/api/v3rpc/quota.go`（`:40-47`）；`server/etcdserver/errors/errors.go:38`
- Prometheus：`https://github.com/prometheus/prometheus/blob/main/tsdb/db.go`（`:2245-2266`、`:2311-2347`）
- restic：`https://github.com/restic/restic/blob/master/doc/060_forget.rst`（`:17-23`、`:155-173`）
- RocksDB：`https://github.com/meta/rocksdb/blob/main/include/rocksdb/write_buffer_manager.h`（`:144-184`）
- Caffeine：`https://github.com/ben-manes/caffeine/blob/master/caffeine/src/main/java/com/github/benmanes/caffeine/cache/Caffeine.java`（`:458-483`）
- 元数据（stars/license/pushed）：`gh api repos/{etcd-io/etcd,kubernetes/kubernetes,facebook/rocksdb,bazelbuild/bazel,prometheus/prometheus,ben-manes/caffeine,restic/restic}`（2026-10-29 实跑）
