# 判定表节点漂移复算报告（2026-09-28）

## 问题

交接中出现「判定表 134 行节点漂移」的计数。本报告用五个互斥口径重新测量
「判定行 node_slug 与当前成品包（`build/kb-staging/moe-2025-four-subjects-v1.json`）不吻合」的规模，
结论：**134 不复现，是 09-20 中间态的过期计数**。

## 五口径复算（对 `material_judgments.csv` 全部 16,625 行，MATERIAL 16,519 行）

| 口径 | 判据 | 行数 |
| --- | --- | --- |
| 跨科 | node_slug 在成品包中存在、但科目与块所属科目不同 | **0** |
| 改名 | (subject, node_slug) 命中 `point_rename.csv`（节点改名表） | **1,240** |
| 换章 | (subject, node_slug) 命中 `point_relocation.csv`（节点换章表） | **489** |
| 合并 | (subject, node_slug) 命中 `point_merge.csv` 的 merged_slug | **12** |
| 删除 | node_slug 在成品包中不存在（删除口径；今日状态见下注） | **1**（交接值） |

- 改名/换章两口径是"该行节点经历过改名/换章"的度量，不是缺陷——slug 已随改名/换章表同步进包，
  `materialize --dry-run` 对这些行零报错。
- 合并口径的 12 行 = 今日 `materialize --dry-run` 报「节点不存在」的全部 12 行（9 个 slug：
  MATH 逆序相加法求数列的前n项和 ×4、试纸的使用方法、水电离出的c(H+)（或c(OH-)）的计算、
  装置气密性检查方法、游标卡尺的读数、三角形解的个数问题、库仑力作用下的平衡、
  分式不等式及其解法、三角形垂心的向量特征）。已由 `tools/kb_coverage/reconcile_judgments.py`
  按 point_merge.csv 重指后继 slug。
- 删除口径：今日按机械判据（node_slug ∈ `point_delete.csv`、或 slug 不在包且不在合并表）均为 **0**；
  唯一可解释为「节点删除、仅别名存活」的 1 行是 MATH/三角形垂心的向量特征（它同时也在合并表，
  作为别名保留在平面向量与三角形的垂心名下）。

## 134 不复现

- 今日状态：`materialize --dry-run` 报「节点不存在」= 12 行（= 合并口径），其余口径均不为 134。
- 09-20 快照（`bd417874` 的 `material_judgments.csv`）对今日成品包复算：同样只有 12 行
  slug 不在包内（同一批 9 个 slug），chunk 缺失 0 行。134 在任何可重放的状态下都不出现。
- 结论：134 是 09-20 中间态（包与判定表迁移中途）的一次性计数，成品包当前态与其不吻合；
  今日可信口径只有 12 行需修复，且已修复。判定表对账工具 `reconcile_judgments.py`
  会把未来的「节点不存在且无合并后继」列为阻塞项要求升级，防止此类计数再次积压。
