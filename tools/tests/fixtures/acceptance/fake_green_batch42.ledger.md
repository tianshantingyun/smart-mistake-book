<!-- 历史假绿回放夹具（批次 44 / D0 item 15）。
     来源：docs/research/2026-11-04-batch-42-record.md §2.3。
     事实：15 例全部 -4（assumption 失败、零执行），JUnit 仍打印 `OK (15 tests)`。
     用途：acceptance_report.py 与 acceptance_lint.py 必须 exit 1。
     本文件不是证据，不得被任何 PASS 行引用。 -->

| ID | 域 | 检查 | 方法 | 证据 | verdict |
|---|---|---|---|---|---|
| D0-001 | D0 | 回放：15 例全 -4 被记为通过 | device: adb shell am instrument -w VisualJsIsolationInstrumentedTest | cmd:tools/tests/fixtures/acceptance/fake_green_run.log#exit=0 | PASS |
| D0-002 | D0 | 回放：批次记录 summary 被当作证据 | cmd: python -I tools/ci/acceptance_report.py | file:docs/research/2026-11-04-batch-42-record.md | PASS |
