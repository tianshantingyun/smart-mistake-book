# AUD-08 人工金标标注协议 V1（2026-11-03，批次 41 item 49）

> 定位：**协议落盘**，不是"人工金标已完成"的声明。本文规定如何从当前 G2 代理金标
> 升级到可复核的人工金标（目标 **G4**），以及哪些字段由机器检查、哪些只能自证。
> 本会话没有标注员参与、没有新金标产物——所有人工层结论 **UNVERIFIED**。
>
> 现行冻结产物仍是 `tools/split_golden/tables/split_golden_v1.json`（7 页 / 16 框，
> sha `f9e92c47…`），等级 **G2 且不可复算**（`GATE.md` §2.1）。本协议**不改 v1 一个字节**；
> 升级只能出新文件 `split_golden_v2.json` + 新旁车。

## 0. 结论摘要（先读）

| # | 问题 | 本协议的答案 | 机检状态 |
|---|---|---|---|
| 1 | 标注等级怎么记 | 根级结构化字段 `evidenceGradeLevel`（G0–G5）+ 该等级要求的登记块；自由文本 `evidenceGrade` 只作说明，不作等级依据 | **已机检**（`validate_split_golden.py:395-426`，C7） |
| 2 | 模型标注怎么显式 | `labelers[].kind="model"`，必填 `provider/modelId/modelVersion/promptSha256/ranAtUtc`；模型永远不得写进 `kind="human"` 条目 | **部分机检**（`:310-333`；身份真伪不机检） |
| 3 | 双人复核怎么走 | 两名人工**独立**标注（互不可见）→ 一致性报告 → 分歧清单 → 第三人裁定 → 冻结 v2 → 验收集只跑一次 | 流程本身不机检；产物字段由 C7 检查 |
| 4 | 目标等级 | **G4**（≥2 名人工独立标注 + 分歧裁定 + 一致性）；G3 只是单人核验，不称"人工双标" | C7 检查字段，不检查"是否真的独立" |
| 5 | 验收集 | `split.dev` / `split.acceptance` 页 id 列表；`heldOutUnusedForTuning=true` 是**自证字段** | 字段形态机检；真伪 **UNVERIFIED** |
| 6 | 当前人工正确率 | **不存在**：无人工标注产物，v1 的"两名标注员"不可从产物复算 | **UNVERIFIED** |

## 1. 不可越过的边界

- v1 冻结字节不可改（C1 逐字节 + C2 根字段恰好 5 个、`evidenceGrade` 冻结常量，
  `validate_split_golden.py:88,152-155,79`）。"给 v1 加 `labelers` 提升等级"会被 C2 判红。
- v2 是新文件 + 新旁车；`schemaVersion=2` 的根字段必含
  `schemaVersion/generatedAt/sampleRule/evidenceGrade/evidenceGradeLevel/labelingProtocolVersion/guidelineVersion/pages`
  （`validate_split_golden.py:93-95`），可选登记块 `labelers/adjudication/agreement/review/split`
  （`:96-97`）；页级额外允许 `strata/labelers/adjudication/review/reviewedBy/evidenceGradeLevel`
  （`:98-99`）。
- 等级**不改变门值**：①≥0.9 且 ③≤0.05 仍是"立项自定"（`GATE.md` §0）；升级金标不能顺手改阈值。
- **红可下传，绿不可上推**：任何等级的 FAIL 都阻断；G2 的 PASS 不等于 G3/G4 的 PASS
  （`GATE.md` §2.1）。

## 2. 标注等级字段（结构化，机检）

### 2.1 等级与必填块

| 等级 | 定义 | 根级必填登记块 | C7 入口 |
|---|---|---|---|
| G0 | 未标注 | — | `:397-398` |
| G1 | 单次模型标注 | `labelers[≥1]`（`kind=model`） | `:400-401` |
| G2 | 两次独立模型标注一致 | `labelers[≥2]`（不同 `modelId/modelVersion/promptSha256`）+ `agreement{metric,value,subset}` | `:402-415` |
| G3 | 一名人工 100% 逐条核过 | `review{humanId,guidelineVersion,reviewedAtUtc,changes[]}` + 每页 `reviewedBy` | `:416-417` |
| **G4（本协议目标）** | ≥2 名人工独立标注 + 分歧裁定 | G3 全部 + `labelers[≥2]`（`kind=human`）+ `adjudication{method,adjudicatorId,role,guidelineVersion,decidedAtUtc}` + `agreement` | `:418-423` |
| G5 | G4 + 专家裁定 + 指南冻结 + 训练/开发/验收切分 | G4 全部 + `split{dev,acceptance,heldOutUnusedForTuning}` | `:424-425` |

### 2.2 字段级要求

| 字段 | 取值/形态 | C7 实际检查 | 协议附加要求（部分未机检） |
|---|---|---|---|
| `evidenceGradeLevel` | `G0`–`G5` | 枚举（`:438-440`） | 必须与登记块匹配；不得只改字符串 |
| `labelers[].kind` | `model` / `human` | 参与计数与必填字段分派（`:321-332`） | 同一实体不得两种 kind 并存；模型不得填 `kind=human` |
| model 条目 | `provider/modelId/modelVersion/promptSha256/ranAtUtc` | 五字段非空（`:327,329-332`） | `promptSha256` 必须是实际提示词字节的 sha256；`ranAtUtc` 是运行时刻，不是复核时刻 |
| human 条目 | `agentId/role` | 两字段非空（`:328`） | `agentId` 是真实标注员 id；`role` ∈ {labeler, adjudicator}；与 `review.humanId`/`adjudication.adjudicatorId` 可互查 |
| `agreement` | `metric/subset` 非空、`value` ∈ [0,1] | 形态（`:336-346`） | 必须同时报原始一致率与机会校正统计量；框类另报逐题 IoU≥0.5 与逐页题数一致 |
| `review.changes` | 数组 | 类型（`:360-361`） | 每条记 `{pageId,boxIndex,field,before,after,reason,changedBy,changedAtUtc}`；空数组表示"核过且未改" |
| `adjudication` | 五字段非空 | 形态（`:369-377`） | 裁定人不得是两名标注员之一；`method` 写分歧消解规则，不写"讨论后决定" |
| `split.dev/acceptance` | 非空页 id 数组 | 形态（`:380-392`） | 两集合不相交；acceptance 在门值冻结前不可见 |
| `split.heldOutUnusedForTuning` | `true` | 必须为 `true`（`:389-391`） | **自证字段**：真伪不可机检，记录中单列 UNVERIFIED |
| `labelingProtocolVersion` | 非空字符串 | 根级必填（`:93-95`） | 本协议取值 `aud08-human-gold-v1` |
| `guidelineVersion` | 非空字符串 | 根级必填（`:93-95`） | 指南每次改边界规则必须升版并重标受影响页 |

### 2.3 机检能证明什么、不能证明什么

- 能证明：字段存在、非空、枚举合法、G2 的两组模型标识确实不同（`:407-413`）、
  每页有 `reviewedBy`（`:362-365`）。
- **不能证明**：两名标注员是否真的互不可见、`agentId` 是否真人、模型标注是否被人工
  改过而未记 `changes`、`heldOutUnusedForTuning` 是否为真、验收集是否参与过调参。
  这些在证据文件中必须写 **UNVERIFIED**，不得因为 C7 绿就当作已核实。

## 3. 模型标注显式规则

1. **kind 如实**：模型产出只能记 `kind="model"`；把模型标注写成 `kind="human"` 属等级造假，
   不是格式问题。
2. **身份五元组**：`provider/modelId/modelVersion/promptSha256/ranAtUtc` 缺一不可；
   `promptSha256` 是提示词模板**字节**摘要（含版本头），不是模型名或自然语言描述。
3. **独立性**：G2 要求至少两组的 `(modelId,modelVersion,promptSha256)` 不同
   （C7 `:407-413`）；"同一模型重跑两次"不构成独立标注，仍停在 G1/G2 语义边界内。
4. **模型 + 人工**：模型预标注后人工逐条核过，等级是 **G3**（100% 人工复核）或 **G4**
   （≥2 人独立 + 裁定），且模型条目必须保留在 `labelers[]` 里；不得删除模型来源后
   把产物写成纯人工。
5. **人工改动留痕**：人工改框必须进 `review.changes[]` 或页级 `adjudication`；
   无痕改动在下次判门时无法与"模型原始输出"区分。
6. **门裁定里的机器身份**：`rulings.json` 的 `decidedBy.kind` 只允许 `automated`，
   `agentId` 以 `script:` 开头（`tools/split_golden/rulings.py:112-119`）；不得填人名。
7. **措辞**：`evidenceGrade` 自由文本涉及模型时必须含 `model-labeled`；不得把 G2 写成
   "人工金标"。等级以 `evidenceGradeLevel` 为准。
8. **不追溯 v1**：v1 的 `evidenceGrade` 字符串冻结（`validate_split_golden.py:79`），
   其"两名标注员"不可复算——不得补写 `labelers[]` 到 v1 来"修复"它。

## 4. 双人复核流程（G4）

角色：标注员 A、标注员 B、裁定员 C。C 不得是 A/B；A/B 在标注阶段互不可见，
也不得看到对方或模型的中间结果。

| 步 | 动作 | 产物 | 失败即停的条件 |
|---|---|---|---|
| S0 | **指南冻结**：`guidelineVersion` + 八项属性（科目/题型/公式图/手写/跨页/题块边界/完整选项/附图归属）+ 3 正例 3 反例边界锚 | 指南文件 + 版本号 | 指南未冻结就开标 → 后续一致性无意义 |
| S1 | **抽样与切分**：记录入集准则；页 id 列表分 `dev` / `acceptance`；acceptance 封存 | `sampleRule` + `split` | 24→7 这类入集准则再次缺失 → 样本偏差不可评估 |
| S2 | **独立标注**：A、B 各自按页标框；页顺序随机；不沟通、不看模型输出 | 两份独立标注记录 | 任一方看到对方结果 → 降级，不能记 G4 |
| S3 | **一致性报告**：原始一致率 + 机会校正统计量；逐页题数完全一致率；逐题 IoU≥0.5 配对率 | `agreement{metric,value,subset,chanceCorrected}` | 只报一个总体数字、不报子集 → 不可复核 |
| S4 | **分歧清单**：逐页/逐框列出 A≠B 的项，不合并、不静默改 | 分歧清单（附页 id/框 index） | 直接改到一致而无记录 → 视为无裁定 |
| S5 | **裁定**：C 按指南裁决；记录方法、身份、角色、指南版本、时刻、改动 | `adjudication` + `changes[]` | 裁定人=标注员之一 → 不构成独立裁定 |
| S6 | **冻结 v2**：canonical 序列化 + 新旁车；跑 C1–C7；负控（改一处数字/等级/摘要必红） | `split_golden_v2.json` + `.sha256` | 任何 C 红 → 不落盘、不升级等级 |
| S7 | **验收集单次运行**：冻结后才跑 acceptance；结果原样记录 | 门报告 + 证据 | 看到结果后改标注/阈值/提示词 → 该 acceptance 作废 |

补充：

- **降级触发器**（任一命中降一级）：标注员身份不可考；独立性不可证；指南升版后未重标；
  人工改框未记 `changes`；验收集参与过调阈值；模型条目被删除或改写为人工。
- **重标**：只能出新 v2 文件（或 v3），旧文件保留；不得在原文件上覆盖。
- **抽样偏差**：v1 的 `sampleRule` 只记 155→24，24→7 未记，且自承 24 张里仅 2 张被人眼
  打开过（`docs/research/2026-11-02-aud08-annotation.md` §1.1）。v2 必须补入集准则与分层。

## 5. 标注单元、边界与分层

沿用 `docs/research/2026-11-02-aud08-annotation.md` §3.5 的提案，逐项写进指南：

1. **标注单元**：一页 = 一个 `pageId`；框 = 题干区域。
2. **单题页规则**：`questionCount < 2` 时期望"不拆分"（与度量口径 v2 一致，
   `measure_split_accuracy.py:297-303`）。
3. **跨页题**：在哪一页记框、是否允许一题多框，必须写死并逐页标 `crossPage`。
4. **边界**：框贴题干外缘还是含页眉/页码/红笔批注；用 3 正例 + 3 反例锚定。
5. **附图归属**：图属于哪道题单独登记，不并入题干框。
6. **手写/公式**：逐页标 `hasHandwriting` / `hasFormula`。
7. **分层报告**：门值必须能按 `subject/layout/crossPage/hasFigure` 切片报；
   一个总体 PASS 不得掩盖某一层全灭。

## 6. 一致性与样本量（均为立项自定）

- 人工双标：**100% 页双标**；一致性报原始一致率 + 机会校正统计量 + 逐题 IoU≥0.5 配对率。
- 模型-人工替代性验证：**≥30 个样本、≥3 名人工**，留一法比较"模型 vs 其余人"与
  "被留出的人 vs 其余人"（沿用 `2026-11-02-aud08-annotation.md` §3.5 的提案值）。
- **不引用通用 kappa 分档当验收线**：分割/结构化标注没有统一解释尺度；验收线由本项目
  立项并留旧值对照。
- 样本量不足时：只报描述性统计，不写"通过"；门结论按 `NOT_EVALUABLE`/FAIL 处理。

## 7. 落盘与校验

| 产物 | 路径 | 规则 |
|---|---|---|
| 人工金标 v2 | `tools/split_golden/tables/split_golden_v2.json` | 新文件；`schemaVersion=2`；canonical dump（`validate_split_golden.py:106-108`） |
| 旁车 | `split_golden_v2.json.sha256` | 一行 `<64-hex>  <文件名>`；与 v1 同形 |
| 校验 | `python tools/split_golden/validate_split_golden.py --file …` | C1–C7；C2 按 `schemaVersion` 分派（v1 常量不放松） |
| 逐页门裁定 | `build/split-eval/rulings/<runId>.json` | 与标签裁定分开（`rulings.py` 头注）；append-only |
| 标注协议版本 | 根级 `labelingProtocolVersion` | 本协议 = `aud08-human-gold-v1` |

**标签裁定 ≠ 门裁定**：改金标框属标签裁定（进 v2，换文件+旁车）；"系统输出 vs 金标"
属门裁定（每次判门重算，进 `rulings.json`）。两者字段名不共用，不得互相覆盖
（`2026-11-02-aud08-annotation.md` §2.1）。

## 8. 禁止清单

1. 不得把模型标注写成 `kind="human"`，或删除模型条目后宣称纯人工。
2. 不得在 v1 上加字段/改字符串提升等级；升级只出 v2 新文件。
3. 不得用验收集（`split.acceptance`）调阈值、选提示词、改指南；冻结后只跑一次。
4. 不得在看到门值后回头改标注让门过；改框必须走新版本并记 `changes[]`。
5. 不得把 G2 的绿写成"人工金标通过"；G3 也不得写成"人工双标"。
6. 不得引用无标注员身份、无指南版本的"人工复核"。
7. 不得只报一致率而不报分歧与裁定记录（不可复核）。
8. 不得用通用 kappa 分档替代本项目立项的验收线。
9. 不得在 `rulings.json` 的 `decidedBy` 里把机器判定填成人名。

## 9. UNVERIFIED / 未执行（如实登记）

- **无标注员参与**：本协议未执行；G3/G4/G5 产物均不存在。
- v1 的"两名独立标注员"：身份、模型、提示词版本、是否互不可见——**不可从产物复算**。
- 人工金标正确率、双人一致性、裁定质量：**UNVERIFIED**。
- `heldOutUnusedForTuning=true` 的真伪：**不可机检**（C7 只检查该字段为 `true`）。
- C7 只检查字段存在与形态，不检查内容真实性；"C7 PASS"≠"人工层已核实"。
- 样本量/一致性线均为**立项自定提案**，不是行业标准，也未经本项目校准。

## 10. 来源

- 仓内：`tools/split_golden/GATE.md` §0/§2/§2.1/§5.7；
  `tools/split_golden/validate_split_golden.py:75-99,310-426`；
  `tools/split_golden/rulings.py:112-119`；
  `tools/split_golden/measure_split_accuracy.py:297-303`；
  `docs/research/2026-11-02-aud08-annotation.md` §2.1/§3.2–§3.6/§5.7；
  `docs/research/2026-10-11-new-stages-spec.md:162-166`（八项属性、训练/开发/验收切分）。
- 外部（沿用 `2026-11-02-aud08-annotation.md` §8.2 的登记，本会话未重新联网）：
  AI Singapore 的模型-人工一致性验证提案（≥30 样本/≥3 人/留一法/ω≥0.5）；
  Klie et al. 2024 的标注质量管理统计；IAA 度量文献关于 kappa 分档"无统一尺度"的结论。
  这些是协议提案的依据，不是本项目已实测的结论。
