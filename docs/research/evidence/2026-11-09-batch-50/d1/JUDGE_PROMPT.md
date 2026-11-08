# B50-D1 绑定判定提示词（model-labeled, single model）

版本：v1（2026-11-08）。此文件是判定口径原文；`judgments.csv` 的 `prompt_sha256` 指本文件。
判定员：`deepseek/deepseek-flash`（ZCode harness）。**无人类专家、无独立第二判定会话**（见 manifest/报告局限）。

## 任务

给定一条成品包材料（title / 正文五字段）与它当前绑定的知识点节点（name / kind / topic / boundary），
判断这条绑定是否成立。**逐条独立判断**，不要参考其它条目。

## 判定前先做一步（顺序固定）

先用一句话在内部写出「**这条材料自己讲的是什么知识**」（不是节点名，不是材料标题的复述）。
再把这句话与现绑节点的 name + boundary 比对。**不得**先看节点名再去找材料里的对应词。

## 三档判定

- `CORRECT`：材料的主知识点就是现绑节点，或属于该节点 name/boundary 明确覆盖的组成部分/下位方法。
  - 节点是上位概念时，讲它的一个下位方法/子类**算 CORRECT**（例：节点「育种」，材料讲「诱变育种」）。
  - 措辞不同、详略不同不算错；材料附带邻近知识不算错。
- `REBIND`：材料的主知识点属于**同科另一个已存在节点**，且现绑节点不是它的上位/下位覆盖关系。
  - 必须给出 `target_node_slug`（用 `node_index.tsv` 中真实存在的同科 slug，逐字抄）。
  - 仅"另一个节点更贴切"不够：现绑节点必须**不成立**（主知识点落在其 scope 之外）。
- `NONE`：材料不含可归入任何知识点的教学内容（纯题干/答案/目录/装饰/重复/空泛套话），
  或现绑错误且**同科没有**更合适的既有节点（应剔除或新建点，交产品裁决）。

**宽松偏差的对冲**：拿不准时**不要**默认 CORRECT。先在 `confidence=low` 如实标出；
`low` 会在复判环节单独处理，最终仍无法确认的按错误计。

## 证据引文（强制）

- `evidence_quote`：从材料正文（title/summary/applicability/content/boundary 任一字段）**逐字复制**的连续子串，
  ≤160 字，必须直接支撑你的判定：
  - CORRECT：引文要写出与节点对应的那个知识点结论；
  - REBIND/NONE：引文要写出材料**实际**讲的知识点。
- 禁止改写、拼接、加省略号；脚本会机械校验 `quote in text`，不通过的行降级为错误（ESCALATE）。

## 置信

- `high`：语义明确，换一个判定员也会同意；
- `medium`：节点 scope 较宽/材料跨两个点，但方向清楚；
- `low`：需要第二判定；**不得**为了好看把 low 写成 high。

## 输出格式（TSV，一行一条，表头逐字）

    material_slug	verdict	target_node_slug	confidence	evidence_quote	note

- `verdict ∈ {CORRECT, REBIND, NONE}`；`target_node_slug` 仅 REBIND 填，其余留空。
- `note` 可空；REBIND/NONE 必须写一句理由（≤60 字）。
- 不写 layer，不写其它列。

## 硬禁令

1. 不得因为材料标题出现在节点 aliases 里就判 CORRECT（aliases 由历史材料标题生成，判定件不提供 aliases）。
2. 不得因为「节点更大/更小」本身判错：只有主知识点落在 scope 外才判 REBIND/NONE。
3. 不得跨科改绑（现行包跨科绑定为 0；如遇跨科只能 NONE + 记理由）。
4. 不得漏判、重判；每条 `material_slug` 恰一行。
