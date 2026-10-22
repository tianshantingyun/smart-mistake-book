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
