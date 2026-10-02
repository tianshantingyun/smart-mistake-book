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

## 3. 段2：块池全量判定（**232 / 232 片全部落定**，2026-10-01）

- 切片：232 片 / 68,363 块（唯一键 68,363，**跨片重复键 0**）。
- 判定产物：`knowledge-production/judgment-verdicts/*.jsonl.csv`（逐片，闸门 `check_slice_verdicts.py`）。
- **进度：232 / 232 片，自检门 `不过 0 个`**。首轮落定时为 70,051 行
  （MATERIAL 22,955 / SKIP 47,096 / NEW 提案 532）；**建点闭环（§3f）之后的最终态**
  （`check_slice_verdicts.py` 实测）：**70,072 行：MATERIAL 23,292 / SKIP 46,780 / NEW 提案 218**
  ——材料 **+337**（296 个新点的材料 + 41 条改绑），建成的提案从 SKIP 行改写为 MATERIAL 行。
  按来源全部完成：53 四本（首轮 52 片）、27版五三 56/56、新高考资料(1) 12、(3) 29/29、(4) 21/21、
  2026年新高考资料 40/40、6.2026生物总复习 18/18。
- 已合并（幂等，`merge_text_judgments --dir-mode --apply --resync`）：判定表 **86,414 行**
（MATERIAL 38,929 / SKIP 47,485）；**入库干跑 = 材料 38,508 / 跳过 47,800 / 错误 0**（本会话起点 材料 20,614）。
- 建点提案：**532 行**（`NEW:` 记在 SKIP 行 note 里）→ `plan_block_proposals` 收拢为
  **304 可建点 / 37 可改绑既有节点 / 9 需人工处置**（§4.10）。

## 3b. 全量入库 → 成品包 v9（**已完成**）

2026-10-01 执行序列（每步都有复算命令，见 §5c）：

1. `staging_freeze --snapshot`（写前指纹）→ `materialize --write`：
   **17,890 条材料**落盘，新增 7 卷侧车（v2-12…v2-18；建点闭环后共 19 卷，新增 v2-19）。
2. 之后三道收口（都是"只有全量入库后才会暴露"的）：
   - **2 个残渣节点归零**（`unbound_points=2`）：`溶质为碱的溶液-h-全部来自水的电离`、
     `六种表示物质变化的方程式`——两条材料的唯一归属在腾位时按内容改绑到更准的节点，
     残渣节点名（一整句 / 一个材料标题）随之清掉：`point_merge.csv` +2 行 → `merge_points --write`
     （**点数 3,572 → 3,570**；无损校验：材料数不变、无环、无悬空）。
   - **LaTeX 损坏 6 条**（`latex_damage=6`）：新入库材料把希腊字母写成**裸词**
     （`倾斜角为theta的弦长`），被门判"反斜杠被吃"。按包内既有约定改成行内公式
     （`$\theta$`；实测 12,813 条材料用 `$…$`）——侧车 6 字段 + 判定表 6 字段同步改。
   - **幽灵别名 81 条**（`ghost_aliases=81`）：别名是从绑定材料的标题派生的，
     材料全量入库后必须重算：`rebuild_aliases --write` → **别名 27,794 → 36,749**（2,376 节点变更）。
3. **`promote --dry-run` 全绿**：`gates OK / consistency OK / roundtrip OK / manifest version→8`。
   成品目录当时未写；**其后已按用户授权写完 v8 与 v9**（见 §3f.2）。
4. **建点闭环把 staging 推到最终态**（§3f）：+304 新建点（其中 296 个写成材料）、−8 撤回空点、
   +41 条改绑材料、合并缺陷修复补入 104 行材料；随后 promote **version 9**。

**入库后包的实测规模**（vs 会话起点，**建点闭环后的终值**）：材料 **27,182 → 50,383**、
知识点 3,572 → **3,866**、别名 25,359 → **37,327**、侧车 **10 卷 48.2 MB → 19 卷**
（P0.4 预算估的是 +141–174 MB，实测远低于预算；`overBudget=false` 结论不变）。

## 3f. 建点闭环与 promote v9（**2026-10-02 完成**）

块池判定留下的 **532 条建点提案**是本任务最后一段：规划为 304 可建点 / 37 可改绑 / 9 需人工，
其余为容量拦截或重复提案。全过程报告见 **`docs/kb-point-intake-2026-10-01.md`**；要点：

1. **建点**：304 行先经 name/kind/parent 复核（`append_new_points` 预校验），
   `create_points --write` 幂等执行（新 304 / 跳过 1,495）→ staging 节点 3,570 → 3,874。
2. **材料重落**：工件单 345 件（304 新建 + 41 改绑）分批由 agent 把 SKIP 行改写成 MATERIAL 行
   （五字段齐备、节点逐字来自判定工具包）→ **337 条材料**；**8 个点因块过薄写不出材料 → 按权威
   删点通道撤回**（`point_delete.csv` +8，节点 3,874 → **3,866**），其余 296 点全部落料。
3. **三件收口**：判定表与包重同步（含**合并工具的真实缺陷修复**——同块追加行在既有键上被丢弃，
   补入 104 行）；304 个新点的 boundary 由材料派生（`finalize_new_points`）；
   章表补 `53知识清单` 派生单元 → **门 23/23 全绿**。
4. **promote v9**：`promote --dry-run` 全绿后写成品包——**version 9、内容戳 `f1f6a470c840b365`**、
   22 个文件（19 卷 + 主包 + 索引 + manifest）。
5. **dense 二次重打**：包变了就必须重打（见 §4.12 与 `docs/kb-dense-rebuild-2026-10-01.md` 附录）。
6. **新一轮 pin 同步**：`test_kb_delete_points` / `test_kb_rename_points` 点数 3,570 → **3,866**、
   `test_kb_update_manifest` 删点计数 184 → 192、`test_kb_golden_slices` 的切片科目改为从产物派生
   （切片按章的 nodeCount 生成，包一变入选章就换位，写死 subject 的用例会被无关错误挡下）。

## 3c. 入库前腾位：102 条归属冲突（侧车 vs 判定表）

`materialize --write` 对同一 slug 只认一个节点：侧车里那条材料挂的节点必须与判定表一致，
否则整批拒绝（`slug 撞车`）。多轮判定下实测 **102 条**冲突（同一块被两轮判到不同节点）。

- **逐条按内容裁定**（不信机械规则：先看的 3 条样本里两种都有）：**82 条取本轮判定节点、
  20 条取侧车节点**；`high` 70 / `low` 32。裁定表 `build/agent-input/rebind_conflicts_verdict.csv`。
- 两类结构性发现：① **多子材序号错位**（14 条）：同一块两轮切成不同子材，slug 的 `midx` 指向不同正文
  （如 `v-t/φ-x/E-x` 与 `φ-x/E-x/v-t`），必须按本轮正文定节点；② 长描述式 slug 逐条核了 `node_name` 真义。
- 落库：`apply_rebind_conflicts --write`（改判定行 20 条）→ `drop_materials --write`
  （删侧车陈旧材料 102 条）→ 重跑 `materialize --write` 让本轮正文重落（**修正向前**：内容用最新一轮）。
- 新增工具：`tools/kb_coverage/apply_rebind_conflicts.py`、`tools/kb_build/drop_materials.py`。
- 遗留：**32 条 low** 已点在裁定表 `reason` 里（两轮各取同一块不同子节 / 两侧节点名同义），
  需要时可逐条复核；含 1 条主判定已注明的分歧（`ext-phy-9404c786bf-019`：块含 E-x/φ-x/Ep-x 三节，
  裁定取上位节点 `电磁学图像的解题通法`，而侧车是专指 `e-x图像`）。

## 3d. 本会话修的四处口径 / 八个工具（都有复算命令）

1. **块池科目口径**（`extraction_state._subject` 改逐级目录判定；池 1,469 块 + 切片 642 行落盘）——
   详见 §3e。**三处下游各抄一份的旧规则统一**到 `subject_of_path`
   （`merge_text_judgments` / `dedupe_judged_materials` / `plan_new_nodes`）。
2. **薄料账本重算** `report_material_gaps --write`：1,232 → 661。
3. **判定员工具包** `make_judge_kit.py` → `build/agent-input/judge-kit/`
   （`nodes_<SUBJECT>.tsv` / `thin_nodes.tsv` / `overfull_nodes.tsv`；
   材料数**必须从侧车算**——主包 `knowledgePoints[].materials` 实测恒空）。
4. **建点闭环入口** `plan_block_proposals.py`（判定员的 `NEW:` 写在 SKIP 行 note 里，
   此前无任何工具消费；名字清洗后 427 条 → 304 可建点）。
5. **合表增 `--resync`**（`merge_text_judgments.py`）：判定产物落表后被修复（超 4 段 content 合并、
   改绑、双写覆盖）时，"既有键跳过"会让修正**永远进不了表**（实测：一次同步修正 309 行）。
   默认关，只覆盖同键同 midx，第二次跑 0 改动（幂等）。
6. **`fix_content_segments.py`**：把 MATERIAL 行里 5+ 段的 `content` 按"合并最短相邻两段"合到 ≤4 段
   （本会话 20 行 / 4 片；判定员在课件类切片里反复写超）。
7. **`apply_rebind_conflicts.py`** + **`drop_materials.py`**：入库前腾位（§3c）。
8. **`fix_chunk_subjects.py`**：科目口径落盘（池 + 切片，幂等）。

## 3e. 块池科目口径修复（**已落地**）

- **问题**：池内 **1,469 块** `subject=UNASSIGNED`——旧规则"路径含多个科目词就不猜"被**考点词**误伤
  （`烃的衍生物` 含"生物"、`降低化学反应活化能的酶` 含"化学"、`图像法或数学归纳法…` 含"数学"）。
  `materialize.py` 对非四科块直接 `非四科块` 硬错误。
- **修法**：`extraction_state._subject` 改**逐级目录**判定（第一个"恰含一个科目词"的部件定科目，
  取**最浅**的——深到文件名会把物理文件判成数学）。**实测零回归**：对全部 186,635 行池记录，
  新规则与既有标注**逐条一致**（0 处分歧），并把 1,469 块全部解出（化学 1,363 / 生物 59 / 物理 47）。
- **三处下游口径统一**（原先各抄一份"关键词出现顺序"规则，实测 59 条生物路径被判成 CHEMISTRY）：
  `merge_text_judgments`（节点校验）、`dedupe_judged_materials`（材料 slug 前缀 `ext-<科>-…`）、
  `plan_new_nodes`（提案归科）→ 一律走 `extraction_state.subject_of_path`。
- **落盘**：`PYTHONPATH=tools python tools/kb_coverage/fix_chunk_subjects.py --write`（幂等；池 1,469 行 + 切片 642 行）。
  复算：`materialize --dry-run` 错误 0；按旧规则复算，判定表里 **36 行 MATERIAL** 本会判 `非四科块`。

## 4. 遗留与未验证（逐条列出，不静默）

1. **容量拦下的 91 条补料 + 包内跑题材料**：多批独立报出"节点名与其材料不符"（如「超重」节点
   的材料讲斜面板块/弹簧剪断、`v2o5 催化` 节点 5 条全是无关流程题）。**已按建议补跑**
   `audit_bindings_by_alias.py` 与 `scan_duplicate_nodes.py`（结果见 §4.8：错绑嫌疑只有 5 条、
   近重复 28 对前缀包含）——原记录的量级不成立，但 91 条容量拦截本身仍未处置，待与
   超配节点（2,189 个）一并裁决。
2. ~~**dense 向量资产落后于包**~~ **已收口（2026-10-02，两轮）**：promote v8 后按链重打
   （`.vec` 40,319 行 / sha `75710e7b…`）；建点闭环给包加了 296 个点之后**再打一次**——
   **41,193 行（= 3,866 + 37,327）、24,403,823 B、sha `8649afa6…`**，还原精度与端侧对拍全过，
   见 §4.12 与 `docs/kb-dense-rebuild-2026-10-01.md`。
3. **覆盖口径边界**：段1 裁决以材料 title+summary 为覆盖依据（未逐条读正文），属"宁漏不错"方向。
4. **近重复待复核**：`near_duplicate_review.csv` 2 组（同节点同标题、正文不同）。
5. **未跑的硬门**：真机/仪器化门（KD-25）、dense 端侧对拍——沿用既有登记，不在本轮环境能力内。
6. ~~**薄料账本过期**~~ **已收口（2026-09-30 夜）**：按当前包重算
   （`PYTHONPATH=tools python -m kb_build.report_material_gaps --write`），账本 1,232 → **661** 条
   （575 条已补料或 slug 已随合并消失、4 条新登记）。判定员改看现算的
   `build/agent-input/judge-kit/thin_nodes.tsv`，不再照旧账本找不存在的 slug。
7. ~~**118 条重判与权威改绑冲突的 type 遗留**~~ **不可复现（2026-09-30 夜实测）**：逐条核对
   `build/agent-input/rejudged_replacements.csv` 的 118 个 slug，包内 118/118 存在，type 分布
   46 `CONCEPT_EXPLANATION` / 70 `METHOD_MODEL` / 2 `MISCONCEPTION_GUIDE` —— **非三值 type 0 条**，
   原登记不成立。真正遗留的是**全包 726 条历史材料**仍用冻结前 7 值 type
   （`REPRESENTATION_GUIDE` 539 / `DERIVATION` 158 / `WORKED_EXAMPLE` 25 / `COMPLETE_SOLUTION` 4），
   与新判定无关，**重新登记**为独立收口项。
8. **P2 三项实测数字（2026-09-30 夜，命令见 §5b）**：
   - **错绑嫌疑 5 条**（已裁定豁免 21 条）——远小于 §4.1 "多批独立报出"给人的印象；
   - **近重复节点**：同 topic 规范名相同的成组 + **28 对前缀包含**（如
     「装置气密性检查」vs「装置气密性检查的方法」，各 4 条材料；「遗传病的检测与预防」vs
     「遗传病的检测和预防」，5 vs 4 条）；
   - **超配节点 2,189 个**（绑定 ≥4 条材料）。
9. **安全扫描第 5 次结果（2026-09-30 夜）**：首次拿到完整判决
   （`scan-2026-09-30T13-26-54`，12 条：5 HIGH / 7 LOW），但 `coverage.json` 仍
   `completeness=partial` / `runStatus=inconclusive`（威胁建模与发现阶段 partial、路径分析
   `not_applicable`）——**仍不得据此声称项目安全**，KD-14 边界不变。12 条全部落在**开发者工具脚本**：
   4 条"路径穿越"是写死输出路径的误报（`_judge30_build.py`、`scratch/ocr_53math.py`、
   `tmp_judge_8/make_csv.py`、`tools/kb_coverage/_slice26_verdicts.py`，前三者未被 git 跟踪）、
   1 条是 CLI 参数进 `patch()`（`tools/dense_build/patch_litert_api_aar.py:97`，运行者即开发者）、
   7 条是 seed 固定的 `random.Random` 抽样——无一条在随 APK 分发的 Kotlin 代码里。
10. ~~**promote 待用户授权**~~ **已授权并完成（2026-10-02）**：v8 与 v9 均已写成品目录
    （v9 = 节点 3,866 / 别名 37,327 / 材料 50,383，内容戳 `f1f6a470c840b365`）。
    两处点数 pin 已同步为 3,866（`test_kb_delete_points` / `test_kb_rename_points`，注释写明理由）。
11. ~~**两条既有测试的红是"表比成品包新"**~~ **promote 后已转绿**（2026-10-02 实测）：
    `test_kb_table_consistency` 的两条读**成品目录**，promote v9 前是预期红、promote 后随全量
    Python 套件（579 项）一起转绿。
12. **dense 向量资产已与成品包同源（2026-10-02 完成，两轮）**：promote v8（3,570 原子节点 /
    36,749 别名、材料 49,962）之后按 `tools/dense_build/README.md §1` 的五步链重打，`.vec` =
    **40,319 行 × 512 维 int8、23,889,119 B、sha256 `75710e7b…`**；旁车 corpus = 3,570 / 36,749 /
    40,319，`check_asset.py` 十项与 `run_kb_checks.py` 的 dense 节全绿。链①的 5 条既有红
    （`ProductionLexicalLegExportTest`、`Stage1LexicalLabTest`、`Stage2LexicalScoresExportTest`、
    `GoldenRetrievalJvmTest`、`DenseTokenizerParityTest`）已按判官 v2（130 条 / 20 章）重钉，
    判据与容差未动；词面腿重出 46,433 行 / sha `4ea61a3d…`；参考数 v2 口径：融合 **0.7308（95/130）**、
    稠密单路 **0.6923（90/130）**、词面腿单独 **0.6308（82/130）**。端侧常量与 fixture（tokenizer /
    encoder parity / scan / device order）已同步；重打、复核与未验证项见
    **`docs/kb-dense-rebuild-2026-10-01.md`**。
    **建点闭环后第二打（v9 包）**：`.vec` = **41,193 行（3,866 + 37,327）、24,403,823 B、
    sha256 `8649afa6…`**；先跑 `GoldenRetrievalJvmTest` 重出 130 题指标文件、再重出词面腿；
    随包变了重钉的期望值有 6 组（腿 MRR、Stage1/Stage2 MRR、reranker 候选集 1,152→1,250、
    `DenseRecallAssembly.VECTOR_ASSET_SHA256`、`DenseVectorAssetTest` 节点数 3,570→3,866），
    全部是数据漂移、判据未动，逐条理由写在 `docs/kb-dense-rebuild-2026-10-01.md` 的附录。
13. **32 条 low 置信归属裁定**（§3c）：裁定表 `reason` 已逐条写明分歧，
    建议随手抽样复核；其中 `ext-phy-9404c786bf-019` 是主判定已确认的分歧点。

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

### 5b. 本轮新增/修复项的复算命令

```bash
PYTHONPATH=tools python tools/kb_coverage/fix_chunk_subjects.py            # 块池科目口径（--write 才落盘）
PYTHONPATH=tools python tools/kb_coverage/make_judge_kit.py                # 判定工具包（节点/薄料/超配清单）
PYTHONPATH=tools python -m kb_coverage.plan_block_proposals                # 建点提案规划（--write 落表）
PYTHONPATH=tools python -m kb_build.report_material_gaps --write           # 薄料账本重算
PYTHONPATH=tools python -m kb_build.audit_bindings_by_alias                # 错绑嫌疑（只读）
python tools/kb_build/scan_duplicate_nodes.py                              # 近重复节点（只读）
python -m unittest discover -s tools/tests -t tools -p "test_kb_plan_block_proposals.py"
```

### 5c. 全量入库 / 腾位 / 写包序列（照抄可重放）

```bash
# ① 判定 → 合并（幂等；--resync 让修复过的判定行同步回表）
PYTHONPATH=tools python tools/kb_coverage/check_slice_verdicts.py          # 判定自检门（不过必须 0）
PYTHONPATH=tools python -m kb_coverage.merge_text_judgments --dir-mode --apply --resync
PYTHONPATH=tools python tools/kb_coverage/materialize.py --dry-run         # 错误必须 0
# ② 全量入库（只写 build/kb-staging，不碰成品目录）
PYTHONPATH=tools python tools/kb_build/staging_freeze.py --snapshot
PYTHONPATH=tools python tools/kb_coverage/materialize.py --write
PYTHONPATH=tools python tools/kb_build/staging_freeze.py --compare         # 只应有本次写入的变动
# ③ 腾位（仅当 materialize 报 slug 撞车时；先裁定再删、再重跑 ②）
PYTHONPATH=tools python -m kb_coverage.apply_rebind_conflicts --verdicts build/agent-input/rebind_conflicts_verdict.csv
PYTHONPATH=tools python -m kb_build.drop_materials --slugs-file build/agent-input/rebind_conflicts_verdict.csv --write
# ④ 入库后收口三件套（全量入库后才会暴露）
PYTHONPATH=tools python -m kb_build.merge_points                            # 残渣节点归零（point_merge.csv）
PYTHONPATH=tools python -m kb_build.rebuild_aliases --write                 # 幽灵别名归零
PYTHONPATH=tools python -m kb_build.fix_material_text --write               # 可机械还原的 LaTeX 损坏
# ⑤ 晋升干跑（23 门 + 表↔包一致性 + roundtrip；全绿才谈写成品）
PYTHONPATH=tools python tools/kb_build/promote.py --dry-run                 # 期望：gates/consistency/roundtrip 全 OK
```

### 5d. 建点闭环（§3f）的复算命令

见 `docs/kb-point-intake-2026-10-01.md §7`（规划 → 清障 → 建点 → 工件单 → 重落 → 合并 → 入库 →
别名 → 边界收口 → 撤点 → 晋升），此处不重复以免两处漂移。
