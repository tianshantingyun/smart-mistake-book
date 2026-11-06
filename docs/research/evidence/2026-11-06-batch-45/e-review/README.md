# E 域独立重跑的归档副本（2026-11-05 10:54–11:08 +0800）

本目录由 `build/e-review/`（gitignored）原样复制而来，用于让 E-review 的
“独立重跑”不再只存在于本机 build/。内容与限制：

- `snap-domain/`、`snap-data/`：重跑后的 JUnit XML 快照；
- `xml-counts.json`：XML 计数复算；`data-rerun.out` / `domain-rerun.out`：
  `-q` 模式只剩空行，不能单独证明执行（真正证据是 XML 的 tests/failures）；
- `lint.json`、`final-report.json`：当轮报告，绑定**修复前**台账
  `bb25ef23…`，不是最终台账；
- 本归档不改变 E-review 的独立性结论：reviewer 仍是角色标签，
  不代表跨模型/人类签字；80 个 E PASS 的行级 `review:` 证据仍为 0。
