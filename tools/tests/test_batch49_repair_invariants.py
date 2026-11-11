# -*- coding: utf-8 -*-
"""批次 49 修复环的回归不变量（F-057 假 PASS / soak 退出码 / 设备绑定自述）。

消灭的具体失败（逐条对应本批 review finding）：
1. F-057「超限」判据被反向改写成「超限路径未命中」后记 PASS——把“没测到”当成“测过了”。
   判据是正向要求：2h 内至少 1 次 FATAL + 超限路径命中；未命中时只能 FAIL。
2. `f-soak.sh` 的汇总 heredoc 无条件写 `EXIT=0`，崩溃的批次 44 也显示 0；退出码证据是空的。
3. `06/07 .device.json` 声称 serial/APK sha 由 instrument 日志“逐字转写”，但日志里没有这两项。

测试只读仓库文件；允许用 BATCH49_REPAIR_ROOT 指向“修复前”夹具做红样回放。

运行：
    python -I -m pytest tools/tests/test_batch49_repair_invariants.py -q
"""
from __future__ import annotations

import json
import os
import re
import unittest
from pathlib import Path

REPO = Path(os.environ.get("BATCH49_REPAIR_ROOT", Path(__file__).resolve().parents[2]))
LEDGER = REPO / "docs" / "acceptance" / "ledger.md"
ROWS = REPO / "docs" / "acceptance" / "verdicts" / "rows"
SOAK = REPO / "docs" / "research" / "evidence" / "2026-11-08-batch-49" / "device" / "soak"
DEVICE = REPO / "docs" / "research" / "evidence" / "2026-11-08-batch-49" / "device"


def _row(line_prefix: str) -> str:
    for line in LEDGER.read_text(encoding="utf-8").splitlines():
        if line.startswith(line_prefix):
            return line
    raise AssertionError(f"ledger row not found: {line_prefix}")


def _fields(line: str) -> list[str]:
    return line.strip()[2:-2].split(" | ")


class F057VerdictTest(unittest.TestCase):
    def test_over_limit_row_keeps_the_positive_criterion_and_fails(self):
        fields = _fields(_row("| F-057 |"))
        verdict = fields[-1]
        method = fields[3]
        evidence = fields[4]

        self.assertEqual("FAIL", verdict,
                         "超限路径未命中不得记 PASS：判据是正向要求「至少 1 次 FATAL / 超限路径命中」")
        self.assertIn("至少 1 次 FATAL EXCEPTION", method,
                      "HEAD 的正向判据原文必须保留，不能反向改写成「未命中=通过」")
        self.assertIn("超限路径命中", method)
        self.assertNotEqual("PASS", verdict,
                            "把「超限路径未命中」记成 PASS 就是改写成功含义")
        self.assertIn(
            "TEST-com.tingyun.smartmistakebook.SchedulingRetentionSliderValueTest.xml",
            evidence,
            "唯一定位滑杆端点越界的单测 XML 不能从证据里删掉",
        )


class SoakExitEvidenceTest(unittest.TestCase):
    def test_script_records_a_real_status_instead_of_the_literal(self):
        text = (SOAK / "f-soak.sh").read_text(encoding="utf-8")
        self.assertNotIn('"EXIT=0"]', text,
                         "旧脚本无条件写 EXIT=0：批次44 的 2504s FATAL 也显示 EXIT=0")
        self.assertIn("OUTCOME=", text)
        self.assertIn("sys.exit(status)", text,
                      "汇总必须以真实状态退出，不能只打印字面量")
        self.assertIn("EXIT={status}", text)

    def test_rows_do_not_claim_cmd_exit_from_the_caveated_log(self):
        for row_id in ("F-055", "F-063"):
            data = json.loads((ROWS / f"{row_id}.json").read_text(encoding="utf-8"))
            names = [c.get("name") or c.get("check") for c in data["checks"]]
            self.assertNotIn(
                "cmd_exit", names,
                f"{row_id}: soak.cmd.log 的 EXIT=0 是脚本字面量，不能作 cmd_exit 证据",
            )
            self.assertIn(
                "file:docs/research/evidence/2026-11-08-batch-49/device/soak/soak.cmd.log",
                data["evidence"],
                f"{row_id}: 命令日志只能作为 file 出处保留，不得声明退出码",
            )


class DeviceBindingProvenanceTest(unittest.TestCase):
    def test_sidecars_do_not_claim_serial_apk_from_the_instrument_log(self):
        for name in ("06-instrument-green-phase.device.json",
                     "07-instrument-calibration-page.device.json"):
            data = json.loads((DEVICE / name).read_text(encoding="utf-8"))
            note = data.get("note", "")
            self.assertNotIn(
                "逐字转写 counts/serial/APK sha", note,
                f"{name}: instrument 日志只有类名/Time/OK，没有 serial 与 APK sha",
            )
            provenance = data.get("binding_provenance")
            self.assertIsInstance(provenance, dict, f"{name}: 必须单列来源")
            self.assertIn("00-snapshot-and-device-meta.txt", provenance["serial"])
            self.assertIn("00-snapshot-and-device-meta.txt", provenance["apk_sha256"])
            self.assertIn("不证明", provenance["source_log_sha256_meaning"])


if __name__ == "__main__":
    unittest.main()
