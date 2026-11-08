# AUD-08 acceptance「只跑一次」口径更正（批次 50 修复环）

> 本文件**不修改**任何门值、阈值、分母或判定；只更正一句会被读成全局一次性的范围文案。
> 原 `gate-report-b50.json` / `gate-evidence-b50.md` 保持原字节（它们是一次运行的记录，
> 见 `MANIFEST-SHA256.txt`）；本文件是这两份产物中该句话的**取代声明**。

## 1. 被更正的原句

`gate-report-b50.json` → `scope.reasons[1]`（`gate-evidence-b50.md:73` 同文，由
`tools/split_golden/check_split_gate.py` 的 `SCOPE_REASONS_V2` 生成）：

```text
v2 已做 dev/acceptance（16/8）切分：dev 16 页参与了切分修复调参，acceptance 8 页只复算一次；
门值不外推为未参与调参页面的准确率
```

问题：**"只复算一次"没有限定"本批"**，会被读成这 8 页从未被消费、held-out 属性成立。
事实不是这样。

## 2. 事实（一手证据）

- 批次 48 已用**同一 8 页**跑过 acceptance：
  `docs/research/evidence/2026-11-07-batch-48/splitter/README.md:34` →
  `acceptance-once-01 | A（唯一一次） | acceptance 8 | 5/8=0.6250 | 13/26=0.50 | 3/26=0.1154`。
- 两批 acceptance 导出的页名集合逐页相同（`IMG20260810003928/004353/004952/005711/010034/010202/011358/011800`），
  已在本批复核（8/8 同名）。
- 本批 `acceptance-b50-01` 的协议在运行前已如实登记这一点：
  `acceptance-protocol.md` §3.1「同一 8 页在新配置下的第二次观测；held-out 不可恢复」。
- 本批**确实只运行一次**：`acceptance-protocol.md` 写于 17:14，`runs/acceptance-b50-01/` 是唯一 run；
  未重跑换绿、未回调用参。

因此正确口径是：**本批唯一一次运行，但整组 8 页不是首次盲测，held-out 属性不可恢复。**

## 3. 取代文本（后续报告应引用这句）

```text
v2 已做 dev/acceptance（16/8）切分：dev 16 页参与了切分修复调参；acceptance 8 页在本批只运行一次
（唯一 run）。但同一 8 页已在批次 48 的 acceptance-once-01 被消费过，held-out 属性不可恢复
（本批是第二次观测）；门值不外推为未参与调参页面的准确率
```

## 4. 工具修复与影响面

- `tools/split_golden/check_split_gate.py` 的 `SCOPE_REASONS_V2[1]` 已改为上述文本
  （第 202–212 行附近），未来生成的报告不再带旧口径。
- **未重跑门**：`check_split_gate.py` 对已存在的 `--report-json/--evidence-out` 拒绝覆盖；
  本批门值仍是原来的唯一一次运行结果——① 7/24=0.2917、③ 1/78=0.0128、
  `fragmentedQuestions=1` → **FAIL**（exit 1）。本更正**不产生**第二个 gate report，
  也不消费 acceptance 第二次。
- 已同步更正：`docs/acceptance/verdicts.md`（判词与 §3 不可变项）、
  `docs/acceptance/evidence/2026-11-09-batch-50/make_verdicts_report.py`（生成器）、
  `docs/research/2026-11-09-batch-50-record.md`（修复环节）。
- 固定检查：`tools/tests/test_batch50_repair_invariants.py` 断言范围文案含「本批」与「批次 48」，
  防止旧文案回潮。
