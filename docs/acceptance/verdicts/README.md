# 逐项终裁目录（批次 44 / D0 item 5）

每个进入 `PASS` 的台账行都应有一个 `<ID>.json` 终裁文件；文件名与台账 id
一致（例如 `A-001.json`）。`FAIL` / `BLOCKED` / `UNVERIFIED` 也应留终裁或
缺口说明，便于下一批复跑。

## 文件格式

```json
{
  "id": "A-001",
  "verdict": "PASS",
  "run_id": "2026-11-06T09:00:00Z-batch44",
  "reviewer": "independent-reviewer",
  "authors": ["implementer"],
  "ledger_sha256": "<64 hex>",
  "evidence": [
    "cmd:build/acceptance/a001.log#exit=0",
    "test:build/acceptance/a001.xml"
  ],
  "evidence_sha256": {
    "build/acceptance/a001.log": "<64 hex>",
    "build/acceptance/a001.xml": "<64 hex>"
  },
  "adversarial": {"attempted": true, "result": "rejected"},
  "notes": "范围、残余、下一动作"
}
```

## 规则

- `reviewer` 不得在 `authors` 内，`self_review` 不得为 true；
- `run_id` 必须与产生证据的命令/设备轮次一致；
- `ledger_sha256` 由 `sha256sum docs/acceptance/ledger.md` 得出；
- `evidence` 与台账行一致；`evidence_sha256` 覆盖所有证据文件；
- 终裁只对单行 id 生效，不写总体“通过”；
- 人类语感/审美残余写 `UNVERIFIED` + `notes`，不得签字通过。

`_template.json` 是本目录的空模板；它不是终裁，不能作为证据引用。
