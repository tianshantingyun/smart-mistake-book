# apply_I_rows.b47 —— 一次性构建脚本存档说明

真实的批次 47 I 域行级 JSON 构建脚本保存在 gitignored 的
`build/batch47-I/apply_I_rows.py`（sha256
`6422df3635346f4cadd7f3dc51ec309e8383fe2e0b2cb630a69949fd9718f6a9`，417 行）；
本目录曾放过的 `.py` 副本写入时被截断，已删除，避免误导审计者。

可复算性不依赖该脚本存档：

- 每条新增 check 的 `name/source/value` 都完整落在 42 份
  `docs/acceptance/verdicts/rows/I-*.json` 里，由
  `tools/ci/acceptance_review.py` 的 `check_one`（`tools/ci/acceptance_review.py:262`）
  按字节复算；
- 域内唯一性由 `python -I tools/ci/check_row_specific_checks.py --domain I` 复算
  （工具在树：`tools/ci/check_row_specific_checks.py:1`）；
- 本次落盘的命令与 exit 见 `00-index.md`。
