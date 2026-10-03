# KB 批次 3 报告：节点合并 + 绑定改绑（2026-10-03）

- **范围**：① 节点合并（候选 156 对 → 首轮裁定 → 独立复核 → 执行 103 对写盘）；② 第二轮绑定语义裁定的改绑落表与生效（111 条 REBIND）；③ 两者的收口（零材料节点、别名、切片判定引用、晋升门、套件 pin）。不含内容层边界改写、不含端侧改绑重放链路。
- **终局状态（本报告撰写时重跑确认）**：`promote --dry-run` EXIT=0；`check_slice_verdicts` EXIT=0（不过 0 个）；Python 套件 624 例 EXIT=1（failures=3，均为「晋升前」预期红，见 §6.2）。计数：知识点 **3761**、主题 398、材料 **50383**、别名（含主名）37356。
- **证据口径**：本报告数字有三类来源，行内标注——【实读】本报告撰写时直接读取的工件；【实跑】本报告撰写时重跑的命令；【执行轮】合并/改绑写盘步骤的执行轮输出记录（写盘步骤不重跑，其结论与落盘状态已由本报告用 git diff/表实读/终局门交叉核对）。

---

## 1. 合并：候选 → 裁定 → 复核

### 1.1 两轮裁定

| 环节 | 工件 | 实读结果 |
|---|---|---|
| 候选 | `build/agent-batch3/merge-candidates.json` | 156 对（另有 19 对在候选步被排除、未进入本轮裁定；该 19 对清单未落盘于本轮工件，本报告未逐对复核） |
| 首轮裁定 | `build/agent-batch3/verdicts/merge-part-1..3.csv` | 各 52 行、合计 156；**MERGE 114**（37+30+47）/ KEEP 42（15+22+5） |
| 独立复核（确认轮） | `build/agent-batch3/merge-confirm.csv` | 114 行；claim 全为 `MERGE->X`；**AGREE 112 / DISAGREE 2** |

落地规则：只写 AGREE；`reason` = 首轮裁定 reason + 「（复核 AGREE 2026-10-03）」；写前核对与既有 132 行 `point_merge.csv` 无重复对。本报告复核：新增 103 对与 HEAD 既有 132 行的正向/反向重复均 **0**（【实读】）。

### 1.2 独立复核 DISAGREE 2 行处置（逐条）

两行行号为 0 基（与 `merge-confirm.csv` 数据行序一致；本报告实读确认 verdict=DISAGREE 的即这两行）。

- **#6（CHEMISTRY）**：A=物质间不能一步实现的转化，B=在指定条件下-下列有关铁单质的转化不能实现的是，claim `MERGE->物质间不能一步实现的转化`。复核 note（摘）：对级同点成立，但幸存者不应独立保留——同一 B 在 #6/#7/#8 三行被并入三个不同幸存者，且三个 A（物质间不能一步实现的转化／不能一步实现的物质转化／物质间转化的5个常见易错点）互为近重复；正确去向是「四节点一次并为一个（簇主建议取材料最多的『物质间转化的5个常见易错点』，本行 A 作为被并方一并并入），B 只并一次；三行不可同时执行」。
  - **处置：按规则跳过并在此记账，未写表、未执行。** 未执行的具体前置（执行轮记录）：执行 #7 方向的前置是「A 池 `chem-kb-invalid-equilibrium-flags`、`chem-hj-pingheng-biaozhi-shiyong-tiaojian` 两条跨域误绑须解绑不随迁」——实测这两条材料的唯一绑定就在该节点上，解绑会变成 0 绑定材料并使 `unbound_materials` 门（23 门均为 value==0）转红；复核未给出可改绑的正确节点，故不越界执行。
- **#7（CHEMISTRY）**：A=判断下列转化能否实现-能实现的打-不能实现的打，B=同上，claim `MERGE->判断下列转化能否实现-能实现的打-不能实现的打`。复核 note（摘）：对级同点成立，但幸存者不应独立保留（同 #6：三行把同一 B 并入三个幸存者，三 A 互为近重复）；正确去向：本行 A 亦作为被并方，与「物质间转化的5个常见易错点」（簇主）、「物质间不能一步实现的转化」及 B 一次并为一个，B 只并一次。另注 A 池两条跨域误绑材料须解绑不随迁。
  - **处置：同上，跳过未执行。**
- **相邻已执行行 #8（AGREE）**：在指定条件下-… → 物质间转化的5个常见易错点（已并入）。但其复核 note 的前置「其余两 A 也须一并并入本簇主」仍未满足，留待绑定层先处理那两条误绑材料后再补：物质间转化的5个常见易错点 ← 物质间不能一步实现的转化、← 判断下列转化能否实现-能实现的打-不能实现的打。

---

## 2. 合并执行（五步）

### 2.1 改动前基线（【执行轮】本会话实测，全绿）

```
PYTHONPATH=tools python -m kb_build.check_pack_contract   # → 主题 398、知识点 3866；问题 0            EXIT=0
PYTHONPATH=tools python -m kb_build.promote --dry-run      # → 四节（gates/consistency/roundtrip/manifest）全 OK  EXIT=0
PYTHONPATH=tools python -m kb_coverage.check_slice_verdicts # → 已检 232 个判定文件 / 70072 块…不过 0 个   EXIT=0
```

裁定来源：`build/agent-batch3/merge-confirm.csv`（114 行；claim 全为 `MERGE->X`；verdict=AGREE 112 / DISAGREE 2）；对应首轮裁定 `build/agent-batch3/verdicts/merge-part-1..3.csv`。

### 2.2 步骤 1–2：dry-run → 写盘（`kb_build.merge_points`）

**步骤 1**（【执行轮】）：

```
PYTHONPATH=tools python -m kb_build.merge_points            # EXIT=0
合并 103；跳过（幂等/未找到）132；因会产生前置环而跳过 0
  材料改指 658；绑定去重 0
  点数 3866 → 3763；材料 50383 → 50383；悬空绑定 0
无损校验通过：点数减量=合并数、材料总数不变、无环、无悬空绑定
```

**步骤 2**（【执行轮】）：

```
PYTHONPATH=tools python -m kb_build.merge_points --write    # EXIT=0
（在步骤 1 输出基础上追加：）
→ 已写回 staging 包 + 19 个 sidecar；清除外部表悬空引用 chapter_map.csv 43、alias_map.csv 442、chapter_point_relocation.csv 2
→ 取代台账 +103 条（共 505 条退役）
```

交叉核对（【实读】本报告）：`chapter_map.csv` 相对 HEAD 恰为 −43 行、`chapter_point_relocation.csv` 恰为 −2 行；退役台账（`build/kb-staging/moe-2025-update-manifest.json` 的 `retired` 列表）终态 507 条（292 MERGE + 215 DELETE），与「+103 → 505，再 +1 → 506，再 +1 → 507」一致。

### 2.3 步骤 3：别名重建（`kb_build.rebuild_aliases --write`，本批）

【执行轮】：

```
PYTHONPATH=tools python -m kb_build.rebuild_aliases --write  # EXIT=0
节点 3763：有别名 3613，别名总数 33586
丢弃候选 12680（按原因）: 超长 9822、被 2 节点同时 claim 1462、以题面/指令开头 725、…
别名：37327 → 37349；变更节点 150
→ 已写回 …moe-2025-four-subjects-v1.json；表：alias_map.csv / alias_dropped.csv
```

（本批之后的改绑收口又跑过一次，见 §3.3；终态别名（含主名）37356 为 3761 节点口径，【实读】。）

### 2.4 步骤 4：晋升门先红后绿（`kb_build.promote --dry-run`）

**第一跑（红，【执行轮】）**：`EXIT=1`

```
FAIL gates：undeclared_prereq=5
FAIL consistency：chapter_by_node×20、prereq_map×15、point_rename×3、point_relocation×2
OK   roundtrip
OK   manifest：version→10，压平 0 条
晋升被拒绝：未全绿，成品目录未写
```

（`chapter_by_node` 列表被 checker 截断为 20，实际 40 行——下表的修复量为准。）

**修复（只动因本次合并悬空的项；基线这些表为 0 问题，故全部为本次合并引起）**：

| 表 | 动作 | 明细 |
|---|---|---|
| `chapter_by_node.csv` | 删 40 行 | 被并节点的章归属行 |
| `point_rename.csv` | 删 3 行 | 平衡条件-根本条件-…-f_合-0、在指定条件下-下列有关铁单质的转化不能实现的是、na22 |
| `point_relocation.csv` | 删 2 行 | 探究弹簧弹力与形变量关系的误差分析与改进、探究两个互成角度的力的合成规律实验的误差与改进 |
| `prereq_map.csv` | 改写 5 行 | 醛和酮的结构和性质易混易错点←醛-酮的化学性质；金属材料和金属的冶炼易错点←常见金属材料；金属材料和金属的冶炼易错点←金属冶炼；斜向变化体现不同价态…←铁的制备和性质；铁的化学性质←铁 |
| `prereq_map.csv` | 删除 5 行 | 海水提镁的工艺流程分析←海水提取镁（合并后自环）；醛和酮的结构和性质←醛-酮的概念（与 survivor 既有行重复）；金属腐蚀的类型←金属腐蚀的本质；铁的氢氧化物的稳定性←铁的氢氧化物；铁盐的性质及应用←铁盐（以上三条合并后自环） |

修复后 `prereq_map.csv` 401 行 == 包内 401 条前置边（本报告复核：表内 401 行【实读】；包内 `prerequisiteSlugs` 边合计 401【实读】；diff 为 10 删 / 5 增 = 5 改写 + 5 删除【实读】）。

**重跑（绿，【执行轮】）**：`EXIT=0`

```
OK gates / OK consistency / OK roundtrip / OK manifest：version→10，压平 0 条
（dry-run：门全绿，未写盘；将写入 version 10）
```

**终态复核（【执行轮】）**：`PYTHONPATH=tools python - <<… gate.evaluate() …` → `metrics total: 23 failed: 0 []`。随后仅对 1 行 reason 做措辞修正（「与 #30」→「经 #32」），再跑 `promote --dry-run` 仍 EXIT=0 全绿。

### 2.5 步骤 5：切片自检（当时红，留档；后由改绑轮修复）

【执行轮】`PYTHONPATH=tools python -m kb_coverage.check_slice_verdicts`：`EXIT=1`

```
已检 232 个判定文件 / 70072 块：MATERIAL 23292、SKIP 46780、NEW 提案 218
不过 81 个
（错误行形如：…: 节点不存在 PHYSICS/安安法测电阻（电流表差值法））
```

独立复算（【执行轮】，本报告按修复映射复算一致）：`knowledge-production/judgment-verdicts` 下共 **293 条 MATERIAL 行**引用本次被并掉的 103 个 slug，分布 PHYSICS 121、CHEMISTRY 161、BIOLOGY 9、MATH 2，涉及 **81 个文件**（check 每文件只打印前 6 条，故打印 232 行）。这些引用在改动前不存在（基线 0 个不过）。机械修法（把这些 `node_slug` 改写为链终点 survivor）不在合并轮的授权范围内，未动、如实上报；**改绑轮已执行该修复并把不过归 0**，见 §3.3。

### 2.6 合并集构造说明

- **只读沙箱预演**（写表前，`build/_merge_sandbox`，已清理）：确认无损——103 合并、0 环、0 悬空、点数 3866→3763、材料不变、23 门 0 失败、consistency 全空。
- **9 条链式中间行折叠为链终点等价行**（其 survivor 又会被别的行并走；表↔包一致性要求每行 survivor 存活、merged 消失，见 `tools/kb_build/check_pack_contract.py:315-322`）：
  - #26 →（水的电离平衡移动的影响因素←水的电离平衡曲线 由 #24 承担）
  - #33 →（金属的腐蚀←金属腐蚀的本质 由 #35 承担）
  - #50 →（#49 承担）；#72 →（#71 承担）；#75 →（#74 承担）；#79 →（#80 承担）；#81/#82 →（#83 承担）；#97 →（#96 承担）
- **1 条链式合流特例**：#30（金属的冶炼-金属矿物的开发利用←金属矿物的开发利用）按 #30+#32 链式合流写成（金属冶炼←金属矿物的开发利用），reason 中注明链式合流与日期。
- 计数闭合（【实读】）：112 AGREE − 9 折叠 = **103** 条写盘；`point_merge.csv` HEAD 132 行 → 本批后 235 行（+103）→ 改绑收口再 +1（零材料合并，见 §3.3）= 终态 236 行。

---

## 3. 改绑：111 条 REBIND 的落表与生效

### 3.1 输入与落表

- 输入（【实读】）：`PYTHONPATH=tools python` 读 `tools/kb_build/tables/content_audit_2026-10-02.csv` → **496 行 = KEEP 383 / REBIND 111 / NONE 2**（与 `docs/kb-bind-audit-round2-2026-10-02.md` §6 一致）；`point_merge.csv` 当时 235 行（本轮新增 103 行、HEAD 版 132 行），survivor 链解析无二义、无多跳。
- **① 裁定落表**（【执行轮】）：111 条中 **107 条写成追加行**（to 经合并链改指 16 条、from 经合并链改指 10 条），4 条不写（见 §6.2）；evidence = 裁定原句 + 「（round2 裁定 2026-10-02）」；(material,to) 与既有 1246 行去重（实测 **0** 重复对；本报告复核同结果【实读】）。日志 `build/kb-round2-rebind/plan.txt`；计划行 `build/kb-round2-rebind/planned_rows.csv`（107 行，与表内 107 条 round2 行逐条一致【实读】，0 差异）。

### 3.2 生效（`kb_build.rebind_materials`）

- **首跑（红，【执行轮】）**：`EXIT=1` —「改绑 107；跳过 1236；材料总数 50383；悬空绑定 0；可写回: False」+ 拒绝 10 行（目标节点被本批合并掉）：na2o2的性质及应用 / 铁的氢氧化物的稳定性 / 过氧化钠的性质与探究 / 有关物质的量浓度的计算 / 棱切球（与各棱相切的球）/ 金属矿物的开发利用 / na22 / 遗传病的检测与预防 / 力的正交分解法 / 探究弹簧弹力与形变量关系的实验。
- 修复后复跑（【执行轮】）：「改绑 107；跳过 1244；材料总数 50383；悬空绑定 0；可写回: True」`EXIT=0`。
- **③ `--write`（【执行轮】）**：`EXIT=0` —「改绑 107；跳过（幂等/错行）1244；材料总数 50383；悬空绑定 0；可写回: True / → 已写回 19 个 sidecar」；复跑 →「改绑 0；跳过 1351；材料总数 50383；悬空绑定 0」`EXIT=0`（幂等成立）。
- **既有改绑表行修复（非 111 条内，【执行轮】+【实读】核对）**：`material_rebind.csv` 原有行有 **47 行**引用本批合并掉的节点——**改 to 8 行、改 from 16 行**（合计 24 处字段改动、涉及 23 行；其中 1 行同时改了 from 与 to），另 **24 行**解析后 from==to（改绑已被合并吸收）：其中 **22 行 to 仍有效 → 原样保留**（改指会变成每次运行都计一次改绑的自指行、破坏幂等契约），**2 行按「跳过」删除**（`chem-hj-na2o2-qiangyanghuaxing`→na22、`ext-bio-e3a300b276-011b`→遗传病的检测与预防；其意图已由合并达成；两行原文见 `build/kb-round2-rebind/table_repair_report.txt`）。本报告核对终态：22 行俱在、2 行俱缺【实读】；`material_rebind.csv` 终态 1351 行 = HEAD 1246 + 107 − 2。
- **④ 晋升门复跑（收口后，【执行轮】）**：`promote --dry-run` →「OK gates / OK consistency / OK roundtrip / OK manifest：version→10，压平 0 条」`EXIT=0`（快照 `build/kb-round2-rebind/final_snapshot.txt`）。

### 3.3 收口：零材料节点 + 别名 + 切片

- **零材料节点收口（只修本次引起的，【执行轮】）**：`merge_points` 报告「合并 1；跳过 235；点数 3763 → 3762；材料 50383 → 50383；悬空绑定 0」→ `--write`「清洁 alias_map 1 行、取代台账 +1（共 506）」；`delete_points`「删除 1；点数 3762 → 3761」→ `--write`「随删材料 0、清 alias_map 1 行、取代台账 +1（共 507）」。本报告核对：被并的「模型特点」与被删的「菠菜常用作补铁食品之一-为探究其中铁元素的价态-现进行如下实验」在 `point_merge.csv`/`point_delete.csv`/退役台账各有对应行【实读】。
- **别名单收口（【执行轮】+【实读】）**：`python -m kb_build.rebuild_aliases --write` →「节点 3761：有别名 3611，别名总数 33595；丢弃候选 12662；别名（含主名）：37347 → 37356；变更节点 108」→「已写回 staging 包；表 alias_map.csv / alias_dropped.csv」。写前实测变更节点 108 个全部落在本表改绑涉及点 ∪ 本次合并对（别名 +84/−75 全可归因；分析稿 `alias_delta.txt` 记 109 个变更节点、NOT touched by rebind = 0，为收口前 3763 节点口径）。
- **切片判定引用修复（【执行轮】）**：`build/kb-round2-rebind/slice_verdicts_repair.txt` →「改 81 个文件 / 293 行」（映射逐条为 merged→survivor，共 293 行）；复跑 `check_slice_verdicts` →「已检 232 个判定文件 / 70072 块：MATERIAL 23292、SKIP 46780、NEW 提案 218 / 不过 0 个」`EXIT=0`。本报告复核：修复映射按学科复算 = CHEMISTRY 161 / PHYSICS 121 / BIOLOGY 9 / MATH 2（=293）；现文件内已无任何 node_slug 指向被并 slug【实读】。
- **`promote --dry-run`（改绑后首跑红 → 收口后绿）**：首跑 FAIL「unbound_points=2、ghost_aliases=53」（明细：PHYSICS/动量模型的特点、CHEMISTRY/菠菜常用作补铁食品之一 两节点被改绑抽空；53 条别名失去绑定材料背书，见 `build/kb-round2-rebind/gate_details.txt`）；零材料节点收口（1 合并 + 1 删除）+ 别名重建后复跑全绿（绿态与计数见 `build/kb-round2-rebind/final_snapshot.txt`）。

### 3.4 套件与 pin 更新

- **套件（最终，【执行轮】）**：`python -m unittest discover -s tools/tests -t tools` → **Ran 624 tests，FAILED (failures=3)**；红项点名（本报告重跑确认，见 §5）：
  - `test_kb_table_consistency.CurrentStateGreenTest.test_current_tables_agree_with_pack`
  - `test_kb_table_consistency.RowDeletionTurnsRedTest.test_dropping_a_point_merge_row_turns_red`
  - `test_kb_table_consistency.RowDeletionTurnsRedTest.test_dropping_an_alias_row_turns_red`
- 红线归因（【执行轮】复算，日志 `build/kb-round2-rebind/table_consistency_gen_check.txt`）：staging 包 ↔ 工作表 = 0 问题；成品包 ↔ 工作表 = 48 问题（alias_map 10 / chapter_map 1 / chapter_by_node 1 / prereq_map 15 / point_delete 1 / point_merge 20）；「删 alias 行必红」「删 point_merge 行必红」两场景改用 staging 包 + staging 台账重放均 True（用例逻辑未坏，只是基准代次落后）。promote 落盘成品后三条随即转绿。
- 基线（本线动手前）为 11F+1E；本线修复并转绿的有：`test_kb_alignment`(3)、`test_kb_apply_round4_verdicts`、`test_kb_bad_name_actions`、`test_kb_delete_points`、`test_kb_rename_points`、`test_kb_scan_duplicate_nodes`(2)、`test_kb_table_consistency` 其余 8 例。
- **pin 更新（按新实测值就地、保留断言与口径，【实读】核对）**：
  - `tools/tests/test_kb_delete_points.py:88`、`test_kb_rename_points.py:73`：由 3866 改 **3761**（注释写明 3866 − 103 − 1 − 1）。
  - `tools/tests/test_kb_scan_duplicate_nodes.py`：真实包基线由 11 组/18 对改 **1 组/0 对**（新实测 `S.scan()` → same=1（CHEMISTRY 误差分析/误差分析的方法）、prefix_pairs=0；文件头注明是重复本身被清掉、不是判据变宽）。
  - `tools/tests/test_kb_bad_name_actions.py`：移除 1 条 na22 例外（现 slug 已不在包，`_state=absent`）。

---

## 4. 计数与节点数变化

| 项 | 批前 | 终态 | 说明 |
|---|---|---|---|
| 知识点 | 3866 | **3761** | 3866 − 103（本批合并）− 1（零材料合并）− 1（空壳删除）= 3761；`merge_points` 三次数：3866→3763→3762→3761【执行轮】；终态实读：包内 3761、`check_pack_contract`「知识点 3761」 |
| 主题 | 398 | 398 | 终态实读（`check_pack_contract`） |
| 材料 | 50383 | **50383** | 只增不减口径：合并/改绑只改绑定目标、不删材料【实读：19 个 sidecar 材料数之和 = 50383】 |
| 别名（含主名） | 37327 | **37356** | 37327（成品包一代）→ 本批重建 37349 → 收口 37347 → 终态 37356【实读：33595 条额外别名 + 3761 主名】 |
| 退役台账 | — | 507 条 | 292 MERGE + 215 DELETE【实读 `build/kb-staging/moe-2025-update-manifest.json`】 |

工作表的行数变化（【实读】git diff，含合并修复 + 收口）：

| 表 | HEAD → 终态 | 构成 |
|---|---|---|
| `point_merge.csv` | 132 → 236 | +103（本批合并）+1（零材料收口） |
| `point_delete.csv` | 192 → 193 | +1（空壳删除） |
| `material_rebind.csv` | 1246 → 1351 | +107 追加、−2 删除（23 行原值改写） |
| `chapter_by_node.csv` | 2048 → 2006 | −42 = 40（合并修复）+1（收口合并）+1（删点） |
| `point_rename.csv` | 300 → 295 | −5 = 3（合并修复）+1+1（收口） |
| `point_relocation.csv` | 148 → 146 | −2（合并修复） |
| `prereq_map.csv` | 406 → 401 | 5 改写 + 5 删除 |
| `chapter_map.csv` | −43 行 | 合并写盘清悬空 |
| `chapter_point_relocation.csv` | −2 行 | 合并写盘清悬空 |

---

## 5. 终局门（本报告撰写时实跑；执行轮结论相同）

本报告在 2026-10-03 撰写会话中重跑了下列命令，输出与退出码如下（写盘步骤未重跑）：

1. `PYTHONPATH=tools python -m kb_build.promote --dry-run` → 全部 `OK`（gates / consistency / roundtrip / manifest：version→10，压平 0 条）+「（dry-run：门全绿，未写盘；将写入 version 10）」→ **EXIT=0**。
2. `PYTHONPATH=tools python -m kb_coverage.check_slice_verdicts` →「已检 232 个判定文件 / 70072 块：MATERIAL 23292、SKIP 46780、NEW 提案 218 / 不过 0 个」→ **EXIT=0**。
3. `python -m unittest discover -s tools/tests -t tools` → **Ran 624 tests，FAILED (failures=3)** → EXIT=1；红项即 §3.4 三条（晋升前预期，归因见 §6.2）。
4. 附加复核（并入本报告结论）：
   - `PYTHONPATH=tools python -m kb_build.check_pack_contract` →「主题 398、知识点 3761；问题 0」→ EXIT=0。
   - `PYTHONPATH=tools python - <<… gate.evaluate()` →「metrics total: 23 failed: 0」，23 门全部 value=0。
   - `python tools/ci/run_kb_checks.py` → gates / consistency / roundtrip / manifest / dense 全 `OK` → EXIT=0（dense 现状说明见 §7①）。

---

## 6. 遗留（gaps）记账

### 6.1 合并侧

1. **DISAGREE 两行按规则跳过并记账**（§1.2 已逐条列出）：#6/#7 的 B 同为「在指定条件下-下列有关铁单质的转化不能实现的是」；复核给出的正确去向是「四节点一次并为一个（簇主取材料最多的『物质间转化的5个常见易错点』），B 只并一次」。未执行的原因：执行 #7 方向的前置是「A 池 `chem-kb-invalid-equilibrium-flags`、`chem-hj-pingheng-biaozhi-shiyong-tiaojian` 两条跨域误绑须解绑不随迁」——实测这两条材料的唯一绑定就在该节点上，解绑会变成 0 绑定材料并使 `unbound_materials` 门（23 门均为 value==0）转红；复核未给出可改绑的正确节点，故不越界执行。已执行的是 #8（AGREE），但其复核 note 的前置「其余两 A 也须一并并入本簇主」仍未满足，留待绑定层先处理那两条误绑材料后再补（物质间转化的5个常见易错点 ← 物质间不能一步实现的转化、← 判断下列转化能否实现-能实现的打-不能实现的打）。
2. **合并轮步骤 5 的切片红**：当时不过 81 个文件、293 条 MATERIAL 行引用被并 slug（均为「节点不存在」）；机械修法属 materialize 流水线的判定输入（且部分切片可能处于重判作废队列），不属合并轮授权范围，当时未动、如实上报。**该遗留已在改绑轮修复**（§3.3：81 件 / 293 行改指幸存者，复跑不过 0）。
3. **复核 note 里的后续数据条件未执行**（不阻塞合并与门，逐条留档）：如 #25/#33/#42/#56/#84 等要求对误绑材料解绑或改绑（例：水的电离两池的非水电离平衡材料、金属腐蚀簇的烯烃/燃烧热类词面吸附材料、过氧化钠 B 池 4 条误绑、瞬时性突变类 A 池小船渡河材料、摩擦力的突变类 B 池弹力材料）；#0/#3/#17/#91 等要求随并改写幸存者边界（电镀/金属防护/新型无机非金属材料/光的波粒二象性）。这些是内容层依赖，不属本次合并动作。
4. **台账措辞小残差**：「金属矿物的开发利用」的退役条目 reason 是写入时文本「…并入 A。（并入链终点「金属冶炼」——与 #30 后续链式合流；复核 AGREE 2026-10-03）」，此后表内该行改为「…经 #32 链式合流…」。**无任何检查比对表与退役台账的 reason 文本，仅措辞差异**（本报告实读确认两处文本现况如此）。
5. 沙箱（`build/_merge_sandbox`）与全部临时文件已清理，合并轮未留报告文件（本文件的 §1–§2 即该批次的报告）。

### 6.2 改绑侧

1. **【按裁定①收尾·晋升前状态，不是未达成】3 个红项点名**：`test_current_tables_agree_with_pack` / `test_dropping_a_point_merge_row_turns_red` / `test_dropping_an_alias_row_turns_red`（均在 `tools/tests/test_kb_table_consistency.py`）。归因：工作表已按本批合并/改绑更新，成品包仍是上一代（release v9 / 3866 点），两代差 48 处（alias_map 10 / chapter_map 1 / chapter_by_node 1 / prereq_map 15 / point_delete 1 / point_merge 20）。并存不矛盾的事实：staging 包 ↔ 工作表 = 0 问题（本阶段的真不变量），两个「删行必红」场景在 staging + 台账重放上仍成立（用例逻辑没坏）。收口：`promote`（本工作流之后单独执行）把 staging 镜像回成品后三条随即转绿，此后重跑套件复核。测试文件一个字符未改。
2. **【晋升后待更新、本阶段按现状不动】** `DenseVectorAssetTest`（`core/data/src/test/.../DenseVectorAssetTest.kt:46,98` 的 3866）：本阶段 dense 资产仍与成品包同源（Python 侧 `test_dense_asset_gate` 全绿），现在改成 3761 会立刻变红；它随晋升 + dense 重打后更新。`test_kb_update_manifest` 的 `RealArtifactsTest` 计数（KIND_MERGE=132 / KIND_DELETE=192）读的是成品包，本阶段未变、模块实跑 OK；晋升后应随新表行数（236 / 193）更新。`test_kb_update_manifest` / `DenseVectorAssetTest` 本身：Kotlin 侧未 run（本机未跑 Gradle），Python 侧已 run。
3. **【111 条 REBIND 中 4 条未落表（合并后空操作，逐条列明）】**
   - `chem-fx-zheng-que-li-jie-shui-de-dian-li-ping-f48x04` [CHEMISTRY]：裁定 水的电离平衡移动的影响因素 → 水的电离平衡曲线；靶点已被本批并入现绑节点 → 解析后两点相同，材料已在目标（已由合并达成）。
   - `ext-phy-86fa7c991c-028` [PHYSICS]：自锁现象（已并入）→ 全反力与摩擦角；材料现绑即全反力与摩擦角 → 空操作。
   - `phys-hj3-rope-rod-spring-model-compare` [PHYSICS]：瞬时性突变类问题-轻绳-轻弹簧 → 弹力的三类模型（已并入前者）→ 空操作。
   - `ext-phy-de9921c433-058b` [PHYSICS]：连接体间内力的求解 与 连接体问题的分析易错-整体法与隔离法应用 双双并入 整体法与隔离法在受力分析中的应用 → 空操作。
   - 四条均按规则跳过、未写表、未改包。
4. **【既有改绑表行修复记录（非 111 条内）】** 见 §3.2 末段：47 行引用被并节点 → 改 to 8 行、改 from 16 行；24 行解析后 from==to（22 行原样保留、2 行按「跳过」删除）。
5. **命令口径说明**：`python -m kb_build.*` 直接跑会 `ModuleNotFoundError: kb_build`（实测），一律按模块自述加 `PYTHONPATH=tools` 运行；`python tools/kb_coverage/check_slice_verdicts.py` 亦加 `PYTHONPATH=tools`（它自己往 `sys.path` 插 tools，但保持一致）。
6. **跨线接口（只报不做）**：`docs/kb-outstanding-research-2026-10-02.md:836` 指明 D-1 改绑产出必须走内核线 KF-32 的 `BINDING_CHANGED` 事件链（改绑→全量重放）——本阶段只改包/sidecar 与权威表，未触发/未验证端侧重放链路（该链路归内核线 3B B4）。

---

## 7. 下一步

1. **dense 重打**：包变 ⇒ `.vec` 预期过期，`run_kb_checks` 的 dense 节会红，**这是预期的**。现状说明（本报告实跑）：dense 门比对的是成品包 `core/data/src/main/resources/knowledge/moe-2025-four-subjects-v1.json`（现仍为 3866 点 / 37327 别名一代），所以**现在**跑 `run_kb_checks.py` 的 dense 节是 OK；一旦 ② 把 v10 成品包落盘，`.vec` 即与包脱节、dense 节预期转红，须按 `tools/dense_build/` 流程重打并以新旁车哈希收口。
2. **promote 写成品包 v10**：dry-run 已全绿（「将写入 version 10」；manifest：version→10，压平 0 条）；正式晋升后，§6.2 的三条套件红项随成品包换代转绿，需重跑套件复核；同时按 §6.2 更新 `test_kb_update_manifest` / `DenseVectorAssetTest` 的成品包口径计数。
3. **push 后 CI 验证**：CI 与本地用同一入口 `tools/ci/run_kb_checks.py`（干净检出无 staging 时自动回退查成品）；push 后以 CI 结果为准复核 gates / consistency / roundtrip / manifest / dense 五节。
