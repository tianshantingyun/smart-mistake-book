# acceptance_lint 检查列前缀临时样例（批次 49，非台账）

> 本文件只用于验证 `tools/ci/acceptance_lint.py:110-116` 对**检查列前缀**的判定，
> **不是** `docs/acceptance/ledger.md` 的一部分，也不得被引用成验收证据。
> 期望：E-999（`UNVERIFIED:` 前缀）与 E-997（`未验证：` 前缀）报
> `UNVERIFIED_AS_PASS`；E-998（正文提及 UNVERIFIED 但非前缀）不报；E-996（方法列
> 含 UNVERIFIED）报。

| ID | 域 | 检查 | 方法 | 证据 | verdict |
|---|---|---|---|---|---|
| E-999 | E | UNVERIFIED: 设备未跑 | cmd: python -I tools/ci/acceptance_lint.py --ledger docs/research/evidence/2026-11-08-batch-49/lint-prefix/sample-ledger.md --partial | cmd:docs/research/evidence/2026-11-08-batch-49/lint-prefix/fake-cmd.txt | PASS |
| E-998 | E | 检查规则：UNVERIFIED 不得标 PASS（正文提及，不是前缀） | cmd: python -I tools/ci/acceptance_lint.py --ledger docs/research/evidence/2026-11-08-batch-49/lint-prefix/sample-ledger.md --partial | cmd:docs/research/evidence/2026-11-08-batch-49/lint-prefix/fake-cmd.txt | PASS |
| E-997 | E | 未验证：设备未跑 | cmd: python -I tools/ci/acceptance_lint.py --ledger docs/research/evidence/2026-11-08-batch-49/lint-prefix/sample-ledger.md --partial | cmd:docs/research/evidence/2026-11-08-batch-49/lint-prefix/fake-cmd.txt | PASS |
| E-996 | E | 方法列含 UNVERIFIED 应报 | cmd: python -I tools/ci/acceptance_lint.py --ledger docs/research/evidence/2026-11-08-batch-49/lint-prefix/sample-ledger.md --partial，UNVERIFIED 是方法残留 | cmd:docs/research/evidence/2026-11-08-batch-49/lint-prefix/fake-cmd.txt | PASS |
