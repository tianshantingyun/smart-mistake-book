# -*- coding: utf-8 -*-
"""acceptance_init.py 的机制测试（批次 44 / D0 item 1、item 15）。

消灭的具体失败：台账被重新生成时覆盖已裁定 verdict，或生成的行数/域分布
悄悄漂移（批次 41–43 连续三批出现“计划写 100、实账 104”一类口径缺陷）。

运行：
    python -I -m pytest tools/tests/test_acceptance_init.py -q
"""
from __future__ import annotations

import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
SCRIPT = REPO / "tools" / "ci" / "acceptance_init.py"
sys.path.insert(0, str(REPO / "tools" / "ci"))

import acceptance_model as M  # noqa: E402


class AcceptanceInitTest(unittest.TestCase):
    def _run(self, ledger: Path, *extra: str) -> subprocess.CompletedProcess:
        return subprocess.run(
            [sys.executable, "-I", str(SCRIPT), "--ledger", str(ledger), *extra],
            capture_output=True, text=True, encoding="utf-8", errors="replace", cwd=str(REPO),
        )

    def test_generates_1200_rows_with_expected_domains(self) -> None:
        with tempfile.TemporaryDirectory() as td:
            ledger = Path(td) / "ledger.md"
            proc = self._run(ledger)
            self.assertEqual(proc.returncode, 0, proc.stdout + proc.stderr)
            rows, issues = M.parse_ledger(ledger)
            self.assertEqual(issues, [])
            self.assertEqual(len(rows), 1200)
            counts: dict[str, int] = {}
            for row in rows:
                counts[row.domain] = counts.get(row.domain, 0) + 1
            self.assertEqual(counts["D0"], 100)
            for domain in "ABCDEFGHIJ":
                self.assertEqual(counts[domain], 90, domain)
            self.assertEqual(counts["FIX"], 100)
            self.assertEqual(counts["GATE"], 100)
            self.assertEqual(len({r.row_id for r in rows}), 1200)
            verdicts = {r.verdict for r in rows}
            self.assertLessEqual(verdicts, {"UNVERIFIED", "BLOCKED"})
            self.assertGreater(sum(1 for r in rows if r.verdict == "BLOCKED"), 0)
            for row in rows:
                if row.verdict == "BLOCKED":
                    self.assertEqual(row.evidence_text, "file: docs/acceptance/runbook.md")

    def test_second_run_is_byte_identical(self) -> None:
        with tempfile.TemporaryDirectory() as td:
            ledger = Path(td) / "ledger.md"
            self.assertEqual(self._run(ledger).returncode, 0)
            first = ledger.read_bytes()
            self.assertEqual(self._run(ledger).returncode, 0)
            self.assertEqual(first, ledger.read_bytes())

    def test_existing_verdict_is_never_overwritten(self) -> None:
        with tempfile.TemporaryDirectory() as td:
            ledger = Path(td) / "ledger.md"
            self.assertEqual(self._run(ledger).returncode, 0)
            text = ledger.read_text(encoding="utf-8")
            text = text.replace("| D0-001 | D0 |", "| D0-001 | D0 |", 1)
            # 把第一行 verdict 改成 FAIL，并补一个可解析证据。
            lines = text.splitlines()
            for i, line in enumerate(lines):
                if line.startswith("| D0-001 |"):
                    cells = [c.strip() for c in line.strip("|").split("|")]
                    cells[4] = "cmd:build/x.log#exit=1"
                    cells[5] = "FAIL"
                    lines[i] = "| " + " | ".join(cells) + " |"
                    break
            ledger.write_text("\n".join(lines) + "\n", encoding="utf-8")
            self.assertEqual(self._run(ledger).returncode, 0)
            rows, _ = M.parse_ledger(ledger)
            first = next(r for r in rows if r.row_id == "D0-001")
            self.assertEqual(first.verdict, "FAIL")
            self.assertEqual(first.evidence_text, "cmd:build/x.log#exit=1")

    def test_check_mode_detects_count_drift(self) -> None:
        with tempfile.TemporaryDirectory() as td:
            ledger = Path(td) / "ledger.md"
            self.assertEqual(self._run(ledger).returncode, 0)
            self.assertEqual(self._run(ledger, "--check").returncode, 0)
            lines = ledger.read_text(encoding="utf-8").splitlines()
            ledger.write_text("\n".join(lines[:-1]) + "\n", encoding="utf-8")
            proc = self._run(ledger, "--check")
            self.assertEqual(proc.returncode, 1, proc.stdout + proc.stderr)
            self.assertIn("MISSING_ROW", proc.stdout)

    def test_refuses_to_write_when_existing_row_is_malformed(self) -> None:
        with tempfile.TemporaryDirectory() as td:
            ledger = Path(td) / "ledger.md"
            self.assertEqual(self._run(ledger).returncode, 0)
            before = ledger.read_text(encoding="utf-8")
            broken = before.replace(
                "| D0-001 | D0 |", "| D0-001 | D0 |", 1)
            lines = broken.splitlines()
            for i, line in enumerate(lines):
                if line.startswith("| D0-001 |"):
                    lines[i] = "| D0-001 | D0 | broken row |"
                    break
            ledger.write_text("\n".join(lines) + "\n", encoding="utf-8")
            malformed = ledger.read_text(encoding="utf-8")
            proc = self._run(ledger, "--write")
            self.assertEqual(proc.returncode, 1, proc.stdout + proc.stderr)
            self.assertIn("REFUSED", proc.stderr)
            self.assertEqual(malformed, ledger.read_text(encoding="utf-8"))

    def test_meta_reports_matrix(self) -> None:
        with tempfile.TemporaryDirectory() as td:
            ledger = Path(td) / "ledger.md"
            meta = Path(td) / "meta.json"
            self.assertEqual(self._run(ledger, "--meta", str(meta)).returncode, 0)
            data = json.loads(meta.read_text(encoding="utf-8"))
            self.assertEqual(data["total"], 1200)
            self.assertEqual(data["domains"]["D0"], 100)


if __name__ == "__main__":
    unittest.main()
