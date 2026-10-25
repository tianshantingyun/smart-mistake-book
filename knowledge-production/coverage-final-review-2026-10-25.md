# KB 覆盖台账终审落账（2026-10-25 · kb-final-review-r20）

把 `build/agent-batch20/partial-review/decisions.json`（43 行终审裁定：UPGRADE 26 / KEEP 16 / WAIVE 1）
落进权威台账 `knowledge-production/kb-coverage-alignment-2026-v2.json`。判定方法：
**sidecar-content-evidence**——对主线 43 个 PARTIAL 行，逐行以其目标节点绑定的 25 分册侧车（
`core/data/src/main/resources/knowledge/moe-2025-teaching-support-v2-{01..25}.json`）全文逐块取证：
UPGRADE＝命中块证据摘录实质讲授该未单设子面；KEEP＝无实质证据但注记属「子面挂既有节点」粒度口径且目标节点有料，维持 PARTIAL；
WAIVE＝子面属价值面/态度类/活动类（性质判定＋无承载，登记豁免）。判定通道与逐行证据全文见 decisions.json。

## 输入完整性与改动前三门（均已实跑）

- 台账指纹：LF 归一化 sha256 = `78817628fe3436afaa3be75b6627418f19830c539a1ad8d8211c73818322d973`，
  与 decisions.json / rows.json 记录一致（盘面 CRLF 仅为检出换行，git blob 同为该指纹）。
- porcelain 守卫：三个待改文件改动前均无 M/?? 状态。
- 门基线：`PYTHONPATH=tools python tools/kb_coverage/alignment.py --check` 改动前 exit 0。
- 备份：`build/agent-batch20/backup/{kb-coverage-alignment-2026-v2.json,f14-waivers.csv,curriculum_alignment.csv}.pre-finalreview-2026-10-25`。

## 变更清单（只动台账，零写包）

1. `knowledge-production/kb-coverage-alignment-2026-v2.json`：43 对应行追加
   `finalReview{date:"2026-10-25", reviewer:"kb-final-review-r20", method:"sidecar-content-evidence", decision, evidence}`；
   26 个 UPGRADE 行 status→COVERED；1 个 WAIVE 行 status→NOT_COVERED 且 targetRef 置空（NOT_COVERED 行不挂 target
   为台账既有 schema 约定（改前 198/198 行如此）且为 alignment.py --check 对 CSV 的硬要求）；16 个 KEEP 行 status 不变；
   行级其余字段（note/reviewState/reviewer/reviewedAt/evidencePhrase）逐字段对拍核对零改写，661 行行序不变；
   subject 级与 total 的 counts 块（status 的派生投影）随终态重算。
2. `knowledge-production/f14-waivers.csv`：按既有 5 列格式追加 1 行
   `required-chemistry-social-development-candidate-001, CHEMISTRY, reason=性质判定+无承载+登记豁免, evidence=绿色化学节点 16 条绑定材料「可持续发展」0 命中（rows.json hit=False）, date=2026-10-25`；
   既有 9 行字节不动（CRLF 保留）。
3. `tools/kb_coverage/tables/curriculum_alignment.csv`：无 JSON→CSV 生成器（`alignment.py` 只读该 CSV，
   `--build` 方向为 CSV→JSON；全 tools/ 检索无写方），按 ask 写转换脚本
   `build/agent-batch20/scripts/final_review_apply_r20.py` 重生成投影：与台账逐行对拍——661 行行数一致、
   status 集合一致（COVERED 416 / PARTIAL 46 / NOT_COVERED 199）、target 规则一致；字节面仅 27 行变更
   （26 行 status→COVERED ＋ 1 行 status→NOT_COVERED 且 target 清空），其余 635 行字节不动。

## 终态计数（主线 426 分母 ＝ required ＋ selective 必修/选择性必修行；选修 235 行不计入）

| 口径 | 分母 | COVERED | PARTIAL | NOT_COVERED | UNDECIDED | 覆盖（COVERED/分母） |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 主线 | 426 | 400 | 16 | 10 | 0 | 0.939 |
| 选修（不计入主线） | 235 | 16 | 30 | 189 | 0 | — |
| 全账 | 661 | 416 | 46 | 199 | 0 | 0.6293 |

分科终态（total / COVERED / PARTIAL / NOT_COVERED / UNDECIDED）：

- MATH：308 / 151 / 26 / 131 / 0
- PHYSICS：138 / 86 / 9 / 43 / 0
- CHEMISTRY：73 / 59 / 5 / 9 / 0
- BIOLOGY：142 / 120 / 6 / 16 / 0

主线 426 分母的 COVERED/PARTIAL/NOT_COVERED 新计数：**COVERED 400 / PARTIAL 16 / NOT_COVERED 10**
（改前 374 / 43 / 9；Δ＝26 UPGRADE 行 PARTIAL→COVERED、1 WAIVE 行 PARTIAL→NOT_COVERED）。

## 43 行逐行终态表（顺序同 decisions.json）

| # | 科 | candidate_slug | 目标节点 | 终审裁定 | 终态 status | 证据（首块/口径） |
| ---: | --- | --- | --- | --- | --- | --- |
| 1 | MATH | `required-functions-candidate-002` | 函数的三要素 | UPGRADE | COVERED | ext-mat-80fe6c0ef5-001 |
| 2 | MATH | `required-functions-candidate-014` | 对数函数的概念 | UPGRADE | COVERED | ext-mat-1544d7c08e-001 |
| 3 | MATH | `required-functions-candidate-024` | 三角函数的图象与性质 | KEEP | PARTIAL | 5 处命中摘录均为单调性/周期/最值的公式应用话术（如 ex… |
| 4 | MATH | `required-geometry-algebra-candidate-002` | 平面向量的概念 | UPGRADE | COVERED | ext-mat-a5c8e4e572-001 |
| 5 | MATH | `required-geometry-algebra-candidate-014` | 平面向量的坐标运算 | KEEP | PARTIAL | 35 处命中均为坐标运算/基底法/数量积技巧块（如 ext-… |
| 6 | MATH | `required-probability-statistics-candidate-004` | 随机事件 | KEEP | PARTIAL | rows.json hit=False（0 命中） |
| 7 | MATH | `required-probability-statistics-candidate-010` | 分层随机抽样 | UPGRADE | COVERED | ext-mat-c94bc82d48-003 |
| 8 | MATH | `selective-functions-candidate-012` | 导数定义在极限中的计算 | UPGRADE | COVERED | math-kb-derivative-definition-limit |
| 9 | MATH | `selective-functions-candidate-016` | 基本初等函数的导数 | UPGRADE | COVERED | ext-mat-3cd410f924-003b |
| 10 | MATH | `selective-geometry-algebra-candidate-001` | 空间向量的有关概念 | KEEP | PARTIAL | rows.json hit=False（0 命中） |
| 11 | MATH | `selective-geometry-algebra-candidate-008` | 投影向量 | UPGRADE | COVERED | ext-mat-e72ba411f6-004b |
| 12 | MATH | `selective-geometry-algebra-candidate-014` | 直线的方程 | KEEP | PARTIAL | 36 处命中摘录均为直线方程选用语境把斜率/倾斜角当已知量使… |
| 13 | MATH | `selective-geometry-algebra-candidate-018` | 两条直线的位置关系 | KEEP | PARTIAL | rows.json hit=False（0 命中） |
| 14 | MATH | `selective-geometry-algebra-candidate-020` | 直线与圆的位置关系 | KEEP | PARTIAL | rows.json hit=False（0 命中） |
| 15 | MATH | `selective-geometry-algebra-candidate-021` | 直线与圆的位置关系 | KEEP | PARTIAL | ext-mat-45351bbbe7-012 |
| 16 | MATH | `selective-geometry-algebra-candidate-022` | 圆锥曲线的综合问题 | KEEP | PARTIAL | 12 处命中均为技巧/结论块（如 math-hj-yuanz… |
| 17 | MATH | `selective-geometry-algebra-candidate-026` | 圆锥曲线的综合问题 | KEEP | PARTIAL | ext-mat-0e56aad79f-053 |
| 18 | MATH | `selective-probability-statistics-candidate-007` | 全概率 | UPGRADE | COVERED | math-mo-quangailv-yu-beiyesi |
| 19 | MATH | `selective-probability-statistics-candidate-012` | 正态分布 | UPGRADE | COVERED | ext-mat-d04314ab0b-002h |
| 20 | MATH | `selective-probability-statistics-candidate-013` | 回归分析 | UPGRADE | COVERED | ext-mat-914a1f50be-004 |
| 21 | MATH | `selective-probability-statistics-candidate-014` | 回归分析 | UPGRADE | COVERED | ext-mat-d04314ab0b-010 |
| 22 | MATH | `selective-probability-statistics-candidate-016` | 回归分析 | UPGRADE | COVERED | ext-mat-914a1f50be-004 |
| 23 | MATH | `selective-probability-statistics-candidate-017` | 独立性检验 | UPGRADE | COVERED | math-hj-s94-contingency |
| 24 | PHYSICS | `required-physics-3-candidate-007` | 必备实验仪器的原理及使用 | KEEP | PARTIAL | rows.json hit=False（0 命中） |
| 25 | PHYSICS | `required-physics-3-candidate-013` | 磁场的基本概念和性质 | KEEP | PARTIAL | rows.json hit=False（0 命中） |
| 26 | PHYSICS | `required-physics-3-candidate-017` | 电磁波 | UPGRADE | COVERED | ext-phy-91ff66998d-005 |
| 27 | PHYSICS | `selective-physics-2-candidate-010` | 电磁说-麦克斯韦认为光是一种电磁波 | UPGRADE | COVERED | phys-kb-electromagnetic-wave-theory |
| 28 | PHYSICS | `selective-physics-2-candidate-012` | 无线电波的发射 | UPGRADE | COVERED | ext-phy-392e36484a-008 |
| 29 | PHYSICS | `selective-physics-2-candidate-013` | 电磁波 | UPGRADE | COVERED | ext-phy-fffc8cde9c-003c |
| 30 | PHYSICS | `selective-physics-2-candidate-016` | 常见传感器的特点 | UPGRADE | COVERED | ext-phy-206fb04353-103 |
| 31 | PHYSICS | `selective-physics-3-candidate-014` | 近代物理学史-相关粒子的发现和实验 | KEEP | PARTIAL | rows.json hit=False（0 命中） |
| 32 | CHEMISTRY | `required-chemistry-social-development-candidate-001` | 绿色化学 | WAIVE | NOT_COVERED | 性质判定（价值/理念面）＋无承载（检索 0 命中） |
| 33 | CHEMISTRY | `required-chemistry-social-development-candidate-002` | 常见金属材料 | KEEP | PARTIAL | rows.json hit=False（0 命中） |
| 34 | CHEMISTRY | `required-chemistry-social-development-candidate-004` | 酸雨 | KEEP | PARTIAL | ext-che-813727c8d6-005 |
| 35 | CHEMISTRY | `selective-organic-foundations-candidate-007` | 常见有机合成路线 | KEEP | PARTIAL | rows.json hit=False（0 命中） |
| 36 | BIOLOGY | `required-genetics-evolution-candidate-017` | 生物有共同祖先的证据 | UPGRADE | COVERED | bio-kb-common-ancestor-other-evidences |
| 37 | BIOLOGY | `required-genetics-evolution-candidate-021` | 自然选择 | UPGRADE | COVERED | ext-bio-c496a62e4c-003d |
| 38 | BIOLOGY | `selective-homeostasis-regulation-candidate-003` | 内环境 | UPGRADE | COVERED | bio-kb-cell-exchange-with-external-systems |
| 39 | BIOLOGY | `selective-homeostasis-regulation-candidate-010` | 神经系统的结构 | UPGRADE | COVERED | ext-bio-812daccffe-001 |
| 40 | BIOLOGY | `selective-biology-environment-candidate-003` | 种群数量的变化 | UPGRADE | COVERED | ext-bio-c1f36a400a-002b |
| 41 | BIOLOGY | `selective-biology-environment-candidate-010` | 物质循环 | UPGRADE | COVERED | ext-bio-5f510cdc4a-1615 |
| 42 | BIOLOGY | `selective-biotech-engineering-candidate-011` | 植物组织培养 | UPGRADE | COVERED | ext-bio-c168f35dac-001 |
| 43 | BIOLOGY | `selective-biotech-engineering-candidate-022` | 基因工程 | UPGRADE | COVERED | ext-bio-5b6cb16a7f-013 |

（逐行证据全文＝台账该行 `finalReview.evidence`＝decisions.json 对应条目，二者逐字一致已对拍。）

## 薄料 3 条 thinAssessment（来自 decisions.json thinTargetsAssessment）

3 个薄料节点行中：

- 行 07「导数定义在极限中的计算」（≈4 条材料）→ UPGRADE（命中块可作答，无需源料评估）。
- 行 26「电磁说-麦克斯韦认为光是一种电磁波」（≈4 条材料）→ UPGRADE（PRIMARY 绑定块侧车全文复读取证，无需源料评估）。
- 行 23「必备实验仪器的原理及使用」（1 条材料）→ KEEP，评估如下（**只评估、不写入**——本终审零写包，未据该评估新增或改动任何材料）：
  - 已有现成料：`knowledge-production/agent-authored-materials-2026-10-13.jsonl` 第 2 行 slug=`phy-basic-instruments-principle-use`
    《必备实验仪器的原理与使用要点》恰 PRIMARY 绑定本薄节点，其 ④ 节实质讲授多用电表/欧姆表使用，可直接补足「会使用多用电表」半边。
  - 缺口：「电路元器件识别及其作用」半边在 agent-authored-materials-2026-10-13.jsonl、-window.jsonl 与 candidate-merge/authored_materials.jsonl（80 行）三处检索「电路元/元器件」均 0 命中，无现成可用料；该半边按行注记挂「电路中的基本概念」既有节点。
  - window 文件（4 行）仅 mat-data-acquisition-channels 含「传感器」字样，与本子面无关。

## 「零写包」登记

本次终审触碰面仅限：台账 JSON、f14-waivers.csv、curriculum_alignment.csv、本 md、
`build/agent-batch20/scripts/final_review_{apply,verify}_r20.py` 两脚本、backup/ 三份备份与本清单——全部位于
`knowledge-production/`、`tools/kb_coverage/tables/`、`build/`（gitignored）之下；两脚本无任何写向
`core/data/` 的代码路径（25 个 sidecar 与包主件由本终审零改动）。
porcelain 时点记录：本终审三件写盘完成并复核后（13:04 前后），`git status --porcelain -- core/data/src/main/resources/` 输出为空；
13:08:20 起 `dense/bge-small-zh-int8.vec{,.json}` 出现 M——经 mtime 核验为**并行任务（标题重建）**所写，非本终审触碰。

## 门与复算命令

```bash
# alignment 门（改动前、改动后各实跑一次，均 exit 0）
PYTHONPATH=tools python tools/kb_coverage/alignment.py --check
# → 台账完整：全部候选恰有一行判定，target 均在包中（MATH 308 / PHYSICS 138 / CHEMISTRY 73 / BIOLOGY 142）

# 三方对拍（台账 JSON ↔ 判定表 CSV ↔ 豁免表 CSV，7 项断言，exit 0）
python build/agent-batch20/scripts/final_review_verify_r20.py

# 主线 426 终态计数复算
python -c "import json,collections;d=json.load(open('knowledge-production/kb-coverage-alignment-2026-v2.json',encoding='utf-8'));print(dict(collections.Counter(r['status'] for s in d['subjects'] for r in s['statements'] if r['candidateRef'].rsplit('/',1)[-1].rsplit('-candidate',1)[0].startswith(('required-','selective-')))))"
# → {'COVERED': 400, 'PARTIAL': 16, 'NOT_COVERED': 10}
```

run_kb_checks（`tools/ci/run_kb_checks.py`）**不含 alignment 节**：grep 该文件与 `tools/kb_build/*.py` 均无
alignment/curriculum_alignment 引用（dense_build 的 alignment 命中为设备对齐 fixture，无关）——ask 中
「run_kb_checks 若含 alignment 节也要绿」条件不成立；alignment 门以 `alignment.py --check` 为准（已绿）。

## 剩余风险

- `alignment.py --build` 以判定表 CSV 为源**整册重建**台账 JSON，现无合并逻辑，重建会丢 43 行 `finalReview` 叠加层；
  本次未运行 `--build`。若将来重建，需先备份并把 finalReview 按 candidateRef 回并（或给 alignment.py 增加合并逻辑）。
- 判定表 CSV 行级 note/evidence_phrase 仍是原 AI 评审（2026-09 批）文本；终审证据在台账 finalReview 与 decisions.json，
  两层分工：CSV 供门与重建，finalReview 供终审追溯。

## 触碰文件清单

见 `build/agent-batch20/partial-touched-files.txt`。
