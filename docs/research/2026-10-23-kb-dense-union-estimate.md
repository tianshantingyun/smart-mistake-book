# dense-union 前置实测：dense 全扫名次（2026-10-23 预注册的估计，实测日 2026-10-25）

> 回答 `docs/research/2026-10-22-dense-union-prereg.md` 的前置问题：**dense 全扫能否看到 4 个
> absent 节点**（镁的化学性质、等和线、活结与死结模型、力的分解），以及 29 条 A0 融合 MISS
> 期望节点的 dense 名次分布——按 K≈64 口径给 go/no-go。**这是估计，不是臂**：只报名次，
> 不调参、不扫 K、不改任何生产文件；两份预注册（dense-union §5、d3-round3 A1）结果节均不动。

## 1. 口径（全部可复算）

- **读取实现**：`tools/dense_build/dense_asset.py`（`.vec` 权威读取定义，`load_vector_file`
  还原 int8×per-vector-scale → float32）。行已 L2 归一（实测还原范数 min 0.99765 / max
  1.00269），点积即余弦——与端侧同式（`DenseVectorAsset.kt:34-42`「行已 L2 归一 ⇒ 点积即
  余弦；还原 = int8 × per-vector scale」）。
- **查询向量**：`core/data/src/test/resources/dense/golden-query-vectors.i8`（129 条，[f32]
  行，**原文无前缀**——与设备生产同变换，`export_golden_query_vectors.py:78-107`；批 11 设备
  探针锚：设备↔本文件 min cosine 0.999776 / median 0.999834，fixture 头部第 6 行）。
- **节点分 = 行 max**（`DenseRecallAssembly.kt:13`「与资产里全部向量做点积 → 节点取 max」）。
- **名次**：节点分降序、1-based。**主口径 = 全科排序**（3,245 节点全体）；辅助口径 = 同科排序
  （生产 `nodeScores(query, subjectKey)` 的实际域，`DenseVectorAsset.kt:51-56`）。平分按节点
  布局序稳定排序（实测无平分影响名次的情形）。
- **A0 账本**：`build/agent-batch15/d3/a0-device-fused-misses.txt`（29 条设备融合 MISS，sha
  `27f1c06e62200cb9…`；4 条 `rank=absent` = 词面 512 候选域内不存在，与批次 16 弱章诊断一致，
  `docs/kb-batch16-2026-10-22.md:29`）。逐条按（科目, 题面）精确匹配金标 129 条并校验
  expectedSlug 一致（脚本内 assert，29/29 命中、0 冲突）。

## 2. 资产身份（本批收束后；三方一致由脚本当场校验）

| 件 | 身份 |
|---|---|
| `.vec` | `core/data/src/main/resources/knowledge/dense/bge-small-zh-int8.vec`，sha256 **`0536d0d1cda99bd5614bc94d9176628d7ce999938db1c289b3b44600ae13b23a`**；44,041 行 = 3,245 原子节点（canonical）+ 40,796 别名；dim 512 |
| 旁车 / Kotlin 常量 | `…vec.json` sha256 与 `DenseRecallAssembly.VECTOR_ASSET_SHA256`（`DenseRecallAssembly.kt:107`）同值（脚本 assert 三方一致） |
| 成品包 | `moe-2025-four-subjects-v1.json` sha256 `298f4c0c41519d51…`（批次 19 A1 promote v20 后；A1 前备份 `build/agent-batch19/backup/` 实测 sha `f6098c41d4901d56…`） |
| 布局对拍 | 包内 `atomic_layout` 行序 == `.vec` ids 逐行一致（脚本 assert，44,041 行）；taxonomy = `moe-2025-four-subjects-v1` |
| 金标题面 | `tools/kb_coverage/tables/golden_queries_v2.json`，sha `eabef102…`（`dense_asset.GOLDEN_SHA256` 封存读） |
| 查询向量 fixture | sha256 `6411754368c3a769…`，129 条 |

基线口径注：预注册 §3 要求「A1 之后的 A0」——A1 已采纳（v20），本估计用的正是 A1 后资产。
其中**仅** `镁的化学性质` 拿到 A1 别名（对拍 A1 前备份：+`镁着火不能用二氧化碳灭火器`）；
`等和线 / 活结与死结模型 / 力的分解` 三节点的向量面 A1 未触碰（同上对拍 added=[]），其名次
不受 A1 臂影响。

## 3. 结果 A：4 条 absent 的 dense 全扫名次（1-based / 3,245 节点）

| 期望节点 | dense 全科名次 | 同科名次 | 节点分 | A0 融合名次 |
|---|---|---|---|---|
| 镁的化学性质（CHEMISTRY） | **1** | 1 | 0.8912 | absent |
| 活结与死结模型（PHYSICS） | **2** | 2 | 0.6507 | absent |
| 力的分解（PHYSICS） | **7** | 7 | 0.5843 | absent |
| 等和线（MATH） | **336** | 182 | 0.4986 | absent |

4 条期望节点**全部存在于当前 `.vec`**（无「>总节点数」型 absent）；absent 只是词面候选域意义上的。
K≈64 口径下 **3/4 结构上可召回**（名次 1/2/7，余量极深，对 K 在 8–128 区间内不敏感）；
`等和线` 在 K=64、甚至 K=128/182/336 之外——dense 全扫**救不了**它。

## 4. 结果 B：29 条 A0 融合 MISS 期望节点的 dense 名次分布

| 范围 | n | 中位 | 最好 | 最差 | ≤64 | ≤128 | ≤5 |
|---|---|---|---|---|---|---|---|
| 29 条全量 · 全科 | 29 | 4.0 | 1 | 336 | **26** | 27 | 17 |
| 29 条全量 · 同科 | 29 | 3.0 | 1 | 182 | **26** | 28 | 17 |
| 4 条 absent · 全科 | 4 | 4.5 | 1 | 336 | 3 | 3 | 2 |
| 25 条域内 · 全科 | 25 | 4.0 | 1 | 169 | 23 | 24 | 15 |
| 25 条域内 · 同科 | 25 | 3.0 | 1 | 100 | 23 | 25 | 15 |

域内 25 条中 dense 名次 >64 的只有 2 条：`判别式法…`（全科 108 / 同科 100）、
`电源的内阻…`（全科 169 / 同科 90）——这两条本就在词面 512 域内，不依赖并集救。
29 条逐行明细（slug / A0 融合名次 / dense 全科 / 同科 / 节点分）：
`build/agent-batch19/dense-union-estimate/dense-union-estimate.json` 的 `missRows`；
金标 129 条全表：同目录 `golden-129-dense-ranks.json`。

## 5. Go / No-Go（K≈64 口径）

**结论：go——值得推进跨线施工（core/database 并集臂）。** 理由：

1. **结构召回成立**：3/4 absent（镁=1、活结=2、力的分解=7）在 dense 全科名次 ≤7，远在 K=64
   之内；并集（词面 512 ∪ dense 64）对 A0 的 29 条 MISS 结构覆盖率 = 25（域内）+ 3 = **28/29**。
   以 A1（v20，JVM 融合镜像 117/129、12 MISS）为新基线计：12 条 MISS 中 2 条 absent
   （活结/力的分解）结构上可救，1 条（等和线）不可——**单独靠并集的主集上限约 119/129**，
   等和线需按预注册 §4 预案转内容侧（1 条，不是预注册担心的 3 条）。
2. **无新增算力面**：生产 `DenseVectorAsset.nodeScores(query, subjectKey)`（`DenseVectorAsset.kt:51-56`）
   **本就每次查询全扫同科全部向量**并产出全体节点分，只是融合时弃用候选集之外的分数——
   并集臂是 store 返回成员的变更（预注册 §2 的 core/database 面），不新增编码/扫描开销；
   同科平均节点数约 811（3,245/4 科）。
3. **风险与门槛不变**（引用预注册 §3/§4，本估计不替代）：结构召回 ≠ 融合 top-5——dense-only
   候选缺词面腿，`DenseFusion` 缺腿归一化（core/data 面）决定它们融合后能否进前 5，**本估计
   读不出融合后名次**；臂仍须过主集严格更高 + 24 章逐章不劣 + 负向守卫 + 零漂移对称差 0 +
   显著性 20k/seed 0 + p50/p95 门，且全部既有钉值按实测重钉、独立成臂。

## 6. 复算命令（仓库根）

```
python build/agent-batch19/scripts/dense_union_estimate.py        # 全部对拍 assert + 名次计算
```

读数落盘：`build/agent-batch19/dense-union-estimate/dense-union-estimate.json`（汇总 + 29 条
明细 + absent 明细）、`build/agent-batch19/dense-union-estimate/golden-129-dense-ranks.json`
（129 条全表）。脚本内置对拍门（任一失败即非零退出）：.vec sha 三方一致、行范数≈1、
atomic_layout 行序==ids、金标封存 sha、fixture 129 条、A0 账本 29/29 匹配且 expectedSlug
零冲突、4 absent 集合与预注册一致。

## 7. 未验证项与边界

- 未实测端侧全扫延迟（无需：全扫已在现生产路径内，见 §5-2；延迟门归臂本身）。
- 未跑融合臂——融合后名次、逐章、显著性等一律未测，本文件不构成任何臂结论。
- 名次基于离线 fixture 查询向量（设备对拍锚 min 0.999776，§1）；设备向量逐条差异未在本轮复测。
- 平分处理为「分降序 + 节点布局序稳定」的确定性约定；未做平分敏感性分析（3,245 个 float32
  点积分里未出现影响名次的平分）。
- 报告路径沿用预注册任务指定的 `2026-10-23` 文件名（实测执行日 2026-10-25）。
