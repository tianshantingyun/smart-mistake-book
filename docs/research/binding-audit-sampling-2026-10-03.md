# 绑定审计分层抽样报告（全库语义错误率估计）· 2026-10-03

> 数字只用样本与判定的实测值；样本量、分层与判据如实标注。本报告全部计数与估计为本次会话对产物文件的实点/实算。
> 产物：`build/agent-batch4/audit-sample.csv`（样本 240 条）、`build/agent-batch4/kit/kit-01..08.json`（判定工具包 8×30）、`build/agent-batch4/verdicts/kit-01..08.verdicts.csv`（判定 8×30）、`build/agent-batch4/audit-confirm.csv`（重结论复核 37 条）。
> 抽样与判定设计：`.zcode/workflow-drafts/批次-4-第一段管道回绿--两类抽样体检.dwf.ts` 段 3（行 178–246；该目录被 gitignore，不入库）。

## 0. 结论摘要

- 全库 **3,761 节点 / 50,383 材料**（每材料恰 1 条绑定、0 悬空；实点）。抽样框 = 超配节点清单 `build/kb-staging/overfull_nodes.csv`（节点内 **>4 条材料** 的节点）：**2,473 节点 / 47,659 条绑定**；分层抽 **240 条**（4 层 × 60）。
- 判定：**KEEP 203 / REBIND 36 / NONE 1**（8 件 × 30）。复核：重结论 **37 条 → AGREE 36 / DISAGREE 1**（推翻逐条：`ext-bio-6c193f35a8-003d`；复核未把任何一条改判为「现绑正确」，仅订正 1 条去向）。
- **全库（框内）绑定语义错误率 ≈ 10.6%，95% CI ≈ [5.1%, 16.2%]**；框内错误绑定预计 **≈ 5,075 条**（95% CI ≈ 2,424–7,726）；外推全库 ≈ 5,365 条。
- 规模建议：若全量逐条审计，需判 **≈ 4.8 万～5.0 万条**绑定 → **≈ 1,600–1,700 个判定会话**（30 条/会话）＋ **≈ 140–165 个复核会话** → 合计 **≈ 1,700–1,830 个代理会话**量级（详见 §7）。

## 1. 样本与分层

- **全库口径**（实点，`PYTHONPATH=tools python -c "from kb_build import overfull_nodes; ..."`）：知识点 **3,761**、有绑定的节点 3,761、材料 **50,383**（绑定 50,383 条，去重后材料数同为 50,383 → 每材料恰 1 条绑定，多绑 0）。与 `docs/kb-batch3-merge-rebind-2026-10-03.md:4,182` 终态（3761 / 50383）一致。
- **抽样框**：`build/kb-staging/overfull_nodes.csv` —— 「节点内 >4 条材料」的超配节点清单（`tools/kb_build/overfull_nodes.py:2-12,44`；是**分诊名单**，不是「死绑定」清单）。实点：**47,659 条记录 / 2,473 个节点 / 47,659 个唯一 material_slug**。文件 `wc -l` 为 47,669 行（多出的 10 行来自字段内换行），按 csv 解析为 47,659 条。
  - **框外** = 50,383 − 47,659 = **2,724 条**（5.4%，挂在 ≤4 条材料的节点上）不在抽样框内；本报告估计只覆盖框内（§6 局限 4）。
- **表列**（13 列）：`node_id, subject, chapter, node_name, material_count, verdict_keep_or_move, rank, role, type, title, material_slug, chars, weak_match`。
  - `weak_match=yes` ⇔ 材料标题与节点名**零 2-gram 重合**（`overfull_nodes.py:133`；生成器只写 `yes` 或留空，空即 no）。它是机械「疑似错绑」分诊标记，工具自述零重合里约 54% 本来就是对的（`overfull_nodes.py:54,131-132`）。
  - `rank` = 节点内排序位（角色秩 → 重教类型优先级 → title → material_id，`overfull_nodes.py:112-114,176`）；1–4 位为「前 4 条」，>4 为「第 5 位及以后」。
- **分层与抽样**：按 `weak_match × rank` 分 4 层，每层抽 60 条，层内按源表行序**等距抽（确定性，非随机）**：`idx_i = floor((i+0.5)·N_h/60)`，`i = 0..59`（`N_h` = 该层在源表条数）。

| 层（slice） | 源表 `N_h` | 占框 | 样本 `n_h` | 层内含 |
|---|---:|---:|---:|---|
| `sample-weak_match-yes_rank_le4` | 1,188 | 2.49% | 60 | 机械疑似错绑 × 节点内前 4 条 |
| `sample-weak_match-yes_rank_gt4` | 4,311 | 9.05% | 60 | 机械疑似错绑 × 第 5 位及以后 |
| `sample-weak_match-no_rank_le4` | 8,704 | 18.26% | 60 | 标题有 2-gram 重合 × 前 4 条 |
| `sample-weak_match-no_rank_gt4` | 33,456 | 70.20% | 60 | 标题有 2-gram 重合 × 第 5 位及以后 |
| **合计** | **47,659** | 100% | **240** | 等额分配（非按比例） |

- **实核**：4 层样本与上述公式逐条相等（复算见 §8 ②）；样本 240 行与源表逐字段一致（0 处不符）。
- 样本落 `build/agent-batch4/audit-sample.csv`（8 列表头与候选表同构：`subject,slug,material_slug,current_node_slug,suggested_node_slug,verdict,evidence,slice`）；`slug` 与 `current_node_slug` 均填节点短名（= `node_id` 末段），`evidence` 填 `weak_match=yes|no｜rank=N`，`verdict`/`suggested_node_slug` 留空；每层恰 60 行（实点）。

## 2. 判据与判定

- **判定工具包** `kit/kit-01..08.json`（8 件 × 30 条；实核每件行集 = 样本第 `30(i−1)`…`30i−1` 行，idx 连续）。每件一条一个对象：材料正文（summary/content）、现绑节点（name/boundary/aliases）、`audit_note`（weak_match｜rank）。本轮 `target` 字段逐条等于 `current`（8 件 240 条全部相同）——即判定问题是「**现绑是否正确**」，REBIND 去向由判定员从节点表自选（工作流行 204–209）。
- **判定规则**（工作流行 204–209，与第二轮同一口径）：
  - `KEEP` = 材料正文讲的就是现绑节点那个知识点（标题措辞不同不算错）；
  - `REBIND` = 正文明显属于另一个**已存在**的节点（`suggested_node_slug` 必须是节点表里能看到的 slug）；
  - `NONE` = 不含可归知识点的内容；`evidence` ≤160 字且**引用材料正文原句**；拿不准就 KEEP 并写保留理由。
- 8 名判定员各判 1 件（30 条）互不共享理由；判定件 8 列与 kit 同构。实核：每件行集与对应 kit 完全一致；REBIND 行 `suggested_node_slug` 均已填、KEEP/NONE 行留空。

## 3. 结果

### 3.1 判定回报（8 件，与判定件实点一致）

| 判定件 | 覆盖样本 | 行数 | KEEP | REBIND | NONE |
|---|---|---:|---:|---:|---:|
| `kit-01.verdicts.csv` | 第 1–30 条 | 30 | 20 | 10 | 0 |
| `kit-02.verdicts.csv` | 第 31–60 条 | 30 | 22 | 8 | 0 |
| `kit-03.verdicts.csv` | 第 61–90 条 | 30 | 29 | 1 | 0 |
| `kit-04.verdicts.csv` | 第 91–120 条 | 30 | 24 | 6 | 0 |
| `kit-05.verdicts.csv` | 第 121–150 条 | 30 | 27 | 2 | 1 |
| `kit-06.verdicts.csv` | 第 151–180 条 | 30 | 27 | 3 | 0 |
| `kit-07.verdicts.csv` | 第 181–210 条 | 30 | 26 | 4 | 0 |
| `kit-08.verdicts.csv` | 第 211–240 条 | 30 | 28 | 2 | 0 |
| **合计** | — | **240** | **203** | **36** | **1** |

### 3.2 分层汇总（判定件按样本行序每 30 条一件，故两件合成一层，加法实核一致）

| 层 | 判定 `n_h` | KEEP | REBIND | NONE | 重结论 | 层错误率 `k_h/60` |
|---|---:|---:|---:|---:|---:|---:|
| yes × rank≤4 | 60 | 42 | 18 | 0 | 18 | 30.0% |
| yes × rank>4 | 60 | 53 | 7 | 0 | 7 | 11.7% |
| no × rank≤4 | 60 | 54 | 5 | 1 | 6 | 10.0% |
| no × rank>4 | 60 | 54 | 6 | 0 | 6 | 10.0% |
| **合计** | **240** | **203** | **36** | **1** | **37** | 15.4%（未加权） |

### 3.3 复核（37 条重结论全覆盖）

- 复核员未参与判定、**不看** `verdicts/` 里别人的理由，读材料正文与两侧节点独立复判（工作流行 226–233）。实核：复核 37 行与判定件的重结论（REBIND/NONE）集合**完全一致**（无漏无多）。
- **结论：AGREE 36 / DISAGREE 1**。推翻逐条：**`ext-bio-6c193f35a8-003d`**。
  - 该条详情（`audit-confirm.csv` 第 5 行）：BIOLOGY，现绑『三率-的关系及测定』，rank=4（`sample-weak_match-yes_rank_le4` 层，kit-01）；原判 REBIND → 『影响光合作用强度的环境因素』。复核：**改绑方向成立（应改出『三率-的关系及测定』），但去向应为『光合作用与细胞呼吸"关键点"的移动』**——同口诀同示例的近同条已在该节点，宜随近重复归并。
- **复核后的数**：36 条完全维持；该 1 条复核**没有改判「现绑正确」**（方向成立），只订正去向 → **错误计数仍按 37 条**（其中 1 条去向按复核订正）。此读法与第二轮先例一致：复核 DISAGREE 分两种，一种「现绑其实正确」（订正为 KEEP、退出错误计数，见第二轮切片内 1 条），一种「去向不对」（仍计重结论、只订正去向，见第二轮切片外 1 条）。
- 口径敏感性：若按「DISAGREE 即不计错误」的从严口径，错误数 36 条 → 点估计 10.61%（Δ ≈ −0.04pp，见 §4 附注）。两个口径都不改变本报告任何结论。

## 4. 全库错误率估计（按层加权，正态近似）

**错误口径**：`REBIND`（正文属于另一已存在节点）＋ `NONE`（不含可归知识点、不应绑任何知识点）都计为本条绑定的语义错误；`KEEP` 不计。

符号：层 `h` 的全量条数 `N_h`、样本判定条数 `n_h`（=60）、复核后错误数 `k_h`；`N = ΣN_h = 47,659`（抽样框）。

- 点估计（框内全量错误率）：`p̂ = Σ_h (N_h / N) · (k_h / n_h)`
- 方差（各层独立、层内二项近似，忽略有限总体校正）：`Var(p̂) = Σ_h (N_h / N)² · p̂_h(1 − p̂_h) / n_h`，`SE = √Var`
- 95% 置信区间：`p̂ ± 1.96 · SE`（正态近似）
- 错误条数：`K̂ = N · p̂`，区间 `N · (p̂ ± 1.96 · SE)`

分层明细：

| 层 | `N_h` | `n_h` | `k_h` | `p̂_h = k_h/60` | 权重 `N_h/N` | 贡献 `(N_h/N)·p̂_h` | `K̂_h` |
|---|---:|---:|---:|---:|---:|---:|---:|
| yes × rank≤4 | 1,188 | 60 | 18 | 30.00% | 0.0249 | 0.00748 | ≈ 356 |
| yes × rank>4 | 4,311 | 60 | 7 | 11.67% | 0.0905 | 0.01055 | ≈ 503 |
| no × rank≤4 | 8,704 | 60 | 6 | 10.00% | 0.1826 | 0.01826 | ≈ 870 |
| no × rank>4 | 33,456 | 60 | 6 | 10.00% | 0.7020 | 0.07020 | ≈ 3,346 |
| **全量** | **47,659** | **240** | **37** | — | — | **p̂ = 0.10649 → 10.65%** | **≈ 5,075** |

结果：

- **错误率 ≈ 10.65%，95% CI ≈ [5.09%, 16.21%]**（`SE = 0.02838`）。
- **框内错误绑定预计 ≈ 5,075 条，95% CI ≈ [2,424, 7,726]**。
- 外推全库（把框外 2,724 条按同率估算，**未测、假设**）：≈ 5,365 条，95% CI ≈ [2,563, 8,168]。
- 敏感性：① 逐层加有限总体校正后 `SE = 0.02835`，区间变化 <0.1pp，可忽略；② 口径敏感性（逐条实算）：k=36（DISAGREE 不计）→ 10.61% [5.05%, 16.17%]；k=36（只算 REBIND、NONE 不计）→ 10.34% [4.81%, 15.88%]；k=35（两者都剔）→ 10.30% [4.77%, 15.84%]。点估计区间 10.3%–10.6%，结论量级不变。
- 描述性观察（只作线索，不作推断）：错误率最高的是「机械疑似错绑 × 前 4 条」层（30.0%）；「第 5 位及以后」并不比「前 4 条」更差（11.7% / 10.0% vs 30.0% / 10.0%）；机械弱匹配两层合计（5,499 条，加权 ≈15.6%）高于非弱匹配两层（10.0%），但差异主要由 yes 层错误集中在前 4 条驱动（样本 60/层）。

## 5. 与第二轮的关系（口径不同，不可混比）

- **第二轮**（2026-10-02，`docs/kb-bind-audit-round2-2026-10-02.md`）：**整章靶区审计**。对象 = `audit_content_bindings` 机械预筛出的 **496 条「可疑绑定候选」**（C1–C4 判据：标题/首句主语零词面交集、靶区 IDF 强匹配、登记锚点复核等；靶区节点 161）——候选是被挑出来的可疑行，**不是随机样本**。判定 KEEP 383 / REBIND 111 / NONE 2（重结论 22.4%）；复核：切片内 15 条 → 14 AGREE / 1 DISAGREE（订正为 KEEP），切片外 99 条 → 97 AGREE / 1 DISAGREE（订正去向）/ 1 UNCLEAR（doc:66-71）。
- **本轮**：**全库分层抽样**（超配框 47,659 条，按 weak_match × rank 4 层，抽 240 条）。估计的是「**全库绑定错误率本身**」（分母 = 全库/框内绑定），不是「候选命中率」。
- 两个数不能相除或直接比较：第二轮的 111/496 ≈ 22.4% 是「机械候选里改绑成立的比例」（候选挑选偏向可疑行、靶区偏向两最弱章）；本轮的 10.65% 是全库绑定的错误率估计。用途也不同：第二轮是「清理靶区」的施工口径，本轮是「要不要全量审计、要多少会话」的规模口径。
- 时间关系：第二轮的 111 条 REBIND 已由批次 3 落表并生效（111 条中 107 条写成改绑追加行、4 条经合并后为空操作；`docs/kb-batch3-merge-rebind-2026-10-03.md` §3.1–3.2）；本轮抽样框在工作流内重生成（`overfull_nodes --write`，工作流行 180–185），抽的是批次 3 之后的现状。

## 6. 局限

1. **模型单一来源、无人类双复核**：判定由 8 名模型判定员各判 30 条，重结论由另一名未参与判定的模型复核员独立复核；两层均为模型判定 → 本报告的「错误」是**模型口径下的错误**（与第二轮、本批 SKIP 报告同一口径声明）。
2. **样本量与分层**：4×60=240；层为**等额分配**（非按比例）→ 小层（yes×rank≤4 占框 2.5%）与 70% 权重的 no×rank>4 层拿到同等精度；全库点估计主要由 no×rank>4 层决定（权重 70.2%、贡献 65.9%）。
3. **抽样方式**：层内**确定性等距**（非随机）；CI 用简单随机 + 独立二项正态近似，未计序列相关与「行序=按节点拼接」的整群效应 → 区间应视为近似（量级参考）。
4. **抽样框覆盖 47,659 / 50,383 = 94.6%**：框外 2,724 条（≤4 条材料的节点）未测；「外推全库 ≈5,365 条」含假设。
5. **层标签噪声**：`weak_match=no` 只是「标题 2-gram 有重合」，不是质量标记；`rank` 是排序位（与质量无关，`overfull_nodes.py:27-28`）。分层标签只作分诊线索。
6. **错误口径**：`NONE` 的 1 条是「材料不含可归知识点」（题型导语类非知识材料）；本报告计为绑定错误，若口径改为只算 REBIND，点估计 10.34%。
7. **单条 DISAGREE 的口径**：见 §3.3 与 §4 附注（对点估计影响 ≤0.04pp）。

## 7. 规模建议（若全量逐条审计）

以本轮实测容量外推（判定 30 条/件，复核 37 条/会话）：

- **需判条数**：全库 **50,383 条绑定**；其中超配框 **47,659 条**可直接沿用本轮分层抽取口径，框外 2,724 条（≤4 条材料的节点）建议一并判。
- **判定会话**：≈ **1,680 个**（50,383 / 30；只判框内 ≈ 1,589 个）。
- **复核会话**：预计重结论（需独立复核）≈ **5,075 条**（框内；外推全库 ≈5,365）→ ≈ **138–146 个**会话（按本轮 37 条/会话；若按第二轮 33 条/会话口径 ≈ 154–163 个）。
- **合计 ≈ 1,700–1,830 个代理会话**量级（框内 ≈1,730 / 全库外推 ≈1,830；判定 + 复核，不含抽样/汇总/写报告等少量会务会话）。判定员与复核员必须是不同会话（本仓纪律：复核不参与判定）。
- 参照：本轮 240 条 = 8 判定会话 + 1 复核会话；全量约为其 **210×**（50,383 / 240）。

## 8. 复现

```bash
# ① 抽样框实点 + 分层计数（应得 47659 与 1188/4311/8704/33456）
python - <<'EOF'
import csv, collections
rows = list(csv.DictReader(open('build/kb-staging/overfull_nodes.csv', encoding='utf-8-sig')))
def s(r):
    w = 'yes' if r['weak_match'].strip() == 'yes' else 'no'
    return 'sample-weak_match-'+w+'_rank_'+('le4' if int(r['rank']) <= 4 else 'gt4')
print(len(rows), collections.Counter(s(r) for r in rows))
EOF

# ② 抽样公式核验（应得 4 层 formula_match True；等价于 idx_i = floor((i+0.5)*N_h/60)）
python - <<'EOF'
import csv, collections
src = list(csv.DictReader(open('build/kb-staging/overfull_nodes.csv', encoding='utf-8-sig')))
def stratum(r):
    w = 'yes' if r['weak_match'].strip() == 'yes' else 'no'
    return 'sample-weak_match-%s_rank_%s' % (w, 'le4' if int(r['rank']) <= 4 else 'gt4')
layers = collections.defaultdict(list)
for r in src: layers[stratum(r)].append(r['material_slug'])
samp = list(csv.DictReader(open('build/agent-batch4/audit-sample.csv', encoding='utf-8-sig')))
by = collections.defaultdict(list)
for r in samp: by[r['slice']].append(r['material_slug'])
for s, rows in layers.items():
    N = len(rows)
    exp = [rows[((2*i+1)*N)//120] for i in range(60)]
    print(s, 'N=', N, 'formula_match', exp == by[s])
EOF

# ③ 判定计数 + 复核后加权估计（应得 KEEP 203 / REBIND 36 / NONE 1；
#    p=0.10649 se=0.02838 CI=[0.0509,0.1621] K=5075）
python - <<'EOF'
import csv, collections, math
N = {'yes_le4':1188,'yes_gt4':4311,'no_le4':8704,'no_gt4':33456}
k = {'yes_le4':18,'yes_gt4':7,'no_le4':6,'no_gt4':6}   # 复核后（§3.3：37 条重结论全部仍计现绑错误）
n = 60; T = sum(N.values())
samp = {r['material_slug']: r['slice'] for r in csv.DictReader(open('build/agent-batch4/audit-sample.csv', encoding='utf-8-sig'))}
tot = collections.Counter()
for i in range(1, 9):
    for r in csv.DictReader(open(f'build/agent-batch4/verdicts/kit-{i:02d}.verdicts.csv', encoding='utf-8-sig')):
        tot[r['verdict']] += 1
print('verdicts', dict(tot))
p = v = 0.0
for h in N:
    w = N[h]/T; ph = k[h]/n
    p += w*ph; v += w*w*ph*(1-ph)/n
se = math.sqrt(v)
print('p=%.5f se=%.5f CI=[%.4f,%.4f] K=%.0f' % (p, se, p-1.96*se, p+1.96*se, T*p))
EOF
```

## 9. 证据文件

- 抽样框（全量）：`build/kb-staging/overfull_nodes.csv`（47,659 条绑定 / 2,473 节点；由 `tools/kb_build/overfull_nodes.py --write` 生成，本工作流内重生成）。
- 样本：`build/agent-batch4/audit-sample.csv`（240 条 = 4 层 × 60）。
- 判定工具包：`build/agent-batch4/kit/kit-01..08.json`（8×30；材料正文 + 现绑节点）。
- 判定：`build/agent-batch4/verdicts/kit-01..08.verdicts.csv`（8×30，8 列）。
- 复核：`build/agent-batch4/audit-confirm.csv`（37 行，AGREE 36 / DISAGREE 1）。
- 全库终态计数旁证：`docs/kb-batch3-merge-rebind-2026-10-03.md:4,182`（知识点 3761、材料 50383）。
- 第二轮（口径对照）：`docs/kb-bind-audit-round2-2026-10-02.md`（496 条：KEEP 383 / REBIND 111 / NONE 2；§3 与 §6 复核）。
- 同批姊妹报告（同一工作流段 2）：`docs/research/skip-sampling-2026-10-03.md`。
- 设计源：`.zcode/workflow-drafts/批次-4-第一段管道回绿--两类抽样体检.dwf.ts`（段 3，行 178–246；gitignore 目录，不入库）。
