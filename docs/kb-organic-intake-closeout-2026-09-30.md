# 知识点全部有机入包 · 验收报告（阶段性，2026-09-30）

> 状态：**进行中**——段1 已完成收口（数字已实测）；段2 的「53 四本优先轮」判定 24/26 片已落地，
> 合并与干跑已多次验证；待全部落地后本报告的 §3/§5 数字再收口一次。
> 本报告只写**实测数字**；每条结论都给出可重跑的确定性命令，不转述代理自述。

## 1. 验收口径（本任务的"有机"定义）

1. **知识点全覆盖、不漏不静默**：每个来源的每个知识点都落进四档之一——① 绑既有节点成材料
   ② 建为新节点（多级目录到位）+ 材料 ③ 判定重复/无价值 SKIP（附证据）④ 待复核队列（显式列出）。
2. **有机**：全库经 23 道门校验全绿（无孤儿点、无悬空绑定、无重名、别名从绑定材料标题派生、
   边界真值、章节覆盖、树序正确）。
3. **封装**：staging 全绿 → promote 写成品包（主包 + 10 卷侧车 + 索引 + 台账，version 单调）
   → dense 资产与包同源 → roundtrip 可重放 → App 可加载。

## 2. 段1：候选区（知识清单 docx 考点 3,536 + 未入包材料 375）→ 成品包

| 项 | 实测 | 命令/证据 |
|---|---|---|
| 目录层裁决 | 3,536 条 → **新点 0**、覆盖证据 2,779、驳回 959 | `merge_candidate_verdicts.py --write` → `summary.json` |
| 材料层裁决 | 375 条 → **PROMOTE 0**（357 条仅 slug 大小写差异、17 条题目残片、1 条已覆盖） | 同上报 + `FINDINGS.md §6` |
| 补料入包 | **81 条**（新点 0、隔离 0；91 条因目标节点已有 ≥4 条材料被容量拦下、1 条完全重复） | `apply_authored_materials --write` |
| 别名 | 25,359 → **25,436**（65 个节点变更） | `rebuild_aliases --write` |
| 门禁 | 23 门 + 契约 + roundtrip **全绿** | `promote --dry-run` |
| 晋升 | 成品包 13 个文件、**version 5**、内容戳 `42a2ab7e10859cad` | `promote` |

**容量拦下的 91 条**（`capacity_blocked.csv`）：目标节点已有 ≥4 条材料（每节点只展示前 4 条），
塞进去永远不可见——正确处置是先清该节点内的**跑题材料**（见 §4 遗留①）。

## 3. 段2：块池判定（53 四本优先轮，进行中）

- 切片：232 片 / 68,363 块；其中 **53 四本 26 片**（重键后原「重复键」块恢复可判，块数比预筛口径多）。
- 判定产物：`knowledge-production/judgment-verdicts/*.jsonl.csv`（逐片，闸门 `check_slice_verdicts.py`）。
- 已完成（24/26 片）：**6,636 块 → 材料 4,157 / SKIP 2,479 / 建点提案 195 行**。
- 已合并（幂等）：+2,744 行 → 判定表 **23,273 行**；`materialize --dry-run` = **材料 19,968 / 跳过 3,305 / 错误 0**。
- 建点提案（正式区确缺的考点）：**159 个**（生物 76 / 化学 50 / 数学 24 / 物理 9）→
  `knowledge-production/block-judgment-proposals-2026-09-30.csv`。
- 待办：化学片1、物理片1（在飞）、生物片3（在写 256/300，含 1 行 content 超 4 段待修）。

## 4. 遗留与未验证（逐条列出，不静默）

1. **容量拦下的 91 条补料 + 包内跑题材料**：多批独立报出"节点名与其材料不符"（如「超重」节点
   的材料讲斜面板块/弹簧剪断、`v2o5 催化` 节点 5 条全是无关流程题）；建议单独立项跑
   `audit_bindings_by_alias.py` 与 `scan_duplicate_nodes.py`（本轮只在 evidence 记录，未改包）。
2. **dense 向量资产落后于包**（ids 28,931 vs 29,008）：重打链第①步被**既有红**
   `ProductionLexicalLegExportTest`（判官 v2 金标集 130 vs 断言 90）阻断；`.vec` 保持旧件、
   设备期望值未刷新，恢复顺序见 `knowledge-production/candidate-merge/FINDINGS.md §7`。
3. **覆盖口径边界**：段1 裁决以材料 title+summary 为覆盖依据（未逐条读正文），属"宁漏不错"方向。
4. **近重复待复核**：`near_duplicate_review.csv` 2 组（同节点同标题、正文不同）。
5. **未跑的硬门**：真机/仪器化门（KD-25）、dense 端侧对拍——沿用既有登记，不在本轮环境能力内。
6. **薄料账本过期**（2026-09-30 判定员实测）：`node-material-gaps-2026-09.csv` 里至少 3 个物理 slug
   （`小量程电流表-表头`、`库仑力作用下的平衡`、`游标卡尺的读数`）在包内已被合并掉、不存在；
   判定员按包内真实 slug 选点未受影响。账本需按 point_merge 表重算一次（P2 收口项）。
7. **118 条重判与权威改绑冲突**：改绑表优先（已还原，见 §2/提交 261294fa）；这 118 个块的"type 修正"
   随之未生效（保留改绑表裁定的原字段），即它们仍是冻结规则之前的旧 type——若要收口，
   需人工/代理在**保留改绑目标**的前提下重写字段（登记为遗留）。

## 5. 复算命令清单（任何人可重跑）

```bash
python tools/kb_build/prep_candidate_merge.py                 # 1a 输入（dry-run 报数）
python tools/kb_build/check_candidate_verdicts.py             # 1a 裁决自检门
python tools/kb_build/merge_candidate_verdicts.py --write     # 1a 收拢
python tools/kb_coverage/check_slice_verdicts.py              # 块池切片判定自检门
PYTHONPATH=tools python -m kb_coverage.merge_text_judgments --dir-mode --apply   # 合并进判定表（幂等）
python tools/kb_coverage/materialize.py --dry-run             # 入库干跑（错误必须为 0）
python tools/kb_build/staging_freeze.py --snapshot|--compare  # 写入窗口并发检测
python tools/kb_build/promote.py --dry-run                    # 23 门 + 契约 + roundtrip
python -m unittest discover -s tools/tests -t tools           # 全量 Python 套件
```
