# 块池判定员派单模板（53 知识清单优先轮；Agent 通道用）

派单时告诉代理 **切片文件名 `<SLICE>`**（如 `2027版高中《53知识清单》彩色版（数学）__3.jsonl`），其余照本模板。

---

你是知识库块池的**判定员**。工作目录 `D:/smart mistake book`。

## 先读规则（逐条遵守）

`docs/kb-chunk-judgment-protocol.md`——冻结判定规则 + **v1.1 增补**（扫描件 heading 是页描述串、
装饰块直接 SKIP、53 知识清单最高优先、学生版填空原文不得入库）。协议里 §一/§二/§三 就是本任务的验收口径。

## 你的切片

`tools/kb_coverage/tables/judgment_slices/<SLICE>`（JSONL，每行：`chunk_rel, chunk_id, heading, text, subject, fp`）。
块来源以切片里的 `chunk_rel` 为准（`2027版高中《53知识清单》彩色版` / `2026年新高考资料(1)(3)(4)` /
`27版五三` / `6. 2026生物总复习` 等）。两条**按来源条件适用**的规则：

- **只有扫描件来源**（53 四本）适用协议 v1.1 第 1 条：`heading` 是页描述串，考点名一律从**正文**读出；
  非扫描来源（docx/pptx）的 heading 通常是真实讲次/页签名，可作定位但仍以正文为准。
- **53 四本是最高优先来源**：判定它时多看一眼"这里有没有正式区还缺的考点"，缺则按规则 7 提建点提案；
  其它来源同样按规则 7 办事，只是没有"多看一眼"的额外义务。

## 判定与落盘

对**每个块**判 `MATERIAL`（入库）或 `SKIP`（显式放弃），写成 CSV：
`knowledge-production/judgment-verdicts/<SLICE>.csv`（目录若不存在请创建；**UTF-8**）。
表头逐字（12 列，与协议 §三 一致）：

    chunk_rel,chunk_id,action,node_slug,type,title,summary,applicability,content,boundary,note,midx

- `MATERIAL` 行：`node_slug` 必须是**正式包里真实存在的节点 slug**——**直接 grep 工具包**
  `build/agent-input/judge-kit/nodes_<SUBJECT>.tsv`（列：`topic_slug / node_slug / node_name /
  material_count`，材料数按侧车实算；按块 `subject` 取对应科目的那个文件）。
  不要自己去解析 4MB 的主包 JSON（抄错 slug 就是整行作废）；`type` 仅
  `CONCEPT_EXPLANATION`/`METHOD_MODEL`/`MISCONCEPTION_GUIDE`；
  `title`/`summary`/`applicability`/`content`/`boundary` 五字段齐备；`applicability` 写清 PRIMARY 适用场景；
  `content` **1–4 行**（多行用字面 `\n` 两字符分隔，字段内不得出现真实换行）；`boundary` 是真边界（不得是"定位：…"占位）；
  同一节点在**本切片内**最多给 4 条材料；`midx` 只在同一块出多条材料时用（`''`/`b`/`c`…）。
- **找不到对口节点**：不要硬绑近似节点——`action` 写 `SKIP`，`note` 写 `NEW:<subject>/<建议slug>`（建点提案）。
- `SKIP` 行：`note` 写理由（题目派生/装饰页/无结论/重复…），其余字段留空。
- 薄料节点优先：`build/agent-input/judge-kit/thin_nodes.tsv`（按**当前包**重算，不再照旧的
  `node-material-gaps-2026-09.csv` 找点——它此前记着已被合并、包内不存在的 slug）。
  已满节点（`material_count` ≥ 4，见 `overfull_nodes.tsv`）**不禁止**绑定（首轮 73% 的 MATERIAL 行
  绑在满节点上，可见性是单独的收口项），但同等对口时优先薄料节点。

## 硬禁令（违反即整批作废）

1. **题目/题干/成套选项/答案/解析不得成为材料**（改写得像知识点也不行）；
2. 不得抄教材原文，尤其**不得把学生版填空下划线原样搬进材料**——材料必须是你写的结构化结论；
3. 禁止词面匹配：必须**读块正文**再判（扫描件 heading 常是页描述串）；
4. 文本不得含 ASCII 双引号（用中文引号）、不得含控制字符；
5. 一条记录一行（CSV 用 `csv.DictWriter` 写，不要手工拼串）。

## 边界与返回

- 只写你这一个 CSV；不碰 staging（`build/`）与其它文件；不跑全量测试。
- 写完后 python 读回自检（行数 == 切片行数、表头逐字一致、MATERIAL 行五字段齐备且 node_slug 存在于包里、
  无 ASCII 双引号、无真实换行），然后返回 JSON：
  `{slice, rows, material, skip, new_proposals, out_path}`
