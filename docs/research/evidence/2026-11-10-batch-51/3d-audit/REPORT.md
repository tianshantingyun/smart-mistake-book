# D-1 语义绑定审计报告（批次 51）

- audit_id：`B51-D1-2026-11-10`
- 日期：2026-11-10
- 包：`moe-2025-four-subjects-v1` v29（主包 sha256 `aaf81be884933b9105db737feee3eb3d84ff440c53f0e2d01c806aafd3790032`）
- 标注性质：**model-labeled**（单一模型 `deepseek/deepseek-flash`，非人工专家，无独立第二判定会话）
- 门定义：KC 语义绑定精确率，分层抽样 + 单侧 95% Wilson 下界 **≥0.95**；每层护栏 Wilson 单侧 95% 下界 **≥0.90**（预注册 no-FPC 口径）
- 本报告**不修改**门阈值、样本、判定或 pack/KB 文件；只做汇总与复算。判定原件为 `verdicts_raw/batch-01..32.tsv`（32 片 × 50 条）。

## 0. 结论（先看这里）

| 项 | 结果 |
|---|---|
| **主判据** 单侧 95% Wilson 下界（overall ≥0.95） | **0.9291236**（92.9124%）< 0.95 → **FAIL**，差 **−2.0876pp** |
| 分层加权点估计 | 0.9403574（94.0357%），SE 0.0063028，n_eff 1411.8253 |
| 未加权正确率（参考） | 1501/1600 = 0.9381250 |
| **护栏 G2**（每层 Wilson 下界 ≥0.90，预注册 no-FPC） | overfull 0.9290572 **通过**；**normal 0.8979148 未通过（差 −0.2085pp）** |
| 护栏 FPC 口径（**并列披露，不改判定**） | overfull 0.9291886 通过；normal 0.9005926 通过（+0.0593pp） |
| **总判定** | **`FAIL (main)`**；按预注册 no-FPC 口径护栏也 **FAIL** |
| 错误项 | 99 条 = 89 REBIND + 10 NONE（6.1875%） |
| 判定一致性 κ | **NOT RUN**（单模型单轮；见 §7） |

一句话：**主判据与预注册护栏均未过门，本报告如实记 FAIL。** normal 层护栏在 FPC 口径下会翻过 0.90，但预注册口径是 no-FPC，且主判据无论 FPC / no-FPC / deff=1.05 都低于 0.95；即使把 14 条 low-confidence 判定全部按 CORRECT 计，主判据仍为 0.9340784 < 0.95。不得把本报告读作“3D D-1 已通过”。

## 1. 输入、抽样框与完整性

`manifest.json` 与实算复核（本会话对包、框快照、样本文件重算 sha256）：

| 对象 | manifest 登记 | 本会话实算 | 一致 |
|---|---|---|---|
| 主包 `core/data/src/main/resources/knowledge/moe-2025-four-subjects-v1.json` | `aaf81be884933b9105db737feee3eb3d84ff440c53f0e2d01c806aafd3790032` | 同左 | 是 |
| 框快照 `frame/overfull_nodes.snapshot.csv` | `ce7d6823787204640a646854561ea85cbbf4f19c48a0809754a3b0f419c69d9d` | 同左 | 是 |
| `sample.csv` | `77eb996983b53ef9bcfa50385e46129aa506bc9f75a6890a8dba00861c9eb1c8` | 同左 | 是 |
| `sample.jsonl` | `a994e5b674e31d71b8eb157909bc949fcf5ef3058373ab00fa511de6320d30ef` | 同左 | 是 |

- pack scope sha256：`31da98b597a2a32949d376b418b9ecad6b5494e3c068df6c36c8515ebc0bd820`；节点 3,279；材料 = 绑定 64,850。
- 分层框：overfull **N=62,956**（weight 0.9707941403）、normal **N=1,894**（weight 0.0292058597），合计 64,850，权重和 = 1.0000000000。
- 抽样：层内确定性哈希排序 + 哈希起点系统选样；overfull 1,300 + normal 300 = **1,600**，`sample.csv` 1,600 行且 `material_slug` 唯一，层计数 1300/300 与 manifest 一致；固定 n、一次判读、无补样。
- 分片：32 片 × 50 条 = 1,600；`check_raw.py` 逐片校验行集/顺序、verdict 枚举、REBIND 目标同科存在且 ≠ 现绑、`evidence_quote` 逐字子串、confidence 枚举、REBIND/NONE note。**实跑 32 片全部 problems=0**（§8）。
- 可评性：1,600/1,600 均有判定，**无 NOT_EVALUABLE 行、无 BLOCKED 行**。
- 框说明：overfull 成员按指令沿用 **v28 时代冻结快照**（62,944 行 / 2,481 节点），绑定内容为 v29；D-2 已登记 v29 复算差异（2,483 节点 / 62,969 条），本审计不替换冻结框。

## 2. 判定协议与标注性质

- 判定口径：`JUDGE_PROMPT.md`（sha256 `2600904e01dd90d8fcf32d1c7ccafdd480276ecc2fcb63681af7eabc184a7d81`）。与批次 50 的提示词逐行 diff 只有标题/版本注记 2 行不同，**正文规则逐字未改**。
- 判定员：`deepseek/deepseek-flash (ZCode harness)`；`labeler_kind=model`、`human_expert=false`、`blind_to_layer=true`。**无人类专家金标、无独立第二判定会话、κ NOT RUN**。
- 盲层：判定输入不含 `layer`/`pool`/全局序号；32 片按 `sha256(audit_id|'shard'|material_slug)` 确定性混层。
- 引文：`judgments.csv` 的 `quote_is_substring` 1,600/1,600 为 `true`；`check_raw.py` 与 `compile_judgments.py` 双重机械校验通过。
- 置信分布：high 1,275 / medium 311 / low 14；99 条错误中 high 31 / medium 61 / low 7。14 条 low 未做第二轮复判（见 §7）。
- 逐片错误数 0–8（每片 50 条），**无单片异常集中**（最大 batch-15：8 错；batch-22/30：0 错）；99 条错误不是由某一片或某一判定员批次单独造成。

## 3. 复算口径（不改阈值）

主判据（R2 §3.1，预注册唯一口径，含 FPC）：

```text
p_hat = Σ_h w_h · (k_h / n_h)                    w_h = N_h / N
Var   = Σ_h w_h² · p_h(1 − p_h)/n_h · FPC_h      FPC_h = (N_h − n_h)/(N_h − 1)
n_eff = p_hat(1 − p_hat) / Var
L     = Wilson_lower(k = p_hat · n_eff, n = n_eff, z = 1.6448536269514722)
PASS iff L ≥ 0.95
```

护栏 G2（`docs/research/2026-11-09-binding-audit.md:192-196`）：**每个抽样层各自的 Wilson 单侧 95% 下界 ≥0.90**；预注册口径为 **no-FPC**（`wilson_lower(k_h, n_h)`）。
FPC 口径仅作并列披露：把该层样本量替换为 `n_eff_h = n_h / FPC_h` 后算同一 Wilson 下界（与“Wilson 公式中方差项乘 FPC”代数等价），**不参与门判**。

z = 1.6448536269514722（单侧 95%）；阈值 0.95 / 0.90 未改动。

## 4. 结果

### 4.1 分层

| 层 | N | n | 正确 | 错误（REBIND/NONE） | p | 护栏 no-FPC | 护栏 FPC（并列） |
|---|---:|---:|---:|---:|---:|---:|---:|
| overfull | 62,956 | 1,300 | 1,223 | 77（69 / 8） | 0.9407692 | 0.9290572 **通过** | 0.9291886 通过 |
| normal | 1,894 | 300 | 278 | 22（20 / 2） | 0.9266667 | **0.8979148 未通过** | 0.9005926 通过 |
| 合计 | 64,850 | 1,600 | 1,501 | 99（89 / 10） | — | — | — |

### 4.2 主判据

```text
p_hat = 0.940357353260977
SE    = 0.00630281314283585
n_eff = 1411.825327807053
L     = 0.9291236149990565
0.9291236 < 0.95  → FAIL（差 −0.0208764 = −2.0876pp）
```

敏感性（均不改变主判据）：

| 口径 | SE | 单侧 95% 下界 | ≥0.95 |
|---|---:|---:|---|
| 主判据（含 FPC） | 0.0063028 | 0.9291236 | **否** |
| 不含 FPC | 0.0063710 | 0.9289924 | 否 |
| 设计效应 deff=1.05 | 0.0064585 | 0.9288237 | 否 |

### 4.3 护栏

- overfull：no-FPC 0.9290572 通过；FPC 0.9291886 通过。
- normal：no-FPC **0.8979148 未通过**（低于 0.90 共 0.2085pp）；FPC 0.9005926 通过（高于 0.90 共 0.0593pp）。
- **预注册口径是 no-FPC → 护栏总判 `false`。** FPC 通过只作并列披露，不改变门判；且主判据即使按 FPC 也是 0.9291236 < 0.95，结论不依赖护栏口径。
- normal 层差 1 条判定即翻转：279/300 的 no-FPC 下界为 0.9017308。但当前样本事实是 278/300（22 错），按门禁纪律不改阈值、不改判。

### 4.4 稳健性（low-confidence 全放宽）

把 14 条 `confidence=low` 的判定全部按 CORRECT 计（对门最宽松的极端假设）：

- 正确数 1,508/1,600；加权点 0.9449353；主判据下界 **0.9340784 < 0.95 → 仍 FAIL**。
- normal 279/300（no-FPC 0.9017308，护栏翻过）、overfull 1,229/1,300（下界 0.9340630）。
- 即 **FAIL 不由 low-confidence 条目单独造成**。

## 5. 错误清单

99 条 = **89 REBIND + 10 NONE**（占样本 6.1875%）。完整 12 列清单见 `errors.csv`（含现绑 node id/slug/name、目标 node id/slug、逐字引文、note）。

| 维度 | 分布 |
|---|---|
| 按层 | overfull 77（69 R + 8 N）；normal 22（20 R + 2 N） |
| 按学科 | CHEMISTRY 40；BIOLOGY 25；PHYSICS 19；MATH 15 |
| 按层×学科 | overfull：CHEM 26 / BIO 25 / PHY 14 / MATH 12；normal：CHEM 14 / PHY 5 / MATH 3 |
| REBIND 目标 | 88 个不同目标 slug；仅 `实验综合题答题模板` 出现 2 次，其余各 1 次 |
| 置信 | high 31 / medium 61 / low 7 |

10 条 NONE（材料无可归知识，或现绑不成立且同科无既有落点）：

| 层 | 学科 | material_slug | 现绑节点 | 置信 | note（摘要） |
|---|---|---|---|---|---|
| normal | CHEMISTRY | `chem-fx-heng-rong-mi-bi-rong-qi-zhong-an-bu-to-f44x07` | 某温度下-在一恒容密闭容器中进行如下两个反应并达到平衡 | medium | 残缺题干，变量与结论缺失 |
| normal | CHEMISTRY | `ext-che-5c62da9e03-1476` | 羧酸 | medium | 四类物质性质图谱跨多节点，现绑仅总论 |
| overfull | BIOLOGY | `ext-bio-172295ebf0-013` | 细胞中的元素 | medium | 四项前沿研究情境拼合，无可归入单一知识点 |
| overfull | CHEMISTRY | `ext-che-5792a432da-005b` | 电解池 | medium | 四类电化学装置判定，现绑仅覆盖其一 |
| overfull | CHEMISTRY | `ext-che-5c62da9e03-1288` | 烷烃的命名 | medium | 讲烷基/烃基定义，非命名规则；同科无对应节点 |
| overfull | CHEMISTRY | `ext-che-78beaac7a8-015` | 斜向变化体现不同价态… | low | 铁三角+三类变化，现绑仅斜向一类 |
| overfull | CHEMISTRY | `ext-che-e1f44db337-029b` | 配位化合物 | medium | 本包无配位平衡/稳定常数节点 |
| overfull | MATH | `ext-mat-0e56aad79f-053` | 圆锥曲线的综合问题 | medium | 三曲线定义与几何性质，现绑综合问题不含 |
| overfull | MATH | `ext-mat-68ab4340ee-001b` | 全称量词与全称量词命题 | high | 材料讲存在量词，同科无对应节点 |
| overfull | MATH | `ext-mat-9c17f83578-001` | 平行四边形恒等式与中线定理 | medium | 中线四法综述，现绑仅含一法 |

全部 99 条明细见 §10 附录。

## 6. 与原批次 50 的可比性

数据来源：批次 50 `docs/research/evidence/2026-11-09-batch-50/d1/{stats.json,REPORT.md,judgments.csv,errors.csv}`；本批 `stats.json` / `judgments.csv` / `errors.csv`。

| 项 | 批次 50（B50-D1-2026-11-08） | 批次 51（B51-D1-2026-11-10） |
|---|---|---|
| 包 | v28，主包 sha `10ede1bf…`；scope `859ae71e…` | v29，主包 sha `aaf81be8…`；scope `31da98b5…` |
| 框 N（overfull / normal / 合计） | 62,948 / 1,905 / 64,853 | 62,956 / 1,894 / 64,850 |
| 样本 | 1,300 + 300 = 1,600；seed `148a5f12…` | 1,300 + 300 = 1,600；seed `a4ee6c1f…` |
| 两轮样本交集 | — | **68 / 1,600**（overfull 24、normal 42） |
| 判定 C / R / N | 1,557 / 39 / 4 | 1,501 / 89 / 10 |
| 加权点估计 | 0.9821666 | 0.9403574 |
| 主判据下界（FPC） | 0.9756913 → **PASS** | 0.9291236 → **FAIL** |
| overfull p / 护栏 no-FPC | 0.9838462 / 0.9770088 通过 | 0.9407692 / 0.9290572 通过 |
| normal p / 护栏 no-FPC | 0.9266667 / 0.8979148 **未通过** | 0.9266667 / 0.8979148 **未通过** |
| normal 护栏 FPC（并列） | 0.9005766（N=1,905） | 0.9005926（N=1,894） |
| 错误条数 | 43（39 R + 4 N） | 99（89 R + 10 N） |
| 两轮错误 slug 交集 | — | **0 条** |
| 置信分布 high/medium/low | 1,538 / 62 / 0 | 1,275 / 311 / 14 |
| 提示词 sha | `56158a5e…` | `2600904e…`（diff 仅标题/版本注记 2 行，规则逐字未改） |
| 总判定 | `PASS main / FAIL guardrail` | **`FAIL (main)`**；no-FPC 护栏亦 FAIL |

**可比性结论（必读）**：

1. **不是配对前后比较。** 本批是新 audit id → 新 seed → 新样本；两轮仅 68/1,600 条材料相同（4.25%），且包从 v28 换到 v29（40 条改绑、3 条撤料、1 个节点删除）。两轮读数之差同时混合了“样本组成变化 + 包变化 + 判定波动”，**不能归因于 43 条改绑的修复效果**。
2. **批次 50 的 43 条错误材料中有 4 条进入本批样本，且本批均判 CORRECT**（交集清单与逐条 verdict 见 `../fix-loop/b50-b51-overlap.json` / `.csv`，本会话只读复算 4/4 CORRECT）；其余 36 条改绑 + 3 条撤料未被本批直接测量。**两轮错误 slug 交集才是 0**。注意：这 4 条仍由同一 `deepseek-flash` 判定，属同模型复测，不是独立第二方验证；因此本批仍不足以单独证明 43 条改绑/撤料的修复效果，定向复核仍需独立判定。
3. **normal 层 278/300 完全相同是数值巧合，不是同一批条目**：两轮 normal 样本仅 42/300 重叠；相同的是 k/n，因此 no-FPC 下界也恰好同为 0.8979148。
4. **层框口径不同步**：本批 overfull 成员按指令沿用 v28 时代冻结快照，绑定内容是 v29；批次 50 后期框也已是同一快照（`ce7d6823…`）。层权重的解释应带这一前提。
5. **判定波动**：同一模型两轮，但本批 medium/low 显著增多（311/14 vs 62/0），说明判定不确定度或材料难度分布不同；两轮都没有 κ 与人工复核，不能把差异全部当作真实质量变化。
6. 计划 G1 的目标是“改绑/剔除 43 条 → 新抽样框、新 audit id 复 audit → 两判据同时 PASS”。**实际两判据均未 PASS（主判据 FAIL；预注册护栏 FAIL）**；本报告不写 PASS。

## 7. 未验证项与局限（必须随结论一起引用）

1. **κ 双重判定：NOT RUN。** R2 §4.5 要求每层 10% 盲判并报 Cohen's κ，且预注册 `κ<0.60 → 该轮判定不得用于门`。本批只有单模型单轮判定，**没有 κ**；因此主判据应读作 **model-labeled 暂定值**，按预注册**本轮即使读数达标也不得签门级 PASS**。未运行原因：没有独立第二判定会话；由已见结论的同一会话补做不构成盲判，宁可留空。**该局限只会削弱“PASS”的可信度，不改变本轮的 FAIL 结论。**
2. **无人类专家复核**（`stats.json.metadata.human_expert=false`）。
3. **Wilson 区间只覆盖抽样误差**，不含判定噪声（R2 `:285` 第 4 条）。
4. **14 条 low-confidence 未复判**；按提示词应在复判环节单独处理。§4.4 给出“全部按 CORRECT”的宽松极端，主判据仍 FAIL。
5. **非 SRS**：分层 + 系统抽样 + FPC；推断只覆盖当前 pack 快照，包内容变化后需新 seed 重抽。
6. **判定输入截断**：材料正文截到 500 字符、节点 boundary 截到 120 字符（`sample_d1.py:280`、`:283`）；超长材料尾部信息未进入判定。
7. **护栏口径敏感**：normal no-FPC 0.8979148（未过）/ FPC 0.9005926（过）。预注册口径是 no-FPC，故本报告判红；同时不得据此断言“normal 层真实精确率 <0.90”，只能说“本轮证据不足以证明 ≥0.90”。
8. **可评性完整**：1,600/1,600 有判定，无 NOT_EVALUABLE / BLOCKED；本门无外部阻塞项。
9. 本报告**不涉及** D-2 结构审计、D-3 召回、3E、MVA、AUD-08；那些门由各自子线报告。
10. **B50 错误材料的部分复测不是独立验证**：4/43 条进入本批样本且判 CORRECT，但判定模型与 B50 改绑判定同为 `deepseek-flash`；两轮错误 slug 交集 0 只说明这 4 条本轮未再判错，不构成对 43 条处置的独立验证（`../fix-loop/b50-b51-overlap.json`）。

## 8. 复算命令（本轮实跑）

工作目录：`docs/research/evidence/2026-11-10-batch-51/3d-audit/`

```bash
# 1) 32 片判定原件机械校验（逐片）
for f in verdicts_raw/batch-*.tsv; do python -I -X utf8 check_raw.py "$f"; done \
 | python -I -X utf8 -c "import sys,json;rs=[json.loads(l) for l in sys.stdin if l.startswith('{')];print('batches',len(rs),'rows',sum(r['rows'] for r in rs),'problems',sum(r['problems'] for r in rs))"
# 实数输出：batches 32 rows 1600 problems 0

# 2) 合并 32 片 + 主判据/护栏 + errors.csv
python -I -X utf8 compile_judgments.py
# EXIT=0；写 judgments.csv / errors.csv / stats.json
# stats.json: verdict="FAIL (main)", estimate.pass=false, per_layer_guardrail_pass=false

# 3) 独立复算（不 import 上一步代码；只读 manifest + judgments.csv）
python -I -X utf8 recompute.py
# EXIT=0；stats_recomputed.json: all_match=true

# 4) 并列读数（未加权 / FPC 护栏 / no-FPC / deff=1.05）
python -I -X utf8 recompute_detail.py
# EXIT=0；stats_detail.json: all_match=true
```

输入 sha 复核（本会话实跑，输出与 §1 表一致）：

```bash
python -I -X utf8 - <<'PY'
import hashlib, json
from pathlib import Path
def sha(p):
    h = hashlib.sha256()
    with open(p, 'rb') as f:
        for c in iter(lambda: f.read(1 << 20), b''):
            h.update(c)
    return h.hexdigest()
m = json.loads(Path('manifest.json').read_text(encoding='utf-8'))
print(sha('core/data/src/main/resources/knowledge/moe-2025-four-subjects-v1.json') == m['pack']['scope_files'][0]['sha256'])
print(sha('frame/overfull_nodes.snapshot.csv') == m['frame']['layers']['overfull']['list']['sha256'])
print(sha('sample.csv') == m['sample']['sha256']['sample.csv'])
print(sha('sample.jsonl') == m['sample']['sha256']['sample.jsonl'])
PY
# True / True / True / True
```

`recompute.py` 只读 `manifest.json` + `judgments.csv`，重算点估计、SE、n_eff、主判据下界、两层正确数并与 `stats.json` 逐项比对（容差 1e-12 / n_eff 1e-9）；`recompute_detail.py` 额外输出未加权正确率、每层 no-FPC 与 FPC 护栏、no-FPC 与 deff=1.05 敏感性，并与 `stats.json` 交叉核对。本轮两者均 `all_match: true`。

## 9. 产物

| 文件 | 作用 | sha256 |
|---|---|---|
| `judgments.csv` | 1,600 条判定 + 层/学科/引文校验/模型元数据 | `58452cd76b2dd2644ef4bac39e682c6fc0f1d1f05e136c99a8e19df002fd7961` |
| `errors.csv` | 99 条改绑/剔除清单（12 列，含目标 node id） | `77155bd27fe0d965bada49e506ed9355aadad5674723a0b98475f5d7356ca8de` |
| `stats.json` | 门值、分层、敏感性、判定字符串 | `089dad84ac7e3e602166973bcbd7c4ba159128b88dc6b49fad2bd3bd1f92ce11` |
| `stats_recomputed.json` | 独立复算结果与比对 | `35467709adbe0b85bf49c22596773a7bead7cf1700a281a54012ca6f6900d167` |
| `stats_detail.json` | 未加权/FPC 护栏/no-FPC/deff 并列读数 | `430df7b974b275ab907764d82fa0f3efdcc6017ffb53fb03a3df74e227438676` |
| `recompute_detail.py` | 并列读数复算器（只读，不 import compile/recompute） | `060cbb6b38241807d6c90ac36190279ccf3759ea8920343723edd73b93778637` |
| `REPORT.md` | 本报告 | （自引用不列） |

输入与既有工具（本批未改）：`manifest.json`、`sample.csv`、`sample.jsonl`、`sample.sha256`、`node_index.tsv`、`JUDGE_PROMPT.md`（`2600904e…`）、`verdicts_raw/batch-01..32.tsv`、`check_raw.py`、`compile_judgments.py`、`recompute.py`、`sample_d1.py`、`render_compact.py`、`judge_input/`、`shards/`、`frame/`。

## 10. 附录：99 条错误明细

`R` = REBIND，`N` = NONE；目标列给的是目标节点 slug（同科既有节点，已由 `check_raw.py` 机械校验存在且 ≠ 现绑）。逐字引文与 note 见 `errors.csv`。

### 10.1 normal 层（22 条：20 R + 2 N）

| 层 | 学科 | material_slug | 判定 | 目标节点 slug | 置信 |
|---|---|---|---|---|---|
| normal | CHEMISTRY | `chem-fx-ci-lei-tu-xiang-yi-ban-zong-zuo-biao-b-f47x05` | R | 含量-或转化率-时间-温度-压强-图像 | medium |
| normal | CHEMISTRY | `chem-fx-heng-rong-mi-bi-rong-qi-zhong-an-bu-to-f44x07` | N | — | medium |
| normal | CHEMISTRY | `chem-fx-qi-ti-de-chu-za-fang-fa-f28x04` | R | 写出除去下列气体中混有的杂质-括号内为杂质-可采取的方法 | high |
| normal | CHEMISTRY | `chem-fx-rong-yu-xi-f27x04` | R | 硝酸 | high |
| normal | CHEMISTRY | `chem-fx-ruo-tong-ru-shui-zhong-f25x04` | R | 氮氧化物与氧气混合溶于水的计算 | high |
| normal | CHEMISTRY | `chem-hj-rongjiedu-gainian-yinsu` | R | 固体和气体的溶解度 | high |
| normal | CHEMISTRY | `chem-hj2-fangchengshi-jisuan-shaoliang-guoliang` | R | 物质的量在化学方程式计算中的应用 | medium |
| normal | CHEMISTRY | `chem-hj2-jishi-fuhao-guifan-jisuan` | R | 根据化学方程式计算的步骤 | low |
| normal | CHEMISTRY | `chem-thiosulfate-acid-decomposition` | R | 臭氧与硫代硫酸盐的性质 | medium |
| normal | CHEMISTRY | `ext-che-00be305a22-005c` | R | 常规图像解题分析 | medium |
| normal | CHEMISTRY | `ext-che-5c62da9e03-1476` | N | — | medium |
| normal | CHEMISTRY | `ext-che-5c62da9e03-1757` | R | 研究物质性质的基本程序 | high |
| normal | CHEMISTRY | `ext-che-5c62da9e03-2334` | R | 硫及其化合物之间的转化规律 | high |
| normal | CHEMISTRY | `ext-che-a988fb0714-005` | R | 氮族元素及其化合物 | medium |
| normal | MATH | `ext-mat-b1aceeb4bf-005b` | R | 求函数解析式的常用方法 | medium |
| normal | MATH | `math-fx-yuxian-pan-xingzhuang` | R | 三角形形状的判断 | high |
| normal | MATH | `math-hj-s11-subset-count` | R | 子集的个数问题 | high |
| normal | PHYSICS | `ext-phy-14007d0207-014` | R | 关联速度的分析-运动分解的核心思想 | high |
| normal | PHYSICS | `ext-phy-18cd4d7b47-042` | R | 电磁感应中的电路问题 | medium |
| normal | PHYSICS | `ext-phy-3edb60db02-004` | R | 单位制 | medium |
| normal | PHYSICS | `ext-phy-5788d44c37-002` | R | 连接体的运动特点 | medium |
| normal | PHYSICS | `phys-mo-l17-kepler-gravitation-b-4` | R | 牛顿力学的局限性 | medium |

### 10.2 overfull 层（77 条：69 R + 8 N）

| 层 | 学科 | material_slug | 判定 | 目标节点 slug | 置信 |
|---|---|---|---|---|---|
| overfull | BIOLOGY | `bio-adrenal-cortex-glucocorticoid` | R | 血糖平衡的调节 | medium |
| overfull | BIOLOGY | `bio-consanguinity-multiplier` | R | 遗传病的检测和预防 | medium |
| overfull | BIOLOGY | `bio-fj-02-c-h-o-n-most-4` | R | 组成细胞的元素 | medium |
| overfull | BIOLOGY | `bio-fj-04-amino-acid-transport` | R | 主动运输 | low |
| overfull | BIOLOGY | `bio-fj-12-surface-volume-ratio` | R | 细胞不能无限长大的原因 | high |
| overfull | BIOLOGY | `bio-fj-20-accurate-replication-reason` | R | dna的复制 | medium |
| overfull | BIOLOGY | `bio-fj-24-internal-environment-function` | R | 细胞生活的环境 | medium |
| overfull | BIOLOGY | `bio-fx-judge-metabolism-site` | R | 内环境 | high |
| overfull | BIOLOGY | `bio-mo-immunity-013` | R | 体液免疫 | medium |
| overfull | BIOLOGY | `bio-mo-nerve-042` | R | 神经系统的分级调节 | medium |
| overfull | BIOLOGY | `ext-bio-158acc839a-011` | R | 渗透压 | medium |
| overfull | BIOLOGY | `ext-bio-172295ebf0-013` | N | — | medium |
| overfull | BIOLOGY | `ext-bio-1e23a952bc-039` | R | 配子来源的判断 | medium |
| overfull | BIOLOGY | `ext-bio-3d00ff9c08-001` | R | 种间关系 | high |
| overfull | BIOLOGY | `ext-bio-4e8dccf192-013` | R | 生物体内水的产生与消耗 | low |
| overfull | BIOLOGY | `ext-bio-5f510cdc4a-641` | R | 减数分裂细胞中染色体-dna数量变化规律 | medium |
| overfull | BIOLOGY | `ext-bio-5f510cdc4a-840` | R | 细胞分裂中的同位素标记分析 | medium |
| overfull | BIOLOGY | `ext-bio-61401994ab-003` | R | 孟德尔规律的应用 | medium |
| overfull | BIOLOGY | `ext-bio-64a3002892-134` | R | 目的基因插入方向的鉴定（PCR与酶切） | high |
| overfull | BIOLOGY | `ext-bio-71f37f4190-002` | R | 蛋白质的盐析变性与水解的辨析 | medium |
| overfull | BIOLOGY | `ext-bio-a77993e4a4-013` | R | 影响光合作用强度的环境因素 | medium |
| overfull | BIOLOGY | `ext-bio-c02c7a633d-017` | R | 多倍体 | medium |
| overfull | BIOLOGY | `ext-bio-d9f5ec82ec-007` | R | 核苷酸 | high |
| overfull | BIOLOGY | `ext-bio-ec9b2217ea-011` | R | 适应的普遍性与相对性 | medium |
| overfull | BIOLOGY | `ext-bio-f7bf8a79a5-040` | R | 光反应 | medium |
| overfull | CHEMISTRY | `chem-fx-chang-yong-de-kong-zhi-fan-ying-tiao-j-f56x03` | R | 调节pH分离提纯金属离子 | medium |
| overfull | CHEMISTRY | `chem-fx-nan-rong-xing-de-jian-yi-fen-jie-sheng-f16x02` | R | 横向变化体现了同价态不同的类别-氧化物-碱-盐-物质之间的转化 | medium |
| overfull | CHEMISTRY | `chem-kb-subgroup-zero-valence-config` | R | 原子结构与元素周期表的关系 | high |
| overfull | CHEMISTRY | `chem-oxidant-fe2-to-fe3-purpose` | R | 有关铁及其化合物的除杂 | high |
| overfull | CHEMISTRY | `ext-che-067cb2c820-011` | R | 简单有机物同分异构体的书写 | medium |
| overfull | CHEMISTRY | `ext-che-1422ee20c2-007b` | R | 实验综合题答题模板 | high |
| overfull | CHEMISTRY | `ext-che-1422ee20c2-009b` | R | 胶体 | medium |
| overfull | CHEMISTRY | `ext-che-1a9ac8e309-013` | R | 溶度积 | high |
| overfull | CHEMISTRY | `ext-che-1b66b297bd-002b` | R | 结晶方法的选择与操作 | high |
| overfull | CHEMISTRY | `ext-che-4112b48508-001b` | R | 晶胞组成的计算-均摊法 | low |
| overfull | CHEMISTRY | `ext-che-5792a432da-005b` | N | — | medium |
| overfull | CHEMISTRY | `ext-che-5892d1ea1a-013` | R | 多重平衡中的产物调控与选择性 | high |
| overfull | CHEMISTRY | `ext-che-594d9258ef-029` | R | 电荷守恒在离子推断中的应用 | medium |
| overfull | CHEMISTRY | `ext-che-5c62da9e03-1190` | R | 芳香烃 | high |
| overfull | CHEMISTRY | `ext-che-5c62da9e03-1288` | N | — | medium |
| overfull | CHEMISTRY | `ext-che-5c62da9e03-2375` | R | 实验综合题答题模板 | medium |
| overfull | CHEMISTRY | `ext-che-5c62da9e03-2706` | R | 单糖 | medium |
| overfull | CHEMISTRY | `ext-che-5c8ab8324c-004` | R | 判断原电池正-负极的方法 | high |
| overfull | CHEMISTRY | `ext-che-735d3d72c5-003` | R | 元素周期律的内容及实质 | medium |
| overfull | CHEMISTRY | `ext-che-78beaac7a8-015` | N | — | low |
| overfull | CHEMISTRY | `ext-che-8cc878ccbe-018` | R | 溶液中粒子等量关系-三大守恒 | medium |
| overfull | CHEMISTRY | `ext-che-923cf868a0-009` | R | 锂电池与锂离子电池 | medium |
| overfull | CHEMISTRY | `ext-che-b0471d5c46-006` | R | 氧化性-还原性强弱的比较 | high |
| overfull | CHEMISTRY | `ext-che-db3cec307b-001b` | R | 物质制备流程与条件控制 | medium |
| overfull | CHEMISTRY | `ext-che-e1f44db337-029b` | N | — | medium |
| overfull | CHEMISTRY | `ext-che-ea3ea77a38-096` | R | 电子转移 | high |
| overfull | MATH | `ext-mat-0e56aad79f-053` | N | — | medium |
| overfull | MATH | `ext-mat-347cb02bf5-002c` | R | 复数的四则运算 | medium |
| overfull | MATH | `ext-mat-4308c78446-026` | R | 常见抽象函数模型与特征式 | high |
| overfull | MATH | `ext-mat-463094f8d7-002` | R | 已知函数零点求参数的方法 | high |
| overfull | MATH | `ext-mat-63db0a8b06-030` | R | 与距离有关的最值问题 | medium |
| overfull | MATH | `ext-mat-68ab4340ee-001b` | N | — | high |
| overfull | MATH | `ext-mat-9c17f83578-001` | N | — | medium |
| overfull | MATH | `ext-mat-f720be0b99-001b` | R | 线面角的定义与求法 | high |
| overfull | MATH | `math-fx-zuobiao-fa-shuliangji` | R | 平面向量的数量积 | high |
| overfull | MATH | `math-hj-s12-judge-set` | R | 充分条件与必要条件的判断 | high |
| overfull | MATH | `math-hj-s75-plane-plane-vertical` | R | 面面垂直性质的应用 | low |
| overfull | MATH | `math-hj-tuoyuan-jihe-xingzhi` | R | 椭圆 | medium |
| overfull | PHYSICS | `ext-phy-1dbc2880eb-031c` | R | 求解非点电荷电场强度的四种方法 | medium |
| overfull | PHYSICS | `ext-phy-3f87e2a35b-009` | R | 求解电能的思路-x1 | medium |
| overfull | PHYSICS | `ext-phy-504a241474-012` | R | 测电阻方法体系与选择 | low |
| overfull | PHYSICS | `ext-phy-895e882223-029` | R | 动量守恒定律 | medium |
| overfull | PHYSICS | `ext-phy-bf0fd75088-014` | R | 能量图像的斜率意义 | high |
| overfull | PHYSICS | `ext-phy-cebfcf3898-016` | R | 卫星中的超重与失重现象 | medium |
| overfull | PHYSICS | `ext-phy-e14aad82a5-083` | R | 力的分解 | medium |
| overfull | PHYSICS | `ext-phy-fcb7cd26a9-006b` | R | 等分法 | medium |
| overfull | PHYSICS | `phys-capacitor-rod-charge-not-zero` | R | 电磁感应中的电容器问题 | medium |
| overfull | PHYSICS | `phys-fx-block-plank-solve-key` | R | 板块模型 | medium |
| overfull | PHYSICS | `phys-fx-paper-tape-data-processing` | R | 纸带在力学实验中的应用分析 | medium |
| overfull | PHYSICS | `phys-hj2-likuang-dianchang-san-da-guandian-chuli` | R | 动力学三大观点的基本理解和应用 | medium |
| overfull | PHYSICS | `phys-hj3-belt-model-scenarios` | R | 传送带模型 | medium |
| overfull | PHYSICS | `phys-hj3-transformer-special-cases` | R | 变压器 | medium |
