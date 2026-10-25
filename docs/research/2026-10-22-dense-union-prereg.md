# dense-union 候选域扩张 · 预注册（2026-10-22）

> 跨线提案（改 core/database 契约），需与他会话协调窗口；本文件只冻结设计与判据，不施工。

## 1. 动机（可复算的事实）

弱章诊断（批次16，29 条融合 MISS 逐条回查）：**4 条 absent**——`镁的化学性质`、`等和线`、`活结与死结模型`、`力的分解`——在词面 512 候选域内根本不存在；当前 dense **只重排词面候选集**（成员不变），因此结构上无法救回。
其余 25 条在域内（rank>5），属排序问题。A1 别名臂（T/C-only）只能救回其中的镁；等和线/活结/力的分解需要**候选域扩张**。

## 2. 改动面（跨线清单，只读列出）

| 层 | 文件 | 需要的改动 |
|---|---|---|
| core/database | `RoomKnowledgeBaseStore.kt`（`readSubjectKnowledgeRecallCandidates`） | 返回「词面 top-512 ∪ dense top-K」并集（K 写死，如 64），保持长度语义（调用方仍取前 5） |
| core/database | `DenseRecallReranker.kt`（端口） | 载荷增加 dense-only 候选（nodeId + denseScore），或提供独立的 dense 全扫入口 |
| core/database | `StudyDatabasePort.kt` | 若新增常量，写入同一文件（`MAX_KNOWLEDGE_RECALL_CANDIDATES` 旁） |
| core/data | `DenseRecallAssembly.kt` / `DenseFusion.kt` | 融合缺失腿的归一化口径（dense-only 候选的 lex 腿缺失处理） |
| 资产 | `bge-small-zh-int8.vec` | 无需重打（dense 已覆盖 3,245 节点 + 别名向量）；需确认全扫 3,245 节点的延迟 |

## 3. 判据

- 与 A1 同：主集严格更高 + 24 章逐章不劣 + 负向守卫 CI 上界 ≥0；零漂移同路对称差 0；显著性 20k/seed 0。
- 另加：返回节点数仍 ≤5、工具结果形态不变；p95 ≤250ms / p50 ≤150ms（现有融合门）；内存与初始化预算不变。
- **必须先有 A1 之后的 A0**（A1 采纳则 A1 为新基线；不采纳则 v19 为基线），跨臂不可比。

## 4. 风险与前提

- 候选域扩张改变所有融合分数 → 所有既有钉值需按实测重钉；必须独立成臂、一条臂一次变更。
- core/database 为他会话区域：需协调"谁改、何时改"；建议由他会话施工、KB 线提供预注册与判据、共同读结果。
- 若并集仍无法覆盖 3 条 absent（内容侧概念缺口：活结的小环/横杆例子、力的分解的精确材料错挂），则按"已知边界"结案并转内容侧。

## 5. 结果节

（待填：A0/K 值/主集/逐章/零漂移/显著性/三规则/采纳或回滚）

## §6.1 增补（2026-10-26，出数前冻结；§1–§6 前文一字不动）

> 性质：**出数前冻结**。本节只钉口径与判据，不产生结果数；结果仍回填 §5（或后续结果节）。
> 依据分两层：**支持机制**（口径有外部先例/本地结构读数）与**存疑效果**（同一口径已有零增益实测、α 非最优证据）。外部先例与逐条来源见 `docs/research/2026-10-26-dense-union-fusion-precedents.md`；本地名次读数见 `docs/research/2026-10-23-kb-dense-union-estimate.md` 与 `build/agent-batch19/dense-union-estimate/`；代码链路以本仓库工作树实读为准：store 入口 `RoomKnowledgeBaseStore.kt:166-193` → `rerankMatchOrder` `:199-223` → 守卫 `DenseRecallOrdering.kt:17-27` → 端侧装配 `DenseRecallAssembly.kt:37-43`。

### 6.1.1 支持机制（口径本身可辩护）

- **α=0.5 有等权默认先例**：Supabase 两路权重 1/1、Qdrant 权重默认 1、OpenSearch 省略 weights 时等权、Azure RRF 各 child 等权（前引报告 §5.1/§7）。
- **每腿独立 min-max 有先例**：Weaviate `relativeScoreFusion`、OpenSearch per-clause normalization、Bruch 等人的 φ_mm/φ_tmm 都按每条腿自己的分数集归一（前引报告 §5.1）。
- **缺腿给 0 且不入域有明文/实现支持**：OpenSearch 明文“分子 0、分母仍计、不进该 clause 统计”；Supabase/pgvector/sqlite-vec 用 `COALESCE(..., 0)`；Qdrant/Elasticsearch/Weaviate 的缺腿项不贡献（等价该腿 0）（前引报告 §3.2）。
- **K=64 在外部常见区间内**：Supabase 2×final（top-5→10）、ParadeDB 20 for final 5、OpenSearch 100–200、Azure text 50 / vector k、ES `rank_window_size` 默认=size；64 落在 10–200 内（前引报告 §5.1）。
- **本地结构读数支持“能看见”**：4 条 absent 中 3 条的 dense 全科名次为 1/2/7（`镁的化学性质`/`活结与死结模型`/`力的分解`；`等和线` 为 336），3/4 在 K=64 内；29 条 A0 融合 MISS 中 26 条的 dense 名次 ≤64（全科与同科两口径同为 26/29）。复算：`build/agent-batch19/dense-union-estimate/dense-union-estimate.json` 的 `absent`/`miss29Global`（本轮实算 3/4、26/29）。
- **A1 基线的 12 条 MISS 中 10 条 dense 名次 ≤64**：本轮按 `build/agent-batch19/a1/jvm-fused-misses.txt` 的 12 个 expectedSlug 对 `build/agent-batch19/dense-union-estimate/golden-129-dense-ranks.json` 逐条实算 = **10/12**；其中 2 条是词面 absent 的 `活结与死结模型`/`力的分解`，8 条已在词面域内；域外 2 条为 `判别式法…` 全科 108/同科 100、`等和线` 336/182。

### 6.1.2 存疑 / 不支持效果（出数前必须承认）

1. **α=0.5 不是最优证据**：Weaviate 服务端默认 0.75；Bruch 论文在 α∈[0.6,0.8] 持续改善；本地离线诊断 α=0.6→0.7674（99/129）、α=0.7→0.7829（101/129）高于 α=0.5 的 0.7597（非判读）；α=0.7 设备臂因零漂移装置缺陷**不判读**。因此 0.5 只作为“保守默认”冻结，不声称最优（前引报告 §0-7、§5.1）。
2. **“缺腿=0”有结构上限**：dense-only 候选 `fused = 0.5·dense_norm ≤ 0.5`；α=0.5 下它不可能严格超过“词面满分且 dense_norm>0”的候选（前引报告 §5.2）。**同一口径已实测过并回滚**：批次18 lever-1（词面 512 ∪ dense-only 64、缺腿 0、α=0.5）设备/JVM 融合 **100/129**、MRR **0.622868**，与 A0 逐题完全一致（symdiff 0/0/0/0、配对差 0.0000、p=1.0000）；4 条 absent 目标融合名次为 26/absent/17/26，已回滚（`build/agent-batch18/lever-1/device-metrics.txt:3-5`、`probe-union-ranks.xml:6-10`、`docs/kb-acceptance-2026-10-17.md:510-512`）。**本臂不能承诺主集提升**；它回答的是“在 A1/v20 基线与当前守卫语义下，同一并集口径是否复现/改变读数”。
3. **K 的语义按本增补钉死为“排除词面候选后的 dense-only 追加预算”**，不是“全局稠密名次前 K”。这是本仓库对 K 的写法（见冻结条目 1），与外部主流“每腿全局 top-N 再合并”不同；结果节必须按此定义复算，不能与全局 top-K 读数混用（前引报告 §4.3）。
4. **判官/镜像的可行性边界**：两判官**不钉检索数字**（`GoldenRetrievalJvmTest.kt:23-27` 明写“测量台，不是守门人”；`GoldenRetrievalInstrumentedTest.kt:382-414` 只有 Recall/MRR 下界地板与 p50/p95 门，无 117/129 类断言）。但 JVM 融合镜像 `RetrievalBenchmark.kt:477-483` 用 `byId.getValue(...)` 只映射词面召回集；并集新增 id 会抛 `NoSuchElementException`。该文件是**装置面，不是判官**；若本臂要出 JVM 融合数，必须同步镜像（不动判官文件本身）。
5. **候选作用域**：`ProblemOrganizationDao.readKnowledgeNodesByIds` 的 SQL 注释明写“刻意不过滤 RETIRED”（`ProblemOrganizationDao.kt:346-352`），且不过滤 subject/可信；store 现有词面召回 SQL 则带 subject + 可信 + 非 RETIRED。dense-only 实体经一次 `readKnowledgeNodesByIds` 取行后，必须按既有作用域口径过滤（subject、CURATED/SOURCE_GROUNDED/USER_CONFIRMED、非 RETIRED），否则并集会引入域外节点。此条是冻结条目 3/4 的实现前置，不改变融合口径。

### 6.1.3 冻结条目

1. **K = 64**：稠密 top-K = 该科原子节点按 dense 全扫节点分降序排序、**排除词面候选集成员后**的前 64（dense-only 追加预算，不是全局 top-K）。实现锚点：`DenseVectorAsset.nodeScores(query, subjectKey)` 的键集就是该科全部有向量的原子节点（`DenseVectorAsset.kt:45-65`）；`DenseRecallAssembly.kt:37-43` 现有 `orderDomain` 仍是召回集，本臂改为并集域。
2. **并集域 `orderDomain = 词面候选 ∪ dense top-K`**；融合口径不动：α=0.5、每腿独立 min-max、缺腿给 0 且不入域（spec §2.4：`docs/kb-stage2-dense-spec.md:118-119`；端侧原文：`DenseFusion.kt:4-14,21,24-35,52-56`）。**既有成员融合分不变**：稠密域 = 该科全科（`DenseRecallAssembly.kt:37`；`DenseVectorAsset.kt:45-65`），词面域 = 原召回集（`DenseRecallAssembly.kt:38`），均不受并集成员影响；`denseScores` 必须保持 `nodeScores` 的全科键集，不得裁剪为并集成员。**实现约束**：dense-only 候选不得写入 `lexicalScores`；`lexicalScores` 只从原词面候选构建（否则 0 进入词面域会改变既有成员归一化与名次，前引报告 §4.2 有算式）。
3. **返回语义**：store 返回“**融合序的并集 + 父节点**”；父节点追加逻辑不变（`RoomKnowledgeBaseStore.kt:186-192`：从 matched 取 parent、减 matchedIds、`(matched + parents).distinctBy`）。dense-only 实体用**一次** `readKnowledgeNodesByIds` 取行（≤64 id；`RoomKnowledgeBaseStore.kt:286-293`），取行后按 6.1.2-5 的作用域过滤。返回长度不再是“≤ limit matched + 父节点”（原契约 `RoomKnowledgeBaseStore.kt:151-165` 的该句在本臂失效，改由本增补定义）；调用方仍取前 5。
4. **排列守卫新语义**：返回列表必须是“**入参候选的排列 + 允许的 dense-only 追加**”——全体入参 id 必须在返回中出现且无重复；追加项不得超过 K、必须来自同 subject 的 dense top-K，且能解析到实体；违反则**整体退回词面序**（不做部分应用）。现有守卫 `DenseRecallOrdering.orderOrLexical`（`DenseRecallOrdering.kt:17-27`）的“同长度 + 同 id 集合”条件按此更新；测试钉住新语义（含重复、缺项、超池、不可解析四类）。
5. **离线镜像**：`tools/dense_build/stage3_expectation.py` 镜像同一并集口径（K=64 / 同缺腿规则）：`build()` 的 `domain` 改为“词面候选 ∪ dense-only top-K”，`dense_norm`/`lex_norm` 的域与缺腿 0 规则不变（现镜像域 = 全科原子+topic，`stage3_expectation.py:225-240,259-272`）。重出参考数；**仅当钉值夹具因此变化才重生成**（`gen_dense_device_fixture.py` 的参考数由 `minmax()`/`best_first()` 现算，`stage3_expectation.py:76-92`）。
6. **判官零改动**：两判官不钉检索数，只钉金标 sha/条数、金标节点存在性、MISS 账本自洽、跨科作用域、腿存活、Recall/MRR 下界地板与 p50/p95（`GoldenRetrievalInstrumentedTest.kt:382-414`；`GoldenRetrievalJvmTest.kt:23-27` 等）。**金标冻结**：`tools/kb_coverage/tables/golden_queries_v2.json` 与 `golden-query-vectors.i8` 不改；任何改动都算换题，不在本臂内。
7. **预算不变**：p95 ≤250ms / p50 ≤150ms、`overBudget=0`（现有设备门；批次19 A1 实测 p95=51ms / p50=38ms，`build/agent-batch19/a1/device-metrics.txt:2`）。额外开销 = 一次 ≤64 id 的实体查询 + `nodeScores` 现有全扫（该全扫已在现生产路径内，`DenseRecallAssembly.kt:37`；并集不新增编码/扫描）。
8. **三规则 / 零漂移 / 显著性沿用**：三规则（主集严格更高、24 章逐章不劣、负向守卫 CI 上界 ≥0）、零漂移同路对称差 0、显著性 20k/seed0；**A=union，B=A1**（v20 基线）。回滚面 = 本臂触碰清单（全部 CLEAN 起手；`git checkout` 逐字回滚，含新增文件删除；不碰判官与金标）。
9. **预期（不承诺）**：结构覆盖 **10/12**（A1 的 12 条 MISS 中 10 条 dense 名次 ≤64，本轮实算；域外 2 条 = `判别式法…` 108/100、`等和线` 336/182）。既有 JVM/设备融合 **117/129** 的命中分数不变（冻结条目 2 的域不变性），变化只来自 dense-only 新成员进/不出 top-5；其中真正可由并集新增的 absent 目标只有 `活结与死结模型`、`力的分解` 2 条，故**主集上限 ≈119/129**（117+2；`docs/research/2026-10-23-kb-dense-union-estimate.md:73-79`）。这是上限，不是承诺；批次18 同口径实测为零增益（6.1.2-2）。

## §7 结果节（批次21 执行，2026-10-26）

> 性质：结果回填。§1–§6.1 原文未改；§5 的旧占位保留，本臂结果按 §6.1 约定记于此节。
> 证据分级：**设备/装置产物实读**（`build/agent-batch21/`）、**对拍/台账实读**（`build/agent-batch21/consistency.txt`）、**本代理复算**（附录命令）；未测项逐条标 `NOT_RUN`，不以推断代替。

### 7.1 执行身份与基线

| 项 | 值 | 依据 |
|---|---|---|
| 臂 | dense-union：词面召回集 ∪ dense-only 追加预算 K=64；α=0.5、每腿独立 min-max、缺腿 0 且不入域 | §6.1.3-1/2；`StudyDatabasePort.kt:51` `MAX_DENSE_ONLY_RECALL_CANDIDATES = 64` |
| 对照基线 | **A1（v20，117/129）**；预注册 §3 所称“A1 之后的 A0” | `build/agent-batch19/a1/device-metrics.txt:3,5` |
| 执行窗 | 2026-10-26 00:22–01:27（主机时间；设备轮 00:53:03–00:56:40） | `build/agent-batch21/impl-touched-files.txt` mtime（00:22）；`d3/round-start-ms.txt`（00:52:59）；`d3/step3_run_device.start/.end`（00:53:03–00:56:40） |
| HEAD | `ce01508f622e290ceaec580122a84a19120eeb61`（快照采集 01:24） | `consistency.txt:1`；`git rev-parse HEAD` |
| 实现触碰 | 8 个 tracked 文件修改 + 新增 `DenseUnionRecallTest.kt`；判官与金标零改 | `build/agent-batch21/impl-touched-files.txt:1-8`；`git status --porcelain`（本代理复跑） |
| 装置门 | green（`:core:data:compileDebugUnitTestKotlin`；本文书 ask 头 `门=true`） | ask 头；`tests/targeted-results/` 与 `d3/step2_build.log` 佐证 |
| 八门 | rc 全 0、`overall red=0` | `build/agent-batch21/gates/summary.txt:1-18` |

### 7.2 读数

**设备（`GoldenRetrievalInstrumentedTest`，判官零改；`build/agent-batch21/union/device-metrics.txt`）**

| 路线 | Recall@5 | MRR | p50 / p95 / max | overBudget | 其他 |
|---|---|---|---|---|---|
| 融合（本臂） | **117/129 = 0.9069767441860465** | **0.7322997416020672** | 45 / 59 / 87 ms | 0 | `denseLegLive=true`（`:2-5`） |
| 词面（本臂） | **102/129 = 0.7906976744186046** | **0.6206718346253232** | 8 / 16 / 20 ms | — | `:32-35` |
| 融合（A1 基线） | 117/129 = 0.9069767441860465 | 0.7322997416020672 | 38 / 51 / 84 ms | 0 | `build/agent-batch19/a1/device-metrics.txt:2-5` |
| 词面（A1 基线） | 102/129 = 0.7906976744186046 | 0.6206718346253232 | 7 / 13 / 18 ms | — | 同上 `:32-35` |

- **主集与 A1 逐位相同**（117/129，MRR 同值）；24 章逐章行与 A1 逐行文本相同（本代理 `diff` rc=0，见 §7.9）。
- **并集确实执行**：设备 logcat 共 658 行 `denseOnlyAppended=64`（例 `build/agent-batch21/d3/device-logcat.txt:4229`），即每次重排追加了 64 个 dense-only 候选；但 top-5 命中未变。
- **MISS 集合同集、名次有变**：fused 12 条 query 集合与 A1 `diff` rc=0；词面 27 条同集。名次变化（`zero-drift.json` 的 `a_side_union_device_fused_vs_b_side_a1.rank_delta`）：
  - `活结与死结模型`：A1 `absent` → 本臂 **17**；
  - `力的分解`：A1 `absent` → 本臂 **28**；
  - `等和线`：`absent` → `absent`；
  - `同分异构现象和同分异构体`：12 → 20（仍 MISS）；
  - `共点力的动态平衡`：51 → 53（仍 MISS）；
  - `comparable=9, max_abs_delta=8, absent_rank_mismatch=2`。
  → 并集把 2 条词面 absent 目标拉进了 256 名探针窗口，但没有进入 top-5；1 条（`等和线`，dense 全科 336）仍在窗口外。这与 §6.1.2-2 的“缺腿=0 结构上限”一致，但本臂没有保存 per-leg 分数分解，**不能把该解释升级为逐候选因果证明**。

**离线镜像（`tools/dense_build/stage3_expectation.py` 并集口径；`build/agent-batch21/stage3_expectation.union.log`）**

| 项 | 读数 | 说明 |
|---|---|---|
| `refFusedD1` | **117/129 = 0.9069767441860465**，`chapterMin=0.5`，MRR **0.7241602067183462** | `:4,7`；MRR 口径为“只在前 5 名内取名次”，与设备“最优名次”不可直接比较 |
| `refDenseD1` | 104/129 = 0.8062015503875969 | `:3,8` |
| `refLexicalOnlyD1` | 102/129 = 0.7906976744186046 | `:2,9` |
| 改前/改后参考数 | **逐项相同**（`build/agent-batch21/backup/stage3-device-expectation.before.json` vs `build/stage3-device-expectation.json`） | 旧镜像域已是全科原子+topic；并集只显式化定序域，未改变参考数 |
| 脚本自证 | 并集自证断言（既有候选相对次序不变、追加 ≤K、父节点不入域）未触发 | 脚本正常出数；未记录 exit code，本代理未重跑 |
| Stage-2 冻结锚 | **N/A，复现断言未运行**：语料 28,931 行 / 当前布局 44,041 行 / 锚点语料 28,932 行 | `stage3_expectation.union.log:5` 原文“不算已通过” |

### 7.3 零漂移

**结论：`parked-gate-red`（`NOT_COMPUTABLE`），不是 PASS，也不是 FAIL。**

- 权威 JSON：`build/agent-batch21/union/zero-drift.json:3` `"status": "not_computable"`；`:234-235` `verdict_lexical=NOT_COMPUTABLE(jvm-lexical ledger absent: judge mirror red)`、`verdict_fused=NOT_COMPUTABLE(...)`；`zeroDriftBothRoutes=null`。
- 同路 device↔JVM **未计算**（JVM 账本未产出）。两条独立原因：
  1. **编译阻断（他线在飞文件）**：`:core:domain:compileKotlin FAILED`，`EloCorrectnessPredictor.kt:288:41 / 289:41 Unresolved reference 'roundTo'`（该文件 `??`）；JVM 测试 exit=1（`build/agent-batch21/d3/step5_run_jvm.log:41-43,67`）。
  2. **并集与 JVM 镜像的已知冲突**：`RetrievalBenchmark.fusedRecall` 的 `byId.getValue(...)`（`RetrievalBenchmark.kt:482`）只映射词面召回集，对 dense-only 追加 id 抛 `NoSuchElementException`；`:core:data` 全量 **1109 tests, 1 failed**（`build/agent-batch21/tests/full-core-data.log:64-67`；XML `tests="2" failures="1"`）。
- **设备侧 A/B 对照（仅说明设备结果）**：本臂 device-fused 12 vs A1 12、device-lexical 27 vs A1 27，query 集合 symdiff 均 **0**（`zero-drift.out.txt:4-6`；`zero-drift.PROXY-A1JVM.json` 同值）。
- **代理读数（明确不是 B2 门）**：以 A1 的 JVM 账本作 B 侧，`zero-drift.PROXY-A1JVM.out.txt:1-3` 得 `lexical=PASS fused=PASS`；这是“批次21 设备 × 批次19 A1 JVM”，不是同路同代测量，**不能充当 B2 通过**。

### 7.4 显著性

**`NOT_RUN`。** 本臂未执行 20k/seed0 配对 bootstrap，`build/agent-batch21/` 下无任何 `significance-*` 产物；因 B2 已 parked，按预注册流程未进入“显著性与三规则”判读段。不得以“无显著差异”或“p=…”表述本臂。

### 7.5 三规则

**未正式判读**（判读段未执行）；按设备读数做原始核对如下，供后续复跑时对照：

| # | 规则 | 原始核对 | 结论 |
|---|---|---|---|
| 1 | 主集严格更高 | 117 vs 117（A1） | **不成立**（相等，不是严格更高） |
| 2 | 24 章逐章不劣 | 24 章逐行文本与 A1 相同（`diff` rc=0） | 原始读数无下降；未走正式判读 |
| 3 | 负向守卫 CI 上界 ≥0 | 显著性未跑，无 CI | `NOT_RUN` |

- 即使 B2 后续修复，当前读数下规则①已不成立；因此本臂**不能据现有读数采纳**。若未来复跑仍为 117/129，应按 §6.1.3-8 回滚，而不是把“与基线相同”解释成通过。

### 7.6 采纳或回滚

**裁定状态：`parked-gate-red`——未采纳、未回滚。**

- 原因：设备取数完成（117/129、预算内），但 B2 同路零漂移 `NOT_COMPUTABLE`；按预注册“非 0/不可计算不判读”，收束段未执行（无 `build/agent-batch21/union-final.md`，无回滚 sha 核验）。
- **实现仍在工作树**：8 个 tracked 文件 `M` + `DenseUnionRecallTest.kt` `??`（本代理 `git status --porcelain` 复跑）；未做 `git checkout` 回滚。**这些改动不得当作新基线或已采纳代码**；下一次会话必须先修 JVM 镜像/编译阻断并复跑 B2 + 显著性 + 三规则，或按 §6.1.3-8 逐字回滚。
- 基线不变：B1 仍为 **A1 117/129**；批次20 验收宣告与 `docs/kb-acceptance-2026-10-17.md` §10 不因本臂改写（本臂未采纳，未追加 §11；该文当前止于 §10）。

### 7.7 sha 核验

| 项 | 实测 | 对照 | 结果 |
|---|---|---|---|
| 设备判官 `GoldenRetrievalInstrumentedTest.kt` | `c2280255a9b0db2bc77ac1213e977be755a431efe80cfdf5b3c0c6e7bb7a4786` | 批次19 A1 `build/agent-batch19/a1/judge-files-record.md:16` 同值；porcelain clean | ✓ 未变 |
| JVM 判官 `GoldenRetrievalJvmTest.kt` | `62c232fcbf3ffd11fcdedaaa7a4340981bbc22b8d6a2e0ad39614ef78f5ccd95` | 批次19 A1 记录同值；工作树 `M` 为他线 3+/3− 注释（batch13→14） | ✓ 判分逻辑未改 |
| JVM 镜像 `RetrievalBenchmark.kt` | `8c2a2ef05b1c96bb31bbef206e1d432ec615c2ab36f7c06c86076ec3d824e5be` | 批次18 回滚记录同值 | ✓ 未变 |
| dense `.vec` | `0536d0d1cda99bd5614bc94d9176628d7ce999938db1c289b3b44600ae13b23a`；`vectorCount=44041` | `consistency.txt:4-13`；`DenseRecallAssembly.kt:127` 同值；本臂 diff 命中数 0 | ✓ 未打资产 |
| 本臂 APK | `db6eb3e9d2a96b2f0623db06b0dcf8c9d4bad2df22b42c2d242fe8b1a50e34db`；内嵌 vec `0536d0d1…` | `d3/post-run-samegen-check.txt:2-11`：worktree = local built = installed | ✓ 同代 MATCH |
| 包身份 | v20 / `ebf477816c318704` | `consistency.txt:2-3` | ✓ 未变 |

- 装置同代说明：`d3/apk-samegen-check.log` 是**重建前**残留（当时本地 APK 仍是批次20 的 16:32 代），只能证明资产同代；**权威同代证据是重建后的** `d3/post-run-samegen-check.txt`（APK `db6eb3e9…`、内嵌 vec `0536d0d1…`、当前已安装 APK 同值）。并集代码在包内另有 logcat `denseOnlyAppended=64` 佐证。
- **回滚 sha 核验：N/A**（本臂未回滚）。

### 7.8 本臂未验证 / 未运行项

1. **同路 device↔JVM 零漂移**：`NOT_COMPUTABLE`（JVM 账本缺失；两条原因见 §7.3）。代理 PASS 不是门。
2. **显著性 20k/seed0**：`NOT_RUN`。
3. **三规则正式判读**：未执行；规则③无 CI；规则①按读数不成立。
4. **`:core:data` 全量单测**：红——1109/1109 中 1 失败（`GoldenRetrievalJvmTest`，镜像冲突），不是全绿；`:core:database` 全量 `BUILD SUCCESSFUL`（`tests/full-core-database.log`）。
5. **离线 Stage-2 冻结锚复现**：`N/A`，复现断言未运行（语料代际差）。
6. **红测先行的独立红日志**：未在 `build/agent-batch21/` 找到；最终 `DenseUnionRecallTest` 3/3 绿由 XML 钉住，但“先红后绿”的过程证据不完整。
7. **真机**：无；设备读数为 emulator-5584（`kb_test_device`）。
8. **本臂未重跑**：设备测试、JVM 测试、Gradle 全量均未由本代理重跑；所有读数均为执行段产物实读。
9. **验收文书未改**：本臂未采纳，故 `docs/kb-acceptance-2026-10-17.md` §11（该文当前止于 §10）与 `docs/research/2026-10-25-kb-final-acceptance-criteria-v1.md` 状态列未刷新。

### 7.9 复算命令（只读）

```bash
cd "D:/smart mistake book"

# 设备融合/词面读数与预算
sed -n '2,5p;32,35p' build/agent-batch21/union/device-metrics.txt

# 逐章与 A1 逐行对拍（预期 rc=0）
diff <(sed -n '6,30p' build/agent-batch21/union/device-metrics.txt | sed 's/^.*System.out: //') \
     <(sed -n '6,30p' build/agent-batch19/a1/device-metrics.txt | sed 's/^.*System.out: //')

# MISS query 集合对拍（预期 rc=0）
diff <(grep -o 'query=.*' build/agent-batch21/union/device-fused-misses.txt) \
     <(grep -o 'query=.*' build/agent-batch19/a1/device-fused-misses.txt)

# 零漂移门状态（预期 NOT_COMPUTABLE）
python -c "import json;d=json.load(open('build/agent-batch21/union/zero-drift.json',encoding='utf-8'));print(d['status'],d['verdict_lexical'],d['verdict_fused'],d['zeroDriftBothRoutes'])"

# 并集追加确实发生（预期 658 行 / 每次 64）
grep -c 'denseOnlyAppended=64' build/agent-batch21/d3/device-logcat.txt

# 离线参考数
sed -n '2,9p' build/agent-batch21/stage3_expectation.union.log

# 靶向测试计数
for f in build/agent-batch21/tests/targeted-results/TEST-*.xml; do sed -n '2p' "$f"; done

# 资产/判官 sha
sha256sum core/data/src/main/resources/knowledge/dense/bge-small-zh-int8.vec
sed -n '1,3p' build/agent-batch21/d3/judge-sha.txt

# 八门汇总
sed -n '1,18p' build/agent-batch21/gates/summary.txt
```

### 7.10 收口补记（主循环终裁，2026-10-26）

- **终裁动作：逐字回滚已执行**——`git checkout --` 8 个 tracked 文件（`DenseRecallReranker.kt`、`DenseRecallOrdering.kt`、`StudyDatabasePort.kt`、`RoomKnowledgeBaseStore.kt`、`DenseRecallOrderingTest.kt`、`DenseRecallAssembly.kt`、`DenseRecallRerankerTest.kt`、`stage3_expectation.py`）+ 删除 `DenseUnionRecallTest.kt`；该面 `git status --porcelain` 为空（逐字节回到 HEAD）；他线文件零触碰（`GoldenRetrievalJvmTest.kt` 仍为其 `M` 态，sha `62c232fc…` 未变）。§7.6 的「未回滚」由本节取代。
- **复跑核验（本代理实跑）**：`:core:data:testDebugUnitTest --tests "*GoldenRetrievalJvmTest*" --tests "*DenseRecall*"` → **BUILD SUCCESSFUL in 1m 24s**（并集引入的 `NoSuchElementException` 随回滚消除）。
- **终裁结论：不采纳、已回滚、结案。** 三条互相独立的依据：
  1. 设备实测与 A1 **逐位相同**：117/129、MRR 0.7322997416020672、24 章逐章同值、融合 MISS 12 条同集（§7.2）；
  2. **并集确实执行**（logcat `denseOnlyAppended=64`；`活结与死结模型`/`力的分解` 从 absent → 探针名次 17/28 仍未进 top-5）——在冻结融合口径（缺腿=0、不入域）下 dense-only 的融合分上限 0.5 低于错误 top-5 的双腿分，**结构性无效**，不是实现缺陷；
  3. **批次18 lever-1 在 A0 基线上同口径亦逐位零增益**（100/129、MRR 0.622868）——两次独立基线复现同一结论。
- **不可判读的装置原因**：JVM 镜像 `RetrievalBenchmark.kt:482` 对非词面候选用 `byId.getValue` → 并集新增 id 必抛异常；修复属判官装置变更，须单独预注册，不顺手改判官。
- **重开条件（写死）**：仅在**新预注册**下重开——①缺腿归一化口径变更（须有外部依据，禁止金标调参）；②判官装置并集感知（独立预注册，与臂解耦）；③研究建议的 α/K 拆分臂（`docs/research/2026-10-26-dense-union-fusion-precedents.md`）。
- **基线维持**：B1 = A1 **117/129 = 0.9069767**；批次20 验收宣告与验收文书 §10 不变。
