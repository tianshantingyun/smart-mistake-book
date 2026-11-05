# -*- coding: utf-8 -*-
"""acceptance_report.py 的机制测试（批次 44 / D0 item 3）。

消灭的具体失败：台账里写 `PASS` 只凭一句 summary 或单个日志，报告工具照样
给 exit 0——这正是批次 41/42 假绿的复现路径。本测试钉住：

- PASS 需要 ≥2 类独立证据（cmd/test/device/review），`file`/`hash` 不计；
- 证据路径必须存在；
- cmd 日志里的 exit 必须可读且为 0；
- test XML 的 failures/errors 必须为 0；
- 历史假绿夹具（15 例 `-4` + `OK (15 tests)`）必须 exit 1。

运行：
    python -I -m pytest tools/tests/test_acceptance_report.py -q
"""
from __future__ import annotations

import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
SCRIPT = REPO / "tools" / "ci" / "acceptance_report.py"
FAKE_GREEN = REPO / "tools" / "tests" / "fixtures" / "acceptance" / "fake_green_batch42.ledger.md"
FAKE_GREEN_41 = REPO / "tools" / "tests" / "fixtures" / "acceptance" / "fake_green_batch41.ledger.md"

HEADER = "| ID | 域 | 检查 | 方法 | 证据 | verdict |\n|---|---|---|---|---|---|\n"


def _row(row_id: str, verdict: str, evidence: str, method: str = "cmd: python -I tools/ci/check_lockfiles.py") -> str:
    return f"| {row_id} | D0 | 测试行 | {method} | {evidence} | {verdict} |\n"


def _run(tmp: Path, ledger: Path, *extra: str) -> tuple[subprocess.CompletedProcess, dict | None]:
    out = tmp / "report.json"
    proc = subprocess.run(
        [sys.executable, "-I", str(SCRIPT),
         "--ledger", str(ledger), "--repo-root", str(tmp),
         "--out", str(out), "--partial", *extra],
        capture_output=True, text=True, cwd=str(REPO),
    )
    data = json.loads(out.read_text(encoding="utf-8")) if out.exists() else None
    return proc, data


def _write_log(root: Path, rel: str, text: str) -> Path:
    path = root / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")
    return path


def _write_xml(root: Path, rel: str, failures: int = 0, errors: int = 0,
               skipped: int = 0, tests: int = 2) -> Path:
    path = root / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        f'<testsuite name="x" tests="{tests}" failures="{failures}" '
        f'errors="{errors}" skipped="{skipped}"></testsuite>\n',
        encoding="utf-8",
    )
    return path


class AcceptanceReportTest(unittest.TestCase):
    def test_two_independent_evidence_pass(self) -> None:
        with tempfile.TemporaryDirectory() as td:
            tmp = Path(td)
            _write_log(tmp, "build/x.log", "step 1\nEXIT=0\n")
            _write_xml(tmp, "build/x.xml")
            ledger = tmp / "ledger.md"
            ledger.write_text(HEADER + _row("D0-001", "PASS", "cmd:build/x.log#exit=0; test:build/x.xml"),
                              encoding="utf-8")
            proc, data = _run(tmp, ledger)
            self.assertEqual(proc.returncode, 0, proc.stdout + proc.stderr)
            self.assertTrue(data["ok"])
            self.assertEqual(data["counts"]["PASS"], 1)
            # 复核缺口必须可度量：PASS 有 2 类证据，但没有 review 终裁。
            self.assertEqual(data["pass_with_review"], 0)
            self.assertEqual(data["pass_without_review"], 1)

    def test_single_log_is_not_enough(self) -> None:
        with tempfile.TemporaryDirectory() as td:
            tmp = Path(td)
            _write_log(tmp, "build/x.log", "EXIT=0\n")
            ledger = tmp / "ledger.md"
            ledger.write_text(HEADER + _row("D0-001", "PASS", "cmd:build/x.log#exit=0"),
                              encoding="utf-8")
            proc, data = _run(tmp, ledger)
            self.assertEqual(proc.returncode, 1)
            self.assertIn("PASS_NEEDS_TWO_INDEPENDENT_EVIDENCE",
                          {v["code"] for v in data["violations"]})

    def test_missing_evidence_path_fails(self) -> None:
        with tempfile.TemporaryDirectory() as td:
            tmp = Path(td)
            _write_xml(tmp, "build/x.xml")
            ledger = tmp / "ledger.md"
            ledger.write_text(HEADER + _row("D0-001", "PASS", "cmd:build/nope.log#exit=0; test:build/x.xml"),
                              encoding="utf-8")
            proc, data = _run(tmp, ledger)
            self.assertEqual(proc.returncode, 1)
            self.assertIn("EVIDENCE_PATH_MISSING", {v["code"] for v in data["violations"]})

    def test_nonzero_command_exit_fails(self) -> None:
        with tempfile.TemporaryDirectory() as td:
            tmp = Path(td)
            _write_log(tmp, "build/x.log", "FAILED\nEXIT=1\n")
            _write_xml(tmp, "build/x.xml")
            ledger = tmp / "ledger.md"
            ledger.write_text(HEADER + _row("D0-001", "PASS", "cmd:build/x.log#exit=0; test:build/x.xml"),
                              encoding="utf-8")
            proc, data = _run(tmp, ledger)
            self.assertEqual(proc.returncode, 1)
            self.assertIn("CMD_EXIT_NONZERO", {v["code"] for v in data["violations"]})

    def test_test_xml_failures_fails(self) -> None:
        with tempfile.TemporaryDirectory() as td:
            tmp = Path(td)
            _write_log(tmp, "build/x.log", "EXIT=0\n")
            _write_xml(tmp, "build/x.xml", failures=1)
            ledger = tmp / "ledger.md"
            ledger.write_text(HEADER + _row("D0-001", "PASS", "cmd:build/x.log#exit=0; test:build/x.xml"),
                              encoding="utf-8")
            proc, data = _run(tmp, ledger)
            self.assertEqual(proc.returncode, 1)
            self.assertIn("TEST_XML_FAILURES", {v["code"] for v in data["violations"]})

    def test_historical_fake_green_fixture_exits_one(self) -> None:
        out = REPO / "build" / "acceptance" / "report-fake-green.json"
        out.parent.mkdir(parents=True, exist_ok=True)
        proc = subprocess.run(
            [sys.executable, "-I", str(SCRIPT), "--ledger", str(FAKE_GREEN),
             "--repo-root", str(REPO), "--out", str(out), "--partial"],
            capture_output=True, text=True, cwd=str(REPO),
        )
        data = json.loads(out.read_text(encoding="utf-8"))
        self.assertEqual(proc.returncode, 1, proc.stdout + proc.stderr)
        codes = {v["code"] for v in data["violations"]}
        self.assertIn("CMD_LOG_SKIP_MARKER", codes)
        self.assertIn("PASS_NEEDS_TWO_INDEPENDENT_EVIDENCE", codes)

    def test_batch41_post_gate_invalid_exits_one(self) -> None:
        out = REPO / "build" / "acceptance" / "report-fake-green-41.json"
        out.parent.mkdir(parents=True, exist_ok=True)
        proc = subprocess.run(
            [sys.executable, "-I", str(SCRIPT), "--ledger", str(FAKE_GREEN_41),
             "--repo-root", str(REPO), "--out", str(out), "--partial"],
            capture_output=True, text=True, cwd=str(REPO),
        )
        self.assertEqual(proc.returncode, 1, proc.stdout + proc.stderr)
        data = json.loads(out.read_text(encoding="utf-8"))
        self.assertIn("CMD_LOG_SKIP_MARKER",
                      {v["code"] for v in data["violations"]})

    def test_device_evidence_must_bind_serial_and_apk(self) -> None:
        with tempfile.TemporaryDirectory() as td:
            tmp = Path(td)
            (tmp / "build").mkdir()
            (tmp / "build" / "device.json").write_text("{}", encoding="utf-8")
            _write_xml(tmp, "build/x.xml")
            ledger = tmp / "ledger.md"
            ledger.write_text(
                HEADER + _row("D0-001", "PASS", "device:build/device.json; test:build/x.xml"),
                encoding="utf-8")
            proc, data = _run(tmp, ledger)
            self.assertEqual(proc.returncode, 1)
            self.assertIn("DEVICE_EVIDENCE_UNBOUND",
                          {v["code"] for v in data["violations"]})

    def test_blocked_without_evidence_is_allowed(self) -> None:
        with tempfile.TemporaryDirectory() as td:
            tmp = Path(td)
            ledger = tmp / "ledger.md"
            ledger.write_text(HEADER + _row("D0-001", "BLOCKED", "file:docs/acceptance/runbook.md"),
                              encoding="utf-8")
            (tmp / "docs" / "acceptance").mkdir(parents=True)
            (tmp / "docs" / "acceptance" / "runbook.md").write_text("runbook\n", encoding="utf-8")
            proc, data = _run(tmp, ledger)
            self.assertEqual(proc.returncode, 0, proc.stdout + proc.stderr)
            self.assertEqual(data["counts"]["BLOCKED"], 1)

    def test_review_must_bind_ledger_hash(self) -> None:
        with tempfile.TemporaryDirectory() as td:
            tmp = Path(td)
            (tmp / "build").mkdir()
            (tmp / "build" / "x.log").write_text("EXIT=0\n", encoding="utf-8")
            (tmp / "build" / "review.json").write_text(
                '{"reviewer": "r", "run_id": "run-1"}', encoding="utf-8")
            ledger = tmp / "ledger.md"
            ledger.write_text(
                HEADER + _row("D0-001", "PASS", "cmd:build/x.log#exit=0; review:build/review.json"),
                encoding="utf-8")
            proc, data = _run(tmp, ledger)
            self.assertEqual(proc.returncode, 1)
            self.assertIn("REVIEW_MISSING_FIELD",
                          {v["code"] for v in data["violations"]})

    def _review_fixture(self, tmp: Path, *, xml_tests: int = 2,
                        run_id: str | None = None, ledger_batch: int | None = None,
                        checks: list[dict] | None = None) -> tuple[Path, Path]:
        """构造一份字段齐全、checks 可复算的行级复核夹具。"""
        import hashlib
        log = tmp / "build" / "x.log"
        log.parent.mkdir(parents=True, exist_ok=True)
        log.write_text("EXIT=0\n", encoding="utf-8")
        xml = _write_xml(tmp, "build/x.xml", tests=xml_tests)
        ledger = tmp / "ledger.md"
        ledger.write_text(
            HEADER + _row("D0-001", "PASS",
                          "cmd:build/x.log#exit=0; test:build/x.xml; review:build/review.json"),
            encoding="utf-8")
        ledger_sha = hashlib.sha256(ledger.read_bytes()).hexdigest()
        review_checks = checks if checks is not None else [
            {"name": "ledger_row", "source": "ledger.md",
             "value": f"id=D0-001; verdict=PASS; line=3; ledger_sha256={ledger_sha}"},
            {"name": "xml_aggregate", "source": "build/x.xml",
             "value": f"tests={xml_tests}; failures=0; errors=0; skipped=0; "
                      f"sha256={hashlib.sha256(xml.read_bytes()).hexdigest()}"},
            {"name": "cmd_exit", "source": "build/x.log",
             "value": "exit_markers=[0]; "
                      f"sha256={hashlib.sha256(log.read_bytes()).hexdigest()}"},
        ]
        review = {
            "id": "D0-001", "verdict": "PASS",
            "run_id": run_id or "2026-11-05T00:00:00Z-batch46-review",
            "reviewer": "reviewer-indep", "authors": ["author-a"],
            "ledger_sha256": ledger_sha,
            "evidence": ["cmd:build/x.log", "test:build/x.xml"],
            "evidence_sha256": {
                "build/x.log": hashlib.sha256(log.read_bytes()).hexdigest(),
                "build/x.xml": hashlib.sha256(xml.read_bytes()).hexdigest(),
            },
            "checks": review_checks,
            "basis": "fixture",
        }
        if ledger_batch is not None:
            review["ledger_batch"] = ledger_batch
        (tmp / "build" / "review.json").write_text(
            json.dumps(review), encoding="utf-8")
        return tmp, ledger

    def test_review_with_recomputable_checks_counts(self) -> None:
        """字段齐全且 checks 可复算的复核才计入 pass_with_review。"""
        with tempfile.TemporaryDirectory() as td:
            tmp, ledger = self._review_fixture(Path(td))
            proc, data = _run(tmp, ledger, "--require-complete")
            self.assertEqual(proc.returncode, 0, proc.stdout + proc.stderr)
            self.assertEqual(data["pass_with_review"], 1)
            self.assertEqual(data["pass_with_invalid_review"], 0)

    def test_review_check_mismatch_is_invalid(self) -> None:
        """checks 声明的 XML tests 与磁盘不符时必须判无效（finding 2）。"""
        with tempfile.TemporaryDirectory() as td:
            tmp = Path(td)
            (tmp / "build").mkdir()
            (tmp / "build" / "x.log").write_text("EXIT=0\n", encoding="utf-8")
            xml = _write_xml(tmp, "build/x.xml", tests=2)
            ledger = tmp / "ledger.md"
            ledger.write_text(
                HEADER + _row("D0-001", "PASS",
                              "cmd:build/x.log#exit=0; test:build/x.xml; review:build/review.json"),
                encoding="utf-8")
            import hashlib
            ledger_sha = hashlib.sha256(ledger.read_bytes()).hexdigest()
            review = {
                "id": "D0-001", "verdict": "PASS",
                "run_id": "2026-11-05T00:00:00Z-batch46-review",
                "reviewer": "r", "authors": ["a"], "ledger_sha256": ledger_sha,
                "evidence": ["cmd:build/x.log", "test:build/x.xml"],
                "evidence_sha256": {
                    "build/x.log": hashlib.sha256((tmp / "build/x.log").read_bytes()).hexdigest(),
                    "build/x.xml": hashlib.sha256(xml.read_bytes()).hexdigest(),
                },
                "checks": [
                    {"name": "ledger_row", "source": "ledger.md",
                     "value": f"id=D0-001; verdict=PASS; line=3; ledger_sha256={ledger_sha}"},
                    {"name": "xml_aggregate", "source": "build/x.xml",
                     "value": "tests=99; failures=0; errors=0; skipped=0"},
                ],
                "basis": "fixture",
            }
            (tmp / "build" / "review.json").write_text(
                json.dumps(review), encoding="utf-8")
            proc, data = _run(tmp, ledger, "--require-complete")
            self.assertEqual(proc.returncode, 1, proc.stdout + proc.stderr)
            self.assertIn("REVIEW_CHECK_FAILED",
                          {v["code"] for v in data["violations"]})
            self.assertEqual(data["pass_with_invalid_review"], 1)
            self.assertIn("PASS_REVIEW_INVALID",
                          {v["code"] for v in data["violations"]})

    def test_review_old_batch_run_id_is_flagged(self) -> None:
        """run_id batch44 却声明 ledger_batch=46 时必须阻断（finding 1）。"""
        with tempfile.TemporaryDirectory() as td:
            tmp, ledger = self._review_fixture(
                Path(td), run_id="2026-11-04T13:00:00Z-batch44-c-domain",
                ledger_batch=46)
            proc, data = _run(tmp, ledger, "--require-complete")
            self.assertEqual(proc.returncode, 1, proc.stdout + proc.stderr)
            self.assertIn("REVIEW_RUN_PREDATES_LEDGER",
                          {v["code"] for v in data["violations"]})

    def test_review_without_checks_is_invalid(self) -> None:
        """只有字段、没有 checks 的复核不算行级复核。"""
        with tempfile.TemporaryDirectory() as td:
            tmp, ledger = self._review_fixture(Path(td), checks=[])
            proc, data = _run(tmp, ledger, "--require-complete")
            self.assertEqual(proc.returncode, 1, proc.stdout + proc.stderr)
            self.assertIn("REVIEW_MISSING_CHECKS",
                          {v["code"] for v in data["violations"]})
            self.assertEqual(data["pass_with_review"], 0)

    def test_review_rejects_doctype_xml(self) -> None:
        """XML 证据含 DOCTYPE/ENTITY 时必须在解析前拒绝（安全约束）。"""
        with tempfile.TemporaryDirectory() as td:
            tmp, ledger = self._review_fixture(Path(td))
            (tmp / "build" / "x.xml").write_text(
                '<?xml version="1.0"?>\n<!DOCTYPE testsuite [<!ENTITY x "y">]>\n'
                '<testsuite tests="2" failures="0" errors="0" skipped="0"/>\n',
                encoding="utf-8")
            # 重新按新 XML 字节写 evidence_sha256 + xml check，避免先被 sha 拦下。
            import hashlib
            review_path = tmp / "build" / "review.json"
            review = json.loads(review_path.read_text(encoding="utf-8"))
            review["evidence_sha256"]["build/x.xml"] = hashlib.sha256(
                (tmp / "build/x.xml").read_bytes()).hexdigest()
            review["checks"][1]["value"] = "tests=2; failures=0; errors=0; skipped=0"
            review_path.write_text(json.dumps(review), encoding="utf-8")
            proc, data = _run(tmp, ledger, "--require-complete")
            self.assertEqual(proc.returncode, 1, proc.stdout + proc.stderr)
            self.assertIn("REVIEW_CHECK_FAILED",
                          {v["code"] for v in data["violations"]})
            details = [v["detail"] for v in data["violations"]
                       if v["code"] == "REVIEW_CHECK_FAILED"]
            self.assertTrue(any("DOCTYPE/ENTITY rejected" in d for d in details), details)

    def test_review_ledger_sha_must_match_ledger(self) -> None:
        """复核 JSON 的 ledger_sha256 必须等于被复核台账的 sha（防伪造复核）。"""
        with tempfile.TemporaryDirectory() as td:
            tmp = Path(td)
            _write_log(tmp, "build/x.log", "EXIT=0\n")
            (tmp / "build" / "review.json").write_text(
                json.dumps({"reviewer": "r2", "run_id": "run-2",
                            "ledger_sha256": "a" * 64}),
                encoding="utf-8")
            ledger = tmp / "ledger.md"
            ledger.write_text(
                HEADER + _row("D0-001", "PASS",
                              "cmd:build/x.log#exit=0; review:build/review.json"),
                encoding="utf-8")
            proc, data = _run(tmp, ledger)
            self.assertEqual(proc.returncode, 1, proc.stdout + proc.stderr)
            self.assertIn("REVIEW_LEDGER_SHA_MISMATCH",
                          {v["code"] for v in data["violations"]})

    def test_review_verdict_must_be_a_known_verdict(self) -> None:
        """复核 JSON 的 verdict 必须是 PASS/FAIL/BLOCKED/UNVERIFIED。

        批次 45 复核 finding 5：B/D/E 的 domain-review 用了 SAMPLED_REVIEW /
        PASS_WORKTREE / PASS_WITH_REVIEW，报告只查字段不查枚举，非标准判词
        被原样接受并进入域索引。复核判词不能自造。
        """
        with tempfile.TemporaryDirectory() as td:
            tmp = Path(td)
            _write_log(tmp, "build/x.log", "EXIT=0\n")
            (tmp / "build" / "review.json").write_text(
                json.dumps({"reviewer": "r3", "run_id": "run-3",
                            "ledger_sha256": "a" * 64,
                            "verdict": "SAMPLED_REVIEW"}),
                encoding="utf-8")
            ledger = tmp / "ledger.md"
            ledger.write_text(
                HEADER + _row("D0-001", "PASS",
                              "cmd:build/x.log#exit=0; review:build/review.json"),
                encoding="utf-8")
            proc, data = _run(tmp, ledger)
            self.assertEqual(proc.returncode, 1, proc.stdout + proc.stderr)
            self.assertIn("REVIEW_VERDICT_INVALID",
                          {v["code"] for v in data["violations"]})

    def test_review_without_verdict_is_invalid(self) -> None:
        with tempfile.TemporaryDirectory() as td:
            tmp = Path(td)
            _write_log(tmp, "build/x.log", "EXIT=0\n")
            (tmp / "build" / "review.json").write_text(
                json.dumps({"reviewer": "r4", "run_id": "run-4",
                            "ledger_sha256": "a" * 64}),
                encoding="utf-8")
            ledger = tmp / "ledger.md"
            ledger.write_text(
                HEADER + _row("D0-001", "PASS",
                              "cmd:build/x.log#exit=0; review:build/review.json"),
                encoding="utf-8")
            proc, data = _run(tmp, ledger)
            self.assertEqual(proc.returncode, 1, proc.stdout + proc.stderr)
            self.assertIn("REVIEW_VERDICT_INVALID",
                          {v["code"] for v in data["violations"]})

    def test_device_verdict_fail_is_not_a_pass(self) -> None:
        """设备 JSON 自带 verdict=FAIL 时，即使 cmd 日志 EXIT=0 也不得判 PASS。"""
        with tempfile.TemporaryDirectory() as td:
            tmp = Path(td)
            (tmp / "build").mkdir()
            (tmp / "build" / "device.json").write_text(json.dumps({
                "serial": "emulator-1", "apk_sha256": "b" * 64,
                "counts": {"tests": 1, "evaluated": 0, "failures": 0},
                "verdict": "FAIL", "reason": "adb/am timeout",
            }), encoding="utf-8")
            _write_xml(tmp, "build/x.xml")
            ledger = tmp / "ledger.md"
            ledger.write_text(
                HEADER + _row("D0-001", "PASS",
                              "device:build/device.json; test:build/x.xml"),
                encoding="utf-8")
            proc, data = _run(tmp, ledger)
            self.assertEqual(proc.returncode, 1, proc.stdout + proc.stderr)
            self.assertIn("DEVICE_EVIDENCE_VERDICT",
                          {v["code"] for v in data["violations"]})

    def test_device_zero_evaluated_is_not_coverage(self) -> None:
        """counts.evaluated=0 表示没有真正执行，不得当作设备覆盖。"""
        with tempfile.TemporaryDirectory() as td:
            tmp = Path(td)
            (tmp / "build").mkdir()
            (tmp / "build" / "device.json").write_text(json.dumps({
                "serial": "emulator-1", "apk_sha256": "b" * 64,
                "counts": {"tests": 1, "evaluated": 0, "failures": 0},
                "verdict": "PASS",
            }), encoding="utf-8")
            _write_xml(tmp, "build/x.xml")
            ledger = tmp / "ledger.md"
            ledger.write_text(
                HEADER + _row("D0-001", "PASS",
                              "device:build/device.json; test:build/x.xml"),
                encoding="utf-8")
            proc, data = _run(tmp, ledger)
            self.assertEqual(proc.returncode, 1, proc.stdout + proc.stderr)
            self.assertIn("DEVICE_EVIDENCE_NOT_EVALUATED",
                          {v["code"] for v in data["violations"]})

    def test_device_counts_failures_are_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as td:
            tmp = Path(td)
            (tmp / "build").mkdir()
            (tmp / "build" / "device.json").write_text(json.dumps({
                "serial": "emulator-1", "apk_sha256": "b" * 64,
                "counts": {"tests": 2, "evaluated": 2, "failures": 1},
                "verdict": "PASS",
            }), encoding="utf-8")
            _write_xml(tmp, "build/x.xml")
            ledger = tmp / "ledger.md"
            ledger.write_text(
                HEADER + _row("D0-001", "PASS",
                              "device:build/device.json; test:build/x.xml"),
                encoding="utf-8")
            proc, data = _run(tmp, ledger)
            self.assertEqual(proc.returncode, 1, proc.stdout + proc.stderr)
            self.assertIn("DEVICE_EVIDENCE_FAILURES",
                          {v["code"] for v in data["violations"]})

    def test_report_binds_head_and_dirty_paths(self) -> None:
        """报告必须记录源快照（HEAD/dirty），不能只绑 ledger sha。"""
        with tempfile.TemporaryDirectory() as td:
            tmp = Path(td)
            ledger = tmp / "ledger.md"
            ledger.write_text(HEADER + _row("D0-001", "UNVERIFIED", "-"),
                              encoding="utf-8")
            proc, data = _run(tmp, ledger)
            self.assertIn("head", data)
            self.assertIn("dirty_paths", data)

    def test_fail_verdict_exits_nonzero_without_require_complete(self) -> None:
        """state=FAIL 时默认退出码必须非 0；否则 CI 会在 21 个 FAIL 上变绿。"""
        with tempfile.TemporaryDirectory() as td:
            tmp = Path(td)
            ledger = tmp / "ledger.md"
            ledger.write_text(HEADER + _row("D0-001", "FAIL", "-"),
                              encoding="utf-8")
            proc, data = _run(tmp, ledger)
            self.assertEqual(data["state"], "FAIL")
            self.assertEqual(proc.returncode, 1, proc.stdout + proc.stderr)

    def test_ci_patch_fails_closed(self) -> None:
        """CI 草案不得在未完成时变绿：report 需 --require-complete，trace 不得容错。"""
        patch = (REPO / "tools" / "ci" / "acceptance_ci.patch").read_text(encoding="utf-8")
        self.assertIn("--require-complete", patch)
        self.assertNotIn("continue-on-error", patch)

    def test_cmd_log_with_placeholder_is_not_a_run_record(self) -> None:
        """`CMD: ./gradlew ... <E-domain classes>` 是占位，不是可复跑运行记录。"""
        with tempfile.TemporaryDirectory() as td:
            tmp = Path(td)
            _write_log(tmp, "build/x.log",
                       "CMD: ./gradlew --tests <E-domain study classes>\nEXIT=0\n")
            _write_xml(tmp, "build/x.xml")
            ledger = tmp / "ledger.md"
            ledger.write_text(
                HEADER + _row("D0-001", "PASS",
                              "cmd:build/x.log#exit=0; test:build/x.xml"),
                encoding="utf-8")
            proc, data = _run(tmp, ledger)
            self.assertEqual(proc.returncode, 1, proc.stdout + proc.stderr)
            self.assertIn("PLACEHOLDER_CMD",
                          {v["code"] for v in data["violations"]})

    def test_require_complete_rejects_pass_without_review(self) -> None:
        """DoD §1.5：终裁门要求每个 PASS 有独立复核，不能只看 cmd+test。"""
        with tempfile.TemporaryDirectory() as td:
            tmp = Path(td)
            _write_log(tmp, "build/x.log", "EXIT=0\n")
            _write_xml(tmp, "build/x.xml")
            ledger = tmp / "ledger.md"
            ledger.write_text(
                HEADER + _row("D0-001", "PASS",
                              "cmd:build/x.log#exit=0; test:build/x.xml"),
                encoding="utf-8")
            proc, data = _run(tmp, ledger, "--require-complete")
            self.assertEqual(proc.returncode, 1, proc.stdout + proc.stderr)
            self.assertIn("PASS_WITHOUT_REVIEW",
                          {v["code"] for v in data["violations"]})

    def test_require_complete_rejects_unverified(self) -> None:
        with tempfile.TemporaryDirectory() as td:
            tmp = Path(td)
            ledger = tmp / "ledger.md"
            ledger.write_text(HEADER + _row("D0-001", "UNVERIFIED", "-"), encoding="utf-8")
            proc, data = _run(tmp, ledger, "--require-complete")
            self.assertEqual(proc.returncode, 1)
            self.assertFalse(data["acceptance_complete"])
            self.assertIn("NOT_ACCEPTANCE_COMPLETE",
                          {v["code"] for v in data["violations"]})

    def test_require_complete_rejects_blocked(self) -> None:
        with tempfile.TemporaryDirectory() as td:
            tmp = Path(td)
            (tmp / "docs" / "acceptance").mkdir(parents=True)
            (tmp / "docs" / "acceptance" / "runbook.md").write_text("runbook\n", encoding="utf-8")
            ledger = tmp / "ledger.md"
            ledger.write_text(
                HEADER + _row("D0-001", "BLOCKED", "file:docs/acceptance/runbook.md"),
                encoding="utf-8")
            proc, data = _run(tmp, ledger, "--require-complete")
            self.assertEqual(proc.returncode, 1)
            self.assertIn("NOT_ACCEPTANCE_COMPLETE",
                          {v["code"] for v in data["violations"]})


if __name__ == "__main__":
    unittest.main()
