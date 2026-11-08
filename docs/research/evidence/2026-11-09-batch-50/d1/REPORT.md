# D-1 语义绑定审计报告（批次 50）

- audit_id：`B50-D1-2026-11-08`
- 日期：2026-11-08
- 包：`moe-2025-four-subjects-v1`
- 标注性质：**model-labeled**（单一模型，非人工专家）
- 门定义：KC 语义绑定精确率，分层抽样 + 单侧 95% Wilson 下界 **≥0.95**（计划 `.zcode/plans/plan-2026-11-09-batch-50-release.md:10`、`:37`；口径见 `docs/research/2026-11-09-binding-audit.md:9`、`:202-215`）
- 本报告不修改任何门阈值；不含 KB 生产文件的任何改动。

## 0. 结论（先看这里）

| 项 | 结果 |
|---|---|
| **主判据** 单侧 95% Wilson 下界 | **0.9756913**（97.5691%）≥ 0.95 → **PASS**，余量 **+2.5691pp** |
| 分层加权点估计 | 0.9821666（98.2167%），SE 0.0033830，n_eff 1530.44 |
| 未加权正确率（参考） | 1557/1600 = 0.9731250 |
| **护栏 G2**（每层 Wilson 下界 ≥0.90） | overfull 0.9770088 通过；**normal 0.8979148 未通过（差 0.2085pp）**（预注册 no-FPC 口径；FPC 口径下为 0.9005766 → 见 §4.3） |
| **总判定** | **`PASS main / FAIL guardrail`**（`overall_pass = false`） |
| 错误项 | 43 条 = 39 REBIND + 4 NONE（2.6875%） |
| 判定一致性 κ | **未运行（NOT RUN）**，见 §7；按 R2 §4.5，主判据应读作"模型判定下的暂定值" |

一句话：**加权主判据过门，但小层（normal）护栏跳闸，且判定一致性未认证。**不得把本报告读作"3D D-1 已整体通过"。

> **2026-11-09 修复环补记（必读）**：抽样框 `build/kb-staging/overfull_nodes.csv` 在抽样后
> 被 D-2 原地重写，`sample_d1.py --check` 现为 `match=false` / exit 2，抽样时点名单不可重建。
> 影响已量化为 **层归属 0 条不一致、主判据 0.9756913→0.9756882（仍 PASS）、normal 护栏仍 FAIL**；
> 但本报告 §2 "可重放"与 §8 "match:true" 的表述**已被取代**。详见 `FRAME-DRIFT.md` /
> `frame-drift.json`。门值本身与 §4 的算术不受影响。

## 1. 审计对象与抽样框

包内实测（`manifest.json`，由 `sample_d1.py` 从包文件直接统计，非引用旧文档）：

| 量 | 值 |
|---|---|
| 知识节点 | 3,280 |
| 材料 / 绑定 | 64,853 / 64,853（1 材料恰好 1 绑定） |
| `overfull` 层绑定 | 62,948（材料所绑节点出现在 `build/kb-staging/overfull_nodes.csv`，共 2,482 个节点 id，全部可在当前包解析）**——抽样时点读数；该文件已被 D-2 覆盖为 62,944/2,481，见 `FRAME-DRIFT.md`** |
| `normal` 层绑定 | 1,905（其余节点） |
| pack scope sha256 | `859ae71e64fcee0ba96113b5f9572f8af209219fac613d1c5fd759b18bc7a481` |

分层理由与 R2 一致（`docs/research/2026-11-09-binding-audit.md:119-134`）：`overfull` 节点平均承载约 25 条材料，是错绑主要风险面；`normal` 层权重仅 2.9%，但会被加权值遮住，故单列护栏（R2 §2.5，`:192-196`）。

**未采用 thin 子层**：R2 指出 thin 名单已过期（11 条未解析，`:111`），且按名单分层会使权重不可信，因此本批只用 2 层框；`normal` 在本包中实际上近似"≤4 条材料的薄料节点"集合，这也是其错误率更高的直接原因（§6）。

## 2. 抽样设计（确定性，无 RNG）

`sample_d1.py:13-15`、`:183-196`、`:326-328`：

```
seed        = sha256(pack_scope_sha256 | audit_id)
层内排序     = 按 sha256(seed | layer | material_slug) 升序
start       = int(sha256(seed | layer | "start")[:8], 16) mod floor(N_layer / n_layer)
第 i 个     = ordered[start + floor(i * N_layer / n_layer)]，i = 0..n_layer-1
```

- `seed = 148a5f126b7a6e3203a4520d273dd84694bdf10e9faac12f9f851c2d4ffd48b3`
- 样本量：overfull **1,300** / normal **300**，合计 **1,600**（R2 §2.3 推荐档；计划下限 1,200 已满足）
- 抽样比：overfull 2.07%，normal 15.75%
- 全流程只用 `hashlib`，无 `random`；同一包 sha + 同一 audit id 必得同一样本
- 复放自检（**2026-11-09 修复环实跑，取代抽样时点读数**）：`python -I -X utf8 sample_d1.py --check`
  → `{"check": "sample_keys", "match": false, "expected": 1600, "actual": 1600}`，**exit 2**。
  原因与量化见 `FRAME-DRIFT.md`：抽样框 CSV 在抽样后于 15:20 被 D-2 重写；旧名单（sha `fce8269c…`）
  未随证据保存，因此**本报告不能声称样本键可在当前树复放**。已判定样本本身不受影响
  （1,600 条按当前框复检，层归属 0 条不一致；按当前 N 复算门判不变）。

样本冻结后才开始判定；判定期间未重抽、未补样、未删样（固定 n、一次判读，符合 R2 §3.5）。

## 3. 判定协议

- **判定单元**：一条绑定（材料 × 现绑节点）。
- **判定词**：`CORRECT` / `REBIND` / `NONE`（与 R2 §4.1 处置词表对应：KEEP / REBIND / DROP_MATERIAL 或 NEW_POINT）。
- **两步法**（`JUDGE_PROMPT.md`，sha256 `56158a5e74c5b613…`）：先用自己的话写出材料自身主题，再与该节点的 name/kind/topic/boundary 比较；节点 `aliases` **不提供**给判定（alias 由历史材料标题派生，提供会构成同义反复）。
- **盲层**：判定输入不含 layer 字段，32 个 batch 文件按样本顺序混排；判定完成后才在 `compile_judgments.py` 中合入层信息。
- **证据**：每条必须给材料五个字段（title/summary/applicability/content/boundary）中的**逐字子串**，≤160 字符；`check_raw.py` 与 `compile_judgments.py` 双重机械校验（`quote_is_substring` 全为 true）。
- **REBIND 约束**：目标必须存在于当前包、必须同科、不得等于现绑；`check_raw.py` 逐条校验。
- **置信**：high/medium/low 由判定时给出；全样本 high 1,538、medium 62、low 0；43 条错误中 high 34、medium 9。

判定分 32 批执行（每批 50 条），原件为 `verdicts_raw/batch-01.tsv … batch-32.tsv`；每批入库前跑 `check_raw.py`，最终 32 批全部通过：`batches 32 rows 1600 problems 0`。

## 4. 结果

### 4.1 分层

| 层 | N | n | 正确 | 错误 | p | 单侧 95% Wilson 下界 | 护栏 0.90 |
|---|---:|---:|---:|---:|---:|---:|---|
| overfull | 62,948 | 1,300 | 1,279 | 21（19 R + 2 N） | 0.9838462 | 0.9770088 | 通过 |
| normal | 1,905 | 300 | 278 | 22（20 R + 2 N） | 0.9266667 | **0.8979148** | **未通过** |
| 合计 | 64,853 | 1,600 | 1,557 | 43 | — | — | — |

### 4.2 主判据（R2 §3.1 唯一口径）

```
p_hat = Σ_h w_h · p_h                         w_h = N_h / N
Var   = Σ_h w_h² · p_h(1-p_h)/n_h · FPC_h      FPC_h = (N_h-n_h)/(N_h-1)
n_eff = p_hat(1-p_hat) / Var
L     = Wilson_lower(k = p_hat·n_eff, n = n_eff, z = 1.6448536269514722)
PASS iff L ≥ 0.95
```

代入：`p_hat = 0.9821665565557136`，`SE = 0.0033830036607307647`，`n_eff = 1530.4368543831492`，`L = 0.975691305961763` → **PASS**。

### 4.3 稳健性（不改变主判据，仅作敏感性披露）

| 口径 | SE | 单侧 95% 下界 | ≥0.95 |
|---|---:|---:|---|
| 主判据（含 FPC） | 0.0033830 | 0.9756913 | 是 |
| 不含 FPC | 0.0034224 | 0.9756044 | 是 |
| 设计效应 deff=1.05 | 0.0034665 | 0.9755069 | 是 |

三种口径都远高于 0.95，主判据结论对 FPC 与轻度设计效应不敏感。

**护栏口径敏感性（2026-11-09 修复环补记；原报告未披露）**：主判据含 FPC，而 G2 护栏按预注册
口径是**每层 no-FPC** Wilson 下界（`docs/research/2026-11-09-binding-audit.md:192-196`）。若把同一
FPC 也用于护栏，normal 层会翻过 0.90：

| normal 层护栏口径 | n_eff | Wilson 下界 | ≥0.90 |
|---|---:|---:|---|
| 预注册 no-FPC（n=300, k=278） | 300 | **0.8979148347511259** | **否** |
| FPC（抽样时点 N=1,905） | 355.8878 | 0.9005765917383107 | 是 |
| FPC（当前框 N=1,909） | 355.7489 | 0.9005708241640439 | 是 |

因此 `overall_pass=false` 是**按预注册 no-FPC 口径**成立，对口径敏感；不得据此断言
"normal 层真实精确率 <0.90"（见 §7.4）。口径与阈值均不改，敏感性如实并列。
复算：`python -I -X utf8 frame_drift_check.py` → `frame-drift.json`
（`estimate_under_current_frame.per_layer.normal`）。

### 4.4 护栏为何跳闸

normal 层 22 错（20 REBIND + 2 NONE），p=0.9267；n=300 时 Wilson 下界 0.8979，比 0.90 低 0.2085pp。按 R2 §2.5 的同一张表，n=300 的护栏约等价于要求该层真值 ≳0.928；当前点估计 0.9267 恰在门槛下方。**只要 normal 层少 1 条错误（279/300），下界即为 0.9017308，护栏翻过。**这说明护栏结论本身处在 1 条判定的宽度内，不应被读作"normal 层确定不达标"，但也不能因此放宽阈值——按门禁纪律，阈值不动，事实照写。另见 §4.3：若把 FPC 用于护栏，下界为 0.9005766（抽样时点 N）；预注册口径是 no-FPC，故本条按 0.8979 判红，不按 0.9006 判绿。

## 5. 错误项清单（改绑 / 剔除）

完整 43 条见 **`errors.csv`**（含 `current_node_id`、`current_node_slug`、`current_node_name`、`target_node_id`、`evidence_quote`、`note`，共 12 列）。汇总：

- 39 条 `REBIND`：现绑不是"错学科"，而是"错粒度/错上位点"，同科均存在更贴切的既有节点。
- 4 条 `NONE`：材料本身无可归知识，或同科无既有落点。逐条：

| material_slug | 层 | 现绑节点 | 依据（逐字引文） | 处置建议 |
|---|---|---|---|---|
| `chem-fx-yan-dao-qi-zhong-de-shi-zhu-yao-de-da-f51x12` | normal | NOx 对大气的污染 | `为了监测其含量，选用如下采样和检测方法` | 仅题干导语，无方法内容 → 撤料或补全材料；删绑定不可行（数据契约 1 材料 ≥1 绑定） |
| `ext-phy-2cea5a0fdb-045` | normal | 平抛与竖直面结合问题 | `平抛小球恰好沿切线进入圆轨道时` | 同科无"平抛→圆轨道切向衔接"节点 → NEW_POINT 候选，或并入平抛运动 |
| `chem-fx-you-ji-hua-he-wu-de-ming-ming-jie-gou-f64x10` | overfull | 有机化合物的空间结构 | `有机化合物的命名、结构简式的书写、有机化学方程式的书写等` | 仅章节标题罗列；同科无覆盖三者的节点 → NEW_POINT 候选/拆点 |
| `chem-fx-zai-biao-zhun-zhuang-kuang-xia-f01x08` | overfull | 气体摩尔体积的适用条件 | `在标准状况下，将含a L NO2和b L NO的混合气体通入625 g质量分数为21.2%的纯碱溶液中` | 仅题干数值情境，无可归结论 → 撤料 |

REBIND 目标分布：`电磁波谱与各波段的特性` ×3，`晶胞中粒子数目、配位数、坐标与密度的计算` ×2，其余各 1。错误按学科：normal 层 CHEMISTRY 12、MATH 5、PHYSICS 5；overfull 层 BIOLOGY 8、CHEMISTRY 8、PHYSICS 5。

**未执行任何改绑/删料动作。**`git status --porcelain -- core/data/src/main/resources/knowledge` 输出为空（exit 0），KB 生产文件目录干净；本报告只交付清单，若后续该目录进入在飞状态，应按计划只提交补丁而非整文件覆盖。

## 6. 错误的性质（供修复排期）

1. **错粒度为主**：39 条 REBIND 中绝大多数是"现绑为上位概念/总论节点，材料讲的是其下一个具体考点"（如 电磁波总论 → 电磁波谱；磁场基本概念 → 液柱模型；热学/光学数据处理 → 电阻率实验数据处理）。这类错误不影响学科归属，但会稀释该节点的召回语义。
2. **薄料节点更易错**：normal 层错误率 7.33% 是 overfull 层 1.62% 的 4.5 倍，与"薄料节点缺少足够材料形成语义约束"的假设一致；但本批是单轮观察，不作因果结论。
3. **NONE 集中在"材料残缺"**：4 条中 3 条是"只有题干/标题、没有知识结论"，1 条是同科无落点。修复优先级应低于 REBIND（影响面小），但需产品侧决定"撤料"是否可接受。

## 7. 未验证项与局限（必须随结论一起引用）

1. **κ 双重判定：NOT RUN。** R2 §4.5（`:340-344`）要求每层 10%（160 条）由第二个会话盲判并报 Cohen's κ，且预注册 `κ<0.60 → 该轮判定不得用于门`。本批只有单模型单轮判定，**没有 κ**。因此：
   - 主判据数值是按 R2 §3.1 公式对已判定样本的正确计算，可作为"模型判定结果"引用；
   - 但它**不满足 R2 对"可用于门"的判定一致性前置条件**，只能标为 **model-labeled 暂定值**，不能对外声称"3D 判定可复现"。
   - 未运行的原因：本轮只有当前会话一个判定者，且判定者已见过全部结论，补做的"第二遍"不构成盲判，做出来也是假 κ；如实留空优于伪造一致性。
2. **无人类专家复核**（R2 §5；`stats.json.metadata.human_expert=false`）。
3. **抽样误差之外还有判定噪声未计入**（R2 `:285` 第 4 条）；Wilson 区间只覆盖抽样误差。
4. **护栏结论脆弱**：normal 层差 1 条错误即翻转（§4.4）。不得据本报告断言"normal 层真实精确率 <0.90"，只能说"本轮证据不足以证明 ≥0.90"。
5. **非 SRS**：分层 + 系统抽样 + FPC，推断仅覆盖当前包当前快照；包内容变化后需新 seed 重抽（R2 §4.6）。
6. **alias 未纳入判定**：节点 alias 由历史材料标题派生，若把 alias 当判定依据会循环论证；因此本审计不评价"alias 是否与节点语义一致"，该维度未覆盖。
7. **材料文本截断**：判定输入将材料文本截到 500 字符、节点 boundary 截到 120 字符（`sample_d1.py`），极长材料的尾部信息未进入判定；对 p50=328、p90=554 字符的分布影响有限，但属未覆盖面。
8. **本报告不涉及** 3D 的 D-2/D-3、3E、MVA、AUD-08；AUD-08 acceptance 未在本审计中运行。
9. **抽样框已被下游工具重写，样本键不可复放**（2026-11-09 修复环补记）：抽样时点的
   `overfull_nodes.csv`（sha `fce8269c…`，63,102 行 / 2,482 节点）在 15:20 被 D-2
   `overfull_nodes --write` 原地覆盖为 `ce7d6823…`（62,944 / 2,481）；旧文件未随证据保存。
   已量化：1,600 条样本层归属 0 条不一致、按当前 N 复算主判据 0.9756882（PASS）、
   normal 护栏 0.8979148（FAIL），门判不变；但**"同一框 + 同一 seed 必得同一样本"只在旧框存在时成立**。
   详见 `FRAME-DRIFT.md`。下一步：抽样前把框复制进证据目录并只读该副本。
10. **护栏口径敏感**：见 §4.3——FPC 口径下 normal 护栏 0.9005766 通过，预注册 no-FPC 口径
    0.8979148 未通过；本报告按预注册口径判红，并披露差异。

## 8. 复现（本轮实跑，命令与输出）

工作目录：`docs/research/evidence/2026-11-09-batch-50/d1/`

```bash
# 1) 样本复放（确定性；2026-11-09 修复环实跑，抽样框已漂移 → exit 2）
python -I -X utf8 sample_d1.py --check
# {"check": "sample_keys", "match": false, "expected": 1600, "actual": 1600}
# EXIT=2  ← 抽样时点旧框已被 D-2 覆盖；正确行为，不得改脚本/换 sample.csv 求绿

# 1b) 漂移量化（抽样框 → 当前框；层归属 / 按当前 N 复算 / FPC 护栏敏感性）
python -I -X utf8 frame_drift_check.py
# 见 frame-drift.json；EXIT=0 = 结论不变

# 2) 32 批判定原件机械校验（逐批 JSON + 汇总）
for f in verdicts_raw/batch-*.tsv; do python -I -X utf8 check_raw.py "$f"; done \
 | python -I -X utf8 -c "import sys,json;rs=[json.loads(l) for l in sys.stdin if l.startswith('{')];print('batches',len(rs),'rows',sum(r['rows'] for r in rs),'problems',sum(r['problems'] for r in rs))"
# batches 32 rows 1600 problems 0

# 3) 合并 + 门值
python -I -X utf8 compile_judgments.py    # COMPILE_EXIT=0，写 judgments.csv / errors.csv / stats.json

# 4) 独立复算（不 import 上一步代码）
python -I -X utf8 recompute.py            # RECOMPUTE_EXIT=0，all_match=true
```

`recompute.py` 只读 `manifest.json` + `judgments.csv`，重算点估计、SE、n_eff、主判据下界、两层正确数并与 `stats.json` 逐项比对，容差 1e-12（n_eff 1e-9）；本轮 `all_match: true`。

## 9. 产物

| 文件 | 作用 | sha256（前 24 位） |
|---|---|---|
| `manifest.json` | 框、层权重、seed、抽样规则、包 sha | `aea1cf97c291f500e518f5ac` |
| `sample.jsonl` | 1,600 条样本（含材料全文、当前节点、层） | `12fc0758f85732ab8dcd5ede` |
| `sample.csv` | 样本扁平表 | `8a26f5323ce0c863b0ce59b7` |
| `judge_input/batch-01..32.json` | 盲层判定输入（50 条/批） | 逐批 sha 记入 `judgments.csv.batch_sha256` |
| `judge_input/compact-01..32.txt` | 判定用紧凑渲染 | — |
| `verdicts_raw/batch-01..32.tsv` | 判定原件（32×50 行） | — |
| `judgments.csv` | 1,600 条判定 + 层/学科/引文校验/模型元数据 | `17f057c0b500d82401d65947` |
| `errors.csv` | **43 条改绑/剔除清单**（含目标 node id） | `92798f66a15a6ca84a63b3fe` |
| `stats.json` | 门值、分层、敏感性、判定字符串 | `1f6b8189cb2b057eebdff7ab` |
| `stats_recomputed.json` | 独立复算结果与比对 | `b66df4f95f2765a988dd07a4` |
| `JUDGE_PROMPT.md` | 判定口径（v1） | `56158a5e74c5b613c0201b16` |
| `sample_d1.py` | 框构建 + 确定性抽样 + 输入生成 | `7a5fbdef4775adc0c1cd40a4` |
| `check_raw.py` | 单批判定原件校验 | `4b041248b4489811ada8c213` |
| `compile_judgments.py` | 合并 + 门值 + 错误清单 | `95e1d66e662c24918e277293` |
| `recompute.py` | 独立复算 | `1ed117494fefb83526ac5aad` |
| `render_compact.py` | 判定输入紧凑渲染 | `e90b7776f55cffd6fa6a86a1` |
| `find_targets.py` | REBIND 目标检索（只读） | `1b8cf3b08238146463fb84fb` |
| `node_index.tsv` | 3,280 节点查找表 | `e4d3a970224d0a8f8ccf49de` |
| `FRAME-DRIFT.md` | 抽样框漂移事实/量化/后续硬规则（修复环） | 见文件本身 |
| `frame-drift.json` | 漂移量化机器读数（replay/层归属/当前 N 复算/FPC 敏感性） | 运行时由脚本写入 |
| `frame_drift_check.py` | 漂移复算器（只读；不重抽、不改判定） | 运行时由脚本自记 `checker_sha256` |

元数据（`stats.json.metadata`）：`labeler_kind=model`，`model_id=deepseek/deepseek-flash (ZCode harness)`，`blind_to_layer=true`，`human_expert=false`，`judged_at=2026-11-08T00:00:00+08:00`。

## 10. 附录：43 条错误明细

`R` = REBIND，`N` = NONE；目标列给的是目标节点 slug（同科既有节点已校验存在）。

| 层 | 学科 | material_slug | 判定 | 目标节点 slug | 置信 |
|---|---|---|---|---|---|
| normal | CHEMISTRY | `chem-fx-dian-fen-he-xian-wei-su-yin-tong-shi-z-f62x02` | R | 同分异构现象和同分异构体 | high |
| normal | CHEMISTRY | `chem-fx-dian-rong-zhi-wei-f51x05` | R | 溶液中微粒浓度大小的比较 | high |
| normal | CHEMISTRY | `chem-fx-rong-ye-he-xi-yan-suan-jia-f76x07` | R | 离子共存 | high |
| normal | CHEMISTRY | `chem-fx-wei-guan-you-jiu-hua-xue-jian-duan-lie-f07x04` | R | 化学反应的本质 | high |
| normal | CHEMISTRY | `chem-fx-yan-dao-qi-zhong-de-shi-zhu-yao-de-da-f51x12` | N | — | high |
| normal | CHEMISTRY | `chem-hj2-liu-danxiangsi-xiangrong-jieshi` | R | 硫及其化合物之间的转化规律 | medium |
| normal | CHEMISTRY | `chem-hj2-mei-dan-dian-fanshi-ranshao` | R | 镁的化学性质 | high |
| normal | CHEMISTRY | `ext-che-5c62da9e03-1332` | R | 一些有机物的命名 | high |
| normal | CHEMISTRY | `ext-che-5c62da9e03-2705` | R | 依据官能团分类 | medium |
| normal | CHEMISTRY | `ext-che-d3a055d224-007` | R | 依据反应条件判断有机反应类型 | high |
| normal | CHEMISTRY | `ext-che-d8cfaa5609-013` | R | 抑制-促进水电离平衡的方法 | high |
| normal | CHEMISTRY | `ext-che-dc424420ff-003` | R | 常见仪器 | high |
| normal | MATH | `ext-mat-abf14fbf11-013` | R | 圆的参数方程 | high |
| normal | MATH | `ext-mat-fe0e9d84cf-008c` | R | 忽视异面直线的夹角与向量的夹角范围不同 | high |
| normal | PHYSICS | `ext-phy-2cea5a0fdb-045` | N | — | medium |
| normal | PHYSICS | `ext-phy-e14aad82a5-423` | R | 电磁波谱与各波段的特性 | high |
| normal | PHYSICS | `ext-phy-e14aad82a5-870` | R | 电功率 | medium |
| normal | PHYSICS | `ext-phy-f978043935-014` | R | 滑块-小球-弹簧模型 | high |
| normal | MATH | `math-fixed-order-divide-method` | R | 保序问题与书目插书法 | high |
| normal | MATH | `math-fx-sanjiaoxing-neijiaohe-guanxi` | R | 解三角形常用二级结论 | medium |
| normal | MATH | `math-hj-s98-parabola-three-lines` | R | 直线与圆锥曲线的位置关系 | high |
| normal | PHYSICS | `phys-chao-zhong-shi-zhong-pan-duan` | R | 超重与失重的判断方法 | high |
| overfull | BIOLOGY | `bio-feeding-method-scope` | R | 激素 | high |
| overfull | BIOLOGY | `bio-fx-acetobacter-characters` | R | 果酒和果醋的制作 | high |
| overfull | BIOLOGY | `bio-kb3-f4-s53-n03` | R | 有丝分裂 | high |
| overfull | BIOLOGY | `bio-kb3-f5-s5-2-n06` | R | atp的合成和利用 | high |
| overfull | BIOLOGY | `bio-mo-cell-091` | R | 核糖体 | high |
| overfull | BIOLOGY | `bio-mo-genetic-engineering-008` | R | DNA的粗提取与鉴定 | high |
| overfull | CHEMISTRY | `chem-fx-you-ji-hua-he-wu-de-ming-ming-jie-gou-f64x10` | N | — | high |
| overfull | CHEMISTRY | `chem-fx-zai-biao-zhun-zhuang-kuang-xia-f01x08` | N | — | high |
| overfull | BIOLOGY | `ext-bio-3a60560995-011` | R | 管家基因与奢侈基因 | high |
| overfull | BIOLOGY | `ext-bio-c1fc984e29-004` | R | 辨析真正光合速率-净光合速率和呼吸速率 | high |
| overfull | CHEMISTRY | `ext-che-366722558b-015b` | R | 功能高分子材料 | high |
| overfull | CHEMISTRY | `ext-che-3713da16bf-132` | R | 晶胞中粒子数目、配位数、坐标与密度的计算 | high |
| overfull | CHEMISTRY | `ext-che-3a8fd8d517-030` | R | 聚合反应 | medium |
| overfull | CHEMISTRY | `ext-che-52e4b9ee73-017` | R | 中心原子杂化类型的判断 | high |
| overfull | CHEMISTRY | `ext-che-789339a0b0-016` | R | 硅酸盐 | high |
| overfull | CHEMISTRY | `ext-che-80dc9e8d0c-006` | R | 晶胞中粒子数目、配位数、坐标与密度的计算 | high |
| overfull | PHYSICS | `ext-phy-0b2ace4bef-016` | R | 电磁波谱与各波段的特性 | medium |
| overfull | PHYSICS | `ext-phy-fffc8cde9c-003c` | R | 电磁波谱与各波段的特性 | medium |
| overfull | PHYSICS | `phys-hj2-yehzhu-fengbi-yaqiang-yaodian` | R | 液柱模型 | high |
| overfull | PHYSICS | `phys-hj3-resistivity-exp-dataprocess` | R | 测量金属丝的电阻率实验 | high |
| overfull | PHYSICS | `phys-pingpao-sudu-fen-liang-pian-xiang-jiao` | R | 平抛运动 | medium |
