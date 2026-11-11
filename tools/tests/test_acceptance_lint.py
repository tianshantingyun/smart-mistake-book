# -*- coding: utf-8 -*-
"""acceptance_lint.py 的机制测试（批次 44 / D0 item 4、item 15）。

消灭的具体失败：
- 把批次记录 / summary 当证据；
- 台账把 UNVERIFIED 写成 PASS；
- 15 例 `-4` 却打印 `OK (15 tests)` 的跳过门被计通过。

历史假绿回放夹具来自 `docs/research/2026-11-04-batch-42-record.md` §2.3
（15 例全 `-4`、零执行，却打印 `OK (15 tests)`）。lint 必须 exit 1。

运行：
    python -I -m pytest tools/tests/test_acceptance_lint.py -q
"""
from __future__ import annotations

import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
SCRIPT = REPO / "tools" / "ci" / "acceptance_lint.py"
FAKE_GREEN = REPO / "tools" / "tests" / "fixtures" / "acceptance" / "fake_green_batch42.ledger.md"
FAKE_GREEN_41 = REPO / "tools" / "tests" / "fixtures" / "acceptance" / "fake_green_batch41.ledger.md"

HEADER = "| ID | 域 | 检查 | 方法 | 证据 | verdict |\n|---|---|---|---|---|---|\n"


def _row(row_id: str, verdict: str, evidence: str, check: str = "测试行") -> str:
    return f"| {row_id} | D0 | {check} | cmd: true | {evidence} | {verdict} |\n"


def _run(tmp: Path, ledger: Path, *extra: str) -> tuple[subprocess.CompletedProcess, dict | None]:
    out = tmp / "lint.json"
    proc = subprocess.run(
        [sys.executable, "-I", str(SCRIPT), "--ledger", str(ledger),
         "--repo-root", str(tmp), "--out", str(out), "--partial", *extra],
        capture_output=True, text=True, encoding="utf-8", errors="replace", cwd=str(REPO),
    )
    data = json.loads(out.read_text(encoding="utf-8")) if out.exists() else None
    return proc, data


class AcceptanceLintTest(unittest.TestCase):
    def test_historical_fake_green_fixture_exits_one(self) -> None:
        out = REPO / "build" / "acceptance" / "lint-fake-green.json"
        out.parent.mkdir(parents=True, exist_ok=True)
        proc = subprocess.run(
            [sys.executable, "-I", str(SCRIPT), "--ledger", str(FAKE_GREEN),
             "--repo-root", str(REPO), "--out", str(out), "--partial"],
            capture_output=True, text=True, encoding="utf-8", errors="replace", cwd=str(REPO),
        )
        self.assertEqual(proc.returncode, 1, proc.stdout + proc.stderr)
        data = json.loads(out.read_text(encoding="utf-8"))
        codes = {v["code"] for v in data["violations"]}
        self.assertIn("SKIPPED_GATE_AS_PASS", codes)

    def test_batch41_post_gate_invalid_exits_one(self) -> None:
        out = REPO / "build" / "acceptance" / "lint-fake-green-41.json"
        out.parent.mkdir(parents=True, exist_ok=True)
        proc = subprocess.run(
            [sys.executable, "-I", str(SCRIPT), "--ledger", str(FAKE_GREEN_41),
             "--repo-root", str(REPO), "--out", str(out), "--partial"],
            capture_output=True, text=True, encoding="utf-8", errors="replace", cwd=str(REPO),
        )
        self.assertEqual(proc.returncode, 1, proc.stdout + proc.stderr)
        data = json.loads(out.read_text(encoding="utf-8"))
        self.assertIn("SKIPPED_GATE_AS_PASS",
                      {v["code"] for v in data["violations"]})

    def test_summary_as_evidence(self) -> None:
        with tempfile.TemporaryDirectory() as td:
            tmp = Path(td)
            (tmp / "docs").mkdir()
            (tmp / "docs" / "summary.md").write_text("all green\n", encoding="utf-8")
            ledger = tmp / "ledger.md"
            ledger.write_text(HEADER + _row("D0-001", "PASS", "file:docs/summary.md; cmd:true"),
                              encoding="utf-8")
            proc, data = _run(tmp, ledger)
            self.assertEqual(proc.returncode, 1)
            self.assertIn("SUMMARY_AS_EVIDENCE", {v["code"] for v in data["violations"]})

    def test_unverified_marked_pass(self) -> None:
        with tempfile.TemporaryDirectory() as td:
            tmp = Path(td)
            (tmp / "build").mkdir()
            (tmp / "build" / "x.md").write_text("UNVERIFIED: device not run\n", encoding="utf-8")
            ledger = tmp / "ledger.md"
            ledger.write_text(HEADER + _row("D0-001", "PASS", "file:build/x.md; cmd:true"),
                              encoding="utf-8")
            proc, data = _run(tmp, ledger)
            self.assertEqual(proc.returncode, 1)
            self.assertIn("UNVERIFIED_AS_PASS", {v["code"] for v in data["violations"]})

    def test_clean_pass_is_clean(self) -> None:
        with tempfile.TemporaryDirectory() as td:
            tmp = Path(td)
            (tmp / "build").mkdir()
            (tmp / "build" / "x.log").write_text("EXIT=0\n", encoding="utf-8")
            (tmp / "build" / "x.xml").write_text(
                '<testsuite tests="1" failures="0" errors="0" skipped="0"></testsuite>\n',
                encoding="utf-8")
            ledger = tmp / "ledger.md"
            ledger.write_text(HEADER + _row("D0-001", "PASS", "cmd:build/x.log#exit=0; test:build/x.xml"),
                              encoding="utf-8")
            proc, data = _run(tmp, ledger)
            self.assertEqual(proc.returncode, 0, proc.stdout + proc.stderr)
            self.assertEqual(data["violations"], [])

    def test_duplicate_id(self) -> None:
        with tempfile.TemporaryDirectory() as td:
            tmp = Path(td)
            ledger = tmp / "ledger.md"
            ledger.write_text(
                HEADER + _row("D0-001", "UNVERIFIED", "-") + _row("D0-001", "UNVERIFIED", "-"),
                encoding="utf-8")
            proc, data = _run(tmp, ledger)
            self.assertEqual(proc.returncode, 1)
            self.assertIn("DUPLICATE_ID", {v["code"] for v in data["violations"]})

    def test_strict_warnings_blocks_evidence_reuse(self) -> None:
        """共享证据默认即阻断；--warn-only 只用于诊断（批次 46 finding 6）。"""
        with tempfile.TemporaryDirectory() as td:
            tmp = Path(td)
            (tmp / "build").mkdir()
            (tmp / "build" / "x.log").write_text("EXIT=0\n", encoding="utf-8")
            rows = "".join(
                _row(f"D0-{index:03d}", "PASS", "cmd:build/x.log#exit=0")
                for index in range(1, 12)
            )
            ledger = tmp / "ledger.md"
            ledger.write_text(HEADER + rows, encoding="utf-8")
            proc, data = _run(tmp, ledger)
            self.assertEqual(proc.returncode, 1, proc.stdout + proc.stderr)
            self.assertTrue(data["warnings"])
            self.assertIn("EVIDENCE_REUSED",
                          {v["code"] for v in data["violations"]})
            proc, data = _run(tmp, ledger, "--warn-only")
            self.assertEqual(proc.returncode, 0, proc.stdout + proc.stderr)
            self.assertEqual(data["violations"], [])
            self.assertTrue(data["warnings"])

    def test_evidence_shared_by_five_rows_warns(self) -> None:
        """同一证据被 5 行 PASS 共用即阻断（批次 46 finding 6）。

        旧阈值 `count > 10` 让 GATE 域“每证据恰好 10 行”的复用永远不可见；
        批次 46 起默认把 ≥5 行复用升级为违规，只有 --warn-only 才保留告警。
        """
        with tempfile.TemporaryDirectory() as td:
            tmp = Path(td)
            (tmp / "build").mkdir()
            (tmp / "build" / "x.log").write_text("EXIT=0\n", encoding="utf-8")
            rows = "".join(
                _row(f"D0-{index:03d}", "PASS", "cmd:build/x.log#exit=0")
                for index in range(1, 6)
            )
            ledger = tmp / "ledger.md"
            ledger.write_text(HEADER + rows, encoding="utf-8")
            proc, data = _run(tmp, ledger)
            self.assertEqual(proc.returncode, 1, proc.stdout + proc.stderr)
            self.assertEqual([v["code"] for v in data["violations"]],
                             ["EVIDENCE_REUSED"])
            self.assertEqual([w["code"] for w in data["warnings"]],
                             ["EVIDENCE_REUSED"])
            self.assertEqual(len(data["reuse"]), 1)
            self.assertEqual(data["reuse"][0]["count"], 5)

    def test_evidence_shared_by_four_rows_is_not_a_warning(self) -> None:
        with tempfile.TemporaryDirectory() as td:
            tmp = Path(td)
            (tmp / "build").mkdir()
            (tmp / "build" / "x.log").write_text("EXIT=0\n", encoding="utf-8")
            rows = "".join(
                _row(f"D0-{index:03d}", "PASS", "cmd:build/x.log#exit=0")
                for index in range(1, 5)
            )
            ledger = tmp / "ledger.md"
            ledger.write_text(HEADER + rows, encoding="utf-8")
            proc, data = _run(tmp, ledger)
            self.assertEqual(proc.returncode, 0, proc.stdout + proc.stderr)
            self.assertEqual(data["warnings"], [])
            self.assertEqual(data["reuse"][0]["count"], 4)

    def test_self_review_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as td:
            tmp = Path(td)
            (tmp / "v").mkdir()
            (tmp / "v" / "r.json").write_text(
                '{"reviewer": "author", "authors": ["author"], "run_id": "r1"}',
                encoding="utf-8")
            ledger = tmp / "ledger.md"
            ledger.write_text(HEADER + _row("D0-001", "PASS", "review:v/r.json; cmd:true"),
                              encoding="utf-8")
            proc, data = _run(tmp, ledger)
            self.assertEqual(proc.returncode, 1)
            self.assertIn("SELF_REVIEW", {v["code"] for v in data["violations"]})


if __name__ == "__main__":
    unittest.main()
