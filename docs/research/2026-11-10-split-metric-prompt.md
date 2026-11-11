# 模型切题（AUD-08）评测口径与提示词研究（R1 · 批次 51）

| 字段 | 值 |
|---|---|
| evidence-id | `B51-R1-SPLIT-METRIC-PROMPT-01` |
| batch | 2026-11-10-batch-51 / 计划项 4（R 研究 · R1） |
| 输入 | `.zcode/plans/plan-2026-11-10-batch-51-final.md`（:13、:23、:33、:67-71）|
| 本会话实读 | `tools/split_golden/{GATE.md,measure_split_accuracy.py,check_split_gate.py,rulings.py,validate_rulings.py}`、`core/model/.../CaptureAssessmentTasks.kt`、`core/data/.../OpenAiModelTaskAdapters.kt`、`OpenAiModelProtocol.kt`、`OpenAiModelResponseParsing.kt`、`OpenAiModelResponseParsers.kt`、`core/domain/.../CaptureWorkflowRepository.kt`、`core/data/.../capture/BatchSplitRecognizer.kt`、`core/data/src/test/.../GoldenSplitEvalChannelHarness.kt`、`build/batch50-split/assemble_export.py`、`tools/tests/test_split_gate.py`、`docs/research/2026-11-09-split-cv-llm.md`、`docs/research/2026-11-03-aud08-human-gold-protocol.md`、`docs/research/2026-11-04-aud08-human-gold-sampling-plan.md`、`docs/research/evidence/2026-11-0{7-batch-48,9-batch-50}/split*/` |
| 本会话实跑 | ① 当前口径探针（`count_verdict` / `_page_metric` / `_aggregate`）；② 批次 48/50 导出在新旧口径下的复算；③ 6 条先红后绿用例（红 4 绿 2）；④ `python -m unittest discover -s tools/tests -p test_split_gate.py`（61 tests OK）；⑤ `tools/ci/check_eval_label_leakage.py --paths tools/split_golden`（exit 0）。逐条命令与输出见 §6 与 `docs/research/evidence/2026-11-10-batch-51/r1/probe-output.txt` |
| 本会话未跑 | Gradle/设备/任何 provider 调用（红线：Gradle 不得与其他 agent 并发；本会话零出网）；未改任何产品代码、金标、阈值、口径实现 |
| 裁定性质 | **研究建议**。口径 v3 与提示词草稿是给 P-G3（计划项 20–24）的实现规格，不是已生效的改动 |

> 说明：任务书提到的 `docs/research/2026-11-04-aud08-human-gold-protocol.md` **不存在**；
> 仓内实际文件是 `docs/research/2026-11-03-aud08-human-gold-protocol.md`（`Read` 返回 not found，
> 随后 `ls docs/research | grep -i aud08` 列出 `2026-11-02-aud08-annotation.md`、`2026-11-03-…protocol.md`、
> `2026-11-04-…sampling-plan.md`、`2026-11-09-split-cv-llm.md`）。本文按 11-03 协议与 11-04 抽样计划引用。

---

## 0. 结论摘要（先读）

| # | 问题 | 裁定 | 可执行改动（不是改阈值） | 对读数的实测影响 |
|---|---|---|---|---|
| a | 金标 1 题、模型返回 PASS | **PASS 记「一致」是正确语义，保留**；但「0 框」不等于 PASS——**只有被接受的判定才能记一致**（设备导出无判定字段时回落 v2 并标注，见 §2.3） | 度量层新增逐页 `pageClass`（6 类）；单题页规则从"框数=0 即一致"改为"判定被接受且为不拆分才一致" | 批次 50 总 ① **7/24 → 6/24**；acceptance 8 页 3/8 → 2/8；批次 48 **10/24 不变** |
| b | NEED_MORE_IMAGE / RECAPTURE | **不折算成 0 框**：题数记 `null`（弃答），单列 `abstain` 块；进 ① 时按 ITT 记不一致（分母不减） | 同上 + 报告新增 `abstain{count,pages,rate}`、`notEvaluable{...}` | 批次 50 弃答 2/24 = 8.3%；通道不可评 5/24 = 20.8% |
| c | 提示词模式 | 六条规则 + 三层键白名单 + 一个形状示例；**Chat Completions 只有 `json_object`（无 `json_schema`），提示词是唯一可用的键约束手段** | 改 `singlePageAssessmentPrompt`（`OpenAiModelTaskAdapters.kt:161-173`，现 567 字符），加提示词断言测试（3 条 + 1 条回归） | 本会话**未实测模型效果**（零 provider 调用）；dev 预算见 §5.3 |
| d | 不在 acceptance 上调参 | 新 v3 切片（131 张未用页中确定性抽 ≥20）+ 预注册 + 一次运行 + **机械守卫**（配置 sha 绑定、切片复用检测） | 新增 `tools/ci/check_acceptance_once.py`；`check_split_gate.py` 增可选 `--config`（不匹配 → exit 2） | 现状：同一 8 页已被批次 48、50 消费两次，无任何机器检查报红 |

三条必须先说清的判断（都会改变主会话的预期）：

1. **口径修正是「纠正假阳性」，不是「提分」。** 它把批次 50 的 ① 从 0.2917 压到 **0.2500**（§2.4）。
   计划项 20 的语义可以通过它落地，但 **①≥0.9 不会因此更近**。批次 50 的 ① 距门差 15 页，
   任何"改口径即可过门"的叙事都不成立。
2. **提示词修复（`type` 键）也不是过门证据。** 批次 50 README §2.2 已实证：4 个 `type` 键页里，
   011800（PASS）与 004952（NEED_MORE_IMAGE）修复后仍是 0 框；010306/010633 在带修复规则的 B2 臂
   分别只拿到 3 框 / 0 框（金标 4 / 3）——**均不构成"修复后就能一致"的证据**。
3. **acceptance 8 页的 ① 是 8/8 全或无**（7/8=0.875 < 0.9，`2026-11-09-split-cv-llm.md:364` 复算）。
   n=8 的切片对 0.9 门几乎没有分辨力；新切片若仍是 8 页，等于把门押在"零错"上。建议新 acceptance
   **n≥20**（18/20=0.9000 恰过；17/20=0.85 不过）——这是**样本设计**，不是改阈值（§5.2）。

---

## 1. 事实闭包（当前口径逐段，本会话实读）

### 1.1 度量与门

- ① 完整题命中 `countExactRate` = 一致页数 / **可比金标页数**；单题金标页（`questionCount<2`）
  期望"不拆分"：应用 0 区域 ⇒ 一致，>0 ⇒ 不一致（`measure_split_accuracy.py:12-18`、`:298-308`）。
- ② IoU≥0.5 题占比：只度量、不设门（`:20-23`）。
- ③ 切残 `fragmentRate` = 切残金标题数 / 金标总题数（`:24-28`）。
- 门式：①≥0.9 且 ③≤0.05，闭边界；阈值常量在 `measure_split_accuracy.py:76-79` 与
  `check_split_gate.py:91-92` 两处逐字一致；硬红线 `fragmentedQuestions == 0`（`check_split_gate.py:245-253`）。
- 分母口径的既有裁定：**应用缺页按 0 框计入、分母不减——"缺页就是漏"**（`GATE.md:57`）。
  这条是下面所有"ITT"选择的仓内依据：漏与错在 ① 里同权，不得用剔除分母来变绿。
- 统计区间只作描述（`GATE.md:60`）：本轮实算 `wilson95(22,24) = 0.7415–0.9768`、
  `wilson95(8,8) = 0.6756–1.0000`——**点估计过门不等于下界过门**；把门下界化属改阈值，禁止。

### 1.2 度量只读框数，不读判定（缺陷的根）

- 导出侧已经写全了判定：`build/batch50-split/assemble_export.py:44-56` 每页写
  `outcomeStatus`（`OK`/`ERROR`/`HTTP_ERROR`）、`decision`（`PASS`/`SPLIT`/`RECAPTURE`/`NEED_MORE_IMAGE`/`null`）、
  `questionCount`、`questions[]`；批次 48/50 的 24 页导出**都带这两个字段**（本会话读两个导出确认）。
- 度量侧把它们丢了：`PROVENANCE_KEYS` 不含 `decision`/`outcomeStatus`（`measure_split_accuracy.py:82-84`），
  `_page_metric` 的返回行也没有这两个键（`:344-373` 本会话实测行键清单见 §6.1）。
- 结果：`count_verdict(gold, app)` 只看两个整数（`:298-308`）。**0 框 = PASS = 弃答 = 通道拒收 = 漏切**，
  五件事在 ① 里无法区分。

### 1.3 产品侧其实分得清（所以这是度量缺陷，不是产品缺陷）

- `BatchSplitRecognizer.kt:79-88`：任务失败返回 `BatchSplitOutcome.Failed`；判定成功但区域不可用
  返回 `NotASplit`。产品面把"失败 / 不切"分开，只有度量面把它们合并。
- 区域可用的判据（消费侧唯一事实源，`CaptureWorkflowRepository.kt:439-479`）：
  数量 2..12、去重后同数、每框 `宽≥0.08`、`高≥0.04`、`面积≥0.006`（归一化坐标）、
  两两重叠 / 较小框面积 ≤ 0.8。
- 单题页结构上不可能产生合法拆分（最小 2 块），所以"单题页期望不拆分"只能由**判定**表达，
  不能由框数表达（`GATE.md:57` 口径 v2 的由来）。

### 1.4 契约（决定"整轮拒收"的边界）

- 顶层键白名单：`decision/issues/suggestedActions/questionRegions/followingPageRelations`
  （`OpenAiModelResponseParsing.kt:127-128`），多一个键即 `requireOnlyKeys` 抛
  `InvalidModelResponseException`，整页 0 框（`OpenAiModelResponseParsers.kt:121-153`）。
- 嵌套也有白名单：issue 只允许 `code/severity/region/message`（`:129`），region 只允许
  `left/top/right/bottom`（`:113-126`）。
- `CaptureAssessment` 不变量（`CaptureAssessmentTasks.kt:87-152`）：PASS 不得含 BLOCKING issue、
  不得建议 RECAPTURE/ADD_IMAGE（`:90-98`）；RECAPTURE 必须带 RECAPTURE（`:99-102`）；
  NEED_MORE_IMAGE 必须带 ADD_IMAGE（`:103-106`）；只有 SPLIT 能带 `questionRegions`（`:138-140`）；
  SPLIT 必须带 MULTIPLE_QUESTIONS（`:130-132`）。

### 1.5 提示词现状（实测）

- 单页题图提示词 = 567 字符，**不含** `json` 字样、**不含** 顶层键白名单、**不含** 示例
  （本会话从 `OpenAiModelTaskAdapters.kt:161-173` 的字面量拼出并计数，见 §6.1）。
- 该 567 字符文本的 sha256 = `31aef2ab0f07b8d3e3337d4f64009e9bf3bdd08d1050d3e784990ac8a9bb496d`，
  **与批次 50 acceptance 协议冻结的生产提示词 sha 逐字节一致**
  （`evidence/2026-11-09-batch-50/split-gate/acceptance-protocol.md:14`）。⇒ 拼字符的还原方法可信，
  且 batch 50 之后提示词未被改动。
- 请求体：`temperature 0.1`（`OpenAiModelProtocol.kt:192`）、`response_format={"type":"json_object"}`
  （`:198`）、`detail: "high"`（`:181`）、system 消息含"必须只返回符合要求的JSON"（`:912-914`）。

### 1.6 provider 约束（外部一手，决定了 c 的可行域）

- Chat Completions 的 `response_format.type` **只有 `text` 与 `json_object`** 两个取值；
  `json_schema` 不存在于该端点（DeepSeek API Reference「Chat Completions API」`response_format` 段）【V】。
  ⇒ **不能用 strict schema 从结构上禁止多键**；提示词 + 本地白名单解析器是唯一组合。
- JSON Output 的三条要求：`response_format={'type':'json_object'}`、**提示词里出现 "json" 并给出
  期望格式的示例**、合理设置 `max_tokens`；且"若模型输出无法被识别为 JSON，API 用空格（U+0020）
  替换非 JSON 内容"（DeepSeek「JSON Output」Notice）【V】。当前提示词只有 system 消息满足"出现 json"，
  **示例一条缺失**——这与实测的 `{"type":"json_object",…}` 首键回显吻合【P】。
- 视觉：`deepseek-flash` 支持图片；`detail` 取值 `low`（512×512 降采样）/`high`/`original`/`auto`；
  **推理前每张图都会被重采样到总像素约 1300×1300，单图上限 1024 tokens**（DeepSeek「Vision」Token Usage）【V】。
  ⇒ 出网副本的 2048 长边不是瓶颈，**provider 侧还有一次重采样**；一页 6–7 题的密排页在 1300² 下
  每题只剩很小像素预算，这是"题数少一两个"的结构性来源【P】。
- 思考模式默认开启（`thinking` 缺省时按 `reasoning_effort`，再缺省即启用），
  **思考模式下 `temperature` 无效**（API Reference `temperature` 段）【V】。
  ⇒ 评测的"temperature 0.1 = 低随机"在本端点不成立；单轮 ±2 页的波动不能靠温度压住（§5.5）。

### 1.7 历史读数（引用时必须带口径）

| 批次 | ① | ③ | 硬红线 | 来源 |
|---|---|---|---|---|
| 48（v2，24 页） | 10/24 = 0.4167 | 3/78 = 0.0385 | 3 | `evidence/2026-11-07-batch-48/splitter/gate-evidence-v2-01.md:32-36` |
| 50（v2，24 页） | 7/24 = 0.2917 | 1/78 = 0.0128 | 1 | `evidence/2026-11-09-batch-50/split-gate/README.md:12-14` |
| 50 acceptance 8 页 | 3/8 | 1/26 | 1 | 同上 `:31` |

---

## 2. （a）金标 1 题、模型返回 PASS 怎么计分

### 2.1 现状的正确部分

单题页"不拆分 = 期望"是**已裁定的语义**，不是漏洞：`measure_split_accuracy.py:298-308` 与
`GATE.md:57` 一致，`2026-11-03-aud08-human-gold-protocol.md:121-122` 也把这条写进标注指南。
PASS 是模型对"整页一题、无需切分"的**正确回答**，必须记满分——任务书问的"如何不惩罚正确行为"，
第一半答案就是：**保持这条规则不动**。

### 2.2 现状的错误部分（本会话实证）

`count_verdict` 只看整数：`count_verdict(1, 0) -> (True, '一致（单题页：不拆分=期望）')`，
与导出里的 `decision`/`outcomeStatus` 完全无关（§6.1 探针 1）。于是：

- 模型返回 `NEED_MORE_IMAGE`（弃答）→ 0 框 → **记一致**；
- 响应被契约整轮拒收（`type` 键）→ 0 框 → **记一致**；
- 导出里该页根本缺页 → 也是 0 框 → **记一致**（`_page_metric` 的 `appPageMissing` 没进 ①）。

批次 50 的 `IMG20260810011800.jpg` 就是活例：acceptance 臂记录
`status=ERROR / InvalidModelResponseException`，原始回包 `{"type":"json_object","decision":"PASS",…}`
被 `requireOnlyKeys` 拒收（本会话从 `build/batch50-split/runs/acceptance-b50-01/IMG20260810011800.json`
实读 `responseHead`）。它被记成一致，贡献了 7/24 里的 1 页。**产品在这一页什么都没给用户**，
门却给了分——这是假阳性，不是"不惩罚正确行为"。

### 2.3 口径 v3（建议实现）

逐页先分类，再判一致。分类输入 = `gold.questionCount`、`app.questions.length`、
`app.outcomeStatus`、`app.decision`：

| pageClass | 条件 | 题数（度量用） | ①(v3) 判定 |
|---|---|---|---|
| `ACCEPTED_SPLIT` | `OK` + `SPLIT` + 区域可用 | `len(regions)` | `== gold` ⇒ 一致 |
| `SPLIT_UNUSABLE` | `OK` + `SPLIT` + 区域不可用（含 0 块） | `0`（产品没切） | 不一致（gold≥2） |
| `ACCEPTED_NO_SPLIT` | `OK` + `PASS`（regions 必空） | `"none"`（不拆分） | `gold<2` ⇒ 一致；`gold≥2` ⇒ 不一致 |
| `ABSTAIN` | `OK` + `RECAPTURE`/`NEED_MORE_IMAGE` | `null` | **一律不一致**，并单列 |
| `NOT_EVALUABLE_CHANNEL` | `status != OK` 或无 `decision`（HTTP 错、契约拒收） | `null` | **一律不一致**，并单列 |
| `MISSING_APP` | 导出里无该页 | `null` | 不一致（`GATE.md:57` 缺页就是漏） |

规则三句话：

1. **单题页（gold<2）只有 `ACCEPTED_NO_SPLIT` 记一致**；PASS 拿满分，弃答/拒收/缺页不拿分。
2. **多题页（gold≥2）继续按题数相等判**（口径不变）；`ACCEPTED_NO_SPLIT` 等于"模型把 N 题读成 1 题"，
   记不一致（批次 50 的 `011126` 即此形态：金标 2 题、PASS）。
3. **分母恒为可比金标页数**（v2 即 24），不因任何类剔除——与 `GATE.md:57` 同一条纪律。

`decision` 字段缺失时（历史设备导出，`export_split_eval.py` 的 SELECT 不含 decision）：
**回落 v2 规则**（单题页 0 框仍记一致），同时把该页标 `singleQuestionUnverified: true`、报告计
`singleQuestionUnverifiedCount`；**该计数 > 0 时门报告只能写 `PASS(v2-fallback)`，不得声称 v3 严格口径**。
理由：严格规则需要判定证据，设备导出给不出；把"未知"当"不一致"会把历史测试钉住的 4/7 改写成 2/7
（`tools/tests/test_split_gate.py:1018-1039` 正在断言 4/7）——那是用口径改写历史读数，不是纠错。
旧值对照仍由 `countExactV2` 保留（`GATE.md:236` "改口径须先裁定并留旧值对照"）。

### 2.4 对既有数据的重算（本会话实跑，原样输出见 `evidence/…/r1/probe-output.txt` §3）

| 数据 | 旧口径 ① | v3 ① | 差异来源 |
|---|---|---|---|
| 批次 48（24 页） | 10/24 = 0.4167 | **10/24 = 0.4167** | 两页单题页都是被接受的 PASS，不误伤 |
| 批次 50（24 页） | 7/24 = 0.2917 | **6/24 = 0.2500** | 仅 `011800`（ERROR）由假阳性转不一致 |
| 批次 50 acceptance（8 页） | 3/8 = 0.375 | **2/8 = 0.250** | 同上 |

⇒ 口径修正**不是普遍降分**，只在"通道真失败却被记分"时纠正。这是它值得做的原因，也是它
**不足以**把门推到 0.9 的证据（批次 50 差 15 页）。

### 2.5 为什么不选另外两条路

| 方案 | 算术 | 否决理由 |
|---|---|---|
| 把单题页从 ① 分母剔除 | 20/22 = 0.9091 与 22/24 = 0.9167 几乎同义 | **改分母**，`GATE.md:236-237` 禁止；收益近零，治理成本高 |
| ① 改成题级 precision/recall | 新分母、新阈值语义 | ② 已在度量框质量；换 ① 等于换门，须先裁 0.9 的适用对象 |
| 用 Wilson 下界判门 | n=24 时 22/24 的下界 0.7415 | 门槛值化（要求 n≈90+ 才可达），同属改门 |

---

## 3. （b）NEED_MORE_IMAGE / RECAPTURE 在题数口径下如何折算

### 3.1 不折算：题数记 `null`，不是 `0`

`0` 是一个**判定结果**（"整页不切"），弃答是**没有判定**。两者在导出里已有区分
（`decision` 字段），度量层不得把它们压成同一个整数。具体：

- `questionCountForMetric = null`，`pageClass = ABSTAIN`；
- 框集仍如实记 `[]`（几何真相，供 ②/③ 用）；
- 逐页裁定 `countVerdict` 保持两值枚举（`validate_rulings.py:161`），新增 `appDecision`、`pageClass`
  两个**加法字段**（`validate_rulings.py` 不校验精确键集，本会话确认无 `requireOnlyKeys` 式检查）；
- 报告新增块：`abstain{count, pages[], rate}`、`notEvaluable{count, pages[], rate}`。

### 3.2 计分：进 ① 记不一致（ITT），单列率（不设阈值）

理由三条，都能指到具体失败：

1. 金标样本是**条件样本**：24 页每页都至少有一道完整可见题（v2 `questionCount` 最小 1，
   分布 1×2/2×5/3×9/4×4/5×2/6×1/7×1），所以"这页要重拍"在该样本里 **按定义就是错判**。
   真需要重拍的页不会进金标，这与 `GATE.md:57` "缺页就是漏"同构。
2. 若把弃答排除出分母，等于奖励"多弃答"：极端模型全弃答 → ① = 0/0 或 1.0。
   这与"切残硬红线"同类的反作弊直觉冲突。
3. 弃答**不会**污染 ③：没有框就不会产生碎块，③ 分子分母都不动（批次 50 的 2 页弃答
   在 ③ 里贡献 0/78）。所以弃答只在 ① 上体现，语义单一。

**不新增阈值**：`abstentionRate` 只报数（批次 50 = 2/24 = 0.0833），不设上限门。
理由：新增数值门要立项，且会与既有 ①≥0.9 重复计分（弃答已经压 ①）。

### 3.3 边界情形

- `RECAPTURE` 与 `NEED_MORE_IMAGE` 同类处理（都无区域）。二者的区别只在 `suggestedActions`
  与文案，对 ① 无差别。
- `SPLIT` 但区域不可用（批次 50 `005144`：5 个互相重叠的区域含整页框）→ **不是弃答**，是
  `SPLIT_UNUSABLE`，记不一致；它证明"模型想切但产物被契约拒绝"，与弃答是两种失败原因。
- 弃答的逐页证据必须能指出**模型说缺什么**：`issues[].code/region/message` 是契约字段，
  但导出只透传了 `decision`/`outcomeStatus`（`assemble_export.py:44-56`），未带 `issueCodes`；
  harness 记录里已有该字段（`GoldenSplitEvalChannelHarness.kt:213-218`），建议一并透传。
- 批次 50 的 `004854` 另有已知金标坐标系缺陷（研究 §3.6 登记、`README.md` §6 第 4 条），该页弃答
  **不得**被叙述成"模型错"或"模型对"；报告里单列 `goldFrameSuspect` 标记，分母不动。

---

## 4. （c）提示词模式：题号枚举 / 边缘裁切 / 多栏 / 整页框 / 顶层键白名单

### 4.1 先定约束（决定只能怎么写）

| 约束 | 事实 | 对提示词的含义 |
|---|---|---|
| 键约束 | Chat Completions 无 `json_schema`（§1.6） | 只能靠提示词写死"顶层恰好五个键"+ 本地 `requireOnlyKeys` 兜底 |
| JSON 模式 | 官方要求"出现 json + 给示例" | **当前缺示例**（§1.5），补一个形状示例是文档级要求，不是风格偏好 |
| 图像 | provider 重采样到 ~1300²（§1.6） | 密排页信息受限；提示词不得要求"逐字读全"，只要求"按可见题号计数" |
| 区域契约 | 2..12、宽≥0.08、高≥0.04、面积≥0.006、重叠≤0.8×较小框 | 提示词必须把这些数字写出来，否则模型持续产出被 `isUsableSplitRegionSet` 整组拒绝的框 |
| 采样 | 思考模式默认开、temperature 无效 | 提示词改动必须靠多次 dev 重复验证，不能靠"温度低就稳定" |

### 4.2 五种模式的写法（每条都指向一次真实失败）

| 模式 | 规则文本（要点） | 它消灭的失败（证据） |
|---|---|---|
| **题号枚举** | "先按阅读顺序数可见题号（`1.` `2.` `(1)(2)` `①②`），`questionRegions` 项数必须等于题号数；同一题的多个小问、共用材料的多问算一道" | 批次 50 的 9 页"题数差 ±1–2"（`README.md:74-75`）：004511/005301/005711/010202 3→2、010034 6→5、011358 5→4、011248 5→4、010859 4→3、011649 3→2 |
| **边缘裁切** | "题号与题干主体可见即视为完整题，即使页面边缘被裁也照常成框；只有题干主体或关键条件确在画面外才 `NEED_MORE_IMAGE`，并在 `issues.region` 标缺失位置" | 批次 50 两页弃答（`004854`/`005605`）而金标判为完整题（`README.md:68`）；边缘裁切是拍照常态 |
| **多栏** | "先左栏自上而下，再右栏；框不得跨分栏线；题干与选项分处两栏时取覆盖该题全部可见内容的最小外接矩形；**不得把一题拆成两块**，也不得把两栏的两道题并进一个框" | 批次 50 唯一硬红线 1 题切残（`004353`，`README.md:65`）；acceptance 切片 0 张双栏是已知盲区（`README.md` §4.3） |
| **整页框** | "不得返回覆盖整页或包含多题的框；区域两两重叠不得超过较小框面积的 80%" | `005144` 返回 5 个互相重叠的区域（含整页框）→ `isUsableSplitRegionSet` 判不可用 → 0 框（`README.md:71`）；HiLEx 的错误分析也把"同一答案块周围多个框"列为主要过检形态【V】 |
| **顶层键白名单** | "顶层恰好五个键 `decision/issues/suggestedActions/questionRegions/followingPageRelations`；不得返回 `type`、`schema`、`version` 或任何其他键（把请求的 `response_format` 抄进结果会被整轮丢弃）"；再加 issue 四键、region 四键两级 | 批次 50 的 4 页 `type` 键整轮拒收（`README.md:44-47`：010306/010633/004952/011800）；嵌套白名单同样会拒（`OpenAiModelResponseParsing.kt:113-130`） |

补充两条**契约自洽**规则（同样来自整轮拒收的真实失败）：

- "`RECAPTURE` 必须含 `RECAPTURE`；`NEED_MORE_IMAGE` 必须含 `ADD_IMAGE`；`PASS` 不得含 BLOCKING issue、
  不得建议 `RECAPTURE`/`ADD_IMAGE`" → 消灭 `011017`（NEED_MORE_IMAGE 未配 ADD_IMAGE，`README.md:69`）。
- "整页只有一道题时返回 `PASS` + 空 `questionRegions`，不要为凑数把一题拆两块" → 把 (a) 的语义
  直接写进提示词，减少"单题页硬凑 2 块"的过切。

外部依据（只作方法参考，**不是**要把 CV 装回来）：
- OpenAI 文档建议定位类任务用"固定坐标空间 + 明确 box 格式（`[x_min,y_min,x_max,y_max]`，0..999，
  左上原点）"，密排/手写页用最高图像细节，必要时"先定位→裁剪→对裁剪块再跑一次"【V】。
  我们用的是 0..1 归一化；**等价做法是要求 3 位小数（≈1/1000 网格）**，不改 wire。
- LocateBench（arXiv 2410.19808）：最强模型 GPT-4o 的定位准确率仍落后人类 >10%【V】。
  ⇒ ② 只能作诊断，不得当门；提示词不承诺逐像素精度。
- HiLEx（ICDAR 2025，Springer pp.485-505）：首个题卷版面基准，1,965 页、6 类层级标注、
  专家标注 κ>0.90；零样本 VLM（PaLI-Gemma2）mAP@50 83.5，微调检测器 94.0；难点集中在
  小目标（Instruction/Description），主要错误含"同一块多个框"【V】。
  ⇒ "大区域（整页/题+选项块）容易、小结构难"与我们的失败面一致；**该数据集是 CV 标注基准，
  引用它只为评测口径，不构成恢复 CV 切题的依据**（用户裁定：切题只走模型识别）。

### 4.3 提示词草稿（可直接落地的整段，替换 `OpenAiModelTaskAdapters.kt:161-173`）

```text
判断这张题图是否足以完整转写、画面里有几道互相独立的题；若足以转写且有多道题，给出每道题的区域。
只返回一个JSON对象，顶层恰好五个键：decision、issues、suggestedActions、questionRegions、
followingPageRelations；不得返回type、schema、version或任何其他键（把请求的response_format抄进
结果会被整轮丢弃）。
decision只取PASS/RECAPTURE/NEED_MORE_IMAGE/SPLIT；issues每项只含code、severity、region、message，
code只取MISSING_OPTIONS/KEY_TEXT_UNREADABLE/GLARE_COVERS_FORMULA/OCCLUDED/MULTIPLE_QUESTIONS，
severity只取BLOCKING或REVIEW，message为简短中文；suggestedActions只取RECAPTURE/ADD_IMAGE/
CONTINUE_ANYWAY；region只含left、top、right、bottom四个0到1的数，左上为原点、y轴向下，保留3位小数。
示例（只示形状）：{"decision":"SPLIT","issues":[{"code":"MULTIPLE_QUESTIONS","severity":"BLOCKING",
"region":{"left":0.040,"top":0.080,"right":0.960,"bottom":0.360},"message":"本页有2道独立题"}],
"suggestedActions":[],"questionRegions":[{"left":0.040,"top":0.080,"right":0.960,"bottom":0.360},
{"left":0.040,"top":0.380,"right":0.960,"bottom":0.660}],"followingPageRelations":[]}
先按阅读顺序数可见题号（1. 2. 3. 或 (1)(2) 或 ①②），questionRegions的项数必须等于题号数；
同一题的多个小问、共用材料的多问算一道题。
双栏页面先左栏自上而下、再右栏；框不得跨过分栏线；题干与选项分处两栏时，取覆盖该题全部可见
内容的最小外接矩形；不得把一道题拆成两块，也不得把两栏的两道题并进一个框。
框贴可见内容外缘，含题干、选项、图形与作答区；不含页眉、页码、装订线与红笔批注；
不得返回覆盖整页或包含多道题的框；区域两两重叠不得超过较小框面积的80%。
题号与题干主体可见即视为完整题，页面边缘被裁切也照常成框；只有题干主体或关键条件确在画面外
时才返回NEED_MORE_IMAGE，并在issues的region标出缺失位置。
decision与区域：只有SPLIT带questionRegions；PASS、RECAPTURE、NEED_MORE_IMAGE必须返回空数组。
整页只有一道题时返回PASS与空数组，不要为凑数把一题拆成两块。
2到12道独立题必须返回SPLIT，并把MULTIPLE_QUESTIONS标为BLOCKING；超过12道时不要合并题目，
仍按题给框（本地会拒绝采用，不猜）。
suggestedActions与decision必须自洽：RECAPTURE必须含RECAPTURE；NEED_MORE_IMAGE必须含ADD_IMAGE；
PASS不得含BLOCKING级issues，也不得建议RECAPTURE或ADD_IMAGE。
同一道题跨页不算多题，内容未拍全时返回NEED_MORE_IMAGE。
followingPageRelations必须返回空数组。
```

预算：上段 **1,594 字符**（不含换行；含换行 1,622；本会话从本文代码块实数字符），
替换原 567 字符后净增约 1.0k 字符（按字符计，**未做 provider 侧 token 计量**）。
**提示词文本一变，`promptSha256` 就变**——它必须进预注册（§5.3），否则 acceptance 的"同配置"不可核。
"超过 12 题"分支**无金标覆盖**（v2 最大 7 题），属未验证分支；不要把它写成已知可用的能力。

### 4.4 提示词**不能**解决的部分（如实）

1. **坐标帧**：`004854` 的金标框在旋转 90° 系里（研究 §3.6 已实证）。这是金标缺陷，
   提示词无法修；只能修金标（v3）或在报告里单列，不得从分母剔除。
2. **provider 重采样**：~1300² 上限下，6–7 题密排页的题号可能已不可辨；这是能力上限，
   不是提示词缺陷。若要突破，需"裁剪分块多次调用"这类**新管线**（外部文档的 crop-and-rerun），
   属产品改动、需另立裁定，不在本批。
3. **结构性保证**：提示词是概率约束；只有本地 `requireOnlyKeys` 是硬约束。两者不可互换——
   不得因为提示词写了白名单就放宽解析器（否则"type 键缺陷"变成"静默接受任意键"）。

---

## 5. （d）如何避免在 acceptance 上调参

### 5.1 现状缺口（已发生的失败）

- v2 的 acceptance 8 页**已被消费两次**：批次 48 `acceptance-once-01`（① 5/8、③ 3/26，
  `evidence/2026-11-07-batch-48/splitter/`）与批次 50 `acceptance-b50-01`（① 3/8、③ 1/26）。
  批次 50 自己在 `README.md:79-92` 与 `acceptance-protocol.md:30-38` 如实登记了
  "held-out 属性不可恢复"，`check_split_gate.py:202-211` 的 scope 文案也已改写。
- **没有任何机器检查会因此报红**：`tools/ci/check_eval_label_leakage.py` 只做静态 AST
  （阈值是否由 protected 标签派生、是否开放阈值覆盖面），其 limitations 明写"动态路径/跨仓库/
  git 历史改值不在可见范围"。⇒ "同一 acceptance 被第二次消费"这一具体失败，目前零防护。

### 5.2 新切片 v3 的设计（样本设计，不动阈值）

| 项 | 取值 | 依据 |
|---|---|---|
| 抽样框 | 同一 155 张目录；**排除 v2 已用的 24 张** → 131 张候选 | `2026-11-04-…sampling-plan.md` §2/§3 |
| 抽样 | `seed = sha256("aud08-v3|<v2 golden sha>|<批次日>")`；对 131 张按 `sha256(seed|file)` 升序取前 N | 确定性、无 RNG（沿用 v2 规则精神） |
| 规模 | **acceptance n≥20**（建议 20）；调参集用 v2 dev 16 页（已"看过"，本来就用于调参）；若需更大 dev，再从 131 张里另取 16，**一旦取用即永久退出 acceptance 候选** | 18/20 = 0.9000 恰过、17/20 = 0.85 不过 ⇒ 允许 2 页错；n=8 时是 8/8 全或无 |
| 分层配额 | 至少 2 页"金标 1 题"、至少 2 页双栏 | (a) 规则需要单题页才能被检验；v2 acceptance 0 张双栏是已登记盲区（批次 50 `README.md` §4.3） |
| 标注 | 盲标（不看切分输出/不看提示词版本），G1 起；两名标注员 A/B 一致升 G2 | `2026-11-03-…protocol.md` §2、§3 |
| 冻结 | 新文件 `split_golden_v3.json` + 新旁车，C1–C7 | 同上 §7；**不改 v2 一个字节** |
| 门值 | 仍 0.9 / 0.05，分母 = 可比金标页数 | 红线：不改阈值 |

分层配额的可行性 **UNVERIFIED**：131 张里"单题页/双栏页"的实际数量本会话未统计（不打开图片）。
若配额不足，按"先到先得 + 在报告里登记缺口"处理，不得为了让配额成立而把 v2 已用页放回来。

### 5.3 预注册 + 一次运行 + 配置绑定

1. **预注册文件**（跑之前写、提交、算 sha）：`tools/split_golden/tables/acceptance-prereg-v3.json`
   ```json
   {"schemaVersion":1,"goldenSha256":"<v3>","pageListSha256":"<页名列表>",
    "promptSha256":"<生产提示词文本 sha>","modelId":"deepseek-flash","temperature":0.1,
    "egress":{"longEdge":2048,"jpegQuality":88,"fallbackQuality":70},
    "decisionRule":"caliber-v3","expectedReadings":"①≥0.9 ③≤0.05 fragmented=0",
    "oneShot":true,"writtenAtUtc":"<早于运行>"}
   ```
2. **一次运行**：run 目录 append-only；同一切片第二次运行**只能**以 `rerun=true` 落盘，
   且该切片此后不得再作为 acceptance 使用（与批次 50 的登记方式一致，但这次要机器化）。
3. **配置绑定**：`check_split_gate.py` 增 `--config <prereg.json>`（可选参数，向后兼容）：
   - 读应用导出 `source.pageRecordSha256`/逐页 `promptSha256`（harness 记录已有，
     `GoldenSplitEvalChannelHarness.kt:246-248`）与 `source.model`；
   - 与预注册逐项比对，不一致 → `gate.state = NOT_EVALUABLE_CONFIG`、**exit 2**、不产出 rulings
     （与"不可判不伪造"同一纪律，`check_split_gate.py:616-619`）。
   - 该参数**不改变** 0.9/0.05 与分母；它只决定"这一轮算不算同一配置"。

### 5.4 机械守卫（新增一件工具，先指认它消灭的失败）

`tools/ci/check_acceptance_once.py`（新文件，只读）：

| 检查 | 判据 | 消灭的失败 |
|---|---|---|
| `ACCEPTANCE_REUSED` | 任一非 acceptance run 的 manifest/页记录里出现 acceptance 页 id | 批次 48→50 的"同 8 页第二次消费" |
| `ACCEPTANCE_RUN_COUNT` | acceptance run 记录 >1（且未标 `rerun`） | "重跑换绿" |
| `PROMPT_SHA_DRIFT` | run 的 `promptSha256` ≠ 预注册值 | 看到结果后改提示词再跑同一切片 |
| `GOLDEN_DRIFT` | run 绑定的 golden sha ≠ 预注册 | 事后改金标 |

它**不**试图证明"人没看过结果"（不可机检，沿用 `2026-11-03-…protocol.md:64-70` 的 UNVERIFIED 纪律）。

### 5.5 噪声、样本量与调参预算

- **dev 调参**：沿用 `2026-11-09-split-cv-llm.md:342-345` 的上限（≤6 组），但把"每组跑 1 轮"
  改为"每组 3 轮"：思考模式默认开、temperature 无效（§1.6），单轮 ±1–2 页是噪声，
  单轮比较会把噪声当选型依据（批次 50 三臂 ① 4/16 vs 4/16 vs 3/16 已示范）。
  记录格式：`{variant, promptSha256, run1/2/3 的 ①②③, 中位数}`。
- **选型规则（跑之前写死）**：先看 ③ 与硬红线（任一轮切残 >0 即淘汰），再看 ① 中位数；
  中位数相同则取参数更少的方案（不引入分块/重试等新机制）。
- **acceptance**：只跑 1 轮、1 次；报 `①/③/硬红线/abstain/notEvaluable` 六项，附 Wilson 描述区间。
- **不得做的事**：看到 acceptance 逐页结果后改提示词/金标/口径再跑同一切片；用 dev 的 per-page
  结果挑"看起来像 acceptance 的页"。

---

## 6. 先红后绿用例（本会话已实跑红态）

### 6.1 当前口径探针（实跑，证明缺陷存在）

```bash
python -I -c "import sys;sys.path.insert(0,'tools/split_golden');import measure_split_accuracy as M;
print(M.count_verdict(1,0));print(M.count_verdict(2,0))"
# -> (True, '一致（单题页：不拆分=期望）') / (False, '不一致（金标 2 vs 应用 0，漏拆）')
```

```text
# _page_metric 的行键（本会话实测）不含 decision/outcomeStatus：
appBoxes appBoxesSha256 appJobId appJobPageCountMatches appJobQuestionCountMatches appNameSource
appPageId appPageIdKind appPageIndex appPageMissing appProvenance consistent file
fragmentPiecesByGoldIndex fragmentedByThreshold fragmentedCount fragmentedGoldIndices
goldBoxesSha256 goldQuestions iouOk legacyConsistent matchedIous reason unmatchedAppBoxCount
# 两页夹具（一页 PASS+OK、一页 ERROR）在现行代码下 ① = 2/2 = 1.0、gatePassed = True
```

提示词侧（源码逐字）：`singlePageAssessmentPrompt` 567 字符，**不含** `json`、`type`、示例
（§1.5 的计数与 sha 复算）。

### 6.2 新增用例（红 4 / 绿 2；在仓外临时目录跑，不改仓）

文件：`C:/tmp/r1-probe/test_split_caliber_v3.py`（本会话用 `Write` 落盘后运行）

```bash
cd /c/tmp/r1-probe && python -I -m unittest discover -s . -p "test_split_caliber_v3.py" -v
```

| # | 用例 | 期望（v3 后） | 当前实跑结果 |
|---|---|---|---|
| T1 | `test_pass_with_empty_regions_is_consistent`（单题页 + PASS/OK/空框 ⇒ 一致） | 绿 | **绿（今天已是绿的，必须保持）** |
| T2 | `test_channel_error_is_not_a_success`（单题页 + ERROR/无判定 ⇒ 不一致） | 绿 | **红**：`True is not false` |
| T3 | `test_abstention_is_not_a_success`（单题页 + NEED_MORE_IMAGE ⇒ 不一致） | 绿 | **红**：`True is not false` |
| T4 | `test_abstention_page_class_is_explicit`（行内 `pageClass == "ABSTAIN"`） | 绿 | **红**：`'ABSTAIN' != None` |
| T5 | `test_aggregate_reports_abstain_and_not_evaluable`（聚合含 `abstain`/`notEvaluable` 块） | 绿 | **红**：`'abstain' not found` |
| T6 | `test_thresholds_and_denominator_unchanged`（0.9/0.05、分母=可比页数） | 绿 | **绿（今天已是绿的，必须保持）** |

结论：`Ran 6 tests … FAILED (failures=4)`。T1/T6 是"不得回退"的护栏，T2–T5 是本次要转绿的四条。

### 6.3 Kotlin 侧用例（**未运行**，只给规格）

落位：`core/data/src/test/kotlin/…/model/OpenAiCaptureAssessmentAdapterTest.kt`（既有提示词断言文件，
`:27-72` 已有"包含/不包含"式断言的先例，复用其测试设施）。

| 用例 | 断言（要点） | 当前红的原因 |
|---|---|---|
| `single page prompt pins the five top level keys` | 提示词包含五个键名 + "不得返回type" | 现文本无白名单（§1.5 实读） |
| `single page prompt ships a shape example` | 包含 `"decision":"SPLIT"` 的示例对象 | 现文本无示例 |
| `single page prompt states the region usability bounds` | 包含 2..12、80% 重叠、单题页 PASS 三条 | 现文本只有"2到12道必须SPLIT"，无重叠/单题页规则 |
| （回归，应保持绿）`page relation prompt still forbids splitting` | 既有 `assertFalse(prompt.contains("必须返回SPLIT"))` 等 | 改单页提示词时**不得**把"必须返回SPLIT"带进关系版 |

**未跑原因**：红线"Gradle 不得与其他 agent 并发"；本会话属研究阶段，不占用 Gradle。
⇒ 这三条的红态是**源码逐字阅读**结论，不是测试运行结论；实现阶段必须实跑
`./gradlew.bat :core:data:testDebugUnitTest --tests "…OpenAiCaptureAssessmentAdapterTest" --console=plain`。

### 6.4 一批必须同时钉住的回归（避免"改口径变变绿"）

| 用例 | 断言 | 现状 |
|---|---|---|
| `GateThresholdBoundaryTest`（既有 4 条） | 0.9/0.05 闭边界 | **绿**（61 tests OK，§6.5） |
| `GateReportShapeTest.test_report_shape_and_constants` | 阈值数值 + 出处 quote 必须仍在 | **绿** |
| `HistoricalAppExportTest.test_gate_value_and_automation_ruling_unchanged` | 历史设备导出的门值 4/7 与自动化裁定不变 | **绿**；v3 的 fallback 路径必须让它继续绿（严格规则只作用于带 `decision` 的导出） |

### 6.5 门与工具的基线（本会话实跑）

```bash
python -m unittest discover -s tools/tests -t tools -p "test_split_gate.py"
# -> Ran 61 tests ... OK（本会话跑两次：2.940s / 3.539s，均 OK、无 skip）

python -I tools/ci/check_eval_label_leakage.py --paths tools/split_golden
# -> 扫描 4 个候选脚本；protected 标签读者 2 个；error 0 / warning 0 → exit 0
```

---

## 7. 影响面与"不改"的清单

**要改（实现阶段，P-G3）**

1. `tools/split_golden/measure_split_accuracy.py`：`PROVENANCE_KEYS` 增 `decision`/`outcomeStatus`；
   新增 `classify_page`；`count_verdict` 增可选判定参数（缺省行为仅用于 `decisionSource=absent` 的历史导出）；
   `_page_metric` 增 `pageClass`/`decision`/`outcomeStatus`；`_aggregate` 增 `abstain`/`notEvaluable`/
   `countExactV3`（并把现行为留在 `countExactV2`）。
2. `tools/split_golden/check_split_gate.py`：报告加 `caliber`、`abstain`、`notEvaluable`、`countExactV2`；
   可选 `--config` 绑定预注册（不匹配 → exit 2 `NOT_EVALUABLE_CONFIG`）。
3. `tools/split_golden/rulings.py`：逐页裁定增 `appDecision`/`pageClass`（加法字段）。
4. `core/data/.../OpenAiModelTaskAdapters.kt:161-173`：提示词按 §4.3 重写。
5. 新增 `tools/ci/check_acceptance_once.py` + 预注册文件 + v3 金标（S1 抽样、S2 盲标、S6 冻结）。

**不改**

- 阈值 0.9 / 0.05、③ 的分母（金标总题数）、① 的分母（可比金标页数）、硬红线 `fragmentedQuestions==0`。
- 金标 v1/v2 一个字节；`validate_split_golden.py` 的 C1–C7。
- 解析器白名单（`requireOnlyKeys`）——提示词修复不得以放宽解析器为代价。
- 任何产品开关：门脚本仍不切任何开关（`check_split_gate.py:123-124`）。

---

## 8. 未验证 / 风险（引用本文时必须带上）

1. **提示词效果 UNVERIFIED**：本会话零 provider 调用，§4.3 的草稿未在任何页上跑过；
   "六条规则能让 ① 到 0.9"没有任何证据，且批次 50 的三臂实验（4/16、4/16、3/16）提示单轮
   改动量级在 ±1 页。
2. **`type` 键归因是推断**：首键 `{"type":"json_object"}` 与请求 `response_format` 逐字同形，
   与 DeepSeek"要示例"的文档要求缺失一致，但"补示例即消除"**未经实验**。
3. **v3 金标不存在**：131 张剩余页的单题/双栏配额、可解码性、标注工作量均未统计。
4. **`check_acceptance_once.py` 未实现**：仓库当前无此文件；其检查项、退出码、与
   `check_eval_label_leakage.py` 的边界（静态 vs 运行产物）本文只给规格。
5. **Kotlin 用例未运行**（Gradle 并发红线），红态来自源码阅读。
6. **provider 侧版本不可锚定**：模型名只有 `deepseek-flash`，无版本号；名字漂移会让跨批次比较失效
   （批次 50 已登记同一风险）。
7. **`temperature 0.1` 在思考模式无效**这一条来自官方文档；本会话未实测请求是否真的进入思考模式。
8. **设备导出走 fallback**：口径 v3 的严格规则只在带 `decision` 的 harness 导出上生效；设备导出
   （`export_split_eval.py`，SELECT 列表见 `:99-105`、`:268-270`，无 decision 列）回落到 v2 规则并
   计 `singleQuestionUnverifiedCount`。本文只对批次 48/50 两份 harness 导出做了新旧复算，**未对设备
   导出复算**，也未验证设备侧能否从 `batch_import_page.status/failure_code` 推出等价判定。

---

## 9. 来源

**仓内一手（本会话实读/实跑）**

| 文件 | 用途 |
|---|---|
| `tools/split_golden/measure_split_accuracy.py:12-28,76-84,298-308,320-430` | 口径 v2、常量、单题页规则、逐页/聚合实现 |
| `tools/split_golden/check_split_gate.py:91-92,123-124,202-211,245-253,333-338,540-542,616-630` | 阈值、自动化裁定、v2 scope、硬红线、退出码、rulings 生成条件 |
| `tools/split_golden/GATE.md:14,17-21,30-31,57,60,117,151,236-242` | 门式、硬红线、"缺页就是漏"、区间描述性、等级、禁止清单 |
| `tools/split_golden/rulings.py:47-54,71-80,104` / `validate_rulings.py:161,179-184` | countVerdict 枚举与校验（加法字段安全） |
| `core/model/.../CaptureAssessmentTasks.kt:62-163` + `core/data/.../OpenAiModelResponseParsing.kt:113-130`、`OpenAiModelResponseParsers.kt:121-153` | 契约不变量、decision/region 耦合、三层键白名单 |
| `core/data/.../OpenAiModelTaskAdapters.kt:154-201` | 单页/关系版提示词原文（567 字符） |
| `core/data/.../OpenAiModelProtocol.kt:181,192-198,912-914` | `detail`、temperature、`response_format`、system prompt |
| `core/domain/.../CaptureWorkflowRepository.kt:439-479` | 区域可用判据（2..12、尺寸、80% 重叠） |
| `core/data/.../capture/BatchSplitRecognizer.kt:79-88` | 产品面 `Failed` 与 `NotASplit` 的区分 |
| `core/data/src/test/.../GoldenSplitEvalChannelHarness.kt:97-105,206-248` | run manifest、decision/appliedRegionCount/promptSha256 落盘 |
| `build/batch50-split/assemble_export.py:44-56` | 导出侧的 `outcomeStatus`/`decision` |
| `docs/research/2026-11-09-split-cv-llm.md:321-366` | dev/acceptance 协议、8 页全或或无的算术 |
| `docs/research/2026-11-03-aud08-human-gold-protocol.md` / `2026-11-04-…sampling-plan.md` | 等级、盲标、抽样与切分 |
| `docs/research/evidence/2026-11-07-batch-48/splitter/gate-evidence-v2-01.md`、`…/2026-11-09-batch-50/split-gate/{README.md,acceptance-protocol.md,app-split-b50-final.json}` | 历史读数、失败页清单、acceptance 二次消费、逐页 decision/status |
| `tools/tests/test_split_gate.py:193-230,290-330` | 测试基座与阈值/形状约束（本次实跑 61 tests OK） |

**外部（本会话联网实读；标注可信度）**

| 来源 | 取用内容 | 标记 |
|---|---|---|
| DeepSeek API Docs「JSON Output」`https://api-docs.deepseek.com/guides/json_mode/` | `response_format={'type':'json_object'}`；提示词需含 "json" 与示例；非 JSON 内容被替换为空格 | 【V】 |
| DeepSeek API Reference「Chat Completions API」`https://api-docs.deepseek.com/api/create-chat-completion` | `response_format.type` 仅 `text`/`json_object`；`function.strict` 为 Beta；`temperature` 在思考模式无效；思考默认开启 | 【V】 |
| DeepSeek API Docs「Vision」`https://api-docs.deepseek.com/guides/vision` | `detail` 语义（low=512²）；图像重采样到 ~1300×1300、单图 ≤1024 tokens；格式/尺寸上限 | 【V】 |
| OpenAI Cookbook「Getting the Most out of GPT-5.4 for Vision and Document Understanding」(2026-03-06) | 密排/手写用最高细节；定位用固定坐标空间 + 明确 box 格式；定位+裁剪重跑的替代管线；Structured Outputs vs JSON mode | 【V】 |
| OpenAI「Structured model outputs」`…/api/docs/guides/structured-outputs.md` | JSON mode 不保证 schema 遵从；Structured Outputs 需 `json_schema` + `strict` + `additionalProperties:false` | 【V】 |
| HiLEx（ICDAR 2025，Springer pp.485–505）`https://hilex-dla.github.io/HiLEx/` | 题卷版面基准：1,965 页、6 类层级、专家 κ>0.90；零样本 VLM 83.5 vs 微调 94.0 mAP@50；小目标难、同块多框 | 【V】 |
| LocateBench（arXiv:2410.19808） | 最强模型定位准确率仍落后人类 >10% | 【V】 |
| selective prediction / risk–coverage（第三方综述页，`insightful-data-lab.com/2025/03/28/selective-prediction/`） | "弃答要同时报 coverage 与 risk"的通用框架（本文只借用其报告原则，不引入阈值） | 【I】（单一第三方来源） |
