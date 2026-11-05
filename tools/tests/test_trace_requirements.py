# -*- coding: utf-8 -*-
"""trace_requirements.py 的机制测试（批次 44 / D0 item 6）。

消灭的具体失败：台账里写了一个不存在的测试路径或 `<占位符>`，仍被当作
“已有落点”。trace 必须把 需求 id → 测试路径 → 证据 串起来，并把缺口
显式输出（PLACEHOLDER / MISSING_TEST_PATH / DEVICE_EVIDENCE_MISSING）。

运行：
    python -I -m pytest tools/tests/test_trace_requirements.py -q
"""
from __future__ import annotations

import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
SCRIPT = REPO / "tools" / "ci" / "trace_requirements.py"
HEADER = "| ID | 域 | 检查 | 方法 | 证据 | verdict |\n|---|---|---|---|---|---|\n"


def _row(row_id: str, method: str, evidence: str = "-", verdict: str = "UNVERIFIED") -> str:
    return f"| {row_id} | D0 | 测试行 | {method} | {evidence} | {verdict} |\n"


def _run(tmp: Path, ledger: Path) -> tuple[subprocess.CompletedProcess, dict | None]:
    out = tmp / "trace.json"
    proc = subprocess.run(
        [sys.executable, "-I", str(SCRIPT), "--ledger", str(ledger),
         "--repo-root", str(tmp), "--out", str(out), "--partial"],
        capture_output=True, text=True, cwd=str(REPO),
    )
    data = json.loads(out.read_text(encoding="utf-8")) if out.exists() else None
    return proc, data


class TraceRequirementsTest(unittest.TestCase):
    def test_existing_command_path_is_traceable(self) -> None:
        with tempfile.TemporaryDirectory() as td:
            tmp = Path(td)
            (tmp / "tools" / "ci").mkdir(parents=True)
            (tmp / "tools" / "ci" / "gate.py").write_text("print('ok')\n", encoding="utf-8")
            ledger = tmp / "ledger.md"
            ledger.write_text(HEADER + _row("D0-001", "cmd: python -I tools/ci/gate.py",
                                            "cmd:build/x.log#exit=0"),
                              encoding="utf-8")
            proc, data = _run(tmp, ledger)
            self.assertEqual(proc.returncode, 0, proc.stdout + proc.stderr)
            self.assertEqual(data["gaps"], [])

    def test_missing_test_path_is_a_gap(self) -> None:
        with tempfile.TemporaryDirectory() as td:
            tmp = Path(td)
            ledger = tmp / "ledger.md"
            ledger.write_text(HEADER + _row("D0-001", "test:missing/path.py"), encoding="utf-8")
            proc, data = _run(tmp, ledger)
            self.assertEqual(proc.returncode, 1)
            self.assertIn("MISSING_TEST_PATH", {g["code"] for g in data["gaps"]})

    def test_placeholder_is_a_gap(self) -> None:
        with tempfile.TemporaryDirectory() as td:
            tmp = Path(td)
            ledger = tmp / "ledger.md"
            ledger.write_text(HEADER + _row("D0-001", "device: adb shell am instrument <JourneyTest>"),
                              encoding="utf-8")
            proc, data = _run(tmp, ledger)
            self.assertEqual(proc.returncode, 1)
            self.assertIn("PLACEHOLDER", {g["code"] for g in data["gaps"]})

    def test_device_method_without_device_evidence_is_a_gap(self) -> None:
        with tempfile.TemporaryDirectory() as td:
            tmp = Path(td)
            ledger = tmp / "ledger.md"
            ledger.write_text(HEADER + _row("D0-001", "device: adb shell am instrument -w",
                                            "cmd:build/x.log#exit=0"),
                              encoding="utf-8")
            proc, data = _run(tmp, ledger)
            self.assertEqual(proc.returncode, 1)
            self.assertIn("DEVICE_EVIDENCE_MISSING", {g["code"] for g in data["gaps"]})

    def test_python_dash_m_pytest_is_traceable(self) -> None:
        """`python -I -m pytest <path>` 不得把 `-m` 当成脚本路径。"""
        with tempfile.TemporaryDirectory() as td:
            tmp = Path(td)
            (tmp / "tools" / "tests").mkdir(parents=True)
            (tmp / "tools" / "tests" / "test_x.py").write_text("def test_x():\n    pass\n",
                                                               encoding="utf-8")
            ledger = tmp / "ledger.md"
            ledger.write_text(
                HEADER + _row("D0-001",
                              "cmd: python -I -m pytest tools/tests/test_x.py -q"),
                encoding="utf-8")
            proc, data = _run(tmp, ledger)
            self.assertEqual(proc.returncode, 0, proc.stdout + proc.stderr)
            self.assertEqual(data["gaps"], [])

    def test_test_path_with_method_anchor_is_traceable(self) -> None:
        """`test: path#method` 的 #method 是锚点，不是路径的一部分。"""
        with tempfile.TemporaryDirectory() as td:
            tmp = Path(td)
            (tmp / "tools" / "tests").mkdir(parents=True)
            (tmp / "tools" / "tests" / "test_x.py").write_text("def test_x():\n    pass\n",
                                                               encoding="utf-8")
            ledger = tmp / "ledger.md"
            ledger.write_text(HEADER + _row("D0-001", "test: tools/tests/test_x.py#test_x"),
                              encoding="utf-8")
            proc, data = _run(tmp, ledger)
            self.assertEqual(proc.returncode, 0, proc.stdout + proc.stderr)
            self.assertEqual(data["gaps"], [])

    def test_device_evidence_counts_as_a_landing_point(self) -> None:
        """方法只写工具命令、但证据里有 device 时，不应再报无测试落点。"""
        with tempfile.TemporaryDirectory() as td:
            tmp = Path(td)
            (tmp / "tools" / "ci").mkdir(parents=True)
            (tmp / "tools" / "ci" / "gate.py").write_text("print('ok')\n", encoding="utf-8")
            (tmp / "build").mkdir()
            (tmp / "build" / "x.log").write_text("EXIT=0\n", encoding="utf-8")
            (tmp / "build" / "device.json").write_text(
                '{"serial": "s", "apk_sha256": "x"}', encoding="utf-8")
            ledger = tmp / "ledger.md"
            ledger.write_text(
                HEADER + _row("D0-001", "cmd: python -I tools/ci/gate.py",
                              "device:build/device.json; cmd:build/x.log#exit=0",
                              "PASS"),
                encoding="utf-8")
            proc, data = _run(tmp, ledger)
            self.assertNotIn("PASS_WITHOUT_TEST_LANDING",
                             {g["code"] for g in data["gaps"]})

    def test_test_evidence_counts_as_a_landing_point(self) -> None:
        """方法只写工具命令、但证据里已有 test XML 时，不应再报无测试落点。"""
        with tempfile.TemporaryDirectory() as td:
            tmp = Path(td)
            (tmp / "tools" / "ci").mkdir(parents=True)
            (tmp / "tools" / "ci" / "gate.py").write_text("print('ok')\n", encoding="utf-8")
            (tmp / "build").mkdir()
            (tmp / "build" / "x.log").write_text("EXIT=0\n", encoding="utf-8")
            (tmp / "build" / "x.xml").write_text(
                '<testsuite tests="1" failures="0" errors="0"></testsuite>\n',
                encoding="utf-8")
            ledger = tmp / "ledger.md"
            ledger.write_text(
                HEADER + _row("D0-001", "cmd: python -I tools/ci/gate.py",
                              "test:build/x.xml; cmd:build/x.log#exit=0", "PASS"),
                encoding="utf-8")
            proc, data = _run(tmp, ledger)
            self.assertNotIn("PASS_WITHOUT_TEST_LANDING",
                             {g["code"] for g in data["gaps"]})

    def test_pass_row_needs_a_test_or_device_landing_point(self) -> None:
        with tempfile.TemporaryDirectory() as td:
            tmp = Path(td)
            (tmp / "build").mkdir()
            (tmp / "build" / "x.log").write_text("EXIT=0\n", encoding="utf-8")
            ledger = tmp / "ledger.md"
            ledger.write_text(
                HEADER + _row("D0-001", "cmd: true", "cmd:build/x.log#exit=0; review:v.json", "PASS"),
                encoding="utf-8")
            proc, data = _run(tmp, ledger)
            self.assertEqual(proc.returncode, 1)
            self.assertIn("PASS_WITHOUT_TEST_LANDING", {g["code"] for g in data["gaps"]})


if __name__ == "__main__":
    unittest.main()
