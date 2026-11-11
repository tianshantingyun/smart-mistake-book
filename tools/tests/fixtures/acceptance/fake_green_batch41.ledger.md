<!-- 历史假绿回放夹具（批次 44 / D0 item 15）。
     来源：docs/research/2026-11-02-batch-41-record.md 的 P-01 口径
     （6 个配对 run 全 15/15 PASS，但 5/6 post-gate INVALID → UNCONVERGED）。
     用途：acceptance_report.py 与 acceptance_lint.py 必须 exit 1。
     本文件不是证据，不得被任何 PASS 行引用。 -->

| ID | 域 | 检查 | 方法 | 证据 | verdict |
|---|---|---|---|---|---|
| D0-001 | D0 | 回放：post-gate INVALID 被记为通过 | device: adb shell am instrument -w VisualJsIsolationInstrumentedTest | cmd:tools/tests/fixtures/acceptance/fake_green_batch41.log#exit=0 | PASS |
