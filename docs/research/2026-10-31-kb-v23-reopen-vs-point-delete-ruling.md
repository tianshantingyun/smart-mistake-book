# KB v23：重开建点 vs 批次13「空点撤回」台账 · 条件性回撤裁决（2026-10-31）

> 性质：append-only 台账的**记名例外**（不是静默改写）。本文档先落盘，再动数据；
> 改后只追加 §7（结果与 post-sha），不回改 §1–§6。
> 仓库：`D:/smart mistake book`；HEAD `d1bacec5a58c4cf7d9dfddd747f31ed2a166acc5`。

---

## 1. 委托来源

本轮 `escalate` 的裁决答复（主代理转述用户授权原文，2026-10-31 收到）：

- 用户 2026-10-29：「不需要我来复核，你直接判断」
- 用户 2026-10-30：「全权决定，把任务做完，自己找方法」
- 本轮裁决原文：「授权删除（34 行 point_delete + 34 条 DELETE 退役记录），按以下五条执行，缺一不可……」

本文件即该裁决第 1 条要求的「先写决策文档再动数据」。第 4 条要求的
`alias_dropped` +1 偏差登记写进批报，不在本文改判定规则。

---

## 2. 冲突事实

v23 的 35 个 BUILD 节点中，**34 个**的 slug 已在 `tools/kb_build/tables/point_delete.csv`
里各有一行「撤回」记录，同时在 `build/kb-staging/moe-2025-update-manifest.json` 的
`retired` 账本里各有一条 `kind=DELETE`（同一个 nodeId）。第 35 个
（`MATH / 抽屉原理`）是本次新增，不在撤回账内。

- `tools/kb_build/check_pack_contract.py:315-317`：point_delete 行要求 slug **不在**包内；
  v23 建点后这 34 个 slug 在包内 → `promote --dry-run` 报 `point_delete 未生效`（34 条，
  报告 `_truncate` 只显示前 20）。
- 若只删表行：`check_pack_contract.py:350` 会反向报「台账有账、表无行」。
  因此两处必须成对处理。
- `tools/kb_build/update_manifest.py` 文档与模块契约：`retired` 是「**只增的账本**」。
  本裁决对它做的是**一次有文档、有哈希、理由与原台账一致的条件性回撤**，不是常态。

---

## 3. 原台账自带的「条件理由」原文（逐字）

两处（`point_delete.csv` 的 `reason` 列、manifest `retired[].reason`）文本相同：

> 批次13 空点撤回：判定表无任何 MATERIAL 行指向该节点（待后续配材后按 new_points_manual 重建）；批次13 空点收口 2026-10-19

即：这批撤回**不是永久删除**，而是「无材料 → 先撤回 → 配材后按 `new_points_manual` 重建」的
条件性动作。`docs/kb-batch15-2026-10-21.md:258` 记「是否按 new_points_manual 逐点重建/重判
需用户裁定」；本轮用户授权（§1）即该裁定。

---

## 4. 条件已满足的证据

1. **材料条件**：v23 的 41 条 flip 行（35 BUILD + 6 MERGE）已在五片判定表写全
   `node_slug/type/title/summary/applicability/content/boundary`；`materialize` 对本批 41 行
   `plan: material 41 / skip 0 / errors 0`，`write` 落 staging 后包材料 `64,973 → 65,014`（+41）。
2. **建点条件**：35 个 BUILD 节点全部建入 staging，`create_points` 实跑
   `selected 35 / created 35 / skipped 0 / errors []`；包节点 `3,245 → 3,280`（+35）。
3. **落点条件**：这 34 个 slug 现在都有 MATERIAL 行指向（每条材料的 `bindings[0]`
   为对应 nodeId，role=PRIMARY），正是原理由所等待的「配材后重建」。
4. 因此原条件「判定表无任何 MATERIAL 行指向该节点（待后续配材后按 new_points_manual 重建）」
   已不再成立；台账继续保留 DELETE 会与包事实相反。

---

## 5. 处置范围（只动这 34 条，双向相等）

删除集合 = 下列 34 个 `(subject, slug)` / nodeId，**双向相等**（表行 34 行、台账 34 条，
不多不少）：

| # | subject | slug | nodeId |
|---:|---|---|---|
| 1 | BIOLOGY | 泛素-蛋白酶体途径 | `kb:moe-2025-four-subjects-v1:biology:atomic:泛素-蛋白酶体途径` |
| 2 | BIOLOGY | 牵涉痛与会聚学说 | `kb:moe-2025-four-subjects-v1:biology:atomic:牵涉痛与会聚学说` |
| 3 | BIOLOGY | 疼痛的闸门控制学说 | `kb:moe-2025-four-subjects-v1:biology:atomic:疼痛的闸门控制学说` |
| 4 | CHEMISTRY | 净反应速率与反应方向判断 | `kb:moe-2025-four-subjects-v1:chemistry:atomic:净反应速率与反应方向判断` |
| 5 | CHEMISTRY | 分子筛的硅铝比与孔径筛分作用 | `kb:moe-2025-four-subjects-v1:chemistry:atomic:分子筛的硅铝比与孔径筛分作用` |
| 6 | CHEMISTRY | 动力学同位素效应 | `kb:moe-2025-four-subjects-v1:chemistry:atomic:动力学同位素效应` |
| 7 | CHEMISTRY | 常见气体的定量吸收剂 | `kb:moe-2025-four-subjects-v1:chemistry:atomic:常见气体的定量吸收剂` |
| 8 | CHEMISTRY | 快离子导体 | `kb:moe-2025-four-subjects-v1:chemistry:atomic:快离子导体` |
| 9 | CHEMISTRY | 溶剂对酸强度的影响 | `kb:moe-2025-four-subjects-v1:chemistry:atomic:溶剂对酸强度的影响` |
| 10 | CHEMISTRY | 逆流萃取与单级萃取的比较 | `kb:moe-2025-four-subjects-v1:chemistry:atomic:逆流萃取与单级萃取的比较` |
| 11 | CHEMISTRY | 酮式-烯醇式互变异构 | `kb:moe-2025-four-subjects-v1:chemistry:atomic:酮式-烯醇式互变异构` |
| 12 | MATH | 两数列归并后的项定位与求和 | `kb:moe-2025-four-subjects-v1:math:atomic:两数列归并后的项定位与求和` |
| 13 | MATH | 伯努利不等式 | `kb:moe-2025-four-subjects-v1:math:atomic:伯努利不等式` |
| 14 | MATH | 偏导数 | `kb:moe-2025-four-subjects-v1:math:atomic:偏导数` |
| 15 | MATH | 函数的不动点与稳定点 | `kb:moe-2025-four-subjects-v1:math:atomic:函数的不动点与稳定点` |
| 16 | MATH | 函数零点的求积问题 | `kb:moe-2025-four-subjects-v1:math:atomic:函数零点的求积问题` |
| 17 | MATH | 反证法 | `kb:moe-2025-four-subjects-v1:math:atomic:反证法` |
| 18 | MATH | 向量积的模 | `kb:moe-2025-four-subjects-v1:math:atomic:向量积的模` |
| 19 | MATH | 拉格朗日乘数法 | `kb:moe-2025-four-subjects-v1:math:atomic:拉格朗日乘数法` |
| 20 | MATH | 敏感性问题调查的随机化回答法 | `kb:moe-2025-four-subjects-v1:math:atomic:敏感性问题调查的随机化回答法` |
| 21 | MATH | 数列的有界性及其判定 | `kb:moe-2025-four-subjects-v1:math:atomic:数列的有界性及其判定` |
| 22 | MATH | 数阵分组问题 | `kb:moe-2025-four-subjects-v1:math:atomic:数阵分组问题` |
| 23 | MATH | 曲率 | `kb:moe-2025-four-subjects-v1:math:atomic:曲率` |
| 24 | MATH | 曲线的上切线与下切线 | `kb:moe-2025-four-subjects-v1:math:atomic:曲线的上切线与下切线` |
| 25 | MATH | 椭圆的仿射变换 | `kb:moe-2025-four-subjects-v1:math:atomic:椭圆的仿射变换` |
| 26 | MATH | 欧拉函数 | `kb:moe-2025-four-subjects-v1:math:atomic:欧拉函数` |
| 27 | MATH | 点与直线的位置关系 | `kb:moe-2025-four-subjects-v1:math:atomic:点与直线的位置关系` |
| 28 | MATH | 直线划分平面区域 | `kb:moe-2025-four-subjects-v1:math:atomic:直线划分平面区域` |
| 29 | MATH | 错排问题 | `kb:moe-2025-four-subjects-v1:math:atomic:错排问题` |
| 30 | MATH | 阳马 | `kb:moe-2025-four-subjects-v1:math:atomic:阳马` |
| 31 | MATH | 零点差问题 | `kb:moe-2025-four-subjects-v1:math:atomic:零点差问题` |
| 32 | MATH | 鳖臑 | `kb:moe-2025-four-subjects-v1:math:atomic:鳖臑` |
| 33 | PHYSICS | 一般曲线运动的曲率半径 | `kb:moe-2025-four-subjects-v1:physics:atomic:一般曲线运动的曲率半径` |
| 34 | PHYSICS | 循环运动稳定速度的能量分析 | `kb:moe-2025-four-subjects-v1:physics:atomic:循环运动稳定速度的能量分析` |

执行顺序（裁决第 3 条）：**先删台账 DELETE 记录，再删表行**；随即跑
`check_pack_contract.py` 的 315-317 与 350 两处检查（必须同时绿），再跑
`promote --dry-run`（四项全绿 + 零写盘 sha 对拍）。任一非本 34 行的门红 → 不扩大改动面，
按 blocked 停在 release 写盘前。

---

## 6. 改前 sha（工作树字节）

| 文件 | 改前 sha256 | 改前规模 |
|---|---|---|
| `tools/kb_build/tables/point_delete.csv` | `fc27ee664e5160b0115302290a96b00f62b9afe86dba3609bbb003449bf3e507` | 283 数据行 |
| `build/kb-staging/moe-2025-update-manifest.json` | `93c178ddede171f8cb6b9cc93577b94adf4c43ab43332475720c7242ef092827` | version 22 / retired 1,256 |

（staging 台账是 gitignored 工作副本，release 台账此刻未动：`c4d38750…deced3e0`。）

---

## 7. 执行结果（改后追加）

### 7.1 执行顺序与删改数（2026-10-31）

按裁决第 3 条：**先删 staging 台账 DELETE 记录，再删表行**。

| 对象 | 改前 | 改后 | 删除集合 |
|---|---|---|---|
| `build/kb-staging/moe-2025-update-manifest.json` 的 `retired` | 1,256 条 | **1,222 条** | 34 条 `kind=DELETE` |
| `tools/kb_build/tables/point_delete.csv` | 283 数据行 | **249 数据行** | 34 行 |

双向相等断言（脚本 `build/agent-batch28/scripts/drop_withdrawn_rows.py` 实跑）：

```text
assert: 删行集合双向相等；其余行逐行未变
```

- 表侧删行集合 == 台账侧删 nodeId 集合 == §5 的 34 个 `(subject, slug)`，三者相等；
- 未删行按原顺序逐行相等（无位移、无改写）；
- 只删 `kind=DELETE` 且命中这 34 个 id 的台账条目；34 个节点均无 MERGE 条目被触碰。

### 7.2 两处一致性检查（删后立即跑，同一口径）

```text
check_pack_contract: 全绿（point_delete 两向均无问题）
```

即 `check_pack_contract.py:315-317`（行 → 包）与 `:350`（台账 → 表）**同时**绿；
其余 consistency 桶无新增问题。

### 7.3 promote --dry-run（零写盘）

```text
OK gates / OK consistency / OK roundtrip / OK manifest：version→23，压平 0 条
（dry-run：门全绿，未写盘；将写入 version 23）
```

跑前/跑后对 release 28 个文件与 staging 28 个文件逐文件 sha 对拍：两侧 28/28 相同
（`build/agent-batch28/rebuild/promote-dry2-pre.json` == `promote-dry2-post.json`）。

### 7.4 promote 真写

```text
已晋升 28 个文件 → core/data/src/main/resources/knowledge
  内容戳 → 868a177d1de4f97a
  主包 sha256 → 051141e2a05cecc821931bba139cd919b3a205cabfb99439bebc0be0867b1be7（staging == release 已对拍）
  version → 23（压平 0 条链）
```

写后自检：release 与 staging 的 28 个文件 sha **28/28 相同**；sidecar 聚合
`dac000f2…73ce4f` 两侧相同。release 台账 version 23 / schema 2 / retired 1,222 /
contentVersion `868a177d1de4f97a`。

### 7.5 改后 sha（工作树字节）

| 文件 | 改前 sha256 | 改后 sha256 |
|---|---|---|
| `tools/kb_build/tables/point_delete.csv` | `fc27ee664e5160b0115302290a96b00f62b9afe86dba3609bbb003449bf3e507` | `aa8a8350ed3ab9ca7faa56b99fb1511539409867eecbbced24b6de9de7ff6f5b` |
| staging 台账（promote 前） | `93c178ddede171f8cb6b9cc93577b94adf4c43ab43332475720c7242ef092827` | `e057429ee9110bcfb1abf679dd71afb78afa3a5241292834e5231ebbb2fef6d6` |
| release 台账（promote 后） | `c4d3875049dab731bae7dfdeb960a3b35473cf706e48d71a1c8b7ff8deced3e0` | `14fe7b3af9c9fef2c48a629bb836d1d3715316dfd62b5ff9fc791340057308a7` |

### 7.6 边界

本裁决只覆盖 §5 的 34 行/条。v23 其余事实（41 材料、35 建点、+6 别名、R6 29 条重放、
version 23）按预注册与 exec-plan 执行，不在本文扩张。`alias_dropped` 22,559 → 22,560
（+1）已在批报「偏差登记」单列，不改判定规则。
