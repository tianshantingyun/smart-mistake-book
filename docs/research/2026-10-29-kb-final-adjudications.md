# 知识库三件终裁落账（182 / 114 / 402，2026-10-29/30）

> 性质：**文书/台账终裁落账，零写包**。未改材料、侧车、包、dense、绑定、对齐表、判定源；未建点、未重绑、未 promotion。
> 委托来源：用户 2026-10-29「不需要我来复核，你直接判断」。
> 裁决人：`zcode-principal(delegated-by-user)`（主控工作流委托；用户可覆盖）。
> 本文件是三件终裁的单一跨引用入口；逐条明细在各自产物 CSV/JSON 中。

## 0. 结论

| 终裁件 | 输入 | 终态 | 计数 | 是否本批执行 |
|---|---|---|---|---:|
| 182 拆分提案 | `knowledge-production/curriculum-warning-human-review-decisions-2025-v1.json` | `FINAL_ADJUDICATED` | REJECT **50 维持**；DEFER **132 → NOT_APPLIED 132**；**ACT_NOW 0** | 否（零写包） |
| 114 重开提案 | `knowledge-production/reopen-proposals-decisions-2026-10-23.csv` | `FINAL_ADJUDICATED` | **REOPEN_NOW 68 / NOT_APPLIED 46**（BUILD 35/1、MERGE 33/3、REJECT 0/42） | 否（REOPEN_NOW 仅为批准标记，见 §3.4） |
| 402 同键分类 | `build/agent-batch18/multi-midx-classification.csv` | `FINAL_ADJUDICATED` | **KEEP_ALL 402 / DUPLICATE 0** | 否（只确认分类，不改判定源） |

产物：

- 182：`build/agent-batch25/adjudication/182-final.json`、`build/agent-batch25/adjudication/182-final-summary.md`；决策文件 `reviewState=FINAL_ADJUDICATED`（sha256 终裁前 `a2cd87dd…e0190` → 终裁后 `d6839195…40f60`）。
- 114/402：`build/agent-batch25/adjudication/114-402-final.csv`（516 行 = 114 + 402；sha256 `1ce35ca1…40b1`）、`build/agent-batch25/adjudication/114-402-final-summary.md`。
- 114/402 源与判定源均未改：`reopen-proposals-decisions-2026-10-23.csv` sha256 `ada56161…a7e`；`multi-midx-classification.csv` sha256 `8311f8cf…2a4`；`reopen-2026-10-17.jsonl.csv` sha256 `57aa7614…3cb3f`。

## 1. 委托与裁决人

- 用户原话（2026-10-29）：「不需要我来复核，你直接判断」。
- 因此三件原先标注「用户复核开放中」的 AI 预裁，转为**主控委托终裁**：`zcode-principal(delegated-by-user)`。
- 边界：委托只授权**文书终裁**，不授权写包、晋升、改判定源；任何需要改包/改判定源的动作只标记并给触发条件（零写包）。

## 2. 182 拆分提案终裁

### 2.1 规则

逐条复核既有 50 REJECT / 132 DEFER：

1. **REJECT 复核**：证据是否仍成立（委内拆分边界、`promotionAllowed=false`、已由既有材料/节点承担、0 材料/活动性要求等）→ 成立则维持，理由一行。
2. **DEFER 三条件**：
   - ① 有材料可配（判定池已清零，查提案指向的材料/绑定是否仍可用）；
   - ② 有检索收益证据（弱章诊断 0 条内容/绑定缺口）；
   - ③ 源允许晋升（`promotionAllowed`）。
   三条件不齐 → `NOT_APPLIED` + 触发条件；全齐才 `ACT_NOW`。

### 2.2 计数

| 项 | 数 |
|---|---:|
| 总提案 | 182 |
| REJECT 复核 | 50（MATH 15 / BIOLOGY 35） |
| REJECT 维持 | **50** |
| REJECT 推翻 | 0 |
| DEFER 复核 | 132（MATH 13 / BIOLOGY 119） |
| DEFER → ACT_NOW | **0** |
| DEFER → NOT_APPLIED | **132** |

DEFER 的 c1 形态：精确 0 命中 **91**、仅他节点占用/泛提 **35**、仅非本点命中 **5**、有直接相关材料 **1**（#30 传染病的预防，但 ②③ 不成立）。
c2：无检索收益证据 **132/132**；c3：`promotionAllowed=false` **25/25 模块**。

### 2.3 REJECT 证据复核读数

- 182 个 `evidencePhrase` 去空白后 **182/182** 可在来源正文定位。
- 50 条 REJECT 引用材料 contentId **23/23 在位**；引用节点 **43/43 在位**（2 条按名逐字未命中，按 slug/别名解析为 `dna的复制`、`pcr技术`）；对齐表引用 **11/11 行状态一致**。
- 30 条“0 材料/无独立落点”型：现包 64,973 条材料精确检索仍 0 命中；20 条“已由既有材料/节点承担”型：引用材料/节点均在位。
- 计数漂移已注记、不改变结论：#24 原记“统计/成对数据 24 个节点”未复现（现包名称含统计/成对数据 5 节点），但「获取数据的基本途径」boundary 明列“社会调查”；#129/#131/#132/#134 原记命中数随 v21 包漂移，同名节点均在位。

### 2.4 ACT_NOW=0 的原因

无 ACT_NOW，因此没有“本批不执行”的 ACT_NOW 条目。原因是 ③ 对 25/25 源模块均为 `promotionAllowed=false`，且 ② 弱章诊断 29 条融合 MISS 中内容/绑定缺口 **0 条**（`docs/kb-batch16-2026-10-22.md:29`；终验 B4 12 条 MISS 亦无内容缺口，`docs/research/2026-10-25-kb-final-acceptance-criteria-v1.md:28`）。判定池已清零：`docs/research/2026-10-25-kb-final-acceptance-criteria-v1.md:19`。

## 3. 114 重开提案终裁

### 3.1 规则

逐条复核既有 `BUILD 36 / MERGE 36 / REJECT 42`，按三条件落 `c1_material / c2_benefit / c3_executable`：

1. **可配料**：chunk 同时在 `build/agent-input/new_proposals_full.csv` 与 `knowledge-production/judgment-verdicts/reopen-2026-10-17.jsonl.csv` 在位；BUILD 需 `name/kind/parent/boundary` 四字段齐全，MERGE 需目标节点在位，且原裁未注明“材料不可用/残句/公式剥离/不应作材料”。
2. **收益证据**：BUILD 需 proposed name 在现包 name/alias 精确 0 命中（结构缺口）；MERGE 需目标别名或已绑材料语义命中，且原裁未注明“既有材料已逐条覆盖、重建重复”。
3. **可执行**：BUILD 需 parent 可解析到现包 topic 且名称唯一；MERGE 需目标节点可解析且语义命中。**写包授权不计入本列**，单列 `trigger`。

### 3.2 计数

| 既有裁定 | REOPEN_NOW | NOT_APPLIED | 合计 |
|---|---:|---:|---:|
| BUILD | 35 | 1 | 36 |
| MERGE | 33 | 3 | 36 |
| REJECT | 0 | 42 | 42 |
| **合计** | **68** | **46** | **114** |

三条件合计：c1 PASS 70 / FAIL 44；c2 PASS 71 / FAIL 43；c3 PASS 71 / FAIL 43。

### 3.3 逐条读数

- 114 个 chunk 全部在 `new_proposals_full.csv` 与判定源中命中（114/114）；判定源 action 均为 `SKIP`，`content` 为空、只有 `note` 原句。
- BUILD 36：spec 四字段缺 0、名称互不重复（`verify_reopen_decisions.py`：`BUILD rows missing spec fields: 0`、`BUILD distinct names: 36`）；现包 name/alias **精确命中 0**；parent 可解析 35/36。
- MERGE 36：目标节点 **36/36 在位**（`MERGE targets missing from pack: 0`）；字面别名命中 25 条，其余 11 条按别名/已绑材料语义命中。
- REJECT 42：现包 slug name/alias **精确 0 命中 42/42**；原裁均为题目派生/材料残缺/材料单薄/同批重复/非独立知识点。
- NOT_APPLIED 46 条：
  - REJECT 42 条：无可成段材料/结构收益。
  - BUILD 1 条：`万能角度尺的读数原理` 的 `parent=PHYSICS·综合·综合` 在现包无对应 topic（现包只有 `综合复习`/`跨册综合`）。
  - MERGE 3 条：`均匀球体内部与表面的重力加速度分布`（chunk 47a448da0e-055 为残句，原裁“不应作为材料”）、`三倍角公式`（chunk 2c603d11ef-029 公式剥离、写不出材料）、`强酸制弱酸原理的应用`（整段已被既有材料逐条覆盖、合并无新增收益）。
- 已注记的漂移：`曲率` BUILD 原裁写“0 命中”，v21 现包已有 1 条特例别名 `抛体轨迹最高点的曲率半径`；不覆盖一般曲率，结论不变。

### 3.4 REOPEN_NOW 为什么本批不执行

`REOPEN_NOW` 是**批准重开标记**，不是已执行：

- 零写包边界：未改判定源、未建点、未重绑、未重建包、未 promotion。
- 执行需要：判定源 `action: SKIP → MATERIAL/NEW`；BUILD 建点 / MERGE 重绑；重建包并跑门（会触碰已测检索基线）。
- 每条 `REOPEN_NOW` 的 `trigger` 已写明执行条件；本批不执行，避免在未开写包窗口时改变包/判定源。

## 4. 402 同键分类终裁

### 4.1 规则

与 `build/agent-batch18/scripts/classify_multi_midx.py:8-13` 同源，DUPLICATE 当且仅当组内存在两行在任一口径下相同：

- a) 去重载荷 `(chunk_id,node_slug,type,title,content)`；
- b) `content` 归一化（去空白/中文标点）后相同；
- c) `summary`/`boundary`/`note` 同字段归一化后相同；
- d) 同节点且 `title` 归一化后相同。

### 4.2 计数

| 项 | 读数 |
|---|---:|
| 源判定 | `reopen-2026-10-17.jsonl.csv` 31,272 行 |
| 目标组 | **402 组 / 827 行**（2 行 381 组、3 行 19 组、4 行 2 组） |
| 分类结果 | **KEEP_ALL 402 / DUPLICATE 0** |
| a–d 四口径重复组 | **0 / 0 / 0 / 0** |
| 判定源 sha256 | `57aa7614…3cb3f`，运行前后未变 |

### 4.3 复核命令与读数

```bash
python build/agent-batch18/scripts/verify_multi_midx.py
# groups=402 rows_sum=827 decisions={'KEEP_ALL': 402} payload_dup_groups=0
# source_sha256=57aa76144f5a2eed1e72b87e71d8fd6d4e24586675541652df8c744013d3cb3f
# PASS (RC=0)
```

独立复算 a–d 四口径：`dup groups by rule {}`（0 组）；产品键与源组键完全一致（`product keys match target True`）。本项只确认分类，不改判定源。

## 5. 写面与零写包

| 路径 | 动作 |
|---|---|
| `knowledge-production/curriculum-warning-human-review-decisions-2025-v1.json` | 只改顶层 `reviewState` + 逐行追加 `finalDecision`；其余字段与 git HEAD 逐字段相等 |
| `build/agent-batch25/adjudication/182-final.json` / `182-final-summary.md` | 新增（文书/证据） |
| `build/agent-batch25/adjudication/114-402-final.csv` / `114-402-final-summary.md` | 新增（文书/证据） |
| `reopen-proposals-decisions-2026-10-23.csv` | 未改（sha256 `ada56161…a7e`） |
| `multi-midx-classification.csv` | 未改（sha256 `8311f8cf…2a4`） |
| `reopen-2026-10-17.jsonl.csv` | 未改（sha256 `57aa7614…3cb3f`） |
| 包/侧车/dense/绑定/对齐表 | 未改 |

## 6. 未运行 / 限制

- 未运行 gradle、单测、模拟器/真机仪器化测试；本文件是文书终裁，不是测试绿声明。
- 未执行任何写包/晋升；182 的 ACT_NOW=0 与 114 的 68 条 REOPEN_NOW 均未产生包内效果。
- 182 的 50 REJECT 复核基于 2026-10-29 的现包实查（64,973 材料 / 3,245 节点 / 25 侧车）；本次落账只转引该读数并复核决策文件终态，未重跑全量材料检索。
- 114 的 `content` 字段在输入中为空（action=SKIP），“可配料”依据是 chunk 在位 + note 原句 + spec；实际材料化仍需改判与抽取。
- 402 的 reason 为源 CSV 原文摘要；本次只复算判定口径，不改判、不改源。

## 7. 证据与复算命令

```bash
cd "D:/smart mistake book"

# 182 终态
python - <<'PY'
import json
d=json.load(open('knowledge-production/curriculum-warning-human-review-decisions-2025-v1.json',encoding='utf-8'))
print(d['reviewState'], len(d['decisions']))
print(sum(1 for x in d['decisions'] if '维持 REJECT。' in x['finalDecision']['basis']))
print(sum(1 for x in d['decisions'] if 'NOT_APPLIED' in x['finalDecision']['basis']))
PY
# 期望：FINAL_ADJUDICATED 182 / 50 / 132

# 114 完整性
python build/agent-batch18/verify_reopen_decisions.py
# 期望：rows 114；tally REJECT 42 / MERGE 36 / BUILD 36；MERGE targets missing 0；BUILD missing spec 0；distinct 36；RC=0

# 402 分类
python build/agent-batch18/scripts/verify_multi_midx.py
# 期望：groups=402 rows_sum=827 decisions={'KEEP_ALL': 402} payload_dup_groups=0；PASS

# 终裁产物逐行计数
python - <<'PY'
import csv,collections
r=list(csv.DictReader(open('build/agent-batch25/adjudication/114-402-final.csv',encoding='utf-8-sig')))
print(len(r), collections.Counter((x['track'],x['final_decision']) for x in r))
PY
# 期望：516 Counter({('402','KEEP_ALL'):402, ('114','REOPEN_NOW'):68, ('114','NOT_APPLIED'):46})
```

## 8. 引用格式

> 「三件终裁（2026-10-29/30，主控委托 zcode-principal(delegated-by-user)，用户原话「不需要我来复核，你直接判断」）：182 = REJECT 50 维持 / DEFER 132 NOT_APPLIED（ACT_NOW 0）；114 = REOPEN_NOW 68 / NOT_APPLIED 46（BUILD 35/1、MERGE 33/3、REJECT 0/42）；402 = KEEP_ALL 402 / DUPLICATE 0。零写包：未建点、未重绑、未改判定源、未 promotion；REOPEN_NOW 仅为批准标记，执行触发见 114-402-final.csv。证据：`build/agent-batch25/adjudication/{182-final.json,114-402-final.csv,182-final-summary.md,114-402-final-summary.md}`。」
