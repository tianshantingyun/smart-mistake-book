# KB 救援配额（rescue quota = 1）臂：预注册（2026-10-31）

> **性质**：出数前冻结。§1–§5 写死，执行轮只允许在文末追加 §7；不得回改本文 §1–§5。
> **写作时点**：2026-10-31；HEAD `5ee1f90f7e2411a6c1865890f6bf5da21f2b57aa`（`git rev-parse HEAD` 实跑）。
> **重开依据**：`docs/research/2026-10-22-dense-union-prereg.md` §7.10 重开条件②「判官装置并集感知（独立预注册，与臂解耦）」（`:226`）；救援配额策略来源 `docs/research/2026-10-26-dense-union-fusion-precedents.md` §6.2（`:263`）。
> **本步唯一新增写盘**：本文件。未改源码、未跑 Gradle/设备/判官、未写 staging。

---

## 0. 写前守卫与基线身份

### 0.1 porcelain 守卫（写前实跑）

```bash
git status --porcelain | wc -l                                  # 729
git status --porcelain | awk '{print $1}' | sort | uniq -c      # 485 ?? / 244 M
git rev-parse HEAD                                             # 5ee1f90f7e2411a6c1865890f6bf5da21f2b57aa
git status --porcelain -- <§4.1 的写面>                          # 2 条 M：DenseRecallAssembly.kt（v24 钉值）、RoomTutorToolRunner.kt（渲染 v2）
ls docs/research/2026-10-31-kb-rescue-quota-prereg.md           # No such file
```

- **目标文件不存在 → 本次是新建，只写一次**；执行轮结果按“只追加”写 §7。
- 729 条脏项属他线 + v24/渲染 v2 已采纳但未提交的改动；本批不触碰、不裹挟、不回滚。
- `DenseRecallAssembly.kt` 的 `M` 是 v24 dense 重打的钉值改动（`VECTOR_ASSET_SHA256` → `70329115…`）；`RoomTutorToolRunner.kt` 的 `M` 是批次 30 渲染 v2 已采纳的改动。两者都是**本臂的起点，不是本臂的改动**；§4.1 的 pre-sha 取当前工作树字节。

### 0.2 v24 基线身份（本会话实算/实读）

| 项 | 值 | 证据 |
|---|---|---|
| manifest | `version=24`、`schemaVersion=2`、`contentVersion=9664896aeba22e86`、`packId=moe-2025-four-subjects-v1`、`retired=1,222` | 读 `moe-2025-update-manifest.json` |
| manifest sha | `2aa5e5e5d6ca2d3c426f590d1fcddd63e0241b02be56fc371e4d694fa15cf076`（581,672 B） | `sha256sum` |
| 主包 | `96246c78a1706bfc29e9de9f98e43a927e055e16d44bc4dcd1b94b3b78ca0b4f`（4,538,352 B） | `sha256sum` + `stat` |
| 节点 / 材料 / aliasVectors | **3,280 / 65,016 / 40,841**；vectorCount 44,121 | 遍历主包 + 25 sidecar；`.vec.json.corpus` |
| sidecar v2-02 | `ba443288dca7e10e598a26934516348b47055eb017f4fd9bcda1f7d9f6ec56e1`（1 条改绑） | `sha256sum` |
| sidecar v2-25 | `d132dd684ddda2224704abd93d920a77e2591b77db4e89f61f314bede33937ac`（材料 152→154） | `sha256sum` |
| sidecar 索引 | `2da0b9c5702c708723e4129fba3eda6e34875271ea07422f2aa9fb9981145b92`（未变） | `sha256sum` |
| `.vec` | `70329115240fe6beafcb88e6fd85d80585fd64f8b22dbe284730f742b0a2a1a5`；26,087,046 B；dim 512；44,121 vectors | `sha256sum` + `.vec.json` |
| `.vec.json` | `5cbbcb6f1b45e1638a4e6bb7f56f5a86a28d4e5a0a70b5efe83aead8f2d2f089` | `sha256sum` |
| `model-manifest` | `b389653153ab6698b7a0be575f39c49df7fce8f9babac78485f2fe9c566183cb` | `sha256sum` |
| 代码钉值 | `DenseRecallAssembly.kt:113` `VECTOR_ASSET_SHA256 = "70329115…"`；文件 sha `c654d988f9a8e730168c435b788e196c858691e9a3af50b2494c07ed57512683` | `grep -n` + `sha256sum` |
| dense 夹具 10 件 | **6 改 / 4 未变**（改：encoder-parity cases/vectors/json、dense-scan、tokenizer cases/summary；未变：dense-device-order、dense-fusion、golden-query-vectors.i8、tokenizer-stages） | `build/agent-batch30/v24-final.md` §二；`dense/post-sha.txt` |
| 钉值文件 6 件 | **2 改 / 4 未变**（改：`DenseRecallAssembly.kt`、`DenseVectorAssetTest.kt`；未变 4 件） | 同上 |
| 内容表 5 改 | PHYSICS `37e0adbc…`、MATH `d1d68d61…`、material_rebind `b8d16b91…`、extraction_state `dff4ca17…`、alias_map `5e8ed8ba…` | `sha256sum`；与 `v24-touched-files.txt` post 值同 |
| 金标 | `eabef10228916d46a18aaaa4e3084f5bacdf6e07ce3a3f8384d31ff71c6f2102`（129 / 24 章） | `sha256sum` |
| 判官 / JVM 镜像 | 设备 `GoldenRetrievalInstrumentedTest.kt` `c2280255…`；JVM `GoldenRetrievalJvmTest.kt` `62c232fc…`；镜像 `RetrievalBenchmark.kt` `8c2a2ef0…` | `sha256sum` |
| 设备基线 | 融合 **117/129 = 0.9069767441860465**、MRR **0.7239018087855298**；词面 101/129；p50 47 / p95 77 / max 185 ms；`overBudgetSamples=0` | `build/agent-batch30/v24/device-metrics.txt:2-5,32-35` |
| JVM 基线 | 融合 117/129、MRR 0.7239018087855298、`chapters=24` | `build/agent-batch30/v24/jvm-fused-metrics.txt:1,7-8` |
| 零漂移 | lexical 28/28 symdiff **0**；fused 12/12 symdiff **0**；PASS/PASS | `build/agent-batch30/v24/zero-drift-recheck.json` |
| 三个边界单点 | `等和线` / `活结与死结模型` / `力的分解` 四路仍全 `absent` | `build/agent-batch30/v24/three-boundary-single-points.json`；`v24-final.md` §四 |

> 本臂**不改** dense 资产、夹具、内容表、别名账本；上述 6/4、2/4、5 改只是 v24 基线身份，不是本臂的动作。

---

## 1. 臂定义（冻结）

### 1.1 开关、配额与触发条件

- **配额 = 1**（每查询至多 1 个救援槽）。
- **开关**：新增可选端口 `DenseRescueSelector`；`null` = 完全关闭，检索路径与 v24 **逐字节同行为**。生产装配由 `DenseRecallAssembly.ENABLED` 一并控制（关闭时不返回 selector）。
- **生效范围（写死）**：只作用于 `KNOWLEDGE_READ` 的 5 槽结果（`RoomTutorToolRunner.knowledgeRead` 调用新增的 scoped 读口）。拍照讲题预披露（`RoomTutorKnowledgeContextLoader`）、错题归类（`RoomMistakeOrganizationRepository`）、`MASTERY_READ` 的聚焦解析（`queryText=null`）等其它 `readSubjectKnowledgeRecallCandidates` 调用方**不经过**救援路径，零变化（I10）。
- **触发条件（全部满足才触发）**：
  1. `queryText` 非空白，`searchFeatures` 非空；
  2. dense 腿可用（编码器/资产加载成功，`nodeScores` 返回非空）；
  3. **存在 dense-only 候选**（见 §1.2）；
  4. 最终可见列表长度 ≥ 5（够 5 槽才谈“第 5 槽”；不足 5 不补位、不救援）。
- 任一不满足 ⇒ 不救援，结果与 v24 相同。

### 1.2 dense-only 候选域（冻结）

```text
lexicalIds = dao.searchSubjectKnowledgeRecallCandidates(subject, features, limit=512) 的行 id
denseScores = DenseVectorAsset.nodeScores(queryVector, subjectKey)   // 该科全部有向量的原子节点
denseOnly = denseScores.keys - lexicalIds - visibleIds[0..3]
denseOnlyRanked = sort(denseOnly, by fused desc, node_id asc).take(8)
```

- **dense 全扫**：正常路径沿用生产 dense 腿的同一份 `nodeScores`（`DenseVectorAsset.kt:51-63`），不新增编码/扫描；其键集同时是稠密腿 min-max 域。
- **单查询单次计算（正常路径）**：正常路径（`order` 后紧跟 `denseOnlyRanked`、同键命中）同一查询只编码一次、只全扫一次。`OnDeviceDenseRecallReranker.order` 把本次 `(subject, queryText) → denseScores` 放入**单条**快照；`denseOnlyRanked` 命中同键时直接复用，未命中可重算，但**命中/未命中结果必须逐字相同**（编码器与资产确定）。单条快照不随查询数增长；并发未命中只多算一次，不影响正确性。
- **排除词面候选集**：`lexicalIds` = SQL 召回集（**不是**融合后的 top-5，也不是父节点集）。
- **追加排除 `visibleIds[0..3]`**：只为“5 槽不重复”这一正确性不变量；不改变“dense-only = 不在词面候选集”的定义。若最高分 dense-only 已在前 4 槽（可能是父节点），取下一个最高的 dense-only；没有则本轮不救援。
- **返回 top 8**：给“前 4 槽去重 + 少量不可信节点跳过”留余量；排序在 selector 内完成，store 只按序取第一个合法项。
- 父节点不在 dense 资产里（资产只含原子节点 + 别名向量），故候选域是**该科原子节点**。

### 1.3 融合分与选择（冻结）

- 对 `denseScores` 做**全科同域** min-max（`DenseFusion.minMax`，`DenseFusion.kt:24-35`），不因救援改变域。
- dense-only 缺词面腿 ⇒ `lexNorm = 0`、且**不入**词面腿 min-max（与 `DenseFusion.kt` 的缺腿语义逐字一致）。
- 融合分：`fused = 0.5 · denseNorm + 0.5 · 0 = 0.5 · denseNorm`（α=`DenseFusion.ALPHA`，`:21`）。
- 选择：按 `max fused` 降序；并列按 **node_id 升序**（与 `DenseFusion.order` 的 `thenBy { it }` 同序）。`denseOnlyRanked` 返回 top 8，store 按序取第一个合法项。
- 救援节点必须是该科、有向量的原子节点（由 `nodeScores` 保证）；加载后必须与词面腿**同筛** `verification_status ∈ {CURATED, SOURCE_GROUNDED, USER_CONFIRMED}`（`ProblemOrganizationDao.kt:219`；与 `RetrievalBenchmark` 的 `TRUSTED_VERIFICATION_STATUSES` 同集）。不满足或加载不到 ⇒ 跳过该候选，取下一个；没有则本轮不救援。

### 1.4 第 5 槽替换（冻结）

- **scoped 读口**：新增 `readSubjectKnowledgeRecallCandidatesWithRescue(subject, searchFeatures, limit, queryText)`（端口默认实现 = 直接回退到原 `readSubjectKnowledgeRecallCandidates`；只有真实 store 覆盖为“召回 + 救援”）。原方法的签名/语义/所有调用方**零改**；只有 `RoomTutorToolRunner.knowledgeRead` 改调 scoped 口。
- **替换点**：scoped 口内部按下列伪代码替换第 5 槽；`knowledgeRead` 仍对返回列表 `.take(5)`。

```text
result = (matched + parents).distinctBy(id)          // 现有返回，长度 ≤ limit
if (result.size >= 5):
    for id in denseOnlyRanked:                       // top 8，已按 fused 降序
        node = loadNode(id)
        if (node != null && node.subject == subject
            && node.verificationStatus in TRUSTED
            && id !in result[0..3]):
            result[4] = node                          // 只换第 5 槽
            break
return result                                        // 上层 knowledgeRead 仍 .take(5)
```

- **只换第 5 槽**：`result[0..3]` 与 `result[5..]` 的成员和相对次序不变；救援节点不与任何已显示节点重复。
- **不重新排序、不重新归一化**：`DenseRecallReranker.order` 的入参仍是词面候选集，**不得**把救援节点加入 `orderDomain`——否则词面腿/稠密腿的 min-max 域改变，前 4 槽可能被重排，违反“其余 1–4 槽与主融合分不变”。
- 若 `result.size < 5`：不补位、不救援（不改变现有长度语义）。

### 1.5 不变量（F9 类回归门）

| # | 不变量 | 判法 |
|---|---|---|
| I1 | 关闭 selector 时结果与 v24 逐字节相同 | 同夹具 A/B |
| I2 | 主融合分的输入域与计算不变 | `order()` 的 `orderDomain`/`denseScores`/`lexicalScores` 与 v24 同 |
| I3 | 前 4 槽成员与次序不变 | A/B 对比前 4 个 id |
| I4 | 结果长度语义不变（≤ limit） | `result.size ≤ limit` |
| I5 | 5 槽无重复节点 | `distinctBy` 后长度 = 替换前 |
| I6 | 救援节点属于该科、可信、不在词面候选集 | 断言 |
| I7 | dense 腿不可用/query 空/候选不足 5 ⇒ 零变化 | 三条降级夹具 |
| I8 | 正常路径（order → denseOnlyRanked 同键）只编码一次、只全扫一次；未命中重算结果与命中相同 | 计数编码器调用 + 命中/冷启两支对拍 |
| I9 | `denseOnlyRanked` 长度 ≤ 8，按 fused 降序/ id 升序 | 单测 |
| I10 | 其它 recall 调用方（photo tutor 预披露/错题归类/mastery 聚焦）零变化 | 调用点 diff + 同夹具 A/B |

### 1.6 关闭与降级路径（写死）

- `DenseRescueSelector == null` ⇒ 不执行任何新代码分支（生产默认与测试默认都是 null）。
- **同一 runtime**：`DenseRecallAssembly` 的 `reranker(context)` 与 `rescueSelector(context)` 必须从**同一** `DenseRuntime`（tokenizer/asset/encoder 只加载一次）与同一单条快照派生；不得各建一份（否则模型/资产内存与首用成本翻倍）。
- dense 腿不可用（`null`/异常/资产哈希不符）⇒ 不救援，结果与 v24 相同；不允许因救援失败让主检索失败。
- `searchFeatures.isEmpty()` 走现有 `readSubjectKnowledgeNodes` 回退分支，**不救援**。

---

## 2. 判读（冻结）

### 2.1 三规则 + 零漂移 + 显著性

| # | 判据 | 求值口径 |
|---|---|---|
| 1 | **主集严格更高** | 设备融合 Recall@5 **≥ 118/129**（v24 基线 117/129；等于 117 不算通过） |
| 2 | **24 章逐章不劣** | 24 章逐章 Recall@5 与 v24 基线比较；任一章**严格更低**即不采纳；equal/improved 均通过 |
| 3 | **负向守卫** | 按章 cluster 配对 bootstrap `--reps 20000 --seed 0`；**A = 本臂、B = v24**，配对差 = A − B；要求 95% CI **上界 ≥ 0** |
| 4 | **零漂移对称差 0** | 同路 device↔JVM 的 fused 与 lexical 两条 MISS query 集合 symmetric difference 必须 **0**；非 0 = 装置缺陷，本臂**不判读**（不是 FAIL，也不是回滚结论） |
| 5 | **显著性** | 20k / seed0 的配对 bootstrap 必须实际执行并落盘 delta/CI/p；缺失记 `NOT_RUN`，不得写“无显著差异” |

- **基线读数（v24，转引，执行轮复跑）**：设备融合 117/129、MRR 0.7239018087855298；JVM 融合 117/129；零漂移 lexical/fused 均 0；24 章 `chapters=24`。
- **方向**：规则 3 的 A−B 是“本臂 − v24”；上界 ≥0 只排除“显著更差”，不要求显著更高（严格更高由规则 1 承担）。
- 显著性脚本沿用 `tools/dense_build/retrieval_significance.py`（v24 收束轮实跑，见 `build/agent-batch30/v24/significance-*-v24-vs-render-v2.json`）；本臂必须用同一脚本、同一 cluster 口径、20k/seed0。

### 2.2 决策规则（冻结）

1. **规则 1/2/3 全绿 + 零漂移 0 + 显著性已跑 → adopted**；规则 1/2/3 任一红 → 不采纳，按 §4 回滚。
2. **零漂移非 0 / NOT_COMPUTABLE → 不判读（park）**：不采纳、也不下回滚结论；先修装置或按 §3 的独立预注册补齐 JVM 镜像，不得用代理账本冒充同路零漂移。
3. **显著性 `NOT_RUN` → 不判读、不采纳**：不得以“聚合相同”代替 20k/seed0；代码可保留待补跑，但不得称 adopted。
4. **阈值/分母/样本事后不改**：117/129 基线、118/129 门槛、24 章、20k/seed0 全部冻结；不得因实测贴线而放宽或换样本。
5. **单臂单变量**：本臂只改“救援配额=1”；α、缺腿口径、K、别名、内容、dense 资产全部零改。若同时改任一项，读数不可归因，按违规登记。

### 2.3 报告义务

§7 至少报告：设备融合/词面主集与 MRR；24 章逐章 lower/equal/higher 计数；零漂移两路 symdiff；bootstrap 的 delta/CI/p；**逐查询救援表**（query、rescueId、原第 5 槽 id、原第 5 槽是否为 expectedSlug、是否命中）；I10 四类调用方零变化；材料/节点计数与包 sha。未跑项写 `NOT_RUN` / `UNVERIFIED`。

---

## 3. 判官装置（独立预注册，与臂解耦）

- **重开条件**：dense-union §7.10② 明确要求“判官装置并集感知（独立预注册，与臂解耦）”（`docs/research/2026-10-22-dense-union-prereg.md:226`）。本臂据此重开，但**不把装置改动算作本臂改动**。
- **当前装置缺陷（事实）**：JVM 镜像 `RetrievalBenchmark.fusedRecall` 用 `byId.getValue(...)` 只映射词面召回集（`core/data/src/test/kotlin/…/knowledge/RetrievalBenchmark.kt:482`；ask 记 `:483`，当前工作树实读差 1 行，同一条语句）；救援/并集新增的 dense-only id 不在 `byId`，必抛 `NoSuchElementException`。批次 21 已实测该冲突（`docs/research/2026-10-22-dense-union-prereg.md:127`、`:225`）。
- **装置变更范围（独立预注册定义，本文件只引用）**：让镜像能解析 dense-only id 并按本臂 §1.4 的同一“第 5 槽替换”语义镜像；**不得**改判官断言、金标、评分口径或 `GoldenRetrievalJvmTest` 的测量语义。
- **解耦要求**：
  1. 装置预注册必须先落盘并登记路径 + sha256，再出本臂的 JVM/零漂移读数；
  2. 本臂的 §4 触碰清单**不含** `RetrievalBenchmark.kt`；该文件归装置预注册；
  3. 装置变更不得与本臂同批提交/同一 diff 归因；若装置与臂同时改，本臂读数作废。
- **没有装置预注册时**：零漂移记 `NOT_COMPUTABLE`，本臂**不判读、不采纳**；不得用旧 JVM 账本或代理 PASS 代替。

---

## 4. 回滚

### 4.1 触碰清单 + pre-sha（arm 面；当前工作树实算）

| # | 路径 | 动作 | pre-sha256 |
|---|---|---|---|
| 1 | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/DenseRescueSelector.kt` | **新增**端口（`denseOnlyRanked(subject, queryText, excludedIds): List<String>`，top 8） | —（新文件） |
| 2 | `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/dense/OnDeviceDenseRescueSelector.kt` | **新增**实现（复用 tokenizer/asset/encoder 与单条分数快照；全科 `nodeScores` + `DenseFusion.minMax`；未命中可重算） | —（新文件） |
| 3 | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/RoomKnowledgeBaseStore.kt` | 构造参数 + scoped 读口实现（召回 + 第 5 槽替换，§1.4） | `8cdbda580ef8b81952cf6cbb400abe7e20740a680a5edca97bcb9a386adc31de` |
| 4 | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/RoomStudyDatabase.kt` | 构造/委托新增可选参数 + scoped 读口 override | `db6e14ff804a0b41f1f9c8aead9c239b3ee28ad8ff53ad2aa1ddbfacbe523de6` |
| 5 | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabase.kt` | `StudyDatabaseFactory.open` 新增可选参数（默认 null） | `7210f974fbfd1f447e8165c5ef2becd05a8105787cf2c4a5a4a91e7f98ef8336` |
| 6 | `app/src/main/kotlin/com/tingyun/smartmistakebook/SmartMistakeBookApplication.kt` | 生产装配传入 `DenseRecallAssembly.rescueSelector(this)` | `20bc8de8ce6e7f928c0ad9bc52efbb71f054822524f92198208aba5e67efc219` |
| 7 | `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/dense/DenseRecallAssembly.kt` | 新增 `rescueSelector(context)`（`ENABLED` 同开关）+ `OnDeviceDenseRecallReranker` 写单条 `(subject, queryText, denseScores)` 快照 | `c654d988f9a8e730168c435b788e196c858691e9a3af50b2494c07ed57512683`（当前 `M`，v24 已采纳钉值） |
| 8 | `core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/GoldenRetrievalInstrumentedTest.kt` | **测量路径装配**：DB 加 `denseRescue = DenseRecallAssembly.rescueSelector(this)`；fused 探针改调 scoped 读口；lexical 探针仍走原方法（`queryText=null`）；**断言/阈值/金标零改** | `c2280255a9b0db2bc77ac1213e977be755a431efe80cfdf5b3c0c6e7bb7a4786` |
| 9 | `core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/RescueQuotaRecallTest.kt` | **新增**臂测试（§1.5 I1–I10） | —（新文件） |
| 10 | `core/database/src/test/kotlin/com/tingyun/smartmistakebook/core/database/DenseRescueSelectorTest.kt` | **新增** store 拼接/去重/降级测试 | —（新文件） |
| 11 | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabasePort.kt` | 新增 scoped 读口（默认实现回退原方法） | `dc1ba2e2dc1f65f5d3620e0582e4597e6c7ac919555855e9b3405cf9e78c05b7` |
| 12 | `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/RoomTutorToolRunner.kt` | `knowledgeRead` 改调 scoped 读口（其余零改） | `4c716754a8b04f4bdc609975bd0bfb793609a33b12950eaa9b81f612b1e186ae`（当前 `M`，渲染 v2 已采纳） |

- **不触碰（写死）**：`RetrievalBenchmark.kt`（装置，归 §3 独立预注册）；`GoldenRetrievalJvmTest.kt`（判官）；`DenseRecallReranker.kt`（`order()` 契约不变）；`DenseFusion.kt`（复用 `minMax`/`ALPHA`，不改）；DAO/SQL/schema；25 sidecar / 主包 / manifest；dense `.vec` / `.vec.json` / 夹具 / `model-manifest`；金标；别名/内容表；其它 recall 调用方（`RoomTutorKnowledgeContextLoader`、`RoomMistakeOrganizationRepository`、`MASTERY_READ` 聚焦解析）。
- **为什么需要新端口而不是改 `order()`**：`order()` 的契约是“只改次序、不改成员”（`DenseRecallReranker.kt:16-22`），救援要**换成员**；若把救援塞进 `order()`，必须破坏排列校验或改变主融合域，前者违反既有契约、后者违反“前 4 槽分数不变”。新端口把“换成员”局部化，关闭时零行为。
- **为什么新增可选构造参数而不是全局单例**：默认 `null` 让所有既有测试/调用方无需改动即可保持 v24 行为（`StudyDatabaseFactory.open` 现有调用点全部带默认参数，实读 `StudyDatabase.kt:415,430`）；救援只在生产装配点显式打开。

### 4.2 写前备份与回滚程序

**写前备份（执行轮第 1 步之前）**

1. 把 §4.1 第 3–8、11–12 项复制到 `build/agent-batch31/backup-rescue/`，落 `sha256sum` 全表。
2. **第 7、12 项必须单独备份**：两者当前都是已采纳但未提交的 `M`（第 7 项 = v24 dense 钉值 `c654d988…`；第 12 项 = 渲染 v2 `4c716754…`），HEAD 是各自改动前的旧字节。回滚目标是**当前工作树字节**，不是 HEAD；`git checkout --` 会丢掉 v24/渲染 v2，禁止用于这两项。

**回滚**

1. tracked 且 HEAD 即目标的第 3–6、8、11 项：`git checkout --`；第 7、12 项用 `backup-rescue/` 覆盖（目标 sha 分别为 `c654d988…`、`4c716754…`）；删除第 1、2、9、10 项新文件。
2. 核验：§4.1 逐文件 sha 与 pre 相同（第 7 项 = `c654d988…`、第 12 项 = `4c716754…`，不是 HEAD）；`DenseRecallReranker.kt` / `DenseFusion.kt` / `RetrievalBenchmark.kt` sha 与 pre 相同。
3. 不触碰 v24 已采纳的 dense 资产、内容表、sidecar、manifest；回滚只是代码级，不需要重建 dense。
4. 复跑编译门（`./gradlew :core:data:compileDebugUnitTestKotlin` + `:core:data:assembleDebugAndroidTest`，均 exit 0）；回滚后仍红则登记为他线/基线问题，不归因本臂。
5. §7 写明回滚原因与逐文件 sha；不得把“已回滚”写成“未采纳但保留改动”。

---

## 5. 预期（上限 +2；可能回归；不承诺分数）

| 项 | 值 | 依据 |
|---|---|---|
| v24 主集 | 117/129（12 MISS） | §0.2 |
| 可救援面 | 12 MISS 中只有 **3 条 absent**（`absent` = 词面 512 候选域内不存在）可能进 dense-only；其余 9 条在词面候选集内，已被 §1.2 排除，救援**结构上碰不到** | `docs/research/2026-10-23-kb-dense-union-estimate.md:19-20`；`v24-final.md` §四 |
| 结构上限 | **119/129（+2）** | 3 条 absent 中 2 条 dense 全科名次 ≤7（活结 2、力的分解 7），若它们是该查询的**最高融合分 dense-only**，可各救 1 条 |
| `等和线` | **不计入上限**（dense 全科名次 336，远在词面 512 域外；v24 四路仍 absent） | `docs/research/2026-10-23-kb-dense-union-estimate.md:47-49`；`v24-final.md` §四 |
| 回归风险 | **存在**：每个有 dense-only 的查询都会换第 5 槽；若原第 5 槽是该查询的唯一命中，该查询回退。净收益可能低于 +2，甚至为负 | §1.4；§2.1 规则 1/2 |
| 分数承诺 | **无**。上限是算术上界，不是预期读数；采纳只由 §2 五道门决定 | 本预注册 |

- 结构上限的 rank 读数是 **A0 代（3,245 节点）估算**；v24 为 3,280 节点，执行轮必须用 v24 资产复算 `活结/力的分解/等和线` 的 dense 全科名次，并在 §7 报四路单点；复算值不改变判据，只影响对“上限”的解释。
- 若实测为 117/129（与基线相等）或更低，按规则 1 **不采纳**；不得把“没有更差”写成通过。
- 本臂不主张 MRR、词面或跨章收益；MRR/词面只作报告项。

---

## 附录 A：执行轮 §7 必报字段（只追加，不改变 §1–§5 判据）

1. 写前/写后 porcelain 与 HEAD；§4.1 逐文件写后 sha；新增文件清单。
2. 命令与 exit code：双门编译、设备 `am instrument`、JVM 判官（装置预注册后）、零漂移、20k/seed0 bootstrap。
3. 主集/词面 Recall@5 与 MRR；24 章逐章 lower/equal/higher；零漂移两路 symdiff；bootstrap delta/CI/p。
4. 逐查询救援表：query、rescueId、原第 5 槽 id、原第 5 槽是否为 expectedSlug、替换后是否命中；`rescuedQueries` 计数。
5. 降级路径证据：selector=null / dense 不可用 / query 空 / 候选 <5 四类夹具的零变化。
6. 装置预注册路径 + sha；若无，零漂移记 `NOT_COMPUTABLE`。
7. 未跑项一律 `NOT_RUN` / `UNVERIFIED`，不得写成 PASS。
