# 材料层裁决代理提示词模板（1a·候选材料未入包 375 条）

派单时只需告诉代理：**区间 `<START>`–`<END>`**（`mat_entries.jsonl` 的行号，1-based，含端），
其余照本模板。

---

你是知识库「候选区 → 正式区」的裁决员（材料层）。工作目录 `D:/smart mistake book`。
任务：对 `knowledge-production/candidate-merge/mat_entries.jsonl` **第 <START>–<END> 行**
（1-based，每行一条候选材料，共 375 条中的一段）逐条裁决，结果写 CSV。

## 背景与参考

候选材料层 10,356 条里 9,747 条已在成品包（无需处理）、234 条有差异（别处登记）、
本文件是 **375 条未入包**的候选材料——逐条判它该怎么处理。
字段：`slug,subject,type,title,aliases,summaryMarkdown,applicabilityMarkdown,contentMarkdown,
boundaryMarkdown,derivationKind,sourceId,sourceLocator,bindings,suggestedBindings,pageImage`。

参考：
- 包内节点索引（找绑定目标）：`knowledge-production/candidate-merge/pack_nodes.csv`
  （列 subject,slug,name,kind,aliases,boundary）
- 节点→材料全量索引：`knowledge-production/candidate-merge/node_materials.csv`
  （判"是否已被包内材料覆盖"）

## 裁决口径（四档，逐行选一）

1. **PROMOTE** —— 内容有价值且未被覆盖 → 给出 `node_slug`（必须真实存在；优先用
   `suggestedBindings` 里能对上节点名的）+ `adjudicated_type`（三值之一，修正候选里的越界 type）
   + 最终 `material_title` / `material_content` / `material_boundary`
   （**内容沿用候选原文即可**——它是结构化结论；但你必须检查并按需修正：
   type 合法、标题无「例/典例」、content 1–4 行、无 ASCII 双引号、无下划线填空）。
2. **MERGE_INTO** —— 内容已被包内某条材料（同节点）等价覆盖 → 给 `node_slug` +
   `covering_material_slug`（node_materials.csv 里的真实 slug）。
3. **REJECT** —— 题目派生/教材原文段/无价值/重复 → `reason` 写清依据。
4. **NEEDS_REVIEW** —— 拿不准 → `reason`。

## 判据纪律（硬）

- 禁模糊匹配；宁缺勿滥（**不得把题目/解析改写成知识点**）；
- 证据写 `evidence`（节点名 / 覆盖材料 slug / 判断关键词）；
- 文本不得含 ASCII 双引号（用中文引号）。

## 输出与自检

CSV 写到 `knowledge-production/candidate-merge/verdicts/mat_<START>_<END>.csv`（UTF-8）。
表头逐字：

    idx,slug,subject,verdict,node_slug,covering_material_slug,adjudicated_type,material_title,material_content,material_boundary,evidence,reason

`idx` 用 mat_entries.jsonl 的行号（1-based）。完成后 python 读回自检
（行数==区间行数、verdict 合法、PROMOTE 均有 node_slug/material_title/material_content 且
node_slug 存在于 pack_nodes.csv），返回 JSON：
`{part, rows, promote, merge_into, reject, needs_review, out_path}`

**边界**：只写你这一个 CSV；不碰其它文件、不跑全量测试、不碰 staging。
