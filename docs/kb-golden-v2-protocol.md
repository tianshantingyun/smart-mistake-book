# 金标 v2 扩集协议（规则先写死 · 冻结清单 · 重锚顺序）

> **状态（2026-09-28）**：**规则与清单已定稿；判官尚未冻结**。金标集 `golden_queries_v1.json`
> （90 条，sha `7c004b76…`）**一字未动**，仍是唯一生效的判官。本文的存在是为了让"扩集"这件事
> 有据可查地按**先写死规则 → 再生成 → 生成后冻结 → 先出基线再定线**的顺序推进——
> 而不是一边看结果一边改判官。
>
> 草稿：`build/stage7/golden-v2-draft.json`（40 条，子代理起草，**未冻结、未合入**；在 `build/` 下，
> 不入库）。启用前必须走完本文 §3 的清单。

---

## 0. 外部依据（2026 年检索评测的常规做法，来自 2026-09-28 检索）

| 项 | 外部做法 | 本仓库原状 | 本协议的动作 |
|---|---|---|---|
| 金标规模 | "labeled dataset of **100+** queries"（多篇 2026 指南口径一致） | 90 条 | 扩到 **≥120** |
| 不确定性 | **配对显著性检验**（`ir-eval` 2026：per-query 结果 + paired permutation，"no distributional assumptions, exact p-values"）；报告统计置信 | 只有点估计，无区间 | 新增 `tools/kb_coverage/retrieval_significance.py`（逐题 + 按章 cluster 两种 bootstrap） |
| 版本化 | "versioned queries, **corpus snapshots**, relevance judgments—not an uncalibrated LLM judge" | 查询已冻结（sha 旁车）；语料无快照声明 | v2 冻结时一并记语料（主包 + sidecar）sha |
| 指标次序 | "先 Recall@K 不漏证据，再用 nDCG@K / MRR 看名次" | 已是 Recall@5 + MRR | 不变 |

**一处诚实更正**：Stage-5 曾记"Qwen3-Embedding-0.6B 无端侧 int8 路线"——2026-09-28 检索到该模型的
uint8 ONNX 已公开存在（如 `electroglyph/Qwen3-Embedding-0.6B-onnx-uint8`）。**该判断不再成立**，
但结论不变：0.6B 参数是 bge-base 的 ~6×，而 base 档已按 6.9× 比值判死（`docs/kb-stage6-report-2026-09-26.md` §2）
⇒ 大档仍然关着，理由从"没有端侧路径"改成"**延迟不允许**"。

---

## 1. v2 撰写规则（写死在生成之前）

**目标规模**：**130 条** = 现有 90 + 新增 40。
（下限 120；取 40 是为了同时满足"新增章"与"弱章加密"两个目的。）

**A 组 30 条 = 10 个新章 × 3 条**
- 新章不得与现有 10 章重复；现有 10 章见 `golden_queries_v1.json` 的 `chapter` 字段。
- 每科的**章数配额**：数学 3 / 物理 3 / 化学 2 / 生物 2。生物目前只有 1 章 ⇒ 优先补；
  新章要落在不同册，避免都挤在必修一。

**B 组 10 条 = 4 个弱章各补 2–3 条**
- 弱章（2026-09-28 真机逐章 Recall@5）：化学必修第一册·第三章·铁与金属材料 **0.4444**、
  物理必修第一册·第三章·相互作用 **0.4444**、数学必修第一册·第三章·函数的概念与性质 **0.5556**、
  数学选择性必修第二册·第四章·数列 **0.5556**。
- 补题必须定向到该章**现有 9 条未覆盖**的子主题，且优先 `kind ∈ {PROCEDURE, REASONING, EXPERIMENT}`。

**全 40 条共用硬约束**
0. **科配额**：数学 **≥12**、物理 **≥11**、化学 **≥9**、生物 **≥5**（合计 ≥37，留 3 条机动）。
   *（2026-09-28 修正：初版写的是"数学 ≥13、物理 ≥13、化学 ≥9、生物 ≥5"，它与 A 组结构
   ——10 新章 × 3 条、按科 3/3/2/2 = 数学9/物理9/化学6/生物6——**算术冲突**：A 组已定，
   B 组 10 条要同时满足 数学≥4、物理≥4、化学≥3 需 ≥11 条。规则写错了就改规则，
   不改数（冻结尚未发生，此处修正不涉及任何检索结果）。）*
1. `expectedSlug` 必须**逐字**是知识库里真实存在的 `slug`；`chapter` 必须与该 slug 真属同一章。
2. 同一 `expectedSlug` 最多用 2 次；同章 3 条的 slug 互不相同。
3. `query` 文本必须与现有 90 条**逐字不同**，且不得是对现任何一条的改写/同义替换/换数字复刻。
4. 文风照现有 90 条：学生口吻的真问题（可含误区/具体数字/情境），15–60 字；
   **不是**"什么是 X"式的知识点名改写。
5. 至少 12 条含具体数值或公式符号；至少 8 条为"为什么/能不能/对不对"式概念辨析。
6. **起草期间不得接触任何检索结果**（不跑检索、不读 MISS 账本、不读期望文件）
   ——否则就是把结果写进判官。

**判官纪律**：v2 冻结后**继续冻结**；本次是唯一一次允许换判官。任何"看结果再调题面"的动作都违规。

---

## 2. 为什么先不冻结（三条，都是本仓库实测过的坑）

1. **冻结的判官不能半开**：`GoldenRetrievalJvmTest` 会**断言** `golden_queries_v1.json` 与
   同名 `.sha256` 旁车一致（`GoldenRetrievalJvmTest.kt:54-61`）。只改 JSON 不改旁车 ⇒ 直接红；
   改了旁车而设备侧的期望文件没跟上 ⇒ 设备侧按不同题集出数（**没人会红**，因为期望文件是
   按 slug/章节比对的旧集）。**半冻结比不冻结更危险。**
2. **一条没被点名的连锁**（本文最重要的操作事实）：新查询会一路波及——
   `golden_queries` → 离线 `build/dense-model/int8-queries.npy`（新查询的参考向量）
   → 设备对拍 fixture（`androidTest/assets/dense/encoder-parity-{cases.tsv,vectors.f32,json}`：
   现有 200 条语料 + **90 条查询**行）→ `build/stage3-device-expectation.json`（设备金标测试的期望）。
   这四处是同一批 shas 互相钉住的（见 `docs/kb-stage6-report-2026-09-26.md` §4.4 的"三处自洽"表），
   必须**一趟全部重生成**。
3. **顺序有唯一解**：先出基线再定线。反过来（先定线再量）就是面向结果。

---

## 3. 冻结清单（按序执行，每步留读数）

```bash
# 1) 规则自检 + 候选批次机械校验（**只读，不改判官**）
python tools/kb_coverage/check_golden_candidate.py \
  --candidate tools/kb_coverage/candidates/golden_queries_v2_candidate_2026-09-28.json \
  --misses build/stage7/golden-fused-misses-device-2026-09-28.txt
#   断言：形状/题面长度与汉字/（subject,slug）在包内/**chapter 逐字等于所属 topic 的 slug**/
#   题面与 slug 唯一性/与 v1 的逐字重合与近似重复（difflib ≥0.80）/与 MISS 账本的泄漏相似度
#   （≥0.60 报出，需人工判断）/风格代理量（数值≥12、辨析≥8）。
```

# 2) 合入并冻结新集（**这一步才动判官**）
#    产物：tools/kb_coverage/tables/golden_queries_v2.json + golden_queries_v2.json.sha256
#    同时把 v1 原样留档（改名 v1-frozen-2026-09-25.json 或保持不动 + 记录新文件名），并在本文件记录：
#      - v1 sha 7c004b76…（90 条）、v2 sha（130 条）
#      - 语料快照 sha（主包 + sidecar index + update-manifest）
#      - 新增 40 条的来源说明（起草方式、复核人）

# 3) 参考向量重算（离线，档=随包小档）
python tools/dense_build/export_bge_int8.py --model bge-small-zh-v1.5      # 刷新 build/dense-model/*
#    → 新的 int8-queries.npy 覆盖 130 条查询（docs: 逐行与题面同序）

# 4) 设备对拍 fixture 重生成（两档 tokenizer 逐条同 id 必须复跑）
python tools/dense_build/gen_device_parity_fixture.py --model bge-small-zh-v1.5
python tools/dense_build/gen_tokenizer_fixture.py --model bge-small-zh-v1.5

# 5) 设备金标期望重生成（否则设备侧仍按旧集比对）
python tools/dense_build/stage3_expectation.py --model bge-small-zh-v1.5   # → build/stage3-device-expectation.json

# 6) **先出基线**（现役生产口径，新集上第一次出数——这就是基线，不许据此改题）
./gradlew :core:data:testDebugUnitTest --tests "*GoldenRetrievalJvmTest*"          # JVM 镜像两路
./gradlew :core:data:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=...GoldenRetrievalInstrumentedTest
#    → 记：主集 Recall@5 / MRR / 逐章 / p95 预算，并落 docs/kb-retrieval-ab-report.md（R3 缺的 A/B 报告）

# 7) **定线**（在基线之后写死；旧值并列保留，不撤）
#    - 新回归地板 = 基线 − 噪声下界（用 retrieval_significance.py 的按章 CI 下界）
#    - 新目标线：只在基线之上按 §1 的规模效应重估，不许把线降到基线正下方
#    - 预注册线 0.75/0.60 与地板 0.64/0.56 的沿革记录在本文件

# 8) 显著性 + 词面/稠密 A/B 报告落盘
python tools/kb_coverage/retrieval_significance.py \
  --golden tools/kb_coverage/tables/golden_queries_v2.json \
  --misses build/golden-fused-misses.txt --misses-b <对照路账本> --json > build/stage7/significance-v2.json
```

**回退**：v2 冻结后若发现题面质量问题，**不许改 v2**（冻结即判官）；改为记缺陷 + 起草 v3，
并在本文件登记原因。回退到 v1 只需把两处消费者指回 v1 文件名（一次性、可复核）。

---

## 4. 当前 90 条集的不确定性（工具已跑，2026-09-28）

`python tools/kb_coverage/retrieval_significance.py --misses build/stage7/golden-fused-misses-device-2026-09-28.txt`
（真机 MISS 账本 = 2026-09-28 那次 `GoldenRetrievalInstrumentedTest` 的 23 条）：

| 口径 | 值 |
|---|---|
| 主集 Recall@5（生产融合路由） | **0.7444（67/90）** |
| 95% CI（逐题 bootstrap） | **[0.6556, 0.8333]** |
| **95% CI（按章 cluster，诚实口径）** | **[0.6111, 0.8667]** |

**读法（这条纠正一个此前的说法）**：预注册线 **0.75 落在区间内** ⇒ "小档没过 0.75"这句话
在 n=90 / 10 章的整群设计下**不可分辨**：区间宽度 ±0.13 远大于 0.7444 与 0.75 的 0.0056 之差。
此前报告里"预注册线未过"的表述应读作"点估计低于线，但差异落在噪声带内"——
**扩集与显著性检验正是为了把这句话变成可判定的**（写在 §1 的规模规则里）。

---

## 5. 候选批次的复核记录（2026-09-28，机械部分已完成）

**候选**：`tools/kb_coverage/candidates/golden_queries_v2_candidate_2026-09-28.json`
（40 条 / 14 章；sha256 `a4421ff19d73469a8c3db03a101a84e183ebbc01508af61376522708ba79cf51`）。
**它不是判官**——在 §3 第 2 步之前，任何代码都不读它。

**起草方式（如实登记）**：由子代理按 §1 规则起草，起草者声明"未运行任何检索脚本、未读 MISS 账本
与 `stage3-device-expectation.json` 的内容"。**起草者的自述是二手信息**，故下列全部由
`check_golden_candidate.py` 机械重核（本会话实跑，一次通过，含一条自我修正）：

| 项 | 结果 |
|---|---|
| 形状 / 题面长度 15–60 / 含汉字 / 无换行 | 40/40 通过 |
| （subject, slug）在主包中逐字存在 | 40/40 通过 |
| **chapter 逐字等于该知识点所属 topic 的 slug** | 40/40 通过（精确断言，非模糊匹配） |
| 题面唯一性（候选内 + 与 v1 90 条逐字重合） | 0 冲突 |
| 近似重复（difflib ≥0.80 对 v1） | 0 命中 |
| **泄漏检查**（与真机 MISS 账本 23 条相似度 ≥0.60） | 0 命中 |
| slug 使用次数 ≤2 / 同章内 slug 互不相同 | 通过（最大使用次数 1） |
| 风格代理量 | 数值或公式符号 **24** 条（≥12）、辨析式 **29** 条（≥8） |
| 科分布 | 数学 13 / 物理 12 / 化学 9 / 生物 6（按修正后的 §1 约束 0 全部满足） |

**校验器自己先修了一处**：初版把 slug 当全局唯一，实跑即报"`阿伏加德罗常数` 在 PHYSICS 与
CHEMISTRY 各有一个"——这是**知识的正常重复**（节点 id 按科隔离：`kb:<pack>:<subject>:atomic:<slug>`），
不是数据缺陷；键已改为 (subject, slug)。**留档理由**：一个"看着像数据错误"的告警很容易被
后人当成真缺陷去改知识库。**候选备案时"生物章名写法"一处差异**：候选用 KB 逐字
（`生物学必修2·…`），与 v1 一致；起草者报告里"v1 写的是 `生物必修2`"的说法**不成立**（v1 用的也是
`生物学…`），其结论（用 KB 原样）正确，故不影响数据。

**尚缺（人类判断，不能机械替代）**：40 条题面的**内容质量**需你过目——机械断言只能证明
"slug 真、章归属真、不重复、不像照结果反推"，证不了"这道题是不是学生真会问的、是不是错题本场景里
会出现的那种题"。这一条过完，就可以走 §3 第 2 步开始的冻结链。
