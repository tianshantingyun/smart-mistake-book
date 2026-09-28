# 知识库块池全量判定协议（P0.1 定稿）

本文是块池"全量判定入库"的**冻结判定规则与落盘格式**。所有判定代理（切片判定轮）与合并/预筛工具
都以此为准。规则编号与 2026-09-28 P0.1 冻结版一致，后续修改须显式改本文件并升级版本。

涉及文件（现状实测值，2026-09-28）：

- 块池：`tools/kb_coverage/tables/extracted_chunks.jsonl` — 186,635 行；排除已判定键
  （`material_judgments.csv` 命中 11,745 个）后**未判定 174,890 行 / 171,461 个唯一键**
  （174,890 − 3,429 个重复键行）。
- 预筛：`tools/kb_coverage/prescreen_chunks.py`（机械分流，只报告不写块池），
  报告 `tools/kb_coverage/tables/prescreen_report.json`。
- 判定表：`tools/kb_coverage/tables/material_judgments.csv`（判定行的唯一落盘处）。
- 薄料节点清单：`knowledge-production/node-material-gaps-2026-09.csv`（1,232 节点）。
- 建点提案：`tools/kb_build/tables/new_points_manual.csv` → `tools/kb_build/create_points.py`。

---

## 一、判定规则（冻结版）

1. 材料 type 仅 3 值：`CONCEPT_EXPLANATION` / `METHOD_MODEL` / `MISCONCEPTION_GUIDE`；
   其余值禁用（`WORKED_EXAMPLE`/`COMPLETE_SOLUTION`/`DERIVATION`/`REPRESENTATION_GUIDE` 一律不用）。
2. 只要知识点不要题目：题目/题干/成套选项/答案/解析不得成为材料，发现即 SKIP。
3. 每条材料恰好 1 条 PRIMARY（applicability 表达）。
4. content 1–4 行、自包含可直接用。
5. 每条材料 ≥1 条已核实引文锚（title/summary 能溯源到来源件与位置）。
6. 每个知识点节点 ≤4 条材料（全局上限在 P2 收口；切片内同一节点最多 4 条）。
7. 找不到对口节点不硬绑：note 记 `NEW:<subject>/<建议slug>` 建点提案，走
   `tools/kb_build/tables/new_points_manual.csv` → create_points 通道；建完从提案表删行防重放。
8. 边界（既有裁定，不动）：教材原文不入库；题目/答案/解析不成为材料；不做学生可见对象。
9. 判定行 schema：`chunk_rel,chunk_id,action(MATERIAL|SKIP),node_slug,type,title,summary,applicability,content,boundary,note`。
10. 薄料节点优先补（`knowledge-production/node-material-gaps-2026-09.csv`，1,232 节点）。
11. 语义判定必须亲自读块内容，禁止词面匹配。

---

## 二、判定行 schema

判定表 `tools/kb_coverage/tables/material_judgments.csv`，UTF-8（读侧兼容 BOM，写侧不带 BOM，
`newline=""` 交给平台），每行一个块（一个块可出多条材料时用 `midx` 区分）。

| 列 | 语义 | 约束 |
| --- | --- | --- |
| `chunk_rel` | 块来源件路径（相对仓库根，如 `2026年新高考资料/…/1.1集合的概念（讲义）（学生版）.docx`） | 必须与块池 `rel_path` 逐字一致；写错会整行被 materialize 拒 |
| `chunk_id` | 块 id（`sha256(rel_path)[:10]-序号`，如 `9a51501f22-001`） | 与 `chunk_rel` 成对唯一；哈希前缀可反查 rel_path |
| `action` | `MATERIAL` 或 `SKIP` | 只有 `MATERIAL` 会入库；`SKIP` 是显式裁定，同样落表防重判 |
| `node_slug` | 绑定知识点 slug | MATERIAL 行必填；找不到对口节点时不得硬绑，写 `NEW:<subject>/<建议slug>`（见规则 7） |
| `type` | `CONCEPT_EXPLANATION` / `METHOD_MODEL` / `MISCONCEPTION_GUIDE` | 其余 4 值禁用（规则 1） |
| `title` | 材料标题 | 须含可溯源引文锚（规则 5） |
| `summary` | 一句话摘要 | 同上 |
| `applicability` | PRIMARY（适用场景/何时用） | 恰好 1 条、非空（规则 3） |
| `content` | 材料正文 | 1–4 行、自包含可直接用（规则 4）；不得是题目/解析/答案（规则 2、8） |
| `boundary` | 边界（适用范围/前提/易错） | 非空；禁止教材原文摘录 |
| `note` | 备注 | 建点提案记 `NEW:<subject>/<建议slug>`（规则 7） |
| `midx` | 同一块出多条材料时的序号（`''/b/c/d/…`） | 合并工具按出现顺序重排（`tools/kb_coverage/merge_text_judgments.py`）；只有一条材料时为空 |

### 已落表数据的现实差距（P2 收口项，非本次修改范围）

2026-09-28 实测 `material_judgments.csv` 16,625 行：MATERIAL 16,519 / SKIP 106。
其中 **698 行 MATERIAL 用了规则 1 禁用的 type**（REPRESENTATION_GUIDE 519、DERIVATION 150、
WORKED_EXAMPLE 25、COMPLETE_SOLUTION 4），另有 102 行 SKIP 的 type 为空。这些是冻结规则之前的
产物，按既有裁定不动；P2 收口时按本协议重新裁定或清理。`merge_text_judgments.py` 的 TYPES 白名单
（现含 7 值）也需要同步收紧到 3 值——在它下次跑之前完成。

---

## 三、判定 CSV 输出格式

- 文件：`tools/kb_coverage/tables/material_judgments.csv`，表头固定 12 列
  `chunk_rel,chunk_id,action,node_slug,type,title,summary,applicability,content,boundary,note,midx`。
- 编码 UTF-8；读侧用 `utf-8-sig` 容忍 BOM，写侧用 `csv.DictWriter` + `newline=""`。
- 每个未判定唯一键**恰好一行**（同键重复行由预筛去重，`SKIP-重复键`）；一块多材料用 `midx` 顺序排 `''/b/c/d`。
- `action` 只有 `MATERIAL`（入库）与 `SKIP`（显式放弃）；两值都落表，防止后续轮次重判。
- 合并纪律：判定轮产物经 `tools/kb_coverage/merge_text_judgments.py` 合并进表——它校验 12 列、
  拒绝块不存在的行、按哈希前缀回填截断的 `chunk_rel`、按 `(chunk_rel, chunk_id)` 归组重排 `midx`、
  节点不逐字时唯一命中的改规范 slug、改不动的转 `NEW:`（不静默丢弃）。
- 预筛只读这张表去重（`load_judged_keys`），**判定行一旦落表，块就不再有资格被重判**——所以
  语义判定在落表前必须过完本协议全部 11 条规则。

---

## 四、升级纪律

- 语义判定必须亲自读块内容，禁止词面匹配；词面正则只用于预筛机械 SKIP，不能替代判定。
- 规则之间矛盾、门跑不通、或某块找不到满足全部规则的合法写法时：**升级提问**（escalate），
  附上块内容与已试过的写法；**不硬闯、不造假、不为了过门放宽规则**。
- 题目/解析/答案即使"改写得像知识点"也一律 SKIP（规则 2 优先于一切改写冲动）。
- 找不到对口节点 → 提案建点，不硬绑已有近似节点；建点提案落 `new_points_manual.csv`，
  建完从提案表删行防重放。
- 不做学生可见对象（规则 8）：本协议产物只进知识库侧车，不出现在学生可见 UI。
- 全局上限（每节点 ≤4 条）在 P2 收口；切片轮判定时至少守住"切片内同一节点最多 4 条"。

---

## 五、预筛现状（P0.1 实测，供排工）

预筛命令与结果（`python tools/kb_coverage/prescreen_chunks.py --json tools/kb_coverage/tables/prescreen_report.json`，
退出码 0）：

- 块池 186,635 行；已判定键命中 11,745；未判定 174,890 行（171,461 唯一键）。
- **JUDGE（需模型判定）65,453（37.4%）**；**SKIP（机械可判）109,437（62.6%）**。

SKIP 理由分布：题目派生 100,317（成套选项 68,574 / 答案·故选语 30,452 / 标题题号 751 /
短题干无结论 540）、同键重复行 3,429、广告宣传 2,478、过短(<40 字) 2,028、同指纹重复 1,131、
版权/出版信息 28、空白/占位页 24、目录 2。

按来源（JUDGE / SKIP，块数）：2026年新高考资料(1) 12,411 / 32,102；2026年新高考资料 11,757 / 32,533；
27版五三 16,595 / 14,485；2026年新高考资料(3) 8,469 / 20,244；6. 2026生物总复习 5,371 / 3,521；
2026年新高考资料(4) 6,261 / 1,828；53知识清单·化学 1,416 / 1,544；·数学 1,006 / 1,296；
·生物 1,779 / 275；·物理 388 / 1,170；解题觉醒四科（生物 201、化学 193、物理 36、数学 9）全部 SKIP。

排工含义：模型判定轮只处理 65,453 块；薄料节点（规则 10）优先切片。
