# 切分金标门说明（AUD-08 · v1，2026-10-27；2026-11-02 批次 40 增补等级/裁定/范围）

> 本文是切分门的**独立载体**（此前门值散落在脚本头部与 4C 阶段记录里，无独立仓内产物——
> 批次 26 基线 §7.3 缺口 2）。规范原文：`docs/research/2026-10-11-new-stages-spec.md` §4C
> ：167"门说明**分别**给完整题命中、切残、漏题、错误合并、重复率的分母/阈值/样本量/
> 统计区间和严重度。**切残不得自动入库**沿用既有红线；数值须按既有'立项自定/科学落实'
> 流程形成设计产出，不在本次维护中编造。"
>
> 2026-11-02 增补依据：`docs/research/2026-11-02-aud08-annotation.md`（R5，逐页裁定 schema /
> G0–G5 等级 / 生产者 v2 / FAIL 安全口径）。

## 0. 门值与红线（先读）

- **门式：① 完整题命中率 ≥ 0.9 且 ③ 切残率 ≤ 0.05**（闭边界，`>=` / `<=`）。
  数值属性：**"立项自定"建议值**（4C 计划 E4，`docs/research/2026-10-14-4c-stage1-record.md:19-22`），
  非测量校准产物；改值必须先走裁定并留旧值对照（R4 §3.7 验收原文）。
- **切残硬红线（独立于门式）**：切残题干**不得入库**（裁定 37 / 4C③）。门报告新增
  `hardRedLines.fragmentedQuestions`（value / required=0 / satisfied）。**金标总题数 n≥20 时
  1/20 = 0.05 会通过 ③ 的闭边界**，因此任何"门后自动化"讨论都必须同时满足
  `gate.passed && hardRedLines.satisfied`，不得只看 ③（见 §5）。
- **门不过 → 门后自动化不启用**。门脚本 `check_split_gate.py` 只判门、只报数、只落证据：
  **不切任何产品开关**。
- 仓内**不存在**门控开关/自动入库实现。2026-11-02 实测命令：
  `grep -rniE 'split.?gate|gate.?split|qualityGate|autoIngest|auto_ingest' --include=*.kt
  --include=*.kts app core feature` → **1 命中且无关**（`core/database/.../
  MasteryOverviewInstrumentedTest.kt:124` 的测试方法名
  `modelEvidenceAggregatesSplitAcceptedFromRejected`）；"自动入库"一词在
  `feature/capture/.../CaptureAcquisitionCommands.kt:161` 的注释里出现一次
  （A7 分享路径，与切分门无关）。**旧版"grep 0 命中"的写法已按实测更正。**
- 当前判定：**门 = FAIL**（① 0.5714 < 0.9，③ 0.0000 ≤ 0.05；数据源为 4C 存量设备导出，
  sha `9b49b0c4…`，来源绑定 UNVERIFIED）→ 门后自动化继续不启用。
  门后自动化启用属 4C③ 产品裁定，须另立 PRD/裁定，不以工具面交付触发。
- **范围声明（不得外推）**：上述门值只对「金标 `f9e92c47…`（7 页/16 框，G2）×
  应用导出 `9b49b0c4…`」这份样本成立；它**不等于**当前设备库切分准确率，也**不等于**
  模型在 155 页数据集上的准确率（7 页不是 155/24 页的概率样本；训练/开发/验收切分未做；
  等级 G2 是代理金标）。逐页证据见 `docs/research/evidence/2026-11-02-batch-40/
  step-aud08-gate-4c.md`。
- 2026-11-02 真设备库复跑（emulator-5562，schema **72**，`split_import_job`/`split_import_question`
  均为 0 行）：① 2/7 = 0.2857、③ 0/16 = 0.0000 → **FAIL**；emulator-5596（schema **71**，
  同样 0 分题行）导出同形（sha `02cec6f0…`）。该值同样只对这份导出成立（0 分题行 ≠
  设备切分能力）。证据
  `docs/research/evidence/2026-11-02-batch-40/step-aud08-gate-device72.md`。
- **2026-11-03 批次 41 复算（阈值零改动）**：金标 sha `f9e92c47…` × 4C 存量导出 sha
  `9b49b0c4…` → ① **4/7 = 0.5714 < 0.9**、③ **0/16 = 0.0000 ≤ 0.05** → **FAIL（exit 1）**，
  门后自动化继续不启用。本次同时落盘报告 `build/split-eval/gate-report-batch41.json`、
  证据 `docs/research/evidence/2026-11-03-batch-41/step-aud08-gate-recompute.md` 与逐页裁定
  `docs/research/evidence/2026-11-03-batch-41/rulings/20261102T143319Z-4c83a01e.json`
  （`validate_rulings.py` R1–R9 PASS）。与批次 40 同输入裁定内容级一致（判词/几何/差异全同；
  仅 `decidedAtUtc` 与工具 sha 属运行身份）。**不得为让门变绿改阈值/口径/分母/金标。**

## 1. 五类指标的门说明（逐类：分母 / 阈值 / 样本量 / 统计区间 / 严重度）

### ① 完整题命中（countExactRate）——已实现，设门

| 项 | 值 |
|---|---|
| 分母 | **可比金标页数**（金标与应用导出按页文件名配对成功的页；应用缺页按 0 框计入度量、分母不减——缺页就是漏，如实压低分子）。口径 v2：单题金标页（questionCount<2）"不拆分即一致"（应用侧最小合法拆分 2 块，`CaptureWorkflowRepository.kt` `isUsableSplitRegionSet()` 2..12） |
| 阈值 | ≥ **0.9**（立项自定；出处：stage1 记录 :19-22 + `measure_split_accuracy.py` 常量 `COUNT_GATE`） |
| 样本量 | 当前金标 v1：**7 页**（24 抽样入集 7，步长 6 系统抽样，`sampleRule` 内登记）；样本量极小，门值是该样本上的裁定，不是统计推断 |
| 统计区间 | Wilson 95%（门脚本随报告输出，**描述性区间、非门**；7 页样本下区间极宽——例：4/7 的区间 0.25–0.84） |
| 严重度 | 门不过 → 门后自动化不启用（4C③）；不影响既有逐题确认入库路径 |
| 当前值 | **0.5714**（4/7）→ 未过 |

### ③ 切残（fragmentRate）——已实现，设门 + 红线

| 项 | 值 |
|---|---|
| 分母 | **金标总题数**（16）。切残 = 同一道金标题的题干被应用切成 ≥2 个区域（应用框按"重叠/自身面积 ≥ 阈值 0.5"分派给金标题；同一金标题收到 ≥2 碎片即切残；未被覆盖不算切残——那是漏） |
| 阈值 | ≤ **0.05**（立项自定；出处同上，常量 `FRAGMENT_GATE`）；附 0.3/0.4/0.5/0.6 分派阈值敏感性输出 |
| 样本量 | 同一金标 v1（16 题） |
| 统计区间 | Wilson 95%（描述性） |
| 严重度 | **红线：切残题干不得入库**（无论门值如何）；门式内 ≤0.05 不过 → 自动化不启用。当前 0.0 —— 红线未触发，门 false 由 ① 单独造成 |
| 当前值 | **0.0000**（0/16）→ 过 |

### ② 边界 IoU≥0.5 题占比（iouRate）——已实现，**只度量、不设门**

| 项 | 值 |
|---|---|
| 分母 | 金标总题数；逐页最大权 1:1 配对（应用框少于金标时未配对金标题按未达标计） |
| 阈值 | 无门（4C 计划 E3/E4 明确"② 只度量、不在门式内"）；度量内阈值 0.5 |
| 样本量 | 金标 v1（16 题） |
| 统计区间 | 不输出（信息性指标） |
| 严重度 | 无门则无严重度裁定；用于诊断边界质量与切残/漏题定位 |
| 当前值 | 0.4375（7/16） |

### 漏题（missing）——**无独立指标实现**，间接体现于 ①/②

| 项 | 值 |
|---|---|
| 分母/阈值/样本量/区间 | **未立项**（无独立分母定义与阈值；不得编造）。现状：漏题仅以"金标页无应用输出/未配对金标题"形式体现在 ① 的分子与 ② 的未配对计里；`--json` 报告与门证据以 `missingAppPages` 逐页列名 |
| 严重度 | 间接：漏拆页压低 ① → 门不过 → 自动化不启用；用户侧失败页有"继续补拍本题"出口（`CaptureModelTaskCard.kt:116-124`，R4 §3.7 复核） |
| 立项待办 | 若产品裁定要求独立漏题门，按"立项自定/科学落实"流程定义分母（建议方向：漏题数/金标总题数）与阈值，另改本文件并留旧值对照 |

### 错误合并（wrong-merge）——**未实现，未立项**

| 项 | 值 |
|---|---|
| 分母/阈值/样本量/区间 | **无**（批次 26 基线 §7.3 缺口 1 原文："错误合并、重复率无指标实现"）。定义方向（未立项，仅供立项参考，不是门）：一道应用框横跨 ≥2 道金标题且 IoU 配对失败 |
| 严重度 | 未裁定 |
| 立项待办 | 同上，须按规范流程形成设计产出后再进本表 |

### 重复率（duplicate）——**未实现，未立项**

| 项 | 值 |
|---|---|
| 分母/阈值/样本量/区间 | **无**。工具侧现有相邻防线：金标校验 C5（页不重复）、度量 `load_app`（应用页名重复即 DataError）、导出器（重复页名 exit 2）——但这些是**数据完整性防呆**，不是"切分重复率"指标 |
| 严重度 | 未裁定 |
| 立项待办 | 同上 |

## 2. 样本与数据集（冻结与切分状态）

- 金标 v1：`tables/split_golden_v1.json` + `.sha256` 旁车，冻结于 2026-10-13T14:50:48Z，
  sha `f9e92c47b399252d2e92ecd633c2a2b76a52b0bfcdcff78868a513dedd1e4c9f`；7 页 / 16 框；
  科目 化学 3 / 其他 4；版面 整页 1 / 裁边 6。
- **证据等级（如实）**：`model-labeled, no human expert; two independent labelers agreed`
  ——模型标注、两名标注员独立一致、**无人工专家**，是代理金标。真实人工正确率
  **UNVERIFIED**；不得在任何文本里把 v1 称作"人工金标"。
- **训练/开发/验收切分：未做**（规范 :165"分训练/开发/验收，不能用同一批调门又验门"）。
  现状是单集调门+验门同体——这是登记在案的数据半边缺口：补齐须另建 v2（新文件+新旁车，
  v1 冻结不动），并至少留一个验收子集在门值裁定后不参与任何调参。
- 标注来源/复核的结构化登记字段（标注员/复核人）：v1 冻结无此字段（C1 冻结字节不可改），
  升级 v2 时补；当前等级靠 evidenceGrade 常量锁死（validate C2 防改写）。

## 2.1 证据等级 G0–G5（2026-11-02 起机检）

两套"证据等级"不可互换：研究文档正文的【V】/【P】/【I】是**陈述可信度**；金标文件的
G0–G5 是**数据集标签来源等级**。`evidenceGrade` 是自由文本，C2 只能锁"这行字没被改过"；
等级必须结构化，否则"把模型标注改写成人工金标"只需要改一个字符串。

| 级别 | 定义 | 必须随产物登记（C7 机检） | 门值含义 |
|---|---|---|---|
| G0 | 未标注 | — | 不可判（NOT_EVALUABLE） |
| G1 | 单次模型标注 | `labelers[1]`（kind=model + provider/modelId/modelVersion/promptSha256/ranAtUtc） | 不可当正确率 |
| **G2（当前 v1）** | 两次独立模型标注一致 | `labelers[≥2]`（不同 modelId/modelVersion/promptSha256）+ `agreement{metric,value,subset}` | 红可阻断；绿仅可进人工复核队列 |
| G3 | 一名人工逐条核过 | 根 `review{humanId,guidelineVersion,reviewedAtUtc,changes}` + 每页 `reviewedBy` | 绿可进人工在环试点，仍需产品裁定 |
| G4 | ≥2 名人工独立标注 + 分歧裁定 | G3 全部 + ≥2 个 human labeler + `adjudication{method,adjudicatorId,role,guidelineVersion,decidedAtUtc}` + `agreement` | 绿可作自动化裁定的输入（仍需 4C③ + held-out） |
| G5 | G4 + 专家裁定 + 冻结指南 + 训练/开发/验收切分 | G4 全部 + `split{dev,acceptance,heldOutUnusedForTuning=true}` | 可在**该样本范围内**对外表述 |

- **v1 = G2 但 `recomputable=false`**：冻结产物没有逐页 `labelers` 记录，"两名标注员独立一致"
  无法从产物复算（哪两个标注员、什么模型/提示词版本、是否真的互不可见——全部 UNVERIFIED）。
  C2 只能证明字符串没被改过。v1 冻结字节不可改，**升级 = 新文件 v2 + 新旁车**。
  人工层的结构化字段、模型标注显式规则与双人复核流程见
  [`docs/research/2026-11-03-aud08-human-gold-protocol.md`](../../docs/research/2026-11-03-aud08-human-gold-protocol.md)
  （协议落盘；无标注员执行，人工金标正确率仍 UNVERIFIED）。
- `evidenceGradeLevel` 是 v2 根级必填字段；C7 逐级检查上表字段。**不可机检项**
  （互不可见、专家资质真伪、held-out 是否真的未参与调参）不假装已检：G5 的
  `heldOutUnusedForTuning` 是**自证字段**，须在记录中单列 UNVERIFIED。
- **等级不改变门值**（0.9/0.05 不动）；等级改变的是"这个门值能推出什么"。
- **红可下传，绿不可上推**：任何级别上的 FAIL 都阻断；PASS 只在该级允许范围内成立。
  升级只能靠新增独立证据（"重跑一次模型，两次一致"永远停在 G2）。
- 金标 v1 的 24→7 入集准则未记录：7 页不是 24 页的概率样本，更不是 155 页的概率样本。

## 3. 判门入口与证据格式

- 判门：`python tools/split_golden/check_split_gate.py`（先冻结校验 C1–C7，再度量，
  再按 0.9/0.05 闭边界判定；exit 0 过 / 1 不过 / 2 冻结或数据不可用 / 3 应用导出缺失）。
- 报告：`--report-json`（确定性 JSON：sha、命令、exit、阈值+出处、wilson95、逐页裁定、
  `appSource` 来源摘要、`golden.evidenceGradeLevel`、`hardRedLines`、`scope`）；
  `--evidence-out`（R4 模板精神 md：E-ID 表 / 产物身份 / 命令与退出码 / 指标与门 /
  逐页表（含"来源"列）/ UNVERIFIED 清单 + 范围声明 / 体积自查 ≤400 行 ≤64 KiB，
  已存在拒绝覆盖）。逐页"来源"取自应用导出的 `nameSource`+`jobId`；无溯源块（4C 历史
  导出）写 `UNVERIFIED（导出无溯源块）`，不以文件名反推。
- **逐页裁定（rulings）**：`--rulings-out <dir>` 写 `<dir>/<runId>.json`（一 run 一份、
  已存在即拒绝；门不可判时**不生成**——不可判 ≠ 没发现问题）。
  校验：`python tools/split_golden/validate_rulings.py --file <rulings.json>`（R1–R9）。
  设计见 `docs/research/2026-11-02-aud08-annotation.md` §2。要点：
  - `pageId/pageIdKind`、`appRef{pageId,jobId,pageIndex,nameSource,pagePresent}`、
    `gold/app.boxesSha256`（几何绑定）、`diff` 全部数值（含 `iouByGoldIndex`/
    `unmatchedAppBoxCount`/`fragmentPiecesByGoldIndex`）、`ruling.decidedBy.kind`
    （机器判定必须是 `script:` 前缀的 agentId，不得填人名）、`evidenceGradeLevel`；
  - `rulingsSha256` 是**自洽摘要**（不是防篡改）：改一处数字而不更新摘要即校验红；
  - `summary.missingInApp` 与指标 `①` 的分子**不是同一口径**：单题金标页（questionCount<2）
    在应用导出里缺页时，① 仍记"一致（不拆分=期望）"，而逐页主判词按页存在性记
    `MISSING_IN_APP`——两个数各自正确，引用时必须写明口径。
- 度量机器口径：`python tools/split_golden/measure_split_accuracy.py --json`。
- 数据生产：`python tools/split_golden/export_split_eval.py --db <设备DB快照>
  [--page-map <map.json>]`（只读 DB、同快照同 sha、页名三级解析并逐页标 `nameSource`；
  v2 额外写 `producer`/`db.userVersion`/`job`/`integrity`/`boxesSha256`/`snapshot.mode`）。
- 工具链总览：`README.md`。

### 3.1 逐页裁定统计（2026-11-03 批次 41）

逐页裁定不是"再算一遍门值"，而是把每页的判词、几何摘要与差异数值固化下来（`rulings.py`）。
当前仓内三份裁定（均 `gate.state=FAIL`，`validate_rulings.py` 全 PASS）：

| 运行 | 应用导出 sha | 输入 | 判词统计（7 页） | 说明 |
|---|---|---|---|---|
| 批次 40 `20261101T234146Z-2318b8db` | `9b49b0c4…` | 4C 存量导出（无 `source` 块） | CONSISTENT 4 / INCONSISTENT_COUNT 3 / 其余 0 | 基线 |
| 批次 40 `20261101T234207Z-76aba6d0` | `fa0ba8ae…` | emulator-5562，schema 72，split 表 0 行 | MISSING_IN_APP 7 | **0 页导出 ≠ 设备切分能力**；只说明该库没有可导出的分题行 |
| 批次 41 `20261102T143319Z-4c83a01e` | `9b49b0c4…` | 同基线输入，当前工具 | CONSISTENT 4 / INCONSISTENT_COUNT 3 / 其余 0 | 复算；与基线**内容级一致**（仅时间戳/工具 sha 为运行身份） |

批次 41 逐页（`--rulings-out` 产物）：

| file | 主判词 | 金标题数 | 应用框数 | countVerdict | IoU≥0.5 题数 | 切残题数 |
|---|---:|---:|---:|---|---:|---:|
| IMG20260810003928.jpg | CONSISTENT | 4 | 4 | CONSISTENT | 4 | 0 |
| IMG20260810004304.jpg | CONSISTENT | 1 | 0 | CONSISTENT | 0 | 0 |
| IMG20260810004353.jpg | INCONSISTENT_COUNT | 2 | 0 | INCONSISTENT_COUNT | 0 | 0 |
| IMG20260810005605.jpg | INCONSISTENT_COUNT | 2 | 0 | INCONSISTENT_COUNT | 0 | 0 |
| IMG20260810010349.jpg | CONSISTENT | 4 | 4 | CONSISTENT | 3 | 0 |
| IMG20260810011126.jpg | INCONSISTENT_COUNT | 2 | 0 | INCONSISTENT_COUNT | 0 | 0 |
| IMG20260810011800.jpg | CONSISTENT | 1 | 0 | CONSISTENT | 0 | 0 |

读法（引用时必须带口径）：

- 两张单题页（`…4304`、`…1800`）应用 0 框但 ① 记**一致**——单题页"不拆分"即期望
  （口径 v2）；而逐页主判词按"页面存在 + 0 框"记 `CONSISTENT`，两者不矛盾。
- `…0349` 页题数一致但 1 题 IoU 0.4751 < 0.5：① 记一致，②（只度量）记 3/4——
  "题数一致"不等于"边界合格"。
- 三页多题页应用 0 框（漏拆）是 ① 0.5714 的唯一来源；切残 0/16，硬红线未触发。
- 7 页样本的 Wilson 区间极宽（① 0.2505–0.8418），**不得**把逐页统计外推为总体准确率。

## 4. 门红时的安全口径（KD-76 先例）

1. 总结论行只能出现 `PASS` / `FAIL` / `NOT EVALUABLE` 三者之一，且与 `gate.state` 一致。
2. 门红时任何"通过证据"分量行**不得**无条件写 `PASS`；子步骤（冻结校验、度量）可以写
   `PASS（子步骤）`，且不得出现在总结论之后。`render_evidence` 的输出由
   `tools/tests/test_split_gate.py::GateScopeAndRedLineTest` 断言（门红时每个含 `PASS`
   的行都必须带 `（子步骤）`）。
3. `NOT_EVALUABLE` 不得代替 `FAIL` 上报，也不得当作"未发现问题"。
4. 门值、口径、分母、金标都不得为了让门变绿而改；阈值改动必须先裁定并留旧值对照。

## 5. 红线与门式的张力（必须先裁，不是实现细节）

- 门式 ③：切残率 `≤ 0.05`；红线：切残题干**不得入库（无论门值如何）**。
- 算术后果：n=16（当前金标总题数）时 `1/16=0.0625 > 0.05`，两者不冲突；**n≥20 时
  `1/20=0.05 ≤ 0.05`，1 道切残题会通过门式**，与红线直接冲突。
- 因此：**在扩样本（或任何金标总题数 n≥20）之前，必须先裁定"门后自动化是否 AND 一个
  独立的 `fragmentedQuestions == 0` 硬红线"**。工具侧已把该红线作为报告字段
  `hardRedLines.fragmentedQuestions` 输出（只报不算）；开启门后自动化仍须 4C③ 产品裁定。
- 即使门变绿，以下四项仍各自独立阻断自动化：held-out 未做；等级仅 G2（代理金标）；
  样本不可外推（7 页）；无产品裁定（4C③）。

### 5.1 禁止清单

1. 不得为让门变绿改阈值/口径/分母/金标。
2. 不得用 `NOT_EVALUABLE` 代替 `FAIL` 上报，也不得把 `NOT_EVALUABLE` 当"未发现问题"。
3. 不得在门红时打印任何门级 `PASS` 判词（子步骤须带限定词）。
4. 不得覆盖既有证据/报告/裁定文件（脚本已拒绝）。
5. 不得用旧导出的门值代表新设备/新模型/新 schema（`inputs.app.dbUserVersion` 必须同读）。
6. 不得在未裁定 `fragmentedQuestions == 0` 硬红线前，用金标总题数 n≥20 的金标开自动化讨论。
7. 不得把 G2 的绿写成"人工金标通过"；等级升级只能出 v2 新文件。
