# AUD-08 标注协议与金标证据等级（R5 / 驱动 WS5 五项）

- 日期：2026-11-02
- 定位：**评测标注协议研究**，不是实现交付。产出四件：①逐页裁定 schema；②`model-labeled` 与人工标注的
  证据等级分层及门值含义；③app-split 生产者格式（真实设备库快照）；④门仍 FAIL 时的安全口径。
- 服务对象：计划 `.zcode/plans/plan-2026-11-02-batch-40-5x.md` §1 WS5 item 29–33（`:51-56`）。
- 方法：仓内一手实读 + 本会话实跑（`validate` / `measure --json` / `check_split_gate` 三条，均只读）+
  联网取官方文档与文献。**未跑 Gradle、未操作设备、未做任何 git 写操作。**
- 证据等级约定（沿用 `docs/research/batch-intake-research.md:6`）：
  `【V】`=已核实原文数字/官方原文；`【P】`=已核实来源存在+定性结论；`【I】`=工程推断待校准；
  `UNVERIFIED`=未溯源。
- **注意区分两套"证据等级"**：本文的【V】/【P】/【I】是**文档陈述**的可信度标记；
  第 3 节的 `G0–G5` 是**数据集标签**的来源等级。两者不可互换（第 3.1 节）。

---

## 0. 结论摘要（先读）

| # | 问题 | 结论 | 依据 |
|---|---|---|---|
| 1 | 逐页裁定 schema | **必须把"门裁定"与"标签裁定"拆成两种记录**。门裁定 = 脚本对"系统输出 vs 金标"的确定性计算（可每次重算、append-only）；标签裁定 = 金标本身怎么来的（冻结在数据集里，改它必须换版本+旁车）。前者写 `rulings[]`，后者写 golden v2 的 `labelers[]/adjudication`。**"裁定人"字段必须带 `kind=automated/human/model`，机器判定不得填人名。** | §2 |
| 2 | 证据等级分层 | 定义 `G0–G5` 六级。当前金标 v1 = **G2**（`model-labeled, no human expert; two independent labelers agreed`，见 `validate_split_golden.py:63`、金标 `evidenceGrade`）。门语义的核心规则：**红可下传，绿不可上推**——任何级别的 FAIL 都阻断；PASS 只在该级别允许的范围内成立。 | §3 |
| 3 | app-split 生产者格式 | 现有格式有**七项缺口**（DB schema 版本、job 级来源、稳定页 id、job 题数一致性、生产者身份、快照模式、逐页几何摘要），归为四类溯源缺失。建议 v2 **只做加法**（不改 `pages[].file/questions`），并保持"同快照复跑逐字节一致"；`-shm` 继续排除。真实设备快照的取库纪律用 SQLite 官方 WAL 原文加固。 | §4 |
| 4 | 门 FAIL 的安全口径 | 三值逻辑 **PASS / FAIL / NOT_EVALUABLE**，只有 PASS 才能进下一道产品裁定；`automation.enabled` 在三个脚本里**只可能是 `False`**（`check_split_gate.py:160`，`measure_split_accuracy.py:434,463`）。门红时**任何门级判词不得出现 PASS**（KD-76 先例）。另发现一处**潜在红线冲突**：③ 的 `≤0.05` 在金标总题数 n≥20 时会放行 1 道切残题（1/20=0.05≤0.05），与 `GATE.md:42` 的红线不一致，扩样本前必须先裁。 | §5 |
| 5 | 当前门值 | 本会话实跑 **FAIL（exit 1）**：① 4/7=0.5714、③ 0/16=0.0000、② 7/16=0.4375。**该值只对 `app-split.json` sha `9b49b0c4…` 这份 4C 存量导出成立**，不得外推为"当前设备库切分准确率"。 | §5.5 |

---

## 1. 事实闭包（本会话实读/实跑）

### 1.1 冻结金标 v1

`tools/split_golden/tables/split_golden_v1.json`（3,809 B，sha `f9e92c47b399252d2e92ecd633c2a2b76a52b0bfcdcff78868a513dedd1e4c9f`）：

- 7 页 / 16 框；`generatedAt=2026-10-13T14:50:48Z`；科目 化学 3 / 其他 4；版面 整页 1 / 裁边 6。
- 根字段恰好 `{schemaVersion, generatedAt, sampleRule, evidenceGrade, pages}`；页字段恰好
  `{file, subject, layout, questionCount, boxes}`（`validate_split_golden.py:68-69`）。
- **页级没有**题型/公式图/手写/跨页/选项/附图归属等分层字段——规范 `docs/research/2026-10-11-new-stages-spec.md:165`
  要求的八项属性，v1 只落了 `subject` 与 `layout` 两项。
- `sampleRule` 原文自承：155 个文件按步长 6 取 24 张，**"仅实际打开并肉眼查看了其中 2 张"**；
  其余 22 张只做存在性与魔数核验。
- **24→7 的入集规则未记录**（`sampleRule` 只写抽样到 24；GATE.md:29 写"入集 7"，但选择准则不在产物里）。

实跑（本会话）：

```bash
python tools/split_golden/validate_split_golden.py
# → 拆题金标校验 PASS（C1–C6 全过）；exit 0
```

### 1.2 工具链四脚本与当前字段

| 脚本 | 现状（本会话实读） |
|---|---|
| `validate_split_golden.py` | C1 冻结字节（旁车+canonical 逐字节）、C2 schema 与 `evidenceGrade` 冻结常量、C3 count==len(boxes)、C4 框合法、C5 页不重复、C6 页数≥7（`:244-272`） |
| `export_split_eval.py` | 设备库 → `app-split.json`；产物字段见 `:301-318`：`schemaVersion/source{db,snapshot,payload,pageMap,queries,readTransaction,nameSourceCounts}/pages[]`；页字段 `{file,nameSource,jobId,pageIndex,questionCount,selectedCount,confirmStates,questions}`（`:266-274`） |
| `measure_split_accuracy.py` | 口径 v2；单页计算 `_page_metric`（`:299-339`），报告 `build_report`（`:426-458`）；`--json` 供门消费 |
| `check_split_gate.py` | 冻结校验 → 子进程度量 → 闭边界判定（`:222-240`）；`perPage` 透传（`:241`）；证据渲染（`:267-394`） |

测试面：`tools/tests/test_split_gate.py` 共 **37** 个 `def test_`（`grep -c` 计数）；本会话实跑
`python -m unittest discover -s tools/tests -t tools -k split_gate` → **Ran 37 tests … OK**（5.503 s，exit 0）。覆盖阈值边界、
报告形状、证据等级改写拦截、快照身份、WAL 漂移、溯源透传。

### 1.3 门值实跑（本会话）

```bash
python tools/split_golden/measure_split_accuracy.py --json     # exit 0
python tools/split_golden/check_split_gate.py                  # exit 1
```

`check_split_gate.py` 输出（逐字）：`切分金标门判定（AUD-08）：FAIL（exit 1）`；
`① countExactRate = 4/7 = 0.5714`；`③ fragmentRate = 0/16 = 0.0000`；
`自动化：门后自动化不启用（裁定 37 / 4C③…）`。

逐页（`--json` 实跑）：

| file | 金标题数 | 应用框数 | ①一致 | IoU≥0.5 | 切残 |
|---|---:|---:|---|---:|---:|
| IMG20260810003928.jpg | 4 | 4 | 一致 | 4 | 0 |
| IMG20260810004304.jpg | 1 | 0 | 一致（单题页不拆分=期望） | 0 | 0 |
| IMG20260810004353.jpg | 2 | 0 | 不一致（漏拆） | 0 | 0 |
| IMG20260810005605.jpg | 2 | 0 | 不一致（漏拆） | 0 | 0 |
| IMG20260810010349.jpg | 4 | 4 | 一致 | 3 | 0 |
| IMG20260810011126.jpg | 2 | 0 | 不一致（漏拆） | 0 | 0 |
| IMG20260810011800.jpg | 1 | 0 | 一致（单题页不拆分=期望） | 0 | 0 |

Wilson 95%（本会话用 `wilson95` 复算）：① `4/7 → 0.2505–0.8418`；③ `0/16 → 0.0000–0.1936`；
② `7/16 → 0.2310–0.6682`。均为**描述性区间，非门**（`GATE.md:30`）。

### 1.4 应用导出的两种形态（重要）

| 形态 | 实例 | 事实 |
|---|---|---|
| 4C 存量导出 | `build/split-eval/app-split.json`，1,482 B，sha `9b49b0c4…` | **7 页全在，但 5 页 `questions=[]`；文件根只有 `pages`，无 `source` 块**（本会话读取确认）。所以"漏拆"不是"缺页"——`missingAppPages=[]` 是正确的，"应用缺该页"判词不适用 |
| 批次 31 真设备导出 | `build/split-eval/device-5562/app-split.json`，677 B | **0 页**；`source` 只有 `db/nameSourceCounts/pageMap/queries`，**无 `snapshot`/`payload`**（早于 KD-81 修复的格式） |

结论：现有两份产物**没有一份**能同时回答"哪台设备、哪个 DB schema、哪次导入 job、哪一版生产者"。

### 1.5 仓内外既有裁定（不可绕开）

- 切残不得自动入库；门不过不允许自动入库（裁定 37 / 4C③，`GATE.md:11-19`、`:14-16`）。
- 阈值 0.9/0.05 是**"立项自定"建议值**，不是测量校准产物；改值必须先裁定并留旧值对照（`GATE.md:12-13`）。
- 训练/开发/验收切分**未做**（`GATE.md:88-92`；规范 `:165` 要求"不能用同一批调门又验门"）。
- `-shm` 不进身份也不进输出；0 字节 `-wal` 按"无 WAL"归一（`README.md:99-108`；KD-81 已于批次 35 关闭，
  `docs/known-defects.md:3179`）。
- KD-76 的判词教训：分量打印 `PASS` 而主口径为红，属"该判词口径错误、不得再作为通过证据"；
  n 不足或上游红时只打印 `NOT EVALUABLE`（`docs/known-defects.md:2928`）。

---

## 2. 一、逐页裁定 schema（页 id / 框数 / 差异 / 裁定人 / 证据等级）

### 2.1 先拆两类裁定（本节的中心命题）

"逐页裁定"在 AUD-08 里被两种完全不同的动作共用：

| | A. 门裁定（gate ruling） | B. 标签裁定（label adjudication） |
|---|---|---|
| 问题 | 这次系统输出相对**已冻结金标**表现如何 | 金标这一页的框**本身**对不对 |
| 谁做 | 脚本，确定性规则 | 人（或模型+人复核） |
| 何时 | 每次判门都可重算 | 只在建/升金标版本时发生 |
| 产物 | 一次运行一份 `rulings.json`，append-only | 冻结进 golden v2，改它必须换文件+旁车 |
| 可否被新运行取代 | 是（新 run 新文件，旧文件保留） | 否（v1 冻结字节不可改，只能出 v2） |
| "裁定人"含义 | 脚本身份 + 规则 id | 真实标注员/裁定员 id + 角色 |

把两者塞进同一字段会产生两个具体错误：
① 机器算出的"不一致"被读成"有人工裁定过"；② 人工改过的框在下次判门时被脚本"重算"覆盖，改动无痕。
所以 schema 分两处落，字段名不共用。

### 2.2 门裁定记录（`rulings.json`，schema v1）

一个 run 一个文件，结构如下（字段表见 2.3；完整示例见附录 A）：

```json
{
  "rulingSchemaVersion": 1,
  "run": {
    "runId": "<UTC时间戳>-<8位随机>",
    "generatedAtUtc": "2026-11-02T05:20:00Z",
    "tool": {"file": "check_split_gate.py", "sha256": "<64-hex>"},
    "command": ["python", "tools/split_golden/check_split_gate.py", "--golden", "…", "--app", "…"]
  },
  "inputs": {
    "golden": {"file": "split_golden_v1.json", "sha256": "f9e92c47…", "evidenceGrade": "model-labeled, no human expert; two independent labelers agreed", "evidenceGradeLevel": "G2"},
    "app": {"file": "app-split.json", "sha256": "9b49b0c4…", "snapshotIdentitySha256": null, "payloadSha256": null, "dbUserVersion": null},
    "caliber": "v2",
    "thresholds": {"countExactMin": {"value": 0.9, "comparison": ">="}, "fragmentMax": {"value": 0.05, "comparison": "<="}}
  },
  "gate": {"state": "FAIL", "passed": false},
  "rulings": [ … ],
  "summary": {"pages": 7, "consistent": 4, "inconsistentCount": 3, "fragmented": 0, "missingInApp": 0, "notEvaluable": 0}
}
```

单页记录（真实数据取自 2026-11-02 实跑，`boxesSha256` 截断显示）：

```json
{
  "pageId": "IMG20260810010349.jpg",
  "pageIdKind": "golden_filename",
  "appRef": {"jobId": "<job>", "pageIndex": 0, "nameSource": null, "pagePresent": true},
  "gold": {"questionCount": 4, "boxesSha256": "e1e4dddb28f4c53e…"},
  "app":  {"boxCount": 4, "boxesSha256": "15e628008bb6728a…"},
  "diff": {
    "countDelta": 0,
    "countVerdict": "CONSISTENT",
    "iouByGoldIndex": [0.6391, 0.7057, 0.6751, 0.4751],
    "iouOkCount": 3,
    "fragmentPiecesByGoldIndex": [1, 1, 1, 1],
    "fragmentedGoldIndices": [],
    "appPagePresent": true,
    "unmatchedAppBoxCount": 0
  },
  "ruling": {
    "verdict": "CONSISTENT",
    "redLineHit": false,
    "decidedBy": {"kind": "automated", "agentId": "script:check_split_gate.py@sha256:<12>", "ruleId": "count-v2+iou-maxweight-0.5+fragment-0.5"},
    "decidedAtUtc": "2026-11-02T05:20:00Z",
    "evidenceGradeLevel": "G2"
  }
}
```

`verdict` 取值与优先级（后者覆盖前者）：
`NOT_EVALUABLE > FRAGMENTED > MISSING_IN_APP > INCONSISTENT_COUNT > CONSISTENT`。
`diff.countVerdict` 只取题数分支（`CONSISTENT` / `INCONSISTENT_COUNT`），便于逐页筛选。
`diff` 保留全部数值，**优先级只决定一个"主判词"，不丢任何子判据**。

### 2.3 字段表与"每一项消灭什么失败"

| 字段 | 类型 | 消灭的具体失败 |
|---|---|---|
| `run.runId` / `generatedAtUtc` | str | 两次判门的报告混在一起，无法判断哪份更新 |
| `run.tool.sha256` | str | 判门脚本被改过后，旧裁定被当新裁定引用 |
| `inputs.golden.sha256` / `app.sha256` | str | 裁定无法绑定到具体输入字节；换文件后旧结论仍被引用 |
| `inputs.app.snapshotIdentitySha256` / `payloadSha256` | str/null | 两个不同的设备快照被当成同一份（KD-81 同类）；缺失写 `null`，不猜 |
| `inputs.app.dbUserVersion` | int/null | 不知道导出产自 schema 70 还是 72/73；split 表结构随 schema 变，跨版本比较无意义 |
| `inputs.evidenceGradeLevel` | `G0–G5` | 门值被当成"人工正确率"引用（见 §3） |
| `inputs.thresholds` | obj | 阈值被事后改动而无对照（`GATE.md:12-13` 要求） |
| `pageId` + `pageIdKind` | str | 只靠文件名 join；合成页名（`job:<id>#p<n>`）与金标文件名混为一谈 |
| `appRef.jobId/pageIndex/nameSource` | str/int/str | 这一页的框来自哪个导入 job、页名是映射来的还是合成的，事后不可考 |
| `appRef.pagePresent` | bool | 把"页面存在但 0 框（漏拆）"与"导出里没有这页（缺页）"混为一谈（当前 4C 导出正是前者） |
| `gold.boxesSha256` / `app.boxesSha256` | str | 裁定只绑定文件名，不绑定几何；同名页换了框仍复用旧裁定 |
| `diff.countDelta` / `countVerdict` | int/str | 判词没有数字支撑，复核者只能重跑 |
| `diff.iouByGoldIndex` / `iouOkCount` | array/int | ①"一致"掩盖了边界质量（本例 4/4 一致但 1 题 IoU 0.4751 < 0.5） |
| `diff.fragmentPiecesByGoldIndex` / `fragmentedGoldIndices` | array | 切残只报总数，定位不到是哪道题被切碎 |
| `diff.unmatchedAppBoxCount` | int | **现有实现丢弃未配对应用框**（`_page_metric` 只保留配对结果，`measure_split_accuracy.py:309-313`）：应用多切出一块、又不与任何金标题重叠 ≥0.5 时，今天完全不进任何数 |
| `ruling.decidedBy.kind/agentId/ruleId` | enum/str | 机器判定被读成人工裁定；或反过来，人工改动被脚本覆盖 |
| `ruling.redLineHit` | bool | 切残红线（`GATE.md:42`）在报告里不显式，只能靠读者自己从 0.05 反推 |
| `ruling.evidenceGradeLevel` | `G0–G5` | 文件级等级被当成"每页都人工看过" |

**刻意不加的字段**（避免过度设计）：
- 不加数值 `confidence`：门裁定是确定性计算，写 0.87 之类的"置信度"是伪精确。人做裁定时用
  `confidence: "single" | "adjudicated"` 这类离散值（在 golden v2 里）。
- 不加自由文本 `notes` 到每页：自由文本不可校验；需要说明时另开 `remarks[]`（带作者与时间）。
- 不加墙钟到 app-split 导出（见 §4.6）；裁定记录则必须有时间，两者规则不同。

### 2.4 落盘格式与不变量

- 路径：`build/split-eval/rulings/<runId>.json`（不覆盖；已存在即拒绝，沿用
  `check_split_gate.py:397-408` 的"一次运行一份"纪律）。
- 序列化：`json.dumps(..., ensure_ascii=False, sort_keys=True, indent=2) + "\n"`，UTF-8 无 BOM、LF。
- 不变量（应写成测试）：
  1. `len(rulings) == 金标页数`；
  2. 每页 `gold.questionCount == len(iouByGoldIndex)`；
  3. `iouOkCount == count(x >= 0.5 for x in iouByGoldIndex)`；
  4. `fragmentedGoldIndices == [i for i,c in enumerate(fragmentPiecesByGoldIndex) if c >= 2]`；
  5. `gate.passed == (countExact >= 0.9 and fragment <= 0.05)`；
  6. 同输入重跑，`rulings` 逐字节一致（`runId`/`decidedAtUtc` 除外——它们是运行身份，不是内容）。

### 2.5 与现有脚本的接缝

- `check_split_gate.py:241` 已有 `report["perPage"] = measure_report.get("pages")`，字段
  （`file/goldQuestions/appBoxes/consistent/reason/iouOk/fragmentedCount/appNameSource/appJobId/appPageIndex`）
  足够生成 80% 的裁定记录。**缺**：`boxesSha256`、`unmatchedAppBoxCount`、`iouByGoldIndex`
  （`matchedIous` 已有，改名映射即可）、`pagePresent`（`appPageMissing` 的反面已有）。
- 因此 item 30 的实现应**扩 `_page_metric` 的返回**，而不是在门脚本里重算一遍——单一事实源
  （`measure_split_accuracy.py:299` 的注释已声明"唯一实现"）。

### 2.6 验收判据（item 30）

1. schema 校验器（新脚本或 `validate_*` 扩展）对 `rulings.json` 做 2.4 的六条不变量；
2. 负控：手工改一个 `iouOkCount` → 校验必须红；
3. 负控：把 `decidedBy.kind` 从 `automated` 改成 `human` 而无 `agentId` → 校验必须红；
4. 同一输入重跑两次 → `rulings` 除运行身份字段外逐字节一致。

---

## 3. 二、证据等级分层与门值含义

### 3.1 两套"证据等级"不可混用

| 轴 | 回答的问题 | 取值 | 存在位置 |
|---|---|---|---|
| 陈述可信度 | 这句话有没有来源 | `【V】/【P】/【I】/UNVERIFIED` | 研究文档正文 |
| 数据集标签来源 | 这个框是谁标的、复核到什么程度 | `G0–G5` | 金标文件 + 门报告 |

现有 `evidenceGrade` 字符串是第二轴的**自由文本**，只能靠 C2 常量防改写
（`validate_split_golden.py:138-140`），**不能机检等级**。这是 item 31 要补的第一件事。

### 3.2 标签等级阶梯（G0–G5）

| 级别 | 定义 | 必须随产物登记的字段 | 可以支持的断言 |
|---|---|---|---|
| **G0** | 未标注 | — | 无 |
| **G1** | 单次模型标注，无独立复核 | `labelers[0]: {kind:model, provider, modelId, modelVersion, promptSha256, ranAtUtc}` | "某模型在某提示词下这样切"，**不能**当正确率 |
| **G2** | 两次独立模型标注一致（**当前 v1**） | 两个 labeler 记录 + 独立性证据（不同会话/版本、互不可见）；`agreement` | 代理金标上的门值；**红可阻断** |
| **G3** | 一名人工逐条核过（可改框），复核比例 100% | `review: {humanId, guidelineVersion, reviewedAtUtc, changes}` + 每页 `reviewedBy` | "单人核验金标"；绿可进人工在环试点 |
| **G4** | ≥2 名人工独立标注 + 分歧裁定 + 报告一致性 | `labelers[2+]`、`adjudication: {method, adjudicatorId, role, guidelineVersion, decidedAtUtc}`、`agreement: {metric, value, subset}` | "人工双标金标"；绿可作自动化裁定的输入 |
| **G5** | G4 + 领域专家裁定 + 指南冻结 + 训练/开发/验收切分 | G4 全部 + `split: {dev, acceptance}` + 专家资质 + held-out 未参与调参的证据 | 可在**该样本范围内**对外表述准确率 |

升级/降级规则：

- **升级只能靠新增独立证据**。"重跑一次模型，两次一致"永远停在 G2，不升 G3。
- **降级触发器**（任一命中降一级）：标注员身份不可考；独立性不可证；指南升版后未重标；
  人工改过框但 `changes` 未记；验收集参与过调阈值。
- **红可下传，绿不可上推**：
  - 任何级别上的 **FAIL 都阻断**——在 G2 上红，说明系统连"廉价标准"都没过；
  - 任何级别上的 **PASS 只在该级允许的范围内成立**——G2 的绿不等于 G3 的绿。
  - 限定：红的"可下传"是**操作上的保守选择**（阻断的代价小于放行），不是断言"金标一定对"。
    金标本身可能错，所以 FAIL 的复核入口（逐页裁定 + 人工抽检）不能省；PASS 才需要等级支撑。

### 3.3 门值在不同等级下的含义（item 31 的判据表）

| 等级 | FAIL 的含义 | PASS 的含义 | 门后自动化 |
|---|---|---|---|
| G0/G1 | 不可判（无金标/只有单模型）→ `NOT_EVALUABLE` | 无 | 禁止 |
| **G2（现）** | 系统未达代理金标 → **阻断** | 达到代理标准，**仅可进入人工复核队列** | **禁止**（4C③ 未裁） |
| G3 | 未达单人核验标准 → 阻断 | 可进"人工在环"试点，仍需产品裁定 | 禁止（需 4C③） |
| G4 | 阻断 | 可作自动化产品裁定的输入 | 仍需 4C③ + held-out |
| G5 | 阻断 | 可在样本量范围内对外表述 | 仍需 4C③ |

**等级不改变门值**。0.9/0.05 仍是"立项自定"；等级改变的是"这个门值能推出什么"。
改门值走另一条裁定（留旧值对照），不得用"升级金标"顺手改阈值。

### 3.4 当前 v1 的实际等级与两处不可复算

- 等级：**G2**，字符串 `model-labeled, no human expert; two independent labelers agreed`
  （`validate_split_golden.py:63`，C2 冻结）。
- 但冻结产物里**没有逐页 `labelers` 记录**，所以"两名标注员独立一致"这句**无法从产物复算**：
  哪两个标注员、什么模型/提示词版本、是否真的互不可见——全部 UNVERIFIED。
  C2 只能证明这行字**没被改过**，不能证明这行字**是真的**。
- 第二处：`sampleRule` 只记录了 155→24 的抽样，**24→7 的入集准则不在产物里**；
  且自承 24 张里只有 2 张被人眼打开过。因此 7 页不是 24 页的概率样本，更不是 155 页的概率样本。
  **0.5714 不能外推为总体准确率**（这也是 §5.5 范围声明的依据）。

这两处不是"造假"，是**登记粒度不足**；修法是 golden v2 的结构化字段，不是改 v1。

另：若 v2 核实"两名 labeler"实际是人工，则：**无裁定记录时按 G3 保守处理**（并存的分歧无法消解），
有裁定记录且一致性已报时才可记 G4；不得沿用 G2，也不得跳过裁定直接记 G4。

### 3.5 升到人工层需要的最小协议（提案，未执行）

标注指南必须先冻结版本（`guidelineVersion`），逐项覆盖规范 `:165` 的八项属性：
科目、题型、公式/图、手写、分页/跨页、题块边界、完整选项、附图归属。

1. **标注单元与题型**：一页 = 一个 `pageId`；框 = 题干区域；题型（选择/填空/解答…）逐页登记，
   因为"选项是否入框"随题型不同。
2. **单题页规则**：金标 `questionCount < 2` 时，期望"不拆分"（与现有口径 v2 一致，
   `measure_split_accuracy.py:277-287`）。
3. **跨页题**：跨页时在哪一页记框、是否允许一题多框，必须写死并逐页标 `crossPage`。
4. **边界规则**：框贴题干外缘还是含页眉/页码/红笔批注；用 3 个正例 + 3 个反例锚定。
5. **附图归属**：图属于哪道题，按"图题核对"口径单独登记，不并入题干框。
6. **手写/公式**：是否影响边界，逐页标 `hasHandwriting` / `hasFormula`。
7. **分层报告**：门值必须能按 `subject / layout / crossPage / hasFigure` 切片报，
   否则一个总体 PASS 可能掩盖某一层全灭。

样本量与一致性（提案值，均为**立项自定**，不是行业标准）：

- 模型-人工一致性验证：**≥30 个样本、≥3 名人工**，用留一法比较"模型与其余人"的一致 vs
  "被留出的人与其余人"的一致（AI Singapore 框架，`ω ≥ 0.5` 才认为模型可替代人工）【V】。
- 双人标注一致性：报告**原始一致率 + 一个机会校正统计量**；对框类标注，另报
  "逐题 IoU≥0.5 配对一致率"与"逐页题数完全一致率"。
- **不引用通用 kappa 分档当验收线**：文献明确指出 kappa 的 poor/fair/moderate/substantial/almost perfect
  分档"not universally accepted"，且分割/结构化标注"lack standard interpretive scales"
  （arXiv 2603.06865）【V】。本项目的验收线必须自己立项并留旧值对照。
- 若将来要建人工标注 UI，**优先复用开源**而不是自研：Label Studio（Apache-2.0，28.6k★，
  2026-10-30 仍在推送）、INCEpTION（Apache-2.0，729★，2026-10-31 仍在推送）——license 与维护度
  由 `gh api` 本会话核实；裁定（adjudication）工作流的定义可参照 Potato 的 adjudication mode
  （"designated reviewers resolve disagreements … produce gold-standard final labels"）【V】。

### 3.6 人工层的记录放哪（golden v2，不是 rulings）

v2 是**新文件 + 新旁车**（`README.md:121-124`），v1 冻结不动。根级新增：
`evidenceGradeLevel`、`guidelineVersion`、`agreement{metric,value,subset,chanceCorrected}`、
`split{train,dev,acceptance}`、`labelingProtocolVersion`。页级新增：
`strata{questionType,hasFormula,hasFigure,hasHandwriting,crossPage,optionsIncluded,figureOwnership}`、
`labelers[]`、`adjudication{}`、`review{}`、`evidenceGradeLevel`。

C2 的机制要按 `schemaVersion` 分派常量（v1 一套、v2 一套），**不得放松 v1 的冻结常量**——
"提升等级"只能出新文件，不能改旧文件里的字符串。

---

## 4. 三、app-split 生产者格式（真实设备库快照）

### 4.1 现有格式（v1，逐字段）

`export_split_eval.py:301-318` 产出：

```
schemaVersion: 1
source:
  db: {file, sha256, bytes}                     # 主库字节摘要（不是快照身份）
  snapshot: {identityKind:"inputs-v1", identitySha256, inputs:{db,wal}, walPresent}
  payload: {sha256, pages, boxes}               # 内容身份
  pageMap: {file, sha256, bytes} | null
  queries / readTransaction                     # 读了什么、怎么读的
  nameSourceCounts: {…}
pages[]: {file, nameSource, jobId, pageIndex, questionCount, selectedCount, confirmStates, questions[]}
```

已有优点（不要回退）：快照身份 db+wal（`:107-136`）、显式 `BEGIN` 钉住一个读事务（`:231-234`）、
导出前后比对且先于写盘（`:288-296`）、重复页名/非法框 exit 2（`:276-283`、`:189-201`）、
不覆盖已有输出（`:340-341`）。

### 4.2 缺口清单（每一项都能指到一次真实失败）

| # | 缺口 | 会导致什么 |
|---|---|---|
| G-1 | **无 DB schema 版本** | 不知道导出产自 schema 70/71/72/73；split 表若迁移过，跨版本比较无意义 |
| G-2 | **无 job 级来源** | `split_import_job` 有 `source_kind/source_fingerprint/status/created_at`（`SplitImportMigration.kt:19-29`），导出只取了 `source_uri/status/page_count`；同一个文件名可能来自不同 job，无法区分 |
| G-3 | **无稳定 pageId 字段** | 现在靠 `file`；合成名 `job:<id>#p<n>` 与金标文件名混在同一列 |
| G-4 | **无 job 记录题数 vs 实际导出的一致性** | job 记 6 题、导出 3 题时静默通过（只按行分组） |
| G-5 | **无生产者身份** | `schemaVersion:1` 不标识代码版本；同版本号两版脚本输出不可区分 |
| G-6 | **无 `snapshot.mode`** | 分不清这份导出是"三件原样拷贝"还是"VACUUM INTO 逻辑快照"；两种身份的语义不同 |
| G-7 | **无逐页 boxes 摘要** | 裁定记录无法绑定几何（见 §2.3） |

### 4.3 建议 v2 格式（只做加法，`schemaVersion` 升 2）

（下面是**片段**，只显示新增/变更的键；完整可解析示例见附录 B。）

```json
"source": {
  "producer": {"file": "export_split_eval.py", "sha256": "<64-hex>", "contract": "app-split-v2"},
  "db": {"file": "smart-mistake-book.db", "sha256": "…", "bytes": 12775424, "userVersion": 72},
  "snapshot": {"identityKind": "inputs-v1", "identitySha256": "…", "mode": "three-file",
               "inputs": {"db": {…}, "wal": {…}|null}, "walPresent": true},
  "payload": {"sha256": "…", "pages": 3, "boxes": 11}
},
"pages": [{
  "pageId": "<job_id>#p<page_index>", "pageIdKind": "job+page",
  "file": "IMG….jpg", "nameSource": "batch_page_source_uri",
  "job": {"jobId": "…", "sourceKind": "…", "sourceFingerprint": "…", "status": "…",
          "pageCount": 3, "questionCount": 11,
          "createdAtEpochMillis": 0, "updatedAtEpochMillis": 0},
  "integrity": {"jobQuestionCountMatches": true, "jobPageCountMatches": true},
  "boxesSha256": "…",
  "questionCount": 11, "selectedCount": 9, "confirmStates": {"…": 2},
  "questions": [ … ]
}]
```

字段取舍：

- `db.userVersion`：`PRAGMA user_version`，Room 以该 pragma 持久化 schema 版本（现树
  `STUDY_DATABASE_VERSION = 72`，`StudyDatabase.kt:140`）。【I】本会话**未**在真实设备库上实读该值，
  落地时须先跑一次 `PRAGMA user_version` 核对。**只记录，不据此拒绝导出**；拒绝属门的事（§5）。
- `job.*`：来自 `split_import_job`（列名见 `SplitImportMigration.kt:19-29`），全部是输入派生值，
  不破坏"同快照复跑逐字节一致"。
- `integrity.*`：两个布尔，比对 `job.question_count` 与 `COUNT(split_import_question)`、
  `job.page_count` 与 `COUNT(DISTINCT page_index)`。不匹配**不 exit**（生产数据可能合法地缺页），
  但必须显式落盘，由门决定怎么读。
- `snapshot.mode`：`"three-file"`（当前）或 `"logical-vacuum-into"`（将来）。
- `boxesSha256`：`sha256(canonical(boxes))`，与 §2.2 同一口径（`json.dumps(sort_keys=True,
  separators=(",",":"))`）。
- **不加墙钟**：任何时间字段都会让"同快照两次导出"不再逐字节一致（`README.md:100-101` 的既有约束）。

兼容策略：`measure_split_accuracy.py` 只读 `pages[].file/questions`，加法字段不影响；
`_describe_app_source`（`:403-412`）只读 `db.sha256/nameSourceCounts/pageMap`，同样不受影响。
但报告里要新增 `appSchemaVersion` 与 `dbUserVersion`，否则门报告仍看不出输入版本。

### 4.4 真实设备库快照：取库模式与失败面

事实（SQLite 官方原文）【V】：

- WAL 里的已提交事务**不在主库**："A COMMIT occurs when a special record indicating a commit is
  appended to the WAL"；"A COMMIT can happen without ever writing to the original database"。
- **主库与 WAL 分离会丢已提交事务或损坏库**："If a database file is separated from its WAL file,
  then transactions that were previously committed to the database might be lost, or the database
  file might become corrupted."
- 读事务钉住一个时间点："for any particular reader, the end mark is unchanged for the duration of
  the transaction, thus ensuring that a single read transaction only sees the database content as it
  existed at a single point in time." —— 这正是导出器显式 `BEGIN` 的依据（`export_split_eval.py:231-234`）。
- `PRAGMA wal_checkpoint(TRUNCATE)`："checkpoints all frames in the log file … the WAL file is
  truncated to zero bytes upon successful completion"，并返回"是否被阻塞"的标志。
- `VACUUM INTO`："the generated output database is a consistent snapshot of the original database"；
  版本 3.27.0（2019-02-07）引入。
- 在线备份 API："make the destination a bit-wise identical copy of the source database as it was
  when the copying commenced. (The destination becomes a 'snapshot.')"；外部写入会导致备份重启。
- Android Room 默认 `JournalMode.AUTOMATIC`：API ≥16 且非低内存设备时用 WAL【V，官方 API 参考】。

三种取库模式：

| 模式 | 做法 | 能保证 | 不能保证 |
|---|---|---|---|
| **A. 三件同时刻**（现用，`README.md:66-79`） | `adb exec-out run-as <pkg> cat databases/{db,db-wal,db-shm}` 各一次 | 若三次之间无写入，字节可重建该时刻状态 | 三次之间有写入时，WAL 可能来自更晚时刻并被重放到更早的主库上；**SQLite 不会把这种"跨时刻"判成错误**（salt 只在 WAL 头与帧头之间比对，不与主库比对），结果是静默读到错误状态或损坏 |
| **B. 先停应用再取** | `adb shell am force-stop <pkg>` 后按 A 取三件 | 减少取库窗口内的写入 | force-stop 是杀进程，SQLite 的 close 不一定执行（【I】）⇒ WAL 可能仍在；仍必须三件全取 |
| **C. 逻辑快照**（宿主） | 对 A/B 取到的快照跑 `VACUUM INTO 'snap2.db'` | 单文件、单事务一致、去掉 WAL 依赖；身份=产物 sha | **不能修复已被撕开的 A/B 快照**——它只对"读到的状态"做一致化；宿主 `sqlite3` 必须 ≥3.27 |

否决的路线：**在设备库上直接 checkpoint**——它需要可写连接，违反"DB 只读"红线（`export_split_eval.py:41-47`），
且会改设备数据。与既有 `-shm` 排除（`README.md:99-100`）同一逻辑：不碰会自指的派生文件。

补充事实（这是 KD-81 的原始教训）：批次 31 的真设备导出 `device-5562/app-split.json` 里，
`source` 只有 `db`，没有 `snapshot`/`payload`；且导出 0 页。所以"真实设备库快照"在仓内**只有一份
可用样本，且它没有 split 数据**。item 29 的"增强"不可能靠现有样本验收，必须新取一份。

### 4.5 生产者 fail-closed 清单（现有 + 新增）

| 条件 | 现有行为 | 建议 |
|---|---|---|
| DB 不存在 / 缺 split 表 | `DataError` → exit 2（`:210-211`、`:244-246`） | 保持 |
| 非法框 / 重复页名 | exit 2（`:189-201`、`:276-283`） | 保持 |
| 导出期间 db/-wal 变化 | exit 2，且先于写盘（`:288-296`） | 保持 |
| 输出已存在 | 拒绝（`:340-341`） | 保持 |
| **`user_version` 未知/过新** | 无 | **新增**：记录 `db.userVersion`；门侧遇到未知版本 → `NOT_EVALUABLE`（不是"当作兼容"） |
| **job 记录题数 ≠ 导出行数** | 无（静默） | **新增**：`integrity.jobQuestionCountMatches=false`，仍 exit 0，但门报告必须显式列出 |
| **`-wal` 缺失但 `journal_mode=wal`** | 只记 `walPresent=false` | **保持如实**：工具无法证明"曾经有 WAL 被漏拷"（`README.md:107-108`）；不得据此推断"无未 checkpoint 行" |

### 4.6 复跑与确定性

- 同快照两次导出**逐字节一致**：无时间戳、无 mtime、无 `-shm`、无随机序（`:266-277` 排序 + `:347-350`）。
- 裁定记录（`rulings.json`）**相反**：必须有 `runId/decidedAtUtc`，且**不覆盖**——两类产物的确定性要求不同，
  不要用同一条规则套。
- `payload.sha256` 是"内容身份"：换物理布局/是否 checkpoint 不改变它；判断"两次是否同一内容"看它，
  不看文件字节（`README.md:90-95`）。

---

## 5. 四、门仍 FAIL 时的安全口径

### 5.1 三值逻辑，只有 PASS 能往下走

`check_split_gate.py:36-41` 定义四类退出码，归并成三种语义：

| 状态 | exit | 含义 | 允许的下一步 |
|---|---|---|---|
| `PASS` | 0 | ①≥0.9 且 ③≤0.05 | 进入下一道**产品裁定**（4C③），仍不得自动改任何开关 |
| `FAIL` | 1 | 度量完成、阈值未达 | 阻断；门后自动化不启用 |
| `NOT_EVALUABLE_*` | 2/3 | 冻结坏/数据坏/导出缺失 | 阻断；**不得把"不可判"读成"未发现问题"** |

`automation.enabled` 的取值在仓内是**字面量 `False`**，三处出现、无一处置 `True`
（`check_split_gate.py:160`、`measure_split_accuracy.py:434,463`）。这不是 convention，是可 grep 的事实。
另：本会话用 `grep -rniE 'split.?gate|gate.?split|qualityGate|autoIngest|auto_ingest'` 扫
`app core feature` 的 `.kt/.kts`，**1 命中且无关**（`MasteryOverviewInstrumentedTest.kt:124` 的测试方法名）。
注意 `GATE.md:16` 写"grep 0 命中"——按本会话口径应写"无门控开关实现；`自动入库` 一词在
`CaptureAcquisitionCommands.kt:161` 的注释里出现过一次（A7 分享路径，与切分门无关）"。这是 item 33 的台账措辞修正点。

### 5.2 报告层禁止误印 PASS（跨线先例）

KD-76 的教训：主口径 `M = S-bind + S-warmup` p95 = 9813 ms > 5000 ms 为红，但旧用例只对
`S-warmup` 单段施门并打印 `… PASS`；台账明确该判词"口径错误、不得再作为通过证据"，
并改为 n 不足或上游红时只打印 `NOT EVALUABLE`（`docs/known-defects.md:2928`）。

**可执行的规则**（适用于 AUD-08 报告与证据）：

1. 总结论行只能出现 `PASS` / `FAIL` / `NOT EVALUABLE` 三者之一，且必须与 `gate.state` 一致；
2. `gate.state != PASS` 时，**任何被读者可能当作"通过证据"的分量行不得含 `PASS` 字样**；
   子步骤（如"冻结校验 PASS"）可以保留，但必须带 `子步骤` 限定词，且不得出现在总结论之后；
3. 应写成断言（`tools/tests/test_split_gate.py` 增一例）：门红时，stdout 与证据 md 中
   匹配 `p95.*PASS` / `门.*PASS` / `准确率.*PASS` 的行数为 0。

### 5.3 红线与门式的已知张力（必须先裁，不是实现细节）

`GATE.md` 同时写着：

- 门式 ③：切残率 `≤ 0.05`（`:11`、`:39`）；
- 红线：切残题干**不得入库（无论门值如何）**（`:42`）。

算术后果（本会话复算）：

- n=16（金标总题数）时 `1/16 = 0.0625 > 0.05` ⇒ 0.05 实际等价于"0 道切残"，两者**当前不冲突**；
- 金标总题数 n≥20 时 `1/20 = 0.05 ≤ 0.05` ⇒ **1 道切残题会通过门式**，与红线直接冲突。

因此：**在扩样本（或任何金标总题数 n≥20 的金标）之前，必须裁定"自动化决策是否 AND 一个独立的
`fragmentedQuestions == 0` 硬红线"**。建议（不改现有门值、不改当前 FAIL）：
门报告新增 `hardRedLines: {fragmentedQuestions: 0, satisfied: bool}`，任何"门后自动化"讨论
都必须同时满足 `gate.passed && hardRedLines.satisfied`。这属于 4C③ 产品裁定面，工具只报不算。

### 5.4 PASS 也只是必要非充分

即使门变绿，以下四项仍各自独立阻断自动化：

1. **held-out 未做**：当前单集既调门又验门（`GATE.md:88-92`；规范 `:165`）。
2. **等级是 G2**：代理金标，不是人工金标（§3.4）。
3. **样本不可外推**：7 页不是 24 页的概率样本（§3.4）；① 的 Wilson 区间 0.2505–0.8418 宽到无法支撑总体断言。
4. **无产品裁定**：4C③ 从未立项；仓内无门控开关（§5.1）。

### 5.5 当前 FAIL 的适用范围（写进报告与台账）

> 本次 FAIL 成立于：金标 `f9e92c47…`（7 页/16 框，G2）× 应用导出 `9b49b0c4…`（1,482 B，4C 存量，
> **无 source 块、设备/DB 版本 UNVERIFIED**）。它**不**等于"当前设备库切分准确率 0.5714"，
> 也**不**等于"当前模型在 155 页数据集上的准确率"。

这条范围声明必须出现在 `GATE.md`、门证据的 UNVERIFIED 段、以及任何引用 0.5714 的台账行里。
现有证据文件（`docs/research/evidence/2026-10-29-batch-30/step-aud08-page-ruling.md:48-51`）
已写"应用导出来源块缺失/UNVERIFIED"，但**没有**写"样本不可外推"——这是 item 33 要补的一句。

### 5.6 FAIL 时的产品行为（不变）

- 切残不得自动入库；门不过不启用门后自动化（裁定 37 / 4C③）。
- 失败页有"继续补拍本题"出口：`feature/capture/.../CaptureModelTaskCard.kt:124`（本会话实读该行文本）。
- 逐题确认入库的既有路径不受门值影响（`GATE.md:31`）。

### 5.7 禁止清单（写进 GATE.md 的"不得"段）

1. 不得为让门变绿改阈值/口径/分母/金标（`GATE.md:12-13`）。
2. 不得用 `NOT_EVALUABLE` 代替 `FAIL` 上报，也不得把 `NOT_EVALUABLE` 当"未发现问题"。
3. 不得在门红时打印任何门级 `PASS` 判词（§5.2）。
4. 不得覆盖既有证据/报告文件（脚本已拒绝，`:397-408`、`:463-465`）。
5. 不得用旧导出的门值代表新设备/新模型（§5.5）。
6. 不得在未裁定 `fragmentedQuestions == 0` 硬红线前，用金标总题数 n≥20 的金标开自动化讨论（§5.3）。

---

## 6. 给 WS5 五项的实施映射（可验收）

| item | 交付物 | 本文依据 | 验收锚 |
|---|---|---|---|
| **29** app-split 生产者增强 | `export_split_eval.py` 增 `producer/db.userVersion/job/integrity/boxesSha256/snapshot.mode`；新增 `rulings.json` 生成入口 | §4.3、§2.5 | 真设备快照导出含上述字段；同快照复跑逐字节一致；`test_split_gate.py` 37→新增用例全绿 |
| **30** 逐页裁定 schema | `rulings` schema + 六条不变量校验器；`_page_metric` 补 `unmatchedAppBoxCount`/`iouByGoldIndex` | §2.2–§2.6 | 负控三例（改数/改 kind/改摘要）全红 |
| **31** 证据等级字段与门脚本扩展 | golden v2 的 `evidenceGradeLevel/labelers/adjudication/agreement/split/strata`；门报告读等级而**不再硬编码** | §3.2–§3.6 | `check_split_gate.py:379-380` 的硬编码字符串改为从 golden 读；v1 仍 G2、C2 常量不放松 |
| **32** 门值复算 | 实跑维持 FAIL；报告加 `inputs.evidenceGradeLevel` 与 §5.5 范围声明 | §1.3、§5.5 | exit 1；① 4/7；阈值零改动 |
| **33** 审计门文档与台账更新 | `GATE.md`（§0 范围声明、§2 等级、§5.3 红线张力、§5.7 禁止清单）、`README.md`（rulings 数据流、v2 冻结规则）、KD/MASTER-LINE AUD-08 行 | §5.2、§5.7 | 文档逐条可 grep；台账措辞按 §5.1 更正（不写"grep 0 命中"） |

---

## 7. 未验证 / UNVERIFIED（如实登记）

| 项 | 状态 | 原因 |
|---|---|---|
| 设备取库与导出复跑 | **本轮未执行** | 本会话零 adb、零 Gradle；§4.4 的命令来自 `README.md:66-79` 与批次 31 记录，不是本轮实测 |
| `adb run-as` / `exec-out` 官方原文 | **未取到** | `developer.android.com` 本会话四次连接超时（WebFetch 三次、curl 一次，均 10–12 s 超时）；改用仓内 README 与批次 31 记录，属【P】 |
| "两名标注员独立一致" | **UNVERIFIED（不可从产物复算）** | 冻结产物无逐页 labeler 记录（§3.4） |
| 24→7 入集准则 | **UNVERIFIED（未记录）** | `sampleRule` 只写到 24 张；选择偏差不可评估 |
| 模型标注在本数据集上的错误率 | **UNVERIFIED** | 文献 1.40%–16.60%（均值 8.93%）来自 10 个**其他**数据集（Klie et al. 2024），不能移植 |
| Android Room WAL 默认 | 【V】但**经搜索摘要读取** | 官方 API 页无法直连；`developer.android.com` 摘要 + `source.android.com` 兼容 WAL 页互证 |
| 人工标注协议 | **提案，未执行** | 无标注员参与；§3.5 的样本量/一致性线均为立项自定 |
| `VACUUM INTO` 对只读源库的行为 | **未验证** | 官方页只写 `VACUUM (but not VACUUM INTO) is a write operation`，未明确只读源；落地前需实测 |
| 门后自动化 | **不存在** | 仓内无门控开关；本会话 grep 结果见 §5.1 |

---

## 8. 来源

### 8.1 仓内一手（本会话实读/实跑）

| 文件 | 用途 |
|---|---|
| `tools/split_golden/tables/split_golden_v1.json` | 冻结金标与 `sampleRule`/`evidenceGrade` |
| `tools/split_golden/validate_split_golden.py:60-70,138-140,244-272` | C1–C6、冻结常量 |
| `tools/split_golden/export_split_eval.py:41-47,101-136,159-186,189-201,208-318,340-341` | 生产者口径与身份 |
| `tools/split_golden/measure_split_accuracy.py:69-75,277-287,299-339,403-412,426-458` | 度量口径与逐页计算 |
| `tools/split_golden/check_split_gate.py:36-41,156-165,235-242,252-264,356-380,397-408,411-413` | 三值逻辑、门式、证据渲染 |
| `tools/split_golden/GATE.md:11-19,28-43,80-92` | 门值与红线、样本与切分、冻结纪律 |
| `tools/split_golden/README.md:10-16,55-64,66-116,119-125` | 数据流、取库纪律、身份三字段 |
| `tools/tests/test_split_gate.py` | 37 个测试（`grep -c` 实测） |
| `core/database/.../SplitImportMigration.kt:19-52` | `split_import_job` / `split_import_question` 列 |
| `core/database/.../StudyDatabase.kt:140,325` | schema 72、库名 |
| `app/build.gradle.kts:27,54` | `applicationId` + `.offline` |
| `docs/research/2026-10-11-new-stages-spec.md:162-166` | 4C 标注要求（八项属性、训练/开发/验收、来源分记） |
| `docs/current/visual-generation-contract.md:208` | 门现状与 KD-81 身份边界 |
| `docs/known-defects.md:2928,3131-3179` | KD-76 判词教训、KD-81 关闭口径 |
| `docs/research/2026-11-02-batch-40-baseline.md:139-150,208-223` | WS5 现状与计划偏差 |
| `docs/research/evidence/2026-10-29-batch-30/step-aud08-page-ruling.md` | 上一轮证据形态 |
| `build/split-eval/app-split.json`、`build/split-eval/device-5562/app-split.json` | 两种导出形态实测 |

实跑命令：

```bash
python tools/split_golden/validate_split_golden.py          # exit 0
python tools/split_golden/measure_split_accuracy.py --json  # exit 0
python tools/split_golden/check_split_gate.py               # exit 1（FAIL）
python -m unittest discover -s tools/tests -t tools -k split_gate  # 37 tests OK，exit 0
```

### 8.2 外部（联网）

1. SQLite, *Write-Ahead Logging* — https://www.sqlite.org/wal.html 【V】
2. SQLite, *VACUUM*（含 `VACUUM INTO`）— https://www.sqlite.org/lang_vacuum.html 【V】
3. SQLite, *Release Log 3.27.0*（2019-02-07）— https://www.sqlite.org/releaselog/3_27_0.html 【V】
4. SQLite, *Using the SQLite Online Backup API* — https://www.sqlite.org/backup.html 【V】
5. SQLite, *PRAGMA*（`wal_checkpoint` / `journal_mode`）— https://www.sqlite.org/pragma.html 【V】
6. SQLite, *Database File Format*（WAL header/frame salt 的定义与失效语义）— https://www.sqlite.org/fileformat2.html 【V】；salt 在 WAL 头与帧头之间比对，**不与主库比对**
7. Android Developers, `RoomDatabase.JournalMode` — https://developer.android.com/reference/androidx/room/RoomDatabase.JournalMode 【V，经搜索摘要】
8. AOSP, *Compatibility WAL* — https://source.android.com/docs/core/perf/compatibility-wal 【V】
9. W3C, *PROV-O: The PROV Ontology*（Entity/Activity/Agent 与 wasGeneratedBy/wasAttributedTo/wasDerivedFrom）— https://www.w3.org/TR/prov-o/ 【V】
10. J.-C. Klie, R. Eckart de Castilho, I. Gurevych, *Analyzing Dataset Annotation Quality Management in the Wild*, Computational Linguistics 50(3):817–866, 2024, DOI 10.1162/coli_a_00516 — https://direct.mit.edu/coli/article/50/3/817/120233/ 【V】；关键数字：591 篇中 314 篇提到人工标注/核验、277 篇没有；自动标注数据中仅 16/293 用了人工核验；人工标注论文 41% 报告了校验步骤；裁定方式 majority voting 34%、manual curation 14%、probabilistic 1%、unknown 46%；一致性 52% 报告、34% 未说明；报告的错误率 1.40%–16.60%（均值 8.93%，中位 8.55%）
11. AI Singapore, *Validating Annotation Agreement between Humans and LLMs* — https://blog.ai.gov.sg/validating-annotation-agreement-between-humans-and-llms/ 【V】；≥30 样本、≥3 名人工、留一法、FDR 0.05、`ω ≥ 0.5`
12. *Counting on Consensus: Selecting the Right Inter-annotator Agreement Metric* — https://arxiv.org/html/2603.06865 【V】；kappa 分档"not universally accepted"、分割/结构化标注"lack standard interpretive scales"
13. *Measuring Annotator Agreement Generally across Complex Tasks* — https://arxiv.org/abs/2212.09503 【P】（只读摘要：结构化/多对象标注的 IAA 度量）
14. *Boundary IoU*（CVPR 2021）— https://arxiv.org/abs/2103.16562 【P】；标准 Mask IoU 对大目标边界误差不敏感，0.5 只是约定
15. Potato Annotation, *Adjudication* — https://potatoannotator.readthedocs.io/en/latest/administration/adjudication/ 【V】；"designated reviewers (adjudicators) resolve disagreements … produce gold-standard final labels"
16. GitHub 元数据（`gh api`，2026-11-02 本会话）：`HumanSignal/label-studio` Apache-2.0、28,599★、pushed 2026-10-30；`eclipse-inception/inception` Apache-2.0、729★、pushed 2026-10-31；`doccano/doccano` MIT、10,808★、pushed 2026-04-14 【V】

---

## 附录 A · 门裁定记录示例（真实数据，摘要截断）

```json
{
  "rulingSchemaVersion": 1,
  "run": {
    "runId": "2026-11-02T05:20:00Z-3f9a2c71",
    "generatedAtUtc": "2026-11-02T05:20:00Z",
    "tool": {"file": "check_split_gate.py", "sha256": "<64-hex>"},
    "command": ["python", "tools/split_golden/check_split_gate.py"]
  },
  "inputs": {
    "golden": {"file": "split_golden_v1.json", "sha256": "f9e92c47b399252d2e92ecd633c2a2b76a52b0bfcdcff78868a513dedd1e4c9f",
               "evidenceGrade": "model-labeled, no human expert; two independent labelers agreed",
               "evidenceGradeLevel": "G2"},
    "app": {"file": "app-split.json", "sha256": "9b49b0c474d68409eba1617b888e7cf823507341ef63f54a65d9f4d68f4c2fd6",
            "snapshotIdentitySha256": null, "payloadSha256": null, "dbUserVersion": null},
    "caliber": "v2",
    "thresholds": {"countExactMin": {"value": 0.9, "comparison": ">="},
                   "fragmentMax": {"value": 0.05, "comparison": "<="}}
  },
  "gate": {"state": "FAIL", "passed": false},
  "rulings": [
    {
      "pageId": "IMG20260810010349.jpg", "pageIdKind": "golden_filename",
      "appRef": {"jobId": null, "pageIndex": null, "nameSource": null, "pagePresent": true},
      "gold": {"questionCount": 4, "boxesSha256": "e1e4dddb28f4c53e…"},
      "app": {"boxCount": 4, "boxesSha256": "15e628008bb6728a…"},
      "diff": {"countDelta": 0, "countVerdict": "CONSISTENT",
               "iouByGoldIndex": [0.6391, 0.7057, 0.6751, 0.4751], "iouOkCount": 3,
               "fragmentPiecesByGoldIndex": [1, 1, 1, 1], "fragmentedGoldIndices": [],
               "appPagePresent": true, "unmatchedAppBoxCount": 0},
      "ruling": {"verdict": "CONSISTENT", "redLineHit": false,
                 "decidedBy": {"kind": "automated", "agentId": "script:check_split_gate.py@sha256:<12>",
                               "ruleId": "count-v2+iou-maxweight-0.5+fragment-0.5"},
                 "decidedAtUtc": "2026-11-02T05:20:00Z", "evidenceGradeLevel": "G2"}
    },
    {
      "pageId": "IMG20260810004353.jpg", "pageIdKind": "golden_filename",
      "appRef": {"jobId": null, "pageIndex": null, "nameSource": null, "pagePresent": true},
      "gold": {"questionCount": 2, "boxesSha256": "ec25f08f5b83e015…"},
      "app": {"boxCount": 0, "boxesSha256": "4f53cda18c2baa0c…"},
      "diff": {"countDelta": -2, "countVerdict": "INCONSISTENT_COUNT",
               "iouByGoldIndex": [null, null], "iouOkCount": 0,
               "fragmentPiecesByGoldIndex": [0, 0], "fragmentedGoldIndices": [],
               "appPagePresent": true, "unmatchedAppBoxCount": 0},
      "ruling": {"verdict": "INCONSISTENT_COUNT", "redLineHit": false,
                 "decidedBy": {"kind": "automated", "agentId": "script:check_split_gate.py@sha256:<12>",
                               "ruleId": "count-v2"},
                 "decidedAtUtc": "2026-11-02T05:20:00Z", "evidenceGradeLevel": "G2"}
    }
  ],
  "summary": {"pages": 7, "consistent": 4, "inconsistentCount": 3, "fragmented": 0,
              "missingInApp": 0, "notEvaluable": 0}
}
```

说明：`jobId/pageIndex/nameSource` 为 `null` 是**如实**——4C 导出没有 `source` 块与页级溯源
（`measure_split_accuracy.py:51-52`：不得由文件名反推）。`boxesSha256` 为 16 位截断显示。
**本示例只列 2 页**（一页一致、一页漏拆），`summary` 与 `gate` 是全量 7 页的结果。

## 附录 B · app-split v2 最小示例（结构）

```json
{
  "schemaVersion": 2,
  "source": {
    "producer": {"file": "export_split_eval.py", "sha256": "<64-hex>", "contract": "app-split-v2"},
    "db": {"file": "smart-mistake-book.db", "sha256": "<64-hex>", "bytes": 12775424, "userVersion": 72},
    "snapshot": {"identityKind": "inputs-v1", "identitySha256": "<64-hex>", "mode": "three-file",
                 "inputs": {"db": {"file": "smart-mistake-book.db", "bytes": 12775424, "sha256": "<64-hex>"},
                            "wal": null},
                 "walPresent": false},
    "payload": {"sha256": "<64-hex>", "pages": 1, "boxes": 2},
    "pageMap": null,
    "queries": "…",
    "readTransaction": "BEGIN … (single WAL snapshot; all SELECTs) … close",
    "nameSourceCounts": {"batch_page_source_uri": 1}
  },
  "pages": [{
    "pageId": "split:batch-1:0#p0", "pageIdKind": "job+page",
    "file": "IMG20260810003928.jpg", "nameSource": "batch_page_source_uri",
    "job": {"jobId": "split:batch-1:0", "sourceKind": "BATCH", "sourceFingerprint": "<hex>",
            "status": "CONFIRMED", "pageCount": 1, "questionCount": 2,
            "createdAtEpochMillis": 0, "updatedAtEpochMillis": 0},
    "integrity": {"jobQuestionCountMatches": true, "jobPageCountMatches": true},
    "boxesSha256": "<64-hex>",
    "questionCount": 2, "selectedCount": 2,
    "confirmStates": {"CONFIRMED": 2},
    "questions": [
      {"left": 0.05, "top": 0.10, "right": 0.95, "bottom": 0.45},
      {"left": 0.05, "top": 0.50, "right": 0.95, "bottom": 0.90}
    ]
  }]
}
```

（`job.*` 的取值是结构示例，不是真实行；真实导出应逐字段来自 `split_import_job`。）
