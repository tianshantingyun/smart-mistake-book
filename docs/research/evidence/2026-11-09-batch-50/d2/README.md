# D-2 结构审计证据（批次 50）：近重复 / 超配 / 容量 / 历史 type

> 结论：**D-2 四项均逐项清零或登记**——节点级近重复 0 组 0 对；候选区 2 组近重复材料
> 实测**确在包内**且近同，已裁决「保留哪条/退休哪条」（退休未执行，登记 + 判据）；
> 超配 2,481 节点有明确保留策略与逐节点台账；容量 91 条逐行登记（22 已同节点入包 /
> 12 异节点入包 / 44 未入包 / 13 节点已退役）；历史 type 三值外 = **0**，白名单单源。
>
> **零包写入/零 promote**：本线只读审计 + 出补丁，未改任何 KB 生产内容。

## 0. 身份与口径

```
python -X utf8 docs/research/evidence/2026-11-09-batch-50/d2/audit_d2.py
```
- release 包 = staging 包，sha256 `10ede1bf449de38f…`（v28，contentVersion `ce8edb8f89d91fe7`）；
- 材料 64,853 / 节点 3,280；每条材料恰好 1 条绑定（审计脚本按首绑定计）；
- 机器汇总：`d2-summary.json`；开工状态快照：`00-git-status-snapshot.txt`。

## 1. 近重复

### 1.1 节点级（D-2 原定义：同名 11 组 / 前缀 18 对 → 本批实测）

```
PYTHONPATH=tools python -X utf8 -m kb_build.scan_duplicate_nodes --json \
  --out docs/research/evidence/2026-11-09-batch-50/d2/d2-near-dup-scan.csv
# → {"same": [], "prefix_pairs": []}   （0 组 / 0 对）
```
存证：`d2-near-dup-scan.json`（JSON）/ `d2-near-dup-scan.csv`（空表 + 表头）。
工具自述读数：`tools/kb_build/scan_duplicate_nodes.py:20-29`（第三代 0/0）。

### 1.2 候选区 2 组（原 `near_duplicate_review.csv` 2 行）——**实测已入包、同节点、正文近同**

| 科目 / 节点 | 两条 slug | 实测 | 裁决 |
|---|---|---|---|
| CHEMISTRY / 乙烯是石油化工最基本的原料之一 | `cmp-che-02368`;`cmp-che-02639` | 两条都在包、同节点；正文 242 vs 240 字，仅「造成/而造成」等虚词差异 | **保留 `cmp-che-02368`，退休 `cmp-che-02639`（未执行）** |
| MATH / 特殊的棱柱和棱锥 | `cmp-mat-00065`;`cmp-mat-00280` | 两条都在包、同节点；正文 161 vs 164 字，仅「要求/再要求」「未必/不一定」差异 | **保留 `cmp-mat-00280`，退休 `cmp-mat-00065`（未执行）** |

- 逐行：`d2-near-dup-dispositions.csv`（含 keep/retire/content_len）。
- 材料级工具复核：`PYTHONPATH=tools python -X utf8 -m kb_coverage.dedupe_near_dup_materials --dry-run`
  → `材料 64853 / 节点 3280；候选对 173；簇 50；拟退休 1；SKIP 20`（44.3s）。
  两组**未进拟退休**：正文仅虚词差异，被该工具的「≤6 字实词替换」实体保护门挡下
  （`tools/kb_coverage/dedupe_near_dup_materials.py:24-31`）——这是该门的已知假阴性，
  故本批人工裁决并登记，不擅自改工具门。
- **未执行说明**：退休需写包（侧车 → promote → 材料数/摘要漂移），本线无内容写窗口；
  执行判据 = 在上述两条 slug 上跑 `drop_materials`/退休链 → `promote --dry-run` 23/23 →
  材料数 **-2**、无悬空绑定、别名唯一支撑检查通过（`alias_map.csv` 当前对两 slug 零引用）。

## 2. 超配（>4 条 / 节点）

```
PYTHONPATH=tools python -X utf8 -m kb_build.overfull_nodes --write
# → 2,481 个超配节点；挂 62,944 条；超出前 4 = 53,020 条；
#   节点内 ≥4 共 2,664（=4 共 183）；最大 146 = MATH「三角函数的图象与性质」
#   零 2-gram 重合（分诊）：5,954 条；超配段 4,918 条
```
- 权威明细：`build/kb-staging/overfull_nodes.csv`（62,944 行绑定级，工具的 `verdict` 列留空待裁决）；
- **节点级台账（本批产物）**：`d2-overfull-ledger.csv`（2,481 行：subject/slug/名称/条数/
  超出数/零重合数/处置/分诊标记）；
- **保留策略（明确）**：`RETAIN_NO_CAP`——运行时无每节点条数门（整批取数上界 1,024 行，
  展示由 20,000 字符预算按「角色秩→重教类型→title→materialId」逐条装填，
  `tools/kb_build/overfull_nodes.py:4-16` 与 `TutorTeachingReferenceSelector` 同源），
  超配不是失效绑定 ⇒ **全部保留、不解绑、不物删**；分诊只决定复核顺序。
- 分诊计数：`PRIORITY_REVIEW` 49 / `REVIEW` 1,009 / `NONE` 1,423。
- 工具报告原文：`d2-overfull-report.txt`。

## 3. 容量拦截 91 条（前提已被运行时推翻）

```
knowledge-production/candidate-merge/capacity_blocked.csv → 91 行（header 1）
```
逐行处置（`d2-capacity-dispositions.csv`）：

| 处置 | 行数 | 含义 |
|---|---|---|
| `ALREADY_IN_PACK_SAME_NODE` | 22 | 材料已在包，且绑定就是当时的目标节点 |
| `IN_PACK_OTHER_NODE` | 12 | 材料已在包，但绑到另一个节点（内容线后续改绑/合并的结果；需内容线复核，不视为丢失） |
| `NOT_ADMITTED_HISTORICAL` | 44 | 未入包、目标节点仍在 ⇒ 历史候选，不据此补/删 |
| `OBSOLETE_NODE` | 13 | 未入包、目标节点已不在包（退役/改名）⇒ 前提消失 |

- **废止依据**：拦截来自 `tools/kb_build/merge_candidate_verdicts.py:165`（`MAX_VISIBLE = 4`）
  与 `:209-216`（≥4 即 return）；运行时无该门（见 §2）。
- **生产器补丁**：`d2-capacity-deprecation.patch` —— **NOT_APPLIED / UNVERIFIED**。
  目标文件开工时已是 M（他线候选区去重改动在飞），按协作规则不整文件抢占。
  补丁把「拦截」改成「登记后照常入列」，判据（必须实跑 1–5）写在补丁头注释里：
  ①改测试语义（拦截行应进 materials，禁止只删断言）；②冻结输入逐条集合断言；
  ③`promote --dry-run` 23/23；④summary 语义改 informational；⑤全绿才可记「已废止」。
- 本文**不声称** capacity 规则已废止：只登记了逐行处置 + 待应用补丁。

## 4. 历史 type（三值外 667 → 0）

- 包内 64,853 条材料：`CONCEPT_EXPLANATION` 35,486 / `METHOD_MODEL` 25,437 /
  `MISCONCEPTION_GUIDE` 3,930；**三值外 = 0**（`d2-type-distribution.csv`）。
- 白名单单源（逐字一致）：
  - `tools/kb_coverage/materialize.py:66` = 3 值；
  - `tools/kb_coverage/merge_text_judgments.py:47` = 3 值；
  - `docs/kb-chunk-judgment-protocol.md:62` 与 `:90` = 3 值，其余 4 值禁用。
  （原文：`d2-type-single-source.txt`。）
- 结论：**type 单源成立**；不用重判队列消耗来「清零」，当前包内已无三值外类型。

## 5. 工具测试

```
python -I -m pytest tools/tests/test_kb_scan_duplicate_nodes.py \
  tools/tests/test_kb_overfull_nodes.py tools/tests/test_kb_materialize.py -q
# → 26 passed in 1.68s   （d2-tool-tests.txt）
```

## 6. 未做 / 边界

- 未写包、未跑 promote、未改 `knowledge-production` 任何内容文件；
- 近重复 2 条的**退休未执行**；容量补丁**未应用**（两者都给了判据）；
- 超配分诊标记是机械口径（零 2-gram 重合只做分诊，不做判定——本仓实测零重合里约 54% 正确）；
- 未对 2,481 个超配节点做逐节点内容裁决（本批给的是「保留策略 + 节点台账 + 分诊顺序」，
  不是「逐节点已判定」）；
- **副作用（如实）**：`dedupe_near_dup_materials --dry-run` 按工具设计会写
  `build/agent-batch31c/{dedupe-clusters.csv,dedupe-pairs.csv,plan.json,anchors.txt}`；
  本次运行已用 v28 包重新生成这 4 个文件（批次31c 原版未保留，`build/` 为 gitignored 产物）。
  `manual-review-20.csv` 未被覆盖。
