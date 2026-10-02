# 建点闭环报告（块池判定的 532 条建点提案 → 304 个新点 + 337 条材料）

> 2026-10-01。本报告只写**实测数字**，每条结论都给出可重跑的确定性命令。
> 配套：`docs/kb-organic-intake-closeout-2026-09-30.md`（块池判定与全量入库）、`docs/kb-dense-rebuild-2026-10-01.md`（dense）。

## 0. 一句话

块池判定时"找不到对口节点"而写成 SKIP+`NEW:` 提案的**532 条提及**，本轮收口为
**304 个新知识点**（其中 **296 个**写成了材料、**8 个**因块过薄撤回）+ **41 条改绑既有节点**，
新增 **337 条材料**；全线门禁 **23/23 全绿**，成品包晋升 **version 9**。

## 1. 提案的清点（先审后建）

| 类别 | 数量 | 处置 |
|---|---|---|
| `NEW:` 提及（判定源 note 里） | 532 | 按名字清洗后映射 |
| → 规划为**可建点** | 304 | 建点 + 落材料 |
| → 名字**已是既有节点**（改绑） | 41 | 块改写成绑既有节点的材料 |
| → 名字仍是整句话/超长（**不建点**） | 17 | 登记 `build/agent-input/reland_worklist_unmatched.csv`，块保持 SKIP |
| 另有 9 条被规划器判"坏名" | 9 | 同上登记（含 8 个"X及其化合物的性质；找不到对口节点：…"的元素化学提案、1 个"平面的概念与表示：…"） |

- 规划器：`PYTHONPATH=tools python -m kb_coverage.plan_block_proposals --write`
  → `build/agent-input/block_proposals_plan.{json,csv}`（304 plan / 37 rebind / 9 bad）。
- **建点前的清障（重要）**：旧 `new_points_manual.csv` 里有 **17 条陈旧行**会被 `create_points` 一起建出来——
  9 条是**已被 `point_merge` 合并**的 slug（建回即复活死节点，登记册 U-03 记过这个坑），
  8 条是 **slug 带前导空格**的残行（包内已有同名正确节点，建出来是只差一个空格的孪生节点）。
  新增 `tools/kb_build/prune_new_points.py` 把这 17 行挪进停车场（`build/agent-input/new_points_parked.csv`，带原因）。
- 追加与 parent 校验：`tools/kb_coverage/append_new_points.py`（304 行全部 parent 存在、无重名）。

## 2. 建点

    PYTHONPATH=tools python -m kb_build.create_points --write

实测：**新增 304、幂等跳过 1,495**；staging 节点 **3,570 → 3,874**；
`unbound_points` 由 0 → 304（预期，由下一节消解）。

## 3. 材料重落（把"提案"变成真材料）

工件单：`tools/kb_coverage/make_reland_worklist.py --write` →
`build/agent-input/reland_worklist.csv`（**345 件**：NEW 304 + REBIND 41；`rewrite` 324 / `append` 21），
按判定源文件切成 16 批（`reland_batches/batch_01..16.csv`，同文件不跨代理）。

执行：**16 个代理并行**逐件读块正文、写五字段材料、把源里的 SKIP 行改写成 MATERIAL 行
（`append` 的追加新行，取下一个空闲 `midx`）。结果：

| 项 | 数量 |
|---|---|
| 落成材料 | **337**（= 304 点里 296 点的材料 + 41 条改绑材料） |
| 正当跳过 | **8**（块过薄/图未提取/填空骨架 —— 逐条给了理由，见下） |
| 判定源自检门 | **232 文件 / 不过 0 个**（`check_slice_verdicts.py`） |

跳过的 8 条（8 个点因此零材料，见 §5 撤回）：
`常见组合函数的图象`（函数与图象是图片未提取）、`物理选择题的常用解题方法与技巧`（仅题型导语）、
`胚芽鞘生长情况的判断方法`（仅版面标题）、`卤素的提取`（仅流程标题+占位符）、
`微型工业流程选择题解题策略`（一句导入语）、`碳元素在自然界中的循环`（学生版填空骨架）、
`碳酸与碳酸盐`（学生版填空骨架）、`能量图像分析`（45 字标题骨架，实质在图中）。

## 4. 收口：合并 / 入库 / 三件门

    PYTHONPATH=tools python -m kb_coverage.merge_text_judgments --dir-mode --apply --resync
    PYTHONPATH=tools python tools/kb_coverage/materialize.py --write
    PYTHONPATH=tools python -m kb_build.rebuild_aliases --write

入库后暴露并收口的三件（都是"只有建完点才看得见"的）：

1. **`locator_boundary` 296 红**：新点的 `boundary` 是 `定位：<册 章>。` 空壳（规划表的 excerpt 为空）。
   → 把**该点材料的 boundary** 接在定位串后（与既有节点同格式）。见 `tools/kb_build/finalize_new_points.py`。
2. **`unbound_points` 8 红**：上表 8 个点。→ 按权威删点通道（`point_delete.csv`）撤回，
   并把手册表里的行挪进停车场；节点 3,874 → **3,866**。
3. **`chapter_uncovered_units` 7 红**：新点的 `source_locator`（`块池判定·<来源>`）不在章表里；
   且 `source_unit()` 会从定位串里**推导**出单元名 `53知识清单`（四科共用）。
   → `chapter_by_source.csv` 追加 11 行，`decision=keep_per_node`（归属按节点自身定位串）。

**实测结果**

| 项 | 数值 |
|---|---|
| 节点 | 3,570 → **3,866**（+304 建、−8 撤） |
| 材料 | 49,962 → **50,383** |
| 别名 | 36,749 → **37,327** |
| 门 | **23 项全绿**（`gate.evaluate()` 非零门：无） |
| `promote --dry-run` | gates / consistency / roundtrip 全 OK，`version→9` |
| `promote` | **已写成品包 version 9**、内容戳 `f1f6a470c840b365`、22 个文件 |

## 5. 本轮修掉的三个**工具缺陷**（都在过程中被真实撞到）

1. **`merge_text_judgments` 的"既有键跳过"按 (chunk_rel, chunk_id) 判** → 某块**一旦入过表**，
   源里**同块新增的行**（append 流程的 midx=b/c…）**永远进不了表**。
   实测：21 件 append 全部静默丢失；修好后一次补进 **104 行**（多出的部分是历史上同类的积压），
   104 条材料随之入库。修法：`--resync` 时把源里存在而表里缺失的 `(key, midx)` 对**补进表**。
2. **`--resync` 是源优先**：只写进判定表的裁决会被下一次合并回退。实测两处翻车——
   20 条"侧车胜出"的归属裁定（与侧车材料撞 slug）与 1 处节点修正。
   修法：新增 `tools/kb_coverage/sync_rebind_decisions.py`（把裁定写回源），
   并从此**所有归属/内容修正一律改判定源**。
3. **`create_points` 会把旧表的残留行一起建出来**（U-03 的复现路径）。修法：`prune_new_points.py` 前置清障。

## 6. 遗留与未验证（逐条列出）

1. **8 个点已撤回**（`point_delete.csv` +8）：不是"漏"，是块里确实写不出材料；要补需换来源
   （例如"常见组合函数的图象"要补图 OCR）。
2. **26 条提案未建点**（17 unmatched + 9 bad）：名字仍是整句话/超长。其中 8 条是元素化学
   （`砷/硼/钛/银/锌/镍及其化合物的性质`——`铅`已在包内），若认可"元素及其化合物"这类考点，
   下一轮清洗后可建；本轮未建，块保持 SKIP。
3. **综合桶占比**：304 个新点里 **62 个**落在 `BIOLOGY·综合·综合·综合` 这类综合桶
   （规划器的正则兜底）。要改进需另一套"节点级章表映射"，本轮按 `keep_per_node` 记账。
4. **同一考点的其它来源块未落**：一个点常有 2–5 个块提过（工件单只取规划表的主证据块），
   其余块仍是 SKIP（登记在 `reland_worklist_unmatched.csv` 的同类清单里）。
5. **dense 二次重打 + 仪器化三门**：节点集变了（+296），dense 必须重打（本轮已启动，
   结果见 `docs/kb-dense-rebuild-2026-10-01.md` 的更新）；core:data 的仪器化三门仍本机不可跑（KD-25，UNVERIFIED）。

## 7. 复算命令

```bash
PYTHONPATH=tools python -m kb_coverage.plan_block_proposals            # 提案规划（--write 落表）
PYTHONPATH=tools python tools/kb_coverage/append_new_points.py         # 追加前 parent 预校验（--write 落表）
PYTHONPATH=tools python tools/kb_build/prune_new_points.py --write     # 清陈旧行（挪停车场）
PYTHONPATH=tools python -m kb_build.create_points --write              # 建点（staging）
PYTHONPATH=tools python tools/kb_coverage/make_reland_worklist.py --write   # 工件单
PYTHONPATH=tools python -m kb_coverage.merge_text_judgments --dir-mode --apply --resync
PYTHONPATH=tools python tools/kb_coverage/materialize.py --write
PYTHONPATH=tools python -m kb_build.rebuild_aliases --write
PYTHONPATH=tools python -m kb_build.finalize_new_points --write        # 真边界 + 删点 + 章表
PYTHONPATH=tools python -m kb_build.delete_points --write              # 撤回 8 个零材料点
PYTHONPATH=tools python tools/kb_build/promote.py --dry-run            # 23 门 + 一致性 + roundtrip
```
