# -*- coding: utf-8 -*-
"""低负载窗门的机制测试（批次 41 / WS4 item 25/28）。

只验证**判据与解析**：真实设备采样不在单测里跑（`adb` 不可作为 CI 依赖）。
覆盖：样本解析、逐 10 s 采样窗口的样本数 fail-closed、相对地板的三项判据、
唤醒探针、`nproc` 能力探测、缺唤醒探针/缺地板时不得判 PASS。

运行（仓库根）：
    python -m unittest discover -s tools/tests -t tools -k lowload_gate
"""
from __future__ import annotations

import json
import sys
import tempfile
import unittest
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO / "tools" / "ci"))

import lowload_gate as G  # noqa: E402

FLOOR = {
    "serial": "emulator-test",
    "nproc": 4,
    "samples": 30,
    "interval_s": 10.0,
    "busy_p95": 1.4,
    "busy_max": 1.4,
    "la_ratio_p95": 0.005,
    "la_ratio_max": 0.005,
    "ctxt_p95": 400.0,
    "wakeup_p95_ms": 14.0,
    "wakeup_max_ms": 17.0,
}


def _sample(i: int, busy_ticks: int = 20, total_ticks: int = 2000, la1: float = 0.02,
            ctxt_delta: int = 4000, runq: int = 1) -> dict:
    """构造相邻两点；调用方用 G.compute_metrics 差分。"""
    # /proc/stat 是累计计数器：第 i 点的值 = 每 10 s 增量 × i。
    return {
        "uptime": float(i * 10),
        "la1": la1,
        "cpu": [busy_ticks * i, 0, 0, (total_ticks - busy_ticks) * i, 0, 0, 0, 0, 0, 0],
        "procs_running": runq,
        "procs_blocked": 0,
        "ctxt": i * ctxt_delta,
    }


def _window(count: int = 31, **kwargs) -> dict:
    return G.compute_metrics([_sample(i, **kwargs) for i in range(count)], nproc=4)


class SampleParsingTest(unittest.TestCase):

    def test_parses_device_side_pipe_rows(self) -> None:
        lines = [
            "12345.67|0.02 0.03 0.01 1/1677 13271|cpu  33818 2742 186828 20263063 15637 2 1617 0 0 0"
            "|procs_running 1|procs_blocked 0|ctxt 23489094",
            "",
        ]
        parsed = G.parse_sample_lines(lines)
        self.assertEqual(1, len(parsed))
        self.assertAlmostEqual(12345.67, parsed[0]["uptime"])
        self.assertAlmostEqual(0.02, parsed[0]["la1"])
        self.assertEqual(10, len(parsed[0]["cpu"]))
        self.assertEqual(1, parsed[0]["procs_running"])
        self.assertEqual(23489094, parsed[0]["ctxt"])

    def test_rejects_malformed_row_instead_of_skipping_it(self) -> None:
        with self.assertRaises(ValueError):
            G.parse_sample_lines(["garbage"])

    def test_snapshot_script_does_not_self_match_the_runner_pattern(self) -> None:
        # grep 自身的 argv 就是 `grep AndroidJUnitRunner`：必须用 awk 排除 awk/sh，
        # 否则"并发 runner"恒为 2（设备实测），低负载门永远不过。
        script = G.snapshot_script()
        self.assertIn("awk", script)
        self.assertIn('$1 != "awk"', script)
        self.assertIn('$1 != "sh"', script)


class WindowCriteriaTest(unittest.TestCase):

    def test_quiet_window_within_floor_passes(self) -> None:
        result = G.evaluate_window(
            FLOOR,
            _window(),
            {"p50_ms": 11.0, "p95_ms": 14.0, "max_ms": 17.0},
            expected_samples=30,
        )
        self.assertEqual("PASS", result["verdict"], result["reasons"])

    def test_missing_sample_is_invalid_not_pass(self) -> None:
        # 30 个样本点 = 29 个差分；少一个点就是 28 个差分。
        result = G.evaluate_window(
            FLOOR,
            _window(count=30),
            {"p50_ms": 11.0, "p95_ms": 14.0, "max_ms": 17.0},
            expected_samples=30,
        )
        self.assertEqual("INVALID", result["verdict"])
        self.assertIn("sample_count", result["reasons"])

    def test_busy_p95_above_floor_band_is_invalid(self) -> None:
        # 每 10 s 的 busy 从 1% 抬到 ~12%：p95 越过 floor_p95 + 3.0pp。
        result = G.evaluate_window(
            FLOOR,
            _window(busy_ticks=240, total_ticks=2000),
            {"p50_ms": 40.0, "p95_ms": 95.0, "max_ms": 110.0},
            expected_samples=30,
        )
        self.assertEqual("INVALID", result["verdict"])
        self.assertIn("busy_p95", result["reasons"])
        self.assertIn("wakeup_p95", result["reasons"])
        self.assertIn("busy_max", result["reasons"])

    def test_absolute_la_cap_catches_run_queue(self) -> None:
        result = G.evaluate_window(
            FLOOR,
            _window(la1=1.4),
            {"p50_ms": 11.0, "p95_ms": 14.0, "max_ms": 17.0},
            expected_samples=30,
        )
        self.assertEqual("INVALID", result["verdict"])
        self.assertIn("la_ratio_max", result["reasons"])

    def test_nproc_mismatch_invalidates_the_window(self) -> None:
        window = _window()
        window["nproc"] = 2
        result = G.evaluate_window(
            FLOOR, window, {"p50_ms": 11.0, "p95_ms": 14.0, "max_ms": 17.0},
            expected_samples=30,
        )
        self.assertEqual("INVALID", result["verdict"])
        self.assertIn("nproc", result["reasons"])

    def test_missing_wakeup_probe_is_invalid(self) -> None:
        result = G.evaluate_window(FLOOR, _window(), None, expected_samples=30)
        self.assertEqual("INVALID", result["verdict"])
        self.assertIn("wakeup_missing", result["reasons"])

    def test_floor_itself_must_be_present(self) -> None:
        result = G.evaluate_window({}, _window(), {"p50_ms": 11.0, "p95_ms": 14.0, "max_ms": 17.0},
                                   expected_samples=30)
        self.assertEqual("INVALID", result["verdict"])
        self.assertIn("floor_missing", result["reasons"])

    def test_runqueue_is_reported_not_gated(self) -> None:
        # procs_running=6 只记录；单点 runq 高不等于窗无效（R5 §4.3 L-3e）。
        result = G.evaluate_window(
            FLOOR,
            _window(runq=6),
            {"p50_ms": 11.0, "p95_ms": 14.0, "max_ms": 17.0},
            expected_samples=30,
        )
        self.assertEqual("PASS", result["verdict"], result["reasons"])
        self.assertEqual(6, result["summary"]["procs_running_max"])


class CliTest(unittest.TestCase):

    def test_evaluate_subcommand_reads_files_and_writes_verdict(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            floor = root / "floor.json"
            window = root / "window.json"
            wakeup = root / "wakeup.json"
            out = root / "gate.json"
            floor.write_text(json.dumps(FLOOR), encoding="utf-8")
            window.write_text(json.dumps(_window()), encoding="utf-8")
            wakeup.write_text(json.dumps({"p50_ms": 11.0, "p95_ms": 14.0, "max_ms": 17.0}),
                              encoding="utf-8")
            code = G.main([
                "evaluate",
                "--floor", str(floor),
                "--window", str(window),
                "--wakeup", str(wakeup),
                "--out", str(out),
            ])
            self.assertEqual(0, code)
            payload = json.loads(out.read_text(encoding="utf-8"))
            self.assertEqual("PASS", payload["verdict"])

    def test_emit_args_only_on_pass(self) -> None:
        args = G.render_instrument_args("PASS", "pre+post PASS")
        self.assertIn("-e smb.lowload.gate PASS", args)
        self.assertIn("-e smb.lowload.reason pre+post_PASS", args)
        args = G.render_instrument_args("INVALID", "busy_p95")
        self.assertIn("-e smb.lowload.gate INVALID", args)


if __name__ == "__main__":
    unittest.main()
