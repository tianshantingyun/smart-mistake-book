# 小 n 配对二值检索评测的统计判定（129 条 / 24 章 / top-5）

> **性质**：方法研究。本文件只写这一份；未改源码、未跑 Gradle/设备/判官、未写 sidecar。
> **写作时点**：2026-11-03T23:36:29+0800（`date '+%Y-%m-%dT%H:%M:%S%z'` 实跑）。
> **HEAD**：`0fef943641f53560b92ce2afec4da22bf52f5e86`（`git rev-parse HEAD` 实跑）。
> **金标**：`tools/kb_coverage/tables/golden_queries_v2.json`，**129 条 / 24 章**，
> sha256 `eabef10228916d46a18aaaa4e3084f5bacdf6e07ce3a3f8384d31ff71c6f2102`（`sha256sum` 实跑）。
> **基线读数**：v26 设备融合 **117/129 = 0.9069767441860465**；v27 **119/129 = 0.9224806201550387**
> （`docs/research/2026-11-02-kb-b33-bm25-prereg.md` §0.2 转录；该文件注明来自
> `build/agent-batch31a/v26-device/device-metrics.txt:3-5` 与 `v27-device/device-metrics.txt:3-5`）。
> **判分口径**：129 题 × 5 样本，top-5 命中为二值；**1 条 = 1/129 = 0.0077519 = 0.775 pp**。

---

## 0. 结论先行

1. **“净差 +6”不是充分统计量。** 精确 McNemar 只由 `(b,c)` 决定；观测到净差 +6 时，只有
   `(b,c)=(6,0)` 显著（双侧 `p=0.03125`）。`(7,1)` 已是 `p=0.0703`，`(9,3)` 是 `p=0.1460`，
   `(12,6)` 是 `p=0.2379`。所以“119→123，+6，采纳”这句话本身不成立。
2. **n=129 对 +6 的功效只有约 31%–56%。** 若真实过程是 6 条改善、0 条回归，功效约 55.8%；
   若真实过程是 9 改善 / 3 回归，功效约 30.7%；12/6 约 22.0%。80% 功效在 n=129 下需要
   **+8（零回归）到 +16（6 条回归）**，不是 +6。
3. **5–6 个臂必须做多重校正。** 5 个“臂 vs 基线”未校正的 FWER 约 **22.6%**；15 个两两比较约
   **53.7%**。Holm 下第一个阈值是 `0.05/5=0.01`，而 +6 的最小精确 p 是 `0.03125`，**连第一关都过不了**；
   要过 Holm 第一关，需要观测到 `(8,0)`（`p=0.0078`）这一档。
4. **早停可以，但不能按 p 值停。** 固定 129 条、最后只测一次最稳。若要早停，只按**不一致对总数**
   `m=b+c` 停（例如 m=8），且只测一次；因为停止规则只看 m、不看 `b-c`，条件二项分布仍精确。
   按“p<0.05 就停”会膨胀 α；按“再加题直到显著”会系统性偏向显著（Webber et al. 2008）。
5. **区间主报 Newcombe method 10，配对 bootstrap 只作敏感性。** 24 章 cluster bootstrap 在
   24 个簇、簇大小 1–12 不均时不可靠；同一 +6，若改善集中在 1 章，cluster 区间可宽到
   `[0, 14.1 pp]`，若分散在 6 章则是 `[2.0, 6.8 pp]`（本会话模拟，非仓内实测）。
6. **非劣界不能取 0，也不能取 1 条。** margin 0 等价于“优于基线”的优效检验，不是非劣；
   margin 1 条低于设计分辨率：即使 `b=c=0`，Newcombe 90% 下界已是 **−2.05 pp**（约 −2.6 条），
   所以“完全打平”也过不了 1 条的非劣界。真要非劣，按本设计至少要把 margin 放到 **5 条（3.88 pp）**
   并写明这是产品风险取舍；否则不要在 n=129 上做非劣。
7. **复用同一批 129 条做模型选择再报“获胜臂”的 p 值，是 winner's curse。** 任何采纳都必须在
   未参与选择的 fresh queries（或预注册 holdout）上复现；否则只能叫 provisional，不能叫 adopt。

---

## 1. 事实

### 1.1 本仓设计与分辨率（实读/实算）

| 项 | 值 | 出处 |
|---|---|---|
| 金标规模 | 129 条 / 24 章 | `tools/kb_coverage/tables/golden_queries_v2.json`（本会话 `json.load` 实读） |
| 判分 | 每题 top-5 命中为二值；129×5 样本 | `docs/research/2026-11-02-kb-b33-bm25-prereg.md` §0.2（转录 `GoldenRetrievalInstrumentedTest.kt:625`） |
| 基线 | 117/129 = 0.90698 | 同上（v26 设备读数） |
| v27 | 119/129 = 0.92248 | 同上（v27 设备读数） |
| 1 条查询的分辨率 | 0.775 pp | 本会话 `1/129` 实算 |
| 章大小 | `12, 11, 11, 11, 9, 9, 9, 9, 8, 8, 3×8, 2×2, 1×4` | 本会话对 JSON 按 `chapter` 计数 |
| 章大小 ≤3 的章数 | **14/24** | 同上 |

**直接后果（本会话实算）**：精确 McNemar 在 `m=b+c<6` 时**不可能**拒绝（最小 p 是
`2/2^5=0.0625`）。14 个章各自只有 1–3 条题，**这些章在逐章精确 McNemar 下永远不可能显著**；
剩下 10 个章也只有 8–12 条，要显著仍需 6 条以上不一致对且方向高度一致。因此
“逐章都显著”在本设计下不是“证据更强”，而是一个**结构性不可达**的指标。

**现有工具的口径**：`tools/kb_coverage/retrieval_significance.py` 目前给的是逐题/按章
bootstrap 区间，以及按章配对 bootstrap 的 `p_two_sided`（`retrieval_significance.py:125-160,189-199`）。
它**没有**精确 McNemar、没有 Newcombe 区间；`p_two_sided` 是“差值符号翻转比例”的 bootstrap p，
不能替代精确 McNemar 的采纳判据。本文建议保留 bootstrap 作敏感性，新增精确检验与 Newcombe 主报。

### 1.2 McNemar 精确检验：公式、临界值、功效

#### 1.2.1 定义与公式

同一批 129 条查询上比较 A（基线）与 B（候选），每题二值命中。四格表：

| | B 命中 | B 未命中 |
|---|---:|---:|
| **A 命中** | `e`（都中） | `b`（A 中、B 不中 = 回归） |
| **A 未命中** | `c`（A 不中、B 中 = 改善） | `h`（都不中） |

McNemar 只使用不一致对 `b,c`；`e,h` 不进入检验。精确双侧 p：

```text
m = b + c
p_exact = min(1, 2 * Σ_{k=0}^{min(b,c)} C(m,k) * (1/2)^m)
```

等价于“在 m 个不一致对中，符号为 B 的对数 `b ~ Bin(m, 0.5)`”的双侧二项检验。
渐近版本是 `χ²=(b-c)²/(b+c)`（可加连续性校正 `(|b-c|-1)²/(b+c)`）；**小 m 时必须用精确版**。
另记住：p 值不度量效应大小或产品重要性；报告必须同时给 δ 与 CI [S33]。
来源：McNemar 1947 [S1]；statsmodels 的 `exact=True` 明确用二项分布、统计量为 `min(b,c)` [S6]；
SciPy `binomtest` 提供同一二项检验 [S7]。Wikipedia 的二手说明还指出：`b+c<6` 时精确 p 总大于 0.05，
且精确二项与连续性校正版都偏保守 [S2]（该句可由上面的公式直接推出：m=5 时最小 p=2/32=0.0625）。

#### 1.2.2 “净差 +6 需要多少不一致对才显著”

固定观测净差 `b-c=+6`，枚举所有可能的 `m=b+c`：

| b | c | m | 精确双侧 p | α=.05 显著？ |
|---:|---:|---:|---:|---|
| 6 | 0 | 6 | **0.03125** | **是** |
| 7 | 1 | 8 | 0.07031 | 否 |
| 8 | 2 | 10 | 0.10938 | 否 |
| 9 | 3 | 12 | 0.145996 | 否 |
| 10 | 4 | 14 | 0.179565 | 否 |
| 12 | 6 | 18 | 0.237885 | 否 |
| 15 | 9 | 24 | 0.307456 | 否 |
| 18 | 12 | 30 | 0.361595 | 否 |

**答案**：观测净差 +6 时，**只有 `m=6` 且 `(b,c)=(6,0)`** 能在双侧 α=.05 下显著；
多出任何一对反向（7-1）就不显著，而且随着 m 增大 p 继续变大。换言之，
**“+6”本身不是证据；证据是“6 条改善、0 条回归”这个完整分布**。

作为对照，若用 mid-p（Fagerland et al. 2013 推荐，作为精确条件的折中）[S4]：
`(5,0)` 的 mid-p 为 0.03125，`(6,0)` 为 0.015625，`(7,1)` 为 0.03906，`(8,2)` 为 0.06543。
mid-p 能把“5-0”和“7-1”救回来，但它不再保证 Type I ≤ α；本仓建议**精确条件为主、mid-p 只作预注册敏感性**，
不能两个都跑、挑显著的那个报。

#### 1.2.3 精确检验的功效（n=129）

令 `π_b=P(A命中,B未命中)`、`π_c=P(A未命中,B命中)`。则
`M=b+c ~ Bin(n, π_b+π_c)`，且条件在 `M=m` 下 `b ~ Bin(m, π_b/(π_b+π_c))`。
功效为：

```text
Power = Σ_{m=0}^{n} P(M=m) * Σ_{b: p_exact(b,m-b)<α} P(B=b | M=m)
```

这是 Lachin 1992 讨论的同一类无条件功效函数 [S3]；R `exact2x2::powerPaired2x2(pb, pc, npairs)`
的参数化也是 `pb=P(control=0,test=1)`、`pc=P(control=1,test=0)` [S5]。本会话用该式实算，
并用 50,000 次 Monte Carlo 对拍（误差 <0.005）。

**真实过程为净 +6/129 时的功效**（`π_b=b/129, π_c=c/129`）：

| 真实 (b,c)/129 | 净差 | 功效（n=129） |
|---|---:|---:|
| 6 / 0 | +6 | **0.558** |
| 7 / 1 | +6 | 0.442 |
| 8 / 2 | +6 | 0.362 |
| 9 / 3 | +6 | **0.307** |
| 10 / 4 | +6 | 0.271 |
| 12 / 6 | +6 | 0.220 |
| 15 / 9 | +6 | 0.169 |
| 18 / 12 | +6 | 0.143 |
| 24 / 18 | +6 | 0.120 |

**n=129、80% 功效所需的最小净改善**（精确双侧 α=.05）：

| 真实回归数 c | 所需改善数 b | 净差 |
|---:|---:|---:|
| 0 | 8 | **+8** |
| 1 | 11 | +10 |
| 2 | 14 | +12 |
| 3 | 16 | +13 |
| 4 | 18 | +14 |
| 6 | 22 | +16 |

**解释**：n=129 的设计分辨率大约在“+8 且零回归”这一档；+6 的真实效应有 44%–69% 的概率
根本不会被判显著。把“+6”写成主判据，等于预注册一个功效不足的试验。若产品只关心 +6，
要么把金标扩到约 169 条（6/0 过程 80% 功效；9/3 过程要约 362 条），要么接受“多数 +6 效应判不出来”。

### 1.3 多臂比较：Bonferroni/Holm 要不要

#### 1.3.1 算术

若对 m 个臂各自做一次未校正 α=.05 的检验，FWER（至少一次假阳性）在独立假设下是
`1-(1-α)^m`；两两比较 m 个系统则是 `1-(1-α)^{m(m-1)/2}`。Sakai 2014 的 IR 评测指南明确写出后者
[S8]。本会话实算：

| 场景 | 检验数 | 未校正 FWER |
|---|---:|---:|
| 4 个候选臂 vs 同一基线 | 4 | 18.5% |
| 5 个候选臂 vs 同一基线 | 5 | **22.6%** |
| 6 个候选臂 vs 同一基线 | 6 | 26.5% |
| 6 个臂两两比较 | 15 | **53.7%** |
| 24 章各做一次 | 24 | 70.8%（独立性假设，仅示意） |

**Holm** 与 Bonferroni 同样控制 FWER，但 Holm 一致更有功效（任意依赖下有效）[S14]：把 p 升序排，
第 i 个与 `α/(m-i+1)` 比；Bonferroni 全部与 `α/m` 比。BH 控制 FDR 而非 FWER [S15]。
5 个候选臂 vs 基线时 Holm 阈值依次是 `0.01, 0.0125, 0.0167, 0.025, 0.05`；实现见 statsmodels
`multipletests` [S35]。**+6 的最小精确 p=0.03125 > 0.01**，所以 5 臂 Holm 下
任何“+6 净改善”都不可能被采纳；要过第一关，需要观测到 `(8,0)` 的 `p=0.0078`。
若同时有 `(8,0)` 和 `(7,0)`：Holm 第一关过（0.0078≤0.01），第二关 `0.0156>0.0125` 失败；
BH（FDR q=.05）则会两个都过（0.0078≤0.01，0.0156≤0.02）。这就是 FWER 与 FDR 的差别。

#### 1.3.2 业界与权威说法

- **Sakai 2014（IR 评测指南）**：m>2 时不要对每对独立做 t 检验；先做 two-way ANOVA，
  显著后再用 randomized Tukey HSD；并给出上面的 FWER 公式 [S8]。Sakai 2018 教材第 4 章
  专门讲 multiple comparison procedures [S11]。
- **Carterette 2012（ACM TOIS）**：系统研究“同一批数据上同时检验很多假设”；摘要指出常见做法
  忽略大量信息，纳入哪怕少量信息也会让结论与已发表文献不同 [S9]。该文是 IR 多重检验的经典引用；
  本会话只读到摘要级（ACM/CiteseerX 全文抓取失败），标“摘要级证据”。
- **Otero, Parapar & Barreiro 2025（ECIR）**：用模拟与真实 TREC 数据比较未校正 t/Wilcoxon、
  Tukey HSD、Bonferroni/Holm/BH/BY；结论是未校正显著膨胀，多数校正过保守，Wilcoxon+BH 在
  50 topics 时最接近名义 FWER 且功效最好，推荐 Wilcoxon + BH [S10]。**单一论文结论，标“待独立复核”**；
  它的语境是“多系统比较/发现”，不等于“每个臂都要做产品采纳裁定”。
- **TREC 经验**：TREC 概述论文通常报告多系统均值/名次，方法学校正不是 track 的强制项。
  本会话实读 TREC 2004 Robust 概述全文，未见 `significance`/`Bonferroni`/`multiple` 字样 [S12]。
  这**不能**证明所有 TREC 论文都不校正，但足以说明“TREC 都这么做”不能当作不校正的理由；
  反过来，也没有找到 TREC 官方“必须 Holm”的规定（见 §4）。
- **本仓适配**：二值配对数据下，多臂的 omnibus 检验用 **Cochran's Q**（McNemar 的 k 臂推广）
  [S13][S36]；Q 显著后再做 pairwise 精确 McNemar + Holm。不要用 ANOVA/Tukey 直接套二值数据
  （Sakai 的 ANOVA 建议针对连续指标）。

#### 1.3.3 结论

**需要校正。** 但更根本的问题是：在 n=129、5–6 臂、二值 top-5 下，FWER 校正会把“+6”这一档
几乎全部判死。正确做法不是“硬校正后宣布全都不显著”，而是**减少 family**：
预注册 1 个主臂（有机制先验的那个）做 α=.05 的精确 McNemar；其余 4–5 个臂作为次要/探索，
用 Holm 控制 family，或只报效应量与 CI、不宣称采纳。若产品要求每个臂都给采纳/否决，
用 Holm 是诚实的：大多数臂的结论会是“未采纳”，这不是失败，而是设计分辨率的真实反映。

### 1.4 序贯检验/早停：可用性、正确写法、先例

#### 1.4.1 为什么不能“看着 p 值停”

重复检验会膨胀 α：Armitage, McPherson & Rowe 1969 的经典结果 [S18]。在 IR 离线评测里，
Webber, Moffat & Zobel 2008 进一步指出：**逐题加入直到功效/显著性达到**，会偏向“既找到功效
又找到显著”，并给出混合设计下的报告要求 [S23]。所以“先跑 60 条，不显著再加到 129 条”
不是免费午餐。

#### 1.4.2 可用的三种写法

**(A) 固定 n=129，最后只测一次（本仓首选）。**
金标集本来就是固定的 129 条，跑满的成本很低；早停省不了多少，反而引入序贯解释问题。
预注册“只在 n=129 时执行一次精确 McNemar”，α 不膨胀。

**(B) 按不一致对总数停，只测一次（信息时间停）。**
预注册：`持续收集，直到 m=b+c 达到 8，或 n 达到 129，以先到者为准；然后只做一次精确 McNemar；
若 m=8 时是 7-1，也按这一次判，不得继续收集后再测。`
这个规则**只依赖 m，不依赖 b-c**；在 H0 下，给定 m 个不一致对，其符号仍是 m 个独立公平硬币，
所以条件二项检验仍精确，α 不膨胀。它不是“按 p 值停”，而是“按信息量停”。
注意：m=8 是“最早可能过 Holm 第一关”的档（8-0 的 p=0.0078）；若只做单臂 α=.05，m=6 即可
（6-0 的 p=0.03125）。但如果你在 m=6 看了结果、不显著又继续到 m=8 再测，就变成两次检验，
必须用下面的 alpha spending。

**(C) 多次查看：group sequential + alpha spending。**
预注册查看时点（按 m 或按 n）与消耗函数。Lan & DeMets 1983 的 α 消耗法允许查看时点灵活 [S19]；
Pocock 1977 [S20] 与 O'Brien & Fleming 1979 [S21] 是两种经典边界。以 Lan-DeMets OBF 近似为例，
双侧 α=.05、两次等距查看（t=0.5,1.0）时，第一次的名义 α 约 **0.00557**，不是 0.05
（`α*(t)=2(1-Φ(z_{α/2}/√t))`；`gsDesign::sfLDOF` 文档 [S37]）。每次查看用对应名义 α，
最终总 α 仍为 .05。

**SPRT（Wald 1945）** [S17] 也可用：预注册 `p0=0.5`（无差异）、`p1`（例如不一致对中 0.65 偏向候选）、
α、β，按 Wald 边界 `A=log((1-β)/α)`、`B=log(β/(1-α))` 在每对不一致对上更新似然比，
越过边界即停。它控制 α/β，但需要指定 `p1`，且停止后的 p 值/CI 解释不如固定样本直观。
对本仓“固定 129 条、只关心采纳/否决”的场景，SPRT 的收益不大。

#### 1.4.3 公开先例

- **有（在线）**：Kharitonov, Vorobev, Macdonald, Serdyukov & Ounis, SIGIR 2015，
  “Sequential Testing for Early Stopping of Online Experiments”，把 O'Brien-Fleming 与 MaxSPRT
  改造成适用于 interleaving / A/B 的序贯检验，数据是 115 个 interleaving、41 个 A/B 实验 [S22]。
  这是**在线**搜索评估，不是离线 test collection。
- **未找到（离线）**：没有检索到“离线检索 test collection 上用 SPRT/group sequential 做早停”的
  公开先例（检索 3 轮：OpenAlex/Crossref/Bing；见 §4）。**未找到 ≠ 不存在**，但至少不能把
  “离线早停”写成有共识的常规做法。
- **反向证据**：Webber et al. 2008 明确警告“加到显著”会偏向显著 [S23]；这与“按 p 值早停”直接冲突。

**本仓结论**：固定 129 条、一次检验；若必须早停，只用 (B) 的“按 m 停、只测一次”，或 (C) 的
预注册 alpha spending。不要用“p<.05 就停”。

### 1.5 置信区间：Newcombe、bootstrap、cluster

#### 1.5.1 Newcombe method 10（配对二值差异的首选）

记 `e`=都中、`f`=A 中 B 不中（回归）、`g`=A 不中 B 中（改善）、`h`=都不中，`n=e+f+g+h`。
要估的是 `θ=p_A-p_B=(f-g)/n`（候选减基线则是 `δ=-θ=(g-f)/n`）。Newcombe 1998 比较了 10 种方法，
推荐 **method 10**：对两个边际比例用 Wilson score 区间，再用连续性校正的相关系数 `φ` 合并 [S24]。
公式：

```text
p1=(e+f)/n, p2=(e+g)/n
(l2,u2)=Wilson(p1,n,z), (l3,u3)=Wilson(p2,n,z)
φ = (e*h - f*g) / sqrt((e+f)(g+h)(e+g)(f+h))
    若 e*h > f*g，分子改为 max(e*h - f*g - n/2, 0)
    若分母为 0，φ=0
dl2=p1-l2, du2=u2-p1, dl3=p2-l3, du3=u3-p2
d  = sqrt(dl2^2 - 2φ·dl2·du3 + du3^2)
d' = sqrt(du2^2 - 2φ·du2·dl3 + dl3^2)
CI(θ) = [θ_hat - d, θ_hat + d']
```

本会话用 Newcombe 1998 Table III 的三个算例对拍，逐位复现：
`(e,f,g,h)=(36,12,2,0)` → `[0.0569,0.3404]`；`(36,14,0,0)` → `[0.1528,0.4167]`；
`(18,12,2,18)` → `[0.0562,0.3290]`（论文表值）[S24]。这说明公式实现正确。

**本仓算例**（`e=117`，其余格如下；δ=候选−基线，单位 pp）：

| (b,c) | δ | Newcombe 95% CI | 配对 bootstrap 95% CI | 精确 p |
|---|---:|---|---|---:|
| (0,6) | +4.65 | **[+0.60, +9.73]** | [+1.55, +8.53] | 0.03125 |
| (1,7) | +4.65 | **[+0.01, +10.14]** | — | 0.07031 |
| (2,8) | +4.65 | [−0.54, +10.51] | [0.00, +9.30] | 0.10938 |
| (3,9) | +4.65 | [−0.85, +10.67] | [−0.78, +10.08] | 0.145996 |
| (6,0) | −4.65 | [−9.73, −0.60] | [−8.53, −1.55] | 0.03125 |

注意 `(1,7)` 的 Newcombe 区间下界略高于 0，而精确 p=0.0703 > .05：**不同方法在边界附近会不一致**。
预注册必须指定“主判据是精确 p 还是 CI 下界”，不能两个都算、挑显著的那个报。

#### 1.5.2 配对 bootstrap：能做什么、不能做什么

- **逐题配对 bootstrap**：以查询为重采样单位，保持 A/B 同题配对；算 δ 的经验分布，
  取 2.5/97.5 分位。它保留了题内相关，适合给“效应量分布”的直观图。
- **局限**：二值 + 小 m 时分布离散，百分位区间可能偏窄或端点跳变；它不条件在 m 上，
  与精确 McNemar 不是同一检验。bootstrap p 值（符号翻转比例）不能替代精确 p。
- **BCa**：可作为偏差校正的敏感性分析；但 n=129、事件数少时，加速常数估计不稳，仍不宜作主判据。
  参考 Efron & Tibshirani 的 bootstrap 区间综述 [S25]。

#### 1.5.3 按章 cluster bootstrap：为什么不能当主判据

本会话用真实章大小 `[12,11,11,11,9,9,9,9,8,8,3×8,2×2,1×4]` 做了一个**合成**演示：
基线 117/129，候选 123/129（+6），两种分布：

| 改善分布 | 逐题 bootstrap 95% CI | 按章 cluster 95% CI |
|---|---|---|
| 6 条改善分散在 6 章 | [+1.55, +8.53] pp | **[+1.98, +6.78] pp** |
| 6 条改善全部集中在 1 个 12 题章 | [+1.55, +8.53] pp | **[0, +14.06] pp** |

同一 +6、同一批题，cluster 区间可以从“显著”变成“跨 0”。原因：24 个簇太少（少数簇问题），
且簇大小 1–12 极不均；重采样章时，小章与大章等权，ratio estimator 的方差很大。
少数簇推断的可靠性问题见 Cameron & Miller 2015 [S26] 与 Cameron, Gelbach & Miller 2008 [S27]。
**结论**：cluster bootstrap 只能作敏感性/诊断；主判据用逐题精确 McNemar + Newcombe，
另加“不得由单章驱动”的产品 guardrail（§2）。

### 1.6 非劣设计：margin 取 0 还是 −1 条

#### 1.6.1 假设与判定

非劣检验的假设（δ=候选−基线）：

```text
H0: δ ≤ -Δ   （候选比基线差至少 Δ）
H1: δ > -Δ   （候选的损失小于 Δ）
```

单侧 α=.05 的判定：**δ 的单侧 95% 下界 > −Δ**（等价于双侧 90% CI 下界 > −Δ）。
对配对二值，用 Newcombe method 10 的 90% 双侧区间，或精确条件区间；报告 δ、CI、b/c、Δ。
不能把“p≥.05”直接翻译成“非劣”；也不能把“未证明非劣”翻译成“劣于基线”——后者需要
CI 上界 < −Δ（或基线优于候选的优效检验显著）。Altman & Bland 1995 的“absence of evidence
is not evidence of absence”在这里同样适用 [S30]。

#### 1.6.2 margin 0 / 1 条为什么不行

- **Δ=0**：H0 变成 `δ≤0`，H1 是 `δ>0`，这就是**优效检验**，不是非劣。一个真正相等的系统
  在单侧 α=.05 下仍有约 **5%** 的概率“通过”；把它叫非劣是概念错误。非劣的 Δ 必须 >0。
- **Δ=1 条（0.775 pp）**：低于本设计的分辨率。即使 `b=c=0`（完全打平），Newcombe 90% 下界
  仍是 **−2.05 pp**（约 −2.6 条）；`(b,c)=(1,0)` 时下界 **−3.40 pp**（约 −4.4 条）。
  所以 margin 1 条连“完全打平”都过不了，更不用说容忍一条回归。

#### 1.6.3 本设计的 margin 可行域（本会话实算）

| margin（条） | Δ（pp） | `b=c=0` 时 90% 下界 | `b=1,c=0` 时 90% 下界 | 判定 |
|---:|---:|---:|---:|---|
| 0 | 0 | −2.05 | −3.40 | 不是非劣，是优效 |
| 1 | 0.775 | −2.05 | −3.40 | 打平也过不了 |
| 2 | 1.550 | −2.05 | −3.40 | 打平也过不了 |
| 3 | 2.326 | −2.05 | −3.40 | 打平可过；1 条回归过不了 |
| 4 | 3.101 | −2.05 | −3.40 | 打平可过；1 条回归过不了 |
| 5 | 3.876 | −2.05 | −3.40 | 打平与 1 条回归都可过 |

**怎么定才不是拍脑袋**：先写“产品最多能接受多少条 top-5 命中损失”（例如 5 条 = 3.88 pp），
再检查这个 Δ 是否大于设计分辨率（上面的表），最后检查在这个 Δ 下的功效/通过概率。
FDA 的非劣指南要求 margin 预先指定，并用历史阳性对照效应 M1、临床判断 M2 来论证 [S28]；
CONSORT 非劣扩展要求报告 margin 的依据与单侧 CI [S29]。检索评测没有“安慰剂/阳性对照”的
直接类比，所以只能把 M2 换成“产品可接受的最大损失”（smallest effect size of interest /
equivalence margin 的思路见 Lakens 2017 [S31]），并明确它是**决策取舍**，不是数据算出来的。

**一个更贴近实际的算术**：若真实系统与基线等价、但存在 5% 的不一致率（每方向 2.5%），
本会话模拟显示：margin 3 条（2.33 pp）只有约 **26%** 的概率通过非劣；margin 4 条约 40%；
margin 5 条约 **56%**。也就是说，margin 3 不是“保守”，而是“即使真打平也大概率判不出非劣”；
margin 5 也只有 56%；若把 **3.876 pp 这个界固定**、只扩 n，约 **240 条**才有 80% 的通过概率。
若产品不能接受 5 条损失，n=129 就不足以支持非劣结论，应扩样或改问优效。

#### 1.6.4 先例

**未找到**“检索系统非劣检验”的公开先例（3 轮检索：OpenAlex/Crossref/Bing；见 §4）。
临床非劣方法（FDA 2016 [S28]、Piaggio et al. 2006 [S29]）可借用假设与报告格式，但不能直接
借用 M1/M2 的“历史阳性对照”论证。相邻的 ML 等价检验/run-count 设计有 2026 年预印本
（arXiv:2610.23700），但**单一来源、未复核**，不作为本仓依据。

### 1.7 与配对 bootstrap 的关系（一句话版）

McNemar 精确检验**条件在 m=b+c 上**，只问“这 m 个不一致对的符号是否公平”；配对 bootstrap
**重采样整个查询集**，估的是 δ 的抽样分布。两者 estimand 相同（δ=p_B−p_A），但一个精确条件、
一个非参数渐近；小 m 时 bootstrap 可能不稳，精确 McNemar 更适合 p 值，Newcombe 更适合 CI。
**不要**用“bootstrap 显著”去救“McNemar 不显著”，也不要用“McNemar 不显著”去否定一个
bootstrap CI 排除 0 的效应；预注册一个主判据，其余只作敏感性。

---

## 2. 本仓应采用的判定规则（预注册模板）

### 2.1 预注册模板（可直接抄进 prereg）

预注册的通用原则（先写死假设、判据、停止规则，再看结果）见 Nosek et al. 2018 [S34]。
本仓模板如下：

```text
评估 ID：KB-R10-<arm>
日期 / HEAD / 金标 sha：<date> / <head> / eabef102…
基线：<version>，129 条，top-5 二值 Recall@5，语料快照 <sha>
臂：A0=基线；A1=主臂（预注册的唯一主比较）；A2..A5=次要臂
主指标：每题 top-5 命中二值（0/1）；不使用 MRR/nDCG 做采纳判据
主判据：精确 McNemar 双侧，α=.05；报告 b,c,m,p, δ, Newcombe 95% CI
主臂采纳：p<.05 且 δ≥+8/129（+6.20 pp）且 Newcombe 95% CI 下界>0
次要家族：A2..A5，Holm α=.05，family size=实际次要臂数（示例为 4）；每个臂只有在
          Holm 校正后 p<.05 且 δ≥+8/129 时才采纳
非劣（可选，仅当预注册为“替换”问题）：Δ=5/129=3.876 pp；
          单侧 95% 下界 > -Δ（Newcombe 90% 双侧）；不与优效混用
早停：无；固定 n=129，最后只测一次。
      若必须早停：只在 m=b+c 达到 8 或 n=129（先到者）时测一次；
      只看 m，不看 b-c；不得在 m=6 看一次、不显著再到 m=8 看第二次。
逐章：只报每章命中数/总数与净变化；不报逐章 p 值，不用逐章星号做总体结论。
      Guardrail：任一章损失 ≥2 条 → 阻断采纳，转人工复核；
      净增益中单章贡献 ≥50% → 标记“单章驱动”，需 fresh-query 复现。
复现：任何采纳必须在未参与选择的 fresh queries / 预注册 holdout 上复现；
      否则只能写“provisional”，不得写“adopt”。
不可判定：n=129 时 m<6 → inconclusive；p≥.05 → 未采纳（不是“等价”）。
数据完整性：同一 golden sha、同一语料快照、同一设备/ABI；缺题/换题即作废。
```

### 2.2 判定表

| 条件 | 判定 | 说明 |
|---|---|---|
| `m<6` 且 `p≥.05` | **不可判定（inconclusive）** | 精确检验在 m<6 时不可能显著；不是“无差异” |
| `p<.05` 且 `δ≥+8/129` 且 CI 下界>0 | **采纳（adopt）** | 统计显著 + 达到设计可分辨的实用阈值 |
| `p<.05` 且 `0<δ<+8/129` | **不采纳；promising** | 统计显著但低于预注册实用阈值；需复现 |
| `p≥.05` 且 CI 下界 > −Δ_NI（仅预注册非劣） | **非劣（not inferior）** | 只能用于预注册的“替换”问题 |
| `p≥.05` 且 CI 上界 < −Δ_NI | **劣于基线（inferior）** | 需要 CI 上界越过 −Δ，不能只看 p |
| 其他 `p≥.05` | **未证明（not demonstrated）** | 不是“等价”，不是“无差异” |
| 次要臂 Holm 后 `p≥.05` | **不采纳** | 即使未校正 p<.05，也不能采纳 |

**“reject”语义**：上表的“不采纳”是“没有足够证据支持采纳”，不是“证明该臂更差”。
只有 CI 上界 < −Δ（或基线优效显著）才写“劣于基线”。

### 2.3 多臂的具体做法

1. **先减 family**：预注册 1 个主臂（A1）。主臂用 α=.05，不校正；这是唯一的 confirmatory 比较。
2. **次要臂用 Holm**：A2..A5 作为一个 family，`m=4`，阈值 `0.0125, 0.0167, 0.025, 0.05`；
   每个臂仍要求 `δ≥+8/129`。Holm 控制 FWER，适合“每个臂都要采纳/否决”的裁定。
3. **若有先验顺序**：改用 fixed-sequence 门控（Wiens 2003 [S16]）：按预注册顺序逐臂测 α=.05，
   一旦某臂失败就停止，后续臂不再采纳。它比 Holm 更有功效，但只在顺序有机制依据时合法。
4. **BH 只用于探索**：若目标是“从 5–6 个臂里筛候选”，可用 BH（q=.05 或 .10）并明确标 exploratory；
   任何 BH 胜出者必须 fresh-query 复现后才能 adopt。不要用 BH 做最终产品采纳。
5. **不要 all-pairwise**：6 臂两两 15 个检验，Holm 第一阈值 .0033，n=129 下几乎不可能；
   只做“每个候选臂 vs 同一基线”的比较（5–6 个），不做所有两两。
6. **不要跨指标凑 family**：Recall@5 是主指标；MRR/nDCG 是次要描述，不参与采纳阈值，
   也不因为“MRR 显著”就采纳一个 top-5 不显著的臂。

### 2.4 非劣的具体做法

- 只有预注册为“替换/降本”问题才做非劣；同一个臂不能既做优效又做非劣、挑一个通过。
- Δ 用 §1.6.3 的可行域：本设计下若要容忍 1 条回归，最小可用 **5 条（3.876 pp）**；
  写清“这是产品可接受的最大损失”，并附上“为什么不是 0/1/3 条”的算术。
- 判定：Newcombe 90% 双侧 CI 的**下界 > −Δ**。报告 δ、CI、b/c、Δ、以及“若真实等价且 5% 不一致，
  通过概率”的敏感性（等价 + 5% 不一致时，margin 3 约 26%、margin 5 约 56%；所以 margin 3 不可用）。
- 若 Δ=5 仍不能接受，结论应是“n=129 无法支持该非劣主张”，而不是偷偷把 Δ 调大。

### 2.5 早停的具体写法

- 默认：固定 n=129，一次检验。
- 可选：`stop when m=b+c reaches 8 or n reaches 129, whichever first; analyze once`。
  这句必须写进 prereg，并写明“m=8 时即使是 7-1 也按这一次判，不得续测”。
- 多次查看：预注册 `m1=8, m2=16` 与 Lan-DeMets OBF 消耗；第一次名义 α≈0.0056（t=.5），
  第二次用剩余 α；报告每次的名义 α 与最终结论。
- 禁止：`if p<.05 stop`、`add queries until significant`、`try one more arm then correct later`。

### 2.6 逐章与复现

- 逐章只作描述：每章 `hits/total`、净变化、是否被单章驱动。不要给 24 个逐章 p 值；
  14/24 章 ≤3 条，逐章精确检验结构性不可达。
- 硬 guardrail：任一章损失 ≥2 条 → 阻断采纳；单章贡献 ≥50% 净增益 → 标记“单章驱动”，
  需复现。若 cluster bootstrap CI 跨 0 而逐题精确 p<.05，标记“cluster-sensitive”，
  不直接采纳。
- 复现：本仓只有一套 129 条金标；在扩集/新集出现前，任何“采纳”都只能是 provisional。
  不要把同一批 129 条既当开发集又当确认集；模型选择（5–6 臂比较）必须与最终确认分离。

---

## 3. 反例清单（本仓数字）

| # | 反例 | 为什么错 | 本仓算例/来源 |
|---:|---|---|---|
| 1 | “119 > 117，+2，采纳” | 点估计不是证据；+2 的精确 p 可以很大 | `(b,c)=(0,2)`：p=0.5；`(1,3)`：p=0.625 |
| 2 | “同样是 +6，证据一样强” | McNemar 用完整 `(b,c)`，不是净差 | 6-0 p=.031；7-1 .070；9-3 .146；12-6 .238 |
| 3 | “117/129 vs 123/129，做个两比例检验” | 忽略配对；配对设计把题内相关消掉 | 独立两比例 z 检验 p≈0.143；配对 6-0 精确 p=.031 |
| 4 | “每章都显著，所以总体显著” | 24 次检验膨胀 FWER；且 14 章 ≤3 条，逐章精确不可能显著 | 24 次未校正 FWER≈70.8%；m<6 无拒绝 |
| 5 | “5 个臂都测，不校正” | 5 次 α=.05 的 FWER≈22.6% | 5 臂 Holm 第一阈值 .01；+6 的 .031 过不了 |
| 6 | “先测 6 条，不显著再加到 129” | 按结果决定是否继续，α 膨胀 | Armitage 1969 [S18]；Webber 2008 [S23] |
| 7 | “m=6 时 6-0 显著，于是继续加到 12 再报一次” | 两次查看 = 两次检验，必须 alpha spending | 第二次不能再用 .05；Lan-DeMets [S19] |
| 8 | “p≥.05，所以两系统等价/非劣” | 未拒绝 ≠ 等价；非劣需要 margin 与 CI | Altman & Bland 1995 [S30]；§1.6 |
| 9 | “非劣界取 0 或 1 条” | 0 是优效；1 条低于设计分辨率，打平也过不了 | 零不一致 90% 下界 −2.05 pp；1 回归 −3.40 pp |
| 10 | “5–6 个臂里挑最大的，报它的 p” | 选择后 p 值不再是确认性 p；winner's curse | Cawley & Talbot 2010 [S32]；Webber 2008 [S23] |
| 11 | “cluster bootstrap 跨 0，但逐题 p<.05，取逐题” | 预注册主判据只能有一个；cluster 敏感性不能忽略 | 同一 +6：集中一章 CI [0,14.1] pp；分散 [2.0,6.8] pp |
| 12 | “MRR 显著，所以 top-5 臂可采纳” | 指标/estimand 不同；主指标是 Recall@5 二值 | 预注册主指标唯一；其余只作描述 |
| 13 | “p<.05 但 δ=+3，也采纳” | 统计显著不等于实用显著；+3 低于设计分辨率 | 80% 功效需 +8（零回归）；+3 多数是噪声 |
| 14 | “用同一批 129 条调 5 个臂，再报最佳臂的 CI” | 选择偏差；CI 未覆盖选择过程 | Cawley & Talbot 2010 [S32]；需 fresh queries |

---

## 4. 未验证项

1. **离线 IR 的 SPRT/group sequential 先例**：未找到（OpenAlex/Crossref/Bing 3 轮）。
   Kharitonov et al. 2015 是在线 A/B/interleaving [S22]，不能直接外推到离线 test collection。
   **未找到 ≠ 不存在**。
2. **检索系统非劣检验先例**：未找到。临床非劣（FDA/Piaggio）只提供假设与报告格式，
   不能提供“检索系统 M1/M2”的历史对照论证。相邻 ML 等价检验预印本 arXiv:2610.23700
   为单一来源、未复核，未采用。
3. **TREC 全局校正惯例**：未做系统审计；只实读 TREC 2004 Robust 概述（无 significance/Bonferroni
   字样）[S12]。没有找到 TREC 官方“必须校正”的规定；不能把“TREC 惯例”当权威依据。
4. **功效表的独立复算**：本会话用无条件卷积实算，并用 50,000 次 Monte Carlo 对拍（误差 <0.005）；
   未与 R `exact2x2::powerPaired2x2` 逐点对拍。公式参数化与 [S5] 一致，但仍标“本会话计算”。
5. **Newcombe 实现**：已用 Newcombe 1998 Table III 三个算例逐位对拍 [S24]；未与第二个独立实现
   （如 R `exact2x2::mcnemarExactDP` 或 `PropCIs`）逐点对拍。公式级验证通过。
6. **Carterette 2012 全文**：ACM/CiteseerX 抓取失败，只读到摘要 [S9]；其具体推荐（是否 Holm/Bayes）
   未逐条核实，标“摘要级证据”。
7. **Otero et al. 2025 的 BH 推荐**：单一论文（ECIR 2025）[S10]；其“BH 在 50 topics 最接近名义
   FWER”未独立复现。标“单一来源·待验证”。
8. **`b+c<6` 精确 p 恒 >.05**：由公式直接推出（m=5 最小 p=0.0625）；Wikipedia 二手说明 [S2]
   与之一致，但未抓取原始教材页面。
9. **cluster bootstrap 演示**：§1.5.3 的 `[0,14.06]` / `[1.98,6.78]` 是本会话**合成**数据，
   不是仓内 129 条的真实读数；只用于说明机制，不作为本仓实测结论。
10. **二值前提**：本文所有 McNemar/Newcombe 结论都假设 top-5 命中是 0/1、无部分 credit、
    无并列截断。若未来改成 graded relevance 或 nDCG，需换配对 permutation/paired t 等，
    不能直接套本文公式。

---

## 5. 参考来源

- [S1] McNemar, Q. (1947). Note on the sampling error of the difference between correlated
  proportions or percentages. *Psychometrika* 12(2):153–157.
  https://doi.org/10.1007/BF02295996
- [S2] Wikipedia, “McNemar's test”（二手；用于 `b+c<6` 与保守性说明）.
  https://en.wikipedia.org/wiki/McNemar%27s_test
- [S3] Lachin, J. M. (1992). Power and sample size evaluation for the McNemar test with
  application to matched case-control studies. *Statistics in Medicine* 11(9):1239–1251.
  https://doi.org/10.1002/sim.4780110909
- [S4] Fagerland, M. W., Lydersen, S., & Laake, P. (2013). The McNemar test for binary
  matched-pairs data: mid-p and asymptotic are better than exact conditional.
  *BMC Medical Research Methodology* 13:91. https://doi.org/10.1186/1471-2288-13-91
- [S5] R `exact2x2::powerPaired2x2` 文档（pb/pc 参数化与精确功效）.
  https://search.r-project.org/CRAN/refmans/exact2x2/html/powerPaired2x2.html
- [S6] statsmodels `mcnemar` 文档（`exact=True` 用二项分布，统计量 `min(n1,n2)`）.
  https://www.statsmodels.org/stable/generated/statsmodels.stats.contingency_tables.mcnemar.html
- [S7] SciPy `binomtest` 文档（二项精确检验）.
  https://docs.scipy.org/doc/scipy/reference/generated/scipy.stats.binomtest.html
- [S8] Sakai, T. (2014). Statistical Reform in Information Retrieval? *ACM SIGIR Forum* 48(1).
  https://sigir.org/files/forum/2014J/2014J_sigirforum_Article_TetsuyaSakai.pdf
- [S9] Carterette, B. A. (2012). Multiple testing in statistical analysis of systems-based
  information retrieval experiments. *ACM TOIS* 30(1):4. https://doi.org/10.1145/2094072.2094076
- [S10] Otero, D., Parapar, J., & Barreiro, Á. (2025). Towards Reliable Testing for Multiple
  Information Retrieval System Comparisons. ECIR 2025; arXiv:2501.03930.
  https://arxiv.org/abs/2501.03930
- [S11] Sakai, T. (2018). *Laboratory Experiments in Information Retrieval*, Ch. 4
  “Multiple Comparison Procedures”. Springer.
  https://link.springer.com/chapter/10.1007/978-981-13-1199-4_4
- [S12] Voorhees, E. M. (2004). Overview of the TREC 2004 Robust Retrieval Track.
  https://trec.nist.gov/pubs/trec13/papers/ROBUST.OVERVIEW.pdf
- [S13] Cochran, W. G. (1950). The comparison of percentages in matched samples.
  *Biometrika* 37(3–4):256–266. https://doi.org/10.1093/biomet/37.3-4.256
- [S14] Holm, S. (1979). A simple sequentially rejective multiple test procedure.
  *Scandinavian Journal of Statistics* 6(2):65–70. 方法说明（二手）：
  https://en.wikipedia.org/wiki/Holm%E2%80%93Bonferroni_method
- [S15] Benjamini, Y., & Hochberg, Y. (1995). Controlling the false discovery rate.
  *JRSS B* 57(1):289–300. https://doi.org/10.1111/j.2517-6161.1995.tb02031.x
- [S16] Wiens, B. L. (2003). A fixed sequence Bonferroni procedure for testing multiple endpoints.
  *Pharmaceutical Statistics* 2(3):211–215. https://doi.org/10.1002/pst.64
- [S17] Wald, A. (1945). Sequential tests of statistical hypotheses.
  *Annals of Mathematical Statistics* 16(2):117–186. https://doi.org/10.1214/aoms/1177731118
- [S18] Armitage, P., McPherson, C. K., & Rowe, B. C. (1969). Repeated significance tests on
  accumulating data. *JRSS A* 132(2):235–244. https://doi.org/10.2307/2343787
- [S19] Lan, K. K. G., & DeMets, D. L. (1983). Discrete sequential boundaries for clinical trials.
  *Biometrika* 70(3):659–663. https://doi.org/10.1093/biomet/70.3.659
- [S20] Pocock, S. J. (1977). Group sequential methods in the design and analysis of clinical trials.
  *Biometrika* 64(2):191–199. https://doi.org/10.1093/biomet/64.2.191
- [S21] O'Brien, P. C., & Fleming, T. R. (1979). A multiple testing procedure for clinical trials.
  *Biometrics* 35(3):549–556. https://doi.org/10.2307/2530245
- [S22] Kharitonov, E., Vorobev, A., Macdonald, C., Serdyukov, P., & Ounis, I. (2015).
  Sequential Testing for Early Stopping of Online Experiments. SIGIR 2015.
  https://doi.org/10.1145/2766462.2767729
  OA PDF: https://eprints.gla.ac.uk/105812/1/105812.pdf
- [S23] Webber, W., Moffat, A., & Zobel, J. (2008). Statistical power in retrieval experimentation.
  CIKM 2008. https://doi.org/10.1145/1458082.1458158
- [S24] Newcombe, R. G. (1998). Improved confidence intervals for the difference between
  binomial proportions based on paired data. *Statistics in Medicine* 17(22):2635–2650.
  https://doi.org/10.1002/(SICI)1097-0258(19981130)17:22%3C2635::AID-SIM954%3E3.0.CO;2-C
  PDF: https://site.uottawa.ca/~nat/Courses/csi5388/Newcombe.1998.pdf
- [S25] DiCiccio, T. J., & Efron, B. (1996). Bootstrap confidence intervals.
  *Statistical Science* 11(3):189–228. https://doi.org/10.1214/ss/1032280214
- [S26] Cameron, A. C., & Miller, D. L. (2015). A practitioner's guide to cluster-robust inference.
  *Journal of Human Resources* 50(2):317–372. https://doi.org/10.3368/jhr.50.2.317
- [S27] Cameron, A. C., Gelbach, J. B., & Miller, D. L. (2008). Bootstrap-based improvements for
  inference with clustered errors. *Review of Economics and Statistics* 90(3):414–427.
  https://doi.org/10.1162/rest.90.3.414
- [S28] U.S. FDA (2016). Non-Inferiority Clinical Trials to Establish Effectiveness: Guidance for
  Industry. https://www.fda.gov/regulatory-information/search-fda-guidance-documents/non-inferiority-clinical-trials
  PDF: https://www.fda.gov/media/78504/download
- [S29] Piaggio, G., Elbourne, D. R., Altman, D. G., Pocock, S. J., & Evans, S. J. W. (2006).
  Reporting of noninferiority and equivalence randomized trials: an extension of the CONSORT
  statement. *JAMA* 295(10):1152–1160. https://doi.org/10.1001/jama.295.10.1152
- [S30] Altman, D. G., & Bland, J. M. (1995). Statistics notes: Absence of evidence is not
  evidence of absence. *BMJ* 311(7003):485. https://doi.org/10.1136/bmj.311.7003.485
- [S31] Lakens, D. (2017). Equivalence tests: A practical primer for t tests, correlations, and
  meta-analyses. *Social Psychological and Personality Science* 8(4):355–362.
  https://doi.org/10.1177/1948550617697177
- [S32] Cawley, G. C., & Talbot, N. L. C. (2010). On over-fitting in model selection and subsequent
  selection bias in performance evaluation. *JMLR* 11:2079–2107.
  https://www.jmlr.org/papers/v11/cawley10a.html
- [S33] Wasserstein, R. L., & Lazar, N. A. (2016). The ASA statement on p-values: Context, process,
  and purpose. *The American Statistician* 70(2):129–133.
  https://doi.org/10.1080/00031305.2016.1154108
- [S34] Nosek, B. A., Ebersole, C. R., DeHaven, A. C., & Mellor, D. T. (2018). The preregistration
  revolution. *PNAS* 115(11):2600–2606. https://doi.org/10.1073/pnas.1708274114
- [S35] statsmodels `multipletests` 文档（Holm/BH 实现）.
  https://www.statsmodels.org/stable/generated/statsmodels.stats.multitest.multipletests.html
- [S36] statsmodels `cochrans_q` 文档（k 臂配对二值）.
  https://www.statsmodels.org/stable/generated/statsmodels.stats.contingency_tables.cochrans_q.html
- [S37] gsDesign `sfLDOF` 文档（Lan-DeMets O'Brien-Fleming 消耗函数）.
  https://keaven.github.io/gsDesign/reference/sfLDOF.html
