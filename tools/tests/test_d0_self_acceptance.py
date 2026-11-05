# -*- coding: utf-8 -*-
"""acceptance_model.py 的台账解析自验收（批次 45 / 计划 WS2 / D0-001…006）。

## 它消灭的具体失败

批次 44 的 D0-001…006 只写了“init --check 会校验”，证据列是 `-`：没有任何
当前断言证明“列数错、ID 重号、域不匹配、证据 token 写坏会被抓住”。本文件直接
对共享解析器下负例，让每条“会校验”的声明都有可失败的测试。

运行：
    python -I -m pytest tools/tests/test_d0_self_acceptance.py -q
"""

from __future__ import annotations

import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools" / "ci"))

import acceptance_model as M  # noqa: E402

LEDGER = ROOT / "docs" / "acceptance" / "ledger.md"
HEADER = "| ID | 域 | 检查 | 方法 | 证据 | verdict |\n|---|---|---|---|---|---|\n"


def row(row_id: str = "D0-001", domain: str = "D0", verdict: str = "PASS",
        evidence: str = "cmd:tools/ci/acceptance_init.py") -> str:
    return f"| {row_id} | {domain} | 自测行 | cmd: x | {evidence} | {verdict} |\n"


class TestModel(unittest.TestCase):
    """D0-001…006：唯一解析器的结构保证。"""

    @classmethod
    def setUpClass(cls) -> None:
        cls.rows, cls.issues = M.parse_ledger(LEDGER)

    def test_001_ledger_readable_1200_rows(self) -> None:
        self.assertEqual(len(self.rows), 1200)
        self.assertEqual(len(M.expected_ids()), 1200)

    def test_002_ids_unique_and_domain_prefixed(self) -> None:
        self.assertEqual(M.identity_issues(self.rows), [])
        self.assertEqual(len({item.row_id for item in self.rows}), 1200)

    def test_003_domain_counts(self) -> None:
        for key, _, count in M.DOMAINS:
            self.assertEqual(sum(1 for item in self.rows if item.domain == key), count, key)

    def test_004_six_columns(self) -> None:
        codes = {issue["code"] for issue in self.issues}
        self.assertNotIn("ROW_COLUMN_COUNT", codes)
        self.assertNotIn("DOMAIN_MISMATCH", codes)

    def test_005_verdict_enum(self) -> None:
        codes = {issue["code"] for issue in self.issues}
        self.assertNotIn("BAD_VERDICT", codes)
        self.assertTrue(all(item.verdict in M.VERDICTS for item in self.rows))
        self.assertTrue(all(item.verdict for item in self.rows))

    def test_006_evidence_tokens_parse(self) -> None:
        codes = {issue["code"] for issue in self.issues}
        self.assertNotIn("BAD_EVIDENCE", codes)
        parsed = sum(len(item.evidence()) for item in self.rows)
        self.assertGreater(parsed, 0)

    # ---------------------------------------------------------- 负例（自突变）

    def test_bad_verdict_is_reported(self) -> None:
        _, issues = M.parse_ledger_text(HEADER + row(verdict="OK"))
        self.assertIn("BAD_VERDICT", {issue["code"] for issue in issues})

    def test_duplicate_id_is_reported(self) -> None:
        _, issues = M.parse_ledger_text(HEADER + row() + row())
        self.assertIn("DUPLICATE_ID", {issue["code"] for issue in issues})

    def test_wrong_column_count_is_reported(self) -> None:
        _, issues = M.parse_ledger_text("| D0-001 | D0 | 自测行 | cmd: x | PASS |\n")
        self.assertIn("ROW_COLUMN_COUNT", {issue["code"] for issue in issues})

    def test_domain_mismatch_is_reported(self) -> None:
        _, issues = M.parse_ledger_text(HEADER + row(row_id="A-001", domain="D0"))
        self.assertIn("DOMAIN_MISMATCH", {issue["code"] for issue in issues})

    def test_bad_evidence_kind_is_reported(self) -> None:
        _, issues = M.parse_ledger_text(HEADER + row(evidence="log:notes/x.txt"))
        self.assertIn("BAD_EVIDENCE", {issue["code"] for issue in issues})

    def test_empty_evidence_path_is_reported(self) -> None:
        _, issues = M.parse_ledger_text(HEADER + row(evidence="cmd:"))
        self.assertIn("BAD_EVIDENCE", {issue["code"] for issue in issues})

    def test_evidence_attrs_round_trip(self) -> None:
        items, errors = M.parse_evidence("cmd:build/a.txt#exit=0,note=x; test:build/b.xml")
        self.assertEqual(errors, [])
        self.assertEqual([item.kind for item in items], ["cmd", "test"])
        self.assertEqual(items[0].attr("exit"), "0")
        self.assertEqual(M.format_evidence(items),
                         "cmd:build/a.txt#exit=0,note=x; test:build/b.xml")


if __name__ == "__main__":
    unittest.main()
