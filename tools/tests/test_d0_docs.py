# -*- coding: utf-8 -*-
"""D0 文档 / 协议 / 补丁 / 回放类台账行的自验收断言（批次 45 / 计划 WS2）。

## 它消灭的具体失败

批次 44 的 D0-008…D0-100 把“文档里写了这句话”当成“这一行已验证”，证据列却
全是 `-`。本文件把每条文档类检查变成可失败的断言：标题真的在、字段真的齐、
补丁真的能 `git apply --check`、历史假绿夹具回放真的还是红的。

## 缺口不写成通过

D0-030（每个 PASS 有终裁 JSON）、D0-035（终裁 JSON 有 schema）、D0-081
（冲突时保留双方判词）当前**不成立**。本文件不替它们写通过断言；对应台账行
按 FAIL / UNVERIFIED 登记，缺口由 `d0_self_acceptance.py --group docs` 的
OBSERVED 行留痕。

运行：
    python -I -m pytest tools/tests/test_d0_docs.py -q
"""

from __future__ import annotations

import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DRIVER = ROOT / "tools" / "ci" / "d0_self_acceptance.py"
FIXTURE_DIR = ROOT / "tools" / "tests" / "fixtures" / "acceptance"


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8", errors="replace")


def run(*argv: str) -> subprocess.CompletedProcess:
    return subprocess.run([sys.executable, "-I", *argv], cwd=str(ROOT),
                          capture_output=True, text=True, encoding="utf-8",
                          errors="replace")


class TestDod(unittest.TestCase):
    """D0-008…014：DoD 六个面 + 台账映射。"""

    @classmethod
    def setUpClass(cls) -> None:
        cls.text = read("docs/acceptance/dod.md")

    def test_008_main_path(self) -> None:
        self.assertIn("### 1.1 功能", self.text)
        self.assertIn("可复跑", self.text)

    def test_009_failure_path(self) -> None:
        self.assertIn("### 1.2 失败路径", self.text)
        self.assertIn("数据安全", self.text)

    def test_010_boundary(self) -> None:
        self.assertIn("### 1.3 边界", self.text)
        self.assertIn("杀进程", self.text)

    def test_011_two_independent_evidence(self) -> None:
        self.assertIn("### 1.4 证据", self.text)
        self.assertIn("2 类独立证据", self.text)

    def test_012_independent_review(self) -> None:
        self.assertIn("### 1.5 独立复核", self.text)
        self.assertIn("reviewer 不是该行作者", self.text)

    def test_013_adversarial_review(self) -> None:
        self.assertIn("### 1.6 对抗复核", self.text)
        self.assertIn("反例", self.text)

    def test_014_ledger_mapping(self) -> None:
        self.assertIn("## 6. 与台账的映射", self.text)
        self.assertIn("D0 → 本文件与工具本身的 100 行", self.text)


class TestVerdicts(unittest.TestCase):
    """D0-029：终裁目录存在且有读取说明（D0-030/035 的缺口不在本文件断言）。"""

    def test_029_directory_and_readme(self) -> None:
        directory = ROOT / "docs" / "acceptance" / "verdicts"
        self.assertTrue(directory.is_dir())
        text = (directory / "README.md").read_text(encoding="utf-8")
        self.assertIn("不得在 `authors` 内", text)
        template = json.loads((directory / "_template.json").read_text(encoding="utf-8"))
        self.assertEqual(template["verdict"], "UNVERIFIED")
        self.assertIn("run_id", template)


class TestPatch(unittest.TestCase):
    """D0-064…070：CI 补丁是提案且可落地。"""

    @classmethod
    def setUpClass(cls) -> None:
        cls.patch = read("tools/ci/acceptance_ci.patch")

    def test_064_proposal_only(self) -> None:
        self.assertFalse((ROOT / ".github" / "workflows" / "acceptance.yml").exists())

    def test_065_report_step(self) -> None:
        self.assertIn("acceptance_report.py", self.patch)

    def test_066_lint_step(self) -> None:
        self.assertIn("acceptance_lint.py", self.patch)

    def test_067_trace_step(self) -> None:
        self.assertIn("trace_requirements.py", self.patch)

    def test_068_failure_blocks(self) -> None:
        self.assertNotIn("continue-on-error", self.patch)

    def test_069_no_write_conflict(self) -> None:
        self.assertEqual(self.patch.count("new file mode"), 1)
        self.assertNotIn("android-check.yml", self.patch)
        self.assertNotIn("schema-export.yml", self.patch)

    def test_070_apply_check(self) -> None:
        proc = subprocess.run(["git", "apply", "--check", "tools/ci/acceptance_ci.patch"],
                              cwd=str(ROOT), capture_output=True, text=True)
        self.assertEqual(proc.returncode, 0, proc.stdout + proc.stderr)


class TestRunbook(unittest.TestCase):
    """D0-071…076：运行手册的六项内容。"""

    @classmethod
    def setUpClass(cls) -> None:
        cls.text = read("docs/acceptance/runbook.md")

    def test_071_per_domain_commands(self) -> None:
        for heading in ("## 2.", "## 4.", "## 5.", "## 6.", "## 10."):
            self.assertIn(heading, self.text)

    def test_072_verdict_criteria(self) -> None:
        self.assertIn("## 8. verdict 决策", self.text)

    def test_073_archive_paths(self) -> None:
        self.assertIn("## 10. 证据哈希清单", self.text)
        self.assertIn("docs/research/evidence/2026-11-05-batch-44", self.text)

    def test_074_device_binding(self) -> None:
        self.assertIn("APK sha", self.text)
        self.assertIn("serial", self.text)

    def test_075_residual_declaration(self) -> None:
        self.assertIn("## 5.", self.text)
        self.assertIn("## 9.1", self.text)

    def test_076_blocked_exit(self) -> None:
        self.assertIn("## 9. BLOCKED 清单与出口", self.text)
        self.assertIn("真实 Provider", self.text)


class TestProtocol(unittest.TestCase):
    """D0-077…080、082：终裁协议的字段与归档路径（D0-081 缺口不在此断言）。"""

    @classmethod
    def setUpClass(cls) -> None:
        cls.runbook = read("docs/acceptance/runbook.md")
        cls.readme = read("docs/acceptance/verdicts/README.md")

    def test_077_sign_fields(self) -> None:
        for field in ('"id"', '"verdict"', '"run_id"', '"reviewer"', '"authors"',
                      '"ledger_sha256"', '"evidence"', '"evidence_sha256"', '"adversarial"'):
            self.assertIn(field, self.runbook)

    def test_078_run_id_and_ledger_sha_binding(self) -> None:
        self.assertIn("`run_id` 与本次命令/设备轮次一致", self.runbook)
        self.assertIn("`ledger_sha256` 由", self.runbook)

    def test_079_non_forgeable_fields(self) -> None:
        self.assertIn("run_id", self.readme)
        self.assertIn("ledger_sha256", self.readme)
        self.assertIn("sha256sum docs/acceptance/ledger.md", self.readme)

    def test_080_double_sign(self) -> None:
        self.assertIn("## 1.5 独立复核", read("docs/acceptance/dod.md"))
        self.assertIn("## 1.6 对抗复核", read("docs/acceptance/dod.md"))

    def test_082_archive_path(self) -> None:
        self.assertIn("verdicts/<ID>.json", self.runbook)


class TestForgery(unittest.TestCase):
    """D0-083…088：拒绝伪造清单六项。"""

    @classmethod
    def setUpClass(cls) -> None:
        cls.dod = read("docs/acceptance/dod.md")
        cls.runbook = read("docs/acceptance/runbook.md")

    def test_083_reviewer_cannot_sign_self(self) -> None:
        self.assertIn("reviewer 不得给自己签字", self.dod)

    def test_084_summary_not_evidence(self) -> None:
        self.assertIn("SUMMARY_AS_EVIDENCE", self.dod)

    def test_085_screenshot_binding(self) -> None:
        self.assertIn("serial", self.dod)
        self.assertIn("apk_sha256", self.dod)

    def test_086_cmd_exit_readable(self) -> None:
        self.assertIn("命令日志必须含可读 exit", self.dod)

    def test_087_device_evidence_serial(self) -> None:
        self.assertIn("`device` JSON 必须有 `serial`", self.runbook)

    def test_088_not_rewritten_as_pass(self) -> None:
        self.assertIn("UNVERIFIED_AS_PASS", self.dod)


class TestReplay(unittest.TestCase):
    """D0-095…100：历史假绿回放仍然必须红。"""

    def test_095_fixtures_exist(self) -> None:
        self.assertTrue((FIXTURE_DIR / "fake_green_batch41.ledger.md").is_file())
        self.assertTrue((FIXTURE_DIR / "fake_green_batch42.ledger.md").is_file())

    def test_096_report_exits_one(self) -> None:
        for name in ("fake_green_batch41", "fake_green_batch42"):
            proc = run("tools/ci/acceptance_report.py", "--ledger",
                       f"tools/tests/fixtures/acceptance/{name}.ledger.md", "--partial")
            self.assertEqual(proc.returncode, 1, proc.stdout)
            report = json.loads(proc.stdout)
            self.assertNotEqual(report["state"], "PASS")

    def test_097_lint_exits_one(self) -> None:
        for name in ("fake_green_batch41", "fake_green_batch42"):
            proc = run("tools/ci/acceptance_lint.py", "--ledger",
                       f"tools/tests/fixtures/acceptance/{name}.ledger.md", "--partial")
            self.assertEqual(proc.returncode, 1, proc.stdout)
            self.assertTrue(json.loads(proc.stdout)["violations"])

    def test_098_runbook_documents_replay(self) -> None:
        text = read("docs/acceptance/runbook.md")
        self.assertIn("## 3. 历史假绿回放", text)
        self.assertIn("fake_green_batch42.ledger.md", text)
        self.assertIn("期望 1", text)

    def test_099_replay_is_archived_by_driver(self) -> None:
        with tempfile.TemporaryDirectory() as td:
            proc = run("tools/ci/d0_self_acceptance.py", "--group", "docs",
                       "--section", "replay", "--work", td)
            self.assertEqual(proc.returncode, 0, proc.stdout + proc.stderr)
            self.assertIn("CHECK report_41 EXPECTED=1 ACTUAL=1 OK", proc.stdout)
            self.assertIn("CHECK lint_42 EXPECTED=1 ACTUAL=1 OK", proc.stdout)
            raw = Path(td) / "docs-replay" / "raw"
            self.assertTrue((raw / "report_41.txt").is_file())
            self.assertTrue((raw / "lint_42.txt").is_file())

    def test_100_thresholds_not_relaxed(self) -> None:
        report = json.loads(run(
            "tools/ci/acceptance_report.py", "--ledger",
            "tools/tests/fixtures/acceptance/fake_green_batch42.ledger.md", "--partial").stdout)
        codes = {item["code"] for item in report["violations"]}
        self.assertIn("CMD_LOG_SKIP_MARKER", codes)
        self.assertIn("PASS_NEEDS_TWO_INDEPENDENT_EVIDENCE", codes)
        lint = json.loads(run(
            "tools/ci/acceptance_lint.py", "--ledger",
            "tools/tests/fixtures/acceptance/fake_green_batch42.ledger.md", "--partial").stdout)
        lint_codes = {item["code"] for item in lint["violations"]}
        self.assertIn("SKIPPED_GATE_AS_PASS", lint_codes)
        self.assertIn("SUMMARY_AS_EVIDENCE", lint_codes)


if __name__ == "__main__":
    unittest.main()
