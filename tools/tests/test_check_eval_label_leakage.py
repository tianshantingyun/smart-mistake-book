# -*- coding: utf-8 -*-
"""结果面向禁令自查脚本的自测（批次 41 / AUD-08 item 50）。

它消灭的失败：禁令"不得用评测集标签调参"只能靠人工 review 保证——没有任何机器检查，
于是"先看金标、再把阈值写成刚过"这类改动在 diff 里与正常常量修改无法区分。本套用例
对每条检查都配正例与负例：

- 正例：门脚本读金标但阈值是硬编码常量（含出处）→ 不得报错；
- 负例：阈值由金标度量值算出 / 由金标数据写进参数文件 → 必须报 error；
- 告警面：同一脚本既读金标又开放阈值 CLI 覆盖 → 报 warning（不是 error）；
- dev 集（`tasks_dev_24.json`）可调参，不得与 protected 集混为一谈；
- 只报不改：扫描前后被扫文件逐字节一致。

运行（仓库根）：
    python -m unittest discover -s tools/tests -t tools -k eval_label_leakage
"""

from __future__ import annotations

import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO / "tools"))

from ci import check_eval_label_leakage as leak  # noqa: E402


CLEAN_GATE = '''\
"""A gate that reads the frozen golden set but keeps thresholds hard-coded."""
import json
from pathlib import Path

DEFAULT_GOLDEN = Path(__file__).resolve().parent / "split_golden_v1.json"
COUNT_GATE = 0.9          # 立项自定，出处见 GATE.md
FRAGMENT_GATE = 0.05


def measure(path):
    doc = json.loads(path.read_text(encoding="utf-8"))
    return len(doc["pages"])


def main():
    pages = measure(DEFAULT_GOLDEN)
    return 0 if pages >= 1 else 1
'''

LABEL_DERIVED_THRESHOLD = '''\
"""A gate that quietly sets its threshold from the golden set it is judging."""
import json
from pathlib import Path

DEFAULT_GOLDEN = Path(__file__).resolve().parent / "split_golden_v1.json"


def main():
    doc = json.loads(DEFAULT_GOLDEN.read_text(encoding="utf-8"))
    rate = len(doc["pages"]) / 10.0
    COUNT_GATE = rate * 0.9
    return 0 if rate >= COUNT_GATE else 1
'''

LABEL_DERIVED_PARAM_FILE = '''\
"""A calibration script that writes gate parameters derived from the blind set."""
import json
from pathlib import Path

BLIND = Path(__file__).resolve().parent / "tasks_blind_120.json"
PARAMS = Path(__file__).resolve().parent / "gate_thresholds.json"


def main():
    tasks = json.loads(BLIND.read_text(encoding="utf-8"))
    observed = len(tasks) / 100.0
    json.dump({"count_threshold": observed - 0.1}, PARAMS.open("w", encoding="utf-8"))
'''

THRESHOLD_OVERRIDE = '''\
"""A gate that reads the golden set and lets the caller move the threshold."""
import argparse
import json
from pathlib import Path

DEFAULT_GOLDEN = Path(__file__).resolve().parent / "retrieval_quality_golden_set.json"


def main(argv=None):
    parser = argparse.ArgumentParser()
    parser.add_argument("--golden", type=Path, default=DEFAULT_GOLDEN)
    parser.add_argument("--recall5-threshold", type=float, default=0.8)
    args = parser.parse_args(argv)
    golden = json.loads(args.golden.read_text(encoding="utf-8"))
    return 0 if len(golden) else 1
'''

DEV_SET_TUNING = '''\
"""Tuning on the dev split is allowed; the dev set is not a protected label set."""
import json
from pathlib import Path

DEV = Path(__file__).resolve().parent / "tasks_dev_24.json"


def main():
    tasks = json.loads(DEV.read_text(encoding="utf-8"))
    tolerance = len(tasks) / 100.0
    return tolerance
'''

REGEX_CONSTANT = '''\
"""A scanner that merely mentions 'golden' in its own regex is not a label reader."""
import re

PROTECTED_LABEL_RE = re.compile(r"(golden|blind|acceptance)")


def main():
    return 0
'''

BARE_FILENAME_LABEL = '''\
"""A bare file-name constant is still a label path."""
import json

BLIND_FILE = "tasks_blind_120.json"


def main():
    with open(BLIND_FILE, encoding="utf-8") as handle:
        return len(json.load(handle))
'''


class _TempScripts:
    """在临时目录里放若干候选脚本；扫描只读，不碰仓库文件。"""

    def __init__(self, scripts: dict[str, str]):
        self._tmp = tempfile.TemporaryDirectory()
        self.root = Path(self._tmp.name)
        for name, source in scripts.items():
            path = self.root / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(source, encoding="utf-8", newline="")
        self._scripts = scripts

    def __enter__(self) -> Path:
        return self.root

    def __exit__(self, *exc) -> None:
        self._tmp.cleanup()

    def snapshot(self) -> dict[str, bytes]:
        return {str(path.relative_to(self.root)): path.read_bytes()
                for path in self.root.rglob("*") if path.is_file()}


class EvalLabelLeakageTest(unittest.TestCase):
    def test_hardcoded_threshold_with_label_read_is_clean(self):
        with _TempScripts({"clean_gate.py": CLEAN_GATE}) as root:
            report = leak.scan([root])
        self.assertEqual([], report["findings"])
        self.assertEqual(1, report["summary"]["protectedLabelReaders"])
        self.assertEqual(0, report["summary"]["errors"])

    def test_threshold_derived_from_label_is_error(self):
        with _TempScripts({"derived_gate.py": LABEL_DERIVED_THRESHOLD}) as root:
            report = leak.scan([root])
        errors = [f for f in report["findings"] if f["severity"] == "error"]
        self.assertEqual(1, len(errors), report["findings"])
        self.assertEqual("LABEL_DERIVED_THRESHOLD", errors[0]["check"])
        self.assertIn("COUNT_GATE", errors[0]["detail"])
        self.assertEqual("derived_gate.py", errors[0]["file"])

    def test_param_file_written_from_label_is_error(self):
        with _TempScripts({"calibrate.py": LABEL_DERIVED_PARAM_FILE}) as root:
            report = leak.scan([root])
        checks = {f["check"] for f in report["findings"] if f["severity"] == "error"}
        self.assertIn("LABEL_DERIVED_PARAM_OUTPUT", checks)

    def test_threshold_cli_override_is_warning_only(self):
        with _TempScripts({"override_gate.py": THRESHOLD_OVERRIDE}) as root:
            report = leak.scan([root])
            warnings = [f for f in report["findings"] if f["severity"] == "warning"]
            self.assertTrue(warnings, report["findings"])
            self.assertTrue(all(f["check"] == "THRESHOLD_OVERRIDE_SURFACE" for f in warnings))
            self.assertEqual(0, report["summary"]["errors"])
            self.assertEqual(0, leak.main(["--paths", str(root)]))

    def test_dev_set_tuning_is_not_a_protected_label_read(self):
        with _TempScripts({"dev_tune.py": DEV_SET_TUNING}) as root:
            report = leak.scan([root])
        self.assertEqual([], report["findings"])
        self.assertEqual(0, report["summary"]["protectedLabelReaders"])

    def test_regex_constant_is_not_a_label_reader(self):
        # 扫描器自身就含 re.compile(r"(golden|…)")；把正则当路径会污染 protected 计数。
        with _TempScripts({"scan_gate.py": REGEX_CONSTANT}) as root:
            report = leak.scan([root])
        self.assertEqual([], report["findings"])
        self.assertEqual(0, report["summary"]["protectedLabelReaders"])

    def test_bare_filename_constant_is_a_label_reader(self):
        with _TempScripts({"bare_gate.py": BARE_FILENAME_LABEL}) as root:
            report = leak.scan([root])
        self.assertEqual(1, report["summary"]["protectedLabelReaders"])

    def test_scan_does_not_modify_scanned_files(self):
        holder = _TempScripts({"clean_gate.py": CLEAN_GATE,
                               "derived_gate.py": LABEL_DERIVED_THRESHOLD})
        with holder as root:
            before = holder.snapshot()
            leak.scan([root])
            after = holder.snapshot()
        self.assertEqual(before, after)

    def test_error_finding_exits_nonzero(self):
        with _TempScripts({"derived_gate.py": LABEL_DERIVED_THRESHOLD}) as root:
            self.assertEqual(1, leak.main(["--paths", str(root)]))

    def test_json_report_is_deterministic(self):
        with _TempScripts({"override_gate.py": THRESHOLD_OVERRIDE}) as root:
            first = leak.scan([root])
            second = leak.scan([root])
        self.assertEqual(json.dumps(first, sort_keys=True, ensure_ascii=False),
                         json.dumps(second, sort_keys=True, ensure_ascii=False))

    def test_real_repo_scan_runs(self):
        report = leak.scan([REPO / "tools"])
        self.assertGreater(report["summary"]["files"], 0)
        self.assertIn("prohibition", report)
        self.assertIn(report["exitCode"], (0, 1))


if __name__ == "__main__":
    unittest.main()
